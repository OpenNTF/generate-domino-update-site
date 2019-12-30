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
package org.openntf.p2.domino.updatesite.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.commons.util.StringUtil;

/**
 * Utilities for working with Notes version identifiers.
 * 
 * @author Jesse Gallagher
 * @since 3.1.0
 */
public enum VersionUtil {
	;

	private static final Pattern NOTESJAR_BUILD_PATTERN = Pattern.compile("Build V(\\d\\d)(\\d)(\\d)_(\\d+)"); //$NON-NLS-1$
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd"); //$NON-NLS-1$
	private static final DateTimeFormatter NOTESVERSIONDATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US); // U-S-A! U-S-A! //$NON-NLS-1$
	
	/**
	 * Generates an OSGi-friendly version number based on the {@code NotesVersion} and
	 * {@code NotesVersionDate} values from the {@code lotus/domino/Version.properties}
	 * file in Notes.jar.
	 * 
	 * @param notesVersion the value of the {@code NotesVersion} property
	 * @param notesVersionDate the value of the {@code NotesVersionDate} property
	 * @return a version number suitable for OSGi use
	 */
	public static String generateNotesJarVersion(String notesVersion, String notesVersionDate) {
		StringBuilder result = new StringBuilder();
		if(notesVersion.startsWith("Release ")) { //$NON-NLS-1$
			result.append(notesVersion.substring("Release ".length())); //$NON-NLS-1$
			// Append a .0 if needed
			if(StringUtil.countMatch(result.toString(), '.') < 2) {
				result.append(".0"); //$NON-NLS-1$
			}
		} else {
			// Beta builds have special formatting
			Matcher buildMatcher = NOTESJAR_BUILD_PATTERN.matcher(notesVersion);
			if(buildMatcher.matches()) {
				result.append(buildMatcher.group(1) + '.' + buildMatcher.group(2) + '.' + buildMatcher.group(3));
			} else {
				result.append(notesVersion);
			}
		}

		// Check the NotesVersionDate to get a qualifier
		TemporalAccessor parsedDate = null;
		try {
			parsedDate = NOTESVERSIONDATE_FORMAT.parse(notesVersionDate);
		} catch(DateTimeParseException e) {
			parsedDate = null;
		}
		if(parsedDate == null) {
			// Then just use today
			parsedDate = LocalDate.now();
		}
		result.append('.');
		result.append(TIMESTAMP_FORMAT.format(parsedDate));
		return result.toString();
	}
}
