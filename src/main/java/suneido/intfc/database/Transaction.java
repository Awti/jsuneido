/* Copyright 2011 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.intfc.database;

public interface Transaction {

	boolean isReadonly();

	boolean isReadWrite();

	boolean isEnded();

	String conflict();

	boolean tableExists(String table);

	Table ck_getTable(String tablename);

	Table getTable(String tablename);

	Table ck_getTable(int tblnum);

	Table getTable(int tblnum);

	int tableCount(int tblnum);
	long tableSize(int tblnum);
	int indexSize(int tblnum, String columns);
	int keySize(int tblnum, String columns);
	float rangefrac(int tblnum, String columns, Record from, Record to);

	void abortIfNotComplete();

	void abort();

	void ck_complete();

	/** @return null if successful, an error string if there is a conflict */
	String complete();

	String getView(String viewname);

	void addRecord(String table, Record r);

	enum Blocking { BLOCK, NO_BLOCK }

	int updateRecord(int recadr, Record rec, Blocking blocking);
	default int updateRecord(int recadr, Record rec) {
		return updateRecord(recadr, rec, Blocking.BLOCK);
	}

	int updateRecord(int tblnum, Record oldrec, Record newrec, Blocking blocking);
	default int updateRecord(int tblnum, Record oldrec, Record newrec) {
		return updateRecord(tblnum, oldrec, newrec, Blocking.BLOCK);
	}

	void removeRecord(int recadr);
	void removeRecord(int tblnum, Record rec);

	Record input(int adr);

	// used by Library
	Record lookup(int tblnum, String index, Record key);

	void callTrigger(Table table, Record oldrec, Record newrec);

	int num();

	Record fromRef(Object ref);

	HistoryIterator historyIterator(int tblnum);

	IndexIter iter(int tblnum, String columns);
	IndexIter iter(int tblnum, String columns, Record org, Record end);
	IndexIter iter(int tblnum, String columns, IndexIter iter);

	boolean isAborted();

	default int readCount() {
		return 0;
	}
	default int writeCount() {
		return 0;
	}
}
