# Installer for Windows

 This folder includes the files needed to perform the build of the installers for windows

  Note: this is a work in progress and I hope to add more scripting and automation over time

## Change the versions for an installer release
* all version information is retreived from java/sage/Version.java

## Use powershell script to build each part of the product
### From Admin Powershell
* cd C:\Projects\Installer\sagetv\installer\wix\SageTVSetup
* .\installerbuild.ps1 -A
### Notes
* run .\installerbuild.ps1 without any parameters to see the list of available options
* to upload to bintray add the -u parameter such as ".\installerbuild.ps1 -A -u"

## Notes:
* building of imageloader.dll and swscale.dll still need to be added to this powershell script
* I will expand on this document as time permits as you will need WIX installed as well as VS2015 and a number of environment variables to make this work.

