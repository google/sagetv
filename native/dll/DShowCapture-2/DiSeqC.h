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
#ifndef _H_INCLUDE_DISEQC_H_
#define _H_INCLUDE_DISEQC_H_


#define MAX_LNB_SWITCH  8
typedef struct lnb_types_st {
	char	 name[64];
	int		 sat_no;
	unsigned long	low_val;
	unsigned long	high_val;	
	unsigned long	switch_val;	
	char pos;
	char option;
} LNB;

typedef struct DiSEQC_st {
	int  state;  //0: not ready, 1:ready
	char vendor[12];
	LNB  lnb[MAX_LNB_SWITCH];
	void* device; //proprietary device;
} DISEQC;

struct dvb_diseqc_master_cmd {
        unsigned char msg [6]; /*  { framing, address, command, data[3] } */
        unsigned int msg_len;  /*  valid values are 3...6  */
};

struct diseqc_cmd {
	struct dvb_diseqc_master_cmd cmd;
	unsigned long wait;
} ;

typedef struct diseqc_cmd DISEQCMD;


//Hauppauge DiSEQC 
const GUID KSPROPSETID_BdaTunerExtensionProperties =
{0xfaa8f3e5, 0x31d4, 0x4e41, {0x88, 0xef, 0x00, 0xa0, 0xc9, 0xf2, 0x1f, 0xc7}};


typedef enum
{
 BDA_TUNER_NODE = 0,
 BDA_DEMODULATOR_NODE
}BDA_NODES;

typedef enum
{
 KSPROPERTY_BDA_HAUP_DISEQC = 0,
 KSPROPERTY_BDA_HAUP_PILOT = 0x20,
 KSPROPERTY_BDA_HAUP_ROLL_OFF = 0x21
} KSPROPERTY_BDA_TUNER_EXTENSION;

typedef enum DiseqcVer
{  
 DISEQC_VER_1X=1,
 DISEQC_VER_2X,
 ECHOSTAR_LEGACY,	// (not supported)
 DISEQC_VER_UNDEF=0	// undefined (results in an error)
} DISEQC_VER;

typedef enum RxMode
{
 RXMODE_INTERROGATION=1, // Expecting multiple devices attached
 RXMODE_QUICKREPLY,      // Expecting 1 rx (rx is suspended after 1st rx received)
 RXMODE_NOREPLY,         // Expecting to receive no Rx message(s)
 RXMODE_DEFAULT=0        // use current register setting
} RXMODE;

const BYTE DISEQC_TX_BUFFER_SIZE = 150;	// 3 bytes per message * 50 messages
const BYTE DISEQC_RX_BUFFER_SIZE = 8;		// reply fifo size, do not increase

typedef struct _DISEQC_MESSAGE_PARAMS
{
 UCHAR      uc_diseqc_send_message[DISEQC_TX_BUFFER_SIZE+1];
 UCHAR      uc_diseqc_receive_message[DISEQC_RX_BUFFER_SIZE+1];
 ULONG      ul_diseqc_send_message_length;
 ULONG      ul_diseqc_receive_message_length;
 ULONG      ul_amplitude_attenuation;
 BOOL       b_tone_burst_modulated;
 DISEQC_VER diseqc_version;
 RXMODE     receive_mode;
 BOOL       b_last_message;
} HAUPPAUGE_DISEQC_CMD, *PDISEQC_MESSAGE_PARAMS;

typedef enum
{
 TONE_BURST_UNMODULATED = 0,
 TONE_BURST_MODULATED
} TONE_BURST_MODULATION_TYPE;

/*
typedef enum Roll_Off {
    HCW_ROLL_OFF_NOT_SET = -1,
    HCW_ROLL_OFF_NOT_DEFINED = 0,
    HCW_ROLL_OFF_20 = 1,         // .20 Roll Off (DVB-S2 Only)
    HCW_ROLL_OFF_25,             // .25 Roll Off (DVB-S2 Only)
    HCW_ROLL_OFF_35,             // .35 Roll Off (DVB-S2 Only)
    HCW_ROLL_OFF_MAX,
} Roll_Off;
 
typedef enum Pilot {
    HCW_PILOT_NOT_SET = -1,
    HCW_PILOT_NOT_DEFINED = 0,
    HCW_PILOT_OFF = 1,           // Pilot Off (DVB-S2 Only)
    HCW_PILOT_ON,                // Pilot On  (DVB-S2 Only)
    HCW_PILOT_MAX,
} Pilot;
*/
///////////////////////////////////////////////////////////////////////////////////////////////
//FireDTV
///////////////////////////////////////////////////////////////////////////////////////////////


typedef struct _FIREDTV_DISEQC_CMD {
	UCHAR Voltage;
	UCHAR ConTone;
	UCHAR Burst;
	UCHAR NrDiseqCmds;
	struct {
		UCHAR Length;
		UCHAR Framing;
		UCHAR Address;
		UCHAR Command;
		UCHAR Data[3];
	} DiseqcCmd[3];
} FIREDTV_DISEQC_CMD;

typedef struct _FIRESAT_POWER_STATUS {
	UCHAR uPowerStatus;
} FIRESAT_POWER_STATUS;

#define FIREDTV_POWER_ON 0x70
#define FIREDTV_POWER_OFF 0x60

static const GUID KSPROPSETID_Firesat = { 0xab132414, 0xd060, 0x11d0, { 0x85, 0x83, 0x00, 0xc0, 0x4f, 0xd9, 0xba,0xf3 } };
typedef struct _FIRESAT_DRIVER_VERSION { 
	char strDriverVersion[64]; 
}FIRESAT_DRIVER_VERSION, *PFIRESAT_DRIVER_VERSION;


///////////////////////////////////////////////////////////////////////////////////////////////
//Anysee
///////////////////////////////////////////////////////////////////////////////////////////////
#define GUID_ANYSEE_CAPTUER_FILTER_PROPERTY { 0xb8e78938, 0x899d, 0x41bd, { 0xb5, 0xb4, 0x62, 0x69, 0xf2,  0x80, 0x18, 0x99 } }
static const GUID  KSPROPSETID_Anyseesat = { 0xb8e78938, 0x899d, 0x41bd, { 0xb5, 0xb4, 0x62, 0x69, 0xf2,  0x80, 0x18, 0x99 } };
#define USERPROPERTY_SEND_DiSEqC_DATA   24
#define USERPROPERTY_READ_PLATFORM 6 


typedef enum _EnumDiSEqCToneBurst_ {

	No_DiSEqCToneBurst_ = 0,
	SA_DiSEqCToneBurst_,
	SB_DiSEqCToneBurst_
}	EnumDiSEqCToneBurst_;
            

typedef struct _ANYSEE_DISEQC_CMD {
     KSPROPERTY Property; 
     DWORD dwLength;       // DiSEqC Data(include command) Length
     BYTE Data[16];       // DiSEqC Data
     BYTE ToneBurst;      // EnumDiSEqCToneBurst
	 BYTE DUMY1;		  //Anysee aligment 4 bytes
	 BYTE DUMY2;
	 BYTE DUMY3;
} ANYSEE_DISEQC_CMD;

///////////////////////////////////////////////////////////////////////////////////////////////
//
///////////////////////////////////////////////////////////////////////////////////////////////
void ReleaseDiSEQC( DShowCaptureInfo* pCapInfo );
HRESULT SendDiSEQCCmd( DShowCaptureInfo* pCapInfo, unsigned long freq, int polarisation, unsigned short sat_no );
int SetupSatelliteLNB( DShowCaptureInfo* pCapInfo, int bReload  );

//for anysee test code
HRESULT SetupTunerNIM( DShowCaptureInfo* pCapInfo, unsigned long dwFreqKHZ, int dwModulation, unsigned long SymRate );

#endif
