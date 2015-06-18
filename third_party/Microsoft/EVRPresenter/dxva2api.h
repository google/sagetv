

/* this ALWAYS GENERATED file contains the definitions for the interfaces */


 /* File created by MIDL compiler version 7.00.0499 */
/* Compiler settings for dxva2api.idl:
    Oicf, W1, Zp8, env=Win32 (32b run)
    protocol : dce , ms_ext, c_ext, robust
    error checks: allocation ref bounds_check enum stub_data 
    VC __declspec() decoration level: 
         __declspec(uuid()), __declspec(selectany), __declspec(novtable)
         DECLSPEC_UUID(), MIDL_INTERFACE()
*/
//@@MIDL_FILE_HEADING(  )

#pragma warning( disable: 4049 )  /* more than 64k source lines */


/* verify that the <rpcndr.h> version is high enough to compile this file*/
#ifndef __REQUIRED_RPCNDR_H_VERSION__
#define __REQUIRED_RPCNDR_H_VERSION__ 500
#endif

/* verify that the <rpcsal.h> version is high enough to compile this file*/
#ifndef __REQUIRED_RPCSAL_H_VERSION__
#define __REQUIRED_RPCSAL_H_VERSION__ 100
#endif

#include "rpc.h"
#include "rpcndr.h"

#ifndef __RPCNDR_H_VERSION__
#error this stub requires an updated version of <rpcndr.h>
#endif // __RPCNDR_H_VERSION__

#ifndef COM_NO_WINDOWS_H
#include "windows.h"
#include "ole2.h"
#endif /*COM_NO_WINDOWS_H*/

#ifndef __dxva2api_h__
#define __dxva2api_h__

#if defined(_MSC_VER) && (_MSC_VER >= 1020)
#pragma once
#endif

/* Forward Declarations */ 

#ifndef __IDirect3DDeviceManager9_FWD_DEFINED__
#define __IDirect3DDeviceManager9_FWD_DEFINED__
typedef interface IDirect3DDeviceManager9 IDirect3DDeviceManager9;
#endif 	/* __IDirect3DDeviceManager9_FWD_DEFINED__ */


#ifndef __IDirectXVideoAccelerationService_FWD_DEFINED__
#define __IDirectXVideoAccelerationService_FWD_DEFINED__
typedef interface IDirectXVideoAccelerationService IDirectXVideoAccelerationService;
#endif 	/* __IDirectXVideoAccelerationService_FWD_DEFINED__ */


#ifndef __IDirectXVideoDecoderService_FWD_DEFINED__
#define __IDirectXVideoDecoderService_FWD_DEFINED__
typedef interface IDirectXVideoDecoderService IDirectXVideoDecoderService;
#endif 	/* __IDirectXVideoDecoderService_FWD_DEFINED__ */


#ifndef __IDirectXVideoProcessorService_FWD_DEFINED__
#define __IDirectXVideoProcessorService_FWD_DEFINED__
typedef interface IDirectXVideoProcessorService IDirectXVideoProcessorService;
#endif 	/* __IDirectXVideoProcessorService_FWD_DEFINED__ */


#ifndef __IDirectXVideoDecoder_FWD_DEFINED__
#define __IDirectXVideoDecoder_FWD_DEFINED__
typedef interface IDirectXVideoDecoder IDirectXVideoDecoder;
#endif 	/* __IDirectXVideoDecoder_FWD_DEFINED__ */


#ifndef __IDirectXVideoProcessor_FWD_DEFINED__
#define __IDirectXVideoProcessor_FWD_DEFINED__
typedef interface IDirectXVideoProcessor IDirectXVideoProcessor;
#endif 	/* __IDirectXVideoProcessor_FWD_DEFINED__ */


#ifndef __IDirectXVideoMemoryConfiguration_FWD_DEFINED__
#define __IDirectXVideoMemoryConfiguration_FWD_DEFINED__
typedef interface IDirectXVideoMemoryConfiguration IDirectXVideoMemoryConfiguration;
#endif 	/* __IDirectXVideoMemoryConfiguration_FWD_DEFINED__ */


/* header files for imported files */
#include "unknwn.h"

#ifdef __cplusplus
extern "C"{
#endif 


/* interface __MIDL_itf_dxva2api_0000_0000 */
/* [local] */ 

//
//   The following declarations within the 'if 0' block are dummy typedefs used to make
//   the evr.idl file build. The actual definitions are contained in d3d9.h
//
#if 0
typedef DWORD IDirect3DDevice9;

typedef DWORD IDirect3DSurface9;

typedef DWORD D3DFORMAT;

typedef DWORD D3DPOOL;

#endif
#ifndef DXVA2_API_DEFINED
#define DXVA2_API_DEFINED
DEFINE_GUID(DXVA2_ModeMPEG2_MoComp, 0xe6a9f44b, 0x61b0, 0x4563,0x9e,0xa4,0x63,0xd2,0xa3,0xc6,0xfe,0x66);
DEFINE_GUID(DXVA2_ModeMPEG2_IDCT,   0xbf22ad00, 0x03ea, 0x4690,0x80,0x77,0x47,0x33,0x46,0x20,0x9b,0x7e);
DEFINE_GUID(DXVA2_ModeMPEG2_VLD,    0xee27417f, 0x5e28, 0x4e65,0xbe,0xea,0x1d,0x26,0xb5,0x08,0xad,0xc9);
DEFINE_GUID(DXVA2_ModeH264_A,  0x1b81be64, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeH264_B,  0x1b81be65, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeH264_C,  0x1b81be66, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeH264_D,  0x1b81be67, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeH264_E,  0x1b81be68, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeH264_F,  0x1b81be69, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeWMV8_A,  0x1b81be80, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeWMV8_B,  0x1b81be81, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeWMV9_A,  0x1b81be90, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeWMV9_B,  0x1b81be91, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeWMV9_C,  0x1b81be94, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeVC1_A,   0x1b81beA0, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeVC1_B,   0x1b81beA1, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeVC1_C,   0x1b81beA2, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_ModeVC1_D,   0x1b81beA3, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_NoEncrypt,   0x1b81beD0, 0xa0c7,0x11d3,0xb9,0x84,0x00,0xc0,0x4f,0x2e,0x73,0xc5);
DEFINE_GUID(DXVA2_VideoProcProgressiveDevice, 0x5a54a0c9,0xc7ec,0x4bd9,0x8e,0xde,0xf3,0xc7,0x5d,0xc4,0x39,0x3b);
DEFINE_GUID(DXVA2_VideoProcBobDevice,         0x335aa36e,0x7884,0x43a4,0x9c,0x91,0x7f,0x87,0xfa,0xf3,0xe3,0x7e);
DEFINE_GUID(DXVA2_VideoProcSoftwareDevice,    0x4553d47f,0xee7e,0x4e3f,0x94,0x75,0xdb,0xf1,0x37,0x6c,0x48,0x10);
#define DXVA2_ModeMPEG2_MOCOMP      DXVA2_ModeMPEG2_MoComp
#define DXVA2_ModeWMV8_PostProc     DXVA2_ModeWMV8_A
#define DXVA2_ModeWMV8_MoComp       DXVA2_ModeWMV8_B
#define DXVA2_ModeWMV9_PostProc     DXVA2_ModeWMV9_A
#define DXVA2_ModeWMV9_MoComp       DXVA2_ModeWMV9_B
#define DXVA2_ModeWMV9_IDCT         DXVA2_ModeWMV9_C
#define DXVA2_ModeVC1_PostProc      DXVA2_ModeVC1_A
#define DXVA2_ModeVC1_MoComp        DXVA2_ModeVC1_B
#define DXVA2_ModeVC1_IDCT          DXVA2_ModeVC1_C
#define DXVA2_ModeVC1_VLD           DXVA2_ModeVC1_D
#define DXVA2_ModeH264_MoComp_NoFGT DXVA2_ModeH264_A
#define DXVA2_ModeH264_MoComp_FGT   DXVA2_ModeH264_B
#define DXVA2_ModeH264_IDCT_NoFGT   DXVA2_ModeH264_C
#define DXVA2_ModeH264_IDCT_FGT     DXVA2_ModeH264_D
#define DXVA2_ModeH264_VLD_NoFGT    DXVA2_ModeH264_E
#define DXVA2_ModeH264_VLD_FGT      DXVA2_ModeH264_F
#define DXVA2_E_NOT_INITIALIZED     ((HRESULT)0x80041000L)
#define DXVA2_E_NEW_VIDEO_DEVICE    ((HRESULT)0x80041001L)
#define DXVA2_E_VIDEO_DEVICE_LOCKED ((HRESULT)0x80041002L)
#define DXVA2_E_NOT_AVAILABLE       ((HRESULT)0x80041003L)
#if defined(_WIN32) && !defined(_NO_COM)
DEFINE_GUID(IID_IDirect3DDeviceManager9,          0xa0cade0f,0x06d5,0x4cf4,0xa1,0xc7,0xf3,0xcd,0xd7,0x25,0xaa,0x75);
DEFINE_GUID(IID_IDirectXVideoAccelerationService, 0xfc51a550,0xd5e7,0x11d9,0xaf,0x55,0x00,0x05,0x4e,0x43,0xff,0x02);
DEFINE_GUID(IID_IDirectXVideoDecoderService,      0xfc51a551,0xd5e7,0x11d9,0xaf,0x55,0x00,0x05,0x4e,0x43,0xff,0x02);
DEFINE_GUID(IID_IDirectXVideoProcessorService,    0xfc51a552,0xd5e7,0x11d9,0xaf,0x55,0x00,0x05,0x4e,0x43,0xff,0x02);
DEFINE_GUID(IID_IDirectXVideoDecoder,             0xf2b0810a,0xfd00,0x43c9,0x91,0x8c,0xdf,0x94,0xe2,0xd8,0xef,0x7d);
DEFINE_GUID(IID_IDirectXVideoProcessor,           0x8c3a39f0,0x916e,0x4690,0x80,0x4f,0x4c,0x80,0x01,0x35,0x5d,0x25);
DEFINE_GUID(IID_IDirectXVideoMemoryConfiguration, 0xb7f916dd,0xdb3b,0x49c1,0x84,0xd7,0xe4,0x5e,0xf9,0x9e,0xc7,0x26);
#endif
#ifndef MAX_DEINTERLACE_SURFACES
#define MAX_DEINTERLACE_SURFACES 32
#endif
#ifndef MAX_SUBSTREAMS
#define MAX_SUBSTREAMS 15
#endif
typedef struct _DXVA2_ExtendedFormat
    {
    union 
        {
        struct 
            {
            UINT SampleFormat	: 8;
            UINT VideoChromaSubsampling	: 4;
            UINT NominalRange	: 3;
            UINT VideoTransferMatrix	: 3;
            UINT VideoLighting	: 4;
            UINT VideoPrimaries	: 5;
            UINT VideoTransferFunction	: 5;
            } 	;
        UINT value;
        } 	;
    } 	DXVA2_ExtendedFormat;

typedef 
enum _DXVA2_SampleFormat
    {	DXVA2_SampleFormatMask	= 0xff,
	DXVA2_SampleUnknown	= 0,
	DXVA2_SampleProgressiveFrame	= 2,
	DXVA2_SampleFieldInterleavedEvenFirst	= 3,
	DXVA2_SampleFieldInterleavedOddFirst	= 4,
	DXVA2_SampleFieldSingleEven	= 5,
	DXVA2_SampleFieldSingleOdd	= 6,
	DXVA2_SampleSubStream	= 7
    } 	DXVA2_SampleFormat;

typedef 
enum _DXVA2_VideoChromaSubSampling
    {	DXVA2_VideoChromaSubsamplingMask	= 0xf,
	DXVA2_VideoChromaSubsampling_Unknown	= 0,
	DXVA2_VideoChromaSubsampling_ProgressiveChroma	= 0x8,
	DXVA2_VideoChromaSubsampling_Horizontally_Cosited	= 0x4,
	DXVA2_VideoChromaSubsampling_Vertically_Cosited	= 0x2,
	DXVA2_VideoChromaSubsampling_Vertically_AlignedChromaPlanes	= 0x1,
	DXVA2_VideoChromaSubsampling_MPEG2	= ( DXVA2_VideoChromaSubsampling_Horizontally_Cosited | DXVA2_VideoChromaSubsampling_Vertically_AlignedChromaPlanes ) ,
	DXVA2_VideoChromaSubsampling_MPEG1	= DXVA2_VideoChromaSubsampling_Vertically_AlignedChromaPlanes,
	DXVA2_VideoChromaSubsampling_DV_PAL	= ( DXVA2_VideoChromaSubsampling_Horizontally_Cosited | DXVA2_VideoChromaSubsampling_Vertically_Cosited ) ,
	DXVA2_VideoChromaSubsampling_Cosited	= ( ( DXVA2_VideoChromaSubsampling_Horizontally_Cosited | DXVA2_VideoChromaSubsampling_Vertically_Cosited )  | DXVA2_VideoChromaSubsampling_Vertically_AlignedChromaPlanes ) 
    } 	DXVA2_VideoChromaSubSampling;

typedef 
enum _DXVA2_NominalRange
    {	DXVA2_NominalRangeMask	= 0x7,
	DXVA2_NominalRange_Unknown	= 0,
	DXVA2_NominalRange_Normal	= 1,
	DXVA2_NominalRange_Wide	= 2,
	DXVA2_NominalRange_0_255	= 1,
	DXVA2_NominalRange_16_235	= 2,
	DXVA2_NominalRange_48_208	= 3
    } 	DXVA2_NominalRange;

typedef 
enum _DXVA2_VideoTransferMatrix
    {	DXVA2_VideoTransferMatrixMask	= 0x7,
	DXVA2_VideoTransferMatrix_Unknown	= 0,
	DXVA2_VideoTransferMatrix_BT709	= 1,
	DXVA2_VideoTransferMatrix_BT601	= 2,
	DXVA2_VideoTransferMatrix_SMPTE240M	= 3
    } 	DXVA2_VideoTransferMatrix;

typedef 
enum _DXVA2_VideoLighting
    {	DXVA2_VideoLightingMask	= 0xf,
	DXVA2_VideoLighting_Unknown	= 0,
	DXVA2_VideoLighting_bright	= 1,
	DXVA2_VideoLighting_office	= 2,
	DXVA2_VideoLighting_dim	= 3,
	DXVA2_VideoLighting_dark	= 4
    } 	DXVA2_VideoLighting;

typedef 
enum _DXVA2_VideoPrimaries
    {	DXVA2_VideoPrimariesMask	= 0x1f,
	DXVA2_VideoPrimaries_Unknown	= 0,
	DXVA2_VideoPrimaries_reserved	= 1,
	DXVA2_VideoPrimaries_BT709	= 2,
	DXVA2_VideoPrimaries_BT470_2_SysM	= 3,
	DXVA2_VideoPrimaries_BT470_2_SysBG	= 4,
	DXVA2_VideoPrimaries_SMPTE170M	= 5,
	DXVA2_VideoPrimaries_SMPTE240M	= 6,
	DXVA2_VideoPrimaries_EBU3213	= 7,
	DXVA2_VideoPrimaries_SMPTE_C	= 8
    } 	DXVA2_VideoPrimaries;

typedef 
enum _DXVA2_VideoTransferFunction
    {	DXVA2_VideoTransFuncMask	= 0x1f,
	DXVA2_VideoTransFunc_Unknown	= 0,
	DXVA2_VideoTransFunc_10	= 1,
	DXVA2_VideoTransFunc_18	= 2,
	DXVA2_VideoTransFunc_20	= 3,
	DXVA2_VideoTransFunc_22	= 4,
	DXVA2_VideoTransFunc_709	= 5,
	DXVA2_VideoTransFunc_240M	= 6,
	DXVA2_VideoTransFunc_sRGB	= 7,
	DXVA2_VideoTransFunc_28	= 8
    } 	DXVA2_VideoTransferFunction;

//
// Deprecated labels - please use the ones in the DXVA2_VideoTransferFunction enum.
//
#define DXVA2_VideoTransFunc_22_709         DXVA2_VideoTransFunc_709     
#define DXVA2_VideoTransFunc_22_240M        DXVA2_VideoTransFunc_240M    
#define DXVA2_VideoTransFunc_22_8bit_sRGB   DXVA2_VideoTransFunc_sRGB    
typedef struct _DXVA2_Frequency
    {
    UINT Numerator;
    UINT Denominator;
    } 	DXVA2_Frequency;

typedef struct _DXVA2_VideoDesc
    {
    UINT SampleWidth;
    UINT SampleHeight;
    DXVA2_ExtendedFormat SampleFormat;
    D3DFORMAT Format;
    DXVA2_Frequency InputSampleFreq;
    DXVA2_Frequency OutputFrameFreq;
    UINT UABProtectionLevel;
    UINT Reserved;
    } 	DXVA2_VideoDesc;


enum __MIDL___MIDL_itf_dxva2api_0000_0000_0003
    {	DXVA2_DeinterlaceTech_Unknown	= 0,
	DXVA2_DeinterlaceTech_BOBLineReplicate	= 0x1,
	DXVA2_DeinterlaceTech_BOBVerticalStretch	= 0x2,
	DXVA2_DeinterlaceTech_BOBVerticalStretch4Tap	= 0x4,
	DXVA2_DeinterlaceTech_MedianFiltering	= 0x8,
	DXVA2_DeinterlaceTech_EdgeFiltering	= 0x10,
	DXVA2_DeinterlaceTech_FieldAdaptive	= 0x20,
	DXVA2_DeinterlaceTech_PixelAdaptive	= 0x40,
	DXVA2_DeinterlaceTech_MotionVectorSteered	= 0x80,
	DXVA2_DeinterlaceTech_InverseTelecine	= 0x100,
	DXVA2_DeinterlaceTech_Mask	= 0x1ff
    } ;

enum __MIDL___MIDL_itf_dxva2api_0000_0000_0004
    {	DXVA2_NoiseFilterLumaLevel	= 1,
	DXVA2_NoiseFilterLumaThreshold	= 2,
	DXVA2_NoiseFilterLumaRadius	= 3,
	DXVA2_NoiseFilterChromaLevel	= 4,
	DXVA2_NoiseFilterChromaThreshold	= 5,
	DXVA2_NoiseFilterChromaRadius	= 6,
	DXVA2_DetailFilterLumaLevel	= 7,
	DXVA2_DetailFilterLumaThreshold	= 8,
	DXVA2_DetailFilterLumaRadius	= 9,
	DXVA2_DetailFilterChromaLevel	= 10,
	DXVA2_DetailFilterChromaThreshold	= 11,
	DXVA2_DetailFilterChromaRadius	= 12
    } ;

enum __MIDL___MIDL_itf_dxva2api_0000_0000_0005
    {	DXVA2_NoiseFilterTech_Unsupported	= 0,
	DXVA2_NoiseFilterTech_Unknown	= 0x1,
	DXVA2_NoiseFilterTech_Median	= 0x2,
	DXVA2_NoiseFilterTech_Temporal	= 0x4,
	DXVA2_NoiseFilterTech_BlockNoise	= 0x8,
	DXVA2_NoiseFilterTech_MosquitoNoise	= 0x10,
	DXVA2_NoiseFilterTech_Mask	= 0x1f
    } ;

enum __MIDL___MIDL_itf_dxva2api_0000_0000_0006
    {	DXVA2_DetailFilterTech_Unsupported	= 0,
	DXVA2_DetailFilterTech_Unknown	= 0x1,
	DXVA2_DetailFilterTech_Edge	= 0x2,
	DXVA2_DetailFilterTech_Sharpening	= 0x4,
	DXVA2_DetailFilterTech_Mask	= 0x7
    } ;

enum __MIDL___MIDL_itf_dxva2api_0000_0000_0007
    {	DXVA2_ProcAmp_None	= 0,
	DXVA2_ProcAmp_Brightness	= 0x1,
	DXVA2_ProcAmp_Contrast	= 0x2,
	DXVA2_ProcAmp_Hue	= 0x4,
	DXVA2_ProcAmp_Saturation	= 0x8,
	DXVA2_ProcAmp_Mask	= 0xf
    } ;

enum __MIDL___MIDL_itf_dxva2api_0000_0000_0008
    {	DXVA2_VideoProcess_None	= 0,
	DXVA2_VideoProcess_YUV2RGB	= 0x1,
	DXVA2_VideoProcess_StretchX	= 0x2,
	DXVA2_VideoProcess_StretchY	= 0x4,
	DXVA2_VideoProcess_AlphaBlend	= 0x8,
	DXVA2_VideoProcess_SubRects	= 0x10,
	DXVA2_VideoProcess_SubStreams	= 0x20,
	DXVA2_VideoProcess_SubStreamsExtended	= 0x40,
	DXVA2_VideoProcess_YUV2RGBExtended	= 0x80,
	DXVA2_VideoProcess_AlphaBlendExtended	= 0x100,
	DXVA2_VideoProcess_Constriction	= 0x200,
	DXVA2_VideoProcess_NoiseFilter	= 0x400,
	DXVA2_VideoProcess_DetailFilter	= 0x800,
	DXVA2_VideoProcess_PlanarAlpha	= 0x1000,
	DXVA2_VideoProcess_LinearScaling	= 0x2000,
	DXVA2_VideoProcess_GammaCompensated	= 0x4000,
	DXVA2_VideoProcess_MaintainsOriginalFieldData	= 0x8000,
	DXVA2_VideoProcess_Mask	= 0xffff
    } ;

enum __MIDL___MIDL_itf_dxva2api_0000_0000_0009
    {	DXVA2_VPDev_HardwareDevice	= 0x1,
	DXVA2_VPDev_EmulatedDXVA1	= 0x2,
	DXVA2_VPDev_SoftwareDevice	= 0x4,
	DXVA2_VPDev_Mask	= 0x7
    } ;

enum __MIDL___MIDL_itf_dxva2api_0000_0000_0010
    {	DXVA2_SampleData_RFF	= 0x1,
	DXVA2_SampleData_TFF	= 0x2,
	DXVA2_SampleData_RFF_TFF_Present	= 0x4,
	DXVA2_SampleData_Mask	= 0xffff
    } ;

enum __MIDL___MIDL_itf_dxva2api_0000_0000_0011
    {	DXVA2_DestData_RFF	= 0x1,
	DXVA2_DestData_TFF	= 0x2,
	DXVA2_DestData_RFF_TFF_Present	= 0x4,
	DXVA2_DestData_Mask	= 0xffff
    } ;
typedef struct _DXVA2_VideoProcessorCaps
    {
    UINT DeviceCaps;
    D3DPOOL InputPool;
    UINT NumForwardRefSamples;
    UINT NumBackwardRefSamples;
    UINT Reserved;
    UINT DeinterlaceTechnology;
    UINT ProcAmpControlCaps;
    UINT VideoProcessorOperations;
    UINT NoiseFilterTechnology;
    UINT DetailFilterTechnology;
    } 	DXVA2_VideoProcessorCaps;

#ifdef __midl
typedef LONG DXVA2_Fixed32;

#else
typedef struct _DXVA2_Fixed32
{
    union {
        struct {
            USHORT  Fraction;
            SHORT   Value;
        };
        LONG ll;
    };
} DXVA2_Fixed32;
#endif
typedef struct _DXVA2_AYUVSample8
    {
    UCHAR Cr;
    UCHAR Cb;
    UCHAR Y;
    UCHAR Alpha;
    } 	DXVA2_AYUVSample8;

typedef struct _DXVA2_AYUVSample16
    {
    USHORT Cr;
    USHORT Cb;
    USHORT Y;
    USHORT Alpha;
    } 	DXVA2_AYUVSample16;

typedef LONGLONG REFERENCE_TIME;

#if defined(_D3D9_H_) || defined(_d3d9P_H_)
typedef struct _DXVA2_VideoSample
    {
    REFERENCE_TIME Start;
    REFERENCE_TIME End;
    DXVA2_ExtendedFormat SampleFormat;
    IDirect3DSurface9 *SrcSurface;
    RECT SrcRect;
    RECT DstRect;
    DXVA2_AYUVSample8 Pal[ 16 ];
    DXVA2_Fixed32 PlanarAlpha;
    DWORD SampleData;
    } 	DXVA2_VideoSample;

#endif  // _D3D9_H_
typedef struct _DXVA2_ValueRange
    {
    DXVA2_Fixed32 MinValue;
    DXVA2_Fixed32 MaxValue;
    DXVA2_Fixed32 DefaultValue;
    DXVA2_Fixed32 StepSize;
    } 	DXVA2_ValueRange;

typedef struct _DXVA2_ProcAmpValues
    {
    DXVA2_Fixed32 Brightness;
    DXVA2_Fixed32 Contrast;
    DXVA2_Fixed32 Hue;
    DXVA2_Fixed32 Saturation;
    } 	DXVA2_ProcAmpValues;

typedef struct _DXVA2_FilterValues
    {
    DXVA2_Fixed32 Level;
    DXVA2_Fixed32 Threshold;
    DXVA2_Fixed32 Radius;
    } 	DXVA2_FilterValues;

typedef struct _DXVA2_VideoProcessBltParams
    {
    REFERENCE_TIME TargetFrame;
    RECT TargetRect;
    SIZE ConstrictionSize;
    UINT StreamingFlags;
    DXVA2_AYUVSample16 BackgroundColor;
    DXVA2_ExtendedFormat DestFormat;
    DXVA2_ProcAmpValues ProcAmpValues;
    DXVA2_Fixed32 Alpha;
    DXVA2_FilterValues NoiseFilterLuma;
    DXVA2_FilterValues NoiseFilterChroma;
    DXVA2_FilterValues DetailFilterLuma;
    DXVA2_FilterValues DetailFilterChroma;
    DWORD DestData;
    } 	DXVA2_VideoProcessBltParams;


enum __MIDL___MIDL_itf_dxva2api_0000_0000_0012
    {	DXVA2_PictureParametersBufferType	= 0,
	DXVA2_MacroBlockControlBufferType	= 1,
	DXVA2_ResidualDifferenceBufferType	= 2,
	DXVA2_DeblockingControlBufferType	= 3,
	DXVA2_InverseQuantizationMatrixBufferType	= 4,
	DXVA2_SliceControlBufferType	= 5,
	DXVA2_BitStreamDateBufferType	= 6,
	DXVA2_MotionVectorBuffer	= 7,
	DXVA2_FilmGrainBuffer	= 8
    } ;

enum __MIDL___MIDL_itf_dxva2api_0000_0000_0013
    {	DXVA2_VideoDecoderRenderTarget	= 0,
	DXVA2_VideoProcessorRenderTarget	= 1,
	DXVA2_VideoSoftwareRenderTarget	= 2
    } ;
typedef struct _DXVA2_ConfigPictureDecode
    {
    GUID guidConfigBitstreamEncryption;
    GUID guidConfigMBcontrolEncryption;
    GUID guidConfigResidDiffEncryption;
    UINT ConfigBitstreamRaw;
    UINT ConfigMBcontrolRasterOrder;
    UINT ConfigResidDiffHost;
    UINT ConfigSpatialResid8;
    UINT ConfigResid8Subtraction;
    UINT ConfigSpatialHost8or9Clipping;
    UINT ConfigSpatialResidInterleaved;
    UINT ConfigIntraResidUnsigned;
    UINT ConfigResidDiffAccelerator;
    UINT ConfigHostInverseScan;
    UINT ConfigSpecificIDCT;
    UINT Config4GroupedCoefs;
    USHORT ConfigMinRenderTargetBuffCount;
    USHORT ConfigDecoderSpecific;
    } 	DXVA2_ConfigPictureDecode;

typedef struct _DXVA2_DecodeBufferDesc
    {
    DWORD CompressedBufferType;
    UINT BufferIndex;
    UINT DataOffset;
    UINT DataSize;
    UINT FirstMBaddress;
    UINT NumMBsInBuffer;
    UINT Width;
    UINT Height;
    UINT Stride;
    UINT ReservedBits;
    PVOID pvPVPState;
    } 	DXVA2_DecodeBufferDesc;

typedef struct _DXVA2_DecodeExtensionData
    {
    UINT Function;
    PVOID pPrivateInputData;
    UINT PrivateInputDataSize;
    PVOID pPrivateOutputData;
    UINT PrivateOutputDataSize;
    } 	DXVA2_DecodeExtensionData;

typedef struct _DXVA2_DecodeExecuteParams
    {
    UINT NumCompBuffers;
    DXVA2_DecodeBufferDesc *pCompressedBuffers;
    DXVA2_DecodeExtensionData *pExtensionData;
    } 	DXVA2_DecodeExecuteParams;

#if defined(_D3D9_H_) || defined(_d3d9P_H_)








extern RPC_IF_HANDLE __MIDL_itf_dxva2api_0000_0000_v0_0_c_ifspec;
extern RPC_IF_HANDLE __MIDL_itf_dxva2api_0000_0000_v0_0_s_ifspec;

#ifndef __IDirect3DDeviceManager9_INTERFACE_DEFINED__
#define __IDirect3DDeviceManager9_INTERFACE_DEFINED__

/* interface IDirect3DDeviceManager9 */
/* [local][helpstring][uuid][object] */ 


EXTERN_C const IID IID_IDirect3DDeviceManager9;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("a0cade0f-06d5-4cf4-a1c7-f3cdd725aa75")
    IDirect3DDeviceManager9 : public IUnknown
    {
    public:
        virtual HRESULT STDMETHODCALLTYPE ResetDevice( 
            /* [in] */ 
            __in  IDirect3DDevice9 *pDevice,
            /* [in] */ 
            __in  UINT resetToken) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE OpenDeviceHandle( 
            /* [out] */ 
            __out  HANDLE *phDevice) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE CloseDeviceHandle( 
            /* [in] */ 
            __in  HANDLE hDevice) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE TestDevice( 
            /* [in] */ 
            __in  HANDLE hDevice) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE LockDevice( 
            /* [in] */ 
            __in  HANDLE hDevice,
            /* [out] */ 
            __deref_out  IDirect3DDevice9 **ppDevice,
            /* [in] */ 
            __in  BOOL fBlock) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE UnlockDevice( 
            /* [in] */ 
            __in  HANDLE hDevice,
            /* [in] */ 
            __in  BOOL fSaveState) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetVideoService( 
            /* [in] */ 
            __in  HANDLE hDevice,
            /* [in] */ 
            __in  REFIID riid,
            /* [out] */ 
            __deref_out  void **ppService) = 0;
        
    };
    
#else 	/* C style interface */

    typedef struct IDirect3DDeviceManager9Vtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            IDirect3DDeviceManager9 * This,
            /* [in] */ REFIID riid,
            /* [iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            IDirect3DDeviceManager9 * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            IDirect3DDeviceManager9 * This);
        
        HRESULT ( STDMETHODCALLTYPE *ResetDevice )( 
            IDirect3DDeviceManager9 * This,
            /* [in] */ 
            __in  IDirect3DDevice9 *pDevice,
            /* [in] */ 
            __in  UINT resetToken);
        
        HRESULT ( STDMETHODCALLTYPE *OpenDeviceHandle )( 
            IDirect3DDeviceManager9 * This,
            /* [out] */ 
            __out  HANDLE *phDevice);
        
        HRESULT ( STDMETHODCALLTYPE *CloseDeviceHandle )( 
            IDirect3DDeviceManager9 * This,
            /* [in] */ 
            __in  HANDLE hDevice);
        
        HRESULT ( STDMETHODCALLTYPE *TestDevice )( 
            IDirect3DDeviceManager9 * This,
            /* [in] */ 
            __in  HANDLE hDevice);
        
        HRESULT ( STDMETHODCALLTYPE *LockDevice )( 
            IDirect3DDeviceManager9 * This,
            /* [in] */ 
            __in  HANDLE hDevice,
            /* [out] */ 
            __deref_out  IDirect3DDevice9 **ppDevice,
            /* [in] */ 
            __in  BOOL fBlock);
        
        HRESULT ( STDMETHODCALLTYPE *UnlockDevice )( 
            IDirect3DDeviceManager9 * This,
            /* [in] */ 
            __in  HANDLE hDevice,
            /* [in] */ 
            __in  BOOL fSaveState);
        
        HRESULT ( STDMETHODCALLTYPE *GetVideoService )( 
            IDirect3DDeviceManager9 * This,
            /* [in] */ 
            __in  HANDLE hDevice,
            /* [in] */ 
            __in  REFIID riid,
            /* [out] */ 
            __deref_out  void **ppService);
        
        END_INTERFACE
    } IDirect3DDeviceManager9Vtbl;

    interface IDirect3DDeviceManager9
    {
        CONST_VTBL struct IDirect3DDeviceManager9Vtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IDirect3DDeviceManager9_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define IDirect3DDeviceManager9_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define IDirect3DDeviceManager9_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#define IDirect3DDeviceManager9_ResetDevice(This,pDevice,resetToken)	\
    ( (This)->lpVtbl -> ResetDevice(This,pDevice,resetToken) ) 

#define IDirect3DDeviceManager9_OpenDeviceHandle(This,phDevice)	\
    ( (This)->lpVtbl -> OpenDeviceHandle(This,phDevice) ) 

#define IDirect3DDeviceManager9_CloseDeviceHandle(This,hDevice)	\
    ( (This)->lpVtbl -> CloseDeviceHandle(This,hDevice) ) 

#define IDirect3DDeviceManager9_TestDevice(This,hDevice)	\
    ( (This)->lpVtbl -> TestDevice(This,hDevice) ) 

#define IDirect3DDeviceManager9_LockDevice(This,hDevice,ppDevice,fBlock)	\
    ( (This)->lpVtbl -> LockDevice(This,hDevice,ppDevice,fBlock) ) 

#define IDirect3DDeviceManager9_UnlockDevice(This,hDevice,fSaveState)	\
    ( (This)->lpVtbl -> UnlockDevice(This,hDevice,fSaveState) ) 

#define IDirect3DDeviceManager9_GetVideoService(This,hDevice,riid,ppService)	\
    ( (This)->lpVtbl -> GetVideoService(This,hDevice,riid,ppService) ) 

#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __IDirect3DDeviceManager9_INTERFACE_DEFINED__ */


#ifndef __IDirectXVideoAccelerationService_INTERFACE_DEFINED__
#define __IDirectXVideoAccelerationService_INTERFACE_DEFINED__

/* interface IDirectXVideoAccelerationService */
/* [local][helpstring][uuid][object] */ 


EXTERN_C const IID IID_IDirectXVideoAccelerationService;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("fc51a550-d5e7-11d9-af55-00054e43ff02")
    IDirectXVideoAccelerationService : public IUnknown
    {
    public:
        virtual HRESULT STDMETHODCALLTYPE CreateSurface( 
            /* [in] */ 
            __in  UINT Width,
            /* [in] */ 
            __in  UINT Height,
            /* [in] */ 
            __in  UINT BackBuffers,
            /* [in] */ 
            __in  D3DFORMAT Format,
            /* [in] */ 
            __in  D3DPOOL Pool,
            /* [in] */ 
            __in  DWORD Usage,
            /* [in] */ 
            __in  DWORD DxvaType,
            /* [size_is][out] */ 
            __out_ecount(BackBuffers+1)  IDirect3DSurface9 **ppSurface,
            /* [out][in] */ 
            __inout_opt  HANDLE *pSharedHandle) = 0;
        
    };
    
#else 	/* C style interface */

    typedef struct IDirectXVideoAccelerationServiceVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            IDirectXVideoAccelerationService * This,
            /* [in] */ REFIID riid,
            /* [iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            IDirectXVideoAccelerationService * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            IDirectXVideoAccelerationService * This);
        
        HRESULT ( STDMETHODCALLTYPE *CreateSurface )( 
            IDirectXVideoAccelerationService * This,
            /* [in] */ 
            __in  UINT Width,
            /* [in] */ 
            __in  UINT Height,
            /* [in] */ 
            __in  UINT BackBuffers,
            /* [in] */ 
            __in  D3DFORMAT Format,
            /* [in] */ 
            __in  D3DPOOL Pool,
            /* [in] */ 
            __in  DWORD Usage,
            /* [in] */ 
            __in  DWORD DxvaType,
            /* [size_is][out] */ 
            __out_ecount(BackBuffers+1)  IDirect3DSurface9 **ppSurface,
            /* [out][in] */ 
            __inout_opt  HANDLE *pSharedHandle);
        
        END_INTERFACE
    } IDirectXVideoAccelerationServiceVtbl;

    interface IDirectXVideoAccelerationService
    {
        CONST_VTBL struct IDirectXVideoAccelerationServiceVtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IDirectXVideoAccelerationService_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define IDirectXVideoAccelerationService_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define IDirectXVideoAccelerationService_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#define IDirectXVideoAccelerationService_CreateSurface(This,Width,Height,BackBuffers,Format,Pool,Usage,DxvaType,ppSurface,pSharedHandle)	\
    ( (This)->lpVtbl -> CreateSurface(This,Width,Height,BackBuffers,Format,Pool,Usage,DxvaType,ppSurface,pSharedHandle) ) 

#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __IDirectXVideoAccelerationService_INTERFACE_DEFINED__ */


#ifndef __IDirectXVideoDecoderService_INTERFACE_DEFINED__
#define __IDirectXVideoDecoderService_INTERFACE_DEFINED__

/* interface IDirectXVideoDecoderService */
/* [local][helpstring][uuid][object] */ 


EXTERN_C const IID IID_IDirectXVideoDecoderService;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("fc51a551-d5e7-11d9-af55-00054e43ff02")
    IDirectXVideoDecoderService : public IDirectXVideoAccelerationService
    {
    public:
        virtual HRESULT STDMETHODCALLTYPE GetDecoderDeviceGuids( 
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  GUID **pGuids) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetDecoderRenderTargets( 
            /* [in] */ 
            __in  REFGUID Guid,
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  D3DFORMAT **pFormats) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetDecoderConfigurations( 
            /* [in] */ 
            __in  REFGUID Guid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __reserved  void *pReserved,
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  DXVA2_ConfigPictureDecode **ppConfigs) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE CreateVideoDecoder( 
            /* [in] */ 
            __in  REFGUID Guid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  const DXVA2_ConfigPictureDecode *pConfig,
            /* [size_is][in] */ 
            __in_ecount(NumRenderTargets)  IDirect3DSurface9 **ppDecoderRenderTargets,
            /* [in] */ 
            __in  UINT NumRenderTargets,
            /* [out] */ 
            __deref_out  IDirectXVideoDecoder **ppDecode) = 0;
        
    };
    
#else 	/* C style interface */

    typedef struct IDirectXVideoDecoderServiceVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            IDirectXVideoDecoderService * This,
            /* [in] */ REFIID riid,
            /* [iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            IDirectXVideoDecoderService * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            IDirectXVideoDecoderService * This);
        
        HRESULT ( STDMETHODCALLTYPE *CreateSurface )( 
            IDirectXVideoDecoderService * This,
            /* [in] */ 
            __in  UINT Width,
            /* [in] */ 
            __in  UINT Height,
            /* [in] */ 
            __in  UINT BackBuffers,
            /* [in] */ 
            __in  D3DFORMAT Format,
            /* [in] */ 
            __in  D3DPOOL Pool,
            /* [in] */ 
            __in  DWORD Usage,
            /* [in] */ 
            __in  DWORD DxvaType,
            /* [size_is][out] */ 
            __out_ecount(BackBuffers+1)  IDirect3DSurface9 **ppSurface,
            /* [out][in] */ 
            __inout_opt  HANDLE *pSharedHandle);
        
        HRESULT ( STDMETHODCALLTYPE *GetDecoderDeviceGuids )( 
            IDirectXVideoDecoderService * This,
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  GUID **pGuids);
        
        HRESULT ( STDMETHODCALLTYPE *GetDecoderRenderTargets )( 
            IDirectXVideoDecoderService * This,
            /* [in] */ 
            __in  REFGUID Guid,
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  D3DFORMAT **pFormats);
        
        HRESULT ( STDMETHODCALLTYPE *GetDecoderConfigurations )( 
            IDirectXVideoDecoderService * This,
            /* [in] */ 
            __in  REFGUID Guid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __reserved  void *pReserved,
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  DXVA2_ConfigPictureDecode **ppConfigs);
        
        HRESULT ( STDMETHODCALLTYPE *CreateVideoDecoder )( 
            IDirectXVideoDecoderService * This,
            /* [in] */ 
            __in  REFGUID Guid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  const DXVA2_ConfigPictureDecode *pConfig,
            /* [size_is][in] */ 
            __in_ecount(NumRenderTargets)  IDirect3DSurface9 **ppDecoderRenderTargets,
            /* [in] */ 
            __in  UINT NumRenderTargets,
            /* [out] */ 
            __deref_out  IDirectXVideoDecoder **ppDecode);
        
        END_INTERFACE
    } IDirectXVideoDecoderServiceVtbl;

    interface IDirectXVideoDecoderService
    {
        CONST_VTBL struct IDirectXVideoDecoderServiceVtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IDirectXVideoDecoderService_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define IDirectXVideoDecoderService_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define IDirectXVideoDecoderService_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#define IDirectXVideoDecoderService_CreateSurface(This,Width,Height,BackBuffers,Format,Pool,Usage,DxvaType,ppSurface,pSharedHandle)	\
    ( (This)->lpVtbl -> CreateSurface(This,Width,Height,BackBuffers,Format,Pool,Usage,DxvaType,ppSurface,pSharedHandle) ) 


#define IDirectXVideoDecoderService_GetDecoderDeviceGuids(This,pCount,pGuids)	\
    ( (This)->lpVtbl -> GetDecoderDeviceGuids(This,pCount,pGuids) ) 

#define IDirectXVideoDecoderService_GetDecoderRenderTargets(This,Guid,pCount,pFormats)	\
    ( (This)->lpVtbl -> GetDecoderRenderTargets(This,Guid,pCount,pFormats) ) 

#define IDirectXVideoDecoderService_GetDecoderConfigurations(This,Guid,pVideoDesc,pReserved,pCount,ppConfigs)	\
    ( (This)->lpVtbl -> GetDecoderConfigurations(This,Guid,pVideoDesc,pReserved,pCount,ppConfigs) ) 

#define IDirectXVideoDecoderService_CreateVideoDecoder(This,Guid,pVideoDesc,pConfig,ppDecoderRenderTargets,NumRenderTargets,ppDecode)	\
    ( (This)->lpVtbl -> CreateVideoDecoder(This,Guid,pVideoDesc,pConfig,ppDecoderRenderTargets,NumRenderTargets,ppDecode) ) 

#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __IDirectXVideoDecoderService_INTERFACE_DEFINED__ */


#ifndef __IDirectXVideoProcessorService_INTERFACE_DEFINED__
#define __IDirectXVideoProcessorService_INTERFACE_DEFINED__

/* interface IDirectXVideoProcessorService */
/* [local][helpstring][uuid][object] */ 


EXTERN_C const IID IID_IDirectXVideoProcessorService;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("fc51a552-d5e7-11d9-af55-00054e43ff02")
    IDirectXVideoProcessorService : public IDirectXVideoAccelerationService
    {
    public:
        virtual HRESULT STDMETHODCALLTYPE RegisterVideoProcessorSoftwareDevice( 
            /* [in] */ 
            __in  void *pCallbacks) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetVideoProcessorDeviceGuids( 
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  GUID **pGuids) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetVideoProcessorRenderTargets( 
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  D3DFORMAT **pFormats) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetVideoProcessorSubStreamFormats( 
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  D3DFORMAT RenderTargetFormat,
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  D3DFORMAT **pFormats) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetVideoProcessorCaps( 
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  D3DFORMAT RenderTargetFormat,
            /* [out] */ 
            __out  DXVA2_VideoProcessorCaps *pCaps) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetProcAmpRange( 
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  D3DFORMAT RenderTargetFormat,
            /* [in] */ 
            __in  UINT ProcAmpCap,
            /* [out] */ 
            __out  DXVA2_ValueRange *pRange) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetFilterPropertyRange( 
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  D3DFORMAT RenderTargetFormat,
            /* [in] */ 
            __in  UINT FilterSetting,
            /* [out] */ 
            __out  DXVA2_ValueRange *pRange) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE CreateVideoProcessor( 
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  D3DFORMAT RenderTargetFormat,
            /* [in] */ 
            __in  UINT MaxNumSubStreams,
            /* [out] */ 
            __deref_out  IDirectXVideoProcessor **ppVidProcess) = 0;
        
    };
    
#else 	/* C style interface */

    typedef struct IDirectXVideoProcessorServiceVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            IDirectXVideoProcessorService * This,
            /* [in] */ REFIID riid,
            /* [iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            IDirectXVideoProcessorService * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            IDirectXVideoProcessorService * This);
        
        HRESULT ( STDMETHODCALLTYPE *CreateSurface )( 
            IDirectXVideoProcessorService * This,
            /* [in] */ 
            __in  UINT Width,
            /* [in] */ 
            __in  UINT Height,
            /* [in] */ 
            __in  UINT BackBuffers,
            /* [in] */ 
            __in  D3DFORMAT Format,
            /* [in] */ 
            __in  D3DPOOL Pool,
            /* [in] */ 
            __in  DWORD Usage,
            /* [in] */ 
            __in  DWORD DxvaType,
            /* [size_is][out] */ 
            __out_ecount(BackBuffers+1)  IDirect3DSurface9 **ppSurface,
            /* [out][in] */ 
            __inout_opt  HANDLE *pSharedHandle);
        
        HRESULT ( STDMETHODCALLTYPE *RegisterVideoProcessorSoftwareDevice )( 
            IDirectXVideoProcessorService * This,
            /* [in] */ 
            __in  void *pCallbacks);
        
        HRESULT ( STDMETHODCALLTYPE *GetVideoProcessorDeviceGuids )( 
            IDirectXVideoProcessorService * This,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  GUID **pGuids);
        
        HRESULT ( STDMETHODCALLTYPE *GetVideoProcessorRenderTargets )( 
            IDirectXVideoProcessorService * This,
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  D3DFORMAT **pFormats);
        
        HRESULT ( STDMETHODCALLTYPE *GetVideoProcessorSubStreamFormats )( 
            IDirectXVideoProcessorService * This,
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  D3DFORMAT RenderTargetFormat,
            /* [out] */ 
            __out  UINT *pCount,
            /* [size_is][unique][out] */ 
            __deref_out_ecount_opt(*pCount)  D3DFORMAT **pFormats);
        
        HRESULT ( STDMETHODCALLTYPE *GetVideoProcessorCaps )( 
            IDirectXVideoProcessorService * This,
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  D3DFORMAT RenderTargetFormat,
            /* [out] */ 
            __out  DXVA2_VideoProcessorCaps *pCaps);
        
        HRESULT ( STDMETHODCALLTYPE *GetProcAmpRange )( 
            IDirectXVideoProcessorService * This,
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  D3DFORMAT RenderTargetFormat,
            /* [in] */ 
            __in  UINT ProcAmpCap,
            /* [out] */ 
            __out  DXVA2_ValueRange *pRange);
        
        HRESULT ( STDMETHODCALLTYPE *GetFilterPropertyRange )( 
            IDirectXVideoProcessorService * This,
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  D3DFORMAT RenderTargetFormat,
            /* [in] */ 
            __in  UINT FilterSetting,
            /* [out] */ 
            __out  DXVA2_ValueRange *pRange);
        
        HRESULT ( STDMETHODCALLTYPE *CreateVideoProcessor )( 
            IDirectXVideoProcessorService * This,
            /* [in] */ 
            __in  REFGUID VideoProcDeviceGuid,
            /* [in] */ 
            __in  const DXVA2_VideoDesc *pVideoDesc,
            /* [in] */ 
            __in  D3DFORMAT RenderTargetFormat,
            /* [in] */ 
            __in  UINT MaxNumSubStreams,
            /* [out] */ 
            __deref_out  IDirectXVideoProcessor **ppVidProcess);
        
        END_INTERFACE
    } IDirectXVideoProcessorServiceVtbl;

    interface IDirectXVideoProcessorService
    {
        CONST_VTBL struct IDirectXVideoProcessorServiceVtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IDirectXVideoProcessorService_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define IDirectXVideoProcessorService_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define IDirectXVideoProcessorService_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#define IDirectXVideoProcessorService_CreateSurface(This,Width,Height,BackBuffers,Format,Pool,Usage,DxvaType,ppSurface,pSharedHandle)	\
    ( (This)->lpVtbl -> CreateSurface(This,Width,Height,BackBuffers,Format,Pool,Usage,DxvaType,ppSurface,pSharedHandle) ) 


#define IDirectXVideoProcessorService_RegisterVideoProcessorSoftwareDevice(This,pCallbacks)	\
    ( (This)->lpVtbl -> RegisterVideoProcessorSoftwareDevice(This,pCallbacks) ) 

#define IDirectXVideoProcessorService_GetVideoProcessorDeviceGuids(This,pVideoDesc,pCount,pGuids)	\
    ( (This)->lpVtbl -> GetVideoProcessorDeviceGuids(This,pVideoDesc,pCount,pGuids) ) 

#define IDirectXVideoProcessorService_GetVideoProcessorRenderTargets(This,VideoProcDeviceGuid,pVideoDesc,pCount,pFormats)	\
    ( (This)->lpVtbl -> GetVideoProcessorRenderTargets(This,VideoProcDeviceGuid,pVideoDesc,pCount,pFormats) ) 

#define IDirectXVideoProcessorService_GetVideoProcessorSubStreamFormats(This,VideoProcDeviceGuid,pVideoDesc,RenderTargetFormat,pCount,pFormats)	\
    ( (This)->lpVtbl -> GetVideoProcessorSubStreamFormats(This,VideoProcDeviceGuid,pVideoDesc,RenderTargetFormat,pCount,pFormats) ) 

#define IDirectXVideoProcessorService_GetVideoProcessorCaps(This,VideoProcDeviceGuid,pVideoDesc,RenderTargetFormat,pCaps)	\
    ( (This)->lpVtbl -> GetVideoProcessorCaps(This,VideoProcDeviceGuid,pVideoDesc,RenderTargetFormat,pCaps) ) 

#define IDirectXVideoProcessorService_GetProcAmpRange(This,VideoProcDeviceGuid,pVideoDesc,RenderTargetFormat,ProcAmpCap,pRange)	\
    ( (This)->lpVtbl -> GetProcAmpRange(This,VideoProcDeviceGuid,pVideoDesc,RenderTargetFormat,ProcAmpCap,pRange) ) 

#define IDirectXVideoProcessorService_GetFilterPropertyRange(This,VideoProcDeviceGuid,pVideoDesc,RenderTargetFormat,FilterSetting,pRange)	\
    ( (This)->lpVtbl -> GetFilterPropertyRange(This,VideoProcDeviceGuid,pVideoDesc,RenderTargetFormat,FilterSetting,pRange) ) 

#define IDirectXVideoProcessorService_CreateVideoProcessor(This,VideoProcDeviceGuid,pVideoDesc,RenderTargetFormat,MaxNumSubStreams,ppVidProcess)	\
    ( (This)->lpVtbl -> CreateVideoProcessor(This,VideoProcDeviceGuid,pVideoDesc,RenderTargetFormat,MaxNumSubStreams,ppVidProcess) ) 

#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __IDirectXVideoProcessorService_INTERFACE_DEFINED__ */


#ifndef __IDirectXVideoDecoder_INTERFACE_DEFINED__
#define __IDirectXVideoDecoder_INTERFACE_DEFINED__

/* interface IDirectXVideoDecoder */
/* [local][helpstring][uuid][object] */ 


EXTERN_C const IID IID_IDirectXVideoDecoder;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("f2b0810a-fd00-43c9-918c-df94e2d8ef7d")
    IDirectXVideoDecoder : public IUnknown
    {
    public:
        virtual HRESULT STDMETHODCALLTYPE GetVideoDecoderService( 
            /* [out] */ 
            __deref_out  IDirectXVideoDecoderService **ppService) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetCreationParameters( 
            /* [out] */ 
            __out_opt  GUID *pDeviceGuid,
            /* [out] */ 
            __out_opt  DXVA2_VideoDesc *pVideoDesc,
            /* [out] */ 
            __out_opt  DXVA2_ConfigPictureDecode *pConfig,
            /* [size_is][unique][out] */ 
            __out_ecount(*pNumSurfaces)  IDirect3DSurface9 ***pDecoderRenderTargets,
            /* [out] */ 
            __out_opt  UINT *pNumSurfaces) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetBuffer( 
            /* [in] */ 
            __in  UINT BufferType,
            /* [out] */ 
            __out  void **ppBuffer,
            /* [out] */ 
            __out  UINT *pBufferSize) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE ReleaseBuffer( 
            /* [in] */ 
            __in  UINT BufferType) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE BeginFrame( 
            /* [in] */ 
            __in  IDirect3DSurface9 *pRenderTarget,
            /* [in] */ 
            __in_opt  void *pvPVPData) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE EndFrame( 
            /* [out] */ 
            __inout_opt  HANDLE *pHandleComplete) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE Execute( 
            /* [in] */ 
            __in  const DXVA2_DecodeExecuteParams *pExecuteParams) = 0;
        
    };
    
#else 	/* C style interface */

    typedef struct IDirectXVideoDecoderVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            IDirectXVideoDecoder * This,
            /* [in] */ REFIID riid,
            /* [iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            IDirectXVideoDecoder * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            IDirectXVideoDecoder * This);
        
        HRESULT ( STDMETHODCALLTYPE *GetVideoDecoderService )( 
            IDirectXVideoDecoder * This,
            /* [out] */ 
            __deref_out  IDirectXVideoDecoderService **ppService);
        
        HRESULT ( STDMETHODCALLTYPE *GetCreationParameters )( 
            IDirectXVideoDecoder * This,
            /* [out] */ 
            __out_opt  GUID *pDeviceGuid,
            /* [out] */ 
            __out_opt  DXVA2_VideoDesc *pVideoDesc,
            /* [out] */ 
            __out_opt  DXVA2_ConfigPictureDecode *pConfig,
            /* [size_is][unique][out] */ 
            __out_ecount(*pNumSurfaces)  IDirect3DSurface9 ***pDecoderRenderTargets,
            /* [out] */ 
            __out_opt  UINT *pNumSurfaces);
        
        HRESULT ( STDMETHODCALLTYPE *GetBuffer )( 
            IDirectXVideoDecoder * This,
            /* [in] */ 
            __in  UINT BufferType,
            /* [out] */ 
            __out  void **ppBuffer,
            /* [out] */ 
            __out  UINT *pBufferSize);
        
        HRESULT ( STDMETHODCALLTYPE *ReleaseBuffer )( 
            IDirectXVideoDecoder * This,
            /* [in] */ 
            __in  UINT BufferType);
        
        HRESULT ( STDMETHODCALLTYPE *BeginFrame )( 
            IDirectXVideoDecoder * This,
            /* [in] */ 
            __in  IDirect3DSurface9 *pRenderTarget,
            /* [in] */ 
            __in_opt  void *pvPVPData);
        
        HRESULT ( STDMETHODCALLTYPE *EndFrame )( 
            IDirectXVideoDecoder * This,
            /* [out] */ 
            __inout_opt  HANDLE *pHandleComplete);
        
        HRESULT ( STDMETHODCALLTYPE *Execute )( 
            IDirectXVideoDecoder * This,
            /* [in] */ 
            __in  const DXVA2_DecodeExecuteParams *pExecuteParams);
        
        END_INTERFACE
    } IDirectXVideoDecoderVtbl;

    interface IDirectXVideoDecoder
    {
        CONST_VTBL struct IDirectXVideoDecoderVtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IDirectXVideoDecoder_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define IDirectXVideoDecoder_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define IDirectXVideoDecoder_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#define IDirectXVideoDecoder_GetVideoDecoderService(This,ppService)	\
    ( (This)->lpVtbl -> GetVideoDecoderService(This,ppService) ) 

#define IDirectXVideoDecoder_GetCreationParameters(This,pDeviceGuid,pVideoDesc,pConfig,pDecoderRenderTargets,pNumSurfaces)	\
    ( (This)->lpVtbl -> GetCreationParameters(This,pDeviceGuid,pVideoDesc,pConfig,pDecoderRenderTargets,pNumSurfaces) ) 

#define IDirectXVideoDecoder_GetBuffer(This,BufferType,ppBuffer,pBufferSize)	\
    ( (This)->lpVtbl -> GetBuffer(This,BufferType,ppBuffer,pBufferSize) ) 

#define IDirectXVideoDecoder_ReleaseBuffer(This,BufferType)	\
    ( (This)->lpVtbl -> ReleaseBuffer(This,BufferType) ) 

#define IDirectXVideoDecoder_BeginFrame(This,pRenderTarget,pvPVPData)	\
    ( (This)->lpVtbl -> BeginFrame(This,pRenderTarget,pvPVPData) ) 

#define IDirectXVideoDecoder_EndFrame(This,pHandleComplete)	\
    ( (This)->lpVtbl -> EndFrame(This,pHandleComplete) ) 

#define IDirectXVideoDecoder_Execute(This,pExecuteParams)	\
    ( (This)->lpVtbl -> Execute(This,pExecuteParams) ) 

#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __IDirectXVideoDecoder_INTERFACE_DEFINED__ */


#ifndef __IDirectXVideoProcessor_INTERFACE_DEFINED__
#define __IDirectXVideoProcessor_INTERFACE_DEFINED__

/* interface IDirectXVideoProcessor */
/* [local][helpstring][uuid][object] */ 


EXTERN_C const IID IID_IDirectXVideoProcessor;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("8c3a39f0-916e-4690-804f-4c8001355d25")
    IDirectXVideoProcessor : public IUnknown
    {
    public:
        virtual HRESULT STDMETHODCALLTYPE GetVideoProcessorService( 
            /* [out] */ 
            __deref_out  IDirectXVideoProcessorService **ppService) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetCreationParameters( 
            /* [out] */ 
            __out_opt  GUID *pDeviceGuid,
            /* [out] */ 
            __out_opt  DXVA2_VideoDesc *pVideoDesc,
            /* [out] */ 
            __out_opt  D3DFORMAT *pRenderTargetFormat,
            /* [out] */ 
            __out_opt  UINT *pMaxNumSubStreams) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetVideoProcessorCaps( 
            /* [out] */ 
            __out  DXVA2_VideoProcessorCaps *pCaps) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetProcAmpRange( 
            /* [in] */ 
            __in  UINT ProcAmpCap,
            /* [out] */ 
            __out  DXVA2_ValueRange *pRange) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE GetFilterPropertyRange( 
            /* [in] */ 
            __in  UINT FilterSetting,
            /* [out] */ 
            __out  DXVA2_ValueRange *pRange) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE VideoProcessBlt( 
            /* [in] */ 
            __in  IDirect3DSurface9 *pRenderTarget,
            /* [in] */ 
            __in  const DXVA2_VideoProcessBltParams *pBltParams,
            /* [size_is][in] */ 
            __in_ecount(NumSamples)  const DXVA2_VideoSample *pSamples,
            /* [in] */ 
            __in  UINT NumSamples,
            /* [out] */ 
            __inout_opt  HANDLE *pHandleComplete) = 0;
        
    };
    
#else 	/* C style interface */

    typedef struct IDirectXVideoProcessorVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            IDirectXVideoProcessor * This,
            /* [in] */ REFIID riid,
            /* [iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            IDirectXVideoProcessor * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            IDirectXVideoProcessor * This);
        
        HRESULT ( STDMETHODCALLTYPE *GetVideoProcessorService )( 
            IDirectXVideoProcessor * This,
            /* [out] */ 
            __deref_out  IDirectXVideoProcessorService **ppService);
        
        HRESULT ( STDMETHODCALLTYPE *GetCreationParameters )( 
            IDirectXVideoProcessor * This,
            /* [out] */ 
            __out_opt  GUID *pDeviceGuid,
            /* [out] */ 
            __out_opt  DXVA2_VideoDesc *pVideoDesc,
            /* [out] */ 
            __out_opt  D3DFORMAT *pRenderTargetFormat,
            /* [out] */ 
            __out_opt  UINT *pMaxNumSubStreams);
        
        HRESULT ( STDMETHODCALLTYPE *GetVideoProcessorCaps )( 
            IDirectXVideoProcessor * This,
            /* [out] */ 
            __out  DXVA2_VideoProcessorCaps *pCaps);
        
        HRESULT ( STDMETHODCALLTYPE *GetProcAmpRange )( 
            IDirectXVideoProcessor * This,
            /* [in] */ 
            __in  UINT ProcAmpCap,
            /* [out] */ 
            __out  DXVA2_ValueRange *pRange);
        
        HRESULT ( STDMETHODCALLTYPE *GetFilterPropertyRange )( 
            IDirectXVideoProcessor * This,
            /* [in] */ 
            __in  UINT FilterSetting,
            /* [out] */ 
            __out  DXVA2_ValueRange *pRange);
        
        HRESULT ( STDMETHODCALLTYPE *VideoProcessBlt )( 
            IDirectXVideoProcessor * This,
            /* [in] */ 
            __in  IDirect3DSurface9 *pRenderTarget,
            /* [in] */ 
            __in  const DXVA2_VideoProcessBltParams *pBltParams,
            /* [size_is][in] */ 
            __in_ecount(NumSamples)  const DXVA2_VideoSample *pSamples,
            /* [in] */ 
            __in  UINT NumSamples,
            /* [out] */ 
            __inout_opt  HANDLE *pHandleComplete);
        
        END_INTERFACE
    } IDirectXVideoProcessorVtbl;

    interface IDirectXVideoProcessor
    {
        CONST_VTBL struct IDirectXVideoProcessorVtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IDirectXVideoProcessor_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define IDirectXVideoProcessor_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define IDirectXVideoProcessor_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#define IDirectXVideoProcessor_GetVideoProcessorService(This,ppService)	\
    ( (This)->lpVtbl -> GetVideoProcessorService(This,ppService) ) 

#define IDirectXVideoProcessor_GetCreationParameters(This,pDeviceGuid,pVideoDesc,pRenderTargetFormat,pMaxNumSubStreams)	\
    ( (This)->lpVtbl -> GetCreationParameters(This,pDeviceGuid,pVideoDesc,pRenderTargetFormat,pMaxNumSubStreams) ) 

#define IDirectXVideoProcessor_GetVideoProcessorCaps(This,pCaps)	\
    ( (This)->lpVtbl -> GetVideoProcessorCaps(This,pCaps) ) 

#define IDirectXVideoProcessor_GetProcAmpRange(This,ProcAmpCap,pRange)	\
    ( (This)->lpVtbl -> GetProcAmpRange(This,ProcAmpCap,pRange) ) 

#define IDirectXVideoProcessor_GetFilterPropertyRange(This,FilterSetting,pRange)	\
    ( (This)->lpVtbl -> GetFilterPropertyRange(This,FilterSetting,pRange) ) 

#define IDirectXVideoProcessor_VideoProcessBlt(This,pRenderTarget,pBltParams,pSamples,NumSamples,pHandleComplete)	\
    ( (This)->lpVtbl -> VideoProcessBlt(This,pRenderTarget,pBltParams,pSamples,NumSamples,pHandleComplete) ) 

#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __IDirectXVideoProcessor_INTERFACE_DEFINED__ */


/* interface __MIDL_itf_dxva2api_0000_0006 */
/* [local] */ 

typedef /* [public][public][public] */ 
enum __MIDL___MIDL_itf_dxva2api_0000_0006_0001
    {	DXVA2_SurfaceType_DecoderRenderTarget	= 0,
	DXVA2_SurfaceType_ProcessorRenderTarget	= 1,
	DXVA2_SurfaceType_D3DRenderTargetTexture	= 2
    } 	DXVA2_SurfaceType;



extern RPC_IF_HANDLE __MIDL_itf_dxva2api_0000_0006_v0_0_c_ifspec;
extern RPC_IF_HANDLE __MIDL_itf_dxva2api_0000_0006_v0_0_s_ifspec;

#ifndef __IDirectXVideoMemoryConfiguration_INTERFACE_DEFINED__
#define __IDirectXVideoMemoryConfiguration_INTERFACE_DEFINED__

/* interface IDirectXVideoMemoryConfiguration */
/* [local][helpstring][uuid][object] */ 


EXTERN_C const IID IID_IDirectXVideoMemoryConfiguration;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("b7f916dd-db3b-49c1-84d7-e45ef99ec726")
    IDirectXVideoMemoryConfiguration : public IUnknown
    {
    public:
        virtual HRESULT STDMETHODCALLTYPE GetAvailableSurfaceTypeByIndex( 
            /* [in] */ 
            __in  DWORD dwTypeIndex,
            /* [out] */ 
            __out  DXVA2_SurfaceType *pdwType) = 0;
        
        virtual HRESULT STDMETHODCALLTYPE SetSurfaceType( 
            /* [in] */ 
            __in  DXVA2_SurfaceType dwType) = 0;
        
    };
    
#else 	/* C style interface */

    typedef struct IDirectXVideoMemoryConfigurationVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE *QueryInterface )( 
            IDirectXVideoMemoryConfiguration * This,
            /* [in] */ REFIID riid,
            /* [iid_is][out] */ 
            __RPC__deref_out  void **ppvObject);
        
        ULONG ( STDMETHODCALLTYPE *AddRef )( 
            IDirectXVideoMemoryConfiguration * This);
        
        ULONG ( STDMETHODCALLTYPE *Release )( 
            IDirectXVideoMemoryConfiguration * This);
        
        HRESULT ( STDMETHODCALLTYPE *GetAvailableSurfaceTypeByIndex )( 
            IDirectXVideoMemoryConfiguration * This,
            /* [in] */ 
            __in  DWORD dwTypeIndex,
            /* [out] */ 
            __out  DXVA2_SurfaceType *pdwType);
        
        HRESULT ( STDMETHODCALLTYPE *SetSurfaceType )( 
            IDirectXVideoMemoryConfiguration * This,
            /* [in] */ 
            __in  DXVA2_SurfaceType dwType);
        
        END_INTERFACE
    } IDirectXVideoMemoryConfigurationVtbl;

    interface IDirectXVideoMemoryConfiguration
    {
        CONST_VTBL struct IDirectXVideoMemoryConfigurationVtbl *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IDirectXVideoMemoryConfiguration_QueryInterface(This,riid,ppvObject)	\
    ( (This)->lpVtbl -> QueryInterface(This,riid,ppvObject) ) 

#define IDirectXVideoMemoryConfiguration_AddRef(This)	\
    ( (This)->lpVtbl -> AddRef(This) ) 

#define IDirectXVideoMemoryConfiguration_Release(This)	\
    ( (This)->lpVtbl -> Release(This) ) 


#define IDirectXVideoMemoryConfiguration_GetAvailableSurfaceTypeByIndex(This,dwTypeIndex,pdwType)	\
    ( (This)->lpVtbl -> GetAvailableSurfaceTypeByIndex(This,dwTypeIndex,pdwType) ) 

#define IDirectXVideoMemoryConfiguration_SetSurfaceType(This,dwType)	\
    ( (This)->lpVtbl -> SetSurfaceType(This,dwType) ) 

#endif /* COBJMACROS */


#endif 	/* C style interface */




#endif 	/* __IDirectXVideoMemoryConfiguration_INTERFACE_DEFINED__ */


/* interface __MIDL_itf_dxva2api_0000_0007 */
/* [local] */ 

STDAPI DXVA2CreateDirect3DDeviceManager9(
    __out UINT* pResetToken,
    __deref_out IDirect3DDeviceManager9** ppDeviceManager
    );
STDAPI DXVA2CreateVideoService(
    __in IDirect3DDevice9* pDD,
    __in REFIID riid,
    __deref_out void** ppService
    );
#endif  // _D3D9_H_
__inline DXVA2_Fixed32 DXVA2FloatToFixed(__in const float _float_)
{
    DXVA2_Fixed32 _fixed_;
    _fixed_.Fraction = LOWORD(_float_ * 0x10000);
    _fixed_.Value = HIWORD(_float_ * 0x10000);
    return _fixed_;
}
__inline float DXVA2FixedToFloat(__in const DXVA2_Fixed32 _fixed_)
{
    return (FLOAT)_fixed_.Value + (FLOAT)_fixed_.Fraction / 0x10000;
}
__inline const DXVA2_Fixed32 DXVA2_Fixed32TransparentAlpha()
{
    DXVA2_Fixed32 _fixed_ = {0, 0};
    return _fixed_;
}
__inline const DXVA2_Fixed32 DXVA2_Fixed32OpaqueAlpha()
{
    DXVA2_Fixed32 _fixed_ = {0, 1};
    return _fixed_;
}
#endif


extern RPC_IF_HANDLE __MIDL_itf_dxva2api_0000_0007_v0_0_c_ifspec;
extern RPC_IF_HANDLE __MIDL_itf_dxva2api_0000_0007_v0_0_s_ifspec;

/* Additional Prototypes for ALL interfaces */

/* end of Additional Prototypes */

#ifdef __cplusplus
}
#endif

#endif



