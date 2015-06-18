/*****************************************************************************
 *
 *  XVID MPEG-4 VIDEO CODEC
 *  - Configuration processing header file -
 *
 *  Copyright(C) 2002-2012 Peter Ross <pross@xvid.org>
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
 * $Id: config.h 2059 2012-02-22 19:00:26Z Isibaar $
 *
 ****************************************************************************/

#ifndef _DSHOW_CONFIG_H_
#define _DSHOW_CONFIG_H_

#ifdef __cplusplus
extern "C" {
#endif

/* registry stuff */
#define XVID_REG_KEY	HKEY_CURRENT_USER
#define XVID_REG_SUBKEY	"Software\\GNU\\XviD"
#define XVID_REG_CLASS	"config"

#define REG_GET_N(X, Y, Z) size=sizeof(int);if(RegQueryValueEx(hKey, X, 0, 0, (LPBYTE)&Y, &size) != ERROR_SUCCESS) {Y=Z;}
#define REG_GET_S(X, Y, Z) size=MAX_PATH;if(RegQueryValueEx(hKey, X, 0, 0, Y, &size) != ERROR_SUCCESS) {lstrcpy(Y, Z);}
#define REG_SET_N(X, Y) RegSetValueEx(hKey, X, 0, REG_DWORD, (LPBYTE)&Y, sizeof(int))
#define REG_SET_S(X, Y) RegSetValueEx(hKey, X, 0, REG_SZ, Y, lstrlen(Y)+1)


/* config struct */
#define SUPPORT_3IVX		(1<<0)
#define SUPPORT_DIVX		(1<<1)
#define SUPPORT_MP4V		(1<<2)

#define FORCE_NONE  0
#define FORCE_YV12  1
#define FORCE_YUY2  2
#define FORCE_RGB24 3
#define FORCE_RGB32 4

typedef struct 
{
	int nBrightness;
	int nDeblock_Y;
	int nDeblock_UV;
	int nDering_Y;
	int nDering_UV;
	int nFilmEffect;
	int nFlipVideo;
	int nForceColorspace;
	unsigned int supported_4cc;
	int videoinfo_compat;
	int aspect_ratio;
	int num_threads;
	DWORD cpu;
	int bTrayIcon;
} CONFIG;


/* global */
extern CONFIG g_config;


/* functions */
void LoadRegistryInfo();
void SaveRegistryInfo();
INT_PTR CALLBACK adv_proc(HWND hDlg, UINT uMsg, WPARAM wParam, LPARAM lParam);

#define XVID_DLL_NAME "xvidcore.dll"

#ifdef __cplusplus
}
#endif


#endif

