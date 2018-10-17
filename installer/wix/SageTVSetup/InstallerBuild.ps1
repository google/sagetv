<#=========================================================================
This script requires the following available on your system
 - User Environment Variable named MSBuildVCTargets
    - set it to the path Visual Studio uses for this variable
    - example "C:\Program Files (x86)\MSBuild\Microsoft.Cpp\v4.0\V140"
===========================================================================#>
param(
	[parameter( ValueFromRemainingArguments = $true )]
	[string[]]$other = "",
	[switch]$S,
	[switch]$C,
	[switch]$P,
	[switch]$A,
	[switch]$b,
	[switch]$i,
	[switch]$j,
	[switch]$nb,
	[switch]$ni,
	[switch]$nj,
	[switch]$u,
	[switch]$l,
	[switch]$h
)
 
if ( $h -Or $PSBoundParameters.Count -eq 0 ) {
	Write-Host
	Write-Host "InstallerBuild.ps1,  Version 1.00"
	Write-Host "Builds each installer target and then uploads to BinTray"
	Write-Host
	Write-Host "Usage:   " -NoNewline
	Write-Host "./InstallerBuild.ps1  -A [-C, -S, -P] [-i, -b, -j] [-ni, -nb, -nj] [-u] [-l]" -ForegroundColor White
	Write-Host
	Write-Host "Where:   " -NoNewline
	Write-Host "-A          " -ForegroundColor White -NoNewline
	Write-Host " - process All targets - same as -C -S -P"
	Write-Host
	Write-Host "         -C          " -ForegroundColor White -NoNewline
	Write-Host " - process Client target"
	Write-Host
	Write-Host "         -S          " -ForegroundColor White -NoNewline
	Write-Host " - process Server target"
	Write-Host
	Write-Host "         -P          " -ForegroundColor White -NoNewline
	Write-Host " - process Placeshifter target"
	Write-Host
	Write-Host "         -i          " -ForegroundColor White -NoNewline
	Write-Host " - perform only an installer build (no native or java source build)"
	Write-Host
	Write-Host "         -b          " -ForegroundColor White -NoNewline
	Write-Host " - perform only a native source build (no java source or installer build)"
	Write-Host
	Write-Host "         -j          " -ForegroundColor White -NoNewline
	Write-Host " - perform only a java source build (no native source or installer build)"
	Write-Host
	Write-Host "         -ni          " -ForegroundColor White -NoNewline
	Write-Host "- do NOT perform an installer build"
	Write-Host
	Write-Host "         -nb          " -ForegroundColor White -NoNewline
	Write-Host "- do NOT perform a native source build"
	Write-Host
	Write-Host "         -nj          " -ForegroundColor White -NoNewline
	Write-Host "- do NOT perform a java source build"
	Write-Host
	Write-Host "         -u          " -ForegroundColor White -NoNewline
	Write-Host " - perform an upload in addition to any other parameter passed"
	Write-Host
	Write-Host "         -l          " -ForegroundColor White -NoNewline
	Write-Host " - perform lite build - redistributables download as required"
	Write-Host
	Write-Host "Note:    Return code is 0 if all targets processed successfully,"
	Write-Host "         1 if any failures."
	Write-Host
	Write-Host "Written by the SageTV Authors for building the SageTV Windows Installer"
	Exit 1
}

####################################################################################################
# log
# Write formatted log output
####################################################################################################
function log {
	param(
		[parameter( ValueFromRemainingArguments = $true )]
		[string]$logString
	)
    $calling = Get-PSCallStack | Select-Object FunctionName -Skip 1 -First 1
    Write-Host "**" $calling "*" $logString
}

####################################################################################################
# GetVersionStr
# Build the version string from it's parts
####################################################################################################
function GetVersionStr {
    $script:verMajor= GetVersion("MAJOR")
    $script:verMinor= GetVersion("MINOR")
    $script:verMicro= GetVersion("MICRO")
    #removed using the build number as we now change micro with each build
    #$script:verBuild = GetBuildNumber
    log "returning "$verMajor + "." + $verMinor + "." + $verMicro
    return $verMajor + "." + $verMinor + "." + $verMicro
}

####################################################################################################
# GetBuildNumber
# Get the build number from commit count using Git
# *** removed using the build number as we now change micro with each build
####################################################################################################
function GetBuildNumber {
    # git must be in the path
    return git rev-list HEAD --count
}

####################################################################################################
# GetVersion
# reads the version number from the Version.java file
# pass in "MAJOR" "MINOR" or "MICRO"
####################################################################################################
function GetVersion {
	param(
		[parameter( Mandatory = $true )]
		[string]$RegString
	)
    $javaVerFile = "..\..\..\java\sage\Version.java"
    $RegVer = ".*" + $RegString + "_VERSION = (\d+).*"
    $text = select-string -Path $javaVerFile -Pattern $RegVer
    $found = $text -match $RegVer
    return $Matches[1]
}

####################################################################################################
# CreateVersionHeader
# creates version.h file used by various native code projects that need the current version number
# depends on GetVersionStr to be run first
####################################################################################################
function CreateVersionHeader {
    $javaVerHeader = "..\..\..\native\include\version.h"
    $headercontent += "/* Created by InstallerBuild.ps1 script " + (Get-Date -format "dd-MMM-yyyy HH:mm") + " */`n"
    $headercontent += "#define STRINGIZE2(s) #s`n"
    $headercontent += "#define STRINGIZE(s) STRINGIZE2(s)`n"
    $headercontent += "`n"
    $headercontent += "#define VERSION_MAJOR               $verMajor`n"
    $headercontent += "#define VERSION_MINOR               $verMinor`n"
    $headercontent += "#define VERSION_REVISION            $verMicro`n"
    $headercontent += "`n"
    $headercontent += "#define VER_FILE_VERSION            VERSION_MAJOR, VERSION_MINOR, VERSION_REVISION`n"
    $headercontent += "#define VER_FILE_VERSION_STR        STRINGIZE(VERSION_MAJOR)        \`n"
    $headercontent += '                                    "." STRINGIZE(VERSION_MINOR)    \' + "`n"
    $headercontent += '                                    "." STRINGIZE(VERSION_REVISION) \' + "`n"
    $headercontent += "`n"
    $headercontent += "#define VER_PRODUCT_VERSION         VER_FILE_VERSION`n"
    $headercontent += "#define VER_PRODUCT_VERSION_STR     VER_FILE_VERSION_STR`n"
    Set-Content -Value $headercontent -Path $javaVerHeader
    log "$javaVerHeader created"
}

####################################################################################################
# CreateWIXVersionInclude
# creates SageTVVersionInclude.wxi file used by the wix installer builds
# depends on GetVersionStr to be run first
####################################################################################################
function CreateWIXVersionInclude {
    $wixVerInclude = "SageTVVersionInclude.wxi"
    $includecontent += '<?xml version="1.0" encoding="utf-8"?>' + "`n"
    $includecontent += "<!--  Created by InstallerBuild.ps1 script " + (Get-Date -format "dd-MMM-yyyy HH:mm") + " -->`n"
    $includecontent += "<Include>`n"
    $includecontent += '  <?define MajorVersion="' + $verMajor + '" ?>' + "`n"
    $includecontent += '  <?define MinorVersion="' + $verMinor + '" ?>' + "`n"
    $includecontent += '  <?define BuildVersion="' + $verMicro + '" ?>' + "`n"
    $includecontent += '  <?define VersionNumber="$(var.MajorVersion).$(var.MinorVersion).$(var.BuildVersion)" ?>' + "`n"
    $includecontent += "</Include>`n"
    Set-Content -Value $includecontent -Path $wixVerInclude
    log "$wixVerInclude created"
}

####################################################################################################
# run-Gradle
# used to run the gradlew batch file from powershell
# depends on runGradle.bat to be in the same folder as this powershell script
# pass a single argument which is the gradle task to be run
#  - sageJar
#  - miniclientJar
####################################################################################################
function run-Gradle {
	param(
		[parameter( Mandatory = $true )]
		[string]$GradleTask
	)
 
	[boolean]$rc = $true
    log "Running Gradlew for task " $GradleTask

    $GradleBat = ".\runGradle.bat"
    $Cmd = ("{0} *>&1" -f $GradleBat + " " + $GradleTask)
    $ReturnOutput = Invoke-Expression $Cmd
    $ReturnOutput | out-file ("{0}.log" -f $GradleBat)
    Write-Host $ReturnOutput
    #Remove-Item out-file ("{0}.log" -f $GradleBat)
    Remove-Item ("{0}.log" -f $GradleBat)
    log "Gradlew for task " $GradleTask "completed"
}

####################################################################################################
# Compile-Source
# used to compile the native code source files using msbuild
# pass a single argument which is the target to be compiled
#  - Release
#  - Client
#  - Service
####################################################################################################
function Compile-Source {
	param(
		[parameter( Mandatory = $true )]
		[string]$target
	)
 
	[boolean]$rc = $true

    # these change by target
    If ($target -eq "Release") {
        $targetconfig = "Release"
    }ElseIf ($target -eq "Client") {
        $targetconfig = "Client Release"
    }ElseIf ($target -eq "Service") {
        $targetconfig = "Service Release"
    }Else{
        $rc = $false
    	return $rc 
    }
    log "Compiling for Configuration = " $targetconfig
    $solution = "..\..\..\native\SageWorkspace.sln"
    $VCTargetsPath = $env:MSBuildVCTargets + "\\"
    $SolutionDir = (get-item $PSScriptRoot).parent.Parent.Parent.FullName + "\native"
    & msbuild $solution /p:Configuration=$targetconfig /p:Platform=Win32 /p:SolutionDir=$SolutionDir /p:VCTargetsPath=$VCTargetsPath | Out-Host
    log "Compile for " $targetconfig " complete"
 
	return $rc 
}

####################################################################################################
# Build-Target
# used to build the installer files using msbuild
# pass a single argument which is the target to be built
#  - Server
#  - Client
#  - Placeshifter
####################################################################################################
function Build-Target {
	param(
		[parameter( Mandatory = $true )]
		[string]$target
	)
 
	[boolean]$rc = $true

    # these change by target
    If ($target -eq "Server") {
        $targetconfig = "SetupServer"
    }ElseIf ($target -eq "Client") {
        $targetconfig = "SetupClient"
    }ElseIf ($target -eq "Placeshifter") {
        $targetconfig = "SetupPlaceshifter"
    }Else{
        $rc = $false
    	return $rc 
    }
    $InstallType = "Full"
    If ($l) {
        $InstallType = "Lite"
    }
    log "Building" $target " - " $InstallType
    & msbuild SageTVSetupBootstrapper.wixproj /p:Configuration=$targetconfig /p:InstallType=$InstallType /p:ProdVersionStr=$ProductVersion | Out-Host
    log "Build of" $target $InstallType  "complete"
 
	return $rc 
}

####################################################################################################
# Upload-Binaries
# used to upload all the base binary files to bintray
# creats a single zip file called WindowsBinaries.zip
# this is then uploaded to a new version on bintray under package WindowsInstallerSupportFiles
# ** this is run only on a bintray upload, but is always run if a bintray upload is run
####################################################################################################
function Upload-Binaries {
 
	[boolean]$rc = $true
    $source_dirname = "release"
    $source_dir = ".\$source_dirname"
    $source_subdir = "Binaries"
    $file = "WindowsBinaries"
    $ext = ".zip"
    $sourcefile = "$source_dir\$source_subdir\$file$ext"

    #create the zip with the Windows binaries
    $zipsource = $PSScriptRoot + "\..\..\..\native\Build\Release"
    $zipdestinationdir = $PSScriptRoot + "\$source_dirname\$source_subdir"
    #create the destination if not exists
    New-Item -ItemType Directory -Force -Path $zipdestinationdir
    $zipdestination = $zipdestinationdir + "\$file$ext"
    #delete the zip if it already exists
    If(Test-path $zipdestination) {Remove-item $zipdestination}
    Add-Type -assembly "system.io.compression.filesystem"
    [io.compression.zipfile]::CreateFromDirectory($zipsource, $zipdestination)

    #add Imageloader.dll to the already created zip
    $zipsource = $PSScriptRoot + "\..\..\..\buildwin\dll\ImageLoader.dll"
    $zipsourcefilename = [System.IO.Path]::GetFileName($zipsource)
    $zip = [System.IO.Compression.ZipFile]::Open($zipdestination,"Update")
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$zipsource,$zipsourcefilename,"Optimal")
    $zip.Dispose()

    #add swscale.dll to the already created zip
    $zipsource = $PSScriptRoot + "\..\..\..\buildwin\dll\swscale.dll"
    $zipsourcefilename = [System.IO.Path]::GetFileName($zipsource)
    $zip = [System.IO.Compression.ZipFile]::Open($zipdestination,"Update")
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$zipsource,$zipsourcefilename,"Optimal")
    $zip.Dispose()

    #add Sage.jar file to the already created zip
    $zipsource = $PSScriptRoot + "\..\..\..\build\release\Sage.jar"
    $zipsourcefilename = [System.IO.Path]::GetFileName($zipsource)
    $zip = [System.IO.Compression.ZipFile]::Open($zipdestination,"Update")
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$zipsource,$zipsourcefilename,"Optimal")
    $zip.Dispose()

    #add MiniClient.jar file to the already created zip
    $zipsource = $PSScriptRoot + "\..\..\..\build\minirelease\MiniClient.jar"
    $zipsourcefilename = [System.IO.Path]::GetFileName($zipsource)
    $zip = [System.IO.Compression.ZipFile]::Open($zipdestination,"Update")
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$zipsource,$zipsourcefilename,"Optimal")
    $zip.Dispose()

    #upload to bintray
    # common variables
    $bt_repository = "sagetv"
    $deployment_dir = "sagetvwinbin"
    $bt_api_url = "https://api.bintray.com"
    $package = "WindowsInstallerSupportFiles"
    $bt_password = ConvertTo-SecureString $Env:BINTRAY_API -AsPlainText -Force
    $bt_credentials = New-Object System.Management.Automation.PSCredential ($Env:BINTRAY_USER, $bt_password)
    $bt_headers = @{
        "X-Bintray-Package" = $package;
        "X-Bintray-Version" = $ProductVersion;
        "X-Bintray-Publish" = 1;
        "X-Bintray-Override" = 1;
    }
    $filename = "$deployment_dir/$ProductVersion/$file" + "_" + "$ProductVersion" + $ext
    $url = "$bt_api_url/content/opensagetv/$bt_repository/$filename"
    
    add-type @"
        using System.Net;
        using System.Security.Cryptography.X509Certificates;
        public class TrustAllCertsPolicy : ICertificatePolicy {
            public bool CheckValidationResult(
                ServicePoint srvPoint, X509Certificate certificate,
                WebRequest request, int certificateProblem) {
                return true;
            }
        }
"@
    $AllProtocols = [System.Net.SecurityProtocolType]'Ssl3,Tls,Tls11,Tls12'
    [System.Net.ServicePointManager]::SecurityProtocol = $AllProtocols
        
    [System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy
    
    $result = Invoke-WebRequest -Uri $url -Credential $bt_credentials -Method PUT -Headers $bt_headers -InFile "$sourcefile" -TimeoutSec 6000
    log "Binary Upload of" $sourcefile "to" $filename "returned" $result
 
	return $rc 
}
 
####################################################################################################
# Upload-Target
# used to upload a specific target to bintray
# this is then uploaded to a new version on bintray under package SageTVforWindows
# pass a single argument which is the target to be uploaded
#  - Server
#  - Client
#  - Placeshifter
####################################################################################################
function Upload-Target {
	param(
		[parameter( Mandatory = $true )]
		[string]$target
	)
 
	[boolean]$rc = $true

    # these change by target
    If ($target -eq "Server") {
        $source_subdir = "SetupServer"
        $file = "SageTVSetup"
    }ElseIf ($target -eq "Client") {
        $source_subdir = "SetupClient"
        $file = "SageTVClientSetup"
    }ElseIf ($target -eq "Placeshifter") {
        $source_subdir = "SetupPlaceshifter"
        $file = "SageTVPlaceshifterSetup"
    }Else{
        $rc = $false
    	return $rc 
    }

    # common variables
    $ext = ".exe"
    $bt_repository = "sagetv"
    $deployment_dir = "sagetvwin"
    $source_dir = ".\release"
    $bt_api_url = "https://api.bintray.com"
    $package = "SageTVforWindows"
    $bt_password = ConvertTo-SecureString $Env:BINTRAY_API -AsPlainText -Force
    $bt_credentials = New-Object System.Management.Automation.PSCredential ($Env:BINTRAY_USER, $bt_password)
    $bt_headers = @{
        "X-Bintray-Package" = $package;
        "X-Bintray-Version" = $ProductVersion;
        "X-Bintray-Publish" = 1;
        "X-Bintray-Override" = 1;
    }
    $sourcefile = "$source_dir\$source_subdir\$file$ext"
    $filename = "$deployment_dir/$ProductVersion/$file" + "_" + "$ProductVersion" + ".exe"
    # PUT /content/:subject/:repo/:file_path
    $url = "$bt_api_url/content/opensagetv/$bt_repository/$filename"
    $result = Invoke-WebRequest -Uri $url -Credential $bt_credentials -Method PUT -Headers $bt_headers -InFile "$sourcefile" -TimeoutSec 6000
    log "Upload of" $target "returned" $result
 
	return $rc 
}

####################################################################################################
#Startup code begins below here
####################################################################################################
 
$result = 0
# start logging the output for this run
Start-Transcript -Path ".\InstallerBuild_$(get-date -f yyyy-MM-dd_HHmmss).log"

# get the version for use throughout the process
$ProductVersion = GetVersionStr

# Create the version.h header file used by native windows code with current version
# must be run after GetVersionStr
CreateVersionHeader

# Create the version include file used by WIX for the ProductVersion
# must be run after GetVersionStr
CreateWIXVersionInclude

# determine which targets to work on
[boolean]$buildneeded = $false
$targets = @()
[boolean]$javaJar = $false
[boolean]$javaMiniJar = $false
If ($A) { #do All Targets
    $targets = @("Server","Client","Placeshifter")
    $javaJar = $true
    $javaMiniJar = $true
    $buildneeded = $true
}Else{
    If ($S) { 
        $targets += "Server"
        $javaJar = $true
        $buildneeded = $true
    }
    If ($C) { 
        $targets += "Client"
        $javaJar = $true
        $buildneeded = $true
    }
    If ($P) { 
        $targets += "Placeshifter"
        $javaMiniJar = $true
        $buildneeded = $true
    }
}
Write-Host "Processing" $targets

# perform source build
If ((!$i) -and (!$j) -and (!$nb) -and ($buildneeded)){
    if ( -not ( Compile-Source "Release" ) ) {
	    $result = 1
    }
    if ( -not ( Compile-Source "Client" ) ) {
	    $result = 1
    }
    if ( -not ( Compile-Source "Service" ) ) {
	    $result = 1
    }
}

# build java source if required
If ((!$b) -and (!$i) -and (!$nj)){
    If ($javaJar){
        run-Gradle sageJar
    }
    If ($javaMiniJar){
        run-Gradle miniclientJar
    }
}

# build all installer targets first
If ((!$b) -and (!$j) -and (!$ni)){
    foreach ( $target in $targets ) {
	    if ( -not ( Build-Target $target ) ) {
		    $result = 1
	    }
    }
}

# upload to bintray
# Upload will only occur if the -u parameter is passed
If ($u){
    #create the binary zip and upload it
    Upload-Binaries
    #upload the installers
    foreach ( $target in $targets ) {
	    if ( -not ( Upload-Target $target ) ) {
		    $result = 1
	    }
    }

}
Stop-Transcript
 
Exit $result