/**
 * projectM -- Milkdrop-esque visualisation SDK
 * Copyright (C)2003-2007 projectM Team
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * See 'LICENSE.txt' included within this release
 *
 */
/**
 * $Id: BeatDetect.hpp,v 1.1 2009-09-17 14:26:03 jeanfrancois Exp $
 *
 * Beat detection class. Takes decompressed sound buffers and returns
 * various characteristics
 *
 * $Log: BeatDetect.hpp,v $
 * Revision 1.1  2009-09-17 14:26:03  jeanfrancois
 * First visualisation version
 *
 *
 */

#ifndef _BEAT_DETECT_H
#define _BEAT_DETECT_H

#include "PCM.hpp"

class BeatDetect
{
	public:
		/** Vars */
		mathtype beat_buffer[32][80],
		beat_instant[32],
		beat_history[32];
		mathtype beat_val[32],
		beat_att[32],
		beat_variance[32];
		int beat_buffer_pos;
		mathtype vol_buffer[80],
		vol_instant,
		vol_history;

		mathtype treb ;
		mathtype mid ;
		mathtype bass ;
		mathtype vol_old ;
		mathtype beat_sensitivity;
		mathtype treb_att ;
		mathtype mid_att ;
		mathtype bass_att ;
		mathtype vol;

		PCM *pcm;

		/** Methods */
		BeatDetect(PCM *pcm);
		~BeatDetect();
		void initBeatDetect();
		void reset();
		void detectFromSamples();
		void getBeatVals ( mathtype *vdataL, mathtype *vdataR );

};

#endif /** !_BEAT_DETECT_H */
