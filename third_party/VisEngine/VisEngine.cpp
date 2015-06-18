#include "BeatDetect.hpp"
#include "PCM.hpp"
#include "Preset.hpp"
#include "TimeKeeper.hpp"
#include "BuiltinFuncs.hpp"
#include "Eval.hpp"
#include "wipemalloc.h"
#include "VisEngine.h"

static PCM * _pcm = NULL;
static BeatDetect * beatDetect = NULL;
static int gx=17;
static int gy=17;

static mathtype **origx2;  //original mesh 
static mathtype **origy2;

/// All readonly variables which are passed as inputs to presets
static PresetInputs presetInputs;
/// A preset outputs container used and modified by the "current" preset
static PresetOutputs presetOutputs;

static Preset *m_activePreset = NULL;

static TimeKeeper *timeKeeper;


static void setupPresetInputs(PresetInputs *inputs)
{
  inputs->ResetMesh();

  inputs->time = timeKeeper->GetRunningTime();
  inputs->bass = beatDetect->bass;
  inputs->mid = beatDetect->mid;
  inputs->treb = beatDetect->treb;
  inputs->bass_att = beatDetect->bass_att;
  inputs->mid_att = beatDetect->mid_att;
  inputs->treb_att = beatDetect->treb_att;
}

static void projectM_resetengine()
{

	presetOutputs.zoom=mathval(1.0f);
	presetOutputs.zoomexp=mathval( 1.0f);
	presetOutputs.rot=mathval( 0.0f);
	presetOutputs.warp=mathval( 0.0f);

	presetOutputs.sx=mathval( 1.0f);
	presetOutputs.sy=mathval( 1.0f);
	presetOutputs.dx=mathval( 0.0f);
	presetOutputs.dy=mathval( 0.0f);
	presetOutputs.cx=mathval( 0.5f);
	presetOutputs.cy=mathval( 0.5f);

	presetOutputs.decay=mathval(.98f);

	presetOutputs.wave_r=mathval( 1.0f);
	presetOutputs.wave_g=mathval( 0.2f);
	presetOutputs.wave_b=mathval( 0.0f);
	presetOutputs.wave_x=mathval( 0.5f);
	presetOutputs.wave_y=mathval( 0.5f);
	presetOutputs.wave_mystery=mathval( 0.0f);

	presetOutputs.ob_size=mathval( 0.0f);
	presetOutputs.ob_r=mathval( 0.0f);
	presetOutputs.ob_g=mathval( 0.0f);
	presetOutputs.ob_b=mathval( 0.0f);
	presetOutputs.ob_a=mathval( 0.0f);

	presetOutputs.ib_size =mathval( 0.0f);
	presetOutputs.ib_r =mathval( 0.0f);
	presetOutputs.ib_g =mathval( 0.0f);
	presetOutputs.ib_b =mathval( 0.0f);
	presetOutputs.ib_a =mathval( 0.0f);

	presetOutputs.mv_a =mathval( 0.0f);
	presetOutputs.mv_r =mathval( 0.0f);
	presetOutputs.mv_g =mathval( 0.0f);
	presetOutputs.mv_b =mathval( 0.0f);
	presetOutputs.mv_l =mathval( 1.0f);
	presetOutputs.mv_x =mathval( 16.0f);
	presetOutputs.mv_y =mathval( 12.0f);
	presetOutputs.mv_dy =mathval( 0.02f);
	presetOutputs.mv_dx =mathval( 0.02f);


	if ( beatDetect != NULL )
	{
		beatDetect->reset();
	}
	presetInputs.progress = 0;
	presetInputs.frame = 1;
// bass_thresh = 0;

	/* PER_FRAME CONSTANTS END */
	presetOutputs.fRating = mathval(0.0f);
	presetOutputs.fGammaAdj = mathval(1.0f);
	presetOutputs.fVideoEchoZoom = mathval(1.0f);
	presetOutputs.fVideoEchoAlpha = mathval(0.0f);
	presetOutputs.nVideoEchoOrientation = 0;

	presetOutputs.nWaveMode = 7;
	presetOutputs.bAdditiveWaves = 0;
	presetOutputs.bWaveDots = 0;
	presetOutputs.bWaveThick = 0;
	presetOutputs.bModWaveAlphaByVolume = 0;
	presetOutputs.bMaximizeWaveColor = 0;
	presetOutputs.bTexWrap = 0;
	presetOutputs.bDarkenCenter = 0;
	presetOutputs.bRedBlueStereo = 0;
	presetOutputs.bBrighten = 0;
	presetOutputs.bDarken = 0;
	presetOutputs.bSolarize = 0;
	presetOutputs.bInvert = 0;
	presetOutputs.bMotionVectorsOn = 1;

	presetOutputs.fWaveAlpha =mathval(1.0f);
	presetOutputs.fWaveScale = mathval(1.0f);
	presetOutputs.fWaveSmoothing = mathval(0.0f);
	presetOutputs.fWaveParam = mathval(0.0f);
	presetOutputs.fModWaveAlphaStart = mathval(0);
	presetOutputs.fModWaveAlphaEnd = mathval(0);
	presetOutputs.fWarpAnimSpeed = mathval(0);
	presetOutputs.fWarpScale = mathval(0);
	presetOutputs.fShader = mathval(0);


	/* PER_PIXEL CONSTANTS BEGIN */
	presetInputs.x_per_pixel = 0;
	presetInputs.y_per_pixel = 0;
	presetInputs.rad_per_pixel = 0;
	presetInputs.ang_per_pixel = 0;

	/* PER_PIXEL CONSTANT END */

	/* Q VARIABLES START */

	presetOutputs.q1 = 0;
	presetOutputs.q2 = 0;
	presetOutputs.q3 = 0;
	presetOutputs.q4 = 0;
	presetOutputs.q5 = 0;
	presetOutputs.q6 = 0;
	presetOutputs.q7 = 0;
	presetOutputs.q8 = 0;


	/* Q VARIABLES END */
}

static void PerPixelMath(PresetOutputs * presetOutputs, PresetInputs * presetInputs)
{
    int x, y;
    mathtype fZoom2, fZoom2Inv;

    for (x=0;x<gx;x++)
    {
        for(y=0;y<gy;y++)
        {
            fZoom2 = fixpow16(presetOutputs->zoom_mesh[x][y], 
                fixpow16(presetOutputs->zoomexp_mesh[x][y], 
                    presetInputs->rad_mesh[x][y]*2 - mathone));
/*            mathval(powf( mathtofloat(presetOutputs->zoom_mesh[x][y]), 
            powf( mathtofloat(presetOutputs->zoomexp_mesh[x][y]), 
                mathtofloat(presetInputs->rad_mesh[x][y])*2.0f - 1.0f)));*/
            fZoom2Inv = mathdiv(mathone,fZoom2);
            presetOutputs->x_mesh[x][y]= mathmul(origx2[x][y]/2,fZoom2Inv) + mathval(0.5f);
            presetOutputs->y_mesh[x][y]= mathmul(origy2[x][y]/2,fZoom2Inv) + mathval(0.5f);
        }
    }

    for (x=0;x<gx;x++)
    {
        for(y=0;y<gy;y++)
        {
            presetOutputs->x_mesh[x][y]  = mathdiv(( presetOutputs->x_mesh[x][y] - presetOutputs->cx_mesh[x][y]),presetOutputs->sx_mesh[x][y]) + presetOutputs->cx_mesh[x][y];
        }
    }

    for (x=0;x<gx;x++)
    {
        for(y=0;y<gy;y++)
        {
            presetOutputs->y_mesh[x][y] = mathdiv(( presetOutputs->y_mesh[x][y] - presetOutputs->cy_mesh[x][y]),presetOutputs->sy_mesh[x][y]) + presetOutputs->cy_mesh[x][y];
        }
    }

    mathtype fWarpTime = mathmul(presetInputs->time,presetOutputs->fWarpAnimSpeed);
    mathtype fWarpScaleInv = mathdiv(mathone, presetOutputs->fWarpScale);
    mathtype f[4];
    f[0] = mathval(11.68f) + mathmul(mathval(4.0f),fixcos16(mathmul(fWarpTime, mathval(1.413f)) + mathval(10)));
    f[1] = mathval(8.77f) + mathmul(mathval(3.0f),fixcos16(mathmul(fWarpTime,mathval(1.113f)) + mathval(7)));
    f[2] = mathval(10.54f) + mathmul(mathval(3.0f),fixcos16(mathmul(fWarpTime,mathval(1.233f)) + mathval(3)));
    f[3] = mathval(11.49f) + mathmul(mathval(4.0f),fixcos16(mathmul(fWarpTime,mathval(0.933f)) + mathval(5)));

    for (x=0;x<gx;x++)
    {
        for(y=0;y<gy;y++)
        {
            presetOutputs->x_mesh[x][y] += mathmul(mathmul(presetOutputs->warp_mesh[x][y],mathval(0.0035f)),
                fixsin16(mathmul(fWarpTime,mathval(0.333f)) + mathmul(fWarpScaleInv,(mathmul(origx2[x][y],f[0]) - mathmul(origy2[x][y],f[3])))));
            presetOutputs->y_mesh[x][y] += mathmul(mathmul(presetOutputs->warp_mesh[x][y],mathval(0.0035f)),
                fixcos16(mathmul(fWarpTime, mathval(0.375f)) - mathmul(fWarpScaleInv,(mathmul(origx2[x][y],f[2]) + mathmul(origy2[x][y],f[1])))));
            presetOutputs->x_mesh[x][y] += mathmul(mathmul(presetOutputs->warp_mesh[x][y],mathval(0.0035f)),
                fixcos16(mathmul(fWarpTime, mathval(0.753f)) - mathmul(fWarpScaleInv,(mathmul(origx2[x][y],f[1]) - mathmul(origy2[x][y],f[2])))));
            presetOutputs->y_mesh[x][y] += mathmul(mathmul(presetOutputs->warp_mesh[x][y],mathval(0.0035f)),
                fixsin16(mathmul(fWarpTime,0.825f) + mathmul(fWarpScaleInv,(mathmul(origx2[x][y],f[0]) + mathmul(origy2[x][y],f[3])))));
        }
    }
    for (x=0;x<gx;x++)
    {
        for(y=0;y<gy;y++)
        {
            mathtype u2 = presetOutputs->x_mesh[x][y] - presetOutputs->cx_mesh[x][y];
            mathtype v2 = presetOutputs->y_mesh[x][y] - presetOutputs->cy_mesh[x][y];

            mathtype cos_rot = fixcos16(presetOutputs->rot_mesh[x][y]);
            mathtype sin_rot = fixsin16(presetOutputs->rot_mesh[x][y]);

            presetOutputs->x_mesh[x][y] = mathmul(u2,cos_rot) - mathmul(v2,sin_rot) + presetOutputs->cx_mesh[x][y];
            presetOutputs->y_mesh[x][y] = mathmul(u2,sin_rot) + mathmul(v2,cos_rot) + presetOutputs->cy_mesh[x][y];
        }
    }


    for (x=0;x<gx;x++)
    {
        for(y=0;y<gy;y++)
        {
            presetOutputs->x_mesh[x][y] -= presetOutputs->dx_mesh[x][y];
            presetOutputs->y_mesh[x][y] -= presetOutputs->dy_mesh[x][y];
        }
    }
}

static void WaveformMath(PresetOutputs *presetOutputs, PresetInputs *presetInputs, bool isSmoothing)
{

    int x;

    mathtype r, theta;

    mathtype scale;

    mathtype wave_x_temp=0;
    mathtype wave_y_temp=0;

    mathtype cos_rot;
    mathtype sin_rot;

    // Not used?
    // mathtype offset;
    //offset=presetOutputs->wave_x-.5;

    scale=mathval(505.0f/512.0f);

    presetOutputs->two_waves = false;
    presetOutputs->draw_wave_as_loop = false;

    switch(presetOutputs->nWaveMode)
    {
        case 0:
        {
            presetOutputs->draw_wave_as_loop = true;
            presetOutputs->wave_rot =   0;
            presetOutputs->wave_scale =mathtype(1.0f);
            presetOutputs->wave_y=-1*(presetOutputs->wave_y-mathval(1.0f));

            presetOutputs->wave_samples = isSmoothing ? 512-32 : beatDetect->pcm->numsamples;

            mathtype inv_nverts_minus_one = 65536/(presetOutputs->wave_samples);

            mathtype last_value =
                beatDetect->pcm->pcmdataR[presetOutputs->wave_samples-1]+
                beatDetect->pcm->pcmdataL[presetOutputs->wave_samples-1];
            mathtype first_value = beatDetect->pcm->pcmdataR[0]+beatDetect->pcm->pcmdataL[0];
            mathtype offset = first_value-last_value;

            for (x=0;x<presetOutputs->wave_samples;x++)
            {
                mathtype value = beatDetect->pcm->pcmdataR[x]+beatDetect->pcm->pcmdataL[x];
                value += mathmul(offset, (x*65536/presetOutputs->wave_samples));
                r=(mathval(0.5f) + mathmul(mathmul(mathval(0.4f*.12),value),presetOutputs->fWaveScale) + presetOutputs->wave_mystery)>>1;

                theta=mathmul((x)*inv_nverts_minus_one,mathval(6.28f)) + mathmul(presetInputs->time,mathval(0.2f));

                // We could put the aspect ratio correction here if we want...
                presetOutputs->wavearray[x][0]=mathmul(r,fixcos16(theta)) + presetOutputs->wave_x;
                presetOutputs->wavearray[x][1]=mathmul(r,fixsin16(theta)) + presetOutputs->wave_y;
            }
        }
        break;

        case 1://circularly moving waveform
            presetOutputs->wave_rot =   0;
            presetOutputs->wave_scale = mathval(9.0f/16.0f); // aspect?

            presetOutputs->wave_y=-1*(presetOutputs->wave_y-mathval(1.0f));

            presetOutputs->wave_samples = 512-32;
            for ( x=0;x<(512-32);x++)
            {
                theta=mathmul(mathmul(beatDetect->pcm->pcmdataL[x+32],presetOutputs->fWaveScale), mathval(0.06*1.57)) +
                    mathmul(presetInputs->time,mathval(2.3f));
                r=(mathval(0.53f) +
                    mathmul(mathmul(beatDetect->pcm->pcmdataR[x],presetOutputs->fWaveScale),mathval(0.43*0.12))+
                    presetOutputs->wave_mystery)>>1;

                presetOutputs->wavearray[x][0]=mathmul(r,fixcos16(theta)) + presetOutputs->wave_x;
                presetOutputs->wavearray[x][1]=mathmul(r,fixsin16(theta)) + presetOutputs->wave_y;
            }
            break;

        case 2://EXPERIMENTAL
            presetOutputs->wave_y=-1*(presetOutputs->wave_y-mathval(1.0f));
            presetOutputs->wave_rot =   0;
            presetOutputs->wave_scale =mathval(1.0f);
            presetOutputs->wave_samples = 512-32;

            for (x=0; x<512-32; x++)
            {
                // Add aspect?
                presetOutputs->wavearray[x][0]=
                    ((mathmul(beatDetect->pcm->pcmdataR[x],presetOutputs->fWaveScale)>>1) +
                        presetOutputs->wave_x);

                presetOutputs->wavearray[x][1]=
                    ((mathmul(beatDetect->pcm->pcmdataL[x+32],presetOutputs->fWaveScale)>>1) +
                        presetOutputs->wave_y);
            }
            break;

        case 3://EXPERIMENTAL
            presetOutputs->wave_y=-1*(presetOutputs->wave_y-mathval(1.0f));

            presetOutputs->wave_rot =   0;
            presetOutputs->wave_scale =mathval(1.0f);

            presetOutputs->wave_samples = 512-32;

            for (x=0; x<512-32; x++)
            {
                presetOutputs->wavearray[x][0]=
                    ((mathmul(beatDetect->pcm->pcmdataR[x],presetOutputs->fWaveScale)>>1) +
                        presetOutputs->wave_x);

                presetOutputs->wavearray[x][1]=
                    ((mathmul(beatDetect->pcm->pcmdataL[x+32],presetOutputs->fWaveScale)>>1) +
                        presetOutputs->wave_y);
            }
            break;

        case 4://single x-axis derivative waveform
        {
            presetOutputs->wave_rot =-mathmul(presetOutputs->wave_mystery,mathval(90));
            presetOutputs->wave_scale=mathval(1.0);

            presetOutputs->wave_y=-1*(presetOutputs->wave_y-mathval(1.0f));

            mathtype w1 = mathval(0.45f) + ((presetOutputs->wave_mystery>>1) + mathval(0.5f))>>1;
            mathtype w2 = mathval(1.0f) - w1;
            mathtype xx[512], yy[512];
            presetOutputs->wave_samples = 512-32;

            for (int i=0; i<512-32; i++)
            {
                xx[i] = mathval(-1.0f) + i*mathval(2.0f/(512.0f-32.0f)) + presetOutputs->wave_x;
                yy[i] = mathmul(mathmul(beatDetect->pcm->pcmdataL[i],
                    presetOutputs->fWaveScale),mathval(0.4f*0.47f)) +
                    presetOutputs->wave_y;
                xx[i] +=                    mathmul(mathmul(beatDetect->pcm->pcmdataR[i],
                    presetOutputs->fWaveScale),mathval(0.4f*0.44f));

                if (i>1)
                {
                    xx[i] = mathmul(xx[i],w2) + mathmul(w1,(xx[i-1]<<1 - xx[i-2]));
                    yy[i] = mathmul(yy[i],w2) + mathmul(w1,(yy[i-1]<<1 - yy[i-2]));
                }
                presetOutputs->wavearray[i][0]=xx[i];
                presetOutputs->wavearray[i][1]=yy[i];
            }
        }
        break;

        case 5://EXPERIMENTAL
            presetOutputs->wave_rot = 0;
            presetOutputs->wave_scale =mathval(1.0f);

            presetOutputs->wave_y=-1*(presetOutputs->wave_y-mathval(1.0f));

            cos_rot = fixcos16(mathmul(presetInputs->time,mathval(0.3f)));
            sin_rot = fixsin16(mathmul(presetInputs->time,mathval(0.3f)));
            presetOutputs->wave_samples = 512-32;

            for (x=0; x<512-32; x++)
            {
                mathtype x0 = (mathmul(beatDetect->pcm->pcmdataR[x],beatDetect->pcm->pcmdataL[x+32]) +
                    mathmul(beatDetect->pcm->pcmdataL[x+32],beatDetect->pcm->pcmdataR[x]));
                mathtype y0 = (mathmul(beatDetect->pcm->pcmdataR[x],beatDetect->pcm->pcmdataR[x]) -
                    mathmul(beatDetect->pcm->pcmdataL[x+32],beatDetect->pcm->pcmdataL[x+32]));
                presetOutputs->wavearray[x][0]=((mathmul((mathmul(x0,cos_rot) -
                    mathmul(y0,sin_rot)),presetOutputs->fWaveScale)>>1) + presetOutputs->wave_x);
                presetOutputs->wavearray[x][1]=((mathmul((mathmul(x0,sin_rot) +
                    mathmul(y0,cos_rot)),presetOutputs->fWaveScale)>>1) + presetOutputs->wave_y);
            }
            break;

        case 6://single waveform
            wave_x_temp=mathmul(mathval(-2*0.4142),(mathabs(mathabs(presetOutputs->wave_mystery)-mathval(.5f))-mathval(.5f)));

            presetOutputs->wave_rot = -presetOutputs->wave_mystery*90;
            presetOutputs->wave_scale =mathval(1.0f)+wave_x_temp;

            wave_x_temp=-1*(presetOutputs->wave_x-mathval(1.0));
            presetOutputs->wave_samples = isSmoothing ? 512-32 : beatDetect->pcm->numsamples;

            for ( x=0;x<  presetOutputs->wave_samples;x++)
            {
                presetOutputs->wavearray[x][0]=mathdiv(mathval(x),mathval(presetOutputs->wave_samples));
                presetOutputs->wavearray[x][1]=mathmul(mathmul(beatDetect->pcm->pcmdataR[x],presetOutputs->fWaveScale),mathval(.04f))+wave_x_temp;
            }

            break;
            
        case 7://dual waveforms
            wave_x_temp=mathmul(mathval(-2*0.4142),(mathabs(mathabs(presetOutputs->wave_mystery)-mathval(.5f))-mathval(.5f)));
            
            presetOutputs->wave_rot = -presetOutputs->wave_mystery*90;
            presetOutputs->wave_scale =mathval(1.0f)+wave_x_temp;
            
            
            presetOutputs->wave_samples = isSmoothing ? 512-32 : beatDetect->pcm->numsamples;
            presetOutputs->two_waves = true;

            mathtype y_adj = mathmul(presetOutputs->wave_y,presetOutputs->wave_y)>>1;

            wave_y_temp=-1*(presetOutputs->wave_x-mathval(1.0f));

            for ( x=0;x<  presetOutputs->wave_samples ;x++)
            {
                presetOutputs->wavearray[x][0]=mathdiv(mathval(x),mathval(presetOutputs->wave_samples));
                presetOutputs->wavearray[x][1]= mathmul(mathmul(beatDetect->pcm->pcmdataL[x],presetOutputs->fWaveScale),mathval(.04f))+(wave_y_temp+y_adj);
            }
            for ( x=0;x<  presetOutputs->wave_samples;x++)
            {
                presetOutputs->wavearray2[x][0]=mathdiv(mathval(x),mathval(presetOutputs->wave_samples));
                presetOutputs->wavearray2[x][1]= mathmul(mathmul(beatDetect->pcm->pcmdataR[x],presetOutputs->fWaveScale),mathval(.04f))+(wave_y_temp-y_adj);
            }
            break;
    }
}

static void modulate_opacity_by_volume(PresetOutputs *presetOutputs)
{

    //modulate volume by opacity
    //
    //set an upper and lower bound and linearly
    //calculate the opacity from 0=lower to 1=upper
    //based on current volume


    if (presetOutputs->bModWaveAlphaByVolume==1)
    {
        if(beatDetect->vol<=presetOutputs->fModWaveAlphaStart)
            presetOutputs->wave_o=mathval(0.0f);
        else if(beatDetect->vol>=presetOutputs->fModWaveAlphaEnd)
            presetOutputs->wave_o=presetOutputs->fWaveAlpha;
        else
            presetOutputs->wave_o=mathmul(presetOutputs->fWaveAlpha,(mathdiv((beatDetect->vol-presetOutputs->fModWaveAlphaStart),(presetOutputs->fModWaveAlphaEnd-presetOutputs->fModWaveAlphaStart))));
    }
    else
        presetOutputs->wave_o=presetOutputs->fWaveAlpha;
}

static void maximize_colors(PresetOutputs *presetOutputs, int *color)
{
    
    mathtype wave_r_switch=0, wave_g_switch=0, wave_b_switch=0;
    mathtype tempr,tempg,tempb,tempa;
    //wave color brightening
    //
    //forces max color value to 1.0 and scales
    // the rest accordingly
    if(presetOutputs->nWaveMode==2 || presetOutputs->nWaveMode==5)
    {
        presetOutputs->wave_o = mathmul(presetOutputs->wave_o, mathval(0.07f));
    }

    else if(presetOutputs->nWaveMode==3)
    {
        presetOutputs->wave_o = mathmul(presetOutputs->wave_o, mathval(0.075f*1.3f));
        presetOutputs->wave_o= mathmul(presetOutputs->wave_o, fixpow16(beatDetect->treb , mathval(2.0f)));
    }

    if (presetOutputs->bMaximizeWaveColor==1)
    {
        if(presetOutputs->wave_r>=presetOutputs->wave_g && presetOutputs->wave_r>=presetOutputs->wave_b)   //red brightest
        {
            wave_r_switch=mathval(1.0f);
            wave_g_switch=mathmul(presetOutputs->wave_g,(mathdiv(mathval(1),presetOutputs->wave_r)));
            wave_b_switch=mathmul(presetOutputs->wave_b,(mathdiv(mathval(1),presetOutputs->wave_r)));
        }
        else if   (presetOutputs->wave_b>=presetOutputs->wave_g && presetOutputs->wave_b>=presetOutputs->wave_r)         //blue brightest
        {
            wave_r_switch=mathmul(presetOutputs->wave_r,(mathdiv(mathval(1),presetOutputs->wave_b)));
            wave_g_switch=mathmul(presetOutputs->wave_g,(mathdiv(mathval(1),presetOutputs->wave_b)));
            wave_b_switch=mathval(1.0f);
        }
        else  if (presetOutputs->wave_g>=presetOutputs->wave_b && presetOutputs->wave_g>=presetOutputs->wave_r)         //green brightest
        {
            wave_r_switch=mathmul(presetOutputs->wave_r,(mathdiv(mathval(1),presetOutputs->wave_g)));
            wave_g_switch=mathval(1.0f);
            wave_b_switch=mathmul(presetOutputs->wave_b,(mathdiv(mathval(1),presetOutputs->wave_g)));
        }
        tempr=mathmul(wave_r_switch, mathval(255.0f));
        tempg=mathmul(wave_g_switch, mathval(255.0f));
        tempb=mathmul(wave_b_switch, mathval(255.0f));
        tempa=mathmul(presetOutputs->wave_o, mathval(255.0f));
        //glColor4f(wave_r_switch, wave_g_switch, wave_b_switch, presetOutputs->wave_o);
    }
    else
    {
        tempr=mathmul(presetOutputs->wave_r, mathval(255.0f));
        tempg=mathmul(presetOutputs->wave_g, mathval(255.0f));
        tempb=mathmul(presetOutputs->wave_b, mathval(255.0f));
        tempa=mathmul(presetOutputs->wave_o, mathval(255.0f));
        //glColor4f(presetOutputs->wave_r, presetOutputs->wave_g, presetOutputs->wave_b, presetOutputs->wave_o);
    }

    if(tempr>mathval(255.0f)) tempr=mathval(255.0f);
    if(tempr<0) tempr=0;
    if(tempg>mathval(255.0f)) tempg=mathval(255.0f);
    if(tempg<0) tempg=0;
    if(tempb>mathval(255.0f)) tempb=mathval(255.0f);
    if(tempb<0) tempb=0;
    if(tempa>mathval(255.0f)) tempa=mathval(255.0f);
    if(tempa<0) tempa=0;

    *color=((tempr>>0)&0x00FF0000)|
        ((tempg>>8)&0x0000FF00)|
        ((tempb>>16)&0x000000FF)|
        ((tempa<<8)&0xFF000000);
}

static int draw_waveform(PresetOutputs * presetOutputs, int * wavepoints, int *flag, int *color)
{
    int x;
    modulate_opacity_by_volume(presetOutputs);
    maximize_colors(presetOutputs, color);

    *flag =0;

    if (presetOutputs->bAdditiveWaves!=0)
        *flag|=(1<<16);


    if (presetOutputs->draw_wave_as_loop)
        *flag|=(2<<16);

    // TODO
    if (presetOutputs->two_waves)
        *flag|=(4<<16);

    if (presetOutputs->bWaveDots)
        *flag|=(8<<16);

    *flag |= presetOutputs->wave_samples;

    // We must apply math to the wave :
    // presetOutputs->wave_rot
    // presetOutputs->wave_scale

    // We must scale the wave using scale then from 0-1 to 0-256
    for (x=0;x<presetOutputs->wave_samples;x++)
    {
        wavepoints[x*2+0]=presetOutputs->wavearray[x][0]*256;
        wavepoints[x*2+1]=presetOutputs->wavearray[x][1]*256;
    }

    return 0;
}


// define library for visualisation

// Init the library
extern "C" int sagevis_init(int fps)
{
    int i,x,y;
    if(!_pcm)
        _pcm = new PCM();
    if(!beatDetect)
        beatDetect = new BeatDetect ( _pcm );

    BuiltinFuncs::init_builtin_func_db();

    /* Initializes all infix operators */
    Eval::init_infix_ops();
    projectM_resetengine();

    presetInputs.Initialize ( gx, gy );
    presetOutputs.Initialize ( gx, gy );
    presetInputs.fps = fps;

    origx2=(mathtype **)wipemalloc(gx * sizeof(mathtype *));
    origy2=(mathtype **)wipemalloc(gx * sizeof(mathtype *));
    for(x = 0; x < gx; x++)
    {
        origx2[x] = (mathtype *)wipemalloc(gy * sizeof(mathtype));
        origy2[x] = (mathtype *)wipemalloc(gy * sizeof(mathtype));
        for(y=0;y<gy;y++)
        {
            mathtype origx=mathfromint(x)/(gx-1);
            mathtype origy=mathfromint(y)/(gy-1);
            origx2[x][y]=( origx-32768)*2;
            origy2[x][y]=( origy-32768)*2;
        }
    }

    timeKeeper = new TimeKeeper(15,10, 0);
}

// Deinit the library
extern "C" int sagevis_deinit()
{
    int i,x,y;
    delete timeKeeper;
    timeKeeper=NULL;
    for(x = 0; x < gx; x++)
    {
        free(origx2[x]);
        free(origy2[x]);
    }
    free(origx2);
    free(origy2);

    Eval::destroy_infix_ops();
    BuiltinFuncs::destroy_builtin_func_db();
    if(_pcm)
    {
        delete _pcm;
        _pcm = NULL;
    }
    if(beatDetect)
    {
        delete beatDetect;
        beatDetect = NULL;
    }
    if(m_activePreset)
    {
        delete m_activePreset;
        m_activePreset = NULL;
    }
}

// Set the preset file
extern "C" int sagevis_loadpreset(const char *name)
{
    const std::string *presetname = new std::string(name);
    m_activePreset = new Preset(*presetname, 
    *presetname, presetInputs, presetOutputs);
    timeKeeper->StartPreset();
    delete presetname;
}

typedef short (*pcmarray) [512] ;

// Send audio data and get back drawing commands ?
extern "C" int sagevis_update(short *pcmdata, int *decay, int *coord, int *wavepoints, int *waveflag, int *wavecolor)
{
    int i=0, x, y;
    _pcm->addPCM16((pcmarray)pcmdata);
    beatDetect->detectFromSamples();
    timeKeeper->UpdateTimers();
    setupPresetInputs(&m_activePreset->presetInputs());
    m_activePreset->presetInputs().frame = timeKeeper->PresetFrameA();
    m_activePreset->presetInputs().progress= timeKeeper->PresetProgressA();
    m_activePreset->evaluateFrame();
    PerPixelMath(&presetOutputs, &presetInputs);
    *decay = (int) (mathtofloat(presetOutputs.decay)*255.0f);
    if(*decay>255) *decay=255;
    if(*decay<0) *decay=0;

    for(y=0;y<gy;y++)
    {
        for (x=0;x<gx;x++)
        {
            // Convert coords from mathtype to fixed and from 0-1 range to texture range 0-256
            // Assumes we're using fixed point mode
            coord[i]=(int) presetOutputs.x_mesh[x][y]<<8;
            coord[i+1]=(int) presetOutputs.y_mesh[x][y]<<8;
            i+=2;
        }
    }
    WaveformMath(&presetOutputs, &presetInputs, false);
    draw_waveform(&presetOutputs, wavepoints, waveflag, wavecolor);
    return 0;
}

