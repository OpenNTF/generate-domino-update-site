/**
 * Copyright © 2018-2023 Jesse Gallagher
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
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
	private static final Pattern RELEASE_PATTERN = Pattern.compile("Release (\\d+\\.\\d+(\\.\\d+)?)(FP(\\d+))?"); //$NON-NLS-1$
	
	/**
	 * This is the public Eclipse update site that best matches what's found in 9.0.1FP10 through 12.0.2.
	 */
	public static final String UPDATE_SITE_NEON = "https://download.eclipse.org/releases/neon/201612211000"; //$NON-NLS-1$
	/**
	 * This is the public Eclipse update site that best matches what's found in 14.0.0.
	 */
	public static final String UPDATE_SITE_202112 = "https://download.eclipse.org/releases/2021-12/202112081000/"; //$NON-NLS-1$
	/**
	 * The file name for the Eclipse core runtime bundle used in 2021-12.
	 */
	public static final String ECLIPSE_CORE_202112 = "org.eclipse.core.runtime_3.24.0.v20210910-0750.jar"; //$NON-NLS-1$
	
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
		Matcher releaseMatcher = RELEASE_PATTERN.matcher(notesVersion);
		if(releaseMatcher.matches()) {
			String v = releaseMatcher.group(1);
			result.append(v);
			// Append a .0 if needed
			if(StringUtil.countMatch(result.toString(), '.') < 2) {
				result.append(".0"); //$NON-NLS-1$
			}
			
			String fp = releaseMatcher.group(4);
			if(StringUtil.isNotEmpty(fp)) {
				result.append(String.format("%03d", Integer.parseInt(fp, 10))); //$NON-NLS-1$
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
	
	public static String chooseEclipseUpdateSite(Collection<Path> eclipsePaths) throws IOException {
		for(Path eclipse : eclipsePaths) {
			if(Files.find(eclipse, Integer.MAX_VALUE, (path, attr) -> path.endsWith(ECLIPSE_CORE_202112)).findFirst().isPresent()) {
				return UPDATE_SITE_202112;
			}
		}
		return UPDATE_SITE_NEON;
	}
}
