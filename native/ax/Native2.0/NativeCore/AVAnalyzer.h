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

#ifndef FORMAT_ANALYZER_H
#define FORMAT_ANALYZER_H

#ifdef __cplusplus
extern "C" {
#endif

#define UNKNOWN_MEDIA		0
#define SAGETV_TV_RECORDING	1
#define BLUERAY_DVD_MEDIA	4
#define VOB_DVD_MEDIA		2

void ReleaseAVElementData( AV_ELEMENT *pAVEelement );
int  AnylyzeAVElement( AV_ELEMENT *pAVEelement, ES_ELEMENT *pESEelement, TS_ELEMENT *pTSEelement, 
					  const unsigned char* pData, int nBytes, int MediaType ); 

int GetAudioSoundChannelNum( AV_ELEMENT *pAVElmnt );
int GetAudioSoundBitRate( AV_ELEMENT *pAVElmnt );

//debug utility
void _prints_av_elmnt( AV_ELEMENT* elmnt, int slot_index, int process_bytes );

#ifdef __cplusplus
}
#endif


#endif

