@REM Store args...
set _configName=%1%
set _projectName=%2%

@REM Remove quotes from remaining args...
set _projectDir=###%3%###
set _projectDir=%_projectDir:"###=%
set _projectDir=%_projectDir:###"=%
set _projectDir=%_projectDir:###=%

set _targetDir=%_projectDir%source\SageTV
@echo target %_targetDir%

@REM set the location for extra support files
set _supportDir=%_projectDir%..\..\SageSupportFiles

@REM do common processing needed for all configurations
echo Preparing pre-build for %_configName%
@REM Start with a clean source folder
rd /S /Q "%_targetDir%"

@REM Create the proper source folder for this configuration
if not exist "%_targetDir%" mkdir "%_targetDir%"

if %_configName% == SetupServer goto ProcessServer
if %_configName% == SetupClient goto ProcessClient
if %_configName% == SetupPlaceshifter goto ProcessPlaceshifter
echo No pre-build processing required.
exit 0

@REM Start pre-build processing for Server
:ProcessServer
@REM Start pre-build processing for Client
:ProcessClient
echo Starting pre-build processing for %_configName%

@REM Copy the SendMessage.exe file
xcopy "%_projectDir%..\..\..\buildwin\prebuilt\release\SendMessage.exe" "%_targetDir%\SageTV\" /q
if errorlevel 1 goto CopyFailure

@REM Copy the remote codes folders
xcopy "%_projectDir%..\..\..\buildwin\prebuilt\release\RemoteCodes" "%_targetDir%\Common\RemoteCodes" /i /q /e
if errorlevel 1 goto CopyFailure

@REM Copy the XBMC file
xcopy "%_projectDir%..\..\..\stvs\XBMC\XBMCBase.xml" "%_targetDir%\SageTV\" /q
if errorlevel 1 goto CopyFailure

@REM Copy the thirdparty license
xcopy "%_projectDir%..\..\..\third_party\ThirdPartyLicense.txt" "%_targetDir%\SageTV\" /q
if errorlevel 1 goto CopyFailure

@REM Copy the thirdparty JARs
xcopy "%_projectDir%..\..\..\third_party\Apache\commons-jxpath-1.1.jar" "%_targetDir%\SageTV\JARs\" /q
if errorlevel 1 goto CopyFailure
xcopy "%_projectDir%..\..\..\third_party\JCIFS\jcifs-1.1.6.jar" "%_targetDir%\SageTV\JARs\" /q
if errorlevel 1 goto CopyFailure
xcopy "%_projectDir%..\..\..\third_party\Oracle\vecmath.jar" "%_targetDir%\SageTV\JARs\" /q
if errorlevel 1 goto CopyFailure
xcopy "%_projectDir%..\..\..\third_party\UPnPLib\sbbi-upnplib-1.0.3.jar" "%_targetDir%\SageTV\JARs\" /q
if errorlevel 1 goto CopyFailure

@REM Copy the fonts folder
xcopy "%_projectDir%..\..\..\third_party\DejaVuFonts\*.ttf" "%_targetDir%\SageTV\fonts" /i /q /e
if errorlevel 1 goto CopyFailure

@REM Copy the thirdparty Hauppauge
xcopy "%_projectDir%..\..\..\third_party\Hauppauge\HCWIRBlaster.dll" "%_targetDir%\Common\" /q
if errorlevel 1 goto CopyFailure

@REM Copy the thirdparty JDIC components
xcopy "%_projectDir%..\..\..\third_party\JDIC" "%_targetDir%\SageTV" /i /q /e
if errorlevel 1 goto CopyFailure

@REM Copy the thirdparty Technotrend component
xcopy "%_projectDir%..\..\..\third_party\technotrend\ttBdaDrvApi_Dll.dll" "%_targetDir%\SageTV\" /q
if errorlevel 1 goto CopyFailure

@REM Copy the Support Binaries - these are ones where I could not find source
xcopy "%_supportDir%\SupportBinaries" "%_targetDir%" /i /q /e
if errorlevel 1 goto CopyFailure

@REM Copy the elf build files - these for now are stored in this location from a ZIP
xcopy "%_supportDir%\elf" "%_targetDir%\SageTV" /i /q /e
if errorlevel 1 goto CopyFailure

@REM generate Heat output for the above files
heat dir "%_projectDir%\source\SageTV\SageTV" -sw -gg -dr INSTALLFOLDER -cg SageTVSourceComponents -var var.SourceSageTVSource -sfrag -out "%_projectDir%\source-dir-sagetv.wxs"
heat dir "%_projectDir%\source\SageTV\Common" -sw -gg -dr INSTALLFOLDER -cg CommonSourceComponents -var var.SourceCommonSource -sfrag -out "%_projectDir%\source-dir-common.wxs"
@REM generate Heat output for the STVs
heat dir "%_projectDir%\..\..\..\stvs\SageTV7" -gg -dr STVs -cg STVsComponents -var var.SourceSTV -sfrag -out "%_projectDir%\stvs-dir-sagetv7.wxs"

goto PreBuildSuccess

@REM Start pre-build processing for Placeshifter
:ProcessPlaceshifter
echo Starting pre-build processing for %_configName%

@REM Copy the Support Binaries - these are ones where I could not find source
xcopy "%_supportDir%\SupportBinaries\SageTV\ImageLoader.dll" "%_targetDir%\Placeshifter\" /q
xcopy "%_supportDir%\SupportBinaries\SageTV\pthreadGC2.dll" "%_targetDir%\Placeshifter\" /q
xcopy "%_supportDir%\SupportBinaries\SageTV\swscale.dll" "%_targetDir%\Placeshifter\" /q
if errorlevel 1 goto CopyFailure

@REM Copy the thirdparty license
xcopy "%_projectDir%..\..\..\third_party\ThirdPartyLicense.txt" "%_targetDir%\Placeshifter\" /q
if errorlevel 1 goto CopyFailure

@REM Copy the elf SageTVPlayer files- these for now are stored in this location from a ZIP
xcopy "%_supportDir%\elf\SageTVPlayer.exe" "%_targetDir%\Placeshifter\" /q
if errorlevel 1 goto CopyFailure

@REM generate Heat output for the above files
heat dir "%_projectDir%\source\SageTV\Placeshifter" -sw -gg -dr INSTALLFOLDER -cg PlaceshifterSourceComponents -var var.SourcePlaceshifterSource -sfrag -out "%_projectDir%\source-dir-placeshifter.wxs"

goto PreBuildSuccess

@REM Failure labels
:CopyFailure
echo Pre-build processing for %_projectName% %_configName% FAILED: Failed to copy file(s) to common source directory!
@REM exit 1

@REM Pre-build success
:PreBuildSuccess
echo Pre-build processing for %_projectName% %_configName% completed OK.
@REM exit 0