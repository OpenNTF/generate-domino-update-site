package org.openntf.p2.domino.updatesite.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openntf.p2.domino.updatesite.util.ClassWriterUtil;

public class TestConstructorBodies {
	static class Foo<A> {
		public Foo(A arg0) {
			
		}
	}
	static class Bar<A> extends Foo<A> {
		public Bar(A arg0) {
			super((A)null);
		}
	}
	static class Baz extends Foo<String> {
		public Baz(String arg0) {
			super((String)null);
		}
	}
	
	@Test
	public void testBarConstructor() {
		String actual = ClassWriterUtil.writeBasicConstructorBody(Bar.class, Bar.class.getClassLoader(), null);
		String expected = " {\n\t\tsuper((A)null);\n\t}\n"; //$NON-NLS-1$
		assertEquals(expected, actual);
	}
	
	@Disabled("need to figure out how to map these values")
	@Test
	public void testBazConstructor() {
		String actual = ClassWriterUtil.writeBasicConstructorBody(Baz.class, Baz.class.getClassLoader(), null);
		String expected = " {\n\t\tsuper((String)null);\n\t}\n"; //$NON-NLS-1$
		assertEquals(expected, actual);
	}
}
