/**************************************************************************
 *
 *	XVID VFW FRONTEND
 *	codec
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
 * $Id: codec.c 1985 2011-05-18 09:02:35Z Isibaar $
 *
 *************************************************************************/

#include <windows.h>
#include <vfw.h>
#include <stdio.h>
#include "vfwext.h"

#include <xvid.h>
#include "debug.h"
#include "codec.h"
#include "status.h"



static const int pmvfast_presets[7] = {
	0, 0, 0, 0,
	0 | XVID_ME_HALFPELREFINE16 | 0,
	0 | XVID_ME_HALFPELREFINE16 | 0 |
	XVID_ME_ADVANCEDDIAMOND16, XVID_ME_HALFPELREFINE16 | XVID_ME_EXTSEARCH16 |
	XVID_ME_HALFPELREFINE8 | 0 | XVID_ME_USESQUARES16
};



/*	return xvid compatbile colorspace,
	or XVID_CSP_NULL if failure
*/

static int get_colorspace(BITMAPINFOHEADER * hdr)
{
	/* rgb only: negative height specifies top down image */
	int rgb_flip = (hdr->biHeight < 0 ? 0 : XVID_CSP_VFLIP);

	switch(hdr->biCompression)
	{
	case BI_RGB :
		if (hdr->biBitCount == 16)
		{
			DPRINTF("RGB16 (RGB555)");
			return rgb_flip | XVID_CSP_RGB555;
		}
		if (hdr->biBitCount == 24) 
		{
			DPRINTF("RGB24");
			return rgb_flip | XVID_CSP_BGR;
		}
		if (hdr->biBitCount == 32) 
		{
			DPRINTF("RGB32");
			return rgb_flip | XVID_CSP_BGRA;
		}

		DPRINTF("unsupported BI_RGB biBitCount=%i", hdr->biBitCount);
		return XVID_CSP_NULL;

	case BI_BITFIELDS :
		if (hdr->biSize >= sizeof(BITMAPV4HEADER))
		{
			BITMAPV4HEADER * hdr4 = (BITMAPV4HEADER *)hdr;

			if (hdr4->bV4BitCount == 16 &&
				hdr4->bV4RedMask == 0x7c00 &&
				hdr4->bV4GreenMask == 0x3e0 &&
				hdr4->bV4BlueMask == 0x1f)
			{
				DPRINTF("RGB555");
				return rgb_flip | XVID_CSP_RGB555;
			}

			if(hdr4->bV4BitCount == 16 &&
				hdr4->bV4RedMask == 0xf800 &&
				hdr4->bV4GreenMask == 0x7e0 &&
				hdr4->bV4BlueMask == 0x1f)
			{
				DPRINTF("RGB565");
				return rgb_flip | XVID_CSP_RGB565;
			}

			DPRINTF("unsupported BI_BITFIELDS mode");
			return XVID_CSP_NULL;
		}
		
		DPRINTF("unsupported BI_BITFIELDS/BITMAPHEADER combination");
		return XVID_CSP_NULL;

	case FOURCC_I420 :
	case FOURCC_IYUV :
		DPRINTF("IYUY");
		return XVID_CSP_I420;

	case FOURCC_YV12 :
		DPRINTF("YV12");
		return XVID_CSP_YV12;
			
	case FOURCC_YUYV :
	case FOURCC_YUY2 :
		DPRINTF("YUY2");
		return XVID_CSP_YUY2;

	case FOURCC_YVYU :
		DPRINTF("YVYU");
		return XVID_CSP_YVYU;

	case FOURCC_UYVY :
		DPRINTF("UYVY");
		return XVID_CSP_UYVY;

	default :
		DPRINTF("unsupported colorspace %c%c%c%c", 
			hdr->biCompression&0xff,
			(hdr->biCompression>>8)&0xff,
			(hdr->biCompression>>16)&0xff,
			(hdr->biCompression>>24)&0xff);
		return XVID_CSP_NULL;
	}
}


/* compressor */


/* test the output format */

LRESULT compress_query(CODEC * codec, BITMAPINFO * lpbiInput, BITMAPINFO * lpbiOutput)
{
	BITMAPINFOHEADER * inhdr = &lpbiInput->bmiHeader;
	BITMAPINFOHEADER * outhdr = &lpbiOutput->bmiHeader;

	/* VFWEXT detection */
	if (inhdr->biCompression == VFWEXT_FOURCC) {
		return (ICM_USER+0x0fff);
	}

	if (get_colorspace(inhdr) == XVID_CSP_NULL) 
	{
		return ICERR_BADFORMAT;
	}

	if (lpbiOutput == NULL) 
	{
		return ICERR_OK;
	}

	if (inhdr->biWidth != outhdr->biWidth || inhdr->biHeight != outhdr->biHeight ||
		(outhdr->biCompression != FOURCC_XVID && outhdr->biCompression != FOURCC_DIVX && outhdr->biCompression != FOURCC_DX50))
	{
		return ICERR_BADFORMAT;
	}

	return ICERR_OK;
}


LRESULT compress_get_format(CODEC * codec, BITMAPINFO * lpbiInput, BITMAPINFO * lpbiOutput)
{
	BITMAPINFOHEADER * inhdr = &lpbiInput->bmiHeader;
	BITMAPINFOHEADER * outhdr = &lpbiOutput->bmiHeader;

	if (get_colorspace(inhdr) == XVID_CSP_NULL)
	{
		return ICERR_BADFORMAT;
	}

	if (lpbiOutput == NULL) 
	{
		return sizeof(BITMAPINFOHEADER);
	}

	memcpy(outhdr, inhdr, sizeof(BITMAPINFOHEADER));
	outhdr->biSize = sizeof(BITMAPINFOHEADER);
	outhdr->biSizeImage = compress_get_size(codec, lpbiInput, lpbiOutput);
	outhdr->biXPelsPerMeter = 0;
	outhdr->biYPelsPerMeter = 0;
	outhdr->biClrUsed = 0;
	outhdr->biClrImportant = 0;

	if ((codec->config.fourcc_used == 0) || (profiles[codec->config.profile].flags & PROFILE_XVID))
	{
		outhdr->biCompression = FOURCC_XVID;
	}
	else if (codec->config.fourcc_used == 1)
	{
		outhdr->biCompression = FOURCC_DIVX;
	}
	else
	{
		outhdr->biCompression = FOURCC_DX50;
	}

	return ICERR_OK;
}


LRESULT compress_get_size(CODEC * codec, BITMAPINFO * lpbiInput, BITMAPINFO * lpbiOutput)
{
	return 2 * lpbiOutput->bmiHeader.biWidth * lpbiOutput->bmiHeader.biHeight * 3;
}


LRESULT compress_frames_info(CODEC * codec, ICCOMPRESSFRAMES * icf)
{
#if 0
	DPRINTF("%i %i", icf->lStartFrame, icf->lFrameCount);
#endif
	codec->fincr = icf->dwScale;
	codec->fbase = icf->dwRate;
	return ICERR_OK;
}


static char type2char(int type)
{
	if (type==XVID_TYPE_IVOP)
		return 'I';
	if (type==XVID_TYPE_PVOP)
		return 'P';
	if (type==XVID_TYPE_BVOP)
		return 'B';
	return 'S';
}

static int vfw_debug(void *handle,
			 int opt,
			 void *param1,
			 void *param2)
{
	switch (opt) {
	case XVID_PLG_CREATE:
		*((void**)param2) = NULL;
	case XVID_PLG_INFO:
	case XVID_PLG_DESTROY:
	case XVID_PLG_BEFORE:
		return 0;

	case XVID_PLG_AFTER:
		{
			xvid_plg_data_t *data = (xvid_plg_data_t *) param1;

			/* We don't use DPRINTF here because it's active only for _DEBUG
			 * builds and that activates lot of other debug printfs. We only
			 * want these all the time */
			char buf[1024];
			sprintf(buf, "[%6i]   type=%c   Q:%2i   length:%6i",
					data->frame_num, 
					type2char(data->type),
					data->quant, 
					data->length);
			OutputDebugString(buf);

			return 0;
		}
	}

	return XVID_ERR_FAIL;
}

#define XVID_DLL_NAME "xvidcore.dll"

static int init_dll(CODEC* codec)
{
	if (codec->m_hdll != NULL)
		return 0;

	DPRINTF("init_dll");
	codec->m_hdll = LoadLibrary(XVID_DLL_NAME);
	if (codec->m_hdll == NULL) {
		DPRINTF("dll load failed");
		MessageBox(0, XVID_DLL_NAME " not found!","Error!", MB_ICONEXCLAMATION|MB_OK);
		return XVID_ERR_FAIL;
	}

	codec->xvid_global_func = (int (__cdecl *)(void *, int, void *, void *))GetProcAddress(codec->m_hdll, "xvid_global");
	if (codec->xvid_global_func == NULL) {
		MessageBox(0, "xvid_global() not found", "Error", 0);
		return XVID_ERR_FAIL;
	}

	codec->xvid_encore_func = (int (__cdecl *)(void *, int, void *, void *))GetProcAddress(codec->m_hdll, "xvid_encore");
	if (codec->xvid_encore_func == NULL) {
		MessageBox(0, "xvid_encore() not found", "Error", 0);
		return XVID_ERR_FAIL;
	}

	codec->xvid_decore_func = (int (__cdecl *)(void *, int, void *, void *))GetProcAddress(codec->m_hdll, "xvid_decore");
	if (codec->xvid_decore_func == NULL) {
		MessageBox(0, "xvid_decore() not found", "Error", 0);
		return XVID_ERR_FAIL;
	}

	codec->xvid_plugin_single_func = 
		(int (__cdecl *)(void *, int, void *, void *))(GetProcAddress(codec->m_hdll, "xvid_plugin_single"));
	codec->xvid_plugin_2pass1_func = 
		(int (__cdecl *)(void *, int, void *, void *))(GetProcAddress(codec->m_hdll, "xvid_plugin_2pass1"));
	codec->xvid_plugin_2pass2_func = 
		(int (__cdecl *)(void *, int, void *, void *))(GetProcAddress(codec->m_hdll, "xvid_plugin_2pass2"));
	codec->xvid_plugin_lumimasking_func = 
		(int (__cdecl *)(void *, int, void *, void *))(GetProcAddress(codec->m_hdll, "xvid_plugin_lumimasking"));
	codec->xvid_plugin_psnr_func = 
		(int (__cdecl *)(void *, int, void *, void *))(GetProcAddress(codec->m_hdll, "xvid_plugin_psnr"));

	return 0;
}

/* constant-quant zones for fixed quant encoding */
static void
prepare_cquant_zones(CONFIG * config) {
	
	int i = 0;
	if (config->num_zones == 0 || config->zones[0].frame != 0) {
		/* first zone does not start at frame 0 or doesn't exist */

		if (config->num_zones >= MAX_ZONES) config->num_zones--; /* we scrifice last zone */

		config->zones[config->num_zones].frame = 0;
		config->zones[config->num_zones].mode = RC_ZONE_QUANT;
		config->zones[config->num_zones].weight = 100;
		config->zones[config->num_zones].quant = config->desired_quant;
		config->zones[config->num_zones].type = XVID_TYPE_AUTO;
		config->zones[config->num_zones].greyscale = 0;
		config->zones[config->num_zones].chroma_opt = 0;
		config->zones[config->num_zones].bvop_threshold = 0;
		config->num_zones++;

		sort_zones(config->zones, config->num_zones, &i);
	}

	/* step 2: let's change all weight zones into quant zones */
	
	for(i = 0; i < config->num_zones; i++)
		if (config->zones[i].mode == RC_ZONE_WEIGHT) {
			config->zones[i].mode = RC_ZONE_QUANT;
			config->zones[i].quant = (100*config->desired_quant) / config->zones[i].weight;
		}
}

/* full first pass zones */
static void
prepare_full1pass_zones(CONFIG * config) {
	
	int i = 0;
	if (config->num_zones == 0 || config->zones[0].frame != 0) {
		/* first zone does not start at frame 0 or doesn't exist */

		if (config->num_zones >= MAX_ZONES) config->num_zones--; /* we scrifice last zone */

		config->zones[config->num_zones].frame = 0;
		config->zones[config->num_zones].mode = RC_ZONE_QUANT;
		config->zones[config->num_zones].weight = 100;
		config->zones[config->num_zones].quant = 200;
		config->zones[config->num_zones].type = XVID_TYPE_AUTO;
		config->zones[config->num_zones].greyscale = 0;
		config->zones[config->num_zones].chroma_opt = 0;
		config->zones[config->num_zones].bvop_threshold = 0;
		config->num_zones++;

		sort_zones(config->zones, config->num_zones, &i);
	}

	/* step 2: let's change all weight zones into quant zones */
	
	for(i = 0; i < config->num_zones; i++)
		if (config->zones[i].mode == RC_ZONE_WEIGHT) {
			config->zones[i].mode = RC_ZONE_QUANT;
			config->zones[i].quant = 200;
		}
}


LRESULT compress_begin(CODEC * codec, BITMAPINFO * lpbiInput, BITMAPINFO * lpbiOutput)
{
	xvid_gbl_init_t init;
	xvid_enc_create_t create;
	xvid_enc_plugin_t plugins[3];
	xvid_plugin_single_t single;
	xvid_plugin_2pass1_t pass1;
	xvid_plugin_2pass2_t pass2;
	xvid_plugin_lumimasking_t masking;
    xvid_gbl_info_t info;
	int i;
	HANDLE hFile;
	const quality_t* quality_preset = (codec->config.quality==quality_table_num) ?
    &codec->config.quality_user : &quality_table[codec->config.quality];

	CONFIG tmpCfg; /* if we want to alter config to suit our needs, it shouldn't be visible to user later */
	memcpy(&tmpCfg, &codec->config, sizeof(CONFIG));

	if (init_dll(codec) != 0) return ICERR_ERROR;
	/* destroy previously created codec */
	if(codec->ehandle) {
		codec->xvid_encore_func(codec->ehandle, XVID_ENC_DESTROY, NULL, NULL);
		codec->ehandle = NULL;
	}

	memset(&init, 0, sizeof(init));
	init.version = XVID_VERSION;
	init.cpu_flags = codec->config.cpu;
	init.debug = codec->config.debug;
	codec->xvid_global_func(0, XVID_GBL_INIT, &init, NULL);

	memset(&info, 0, sizeof(info));
	info.version = XVID_VERSION;
	codec->xvid_global_func(0, XVID_GBL_INFO, &info, NULL);

	memset(&create, 0, sizeof(create));
	create.version = XVID_VERSION;

    /* Encoder threads */
    if (codec->config.cpu & XVID_CPU_FORCE)
		create.num_threads = codec->config.num_threads;
	else 
        create.num_threads = info.num_threads; /* Autodetect */

	/* Encoder slices */
	if ((profiles[codec->config.profile].flags & PROFILE_RESYNCMARKER) && codec->config.num_slices != 1) {
		
		if (codec->config.num_slices == 0) { /* auto */
			int mb_width = (lpbiInput->bmiHeader.biWidth + 15) / 16;
			int mb_height = (lpbiInput->bmiHeader.biHeight + 15) / 16;

			int slices = (int)((mb_width*mb_height) / 811); /* use multiple slices only above SD resolutions for now */

			if (slices > 1) {
				if (create.num_threads <= 1)
					slices &= ~1; /* make even */
				else if (create.num_threads <= slices)
					slices = (slices / create.num_threads) * create.num_threads; /* multiple of threads */
				else if (create.num_threads % slices)
					slices = (!(create.num_threads%2)) ? (create.num_threads/2) : (create.num_threads/3);
			}

			create.num_slices = slices;
		}
		else {
			create.num_slices = codec->config.num_slices; /* force manual value - by registry edit */
		}

	}

	/* plugins */
	create.plugins = plugins;
	switch (codec->config.mode) 
	{
	case RC_MODE_1PASS :
		memset(&single, 0, sizeof(single));
		single.version = XVID_VERSION;
		single.bitrate = codec->config.bitrate * CONFIG_KBPS;
		single.reaction_delay_factor = codec->config.rc_reaction_delay_factor;
		single.averaging_period = codec->config.rc_averaging_period;
		single.buffer = codec->config.rc_buffer;
		plugins[create.num_plugins].func = codec->xvid_plugin_single_func;
		plugins[create.num_plugins].param = &single;
		create.num_plugins++;
		if (!codec->config.use_2pass_bitrate) /* constant-quant mode */
			prepare_cquant_zones(&tmpCfg);
		break;

	case RC_MODE_2PASS1 :
		memset(&pass1, 0, sizeof(pass1));
		pass1.version = XVID_VERSION;
		pass1.filename = codec->config.stats;
		if (codec->config.full1pass)
			prepare_full1pass_zones(&tmpCfg);
		plugins[create.num_plugins].func = codec->xvid_plugin_2pass1_func;
		plugins[create.num_plugins].param = &pass1;
		create.num_plugins++;
		break;

	case RC_MODE_2PASS2 :
		memset(&pass2, 0, sizeof(pass2));
		pass2.version = XVID_VERSION;
		if (codec->config.use_2pass_bitrate) {
			pass2.bitrate = codec->config.bitrate * CONFIG_KBPS;
		} else {
			pass2.bitrate = -codec->config.desired_size;	/* kilobytes */
		}
		pass2.filename = codec->config.stats;

		hFile = CreateFile(pass2.filename, 0, FILE_SHARE_READ | FILE_SHARE_WRITE, NULL, OPEN_EXISTING, 0, NULL);	
		if (hFile == INVALID_HANDLE_VALUE)
		{
			MessageBox(0, "Statsfile not found!","Error!", MB_ICONEXCLAMATION|MB_OK);
			return XVID_ERR_FAIL;
		} else
		{
			CloseHandle(hFile);
		}

		pass2.keyframe_boost = codec->config.keyframe_boost;   /* keyframe boost percentage: [0..100...]; */
		pass2.curve_compression_high = codec->config.curve_compression_high;
		pass2.curve_compression_low = codec->config.curve_compression_low;
		pass2.overflow_control_strength = codec->config.overflow_control_strength;
		pass2.max_overflow_improvement = codec->config.twopass_max_overflow_improvement;
		pass2.max_overflow_degradation = codec->config.twopass_max_overflow_degradation;
		pass2.kfreduction = codec->config.kfreduction;
		pass2.kfthreshold = codec->config.kfthreshold;
		pass2.container_frame_overhead = 24;	/* AVI */

		/* VBV */
		pass2.vbv_size = profiles[codec->config.profile].max_vbv_size;
		pass2.vbv_initial = (profiles[codec->config.profile].max_vbv_size*3)/4; /* 75% */
		pass2.vbv_maxrate = profiles[codec->config.profile].max_bitrate;
		pass2.vbv_peakrate = profiles[codec->config.profile].vbv_peakrate;

		plugins[create.num_plugins].func = codec->xvid_plugin_2pass2_func;
		plugins[create.num_plugins].param = &pass2;
		create.num_plugins++;
		break;

	case RC_MODE_NULL :
		return ICERR_OK;

	default :
		break;
	}

	/* zones  - copy from tmpCfg in case we automatically altered them above */
	create.zones = malloc(sizeof(xvid_enc_zone_t) * tmpCfg.num_zones);
	create.num_zones = tmpCfg.num_zones;
	for (i=0; i < create.num_zones; i++) {
		create.zones[i].frame = tmpCfg.zones[i].frame;
		if (tmpCfg.zones[i].mode == RC_ZONE_QUANT) {
			create.zones[i].mode = XVID_ZONE_QUANT;
			create.zones[i].increment = tmpCfg.zones[i].quant;
		}else{
			create.zones[i].mode = XVID_ZONE_WEIGHT;
			create.zones[i].increment = tmpCfg.zones[i].weight;
		}
		create.zones[i].base = 100;
	}

	/* lumimasking plugin */
  	if ((profiles[codec->config.profile].flags & PROFILE_ADAPTQUANT) && (codec->config.lum_masking>0)) {
		memset(&masking, 0, sizeof(masking));
		masking.method = (codec->config.lum_masking==2);
		plugins[create.num_plugins].func = codec->xvid_plugin_lumimasking_func;
		plugins[create.num_plugins].param = &masking;
		create.num_plugins++; 
	}

	plugins[create.num_plugins].func = vfw_debug;
	plugins[create.num_plugins].param = NULL;
	create.num_plugins++; 

	create.profile = profiles[codec->config.profile].id;

	create.width = lpbiInput->bmiHeader.biWidth;
	create.height = lpbiInput->bmiHeader.biHeight;
	create.fincr = codec->fincr;
	create.fbase = codec->fbase;

	create.max_key_interval = quality_preset->max_key_interval;

	create.min_quant[0] = quality_preset->min_iquant;
	create.max_quant[0] = quality_preset->max_iquant;
	create.min_quant[1] = quality_preset->min_pquant;
	create.max_quant[1] = quality_preset->max_pquant;
	create.min_quant[2] = quality_preset->min_bquant;
	create.max_quant[2] = quality_preset->max_bquant;

	if ((profiles[codec->config.profile].flags & PROFILE_BVOP) && codec->config.use_bvop) {

    /* dxn: prevent bframes usage if interlacing is selected */
    if (!((profiles[codec->config.profile].flags & PROFILE_EXTRA) && codec->config.interlacing)) {
      create.max_bframes = codec->config.max_bframes;
		  create.bquant_ratio = codec->config.bquant_ratio;
		  create.bquant_offset = codec->config.bquant_offset;

		  if (codec->config.packed) 
			  create.global |= XVID_GLOBAL_PACKED;

		  create.global |= XVID_GLOBAL_CLOSED_GOP;

      /* restrict max bframes */
      if ((create.max_bframes > profiles[codec->config.profile].xvid_max_bframes) && (profiles[codec->config.profile].xvid_max_bframes >= 0))
        create.max_bframes = profiles[codec->config.profile].xvid_max_bframes;

      /* DXN: enable packed bframes */
      if ((profiles[codec->config.profile].flags & PROFILE_PACKED)) {
        create.global |= XVID_GLOBAL_PACKED;
      }
    }
	}

    /* dxn: always write divx5 userdata */
    if ((profiles[codec->config.profile].flags & PROFILE_EXTRA))
      create.global |= XVID_GLOBAL_DIVX5_USERDATA;

	if ((profiles[codec->config.profile].flags & PROFILE_EXTRA) || 
		(profiles[codec->config.profile].flags & PROFILE_XVID)) {
  	  create.frame_drop_ratio = 0;
	} else {
  	  create.frame_drop_ratio = quality_preset->frame_drop_ratio;
	}

	switch(codec->xvid_encore_func(0, XVID_ENC_CREATE, &create, NULL))
	{
	case XVID_ERR_FAIL :	
		return ICERR_ERROR;

	case XVID_ERR_MEMORY :
		return ICERR_MEMORY;

	case XVID_ERR_FORMAT :
		return ICERR_BADFORMAT;

	case XVID_ERR_VERSION :
		return ICERR_UNSUPPORTED;
	}

	free(create.zones);
	codec->ehandle = create.handle;
	codec->framenum = 0;
	codec->keyspacing = 0;

	if (codec->config.display_status) {
		status_destroy_always(&codec->status);
		status_create(&codec->status, codec->fincr, codec->fbase);
	}

	return ICERR_OK;
}


LRESULT compress_end(CODEC * codec)
{
  if (codec==NULL)
    return ICERR_OK;

	if (codec->m_hdll != NULL) {
		if (codec->ehandle != NULL) {
			codec->xvid_encore_func(codec->ehandle, XVID_ENC_DESTROY, NULL, NULL);
			codec->ehandle = NULL;
		}
	}

	if (codec->config.display_status)
		status_destroy(&codec->status);

	return ICERR_OK;
}


static void apply_zone_modifiers(xvid_enc_frame_t * frame, CONFIG * config, int framenum)
{
	int i;

	for (i=0; i<config->num_zones && config->zones[i].frame <= framenum; i++) ;

	if (--i < 0) return; /* there are no zones, or we're before the first zone */

	if (framenum == config->zones[i].frame)
		frame->type = config->zones[i].type;

	if (config->zones[i].greyscale) {
		frame->vop_flags |= XVID_VOP_GREYSCALE;
	}

	if (config->zones[i].chroma_opt) {
		frame->vop_flags |= XVID_VOP_CHROMAOPT;
	}

	if (config->zones[i].cartoon_mode) {
		frame->vop_flags |= XVID_VOP_CARTOON;
		frame->motion |= XVID_ME_DETECT_STATIC_MOTION;
	}

	if ((profiles[config->profile].flags & PROFILE_BVOP) && config->use_bvop) {
		frame->bframe_threshold = config->zones[i].bvop_threshold;
	}
}


#define CALC_BI_STRIDE(width,bitcount)  ((((width * bitcount) + 31) & ~31) >> 3)

LRESULT compress(CODEC * codec, ICCOMPRESS * icc)
{
	BITMAPINFOHEADER * inhdr = icc->lpbiInput;
	BITMAPINFOHEADER * outhdr = icc->lpbiOutput;
	xvid_enc_frame_t frame;
	xvid_enc_stats_t stats;
	int length;
  const quality_t* quality_preset = (codec->config.quality==quality_table_num) ?
    &codec->config.quality_user : &quality_table[codec->config.quality];
	
	memset(&frame, 0, sizeof(frame));
	frame.version = XVID_VERSION;

	frame.type = XVID_TYPE_AUTO;

	/* vol stuff */

	if ((profiles[codec->config.profile].flags & PROFILE_MPEGQUANT) && 
		codec->config.quant_type != QUANT_MODE_H263)
	{
		frame.vol_flags |= XVID_VOL_MPEGQUANT;

		if (codec->config.quant_type == QUANT_MODE_CUSTOM) {
			frame.quant_intra_matrix = codec->config.qmatrix_intra;
			frame.quant_inter_matrix = codec->config.qmatrix_inter;
		}else{
			frame.quant_intra_matrix = NULL;
			frame.quant_inter_matrix = NULL;
		}
	}

	if ((profiles[codec->config.profile].flags & PROFILE_QPEL) && codec->config.qpel) {
		frame.vol_flags |= XVID_VOL_QUARTERPEL;
		frame.motion |= XVID_ME_QUARTERPELREFINE16 | XVID_ME_QUARTERPELREFINE8;
	}

	if ((profiles[codec->config.profile].flags & PROFILE_GMC) && codec->config.gmc) {
		frame.vol_flags |= XVID_VOL_GMC;
		frame.motion |= XVID_ME_GME_REFINE;
	}

	if ((profiles[codec->config.profile].flags & PROFILE_INTERLACE) && codec->config.interlacing)
		frame.vol_flags |= XVID_VOL_INTERLACING;

  /* dxn: force 1:1 picture aspect ration */
  if ((profiles[codec->config.profile].flags & PROFILE_EXTRA)) {
    frame.par = XVID_PAR_11_VGA;
  } else if (codec->config.ar_mode == 0) { /* PAR */
		if (codec->config.display_aspect_ratio != 5) {
			frame.par = codec->config.display_aspect_ratio + 1;
		} else {
			frame.par = XVID_PAR_EXT;
			frame.par_width = codec->config.par_x;
			frame.par_height= codec->config.par_y;
		}
	} else { /* AR */
		/* custom pixel aspect ratio -> calculated from DAR */
		frame.par = XVID_PAR_EXT;
		frame.par_width = (100 * inhdr->biHeight) / codec->config.ar_y;
		frame.par_height= (100 * inhdr->biWidth) / codec->config.ar_x;
	}

	/* vop stuff */

	frame.vop_flags |= XVID_VOP_HALFPEL;
	frame.vop_flags |= XVID_VOP_HQACPRED;

	if (codec->config.interlacing && codec->config.tff)
		frame.vop_flags |= XVID_VOP_TOPFIELDFIRST;


	if (codec->config.vop_debug) 
		frame.vop_flags |= XVID_VOP_DEBUG;

	if (quality_preset->trellis_quant) {
		frame.vop_flags |= XVID_VOP_TRELLISQUANT;
	}

  if ((profiles[codec->config.profile].flags & PROFILE_4MV)) {
	  if (quality_preset->motion_search > 4)
		  frame.vop_flags |= XVID_VOP_INTER4V;
  }

	if (quality_preset->chromame)
		frame.motion |= XVID_ME_CHROMA_PVOP + XVID_ME_CHROMA_BVOP;

	if (quality_preset->turbo)
 		frame.motion |= XVID_ME_FASTREFINE16 | XVID_ME_FASTREFINE8 | 
						XVID_ME_SKIP_DELTASEARCH | XVID_ME_FAST_MODEINTERPOLATE | 
						XVID_ME_BFRAME_EARLYSTOP;

	frame.motion |= pmvfast_presets[quality_preset->motion_search];

	if (quality_preset->vhq_bframe) frame.vop_flags |= XVID_VOP_RD_BVOP;


	switch (quality_preset->vhq_mode)
	{
	case VHQ_MODE_DECISION :
		frame.vop_flags |= XVID_VOP_MODEDECISION_RD;
		break;

	case VHQ_LIMITED_SEARCH :
		frame.vop_flags |= XVID_VOP_MODEDECISION_RD;
		frame.motion |= XVID_ME_HALFPELREFINE16_RD;
		frame.motion |= XVID_ME_QUARTERPELREFINE16_RD;
		break;

	case VHQ_MEDIUM_SEARCH :
		frame.vop_flags |= XVID_VOP_MODEDECISION_RD;
		frame.motion |= XVID_ME_HALFPELREFINE16_RD;
		frame.motion |= XVID_ME_HALFPELREFINE8_RD;
		frame.motion |= XVID_ME_QUARTERPELREFINE16_RD;
		frame.motion |= XVID_ME_QUARTERPELREFINE8_RD;
		frame.motion |= XVID_ME_CHECKPREDICTION_RD;
		break;

	case VHQ_WIDE_SEARCH :
		frame.vop_flags |= XVID_VOP_MODEDECISION_RD;
		frame.motion |= XVID_ME_HALFPELREFINE16_RD;
		frame.motion |= XVID_ME_HALFPELREFINE8_RD;
		frame.motion |= XVID_ME_QUARTERPELREFINE16_RD;
		frame.motion |= XVID_ME_QUARTERPELREFINE8_RD;
		frame.motion |= XVID_ME_CHECKPREDICTION_RD;
		frame.motion |= XVID_ME_EXTSEARCH_RD;
		break;

	default :
		break;
	}

	if (quality_preset->vhq_metric == 1)
		frame.vop_flags |= XVID_VOP_RD_PSNRHVSM;

	frame.input.plane[0] = icc->lpInput;
	frame.input.stride[0] = CALC_BI_STRIDE(icc->lpbiInput->biWidth, icc->lpbiInput->biBitCount);

	if ((frame.input.csp = get_colorspace(inhdr)) == XVID_CSP_NULL)
		return ICERR_BADFORMAT;

	if (frame.input.csp == XVID_CSP_I420 || frame.input.csp == XVID_CSP_YV12) {
		frame.input.stride[0] = (4 * icc->lpbiInput->biWidth + 3) / 4;
		frame.input.stride[1] = frame.input.stride[2] = frame.input.stride[0] / 2 ;
	}

	frame.bitstream = icc->lpOutput;
	frame.length = icc->lpbiOutput->biSizeImage;

	frame.quant = 0;

	if (codec->config.mode == RC_MODE_NULL) {
		outhdr->biSizeImage = 0;
		*icc->lpdwFlags = AVIIF_KEYFRAME;
		return ICERR_OK;
	}

	// force keyframe spacing in 2-pass 1st pass
	if (quality_preset->motion_search == 0)
		frame.type = XVID_TYPE_IVOP;

	/* frame-based stuff */
	apply_zone_modifiers(&frame, &codec->config, codec->framenum);

	/* call encore */

	memset(&stats, 0, sizeof(stats));
	stats.version = XVID_VERSION;

	length = codec->xvid_encore_func(codec->ehandle, XVID_ENC_ENCODE, &frame, &stats);
	switch (length) 
	{
	case XVID_ERR_FAIL :	
		return ICERR_ERROR;

	case XVID_ERR_MEMORY :
		return ICERR_MEMORY;

	case XVID_ERR_FORMAT :
		return ICERR_BADFORMAT;	

	case XVID_ERR_VERSION :
		return ICERR_UNSUPPORTED;
	}

	if (codec->config.display_status && stats.type>0) {
		status_update(&codec->status, stats.type, stats.length, stats.quant);
	}

	DPRINTF("{type=%i len=%i} length=%i", stats.type, stats.length, length);

	if (length == 0)	/* no encoder output */
	{
		*icc->lpdwFlags = 0;
		((char*)icc->lpOutput)[0] = 0x7f;	/* virtual dub skip frame */
		outhdr->biSizeImage = 1;
		
	}else{
		if (frame.out_flags & XVID_KEYFRAME)
		{
			codec->keyspacing = 0;
			*icc->lpdwFlags = AVIIF_KEYFRAME;
		}
		else
		{
			 *icc->lpdwFlags = 0;
		}

		outhdr->biSizeImage = length;

		if (codec->config.mode == RC_MODE_2PASS1 && codec->config.discard1pass)
		{
			outhdr->biSizeImage = 0;
		}
	}

	codec->framenum++;
	codec->keyspacing++;

	return ICERR_OK;
}


/* decompressor */


LRESULT decompress_query(CODEC * codec, BITMAPINFO *lpbiInput, BITMAPINFO *lpbiOutput)
{
	BITMAPINFOHEADER * inhdr = &lpbiInput->bmiHeader;
	BITMAPINFOHEADER * outhdr = &lpbiOutput->bmiHeader;
	int in_csp = XVID_CSP_NULL, out_csp = XVID_CSP_NULL;

	if (lpbiInput == NULL) 
	{
		return ICERR_ERROR;
	}

	if (inhdr->biCompression != FOURCC_XVID && inhdr->biCompression != FOURCC_DIVX && inhdr->biCompression != FOURCC_DX50 && inhdr->biCompression != FOURCC_MP4V &&
		inhdr->biCompression != FOURCC_xvid && inhdr->biCompression != FOURCC_divx && inhdr->biCompression != FOURCC_dx50 && inhdr->biCompression != FOURCC_mp4v &&
		(in_csp = get_colorspace(inhdr)) != XVID_CSP_YV12)
	{
		return ICERR_BADFORMAT;
	}

	if (lpbiOutput == NULL) 
	{
		return ICERR_OK;
	}

	out_csp = get_colorspace(outhdr);

	if (inhdr->biWidth != outhdr->biWidth ||
		inhdr->biHeight != outhdr->biHeight ||
		out_csp == XVID_CSP_NULL ||
		(in_csp == XVID_CSP_YV12 && in_csp != out_csp)) 
	{
		return ICERR_BADFORMAT;
	}

	return ICERR_OK;
}


LRESULT decompress_get_format(CODEC * codec, BITMAPINFO * lpbiInput, BITMAPINFO * lpbiOutput)
{
	BITMAPINFOHEADER * inhdr = &lpbiInput->bmiHeader;
	BITMAPINFOHEADER * outhdr = &lpbiOutput->bmiHeader;
	LRESULT result;

	if (lpbiOutput == NULL) 
	{
		return sizeof(BITMAPINFOHEADER);
	}

	/* --- yv12 --- */

	if (get_colorspace(inhdr) != XVID_CSP_NULL) {
		memcpy(outhdr, inhdr, sizeof(BITMAPINFOHEADER));
		/* XXX: should we set outhdr->biSize ?? */
		return ICERR_OK;
	}
	/* --- yv12 --- */

	result = decompress_query(codec, lpbiInput, lpbiOutput);
	if (result != ICERR_OK) 
	{
		return result;
	}

	outhdr->biSize = sizeof(BITMAPINFOHEADER);
	outhdr->biWidth = inhdr->biWidth;
	outhdr->biHeight = inhdr->biHeight;
	outhdr->biPlanes = 1;
	outhdr->biBitCount = 24;
	outhdr->biCompression = BI_RGB;	/* sonic foundry vegas video v3 only supports BI_RGB */
	outhdr->biSizeImage = outhdr->biHeight * CALC_BI_STRIDE(outhdr->biWidth, outhdr->biBitCount);

	outhdr->biXPelsPerMeter = 0;
	outhdr->biYPelsPerMeter = 0;
	outhdr->biClrUsed = 0;
	outhdr->biClrImportant = 0;

	return ICERR_OK;
}

#define REG_GET_N(X, Y, Z) \
{ \
	DWORD size = sizeof(int); \
	if (RegQueryValueEx(hKey, X, 0, 0, (LPBYTE)&Y, &size) != ERROR_SUCCESS) { \
		Y=Z; \
	} \
}while(0)

LRESULT decompress_begin(CODEC * codec, BITMAPINFO * lpbiInput, BITMAPINFO * lpbiOutput)
{
	BITMAPINFOHEADER * inhdr = &lpbiInput->bmiHeader;
	xvid_gbl_init_t init;
	xvid_gbl_info_t info;
	xvid_dec_create_t create;
	HKEY hKey;

	if (init_dll(codec) != 0) return ICERR_ERROR;

	memset(&init, 0, sizeof(init));
	init.version = XVID_VERSION;
	init.cpu_flags = codec->config.cpu;
	init.debug = codec->config.debug;
	codec->xvid_global_func(0, XVID_GBL_INIT, &init, NULL);

	memset(&info, 0, sizeof(info));
	info.version = XVID_VERSION;
	codec->xvid_global_func(0, XVID_GBL_INFO, &info, NULL);

	memset(&create, 0, sizeof(create));
	create.version = XVID_VERSION;
	create.width = lpbiInput->bmiHeader.biWidth;
	create.height = lpbiInput->bmiHeader.biHeight;
	create.fourcc = inhdr->biCompression;

    /* Decoder threads */
    if (codec->config.cpu & XVID_CPU_FORCE)
		create.num_threads = codec->config.num_threads;
	else 
        create.num_threads = info.num_threads; /* Autodetect */

	switch(codec->xvid_decore_func(0, XVID_DEC_CREATE, &create, NULL)) 
	{
	case XVID_ERR_FAIL :
		return ICERR_ERROR;

	case XVID_ERR_MEMORY :
		return ICERR_MEMORY;

	case XVID_ERR_FORMAT :
		return ICERR_BADFORMAT;

	case XVID_ERR_VERSION :
		return ICERR_UNSUPPORTED;
	}

	codec->dhandle = create.handle;

	RegOpenKeyEx(XVID_REG_KEY, XVID_REG_PARENT "\\" XVID_REG_CHILD, 0, KEY_READ, &hKey);

	REG_GET_N("Brightness", pp_brightness, 0);
	REG_GET_N("Deblock_Y",  pp_dy, 0);
	REG_GET_N("Deblock_UV", pp_duv, 0);
	REG_GET_N("Dering_Y",  pp_dry, 0);
	REG_GET_N("Dering_UV", pp_druv, 0);
	REG_GET_N("FilmEffect", pp_fe, 0);

	RegCloseKey(hKey);

	return ICERR_OK;
}


LRESULT decompress_end(CODEC * codec)
{
	if (codec->m_hdll != NULL) {
		if (codec->dhandle != NULL) {
			codec->xvid_decore_func(codec->dhandle, XVID_DEC_DESTROY, NULL, NULL);
			codec->dhandle = NULL;
		}
	}

	return ICERR_OK;
}


LRESULT decompress(CODEC * codec, ICDECOMPRESS * icd)
{
	xvid_dec_frame_t frame;
	
	/* --- yv12 --- */	
	if (icd->lpbiInput->biCompression != FOURCC_XVID &&
		 icd->lpbiInput->biCompression != FOURCC_DIVX &&
		 icd->lpbiInput->biCompression != FOURCC_DX50 &&
		 icd->lpbiInput->biCompression != FOURCC_MP4V &&
		 icd->lpbiInput->biCompression != FOURCC_xvid &&
		 icd->lpbiInput->biCompression != FOURCC_divx &&
		 icd->lpbiInput->biCompression != FOURCC_dx50 &&
		 icd->lpbiInput->biCompression != FOURCC_mp4v)
	{
		xvid_gbl_convert_t convert;

		DPRINTF("input=%c%c%c%c output=%c%c%c%c", 
			icd->lpbiInput->biCompression&0xff,
			(icd->lpbiInput->biCompression>>8)&0xff,
			(icd->lpbiInput->biCompression>>16)&0xff,
			(icd->lpbiInput->biCompression>>24)&0xff,
			icd->lpbiOutput->biCompression&0xff,
			(icd->lpbiOutput->biCompression>>8)&0xff,
			(icd->lpbiOutput->biCompression>>16)&0xff,
			(icd->lpbiOutput->biCompression>>24)&0xff);

		memset(&convert, 0, sizeof(convert));
		convert.version = XVID_VERSION;

		convert.input.csp = get_colorspace(icd->lpbiInput);
		convert.input.plane[0] = icd->lpInput;
		convert.input.stride[0] = CALC_BI_STRIDE(icd->lpbiInput->biWidth, icd->lpbiInput->biBitCount);
		if (convert.input.csp == XVID_CSP_I420 || convert.input.csp == XVID_CSP_YV12)
			convert.input.stride[0] = (convert.input.stride[0]*2)/3;

		convert.output.csp = get_colorspace(icd->lpbiOutput);
		convert.output.plane[0] = icd->lpOutput;
		convert.output.stride[0] = CALC_BI_STRIDE(icd->lpbiOutput->biWidth, icd->lpbiOutput->biBitCount);
		if (convert.output.csp == XVID_CSP_I420 || convert.output.csp == XVID_CSP_YV12)
			convert.output.stride[0] = (convert.output.stride[0]*2)/3;

		convert.width = icd->lpbiInput->biWidth;
		convert.height = icd->lpbiInput->biHeight;
		convert.interlacing = 0;
		if (convert.input.csp == XVID_CSP_NULL ||
			convert.output.csp == XVID_CSP_NULL ||
			codec->xvid_global_func(0, XVID_GBL_CONVERT, &convert, NULL) < 0)
		{
			 return ICERR_BADFORMAT;
		}
		return ICERR_OK;
	}
	/* --- yv12 --- */
	
	memset(&frame, 0, sizeof(frame));
	frame.version = XVID_VERSION;
	frame.general = XVID_LOWDELAY;	/* force low_delay_default mode */
	frame.bitstream = icd->lpInput;
	frame.length = icd->lpbiInput->biSizeImage;
	
	if (~((icd->dwFlags & ICDECOMPRESS_HURRYUP) | (icd->dwFlags & ICDECOMPRESS_UPDATE) | (icd->dwFlags & ICDECOMPRESS_PREROLL)))
	{
		if ((frame.output.csp = get_colorspace(icd->lpbiOutput)) == XVID_CSP_NULL) 
		{
			return ICERR_BADFORMAT;
		}
		frame.output.plane[0] = icd->lpOutput;
		frame.output.stride[0] = CALC_BI_STRIDE(icd->lpbiOutput->biWidth, icd->lpbiOutput->biBitCount);
		if (frame.output.csp == XVID_CSP_I420 || frame.output.csp == XVID_CSP_YV12)
			frame.output.stride[0] = CALC_BI_STRIDE(icd->lpbiOutput->biWidth, 8);
	}
	else
	{
		frame.output.csp = XVID_CSP_NULL;
	}

	if (pp_dy)frame.general |= XVID_DEBLOCKY;
	if (pp_duv) frame.general |= XVID_DEBLOCKUV;
	if (pp_dry) frame.general |= XVID_DERINGY; 
	if (pp_druv) frame.general |= XVID_DERINGUV; 
	if (pp_fe) frame.general |= XVID_FILMEFFECT;

	frame.brightness = pp_brightness;

	switch (codec->xvid_decore_func(codec->dhandle, XVID_DEC_DECODE, &frame, NULL)) 
	{
	case XVID_ERR_FAIL :	
		return ICERR_ERROR;

	case XVID_ERR_MEMORY :
		return ICERR_MEMORY;

	case XVID_ERR_FORMAT :
		return ICERR_BADFORMAT;	

	case XVID_ERR_VERSION :
		return ICERR_UNSUPPORTED;
	}

	return ICERR_OK;
}

