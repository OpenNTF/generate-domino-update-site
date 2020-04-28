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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.openntf.p2.domino.updatesite.util.VersionUtil;
import org.tukaani.xz.XZInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.ibm.commons.util.PathUtil;
import com.ibm.commons.util.StringUtil;
import com.ibm.commons.xml.DOMUtil;
import com.ibm.commons.xml.XMLException;
import com.ibm.commons.xml.XResult;

public class GenerateUpdateSiteTask implements Runnable {
	private static final Pattern FEATURE_FILENAME_PATTERN = Pattern.compile("^(.+)_(\\d.+)\\.jar$"); //$NON-NLS-1$
	private static final Pattern BUNDLE_FILENAME_PATTERN = FEATURE_FILENAME_PATTERN;
	
	/**
	 * This is the public Eclipse update site that best matches what's found in 9.0.1FP10 to current (11.0.1).
	 */
	public static final String NEON_UPDATE_SITE = "https://download.eclipse.org/releases/neon/201612211000"; //$NON-NLS-1$

	private final Path dominoDir;
	private final Path destDir;

	public GenerateUpdateSiteTask(Path dominoDir, Path destDir) {
		super();
		this.dominoDir = dominoDir;
		this.destDir = destDir;
	}

	@Override
	public void run() {
		Path domino = checkDirectory(dominoDir);
		
		List<Path> eclipsePaths = findEclipsePaths(domino);
		Path notesJar = findNotesJar(domino);

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
										.map(path -> path.toString().replace('/', '.'))
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
					try (OutputStream fos = Files.newOutputStream(plugin, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
						try (JarOutputStream jos = new JarOutputStream(fos)) {
							jos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF")); //$NON-NLS-1$
							Manifest manifest = new Manifest();
							Attributes attrs = manifest.getMainAttributes();
							attrs.putValue("Manifest-Version", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-ClassPath", "Notes.jar"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-Vendor", "IBM"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Fragment-Host", "com.ibm.notes.java.api"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-Name", "Notes Java API Windows and Linux Fragment"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-SymbolicName", fragmentId + ";singleton:=true"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-Version", version); //$NON-NLS-1$
							attrs.putValue("Bundle-ManifestVersion", "2"); //$NON-NLS-1$ //$NON-NLS-2$
							manifest.write(jos);
							jos.closeEntry();

							jos.putNextEntry(new ZipEntry("Notes.jar")); //$NON-NLS-1$
							Files.copy(notesJar, jos);
							jos.closeEntry();
						}
					}
				}
			}
			
			// Create an XSP HTTP Bootstrap bundle, if possible
			{
				Path xspBootstrap = findXspBootstrap(domino);
				if(Files.isRegularFile(xspBootstrap)) {
					String bundleId = "com.ibm.xsp.http.bootstrap"; //$NON-NLS-1$
					Path plugin = destPlugins.resolve(bundleId + "_" + version + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
					try (OutputStream fos = Files.newOutputStream(plugin, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
						try (JarOutputStream jos = new JarOutputStream(fos)) {
							jos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF")); //$NON-NLS-1$
							Manifest manifest = new Manifest();
							Attributes attrs = manifest.getMainAttributes();
							attrs.putValue("Manifest-Version", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-ClassPath", "xsp.http.bootstrap.jar"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-Vendor", "IBM"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-Name", "XSP HTTP Bootstrap"); //$NON-NLS-1$ //$NON-NLS-2$
							attrs.putValue("Bundle-SymbolicName", bundleId); //$NON-NLS-1$
							attrs.putValue("Bundle-Version", version); //$NON-NLS-1$
							attrs.putValue("Bundle-ManifestVersion", "2"); //$NON-NLS-1$ //$NON-NLS-2$
							
							// Find the packages to export
							try (JarFile notesJarFile = new JarFile(xspBootstrap.toFile())) {
								String exports = notesJarFile.stream()
										.filter(jarEntry -> StringUtil.toString(jarEntry.getName()).endsWith(".class")) //$NON-NLS-1$
										.map(jarEntry -> Paths.get(jarEntry.getName()).getParent())
										.filter(Objects::nonNull)
										.map(path -> path.toString().replace('/', '.'))
										.distinct()
										.filter(Objects::nonNull)
										.filter(name -> !"META-INF".equals(name)) //$NON-NLS-1$
										.collect(Collectors.joining(",")); //$NON-NLS-1$
								attrs.putValue("Export-Package", exports); //$NON-NLS-1$
							}
							
							manifest.write(jos);
							jos.closeEntry();

							jos.putNextEntry(new ZipEntry("xsp.http.bootstrap.jar")); //$NON-NLS-1$
							Files.copy(xspBootstrap, jos);
							jos.closeEntry();
						}
					}
				} else {
					System.out.println("Unable to locate xsp.http.bootstrap.jar - skipping bundle creation");
				}
			}

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
			throw new RuntimeException("Directory does not exist: " + dir.toAbsolutePath());
		}
		return dir;
	}

	private Path mkDir(Path dir) throws IOException {
		if (Files.isRegularFile(dir)) {
			throw new RuntimeException("Planned directory exists as a file: " + dir.toAbsolutePath());
		}
		Files.createDirectories(dir);
		return dir;
	}

	private void copyArtifacts(Path sourceDir, Path destDir, Document eclipseArtifacts) throws Exception {
		Files.list(sourceDir).forEach(artifact -> {
			System.out.println("Copying " + artifact.getFileName().toString());
			try {
				Path destJar = copyOrPack(artifact, destDir);
				
				if(eclipseArtifacts != null) {
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
			try(JarFile jarFile = new JarFile(source.toFile())) {
				if(jarFile.getEntry("META-INF/MANIFEST.MF") == null) { //$NON-NLS-1$
					return null;
				}
			}
			
			Path dest = destDir.resolve(source.getFileName());
			Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
			return dest;
		} else if (Files.isDirectory(source)) {
			// Check for a MANIFEST.MF in a subdirectory
			if(!Files.isRegularFile(source.resolve("META-INF").resolve("MANIFEST.MF"))) { //$NON-NLS-1$ //$NON-NLS-2$
				return null;
			}
			
			// Must be an unpacked plugin
			Path destPlugin = destDir.resolve(source.getFileName() + ".jar"); //$NON-NLS-1$
			zipFolder(source.toAbsolutePath(), destPlugin.toAbsolutePath());
			return destPlugin;
		}
		return null;
	}

	private void zipFolder(Path sourceFolderPath, Path zipPath) throws Exception {
		try(OutputStream fos = Files.newOutputStream(zipPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			try(ZipOutputStream zos = new ZipOutputStream(fos)) {
				Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						String relativePath = sourceFolderPath.relativize(file).toString().replace(File.separatorChar, '/');
						// Strip signature files, since they'll no longer quite match
						if (!(relativePath.endsWith(".RSA") || relativePath.endsWith(".SF"))) { //$NON-NLS-1$ //$NON-NLS-2$
							zos.putNextEntry(new ZipEntry(relativePath));
							Files.copy(file, zos);
							zos.closeEntry();
						}
						return FileVisitResult.CONTINUE;
					}
				});
			}
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
			throw new RuntimeException("Repository directory does not exist.");
		} else {
			Path features = f.resolve("features"); //$NON-NLS-1$
			if (!Files.isDirectory(features)) {
				throw new RuntimeException("Unable to find features directory: " + features.toAbsolutePath());
			}

			try {
				Document doc = DOMUtil.createDocument();
				Element root = DOMUtil.createElement(doc, "site"); //$NON-NLS-1$

				// Create the category entry if applicable
				String category = "XPages Runtime"; //$NON-NLS-1$
				if (StringUtil.isNotEmpty(category)) {
					Element categoryDef = DOMUtil.createElement(doc, root, "category-def"); //$NON-NLS-1$
					categoryDef.setAttribute("name", category); //$NON-NLS-1$
					categoryDef.setAttribute("label", category); //$NON-NLS-1$
				}

				Files.list(features)
					.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".jar")) //$NON-NLS-1$
					.forEach(feature -> {
					String featureFilename = feature.getFileName().toString();
					Matcher matcher = FEATURE_FILENAME_PATTERN.matcher(featureFilename);
					if (!matcher.matches()) {
						throw new IllegalStateException("Could not match filename pattern to " + featureFilename);
					}
					String featureName = matcher.group(1);
					String version = matcher.group(2);

					Element featureElement = DOMUtil.createElement(doc, root, "feature"); //$NON-NLS-1$
					String url = "features/" + featureFilename; //$NON-NLS-1$
					featureElement.setAttribute("url", url); //$NON-NLS-1$
					featureElement.setAttribute("id", featureName); //$NON-NLS-1$
					featureElement.setAttribute("version", version); //$NON-NLS-1$

					if (StringUtil.isNotEmpty(category)) {
						Element categoryElement = DOMUtil.createElement(doc, featureElement, "category"); //$NON-NLS-1$
						categoryElement.setAttribute("name", category); //$NON-NLS-1$
					}
				});

				String xml = DOMUtil.getXMLString(doc, false, true);
				Path output = f.resolve("site.xml"); //$NON-NLS-1$
				try (BufferedWriter w = Files.newBufferedWriter(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
					w.write(xml);
				} catch (IOException e) {
					throw new RuntimeException("Error writing site.xml file", e);
				}

				System.out.println(StringUtil.format("Wrote site.xml contents to {0}", output.toAbsolutePath()));
			} catch (XMLException e) {
				throw new RuntimeException("Exception while building site.xml document", e);
			}
		}
	}
	
	/**
	 * @since 3.1.0
	 */
	private List<Path> findEclipsePaths(Path domino) {
		// Account for various layouts
		List<Path> eclipsePaths = Stream.of(
				// macOS Notes client
				domino.resolve("Contents").resolve("MacOS").resolve("shared").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				domino.resolve("Contents").resolve("MacOS").resolve("rcp").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				// macOS Notes client pointed at Contents/MacOS
				domino.resolve("shared").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$
				domino.resolve("rcp").resolve("eclipse"), //$NON-NLS-1$ //$NON-NLS-2$
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
			throw new IllegalArgumentException("Unable to locate plugin directories within " + domino);
		}
		return eclipsePaths;
	}

	/**
	 * @since 3.1.0
	 */
	private Path findNotesJar(Path domino) {
		return Stream.of(
			// macOS Notes client
			domino.resolve("Contents").resolve("MacOS").resolve("jvm").resolve("lib").resolve("ext").resolve("Notes.jar"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			// All Notes and Domino, including macOS Notes client pointed at Contents/MacOS
			domino.resolve("jvm").resolve("lib").resolve("ext").resolve("Notes.jar") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		)
		.filter(Files::exists)
		.filter(Files::isRegularFile)
		.findFirst()
		.orElseThrow(() -> new IllegalArgumentException("Unable to locate Notes.jar within " + domino));
	}
	
	/**
	 * 
	 * @return a {@link Path} to xsp.http.bootstrap.jar, which may not exist
	 * @since 3.2.0
	 */
	private Path findXspBootstrap(Path domino) {
		// This only exists on servers and the Windows client for now, so no need to look through special Mac paths
		return domino.resolve("jvm").resolve("lib").resolve("ext").resolve("xsp.http.bootstrap.jar"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	/**
	 * @throws XMLException 
	 * @throws MalformedURLException 
	 * Retrieves the contents of the artifacts.jar file for the current matching Eclipse update
	 * site as a {@link Document}.
	 * 
	 * @since 3.3.0
	 */
	private Document fetchEclipseArtifacts() throws MalformedURLException, XMLException {
		String urlString = PathUtil.concat(NEON_UPDATE_SITE, "artifacts.xml.xz", '/'); //$NON-NLS-1$
		URL artifactsUrl = new URL(urlString);
		try(InputStream is = artifactsUrl.openStream()) {
			try(XZInputStream zis = new XZInputStream(is)) {
				return DOMUtil.createDocument(zis);
			}
		} catch (IOException e) {
			System.err.println("Unable to load Neon artifacts.xml.xz");
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
			XResult result = DOMUtil.evaluateXPath(artifacts, query);
			Object[] artifactNodes = result.getNodes();
			if(artifactNodes != null && artifactNodes.length > 0) {
				// Then we can be confident that it will exist at the expected URL
				String bundleName = StringUtil.format("{0}_{1}.jar", symbolicName, version); //$NON-NLS-1$
				Path dest = destDir.resolve(bundleName);
				
				String urlString = PathUtil.concat(NEON_UPDATE_SITE, "plugins", '/'); //$NON-NLS-1$
				urlString = PathUtil.concat(urlString, bundleName, '/');
				URL bundleUrl = new URL(urlString);
				try(InputStream is = bundleUrl.openStream()) {
					System.out.println("- Downloading source bundle " + artifact.getFileName().toString());
					Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
				} catch(IOException e) {
					System.err.println("Unable to download source bundle " + urlString);
					e.printStackTrace();
				}
			}
		}
	}
}
