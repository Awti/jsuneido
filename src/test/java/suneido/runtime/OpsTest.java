/* Copyright 2009 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.runtime;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static suneido.runtime.Numbers.INF;
import static suneido.runtime.Numbers.MC;
import static suneido.runtime.Numbers.MINUS_INF;
import static suneido.runtime.Numbers.ZERO;
import static suneido.runtime.Ops.*;

import java.math.BigDecimal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Strings;

import suneido.SuException;
import suneido.compiler.ExecuteTest;

public class OpsTest {

	@Before
	public void setQuoting() {
		Ops.default_single_quotes = true;
	}

	@After
	public void restoreQuoting() {
		Ops.default_single_quotes = false;
	}

	/** @see ExecuteTest test_display */
	@Test
	public void test_display() {
		assertThat(Ops.display("hello"), equalTo("'hello'"));
		assertThat(Ops.display("hello\000"), equalTo("'hello\\x00'"));
		assertThat(Ops.display(new Concats("hello", "world")), equalTo("'helloworld'"));
	}

	@Test
	public void test_is() {
		assertFalse(Ops.is(null, 123));
		assertFalse(Ops.is(123, null));
		is(null, null);
		is(123, 123);
		is((long) 123, 123);
		is((byte) 123, (long) 123);
		is(1.0F, 1.0D);
		is((byte) 1, 1.0D);
		is(1.0F, BigDecimal.valueOf(1));
		is(123, BigDecimal.valueOf(123));
		is("hello", "hello");
		is("hello", new Concats("hel", "lo"));
		is("hello", new Except("hello", null));
	}
	private static void is(Object x, Object y) {
		assertTrue(Ops.is(x, y));
		assertTrue(Ops.is(y, x));
		assertTrue(Ops.cmp(x, y) == 0);
		assertTrue(Ops.cmp(y, x) == 0);
	}

	@Test
	public void test_cmp() {
		lt(false, true);
		lt(true, 123);
		lt(123, 456);
		lt(123, "def");
		lt("abc", "def");
		lt(1, BigDecimal.valueOf(1.1));
		lt(BigDecimal.valueOf(.9), 1);
		lt(BigDecimal.valueOf(.9), 1);
		lt(1, BigDecimal.valueOf(1.1));
		lt(123, this);
		lt("hello", this);
	}
	private static void lt(Object x, Object y) {
		assertTrue(Ops.cmp(x, y) < 0);
		assertTrue(Ops.cmp(y, x) > 0);
		assertFalse(Ops.is(x, y));
		assertFalse(Ops.is(y, x));
	}

	@Test
	public void test_cat() {
		assertEquals("onetwo", cat("one", "two"));
		assertEquals("one.2", cat("one", BigDecimal.valueOf(.2)));
		assertEquals("1two", cat(1, "two"));
	}

	@Test
	public void test_cat2() {
		Object x = Ops.cat("hello", "world");
		assertTrue(x instanceof String);
		assertEquals("helloworld", x);

		String s = Strings.repeat("helloworld", 30);
		x = Ops.cat(s, ".");
		assertTrue(x instanceof String2);
		assertEquals(s + ".", x.toString());

		x = Ops.cat(x, ">");
		assertTrue(x instanceof String2);
		assertEquals(s + "." + ">", x.toString());
		}

	private static final Object x = 123;
	private static final Object y = 456;
	private static final Object z = BigDecimal.valueOf(.9);

	@Test
	public void test_add() {
		assertEquals(579, add(x, y));
		assertEquals(new BigDecimal("456.9"), add(z, y));
		assertEquals(new BigDecimal("123.9"), add(x, z));
		assertEquals(new BigDecimal("1.8"), add(z, z));

		assertEquals(INF, add(INF, x));
		assertEquals(INF, add(x, INF));
		assertEquals(INF, add(INF, z));
		assertEquals(INF, add(z, INF));
		assertEquals(INF, add(INF, INF));
		assertEquals(0, add(INF, MINUS_INF));

		assertEquals(MINUS_INF, add(MINUS_INF, x));
		assertEquals(MINUS_INF, add(x, MINUS_INF));
		assertEquals(MINUS_INF, add(MINUS_INF, z));
		assertEquals(MINUS_INF, add(z, MINUS_INF));
		assertEquals(MINUS_INF, add(MINUS_INF, MINUS_INF));
		assertEquals(0, add(MINUS_INF, INF));
	}

	@Test
	public void test_sub() {
		assertEquals(-333, sub(x, y));
		assertEquals(333, sub(y, x));
		assertEquals(new BigDecimal("-455.1"), sub(z, y));
		assertEquals(new BigDecimal("122.1"), sub(x, z));
		is(ZERO, sub(z, z));

		assertEquals(INF, sub(INF, x));
		assertEquals(MINUS_INF, sub(x, INF));
		assertEquals(INF, sub(INF, z));
		assertEquals(MINUS_INF, sub(z, INF));
		assertEquals(0, sub(INF, INF));
		assertEquals(INF, sub(INF, MINUS_INF));

		assertEquals(MINUS_INF, sub(MINUS_INF, x));
		assertEquals(INF, sub(x, MINUS_INF));
		assertEquals(MINUS_INF, sub(MINUS_INF, z));
		assertEquals(INF, sub(z, MINUS_INF));
		assertEquals(0, sub(MINUS_INF, MINUS_INF));
		assertEquals(MINUS_INF, sub(MINUS_INF, INF));
	}

	private static final Object p9 = BigDecimal.valueOf(9);
	private static final Object m9 = BigDecimal.valueOf(-9);

	@Test
	public void test_mul() {
		assertEquals(INF,		mul(MINUS_INF, MINUS_INF));
		assertEquals(INF,		mul(MINUS_INF, -9));
		assertEquals(INF,		mul(MINUS_INF, m9));
		assertEquals(0,			mul(MINUS_INF, ZERO));
		assertEquals(MINUS_INF, mul(MINUS_INF, 9));
		assertEquals(MINUS_INF, mul(MINUS_INF, p9));
		assertEquals(MINUS_INF, mul(MINUS_INF, INF));

		assertEquals(INF,		mul(m9, MINUS_INF));
		assertEquals(81,		mul(m9, -9));
		assertEquals(81,		mul(m9, m9));
		assertEquals(0,			mul(m9, 0));
		assertEquals(0,			mul(m9, ZERO));
		assertEquals(-81,		mul(m9, 9));
		assertEquals(-81,		mul(m9, p9));
		assertEquals(MINUS_INF, mul(m9, INF));

		assertEquals(INF,		mul(-9, MINUS_INF));
		assertEquals(81,		mul(-9, m9));
		assertEquals(81,		mul(-9, -9));
		assertEquals(0,			mul(-9, 0));
		assertEquals(0,			mul(-9, ZERO));
		assertEquals(-81,		mul(-9, 9));
		assertEquals(-81,		mul(-9, p9));
		assertEquals(MINUS_INF, mul(-9, INF));

		assertEquals(0,			mul(0, MINUS_INF));
		assertEquals(0,			mul(0, m9));
		assertEquals(0,			mul(0, 0));
		assertEquals(0,			mul(0, 0));
		assertEquals(0,			mul(0, ZERO));
		assertEquals(0,			mul(0, 9));
		assertEquals(0,			mul(0, p9));
		assertEquals(0, 		mul(0, INF));

		assertEquals(0,			mul(ZERO, MINUS_INF));
		assertEquals(0,			mul(ZERO, m9));
		assertEquals(0,			mul(ZERO, 0));
		assertEquals(0,			mul(ZERO, 0));
		assertEquals(0,			mul(ZERO, ZERO));
		assertEquals(0,			mul(ZERO, 9));
		assertEquals(0,			mul(ZERO, p9));
		assertEquals(0, 		mul(ZERO, INF));

		assertEquals(MINUS_INF,	mul(9, MINUS_INF));
		assertEquals(-81,		mul(9, m9));
		assertEquals(-81,		mul(9, -9));
		assertEquals(0,			mul(9, 0));
		assertEquals(0,			mul(9, ZERO));
		assertEquals(81,		mul(9, 9));
		assertEquals(81,		mul(9, p9));
		assertEquals(INF,		mul(9, INF));

		assertEquals(MINUS_INF,	mul(p9, MINUS_INF));
		assertEquals(-81,		mul(p9, m9));
		assertEquals(-81,		mul(p9, -9));
		assertEquals(0,			mul(p9, 0));
		assertEquals(0,			mul(p9, ZERO));
		assertEquals(81,		mul(p9, 9));
		assertEquals(81,		mul(p9, p9));
		assertEquals(INF,		mul(p9, INF));

		assertEquals(MINUS_INF,	mul(INF, MINUS_INF));
		assertEquals(MINUS_INF,	mul(INF, -9));
		assertEquals(MINUS_INF,	mul(INF, m9));
		assertEquals(0,			mul(INF, ZERO));
		assertEquals(INF, 		mul(INF, 9));
		assertEquals(INF,		mul(INF, p9));
		assertEquals(INF,		mul(INF, INF));
	}

	@Test
	public void test_overflow() {
		// overflow from int to long
		assertEquals(Integer.MAX_VALUE + 1L, add(Integer.MAX_VALUE, 1));
		assertEquals(Integer.MAX_VALUE + 1L, sub(Integer.MAX_VALUE, -1));
		assertEquals(Integer.MAX_VALUE * 10L, mul(Integer.MAX_VALUE, 10));
		assertEquals(Integer.MAX_VALUE * (long) Integer.MAX_VALUE,
				mul(Integer.MAX_VALUE, Integer.MAX_VALUE));
		assertEquals(Integer.MAX_VALUE + 1L, uminus(Integer.MIN_VALUE));

		// overflow from long to BigDecimal
		BigDecimal MAX_LONG = BigDecimal.valueOf(Long.MAX_VALUE);
		assertEquals(MAX_LONG.add(BigDecimal.ONE, MC),
				add(Long.MAX_VALUE, 1));
		assertEquals(MAX_LONG.subtract(BigDecimal.ONE.negate(), MC),
				sub(Long.MAX_VALUE, -1));
		assertEquals(MAX_LONG.multiply(BigDecimal.TEN, MC),
				mul(Long.MAX_VALUE, 10));
		assertEquals(MAX_LONG.add(BigDecimal.ONE, MC), uminus(Long.MIN_VALUE));
	}

	@Test
	public void test_div() {
		assertEquals(1,			div(MINUS_INF, MINUS_INF));
		assertEquals(INF,		div(MINUS_INF, -9));
		assertEquals(INF,		div(MINUS_INF, m9));
		assertEquals(MINUS_INF,	div(MINUS_INF, ZERO));
		assertEquals(MINUS_INF, div(MINUS_INF, 9));
		assertEquals(MINUS_INF, div(MINUS_INF, p9));
		assertEquals(-1, 		div(MINUS_INF, INF));

		assertEquals(0,			div(m9, MINUS_INF));
		assertEquals(1,			div(m9, -9));
		assertEquals(1,			div(m9, m9));
		assertEquals(MINUS_INF, div(m9, 0));
		assertEquals(MINUS_INF, div(m9, ZERO));
		assertEquals(-1,		div(m9, 9));
		assertEquals(-1,		div(m9, p9));
		assertEquals(0,			div(m9, INF));

		assertEquals(0,			div(-9, MINUS_INF));
		assertEquals(1,			div(-9, m9));
		assertEquals(1,			div(-9, -9));
		assertEquals(MINUS_INF,	div(-9, 0));
		assertEquals(MINUS_INF,	div(-9, ZERO));
		assertEquals(-1,		div(-9, 9));
		assertEquals(-1,		div(-9, p9));
		assertEquals(0, 		div(-9, INF));

		assertEquals(0,			div(0, MINUS_INF));
		assertEquals(0,			div(0, m9));
		assertEquals(0,			div(0, 0));
		assertEquals(0,			div(0, 0));
		assertEquals(0,			div(0, ZERO));
		assertEquals(0,			div(0, 9));
		assertEquals(0,			div(0, p9));
		assertEquals(0, 		div(0, INF));

		assertEquals(0,			div(ZERO, MINUS_INF));
		assertEquals(0,			div(ZERO, m9));
		assertEquals(0,			div(ZERO, 0));
		assertEquals(0,			div(ZERO, 0));
		assertEquals(0,			div(ZERO, ZERO));
		assertEquals(0,			div(ZERO, 9));
		assertEquals(0,			div(ZERO, p9));
		assertEquals(0, 		div(ZERO, INF));

		assertEquals(0,			div(9, MINUS_INF));
		assertEquals(-1,		div(9, m9));
		assertEquals(-1,		div(9, -9));
		assertEquals(INF,		div(9, 0));
		assertEquals(INF,		div(9, ZERO));
		assertEquals(1,			div(9, 9));
		assertEquals(1,			div(9, p9));
		assertEquals(0,			div(9, INF));

		assertEquals(0,			div(p9, MINUS_INF));
		assertEquals(-1,		div(p9, m9));
		assertEquals(-1,		div(p9, -9));
		assertEquals(INF,		div(p9, 0));
		assertEquals(INF,		div(p9, ZERO));
		assertEquals(1,			div(p9, 9));
		assertEquals(1,			div(p9, p9));
		assertEquals(0,			div(p9, INF));

		assertEquals(-1,		div(INF, MINUS_INF));
		assertEquals(MINUS_INF,	div(INF, -9));
		assertEquals(MINUS_INF,	div(INF, m9));
		assertEquals(INF,		div(INF, 0));
		assertEquals(INF,		div(INF, ZERO));
		assertEquals(INF, 		div(INF, 9));
		assertEquals(INF,		div(INF, p9));
		assertEquals(1,			div(INF, INF));
	}

	@Test
	public void test_catchMatch() {
		match("abc", "a");
		nomatch("abc", "b");
		match("abc", "b|ab");
		nomatch("abc", "x|y|z");
		match("abc", "*bc|*xy");
		nomatch("abc", "*x|*y");
		match("abc", "*");
	}

	private static void match(String exception, String pattern) {
		try {
			catchMatch(new SuException(exception), pattern);
		} catch (Throwable e) {
			fail();
		}
	}
	private static void nomatch(String exception, String pattern) {
		try {
			catchMatch(new SuException(exception), pattern);
			fail();
		} catch (Throwable e) {
			// expected
		}
	}

	@Test
	public void dont_catch_block_return_exception() {
		try {
			catchMatch(new BlockReturnException(null,0));
			fail();
		} catch (Throwable e) {
			assertThat(e, instanceOf(BlockReturnException.class));
		}
		try {
			catchMatch(new BlockReturnException(null,0), "*");
			fail();
		} catch (Throwable e) {
			assertThat(e, instanceOf(BlockReturnException.class));
		}
	}

	@Test
	public void test_toStringBD() {
		assertEquals("10000000000000000000", Ops.toStringBD(new BigDecimal("1e19")));
		assertEquals("1e20", Ops.toStringBD(new BigDecimal("1e20")));
	}

	@Test
	public void getString() {
		assertEquals("a", Ops.get("abcd", 0));
		assertEquals("d", Ops.get("abcd", 3));
		assertEquals("", Ops.get("abcd", 4));
		assertEquals("d", Ops.get("abcd", -1));
		assertEquals("a", Ops.get("abcd", -4));
		assertEquals("", Ops.get("abcd", -5));
	}
}
