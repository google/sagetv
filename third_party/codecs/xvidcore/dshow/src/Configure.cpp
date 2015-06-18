/*****************************************************************************
 *
 *  XVID MPEG-4 VIDEO CODEC
 *  - Configure from command line  -
 *
 *  Copyright(C) 2002-2010 Peter Ross <pross@xvid.org>
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
 * $Id: Configure.cpp 2006 2011-05-19 12:48:59Z Isibaar $
 *
 ****************************************************************************/


#include <windows.h>
#include <commctrl.h>
#include "config.h"
#include "resource.h"


HINSTANCE g_xvid_hInst;


INT_PTR adv_dialog(HWND hwndOwner)
{
	PROPSHEETPAGE psp [1];
	PROPSHEETHEADER psh;

	psp[0].dwSize = sizeof (PROPSHEETPAGE);
	psp[0].dwFlags = PSP_USETITLE;
	psp[0].hInstance = g_xvid_hInst;
	psp[0].pszTemplate = MAKEINTRESOURCE (IDD_ABOUT);
	psp[0].pszIcon = NULL;
	psp[0].pfnDlgProc = adv_proc;
	psp[0].pszTitle = "About";
	psp[0].lParam = 0;

	psh.dwSize = sizeof (PROPSHEETHEADER);
	psh.dwFlags = PSH_PROPSHEETPAGE;
	psh.hwndParent = hwndOwner;
	psh.hInstance = g_xvid_hInst;
	psh.pszIcon = NULL;
	psh.pszCaption = (LPSTR)"Xvid Configuration";
	psh.nPages = sizeof (psp) / sizeof (PROPSHEETPAGE);
	psh.ppsp = psp;

	return PropertySheet (&psh);
}


extern "C" void CALLBACK Configure(HWND hWndParent, HINSTANCE hInstParent, LPSTR lpCmdLine, int nCmdShow );

void CALLBACK Configure(HWND hWndParent, HINSTANCE hInstParent, LPSTR lpCmdLine, int nCmdShow ) 
{
	InitCommonControls();
	LoadRegistryInfo();
	adv_dialog( GetDesktopWindow() );
}


/* strmbase.lib\dllentry.obj:DllEntryPoint@12 */
extern "C" BOOL WINAPI DllEntryPoint(HINSTANCE, ULONG, LPVOID);


extern "C" BOOL WINAPI DllMain(HINSTANCE hInst, DWORD fdwReason, LPVOID lpvReserved);

BOOL WINAPI DllMain(HINSTANCE hInst, DWORD fdwReason, LPVOID lpvReserved)
{
	g_xvid_hInst = hInst;

	/* Call directshow DllEntryPoint@12 */
    return DllEntryPoint(hInst, fdwReason, lpvReserved);
}

