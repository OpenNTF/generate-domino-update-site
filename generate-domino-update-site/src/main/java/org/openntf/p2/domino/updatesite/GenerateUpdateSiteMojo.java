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
package org.openntf.p2.domino.updatesite;

import java.io.File;

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
	@Parameter(property="src", required=true)
	private File src;
	
	/**
	 * Destination directory
	 */
	@Parameter(property="dest", required=true)
	private File dest;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		String dominoDir = src.getAbsolutePath();
		String destDir = dest.getAbsolutePath();
		
		new GenerateUpdateSiteTask(dominoDir, destDir).run();
	}

}
