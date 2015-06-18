/*
 * Copyright 2004 Hauppauge Computer Works, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef HCW_HCWECP_H
#define HCW_HCWECP_H

// ------------------------------------------------------------------------
// Property set for HCW_ENCODE_CONFIG_PROPERTIES
// -----------------------------------------------------------------------
// {432A0DA4-806A-43a0-B426-4F2A234AA6B8}
static const GUID PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES = 
{ 0x432a0da4, 0x806a, 0x43a0, { 0xb4, 0x26, 0x4f, 0x2a, 0x23, 0x4a, 0xa6, 0xb8 } };


// ------------------------------------------------------------------------
// Property set for PROPSETID_HCW_ECP_PROPPAGES (A Family of Property Pages)
// -----------------------------------------------------------------------
// {70BDBEEE-48CA-40ae-B700-34A3F2A29142}
static const GUID PROPSETID_HCW_ECP_PROPPAGES = 
{ 0x70bdbeee, 0x48ca, 0x40ae, { 0xb7, 0x0, 0x34, 0xa3, 0xf2, 0xa2, 0x91, 0x42 } };



// ------------------------------------------------------------------------
// Property Page GUIDs (for PROPSETID_HCW_ECP_PROPPAGES) including...
//
//
// HCW_GUID_ECP_PROPPAGE_DIAG a diagnostic page
// {EA4F9A26-4AD7-401b-B137-96943BF9DDDD}
static const GUID HCW_GUID_ECP_PROPPAGE_DIAG = 
{ 0xea4f9a26, 0x4ad7, 0x401b, { 0xb1, 0x37, 0x96, 0x94, 0x3b, 0xf9, 0xdd, 0xdd } };
//
//
// HCW_GUID_ECP_PROPPAGE_INFO a generic "Info" page.
// {33A93DDC-434C-4881-8788-9458ACEFDA80}
static const GUID HCW_GUID_ECP_PROPPAGE_INFO = 
{ 0x33a93ddc, 0x434c, 0x4881, { 0x87, 0x88, 0x94, 0x58, 0xac, 0xef, 0xda, 0x80 } };
//
//
// HCW_GUID_ECP_PROPPAGE_VIDEO Video Properties Page (bitrate, etc.)
// {2C669739-B41C-4092-9750-97AC9073507B}
static const GUID HCW_GUID_ECP_PROPPAGE_VIDEO = 
{ 0x2c669739, 0xb41c, 0x4092, { 0x97, 0x50, 0x97, 0xac, 0x90, 0x73, 0x50, 0x7b } };
//
//
// HCW_GUID_ECP_PROPPAGE_AUDIO Audio Properties Page (bitrate, samplerate, etc.)
// {10A64E72-CE72-4ffd-8D0D-9ECA43FB6A5A}
static const GUID HCW_GUID_ECP_PROPPAGE_AUDIO = 
{ 0x10a64e72, 0xce72, 0x4ffd, { 0x8d, 0xd, 0x9e, 0xca, 0x43, 0xfb, 0x6a, 0x5a } };
//
//
// HCW_GUID_ECP_PROPPAGE_SYSTEM System Properties Page (StreamType)
// {DB2132AE-12DB-4624-A4F1-517F1313D66F}
static const GUID HCW_GUID_ECP_PROPPAGE_SYSTEM = 
{ 0xdb2132ae, 0x12db, 0x4624, { 0xa4, 0xf1, 0x51, 0x7f, 0x13, 0x13, 0xd6, 0x6f } };
//
//
// -----------------------------------------------------------------------





/*
** HCW_ENCODE_CONFIG_PROPERTIES
** Access these properties through the PROPSETID_HCW_ENCODE_CONFIG_PROPERTIES GUID.
** 
** All Properties are BOTH Get and Set unless otherwise specified.
*/
typedef enum 
{
	HCW_ECP_SYSTEM_StreamType		=   0, // Type of Stream

	HCW_ECP_VIDEO_Horizontal_Res	= 100, // Horizontal Resolution (Vertical is implied by StreamType and VideoFormat)
	HCW_ECP_VIDEO_GOP_OpenClosed	= 101, // Closed or Open GOP														
	HCW_ECP_VIDEO_BitRate			= 102, // BitRate and Mode(CBR/VBR)													
	HCW_ECP_VIDEO_GOP_Size			= 103, // GOP Size Details (Number of Pictures / Number of BFrames)														
	HCW_ECP_VIDEO_InverseTelecine	= 104, // Inverse Telecine On or Off

	HCW_ECP_AUDIO_BitRate			= 200, // BitRate and Layer															
	HCW_ECP_AUDIO_SampleRate		= 201, // SampleRate																
	HCW_ECP_AUDIO_Output			= 202, // Output Mode (Mono, Stereo, Joint, Dual)											
	HCW_ECP_AUDIO_CRC				= 203, // CRC On/Off

	HCW_ECP_DETECT_Macrovision		= 300, // GET ONLY
										   // Macrovision Detection (Yes, No, Don't Know)
	HCW_ECP_DETECT_MVision_Level	= 301, // GET ONLY
								           // Macrovision Level Detection (ie. Level1, Level2, Level3)

	HCW_ECP_INFO_Model				= 400, // GET ONLY
										   // Numeric Hardware Model
	HCW_ECP_INFO_DriverVersion		= 401, // GET ONLY
										   // Numeric Driver Version
	HCW_ECP_INFO_GeneralString		= 402, // GET ONLY
										   // String Buffer containing Device Specific Information
										   // It is NOT intended to be useful for programatic parsing but
										   // rather to be human readable and suitable for presentation in
										   // perhaps an advanced dialog box.  Perhaps the only user of this
										   // this property will be Hauppauge's own Info Tab Property Page.
}	HCW_ENCODE_CONFIG_PROPERTIES;




// ------------------------------------------------------------------------
// HCW_ECP_SYSTEM_ Properties...
// Enums and Structures
// -----------------------------------------------------------------------

/*
** NOTE Regarding HCW_Sys_StreamType_Program:
** While "Program" is supported, it is highly recommended that the more
** specific Program_DVD, Program_DVD_MC, or Program_SVCD versions are used
** unless you have a very good reason for not using them.
*/
typedef enum 
{
	HCW_Sys_StreamType_Unknown			=   0, //Unknown (error?) StreamType

	HCW_Sys_StreamType_Program			= 101, //Program Stream (SEE NOTE ABOVE)
	HCW_Sys_StreamType_Program_DVD		= 102, //Program Stream (DVD Compliant)
	HCW_Sys_StreamType_Program_DVD_MC	= 103, //Program Stream (Meets MS MediaCenter Requirements -- Not Strictly DVD-Compliant)
	HCW_Sys_StreamType_Program_SVCD		= 104, //Program Stream (SVCD Compliant)

	HCW_Sys_StreamType_MPEG1			= 201, //MPGE1 Stream					
	HCW_Sys_StreamType_MPEG1_VCD		= 202, //MPEG1 Stream (VCD Compliant)	
}	HCW_Sys_StreamType, *PHCW_Sys_StreamType;





// ------------------------------------------------------------------------
// HCW_ECP_VIDEO_ Properties...
// Enums and Structures
// -----------------------------------------------------------------------

typedef enum
{
	HCW_Vid_HRes_Unknown			= 0,

	HCW_Vid_HRes_FullD1				= 1,
	HCW_Vid_HRes_720				= 1,

	HCW_Vid_HRes_TwoThirdsD1		= 2,
	HCW_Vid_HRes_480				= 2,

	HCW_Vid_HRes_HalfD1				= 3,
	HCW_Vid_HRes_352				= 3,

	HCW_Vid_HRes_320				= 4,

	HCW_Vid_HRes_640				= 5,
}	HCW_Vid_HRes, *PHCW_Vid_HRes;

typedef enum
{
	HCW_Vid_GOP_OC_Open		= 0, //Open GOPS
	HCW_Vid_GOP_OC_Closed	= 1, //Closed GOPS
}	HCW_Vid_GOP_OpenClosed, *PHCW_Vid_GOP_OpenClosed;

typedef enum
{
	HCW_Vid_EncMode_CBR		= 0, //Constant Bit Rate
	HCW_Vid_EncMode_VBR		= 1, //Variable Bit Rate
}	HCW_Vid_EncMode, *PHCW_Vid_EncMode;

typedef struct
{
	DWORD			dwSize;			//SizeOf Struct
	DWORD			dwReserved;		//Reserved, Must Be Zero
	HCW_Vid_EncMode Mode;			//Indicates CBR/VBR
	DWORD			dwBitRate;		//BitRate in kbits/sec (Average or Target for Mode VBR)
	DWORD			dwBitRatePeak;	//Peak VBR BitRate in kbits/sec (ignored for Mode CBR)
}	HCW_Vid_BitRate, *PHCW_Vid_BitRate;

/*
** HCW_Vid_GOPSize
** Set the Number of Pictures and PFrame Spacing.
**
** For IBBPBBPBBPBBPBB...
** 15/3
** For IBBPBBPBBPBB...
** 12/3
** For IPPPPP
** 6/1
**
** NOTE: Set dwNumPictures to 0 to reset to driver default for both.
*/
typedef struct
{
	DWORD			dwSize;			//SizeOf Struct
	DWORD			dwReserved;		//Reserved, Must Be Zero
	DWORD			dwNumPictures;	//Number of Pictures
	DWORD			dwPFrameDistance;//Distance from P to P
}	HCW_Vid_GOPSize,*PHCW_Vid_GOPSize;

typedef enum
{
	HCW_Vid_InverseTelecine_Off = 0, //Inverse Telecine Off
	HCW_Vid_InverseTelecine_On  = 1, //Inverse Telecine On
}	HCW_Vid_InverseTelecine, *PHCW_Vid_InverseTelecine;



// ------------------------------------------------------------------------
// HCW_ECP_AUDIO_ Properties...
// Enums and Structures
// -----------------------------------------------------------------------

typedef enum
{
	HCW_Aud_BitRate_Layer2_32		=  1,
	HCW_Aud_BitRate_Layer2_48		=  2,
	HCW_Aud_BitRate_Layer2_56		=  3,
	HCW_Aud_BitRate_Layer2_64		=  4,
	HCW_Aud_BitRate_Layer2_80		=  5,
	HCW_Aud_BitRate_Layer2_96		=  6,
	HCW_Aud_BitRate_Layer2_112		=  7,
	HCW_Aud_BitRate_Layer2_128		=  8,
	HCW_Aud_BitRate_Layer2_160		=  9,
	HCW_Aud_BitRate_Layer2_192		=  10,
	HCW_Aud_BitRate_Layer2_224		=  11,
	HCW_Aud_BitRate_Layer2_256		=  12,
	HCW_Aud_BitRate_Layer2_320		=  13,
	HCW_Aud_BitRate_Layer2_384		=  14,
}	HCW_Aud_BitRate_Layer2, *PHCW_Aud_BitRate_Layer2;

typedef enum
{
	HCW_Aud_Layer_2		=  2,
}	HCW_Aud_Layer, *PHCW_Aud_Layer;

typedef struct
{
	DWORD			dwSize;			//SizeOf Struct
	DWORD			dwReserved;		//Reserved, Must Be Zero
	HCW_Aud_Layer	Layer;			//Audio Layer (dictates meaning of dwBitRate)
	DWORD			dwBitRate;		//BitRate, meaning dependent on Layer as follows...
									//         If Layer==HCW_Aud_Layer_2, dwBitRate is of type HCW_Aud_BitRate_Layer2
									//         Any other value is presently undefined
}	HCW_Aud_BitRate, *PHCW_Aud_BitRate;

typedef enum
{
	HCW_Aud_SampleRate_32			= 0,
	HCW_Aud_SampleRate_44			= 1, //44.1
	HCW_Aud_SampleRate_48			= 2,
}	HCW_Aud_SampleRate, *PHCW_Aud_SampleRate;

typedef enum
{	
	HCW_Aud_Output_Stereo		= 0,
	HCW_Aud_Output_JointStereo	= 1,
	HCW_Aud_Output_DualChannel	= 2,
	HCW_Aud_Output_Mono			= 3,
}	HCW_Aud_Output, *PHCW_Aud_Output;

typedef enum
{
	HCW_Aud_CRC_Off	= 0,
	HCW_Aud_CRC_On	= 1,
}	HCW_Aud_CRC, *PHCW_Aud_CRC;




// ------------------------------------------------------------------------
// HCW_ECP_DETECT_ Properties...
// Enums and Structures
// -----------------------------------------------------------------------

/*
** HCW_ECP_DETECT_Macrovision (a Yes/No question)
** Note that this property will return NOT_IMPLEMENTED if Macrovision Detection
** is not supported.  Else we will return SUCCESS with one of the following
** values.
*/
typedef enum
{
	HCW_Detect_Macrovision_No		= 0, //Macrovision Detection - NOT Detected
	HCW_Detect_Macrovision_Yes		= 1, //Macrovision Detection - YES Detected
	HCW_Detect_Macrovision_DontKnow	= 2, //Macrovision Detection - Can't Determine Right Now
}	HCW_Detect_Macrovision, *PHCW_Detect_Macrovision;


/*
** HCW_ECP_DETECT_MVision_Level (not just Yes/No but "which level")
** NOTE THAT this property will return NOT_IMPLEMENTED if Macrovision Level Detection
** is not supported.  Else we will return SUCCESS with one of the following values...
**
** HCW_Detect_MVision_Level_Unknown	- Error / Can't determine right now.
** HCW_Detect_MVision_Level_None	- We CAN detect Macrovision but we DO NOT currently detect any.
** HCW_Detect_MVision_Level_1       - We detect Macrovision Level 1.
** HCW_Detect_MVision_Level_2       - We detect Macrovision Level 2.
** HCW_Detect_MVision_Level_3       - We detect Macrovision Level 3.
**
**
** IMPORTANT NOTE...
** The HCW_Detect_MVision_Level type is signed.  Anything "greater than" '_None'
** indicates that Macrovision is enabled at some level.  Negative numbers are reserved
** for special use.  For example, the "_Unknown" option is used to indicate some issue with the driver
** which does not allow it to give an accurate answer.
**
** ANOTHER IMPORTANT NOTE:
** The "Macrovision Levels" are not universally documented.  Therefore, these levels which we
** detect/report should be thought of more as the levels which Micrsoft
** defines with their AM_PROPERTY_COPY_MACROVISION property.  It is our goal to provide a mechanism
** for vendors to coordinate the video source with Microsoft's AM_PROPERTY_COPY_MACROVISION property.
*/
typedef long HCW_Detect_MVision_Level, *PHCW_Detect_MVision_Level;
#define HCW_Detect_MVision_Level_Unknown ((HCW_Detect_MVision_Level)-1) //error / can't determine right now
#define HCW_Detect_MVision_Level_None    0                              //no macrovision detected
#define HCW_Detect_MVision_Level_1       1                              //Level1 detected
#define HCW_Detect_MVision_Level_2       2                              //Level2 detected
#define HCW_Detect_MVision_Level_3       3                              //Level3 detected




// ------------------------------------------------------------------------
// HCW_ECP_INFO_ Properties...
// Enums and Structures
// ------------------------------------------------------------------------

/*
** Hauppauge WinTV Model numbers are typically of the form MMxxx
** where MM indicates a family of hardware.  The xxx specifies
** model specific details.
** Most callers will only want to know if a particular model is in
** the range MM000 to MM999 and treat all of them the same.
*/
typedef unsigned long HCW_Info_Model, *PHCW_Info_Model;


typedef struct
{
	DWORD			dwSize;			//SizeOf Struct
	DWORD			dwReserved;		//Reserved, Must Be Zero
	DWORD			dwVersionMajor;	//Major Version Number
	DWORD			dwVersionMinor;	//Minor Version Number
	DWORD			dwRevision;		//Major Version Number
	DWORD			dwBuildNo;		//Hauppauge Build Date Code
}	HCW_Info_DriverVersion, *PHCW_Info_DriverVersion;


/*
** HCW_ECP_INFO_GeneralString / HCW_Info_GeneralString
** We can always make a smarter, more general property if 4K should turn out not to
** be enough data.  For now this is fast and easy and can always be superceded
** in the future.
*/
typedef struct
{
	DWORD			dwSize;			//SizeOf Struct
	DWORD			dwReserved;		//Reserved, Must Be Zero
	char			sz[4096];		//Contains zero terminated string on return.
}	HCW_Info_GeneralString, *PHCW_Info_GeneralString;


#endif //HCW_HCWECP_H
