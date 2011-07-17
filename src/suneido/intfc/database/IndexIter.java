/* Copyright 2011 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.intfc.database;

import suneido.database.Record;

public interface IndexIter {

	boolean eof();

	Record curKey();

	long keyadr();

	void next();

	void prev();

}