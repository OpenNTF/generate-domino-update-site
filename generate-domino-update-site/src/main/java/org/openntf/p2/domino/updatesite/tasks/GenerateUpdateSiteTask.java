package org.openntf.p2.domino.updatesite.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
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
import com.ibm.commons.util.io.StreamUtil;
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
		File domino = checkDirectory(new File(dominoDir));

		File source = checkDirectory(new File(domino, "osgi" + File.separator + "shared" + File.separator + "eclipse")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		File sourceFeatures = checkDirectory(new File(source, "features")); //$NON-NLS-1$
		File sourcePlugins = checkDirectory(new File(source, "plugins")); //$NON-NLS-1$
		File rcp = checkDirectory(new File(domino, "osgi" + File.separator + "rcp" + File.separator + "eclipse")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		File rcpFeatures = checkDirectory(new File(rcp, "features")); //$NON-NLS-1$
		File rcpPlugins = checkDirectory(new File(rcp, "plugins")); //$NON-NLS-1$
		
		File frameworkEclipse = new File(domino, "framework" + File.separator + "rcp" + File.separator + "eclipse"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		File frameworkFeatures = new File(frameworkEclipse, "features"); //$NON-NLS-1$
		File frameworkPlugins = new File(frameworkEclipse, "plugins"); //$NON-NLS-1$

		File frameworkShared = new File(domino, "framework" + File.separator + "shared" + File.separator + "eclipse"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		File frameworkSharedFeatures = new File(frameworkShared, "features"); //$NON-NLS-1$
		File frameworkSharedPlugins = new File(frameworkShared, "plugins"); //$NON-NLS-1$

		File notesJar = checkFile(new File(domino,
				"jvm" + File.separator + "lib" + File.separator + "ext" + File.separator + "Notes.jar")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		File dest = mkDir(new File(destDir));
		File destFeatures = mkDir(new File(dest, "features")); //$NON-NLS-1$
		File destPlugins = mkDir(new File(dest, "plugins")); //$NON-NLS-1$

		File eclipse = checkDirectory(new File(eclipseDir));
		File eclipsePlugins = checkDirectory(new File(eclipse, "plugins")); //$NON-NLS-1$
		File eclipseLauncher = eclipsePlugins
				.listFiles(file -> file.getName().startsWith("org.eclipse.equinox.launcher_"))[0]; //$NON-NLS-1$

		try {
			copyArtifacts(rcpFeatures, destFeatures);
			copyArtifacts(sourceFeatures, destFeatures);
			if(frameworkFeatures.exists() && frameworkFeatures.isDirectory()) {
				copyArtifacts(frameworkFeatures, destFeatures);
			}
			if(frameworkSharedFeatures.exists() && frameworkSharedFeatures.isDirectory()) {
				copyArtifacts(frameworkSharedFeatures, destFeatures);
			}
			copyArtifacts(rcpPlugins, destPlugins);
			copyArtifacts(sourcePlugins, destPlugins);
			if(frameworkPlugins.exists() && frameworkPlugins.isDirectory()) {
				copyArtifacts(frameworkPlugins, destPlugins);
			}
			if(frameworkSharedPlugins.exists() && frameworkSharedPlugins.isDirectory()) {
				copyArtifacts(frameworkSharedPlugins, destPlugins);
			}

			{

				String timestamp = TIMESTAMP_FORMAT.get().format(new Date());
				String version = "9.0.1." + timestamp + "-1500"; //$NON-NLS-1$ //$NON-NLS-2$
				// Create the Notes API 9.0.1 plugin, since stock still lists 8.5.3
				{
					String bundleId = "com.ibm.notes.java.api"; //$NON-NLS-1$
					File plugin = new File(destPlugins, bundleId + "_" + version + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
					try (FileOutputStream fos = new FileOutputStream(plugin)) {
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
							try (JarFile notesJarFile = new JarFile(notesJar)) {
								String exports = notesJarFile.stream()
										.map(jarEntry -> new File(jarEntry.getName()).getParent().replace('/', '.'))
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
					File plugin = new File(destPlugins, bundleId + "_" + version + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
					try (FileOutputStream fos = new FileOutputStream(plugin)) {
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
							try (FileInputStream notesJarIs = new FileInputStream(notesJar)) {
								StreamUtil.copyStream(notesJarIs, jos);
							}
							jos.closeEntry();
						}
					}
				}
			}

			// Create site.xml
			buildSiteXml(dest);

			// Have Eclipse build p2 metadata
			File java = new File(System.getProperty("java.home"), "bin" + File.separator + "java"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String destUri = Paths.get(dest.getAbsolutePath()).toUri().toString();
			new ProcessBuilder(java.getAbsolutePath(), "-jar", eclipseLauncher.getAbsolutePath(), "-application", //$NON-NLS-1$ //$NON-NLS-2$
					"org.eclipse.equinox.p2.publisher.EclipseGenerator", "-base", dest.getAbsolutePath(), "-source", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					dest.getAbsolutePath(), "-metadataRepository", destUri, "-artifactRepository", destUri, "-compress") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.inheritIO().start().waitFor();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	private File checkDirectory(File dir) {
		if (!dir.exists() || !dir.isDirectory()) {
			throw new RuntimeException("Directory does not exist: " + dir.getAbsolutePath());
		}
		return dir;
	}

	private File checkFile(File file) {
		if (!file.exists() || !file.isFile()) {
			throw new RuntimeException("File does not exist: " + file.getAbsolutePath());
		}
		return file;
	}

	private File mkDir(File dir) {
		if (dir.isFile()) {
			throw new RuntimeException("Planned directory exists as a file: " + dir.getAbsolutePath());
		}
		if (!dir.exists()) {
			dir.mkdirs();
		}
		return dir;
	}

	private void copyArtifacts(File sourceDir, File destDir) throws Exception {
		for (File artifact : sourceDir.listFiles()) {
			System.out.println("Copying " + artifact.getName());
			try {
				copyOrPack(artifact, destDir);

				if (Thread.currentThread().isInterrupted()) {
					return;
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void copyOrPack(File source, File destDir) throws Exception {
		if (source.isFile() && source.getName().endsWith(".jar")) { //$NON-NLS-1$
			try (FileInputStream fis = new FileInputStream(source)) {
				try (FileOutputStream fos = new FileOutputStream(new File(destDir, source.getName()))) {
					StreamUtil.copyStream(fis, fos);
				}
			}
		} else if (source.isDirectory()) {
			// Must be an unpacked plugin
			File destPlugin = new File(destDir, source.getName() + ".jar"); //$NON-NLS-1$
			zipFolder(Paths.get(source.getAbsolutePath()), Paths.get(destPlugin.getAbsolutePath()));
		}
	}

	private void zipFolder(Path sourceFolderPath, Path zipPath) throws Exception {
		ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()));
		java.nio.file.Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String relativePath = sourceFolderPath.relativize(file).toString();
				// Strip signature files, since they'll no longer quite match
				if (!(relativePath.endsWith(".RSA") || relativePath.endsWith(".SF"))) { //$NON-NLS-1$ //$NON-NLS-2$
					zos.putNextEntry(new ZipEntry(relativePath));
					java.nio.file.Files.copy(file, zos);
					zos.closeEntry();
				}
				return FileVisitResult.CONTINUE;
			}
		});
		zos.close();
	}

	private void buildSiteXml(File baseDir) {
		File f = baseDir;

		if (!f.exists() || !f.isDirectory()) {
			throw new RuntimeException("Repository directory does not exist.");
		} else {
			File features = new File(f, "features"); //$NON-NLS-1$
			if (!features.exists() || !features.isDirectory()) {
				throw new RuntimeException("Unable to find features directory: " + features.getAbsolutePath());
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

				String[] featureFiles = features.list(new FilenameFilter() {
					@Override
					public boolean accept(File parent, String fileName) {
						return StringUtil.toString(fileName).toLowerCase().endsWith(".jar"); //$NON-NLS-1$
					}
				});

				for (String featureFilename : featureFiles) {
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
				}

				String xml = DOMUtil.getXMLString(doc, false, true);
				File output = new File(f, "site.xml"); //$NON-NLS-1$
				try (FileWriter w = new FileWriter(output)) {
					w.write(xml);
				} catch (IOException e) {
					throw new RuntimeException("Error writing site.xml file", e);
				}

				System.out.println(StringUtil.format("Wrote site.xml contents to {0}", output.getAbsolutePath()));
			} catch (XMLException e) {
				throw new RuntimeException("Exception while building site.xml document", e);
			}
		}
	}
}
