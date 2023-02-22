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
package org.openntf.p2.domino.updatesite;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.openntf.p2.domino.updatesite.tasks.GenerateUpdateSiteTask;

@Mojo(name="generateUpdateSite", requiresProject=false)
public class GenerateUpdateSiteMojo extends AbstractMojo {
	
	/**
	 * Source Domino program directory
	 */
	@Parameter(property="src", required=false, defaultValue="${notes-program}")
	private File src;
	
	/**
	 * Destination directory
	 */
	@Parameter(property="dest", required=true)
	private File dest;
	
	/**
	 * Whether embedded JARs should be "flattened" into their containing
	 * bundles (defaults to false).
	 * 
	 * @since 5.0.0
	 */
	@Parameter(property="flattenEmbeds", required=false, defaultValue="false")
	private boolean flattenEmbeds = false;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Path dominoDir;
		if(src != null) {
			dominoDir = src.toPath();
		} else {
			dominoDir = findDominoDir();
		}
		if(dominoDir == null || !Files.exists(dominoDir)) {
			throw new MojoExecutionException(Messages.getString("GenerateUpdateSiteMojo.unableToLocateDomino")); //$NON-NLS-1$
		}
		Path destDir = dest.toPath();
		
		new GenerateUpdateSiteTask(dominoDir, destDir, flattenEmbeds, getLog()).run();
	}

	private Path findDominoDir() {
		if(SystemUtils.IS_OS_MAC) {
			return Stream.of(
					"/Applications/HCL Notes.app/Contents/MacOS", //$NON-NLS-1$
					"/Applications/IBM Notes.app/Contents/MacOS" //$NON-NLS-1$
				).map(Paths::get)
				.filter(Files::exists)
				.findFirst()
				.orElse(null);
		} else if(SystemUtils.IS_OS_WINDOWS) {
			return Stream.of(
					"C:\\Program Files\\HCL\\Domino", //$NON-NLS-1$
					"C:\\Program Files\\IBM\\Domino", //$NON-NLS-1$
					"C:\\Domino", //$NON-NLS-1$
					"C:\\Program Files (x86)\\HCL\\Domino", //$NON-NLS-1$
					"C:\\Program Files (x86)\\IBM\\Domino", //$NON-NLS-1$
					"C:\\Program Files\\HCL\\Notes", //$NON-NLS-1$
					"C:\\Program Files\\IBM\\Notes", //$NON-NLS-1$
					"C:\\Program Files (x86)\\HCL\\Notes", //$NON-NLS-1$
					"C:\\Program Files (x86)\\IBM\\Notes", //$NON-NLS-1$
					"C:\\Notes" //$NON-NLS-1$
				).map(Paths::get)
				.filter(Files::exists)
				.findFirst()
				.orElse(null);
		} else if(SystemUtils.IS_OS_LINUX) {
			return Stream.of(
					"/opt/hcl/domino/notes/latest/linux", //$NON-NLS-1$
					"/opt/ibm/domino/notes/latest/linux", //$NON-NLS-1$
					"/opt/ibm/lotus/domino/notes/latest/linux", //$NON-NLS-1$
					"/opt/lotus/domino/notes/latest/linux" //$NON-NLS-1$
				).map(Paths::get)
				.filter(Files::exists)
				.findFirst()
				.orElse(null);
		}
		return null;
	}
}
