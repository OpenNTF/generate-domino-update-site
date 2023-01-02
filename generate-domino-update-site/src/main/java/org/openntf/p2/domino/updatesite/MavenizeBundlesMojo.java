/**
 * Copyright © 2018-2020 Jesse Gallagher
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

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.openntf.p2.domino.updatesite.model.BundleEmbed;
import org.openntf.p2.domino.updatesite.model.BundleInfo;
import org.twdata.maven.mojoexecutor.MojoExecutor;

/**
 * Mavenizes any bundles in the provided XPages p2 directory.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 * @see Consider using <a href="https://github.com/OpenNTF/p2-layout-provider">p2-layout-provider</a> instead
 */
@Mojo(name="mavenizeBundles", requiresProject=false)
public class MavenizeBundlesMojo extends AbstractMavenizeBundlesMojo {
	
	/**
	 * Specifies an alternate local repository for the installation.
	 * 
	 * @since 3.4.0
	 */
	@Parameter(property="localRepositoryPath", required=false)
	private File localRepositoryPath;
	
	@Override
	protected void processBundle(BundleInfo bundle, List<BundleInfo> bundles, Map<String, BundleInfo> bundlesByName,
			Path tempPom) throws MojoExecutionException {
		executeMojo(
			plugin(
				groupId("org.apache.maven.plugins"), //$NON-NLS-1$
				artifactId("maven-install-plugin"), //$NON-NLS-1$
				version("2.5.2") //$NON-NLS-1$
			),
			goal("install-file"), //$NON-NLS-1$
			configuration(
				element("file", bundle.getFilePath()), //$NON-NLS-1$
				element("groupId", groupId), //$NON-NLS-1$
				element("artifactId", bundle.getArtifactId()), //$NON-NLS-1$
				element("version", bundle.getVersion()), //$NON-NLS-1$
				element("packaging", "jar"), //$NON-NLS-1$ //$NON-NLS-2$
				element("pomFile", tempPom.toString()) //$NON-NLS-1$
			),
			executionEnvironment(
				mavenProject,
        		mavenSession,
        		pluginManager
			)
		);
		
		// Generate additional executions for each embed
		for(BundleEmbed embed : bundle.getEmbeds()) {
			String baseName = embed.getName().substring(0, embed.getName().lastIndexOf('.')).replace('/', '$');
			
			List<MojoExecutor.Element> elements = new ArrayList<>(Arrays.asList(
				element("file", embed.getFile().toString()), //$NON-NLS-1$
				element("groupId", groupId), //$NON-NLS-1$
				element("artifactId", bundle.getArtifactId()), //$NON-NLS-1$
				element("version", bundle.getVersion()), //$NON-NLS-1$
				element("packaging", "jar"), //$NON-NLS-1$ //$NON-NLS-2$
				element("classifier", baseName) //$NON-NLS-1$
			));
			if(this.localRepositoryPath != null) {
				element("localRepositoryPath", this.localRepositoryPath.toString()); //$NON-NLS-1$
			}
			executeMojo(
				plugin(
					groupId("org.apache.maven.plugins"), //$NON-NLS-1$
					artifactId("maven-install-plugin"), //$NON-NLS-1$
					version("2.5.2") //$NON-NLS-1$
				),
				goal("install-file"), //$NON-NLS-1$
				configuration(
					elements.toArray(new MojoExecutor.Element[elements.size()])
				),
				executionEnvironment(
					mavenProject,
	        		mavenSession,
	        		pluginManager
				)
			);
		}
		
		// Same goes for the source bundle if present
		if(bundle.getSource() != null) {
			List<MojoExecutor.Element> elements = new ArrayList<>(Arrays.asList(
				element("file", bundle.getSource().toString()), //$NON-NLS-1$
				element("groupId", groupId), //$NON-NLS-1$
				element("artifactId", bundle.getArtifactId()), //$NON-NLS-1$
				element("version", bundle.getVersion()), //$NON-NLS-1$
				element("packaging", "jar"), //$NON-NLS-1$ //$NON-NLS-2$
				element("classifier", "sources") //$NON-NLS-1$ //$NON-NLS-2$
			));
			if(this.localRepositoryPath != null) {
				element("localRepositoryPath", this.localRepositoryPath.toString()); //$NON-NLS-1$
			}
			executeMojo(
				plugin(
					groupId("org.apache.maven.plugins"), //$NON-NLS-1$
					artifactId("maven-install-plugin"), //$NON-NLS-1$
					version("2.5.2") //$NON-NLS-1$
				),
				goal("install-file"), //$NON-NLS-1$
				configuration(
					elements.toArray(new MojoExecutor.Element[elements.size()])
				),
				executionEnvironment(
					mavenProject,
	        		mavenSession,
	        		pluginManager
				)
			);
		}
	}
}
