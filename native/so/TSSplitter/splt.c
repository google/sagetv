/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/errno.h>
#include <unistd.h>
#include <features.h> 
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <getopt.h>
#include <errno.h>
#include <sys/time.h>
#include <math.h>
#include <linux/types.h>
#include <stdarg.h>
#include "TSSplitter.h"


static void flog( char* logname, const char* cstr, ... )
{
	time_t ct;
	struct tm ltm;
	FILE* fp = fopen( logname, "a" );
	if ( fp == NULL ) return;
	time(&ct); localtime_r( &ct, &ltm );
	fprintf( fp, "%02d/%02d/%d %02d:%02d:%02d  ", ltm.tm_mon+1, ltm.tm_mday, ltm.tm_year+1900, 
	ltm.tm_hour, ltm.tm_min, ltm.tm_sec );  
	va_list args;
	va_start(args, cstr);
	vfprintf( fp, cstr, args );
	va_end(args);
	fclose( fp );
}


typedef int (*OUTPUT_DUMP)( void* context, unsigned char* pData, unsigned long lBytes );

#define EPG_MSG_TYPE		10
#define AV_INF_TYPE		11

void postMessage( char* pSrc, char* pData, int MsgType, int Priority );
long EPG_Dumper( void* context, short bytes, void* mesg );
long AVInf_Dumper( void* context, short bytes, void* mesg );


#define TS_BUFFER_PACKETS   24

typedef struct {
   
   TSSPLT *pSplt;
   
   unsigned long long  lTotalOutBytes;
   
   unsigned char OutBuffer[TS_BUFFER_PACKETS*188];
   
   OUTPUT_DUMP    pfnOutput;
   void* pConext;
   
} PARSER; 

int SplitStream( PARSER *pParser, char* pData, int lDataLen );
int FlushOutStreamTS( PARSER *pParser );

int WriteFile( void* context, unsigned char* pData, unsigned long lBytes )
{
	FILE* fd = (FILE*)context;
	int size = fwrite( pData, 1, lBytes, fd );
	return size;
}

unsigned char buf[1024*8];
int main(int argc, char* argv[] )
{
	FILE *fdin, *fdout;	
	int bytes, channel = 0;
	PARSER parser={0};
	unsigned long long  lTotalInBytes = 0;
	bool found_video = false;
	bool found_audio = false;
	

	if ( argc < 3 )
	{
		puts( "splt ts_stream_file, out_mpeg_file channel\n");
		return 1;
	}
	fdin = fopen( argv[1], "rb" );
	if ( fdin == NULL )
	{
		printf( "can't open input file:'%s'\n", argv[1] );
		return 1;
	}
	fdout = fopen( argv[2], "wb" );
	if ( fdout == NULL )
	{
		fclose( fdin );
		printf( "can't open output file:'%s'\n", argv[2] );
		return 1;
	}
	if ( argc > 3 )
	
	channel = atoi(argv[3]);
	printf( "Split channel:%d program out off stream %s into %s...\n", channel, argv[1], argv[2] );
		
	parser.pSplt = OpenTSSplitter( 1 );
	if ( parser.pSplt == NULL )
		return 1;
	
	parser.pfnOutput = WriteFile;
	parser.lTotalOutBytes = 0;
	parser.pConext = fdout;
	
	SelectTSChannel( parser.pSplt, channel+1, true );
	SetEPGDump( parser.pSplt, (void*)EPG_Dumper, &parser );
	SetAVInfDump( parser.pSplt, (void*)AVInf_Dumper, &parser );

	while ( !feof( fdin ) )
	{
		bytes = fread( buf, 1, sizeof(buf), fdin );
		if ( bytes == 0 ) break;
		SplitStream( &parser, buf, bytes );
		
		if ( !found_video && IsVideoStreamReady( parser.pSplt ) )
		{
			printf( "Video decoded at pos:%lld\n", lTotalInBytes );
			found_video = true;
		}
		if ( !found_audio && IsAudioStreamReady( parser.pSplt ) )
		{
			printf( "Audio decoded at pos:%lld\n", lTotalInBytes );
			found_audio = true;
		}
		
		lTotalInBytes += bytes;
		
		//printf( "." );
	}

	
	FlushOutStreamTS( &parser );	
	fclose( fdin );
	fclose( fdout );
	CloseTSSplitter( parser.pSplt );
	printf( "feeded in %lld bytes, wrote %lld bytes %s\n", lTotalInBytes, parser.lTotalOutBytes, argv[2] );
	return 1;
}



int SplitStream( PARSER *pParser, char* pData, int lDataLen )
{
	const unsigned char* pStart;
	unsigned long Bytes;
	unsigned int Size;
	unsigned char *pOutbuf;
	int 	nBufferIndex;
	
	if ( pParser == NULL || pParser->pSplt == NULL )
		return 0;
		
	pStart = (const unsigned char*)pData;
	while ( lDataLen > 0 )
	{
		
		Bytes = min(  TS_PACKET_LENGTH*3, lDataLen );
		ParseData( pParser->pSplt, pStart, Bytes );
		pStart += Bytes;
		lDataLen -= Bytes;
		//pParser->lTotalOutBytes += Bytes;

		if ( pParser->pSplt->output_select == 0 )  //TS output
		{
			if (  NumOfPacketsInPool( pParser->pSplt ) >= TS_BUFFER_PACKETS  )
			{ 
				nBufferIndex = 0;
				pOutbuf = (unsigned char*)pParser->OutBuffer;
				Size = TS_PACKET_LENGTH;
				while ( PopupPacket( pParser->pSplt, pOutbuf, &Size ) )
				{
					pOutbuf += TS_PACKET_LENGTH;
					nBufferIndex++;
					if ( nBufferIndex >= TS_BUFFER_PACKETS )
						break;
				}
				Bytes = nBufferIndex * TS_PACKET_LENGTH;
				if ( pParser->pfnOutput )
					pParser->pfnOutput( pParser->pConext, (char*)pParser->OutBuffer, Bytes );
				pParser->lTotalOutBytes += Bytes;
			}
		} else    //PS output
		{
				Size = sizeof(pParser->OutBuffer);
				pOutbuf =  (unsigned char*)pParser->OutBuffer;
				while ( PopupPacket( pParser->pSplt, pOutbuf, &Size ) )
				{
					if ( pParser->pfnOutput )
						pParser->pfnOutput( pParser->pConext, (char*)pParser->OutBuffer, Size );
					pParser->lTotalOutBytes += Size;
				}
		}
	}
	
	return 1;
}

int FlushOutStreamTS( PARSER *pParser )
{
	unsigned long Bytes;
	unsigned int Size;
	unsigned char *pOutbuf;
	int 	nBufferIndex;
	
	if ( pParser == NULL || pParser->pSplt == NULL )
		return 0;

	if ( ((TSSPLT *)(pParser->pSplt))->output_select == 0 )  //TS output
	{
		while (  NumOfPacketsInPool( (TSSPLT *)pParser->pSplt ) > 0 )
		{ 
			nBufferIndex = 0;
			pOutbuf = (unsigned char*)pParser->OutBuffer;
			Size = TS_PACKET_LENGTH;
			while ( DrainPacket( (TSSPLT *)pParser->pSplt, pOutbuf, &Size ) )
			{
				pOutbuf += TS_PACKET_LENGTH;
				nBufferIndex++;
				if ( nBufferIndex >= TS_BUFFER_PACKETS )
					break;
			}
			Bytes = nBufferIndex * TS_PACKET_LENGTH;
			if ( pParser->pfnOutput )
				pParser->pfnOutput( pParser->pConext, (unsigned char*)pParser->OutBuffer, Bytes );
			pParser->lTotalOutBytes += Bytes;
		}
	} else    //PS output
	{
			Size = sizeof(pParser->OutBuffer);
			pOutbuf =  (unsigned char*)pParser->OutBuffer;
			while ( DrainPacket( (TSSPLT *)pParser->pSplt, pOutbuf, &Size ) )
			{
				if ( pParser->pfnOutput )
					pParser->pfnOutput( pParser->pConext, (unsigned char*)pParser->OutBuffer, Size );
				pParser->lTotalOutBytes += Size;
			}
	}
	
	return 1;

}


long EPG_Dumper( void* context, short bytes, void* mesg )
{
  char src[64]={"EPG"};
  //DVBCaptureDev *CDev = (DVBCaptureDev*)context;
  //if ( CDev == NULL )
  //	return 0;
	
  //strcat( src, CDev->devName );
  flog( "EPGData.log", "%s:%s\n", src, mesg );
  //postMessage( src, (char*)mesg, EPG_MSG_TYPE, 100 );
  
  return 1;
}

long AVInf_Dumper( void* context, short bytes, void* mesg )
{
  char src[64]={"AVINF"};
  //DVBCaptureDev *CDev = (DVBCaptureDev*)context;

  //if ( CDev == NULL )
  //	  return 0;

  //strcat( src, CDev->devName );
  flog( "AVInf.log", "%s:%s\n", src, mesg );
  postMessage( src, (char*)mesg, AV_INF_TYPE, 100 );
  
  return 1;
}


void postMessage( char* pSrc, char* pData, int MsgType, int Priority )
{
	printf( "%s\n", pData );
}


