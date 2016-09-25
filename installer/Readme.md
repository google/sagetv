# Installer for Windows

 This folder includes the files needed to perform the build of the installers for windows

  Note: this is a work in progress and I hope to add more scripting and automation over time

## Change the versions for an installer release
* SageLauncher - change file version and product version in rc file
* MiniLauncher - change file version and product version in rc file
* SageTVInclude.wxi - change product version AND installer version
* Java/sage/SageConstants - change the build number in here

## Build each part of the product

### From admin command prompt 
* run `gradlew.bat sageJar` 
* and then run `gradlew.bat miniclientJar` 

### From VS 2015 (will script this later) 

#### Set target and platform to Release + Win32
* Build SageLauncher

#### Set target and platform to Client Release + Win32
* Build SageLauncher

#### Set target and platform to Service Release + Win32
* Build ServiceControlLaunch

## Build each part of the product

### From admin command prompt 
* run `msbuild SageTVSetupBootstrapper.wixproj /p:Configuration=SetupPlaceshifter` 
* run `msbuild SageTVSetupBootstrapper.wixproj /p:Configuration=SetupClient` 
* run `msbuild SageTVSetupBootstrapper.wixproj /p:Configuration=SetupServer` 

## Notes:
I will expand on this document as time permits as you will need WIX installed as well as VS2015 and a number of environment variables to make this work.
