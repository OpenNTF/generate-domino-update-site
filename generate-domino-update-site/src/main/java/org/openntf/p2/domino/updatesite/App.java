/**
 * Copyright © 2018-2019 Jesse Gallagher
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
