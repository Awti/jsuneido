/* Copyright 2009 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static suneido.compiler.Compiler.compile;
import static suneido.compiler.Compiler.eval;
import static suneido.util.testing.Throwing.assertThrew;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import suneido.PortTests;
import suneido.SuException;
import suneido.SuObject;
import suneido.Suneido;
import suneido.runtime.*;

public class ExecuteTest {

	@Before
	public void setQuoting() {
		Ops.default_single_quotes = true;
	}

	@After
	public void restoreQuoting() {
		Ops.default_single_quotes = false;
	}

	@Test public void tests() {
		test("123 + 456", "579");
		test("'hello' $ ' ' $ 'world'", "'hello world'");
		test("1 + 2 * 3", "7");
		test("a = 2; -a", "-2");
		test("a = true; not a", "false");
		test("'hello'.Size()", "5");
		test("'hello'.Substr(3, 2)", "'lo'");
		test("x = 123; y = 456; return x + y", "579");
		test("f = function (x, y) { x + y }; f(123, 456)", "579");
		test("'hello world'.Size()", "11");
		test("s = 'hello'; s.Substr(s.Size() - 2, 99)", "'lo'");
		test("f = function (@x) { x }; f()", "#()");
		test("f = function (adv?) { adv? }; f(adv?:)", "true");
		test("f = function (@args) { args.adv! }; f(adv!: false)", "false");
		test("f = function (a, b, c = 3, d = 4) { [a,b,c,d] }; f(1, 2, d: 9)",
				"[1, 2, 3, 9]");
	}
	@Test public void test_bigdecimal_is() {
		test("1000 is 1e3", "true");
		test("1e3 is 1000", "true");
		test("10e3 is 1e4", "true");
	}
	@Test public void test_incdec() {
		test("f = function (@x) { x.a = 0; ++x.a }; f()", "1");
		test("f = function (@x) { x.a = 0; x.a++ }; f()", "0");
		test("f = function (@x) { x.a = 0; x.a++; x.a }; f()", "1");
		test("f = function (@x) { x.a = 0; --x.a }; f()", "-1");
		test("f = function (@x) { x.a = 0; x.a-- }; f()", "0");
		test("f = function (@x) { x.a = 0; x.a--; x.a }; f()", "-1");

		test("f = function (@x) { x[0] = 0; ++x[0] }; f()", "1");
		test("f = function (@x) { x[0] = 0; x[0]++ }; f()", "0");
		test("f = function (@x) { x[0] = 0; x[0]++; x[0] }; f()", "1");
		test("f = function (@x) { x[0] = 0; --x[0] }; f()", "-1");
		test("f = function (@x) { x[0] = 0; x[0]-- }; f()", "0");
		test("f = function (@x) { x[0] = 0; x[0]--; x[0] }; f()", "-1");
	}
	@Test public void test_args() {
		test("f = function (@x) { x }; f(1, a: 2)", "#(1, a: 2)");
		test("f = function (@x) { x }; f(a: 1, b: 2)", "#(b: 2, a: 1)");
		test("f = function (@x) { x }; f(a: 1, b: 2, c: 3, d: 4, e: 5, f: 6"
				+ "g: 7, h: 8, i: 9, j: 10, k: 11)",
				"#(f: 6, g: 7, d: 4, e: 5, b: 2, c: 3, a: 1, j: 10, k: 11, h: 8, i: 9)");
	}
	@Test public void test_and() {
		test("true and false", "false");
		test("false and true", "false");
		test("true and true", "true");
	}
	@Test public void test_or() {
		test("true or false", "true");
		test("false or true", "true");
		test("false or false", "false");
	}
	@Test public void test_conditional() {
		test("true ? 123 : 456", "123");
		test("false ? 123 : 456", "456");
		test("true ? x = 1 : 2", "1");
		test("true ? x = 1 : x = 2", "1");
		test("true ? x = 1 : x = 2; x", "1");
	}
	@Test public void test_if() {
		test("if (true) x = 1; else x = 2; x", "1");
		test("if (false) x = 1; else x = 2; x", "2");
		test("if (true) x = 1; else x = 2; x", "1");
		test("if (x = true) y = 123; else y = 456; y", "123");
	}
	@Test public void test_lt() {
		test("2 < 3", "true");
		test("3 < 2", "false");
		test("2 < 2", "false");
		test("1.1 < 1.2", "true");
		test("1.2 < 1.1", "false");
		test("[99] < [.5]", "false");
		test("#{99} < #(.5)", "false");
		test("#{.5} < #(99)", "true");
	}
	@Test public void test_lte() {
		test("2 <= 3", "true");
		test("3 <= 2", "false");
		test("2 <= 2", "true");
	}
	@Test public void test_gt() {
		test("2 > 3", "false");
		test("3 > 2", "true");
		test("2 > 2", "false");
	}
	@Test public void test_gte() {
		test("2 >= 3", "false");
		test("3 >= 2", "true");
		test("2 >= 2", "true");
	}
	@Test public void test_while() {
		test("i = 0; while (i < 5) i += 2; i", "6");
		test("i = 6; while (i < 5) i += 2; i", "6");
	}
	@Test public void test_do_while() {
		test("i = 0; do i += 2; while (i < 5); i", "6");
		test("i = 6; do i += 2; while (i < 5); i", "8");
	}
	@Test public void test_assignOpOrder() {
		test("s = 1; s $= 2", "'12'");
		test("n = 10; n -= 5", "5");
	}
	@Test public void test_forever_break() {
		test("i = 0; forever { ++i; if (i > 4) break }; i", "5");
	}
	@Test public void test_for_classic() {
		test("for (i = 0; i < 5; ++i) ; i", "5");
		test("i = 0; for (; i < 5; ++i) ; i", "5");
		test("for (i = 0; i < 5; ) ++i; i", "5");
		test("for (i = 0; ; ++i) if (i > 5) break; i", "6");
	}
	@Test public void test_switch() {
		test("x=''; switch(1) { case 0: ; case 1: x='one'; }; x", "'one'");
		test("x=''; switch(1) { case 0,1,2: x='one'; }; x", "'one'");
		test("x=''; switch(2) { case 1: x='one'; default: x='def' }; x", "'def'");
	}
	@Test public void test_block() {
		test("b = { 123 }; b()", "123");
		test("a = 123; b = { a + 456 }; b()", "579");
		test("b = {|x| x * 2 }; b(123)", "246");
		test("x = 111; b = {|x| x * 2 }; b(123) + x", "357");
		test("b = { 2 * it }; b(123)", "246");
		test("b = {|it| 2 * it }; b(123)", "246");
		test("it = 111; b = {|it| 2 * it }; b(123) + it", "357");
		test("function () { b = { return 123 }; b(); 456 }()", "123");
		test("function () { b = { do { return 123 } while(false) }; b(); 456 }()",
				"123");
		test("function () { b = { return 123 }; do { b() } while(false); 456 }()",
				"123");
		test("run = function (block) { block() }; run() { run() { return 123 } }; 456", "123");

		test("b = { break }; try b() catch (e) return e", "'block:break'");
		test("b = { continue }; try b() catch (e) return e", "'block:continue'");

		test("b = { forever break; 123 }; b()", "123");
		test("b1 = {|f| this; b2 = { f }; b2() }; b1(123)", "123");
	}
	@Test public void test_exceptions() {
		test("try return 123", "123");
		test("try return 123 catch ;", "123");
		test("try throw 'abc' catch (e) return e", "'abc'");
		test("try throw 'x' catch (e) { return e } return 'y'", "'x'");
		test("try { try throw 'x' catch (e) return e } return 'y'", "'x'");
		test("try { " +
				"try Object() catch (e) return 'y'; " +
				"throw 'x' } catch(e) return e; " +
				"return 'y'", "'x'");
		blockReturn("f = function () { return { return 123 } }; b = f(); b()");

		test("try throw 'abc' catch (e) return Type(e)", "'Except'");
		test("try throw 'abc' catch (e) return e.As('def')", "'def'");
		test("try { try throw 'abc' catch (e) throw e.As('def') } " +
				"catch (x) return x", "'def'");
		test("c = 0;" +
				"try { try throw 'abc' catch (e) { c = e.Callstack(); throw e.As('def') } } " +
				"catch (x) { return c is x.Callstack() }", "true");
		test("try NullPointerException() " +
				"catch (e) return e.Has?('java.lang.NullPointerException')", "true");
		test("try AssertionError() " +
				"catch (e) return e.Has?('java.lang.AssertionError')", "true");
	}
	private static void blockReturn(String expr) {
		try {
			eval(expr);
			fail();
		} catch (BlockReturnException e) {
			// expected
		}
	}

	@Test public void test_nested_class() {
		testDisp("c = class { }; new c", "eval$c()");
		test("c = class { F() { 123 } }; c.F()", "123");
	}

	@Test public void test_function_in_object() {
		test("x = #(F: function (n) { n + 1 }); (x.F)(123)", "124");
	}

	@Test
	public void test_eval() {
		test("#(1).Eval(function () { this })", "#(1)");
		test("#(A: 123).Eval(function () { .A })", "123");
		test("#(a: 123).Eval(function () { .a })", "123");

		def("F", "function () { b = { .a }; b() }");
		test("[a: 123].Eval(F)", "123");

		test("#(1).Eval({ this })", "#(1)");
		test("#(a: 123).Eval({ .a })", "123");
	}

	@Test
	public void test_display() {
		def("F", "function () { }");
		testDisp("F", "F /* function */");

		def("C", "class { }");
		testDisp("C", "C /* class */");
		testDisp("C()", "C()");

		def("C", "class { M() { } }");
		testDisp("C.M", "C.M /* method */");

		testDisp("Pack", "Pack /* builtin function */");
		testDisp("SocketServer", "SocketServer /* builtin class */");

		libdef("G", "function () { }");
		testDisp("G", "G /* lib function */");

		libdef("C", "class { M() { } }");
		testDisp("C", "C /* lib class */");
		testDisp("C()", "C()");									// ???
		testDisp("C.M", "C.M /* lib method */");
		testDisp("C().M", "C.M /* lib method */");				// ???

		testDisp("b = { }", "/* block */");
		testDisp("x = 1; b = { x }", "/* block */");

		testDisp("class { }", "/* class */");

		testDisp("class { M() { } }.M", "/* method M */");		// ???
	}

	@Test
	public void test_params() {
		test("f = function () { }; f.Params()", "'()'");
		test("f = function (a, b = 0) { }; f.Params()", "'(a,b=0)'");
		test("c = class { F() { } }; c.F.Params()", "'()'");
	}

	@Test
	public void test_string_range() {
		test("'abcd'[1 .. 3]", "'bc'");
		test("'abcd'[1 .. 9]", "'bcd'");
		test("'abcd'[1 ..]", "'bcd'");
		test("'abcd'[6 .. 9]", "''");
		test("'abcd'[2 .. 1]", "''");
		test("'abcd'[-3 .. -1]", "'bc'");
		test("'abcd'[1 .. -1]", "'bc'");
		test("'abcd'[.. -2]", "'ab'");
		test("'abcd'[-2 ..]", "'cd'");

		test("'abcd'[1 :: 2]", "'bc'");
		test("'abcd'[:: 2]", "'ab'");
		test("'abcd'[-2 :: 1]", "'c'");
		test("'abcd'[1 :: -1]", "''");
		test("'abcd'[1 :: 9]", "'bcd'");
		test("'abcd'[1 ::]", "'bcd'");
		test("'abcd'[9 :: 1]", "''");
	}

	@Test
	public void test_object_range() {
		test("#(a, b, c, d)[1 .. 3]", "#('b', 'c')");
		test("#(a, b, c, d)[1 .. 9]", "#('b', 'c', 'd')");
		test("#(a, b, c, d)[1 ..]", "#('b', 'c', 'd')");
		test("#(a, b, c, d)[6 .. 9]", "#()");
		test("#(a, b, c, d)[2 .. 1]", "#()");
		test("#(a, b, c, d)[-3 .. -1]", "#('b', 'c')");
		test("#(a, b, c, d)[1 .. -1]", "#('b', 'c')");
		test("#(a, b, c, d)[.. -2]", "#('a', 'b')");
		test("#(a, b, c, d)[-2 ..]", "#('c', 'd')");

		test("#(a, b, c, d)[1 :: 2]", "#('b', 'c')");
		test("#(a, b, c, d)[:: 2]", "#('a', 'b')");
		test("#(a, b, c, d)[-2 :: 1]", "#('c')");
		test("#(a, b, c, d)[1 :: -1]", "#()");
		test("#(a, b, c, d)[1 :: 9]", "#('b', 'c', 'd')");
		test("#(a, b, c, d)[1 ::]", "#('b', 'c', 'd')");
		test("#(a, b, c, d)[9 :: 1]", "#()");
	}

	@Test
	public void test_dot_parameters() {
		test("c = class { New(.P) { x = p } A() { .P } }; i = c(123); i.A()", "123");
		test("c = class { New(.p) { } A() { .p } }; i = c(123); i.A()", "123");
		test("c = class { New(a, b, .p, .q = 4) { } A() { [.p, .q] } }; " +
				"i = c(1, 2, 3); i.A()", "[3, 4]");
		test("c = class { New(a, b, .p, .q = 4) { } A() { [.p, .q] } }; " +
				"i = c(1, 2, 33, 44); i.A()", "[33, 44]");
	}

	@Test
	public void test_dynamic_implicit_parameters() {
		test("function(_p){ p }(123)", "123");
		test("function(_p){ p }(p: 123)", "123");
		test("try function(_p){ p }() catch (e) return e", "'missing argument(s)'");
		test("_p = 123; function(_p){ p }()", "123");
		test("function(_p = 0){ p }()", "0");
		test("function(_p = 0){ p }()", "0");
		test("function(_p = 0){ p }(123)", "123");
		test("function(_p = 0){ p }(p: 123)", "123");
		test("_p = 123; function(_p = 0){ p }()", "123");
		test("c = class { New(._p) { } A() { .p } }; _p = 123; i = c(); i.A()", "123");
		test("c = class { New(._P) { } A() { .P } }; _p = 123; i = c(); i.A()", "123");
	}

	@Test
	public void test_call_string() {
		test("#Size('abc')", "3");
		test("x = #(abc); #Size(@x)", "3");
		test("x = #(#(a b c), ' '); #Size(#Join(@x))", "5");
		test("x = #(0, (a b c), ':'); #Size(#Join(@+1x))", "5");
		needsThis("#Size(x: 10)");
		needsThis("#Size()");
		needsThis("x = #(a, z: 4); #Size(@+1 x)");
	}

	@Test
	public void test_eval_args_each() {
		test("fn = function(a = false) { return a };" +
				"args = Object(fn, a:);" +
				"Object().Eval(@args)", "true");
	}

	private static void needsThis(String expr) {
		assertThrew(() -> eval(expr), SuException.class,
				"string call requires 'this' argument");
	}

	public static void def(String name, String source) {
		Suneido.context.set(name, Compiler.compile(name, source));
	}

	public static void libdef(String name, String source) {
		Suneido.context.set(name,
				Compiler.compile("lib", name, source, Suneido.context));
	}

	public static void test(String expr, String expected) {
		Object result = eval(expr);
		if (result instanceof SuBoundMethod)
			result = ((SuBoundMethod) result).method();
		Object exptd = eval(expected);
		assert Ops.is_(exptd, result) : "expected: " + expected +
				" but was " + Ops.display(result);
	}

	public static void testDisp(String expr, String expected) {
		assertEquals(expected, Ops.display(eval(expr)));
	}

	/**
	 * PortTests fixture.
	 */
	public static boolean pt_execute(String... args) {
		if (args[0].contains("Name(foo)") || args[0].contains("Name(c.Foo)")) {
			System.out.println("SKIP " + Arrays.toString(args));
			return true;
		}
		//System.out.println(Arrays.toString(args));
		Ops.default_single_quotes = true;
		try {
			String expected = "**notfalse**";
			if (args.length > 1)
				expected = args[1];
			Object result;
			boolean ok;
			if (expected.equals("throws")) {
				if (args[2].contains("super requires parent")) {
					System.out.println("SKIP " + Arrays.toString(args));
					return true;
				}
				expected = "throws: " + args[2];
				try {
					result = Ops.display(eval(args[0]));
					ok = false;
				} catch (RuntimeException e) {
					result = e.getMessage();
					ok = e.getMessage().contains(args[2]);
				}
			} else if (expected.equals("**notfalse**")) {
				result = eval(args[0]);
				ok = result != Boolean.FALSE;
			} else if (expected.equals("nil")) {
				result = eval(args[0]);
				ok = result == null;
			} else {
				result = eval(args[0]);
			 	Object expectedOb = compile(expected);
				ok = Ops.is(result, expectedOb);
			}
			if (! ok) {
				System.out.println("got: " + Ops.display(result));
				System.out.println("expected: " + expected);
			}
			return ok;
		} catch (Throwable e) {
			System.out.println("threw: " + e);
			return false;
		} finally {
			Ops.default_single_quotes = false;
		}
	}

	/**
	 * PortTests fixture.
	 * Test [from..to] for both strings and containers.
	 */
	public static boolean pt_lang_rangeto(String... args) {
		String s = args[0];
		int from = Integer.parseInt(args[1]);
		int to = Integer.parseInt(args[2]);
		String expected = args[3];
		if (!Ops.rangeTo(s, from, to).equals(expected))
			return false;
		SuObject list = stringToCharList(s);
		SuObject expectedList = stringToCharList(expected);
		return Ops.rangeTo(list, from, to).equals(expectedList);
	}

	/**
	 * PortTests fixture.
	 * Test [from::len] for both strings and containers.
	 */
	public static boolean pt_lang_rangelen(String... args) {
		String s = args[0];
		int i = Integer.parseInt(args[1]);
		int n = args.length == 4 ? Integer.parseInt(args[2]) : 9999;
		String expected = args[args.length - 1];

		if (!Ops.rangeLen(s, i, n).equals(expected))
			return false;

		SuObject list = stringToCharList(s);
		SuObject expectedList = stringToCharList(expected);
		return Ops.rangeLen(list, i, n).equals(expectedList);
	}

	private static SuObject stringToCharList(String s) {
		char[] ca = s.toCharArray();
		SuObject list = new SuObject();
		for (char c : ca)
			list.add(String.valueOf(c));
		return list;
	}

	/**
	 * PortTests fixture.
	 * Calls a method on a literal with specified arguments.
	 * i.e. object, MethodName, arg ..., expected
	 */
	public static boolean pt_method(boolean[] str, String... args) {
		Object ob = toValue(str, args, 0);
		String method = args[1];
		Object expected = toValue(str, args, args.length - 1);
		Object[] argvals = new Object[args.length - 3];
		for (int i = 0; i < argvals.length; ++i)
			argvals[i] = toValue(str, args, 2 + i);
		Object result = Ops.invoke(ob, method, argvals);
		boolean ok = Ops.is_(result, expected);
		if (!ok)
			System.out.println("\tgot: " + Ops.display(result));
		return ok;
	}

	public static Object toValue(boolean[] str, String[] args, int i) {
		return str[i] ? args[i] : compile(args[i]);
	}

	@Test
	public void porttests() {
		Suneido.context.set("Def", Builtins.function(Def.class));
		PortTests.addTest("execute", ExecuteTest::pt_execute);
		PortTests.addTest("lang_rangeto", ExecuteTest::pt_lang_rangeto);
		PortTests.addTest("lang_rangelen", ExecuteTest::pt_lang_rangelen);
		PortTests.addTest2("method", ExecuteTest::pt_method);
		assert PortTests.runFile("execute.test");
		assert PortTests.runFile("execute2.test");
		assert PortTests.runFile("class.test");
		assert PortTests.runFile("strings.test");
		assert PortTests.runFile("objects.test");
		System.out.println("PASS");
	}

	public static class Def {
		@Params("name, value")
		public static Object Def(Object nameOb, Object value) {
			var name = Ops.toStr(nameOb);
			if (value instanceof String) {
				value = Compiler.compile(name, (String) value);
			}
			Suneido.context.set(name, value);
			return null;
		}
	}

}
