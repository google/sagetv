/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef _H_INCLUDE_DSHOWUTILS_H_
#define _H_INCLUDE_DSHOWUTILS_H_

#include <math.h>
#include <streams.h>
#include <ks.h>
#include "DShowCapture.h"

#define WAIT_FOR_COMPLETIONS FALSE

inline jint convertVolumeLog(jint x)
{
	return (jint) floor(65535.0 * log((x*9.0/65535.0) + 1)/log(10.0));
}

inline jint convertVolumeAntilog(jint x)
{
	return (jint) floor((65535.0/9.0) * (pow(10.0, x/65535.0) - 1));
}

typedef struct SageTVMPEG2EncodingParameters
{
	int audiooutputmode;// = 0; 
	int audiocrc;// = 0;
	int gopsize;// = 15;
	int videobitrate;// = 4000000;
	int peakvideobitrate;//new Integer(5000000);
	int inversetelecine;// = 0;
	int closedgop;// = 0;
	int vbr;// = 0;
	int outputstreamtype;// = 0;
	int width;// = 720;
	int height;// = 480;
	int audiobitrate;// = 384;
	int audiosampling;// = 48000;

	int disablefilter; // 1
	int medianfilter; // 3
	/*int mediancoringlumahi; // 0
	int mediancoringlumalo; // 255
	int mediancoringchromahi; // 0 to 255 (default 0)
	int mediancoringchromalo; // 0 to 255 (default 255)
	int lumaspatialflt; // 3 enum SPATIAL_FILTER_LUMA
	int chromaspatialflt; // 1 enum SPATIAL_FILTER_CHROMA
	int dnrmode; // 3 enum DNR_MODE
	int dnrspatialfltlevel;// 0 to 15 (default 0) - used in static mode only
	int dnrtemporalfltlevel;// 0 to 15 (default 0) - used in static mode only
	int dnrsmoothfactor;      // 0 to 255 (default 200)
	int dnr_ntlf_max_y;       // 0 to 15 (default 15) - max NTLF Luma
	int dnr_ntlf_max_uv;      // 0 to 15 (default 15) - max NTLF Chroma
	int dnrtemporalmultfactor;// 0 to 255 (default 48) - temporal filter multplier factor
	int dnrtemporaladdfactor; // 0 to 15 (default 4) - temporal filter add factor
	int dnrspatialmultfactor; // 0 to 255 (default 21) - spatial filter multiplier factor
	int dnrspatialsubfactor;  // 0 to 15 (default 2) - spatial filter sub factor
	int lumanltflevel;        // (default 0) 0 to 15
	int lumanltfcoeffindex;   // 0 to 63 (default 0) 
	int lumanltfcoeffvalue;   // 0 to 255 (default 0) 
	int vimzoneheight;        // 0 to 15 (default 2)*/

	int fps; // 30, 25 or 15
	int ipb; // 0,1 is ipb, 1 is i, 2 is ip
	int deinterlace;
	int aspectratio; // 0 is 1:1, 1 is 4:3 and 2 is 16:9
} SageTVMPEG2EncodingParameters;

// For setting the video format on the 656 pins
#ifndef mmioFOURCC
#define mmioFOURCC( ch0, ch1, ch2, ch3 )				\
		( (DWORD)(BYTE)(ch0) | ( (DWORD)(BYTE)(ch1) << 8 ) |	\
		( (DWORD)(BYTE)(ch2) << 16 ) | ( (DWORD)(BYTE)(ch3) << 24 ) )
#endif

#define FOURCC_YUV420       mmioFOURCC('I', 'Y', 'U', 'V')

HRESULT AddToRot(IUnknown *pUnkGraph, DWORD *pdwRegister);

void RemoveFromRot(DWORD pdwRegister);

IPin* FindPinByName(IBaseFilter* pFilter, jstring jPinName, JNIEnv* env, PIN_DIRECTION dir);

IPin* FindPinByName(IBaseFilter* pFilter, LPCSTR szPinName, PIN_DIRECTION dir);


HRESULT FindCaptureDevice(IBaseFilter **, int, REFCLSID);

HRESULT FindFilterByName(IBaseFilter** pFilter, REFCLSID filterCat,
	const char* filterName, int deviceIndex = 0, char* ppDeviceName = NULL, int nDeviceNameSize=0 );

HRESULT FindFilterByName2(IBaseFilter** pFilter, REFCLSID filterCat,
	const char* filterName, int deviceIndex, char* ppFriendName, int nDeviceNameSize=0 );

HRESULT FindFilterByDeviceName(IBaseFilter** pFilter, REFCLSID filterCat,
	int deviceIndex, char* pDeviceName);

HRESULT GetPin( IBaseFilter * pFilter, PIN_DIRECTION dirrequired, int iNum, IPin **ppPin);

HRESULT ConnectPins(IGraphBuilder* pGraph, IBaseFilter* outputFilter, 
					const char* outPinName, IBaseFilter* inputFilter,
					const char* inPinName, BOOL directConnect = FALSE);

IPin* FindPin(IBaseFilter* pSrcVideoFilter, PIN_DIRECTION desiredDir, const GUID *majorType, const GUID *subType);

IPin* FindUnconnectedPin(IBaseFilter* pSrcVideoFilter, PIN_DIRECTION desiredDir, BOOL connectedInstead = FALSE);

IPin* FindPinByCategoryAndType(IBaseFilter* pFilter, const GUID* pinCategory, const GUID* majorType);

HRESULT GetPinMedium( KSMULTIPLE_ITEM **ppmi, IPin* pPin );
int  MatchMedium( KSMULTIPLE_ITEM *pmi1, KSMULTIPLE_ITEM *pmi2 );
void ReleaseMedium( KSMULTIPLE_ITEM *pmi );
int  CheckFilterMedium( IBaseFilter* pUpFilter, IBaseFilter* pDownFilter );

BOOL WaitForState(JNIEnv* env, IMediaControl* pMC, FILTER_STATE graphState);

void RemoveAllOutputFilters(IFilterGraph* pGraph, IBaseFilter* pFilter);

void DisplayPinMTInfo(JNIEnv *env, IPin *pPin);

void WaitForEvent(JNIEnv* env, IFilterGraph* pGraph, long evtCode);

IBaseFilter* FindRendererFilter(IGraphBuilder* pGraph, const GUID* testType);

inline IBaseFilter* FindAudioRendererFilter(IGraphBuilder* pGraph) { return FindRendererFilter(pGraph, &MEDIATYPE_Audio); }

inline IBaseFilter* FindVideoRendererFilter(IGraphBuilder* pGraph) { return FindRendererFilter(pGraph, &MEDIATYPE_Video); }

inline BOOL capMask(int capType, int testMask)
{
	return ((capType & testMask) == testMask);
}

typedef struct {
	char state;
	char device_class;
	char regkey[128];
	char device_type[12];
	char device_name[128];
	char device_inst[64];
	char vendor_id[5];
	//char venstring[16];
	char device_id[16];
	char subsys_name[32];
	char subsys_id[5];
	char rev_tag[16];
	char inst1[16];
	char inst2[16];
	char inst3[16];
	char hardware_loc[128];
	char mfg[64];
	char hardware_id[128];
	char inst_guid1[64];
	char inst_guid2[64];
	char full_name[256];
	char device_desc[128];
} DEVICE_DRV_INF;

typedef struct {
	char CaptureName[64];
	unsigned long Mask;
	char Param[80];
} VIRTUAL_TUNER;




HRESULT GetDeviceInfo( char*  pCaptureDevName, DEVICE_DRV_INF* pDrvInfo );
int GetTunerNum( JNIEnv *env,  char* devName, REFCLSID devClassid, DEVICE_DRV_INF* devLocation );

void throwEncodingException(JNIEnv* env, jint errCode, HRESULT hr);
void throwPlaybackException(JNIEnv* env, jint errCode, HRESULT hr);
void throwNPE(JNIEnv* env);

#define ENCEXCEPT_RET0(errCode) if(FAILED(hr)){slog((env,"Exception from line: %d\r\n", __LINE__));throwEncodingException(env, errCode, hr);return 0;}
#define ENCEXCEPT_RET(errCode) if(FAILED(hr)){slog((env,"Exception from line: %d\r\n", __LINE__));throwEncodingException(env, errCode, hr);return;}
#define PLAYEXCEPT_RET0(errCode) if(FAILED(hr)){slog((env,"Exception from line: %d\r\n", __LINE__));throwPlaybackException(env, errCode, hr);return 0;}
#define PLAYEXCEPT_RET(errCode) if(FAILED(hr)){slog((env,"Exception from line: %d\r\n", __LINE__));throwPlaybackException(env, errCode, hr);return;}
#define NPE_RET0(npeTest) if(!npeTest){throwNPE(env);return 0;}
#define NPE_RET(npeTest) if(!npeTest){throwNPE(env);return;}

#define DSHOWTAG  "DSHOW"
#define CHECK_TAG(x)  { if ( x && strcmp(x->TAG, "DSHOW") )  slog((env, "FATAL:CapInfo Data is damaged.  %s %d\r\n", __FILE__, __LINE__));  }
#define CHECK_TAG_CAPINFO  { if ( pCapInfo && strcmp(pCapInfo->TAG, "DSHOW") )  slog((env, "FATAL:CapInfo Data is damaged.  %s %d\r\n", __FILE__, __LINE__));  }
#define CHECK_TAG2(x)  { if ( x && strcmp(x->TAG, "DSHOW") )  slog(( "FATAL:CapInfo Data is damaged.  %s %d\r\n", __FILE__, __LINE__));  }
#define CHECK_TAG_CAPINFO2  { if ( pCapInfo && strcmp(pCapInfo->TAG, "DSHOW") )  slog(( "FATAL:CapInfo Data is damaged.  %s %d\r\n", __FILE__, __LINE__)); }


#define MUTEX350WAITTIME 45000

#define GETENCODERMUTEX if(mutex350Encoder){WaitForSingleObject(mutex350Encoder, MUTEX350WAITTIME);}
#define RELEASEENCODERMUTEX if(mutex350Encoder){ReleaseMutex(mutex350Encoder);}

#ifdef _DEBUG
#define TEST_AND_PRINT if (FAILED(hr)){ sysOutPrint(env, "NATIVE WARNING (non-FAILURE) line %d hr=0x%x\r\n", __LINE__, hr);}
#else
#define TEST_AND_PRINT 0;
#endif

HRESULT FindMatchingMedium(IPin *pPin, REGPINMEDIUM *pMedium, bool *pfMatch);
HRESULT FindFilterByPinMedium(IBaseFilter** ppFilter, REFCLSID filterCat, IPin* srcPin);
HRESULT FindUpstreamFilter(IBaseFilter** ppFilter, REFCLSID filterCat, IPin* srcPin, char* pDeviceName);
HRESULT FindDownstreamFilter(IBaseFilter** ppFilter, REFCLSID filterCat, IPin* srcPin, char* pDeviceName);

HRESULT DestroyGraph(IGraphBuilder* pGraph);
void LogFilterGraphInfo(IGraphBuilder* pGraph);

inline BOOL IsNTSCVideoCode(DWORD x) { return x < AnalogVideo_PAL_B; }
inline BOOL IsPALVideoCode(DWORD x) { return   ((x >= AnalogVideo_PAL_B)   && (x < AnalogVideo_SECAM_B)); }
inline BOOL IsSECAMVideoCode(DWORD x) { return ((x >= AnalogVideo_SECAM_B) && ( x < AnalogVideo_PAL_N_COMBO )); }

IPin* FindVideoCaptureInputPin(IBaseFilter* pFilter);
DWORD GetRegistryDword(HKEY rootKey, const char* keyName, const char* valueName, DWORD defaultValue);
char* GetRegistryString(HKEY rootKey, const char* keyName, const char* valueName, char* szBuf, int dwSize );

HRESULT RenderStream(IGraphBuilder* pGraph, const GUID* pinCategory, const GUID* majorType, IBaseFilter* pCapFilter,
					 IBaseFilter* pCompFilter, IBaseFilter* pMuxFilter, IBaseFilter* pResync = NULL);
HRESULT RenderStreamDebug(IGraphBuilder* pGraph, const GUID* pinCategory, const GUID* majorType, IBaseFilter* pCapFilter,
					 IBaseFilter* pCompFilter, IBaseFilter* pMuxFilter);
HRESULT loadDivXFW(IBaseFilter **pFilter);

#include <bdatif.h>   //ZQ
#include <tuner.h>    //ZQ        
#include <tune.h>     //ZQ       

//#define SAFE_RELEASE_INTERFACE(x) { if (x) x.Release(); x = NULL; }  //ZQ

/*

HRESULT AddFilterByName(IGraphBuilder* piGraphBuilder, IBaseFilter **ppiFilter, CLSID clsidDeviceClass, LPCWSTR friendlyName); //ZQ
HRESULT AddFilter(IGraphBuilder* piGraphBuilder, REFCLSID rclsid, IBaseFilter **ppiFilter, LPCWSTR pName ); //ZQ
HRESULT CreateATSCTuneRequest( JNIEnv* env, CComPtr<ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
												long lMajorChannel, long lMinorChannel, long lPhysicalChannel ); //ZQ
HRESULT CreateDVBTTuneRequest( JNIEnv* env, CComPtr <ITuningSpace> piTuningSpace, CComPtr <ITuneRequest> &pExTuneRequest, 
												long frequency, long bandwidth); //ZQ
*/
HRESULT loadDivX(IBaseFilter **pFilter);
BOOL configureDivXVideoEncoder(IBaseFilter* pVidEnc, 
							 SageTVMPEG2EncodingParameters *pythonParams, JNIEnv *env,
							 BOOL aviEncoding);
DWORD ReadAdditionalSettingDWord( JNIEnv *env, DShowCaptureInfo* pCapInfo, char* ValueName, DWORD dwDefault );
char* ReadAdditionalSettingString( JNIEnv *env, char* CaptureFilterName, char* ValueName, char* szBuf, int dwSize );

BOOL DoesPinSupportMediaType(IPin* pPin, const GUID* majorType, const GUID* subType);
int  LoadVirtualTunerList(  JNIEnv *env, VIRTUAL_TUNER* pVirtalTunerList, int maxListNum );
BOOL IsVirtualTuner(  JNIEnv *env, const char* capFiltName  );
BOOL GetVirtualTunerDevName( JNIEnv *env, char* capFiltName, int CapFiltNum, char* BDAFiltDevName, int BDAFiltDevNameSize, 
							                   char* pParam, int dwParamSize, unsigned long* pMask );
char *GetClsidString( CLSID clsid, char* pBuf, int nBufSize  );
BOOL GetFilterDevName( JNIEnv *env, char* capFiltName, int CapFiltNum, char* BDAFiltDevName, int BDAFiltDevNameSize );
int GetOSversion( );
#endif //_H_INCLUDE_DSHOWUTILS_H_
