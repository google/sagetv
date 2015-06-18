// TODO : fixed point fix...

/**
 * projectM -- Milkdrop-esque visualisation SDK
 * Copyright (C)2003-2004 projectM Team
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
 * Takes sound data from wherever and returns beat detection values
 * Uses statistical Energy-Based methods. Very simple
 * 
 * Some stuff was taken from Frederic Patin's beat-detection article,
 * you'll find it online
 */

#include <stdlib.h>
#include <stdio.h>

#include "wipemalloc.h"

#include "Common.hpp"
#include "PCM.hpp"
#include <cmath>
#include "BeatDetect.hpp"

DLLEXPORT BeatDetect::BeatDetect(PCM *pcm) {
  int x,y; 

  this->pcm=pcm;

  this->vol_instant=0;
  this->vol_history=0;

  for (y=0;y<80;y++)
    {
      this->vol_buffer[y]=0;
    }

  this->beat_buffer_pos=0;

  for (x=0;x<32;x++) {
      this->beat_instant[x]=mathval(0.0f);
      this->beat_history[x]=mathval(0.0f);
      this->beat_val[x]=mathval(1.0f);
      this->beat_att[x]=mathval(1.0f);
      this->beat_variance[x]=mathval(0.0f);
      for (y=0;y<80;y++) {
	    this->beat_buffer[x][y]=mathval(0.0f);
	    }
    }

    this->treb = 0;
    this->mid = 0;
    this->bass = 0;
    this->vol_old = 0;
    this->beat_sensitivity = mathval(10.00f);
    this->treb_att = 0;
    this->mid_att = 0;
    this->bass_att = 0;
    this->vol = 0;

  
  }

DLLEXPORT BeatDetect::~BeatDetect() 
{

}

void BeatDetect::reset() {
  this->treb = 0;
  this->mid = 0;
  this->bass = 0;
  this->treb_att = 0;
  this->mid_att = 0;
  this->bass_att = 0;
  }

void BeatDetect::detectFromSamples() {
    vol_old = vol;
    bass=0;mid=0;treb=0;

    getBeatVals(pcm->pcmdataL,pcm->pcmdataR);
  }

void BeatDetect::getBeatVals( mathtype *vdataL,mathtype *vdataR ) 
{
    int linear=0;
    int x,y;
    mathtype temp2=0;

    vol_instant=0;
    for ( x=0;x<16;x++)
    {
        beat_instant[x]=0;
        for ( y=linear*2;y<(linear+8+x)*2;y++)
        {
            beat_instant[x]+=mathmul(((mathmul(vdataL[y],vdataL[y]))+(mathmul(vdataR[y],vdataR[y]))),(mathdiv(mathval(1.0f),(mathval(8)+mathval(x))))); 
            vol_instant+=mathmul(((mathmul(vdataL[y],vdataL[y]))+(mathmul(vdataR[y],vdataR[y]))),(mathval(1.0f/512.0f)));

        }

        linear=y/2;
        beat_history[x]-=mathmul((beat_buffer[x][beat_buffer_pos]),mathval(.0125f));
        beat_buffer[x][beat_buffer_pos]=beat_instant[x];
        beat_history[x]+=mathmul((beat_instant[x]),mathval(.0125f));

        if(beat_history[x]!=0)
        {
            beat_val[x]=mathdiv((beat_instant[x]),(beat_history[x]));
        }
        else
        {
            beat_val[x]=0;
        }

        if(beat_history[x]!=0)
        {
            beat_att[x]+=mathdiv((beat_instant[x]),(beat_history[x]));
        }
        else
        {
            beat_att[x]=0;
        }
    }

    vol_history-=mathmul((vol_buffer[beat_buffer_pos]),mathval(.0125f));
    vol_buffer[beat_buffer_pos]=vol_instant;
    vol_history+=mathmul((vol_instant),mathval(.0125f));

    mid=0;
    for(x=1;x<10;x++)
    {
        mid+=(beat_instant[x]);
        temp2+=(beat_history[x]);
    }

    if(temp2!=0)
    {
        mid=mathdiv(mid,(mathmul(mathval(1.5),temp2)));
    }
    else
    {
        mid=0;
    }
    temp2=0;
    treb=0;
    for(x=10;x<16;x++)
    {
        treb+=(beat_instant[x]);
        temp2+=(beat_history[x]);
    }

    if(temp2!=0)
    {
        treb=mathdiv(treb,mathmul(mathval(1.5f),temp2));
    }
    else
    {
        treb=0;
    }

    if(vol_history!=0)
    {
        vol=mathdiv(vol_instant,mathmul(mathval(1.5f),vol_history));
    }
    else
    {
        vol_history=0;
    }

    if(beat_history[0]!=0)
    {
        bass=mathdiv((beat_instant[0]),mathmul(mathval(1.5f),beat_history[0]));
    }
    else
    {
        bass=0;
    }
    
    /*if ( projectM_isnan( treb ) ) {
        treb = 0.0f;
    }
    if ( projectM_isnan( mid ) ) {
        mid = 0.0f;
    }
    if ( projectM_isnan( bass ) ) {
        bass = 0.0f;
    }*/
    treb_att=mathmul(mathval(.6f), treb_att) + mathmul(mathval(.4f), treb);
    mid_att=mathmul(mathval(.6f), mid_att) + mathmul(mathval(.4f), mid);
    bass_att=mathmul(mathval(.6f), bass_att) + mathmul(mathval(.4f), bass);

    if(bass_att>mathval(100))bass_att=mathval(100);
    if(bass >mathval(100))bass=mathval(100);
    if(mid_att>mathval(100))mid_att=mathval(100);
    if(mid >mathval(100))mid=mathval(100);
    if(treb_att>mathval(100))treb_att=mathval(100);
    if(treb >mathval(100))treb=mathval(100);
    if(vol>mathval(100))vol=mathval(100);

    // *vol=(beat_instant[3])/(beat_history[3]);
    beat_buffer_pos++;
    if( beat_buffer_pos>79)beat_buffer_pos=0;

}


