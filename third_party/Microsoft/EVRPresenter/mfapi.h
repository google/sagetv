//*@@@+++@@@@******************************************************************
//
// Microsoft Windows Media Foundation
// Copyright (C) Microsoft Corporation. All rights reserved.
//
//*@@@---@@@@******************************************************************
//

//
// MFAPI.h is the header containing the APIs for using the MF platform.
//

#pragma once
#if !defined(__MFAPI_H__)
#define __MFAPI_H__

#pragma pack(push, mfhrds)
#include <mfobjects.h>
#pragma pack(pop, mfhrds)

#include "mmreg.h"

#include <avrt.h>
#ifndef AVRT_DATA
#define AVRT_DATA
#endif
#ifndef AVRT_BSS
#define AVRT_BSS
#endif

#if !defined(MF_VERSION)
#define MF_SDK_VERSION 0x0001
#define MF_API_VERSION 0x0070 // Increment this whenever you change an API
#define MF_VERSION (MF_SDK_VERSION << 16 | MF_API_VERSION)
#endif

#define MFSTARTUP_NOSOCKET 0x1
#define MFSTARTUP_LITE (MFSTARTUP_NOSOCKET)
#define MFSTARTUP_FULL 0

#if defined(__cplusplus)
extern "C" {
#endif

////////////////////////////////////////////////////////////////////////////////
///////////////////////////////   Startup/Shutdown  ////////////////////////////
////////////////////////////////////////////////////////////////////////////////

//
// Initializes the platform object.
// Must be called before using Media Foundation.
// A matching MFShutdown call must be made when the application is done using
// Media Foundation.
// The "Version" parameter should be set to MF_API_VERSION.
// Application should not call MFStartup / MFShutdown from workqueue threads
//
#if defined(__cplusplus)
STDAPI MFStartup( ULONG Version, DWORD dwFlags = MFSTARTUP_FULL );
#else
STDAPI MFStartup( ULONG Version, DWORD dwFlags );
#endif

//
// Shuts down the platform object.
// Releases all resources including threads.
// Application should call MFShutdown the same number of times as MFStartup
// Application should not call MFStartup / MFShutdown from workqueue threads
//
STDAPI MFShutdown();


////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////    Platform    ///////////////////////////////
////////////////////////////////////////////////////////////////////////////////

//
// These functions can be used to keep the MF platform object in place.
// Every call to MFLockPlatform should have a matching call to MFUnlockPlatform
//
STDAPI MFLockPlatform();
STDAPI MFUnlockPlatform();

///////////////////////////////////////////////////////////////////////////////

//
// MF workitem functions
//
typedef unsigned __int64 MFWORKITEM_KEY;

STDAPI MFPutWorkItem(
            DWORD dwQueue,
            IMFAsyncCallback * pCallback,
            IUnknown * pState);

STDAPI MFPutWorkItemEx(
            DWORD dwQueue,
            IMFAsyncResult * pResult);

STDAPI MFScheduleWorkItem(
            IMFAsyncCallback * pCallback,
            IUnknown * pState,
            INT64 Timeout,
            MFWORKITEM_KEY * pKey);

STDAPI MFScheduleWorkItemEx(
            IMFAsyncResult * pResult,
            INT64 Timeout,
            MFWORKITEM_KEY * pKey);

//
//   The CancelWorkItem method is used by objects to cancel scheduled operation
//   Due to asynchronous nature of timers, application might still get a
//   timer callback after MFCancelWorkItem has returned.
//
STDAPI MFCancelWorkItem(
            MFWORKITEM_KEY Key);


///////////////////////////////////////////////////////////////////////////////

//
// MF periodic callbacks
//
STDAPI MFGetTimerPeriodicity(
            DWORD * Periodicity);

typedef void (*MFPERIODICCALLBACK)(IUnknown* pContext);

STDAPI MFAddPeriodicCallback(
            MFPERIODICCALLBACK Callback,
            IUnknown * pContext,
            DWORD * pdwKey);

STDAPI MFRemovePeriodicCallback(
            DWORD dwKey);

///////////////////////////////////////////////////////////////////////////////

//
// MF work queues
//
STDAPI MFAllocateWorkQueue(
            __out OUT DWORD * pdwWorkQueue);

STDAPI MFLockWorkQueue(
            IN DWORD dwWorkQueue);

STDAPI MFUnlockWorkQueue(
            IN DWORD dwWorkQueue);

STDAPI MFBeginRegisterWorkQueueWithMMCSS(
            DWORD dwWorkQueueId,
            __in LPCWSTR wszClass,
            DWORD dwTaskId,
            __in IMFAsyncCallback * pDoneCallback,
            __in IUnknown * pDoneState );

STDAPI MFEndRegisterWorkQueueWithMMCSS(
            __in IMFAsyncResult * pResult,
            __out DWORD * pdwTaskId );

STDAPI MFBeginUnregisterWorkQueueWithMMCSS(
            DWORD dwWorkQueueId,
            __in IMFAsyncCallback * pDoneCallback,
            __in IUnknown * pDoneState );

STDAPI MFEndUnregisterWorkQueueWithMMCSS(
            __in IMFAsyncResult * pResult );

STDAPI MFSetWorkQueueClass(
            DWORD dwWorkQueueId,
            LPCWSTR szClass );

STDAPI MFGetWorkQueueMMCSSClass(
            DWORD dwWorkQueueId,
            __out_ecount_part_opt(*pcchClass,*pcchClass)  LPWSTR pwszClass,
            __inout  DWORD *pcchClass );

STDAPI MFGetWorkQueueMMCSSTaskId(
            DWORD dwWorkQueueId,
            __out LPDWORD pdwTaskId );

///////////////////////////////////////////////////////////////////////////////
/////////////////////////////////    Async Model //////////////////////////////
///////////////////////////////////////////////////////////////////////////////

//
// Instantiates the MF-provided Async Result implementation
//
STDAPI MFCreateAsyncResult(
    IUnknown * punkObject,
    IMFAsyncCallback * pCallback,
    IUnknown * punkState,
    IMFAsyncResult ** ppAsyncResult );

//
// Helper for calling IMFAsyncCallback::Invoke
//
STDAPI MFInvokeCallback(
    IMFAsyncResult * pAsyncResult );


//
// MFASYNCRESULT struct.
// Any implementation of IMFAsyncResult must inherit from this struct;
// the Media Foundation workqueue implementation depends on this.
//
#if defined(__cplusplus) && !defined(CINTERFACE)
typedef struct tagMFASYNCRESULT : public IMFAsyncResult
{
    OVERLAPPED overlapped;
    IMFAsyncCallback * pCallback;
    HRESULT hrStatusResult;
    DWORD dwBytesTransferred;
    HANDLE hEvent;
}   MFASYNCRESULT;
#else /* C style interface */
typedef struct tagMFASYNCRESULT
{
    IMFAsyncResult AsyncResult;
    OVERLAPPED overlapped;
    IMFAsyncCallback * pCallback;
    HRESULT hrStatusResult;
    DWORD dwBytesTransferred;
    HANDLE hEvent;
}   MFASYNCRESULT;
#endif /* C style interface */


///////////////////////////////////////////////////////////////////////////////
/////////////////////////////////    Files       //////////////////////////////
///////////////////////////////////////////////////////////////////////////////

//
// Regardless of the access mode with which the file is opened, the sharing
// permissions will allow shared reading and deleting.
//
STDAPI MFCreateFile(
    MF_FILE_ACCESSMODE  AccessMode,
    MF_FILE_OPENMODE    OpenMode,
    MF_FILE_FLAGS       fFlags,
    LPCWSTR             pwszFileURL,
    IMFByteStream       **ppIByteStream );

STDAPI MFCreateTempFile(
    MF_FILE_ACCESSMODE  AccessMode,
    MF_FILE_OPENMODE    OpenMode,
    MF_FILE_FLAGS       fFlags,
    IMFByteStream       **ppIByteStream );

STDAPI MFBeginCreateFile(
    IN  MF_FILE_ACCESSMODE  AccessMode,
    IN  MF_FILE_OPENMODE    OpenMode,
    IN  MF_FILE_FLAGS       fFlags,
    IN  LPCWSTR             pwszFilePath,
    IN  IMFAsyncCallback *  pCallback,
    IN  IUnknown *          pState,
    OUT IUnknown ** ppCancelCookie);

STDAPI MFEndCreateFile(
    IN IMFAsyncResult * pResult,
    OUT IMFByteStream **ppFile );

STDAPI MFCancelCreateFile(
    IN IUnknown * pCancelCookie);


///////////////////////////////////////////////////////////////////////////////
/////////////////////////////////    Buffers     //////////////////////////////
///////////////////////////////////////////////////////////////////////////////

//
// Creates an IMFMediaBuffer in memory
//
STDAPI MFCreateMemoryBuffer(
    __in DWORD                      cbMaxLength,
    __deref_out IMFMediaBuffer **   ppBuffer );

//
// Creates an IMFMediaBuffer wrapper at the given offset and length
// within an existing IMFMediaBuffer
//
STDAPI MFCreateMediaBufferWrapper(
    __in IMFMediaBuffer *           pBuffer,
    __in DWORD                      cbOffset,
    __in DWORD                      dwLength,
    __deref_out IMFMediaBuffer **   ppBuffer );

//
// Creates a legacy buffer (IMediaBuffer) wrapper at the given offset within
// an existing IMFMediaBuffer.
// pSample is optional.  It can point to the original IMFSample from which this
// IMFMediaBuffer came.  If provided, then *ppMediaBuffer will succeed
// QueryInterface for IID_IMFSample, from which the original sample's attributes
// can be obtained
//
STDAPI MFCreateLegacyMediaBufferOnMFMediaBuffer(
    __in_opt IMFSample *            pSample,
    __in IMFMediaBuffer *           pMFMediaBuffer,
    __in DWORD                      cbOffset,
    __deref_out IMediaBuffer **     ppMediaBuffer );

//
// Create a DirectX surface buffer
//
STDAPI MFCreateDXSurfaceBuffer(
    __in REFIID                     riid,
    __in IUnknown *                 punkSurface,
    __in BOOL                       fBottomUpWhenLinear,
    __deref_out IMFMediaBuffer **   ppBuffer );


//
// Create an aligned memory buffer.
// The following constants were chosen for parity with the alignment constants
// in ntioapi.h
// 
#define MF_1_BYTE_ALIGNMENT       0x00000000 
#define MF_2_BYTE_ALIGNMENT       0x00000001
#define MF_4_BYTE_ALIGNMENT       0x00000003
#define MF_8_BYTE_ALIGNMENT       0x00000007 
#define MF_16_BYTE_ALIGNMENT      0x0000000f
#define MF_32_BYTE_ALIGNMENT      0x0000001f
#define MF_64_BYTE_ALIGNMENT      0x0000003f
#define MF_128_BYTE_ALIGNMENT     0x0000007f
#define MF_256_BYTE_ALIGNMENT     0x000000ff
#define MF_512_BYTE_ALIGNMENT     0x000001ff

STDAPI MFCreateAlignedMemoryBuffer(
    __in DWORD                      cbMaxLength,
    __in DWORD                      cbAligment, 
    __deref_out IMFMediaBuffer **   ppBuffer );

//
// This GUID is used in IMFGetService::GetService calls to retrieve 
// interfaces from the buffer.  Its value is defined in evr.h
// 
EXTERN_C const GUID MR_BUFFER_SERVICE;

///////////////////////////////////////////////////////////////////////////////
/////////////////////////////////    Events      //////////////////////////////
///////////////////////////////////////////////////////////////////////////////

//
// Instantiates the MF-provided Media Event implementation.
//
STDAPI MFCreateMediaEvent(
    MediaEventType met,
    REFGUID guidExtendedType,
    HRESULT hrStatus,
    const PROPVARIANT * pvValue,
    IMFMediaEvent ** ppEvent );

//
// Instantiates an object that implements IMFMediaEventQueue.
// Components that provide an IMFMediaEventGenerator can use this object
// internally to do their Media Event Generator work for them.
// IMFMediaEventGenerator calls should be forwarded to the similar call
// on this object's IMFMediaEventQueue interface (e.g. BeginGetEvent,
// EndGetEvent), and the various IMFMediaEventQueue::QueueEventXXX methods
// can be used to queue events that the caller will consume.
//
STDAPI MFCreateEventQueue(
    IMFMediaEventQueue **ppMediaEventQueue );

//
// Event attributes
// Some of the common Media Foundation events have associated attributes
// that go in their IMFAttributes stores
//

//
// MESessionCapabilitiesChanged attributes
//

// MF_EVENT_SESSIONCAPS {7E5EBCD0-11B8-4abe-AFAD-10F6599A7F42}
// Type: UINT32
DEFINE_GUID(MF_EVENT_SESSIONCAPS,
0x7e5ebcd0, 0x11b8, 0x4abe, 0xaf, 0xad, 0x10, 0xf6, 0x59, 0x9a, 0x7f, 0x42);

// MF_EVENT_SESSIONCAPS_DELTA {7E5EBCD1-11B8-4abe-AFAD-10F6599A7F42}
// Type: UINT32
DEFINE_GUID(MF_EVENT_SESSIONCAPS_DELTA,
0x7e5ebcd1, 0x11b8, 0x4abe, 0xaf, 0xad, 0x10, 0xf6, 0x59, 0x9a, 0x7f, 0x42);

// Session capabilities bitflags
#define MFSESSIONCAP_START              0x00000001
#define MFSESSIONCAP_SEEK               0x00000002
#define MFSESSIONCAP_PAUSE              0x00000004
#define MFSESSIONCAP_RATE_FORWARD       0x00000010
#define MFSESSIONCAP_RATE_REVERSE       0x00000020

//
// MESesssionTopologyStatus attributes
//

// Possible values for MF_EVENT_TOPOLOGY_STATUS attribute.
//
// For a given topology, these status values will arrive via
// MESessionTopologyStatus in the order below.
//
// However, there are no guarantees about how these status values will be
// ordered between two consecutive topologies.  For example,
// MF_TOPOSTATUS_READY could arrive for topology n+1 before
// MF_TOPOSTATUS_ENDED arrives for topology n if the application called
// IMFMediaSession::SetTopology for topology n+1 well enough in advance of the
// end of topology n.  Conversely, if topology n ends before the application
// calls IMFMediaSession::SetTopology for topology n+1, then
// MF_TOPOSTATUS_ENDED will arrive for topology n before MF_TOPOSTATUS_READY
// arrives for topology n+1.
typedef enum
{
    // MF_TOPOSTATUS_INVALID: Invalid value; will not be sent
    MF_TOPOSTATUS_INVALID = 0,

    // MF_TOPOSTATUS_READY: The topology has been put in place and is
    // ready to start.  All GetService calls to the Media Session will use
    // this topology.
    MF_TOPOSTATUS_READY     = 100,

    // MF_TOPOSTATUS_STARTED_SOURCE: The Media Session has started to read
    // and process data from the Media Source(s) in this topology.
    MF_TOPOSTATUS_STARTED_SOURCE = 200,

    // MF_TOPOSTATUS_SINK_SWITCHED: The Media Sinks in the pipeline have
    // switched from a previous topology to this topology.
    // Note that this status does not get sent for the first topology;
    // applications can assume that the sinks are playing the first
    // topology when they receive MESessionStarted.
    MF_TOPOSTATUS_SINK_SWITCHED = 300,

    // MF_TOPOSTATUS_ENDED: Playback of this topology is complete.
    // Before deleting this topology, however, the application should wait
    // for either MESessionEnded or the MF_TOPOSTATUS_STARTED_SOURCE status
    // on the next topology to ensure that the Media Session is no longer
    // using this topology.
    MF_TOPOSTATUS_ENDED = 400,

}   MF_TOPOSTATUS;

// MF_EVENT_TOPOLOGY_STATUS {30C5018D-9A53-454b-AD9E-6D5F8FA7C43B}
// Type: UINT32 {MF_TOPOLOGY_STATUS}
DEFINE_GUID(MF_EVENT_TOPOLOGY_STATUS,
0x30c5018d, 0x9a53, 0x454b, 0xad, 0x9e, 0x6d, 0x5f, 0x8f, 0xa7, 0xc4, 0x3b);

//
// MESessionNotifyPresentationTime attributes
//

// MF_EVENT_START_PRESENTATION_TIME {5AD914D0-9B45-4a8d-A2C0-81D1E50BFB07}
// Type: UINT64
DEFINE_GUID(MF_EVENT_START_PRESENTATION_TIME,
0x5ad914d0, 0x9b45, 0x4a8d, 0xa2, 0xc0, 0x81, 0xd1, 0xe5, 0xb, 0xfb, 0x7);

// MF_EVENT_PRESENTATION_TIME_OFFSET {5AD914D1-9B45-4a8d-A2C0-81D1E50BFB07}
// Type: UINT64
DEFINE_GUID(MF_EVENT_PRESENTATION_TIME_OFFSET,
0x5ad914d1, 0x9b45, 0x4a8d, 0xa2, 0xc0, 0x81, 0xd1, 0xe5, 0xb, 0xfb, 0x7);

// MF_EVENT_START_PRESENTATION_TIME_AT_OUTPUT {5AD914D2-9B45-4a8d-A2C0-81D1E50BFB07}
// Type: UINT64
DEFINE_GUID(MF_EVENT_START_PRESENTATION_TIME_AT_OUTPUT,
0x5ad914d2, 0x9b45, 0x4a8d, 0xa2, 0xc0, 0x81, 0xd1, 0xe5, 0xb, 0xfb, 0x7);

//

//
// MESourceStarted attributes
//

// MF_EVENT_SOURCE_FAKE_START {a8cc55a7-6b31-419f-845d-ffb351a2434b}
// Type: UINT32
DEFINE_GUID(MF_EVENT_SOURCE_FAKE_START,
0xa8cc55a7, 0x6b31, 0x419f, 0x84, 0x5d, 0xff, 0xb3, 0x51, 0xa2, 0x43, 0x4b);

// MF_EVENT_SOURCE_PROJECTSTART {a8cc55a8-6b31-419f-845d-ffb351a2434b}
// Type: UINT64
DEFINE_GUID(MF_EVENT_SOURCE_PROJECTSTART,
0xa8cc55a8, 0x6b31, 0x419f, 0x84, 0x5d, 0xff, 0xb3, 0x51, 0xa2, 0x43, 0x4b);

// MF_EVENT_SOURCE_ACTUAL_START {a8cc55a9-6b31-419f-845d-ffb351a2434b}
// Type: UINT64
DEFINE_GUID(MF_EVENT_SOURCE_ACTUAL_START,
0xa8cc55a9, 0x6b31, 0x419f, 0x84, 0x5d, 0xff, 0xb3, 0x51, 0xa2, 0x43, 0x4b);

//
// MEEndOfPresentationSegment attributes
//

// MF_EVENT_SOURCE_TOPOLOGY_CANCELED {DB62F650-9A5E-4704-ACF3-563BC6A73364}
// Type: UINT32
DEFINE_GUID(MF_EVENT_SOURCE_TOPOLOGY_CANCELED,
0xdb62f650, 0x9a5e, 0x4704, 0xac, 0xf3, 0x56, 0x3b, 0xc6, 0xa7, 0x33, 0x64);

//
// MESourceCharacteristicsChanged attributes
//

// MF_EVENT_SOURCE_CHARACTERISTICS {47DB8490-8B22-4f52-AFDA-9CE1B2D3CFA8}
// Type: UINT32
DEFINE_GUID(MF_EVENT_SOURCE_CHARACTERISTICS,
0x47db8490, 0x8b22, 0x4f52, 0xaf, 0xda, 0x9c, 0xe1, 0xb2, 0xd3, 0xcf, 0xa8);

// MF_EVENT_SOURCE_CHARACTERISTICS_OLD {47DB8491-8B22-4f52-AFDA-9CE1B2D3CFA8}
// Type: UINT32
DEFINE_GUID(MF_EVENT_SOURCE_CHARACTERISTICS_OLD,
0x47db8491, 0x8b22, 0x4f52, 0xaf, 0xda, 0x9c, 0xe1, 0xb2, 0xd3, 0xcf, 0xa8);

//
// MESourceRateChangeRequested attributes
//

// MF_EVENT_DO_THINNING {321EA6FB-DAD9-46e4-B31D-D2EAE7090E30}
// Type: UINT32
DEFINE_GUID(MF_EVENT_DO_THINNING,
0x321ea6fb, 0xdad9, 0x46e4, 0xb3, 0x1d, 0xd2, 0xea, 0xe7, 0x9, 0xe, 0x30);

//
// MEStreamSinkScrubSampleComplete attributes
//

// MF_EVENT_SCRUBSAMPLE_TIME {9AC712B3-DCB8-44d5-8D0C-37455A2782E3}
// Type: UINT64
DEFINE_GUID(MF_EVENT_SCRUBSAMPLE_TIME,
0x9ac712b3, 0xdcb8, 0x44d5, 0x8d, 0xc, 0x37, 0x45, 0x5a, 0x27, 0x82, 0xe3);

//
// MESinkInvalidated and MESessionStreamSinkFormatChanged attributes
//

// MF_EVENT_OUTPUT_NODE {830f1a8b-c060-46dd-a801-1c95dec9b107}
// Type: UINT64
DEFINE_GUID(MF_EVENT_OUTPUT_NODE,
0x830f1a8b, 0xc060, 0x46dd, 0xa8, 0x01, 0x1c, 0x95, 0xde, 0xc9, 0xb1, 0x07);

////////////////////////////////////////////////////////////////////////////////
///////////////////////////////  Samples  //////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

//
// Creates an instance of the Media Foundation implementation of IMFSample
//
STDAPI MFCreateSample( __out IMFSample **ppIMFSample );

//
// Sample attributes
// These are the well-known attributes that can be present on an MF Sample's
// IMFAttributes store
//

// MFSampleExtension_CleanPoint {9cdf01d8-a0f0-43ba-b077-eaa06cbd728a}
// Type: UINT32
// If present and nonzero, indicates that the sample is a clean point (key
// frame), and decoding can begin at this sample.
DEFINE_GUID(MFSampleExtension_CleanPoint,
0x9cdf01d8, 0xa0f0, 0x43ba, 0xb0, 0x77, 0xea, 0xa0, 0x6c, 0xbd, 0x72, 0x8a);

// MFSampleExtension_Discontinuity {9cdf01d9-a0f0-43ba-b077-eaa06cbd728a}
// Type: UINT32
// If present and nonzero, indicates that the sample data represents the first
// sample following a discontinuity (gap) in the stream of samples.
// This can happen, for instance, if the previous sample was lost in
// transmission.
DEFINE_GUID(MFSampleExtension_Discontinuity,
0x9cdf01d9, 0xa0f0, 0x43ba, 0xb0, 0x77, 0xea, 0xa0, 0x6c, 0xbd, 0x72, 0x8a);

// MFSampleExtension_Token {8294da66-f328-4805-b551-00deb4c57a61}
// Type: IUNKNOWN
// When an IMFMediaStream delivers a sample via MEMediaStream, this attribute
// should be set to the IUnknown *pToken argument that was passed with the
// IMFMediaStream::RequestSample call to which this sample corresponds.
DEFINE_GUID(MFSampleExtension_Token,
0x8294da66, 0xf328, 0x4805, 0xb5, 0x51, 0x00, 0xde, 0xb4, 0xc5, 0x7a, 0x61);

//
// The following four sample attributes are used for encrypted samples
//
DEFINE_GUID(MFSampleExtension_DescrambleData,   // UINT64
0x43483be6, 0x4903, 0x4314, 0xb0, 0x32, 0x29, 0x51, 0x36, 0x59, 0x36, 0xfc);
DEFINE_GUID(MFSampleExtension_SampleKeyID,      // UINT32
0x9ed713c8, 0x9b87, 0x4b26, 0x82, 0x97, 0xa9, 0x3b, 0x0c, 0x5a, 0x8a, 0xcc);
DEFINE_GUID(MFSampleExtension_GenKeyFunc,       // UINT64
0x441ca1ee, 0x6b1f, 0x4501, 0x90, 0x3a, 0xde, 0x87, 0xdf, 0x42, 0xf6, 0xed);
DEFINE_GUID(MFSampleExtension_GenKeyCtx,        // UINT64
0x188120cb, 0xd7da, 0x4b59, 0x9b, 0x3e, 0x92, 0x52, 0xfd, 0x37, 0x30, 0x1c);
DEFINE_GUID(MFSampleExtension_PacketCrossOffsets,        // BLOB
0x2789671d, 0x389f, 0x40bb, 0x90, 0xd9, 0xc2, 0x82, 0xf7, 0x7f, 0x9a, 0xbd);

/////////////////////////////////////////////////////////////////////////////
//
// MFSample STANDARD EXTENSION ATTRIBUTE GUIDs
//
/////////////////////////////////////////////////////////////////////////////

// {b1d5830a-deb8-40e3-90fa-389943716461}   MFSampleExtension_Interlaced                {UINT32 (BOOL)}
DEFINE_GUID(MFSampleExtension_Interlaced,
0xb1d5830a, 0xdeb8, 0x40e3, 0x90, 0xfa, 0x38, 0x99, 0x43, 0x71, 0x64, 0x61);

// {941ce0a3-6ae3-4dda-9a08-a64298340617}   MFSampleExtension_BottomFieldFirst          {UINT32 (BOOL)}
DEFINE_GUID(MFSampleExtension_BottomFieldFirst,
0x941ce0a3, 0x6ae3, 0x4dda, 0x9a, 0x08, 0xa6, 0x42, 0x98, 0x34, 0x06, 0x17);

// {304d257c-7493-4fbd-b149-9228de8d9a99}   MFSampleExtension_RepeatFirstField          {UINT32 (BOOL)}
DEFINE_GUID(MFSampleExtension_RepeatFirstField,
0x304d257c, 0x7493, 0x4fbd, 0xb1, 0x49, 0x92, 0x28, 0xde, 0x8d, 0x9a, 0x99);

// {9d85f816-658b-455a-bde0-9fa7e15ab8f9}   MFSampleExtension_SingleField               {UINT32 (BOOL)}
DEFINE_GUID(MFSampleExtension_SingleField,
0x9d85f816, 0x658b, 0x455a, 0xbd, 0xe0, 0x9f, 0xa7, 0xe1, 0x5a, 0xb8, 0xf9);

// {6852465a-ae1c-4553-8e9b-c3420fcb1637}   MFSampleExtension_DerivedFromTopField       {UINT32 (BOOL)}
DEFINE_GUID(MFSampleExtension_DerivedFromTopField,
0x6852465a, 0xae1c, 0x4553, 0x8e, 0x9b, 0xc3, 0x42, 0x0f, 0xcb, 0x16, 0x37);



///////////////////////////////////////////////////////////////////////////////////////////////////////////////  Attributes ////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

STDAPI
MFCreateAttributes(
    __out   IMFAttributes** ppMFAttributes,
    __in    UINT32          cInitialSize
    );

STDAPI
MFInitAttributesFromBlob(
    __in                    IMFAttributes*  pAttributes,
    __in_bcount(cbBufSize)  const UINT8*    pBuf,
    __in                    UINT            cbBufSize
    );

STDAPI
MFGetAttributesAsBlobSize(
    __in    IMFAttributes*  pAttributes,
    __out   UINT32*         pcbBufSize
    );

STDAPI
MFGetAttributesAsBlob(
    __in                    IMFAttributes*  pAttributes,
    __out_bcount(cbBufSize) UINT8*          pBuf,
    __in                    UINT            cbBufSize
    );

///////////////////////////////////////////////////////////////////////////////////////////////////////////////  MFT Register & Enum ////////////////////////////
////////////////////////////////////////////////////////////////////////////////

//
// MFT Registry categories
//

#ifdef MF_INIT_GUIDS
#include <initguid.h>
#endif

// {d6c02d4b-6833-45b4-971a-05a4b04bab91}   MFT_CATEGORY_VIDEO_DECODER
DEFINE_GUID(MFT_CATEGORY_VIDEO_DECODER,
0xd6c02d4b, 0x6833, 0x45b4, 0x97, 0x1a, 0x05, 0xa4, 0xb0, 0x4b, 0xab, 0x91);

// {f79eac7d-e545-4387-bdee-d647d7bde42a}   MFT_CATEGORY_VIDEO_ENCODER
DEFINE_GUID(MFT_CATEGORY_VIDEO_ENCODER,
0xf79eac7d, 0xe545, 0x4387, 0xbd, 0xee, 0xd6, 0x47, 0xd7, 0xbd, 0xe4, 0x2a);

// {12e17c21-532c-4a6e-8a1c-40825a736397}   MFT_CATEGORY_VIDEO_EFFECT
DEFINE_GUID(MFT_CATEGORY_VIDEO_EFFECT,
0x12e17c21, 0x532c, 0x4a6e, 0x8a, 0x1c, 0x40, 0x82, 0x5a, 0x73, 0x63, 0x97);

// {059c561e-05ae-4b61-b69d-55b61ee54a7b}   MFT_CATEGORY_MULTIPLEXER
DEFINE_GUID(MFT_CATEGORY_MULTIPLEXER,
0x059c561e, 0x05ae, 0x4b61, 0xb6, 0x9d, 0x55, 0xb6, 0x1e, 0xe5, 0x4a, 0x7b);

// {a8700a7a-939b-44c5-99d7-76226b23b3f1}   MFT_CATEGORY_DEMULTIPLEXER
DEFINE_GUID(MFT_CATEGORY_DEMULTIPLEXER,
0xa8700a7a, 0x939b, 0x44c5, 0x99, 0xd7, 0x76, 0x22, 0x6b, 0x23, 0xb3, 0xf1);

// {9ea73fb4-ef7a-4559-8d5d-719d8f0426c7}   MFT_CATEGORY_AUDIO_DECODER
DEFINE_GUID(MFT_CATEGORY_AUDIO_DECODER,
0x9ea73fb4, 0xef7a, 0x4559, 0x8d, 0x5d, 0x71, 0x9d, 0x8f, 0x04, 0x26, 0xc7);

// {91c64bd0-f91e-4d8c-9276-db248279d975}   MFT_CATEGORY_AUDIO_ENCODER
DEFINE_GUID(MFT_CATEGORY_AUDIO_ENCODER,
0x91c64bd0, 0xf91e, 0x4d8c, 0x92, 0x76, 0xdb, 0x24, 0x82, 0x79, 0xd9, 0x75);

// {11064c48-3648-4ed0-932e-05ce8ac811b7}   MFT_CATEGORY_AUDIO_EFFECT
DEFINE_GUID(MFT_CATEGORY_AUDIO_EFFECT,
0x11064c48, 0x3648, 0x4ed0, 0x93, 0x2e, 0x05, 0xce, 0x8a, 0xc8, 0x11, 0xb7);

// {90175d57-b7ea-4901-aeb3-933a8747756f}   MFT_CATEGORY_OTHER
DEFINE_GUID(MFT_CATEGORY_OTHER,
0x90175d57, 0xb7ea, 0x4901, 0xae, 0xb3, 0x93, 0x3a, 0x87, 0x47, 0x75, 0x6f);



//
// new version of MFT_REGISTER_TYPE_INFO for MF
//
typedef struct _MFT_REGISTER_TYPE_INFO {
    GUID    guidMajorType;
    GUID    guidSubtype;
} MFT_REGISTER_TYPE_INFO;

//
// "Flags" is for future expansion - for now must be 0
//
STDAPI
MFTRegister(
    __in                            CLSID                   clsidMFT,
    __in                            GUID                    guidCategory,
    __in                            LPWSTR                  pszName,
    __in                            UINT32                  Flags,
    __in                            UINT32                  cInputTypes,
    __in_ecount_opt(cInputTypes)    MFT_REGISTER_TYPE_INFO* pInputTypes,
    __in                            UINT32                  cOutputTypes,
    __in_ecount_opt(cOutputTypes)   MFT_REGISTER_TYPE_INFO* pOutputTypes,
    __in_opt                        IMFAttributes*          pAttributes
    );

STDAPI
MFTUnregister(
    __in    CLSID   clsidMFT
    );

//
// result *ppclsidMFT must be freed with CoTaskMemFree.
//
STDAPI
MFTEnum(
    __in                    GUID                    guidCategory,
    __in                    UINT32                  Flags,
    __in_opt                MFT_REGISTER_TYPE_INFO* pInputType,
    __in_opt                MFT_REGISTER_TYPE_INFO* pOutputType,
    __in_opt                IMFAttributes*          pAttributes,
    __out_ecount(*pcMFTs)   CLSID**                 ppclsidMFT, // must be freed with CoTaskMemFree
    __out                   UINT32*                 pcMFTs
    );

//
// results *pszName, *ppInputTypes, and *ppOutputTypes must be freed with CoTaskMemFree.
// *ppAttributes must be released.
//
STDAPI
MFTGetInfo(
    __in                                CLSID                       clsidMFT,
    __deref_out_opt                     LPWSTR*                     pszName,
    __out_ecount_opt(*pcInputTypes)     MFT_REGISTER_TYPE_INFO**    ppInputTypes,
    __out_opt                           UINT32*                     pcInputTypes,
    __out_ecount_opt(*pcOutputTypes)    MFT_REGISTER_TYPE_INFO**    ppOutputTypes,
    __out_opt                           UINT32*                     pcOutputTypes,
    __deref_out_opt                     IMFAttributes**             ppAttributes
    );

///////////////////////////////////////////////////////////////////////////////////////////////////////////////  MFT  Attributes GUIDs ////////////////////////////
// {53476A11-3F13-49fb-AC42-EE2733C96741} MFT_SUPPORT_DYNAMIC_FORMAT_CHANGE {UINT32 (BOOL)}
DEFINE_GUID(MFT_SUPPORT_DYNAMIC_FORMAT_CHANGE,
0x53476a11, 0x3f13, 0x49fb, 0xac, 0x42, 0xee, 0x27, 0x33, 0xc9, 0x67, 0x41);

///////////////////////////////////////////////////////////////////////////////////////////////////////////////  Media Type GUIDs ////////////////////////////
////////////////////////////////////////////////////////////////////////////////

//
// GUIDs for media types
//

//
// In MF, media types for uncompressed video formats MUST be composed from a FourCC or D3DFORMAT combined with
// the "base GUID" {00000000-0000-0010-8000-00AA00389B71} by replacing the initial 32 bits with the FourCC/D3DFORMAT
//
// Audio media types for types which already have a defined wFormatTag value can be constructed similarly, by
// putting the wFormatTag (zero-extended to 32 bits) into the first 32 bits of the base GUID.
//
// Compressed video or audio can also use any well-known GUID that exists, or can create a new GUID.
//
// GUIDs for common media types are defined below.
//


#ifndef FCC
#define FCC(ch4) ((((DWORD)(ch4) & 0xFF) << 24) |     \
                  (((DWORD)(ch4) & 0xFF00) << 8) |    \
                  (((DWORD)(ch4) & 0xFF0000) >> 8) |  \
                  (((DWORD)(ch4) & 0xFF000000) >> 24))
#endif


//
// this macro creates a media type GUID from a FourCC, D3DFMT, or WAVE_FORMAT
//
#ifndef DEFINE_MEDIATYPE_GUID
#define DEFINE_MEDIATYPE_GUID(name, format) \
    DEFINE_GUID(name,                       \
    format, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xaa, 0x00, 0x38, 0x9b, 0x71);
#endif

//
// video media types
//

//
// If no D3D headers have been included yet, define local versions of D3DFMT constants we use.
// We can't include D3D headers from this header because we need it to be compatible with all versions
// of D3D.
//
#ifndef DIRECT3D_VERSION
#define D3DFMT_R8G8B8       20
#define D3DFMT_A8R8G8B8     21
#define D3DFMT_X8R8G8B8     22
#define D3DFMT_R5G6B5       23
#define D3DFMT_X1R5G5B5     24
#define LOCAL_D3DFMT_DEFINES 1
#endif

DEFINE_MEDIATYPE_GUID( MFVideoFormat_Base,      0x00000000 );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_RGB32,     D3DFMT_X8R8G8B8 );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_ARGB32,    D3DFMT_A8R8G8B8 );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_RGB24,     D3DFMT_R8G8B8 );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_RGB555,    D3DFMT_X1R5G5B5 );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_RGB565,    D3DFMT_R5G6B5 );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_AI44,      FCC('AI44') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_AYUV,      FCC('AYUV') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_YUY2,      FCC('YUY2') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_UYVY,      FCC('UYVY') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_NV11,      FCC('NV11') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_NV12,      FCC('NV12') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_YV12,      FCC('YV12') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_IYUV,      FCC('IYUV') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_Y210,      FCC('Y210') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_Y216,      FCC('Y216') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_Y410,      FCC('Y410') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_Y416,      FCC('Y416') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_P210,      FCC('P210') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_P216,      FCC('P216') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_P010,      FCC('P010') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_P016,      FCC('P016') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_v210,      FCC('v210') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_v410,      FCC('v410') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_MP43,      FCC('MP43') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_MP4S,      FCC('MP4S') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_WMV1,      FCC('WMV1') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_WMV2,      FCC('WMV2') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_WMV3,      FCC('WMV3') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_MSS1,      FCC('MSS1') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_MSS2,      FCC('MSS2') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_MPG1,      FCC('MPG1') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_DVSL,      FCC('dvsl') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_DVSD,      FCC('dvsd') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_DV25,      FCC('dv25') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_DV50,      FCC('dv50') );
DEFINE_MEDIATYPE_GUID( MFVideoFormat_DVH1,      FCC('dvh1') );

//
// undef the local D3DFMT definitions to avoid later clashes with D3D headers
//
#ifdef LOCAL_D3DFMT_DEFINES
#undef D3DFMT_R8G8B8
#undef D3DFMT_A8R8G8B8
#undef D3DFMT_X8R8G8B8
#undef D3DFMT_R5G6B5
#undef D3DFMT_X1R5G5B5
#undef LOCAL_D3DFMT_DEFINES
#endif

//
// some legacy formats that don't fit the common pattern
//

// {e06d8026-db46-11cf-b4d1-00805f6cbbea}       MFVideoFormat_MPEG2
DEFINE_GUID(MFVideoFormat_MPEG2,
0xe06d8026, 0xdb46, 0x11cf, 0xb4, 0xd1, 0x00, 0x80, 0x5f, 0x6c, 0xbb, 0xea);

#define MFVideoFormat_MPG2 MFVideoFormat_MPEG2

//
// audio media types
//
DEFINE_MEDIATYPE_GUID( MFAudioFormat_Base,              0x00000000 );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_PCM,               WAVE_FORMAT_PCM );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_Float,             WAVE_FORMAT_IEEE_FLOAT );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_DTS,               WAVE_FORMAT_DTS );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_Dolby_AC3_SPDIF,   WAVE_FORMAT_DOLBY_AC3_SPDIF );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_DRM,               WAVE_FORMAT_DRM );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_WMAudioV8,         WAVE_FORMAT_WMAUDIO2 );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_WMAudioV9,         WAVE_FORMAT_WMAUDIO3 );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_WMAudio_Lossless,  WAVE_FORMAT_WMAUDIO_LOSSLESS );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_WMASPDIF,          WAVE_FORMAT_WMASPDIF );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_MSP1,              WAVE_FORMAT_WMAVOICE9 );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_MP3,               WAVE_FORMAT_MPEGLAYER3 );
DEFINE_MEDIATYPE_GUID( MFAudioFormat_MPEG,              WAVE_FORMAT_MPEG );


///////////////////////////////////////////////////////////////////////////////////////////////////////////////  Media Type Attributes GUIDs ////////////////////////////
////////////////////////////////////////////////////////////////////////////////

//
// GUIDs for IMFMediaType properties - prefix 'MF_MT_' - basic prop type in {},
// with type to cast to in ().
//


//
// core info for all types
//
// {48eba18e-f8c9-4687-bf11-0a74c9f96a8f}   MF_MT_MAJOR_TYPE                {GUID}
DEFINE_GUID(MF_MT_MAJOR_TYPE,
0x48eba18e, 0xf8c9, 0x4687, 0xbf, 0x11, 0x0a, 0x74, 0xc9, 0xf9, 0x6a, 0x8f);

// {f7e34c9a-42e8-4714-b74b-cb29d72c35e5}   MF_MT_SUBTYPE                   {GUID}
DEFINE_GUID(MF_MT_SUBTYPE,
0xf7e34c9a, 0x42e8, 0x4714, 0xb7, 0x4b, 0xcb, 0x29, 0xd7, 0x2c, 0x35, 0xe5);

// {c9173739-5e56-461c-b713-46fb995cb95f}   MF_MT_ALL_SAMPLES_INDEPENDENT   {UINT32 (BOOL)}
DEFINE_GUID(MF_MT_ALL_SAMPLES_INDEPENDENT,
0xc9173739, 0x5e56, 0x461c, 0xb7, 0x13, 0x46, 0xfb, 0x99, 0x5c, 0xb9, 0x5f);

// {b8ebefaf-b718-4e04-b0a9-116775e3321b}   MF_MT_FIXED_SIZE_SAMPLES        {UINT32 (BOOL)}
DEFINE_GUID(MF_MT_FIXED_SIZE_SAMPLES,
0xb8ebefaf, 0xb718, 0x4e04, 0xb0, 0xa9, 0x11, 0x67, 0x75, 0xe3, 0x32, 0x1b);

// {3afd0cee-18f2-4ba5-a110-8bea502e1f92}   MF_MT_COMPRESSED                {UINT32 (BOOL)}
DEFINE_GUID(MF_MT_COMPRESSED,
0x3afd0cee, 0x18f2, 0x4ba5, 0xa1, 0x10, 0x8b, 0xea, 0x50, 0x2e, 0x1f, 0x92);

//
// MF_MT_SAMPLE_SIZE is only valid if MF_MT_FIXED_SIZED_SAMPLES is TRUE
//
// {dad3ab78-1990-408b-bce2-eba673dacc10}   MF_MT_SAMPLE_SIZE               {UINT32}
DEFINE_GUID(MF_MT_SAMPLE_SIZE,
0xdad3ab78, 0x1990, 0x408b, 0xbc, 0xe2, 0xeb, 0xa6, 0x73, 0xda, 0xcc, 0x10);

// 4d3f7b23-d02f-4e6c-9bee-e4bf2c6c695d     MF_MT_WRAPPED_TYPE              {Blob}
DEFINE_GUID(MF_MT_WRAPPED_TYPE,
0x4d3f7b23, 0xd02f, 0x4e6c, 0x9b, 0xee, 0xe4, 0xbf, 0x2c, 0x6c, 0x69, 0x5d);

//
// AUDIO data
//

// {37e48bf5-645e-4c5b-89de-ada9e29b696a}   MF_MT_AUDIO_NUM_CHANNELS            {UINT32}
DEFINE_GUID(MF_MT_AUDIO_NUM_CHANNELS,
0x37e48bf5, 0x645e, 0x4c5b, 0x89, 0xde, 0xad, 0xa9, 0xe2, 0x9b, 0x69, 0x6a);

// {5faeeae7-0290-4c31-9e8a-c534f68d9dba}   MF_MT_AUDIO_SAMPLES_PER_SECOND      {UINT32}
DEFINE_GUID(MF_MT_AUDIO_SAMPLES_PER_SECOND,
0x5faeeae7, 0x0290, 0x4c31, 0x9e, 0x8a, 0xc5, 0x34, 0xf6, 0x8d, 0x9d, 0xba);

// {fb3b724a-cfb5-4319-aefe-6e42b2406132}   MF_MT_AUDIO_FLOAT_SAMPLES_PER_SECOND {double}
DEFINE_GUID(MF_MT_AUDIO_FLOAT_SAMPLES_PER_SECOND,
0xfb3b724a, 0xcfb5, 0x4319, 0xae, 0xfe, 0x6e, 0x42, 0xb2, 0x40, 0x61, 0x32);

// {1aab75c8-cfef-451c-ab95-ac034b8e1731}   MF_MT_AUDIO_AVG_BYTES_PER_SECOND    {UINT32}
DEFINE_GUID(MF_MT_AUDIO_AVG_BYTES_PER_SECOND,
0x1aab75c8, 0xcfef, 0x451c, 0xab, 0x95, 0xac, 0x03, 0x4b, 0x8e, 0x17, 0x31);

// {322de230-9eeb-43bd-ab7a-ff412251541d}   MF_MT_AUDIO_BLOCK_ALIGNMENT         {UINT32}
DEFINE_GUID(MF_MT_AUDIO_BLOCK_ALIGNMENT,
0x322de230, 0x9eeb, 0x43bd, 0xab, 0x7a, 0xff, 0x41, 0x22, 0x51, 0x54, 0x1d);

// {f2deb57f-40fa-4764-aa33-ed4f2d1ff669}   MF_MT_AUDIO_BITS_PER_SAMPLE         {UINT32}
DEFINE_GUID(MF_MT_AUDIO_BITS_PER_SAMPLE,
0xf2deb57f, 0x40fa, 0x4764, 0xaa, 0x33, 0xed, 0x4f, 0x2d, 0x1f, 0xf6, 0x69);

// {d9bf8d6a-9530-4b7c-9ddf-ff6fd58bbd06}   MF_MT_AUDIO_VALID_BITS_PER_SAMPLE   {UINT32}
DEFINE_GUID(MF_MT_AUDIO_VALID_BITS_PER_SAMPLE,
0xd9bf8d6a, 0x9530, 0x4b7c, 0x9d, 0xdf, 0xff, 0x6f, 0xd5, 0x8b, 0xbd, 0x06);

// {aab15aac-e13a-4995-9222-501ea15c6877}   MF_MT_AUDIO_SAMPLES_PER_BLOCK       {UINT32}
DEFINE_GUID(MF_MT_AUDIO_SAMPLES_PER_BLOCK,
0xaab15aac, 0xe13a, 0x4995, 0x92, 0x22, 0x50, 0x1e, 0xa1, 0x5c, 0x68, 0x77);

// {55fb5765-644a-4caf-8479-938983bb1588}`  MF_MT_AUDIO_CHANNEL_MASK            {UINT32}
DEFINE_GUID(MF_MT_AUDIO_CHANNEL_MASK,
0x55fb5765, 0x644a, 0x4caf, 0x84, 0x79, 0x93, 0x89, 0x83, 0xbb, 0x15, 0x88);

//
// MF_MT_AUDIO_FOLDDOWN_MATRIX stores folddown structure from multichannel to stereo
//
typedef struct _MFFOLDDOWN_MATRIX
{
    UINT32 cbSize;
    UINT32 cSrcChannels; // number of source channels
    UINT32 cDstChannels; // number of destination channels
    UINT32 dwChannelMask; // mask
    LONG Coeff[64];
} MFFOLDDOWN_MATRIX;

// {9d62927c-36be-4cf2-b5c4-a3926e3e8711}`  MF_MT_AUDIO_FOLDDOWN_MATRIX         {BLOB, MFFOLDDOWN_MATRIX}
DEFINE_GUID(MF_MT_AUDIO_FOLDDOWN_MATRIX,
0x9d62927c, 0x36be, 0x4cf2, 0xb5, 0xc4, 0xa3, 0x92, 0x6e, 0x3e, 0x87, 0x11);

// {0x9d62927d-36be-4cf2-b5c4-a3926e3e8711}`  MF_MT_AUDIO_WMADRC_PEAKREF         {UINT32}
DEFINE_GUID(MF_MT_AUDIO_WMADRC_PEAKREF,
0x9d62927d, 0x36be, 0x4cf2, 0xb5, 0xc4, 0xa3, 0x92, 0x6e, 0x3e, 0x87, 0x11);

// {0x9d62927e-36be-4cf2-b5c4-a3926e3e8711}`  MF_MT_AUDIO_WMADRC_PEAKTARGET        {UINT32}
DEFINE_GUID(MF_MT_AUDIO_WMADRC_PEAKTARGET,
0x9d62927e, 0x36be, 0x4cf2, 0xb5, 0xc4, 0xa3, 0x92, 0x6e, 0x3e, 0x87, 0x11);


// {0x9d62927f-36be-4cf2-b5c4-a3926e3e8711}`  MF_MT_AUDIO_WMADRC_AVGREF         {UINT32}
DEFINE_GUID(MF_MT_AUDIO_WMADRC_AVGREF,
0x9d62927f, 0x36be, 0x4cf2, 0xb5, 0xc4, 0xa3, 0x92, 0x6e, 0x3e, 0x87, 0x11);

// {0x9d629280-36be-4cf2-b5c4-a3926e3e8711}`  MF_MT_AUDIO_WMADRC_AVGTARGET      {UINT32}
DEFINE_GUID(MF_MT_AUDIO_WMADRC_AVGTARGET,
0x9d629280, 0x36be, 0x4cf2, 0xb5, 0xc4, 0xa3, 0x92, 0x6e, 0x3e, 0x87, 0x11);

//
// MF_MT_AUDIO_PREFER_WAVEFORMATEX tells the converter to prefer a plain WAVEFORMATEX rather than
// a WAVEFORMATEXTENSIBLE when converting to a legacy type. It is set by the WAVEFORMATEX->IMFMediaType
// conversion routines when the original format block is a non-extensible WAVEFORMATEX.
//
// This preference can be overridden and does not guarantee that the type can be correctly expressed
// by a non-extensible type.
//
// {a901aaba-e037-458a-bdf6-545be2074042}   MF_MT_AUDIO_PREFER_WAVEFORMATEX     {UINT32 (BOOL)}
DEFINE_GUID(MF_MT_AUDIO_PREFER_WAVEFORMATEX,
0xa901aaba, 0xe037, 0x458a, 0xbd, 0xf6, 0x54, 0x5b, 0xe2, 0x07, 0x40, 0x42);

//
// VIDEO core data
//

// {1652c33d-d6b2-4012-b834-72030849a37d}   MF_MT_FRAME_SIZE                {UINT64 (HI32(Width),LO32(Height))}
DEFINE_GUID(MF_MT_FRAME_SIZE,
0x1652c33d, 0xd6b2, 0x4012, 0xb8, 0x34, 0x72, 0x03, 0x08, 0x49, 0xa3, 0x7d);

// {c459a2e8-3d2c-4e44-b132-fee5156c7bb0}   MF_MT_FRAME_RATE                {UINT64 (HI32(Numerator),LO32(Denominator))}
DEFINE_GUID(MF_MT_FRAME_RATE,
0xc459a2e8, 0x3d2c, 0x4e44, 0xb1, 0x32, 0xfe, 0xe5, 0x15, 0x6c, 0x7b, 0xb0);

// {c6376a1e-8d0a-4027-be45-6d9a0ad39bb6}   MF_MT_PIXEL_ASPECT_RATIO        {UINT64 (HI32(Numerator),LO32(Denominator))}
DEFINE_GUID(MF_MT_PIXEL_ASPECT_RATIO,
0xc6376a1e, 0x8d0a, 0x4027, 0xbe, 0x45, 0x6d, 0x9a, 0x0a, 0xd3, 0x9b, 0xb6);

// {8772f323-355a-4cc7-bb78-6d61a048ae82}   MF_MT_DRM_FLAGS                 {UINT32 (anyof MFVideoDRMFlags)}
DEFINE_GUID(MF_MT_DRM_FLAGS,
0x8772f323, 0x355a, 0x4cc7, 0xbb, 0x78, 0x6d, 0x61, 0xa0, 0x48, 0xae, 0x82);

typedef enum _MFVideoDRMFlags {
    MFVideoDRMFlag_None                 = 0,
    MFVideoDRMFlag_AnalogProtected      = 1,
    MFVideoDRMFlag_DigitallyProtected   = 2,
} MFVideoDRMFlags;


// {4d0e73e5-80ea-4354-a9d0-1176ceb028ea}   MF_MT_PAD_CONTROL_FLAGS         {UINT32 (oneof MFVideoPadFlags)}
DEFINE_GUID(MF_MT_PAD_CONTROL_FLAGS,
0x4d0e73e5, 0x80ea, 0x4354, 0xa9, 0xd0, 0x11, 0x76, 0xce, 0xb0, 0x28, 0xea);

typedef enum _MFVideoPadFlags {
    MFVideoPadFlag_PAD_TO_None  = 0,
    MFVideoPadFlag_PAD_TO_4x3   = 1,
    MFVideoPadFlag_PAD_TO_16x9  = 2
} MFVideoPadFlags;

// {68aca3cc-22d0-44e6-85f8-28167197fa38}   MF_MT_SOURCE_CONTENT_HINT       {UINT32 (oneof MFVideoSrcContentHintFlags)}
DEFINE_GUID(MF_MT_SOURCE_CONTENT_HINT,
0x68aca3cc, 0x22d0, 0x44e6, 0x85, 0xf8, 0x28, 0x16, 0x71, 0x97, 0xfa, 0x38);

typedef enum _MFVideoSrcContentHintFlags {
    MFVideoSrcContentHintFlag_None  = 0,
    MFVideoSrcContentHintFlag_16x9  = 1,
    MFVideoSrcContentHintFlag_235_1 = 2
} MFVideoSrcContentHintFlags;

// {65df2370-c773-4c33-aa64-843e068efb0c}   MF_MT_CHROMA_SITING             {UINT32 (anyof MFVideoChromaSubsampling)}
DEFINE_GUID(MF_MT_VIDEO_CHROMA_SITING,
0x65df2370, 0xc773, 0x4c33, 0xaa, 0x64, 0x84, 0x3e, 0x06, 0x8e, 0xfb, 0x0c);

// {e2724bb8-e676-4806-b4b2-a8d6efb44ccd}   MF_MT_INTERLACE_MODE            {UINT32 (oneof MFVideoInterlaceMode)}
DEFINE_GUID(MF_MT_INTERLACE_MODE,
0xe2724bb8, 0xe676, 0x4806, 0xb4, 0xb2, 0xa8, 0xd6, 0xef, 0xb4, 0x4c, 0xcd);

// {5fb0fce9-be5c-4935-a811-ec838f8eed93}   MF_MT_TRANSFER_FUNCTION         {UINT32 (oneof MFVideoTransferFunction)}
DEFINE_GUID(MF_MT_TRANSFER_FUNCTION,
0x5fb0fce9, 0xbe5c, 0x4935, 0xa8, 0x11, 0xec, 0x83, 0x8f, 0x8e, 0xed, 0x93);

// {dbfbe4d7-0740-4ee0-8192-850ab0e21935}   MF_MT_VIDEO_PRIMARIES           {UINT32 (oneof MFVideoPrimaries)}
DEFINE_GUID(MF_MT_VIDEO_PRIMARIES,
0xdbfbe4d7, 0x0740, 0x4ee0, 0x81, 0x92, 0x85, 0x0a, 0xb0, 0xe2, 0x19, 0x35);

// {47537213-8cfb-4722-aa34-fbc9e24d77b8}   MF_MT_CUSTOM_VIDEO_PRIMARIES    {BLOB (MT_CUSTOM_VIDEO_PRIMARIES)}
DEFINE_GUID(MF_MT_CUSTOM_VIDEO_PRIMARIES,
0x47537213, 0x8cfb, 0x4722, 0xaa, 0x34, 0xfb, 0xc9, 0xe2, 0x4d, 0x77, 0xb8);

typedef struct _MT_CUSTOM_VIDEO_PRIMARIES {
    float fRx;
    float fRy;
    float fGx;
    float fGy;
    float fBx;
    float fBy;
    float fWx;
    float fWy;
} MT_CUSTOM_VIDEO_PRIMARIES;

// {3e23d450-2c75-4d25-a00e-b91670d12327}   MF_MT_YUV_MATRIX                {UINT32 (oneof MFVideoTransferMatrix)}
DEFINE_GUID(MF_MT_YUV_MATRIX,
0x3e23d450, 0x2c75, 0x4d25, 0xa0, 0x0e, 0xb9, 0x16, 0x70, 0xd1, 0x23, 0x27);

// {53a0529c-890b-4216-8bf9-599367ad6d20}   MF_MT_VIDEO_LIGHTING            {UINT32 (oneof MFVideoLighting)}
DEFINE_GUID(MF_MT_VIDEO_LIGHTING,
0x53a0529c, 0x890b, 0x4216, 0x8b, 0xf9, 0x59, 0x93, 0x67, 0xad, 0x6d, 0x20);

// {c21b8ee5-b956-4071-8daf-325edf5cab11}   MF_MT_VIDEO_NOMINAL_RANGE       {UINT32 (oneof MFNominalRange)}
DEFINE_GUID(MF_MT_VIDEO_NOMINAL_RANGE,
0xc21b8ee5, 0xb956, 0x4071, 0x8d, 0xaf, 0x32, 0x5e, 0xdf, 0x5c, 0xab, 0x11);

// {66758743-7e5f-400d-980a-aa8596c85696}   MF_MT_GEOMETRIC_APERTURE        {BLOB (MFVideoArea)}
DEFINE_GUID(MF_MT_GEOMETRIC_APERTURE,
0x66758743, 0x7e5f, 0x400d, 0x98, 0x0a, 0xaa, 0x85, 0x96, 0xc8, 0x56, 0x96);

// {d7388766-18fe-48c6-a177-ee894867c8c4}   MF_MT_MINIMUM_DISPLAY_APERTURE  {BLOB (MFVideoArea)}
DEFINE_GUID(MF_MT_MINIMUM_DISPLAY_APERTURE,
0xd7388766, 0x18fe, 0x48c6, 0xa1, 0x77, 0xee, 0x89, 0x48, 0x67, 0xc8, 0xc4);

// {79614dde-9187-48fb-b8c7-4d52689de649}   MF_MT_PAN_SCAN_APERTURE         {BLOB (MFVideoArea)}
DEFINE_GUID(MF_MT_PAN_SCAN_APERTURE,
0x79614dde, 0x9187, 0x48fb, 0xb8, 0xc7, 0x4d, 0x52, 0x68, 0x9d, 0xe6, 0x49);

// {4b7f6bc3-8b13-40b2-a993-abf630b8204e}   MF_MT_PAN_SCAN_ENABLED          {UINT32 (BOOL)}
DEFINE_GUID(MF_MT_PAN_SCAN_ENABLED,
0x4b7f6bc3, 0x8b13, 0x40b2, 0xa9, 0x93, 0xab, 0xf6, 0x30, 0xb8, 0x20, 0x4e);

// {20332624-fb0d-4d9e-bd0d-cbf6786c102e}   MF_MT_AVG_BITRATE               {UINT32}
DEFINE_GUID(MF_MT_AVG_BITRATE,
0x20332624, 0xfb0d, 0x4d9e, 0xbd, 0x0d, 0xcb, 0xf6, 0x78, 0x6c, 0x10, 0x2e);

// {799cabd6-3508-4db4-a3c7-569cd533deb1}   MF_MT_AVG_BIT_ERROR_RATE        {UINT32}
DEFINE_GUID(MF_MT_AVG_BIT_ERROR_RATE,
0x799cabd6, 0x3508, 0x4db4, 0xa3, 0xc7, 0x56, 0x9c, 0xd5, 0x33, 0xde, 0xb1);

// {c16eb52b-73a1-476f-8d62-839d6a020652}   MF_MT_MAX_KEYFRAME_SPACING      {UINT32}
DEFINE_GUID(MF_MT_MAX_KEYFRAME_SPACING,
0xc16eb52b, 0x73a1, 0x476f, 0x8d, 0x62, 0x83, 0x9d, 0x6a, 0x02, 0x06, 0x52);

//
// VIDEO - uncompressed format data
//

// {644b4e48-1e02-4516-b0eb-c01ca9d49ac6}   MF_MT_DEFAULT_STRIDE            {UINT32 (INT32)} // in bytes
DEFINE_GUID(MF_MT_DEFAULT_STRIDE,
0x644b4e48, 0x1e02, 0x4516, 0xb0, 0xeb, 0xc0, 0x1c, 0xa9, 0xd4, 0x9a, 0xc6);

// {6d283f42-9846-4410-afd9-654d503b1a54}   MF_MT_PALETTE                   {BLOB (array of MFPaletteEntry - usually 256)}
DEFINE_GUID(MF_MT_PALETTE,
0x6d283f42, 0x9846, 0x4410, 0xaf, 0xd9, 0x65, 0x4d, 0x50, 0x3b, 0x1a, 0x54);

//
// the following is only used for legacy data that was stuck at the end of the format block when the type
// was converted from a VIDEOINFOHEADER or VIDEOINFOHEADER2 block in an AM_MEDIA_TYPE.
//
// {b6bc765f-4c3b-40a4-bd51-2535b66fe09d}   MF_MT_USER_DATA                 {BLOB}
DEFINE_GUID(MF_MT_USER_DATA,
0xb6bc765f, 0x4c3b, 0x40a4, 0xbd, 0x51, 0x25, 0x35, 0xb6, 0x6f, 0xe0, 0x9d);

// {73d1072d-1870-4174-a063-29ff4ff6c11e}
DEFINE_GUID(MF_MT_AM_FORMAT_TYPE,
0x73d1072d, 0x1870, 0x4174, 0xa0, 0x63, 0x29, 0xff, 0x4f, 0xf6, 0xc1, 0x1e);

//
// VIDEO - MPEG1/2 extra data
//

// {91f67885-4333-4280-97cd-bd5a6c03a06e}   MF_MT_MPEG_START_TIME_CODE      {UINT32}
DEFINE_GUID(MF_MT_MPEG_START_TIME_CODE,
0x91f67885, 0x4333, 0x4280, 0x97, 0xcd, 0xbd, 0x5a, 0x6c, 0x03, 0xa0, 0x6e);

// {ad76a80b-2d5c-4e0b-b375-64e520137036}   MF_MT_MPEG2_PROFILE             {UINT32 (oneof AM_MPEG2Profile)}
DEFINE_GUID(MF_MT_MPEG2_PROFILE,
0xad76a80b, 0x2d5c, 0x4e0b, 0xb3, 0x75, 0x64, 0xe5, 0x20, 0x13, 0x70, 0x36);

// {96f66574-11c5-4015-8666-bff516436da7}   MF_MT_MPEG2_LEVEL               {UINT32 (oneof AM_MPEG2Level)}
DEFINE_GUID(MF_MT_MPEG2_LEVEL,
0x96f66574, 0x11c5, 0x4015, 0x86, 0x66, 0xbf, 0xf5, 0x16, 0x43, 0x6d, 0xa7);

// {31e3991d-f701-4b2f-b426-8ae3bda9e04b}   MF_MT_MPEG2_FLAGS               {UINT32 (anyof AMMPEG2_xxx flags)}
DEFINE_GUID(MF_MT_MPEG2_FLAGS,
0x31e3991d, 0xf701, 0x4b2f, 0xb4, 0x26, 0x8a, 0xe3, 0xbd, 0xa9, 0xe0, 0x4b);

// {3c036de7-3ad0-4c9e-9216-ee6d6ac21cb3}   MF_MT_MPEG_SEQUENCE_HEADER      {BLOB}
DEFINE_GUID(MF_MT_MPEG_SEQUENCE_HEADER,
0x3c036de7, 0x3ad0, 0x4c9e, 0x92, 0x16, 0xee, 0x6d, 0x6a, 0xc2, 0x1c, 0xb3);

//
// INTERLEAVED - DV extra data
//
// {84bd5d88-0fb8-4ac8-be4b-a8848bef98f3}   MF_MT_DV_AAUX_SRC_PACK_0        {UINT32}
DEFINE_GUID(MF_MT_DV_AAUX_SRC_PACK_0,
0x84bd5d88, 0x0fb8, 0x4ac8, 0xbe, 0x4b, 0xa8, 0x84, 0x8b, 0xef, 0x98, 0xf3);

// {f731004e-1dd1-4515-aabe-f0c06aa536ac}   MF_MT_DV_AAUX_CTRL_PACK_0       {UINT32}
DEFINE_GUID(MF_MT_DV_AAUX_CTRL_PACK_0,
0xf731004e, 0x1dd1, 0x4515, 0xaa, 0xbe, 0xf0, 0xc0, 0x6a, 0xa5, 0x36, 0xac);

// {720e6544-0225-4003-a651-0196563a958e}   MF_MT_DV_AAUX_SRC_PACK_1        {UINT32}
DEFINE_GUID(MF_MT_DV_AAUX_SRC_PACK_1,
0x720e6544, 0x0225, 0x4003, 0xa6, 0x51, 0x01, 0x96, 0x56, 0x3a, 0x95, 0x8e);

// {cd1f470d-1f04-4fe0-bfb9-d07ae0386ad8}   MF_MT_DV_AAUX_CTRL_PACK_1       {UINT32}
DEFINE_GUID(MF_MT_DV_AAUX_CTRL_PACK_1,
0xcd1f470d, 0x1f04, 0x4fe0, 0xbf, 0xb9, 0xd0, 0x7a, 0xe0, 0x38, 0x6a, 0xd8);

// {41402d9d-7b57-43c6-b129-2cb997f15009}   MF_MT_DV_VAUX_SRC_PACK          {UINT32}
DEFINE_GUID(MF_MT_DV_VAUX_SRC_PACK,
0x41402d9d, 0x7b57, 0x43c6, 0xb1, 0x29, 0x2c, 0xb9, 0x97, 0xf1, 0x50, 0x09);

// {2f84e1c4-0da1-4788-938e-0dfbfbb34b48}   MF_MT_DV_VAUX_CTRL_PACK         {UINT32}
DEFINE_GUID(MF_MT_DV_VAUX_CTRL_PACK,
0x2f84e1c4, 0x0da1, 0x4788, 0x93, 0x8e, 0x0d, 0xfb, 0xfb, 0xb3, 0x4b, 0x48);

////////////////////////////////////////////////////////////////////////////////
///////////////////////////////  Media Type GUIDs //////////////////////////////
////////////////////////////////////////////////////////////////////////////////

//
// Major types
//
DEFINE_GUID(MFMediaType_Default,
0x81A412E6, 0x8103, 0x4B06, 0x85, 0x7F, 0x18, 0x62, 0x78, 0x10, 0x24, 0xAC);
DEFINE_GUID(MFMediaType_Audio,
0x73647561, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71);
DEFINE_GUID(MFMediaType_Video,
0x73646976, 0x0000, 0x0010, 0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71);
DEFINE_GUID(MFMediaType_Protected,
0x7b4b6fe6, 0x9d04, 0x4494, 0xbe, 0x14, 0x7e, 0x0b, 0xd0, 0x76, 0xc8, 0xe4);
DEFINE_GUID(MFMediaType_SAMI,
0xe69669a0, 0x3dcd, 0x40cb, 0x9e, 0x2e, 0x37, 0x08, 0x38, 0x7c, 0x06, 0x16);
DEFINE_GUID(MFMediaType_Script,
0x72178C22, 0xE45B, 0x11D5, 0xBC, 0x2A, 0x00, 0xB0, 0xD0, 0xF3, 0xF4, 0xAB);
DEFINE_GUID(MFMediaType_Image,
0x72178C23, 0xE45B, 0x11D5, 0xBC, 0x2A, 0x00, 0xB0, 0xD0, 0xF3, 0xF4, 0xAB);
DEFINE_GUID(MFMediaType_HTML,
0x72178C24, 0xE45B, 0x11D5, 0xBC, 0x2A, 0x00, 0xB0, 0xD0, 0xF3, 0xF4, 0xAB);
DEFINE_GUID(MFMediaType_Binary,
0x72178C25, 0xE45B, 0x11D5, 0xBC, 0x2A, 0x00, 0xB0, 0xD0, 0xF3, 0xF4, 0xAB);
DEFINE_GUID(MFMediaType_FileTransfer,
0x72178C26, 0xE45B, 0x11D5, 0xBC, 0x2A, 0x00, 0xB0, 0xD0, 0xF3, 0xF4, 0xAB);

//
// Representations
//
DEFINE_GUID(AM_MEDIA_TYPE_REPRESENTATION,
0xe2e42ad2, 0x132c, 0x491e, 0xa2, 0x68, 0x3c, 0x7c, 0x2d, 0xca, 0x18, 0x1f);
DEFINE_GUID(FORMAT_MFVideoFormat,
0xaed4ab2d, 0x7326, 0x43cb, 0x94, 0x64, 0xc8, 0x79, 0xca, 0xb9, 0xc4, 0x3d);


///////////////////////////////////////////////////////////////////////////////////////////////////////////////  Media Type functions //////////////////////////
////////////////////////////////////////////////////////////////////////////////

//
// Forward declaration
//
struct tagVIDEOINFOHEADER;
typedef struct tagVIDEOINFOHEADER VIDEOINFOHEADER;
struct tagVIDEOINFOHEADER2;
typedef struct tagVIDEOINFOHEADER2 VIDEOINFOHEADER2;
struct tagMPEG1VIDEOINFO;
typedef struct tagMPEG1VIDEOINFO MPEG1VIDEOINFO;
struct tagMPEG2VIDEOINFO;
typedef struct tagMPEG2VIDEOINFO MPEG2VIDEOINFO;
struct _AMMediaType;
typedef struct _AMMediaType AM_MEDIA_TYPE;

STDAPI
MFValidateMediaTypeSize(
    __in                    GUID    FormatType,
    __in_bcount_opt(cbSize) UINT8*  pBlock,
    __in                    UINT32  cbSize
    );

STDAPI
MFCreateMediaType(
    __deref_out IMFMediaType**  ppMFType
    );

STDAPI
MFCreateMFVideoFormatFromMFMediaType(
    __in        IMFMediaType*           pMFType,
    __out       MFVIDEOFORMAT**         ppMFVF, // must be deleted with CoTaskMemFree
    __out_opt   UINT32*                 pcbSize
    );

typedef enum _MFWaveFormatExConvertFlags {
    MFWaveFormatExConvertFlag_Normal            = 0,
    MFWaveFormatExConvertFlag_ForceExtensible   = 1
} MFWaveFormatExConvertFlags;

#ifdef __cplusplus

//
// declarations with default parameters
//

STDAPI
MFCreateWaveFormatExFromMFMediaType(
    __in        IMFMediaType*   pMFType,
    __out       WAVEFORMATEX**  ppWF,
    __out_opt   UINT32*         pcbSize,
    __in        UINT32          Flags = MFWaveFormatExConvertFlag_Normal
    );

STDAPI
MFInitMediaTypeFromVideoInfoHeader(
    __in                    IMFMediaType*           pMFType,
    __in_bcount(cbBufSize)  const VIDEOINFOHEADER*  pVIH,
    __in                    UINT32                  cbBufSize,
    __in_opt                const GUID*             pSubtype = NULL
    );

STDAPI
MFInitMediaTypeFromVideoInfoHeader2(
    __in                    IMFMediaType*           pMFType,
    __in_bcount(cbBufSize)  const VIDEOINFOHEADER2* pVIH2,
    __in                    UINT32                  cbBufSize,
    __in_opt                const GUID*             pSubtype = NULL
    );

STDAPI
MFInitMediaTypeFromMPEG1VideoInfo(
    __in                    IMFMediaType*           pMFType,
    __in_bcount(cbBufSize)  const MPEG1VIDEOINFO*   pMP1VI,
    __in                    UINT32                  cbBufSize,
    __in_opt                const GUID*             pSubtype = NULL
    );

STDAPI
MFInitMediaTypeFromMPEG2VideoInfo(
    __in                    IMFMediaType*           pMFType,
    __in_bcount(cbBufSize)  const MPEG2VIDEOINFO*   pMP2VI,
    __in                    UINT32                  cbBufSize,
    __in_opt                const GUID*             pSubtype = NULL
    );

STDAPI
MFCalculateBitmapImageSize(
    __in_bcount(cbBufSize)  const BITMAPINFOHEADER* pBMIH,
    __in                    UINT32                  cbBufSize,
    __out                   UINT32*                 pcbImageSize,
    __out_opt               BOOL*                   pbKnown = NULL
    );

#else

//
// same declarations without default parameters
//

STDAPI
MFCreateWaveFormatExFromMFMediaType(
    __in        IMFMediaType*   pMFType,
    __out       WAVEFORMATEX**  ppWF,
    __out_opt   UINT32*         pcbSize,
    __in        UINT32          Flags
    );

STDAPI
MFInitMediaTypeFromVideoInfoHeader(
    __in                    IMFMediaType*           pMFType,
    __in_bcount(cbBufSize)  const VIDEOINFOHEADER*  pVIH,
    __in                    UINT32                  cbBufSize,
    __in_opt                const GUID*             pSubtype
    );

STDAPI
MFInitMediaTypeFromVideoInfoHeader2(
    __in                    IMFMediaType*           pMFType,
    __in_bcount(cbBufSize)  const VIDEOINFOHEADER2* pVIH2,
    __in                    UINT32                  cbBufSize,
    __in_opt                const GUID*             pSubtype
    );

STDAPI
MFInitMediaTypeFromMPEG1VideoInfo(
    __in                    IMFMediaType*           pMFType,
    __in_bcount(cbBufSize)  const MPEG1VIDEOINFO*   pMP1VI,
    __in                    UINT32                  cbBufSize,
    __in_opt                const GUID*             pSubtype
    );

STDAPI
MFInitMediaTypeFromMPEG2VideoInfo(
    __in                    IMFMediaType*           pMFType,
    __in_bcount(cbBufSize)  const MPEG2VIDEOINFO*   pMP2VI,
    __in                    UINT32                  cbBufSize,
    __in_opt                const GUID*             pSubtype
    );

STDAPI
MFCalculateBitmapImageSize(
    __in_bcount(cbBufSize)  const BITMAPINFOHEADER* pBMIH,
    __in                    UINT32                  cbBufSize,
    __out                   UINT32*                 pcbImageSize,
    __out_opt               BOOL*                   pbKnown
    );

#endif

STDAPI
MFCalculateImageSize(
    __in                    REFGUID                 guidSubtype,
    __in                    UINT32                  unWidth,
    __in                    UINT32                  unHeight,
    __out                   UINT32*                 pcbImageSize
    );

STDAPI
MFFrameRateToAverageTimePerFrame(
    __in                    UINT32                  unNumerator,
    __in                    UINT32                  unDenominator,
    __out                   UINT64*                 punAverageTimePerFrame
    );

STDAPI
MFAverageTimePerFrameToFrameRate(
    __in                    UINT64                  unAverageTimePerFrame,
    __out                   UINT32*                 punNumerator,
    __out                   UINT32*                 punDenominator
    );

STDAPI
MFInitMediaTypeFromMFVideoFormat(
    __in                    IMFMediaType*           pMFType,
    __in_bcount(cbBufSize)  const MFVIDEOFORMAT*    pMFVF,
    __in                    UINT32                  cbBufSize
    );

STDAPI
MFInitMediaTypeFromWaveFormatEx(
    __in                    IMFMediaType*           pMFType,
    __in_bcount(cbBufSize)  const WAVEFORMATEX*     pWaveFormat,
    __in                    UINT32                  cbBufSize
    );

STDAPI
MFInitMediaTypeFromAMMediaType(
    __in    IMFMediaType*           pMFType,
    __in    const AM_MEDIA_TYPE*    pAMType
    );

STDAPI
MFInitAMMediaTypeFromMFMediaType(
    __in    IMFMediaType*           pMFType,
    __in    GUID                    guidFormatBlockType,
    __inout AM_MEDIA_TYPE*          pAMType
    );

STDAPI
MFCreateAMMediaTypeFromMFMediaType(
    __in    IMFMediaType*           pMFType,
    __in    GUID                    guidFormatBlockType,
    __inout AM_MEDIA_TYPE**         ppAMType // delete with DeleteMediaType
    );


//
// This function compares a full media type to a partial media type.
//
// A "partial" media type is one that is given out by a component as a possible
// media type it could accept. Many attributes may be unset, which represents
// a "don't care" status for that attribute.
//
// For example, a video effect may report that it supports YV12,
// but not want to specify a particular size. It simply creates a media type and sets
// the major type to MFMediaType_Video and the subtype to MEDIASUBTYPE_YV12.
//
// The comparison function succeeds if the partial type contains at least a major type,
// and all of the attributes in the partial type exist in the full type and are set to
// the same value.
//
STDAPI_(BOOL)
MFCompareFullToPartialMediaType(
    __in    IMFMediaType*   pMFTypeFull,
    __in    IMFMediaType*   pMFTypePartial
    );


STDAPI
MFWrapMediaType(
    __in    IMFMediaType*    pOrig,
    __in    REFGUID          MajorType,
    __in    REFGUID          SubType,
    __out   IMFMediaType **  ppWrap
    );

STDAPI
MFUnwrapMediaType(
    __in    IMFMediaType*    pWrap,
    __out   IMFMediaType **  ppOrig
    );


//
// MFCreateVideoMediaType
//

#ifdef _KSMEDIA_
STDAPI MFCreateVideoMediaTypeFromVideoInfoHeader(
    const KS_VIDEOINFOHEADER* pVideoInfoHeader,
    DWORD cbVideoInfoHeader,
    DWORD dwPixelAspectRatioX,
    DWORD dwPixelAspectRatioY,
    MFVideoInterlaceMode InterlaceMode,
    QWORD VideoFlags,
    const GUID * pSubtype,
    IMFVideoMediaType** ppIVideoMediaType
    );

STDAPI MFCreateVideoMediaTypeFromVideoInfoHeader2(
    const KS_VIDEOINFOHEADER2* pVideoInfoHeader,
    DWORD cbVideoInfoHeader,
    QWORD AdditionalVideoFlags,
    const GUID * pSubtype,
    IMFVideoMediaType** ppIVideoMediaType
    );

#endif

STDAPI MFCreateVideoMediaType(
    const MFVIDEOFORMAT* pVideoFormat,
    IMFVideoMediaType** ppIVideoMediaType
    );

STDAPI MFCreateVideoMediaTypeFromSubtype(
    __in const GUID * pAMSubtype,
    __out IMFVideoMediaType  **ppIVideoMediaType
    );

STDAPI_(BOOL)
MFIsFormatYUV(
    DWORD Format
    );

//
//  These depend on BITMAPINFOHEADER being defined
//
STDAPI MFCreateVideoMediaTypeFromBitMapInfoHeader(
    const BITMAPINFOHEADER* pbmihBitMapInfoHeader,
    DWORD dwPixelAspectRatioX,
    DWORD dwPixelAspectRatioY,
    MFVideoInterlaceMode InterlaceMode,
    QWORD VideoFlags,
    QWORD qwFramesPerSecondNumerator,
    QWORD qwFramesPerSecondDenominator,
    DWORD dwMaxBitRate,
    IMFVideoMediaType** ppIVideoMediaType
    );

STDAPI MFGetStrideForBitmapInfoHeader(
    DWORD format,
    DWORD dwWidth,
    LONG* pStride
    );

STDAPI MFGetPlaneSize(
    DWORD format,
    DWORD dwWidth,
    DWORD dwHeight,
    DWORD* pdwPlaneSize
    );


//
// MFCreateMediaTypeFromRepresentation
//

STDAPI MFCreateMediaTypeFromRepresentation(
    GUID guidRepresentation,
    LPVOID pvRepresentation,
    IMFMediaType** ppIMediaType
    );


//
// MFCreateAudioMediaType
//

STDAPI
MFCreateAudioMediaType(
    __in    const WAVEFORMATEX* pAudioFormat,
    __out   IMFAudioMediaType** ppIAudioMediaType
    );


DWORD
STDMETHODCALLTYPE
MFGetUncompressedVideoFormat(
    __in    const MFVIDEOFORMAT* pVideoFormat
    );

STDAPI 
MFInitVideoFormat(
    __in    MFVIDEOFORMAT*          pVideoFormat,
    __in    MFStandardVideoFormat   type
    );

STDAPI
MFInitVideoFormat_RGB(
    __in    MFVIDEOFORMAT*  pVideoFormat,
    __in    DWORD           dwWidth,
    __in    DWORD           dwHeight,
    __in    DWORD           D3Dfmt /* 0 indicates sRGB */
    );

STDAPI 
MFConvertColorInfoToDXVA(
    DWORD* pdwToDXVA,
    const MFVIDEOFORMAT* pFromFormat
    );
STDAPI
MFConvertColorInfoFromDXVA(
    MFVIDEOFORMAT* pToFormat,
    DWORD dwFromDXVA
    );

//
// Optimized stride copy function
//
STDAPI MFCopyImage(
    BYTE* pDest,
    LONG    lDestStride,
    const BYTE* pSrc,
    LONG    lSrcStride,
    DWORD   dwWidthInBytes,
    DWORD   dwLines
    );

STDAPI MFConvertFromFP16Array(
    float* pDest,
    const WORD* pSrc,
    DWORD dwCount
    );

STDAPI MFConvertToFP16Array(
    WORD* pDest,
    const float* pSrc,
    DWORD dwCount
    );




///////////////////////////////////////////////////////////////////////////////////////////////////////////////  Attributes Utility functions ////////////////////////////
////////////////////////////////////////////////////////////////////////////////

#ifdef __cplusplus

//
// IMFAttributes inline UTILITY FUNCTIONS - used for IMFMediaType as well
//
inline
UINT32
HI32(UINT64 unPacked)
{
    return (UINT32)(unPacked >> 32);
}

inline
UINT32
LO32(UINT64 unPacked)
{
    return (UINT32)unPacked;
}

inline
UINT64
Pack2UINT32AsUINT64(UINT32 unHigh, UINT32 unLow)
{
    return ((UINT64)unHigh << 32) | unLow;
}

inline
void
Unpack2UINT32AsUINT64(UINT64 unPacked, UINT32* punHigh, UINT32* punLow)
{
    *punHigh = HI32(unPacked);
    *punLow = LO32(unPacked);
}

inline
UINT64
PackSize(UINT32 unWidth, UINT32 unHeight)
{
    return Pack2UINT32AsUINT64(unWidth, unHeight);
}

inline
void
UnpackSize(UINT64 unPacked, UINT32* punWidth, UINT32* punHeight)
{
    Unpack2UINT32AsUINT64(unPacked, punWidth, punHeight);
}

inline
UINT64
PackRatio(INT32 nNumerator, UINT32 unDenominator)
{
    return Pack2UINT32AsUINT64((UINT32)nNumerator, unDenominator);
}

inline
void
UnpackRatio(UINT64 unPacked, INT32* pnNumerator, UINT32* punDenominator)
{
    Unpack2UINT32AsUINT64(unPacked, (UINT32*)pnNumerator, punDenominator);
}


//
// "failsafe" inline get methods - return the stored value or return a default
//
inline
UINT32
MFGetAttributeUINT32(
    IMFAttributes*  pAttributes,
    REFGUID         guidKey,
    UINT32          unDefault
    )
{
    UINT32 unRet;
    if (FAILED(pAttributes->GetUINT32(guidKey, &unRet))) {
        unRet = unDefault;
    }
    return unRet;
}

inline
UINT64
MFGetAttributeUINT64(
    IMFAttributes*  pAttributes,
    REFGUID         guidKey,
    UINT64          unDefault
    )
{
    UINT64 unRet;
    if (FAILED(pAttributes->GetUINT64(guidKey, &unRet))) {
        unRet = unDefault;
    }
    return unRet;
}

inline
double
MFGetAttributeDouble(
    IMFAttributes*  pAttributes,
    REFGUID         guidKey,
    double          fDefault
    )
{
    double fRet;
    if (FAILED(pAttributes->GetDouble(guidKey, &fRet))) {
        fRet = fDefault;
    }
    return fRet;
}

//
// helpers for getting/setting ratios and sizes
//

inline
HRESULT
MFGetAttribute2UINT32asUINT64(
    IMFAttributes*  pAttributes,
    REFGUID         guidKey,
    UINT32*         punHigh32,
    UINT32*         punLow32
    )
{
    UINT64 unPacked;
    HRESULT hr = S_OK;

    hr = pAttributes->GetUINT64(guidKey, &unPacked);
    if (FAILED(hr)) {
        return hr;
    }
    Unpack2UINT32AsUINT64(unPacked, punHigh32, punLow32);

    return hr;
}

inline
HRESULT
MFSetAttribute2UINT32asUINT64(
    IMFAttributes*  pAttributes,
    REFGUID         guidKey,
    UINT32          unHigh32,
    UINT32          unLow32
    )
{
    return pAttributes->SetUINT64(guidKey, Pack2UINT32AsUINT64(unHigh32, unLow32));
}

inline
HRESULT
MFGetAttributeRatio(
    IMFAttributes*  pAttributes,
    REFGUID         guidKey,
    UINT32*         punNumerator,
    UINT32*         punDenominator
    )
{
    return MFGetAttribute2UINT32asUINT64(pAttributes, guidKey, punNumerator, punDenominator);
}

inline
HRESULT
MFGetAttributeSize(
    IMFAttributes*  pAttributes,
    REFGUID         guidKey,
    UINT32*         punWidth,
    UINT32*         punHeight
    )
{
    return MFGetAttribute2UINT32asUINT64(pAttributes, guidKey, punWidth, punHeight);
}

inline
HRESULT
MFSetAttributeRatio(
    IMFAttributes*  pAttributes,
    REFGUID         guidKey,
    UINT32          unNumerator,
    UINT32          unDenominator
    )
{
    return MFSetAttribute2UINT32asUINT64(pAttributes, guidKey, unNumerator, unDenominator);
}

inline
HRESULT
MFSetAttributeSize(
    IMFAttributes*  pAttributes,
    REFGUID         guidKey,
    UINT32          unWidth,
    UINT32          unHeight
    )
{
    return MFSetAttribute2UINT32asUINT64(pAttributes, guidKey, unWidth, unHeight);
}
#endif

////////////////////////////////////////////////////////////////////////////////
////////////////////////////////  Memory Management ////////////////////////////
////////////////////////////////////////////////////////////////////////////////

//
// Heap alloc/free
//
typedef enum _EAllocationType
{
    eAllocationTypeDynamic,
    eAllocationTypeRT,
    eAllocationTypePageable,
    eAllocationTypeIgnore
}   EAllocationType;

EXTERN_C void* MFHeapAlloc( size_t nSize,
                            ULONG dwFlags,
                            __in_opt char *pszFile,
                            int line,
                            EAllocationType eat);
EXTERN_C void MFHeapFree( void * pv );

///////////////////////////////  Collection         ////////////////////////////
////////////////////////////////////////////////////////////////////////////////

//
// Instantiates the MF-provided IMFCollection implementation
//
STDAPI MFCreateCollection(
    __deref_out IMFCollection **ppIMFCollection );


//////////////////////////       SourceResolver     ////////////////////////////
////////////////////////////////////////////////////////////////////////////////
DEFINE_GUID(CLSID_MFSourceResolver,
    0x90eab60f,
    0xe43a,
    0x4188,
    0xbc, 0xc4, 0xe4, 0x7f, 0xdf, 0x04, 0x86, 0x8c);

#if defined(__cplusplus)
}
#endif

#endif //#if !defined(__MFAPI_H__)


