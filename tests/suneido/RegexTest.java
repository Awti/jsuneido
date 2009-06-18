package suneido;

import static org.junit.Assert.*;

import java.util.regex.Pattern;

import org.junit.Test;

public class RegexTest {

	@Test
	public void convertRegex() {
		assertEquals("[\\p{Alnum}]", Regex.convertRegex("[[:alnum:]]"));
	}

	@Test
	public void getPat() {
		Pattern p1 = Regex.getPat("x");
		Pattern p2 = Regex.getPat("a.*b");
		assertSame(p1, Regex.getPat("x"));
		assertSame(p2, Regex.getPat("a.*b"));
	}

	@Test
	public void contains() {
		String truecases[] = new String[] {
				"abc", "b",
				"abc", "a.c",
				"a", "[[:alnum:]]",
				"hello", "\\<hello\\>",
				"-", "[-x]",
				"-", "[x-]",
				"[", "[[]",
				"]", "]"
				};
		for (int i = 0; i < truecases.length; i += 2)
			assertTrue(truecases[i] + " =~ " + truecases[i + 1],
					Regex.contains(truecases[i], truecases[i + 1]));
		String falsecases[] = new String[] {
				"abc", "d",
				"abc", "(?q).",
				"hello", "\\<hell\\>",
				};
		for (int i = 0; i < falsecases.length; i += 2)
			assertFalse(falsecases[i] + " !~ " + falsecases[i + 1],
					Regex.contains(falsecases[i], falsecases[i + 1]));
	}

	@Test
	public void convertReplacement() {
		assertEquals("abc", Regex.convertReplacement("abc"));
		assertEquals("$0x", Regex.convertReplacement("&x"));
		assertEquals("a$0b", Regex.convertReplacement("a&b"));
		assertEquals("a&b", Regex.convertReplacement("a\\&b"));
		assertEquals("a\\$b", Regex.convertReplacement("a$b"));
		assertEquals("$3", Regex.convertReplacement("\\3"));
	}

}
