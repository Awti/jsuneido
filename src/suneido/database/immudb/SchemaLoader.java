/* Copyright 2011 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.immudb;

import static suneido.database.immudb.Bootstrap.indexColumns;

import java.util.Collections;
import java.util.List;

import suneido.database.immudb.Bootstrap.TN;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Load the database schema into memory at startup
 * by reading the system tables.
 * Used by {@link Database}.open
 */
class SchemaLoader {
	private final ReadTransaction t;

	static Tables load(ReadTransaction t, int maxTblnum) {
		SchemaLoader sl = new SchemaLoader(t);
		return sl.load(maxTblnum);
	}

	private SchemaLoader(ReadTransaction t) {
		this.t = t;
	}

	private Tables load(int maxTblnum) {
		TranIndex tablesIndex = t.getIndex(TN.TABLES, indexColumns[TN.TABLES]);
		TranIndex columnsIndex = t.getIndex(TN.COLUMNS, indexColumns[TN.COLUMNS]);
		TranIndex indexesIndex = t.getIndex(TN.INDEXES, indexColumns[TN.INDEXES]);

		TablesReader tr = new TablesReader(tablesIndex);
		ColumnsReader cr = new ColumnsReader(columnsIndex);
		IndexesReader ir = new IndexesReader(indexesIndex);
		Tables.Builder tsb = new Tables.Builder(maxTblnum);
		while (true) {
			Record tblrec = tr.next();
			if (tblrec == null)
				break;
			int tblnum = tblrec.getInt(Table.TBLNUM);
			Columns columns = cr.next(tblnum);
			Indexes indexes = ir.next();
			Table table = new Table(tblrec, columns, indexes);
			tsb.add(table);
		}
		t.complete();
		return tsb.build();
	}

	private class TablesReader {
		IndexIter iter;

		TablesReader(TranIndex tablesIndex) {
			iter = tablesIndex.iterator();
		}
		Record next() {
			iter.next();
			if (iter.eof())
				return null;
			return input(iter.keyadr());
		}
	}

	private static final ImmutableList<Column> noColumns = ImmutableList.of();

	private class ColumnsReader {
		IndexIter iter;
		Column next;
		List<Column> list = Lists.newArrayList();

		ColumnsReader(TranIndex columnsIndex) {
			iter = columnsIndex.iterator();
			iter.next();
			next = column();
		}
		Columns next(int tblnum) {
			if (next == null || next.tblnum > tblnum)
				return new Columns(noColumns);
			while (true) {
				assert next.tblnum == tblnum;
				list.add(next);
				iter.next();
				if (iter.eof())
					break;
				Column prev = next;
				next = column();
				if (prev.tblnum != next.tblnum) {
					Columns cols = columns();
					list = Lists.newArrayList();
					return cols;
				}
			}
			next = null;
			return columns();
		}
		Column column() {
			return new Column(input(iter.keyadr()));
		}
		Columns columns() {
			Collections.sort(list);
			return new Columns(ImmutableList.copyOf(list));
		}
	}

	private class IndexesReader {
		IndexIter iter;
		Index next;
		ImmutableList.Builder<Index> list = ImmutableList.builder();

		IndexesReader(TranIndex indexesIndex) {
			iter = indexesIndex.iterator();
			iter.next();
			next = index();
		}
		Indexes next() {
			if (next == null)
				return null;
			while (true) {
				list.add(next);
				iter.next();
				if (iter.eof())
					break;
				Index prev = next;
				next = index();
				if (prev.tblnum != next.tblnum) {
					Indexes result = new Indexes(list.build());
					list = ImmutableList.builder();
					return result;
				}
			}
			next = null;
			return new Indexes(list.build());
		}
		private Index index() {
			return new Index(input(iter.keyadr()));
		}
	}

	private Record input(int adr) {
		return t.input(adr);
	}

}
