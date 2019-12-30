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
package org.openntf.p2.domino.updatesite.tasks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.ibm.commons.util.StringUtil;
import com.ibm.commons.xml.DOMUtil;
import com.ibm.commons.xml.XMLException;

public class GenerateUpdateSiteTask implements Runnable {
	private static final Pattern FEATURE_FILENAME_PATTERN = Pattern.compile("^(.+)_(\\d.+)\\.jar$"); //$NON-NLS-1$
	private static final Pattern NOTESJAR_BUILD_PATTERN = Pattern.compile("Build V(\\d\\d)(\\d)(\\d)_(\\d+)"); //$NON-NLS-1$
	private static final ThreadLocal<DateFormat> TIMESTAMP_FORMAT = ThreadLocal
			.withInitial(() -> new SimpleDateFormat("yyyyMMdd")); //$NON-NLS-1$

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
			Path dest = mkDir(destDir);
			Path destFeatures = mkDir(dest.resolve("features")); //$NON-NLS-1$
			Path destPlugins = mkDir(dest.resolve("plugins")); //$NON-NLS-1$
			
			for(Path eclipse : eclipsePaths) {
				Path features = eclipse.resolve("features");
				if(Files.isDirectory(features)) {
					copyArtifacts(features, destFeatures);
				}
				Path plugins = eclipse.resolve("plugins");
				if(Files.isDirectory(plugins)) {
					copyArtifacts(plugins, destPlugins);
				}
			}

			{
				String baseVersion = readNotesVersion(notesJar);
				String timestamp = TIMESTAMP_FORMAT.get().format(new Date());
				String version = baseVersion + "." + timestamp + "-1500"; //$NON-NLS-1$ //$NON-NLS-2$
				// Create the Notes API plugin for the true version, since the shipping plugin one is often out of step
				{
					String bundleId = "com.ibm.notes.java.api"; //$NON-NLS-1$
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
										.filter(name -> !"META-INF".equals(name))
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
					String bundleId = "com.ibm.notes.java.api.win32.linux"; //$NON-NLS-1$
					Path plugin = destPlugins.resolve(bundleId + "_" + version + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
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
							attrs.putValue("Bundle-SymbolicName", bundleId + ";singleton:=true"); //$NON-NLS-1$ //$NON-NLS-2$
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

			// Create site.xml
			buildSiteXml(dest);

			// Have Eclipse build p2 metadata
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

	private void copyArtifacts(Path sourceDir, Path destDir) throws Exception {
		Files.list(sourceDir).forEach(artifact -> {
			System.out.println("Copying " + artifact.getFileName().toString());
			try {
				copyOrPack(artifact, destDir);

				if (Thread.currentThread().isInterrupted()) {
					return;
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	private void copyOrPack(Path source, Path destDir) throws Exception {
		if (Files.isRegularFile(source) && source.getFileName().toString().toLowerCase().endsWith(".jar")) { //$NON-NLS-1$
			Path dest = destDir.resolve(source.getFileName());
			Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
		} else if (Files.isDirectory(source)) {
			// Must be an unpacked plugin
			Path destPlugin = destDir.resolve(source.getFileName() + ".jar"); //$NON-NLS-1$
			zipFolder(source.toAbsolutePath(), destPlugin.toAbsolutePath());
		}
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
			ZipEntry versionProps = jarFile.getEntry("lotus/domino/Version.properties");
			try(InputStream is = jarFile.getInputStream(versionProps)) {
				Properties props = new Properties();
				props.load(is);
				String notesVersion = props.getProperty("NotesVersion", "");
				if(notesVersion.startsWith("Release ")) {
					return notesVersion.substring("Release ".length());
				} else {
					// Beta builds have special formatting
					Matcher buildMatcher = NOTESJAR_BUILD_PATTERN.matcher(notesVersion);
					if(buildMatcher.matches()) {
						return buildMatcher.group(1) + '.' + buildMatcher.group(2) + '.' + buildMatcher.group(3) + '.' + buildMatcher.group(4);
					} else {
						return notesVersion;
					}
				}
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
				String category = "IBM XPages Runtime"; //$NON-NLS-1$
				if (StringUtil.isNotEmpty(category)) {
					Element categoryDef = DOMUtil.createElement(doc, root, "category-def"); //$NON-NLS-1$
					categoryDef.setAttribute("name", category); //$NON-NLS-1$
					categoryDef.setAttribute("label", category); //$NON-NLS-1$
				}

				Files.list(features)
					.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".jar"))
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
				domino.resolve("Contents").resolve("MacOS").resolve("shared").resolve("eclipse"),
				domino.resolve("Contents").resolve("MacOS").resolve("rcp").resolve("eclipse"),
				// macOS Notes client pointed at Contents/MacOS
				domino.resolve("shared").resolve("eclipse"),
				domino.resolve("rcp").resolve("eclipse"),
				// Domino and Windows Notes
				domino.resolve("osgi").resolve("shared").resolve("eclipse"),
				domino.resolve("osgi").resolve("rcp").resolve("eclipse"),
				// Windows Notes
				domino.resolve("framework").resolve("shared").resolve("eclipse"),
				domino.resolve("framework").resolve("rcp").resolve("eclipse")
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
			domino.resolve("Contents").resolve("MacOS").resolve("jvm").resolve("lib").resolve("ext").resolve("Notes.jar"),
			// All Notes and Domino, including macOS Notes client pointed at Contents/MacOS
			domino.resolve("jvm").resolve("lib").resolve("ext").resolve("Notes.jar")
		)
		.filter(Files::exists)
		.filter(Files::isRegularFile)
		.findFirst()
		.orElseThrow(() -> new IllegalArgumentException("Unable to locate Notes.jar within " + domino));
	}
}
