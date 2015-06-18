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
 * $Id: CustomWave.hpp,v 1.1 2009-09-17 14:26:03 jeanfrancois Exp $
 *
 * Encapsulation of a custom wave
 *
 * $Log: CustomWave.hpp,v $
 * Revision 1.1  2009-09-17 14:26:03  jeanfrancois
 * First visualisation version
 *
 */

#ifndef _CUSTOM_WAVE_H
#define _CUSTOM_WAVE_H

#define CUSTOM_WAVE_DEBUG 0

class CustomWave;
class GenExpr;
class PerPointEqn;
class Preset;

#include <vector>

#include "Common.hpp"
#include "Param.hpp"
#include "PerFrameEqn.hpp"

#include <map>

class CustomWave {
public:

     /** Empty constructor leaves wave in undefined state **/
     CustomWave() {}

     /** Initializes a custom wave id given the integer id */
     CustomWave(int id);

    /** Destructor is necessary so we can free the per point matrices **/
    ~CustomWave();

    /* Numerical id */
    int id;
    int per_frame_count;

    /* Parameter tree associated with this custom wave */
    std::map<std::string,Param*> param_tree;

    /* Engine variables */
    mathtype x; /* x position for per point equations */
    mathtype y; /* y position for per point equations */
    mathtype r; /* red color value */
    mathtype g; /* green color value */
    mathtype b; /* blue color value */
    mathtype a; /* alpha color value */
    mathtype * x_mesh;
    mathtype * y_mesh;
    mathtype * r_mesh;
    mathtype * b_mesh;
    mathtype * g_mesh;
    mathtype * a_mesh;
    mathtype * value1;
    mathtype * value2;
    mathtype * sample_mesh;
    
    bool enabled; /* if true then wave is visible, hidden otherwise */
    int samples; /* number of samples associated with this wave form. Usually powers of 2 */
    mathtype sample;
    bool bSpectrum; /* spectrum data or pcm data */
    bool bUseDots; /* draw wave as dots or lines */
    bool bDrawThick; /* draw thicker lines */
    bool bAdditive; /* add color values together */
    
    mathtype scaling; /* scale factor of waveform */
    mathtype smoothing; /* smooth factor of waveform */
    int sep;  /* no idea what this is yet... */
    
    /* stupid t variables */
    mathtype t1;
    mathtype t2;
    mathtype t3;
    mathtype t4;
    mathtype t5;
    mathtype t6;
    mathtype t7;
    mathtype t8;


    /* stupider q variables */
    mathtype q1;
    mathtype q2;
    mathtype q3;
    mathtype q4;
    mathtype q5;
    mathtype q6;
    mathtype q7;
    mathtype q8;

    mathtype v1,v2;

    /* Data structures to hold per frame and per point equations */
    std::map<std::string,InitCond*>  init_cond_tree;
    std::vector<PerFrameEqn*>  per_frame_eqn_tree;
    std::vector<PerPointEqn*>  per_point_eqn_tree;
    std::map<std::string,InitCond*>  per_frame_init_eqn_tree;

    /* Denotes the index of the last character for each string buffer */
    int per_point_eqn_string_index;
    int per_frame_eqn_string_index;
    int per_frame_init_eqn_string_index;

    int add_per_point_eqn(char * name, GenExpr * gen_expr);
    void evalCustomWaveInitConditions(Preset *preset);
    void evalPerPointEqns();

    void loadUnspecInitConds();
    
    void evalInitConds();

};

#endif /** !_CUSTOM_WAVE_H */
