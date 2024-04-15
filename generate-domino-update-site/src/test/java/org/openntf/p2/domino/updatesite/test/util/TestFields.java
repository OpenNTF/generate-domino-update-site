package org.openntf.p2.domino.updatesite.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openntf.p2.domino.updatesite.GenerateSourceStubProjectsMojo;
import org.openntf.p2.domino.updatesite.util.ClassWriterConfig;
import org.openntf.p2.domino.updatesite.util.ClassWriterUtil;

@SuppressWarnings("nls")
public class TestFields {
	class StaticField {
		public static final String FOO = "Bar";
	}
	class OverwriteField {
		public static final String BAR = "Baz";
	}
	class SkipField {
		public String skipMe;
		public String doNotSkip;
	}
	
	@Test
	public void testStaticString() {
		String actual = ClassWriterUtil.printClassFields(StaticField.class, null);
		String expected = "\tpublic static final java.lang.String FOO = \"Bar\";\n\n";
		assertEquals(expected, actual);
	}
	
	@Test
	public void testHardCodedField() {
		Map<String, String> overrides = ClassWriterConfig.KNOWN_STRING_CONSTANTS.compute(OverwriteField.class.getName(), (k, v) -> new HashMap<>());
		overrides.put("BAR", "Ness");
		
		String actual = ClassWriterUtil.printClassFields(OverwriteField.class, null);
		String expected = "\tpublic static final java.lang.String BAR = \"Ness\";\n\n";
		assertEquals(expected, actual);
	}
	
	@Test
	public void testSkipField() {
		ClassWriterConfig.SKIP_FIELDS.put(SkipField.class.getName(), Collections.singleton("skipMe"));

		String actual = ClassWriterUtil.printClassFields(SkipField.class, null);
		String expected = "\tpublic java.lang.String doNotSkip;\n\n";
		assertEquals(expected, actual);
	}
}
