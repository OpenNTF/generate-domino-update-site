package org.openntf.p2.domino.updatesite.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.openntf.p2.domino.updatesite.util.ClassWriterUtil;

@SuppressWarnings("nls")
public class TestMethodTypeVariables {
	static interface Foo {
		<L extends java.util.EventListener> L get(Class<L> arg0);
		
		<A extends java.util.EventListener, B extends A> B get(Class<A> arg0, Class<B> arg1);
	}
	
	@Test
	public void testBasicMethodBounds() throws NoSuchMethodException, SecurityException {
		String expected = "<L extends java.util.EventListener>";
		Class<?> foo = Foo.class;
		Method get = foo.getDeclaredMethod("get", Class.class);
		
		String result = ClassWriterUtil.printTypeVariables(get.getTypeParameters());
		assertEquals(expected, result);
	}
	
	@Test
	public void testChainedMethodBounds() throws NoSuchMethodException, SecurityException {
		String expected = "<A extends java.util.EventListener, B extends A>";
		Class<?> foo = Foo.class;
		Method get = foo.getDeclaredMethod("get", Class.class, Class.class);
		
		String result = ClassWriterUtil.printTypeVariables(get.getTypeParameters());
		assertEquals(expected, result);
	}
}
