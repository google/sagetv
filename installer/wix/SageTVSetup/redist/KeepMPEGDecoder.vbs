
Function moveMPEGFilters()
    Dim CommonFolder, LegacyFolder, InstallFolder, DestinationFolder, SourceTestFile, DestinationLegacy
    InstallFolder = Session.TargetPath("INSTALLFOLDER")
    'InstallFolder = "C:\Program Files (x86)\SageTV"
    CommonFolder = InstallFolder + "\Common\"
    LegacyFolder = InstallFolder + "\Common\Legacy\"
    'Use this test file to see if the Decoders are available for copy
    SourceTestFile = "stvmpgadec.dll"
    'DestinationFolder = "C:\BackupMPEG\"
    DestinationFolder = InstallFolder + "\MPEGDecoder\"
    DestinationLegacy = DestinationFolder + "Legacy\"

    Set fso = CreateObject("Scripting.FileSystemObject")
    'Check to see if the Source Files Exist use the test file
    If Not fso.FileExists(CommonFolder + SourceTestFile) Then
        'the source does not exist so just exit
	'msgbox "Decoders do not exist on this system"
	moveMPEGFilters = 1
	Set fso = Nothing
	Exit Function
    End If
    'Check to see if the file already exists in the destination folder
    If fso.FileExists(DestinationFolder + SourceTestFile) Then
        'the file exists so just exit
	'msgbox "File already in the destination location"
	moveMPEGFilters = 1
    Else
        'The file does not exist in the destination folder
        'Create the destination folder if needed
	If Not fso.FolderExists(DestinationFolder) Then
		fso.CreateFolder(DestinationFolder) 
		'msgbox "Created folder " + DestinationFolder
		'create the legacy destination folder if needed
		If Not fso.FolderExists(DestinationLegacy) Then
			fso.CreateFolder(DestinationLegacy) 
			'msgbox "Created folder " + DestinationLegacy
		End If
	End If
	'copy all the Common Files
        fso.CopyFile CommonFolder + "stv*.*", DestinationFolder, True
        fso.CopyFile CommonFolder + "msvcp71.dll", DestinationFolder, True
        fso.CopyFile CommonFolder + "msvcr71.dll", DestinationFolder, True
	'msgbox "Copying complete to " + DestinationFolder
	'copy all the Legacy Files
        fso.CopyFile LegacyFolder + "stv*.*", DestinationLegacy, True
	'msgbox "Copying complete to " + DestinationLegacy
	
	Session.Property("MPEGDecoderMoved") = "1"
	moveMPEGFilters = 1
    End If
    Set fso = Nothing
    moveMPEGFilters = 1
    Exit Function
End Function

Function registerMPEGFilters()
    Dim CommonFolder, LegacyFolder, InstallFolder, DestinationFolder, SourceTestFile, DestinationLegacy
    Dim intRegister
    InstallFolder = Session.Property("CustomActionData")
    'InstallFolder = "C:\Program Files (x86)\SageTV"
    CommonFolder = InstallFolder + "\Common\"
    LegacyFolder = InstallFolder + "\Common\Legacy\"
    'Use this test file to see if the Decoders are available for copy
    SourceTestFile = "stvmpgadec.dll"
    'DestinationFolder = "C:\BackupMPEG\"
    DestinationFolder = InstallFolder + "\MPEGDecoder\"
    DestinationLegacy = DestinationFolder + "Legacy\"

    Set fso = CreateObject("Scripting.FileSystemObject")
    'Check to see if the file exists in the destination folder
    If fso.FileExists(DestinationFolder + SourceTestFile) Then
        'the file exists so we will register the filters

	Set objShell = CreateObject("WScript.Shell")
	objShell.CurrentDirectory = DestinationFolder
	'msgbox "Changing folder to " + objShell.CurrentDirectory
	intRegister = objShell.Run("regsvr32 /s  " + Chr(34) + DestinationFolder + "stvl2ad.ax" + Chr(34))
	intRegister = objShell.Run("regsvr32 /s  " + Chr(34) + DestinationFolder + "stvl2ae.ax" + Chr(34))
	intRegister = objShell.Run("regsvr32 /s  " + Chr(34) + DestinationFolder + "stvm2vd.ax" + Chr(34))
	intRegister = objShell.Run("regsvr32 /s  " + Chr(34) + DestinationFolder + "stvm2ve.ax" + Chr(34))
	intRegister = objShell.Run("regsvr32 /s  " + Chr(34) + DestinationFolder + "stvmpeg2mux.ax" + Chr(34))
	intRegister = objShell.Run("regsvr32 /s  " + Chr(34) + DestinationFolder + "stvmpgdmx.ax" + Chr(34))
	intRegister = objShell.Run("regsvr32 /s  " + Chr(34) + DestinationFolder + "stvmuxmpeg.ax" + Chr(34))
	
	objShell.CurrentDirectory = DestinationLegacy
	'msgbox "Changing folder to " + objShell.CurrentDirectory
	intRegister = objShell.Run("regsvr32 /s  " + Chr(34) + DestinationLegacy + "stvmcdsmpeg.ax" + Chr(34))
    Else
        'The file does not exist so just exit
    End If
    Set fso = Nothing
    registerMPEGFilters = 1
    Exit Function
End Function