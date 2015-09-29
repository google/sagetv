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

#include <stdio.h>	
#include <stdlib.h>
#include <string.h>
#include "NativeCore.h"
#include "TSFilter.h"
#include "PSIParser.h"
#include "TSParser.h"
#include "ESAnalyzer.h"
#include "ChannelScan.h"
#include "TSEPGParser.h" 



static int PSIPidDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_EPG_PARSER *pTSEPGParser = (TS_EPG_PARSER*)pContext;
	return pTSEPGParser->dumper.pid_dumper( pTSEPGParser->dumper.pid_dumper_context, pData, nSize );
}

TS_EPG_PARSER* CreateTSEPGParser( int nStreamFormat, int nSubFormat )
{
	TS_EPG_PARSER *pTSEPGParser = SAGETV_MALLOC( sizeof(TS_EPG_PARSER) );
	pTSEPGParser->ts_filter = CreateTSFilter( DEFAULT_PAT_NUM, DEFAULT_PMT_NUM, nStreamFormat, nSubFormat ); //ATSC_STREAM, TERRESTRIAL;

	pTSEPGParser->ts_filter->psi_parser->dumper.pid_dumper  =	(DUMP)PSIPidDumper;
	pTSEPGParser->ts_filter->psi_parser->dumper.pid_context =	pTSEPGParser;

	SageLog(( _LOG_TRACE, 3, TEXT("TS EPG Parser is created  version 1.0.0  .") ));
	return pTSEPGParser;
}

void ReleaseTSEPGParser( TS_EPG_PARSER *pTSEPGParser )
{
	ReleaseTSFilter( pTSEPGParser->ts_filter );
	SAGETV_FREE( pTSEPGParser );

	SageLog(( _LOG_TRACE, 3, TEXT("TS Channel Parser is released  .") ));
}

void ResetTSEPGParser( TS_EPG_PARSER *pTSEPGParser )
{
	ResetTSFilter( pTSEPGParser->ts_filter );
	pTSEPGParser->ts_filter->disable_ts_table_parse = 0;
	pTSEPGParser->ts_filter->disable_stream_filter = 0;
	SageLog(( _LOG_TRACE, 3, TEXT("TS EPG Parser is reset  .") ));
	pTSEPGParser->state = 0;
}


void SetupTSEPGParserDump( TS_EPG_PARSER *pTSEPGParser, 
					 DUMP pfnPidDump,   void* pfnPidDumpContext,
					 DUMP pfnEPGDump,	void* pfnEPGDumpContext )
{
	pTSEPGParser->dumper.pid_dumper = pfnPidDump;
	pTSEPGParser->dumper.pid_dumper_context = pfnPidDumpContext;
	pTSEPGParser->ts_filter->psi_parser->dumper.epg_dumper  =	(DUMP)pfnEPGDump;
	pTSEPGParser->ts_filter->psi_parser->dumper.epg_dumper_context =	pfnEPGDumpContext;
}

int StartTSEPGParser( TS_EPG_PARSER *pTSEPGParser )
{
	PID_ITEM pid_inf;
	pTSEPGParser->state = 0;
	
	pid_inf.pid =  0x1ffb;
	pid_inf.type = 0;//PID_TYPE_PSI;
	pid_inf.service = 0;
	pTSEPGParser->dumper.pid_dumper( pTSEPGParser->dumper.pid_dumper_context, &pid_inf, sizeof(pid_inf) );

	return 1;
}

void StopTSEPGParser( TS_EPG_PARSER *pTSEPGParser )
{
	pTSEPGParser->state = 0;
}


int TSProcessInfo( TS_FILTER* pTSFilter, uint8_t* pData );
int PushTSEPGPacketParser( TS_EPG_PARSER *pTSEPGParser, uint8_t* pData, int nSize )
{
	int used_bytes = 0, size = nSize;
	uint8_t* data = pData;

	while ( size >= TS_PACKET_LENGTH )
	{
		int ret;

		if ( *data != TS_SYNC ) //sync header
		{
			for (  ; size>TS_PACKET_LENGTH && *(data) != TS_SYNC ; size--, data++ )
				used_bytes++;

			if ( size < TS_PACKET_LENGTH )
			{
				SageLog(( _LOG_ERROR, 3, TEXT("ERROR: TS SYNC header is lost!") ));
				return used_bytes;
			}
		}

		ret = TSProcessInfo( pTSEPGParser->ts_filter, data  ); //must to be 188 bytes of data

		data += TS_PACKET_LENGTH;
		size -= TS_PACKET_LENGTH;
		used_bytes += TS_PACKET_LENGTH;
	}

	return used_bytes;
}

