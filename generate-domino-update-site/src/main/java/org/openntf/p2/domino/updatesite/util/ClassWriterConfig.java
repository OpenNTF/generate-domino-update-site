package org.openntf.p2.domino.updatesite.util;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public enum ClassWriterConfig {
	;
	
	public static final Set<String> SKIP_SOURCE_BUNDLES;
	/**
	 * Names of classes known to contain inner classes that are complicated
	 * but unnecessary.
	 */
	public static final Set<String> SKIP_INNER_CLASSES;
	/**
	 * Names of bundles that should have org.eclipse.equinox.registry added
	 * as an explicit dependency.
	 */
	public static final Map<String, Collection<String>> ADD_ADDITIONAL_BUNDLES;
	/**
	 * Classes in source bundles that we don't need and would impose odd restrictions.
	 */
	public static final Set<String> SKIP_SOURCE_CLASSES;
	/**
	 * Explicit code to add to some classes, such as those originally compiled against
	 * an older version of Servlet
	 */
	public static final Map<String, String> RAW_CLASS_BODY_ADDITIONS;
	/**
	 * Packages to skip copying outright, as they add complexity but are not
	 * needed
	 */
	public static final Set<String> SKIP_PACKAGES;
	/**
	 * Classes to skip copying outright, as they add complexity but are not
	 * needed
	 */
	public static final Set<String> SKIP_CLASSES;
	/**
	 * Classes that should be marked public even if they aren't currently
	 */
	public static final Set<String> PUBLIC_CLASSES;
	/**
	 * A table of string constants for classes where initializing causes trouble
	 */
	public static final Map<String, Map<String, String>> KNOWN_STRING_CONSTANTS;
	/**
	 * A table of methods on classes where the return type needs to be overridden,
	 * e.g. the ancient MultiKeyMap.remove method that conflicts with Map
	 */
	public static final Map<String, Map<MethodMatcher, Class<?>>> RETURN_OVERRIDES;
	/**
	 * A table of fields to skip generating
	 */
	public static final Map<String, Collection<String>> SKIP_FIELDS;
	static {
		SKIP_SOURCE_BUNDLES = new HashSet<>();
		// These use downstream dependencies
		SKIP_SOURCE_BUNDLES.add("org.eclipse.osgi"); //$NON-NLS-1$
		SKIP_SOURCE_BUNDLES.add("org.eclipse.osgi.services"); //$NON-NLS-1$
		
		SKIP_INNER_CLASSES = new HashSet<>();
		SKIP_INNER_CLASSES.add("javax.servlet.jsp.el.ImplicitObjectELResolver"); //$NON-NLS-1$
		SKIP_INNER_CLASSES.add("org.apache.jasper.compiler.Validator"); //$NON-NLS-1$
		SKIP_INNER_CLASSES.add("org.apache.jasper.runtime.PerThreadTagHandlerPool"); //$NON-NLS-1$
		SKIP_INNER_CLASSES.add("org.apache.jasper.runtime.ProtectedFunctionMapper"); //$NON-NLS-1$
		SKIP_INNER_CLASSES.add("org.apache.jasper.compiler.ELParser"); //$NON-NLS-1$
		SKIP_INNER_CLASSES.add("org.apache.jasper.compiler.SmapUtil"); //$NON-NLS-1$
		SKIP_INNER_CLASSES.add("com.ibm.xsp.http.io.FileCleaningTracker"); //$NON-NLS-1$
		SKIP_INNER_CLASSES.add("com.ibm.xsp.debug.DebugMemory"); //$NON-NLS-1$
		
		ADD_ADDITIONAL_BUNDLES = new HashMap<>();
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.commons", Arrays.asList("org.eclipse.equinox.registry")); //$NON-NLS-1$ //$NON-NLS-2$
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.pvc.sharedbundle", Arrays.asList( //$NON-NLS-1$
			"org.eclipse.equinox.registry", //$NON-NLS-1$
			"org.eclipse.equinox.preferences", //$NON-NLS-1$
			"org.eclipse.equinox.common" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("org.apache.commons.el", Arrays.asList( //$NON-NLS-1$
			"com.ibm.pvc.servlet", //$NON-NLS-1$
			"com.ibm.pvc.servlet.jsp" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.rcp.webcontainer.utils", Arrays.asList( //$NON-NLS-1$
			"org.eclipse.equinox.registry" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.pvc.webhttpservice", Arrays.asList( //$NON-NLS-1$
			"com.ibm.pvc.servlet" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.pvc.webcontainer", Arrays.asList( //$NON-NLS-1$
			"com.ibm.pvc.servlet", //$NON-NLS-1$
			"org.apache.jasper" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.designer.lib.jsf", Arrays.asList( //$NON-NLS-1$
			"com.ibm.pvc.servlet", //$NON-NLS-1$
			"com.ibm.pvc.servlet.jsp" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.domino.napi.impl", Arrays.asList( //$NON-NLS-1$
			"com.ibm.notes.java.api.win32.linux", //$NON-NLS-1$
			"org.eclipse.osgi" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.domino.xsp.bootstrap", Arrays.asList( //$NON-NLS-1$
			"com.ibm.pvc.servlet", //$NON-NLS-1$
			"com.ibm.notes.java.api.win32.linux", //$NON-NLS-1$
			"org.eclipse.osgi" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.domino.xsp.adapter", Arrays.asList( //$NON-NLS-1$
			"com.ibm.pvc.servlet" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.xsp.http.bootstrap", Arrays.asList( //$NON-NLS-1$
			"com.ibm.notes.java.api.win32.linux", //$NON-NLS-1$
			"com.ibm.notes.java.api" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.domino.xsp.bridge.http", Arrays.asList( //$NON-NLS-1$
			"com.ibm.pvc.servlet", //$NON-NLS-1$
			"com.ibm.notes.java.api.win32.linux" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.domino.xsp.adapter.osgi", Arrays.asList( //$NON-NLS-1$
			"com.ibm.notes.java.api.win32.linux", //$NON-NLS-1$
			"com.ibm.domino.napi.impl" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.domino.osgi.core", Arrays.asList( //$NON-NLS-1$
			"com.ibm.notes.java.api.win32.linux", //$NON-NLS-1$
			"com.ibm.domino.napi.impl", //$NON-NLS-1$
			"org.eclipse.equinox.registry" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.commons.jdbc", Arrays.asList( //$NON-NLS-1$
			"org.eclipse.osgi" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.jscript", Arrays.asList( //$NON-NLS-1$
			"org.eclipse.osgi" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.designer.runtime", Arrays.asList( //$NON-NLS-1$
			"com.ibm.pvc.servlet", //$NON-NLS-1$
			"org.eclipse.osgi", //$NON-NLS-1$
			"org.eclipse.core.runtime" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.xsp.core", Arrays.asList( //$NON-NLS-1$
			"com.ibm.pvc.servlet", //$NON-NLS-1$
			"org.eclipse.osgi" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.xsp.extsn", Arrays.asList( //$NON-NLS-1$
			"com.ibm.pvc.servlet", //$NON-NLS-1$
			"com.ibm.designer.lib.jsf" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.xsp.designer", Arrays.asList( //$NON-NLS-1$
			"com.ibm.xsp.core", //$NON-NLS-1$
			"com.ibm.pvc.servlet" //$NON-NLS-1$
		));
		ADD_ADDITIONAL_BUNDLES.put("com.ibm.xsp.domino", Arrays.asList( //$NON-NLS-1$
			"com.ibm.pvc.servlet", //$NON-NLS-1$
			"com.ibm.designer.lib.jsf", //$NON-NLS-1$
			"com.ibm.jscript", //$NON-NLS-1$
			"org.eclipse.osgi", //$NON-NLS-1$
			"com.ibm.xsp.http.bootstrap" //$NON-NLS-1$
		));
		
		SKIP_SOURCE_CLASSES = new HashSet<>();
		SKIP_SOURCE_CLASSES.add("org.apache.commons.logging.impl.Log4JLogger"); //$NON-NLS-1$
		SKIP_SOURCE_CLASSES.add("org.apache.commons.logging.impl.LogKitLogger"); //$NON-NLS-1$
		SKIP_SOURCE_CLASSES.add("org.apache.commons.logging.impl.AvalonLogger"); //$NON-NLS-1$
		
		SKIP_PACKAGES = new HashSet<>();
		SKIP_PACKAGES.add("com.ibm.osg.util"); //$NON-NLS-1$
		SKIP_PACKAGES.add("org.apache.commons.logging.impl"); //$NON-NLS-1$
		SKIP_PACKAGES.add("org.eclipse.equinox.http.registry.internal"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.document"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.optimizedtag"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.optimizedtag.impl"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.resource"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.utils"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.visitor"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.visitor.configuration"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.visitor.generator"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.visitor.smap"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.visitor.tagfiledep"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.visitor.tagfilescan"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.visitor.validator"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.translator.visitor.xml"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.ibm.ws.jsp.inmemory.generator"); //$NON-NLS-1$
		SKIP_PACKAGES.add("com.hcl.domino.module.nsf"); //$NON-NLS-1$
		
		SKIP_CLASSES = new HashSet<>();
		SKIP_CLASSES.add("lotus.domino.AgentLoader"); //$NON-NLS-1$
		
		RAW_CLASS_BODY_ADDITIONS = new HashMap<>();
		RAW_CLASS_BODY_ADDITIONS.put("org.apache.jasper.runtime.JspContextWrapper", "public javax.el.ELContext getELContext() { return null; }"); //$NON-NLS-1$ //$NON-NLS-2$
		RAW_CLASS_BODY_ADDITIONS.put("org.apache.jasper.runtime.JspFactoryImpl", "public javax.servlet.jsp.JspApplicationContext getJspApplicationContext(javax.servlet.ServletContext paramServletContext) { return null; }"); //$NON-NLS-1$ //$NON-NLS-2$
		RAW_CLASS_BODY_ADDITIONS.put("org.apache.jasper.compiler.TagLibraryInfoImpl", "public javax.servlet.jsp.tagext.TagLibraryInfo[] getTagLibraryInfos() { return null; }"); //$NON-NLS-1$ //$NON-NLS-2$
		RAW_CLASS_BODY_ADDITIONS.put("org.apache.jasper.runtime.PageContextImpl", "public javax.el.ELContext getELContext() { return null; }"); //$NON-NLS-1$ //$NON-NLS-2$
		RAW_CLASS_BODY_ADDITIONS.put("org.apache.jasper.compiler.ImplicitTagLibraryInfo", "public javax.servlet.jsp.tagext.TagLibraryInfo[] getTagLibraryInfos() { return null; }"); //$NON-NLS-1$ //$NON-NLS-2$
		RAW_CLASS_BODY_ADDITIONS.put("org.apache.jasper.servlet.JspCServletContext", "public String getContextPath() { return null; }"); //$NON-NLS-1$ //$NON-NLS-2$
		
		PUBLIC_CLASSES = new HashSet<>();
		PUBLIC_CLASSES.add("org.apache.jasper.compiler.ELNode"); //$NON-NLS-1$
		PUBLIC_CLASSES.add("com.ibm.commons.vfs.VFS$FileEntry"); //$NON-NLS-1$
		PUBLIC_CLASSES.add("com.ibm.commons.vfs.VFS$FolderEntry"); //$NON-NLS-1$
		PUBLIC_CLASSES.add("com.ibm.xsp.webapp.resources.AbstractResourceProvider$AbstractResource"); //$NON-NLS-1$
		PUBLIC_CLASSES.add("com.ibm.xsp.model.AbstractDataSource$RuntimeProperties"); //$NON-NLS-1$
		PUBLIC_CLASSES.add("com.ibm.designer.runtime.domino.adapter.ComponentModule$ServletInvoker"); //$NON-NLS-1$
		
		// TODO consider removing this and instead using an alternate bytecode reader to load string constants
		KNOWN_STRING_CONSTANTS = new HashMap<>();
		{
			Map<String, String> map = KNOWN_STRING_CONSTANTS.compute("com.ibm.ws.http.HttpTransport", (k,v) -> new HashMap<>()); //$NON-NLS-1$
			map.put("HOST", "Host"); //$NON-NLS-1$ //$NON-NLS-2$
			map.put("HTTP", "http"); //$NON-NLS-1$ //$NON-NLS-2$
			map.put("HTTPS", "https"); //$NON-NLS-1$ //$NON-NLS-2$
			map.put("PORT", "Port"); //$NON-NLS-1$ //$NON-NLS-2$
			map.put("MAX_CONNECT_BACKLOG", "MaxConnectBacklog"); //$NON-NLS-1$ //$NON-NLS-2$
			map.put("TCP_NO_DELAY", "TcpNoDelay"); //$NON-NLS-1$ //$NON-NLS-2$
			map.put("KEEP_ALIVE_ENABLE", "KeepAliveEnabled"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		{
			Map<String, String> map = KNOWN_STRING_CONSTANTS.compute("com.ibm.designer.runtime.domino.bootstrap.BootstrapEnvironment", (k,v) -> new HashMap<>()); //$NON-NLS-1$
			map.put("DIR_SHARED", "shared"); //$NON-NLS-1$ //$NON-NLS-2$
			map.put("DIR_NSF", "nsf"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		{
			Map<String, String> map = KNOWN_STRING_CONSTANTS.compute("com.ibm.domino.xsp.bridge.http.servlet.XspCmdHttpServletResponse", (k,v) -> new HashMap<>()); //$NON-NLS-1$
			map.put("CONTENT_LENGTH", "CONTENT_LENGTH"); //$NON-NLS-1$ //$NON-NLS-2$
			map.put("CONTENT_TYPE", "CONTENT_TYPE"); //$NON-NLS-1$ //$NON-NLS-2$
			map.put("HTTP_RESPONSE", "HTTP_RESPONSE"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		RETURN_OVERRIDES = new HashMap<>();
		RETURN_OVERRIDES.put("org.apache.commons.collections.map.MultiKeyMap", Collections.singletonMap(new MethodMatcher("remove", MethodType.methodType(Object.class, Object.class, Object.class)), boolean.class)); //$NON-NLS-1$ //$NON-NLS-2$
		RETURN_OVERRIDES.put("org.apache.commons.collections.MultiHashMap", Collections.singletonMap(new MethodMatcher("remove", MethodType.methodType(Object.class, Object.class, Object.class)), boolean.class)); //$NON-NLS-1$ //$NON-NLS-2$
		RETURN_OVERRIDES.put("org.apache.commons.collections.MultiMap", Collections.singletonMap(new MethodMatcher("remove", MethodType.methodType(Object.class, Object.class, Object.class)), boolean.class)); //$NON-NLS-1$ //$NON-NLS-2$
		RETURN_OVERRIDES.put("org.apache.commons.collections.map.MultiValueMap", Collections.singletonMap(new MethodMatcher("remove", MethodType.methodType(Object.class, Object.class, Object.class)), boolean.class)); //$NON-NLS-1$ //$NON-NLS-2$
		RETURN_OVERRIDES.put("org.apache.bcel.verifier.exc.AssertionViolatedException", Collections.singletonMap(new MethodMatcher("getStackTrace", MethodType.methodType(String.class)), StackTraceElement[].class)); //$NON-NLS-1$ //$NON-NLS-2$
		
		SKIP_FIELDS = new HashMap<>();
		SKIP_FIELDS.put("org.apache.xalan.xsltc.compiler.XPathParser", Collections.singleton("action_obj")); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
