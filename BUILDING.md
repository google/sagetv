# Gradle Builds #

The gradle.build build file can be used to recreate the Sage.jar on Windows, Linux or Mac.

After cloning the repository, you can re-create the Sage.jar by running the **sageJar** task

Linux Users

```
./gradlew sageJar
```

Windows Users

```
gradlew.bat sageJar
```

Keep in mind the first time you run **gradlew** it will take some time has it has to download some dependencies.


## Settings up Eclipse ##
After cloning the repository, you can create the Eclipse Project Files by running the commands **cleanEclipse eclipse**

```
./gradlew cleanEclipse eclipse
```

You can open the project in eclipse and it will be fully configured as a Java Project.

You cannot recreate the Sage.jar from within eclipse, but it will compile files, etc.  When you need to rebuild the Sage.jar simple use the **sageJar** task.

### Gradle Plugin for Eclipse ###
You can find information on how to install the Gradle Plugin here
http://www.vogella.com/tutorials/EclipseGradle/article.html

YMMV since I find the Gradle Plugin to be of little value so far.
