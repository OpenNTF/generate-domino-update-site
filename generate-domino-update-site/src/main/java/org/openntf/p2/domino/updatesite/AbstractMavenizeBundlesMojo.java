/**
 * Copyright © 2018-2025 Contributors to the generate-domino-update-site project
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
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
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
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.osgi.util.ManifestElement;
import org.openntf.nsfodp.commons.NSFODPUtil;
import org.openntf.nsfodp.commons.xml.NSFODPDomUtil;
import org.openntf.p2.domino.updatesite.model.BundleEmbed;
import org.openntf.p2.domino.updatesite.model.BundleInfo;
import org.osgi.framework.Version;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.StreamUtil;

public abstract class AbstractMavenizeBundlesMojo extends AbstractMojo {

	public static final String GROUP_ID = "com.ibm.xsp"; //$NON-NLS-1$
	/**
	 * Source p2 repository
	 */
	@Parameter(property = "src", required = true)
	protected File src;
	/**
	 * The groupId to use for mavenized bundles
	 */
	@Parameter(property = "groupId", required = false, defaultValue = GROUP_ID)
	protected String groupId;
	/**
	 * Whether dependencies between repository bundles derived from {@code Require-Bundle}
	 * should be marked as {@code optional}. 
	 */
	@Parameter(property = "optionalDependencies", required = false)
	protected boolean optionalDependencies = false;
	
	@Parameter(defaultValue = "${project}", readonly = true)
	protected MavenProject mavenProject;
	@Parameter(defaultValue = "${session}", readonly = true)
	protected MavenSession mavenSession;
	@Component
	protected BuildPluginManager pluginManager;
	
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
				.filter(path -> !isSourceBundle(path))
				.map(this::toInfo)
				.filter(Objects::nonNull)
				.forEach(b -> {
					bundles.add(b);
					bundlesByName.put(b.getArtifactId(), b);
				});
		} catch(IOException e) {
			throw new MojoExecutionException(Messages.getString("AbstractMavenizeBundlesMojo.exceptionProcessingBundles")); //$NON-NLS-1$
		}
			
		for(BundleInfo bundle : bundles) {
			Path tempPom;
			try {
				tempPom = generateBundlePom(bundle, basePom, bundlesByName);
			} catch(IOException e) {
				throw new MojoExecutionException(Messages.getString("AbstractMavenizeBundlesMojo.exceptionGeneratingPom"), e); //$NON-NLS-1$
			}
			
			try {
				processBundle(bundle, bundles, bundlesByName, tempPom);
			} finally {
				if(tempPom != null) {
					try {
						Files.deleteIfExists(tempPom);
					} catch (IOException e) {
						getLog().info(MessageFormat.format("Unable to delete temporary file {0}: {1}", tempPom, e));
					}
				}
			}
		}
	}
	
	protected abstract void processBundle(BundleInfo bundle, List<BundleInfo> bundles, Map<String, BundleInfo> bundlesByName, Path tempPom) throws MojoExecutionException;
	
	protected Path generateBundlePom(BundleInfo bundle, String basePom, Map<String, BundleInfo> bundles) throws IOException {
		Document xml = NSFODPDomUtil.createDocument(new StringReader(basePom));
		
		Element project = xml.getDocumentElement();
	
		Element groupIdEl = NSFODPDomUtil.createElement(project, "groupId"); //$NON-NLS-1$
		groupIdEl.setTextContent(this.groupId);
		
		Element artifactId = NSFODPDomUtil.createElement(project, "artifactId"); //$NON-NLS-1$
		artifactId.setTextContent(bundle.getArtifactId());
		
		Element version = NSFODPDomUtil.createElement(project, "version"); //$NON-NLS-1$
		version.setTextContent(bundle.getVersion());
		
		if(StringUtil.isNotEmpty(bundle.getVendor())) {
			Element organization = NSFODPDomUtil.createElement(project, "organization"); //$NON-NLS-1$
			Element name = NSFODPDomUtil.createElement(organization, "name"); //$NON-NLS-1$
			name.setTextContent(bundle.getVendor());
		}
	
		Element dependencies = NSFODPDomUtil.createElement(project, "dependencies"); //$NON-NLS-1$
		
		// Add dependencies based on Require-Bundle
		if(!bundle.getRequires().isEmpty()) {
			for(String require : bundle.getRequires()) {
				BundleInfo dep = bundles.get(require);
				if(dep != null) {
					Element dependency = NSFODPDomUtil.createElement(dependencies, "dependency"); //$NON-NLS-1$
					Element groupId = NSFODPDomUtil.createElement(dependency, "groupId"); //$NON-NLS-1$
					groupId.setTextContent(this.groupId);
					Element depArtifactId = NSFODPDomUtil.createElement(dependency, "artifactId"); //$NON-NLS-1$
					depArtifactId.setTextContent(dep.getArtifactId());
					Element depVersion = NSFODPDomUtil.createElement(dependency, "version"); //$NON-NLS-1$
					depVersion.setTextContent(dep.getVersion());
					if(optionalDependencies) {
						NSFODPDomUtil.createElement(dependency, "optional").setTextContent("true"); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			}
		}
		
		// Add internal dependencies for Bundle-ClassPath entries
		if(!bundle.getEmbeds().isEmpty()) {
			for(BundleEmbed embed : bundle.getEmbeds()) {
				Element dependency = NSFODPDomUtil.createElement(dependencies, "dependency"); //$NON-NLS-1$
				Element groupId = NSFODPDomUtil.createElement(dependency, "groupId"); //$NON-NLS-1$
				groupId.setTextContent(this.groupId);
				Element depArtifactId = NSFODPDomUtil.createElement(dependency, "artifactId"); //$NON-NLS-1$
				depArtifactId.setTextContent(bundle.getArtifactId());
				Element depVersion = NSFODPDomUtil.createElement(dependency, "version"); //$NON-NLS-1$
				depVersion.setTextContent(bundle.getVersion());
				Element depClassifier = NSFODPDomUtil.createElement(dependency, "classifier"); //$NON-NLS-1$
				depClassifier.setTextContent(toEmbedClassifierName(embed.getName()));
			}
		}
		
		// Write out the temporary pom
		Path tempPom = Files.createTempFile(bundle.getArtifactId(), ".pom"); //$NON-NLS-1$
		Files.write(tempPom, NSFODPDomUtil.getXmlString(xml, null).getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
		
		return tempPom;
	}

	protected BundleInfo toInfo(Path path) {
        try (JarFile jarFile = new JarFile(path.toFile())) {
            Manifest manifest = jarFile.getManifest();

            // Look for an appropriate properties bundle
            Properties props = new Properties();
            String propsName = null;
            if (manifest.getMainAttributes().getValue("Fragment-Host") != null) { //$NON-NLS-1$
                propsName = "fragment"; //$NON-NLS-1$
            } else if (manifest.getMainAttributes().getValue("Eclipse-SystemBundle") != null) { //$NON-NLS-1$
                propsName = "systembundle"; //$NON-NLS-1$
            } else {
                propsName = "plugin"; //$NON-NLS-1$
            }
            JarEntry propsEntry = jarFile.getJarEntry(propsName + ".properties"); //$NON-NLS-1$
            if (propsEntry != null) {
                try (InputStream is = jarFile.getInputStream(propsEntry)) {
                    props.load(is);
                }
            }

            // Determine the appropriate Maven metadata
            String artifactId = manifest.getMainAttributes().getValue("Bundle-SymbolicName"); //$NON-NLS-1$
            if (StringUtil.isEmpty(artifactId)) {
                // Account for anomalous V11 JARs
                return null;
            }
            artifactId = StringUtil.trim(artifactId.replaceAll(";.*", "")); //$NON-NLS-1$ //$NON-NLS-2$
            String version = manifest.getMainAttributes().getValue("Bundle-Version"); //$NON-NLS-1$
            String name = manifest.getMainAttributes().getValue("Bundle-Name"); //$NON-NLS-1$
            if (name == null || name.isEmpty()) {
                name = artifactId;
            } else if (name.startsWith("%")) { //$NON-NLS-1$
                name = props.getProperty(name.substring(1));
            }

            String vendor = manifest.getMainAttributes().getValue("Bundle-Vendor"); //$NON-NLS-1$
            if (vendor == null) {
                vendor = ""; //$NON-NLS-1$
            } else if (vendor.startsWith("%")) { //$NON-NLS-1$
                vendor = props.getProperty(vendor.substring(1));
            }

            // Figure out dependencies based on Require-Bundle
            String requires = manifest.getMainAttributes().getValue("Require-Bundle"); //$NON-NLS-1$
            if (requires == null) {
                requires = "";
            } //$NON-NLS-1$
            requires = requires.replaceAll(";bundle-version=\"[^\"]+\"", "")
                               .replaceAll(";[^,]+", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            String[] requireBits = requires.split(","); //$NON-NLS-1$
            List<String> requireEntries = new ArrayList<>();
            for (String bit : requireBits) {
                if (bit != null && !bit.isEmpty()) {
                    requireEntries.add(bit);
                }
            }

            // Look for embedded jars in Bundle-ClassPath
            List<BundleEmbed> embeds = new ArrayList<>();
            String classpath = manifest.getMainAttributes().getValue("Bundle-ClassPath"); //$NON-NLS-1$
            if (classpath != null && !classpath.isEmpty()) {
                String[] cpEntries = classpath.split("\\n|,"); //$NON-NLS-1$
                for (String cpEntry : cpEntries) {
                    if (cpEntry.toLowerCase().endsWith(".jar")) { //$NON-NLS-1$
                        // Extract the jar file to a temp location and add an embed reference
                        JarEntry embedEntry = jarFile.getJarEntry(cpEntry);
                        if (embedEntry != null) {
                            try (InputStream embedIs = jarFile.getInputStream(embedEntry)) {
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
            try (OutputStream os = Files.newOutputStream(tempFile, StandardOpenOption.TRUNCATE_EXISTING)) {
                try (ZipOutputStream zos = new ZipOutputStream(os)) {
                    try (InputStream is = Files.newInputStream(path)) {
                        try (ZipInputStream zis = new ZipInputStream(is)) {
                            ZipEntry entry = zis.getNextEntry();
                            while (entry != null) {
                                zos.putNextEntry(entry);
                                if ("META-INF/MANIFEST.MF".equals(entry.getName())) { //$NON-NLS-1$
                                    Manifest tempManifest = new Manifest();
                                    tempManifest.read(zis);

                                    // Replace the com.ibm.pvc.servlet hard requirement with just servlet imports
                                    String requireBundle = tempManifest.getMainAttributes().getValue("Require-Bundle"); //$NON-NLS-1$
                                    if (StringUtil.isNotEmpty(requireBundle)) {
                                        ManifestElement[] elements = ManifestElement.parseHeader("Require-Bundle", requireBundle); //$NON-NLS-1$
                                        requireBundle = Stream.of(elements)
                                                              .filter(el -> !"com.ibm.pvc.servlet".equals(el.getValue())) //$NON-NLS-1$
                                                              .map(el -> el.toString() + (
                                                                  "optional".equals(el.getDirective("resolution")) ? ""
                                                                      : ";resolution:=optional")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                                                              .collect(Collectors.joining(",")); //$NON-NLS-1$
                                        if (StringUtil.isEmpty(requireBundle)) {
                                            tempManifest.getMainAttributes()
                                                        .remove(new java.util.jar.Attributes.Name("Require-Bundle")); //$NON-NLS-1$
                                        } else {
                                            tempManifest.getMainAttributes().putValue("Require-Bundle", requireBundle); //$NON-NLS-1$
                                        }
                                    }

                                    // Validate the bundle version
                                    String versionString = tempManifest.getMainAttributes().getValue("Bundle-Version"); //$NON-NLS-1$
                                    if (StringUtil.isNotEmpty(versionString)) {
                                        try {
                                            new Version(versionString);
                                        } catch (IllegalArgumentException e) {
                                            // This case should be that there are more than three "."s
                                            String[] bits = versionString.split("\\.", 4); //$NON-NLS-1$
                                            if (bits.length >= 4) {
                                                versionString = bits[0] + "." + bits[1] + "." + bits[2] + "."
                                                    + bits[3].replace(".", "_"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                                                tempManifest.getMainAttributes()
                                                            .putValue("Bundle-Version", versionString); //$NON-NLS-1$
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
                                    tempManifest.getMainAttributes()
                                                .putValue("DynamicImport-Package", String.join(",", imports)); //$NON-NLS-1$ //$NON-NLS-2$

                                    tempManifest.write(zos);
                                } else {
                                    StreamUtil.copyStream(zis, zos);
                                }

                                zis.closeEntry();
                                entry = zis.getNextEntry();
                            }

                            // Very special handling of com.ibm.notes.java.api to make it play nicer in other environments
                            if (path.getFileName().startsWith("com.ibm.notes.java.api_")) { //$NON-NLS-1$
                                // Find the companion fragment
                                Optional<Path> maybeFragment = Files.find(path.getParent(), 0,
                                                                          (p, attr) -> p.getFileName()
                                                                                        .startsWith("com.ibm.notes.java.api.win32.linux_"))
                                                                    .findFirst(); //$NON-NLS-1$
                                if (maybeFragment.isPresent()) {
                                    ZipFile fragmentZip = new ZipFile(maybeFragment.get().toFile());
                                    try {
                                        ZipEntry notesJar = fragmentZip.getEntry("Notes.jar"); //$NON-NLS-1$
                                        if (notesJar != null) {
                                            zos.putNextEntry(notesJar);
                                            try (InputStream nis = fragmentZip.getInputStream(notesJar)) {
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

            // Check for a source bundle
            Path source = null;
            Path potentialSource = path.getParent().resolve(artifactId + ".source_" + version + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
            if (Files.isRegularFile(potentialSource)) {
                // Check if it's marked as a source bundle
                if (isSourceBundle(potentialSource)) {
                    source = potentialSource;
                }
            }

            return new BundleInfo(name, vendor, artifactId, version, tempFile.toAbsolutePath()
                                                                             .toString(), requireEntries, embeds, source);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
	}

	public static String toEmbedClassifierName(String embedName) {
		return embedName.substring(0, embedName.lastIndexOf('.')).replace('/', '$');
	}
	
	private static final boolean isSourceBundle(Path path) {
		if(!path.getFileName().toString().contains(".source_")) { //$NON-NLS-1$
			return false;
		}
		
		try(FileSystem sourceFs = NSFODPUtil.openZipPath(path)) {
			Path root = sourceFs.getPath("/"); //$NON-NLS-1$
			Path manifestPath = root.resolve("META-INF").resolve("MANIFEST.MF"); //$NON-NLS-1$ //$NON-NLS-2$
			if(Files.exists(manifestPath)) {
				Manifest sourceManifest;
				try(InputStream is = Files.newInputStream(manifestPath)) {
					sourceManifest = new Manifest(is);
				}
				String sourceBundle = sourceManifest.getMainAttributes().getValue("Eclipse-SourceBundle"); //$NON-NLS-1$
				return StringUtil.isNotEmpty(sourceBundle);
			}
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
		
		return false;
	}
}
