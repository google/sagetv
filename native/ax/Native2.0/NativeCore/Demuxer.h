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

#ifndef _DEMUXER_H
#define _DEMUXER_H


#define BUFFER_SIZE      (64*1024*2)
#define FIFO_QUEUE_SIZE  (64*2)

#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>

typedef struct TRACK_DEBUG
{
	FILE* track_dump_file[MAX_TRACK_NUM];
} TRACK_DEBUG;

#define MAIN_TRACK_ONLY		0x0001

typedef struct MEMORY_ALLOC
{
	MEM_ALLOC_HOOK memory_alloc_hook;
	void* memory_alloc_hook_context;

	unsigned short block_size;
	unsigned short local_mem_block_num; //total memery block number
	int8_t  *local_mem_block_state;
	int8_t **local_mem_block_addr;
	int8_t  *local_buffer_data;
	uint32_t  local_buffer_size;

	unsigned short inuse_num;

} MEMORY_ALLOC;

typedef struct DEMUXER_DUMPER
{
	DUMP  block_data_dumper;            //dump raw block data to whom want
	void  *block_data_dumper_context;
} DEMUXER_DUMPER;

typedef struct DEMUXER
{
	unsigned short state; //0: unknown; 1:parsing info, 2:output data 
	struct TS_PARSER *ts_parser;
	struct PS_PARSER *ps_parser;
	struct TRACKS    *tracks[MAX_SLOT_NUM];
	struct FIFO_BUFFER *fifo_buffer[MAX_SLOT_NUM];
	MEMORY_ALLOC memory_alloc[MAX_SLOT_NUM];
	unsigned short block_size;

	unsigned short source_format;
	
	int source_file;
	int output_file;

	//used for a file source, contain reading data from a file.
	uint32_t size;
	uint32_t input_buffer_size;
	uint8_t *data;
	uint8_t *input_buffer;       

	uint32_t language_code;
	uint32_t remuxer_ctrl; 

	DEMUXER_DUMPER dumper;

	LONGLONG	output_cue;
	unsigned short out_of_order_blocks;
	TRACK_DEBUG *track_debug[MAX_SLOT_NUM];

} DEMUXER;

DEMUXER* CreateDemuxer( int nStreamType, int nTrackNum, int nESBlockSize );
void ReleaseDemuxer( DEMUXER* pDemuxer );
void ResetDemuxerAll( DEMUXER* pDemuxer );
void ResetDemuxer( DEMUXER* pDemuxer, int nSlot );

int  PumpFileData( DEMUXER *pDemuxer, ULONGLONG lMaxLimitBytes, DUMP pfnProgressCallback, void* pCallbaclContext  );
int  OpenFileSource( DEMUXER *pDemuxer, char* pFileName, int nFileFormat,  TUNE* pTune );
int  OpenFileSourceW( DEMUXER *pDemuxer, wchar_t* pFileName, int nFileFormat,  TUNE* pTune );
void CloseFileSource( DEMUXER *pDemuxer );
int  OpenStreamSource( DEMUXER *pDemuxer, int nFileFormat, TUNE* pTune );
void CloseStreamSource( DEMUXER *pDemuxer );
void FlushDemuxer( DEMUXER* pDemuxer, int nSlot );

void* OpenDemuxStream( TUNE* pTune, 
					   int nInputFormat, int nOutputFormat, 
					   MEM_ALLOC_HOOK pfnMemAlloc, void* pMemAllocContext,
					   DUMP pfnOutputDump, void* pOutputDumpContext );
void CloseDemuxStream( void* Handle );
void ResetDemuxStream( void* Handle );
void FlushDemuxStream( void* Handle );
void SetupDemuxStreamTune( void* Handle, TUNE *pTune );
void SetupDemuxTSStreamFormat( void* Handle, int nFormat, int nSubFormat );
int	 PushDemuxStreamData( void* Handle, uint8_t *pData, int nBytes, int *nExpectedBytes );

void ResetBlockBuffer( DEMUXER* pDemuxer, int nSlot );
int  DetectFileType( char* pFileName );
int  DetectFileTypeW( wchar_t* pFileName );
void DisableAuxTrack( TRACKS *pTracks );
void SetupMemAllocHook(  DEMUXER* pDemuxer, MEM_ALLOC_HOOK pfnMemAlloc, void* pMemAllocContext );
void SetupMessageDumper( DEMUXER* pDemuxer, DUMP pfnMessageDmper, void* pMessageDumperContext );
void SetupBlockDataDumper( DEMUXER* pDemuxer, DUMP pfnBlockDataDmper, void* pMsgBlockDataDumperContext );
void SetupPCRDumper( DEMUXER* pDemuxer, DUMP pfnPCRDumper, void* pPCRDumperContext );
void SetupTSProgramDataDump( DEMUXER* pDemuxer, DUMP pfnProgramDataDump, void* pfnProgramDataContext );
void TracksIndexing( TRACKS *pTracks );
static inline TRACKS* GetTracks( DEMUXER* pDemuxer, int nSlot ) {	return pDemuxer->tracks[nSlot]; }
static inline int SourceIsPSType( DEMUXER* pDemuxer ) { return IS_PS_TYPE( pDemuxer->source_format );}
static inline int SourceIsTSType( DEMUXER* pDemuxer ) { return IS_TS_TYPE( pDemuxer->source_format );}
static inline void SetupDemuxTSTune( DEMUXER *pDemuxer, int nSlot, TUNE* pTune )    {  if IS_TS_TYPE( pDemuxer->source_format ) SetupTune( pDemuxer->ts_parser, nSlot, pTune ); }
static inline void DisableDemuxTSPSI( DEMUXER *pDemuxer )
		 { if IS_TS_TYPE( pDemuxer->source_format ) DisablePSI( pDemuxer->ts_parser ); }
static inline void EnableDemuxTSPSI( DEMUXER *pDemuxer )
         { if IS_TS_TYPE( pDemuxer->source_format ) EnablePSI( pDemuxer->ts_parser ); }
static inline void LockDemuxTSPPmt( DEMUXER *pDemuxer )
		 { if IS_TS_TYPE( pDemuxer->source_format ) DisablePatPmt( pDemuxer->ts_parser ); }
static inline void UnLockDemuxTSPPmt( DEMUXER *pDemuxer )
		 { if IS_TS_TYPE( pDemuxer->source_format ) EnablePatPmt( pDemuxer->ts_parser ); }
static inline int GetNumOfChannels( DEMUXER* pDemuxer, int nSlot )
		 { if IS_TS_TYPE( pDemuxer->source_format ) return pDemuxer->ts_parser->slot[nSlot].num_of_channels; else return 1; }
static inline int GetChannelNumber( DEMUXER* pDemuxer, int nSlot )
		 { if IS_TS_TYPE( pDemuxer->source_format ) return pDemuxer->ts_parser->slot[nSlot].tune.channel; else return 1; }
static inline int TSPacketLength( DEMUXER *pDemuxer ) { return pDemuxer->ts_parser->packet_length;   }
static inline int BlockSize( DEMUXER *pDemuxer ) { return pDemuxer->block_size;   }
static inline void EnableRebuildTSStream( DEMUXER *pDemuxer )
		 { if IS_TS_TYPE( pDemuxer->source_format )  EnableRebuildStream( pDemuxer->ts_parser ); }
static inline void DisableRebuildTSStream( DEMUXER *pDemuxer )
		 { if IS_TS_TYPE( pDemuxer->source_format )  DisableRebuildStream( pDemuxer->ts_parser ); }
static inline void SetupDemuxerTSStreamType( DEMUXER *pDemuxer, int nStreamType )
		 { if IS_TS_TYPE( pDemuxer->source_format )  SetupTSStreamType( pDemuxer->ts_parser , nStreamType ); }
		 
void	  DisablePTSFix( DEMUXER *pDemuxer );
void	  DisableSubtitleParsing( DEMUXER *pDemuxer );
void	  EnableAudioTSPriorityHack( DEMUXER *pDemuxer );
void      WaitTSCleanStream( DEMUXER *pDemuxer );
int       IsSageTVRecording( DEMUXER *pDemuxer );
void      PickupSageTVMainTrack( DEMUXER *pDemuxer, TRACKS* pTracks );
ULONGLONG DemuxUsedBytes( DEMUXER *pDemuxer );
void      QueueResetDemux( DEMUXER *pDemuxer );
void      QueueZeroDemux( DEMUXER *pDemuxer );
void	  QueueAbortDemux( DEMUXER *pDemuxer );
void	  SetZeroDemux( DEMUXER *pDemuxer );  //reset but not clean up all
void      ResetDemuxCommad( DEMUXER *pDemuxer );
ULONGLONG DemuxSourceSeekPos( DEMUXER *pDemuxer, ULONGLONG lPos, int SeekSet );
ULONGLONG DemuxSourceLength( DEMUXER *pDemuxer);
ULONGLONG DemuxSourceCurPos( DEMUXER *pDemuxer);
int PostDemuxMessage( DEMUXER *pDemuxer, MESSAGE_DATA *pMessage );
void      SetupPESDump( DEMUXER *pDemuxer, DUMP pfnPESDumper, void* pPESDumperContext );

int GetTSStreamFormat( DEMUXER *pDemuxer );
int GetTSStreamSubFormat( DEMUXER *pDemuxer );
uint32_t GetInputVideoPacketCount( DEMUXER *pDemuxer, int nSlot );
uint32_t GetInputAudioPacketCount( DEMUXER *pDemuxer, int nSlot );
int IsEncryptedTSChannel( DEMUXER *pDemuxer, int nSlot );
int IsAVDataPresent( DEMUXER *pDemuxer, int nSlot );
int IsVideoDataPresent( DEMUXER *pDemuxer, int nSlot );
int IsAudioDataPresent( DEMUXER *pDemuxer, int nSlot );
int IsEncryptedData( DEMUXER *pDemuxer, int nSlot );
void UpdateDemuxerClock( DEMUXER *pDemuxer, uint32_t lClock ); //units 1ms
LONGLONG SetDemuxPTSOffset( DEMUXER *pDemuxer, LONGLONG llPTSOffset );

void SetupTSATSDump( DEMUXER *pDemuxer, DUMP pfnATSDumper, void* pATSDumperContext );
//void* CreateTSPacketDumper( DEMUXER *pDemuxer, DUMP pfnStreamDump, 
//		         void* pStreamDumpContext, DUMP pfnIndexDump, void* pIndexDumpContext );
//void  ReleaseTSPacketDumper( void *pTSFilterDemuxer );
//int   StartTSPacketDumper( void *pTSFilterDemuxer, int nFlag );
//void  StopTSPacketDumper( void *pTSFilterDemuxer );

int   time_stamp( LONGLONG llTime, char* pBuffer, int nSize );
char* time_stamp_s( LONGLONG llTime, char* pBuffer, int nSize );
int   long_long( ULONGLONG llVal, char* pBuffer, int nSize );
char* time_stamp_s( LONGLONG llTime, char* pBuffer, int nSize );
char* long_long_s( ULONGLONG llVal, char* pBuffer, int nSize );
char* long_long_ss( ULONGLONG llVal );
char* time_stamp_ss( LONGLONG llTime );
int   CheckFormat( const uint8_t* pData, int nBytes );
void _scr_log( ULONGLONG scr );
uint8_t* _search_data_( uint8_t* match, int len, uint8_t* data, int data_size );

#ifdef __cplusplus
}
#endif


#endif

