package org.openntf.p2.domino.updatesite.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.regex.Matcher;

import org.apache.maven.plugin.logging.Log;
import org.openntf.nsfodp.commons.xml.NSFODPDomUtil;
import org.openntf.p2.domino.updatesite.Messages;
import org.openntf.p2.domino.updatesite.tasks.GenerateUpdateSiteTask;
import org.tukaani.xz.XZInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.ibm.commons.util.PathUtil;
import com.ibm.commons.util.StringUtil;

public enum EclipseUtil {
	;
	
	/**
	 * This is the public Eclipse update site that best matches what's found in 9.0.1FP10 through 12.0.2.
	 */
	public static final String UPDATE_SITE_NEON = "https://download.eclipse.org/releases/neon/201612211000"; //$NON-NLS-1$
	/**
	 * This is the public Eclipse update site that best matches what's found in 14.0.0.
	 */
	public static final String UPDATE_SITE_202112 = "https://download.eclipse.org/releases/2021-12/202112081000/"; //$NON-NLS-1$
	/**
	 * The file name for the Eclipse core runtime bundle used in 2021-12.
	 */
	public static final String ECLIPSE_CORE_202112 = "org.eclipse.core.runtime_3.24.0.v20210910-0750.jar"; //$NON-NLS-1$
	
	public static String chooseEclipseUpdateSite(Collection<Path> eclipsePaths) throws IOException {
		for(Path eclipse : eclipsePaths) {
			if(Files.find(eclipse, Integer.MAX_VALUE, (path, attr) -> path.endsWith(ECLIPSE_CORE_202112)).findFirst().isPresent()) {
				return UPDATE_SITE_202112;
			}
		}
		return UPDATE_SITE_NEON;
	}
	
	/**
	 * @throws XMLException 
	 * @throws MalformedURLException 
	 * Retrieves the contents of the artifacts.jar file for the current matching Eclipse update
	 * site as a {@link Document}.
	 * 
	 * @since 3.3.0
	 */
	public static Document fetchEclipseArtifacts(Log log, String eclipseUpdateSite) throws MalformedURLException {
		String urlString = PathUtil.concat(eclipseUpdateSite, "artifacts.xml.xz", '/'); //$NON-NLS-1$
		URL artifactsUrl = new URL(urlString);
		try(InputStream is = artifactsUrl.openStream()) {
			try(XZInputStream zis = new XZInputStream(is)) {
				return NSFODPDomUtil.createDocument(zis);
			}
		} catch (IOException e) {
			if(log.isWarnEnabled()) {
				log.warn(Messages.getString("GenerateUpdateSiteTask.unableToLoadNeon"), e); //$NON-NLS-1$
			}
			return null;
		}
	}
	
	/**
	 * Looks for a source bundle matching the given artifact on the Eclipse update site.
	 * 
	 * @since 3.3.0
	 */
	public static Path downloadSource(Log log, Path artifact, Path destDir, Document artifacts, String eclipseUpdateSite) throws IOException {
		String fileName = StringUtil.toString(artifact.getFileName());
		Matcher matcher = GenerateUpdateSiteTask.BUNDLE_FILENAME_PATTERN.matcher(fileName);
		if(matcher.matches()) {
			String symbolicName = matcher.group(1) + ".source"; //$NON-NLS-1$
			String version = matcher.group(2);
			
			String query = StringUtil.format("/repository/artifacts/artifact[@classifier='osgi.bundle'][@id='{0}'][@version='{1}']", symbolicName, version); //$NON-NLS-1$
			NodeList result = NSFODPDomUtil.selectNodes(artifacts, query);
			
			if((result == null || result.getLength() == 0)) {
				// HCL sometimes re-packages same-version artifacts with newer years.
				//   Though there may be tweaks, we're fine to have a fuzzy match.
				
				int vindex = version.lastIndexOf('.');
				if(vindex > -1) {
					version = version.substring(0, vindex);
					
					// Also, special-case org.eclipse.osgi, as Domino 14 uses a non-standard
					//   patch version
					if("org.eclipse.osgi.source".equals(symbolicName) && "3.17.101".equals(version)) { //$NON-NLS-1$ //$NON-NLS-2$
						version = "3.17.100"; //$NON-NLS-1$
					}
					
					query = StringUtil.format("/repository/artifacts/artifact[@classifier='osgi.bundle'][@id='{0}'][starts-with(@version, '{1}')]", symbolicName, version); //$NON-NLS-1$
					result = NSFODPDomUtil.selectNodes(artifacts, query);
				}
			}
			
			if(result != null && result.getLength() > 0) {
				// Then we can be confident that it will exist at the expected URL
				version = result.item(0).getAttributes().getNamedItem("version").getTextContent(); //$NON-NLS-1$
				String bundleName = StringUtil.format("{0}_{1}.jar", symbolicName, version); //$NON-NLS-1$
				Path dest = destDir.resolve(bundleName);
				
				String urlString = PathUtil.concat(eclipseUpdateSite, "plugins", '/'); //$NON-NLS-1$
				urlString = PathUtil.concat(urlString, bundleName, '/');
				URL bundleUrl = new URL(urlString);
				try(InputStream is = bundleUrl.openStream()) {
					if(log.isInfoEnabled()) {
						log.info(Messages.getString("GenerateUpdateSiteTask.downloadingSourceBundle", artifact.getFileName())); //$NON-NLS-1$
					}
					Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
					return dest;
				} catch(Exception e) {
					if(log.isWarnEnabled()) {
						log.warn(Messages.getString("GenerateUpdateSiteTask.unableToDownloadSourceBundle", urlString), e); //$NON-NLS-1$
					}
				}
			}
		}
		return null;
	}
}
