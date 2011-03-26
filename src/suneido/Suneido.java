/* Copyright 2010 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;

import suneido.database.server.DbmsServer;
import suneido.database.tools.*;
import suneido.language.Compiler;
import suneido.util.Print;

public class Suneido {
	public static final ScheduledExecutorService scheduler
			= Executors.newSingleThreadScheduledExecutor();
	public static CommandLineOptions cmdlineoptions;

	public static void main(String[] args) {
		ClassLoader.getSystemClassLoader().setPackageAssertionStatus("suneido", true);
		cmdlineoptions = CommandLineOptions.parse(args);
		try {
			doAction();
		} catch (Throwable e) {
			fatal(cmdlineoptions.action + " FAILED", e);
		}
	}

	public static void fatal(String s) {
		errlog("FATAL: " + s);
		System.exit(-1);
	}

	public static void fatal(String s, Throwable e) {
		errlog("FATAL: " + s + ": " + e, e);
		System.exit(-1);
	}

	public static void uncaught(String s, Throwable e) {
		errlog("UNCAUGHT: " + s + ": " + e, e);
	}

	public synchronized static void errlog(String s) {
		errlog(s, null);
	}

	public synchronized static void errlog(String s, Throwable err) {
		System.out.println(s);
		try {
			FileWriter fw = new FileWriter("error.log", true);
			fw.append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
			fw.append(" ");
			fw.append(s);
			fw.append("\n");
			if (err != null)
				err.printStackTrace(new PrintWriter(fw));
			fw.close();
		} catch (IOException e) {
			System.out.println("can't write to error.log " + e);
		}
	}

	private static void doAction() throws Throwable {
		switch (cmdlineoptions.action) {
		case REPL:
			Repl.repl();
			break;
		case SERVER:
			if (! System.getProperty("java.vm.name").contains("Server VM"))
				System.out.println("WARNING: Server VM is recommended");
			Print.timestamped("starting server");
			HttpServerMonitor.run(cmdlineoptions.serverPort + 1);
			DbmsServer.run(cmdlineoptions.serverPort, cmdlineoptions.timeoutMin);
			break;
		case CLIENT:
			TheDbms.remote(cmdlineoptions.actionArg, cmdlineoptions.serverPort);
			scheduleAtFixedRate(TheDbms.closer, 30, TimeUnit.SECONDS);
			Compiler.eval("JInit()");
			Repl.repl();
			scheduler.shutdown();
			break;
		case DUMP:
			if (cmdlineoptions.actionArg == null)
				DbDump.dumpDatabasePrint("suneido.db", "database.su");
			else
				DbDump.dumpTablePrint(cmdlineoptions.actionArg);
			break;
		case LOAD:
			if (cmdlineoptions.actionArg != null)
				DbLoad.loadTablePrint(cmdlineoptions.actionArg);
			else
				DbLoad.loadDatabasePrint("database.su", "suneido.db");
			break;
		case LOAD2:
			DbLoad.load2("database.su", cmdlineoptions.actionArg);
			break;
		case CHECK:
			DbCheck.checkPrintExit("suneido.db");
			break;
		case REBUILD:
			DbRebuild.rebuildOrExit("suneido.db");
			break;
		case REBUILD2:
			DbRebuild.rebuild2("suneido.db", cmdlineoptions.actionArg);
			break;
		case COMPACT:
			DbCompact.compactPrint("suneido.db");
			break;
		case COMPACT2:
			DbCompact.compact2("suneido.db", cmdlineoptions.actionArg);
			break;
		case VERSION:
			System.out.println("jSuneido " + WhenBuilt.when());
			System.out.println("Java " + System.getProperty("java.version")
					+ System.getProperty("java.vm.name").replace("Java", ""));
			break;
		case ERROR:
			System.out.println(cmdlineoptions.actionArg);
			System.out.println();
			// fall through
		case HELP:
			printHelp();
			break;
		default:
			throw SuException.unreachable();
		}
	}

	private static void printHelp() {
		System.out.println("usage: [options] [--] [arguments]");
		System.out.println("options:");
		System.out.println("-s[erver]               start the server (this is the default option)");
		System.out.println("-c[lient]               run as client");
		System.out.println("-p[ort] #               the TCP/IP port for server or client (default 3147)");
		System.out.println("-repl                   interactive read-eval-print-loop command line interface");
		System.out.println("-d[ump] [table]         dump to database.su or <table> to <table>.su");
		System.out.println("-l[oad] [table]         load from database.su or <table> from <table>.su");
		System.out.println("-check                  check the database integrity");
		System.out.println("-rebuild                check and rebuild the database, i.e. for crash recovery");
		System.out.println("-compact                remove deleted records");
		System.out.println("-t[ests[                run the built-in JUnit tests");
		System.out.println("-v[ersion]              print the version");
		System.out.println("-i[mpersonate] version  tell clients this version");
		System.out.println("-h[elp] or -?           print this message");
		System.out.println("--                      end the options, useful if arguments start with '-'");
	}

	public static void schedule(Runnable fn, long delay, TimeUnit unit) {
		scheduler.schedule(fn, delay, unit);
	}

	public static void scheduleAtFixedRate(Runnable fn, long delay, TimeUnit unit) {
		scheduler.scheduleAtFixedRate(fn, delay, delay, unit);
	}

}
