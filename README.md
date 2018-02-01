# generate-domino-update-site

This tool allows the generation of a p2 site from a Domino installation folder, similar to the official [IBM Domino Update Site for Build Management](https://openntf.org/main.nsf/project.xsp?r=project/IBM%20Domino%20Update%20Site%20for%20Build%20Management). This is useful to compile code that targets a newer release of Domino than the one packaged officially.

## What It Does

The tool performs several tasks to generate its result:

1. Copies the features and plugins from the  `osgi/rcp/eclipe` and `osgi/shared/eclipse`  directories, converting unpacked folder artifacts back into Jar files
2. Generates `com.ibm.notes.java.api` and `com.ibm.notes.java.api.win32.linux` bundles using Domino's Notes.jar with a version matching today's date
3. Creates a basic site.xml file
4. Generates artifacts.jar and content.jar files using Eclipse's p2 generator

## Command Line Use

To use the tool from the command line, build the Maven project and run the jar with arguments to point to the base of your Domino installation, the target folder, and the p2 directory of an active Eclipse installation. For example:

```sh
$ cd generate-domino-update-site
$ mvn package
$ java -jar target/generate-domino-update-site-1.0.0-jar-with-dependencies.jar \
	-src "/Volumes/C/Program Files/IBM/Domino" \
	-dest ~/Desktop/UpdateSite \
	-eclipse /Applications/Eclipse.app/Contents/Eclipse
```

## Programmatic Use

To incorporate the tool into another program, create a new object of class `org.openntf.p2.domino.updatesite.tasks.GenerateUpdateSiteTask` with the same parameters as via the command line and execute its `run` method (or provide it to any executor that can take a `Runnable`).