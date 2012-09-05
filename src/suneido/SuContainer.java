/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido;

import static suneido.Suneido.dbpkg;
import static suneido.language.Ops.cmp;
import static suneido.util.Verify.verify;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;

import javax.annotation.concurrent.NotThreadSafe;

import suneido.database.query.Header;
import suneido.intfc.database.Record;
import suneido.intfc.database.RecordBuilder;
import suneido.language.Concat;
import suneido.language.Ops;
import suneido.language.Pack;
import suneido.language.Range;
import suneido.language.builtin.ContainerMethods;
import suneido.util.NullIterator;
import suneido.util.Util;

import com.google.common.collect.Iterables;

//TODO detect the same modification-during-iteration as cSuneido (see ObjectsTest)

/**
 * Suneido's single container type.
 * Combines an extendible array plus a hash map.
 * vec and map are synchronized for partial thread safety
 */
@NotThreadSafe // i.e. objects/records should be thread contained
public class SuContainer extends SuValue
		implements Comparable<SuContainer>, Iterable<Object> {
	public final List<Object> vec =
			Collections.synchronizedList(new ArrayList<Object>());
	private final Map<Object,Object> map =
			Collections.synchronizedMap(new CanonicalMap());
	private Object defval = null;
	private boolean readonly = false;

	@SuppressWarnings("serial")
	private static class CanonicalMap extends HashMap<Object, Object> {
		@Override
		public Object get(Object key) {
			return super.get(canonical(key));
		}
		@Override
		public Object put(Object key, Object value) {
			assert key != null;
			assert value != null;
			return super.put(canonical(key), value);
		}
		@Override
		public Object remove(Object key) {
			return super.remove(canonical(key));
		}
		@Override
		public boolean containsKey(Object key) {
			return super.containsKey(canonical(key));
		}
	}

	public SuContainer() {
	}

	/** create a new container and add the specified values */
	public SuContainer(Iterable<?> c) {
		addAll(c);
	}

	public SuContainer(SuContainer other) {
		vec.addAll(other.vec);
		map.putAll(other.map);
		defval = other.defval;
	}

	public static SuContainer of(Object x, Object y) {
		SuContainer c = new SuContainer();
		c.add(x);
		c.add(y);
		return c;
	}

	public Object vecGet(int i) {
		return vec.get(i);
	}
	public Object mapGet(Object key) {
		return map.get(key);
	}
	public Set<Map.Entry<Object, Object>> mapEntrySet() {
		return map.entrySet();
	}
	public Set<Object> mapKeySet() {
		return map.keySet();
	}

	public void add(Object value) {
		checkReadonly();
		vec.add(value);
		migrate();
	}

	public void addAll(Iterable<?> iterable) {
		Iterables.addAll(vec, iterable);
	}

	private void checkReadonly() {
		if (readonly)
			throw new SuException("can't modify readonly objects");
	}

	private void migrate() {
		Object x;
		while (null != (x = map.remove(vec.size())))
			vec.add(x);
	}

	public void insert(int at, Object value) {
		checkReadonly();
		if (0 <= at && at <= vec.size()) {
			vec.add(at, value);
			migrate();
		} else
			put(at, value);
	}

	public void merge(SuContainer c) {
		vec.addAll(c.vec);
		map.putAll(c.map);
		migrate();
	}

	@Override
	public void put(Object key, Object value) {
		preset(key, value);
	}

	public void preset(Object key, Object value) {
		checkReadonly();
		int i = index(key);
		if (0 <= i && i < vec.size())
			vec.set(i, value);
		else if (i == vec.size())
			add(value);
		else
			map.put(key, value);
	}

	/** used by CallRule, bypasses readonly */
	protected void putMap(Object key, Object value) {
		map.put(key, value);
	}

	@Override
	public Object get(Object key) {
		if (key instanceof Range)
			return ((Range) key).sublist(this);
		else
			return getDefault(key, defval);
	}

	public Object getDefault(Object key, Object defval) {
		Object x = getIfPresent(key);
		if (x != null)
			return x;
		if (defval instanceof SuContainer) {
			x = new SuContainer((SuContainer) defval);
			if (!readonly)
				put(key, x);
			return x;
		}
		return defval;
	}

	public Object getIfPresent(Object key) {
		int i = index(key);
		return (0 <= i && i < vec.size()) ? vec.get(i) : map.get(key);
	}

	public boolean containsKey(Object key) {
		int i = index(key);
		return (0 <= i && i < vec.size()) || map.containsKey(key);
	}

	public int size() {
		return vec.size() + map.size();
	}

	@Override
	public String toString() {
		return toString("#(", ")");
	}

	protected String toString(String before, String after) {
		StringBuilder sb = new StringBuilder(before);
		for (Object x : vec)
			sb.append(Ops.display(x)).append(", ");
		for (Map.Entry<Object, Object> e : map.entrySet())
			sb.append(keyToString(e.getKey()) + ": " + Ops.display(e.getValue()))
					.append(", ");
		if (size() > 0)
			sb.delete(sb.length() - 2, sb.length());
		return sb.append(after).toString();
	}
	static String keyToString(Object x) {
		return Ops.isString(x) ? keyToString(x.toString()) : Ops.display(x);
	}
	private static final Pattern idpat;
	static { idpat = Pattern.compile("^[_a-zA-Z][_a-zA-Z0-9]*[?!]?$"); }
	static String keyToString(String s) {
		return idpat.matcher(s).matches() ? s : Ops.display(s);
	}

	@Override
	public int hashCode() {
		return hashCode(0);
	}
	/** can't use vec and map hashCode methods
	 *  because we need to check nesting */
	@Override
	public int hashCode(int nest) {
		checkNest(++nest);
		int result = 17;
		for (Object x : vec)
			result = 31 * result + hashCode(x, nest);
		for (Map.Entry<Object, Object> e : map.entrySet()) {
			result = 31 * result + hashCode(e.getKey(), nest);
			result = 31 * result + hashCode(e.getValue(), nest);
		}
		return result;
	}

	private static int hashCode(Object x, int nest) {
		if (x instanceof SuContainer)
			return ((SuContainer) x).hashCode(nest);
		if (x instanceof BigDecimal)
			return canonical(x).hashCode();
		return x.hashCode();
	}

	/**
	 * convert to standardized types so lookup works consistently
	 * Number is converted to Integer if within range, else BigDecimal
	 * Concat is converted to String
	 */
	private static Object canonical(Object x) {
		if (x instanceof Number) {
			if (x instanceof Integer)
				return x;
			if (x instanceof Byte || x instanceof Short)
				return ((Number) x).intValue();
			if (x instanceof Long) {
				long i = (Long) x;
				if (Integer.MIN_VALUE <= i && i <= Integer.MIN_VALUE)
					return (int) i;
			}
			return canonicalBD(Ops.toBigDecimal(x));
		}
		if (x instanceof Concat)
			return x.toString();
		return x;
	}
	private static Number canonicalBD(BigDecimal n) {
		try {
			return n.intValueExact();
		} catch (ArithmeticException e) {
			return n.stripTrailingZeros();
		}
	}

	@Override
	public boolean equals(Object value) {
		if (value == this)
			return true;
		SuContainer c = Ops.toContainer(value);
		if (c == null)
			return false;
		if (vec.size() != c.vec.size() || map.size() != c.map.size())
			return false;
		for (int i = 0; i < vec.size(); ++i)
			if (!Ops.is_(vec.get(i), c.vec.get(i)))
				return false;
		for (Map.Entry<Object, Object> e : map.entrySet())
			if (!Ops.is_(e.getValue(), c.map.get(e.getKey())))
				return false;
		return true;
		//TODO handle stack overflow from self-reference
	}

	@Override
	public int compareTo(SuContainer other) {
		int ord;
		for (int i = 0; i < vec.size() && i < other.vec.size(); ++i)
			if (0 != (ord = cmp(vec.get(i), other.vec.get(i))))
				return ord;
		return vec.size() - other.vec.size();
		//TODO handle stack overflow from self-reference
	}

	public boolean delete(Object key) {
		checkReadonly();
		if (null != map.remove(key))
			return true;
		int i = index(key);
		if (0 <= i && i < vec.size()) {
			vec.remove(i);
			return true;
		} else
			return false;
	}

	public boolean erase(Object key) {
		checkReadonly();
		if (null != map.remove(key))
			return true;
		int i = index(key);
		if (i < 0 || vec.size() <= i)
			return false;
		// migrate from vec to map
		for (int j = vec.size() - 1; j > i; --j) {
			map.put(j, vec.get(j));
			vec.remove(j);
		}
		vec.remove(i);
		return true;
	}

	public void clear() {
		vec.clear();
		map.clear();
	}

	private static int index(Object x) {
		x = canonical(x);
		return x instanceof Integer ? (Integer) x : -1;
	}

	public int vecSize() {
		return vec.size();
	}
	public int mapSize() {
		return map.size();
	}

	@Override
	public int packSize(int nest) {
		checkNest(++nest);
		int ps = 1;
		if (size() == 0)
			return ps;

		ps += 4; // vec size
		for (Object x : vec)
			ps += 4 /* value size */+ Pack.packSize(x, nest);

		ps += 4; // map size
		for (Map.Entry<Object, Object> e : map.entrySet())
			ps += 4 /* member size */ + Pack.packSize(e.getKey(), nest)
					+ 4 /* value size */ + Pack.packSize(e.getValue(), nest);

		return ps;
	}

	static final int NESTING_LIMIT = 20;

	private static void checkNest(int nest) {
		if (nest > NESTING_LIMIT)
			throw new SuException("pack: object nesting limit ("
					+ NESTING_LIMIT + ") exceeded");
	}

	@Override
	public void pack(ByteBuffer buf) {
		pack(buf, Pack.Tag.OBJECT);
	}

	protected void pack(ByteBuffer buf, byte tag) {
		buf.put(tag);
		if (size() == 0)
			return;
		buf.putInt(vec.size() ^ 0x80000000);
		for (Object x : vec)
			packvalue(buf, x);

		buf.putInt(map.size() ^ 0x80000000);
		for (Map.Entry<Object, Object> e : map.entrySet()) {
			packvalue(buf, e.getKey()); // member
			packvalue(buf, e.getValue()); // value
		}
	}

	private static void packvalue(ByteBuffer buf, Object x) {
		buf.putInt(Pack.packSize(x) ^ 0x80000000);
		Pack.pack(x, buf);
	}

	public static Object unpack(ByteBuffer buf) {
		return unpack(buf, new SuContainer());
	}

	public static Object unpack(ByteBuffer buf, SuContainer c) {
		if (buf.remaining() == 0)
			return c;
		int n = buf.getInt() ^ 0x80000000; // vec size
		for (int i = 0; i < n; ++i)
			c.vec.add(unpackvalue(buf));
		n = buf.getInt() ^ 0x80000000; // map size
		for (int i = 0; i < n; ++i) {
			Object key = unpackvalue(buf);
			Object val = unpackvalue(buf);
			c.map.put(key, val);
		}
		verify(buf.remaining() == 0);
		return c;
	}

	private static Object unpackvalue(ByteBuffer buf) {
		int n = buf.getInt() ^ 0x80000000;
		ByteBuffer buf2 = buf.slice();
		buf2.limit(n);
		buf.position(buf.position() + n);
		return Pack.unpack(buf2);
	}

	public void setReadonly() {
		readonly = true;
	}

	public Object getReadonly() {
		return readonly;
	}

	public Object slice(int i) {
		SuContainer c = new SuContainer();
		c.vec.addAll(vec.subList(i, vec.size()));
		c.map.putAll(map);
		return c;
	}

	public static enum IterWhich { LIST, NAMED, ALL };

	@Override
	public Iterator<Object> iterator() {
		return iterator(IterWhich.ALL, IterResult.VALUE);
	}

	@SuppressWarnings("unchecked")
	public Iterator<Object> iterator(IterWhich iterWhich, IterResult iterResult) {
		return new Iter(
				iterWhich == IterWhich.NAMED ? nullIter : vec.iterator(),
				iterWhich == IterWhich.LIST ? nullIter : map.entrySet().iterator(),
				iterResult);
	}

	public Iterable<Object> iterable(IterWhich iterWhich, IterResult iterResult) {
		if (iterWhich == IterWhich.ALL && iterResult == IterResult.VALUE)
			return this;
		else
			return new IterableAdapter(iterWhich, iterResult);
	}

	private class IterableAdapter implements Iterable<Object> {
		private final IterWhich iterWhich;
		private final IterResult iterResult;

		public IterableAdapter(IterWhich iterWhich, IterResult iterResult) {
			this.iterWhich = iterWhich;
			this.iterResult = iterResult;
		}

		@Override
		public Iterator<Object> iterator() {
			return SuContainer.this.iterator(iterWhich, iterResult);
		}
	}

	@SuppressWarnings({ "rawtypes" })
	private static final NullIterator nullIter = new NullIterator();

	public static enum IterResult {
		KEY, VALUE, ASSOC, ENTRY
	};

	static class Iter implements Iterator<Object> {
		private final Iterator<Object> veciter;
		private int vec_i = 0;
		private final Iterator<Map.Entry<Object, Object>> mapiter;
		private final IterResult iterResult;

		public Iter(Iterator<Object> veciter,
				Iterator<Map.Entry<Object, Object>> mapiter, IterResult iterResult) {
			this.veciter = veciter;
			this.mapiter = mapiter;
			this.iterResult = iterResult;
		}
		@Override
		public boolean hasNext() {
			return veciter.hasNext() || mapiter.hasNext();
		}
		@Override
		public Object next() {
			if (veciter.hasNext())
				return result(vec_i++, veciter.next());
			else if (mapiter.hasNext()) {
				Map.Entry<Object, Object> e = mapiter.next();
				if (iterResult == IterResult.ENTRY)
					return e;
				return result(e.getKey(), e.getValue());
			} else
				throw new NoSuchElementException();
		}
		private Object result(Object key, Object value) {
			switch (iterResult) {
			case KEY:
				return key;
			case VALUE:
			case ENTRY:
				return value;
			case ASSOC:
				return SuContainer.of(key, value);
			default:
				throw SuException.unreachable();
			}
		}
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	public Object find(Object value) {
		for (int i = 0; i < vec.size(); ++i)
			if (Ops.is_(value, vec.get(i)))
				return i;
		for (Map.Entry<Object, Object> e : map.entrySet())
			if (Ops.is_(value, e.getValue()))
				return e.getKey();
		return null;
	}

	public void reverse() {
		checkReadonly();
		Collections.reverse(vec);
	}

	public void sort(final Object fn) {
		checkReadonly();
		if (fn == Boolean.FALSE)
			Collections.sort(vec, Ops.comp);
		else
			Collections.sort(vec, new Comparator<Object>() {
				@Override
				public int compare(Object x, Object y) {
					return Ops.call(fn, x, y) == Boolean.TRUE ? -1
							: Ops.call(fn, y, x) == Boolean.TRUE ? 1 : 0;
				}
			});
	}

	public int lowerBound(Object value, final Object fn) {
		if (fn == Boolean.FALSE)
			return Util.lowerBound(vec, value, Ops.comp);
		else
			return Util.lowerBound(vec, value, new Comparator<Object>() {
				@Override
				public int compare(Object x, Object y) {
					return Ops.call(fn, x, y) == Boolean.TRUE ? -1 : 1;
				}
			});
	}

	public int upperBound(Object value, final Object fn) {
		if (fn == Boolean.FALSE)
			return Util.upperBound(vec, value, Ops.comp);
		else
			return Util.upperBound(vec, value, new Comparator<Object>() {
				@Override
				public int compare(Object x, Object y) {
					return Ops.call(fn, x, y) == Boolean.TRUE ? -1 : 1;
				}
			});
	}

	public Util.Range equalRange(Object value, final Object fn) {
		if (fn == Boolean.FALSE)
			return Util.equalRange(vec, value, Ops.comp);
		else
			return Util.equalRange(vec, value, new Comparator<Object>() {
				@Override
				public int compare(Object x, Object y) {
					return Ops.call(fn, x, y) == Boolean.TRUE ? -1 : 1;
				}
			});
	}

	public Record toDbRecord(Header hdr) {
		RecordBuilder rec = dbpkg.recordBuilder();
		Object x;
		String ts = hdr.timestamp_field();
		for (String f : hdr.output_fldsyms())
			if (f == "-")
				rec.addMin();
			else if (f.equals(ts))
				rec.add(TheDbms.dbms().timestamp());
			else if (null != (x = get(f)))
				rec.add(x);
			else
				rec.addMin();
		return rec.build();
	}

	public void setDefault(Object value) {
		defval = value;
	}

	@Override
	public SuContainer toContainer() {
		return this;
	}

	@Override
	public String typeName() {
		return "Object";
	}

	public boolean isEmpty() {
		return vec.isEmpty() && map.isEmpty();
	}

	@Override
	public SuValue lookup(String method) {
		return ContainerMethods.methods.lookup(method);
	}

	public SuContainer subList(int from, int to) {
		return new SuContainer(vec.subList(from, to));
	}

}
