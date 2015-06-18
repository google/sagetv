/**************************************************************************
 *
 *	XVID VFW FRONTEND
 *	config
 *
 *	Copyright(C) Peter Ross <pross@xvid.org>
 *
 *	This program is free software; you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation; either version 2 of the License, or
 *	(at your option) any later version.
 *
 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public License
 *	along with this program; if not, write to the Free Software
 *	Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * $Id: config.c 1985 2011-05-18 09:02:35Z Isibaar $
 *
 *************************************************************************/

#include <windows.h>
#include <commctrl.h>
#include <stdio.h>  /* sprintf */
#include <xvid.h>	/* Xvid API */

#include "debug.h"
#include "config.h"
#include "resource.h"


#define CONSTRAINVAL(X,Y,Z) if((X)<(Y)) X=Y; if((X)>(Z)) X=Z;
#define IsDlgChecked(hwnd,idc)	(IsDlgButtonChecked(hwnd,idc) == BST_CHECKED)
#define CheckDlg(hwnd,idc,value) CheckDlgButton(hwnd,idc, value?BST_CHECKED:BST_UNCHECKED)
#define EnableDlgWindow(hwnd,idc,state) EnableWindow(GetDlgItem(hwnd,idc),state)

static void zones_update(HWND hDlg, CONFIG * config);

HINSTANCE g_hInst;
HWND g_hTooltip;

static int g_use_bitrate = 1;


int pp_brightness, pp_dy, pp_duv, pp_fe, pp_dry, pp_druv; /* decoder options */

/* enumerates child windows, assigns tooltips */
BOOL CALLBACK enum_tooltips(HWND hWnd, LPARAM lParam)
{
	char help[500];

	if (LoadString(g_hInst, GetDlgCtrlID(hWnd), help, 500))
	{
		TOOLINFO ti;
		ti.cbSize = sizeof(TOOLINFO);
		ti.uFlags = TTF_SUBCLASS | TTF_IDISHWND;
		ti.hwnd = GetParent(hWnd);
		ti.uId	= (LPARAM)hWnd;
		ti.lpszText = help;
		SendMessage(g_hTooltip, TTM_ADDTOOL, 0, (LPARAM)&ti);
	}

	return TRUE;
}


/* ===================================================================================== */
/* MPEG-4 PROFILES/LEVELS ============================================================== */
/* ===================================================================================== */

/* #define EXTRA_PROFILES */

/* default vbv_occupancy is (64/170)*vbv_buffer_size */

#define PROFILE_S       (PROFILE_4MV)
#define PROFILE_ARTS		(PROFILE_4MV|PROFILE_ADAPTQUANT)
#define PROFILE_AS			(PROFILE_4MV|PROFILE_ADAPTQUANT|PROFILE_BVOP|PROFILE_MPEGQUANT|PROFILE_INTERLACE|PROFILE_QPEL|PROFILE_GMC)

const profile_t profiles[] =
{
/*  name                p@l    w    h    fps  obj Tvmv vmv    vcv    ac%   vbv        pkt     bps    vbv_peak dbf flags */
#ifndef EXTRA_PROFILES
  { "Xvid Mobile",  "Xvid Mobile",  0x00,  352, 240, 30,  1,  990,  330,   9900, 100,  128*8192,    -1, 1334850,  8000000,  5, PROFILE_4MV|PROFILE_ADAPTQUANT|PROFILE_BVOP|PROFILE_PACKED|PROFILE_MPEGQUANT|PROFILE_QPEL|PROFILE_XVID },
  { "Xvid Home",    "Xvid Home",    0x00,  720, 576, 25,  1, 4860, 1620,  40500, 100,  384*8192,    -1, 4854000,  8000000,  5, PROFILE_4MV|PROFILE_ADAPTQUANT|PROFILE_BVOP|PROFILE_PACKED|PROFILE_MPEGQUANT|PROFILE_QPEL|PROFILE_INTERLACE|PROFILE_XVID },
  { "Xvid HD 720",  "Xvid HD 720",  0x00, 1280, 720, 30,  1,10800, 3600, 108000, 100,  768*8192,    -1, 9708400, 16000000,  5, PROFILE_4MV|PROFILE_ADAPTQUANT|PROFILE_BVOP|PROFILE_PACKED|PROFILE_MPEGQUANT|PROFILE_QPEL|PROFILE_INTERLACE|PROFILE_XVID },
  { "Xvid HD 1080",	"Xvid HD 1080", 0x00, 1920,1080, 30,  1,24480, 8160, 244800, 100, 2047*8192,    -1,20480000, 36000000,  5, PROFILE_4MV|PROFILE_ADAPTQUANT|PROFILE_BVOP|PROFILE_PACKED|PROFILE_MPEGQUANT|PROFILE_QPEL|PROFILE_INTERLACE|PROFILE_XVID },
#else
  { "Handheld",	         "Handheld",		  0x00,  176, 144, 15,  1,  198,   99,   1485, 100,   32*8192,    -1,  537600,   800000,  0, PROFILE_ADAPTQUANT|PROFILE_EXTRA },
  { "Portable NTSC",     "Portable NTSC",	  0x00,  352, 240, 30,  1,  990,  330,  36000, 100,  384*8192,    -1, 4854000,  8000000,  1, PROFILE_4MV|PROFILE_ADAPTQUANT|PROFILE_BVOP|PROFILE_PACKED|PROFILE_EXTRA },
  { "Portable PAL",	     "Portable PAL",	  0x00,  352, 288, 25,  1, 1188,  396,  36000, 100,  384*8192,    -1, 4854000,  8000000,  1, PROFILE_4MV|PROFILE_ADAPTQUANT|PROFILE_BVOP|PROFILE_PACKED|PROFILE_EXTRA },
  { "Home Theatre NTSC", "Home Theatre NTSC", 0x00,  720, 480, 30,  1, 4050, 1350,  40500, 100,  384*8192,    -1, 4854000,  8000000,  1, PROFILE_4MV|PROFILE_ADAPTQUANT|PROFILE_BVOP|PROFILE_PACKED|PROFILE_INTERLACE|PROFILE_EXTRA },
  { "Home Theatre PAL",  "Home Theatre PAL",  0x00,  720, 576, 25,  1, 4860, 1620,  40500, 100,  384*8192,    -1, 4854000,  8000000,  1, PROFILE_4MV|PROFILE_ADAPTQUANT|PROFILE_BVOP|PROFILE_PACKED|PROFILE_INTERLACE|PROFILE_EXTRA },
  { "HDTV",	             "HDTV",			  0x00, 1280, 720, 30,  1,10800, 3600, 108000, 100,  768*8192,    -1, 9708400, 16000000,  2, PROFILE_4MV|PROFILE_ADAPTQUANT|PROFILE_BVOP|PROFILE_PACKED|PROFILE_INTERLACE|PROFILE_EXTRA },
#endif

  { "MPEG4 Simple @ L0", "MPEG4 SP @ L0",     0x08,  176, 144, 15,  1,  198,   99,   1485, 100,  10*16368,  2048,   64000,        0, -1, PROFILE_S },
  /* simple@l0: max f_code=1, intra_dc_vlc_threshold=0 */
  /* if ac preidition is used, adaptive quantization must not be used */
  /* <=qcif must be used */
  { "MPEG4 Simple @ L1", "MPEG4 SP @ L1",     0x01,  176, 144, 15,  4,  198,   99,   1485, 100,  10*16368,  2048,   64000,        0, -1, PROFILE_S|PROFILE_ADAPTQUANT },
  { "MPEG4 Simple @ L2", "MPEG4 SP @ L2",     0x02,  352, 288, 15,  4,  792,  396,   5940, 100,  40*16368,  4096,  128000,        0, -1, PROFILE_S|PROFILE_ADAPTQUANT },
  { "MPEG4 Simple @ L3", "MPEG4 SP @ L3",     0x03,  352, 288, 30,  4,  792,  396,  11880, 100,  40*16368,  8192,  384000,        0, -1, PROFILE_S|PROFILE_ADAPTQUANT },
  /* From ISO/IEC 14496-2:2004/FPDAM 2: New Levels for Simple Profile */
  { "MPEG4 Simple @ L4a","MPEG4 SP @ L4a",    0x04,  640, 480, 30,  4, 2400, 1200,  36000, 100,  80*16368, 16384, 4000000,        0, -1, PROFILE_S|PROFILE_ADAPTQUANT },
  { "MPEG4 Simple @ L5", "MPEG4 SP @ L5",     0x05,  720, 576, 30,  4, 3240, 1620,  40500, 100, 112*16368, 16384, 8000000,        0, -1, PROFILE_S|PROFILE_ADAPTQUANT },
  /* From ISO/IEC 14496-2:2004/FPDAM 4: Simple profile level 6 */
  { "MPEG4 Simple @ L6", "MPEG4 SP @ L6",     0x06, 1280, 720, 30,  4, 7200, 3600, 108000, 100, 248*16368, 16384,12000000,        0, -1, PROFILE_S|PROFILE_ADAPTQUANT },

#if 0 /* since rrv encoding is no longer support, these profiles have little use */
  { "MPEG4 ARTS @ L1", "MPEG4 ARTS @ L1",       0x91,  176, 144, 15,  4,  198,   99,   1485, 100,  10*16368,  8192,   64000,        0, -1, PROFILE_ARTS },
  { "MPEG4 ARTS @ L2", "MPEG4 ARTS @ L2",       0x92,  352, 288, 15,  4,  792,  396,   5940, 100,  40*16368, 16384,  128000,        0, -1, PROFILE_ARTS },
  { "MPEG4 ARTS @ L3", "MPEG4 ARTS @ L3",       0x93,  352, 288, 30,  4,  792,  396,  11880, 100,  40*16368, 16384,  384000,        0, -1, PROFILE_ARTS },
  { "MPEG4 ARTS @ L4", "MPEG4 ARTS @ L4",       0x94,  352, 288, 30, 16,  792,  396,  11880, 100,  80*16368, 16384, 2000000,        0, -1, PROFILE_ARTS },
#endif

  { "MPEG4 Advanced Simple @ L0", "MPEG4 ASP @ L0",         0xf0,  176, 144, 30,  1,  297,   99,   2970, 100,  10*16368,  2048,  128000,        0, -1, PROFILE_AS },
  { "MPEG4 Advanced Simple @ L1", "MPEG4 ASP @ L1",         0xf1,  176, 144, 30,  4,  297,   99,   2970, 100,  10*16368,  2048,  128000,        0, -1, PROFILE_AS },
  { "MPEG4 Advanced Simple @ L2", "MPEG4 ASP @ L2",         0xf2,  352, 288, 15,  4, 1188,  396,   5940, 100,  40*16368,  4096,  384000,        0, -1, PROFILE_AS },
  { "MPEG4 Advanced Simple @ L3", "MPEG4 ASP @ L3",         0xf3,  352, 288, 30,  4, 1188,  396,  11880, 100,  40*16368,  4096,  768000,        0, -1, PROFILE_AS },
 /*  ISMA Profile 1, (ASP) @ L3b (CIF, 1.5 Mb/s) CIF(352x288), 30fps, 1.5Mbps max ??? */
  { "MPEG4 Advanced Simple @ L4", "MPEG4 ASP @ L4",         0xf4,  352, 576, 30,  4, 2376,  792,  23760,  50,  80*16368,  8192, 3000000,        0, -1, PROFILE_AS },
  { "MPEG4 Advanced Simple @ L5", "MPEG4 ASP @ L5",         0xf5,  720, 576, 30,  4, 4860, 1620,  48600,  25, 112*16368, 16384, 8000000,        0, -1, PROFILE_AS },

  { "(unrestricted)", "(unrestricted)",  0x00,    0,   0,  0,  0,    0,    0,      0, 100,   0*16368,    -1,       0,        0, -1, 0xffffffff & ~(PROFILE_EXTRA | PROFILE_PACKED | PROFILE_XVID)},
};


const quality_t quality_table[] = 
{
    /* name                 |  m  vhq mtc bf cme  tbo  kfi  fdr  | iquant pquant bquant trellis */
  { "Real-time",               1,  0,  0,  0,  0,  0,  300,  0,     1, 31, 1, 31, 1, 31,   0   },
  { QUALITY_GENERAL_STRING,    6,  1,  0,  0,  1,  0,  300,  0,     1, 31, 1, 31, 1, 31,   1   },
};

const int quality_table_num = sizeof(quality_table)/sizeof(quality_t);

typedef struct {
	char * name;
	float value;
} named_float_t;

static const named_float_t video_fps_list[] = {
	{  "15.0",				15.0F	},
	{  "23.976 (FILM)",		23.976F	},
	{  "25.0 (PAL)",		25.0F	},
	{  "29.97 (NTSC)",		29.970F	},
	{  "30.0",				30.0F	},
	{  "50.0 (HD PAL)",		50.0F	},
	{  "59.94 (HD NTSC)",	59.940F	},
	{  "60.0",				60.0F	},
};


typedef struct {
	char * name;
	int avi_interval;		/* audio overhead intervals (milliseconds) */
	float mkv_multiplier;	/* mkv multiplier */
} named_int_t;


#define NO_AUDIO	7
static const named_int_t audio_type_list[] = {
	{	"MP3-CBR",		1000,	48000/1152/6					},
	{	"MP3-VBR",		  24,	48000/1152/6					},
	{	"OGG",	   /*?*/1000,	48000*(0.7F/1024 + 0.3F/180) 	},
	{	"AC3",			  64,	48000/1536/6					},
	{	"DTS",			  21,	/*?*/48000/1152/6				},
	{	"AAC",			  21,	48000/1024/6					},
	{	"HE-AAC",		  42,	48000/1024/6					},
	{	"(None)",		   0,	0								},
};
		

/* ===================================================================================== */
/* REGISTRY ============================================================================ */
/* ===================================================================================== */

/* registry info structs */
CONFIG reg;

static const REG_INT reg_ints[] = {
	{"mode",					&reg.mode,						RC_MODE_1PASS},
	{"bitrate",					&reg.bitrate,					700},
	{"desired_size",			&reg.desired_size,				570000},
	{"use_2pass_bitrate",		&reg.use_2pass_bitrate,			0},
	{"desired_quant",			&reg.desired_quant,				DEFAULT_QUANT}, /* 100-base float */

	/* profile */
	{"quant_type",				&reg.quant_type,				0},
	{"lum_masking",				&reg.lum_masking,				0},
	{"interlacing",				&reg.interlacing,				0},
	{"tff",						&reg.tff,						0},
	{"qpel",					&reg.qpel,						0},
	{"gmc",						&reg.gmc,						0},
	{"use_bvop",				&reg.use_bvop,					1},
	{"max_bframes",				&reg.max_bframes,				2},
	{"bquant_ratio",			&reg.bquant_ratio,				150},   /* 100-base float */
	{"bquant_offset",			&reg.bquant_offset,				100},   /* 100-base float */
	{"packed",					&reg.packed,					1},
	{"num_slices", 				&reg.num_slices,				1},

	/* aspect ratio */
	{"ar_mode",					&reg.ar_mode,					0},
	{"aspect_ratio",			&reg.display_aspect_ratio,		0},
	{"par_x",					&reg.par_x,						1},
	{"par_y",					&reg.par_y,						1},
	{"ar_x",					&reg.ar_x,						4},
	{"ar_y",					&reg.ar_y,						3},

	/* zones */
	{"num_zones",				&reg.num_zones,					1},

	/* single pass */
	{"rc_reaction_delay_factor",&reg.rc_reaction_delay_factor,	16},
	{"rc_averaging_period",		&reg.rc_averaging_period,		100},
	{"rc_buffer",				&reg.rc_buffer,					100},

	/* 2pass1 */
	{"discard1pass",			&reg.discard1pass,				1},
	{"full1pass",				&reg.full1pass,					0},

	/* 2pass2 */
	{"keyframe_boost",			&reg.keyframe_boost,			10},
	{"kfreduction",				&reg.kfreduction,				20},
	{"kfthreshold",				&reg.kfthreshold,				1},
	{"curve_compression_high",	&reg.curve_compression_high,	0},
	{"curve_compression_low",	&reg.curve_compression_low,		0},
	{"overflow_control_strength", &reg.overflow_control_strength, 5},
	{"twopass_max_overflow_improvement", &reg.twopass_max_overflow_improvement, 5},
	{"twopass_max_overflow_degradation", &reg.twopass_max_overflow_degradation, 5},

	/* bitrate calculator */
	{"container_type",			&reg.container_type,			1},
	{"target_size",				&reg.target_size,				650 * 1024},
	{"subtitle_size",			&reg.subtitle_size,				0},
	{"hours",        			&reg.hours,						1},
	{"minutes",        			&reg.minutes,					30},
	{"seconds",        			&reg.seconds,					0},
	{"fps",	        			&reg.fps,						2},
	{"audio_mode",				&reg.audio_mode,				0},
	{"audio_type",				&reg.audio_type,				0},
	{"audio_rate",				&reg.audio_rate,				128},
	{"audio_size",				&reg.audio_size,				0},

	/* motion */
	{"motion_search",			&reg.quality_user.motion_search,				6},
	{"vhq_mode",				&reg.quality_user.vhq_mode,					1},
	{"vhq_metric",				&reg.quality_user.vhq_metric,				0},
	{"vhq_bframe",				&reg.quality_user.vhq_bframe,				0},
	{"chromame",				&reg.quality_user.chromame,					1},
	{"turbo",					&reg.quality_user.turbo,						0},
	{"max_key_interval",		&reg.quality_user.max_key_interval,			300},
	{"frame_drop_ratio",		&reg.quality_user.frame_drop_ratio,			0},

	/* quant */
	{"min_iquant",				&reg.quality_user.min_iquant,				1},
	{"max_iquant",				&reg.quality_user.max_iquant,				31},
	{"min_pquant",				&reg.quality_user.min_pquant,				1},
	{"max_pquant",				&reg.quality_user.max_pquant,				31},
	{"min_bquant",				&reg.quality_user.min_bquant,				1},
	{"max_bquant",				&reg.quality_user.max_bquant,				31},
	{"trellis_quant",			&reg.quality_user.trellis_quant,				1},

	/* debug */
	{"fourcc_used",				&reg.fourcc_used,				0},
	{"debug",					&reg.debug,						0x0},
	{"vop_debug",				&reg.vop_debug,					0},
	{"display_status",			&reg.display_status,			1},
	{"cpu_flags", 				&reg.cpu,						0},

	/* smp */
	{"num_threads",				&reg.num_threads,				0},

	/* decoder, shared with dshow */
	{"Brightness",				&pp_brightness,					0},
	{"Deblock_Y",				&pp_dy,							0},
	{"Deblock_UV",				&pp_duv,						0},
	{"Dering_Y",				&pp_dry,						0},
	{"Dering_UV",				&pp_druv,						0},
	{"FilmEffect",				&pp_fe,							0},
	
};

static const REG_STR reg_strs[] = {
	{"profile",					reg.profile_name,				"Xvid Home"},
	{"quality",         reg.quality_name,       QUALITY_GENERAL_STRING},
	{"stats",					reg.stats,						CONFIG_2PASS_FILE},
};


zone_t stmp;
static const REG_INT reg_zone[] = {
	{"zone%i_frame",			&stmp.frame,					0},
	{"zone%i_mode",				&stmp.mode,						RC_ZONE_WEIGHT},
	{"zone%i_weight",			&stmp.weight,					100},	  /* 100-base float */
	{"zone%i_quant",			&stmp.quant,					500},	  /* 100-base float */
	{"zone%i_type",				&stmp.type,						XVID_TYPE_AUTO},
	{"zone%i_greyscale",		&stmp.greyscale,				0},
	{"zone%i_chroma_opt",		&stmp.chroma_opt,				0},
	{"zone%i_bvop_threshold",   &stmp.bvop_threshold,			0},
	{"zone%i_cartoon_mode",		&stmp.cartoon_mode,				0},
};

static const BYTE default_qmatrix_intra[] = {
	8, 17,18,19,21,23,25,27,
	17,18,19,21,23,25,27,28,
	20,21,22,23,24,26,28,30,
	21,22,23,24,26,28,30,32,
	22,23,24,26,28,30,32,35,
	23,24,26,28,30,32,35,38,
	25,26,28,30,32,35,38,41,
	27,28,30,32,35,38,41,45
};

static const BYTE default_qmatrix_inter[] = {
	16,17,18,19,20,21,22,23,
	17,18,19,20,21,22,23,24,
	18,19,20,21,22,23,24,25,
	19,20,21,22,23,24,26,27,
	20,21,22,23,25,26,27,28,
	21,22,23,24,26,27,28,30,
	22,23,24,26,27,28,30,31,
	23,24,25,27,28,30,31,33
};



#define REG_GET_B(X, Y, Z) size=sizeof((Z));if(RegQueryValueEx(hKey, X, 0, 0, Y, &size) != ERROR_SUCCESS) {memcpy(Y, Z, sizeof((Z)));}

#define XVID_DLL_NAME "xvidcore.dll"

void config_reg_get(CONFIG * config)
{
	char tmp[32];
	HKEY hKey;
	DWORD size;
	int i,j;
	xvid_gbl_info_t info;
	HINSTANCE m_hdll;

	memset(&info, 0, sizeof(info));
	info.version = XVID_VERSION;

	m_hdll = LoadLibrary(XVID_DLL_NAME);
	if (m_hdll != NULL) {

		((int (__cdecl *)(void *, int, void *, void *))GetProcAddress(m_hdll, "xvid_global"))
			(0, XVID_GBL_INFO, &info, NULL);

		FreeLibrary(m_hdll);
	}

	RegOpenKeyEx(XVID_REG_KEY, XVID_REG_PARENT "\\" XVID_REG_CHILD, 0, KEY_READ, &hKey);

	/* read integer values */
	for (i=0 ; i<sizeof(reg_ints)/sizeof(REG_INT); i++) {
		size = sizeof(int);
		if (RegQueryValueEx(hKey, reg_ints[i].reg_value, 0, 0, (LPBYTE)reg_ints[i].config_int, &size) != ERROR_SUCCESS) {
			*reg_ints[i].config_int = reg_ints[i].def;
		}
	}

	/* read string values */
	for (i=0 ; i<sizeof(reg_strs)/sizeof(REG_STR); i++) {
		size = MAX_PATH;
		if (RegQueryValueEx(hKey, reg_strs[i].reg_value, 0, 0, (LPBYTE)reg_strs[i].config_str, &size) != ERROR_SUCCESS) {
			memcpy(reg_strs[i].config_str, reg_strs[i].def, MAX_PATH);
		}
	}

  /* find profile table index */
	reg.profile = 0;
	for (i=0; i<sizeof(profiles)/sizeof(profile_t); i++) {
		char perm1[255] = {"Xvid "}, perm2[255] = {"MPEG4 "};
		if (lstrcmpi(profiles[i].name, reg.profile_name) == 0 || 
			lstrcmpi(profiles[i].name, lstrcat(perm1, reg.profile_name)) == 0 ||
			lstrcmpi(profiles[i].name, lstrcat(perm2, reg.profile_name)) == 0) { /* legacy names */
			reg.profile = i;
		}
	}

  /* find quality table index */
	reg.quality = quality_table_num;
	for (i=0; i<quality_table_num; i++) {
		if (lstrcmpi(quality_table[i].name, reg.quality_name) == 0) {
			reg.quality = i;
		}
	}

	memcpy(config, &reg, sizeof(CONFIG));


	/* read quant matrices */
	REG_GET_B("qmatrix_intra", config->qmatrix_intra, default_qmatrix_intra);
	REG_GET_B("qmatrix_inter", config->qmatrix_inter, default_qmatrix_inter);


	/* read zones */
	if (config->num_zones>MAX_ZONES) {
		config->num_zones=MAX_ZONES;
	}else if (config->num_zones<=0) {
		config->num_zones = 1;
	}

	for (i=0; i<config->num_zones; i++) {
		for (j=0; j<sizeof(reg_zone)/sizeof(REG_INT); j++)  {
			size = sizeof(int);

			wsprintf(tmp, reg_zone[j].reg_value, i);
			if (RegQueryValueEx(hKey, tmp, 0, 0, (LPBYTE)reg_zone[j].config_int, &size) != ERROR_SUCCESS)
				*reg_zone[j].config_int = reg_zone[j].def;
		}

		memcpy(&config->zones[i], &stmp, sizeof(zone_t));
	}

	if (!(config->cpu&XVID_CPU_FORCE)) {
		config->cpu = info.cpu_flags;
		config->num_threads = info.num_threads; /* autodetect */
	}

	RegCloseKey(hKey);
}


/* put config settings in registry */

#define REG_SET_B(X, Y) RegSetValueEx(hKey, X, 0, REG_BINARY, Y, sizeof((Y)))

void config_reg_set(CONFIG * config)
{
	char tmp[64];
	HKEY hKey;
	DWORD dispo;
	int i,j;

	if (RegCreateKeyEx(
			XVID_REG_KEY,
			XVID_REG_PARENT "\\" XVID_REG_CHILD,
			0,
			XVID_REG_CLASS,
			REG_OPTION_NON_VOLATILE,
			KEY_WRITE,
			0,
			&hKey,
			&dispo) != ERROR_SUCCESS)
	{
		DPRINTF("Couldn't create XVID_REG_SUBKEY - GetLastError=%i", GetLastError());
		return;
	}

	memcpy(&reg, config, sizeof(CONFIG));

	/* set integer values */
	for (i=0 ; i<sizeof(reg_ints)/sizeof(REG_INT); i++) {
		RegSetValueEx(hKey, reg_ints[i].reg_value, 0, REG_DWORD, (LPBYTE)reg_ints[i].config_int, sizeof(int));
	}

	/* set string values */
	strcpy(reg.profile_name, profiles[reg.profile].name);
  strcpy(reg.quality_name, 
    reg.quality<quality_table_num ? quality_table[reg.quality].name : QUALITY_USER_STRING);
	for (i=0 ; i<sizeof(reg_strs)/sizeof(REG_STR); i++) {
		RegSetValueEx(hKey, reg_strs[i].reg_value, 0, REG_SZ, reg_strs[i].config_str, lstrlen(reg_strs[i].config_str)+1);
	}

	/* set quant matrices */
	REG_SET_B("qmatrix_intra", config->qmatrix_intra);
	REG_SET_B("qmatrix_inter", config->qmatrix_inter);

	/* set seections */
	for (i=0; i<config->num_zones; i++) {
		memcpy(&stmp, &config->zones[i], sizeof(zone_t));
		for (j=0; j<sizeof(reg_zone)/sizeof(REG_INT); j++)  {
			wsprintf(tmp, reg_zone[j].reg_value, i);
			RegSetValueEx(hKey, tmp, 0, REG_DWORD, (LPBYTE)reg_zone[j].config_int, sizeof(int));
		}
	}

	RegCloseKey(hKey);
}


/* clear Xvid registry key, load defaults */

static void config_reg_default(CONFIG * config)
{
	HKEY hKey;

	if (RegOpenKeyEx(XVID_REG_KEY, XVID_REG_PARENT, 0, KEY_ALL_ACCESS, &hKey)) {
		DPRINTF("Couldn't open registry key for deletion - GetLastError=%i", GetLastError());
		return;
	}

	if (RegDeleteKey(hKey, XVID_REG_CHILD)) {
		DPRINTF("Couldn't delete registry key - GetLastError=%i", GetLastError());
		return;
	}

	RegCloseKey(hKey);
	config_reg_get(config);
	config_reg_set(config);
}


/* leaves current config value if dialog item is empty */
static int config_get_int(HWND hDlg, INT item, int config)
{
	BOOL success = FALSE;
	int tmp = GetDlgItemInt(hDlg, item, &success, TRUE);
	return (success) ? tmp : config;
}


static int config_get_uint(HWND hDlg, UINT item, int config)
{
	BOOL success = FALSE;
	int tmp = GetDlgItemInt(hDlg, item, &success, FALSE);
	return (success) ? tmp : config;
}

/* get uint from combobox
   GetDlgItemInt doesnt work properly for cb list items */
#define UINT_BUF_SZ	20
static int config_get_cbuint(HWND hDlg, UINT item, int def)
{
	LRESULT sel = SendMessage(GetDlgItem(hDlg, item), CB_GETCURSEL, 0, 0);
	char buf[UINT_BUF_SZ];

	if (sel<0) {
		return config_get_uint(hDlg, item, def);
	}
		
	if (SendMessage(GetDlgItem(hDlg, item), CB_GETLBTEXT, sel, (LPARAM)buf) == CB_ERR) {
		return def;
	}
	
	return atoi(buf);
}


/* we use "100 base" floats */

#define FLOAT_BUF_SZ	20
static int get_dlgitem_float(HWND hDlg, UINT item, int def)
{
	char buf[FLOAT_BUF_SZ];

	if (GetDlgItemText(hDlg, item, buf, FLOAT_BUF_SZ) == 0)
		return def;

	return (int)(atof(buf)*100);
}

static void set_dlgitem_float(HWND hDlg, UINT item, int value)
{
	char buf[FLOAT_BUF_SZ];
	sprintf(buf, "%.2f", (float)value/100);
	SetDlgItemText(hDlg, item, buf);
}

static void set_dlgitem_float1000(HWND hDlg, UINT item, int value)
{
	char buf[FLOAT_BUF_SZ];
	sprintf(buf, "%.3f", (float)value/1000);
	SetDlgItemText(hDlg, item, buf);
}

#define HEX_BUF_SZ  16
static unsigned int get_dlgitem_hex(HWND hDlg, UINT item, unsigned int def)
{
	char buf[HEX_BUF_SZ];
	unsigned int value;

	if (GetDlgItemText(hDlg, item, buf, HEX_BUF_SZ) == 0)
		return def;

	if (sscanf(buf,"0x%x", &value)==1 || sscanf(buf,"%x", &value)==1) {
		return value;
	}

	return def;
}

static void set_dlgitem_hex(HWND hDlg, UINT item, int value)
{
	char buf[HEX_BUF_SZ];
	wsprintf(buf, "0x%x", value);
	SetDlgItemText(hDlg, item, buf);
}

/* ===================================================================================== */
/* QUANT MATRIX DIALOG ================================================================= */
/* ===================================================================================== */

static void quant_upload(HWND hDlg, CONFIG* config)
{
	int i;

	for (i=0 ; i<64; i++) {
		SetDlgItemInt(hDlg, IDC_QINTRA00 + i, config->qmatrix_intra[i], FALSE);
		SetDlgItemInt(hDlg, IDC_QINTER00 + i, config->qmatrix_inter[i], FALSE);
	}
}


static void quant_download(HWND hDlg, CONFIG* config)
{
	int i;

	for (i=0; i<64; i++) {
		int temp;

		temp = config_get_uint(hDlg, i + IDC_QINTRA00, config->qmatrix_intra[i]);
		CONSTRAINVAL(temp, 1, 255);
		config->qmatrix_intra[i] = temp;

		temp = config_get_uint(hDlg, i + IDC_QINTER00, config->qmatrix_inter[i]);
		CONSTRAINVAL(temp, 1, 255);
		config->qmatrix_inter[i] = temp;
	}
}


static void quant_loadsave(HWND hDlg, CONFIG * config, int save)
{
	char file[MAX_PATH];
	OPENFILENAME ofn;
	HANDLE hFile;
	DWORD read=128, wrote=0;
	BYTE quant_data[128];

	strcpy(file, "\\matrix");
	memset(&ofn, 0, sizeof(OPENFILENAME));
	ofn.lStructSize = sizeof(OPENFILENAME);

	ofn.hwndOwner = hDlg;
	ofn.lpstrFilter = "All files (*.*)\0*.*\0\0";
	ofn.lpstrFile = file;
	ofn.nMaxFile = MAX_PATH;
	ofn.Flags = OFN_PATHMUSTEXIST;

	if (save) {
		ofn.Flags |= OFN_OVERWRITEPROMPT;
		if (GetSaveFileName(&ofn)) {
			hFile = CreateFile(file, GENERIC_WRITE, 0, 0, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, 0);

			quant_download(hDlg, config);
			memcpy(quant_data, config->qmatrix_intra, 64);
			memcpy(quant_data+64, config->qmatrix_inter, 64);

			if (hFile == INVALID_HANDLE_VALUE) {
				DPRINTF("Couldn't save quant matrix");
			}else{
				if (!WriteFile(hFile, quant_data, 128, &wrote, 0)) {
					DPRINTF("Couldnt write quant matrix");
				}
			}
			CloseHandle(hFile);
		}
	}else{
		ofn.Flags |= OFN_FILEMUSTEXIST;
		if (GetOpenFileName(&ofn)) {
			hFile = CreateFile(file, GENERIC_READ, 0, 0, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0);

			if (hFile == INVALID_HANDLE_VALUE) {
				DPRINTF("Couldn't load quant matrix");
			} else {
				if (!ReadFile(hFile, quant_data, 128, &read, 0)) {
					DPRINTF("Couldnt read quant matrix");
				}else{
					memcpy(config->qmatrix_intra, quant_data, 64);
					memcpy(config->qmatrix_inter, quant_data+64, 64);
					quant_upload(hDlg, config);
				}
			}
			CloseHandle(hFile);
		}
	}
}

/* quantization matrix dialog proc */

static INT_PTR CALLBACK quantmatrix_proc(HWND hDlg, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	CONFIG* config = (CONFIG*)GetWindowLongPtr(hDlg, GWLP_USERDATA);

	switch (uMsg)
	{
	case WM_INITDIALOG :
		SetWindowLongPtr(hDlg, GWLP_USERDATA, lParam);
		config = (CONFIG*)lParam;
		quant_upload(hDlg, config);

		if (g_hTooltip)
		{
			EnumChildWindows(hDlg, enum_tooltips, 0);
		}
		break;

	case WM_COMMAND :

		if (HIWORD(wParam) == BN_CLICKED) {
			switch(LOWORD(wParam)) {
			case IDOK :
				quant_download(hDlg, config);
				EndDialog(hDlg, IDOK);
				break;

			case IDCANCEL :
				EndDialog(hDlg, IDCANCEL);
				break;

			case IDC_SAVE :
				quant_loadsave(hDlg, config, 1);
				break;

			case IDC_LOAD :
				quant_loadsave(hDlg, config, 0);
				break;

			default :
				return FALSE;
			}
			break;
		}
		return FALSE;

	default :
		return FALSE;
	}

	return TRUE;
}


/* ===================================================================================== */
/* ADVANCED DIALOG PAGES ================================================================ */
/* ===================================================================================== */

/* initialise pages */
static void adv_init(HWND hDlg, int idd, CONFIG * config)
{
	unsigned int i;

	switch(idd) {
	case IDD_PROFILE :
		for (i=0; i<sizeof(profiles)/sizeof(profile_t); i++)
			SendDlgItemMessage(hDlg, IDC_PROFILE_PROFILE, CB_ADDSTRING, 0, (LPARAM)profiles[i].name);
		SendDlgItemMessage(hDlg, IDC_QUANTTYPE, CB_ADDSTRING, 0, (LPARAM)"H.263");
		SendDlgItemMessage(hDlg, IDC_QUANTTYPE, CB_ADDSTRING, 0, (LPARAM)"MPEG");
		SendDlgItemMessage(hDlg, IDC_QUANTTYPE, CB_ADDSTRING, 0, (LPARAM)"MPEG-Custom");

		SendDlgItemMessage(hDlg, IDC_LUMMASK, CB_ADDSTRING, 0, (LPARAM)"Off");
		SendDlgItemMessage(hDlg, IDC_LUMMASK, CB_ADDSTRING, 0, (LPARAM)"Luminance-Masking");
		SendDlgItemMessage(hDlg, IDC_LUMMASK, CB_ADDSTRING, 0, (LPARAM)"Variance-Masking");
		break;

	case IDD_AR:
		SendDlgItemMessage(hDlg, IDC_ASPECT_RATIO, CB_ADDSTRING, 0, (LPARAM)"Square (default)");
		SendDlgItemMessage(hDlg, IDC_ASPECT_RATIO, CB_ADDSTRING, 0, (LPARAM)"4:3 PAL");
		SendDlgItemMessage(hDlg, IDC_ASPECT_RATIO, CB_ADDSTRING, 0, (LPARAM)"4:3 NTSC");
		SendDlgItemMessage(hDlg, IDC_ASPECT_RATIO, CB_ADDSTRING, 0, (LPARAM)"16:9 PAL");
		SendDlgItemMessage(hDlg, IDC_ASPECT_RATIO, CB_ADDSTRING, 0, (LPARAM)"16:9 NTSC");
		SendDlgItemMessage(hDlg, IDC_ASPECT_RATIO, CB_ADDSTRING, 0, (LPARAM)"Custom...");
		break;

	case IDD_LEVEL :
		for (i=0; i<sizeof(profiles)/sizeof(profile_t); i++)
			SendDlgItemMessage(hDlg, IDC_LEVEL_PROFILE, CB_ADDSTRING, 0, (LPARAM)profiles[i].name);
		break;

	case IDD_BITRATE :
		SendDlgItemMessage(hDlg, IDC_BITRATE_CFORMAT, CB_ADDSTRING, 0, (LPARAM)"AVI-Legacy");
		SendDlgItemMessage(hDlg, IDC_BITRATE_CFORMAT, CB_ADDSTRING, 0, (LPARAM)"AVI-OpenDML");
		SendDlgItemMessage(hDlg, IDC_BITRATE_CFORMAT, CB_ADDSTRING, 0, (LPARAM)"Matroska");
		SendDlgItemMessage(hDlg, IDC_BITRATE_CFORMAT, CB_ADDSTRING, 0, (LPARAM)"OGM");
		SendDlgItemMessage(hDlg, IDC_BITRATE_CFORMAT, CB_ADDSTRING, 0, (LPARAM)"(None)");

		SendDlgItemMessage(hDlg, IDC_BITRATE_TSIZE, CB_ADDSTRING, 0, (LPARAM)"665600");
		SendDlgItemMessage(hDlg, IDC_BITRATE_TSIZE, CB_ADDSTRING, 0, (LPARAM)"716800");
		SendDlgItemMessage(hDlg, IDC_BITRATE_TSIZE, CB_ADDSTRING, 0, (LPARAM)"1331200");
		SendDlgItemMessage(hDlg, IDC_BITRATE_TSIZE, CB_ADDSTRING, 0, (LPARAM)"1433600");

		for (i=0; i<sizeof(video_fps_list)/sizeof(named_float_t); i++)
			SendDlgItemMessage(hDlg, IDC_BITRATE_FPS, CB_ADDSTRING, 0, (LPARAM)video_fps_list[i].name);

		for (i=0; i<sizeof(audio_type_list)/sizeof(named_int_t); i++)
			SendDlgItemMessage(hDlg, IDC_BITRATE_AFORMAT, CB_ADDSTRING, 0, (LPARAM)audio_type_list[i].name);

		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"32");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"56");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"64");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"96");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"112");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"128");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"160");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"192");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"224");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"256");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"384");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"448");
		SendDlgItemMessage(hDlg, IDC_BITRATE_ARATE, CB_ADDSTRING, 0, (LPARAM)"512");
		break;

	case IDD_ZONE :
		EnableDlgWindow(hDlg, IDC_ZONE_FETCH, config->ci_valid);
		break;

	case IDD_MOTION :
		SendDlgItemMessage(hDlg, IDC_MOTION, CB_ADDSTRING, 0, (LPARAM)"0 - None");
		SendDlgItemMessage(hDlg, IDC_MOTION, CB_ADDSTRING, 0, (LPARAM)"1 - Very Low");
		SendDlgItemMessage(hDlg, IDC_MOTION, CB_ADDSTRING, 0, (LPARAM)"2 - Low");
		SendDlgItemMessage(hDlg, IDC_MOTION, CB_ADDSTRING, 0, (LPARAM)"3 - Medium");
		SendDlgItemMessage(hDlg, IDC_MOTION, CB_ADDSTRING, 0, (LPARAM)"4 - High");
		SendDlgItemMessage(hDlg, IDC_MOTION, CB_ADDSTRING, 0, (LPARAM)"5 - Very High");
		SendDlgItemMessage(hDlg, IDC_MOTION, CB_ADDSTRING, 0, (LPARAM)"6 - Ultra High");

		SendDlgItemMessage(hDlg, IDC_VHQ, CB_ADDSTRING, 0, (LPARAM)"0 - Off");
		SendDlgItemMessage(hDlg, IDC_VHQ, CB_ADDSTRING, 0, (LPARAM)"1 - Mode Decision");
		SendDlgItemMessage(hDlg, IDC_VHQ, CB_ADDSTRING, 0, (LPARAM)"2 - Limited Search");
		SendDlgItemMessage(hDlg, IDC_VHQ, CB_ADDSTRING, 0, (LPARAM)"3 - Medium Search");
		SendDlgItemMessage(hDlg, IDC_VHQ, CB_ADDSTRING, 0, (LPARAM)"4 - Wide Search");

		SendDlgItemMessage(hDlg, IDC_VHQ_METRIC, CB_ADDSTRING, 0, (LPARAM)"0 - PSNR");
		SendDlgItemMessage(hDlg, IDC_VHQ_METRIC, CB_ADDSTRING, 0, (LPARAM)"1 - PSNR-HVS-M");
		break;

	case IDD_ENC :
		SendDlgItemMessage(hDlg, IDC_FOURCC, CB_ADDSTRING, 0, (LPARAM)"XVID");
		SendDlgItemMessage(hDlg, IDC_FOURCC, CB_ADDSTRING, 0, (LPARAM)"DIVX");
		SendDlgItemMessage(hDlg, IDC_FOURCC, CB_ADDSTRING, 0, (LPARAM)"DX50");
		break;

	case IDD_DEC :
		SendDlgItemMessage(hDlg, IDC_DEC_BRIGHTNESS, TBM_SETRANGE, (WPARAM)TRUE, (LPARAM)MAKELONG(-96, 96));
		SendDlgItemMessage(hDlg, IDC_DEC_BRIGHTNESS, TBM_SETTICFREQ, (WPARAM)16, (LPARAM)0);
		break;
	}
}


/* enable/disable controls based on encoder-mode or user selection */

static void adv_mode(HWND hDlg, int idd, CONFIG * config)
{
	int profile;
	int weight_en, quant_en;
	int cpu_force;
	int custom_quant, bvops;
	int ar_mode, ar_par;
	int qpel_checked, mot_srch_prec, vhq_enabled, bvhq_enabled;

	switch(idd) {
	case IDD_PROFILE :
		profile = SendDlgItemMessage(hDlg, IDC_PROFILE_PROFILE, CB_GETCURSEL, 0, 0);
		EnableDlgWindow(hDlg, IDC_BVOP, profiles[profile].flags&PROFILE_BVOP);

		EnableDlgWindow(hDlg, IDC_QUANTTYPE_S, profiles[profile].flags&PROFILE_MPEGQUANT);
		EnableDlgWindow(hDlg, IDC_QUANTTYPE_S, profiles[profile].flags&PROFILE_MPEGQUANT);
		EnableDlgWindow(hDlg, IDC_QUANTTYPE, profiles[profile].flags&PROFILE_MPEGQUANT);
		custom_quant = (profiles[profile].flags&PROFILE_MPEGQUANT) && SendDlgItemMessage(hDlg, IDC_QUANTTYPE, CB_GETCURSEL, 0, 0)==QUANT_MODE_CUSTOM;
		EnableDlgWindow(hDlg, IDC_QUANTMATRIX, custom_quant);
		EnableDlgWindow(hDlg, IDC_LUMMASK, profiles[profile].flags&PROFILE_ADAPTQUANT);
		EnableDlgWindow(hDlg, IDC_INTERLACING, profiles[profile].flags&PROFILE_INTERLACE);
		EnableDlgWindow(hDlg, IDC_TFF, IsDlgChecked(hDlg, IDC_INTERLACING));
		EnableDlgWindow(hDlg, IDC_QPEL, profiles[profile].flags&PROFILE_QPEL);
		EnableDlgWindow(hDlg, IDC_GMC, profiles[profile].flags&PROFILE_GMC);
		EnableDlgWindow(hDlg, IDC_SLICES, profiles[profile].flags&PROFILE_RESYNCMARKER);

		bvops = (profiles[profile].flags&PROFILE_BVOP) && IsDlgChecked(hDlg, IDC_BVOP);
		EnableDlgWindow(hDlg, IDC_MAXBFRAMES,	   bvops);
		EnableDlgWindow(hDlg, IDC_BQUANTRATIO,	  bvops);
		EnableDlgWindow(hDlg, IDC_BQUANTOFFSET,	 bvops);
		EnableDlgWindow(hDlg, IDC_MAXBFRAMES_S,	 bvops);
		EnableDlgWindow(hDlg, IDC_BQUANTRATIO_S,	bvops);
		EnableDlgWindow(hDlg, IDC_BQUANTOFFSET_S,   bvops);
		EnableDlgWindow(hDlg, IDC_PACKED,		   bvops && !(profiles[profile].flags & PROFILE_PACKED));

		switch(profile) {
		case 0:
			{
				HICON profile_icon = LoadImage(g_hInst, MAKEINTRESOURCE(IDI_MOBILE), IMAGE_ICON, 0, 0, 0);
				SendDlgItemMessage(hDlg, IDC_PROFILE_LOGO, STM_SETIMAGE, IMAGE_ICON, (LPARAM)profile_icon); 
			}
			break;
		case 1:
			{
				HICON profile_icon = LoadImage(g_hInst, MAKEINTRESOURCE(IDI_HOME), IMAGE_ICON, 0, 0, 0);
				SendDlgItemMessage(hDlg, IDC_PROFILE_LOGO, STM_SETIMAGE, IMAGE_ICON, (LPARAM)profile_icon); 
			}
			break;
		case 2:
			{
				HICON profile_icon = LoadImage(g_hInst, MAKEINTRESOURCE(IDI_HD720), IMAGE_ICON, 0, 0, 0);
				SendDlgItemMessage(hDlg, IDC_PROFILE_LOGO, STM_SETIMAGE, IMAGE_ICON, (LPARAM)profile_icon); 
			}
			break;
		case 3:
			{
				HICON profile_icon = LoadImage(g_hInst, MAKEINTRESOURCE(IDI_HD1080), IMAGE_ICON, 0, 0, 0);
				SendDlgItemMessage(hDlg, IDC_PROFILE_LOGO, STM_SETIMAGE, IMAGE_ICON, (LPARAM)profile_icon); 
			}
			break;
		default:
			SendDlgItemMessage(hDlg, IDC_PROFILE_LOGO, STM_SETIMAGE, IMAGE_ICON, (LPARAM)NULL); 
			break;
		}
		if (profile < 4) 
			ShowWindow(GetDlgItem(hDlg, IDC_PROFILE_LABEL), SW_HIDE);
		else
			ShowWindow(GetDlgItem(hDlg, IDC_PROFILE_LABEL), SW_SHOW);
		break;

	case IDD_AR:
		ar_mode = IsDlgChecked(hDlg, IDC_PAR);
		EnableDlgWindow(hDlg, IDC_ASPECT_RATIO, ar_mode);

		ar_par = SendDlgItemMessage(hDlg, IDC_ASPECT_RATIO, CB_GETCURSEL, 0, 0);
		if (ar_par == 5) { /* custom par */
			SetDlgItemInt(hDlg, IDC_PARY, config->par_y, FALSE);
			SetDlgItemInt(hDlg, IDC_PARX, config->par_x, FALSE);

			EnableDlgWindow(hDlg, IDC_PARX, ar_mode);
			EnableDlgWindow(hDlg, IDC_PARY, ar_mode);
		} else {
			SetDlgItemInt(hDlg, IDC_PARX, PARS[ar_par][0], FALSE);
			SetDlgItemInt(hDlg, IDC_PARY, PARS[ar_par][1], FALSE);
			EnableDlgWindow(hDlg, IDC_PARX, FALSE);
			EnableDlgWindow(hDlg, IDC_PARY, FALSE);
		}

		ar_mode = IsDlgChecked(hDlg, IDC_AR);

		config->ar_x = config_get_uint(hDlg, IDC_ARX, config->ar_x);
		config->ar_y = config_get_uint(hDlg, IDC_ARY, config->ar_y);

		EnableDlgWindow(hDlg, IDC_ARX, ar_mode);
		EnableDlgWindow(hDlg, IDC_ARY, ar_mode);
		break;

	case IDD_LEVEL :
		profile = SendDlgItemMessage(hDlg, IDC_LEVEL_PROFILE, CB_GETCURSEL, 0, 0);
		SetDlgItemInt(hDlg, IDC_LEVEL_WIDTH, profiles[profile].width, FALSE);
		SetDlgItemInt(hDlg, IDC_LEVEL_HEIGHT, profiles[profile].height, FALSE);
		SetDlgItemInt(hDlg, IDC_LEVEL_FPS, profiles[profile].fps, FALSE);
		SetDlgItemInt(hDlg, IDC_LEVEL_VMV, profiles[profile].max_vmv_buffer_sz, FALSE);
		SetDlgItemInt(hDlg, IDC_LEVEL_VCV, profiles[profile].vcv_decoder_rate, FALSE);
		SetDlgItemInt(hDlg, IDC_LEVEL_VBV, profiles[profile].max_vbv_size, FALSE);
    set_dlgitem_float1000(hDlg, IDC_LEVEL_BITRATE, profiles[profile].max_bitrate);
    SetDlgItemInt(hDlg, IDC_LEVEL_PEAKRATE, profiles[profile].vbv_peakrate, FALSE);

    {
      int en_dim = profiles[profile].width && profiles[profile].height;
      int en_vmv = profiles[profile].max_vmv_buffer_sz;
      int en_vcv = profiles[profile].vcv_decoder_rate;
      EnableDlgWindow(hDlg, IDC_LEVEL_LEVEL_G, en_dim || en_vmv || en_vcv);
      EnableDlgWindow(hDlg, IDC_LEVEL_DIM_S, en_dim);
      EnableDlgWindow(hDlg, IDC_LEVEL_WIDTH, en_dim);
      EnableDlgWindow(hDlg, IDC_LEVEL_HEIGHT,en_dim);
      EnableDlgWindow(hDlg, IDC_LEVEL_FPS,   en_dim);
      EnableDlgWindow(hDlg, IDC_LEVEL_VMV_S, en_vmv);
      EnableDlgWindow(hDlg, IDC_LEVEL_VMV,   en_vmv);
      EnableDlgWindow(hDlg, IDC_LEVEL_VCV_S, en_vcv);
      EnableDlgWindow(hDlg, IDC_LEVEL_VCV,   en_vcv);
    }
    {
      int en_vbv = profiles[profile].max_vbv_size;
      int en_br = profiles[profile].max_bitrate;
      int en_pr = profiles[profile].vbv_peakrate;

      EnableDlgWindow(hDlg, IDC_LEVEL_VBV_G,      en_vbv || en_br || en_pr);
      EnableDlgWindow(hDlg, IDC_LEVEL_VBV_S,      en_vbv);
      EnableDlgWindow(hDlg, IDC_LEVEL_VBV,        en_vbv);
      EnableDlgWindow(hDlg, IDC_LEVEL_BITRATE_S,  en_br);
      EnableDlgWindow(hDlg, IDC_LEVEL_BITRATE,    en_br);
      EnableDlgWindow(hDlg, IDC_LEVEL_PEAKRATE_S, en_pr);
      EnableDlgWindow(hDlg, IDC_LEVEL_PEAKRATE,   en_pr);
    }

		switch(profile) {
		case 0:
			{
				HICON profile_icon = LoadImage(g_hInst, MAKEINTRESOURCE(IDI_MOBILE), IMAGE_ICON, 0, 0, 0);
				SendDlgItemMessage(hDlg, IDC_PROFILE_LOGO, STM_SETIMAGE, IMAGE_ICON, (LPARAM)profile_icon); 
			}
			break;
		case 1:
			{
				HICON profile_icon = LoadImage(g_hInst, MAKEINTRESOURCE(IDI_HOME), IMAGE_ICON, 0, 0, 0);
				SendDlgItemMessage(hDlg, IDC_PROFILE_LOGO, STM_SETIMAGE, IMAGE_ICON, (LPARAM)profile_icon); 
			}
			break;
		case 2:
			{
				HICON profile_icon = LoadImage(g_hInst, MAKEINTRESOURCE(IDI_HD720), IMAGE_ICON, 0, 0, 0);
				SendDlgItemMessage(hDlg, IDC_PROFILE_LOGO, STM_SETIMAGE, IMAGE_ICON, (LPARAM)profile_icon); 
			}
			break;
		case 3:
			{
				HICON profile_icon = LoadImage(g_hInst, MAKEINTRESOURCE(IDI_HD1080), IMAGE_ICON, 0, 0, 0);
				SendDlgItemMessage(hDlg, IDC_PROFILE_LOGO, STM_SETIMAGE, IMAGE_ICON, (LPARAM)profile_icon); 
			}
			break;
		default:
			SendDlgItemMessage(hDlg, IDC_PROFILE_LOGO, STM_SETIMAGE, IMAGE_ICON, (LPARAM)NULL); 
			break;
		}
		if (profile < 4) 
			ShowWindow(GetDlgItem(hDlg, IDC_PROFILE_LABEL), SW_HIDE);
		else
			ShowWindow(GetDlgItem(hDlg, IDC_PROFILE_LABEL), SW_SHOW);

		break;

	case IDD_BITRATE :
		{
			int ctype = SendDlgItemMessage(hDlg, IDC_BITRATE_CFORMAT, CB_GETCURSEL, 0, 0);
			int target_size = config_get_cbuint(hDlg, IDC_BITRATE_TSIZE, 0);
			int subtitle_size = config_get_uint(hDlg, IDC_BITRATE_SSIZE, 0);
			int fps = SendDlgItemMessage(hDlg, IDC_BITRATE_FPS, CB_GETCURSEL, 0, 0);

			int duration = 
				3600 * config_get_uint(hDlg, IDC_BITRATE_HOURS, 0) +
				60 * config_get_uint(hDlg, IDC_BITRATE_MINUTES, 0) +
				config_get_uint(hDlg, IDC_BITRATE_SECONDS, 0);

			int audio_type = SendDlgItemMessage(hDlg, IDC_BITRATE_AFORMAT, CB_GETCURSEL, 0, 0);
			int audio_mode = IsDlgChecked(hDlg, IDC_BITRATE_AMODE_SIZE);
			int audio_rate = config_get_cbuint(hDlg, IDC_BITRATE_ARATE, 0);
			int audio_size = config_get_uint(hDlg, IDC_BITRATE_ASIZE, 0);

			int frames;
			int overhead;
			int vsize;

			if (duration == 0) 
				break;

			if (fps < 0 || fps >= sizeof(video_fps_list)/sizeof(named_float_t)) {
				fps = 0;
			}
			if (audio_type < 0 || audio_type >= sizeof(audio_type_list)/sizeof(named_int_t)) {
				audio_type = 0;
			}

			EnableDlgWindow(hDlg, IDC_BITRATE_AMODE_RATE, audio_type!=NO_AUDIO);
			EnableDlgWindow(hDlg, IDC_BITRATE_AMODE_SIZE, audio_type!=NO_AUDIO);
			EnableDlgWindow(hDlg, IDC_BITRATE_ARATE, audio_type!=NO_AUDIO && !audio_mode);
			EnableDlgWindow(hDlg, IDC_BITRATE_ASIZE, audio_type!=NO_AUDIO && audio_mode);
			EnableDlgWindow(hDlg, IDC_BITRATE_ASELECT, audio_type!=NO_AUDIO && audio_mode);

			/* step 1: calculate number of frames */
			frames = (int)(duration * video_fps_list[fps].value);
			
			/* step 2: calculate audio_size (kbytes)*/
			if (audio_type!=NO_AUDIO) {
				if (audio_mode==0) {
					int new_audio_size = (int)( (1000.0 * duration * audio_rate) / (8.0*1024) );
					
					/* this check is needed to avoid a loop */
					if (new_audio_size!=audio_size) {
						audio_size = new_audio_size;
						SetDlgItemInt(hDlg, IDC_BITRATE_ASIZE, new_audio_size, TRUE);
					}
				}else{
					int tmp_rate = (int)( (audio_size * 8.0 * 1024) / (1000.0 * duration) );
					SetDlgItemInt(hDlg, IDC_BITRATE_ARATE, tmp_rate, TRUE);
				}
			}else{
				audio_size = 0;
			}

			/* step 3: calculate container overhead */

			switch(ctype) {
			case 0 :	/* AVI */
			case 1 :	/* AVI-OpenDML */

				overhead = frames;

				if (audio_type!=NO_AUDIO) {
					overhead += (duration * 1000) / audio_type_list[audio_type].avi_interval;
				}

				overhead *= (ctype==0) ? 24 : 16;

				overhead /= 1024;
				break;

			case 2 :	/* Matroska: gknot formula */

				/* common overhead */
				overhead = 40 + 12 + 8+ 16*duration + 200 + 100*1/*one audio stream*/ + 11*duration;

				/* video overhead */
				overhead += frames*8 + (int)(frames * 4 * 0.94);

				/* cue tables and menu seek entries (300k default) */
				overhead += 300 * 1024;

				/* audio */
				overhead += (int)(duration * audio_type_list[audio_type].mkv_multiplier);

				overhead /= 1024;
				break;

			case 3 :	/* alexnoe formula */
				overhead = (int)( (target_size - subtitle_size) * (28.0/4224.0 + (1.0/255.0)) );
				break;

			default	:	/* (none) */
				overhead = 0;
				break;
			}

			SetDlgItemInt(hDlg, IDC_BITRATE_COVERHEAD, overhead, TRUE);

			/* final video bitstream size */
			vsize = target_size - subtitle_size - audio_size - overhead;
			if (vsize > 0) {
				SetDlgItemInt(hDlg, IDC_BITRATE_VSIZE, vsize, TRUE);
				/* convert from kbytes to kbits-per-second */
				SetDlgItemInt(hDlg, IDC_BITRATE_VRATE, (int)(((__int64)vsize * 8 * 128) / (duration * 125)), TRUE);
			}else{
				SetDlgItemText(hDlg, IDC_BITRATE_VSIZE, "Overflow");
				SetDlgItemText(hDlg, IDC_BITRATE_VRATE, "Overflow");
			}

		}
		break;

	case IDD_ZONE :
		weight_en = IsDlgChecked(hDlg, IDC_ZONE_MODE_WEIGHT);
		quant_en =   IsDlgChecked(hDlg, IDC_ZONE_MODE_QUANT);
		EnableDlgWindow(hDlg, IDC_ZONE_WEIGHT, weight_en);
		EnableDlgWindow(hDlg, IDC_ZONE_QUANT, quant_en);
		EnableDlgWindow(hDlg, IDC_ZONE_SLIDER, weight_en|quant_en);

		if (weight_en) {
			SendDlgItemMessage(hDlg, IDC_ZONE_SLIDER, TBM_SETRANGE, TRUE, MAKELONG(001,200));
			SendDlgItemMessage(hDlg, IDC_ZONE_SLIDER, TBM_SETPOS, TRUE, get_dlgitem_float(hDlg, IDC_ZONE_WEIGHT, 100));
			SetDlgItemText(hDlg, IDC_ZONE_MIN, "0.01");
			SetDlgItemText(hDlg, IDC_ZONE_MAX, "2.00");
		}else if (quant_en) {
			SendDlgItemMessage(hDlg, IDC_ZONE_SLIDER, TBM_SETRANGE, TRUE, MAKELONG(100,3100));
			SendDlgItemMessage(hDlg, IDC_ZONE_SLIDER, TBM_SETPOS, TRUE, get_dlgitem_float(hDlg, IDC_ZONE_QUANT, 100));
			SetDlgItemText(hDlg, IDC_ZONE_MIN, "1");
			SetDlgItemText(hDlg, IDC_ZONE_MAX, "31");
		}

		bvops = (profiles[config->profile].flags&PROFILE_BVOP) && config->use_bvop;
		EnableDlgWindow(hDlg, IDC_ZONE_BVOPTHRESHOLD_S, bvops);
		EnableDlgWindow(hDlg, IDC_ZONE_BVOPTHRESHOLD, bvops);
		break;

	case IDD_COMMON :
		cpu_force			= IsDlgChecked(hDlg, IDC_CPU_FORCE);
		EnableDlgWindow(hDlg, IDC_CPU_MMX,		cpu_force);
		EnableDlgWindow(hDlg, IDC_CPU_MMXEXT,	cpu_force);
		EnableDlgWindow(hDlg, IDC_CPU_SSE,		cpu_force);
		EnableDlgWindow(hDlg, IDC_CPU_SSE2,		cpu_force);
		EnableDlgWindow(hDlg, IDC_CPU_SSE3,		cpu_force);
		EnableDlgWindow(hDlg, IDC_CPU_SSE4, 	cpu_force);
        EnableDlgWindow(hDlg, IDC_CPU_3DNOW,	cpu_force);
		EnableDlgWindow(hDlg, IDC_CPU_3DNOWEXT,	cpu_force);
		EnableDlgWindow(hDlg, IDC_NUMTHREADS,   cpu_force);
		EnableDlgWindow(hDlg, IDC_NUMTHREADS_STATIC,   cpu_force);
		break;

	case IDD_MOTION:
		{
			const int userdef = (config->quality==quality_table_num);
			if (userdef) {
				bvops = (profiles[config->profile].flags&PROFILE_BVOP) && config->use_bvop;
				qpel_checked = (profiles[config->profile].flags&PROFILE_QPEL) && config->qpel;
				mot_srch_prec = SendDlgItemMessage(hDlg, IDC_MOTION, CB_GETCURSEL, 0, 0);
				vhq_enabled = SendDlgItemMessage(hDlg, IDC_VHQ, CB_GETCURSEL, 0, 0);
				bvhq_enabled = IsDlgButtonChecked(hDlg, IDC_VHQ_BFRAME);
				EnableDlgWindow(hDlg, IDC_VHQ, mot_srch_prec);
				EnableDlgWindow(hDlg, IDC_VHQ_BFRAME, mot_srch_prec && bvops && vhq_enabled);
				EnableDlgWindow(hDlg, IDC_CHROMAME, mot_srch_prec);
				EnableDlgWindow(hDlg, IDC_TURBO, mot_srch_prec && (bvops || qpel_checked));
				EnableDlgWindow(hDlg, IDC_VHQ_METRIC, mot_srch_prec && (vhq_enabled || bvhq_enabled));
				EnableDlgWindow(hDlg, IDC_FRAMEDROP, mot_srch_prec);
				EnableDlgWindow(hDlg, IDC_MAXKEY, mot_srch_prec);
			}
			break;
		}
	}
}


/* upload config data into dialog */
static void adv_upload(HWND hDlg, int idd, CONFIG * config)
{
	switch (idd)
	{
	case IDD_PROFILE :
		SendDlgItemMessage(hDlg, IDC_PROFILE_PROFILE, CB_SETCURSEL, config->profile, 0);

		SendDlgItemMessage(hDlg, IDC_QUANTTYPE, CB_SETCURSEL, config->quant_type, 0);
		SendDlgItemMessage(hDlg, IDC_LUMMASK, CB_SETCURSEL, config->lum_masking, 0);
  		CheckDlg(hDlg, IDC_INTERLACING, config->interlacing);
		CheckDlg(hDlg, IDC_TFF, config->tff);
		CheckDlg(hDlg, IDC_QPEL, config->qpel);
  		CheckDlg(hDlg, IDC_GMC, config->gmc);
		CheckDlg(hDlg, IDC_SLICES, (config->num_slices != 1));
		CheckDlg(hDlg, IDC_BVOP, config->use_bvop);

		SetDlgItemInt(hDlg, IDC_MAXBFRAMES, config->max_bframes, FALSE);
		set_dlgitem_float(hDlg, IDC_BQUANTRATIO, config->bquant_ratio);
		set_dlgitem_float(hDlg, IDC_BQUANTOFFSET, config->bquant_offset);
		CheckDlg(hDlg, IDC_PACKED, config->packed);

		break;
	case IDD_AR:
		CheckRadioButton(hDlg, IDC_AR, IDC_PAR, config->ar_mode == 0 ? IDC_PAR : IDC_AR);
		SendDlgItemMessage(hDlg, IDC_ASPECT_RATIO, CB_SETCURSEL, (config->display_aspect_ratio), 0);
		SetDlgItemInt(hDlg, IDC_ARX, config->ar_x, FALSE);
		SetDlgItemInt(hDlg, IDC_ARY, config->ar_y, FALSE);
		break;

	case IDD_LEVEL :
		SendDlgItemMessage(hDlg, IDC_LEVEL_PROFILE, CB_SETCURSEL, config->profile, 0);
		break;

	case IDD_RC_CBR :
		SetDlgItemInt(hDlg, IDC_CBR_REACTIONDELAY, config->rc_reaction_delay_factor, FALSE);
		SetDlgItemInt(hDlg, IDC_CBR_AVERAGINGPERIOD, config->rc_averaging_period, FALSE);
		SetDlgItemInt(hDlg, IDC_CBR_BUFFER, config->rc_buffer, FALSE);
		break;

	case IDD_RC_2PASS1 :
		SetDlgItemText(hDlg, IDC_STATS, config->stats);
		CheckDlg(hDlg, IDC_DISCARD1PASS, config->discard1pass);
		CheckDlg(hDlg, IDC_FULL1PASS, config->full1pass);
		break;

	case IDD_RC_2PASS2 :
		SetDlgItemText(hDlg, IDC_STATS, config->stats);
		SetDlgItemInt(hDlg, IDC_KFBOOST, config->keyframe_boost, FALSE);
		SetDlgItemInt(hDlg, IDC_KFREDUCTION, config->kfreduction, FALSE);

		SetDlgItemInt(hDlg, IDC_OVERFLOW_CONTROL_STRENGTH, config->overflow_control_strength, FALSE);
		SetDlgItemInt(hDlg, IDC_OVERIMP, config->twopass_max_overflow_improvement, FALSE);
		SetDlgItemInt(hDlg, IDC_OVERDEG, config->twopass_max_overflow_degradation, FALSE);

		SetDlgItemInt(hDlg, IDC_CURVECOMPH, config->curve_compression_high, FALSE);
		SetDlgItemInt(hDlg, IDC_CURVECOMPL, config->curve_compression_low, FALSE);
		SetDlgItemInt(hDlg, IDC_MINKEY, config->kfthreshold, FALSE);
		break;

	case IDD_BITRATE :
		SendDlgItemMessage(hDlg, IDC_BITRATE_CFORMAT, CB_SETCURSEL, config->container_type, 0);
		SetDlgItemInt(hDlg, IDC_BITRATE_TSIZE, config->target_size, FALSE);
		SetDlgItemInt(hDlg, IDC_BITRATE_SSIZE, config->subtitle_size, FALSE);

		SetDlgItemInt(hDlg, IDC_BITRATE_HOURS, config->hours, FALSE);
		SetDlgItemInt(hDlg, IDC_BITRATE_MINUTES, config->minutes, FALSE);
		SetDlgItemInt(hDlg, IDC_BITRATE_SECONDS, config->seconds, FALSE);
		SendDlgItemMessage(hDlg, IDC_BITRATE_FPS, CB_SETCURSEL, config->fps, 0);
		
		SendDlgItemMessage(hDlg, IDC_BITRATE_AFORMAT, CB_SETCURSEL, config->audio_type, 0);
		CheckRadioButton(hDlg, IDC_BITRATE_AMODE_RATE, IDC_BITRATE_AMODE_SIZE, config->audio_mode == 0 ? IDC_BITRATE_AMODE_RATE : IDC_BITRATE_AMODE_SIZE);
		SetDlgItemInt(hDlg, IDC_BITRATE_ARATE, config->audio_rate, FALSE);
		SetDlgItemInt(hDlg, IDC_BITRATE_ASIZE, config->audio_size, FALSE);
		break;

	case IDD_ZONE :
		SetDlgItemInt(hDlg, IDC_ZONE_FRAME, config->zones[config->cur_zone].frame, FALSE);

		CheckDlgButton(hDlg, IDC_ZONE_MODE_WEIGHT,   config->zones[config->cur_zone].mode == RC_ZONE_WEIGHT);
		CheckDlgButton(hDlg, IDC_ZONE_MODE_QUANT,		 config->zones[config->cur_zone].mode == RC_ZONE_QUANT);

		set_dlgitem_float(hDlg, IDC_ZONE_WEIGHT, config->zones[config->cur_zone].weight);
		set_dlgitem_float(hDlg, IDC_ZONE_QUANT, config->zones[config->cur_zone].quant);
	 
		CheckDlgButton(hDlg, IDC_ZONE_FORCEIVOP, config->zones[config->cur_zone].type==XVID_TYPE_IVOP);
		CheckDlgButton(hDlg, IDC_ZONE_GREYSCALE, config->zones[config->cur_zone].greyscale);
		CheckDlgButton(hDlg, IDC_ZONE_CHROMAOPT, config->zones[config->cur_zone].chroma_opt);

		CheckDlg(hDlg, IDC_CARTOON, config->zones[config->cur_zone].cartoon_mode);

		SetDlgItemInt(hDlg, IDC_ZONE_BVOPTHRESHOLD, config->zones[config->cur_zone].bvop_threshold, TRUE);
		break;

	case IDD_MOTION :
    {
    const int userdef = (config->quality==quality_table_num);
    const quality_t* quality_preset = userdef ? &config->quality_user : &quality_table[config->quality];
    int bvops = (profiles[config->profile].flags&PROFILE_BVOP) && config->use_bvop;
    int qpel_checked = (profiles[config->profile].flags&PROFILE_QPEL) && config->qpel;
    int bvops_qpel_motion = (bvops || qpel_checked) && quality_preset->motion_search;
    int vhq_or_bvhq = quality_preset->vhq_mode || quality_preset->vhq_bframe;

		SendDlgItemMessage(hDlg, IDC_MOTION, CB_SETCURSEL, quality_preset->motion_search, 0);
		SendDlgItemMessage(hDlg, IDC_VHQ, CB_SETCURSEL, quality_preset->vhq_mode, 0);
		SendDlgItemMessage(hDlg, IDC_VHQ_METRIC, CB_SETCURSEL, quality_preset->vhq_metric, 0);
		CheckDlg(hDlg, IDC_VHQ_BFRAME, quality_preset->vhq_bframe);
		CheckDlg(hDlg, IDC_CHROMAME, quality_preset->chromame);
		CheckDlg(hDlg, IDC_TURBO, quality_preset->turbo);
		SetDlgItemInt(hDlg, IDC_FRAMEDROP, quality_preset->frame_drop_ratio, FALSE);
		SetDlgItemInt(hDlg, IDC_MAXKEY, quality_preset->max_key_interval, FALSE);

    EnableDlgWindow(hDlg, IDC_MOTION,     userdef);
    EnableDlgWindow(hDlg, IDC_VHQ,        userdef && quality_preset->motion_search);
    EnableDlgWindow(hDlg, IDC_VHQ_METRIC, userdef && vhq_or_bvhq);
    EnableDlgWindow(hDlg, IDC_VHQ_BFRAME, userdef && bvops);
    EnableDlgWindow(hDlg, IDC_CHROMAME,   userdef && quality_preset->motion_search);
    EnableDlgWindow(hDlg, IDC_TURBO,      userdef && bvops_qpel_motion);
    EnableDlgWindow(hDlg, IDC_FRAMEDROP,  userdef && quality_preset->motion_search);
    EnableDlgWindow(hDlg, IDC_MAXKEY,     userdef && quality_preset->motion_search);

		break;
    }

	case IDD_QUANT :
    {
    const int userdef = (config->quality==quality_table_num);
    const quality_t* quality_preset = userdef ? &config->quality_user : &quality_table[config->quality];

    SetDlgItemInt(hDlg, IDC_MINIQUANT, quality_preset->min_iquant, FALSE);
		SetDlgItemInt(hDlg, IDC_MAXIQUANT, quality_preset->max_iquant, FALSE);
		SetDlgItemInt(hDlg, IDC_MINPQUANT, quality_preset->min_pquant, FALSE);
		SetDlgItemInt(hDlg, IDC_MAXPQUANT, quality_preset->max_pquant, FALSE);
		SetDlgItemInt(hDlg, IDC_MINBQUANT, quality_preset->min_bquant, FALSE);
		SetDlgItemInt(hDlg, IDC_MAXBQUANT, quality_preset->max_bquant, FALSE);
		CheckDlg(hDlg, IDC_TRELLISQUANT, quality_preset->trellis_quant);

    EnableDlgWindow(hDlg, IDC_MINIQUANT, userdef);
    EnableDlgWindow(hDlg, IDC_MAXIQUANT, userdef);
    EnableDlgWindow(hDlg, IDC_MINPQUANT, userdef);
    EnableDlgWindow(hDlg, IDC_MAXPQUANT, userdef);
    EnableDlgWindow(hDlg, IDC_MINBQUANT, userdef);
    EnableDlgWindow(hDlg, IDC_MAXBQUANT, userdef);
    EnableDlgWindow(hDlg, IDC_TRELLISQUANT, userdef);
		break;
    }

	case IDD_COMMON :
		CheckDlg(hDlg, IDC_CPU_MMX, (config->cpu & XVID_CPU_MMX));
		CheckDlg(hDlg, IDC_CPU_MMXEXT, (config->cpu & XVID_CPU_MMXEXT));
		CheckDlg(hDlg, IDC_CPU_SSE, (config->cpu & XVID_CPU_SSE));
		CheckDlg(hDlg, IDC_CPU_SSE2, (config->cpu & XVID_CPU_SSE2));
		CheckDlg(hDlg, IDC_CPU_SSE3, (config->cpu & XVID_CPU_SSE3));
		CheckDlg(hDlg, IDC_CPU_SSE4, (config->cpu & XVID_CPU_SSE41));
        CheckDlg(hDlg, IDC_CPU_3DNOW, (config->cpu & XVID_CPU_3DNOW));
		CheckDlg(hDlg, IDC_CPU_3DNOWEXT, (config->cpu & XVID_CPU_3DNOWEXT));

		CheckRadioButton(hDlg, IDC_CPU_AUTO, IDC_CPU_FORCE,
			config->cpu & XVID_CPU_FORCE ? IDC_CPU_FORCE : IDC_CPU_AUTO );
		set_dlgitem_hex(hDlg, IDC_DEBUG, config->debug);
		SetDlgItemInt(hDlg, IDC_NUMTHREADS, config->num_threads, FALSE);
    break;

  case IDD_ENC:
		if(profiles[config->profile].flags & PROFILE_XVID)
			SendDlgItemMessage(hDlg, IDC_FOURCC, CB_SETCURSEL, 0, 0);
		else
			SendDlgItemMessage(hDlg, IDC_FOURCC, CB_SETCURSEL, config->fourcc_used, 0);
		EnableDlgWindow(hDlg, IDC_FOURCC, (!(profiles[config->profile].flags & PROFILE_XVID)));
		CheckDlg(hDlg, IDC_VOPDEBUG, config->vop_debug);
		CheckDlg(hDlg, IDC_DISPLAY_STATUS, config->display_status);
		break;

	case IDD_DEC :
		SendDlgItemMessage(hDlg, IDC_DEC_BRIGHTNESS, TBM_SETPOS, (WPARAM)TRUE, (LPARAM)pp_brightness);
		CheckDlg(hDlg, IDC_DEC_DY,	pp_dy);
		CheckDlg(hDlg, IDC_DEC_DUV,	pp_duv);
		CheckDlg(hDlg, IDC_DEC_DRY,	pp_dry);
		CheckDlg(hDlg, IDC_DEC_DRUV,pp_druv);
		CheckDlg(hDlg, IDC_DEC_FE,	pp_fe);
		EnableDlgWindow(hDlg, IDC_DEC_DRY, pp_dy);
		EnableDlgWindow(hDlg, IDC_DEC_DRUV, pp_duv);
		break;
	}
}


/* download config data from dialog */

static void adv_download(HWND hDlg, int idd, CONFIG * config)
{
	switch (idd)
	{
	case IDD_PROFILE :
		config->profile = SendDlgItemMessage(hDlg, IDC_PROFILE_PROFILE, CB_GETCURSEL, 0, 0);

		config->quant_type = SendDlgItemMessage(hDlg, IDC_QUANTTYPE, CB_GETCURSEL, 0, 0);
		config->lum_masking = SendDlgItemMessage(hDlg, IDC_LUMMASK, CB_GETCURSEL, 0, 0);
		config->interlacing = IsDlgChecked(hDlg, IDC_INTERLACING);
		config->tff = IsDlgChecked(hDlg, IDC_TFF);
		config->qpel = IsDlgChecked(hDlg, IDC_QPEL);
		config->gmc = IsDlgChecked(hDlg, IDC_GMC);
		config->num_slices = (IsDlgChecked(hDlg, IDC_SLICES) ? ((config->num_slices < 2) ? 0 : config->num_slices) : 1);

		config->use_bvop = IsDlgChecked(hDlg, IDC_BVOP);
		config->max_bframes = config_get_uint(hDlg, IDC_MAXBFRAMES, config->max_bframes);
		config->bquant_ratio = get_dlgitem_float(hDlg, IDC_BQUANTRATIO, config->bquant_ratio);
		config->bquant_offset = get_dlgitem_float(hDlg, IDC_BQUANTOFFSET, config->bquant_offset);
		config->packed = IsDlgChecked(hDlg, IDC_PACKED);
		break;

	case IDD_AR:
		config->ar_mode = IsDlgChecked(hDlg, IDC_PAR) ? 0:1;
		config->ar_x = config_get_uint(hDlg, IDC_ARX, config->ar_x);
		config->ar_y = config_get_uint(hDlg, IDC_ARY, config->ar_y);
		config->display_aspect_ratio = SendDlgItemMessage(hDlg, IDC_ASPECT_RATIO, CB_GETCURSEL, 0, 0);
		if (config->display_aspect_ratio == 5) {
			config->par_x = config_get_uint(hDlg, IDC_PARX, config->par_x);
			config->par_y = config_get_uint(hDlg, IDC_PARY, config->par_y);
		}
		break;

	case IDD_LEVEL :
		config->profile = SendDlgItemMessage(hDlg, IDC_LEVEL_PROFILE, CB_GETCURSEL, 0, 0);
		break;

	case IDD_RC_CBR :
		config->rc_reaction_delay_factor = config_get_uint(hDlg, IDC_CBR_REACTIONDELAY, config->rc_reaction_delay_factor);
		config->rc_averaging_period = config_get_uint(hDlg, IDC_CBR_AVERAGINGPERIOD, config->rc_averaging_period);
		config->rc_buffer = config_get_uint(hDlg, IDC_CBR_BUFFER, config->rc_buffer);
		break;

	case IDD_RC_2PASS1 :
		if (GetDlgItemText(hDlg, IDC_STATS, config->stats, MAX_PATH) == 0)
			lstrcpy(config->stats, CONFIG_2PASS_FILE);
		config->discard1pass = IsDlgChecked(hDlg, IDC_DISCARD1PASS);
		config->full1pass = IsDlgChecked(hDlg, IDC_FULL1PASS);
		break;

	case IDD_RC_2PASS2 :
		if (GetDlgItemText(hDlg, IDC_STATS, config->stats, MAX_PATH) == 0)
			lstrcpy(config->stats, CONFIG_2PASS_FILE);

		config->keyframe_boost = GetDlgItemInt(hDlg, IDC_KFBOOST, NULL, FALSE);
		config->kfreduction = GetDlgItemInt(hDlg, IDC_KFREDUCTION, NULL, FALSE);
		CONSTRAINVAL(config->keyframe_boost, 0, 1000);

		config->overflow_control_strength = GetDlgItemInt(hDlg, IDC_OVERFLOW_CONTROL_STRENGTH, NULL, FALSE);
		config->twopass_max_overflow_improvement = config_get_uint(hDlg, IDC_OVERIMP, config->twopass_max_overflow_improvement);
		config->twopass_max_overflow_degradation = config_get_uint(hDlg, IDC_OVERDEG, config->twopass_max_overflow_degradation);
		CONSTRAINVAL(config->twopass_max_overflow_improvement, 1, 80);
		CONSTRAINVAL(config->twopass_max_overflow_degradation, 1, 80);
		CONSTRAINVAL(config->overflow_control_strength, 0, 100);

		config->curve_compression_high = GetDlgItemInt(hDlg, IDC_CURVECOMPH, NULL, FALSE);
		config->curve_compression_low = GetDlgItemInt(hDlg, IDC_CURVECOMPL, NULL, FALSE);
		CONSTRAINVAL(config->curve_compression_high, 0, 100);
		CONSTRAINVAL(config->curve_compression_low, 0, 100);

		config->kfthreshold = config_get_uint(hDlg, IDC_MINKEY, config->kfthreshold);

		break;

	case IDD_BITRATE :
		config->container_type = SendDlgItemMessage(hDlg, IDC_BITRATE_CFORMAT, CB_GETCURSEL, 0, 0);
		config->target_size = config_get_uint(hDlg, IDC_BITRATE_TSIZE, config->target_size);
		config->subtitle_size = config_get_uint(hDlg, IDC_BITRATE_SSIZE, config->subtitle_size);

		config->hours = config_get_uint(hDlg, IDC_BITRATE_HOURS, config->hours);
		config->minutes = config_get_uint(hDlg, IDC_BITRATE_MINUTES, config->minutes);
		config->seconds = config_get_uint(hDlg, IDC_BITRATE_SECONDS, config->seconds);
		config->fps = SendDlgItemMessage(hDlg, IDC_BITRATE_FPS, CB_GETCURSEL, 0, 0);

		config->audio_type = SendDlgItemMessage(hDlg, IDC_BITRATE_AFORMAT, CB_GETCURSEL, 0, 0);
		config->audio_mode = IsDlgChecked(hDlg, IDC_BITRATE_AMODE_SIZE) ? 1 : 0 ;
		config->audio_rate = config_get_uint(hDlg, IDC_BITRATE_ARATE, config->audio_rate);
		config->audio_size = config_get_uint(hDlg, IDC_BITRATE_ASIZE, config->audio_size);

		/* the main window uses "AVI bitrate/filesize" not "video bitrate/filesize",
		   so we have to compensate by frames * 24 bytes */
		{
			int frame_compensate = 24 * (int)(
				(3600*config->hours +
				   60*config->minutes + 
				      config->seconds) * video_fps_list[config->fps].value) / 1024;

			int bitrate_compensate = (int)(24 * video_fps_list[config->fps].value) / 125;

			config->desired_size = 
						config_get_uint(hDlg, IDC_BITRATE_VSIZE, config->desired_size) + frame_compensate;
			
			config->bitrate = 
						config_get_uint(hDlg, IDC_BITRATE_VRATE, config->bitrate) + bitrate_compensate;
		}
		break;

	case IDD_ZONE :
		config->zones[config->cur_zone].frame = config_get_uint(hDlg, IDC_ZONE_FRAME, config->zones[config->cur_zone].frame);

		if (IsDlgChecked(hDlg, IDC_ZONE_MODE_WEIGHT)) {
			config->zones[config->cur_zone].mode = RC_ZONE_WEIGHT;
		}else if (IsDlgChecked(hDlg, IDC_ZONE_MODE_QUANT)) {
			config->zones[config->cur_zone].mode = RC_ZONE_QUANT;
		}

		config->zones[config->cur_zone].weight = get_dlgitem_float(hDlg, IDC_ZONE_WEIGHT, config->zones[config->cur_zone].weight);
		config->zones[config->cur_zone].quant =  get_dlgitem_float(hDlg, IDC_ZONE_QUANT, config->zones[config->cur_zone].quant);

		config->zones[config->cur_zone].type = IsDlgButtonChecked(hDlg, IDC_ZONE_FORCEIVOP)?XVID_TYPE_IVOP:XVID_TYPE_AUTO;
		config->zones[config->cur_zone].greyscale = IsDlgButtonChecked(hDlg, IDC_ZONE_GREYSCALE);
		config->zones[config->cur_zone].chroma_opt = IsDlgButtonChecked(hDlg, IDC_ZONE_CHROMAOPT);

		config->zones[config->cur_zone].bvop_threshold = config_get_int(hDlg, IDC_ZONE_BVOPTHRESHOLD, config->zones[config->cur_zone].bvop_threshold);
		config->zones[config->cur_zone].cartoon_mode = IsDlgChecked(hDlg, IDC_CARTOON);
		break;

	case IDD_MOTION :
    if (config->quality==quality_table_num) {
      config->quality_user.motion_search = SendDlgItemMessage(hDlg, IDC_MOTION, CB_GETCURSEL, 0, 0);
		  config->quality_user.vhq_mode = SendDlgItemMessage(hDlg, IDC_VHQ, CB_GETCURSEL, 0, 0);
		  config->quality_user.vhq_metric = SendDlgItemMessage(hDlg, IDC_VHQ_METRIC, CB_GETCURSEL, 0, 0);
		  config->quality_user.vhq_bframe = IsDlgButtonChecked(hDlg, IDC_VHQ_BFRAME);
		  config->quality_user.chromame = IsDlgChecked(hDlg, IDC_CHROMAME);
		  config->quality_user.turbo = IsDlgChecked(hDlg, IDC_TURBO);

		  config->quality_user.frame_drop_ratio = config_get_uint(hDlg, IDC_FRAMEDROP, config->quality_user.frame_drop_ratio);

		  config->quality_user.max_key_interval = config_get_uint(hDlg, IDC_MAXKEY, config->quality_user.max_key_interval);
    }
		break;

	case IDD_QUANT :
    if (config->quality==quality_table_num) {
		  config->quality_user.min_iquant = config_get_uint(hDlg, IDC_MINIQUANT, config->quality_user.min_iquant);
		  config->quality_user.max_iquant = config_get_uint(hDlg, IDC_MAXIQUANT, config->quality_user.max_iquant);
		  config->quality_user.min_pquant = config_get_uint(hDlg, IDC_MINPQUANT, config->quality_user.min_pquant);
		  config->quality_user.max_pquant = config_get_uint(hDlg, IDC_MAXPQUANT, config->quality_user.max_pquant);
		  config->quality_user.min_bquant = config_get_uint(hDlg, IDC_MINBQUANT, config->quality_user.min_bquant);
		  config->quality_user.max_bquant = config_get_uint(hDlg, IDC_MAXBQUANT, config->quality_user.max_bquant);

		  CONSTRAINVAL(config->quality_user.min_iquant, 1, 31);
		  CONSTRAINVAL(config->quality_user.max_iquant, config->quality_user.min_iquant, 31);
		  CONSTRAINVAL(config->quality_user.min_pquant, 1, 31);
		  CONSTRAINVAL(config->quality_user.max_pquant, config->quality_user.min_pquant, 31);
		  CONSTRAINVAL(config->quality_user.min_bquant, 1, 31);
		  CONSTRAINVAL(config->quality_user.max_bquant, config->quality_user.min_bquant, 31);

		  config->quality_user.trellis_quant = IsDlgChecked(hDlg, IDC_TRELLISQUANT);
    }
		break;

	case IDD_COMMON :
		config->cpu = 0;
		config->cpu |= IsDlgChecked(hDlg, IDC_CPU_MMX)	  ? XVID_CPU_MMX : 0;
		config->cpu |= IsDlgChecked(hDlg, IDC_CPU_MMXEXT)   ? XVID_CPU_MMXEXT : 0;
		config->cpu |= IsDlgChecked(hDlg, IDC_CPU_SSE)	  ? XVID_CPU_SSE : 0;
		config->cpu |= IsDlgChecked(hDlg, IDC_CPU_SSE2)	 ? XVID_CPU_SSE2 : 0;
		config->cpu |= IsDlgChecked(hDlg, IDC_CPU_SSE3)	  ? XVID_CPU_SSE3 : 0;
		config->cpu |= IsDlgChecked(hDlg, IDC_CPU_SSE4)     ? XVID_CPU_SSE41 : 0;
        config->cpu |= IsDlgChecked(hDlg, IDC_CPU_3DNOW)	? XVID_CPU_3DNOW : 0;
		config->cpu |= IsDlgChecked(hDlg, IDC_CPU_3DNOWEXT) ? XVID_CPU_3DNOWEXT : 0;
		config->cpu |= IsDlgChecked(hDlg, IDC_CPU_FORCE)	? XVID_CPU_FORCE : 0;
	    config->debug = get_dlgitem_hex(hDlg, IDC_DEBUG, config->debug);
		config->num_threads = min(16, config_get_uint(hDlg, IDC_NUMTHREADS, config->num_threads));
    break;

  case IDD_ENC :
		if(!(profiles[config->profile].flags & PROFILE_XVID))
		  config->fourcc_used = SendDlgItemMessage(hDlg, IDC_FOURCC, CB_GETCURSEL, 0, 0);
		config->vop_debug = IsDlgChecked(hDlg, IDC_VOPDEBUG);
		config->display_status = IsDlgChecked(hDlg, IDC_DISPLAY_STATUS);
		break;

	case IDD_DEC :
		pp_brightness = SendDlgItemMessage(hDlg, IDC_DEC_BRIGHTNESS, TBM_GETPOS, (WPARAM)NULL, (LPARAM)NULL);
		pp_dy = IsDlgChecked(hDlg, IDC_DEC_DY);
		pp_duv = IsDlgChecked(hDlg, IDC_DEC_DUV);
		pp_dry = IsDlgChecked(hDlg, IDC_DEC_DRY);
		pp_druv = IsDlgChecked(hDlg, IDC_DEC_DRUV);
		pp_fe = IsDlgChecked(hDlg, IDC_DEC_FE);
		break;
	}
}



/* advanced dialog proc */

static INT_PTR CALLBACK adv_proc(HWND hDlg, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	PROPSHEETINFO *psi;

	psi = (PROPSHEETINFO*)GetWindowLongPtr(hDlg, GWLP_USERDATA);

	switch (uMsg)
	{
	case WM_INITDIALOG :
		psi = (PROPSHEETINFO*) ((LPPROPSHEETPAGE)lParam)->lParam;
		SetWindowLongPtr(hDlg, GWLP_USERDATA, (LPARAM)psi);

		if (g_hTooltip)
			EnumChildWindows(hDlg, enum_tooltips, 0);

		adv_init(hDlg, psi->idd, psi->config);
		break;

	case WM_COMMAND :
		if (HIWORD(wParam) == BN_CLICKED)
		{
			switch (LOWORD(wParam))
			{
			case IDC_INTERLACING :
			case IDC_VHQ_BFRAME :
			case IDC_BVOP :
			case IDC_ZONE_MODE_WEIGHT :
			case IDC_ZONE_MODE_QUANT :
			case IDC_ZONE_BVOPTHRESHOLD_ENABLE :
			case IDC_CPU_AUTO :
			case IDC_CPU_FORCE :
			case IDC_AR :
			case IDC_PAR :
			case IDC_BITRATE_AMODE_RATE :
			case IDC_BITRATE_AMODE_SIZE :
				adv_mode(hDlg, psi->idd, psi->config);
				break;

			case IDC_BITRATE_SSELECT :
			case IDC_BITRATE_ASELECT :
				{
				OPENFILENAME ofn;
				char filename[MAX_PATH] = "";

				memset(&ofn, 0, sizeof(OPENFILENAME));
				ofn.lStructSize = sizeof(OPENFILENAME);

				ofn.hwndOwner = hDlg;
				if (LOWORD(wParam)==IDC_BITRATE_SSELECT) {
					ofn.lpstrFilter = "Subtitle files (*.sub, *.ssa, *.txt, *.dat)\0*.sub;*.ssa;*.txt;*.dat\0All files (*.*)\0*.*\0\0";
				}else{
					ofn.lpstrFilter = "Audio files (*.mp3, *.ac3, *.aac, *.ogg, *.wav)\0*.mp3; *.ac3; *.aac; *.ogg; *.wav\0All files (*.*)\0*.*\0\0";
				}
				
				ofn.lpstrFile = filename;
				ofn.nMaxFile = MAX_PATH;
				ofn.Flags = OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST;

				if (GetOpenFileName(&ofn)) {
					HANDLE hFile;
					DWORD filesize;
				
					if ((hFile = CreateFile(filename, GENERIC_READ, FILE_SHARE_READ, 0, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0)) == INVALID_HANDLE_VALUE ||
						(filesize = GetFileSize(hFile, NULL)) == INVALID_FILE_SIZE) {
						MessageBox(hDlg, "Could not get file size", "Error", 0);
					}else{
						SetDlgItemInt(hDlg,
								LOWORD(wParam)==IDC_BITRATE_SSELECT? IDC_BITRATE_SSIZE : IDC_BITRATE_ASIZE, 
								filesize / 1024, FALSE);
						CloseHandle(hFile);
					}
				}
				}
				break;

			case IDC_QUANTMATRIX :
				DialogBoxParam(g_hInst, MAKEINTRESOURCE(IDD_QUANTMATRIX), hDlg, quantmatrix_proc, (LPARAM)psi->config);
				break;

			case IDC_STATS_BROWSE :
			{
				OPENFILENAME ofn;
				char tmp[MAX_PATH];

				GetDlgItemText(hDlg, IDC_STATS, tmp, MAX_PATH);

				memset(&ofn, 0, sizeof(OPENFILENAME));
				ofn.lStructSize = sizeof(OPENFILENAME);

				ofn.hwndOwner = hDlg;
				ofn.lpstrFilter = "bitrate curve (*.pass)\0*.pass\0All files (*.*)\0*.*\0\0";
				ofn.lpstrFile = tmp;
				ofn.nMaxFile = MAX_PATH;
				ofn.Flags = OFN_PATHMUSTEXIST;

				if (psi->idd == IDD_RC_2PASS1) {
					ofn.Flags |= OFN_OVERWRITEPROMPT;
				}else{
					ofn.Flags |= OFN_FILEMUSTEXIST;
				}

				if ((psi->idd==IDD_RC_2PASS1 && GetSaveFileName(&ofn)) || 
					(psi->idd==IDD_RC_2PASS2 && GetOpenFileName(&ofn))) {
					SetDlgItemText(hDlg, IDC_STATS, tmp);
				}
				}
				break;

			case IDC_ZONE_FETCH :
				SetDlgItemInt(hDlg, IDC_ZONE_FRAME, psi->config->ci.ciActiveFrame, FALSE);
				break;

			case IDC_AR_DEFAULT:
				CheckRadioButton(hDlg, IDC_AR, IDC_PAR, IDC_PAR);
				SendDlgItemMessage(hDlg, IDC_ASPECT_RATIO, CB_SETCURSEL, 0, 0);
				adv_mode(hDlg, psi->idd, psi->config);
				break;
			case IDC_AR_4_3:
				SetDlgItemInt(hDlg, IDC_ARX, 4, FALSE);
				SetDlgItemInt(hDlg, IDC_ARY, 3, FALSE);
				CheckRadioButton(hDlg, IDC_AR, IDC_PAR, IDC_AR);
				adv_mode(hDlg, psi->idd, psi->config);
				break;
			case IDC_AR_16_9:
				SetDlgItemInt(hDlg, IDC_ARX, 16, FALSE);
				SetDlgItemInt(hDlg, IDC_ARY, 9, FALSE);
				CheckRadioButton(hDlg, IDC_AR, IDC_PAR, IDC_AR);
				adv_mode(hDlg, psi->idd, psi->config);
				break;
			case IDC_AR_235_100:
				SetDlgItemInt(hDlg, IDC_ARX, 235, FALSE);
				SetDlgItemInt(hDlg, IDC_ARY, 100, FALSE);
				CheckRadioButton(hDlg, IDC_AR, IDC_PAR, IDC_AR);
				adv_mode(hDlg, psi->idd, psi->config);
				break;
			case IDC_DEC_DY:
			case IDC_DEC_DUV:
				EnableDlgWindow(hDlg, IDC_DEC_DRY, IsDlgChecked(hDlg, IDC_DEC_DY));
				EnableDlgWindow(hDlg, IDC_DEC_DRUV, IsDlgChecked(hDlg, IDC_DEC_DUV));
				break;
			default :
				return TRUE;
			}
		}else if ((HIWORD(wParam) == CBN_EDITCHANGE || HIWORD(wParam)==CBN_SELCHANGE) &&
			(LOWORD(wParam)==IDC_BITRATE_TSIZE ||
			 LOWORD(wParam)==IDC_BITRATE_ARATE )) {

			adv_mode(hDlg, psi->idd, psi->config);

		}else if (HIWORD(wParam) == LBN_SELCHANGE &&
			(LOWORD(wParam) == IDC_PROFILE_PROFILE ||
			 LOWORD(wParam) == IDC_LEVEL_PROFILE ||
			 LOWORD(wParam) == IDC_QUANTTYPE ||
			 LOWORD(wParam) == IDC_ASPECT_RATIO ||
 			 LOWORD(wParam) == IDC_MOTION ||
			 LOWORD(wParam) == IDC_VHQ ||
			 LOWORD(wParam) == IDC_BITRATE_CFORMAT ||
			 LOWORD(wParam) == IDC_BITRATE_AFORMAT ||
			 LOWORD(wParam) == IDC_BITRATE_FPS)) {
			adv_mode(hDlg, psi->idd, psi->config);
		}else if (HIWORD(wParam) == EN_UPDATE && (LOWORD(wParam)==IDC_ZONE_WEIGHT || LOWORD(wParam)==IDC_ZONE_QUANT)) {

			SendDlgItemMessage(hDlg, IDC_ZONE_SLIDER, TBM_SETPOS, TRUE,
					get_dlgitem_float(hDlg, LOWORD(wParam), 100));

		} else if (HIWORD(wParam) == EN_UPDATE && (LOWORD(wParam)==IDC_PARX || LOWORD(wParam)==IDC_PARY)) {

			if (5 == SendDlgItemMessage(hDlg, IDC_ASPECT_RATIO, CB_GETCURSEL, 0, 0)) {
				if(LOWORD(wParam)==IDC_PARX)
					psi->config->par_x = config_get_uint(hDlg, LOWORD(wParam), psi->config->par_x);
				else
					psi->config->par_y = config_get_uint(hDlg, LOWORD(wParam), psi->config->par_y);
			}
		} else if (HIWORD(wParam) == EN_UPDATE &&
			(LOWORD(wParam)==IDC_BITRATE_SSIZE ||
			 LOWORD(wParam)==IDC_BITRATE_HOURS ||
			 LOWORD(wParam)==IDC_BITRATE_MINUTES ||
			 LOWORD(wParam)==IDC_BITRATE_SECONDS ||
			 LOWORD(wParam)==IDC_BITRATE_ASIZE)) {
			adv_mode(hDlg, psi->idd, psi->config);
		} else
			return 0;
		break;

	case WM_HSCROLL :
		if((HWND)lParam == GetDlgItem(hDlg, IDC_ZONE_SLIDER)) {
			int idc = IsDlgChecked(hDlg, IDC_ZONE_MODE_WEIGHT) ? IDC_ZONE_WEIGHT : IDC_ZONE_QUANT;
			set_dlgitem_float(hDlg, idc, SendMessage((HWND)lParam, TBM_GETPOS, 0, 0) );
			break;
		}
		return 0;

 
	case WM_NOTIFY :
		switch (((NMHDR *)lParam)->code)
		{
		case PSN_SETACTIVE :
			DPRINTF("PSN_SET");
			adv_upload(hDlg, psi->idd, psi->config);
			adv_mode(hDlg, psi->idd, psi->config);
			SetWindowLongPtr(hDlg, DWLP_MSGRESULT, FALSE);
			break;

		case PSN_KILLACTIVE :
			DPRINTF("PSN_KILL");
			adv_download(hDlg, psi->idd, psi->config);
			SetWindowLongPtr(hDlg, DWLP_MSGRESULT, FALSE);
			break;

		case PSN_APPLY :
			DPRINTF("PSN_APPLY");
			psi->config->save = TRUE;
			SetWindowLongPtr(hDlg, DWLP_MSGRESULT, FALSE);
			break;
		}
		break;

	default :
		return 0;
	}

	return 1;
}




/* load advanced options property sheet
  returns true, if the user accepted the changes
  or fasle if changes were canceled.

  */

#ifndef PSH_NOCONTEXTHELP
#define PSH_NOCONTEXTHELP 0x02000000
#endif

static BOOL adv_dialog(HWND hParent, CONFIG * config, const int * dlgs, int size)
{
	PROPSHEETINFO psi[6];
	PROPSHEETPAGE psp[6];
	PROPSHEETHEADER psh;
	CONFIG temp;
	int i;

	config->save = FALSE;
	memcpy(&temp, config, sizeof(CONFIG));

	for (i=0; i<size; i++)
	{
		psp[i].dwSize = sizeof(PROPSHEETPAGE);
		psp[i].dwFlags = 0;
		psp[i].hInstance = g_hInst;
		psp[i].pfnDlgProc = adv_proc;
		psp[i].lParam = (LPARAM)&psi[i];
		psp[i].pfnCallback = NULL;
		psp[i].pszTemplate = MAKEINTRESOURCE(dlgs[i]);

		psi[i].idd = dlgs[i];
		psi[i].config = &temp;
	}

	psh.dwSize = sizeof(PROPSHEETHEADER);
	psh.dwFlags = PSH_PROPSHEETPAGE | PSH_NOAPPLYNOW | PSH_NOCONTEXTHELP;
	psh.hwndParent = hParent;
	psh.hInstance = g_hInst;
	psh.pszCaption = (LPSTR) "Xvid Configuration";
	psh.nPages = size;
	psh.nStartPage = 0;
	psh.ppsp = (LPCPROPSHEETPAGE)&psp;
	psh.pfnCallback = NULL;
	PropertySheet(&psh);

	if (temp.save)
		memcpy(config, &temp, sizeof(CONFIG));

	return temp.save;
}

/* ===================================================================================== */
/* MAIN DIALOG ========================================================================= */
/* ===================================================================================== */


static void main_insert_zone(HWND hDlg, zone_t * s, int i, BOOL insert)
{
	char tmp[32];

	wsprintf(tmp,"%i",s->frame);

	if (insert) {
		LVITEM lvi;

		lvi.mask = LVIF_TEXT | LVIF_IMAGE | LVIF_PARAM | LVIF_STATE;
		lvi.state = 0;
		lvi.stateMask = 0;
		lvi.iImage = 0;
		lvi.pszText = tmp;
		lvi.cchTextMax = strlen(tmp);
		lvi.iItem = i;
		lvi.iSubItem = 0;
		ListView_InsertItem(hDlg, &lvi);
	}else{
		ListView_SetItemText(hDlg, i, 0, tmp);
	}

	if (s->mode == RC_ZONE_WEIGHT) {
		sprintf(tmp,"W %.2f",(float)s->weight/100);
	}else if (s->mode == RC_ZONE_QUANT) {
		sprintf(tmp,"Q %.2f",(float)s->quant/100);
	}else {
		strcpy(tmp,"EXT");
	}
	ListView_SetItemText(hDlg, i, 1, tmp);

	tmp[0] = '\0';
	if (s->type==XVID_TYPE_IVOP)
		strcat(tmp, "K ");

	if (s->greyscale)
		strcat(tmp, "G ");

	if (s->chroma_opt)
		strcat(tmp, "O ");

	if (s->cartoon_mode)
		strcat(tmp, "C ");

	ListView_SetItemText(hDlg, i, 2, tmp);
}


static void main_mode(HWND hDlg, CONFIG * config)
{
	const int profile = SendDlgItemMessage(hDlg, IDC_PROFILE, CB_GETCURSEL, 0, 0);
	const int rc_mode = SendDlgItemMessage(hDlg, IDC_MODE, CB_GETCURSEL, 0, 0);
	/* enable target rate/size control only for 1pass and 2pass  modes*/
	const int target_en = rc_mode==RC_MODE_1PASS || rc_mode==RC_MODE_2PASS2;
	const int target_en_slider = rc_mode==RC_MODE_1PASS || 
		(rc_mode==RC_MODE_2PASS2 && config->use_2pass_bitrate);
 
	char buf[16];
	int max;

	g_use_bitrate = config->use_2pass_bitrate;

	if (g_use_bitrate) {
		SetDlgItemText(hDlg, IDC_BITRATE_S, "Target bitrate (kbps):");

		wsprintf(buf, "%i kbps", DEFAULT_MIN_KBPS);
		SetDlgItemText(hDlg, IDC_BITRATE_MIN, buf);

		max = profiles[profile].max_bitrate / 1000;
		if (max == 0) max = DEFAULT_MAX_KBPS;
		wsprintf(buf, "%i kbps", max);
		SetDlgItemText(hDlg, IDC_BITRATE_MAX, buf);

  		SendDlgItemMessage(hDlg, IDC_SLIDER, TBM_SETRANGE, TRUE, MAKELONG(DEFAULT_MIN_KBPS, max));
		SendDlgItemMessage(hDlg, IDC_SLIDER, TBM_SETPOS, TRUE,
						config_get_uint(hDlg, IDC_BITRATE, DEFAULT_MIN_KBPS) );

	} else if (rc_mode==RC_MODE_2PASS2) {
		SetDlgItemText(hDlg, IDC_BITRATE_S, "Target size (kbytes):");
	} else if (rc_mode==RC_MODE_1PASS) {
		SetDlgItemText(hDlg, IDC_BITRATE_S, "Target quantizer:");
  		SendDlgItemMessage(hDlg, IDC_SLIDER, TBM_SETRANGE, TRUE, MAKELONG(100, 3100));
		SendDlgItemMessage(hDlg, IDC_SLIDER, TBM_SETPOS, TRUE,
							get_dlgitem_float(hDlg, IDC_BITRATE, DEFAULT_QUANT ));
		SetDlgItemText(hDlg, IDC_BITRATE_MIN, "1 (maximum quality)");
		SetDlgItemText(hDlg, IDC_BITRATE_MAX, "(smallest file) 31");

	}

	EnableDlgWindow(hDlg, IDC_BITRATE_S, target_en);
	EnableDlgWindow(hDlg, IDC_BITRATE, target_en);
	EnableDlgWindow(hDlg, IDC_BITRATE_ADV, target_en);

	EnableDlgWindow(hDlg, IDC_BITRATE_MIN, target_en_slider);
	EnableDlgWindow(hDlg, IDC_BITRATE_MAX, target_en_slider);
	EnableDlgWindow(hDlg, IDC_SLIDER, target_en_slider);
}


static void main_upload(HWND hDlg, CONFIG * config)
{

	SendDlgItemMessage(hDlg, IDC_PROFILE, CB_SETCURSEL, config->profile, 0);
	SendDlgItemMessage(hDlg, IDC_MODE, CB_SETCURSEL, config->mode, 0);
  SendDlgItemMessage(hDlg, IDC_QUALITY, CB_SETCURSEL, config->quality, 0);

	g_use_bitrate = config->use_2pass_bitrate;

	if (g_use_bitrate) {
		SetDlgItemInt(hDlg, IDC_BITRATE, config->bitrate, FALSE);
	} else if (config->mode == RC_MODE_2PASS2) {
		SetDlgItemInt(hDlg, IDC_BITRATE, config->desired_size, FALSE);
	} else if (config->mode == RC_MODE_1PASS) {
		set_dlgitem_float(hDlg, IDC_BITRATE, config->desired_quant);
	}

	zones_update(hDlg, config);
}


/* downloads data from main dialog */
static void main_download(HWND hDlg, CONFIG * config)
{
	config->profile = SendDlgItemMessage(hDlg, IDC_PROFILE, CB_GETCURSEL, 0, 0);
	config->mode = SendDlgItemMessage(hDlg, IDC_MODE, CB_GETCURSEL, 0, 0);
  config->quality = SendDlgItemMessage(hDlg, IDC_QUALITY, CB_GETCURSEL, 0, 0);

	if (g_use_bitrate) {
		config->bitrate = config_get_uint(hDlg, IDC_BITRATE, config->bitrate);
	} else if (config->mode == RC_MODE_2PASS2) {
		config->desired_size = config_get_uint(hDlg, IDC_BITRATE, config->desired_size);
	} else if (config->mode == RC_MODE_1PASS) {
		config->desired_quant = get_dlgitem_float(hDlg, IDC_BITRATE, config->desired_quant);
	}
}


/* main dialog proc */

static const int profile_dlgs[] = { IDD_PROFILE, IDD_LEVEL, IDD_AR };
static const int single_dlgs[] = { IDD_RC_CBR };
static const int pass1_dlgs[] = { IDD_RC_2PASS1 };
static const int pass2_dlgs[] = { IDD_RC_2PASS2 };
static const int bitrate_dlgs[] = { IDD_BITRATE };
static const int zone_dlgs[] = { IDD_ZONE };
static const int quality_dlgs[] = { IDD_MOTION, IDD_QUANT };
static const int other_dlgs[] = { IDD_ENC, IDD_DEC, IDD_COMMON };


INT_PTR CALLBACK main_proc(HWND hDlg, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	CONFIG* config = (CONFIG*)GetWindowLongPtr(hDlg, GWLP_USERDATA);
	unsigned int i;

	switch (uMsg)
	{
	case WM_INITDIALOG :
		SetWindowLongPtr(hDlg, GWLP_USERDATA, lParam);
		config = (CONFIG*)lParam;

		for (i=0; i<sizeof(profiles)/sizeof(profile_t); i++)
			SendDlgItemMessage(hDlg, IDC_PROFILE, CB_ADDSTRING, 0, (LPARAM)profiles[i].short_name);

		SendDlgItemMessage(hDlg, IDC_MODE, CB_ADDSTRING, 0, (LPARAM)"Single pass");
		SendDlgItemMessage(hDlg, IDC_MODE, CB_ADDSTRING, 0, (LPARAM)"Twopass - 1st pass");
		SendDlgItemMessage(hDlg, IDC_MODE, CB_ADDSTRING, 0, (LPARAM)"Twopass - 2nd pass");
#ifdef _DEBUG
		SendDlgItemMessage(hDlg, IDC_MODE, CB_ADDSTRING, 0, (LPARAM)"Null test speed");
#endif

		for (i=0; i<(unsigned int)quality_table_num; i++)
			SendDlgItemMessage(hDlg, IDC_QUALITY, CB_ADDSTRING, 0, (LPARAM)quality_table[i].name);
    SendDlgItemMessage(hDlg, IDC_QUALITY, CB_ADDSTRING, 0, (LPARAM)QUALITY_USER_STRING);

		InitCommonControls();

		if ((g_hTooltip = CreateWindow(TOOLTIPS_CLASS, NULL, TTS_ALWAYSTIP,
				CW_USEDEFAULT, CW_USEDEFAULT, CW_USEDEFAULT, CW_USEDEFAULT,
				NULL, NULL, g_hInst, NULL)))
		{
			SetWindowPos(g_hTooltip, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE|SWP_NOSIZE|SWP_NOACTIVATE);
			SendMessage(g_hTooltip, TTM_SETDELAYTIME, TTDT_AUTOMATIC, MAKELONG(1500, 0));
#if (_WIN32_IE >= 0x0300)
			SendMessage(g_hTooltip, TTM_SETMAXTIPWIDTH, 0, 400);
#endif

			EnumChildWindows(hDlg, enum_tooltips, 0);
		}

		SetClassLongPtr(GetDlgItem(hDlg, IDC_BITRATE_S), GCLP_HCURSOR, (LONG_PTR)LoadCursor(NULL, IDC_HAND));

		{
			DWORD ext_style = ListView_GetExtendedListViewStyle(GetDlgItem(hDlg,IDC_ZONES));
#if (_WIN32_IE >= 0x0300)
			ext_style |= LVS_EX_FULLROWSELECT;
#endif
#if( _WIN32_IE >= 0x0400 )
			ext_style |= LVS_EX_FLATSB ;
#endif
			ListView_SetExtendedListViewStyle(GetDlgItem(hDlg,IDC_ZONES), ext_style);
		}

		{
			typedef struct {
				char * name;
				int value;
			} char_int_t;

			const static char_int_t columns[] = {
				{"Frame #",	 64},
				{"Weight/Quant",  82},
				{"Modifiers",   120}};

			LVCOLUMN lvc;
			int i;

			/* Initialize the LVCOLUMN structure.  */
			lvc.mask = LVCF_FMT | LVCF_WIDTH | LVCF_TEXT | LVCF_SUBITEM;
			lvc.fmt = LVCFMT_LEFT;

			/* Add the columns.  */
			for (i=0; i<sizeof(columns)/sizeof(char_int_t); i++) {
				lvc.pszText = (char*)columns[i].name;
				lvc.cchTextMax = strlen(columns[i].name);
				lvc.iSubItem = i;
				lvc.cx = columns[i].value;  /* column width, pixels */
				ListView_InsertColumn(GetDlgItem(hDlg,IDC_ZONES), i, &lvc);
			}
		}

		/* XXX: main_mode needs RC_MODE_xxx, main_upload needs g_use_bitrate set correctly... */
		main_upload(hDlg, config);
		main_mode(hDlg, config);
		main_upload(hDlg, config);
		break;

	case WM_NOTIFY :
		{
			NMHDR * n = (NMHDR*)lParam;

			if (n->code == NM_DBLCLK) {
				 NMLISTVIEW * nmlv = (NMLISTVIEW*) lParam;
				 config->cur_zone = nmlv->iItem;

				 main_download(hDlg, config);
				 if (config->cur_zone >= 0 && adv_dialog(hDlg, config, zone_dlgs, sizeof(zone_dlgs)/sizeof(int))) {
					 zones_update(hDlg, config);
				 }
				 break;
			}

		break;
		}

	case WM_COMMAND :
		if (HIWORD(wParam) == BN_CLICKED) {

			switch(LOWORD(wParam)) {
			case IDC_PROFILE_ADV :
				main_download(hDlg, config);
				adv_dialog(hDlg, config, profile_dlgs, sizeof(profile_dlgs)/sizeof(int));

				SendDlgItemMessage(hDlg, IDC_PROFILE, CB_SETCURSEL, config->profile, 0);
				main_mode(hDlg, config);
				break;

			case IDC_MODE_ADV :
				main_download(hDlg, config);
				if (config->mode == RC_MODE_1PASS) {
					adv_dialog(hDlg, config, single_dlgs, sizeof(single_dlgs)/sizeof(int));
				}else if (config->mode == RC_MODE_2PASS1) {
					adv_dialog(hDlg, config, pass1_dlgs, sizeof(pass1_dlgs)/sizeof(int));
				}else if (config->mode == RC_MODE_2PASS2) {
					adv_dialog(hDlg, config, pass2_dlgs, sizeof(pass2_dlgs)/sizeof(int));
				}
				break;

			case IDC_BITRATE_S :
				/* alternate between bitrate/desired_length metrics */
				main_download(hDlg, config);
				config->use_2pass_bitrate = !config->use_2pass_bitrate;
				main_mode(hDlg, config);
				main_upload(hDlg, config);
				break;

			case IDC_BITRATE_ADV :
				main_download(hDlg, config);
				adv_dialog(hDlg, config, bitrate_dlgs, sizeof(bitrate_dlgs)/sizeof(int));
				main_mode(hDlg, config);
				main_upload(hDlg, config);
				break;

			case IDC_OTHER :
				main_download(hDlg, config);
				adv_dialog(hDlg, config, other_dlgs, sizeof(other_dlgs)/sizeof(int));
				main_mode(hDlg, config);
				break;

			case IDC_ADD :
			{
				int i, sel, new_frame;

				if (config->num_zones >= MAX_ZONES) {
					MessageBox(hDlg, "Exceeded maximum number of zones.\nIncrease config.h:MAX_ZONES and rebuild.", "Warning", 0);
					break;
				}

				sel = ListView_GetNextItem(GetDlgItem(hDlg, IDC_ZONES), -1, LVNI_SELECTED);

				if (sel<0) {
					if (config->ci_valid && config->ci.ciActiveFrame>0) {
						for(sel=0; sel<config->num_zones-1 && config->zones[sel].frame<config->ci.ciActiveFrame; sel++) ;
						sel--;
						new_frame = config->ci.ciActiveFrame;
					}else{
						sel = config->num_zones-1;
						new_frame = sel<0 ? 0 : config->zones[sel].frame + 1;
					}
				}else{
					new_frame = config->zones[sel].frame + 1;
				}

				for(i=config->num_zones-1; i>sel; i--) {
					config->zones[i+1] = config->zones[i];
				}
				config->num_zones++;
				config->zones[sel+1].frame = new_frame;
				config->zones[sel+1].mode = RC_ZONE_WEIGHT;
				config->zones[sel+1].weight = 100;
				config->zones[sel+1].quant = 500;
				config->zones[sel+1].type = XVID_TYPE_AUTO;
				config->zones[sel+1].greyscale = 0;
				config->zones[sel+1].chroma_opt = 0;
				config->zones[sel+1].bvop_threshold = 0;

				ListView_SetItemState(GetDlgItem(hDlg, IDC_ZONES), sel, 0x00000000, LVIS_SELECTED);
				zones_update(hDlg, config);
				ListView_SetItemState(GetDlgItem(hDlg, IDC_ZONES), sel+1, 0xffffffff, LVIS_SELECTED);
				break;
			}

			case IDC_REMOVE :
			{
				int i, sel;
				sel = ListView_GetNextItem(GetDlgItem(hDlg, IDC_ZONES), -1, LVNI_SELECTED);

				if (sel == -1 || config->num_zones < 1) {
					/*MessageBox(hDlg, "Nothing selected", "Warning", 0);*/
					break;
				}

				for (i=sel; i<config->num_zones-1; i++)
					config->zones[i] = config->zones[i+1];

				config->num_zones--;

				zones_update(hDlg, config);
				break;
			}

			case IDC_EDIT :
				main_download(hDlg, config);
				config->cur_zone = ListView_GetNextItem(GetDlgItem(hDlg, IDC_ZONES), -1, LVNI_SELECTED);
				if (config->cur_zone != -1 && adv_dialog(hDlg, config, zone_dlgs, sizeof(zone_dlgs)/sizeof(int))) {
					zones_update(hDlg, config);
				}
				break;

			case IDC_QUALITY_ADV :
				main_download(hDlg, config);

        if (config->quality < quality_table_num) {
          int result = MessageBox(hDlg, 
            "The built-in quality presets are read-only. Would you like to copy the values\n"
            "of the selected preset into the \"" QUALITY_USER_STRING "\" preset for editing?", 
            "Question", MB_YESNOCANCEL|MB_DEFBUTTON2|MB_ICONQUESTION);

          if (result==0 || result==IDCANCEL) break;
          if (result==IDYES) {
            memcpy(&config->quality_user, &quality_table[config->quality], sizeof(quality_t));
            config->quality = quality_table_num;
          }
        }
				adv_dialog(hDlg, config, quality_dlgs, sizeof(quality_dlgs)/sizeof(int));
        SendDlgItemMessage(hDlg, IDC_QUALITY, CB_SETCURSEL, config->quality, 0);
				break;

			case IDC_DEFAULTS :
				config_reg_default(config);
				SendDlgItemMessage(hDlg, IDC_PROFILE, CB_SETCURSEL, config->profile, 0);
				SendDlgItemMessage(hDlg, IDC_MODE, CB_SETCURSEL, config->mode, 0);
				main_mode(hDlg, config);
				main_upload(hDlg, config);
				break;

			case IDOK :
				main_download(hDlg, config);
				config->save = TRUE;
				EndDialog(hDlg, IDOK);
				break;

			case IDCANCEL :
				config->save = FALSE;
				EndDialog(hDlg, IDCANCEL);
				break;
			}
		} else if (HIWORD(wParam) == LBN_SELCHANGE &&
			(LOWORD(wParam)==IDC_PROFILE || LOWORD(wParam)==IDC_MODE)) {

			config->mode = SendDlgItemMessage(hDlg, IDC_MODE, CB_GETCURSEL, 0, 0);
			config->profile = SendDlgItemMessage(hDlg, IDC_PROFILE, CB_GETCURSEL, 0, 0);

			if (!g_use_bitrate) {
				if (config->mode == RC_MODE_1PASS)
					set_dlgitem_float(hDlg, IDC_BITRATE, config->desired_quant);
				else if (config->mode == RC_MODE_2PASS2)
					SetDlgItemInt(hDlg, IDC_BITRATE, config->desired_size, FALSE);
			}

			main_mode(hDlg, config);
			main_upload(hDlg, config);

		}else if (HIWORD(wParam)==EN_UPDATE && LOWORD(wParam)==IDC_BITRATE) {

			if (g_use_bitrate) {
				SendDlgItemMessage(hDlg, IDC_SLIDER, TBM_SETPOS, TRUE,
						config_get_uint(hDlg, IDC_BITRATE, DEFAULT_MIN_KBPS) );
			} else if (config->mode == RC_MODE_1PASS) {
				SendDlgItemMessage(hDlg, IDC_SLIDER, TBM_SETPOS, TRUE,
						get_dlgitem_float(hDlg, IDC_BITRATE, DEFAULT_QUANT) );
			}
			main_download(hDlg, config);

		}else {
			return 0;
		}
		break;

	case WM_HSCROLL :
		if((HWND)lParam == GetDlgItem(hDlg, IDC_SLIDER)) {
			if (g_use_bitrate)
				SetDlgItemInt(hDlg, IDC_BITRATE, SendMessage((HWND)lParam, TBM_GETPOS, 0, 0), FALSE);
			else
				set_dlgitem_float(hDlg, IDC_BITRATE, SendMessage((HWND)lParam, TBM_GETPOS, 0, 0));

			main_download(hDlg, config);
			break;
		}
		return 0;

	default :
		return 0;
	}

	return 1;
}


/* ===================================================================================== */
/* LICENSE DIALOG ====================================================================== */
/* ===================================================================================== */

static INT_PTR CALLBACK license_proc(HWND hDlg, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	switch (uMsg)
	{
	case WM_INITDIALOG :
		{
			HRSRC hRSRC;
			HGLOBAL hGlobal = NULL;
			if ((hRSRC = FindResource(g_hInst, MAKEINTRESOURCE(IDR_GPL), "TEXT"))) {
				if ((hGlobal = LoadResource(g_hInst, hRSRC))) {
					LPVOID lpData;
					if ((lpData = LockResource(hGlobal))) {
						SendDlgItemMessage(hDlg, IDC_LICENSE_TEXT, WM_SETFONT, (WPARAM)GetStockObject(ANSI_FIXED_FONT), MAKELPARAM(TRUE, 0));
						SetDlgItemText(hDlg, IDC_LICENSE_TEXT, lpData);
						SendDlgItemMessage(hDlg, IDC_LICENSE_TEXT, EM_SETSEL, (WPARAM)-1, (LPARAM)0);
					}
				}
			}
			SetWindowLongPtr(hDlg, GWLP_USERDATA, (LONG_PTR)hGlobal);
		}
		break;

	case WM_DESTROY :
		{
			HGLOBAL hGlobal = (HGLOBAL)GetWindowLongPtr(hDlg, GWLP_USERDATA);
			if (hGlobal) {
				FreeResource(hGlobal);
			}
		}
		break;

	case WM_COMMAND :
		if (HIWORD(wParam) == BN_CLICKED) {
			switch(LOWORD(wParam)) {
			case IDOK :
			case IDCANCEL :
				EndDialog(hDlg, 0);
				break;
			default :
				return 0;
			}
			break;
		}
		break;

	default :
		return 0;
	}

	return 1;
}

/* ===================================================================================== */
/* ABOUT DIALOG ======================================================================== */
/* ===================================================================================== */

INT_PTR CALLBACK about_proc(HWND hDlg, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	switch (uMsg)
	{
	case WM_INITDIALOG :
		{
			xvid_gbl_info_t info;
			char core[100];
			HFONT hFont;
			LOGFONT lfData;
			HINSTANCE m_hdll;

			SetDlgItemText(hDlg, IDC_BUILD, XVID_BUILD);
#ifdef _WIN64
			wsprintf(core, "(%s, 64-bit Edition)", XVID_SPECIAL_BUILD);
#else
			wsprintf(core, "(%s)", XVID_SPECIAL_BUILD);
#endif
			SetDlgItemText(hDlg, IDC_SPECIAL_BUILD, core);

			memset(&info, 0, sizeof(info));
			info.version = XVID_VERSION;

			m_hdll = LoadLibrary(XVID_DLL_NAME);
			if (m_hdll != NULL) {

				((int (__cdecl *)(void *, int, void *, void *))GetProcAddress(m_hdll, "xvid_global"))
					(0, XVID_GBL_INFO, &info, NULL);

				wsprintf(core, "xvidcore.dll version %d.%d.%d (\"%s\")",
					XVID_VERSION_MAJOR(info.actual_version),
					XVID_VERSION_MINOR(info.actual_version),
					XVID_VERSION_PATCH(info.actual_version),
					info.build);

				FreeLibrary(m_hdll);
			} else {
				wsprintf(core, "xvidcore.dll not found!");
			}

			SetDlgItemText(hDlg, IDC_CORE, core);

			hFont = (HFONT)SendDlgItemMessage(hDlg, IDC_WEBSITE, WM_GETFONT, 0, 0L);

			if (GetObject(hFont, sizeof(LOGFONT), &lfData)) {
				lfData.lfUnderline = 1;

				hFont = CreateFontIndirect(&lfData);
				if (hFont) {
					SendDlgItemMessage(hDlg, IDC_WEBSITE, WM_SETFONT, (WPARAM)hFont, 1L);
				}
			}

			SetClassLongPtr(GetDlgItem(hDlg, IDC_WEBSITE), GCLP_HCURSOR, (LONG_PTR)LoadCursor(NULL, IDC_HAND));
			SetDlgItemText(hDlg, IDC_WEBSITE, XVID_WEBSITE);
		}
		break;
	case WM_CTLCOLORSTATIC :
		if ((HWND)lParam == GetDlgItem(hDlg, IDC_WEBSITE))
		{
			SetBkMode((HDC)wParam, TRANSPARENT) ;
			SetTextColor((HDC)wParam, RGB(0x00,0x00,0xc0));
			return (INT_PTR)GetStockObject(NULL_BRUSH);
		}
		return 0;

	case WM_COMMAND :
		if (LOWORD(wParam) == IDC_WEBSITE && HIWORD(wParam) == STN_CLICKED)	{
			ShellExecute(hDlg, "open", XVID_WEBSITE, NULL, NULL, SW_SHOWNORMAL);
		}else if (LOWORD(wParam) == IDC_LICENSE) {
			DialogBoxParam(g_hInst, MAKEINTRESOURCE(IDD_LICENSE), hDlg, license_proc, (LPARAM)0);
		} else if (LOWORD(wParam) == IDOK || LOWORD(wParam) == IDCANCEL) {
			EndDialog(hDlg, LOWORD(wParam));
		}
		break;

	default :
		return 0;
	}

	return 1;
}


void
sort_zones(zone_t * zones, int zone_num, int * sel)
{
	int i, j;
	zone_t tmp;
	for (i = 0; i < zone_num; i++) {
		int cur = i;
		int min_f = zones[i].frame;
		for (j = i + 1; j < zone_num; j++) {
			if (zones[j].frame < min_f) {
				min_f = zones[j].frame;
				cur = j;
			}
		}
		if (cur != i) {
			tmp = zones[i];
			zones[i] = zones[cur];
			zones[cur] = tmp;
			if (i == *sel) *sel = cur;
			else if (cur == *sel) *sel = i;
		}
	}
}


static void
zones_update(HWND hDlg, CONFIG * config)
{
	int i, sel;

	sel = ListView_GetNextItem(GetDlgItem(hDlg, IDC_ZONES), -1, LVNI_SELECTED);

	sort_zones(config->zones, config->num_zones, &sel);

	ListView_DeleteAllItems(GetDlgItem(hDlg,IDC_ZONES));

	for (i = 0; i < config->num_zones; i++)
		main_insert_zone(GetDlgItem(hDlg,IDC_ZONES), &config->zones[i], i, TRUE);

	if (sel == -1 && config->num_zones > 0) sel = 0;
	if (sel >= config->num_zones) sel = config->num_zones-1;

	config->cur_zone = sel;
	ListView_SetItemState(GetDlgItem(hDlg, IDC_ZONES), sel, 0xffffffff, LVIS_SELECTED);
}
