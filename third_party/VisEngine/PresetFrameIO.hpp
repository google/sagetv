#ifndef PRESET_FRAME_IO_HPP
#define PRESET_FRAME_IO_HPP

#include "FixedPoint.h"
#include <vector>
class CustomWave;
class CustomShape;


/// Container class for all preset writeable engine variables. This is the important glue 
/// between the presets and renderer to facilitate smooth preset switching
/// Every preset object needs a reference to one of these.
class PresetOutputs {
public:
    typedef std::vector<CustomWave*> cwave_container;
    typedef std::vector<CustomShape*> cshape_container;

    cwave_container customWaves;
    cshape_container customShapes;

    void Initialize(int gx, int gy);
    PresetOutputs();
    ~PresetOutputs();
    /* PER FRAME VARIABLES BEGIN */
    mathtype zoom;
    mathtype zoomexp;
    mathtype rot;
    mathtype warp;

    mathtype sx;
    mathtype sy;
    mathtype dx;
    mathtype dy;
    mathtype cx;
    mathtype cy;

    mathtype decay;

    mathtype wave_r;
    mathtype wave_g;
    mathtype wave_b;
    mathtype wave_o;
    mathtype wave_x;
    mathtype wave_y;
    mathtype wave_mystery;

    mathtype ob_size;
    mathtype ob_r;
    mathtype ob_g;
    mathtype ob_b;
    mathtype ob_a;

    mathtype ib_size;
    mathtype ib_r;
    mathtype ib_g;
    mathtype ib_b;
    mathtype ib_a;

    mathtype mv_a ;
    mathtype mv_r ;
    mathtype mv_g ;
    mathtype mv_b ;
    mathtype mv_l;
    mathtype mv_x;
    mathtype mv_y;
    mathtype mv_dy;
    mathtype mv_dx;

    int gy,gx;
    /* PER_FRAME VARIABLES END */

    mathtype fRating;
    mathtype fGammaAdj;
    mathtype fVideoEchoZoom;
    mathtype fVideoEchoAlpha;

    int nVideoEchoOrientation;
    int nWaveMode;

    bool bAdditiveWaves;
    bool bWaveDots;
    bool bWaveThick;
    bool bModWaveAlphaByVolume;
    bool bMaximizeWaveColor;
    bool bTexWrap;
    bool bDarkenCenter;
    bool bRedBlueStereo;
    bool bBrighten;
    bool bDarken;
    bool bSolarize;
    bool bInvert;
    bool bMotionVectorsOn;


    mathtype fWaveAlpha ;
    mathtype fWaveScale;
    mathtype fWaveSmoothing;
    mathtype fWaveParam;
    mathtype fModWaveAlphaStart;
    mathtype fModWaveAlphaEnd;
    mathtype fWarpAnimSpeed;
    mathtype fWarpScale;
    mathtype fShader;

    /* Q VARIABLES START */

    mathtype q1;
    mathtype q2;
    mathtype q3;
    mathtype q4;
    mathtype q5;
    mathtype q6;
    mathtype q7;
    mathtype q8;


    /* Q VARIABLES END */

    mathtype **zoom_mesh;
    mathtype **zoomexp_mesh;
    mathtype **rot_mesh;

    mathtype **sx_mesh;
    mathtype **sy_mesh;
    mathtype **dx_mesh;
    mathtype **dy_mesh;
    mathtype **cx_mesh;
    mathtype **cy_mesh;
    mathtype **warp_mesh;


    mathtype **x_mesh;
    mathtype **y_mesh;

    mathtype wavearray[2048][2];
    mathtype wavearray2[2048][2];

    int wave_samples;
    bool two_waves;
    bool draw_wave_as_loop;
    mathtype wave_rot;
    mathtype wave_scale;
};

/// Container for all *read only* engine variables a preset requires to 
/// evaluate milkdrop equations. Every preset object needs a reference to one of these.
class PresetInputs {

public:
    /* PER_PIXEL VARIBLES BEGIN */

    mathtype x_per_pixel;
    mathtype y_per_pixel;
    mathtype rad_per_pixel;
    mathtype ang_per_pixel;

    /* PER_PIXEL VARIBLES END */

    int fps;


    mathtype time;
    mathtype bass;
    mathtype mid;
    mathtype treb;
    mathtype bass_att;
    mathtype mid_att;
    mathtype treb_att;
    int frame;
    mathtype progress;

    /* variables were added in milkdrop 1.04 */
    int gx,gy;

    mathtype **x_mesh;
    mathtype **y_mesh;
    mathtype **rad_mesh;
    mathtype **theta_mesh;

    mathtype **origtheta;  //grid containing interpolated mesh reference values
    mathtype **origrad;
    mathtype **origx;  //original mesh
    mathtype **origy;

    mathtype mood_r, mood_g, mood_b;

    void ResetMesh();
    ~PresetInputs();
    PresetInputs();
    void Initialize(int gx, int gy);
};

#endif
