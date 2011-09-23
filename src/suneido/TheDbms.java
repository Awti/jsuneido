package suneido;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import suneido.database.server.Dbms;
import suneido.database.server.DbmsLocal;
import suneido.database.server.DbmsRemote;
import suneido.intfc.database.Database;

public class TheDbms {
	private static String ip = null;
	private static int port;
	private static DbmsLocal theDbms;
	private static ThreadLocal<DbmsRemote> remoteDbms =
		new ThreadLocal<DbmsRemote>() {
			@Override
			protected DbmsRemote initialValue() {
				DbmsRemote dbms = new DbmsRemote(ip, port);
				dbmsRemotes.add(dbms);
				dbms.sessionid(dbms.sessionid("") + ":" +
						Thread.currentThread().getName());
				return dbms;
			};
			@Override
			protected void finalize() throws Throwable {
				get().close();
			};
		};
	private static List<DbmsRemote> dbmsRemotes = new ArrayList<DbmsRemote>();

	public static Dbms dbms() {
		return (ip == null) ? theDbms : remoteDbms.get();
	}

	public static void remote(String ip, int port) {
		TheDbms.ip = ip;
		TheDbms.port = port;
	}

	public static void set(Database db) {
		theDbms = new DbmsLocal(db);
	}

	public static boolean isOpen() {
		return theDbms != null || ip != null;
	}

	/**
	 * Run regularly to close connections owned by threads that SocketServer
	 * has timed out.
	 */
	public static Runnable closer = new Runnable() {
		@Override
		public void run() {
			Iterator<DbmsRemote> iter = dbmsRemotes.iterator();
			while (iter.hasNext()) {
				DbmsRemote dbms = iter.next();
				if (! dbms.owner.isAlive())
					dbms.close();
			}
		}
	};

}
