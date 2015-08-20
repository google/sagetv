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
#ifndef __MCPROP_H__
#define __MCPROP_H__

typedef struct {
    char *name;
    char *value;
    int len; // 0:RO, 1+: len that can be modified
}MCProperty;

extern char pubkey[256];
extern char symkey[128];
extern int encryptenabled;
extern char videomode[256];
extern char aspectProp[16];
extern char firmwareversion[128];
#ifdef EM86
extern char hdmimode[16];
extern char advancedAspect[256];
extern char defaultadvancedAspect[256];
extern char audioout[16];
extern char standardmodes[4096];
extern char digitalmodes[4096];
extern char tvstandard[16];
extern char subtitlecallback[16];
#endif
#ifdef EM8634
extern char cachedauth[33];
extern char cachedsetauth[128];
#endif
extern char gfxflip[16];
#ifdef EM8654
extern char advdeintstr[16];
#endif

// Note: creating buffers directly with malloc is not a good idea...
MCProperty MCProperties [] = {
#ifdef EM86
{ "GFX_BLENDMODE","POSTMULTIPLY", 0 },
#endif
#ifdef STB
{ "GFX_BLENDMODE","PREMULTIPLY", 0 },
#endif
#ifdef GLES2
{ "GFX_BLENDMODE","PREMULTIPLY", 0 },
#endif

{ "GFX_DRAWMODE","UPDATE", 0 },
{ "GFX_TEXTMODE","NONE", 0 },
#ifdef EM86
#ifdef EM8634
{ "GFX_BITMAP_FORMAT","RAW32,RAW8,PNG,JPG,SCALE,DIRECT", 0 }, 
#else
{ "GFX_BITMAP_FORMAT","RAW32,RAW8,PNG,JPG", 0 }, 
#endif
#else
{ "GFX_BITMAP_FORMAT","RAW32", 0 },
#endif
{ "GFX_SUPPORTED_ASPECTS","4:3,16:9", 0 },
{ "GFX_ASPECT",aspectProp, 15 },
#ifdef EM86
{ "GFX_SUPPORTED_RESOLUTIONS", standardmodes, 0 },
{ "GFX_SUPPORTED_RESOLUTIONS_DIGITAL", digitalmodes, 0 },
{ "GFX_SCALING", "HARDWARE" },
#endif
#ifdef STB
{ "GFX_SUPPORTED_RESOLUTIONS","720x480i@59.94;720x576i@50", 0 },
#endif
{ "GFX_RESOLUTION",videomode, 0 },
{ "GFX_COMPOSITE","BLEND", 0 },
{ "GFX_COLORKEY","00000000", 0 },
#ifdef EM86
#ifdef EM8634
{ "VIDEO_CODECS","MPEG2-VIDEO,MPEG2-VIDEO@HL,MPEG1-VIDEO,MPEG4-VIDEO,DIVX3,MSMPEG4,FLASHVIDEO,H.264,WMV9,VC1,MJPEG", 0 },
#else
{
"VIDEO_CODECS","MPEG2-VIDEO,MPEG2-VIDEO@HL,MPEG1-VIDEO,MPEG4-VIDEO,DIVX3,MSMPEG4,H.264,WMV9,VC1", 0 },
#endif
#endif
#ifdef STB
{ "VIDEO_CODECS","MPEG2-VIDEO", 0 },
#endif
#ifdef EM86
{ "AUDIO_CODECS","MPG1L2,MPG1L3,AC3,AAC,AAC-HE,WMA,FLAC,VORBIS,PCM,DTS,DCA,"
   "PCM_S16LE,WMA8,ALAC,WMAPRO,0X0162,DolbyTrueHD,DTS-HD,DTS-MA,EAC3,EC-3", 0 },
// WMA9Lossless,0X0163
#endif
#ifdef STB
{ "AUDIO_CODECS","MPG1L2,MPG1L3,AC3", 0 },
#endif
{ "AV_CONTAINERS","", 0 },
#ifdef EM86
{ "CRYPTO_ALGORITHMS","RSA,Blowfish",0 },
{ "CRYPTO_PUBLIC_KEY", pubkey, 256},
{ "CRYPTO_SYMMETRIC_KEY", symkey, 128},
{ "CRYPTO_EVENTS_ENABLE", (char *) &encryptenabled, 1},
#endif
{ "IR_PROTOCOL", "RC5"},
#ifdef STB
{ "PUSH_AV_CONTAINERS", "MPEG2-PS" },
#endif
#ifdef EM86
{ "PUSH_AV_CONTAINERS", "MPEG2-PS,MPEG2-TS,MPEG1-PS" },
{ "GFX_SURFACES", "TRUE"},
#endif
#ifdef EM86
{ "STREAMING_PROTOCOLS", "file,stv"},
{ "PULL_AV_CONTAINERS", "AVI,FLASHVIDEO,Quicktime,Ogg,MP3,AAC,WMV,ASF,FLAC,MATROSKA,WAV,AC3" },
#endif
#ifdef EM86
{ "GFX_HDMI_MODE", hdmimode, 0},
{ "VIDEO_ADVANCED_ASPECT", advancedAspect, 0},
{ "VIDEO_ADVANCED_ASPECT_LIST", "Source;Fill|blackstrip=0,0|cutstrip=0,0;"
    "ZoomA|blackstrip=0,4096;"
    "ZoomB|source=2048,2048,3072,3072,FrontEdgeToCenter,FrontEdgeToCenter,Relative,Relative,Relative,Relative;"
    "ZoomC|source=2048,2048,4096,3072,FrontEdgeToCenter,FrontEdgeToCenter,Relative,Relative,Relative,Relative;"
    "Fill Wide|source=2048,2048,3072,4096,FrontEdgeToCenter,FrontEdgeToCenter,Relative,Relative,Relative,Relative"
    "|blackstrip=0,0|cutstrip=0,0;" },
#ifdef EM8654
{ "AUDIO_OUTPUTS", "Analog;Digital;HDMI;HDMIHBR"},
#else
{ "AUDIO_OUTPUTS", "Analog;Digital;HDMI"},
#endif
{ "AUDIO_OUTPUT", audioout, 0},
{ "DEFAULT_VIDEO_ADVANCED_ASPECT", defaultadvancedAspect, 0},
{ "TV_STANDARD", tvstandard, 0},
#endif
{ "FIRMWARE_VERSION", firmwareversion, 0},
#ifdef EM86
{ "GFX_HIRES_SURFACES", "TRUE", 0},
{ "GFX_VIDEO_UPDATE", "TRUE", 0},
#ifdef EM8634
{ "GFX_YUV_IMAGE_CACHE", "UNIFIED", 0},
#else
{ "GFX_YUV_IMAGE_CACHE", "SEPARATE", 0},
#endif
#endif

#ifdef EM8634
{ "REMOTE_FS", "FALSE", 0}, // Until the server side is fixed...
#else
{ "REMOTE_FS", "FALSE", 0},
#endif
{ "FS_XFER_BUFFER_SIZE", "16384", 0},
#ifdef GLES2
{ "GFX_SURFACES", "TRUE", 0},
#endif

#ifdef EM86
{ "GFX_SUBTITLES", "TRUE", 0},
{ "SUBTITLES_CALLBACKS", subtitlecallback, 0 },
{ "PUSH_BUFFER_LIMIT", "524288", 0},
#endif
#ifdef EM8634
{ "AUTH_CACHE", "TRUE", 0},
{ "GET_CACHED_AUTH", cachedauth, 32},
{ "SET_CACHED_AUTH", cachedsetauth, 0},
#endif
{ "FORCED_MEDIA_RECONNECT", "TRUE", 0},
{ "GFX_VIDEO_MASKS", "31", 0}, 
#ifdef EM8634
{ "MUSIC_VISUALISATION", "TRUE", 0},
{ "GFX_DIFFUSE_TEXTURES", "ALPHA", 0},
{ "GFX_NEGSCALING", "VERTICAL", 0 },
{ "GFX_CURSORPROP", "TRUE", 0},
#endif
{ "GFX_FLIP", gfxflip, 0}, // We can flip buffer
{ "GFX_TEXTURE_BATCH_LIMIT", "8192", 0 },
#ifdef EM8654
{ "DEINTERLACE_CONTROL", "TRUE", 0 },
{ "ADVANCED_DEINTERLACING", advdeintstr, 0 },
#endif
{ "OPENURL_INIT", "TRUE", 0 },
{ "FRAME_STEP", "TRUE", 0},

// TEST fixed placeshifter rate
//{ "FIXED_PUSH_MEDIA_FORMAT", "videobitrate=650000;audiobitrate=80000;gop=300;bframes=3;fps=30;resolution=CIF;"},
{ NULL, NULL, 0 }
};

#endif // __MCPROP_H__
