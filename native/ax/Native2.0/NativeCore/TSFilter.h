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

#ifndef TSFILTER_H
#define TSFILTER_H

#ifdef __cplusplus
extern "C" {
#endif


#include "SectionData.h"

#define PACK_PMT  1

#define DEFAULT_PAT_NUM	    6
#define DEFAULT_PMT_NUM     80
#define DEFAULT_TS_STREAM   8

#ifdef  PACK_PMT           
//PACK PMT: drop (type=5) private sections stream  ISO/IEC 19818-1, and 
//( type=0x0b ) DSM-CC U-N message ISO/IEC13818-6 in a PMT, so we have a samll size PMT table 
//( type=0x90 ) user private data in BlueRay data
#define MAX_ES		       24   //ZQ maxium on I saw is 20 (a buleray )
#else
#define MAX_ES		       6
#endif

#define MAX_PMT_NUM	       80

#define MAX_PID_HIST_NUM   (MAX_ES+1)
#define PID_HIST_THRESHOLD 0x3000

#define	TS_PACKET_LENGTH    188
#define	M2TS_PACKET_LENGTH  192
#define ASI_PACKET_LENGTH   208
#define TS_SYNC				0x47         /* 'G' */

#define SECTION_UPDATED		0x01
#define PAT_UPDATED			0x02
#define PMT_UPDATED			0x04
#define PMT_REPEAT			0x08


#define PID_TYPE_PROGRAM 1
#define PID_TYPE_VIDEO	 2
#define PID_TYPE_AUDIO	 3
#define PID_TYPE_PCR	 4
#define PID_TYPE_PSI	 5
#define PID_TYPE_CA		 4

///////////////////////////////////////////////////
/*
 0x00  "reserved",
 0x01  "ISO 11172     Video",
 0x02  "ISO 13818-2   Video",
 0x03  "ISO 11172     Audio",
 0x04  "ISO 13818-3   Audio",
 0x05  "ISO 13818-1   private sections",
 0x06  "ISO 13818-1   private data",
 0x07  "ISO 13522     MHEG",
 0x08  "ISO 13818-1   Annex A DSS CC",
 0x09  "ITU-T H.222.1",
 0x0a  "ISO 13818-6   type A",
 0x0b  "ISO 13818-6   type B",
 0x0c  "ISO 13818-6   type C",
 0x0d  "ISO 13818-6   type D",
 0x0e  "ISO 13818-6   auxiliary",
 0x0f - 0x7f "reserved",
 0x80 - 0xff "user private",
*/

#define VIDEO_STREAM_TYPE			0x02	
#define AUDIO_STREAM_TYPE			0x03
#define AUDIO_STREAM1_TYPE			0x04
#define PRIVATE_STREAM_TYPE			0x06
#define VC1_STREAM_TYPE				0xea
#define MPEG4_STREAM_TYPE			0x10
#define H264_STREAM_TYPE			0x1b
#define AAC_STREAM_TYPE				0x0f
#define AAC_HE_STREAM_TYPE			0x11
#define AC3_STREAM_TYPE				0x81
#define DTS_STREAM_TYPE				0x82
#define TUREHD_AC3_STREAM_TYPE		0x83
#define E_AC3_STREAM_TYPE			0x84
#define HD_DTS_STREAM_TYPE			0x85
#define HD_MASTER_DTS_STREAM_TYPE	0x86
#define E_AC3_STREAM2_TYPE			0x87

#define PRIVATE_0x80_STREAM_TYPE    0x80
#define LPCM_STREAM_TYPE			0x80

#define UNKNOW_VIDEO_STREAM_TYPE	0xF1
#define UNKNOW_AUDIO_STREAM_TYPE	0xF2

///////////////////////////////////////////////////
#define REGISTRATION_DESC			0x05
#define CA_DESC						0x09
#define ISO639_LANGUAGE_DESC		0x0a
#define DVB_PRIVATE_INDICATOR		0x0f
#define DVB_BITRATE					0x0e
#define DVB_AC3_DESC				0x6a
#define ATSC_AC3_DESC				0x81
#define SCTE_EXTENDED_VIDEO			0x82
#define ATSC_CAPTION_DESC			0x86
#define MPEG4_AUDIO_DESC			0x1c
#define DTS_DESC					0x73
#define DVB_EAC3_DESC				0x7A
#define DVB_DTS_DESC				0x7B
#define DVB_AAC_DESC				0x7C
#define VIDEO_DESC					0x02
#define AUDIO_DESC					0x03
#define TELTEXT_DESC				0x56
#define SUBTITLE_DESC				0x59
#define VBI_DATA_DESC				0x45
#define VBI_TELTEXT					0x46
#define DVB_STREAM_ID				0x52
#define DVB_PRIVATE_SPEC			0x5f
#define DVB_REFERENCE_ID			0xd1
#define DVB_USER_DESC				0xfe
///////////////////////////////////////////////////

#define FAST_FILTER_SET			0x01
#define FAST_FILTER_DROP_TABLE	0x02
#define FAST_FILTER_DROP_STREAM	0x04
#define FAST_FILTER_DROP_PSINF	0x08
#define FAST_FILTER_DROP_ALL	0xff


/*
Private stream 1 (Audio and subpictures)
sub-stream 0x20 to 0x3f are subpictures
sub-stream 0x80 to 0x87 are audio (AC3, DTS, SDDS)
sub-stream 0xA0 to 0xA7 are LPCM audio

sub-stream 0x80: MPEG2 video or PCM audio in TS; LPCM in M2TS. <<==
sub-stream 0x81: AC3 audio
sub-stream 0x82: DTS audio
sub-stream 0x83: TrueHD/AC3 interweaved audio
sub-stream 0x84: E-AC3 audio
sub-stream 0x85: DTS-HD High Resolution Audio
sub-stream 0x86: DTS-HD Master Audio
sub-stream 0x87: E-AC3 audio
sub-stream 0xA1: secondary E-AC3 audio
sub-stream 0xA2: secondary DTS audio
sub-stream 0xEA: VC-1 video
*/

//#define IsVideoType( StreamType	)  ( StreamType	== 2 ||	StreamType == 1	|| StreamType == 0xf1 || StreamType == 0x1b || StreamType == 0xea || StreamType == 0x80 || StreamType == 0x10  ) 	
//#define IsAudioType( StreamType	)  ( StreamType	== 3 ||	StreamType == 4	|| StreamType == 0x81 || StreamType == 0x82 || StreamType == 0x83 || StreamType == 0x84 || StreamType == 0x85 || StreamType == 0x86 ||  StreamType == 0x11 || StreamType == 0x0f ) 	
//#define IsVideoType( StreamType	)  ( StreamType	== 2 ||	StreamType == 1	|| StreamType == 0xf1 || StreamType == 0x1b || StreamType == 0xea || StreamType == 0x10  ) 	
#define IsVideoType( StreamType	)  ( StreamType	== 2 ||	StreamType == 1	|| StreamType == 0x1b || StreamType == 0xea || StreamType == 0x10 ) 	
#define IsAudioType( StreamType	)  ( StreamType	== 3 ||	StreamType == 4	|| StreamType == 0x11 || StreamType == 0x0f || (StreamType >= 0x81 && StreamType <= 0x87 ) ) 	
#define IsTVAuidoType( StreamType )( StreamType	== 3 ||	StreamType == 4	|| StreamType == 0x81 ||  StreamType == 0x11 || StreamType == 0x0f ) 	


typedef struct TS_FILTER_DUMPER
{
	DUMP  pat_dumper;
	void* pat_dumper_context;

	DUMP  pmt_dumper;
	void* pmt_dumper_context;

	DUMP  pcr_dumper;
	void* pcr_dumper_context;

	DUMP  stream_dumper;
	void* stream_dumper_context;

	DUMP  ca_dumper;
	void* ca_dumper_context;

	DUMP  ats_dumper;
	void* ats_dumper_context;

} TS_FILTER_DUMPER;

typedef struct {
	unsigned char* desc_ptr;
	unsigned short desc_bytes;
	unsigned short buffer_size;
} DESC_DATA;


//typedef struct DESC
//{
//	int length;
//	unsigned char* desc[MAX_DESC_INFO_LEN];
//} DESC;

typedef	struct	
{
	int			  discontinute;	
	int			  random_acceess;
	int			  elem_stream_prio;	
	unsigned int apapt_len;
	int		  flags;
} TS_ADAPTION;

typedef	struct TS_PACKET
{
	unsigned short pid;	
	unsigned char* data;
	int		      payload_offset;
	int		      payload_bytes;
	int			  error;
	int			  start;
	int			  priority;	
	int		  sync;
	int		  scrambling_ctr;
	int		  adaption_ctr;	
	int		  continuity_ct;
	int		  pcr_flag;
	ULONGLONG	  pcr;
	ULONGLONG	  opcr;
	TS_ADAPTION	  adaption;
} TS_PACKET;


//PAT table
typedef	struct	TS_PAT
{
	unsigned short tsid;
	unsigned short total_program_number;	
	unsigned short program_number[MAX_PMT_NUM];
	unsigned short program_pid[MAX_PMT_NUM];	

	unsigned char  update_flag;
	unsigned char  locked;
	unsigned long  section_crc32; //used to check pmt updating to set update_flag
	unsigned long  stamp;         //use it to drop expired/aged pat
} TS_PAT;

typedef struct
{
	TS_PAT     *pat_table;
	TS_SECTION *pat_section;
	short	   pat_index;
	short	   update_flag; //flag of undated PAT
} PAT_DATA;


/* Program Map data*/
typedef	struct	_TS_PMT
{
	unsigned short program_number;
	unsigned short pcr_pid;
	unsigned short ca_pid;
	unsigned short ca_id;
	DESC_DATA	   program_desc;

	unsigned short total_stream_number;
	unsigned short stream_pid[MAX_ES];
	unsigned char  stream_type[MAX_ES];	
	DESC_DATA      stream_desc[MAX_ES];

	unsigned long  section_crc32; //used to check pmt updating to set update_flag
	unsigned long  active_count;  //data packet counter, used to check an empty pmt
	unsigned char  update_flag;
	unsigned char  padding;
} TS_PMT;

typedef struct
{
	TS_PMT		*pmt_table;
	TS_SECTION	*pmt_section;
	unsigned short	pid;
	unsigned short	tsid;
	unsigned short	channel;
	unsigned short	total_channel_num; //total channels (programs) go with this channel
	short			update_flag; //flag upadted PMT
} PMT_DATA;

//mapping pid to program of PMT and PAT
typedef struct TS_PMT_MAP
{
	unsigned short pid;
	unsigned short pat_index;
	unsigned short program;
	unsigned short channel;
	unsigned short group_flag;  //multiple program share one pid
	unsigned char  hit_flag;
	unsigned char  padding;
} TS_PMT_MAP;

typedef struct STREAM_DATA
{
	unsigned char *data_ptr;
	unsigned short bytes;
	unsigned char  scrambling;
	unsigned char  group_start;
	unsigned char  priority;
	unsigned char  padding;
	TS_ELEMENT    *ts_elment;
	void          *container;
} STREAM_DATA;


typedef struct
{
	LONGLONG pcr;
	unsigned short pid;
	unsigned long  ts_packet_counter;
	void		   *container;
} PCR_DATA;


typedef struct
{
	unsigned short pid;
	unsigned short ca_id;
	unsigned short bytes;
	unsigned char *data_ptr;
	void		   *container;
} CA_DATA;


#define PCR_FLAG        0x01
#define SCRAMB_FLAG		0x02;

typedef struct
{
	unsigned short pid;
	unsigned short count;
	unsigned short flag;  //first bit for PCR flag, 2'd for scrambimg flg
} PID_HIST;

#define MAX_PID_TBL_NUM 6

typedef struct PID_ITEM
{
	unsigned short pid;
	unsigned short type;
	unsigned short service;
	unsigned short data;
} PID_ITEM;

typedef struct FAST_FILTER
{
	DUMP  pcr_dumper;
	void* pcr_dumper_context;

	DUMP  packet_dumper;
	void* packet_dumper_context;

	unsigned short pid_tbl_num;
	PID_ITEM pid_tbl[MAX_PID_TBL_NUM];
} FAST_FILTER;

typedef struct PACKET_DATA
{
	TS_PACKET*  packet;
	PID_ITEM	pid_item;
} PACKET_DATA;

#define TSFILTER_TAG  "TSFT"
typedef struct TS_FILTER
{
	//stream packet filter
	int ts_streams_num;
	struct TS_STREAMS *ts_streams[MAX_SLOT_NUM]; 

	//stream dumper
	TS_FILTER_DUMPER dumper;

	//PSI parser
	struct PSI_PARSER* psi_parser;

	//parser control
	short disable_ts_table_parse;
	short disable_psi_parse;
	short disable_stream_filter;

	//PAT
	int pat_num;
	TS_PAT  *pat;
	TS_SECTION* pat_section;

	//PMT
	int pmt_num;
	TS_PMT *pmt;
	TS_SECTION **pmt_section;

	//PAT-PMT map
	int map_num;
	int mapped_num;
	TS_PMT_MAP *pmt_map;

	PID_HIST  pid_hist[MAX_PID_HIST_NUM]; //catch most active 10 pids
	short     disable_pid_hist;
	unsigned  short pid_hist_num;
	unsigned  short pid_hist_ctrl;
	unsigned  long ts_packet_counter;
	
	FAST_FILTER fast_filter;

	char _tag_[4]; //debug tag
} TS_FILTER;


TS_FILTER* CreateTSFilter( int nPatNum, int nPmtNum, int nStreamFormat, int nSubFormat  );
void ReleaseTSFilter( TS_FILTER* pTSFilter );
void ResetTSFilter( TS_FILTER* pTSFilter );
int  TSProcess( TS_FILTER* pTSFilter, unsigned char* pData ); //must to be 188 bytes data
int  SelectTSFilterChannel( TS_FILTER* pTSFilter, struct TS_STREAMS* pTsStreams, unsigned short nTsid, unsigned short nProgram, unsigned short nMediaType );
int  SetupChannelFilter( TS_FILTER* pTSFilter, struct  TS_STREAMS* pTsStreams  );
int  BuildChannelTSFilter( TS_FILTER* pTSFilter, struct TS_STREAMS* pTsStreams );
int  CheckPacketContinuity( TS_PACKET *pTSPacket, unsigned char* pContinuity );
int  SetupChannelFilterByPids( TS_FILTER* pTSFilter, struct TS_STREAMS* pTsStreams, PIDS_TABLE *pPidTbl, int nMediaType  );
//DESC data
DESC_DATA* CreateDesc( );
void ReleaseDesc( DESC_DATA* pDesc );
void EraseDescData( DESC_DATA *pDesc );
unsigned char* FillDescData( DESC_DATA *pDesc, unsigned char* pData, int nBytes );
unsigned char* AppendDescData( DESC_DATA *pDesc, unsigned char* pData, int nBytes );
void ReleaseDescData( DESC_DATA *pDesc );
unsigned char* NewDescData( DESC_DATA* pDesc, int nBytes );

unsigned long AudioFormatByStreamType( unsigned char StreamType );
unsigned char* GetDescriptor( const unsigned char *pData, int Bytes, unsigned char Tag, int *pLength );
unsigned long  GetLanguageCode( unsigned char* pData, int nBytes );
unsigned char GetAudioType( unsigned char* pData, int nBytes );
char* Language( unsigned long lLanguageCode, char* pLanguage );
unsigned long LanguageCode( unsigned char* pLanguage );
unsigned long GetSubtitleLanuage( unsigned char *pData, int nBytes );

int BlastPatTable( TS_FILTER* pTSFilter, int nProgram, int nTsid );
int BlastPmtTable( TS_FILTER* pTSFilter, int nProgram, int nTsid );
unsigned long HasAudioDesc( unsigned char *pData, int nBytes );
char*  _dvb_service_type_( unsigned short type );
char*  _atsc_service_type_( unsigned short type );

#ifdef __cplusplus
}
#endif

#endif

