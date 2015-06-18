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

#ifndef  _CHANNEL_C_
#define  _CHANNEL_C_

#if defined(__APPLE__) || defined(__linux__)
#include <stdbool.h> // clear up bool type confusion in the extern "C" block
#endif

#ifndef _WIN32
#define _MAX_PATH 	256
#endif

#define TIMEOUT_CNT    6
#define BUFFERSIZE 188*24

#define TS_BUFFER_PACKETS   24
#define TS_PACKET_LENGTH    188
#define COUNTRY_REGION_LEN   512


#define MAX_DVB_SCAN_ENTRY 100
#define MAX_ATSC_FREQ   100
#define MAX_QAM_FREQ    200
#define MAX_DVBS_FREQ   300
#define MAX_DVBS_SCAN_ENTRY 200

#define ATSC_NETWORK_TYPE	1
#define DVB_NETWORK_TYPE	2
#define UNKNOW_NETWORK_TYPE 0


#define ATSC_FREQ_TYPE			1
#define NTSC_CABLE_FREQ_TYPE		2
#define NTSC_HRC_FREQ_TYPE		3

#define MAX_SATELLATE_NUM  6

#ifndef bool
#define bool int
#endif
#ifndef false
#define false 0
#endif
#ifndef true
#define true 1
#endif

//data structure byte packing throughout
#ifdef WIN32
#if( _MSC_VER <= 800 )
#pragma pack(1)  
#else
#include <pshpack1.h>
#endif
#endif

#if defined(__APPLE__)
#pragma pack(push,1)
#endif

#ifdef __cplusplus
extern "C" {
#endif

#define NONE_FREQ_SCHEME			0
#define ATSC_FREQ_SCHEME			1
#define NTSC_FREQ_SCHEME			2
#define HRC_FREQ_SCHEME				3
#define USER_FREQ_SCHEME			4
#define PRIVTE1_FREQ_SCHEME			5    //used by OnAir Tuner for QAM (doesn't need modulation pass in)

struct bcast 
{
	int ch;
	unsigned long fq;
};

struct bcast_qam 
{
	int ch;
	unsigned long fq;
	char mod;
	char inv;
};

typedef struct {
	unsigned short index;
	unsigned short major;
	unsigned short minor;
	unsigned short program;
	unsigned short physical_ch;
	unsigned long  frequency;
	unsigned short video_pid;
	unsigned short audio_pid;
	unsigned long ctrl;
	char name[32];
} ATSC_FREQ;

typedef struct {
	unsigned short index;
	unsigned short major;
	unsigned short minor;
	unsigned short tsid;
	unsigned short program;
	unsigned short physical_ch;
	unsigned long  frequency;
	unsigned char  modulation;
	unsigned char  inversal;
	unsigned short video_pid;
	unsigned short audio_pid;
	unsigned long  ctrl;
	char name[32];
} QAM_FREQ;

typedef struct {
	unsigned short index;
	unsigned short onid;
	unsigned short tsid;
	unsigned short sid;
	unsigned short physical_ch;
	unsigned long  frequency;
	unsigned long  bandwidth;
	unsigned char  code_rate_hp;
	unsigned char  code_rate_lp;
	unsigned char  trans_mode;
	unsigned char  guard_interval;
	unsigned char  hierarchy;
	unsigned char  constellation;
	unsigned short video_pid;
	unsigned short audio_pid;
	unsigned long  ctrl;
	char name[32];
	unsigned long  shift;
} DVB_T_FREQ;

typedef struct {
	unsigned short index;
	unsigned short onid;
	unsigned short tsid;
	unsigned short sid;
	unsigned short physical_ch;
	unsigned long  frequency;
	unsigned long  symbol_rate;
	unsigned char  fec_inner;
	unsigned char  fec_inner_rate;
	unsigned char  fec_outer;
	unsigned char  fec_outer_rate;
	unsigned char  modulation;
	unsigned short video_pid;
	unsigned short audio_pid;
	unsigned long  ctrl;
	char name[32];
} DVB_C_FREQ;

typedef struct {
	unsigned short index;
	unsigned short onid;
	unsigned short tsid;
	unsigned short sid;
	unsigned short physical_ch;
	unsigned long  frequency;
	unsigned long  symbol_rate;
	unsigned char  polarisation;
	unsigned char  fec_inner;
	unsigned char  fec_inner_rate;
	unsigned char  fec_outer;
	unsigned char  fec_outer_rate;
	unsigned char  modulation;
	unsigned char  roll;
	unsigned char  pilot;
	short          orbit;
	unsigned short video_pid;
	unsigned short audio_pid;
	unsigned long  ctrl;
	char name[32];
	unsigned short sat_no;
} DVB_S_FREQ;

typedef struct {
	int  totalNum;
	int  entryNum;
	char sourceType[16];
	union {
		ATSC_FREQ*  atsc;
		QAM_FREQ*   qam;
		DVB_T_FREQ* dvbt;
		DVB_C_FREQ* dvbc;
		DVB_S_FREQ* dvbs;
	} tbl;
} FREQTBL;


typedef struct sat_name {
	char	        name[64];
	unsigned short  sat_no;
} SAT_NAME;

typedef struct Channel
{
	void* Dev;          //hold device data
	int   scanType;		//0: normal scan, 1: load scan table and bypassing real scan
	unsigned long lastTune;
	int  outputFormat;
	int  freqTableType;	       //1:ATSC, 2:NTSC-CABLE, 3:NTSC-HRC, 4:user defined
	int  guessFreqTableType;
	char sourceType[8];        //ATSC, QAM, DVB-T, DVB-C, DVB-S, DVB-S2
	char countryRegion[COUNTRY_REGION_LEN];
	int  countryCode;
	char tunerMode[16];         //obsolete
	int  lockTimeout;
	int      freqScheme;        //freqTable's freq type: 1:ATSC, 2:NTSC-CABLE, 3:NTSC-HRC, 4:user defined
	FREQTBL  FreqTable;
	FREQTBL* PreDefinedFreqTable;
	int  delayParse;     //postponde to start parser im million seconds
	unsigned long  freqShift;
	int	 frqTableUpdateCheck;
	unsigned long frqTableTimeStamp;
	char frqTableFileName[256];
	char frqTableFilePath[128];
	int  tuneCtrl;             //1: naked qam tune.
} CHANNEL_DATA;

extern struct bcast ATSC_BRCAST[256];
extern struct bcast NTSC_CABLE[256];
extern struct bcast NTSC_HRC[256];

//import API
int SageTuneATSCChannel( void* Capture, ATSC_FREQ* atsc, int dryTune );
int SageTuneQAMFrequency( void* Capture, QAM_FREQ* qam , int dryTune ); 
int SageTuneDVBTFrequency( void* Capture, DVB_T_FREQ* dvbt, int dryTune);
int SageTuneDVBCFrequency( void* Capture, DVB_C_FREQ* dvbc, int dryTune);
int SageTuneDVBSFrequency( void* Capture, DVB_S_FREQ* dvbs, int dryTune);
int SageCheckLocked( void* Capture );
void SageDelay( void* Capture, unsigned long ms );

int SageTVLockTSChannel( void* Capture, void* pTune, int nFlag );
int SageTVScanChannel( void* Capture,   void* pTune );
int SageTVReleaseScanChannel( void* Capture );
int SageTVScanChannelState( void* Capture, int* pScanState, int* pFoundChannelNum, int nClock );
int SageTVScanChannelList( void* Capture, void** ppChannelList );
int SageTVScanTuneList( void* Capture, void** ppNit );
int SageSelectOuputFormat( void* Capture, int format ); //0:TS; 1:PS

char* SageGetTunerDeviceName( void* Capture );
int  SageGetSatelliteTble( void* Capture, SAT_NAME *sat_name, int max_sat );
void SageEnableCAM( void* Capture );
void SageDisableCAM( void* Capture );

void SageStartParser( void* Capture );
void SageStopParser( void* Capture  );
//int  SagePacketInputNum( void* Capture );

//int  SagePickupTSChannel( void* Capture, unsigned short channel, unsigned short program, char *channel_name, bool reset );
//int  SagePickupATSCChannel( void* Capture, unsigned short major, unsigned short minor, unsigned short program, char *channel_name, bool reset );
//void SageSetNetworkType( void* Capture,  unsigned short networkType );//network type 1:ATSC; 2:DVB
//int  SageChangeCAMChannel( void* Capture, int program );
//int  SageGetNetworkList(  void* Capture, char* Buf, int MaxBufSize );
//int  SageGetNetworkNum(  void* Capture );
//void SageGetTSDebugInfo( void* Capture, int Cmd, char* Buf, int BufSize );


//export API
int   openChannel( CHANNEL_DATA *Channel, void* Dev );
void  closeChannel( CHANNEL_DATA *Channel );
void* setChannelDev( CHANNEL_DATA *Channel, void* Dev );
void  setSourceType( CHANNEL_DATA *Channel, char* type );
char* getSourceType( CHANNEL_DATA *Channel );
void  setFreqType( CHANNEL_DATA *Channel,  int freqType );
int   getFreqType( CHANNEL_DATA *Channel );
void  setOutputFormat( CHANNEL_DATA *Channel,  int format );
int   getOutputFormat( CHANNEL_DATA *Channel );
void  setCountryCode( CHANNEL_DATA *Channel,  int CountryCode );
int   getCountryCode( CHANNEL_DATA *Channel );
void  setCountryRegion( CHANNEL_DATA *Channel,  char* Region );
char* getCountryRegion( CHANNEL_DATA *Channel );
void  setTunerMode( CHANNEL_DATA *Channel,  char* tunerMode );
char* getTunerMode( CHANNEL_DATA *Channel );
void  parserDelayTime( CHANNEL_DATA *Channel, unsigned long ms );
void  setQAMScheme(  CHANNEL_DATA *Channel, int QAMScheme );
unsigned short getProgramNum( void* pTune );

int   TranslateJWideString( char* buf_out, int buf_out_size, unsigned short* buf_in );
void* getTuningEntry(  CHANNEL_DATA *Channel, int tune_type, int major, int minor, int physical, int index );
int scanATSCChannel( CHANNEL_DATA* ch, char* tuningString, char** scanResult );
int scanQAMChannel(  CHANNEL_DATA* ch, char* tuningString, char** scanResult );
int scanDVBTChannel( CHANNEL_DATA* ch, char* tuningString, char** scanResult );
int scanDVBCChannel( CHANNEL_DATA* ch, char* tuningString, char** scanResult );
int scanDVBSChannel( CHANNEL_DATA* ch, char* tuningString, char** scanResult );
int loadScanTuningTable( CHANNEL_DATA *Channel );
int saveScanTuningTable( CHANNEL_DATA *Channel, char* file_ext );
bool tuneChannel( CHANNEL_DATA *channel, const char* tune_string );
unsigned long getChannelFreq( int physical_ch, int freqTableType, int shift );

char* makeDVBTFreqString( char*Buf, int Size,  DVB_T_FREQ * Freq  );
int TranslateJWideString( char* buf_out, int buf_out_size, unsigned short* buf_in );
int TranslateJWideString2( char* buf_out, int buf_out_size, unsigned short* buf_in );
int ConvertIllegalChar( char* filename );
int ConvertIllegalChar2( char* filename );

void enable_channel_log();
void disable_channel_log();
char* scanChannel( CHANNEL_DATA* channel, char* deviceName, char* tuningString, char* countryRegion, int streamType );
//int  UpdateDVBPreDefinedFreqTable( CHANNEL_DATA *Channel, char* NIT_list, int total_bytes, int List_num );

void SageTunerStart( void* Capture );
void SageTunerStop( void* Capture );
void SageLockATSCChannel( void* Capture, unsigned short major, unsigned short minor  );

int loadPredefinedQAMFreq( CHANNEL_DATA *Channel, struct bcast_qam *freqTbl, int max, int* freqScheme );
int loadCacheQAMFreq(  CHANNEL_DATA *Channel,  struct bcast_qam *freqTbl, int max );
int loadScanDataFile( CHANNEL_DATA *Channel );

void enableFrqTableUpdate( CHANNEL_DATA *Channel );
void setFrqFilePath( CHANNEL_DATA *Channel, char *FileLocation );

#ifdef __cplusplus
}
#endif

#if defined(__APPLE__)
#pragma pack(pop)
#endif

#endif
