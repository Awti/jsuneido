/* Copyright 2011 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido;

import static suneido.intfc.database.DatabasePackage.printObserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import com.google.common.base.Stopwatch;

import suneido.intfc.database.Database;
import suneido.intfc.database.DatabasePackage;
import suneido.intfc.database.DatabasePackage.Status;
import suneido.util.Errlog;
import suneido.util.FileUtils;
import suneido.util.Jvm;

public class DbTools {
	private static final String SEPARATOR = "!!";

	public static void dumpPrintExit(DatabasePackage dbpkg, String dbFilename,
			String outputFilename) {
		if (Status.OK != checkPrint(dbpkg, dbFilename)) {
			System.out.println("Dump ABORTED - check failed - database CORRUPT");
			System.exit(-1);
		}
		Database db = dbpkg.openReadonly(dbFilename);
		try {
			Stopwatch sw = Stopwatch.createStarted();
			int n = dumpDatabase(dbpkg, db, outputFilename);
			System.out.println("dumped " + n + " tables " +
					"from " + dbFilename + " to " + outputFilename +
					" in " + sw);
		} finally {
			db.close();
		}
	}

	public static int dumpDatabase(DatabasePackage dbpkg, Database db,
			String outputFilename) {
		try {
			@SuppressWarnings("resource")
			WritableByteChannel fout = new FileOutputStream(outputFilename).getChannel();
			try {
				return dbpkg.dumpDatabase(db, fout);
			} finally {
				fout.close();
			}
		} catch (Exception e) {
			throw new RuntimeException("dump failed", e);
		}
	}

	public static void dumpTablePrint(DatabasePackage dbpkg, String dbFilename,
			String tablename) {
		Database db = dbpkg.openReadonly(dbFilename);
		try {
			int n = dumpTable(dbpkg, db, tablename);
			System.out.println("dumped " + n + " records " +
					"from " + tablename + " to " + tablename + ".su");
		} finally {
			db.close();
		}
	}

	public static int dumpTable(DatabasePackage dbpkg, Database db,
			String tablename) {
		try {
			@SuppressWarnings("resource")
			WritableByteChannel fout = new FileOutputStream(tablename + ".su").getChannel();
			try {
				return dbpkg.dumpTable(db, tablename, fout);
			} finally {
				fout.close();
			}
		} catch (Exception e) {
			throw new RuntimeException("dump table failed", e);
		}
	}

	public static void loadDatabasePrint(DatabasePackage dbpkg, String dbFilename,
			String filename) {
		String tempfile = FileUtils.tempfile().toString();
		if (! Jvm.runWithNewJvm("-load:" + filename + SEPARATOR + tempfile))
			Errlog.fatal("Load FAILED");
		if (! Jvm.runWithNewJvm("-check:" + tempfile))
			Errlog.fatal("Load ABORTED - check failed after load");
		dbpkg.renameDbWithBackup(tempfile, dbFilename);
	}

	static void load2(DatabasePackage dbpkg, String arg) {
		int i = arg.indexOf(SEPARATOR);
		String filename = arg.substring(0, i);
		String tempfile = arg.substring(i + SEPARATOR.length());
		// FIXME: The three lines above just assume the existence of SEPARATOR.
		//        But if it's not there, i == -1 and this function throws an
		//        obscure -- in the sense of non-informational --
		//        StringIndexOutOfBoundsError...
		Database db = dbpkg.create(tempfile);
		try {
			@SuppressWarnings("resource")
			ReadableByteChannel fin = new FileInputStream(filename).getChannel();
			try {
				Stopwatch sw = Stopwatch.createStarted();
				int n = dbpkg.loadDatabase(db, fin);
				System.out.println("loaded " + n + " tables from " + filename +
						" in " + sw);
			} finally {
				fin.close();
			}
		} catch (Exception e) {
			throw new RuntimeException("load failed", e);
		} finally {
			db.close();
		}
	}

	public static void loadTablePrint(DatabasePackage dbpkg, String dbFilename,
			String tablename) {
		if (tablename.endsWith(".su"))
			tablename = tablename.substring(0, tablename.length() - 3);
		Database db = dbpkg.dbExists(dbFilename)
				? dbpkg.open(dbFilename) : dbpkg.create(dbFilename);
		if (db == null)
			throw new RuntimeException("can't open database");
		try {
			@SuppressWarnings("resource")
			ReadableByteChannel fin = new FileInputStream(tablename + ".su").getChannel();
			try {
				int n = dbpkg.loadTable(db, tablename, fin);
				System.out.println("loaded " + n + " records " +
						"from " + tablename + ".su into " + tablename + " in " + dbFilename);
			} finally {
				fin.close();
			}
		} catch (Exception e) {
			throw new RuntimeException("load " + tablename + " failed", e);
		} finally {
			db.close();
		}
	}

	public static void checkPrintExit(DatabasePackage dbpkg, String dbFilename) {
		Status status = checkPrint(dbpkg, dbFilename);
		System.exit(status == Status.OK ? 0 : -1);
	}

	public static Status checkPrint(DatabasePackage dbpkg, String dbFilename) {
		System.out.println("Checking " +
				(dbFilename.endsWith(".tmp") ? "" : dbFilename + " ") + "...");
		Stopwatch sw = Stopwatch.createStarted();
		Status result = dbpkg.check(dbFilename, printObserver);
		System.out.println("Checked in " + sw);
		return result;
	}

	public static void compactPrintExit(DatabasePackage dbpkg, String dbFilename) {
		if (! Jvm.runWithNewJvm("-check:" + dbFilename))
			Errlog.fatal("Compact ABORTED - check failed before compact - database CORRUPT");
		String tempfile = FileUtils.tempfile().toString();
		if (! Jvm.runWithNewJvm("-compact:" + dbFilename + SEPARATOR + tempfile))
			Errlog.fatal("Compact FAILED");
		if (! Jvm.runWithNewJvm("-check:" + tempfile))
			Errlog.fatal("Compact ABORTED - check failed after compact");
		dbpkg.renameDbWithBackup(tempfile, dbFilename);
	}

	static void compact2(DatabasePackage dbpkg, String arg) {
		int i = arg.indexOf(SEPARATOR);
		String dbFilename = arg.substring(0, i);
		String tempfile = arg.substring(i + SEPARATOR.length());
		Database srcdb = dbpkg.openReadonly(dbFilename);
		try {
			Database dstdb = dbpkg.create(tempfile);
			try {
				System.out.printf("size before: %,d%n", srcdb.size());
				System.out.println("Compacting...");
				Stopwatch sw = Stopwatch.createStarted();
				int n = dbpkg.compact(srcdb, dstdb);
				System.out.println("Compacted " + n + " tables in " + dbFilename +
						" in " + sw);
				System.out.printf("size after: %,d%n", dstdb.size());
			} finally {
				dstdb.close();
			}
		} finally {
			srcdb.close();
		}
	}

	public static void rebuildOrExit(DatabasePackage dbpkg, String dbFilename) {
		System.out.println("Rebuilding " + dbFilename + " ...");
		File tempfile = FileUtils.tempfile();
		String cmd = "-rebuild:" + dbFilename + SEPARATOR + tempfile;
		if (! Jvm.runWithNewJvm(cmd))
			Errlog.fatal("Rebuild FAILED " + Jvm.runWithNewJvmCmd(cmd));
		if (! tempfile.isFile())
			return; // assume db was ok, rebuild not needed
		if (! Jvm.runWithNewJvm("-check:" + tempfile))
			Errlog.fatal("Rebuild ABORTED - check failed after rebuild");
		dbpkg.renameDbWithBackup(tempfile.toString(), dbFilename);
		tempfile.delete();
	}

	/** called in the new jvm */
	static void rebuild2(DatabasePackage dbpkg, String arg) {
		int i = arg.indexOf(SEPARATOR);
		String dbFilename = arg.substring(0, i);
		String tempfile = arg.substring(i + SEPARATOR.length());
		Stopwatch sw = Stopwatch.createStarted();
		String result = dbpkg.rebuild(dbFilename, tempfile);
		if (result == null)
			System.exit(-1);
		else {
			Errlog.error("Rebuild " + dbFilename + ": " + result);
			System.out.println("Rebuild SUCCEEDED in " + sw);
		}
	}

}
