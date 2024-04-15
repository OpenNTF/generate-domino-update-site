package org.openntf.p2.domino.updatesite.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.ref.WeakReference;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openntf.p2.domino.updatesite.test.util.data.ExampleCtorEmbed.ExampleEmbed2;
import org.openntf.p2.domino.updatesite.test.util.data.ExampleDoubleInner;
import org.openntf.p2.domino.updatesite.test.util.data.ExampleStaticDoubleInner;
import org.openntf.p2.domino.updatesite.test.util.data.ExampleThreadGroup;
import org.openntf.p2.domino.updatesite.test.util.data.ExampleThreadGroup.ExampleThreadGroup2;
import org.openntf.p2.domino.updatesite.util.ClassWriterUtil;

@SuppressWarnings("nls")
public class TestConstructors {
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
		
		class BazInner {
			public BazInner() {
				
			}
		}
		
		static class BazStaticInner {
			public BazStaticInner(Baz baz) {
				
			}
		}
	}
	
	@SuppressWarnings("rawtypes")
	static class ExampleRef extends WeakReference {

		@SuppressWarnings("unchecked")
		public ExampleRef() {
			super((Object)null);
		}
		
	}
	
	@Test
	public void testBarConstructor() {
		String actual = ClassWriterUtil.writeBasicConstructorBody(Bar.class, null, Bar.class.getClassLoader(), null);
		String expected = " {\n\t\tsuper((A)null);\n\t}\n"; //$NON-NLS-1$
		assertEquals(expected, actual);
	}
	
	@Disabled("need to figure out how to map these values")
	@Test
	public void testBazConstructor() {
		String actual = ClassWriterUtil.writeBasicConstructorBody(Baz.class, null, Baz.class.getClassLoader(), null);
		String expected = " {\n\t\tsuper((String)null);\n\t}\n"; //$NON-NLS-1$
		assertEquals(expected, actual);
	}
	
	@Test
	public void testRawTypeSuper() {
		String actual = ClassWriterUtil.writeBasicConstructorBody(ExampleRef.class, null, ExampleRef.class.getClassLoader(), null);
		String expected = " {\n\t\tsuper((Object)null);\n\t}\n"; //$NON-NLS-1$
		assertEquals(expected, actual);
	}
	
	@Test
	public void testInnerCtor() {
		String actual = ClassWriterUtil.printConstructors(Baz.BazInner.class, Baz.BazInner.class.getClassLoader(), "BazInner", null);
		String expected = "\tpublic BazInner() {\n\t}\n\n";
		assertEquals(expected, actual);
	}
	
	@Test
	public void testInnerStaticCtor() {
		String actual = ClassWriterUtil.printConstructors(Baz.BazStaticInner.class, Baz.BazStaticInner.class.getClassLoader(), "BazStaticInner", null);
		String expected = "\tpublic BazStaticInner(org.openntf.p2.domino.updatesite.test.util.TestConstructors.Baz arg0) {\n\t}\n\n";
		assertEquals(expected, actual);
	}
	
	@Test
	public void testDoubleInner() {
		String actual = ClassWriterUtil.printConstructors(ExampleDoubleInner.InnerA.InnerB.class, ExampleDoubleInner.InnerA.InnerB.class.getClassLoader(), "InnerB", null);
		String expected = "\tpublic InnerB() {\n\t}\n\n";
		assertEquals(expected, actual);
	}
	
	@Test
	public void testDoubleInnerExtends() {
		String actual = ClassWriterUtil.printConstructors(ExampleDoubleInner.InnerA.InnerC.class, ExampleDoubleInner.InnerA.InnerC.class.getClassLoader(), "InnerC", null);
		String expected = "\tpublic InnerC() {\n\t}\n\n";
		assertEquals(expected, actual);
	}
	
	@Test
	public void testStaticDoubleInner() {
		String actual = ClassWriterUtil.printConstructors(ExampleStaticDoubleInner.InnerA.InnerB.class, ExampleStaticDoubleInner.InnerA.InnerB.class.getClassLoader(), "InnerB", null);
		String expected = "\tpublic InnerB() {\n\t}\n\n";
		assertEquals(expected, actual);
	}
	
	@Test
	public void testExampleThreadGroup() {
		String actual = ClassWriterUtil.printConstructors(ExampleThreadGroup.class, ExampleThreadGroup.class.getClassLoader(), "ExampleThreadGroup", null);
		String expected = "\tpublic ExampleThreadGroup(java.lang.String arg0) {\n\t\tsuper((java.lang.ThreadGroup)null, (java.lang.String)null);\n\t}\n\n";
		assertEquals(expected, actual);
	}
	
	@Test
	public void testExampleThreadGroup2() {
		String actual = ClassWriterUtil.printConstructors(ExampleThreadGroup2.class, ExampleThreadGroup2.class.getClassLoader(), "ExampleThreadGroup2", null);
		String expected = "\tpublic ExampleThreadGroup2(java.lang.String arg1) {\n\t\tsuper((java.lang.ThreadGroup)null, (java.lang.String)null);\n\t}\n\n";
		assertEquals(expected, actual);
	}
	
	@Test
	public void testExampleCtorEmbed() {
		String actual = ClassWriterUtil.printConstructors(ExampleEmbed2.class, ExampleEmbed2.class.getClassLoader(), "ExampleEmbed2", null);
		String expected = "\tpublic ExampleEmbed2() {\n\t}\n\n";
		assertEquals(expected, actual);
	}
}
