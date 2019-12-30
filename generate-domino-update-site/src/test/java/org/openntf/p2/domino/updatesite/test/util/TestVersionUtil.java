package org.openntf.p2.domino.updatesite.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.openntf.p2.domino.updatesite.util.VersionUtil;

@SuppressWarnings("nls")
public class TestVersionUtil {
	@Test
	public void testMacV11Beta2() {
		assertEquals("11.0.0.20191016", VersionUtil.generateNotesJarVersion("Build V1100_10162019", "October 16, 2019"));
	}
	
	@Test
	public void testDominoV10() {
		assertEquals("10.0.0.20180919", VersionUtil.generateNotesJarVersion("Release 10.0", "September 19, 2018"));
	}
	
	@Test
	public void testV11BetaSomething() {
		assertEquals("11.0.0.20190830", VersionUtil.generateNotesJarVersion("Build V1100_08302019", "August 30, 2019"));
	}
}
