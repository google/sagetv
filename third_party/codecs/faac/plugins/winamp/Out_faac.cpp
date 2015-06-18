/*
FAAC - codec plugin for Cooledit
Copyright (C) 2002-2004 Antonio Foranna

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation.
	
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
		
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
			
The author can be contacted at:
ntnfrn_email-temp@yahoo.it
*/

#include <windows.h>
#include "resource.h"
#include "out.h"
#include "wa_ipc.h"
#include "defines.h"
#include "EncDialog.h"
#include "Cfaac.h"


// *********************************************************************************************


void Config(HWND);
void About(HWND);
void Init();
void Quit();
int Open(int, int, int, int, int);
void Close();
int Write(char*, int);
int CanWrite();
int IsPlaying();
int Pause(int);
void SetVolume(int);
void SetPan(int);
void Flush(int);
int GetOutputTime();
int GetWrittenTime();



Cfaac			*Cpcmaac;
//char			OutDir[MAX_PATH]="";

HINSTANCE		hInstance=NULL;
HBITMAP			hBmBrowse=NULL;
char			config_AACoutdir[MAX_PATH]="";

static int		srate, numchan, bps;
volatile int	writtentime, w_offset;
static int		last_pause=0;


Out_Module out = {
	OUT_VER,
	APP_NAME " " APP_VER,
	NULL,
    NULL, // hmainwindow
    NULL, // hdllinstance
    Config,
    About,
    Init,
    Quit,
    Open,
    Close,
    Write,
    CanWrite,
    IsPlaying,
    Pause,
    SetVolume,
    SetPan,
    Flush,
    GetOutputTime,
    GetWrittenTime
};



// *********************************************************************************************



Out_Module *winampGetOutModule()
{
	return &out;
}
// *********************************************************************************************

BOOL WINAPI DllMain (HINSTANCE hInst, DWORD ulReason, LPVOID lpReserved)
{
	switch(ulReason)
	{
	case DLL_PROCESS_ATTACH:
		hInstance=hInst;
		DisableThreadLibraryCalls((struct HINSTANCE__ *)hInst);
		if(!hBmBrowse)
			hBmBrowse=(HBITMAP)LoadImage(hInst,MAKEINTRESOURCE(IDB_BROWSE),IMAGE_BITMAP,0,0,/*LR_CREATEDIBSECTION|*/LR_LOADTRANSPARENT|LR_LOADMAP3DCOLORS);
		
		/*	Code from LibMain inserted here.  Return TRUE to keep the
			DLL loaded or return FALSE to fail loading the DLL.

			You may have to modify the code in your original LibMain to
			account for the fact that it may be called more than once.
			You will get one DLL_PROCESS_ATTACH for each process that
			loads the DLL. This is different from LibMain which gets
			called only once when the DLL is loaded. The only time this
			is critical is when you are using shared data sections.
			If you are using shared data sections for statically
			allocated data, you will need to be careful to initialize it
			only once. Check your code carefully.

			Certain one-time initializations may now need to be done for
			each process that attaches. You may also not need code from
			your original LibMain because the operating system may now
			be doing it for you.
		*/
		break;
		
	case DLL_THREAD_ATTACH:
		/*	Called each time a thread is created in a process that has
			already loaded (attached to) this DLL. Does not get called
			for each thread that exists in the process before it loaded
			the DLL.
	
			Do thread-specific initialization here.
		*/
		break;
		
	case DLL_THREAD_DETACH:
		/*	Same as above, but called when a thread in the process
			exits.
		
			Do thread-specific cleanup here.
		*/
		break;
		
	case DLL_PROCESS_DETACH:
		hInstance=NULL;
		if(hBmBrowse)
		{
            DeleteObject(hBmBrowse);
            hBmBrowse=NULL;
		}
		/*	Code from _WEP inserted here.  This code may (like the
			LibMain) not be necessary.  Check to make certain that the
			operating system is not doing it for you.
		*/
		break;
	}
	
	/*	The return value is only used for DLL_PROCESS_ATTACH; all other
		conditions are ignored.
	*/
	return TRUE;   // successful DLL_PROCESS_ATTACH
}

// *********************************************************************************************

void About(HWND hWndDlg)
{
	DialogBox(out.hDllInstance, MAKEINTRESOURCE(IDD_ABOUT), hWndDlg, (DLGPROC)DialogMsgProcAbout);

/*char buf[256];
  unsigned long samplesInput, maxBytesOutput;
  faacEncHandle hEncoder =
    faacEncOpen(44100, 2, &samplesInput, &maxBytesOutput);
  faacEncConfigurationPtr myFormat =
    faacEncGetCurrentConfiguration(hEncoder);

	sprintf(buf,
			APP_NAME " %s by Antonio Foranna\n\n"
			"This plugin uses FAAC encoder engine v%s\n\n"
			"Compiled on %s\n",
			 APP_VER,
			 myFormat->name,
			 __DATE__
			 );
	faacEncClose(hEncoder);
	MessageBox(hWndDlg, buf, "About", MB_OK);*/
}
// *********************************************************************************************

void Config(HWND hWnd)
{
	DialogBox(out.hDllInstance, MAKEINTRESOURCE(IDD_ENCODER), hWnd, DIALOGMsgProcEnc);
//	dwOptions=DialogBoxParam((HINSTANCE)out.hDllInstance,(LPCSTR)MAKEINTRESOURCE(IDD_ENCODER), (HWND)hWnd, (DLGPROC)DIALOGMsgProc, dwOptions);
}

// *********************************************************************************************
//									Utilities
// *********************************************************************************************

char *getSourceName(HWND hwnd)
{
HANDLE hProcess;
DWORD processid;
char filename[MAX_PATH], *pname;
SIZE_T bread;
//HWND hdlgPE;
	
	memset(filename, 0, MAX_PATH);
	GetWindowThreadProcessId(hwnd, &processid);
	hProcess = OpenProcess(PROCESS_VM_READ, FALSE, processid);
//	hdlgPE=SendMessage(hwnd,WM_WA_IPC,IPC_GETWND_PE,IPC_GETWND);
	pname=(char*)SendMessage(hwnd,WM_WA_IPC,SendMessage(hwnd,WM_WA_IPC,0,IPC_GETLISTPOS),IPC_GETPLAYLISTFILE);
	ReadProcessMemory(hProcess, pname, filename, MAX_PATH, &bread);
	CloseHandle(hProcess);
	return strdup(filename);
}

char *getWASourceName(char *src)
{
char	*dst=NULL, *tmp=src;
int		l;
	if(!src)
	{
		if(dst=(char *)malloc(1))
			*dst='\0';
		return dst;
	}

	while(*src && *src>='0' && *src<='9')
		src++;
	if(src[0]=='.' && src[1]==' ')
		src+=2;
	else
		src=tmp;
	if(!(dst=(char *)malloc(strlen(src)+1)))
		return dst;
	strcpy(dst,src);
	l=strlen(src);
	if(l>9 && !strcmpi(src+l-9," - Winamp"))
		dst[l-9]='\0';
	// cut ext
	tmp=dst+strlen(dst);
	while(tmp!=dst && *tmp!='.')
		tmp--;
	if(*tmp=='.')
		*tmp='\0';
	return dst;
}

static char *scanstr_back(char *str, char *toscan, char *defval)
{
char *s=str+strlen(str)-1;

	if (strlen(str) < 1) return defval;
	if (strlen(toscan) < 1) return defval;
	while (1)
	{
		char *t=toscan;
		while (*t)
			if (*t++ == *s) return s;
		t=CharPrev(str,s);
		if (t==s) return defval;
		s=t;
	}
}
//------------------------------------------------------------------------------------------

void GetNewFileName(char *lpstrFilename)
{
char temp2[MAX_PATH];
char *t,*p;

	GetWindowText(out.hMainWindow,temp2,sizeof(temp2));
	t=temp2;
	
	t=scanstr_back(temp2,"-",NULL);
	if (t) t[-1]=0;
	
	if (temp2[0] && temp2[1] == '.')
	{
		char *p1,*p2;
		p1=lpstrFilename;
		p2=temp2;
		while (*p2) *p1++=*p2++;
		*p1=0;
		p1 = temp2+1;
		p2 = lpstrFilename;
		while (*p2)
		{
			*p1++ = *p2++;
		}
		*p1=0;
		temp2[0] = '0';
	}
	p=temp2;
	while (*p != '.' && *p) p++;
	if (*p == '.') 
	{
		*p = '-';
		p=CharNext(p);
	}
	while (*p)
	{
		if (*p == '.' || *p == '/' || *p == '\\' || *p == '*' || 
			*p == '?' || *p == ':' || *p == '+' || *p == '\"' || 
			*p == '\'' || *p == '|' || *p == '<' || *p == '>') *p = '_';
		p=CharNext(p);
	}
	
	p=config_AACoutdir;
	if (p[0]) while (p[1]) p++;
	
	if (!config_AACoutdir[0] || config_AACoutdir[0] == ' ')
		Config(out.hMainWindow);
	if (!config_AACoutdir[0])
		wsprintf(lpstrFilename,"%s.aac",temp2);
	else if (p[0]=='\\')
		wsprintf(lpstrFilename,"%s%s.aac",config_AACoutdir,temp2);
	else
		wsprintf(lpstrFilename,"%s\\%s.aac",config_AACoutdir,temp2);
}

// *********************************************************************************************
//									Main functions
// *********************************************************************************************

void Init()
{
}
// *********************************************************************************************

void Quit()
{
}
// *********************************************************************************************

#define ERROR_O(msg) \
{ \
	if(msg) \
		MessageBox(0, msg, "FAAC plugin", MB_OK); \
	Close(); \
	return -1; \
}

int Open(int lSamprate, int wChannels, int wBitsPerSample, int bufferlenms, int prebufferms)
{
CMyEncCfg	cfg;
char		OutFilename[MAX_PATH],
			*srcFilename=NULL;
//			buf[MAX_PATH],
//			*tsrcFilename;

	w_offset = writtentime = 0;
	numchan = wChannels;
	srate = lSamprate;
	bps = wBitsPerSample;

	strcpy(config_AACoutdir,cfg.OutDir);
	GetNewFileName(OutFilename);

	Cpcmaac=new Cfaac();
#ifdef USE_IMPORT_TAG
/*	GetWindowText(out.hMainWindow,buf,sizeof(buf));
	tsrcFilename=getWASourceName(buf);
	srcFilename=Cpcmaac->getSourceFilename(cfg.TagSrcPath,tsrcFilename,cfg.TagSrcExt);
	FREE_ARRAY(tsrcFilename);*/
	srcFilename=getSourceName(out.hMainWindow);
#endif
	if(!Cpcmaac->Init(srcFilename,OutFilename,lSamprate,wBitsPerSample,wChannels,-1))
		ERROR_O(0);

	FREE_ARRAY(srcFilename);
	return 0;
}
// *********************************************************************************************

void Close()
{
	if(Cpcmaac)
	{
		delete Cpcmaac;
		Cpcmaac=NULL;
	}
}
// *********************************************************************************************

int Write(char *wabuf, int len)
{
	writtentime+=len;

	if(Cpcmaac->processDataBufferized(Cpcmaac->hOutput,(BYTE *)wabuf,len)<0)
		return -1;

//	Sleep(10);
	return 0;
}
// *********************************************************************************************

int CanWrite()
{
	return last_pause ? 0 : 16*1024*1024;
//	return last_pause ? 0 : mo->samplesInput*(mo->wBitsPerSample>>3);
}
// *********************************************************************************************

int IsPlaying()
{
	return 0;
}
// *********************************************************************************************

int Pause(int pause)
{
	int t=last_pause;
	last_pause=pause;
	return t;
}
// *********************************************************************************************

void SetVolume(int volume)
{
}
// *********************************************************************************************

void SetPan(int pan)
{
}
// *********************************************************************************************

void Flush(int t)
{
int a;

	  w_offset=0;
	  a = t - GetWrittenTime();
	  w_offset=a;
}
// *********************************************************************************************

int GetOutputTime()
{
int t=srate*numchan,
	ms=writtentime,
	l;

	if(t)
	{
		l=ms%t;
		ms /= t;
		ms *= 1000;
		ms += (l*1000)/t;
		if (bps == 16) ms/=2;
	}
	return ms + w_offset;
}
// *********************************************************************************************
	
int GetWrittenTime()
{
int t=srate*numchan,
	ms=writtentime,
	l;

	if(t)
	{
		l=ms%t;
		ms /= t;
		ms *= 1000;
		ms += (l*1000)/t;
		if (bps == 16) ms/=2;
	}
	return ms + w_offset;
}
