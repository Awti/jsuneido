/* Copyright 2011 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.immudb;

import suneido.SuException;
import suneido.util.ThreadConfined;

/**
 * Transactions must be thread confined.
 * Load and compact bend the rules and write data prior to commit
 * using loadRecord and saveBtrees
 */
@ThreadConfined
public class ExclusiveTransaction extends UpdateTransaction
		implements ImmuExclTran {

	ExclusiveTransaction(int num, Database db) {
		super(num, db);
		tran.allowStore();
	}

	@Override
	protected void lock(Database db) {
		assert ! db.exclusiveLock.isWriteLocked() : "already exclusively locked";
		if (! db.exclusiveLock.writeLock().tryLock())
			throw new SuException("can't make schema changes " +
					"when there are outstanding update transactions");
		locked = true;
	}

	@Override
	protected void unlock() {
		db.exclusiveLock.writeLock().unlock();
		locked = false;
	}

	@Override
	void verifyNotSystemTable(int tblnum, String what) {
	}

	// used by Bootstrap and TableBuilder
	@Override
	public Btree addIndex(Index index) {
		assert locked;
		Btree btree = new Btree(tran, this);
		indexes.put(index.tblnum, new ColNums(index.colNums), btree);
		return btree;
	}

	// used by TableBuilder
	@Override
	public void addSchemaTable(Table tbl) {
		assert locked;
		newSchema = newSchema.with(tbl);
	}

	// used by TableBuilder and Bootstrap
	@Override
	public void addTableInfo(TableInfo ti) {
		assert locked;
		udbinfo.add(ti);
	}

	// used by TableBuilder
	@Override
	public void updateSchemaTable(Table tbl) {
		assert locked;
		Table oldTbl = getTable(tbl.num);
		if (oldTbl != null)
			newSchema = newSchema.without(oldTbl);
		newSchema = newSchema.with(tbl);
	}

	// used by TableBuilder
	@Override
	public void dropTableSchema(Table table) {
		newSchema = newSchema.without(table);
	}

	// used by DbLoad and DbCompact
	@Override
	public int loadRecord(int tblnum, Record rec) {
		rec.tblnum = tblnum;
		int adr = rec.store(stor);
		udbinfo.updateRowInfo(tblnum, 1, rec.bufSize());
		return adr;
	}

	@Override
	protected void mergeDatabaseDbInfo() {
		assert rdbinfo.dbinfo == db.getDbinfo();
	}

	@Override
	protected void mergeRedirs() {
		tran.assertNoRedirChanges(db.getRedirs());
	}

	// used by DbLoad
	@Override
	public void saveBtrees() {
		tran.intrefs.startStore();
		Btree.store(tran);
		for (Btree btree : indexes.values())
			btree.info(); // convert roots from intrefs
		tran.intrefs.clear();
	}

	@Override
	public StoredRecordIterator storedRecordIterator(int first, int last) {
		return new StoredRecordIterator(stor, first, last);
	}

	@Override
	public void abort() {
		try {
			int redirsAdr = db.getRedirs().store(null);
			int dbinfoAdr = db.getDbinfo().store(null);
			store(dbinfoAdr, redirsAdr);
			tran.endStore();
		} finally {
			super.abort();
		}
	}

	@Override
	public void readLock(int adr) {
	}

	@Override
	public void writeLock(int adr) {
	}

	@Override
	public String toString() {
		return "et" + num;
	}

}
