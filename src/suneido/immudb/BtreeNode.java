/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.immudb;

import java.io.IOException;
import java.io.Writer;

import javax.annotation.concurrent.Immutable;

import suneido.immudb.Btree.Split;

import com.google.common.base.Strings;

/**
 * Parent type for {@link BtreeDbNode}, {@link BtreeDbMemNode},
 * and {@link BtreeMemNode}
 * Provides access to a list of keys in sorted order.
 * Keys are {@link Record}'s.
 * The last field on leaf keys is a pointer to the corresponding data record.
 * A tree node key is a leaf key plus a pointer to the child node.
 * Pointers are {@link MmapFile} adr int's
 */
@Immutable
abstract class BtreeNode {
	protected final int level;

	BtreeNode(int level) {
		this.level = level;
	}

	static BtreeNode emptyLeaf() {
		return emptyNode(0);
	}

	static BtreeNode emptyNode(int level) {
		return new BtreeMemNode(level);
	}

	int level() {
		return level;
	}

	boolean isLeaf() {
		return level == 0;
	}

	abstract int size();

	boolean isEmpty() {
		return size() == 0;
	}

	/** Inserts key in order */
	abstract BtreeStorableNode with(Record key);

	/** @return null if key not found */
	BtreeNode without(Record key) {
		if (isEmpty())
			return null;
		int at = lowerBound(key);
		if (isLeaf()) {
			if (at >= size() || ! get(at).equals(key))
				return null; // key not found
		} else { // tree node
			if (at >= size() || ! get(at).startsWith(key))
				--at;
			assert at >= 0;
		}
		return without(at);
	}

	protected abstract BtreeNode without(int i);

	/** used by split */
	abstract BtreeNode slice(int from, int to);

	/** used by split, either from will be 0 or to will be size */
	abstract BtreeNode without(int from, int to);

	abstract Record get(int i);

	abstract int store(Tran tran);

	Record find(Record key) {
		int at = findPos(key);
		return (at < 0 || at >= size()) ? null : get(at);
	}

	int findPos(Record key) {
		int at = lowerBound(key);
		if (isLeaf())
			return at;
		else { // tree node
			return Math.max(0, at - 1);
		}
	}

	int lowerBound(Record key) {
		int first = 0;
		int len = size();
		while (len > 0) {
			int half = len >> 1;
			int middle = first + half;
			if (compare(middle, key) < 0) {
				first = middle + 1;
				len -= half + 1;
			} else
				len = half;
		}
		return first;
	}

	private int compare(int middle, Record key) {
		return get(middle).compareTo(key);
	}

	/*
	 * e.g. this node has 10 keys
	 * mid = 5
	 * splitKey is node[4]
	 * if keyPos is 0 to 4, key goes in left
	 * if keyPos is 5 to 9, key goes in right
	 */
	/**
	 * @param key The key being added
	 * @param adr Address of this node
	 *
	 * @return a Split containing the key to be inserted into the parent
	 */
	Split split(Tran tran, Record key, int adr) {
		int level = level();
		BtreeNode right;
		Record splitKey;
		int keyPos = lowerBound(key);
		if (keyPos == size()) {
			// key is at end of node, just make new node
			right = new BtreeMemNode(level, key);
			splitKey = key;
		} else {
			int mid = size() / 2;
			splitKey = get(mid);
			BtreeNode left;
			right = slice(mid, size());
			left = without(mid, size());
			if (keyPos <= mid)
				left = left.with(key);
			else
				right = right.with(key);
			tran.redir(adr, left);
		}
		int splitKeySize = splitKey.size() - 1;
		if (level > 0) // tree node
			--splitKeySize;
		int rightAdr = tran.refToInt(right);
		splitKey = new RecordBuilder().addPrefix(splitKey, splitKeySize)
				.addMin().adduint(rightAdr).build();
		return new Split(level, adr, splitKey);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("BtreeNode ");
		if (isLeaf())
			sb.append("leaf");
		else
			sb.append("level=").append(level());
		sb.append(" size=").append(size());
		sb.append(" [");
		for (int i = 0; i < size(); ++i)
			sb.append(get(i));
		sb.append("]");
		return sb.toString();
	}

	void print(Writer w, Tran tran, int at) throws IOException {
		int level = level();
		String indent = Strings.repeat("     ", level);
		w.append(indent).append("NODE @ " + (at & 0xffffffffL) + "\n");
		for (int i = 0; i < size(); ++i) {
			Record slot = get(i);
			w.append(indent).append(slot.toString()).append("\n");
			if (level > 0) {
				int adr = ((Number) slot.get(slot.size() - 1)).intValue();
				Btree.nodeAt(tran, level - 1, adr).print(w, tran, adr);
			}
		}
	}

	/** returns the number of nodes processed */
	int check(Tran tran, Record key) {
		for (int i = 1; i < size(); ++i)
			assert get(i - 1).compareTo(get(i)) < 0;
		if (isLeaf()) {
			if (! isEmpty()) {
				key = new RecordBuilder().addPrefix(key, key.size() - 1).build();
				assert key.compareTo(get(0)) <= 0;
			}
			return 1;
		}
		assert isMinimalKey(get(0)) : "minimal";
		if (size() > 1)
			assert key.compareTo(get(1)) <= 0;
		int adr = Btree.getAddress(get(0));
		int nnodes = 1;
		nnodes += Btree.nodeAt(tran, level - 1, adr).check(tran, key);
		for (int i = 1; i < size(); ++i) {
			Record key2 = get(i);
			adr = Btree.getAddress(key2);
			nnodes += Btree.nodeAt(tran, level - 1, adr).check(tran, key2);
		}
		return nnodes;
	}

	protected static boolean isMinimalKey(Record key) {
		int keySize = key.size();
		for (int i = 0; i < keySize - 1; ++i)
			if (key.fieldLength(i) > 0)
				return false;
		return true;
	}

	@Override
	public boolean equals(Object other) {
		if (other == this)
			return true;
		if (! (other instanceof BtreeNode))
			return false;
		BtreeNode that = (BtreeNode) other;
		if (this.size() != that.size())
			return false;
		for (int i = 0; i < size(); ++i)
			if (! this.get(i).equals(that.get(i)))
				return false;
		return true;
	}

	@Override
	public int hashCode() {
		throw new UnsupportedOperationException();
	}

	protected static Record minimize(Record key) {
		int ptr = pointer(key);
		return minimalKey(key.size(), ptr);
	}

	protected static int pointer(Record rec) {
		return ((Number) rec.get(rec.size() - 1)).intValue();
	}

	protected static Record minimalKey(int nfields, int ptr) {
		RecordBuilder rb = new RecordBuilder();
		for (int i = 0; i < nfields - 1; ++i)
			rb.add("");
		rb.adduint(ptr);
		return rb.build();
	}

}
