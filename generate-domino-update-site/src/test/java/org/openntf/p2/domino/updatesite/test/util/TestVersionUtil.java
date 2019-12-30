/**
 * Copyright © 2018-2019 Jesse Gallagher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
