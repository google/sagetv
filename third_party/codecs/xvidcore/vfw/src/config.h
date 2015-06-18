/*****************************************************************************
 *
 *  XVID MPEG-4 VIDEO CODEC
 *  - VFW configuration header  -
 *
 *  Copyright(C) Peter Ross <pross@xvid.org>
 *
 *  This program is free software ; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation ; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY ; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program ; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA
 *
 * $Id: config.h 1985 2011-05-18 09:02:35Z Isibaar $
 *
 ****************************************************************************/

#ifndef _CONFIG_H_
#define _CONFIG_H_

#include <windows.h>
#include "vfwext.h"
#include <xvid.h>

extern HINSTANCE g_hInst;


/* small hack */
#ifndef IDC_HAND
#define IDC_HAND	MAKEINTRESOURCE(32649)
#endif

/* one kilobit */
#define CONFIG_KBPS 1000

/* min/max bitrate when not specified by profile */
#define DEFAULT_MIN_KBPS	16
#define DEFAULT_MAX_KBPS	20480
#define DEFAULT_QUANT		400

/* registry stuff */
#define XVID_REG_KEY	HKEY_CURRENT_USER
#define XVID_REG_PARENT	"Software\\GNU"
#define XVID_REG_CHILD	"XviD"
#define XVID_REG_CLASS	"config"

#define XVID_BUILD		__TIME__ ", " __DATE__
#define XVID_WEBSITE	"http://www.xvid.org/"
#define XVID_SPECIAL_BUILD	"Vanilla CVS Build"

/* constants */
#define CONFIG_2PASS_FILE ".\\video.pass"

/* codec modes */
#define RC_MODE_1PASS			0
#define RC_MODE_2PASS1			1
#define RC_MODE_2PASS2			2
#define RC_MODE_NULL			3

#define RC_ZONE_WEIGHT			0
#define RC_ZONE_QUANT			1

/* vhq modes */
#define VHQ_OFF					0
#define VHQ_MODE_DECISION		1
#define VHQ_LIMITED_SEARCH		2
#define VHQ_MEDIUM_SEARCH		3
#define VHQ_WIDE_SEARCH			4

/* quantizer modes */
#define QUANT_MODE_H263			0
#define QUANT_MODE_MPEG			1
#define QUANT_MODE_CUSTOM		2


#define MAX_ZONES	64
typedef struct
{
	int frame;
	
	int type;
	int mode;
	int weight;
	int quant;

	unsigned int greyscale;
	unsigned int chroma_opt;
	unsigned int bvop_threshold;
	unsigned int cartoon_mode;
} zone_t;


/* this structure represents a quality preset. it encapsulates
   options from the motion and quantizer config pages. */
#define QUALITY_GENERAL_STRING  "General purpose"
#define QUALITY_USER_STRING   	"(User defined)"
typedef struct {
	char * name;
	/* motion */
	int motion_search;
	int vhq_mode;
	int vhq_metric;
	int vhq_bframe;
	int chromame;
	int turbo;
	int max_key_interval;
	int frame_drop_ratio;

	/* quant */
	int min_iquant;
	int max_iquant;
	int min_pquant;
	int max_pquant;
	int min_bquant;
	int max_bquant;
	int trellis_quant;
} quality_t;


typedef struct
{
/********** ATTENTION **********/
	int mode;					/* Vidomi directly accesses these vars */
	int bitrate;				
	int desired_size;			/* please try to avoid modifications here */
	char stats[MAX_PATH];		
/*******************************/
	int use_2pass_bitrate;		/* use bitrate for 2pass2 (instead of desired size) */
	int desired_quant;			/* for one-pass constant quant */

	/* profile  */
	char profile_name[MAX_PATH];
	int profile;			/* used internally; *not* written to registry */

  /* quality preset */
	char quality_name[MAX_PATH];
	int quality;			/* used internally; *not* written to registry */

	int quant_type;
	BYTE qmatrix_intra[64];
	BYTE qmatrix_inter[64];
	int lum_masking;
	int interlacing;
	int tff;
	int qpel;
	int gmc;
	int use_bvop;
	int max_bframes;
	int bquant_ratio;
	int bquant_offset;
	int packed;
	int display_aspect_ratio;				/* aspect ratio */
	int ar_x, ar_y;							/* picture aspect ratio */
	int par_x, par_y;						/* custom pixel aspect ratio */
	int ar_mode;							/* picture/pixel AR */

	/* zones */
	int num_zones;
	zone_t zones[MAX_ZONES];
	int cur_zone;		/* used internally; *not* written to registry */

	/* single pass */
	int rc_reaction_delay_factor;
	int rc_averaging_period;
	int rc_buffer;

	/* 2pass1 */
	int discard1pass;

	/* 2pass2 */
	int keyframe_boost;
	int kfthreshold;
	int kfreduction;
	int curve_compression_high;
	int curve_compression_low;
	int overflow_control_strength;
	int twopass_max_overflow_improvement;
	int twopass_max_overflow_degradation;

	/* bitrate calculator */
	int target_size;
	int subtitle_size;
	int container_type;
	int hours;
	int minutes;
	int seconds;
	int fps;
	int audio_mode;
	int audio_type;
	int audio_rate;
	int audio_size;

  /* user defined quality settings */
  quality_t quality_user;

	/* debug */
	int num_threads;
	int fourcc_used;
	int vop_debug;
	int debug;
	int display_status;
	int full1pass;

	DWORD cpu;

	int num_slices;

	/* internal */
	int ci_valid;
	VFWEXT_CONFIGURE_INFO_T ci;

	BOOL save;
} CONFIG;

typedef struct PROPSHEETINFO
{
	int idd;
	CONFIG * config;
} PROPSHEETINFO;

typedef struct REG_INT
{
	char* reg_value;
	int* config_int;
	int def;
} REG_INT;

typedef struct REG_STR
{
	char* reg_value;
	char* config_str;
	char* def;
} REG_STR;


#define PROFILE_ADAPTQUANT   0x00000001
#define PROFILE_BVOP		 0x00000002
#define PROFILE_MPEGQUANT	 0x00000004
#define PROFILE_INTERLACE	 0x00000008
#define PROFILE_QPEL		 0x00000010
#define PROFILE_GMC			 0x00000020
#define PROFILE_4MV		     0x00000040
#define PROFILE_PACKED       0x00000080
#define PROFILE_EXTRA        0x00000100
#define PROFILE_XVID         0x00000200
#define PROFILE_RESYNCMARKER 0x00000400

static const int PARS[][2] = {
	{1, 1},
	{12, 11},
	{10, 11},
	{16, 11},
	{40, 33},
	{0, 0},
};




typedef struct
{
	char * name;
	char * short_name;
	int id;		 /* mpeg-4 profile id; iso/iec 14496-2:2001 table G-1 */
	int width;
	int height;
	int fps;
	int max_objects;
	int total_vmv_buffer_sz;	/* macroblock memory; when BVOPS=false, vmv = 2*vcv; when BVOPS=true,  vmv = 3*vcv*/
	int max_vmv_buffer_sz;		/* max macroblocks per vop */
	int vcv_decoder_rate;		/* macroblocks decoded per second */
	int max_acpred_mbs;			/* percentage */ 
	int max_vbv_size;			/*	max vbv size (bits) 16368 bits */
	int max_video_packet_length;/* bits */
	int max_bitrate;			/* bits per second */
    int vbv_peakrate;			/* max bits over anyone second period; 0=don't care */
    int xvid_max_bframes;		/* xvid: max consecutive bframes */
	unsigned int flags;
} profile_t;


extern const profile_t profiles[];

extern const quality_t quality_table[];
extern const int quality_table_num; /* number of elements in quality table */


void config_reg_get(CONFIG * config);
void config_reg_set(CONFIG * config);
void sort_zones(zone_t * zones, int zone_num, int * sel);


INT_PTR CALLBACK main_proc(HWND, UINT, WPARAM, LPARAM);
INT_PTR CALLBACK about_proc(HWND, UINT, WPARAM, LPARAM);

#endif /* _CONFIG_H_ */
