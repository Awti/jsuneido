/* Copyright 2009 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.runtime.builtin;

import static org.junit.Assert.assertEquals;
import static suneido.compiler.Compiler.eval;
import static suneido.runtime.Ops.display;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import suneido.SuObject;
import suneido.runtime.Ops;

public class ContainerMethodsTest {

	@Before
	public void setQuoting() {
		Ops.default_single_quotes = true;
	}

	@After
	public void restoreQuoting() {
		Ops.default_single_quotes = false;
	}

	@Test
	public void test_add() {
		test("[].Add(123)", "[123]");
		test("[].Add(123)", "[123]");
		test("[].Add(1,2,3)", "[1, 2, 3]");
		test("[1,2].Add(3,4)", "#(1, 2, 3, 4)");
		test("[1,2,3].Add(11,22,at: 1)", "#(1, 11, 22, 2, 3)");
		test("[].Add(123, at: 'a')", "[a: 123]");
		test("[].Add(1, 2, 3, at: 0)", "[1, 2, 3]");
		test("[].Add(@#(1, 2, 3, at: 0))", "[1, 2, 3]");
	}
	@Test
	public void test_list_named() {
		test("#(11, 22, a: 33, b: 44, c: 55).Size()", "5");
		test("#(11, 22, a: 33, b: 44, c: 55).Size(list:)", "2");
		test("#(11, 22, a: 33, b: 44, c: 55).Size(named:)", "3");

		test("#(11, 22, a: 33, b: 44, c: 55).Members().Sort!()",
				"#(0, 1, 'a', 'b', 'c')");
		test("#(11, 22, a: 33, b: 44, c: 55).Members(list:)",
				"#(0, 1)");
		test("#(11, 22, a: 33, b: 44, c: 55).Members(named:).Sort!()",
				"#('a', 'b', 'c')");

		test("#(11, 22, a: 33, b: 44, c: 55).Values().Sort!()", "#(11, 22, 33, 44, 55)");
		test("#(11, 22, a: 33, b: 44, c: 55).Values(list:)", "#(11, 22)");
		test("#(11, 22, a: 33, b: 44, c: 55).Values(named:).Sort!()", "#(33, 44, 55)");

		test("#(11, 22, a: 33, b: 44, c: 55).Assocs().Sort!()",
				"#(#(0, 11), #(1, 22), #('a', 33), #('b', 44), #('c', 55))");
		test("#(11, 22, a: 33, b: 44, c: 55).Assocs(list:)",
				"#(#(0, 11), #(1, 22))");
		test("#(11, 22, a: 33, b: 44, c: 55).Assocs(named:).Sort!()",
				"#(#('a', 33), #('b', 44), #('c', 55))");
	}
	@Test
	public void test_find() {
		test("#(11, 22, a: 33, b: 44).Find(123)", "false");
		test("#(11, 22, a: 33, b: 44).Find(22)", "1");
		test("#(11, 22, a: 33, b: 44).Find(33)", "'a'");

	}

	public static void test(String expr, String result) {
		assertEquals(result, display(eval(expr)));
	}

	@Test
	public void test_join() {
		testjoin("", "");
		testjoin("", "<>");
		testjoin("", "", "", "", "");
		testjoin("abc", "", "abc");
		testjoin("abc", "<>", "abc");
		testjoin("123", "", 1, 2, 3);
		testjoin("1.2.3", ".", 1, 2, 3);
		testjoin("1.two.3", ".", 1, "two", 3);
		testjoin("1<>2<>3", "<>", 1, 2, 3);
	}

	private static void testjoin(String result, String sep, Object... values) {
		SuObject c = new SuObject(Arrays.asList(values));
		assertEquals(result, ObjectMethods.lookup("Join").eval1(c, sep));
	}

	@Test
	public void test_unique() {
		test("#().Unique!()", "#()");
		test("#(1, 2, 3).Unique!()", "#(1, 2, 3)");
		test("#(1, 1).Unique!()", "#(1)");
		test("#(1, 1, 1, 1).Unique!()", "#(1)");
		test("#(1, 1, 2, 3).Unique!()", "#(1, 2, 3)");
		test("#(1, 2, 2, 3).Unique!()", "#(1, 2, 3)");
		test("#(1, 2, 3, 3).Unique!()", "#(1, 2, 3)");
	}

	@Test
	public void test_iter() {
		test("Type(#().Iter())", "'ObjectIter'");
	}
}
