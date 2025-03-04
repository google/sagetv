# Installer for Windows

 This folder includes the files needed to perform the build of the installers for windows

## Change the versions for an installer release
* all version information is retreived from java/sage/Version.java

## Use powershell script to build each part of the product
### From Admin Powershell
* cd C:\Projects\sagetv\installer\wix\SageTVSetup  #or whatever the path is to ""
* .\installerbuild.ps1 -A
### Notes
* run .\installerbuild.ps1 without any parameters to see the list of available options
* if building the sage.jar, miniclient.jar elsewhere then use .\installerbuild.ps1 -A -xAll -nj

## Notes:
* building of imageloader.dll and swscale.dll still need to be added to this powershell script
* after cloning the sagetv github repo you will be missing files needed for the installer build.  They can be downloaded here...
    https://github.com/OpenSageTV/sagetv-windows/releases/tag/v1.1

