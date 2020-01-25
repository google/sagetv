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
#include <memory.h>
#include "TSBuilder.h"


void BuildTSHeader( TS_HEADER* pTSHeader, char* pData, int Bytes, char** pPayload );
static bool PutPacket( TS_BUILDER* pBuilder, char* Packet, short type, short PayloadOffset ); 
static unsigned short GetNextTblPID( TS_BUILDER* pBuilder );
static unsigned short GetNextElmntPID( TS_BUILDER* pBuilder );



/////////////////////////////////////////////////////////////////////////////////////////
//CTSBuilder
/////////////////////////////////////////////////////////////////////////////////////////
TS_BUILDER* TSBuilderOpen( )
{
	int i;
	TS_BUILDER* pBuilder;
	
	pBuilder = (TS_BUILDER*)sagetv_malloc( sizeof(TS_BUILDER) );

	memset( &pBuilder->Pat, 0x0, sizeof(TS_PAT) );
	pBuilder->Pat.TSID = 0;
	pBuilder->Pat.VersionNumber = 9;
	pBuilder->Pat.section.data = (char*)sagetv_malloc( 1024 );
	pBuilder->Pat.section.data_size = 1024;
    pBuilder->Pat.NumPrograms = 0;
	for ( i = 0; i<MAX_PROGRAM; i++ )
		pBuilder->Pat.ProgramNumber[i] = 0xffff;


	memset( &pBuilder->Pmt, 0x0, sizeof(TS_PMT) );

	for ( i = 0; i<MAX_PROGRAM; i++ )
	{
		pBuilder->Pmt[i].TableId = 1;
		pBuilder->Pmt[i].VersionNumber = 0;
		pBuilder->Pmt[i].PCRPID = 0x1fff;
	}

	pBuilder->tbl_pid =   TBL_PID_START;
	pBuilder->elmnt_pid = ELMNT_PID_START;

	pBuilder->PacketsInPoolNum = 0;
	pBuilder->PacketQueueHead = 0;
	pBuilder->PacketQueueTail = 0;
	pBuilder->builder_dumper = 0;
	pBuilder->env = NULL;

	return pBuilder;

}

void TSBuilderClose( TS_BUILDER* pBuilder )
{
	int	i,j;

	if ( pBuilder == NULL )
		return;

	if ( pBuilder->Pat.section.data != NULL  )
	{
		sagetv_free( pBuilder->Pat.section.data );
		pBuilder->Pat.section.data = NULL;
	}
	for	( j	= 0; j<MAX_PROGRAM;	j++	)
	{
		if ( pBuilder->Pmt[j].ProgramInfo	)
		{
			sagetv_free(  pBuilder->Pmt[j].ProgramInfo );
			pBuilder->Pmt[j].ProgramInfo = NULL;
		}
		for	( i	= 0; i<MAX_ES; i++ )
		{
			if ( pBuilder->Pmt[j].ESInfo[i] ) 
			{
				sagetv_free( pBuilder->Pmt[j].ESInfo[i] );
				pBuilder->Pmt[j].ESInfo[i] = NULL;
			}
		}

		if ( pBuilder->Pmt[j].section != NULL  )	
		{ 
			if ( pBuilder->Pmt[j].section->data ) 
			{
				sagetv_free(  pBuilder->Pmt[j].section->data ); 
				pBuilder->Pmt[j].section->data = NULL;
			}

			sagetv_free(  pBuilder->Pmt[j].section ); 
			pBuilder->Pmt[j].section = NULL;
		}
	}

	sagetv_free( pBuilder );
}

unsigned short GetNextTblPID( TS_BUILDER* pBuilder )
{
	if ( ++pBuilder->tbl_pid >= ELMNT_PID_START )
		pBuilder->tbl_pid = TBL_PID_START;
	return pBuilder->tbl_pid;

}

unsigned short GetNextElmntPID( TS_BUILDER* pBuilder )
{
	if ( ++pBuilder->elmnt_pid  >= 0x1fff )
		pBuilder->elmnt_pid = ELMNT_PID_START;
	return pBuilder->elmnt_pid;
}

int AddProgram( TS_BUILDER* pBuilder, int PrgrmID, unsigned short PCRPID, DESC* PmtDesc, int StreamNum, short *StreamType, short *StreamPid, DESC *ESDesc  )
{
	int i,j;

	if ( StreamNum <= 0 || StreamNum > MAX_ES )
		return -1;

	//check if there is already exist a program
	for ( i = 0; i<MAX_PROGRAM; i++ )
	{
		if ( pBuilder->Pat.ProgramNumber[i] == PrgrmID )
			return -2;
	}

	//find a availabe program table
	for ( i = 0; i<MAX_PROGRAM; i++ )
		if ( (short)pBuilder->Pat.ProgramNumber[i] == -1 )
			break;
	//table is full
	if ( i >= MAX_PROGRAM )
		return -3;

	pBuilder->Pat.CurrentNextIndicator = 1;
	pBuilder->Pat.NumPrograms++;
	pBuilder->Pat.ProgramNumber[i] = PrgrmID;
	pBuilder->Pat.ProgramPID[i] = GetNextTblPID(pBuilder);

	pBuilder->Pmt[i].CurrentNextIndicator = 1;
	pBuilder->Pmt[i].ProgramNumber = PrgrmID;
	pBuilder->Pmt[i].NumStreams = StreamNum;
	pBuilder->Pmt[i].ProgramInfoLength = PmtDesc->DescLength;
	pBuilder->Pmt[i].ProgramInfo = (unsigned char*)sagetv_malloc(PmtDesc->DescLength);
	pBuilder->Pmt[i].PCRPID = PCRPID == 0 ? 0x1fff : PCRPID;
	memcpy( pBuilder->Pmt[i].ProgramInfo, PmtDesc->DescInfo, PmtDesc->DescLength );
	for ( j = 0; j<StreamNum; j++ )
	{
		pBuilder->Pmt[i].StreamType[j] = (char)StreamType[j];
		pBuilder->Pmt[i].ElementaryPID[j] = StreamPid[j];//GetNextElmntPID(pBuilder);
		pBuilder->Pmt[i].ESInfoLength[j] = ESDesc[j].DescLength;
		if ( pBuilder->Pmt[i].ESInfo[j] != NULL )
		{
			sagetv_free( pBuilder->Pmt[i].ESInfo[j] );
			pBuilder->Pmt[i].ESInfo[j] = NULL;
		}
		if ( ESDesc[j].DescLength != 0 && ESDesc[j].DescLength < TS_PACKET_LENGTH )
			pBuilder->Pmt[i].ESInfo[j] = (unsigned char*) sagetv_malloc( ESDesc[j].DescLength );
		memcpy( pBuilder->Pmt[i].ESInfo[j], ESDesc[j].DescInfo, ESDesc[j].DescLength );
	}
	for ( ; j<MAX_ES; j++ )
	{
		pBuilder->Pmt[i].StreamType[j] = 0;
		pBuilder->Pmt[i].ElementaryPID[j] = 0;
		pBuilder->Pmt[i].ESInfoLength[j] = 0;
		if ( pBuilder->Pmt[i].ESInfo[j] != NULL )
		{
			sagetv_free( pBuilder->Pmt[i].ESInfo[j] );
			pBuilder->Pmt[i].ESInfo[j] = NULL;
		}
	}

	if ( pBuilder->Pmt[i].section == NULL )
	{
		pBuilder->Pmt[i].section = (TS_SECTION*)sagetv_malloc( sizeof(TS_SECTION) ); 
		pBuilder->Pmt[i].section->data = (char*)sagetv_malloc( 1024 );
		pBuilder->Pmt[i].section->data_size = 1024;
		pBuilder->Pmt[i].section->bytes = 0;
		pBuilder->Pmt[i].section->total_bytes = 0;
	}

	return 1; 

}

// int  GetProgramNum( TS_BUILDER* pBuilder )
// {
// 	return pBuilder->Pat.NumPrograms;
// }

void TSResetBuilder( TS_BUILDER* pBuilder )
{
	int i;
	if ( pBuilder == NULL )
		return;
	pBuilder->PacketsInPoolNum = 0;
	pBuilder->PacketQueueHead = 0;
	pBuilder->PacketQueueTail = 0;
	pBuilder->Pat.NumPrograms = 0;
	for ( i = 0; i<MAX_PROGRAM; i++ )
		pBuilder->Pat.ProgramNumber[i] = 0xffff;

}

void ClearProgram( TS_BUILDER* pBuilder )
{
	int i,j;
	for ( i = 0; i<MAX_PROGRAM; i++ )
	{
		pBuilder->Pat.ProgramNumber[i] = 0;
		for ( j = 0; j<pBuilder->Pmt[i].ProgramNumber; j++ )
		{
			pBuilder->Pmt[i].StreamType[j] = 0;
			pBuilder->Pmt[i].ElementaryPID[j] = 0;
			pBuilder->Pmt[i].ESInfoLength[j] = 0;
			if ( pBuilder->Pmt[i].ESInfo[j] != NULL )
				sagetv_free( pBuilder->Pmt[i].ESInfo[j] );
			pBuilder->Pmt[i].ESInfo[j] = NULL;
		}
		pBuilder->Pmt[i].ProgramNumber = 0;
		pBuilder->Pmt[i].NumStreams = 0;
	}

}

int PushPat( TS_BUILDER* pBuilder )
{
	int i;
	unsigned char data[1024];
	int	 offset;
	int  section_length;
	memset( data, 0, sizeof(data) );

	section_length = 0;
	offset = 8;
	for ( i = 0; i<pBuilder->Pat.NumPrograms && offset < (int)sizeof(data); i++ )
	{
		data[offset] = ( pBuilder->Pat.ProgramNumber[i]>>8 ) & 0xff;
		data[offset + 1] = ( pBuilder->Pat.ProgramNumber[i] ) & 0xff;
		data[offset + 2] = 0xe0;		//reserved bits 111
		data[offset + 2] |= ( pBuilder->Pat.ProgramPID[i]>>8 ) & 0x1f; 
		data[offset + 3] = ( pBuilder->Pat.ProgramPID[i] ) & 0xff;		
		offset +=	4;
		section_length += 4;
	}

	section_length += 8;
	pBuilder->Pat.SectionLength = section_length+1;
	data[0]	= 0x0;
	data[1] = 0x80;  //section syntax indicator	
	data[1] |= ( pBuilder->Pat.SectionLength >> 8 ) & 0x0f;
	data[2] =  pBuilder->Pat.SectionLength & 0xff;
	data[3] =  ( pBuilder->Pat.TSID >> 8 ) & 0xff;
	data[4] =  pBuilder->Pat.TSID  & 0xff;
	data[5] =  0xc0;
	data[5] |=  (pBuilder->Pat.VersionNumber & 0x1f)<< 1;
	data[5] |= pBuilder->Pat.CurrentNextIndicator & 0x01;
	data[6] =  pBuilder->Pat.SectionNumber;
	data[7] =  pBuilder->Pat.LastSectionNumber;

	BuildSectionData( &pBuilder->Pat.section, section_length, (char*)data );

	i = 0;
	while( 1 )
	{
		unsigned char* pPayload;
		int bytes;
		TS_HEADER TSHeader={0};
		TSHeader.start = i == 0 ? 1:0 ;
		TSHeader.pid = 0;
		TSHeader.continuity_ct = pBuilder->Pat.counter;
		memset( data, 0xff, TS_PACKET_LENGTH );
		BuildTSHeader( &TSHeader, (char*)data, TS_PACKET_LENGTH-4, (char**)&pPayload );

		/* start section offset */
		if ( i == 0 )
		{
			*pPayload = 0x0;
			pPayload += *pPayload + 1; 
		}

		bytes = PopSectionData( &pBuilder->Pat.section, (char*)pPayload, TS_PACKET_LENGTH-(int)(pPayload-data) );
		if ( bytes )
		{
			PutPacket( pBuilder, (char*)data, 0, (short)(data-pPayload) );
		} else
			break;
		i++;
		if ( ++pBuilder->Pat.counter > 0x0f ) pBuilder->Pat.counter = 0x0;
	}

	return 1;
}

int PushPmt( TS_BUILDER* pBuilder, int PrgrmID )
{
	int i, j;
	unsigned char data[1024];
	int	 offset;
	int  section_length;
	unsigned short pid;
	memset( data, 0, sizeof(data) );

	//check if there is a vaild PorgramID
	for ( i = 0; i<MAX_PROGRAM; i++ )
	{
		if ( pBuilder->Pat.ProgramNumber[i] == PrgrmID && pBuilder->Pmt[i].ProgramNumber == PrgrmID )
			break;
	}
	if ( i >= MAX_PROGRAM )
		return -2;
	pid = pBuilder->Pat.ProgramPID[i];


	section_length = 12;
	offset	= 12;
	if ( pBuilder->Pmt[i].ProgramInfoLength )
	{
		memcpy( data+offset, pBuilder->Pmt[i].ProgramInfo, pBuilder->Pmt[i].ProgramInfoLength );
		offset += pBuilder->Pmt[i].ProgramInfoLength;
		section_length += pBuilder->Pmt[i].ProgramInfoLength;
	}
	for ( j = 0; j<pBuilder->Pmt[i].NumStreams; j++ )
	{
		data[offset]   = pBuilder->Pmt[i].StreamType[j];	
		data[offset+1] = 0xe0;  //reserved bits 111
		data[offset+1] |= ( pBuilder->Pmt[i].ElementaryPID[j] >> 8 ) & 0x1f;
		data[offset+2] = pBuilder->Pmt[i].ElementaryPID[j] & 0xff;
		data[offset+3] = 0xf0; //reserved bits 1111
		data[offset+3] |= ( pBuilder->Pmt[i].ESInfoLength[j] >> 8 ) & 0x0f;
		data[offset+4] = pBuilder->Pmt[i].ESInfoLength[j] & 0xff;
		if ( pBuilder->Pmt[i].ESInfoLength[j] )
			memcpy( data+offset+5, pBuilder->Pmt[i].ESInfo[j], pBuilder->Pmt[i].ESInfoLength[j] );
		
		offset +=  5 + pBuilder->Pmt[i].ESInfoLength[j];
		section_length += 5 + pBuilder->Pmt[i].ESInfoLength[j];

	}

	pBuilder->Pmt[i].SectionLength = section_length + 1;
	data[0] = 0x02;  //Table Id 0x02 of PMT
	data[1] = 0x80;  //section syntax indicator	
	data[1] |= 0x30;  //section syntax indicator	
	data[1] |= ( pBuilder->Pmt[i].SectionLength >> 8 ) & 0x0f;
	data[2] =  pBuilder->Pmt[i].SectionLength & 0xff;
	data[3] = ( pBuilder->Pmt[i].ProgramNumber >> 8 ) & 0x0ff;
	data[4] =  pBuilder->Pmt[i].ProgramNumber & 0xff;
	data[5] = 0xc0;
	data[5] |= ( pBuilder->Pmt[i].VersionNumber & 0x1f ) << 1;
	data[5] |= pBuilder->Pmt[i].CurrentNextIndicator ? 0x01 : 0;
	data[6] =  pBuilder->Pmt[i].SectionNum;    
	data[7] =  pBuilder->Pmt[i].LastSectionNum;
	data[8] =  0xe0;


	data[8]	|= ( pBuilder->Pmt[i].PCRPID >> 8 ) & 0x1f;
	data[9]	= pBuilder->Pmt[i].PCRPID & 0xff;
	data[10] = 0xf0;
	data[10] |= ( pBuilder->Pmt[i].ProgramInfoLength >> 8 ) & 0x0f;
	data[11] = pBuilder->Pmt[i].ProgramInfoLength  & 0xff;

	BuildSectionData( pBuilder->Pmt[i].section, section_length, (char*)data );

	j = 0;
	while( 1 )
	{
		unsigned char* pPayload;
		short bytes;
		TS_HEADER TSHeader={0};
		TSHeader.start = j == 0 ? 1:0 ;
		TSHeader.pid = pid;
		TSHeader.continuity_ct = pBuilder->Pmt[i].counter;
		memset( data, 0xff, TS_PACKET_LENGTH );
		
		BuildTSHeader( &TSHeader, (char*)data, TS_PACKET_LENGTH-4, (char**)&pPayload );
		/* start section offset */
		if ( j == 0 )
		{
			*pPayload = 0x0;
			pPayload += *pPayload + 1; 
		}
	
		bytes = PopSectionData( pBuilder->Pmt[i].section, (char*)pPayload, TS_PACKET_LENGTH-(int)(pPayload-data) );
		if ( bytes )
		{
			PutPacket( pBuilder, (char*)data, 1, (short)(data-pPayload) );
		} else
			break;

		if ( ++pBuilder->Pmt[i].counter > 0x0f ) pBuilder->Pmt[i].counter = 0x0;
		j++;
	}

	return 1;

}

int PushAVPacketData( TS_BUILDER* pBuilder, int PrgrmID, int StreamID, bool Start, char* pPayload, int Bytes  )
{
	int i;
	char data[1024];
	TS_HEADER TSHeader={0};
	char* payload_loc;

	if ( pBuilder == NULL || StreamID < 0 || StreamID >= MAX_ES )
		return -1;

	memset( data, 0, sizeof(data) );

	//check if there is a vaild PorgramID
	for ( i = 0; i<MAX_PROGRAM; i++ )
	{
		if ( pBuilder->Pat.ProgramNumber[i] == PrgrmID && pBuilder->Pmt[i].ProgramNumber == PrgrmID )
			break;
	}
	if ( i >= MAX_PROGRAM )
		return -2;

	TSHeader.start = Start ? 1:0 ;
	TSHeader.pid = pBuilder->Pmt[i].ElementaryPID[StreamID] ;
	TSHeader.continuity_ct = pBuilder->Pmt[i].EScounter[StreamID] ;
	if ( ++pBuilder->Pmt[i].EScounter[StreamID] > 0x0f ) 
		pBuilder->Pmt[i].EScounter[StreamID] = 0;

	memset( data, 0xff, TS_PACKET_LENGTH );
	BuildTSHeader( &TSHeader, data, Bytes, &payload_loc );

	memcpy( payload_loc, pPayload, Bytes );

	PutPacket( pBuilder, data, 2, (short)4 );
	pBuilder->av_frames++;
	return 1;
}

int PushBlockData( TS_BUILDER* pBuilder, int PrgrmID, int StreamID, char* pData, long Size  )
{
	int i;
	char data[1024];
	unsigned short pid;
	long total_bytes;
	memset( data, 0, sizeof(data) );

	//check if there is a vaild PorgramID
	for ( i = 0; i<MAX_PROGRAM; i++ )
	{
		if ( pBuilder->Pat.ProgramNumber[i] == PrgrmID && pBuilder->Pmt[i].ProgramNumber == PrgrmID )
			break;
	}
	if ( i >= MAX_PROGRAM )
		return -2;

	pid = pBuilder->Pmt[i].ElementaryPID[StreamID] ;


	i = 0;
	total_bytes = 0;
	while( total_bytes < Size )
	{
		char* pPayload;
		int  bytes;
		TS_HEADER TSHeader={0};
		TSHeader.start = i == 0 ? 1:0 ;
		TSHeader.pid = pid;
		TSHeader.continuity_ct = pBuilder->Pmt[i].EScounter[StreamID] ;
		if ( ++pBuilder->Pmt[i].EScounter[StreamID] > 0x0f ) 
			pBuilder->Pmt[i].EScounter[StreamID] = 0;
		memset( data, 0xff, TS_PACKET_LENGTH );
		BuildTSHeader( &TSHeader, data, TS_PACKET_LENGTH-4, &pPayload );

		/* start section offset */
		if ( i == 0 )
		{
			*pPayload = 0x0;
			pPayload += *pPayload + 1; 
		}

		bytes = TS_PACKET_LENGTH-(int)(pPayload-data);
		memcpy( pPayload, pData + total_bytes, bytes);
		PutPacket( pBuilder, data, 2, (short)(data-pPayload) );
		i++;
		total_bytes += bytes;

	}

	return 1; 
}



void BuildTSHeader( TS_HEADER* pTSHeader, char* pData, int Bytes, char** pPayload )
{
	unsigned int start;
	pData[0] = SYNC;
	pData[1] = 0;	pData[2]=	0; pData[3] =	0;
    pTSHeader->adaption_ctr = 01;


	start = 4;
	
	//use adaption field to pad null data to get a 188 bytes packet
	if ( Bytes < TS_PACKET_LENGTH-4 )
	{
		int adaption_length = TS_PACKET_LENGTH-4 - Bytes;
		pTSHeader->adaption_ctr = 3;
		pData[4] = TS_PACKET_LENGTH-4 - Bytes - 1 ;
		pData[5] = 0x0;
		start = 4+adaption_length;
	} 
	
	*pPayload = pData+start;
    

	if ( pTSHeader->error )	   pData[1] |= 0x80;	
	if ( pTSHeader->start )	   pData[1] |= 0x40;	
	if ( pTSHeader->priority ) pData[1] |= 0x20;

	pData[1] |= ( pTSHeader->pid >> 8	) &	0x1f;
	pData[2] =   pTSHeader->pid & 0xff;
	pData[3] |= ( pTSHeader->scrambling_ctr & 0x03	) << 6;	
	pData[3] |= ( pTSHeader->adaption_ctr	 & 0x03	) << 4;	
	pData[3] |= pTSHeader->continuity_ct & 0x0f;

}

bool PutPacket( TS_BUILDER* pBuilder, char* pPacketData, short type, short PayloadOffset )
{
	int index;
	if ( pBuilder->PacketsInPoolNum >= PACKET_POOL_NUMBER ) 
		return false;

	index = pBuilder->PacketQueueTail;

	memcpy( pBuilder->Packets[index].Packet, pPacketData, TS_PACKET_LENGTH );
	pBuilder->Packets[index].Type = type;
	pBuilder->Packets[index].PayloadOffset = PayloadOffset;
	if ( ++pBuilder->PacketQueueTail >= PACKET_POOL_NUMBER ) 
		pBuilder->PacketQueueTail = 0;

	pBuilder->PacketsInPoolNum++;

	if ( pBuilder->builder_dumper != NULL )
		pBuilder->builder_dumper( pBuilder->env, TS_PACKET_LENGTH, pPacketData );

	return true;
}


bool PopPacket( TS_BUILDER* pBuilder, char* pPacketData, short* type, short* PayloadOffset )
{
	int index = pBuilder->PacketQueueHead;

	if ( pBuilder->PacketsInPoolNum == 0 ) 
		return false;

	memcpy( pPacketData, pBuilder->Packets[index].Packet, TS_PACKET_LENGTH );
	*type = pBuilder->Packets[index].Type;
	*PayloadOffset = pBuilder->Packets[index].PayloadOffset;

	if ( ++pBuilder->PacketQueueHead >= PACKET_POOL_NUMBER ) 
		pBuilder->PacketQueueHead = 0;

	pBuilder->PacketsInPoolNum--;

	pBuilder->packets++;     

	return true;

}

int  GetPacketNumInPool( TS_BUILDER* pBuilder )
{
	return pBuilder->PacketsInPoolNum;
}



