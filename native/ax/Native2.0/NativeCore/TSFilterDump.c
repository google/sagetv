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
#include "TSFilterDump.h"

static int TSPacketDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_FILTER_DUMP *pTSFilterDump = (TS_FILTER_DUMP*)pContext;
	PACKET_DATA*   packet_data    = (PACKET_DATA*)pData;
	OUTPUT_DATA    output_data;
	output_data.bytes = pTSFilterDump->packet_length;
	output_data.data_ptr = packet_data->packet->data;
	output_data.group_flag = packet_data->packet->start;
	//uint16_t pid = packet_data->packet->pid;
	pTSFilterDump->dumper.stream_dumper( pTSFilterDump->dumper.stream_dumper_context, &output_data, sizeof(output_data) );
	return 1;
}

static int PCRDumper( void* pContext, uint8_t* pData, int nSize )
{
	TS_FILTER_DUMP *pTSFilterDump = (TS_FILTER_DUMP*)pContext;
	PCR_DATA  *pcr_data = (PCR_DATA *)pData;

	return 1;
}

TS_FILTER_DUMP* CreateTSFilterDump( TS_PARSER *pTSParser, DUMP pfnStreamDump, void* pStreamDumpContext, DUMP pfnIndexDump, void* pIndexDumpContext )
{
	TS_FILTER_DUMP *pTSFilterDump = SAGETV_MALLOC( sizeof(TS_FILTER_DUMP) );
	pTSFilterDump->ts_parser = pTSParser;
	pTSFilterDump->packet_length = pTSParser->packet_length;
	pTSFilterDump->dumper.stream_dumper = pfnStreamDump;
	pTSFilterDump->dumper.stream_dumper_context = pStreamDumpContext;
	pTSFilterDump->dumper.index_dumper = pfnIndexDump;
	pTSFilterDump->dumper.index_dumper_context = pIndexDumpContext;
	SageLog(( _LOG_TRACE, 3, TEXT("TS filter dump is created  version 1.0.0  .") ));
	return pTSFilterDump;
}

void ReleaseTSFilterDump( TS_FILTER_DUMP *pTSFilterDump )
{
	SAGETV_FREE( pTSFilterDump );
	SageLog(( _LOG_TRACE, 3, TEXT("TS Filter dump is released  .") ));
}

int StartTSFilterDump( TS_FILTER_DUMP *pTSFilterDump, int nFlag )
{
	int ret;
	TS_FILTER_DUMPER ts_filter_dump={0};
	ts_filter_dump.pcr_dumper = (DUMP)PCRDumper;
	ts_filter_dump.pcr_dumper_context = pTSFilterDump;
	ts_filter_dump.stream_dumper = (DUMP)TSPacketDumper;
	ts_filter_dump.stream_dumper_context = pTSFilterDump;

	ret = SetupFastFilter( pTSFilterDump->ts_parser, (void*)&ts_filter_dump );
	if ( ret > 0 )
	{
		StartFastFilter( pTSFilterDump->ts_parser, nFlag );
		pTSFilterDump->state = 1;
	}
	return ret;
}

void StopTSFilterDump( TS_FILTER_DUMP *pTSFilterDump )
{
	StopFastFilter( pTSFilterDump->ts_parser );
	pTSFilterDump->state = 0;
}

