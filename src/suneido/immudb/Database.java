/* Copyright 2011 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.immudb;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import suneido.immudb.DbHashTrie.Entry;
import suneido.immudb.DbHashTrie.IntEntry;
import suneido.intfc.database.DatabasePackage.Status;
import suneido.language.Triggers;
import suneido.util.FileUtils;

import com.google.common.primitives.Ints;

@ThreadSafe
class Database implements ImmuDatabase {
	final Transactions trans = new Transactions();
	final Storage stor;
	final ReentrantReadWriteLock exclusiveLock = new ReentrantReadWriteLock();
	private final Triggers triggers = new Triggers();
	final Object commitLock = new Object();
	@GuardedBy("commitLock")
	private DbHashTrie dbinfo;
	@GuardedBy("commitLock")
	private DbHashTrie redirs;
	@GuardedBy("commitLock")
	Tables schema;

	// create

	static Database create(String dbfilename) {
		FileUtils.deleteIfExisting(dbfilename);
		return create(new MmapFile(dbfilename, "rw"));
	}

	static Database create(Storage stor) {
		Database db = new Database(stor, DbHashTrie.empty(stor),
				DbHashTrie.empty(stor), new Tables());
		Bootstrap.create(db.exclusiveTran());
		return db;
	}

	private Database(Storage stor,
			DbHashTrie dbinfo, DbHashTrie redirs, Tables schema) {
		this.stor = stor;
		this.setDbinfo(dbinfo);
		this.setRedirs(redirs);
		this.schema = schema == null ? SchemaLoader.load(readTransaction()) : schema;
	}

	// open

	static Database open(String filename) {
		return open(new MmapFile(filename, "rw"));
	}

	static Database openReadonly(String filename) {
		return open(new MmapFile(filename, "r"));
	}

	static Database open(Storage stor) {
		Check check = new Check(stor);
		if (! check.fastcheck()) {
			stor.close();
			return null;
		}
		return openWithoutCheck(stor);
	}

	static Database openWithoutCheck(Storage stor) {
		ByteBuffer buf = stor.buffer(-(Tran.TAIL_SIZE + 2 * Ints.BYTES));
		int adr = buf.getInt();
		DbHashTrie dbinfo = DbHashTrie.load(stor, adr, new DbinfoTranslator(stor));
		adr = buf.getInt();
		DbHashTrie redirs = DbHashTrie.from(stor, adr);
		return new Database(stor, dbinfo, redirs);
	}

	static class DbinfoTranslator implements DbHashTrie.Translator {
		final Storage stor;

		DbinfoTranslator(Storage stor) {
			this.stor = stor;
		}

		@Override
		public Entry translate(Entry e) {
			if (e instanceof IntEntry) {
				int adr = ((IntEntry) e).value;
				Record rec = Record.from(stor, adr);
				return new TableInfo(rec, adr);
			} else
				throw new RuntimeException("DbinfoTranslator bad type " + e);
		}
	}

	/** reopens with same Storage */
	@Override
	public Database reopen() {
		return Database.open(stor);
	}

	/** used by tests */
	@Override
	public Status check() {
		return DbCheck.check(stor);
	}

	private Database(Storage stor, DbHashTrie dbinfo, DbHashTrie redirs) {
		this(stor, dbinfo, redirs, null);
	}

	// used by DbCheck
	Tables schema() {
		return schema;
	}

	@Override
	public ReadTransaction readTransaction() {
		int num = trans.nextNum(true);
		return new ReadTransaction(num, this);
	}

	@Override
	public UpdateTransaction updateTransaction() {
		int num = trans.nextNum(false);
		return new UpdateTransaction(num, this);
	}

	@Override
	public ExclusiveTransaction exclusiveTran() {
		int num = trans.nextNum(false);
		return new ExclusiveTransaction(num, this);
	}

	@Override
	public TableBuilder createTable(String tableName) {
		checkForSystemTable(tableName, "create");
		return TableBuilder.create(exclusiveTran(), tableName, nextTableNum());
	}

	int nextTableNum() {
		return schema.maxTblNum + 1;
	}

	@Override
	public TableBuilder alterTable(String tableName) {
		checkForSystemTable(tableName, "alter");
		return TableBuilder.alter(exclusiveTran(), tableName);
	}

	@Override
	public TableBuilder ensureTable(String tableName) {
		checkForSystemTable(tableName, "ensure");
		return schema.get(tableName) == null
			? TableBuilder.create(exclusiveTran(), tableName, nextTableNum())
			: TableBuilder.alter(readTransaction(), tableName);
	}

	@Override
	public boolean dropTable(String tableName) {
		checkForSystemTable(tableName, "drop");
		ExclusiveTransaction t = exclusiveTran();
		try {
			return TableBuilder.dropTable(t, tableName);
		} finally {
			t.abortIfNotComplete();
		}
	}

	@Override
	public void renameTable(String from, String to) {
		checkForSystemTable(from, "rename");
		ExclusiveTransaction t = exclusiveTran();
		try {
			TableBuilder.renameTable(t, from, to);
		} finally {
			t.abortIfNotComplete();
		}
	}

	@Override
	public void addView(String name, String definition) {
		checkForSystemTable(name, "create view");
		ExclusiveTransaction t = exclusiveTran();
		try {
			if (null != Views.getView(t, name))
				throw new RuntimeException("view: '" + name + "' already exists");
			Views.addView(t, name, definition);
			t.complete();
		} finally {
			t.abortIfNotComplete();
		}
	}

	static void checkForSystemTable(String tablename, String operation) {
		if (isSystemTable(tablename))
			throw new RuntimeException("can't " + operation +
					" system table: " + tablename);
	}

	static boolean isSystemTable(String table) {
		return table.equals("tables") || table.equals("columns")
				|| table.equals("indexes") || table.equals("views");
	}

	@Override
	public void close() {
		stor.close();
	}

	@Override
	public long size() {
		return stor.sizeFrom(0);
	}

	@Override
	public String getSchema(String tableName) {
		ReadTransaction t = readTransaction();
		try {
			Table tbl = t.getTable(tableName);
			return tbl == null ? null : tbl.schema();
		} finally {
			t.complete();
		}
	}

	@Override
	public List<Integer> tranlist() {
		return trans.tranlist();
	}

	@Override
	public void limitOutstandingTransactions() {
		trans.limitOutstanding();
	}

	@Override
	public int finalSize() {
		return trans.finalSize();
	}

	@Override
	public void force() {
	}

	@Override
	public void disableTrigger(String table) {
		triggers.disableTrigger(table);
	}

	@Override
	public void enableTrigger(String table) {
		triggers.enableTrigger(table);
	}

	void callTrigger(
			ReadTransaction t, Table table, Record oldrec, Record newrec) {
		triggers.call(t, table, oldrec, newrec);
	}

	DbHashTrie getDbinfo() {
		assert dbinfo.immutable();
		return dbinfo;
	}

	void setDbinfo(DbHashTrie dbinfo) {
		assert dbinfo.immutable();
		this.dbinfo = dbinfo;
	}

	DbHashTrie getRedirs() {
		assert redirs.immutable();
		return redirs;
	}

	void setRedirs(DbHashTrie redirs) {
		assert redirs.immutable();
		this.redirs = redirs;
	}

	public void checkLock() {
		if (exclusiveLock.isWriteLocked())
			throw new RuntimeException("should not be locked");
	}

	@Override
	public void checkTransEmpty() {
		trans.checkTransEmpty();
	}

}
