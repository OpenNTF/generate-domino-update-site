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

import static org.openntf.p2.domino.updatesite.util.ClassWriterUtil.printClassSignature;
import static org.openntf.p2.domino.updatesite.util.ClassWriterUtil.printConstructors;
import static org.openntf.p2.domino.updatesite.util.ClassWriterUtil.printMethod;
import static org.openntf.p2.domino.updatesite.util.ClassWriterUtil.writeBasicConstructorBody;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.component.repository.ComponentDependency;
import org.openntf.nsfodp.commons.NSFODPUtil;
import org.openntf.nsfodp.commons.xml.NSFODPDomUtil;
import org.openntf.p2.domino.updatesite.model.BundleInfo;
import org.openntf.p2.domino.updatesite.util.ClassWriterConfig;
import org.openntf.p2.domino.updatesite.util.ClassWriterUtil;
import org.openntf.p2.domino.updatesite.util.EclipseUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.ibm.commons.util.StringUtil;

@Mojo(name = "generateSourceProjects", requiresProject = false)
public class GenerateSourceStubProjectsMojo extends AbstractMavenizeBundlesMojo {
	private static final String[] COPY_HEADERS = {
			"Manifest-Version", //$NON-NLS-1$
			"Bundle-SymbolicName", //$NON-NLS-1$
			"Bundle-Name", //$NON-NLS-1$
			"Bundle-Version", //$NON-NLS-1$
			"Bundle-ManifestVersion", //$NON-NLS-1$
			"Bundle-Vendor", //$NON-NLS-1$
			"Import-Package", //$NON-NLS-1$
			"Require-Bundle", //$NON-NLS-1$
			"DynamicImport-Package", //$NON-NLS-1$
			"Export-Package", //$NON-NLS-1$
			"Bundle-RequiredExecutionEnvironment", //$NON-NLS-1$
			"Fragment-Host", //$NON-NLS-1$
			"Eclipse-ExtensibleAPI", //$NON-NLS-1$
			"Eclipse-SourceReferences" //$NON-NLS-1$
	};
	
	private static final String[] DEFAULT_CLASSPATH = {

		// Provide CORBA and JEE dependencies for Java > 9
		// This is declared in the project dependencies, so will have been downloaded
		"glassfish-corba-omgapi", //$NON-NLS-1$
		"jakarta.jms-api", //$NON-NLS-1$
		"jakarta.transaction-api", //$NON-NLS-1$
		"xmlpull", //$NON-NLS-1$

		// RuntimeDelegate tries to initialize and needs to see an implementation
		"jersey-bundle", //$NON-NLS-1$
		
		// Observed needing a missing interface
		"lucene-grouping", //$NON-NLS-1$
		"lucene-spatial-extras", //$NON-NLS-1$
		
		// Some components reference Log4J in their API
		"log4j-core" //$NON-NLS-1$
	};

	/**
	 * Destination directory
	 */
	@org.apache.maven.plugins.annotations.Parameter(property = "dest", required = true)
	private File dest;

	@Parameter(defaultValue = "${plugin}", readonly = true)
	protected PluginDescriptor pluginDescriptor;

	private URLClassLoader cl;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try (URLClassLoader cl = (this.cl = buildWorkspaceClassLoader())) {
			super.execute();
		} catch (IOException e) {
			// Ignore - from closing the URLClassLoader
		}

		try {
			createTychoStructure(this.dest.toPath());
		} catch (IOException e) {
			throw new MojoExecutionException("Encountered exception building Tycho structure", e);
		}
	}

	@Override
	protected void processBundle(BundleInfo bundle, List<BundleInfo> bundles, Map<String, BundleInfo> bundlesByName,
			Path tempPom) throws MojoExecutionException {

		getLog().info(MessageFormat.format("Processing bundle {0}", bundle.getArtifactId()));

		// Special-case ICU base, as it's impossibly old, troublesome, and a subset of
		// real ICU, which is also included
		if ("com.ibm.icu.base".equals(bundle.getArtifactId())) { //$NON-NLS-1$
			if (getLog().isDebugEnabled()) {
				getLog().debug("- Skipping com.ibm.icu.base");
			}
			return;
		} else if (bundle.getArtifactId().endsWith(".source")) { //$NON-NLS-1$
			// TODO prefer using this local bundle when available
			if (getLog().isDebugEnabled()) {
				getLog().debug(MessageFormat.format("- Skipping existing source bundle {0}", bundle.getArtifactId()));
			}
			return;
		} else if(!shouldCopyBundle(bundle.getArtifactId())) {
			if(getLog().isInfoEnabled()) {
				getLog().info("- Skipping non-proprietary or non-useful bundle");
			}
			return;
		}

		Path plugins = dest.toPath().resolve("bundles"); //$NON-NLS-1$
		try {
			Files.createDirectories(plugins);
		} catch (IOException e) {
			throw new MojoExecutionException(MessageFormat.format("Unable to create bundles directory: {0}", plugins),
					e);
		}
		Path bundleBase = plugins.resolve(bundle.getArtifactId());
		try {
			Files.createDirectories(bundleBase);
		} catch (IOException e) {
			throw new MojoExecutionException(MessageFormat.format("Unable to create bundle directory: {0}", plugins),
					e);
		}

		Path src = bundleBase.resolve("src"); //$NON-NLS-1$

		try {
			Files.createDirectories(src);

			String eclipseUpdateSite = EclipseUtil.chooseEclipseUpdateSite(Collections.singleton(this.src.toPath()));
			Document eclipseArtifacts = EclipseUtil.fetchEclipseArtifacts(getLog(), eclipseUpdateSite);

			Path origJarPath = Paths.get(bundle.getFilePath());

			Path sourceTemp = Files.createTempDirectory("bundlesource"); //$NON-NLS-1$
			try {
				// See if we can find the source for it
				String bundleName = bundle.getArtifactId() + '_' + bundle.getVersion() + ".jar"; //$NON-NLS-1$
				Path sourceJar = EclipseUtil.downloadSource(getLog(), Paths.get(bundleName), sourceTemp,
						eclipseArtifacts, eclipseUpdateSite);
				if (!ClassWriterConfig.SKIP_SOURCE_BUNDLES.contains(bundle.getArtifactId()) && sourceJar != null) {
					// If so, build a new source project for it
					copySourceBundle(src, sourceJar, origJarPath);
					createBuildStubs(bundleBase);
				} else {
					// Otherwise, generate stub content
					try (JarFile origBundle = new JarFile(origJarPath.toFile(), false)) {
						copyManifest(bundleBase, origBundle);
						createSourceStubs(src, origBundle);
						createBuildStubs(bundleBase);
					} catch (IOException e) {
						throw new MojoExecutionException(
								MessageFormat.format("Encountered exception working with JAR {0}", origJarPath), e);
					}
				}
			} finally {
				NSFODPUtil.deltree(sourceTemp);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Encountered generation exception", e);
		}
	}

	/**
	 * Copies a subset of the original bundle's MANIFEST entries to a new
	 * MANIFEST.MF file.
	 * 
	 * @param bundleBase the base path of the new source bundle
	 * @param origBundle the original bundle as a {@link JarFile}
	 * @throws MojoExecutionException if there is a problem reading or writing the
	 *                                manifest
	 * @see {@link #COPY_HEADERS}
	 */
	private void copyManifest(Path bundleBase, JarFile origBundle) throws MojoExecutionException {
		try {
			Manifest origManifest = origBundle.getManifest();
			Attributes origAttributes = origManifest.getMainAttributes();

			Manifest targetManifest = new Manifest();
			Attributes targetAttributes = targetManifest.getMainAttributes();
			for (String headerName : COPY_HEADERS) {
				String val = origAttributes.getValue(headerName);
				if (StringUtil.isNotEmpty(val)) {
					targetAttributes.putValue(headerName, val);
				}
			}

			// Special handling for some bundles that use classes from
			// org.eclipse.equinox.registry
			// but don't depend on it
			String symbolicName = targetAttributes.getValue("Bundle-SymbolicName"); //$NON-NLS-1$
			int semiIndex = symbolicName.indexOf(';');
			if (semiIndex > -1) {
				symbolicName = symbolicName.substring(0, semiIndex);
			}

			// Remove any packages we were told to ignore
			String exportedPackages = targetAttributes.getValue("Export-Package"); //$NON-NLS-1$
			if (StringUtil.isNotEmpty(exportedPackages)) {
				String[] packages = StringUtil.splitString(exportedPackages, ',');
				exportedPackages = Arrays.stream(packages)
						.filter(p -> !ClassWriterConfig.SKIP_PACKAGES.contains(p))
						.collect(Collectors.joining(",")); //$NON-NLS-1$
				targetAttributes.putValue("Export-Package", exportedPackages); //$NON-NLS-1$
			}
			
			// Post-process Require-Bundle
			boolean[] needsFragments = { false };
			String requireBundle = targetAttributes.getValue("Require-Bundle"); //$NON-NLS-1$
			if(StringUtil.isNotEmpty(requireBundle)) {
				String[] bundles = StringUtil.splitString(requireBundle, ',');
				requireBundle = Arrays.stream(bundles)
					.filter(b -> {
						// Some dependencies are not actualy present, so remove them
						String bundleName = b;
						int bSemiIndex = bundleName.indexOf(';');
						if(bSemiIndex > -1) {
							bundleName = bundleName.substring(0, bSemiIndex);
						}
						
						// While here, see if we're going to need to add the lotus.domino
						// or NAPI fragments
						
						if("com.ibm.notes.java.api".equals(bundleName) || "com.ibm.domino.napi".equals(bundleName)) { //$NON-NLS-1$ //$NON-NLS-2$
							needsFragments[0] = true;
						}
						
						return !ClassWriterConfig.VETOED_REQUIREMENTS.contains(bundleName);
					})
					.map(b -> {
						// Handle cases of internal bundle names being different from
						// generated ones
						String translated = ClassWriterConfig.TRANSLATE_BUNDLE_NAMES.get(b);
						return translated == null ? b : translated;
					})
					.collect(Collectors.joining(",")); //$NON-NLS-1$
				targetAttributes.putValue("Require-Bundle", requireBundle); //$NON-NLS-1$
			}

			// Set the Bundle-Name to the symbolic name for ease of programmatic use
			targetAttributes.putValue("Bundle-Name", symbolicName); //$NON-NLS-1$

			Path metaInf = bundleBase.resolve("META-INF"); //$NON-NLS-1$
			Files.createDirectories(metaInf);
			Path manifest = metaInf.resolve("MANIFEST.MF"); //$NON-NLS-1$
			try (OutputStream os = Files.newOutputStream(manifest, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING)) {
				targetManifest.write(os);
			}
			
			// If the bundle needs a special pom.xml, write that now
			if(needsFragments[0]) {
				// Veto that logic for the bundles themselves
				if(!(symbolicName.startsWith("com.ibm.domino.napi") || symbolicName.startsWith("com.ibm.notes.java.api"))) {
					Document pomXmlDoc;
					try(InputStream is = getClass().getResourceAsStream("/fragmentDepPom.xml")) { //$NON-NLS-1$
						pomXmlDoc = NSFODPDomUtil.createDocument(is);
					}
					Element artifactId = (Element)NSFODPDomUtil.node(pomXmlDoc, "/*[name()='project']/*[name()='artifactId']").orElse(null); //$NON-NLS-1$
					artifactId.setTextContent(symbolicName);
					
					Path pomXml = bundleBase.resolve("pom.xml"); //$NON-NLS-1$
					try(BufferedWriter w = Files.newBufferedWriter(pomXml, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
						NSFODPDomUtil.serialize(w, pomXmlDoc, null);
					}
				}
			}
		} catch (IOException e) {
			throw new MojoExecutionException(
					MessageFormat.format("Encountered exception copying manifest to {0}", bundleBase), e);
		}
	}

	private void copySourceBundle(Path src, Path sourceJar, Path origJarPath) throws IOException {
		try (FileSystem zipFs = NSFODPUtil.openZipPath(sourceJar)) {
			Path rootSource = zipFs.getPath("/"); //$NON-NLS-1$

			// Copy META-INF and OSGI-INF wholesale to the parent of the src destination
			Path metaInfSource = rootSource.resolve("META-INF"); //$NON-NLS-1$
			if(Files.isDirectory(metaInfSource)) {
				NSFODPUtil.copyDirectory(metaInfSource, src.getParent().resolve("META-INF")); //$NON-NLS-1$
			}
			Path osgiInfSource = rootSource.resolve("OSGI-INF"); //$NON-NLS-1$
			if(Files.isDirectory(osgiInfSource)) {
				NSFODPUtil.copyDirectory(osgiInfSource, src.getParent().resolve("OSGI-INF")); //$NON-NLS-1$
			}

			// Copy everything, resources included, into the src dir
			Files.list(rootSource)
					.filter(p -> !"META-INF".equals(p.getFileName().toString()) //$NON-NLS-1$
							&& !"OSGI-INF".equals(p.getFileName().toString())) //$NON-NLS-1$
					.forEach(p -> {
						try {
							if (Files.isDirectory(p)) {
								NSFODPUtil.copyDirectory(p, src.resolve(p.getFileName().toString()));
							} else {
								Files.copy(p, src.resolve(p.getFileName().toString()),
										StandardCopyOption.REPLACE_EXISTING);
							}
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					});

			// Post-process MANIFEST.MF to remove source info
			Path manifestPath = src.getParent().resolve("META-INF").resolve("MANIFEST.MF"); //$NON-NLS-1$ //$NON-NLS-2$
			Manifest manifest;
			try (InputStream is = Files.newInputStream(manifestPath)) {
				manifest = new Manifest(is);
			}
			Attributes attrs = manifest.getMainAttributes();
			String symbolicName = attrs.getValue("Bundle-SymbolicName"); //$NON-NLS-1$
			if (symbolicName.endsWith(".source")) { //$NON-NLS-1$
				symbolicName = symbolicName.substring(0, symbolicName.length() - ".source".length()); //$NON-NLS-1$
				attrs.putValue("Bundle-SymbolicName", symbolicName); //$NON-NLS-1$
			}
			attrs.remove(new Attributes.Name("Eclipse-SourceBundle")); //$NON-NLS-1$

			// Add entries for Export-Package et al, since source bundles don't have those
			try (JarFile origJar = new JarFile(origJarPath.toFile())) {
				Manifest origJarManifest = origJar.getManifest();
				Attributes origAttrs = origJarManifest.getMainAttributes();
				for (String header : COPY_HEADERS) {
					if (origAttrs.containsKey(new Attributes.Name(header))) {
						attrs.putValue(header, origAttrs.getValue(header));
					}
				}
			}

			// Look for known-bad classes
			for (String className : ClassWriterConfig.SKIP_SOURCE_CLASSES) {
				String classPath = className.replace(".", src.getFileSystem().getSeparator()); //$NON-NLS-1$
				Path classFile = src.resolve(classPath + ".java"); //$NON-NLS-1$
				Files.deleteIfExists(classFile);
			}

			try (OutputStream os = Files.newOutputStream(manifestPath, StandardOpenOption.TRUNCATE_EXISTING)) {
				manifest.write(os);
			}
		}
	}

	private void createSourceStubs(Path src, JarFile origBundle) throws MojoExecutionException, IOException {
		Collection<String> exportedPackages = findExportedPackages(origBundle);

		// Pre-build a map of class data so that inner classes can be written
		// after outer classes
		// Map to outer class name to a list of 1 or more classes
		Map<String, SortedSet<Class<?>>> classes = new HashMap<>();

		for (ZipEntry entry : Collections.list(origBundle.entries())) {
			String entryName = entry.getName();

			// Extract applicable class files
			if (entryName.endsWith(".class") && !entryName.endsWith("/package-info.class")) { //$NON-NLS-1$ //$NON-NLS-2$
				processClassEntry(origBundle, entry, exportedPackages, classes);
			}
		}

		// Now do the same for Bundle-ClassPath entries
		Manifest manifest = origBundle.getManifest();
		String classpath = manifest.getMainAttributes().getValue("Bundle-ClassPath"); //$NON-NLS-1$
		if (StringUtil.isNotEmpty(classpath)) {
			String[] cpEntries = StringUtil.splitString(classpath, ',');
			for (String cpEntry : cpEntries) {
				String cpEntryName = cpEntry.trim();
				if (!cpEntryName.isEmpty()) {
					ZipEntry entry = origBundle.getJarEntry(cpEntryName);
					if (entry != null && !entry.isDirectory()) {
						// Make a temporary copy and read from there
						Path tempFile = Files.createTempFile("embed", ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
						try {
							try (InputStream eis = origBundle.getInputStream(entry)) {
								Files.copy(eis, tempFile, StandardCopyOption.REPLACE_EXISTING);
							}
							try (ZipFile embedBundle = new ZipFile(tempFile.toFile())) {
								for (ZipEntry embedEntry : Collections.list(embedBundle.entries())) {
									String entryName = embedEntry.getName();

									// Extract applicable class files
									if (entryName.endsWith(".class") && !entryName.endsWith("/package-info.class")) { //$NON-NLS-1$ //$NON-NLS-2$
										processClassEntry(embedBundle, embedEntry, exportedPackages, classes);
									}
								}
							}
						} finally {
							Files.deleteIfExists(tempFile);
						}
					}
				}
			}
		}

		for (Map.Entry<String, SortedSet<Class<?>>> entry : classes.entrySet()) {
			String className = entry.getKey();
			SortedSet<Class<?>> pool = entry.getValue();
			String packageName = className.substring(0, className.lastIndexOf('.'));
			String baseName = className.substring(packageName.length() + 1);
			createStubClass(src, packageName, baseName, pool);
		}
	}

	private void processClassEntry(ZipFile origBundle, ZipEntry entry, Collection<String> exportedPackages,
			Map<String, SortedSet<Class<?>>> classes) throws MojoExecutionException {
		String entryName = entry.getName();
		int slashIndex = entryName.lastIndexOf('/');
		if (slashIndex > -1) {
			String packageName = entryName.substring(0, slashIndex).replace('/', '.');
			if (true || exportedPackages.contains(packageName)) {
				String className = entryName.substring(slashIndex + 1, entryName.length() - ".class".length()); //$NON-NLS-1$
				className = packageName + "." + className; //$NON-NLS-1$
				if (shouldEmitClassName(className)) {
					try {
						Class<?> clazz = Class.forName(className, false, this.cl);

						if (shouldEmitClass(clazz)) {
							String ownerClass = clazz.getName();
							int dollarIndex = ownerClass.indexOf('$');
							if (dollarIndex > -1) {
								ownerClass = ownerClass.substring(0, dollarIndex);
							}

							SortedSet<Class<?>> pool = classes.computeIfAbsent(ownerClass,
									key -> new TreeSet<>(Comparator.comparing(Class::getName)));
							pool.add(clazz);
						}
					} catch (NoClassDefFoundError | ClassNotFoundException e) {
						getLog().warn(MessageFormat.format("Encountered exception processing class {0}", className), e);
					} catch (IncompatibleClassChangeError e) {
						// Shows up for some ICU classes that inherit final classes
						getLog().warn(MessageFormat.format("Unable to load class {0}: {1}", className, e.toString()));
					}
				}
			}
		}
	}

	private Collection<String> findExportedPackages(JarFile jarFile) throws MojoExecutionException {
		try {
			Manifest origManifest = jarFile.getManifest();
			Attributes origAttributes = origManifest.getMainAttributes();
			String exportPackage = origAttributes.getValue("Export-Package"); //$NON-NLS-1$
			if (StringUtil.isEmpty(exportPackage)) {
				return Collections.emptyList();
			} else {
				String[] packages = StringUtil.splitString(exportPackage, ',');
				return Arrays.stream(packages).map(p -> {
					int semiIndex = p.indexOf(';');
					if (semiIndex > -1) {
						return p.substring(0, semiIndex);
					} else {
						return p;
					}
				}).collect(Collectors.toSet());
			}
		} catch (IOException e) {
			throw new MojoExecutionException(
					MessageFormat.format("Encountered exception reading exported packages from {0}", jarFile.getName()),
					e);
		}
	}

	private void createStubClass(Path src, String packageName, String className, SortedSet<Class<?>> pool)
			throws MojoExecutionException {
		try {
			Class<?> clazz = pool.first();
			if (!Modifier.isPrivate(clazz.getModifiers())) {
				Path outputPath = src.resolve(packageName.replace(".", src.getFileSystem().getSeparator())) //$NON-NLS-1$
						.resolve(className + ".java"); //$NON-NLS-1$
				Files.createDirectories(outputPath.getParent());
				
				// call getDeclaredFields to get any VerifyError out of the way
				clazz.getDeclaredFields();

				try (BufferedWriter w = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8,
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw)) {
					writeClass(pw, pool, className);

					pw.flush();
					w.append(sw.toString());
				}
			}
		} catch (NoClassDefFoundError e) {
			// Shows up for some client-only classes in Domino update sites
			getLog().warn(MessageFormat.format("Unable to write class {0}: {1}", className, e.toString()));
		} catch(IncompatibleClassChangeError | VerifyError e) {
			// Shows up for some ICU classes with broken lineages
			getLog().warn(MessageFormat.format("Unable to write class {0}: {1}", className, e.toString()));
		} catch (IOException e) {
			throw new MojoExecutionException(MessageFormat
					.format("Encountered exception processing class {0}.{1} to {2}", packageName, className, src), e);
		}
	}

	private void writeClass(PrintWriter pw, SortedSet<Class<?>> pool, String className) {
		Class<?> clazz = pool.first();
		SortedSet<Class<?>> innerClasses = pool.stream().skip(1)
				.collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Class::getName))));

		// TODO annotations? They're likely not required for compilation

		// Skip emitting the package name if it's an inner class
		if (clazz.getName().indexOf('$') == -1) {
			pw.println("package " + clazz.getPackage().getName() + ";"); //$NON-NLS-1$ //$NON-NLS-2$
			pw.println();
		}

		// Class opener
		pw.print(printClassSignature(clazz, className));
		pw.println(" {"); //$NON-NLS-1$

		// Visible properties

		// Run through enum properties first
		if (clazz.isEnum()) {
			for (Field f : clazz.getDeclaredFields()) {
				if (f.isEnumConstant()) {
					pw.print(f.getName());
					// TODO implement abstract methods of the class
					pw.print(',');
				}
			}
			pw.println(';');
		}

		// Now normal properties
		pw.print(ClassWriterUtil.printClassFields(clazz, getLog()));

		// Constructors
		if (!clazz.isEnum()) {
			pw.print(printConstructors(clazz, cl, className, getLog()));
		}

		// Methods
		for (Method m : clazz.getDeclaredMethods()) {
			if (shouldEmitMethod(clazz, m)) {
				pw.print(printMethod(clazz, m));
			}
		}

		// There may have not been a constructor, but one may be required by the
		// superclass
		if (needsConstructor(clazz)) {
			pw.println("\tpublic "); //$NON-NLS-1$
			pw.print(className);
			pw.print("()"); //$NON-NLS-1$
			writeBasicConstructorBody(clazz, null, this.cl, getLog());
		}

		// Write out any inner classes
		if (!ClassWriterConfig.SKIP_INNER_CLASSES.contains(clazz.getName())) {
			for (Class<?> innerClass : innerClasses) {
				if (!shouldEmitClass(innerClass)) {
					continue;
				}
				// Process only direct inner classes, i.e. those where the last $ is just past
				// the end of this class
				int dollarIndex = innerClass.getName().lastIndexOf('$');
				if (dollarIndex == clazz.getName().length()) {
					// While here, collect any deeper inner classes
					SortedSet<Class<?>> deeperClasses = pool.stream().skip(1)
							.filter(c -> c.getName().startsWith(innerClass.getName()))
							.filter(c -> {
								// Try getDeclaredFields to get any VerifyErrors out of the way
								try {
									c.getDeclaredFields();
								} catch(VerifyError e) {
									getLog().warn(MessageFormat.format("Unable to write class {0}: {1}", className, e.toString()));
									return false;
								}
								return true;
							})
							.collect(
									Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Class::getName))));

					if(!deeperClasses.isEmpty()) {
						String innerClassName = innerClass.getName().substring(dollarIndex + 1);
						if (Character.isDigit(innerClassName.charAt(0))) {
							int dotIndex = innerClass.getName().lastIndexOf('.');
							innerClassName = innerClass.getName().substring(dotIndex + 1);
						}
						writeClass(pw, deeperClasses, innerClassName);
					}
				}
			}
		}

		// Add any explicit text to add
		String extraSource = ClassWriterConfig.RAW_CLASS_BODY_ADDITIONS.get(clazz.getName());
		if (StringUtil.isNotEmpty(extraSource)) {
			pw.println(extraSource);
		}

		// End class
		pw.println("}"); //$NON-NLS-1$
	}

	private void createBuildStubs(Path bundleBase) throws IOException {
		Path buildProperties = bundleBase.resolve("build.properties"); //$NON-NLS-1$
		Properties props = new Properties();
		props.put("source..", "src"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("output..", "target/classes"); //$NON-NLS-1$ //$NON-NLS-2$

		if (Files.isDirectory(bundleBase.resolve("OSGI-INF"))) { //$NON-NLS-1$
			props.put("bin.includes", "META-INF/,OSGI-INF/,."); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			props.put("bin.includes", "META-INF/,."); //$NON-NLS-1$ //$NON-NLS-2$
		}

		props.put("tycho.pomless.parent", "../../pom.xml"); //$NON-NLS-1$ //$NON-NLS-2$
		// Used by several Equinox bundles implicitly
		props.put("jars.extra.classpath", //$NON-NLS-1$
				"platform:/plugin/org.osgi.annotation.versioning,platform:/plugin/org.openntf.domino.java.api.corba.patch"); //$NON-NLS-1$

		String bundleName = bundleBase.getFileName().toString();
		Collection<String> extraDeps = ClassWriterConfig.ADD_ADDITIONAL_BUNDLES.get(bundleName);
		if (extraDeps != null) {
			props.put("additional.bundles", String.join(",", extraDeps)); //$NON-NLS-1$ //$NON-NLS-2$
		}

		try (OutputStream os = Files.newOutputStream(buildProperties, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING)) {
			props.store(os, null);
		}
	}

	private void createTychoStructure(Path projectBase) throws IOException {
		// Register the Tycho pomless extension
		Path mvn = projectBase.resolve(".mvn"); //$NON-NLS-1$
		Files.createDirectories(mvn);
		Path extensions = mvn.resolve("extensions.xml"); //$NON-NLS-1$
		try (InputStream is = getClass().getResourceAsStream("/extensions.xml")) { //$NON-NLS-1$
			Files.copy(is, extensions, StandardCopyOption.REPLACE_EXISTING);
		}

		// Create our base POM
		Path pomXml = projectBase.resolve("pom.xml"); //$NON-NLS-1$
		try (InputStream is = getClass().getResourceAsStream("/tycho.pom.xml")) { //$NON-NLS-1$
			// TODO change the source/target Java version based on the source update site
			Files.copy(is, pomXml, StandardCopyOption.REPLACE_EXISTING);
		}

		// Add our upstream dependency target
		Path depsTarget = projectBase.resolve("deps.target"); //$NON-NLS-1$
		try(InputStream is = getClass().getResourceAsStream("/deps.target")) { //$NON-NLS-1$
			Files.copy(is, depsTarget, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private URLClassLoader buildWorkspaceClassLoader() throws MojoExecutionException {
		Path bundlesDir = src.toPath();
		if (Files.exists(bundlesDir.resolve("plugins"))) { //$NON-NLS-1$
			bundlesDir = bundlesDir.resolve("plugins"); //$NON-NLS-1$
		}
		try {
			Collection<URL> urls = new ArrayList<>();
			
			Arrays.stream(DEFAULT_CLASSPATH)
				.map(this::getDependencyJar)
				.map(Path::toUri)
				.map(t -> {
					try {
						return t.toURL();
					} catch (MalformedURLException e) {
						throw new UncheckedIOException(e);
					}
				})
				.forEach(urls::add);

			try (Stream<Path> bundleStream = Files.list(bundlesDir)) {
				bundleStream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar")) //$NON-NLS-1$
					.map(p -> {
						try {
							return p.toUri().toURL();
						} catch (MalformedURLException e) {
							throw new UncheckedIOException(e);
						}
					}).forEach(urls::add);
			}
			return new URLClassLoader(urls.toArray(new URL[urls.size()]), ClassLoader.getSystemClassLoader());
		} catch (IOException | UncheckedIOException e) {
			throw new MojoExecutionException("Encountered exception building workspace ClassLoader", e);
		}
	}

	private boolean needsConstructor(Class<?> clazz) {
		if (true) {
			return false;
		}
		if (clazz.isInterface() || clazz.isEnum()) {
			return false;
		}
		if (Arrays.stream(clazz.getMethods()).noneMatch(m -> "<init>".equals(m.getName()))) { //$NON-NLS-1$
			return true;
		}
		return false;
	}

	private boolean shouldEmitClassName(String className) {
		// Ignore META-INF/versions, etc. for our needs
		if (className.startsWith("META-INF.")) { //$NON-NLS-1$
			return false;
		}
		// Ignore module-info
		if (className.endsWith(".module-info")) { //$NON-NLS-1$
			return false;
		}
		
		// Ignore com.sun.* classes, which are generally re-packaged XML classes
		if(className.startsWith("com.sun.")) { //$NON-NLS-1$
			return false;
		}
		
		// Ignore numbered anonymous classes
		int dollarIndex = className.indexOf('$');
		if(dollarIndex > -1) {
			try {
				Integer.parseInt(className.substring(dollarIndex+1));
				
				// Must be a numeric name - don't emit
				return false;
			} catch(NumberFormatException e) {
				// Not a numeric name
			}
		}
		
		return true;
	}

	private boolean shouldEmitClass(Class<?> clazz) {
		int dollarIndex = clazz.getName().indexOf('$');
		if (Modifier.isPrivate(clazz.getModifiers()) && dollarIndex == -1) {
			// Equinox has some cases of public methods referencing private internal types
			return false;
		}
		if (clazz.isAnonymousClass()) {
			return false;
		}
		if (ClassWriterConfig.SKIP_CLASSES.contains(clazz.getName())) {
			return false;
		}
		// "Anonymous" seems to not be enough of what I mean - look for number-only
		// classes like "JspClassLoader$1"
		if (dollarIndex > -1) {
			String postName = clazz.getName().substring(dollarIndex + 1);
			try {
				Integer.parseInt(postName);
				return false;
			} catch (NumberFormatException e) {
				// Not numeric only
			}
		}

		if (clazz.isSynthetic()) {
			return false;
		}
		try {
			if (clazz.getDeclaringClass() != null && clazz.getDeclaringClass().isAnonymousClass()) {
				// Used in e.g. org.apache.felix.resolver.ResolverImpl -> anon -> Computer
				return false;
			}
		} catch (IllegalAccessError e) {
			// Assume it's deep enough that we don't want it
			return false;
		} catch (IncompatibleClassChangeError e) {
			// Seen in exactly these kinds of cases - we don't want it
			return false;
		}

		// Implementation-specific classes geared towards OpenJ9
		if (clazz.getName().startsWith("org.eclipse.osgi.internal.cds.")) { //$NON-NLS-1$
			return false;
		}

		if (clazz.getPackage() != null && ClassWriterConfig.SKIP_PACKAGES.contains(clazz.getPackage().getName())) {
			return false;
		}

		// Domino re-packages the core XML APIs, but they are standard in all supported
		// Java versions
		// and trying to re-make them is trouble on Java > 8
		String className = clazz.getName();
		if (className.startsWith("javax.xml.stream.")) { //$NON-NLS-1$
			return false;
		} else if (className.startsWith("javax.xml.stream.events.")) { //$NON-NLS-1$
			return false;
		} else if (className.startsWith("javax.xml.datatype.")) { //$NON-NLS-1$
			return false;
		} else if (className.startsWith("javax.xml.validation.")) { //$NON-NLS-1$
			return false;
		} else if (className.startsWith("javax.xml.xpath.")) { //$NON-NLS-1$
			return false;
		} else if (className.startsWith("javax.xml.transform.")) { //$NON-NLS-1$
			return false;
		} else if (className.startsWith("javax.xml.parsers.")) { //$NON-NLS-1$
			return false;
		} else if (className.startsWith("javax.xml.crypto.")) { //$NON-NLS-1$
			return false;
		} else if (className.startsWith("javax.xml.catalog.")) { //$NON-NLS-1$
			return false;
		} else if (className.startsWith("org.xml.")) { //$NON-NLS-1$
			return false;
		} else if (className.startsWith("org.w3c.")) { //$NON-NLS-1$
			return false;
		}

		return true;
	}

	private boolean shouldEmitMethod(Class<?> clazz, Method m) {
		// Skip auto-generated methods and constructors for enums
		if (clazz.isEnum()) {
			if ("valueOf".equals(m.getName())) { //$NON-NLS-1$
				if (m.getParameterCount() == 1 && String.class.equals(m.getParameterTypes()[0])) {
					return false;
				}
			}
			if ("values".equals(m.getName())) { //$NON-NLS-1$
				if (m.getParameterCount() == 0) {
					return false;
				}
			}
			if ("<init>".equals(m.getName())) { //$NON-NLS-1$
				return false;
			}
		}

		if (m.isSynthetic()) {
			return false;
		}
		if (Modifier.isPrivate(m.getModifiers())) {
			return false;
		}
		if ("<clinit>".equals(m.getName())) { //$NON-NLS-1$
			return false;
		}
//		if("<init>".equals(m.getName())) { //$NON-NLS-1$
//			return true;
//		}
		return true;
	}

	private Path getDependencyJar(String artifactId) {
		List<ComponentDependency> dependencies = pluginDescriptor.getDependencies();
		ComponentDependency dep = dependencies.stream()
				.filter(a -> artifactId.equals(a.getArtifactId()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						MessageFormat.format("Could not find dependency: {0}", artifactId))); //$NON-NLS-1$

		// flatten-maven-plugin causes trouble with Tycho .qualifier, so alter the
		// version if necessary
		String version = dep.getVersion();
		if (version.endsWith(".qualifier")) { //$NON-NLS-1$
			version = version.substring(0, version.length() - ".qualifier".length()) + "-SNAPSHOT"; //$NON-NLS-1$ //$NON-NLS-2$
		}

		Artifact art = new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), version, "", dep.getType(), "", //$NON-NLS-1$ //$NON-NLS-2$
				new DefaultArtifactHandler());
		art = mavenSession.getLocalRepository().find(art);
		File file = art.getFile();
		Path result;
		if (file.toString().endsWith(".jar")) { //$NON-NLS-1$
			result = file.toPath();
			if (!Files.exists(result)) {
				throw new IllegalStateException(MessageFormat.format("Dependency JAR does not exist: {0}", result)); //$NON-NLS-1$
			}
		} else {
			result = Paths.get(file.toString() + ".jar"); //$NON-NLS-1$
		}
		if (!Files.exists(result)) {
			throw new IllegalStateException(MessageFormat.format("Dependency JAR does not exist: {0}", result)); //$NON-NLS-1$
		}
		return result;
	}
	
	private boolean shouldCopyBundle(String symbolicName) {
		if(symbolicName.startsWith("com.ibm")) { //$NON-NLS-1$
			if(symbolicName.startsWith("com.ibm.icu")) { //$NON-NLS-1$
				return false;
			} else if(symbolicName.startsWith("com.ibm.verse")) { //$NON-NLS-1$
				return false;
			} else {
				return true;
			}
		} else if(symbolicName.startsWith("com.hcl")) { //$NON-NLS-1$
			return true;
		} else if("org.osgi.service.webapplication".equals(symbolicName)) { //$NON-NLS-1$
			return true;
		} else {
			return false;
		}
	}
}
