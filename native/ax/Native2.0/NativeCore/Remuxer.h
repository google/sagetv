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
#ifndef _REMUXER_H_
#define _REMUXER_H_

#ifdef __cplusplus
extern "C" {
#endif

#define LANGUAGE_CODE( x ) LanguageCode( (unsigned char*)x )

typedef struct REMUXER_DUMPER
{
	DUMP  output_dumper;
	void* output_dumper_context;
	DUMP  avinf_dumper;
	void* avinf_dumper_context;
	DUMP  pmt_dumper;
	void* pmt_dumper_context;
} REMUXER_DUMPER;


typedef struct REMUXER
{
	unsigned short state;
	unsigned short task;
	struct DEMUXER* demuxer;

	struct PS_BUILDER *ps_builder;
	struct TS_BUILDER *ts_builder;
	struct TRACKS	  *output_track[MAX_PROGRAM_NUM];

	unsigned short output_format;
	int output_file;

	REMUXER_DUMPER dumper;

	uint32_t language_code;
	uint32_t remuxer_ctrl; 

} REMUXER;

//option: 01 disable PTSfix; 02 enable log EPG data;
int  RemuxFile(  unsigned short task, char* pInputFile, TUNE* pTune, int nInputFormat, 
			       char* pOutFile, int nOutputFormat, int nOption );
int  RemuxFileW(  unsigned short task, wchar_t* pInputFile, TUNE* pTune, int nInputFormat, 
			       wchar_t* pOutFile, int nOutputFormat, int nOption );
void* OpenRemuxStream( unsigned short nTask, TUNE* pTune, 
					   int nInputFormat, int nOutputFormat, 
					   MEM_ALLOC_HOOK pfnMemAlloc, void* pMemAllocContext,
					   DUMP pfnOutputDump, void* pOutputDumpContext );
int	 PushRemuxStreamData( void* Handle , unsigned char *pData, int nBytes, int *nExpectedBytes );
void CloseRemuxStream( void* Handle );
void ResetRemuxStream( void* Handle);
void ResetRemuxStreamSlot( void* Handle, int nSlot );
void FlushRemuxStream( void* Handle );
void ChangeRemuxOutputFormat( void* Handle, int nOutputFormat );
void SetupRemuxOutputBlockSize( void* Handle, int nSize );
void SetupRemuxStreamTune( void* Handle, TUNE *pTune );
void SetupRemuxTSStreamFormat( void* Handle, int nFormat, int nSubFormat );
void DisableMultipleAudio( void* Handle );
void EnableMultipleAudio( void* Handle );
int  CheckParserStart( void* Handle );
void SetTSStreamType( void* Handle, int nFormat, int nSubFormat );
void GetTSStreamType( void* Handle, int *pFormat, int *nSubFormat );
void LockUpTSParser( void* Handle );
void UnLockUpTSParser( void* Handle );
void DisableTSPSI( void *Handle );
void EnableTSPSI( void* Handle ); 
void DisabeTSPtsFix( void* Handle );
void DisableSubtitle( void* Handle );
void DisableTSRebuildStream( void* Handle );
void EnableTSRebuildStream( void* Handle );
void ChangeDemuxerTSStreamType( void* Handle, int nStreamType ); //MPEG_TS, MPEG_M2TS, MPEG_ASI
void SetupAVInfDump( void* Handle, DUMP pfnAVInfDump, void* pfnAVInfDumpContext );
void SetupEPGDump( void* Handle, DUMP pfnEPGDump, void* pEPGDumpContext );
void SetupEPGDumpLanguage( void* Handle, uint32_t lLanguageCode );
void SetupFastPESDump( void* Handle, DUMP pfnPESDumper, void* pPESDumperContext );
void SetDefaultAudioLanguage( void* Handle, uint32_t lLanguageCode );
uint32_t LanguageCode( unsigned char* pLanguage );
void UpdateClock( void* Handle, uint32_t lClock ); //units 1ms
void DisablePSBuildPading( void* Handle );
int  DemuxBlockSize( void* Handle  );
struct DEMUXER* GetDemuxer( void* Handle );
int CheckFormat( const unsigned char* pData, int nBytes );
int   time_stamp( LONGLONG llTime, char* pBuffer, int nSize );
int   long_long( ULONGLONG llVal, char* pBuffer, int nSize );
char* time_stamp_s( LONGLONG llTime, char* pBuffer, int nSize );
char* long_long_s( ULONGLONG llVal, char* pBuffer, int nSize );
char* long_long_ss( ULONGLONG llVal );
char* time_stamp_ss( LONGLONG llTime );
void  EnableTSAudioPriorityHack( void* Handle );
void  EnableWaitTSCleanStream( void* Handle );

void* CreateTSPacketDump( void* Handle, DUMP pfnStreamDump, 
		         void* pStreamDumpContext, DUMP pfnIndexDump, void* pIndexDumpContext );
void  ReleaseTSPacketDump( void* Handle );
int   StartTSPacketDump( void* Handle, int nFlag );
void  StopTSPacketDump( void* Handle );

void _scr_log( ULONGLONG scr );
unsigned char* _search_data_( unsigned char* match, int len, unsigned char* data, int data_size );

#ifdef __cplusplus
 }
#endif


#endif


