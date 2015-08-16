# Gradle Builds #

The gradle.build build file can be used to recreate the Sage.jar and/or the MiniClient.jar on Windows, Linux or Mac.

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

To rebuild the MiniClient.jar

Linux Users

```
./gradlew miniclientJar
```

Windows Users

```
gradlew.bat miniclientJar
```



## Settings up Eclipse ##
After cloning the repository, you can create the Eclipse Project Files by running the commands **cleanEclipse eclipse**

```
./gradlew cleanEclipse eclipse
```

You can open the project in eclipse and it will be fully configured as a Java Project.  To open the project, you would create an eclipse workspace, and then use the **File -> Import -> Existing Projects** and navigate to the git source directory and it should see a **SageTV** project.

You cannot recreate the Sage.jar from within eclipse, but it will compile files, etc.  When you need to rebuild the Sage.jar simple use the **sageJar** task.

### Gradle Plugin for Eclipse ###
You can find information on how to install the Gradle Plugin here
https://github.com/spring-projects/eclipse-integration-gradle/
https://github.com/groovy/groovy-eclipse/wiki
