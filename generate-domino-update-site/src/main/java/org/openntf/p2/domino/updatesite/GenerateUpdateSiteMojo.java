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

import com.ibm.commons.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.openntf.p2.domino.updatesite.docker.DockerFileManager;
import org.openntf.p2.domino.updatesite.docker.DockerFileManager.Builder;
import org.openntf.p2.domino.updatesite.docker.DockerFileManagerException;
import org.openntf.p2.domino.updatesite.tasks.GenerateUpdateSiteTask;

@Mojo(name="generateUpdateSite", requiresProject=false)
public class GenerateUpdateSiteMojo extends AbstractMojo {
	
	/**
	 * Source Domino program directory
	 */
	@Parameter(property="src", required=false, defaultValue="${notes-program}")
	private File src;

	/**
	 * Source Docker container to use. If srcImage is also given, this will be ignored.
	 */
	@Parameter(property="srcContainer", required=false)
	private String srcContainer;

	/**
	 * Source Docker image to use. If given, a temporary container will be created
	 */
	@Parameter(property="srcImageId", required=false)
	private String srcImageId;

	@Parameter(property="onlyDots", required=false, defaultValue="false")
	private boolean onlyDots = false;

	/**
	 * Destination directory
	 */
	@Parameter(property="dest", required=true)
	private File dest;

	/**
	 * The path to the Domino installation directory inside the Docker container.
	 * This is only used if srcImageId is specified.
	 */
	@Parameter(property="dockerDominoDir", required=false, defaultValue="/opt/hcl/domino/notes/latest/linux")
	private String dockerDominoDir;

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
		Path destPath = dest.toPath();

		if (Files.exists(destPath) && Files.isDirectory(destPath) && Objects.requireNonNull(destPath.toFile().list()).length > 0) {
		    try {
		        FileUtils.cleanDirectory(destPath.toFile());
		    } catch (IOException e) {
		        throw new MojoExecutionException(Messages.getString("GenerateUpdateSiteMojo.destinationCantClear", destPath), e); //$NON-NLS-1$
		    }
		}

		if(StringUtils.isNotEmpty(srcContainer) || StringUtils.isNotEmpty(srcImageId)) {
			executeWithDocker(destPath);
		} else {
			executeWithDomino(destPath);
		}
	}

	private void executeWithDomino(Path destDir) throws MojoExecutionException {
		Path dominoDir;
		if(src != null) {
			dominoDir = src.toPath();
		} else {
			dominoDir = findDominoDir();
		}
		if(dominoDir == null || !Files.exists(dominoDir)) {
			throw new MojoExecutionException(Messages.getString("GenerateUpdateSiteMojo.unableToLocateDomino")); //$NON-NLS-1$
		}

		try {
			new GenerateUpdateSiteTask(dominoDir, destDir, flattenEmbeds, onlyDots, getLog()).run();
		} catch(Throwable t) {
			throw new MojoExecutionException(Messages.getString("GenerateUpdateSiteMojo.exceptionGeneratingUpdateSite"), t); //$NON-NLS-1$
		}
	}

	private void executeWithDocker(Path destDir) throws MojoExecutionException {
		Builder dockerBuilder = DockerFileManager.newBuilder()
		.withImage(srcImageId)
		.withContainer(srcContainer);

		try(DockerFileManager dockerFileManager = dockerBuilder.build()) {

			Path localPath = extractDockerContent(dockerFileManager); //$NON-NLS-1$

			new GenerateUpdateSiteTask(localPath, destDir, flattenEmbeds, onlyDots, getLog()).run();
		} catch(Throwable t) {
			throw new MojoExecutionException(Messages.getString("GenerateUpdateSiteMojo.dockerHostIssue", srcContainer, srcImageId), t); //$NON-NLS-1$
		}

	}

	// This mothod will extract the content of the Docker container to a temporary directory
	private Path extractDockerContent(DockerFileManager dfm) throws DockerFileManagerException {
		Log log = getLog();
		Path ctDominoDir = Paths.get(dockerDominoDir);

		log.info(Messages.getString("GenerateUpdateSiteMojo.dockerPathExtracting", dockerDominoDir)); //$NON-NLS-1$

		String osgiDir = onlyDots ? "osgi-dots":"osgi";

		List<Path> pathsToExtract = Arrays.asList(
			ctDominoDir.resolve(osgiDir),
			ctDominoDir.resolve("ndext")
		);

		return dfm.downloadFileResources(pathsToExtract, true);
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
