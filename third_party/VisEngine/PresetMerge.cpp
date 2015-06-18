#include "PresetMerge.hpp"

#include <iostream>

void PresetMerger::MergePresets(PresetOutputs & A, PresetOutputs & B, mathtype ratio, int gx, int gy)
{  

mathtype invratio = mathval(1.0f) - ratio;
  //Merge Simple Waveforms
  //
  // All the mess is because of Waveform 7, which is two lines. 
  //
  A.wave_rot = mathmul(A.wave_rot ,invratio) + mathmul(B.wave_rot,ratio);
  A.wave_scale = mathmul(A.wave_scale, invratio) + mathmul(B.wave_scale,ratio);
  
  if (!B.draw_wave_as_loop) A.draw_wave_as_loop = false; 

  if (A.two_waves && B.two_waves)
    {
      for (int x = 0; x<A.wave_samples;x++)
	{
	  A.wavearray[x][0] = mathmul(A.wavearray[x][0], invratio) + mathmul(B.wavearray[x][0], ratio);
	  A.wavearray[x][1] = mathmul(A.wavearray[x][1], invratio) + mathmul(B.wavearray[x][1], ratio);
	  A.wavearray2[x][0] = mathmul(A.wavearray2[x][0], invratio) + mathmul(B.wavearray2[x][0], ratio);
	  A.wavearray2[x][1] = mathmul(A.wavearray2[x][1], invratio) + mathmul(B.wavearray2[x][1], ratio);
	}
    }
  else if (A.two_waves)
    {
      for (int x = 0; x<A.wave_samples;x++)
	{
	  A.wavearray[x][0] = mathmul(A.wavearray[x][0], invratio) + mathmul(B.wavearray[x][0], ratio);
	  A.wavearray[x][1] = mathmul(A.wavearray[x][1], invratio) + mathmul(B.wavearray[x][1], ratio);
	  A.wavearray2[x][0] = mathmul(A.wavearray2[x][0], invratio) + mathmul(B.wavearray[x][0], ratio);
	  A.wavearray2[x][1] = mathmul(A.wavearray2[x][1], invratio) + mathmul(B.wavearray[x][1], ratio);
	}
    }
  else if (B.two_waves)
    {
      A.two_waves=true;
      for (int x = 0; x<A.wave_samples;x++)
	{
	  A.wavearray[x][0] = mathmul(A.wavearray[x][0], invratio) + mathmul(B.wavearray[x][0], ratio);
	  A.wavearray[x][1] = mathmul(A.wavearray[x][1], invratio) + mathmul(B.wavearray[x][1], ratio);
	  A.wavearray2[x][0] = mathmul(A.wavearray[x][0], invratio) + mathmul(B.wavearray[x][0], ratio);
	  A.wavearray2[x][1] = mathmul(A.wavearray[x][1], invratio) + mathmul(B.wavearray[x][1], ratio);
	}
    }
  else
    {
      for (int x = 0; x<A.wave_samples;x++)
	{
	  A.wavearray[x][0] = mathmul(A.wavearray[x][0], invratio) + mathmul(B.wavearray[x][0], ratio);
	  A.wavearray[x][1] = mathmul(A.wavearray[x][1], invratio) + mathmul(B.wavearray[x][1], ratio);
	}
    }
 
  //Merge Custom Shapes and Custom Waves 

  for (PresetOutputs::cshape_container::iterator pos = A.customShapes.begin();
	pos != A.customShapes.end(); ++pos) 
    {
       (*pos)->a = mathmul((*pos)->a, invratio);
       (*pos)->a2 = mathmul((*pos)->a2, invratio);
       (*pos)->border_a = mathmul((*pos)->border_a, invratio);
    }
  
  for (PresetOutputs::cshape_container::iterator pos = B.customShapes.begin();
	pos != B.customShapes.end(); ++pos) 
    {
       (*pos)->a = mathmul((*pos)->a, ratio);
       (*pos)->a2 = mathmul((*pos)->a2, ratio);
       (*pos)->border_a = mathmul((*pos)->border_a, ratio);

        A.customShapes.push_back(*pos);

    }
 for (PresetOutputs::cwave_container::iterator pos = A.customWaves.begin();
	pos != A.customWaves.end(); ++pos) 
    {
       (*pos)->a = mathmul((*pos)->a, invratio);
      for (int x=0; x <   (*pos)->samples; x++)
	{
	   (*pos)->a_mesh[x]= mathmul((*pos)->a_mesh[x],invratio);
	}
    }

  for (PresetOutputs::cwave_container::iterator pos = B.customWaves.begin();
	pos != B.customWaves.end(); ++pos) 
    {
       (*pos)->a = mathmul((*pos)->a,ratio);
      for (int x=0; x < (*pos)->samples; x++)
	{
	   (*pos)->a_mesh[x]= mathmul((*pos)->a_mesh[x], ratio);
	}
       A.customWaves.push_back(*pos);
    }


  //Interpolate Per-Pixel mesh

  for (int x=0;x<gx;x++)
    {
      for(int y=0;y<gy;y++)
	{
	  A.x_mesh[x][y]  = mathmul(A.x_mesh[x][y], invratio) + mathmul(B.x_mesh[x][y], ratio);
	}
    }
 for (int x=0;x<gx;x++)
    {
      for(int y=0;y<gy;y++)
	{
	  A.y_mesh[x][y]  = mathmul(A.y_mesh[x][y], invratio) + mathmul(B.y_mesh[x][y], ratio);
	}
    }


 
 //Interpolate PerFrame mathtypes

  A.decay = mathmul(A.decay , invratio) + mathmul(B.decay, ratio);
  
  A.wave_r = mathmul(A.wave_r, invratio) + mathmul(B.wave_r, ratio);
  A.wave_g = mathmul(A.wave_g, invratio) + mathmul(B.wave_g, ratio);
  A.wave_b = mathmul(A.wave_b, invratio) + mathmul(B.wave_b, ratio);
  A.wave_o = mathmul(A.wave_o, invratio) + mathmul(B.wave_o, ratio);
  A.wave_x = mathmul(A.wave_x, invratio) + mathmul(B.wave_x, ratio);
  A.wave_y = mathmul(A.wave_y, invratio) + mathmul(B.wave_y, ratio);
  A.wave_mystery = mathmul(A.wave_mystery, invratio) + mathmul(B.wave_mystery, ratio);
  
  A.ob_size = mathmul(A.ob_size, invratio) + mathmul(B.ob_size, ratio);
  A.ob_r = mathmul(A.ob_r, invratio) + mathmul(B.ob_r, ratio);
  A.ob_g = mathmul(A.ob_g, invratio) + mathmul(B.ob_g, ratio);
  A.ob_b = mathmul(A.ob_b, invratio) + mathmul(B.ob_b, ratio);
  A.ob_a = mathmul(A.ob_a, invratio) + mathmul(B.ob_a, ratio);
  
  A.ib_size = mathmul(A.ib_size, invratio) + mathmul(B.ib_size, ratio);
  A.ib_r = mathmul(A.ib_r, invratio) + mathmul(B.ib_r, ratio);
  A.ib_g = mathmul(A.ib_g, invratio) + mathmul(B.ib_g, ratio);
  A.ib_b = mathmul(A.ib_b, invratio) + mathmul(B.ib_b, ratio);
  A.ib_a = mathmul(A.ib_a, invratio) + mathmul(B.ib_a, ratio);
  
  A.mv_a  = mathmul(A.mv_a, invratio) + mathmul(B.mv_a, ratio);
  A.mv_r  = mathmul(A.mv_r, invratio) + mathmul(B.mv_r, ratio);
  A.mv_g  = mathmul(A.mv_g, invratio) + mathmul(B.mv_g, ratio);
  A.mv_b  = mathmul(A.mv_b, invratio) + mathmul(B.mv_b, ratio);
  A.mv_l = mathmul(A.mv_l, invratio) + mathmul(B.mv_l, ratio);
  A.mv_x = mathmul(A.mv_x, invratio) + mathmul(B.mv_x, ratio);
  A.mv_y = mathmul(A.mv_y, invratio) + mathmul(B.mv_y, ratio);
  A.mv_dy = mathmul(A.mv_dy, invratio) + mathmul(B.mv_dy, ratio);
  A.mv_dx = mathmul(A.mv_dx, invratio) + mathmul(B.mv_dx, ratio);

  
  A.fRating = mathmul(A.fRating, invratio) + mathmul(B.fRating, ratio);
  A.fGammaAdj = mathmul(A.fGammaAdj, invratio) + mathmul(B.fGammaAdj, ratio);
  A.fVideoEchoZoom = mathmul(A.fVideoEchoZoom, invratio) + mathmul(B.fVideoEchoZoom, ratio);
  A.fVideoEchoAlpha = mathmul(A.fVideoEchoAlpha, invratio) + mathmul(B.fVideoEchoAlpha, ratio);
 
  A.fWaveAlpha = mathmul(A.fWaveAlpha, invratio) + mathmul(B.fWaveAlpha, ratio);
  A.fWaveScale = mathmul(A.fWaveScale, invratio) + mathmul(B.fWaveScale, ratio);
  A.fWaveSmoothing = mathmul(A.fWaveSmoothing, invratio) + mathmul(B.fWaveSmoothing, ratio);
  A.fWaveParam = mathmul(A.fWaveParam, invratio) + mathmul(B.fWaveParam, ratio);
  A.fModWaveAlphaStart = mathmul(A.fModWaveAlphaStart, invratio) + mathmul(B.fModWaveAlphaStart, ratio);
  A.fModWaveAlphaEnd = mathmul(A.fModWaveAlphaEnd , invratio) + mathmul(B.fModWaveAlphaEnd , ratio);
  A.fWarpAnimSpeed = mathmul(A.fWarpAnimSpeed, invratio) + mathmul(B.fWarpAnimSpeed, ratio);
  A.fWarpScale = mathmul(A.fWarpScale, invratio) + mathmul(B.fWarpScale, ratio);
  A.fShader = mathmul(A.fShader, invratio) + mathmul(B.fShader, ratio);

  //Switch bools and discrete values halfway.  Maybe we should do some interesting stuff here.
 
  if (ratio > 0.5)
    {
      A.nVideoEchoOrientation = B.nVideoEchoOrientation;
      A.nWaveMode = B.nWaveMode;
      A.bAdditiveWaves = B.bAdditiveWaves;
      A.bWaveDots = B.bWaveDots;
      A.bWaveThick = B.bWaveThick;
      A.bModWaveAlphaByVolume = B.bModWaveAlphaByVolume;
      A.bMaximizeWaveColor = B.bMaximizeWaveColor;
      A.bTexWrap = B.bTexWrap;
      A.bDarkenCenter = B.bDarkenCenter;
      A.bRedBlueStereo = B.bRedBlueStereo;
      A.bBrighten = B.bBrighten;
      A.bDarken = B.bDarken;
      A.bSolarize = B.bSolarize;
      A.bInvert = B.bInvert;
      A.bMotionVectorsOn = B.bMotionVectorsOn;
    }   

  return;
}

