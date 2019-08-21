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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.StreamUtil;
import com.ibm.commons.xml.DOMUtil;
import com.ibm.commons.xml.XMLException;

import lombok.SneakyThrows;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Mavenizes any bundles in the provided XPages p2 directory.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
@Mojo(name="mavenizeBundles", requiresProject=false)
public class MavenizeBundlesMojo extends AbstractMojo {
	
	private static final String GROUP_ID = "com.ibm.xsp"; //$NON-NLS-1$
	
	/**
	 * Source XPages p2 directory
	 */
	@Parameter(property="src", required=true)
	private File src;
	
	@Parameter(property="groupId", required=false, defaultValue=GROUP_ID)
	private String groupId;
	
	@Component
	private MavenProject mavenProject;

	@Component
	private MavenSession mavenSession;

	@Component
	private BuildPluginManager pluginManager;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Map<String, BundleInfo> bundles = new HashMap<>();
		String basePom;
		
		try {
			Path bundlesDir = src.toPath();
			if(Files.exists(bundlesDir.resolve("plugins"))) {
				bundlesDir = bundlesDir.resolve("plugins");
			}
			
			try(InputStream is = getClass().getResourceAsStream("/basePom.xml")) {
				basePom = StreamUtil.readString(is);
			}
			
			Files.list(bundlesDir)
				.filter(path -> path.toString().toLowerCase().endsWith(".jar"))
				.map(this::toInfo)
				.filter(Objects::nonNull)
				.forEach(b -> bundles.put(b.artifactId, b));
		} catch(IOException e) {
			throw new MojoExecutionException("Exception while processing bundles");
		}
			
		for(BundleInfo bundle : bundles.values()) {
			Path tempPom;
			try {
				tempPom = generateBundlePom(bundle, basePom, bundles);
			} catch(XMLException | IOException e) {
				throw new MojoExecutionException("Exception while generating temporary pom", e);
			}
			
			executeMojo(
				plugin(
					groupId("org.apache.maven.plugins"),
					artifactId("maven-install-plugin"),
					version("2.5.2")
				),
				goal("install-file"),
				configuration(
					element("file", bundle.filePath),
					element("groupId", groupId),
					element("artifactId", bundle.artifactId),
					element("version", bundle.version),
					element("packaging", "jar"),
					element("pomFile", tempPom.toString())
				),
				executionEnvironment(
					mavenProject,
	        		mavenSession,
	        		pluginManager
				)
			);
			
			// Generate additional executions for each embed
			for(BundleEmbed embed : bundle.embeds) {
				String baseName = embed.name.substring(0, embed.name.lastIndexOf('.'));
				
				executeMojo(
					plugin(
						groupId("org.apache.maven.plugins"),
						artifactId("maven-install-plugin"),
						version("2.5.2")
					),
					goal("install-file"),
					configuration(
						element("file", embed.file.toString()),
						element("groupId", groupId),
						element("artifactId", bundle.artifactId),
						element("version", bundle.version),
						element("packaging", "jar"),
						element("classifier", baseName)
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
	
	private Path generateBundlePom(BundleInfo bundle, String basePom, Map<String, BundleInfo> bundles) throws XMLException, IOException {
		Document xml = DOMUtil.createDocument(basePom);
		
		Element project = xml.getDocumentElement();

		Element groupIdEl = DOMUtil.createElement(xml, project, "groupId"); //$NON-NLS-1$
		groupIdEl.setTextContent(this.groupId);
		
		Element artifactId = DOMUtil.createElement(xml, project, "artifactId"); //$NON-NLS-1$
		artifactId.setTextContent(bundle.artifactId);
		
		Element version = DOMUtil.createElement(xml, project, "version"); //$NON-NLS-1$
		version.setTextContent(bundle.version);
		
		if(StringUtil.isNotEmpty(bundle.vendor)) {
			Element organization = DOMUtil.createElement(xml, project, "organization"); //$NON-NLS-1$
			Element name = DOMUtil.createElement(xml, organization, "name"); //$NON-NLS-1$
			name.setTextContent(bundle.vendor);
		}
		
		if(!bundle.requires.isEmpty()) {
			Element dependencies = DOMUtil.createElement(xml, project, "dependencies"); //$NON-NLS-1$
			for(String require : bundle.requires) {
				BundleInfo dep = bundles.get(require);
				if(dep != null) {
					Element dependency = DOMUtil.createElement(xml, dependencies, "dependency"); //$NON-NLS-1$
					Element groupId = DOMUtil.createElement(xml, dependency, "groupId"); //$NON-NLS-1$
					groupId.setTextContent(this.groupId);
					Element depArtifactId = DOMUtil.createElement(xml, dependency, "artifactId"); //$NON-NLS-1$
					depArtifactId.setTextContent(dep.artifactId);
					Element depVersion = DOMUtil.createElement(xml, dependency, "version"); //$NON-NLS-1$
					depVersion.setTextContent(dep.version);
				}
			}
		}
		
		// Write out the temporary pom
		Path tempPom = Files.createTempFile(bundle.artifactId, ".pom"); //$NON-NLS-1$
		tempPom.toFile().deleteOnExit();
		Files.write(tempPom, DOMUtil.getXMLString(xml).getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
		
		return tempPom;
	}
	
	@SneakyThrows
	private BundleInfo toInfo(Path path) {
		JarFile jarFile = new JarFile(path.toFile());
		try {
			Manifest manifest = jarFile.getManifest();
			
			
			// Look for an appropriate properties bundle
			Properties props = new Properties();
			String propsName = null;
			if(manifest.getMainAttributes().getValue("Fragment-Host") != null) { //$NON-NLS-1$
				propsName = "fragment"; //$NON-NLS-1$
			} else if(manifest.getMainAttributes().getValue("Eclipse-SystemBundle") != null) { //$NON-NLS-1$
				propsName = "systembundle"; //$NON-NLS-1$
			} else {
				propsName = "plugin"; //$NON-NLS-1$
			}
			JarEntry propsEntry = jarFile.getJarEntry(propsName + ".properties"); //$NON-NLS-1$
			if(propsEntry != null) {
				try(InputStream is = jarFile.getInputStream(propsEntry)) {
					props.load(is);
				}
			}
			
			// Determine the appropriate Maven metadata
			String artifactId = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
			if(StringUtil.isEmpty(artifactId)) {
				// Account for anomalous V11 JARs
				return null;
			}
			artifactId = StringUtil.trim(artifactId.replaceAll(";.*", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String version = manifest.getMainAttributes().getValue("Bundle-Version"); //$NON-NLS-1$
			String name = manifest.getMainAttributes().getValue("Bundle-Name"); //$NON-NLS-1$
			if(name == null || name.isEmpty()) {
				name = artifactId;
			} else if(name.startsWith("%")) { //$NON-NLS-1$
				name = props.getProperty(name.substring(1));
			}
			
			String vendor = manifest.getMainAttributes().getValue("Bundle-Vendor"); //$NON-NLS-1$
			if(vendor == null) {
				vendor = ""; //$NON-NLS-1$
			} else if(vendor.startsWith("%")) { //$NON-NLS-1$
				vendor = props.getProperty(vendor.substring(1));
			}
			
			
			// Figure out dependencies based on Require-Bundle
			String requires = manifest.getMainAttributes().getValue("Require-Bundle"); //$NON-NLS-1$
			if(requires == null) { requires = ""; } //$NON-NLS-1$
			requires = requires.replaceAll(";bundle-version=\"[^\"]+\"", "").replaceAll(";[^,]+", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			String[] requireBits = requires.split(","); //$NON-NLS-1$
			List<String> requireEntries = new ArrayList<>();
			for(String bit : requireBits) {
				if(bit != null && !bit.isEmpty()) {
					requireEntries.add(bit);
				}
			}
			
			// Look for embedded jars in Bundle-ClassPath
			List<BundleEmbed> embeds = new ArrayList<>();
			String classpath = manifest.getMainAttributes().getValue("Bundle-ClassPath"); //$NON-NLS-1$
			if(classpath != null && !classpath.isEmpty()) {
				String[] cpEntries = classpath.split("\\n|,"); //$NON-NLS-1$
				for(String cpEntry : cpEntries) {
					if(cpEntry.toLowerCase().endsWith(".jar")) { //$NON-NLS-1$
						// Extract the jar file to a temp location and add an embed reference
						JarEntry embedEntry = jarFile.getJarEntry(cpEntry);
						if(embedEntry != null) {
							try(InputStream embedIs = jarFile.getInputStream(embedEntry)) {
								File embedName = new File(cpEntry);
								Path embedFile = Files.createTempFile(embedName.getName(), ".jar"); //$NON-NLS-1$
								embedFile.toFile().deleteOnExit();
								Files.copy(embedIs, embedFile, StandardCopyOption.REPLACE_EXISTING);
								
								embeds.add(new BundleEmbed(embedName.getName(), embedFile));
							}
						}
					}
				}
			}
			
			return new BundleInfo(name, vendor, artifactId, version, path.toAbsolutePath().toString(), requireEntries, embeds);	
		} finally {
			jarFile.close();
		}
	}
	
	// *******************************************************************************
	// * Bundle representation classes
	// *******************************************************************************
		
	private static class BundleInfo {
		private final String name;
		private final String vendor;
		private final String artifactId;
		private final String version;
		private final String filePath;
		private final List<String> requires;
		private final List<BundleEmbed> embeds;
		
		public BundleInfo(String name, String vendor, String artifactId, String version, String filePath, List<String> requires, List<BundleEmbed> embeds) {
			this.name = name;
			this.vendor = vendor;
			this.artifactId = artifactId;
			this.version = version;
			this.filePath = filePath;
			this.requires = requires;
			this.embeds = embeds;
		}
		
		@Override
		public String toString() {
			return MessageFormat.format("[{0}: name={1}, vendor={2}, artifactId={3}, version={4}, filePath={5}, requires={6}, embeds={7}]", //$NON-NLS-1$
					getClass().getSimpleName(),
					name,
					vendor,
					artifactId,
					version,
					filePath,
					requires,
					embeds
			);
		}
	}
	
	private static class BundleEmbed {
		private final String name;
		private final Path file;
		
		public BundleEmbed(String name, Path file) {
			this.name = name;
			this.file = file;
		}
		
		@Override
		public String toString() {
			return MessageFormat.format("[{0}: name={1}]", getClass().getSimpleName(), name); //$NON-NLS-1$
		}
	}

}
