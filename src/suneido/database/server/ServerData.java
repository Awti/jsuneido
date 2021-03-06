/* Copyright 2008 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.server;

import static suneido.util.Verify.verify;

import java.io.Closeable;
import java.util.*;

import suneido.util.Errlog;
import suneido.util.NotThreadSafe;

/**
 * Each connection/session has its own ServerData instance
 */
@NotThreadSafe
public class ServerData {
	private int next = 0;
	private final Map<Integer, DbmsTran> trans = new HashMap<>();
	private final Map<Integer, List<Integer>> tranqueries = new HashMap<>();
	private final Map<Integer, DbmsQuery> queries = new HashMap<>();
	private final Map<Integer, DbmsQuery> cursors = new HashMap<>();
	private final Map<String, String> sviews = new HashMap<>();
	private final Stack<String> viewnest = new Stack<>();
	private String sessionId = "127.0.0.1";
	public final Closeable connection; // for kill
	public boolean textmode = true;
	private byte[] nonce = null;
	public boolean auth;

	/** for tests */
	public ServerData() {
		this.connection = null;
		this.auth = true;
	}

	public ServerData(Closeable connection) {
		this.connection = connection;
		auth = Auth.initialValue();
	}

	/**
	 * this is set by {@link DbmsServer} since it is per connection,
	 * not really per thread, initialValue is for tests
	 */
	public static final ThreadLocal<ServerData> threadLocal =
			ThreadLocal.withInitial(ServerData::new);
	public static ServerData forThread() {
		return threadLocal.get();
	}

	public int addTransaction(DbmsTran tran) {
		int num = ((DbmsTranLocal) tran).num();
		trans.put(num, tran);
		tranqueries.put(num, new ArrayList<Integer>());
		return num;
	}

	public void endTransaction(int tn) {
		for (Integer qn : tranqueries.get(tn)) {
			DbmsQuery q = getQuery(qn);
			if (q != null)
				q.close();
			queries.remove(qn);
		}
		Errlog.verify(trans.remove(tn) != null,
				"ServerData.endTransaction missing from trans");
		if (trans.isEmpty())
			verify(queries.isEmpty());
	}

	public int addQuery(int tn, DbmsQuery q) {
		queries.put(next, q);
		tranqueries.get(tn).add(next);
		return next++;
	}

	public void endQuery(int qn) {
		var q = getQuery(qn);
		if (q != null)
			q.close();
		queries.remove(qn);
	}

	public int addCursor(DbmsQuery q) {
		verify(q != null);
		cursors.put(next, q);
		return next++;
	}

	public void endCursor(int qn) {
		var c = getCursor(qn);
		if (c != null)
			c.close();
		cursors.remove(qn);
	}

	public DbmsTran getTransaction(int tn) {
		return trans.get(tn);
	}

	public DbmsQuery getQuery(int qn) {
		return queries.get(qn);
	}

	public DbmsQuery getCursor(int cn) {
		return cursors.get(cn);
	}

	public boolean isEmpty() {
		return trans.isEmpty();
	}

	public void addSview(String name, String definition) {
		sviews.put(name, definition);
	}
	public String getSview(String name) {
		return sviews.get(name);
	}
	public void dropSview(String name) {
		sviews.remove(name);
	}

	public void enterView(String name) {
		viewnest.push(name);
	}
	public boolean inView(String name) {
		return -1 != viewnest.search(name);
	}
	public void leaveView(String name) {
		assert viewnest.peek().equals(name);
		viewnest.pop();
	}

	public int cursorsSize() {
		return cursors.size();
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public void end() {
		for (Map.Entry<Integer, DbmsTran> e : trans.entrySet())
			Errlog.run(e.getValue()::abort);
	}

	public void setNonce(byte[] nonce) {
		this.nonce = nonce;
	}

	/** @return the current nonce and clear it */
	public byte[] getNonce() {
		byte[] result = nonce;
		nonce = null;
		return result;
	}

}
