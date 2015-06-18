/*****************************************************************************
 *
 *	XVID MPEG-4 VFW FRONTEND
 *	- driverproc main -
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
 * $Id: driverproc.c 1985 2011-05-18 09:02:35Z Isibaar $
 *
 ****************************************************************************/

#include <windows.h>
#include <vfw.h>
#include "vfwext.h"

#include "debug.h"
#include "codec.h"
#include "config.h"
#include "status.h"
#include "resource.h"

static int clean_dll_bindings(CODEC* codec);

INT_PTR WINAPI DllMain(
		HANDLE hModule, 
		DWORD  ul_reason_for_call, 
		LPVOID lpReserved)
{
	g_hInst = (HINSTANCE) hModule;
    return TRUE;
}

/* __declspec(dllexport) */ LRESULT WINAPI DriverProc(
	DWORD_PTR dwDriverId, 
	HDRVR hDriver, 
	UINT uMsg, 
	LPARAM lParam1, 
	LPARAM lParam2) 
{
	CODEC * codec = (CODEC *)dwDriverId;

	switch(uMsg)
	{

	/* driver primitives */

	case DRV_LOAD :
	case DRV_FREE :
		return DRVCNF_OK;

	case DRV_OPEN :
		DPRINTF("DRV_OPEN");

		{
			ICOPEN * icopen = (ICOPEN *)lParam2;
			
			if (icopen != NULL && icopen->fccType != ICTYPE_VIDEO)
			{
				return DRVCNF_CANCEL;
			}

			codec = malloc(sizeof(CODEC));

			if (codec == NULL)
			{
				if (icopen != NULL)
				{
					icopen->dwError = ICERR_MEMORY;
				}
				return 0;
			}
            
			memset(codec, 0, sizeof(CODEC));
            
            codec->status.hDlg = NULL;
            codec->config.ci_valid = 0;
            codec->ehandle = codec->dhandle = NULL;
            codec->fbase = 25;
			codec->fincr = 1;
			config_reg_get(&codec->config);

#if 0
			/* bad things happen if this piece of code is activated */
			if (lstrcmp(XVID_BUILD, codec->config.build))
			{
				config_reg_default(&codec->config);
			}
#endif			

			if (icopen != NULL)
			{
				icopen->dwError = ICERR_OK;
			}
			return (LRESULT)codec;
		}

	case DRV_CLOSE :
		DPRINTF("DRV_CLOSE");
		/* compress_end/decompress_end don't always get called */
		compress_end(codec);
		decompress_end(codec);
		clean_dll_bindings(codec);
        status_destroy_always(&codec->status);
		free(codec);
		return DRVCNF_OK;

	case DRV_DISABLE :
	case DRV_ENABLE :
		return DRVCNF_OK;

	case DRV_INSTALL :
	case DRV_REMOVE :
		return DRVCNF_OK;

	case DRV_QUERYCONFIGURE :
	case DRV_CONFIGURE :
		return DRVCNF_CANCEL;


	/* info */

	case ICM_GETINFO :
		DPRINTF("ICM_GETINFO");
		
		if (lParam1 && lParam2 >= sizeof(ICINFO)) {
			ICINFO *icinfo = (ICINFO *)lParam1;

			icinfo->fccType = ICTYPE_VIDEO;
			icinfo->fccHandler = FOURCC_XVID;
			icinfo->dwFlags =
				VIDCF_FASTTEMPORALC |
				VIDCF_FASTTEMPORALD |
				VIDCF_COMPRESSFRAMES;

			icinfo->dwVersion = 0;
#if !defined(ICVERSION)
#define ICVERSION       0x0104
#endif
			icinfo->dwVersionICM = ICVERSION;
			
			wcscpy(icinfo->szName, XVID_NAME_L); 
			wcscpy(icinfo->szDescription, XVID_DESC_L);
						
			return lParam2; /* size of struct */
		}

		return 0;	/* error */
		
		/* state control */

	case ICM_ABOUT :
		DPRINTF("ICM_ABOUT");
		DialogBoxParam(g_hInst, MAKEINTRESOURCE(IDD_ABOUT), (HWND)lParam1, about_proc, 0);
		return ICERR_OK;

	case ICM_CONFIGURE :
		DPRINTF("ICM_CONFIGURE");
		if (lParam1 != -1)
		{
			CONFIG temp;

			codec->config.save = FALSE;
			memcpy(&temp, &codec->config, sizeof(CONFIG));

			DialogBoxParam(g_hInst, MAKEINTRESOURCE(IDD_MAIN), (HWND)lParam1, main_proc, (LPARAM)&temp);

			if (temp.save)
			{
				memcpy(&codec->config, &temp, sizeof(CONFIG));
				config_reg_set(&codec->config);
			}
		}
		return ICERR_OK;
			
	case ICM_GETSTATE :
		DPRINTF("ICM_GETSTATE");
		if ((void*)lParam1 == NULL)
		{
			return sizeof(CONFIG);
		}
		memcpy((void*)lParam1, &codec->config, sizeof(CONFIG));
		return ICERR_OK;

	case ICM_SETSTATE :
		DPRINTF("ICM_SETSTATE");
		if ((void*)lParam1 == NULL)
		{
			DPRINTF("ICM_SETSTATE : DEFAULT");
			config_reg_get(&codec->config);
			return 0;
		}
		memcpy(&codec->config,(void*)lParam1, sizeof(CONFIG));
		return 0; /* sizeof(CONFIG); */

	/* not sure the difference, private/public data? */

	case ICM_GET :
	case ICM_SET :
		return ICERR_OK;


	/* older-stype config */

	case ICM_GETDEFAULTQUALITY :
	case ICM_GETQUALITY :
	case ICM_SETQUALITY :
	case ICM_GETBUFFERSWANTED :
	case ICM_GETDEFAULTKEYFRAMERATE :
		return ICERR_UNSUPPORTED;


	/* compressor */

	case ICM_COMPRESS_QUERY :
		DPRINTF("ICM_COMPRESS_QUERY");
		return compress_query(codec, (BITMAPINFO *)lParam1, (BITMAPINFO *)lParam2);

	case ICM_COMPRESS_GET_FORMAT :
		DPRINTF("ICM_COMPRESS_GET_FORMAT");
		return compress_get_format(codec, (BITMAPINFO *)lParam1, (BITMAPINFO *)lParam2);

	case ICM_COMPRESS_GET_SIZE :
		DPRINTF("ICM_COMPRESS_GET_SIZE");
		return compress_get_size(codec, (BITMAPINFO *)lParam1, (BITMAPINFO *)lParam2);

	case ICM_COMPRESS_FRAMES_INFO :
		DPRINTF("ICM_COMPRESS_FRAMES_INFO");
		return compress_frames_info(codec, (ICCOMPRESSFRAMES *)lParam1);

	case ICM_COMPRESS_BEGIN :
		DPRINTF("ICM_COMPRESS_BEGIN");
		return compress_begin(codec, (BITMAPINFO *)lParam1, (BITMAPINFO *)lParam2);

	case ICM_COMPRESS_END :
		DPRINTF("ICM_COMPRESS_END");
		return compress_end(codec);

	case ICM_COMPRESS :
		DPRINTF("ICM_COMPRESS");
		return compress(codec, (ICCOMPRESS *)lParam1);

	/* decompressor */
	
	case ICM_DECOMPRESS_QUERY :
		DPRINTF("ICM_DECOMPRESS_QUERY");
		return decompress_query(codec, (BITMAPINFO *)lParam1, (BITMAPINFO *)lParam2);

	case ICM_DECOMPRESS_GET_FORMAT :
		DPRINTF("ICM_DECOMPRESS_GET_FORMAT");
		return decompress_get_format(codec, (BITMAPINFO *)lParam1, (BITMAPINFO *)lParam2);
	
	case ICM_DECOMPRESS_BEGIN :
		DPRINTF("ICM_DECOMPRESS_BEGIN");
		return decompress_begin(codec, (BITMAPINFO *)lParam1, (BITMAPINFO *)lParam2);

	case ICM_DECOMPRESS_END :
		DPRINTF("ICM_DECOMPRESS_END");
		return decompress_end(codec);

	case ICM_DECOMPRESS :
		DPRINTF("ICM_DECOMPRESS");
		return decompress(codec, (ICDECOMPRESS *)lParam1);

	case ICM_DECOMPRESS_GET_PALETTE :
	case ICM_DECOMPRESS_SET_PALETTE :
	case ICM_DECOMPRESSEX_QUERY:
	case ICM_DECOMPRESSEX_BEGIN:
	case ICM_DECOMPRESSEX_END:
	case ICM_DECOMPRESSEX:
		return ICERR_UNSUPPORTED;

    /* VFWEXT entry point */
    case ICM_USER+0x0fff :
        if (lParam1 == VFWEXT_CONFIGURE_INFO) {
            VFWEXT_CONFIGURE_INFO_T * info = (VFWEXT_CONFIGURE_INFO_T*)lParam2;
            DPRINTF("%i %i %i %i %i %i",
                info->ciWidth, info->ciHeight,
                info->ciRate, info->ciScale,
                info->ciActiveFrame, info->ciFrameCount);

            codec->config.ci_valid = 1;
            memcpy(&codec->config.ci, (void*)lParam2, sizeof(VFWEXT_CONFIGURE_INFO_T));
            return ICERR_OK;
        }
        return ICERR_UNSUPPORTED;

	default:
		if (uMsg < DRV_USER)
			return DefDriverProc(dwDriverId, hDriver, uMsg, lParam1, lParam2);
		else 
			return ICERR_UNSUPPORTED;
	}
}

void WINAPI Configure(HWND hwnd, HINSTANCE hinst, LPTSTR lpCmdLine, int nCmdShow)
{
	LRESULT dwDriverId;

	dwDriverId = (LRESULT) DriverProc(0, 0, DRV_OPEN, 0, 0);
	if (dwDriverId != (LRESULT)NULL)
	{
		if (lstrcmpi(lpCmdLine, "about")==0) {
			DriverProc(dwDriverId, 0, ICM_ABOUT, (LPARAM)GetDesktopWindow(), 0);
		}else{
			DriverProc(dwDriverId, 0, ICM_CONFIGURE, (LPARAM)GetDesktopWindow(), 0);
		}
		DriverProc(dwDriverId, 0, DRV_CLOSE, 0, 0);
	}
}

static int clean_dll_bindings(CODEC* codec)
{
	if(codec->m_hdll)
	{
		FreeLibrary(codec->m_hdll);
		codec->m_hdll = NULL;
		codec->xvid_global_func = NULL;
		codec->xvid_encore_func = NULL;
		codec->xvid_decore_func = NULL;
		codec->xvid_plugin_single_func = NULL;
		codec->xvid_plugin_2pass1_func = NULL;
		codec->xvid_plugin_2pass2_func = NULL;
		codec->xvid_plugin_lumimasking_func = NULL;
		codec->xvid_plugin_psnr_func = NULL;
	}
	return 0;
}
