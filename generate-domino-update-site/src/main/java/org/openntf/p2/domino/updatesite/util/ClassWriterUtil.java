package org.openntf.p2.domino.updatesite.util;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.maven.plugin.logging.Log;
import org.openntf.p2.domino.updatesite.GenerateSourceStubProjectsMojo;

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
					Type type = bound.getType();
					if(type instanceof Class) {
						return ((Class<?>)type).getName().replace('$', '.');
					} else {
						return bound.getType().toString().replace('$', '.');
					}
				})
				.collect(Collectors.joining(" & ")); //$NON-NLS-1$
			if(!boundsString.isEmpty()) {
				result.append(" extends "); //$NON-NLS-1$
				result.append(boundsString);
			}
		}
		return result.toString();
	}

	public static String printParameters(Parameter[] parameters) {
		StringBuilder result = new StringBuilder();
		result.append('(');

		String params = Arrays.stream(parameters)
				.map(p -> toCastableName(p.getParameterizedType()) + " " + p.getName()) //$NON-NLS-1$
				.collect(Collectors.joining(", ")); //$NON-NLS-1$
		result.append(params);

		result.append(')');
		return result.toString();
	}
	
	public static String printConstructors(Class<?> clazz, ClassLoader cl, String className, Log log) {
		StringBuilder result = new StringBuilder();
		for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
			if(ctor.isSynthetic()) {
				continue;
			}
			
			result.append('\t');

			int cmod = ctor.getModifiers();

			if (Modifier.isPublic(cmod)) {
				result.append("public "); //$NON-NLS-1$
			} else if (Modifier.isProtected(cmod)) {
				result.append("protected "); //$NON-NLS-1$
			} else if (Modifier.isPrivate(cmod)) {
				result.append("private "); //$NON-NLS-1$
			}

			if (Modifier.isNative(cmod)) {
				result.append("native "); //$NON-NLS-1$
			}

			result.append(className);

			// Inner non-static classes have an implicit first argument of their parent object;
			//   don't emit that param
			Parameter[] params = ctor.getParameters();
			if(clazz.getEnclosingClass() != null) {
				if(!Modifier.isStatic(clazz.getModifiers())) {
					params = Arrays.stream(params).skip(1).toArray(Parameter[]::new);
				}
			}
			result.append(printParameters(params));
			Type[] exceps = ctor.getGenericExceptionTypes();
			if (exceps != null && exceps.length > 0) {
				List<String> excepNames = Arrays.stream(exceps).map(ClassWriterUtil::toCastableName)
						.collect(Collectors.toList());
				result.append(" throws "); //$NON-NLS-1$
				result.append(String.join(", ", excepNames)); //$NON-NLS-1$
			}

			result.append(writeBasicConstructorBody(clazz, ctor, cl, log));
			result.append('\n');
		}
		return result.toString();
	}

	public static String toCastableName(Type type) {
		if (type instanceof Class) {
			if (((Class<?>) type).isArray()) {
				Class<?> component = ((Class<?>) type).getComponentType();
				return toCastableName(component) + "[]"; //$NON-NLS-1$
			} else {
				return ((Class<?>) type).getName().replace('$', '.');
			}
		}
		return type.toString().replace('$', '.');
	}
	
	public static String toCastableName(Class<?> clazz, AnnotatedType type) {
		Type t = type.getType();
		
		// Check for the case where a class is extending a generic superclass without generics,
		//   like HashableWeakReference
		Type sup = clazz.getGenericSuperclass();
		if(sup instanceof Class) {
			if(((Class<?>)sup).getTypeParameters().length > 0) {
				if(clazz.getTypeParameters().length == 0) {
					return "Object"; //$NON-NLS-1$
				}
			}
		}
		
		return toCastableName(t);
	}
	
	public static String printClassSignature(Class<?> clazz, String className) {
		StringBuilder result = new StringBuilder();
		
		int modifiers = clazz.getModifiers();
		if (Modifier.isPublic(modifiers) || ClassWriterConfig.PUBLIC_CLASSES.contains(clazz.getName())) {
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
	
	public static String printClassFields(Class<?> clazz, Log log) {
		int constantVal = 0;
		
		Collection<String> skip = ClassWriterConfig.SKIP_FIELDS.get(clazz.getName());
		StringBuilder result = new StringBuilder();
		for (Field f : clazz.getDeclaredFields()) {
			if(f.isSynthetic()) {
				continue;
			}
			if(skip != null && skip.contains(f.getName())) {
				continue;
			}
			
			int fmod = f.getModifiers();
			if (!f.isEnumConstant() && !Modifier.isPrivate(fmod)) {
				result.append("\t"); //$NON-NLS-1$
				result.append("public "); //$NON-NLS-1$
				if (Modifier.isStatic(fmod)) {
					result.append("static "); //$NON-NLS-1$
				}
				// Ignore final for non-statics, since we don't care how it's set
				if (Modifier.isStatic(fmod) && Modifier.isFinal(fmod)) {
					result.append("final "); //$NON-NLS-1$
				}
				String fieldSig = toCastableName(f.getType());
				result.append(fieldSig);
				result.append(" "); //$NON-NLS-1$
				result.append(f.getName());
				
				if (Modifier.isStatic(fmod) && Modifier.isFinal(fmod)) {
					try {
						f.setAccessible(true);
						Class<?> ftype = f.getType();
						if(String.class.equals(ftype)) {
							// Check for a known override
							Map<String, String> overrides = ClassWriterConfig.KNOWN_STRING_CONSTANTS.get(clazz.getName());
							if(overrides != null && overrides.containsKey(f.getName())) {
								String val = overrides.get(f.getName());
								result.append(" = "); //$NON-NLS-1$
								result.append('"');
								result.append(StringEscapeUtils.escapeJava(val));
								result.append('"');
							} else {
								try {
									final Object cv = f.get(null);
									if(cv == null) {
										result.append(" = null"); //$NON-NLS-1$
									} else {
										result.append(" = "); //$NON-NLS-1$
										result.append('"');
										result.append(StringEscapeUtils.escapeJava(cv.toString()));
										result.append('"');
									}
								} catch(Throwable t) {
									// Will be due to the string actually being derived, and could
									//   be any number of problems. Just write nothing
									if(log != null && log.isWarnEnabled()) {
										log.warn(MessageFormat.format("Encountered exception writing String field {0}.{1}",
											clazz.getName(), f.getName()), t);
									}
								}
							}
						} else if(Number.class.isAssignableFrom(ftype)) {
							result.append(" = "); //$NON-NLS-1$
							final Object cv = f.get(null);
							if(cv == null) {
								result.append("null"); //$NON-NLS-1$
							} else if (Double.TYPE.equals(ftype) && Double.NaN == (double) cv) {
								result.append("Double.NaN"); //$NON-NLS-1$
							} else if (Float.class.equals(ftype)) {
								result.append(cv);
								result.append('f');
							} else {
								result.append(cv);
							}
						} else if(ftype.isPrimitive()) {
							// Make up a fake but incrementing value, to avoid initializing
							//   the class but allowing for switch statements to still work
							result.append(" = "); //$NON-NLS-1$
							if (Byte.TYPE.equals(ftype) || Integer.TYPE.equals(ftype) || Short.TYPE.equals(ftype)) {
								result.append('(');
								result.append(ftype.getName().toString());
								result.append(')');
								result.append(String.valueOf(constantVal++));
							} else if (Character.TYPE.equals(ftype)) {
								result.append("'\0'"); //$NON-NLS-1$
							} else {
								result.append(defaultReturnValue(f.getType()));
							}
						} else {
							result.append(" = "); //$NON-NLS-1$
							result.append(defaultReturnValue(f.getType()));
						}
					} catch (IllegalAccessException e) {
						if(log != null && log.isErrorEnabled()) {
							log.error(MessageFormat.format("Encountered exception writing field {0}.{1}",
								clazz.getName(), f.getName()), e);
						}
					}
				}

				result.append(";\n\n"); //$NON-NLS-1$
			}
		}
		return result.toString();
	}
	
	public static String writeBasicConstructorBody(Class<?> clazz, Constructor<?> constructor, ClassLoader cl, Log log) {
		
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
					Constructor<?> base = superClass.getDeclaredConstructor();
					if(Modifier.isPrivate(base.getModifiers())) {
						throw new NoSuchMethodException();
					}
					if(isPackagePrivate(base.getModifiers()) && !superClass.getPackage().getName().equals(clazz.getPackage().getName())) {
						throw new NoSuchMethodException();
					}
				} catch (NoSuchMethodException e) {
					// The parent may itself be an inner class - check for an applicable
					//   "empty" constructor
					boolean skip = false;
					if(superClass.getEnclosingClass() != null) {
						if(!Modifier.isStatic(superClass.getModifiers())) {
							try {
								superClass.getDeclaredConstructor(superClass.getEnclosingClass());
								skip = true;
							} catch(NoSuchMethodException e2) {
								// No dice
							}
						}
					}
					
					if(!skip) {
						// Pick the first callable one and make a stub call to it
						List<Constructor<?>> candidates = Arrays.stream(ctors)
							.filter(c -> {
								int mod = c.getModifiers();
								if(c.isSynthetic()) {
									return false;
								}
								if(Modifier.isPublic(mod) || Modifier.isProtected(mod)) {
									return true;
								}
								if(!(Modifier.isPublic(mod) || Modifier.isProtected(mod) || Modifier.isPrivate(mod))) {
									if(superClass.getPackage().getName().equals(clazz.getPackage().getName())) {
										return true;
									}
								}
								return false;
							})
							.collect(Collectors.toList());
						if(!candidates.isEmpty()) {
							
							// Prefer no-exception constructors
							Constructor<?> ctor = candidates.stream()
								.filter(c -> c.getExceptionTypes().length == 0)
								.findFirst()
								.orElseGet(() -> candidates.get(0));
							result.append("\t\tsuper("); //$NON-NLS-1$
							AnnotatedType[] params = ctor.getAnnotatedParameterTypes();
							// Chomp off an implicit first parameter for inner classes
							if(superClass.getEnclosingClass() != null) {
								if(!Modifier.isStatic(superClass.getModifiers())) {
									params = Arrays.stream(params).skip(1).toArray(AnnotatedType[]::new);
								}
							}
							String paramOut = Arrays.stream(params)
									.map(t -> '(' + toCastableName(clazz, t) + ')' + defaultReturnValue(t))
									.collect(Collectors.joining(", ")); //$NON-NLS-1$
							result.append(paramOut);
							result.append(");\n"); //$NON-NLS-1$
						}
					}
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
	
	public static String printMethod(Class<?> clazz, Method m) {
		StringBuilder result = new StringBuilder();
		result.append('\t');

		int mmod = m.getModifiers();

		if (Modifier.isPublic(mmod)) {
			result.append("public "); //$NON-NLS-1$
		} else if (Modifier.isProtected(mmod)) {
			result.append("protected "); //$NON-NLS-1$
		} else if (Modifier.isPrivate(mmod)) {
			result.append("private "); //$NON-NLS-1$
		}

		if (Modifier.isStatic(mmod)) {
			result.append("static "); //$NON-NLS-1$
		}

		if (Modifier.isNative(mmod)) {
			result.append("native "); //$NON-NLS-1$
		}

		if (Modifier.isAbstract(mmod) && !clazz.isInterface()) {
			result.append("abstract "); //$NON-NLS-1$
		}

		if (m.isDefault()) {
			result.append("default "); //$NON-NLS-1$
		}
		
		TypeVariable<Method>[] typeVars = m.getTypeParameters();
		if(typeVars != null && typeVars.length > 0) {
			result.append(printTypeVariables(typeVars));
			result.append(' ');
		}
		
		Map<MethodMatcher, Class<?>> returnOverrides = ClassWriterConfig.RETURN_OVERRIDES.get(clazz.getName());
		Type returnType = m.getGenericReturnType();
		if(returnOverrides != null) {
			for(Map.Entry<MethodMatcher, Class<?>> entry : returnOverrides.entrySet()) {
				if(entry.getKey().matches(m)) {
					returnType = entry.getValue();
					break;
				}
			}
		}
		
		if (Void.TYPE.equals(returnType)) {
			result.append("void "); //$NON-NLS-1$
		} else {
			result.append(toCastableName(returnType));
			result.append(' ');
		}

		result.append(m.getName());

		result.append(printParameters(m.getParameters()));

		Type[] exceps = m.getGenericExceptionTypes();
		if (exceps != null && exceps.length > 0) {
			List<String> excepNames = Arrays.stream(exceps).map(ClassWriterUtil::toCastableName)
					.collect(Collectors.toList());
			result.append(" throws "); //$NON-NLS-1$
			result.append(String.join(", ", excepNames)); //$NON-NLS-1$
		}

		if (Modifier.isNative(mmod) || Modifier.isAbstract(mmod)) {
			result.append(';');
		} else {
			result.append(" {\n"); //$NON-NLS-1$
			if (!Void.TYPE.equals(returnType)) {
				result.append("\t\treturn "); //$NON-NLS-1$
				result.append(defaultReturnValue(returnType));
				result.append(";\n"); //$NON-NLS-1$
			}
			result.append("\t}"); //$NON-NLS-1$
		}

		result.append('\n');
		
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
	
	public static boolean isPackagePrivate(int mod) {
		return !Modifier.isPublic(mod) && !Modifier.isPrivate(mod) && !Modifier.isProtected(mod);
	}
}
