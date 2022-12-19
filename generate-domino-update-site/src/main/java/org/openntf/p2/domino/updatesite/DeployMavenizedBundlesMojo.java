/**
 * Copyright © 2018-2022 Jesse Gallagher
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

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.openntf.p2.domino.updatesite.model.BundleEmbed;
import org.openntf.p2.domino.updatesite.model.BundleInfo;

/**
 * Mavenizes any bundles in the provided p2 directory and deploys them to a target repository.
 * 
 * @author Jesse Gallagher
 * @since 4.0.0
 */
@Mojo(name="mavenizeAndDeployBundles", requiresProject=false)
public class DeployMavenizedBundlesMojo extends AbstractMavenizeBundlesMojo {
	
	/**
	 * Specifies an alternative repository to which the project artifacts should be deployed.
	 *  
	 * <p>Format: id::layout::url</p>
	 */
	@Parameter(property="deploymentRepository", required=true)
	private String deploymentRepository;

	@Override
	protected void processBundle(BundleInfo bundle, List<BundleInfo> bundles, Map<String, BundleInfo> bundlesByName,
			Path tempPom) throws MojoExecutionException {
		String[] repoBits = deploymentRepository.split("::", 3); //$NON-NLS-1$
		if(repoBits.length != 3) {
			throw new IllegalArgumentException(MessageFormat.format(Messages.getString("DeployMavenizedBundlesMojo.unexpectedRepositoryFormat"), deploymentRepository)); //$NON-NLS-1$
		}
		String repositoryId = repoBits[0];
		String url = repoBits[2];
		
		// Generate extra info for embeds
		List<String> embedFiles = new ArrayList<>();
		List<String> embedClassifiers = new ArrayList<>();
		for(BundleEmbed embed : bundle.getEmbeds()) {
			String baseName = toEmbedClassifierName(embed.getName());
			embedFiles.add(embed.getFile().toString());
			embedClassifiers.add(baseName);
		}
		
		String extraFiles = String.join(",", embedFiles); //$NON-NLS-1$
		String extraClassifiers = String.join(",", embedClassifiers); //$NON-NLS-1$
		String extraTypes = IntStream.range(0, embedFiles.size()).mapToObj(i -> "jar").collect(Collectors.joining(",")); //$NON-NLS-1$ //$NON-NLS-2$
		executeMojo(
			plugin(
				groupId("org.apache.maven.plugins"), //$NON-NLS-1$
				artifactId("maven-deploy-plugin"), //$NON-NLS-1$
				version("3.0.0-M1") //$NON-NLS-1$
			),
			goal("deploy-file"), //$NON-NLS-1$
			configuration(
				element("file", bundle.getFilePath()), //$NON-NLS-1$
				element("repositoryId", repositoryId), //$NON-NLS-1$
				element("url", url), //$NON-NLS-1$
				element("groupId", groupId), //$NON-NLS-1$
				element("artifactId", bundle.getArtifactId()), //$NON-NLS-1$
				element("version", bundle.getVersion()), //$NON-NLS-1$
				element("packaging", "jar"), //$NON-NLS-1$ //$NON-NLS-2$
				element("pomFile", tempPom.toString()), //$NON-NLS-1$
				
				element("files", extraFiles), //$NON-NLS-1$
				element("classifiers", extraClassifiers), //$NON-NLS-1$
				element("types", extraTypes) //$NON-NLS-1$
			),
			executionEnvironment(
				mavenProject,
        		mavenSession,
        		pluginManager
			)
		);
		
		
	}

}
