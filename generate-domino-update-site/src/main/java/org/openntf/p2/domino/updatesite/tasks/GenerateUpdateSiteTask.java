package org.openntf.p2.domino.updatesite.tasks;

import java.io.BufferedWriter;
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
import java.util.Objects;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.ibm.commons.util.StringUtil;
import com.ibm.commons.xml.DOMUtil;
import com.ibm.commons.xml.XMLException;

public class GenerateUpdateSiteTask implements Runnable {
	private static final Pattern FEATURE_FILENAME_PATTERN = Pattern.compile("^(.+)_(\\d.+)\\.jar$"); //$NON-NLS-1$
	private static final ThreadLocal<DateFormat> TIMESTAMP_FORMAT = ThreadLocal
			.withInitial(() -> new SimpleDateFormat("yyyyMMdd")); //$NON-NLS-1$

	private final String dominoDir;
	private final String destDir;
	private final String eclipseDir;

	public GenerateUpdateSiteTask(String dominoDir, String destDir, String eclipseDir) {
		super();
		this.dominoDir = dominoDir;
		this.destDir = destDir;
		this.eclipseDir = eclipseDir;
	}

	@Override
	public void run() {
		Path domino = checkDirectory(Paths.get(dominoDir));

		Path source = checkDirectory(domino.resolve("osgi").resolve("shared").resolve("eclipse")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Path sourceFeatures = checkDirectory(source.resolve("features")); //$NON-NLS-1$
		Path sourcePlugins = checkDirectory(source.resolve("plugins")); //$NON-NLS-1$
		Path rcp = checkDirectory(domino.resolve("osgi").resolve("rcp").resolve("eclipse")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Path rcpFeatures = checkDirectory(rcp.resolve("features")); //$NON-NLS-1$
		Path rcpPlugins = checkDirectory(rcp.resolve("plugins")); //$NON-NLS-1$
		
		Path frameworkEclipse = domino.resolve("framework").resolve("rcp").resolve("eclipse"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Path frameworkFeatures = frameworkEclipse.resolve("features"); //$NON-NLS-1$
		Path frameworkPlugins = frameworkEclipse.resolve("plugins"); //$NON-NLS-1$

		Path frameworkShared = domino.resolve("framework").resolve("shared").resolve("eclipse"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Path frameworkSharedFeatures = frameworkShared.resolve("features"); //$NON-NLS-1$
		Path frameworkSharedPlugins = frameworkShared.resolve("plugins"); //$NON-NLS-1$

		Path notesJar = checkFile(domino.resolve("jvm").resolve("lib").resolve("ext").resolve("Notes.jar")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		try {
			Path dest = mkDir(Paths.get(destDir));
			Path destFeatures = mkDir(dest.resolve("features")); //$NON-NLS-1$
			Path destPlugins = mkDir(dest.resolve("plugins")); //$NON-NLS-1$

			Path eclipse = checkDirectory(Paths.get(eclipseDir));
			Path eclipsePlugins = checkDirectory(eclipse.resolve("plugins")); //$NON-NLS-1$
			Path eclipseLauncher = Files.list(eclipsePlugins)
					.filter(path -> path.getFileName().toString().startsWith("org.eclipse.equinox.launcher_")) //$NON-NLS-1$
					.findFirst().orElseThrow(() -> new IllegalArgumentException("Cannot find Equinox launcher in path " + eclipsePlugins));
			
			copyArtifacts(rcpFeatures, destFeatures);
			copyArtifacts(sourceFeatures, destFeatures);
			if(Files.isDirectory(frameworkFeatures)) {
				copyArtifacts(frameworkFeatures, destFeatures);
			}
			if(Files.isDirectory(frameworkSharedFeatures)) {
				copyArtifacts(frameworkSharedFeatures, destFeatures);
			}
			copyArtifacts(rcpPlugins, destPlugins);
			copyArtifacts(sourcePlugins, destPlugins);
			if(Files.isDirectory(frameworkPlugins)) {
				copyArtifacts(frameworkPlugins, destPlugins);
			}
			if(Files.isDirectory(frameworkSharedPlugins)) {
				copyArtifacts(frameworkSharedPlugins, destPlugins);
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
										.map(jarEntry -> Paths.get(jarEntry.getName()).getParent().toString().replace('/', '.'))
										.distinct().filter(name -> Objects.nonNull(name))
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
			buildP2Metadata(dest, eclipseLauncher);
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

	private Path checkFile(Path file) {
		if (!Files.exists(file) || !Files.isRegularFile(file)) {
			throw new RuntimeException("File does not exist: " + file.toAbsolutePath());
		}
		return file;
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
		if (Files.isRegularFile(source) && source.getFileName().toString().endsWith(".jar")) { //$NON-NLS-1$
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
						String relativePath = sourceFolderPath.relativize(file).toString();
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
					return notesVersion;
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
	
	private void buildP2Metadata(Path dest, Path eclipseLauncher) throws InterruptedException, IOException {
		Path java = Paths.get(System.getProperty("java.home"), "bin", "java"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String destUri = dest.toUri().toString();
		new ProcessBuilder(java.toAbsolutePath().toString(), "-jar", eclipseLauncher.toAbsolutePath().toString(), "-application", //$NON-NLS-1$ //$NON-NLS-2$
				"org.eclipse.equinox.p2.publisher.EclipseGenerator", "-base", dest.toAbsolutePath().toString(), "-source", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				dest.toAbsolutePath().toString(), "-metadataRepository", destUri, "-artifactRepository", destUri, "-compress") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						.inheritIO().start().waitFor();
	}
}
