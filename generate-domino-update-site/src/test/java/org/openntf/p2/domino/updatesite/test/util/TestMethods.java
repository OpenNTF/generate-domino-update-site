package org.openntf.p2.domino.updatesite.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.invoke.MethodType;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.openntf.p2.domino.updatesite.GenerateSourceStubProjectsMojo;
import org.openntf.p2.domino.updatesite.util.ClassWriterConfig;
import org.openntf.p2.domino.updatesite.util.ClassWriterUtil;
import org.openntf.p2.domino.updatesite.util.MethodMatcher;

@SuppressWarnings("nls")
public class TestMethods {
	static class Foo {
		public void bar() { }
		public void bar(Object arg0) { }
		public Object baz() { return null; }
		public Object remove(Object arg0, Object arg1) { return null; }
	}
	
	@Test
	public void testBasicMethod() throws NoSuchMethodException, SecurityException {
		String expected = "\tpublic void bar() {\n\t}\n";
		String actual = ClassWriterUtil.printMethod(Foo.class, Foo.class.getDeclaredMethod("bar"));
		assertEquals(expected, actual);
	}
	
	@Test
	public void testParamMethod() throws NoSuchMethodException, SecurityException {
		String expected = "\tpublic void bar(java.lang.Object arg0) {\n\t}\n";
		String actual = ClassWriterUtil.printMethod(Foo.class, Foo.class.getDeclaredMethod("bar", Object.class));
		assertEquals(expected, actual);
	}
	
	@Test
	public void testReturningMethod() throws NoSuchMethodException, SecurityException {
		String expected = "\tpublic java.lang.Object baz() {\n\t\treturn null;\n\t}\n";
		String actual = ClassWriterUtil.printMethod(Foo.class, Foo.class.getDeclaredMethod("baz"));
		assertEquals(expected, actual);
	}
	
	@Test
	public void testOverrideMethod() throws NoSuchMethodException, SecurityException {
		ClassWriterConfig.RETURN_OVERRIDES.put(Foo.class.getName(), Collections.singletonMap(new MethodMatcher("remove", MethodType.methodType(Object.class, Object.class, Object.class)), boolean.class));
		String expected = "\tpublic boolean remove(java.lang.Object arg0, java.lang.Object arg1) {\n\t\treturn false;\n\t}\n";
		String actual = ClassWriterUtil.printMethod(Foo.class, Foo.class.getDeclaredMethod("remove", Object.class, Object.class));
		assertEquals(expected, actual);
	}
}
