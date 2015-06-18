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

#include "../../include/guids.h"
#include <streams.h>
#include "DShowCapture.h"
#include "DShowUtilities.h"
#include "jni-util.h"
#include <initguid.h>
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
#include "ATITheaterdef.h"
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
#include "ATITheater.h"

void configureATIEncoder(DShowCaptureInfo* pCapInfo, SageTVMPEG2EncodingParameters *pythonParams,
							JNIEnv *env)
{
	if ( pCapInfo == NULL || pCapInfo->pEncoder == NULL  )
		return;
	//slog((env, "try configureATIEncoder is running for %s #%d\r\n", pCapInfo->videoCaptureFilterName, pCapInfo->videoCaptureFilterNum));
/*
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - START
	IRioMPEGVideo *pRioMPEGVideo = NULL;
	IRioMPEGAudio *pRioMPEGAudio = NULL;
	KS_MPEG_AUDIO_BITRATE_PARAM AudioBitrate;
	KS_VIDEO_MPEG_BITRATE_PARAM VideoBitrate;
	HRESULT hr = pCapInfo->pEncoder->QueryInterface( IID_IRioMPEGVideo, (PVOID *)&pRioMPEGVideo );
	if (SUCCEEDED(hr))
	{
		if ( pythonParams->videobitrate <= 1500000 )
			VideoBitrate.enBitrate = MPEG_VIDEO_BITRATE_1_5; // 1.5 Mbps 
		else
		if ( pythonParams->videobitrate  > 1500000 && pythonParams->videobitrate <= 2000000 )
			VideoBitrate.enBitrate = MPEG_VIDEO_BITRATE_2; // 2 Mbps 
		else
		if ( pythonParams->videobitrate  > 2000000 && pythonParams->videobitrate <= 3000000 )
			VideoBitrate.enBitrate = MPEG_VIDEO_BITRATE_3; // 2 Mbps
		else
		if ( pythonParams->videobitrate  > 3000000 && pythonParams->videobitrate <= 4000000 )
			VideoBitrate.enBitrate = MPEG_VIDEO_BITRATE_4; // 4 Mbps 
		else
		if ( pythonParams->videobitrate  > 4000000 && pythonParams->videobitrate <= 5000000 )
			VideoBitrate.enBitrate = MPEG_VIDEO_BITRATE_5; // 5 Mbps 
		else
		if ( pythonParams->videobitrate  > 5000000 && pythonParams->videobitrate <= 6000000 )
			VideoBitrate.enBitrate = MPEG_VIDEO_BITRATE_6; // 6 Mbps 
		else
		if ( pythonParams->videobitrate  > 6000000 && pythonParams->videobitrate <= 7000000 )
			VideoBitrate.enBitrate = MPEG_VIDEO_BITRATE_7; // 7 Mbps 
		else
		if ( pythonParams->videobitrate  > 7000000 && pythonParams->videobitrate <= 8000000 )
			VideoBitrate.enBitrate = MPEG_VIDEO_BITRATE_8; // 8 Mbps 
		else
		if ( pythonParams->videobitrate  > 8000000  )
			VideoBitrate.enBitrate = MPEG_VIDEO_BITRATE_9; // 9 Mbps 


		if ( pythonParams->peakvideobitrate <= 4000000 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_4;
		else
		if ( pythonParams->peakvideobitrate > 4000000 && pythonParams->peakvideobitrate <= 5000000 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_5;
		else
		if ( pythonParams->peakvideobitrate > 5000000 && pythonParams->peakvideobitrate <= 6000000 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_6;
		else
		if ( pythonParams->peakvideobitrate > 6000000 && pythonParams->peakvideobitrate <= 7000000 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_7;
		else
		if ( pythonParams->peakvideobitrate > 7000000 && pythonParams->peakvideobitrate <= 8000000 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_8;
		else
		if ( pythonParams->peakvideobitrate > 8000000 && pythonParams->peakvideobitrate <= 9000000 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_9;
		else
		if ( pythonParams->peakvideobitrate > 9000000 && pythonParams->peakvideobitrate <= 10000000 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_10;
		else
		if ( pythonParams->peakvideobitrate > 10000000 && pythonParams->peakvideobitrate <= 11000000 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_11;
		else
		if ( pythonParams->peakvideobitrate > 11000000 && pythonParams->peakvideobitrate <= 12000000 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_12;
		else
		if ( pythonParams->peakvideobitrate > 12000000 && pythonParams->peakvideobitrate <= 13000000 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_13;
		else
		if ( pythonParams->peakvideobitrate > 13000000 && pythonParams->peakvideobitrate <= 14000000 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_14;
		else
		if ( pythonParams->peakvideobitrate > 14 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_PEAK_BITRATE_15;

		if ( pythonParams->vbr == 0 )
			VideoBitrate.enPeakBitrate = MPEG_VIDEO_BITRATE_CBR;
		
       hr = pRioMPEGVideo->SetVideoEncoderBitrate( VideoBitrate );
	   slog((env, "Set ATI Theater encoder video bitrate property rate:%d %d, vbr:%d, peak: %d %d, hr:%d\r\n",
					pythonParams->videobitrate, VideoBitrate.enBitrate, 
					pythonParams->vbr,
					pythonParams->peakvideobitrate, VideoBitrate.enPeakBitrate,	hr ));

		KS_VIDEO_MPEG_SPATIAL_FMT_PARAM Fmt;
		
		switch ( pythonParams->width )
		{ 
			case 320:
			case 352:
				Fmt.enFormat = SPATIAL_FMT_352x240;
				break;
			case 480:
			case 544:
				Fmt.enFormat = SPATIAL_FMT_544x480;
				break;
			case 640:
				Fmt.enFormat = SPATIAL_FMT_640x480;
				break;
			default:
				Fmt.enFormat = SPATIAL_FMT_720x480;
				break;
		}

		hr = pRioMPEGVideo->SetVideoEncoderSpatialFormat( Fmt );
	    slog((env, "Set ATI Theater encoder video format %d %d, hr:%d\r\n",
							pythonParams->width, Fmt.enFormat, hr ));
	   
	} else
	{
		slog((env, "is not ATI encoder.\r\n" ));
		SAFE_RELEASE(pRioMPEGVideo);
		return;
	}

	hr = pCapInfo->pEncoder->QueryInterface( IID_IRioMPEGAudio, (PVOID *)&pRioMPEGAudio );
	if (SUCCEEDED(hr))
	{
		switch (pythonParams->audiobitrate)
		{
			case 32:
			case 48:
			case 56:
			case 64:
				AudioBitrate.enBitrate = AUDIO_BITRATE_64KBPS;
				break;
			case 80:
			case 96:
				AudioBitrate.enBitrate = AUDIO_BITRATE_96KBPS;
				break;
			case 112:
				AudioBitrate.enBitrate = AUDIO_BITRATE_112KBPS;
				break;
			case 128:
				AudioBitrate.enBitrate = AUDIO_BITRATE_128KBPS;
				break;
			case 160:
				AudioBitrate.enBitrate = AUDIO_BITRATE_160KBPS;
				break;
			case 192:
				AudioBitrate.enBitrate = AUDIO_BITRATE_192KBPS;
				break;
			case 224:
				AudioBitrate.enBitrate = AUDIO_BITRATE_224KBPS;
				break;
			case 256:
				AudioBitrate.enBitrate = AUDIO_BITRATE_256KBPS;
				break;
			case 320:
				AudioBitrate.enBitrate = AUDIO_BITRATE_320KBPS;
				break;
			case 384:
				AudioBitrate.enBitrate = AUDIO_BITRATE_384KBPS;
				break;
			default:
				break;
		}
		hr = pRioMPEGAudio->SetAudioEncoderBitrate( AudioBitrate );
		slog((env, "Set ATI Theater encoder audio bitrate %d %d, hr:%d\r\n" ,
			           pythonParams->audiobitrate,AudioBitrate.enBitrate, hr) );

		KS_AUDIO_SAMPLE_RATE_PARAM AudioSampleRate;
		if (  pythonParams->audiosampling )
		{
			switch( pythonParams->audiosampling )
			{ 
			  case 32000: AudioSampleRate.enRate = AUDIO_SAMPLE_RATE_32; break;
			  case 48000: AudioSampleRate.enRate = AUDIO_SAMPLE_RATE_48; break;
			  case 44100: AudioSampleRate.enRate = AUDIO_SAMPLE_RATE_44_1; break;
			}
			hr = pRioMPEGAudio->SetAudioSampleRate( AudioSampleRate );
			slog((env, "Set ATI Theater encoder audio sample rate %d, hr:%d %d\r\n", 
				pythonParams->audiosampling, AudioSampleRate.enRate, hr) );
		}


	} else
	{
		slog((env, "is not ATI encoder.\r\n" ));
		SAFE_RELEASE(pRioMPEGVideo);
		SAFE_RELEASE(pRioMPEGAudio);
		return;
	}
	SAFE_RELEASE(pRioMPEGVideo);
	SAFE_RELEASE(pRioMPEGAudio);
 * PROPRIETARY CODE BLOCK DISABLED FOR OPEN SOURCE RELEASE - END
 */
}