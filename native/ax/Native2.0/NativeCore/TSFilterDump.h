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
#ifndef TSFILTER_DUMP_H
#define TSFILTER_DUMP_H

#include "TSParser.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct TS_FAST_FILTER_DUMPER
{
	DUMP  stream_dumper;
	void* stream_dumper_context;

	DUMP  index_dumper;
	void* index_dumper_context;

} TS_FAST_FILTER_DUMPER;

typedef struct TS_FILTER_DUMP
{
	uint16_t state;
	TS_PARSER* ts_parser;
	int32_t packet_length;
	TS_FAST_FILTER_DUMPER dumper;

} TS_FILTER_DUMP;

TS_FILTER_DUMP* CreateTSFilterDump( TS_PARSER *pTSParser, DUMP pfnStreamDump, 
		         void* pStreamDumpContext, DUMP pfnIndexDump, void* pIndexDumpContext );
void ReleaseTSFilterDump( TS_FILTER_DUMP *pTSFilterDump );
int  StartTSFilterDump( TS_FILTER_DUMP *pTSFilterDump, int nFlag );
void StopTSFilterDump( TS_FILTER_DUMP *pTSFilterDump );

#ifdef __cplusplus
}
#endif


#endif

