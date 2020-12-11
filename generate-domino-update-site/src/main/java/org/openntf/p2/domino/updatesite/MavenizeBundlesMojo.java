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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.osgi.util.ManifestElement;
import org.openntf.p2.domino.updatesite.model.BundleEmbed;
import org.openntf.p2.domino.updatesite.model.BundleInfo;
import org.osgi.framework.Version;
import org.twdata.maven.mojoexecutor.MojoExecutor;
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
 * @see Consider using <a href="https://github.com/OpenNTF/p2-layout-provider">p2-layout-provider</a> instead
 */
@Mojo(name="mavenizeBundles", requiresProject=false)
public class MavenizeBundlesMojo extends AbstractMavenizeBundlesMojo {
	
	private static final String GROUP_ID = "com.ibm.xsp"; //$NON-NLS-1$
	
	/**
	 * Source XPages p2 directory
	 */
	@Parameter(property="src", required=true)
	private File src;
	
	@Parameter(property="groupId", required=false, defaultValue=GROUP_ID)
	private String groupId;
	
	@Parameter(property="optionalDependencies", required=false)
	private boolean optionalDependencies = true;
	
	/**
	 * Specifies an alternate local repository for the installation.
	 * 
	 * @since 3.4.0
	 */
	@Parameter(property="localRepositoryPath", required=false)
	private File localRepositoryPath;
	
	@Component
	private MavenProject mavenProject;

	@Component
	private MavenSession mavenSession;

	@Component
	private BuildPluginManager pluginManager;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		List<BundleInfo> bundles = new ArrayList<>();
		Map<String, BundleInfo> bundlesByName = new HashMap<>();
		String basePom;
		
		try {
			Path bundlesDir = src.toPath();
			if(Files.exists(bundlesDir.resolve("plugins"))) { //$NON-NLS-1$
				bundlesDir = bundlesDir.resolve("plugins"); //$NON-NLS-1$
			}
			
			try(InputStream is = getClass().getResourceAsStream("/basePom.xml")) { //$NON-NLS-1$
				basePom = StreamUtil.readString(is);
			}
			
			Files.list(bundlesDir)
				.filter(path -> path.toString().toLowerCase().endsWith(".jar")) //$NON-NLS-1$
				.map(this::toInfo)
				.filter(Objects::nonNull)
				.forEach(b -> {
					bundles.add(b);
					bundlesByName.put(b.getArtifactId(), b);
				});
		} catch(IOException e) {
			throw new MojoExecutionException("Exception while processing bundles");
		}
			
		for(BundleInfo bundle : bundles) {
			Path tempPom;
			try {
				tempPom = generateBundlePom(bundle, basePom, bundlesByName);
			} catch(XMLException | IOException e) {
				throw new MojoExecutionException("Exception while generating temporary pom", e);
			}
			
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
				String baseName = embed.getName().substring(0, embed.getName().lastIndexOf('.'));
				
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
		}
	}
	
	protected Path generateBundlePom(BundleInfo bundle, String basePom, Map<String, BundleInfo> bundles) throws XMLException, IOException {
		Document xml = DOMUtil.createDocument(basePom);
		
		Element project = xml.getDocumentElement();

		Element groupIdEl = DOMUtil.createElement(xml, project, "groupId"); //$NON-NLS-1$
		groupIdEl.setTextContent(this.groupId);
		
		Element artifactId = DOMUtil.createElement(xml, project, "artifactId"); //$NON-NLS-1$
		artifactId.setTextContent(bundle.getArtifactId());
		
		Element version = DOMUtil.createElement(xml, project, "version"); //$NON-NLS-1$
		version.setTextContent(bundle.getVersion());
		
		if(StringUtil.isNotEmpty(bundle.getVendor())) {
			Element organization = DOMUtil.createElement(xml, project, "organization"); //$NON-NLS-1$
			Element name = DOMUtil.createElement(xml, organization, "name"); //$NON-NLS-1$
			name.setTextContent(bundle.getVendor());
		}

		Element dependencies = DOMUtil.createElement(xml, project, "dependencies"); //$NON-NLS-1$
		
		// Add dependencies based on Require-Bundle
		if(!bundle.getRequires().isEmpty()) {
			for(String require : bundle.getRequires()) {
				BundleInfo dep = bundles.get(require);
				if(dep != null) {
					Element dependency = DOMUtil.createElement(xml, dependencies, "dependency"); //$NON-NLS-1$
					Element groupId = DOMUtil.createElement(xml, dependency, "groupId"); //$NON-NLS-1$
					groupId.setTextContent(this.groupId);
					Element depArtifactId = DOMUtil.createElement(xml, dependency, "artifactId"); //$NON-NLS-1$
					depArtifactId.setTextContent(dep.getArtifactId());
					Element depVersion = DOMUtil.createElement(xml, dependency, "version"); //$NON-NLS-1$
					depVersion.setTextContent(dep.getVersion());
					if(optionalDependencies) {
						DOMUtil.createElement(xml, dependency, "optional").setTextContent("true"); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			}
		}
		
		// Add internal dependencies for Bundle-ClassPath entries
		if(!bundle.getEmbeds().isEmpty()) {
			for(BundleEmbed embed : bundle.getEmbeds()) {
				Element dependency = DOMUtil.createElement(xml, dependencies, "dependency"); //$NON-NLS-1$
				Element groupId = DOMUtil.createElement(xml, dependency, "groupId"); //$NON-NLS-1$
				groupId.setTextContent(this.groupId);
				Element depArtifactId = DOMUtil.createElement(xml, dependency, "artifactId"); //$NON-NLS-1$
				depArtifactId.setTextContent(bundle.getArtifactId());
				Element depVersion = DOMUtil.createElement(xml, dependency, "version"); //$NON-NLS-1$
				depVersion.setTextContent(bundle.getVersion());
				Element depClassifier = DOMUtil.createElement(xml, dependency, "classifier"); //$NON-NLS-1$
				depClassifier.setTextContent(embed.getName());
			}
		}
		
		// Write out the temporary pom
		Path tempPom = Files.createTempFile(bundle.getArtifactId(), ".pom"); //$NON-NLS-1$
		tempPom.toFile().deleteOnExit();
		Files.write(tempPom, DOMUtil.getXMLString(xml).getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
		
		return tempPom;
	}
	
	@SneakyThrows
	protected BundleInfo toInfo(Path path) {
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
			String artifactId = manifest.getMainAttributes().getValue("Bundle-SymbolicName"); //$NON-NLS-1$
			if(StringUtil.isEmpty(artifactId)) {
				// Account for anomalous V11 JARs
				return null;
			}
			artifactId = StringUtil.trim(artifactId.replaceAll(";.*", "")); //$NON-NLS-1$ //$NON-NLS-2$
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
								Path embedPath = Paths.get(cpEntry);
								String embedName = embedPath.getFileName().toString().replace('/', '$');
								
								Path embedFile = Files.createTempFile(embedName, ".jar"); //$NON-NLS-1$
								embedFile.toFile().deleteOnExit();
								Files.copy(embedIs, embedFile, StandardCopyOption.REPLACE_EXISTING);
								
								embeds.add(new BundleEmbed(embedName, embedFile));
							}
						}
					}
				}
			}
			
			// Create a copy of the JAR in a temp location to tweak the requirements
			Path tempFile = Files.createTempFile(path.getFileName().toString(), ".jar"); //$NON-NLS-1$
			tempFile.toFile().deleteOnExit();
			try(OutputStream os = Files.newOutputStream(tempFile, StandardOpenOption.TRUNCATE_EXISTING)) {
				try(ZipOutputStream zos = new ZipOutputStream(os)) {
					try(InputStream is = Files.newInputStream(path)) {
						try(ZipInputStream zis = new ZipInputStream(is)) {
							ZipEntry entry = zis.getNextEntry();
							while(entry != null) {
								zos.putNextEntry(entry);
								if("META-INF/MANIFEST.MF".equals(entry.getName())) { //$NON-NLS-1$
									Manifest tempManifest = new Manifest();
									tempManifest.read(zis);
									
									// Replace the com.ibm.pvc.servlet hard requirement with just servlet imports
									String requireBundle = tempManifest.getMainAttributes().getValue("Require-Bundle"); //$NON-NLS-1$
									if(StringUtil.isNotEmpty(requireBundle)) {
										ManifestElement[] elements = ManifestElement.parseHeader("Require-Bundle", requireBundle); //$NON-NLS-1$
										requireBundle = Stream.of(elements)
											.filter(el -> !"com.ibm.pvc.servlet".equals(el.getValue())) //$NON-NLS-1$
											.map(el -> el.toString() + ("optional".equals(el.getDirective("resolution")) ? "" : ";resolution:=optional")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
											.collect(Collectors.joining(",")); //$NON-NLS-1$
										if(StringUtil.isEmpty(requireBundle)) {
											tempManifest.getMainAttributes().remove(new java.util.jar.Attributes.Name("Require-Bundle")); //$NON-NLS-1$
										} else {
											tempManifest.getMainAttributes().putValue("Require-Bundle", requireBundle); //$NON-NLS-1$
										}
									}
									
									// Validate the bundle version
									String versionString = tempManifest.getMainAttributes().getValue("Bundle-Version"); //$NON-NLS-1$
									if(StringUtil.isNotEmpty(versionString)) {
										try {
											new Version(versionString);
										} catch(IllegalArgumentException e) {
											// This case should be that there are more than three "."s
											String[] bits = versionString.split("\\.", 4); //$NON-NLS-1$
											if(bits.length >= 4) {
												versionString = bits[0] + "." + bits[1] + "." + bits[2] + "." + bits[3].replace(".", "_"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
												tempManifest.getMainAttributes().putValue("Bundle-Version", versionString); //$NON-NLS-1$
											} else {
												throw e;
											}
										}
									}
									
									// Cover cases where bundles no longer automatically get javax.servlet transitively, as well
									//   as some XML things that these bundles assume are passively available, but which newer OSGi
									//   containers may not automatically provide
									List<String> imports = Arrays.asList(
										"javax.servlet", //$NON-NLS-1$
										"javax.servlet.*", //$NON-NLS-1$
										"org.w3c.dom", //$NON-NLS-1$
										"org.w3c.dom.*", //$NON-NLS-1$
										"org.xml.*", //$NON-NLS-1$
										"javax.xml.*", //$NON-NLS-1$
										"lotus.*" //$NON-NLS-1$
									);
									tempManifest.getMainAttributes().putValue("DynamicImport-Package", String.join(",", imports)); //$NON-NLS-1$ //$NON-NLS-2$
									
									tempManifest.write(zos);
								} else {
									StreamUtil.copyStream(zis, zos);
								}
								
								zis.closeEntry();
								entry = zis.getNextEntry();
							}
							
							// Very special handling of com.ibm.notes.java.api to make it play nicer in other environments
							if(path.getFileName().startsWith("com.ibm.notes.java.api_")) { //$NON-NLS-1$
								// Find the companion fragment
								Optional<Path> maybeFragment = Files.find(path.getParent(), 0,
										(p, attr) -> p.getFileName().startsWith("com.ibm.notes.java.api.win32.linux_")).findFirst(); //$NON-NLS-1$
								if(maybeFragment.isPresent()) {
									ZipFile fragmentZip = new ZipFile(maybeFragment.get().toFile());
									try {
										ZipEntry notesJar = fragmentZip.getEntry("Notes.jar"); //$NON-NLS-1$
										if(notesJar != null) {
											zos.putNextEntry(notesJar);
											try(InputStream nis = fragmentZip.getInputStream(notesJar)) {
												StreamUtil.copyStream(nis, zos);
											}
										}
									} finally {
										fragmentZip.close();
									}
								}
							}
						}
					}
				}
			}
			
			return new BundleInfo(name, vendor, artifactId, version, tempFile.toAbsolutePath().toString(), requireEntries, embeds);	
		} finally {
			jarFile.close();
		}
	}


}
