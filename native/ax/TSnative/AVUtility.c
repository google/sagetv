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
// Eliminate silly MS compiler security warnings about using POSIX functions
#pragma warning(disable : 4996)


#include <stdio.h>
#include <stdlib.h>	
#include <memory.h>	
#include <string.h>	
#include <assert.h>

#ifdef WIN32
#include <pshpack1.h>
#endif

#include "TSnative.h"
#include "AVUtility.h"


////////////////////////////////////
#ifdef windows
#include <windows.h>
void CatchError( char* file, int line )
{
	static SYSTEMTIME last_st = {0};
	SYSTEMTIME st;
	FILE* fp;
	GetSystemTime( &st );

	printf( "Caught a error\n" );

	//too busy skip
	if ( st.wDay == last_st.wDay && st.wHour == last_st.wHour && st.wMinute == last_st.wMinute &&
		st.wSecond == last_st.wSecond && st.wMilliseconds - last_st.wMilliseconds < 50 )
	{
		return ;
	}
	
	fp = fopen( "ErrorCatch.log", "a+" );
	if ( fp != NULL )
	{

		fprintf( fp, "%d/%d/%d %d:%d:%d %d\r\n", st.wMonth, st.wDay, st.wYear,
			st.wHour, st.wMinute, st.wSecond, st.wMilliseconds );

		fflush( fp );
		fclose( fp );
	}

	last_st = st;
}
#endif
////////////////////////////////////
int  UnpackPackHeader(   char* pData, int Bytes, unsigned long *pDemux, LONGLONG* ref_time );
int  UnpackPadPack( const unsigned char* pbData, int Bytes );
int  UnpackPsmPack( const unsigned char* pbData, int Bytes );

//void _cdecl _check_commonlanguageruntime_version(){}
char* Mpeg2Profile(	int	profile	)
{
	switch(	profile	) {	
	case 5:	 return("Simple") ;	
	case 4:	 return("Main")	  ;	
	case 3:	 return("SNR Scalable")	;
	case 2:	 return("Spatially Scalable") ;	
	case 1:	 return("High")	;
	}
	return "unknown	profile";
}

char* Mpeg2Level( int level	)
{
	switch(	level )	{
	case 10	: return("Low")	;
	case 8	: return("Main") ;
	case 6	: return("High 1440") ;	
	case 4	: return("High") ;
	}
	return "unknown	level";	
}						

////////////////////////////////////

const long PictureTimes[16]	= {	
	0,
	(long)((double)10000000	/ 23.976),
	(long)((double)10000000	/ 24),
	(long)((double)10000000	/ 25),
	(long)((double)10000000	/ 29.97),
	(long)((double)10000000	/ 30),
	(long)((double)10000000	/ 50),
	(long)((double)10000000	/ 59.94),
	(long)((double)10000000	/ 60)
};

const float	PictureRates[] =   {
	(float)0,
	(float)23.976,	
	(float)24,	
	(float)25,	
	(float)29.97,
	(float)30,	
	(float)50,	
	(float)59.94,
	(float)60.0
};

const long AspectRatios[16]	= {	
	0,
	393700,	
	(long)(393700.0	* 0.6735),
	(long)(393700.0	* 0.7031),
	(long)(393700.0	* 0.7615),
	(long)(393700.0	* 0.8055),
	(long)(393700.0	* 0.8437),
	(long)(393700.0	* 0.8935),
	(long)(393700.0	* 0.9375),
	(long)(393700.0	* 0.9815),
	(long)(393700.0	* 1.0255),
	(long)(393700.0	* 1.0695),
	(long)(393700.0	* 1.1250),
	(long)(393700.0	* 1.1575),
	(long)(393700.0	* 1.2015),
	0
};

/*	Bit	rate tables	*/
const unsigned short BitRates[3][16] ={
	{ 	0, 32,	64,	 96,  128, 160,	192, 224, 256, 288,	320, 352, 384, 416,	448, 0 },
	{	0, 32,	48,	 56,   64,	80,	 96, 112, 128, 160,	192, 224, 256, 320,	384, 0 },
	{	0, 32,	40,	 48,   56,	64,	 80,  96, 112, 128,	160, 192, 224, 256,	320, 0 }
};

#if !defined(__APPLE__)
unsigned long DWORD_SWAP(unsigned long x)
{
    return
     ((unsigned long)( ((x) << 24) | ((x) >> 24) |
               (((x) & 0xFF00) << 8) | (((x) & 0xFF0000) >> 8)));
}
#endif // __APPLE__

char* PTS2HexString( LONGLONG pts, char* hexs, int size )
{
	int i;
	unsigned char ch, *p;
	bool leading = true;
	if ( hexs == NULL )
		return "";


	p = (unsigned char*)hexs;
	*p = 0;
	if ( pts == 0  )
	{
		if ( size > 1 )
		{
			*p++ = '0';
			*p=0x0;
			return hexs;
		} else
		{
			return hexs;
		}

		size = _MIN( size-1, 10 );
		for ( i = 0; i<size; i++ )
			*p++ = '0';
		*p=0x0;
		return hexs;
	}

	if ( pts < 0 )
	{
		*p++ = '-';
		pts = -pts;
		*p = 0;
	}
	for ( i = 0; i<8; i++ )
	{	
		ch = (unsigned char)(pts >>(56-i*8));
		if ( ch == 0 && leading )
			continue;
		leading = false;

		if ( size < 2 ) 
			return hexs;

		sprintf( (char*)p, "%02x", ch );
		p += 2;
		*p = 0;

	}
	return hexs;
}

char* PTS2TimeString( char *buf, int len, LONGLONG* pllPTS )
{
	char tmp[32];
	int sign = 0;
	int ms, sec, min, hr;
	LONGLONG ll;
	ll = (*pllPTS/90);
	if ( ll < 0 ) { ll = - ll; sign = 1; };

	ms =  (int)ll%1000; ll /= 1000;
	sec = (int)ll % 60; ll /= 60;
	min = (int)ll % 60; ll /= 60;
	hr =  (int)ll;
	sprintf( tmp, "%d:%02d:%02d.%03d", hr, min, sec, ms );
	strncpy(  buf, tmp, len );
	return buf;
}

char* PTS2TimeString2( char *buf, int len, LONGLONG* pllPTS )
{
	char tmp[32];
	int sign = 0;
	int ms, sec, min, hr;
	LONGLONG ll;
	ll = (*pllPTS/90);
	if ( ll < 0 ) { ll = - ll; sign = 1; };

	ms =  (int)ll%1000; ll /= 1000;
	sec = (int)ll % 60; ll /= 60;
	min = (int)ll % 60; ll /= 60;
	hr =  (int)ll;
	sprintf( tmp, "%d:%02d:%02d,%03d", hr, min, sec, ms );
	strncpy(  buf, tmp, len );
	return buf;
}


/*
bool UnpackTimeStamp(const unsigned char	* pData, LONGLONG *Clock )
{
	unsigned char Byte1	= pData[0];	
	unsigned long Word2	= ((unsigned long)pData[1] << 8) + (unsigned long)pData[2];	
	unsigned long Word3	= ((unsigned long)pData[3] << 8) + (unsigned long)pData[4];	
	INTEGER64 liClock;

	//	Do check 
	//if ( (Byte1 & 0xC0) != 0x00 || ( Byte1 & 0x30 ) == 0x0 || (Word2 & 1) != 1 || (Word3 & 1) != 1) {
	//	return false;
	//}
	if ( ( Byte1 & 0x30 ) == 0x0 || (Word2 & 1) != 1 || (Word3 & 1) != 1) {
		return false;
	}


	liClock.HighPart = (Byte1 & 8 ) != 0;
	liClock.LowPart	 = (unsigned long)((((unsigned long)Byte1 &	0x6) << 29)	+
					   (((unsigned long)Word2 &	0xFFFE)	<< 14) +
					   ((unsigned long)Word3 >>	1));

	*Clock = liClock.QuadPart;

	return true;
}
*/


bool UnpackTimeStamp(const unsigned char	* pData, LONGLONG *Clock )
{
	//unsigned char Byte1	= pData[0];	
	//unsigned long Word2	= ((unsigned long)pData[1] << 8) + (unsigned long)pData[2];	
	//unsigned long Word3	= ((unsigned long)pData[3] << 8) + (unsigned long)pData[4];	
	LONGLONG pts;

	if ( ( pData[0] & 0x30 ) == 0x0 || (pData[2] & 1) != 1 || (pData[4] & 1) != 1) {
		return false;
	}

	pts  = ((unsigned long long)(((pData[0] >> 1) & 7))) << 30;
  	pts |= (unsigned long long)pData[1] << 22;
  	pts |= (unsigned long long)(pData[2]>>1) << 15;
  	pts |= pData[3] << 7;
  	pts |= (pData[4]>>1);

	*Clock = pts;

	return true;
}



bool UnpackTimeStamp1(const unsigned char*pData, LONGLONG *Clock )
{
	unsigned char Byte1	= pData[0];	
	unsigned long Word2	= ((unsigned long)pData[1] << 8) + (unsigned long)pData[2];	
	unsigned long Word3	= ((unsigned long)pData[3] << 8) + (unsigned long)pData[4];	
	LONGLONG liClock;

	if ( ( Byte1 & 0x30 ) == 0x0 || (Word2 & 1) != 1 || (Word3 & 1) != 1) {
		return false;
	}


	liClock = (Byte1 & 8 ) != 0;
	liClock <<= 32;
	liClock |= (unsigned long)((((unsigned long)Byte1 &	0x6) << 29)	+
					   (((unsigned long)Word2 &	0xFFFE)	<< 14) +
					   ((unsigned long)Word3 >>	1));

	*Clock = liClock;

	return true;
}

void PackTimeStamp( unsigned char*pData, LONGLONG *llPTS, unsigned char Leading )
{
	unsigned char Byte;

	Byte = Leading << 4;
	Byte |= (unsigned char)((*llPTS) >> (30-1)) & 0xe;		//PTS[32..30]  3
	Byte |= 1;                              //mark bit     1
	//if ( pData[0] != Byte ) CatchError( __FILE__, __LINE__ );
	pData[0] = Byte;

	Byte = (unsigned char)((*llPTS) >> 22) & 0xff;           //PTS[29..22]  8
	//if ( pData[1] != Byte ) CatchError( __FILE__, __LINE__ );
	pData[1] = Byte;					    

	Byte = (unsigned char)((*llPTS) >> (15-1)) & 0xfe;       //PTS[21..15]  7
	Byte |= 1;							    //mark bit     1
	//if ( pData[2] != Byte ) CatchError( __FILE__, __LINE__ );
	pData[2] = Byte;

	Byte = (unsigned char)((*llPTS) >> 7) & 0xff;			//PTS[14..7]   8
	//if ( pData[3] != Byte ) CatchError( __FILE__, __LINE__ );
	pData[3] = Byte;						

	Byte = (unsigned char)((*llPTS) << 1 ) & 0xfe;           //PTS[6..0]    7
	Byte |= 1;							    //mark bit     1
	//if ( pData[4] != Byte ) CatchError( __FILE__, __LINE__ );
	pData[4] = Byte;
}


//_inline bool IsTsHeader( const unsigned char* pStart	)
//{
//	return ( (*pStart ==  SYNC) );
//}
//
//_inline bool IsVideoType( unsigned	char StreamType	)
//{
//	return ( StreamType	== 2 ||	StreamType == 1	) ;	
//}
//
//_inline bool IsAudioType( unsigned	char StreamType	)
//{
//	return ( StreamType	== 3 ||	StreamType == 4	|| StreamType == 0x81 )	;  //0x81 is Dolby	AC3	
//}

bool CheckTSContinuity( unsigned char TSContinuity, unsigned char* pContinuity, bool StartFlag )
{
	bool ret;
	if ( StartFlag )
	{
		*pContinuity = TSContinuity;
		return true;
	}

	(*pContinuity)++;
	*pContinuity &= 0x0f;

	ret = ( TSContinuity == *pContinuity ) ;
	*pContinuity = TSContinuity;
	return ret;

}


short MPEG2SequenceHeaderSize( const unsigned char *pb )	
{
	/*	No quantization	matrices ? */
	if ((pb[11]	& 0x03)	== 0x00) {
		return 12 +	10;	
	}
	/*	Just non-intra quantization	matrix ? */	
	if ((pb[11]	& 0x03)	== 0x01) {
		return 12 +	64 + 10;
	}
	/*	Intra found	- is there a non-intra ? */	
	if (pb[11 +	64]	& 0x01)	{
		return 12 +	64 + 64	+ 10;
	} else {
		return 12 +	64 + 10;
	}
}

bool IsMPEG2StartCode( const unsigned char* pStart, unsigned long StartCode )
{
	unsigned long code;
	code = *pStart++;
	code <<= 8;
    code |= *pStart++;
	code <<= 8;
	code |= *pStart++;
	code <<= 8;
	code |= *pStart;
	return ( code == StartCode );
}

bool SearchMPEG2StartCode( const unsigned char* pStart, int Bytes, unsigned	long StartCode,	const unsigned char** ppSeqStart )
{
	const unsigned char	*pbData;
	int	 len;
	unsigned long code;

	if ( Bytes < 4 )
		return false;

	pbData = pStart;
	code = 0xffffff00;
	len = Bytes;

	code |=  *pbData++;
	while ( --len && code != StartCode )
	{
		code = ( code << 8 )| *pbData++;
	}

	if ( code == StartCode )
	{
		*ppSeqStart	= pbData-4;
		return true;
	}

	*ppSeqStart	= NULL;
	return false;

}

bool SearchMPEG4VOLCode( const unsigned char* pStart, int Bytes, const unsigned char** ppSeqStart )
{
	const unsigned char	*pbData;
	int	 len;
	unsigned long code;

	if ( Bytes < 4 )
		return false;

	pbData = pStart;
	code = 0xffffff00;
	len = Bytes;

	code |=  *pbData++;
	while ( --len && ( code < 0x120 || code > 0x12f ) )
	{
		code = ( code << 8 )| *pbData++;
	}

	if ( code >= 0x120 && code <= 0x12f )
	{
		*ppSeqStart	= pbData-4;
		return true;
	}

	*ppSeqStart	= NULL;
	return false;

}
unsigned int SearFrameType(  char* pData, int Size )
{
	const unsigned char* ptr;

	if ( SearchMPEG2StartCode( (const unsigned char*)pData, Size, PICTURE_START_CODE, &ptr ) )
	{
		unsigned char picture_coding_type = ( (*(ptr+5)&0x38 )>>3 );
		if ( picture_coding_type == 1 || picture_coding_type == 2 || picture_coding_type == 3 )
		return picture_coding_type;
	}
	return 0;
} 


static bool UnpackAC3( AV_CONTEXT* av, const unsigned char *pbData, int Size );
static bool UnpackMpegAudio( AV_CONTEXT* av, const unsigned char * pbData, int Size	);
static bool UnpackLPCM( AV_CONTEXT* av, const unsigned char	* pbData, int Size	);
static bool UnpackMpegVideoHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size );
static bool UnpackMpegAudioHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size );
static bool UnpackAC3AudioHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size	);
static bool UnpackDixVTypeHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size );
static bool UnpackH264VideoHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size );
static bool UnpackLPCMHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size	);
static bool UnpackAACAudioHeader( AV_CONTEXT* av, bool bGoupStart, const unsigned char* pStart, int Size );
static bool UnpackAACAudioADTS( AV_CONTEXT* av, const unsigned char * pbData, int Size	);
static bool UnpackAACAudioLATM( AV_CONTEXT* av, const unsigned char * pbData, int Size	);
static bool UnpackMpeg4VideoHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size );
static bool UnpackMpegVC1VideoHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size );
static bool UnpackDTSHead( AV_CONTEXT* av, const unsigned char *pbData, int Size );

static int DetectPrivateType( const char* pData, int Size, int* MagicBytes )
{
	if ( Size < 4 )
	{
		printf( "Too few data to detecte private audio stream type" );
		return 0;
	}

	if ( IsMPEG2StartCode( (const unsigned char*)pData, MAGIC_DIXV_STREAM ) )
	{
		_MEDIA_DATA* pMediaData;
		pMediaData = (_MEDIA_DATA*)(pData +4); 
		*MagicBytes = 4 + sizeof(_MEDIA_DATA) + pMediaData->cbFormat;
		return 0xf1;   //customized DixV stream
	}

	if ( IsMPEG2StartCode( (const unsigned char*)pData, MAGIC_AC3_STREAM ) )
	{
		*MagicBytes = 4;
		return 0x81;
	}

	return 0;
}



/*	Parse a	packet header when present */
/* return 1: success; 0:packet data is ready( length is short); -1:error PES packet */
int UnpackPESHeader( const unsigned char* pbData, int Bytes, PES_INFO *pPESInfo )	
{
	int i;
	unsigned char* p;
	unsigned short Len;
	unsigned char  StreamId;
	//static unsigned char NoPTSPackCode[] = { PRIVATE_STREAM_2, PROGRAM_STREAM_MAP, ECM_STREAM, EMM_STREAM,
	//	                                     PROGRAM_STREAM_DIRECTORY, DSMCC_STREAM, H222_E, PADDING_STREAM };

	if ( Bytes < 9 )
		return -2;   //too short

	if ( pbData[0] != 0x0 || pbData[1] != 0x0 || pbData[2] != 0x1 )
		return 0;


	memset(	pPESInfo,	0, sizeof(PES_INFO));
	StreamId = pbData[3];
	if ( !IsVideoStreamId(StreamId) && !IsAudioStreamId(StreamId) && !IsPrivateStreamId(StreamId) && !IsVC1StreamId(StreamId) )
		return 0;

	//3 bytes Start code prefix, 1 stream ID( 110x xxxx audio, 1110 yyyy video ), 2 bytes packet length
	Len = pbData[5] | (pbData[4] << 8);
	pPESInfo->HeaderLen = 6;	 
	pPESInfo->PacketLen = Len	+ 6;
	pPESInfo->HasPts    = false;	
	pPESInfo->llPts     = 0;
	pPESInfo->StreamId  = StreamId;

	//for ( i = 0; i<(int)sizeof(NoPTSPackCode); i++ )
	//	if ( NoPTSPackCode[i] == StreamId )
	//		return 0;

	//I have no choice to turn down audio streamid (1101 xxxx )  ZQ. 
	//if ( !IsVideoStreamId(StreamId) && !(StreamId & 0xf0 == 0xc0 ) && !IsPrivateStreamId( StreamId) )
	p = (unsigned char*)&pbData[6];
	Bytes -= 6;

	//skip mpeg1 stuffing ff
	for ( i = 0; i<16 && *p == 0xff && Bytes>0; i++ )
	{
		p++; Bytes--;
		pPESInfo->HeaderLen++;
	}

	if ( Bytes == 0 ) return 0;

	if (  ( *p & 0xc0 )== 0x80 )
		pPESInfo->type = MPEG2_TYPE;
	else
		pPESInfo->type = MPEG1_TYPE;

	if ( pPESInfo->type == MPEG2_TYPE )
	{
		if ( pbData[8] > 64 ) 
		{	
			memset(	pPESInfo,	0, sizeof(PES_INFO));
			return 0;
		}
		/* PES	header */
		pPESInfo->HeaderLen += 3 + pbData[8]; //6 bytes header + 3 bytes option + HeadExtrLen (PTS bytes length)
		Bytes -= (3+pbData[8]);
		if ( Bytes < 0 )
		{
			memset(	pPESInfo,	0, sizeof(PES_INFO));
			return 0;
		}

		/*	Extract	the	PTS	*/
		if (  0 != ( pbData[7]	& 0x80 ) )	
		{
			pPESInfo->HasPts = UnpackTimeStamp(	(const unsigned char*)pbData + 9, &pPESInfo->llPts );
			if ( pPESInfo->HasPts )
				pPESInfo->PTSOffset = 9;
			else
			{
				memset(	pPESInfo,	0, sizeof(PES_INFO));
				return 0;
			}

		}
		/*	Extract	the	DTS	*/
		if ( 0 != ( pbData[7]	& 0x40 ) )	
		{
			pPESInfo->HasDts = UnpackTimeStamp(	(const unsigned char*)pbData + 9 + 5, &pPESInfo->llDts );
			if ( pPESInfo->HasDts )
				pPESInfo->DTSOffset = 9+5;
			else
			{
				pPESInfo->llDts = 0; //because I put wrong DTS in old recording, I have to use this way for playing back those.
				//memset(	pPESInfo,	0, sizeof(PES_INFO));
				//return 0;
			}
		}
	} else
	if ( pPESInfo->type == MPEG1_TYPE )
	{
		unsigned char b;
		b = (*p)>>6;
		if ( b == 1 )
		{
			p += 2;
			b = (*p)>>6;
			pPESInfo->HeaderLen += 2;
			Bytes -= 2;
		} 
		if ( b == 0 )
		{
			if ( Bytes < 4 ) return 0;
			if ( 0 != (*p & 0x20 ) )
			{
				pPESInfo->HasPts = UnpackTimeStamp(	(const unsigned char*)p, &pPESInfo->llPts );
				pPESInfo->HeaderLen += 4;
				Bytes -= 4;
				if ( Bytes < 0 || !pPESInfo->HasPts ) 
				{
					memset(	pPESInfo,	0, sizeof(PES_INFO));
					return 0;
				}
				pPESInfo->PTSOffset = p-pbData;
			}

			if ( Bytes < 5 ) return 0;
			if ( 0 != (*p & 0x10 ) )
			{
				pPESInfo->HasDts = UnpackTimeStamp(	(const unsigned char*)p+5, &pPESInfo->llDts );
				pPESInfo->HeaderLen += 5;
				Bytes -= 5;
				if ( Bytes < 0 || !pPESInfo->HasDts ) 
				{
					memset(	pPESInfo,	0, sizeof(PES_INFO));
					return 0;
				}
				pPESInfo->DTSOffset = p+5-pbData;

			}
			pPESInfo->HeaderLen++;
		}
	} else
	{
		memset(	pPESInfo,	0, sizeof(PES_INFO));
		return -1;
	}


	return 1;
}

//llPTS is -1 not change; -2 drop llPTS;
 void UpdatePESPTS( unsigned char* pbData, int Bytes, PES_INFO* pPESInfo, LONGLONG* llPTS, LONGLONG* llDTS )	
{

	if ( pPESInfo->HasPts && pPESInfo->PTSOffset > 6 && *llPTS != -1)
	{
		unsigned char byte = 0;
		if ( *llPTS != -2 ) byte  = 0x2;
		else  *llPTS = 0;
		if ( pPESInfo->HasDts && *llDTS != -2 ) byte |= 0x1;
		PackTimeStamp(	(unsigned char*)pbData+pPESInfo->PTSOffset, llPTS, byte );
	}
	if ( pPESInfo->HasDts && pPESInfo->DTSOffset > 6 && *llDTS != -1)
	{
		unsigned char byte = 0;
		if ( *llDTS != -2 ) byte = 0x1;
		else
			*llDTS = 0;
		PackTimeStamp( (unsigned char*)pbData+pPESInfo->DTSOffset, llDTS, byte );
	}
}

unsigned char GetPrivatePESSubID( const unsigned char* pbData, int Bytes, PES_INFO *pPESInfo )
{
	if ( Bytes < pPESInfo->HeaderLen+1 )
		return 0;

	return *(pbData + pPESInfo->HeaderLen);
}


int DecodeAVHeader( AV_CONTEXT* av, PES_INFO* pPESInfo, unsigned char StreamType, 
				                              const unsigned char * pData, int Size )
{
	int stream_type = 0;
	const char* data;
	int size;
	int ret =0;

	if ( pPESInfo->PacketLen < pPESInfo->HeaderLen )  //PES pack length is 0 (TS stream ), set a default PacketLen 
		pPESInfo->PacketLen = Size;

	if ( pPESInfo->StreamId &&  pPESInfo->HeaderLen )
	{
		data = (const char*)pData + pPESInfo->HeaderLen;
		size = Size - pPESInfo->HeaderLen;
		if ( size > (int)(pPESInfo->PacketLen-pPESInfo->HeaderLen)  )
			size = pPESInfo->PacketLen-pPESInfo->HeaderLen ;
	} else
	{
		data = (const char*)pData;
		size = Size;
	}

	//if ( IsVideoStreamId( pPESInfo->StreamId ) && StreamType != 0x06 )  //ZQ I got a QAM stream that carries two video streams, one of sub video is tagged with streamType 6
	if ( IsVideoStreamId( pPESInfo->StreamId ))
	{
		if ( StreamType == H264_STREAM_TYPE  )
		{
			ret = UnpackH264VideoHeader( av, (unsigned char*)data, size );
			if ( ret )
			{
				stream_type =  H264_STREAM_TYPE;
				av->VideoPES = *pPESInfo;
			}
		}
		else
		{   //video header doesn't go with PES packet (often in QAM stream )
			if ( av->VideoType == MPEG2_VIDEO || av->VideoType == MPEG1_VIDEO  )  
			{
				stream_type = av->VideoType;
				av->VideoPES = *pPESInfo;
			} else
			{
				ret = UnpackMpegVideoHeader( av, (unsigned char*)data, size );
				if ( ret )
				{
					stream_type = av->VideoType;
					av->VideoPES = *pPESInfo;
				} else
				{
					ret = UnpackMpeg4VideoHeader( av, (unsigned char*)data, size );
					if ( ret )
					{
						stream_type = MPEG4_STREAM_TYPE;
						av->VideoPES = *pPESInfo;
					} else
					{
						if ( av->H264Param.guessH264 > -200 )
						{
							ret = UnpackH264VideoHeader( av, (unsigned char*)data, size );
							if ( ret )
							{
								stream_type =  H264_STREAM_TYPE;
								av->VideoPES = *pPESInfo;
							}
						} else
						{
							ret = UnpackMpegVC1VideoHeader( av, (unsigned char*)data, size );
							if ( ret )
							{
								stream_type =  VC1_STREAM_TYPE;
								av->VideoPES = *pPESInfo;
							}
						}
					}
				}

			}
		}
	}

	else
	if ( IsVC1StreamId( pPESInfo->StreamId ))
	{
		stream_type = 0;
		if (  StreamType != 0x82 && StreamType != 0x81 )
		{
			ret = UnpackMpegVC1VideoHeader( av, (unsigned char*)data, size );
			if ( ret )
			{
				stream_type =  VC1_STREAM_TYPE;
				av->VideoPES = *pPESInfo;
			}
		} else
		if (  StreamType == 0x82 )
		{
			 ret = UnpackDTSHead( av, (unsigned char*)data, size );
			 if ( ret )
			 {
				stream_type = 0x82;
				av->AudioPES = *pPESInfo;
			 }
		} else
		{
			if ( UnpackAC3AudioHeader( av, (unsigned char*)data, size )  )
			{
				stream_type = 0x81;
				av->AudioPES = *pPESInfo;
			} else
			if ( UnpackLPCMHeader( av, (unsigned char*)data, size ) )
			{
				stream_type = 0x81;
				av->AudioPES = *pPESInfo;
			} 
		}
	}
	else
	if ( IsAudioStreamId( pPESInfo->StreamId )  )
	{
		if ( StreamType == AAC_STREAM_TYPE || StreamType == AAC_HE_STREAM_TYPE )
		{
			if ( ( ret = UnpackAACAudioHeader( av,  true, (unsigned char*)data, size )) )
			{
				stream_type = AAC_STREAM_TYPE;
			}
			av->AudioPES = *pPESInfo;

		} else
		if ( UnpackMpegAudioHeader( av, (unsigned char*)data, size ) )
		{
			stream_type = 4;
			av->AudioPES = *pPESInfo;
		} else
		if ( UnpackAC3AudioHeader( av, (unsigned char*)data, size )  ) //BBC-HD in DVB-S uses audo stream id 0xc0 carrying AC3 instead of proviate streamid 0x81
		{
			stream_type = 0x81;
			av->AudioPES = *pPESInfo;
		}
	}
	else
	if ( IsPrivateStreamId( pPESInfo->StreamId ) || (( pPESInfo->StreamId == 0 ) && ( StreamType == 0x81 ) )  )   
	//if ( IsPrivateStreamId( pPESInfo->StreamId ) || (( pPESInfo->StreamId == 0 ) && IsPrivateStreamId( StreamType ) ))
	{
		//if audio is Private stream, we detecte if it's AC3 or something else.
		int private_bytes =0;

		//if stream type is not specified, we need to dectecte it by MAGIC ID
		if ( StreamType != 0 )
			stream_type = StreamType;
		else
			stream_type = DetectPrivateType( data, size, &private_bytes );

		if ( stream_type )
		{
			data += private_bytes;
			size -= private_bytes;
			av->MagicLen = private_bytes;
			if ( stream_type == 0x81 )
			{
				stream_type = 0;
				if ( UnpackAC3AudioHeader( av, (unsigned char*)data, size )  )
				{
					stream_type = 0x81;
					av->AudioPES = *pPESInfo;
				} else
				if ( UnpackLPCMHeader( av, (unsigned char*)data, size ) )
				{
					stream_type = 0x81;
					av->AudioPES = *pPESInfo;
				} 

			}
			else
			if (  stream_type == 0x06 )
			{
				stream_type = 0;
				if ( UnpackAC3AudioHeader( av, (unsigned char*)data, size ) )
				{
					stream_type = 0x81;
					av->AudioPES = *pPESInfo;
				} else
				if ( UnpackMpegAudioHeader( av, (unsigned char*)data, size ) )
				{
					stream_type = 4;
					av->AudioPES = *pPESInfo;
				} else
				if ( UnpackLPCMHeader( av, (unsigned char*)data, size ) )
				{
					stream_type = 0x81;
					av->AudioPES = *pPESInfo;
				} 
			}
			else
			if ( stream_type == 0xf1 )
			{
				if ( UnpackDixVTypeHeader( av, (unsigned char*)data, size ) )
				{
					stream_type = 0xf1;
					av->VideoPES = *pPESInfo;
				} else
					stream_type = 0;
			} else
			if (  stream_type == VC1_STREAM_TYPE )
			{
				ret = UnpackMpegVC1VideoHeader( av, (unsigned char*)data, size );
				if ( ret )
				{
					stream_type =  VC1_STREAM_TYPE;
					av->VideoPES = *pPESInfo;
				}
			} else
			if ( stream_type == 0x82 )
			{
				 ret = UnpackDTSHead( av, (unsigned char*)data, size );
				 if ( ret )
				 {
					stream_type = 0x82;
					av->AudioPES = *pPESInfo;
				 }
			} else
			{
				stream_type = 0;
			}
		} else
		{
			//still not know, force try to decode AC3
			if ( UnpackAC3AudioHeader( av, (unsigned char*)data, size ) )
			{
				stream_type = 0x81;
				if ( (data[0] & 0xf0) == 0x80 )
					av->MagicLen = 4;

				av->AudioPES = *pPESInfo;
			} 
			else
			if ( UnpackLPCMHeader( av, (unsigned char*)data, size ) )
			{
				stream_type = 0x81;
				if ( (data[0] & 0xf0) == 0x80 )
					av->MagicLen = 4;
				av->AudioPES = *pPESInfo;
			} 
		}

	}
	else //QAM don't follow standard to header after sync packet
	if ( pPESInfo->StreamId == 0 )
	{
		if ( StreamType == AAC_STREAM_TYPE || StreamType == AAC_HE_STREAM_TYPE )
		{
			if ( ( ret = UnpackAACAudioHeader( av, false, (unsigned char*)data, size ) ) )
			{
				stream_type = AAC_STREAM_TYPE;
			}
		} else
			UnpackMpegVideoHeader( av, pData, Size );
	}
	return stream_type;
}

static bool UnpackMpegUserDataHeader( MPEG_USER_DATA* ud, const unsigned char* pStart, int Size	);
int DecodeUserHeader( MPEG_USER_DATA* ud, PES_INFO* pPESInfo, unsigned char StreamType, 
				                              const unsigned char * pData, int Size )
{
	const char* data;
	int size;
	bool ret = 0;

	if ( pPESInfo->PacketLen < pPESInfo->HeaderLen )  //PES pack length is 0 (TS stream ), set a default PacketLen 
		pPESInfo->PacketLen = Size - pPESInfo->HeaderLen;

	data = (const char*)pData + pPESInfo->HeaderLen;
	size = _MIN( (Size - pPESInfo->HeaderLen), (int)pPESInfo->PacketLen );
	if ( size < 0 ) return 0;

	if ( IsVideoStreamId( pPESInfo->StreamId ) )
	{
		ret = UnpackMpegUserDataHeader( ud, (unsigned char*)data, size );
		return ret;
	}
	return 0;
}
int CheckProprietaryStream( const char* pData, int Size )
{
	if ( Size < 4 )
	{
		printf( "Too few data to detecte private audio stream type" );
		return 0;
	}

	if ( IsMPEG2StartCode( (unsigned char*)pData, MAGIC_DIXV_STREAM ) )
	{
		return 0xf1;   //customized DixV stream
	}

	return 0;
}

int SkipPrivateData( AV_CONTEXT* av, PES_INFO* pPESInfo, const unsigned char * pData, int Size )
{
	int private_bytes;

	//if ( pPESInfo->StreamId != PRIVATE_STREAM_1 && pPESInfo->StreamId != PRIVATE_STREAM_2 )
	if ( !IsPrivateStreamId( pPESInfo->StreamId ) )
		return 0;

	if ( av->AudioType == AC3_AUDIO || av->AudioType == DTS_AUDIO )
	{
		if ( (pData[0] & 0xf0 ) == 0x80 )
		{
			return 4;
		}

	} else
	if (  DetectPrivateType( (char*)pData, Size, &private_bytes ) )
	{
		return  private_bytes;
	}

	return 0;;
}




int gcd(int a, int b){
    if(b) return gcd(b, a%b);
    else  return a;
}

float Mepg2FrameRate( unsigned char code )
{
	switch (code &0x0f ) {
	case 1: return (float)24000.0/1001;
	case 2: return 24;
	case 3: return 25;
	case 4: return (float)30000.0/1001;
	case 5: return 30;
	case 6: return 50;
	case 7: return (float)60000.0/1001;
	case 8: return 60;
	default: return 0;
	}
	return 0;
}

float Mepg2AspectRatio( unsigned char code, long width, long height )
{
	switch ( code & 0x0f ) {
	case 1:
		return 1;
		//return ((float)width)/((float)height);
	case 2:
		return (float)4.0/3;
	case 3:
		return (float)16.0/9;
	case 4:
		return (float)221/100;

	case 5:
		return (float)12/11;  //MPEG4 625type 4:3 ISO 14496-2
	case 6:
		return (float)10/11;  //MPEG4 525 type 4:3 ISO 14496-2
	case 7:
		return (float)16/11;  //MPEG4 16:11 (625-type stretched for 16:9 picture) ISO 14496-2
	case 8:
		return (float)40/33;  //MPEG4 40:33 (525-type stretched for 16:9 picture) ISO 14496-2
	
	case 0x0f:
		return ((float)width)/((float)height);

	default:
		return 0;
	}

	return 0;
}


int Mepg2AspectRatioNomi( unsigned char code, long width, long height )
{
	switch ( code & 0x0f ) {
	case 1:
		return 1;
		//return width/gcd(width, height);
	case 2:
		return 4;
	case 3:
		return 16;
	case 4:
		return 221;

	case 5:
		return 12;  //MPEG4 625type 4:3 ISO 14496-2
	case 6:
		return 10;  //MPEG4 525 type 4:3 ISO 14496-2
	case 7:
		return 16;  //MPEG4 16:11 (625-type stretched for 16:9 picture) ISO 14496-2
	case 8:
		return 40;  //MPEG4 40:33 (525-type stretched for 16:9 picture) ISO 14496-2

	case 0x0f:
		return height;

	default:
		return 0;
	}
	return 0;
}

int Mepg2AspectRatioDeno( unsigned char code, long width, long height )
{
	switch ( code & 0x0f ) {
	case 1:
		return 1;
		//return height/gcd(width, height);
	case 2:
		return 3;
	case 3:
		return 9;
	case 4:
		return 100;

	case 5:
		return 11;  //MPEG4 625type 4:3 ISO 14496-2
	case 6:
		return 11;  //MPEG4 525 type 4:3 ISO 14496-2
	case 7:
		return 11;  //MPEG4 16:11 (625-type stretched for 16:9 picture) ISO 14496-2
	case 8:
		return 33;  //MPEG4 40:33 (525-type stretched for 16:9 picture) ISO 14496-2

	case 0x0f:
		return width; 

	default:
		return 1;
	}
	return 1;
}

float Mepg1AspectRatio( unsigned char code )
{
	switch ( code & 0x0f ) {
	case 1:
		return 1.0;
	case 2:
		return (float)0.6735;
	case 3:
		return (float)0.7031;
	case 4:
		return (float)0.7615;
	case 5:
		return (float)0.8055;
	case 6:
		return (float)0.8437;
	case 7:
		return (float)0.8935;
	case 8:
		return (float)0.9157;
	case 9:
		return (float)0.9815;
	case 10:
		return (float)1.0255;
	case 11:
		return (float)1.0695;
	case 12:
		return (float)1.0950;
	case 13:
		return (float)1.1575;
	case 14:
		return (float)1.2015;
	default:
		return 0;
	}
	return 0;
}



static int Mepg2FrameRateNomi_( unsigned char code )
{
	switch (code & 0x0f) {
	case 1: return 24000;
	case 2: return 24;
	case 3: return 25;
	case 4: return 30000;
	case 5: return 30;
	case 6: return 50;
	case 7: return 60000;
	case 8: return 60;
	default: return 0;
	}
	return 0;
}

static int Mepg2FrameRateDeno_( unsigned char code )
{
	switch (code & 0x0f) {
	case 1: return 1001;
	case 2: return 1;
	case 3: return 1;
	case 4: return 1001;
	case 5: return 1;
	case 6: return 1;
	case 7: return 1001;
	case 8: return 1;
	default: return 1;
	}
	return 1;
}
static bool UnpackMPEG2SeqHdr( AV_CONTEXT* av, const unsigned char *pbData )
{
	unsigned long dwWidthAndHeight;
	unsigned char PelAspectRatioAndPictureRate;

	/*	Check random marker	bit	*/
	if ( !(	pbData[10] & 0x20 )	) 
		return false;

	memset(	&av->Mpeg2Hdr, 0, sizeof( av->Mpeg2Hdr ) );	
	
	dwWidthAndHeight = ((unsigned	long)pbData[4] << 16) +	
									 ((unsigned	long)pbData[5] << 8) +
									 ((unsigned	long)pbData[6]);

	av->Mpeg2Hdr.SeqHdr.Width  = dwWidthAndHeight >> 12;
	av->Mpeg2Hdr.SeqHdr.Height = dwWidthAndHeight &	0xFFF;

	/* the '8' bit is the scramble flag	used by	sigma designs -	ignore */
	PelAspectRatioAndPictureRate = pbData[7];	
	if ((PelAspectRatioAndPictureRate &	0x0F) >	8)	
		PelAspectRatioAndPictureRate &=	0xF7;

	av->Mpeg2Hdr.SeqHdr.arInfo = PelAspectRatioAndPictureRate >> 4;	

	if ( (PelAspectRatioAndPictureRate & 0xF0) == 0	||
		 (PelAspectRatioAndPictureRate & 0x0F) == 0) 
	{
		return false;
	}

	//av->Mpeg2Hdr.SeqHdr.FrameRateCode = PelAspectRatioAndPictureRate;
	av->Mpeg2Hdr.SeqHdr.FrameRateNomi = Mepg2FrameRateNomi_(PelAspectRatioAndPictureRate);
	av->Mpeg2Hdr.SeqHdr.FrameRateDeno = Mepg2FrameRateDeno_(PelAspectRatioAndPictureRate);

	av->Mpeg2Hdr.SeqHdr.tPictureTime = (LONGLONG)PictureTimes[PelAspectRatioAndPictureRate & 0x0F];	
	av->Mpeg2Hdr.SeqHdr.PictureRate	= PictureRates[PelAspectRatioAndPictureRate	& 0x0F];

	av->Mpeg2Hdr.SeqHdr.TimePerFrame = (long)av->Mpeg2Hdr.SeqHdr.tPictureTime * 9/1000;	

	/*	Pull out the bit rate and aspect ratio for the type	*/
	av->Mpeg2Hdr.SeqHdr.BitRate	= ((((unsigned long)pbData[8]	<< 16) +
								 ((unsigned	long)pbData[9]	<<	8) +
								 (unsigned long)pbData[10])	>> 6 );		
	if (av->Mpeg2Hdr.SeqHdr.BitRate	== 0x3FFFF)	{
		av->Mpeg2Hdr.SeqHdr.BitRate	 = 0;
	} else {
		av->Mpeg2Hdr.SeqHdr.BitRate	*= 400;	
	}

	/*	Pull out the vbv */	
	av->Mpeg2Hdr.SeqHdr.vbv	= ((((long)pbData[10] &	0x1F) << 5)	| ((long)pbData[11]	>> 3)) * 16	* 1024;	

	av->Mpeg2Hdr.SeqHdr.ActualHeaderLen	= MPEG2SequenceHeaderSize(pbData);
	if ( (unsigned int)av->Mpeg2Hdr.SeqHdr.ActualHeaderLen > sizeof(av->Mpeg2Hdr.SeqHdr.RawHeader) )
		return false;

	memcpy((void*)av->Mpeg2Hdr.SeqHdr.RawHeader, (void*)pbData,	av->Mpeg2Hdr.SeqHdr.ActualHeaderLen	);

	return true;
}


static bool UnpackMPEG2ExtHdr( AV_CONTEXT* av, const unsigned char *pbData )
{
	 //	 check the extension id	
	if ( (pbData[4]	>> 4) != 0x01 )	
		return false;

	av->Mpeg2Hdr.SeqHdr.Profile	 = pbData[4] & 0x07;
	av->Mpeg2Hdr.SeqHdr.Level	 = pbData[5] >>	4;
	av->Mpeg2Hdr.SeqHdr.Width	+= ((pbData[5] & 1)	<< 13) + ((pbData[6] & 0x80) <<	5);	
	av->Mpeg2Hdr.SeqHdr.Height	+= (pbData[6] &	0x60) << 7;	
	av->Mpeg2Hdr.SeqHdr.BitRate	+= 400 * (((pbData[6] &	0x1F) << (18 + 7)) +
							 ((pbData[7] & 0xFE) <<	(18	- 1)));	
	av->Mpeg2Hdr.SeqHdr.progressive	= (pbData[5] & 0x8)	>> 3;

	return true;
}


static bool UnpackDixVType( AV_CONTEXT* av, const unsigned char* pData, int Size	)
{
	const unsigned char* ptr;
	_MEDIA_DATA* pMediaData;
	int size;

	ptr = pData - av->MagicLen + 4;  
	pMediaData = (_MEDIA_DATA*)ptr; 
	av->MsftMedia.Media = *pMediaData;

	assert( pMediaData->cbFormat < sizeof(_VIDEOINFOHEADER) );
	size = _MIN( pMediaData->cbFormat, sizeof(_VIDEOINFOHEADER) );

	memcpy( (char*)&av->MsftMedia.Video, ptr+sizeof(av->MsftMedia.Media), size );
	
	av->VideoType =	MSFT_MEDIA;
	av->SubId = 0;
	return true;
}


static bool UnpackMpegVideoHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size )
{
	//Video information always is in header of PES, start indictates start of a PES 	
	const unsigned char* data;
	const unsigned char* start;
	int size;
	bool ret = false;


	if ( av->PacketBufLen )
	{
		int len;
		len = sizeof( av->PacketBuf ) - av->PacketBufLen;
		len = min( len, Size );
		memcpy( av->PacketBuf+ av->PacketBufLen, pStart, len );
		Size   = len + av->PacketBufLen;
		pStart = av->PacketBuf;
	}

	data = pStart;
	size = Size;

	ret	= SearchMPEG2StartCode(	data, size, SEQUENCE_HEADER_CODE,  &start );
	if ( ret )
		ret	= UnpackMPEG2SeqHdr( av, (const	unsigned char*)start );	

	if ( !ret )	
	{
		av->PacketBufLen = 0;
		return false;
	}

	//search and parse mpeg2 extension header
	if ( Size > (start - pStart) + av->Mpeg2Hdr.SeqHdr.ActualHeaderLen-10 )
	{
		data = (const unsigned	char*)start + av->Mpeg2Hdr.SeqHdr.ActualHeaderLen-10;
		size = Size - (start - pStart) - (av->Mpeg2Hdr.SeqHdr.ActualHeaderLen-10);

		if ( size >= 7 )
		{
			ret	= SearchMPEG2StartCode(	data, size, EXTENSION_START_CODE,	&start );
			//MPEG-1 desn't	have extension part	
			if ( ret )
			{
				ret	= UnpackMPEG2ExtHdr( av, (const	unsigned char*)start );	
				av->SubId = 0;
				if ( ret )
					av->VideoType = MPEG2_VIDEO;	
				else
					av->VideoType = MPEG1_VIDEO;
				ret = true;
			} else
			{
				av->VideoType = MPEG1_VIDEO;
				ret = true;
			}
			av->PacketBufLen = 0;
		} else
		{
			int len = Size - ( start - pStart );
			if ( ( len <= sizeof( av->PacketBuf ) ) && av->PacketBufLen == 0 )
			{
				av->PacketBufLen = len;
				memcpy( av->PacketBuf, start, len );
				ret = false;
			}
		}
	} else
	{
		int len = Size - ( start - pStart );
		if ( ( len <= sizeof( av->PacketBuf ) ) && av->PacketBufLen == 0 )
		{
			av->PacketBufLen = len;
			memcpy( av->PacketBuf, start, len );
			ret = false;
		}
	}
	
	return ret;	
}


static int UnpackATSCUserData( MPEG_USER_DATA* ud, const unsigned char* pStart, int Size );
static int UnpackAFDUserData( MPEG_USER_DATA* ud, const unsigned char* pStart, int Size );
static bool UnpackMpegUserDataHeader( MPEG_USER_DATA* ud, const unsigned char* pStart, int Size	)
{
	//Video information always is in header of PES, start indictates start of a PES 	
	const unsigned char* data;
	const unsigned char* start;
	int  size, bytes;
	unsigned long code;
	bool ret;

	data = pStart;
	size = Size;

	ud->DataType = 0;
	ud->afd_format = 0;
	ud->CC_data_num = 0;
	ud->bar_data_num = 0;
	while ( size > 0 )
	{
		ret	= SearchMPEG2StartCode(	data, size, USER_DATA_START_CODE,  &start );
		if ( !ret )
			break; 
		
		data = start + 4;
		size = Size - ( data - pStart );
		if ( size <= 4 ) break;
	
		code = DWORD_SWAP(*(unsigned long *)data);
		if ( code == ATSC_IDENTIFIER )
		{
			bytes = UnpackATSCUserData( ud, data+4, size-4 );
		} else
		if ( code == AFD_IDENTIFIER )
		{
			bytes = UnpackAFDUserData( ud, data+4, size-4 );
		} else
			bytes = 0;
		
		data += bytes;
		if ( data >= pStart + Size ) break;
		size = Size - ( data - pStart );
	}
	
	return ud->DataType != 0;;

}

static int UnpackATSCUserData( MPEG_USER_DATA* ud, const unsigned char* pStart, int Size )
{
	unsigned char data_type;
	unsigned short cc_num;
	bool additional_data_flag, cc_flag; 
	bool top_bar, bottom_bar, left_bar, right_bar;
	unsigned char* data;
	int used_bytes = 0;
	if ( Size <= 1 )
		return 0;

	data_type = *pStart; 
	if ( data_type == 3 && Size > 4)
	{
		cc_flag = (pStart[1]&0x40) != 0; 
		additional_data_flag = (pStart[1]&0x20) != 0;  
		cc_num = (pStart[1]&0x1f);
		used_bytes+= 2 + cc_num*3 ;
		if ( cc_num * 3 < Size-3 && ud->CC_data_num < MAX_CC_DATA_NUM )
		{
			ud->DataType |= 0x01;
			ud->CC_data_byte[ud->CC_data_num] = cc_num * 3;
			ud->CC_data_ptr[ud->CC_data_num]  = (unsigned char*)pStart+3;
			ud->CC_data_num++;
			//check mark bits
			//if  ( pStart[3+3*cc_num] != 0xff )  printf("wrong" );
		}
		if ( additional_data_flag )
		{
			const unsigned char* start;
			if ( SearchMPEG2StartCode( pStart+used_bytes, Size-used_bytes, 0,  &start ) && *(start+4) == 1 )
			{
				used_bytes = start-pStart+5;
			}
		}
	} else
	if ( data_type == 6 && Size > 2)
	{
		ud->DataType |= 0x02;
		used_bytes++;
		top_bar = (pStart[1]&0x80) != 0;
		bottom_bar = (pStart[1]&0x40) != 0;
		left_bar = (pStart[1]&0x20) != 0;
		right_bar = (pStart[1]&0x10) != 0;;
		if ( ud->bar_data_num < MAX_CC_DATA_NUM && Size > 3 )
		{
			data =  (unsigned char*)pStart+2;
			if ( top_bar ){
				ud->BarData[ud->bar_data_num].top = 0x3f & *data;
				data++;
				used_bytes++;
			} else
				ud->BarData[ud->bar_data_num].top = -1;

			if ( bottom_bar ){
				ud->BarData[ud->bar_data_num].bottom = 0x3f & *data;
				data++;
				used_bytes++;
			} else
				ud->BarData[ud->bar_data_num].bottom = -1;

			if ( left_bar ){
				ud->BarData[ud->bar_data_num].left = 0x3f & *data;
				data++;
				used_bytes++;
			} else
				ud->BarData[ud->bar_data_num].left = -1;

			if ( right_bar ){
				ud->BarData[ud->bar_data_num].right = 0x3f & *data;
				data++;
				used_bytes++;
			} else
				ud->BarData[ud->bar_data_num].right = -1;

			ud->bar_data_num++;
		}
	}

	return used_bytes;
}

static int UnpackAFDUserData( MPEG_USER_DATA* ud, const unsigned char* pStart, int Size )
{
	if ( Size < 2 ) return 0;

	if ( ( *pStart & 0x80 ) != 0x0 || ( *pStart & 0x40 ) == 0  )
		return 0;

	ud->DataType |= 0x04;
	ud->afd_format = pStart[1] & 0x0f;
	return 2;
}

static bool UnpackMpegAudioHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size )
{
	//Video information always is in header of PES, start indictates start of a PES 	
	bool ret;
	
	ret	= UnpackMpegAudio( av, pStart, Size );

	return ret;	
}

static bool UnpackAC3AudioHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size	)
{
	bool ret = false;
	
	ret = UnpackAC3( av, pStart, Size );	

	return ret;	
}

static bool UnpackAACAudioHeader( AV_CONTEXT* av, bool bGoupStart, const unsigned char* pStart, int Size )
{
	bool ret = 0;
	
	if ( bGoupStart ) //ADTS header is following GoupStart
		ret	= UnpackAACAudioADTS( av, pStart, Size );

	if ( !ret ) 
		ret = UnpackAACAudioLATM( av, pStart, Size );

	return ret;	
}
////////////////////////////////////// DTS audio //////////////////////////////////////////////////////

#define DTS_HEADER_SIZE 14

static void LittleEndian( unsigned char *p_out, const unsigned char *p_in, int i_in )
{
    int i;
    for( i = 0; i < i_in/2; i++  )
    {
        p_out[i*2] = p_in[i*2+1];
        p_out[i*2+1] = p_in[i*2];
    }
}

static int LittleEndian14( unsigned char *p_out, const unsigned char *p_in, int i_in, int i_le )
{
    unsigned char tmp, cur = 0;
    int bits_in, bits_out = 0;
    int i, i_out = 0;

    for( i = 0; i < i_in; i++  )
    {
        if( i%2 )
        {
            tmp = p_in[i-i_le];
            bits_in = 8;
        }
        else
        {
            tmp = p_in[i+i_le] & 0x3F;
            bits_in = 8 - 2;
        }

        if( bits_out < 8 )
        {
            int need = _MIN( 8 - bits_out, bits_in );
            cur <<= need;
            cur |= ( tmp >> (bits_in - need) );
            tmp <<= (8 - bits_in + need);
            tmp >>= (8 - bits_in + need);
            bits_in -= need;
            bits_out += need;
        }

        if( bits_out == 8 )
        {
            p_out[i_out] = cur;
            cur = 0;
            bits_out = 0;
            i_out++;
        }

        bits_out += bits_in;
        cur <<= bits_in;
        cur |= tmp;
    }

    return i_out;
}

static const unsigned int dts_samplerate[] =
{
    0, 8000, 16000, 32000, 0, 0, 11025, 22050, 44100, 0, 0,
    12000, 24000, 48000, 96000, 192000
};

static const unsigned int dts_bitrate[] =
{
    32000, 56000, 64000, 96000, 112000, 128000,
    192000, 224000, 256000, 320000, 384000,
    448000, 512000, 576000, 640000, 768000,
    896000, 1024000, 1152000, 1280000, 1344000,
    1408000, 1411200, 1472000, 1536000, 1920000,
    2048000, 3072000, 3840000, 1/*open*/, 2/*variable*/, 3/*lossless*/
};

static int UnpackDTSInfo( const unsigned char *buf, AV_CONTEXT* av )
{
    unsigned int audio_mode;
    unsigned int sample_rate;
    unsigned int bit_rate;
    unsigned int frame_length;
	unsigned int pcm_resolution;
	unsigned int channels;

    unsigned int frame_size;
    unsigned int lfe;

    frame_length = ((buf[4] & 0x01) << 6) | (buf[5] >> 2);
    frame_size = ((buf[5] & 0x03) << 12) | (buf[6] << 4) | (buf[7] >> 4);
    audio_mode = ((buf[7] & 0x0f) << 2) | (buf[8] >> 6);
    sample_rate = (buf[8] >> 2) & 0x0f;
    bit_rate = ((buf[8] & 0x03) << 3) | ((buf[9] >> 5) & 0x07);
	pcm_resolution = ((buf[13] & 0x01)<<2) | ((buf[14] >> 6 ) & 0x03);

    if( sample_rate >= sizeof( dts_samplerate ) /sizeof( dts_samplerate[0] ) )
		return 0;

    sample_rate = dts_samplerate[ sample_rate ];
    if( sample_rate == 0 ) return 0;

    if( bit_rate >= sizeof( dts_bitrate ) / sizeof( dts_bitrate[0] ) )
        return 0;
    bit_rate = dts_bitrate[ bit_rate ];

    if( bit_rate == 0 ) return 0;

    lfe = ( buf[10] >> 1 ) & 0x03;
    if( lfe ) audio_mode |= 0x10000;

    switch( audio_mode & 0xFFFF )
    {
        case 0x0:      /* Mono */
			channels = 1;
            break;
        case 0x1:      /* Dual-mono = stereo + dual-mono */
			channels = 3;
            break;
        case 0x2:
        case 0x3:
        case 0x4:         /* Stereo */
            channels = 2;
            break;
        case 0x5:          /* 3F */
            channels = 3;
            break;
        case 0x6:          /* 2F/1R */
            channels = 3;
            break;
        case 0x7:         /* 3F/1R */
            channels = 4;
            break;
        case 0x8:         /* 2F2R */
            channels = 4;
            break;
        case 0x9:           /* 3F2R */
            channels = 5;
            break;
        case 0xA:
        case 0xB:          /* 2F2M2R */
            channels = 6;
            break;
        case 0xC:          /* 3F2M2R */
            channels = 7;
            break;
        case 0xD:
        case 0xE:          /* 3F2M2R/LFE */
            channels = 8;
            break;

        default:  
            if( audio_mode <= 63 )
            {
                /* User defined */
                channels = 0;
            }
            else return 0;
            break;
    }

    if( audio_mode & 0x10000 )
        channels++;
	
	av->DTSWav.wfx.nChannels = channels;
	av->DTSWav.wfx.nSamplesPerSec = sample_rate;
	av->DTSWav.wfx.nAvgBytesPerSec = bit_rate/8;
	av->DTSWav.wfx.nBlockAlign = 0; //pcm_resolution < 2 ? 2 : 3;
	av->DTSWav.wfx.wBitsPerSample = 16;
	av->AudioType = DTS_AUDIO;

    return 1;
}

static bool UnpackDTSHead( AV_CONTEXT* av, const unsigned char *pbData, int Size ) 
{
	int i, ret=0;
	const unsigned char* pData = pbData;
    unsigned char buf[DTS_HEADER_SIZE];

	if ( Size < DTS_HEADER_SIZE )
		return 0;

	for ( i = 0; i<Size-DTS_HEADER_SIZE; i++  )
	{
		/* 14 bits,Little endian version of the bitstream */
		if( pData[i+0] == 0xff && pData[i+1] == 0x1f &&
			pData[i+2] == 0x00 && pData[i+3] == 0xe8 &&
			(pData[i+4] & 0xf0) == 0xf0 && pData[i+5] == 0x07 )
		{
			LittleEndian14( buf, pData+i, DTS_HEADER_SIZE, 1 );
			ret = UnpackDTSInfo( buf, av );
		}
		/* 14 bits, Big Endian version of the bitstream */
		else if( pData[i+0] == 0x1f && pData[i+1] == 0xff &&
				 pData[i+2] == 0xe8 && pData[i+3] == 0x00 &&
				 pData[i+4] == 0x07 && (pData[i+5] & 0xf0) == 0xf0 )
		{
			LittleEndian14( buf, pData+i, DTS_HEADER_SIZE, 0 );
			ret = UnpackDTSInfo( buf, av );
	        
		}
		/* 16 bits, Big Endian version of the bitstream */
		else if( pData[i+0] == 0x7f && pData[i+1] == 0xfe &&
				 pData[i+2] == 0x80 && pData[i+3] == 0x01 && (pData[i+4] & 0xfc) == 0xfc )
		{
			ret = UnpackDTSInfo( pData+i, av );
		}
		/* 16 bits, Little Endian version of the bitstream */
		else if( pData[i+0] == 0xfe && pData[i+1] == 0x7f &&
				 pData[i+2] == 0x01 && pData[i+3] == 0x80 )
		{
			LittleEndian( buf, pData+i, DTS_HEADER_SIZE );
			ret = UnpackDTSInfo( buf, av );
		}

		if ( ret ) break;
	}

   return ret;
}

////////////////////////////////////// MPEG4 Divx (ASF) ///////////////////////////////////////////////
//MPEG4 ASP ( DivX format )
static bool UnpackMPEG4VOL( AV_CONTEXT* av, const unsigned char *pbData, int size )
{
	const unsigned char* p;
	int aspect_ratio_info;
	int bits_offset, total_bits;
	int video_object_type_indication;
	int video_object_layer_verid = 0;
	int video_object_layer_shape;
	int vop_time_increment_resolution;
	if ( size <= 8 ) return false;


	p = pbData + 4;
	bits_offset = 0;
	total_bits = ( size - 4 )*8;

	skip_bits( &bits_offset, &total_bits, 1  ); //random_accessible_vol;
	video_object_type_indication = read_u( p, &bits_offset, &total_bits, 8 );
	if ( video_object_type_indication == 0x12 ) //Fine Granularity Scalable
	{
		//don't support Fine Granularity Scalable
		return false;
	}
	
	if ( read_u( p, &bits_offset, &total_bits, 1 ) )//is_object_layer_identifier
	{
		video_object_layer_verid = read_u( p, &bits_offset, &total_bits, 4 ); //video_object_layer_verid;
		skip_bits( &bits_offset, &total_bits, 3 );//video_object_layer_priority
	}
	
	aspect_ratio_info = read_u( p, &bits_offset, &total_bits, 4 );
	if ( aspect_ratio_info == 0 )
		return false; //forbidden
	if ( aspect_ratio_info == 1 )
		av->Mpeg2Hdr.SeqHdr.arInfo = 1; else
	if ( aspect_ratio_info == 2 )
		av->Mpeg2Hdr.SeqHdr.arInfo = 5; else
	if ( aspect_ratio_info == 3 )
		av->Mpeg2Hdr.SeqHdr.arInfo = 6; else
	if ( aspect_ratio_info == 4 )
		av->Mpeg2Hdr.SeqHdr.arInfo = 7; else
	if ( aspect_ratio_info == 15 )
	{
		unsigned char par_width  = read_u( p, &bits_offset, &total_bits, 8 );
		unsigned char par_height = read_u( p, &bits_offset, &total_bits, 8 );
		av->Mpeg2Hdr.SeqHdr.arInfo = 0xff | ( par_width << 8 ) | ( par_height << 16 ) ;
	}

	if ( read_u( p, &bits_offset, &total_bits, 1 ) ) //vol_control_parameters 
	{
		skip_bits( &bits_offset, &total_bits, 2 ); //chroma_format
		skip_bits( &bits_offset, &total_bits, 1 ); //low_delay
		if ( read_u( p, &bits_offset, &total_bits, 1 ) )//vbv_parameters
		{
			skip_bits( &bits_offset, &total_bits, 79 );
		}
	}

	video_object_layer_shape = read_u( p, &bits_offset, &total_bits, 2 );
	if ( video_object_layer_shape == 3 && video_object_layer_verid != 01 )
	{
		skip_bits( &bits_offset, &total_bits, 4 ); //video_object_layer_shape_extension
	}

	if ( read_u( p, &bits_offset, &total_bits, 1 ) != 1 ) //market bit
		return false;

	vop_time_increment_resolution = read_u( p, &bits_offset, &total_bits, 16 );//vop_time_increment_resolution;
	av->Mpeg2Hdr.SeqHdr.PictureRate = (float) vop_time_increment_resolution;

	if ( read_u( p, &bits_offset, &total_bits, 1 ) != 1 ) //market bit
		return false;

	if ( read_u( p, &bits_offset, &total_bits, 1 )  ) //fixed_vop_rate 
	{
		unsigned short fixed_vop_time_increment;
		int fixed_vop_time_increment_bits = 0;
		int i;
		for ( i = vop_time_increment_resolution; i>0; i /= 2 ) 
			fixed_vop_time_increment_bits++;

		fixed_vop_time_increment = read_u( p, &bits_offset, &total_bits, fixed_vop_time_increment_bits );
		av->Mpeg2Hdr.SeqHdr.PictureRate /= fixed_vop_time_increment ;
	}

	if ( read_u( p, &bits_offset, &total_bits, 1 ) != 1 ) //market bit
		return false;

	av->Mpeg2Hdr.SeqHdr.Width = read_u( p, &bits_offset, &total_bits, 13 );
	 

	if ( read_u( p, &bits_offset, &total_bits, 1 ) != 1 ) //market bit
		return false;

	av->Mpeg2Hdr.SeqHdr.Height  = read_u( p, &bits_offset, &total_bits, 13 );

	if ( read_u( p, &bits_offset, &total_bits, 1 ) != 1 ) //market bit
		return false;

	av->Mpeg2Hdr.SeqHdr.progressive = read_u( p, &bits_offset, &total_bits, 1 ); // interlaced

	//skip reset of data...


	return true;
}

static bool UnpackMpeg4VideoHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size )
{
	//Video information always is in header of PES, start indictates start of a PES 	
	const unsigned char* data;
	const unsigned char* start;
	int size;
	const unsigned char *visual_object_seq_start;

	bool ret = false;

	data = pStart;
	size = Size;

	ret	= SearchMPEG2StartCode(	data, size, VISUAL_OBJECT_SEQUENCE_START_CODE,  &start );
	if ( !ret )	
	{
		return false;
	}
	visual_object_seq_start = start;
	if ( Size > (start - pStart) + 6 )
	{
		data = start;
		size = Size - (start - pStart);
		ret	= SearchMPEG2StartCode(	data, size, VISUAL_OBJECT_START_CODE,  &start );
		if ( !ret )	
		{
			return false;
		}
		if ( Size > (start - pStart) + 6 )
		{
			const unsigned char *p;
			data = start;
			size = Size - (start - pStart);
			ret = SearchMPEG4VOLCode( data, size,  &start );
			if ( !ret )	
			{
				return false;
			}
			data = start;
			size = Size - (start - pStart);
			if ( !SearchMPEG2StartCode(	data, size, VOP_START_CODE,  &p ) && 
				 !SearchMPEG2StartCode(	data, size, GROUP_OF_VOP_START_CODE,  &p ) )
			{
				return false;
			}

			size = Size - (start - pStart);
			ret	= UnpackMPEG4VOL( av, (const unsigned char*)start, size );	
			av->SubId = 0;
			if ( ret )
			{
				av->VideoType = DIVX_VIDEO;	
				ret = true;
			}

		} 
	} 
	return ret;	
}

static const int vc1_fps_nr[5] = { 24, 25, 30, 50, 60 };
static const int vc1_fps_dr[2] = { 1000, 1001 };

static bool UnpackMPEGVC1SEQHeader( AV_CONTEXT* av, const unsigned char *pbData, int size )
{
	const unsigned char* p;
	int bits_offset, total_bits;
	unsigned int profile, level, chromaformat, interlace;
	unsigned int coded_width=0, coded_height=0;  
	unsigned int display_width=0, display_height=0;  
	
	if ( size <= 8 ) return false;

	p = pbData;
	bits_offset = 0;
	total_bits = ( size - 4 )*8;

	profile = read_u( p, &bits_offset, &total_bits, 2 );	
	if ( profile == 2 ) //PROFILE_COMPLEX
		return false; //I don't know WMV9 profile
	else
	if ( profile == 3 ) //PROFILE_ADVANCED
	{
		unsigned int w=0, h=0, ar=0;
		//ADVANCED pofile
		level = read_u( p, &bits_offset, &total_bits, 3 );
		if ( level >= 5 )
			return false; //reserved level
		chromaformat = read_u( p, &bits_offset, &total_bits, 2 );
		if ( chromaformat != 1 )
			return false; //not 4:2:0 chroma format
		av->Mpeg2Hdr.SeqHdr.Profile = profile;
		av->Mpeg2Hdr.SeqHdr.Level = level;
		skip_bits( &bits_offset, &total_bits, 3+5+1 ); //frmrtq_postproc(3bits) bitrtq_postproc(5bits) postprocflag(1bits)
		coded_width = read_u( p, &bits_offset, &total_bits, 12 );
		coded_height = read_u( p, &bits_offset, &total_bits, 12 );

		coded_width  = (coded_width+1)*2;
		coded_height = (coded_height+1)*2;

		display_width = coded_width;
		display_height = coded_height;  

		skip_bits( &bits_offset, &total_bits, 1 ); // broadcast
		interlace =  read_u( p, &bits_offset, &total_bits, 1 );

		skip_bits( &bits_offset, &total_bits, 1+1 ); //tfcntrflag(1bits) finterpflag(1bits)
		skip_bits( &bits_offset, &total_bits, 1 );   //reserved bit
		skip_bits( &bits_offset, &total_bits, 1 );   //psf

		if ( read_u( p, &bits_offset, &total_bits, 1 ) ) //display_info
		{
			display_width = read_u( p, &bits_offset, &total_bits, 14 )+1;
			display_height = read_u( p, &bits_offset, &total_bits, 14 )+1;
		

			if ( read_u( p, &bits_offset, &total_bits, 1 ) ) //ar control
			{
				ar = read_u( p, &bits_offset, &total_bits, 4 );

				if ( ar == 0 )
					return false;

				if ( ar == 15 )
				{
					w = read_u( p, &bits_offset, &total_bits, 8 );
					h = read_u( p, &bits_offset, &total_bits, 8 );
				} 

				av->Mpeg2Hdr.SeqHdr.arInfo = ar | (w<<8) | (h<<16);
			}

			if ( read_u( p, &bits_offset, &total_bits, 1 ) ) //framerate control
			{
				unsigned int frame_rate = 0;
				unsigned int dr = 0, nr = 0;
				if ( read_u( p, &bits_offset, &total_bits, 1 ) ) //defined frame_rate
				{
					frame_rate = read_u( p, &bits_offset, &total_bits, 16 )+1;
					av->Mpeg2Hdr.SeqHdr.FrameRateNomi = Mepg2FrameRateNomi_(frame_rate);
					av->Mpeg2Hdr.SeqHdr.FrameRateDeno = Mepg2FrameRateDeno_(frame_rate);
				}
				else
				{
					nr = read_u( p, &bits_offset, &total_bits, 8 );
					dr = read_u( p, &bits_offset, &total_bits, 4 );
					if(nr && nr < 8 && dr && dr < 3)
					{
						nr = vc1_fps_dr[nr - 1];
						dr = vc1_fps_nr[dr - 1];
					}
				}
				//av->Mpeg2Hdr.SeqHdr.FrameRateCode = frame_rate;// | ( nr << 8 ) | ( dr << 16 );
				av->Mpeg2Hdr.SeqHdr.FrameRateNomi = nr;
				av->Mpeg2Hdr.SeqHdr.FrameRateDeno = dr;
			}

		}
		av->Mpeg2Hdr.SeqHdr.Width = display_width;
	    av->Mpeg2Hdr.SeqHdr.Height = coded_height;
	} else
	{ //sample, main profile

		if ( read_u( p, &bits_offset, &total_bits, 2 ) ) //forbidden 
			return false;
	
		return false; //ZQ I don't know how to get frame rate, ar, etc yet.

	}

	av->VideoType = VC1_VIDEO;	
	return true;
}

static bool UnpackMpegVC1VideoHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size )
{
	//Video information always is in header of PES, start indictates start of a PES 	
	const unsigned char* data;
	const unsigned char* start;
	int size;
	const unsigned char *seq_start;

	bool ret = false;

	data = pStart;
	size = Size;

	ret	= SearchMPEG2StartCode(	data, size, VC1_CODE_SEQHDR,  &start );
	if ( !ret )	
	{
		return false;
	}
	seq_start = start+4;
	if ( Size > (start - pStart) + 12 )
	{
		data = start;
		size = Size - (start - pStart);
		ret	= SearchMPEG2StartCode(	data, size, VC1_CODE_ENTRYPOINT,  &start );
		if ( !ret )
		{
			ret	= SearchMPEG2StartCode(	data, size, VC1_CODE_FRAME,  &start );
			if ( !ret )
			{
				ret	= SearchMPEG2StartCode(	data, size, VC1_CODE_ENDOFSEQ,  &start );
				if ( !ret )
					return false;
			}
		}

		size = Size - (seq_start - pStart);
		ret = UnpackMPEGVC1SEQHeader( av, seq_start, size );
		if ( ret )
		{
			av->VideoType = VC1_VIDEO;	
			ret = true;
		}
	} 

	return ret;	
}



//it's SageTV proprietary way to pack DixV for plaxtor 
static bool UnpackDixVTypeHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size )
{
	bool ret = false;
	
	ret = UnpackDixVType( av, pStart, Size );	

	return ret;	
}

static bool UnpackLPCMHeader( AV_CONTEXT* av, const unsigned char* pStart, int Size	)
{
	bool ret = false;
	
	ret = UnpackLPCM(  av, pStart, Size );

	return ret;	

}

bool UnpackLPCM( AV_CONTEXT* av, const unsigned char * pbData, int Size	)
{
	unsigned int ulNumFrames;
    unsigned int ulFrameStartOffset;
    unsigned int ulAudioEmphasisFlag;
    unsigned int ulAudioMuteFlag;
    unsigned int ulAudioFrameNumber;
    unsigned int ulBpsSelector;
    unsigned int ulBitsPerSample;
    unsigned int ulSampleRateSelector;
    unsigned int ulSampleRate;
    unsigned int ulNumChannels;
    unsigned int ulDyamicRangeControl;
    unsigned int ulBitRate;

	if (pbData[5] != 0x01 || pbData[6] != 0x80)	
		return false;
	
	ulNumFrames = pbData[1];
	ulFrameStartOffset  = ((pbData[2] << 8) | pbData[3]) - 4;
	ulAudioEmphasisFlag = (pbData[4] & 0x80) >> 7;
	ulAudioMuteFlag     = (pbData[4] & 0x40) >> 6;
	ulAudioFrameNumber  =  pbData[4] & 0x1f;
	ulBpsSelector       = (pbData[5] & 0xC0) >> 6;
	ulBitsPerSample     = 0;
	ulSampleRateSelector= (pbData[5] & 0x30) >> 4;
	ulSampleRate        = 0;
	ulNumChannels       = (pbData[5] & 0x07) + 1;
	ulDyamicRangeControl = pbData[6];
	ulBitRate = ulSampleRate * ulBitsPerSample * ulNumChannels;

	if ( ulNumChannels == 0 )
		return false;

	switch (ulBpsSelector)
    {
        case 0: ulBitsPerSample = 16; break;
        case 1: ulBitsPerSample = 20; break;
        case 2: ulBitsPerSample = 24; break;
		default:   return false;
    }
    switch (ulSampleRateSelector)
    {
        case 0: ulSampleRate = 48000; break;
        case 1: ulSampleRate = 96000; break;
		default:   return false;
    }

	av->LPCMWav.wfx.cbSize =	sizeof(av->AC3Wav.wfx);
	av->LPCMWav.wfx.wFormatTag =	0; //WAVE_FORMAT_UNKNOWN;
	av->LPCMWav.wfx.nSamplesPerSec =	ulSampleRate;
	av->LPCMWav.wfx.nChannels = ulNumChannels;;
	av->LPCMWav.wfx.nAvgBytesPerSec = ulBitRate/8;
	av->LPCMWav.wfx.nBlockAlign = ulBitsPerSample/8 + ulBitsPerSample%8 ? 1:0;	
	av->LPCMWav.wfx.wBitsPerSample =	ulBitsPerSample;	

	av->AudioType =	LPCM_AUDIO;

	if ( ( pbData[0] & 0xF8 ) == 0x80 ) 
		av->SubId = pbData[0];

	return true;
}


#define A52_DOLBY 10
#define A52_LFE   16
int AC3SyncInfo ( unsigned char* buf, int * sample_rate, int * bit_rate, int *channels )
{
    static int rate_tab[] = { 32,  40,  48,  56,  64,  80,  96, 112,
			 128, 160, 192, 224, 256, 320, 384, 448, 512, 576, 640};
    static unsigned char lfeon[8] = {0x10, 0x10, 0x04, 0x04, 0x04, 0x01, 0x04, 0x01};
    static unsigned char halfrate[12] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3};
    static int channels_num[8] = { 2, 1, 2, 3, 3, 4, 4, 5 };

    int flags;
    int frmsizecod;
    int bitrate;
    int half;
    int acmod;
    bool sync_code;

	/* syncword */
     sync_code = ( ( buf[0] == 0x0b && buf[1] == 0x77 ) || ( buf[0] == 0x77 && buf[1] == 0x0b ) );
	 if ( !sync_code )
		return 0;

    if (buf[5] >= 0x60)		/* bsid >= 12 */
		return 0;

    half = halfrate[buf[5] >> 3];

    /* acmod, dsurmod and lfeon */
    acmod = buf[6] >> 5;
    flags = ((((buf[6] & 0xf8) == 0x50) ? A52_DOLBY : acmod) |
	      ((buf[6] & lfeon[acmod]) ? A52_LFE : 0));

    frmsizecod = buf[4] & 63;
    if (frmsizecod >= 38)
		return 0;
    bitrate = rate_tab[frmsizecod >> 1];
    *bit_rate = (bitrate * 1000) >> half;

    *channels = channels_num[ flags & 7 ];
     if ( flags & A52_LFE )
	(*channels)++;

    switch (buf[4] & 0xc0) {
    case 0:	/* 48 KHz */
		*sample_rate = 48000 >> half;
		return 1;  
    case 0x40:	  /*44.1 KHZ */
		*sample_rate = 44100 >> half;
		return 1;  
    case 0x80:	  /* 32 KHZ */
		*sample_rate = 32000 >> half;
		return 1;  
    default:
		return 0;
    }
}


/*	parse ac3 stream */	
static bool UnpackAC3( AV_CONTEXT* av, const unsigned char *pbData, int size )
{

	const unsigned char *p;
	int i;
	int sample_rate, bit_rate, channels;
	p = pbData;

	//search for SYNC code 
	for ( i = 0; i<size; i++ )
	{
		if ( ( p[i] == 0x0B && p[i+1] == 0x77 ) ||
			 ( p[i] == 0x77 && p[i+1] == 0x0B ) )
			 break;
	}

	if ( i == size )
		return false;

	//check	AC3Wav mark, and Code format
	if ( !( p[i] == 0x0B && p[i+1] == 0x77) && 
		  ( p[i] == 0x77 && p[i+1] == 0x0B ) ) 
	{
		return false;
	}

	if ( !AC3SyncInfo ( (unsigned char*)p+i, &sample_rate, &bit_rate, &channels ) )
		return false;

	memset(	(void*)&av->AC3Wav,	0, sizeof(av->AC3Wav));	

	av->AC3Wav.wfx.cbSize =	sizeof(TS_AC3WAV) -	sizeof(_WAVEFORMATEX);
	av->AC3Wav.wfx.wFormatTag =	WAVE_FORMAT_DOLBY_AC3;
	av->AC3Wav.wfx.nSamplesPerSec = sample_rate;
	av->AC3Wav.wfx.nAvgBytesPerSec =  bit_rate /8;
	av->AC3Wav.wfx.nChannels = channels;
	av->AC3Wav.wfx.nBlockAlign = (unsigned short)(((long)av->AC3Wav.wfx.nAvgBytesPerSec * 1536) / av->AC3Wav.wfx.nSamplesPerSec);
	av->AudioType =	AC3_AUDIO;

	//check if it's DVD AC3, get its SubID
	if ( ( pbData[0] & 0xF8 ) == 0x80 ) 
		av->SubId = pbData[0];

	return true;

}


/*  Bit rate tables */
const int MpegBitRateTbl[2][3][16]	=
{
	{	// MPEG 1	
		{	0, 32,	64,	 96,  128, 160,	192, 224, 256, 288,	320, 352, 384, 416,	448, 0 },
		{	0, 32,	48,	 56,   64,	80,	 96, 112, 128, 160,	192, 224, 256, 320,	384, 0 },
		{	0, 32,	40,	 48,   56,	64,	 80,  96, 112, 128,	160, 192, 224, 256,	320, 0 }
	},
	{	// MPEG 2 ( 2.5)		
		{	0, 32,	48,	56,	64,	80,	96,	112,	128,	144,	160,	176,  192,  224,  256,},// Layer1
		{	0,	8,	16,	24,	32,	40,	48,	56,		64,		80,		96,		112,  128,  144,  160,},// Layer2
		{	0,	8,	16,	24,	32,	40,	48,	56,		64,		80,		96,		112,  128,  144,  160,}	// Layer3
	}
};

const int SamplingRateTbl[2][3] = 
{ 
	{44100, 48000, 32000  },	// MPEG 1
	{22050, 24000, 16000, }		// MPEG 2
};

static bool UnpackMpegAudio( AV_CONTEXT* av, const unsigned char * pbData, int Size	)
{
	int Layer,i,MPGVersion, CRC_protected, BiteRateIndex, SampleRateIndex;
	unsigned char LayerCode;
	const unsigned char *pData;
	unsigned long Bitrate;


	pData = pbData;

	//search for SYNC bits (12bits of 1) of Mpeg 4 bytes audio header
	//11 bits 1 if we supports MPEG-2.5 , I will add it suport in later versuin  ZQ. 
	for ( i = 0; i<Size; i++, pData++ ) 
		if ( *pData == 0xff && ( *(pData+1) & 0xf0 ) == 0xf0 && ( *(pData+2) & 0xf0 ) != 0xf0  )
			break;
	if ( i>Size-4 )
		return false;

	//verify if it's a vaild header
	if (((*(pData+2)	>> 2) &	3) == 3)	//Invalid sample rate
		return false;
	if ((*(pData+2) >> 4) ==	0x0F)		//Invalid bit rate
		return false;
	if ((*(pData+2) & 0x0C) == 0x0C)		//Invalid audio	sampling frequency
		return false;

	MPGVersion = ((*(pData+1) >> 3) &0x03);
	if ( MPGVersion == 0 || MPGVersion == 1 )
		return false;
	if ( MPGVersion == 0x02 ) MPGVersion = 2; //MPEG1
	if ( MPGVersion == 0x03 ) MPGVersion = 1; //MPEG2

	memset(	&av->MpegWav, 0, sizeof(av->MpegWav) );	
	av->MpegWav.wfx.wFormatTag = WAVE_FORMAT_MPEG;

	/*	Get	the	layer so we	can	work out the bit rate */
	LayerCode	= ((*(pData+1) >> 1) & 0x03);	
	switch ( LayerCode ) {
		case 3:	
			av->MpegWav.fwHeadLayer	= ACM_MPEG_LAYER1;
			Layer =	1;
			break;
		case 2:	
			av->MpegWav.fwHeadLayer	= ACM_MPEG_LAYER2;
			Layer =	2;
			break;
		case 1:	
			av->MpegWav.fwHeadLayer	= ACM_MPEG_LAYER3;
			Layer =	3;
			break;
		case 0:	
			return false;
		default:
			return false;
	}

	CRC_protected = (*(pData+1) & 0x1 );

		/*	Get	samples	per	second from	sampling frequency */
	SampleRateIndex = (*(pData+2) >> 2)	& 3;
	if ( SampleRateIndex	== 3 ) return false;
	av->MpegWav.wfx.nSamplesPerSec	= SamplingRateTbl[MPGVersion-1][SampleRateIndex];
	
	BiteRateIndex = ((*(pData+2)	>> 4 ) & 0x0f);
	if ( BiteRateIndex == 0x0f ) return false;
	Bitrate = (unsigned long )MpegBitRateTbl[MPGVersion-1][Layer - 1][BiteRateIndex] * 1000;
	if ( av->MpegWav.wfx.nSamplesPerSec	!= 44100 &&	
		/*	Layer 3	can	sometimes switch bitrates */
		!(Layer	== 3 &&	/* !m_pStreamList->AudioLock() && */
			(*(pData+2) >> 4) ==	0))	{

		if (Layer == 1)	{
			av->MpegWav.wfx.nBlockAlign	= (short)(4 * ((Bitrate * 12) / av->MpegWav.wfx.nSamplesPerSec));
		} else {
			av->MpegWav.wfx.nBlockAlign	= (short)((144 * Bitrate) / av->MpegWav.wfx.nSamplesPerSec);
		}
	} else {
		av->MpegWav.wfx.nBlockAlign	= 1;
	}


	/*	Get	number of channels from	Mode */	
	switch (*(pData+3) >> 6)	{
	case 0x00:
		av->MpegWav.fwHeadMode = ACM_MPEG_STEREO;
		break;
	case 0x01:
		av->MpegWav.fwHeadMode = ACM_MPEG_JOINTSTEREO;
		break;
	case 0x02:
		av->MpegWav.fwHeadMode = ACM_MPEG_DUALCHANNEL;
		break;
	case 0x03:
		av->MpegWav.fwHeadMode = ACM_MPEG_SINGLECHANNEL;
		break;
	}
	av->MpegWav.wfx.nChannels =	
		(unsigned short)(av->MpegWav.fwHeadMode == ACM_MPEG_SINGLECHANNEL	? 1	: 2);
	av->MpegWav.fwHeadModeExt =	(unsigned short)(1 <<	(*(pData+3) >> 4));
	av->MpegWav.wHeadEmphasis =	(unsigned short)((*(pData+3) &	0x03) +	1);	
	av->MpegWav.fwHeadFlags	  =	(unsigned short)(((*(pData+2) & 1)	? ACM_MPEG_PRIVATEBIT :	0) +
						   ((*(pData+3) & 8)	? ACM_MPEG_COPYRIGHT : 0) +	
						   ((*(pData+3) & 4)	? ACM_MPEG_ORIGINALHOME	: 0) +
						   ((*(pData+1) & 1)	? ACM_MPEG_PROTECTIONBIT : 0) +	
						   ((*(pData+1) & 0x08) ? ACM_MPEG_ID_MPEG1 : 0));




	av->MpegWav.dwHeadBitrate =	Bitrate * av->MpegWav.wfx.nChannels;	
	av->MpegWav.wfx.nAvgBytesPerSec	= Bitrate/8; //av->MpegWav.dwHeadBitrate;

	av->MpegWav.wfx.wBitsPerSample = 0;	
	av->MpegWav.wfx.cbSize = sizeof(_WAVEFORMATEX)-2;	
	av->MpegWav.dwPTSLow  =	0;
	av->MpegWav.dwPTSHigh =	0;

	av->AudioType =	MPEG_AUDIO;   //I'm going to add detail descractption MPEG1 MPEG2 MPEG-2.5... later  ZQ 
	av->SubId = 0;

	return true;
}

static int  UnpackAACAudioPCEChannel( const unsigned char* pData, int Size );
const int AAC_sampling_Frequency[16] = {96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,0,0,0,0};
static bool UnpackAACAudioADTS( AV_CONTEXT* av,  const unsigned char * pbData, int Size	)
{
	int ID, layer, protection_absent,version;
	int profile_objectType, sampling_frequency_index, private_bit, channel_configuration, original_copy, home;
	int copyright_identification_bit, copyright_identification_start, aac_frame_length, adts_buffer_fullness;
	int number_of_raw_data_blocks_in_frame;
	int raw_data_pos[4];
	const unsigned char *pData;
	int i, ret;
	pData = pbData;

	if ( av->AACParam.state == 3 ) return true; //already knew foramat, skip parsing.
	if ( av->AACParam.state && av->AACParam.format != 1 ) return false; //not ADTS format, LOAS

	if ( Size > 8 )
	{
		/*
		//search for SYNC bits (12bits of 1) of sync world
		for ( i = 0; i<Size; i++, pData++ ) 
			if ( *pData == 0xff && ( *(pData+1) & 0xf0 ) == 0xf0  )
				break;
		if ( i>Size-4 )
			return false;
		*/
		//we don't search syncword,it's too dangerous for AAC, we assume ADTS syncword at start of a packet
		if ( !(*pData == 0xff && ( *(pData+1) & 0xf0 ) == 0xf0  ) )
		{
			av->AACParam.state = 0;
			av->AACParam.format = 0;
			return false;
		}

		//ADTS fixed header is fixed for all ADTS header
		if ( av->AACParam.state && (
				av->AACParam.u.atds_header[1] != pData[1] || av->AACParam.u.atds_header[2] != pData[2] || 
				(av->AACParam.u.atds_header[3]&0xf0) != (pData[3]&0xf0) ) )
		{
			av->AACParam.state = 0;
			av->AACParam.format = 0;
			return false;
		}

		//ID:1:MPEG2 AAC 13828-7; 0:MPEG4 AAC
		ID = (*(pData+1) >> 3)&0x01;							 //1 bits
		version = (ID == 1)?2:4;                                 //MPEG2 AAC or MPEG4 AAC
		layer = (*(pData+1) >> 1)&0x3;                           //2 bits
		protection_absent = (*(pData+1))&0x01;                   //1 bits

		//fixed header
		profile_objectType = (*(pData+2)>>6 ) & 0x03;			//2 bits
		sampling_frequency_index = (*(pData+2)>>2) & 0x0f;		//4 bits
		private_bit = (*(pData+2)>>1)&0x01;						//1 bits
		channel_configuration = (*(pData+2)&0x01)<<2;;	        //1 bits
		channel_configuration |= (*(pData+3)>>6)&0x03;          //2 bits
		original_copy = (*(pData+3)>>5)&0x01;			        //1 bits						
		home = (*(pData+3)>>4)&0x01;							//1 bits

		if ( sampling_frequency_index >= 12 ) return false;

		//variable header
		copyright_identification_bit = (*(pData+3)>>3)&0x01;;   //1 bits
		copyright_identification_start = (*(pData+3)>>2)&0x01;; //1 bits
		aac_frame_length =  (*(pData+3)&0x03)<<12;              // 2/13 bits
		aac_frame_length |=  (*(pData+4))<<3;                   // 8/13 bits
		aac_frame_length |=  (*(pData+5)>>5)&0x07;              // 3/13 bits
		adts_buffer_fullness = (*(pData+5)&0x1f)<<6;            // 5/11 bits 
		adts_buffer_fullness |= (*(pData+6)&0x3f)>>2;           // 6/11 bits 
		number_of_raw_data_blocks_in_frame = (*(pData+6)&0x3);  // 2 bits 
		//end of adts_fixed_header + adts_variable_header

		memcpy( av->AACParam.u.atds_header, pData, sizeof(av->AACParam.u.atds_header) );
		//we need more parse more to get channel number
		pData += 7;
		if ( channel_configuration == 0 )
		{
			int id_syn_ele;
			if ( number_of_raw_data_blocks_in_frame == 0 )
			{
				
				if ( protection_absent == 0 )
					pData += 2;
				
				id_syn_ele = ((*pData)>>5)&0x07;
				if ( id_syn_ele == 0x05 /*ID_PCE */ )
					ret = UnpackAACAudioPCEChannel( pData, Size-(pData-pbData) );
			} else
			{
				for ( i = 0; i<number_of_raw_data_blocks_in_frame; i++ )
				{
					raw_data_pos[i]=(pData[0]<<8)|pData[1];
					pData += 2;
				}
				if ( protection_absent == 0 )
					pData += 2;

				id_syn_ele = ((*pData)>>5)&0x07;
				if ( id_syn_ele == 5 /*ID_PCE */ )
					ret = UnpackAACAudioPCEChannel( pData, Size-(pData-pbData) );
			}
			channel_configuration = ret;
		}
			
		ret = ( av->AACParam.version == version ) && 
			  ( av->AACParam.profile == layer ) &&
			  ( av->AACParam.channel_num == channel_configuration ) &&
			  ( av->AACParam.sample_freq == AAC_sampling_Frequency[sampling_frequency_index] );

		av->AACParam.version = version;
		av->AACParam.profile = layer;
		av->AACParam.channel_num = channel_configuration;
		av->AACParam.frame_length = aac_frame_length;
		av->AACParam.sample_freq = AAC_sampling_Frequency[sampling_frequency_index];
		if ( av->AACParam.sample_freq == 0 ) return 0;
		if ( av->AACParam.frames )
			av->AACParam.bitrate = av->AACParam.sample_freq*aac_frame_length/1024;  //just guess

		if ( ret )   //it's not invalid header, if two header is inconsistance
		{
			av->AACParam.format = 1;
			av->AACParam.state = 3; ; //ADTS
			av->AudioType =	AAC_AUDIO;   //I'm going to add detail descractption MPEG1 MPEG2 MPEG-2.5... later  ZQ 
			av->SubId = 0;
			return true;
		}
	}
	return false;
}

int  UnpackAACAudioPCEChannel( const unsigned char* pData, int Size )
{
	unsigned short element_inst_tag, profile, sample_frq_index;
	int num_front_channel_elements, num_side_channel_elements;
	int num_back_channel_elements, num_lfe_channel_elements;
	int channels = 0;

	element_inst_tag = ((*pData)>>4 )&0x0f;
	profile = ((*pData)>>2)&0x03;
	sample_frq_index = ((*pData)&0x03)<<2;
	sample_frq_index |= ((*pData+1)>>6)&0x3;
	num_front_channel_elements = ((*pData+1)>>2)&0xf;
	num_side_channel_elements  = ((*pData+1)&0x3)<<2;
	num_side_channel_elements |= ((*pData+2)>>6)&0x03;
	num_back_channel_elements = ((*pData+2)>>6)&0xf;
	num_lfe_channel_elements  = ((*pData+2)&0x3)<<2;
	num_lfe_channel_elements  |= ((*pData+3)>>6)&0x03;

	if ( num_front_channel_elements ) channels++;
	if ( num_side_channel_elements ) channels+=2;
	if ( num_back_channel_elements ) channels++;
	if ( num_lfe_channel_elements ) channels++;
	return channels;
}

//////////////////////////////////////////////////////////////////////////////////////////

typedef struct {
	char audioObjectType;
	char extensionAudioObjectType;
	int  extensionSamplingFrequency;
	int  channelConfiguration;
	int  SamplingFrequency;
	unsigned short profile;
	int  channel_num;
	short frame_length;
} LATM_CFG;
int StreamMuxConfig( AAC_PARAM *aac, const unsigned char * pbData, int Size );
bool UnpackAACAudioLATM( AV_CONTEXT* av, const unsigned char * pbData, int Size	)
{
	unsigned short pack_frame_length;
	const unsigned char* pData;

	if ( av->AACParam.state == 0 ) av->AACParam.expect_bytes = 0;
	if ( av->AACParam.state == 3 ) return true; //already knew foramat, skip parsing.
	if ( av->AACParam.state && av->AACParam.format != 2 ) return false; //not LATM (DVB) format
	if ( av->AACParam.state > 0 && av->AACParam.expect_bytes > 0 )
	{
		if ( av->AACParam.expect_bytes > Size )
		{
			av->AACParam.expect_bytes -= Size;
			return false;
		} else 
		{
			pbData += av->AACParam.expect_bytes;
			Size  -= av->AACParam.expect_bytes;
			av->AACParam.expect_bytes = 0;
		}
	}

	pData = pbData;

	while ( Size > 8 )
	{
		//search LOAS head
		if ( !(pData[0] == 0x56 && ((pData[1] & 0xE0)==0xE0 )) )
		{
			av->AACParam.state = 0;
			av->AACParam.format = 0;
			return false;
		}

		pack_frame_length = (pData[1]&0x1f)<<8;
		pack_frame_length |= pData[2];

		av->AACParam.expect_bytes = pack_frame_length;
		Size  -= 3;
		pData += 3;
		if ( av->AACParam.expect_bytes < 8 ) return 0;
		if ( ( pData[0] & 0x80 ) == 0x0 ) //useSameStreamMux
		{
			int ret = StreamMuxConfig( &av->AACParam, pData, Size );
			if ( ret )
			{
				memcpy( av->AACParam.u.latm_header, pData-3, sizeof(av->AACParam.u.latm_header) );
			}
		}

		if ( pack_frame_length > Size )
		{
			av->AACParam.expect_bytes = pack_frame_length-Size;
			Size = 0;
		} else
		{
			av->AACParam.expect_bytes = 0;
			Size  -= pack_frame_length;
			pData += pack_frame_length;
		}

		if ( av->AACParam.state == 0 ) 
		{
			av->AACParam.frames = 0;
			av->AACParam.total_frame_bytes = 0;
		}
		av->AACParam.frames++;
		av->AACParam.total_frame_bytes += pack_frame_length;

		; //LOAS
		av->AACParam.format = 2;
		if ( ++av->AACParam.state >= 3  )
		{
			if ( av->AACParam.object_type )
			{
				if ( av->AACParam.frames )
					av->AACParam.bitrate = av->AACParam.sample_freq*av->AACParam.total_frame_bytes/(1024*av->AACParam.frames);
				av->AACParam.version = 0;
				av->AudioType =	AAC_AUDIO;   
				av->SubId = 0;
				return true;
			} else
				av->AACParam.state = 2;
		}
	}
	return false;
}

int ProgramConfigElement( LATM_CFG* cfg, const unsigned char * pbData, int* UsedBits, int* TotalBits  )
{
	int num_front_channel_elements, num_side_channel_elements;
	int num_back_channel_elements, num_lfe_channel_elements;
	int num_assoc_data_elements, num_valid_cc_elements;
	int comment_field_bytes;
	int channels = 0;

	int  bits_offset, total_bits, i;
	bits_offset = *UsedBits; total_bits = *TotalBits;

	//read_u( pbData, &bits_offset, &total_bits,   );
	*UsedBits = 0;
	
	skip_bits( &bits_offset, &total_bits, 4  ); //element_inst_tag
	skip_bits( &bits_offset, &total_bits, 2  ); //object type
	skip_bits( &bits_offset, &total_bits, 4  ); //sample frq inxe
	num_front_channel_elements = read_u( pbData, &bits_offset, &total_bits, 4 );
	num_side_channel_elements  = read_u( pbData, &bits_offset, &total_bits, 4 );
	num_back_channel_elements =  read_u( pbData, &bits_offset, &total_bits, 4 );
	num_lfe_channel_elements  =  read_u( pbData, &bits_offset, &total_bits, 2 );
	num_assoc_data_elements  =  read_u( pbData, &bits_offset, &total_bits, 3 );
	num_valid_cc_elements  =  read_u( pbData, &bits_offset, &total_bits, 4 );

	if ( num_front_channel_elements ) channels++;
	if ( num_side_channel_elements ) channels+=2;
	if ( num_back_channel_elements ) channels++;
	if ( num_lfe_channel_elements ) channels++;

    if ( read_u( pbData, &bits_offset, &total_bits, 1 ) ) //mono_mixdown_present
	   skip_bits( &bits_offset, &total_bits, 4 );        //mono_mixdown_element_number
    if ( read_u( pbData, &bits_offset, &total_bits, 1 ) ) //stereo_mixdown_present
	   skip_bits( &bits_offset, &total_bits, 4 );        //stereo_mixdown_element_number
    if ( read_u( pbData, &bits_offset, &total_bits, 1 ) ) // matrix_mixdown_idx_present
    {
		skip_bits( &bits_offset, &total_bits, 3 );		//matrix_mixdown_idx
		skip_bits( &bits_offset, &total_bits, 1 );		//pseudo_surround_enable;
    }
    
    for ( i = 0; i<num_front_channel_elements; i++ )
	{
		skip_bits( &bits_offset, &total_bits, 1 ); //front_element_is_cpe[i]
		skip_bits( &bits_offset, &total_bits, 4 ); //front_element_tag_select[i]
	};

    for ( i = 0; i <num_side_channel_elements; i++) 
	{
		skip_bits( &bits_offset, &total_bits, 1 ); //side_element_is_cpe[i]
		skip_bits( &bits_offset, &total_bits, 4 ); //side_element_tag_select[i]
	};

    for ( i = 0; i<num_back_channel_elements; i++ )
	{
		skip_bits( &bits_offset, &total_bits, 1 ); //back_element_is_cpe[i];
		skip_bits( &bits_offset, &total_bits, 4 ); //back_element_tag_select[i];
	}
    for ( i = 0; i<num_lfe_channel_elements; i++ )
	{
		skip_bits( &bits_offset, &total_bits, 4 ); //lfe_element_tag_select[i];
	}
    for ( i = 0; i<num_assoc_data_elements; i++ )
	{
		skip_bits( &bits_offset, &total_bits, 4 ); //assoc_data_element_tag_select[i];
	}
    for ( i = 0; i<num_valid_cc_elements; i++ )
	{
		skip_bits( &bits_offset, &total_bits, 1 ); //cc_element_is_ind_sw[i]
		skip_bits( &bits_offset, &total_bits, 4 ); //valid_cc_element_tag_select[i];
	}

	skip_bits( &bits_offset, &total_bits, (8-(bits_offset&0x07)) ); //byte_alignment() relative to audio specific config

    comment_field_bytes = read_u( pbData, &bits_offset, &total_bits, 8 );
    skip_bits( &bits_offset, &total_bits, 8*comment_field_bytes ); //comment_field_data[i]
	*UsedBits = bits_offset;	*TotalBits = total_bits;
	return channels;
}

int GASpecificConfig( LATM_CFG* cfg, const unsigned char * pbData, int* UsedBits, int* TotalBits )
{
	int  bits_offset, total_bits;
	int ext_flag;
	bits_offset = *UsedBits; total_bits = *TotalBits;

	skip_bits( &bits_offset, &total_bits, 1 );			  //framelen_flag
	if ( read_u( pbData, &bits_offset, &total_bits, 1 ) ) //dependsOnCoder
		skip_bits( &bits_offset, &total_bits, 14 );       //delay

	ext_flag = read_u( pbData, &bits_offset, &total_bits, 1 );
	if ( cfg->channelConfiguration == 0 )
	{
		cfg->channel_num = ProgramConfigElement( cfg, pbData, &bits_offset, &total_bits  );
	}
	if ( cfg->audioObjectType == 6 || cfg->audioObjectType == 20 ) 
		skip_bits( &bits_offset, &total_bits, 3 ); //layerNr

	if ( ext_flag ) 
	{
		switch ( cfg->audioObjectType ) {
		case 22:
			skip_bits( &bits_offset, &total_bits, 5 );  //numOfSubFrame 5);
			skip_bits( &bits_offset, &total_bits, 11 ); //layer_length  11;
			break;
		case 17:
		case 19:
		case 20:
		case 23:
			skip_bits( &bits_offset, &total_bits, 3 ); //stuff
			break;
		}
		skip_bits( &bits_offset, &total_bits, 1 ); //extflag3
	}
	*UsedBits = bits_offset;	*TotalBits = total_bits;
	return 1;
}

void ReadAudioSpecificConfig( LATM_CFG* cfg, const unsigned char * pbData, int* UsedBits, int* TotalBits )
{
	int samplingFrequencyIndex;
	int  bits_offset, total_bits;
	bits_offset = *UsedBits; total_bits = *TotalBits;

	cfg->audioObjectType = read_u( pbData, &bits_offset, &total_bits, 5 ); //(5);
	if ( cfg->audioObjectType == 31 )  //audioObjectTypeExt
		cfg->audioObjectType = 32 + read_u( pbData, &bits_offset, &total_bits, 6 );

	samplingFrequencyIndex =  read_u( pbData, &bits_offset, &total_bits, 4 );//(4);
	if ( samplingFrequencyIndex == 15 )
		cfg->SamplingFrequency = read_u( pbData, &bits_offset, &total_bits, 24 );//(24)
	else
		cfg->SamplingFrequency = AAC_sampling_Frequency[samplingFrequencyIndex];

	cfg->channelConfiguration = read_u( pbData, &bits_offset, &total_bits, 4 );//(4);
	//sbrPresentFlag = 0;
	cfg->extensionAudioObjectType = 0;
	if ( cfg->audioObjectType == 5 ) //explicit SBR signaling
	{
		int extensionSamplingFrequencyIndex;
		cfg->extensionAudioObjectType = cfg->audioObjectType;
		//sbrPresentFlag = 1;
		extensionSamplingFrequencyIndex = read_u( pbData, &bits_offset, &total_bits, 4 );//(4);
		if ( extensionSamplingFrequencyIndex == 15 )
			cfg->extensionSamplingFrequency = read_u( pbData, &bits_offset, &total_bits, 24 ); //(24)
		else
			cfg->extensionSamplingFrequency = AAC_sampling_Frequency[extensionSamplingFrequencyIndex];
	};
	switch ( cfg->audioObjectType ) {
	case 1:; case 2:; case 3:; case 4:; case 6:; case 7:; case 17:; case 19:; case 22:; case 23:
		GASpecificConfig( cfg, pbData, &bits_offset, &total_bits  );
		break;
	};

	skip_bits( &bits_offset, &total_bits, bits_offset & 0x07 ); //align byte
	*UsedBits = bits_offset; *TotalBits = total_bits;
};

#define MAX_LATM_STREAM_NUM    64
int StreamMuxConfig( AAC_PARAM *aac, const unsigned char * pbData, int Size )
{
	//LATM parsing according to ISO/IEC 14496-3

	int num, prog, lay, streamCnt;
	unsigned long frame_length_type;
	int use_same_config,  all_same_framing;
	int audio_mux_version, frameLength;
	int numProgram, numSubFrames;
	int objTypes[8];
	LATM_CFG cfgs[MAX_LATM_STREAM_NUM]={0};
	LATM_CFG* cfg;

	int  bits_offset, total_bits;
	bits_offset = 1; total_bits = Size*8-1;

	audio_mux_version = read_u( pbData, &bits_offset, &total_bits, 1 ); //1bits 
	if ( audio_mux_version == 1 ) 
	{
		return 0;
		if ( read_u( pbData, &bits_offset, &total_bits, 1 ) != 0 )
			return 0; //tbd

	}
	all_same_framing = read_u( pbData, &bits_offset, &total_bits, 1 );//(1)
	numSubFrames = read_u( pbData, &bits_offset, &total_bits, 6 );//(6);
	numProgram = read_u( pbData, &bits_offset, &total_bits, 4 );//(4);

  //DVB TS takes multiplexing, only uses one subframe, one program and one layer 
  // and allStreamsSameTimeFraming is always = 1. Though
  //the StreamMuxConfig is parsed and stored completely (maybe we can use it later), the
  //parse first program first layer.

  streamCnt = 0;
  for ( prog = 0; prog <= numProgram; prog++ )
  {
    num = read_u( pbData, &bits_offset, &total_bits, 3 );//(3);
    //numLayer[prog] = num;
    //SetLength(streams,Length(streams)+num+1);
    //SetLength(config,Length(config)+num+1);

    for ( lay = 0; lay <= num; lay++ )
    {
			if ( MAX_LATM_STREAM_NUM < streamCnt ) return 0;
			
			cfg = &cfgs[streamCnt];
			frameLength = 0;

			//streamID[prog][lay] = streamCnt++;
			//progSIndex = prog;
			//laySIndex = lay;
			if ((prog == 0) && (lay == 0)) 
				use_same_config = 0;
			else
				use_same_config = read_u( pbData, &bits_offset, &total_bits, 1 );

			if ( use_same_config )   // same as before
			{
			}
			else 
			if ( audio_mux_version == 0 ) //audio specific config.
			{
				ReadAudioSpecificConfig( cfg, pbData, &bits_offset, &total_bits );
				if ( lay == 0 )
				{
					aac->channel_num = cfg->channelConfiguration ? cfg->channelConfiguration : cfg->channel_num;
					aac->sample_freq = cfg->SamplingFrequency;
					aac->object_type = cfg->audioObjectType;
				}
			}
			else 
			{
				//ZQ, I don't process audio_mux_version > 0 stream
				printf( "Error: audio mux version should be 0.\n" );
				return 0;
			};

			objTypes[lay] = cfg->audioObjectType;
			
			frame_length_type = read_u( pbData, &bits_offset, &total_bits, 3 ); //(3);
			switch ( frame_length_type ) {
				case  0:
					{
						int latmBufferFullness;
						latmBufferFullness = read_u( pbData, &bits_offset, &total_bits, 8 );//(8);
						if ( all_same_framing == 0 )
							if ( (objTypes[lay] == 6 || objTypes[lay] == 20) &&
								 ( objTypes[lay-1] == 8 || objTypes[lay-1] == 24 ) )   
									skip_bits( &bits_offset, &total_bits, 6 ); //(6);core_frame_offset
					}
					break;
				case 1:
					frameLength = read_u( pbData, &bits_offset, &total_bits, 9 );//(9); 
					break;
				case 3: case 4: case 5:
					skip_bits( &bits_offset, &total_bits, 6 );//(6); celp_table_index
					break;
				case 6: case 7:
					skip_bits( &bits_offset, &total_bits, 1 );//(1); hvxc_table_index
					break;
			};
		 
			if ( lay == 0 && frameLength )  aac->frame_length = frameLength;

		streamCnt++;
    }; //for lay = 0 to numLayer[prog]
  }; //for prog = 0 to numProgram

  // other data
  if ( read_u( pbData, &bits_offset, &total_bits, 1 ) ) // other data present
  {
		while ( read_u( pbData, &bits_offset, &total_bits, 1 )  ) //esc
			skip_bits( &bits_offset, &total_bits, 8 );//other_data_bits
  }  

  // CRC
  if ( read_u( pbData, &bits_offset, &total_bits, 1 ) )
		skip_bits( &bits_offset, &total_bits, 8 ); //config_crc (8);

  return 1;
}


////////////////////////////////////////////////////////////////////////////////////

///////////////////////////////////////////////////////////////////////
int CheckMPEGPacket( const unsigned char* pStart, int Bytes )
{
	const unsigned char	*pbData, *p1, *p2;
	int	 len;
	unsigned long code;
	unsigned long StartCode = 0x000001BA;

	if ( Bytes < 4 )
		return false;

	pbData = pStart;
	len = Bytes;
	code = 0xffffff00;

	pbData = pStart;
	code |=  *pbData++;
	while ( --len && code != StartCode )
	{
		code = ( code << 8 )| *pbData++;
	}

	if ( code == StartCode )
	{
		p1 = pbData;
		code = 0xffffff00;
		code |=  *pbData++;
		while ( --len && ( code & 0x00ffffff ) != 0x01  )
		{
			code = ( code << 8 )| *pbData++;
		}
		p2 = pbData;
		if (  ( code & 0x00ffffff ) == 0x01  && p2-p1 < 30 )
		{
			//if ( *pbData == 0xBB || ( *pbData & 0xc0 ) == 0xc0 || 
			//	 ( *pbData & 0xE0 ) == 0xe0 )
			return 1+Bytes-len;
		}
	}

	return 0;

}

bool CheckTSPacket( const unsigned char* pStart, int Bytes ) 
{
	int	 i;

	if ( Bytes < 188*3 )
		return false;

	for ( i = 0; i<=Bytes-188*5; i++ )
	{
		if ( pStart[i] == 0x47 && pStart[i+188] == 0x47 && pStart[i+2*188] == 0x47
			 && pStart[i+3*188] == 0x47 && pStart[i+4*188] == 0x47 && pStart[i+5*188] == 0x47 
		     && (pStart[i+1] & 0x80) == 0 && (pStart[i+188+1] & 0x80) == 0 && (pStart[i+2*188+1] & 0x80) == 0
			 && (pStart[i+3*188+1] & 0x80) == 0 && (pStart[i+4*188+1] & 0x80) == 0 && (pStart[i+5*188+1] & 0x80) == 0 
			 )
		{
			return i+1;
		}
	}

	return 0;
}

#define ISO639_LANGUAGE_DESC  0x0a
unsigned long GetLauguage( char* descriptor )
{
	unsigned long country=0;
	int totoal, len, i;
	unsigned char* p;
	totoal = (int)descriptor[0];
	p = (unsigned char*)descriptor+1;
	i = 0;
	while ( i<totoal )
	{
		len = *(p+1);
		if ( *p == ISO639_LANGUAGE_DESC )
		{
			country = (*(p+2)<<24) | (*(p+3)<<16) | (*(p+4)<<8);
			return country;
		}

		p += 2+len;
		i += 2+len;
	}

	return 0;
}
int GuessAuidoType( char* descriptor )
{
	int len = (int)descriptor[0];
	return GetAuidoType( descriptor+1, len  );
}

int GetAuidoType( char* descriptor, int length  )
{
	int totoal, len, i, guess_type;
	unsigned char* p;
	guess_type = 0;
	totoal = length;
	p = (unsigned char*)descriptor;
	i = 0;
	while ( i<totoal )
	{
		len = *(p+1);
		if ( *p == 0x6a ) // DVB AC3 descriptor )
			return AC3_AUDIO;
		
		if ( *p == 0x81 ) //ASTC AC3
			return AC3_AUDIO;

		if ( *p == 0x56 ) //TELTEXT;
			return -1;

		if ( *p == 0x59 ) //Subtitle;
			return -1;

		if ( *p == 0x73 ) //DTS;
			return DTS_AUDIO;

		if ( *p == 0x1c )  //MPEG-4 audio
			return MPEG4_AUDIO_STREAM;

		if ( *p == 0x03 ) 
			guess_type = MPEG_AUDIO;

		if ( *p == 0x02 ) //video_type;
			return -1;

		p += 2+len;
		i += 2+len;
	}

	return guess_type;
}
bool IsTeletextType( char* descriptor )
{
	int len;
	unsigned char* p;
	len = (int)descriptor[0];
	p = (unsigned char*)descriptor+1;
	return IsTeletextTypeDescriptor( (char*)p, len );

}

bool IsSubtitleType( char* descriptor )
{
	int len;
	char* p;
	len = (int)descriptor[0];
	p = descriptor+1;
	return IsSubtitleDescriptor( p, len );
}


unsigned short GetISO639LanguageType( char* descriptor )
{
	int totoal, len, i, guess_type;
	unsigned char* p;
	guess_type = 0;
	totoal = (int)descriptor[0];
	p = (unsigned char*)descriptor+1;
	i = 0;
	while ( i<totoal )
	{
		len = *(p+1);
		if ( *p == ISO639_LANGUAGE_DESC ) 
		{
			return *(p+5);
		}
		p += 2+len;
		i += 2+len;
	}

	return 0xffff;
}

bool IsTeletextTypeDescriptor( char* descriptor, int len )
{
	int totoal,i;
	unsigned char* p;
	totoal = len;
	p = (unsigned char*)descriptor;
	i = 0;
	while ( i<totoal )
	{
		len = *(p+1);
		if ( *p == 0x6a ) // DVB AC3 descriptor )
			return false;
		
		if ( *p == 0x81 ) //ASTC AC3
			return false;

		if ( *p == 0x56 ) //TELTEXT;
			return true;

		if ( *p == 0x45 ) //VBI_DATA_DESCRIPTION 
			return true;

		if ( *p == 0x46 ) //VBI_TELTEXT_DESCRIPTION;
			return true;

		if ( *p == 0x59 ) //Subtile;
			return false;

		if ( *p == 0x1c )  //MPEG-4 audio
			return false;

		if ( *p == 0x03 ) //Audio
			return false;

		if ( *p == 0x02 ) //video_type;
			return false;

		if ( *p == 0x73 ) //DTS;
			return false;

		p += 2+len;
		i += 2+len;
	}

	return false;;
}

bool IsSubtitleDescriptor( char* descriptor, int len )
{
	int totoal, i;
	unsigned char* p;
	totoal = len;
	p = (unsigned char*)descriptor;
	i = 0;
	while ( i<totoal )
	{
		len = *(p+1);
		if ( *p == 0x6a ) // DVB AC3 descriptor )
			return false;
		
		if ( *p == 0x81 ) //ASTC AC3
			return false;

		if ( *p == 0x59 ) //Subtile;
			return true;

		if ( *p == 0x56 ) //TELTEXT;
			return false;

		if ( *p == 0x1c ) //MPEG-4 audio
			return false;

		if ( *p == 0x03 ) //Audio
			return false;

		if ( *p == 0x02 ) //video_type;
			return false;

		if ( *p == 0x73 ) //DTS;
			return false;

		p += 2+len;
		i += 2+len;
	}

	return false;;
}

bool IsPrivateData( char* descriptor, int len )
{
	int totoal, i;
	unsigned char* p;
	totoal = len; 
	p = (unsigned char*)descriptor;
	i = 0;
	while ( i<totoal )
	{
		len = *(p+1);

		if ( *p == 0xc3 ) 
			return true;

		p += 2+len;
		i += 2+len;
	}
	return false;;
}


bool IsDescriptor( char* descriptor, int len )
{
	int totoal, i;
	unsigned char* p;
	totoal = len;
	p = (unsigned char*)descriptor;
	i = 0;
	while ( i<totoal )
	{
		len = *(p+1);
		if ( *p == 0x6a ) // DVB AC3 descriptor )
			return false;
		
		if ( *p == 0x81 ) //ASTC AC3
			return false;

		if ( *p == 0x59 ) //Subtile;
			return true;

		if ( *p == 0x1c )
			return false;

		if ( *p == 0x03 ) 
			return false;

		if ( *p == 0x02 ) //video_type;
			return false;

		p += 2+len;
		i += 2+len;
	}

	return false;;
}


//////////////////////////////////////////////////////////////////////////////////////
void InitOutBits( BITS_T *bits, unsigned char* buf, int size )
{
	bits->outbfr =0;
	bits->outcnt = 32;
	bits->bytecnt = 0;
    bits->buf = buf;
	bits->buf_start = buf;
	bits->buf_size = size;
}


void PutBits( BITS_T *bits, unsigned long val, int n )  
{
	unsigned long mask;
	if ( n == 0 ) return;
	if ( bits->bytecnt >= bits->buf_size ||  bits->buf == NULL )
		return;

	mask = 0xffffffff;
	mask >>= 32-n;
	val &= mask;

	if ( bits->outcnt > n )
	{
		bits->outbfr <<= n;
		bits->outbfr |= val;
		bits->outcnt -= n;
	}
	else
	{
		if ( bits->outcnt == 32 )   /*intel cpu if shift more than register bits length, operation skips, ZQ */
			bits->outbfr = 0;
		else
			bits->outbfr = ( bits->outbfr << bits->outcnt );
		bits->outbfr |=  val >> (n - bits->outcnt)  ;
		
		//mask = 0xffffffff;
		//mask >>= 32-(n - bits->outcnt);
		//val &= mask;
	
    	*(bits->buf++) = ( (unsigned char)(bits->outbfr>>24) ); /* write to stream */
	    *(bits->buf++) = ( (unsigned char)(bits->outbfr>>16) ); /* write to stream */
		*(bits->buf++) = ( (unsigned char)(bits->outbfr>>8 ) ); /* write to stream */
    	*(bits->buf++) = ( (unsigned char)(bits->outbfr )	 ); /* write to stream */

		bits->outcnt += 32 - n;
		bits->bytecnt+=4;
		bits->outbfr = val;
	}
}

int CloseOutBits( BITS_T *bits )
{
	bits->bytecnt += (32-bits->outcnt)/8;
	bits->bytecnt += ((32-bits->outcnt) & 7 ) != 0 ? 1 : 0 ;
	if ( bits->outcnt < 32 )
	{	
		bits->outbfr = bits->outbfr << bits->outcnt;
		bits->outcnt = 32-bits->outcnt; //Jeff found this bug!!! (twice...it used to be above the prior line, then commented out, and now it's here!)
		if ( bits->outcnt >=24 )
		{
			*(bits->buf++) = ( (unsigned char)(bits->outbfr>>24) ); /* write to stream */
			*(bits->buf++) = ( (unsigned char)(bits->outbfr>>16) ); /* write to stream */
			*(bits->buf++) = ( (unsigned char)(bits->outbfr>>8) ); /* write to stream */
			*(bits->buf) |= (unsigned char)(bits->outbfr);
		} else
		if ( bits->outcnt >=16 )
		{
			*(bits->buf++) = ( (unsigned char)(bits->outbfr>>24) ); /* write to stream */
			*(bits->buf++) = ( (unsigned char)(bits->outbfr>>16) ); /* write to stream */
			*(bits->buf) |= (unsigned char)(bits->outbfr>>8);
		} else
		if ( bits->outcnt >=8 )
		{
			*(bits->buf++) = ( (unsigned char)(bits->outbfr>>24) ); /* write to stream */
			*(bits->buf) |= (unsigned char)(bits->outbfr>>16);
		} else
		if ( bits->outcnt > 0 )
		{
		  *(bits->buf) |= (unsigned char)(bits->outbfr>>24);
		}
	}

	bits->outcnt = 32;
	bits->outbfr = 0;
	return 	bits->bytecnt;

}

void AlignOutBits( BITS_T *bits )                
{
	PutBits( bits, 0, bits->outcnt & 7  );
}

long BitOutCount( BITS_T *bits )                 
{
	return 8 * bits->bytecnt + (32 - bits->outcnt);
}

long BytesOutCount( BITS_T *bits )                  
{
	return bits->bytecnt;
}

int FlushOutBits( BITS_T *bits, char* out_buf, int size )                 
{
	int bytes;
	if ( bits->outcnt < 32 )
		PutBits( bits, 0, bits->outcnt );

	bytes = _MIN( size, bits->bytecnt );
	memcpy( out_buf, bits->buf_start, bytes );
	if ( bytes < bits->bytecnt )
	{
		memcpy( bits->buf_start, bits->buf_start+bytes, ( bits->bytecnt - bytes ) );
		bits->bytecnt -= bytes;
		bits->buf -= bytes;
	} else
	{
		bits->bytecnt = 0;
		bits->buf = bits->buf_start;
	}

	return bytes;
}

//ZQ haven't test yet following code
void InitInBits( BITS_T *bits, unsigned char* buf, int size )
{
	bits->outbfr =0;
	bits->outcnt = 0;
	bits->bytecnt = 0;
    bits->buf = buf;
	bits->buf_start = buf;
	bits->buf_size = size;

}

unsigned long ReadBits( BITS_T *bits, int n )  
{
	unsigned long val;
	unsigned long mask;
	if ( n == 0 ) return 0;
	if ( bits->bytecnt == 0 && bits->outcnt >= 8 )
		return 0;

	if ( n >= 32 )
	{
		val = bits->outbfr;
		bits->outbfr = *(bits->buf++);
		bits->outbfr <<= 8;
		bits->outbfr |= *(bits->buf++);
		bits->outbfr <<= 8;
		bits->outbfr |= *(bits->buf++);
		bits->outbfr <<= 8;
		bits->outbfr |= *(bits->buf++);
		bits->bytecnt -= 4;
		return val;
	}

	mask = 0xffffffff;
	mask <<= 32-n;
	val = bits->outbfr & mask;
	val >>= 32-n;

	mask = 0x80;
	mask >>= bits->outcnt;
	while ( n--  )
	{
		bits->outbfr <<= 1;
		bits->outcnt++;

		if ( bits->bytecnt == 0 && bits->outcnt < 8 )
			continue;
		
		if ( *bits->buf & mask )
			bits->outbfr |= 1;

		mask >>= 1;
		if ( mask == 0 )
		{
			bits->outcnt = 0;
			mask = 0x80;
			if ( bits->bytecnt )
			{
				bits->bytecnt--;
				bits->buf++;
			}
		}
		
	}

	return val;
}

unsigned long AlignInBits( BITS_T *bits )                
{
	return ReadBits( bits, bits->outcnt & 7 );
}

int ReadInBits( BITS_T *bits, char* in_buf, int size )                 
{
	int bytes, empty_val;
	empty_val = ( bits->buf == bits->buf_start && bits->bytecnt == 0 );

	memcpy( bits->buf_start, bits->buf, bits->bytecnt );
    bits->buf = bits->buf_start;

	bytes = _MIN( size, bits->buf_size - bits->bytecnt );
	memcpy( bits->buf_start + bits->bytecnt, in_buf, bytes );

	bits->bytecnt += bytes;

	if ( empty_val )
	{
		bits->outbfr = *(bits->buf++);
		bits->outbfr <<= 8;
		bits->outbfr |= *(bits->buf++);
		bits->outbfr <<= 8;
		bits->outbfr |= *(bits->buf++);
		bits->outbfr <<= 8;
		bits->outbfr |= *(bits->buf++);
		bits->bytecnt -= 4;
	}

	return bytes;
}


////////////////////////////////////// h.264 ///////////////////////////////////////////////
#define NAL_SLICE                1
#define NAL_DPA                  2
#define NAL_DPB                  3
#define NAL_DPC                  4
#define NAL_IDR_SLICE            5
#define NAL_SEI                  6
#define NAL_SPS                  7
#define NAL_PPS                  8
#define NAL_AUD                  9
#define NAL_END_SEQUENCE        10
#define NAL_END_STREAM          11
#define NAL_FILLER_DATA         12
#define NAL_SPS_EXT             13
#define NAL_AUXILIARY_SLICE     19

//int static_type[32]={0};
int InterpretProfileLevel( AV_CONTEXT* av );
static int ParseGolombCode (const unsigned char buffer[], int totbitoffset, int *info, int bytecount )                           
 {                                                                                                   
	register int inf;                                                                                 
	long byteoffset;      // byte from start of buffer                                                
	int bitoffset;      // bit from start of byte                                                     
	int ctr_bit=0;      // control bit for current bit posision                                       
	int bitcounter=1;                                                                                 
	int len;                                                                                          
	int info_bit;                                                                                     
	                                                                                                    
	byteoffset= totbitoffset>>3;                                                                      
	bitoffset= 7-(totbitoffset&0x07);                                                                 
	ctr_bit = (buffer[byteoffset] & (0x01<<bitoffset));   // set up control bit                       
	                                                                                                    
	len=1;                                                                                            
	while (ctr_bit==0)                                                                                
	{                 // find leading 1 bit                                                           
		len++;                                                                                          
		bitoffset-=1;                                                                                   
		bitcounter++;                                                                                   
		if (bitoffset<0)                                                                                
		{                 // finish with current byte ?                                                 
		bitoffset=bitoffset+8;                                                                        
		byteoffset++;                                                                                 
		}                                                                                               
		ctr_bit=buffer[byteoffset] & (0x01<<(bitoffset));                                               
	}                                                                                                 
	    
	// make infoword                                                                                
	inf=0;                          // shortest possible code is 1, then info is always 0             
	for(info_bit=0;(info_bit<(len-1)); info_bit++)                                                    
	{                                                                                                 
		bitcounter++;                                                                                   
		bitoffset-=1;                                                                                   
		if (bitoffset<0)                                                                                
		{ // finished with current byte ?                                               
		bitoffset=bitoffset+8;                                                                        
		byteoffset++;                                                                                 
		}                                                                                               
		if (byteoffset > bytecount)                                                                     
		{                                                                                               
			return -1;                                                                                    
		}                                                                                               
		inf=(inf<<1);                                                                                   
		if(buffer[byteoffset] & (0x01<<(bitoffset)))                                                    
		inf |=1;                                                                                      
	}                                                                                                 
	                                                                                                    
	*info = inf;                                                                                      
	return bitcounter;           // return absolute offset in bit from start of frame                 
 }                                                                                                   



unsigned long ue( const unsigned char *code, int bits_offset, int total_bits, int* code_bits )
{
	int len, info;
	*code_bits = 0;
	len = ParseGolombCode( code, bits_offset, &info, (total_bits>>3)+((total_bits&0x7)?1:0)  );
	if ( len == -1 ) return 0;
	*code_bits = len;
	return (1<<(len>>1))+info-1;
}

int se( const unsigned char *code, int bits_offset, int total_bits, int* code_bits )
{
	int len, info;
	int n, val;
	*code_bits = 0;
	len = ParseGolombCode( code, bits_offset, &info, (total_bits>>3)+((total_bits&0x7)?1:0)  );
	if ( len == -1 ) return 0;
	*code_bits = len;

	n = (1 << (len>>1))+info-1;
	val = (n+1)>>1;
	if((n & 0x01)==0)                           // lsb is signed bit
		val = -val;

	return val;
}

unsigned short u(  const unsigned char *buffer, int totbitoffset, int total_bits, int *code_bits )
{
	long byteoffset;      // byte from start of buffer                                                
	int bitoffset;      // bit from start of byte                                                     
	register short val, i;      // control bit for current bit posision                                       
	    
	if ( *code_bits > total_bits )
	{
		*code_bits = 0;
		return 0;
	}

	val = 0;

	for ( i = 0; i<(short)*code_bits; i++ )
	{
		val <<= 1;
		byteoffset= totbitoffset>>3;                                                                      
		bitoffset= 7-(totbitoffset&0x07); 
		totbitoffset++;
		if ( (buffer[byteoffset] & (0x01<<bitoffset)) )
		{
			 val |= 1;
		}
	}
	return val;
}

void skip_bits( int* bits_offset, int* total_bits, int bits )
{
	*bits_offset += bits;
	*total_bits  -= bits;
}

unsigned int read_ue( const unsigned char* buffer, int* bits_offset, int* total_bits  )
{
	int val, bits;
	val = ue( buffer, *bits_offset, *total_bits, &bits );
	*bits_offset += bits;
	*total_bits  -= bits;
	return val;
}


int read_se( const unsigned char* buffer, int* bits_offset, int* total_bits  )
{
	int val, bits;
	val = se( buffer, *bits_offset, *total_bits, &bits );
	*bits_offset += bits;
	*total_bits  -= bits;
	return val;
}

int read_u( const unsigned char* buffer, int* bits_offset, int* total_bits, int bits  )
{
	int val;
	val = u( buffer, *bits_offset, *total_bits, &bits );
	*bits_offset += bits;
	*total_bits  -= bits;
	return val;
}

static int NAL2RBSP( unsigned char* src, unsigned char* dst, int size )
{
	int si=0, di=0;
    while(si<size)
	{    
        if(si+2<size && src[si]==0 && src[si+1]==0 && src[si+2]<=3){
            if(src[si+2]==3){ //escape
                dst[di++]= 0;
                dst[di++]= 0;
                si+=3;
                continue;
            }else //next start code
                return di;
        }

        dst[di++]= src[si++];
    }
	return di;
}

static void skip_scaling_List(  int sizeOfScalingList, unsigned char* rbsp, int* bits_offset, int* total_bits )                                                                                
{      
	const unsigned char ZZ_SCAN[16]  =                                                                                                                                                                 
	{  0,  1,  4,  8,  5,  2,  3,  6,  9, 12, 13, 10,  7, 11, 14, 15                                                                                                                          
	};                                                                                                                                                                                        
                                                                                                                                                                                          
	const unsigned char ZZ_SCAN8[64] =                                                                                                                                                                 
		{  0,  1,  8, 16,  9,  2,  3, 10, 17, 24, 32, 25, 18, 11,  4,  5,                                                                                                                         
   		12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13,  6,  7, 14, 21, 28,                                                                                                                        
   		35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,                                                                                                                        
   		58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63                                                                                                                         
		};    

	int scalingList[64];

	int j, scanj;                                                                                                                                                                           
	int delta_scale, lastScale, nextScale;                                                                                                                                                  
	lastScale = 8;                                                                                                                                                                     
	nextScale = 8;                                                                                                                                                                     
	for(j=0; j<sizeOfScalingList; j++)                                                                                                                                                      
	{                                                                                                                                                                                       
		scanj = (sizeOfScalingList==16) ? ZZ_SCAN[j]:ZZ_SCAN8[j];                                                                                                                             
		                                                                                                                                                                                        
		if(nextScale!=0)                                                                                                                                                                      
		{                                                                                                                                                                                     
			delta_scale = read_se( rbsp, bits_offset, total_bits );
			nextScale = (lastScale + delta_scale + 256) % 256;                                                                                                                                  
		}                                                                                                                                                                                     
		scalingList[scanj] = (nextScale==0) ? lastScale:nextScale;                                                                                                                            
		lastScale = scalingList[scanj];                                                                                                                                                       
	}    

}                                                                                                                                                                                         
// fill sps with content of p                                                                                                                                                             
bool UnpackH264VideoHeader( AV_CONTEXT* av, const unsigned char* pData, int Size )
{
	const unsigned char* p;
	unsigned char rbsp[256];
	int zero_bytes;
	zero_bytes  = 0;
	p = pData;

	while ( 1 )
	{
		//start code prefix search
		while ( p < pData+Size-1 )
		{
			if ( *p == 0 ) 
				zero_bytes++;
			if ( zero_bytes >= 3 && *p )
			{
				if ( *p != 1 && *p != 3 )
				{
					//illeag byte codes in H.264
					if ( av->H264Param.guessH264 > -200 ) av->H264Param.guessH264 -= 20; //not H.264 stream
					return false;
				} else
				if ( *p == 1 ) 
				{
					if ( *(p+1) & 0x80 )  //forbidden bits
					{
						if ( av->H264Param.guessH264 > -200 )  av->H264Param.guessH264 -= 20; //not H.264 stream
						return false;
					} else
					{
						p++;
						zero_bytes = 0;
						break;                        //found HAL header
					}
				}
			}
			if ( *p ) zero_bytes = 0;
			p++;
		}

		if ( p < pData+Size-1 )
		{
			int nal_ref_idc, nal_unit_type;
			
			int bits_offset, total_bits, bits;
			nal_ref_idc = *p>>5;
			nal_unit_type = *p&0x1F;
			p++;

			//static_type[nal_unit_type]++;
			if ( nal_unit_type == NAL_SPS )
			{
				int bytes = ( Size-(p-pData) < (int)sizeof(rbsp) ) ? Size-(p-pData) : (int)sizeof(rbsp) ;
				bytes = NAL2RBSP( (unsigned char*)p, (unsigned char*)rbsp, bytes );
				p += bytes;
				if ( bytes < 4 )
					return false;  //too little data to parse information

				av->H264Param.profile = rbsp[0]; 
				av->H264Param.constraint_set0 = rbsp[1] & 0x80 ?  1:0;
				av->H264Param.constraint_set1 = rbsp[1] & 0x40 ?  1:0;
				av->H264Param.constraint_set2 = rbsp[1] & 0x20 ?  1:0;
				av->H264Param.level = rbsp[2];

				if ( ( rbsp[1] & 0x1f ) )
				{
					if ( av->H264Param.guessH264 > -200 )  av->H264Param.guessH264 -= 20; //not H.264 stream
					return false;
				}
				
				bits_offset = 24;
				total_bits = bytes * 8 - 24;
				av->H264Param.sps_id = ue( rbsp, bits_offset, total_bits, &bits  );
				if ( bits > 0 )
				{
					bits_offset += bits;
					total_bits  -= bits;
				} else
				{
					if ( av->H264Param.guessH264 > -200 ) av->H264Param.guessH264 -= 10; 
					return false;
				}
				
				if ( av->H264Param.guessH264 < 200 )	av->H264Param.guessH264 += 20;

				////////////////////////////////////////////////////////////

				if(( av->H264Param.profile == 100 ) ||                                                                  
					(av->H264Param.profile == 110 ) ||                                                                       
					(av->H264Param.profile ==122 )  ||                                                                       
					(av->H264Param.profile ==144 ))                                                                      
				{                                                                                                        
				     // ue("SPS: chroma_format_idc")                                                                             
					int chroma_format_idc  = read_ue( rbsp, &bits_offset, &total_bits );
					 // Residue Color Transform                                                                             
					if( chroma_format_idc == 3 ) 
					{	
						int residue_transform_flag = read_u( rbsp, &bits_offset, &total_bits, 1 );
					}
					{                                                                          
						int bit_depth_luma_minus8 = read_ue( rbsp, &bits_offset, &total_bits );
						int bit_depth_chroma_minus8 = read_ue( rbsp, &bits_offset, &total_bits );   
						int lossless_qpprime_flag = read_u( rbsp, &bits_offset, &total_bits, 1 );
						int seq_scaling_matrix_present_flag  = read_u( rbsp, &bits_offset, &total_bits, 1 );
						if( seq_scaling_matrix_present_flag )           
						{   int i;                                                                                      
							for(i=0; i<8; i++)         
							{                                                                                       
								int seq_scaling_list_present_flag   = read_u( rbsp, &bits_offset, &total_bits, 1 ); 
								if( seq_scaling_list_present_flag )                                   
								{      
									if(i<6)                             
										skip_scaling_List( 16, rbsp, &bits_offset, &total_bits );              
									else                          
										skip_scaling_List( 64, rbsp, &bits_offset, &total_bits );    
								}                 
							}                                                                                       
						}                                                                                 
					}  
				}
				{                                                                 
					int log2_max_frame_num_minus4              = read_ue( rbsp, &bits_offset, &total_bits );
					int pic_order_cnt_type                     = read_ue( rbsp, &bits_offset, &total_bits );
				                                                                                                                                                                                        
					if ( pic_order_cnt_type == 0) 
					{
						int log2_max_pic_order_cnt_lsb_minus4 = read_ue( rbsp, &bits_offset, &total_bits );
					}
					else if ( pic_order_cnt_type == 1 )                                                                       
					{        
						int i;
						int delta_pic_order_always_zero_flag      = read_u( rbsp, &bits_offset, &total_bits, 1 ); 
						int offset_for_non_ref_pic                = read_se( rbsp, &bits_offset, &total_bits );
						int offset_for_top_to_bottom_field        = read_se( rbsp, &bits_offset, &total_bits );
						int num_ref_frames_in_pic_order_cnt_cycle = read_ue( rbsp, &bits_offset, &total_bits );
						for( i=0; i<num_ref_frames_in_pic_order_cnt_cycle; i++) 
						{
							int offset_for_ref_frame          = read_se( rbsp, &bits_offset, &total_bits );
						}
					} 
				}
				{
					int num_ref_frames                        = read_ue( rbsp, &bits_offset, &total_bits );
					int gaps_in_frame_num_value_allowed_flag  = read_u( rbsp, &bits_offset, &total_bits, 1 ); 
					int pic_width_in_mbs_minus1               = read_ue( rbsp, &bits_offset, &total_bits );
					int pic_height_in_map_units_minus1        = read_ue( rbsp, &bits_offset, &total_bits );
					int frame_mbs_only_flag                   = read_u( rbsp, &bits_offset, &total_bits, 1 ); 
					av->H264Param.progressive = 0;
					if (!frame_mbs_only_flag)                                                                     
					{                                                                           
						int mb_adaptive_frame_field_flag        = read_u( rbsp, &bits_offset, &total_bits, 1 ); 
						av->H264Param.progressive = 1;
					}
					av->H264Param.width = (pic_width_in_mbs_minus1+1) * 16;
					av->H264Param.height = (pic_height_in_map_units_minus1+1) * (2-frame_mbs_only_flag) * 16;
				}
				{
					int direct_8x8_inference_flag             = read_u( rbsp, &bits_offset, &total_bits, 1 ); 
					int frame_cropping_flag                   = read_u( rbsp, &bits_offset, &total_bits, 1 ); 
				        int vui_parameters_present_flag;                        
					if (frame_cropping_flag)                                                                      
					{                                                                          
						int frame_cropping_rect_left_offset      = read_ue( rbsp, &bits_offset, &total_bits );
						int frame_cropping_rect_right_offset     = read_ue( rbsp, &bits_offset, &total_bits );
						int frame_cropping_rect_top_offset       = read_ue( rbsp, &bits_offset, &total_bits );
						int frame_cropping_rect_bottom_offset    = read_ue( rbsp, &bits_offset, &total_bits );
					}                                                                          
					vui_parameters_present_flag           =  read_u( rbsp, &bits_offset, &total_bits, 1 ); 

				}


				//printf( "profile:%d level:%d width:%d height:%d(spsid:%d)\n", av->H264Param.profile, av->H264Param.level, 
				//	av->H264Param.width, av->H264Param.height, av->H264Param.sps_id );

/////////////////////////////////////////////////////////////
			} else
			if ( nal_unit_type == NAL_SEI )
			{
				int bytes = ( Size-(p-pData) < (int)sizeof(rbsp) ) ? Size-(p-pData) : (int)sizeof(rbsp) ;
				//printf( "NALU SEI:%d\n", nal_unit_type );
				bytes = NAL2RBSP( (unsigned char*)p, (unsigned char*)rbsp, bytes );
				p += bytes;
			} else
			{
				//skip NALU
				//printf( "NALU type:%d\n", nal_unit_type );
			}
		} else
			break;

	}

	if ( av->H264Param.guessH264 >= 20 && av->H264Param.level && av->H264Param.profile )
	{
		if ( InterpretProfileLevel( av ) >  0 )
		{
			av->VideoType = H264_VIDEO;
			return true;
		} else
		{
			if ( av->H264Param.guessH264 >=-200 )
				av->H264Param.guessH264--;
		}
	}

	return false;
}


int InterpretProfileLevel( AV_CONTEXT* av )
{
	//int ret = 1; 
	if ( av->H264Param.profile == 66 )
	{
		//A.2.1	Baseline profile
	} else
	if ( av->H264Param.profile == 77 )
	{
		//A.2.2	Main profile

	} else
	if ( av->H264Param.profile == 88 )
	{
		//A.2.3	Extended profile

	} else
	if ( av->H264Param.profile == 83 )
	{
	} else
	if ( av->H264Param.profile == 100 )
	{
		//A.2.4	High profile

	} else
	if ( av->H264Param.profile == 110 )
	{
		//A.2.5	High 10 profile

	} else
	if ( av->H264Param.profile == 122 )
	{
		//A.2.6	High 4:2:2 profile

	} else
	if ( av->H264Param.profile == 144 )
	{
		//A.2.7	High 4:4:4 profile
	} else
	{
		printf( "unkonw profile %d\n", av->H264Param.profile );
		return -1;
	}

	if ( av->H264Param.level == 10 ) //1
	{
		av->H264Param.cbr = 64000;
	} else
	if ( av->H264Param.level == 11 && av->H264Param.constraint_set2 ) //1.b
	{
		av->H264Param.cbr = 128000;
	} else
	if ( av->H264Param.level == 11 ) //1.1
	{
		av->H264Param.cbr = 192000;
	} else
	if ( av->H264Param.level == 12 ) //1.2
	{
		av->H264Param.cbr = 384000;
	} else
	if ( av->H264Param.level == 13 ) //1.3
	{
		av->H264Param.cbr = 768000;
	} else
	if ( av->H264Param.level == 20 ) //2.0
	{
		av->H264Param.cbr = 2000000;
	} else
	if ( av->H264Param.level == 21 ) //2.1
	{
		av->H264Param.cbr = 4000000;
	} else
	if ( av->H264Param.level == 22 ) //2.2
	{
		av->H264Param.cbr = 4000000;
	} else
	if ( av->H264Param.level == 30 ) //3.0
	{
		av->H264Param.cbr = 10000000;
	} else
	if ( av->H264Param.level == 31 ) //3.1
	{
		av->H264Param.cbr = 14000000;
	} else
	if ( av->H264Param.level == 32 ) //3.2
	{
		av->H264Param.cbr = 20000000;
	} else
	if ( av->H264Param.level == 40 ) //4.0
	{
		av->H264Param.cbr = 20000000;
	} else
	if ( av->H264Param.level == 41 ) //4.1
	{
		av->H264Param.cbr = 50000000;
	} else
	if ( av->H264Param.level == 42 ) //4.2
	{
		av->H264Param.cbr = 50000000;
	} else
	if ( av->H264Param.level == 50 ) //5.0
	{
		av->H264Param.cbr = 135000000;
	} else
	if ( av->H264Param.level == 51 ) //5.1
	{
		av->H264Param.cbr = 24000000;
	} else
	{
		av->H264Param.cbr = -1;
		printf( "unknow level %d\n", av->H264Param.level );
		return -1;
	} 

	return 1;
}

////////////////////////////////////////////////////////////////////////////////////////////

int FillSageDesc( unsigned char* ptr, int bytes, unsigned char write_tag, char* data, int data_len )
{
	unsigned char tag;
	unsigned int len;
	int i;
	if ( ptr == NULL || bytes < 2  ) return 0;

	//skip desc to end;
	tag = ptr[0];
	len = ((unsigned char)ptr[1]);
	i = 0;
	while ( ( 0xff != tag && 0x0 != tag ) && i+2+len < (unsigned int)bytes )
	{
		i += 2+len;
		tag = ptr[i];
		if ( tag == 0 || tag == 0xff ) break;
		len = ((unsigned char)ptr[i+1]);
	}
	//append a new tag
	if ( ( tag == 0 || tag == 0xff ) && i+2 + data_len < bytes )
	{
		ptr[i] = write_tag;
		ptr[i+1] = data_len;
		memcpy( ptr+i+2, data, data_len );
		if ( i+2 + data_len+1 < bytes )
			ptr[i+2+data_len] = 0x0ff;
		return 1;
	}

	return 0;
}

char* ParserDescTag( const unsigned char* ptr, int bytes, unsigned char search_tag )
{
	unsigned char tag;
	unsigned int len;
	int i;

	if ( ptr == NULL || *ptr == 0 || *ptr == 0xff || bytes < 2  ) return NULL;

	tag = ptr[0];
	len = ((unsigned char)ptr[1]);
	i = 0;
	while ( search_tag != tag && i+2+len < (unsigned int)bytes )
	{
		i += 2+len;
		tag = ptr[i];
		if ( tag == 0 || tag == 0xff ) break;
		len = ((unsigned char)ptr[i+1]);
		
	}

	if ( i > bytes || search_tag != tag  )
		return 0;

	return (char*)ptr+i;
}

int  sage_stricmp ( const char * dst,  const char * src )
{
    int f, l;
    do {
        if ( ((f = (unsigned char)(*(dst++))) >= 'A') && (f <= 'Z') )
            f -= 'A' - 'a';
        if ( ((l = (unsigned char)(*(src++))) >= 'A') && (l <= 'Z') )
            l -= 'A' - 'a';
    } while ( f && (f == l) );

    return(f - l);
}

#ifdef WIN32
#include <poppack.h>
#endif
