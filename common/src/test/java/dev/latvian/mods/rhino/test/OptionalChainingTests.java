package dev.latvian.mods.rhino.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OptionalChainingTests {
	public static RhinoTest TEST = new RhinoTest("optionalChaining");

	@Test
	@DisplayName("Should Error")
	public void shouldError() {
		TEST.test("shouldError", """
				let a = { b: { c: 'd' } }
				let e = { f: {} }
				let h = null
				console.info(a.b.c)
				console.info(e.f.g)
				console.info(h.i.j)
				""", """
				d
				undefined
				Error: TypeError: Cannot read property "i" from null (optionalChaining/shouldError#6)
				""");
	}

	@Test
	@DisplayName("Shouldnt Error")
	public void shouldntError() {
		TEST.test("shouldntError", """
				let a = { b: { c: 'd' } }
				let e = { f: {} }
				let h = null
				console.info(a?.b?.c)
				console.info(e?.f?.g)
				console.info(h?.i?.j)
				""", """
				d
				undefined
				undefined
				""");
	}
}
