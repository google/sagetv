/*
 *	CEncoder wrapper for LAME
 *
 *	Copyright (c) 2000 Marie Orlova, Peter Gubanov, Elecard Ltd.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

#include <streams.h>
//#include "lame.h"
#include "avcodec.h"
#include "Encoder2.h"

#define	SIZE_OF_SHORT 2
#define MPEG_TIME_DIVISOR	90000

/////////////////////////////////////////////////////////////
// MakePTS - converts DirectShow refrence time
//			 to MPEG time stamp format
/////////////////////////////////////////////////////////////
/*LONGLONG MakePTS(REFERENCE_TIME rt)
{
	return llMulDiv(CRefTime(rt).GetUnits(),MPEG_TIME_DIVISOR,UNITS,0);
}*/

//////////////////////////////////////////////////////////////////////
// Construction/Destruction
//////////////////////////////////////////////////////////////////////
CEncoder2::CEncoder2() :
	m_bInpuTypeSet(FALSE),
	m_bOutpuTypeSet(FALSE),
	m_rtLast(0),
	m_bLast(FALSE),
	m_nCounter(0),
	m_nPos(0),
	m_pPos(NULL),
//	pgf(NULL),
	m_dwForceSampleRate(0)
{
}

CEncoder2::~CEncoder2()
{
	Close();
}

//////////////////////////////////////////////////////////////////////
// SetInputType - check if given input type is supported
//////////////////////////////////////////////////////////////////////
HRESULT CEncoder2::SetInputType(LPWAVEFORMATEX lpwfex)
{
	CAutoLock l(this);

	if (lpwfex->wFormatTag			== WAVE_FORMAT_PCM) 
	{
 		if (lpwfex->nChannels			== 1 || 
			lpwfex->nChannels			== 2	 ) 
		{
 			if (lpwfex->nSamplesPerSec		== 48000 ||
				lpwfex->nSamplesPerSec		== 44100 ||
				lpwfex->nSamplesPerSec		== 32000 ||
				lpwfex->nSamplesPerSec		== 24000 ||
				lpwfex->nSamplesPerSec		== 22050 ||
				lpwfex->nSamplesPerSec		== 16000 ) 
			{
				if (lpwfex->wBitsPerSample		== 16)
				{
					if (m_dwForceSampleRate)
						lpwfex->nSamplesPerSec = m_dwForceSampleRate;
					memcpy(&m_wfex, lpwfex, sizeof(WAVEFORMATEX));
					m_bInpuTypeSet = true;

					return S_OK;
				}
			}
		}
	}
	m_bInpuTypeSet = false;
	return E_INVALIDARG;
}

//////////////////////////////////////////////////////////////////////
// SetOutputType - try to initialize encoder with given output type
//////////////////////////////////////////////////////////////////////
HRESULT CEncoder2::SetOutputType(MPEG_ENCODER_CONFIG &mabsi)
{
	CAutoLock l(this);

	m_mabsi = mabsi;
	m_bOutpuTypeSet = true;
	return S_OK;
}

//////////////////////////////////////////////////////////////////////
// SetDefaultOutputType - sets default MPEG audio properties according
// to input type
//////////////////////////////////////////////////////////////////////
HRESULT CEncoder2::SetDefaultOutputType(LPWAVEFORMATEX lpwfex)
{
	CAutoLock l(this);

	if(lpwfex->nChannels == 1)
		m_mabsi.dwChMode = MONO;

	if((lpwfex->nSamplesPerSec < m_mabsi.dwSampleRate) || (lpwfex->nSamplesPerSec % m_mabsi.dwSampleRate != 0))
		m_mabsi.dwSampleRate = lpwfex->nSamplesPerSec;

	return S_OK;
}

//////////////////////////////////////////////////////////////////////
// Init - initialized or reiniyialized encoder SDK with given input 
// and output settings
//
// NOTE: these should all be replaced with calls to the API functions
// lame_set_*().  Applications should not directly access the 'pgf'
// data structure. 
//
//////////////////////////////////////////////////////////////////////
HRESULT CEncoder2::Init()
{
	CAutoLock l(this);

  	/* Initialize avcodec lib */
	avcodec_init();
	
	/* Our "all" is only the mpeg 1 layer 2 audio anyway... */
	avcodec_register_all();

	codec = avcodec_find_encoder(CODEC_ID_MP2);
	if (!codec) 
	{
//		fprintf(logfile,"Couldn't find codec\n");
//	    fclose(logfile);
		return E_OUTOFMEMORY;
    }

//	fprintf(logfile,"Allocating context\n");
	c= avcodec_alloc_context();

	/* put sample parameters */
	if (m_mabsi.dwBitrate > 384)
	{
		m_mabsi.dwBitrate = 128;
	}
	c->bit_rate = m_mabsi.dwBitrate * 1000;
	c->sample_rate = m_wfex.nSamplesPerSec;
	c->channels = m_wfex.nChannels;
	DbgLog((LOG_TRACE, 1, TEXT("Using bitrate=%d sampling=%d"), (LONG)c->bit_rate, (LONG)c->sample_rate));

	/* open it */
	if (avcodec_open(c, codec) < 0) 
	{
//		fprintf(logfile,"Could not open codec\n");
//	    fclose(logfile);
        return E_OUTOFMEMORY;
	}

	/* the codec gives us the frame size, in samples */
	frame_size = c->frame_size;
	DbgLog((LOG_TRACE, 1, TEXT("FrameSize=%d\r\n"), (LONG)frame_size));
	samples = (UINT8 *) malloc(frame_size * 2 * c->channels);
	outbuf_size = 10000;
	outbuf = (UINT8 *) malloc(outbuf_size);
	filled=0;
	return S_OK;
}

//////////////////////////////////////////////////////////////////////
// Close - closes encoder
//////////////////////////////////////////////////////////////////////
HRESULT CEncoder2::Close()
{
	CAutoLock l(this);

/*	if(pgf) {
		lame_close(pgf);
		pgf = NULL;
	}*/
	free(c);
	c = NULL;
	return S_OK;
}

//////////////////////////////////////////////////////////////////////
// Encode - encodes data placed on pSrc with size dwSrcSize and returns
// encoded data in pDst pointer. REFERENCE_TIME rt is a DirectShow refrence
// time which is being converted in MPEG PTS and placed in PES header.
//////////////////////////////////////////////////////////////////////
HRESULT CEncoder2::Encode(LPVOID pSrc, DWORD dwSrcSize, LPVOID pDst, LPDWORD lpdwDstSize, REFERENCE_TIME rt)
{
	CAutoLock l(this);
	BYTE temp_buffer[OUTPUT_BUFF_SIZE];

	*lpdwDstSize = 0;// If data are insufficient to be converted, return 0 in lpdwDstSize

	LPBYTE pData = temp_buffer;
	LONG lData = OUTPUT_BUFF_SIZE;

	int nsamples = dwSrcSize/(m_wfex.wBitsPerSample*m_wfex.nChannels/8);

	if (c) {
		DbgLog((LOG_TRACE, 1, TEXT("Encode filled=%d srcSize=%d"), filled, dwSrcSize));
		int left=0;
		int cpylen=dwSrcSize;
		if(filled+dwSrcSize>frame_size*4)
		{
			cpylen=frame_size*4-filled;
			left=filled+dwSrcSize-frame_size*4;
		}
		memcpy(&samples[filled], pSrc, cpylen);
		pSrc = ((LPBYTE) pSrc) + cpylen;
		filled+=cpylen;
		DbgLog((LOG_TRACE, 1, TEXT("Encode 2 filled=%d cpylen=%d left=%d lData=%d"), filled, cpylen, left, lData));
		if(filled==frame_size*4)
		{
			do
			{
// change the endian DEBUG TEST
				/* // This produces a pure tone, and it works!
int x = 0;
static int foo = 0;
static int fooadjust = 1;
for (; x < filled; x+=2)
{
	samples[x] = foo;
	samples[x+1] = foo;
	foo += 2*fooadjust;
	if (fooadjust > 0)
	{
		if (foo > 120)
		{
			fooadjust = -1;
		}
	}
	else
	{
		if (foo < -120)
		{
			fooadjust = 1;
		}
	}
}*/
				lData = avcodec_encode_audio(c, pData, OUTPUT_BUFF_SIZE, (short*) samples);
				filled=0;
				if (rt > 0)
				{
					// Drop audio frame
					rt = 0;
					lData = 0;
					DbgLog((LOG_TRACE, 1, TEXT("Dropping audio frame")));
				}
				else
				{
					memcpy(((LPBYTE)pDst) + (*lpdwDstSize),pData,lData);
					*lpdwDstSize += lData;
					if (rt < 0)
					{
						DbgLog((LOG_TRACE, 1, TEXT("Inserting audio frame")));
						// Insert extra audio frame
						rt = 0;
						memcpy(((LPBYTE)pDst) + (*lpdwDstSize),pData,lData);
						*lpdwDstSize += lData;
					}
				}

				
				if (left >= frame_size * 4)
				{
					// More than one chunk in the input so process again
					cpylen=frame_size*4-filled;
					left=filled+left-frame_size*4;
					memcpy(&samples[filled], pSrc, cpylen);
					pSrc = ((LPBYTE) pSrc) + cpylen;
					filled += cpylen;
					DbgLog((LOG_TRACE, 1, TEXT("Encode 4 filled=%d cpylen=%d left=%d lData=%d dwDstSize=%d"), filled, cpylen, left, lData, *lpdwDstSize));
				}
			} while (filled == frame_size*4);

			DbgLog((LOG_TRACE, 1, TEXT("Encode 5 filled=%d cpylen=%d left=%d lData=%d dwDstSize=%d"), filled, cpylen, left, lData, *lpdwDstSize));
			memcpy(samples, pSrc, left);
			filled+=left;
		}
		else
			lData = 0;

//		lData = lame_encode_buffer_interleaved(pgf,(short*)pSrc,nsamples,pData,lData);

/*		if(m_mabsi.dwPES && lData > 0)
		{
			// Write PES header
			Reset();
			CreatePESHdr((LPBYTE)pDst, MakePTS(m_rtLast), lData);
			pDst = (LPBYTE)pDst + 0x0e;			// add PES header size

			m_bLast = false;
			m_rtLast = rt;
		}
		else*/
			m_bLast = true;

	}
	else {
		*lpdwDstSize = 0;
	}


	return S_OK;
}

//
// Finsh - flush the buffered samples. REFERENCE_TIME rt is a DirectShow refrence
// time which is being converted in MPEG PTS and placed in PES header.
//
HRESULT CEncoder2::Finish(LPVOID pDst, LPDWORD lpdwDstSize)
{
/*	CAutoLock l(this);
	BYTE temp_buffer[OUTPUT_BUFF_SIZE];

	*lpdwDstSize = 0;// If data are insufficient to be converted, return 0 in lpdwDstSize

	LPBYTE pData = temp_buffer;
	LONG lData = OUTPUT_BUFF_SIZE;

#pragma message (REMIND("Finish encoding right!"))
	if (pgf) {
		lData = lame_encode_flush(pgf,pData,lData);

		if(m_mabsi.dwPES && lData > 0)
		{
			// Write PES header
			Reset();
			CreatePESHdr((LPBYTE)pDst, MakePTS(m_rtLast), lData);
			pDst = (LPBYTE)pDst + 0x0e;			// add PES header size
		}
		m_bLast = true;

		memcpy(pDst,pData,lData);
	}
	else
		lData = 0;

	*lpdwDstSize = lData;
*/
	return S_OK;
}

//////////////////////////////////////////////////////////////////////
// PES headers routines
//////////////////////////////////////////////////////////////////////
// WriteBits - writes nVal in nBits bits on m_pPos address 
//////////////////////////////////////////////////////////////////////
void CEncoder2::WriteBits(int nBits, int nVal)
{
	int nCounter = m_nCounter + nBits,
		nPos = (m_nPos << nBits)|(nVal & (~( (~0L) << nBits)));
	while(nCounter >= 8)
	{
		nCounter -= 8;
		*m_pPos++ = (BYTE)(nPos >> nCounter) & 0xff;
	}
	m_nCounter = nCounter;
	m_nPos = nPos;
}


//////////////////////////////////////////////////////////////////////
// Reset - resets all PES header stuff
//////////////////////////////////////////////////////////////////////
void CEncoder2::Reset()
{
	m_nCounter = 0;
	m_nPos  = 0;
	m_pPos = NULL;
}
