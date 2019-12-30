# generate-domino-update-site

This tool allows the generation of a p2 site from a Domino installation folder, similar to the official [IBM Domino Update Site for Build Management](https://openntf.org/main.nsf/project.xsp?r=project/IBM%20Domino%20Update%20Site%20for%20Build%20Management), as well as the Mavenization of a p2 site generated in that way. This is useful to compile code that targets a newer release of Domino than the one packaged officially.

To use the tool from the command line, either add the OpenNTF Maven repository (https://artifactory.openntf.org/openntf) as a plugin repository to your Maven configuration or install the Maven project

## Requirements

- A Notes or Domino installation filesystem-accessible to the running computer
- Maven 3+
- Java 8+

## `generateUpdateSite` Mojo

### What It Does

The tool performs several tasks to generate its result:

1. Copies the features and plugins from the  `osgi/rcp/eclipe` and `osgi/shared/eclipse`  directories, converting unpacked folder artifacts back into Jar files
2. If pointed to a Notes installation directory, it will do the same with the `framework` directory, which contains UI-specific plugins
3. Generates `com.ibm.notes.java.api` and `com.ibm.notes.java.api.win32.linux` bundles using Domino's Notes.jar with a version matching today's date
4. Creates a basic site.xml file
5. Generates artifacts.jar and content.jar files using Eclipse's p2 generator

### Command Line Use

Execute the plugin with properties to point to the base of your Domino installation and the target folder. For example:

```sh
$ cd generate-domino-update-site
$ mvn install
$ mvn org.openntf.p2:generate-domino-update-site:generateUpdateSite \
	-Dsrc="/Volumes/C/Program Files/IBM/Domino" \
	-Ddest="/Users/someuser/Desktop/UpdateSite"
```
- `src` is the location of Domino. On Windows, this might be "C:\Program Files\IBM\Domino"

- `dest` is where you want to save it to. For the Extension Library, this was historically "C:\UpdateSite"

### Programmatic Use

To incorporate the tool into another program, create a new object of class `org.openntf.p2.domino.updatesite.tasks.GenerateUpdateSiteTask` with the same parameters as via the command line and execute its `run` method (or provide it to any executor that can take a `Runnable`).

## `mavenizeBundles` Mojo

### What It Does

This tool processes a p2 site (or a bundles directory directly) and installs the contents into the local Maven repository. It derives its Maven information from the configuration bundle's manifest:

- The `groupId` can be set with the "groupId" parameter, and defaults to "com.ibm.xsp"
- The `artifactId` is the bundle's symbolic name
- The `version` is the bundle version
- The `organization` is the `Bundle-Vendor` value, if present
- The `dependencies` are other created bundles based on the `Require-Bundle` value

Additionally, this installs any embedded JARs as attached entities with the `classifier` matching their base name. For example, Notes.jar from the 9.0.1 release can be accessed like:

```xml
		<dependency>
			<groupId>com.ibm.xsp</groupId>
			<artifactId>com.ibm.notes.java.api.win32.linux</artifactId>
			<version>9.0.1.20140404-1000</version>
			<classifier>Notes</classifier>
			<scope>provided</scope>
		</dependency>
```

### Command Line Use

Execute the plugin with properties to point to the base of your Domino installation and the target folder. For example:

```sh
$ mvn org.openntf.p2:generate-domino-update-site:mavenizeBundles \
	-Dsrc="/Users/someuser/Desktop/UpdateSite" \
	-DgroupId=some.group.id # Optional
```

- `src` is the location of the Update Site
- `groupId` is an optional group ID to use for the installed bundles. It defaults to "com.ibm.xsp"

