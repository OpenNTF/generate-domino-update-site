# generate-domino-update-site

This tool allows the generation of a p2 site from a Domino installation folder, similar to the official [IBM Domino Update Site for Build Management](https://openntf.org/main.nsf/project.xsp?r=project/IBM%20Domino%20Update%20Site%20for%20Build%20Management). This is useful to compile code that targets a newer release of Domino than the one packaged officially.

## Requirements

- A Notes or Domino installation filesystem-accessible to the running computer
- Maven 3+
- Java 8+

## What It Does

The tool performs several tasks to generate its result:

1. Copies the features and plugins from the  `osgi/rcp/eclipe` and `osgi/shared/eclipse`  directories, converting unpacked folder artifacts back into Jar files
2. If pointed to a Notes installation directory, it will do the same with the `framework` directory, which contains UI-specific plugins
3. Generates `com.ibm.notes.java.api` and `com.ibm.notes.java.api.win32.linux` bundles using Domino's Notes.jar with a version matching today's date
4. Creates a basic site.xml file
5. Generates artifacts.jar and content.jar files using Eclipse's p2 generator

## Command Line Use

To use the tool from the command line, either add the OpenNTF Maven repository (https://artifactory.openntf.org/openntf) as a plugin repository to your Maven configuration or install the Maven project. Then, execute the plugin with properties to point to the base of your Domino installation and the target folder. For example:

```sh
$ cd generate-domino-update-site
$ mvn install
$ mvn org.openntf.p2:generate-domino-update-site:generateUpdateSite \
	-Dsrc="/Volumes/C/Program Files/IBM/Domino" \
	-Ddest="/Users/someuser/Desktop/UpdateSite"
```
- `src` is the location of Domino. On Windows, this might be "C:\Program Files\IBM\Domino"

- `dest` is where you want to save it to. For the Extension Library, this was historically "C:\UpdateSite"

## Programmatic Use

To incorporate the tool into another program, create a new object of class `org.openntf.p2.domino.updatesite.tasks.GenerateUpdateSiteTask` with the same parameters as via the command line and execute its `run` method (or provide it to any executor that can take a `Runnable`).