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

#if !defined( _TS_NATIVE_BUILDER_INCLUDED_ )
#define _TS_NATIVE_BUILDER_INCLUDED_

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


#ifdef __cplusplus
extern "C" {
#endif

#define TBL_PID_START    0x20
#define ELMNT_PID_START  0x80

#define PACKET_POOL_NUMBER  (8*1024/188+1)

typedef struct {
	short			Type;		//reserver
	unsigned short	PayloadOffset;
	unsigned char   Packet[TS_PACKET_LENGTH];
} TS_PACKET_DATA;

typedef struct {

	TS_PAT		Pat;					//PAT table
	TS_PMT		Pmt[ MAX_PROGRAM ];	    //PMT table array
    int			ProgramNum;

	unsigned short tbl_pid;             //next availabe PID that is assigned to PMT or section data
	unsigned short elmnt_pid;           //next availabe PID that is assigned to A/V data

	TS_PACKET_DATA Packets[PACKET_POOL_NUMBER];    //packets buffer pool
	int PacketsInPoolNum;                          //number of packet that are in packets buffer pool
	int PacketQueueHead;						   //packets buffer pool queue header	
	int PacketQueueTail;						   //packets buffer pool queue tail

	//Statistic	
	unsigned long packets;
	unsigned long av_frames;	
	
	LPFNBuilderDump builder_dumper;
	void*    env;  //caller enviroment context, used by dumper call back.

} TS_BUILDER;



TS_BUILDER* TSBuilderOpen( );
void TSBuilderClose( TS_BUILDER* buider );
int  AddProgram( TS_BUILDER* buider, int PrgrmID, unsigned short PCRPID, DESC* PmtDesc, 
				                      int StreamNum, short *StreamType, short *StreamPid, DESC *ESDesc  );
void ClearProgram();
void TSResetBuilder(TS_BUILDER* buider);
//int  GetProgramNum();

int  PushPat(TS_BUILDER* pBuilder);
int  PushPmt( TS_BUILDER* pBuilder,int PrgrmID );
int  PushAVPacketData( TS_BUILDER* pBuilder,int PrgrmID, int StreamID, bool Start, char* pPayload, int Bytes  );
int  PushAVBlockData(  TS_BUILDER* pBuilder,int PrgrmID, int StreamID, char* pData, long Size  );
int  PushBlockData( TS_BUILDER* pBuilder, int PrgrmID, int StreamID, char* pData, long Size  );
bool PopPacket( TS_BUILDER* pBuilder,char* pPacket, short* Type, short* PayloadOffset );
int  GetPacketNumInPool( TS_BUILDER* pBuilder );

#ifdef __cplusplus
}
#endif

#endif

