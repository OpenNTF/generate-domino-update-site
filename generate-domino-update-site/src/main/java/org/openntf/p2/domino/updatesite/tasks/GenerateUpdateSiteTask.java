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
package org.openntf.p2.domino.updatesite.tasks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.openntf.nsfodp.commons.NSFODPUtil;
import org.openntf.nsfodp.commons.xml.NSFODPDomUtil;
import org.openntf.p2.domino.updatesite.Messages;
import org.openntf.p2.domino.updatesite.util.VersionUtil;
import org.tukaani.xz.XZInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.ibm.commons.util.PathUtil;
import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.StreamUtil;

public class GenerateUpdateSiteTask implements Runnable {
	private static final Pattern FEATURE_FILENAME_PATTERN = Pattern.compile("^(.+)_(\\d.+)\\.jar$"); //$NON-NLS-1$
	private static final Pattern BUNDLE_FILENAME_PATTERN = FEATURE_FILENAME_PATTERN;
	private static final Set<String> EXCLUDED_FILENAMES = new HashSet<>();
	static {
		EXCLUDED_FILENAMES.addAll(Arrays.asList(
			".DS_STORE", //$NON-NLS-1$
			
			// Signature manifests will be invalid
			"IBM_WPLC.SF", //$NON-NLS-1$
			"IBM_WPLC.RSA", //$NON-NLS-1$
			"SUNCERT.RSA", //$NON-NLS-1$
			"SUNCERT.SF" //$NON-NLS-1$
		));
	}
	
	/**
	 * This is the public Eclipse update site that best matches what's found in 9.0.1FP10 to current (11.0.1).
	 */
	public static final String NEON_UPDATE_SITE = "https://download.eclipse.org/releases/neon/201612211000"; //$NON-NLS-1$

	private final Path dominoDir;
	private final Path destDir;
	private final boolean flattenEmbeds;

	public GenerateUpdateSiteTask(Path dominoDir, Path destDir, boolean flattenEmbeds) {
		super();
		this.dominoDir = dominoDir;
		this.destDir = destDir;
		this.flattenEmbeds = flattenEmbeds;
	}

	@Override
	public void run() {
		Path domino = checkDirectory(dominoDir);
		
		List<Path> eclipsePaths = findEclipsePaths(domino);
		Path notesJar = findNotesJar(domino)
				.orElseThrow(() -> new IllegalArgumentException(Messages.getString("GenerateUpdateSiteTask.unableToLocateLibExtJar", "Notes.jar", domino))); //$NON-NLS-1$ //$NON-NLS-2$

		try {
			Document eclipseArtifacts = fetchEclipseArtifacts();
			
			Path dest = mkDir(destDir);
			Path destFeatures = mkDir(dest.resolve("features")); //$NON-NLS-1$
			Path destPlugins = mkDir(dest.resolve("plugins")); //$NON-NLS-1$
			
			for(Path eclipse : eclipsePaths) {
				Path features = eclipse.resolve("features"); //$NON-NLS-1$
				if(Files.isDirectory(features)) {
					copyArtifacts(features, destFeatures, null);
				}
				Path plugins = eclipse.resolve("plugins"); //$NON-NLS-1$
				if(Files.isDirectory(plugins)) {
					copyArtifacts(plugins, destPlugins, eclipseArtifacts);
				}
			}
			
			// Create a Notes.jar wrapper bundle pair

			String baseVersion = readNotesVersion(notesJar);
			String version = baseVersion + "-1500"; //$NON-NLS-1$
			{
				String bundleId = "com.ibm.notes.java.api"; //$NON-NLS-1$
				// Create the Notes API plugin for the true version, since the shipping plugin one is often out of step
				if(Files.list(destPlugins).noneMatch(p -> p.getFileName().toString().startsWith(bundleId + '_' + baseVersion))) {
					// "-1500" is taken from common suffixes seen from IBM/HCL
					Path plugin = destPlugins.resolve(bundleId + "_" + version + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
					try (OutputStream fos = Files.newOutputStream(plugin, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
						try (JarOutputStream jos = new JarOutputStream(fos)) {
							jos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF")); //$NON-NLS-1$
							Manifest manifest = new Manifest();
							Attributes attrs = manifest.getMainAttributes();
							attrs.putValue("Manifest-Version", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-SymbolicName", bundleId + ";singleton:=true"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-Vendor", "IBM"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-Name", "Notes Java API"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-Version", version); //$NON-NLS-1$
							attrs.putValue("Bundle-ManifestVersion", "2"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Eclipse-ExtensibleAPI", "true"); //$NON-NLS-1$ //$NON-NLS-2$

							// Find the packages to export from the Notes.jar
							try (JarFile notesJarFile = new JarFile(notesJar.toFile())) {
								String exports = notesJarFile.stream()
										.map(jarEntry -> Paths.get(jarEntry.getName()).getParent())
										.filter(Objects::nonNull)
										.map(path -> path.toString().replace('/', '.').replace('\\', '.'))
										.distinct()
										.filter(Objects::nonNull)
										.filter(name -> !"META-INF".equals(name)) //$NON-NLS-1$
										.collect(Collectors.joining(",")); //$NON-NLS-1$
								attrs.putValue("Export-Package", exports); //$NON-NLS-1$
							}

							manifest.write(jos);
							jos.closeEntry();
						}
					}
				}

				// Create the faux Notes.jar fragment
				{
					String fragmentId = "com.ibm.notes.java.api.win32.linux"; //$NON-NLS-1$
					Path plugin = destPlugins.resolve(fragmentId + "_" + version + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
					
					try(FileSystem zip = NSFODPUtil.openZipPath(plugin)) {
						Path root = zip.getPath("/"); //$NON-NLS-1$
						// Write the manifest file to declare it a fragment
						Path manifestPath = root.resolve("META-INF").resolve("MANIFEST.MF"); //$NON-NLS-1$ //$NON-NLS-2$
						Files.createDirectories(manifestPath.getParent());
						try(OutputStream os = Files.newOutputStream(manifestPath, StandardOpenOption.CREATE)) {
							Manifest manifest = new Manifest();
							Attributes attrs = manifest.getMainAttributes();
							attrs.putValue("Manifest-Version", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
							if(!this.flattenEmbeds) {
								attrs.putValue("Bundle-ClassPath", "Notes.jar"); //$NON-NLS-1$ //$NON-NLS-2$
							}
							attrs.putValue("Bundle-Vendor", "IBM"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Fragment-Host", "com.ibm.notes.java.api"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-Name", "Notes Java API Windows and Linux Fragment"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-SymbolicName", fragmentId + ";singleton:=true"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-Version", version); //$NON-NLS-1$
							attrs.putValue("Bundle-ManifestVersion", "2"); //$NON-NLS-1$ //$NON-NLS-2$
							manifest.write(os);
						}
						
						// Either copy in the contents of the source or just bring in the JAR outright
						if(this.flattenEmbeds) {
							try(FileSystem notesFs = NSFODPUtil.openZipPath(notesJar)) {
								Path notesRoot = notesFs.getPath("/"); //$NON-NLS-1$
								copyBundleEmbed(notesRoot, root);
							}
						} else {
							Files.copy(notesJar, root.resolve("Notes.jar")); //$NON-NLS-1$
						}
					}
				}
			}
			
			// Create an XSP HTTP Bootstrap bundle, if possible
			{
				Optional<Path> xspBootstrap = findXspBootstrap(domino);
				if(xspBootstrap.isPresent()) {
					String bundleId = "com.ibm.xsp.http.bootstrap"; //$NON-NLS-1$
					Path plugin = destPlugins.resolve(bundleId + "_" + version + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
					try(FileSystem zip = NSFODPUtil.openZipPath(plugin)) {
						Path root = zip.getPath("/"); //$NON-NLS-1$
						// Write the manifest file to declare it a fragment
						Path manifestPath = root.resolve("META-INF").resolve("MANIFEST.MF"); //$NON-NLS-1$ //$NON-NLS-2$
						Files.createDirectories(manifestPath.getParent());
						try(OutputStream os = Files.newOutputStream(manifestPath, StandardOpenOption.CREATE)) {
							Manifest manifest = new Manifest();
							Attributes attrs = manifest.getMainAttributes();
							attrs.putValue("Manifest-Version", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
							if(!this.flattenEmbeds) {
								attrs.putValue("Bundle-ClassPath", "xsp.http.bootstrap.jar"); //$NON-NLS-1$ //$NON-NLS-2$
							}
							attrs.putValue("Bundle-Vendor", "IBM"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-Name", "XSP HTTP Bootstrap"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-SymbolicName", bundleId); //$NON-NLS-1$
							attrs.putValue("Bundle-Version", version); //$NON-NLS-1$
							attrs.putValue("Bundle-ManifestVersion", "2"); //$NON-NLS-1$ //$NON-NLS-2$
							// Find the packages to export
							try (JarFile notesJarFile = new JarFile(xspBootstrap.get().toFile())) {
								String exports = notesJarFile.stream()
										.filter(jarEntry -> StringUtil.toString(jarEntry.getName()).endsWith(".class")) //$NON-NLS-1$
										.map(jarEntry -> Paths.get(jarEntry.getName()).getParent())
										.filter(Objects::nonNull)
										.map(path -> path.toString().replace('/', '.').replace('\\', '.'))
										.distinct()
										.filter(Objects::nonNull)
										.filter(name -> !"META-INF".equals(name)) //$NON-NLS-1$
										.collect(Collectors.joining(",")); //$NON-NLS-1$
								attrs.putValue("Export-Package", exports); //$NON-NLS-1$
							}
							manifest.write(os);
						}
						
						// Either copy in the contents of the source or just bring in the JAR outright
						if(this.flattenEmbeds) {
							try(FileSystem notesFs = NSFODPUtil.openZipPath(xspBootstrap.get())) {
								Path notesRoot = notesFs.getPath("/"); //$NON-NLS-1$
								copyBundleEmbed(notesRoot, root);
							}
						} else {
							Files.copy(xspBootstrap.get(), root.resolve("xsp.http.bootstrap.jar")); //$NON-NLS-1$
						}
					}
				} else {
					System.out.println(Messages.getString("GenerateUpdateSiteTask.0")); //$NON-NLS-1$
				}
			}
			
			// Build a NAPI fragment if on 12.0.2+
			findNapiJar(domino).ifPresent(napiJar -> {
				try {
					// Find the existing bundle to get its version number
					Path napiBundle = Files.list(destPlugins)
						.filter(p -> p.getFileName().toString().startsWith("com.ibm.domino.napi_")) //$NON-NLS-1$
						.findFirst()
						.orElseThrow(() -> new IllegalStateException(Messages.getString("GenerateUpdateSiteTask.unableToFindNapiBundle", destPlugins))); //$NON-NLS-1$
					
					// Rewrite the MANIFEST.MF to allow Eclipse to resolve fragment classes
					{
						Path tempBundle = Files.createTempFile("com.ibm.domino.napi", ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
						
						try(
							InputStream is = Files.newInputStream(napiBundle);
							JarInputStream jis = new JarInputStream(is);
							OutputStream os = Files.newOutputStream(tempBundle, StandardOpenOption.TRUNCATE_EXISTING);
							JarOutputStream jos = new JarOutputStream(os)
						) {
							JarEntry sourceEntry;
							while((sourceEntry = jis.getNextJarEntry()) != null) {
								jos.putNextEntry(sourceEntry);
								if("META-INF/MANIFEST.MF".equals(sourceEntry.getName())) { //$NON-NLS-1$
									// Modify the manifest
									Manifest napiManifest = new Manifest(jis);
									napiManifest.getMainAttributes().putValue("Eclipse-ExtensibleAPI", "true"); //$NON-NLS-1$ //$NON-NLS-2$
									napiManifest.write(jos);
								} else {
									// Otherwise, copy cleanly
									StreamUtil.copyStream(jis, jos);
								}
							}
						}
						
						Files.delete(napiBundle);
						Files.move(tempBundle, napiBundle);
						
					}
					
					String napiVersion = napiBundle.getFileName().toString().substring("com.ibm.domino.napi_".length()); //$NON-NLS-1$
					napiVersion = napiVersion.substring(0, version.length()-".jar".length()); //$NON-NLS-1$
					
					// Create the fragment to house the JAR
					{
						String fragmentId = "com.ibm.domino.napi.impl"; //$NON-NLS-1$
						Path plugin = destPlugins.resolve(fragmentId + "_" + version + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
						try(FileSystem zip = NSFODPUtil.openZipPath(plugin)) {
							Path root = zip.getPath("/"); //$NON-NLS-1$
							// Write the manifest file to declare it a fragment
							Path manifestPath = root.resolve("META-INF").resolve("MANIFEST.MF"); //$NON-NLS-1$ //$NON-NLS-2$
							Files.createDirectories(manifestPath.getParent());
							try(OutputStream os = Files.newOutputStream(manifestPath, StandardOpenOption.CREATE)) {
								Manifest manifest = new Manifest();
								Attributes attrs = manifest.getMainAttributes();
								attrs.putValue("Manifest-Version", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
								if(!this.flattenEmbeds) {
									attrs.putValue("Bundle-ClassPath", "lwpd.domino.napi.jar"); //$NON-NLS-1$ //$NON-NLS-2$
								}
								attrs.putValue("Bundle-Vendor", "IBM"); //$NON-NLS-1$ //$NON-NLS-2$
								attrs.putValue("Fragment-Host", "com.ibm.domino.napi"); //$NON-NLS-1$ //$NON-NLS-2$
								attrs.putValue("Bundle-Name", "Api Plug-in Implementation JAR"); //$NON-NLS-1$ //$NON-NLS-2$
								attrs.putValue("Bundle-SymbolicName", fragmentId + ";singleton:=true"); //$NON-NLS-1$ //$NON-NLS-2$
								attrs.putValue("Bundle-Version", version); //$NON-NLS-1$
								attrs.putValue("Bundle-ManifestVersion", "2"); //$NON-NLS-1$ //$NON-NLS-2$
								attrs.putValue("Require-Bundle", "com.ibm.notes.java.api,com.ibm.commons,org.eclipse.core.runtime"); //$NON-NLS-1$ //$NON-NLS-2$
								manifest.write(os);
							}
							
							// Either copy in the contents of the source or just bring in the JAR outright
							if(this.flattenEmbeds) {
								try(FileSystem notesFs = NSFODPUtil.openZipPath(napiJar)) {
									Path notesRoot = notesFs.getPath("/"); //$NON-NLS-1$
									copyBundleEmbed(notesRoot, root);
								}
							} else {
								Files.copy(napiJar, root.resolve("lwpd.domino.napi.jar")); //$NON-NLS-1$
							}
						}
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
			

			// Create site.xml
			buildSiteXml(dest);

			// Generate p2 metadata based on the site.xml
			new GenerateP2MetadataTask(dest).run();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	private Path checkDirectory(Path dir) {
		if (!Files.exists(dir) || !Files.isDirectory(dir)) {
			throw new RuntimeException(Messages.getString("GenerateUpdateSiteTask.directoryNotExists") + dir.toAbsolutePath()); //$NON-NLS-1$
		}
		return dir;
	}

	private Path mkDir(Path dir) throws IOException {
		if (Files.isRegularFile(dir)) {
			throw new RuntimeException(Messages.getString("GenerateUpdateSiteTask.directoryExistsAsFile") + dir.toAbsolutePath()); //$NON-NLS-1$
		}
		Files.createDirectories(dir);
		return dir;
	}

	private void copyArtifacts(Path sourceDir, Path destDir, Document eclipseArtifacts) throws Exception {
		Files.list(sourceDir).forEach(artifact -> {
			System.out.println(Messages.getString("GenerateUpdateSiteTask.copying") + artifact.getFileName().toString()); //$NON-NLS-1$
			try {
				Path destJar = copyOrPack(artifact, destDir);
				
				if(eclipseArtifacts != null && destJar != null) {
					downloadSource(destJar, destDir, eclipseArtifacts);
				}

				if (Thread.currentThread().isInterrupted()) {
					return;
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	private Path copyOrPack(Path source, Path destDir) throws Exception {
		if (Files.isRegularFile(source) && source.getFileName().toString().toLowerCase().endsWith(".jar")) { //$NON-NLS-1$
			// Check for a MANIFEST.MF inside the Jar
			String classpath = null;
			try(JarFile jarFile = new JarFile(source.toFile())) {
				if(jarFile.getEntry("META-INF/MANIFEST.MF") == null) { //$NON-NLS-1$
					return null;
				}
				
				// Check for a Bundle-ClassPath for embeds
				Attributes attrs = jarFile.getManifest().getMainAttributes();
				classpath = attrs.getValue("Bundle-ClassPath"); //$NON-NLS-1$
			}

			Path dest = destDir.resolve(source.getFileName());
			if(this.flattenEmbeds && StringUtil.isNotEmpty(classpath)) {
				// Perform a complex copy if there are embeds to flatten
			} else {
				Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
			}
			return dest;
		} else if (Files.isDirectory(source)) {
			// Check for a MANIFEST.MF in a subdirectory
			Path manifestPath = source.resolve("META-INF").resolve("MANIFEST.MF"); //$NON-NLS-1$ //$NON-NLS-2$
			if(!Files.isRegularFile(source.resolve("META-INF").resolve("MANIFEST.MF"))) { //$NON-NLS-1$ //$NON-NLS-2$
				return null;
			}

			// Check for a Bundle-ClassPath for embeds
			String classpath = null;
			try(InputStream is = Files.newInputStream(manifestPath)) {
				Manifest manifest = new Manifest(is);
				Attributes attrs = manifest.getMainAttributes();
				classpath = attrs.getValue("Bundle-ClassPath"); //$NON-NLS-1$
			}
			

			// Must be an unpacked plugin
			Path destPlugin = destDir.resolve(source.getFileName() + ".jar"); //$NON-NLS-1$
			if(this.flattenEmbeds && StringUtil.isNotEmpty(classpath)) {
				Set<String> embeds = new HashSet<>();
				embeds.addAll(Arrays.asList(StringUtil.splitString(classpath, ',')));
				zipFolder(source.toAbsolutePath(), destPlugin.toAbsolutePath(), embeds);
			} else {
				zipFolder(source.toAbsolutePath(), destPlugin.toAbsolutePath(), Collections.emptySet());
			}
			return destPlugin;
		}
		return null;
	}

	private void zipFolder(Path sourceFolderPath, Path zipPath, Collection<String> embeds) throws Exception {
		try(FileSystem fs = NSFODPUtil.openZipPath(zipPath)) {
			Path root = fs.getPath("/"); //$NON-NLS-1$
			Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					String relativePath = sourceFolderPath.relativize(file).toString().replace(File.separatorChar, '/');
					if(EXCLUDED_FILENAMES.contains(file.getFileName().toString())) {
						// skip
						return FileVisitResult.CONTINUE;
					}
					
					if(GenerateUpdateSiteTask.this.flattenEmbeds && "MANIFEST.MF".equals(file.getFileName().toString())) { //$NON-NLS-1$
						// Do this specially to remove the Bundle-ClassPath header
						try(InputStream is = Files.newInputStream(file)) {
							Manifest manifest = new Manifest(is);
							Path target = root.resolve(relativePath);
							Files.createDirectories(target.getParent());
							try(OutputStream os = Files.newOutputStream(target, StandardOpenOption.CREATE)) {
								Manifest newManifest = new Manifest();
								Attributes newAttrs = newManifest.getMainAttributes();
								manifest.getMainAttributes()
									.entrySet()
									.stream()
									.filter(e -> !"Bundle-ClassPath".equalsIgnoreCase(e.getKey().toString())) //$NON-NLS-1$
									.forEach(e -> newAttrs.put(e.getKey(), e.getValue()));
								newManifest.write(os);
							}
						}
						return FileVisitResult.CONTINUE;
					}
					
					if(embeds.contains(relativePath)) {
						// Make a temp copy of the file in case it's inside a JAR
						Path tempEmbed = Files.createTempFile(file.getFileName().toString(), ".jar"); //$NON-NLS-1$
						Files.copy(file, tempEmbed, StandardCopyOption.REPLACE_EXISTING);
						try {
							try(FileSystem tempFs = NSFODPUtil.openZipPath(tempEmbed)) {
								copyBundleEmbed(tempFs.getPath("/"), root); //$NON-NLS-1$
							}
						} finally {
							Files.deleteIfExists(tempEmbed);
						}
					} else {
						Path target = root.resolve(relativePath);
						if(!Files.exists(target.getParent())) {
							Files.createDirectories(target.getParent());
						}
			 			Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}
	
	private String readNotesVersion(Path notesJar) throws IOException {
		try(JarFile jarFile = new JarFile(notesJar.toFile())) {
			ZipEntry versionProps = jarFile.getEntry("lotus/domino/Version.properties"); //$NON-NLS-1$
			try(InputStream is = jarFile.getInputStream(versionProps)) {
				Properties props = new Properties();
				props.load(is);
				String notesVersion = props.getProperty("NotesVersion", ""); //$NON-NLS-1$ //$NON-NLS-2$
				String notesVersionDate = props.getProperty("NotesVersionDate", ""); //$NON-NLS-1$ //$NON-NLS-2$
				
				return VersionUtil.generateNotesJarVersion(notesVersion, notesVersionDate);
			}
		}
	}

	private void buildSiteXml(Path baseDir) throws IOException {
		Path f = baseDir;

		if (!Files.isDirectory(f)) {
			throw new RuntimeException(Messages.getString("GenerateUpdateSiteTask.repoDirDoesNotExist")); //$NON-NLS-1$
		} else {
			Path features = f.resolve("features"); //$NON-NLS-1$
			if (!Files.isDirectory(features)) {
				throw new RuntimeException(Messages.getString("GenerateUpdateSiteTask.unableToFindFeatures") + features.toAbsolutePath()); //$NON-NLS-1$
			}

			try {
				Document doc = NSFODPDomUtil.createDocument();
				Element root = NSFODPDomUtil.createElement(doc, "site"); //$NON-NLS-1$

				// Create the category entry if applicable
				String category = "XPages Runtime"; //$NON-NLS-1$
				if (StringUtil.isNotEmpty(category)) {
					Element categoryDef = NSFODPDomUtil.createElement(root, "category-def"); //$NON-NLS-1$
					categoryDef.setAttribute("name", category); //$NON-NLS-1$
					categoryDef.setAttribute("label", category); //$NON-NLS-1$
				}

				Files.list(features)
					.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".jar")) //$NON-NLS-1$
					.forEach(feature -> {
					String featureFilename = feature.getFileName().toString();
					Matcher matcher = FEATURE_FILENAME_PATTERN.matcher(featureFilename);
					if (!matcher.matches()) {
						throw new IllegalStateException(Messages.getString("GenerateUpdateSiteTask.mismatchedFilename") + featureFilename); //$NON-NLS-1$
					}
					String featureName = matcher.group(1);
					String version = matcher.group(2);

					Element featureElement = NSFODPDomUtil.createElement(root, "feature"); //$NON-NLS-1$
					String url = "features/" + featureFilename; //$NON-NLS-1$
					featureElement.setAttribute("url", url); //$NON-NLS-1$
					featureElement.setAttribute("id", featureName); //$NON-NLS-1$
					featureElement.setAttribute("version", version); //$NON-NLS-1$

					if (StringUtil.isNotEmpty(category)) {
						Element categoryElement = NSFODPDomUtil.createElement(featureElement, "category"); //$NON-NLS-1$
						categoryElement.setAttribute("name", category); //$NON-NLS-1$
					}
				});

				String xml = NSFODPDomUtil.getXmlString(doc, null);
				Path output = f.resolve("site.xml"); //$NON-NLS-1$
				try (BufferedWriter w = Files.newBufferedWriter(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
					w.write(xml);
				} catch (IOException e) {
					throw new RuntimeException(Messages.getString("GenerateUpdateSiteTask.errorWritingSiteXml"), e); //$NON-NLS-1$
				}

				System.out.println(StringUtil.format(Messages.getString("GenerateUpdateSiteTask.wroteSiteXmlTo"), output.toAbsolutePath())); //$NON-NLS-1$
			} catch (Exception e) {
				throw new RuntimeException(Messages.getString("GenerateUpdateSiteTask.exceptionBuildingSiteXml"), e); //$NON-NLS-1$
			}
		}
	}
	
	/**
	 * @since 3.1.0
	 */
	private List<Path> findEclipsePaths(Path domino) {
		// Account for various layouts
		List<Path> eclipsePaths = Stream.of(
				// macOS Notes client < 12
				domino.resolve("Contents").resolve("MacOS").resolve("shared").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				domino.resolve("Contents").resolve("MacOS").resolve("rcp").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				// macOS Notes client 12
				domino.resolve("Contents").resolve("Eclipse").resolve("shared").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				domino.resolve("Contents").resolve("Eclipse").resolve("rcp").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				// macOS Notes client < 12 pointed at Contents/MacOS
				domino.resolve("shared").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$
				domino.resolve("rcp").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$
				// macOS Notes client 12 pointed at Contents/MacOS
				domino.getParent().resolve("Eclipse").resolve("shared").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				domino.getParent().resolve("Eclipse").resolve("rcp").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				// Domino and Windows Notes
				domino.resolve("osgi").resolve("shared").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				domino.resolve("osgi").resolve("rcp").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				// Windows Notes
				domino.resolve("framework").resolve("shared").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				domino.resolve("framework").resolve("rcp").resolve("eclipse") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			)
			.filter(path -> Files.exists(path))
			.collect(Collectors.toList());
		if(eclipsePaths.isEmpty()) {
			throw new IllegalArgumentException(Messages.getString("GenerateUpdateSiteTask.unableToLocatePlugins") + domino); //$NON-NLS-1$
		}
		return eclipsePaths;
	}

	/**
	 * @since 3.1.0
	 */
	private Optional<Path> findNotesJar(Path domino) {
		return findLibExtJar(domino, "Notes.jar"); //$NON-NLS-1$
	}
	
	/**
	 * @since 4.2.0
	 */
	private Optional<Path> findNapiJar(Path domino) {
		return findLibExtJar(domino, "lwpd.domino.napi.jar"); //$NON-NLS-1$
	}
	
	private Optional<Path> findLibExtJar(Path domino, String jarName) {
		return Stream.of(
				// macOS Notes client < 12
				domino.resolve("Contents").resolve("MacOS").resolve("jvm").resolve("lib").resolve("ext").resolve(jarName), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				// macOS Notes client 12
				domino.resolve("Contents").resolve("Resources").resolve("jvm").resolve("lib").resolve("ext").resolve(jarName), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				// All Notes and Domino, including < 12 macOS Notes client pointed at Contents/MacOS
				domino.resolve("jvm").resolve("lib").resolve("ext").resolve(jarName), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				// macOS Notes client 12 pointed at Contents/MacOS
				domino.getParent().resolve("Resources").resolve("jvm").resolve("lib").resolve("ext").resolve(jarName) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		)
		.filter(Files::exists)
		.filter(Files::isRegularFile)
		.findFirst();
	}
	
	/**
	 * 
	 * @return a {@link Path} to xsp.http.bootstrap.jar, which may not exist
	 * @since 3.2.0
	 */
	private Optional<Path> findXspBootstrap(Path domino) {
		// This only exists on servers and the Windows client for now, so no need to look through special Mac paths
		return findLibExtJar(domino, "xsp.http.bootstrap.jar"); //$NON-NLS-1$
	}

	/**
	 * @throws XMLException 
	 * @throws MalformedURLException 
	 * Retrieves the contents of the artifacts.jar file for the current matching Eclipse update
	 * site as a {@link Document}.
	 * 
	 * @since 3.3.0
	 */
	private Document fetchEclipseArtifacts() throws MalformedURLException {
		String urlString = PathUtil.concat(NEON_UPDATE_SITE, "artifacts.xml.xz", '/'); //$NON-NLS-1$
		URL artifactsUrl = new URL(urlString);
		try(InputStream is = artifactsUrl.openStream()) {
			try(XZInputStream zis = new XZInputStream(is)) {
				return NSFODPDomUtil.createDocument(zis);
			}
		} catch (IOException e) {
			System.err.println(Messages.getString("GenerateUpdateSiteTask.unableToLoadNeon")); //$NON-NLS-1$
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Looks for a source bundle matching the given artifact on the Neon update site.
	 * 
	 * @since 3.3.0
	 */
	private void downloadSource(Path artifact, Path destDir, Document artifacts) throws Exception {
		String fileName = artifact.getFileName().toString();
		Matcher matcher = BUNDLE_FILENAME_PATTERN.matcher(fileName);
		if(matcher.matches()) {
			String symbolicName = matcher.group(1) + ".source"; //$NON-NLS-1$
			String version = matcher.group(2);
			
			String query = StringUtil.format("/repository/artifacts/artifact[@classifier='osgi.bundle'][@id='{0}'][@version='{1}']", symbolicName, version); //$NON-NLS-1$
			NodeList result = NSFODPDomUtil.selectNodes(artifacts, query);
			if(result.getLength() > 0) {
				// Then we can be confident that it will exist at the expected URL
				String bundleName = StringUtil.format("{0}_{1}.jar", symbolicName, version); //$NON-NLS-1$
				Path dest = destDir.resolve(bundleName);
				
				String urlString = PathUtil.concat(NEON_UPDATE_SITE, "plugins", '/'); //$NON-NLS-1$
				urlString = PathUtil.concat(urlString, bundleName, '/');
				URL bundleUrl = new URL(urlString);
				try(InputStream is = bundleUrl.openStream()) {
					System.out.println(Messages.getString("GenerateUpdateSiteTask.downloadingSourceBundle") + artifact.getFileName().toString()); //$NON-NLS-1$
					Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
				} catch(IOException e) {
					System.err.println(Messages.getString("GenerateUpdateSiteTask.unableToDownloadSourceBundle") + urlString); //$NON-NLS-1$
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void copyBundleEmbed(Path source, Path dest) throws IOException {
		Files.walkFileTree(source, new CopyEmbedVisitor(dest));
	}
	
	private static class CopyEmbedVisitor extends SimpleFileVisitor<Path> {

		
		private final Path targetPath;
		private Path sourcePath = null;

		public CopyEmbedVisitor(Path targetPath) {
			this.targetPath = targetPath;
		}

		@Override
		public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
			if (sourcePath == null) {
				sourcePath = dir;
			} else {
				Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir).toString()));
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
			if(EXCLUDED_FILENAMES.contains(file.getFileName().toString())) {
				// skip
				return FileVisitResult.CONTINUE;
			} else if("MANIFEST.MF".equals(file.getFileName().toString())) { //$NON-NLS-1$
				// skip
				return FileVisitResult.CONTINUE;
			}
			
			Path target = targetPath.resolve(sourcePath.relativize(file).toString());
			if(Files.exists(target)) {
				// TODO consider merging META-INF/services files, though no duplicates
				//   exist in the distribution as of 12.0.2
				
				// skip
				return FileVisitResult.CONTINUE;
			}
			if(!Files.exists(target.getParent())) {
				Files.createDirectories(target.getParent());
			}
 			Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
			return FileVisitResult.CONTINUE;
		}
	}
}
