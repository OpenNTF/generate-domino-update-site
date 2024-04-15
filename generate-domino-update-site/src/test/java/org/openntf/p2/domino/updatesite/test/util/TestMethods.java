package org.openntf.p2.domino.updatesite.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.openntf.p2.domino.updatesite.util.ClassWriterUtil;

@SuppressWarnings("nls")
public class TestMethods {
	static class Foo {
		public void bar() { }
		public void bar(Object arg0) { }
		public Object baz() { return null; }
	}
	
	@Test
	public void testBasicMethod() throws NoSuchMethodException, SecurityException {
		String expected = "\tpublic void bar() {\n\t}\n";
		String actual = ClassWriterUtil.printMethod(Foo.class, Foo.class.getDeclaredMethod("bar"));
		assertEquals(expected, actual);
	}
	
	@Test
	public void testParamMethod() throws NoSuchMethodException, SecurityException {
		String expected = "\tpublic void bar(Object arg0) {\n\t}\n";
		String actual = ClassWriterUtil.printMethod(Foo.class, Foo.class.getDeclaredMethod("bar", Object.class));
		assertEquals(expected, actual);
	}
	
	@Test
	public void testReturningMethod() throws NoSuchMethodException, SecurityException {
		String expected = "\tpublic Object baz() {\n\t\treturn null;\n\t}\n";
		String actual = ClassWriterUtil.printMethod(Foo.class, Foo.class.getDeclaredMethod("baz"));
		assertEquals(expected, actual);
	}
}
