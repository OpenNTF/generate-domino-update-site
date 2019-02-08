package org.openntf.p2.domino.updatesite;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.openntf.p2.domino.updatesite.tasks.GenerateUpdateSiteTask;

@Mojo(name="generateUpdateSite", requiresProject=false)
public class GenerateUpdateSiteMojo extends AbstractMojo {
	
	/**
	 * Source Domino program directory
	 */
	@Parameter(property="src", required=true)
	private File src;
	
	/**
	 * Destination directory
	 */
	@Parameter(property="dest", required=true)
	private File dest;
	
	/**
	 * Eclipse program root
	 */
	@Parameter(property="eclipse", required=true)
	private File eclipse;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		String dominoDir = src.getAbsolutePath();
		String destDir = dest.getAbsolutePath();
		String eclipseDir = eclipse.getAbsolutePath();
		
		new GenerateUpdateSiteTask(dominoDir, destDir, eclipseDir).run();
	}

}
