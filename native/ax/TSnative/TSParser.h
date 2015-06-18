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

#if !defined( _TS_NATIVE_TS_PARSER_INCLUDED_ )
#define _TS_NATIVE_TS_PARSER_INCLUDED_


#include "TSnative.h"


#ifdef WIN32
#if( _MSC_VER <= 800 )
#pragma pack(1)  
#else
#include <pshpack1.h>
#endif
#else
//typedef _int64 long long
#ifndef _inline
 #define _inline inline
#endif
#endif


#define MAX_PAT_NUM					12

#define MASK_PMT_READY				0x01
#define MASK_MAPPING_READY			0x80
#define MASK_VIDEO_READY			0x02
#define MASK_AUDIO_READY			0x04
#define MASK_VIDEO_DATA_EXIST		0x20
#define MASK_AUDIO_DATA_EXIST		0x40

#define	MAX_PID_COLLECT     80
#define MAX_PCR_PIDS		MAX_PROGRAM*2

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
	unsigned short pid;      //pid;
	unsigned char  type;     //pid type; type<0x80 is section id, type>0x80 is assign type
	unsigned char  state;    //state, state 0x01 vaild pid bit, 0x80 ready bit
	unsigned long  hits;     //pid hit conter
	unsigned char  counter;  //continuty
	unsigned char  scrambled; //encrypted
	TS_SECTION     section;
} PID_REC;

typedef struct {
	unsigned short index;
	unsigned short program;
} CHANNEL_MAPPING;

typedef struct {
	unsigned short pid;
	unsigned short type;
} PID_TBL;

typedef struct {
	unsigned short channelID;
	unsigned short programID;
	unsigned long  pidNum;
	PID_TBL  *pids;
} PID_INF;

typedef	struct	
{
	STREAM_TYPE  StreamType;     //DBV, ATSC, Others(Firwire)
 
	short       TSChannelNum;    //Total Channel Number (A/V program number)

	unsigned short	Channel2Program[MAX_PROGRAM];     //channel Program lookup table
	short       SelectedChannel[MAX_PROGRAM];       //selected channel
	short       SelectedChannelNum;                 //total selected channel

	short       SelectedProgram[MAX_PROGRAM];       //selected Program  (it will be converted into channel )
	short       SelectedProgramNum;                 //total selected Program

	unsigned short WorkingProgram;					//working program from which a/v data is being parsed out
	unsigned short WorkingTSID;						//working TSID of PAT.
	unsigned short SelectedTSID;					//user specified TSID for PAT
	unsigned char  ChannelState[MAX_PROGRAM];       //channel state (PAT, PMT, A/V avaliable bits)
	unsigned char  PatState;                        //PAT state (PAT is ready)
	short		   LockPmtPat;						//provent update PAT PMT table

	TS_HEADER	TSHeader;                           //cuurent TS packet header
	TS_ADAPTION	TSAdapation;						//current TS packet adaption field
	TS_ADAPTION_OPTION TSOption;                    //current TS packet option field
	LONGLONG    PCR;
	LONGLONG    OPCR;
	unsigned short pcr_pid;							//PCR PID

	//Stream data
	TS_PAT		Pat;                                //PAT table
	TS_PMT		Pmt[ MAX_PROGRAM ];	                //PMT table array
	short		ESPidMap[ MAX_PROGRAM ][MAX_ES];    //mapping of ES index vs. PID.

	LONGLONG	  StreamPCR;
	unsigned long packet_pcr_rate;				    //stream rate: packet interval in 27M HZ unite
	unsigned long pcr_packet_syn;					//private variable to calc stream rate
	unsigned long StreamIntervalSum;                //private variable to calc stream rate
	unsigned long StreamIntervalNum;  			    //private variable to calc stream rate

	TS_PAT  PATs[MAX_PAT_NUM];						//cache pats
	unsigned short pats_num;
	unsigned short pats_index;
	PID_REC pid_array[MAX_PID_COLLECT];				//pid hit table for reconstruct PAT/PMT table
	bool    enable_pid_stat;
	unsigned short pcr_pids[MAX_PCR_PIDS];

	//Statistic	
	unsigned long packets;
	unsigned long audio_frames;	
	unsigned long video_frames;	
	unsigned long pat_frames;
	unsigned long pmt_frames;
	unsigned long cat_frames;
	unsigned long err_frames;

	LPFNParserDump av_dumper;                      //video Audio data callback function
	LPFNParserDump signal_dumper;                  //state change callback function
	LPFNParserDump section_dumper;                 //section data callback function
	LPFNParserDump PAT_dumper;					   //dump PAT table
	LPFNParserDump PMT_dumper;					   //dump PMT table
	void* section_dumper_context;
	void* PMT_dumper_context;
	void* PAT_dumper_context;
	void*    env;								   //caller enviroment context, used by dumper call back.

	SI_PARSER_DUMPER si_dumper;                    //pass data to parse service information
	void*     si_env;							   //service information context	
	bool	  bChannelInfReady;					   //if channel infomation isn't ready, keep dumpping PMT 

	LONGLONG	  data_pos;                        //a packet position = data_pos + data_offset
	unsigned long data_offset;                     //

    unsigned short pid_filter_num;				    //pids number in soft pid filter
	unsigned short pid_filter_tbl[MAX_PID_COLLECT];	//soft pid filter table

	char  TSBuf[ TS_PACKET_LENGTH * 3 ];	//aligment buffer
	short TSBytes;							//bytes of data in buffer
	bool  abort;							//abort data processing in TSProcessBlock

} TS_PARSER;

_inline bool CheckContinuity( TS_HEADER* pTSHeader, unsigned char* pContinuity );
bool UnpackSectionData( bool bStartFlag, TS_SECTION* pSection, const unsigned char*  pbData, int nSize );
unsigned long GetCRC32( const unsigned char *pData, int len );

TS_PARSER* TSParserOpen() ;
void TSParserClose( TS_PARSER* pParser );
void ResetParser( TS_PARSER* pParser ) ;

LONGLONG TSDataPosition( TS_PARSER* parser );
void   TSResetDataPosition( TS_PARSER* parser );

// Sets up the callback for data output from the parser
void SetStreamType( TS_PARSER* pParser, STREAM_TYPE StreamType );

// Sets up the callback for data output from the parser
void SetTSDumpAVCallback( TS_PARSER* parser,  LPFNParserDump dumper );
void SetTSDumpSignalCallback( TS_PARSER* pParser,  LPFNParserDump dumper );
void SetTSDumpPATCallback(TS_PARSER* pParser,  LPFNParserDump dumper, void* context);
void SetTSDumpPMTCallback(TS_PARSER* pParser,  LPFNParserDump dumper, void* context);
void SetTSDumpSectionCallback(TS_PARSER* parser,  LPFNParserDump dumper, void* context); //for testing

// Sets the java object pointer for callbacks. The java
// object must implement TSParserCallback
//void SetParserCallback(jobject parserCallback);

// outProgramInfo will hold the PROGRAM_INFO for the return data. 
// When this function is called, numPrograms specifies the length of the
// outProgramInfo array. When it returns it specifies how many elements
// in that arrray were used.
int GetProgramNumber( TS_PARSER* pParser );
int GetProgramList( TS_PARSER* parser, PROGRAM_INFO* outProgramInfo, int numPrograms);
int GetStreamList( TS_PARSER* pParser, short ChannelNum , AVSTREAM_INFO* outStreamInfo, int numPrograms );
int CheckProgramVaild( TS_PARSER* pParser, unsigned short ProgramId );
int GetVideoProgramNumber( TS_PARSER* pParser );
short GetProgramNum( TS_PARSER* pParser, short ChannelNum );
int GetStreamNum( TS_PARSER* pParser, short ChannelNum );
int GetStreamList( TS_PARSER* pParser, short ChannelNum, AVSTREAM_INFO* outStreamInfo, int numPrograms );
unsigned short GetProgramTSID( TS_PARSER* pParser );

// Selects which program should have its data passed through the parser.
bool SelectChannel( TS_PARSER* pParser, unsigned short ChannelNum );
int  SelectProgram( TS_PARSER* pParser, unsigned short ProgramNum );
void SelectTSID(  TS_PARSER* pParser, unsigned short tsid );
int  GetTSProgramNum( TS_PARSER* pParser, unsigned short Channnel );
int  GetTSChannelNum( TS_PARSER* pParser, unsigned short ProgramNum );
unsigned short GetTSTSID( TS_PARSER* pParser );
bool IsScrambledChannel( TS_PARSER* pParser, unsigned short channel );
bool IsScrambledProgram( TS_PARSER* pParser, unsigned short Program );
bool PickupChannel( TS_PARSER* pParser, unsigned short ChannelNum );
int  PickupProgram( TS_PARSER* pParser, unsigned short ProgramNum );

void TSProcessBlock( TS_PARSER* pParser, int size, char* data );
void TSResetBlockBuffer( TS_PARSER* pParser );

bool PCRAvailable(  TS_PARSER* pParser );
unsigned short GetProgramPcrPID( TS_PARSER* pParser );
bool IsValidPcrPID( TS_PARSER* pParser, unsigned short pid );
unsigned short ReselectPCRPID( TS_PARSER* pParser );

int  TSLockPatPmt( TS_PARSER* pParser );
int  TSUnLockPatPmt( TS_PARSER* pParser );

bool ConstructPMT( TS_PARSER* pParser );
bool CreatePMT( TS_PARSER* pParser, PID_TBL* pPidInf, int numPids );
void TSPidStatEnable( TS_PARSER* pParser, bool bEnable );
int  FindPlayableChannel( TS_PARSER* pParser );
int  IsPlayableChannel( TS_PARSER* pParser, int channel );

int GetPMTDescData( TS_PARSER* pParser, unsigned short pid, unsigned char** desc, int *bytes );
int GetTelexLanguageInfo( unsigned char* desc, int bytes, unsigned long* language, unsigned char *type );
int GetAudioLanguageInfo( unsigned char* desc, int bytes, unsigned long* language, unsigned char *type );
int GetVideoBkgrdInfo( unsigned char* desc, int bytes, unsigned short *hor, unsigned short *ver, unsigned char* aspect );
int GetVideoWindowInfo( unsigned char* desc, int bytes, unsigned short *hor, unsigned short *ver, unsigned char* priority );
void StopParser( TS_PARSER* pParser );
void StartParser( TS_PARSER* pParser );
unsigned long PidHits( TS_PARSER* pParser, unsigned short pid );
unsigned long TotalPidHits( TS_PARSER* pParser );
unsigned long ProgramPidHits( TS_PARSER* pParser, unsigned short Program );
unsigned long ChannelPidHits( TS_PARSER* pParser, unsigned short Channel );
bool IsDVBStream( TS_PARSER* pParser );
unsigned long GetPacketCounter( TS_PARSER* pParser );

//LONGLONG GetEstimatePCR( TS_PARSER* pParser );
unsigned long EstimatePCRIncrease( TS_PARSER* pParser, unsigned long packets );
int  CheckProgramState( TS_PARSER* pParser, unsigned short Program );
int  GetProgramAVType( TS_PARSER* pParser, unsigned short Program ); //0: no AV; 1:video only; 2:audio only; 3:video+audio; -1:unkown;
void ResetTSSoftPidFilter( TS_PARSER* pParser );
int  SetTSSoftPidFilter( TS_PARSER* pParser, unsigned short pid );
int  CheckTSIDValid( TS_PARSER* pParser, unsigned short tsid ); //valid tsid: return 1; uncertainty: return 0; invalid: returnn -1
int  GetTSIDList( TS_PARSER* pParser, unsigned short* tsid_list, int max_size );

#ifdef __cplusplus
}
#endif

#endif
