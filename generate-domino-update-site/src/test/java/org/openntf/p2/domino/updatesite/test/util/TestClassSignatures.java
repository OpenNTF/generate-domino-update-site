package org.openntf.p2.domino.updatesite.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EventListener;

import org.junit.jupiter.api.Test;
import org.openntf.p2.domino.updatesite.util.ClassWriterUtil;

@SuppressWarnings("nls")
public class TestClassSignatures {
	interface Foo<A, B> {
		
	}
	class Bar implements Foo<String, Integer>, EventListener {
		
	}
	
	class Baz<C, D> implements Foo<C, D> {
		
	}
	
	class Ness<E, F> extends Baz<E, F> {
		
	}
	
	@Test
	public void testBarSignature() {
		String actual = ClassWriterUtil.printClassSignature(Bar.class, Bar.class.getSimpleName());
		String expected = "class Bar implements org.openntf.p2.domino.updatesite.test.util.TestClassSignatures.Foo<java.lang.String, java.lang.Integer>, java.util.EventListener";
		assertEquals(expected, actual);
	}

	@Test
	public void testBazSignature() {
		String actual = ClassWriterUtil.printClassSignature(Baz.class, Baz.class.getSimpleName());
		String expected = "class Baz<C, D> implements org.openntf.p2.domino.updatesite.test.util.TestClassSignatures.Foo<C, D>";
		assertEquals(expected, actual);
	}
	
	@Test
	public void testNessSignature() {
		String actual = ClassWriterUtil.printClassSignature(Ness.class, Ness.class.getSimpleName());
		String expected = "class Ness<E, F> extends org.openntf.p2.domino.updatesite.test.util.TestClassSignatures.Baz<E, F>";
		assertEquals(expected, actual);
	}
}
