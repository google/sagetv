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

#include "stdafx.h"
#include <bdatypes.h>
#include "DShowCapture.h"
#include "../../include/guids.h"
#include "../../include/sage_DShowCaptureDevice.h"
#include "DShowUtilities.h"
#include "FilterGraphTools.h"
#include "../../include/iTSSplitter.h"
extern FilterGraphTools graphTools;
#include "AnySeeTuner.h"
#include "DiSeqC.h"
#include "uniapi.h"

#define FIREDTV_DISEQ			"FIREDTV   "  
#define TECHNOTREND_DISEQ		"TECHNTD   "  
#define HAUPPAUGE_DISEQ			"HAUPPAUGE "
#define ANYSEE_DISEQ			"ANYSEE  "
#define UNKOWN_DISEQ			"UNKNOWN "
static LNB predefined_lnbs[] = {
	{"UNIVERSAL",0,	9750000, 10600000, 11700000 },
 	{"DBS",		 0, 11250000, 0, 0    },
	{"STANDARD", 0,	10000000, 0, 0    },
	{"ENHANCED", 0,	9750000,  0, 0    },
	{"C-BAND",	 0, 5150000,  0, 0    },
	{"C-MULTI",	 0, 5150000,  5750000, 0 }
};


extern "C" { char *strtok_r (char *s, const char *delim, char **save_ptr); //defined in ChannelDataMng.c

bool GetIntVal( char*p, const char* Name, unsigned long* pVal );
bool GetWordVal( char*p, const char* Name, unsigned short* pVal );
bool GetString( char*p, const char* Name, char* Str, int MaxLen );
bool GetQuoteString( char*p, const char* Name, char* Str, int MaxLen );
int ConvertIllegalChar2( char* filename );
}

TV_TYPE GetTVType( DShowCaptureInfo *pCapInfo );

#ifdef WIN32  
#include <time.h>
static struct tm* localtime_r( time_t* t, struct tm *ltm )
{
	if ( ltm == NULL )
		return 0;
	*ltm = *localtime( t );
	return ltm;
}
#endif

#ifdef REMOVE_LOG	
#define flog(x) {}
#else
#define flog(x)   _flog x
#endif

extern int flog_enabled;

static void _flog( char* logname, const char* cstr, ... ) 
{
	time_t ct;
	struct tm ltm;
	va_list args;
	FILE* fp;
	
	if ( !flog_enabled ) return;

	fp = fopen( logname, "a" ); 
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );
	fprintf( fp, "%02d/%02d/%d %02d:%02d:%02d  ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
	ltm.tm_hour, ltm.tm_min, ltm.tm_sec );  
	va_start(args, cstr);
	vfprintf( fp, cstr, args );
	va_end(args);
	fclose( fp );
}


static void _flog_check()
{
	FILE* fp = fopen( "NATIVE_LOG.ENABLE", "r" );
	if ( fp != NULL )
	{
		flog_enabled = true;
		fclose( fp );
	}
}
/*
// Check to see that the Hauppauge PropertySet interface is supported
hr = PropSet->QuerySupported( KSPROPSETID_BdaTunerExtensionProperties, KSPROPERTY_BDA_HAUP_DISEQC, 
									&type_support);
// Send the previously prepared message to the driver
hr = PropSet->Set( KSPROPSETID_BdaTunerExtensionProperties,  KSPROPERTY_BDA_HAUP_DISEQC,
					&instance_data,  sizeof(instance_data), &DiSEqCCMD,
					sizeof(HAUPPAUGE_DISEQC_CMD) );

*/

int IsHauppaugeDiSEQCSupport( DShowCaptureInfo* pCapInfo )
{
	if ( (DISEQC *)pCapInfo->pDiSEQC != NULL )
	{
		DISEQC *pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;
		if ( !strcmp( pDiSEQC->vendor, HAUPPAUGE_DISEQ ) )
		{
			flog(("native.log", "DVB-S: Hauppauge DiSEQC support tuner\n" ) );
			return 1;
		}
		flog(("native.log", "DVB-S: Is not an Hauppauge DiSEQC tuner (%s)\n", pDiSEQC->vendor ) );
	}
	return 0;
}

static bool IsHauppaugeDiSEQC( DShowCaptureInfo* pCapInfo )
{
	HRESULT hr;
	IPin *pTunerPin; 
	IKsPropertySet* pKPS=NULL;
	DWORD type_support = 0;

	if ( pCapInfo->pBDATuner == NULL )
	{
		flog(("native.log", "DVB-S: Not a DVB-S tuner (%s) %s-%d \n", pCapInfo->tvType,
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return false;
	}

	TV_TYPE BDATVType = GetTVType( pCapInfo ); 
	if ( BDATVType != DVBS )
	{
		
		flog(("native.log", "DVB-S: invalid tuner type (%s) %s-%d \n", pCapInfo->tvType,
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return false;
	}

	pTunerPin = FindPin( pCapInfo->pBDATuner, PINDIR_INPUT, NULL, NULL);
	if ( pTunerPin == NULL )
	{
		flog(("native.log", "DVB-S: Hauppauge tuner pin not found %s-%d \n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return false;
	}

	hr = pTunerPin->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
	if ( FAILED( hr )) 
	{
		flog(("native.log", "DVB-S: Hauppauge tuner pin KPS not found %s-%d \n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		SAFE_RELEASE( pTunerPin );
		return false;
	}
	hr = pKPS->QuerySupported( KSPROPSETID_BdaTunerExtensionProperties, KSPROPERTY_BDA_HAUP_PILOT, &type_support  );
	if ( type_support == 0 )
	{
		flog(("native.log", "DVB-S: Hauppauge tuner doesn't not support DVB-S Pilot (%s-%d, hr:%x).\n", 
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr ) );

	} else
		flog(("native.log", "DVB-S: Hauppauge tuner  support DVB-S Pilot (%s-%d).\n", 
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );


	hr = pKPS->QuerySupported( KSPROPSETID_BdaTunerExtensionProperties, KSPROPERTY_BDA_HAUP_DISEQC, &type_support  );
	if ( type_support == 0 )
	{
		flog(("native.log", "DVB-S: Hauppauge tuner doesn't not support DVB-S DiSEQC (%s-%d, hr:%x).\n", 
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr ) );
		SAFE_RELEASE( pKPS );
		SAFE_RELEASE( pTunerPin );

		
//ZQ testing code for WinTV USB DVB-S, dev in Hauppauge isn't sure ask us to try different one
		if ( 0 ) {
		    flog(("native.log", "DVB-S: ..............Try DVB-S DiSEQC on Output Pin \n" ));
			pTunerPin = FindPin( pCapInfo->pBDATuner, PINDIR_OUTPUT, NULL, NULL);
			if ( pTunerPin == NULL )
			{
				flog(("native.log", "DVB-S: Hauppauge tuner outpin not found %s-%d \n", 
						pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
				return false;
			}

			hr = pTunerPin->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
			if ( FAILED( hr )) 
			{
				flog(("native.log", "DVB-S: Hauppauge tuner pin KPS not found %s-%d \n", 
						pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
				SAFE_RELEASE( pTunerPin );
			} else
			{
				hr = pKPS->QuerySupported( KSPROPSETID_BdaTunerExtensionProperties, KSPROPERTY_BDA_HAUP_DISEQC, &type_support  );
				if ( type_support == 0 )
				{
					flog(("native.log", "DVB-S: Hauppauge tuner doesn't not support DVB-S DiSEQC (%s-%d, hr:%x)..\n", 
							pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr ) );
					SAFE_RELEASE( pKPS );
					SAFE_RELEASE( pTunerPin );
				}
			}

		    flog(("native.log", "DVB-S: ..............Try DVB-S DiSEQC on Capture Filter \n" ));
			pTunerPin = FindPin( pCapInfo->pBDACapture, PINDIR_OUTPUT, NULL, NULL);
			if ( pTunerPin == NULL )
			{
				flog(("native.log", "DVB-S: Hauppauge capture input not found %s-%d \n", 
						pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
				return false;
			}

			hr = pTunerPin->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
			if ( FAILED( hr )) 
			{
				flog(("native.log", "DVB-S: Hauppauge tuner pin KPS not found %s-%d \n", 
						pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
				SAFE_RELEASE( pTunerPin );
			} else
			{
				hr = pKPS->QuerySupported( KSPROPSETID_BdaTunerExtensionProperties, KSPROPERTY_BDA_HAUP_DISEQC, &type_support  );
				if ( type_support == 0 )
				{
					flog(("native.log", "DVB-S: Hauppauge tuner doesn't not support DVB-S DiSEQC (%s-%d, hr:%x)...\n", 
							pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr ) );
					SAFE_RELEASE( pKPS );
					SAFE_RELEASE( pTunerPin );
				}
			}

		}



		return false;
	}

	flog(("native.log", "DVB-S: Hauppauge tuner supports DVB-S DiSEQC (%s-%d, hr:%x).\n", 
				 pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr ) );
	SAFE_RELEASE( pKPS );
	SAFE_RELEASE( pTunerPin );
	return true;

}


static HRESULT HauppaugeDVBSSetup( DShowCaptureInfo* pCapInfo, unsigned long type, void* pData, int dwDataSize )
{
	HRESULT hr;
	IPin *pTunerPin; 
	IKsPropertySet* pKPS=NULL;
	DWORD type_support = 0;

	if ( pCapInfo->pBDATuner == NULL )
	{
		return E_FAIL;
	}

	KSP_NODE instance_data;
	TV_TYPE BDATVType = GetTVType( pCapInfo ); 
	if ( BDATVType != DVBS )
	{
		
		flog(("native.log", "DVB-S: Not DVB-S tuner (%s) %s-%d \n", pCapInfo->tvType,
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return E_FAIL;
	}

	pTunerPin = FindPin( pCapInfo->pBDATuner, PINDIR_INPUT, NULL, NULL);
	if ( pTunerPin == NULL )
	{
		flog(("native.log", "DVB-S: Hauppauge tuner pin not found %s-%d \n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return E_FAIL;
	}

	hr = pTunerPin->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
	if ( FAILED( hr )) 
	{
		flog(("native.log", "DVB-S: Hauppauge tuner pin KPS not found %s-%d \n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		SAFE_RELEASE( pTunerPin );
		return E_FAIL;
	}

	hr = pKPS->QuerySupported( KSPROPSETID_BdaTunerExtensionProperties, type, &type_support  );
	if ( type_support == 0 )
	{
		flog(("native.log", "DVB-S: Hauppauge tuner doesn't not support DVB-S type:%d %s-%d.\n", 
				type, pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		SAFE_RELEASE( pKPS );
		SAFE_RELEASE( pTunerPin );
		return E_FAIL;
	}

	hr = pKPS->Set( KSPROPSETID_BdaTunerExtensionProperties, type, 
		            &instance_data,  sizeof(instance_data), (void*)pData, dwDataSize );

	SAFE_RELEASE( pKPS );
	SAFE_RELEASE( pTunerPin );


	flog(("native.log", "DVB-S: Hauppauge Setup %d (support:%d) for %s hr=0x%x at %s-%d \r\n", 
					type, type_support, pCapInfo->tvType,  hr,
					pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

	return hr;
}

static HRESULT FireDTVDVBSSetup( DShowCaptureInfo* pCapInfo, unsigned long type, void* pData, int dwDataSize )
{
	HRESULT hr;
	IKsPropertySet* pKPS=NULL;

	if ( pCapInfo->pBDATuner == NULL )
	{
		return E_FAIL;
	}

	KSP_NODE instance_data;
	TV_TYPE BDATVType = GetTVType( pCapInfo ); 
	if ( BDATVType != DVBS )
	{
		
		flog(("native.log", "DVB-S: Not DVB-S tuner (%s) %s-%d \n", pCapInfo->tvType,
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return E_FAIL;
	}

	hr = pCapInfo->pBDATuner->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
	if ( FAILED( hr )) 
	{
		hr = pCapInfo->pBDACapture->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
		if ( FAILED( hr ) )
		{
			flog(("native.log", "DVB-S: FireDTV tuner pin KPS not found %s-%d hr:0x%x\n", 
					pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr ) );
			return E_FAIL;
		}
		flog(("native.log", "DVB-S: FireDTV tuner pin KPS is found %s-%d on capture tuner \n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
	}

	hr = pKPS->Set( KSPROPSETID_Firesat, type, 
		            &instance_data,  sizeof(instance_data), (void*)pData, dwDataSize );


	flog(("native.log", "DVB-S: FireDTV Setup %d  for %s hr=0x%x at %s-%d \r\n", 
					type,  pCapInfo->tvType,  hr,
					pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

	SAFE_RELEASE( pKPS );
	return hr;
}

static HRESULT AnyseeDVBSSetup( DShowCaptureInfo* pCapInfo, unsigned long type, void* pData, int dwDataSize )
{
	HRESULT hr;
	IKsPropertySet* pKPS=NULL;

	if ( pCapInfo->pBDATuner == NULL )
	{
		return E_FAIL;
	}

	//KSP_NODE instance_data;
	TV_TYPE BDATVType = GetTVType( pCapInfo ); 
	if ( BDATVType != DVBS )
	{
		
		flog(("native.log", "DVB-S: Not DVB-S tuner (%s) %s-%d \n", pCapInfo->tvType,
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return E_FAIL;
	}

	hr = pCapInfo->pBDACapture->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
	if ( FAILED( hr ) )
	{
		flog(("native.log", "DVB-S: Anysee tuner pin KPS not found %s-%d hr:0x%x\n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr ) );
		return E_FAIL;
	}
	flog(("native.log", "DVB-S: Anysee tuner pin KPS is found %s-%d on capture tuner \n", 
			pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
	hr = pKPS->Set( KSPROPSETID_Anyseesat, type, (void*)pData, dwDataSize, (void*)pData, dwDataSize );

	flog(("native.log", "DVB-S: Anysee Setup property %d (bytes:%d) on IKsPropertySet for DiSEqC, hr=0x%x at %s-%d \r\n", 
					type, dwDataSize,  hr,
					pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

	SAFE_RELEASE( pKPS );
	return hr;
}




#define DISEQC_HIGH_NIBLE	0xF0
#define DISEQC_LOW_BAND		0x00
#define DISEQC_HIGH_BAND	0x01
#define DISEQC_VERTICAL		0x00
#define DISEQC_HORIZONTAL	0x02
#define DISEQC_POSITION_A	0x00
#define DISEQC_POSITION_B	0x04
#define DISEQC_OPTION_A		0x00
#define DISEQC_OPTION_B		0x08

static struct diseqc_cmd* CreateDiSEqCCmd( diseqc_cmd *cmd,  int position_b, int voltage_18, int hiband, int option_b )
{
	
	memset( cmd, 0, sizeof(diseqc_cmd) );
	cmd->cmd.msg_len = 4;
	cmd->cmd.msg[0] = 0xE0;
	cmd->cmd.msg[1] = 0x10;
	cmd->cmd.msg[2] = 0x38;
	cmd->cmd.msg[3] = DISEQC_HIGH_NIBLE;

	cmd->cmd.msg[3] |= voltage_18 ? DISEQC_HORIZONTAL : DISEQC_VERTICAL;
	cmd->cmd.msg[3] |= hiband ? DISEQC_HIGH_BAND : DISEQC_LOW_BAND;
	cmd->cmd.msg[3] |= position_b ? DISEQC_POSITION_B : DISEQC_POSITION_A;
	cmd->cmd.msg[3] |= option_b ? DISEQC_OPTION_B : DISEQC_OPTION_A;

	flog(( "native.log", "DiSEqc: Vol:%c Loc:%s Pos:%c Opt:%c \n",  
 		     voltage_18 ? '18':'13', hiband ? "HiBand":"LowBand", position_b ? 'B':'A', option_b ? 'B':'A' ));
	//flog(( "native.log", "diSEqC: %x %x %x %x\n",  cmd->cmd.msg[0], cmd->cmd.msg[1], cmd->cmd.msg[2], cmd->cmd.msg[3] )); 

	return cmd;
}

static int CreateHauppaugeDiSEQCmd( HAUPPAUGE_DISEQC_CMD *DiSEqCCMD, int pos_b, int voltage_18, int hiband, int opt_b )
{
	DISEQCMD LNBCmd;
	unsigned int i;
	CreateDiSEqCCmd( &LNBCmd, pos_b, voltage_18, hiband, opt_b );
	for ( i = 0; i<LNBCmd.cmd.msg_len; i++ )
		DiSEqCCMD->uc_diseqc_send_message[i] = LNBCmd.cmd.msg[i];
	DiSEqCCMD->ul_diseqc_send_message_length = LNBCmd.cmd.msg_len;
	DiSEqCCMD->ul_diseqc_receive_message_length = 0;
	DiSEqCCMD->ul_amplitude_attenuation = 3;
	DiSEqCCMD->b_tone_burst_modulated = TRUE;
	DiSEqCCMD->diseqc_version = DISEQC_VER_1X;
	DiSEqCCMD->receive_mode = RXMODE_NOREPLY;
	DiSEqCCMD->b_last_message = TRUE;
	return 1;
}

static int CreateFireDTVDiSEQCmd( FIREDTV_DISEQC_CMD *DiSEqCCMD, int pos_b, int voltage_18, int hiband, int opt_b )
{
	DISEQCMD LNBCmd;
	CreateDiSEqCCmd( &LNBCmd, pos_b, voltage_18, hiband, opt_b );
	DiSEqCCMD->Voltage = 0xff;
	DiSEqCCMD->ConTone = 0xff;
	DiSEqCCMD->Burst   = 0xff;
	DiSEqCCMD->NrDiseqCmds = 1;
	DiSEqCCMD->DiseqcCmd[0].Length = 4;
	DiSEqCCMD->DiseqcCmd[0].Framing = LNBCmd.cmd.msg[0];
	DiSEqCCMD->DiseqcCmd[0].Address = LNBCmd.cmd.msg[1];
	DiSEqCCMD->DiseqcCmd[0].Command = LNBCmd.cmd.msg[2];
	DiSEqCCMD->DiseqcCmd[0].Data[0] = LNBCmd.cmd.msg[3];
	DiSEqCCMD->DiseqcCmd[0].Data[1] = LNBCmd.cmd.msg[4];
	DiSEqCCMD->DiseqcCmd[0].Data[2] = LNBCmd.cmd.msg[5];
	return 1;
}

static int CreateAnyseeDiSEQCmd( ANYSEE_DISEQC_CMD *DiSEqCCMD, int pos_b, int voltage_18, int hiband, int opt_b )
{
	DISEQCMD LNBCmd;
	CreateDiSEqCCmd( &LNBCmd, pos_b, voltage_18, hiband, opt_b );
	DiSEqCCMD->ToneBurst = 0/*No_DiSEqCToneBurst*/;
	DiSEqCCMD->dwLength = 4;
	DiSEqCCMD->Data[0] = LNBCmd.cmd.msg[0];
	DiSEqCCMD->Data[1] = LNBCmd.cmd.msg[1];
	DiSEqCCMD->Data[2] = LNBCmd.cmd.msg[2];
	DiSEqCCMD->Data[3] = LNBCmd.cmd.msg[3];
	DiSEqCCMD->Data[4] = LNBCmd.cmd.msg[4];
	DiSEqCCMD->Data[5] = LNBCmd.cmd.msg[5];
	DiSEqCCMD->Data[6] = LNBCmd.cmd.msg[0];

	return 1;
}

static HRESULT HauppaugeDiSEQC(  DShowCaptureInfo* pCapInfo, unsigned long freq, int polarisation, unsigned short sat_no )
{
	int i;
	int hiband = 0;
	int lnb_sat_no;
	int pos_b=0, option_b=0;
	int voltage18 = 0;
	DISEQC *pDiSEQC;
	LNB *pLNB;
	pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;

	for ( i = 0; i<MAX_LNB_SWITCH; i++ )
		if ( pDiSEQC->lnb[i].sat_no == sat_no )
			break;

	if ( i < MAX_LNB_SWITCH )
		pLNB = &pDiSEQC->lnb[i];
	else
	{
		pLNB = &pDiSEQC->lnb[0];
		flog(("native.log", "LNB for sat_no:%d is not found, use sat_no:0 as default\n", sat_no ) );
	}

	HAUPPAUGE_DISEQC_CMD DiSEqCCMD;
	if ( pLNB->switch_val && pLNB->high_val && freq >= pLNB->switch_val )
			hiband = 1;
	pos_b    = ( pLNB->pos=='b'    || pLNB->pos=='B' );
	option_b = ( pLNB->option=='b' || pLNB->option=='B' );
	voltage18= polarisation == 2 ? 0 : 1;

	if ( sat_no != 0xffff ) lnb_sat_no = sat_no; else lnb_sat_no = pLNB->sat_no;
	if ( CreateHauppaugeDiSEQCmd( &DiSEqCCMD, pos_b, voltage18, hiband, option_b ) > 0 )
	{
		return HauppaugeDVBSSetup( pCapInfo, KSPROPERTY_BDA_HAUP_DISEQC, &DiSEqCCMD, sizeof(HAUPPAUGE_DISEQC_CMD) );
	}

	return E_FAIL;
}


/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//FireDTV
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
typedef enum _KSPROPERTY_FIRESAT { 	
	KSPROPERTY_FIRESAT_SELECT_MULTIPLEX_DVB_S=0, 
	KSPROPERTY_FIRESAT_SELECT_SERVICE_DVB_S, 
	KSPROPERTY_FIRESAT_SELECT_PIDS_DVB_S, 		// USE ALSO FOR DVB-C 
	KSPROPERTY_FIRESAT_SIGNAL_STRENGTH_TUNER, 
	KSPROPERTY_FIRESAT_DRIVER_VERSION,
	KSPROPERTY_FIRESAT_SELECT_MULTIPLEX_DVB_T, 
	KSPROPERTY_FIRESAT_SELECT_PIDS_DVB_T, 
	KSPROPERTY_FIRESAT_SELECT_MULTIPLEX_DVB_C, 
	KSPROPERTY_FIRESAT_SELECT_PIDS_DVB_C,		// DON’T USE 
	KSPROPERTY_FIRESAT_GET_FRONTEND_STATUS,
	KSPROPERTY_FIRESAT_GET_SYSTEM_INFO, 
	KSPROPERTY_FIRESAT_GET_FIRMWARE_VERSION, 
	KSPROPERTY_FIRESAT_LNB_CONTROL, 
	KSPROPERTY_FIRESAT_GET_LNB_PARAM, 
	KSPROPERTY_FIRESAT_SET_LNB_PARAM,
	KSPROPERTY_FIRESAT_SET_POWER_STATUS, 
	KSPROPERTY_FIRESAT_SET_AUTO_TUNE_STATUS, 
	KSPROPERTY_FIRESAT_FIRMWARE_UPDATE, 
	KSPROPERTY_FIRESAT_FIRMWARE_UPDATE_STATUS, 
	KSPROPERTY_FIRESAT_CI_RESET,
	KSPROPERTY_FIRESAT_CI_WRITE_TPDU, 
	KSPROPERTY_FIRESAT_CI_READ_TPDU, 
	KSPROPERTY_FIRESAT_HOST2CA, 
	KSPROPERTY_FIRESAT_CA2HOST, 
	KSPROPERTY_FIRESAT_GET_BOARD_TEMP,
	KSPROPERTY_FIRESAT_TUNE_QPSK, 
	KSPROPERTY_FIRESAT_REMOTE_CONTROL_REGISTER, 
	KSPROPERTY_FIRESAT_REMOTE_CONTROL_CANCEL, 
	KSPROPERTY_FIRESAT_GET_CI_STATUS, 
	KSPROPERTY_FIRESAT_TEST_INTERFACE
} KSPROPERTY_FIRESAT;

int IsFireDTV( DShowCaptureInfo* pCapInfo )
{
	HRESULT hr; 
	FIRESAT_DRIVER_VERSION instance; 
	FIRESAT_DRIVER_VERSION Version; 
	DWORD dwBytesReturned; 
	IKsPropertySet* pKPS=NULL;
	DWORD type_support = 0;

	if ( pCapInfo->pBDATuner == NULL )
	{
		flog(("native.log", "DVB-S: Not a DVB-S tuner (%s) %s-%d \n", pCapInfo->tvType,
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return false;
	}

	TV_TYPE BDATVType = GetTVType( pCapInfo ); 
	if ( BDATVType != DVBS )
	{
		
		flog(("native.log", "DVB-S: invalid tuner type (%s) %s-%d \n", pCapInfo->tvType,
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
		return false;
	}

	hr = pCapInfo->pBDATuner->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
	if ( FAILED( hr )) 
	{
		hr = pCapInfo->pBDACapture->QueryInterface( IID_IKsPropertySet, (void**)&pKPS );
		if ( FAILED( hr ) )
		{
			flog(("native.log", "DVB-S: FireDTV tuner pin KPS not found %s-%d hr:0x%x\n", 
					pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr ) );
			return false;
		}
		flog(("native.log", "DVB-S: FireDTV tuner pin KPS is found %s-%d on capture tuner \n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );

	}

	hr = pKPS->Get( KSPROPSETID_Firesat, KSPROPERTY_FIRESAT_DRIVER_VERSION, 
			             	  &instance, sizeof(FIRESAT_DRIVER_VERSION), 
							  &Version, sizeof(FIRESAT_DRIVER_VERSION), &dwBytesReturned );
	if ( FAILED( hr )) 
	{
		flog(("native.log", "DVB-S: Failed getting FireDTV driver version %s-%d hr:0x%x \n", 
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum, hr ) );
		return false;
	}

	flog(("native.log", "DVB-S: FireDTV tuner Version %s %s-%d.\n", Version.strDriverVersion,
				pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum ) );
	
	SAFE_RELEASE( pKPS );
	return true;

}
static HRESULT FireDTVDiSEQC(  DShowCaptureInfo* pCapInfo, unsigned long freq, int polarisation, unsigned short sat_no )
{
	int i;
	int hiband = 0;
	int lnb_sat_no=0;
	int pos_b=0, option_b=0;
	int voltage18 = 0;
	DISEQC *pDiSEQC;
	LNB *pLNB;
	pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;

	for ( i = 0; i<MAX_LNB_SWITCH; i++ )
		if ( pDiSEQC->lnb[i].sat_no == sat_no )
			break;

	if ( i < MAX_LNB_SWITCH )
		pLNB = &pDiSEQC->lnb[i];
	else
	{
		pLNB = &pDiSEQC->lnb[0];
		flog(("native.log", "LNB for sat_no:%d is not found, use sat_no:0 as default\n", sat_no ) );
	}
	

	FIREDTV_DISEQC_CMD DiSEqCCMD;
	if ( pLNB->switch_val && pLNB->high_val && freq >= pLNB->switch_val )
			hiband = 1;

	pos_b    = ( pLNB->pos=='b'    || pLNB->pos=='B' );
	option_b = ( pLNB->option=='b' || pLNB->option=='B' );
	voltage18= polarisation == 2 ? 0 : 1;

	if ( sat_no != 0xffff ) lnb_sat_no = sat_no; else lnb_sat_no = pLNB->sat_no;
	if ( CreateFireDTVDiSEQCmd( &DiSEqCCMD, pos_b, voltage18, hiband, option_b ) > 0 )
	{
		return FireDTVDVBSSetup( pCapInfo, KSPROPERTY_FIRESAT_LNB_CONTROL, &DiSEqCCMD, sizeof(FIREDTV_DISEQC_CMD) );
	}

	return E_FAIL;
}

static HRESULT FireDTVLNBPower( DShowCaptureInfo* pCapInfo, bool on )
{
	FIRESAT_POWER_STATUS power_status;
	if ( on )
		power_status.uPowerStatus = FIREDTV_POWER_ON;
	else
		power_status.uPowerStatus = FIREDTV_POWER_OFF;
	return FireDTVDVBSSetup( pCapInfo, KSPROPERTY_FIRESAT_SET_POWER_STATUS, &power_status, sizeof(FIRESAT_POWER_STATUS) );
}
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//Aynsee
static int IsAnyseeTuner( DShowCaptureInfo* pCapInfo )
{
	TV_TYPE BDATVType = GetTVType( pCapInfo ); 
	if ( BDATVType != DVBS )
		return false;

	if ( strstr ( pCapInfo->videoCaptureFilterName, "anysee " ) )
		return true;

	return false;
}

static HRESULT AnyseeDiSEQC(  DShowCaptureInfo* pCapInfo, unsigned long freq, int polarisation, unsigned short sat_no )
{
	int i;
	int hiband = 0;
	int lnb_sat_no=0;
	int pos_b=0, option_b=0;
	int voltage18 = 0;
	DISEQC *pDiSEQC;
	LNB *pLNB;
	pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;

	for ( i = 0; i<MAX_LNB_SWITCH; i++ )
		if ( pDiSEQC->lnb[i].sat_no == sat_no )
			break;

	if ( i < MAX_LNB_SWITCH )
		pLNB = &pDiSEQC->lnb[i];
	else
	{
		pLNB = &pDiSEQC->lnb[0];
		flog(("native.log", "LNB for sat_no:%d is not found, use sat_no:0 as default\n", sat_no ) );
	}
	

	ANYSEE_DISEQC_CMD DiSEqCCMD={0};
	if ( pLNB->switch_val && pLNB->high_val && freq >= pLNB->switch_val )
			hiband = 1;

	pos_b    = ( pLNB->pos=='b'    || pLNB->pos=='B' );
	option_b = ( pLNB->option=='b' || pLNB->option=='B' );
	voltage18= polarisation == 1 ? 0 : 1;

	if ( sat_no != 0xffff ) lnb_sat_no = sat_no; else lnb_sat_no = pLNB->sat_no;
	if ( CreateAnyseeDiSEQCmd( &DiSEqCCMD, pos_b, voltage18, hiband, option_b ) > 0 )
	{
		//return AnyseeDVBSSetup( pCapInfo, USERPROPERTY_SEND_DiSEqC_DATA, &DiSEqCCMD, 48 );
		return AnyseeDVBSSetup( pCapInfo, USERPROPERTY_SEND_DiSEqC_DATA, &DiSEqCCMD, sizeof(ANYSEE_DISEQC_CMD) );
	}

	return E_FAIL;
}

static HRESULT AnyseeDiSEQC2(  DShowCaptureInfo* pCapInfo, unsigned long freq, int polarisation, unsigned short sat_no )
{
	int i, ret;
	int hiband = 0;
	int lnb_sat_no=0;
	int pos_b=0, option_b=0;
	int horizion = 0;
	DISEQC *pDiSEQC;
	LNB *pLNB;
	pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;

	for ( i = 0; i<MAX_LNB_SWITCH; i++ )
		if ( pDiSEQC->lnb[i].sat_no == sat_no )
			break;

	if ( i < MAX_LNB_SWITCH )
		pLNB = &pDiSEQC->lnb[i];
	else
	{
		pLNB = &pDiSEQC->lnb[0];
		flog(("native.log", "LNB for sat_no:%d is not found, use sat_no:0 as default\n", sat_no ) );
	}

	if ( pLNB->switch_val && pLNB->high_val && freq >= pLNB->switch_val )
		hiband = 1;
	pos_b    = ( pLNB->pos=='b'    || pLNB->pos=='B' );
	option_b = ( pLNB->option=='b' || pLNB->option=='B' );
	horizion = polarisation == 2 ? 0 : 1;
	
	ret = AnyseeDiSEqCNIMSetup( pDiSEQC->device, horizion, hiband, pLNB->low_val/1000, pLNB->high_val/1000, pos_b, option_b );
	flog(( "native.log", "AnyseeNIM: horizion:%d LowLOF:%d HighLOF:%d Pos:%d Opt:%d \n",  horizion, pLNB->low_val/1000, pLNB->high_val/1000,
			pos_b, option_b, hiband ));

	return S_OK;
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
static LNB* GetLNBEntry( DShowCaptureInfo* pCapInfo, int SatNo );
static HRESULT SetupBDALNB( DShowCaptureInfo* pCapInfo, LNB* pLNB, int SatNo );
LNB *SetupLNB( DShowCaptureInfo* pCapInfo, char* vendor_name, char* LNB_name, int SatNo )
{
	int n, i;
	DISEQC *pDiSEQC;
	LNB *pLNB;
	bool found_predefined_LBN = false;
	
	if ( pCapInfo->pDiSEQC == NULL )
	{
		pCapInfo->pDiSEQC = new char [sizeof(DISEQC)];
		memset( pCapInfo->pDiSEQC, 0, sizeof(pCapInfo->pDiSEQC) );
		pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;
		strncpy( pDiSEQC->vendor, vendor_name, sizeof(pDiSEQC->vendor) );
		for ( i = 0; i<MAX_LNB_SWITCH; i++ )
			pDiSEQC->lnb[i].sat_no = -1;

		//propertary device
		//if ( !strcmp( pDiSEQC->vendor,  ANYSEE_DISEQ ) )
		//{
		//	pDiSEQC->device = AnyseeOpenLNBDevice( pCapInfo->videoCaptureFilterName );
		//}
	}
	
	pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;
	for ( i = 0; i<MAX_LNB_SWITCH; i++ )
		if ( pDiSEQC->lnb[i].sat_no == SatNo )
			break;

	if ( i <MAX_LNB_SWITCH )
		pLNB = &pDiSEQC->lnb[i];
	else
	{
		for ( i = 0; i<MAX_LNB_SWITCH; i++ )
			if ( pDiSEQC->lnb[i].sat_no == -1 )
				break;
	
		if ( i <MAX_LNB_SWITCH )
			pLNB = &pDiSEQC->lnb[i];
		else
		{
			flog(( "native.log", "LNB table is full, drop sat_no:%d %s\n", SatNo, LNB_name ) );
			return NULL;
		}
	}

	n = sizeof( predefined_lnbs )/sizeof(LNB);
	for ( i = 0; i<n; i++ )
	{
		if ( !strncmp( predefined_lnbs[i].name, LNB_name, strlen(predefined_lnbs[i].name) ) )
		{
			*pLNB = predefined_lnbs[i];
		    flog(( "native.log", "LNB switch '%s' found.\n", LNB_name ));
			found_predefined_LBN = true;
		} 
	}

	pLNB->sat_no = SatNo;
	
	//check if it's low-high-swith format
	if ( !found_predefined_LBN && strstr( LNB_name, "-" ) )
	{
		const char* delima = "-: ";
		char *token, *savedptr;

		token = (char*)strtok_r( LNB_name, delima, &savedptr );
		if ( token != NULL )
			pLNB->low_val = atoi( token )*1000;
		token = (char*)strtok_r( savedptr, delima, &savedptr );
		if ( token != NULL )
			pLNB->high_val = atoi( token )*1000;
		token = (char*)strtok_r( savedptr, delima, &savedptr );
		if ( token != NULL )
			pLNB->switch_val = atoi( token )*1000;

		found_predefined_LBN = true;

	}

	return pLNB;
}

void ReleaseDiSEQC( DShowCaptureInfo* pCapInfo )
{
	if ( pCapInfo->pDiSEQC != NULL )
	{
		DISEQC *pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;
		//if ( !strcmp( pDiSEQC->vendor,  ANYSEE_DISEQ ) )
		//{
		//	if ( pDiSEQC->device != NULL )
		//	{
		//		AnyseeCloseLNBDevice( pDiSEQC->device );
		//	}
		//}
		delete pCapInfo->pDiSEQC;
	}
	pCapInfo->pDiSEQC = NULL;
}

//LNB file fomrmat: # comments...[\n] sat_no:nnn [\n] lnb=low-high-switch

int SetupSatelliteLNB( DShowCaptureInfo* pCapInfo, int reload  )
{
	FILE *fp;
	char buf[512], lnb_name[80]={0};
	char file_name[128];
	char *vendor_name="";
	int  i, sat_no=0;
	char sat_name[64], sat_pos[8], sat_option[8];
	int lnb_updated = 0;

	if ( pCapInfo == NULL )//|| pCapInfo->pDiSEQC != NULL )
		return 0;

	DISEQC *pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;
	if ( reload == 0 && pDiSEQC != NULL && pDiSEQC->state == 1 )
		return 0;

	if ( IsHauppaugeDiSEQC(  pCapInfo ) )
		vendor_name = HAUPPAUGE_DISEQ;
	else
	if ( IsFireDTV( pCapInfo ) )
	{
		vendor_name = FIREDTV_DISEQ;
	} else
	if ( IsAnyseeTuner( pCapInfo ) )
	{
		vendor_name = ANYSEE_DISEQ;
	} else
	{
		vendor_name = UNKOWN_DISEQ;
	}

	strncpy( buf, pCapInfo->videoCaptureFilterName, sizeof(buf) );
	ConvertIllegalChar2( buf );
	SPRINTF( file_name, sizeof(file_name), "%s-%d.lnb", buf, pCapInfo->videoCaptureFilterNum );
	
	fp = fopen( file_name, "r" );
	if ( fp == NULL )
	{
		flog(( "native.log", "DVB-S LNB configuration '%s'file isn't exist\n", file_name ));
		return 0;
	}

	flog(( "native.log", "LNB configuration '%s' file found\n", file_name ));

	
	if ( pDiSEQC != NULL )
		for ( i = 0; i<MAX_LNB_SWITCH; i++ )
			pDiSEQC->lnb[i].sat_no = -1;

	sat_no = 0;
	sat_name[0] = 0x0;
	sat_pos[0] = 0x0;
	sat_option[0] = 0x0;

	while( !feof( fp ) )
	{
		char *p;
		int i;
		memset( buf, 0, sizeof(buf) );
		fgets( buf, sizeof(buf)-1, fp );
		
		//trim right space
		for ( i = min( strlen(buf), sizeof(buf) ); i>0; i-- )
		  if ( buf[i-1] == ' ' || buf[i-1] == '\n' || buf[i-1]=='\r' ) buf[i-1] = 0;
		  else
		  	break;
		
		//skip white space
		for ( i = 0; i<sizeof(buf)-1; i++ )
			if ( buf[i] != ' ' || buf[i] !='\t' ) break;
		p = buf+i;
		
		//skip comment line
		if ( *p == '#' || *p == '!' ) 
			continue; 
			
		if ( GetIntVal( p, "sat_no", (unsigned long*)&sat_no ) || GetIntVal( p, "SAT_NO", (unsigned long*)&sat_no ) )	
		{
        		flog(( "native.log", "sat_no:%d\n", sat_no ));	
		} else
		if ( GetQuoteString( p, "sat_name", sat_name, sizeof(sat_name) ) || GetQuoteString( p, "SAT_NAME", sat_name, sizeof(sat_name) ) )	
		{
        		flog(( "native.log", "sat_name:%s\n", sat_name ));	
		} else
		if ( GetString( p, "LNB", lnb_name, sizeof(lnb_name)) || GetString( p, "lnb", lnb_name, sizeof(lnb_name)) )
		{
			sat_pos[0] = 0x0;
			sat_option[0] = 0x0;
			if ( GetString( p, "pos", sat_pos, sizeof(sat_pos)) || GetString( p, "POS", sat_pos, sizeof(sat_pos)) )
				if ( !strncmp( sat_pos, "NONE", 4 ) || !strncmp( sat_pos, "null", 4 ) ) sat_pos[0] = 0x0;
			if ( GetString( p, "opt", sat_option, sizeof(sat_option)) || GetString( p, "opt", sat_option, sizeof(sat_option)) )
				if ( !strncmp( sat_option, "NONE", 4 ) || !strncmp( sat_pos, "null", 4 ) ) sat_option[0] = 0x0;

			LNB* pLNB;
			if ( lnb_name[0] )
				flog(( "native.log", "lnb:%s pos:'%c' opt:'%c'\n", lnb_name, sat_pos[0], sat_option[0] ));	
    		 else
	    		flog(( "native.log", "LNB undefined %s\n" ));    

			pLNB = SetupLNB( pCapInfo, vendor_name, lnb_name, sat_no );
			if ( pLNB != NULL )
			{
				strncpy( pLNB->name, sat_name, sizeof(pLNB->name) );
				pLNB->pos = sat_pos[0];
				pLNB->option = sat_option[0];
				lnb_updated = true;
				flog(( "native.log", "SEQ LNB: '%s' (satno:%d) low:%d high:%d switch:%d, pos:'%c' opt:'%c'.\n", pLNB->name, pLNB->sat_no,
					  pLNB->low_val, pLNB->high_val, pLNB->switch_val, pLNB->pos, pLNB->option, pLNB->name ));		
			}
			sat_no = 0;
			sat_name[0] = 0x0;
		} else
			lnb_name[0] = 0;

		
	}

	fclose( fp );

	if ( lnb_updated )
	{
		DISEQC *pDiSEQC;
		pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;
		SetupBDALNB( pCapInfo, &pDiSEQC->lnb[0], 0 );
		if ( pDiSEQC ) pDiSEQC->state = 1;
	}
	return 1;

}

static LNB* GetLNBEntry( DShowCaptureInfo* pCapInfo, int SatNo )
{
	int i;
	DISEQC *pDiSEQC;

	pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;
	if ( pDiSEQC == NULL ) return NULL;

	for ( i = 0; i<MAX_LNB_SWITCH; i++ )
		if ( pDiSEQC->lnb[i].sat_no == SatNo )
			return &pDiSEQC->lnb[i];

	flog(( "native.log", "LNB entry is not found for sat:%d\n", SatNo ));

	return NULL;
}


HRESULT  SetupTunerLNBInfo( DShowCaptureInfo* pCapInfo, long low, long hi, long sw );
static HRESULT SetupBDALNB( DShowCaptureInfo* pCapInfo, LNB* pLNB, int SatNo )
{
	HRESULT hr;
	//setup LNB in tuningspace
	if ( pLNB == NULL ) return 0;
	IDVBSTuningSpace*  piDVBSTuningSpace = (IDVBSTuningSpace*)pCapInfo->pTuningSpace;
	if (  piDVBSTuningSpace != NULL )
	{
		DISEQC *pDiSEQC;
		pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;
		LNB* pLNB = &pDiSEQC->lnb[0];
		if ( pLNB->low_val > 0 )
		{
			hr = piDVBSTuningSpace->put_LowOscillator( pLNB->low_val );
			if ( hr != S_OK )	flog(( "native.log", "LBN:setup low oscillator failed %x\n", hr ));
		}
		if ( pLNB->high_val > 0 )
		{
			hr = piDVBSTuningSpace->put_HighOscillator( pLNB->high_val );
			if ( hr != S_OK )	flog(( "native.log", "LBN:setup high oscillator failed %x\n", hr ));
		}
		if ( pLNB->switch_val > 0 )
		{
			hr = piDVBSTuningSpace->put_LNBSwitch( pLNB->switch_val );
			if ( hr != S_OK )	flog(( "native.log", "LNB switch failed %x\n", hr ));
		}
	}

	hr = SetupTunerLNBInfo( pCapInfo, pLNB->low_val, pLNB->high_val, pLNB->switch_val );
	if ( hr != S_OK ) flog(( "native.log", "Set DVB-S tuner LNB switch (SatNo:%d) info failed %x\n", SatNo, hr ));
	else
		flog(( "native.log", "Set DVB-S tuner LNB info (sat_no:%d) :%d %d %d\n", SatNo, 
		                            pLNB->low_val, pLNB->high_val, pLNB->switch_val ));

	return 0;	
}

HRESULT SendDiSEQCCmd( DShowCaptureInfo* pCapInfo, unsigned long freq, int polarisation, unsigned short sat_no )
{
	HRESULT hr;
	DISEQC *pDiSEQC;
	pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;
	if ( pDiSEQC == NULL  )
	{
		flog(( "native.log", "DiSEQC isn't setup, skip DiSEQ cmd \n" ));
		return 0;
	}
		
	if ( !strcmp( pDiSEQC->vendor,  HAUPPAUGE_DISEQ ) )
	{
		hr = HauppaugeDiSEQC( pCapInfo, freq, polarisation, sat_no );
	}
	else
	if ( !strcmp( pDiSEQC->vendor,  FIREDTV_DISEQ ) )
	{
		//power on
		hr = FireDTVLNBPower( pCapInfo, true ); //power on
		//Sleep( 100 );
		hr = SetupBDALNB( pCapInfo, GetLNBEntry( pCapInfo, sat_no ), sat_no );
		hr = FireDTVDiSEQC( pCapInfo, freq, polarisation, sat_no );

	}else
	if ( !strcmp( pDiSEQC->vendor,  ANYSEE_DISEQ ) )
	{
		hr = SetupBDALNB( pCapInfo, GetLNBEntry( pCapInfo, sat_no ), sat_no );
		hr = AnyseeDiSEQC( pCapInfo, freq, polarisation, sat_no );
	}
	else
	if ( pDiSEQC->vendor[0] )
	{
		flog(( "native.log", "Unknown DiSEQC device '%s', try to use Happauge DiSeqC \n", pDiSEQC->vendor ));
		hr = HauppaugeDiSEQC( pCapInfo, freq, polarisation, sat_no );
	}

	return hr;
}

//ZQ test code
//HRESULT SetupTunerNIM( DShowCaptureInfo* pCapInfo, unsigned long dwFreqKHZ, int dwModulation, unsigned long SymRate )
//{
//	DISEQC *pDiSEQC;
//	pDiSEQC = (DISEQC *)pCapInfo->pDiSEQC;
//	if ( !strcmp( pDiSEQC->vendor,  ANYSEE_DISEQ ) )
//	{
//		int modulation, ret;
//		//ZQ I will convert modulation into Aynsee defined mod, late
//		modulation = 1;
//		ret = AnyseeTunerSetup( pDiSEQC->device, dwFreqKHZ, modulation, SymRate*1000 );
//	}
//	return S_OK;
//}

/*
//Set Pilot Mode
Pilot p = HCW_PILOT_OFF;

// Get the generic property set interface from the pin
HRESULT hr = pTunerPin->QueryInterface( IID_IKsPropertySet, reinterpret_cast<void**>(&PropSet) );

// Check to see that the Hauppauge PropertySet interface is supported
hr = PropSet->QuerySupported( KSPROPSETID_BdaTunerExtensionProperties, KSPROPERTY_BDA_HAUP_PILOT,  &type_support);

// Send the pilot setting to the driver
hr = PropSet->Set( KSPROPSETID_BdaTunerExtensionProperties,  KSPROPERTY_BDA_HAUP_PILOT, &instance_data,
					sizeof(instance_data), (ULONG)&p, sizeof(ULONG) );

//Roll Off Mode
Roll_Off r = HCW_ROLL_OFF_35;
// Get the generic property set interface from the pin
HRESULT hr = pTunerPin->QueryInterface( IID_IKsPropertySet, reinterpret_cast<void**>(&PropSet) );

// Check to see that the Hauppauge PropertySet interface is supported
hr = PropSet->QuerySupported( KSPROPSETID_BdaTunerExtensionProperties, KSPROPERTY_BDA_HAUP_ROLL_OFF,  &type_support);
// Send the rolloff setting to the driver
hr = PropSet->Set( KSPROPSETID_BdaTunerExtensionProperties,  KSPROPERTY_BDA_HAUP_ROLL_OFF, &instance_data,
					sizeof(instance_data), (ULONG)&r, sizeof(ULONG) );

*/


