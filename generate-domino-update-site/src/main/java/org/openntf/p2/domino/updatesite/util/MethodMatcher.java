package org.openntf.p2.domino.updatesite.util;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import edu.emory.mathcs.backport.java.util.Arrays;

public class MethodMatcher {
	private final String name;
	private final MethodType type;
	
	public MethodMatcher(String name, MethodType type) {
		this.name = name;
		this.type = type;
	}
	
	public boolean matches(Method m) {
		if(!m.getName().equals(name)) {
			return false;
		}
		if(!m.getReturnType().equals(type.returnType())) {
			return false;
		}
		if(!Arrays.equals(m.getParameterTypes(), type.parameterArray())) {
			return false;
		}
		return true;
	}
}
