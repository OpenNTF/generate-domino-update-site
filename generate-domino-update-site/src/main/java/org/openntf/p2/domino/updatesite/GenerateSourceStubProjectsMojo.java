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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Collectors;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.ConstantValue;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Utility;
import org.apache.bcel.generic.Type;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.openntf.p2.domino.updatesite.model.BundleInfo;

import com.ibm.commons.util.PathUtil;
import com.ibm.commons.util.StringUtil;

@Mojo(name="generateSourceProjects", requiresProject=false)
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
		"Bundle-RequiredExecutionEnvironment" //$NON-NLS-1$
	};

	/**
	 * Destination directory
	 */
	@Parameter(property="dest", required=true)
	private File dest;
	
	private URLClassLoader cl;
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try(URLClassLoader cl = (this.cl = buildWorkspaceClassLoader())) {
			super.execute();
		} catch(IOException e) {
			// Ignore - from closing the URLClassLoader
		}
		
		try {
			createTychoStructure(this.dest.toPath());
		} catch(IOException e) {
			throw new MojoExecutionException("Encountered exception building Tycho structure", e);
		}
	}
	
	@Override
	protected void processBundle(BundleInfo bundle, List<BundleInfo> bundles, Map<String, BundleInfo> bundlesByName,
			Path tempPom) throws MojoExecutionException {
		
		getLog().info(MessageFormat.format("Processing bundle {0}", bundle.getArtifactId()));
		
		Path plugins = dest.toPath().resolve("plugins"); //$NON-NLS-1$
		try {
			Files.createDirectories(plugins);
		} catch (IOException e) {
			throw new MojoExecutionException(MessageFormat.format("Unable to create plugins directory: {0}", plugins), e);
		}
		Path bundleBase = plugins.resolve(bundle.getArtifactId());
		try {
			Files.createDirectories(bundleBase);
		} catch (IOException e) {
			throw new MojoExecutionException(MessageFormat.format("Unable to create bundle directory: {0}", plugins), e);
		}
		
		Path src = bundleBase.resolve("src"); //$NON-NLS-1$
		
		Path origJarPath = Paths.get(bundle.getFilePath());
		try(JarFile origBundle = new JarFile(origJarPath.toFile(), false)) {
			copyManifest(bundleBase, origBundle);
			createSourceStubs(src, origBundle);
			createBuildStubs(bundleBase);
		} catch (IOException e) {
			throw new MojoExecutionException(MessageFormat.format("Encountered exception working with JAR {0}", origJarPath), e);
		}
	}
	
	/**
	 * Copies a subset of the original bundle's MANIFEST entries to a new MANIFEST.MF file.
	 * 
	 * @param bundleBase the base path of the new source bundle
	 * @param origBundle the original bundle as a {@link JarFile}
	 * @throws MojoExecutionException if there is a problem reading or writing the manifest
	 * @see {@link #COPY_HEADERS}
	 */
	private void copyManifest(Path bundleBase, JarFile origBundle) throws MojoExecutionException {
		try {
			Manifest origManifest = origBundle.getManifest();
			Attributes origAttributes = origManifest.getMainAttributes();
			
			Manifest targetManifest = new Manifest();
			Attributes targetAttributes = targetManifest.getMainAttributes();
			for(String headerName : COPY_HEADERS) {
				String val = origAttributes.getValue(headerName);
				if(StringUtil.isNotEmpty(val)) {
					targetAttributes.putValue(headerName, val);
				}
			}
			
			Path metaInf = bundleBase.resolve("META-INF"); //$NON-NLS-1$
			Files.createDirectories(metaInf);
			Path manifest = metaInf.resolve("MANIFEST.MF"); //$NON-NLS-1$
			try(OutputStream os = Files.newOutputStream(manifest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				targetManifest.write(os);
			}
		} catch (IOException e) {
			throw new MojoExecutionException(MessageFormat.format("Encountered exception copying manifest to {0}", bundleBase), e);
		}
	}
	
	private void createSourceStubs(Path src, JarFile origBundle) throws MojoExecutionException, IOException {
		Collection<String> exportedPackages = findExportedPackages(origBundle);
		
		// Pre-build a map of class data so that inner classes can be written
		//   after outer classes
		// Map to outer class name to a list of 1 or more classes
		Map<String, SortedSet<JavaClass>> classes = new HashMap<>();
		
		for(ZipEntry entry : Collections.list(origBundle.entries())) {
			String entryName = entry.getName();
			
			// Extract applicable class files
			if(entryName.endsWith(".class") && !entryName.endsWith("/package-info.class")) { //$NON-NLS-1$ //$NON-NLS-2$
				processClassEntry(origBundle, entry, exportedPackages, classes);
			}
		}
		
		// Now do the same for Bundle-ClassPath entries
		Manifest manifest = origBundle.getManifest();
		String classpath = manifest.getMainAttributes().getValue("Bundle-ClassPath"); //$NON-NLS-1$
		if(StringUtil.isNotEmpty(classpath)) {
			String[] cpEntries = StringUtil.splitString(classpath, ',');
			for(String cpEntry : cpEntries) {
				String cpEntryName = cpEntry.trim();
				if(!cpEntryName.isEmpty()) {
					ZipEntry entry = origBundle.getJarEntry(cpEntryName);
					if(entry != null && !entry.isDirectory()) {
						// Make a temporary copy and read from there
						Path tempFile = Files.createTempFile("embed", ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
						try {
							try(InputStream eis = origBundle.getInputStream(entry)) {
								Files.copy(eis, tempFile, StandardCopyOption.REPLACE_EXISTING);
							}
							try(ZipFile embedBundle = new ZipFile(tempFile.toFile())) {
								for(ZipEntry embedEntry : Collections.list(embedBundle.entries())) {
									String entryName = embedEntry.getName();
									
									// Extract applicable class files
									if(entryName.endsWith(".class") && !entryName.endsWith("/package-info.class")) { //$NON-NLS-1$ //$NON-NLS-2$
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
		
		for(Map.Entry<String, SortedSet<JavaClass>> entry : classes.entrySet()) {
			String className = entry.getKey();
			SortedSet<JavaClass> pool = entry.getValue();
			String packageName = className.substring(0, className.lastIndexOf('.'));
			String baseName = className.substring(packageName.length()+1);
			createStubClass(src, packageName, baseName, pool);
		}
	}
	
	private void processClassEntry(ZipFile origBundle, ZipEntry entry, Collection<String> exportedPackages, Map<String, SortedSet<JavaClass>> classes) throws MojoExecutionException {
		String entryName = entry.getName();
		int slashIndex = entryName.lastIndexOf('/');
		if(slashIndex > -1) {
			String packageName = entryName.substring(0, slashIndex).replace('/', '.');
			if(exportedPackages.contains(packageName)) {
				String className = entryName.substring(slashIndex+1, entryName.length()-".class".length()); //$NON-NLS-1$
				try(InputStream is = origBundle.getInputStream(entry)) {
					ClassParser parser = new ClassParser(is, packageName + "." + className); //$NON-NLS-1$
					JavaClass clazz = parser.parse();
					
					if(!(clazz.isPrivate() || clazz.isAnonymous())) {

						String baseName;
						int dollarIndex = className.indexOf('$');
						if(dollarIndex > -1) {
							baseName = className.substring(0, dollarIndex);
						} else {
							baseName = className;
						}
							
						SortedSet<JavaClass> pool = classes.computeIfAbsent(packageName + "." + baseName, //$NON-NLS-1$
							key -> new TreeSet<>(Comparator.comparing(JavaClass::getClassName))
						);
						pool.add(clazz);
					}
				} catch (IOException e) {
					throw new MojoExecutionException(MessageFormat.format("Encountered exception reading class file {0} in {1}", entryName, origBundle.getName()), e);
				}
			}
		}
	}
	
	private Collection<String> findExportedPackages(JarFile jarFile) throws MojoExecutionException {
		try {
			Manifest origManifest = jarFile.getManifest();
			Attributes origAttributes = origManifest.getMainAttributes();
			String exportPackage = origAttributes.getValue("Export-Package"); //$NON-NLS-1$
			if(StringUtil.isEmpty(exportPackage)) {
				return Collections.emptyList();
			} else {
				String[] packages = StringUtil.splitString(exportPackage, ',');
				return Arrays.stream(packages)
					.map(p -> {
						int semiIndex = p.indexOf(';');
						if(semiIndex > -1) {
							return p.substring(0, semiIndex);
						} else {
							return p;
						}
					})
					.collect(Collectors.toSet());
			}
		} catch (IOException e) {
			throw new MojoExecutionException(MessageFormat.format("Encountered exception reading exported packages from {0}", jarFile.getName()), e);
		}
	}
	
	private void createStubClass(Path src, String packageName, String className, SortedSet<JavaClass> pool) throws MojoExecutionException {
		try {
			JavaClass clazz = pool.first();
			if(!clazz.isPrivate()) {
				Path outputPath = src.resolve(packageName.replace(".", src.getFileSystem().getSeparator())).resolve(className + ".java"); //$NON-NLS-1$ //$NON-NLS-2$				
				Files.createDirectories(outputPath.getParent());
				
				try(
					BufferedWriter w = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
					PrintWriter pw = new PrintWriter(w)
				) {
					pw.println("package " + packageName + ";"); //$NON-NLS-1$ //$NON-NLS-2$
					pw.println();
					
					writeClass(pw, pool, className);
				}
			}
		} catch (IOException e) {
			throw new MojoExecutionException(MessageFormat.format("Encountered exception processing class {0}.{1} to {2}", packageName, className, src), e);
		}
	}
	
	private void writeClass(PrintWriter pw, SortedSet<JavaClass> pool, String className) {
		JavaClass clazz = pool.first();
		SortedSet<JavaClass> innerClasses = pool.stream()
			.skip(1)
			.collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(JavaClass::getClassName))));
		
		// TODO annotations? They're likely not required for compilation
		
		// Class opener
		if(clazz.isPublic()) {
			pw.print("public "); //$NON-NLS-1$
		}
		if(clazz.isEnum()) {
			pw.print("enum "); //$NON-NLS-1$
		} else {
			if(clazz.isStatic() || Modifier.isStatic(clazz.getModifiers())) {
				pw.print("static "); //$NON-NLS-1$
			}
			if(clazz.isFinal()) {
				pw.print("final "); //$NON-NLS-1$
			}
			if(clazz.isAbstract()) {
				pw.print("abstract "); //$NON-NLS-1$
			}
			pw.print(Utility.classOrInterface(clazz.getAccessFlags()));
		}
		pw.print(" "); //$NON-NLS-1$
		pw.print(className);
		
		// TODO generics
		
		String sup = clazz.getSuperclassName();
		if(StringUtil.isNotEmpty(sup) && !"java.lang.Object".equals(sup) && !clazz.isEnum()) { //$NON-NLS-1$
			pw.print(" extends " + sup.replace('$', '.')); //$NON-NLS-1$
		}
		List<String> interfaces = Arrays.asList(clazz.getInterfaceNames());
		if(!interfaces.isEmpty()) {
			if(clazz.isInterface()) {
				pw.print(" extends "); //$NON-NLS-1$
			} else {
				pw.print(" implements "); //$NON-NLS-1$
			}
			List<String> intNames = interfaces.stream()
				.map(i -> i.replace('$', '.'))
				.collect(Collectors.toList());
			pw.print(String.join(", ", intNames)); //$NON-NLS-1$
		}
		pw.println(" {"); //$NON-NLS-1$
		
		// Visible properties
		// Run through enum properties first
		if(clazz.isEnum()) {
			for(Field f : clazz.getFields()) {
				if(f.isEnum()) {
					pw.print(f.getName());
					pw.print(',');
				}
			}
			pw.println(';');
		}
		// Now normal properties
		for (Field f : clazz.getFields()) {
			if (!f.isEnum() && (f.isPublic() || f.isProtected())) {
				pw.print("\t"); //$NON-NLS-1$
				pw.print("public "); //$NON-NLS-1$
				if(f.isStatic()) {
					pw.print("static "); //$NON-NLS-1$
				}
				if(f.isFinal()) {
					pw.print("final "); //$NON-NLS-1$
				}
				String fieldSig = Utility.signatureToString(f.getSignature());
				// Post-patch for inner types
				fieldSig = fieldSig.replace(f.getType().toString(), f.getType().toString().replace('$', '.'));
				pw.print(fieldSig);
				pw.print(" "); //$NON-NLS-1$
				pw.print(f.getName());
				final ConstantValue cv = f.getConstantValue();
				if (cv != null) {
					pw.print(" = "); //$NON-NLS-1$
					// Special handling for Double.NaN
					if(Type.DOUBLE.equals(f.getType()) && "NaN".equals(cv.toString())) { //$NON-NLS-1$
						pw.print("Double.NaN"); //$NON-NLS-1$
					} else {
						pw.print(cv);
					}
				} else if(f.isFinal()) {
					pw.print(" = "); //$NON-NLS-1$
					pw.print(defaultReturnValue(f.getType()));
				}

				pw.println(";"); //$NON-NLS-1$
				pw.println();
			}
		}
		
		// Methods
		for(Method m : clazz.getMethods()) {
			if(shouldEmitMethod(clazz, m)) {
				
				final String access = Utility.accessToString(m.getAccessFlags());
		        // Get name and signature from constant pool
		        ConstantUtf8 c = (ConstantUtf8) m.getConstantPool().getConstant(m.getSignatureIndex(), Const.CONSTANT_Utf8);
		        String signature = c.getBytes();
		        c = (ConstantUtf8) m.getConstantPool().getConstant(m.getNameIndex(), Const.CONSTANT_Utf8);
		        String name = c.getBytes();
		        if("<init>".equals(name)) { //$NON-NLS-1$
		        	name = className;
		        }
		        pw.print("\t"); //$NON-NLS-1$
		        String sig = Utility.methodSignatureToString(signature, name, access, true,
		                m.getLocalVariableTable());
		        // Post-patch for constructors
		        if("<init>".equals(m.getName())) { //$NON-NLS-1$
		        	sig = sig.replace("void " + className, className); //$NON-NLS-1$
		        	System.out.println(clazz.getClassName() + " - emitting ctor " + sig);
		        }
		        // Post-patch for inner-class constructors
		        if(clazz.isNested() && "<init>".equals(m.getName())) { //$NON-NLS-1$
		        	String housingClass = clazz.getClassName().substring(0, clazz.getClassName().indexOf('$'));
		        	sig = sig.replace(className+"("+housingClass+",", className+"("); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		        }
		        // Post-patch for inner-class properties
		        sig = sig.replace('$', '.');
		        // Post-post-patch for generated classes like tcpmon in Axis
		        sig = sig.replace("this.0", "this$0"); //$NON-NLS-1$ //$NON-NLS-2$
		        // Don't write synthetic, volatile, or transient
		        sig = sig.replace(" synthetic ", " "); //$NON-NLS-1$ //$NON-NLS-2$
		        sig = sig.replace(" volatile ", " "); //$NON-NLS-1$ //$NON-NLS-2$
		        sig = sig.replace(" transient ", " "); //$NON-NLS-1$ //$NON-NLS-2$
		        
		        // Mark default methods as such
		        if(clazz.isInterface() && !m.isAbstract() && !m.isStatic()) {
		        	sig = sig.replace("public ", "public default "); //$NON-NLS-1$ //$NON-NLS-2$
		        }
		        
		        pw.print(sig);
		        
				ExceptionTable exceps = m.getExceptionTable();
				if(exceps != null) {
					List<String> excepNames = Arrays.asList(exceps.getExceptionNames());
					if(!excepNames.isEmpty()) {
						pw.print(" throws "); //$NON-NLS-1$
						pw.print(String.join(", ", excepNames)); //$NON-NLS-1$
					}
				}
				
				if("<init>".equals(m.getName()) && !m.isEnum()) { //$NON-NLS-1$
					writeBasicConstructorBody(pw, clazz);
				} else if(m.isNative() || m.isAbstract()) {
					pw.println(";"); //$NON-NLS-1$
				} else {
					Type returnType = m.getReturnType();
					pw.println(" {"); //$NON-NLS-1$
					if(!Type.VOID.equals(returnType)) {
						pw.print("\t\treturn "); //$NON-NLS-1$
						pw.print(defaultReturnValue(returnType));
						pw.println(";"); //$NON-NLS-1$
					}
					pw.println("\t}"); //$NON-NLS-1$
				}
				
				pw.println();
			}
		}
		
		// There may have not been a constructor, but one may be required by the superclass
		if(needsConstructor(clazz)) {
			pw.println("\tpublic "); //$NON-NLS-1$
			pw.print(className);
			pw.print("()"); //$NON-NLS-1$
			writeBasicConstructorBody(pw, clazz);
		}
		
		// Write out any inner classes
		for(JavaClass innerClass : innerClasses) {
			// Process only direct inner classes, i.e. those where the last $ is just past the end of this class
			int dollarIndex = innerClass.getClassName().lastIndexOf('$');
			if(dollarIndex == clazz.getClassName().length()) {
				// While here, collect any deeper inner classes
				SortedSet<JavaClass> deeperClasses = pool.stream()
					.skip(1)
					.filter(c -> c.getClassName().startsWith(innerClass.getClassName()))
					.collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(JavaClass::getClassName))));
				
				String innerClassName = innerClass.getClassName().substring(dollarIndex+1);
				if(Character.isDigit(innerClassName.charAt(0))) {
					int dotIndex = innerClass.getClassName().lastIndexOf('.');
					innerClassName = innerClass.getClassName().substring(dotIndex+1);
				}
				writeClass(pw, deeperClasses, innerClassName);
			}
		}
		
		// End class
		pw.println("}"); //$NON-NLS-1$
	}

	private String defaultReturnValue(Type returnType) {
		if(Type.BOOLEAN.equals(returnType)) {
			return "false"; //$NON-NLS-1$
		} else if(
			Type.BYTE.equals(returnType)
			|| Type.DOUBLE.equals(returnType)
			|| Type.FLOAT.equals(returnType)
			|| Type.INT.equals(returnType)
			|| Type.LONG.equals(returnType)
			|| Type.SHORT.equals(returnType)
		) {
			return "0"; //$NON-NLS-1$
		} else if(Type.CHAR.equals(returnType)) {
			return "'\\0'"; //$NON-NLS-1$
		}
		return "null"; //$NON-NLS-1$
	}
	
	private String defaultReturnValue(Class<?> returnType) {
		if(Boolean.TYPE.equals(returnType)) {
			return "false"; //$NON-NLS-1$
		} else if(
			Byte.TYPE.equals(returnType)
			|| Double.TYPE.equals(returnType)
			|| Float.TYPE.equals(returnType)
			|| Integer.TYPE.equals(returnType)
			|| Long.TYPE.equals(returnType)
			|| Short.TYPE.equals(returnType)
		) {
			return "0"; //$NON-NLS-1$
		} else if(Character.TYPE.equals(returnType)) {
			return "'\\0'"; //$NON-NLS-1$
		}
		return "null"; //$NON-NLS-1$
	}
	
	private String toCastableName(Class<?> returnType) {
		if(returnType.isArray()) {
			Class<?> component = returnType.getComponentType();
			return toCastableName(component) + "[]"; //$NON-NLS-1$
		}
		return returnType.getName();
	}
	
	private void createBuildStubs(Path bundleBase) throws IOException {
		Path buildProperties = bundleBase.resolve("build.properties"); //$NON-NLS-1$
		Properties props = new Properties();
		props.put("source..", "src"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("output..", "target/classes"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("bin.includes", "META-INF/,\\\n\t."); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("tycho.pomless.parent", "../../pom.xml"); //$NON-NLS-1$ //$NON-NLS-2$
		try(OutputStream os = Files.newOutputStream(buildProperties, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			props.store(os, null);
		}
	}
	
	private void createTychoStructure(Path projectBase) throws IOException {
		// Register the Tycho pomless extension
		Path mvn = projectBase.resolve(".mvn"); //$NON-NLS-1$
		Files.createDirectories(mvn);
		Path extensions = mvn.resolve("extensions.xml"); //$NON-NLS-1$
		try(InputStream is = getClass().getResourceAsStream("/extensions.xml")) { //$NON-NLS-1$
			Files.copy(is, extensions, StandardCopyOption.REPLACE_EXISTING);
		}
		
		// Create our base POM
		Path pomXml = projectBase.resolve("pom.xml"); //$NON-NLS-1$
		try(InputStream is = getClass().getResourceAsStream("/tycho.pom.xml")) { //$NON-NLS-1$
			Files.copy(is, pomXml, StandardCopyOption.REPLACE_EXISTING);
		}
	}
	
	private URLClassLoader buildWorkspaceClassLoader() throws MojoExecutionException {
		Path bundlesDir = src.toPath();
		if(Files.exists(bundlesDir.resolve("plugins"))) { //$NON-NLS-1$
			bundlesDir = bundlesDir.resolve("plugins"); //$NON-NLS-1$
		}
		try {
			Collection<URL> urls = new ArrayList<>();
			
			// Provide CORBA and JEE dependencies for Java > 9
			// This is declared in the project dependencies, so will have been downloaded
			urls.add(findLocalMavenArtifact("org.glassfish.corba", "glassfish-corba-omgapi", "4.2.5", "jar").toUri().toURL()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			urls.add(findLocalMavenArtifact("jakarta.jms", "jakarta.jms-api", "2.0.2", "jar").toUri().toURL()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			urls.add(findLocalMavenArtifact("xmlpull", "xmlpull", "1.1.3.4d_b4_min", "jar").toUri().toURL()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			
			Files.list(bundlesDir)
				.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar")) //$NON-NLS-1$
				.map(p -> {
					try {
						return p.toUri().toURL();
					} catch (MalformedURLException e) {
						throw new UncheckedIOException(e);
					}
				})
				.forEach(urls::add);
			return new URLClassLoader(urls.toArray(new URL[urls.size()]), ClassLoader.getSystemClassLoader());
		} catch(IOException | UncheckedIOException e) {
			throw new MojoExecutionException("Encountered exception building workspace ClassLoader", e);
		}
	}
	
	private static Path findLocalMavenArtifact(String groupId, String artifactId, String version, String type) {
		String mavenRepo = System.getProperty("maven.repo.local"); //$NON-NLS-1$
		if (StringUtil.isEmpty(mavenRepo)) {
			mavenRepo = PathUtil.concat(System.getProperty("user.home"), ".m2", File.separatorChar); //$NON-NLS-1$ //$NON-NLS-2$
			mavenRepo = PathUtil.concat(mavenRepo, "repository", File.separatorChar); //$NON-NLS-1$
		}
		String groupPath = groupId.replace('.', File.separatorChar);
		Path localPath = Paths.get(mavenRepo).resolve(groupPath).resolve(artifactId).resolve(version);
		String fileName = StringUtil.format("{0}-{1}.{2}", artifactId, version, type); //$NON-NLS-1$
		Path localFile = localPath.resolve(fileName);
		
		if(!Files.isRegularFile(localFile)) {
			throw new RuntimeException("Unable to locate Maven artifact: " + localFile);
		}

		return localFile;
	}
	
	private void writeBasicConstructorBody(PrintWriter pw, JavaClass clazz) {
		pw.println(" {"); //$NON-NLS-1$
		// Constructors must look for a suitable parent constructor
		try {
			Class<?> superClass = this.cl.loadClass(clazz.getSuperclassName());
			Constructor<?>[] ctors;
			try {
				ctors = superClass.getDeclaredConstructors();
			} catch(NoClassDefFoundError e) {
				// Will show up with older JVMs for things like com.sun.net.ssl.internal.ssl.Provider
				ctors = new Constructor<?>[0];
				getLog().warn(MessageFormat.format("Unable to process superclass constructors for {0}: {1}", clazz.getClassName(), e.toString()));
			}
			if(ctors.length > 0) {
				try {
					superClass.getDeclaredConstructor();
				} catch(NoSuchMethodException e) {
					// Pick the first one and make a stub call to it
					Constructor<?> ctor = ctors[0];
					pw.print("\t\tsuper("); //$NON-NLS-1$
					String params = Arrays.stream(ctor.getParameterTypes())
						.map(t -> '(' + toCastableName(t) + ')' + defaultReturnValue(t))
						.collect(Collectors.joining(", ")); //$NON-NLS-1$
					pw.print(params);
					pw.println(");"); //$NON-NLS-1$
				}
			}
		} catch (ClassNotFoundException e) {
			getLog().error(MessageFormat.format("Encountered error locating superconstructors for {0}", clazz.getClassName()), e);
		}
		pw.println("\t}"); //$NON-NLS-1$
	}
	
	private boolean needsConstructor(JavaClass clazz) {
		if(clazz.isInterface() || clazz.isEnum()) {
			return false;
		}
		if(Arrays.stream(clazz.getMethods()).noneMatch(m -> "<init>".equals(m.getName()))) { //$NON-NLS-1$
			return true;
		}
		return false;
	}
	
	private boolean shouldEmitMethod(JavaClass clazz, Method m) {
		// Skip auto-generated methods and constructors for enums
		if(clazz.isEnum()) {
			if("valueOf".equals(m.getName())) { //$NON-NLS-1$
				if(m.getArgumentTypes().length == 1 && Type.STRING.equals(m.getArgumentTypes()[0])) {
					return false;
				}
			}
			if("values".equals(m.getName())) { //$NON-NLS-1$
				if(m.getArgumentTypes().length == 0) {
					return false;
				}
			}
			if("<init>".equals(m.getName())) { //$NON-NLS-1$
				return false;
			}
		}
		
		if(m.isSynthetic()) {
			return false;
		}
		if(m.isPrivate()) {
			return false;
		}
		if("<clinit>".equals(m.getName())) { //$NON-NLS-1$
			return false;
		}
//		if("<init>".equals(m.getName())) { //$NON-NLS-1$
//			return true;
//		}
		return true;
	}
}
