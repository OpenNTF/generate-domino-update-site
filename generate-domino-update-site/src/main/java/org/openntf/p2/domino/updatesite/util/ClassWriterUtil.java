package org.openntf.p2.domino.updatesite.util;

import java.io.PrintWriter;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.logging.Log;

public enum ClassWriterUtil {
	;
	
	public static <T extends GenericDeclaration> String printTypeVariables(TypeVariable<T>[] typeVars) {
		StringBuilder pw = new StringBuilder();
		if(typeVars != null && typeVars.length > 0) {
			pw.append('<');
			String paramString = Arrays.stream(typeVars)
				.map(param -> {
					StringBuilder result = new StringBuilder();
					result.append(param.getName().replace('$', '.'));
					result.append(printBounds(param.getAnnotatedBounds()));
					return result.toString();
				})
				.collect(Collectors.joining(", ")); //$NON-NLS-1$
			pw.append(paramString);
			pw.append('>');
		}
		return pw.toString();
	}
	
	public static String printBounds(AnnotatedType[] bounds) {
		StringBuilder result = new StringBuilder();
		if(bounds != null && bounds.length > 0) {
			String boundsString = Arrays.stream(bounds)
				.filter(bound -> !Object.class.equals(bound.getType()))
				.map(bound -> {
					return bound.getType().toString().replace('$', '.');
				})
				.collect(Collectors.joining(" & ")); //$NON-NLS-1$
			if(!boundsString.isEmpty()) {
				result.append(" extends "); //$NON-NLS-1$
				result.append(boundsString);
			}
		}
		return result.toString();
	}

	public static void printParameters(PrintWriter pw, Parameter[] parameters) {
		pw.print('(');

		String params = Arrays.stream(parameters)
				.map(p -> toCastableName(p.getParameterizedType()) + " " + p.getName()) //$NON-NLS-1$
				.collect(Collectors.joining(", ")); //$NON-NLS-1$
		pw.print(params);

		pw.print(')');
	}

	public static String toCastableName(Type type) {
		if (type instanceof Class) {
			if (((Class<?>) type).isArray()) {
				Class<?> component = ((Class<?>) type).getComponentType();
				return toCastableName(component) + "[]"; //$NON-NLS-1$
			} else {
				return getClassName((Class<?>) type).replace('$', '.');
			}
		}
		return type.toString().replace('$', '.');
	}
	
	public static String toCastableName(AnnotatedType type) {
		return toCastableName(type.getType());
	}
	
	private static String getClassName(Class<?> clazz) {
		Package p = clazz.getPackage();
		if(p != null && p.getName().equals("java.lang")) { //$NON-NLS-1$
			return clazz.getSimpleName();
		} else {
			return clazz.getName();
		}
	}
	
	public static String printClassSignature(Class<?> clazz, String className) {
		StringBuilder result = new StringBuilder();
		
		int modifiers = clazz.getModifiers();
		if (Modifier.isPublic(modifiers)) {
			result.append("public "); //$NON-NLS-1$
		}
		if (clazz.isEnum()) {
			result.append("enum "); //$NON-NLS-1$
		} else {
			if (Modifier.isStatic(modifiers)) {
				result.append("static "); //$NON-NLS-1$
			}
			if (Modifier.isFinal(modifiers)) {
				result.append("final "); //$NON-NLS-1$
			}
			if (Modifier.isAbstract(modifiers)) {
				result.append("abstract "); //$NON-NLS-1$
			}
			if (clazz.isInterface()) {
				result.append("interface"); //$NON-NLS-1$
			} else {
				result.append("class"); //$NON-NLS-1$
			}
		}
		result.append(" "); //$NON-NLS-1$
		result.append(className);
		
		@SuppressWarnings("unchecked")
		TypeVariable<Class<?>>[] genericParams = (TypeVariable<Class<?>>[])(Object)clazz.getTypeParameters();
		result.append(printTypeVariables(genericParams));

		AnnotatedType sup = clazz.getAnnotatedSuperclass();
		if (sup != null && !"java.lang.Object".equals(sup.getType().getTypeName()) && !clazz.isEnum()) { //$NON-NLS-1$
			result.append(" extends " + toCastableName(sup.getType())); //$NON-NLS-1$
		}
		
		List<AnnotatedType> interfaces = Arrays.asList(clazz.getAnnotatedInterfaces());
		if (!interfaces.isEmpty()) {
			if (clazz.isInterface()) {
				result.append(" extends "); //$NON-NLS-1$
			} else {
				result.append(" implements "); //$NON-NLS-1$
			}
			List<String> intNames = interfaces.stream()
				.map(i -> {
					StringBuilder result2 = new StringBuilder();
					result2.append(toCastableName(i.getType()));
					return result2.toString();
				})
				.collect(Collectors.toList());
			result.append(String.join(", ", intNames)); //$NON-NLS-1$
		}
		return result.toString();
	}
	
	public static String writeBasicConstructorBody(Class<?> clazz, ClassLoader cl, Log log) {
		
		StringBuilder result = new StringBuilder();
		Class<?> sup = clazz.getSuperclass();
		if (sup == null) {
			return ""; //$NON-NLS-1$
		}

		result.append(" {\n"); //$NON-NLS-1$
		// Constructors must look for a suitable parent constructor
		try {
			Class<?> superClass = cl.loadClass(sup.getName());
			Constructor<?>[] ctors;
			try {
				ctors = superClass.getDeclaredConstructors();
			} catch (NoClassDefFoundError e) {
				// Will show up with older JVMs for things like
				// com.sun.net.ssl.internal.ssl.Provider
				ctors = new Constructor<?>[0];
				if(log != null) {
					log.warn(MessageFormat.format("Unable to process superclass constructors for {0}: {1}",
							clazz.getName(), e.toString()));
				}
			}
			if (ctors.length > 0) {
				try {
					superClass.getDeclaredConstructor();
				} catch (NoSuchMethodException e) {
					// Pick the first one and make a stub call to it
					Constructor<?> ctor = ctors[0];
					result.append("\t\tsuper("); //$NON-NLS-1$
					String params = Arrays.stream(ctor.getAnnotatedParameterTypes())
							.map(t -> '(' + toCastableName(t) + ')' + defaultReturnValue(t))
							.collect(Collectors.joining(", ")); //$NON-NLS-1$
					result.append(params);
					result.append(");\n"); //$NON-NLS-1$
				}
			}
		} catch (ClassNotFoundException e) {
			if(log != null) {
				log.error(
					MessageFormat.format("Encountered error locating superconstructors for {0}", clazz.getName()), e);
			}
		}
		result.append("\t}\n"); //$NON-NLS-1$
		return result.toString();
	}

	public static String defaultReturnValue(Type returnType) {
		if (Boolean.TYPE.equals(returnType)) {
			return "false"; //$NON-NLS-1$
		} else if (Byte.TYPE.equals(returnType) || Double.TYPE.equals(returnType) || Float.TYPE.equals(returnType)
				|| Integer.TYPE.equals(returnType) || Long.TYPE.equals(returnType) || Short.TYPE.equals(returnType)) {
			return "0"; //$NON-NLS-1$
		} else if (Character.TYPE.equals(returnType)) {
			return "'\\0'"; //$NON-NLS-1$
		}
		return "null"; //$NON-NLS-1$
	}
	
	public static String defaultReturnValue(AnnotatedType returnType) {
		return defaultReturnValue(returnType.getType());
	}
}
