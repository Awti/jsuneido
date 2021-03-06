/* Copyright 2009 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.runtime.builtin;

import suneido.Suneido;
import suneido.TheDbms;
import suneido.runtime.Ops;
import suneido.runtime.Params;

public class Use {

	@Params("library")
	public static Boolean Use(Object a) {
		if (! TheDbms.dbms().use(Ops.toStr(a)))
				return false;
		Suneido.context.clearAll();
		return true;
	}

}
