package org.openntf.p2.domino.updatesite;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.openntf.p2.domino.updatesite.tasks.GenerateUpdateSiteTask;

public class App {
	public static void main(String[] args) {
		try {
			Options options = new Options()
					.addRequiredOption("src", null, true, "Source Domino program directory")
					.addRequiredOption("dest", null, true, "Destination directory")
					.addRequiredOption("eclipse", null, true, "Eclipse program plugin root");
			
			CommandLine cmd = new DefaultParser().parse(options, args);
			
			String dominoDir = cmd.getOptionValue("src");
			String destDir = cmd.getOptionValue("dest");
			String eclipseDir = cmd.getOptionValue("eclipse");
			
			new GenerateUpdateSiteTask(dominoDir, destDir, eclipseDir).run();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
}
