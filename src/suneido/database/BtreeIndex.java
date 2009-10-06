package suneido.database;

import static suneido.Suneido.verify;

/**
 * Wraps a {@link Btree} to implement database table indexes. Adds transaction
 * stuff.
 *
 * @author Andrew McKinlay
 * <p><small>Copyright 2008 Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.</small></p>
 */
public class BtreeIndex {
	private final Destination dest;
	private final Btree bt;
	final boolean iskey;
	final boolean unique;
	final int tblnum;
	private String indexColumns;

	/**
	 * Create a new index.
	 */
	public BtreeIndex(Destination dest, int tblnum, String index,
			boolean isKey, boolean unique) {
		this(dest, tblnum, index, isKey, unique, new Btree(dest));
	}

	/**
	 * Open an existing index.
	 */
	public BtreeIndex(Destination dest, int tblnum, String index,
			boolean iskey, boolean unique, long root, int treelevels, int nnodes) {
		this(dest, tblnum, index, iskey, unique,
				new Btree(dest, root, treelevels, nnodes));
	}

	private BtreeIndex(Destination dest, int tblnum, String index,
			boolean iskey, boolean unique, Btree bt) {
		this.dest = dest;
		this.bt = bt;
		this.iskey = iskey;
		this.unique = unique;
		this.tblnum = tblnum;
		this.indexColumns = index;
	}

	public String getIndexColumns() {
		return indexColumns;
	}

	public void setIndexColumns(String indexColumns) {
		this.indexColumns = indexColumns;
	}

	public long root() {
		return bt.root();
	}

	public int nnodes() {
		return bt.nnodes();
	}

	public int treelevels() {
		return bt.treelevels();
	}

	public boolean insert(Transaction tran, Slot x) {
		// if (lower)
		// lower_key(x.key);
		if (iskey || (unique && !isEmpty(x.key))) {
			Record key = x.key;
			// TODO avoid dup - maybe some kind of slice/view
			key = key.dup().truncate(key.size() - 1); // strip record address
			if (find(tran, key) != null)
				return false;
		}
		return bt.insert(x);
	}

	public boolean remove(Record key) {
		return bt.remove(key);
	}

	public boolean isValid() {
		return bt.isValid();
	}

	public float rangefrac(Record from, Record to) {
		float f = bt.rangefrac(from, to);
		return f < .001 ? (float) .001 : f;
	}

	public Slot find(Transaction tran, Record key) {
		Iter iter = iter(tran, key).next();
		if (iter.eof())
			return null;
		Slot cur = iter.cur();
		return cur.key.hasPrefix(key) ? cur : null;
	}

	private boolean isEmpty(Record key) {
		int n = key.size() - 1; // - 1 to ignore record address at end
		if (n <= 0)
			return true;
		for (int i = 0; i < n; ++i)
			if (key.fieldSize(i) != 0)
				return false;
		return true;
	}

	public Iter iter(Transaction tran) {
		return new Iter(tran, Record.MINREC, Record.MAXREC);
	}

	public Iter iter(Transaction tran, Record key) {
		return new Iter(tran, key, key);
	}

	public Iter iter(Transaction tran, Record from, Record to) {
		return new Iter(tran, from, to);
	}

	public class Iter {
		Transaction tran;
		Record from;
		Record to;
		boolean rewound = true;
		Btree.Iter iter;
		TranRead tranread;
		long prevsize = Long.MAX_VALUE;

		private Iter(Transaction tran, Record from, Record to) {
			this.tran = tran;
			this.from = from;
			this.to = to;
			tranread = tran.read_act(tblnum, indexColumns);
		}

		public boolean eof() {
			return iter.eof();
		}

		public Slot cur() {
			verify(!rewound);
			return iter.cur();
		}

		public long keyadr() {
			return cur().keyadr();
		}

		void reset_prevsize() {
			prevsize = Long.MAX_VALUE;
		}

		public Iter next() {
			boolean first = true;
			Record prevkey = Record.MINREC;
			if (rewound) {
				iter = bt.locate(from);
				rewound = false;
				tranread.org = from;
			} else if (!iter.eof()) {
				prevkey = iter.key();
				first = false;
				iter.next();
			}
			while (!iter.eof()
					&& (iter.cur().keyadr() >= prevsize || !visible()))
				iter.next();
			if (!iter.eof() && iter.key().prefixgt(to))
				iter.seteof();
			if (!iter.eof() && (iskey || first || !eq(iter.key(), prevkey)))
				prevsize = dest.size();
			if (iter.eof())
				tranread.end = to;
			else if (iter.key().compareTo(tranread.end) > 0)
				tranread.end = iter.key().dup();
			return this;
		}

		public Iter prev() {
			if (rewound) {
				iter = bt.locate(to.dup(8).addMax());
				if (iter.eof())
					iter = bt.last();
				else
					while (!iter.eof() && iter.key().prefixgt(to))
						iter.prev();
				rewound = false;
				if (tranread != null)
					tranread.end = to;
			} else if (!iter.eof())
				iter.prev();
			while (!iter.eof() && !visible())
				iter.prev();
			prevsize = dest.size();
			if (!iter.eof() && iter.key().compareTo(from) < 0)
				iter.seteof();
			if (iter.eof())
				tranread.org = from;
			else if (iter.key().compareTo(tranread.org) < 0)
				tranread.org = iter.key().dup();
			return this;
		}

		private boolean visible() {
			return tran.visible(iter.cur().keyadr());
		}
	}

	/**
	 * @return True if the records are equal not including the last field.
	 */
	private static boolean eq(Record r1, Record r2) {
		int n = r1.size() - 1;
		if (n != r2.size() - 1)
			return false;
		for (int i = 0; i < n; ++i)
			if (r1.getraw(i) != r2.getraw(i))
				return false;
		return true;
	}
}
