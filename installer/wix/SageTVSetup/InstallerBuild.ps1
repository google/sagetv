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
	[switch]$xAll,
	[switch]$x64,
	[switch]$x86,
	[switch]$b,
	[switch]$i,
	[switch]$j,
	[switch]$nb,
	[switch]$ni,
	[switch]$nj,
	[switch]$u,
	[switch]$Java,
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
	Write-Host "         -xAll       " -ForegroundColor White -NoNewline
	Write-Host " - process target as x86 and x64 platform (both)"
	Write-Host
	Write-Host "         -x64        " -ForegroundColor White -NoNewline
	Write-Host " - process target as x64 platform"
	Write-Host
	Write-Host "         -x86        " -ForegroundColor White -NoNewline
	Write-Host " - process target as x86 platform - this is the default if not set"
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
	Write-Host "         -Java       " -ForegroundColor White -NoNewline
	Write-Host " - include Java in the build - otherwise java redistributable downloads as required"
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

    foreach ( $platform in $platforms ) {

        $CompilePlatform = "Win32"
        If ($platform -eq "x64") {
            $CompilePlatform = "x64"
        }
   
        log "Compiling for Configuration = " $targetconfig " - " $CompilePlatform
        $solution = "..\..\..\native\SageWorkspace.sln"
        $VCTargetsPath = $env:MSBuildVCTargets + "\\"
        $SolutionDir = (get-item $PSScriptRoot).parent.Parent.Parent.FullName + "\native\"
        & msbuild $solution /p:Configuration=$targetconfig /p:Platform=$CompilePlatform /p:SolutionDir=$SolutionDir /p:VCTargetsPath=$VCTargetsPath | Out-Host
        log "Compile for " $targetconfig " complete"
    } 
    return $rc 
}

####################################################################################################
# Build-Target
# used to build the installer files using msbuild
# pass a single argument which is the target to be built
#  - Server
#  - Server64
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
    $InstallType = ""
    If ($Java) {
        $InstallType = "_Java"
    }

    foreach ( $platform in $platforms ) {

        $InstallPlatform = "x86"
        If ($platform -eq "x64") {
            $InstallPlatform = "x64"
        }
        log "Building" $target " - " $InstallType " - " $InstallPlatform " - " $ProductVersion
        & msbuild SageTVSetupBootstrapper.wixproj /p:Configuration=$targetconfig /p:Platform=$InstallPlatform /p:InstallType=$InstallType /p:ProdVersionStr=$ProductVersion | Out-Host
        log "Build of" $target $InstallType $InstallPlatform $ProductVersion "complete"
    } 
	return $rc 
}

####################################################################################################
# PrepareWebRequest
# used to bypass cert errors for webrequest
# needed prior to webrequest ONCE per session
# seems bintray AND github need this
####################################################################################################
function PrepareWebRequest {
    Write-Host "Preparing WebRequest"

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
    $zipsource = $PSScriptRoot + "\..\..\..\native\Build"
    $zipdestinationdir = $PSScriptRoot + "\$source_dirname\$source_subdir"
    #create the destination if not exists
    New-Item -ItemType Directory -Force -Path $zipdestinationdir
    $zipdestination = $zipdestinationdir + "\$file$ext"
    #delete the zip if it already exists
    If(Test-path $zipdestination) {Remove-item $zipdestination}
    Add-Type -assembly "system.io.compression.filesystem"
    [io.compression.zipfile]::CreateFromDirectory($zipsource, $zipdestination)

    #add Win32 binaries to the already created zip
    $zipsource = $PSScriptRoot + "\..\..\..\buildwin\Win32"
    $zip = [System.IO.Compression.ZipFile]::Open($zipdestination,"Update")
    Get-ChildItem $zipsource | ForEach-Object {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, "Win32\" + $_.Name, "Optimal")
    }
    $zip.Dispose()
    
    #add x64 binaries to the already created zip
    $zipsource = $PSScriptRoot + "\..\..\..\buildwin\x64"
    $zip = [System.IO.Compression.ZipFile]::Open($zipdestination,"Update")
    Get-ChildItem $zipsource | ForEach-Object {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, "x64\" + $_.Name, "Optimal")
    }
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

    return $rc 

    #$sourcefile = ".\release\Binaries\testfile.zip"
    If ($uploadType -eq $upGithub) {
        #upload to github
        $a_id = FindAsset $rel_id $sourcefile
        if ($a_id -ne 0) {
          Write-Host "Asset $sourcefile was already uploaded, id: $a_id"
        } else {
          UploadAsset $rel_id $sourcefile
          log "Binary Upload of" $sourcefile "to Github completed"
        }
    } Else {

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
    
        $result = Invoke-WebRequest -Uri $url -Credential $bt_credentials -Method PUT -Headers $bt_headers -InFile "$sourcefile" -TimeoutSec 6000
        log "Binary Upload of" $sourcefile "to" $filename "returned" $result
    }
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

    foreach ( $platform in $platforms ) {

            $InstallPlatform = "x86"
	    If ($platform -eq "x64") {
	        $InstallPlatform = "x64"
	    }

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
            # append the platform and product version as needed
            $source_subdir = $source_subdir + $InstallPlatform
	    $file = $file + $InstallPlatform + "_" + $ProductVersion

	    # common variables
	    $ext = ".exe"
	    $source_dir = ".\release"
	    $sourcefile = "$source_dir\$source_subdir\$file$ext"

	    If ($uploadType -eq $upGithub) {
		#upload to github
		$a_id = FindAsset $rel_id $sourcefile
		if ($a_id -ne 0) {
		  Write-Host "Asset $sourcefile was already uploaded, id: $a_id"
		} else {
		  UploadAsset $rel_id $sourcefile
		  log "Target Upload of" $sourcefile "to Github completed"
		}
	    } Else {
		# bintray common variables
		$bt_repository = "sagetv"
		$deployment_dir = "sagetvwin"
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
		$filename = "$deployment_dir/$ProductVersion/$file$ext"
		# PUT /content/:subject/:repo/:file_path
		$url = "$bt_api_url/content/opensagetv/$bt_repository/$filename"
		$result = Invoke-WebRequest -Uri $url -Credential $bt_credentials -Method PUT -Headers $bt_headers -InFile "$sourcefile" -TimeoutSec 6000
		log "Upload of" $target$InstallPlatform "returned" $result
	    }
    } 

    
 
    return $rc 
}

####################################################################################################
# FindRelease
# used to determine if the release already exists on GitHub
#  - tag_name - the version number
####################################################################################################
function FindRelease([string]$tag_name)
{
  $rel_id = 0
  $rel = Invoke-WebRequest -Headers $auth -Uri https://api.github.com/repos/$user/$project/releases
  if ($rel -eq $null) {
    return 0
  }
  $rel_js = ConvertFrom-Json $rel.Content
  $rel_js | where {$_.tag_name -eq $tag_name} | foreach {
    $rel_id = $_.id
    Write-Host ("Release found, upload_url=" + $_.upload_url)
  }
  return $rel_id
}

####################################################################################################
# CreateRelease
# used to create a new release on GitHub
#  - tag - the version number
#  - name - name of the version
#  - descr - description of the version
####################################################################################################
function CreateRelease([string]$tag,[string]$name,[string]$descr)
{
  #  "target_commitish"="60b20ba"; `
  #  "target_commitish"="master"; `
  #  "target_commitish"="daily"; `

  #$tag_name = ("v"+$build.Substring(0,2)+"."+$build.Substring(2,2)+"."+$build.Substring(4))
  #$tag_name = $tag

  #Write-Host "$tag $name $descr"

  $rel_arg = @{ `
    "tag_name"=$tag; `
    "name"=$name; `
    "body"=$descr; `
    "draft"=$FALSE; `
    "prerelease"=$FALSE
  }

  $rel_arg_js = ConvertTo-Json $rel_arg
  #$rel_arg_js

  $rel = Invoke-WebRequest -Headers $auth -Method POST -Body $rel_arg_js -Uri https://api.github.com/repos/$user/$project/releases
  if ($rel -ne $null)
  {
    $rel_js = ConvertFrom-Json $rel.Content
    return $rel_js.id
  }

  # $host.SetShouldExit(101)
  exit
}

####################################################################################################
# FindAsset
# used to determine if the asset (file) already exists on GitHub
#  - rel_id - the version number
#  - fullpath - path to the asset
####################################################################################################
function FindAsset([int]$rel_id,[string]$fullpath)
{
  $fname = Split-Path $fullpath -Leaf
  $a_id = 0
  $rel = Invoke-WebRequest -Headers $auth -Uri https://api.github.com/repos/$user/$project/releases/$rel_id/assets
  if ($rel -eq $null) {
    return 0
  }
  $rel_js = ConvertFrom-Json $rel.Content
  $rel_js | where {$_.name -eq $fname} | foreach {
    $a_id = $_.id
    Write-Host "Asset $fname was already uploaded, id: $a_id"
    Write-Host ("  Upload is ready for download: " + $_.browser_download_url)
  }
  return $a_id
}

####################################################################################################
# UploadAsset
# used to upload the asset (file) to GitHub
#  - rel_id - the version number
#  - fullpath - path to the asset
####################################################################################################
function UploadAsset([int]$rel_id,[string]$fullpath)
{
  
  #[System.Net.ServicePointManager]::MaxServicePointIdleTime = 5000000
  
  
  $type_7z  = "application/octet-stream"
  $type_zip  = "application/zip"
  $type_exe = "application/x-msdownload"
  $type_txt = "text/rtf"

  $fname = Split-Path $fullpath -Leaf

  $fext = [System.IO.Path]::GetExtension($fname)
  Write-Host "fext = '$fext'"
  if ( $fext -eq ".7z") {
    $content_type = $type_7z
  } elseif ( $fext -eq ".zip") {
    $content_type = $type_zip
  } elseif ( $fext -eq ".txt") {
    $content_type = $type_txt
  } else {
    $content_type = $type_exe
  }
  $rel_arg = $auth + @{"Content-Type"=$content_type; "name"=$fname;}
  $rel_arg_js = ConvertTo-Json $rel_arg

  Write-Host "Loading contents of '$fullpath'"
  Write-Host "Content Type = '$content_type'"
  if ( $content_type -eq $type_txt ) {
    $body = Get-Content -Path $fullpath
    Write-Host "*** text"
  } else {
    $tfullpath = (Get-Item -Path $fullpath).FullName
    Write-Host "tfullpath = " + $tfullpath
    $body = [System.IO.File]::ReadAllBytes($tfullpath)
    #$body = Get-Content -Encoding byte -Raw -Path $fullpath
    Write-Host "*** NOT text"
  }
  #$body = [System.IO.File]::ReadAllBytes($fullpath)

  Write-Host "  -Uri 'https://uploads.github.com/repos/$user/$project/releases/$rel_id/assets?name=$fname'"
  Write-Host -NoNewLine "  -Headers "
  $rel_arg

  $originalPP = $ProgressPreference
  $ProgressPreference = 'SilentlyContinue'
  Write-Host "  Uploading to github"
  $rel = Invoke-WebRequest -Headers $rel_arg -Method POST -Body $body -Uri https://uploads.github.com/repos/$user/$project/releases/$rel_id/assets?name=$fname -TimeoutSec 6000
  Write-Host "  Upload finished, checking result"
  $ProgressPreference = $originalPP

  Write-Host "  Upload finished, checking result 2"
  if (($rel -eq $null) -Or ($rel.StatusCode -ne 201)) {
    Write-Host "  Upload finished, bad result"
    $rel
    # $host.SetShouldExit(101)
    exit
  }

  $rel_js = ConvertFrom-Json $rel.Content
  #Write-Host "  Disposing"
  #$rel.Dispose()
  #Write-Host "  Disposed"
  # $rel_js
  Write-Host ("  Upload is ready for download: " + $rel_js.browser_download_url)
}


####################################################################################################
#Startup code begins below here
####################################################################################################
 
$result = 0

#set the upload type to use for either Bintray or Github - added 10/21/18 as bintray was blocking access
$upBintray = "Bintray"
$upGithub = "Github"
$uploadType = $upBintray

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

# determine which platforms to work on
$platforms = @()
If ($xAll) { #do All Platforms
    $platforms = @("x86","x64")
}Else{
    If ($x64) { 
        $platforms = "x64"
    }Else{
        $platforms = "x86"
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

# upload to bintray or github
# Upload will only occur if the -u parameter is passed
If ($u){

    # one time prep for webRequests due to SSL
    PrepareWebRequest
    
    #for Github we need to ensure the version is created
    If ($uploadType -eq $upGithub) {
        # fill in the variable used for Github
        $token = $Env:GITHUB_TOKEN
        $tag = $ProductVersion
        $name = $tag
        $descr = "Release " + $name
        $user = "OpenSageTV"
        $project = "sagetv-windows"
        $auth = @{"Authorization"="token $token"}

        # $file = ".\release\Binaries\testfile.txt|.\release\Binaries\testfile.zip"
        Write-Host "Trying to find release $tag"
	$rel_id = FindRelease $tag
	if ($rel_id -ne 0) {
	  Write-Host "Release already created, id: $rel_id"
	} else {
	  $rel_id = CreateRelease $tag $name $descr
	  Write-Host "Release created, id: $rel_id"
	}
       
    }

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