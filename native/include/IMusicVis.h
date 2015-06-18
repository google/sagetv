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

#ifndef __H_IMUSICVIS_H
#define __H_IMUSICVIS_H

#ifdef __cplusplus
extern "C" {
#endif

struct MusicVisData
{
	int numBufs;
	int lastWritten;
	REFERENCE_TIME* times; // numBufs in length
	PBYTE ampData; // 2048*numBufs in length
};
typedef struct MusicVisData MusicVisData;


// {C16E87D7-2A18-4823-9C04-08394DFA9C1A}
DEFINE_GUID(IID_IMusicVis, 
0xc16e87d7, 0x2a18, 0x4823, 0x9c, 0x4, 0x8, 0x39, 0x4d, 0xfa, 0x9c, 0x1a);
  

//----------------------------------------------------------------------------
// IMusicVis
//----------------------------------------------------------------------------
DECLARE_INTERFACE_(IMusicVis, IUnknown)
{
	STDMETHOD(put_MusicVisData) (THIS_
					   MusicVisData* pMusicVisData
				) PURE;
};
//----------------------------------------------------------------------------

#ifdef __cplusplus
}
#endif

#endif // __H_IMUSICVIS_H
