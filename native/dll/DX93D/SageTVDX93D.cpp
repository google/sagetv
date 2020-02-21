/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "stdafx.h"
#include "SageTVDX93D.h"
#include "../../include/sage_DirectX9SageRenderer.h"
#include <assert.h>
#include <dxva2api.h>

#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     sage_miniclient_DirectX9GFXCMD
 * Method:    registerMiniClientNatives0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_miniclient_DirectX9GFXCMD_registerMiniClientNatives0
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif


//#include <vmr9.h>

#define TEST_AND_BAIL if (FAILED(hr)){elog((env, "DX9Renderer NATIVE FAILURE line %d hr=0x%x\r\n", __LINE__, hr)); ReleaseSharedD3DDevice(env, jo, pD3DDevice); return JNI_FALSE;}
#define TEST_AND_BAIL_NODEV if (FAILED(hr)){elog((env, "DX9Renderer NATIVE FAILURE line %d hr=0x%x\r\n", __LINE__, hr)); return JNI_FALSE;}
#define TEST_AND_PRINT if (FAILED(hr)){slog((env, "DX9Renderer NATIVE WARNING (non-FAILURE) line %d hr=0x%x\r\n", __LINE__, hr));}

#define SRC_BLEND_ALPHA D3DBLEND_ONE
#define DST_BLEND_ALPHA D3DBLEND_INVSRCALPHA

//#ifdef _DEBUG
//#define dx9log(_x_) sysOutPrint _x_
//#else
#define dx9log(_x_) 0
//#endif

#define D3DFVF_CUSTOMALPHATEXTUREVERTEX ( D3DFVF_XYZ | D3DFVF_DIFFUSE | D3DFVF_TEX1 )
#define D3DFVF_CUSTOMALPHATEXTURE2VERTEX ( D3DFVF_XYZ | D3DFVF_DIFFUSE | D3DFVF_TEX1 | D3DFVF_TEX2 )
#define D3DFVF_CUSTOMTEXTUREVERTEX ( D3DFVF_XYZ | D3DFVF_TEX1 )
#define D3DFVF_CUSTOMSHAPEVERTEX ( D3DFVF_XYZ | D3DFVF_DIFFUSE )
#define D3DFVF_CUSTOMYUVVERTEX ( D3DFVF_XYZ | D3DFVF_TEX1 | D3DFVF_TEX2 | D3DFVF_TEX3 )
D3DMATRIX IDENTITY_MATRIX = {
	1.0f, 0.0f, 0.0f, 0.0f,
	0.0f, 1.0f, 0.0f, 0.0f,
	0.0f, 0.0f, 1.0f,  0.0f,
	0.0f, 0.0f, 0.0f, 1.0f
};

// This should be safer to use managed memory instead of the default memory pool.
// It allows DX to do memory defragmentation as well. Sure, we don't need the benefit of
// what happens when the device resets, but oh well.
// NARFLEX: I changed this back to false on 11/7/2006 because it uses to much virtual
// memory to store the backing textures in managed mode
static bool useManagedMemory = false;
static bool g_supportsLinearMagMin = false;
static bool requiresPow2Textures = true;
// NOTE: People were running into issues with 16k vertices so we've octupled that
static const int NUM_VERTICES = 16384*8;
static const int NUM_POLYGON_VERTICES = 16384*4*8;
struct CUSTOMALPHATEXTUREVERTEX
{
    struct Position {
        Position() : 
            x(0.0f),y(0.0f),z(0.0f) {            
        };
        Position(float x_, float y_, float z_) :
            x(x_),y(y_),z(z_) {
        };
        float x,y,z;
    };

    Position    position; // The position
    D3DCOLOR    color;    // The color
    FLOAT       tu, tv;   // The texture coordinates
};
struct CUSTOMALPHATEXTURE2VERTEX
{
    struct Position {
        Position() : 
            x(0.0f),y(0.0f),z(0.0f) {            
        };
        Position(float x_, float y_, float z_) :
            x(x_),y(y_),z(z_) {
        };
        float x,y,z;
    };

    Position    position; // The position
    D3DCOLOR    color;    // The color
    FLOAT       tu1, tv1;   // The texture coordinates
    FLOAT       tu2, tv2;   // The diffused texture coordinates
};
struct CUSTOMTEXTUREVERTEX
{
    struct Position {
        Position() : 
            x(0.0f),y(0.0f),z(0.0f) {            
        };
        Position(float x_, float y_, float z_) :
            x(x_),y(y_),z(z_) {
        };
        float x,y,z;
    };

    Position    position; // The position
    FLOAT       tu, tv;   // The texture coordinates
};
struct CUSTOMSHAPEVERTEX
{
    struct Position {
        Position() : 
            x(0.0f),y(0.0f),z(0.0f) {            
        };
        Position(float x_, float y_, float z_) :
            x(x_),y(y_),z(z_) {
        };
        float x,y,z;
    };

    Position    position; // The position
    D3DCOLOR    color;    // The color
};
struct CUSTOMYUVVERTEX
{
    struct Position {
        Position() : 
            x(0.0f),y(0.0f),z(0.0f) {            
        };
        Position(float x_, float y_, float z_) :
            x(x_),y(y_),z(z_) {
        };
        float x,y,z;
    };

    Position    position; // The position
    FLOAT       tu1, tv1;   // The texture coordinates
    FLOAT       tu2, tv2;   // The texture coordinates
    FLOAT       tu3, tv3;   // The texture coordinates
};

static CUSTOMALPHATEXTUREVERTEX* image_alpha_vertices = NULL;
static CUSTOMALPHATEXTURE2VERTEX* image_alpha2_vertices = NULL;
static CUSTOMTEXTUREVERTEX* image_vertices = NULL;
static CUSTOMSHAPEVERTEX* polygon_vertices = NULL;
static CUSTOMYUVVERTEX* yuv_vertices = NULL;
static int imageAlphaVertexPos = 0;
static int imageAlpha2VertexPos = 0;
static int polygonVertexPos = 0;
static IDirect3DVertexBuffer9* pVertexImageBuffer = NULL;
static IDirect3DVertexBuffer9* pVertexAlphaImageBuffer = NULL;
static IDirect3DVertexBuffer9* pVertexAlpha2ImageBuffer = NULL;
static IDirect3DVertexBuffer9* pVertexYUVBuffer = NULL;
static IDirect3DVertexBuffer9* pPolygonBuffer = NULL;
static IDirect3DSurface9* pSnapshotSurf = NULL;

static IDirect3DTexture9* pVideoTexture = NULL;
static IDirect3DTexture9* pVideoTextureY = NULL;
static IDirect3DTexture9* pVideoTextureU = NULL;
static IDirect3DTexture9* pVideoTextureV = NULL;
static int gVideoWidth,gVideoHeight;

static ID3DXSprite* gtextSprite = NULL;

#define JNI_TRY try{
#define JNI_RT_CATCH_RET(x) }catch (...){env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Native Exception"); ReleaseSharedD3DDevice(env, jo, pD3DDevice); return (x);}
#define JNI_RT_CATCH_RET_NODEV(x) }catch (...){env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Native Exception"); return (x);}
#define JNI_RT_CATCH }catch (...){env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Native Exception"); ReleaseSharedD3DDevice(env, jo, pD3DDevice); return;}
#define JNI_RT_CATCH_NODEV }catch (...){env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Native Exception"); return;}

static jfieldID fid_pD3DObject;
static jfieldID fid_pD3DDevice;
static jfieldID fid_pD3DDevMgr;
static jfieldID fid_pD3DDevMgrToken;
static jfieldID fid_hD3DMgrHandle;
static jfieldID fid_rerenderedDL;
static jfieldID fid_rectx;
static jfieldID fid_recty;
static jfieldID fid_rectw;
static jfieldID fid_recth;

static bool scissoredLast = false;
static jlong textSpriteActive = 0;
static bool useFlip = false;
// This enables the use of full screen exclusive mode for testing.
static bool fullScreenEx = false;
static D3DPRESENT_PARAMETERS g_d3dpp;
static HWND g_fsWinID;
static HWND g_myWinID;
static jint g_builtWidth;
static jint g_builtHeight;
static bool copyVideoTexture = true;
static int lastScaleHint = -1;
static int lastBlendMode = -1;
static IDirect3DTexture9* lastTexturePtr = NULL;
static IDirect3DTexture9* lastDiffuseTexturePtr = NULL;
static IDirect3DVertexBuffer9* lastVertexBuffer = NULL;
static IDirect3DPixelShader9* yuvRgbPixelShader = NULL;
static DWORD lastViewportWidth = 0;
static DWORD lastViewportHeight = 0;

HRESULT SetRenderState(IDirect3DDevice9* pD3DDevice, D3DRENDERSTATETYPE state, DWORD val)
{
	DWORD currVal = 0;
	HRESULT hr = pD3DDevice->GetRenderState(state, &currVal);
	if (hr != D3D_OK || currVal != val)
	{
		hr = pD3DDevice->SetRenderState(state, val);
	}
	return hr;
}

HRESULT SetSamplerState(IDirect3DDevice9* pD3DDevice, DWORD sampler, D3DSAMPLERSTATETYPE state, DWORD val)
{
	DWORD currVal = 0;
	HRESULT hr = pD3DDevice->GetSamplerState(sampler, state, &currVal);
	if (hr != D3D_OK || currVal != val)
	{
		hr = pD3DDevice->SetSamplerState(sampler, state, val);
	}
	return hr;
}

HRESULT SetTextureStageState(IDirect3DDevice9* pD3DDevice, DWORD stage, D3DTEXTURESTAGESTATETYPE state, DWORD val)
{
	DWORD currVal = 0;
	HRESULT hr = pD3DDevice->GetTextureStageState(stage, state, &currVal);
	if (hr != D3D_OK || currVal != val)
	{
		hr = pD3DDevice->SetTextureStageState(stage, state, val);
	}
	return hr;
}

IDirect3DDevice9* GetSharedD3DDevice(JNIEnv* env, jobject jo)
{
	IDirect3DDeviceManager9* pD3DDevMgr = (IDirect3DDeviceManager9*) env->GetLongField(jo, fid_pD3DDevMgr);
	HANDLE hDevMgr = (HANDLE) env->GetLongField(jo, fid_hD3DMgrHandle);
	if (pD3DDevMgr && hDevMgr)
	{
		IDirect3DDevice9* pD3DDevice = NULL;
		HRESULT hr = pD3DDevMgr->LockDevice(hDevMgr, &pD3DDevice, TRUE);
		if (hr == DXVA2_E_NEW_VIDEO_DEVICE)
		{
			slog((env, "D3DDeviceManager indicates new video device; reload the handle for the shared device\r\n"));
			pD3DDevMgr->CloseDeviceHandle(hDevMgr);
			pD3DDevMgr->OpenDeviceHandle(&hDevMgr);
			hr = pD3DDevMgr->LockDevice(hDevMgr, &pD3DDevice, TRUE);
			if (FAILED(hr))
				return NULL;
		}
		return pD3DDevice;
	}
	else
	{
		// No D3DDeviceManager - just return the pointer we already have
		return (IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	}
	return NULL;
}

HRESULT ReleaseSharedD3DDevice(JNIEnv* env, jobject jo, IDirect3DDevice9* pD3DDevice)
{
	IDirect3DDeviceManager9* pD3DDevMgr = (IDirect3DDeviceManager9*) env->GetLongField(jo, fid_pD3DDevMgr);
	HANDLE hDevMgr = (HANDLE) env->GetLongField(jo, fid_hD3DMgrHandle);
	if (pD3DDevMgr && hDevMgr && pD3DDevice)
	{
		SAFE_RELEASE(pD3DDevice);
		pD3DDevMgr->UnlockDevice(hDevMgr, FALSE);
	}
	return S_OK;
}

static int asyncRenderCount = 0;
JNIEXPORT void JNICALL Java_sage_DirectX9SageRenderer_asyncVideoRender0
  (JNIEnv *env, jobject jo, jstring sharedMemPrefix)
{
	jclass m_dx9SageClass = env->GetObjectClass(jo);
	jmethodID m_dx9UpdateMethodID = env->GetStaticMethodID(m_dx9SageClass, "vmr9RenderNotify", "(IJJJJII)V");
	if (!sharedMemPrefix)
	{
		// This is a way of ensuring it was terminated
		asyncRenderCount++;
		return;
	}
	const char* cName = env->GetStringUTFChars(sharedMemPrefix, NULL);
	HANDLE fileMap = CreateFileMapping(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, 1920 * 540 * 3 + 1024, cName);
	if (fileMap == NULL)
		return;
	char buf[256];
	asyncRenderCount++;
	Sleep(100); // wait more than 50 msec so if a prior MPlayer process was killed forcibly the loop below would have
	// had time to exit and cleanup its pointers before we start on our work
	strcpy_s(buf, sizeof(buf), cName);
	strcat_s(buf, sizeof(buf), "FrameReady");
	HANDLE evtReady = CreateEvent(NULL, FALSE, FALSE, buf);
	strcpy_s(buf, sizeof(buf), cName);
	strcat_s(buf, sizeof(buf), "FrameDone");
	HANDLE evtDone = CreateEvent(NULL, FALSE, FALSE, buf);
	env->ReleaseStringUTFChars(sharedMemPrefix, cName);
	slog((env, "Created FileMap=0x%p evtReady=0x%p evtDone=0x%p\r\n", fileMap, evtReady, evtDone));
	unsigned char* myPtr = (unsigned char*)MapViewOfFile(fileMap, FILE_MAP_READ|FILE_MAP_WRITE, 0, 0, 0);
	unsigned int* myData = (unsigned int*) myPtr;
	if (myPtr == NULL || evtReady == NULL || evtDone == NULL)
	{
		if (myPtr != NULL)
			UnmapViewOfFile(myPtr);
		if (evtReady != NULL)
			CloseHandle(evtReady);
		if (evtDone != NULL)
			CloseHandle(evtDone);
		return;
	}
	slog((env, "Starting to read...0x%p\r\n", myPtr));
	int configured = 0;
	int j = 0;
	int myAsyncRenderCount = asyncRenderCount;
	while (myAsyncRenderCount == asyncRenderCount)
	{
		j++;
		// Decrease this wait time so that if there's case where MPlayer has to be forcibly quit we
		// can exit this loop in a short amount of time
		if (WAIT_OBJECT_0 != WaitForSingleObject(evtReady, 50))
		{
			if (env->GetLongField(jo, fid_pD3DObject) == 0)
				break; // 3D system is dead
			//printf("WAIT TIMEOUT EXPIRED!!!\r\n");
			continue;
		}
		int currCmd = (myData[0] >> 24) & 0xFF;
		//slog((env, "Got video cmd 0x%x\r\n", currCmd));
		if (currCmd == 0x80)
		{
			// Create Video
			gVideoWidth = myData[1];
			gVideoHeight = myData[2];
			// Respond with offset/stride information
			myData[0] = 1024; // offset y
			myData[1] = gVideoWidth; // pitch y
			myData[2] = 1024 + gVideoWidth*gVideoHeight; // offset u
			myData[3] = gVideoWidth/2; // pitch u
			myData[4] = 1024 + gVideoWidth*gVideoHeight*5/4; // offset y
			myData[5] = gVideoWidth/2; // pitch v
		}
		else if (currCmd == 0x81)
		{
			env->CallStaticVoidMethod(m_dx9SageClass, m_dx9UpdateMethodID,
				(jint) 0xCAFEBABE, (jlong) myPtr+1024,
				(jlong) 1,//texture,
				(jlong) j, (jlong) j+1,
				gVideoWidth, gVideoHeight);
		}
		else if (currCmd == 0x82)
		{
			slog((env, "Got the message to do a clean exit on async video render\r\n"));
			break;
		}
		else
		{
		}
		SetEvent(evtDone);
	}
	env->CallStaticVoidMethod(m_dx9SageClass, m_dx9UpdateMethodID,
		(jint) 0xCAFEBABE, 0, 0, 0, 0, 0, 0);
	slog((env, "Async video renderer is cleaning up...\r\n"));
	SAFE_RELEASE(pVideoTextureY);
	SAFE_RELEASE(pVideoTextureU);
	SAFE_RELEASE(pVideoTextureV);
	UnmapViewOfFile(myPtr);
	CloseHandle(evtReady);
	CloseHandle(evtDone);
	CloseHandle(fileMap);
	slog((env, "Async video renderer cleanup is complete.\r\n"));
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    initDX9SageRenderer0
 * Signature: (IIJ)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_initDX9SageRenderer0(JNIEnv *env, jobject jo,
																		   jint width, jint height, jlong winID)
{
	JNI_TRY;
	HRESULT hr = CoInitializeEx(NULL, COM_THREADING_MODE);
	if (FAILED(hr))
		return JNI_FALSE;
	slog((env, "Initializing DirectX9\r\n"));
	if (!fid_pD3DObject)
	{
		jclass dx9Class = env->GetObjectClass(jo);
		fid_pD3DObject = env->GetFieldID(dx9Class, "pD3DObject", "J");
		fid_pD3DDevice = env->GetFieldID(dx9Class, "pD3DDevice", "J");
		fid_rerenderedDL = env->GetFieldID(dx9Class, "rerenderedDL", "Z");
		fid_pD3DDevMgr = env->GetFieldID(dx9Class, "pD3DDevMgr", "J");
		fid_pD3DDevMgrToken = env->GetFieldID(dx9Class, "pD3DDevMgrToken", "J");
		fid_hD3DMgrHandle = env->GetFieldID(dx9Class, "hD3DMgrHandle", "J");

		jclass rectClass = env->FindClass("java/awt/Rectangle");
		fid_rectx = env->GetFieldID(rectClass, "x", "I");
		fid_recty = env->GetFieldID(rectClass, "y", "I");
		fid_rectw = env->GetFieldID(rectClass, "width", "I");
		fid_recth = env->GetFieldID(rectClass, "height", "I");
	}
	IDirect3D9* pD3D = (IDirect3D9*) env->GetLongField(jo, fid_pD3DObject);
	if (!pD3D)
		pD3D = Direct3DCreate9(D3D_SDK_VERSION);
	if (!pD3D)
		return JNI_FALSE;

	BOOL hwVertexProc = FALSE;
	DWORD holder;
	HKEY myKey;
	DWORD readType;
	DWORD hsize = sizeof(holder);
	if (RegCreateKeyEx(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\SageTV\\DirectX9", 0, 0,
		REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS, 0, &myKey, 0) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "PageFlipping", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			if (holder)
				useFlip = true;
		}
		else
		{
			holder = 0;
			RegSetValueEx(myKey, "PageFlipping", 0, REG_DWORD, (LPBYTE) &holder, sizeof(hsize));
		}
		if (RegQueryValueEx(myKey, "HWVertexProcessing", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			if (holder)
				hwVertexProc = TRUE;
		}
		else
		{
			holder = 0;
			RegSetValueEx(myKey, "HWVertexProcessing", 0, REG_DWORD, (LPBYTE) &holder, sizeof(hsize));
		}
		if (RegQueryValueEx(myKey, "CopyVideoTextures", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			if (holder)
				copyVideoTexture = true;
			else
				copyVideoTexture = false;
		}
		else
		{
			// This KILLS performance for HD w/VMR9 so we don't want to have this on by default
			holder = 0;
			RegSetValueEx(myKey, "CopyVideoTextures", 0, REG_DWORD, (LPBYTE) &holder, sizeof(hsize));
		}

		RegCloseKey(myKey);
	}

	D3DDISPLAYMODE d3ddm;
	hr = pD3D->GetAdapterDisplayMode(D3DADAPTER_DEFAULT, &d3ddm);
	TEST_AND_BAIL_NODEV
	env->SetLongField(jo, fid_pD3DObject, (jlong) pD3D);

	if (width < 0 || height < 0)
	{
		fullScreenEx = true;
		width = width > 0 ? width : -1*width;
		height = height > 0 ? height : -1*height;
	}
	else
		fullScreenEx = false;
	g_builtWidth = width;
	g_builtHeight = height;
	g_myWinID = (HWND)winID;
	ZeroMemory(&g_d3dpp, sizeof(g_d3dpp));
	g_d3dpp.Windowed         = TRUE;
	// We have to use D3DSWAPEFFECT_COPY or we're screwed for non-full screen rendering
	// because you can't specify the rectangle for present calls in the other modes. The only
	// other alternative is rendering at a fixed resolution which gets scaled to the screen resolution...
	// which might not be that bad of an idea.
	g_d3dpp.SwapEffect       = useFlip ? D3DSWAPEFFECT_FLIP : D3DSWAPEFFECT_COPY;//D3DSWAPEFFECT_DISCARD;//
	g_d3dpp.BackBufferFormat = d3ddm.Format;
	g_d3dpp.hDeviceWindow    = (HWND)winID;
	g_d3dpp.BackBufferWidth = width;
	g_d3dpp.BackBufferHeight = height;
	g_d3dpp.BackBufferCount = useFlip ? 2 : 1;
	// The debug runtime spits out:
	// Direct3D9: (WARN) :driver set D3DDEVCAPS_TEXTURENONLOCALVIDMEM w/o DDCAPS2_NONLOCALVIDMEM:turning off D3DDEVCAPS_TEXTURENONLOCALVIDMEM
	// FYI - disabling this flag does NOT fix it
	g_d3dpp.Flags = D3DPRESENTFLAG_VIDEO;
	// if this is at default then it uses the low-res system timer for vsync which sucks
	// BUT I'M PRETTY SURE IT CONSUMES A LOT OF CPU IN THE WAIT BECAUSE IT SPINS THE CPU FOR THE DELAY
	// ALBEIT ON A LOW PRIORITY I'M SURE
	hsize = sizeof(holder);
	g_d3dpp.PresentationInterval = D3DPRESENT_INTERVAL_ONE;
	if (RegCreateKeyEx(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\SageTV\\DirectX9", 0, 0,
		REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS, 0, &myKey, 0) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "UsePresentationInterval", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			g_d3dpp.PresentationInterval = holder;
		}
		else
		{
			holder = D3DPRESENT_INTERVAL_ONE;
			RegSetValueEx(myKey, "UsePresentationInterval", 0, REG_DWORD, (LPBYTE) &holder, sizeof(hsize));
		}
		RegCloseKey(myKey);
	}

	if (fullScreenEx)
	{
		g_d3dpp.Windowed = FALSE;
		g_d3dpp.BackBufferCount = 2;
		g_d3dpp.SwapEffect = D3DSWAPEFFECT_FLIP;
		g_d3dpp.FullScreen_RefreshRateInHz = d3ddm.RefreshRate;
		HWND tempParent = GetParent((HWND) winID);
		if (tempParent)
		{
			while (GetParent(tempParent))
			{
				tempParent = GetParent(tempParent);
			}
			winID = (jlong)tempParent;
		}
		g_fsWinID = (HWND) winID;
		g_d3dpp.hDeviceWindow = (HWND) winID;
		slog((env, "Using DX9 Full Screen Exclusive Mode\r\n"));
	}

	IDirect3DDeviceManager9* pD3DDevMgr = (IDirect3DDeviceManager9*) env->GetLongField(jo, fid_pD3DDevMgr);
	UINT resetToken = (UINT) env->GetLongField(jo, fid_pD3DDevMgrToken);
	HANDLE mgrHandle = (HANDLE) env->GetLongField(jo, fid_hD3DMgrHandle);
	if (!pD3DDevMgr)
	{
		slog((env, "Creating the D3D device manager\r\n"));
		HMODULE dxva2Mod = LoadLibrary("dxva2.dll");
		if (dxva2Mod)
		{
			typedef HRESULT (STDAPICALLTYPE *DXVA2CreateDirect3DDeviceManager9PROC)(UINT*, IDirect3DDeviceManager9**);
	
			DXVA2CreateDirect3DDeviceManager9PROC lpfnProc = (DXVA2CreateDirect3DDeviceManager9PROC)GetProcAddress(dxva2Mod, "DXVA2CreateDirect3DDeviceManager9");
			if (lpfnProc)
			{
				hr = lpfnProc(&resetToken, &pD3DDevMgr);
				TEST_AND_PRINT
				env->SetLongField(jo, fid_pD3DDevMgr, (jlong) pD3DDevMgr);
				env->SetLongField(jo, fid_pD3DDevMgrToken, (jlong) resetToken);
			}
		}
		if (!pD3DDevMgr)
		{
			slog((env, "Unable to create the D3D device manager....library not found\r\n"));
		}
	}

	IDirect3DDevice9* pD3DDevice = (IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (pD3DDevice)
	{
		slog((env, "Resetting D3D Device\r\n"));
		hr = pD3DDevice->TestCooperativeLevel();
		TEST_AND_PRINT
		hr = pD3DDevice->Reset(&g_d3dpp);
		slog((env, "Done Resetting D3D Device\r\n"));
	}
	else
	{
		slog((env, "Creating D3D Device\r\n"));
		hr = pD3D->CreateDevice(
			D3DADAPTER_DEFAULT, // always the primary display adapter
			D3DDEVTYPE_HAL,
			(HWND) winID,
	//		D3DCREATE_SOFTWARE_VERTEXPROCESSING
			(hwVertexProc ? D3DCREATE_HARDWARE_VERTEXPROCESSING : D3DCREATE_SOFTWARE_VERTEXPROCESSING)
	//		| D3DCREATE_PUREDEVICE
	// Turning off the multithreaded flag kills performance when we playback video and we also get texture corruption
			| D3DCREATE_MULTITHREADED // I think this is why the UI doesn't render sometimes, it's always w/ video
			,
			&g_d3dpp,
			&pD3DDevice);
	}
	if (FAILED(hr))
	{
/*		if (pD3DDevice)
		{
			pD3DDevice->Release();
			env->SetLongField(jo, fid_pD3DDevice, 0);
		}*/
		TEST_AND_PRINT
//		pD3D->Release();
//		env->SetLongField(jo, fid_pD3DObject, 0);
		return JNI_FALSE;
	}
	env->SetLongField(jo, fid_pD3DDevice, (jlong) pD3DDevice);
	if (pD3DDevMgr)
	{
		hr = pD3DDevMgr->ResetDevice(pD3DDevice, resetToken);
		TEST_AND_PRINT
		if (mgrHandle)
			pD3DDevMgr->CloseDeviceHandle(mgrHandle);
		hr = pD3DDevMgr->OpenDeviceHandle(&mgrHandle);
		TEST_AND_PRINT
		env->SetLongField(jo, fid_hD3DMgrHandle, (jlong) mgrHandle);
	}

	D3DCAPS9 d3dcaps;
	pD3DDevice->GetDeviceCaps( &d3dcaps );
	if ((d3dcaps.TextureFilterCaps & (D3DPTFILTERCAPS_MAGFLINEAR | D3DPTFILTERCAPS_MINFLINEAR)) ==
		(D3DPTFILTERCAPS_MAGFLINEAR | D3DPTFILTERCAPS_MINFLINEAR))
	{
		g_supportsLinearMagMin = true;
	}
	else
	{
		slog((env, "DX9 Disabling support for linear texture filtering.\r\n"));
		g_supportsLinearMagMin = false;
	}
	requiresPow2Textures = ((d3dcaps.TextureCaps & D3DPTEXTURECAPS_POW2) && !(d3dcaps.TextureCaps & D3DPTEXTURECAPS_NONPOW2CONDITIONAL)) ? true : false;
	hsize = sizeof(holder);
	if (RegCreateKeyEx(HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\SageTV\\DirectX9", 0, 0,
		REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS, 0, &myKey, 0) == ERROR_SUCCESS)
	{
		if (RegQueryValueEx(myKey, "DisableLinearTextureFiltering", 0, &readType, (LPBYTE) &holder, &hsize) == ERROR_SUCCESS)
		{
			if (holder)
				g_supportsLinearMagMin = false;
		}
		else
		{
			holder = 0;
			RegSetValueEx(myKey, "DisableLinearTextureFiltering", 0, REG_DWORD, (LPBYTE) &holder, sizeof(hsize));
		}

		RegCloseKey(myKey);
	}

	hr = SetRenderState(pD3DDevice, D3DRS_CULLMODE, D3DCULL_NONE);
	TEST_AND_PRINT
	hr = SetRenderState(pD3DDevice, D3DRS_LIGHTING, FALSE);
	TEST_AND_PRINT
	hr = SetRenderState(pD3DDevice, D3DRS_ALPHABLENDENABLE, TRUE);
	TEST_AND_PRINT
	// The alpha is already in the pixel values so we don't want to set this, otherwise we'll
	// be applying the alpha twice
	hr = SetRenderState(pD3DDevice, D3DRS_SRCBLEND, SRC_BLEND_ALPHA);
	TEST_AND_PRINT
	hr = SetRenderState(pD3DDevice, D3DRS_DESTBLEND, DST_BLEND_ALPHA);
	TEST_AND_PRINT
//    hr = SetRenderState(pD3DDevice, D3DRS_ANTIALIASEDLINEENABLE, TRUE);
//	TEST_AND_PRINT
	hr = SetRenderState(pD3DDevice, D3DRS_ALPHATESTENABLE, TRUE);
	TEST_AND_PRINT
	hr = SetRenderState(pD3DDevice, D3DRS_ALPHAREF, 0x10);
	TEST_AND_PRINT
	hr = SetRenderState(pD3DDevice, D3DRS_ALPHAFUNC, D3DCMP_ALWAYS/*D3DCMP_GREATER*/);
	TEST_AND_PRINT

	hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_ADDRESSU, D3DTADDRESS_CLAMP);
	TEST_AND_PRINT
	hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_ADDRESSV, D3DTADDRESS_CLAMP);
	TEST_AND_PRINT

	// This means that we multiply (modulate) the texture alpha and the vertex alpha to
	// get the alpha for a pixel.
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_ALPHAOP, D3DTOP_MODULATE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_ALPHAARG1, D3DTA_TEXTURE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_ALPHAARG2, D3DTA_DIFFUSE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_COLOROP, D3DTOP_MODULATE/*D3DTOP_BLENDTEXTUREALPHAPM*/);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_COLORARG1, D3DTA_TEXTURE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_COLORARG2, D3DTA_DIFFUSE);
	TEST_AND_PRINT

	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_ALPHAOP, D3DTOP_MODULATE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_ALPHAARG1, D3DTA_TEXTURE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_ALPHAARG2, D3DTA_CURRENT);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_COLOROP, D3DTOP_MODULATE/*D3DTOP_BLENDTEXTUREALPHAPM*/);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_COLORARG1, D3DTA_TEXTURE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_COLORARG2, D3DTA_CURRENT);
	TEST_AND_PRINT

	/*
	 * Im' using dynamic vertex buffers now. That's the usage_dynamic, what's even better
	 * is if I have a big chunk of vertices that I use and give them out until
	 * I run out and then discard the buffer. More info in "Dynamic Vertexes" in the DX9 docs
	 */
	hr = pD3DDevice->CreateVertexBuffer(NUM_VERTICES * sizeof(CUSTOMTEXTUREVERTEX),
		D3DUSAGE_WRITEONLY | D3DUSAGE_DYNAMIC | (hwVertexProc ? 0 : D3DUSAGE_SOFTWAREPROCESSING),
		D3DFVF_CUSTOMTEXTUREVERTEX,
		D3DPOOL_DEFAULT,
		&pVertexImageBuffer, 
		NULL );
	TEST_AND_BAIL_NODEV
	hr = pD3DDevice->CreateVertexBuffer(NUM_VERTICES * sizeof(CUSTOMALPHATEXTUREVERTEX),
		D3DUSAGE_WRITEONLY | D3DUSAGE_DYNAMIC | (hwVertexProc ? 0 : D3DUSAGE_SOFTWAREPROCESSING),
		D3DFVF_CUSTOMALPHATEXTUREVERTEX,
		D3DPOOL_DEFAULT,
		&pVertexAlphaImageBuffer, 
		NULL );
	TEST_AND_BAIL_NODEV
	hr = pD3DDevice->CreateVertexBuffer(NUM_VERTICES * sizeof(CUSTOMALPHATEXTURE2VERTEX),
		D3DUSAGE_WRITEONLY | D3DUSAGE_DYNAMIC | (hwVertexProc ? 0 : D3DUSAGE_SOFTWAREPROCESSING),
		D3DFVF_CUSTOMALPHATEXTURE2VERTEX,
		D3DPOOL_DEFAULT,
		&pVertexAlpha2ImageBuffer, 
		NULL );
	TEST_AND_BAIL_NODEV
	hr = pD3DDevice->CreateVertexBuffer(NUM_POLYGON_VERTICES * sizeof(CUSTOMSHAPEVERTEX),
		D3DUSAGE_WRITEONLY | D3DUSAGE_DYNAMIC | (hwVertexProc ? 0 : D3DUSAGE_SOFTWAREPROCESSING),
		D3DFVF_CUSTOMSHAPEVERTEX,
		D3DPOOL_DEFAULT,
		&pPolygonBuffer, 
		NULL );
	TEST_AND_BAIL_NODEV
	hr = pD3DDevice->CreateVertexBuffer(16 * sizeof(CUSTOMYUVVERTEX),
		D3DUSAGE_WRITEONLY | D3DUSAGE_DYNAMIC | (hwVertexProc ? 0 : D3DUSAGE_SOFTWAREPROCESSING),
		D3DFVF_CUSTOMYUVVERTEX,
		D3DPOOL_DEFAULT,
		&pVertexYUVBuffer, 
		NULL );
	TEST_AND_BAIL_NODEV
	// Try to create a texture now so we can catch the failure early
	IDirect3DTexture9* newTexture = NULL;
	hr = pD3DDevice->CreateTexture(256, 256,
		1, // levels
		0, // usage
		D3DFMT_A8R8G8B8,
		useManagedMemory ? D3DPOOL_MANAGED : D3DPOOL_DEFAULT,
		&newTexture,
		NULL);
	TEST_AND_PRINT
	if (hr != D3D_OK)
	{
		if (useManagedMemory)
			return JNI_FALSE;
		slog((env, "DX9 is switching to using MANAGED memory for textures\r\n"));
		useManagedMemory = true;
		hr = pD3DDevice->CreateTexture(256, 256,
			1, // levels
			0, // usage
			D3DFMT_A8R8G8B8,
			D3DPOOL_MANAGED,
			&newTexture,
			NULL);
		TEST_AND_BAIL_NODEV
	}
	SAFE_RELEASE(newTexture);

	polygon_vertices = new CUSTOMSHAPEVERTEX[1024];
	image_alpha_vertices = new CUSTOMALPHATEXTUREVERTEX[16384];
	image_alpha2_vertices = new CUSTOMALPHATEXTURE2VERTEX[16384];
	image_vertices = new CUSTOMTEXTUREVERTEX[4];
	yuv_vertices = new CUSTOMYUVVERTEX[16];

#define YUV_RGB_PIXEL_SHADER_CODE "ps_1_1\n\
		def c0,0.5,0.5,0.5,0.5\n\
		def c1,1.0,1.0,1.0,0.0\n\
		def c2,0.6855,-0.349,0.0,0.0\n\
		def c3,0.0,-0.336,1.732,0.0\n\
		tex t0\n\
		tex t1\n\
		tex t2\n\
		sub_x2 r1,t1,c0\n\
		mul r0,t0,c1\n\
		mad r0,r1,c2,r0\n\
		sub_x2 r1,t2,c0\n\
		mad r0,r1,c3,r0\n"

	// Create our pixel shader for doing MPlayer video rendering
	LPD3DXBUFFER ppShader;
	hr = D3DXAssembleShader(
		(LPCSTR) YUV_RGB_PIXEL_SHADER_CODE,
		(UINT) strlen(YUV_RGB_PIXEL_SHADER_CODE),
		NULL,
		NULL,
		0,//D3DXSHADER_DEBUG, // TEMP FOR DEBUGGING
		&ppShader,
		NULL);
	TEST_AND_PRINT
	hr = pD3DDevice->CreatePixelShader((const DWORD*)ppShader->GetBufferPointer(), &yuvRgbPixelShader);
	TEST_AND_PRINT
	SAFE_RELEASE(ppShader);

	slog((env, "Done Initializing DirectX9\r\n"));

	return JNI_TRUE;
	JNI_RT_CATCH_RET_NODEV(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    cleanupDX9SageRenderer0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_DirectX9SageRenderer_cleanupDX9SageRenderer0(JNIEnv *env, jobject jo)
{
	JNI_TRY;
	slog((env, "Cleaning up DirectX9\r\n"));
	IDirect3D9* pD3D = (IDirect3D9*) env->GetLongField(jo, fid_pD3DObject);
	IDirect3DDevice9* pD3DDevice = (IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	IDirect3DDeviceManager9* pD3DDevMgr = (IDirect3DDeviceManager9*) env->GetLongField(jo, fid_pD3DDevMgr);
	UINT resetToken = (UINT) env->GetLongField(jo, fid_pD3DDevMgrToken);
	SAFE_RELEASE(gtextSprite);
	SAFE_RELEASE(pVideoTexture);
	SAFE_RELEASE(pVideoTextureY);
	SAFE_RELEASE(pVideoTextureU);
	SAFE_RELEASE(pVideoTextureV);
	SAFE_RELEASE(pSnapshotSurf);
	SAFE_RELEASE(pPolygonBuffer);
	SAFE_RELEASE(pVertexAlphaImageBuffer);
	SAFE_RELEASE(pVertexAlpha2ImageBuffer);
	SAFE_RELEASE(pVertexImageBuffer);
	SAFE_RELEASE(pVertexYUVBuffer);
	SAFE_RELEASE(yuvRgbPixelShader);

	if (fullScreenEx && pD3D && pD3DDevice)
	{
		// This is an attempt to fix a problem where we don't release FSE properly
		g_d3dpp.Windowed                   = true; // XXX turn OFF fullscreen mode 
		g_d3dpp.FullScreen_RefreshRateInHz = 0; // must be 0 for windowed mode
		D3DDISPLAYMODE d3ddm;
		HRESULT hr = pD3D->GetAdapterDisplayMode(D3DADAPTER_DEFAULT, &d3ddm);
		g_d3dpp.BackBufferFormat = d3ddm.Format;
		g_d3dpp.hDeviceWindow    = g_myWinID;
		g_d3dpp.BackBufferWidth = g_builtWidth;
		g_d3dpp.BackBufferHeight = g_builtHeight;
		g_d3dpp.BackBufferCount            = 1; 
		g_d3dpp.PresentationInterval = D3DPRESENT_INTERVAL_ONE;
		g_d3dpp.SwapEffect                 = D3DSWAPEFFECT_DISCARD; 
		slog((env, "Resetting D3D device to cleanup FSE properly...\r\n"));
		int waitsLeft = 5;
		// I've seen a log where this doesn't correct itself after 5 tries...who knows how many it can take so just try forever...
		// But we can easily reproduce a failure if we wait forever...so we'll go back to 5 tries.
		while ((hr = pD3DDevice->TestCooperativeLevel()) == D3DERR_DEVICELOST && waitsLeft-- > 0)
		{
			slog((env, "Waiting to Reset D3D device cooplevel=0x%x\r\n", hr));
			Sleep(100);
		}
		if (hr == D3DERR_DEVICELOST)
		{
			slog((env, "Gave up waiting for D3D device cooperative level to be OK\r\n"));
		}
		hr = pD3DDevice->Reset(&g_d3dpp); 
		HTESTPRINT(hr);
		if (pD3DDevMgr)
		{
			hr = pD3DDevMgr->ResetDevice(pD3DDevice, resetToken);
			HTESTPRINT(hr);
		}
	}
/*	if (pD3D && pD3DDevice)
	{
		// This fixes a problem going in & out of full screen exclusive mode. Without it we
		// can end up with a lost device that won't come back.
	//	ZeroMemory(&g_d3dpp, sizeof(g_d3dpp));
		g_d3dpp.Windowed         = TRUE;
		// We have to use D3DSWAPEFFECT_COPY or we're screwed for non-full screen rendering
		// because you can't specify the rectangle for present calls in the other modes. The only
		// other alternative is rendering at a fixed resolution which gets scaled to the screen resolution...
		// which might not be that bad of an idea.
		g_d3dpp.SwapEffect       = useFlip ? D3DSWAPEFFECT_FLIP : D3DSWAPEFFECT_COPY;//D3DSWAPEFFECT_DISCARD;//
		D3DDISPLAYMODE d3ddm;
		HRESULT hr = pD3D->GetAdapterDisplayMode(D3DADAPTER_DEFAULT, &d3ddm);
		g_d3dpp.BackBufferFormat = d3ddm.Format;
		g_d3dpp.hDeviceWindow    = g_myWinID;
		g_d3dpp.BackBufferWidth = g_builtWidth;
		g_d3dpp.BackBufferHeight = g_builtHeight;
		g_d3dpp.BackBufferCount = useFlip ? 2 : 1;
		// The debug runtime spits out:
		// Direct3D9: (WARN) :driver set D3DDEVCAPS_TEXTURENONLOCALVIDMEM w/o DDCAPS2_NONLOCALVIDMEM:turning off D3DDEVCAPS_TEXTURENONLOCALVIDMEM
		// FYI - disabling this flag does NOT fix it
		g_d3dpp.Flags = D3DPRESENTFLAG_VIDEO;
		// if this is at default then it uses the low-res system timer for vsync which sucks
		// BUT I'M PRETTY SURE IT CONSUMES A LOT OF CPU IN THE WAIT BECAUSE IT SPINS THE CPU FOR THE DELAY
		// ALBEIT ON A LOW PRIORITY I'M SURE
		g_d3dpp.PresentationInterval = D3DPRESENT_INTERVAL_ONE;

		g_d3dpp.BackBufferCount            = 1; 
		g_d3dpp.SwapEffect                 = D3DSWAPEFFECT_DISCARD; 
		g_d3dpp.Windowed                   = true; // XXX turn OFF fullscreen mode 
		g_d3dpp.FullScreen_RefreshRateInHz = 0; // must be 0 for windowed mode
		hr = pD3DDevice->Reset(&g_d3dpp); 
		HTESTPRINT(hr);
	}
*/
	if (!env->GetBooleanField(jo, fid_rerenderedDL))
	{
		slog((env, "Doing full DX9 cleanup.\r\n"));
		if (pD3DDevMgr)
			pD3DDevMgr->Release();
		env->SetLongField(jo, fid_pD3DDevMgr, 0);
		if (pD3DDevice)
		{
			ULONG rCount = pD3DDevice->Release();
			if (rCount)
			{
				slog((env, "D3DDevice refcount=%d\r\n", rCount));
			}
		}
	//	SAFE_RELEASE(pD3DDevice);
		env->SetLongField(jo, fid_pD3DDevice, 0);
		if (pD3D)
		{
			ULONG rCount = pD3D->Release();
			if (rCount)
			{
				slog((env, "D3D refcount=%d\r\n", rCount));
			}
		}
	//	SAFE_RELEASE(pD3D);
		env->SetLongField(jo, fid_pD3DObject, 0);
	}
	if (polygon_vertices)
		delete [] polygon_vertices;
	if (image_alpha_vertices)
		delete [] image_alpha_vertices;
	if (image_alpha2_vertices)
		delete [] image_alpha2_vertices;
	if (image_vertices)
		delete [] image_vertices;
	if (yuv_vertices)
		delete [] yuv_vertices;
	polygon_vertices = NULL;
	image_alpha_vertices = NULL;
	image_alpha2_vertices = NULL;
	image_vertices = NULL;
	yuv_vertices = NULL;

//	CoUninitialize();
	slog((env, "Done cleaning up DirectX9\r\n"));
	JNI_RT_CATCH_NODEV;
}


// Callback function used to clear a texture to black
VOID WINAPI ClearToBlack (D3DXVECTOR4* pOut, const D3DXVECTOR2* pTexCoord, 
const D3DXVECTOR2* pTexelSize, LPVOID pData)
{
   *pOut = D3DXVECTOR4(0.0f, 0.0f, 0.0f, 0.0f);
}



/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    createD3DTextureFromMemory0
 * Signature: (IILjava/nio/ByteBuffer;)J
 */
JNIEXPORT jlong JNICALL Java_sage_DirectX9SageRenderer_createD3DTextureFromMemory0__IILjava_nio_ByteBuffer_2
  (JNIEnv *env, jobject jo, jint width, jint height, jobject data)
{
	slog((env, "Creating DirectX9 Texture from nio buffer w=%d h=%d\r\n", width, height));
	IDirect3D9* pD3D = (IDirect3D9*) env->GetLongField(jo, fid_pD3DObject);
	IDirect3DDevice9* pD3DDevice = /*GetSharedD3DDevice(env, jo);*/(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3D || !pD3DDevice)
	{
		elog((env, "D3DDevice is not initialized, cannot create texture.\r\n")); 
		return 0;
	}
	JNI_TRY;

	HRESULT hr;

    DWORD storedWidth = 1;
    DWORD storedHeight = 1;
    if (requiresPow2Textures)
    {
//		slog((env, "Converting texture to power of 2 size\r\n"));
        while (storedWidth < ((DWORD) width))
            storedWidth = storedWidth << 1;
        while (storedHeight < ((DWORD) height))
            storedHeight = storedHeight << 1;
    }
	else
	{
		storedWidth = width;
		storedHeight = height;
	}

	
	IDirect3DSurface9* newSurface = NULL;
	IDirect3DTexture9* newTexture = NULL;
	hr = pD3DDevice->CreateTexture(storedWidth, storedHeight, 
		1, // levels
		0, // usage
		D3DFMT_A8R8G8B8,
		useManagedMemory ? D3DPOOL_MANAGED : D3DPOOL_DEFAULT,
		&newTexture,
		NULL);
	TEST_AND_PRINT
	if (hr != D3D_OK)
	{
		if (useManagedMemory)
		{
//			ReleaseSharedD3DDevice(env, jo, pD3DDevice);
			return 0;
		}
		slog((env, "DX9 is switching to using MANAGED memory for textures\r\n"));
		useManagedMemory = true;
		hr = pD3DDevice->CreateTexture(storedWidth, storedHeight,
			1, // levels
			0, // usage
			D3DFMT_A8R8G8B8,
			D3DPOOL_MANAGED,
			&newTexture,
			NULL);
		TEST_AND_PRINT
		if (hr != D3D_OK)
		{
//			ReleaseSharedD3DDevice(env, jo, pD3DDevice);
			return 0;
		}
	}

	hr = newTexture->GetSurfaceLevel(0, &newSurface);
	TEST_AND_PRINT
	if (hr != D3D_OK)
	{
//		ReleaseSharedD3DDevice(env, jo, pD3DDevice);
		return 0;
	}

	RECT imgRect;
	imgRect.left = 0;
	imgRect.top = 0;
	imgRect.right = storedWidth;
	imgRect.bottom = storedHeight;
	void* myMemory = env->GetDirectBufferAddress(data);
	unsigned long* swapOrg = (unsigned long*)myMemory;
	unsigned long* swapper = (unsigned long*)calloc((storedWidth*storedHeight), sizeof(unsigned long));  // change from malloc to calloc to prevent 6386 warning
	if (swapper == nullptr)
		return 0;
	DWORD x, y;
	DWORD jheight = (DWORD) height;
	DWORD jwidth = (DWORD) width;
	for (y = 0; y < jheight; y++)
	{
		int idxNew = y * storedWidth;
		int idxOld = y * jwidth;
		for (x = 0; x < jwidth; x++)
			swapper[idxNew + x] = _byteswap_ulong(swapOrg[idxOld + x]);

		if (x < storedWidth)
			ZeroMemory(&(swapper[idxNew + x]), sizeof(unsigned long) * (storedWidth - x));
	}
	if (y < storedHeight)
		ZeroMemory(&(swapper[y * storedWidth]), sizeof(unsigned long) * storedWidth * (storedHeight - y));
	hr = D3DXLoadSurfaceFromMemory(
		newSurface, 
		NULL, // no palette
		&imgRect, // part of the surface which holds our image (it may be larger due to pow2 requirements)
		swapper,
		D3DFMT_A8R8G8B8,
		storedWidth*4, // byte width of a row
		NULL, // no palette
		&imgRect, // image size
		D3DX_FILTER_NONE, // interpolation
		0); // no color key

	free(swapper);

	TEST_AND_PRINT
	if (hr != D3D_OK) 
	{
		SAFE_RELEASE(newSurface);
		SAFE_RELEASE(newTexture);
		if (!useManagedMemory)
		{
			slog((env, "DX9 is switching2 to using MANAGED memory for textures\r\n"));
			useManagedMemory = true;
//			ReleaseSharedD3DDevice(env, jo, pD3DDevice);
			return Java_sage_DirectX9SageRenderer_createD3DTextureFromMemory0__IILjava_nio_ByteBuffer_2(env, jo, width, height, data);
		}
//		ReleaseSharedD3DDevice(env, jo, pD3DDevice);
		return 0;
	}
	SAFE_RELEASE(newSurface);
//	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return (jlong) newTexture;
	JNI_RT_CATCH_RET(0);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    createD3DTextureFromMemory0
 * Signature: (II[I)J
 */
JNIEXPORT jlong JNICALL Java_sage_DirectX9SageRenderer_createD3DTextureFromMemory0__II_3I
  (JNIEnv *env, jobject jo, jint width, jint height, jintArray data)
{
	slog((env, "Creating DirectX9 Texture from memory w=%d h=%d\r\n", width, height));
	IDirect3D9* pD3D = (IDirect3D9*) env->GetLongField(jo, fid_pD3DObject);
	IDirect3DDevice9* pD3DDevice = /*GetSharedD3DDevice(env, jo);*/(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3D || !pD3DDevice)
	{
		elog((env, "D3DDevice is not initialized, cannot create texture.\r\n")); 
		return 0;
	}
	JNI_TRY;

	HRESULT hr;

    DWORD storedWidth = 1;
    DWORD storedHeight = 1;
    if(requiresPow2Textures)
    {
//		slog((env, "Converting texture to power of 2 size\r\n"));
        while (storedWidth < ((DWORD) width))
            storedWidth = storedWidth << 1;
        while (storedHeight < ((DWORD) height))
            storedHeight = storedHeight << 1;
    }
	else
	{
		storedWidth = width;
		storedHeight = height;
	}

	
	IDirect3DSurface9* newSurface = NULL;
	IDirect3DTexture9* newTexture = NULL;
	hr = pD3DDevice->CreateTexture(storedWidth, storedHeight, 
		1, // levels
		0, // usage
		D3DFMT_A8R8G8B8,
		useManagedMemory ? D3DPOOL_MANAGED : D3DPOOL_DEFAULT,
		&newTexture,
		NULL);
	TEST_AND_PRINT
	if (hr != D3D_OK)
	{
		if (useManagedMemory)
		{
//			ReleaseSharedD3DDevice(env, jo, pD3DDevice);
			return 0;
		}

		slog((env, "DX9 is switching to using MANAGED memory for textures\r\n"));
		useManagedMemory = true;
		hr = pD3DDevice->CreateTexture(storedWidth, storedHeight,
			1, // levels
			0, // usage
			D3DFMT_A8R8G8B8,
			D3DPOOL_MANAGED,
			&newTexture,
			NULL);
		TEST_AND_PRINT
		if (hr != D3D_OK) 
		{
//			ReleaseSharedD3DDevice(env, jo, pD3DDevice);
			return 0;
		}
	}

	if (width != storedWidth || height != storedHeight)
	{
		// Clear the texture surface so we don't have residual data in it
		hr = D3DXFillTexture(newTexture, ClearToBlack, NULL);
		TEST_AND_PRINT
	}

	hr = newTexture->GetSurfaceLevel(0, &newSurface);
	TEST_AND_PRINT
	if (hr != D3D_OK)
	{
//		ReleaseSharedD3DDevice(env, jo, pD3DDevice);
		return 0;
	}

	RECT imgRect;
	imgRect.left = 0;
	imgRect.top = 0;
	imgRect.right = width;
	imgRect.bottom = height;
	void* critArr = env->GetPrimitiveArrayCritical(data, NULL);
	if (!critArr)
	{
		slog((env, "NULL POINTER for java image data\r\n"));
//		ReleaseSharedD3DDevice(env, jo, pD3DDevice);
		return 0;
	}
	hr = D3DXLoadSurfaceFromMemory(
		newSurface, 
		NULL, // no palette
		&imgRect, // part of the surface which holds our image (it may be larger due to pow2 requirements)
		critArr,
		D3DFMT_A8R8G8B8,
		width*4, // byte width of a row
		NULL, // no palette
		&imgRect, // image size
		D3DX_FILTER_NONE, // interpolation
		0); // no color key

	env->ReleasePrimitiveArrayCritical(data, critArr, JNI_ABORT);
	TEST_AND_PRINT
	if (hr != D3D_OK) 
	{
		SAFE_RELEASE(newSurface);
		SAFE_RELEASE(newTexture);
//		ReleaseSharedD3DDevice(env, jo, pD3DDevice);
		if (!useManagedMemory)
		{
			slog((env, "DX9 is switching2 to using MANAGED memory for textures\r\n"));
			useManagedMemory = true;
			return Java_sage_DirectX9SageRenderer_createD3DTextureFromMemory0__II_3I(env, jo, width, height, data);
		}
		return 0;
	}
	/*
	 * TEXTURES ARE LOADING AT THE CORRECT SIZE; I HAVE VERIFIED THIS
	 */
	SAFE_RELEASE(newSurface);
//	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return (jlong) newTexture;
	JNI_RT_CATCH_RET(0);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    createD3DTextureFromMemory0
 * Signature: (II[B)J
 */
JNIEXPORT jlong JNICALL Java_sage_DirectX9SageRenderer_createD3DTextureFromMemory0__II_3B
  (JNIEnv *env, jobject jo, jint width, jint height, jbyteArray data)
{
	slog((env, "Creating DirectX9 Texture from file in memory w=%d h=%d\r\n", width, height));
	IDirect3D9* pD3D = (IDirect3D9*) env->GetLongField(jo, fid_pD3DObject);
	IDirect3DDevice9* pD3DDevice = /*GetSharedD3DDevice(env, jo);*/(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3D || !pD3DDevice)
	{
		elog((env, "D3DDevice is not initialized, cannot create texture.\r\n")); 
		return 0;
	}
	JNI_TRY;

	HRESULT hr;

    DWORD storedWidth = 1;
    DWORD storedHeight = 1;
    if(requiresPow2Textures)
    {
//		slog((env, "Converting texture to power of 2 size\r\n"));
        while (storedWidth < ((DWORD) width))
            storedWidth = storedWidth << 1;
        while (storedHeight < ((DWORD) height))
            storedHeight = storedHeight << 1;
    }
	else
	{
		storedWidth = width;
		storedHeight = height;
	}

	
	IDirect3DSurface9* newSurface = NULL;
	IDirect3DTexture9* newTexture = NULL;
	hr = pD3DDevice->CreateTexture(storedWidth, storedHeight, 
		1, // levels
		0, // usage
		D3DFMT_A8R8G8B8,
		useManagedMemory ? D3DPOOL_MANAGED : D3DPOOL_DEFAULT,
		&newTexture,
		NULL);
	TEST_AND_PRINT
	if (hr != D3D_OK)
	{
		if (useManagedMemory)
		{
//			ReleaseSharedD3DDevice(env, jo, pD3DDevice);
			return 0;
		}
		slog((env, "DX9 is switching to using MANAGED memory for textures\r\n"));
		useManagedMemory = true;
		hr = pD3DDevice->CreateTexture(storedWidth, storedHeight,
			1, // levels
			0, // usage
			D3DFMT_A8R8G8B8,
			D3DPOOL_MANAGED,
			&newTexture,
			NULL);
		TEST_AND_PRINT
		if (hr != D3D_OK)
		{
//			ReleaseSharedD3DDevice(env, jo, pD3DDevice);
			return 0;
		}
	}

	if (width != storedWidth || height != storedHeight)
	{
		// Clear the texture surface so we don't have residual data in it
		hr = D3DXFillTexture(newTexture, ClearToBlack, NULL);
		TEST_AND_PRINT
	}

	hr = newTexture->GetSurfaceLevel(0, &newSurface);
	TEST_AND_PRINT
	if (hr != D3D_OK)
	{
//		ReleaseSharedD3DDevice(env, jo, pD3DDevice);
		return 0;
	}

	RECT imgRect;
	imgRect.left = 0;
	imgRect.top = 0;
	imgRect.right = width;
	imgRect.bottom = height;
	D3DXIMAGE_INFO imgInfo;
	ZeroMemory(&imgInfo, sizeof(D3DXIMAGE_INFO));
	void* critArr = env->GetPrimitiveArrayCritical(data, NULL);
	if (!critArr)
	{
//		ReleaseSharedD3DDevice(env, jo, pD3DDevice);
		slog((env, "NULL POINTER for java image data\r\n"));
		return 0;
	}
	hr = D3DXLoadSurfaceFromFileInMemory(
		newSurface, 
		NULL, // no palette
		&imgRect, // part of the surface which holds our image (it may be larger due to pow2 requirements)
		critArr,
		env->GetArrayLength(data),
		&imgRect, // image size
		D3DX_FILTER_NONE, // interpolation
		0, // no color key
		&imgInfo);

	env->ReleasePrimitiveArrayCritical(data, critArr, JNI_ABORT);
	TEST_AND_PRINT
	if (hr != D3D_OK) 
	{
		SAFE_RELEASE(newSurface);
		SAFE_RELEASE(newTexture);
//		ReleaseSharedD3DDevice(env, jo, pD3DDevice);
		// This can fail for other non-mem reasons such as a corrupted image file
		if (!useManagedMemory && hr == D3DERR_OUTOFVIDEOMEMORY)
		{
			slog((env, "DX9 is switching2 to using MANAGED memory for textures\r\n"));
			useManagedMemory = true;
			return Java_sage_DirectX9SageRenderer_createD3DTextureFromMemory0__II_3B(env, jo, width, height, data);
		}
		return 0;
	}
	SAFE_RELEASE(newSurface);
//	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return (jlong) newTexture;
	JNI_RT_CATCH_RET(0);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    createD3DRenderTarget0
 * Signature: (II)J
 */
JNIEXPORT jlong JNICALL Java_sage_DirectX9SageRenderer_createD3DRenderTarget0
  (JNIEnv *env, jobject jo, jint width, jint height)
{
	slog((env, "Creating new render target surface %d x %d\r\n", width, height));
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return 0;
	JNI_TRY;
	HRESULT hr;
	IDirect3DTexture9* rtTexture = NULL;
	hr = D3DXCreateTexture(pD3DDevice, width, height, 1, D3DUSAGE_RENDERTARGET, D3DFMT_A8R8G8B8,
		D3DPOOL_DEFAULT, &rtTexture);
	TEST_AND_PRINT;
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return (jlong)rtTexture;
	JNI_RT_CATCH_RET(0);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    freeD3DTexturePointer0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_DirectX9SageRenderer_freeD3DTexturePointer0
  (JNIEnv *env, jobject jo, jlong surfPtr)
{
	JNI_TRY;
	IDirect3DTexture9* pSurf = (IDirect3DTexture9*) surfPtr;
	SAFE_RELEASE(pSurf);
	JNI_RT_CATCH_NODEV;
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    present0
 * Signature: (IIII)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_present0
  (JNIEnv *env, jobject jo, jint clipX, jint clipY, jint clipW, jint clipH)
{
//	slog((env, "DirectX9 present\r\n"));
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;
	RECT clipRect;
	HRESULT hr;
	clipRect.left = clipX;
	clipRect.top = clipY;
	clipRect.right = clipW + clipX;// - 1;
	clipRect.bottom = clipX + clipH;// - 1;
	IDirect3DSwapChain9* swappy = NULL;
	hr = pD3DDevice->GetSwapChain(0, &swappy);
	TEST_AND_PRINT
	if (useFlip || fullScreenEx)
		hr = swappy->Present(NULL, NULL, NULL, NULL, D3DPRESENT_DONOTWAIT);
	else
		hr = swappy->Present(&clipRect, &clipRect, NULL, NULL, D3DPRESENT_DONOTWAIT);
	if (hr != D3D_OK)
	{
		while (hr == D3DERR_WASSTILLDRAWING)
		{
			Sleep(1);
			if (useFlip || fullScreenEx)
				hr = swappy->Present(NULL, NULL, NULL, NULL, D3DPRESENT_DONOTWAIT);
			else
				hr = swappy->Present(&clipRect, &clipRect, NULL, NULL, D3DPRESENT_DONOTWAIT);
		}
		if (hr != D3D_OK)
		{
			slog((env, "DX9Renderer NATIVE WARNING (non-FAILURE) line %d hr=0x%x\r\n", __LINE__, hr));
		}
	}
	SAFE_RELEASE(swappy);
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	if (FAILED(hr))
		return JNI_FALSE;
	else
		return JNI_TRUE;
	JNI_RT_CATCH_RET(JNI_FALSE);
}
/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    clearScene0
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sage_DirectX9SageRenderer_clearScene0
  (JNIEnv *env, jobject jo, jboolean fullClear)
{
//	slog((env, "DirectX9 clearScene\r\n"));
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	JNI_TRY;
	if (pD3DDevice)
	{
		HRESULT hr;
		IDirect3DSurface9* backBuffer = NULL;
	    hr = pD3DDevice->GetBackBuffer( 0, 0,
                                    D3DBACKBUFFER_TYPE_MONO,
                                    &backBuffer);

		TEST_AND_PRINT
		// SetRenderTarget does NOT increase the reference count, unlike what the DX9
		// docs
		scissoredLast = false;
		hr = pD3DDevice->SetRenderTarget(0, backBuffer);
		TEST_AND_PRINT
		if (fullClear)
		{
			// clear the scene so we don't have any articats left
			// This takes time though....so don't bother.
			hr = pD3DDevice->Clear( 0L, NULL, D3DCLEAR_TARGET, 
						D3DCOLOR_XRGB(0,0,0), 1.0f, 0L );
			TEST_AND_PRINT
		}

		backBuffer->Release(); // one for GetBackBuffer and one for SetRenderTarget
		//SAFE_RELEASE(backBuffer); ///...but this one gives an assertion error....

		ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	}
	// When we restart the scene we can then reuse the vertex buffers
	imageAlphaVertexPos = 0;
	imageAlpha2VertexPos = 0;
	polygonVertexPos = 0;
	JNI_RT_CATCH;
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    setRenderTarget0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_setRenderTarget0
  (JNIEnv *env, jobject jo, jlong rtPtr)
{
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;
	HRESULT hr;
	IDirect3DSurface9* backBuffer = NULL;
	if (rtPtr)
	{
		IDirect3DTexture9* rtTexture = (IDirect3DTexture9*) rtPtr;
		hr = rtTexture->GetSurfaceLevel(0, &backBuffer);
	}
	else
	{
		hr = pD3DDevice->GetBackBuffer( 0, 0,
										D3DBACKBUFFER_TYPE_MONO,
										&backBuffer);
	}
	TEST_AND_PRINT
	// SetRenderTarget does NOT increase the reference count, unlike what the DX9
	// docs
	scissoredLast = false;
	// Changing the render target resets the viewport as well; so retain that during the switch
	D3DVIEWPORT9 viewport;
	pD3DDevice->GetViewport(&viewport);
	hr = pD3DDevice->SetRenderTarget(0, backBuffer);
	TEST_AND_PRINT
	if (!rtPtr)
	{
		viewport.Width = lastViewportWidth;
		viewport.Height = lastViewportHeight;
		pD3DDevice->SetViewport(&viewport);
	}
	else
	{
		// Set it to the min of the size of the surface or the last set viewport
		IDirect3DTexture9* rtTexture = (IDirect3DTexture9*) rtPtr;
		D3DSURFACE_DESC textDesc;
		rtTexture->GetLevelDesc(0, &textDesc);
		viewport.Width = min(textDesc.Width, lastViewportWidth);
		viewport.Height = min(textDesc.Height, lastViewportHeight);
		pD3DDevice->SetViewport(&viewport);
	}
	backBuffer->Release();
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	if (FAILED(hr))
		return JNI_FALSE;
	else
		return JNI_TRUE;
	JNI_RT_CATCH_RET(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    beginScene0
 * Signature: (II)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_beginScene0
  (JNIEnv *env, jobject jo, jint viewportWidth, jint viewportHeight)
{
	dx9log((env, "DirectX9 beginScene\r\n"));
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;
	// Get the current viewport size
	D3DVIEWPORT9 viewport;
	pD3DDevice->GetViewport(&viewport);
	lastViewportWidth = viewportWidth;
	lastViewportHeight = viewportHeight;
	if (viewport.Width != viewportWidth || viewport.Height != viewportHeight)
	{
		viewport.Width = viewportWidth;
		viewport.Height = viewportHeight;
		pD3DDevice->SetViewport(&viewport);
	}
/*	float vw = viewport.Width*0.5f;
	float vh = viewport.Height*0.5f;

	float cameraOffsetX = cameraX - vw;
	float cameraOffsetY = cameraY - vh;

	// For our camera view we need to flip the Y coordinates and put us in the center of the viewport; offset by the camera positions
	D3DXMATRIX flipMat, translateMat, viewMat;
	D3DXMatrixScaling(&flipMat, 1.0f, -1.0f, 1.0f);
	D3DXMatrixTranslation(&translateMat, -(viewport.X + vw + cameraOffsetX), -(viewport.Y + vh + cameraOffsetY), 2*vw);
	D3DXMatrixMultiply(&viewMat, &translateMat, &flipMat);
	pD3DDevice->SetTransform(D3DTS_VIEW, &IDENTITY_MATRIX);

	// Setup the projection matrix; back us up enough in Z so anything that rotates isn't clipped
//	D3DXMATRIX projMat;
//	D3DXMatrixPerspectiveOffCenterLH(&projMat, (-vw - cameraOffsetX)*0.5f, (vw - cameraOffsetX)*0.5f, (-vh + cameraOffsetY)*0.5f, (vh + cameraOffsetY)*0.5f, vw, 100*vw);
	pD3DDevice->SetTransform(D3DTS_PROJECTION, &IDENTITY_MATRIX);
*/
	HRESULT hr = pD3DDevice->BeginScene();

	// This is needed because the subpicture connection was changing these parameters
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_ALPHAOP, D3DTOP_MODULATE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_ALPHAARG1, D3DTA_TEXTURE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_ALPHAARG2, D3DTA_DIFFUSE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_COLOROP, D3DTOP_MODULATE/*D3DTOP_BLENDTEXTUREALPHAPM*/);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_COLORARG1, D3DTA_TEXTURE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 0, D3DTSS_COLORARG2, D3DTA_DIFFUSE);
	TEST_AND_PRINT

	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_ALPHAOP, D3DTOP_MODULATE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_ALPHAARG1, D3DTA_TEXTURE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_ALPHAARG2, D3DTA_CURRENT);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_COLOROP, D3DTOP_MODULATE/*D3DTOP_BLENDTEXTUREALPHAPM*/);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_COLORARG1, D3DTA_TEXTURE);
	TEST_AND_PRINT
	hr = SetTextureStageState(pD3DDevice, 1, D3DTSS_COLORARG2, D3DTA_CURRENT);
	TEST_AND_PRINT

	// NOTE: We were getting error in DrawPrimitive below because these were getting changed by the
	// video renderer (subpicture/line21 or something like that) so we need to reset these each time.
	lastScaleHint = -1;
	lastBlendMode = -1;
	if (lastTexturePtr)
	{
		lastTexturePtr = NULL;
		hr = pD3DDevice->SetTexture( 0, NULL);
	}
	if (lastDiffuseTexturePtr)
		pD3DDevice->SetTexture(1, lastDiffuseTexturePtr = NULL);
	lastVertexBuffer = NULL;

	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	if (hr == D3D_OK)
	{
		return JNI_TRUE;
	}
	else
	{
		TEST_AND_PRINT
		return JNI_FALSE;
	}
	JNI_RT_CATCH_RET(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    endScene0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_DirectX9SageRenderer_endScene0
  (JNIEnv *env, jobject jo)
{
	dx9log((env, "DirectX9 endScene\r\n"));
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return;
	JNI_TRY;

	if (textSpriteActive)
	{
		textSpriteActive = 0;
		gtextSprite->End();
	}
	pD3DDevice->EndScene();
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	JNI_RT_CATCH;
}

bool linearVideoScale = true;
/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    stretchBlt0
 * Signature: (JIIIIJIIIII)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_stretchBlt0
  (JNIEnv *env, jobject jo, jlong srcSurfPtr, jint srcClipX, jint srcClipY, jint srcClipW, jint srcClipH,
	jlong destTexturePtr, jint destClipX, jint destClipY, jint destClipW, jint destClipH, jint scaleHint)
{
	// If scaleHint is 1 then we're re-using the last video frame so we don't need to redo the
	// surface copy in that case, just reuse our surface we have.
//	slog((env, "stretchBlt0 src=%d dest=%d\r\n", srcSurfPtr, destTexturePtr));
	HRESULT hr;
	if (!srcSurfPtr) return JNI_FALSE;
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	IDirect3DSurface9* pD3DRenderTarget	= NULL;
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;
//	if (!pD3DRenderTarget && !destTexturePtr) return JNI_FALSE;
	if (scissoredLast)
	{
		scissoredLast = false;
		SetRenderState(pD3DDevice, D3DRS_SCISSORTESTENABLE, FALSE);
	}
	if (textSpriteActive)
	{
		textSpriteActive = 0;
		gtextSprite->End();
	}
	RECT srcRect;
	srcRect.left = srcClipX;
	srcRect.top = srcClipY;
	srcRect.right = srcClipX + srcClipW;
	srcRect.bottom = srcClipY + srcClipH;
	// I KNOW THAT THIS DOES NOT NEED A -1 AFTER IT, I tested this with the video blitting and when
	// I had the -1 there, there was a 1-pixel corrupt border along the right & bottom edges
	RECT destRect;
	destRect.left = destClipX;
	destRect.top = destClipY;
	destRect.right = destClipX + destClipW;
	destRect.bottom = destClipY + destClipH;

	hr = pD3DDevice->GetBackBuffer(0, 0, D3DBACKBUFFER_TYPE_MONO, &pD3DRenderTarget);
	TEST_AND_PRINT
//slog((env, "srl=%d srt=%d srr=%d srb=%d drl=%d drt=%d drr=%d drb=%d\r\n", srcRect.left, srcRect.top, srcRect.right, srcRect.bottom,
//	destRect.left, destRect.top, destRect.right, destRect.bottom));
	if (scaleHint >= 0)
	{
		IDirect3DSurface9* srcSurf = (IDirect3DSurface9*) srcSurfPtr;
		D3DSURFACE_DESC currSurfDesc;
		srcSurf->GetDesc(&currSurfDesc);
		BOOL allocNewVText = FALSE;
		if (pVideoTexture)
		{
			// Check to see if we're still the same size
			IDirect3DSurface9* vSurf = NULL;
			hr = pVideoTexture->GetSurfaceLevel(0, &vSurf);
			TEST_AND_BAIL;
			D3DSURFACE_DESC surfDesc;
			vSurf->GetDesc(&surfDesc);
			if (surfDesc.Width != currSurfDesc.Width || surfDesc.Height != currSurfDesc.Height)
			{
				allocNewVText = TRUE;
			}
			SAFE_RELEASE(vSurf);
		}
		else
			allocNewVText = TRUE;

		// THIS WILL DISABLE USE OF THE BACKING TEXTURE THAT EACH frame is copied onto. It was meant to
		// help with playback corruption that occured due to D3D Device sharing. But since we use YUV mixing now,
		// we shouldn't have problems with this.
		if (!copyVideoTexture)
			allocNewVText = FALSE;

		// The video surface has changed so update to use a new temp surface for the copy
		if (allocNewVText)
		{
			SAFE_RELEASE(pVideoTexture);
			if (currSurfDesc.Format > '0000')
			{
				slog((env, "Created alternate surface\r\n"));
				D3DDISPLAYMODE dm; 
				hr = pD3DDevice->GetDisplayMode(NULL,  &dm);
				TEST_AND_BAIL
				// create the private texture
				hr = pD3DDevice->CreateTexture(currSurfDesc.Width, currSurfDesc.Height,
										1, 
										D3DUSAGE_RENDERTARGET, 
										dm.Format, 
										D3DPOOL_DEFAULT , 
										&pVideoTexture, NULL );
				if (FAILED(hr))
				{
					slog((env, "Disabling video texture copying due to failure to create texture.\r\n"));
					copyVideoTexture = false;
				}
				TEST_AND_PRINT
			}
		}
		if (pVideoTexture)
		{
			IDirect3DSurface9* surface = NULL;
			hr = pVideoTexture->GetSurfaceLevel(0, &surface);
			TEST_AND_BAIL
			if (!scaleHint)
			{
				hr = pD3DDevice->StretchRect(srcSurf, NULL, surface, NULL, D3DTEXF_NONE);
				TEST_AND_PRINT
			}
			hr = pD3DDevice->StretchRect(surface, &srcRect, pD3DRenderTarget, &destRect,
				linearVideoScale ? D3DTEXF_LINEAR : D3DTEXF_NONE);
			if (FAILED(hr))
			{
				D3DSURFACE_DESC surfDesc;
				surface->GetDesc(&surfDesc);
				slog((env, "DX9 StretchRect failed hr=0x%x surf=%dx%d src=[%d,%d,%d,%d] dest=[%d,%d,%d,%d]\r\n", hr, (int)surfDesc.Width,
					(int) surfDesc.Height, srcRect.left, srcRect.top, srcRect.right, srcRect.bottom,
					destRect.left, destRect.top, destRect.right, destRect.bottom));
			}
			if (FAILED(hr) && linearVideoScale)
			{
				slog((env, "DX9 DISABLING scaling filtering of video blit\r\n"));
				linearVideoScale = false;
				hr = pD3DDevice->StretchRect(surface, &srcRect, pD3DRenderTarget, &destRect,
					linearVideoScale ? D3DTEXF_LINEAR : D3DTEXF_NONE);
				TEST_AND_PRINT
			}
			SAFE_RELEASE(surface);
		}
		else 
		{
			hr = pD3DDevice->StretchRect(srcSurf, &srcRect, pD3DRenderTarget, &destRect, 
				linearVideoScale ? D3DTEXF_LINEAR : D3DTEXF_NONE);
			TEST_AND_PRINT
			if (hr != S_OK && linearVideoScale)
			{
				slog((env, "DX9 DISABLING scaling filtering of video blit\r\n"));
				linearVideoScale = false;
				hr = pD3DDevice->StretchRect(srcSurf, &srcRect, pD3DRenderTarget, &destRect, 
					linearVideoScale ? D3DTEXF_LINEAR : D3DTEXF_NONE);
				TEST_AND_PRINT
				if (FAILED(hr) && !copyVideoTexture)
				{
					slog((env, "DX9 enabling video texture backing copy since stretch is failing\r\n"));
					copyVideoTexture = true;
					linearVideoScale = true; // We can put this back on since it might still be OK
				}
			}
		}
	}
	else
	{
		BOOL allocNewVText = FALSE;
		if (!pVideoTextureY)
			allocNewVText = TRUE;

		// The video surface has changed so update to use a new temp surface for the copy
		if (allocNewVText)
		{
			SAFE_RELEASE(pVideoTextureY);
			SAFE_RELEASE(pVideoTextureU);
			SAFE_RELEASE(pVideoTextureV);
//			if (currSurfDesc.Format > '0000')
			{
				slog((env, "Created alternate surface\r\n"));
//				D3DDISPLAYMODE dm; 
//				hr = pD3DDevice->GetDisplayMode(NULL,  &dm);
//				TEST_AND_BAIL
				// create the private texture
/*				hr = pD3DDevice->CreateOffscreenPlainSurface(720, 480,
										D3DFMT_YUY2,//dm.Format, 
										D3DPOOL_DEFAULT , 
										&pVideoSurface, NULL );*/
				hr = pD3DDevice->CreateTexture(gVideoWidth, gVideoHeight, 1, D3DUSAGE_DYNAMIC,
										D3DFMT_L8, 
										D3DPOOL_DEFAULT , 
										&pVideoTextureY, NULL );
				TEST_AND_PRINT
				hr = pD3DDevice->CreateTexture(gVideoWidth/2, gVideoHeight/2, 1, D3DUSAGE_DYNAMIC,
										D3DFMT_L8, 
										D3DPOOL_DEFAULT, 
										&pVideoTextureU, NULL );
				TEST_AND_PRINT
				hr = pD3DDevice->CreateTexture(gVideoWidth/2, gVideoHeight/2, 1, D3DUSAGE_DYNAMIC,
										D3DFMT_L8, 
										D3DPOOL_DEFAULT, 
										&pVideoTextureV, NULL );
				TEST_AND_PRINT
			}
		}
		if (pVideoTextureY)
		{
			// Load the three YUV surfaces from the shared memory video data
			IDirect3DSurface9* pVideoSurfaceY = NULL;
			IDirect3DSurface9* pVideoSurfaceU = NULL;
			IDirect3DSurface9* pVideoSurfaceV = NULL;
			hr = pVideoTextureY->GetSurfaceLevel(0, &pVideoSurfaceY);
			TEST_AND_PRINT
			hr = pVideoTextureU->GetSurfaceLevel(0, &pVideoSurfaceU);
			TEST_AND_PRINT
			hr = pVideoTextureV->GetSurfaceLevel(0, &pVideoSurfaceV);
			TEST_AND_PRINT
			D3DLOCKED_RECT lockRect;
			hr = pVideoSurfaceY->LockRect(&lockRect, NULL, 0);
			TEST_AND_PRINT
			for (int i = 0; i < gVideoHeight; i++)
			{
				memcpy((PBYTE)lockRect.pBits + i*lockRect.Pitch, (LPVOID)((LPBYTE)srcSurfPtr + i*gVideoWidth), gVideoWidth);
			}
			hr = pVideoSurfaceY->UnlockRect();
			TEST_AND_PRINT
			hr = pVideoSurfaceU->LockRect(&lockRect, NULL, 0);
			TEST_AND_PRINT
			for (int i = 0; i < gVideoHeight/2; i++)
			{
				memcpy((PBYTE)lockRect.pBits + i*lockRect.Pitch, 
					(LPVOID)((LPBYTE)srcSurfPtr + gVideoWidth*gVideoHeight + i*gVideoWidth/2), gVideoWidth/2);
			}
			hr = pVideoSurfaceU->UnlockRect();
			TEST_AND_PRINT
			hr = pVideoSurfaceV->LockRect(&lockRect, NULL, 0);
			TEST_AND_PRINT
			for (int i = 0; i < gVideoHeight/2; i++)
			{
				memcpy((PBYTE)lockRect.pBits + i*lockRect.Pitch, 
					(LPVOID)((LPBYTE)srcSurfPtr + gVideoWidth*gVideoHeight + (gVideoWidth/2)*(gVideoHeight/2) + i*gVideoWidth/2), gVideoWidth/2);
			}
			hr = pVideoSurfaceV->UnlockRect();
			TEST_AND_PRINT
			// Use the pixel shader to convert the Y,U,V surfaces into an RGB surfaces which we'll then stretch blt to the
			// target surface
			hr = pD3DDevice->SetPixelShader(yuvRgbPixelShader);
			TEST_AND_PRINT
			// Set the source textures used for this
			lastTexturePtr = NULL;
			lastDiffuseTexturePtr = NULL;
			hr = pD3DDevice->SetTexture(0, pVideoTextureY);
			TEST_AND_BAIL
			hr = pD3DDevice->SetTexture(1, pVideoTextureU);
			TEST_AND_BAIL
			hr = pD3DDevice->SetTexture(2, pVideoTextureV);
			TEST_AND_BAIL
			if (g_supportsLinearMagMin)
			{
				hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_MAGFILTER, D3DTEXF_LINEAR);
				TEST_AND_PRINT
				hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_MINFILTER, D3DTEXF_LINEAR);
				TEST_AND_PRINT
				hr = SetSamplerState(pD3DDevice, 1, D3DSAMP_MAGFILTER, D3DTEXF_LINEAR);
				TEST_AND_PRINT
				hr = SetSamplerState(pD3DDevice, 1, D3DSAMP_MINFILTER, D3DTEXF_LINEAR);
				TEST_AND_PRINT
				hr = SetSamplerState(pD3DDevice, 2, D3DSAMP_MAGFILTER, D3DTEXF_LINEAR);
				TEST_AND_PRINT
				hr = SetSamplerState(pD3DDevice, 2, D3DSAMP_MINFILTER, D3DTEXF_LINEAR);
				TEST_AND_PRINT
				lastScaleHint = -1;
			}
			D3DVIEWPORT9 viewport;
			pD3DDevice->GetViewport(&viewport);
			float bufW = (float)viewport.Width;
			float bufH = (float)viewport.Height;
			float drx0 = ((destClipX)*2/bufW) - 1.0f - (0.5f/bufW);
			float dry0 = (2.0f - (destClipY)*2/bufH) - 1.0f + (0.5f/bufH);
			float drx1 = ((destClipX+destClipW)*2/bufW) - 1.0f - (0.5f/bufW);
			float dry1 = (2.0f - (destClipY+destClipH)*2/bufH) - 1.0f + (0.5f/bufH);
			float tw = (float)gVideoWidth;
			float th = (float)gVideoHeight;
			float x0 = srcClipX/ tw;
			float y0 = srcClipY/ th;
			float x1 = (srcClipX + srcClipW) / tw;
			float y1 = (srcClipY + srcClipH) / th;
			// update the two rotating vertices with the new position
			yuv_vertices[0].position = CUSTOMYUVVERTEX::Position(drx0, dry0, 0.0f); // top left
			yuv_vertices[1].position = CUSTOMYUVVERTEX::Position(drx0, dry1, 0.0f); // bottom left
			yuv_vertices[2].position = CUSTOMYUVVERTEX::Position(drx1, dry0, 0.0f); // top right
			yuv_vertices[3].position = CUSTOMYUVVERTEX::Position(drx1, dry1, 0.0f); // bottom right

			// set up texture coordinates
			yuv_vertices[0].tu1 = x0; yuv_vertices[0].tv1 = y0; // low left
			yuv_vertices[1].tu1 = x0; yuv_vertices[1].tv1 = y1; // high left
			yuv_vertices[2].tu1 = x1; yuv_vertices[2].tv1 = y0; // low right
			yuv_vertices[3].tu1 = x1; yuv_vertices[3].tv1 = y1; // high right
			yuv_vertices[0].tu2 = x0; yuv_vertices[0].tv2 = y0; // low left
			yuv_vertices[1].tu2 = x0; yuv_vertices[1].tv2 = y1; // high left
			yuv_vertices[2].tu2 = x1; yuv_vertices[2].tv2 = y0; // low right
			yuv_vertices[3].tu2 = x1; yuv_vertices[3].tv2 = y1; // high right
			yuv_vertices[0].tu3 = x0; yuv_vertices[0].tv3 = y0; // low left
			yuv_vertices[1].tu3 = x0; yuv_vertices[1].tv3 = y1; // high left
			yuv_vertices[2].tu3 = x1; yuv_vertices[2].tv3 = y0; // low right
			yuv_vertices[3].tu3 = x1; yuv_vertices[3].tv3 = y1; // high right

			// write the new vertex information into the buffer
			void* pData;
			hr = pVertexYUVBuffer->Lock(0,
				sizeof(CUSTOMYUVVERTEX)*4, &pData, D3DLOCK_NOOVERWRITE);
			TEST_AND_BAIL
			memcpy(pData,yuv_vertices,sizeof(CUSTOMYUVVERTEX)*4);
			hr = pVertexYUVBuffer->Unlock();  
			TEST_AND_BAIL

			if (lastVertexBuffer != pVertexYUVBuffer)
			{
				dx9log((env, "textureMap Changing stream source to yuv vertex buffer\r\n"));
				hr = pD3DDevice->SetStreamSource(0, pVertexYUVBuffer, 0, sizeof(CUSTOMYUVVERTEX));            //set next source ( NEW )
				TEST_AND_BAIL
				hr = pD3DDevice->SetFVF(D3DFVF_CUSTOMYUVVERTEX);
				TEST_AND_BAIL
				lastVertexBuffer = pVertexYUVBuffer;
			}
			lastBlendMode = 0;
			hr = SetRenderState(pD3DDevice, D3DRS_SRCBLEND, SRC_BLEND_ALPHA);
			TEST_AND_PRINT
			hr = SetRenderState(pD3DDevice, D3DRS_DESTBLEND, DST_BLEND_ALPHA);
			TEST_AND_PRINT
			hr = SetRenderState(pD3DDevice, D3DRS_ALPHABLENDENABLE, FALSE);
			TEST_AND_PRINT
			hr = SetRenderState(pD3DDevice, D3DRS_ALPHATESTENABLE, FALSE);
			TEST_AND_PRINT
			hr = pD3DDevice->DrawPrimitive(D3DPT_TRIANGLESTRIP, 0, 2);  //draw quad 
			TEST_AND_BAIL

			hr = SetRenderState(pD3DDevice, D3DRS_ALPHABLENDENABLE, TRUE);
			TEST_AND_PRINT
			hr = SetRenderState(pD3DDevice, D3DRS_ALPHATESTENABLE, TRUE);
			TEST_AND_PRINT
			
			hr = pD3DDevice->SetTexture(0, NULL);
			TEST_AND_BAIL
			hr = pD3DDevice->SetTexture(1, NULL);
			TEST_AND_BAIL
			hr = pD3DDevice->SetTexture(2, NULL);
			TEST_AND_BAIL

			hr = pD3DDevice->SetPixelShader(NULL);
			TEST_AND_PRINT
/*			hr = pD3DDevice->StretchRect(surface, &srcRect, pD3DRenderTarget, &destRect,
				linearVideoScale ? D3DTEXF_LINEAR : D3DTEXF_NONE);
			TEST_AND_PRINT
			if (FAILED(hr) && linearVideoScale)
			{
				slog((env, "DX9 DISABLING scaling filtering of video blit\r\n"));
				linearVideoScale = false;
				hr = pD3DDevice->StretchRect(surface, &srcRect, pD3DRenderTarget, &destRect,
					linearVideoScale ? D3DTEXF_LINEAR : D3DTEXF_NONE);
				TEST_AND_PRINT
			}*/
			SAFE_RELEASE(pVideoSurfaceY);
			SAFE_RELEASE(pVideoSurfaceU);
			SAFE_RELEASE(pVideoSurfaceV);
		}
	}
	SAFE_RELEASE(pD3DRenderTarget);
	TEST_AND_BAIL

	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return JNI_TRUE;
	JNI_RT_CATCH_RET(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    textureMap0
 * Signature: (JFFFFFFFFIIIZ)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_textureMap0
  (JNIEnv *env, jobject jo, jlong srcTexturePtr, jfloat srcClipX, jfloat srcClipY, jfloat srcClipW, jfloat srcClipH,
	jfloat destClipX, jfloat destClipY, jfloat destClipW, jfloat destClipH, jint scaleHint,
	jint compositing, jint textureColor, jboolean spritesOK)
{
	HRESULT hr;
	if ((srcTexturePtr == 0) || (srcTexturePtr == 1)) return JNI_FALSE;
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;
	IDirect3DTexture9* srcTexture;
	if (scissoredLast)
	{
		scissoredLast = false;
		SetRenderState(pD3DDevice, D3DRS_SCISSORTESTENABLE, FALSE);
	}
	if (textSpriteActive)
	{
		textSpriteActive = 0;
		gtextSprite->End();
	}
	srcTexture = (IDirect3DTexture9*) srcTexturePtr;
	if (compositing != 2)
		compositing = 0;
	if (lastBlendMode != compositing)
	{
		dx9log((env, "textureMap - changed blend state to %d\r\n", compositing));
		if (compositing == 2)
		{
			// SRC overwrites detination
			hr = SetRenderState(pD3DDevice, D3DRS_SRCBLEND, D3DBLEND_ONE);
			hr = SetRenderState(pD3DDevice, D3DRS_DESTBLEND, D3DBLEND_ZERO);
		}
		else
		{
			hr = SetRenderState(pD3DDevice, D3DRS_SRCBLEND, SRC_BLEND_ALPHA);
			hr = SetRenderState(pD3DDevice, D3DRS_DESTBLEND, DST_BLEND_ALPHA);
		}
		lastBlendMode = compositing;
	}

	if (!g_supportsLinearMagMin)
		scaleHint = 0;

	/* NOTE on texel alignment
		What are the texel alignment rules? How do I get a one-to-one mapping?
		This is explained fully in the DirectX 9 documentation. However, the executive summary is that 
		you should bias your screen coordinates by -0.5 of a pixel in order to align properly with texels. 
		Most cards now conform properly to the texel alignment rules, however there are some older cards 
		or drivers that do not. To handle these cases, the best advice is to contact the hardware vendor 
		in question and request updated drivers or their suggested workaround.
		*/

	D3DVIEWPORT9 viewport;
	pD3DDevice->GetViewport(&viewport);
	float bufW = (float)viewport.Width;
	float bufH = (float)viewport.Height;
	float drx0 = ((destClipX)*2/bufW) - 1.0f - (0.5f/bufW);
	float dry0 = (2.0f - (destClipY)*2/bufH) - 1.0f + (0.5f/bufH);
	float drx1 = ((destClipX+destClipW)*2/bufW) - 1.0f - (0.5f/bufW);
	float dry1 = (2.0f - (destClipY+destClipH)*2/bufH) - 1.0f + (0.5f/bufH);
/*	float drx0 = destClipX-0.5f;//((destClipX)*2/bufW) - 1.0f - (0.5f/bufW);
	float dry0 = destClipY+0.5f;//(2.0f - (destClipY)*2/bufH) - 1.0f + (0.5f/bufH);
	float drx1 = destClipX+destClipW-0.5f;//((destClipX+destClipW)*2/bufW) - 1.0f - (0.5f/bufW);
	float dry1 = destClipY+destClipH+0.5f;//(2.0f - (destClipY+destClipH)*2/bufH) - 1.0f + (0.5f/bufH);
*/	
	DWORD pix = textureColor & 0xFFFFFFFF;

	if (lastScaleHint != scaleHint)
	{
		dx9log((env, "texturMap Changing DX9 scaling state to %d\r\n", scaleHint));
		hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_MAGFILTER, scaleHint ? D3DTEXF_LINEAR : D3DTEXF_POINT);
		TEST_AND_PRINT
		hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_MINFILTER, scaleHint ? D3DTEXF_LINEAR : D3DTEXF_POINT);
		TEST_AND_PRINT
		lastScaleHint = scaleHint;
	}

	D3DSURFACE_DESC textDesc;
	srcTexture->GetLevelDesc(0, &textDesc);
	
	float tw = (float)textDesc.Width;
	float th = (float)textDesc.Height;
	float x0 = srcClipX/ tw;
	float y0 = srcClipY/ th;
	float x1 = (srcClipX + srcClipW) / tw;
	float y1 = (srcClipY + srcClipH) / th;

	if (lastTexturePtr != srcTexture)
	{
		dx9log((env, "textureMap Changing DX9 texture state to %d\r\n", (int)srcTexture));
		hr = pD3DDevice->SetTexture(0, srcTexture);
		TEST_AND_BAIL
		lastTexturePtr = srcTexture;
	}
	if (lastDiffuseTexturePtr)
		pD3DDevice->SetTexture(1, lastDiffuseTexturePtr = NULL);

	/*
	 * We use less memory when the don't have the vertex color information, and its probably
	 * faster...however we end up switching vertex buffers more often between the two different
	 * texture types. So we'll stick with one instead.
	 */
	
	DWORD lockFlags = D3DLOCK_NOOVERWRITE;
	if (imageAlphaVertexPos + 4 >= NUM_VERTICES)
	{
		dx9log((env, "textureMap Reallocating alpha vertex texture buffer\r\n"));
		imageAlphaVertexPos = 0;
		lockFlags = D3DLOCK_DISCARD;
	}

	if (!env->GetBooleanField(jo, fid_rerenderedDL))
	{
		dx9log((env, "textureMap writing to vertex buffer\r\n"));
		// update the two rotating vertices with the new position
		image_alpha_vertices[0].position = CUSTOMALPHATEXTUREVERTEX::Position(drx0, dry0, 0.0f); // top left
		image_alpha_vertices[1].position = CUSTOMALPHATEXTUREVERTEX::Position(drx0, dry1, 0.0f); // bottom left
		image_alpha_vertices[2].position = CUSTOMALPHATEXTUREVERTEX::Position(drx1, dry0, 0.0f); // top right
		image_alpha_vertices[3].position = CUSTOMALPHATEXTUREVERTEX::Position(drx1, dry1, 0.0f); // bottom right

		// set up diffusion:
		image_alpha_vertices[0].color = pix;
		image_alpha_vertices[1].color = pix;
		image_alpha_vertices[2].color = pix;
		image_alpha_vertices[3].color = pix;

		// set up texture coordinates
		image_alpha_vertices[0].tu = x0; image_alpha_vertices[0].tv = y0; // low left
		image_alpha_vertices[1].tu = x0; image_alpha_vertices[1].tv = y1; // high left
		image_alpha_vertices[2].tu = x1; image_alpha_vertices[2].tv = y0; // low right
		image_alpha_vertices[3].tu = x1; image_alpha_vertices[3].tv = y1; // high right

		// write the new vertex information into the buffer
		void* pData;
		hr = pVertexAlphaImageBuffer->Lock(imageAlphaVertexPos*sizeof(CUSTOMALPHATEXTUREVERTEX),
			sizeof(CUSTOMALPHATEXTUREVERTEX)*4, &pData, lockFlags);
		TEST_AND_BAIL
		memcpy(pData,image_alpha_vertices,sizeof(CUSTOMALPHATEXTUREVERTEX)*4);
		hr = pVertexAlphaImageBuffer->Unlock();  
		TEST_AND_BAIL
	}

	if (lastVertexBuffer != pVertexAlphaImageBuffer)
	{
		dx9log((env, "textureMap Changing stream source to alpha texture vertex buffer\r\n"));
		hr = pD3DDevice->SetStreamSource(0, pVertexAlphaImageBuffer, 0, sizeof(CUSTOMALPHATEXTUREVERTEX));            //set next source ( NEW )
		TEST_AND_BAIL
		hr = pD3DDevice->SetFVF(D3DFVF_CUSTOMALPHATEXTUREVERTEX);
		TEST_AND_BAIL
		lastVertexBuffer = pVertexAlphaImageBuffer;
	}
	hr = pD3DDevice->DrawPrimitive(D3DPT_TRIANGLESTRIP, imageAlphaVertexPos, 2);  //draw quad 
	TEST_AND_BAIL

	imageAlphaVertexPos += 4;
	
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return JNI_TRUE;
	JNI_RT_CATCH_RET(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    fillShape0
 * Signature: ([F[F[IIILjava/awt/Rectangle;[D)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_fillShape0___3F_3F_3IIILjava_awt_Rectangle_2_3D
  (JNIEnv *env, jobject jo, jfloatArray xpoints, jfloatArray ypoints, jintArray colors, jint triangleStrip,
  jint numVertices, jobject clipRect, jdoubleArray affineMatCoefs)
{
	HRESULT hr;
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;
	jdouble* nativeM = (jdouble*) env->GetPrimitiveArrayCritical(affineMatCoefs, NULL);
	D3DMATRIX* transformPtr;
	if (env->GetArrayLength(affineMatCoefs) == 6)
	{
		D3DMATRIX transform = {
		(float)nativeM[0], (float)nativeM[1], 0.0f,            0.0f,
		(float)nativeM[2], (float)nativeM[3], 0.0f,            0.0f,
		0.0f,			   0.0f,              1.0f,            0.0f,
		(float)nativeM[4],(float)nativeM[5], 0.0f,            1.0f
		};
		transformPtr = &transform;
	}
	else
	{
		D3DMATRIX transform = {
		(float)nativeM[0], (float)nativeM[1], (float)nativeM[2], (float)nativeM[3],
		(float)nativeM[4], (float)nativeM[5], (float)nativeM[6], (float)nativeM[7],
		(float)nativeM[8], (float)nativeM[9], (float)nativeM[10], (float)nativeM[11],
		(float)nativeM[12], (float)nativeM[13], (float)nativeM[14], (float)nativeM[15],
		};
		transformPtr = &transform;
	}
	env->ReleasePrimitiveArrayCritical(affineMatCoefs, nativeM, JNI_ABORT);
	hr = pD3DDevice->SetTransform(D3DTS_WORLDMATRIX(0), transformPtr);
	TEST_AND_PRINT
	if (lastBlendMode != 0)
	{
		dx9log((env, "fillShape changed blend mode to 0\r\n"));
		hr = SetRenderState(pD3DDevice, D3DRS_SRCBLEND, SRC_BLEND_ALPHA);
		hr = SetRenderState(pD3DDevice, D3DRS_DESTBLEND, DST_BLEND_ALPHA);
		lastBlendMode = 0;
	}
	if (textSpriteActive)
	{
		textSpriteActive = 0;
		gtextSprite->End();
	}
	if (scissoredLast && clipRect == NULL)
	{
		scissoredLast = false;
		SetRenderState(pD3DDevice, D3DRS_SCISSORTESTENABLE, FALSE);
	}
	else if (clipRect)
	{
		if (!scissoredLast)
		{
			scissoredLast = true;
			SetRenderState(pD3DDevice, D3DRS_SCISSORTESTENABLE, TRUE);
		}
		RECT scissorRect;
		scissorRect.left = env->GetIntField(clipRect, fid_rectx);
		scissorRect.top = env->GetIntField(clipRect, fid_recty);
		scissorRect.right = env->GetIntField(clipRect, fid_rectw) + scissorRect.left;
		scissorRect.bottom = env->GetIntField(clipRect, fid_recth) + scissorRect.top;
		hr = pD3DDevice->SetScissorRect(&scissorRect);
		TEST_AND_PRINT
	}

	if (!env->GetBooleanField(jo, fid_rerenderedDL))
	{
		dx9log((env, "fillShape writing to vertex buffer\r\n"));
		jfloat* nativeX = (jfloat*) env->GetPrimitiveArrayCritical(xpoints, NULL);
		jfloat* nativeY = (jfloat*) env->GetPrimitiveArrayCritical(ypoints, NULL);
		jint* nativeC = (jint*) env->GetPrimitiveArrayCritical(colors, NULL);
		for (int i = 0; i < numVertices; i++)
		{
			polygon_vertices[i].position.x = nativeX[i];
			polygon_vertices[i].position.y = nativeY[i];
			polygon_vertices[i].color = nativeC[i];
		}
		
		env->ReleasePrimitiveArrayCritical(xpoints, nativeX, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(ypoints, nativeY, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(colors, nativeC, JNI_ABORT);

		DWORD lockFlags = D3DLOCK_NOOVERWRITE;
		if (polygonVertexPos + numVertices >= NUM_POLYGON_VERTICES)
		{
			dx9log((env, "fillShape Reallocating vertex buffer for shapes\r\n"));
			polygonVertexPos = 0;
			lockFlags = D3DLOCK_DISCARD;
		}

		// write the new vertex information into the buffer
		void* pData;
		hr = pPolygonBuffer->Lock(polygonVertexPos*sizeof(CUSTOMSHAPEVERTEX),
			sizeof(CUSTOMSHAPEVERTEX)*numVertices, &pData, lockFlags);
		TEST_AND_BAIL
		memcpy(pData, polygon_vertices, sizeof(CUSTOMSHAPEVERTEX)*numVertices);
		hr = pPolygonBuffer->Unlock();  
		TEST_AND_BAIL
	}

	if (lastTexturePtr)
	{
		dx9log((env, "fillShape Clearing texture for shape rendering\r\n"));
		lastTexturePtr = NULL;
		hr = pD3DDevice->SetTexture( 0, NULL);
		TEST_AND_BAIL
	}
	if (lastDiffuseTexturePtr)
		pD3DDevice->SetTexture(1, lastDiffuseTexturePtr = NULL);
	if (lastVertexBuffer != pPolygonBuffer)
	{
		dx9log((env, "fillShape Changing stream source to shape vertex buffer\r\n"));
		hr = pD3DDevice->SetStreamSource(0, pPolygonBuffer, 0, sizeof(CUSTOMSHAPEVERTEX));            //set next source ( NEW )
		TEST_AND_BAIL
		hr = pD3DDevice->SetFVF(D3DFVF_CUSTOMSHAPEVERTEX);
		TEST_AND_BAIL
		lastVertexBuffer = pPolygonBuffer;
	}
	hr = pD3DDevice->DrawPrimitive((triangleStrip == 1) ? D3DPT_TRIANGLESTRIP : D3DPT_TRIANGLEFAN, polygonVertexPos, numVertices - 2);
	TEST_AND_BAIL

	polygonVertexPos += numVertices;

	hr = pD3DDevice->SetTransform(D3DTS_WORLDMATRIX(0), &IDENTITY_MATRIX);
	TEST_AND_PRINT
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return JNI_TRUE;
	JNI_RT_CATCH_RET(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    fillShape0
 * Signature: ([F[F[IIILjava/awt/Rectangle;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_fillShape0___3F_3F_3IIILjava_awt_Rectangle_2
  (JNIEnv *env, jobject jo, jfloatArray xpoints, jfloatArray ypoints, jintArray colors, jint triangleStrip,
  jint numVertices, jobject clipRect)
{
	HRESULT hr;
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;

	if (triangleStrip == -1)
	{
		// This is a clear scene for a rectangular area
		// DON'T forget to clear the scissor rect!
		if (scissoredLast)
		{
			scissoredLast = false;
			SetRenderState(pD3DDevice, D3DRS_SCISSORTESTENABLE, FALSE);
		}

		D3DRECT theRect;
		theRect.x1 = env->GetIntField(clipRect, fid_rectx);
		theRect.y1 = env->GetIntField(clipRect, fid_recty);
		theRect.x2 = env->GetIntField(clipRect, fid_rectw) + theRect.x1;
		theRect.y2 = env->GetIntField(clipRect, fid_recth) + theRect.y1;
		jint* nColors = env->GetIntArrayElements(colors, NULL);
		hr = pD3DDevice->Clear(1, &theRect, D3DCLEAR_TARGET, nColors[0], 0, 0);
		env->ReleaseIntArrayElements(colors, nColors, JNI_ABORT);
		TEST_AND_BAIL
		ReleaseSharedD3DDevice(env, jo, pD3DDevice);
		return JNI_TRUE;
	}
	
	if (lastBlendMode != 0)
	{
		dx9log((env, "fillShape changed blend mode to 0\r\n"));
		hr = SetRenderState(pD3DDevice, D3DRS_SRCBLEND, SRC_BLEND_ALPHA);
		hr = SetRenderState(pD3DDevice, D3DRS_DESTBLEND, DST_BLEND_ALPHA);
		lastBlendMode = 0;
	}
	if (textSpriteActive)
	{
		textSpriteActive = 0;
		gtextSprite->End();
	}
	if (scissoredLast && clipRect == NULL)
	{
		scissoredLast = false;
		SetRenderState(pD3DDevice, D3DRS_SCISSORTESTENABLE, FALSE);
	}
	else if (clipRect)
	{
		if (!scissoredLast)
		{
			scissoredLast = true;
			SetRenderState(pD3DDevice, D3DRS_SCISSORTESTENABLE, TRUE);
		}
		RECT scissorRect;
		scissorRect.left = env->GetIntField(clipRect, fid_rectx);
		scissorRect.top = env->GetIntField(clipRect, fid_recty);
		scissorRect.right = env->GetIntField(clipRect, fid_rectw) + scissorRect.left;
		scissorRect.bottom = env->GetIntField(clipRect, fid_recth) + scissorRect.top;
		hr = pD3DDevice->SetScissorRect(&scissorRect);
		TEST_AND_PRINT
	}

	if (!env->GetBooleanField(jo, fid_rerenderedDL))
	{
		D3DVIEWPORT9 viewport;
		pD3DDevice->GetViewport(&viewport);
		float bufW = (float)viewport.Width;
		float bufH = (float)viewport.Height;

		dx9log((env, "fillShape writing to vertex buffer\r\n"));
		jfloat* nativeX = (jfloat*) env->GetPrimitiveArrayCritical(xpoints, NULL);
		jfloat* nativeY = (jfloat*) env->GetPrimitiveArrayCritical(ypoints, NULL);
		jint* nativeC = (jint*) env->GetPrimitiveArrayCritical(colors, NULL);
		for (int i = 0; i < numVertices; i++)
		{
			polygon_vertices[i].position = CUSTOMSHAPEVERTEX::Position(nativeX[i]*2/bufW-1.0f-
				(0.5f/bufW), (2.0f-nativeY[i]*2/bufH) - 1.0f + (0.5f/bufH), 0.0f);
			polygon_vertices[i].color = nativeC[i];
		}
		
		env->ReleasePrimitiveArrayCritical(xpoints, nativeX, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(ypoints, nativeY, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(colors, nativeC, JNI_ABORT);

		DWORD lockFlags = D3DLOCK_NOOVERWRITE;
		if (polygonVertexPos + numVertices >= NUM_POLYGON_VERTICES)
		{
			dx9log((env, "fillShape Reallocating vertex buffer for shapes\r\n"));
			polygonVertexPos = 0;
			lockFlags = D3DLOCK_DISCARD;
		}

		// write the new vertex information into the buffer
		void* pData;
		hr = pPolygonBuffer->Lock(polygonVertexPos*sizeof(CUSTOMSHAPEVERTEX),
			sizeof(CUSTOMSHAPEVERTEX)*numVertices, &pData, lockFlags);
		TEST_AND_BAIL
		memcpy(pData, polygon_vertices, sizeof(CUSTOMSHAPEVERTEX)*numVertices);
		hr = pPolygonBuffer->Unlock();  
		TEST_AND_BAIL
	}

	if (lastTexturePtr)
	{
		dx9log((env, "fillShape Clearing texture for shape rendering\r\n"));
		lastTexturePtr = NULL;
		hr = pD3DDevice->SetTexture( 0, NULL);
		TEST_AND_BAIL
	}
	if (lastDiffuseTexturePtr)
		pD3DDevice->SetTexture(1, lastDiffuseTexturePtr = NULL);
	if (lastVertexBuffer != pPolygonBuffer)
	{
		dx9log((env, "fillShape Changing stream source to shape vertex buffer\r\n"));
		hr = pD3DDevice->SetStreamSource(0, pPolygonBuffer, 0, sizeof(CUSTOMSHAPEVERTEX));            //set next source ( NEW )
		TEST_AND_BAIL
		hr = pD3DDevice->SetFVF(D3DFVF_CUSTOMSHAPEVERTEX);
		TEST_AND_BAIL
		lastVertexBuffer = pPolygonBuffer;
	}
	hr = pD3DDevice->DrawPrimitive((triangleStrip == 1) ? D3DPT_TRIANGLESTRIP : D3DPT_TRIANGLEFAN, polygonVertexPos, numVertices - 2);
	TEST_AND_BAIL

	polygonVertexPos += numVertices;
	
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return JNI_TRUE;
	JNI_RT_CATCH_RET(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    textureMultiMap0
 * Signature: (J[F[F[F[F[F[F[F[FII[II[D)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_textureMultiMap0__J_3F_3F_3F_3F_3F_3F_3F_3FII_3II_3D
  (JNIEnv *env, jobject jo, jlong srcTexturePtr, jfloatArray srcClipX, jfloatArray srcClipY, 
	jfloatArray srcClipW, jfloatArray srcClipH, jfloatArray destClipX, jfloatArray destClipY,
	jfloatArray destClipW, jfloatArray destClipH, jint scaleHint, jint compositing, jintArray textureColor,
	jint numRects, jdoubleArray affineMatCoefs)
{
	HRESULT hr;
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;
	jdouble* nativeM = (jdouble*) env->GetPrimitiveArrayCritical(affineMatCoefs, NULL);
/*	D3DMATRIX transform = {
		(float)(2.0*nativeM[0]/bufW), 0.0f, 0.0f,            0.0f,
		0.0f,                          (float)(-2.0*nativeM[3]/bufH), 0.0f,            0.0f,
		0.0f,			   0.0f,              1.0f,            0.0f,
		(float)(((nativeM[4])*2.0/bufW)-1.0-(1.0/(2*bufW))),
		(float)(((nativeM[5])*-2.0/bufH)+1.0+(1.0/(2*bufH))), 0.0f,            1.0f
	};*/

	D3DMATRIX* transformPtr;
	if (env->GetArrayLength(affineMatCoefs) == 6)
	{
		D3DMATRIX transform = {
		(float)nativeM[0], (float)nativeM[1], 0.0f,            0.0f,
		(float)nativeM[2], (float)nativeM[3], 0.0f,            0.0f,
		0.0f,			   0.0f,              1.0f,            0.0f,
		(float)nativeM[4],(float)nativeM[5], 0.0f,            1.0f
		};
		transformPtr = &transform;
	}
	else
	{
		D3DMATRIX transform = {
		(float)nativeM[0], (float)nativeM[1], (float)nativeM[2], (float)nativeM[3],
		(float)nativeM[4], (float)nativeM[5], (float)nativeM[6], (float)nativeM[7],
		(float)nativeM[8], (float)nativeM[9], (float)nativeM[10], (float)nativeM[11],
		(float)nativeM[12]-0.5f, (float)nativeM[13]+0.5f, (float)nativeM[14], (float)nativeM[15],
		};
		transformPtr = &transform;
	}
	env->ReleasePrimitiveArrayCritical(affineMatCoefs, nativeM, JNI_ABORT);
	hr = pD3DDevice->SetTransform(D3DTS_WORLDMATRIX(0), transformPtr);
	TEST_AND_PRINT

	IDirect3DTexture9* srcTexture;
	
	dx9log((env, "textureMultiMap doing %d rects at once\r\n", numRects));
	int numVertices = numRects * 6;
	if (!g_supportsLinearMagMin)
		scaleHint = 0;

	if (textSpriteActive)
	{
		textSpriteActive = 0;
		gtextSprite->End();
	}
	if (lastScaleHint != scaleHint)
	{
		dx9log((env, "textureMultiMap Changing DX9 scaling state tp %d\r\n", scaleHint));
		hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_MAGFILTER, scaleHint ? D3DTEXF_LINEAR : D3DTEXF_POINT);
		TEST_AND_PRINT
		hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_MINFILTER, scaleHint ? D3DTEXF_LINEAR : D3DTEXF_POINT);
		TEST_AND_PRINT
		lastScaleHint = scaleHint;
	}

	if (scissoredLast)
	{
		scissoredLast = false;
		SetRenderState(pD3DDevice, D3DRS_SCISSORTESTENABLE, FALSE);
	}

	srcTexture = (IDirect3DTexture9*) srcTexturePtr;

	if (lastTexturePtr != srcTexture)
	{
		dx9log((env, "textureMultiMap Changing DX9 texture state to %d\r\n", (int)srcTexture));
		hr = pD3DDevice->SetTexture(0, srcTexture);
		TEST_AND_BAIL
		lastTexturePtr = srcTexture;
	}
	if (lastDiffuseTexturePtr)
		pD3DDevice->SetTexture(1, lastDiffuseTexturePtr = NULL);

	if (lastBlendMode != 0)
	{
		dx9log((env, "textureMultiMap changed blend mode to 0\r\n"));
		hr = SetRenderState(pD3DDevice, D3DRS_SRCBLEND, SRC_BLEND_ALPHA);
		hr = SetRenderState(pD3DDevice, D3DRS_DESTBLEND, DST_BLEND_ALPHA);
		lastBlendMode = 0;
	}

	if (!env->GetBooleanField(jo, fid_rerenderedDL))
	{
		D3DSURFACE_DESC textDesc;
		srcTexture->GetLevelDesc(0, &textDesc);
		
		float tw = (float)textDesc.Width;
		float th = (float)textDesc.Height;

		dx9log((env, "textureMultiMap writing to vertex buffer\r\n"));
		jfloat* nsx = (jfloat*) env->GetPrimitiveArrayCritical(srcClipX, NULL);
		jfloat* nsy = (jfloat*) env->GetPrimitiveArrayCritical(srcClipY, NULL);
		jfloat* nsw = (jfloat*) env->GetPrimitiveArrayCritical(srcClipW, NULL);
		jfloat* nsh = (jfloat*) env->GetPrimitiveArrayCritical(srcClipH, NULL);
		jfloat* ndx = (jfloat*) env->GetPrimitiveArrayCritical(destClipX, NULL);
		jfloat* ndy = (jfloat*) env->GetPrimitiveArrayCritical(destClipY, NULL);
		jfloat* ndw = (jfloat*) env->GetPrimitiveArrayCritical(destClipW, NULL);
		jfloat* ndh = (jfloat*) env->GetPrimitiveArrayCritical(destClipH, NULL);
		jint* nativeC = (jint*) env->GetPrimitiveArrayCritical(textureColor, NULL);

		for (int i = 0; i < numRects; i++)
		{
			float drx0 = ndx[i];
			float dry0 = ndy[i];
			float drx1 = ndx[i] + ndw[i];
			float dry1 = ndy[i] + ndh[i];
			
			DWORD pix = nativeC[i] & 0xFFFFFFFF;

			float x0 = nsx[i]/ tw;
			float y0 = nsy[i]/ th;
			float x1 = (nsx[i] + nsw[i]) / tw;
			float y1 = (nsy[i] + nsh[i]) / th;

			image_alpha_vertices[i*6].position = CUSTOMALPHATEXTUREVERTEX::Position(drx0, dry0, 0.0f); // top left
			image_alpha_vertices[i*6+1].position = CUSTOMALPHATEXTUREVERTEX::Position(drx1, dry1, 0.0f); // bottom right
			image_alpha_vertices[i*6+2].position = CUSTOMALPHATEXTUREVERTEX::Position(drx0, dry1, 0.0f); // bottom left
			image_alpha_vertices[i*6+3].position = CUSTOMALPHATEXTUREVERTEX::Position(drx1, dry0, 0.0f); // top right
			image_alpha_vertices[i*6+4].position = CUSTOMALPHATEXTUREVERTEX::Position(drx1, dry1, 0.0f); // bottom right
			image_alpha_vertices[i*6+5].position = CUSTOMALPHATEXTUREVERTEX::Position(drx0, dry0, 0.0f); // top left

			// set up diffusion:
			image_alpha_vertices[i*6].color = pix;
			image_alpha_vertices[i*6+1].color = pix;
			image_alpha_vertices[i*6+2].color = pix;
			image_alpha_vertices[i*6+3].color = pix;
			image_alpha_vertices[i*6+4].color = pix;
			image_alpha_vertices[i*6+5].color = pix;

			// set up texture coordinates
			image_alpha_vertices[i*6].tu = x0; image_alpha_vertices[i*6].tv = y0; // top left
			image_alpha_vertices[i*6+1].tu = x1; image_alpha_vertices[i*6+1].tv = y1; // bottom right
			image_alpha_vertices[i*6+2].tu = x0; image_alpha_vertices[i*6+2].tv = y1; // bottom left
			image_alpha_vertices[i*6+3].tu = x1; image_alpha_vertices[i*6+3].tv = y0; // top right
			image_alpha_vertices[i*6+4].tu = x1; image_alpha_vertices[i*6+4].tv = y1; // bottom right
			image_alpha_vertices[i*6+5].tu = x0; image_alpha_vertices[i*6+5].tv = y0; // top left
		}
		
		env->ReleasePrimitiveArrayCritical(srcClipX, nsx, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(srcClipY, nsy, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(srcClipW, nsw, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(srcClipH, nsh, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(destClipX, ndx, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(destClipY, ndy, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(destClipW, ndw, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(destClipH, ndh, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(textureColor, nativeC, JNI_ABORT);

		DWORD lockFlags = D3DLOCK_NOOVERWRITE;
		if (imageAlphaVertexPos + numVertices >= NUM_VERTICES)
		{
			dx9log((env, "textureMultiMap Reallocating vertex buffer for shapes\r\n"));
			imageAlphaVertexPos = 0;
			lockFlags = D3DLOCK_DISCARD;
		}

		// write the new vertex information into the buffer
		void* pData;
		hr = pVertexAlphaImageBuffer->Lock(imageAlphaVertexPos*sizeof(CUSTOMALPHATEXTUREVERTEX),
			sizeof(CUSTOMALPHATEXTUREVERTEX)*numVertices, &pData, lockFlags);
		TEST_AND_BAIL
		memcpy(pData, image_alpha_vertices, sizeof(CUSTOMALPHATEXTUREVERTEX)*numVertices);
		hr = pVertexAlphaImageBuffer->Unlock();  
		TEST_AND_BAIL
	}

	if (lastVertexBuffer != pVertexAlphaImageBuffer)
	{
		dx9log((env, "textureMultiMap Changing stream source to image alpha vertex buffer\r\n"));
		hr = pD3DDevice->SetStreamSource(0, pVertexAlphaImageBuffer, 0, sizeof(CUSTOMALPHATEXTUREVERTEX));            //set next source ( NEW )
		TEST_AND_BAIL
		hr = pD3DDevice->SetFVF(D3DFVF_CUSTOMALPHATEXTUREVERTEX);
		TEST_AND_BAIL
		lastVertexBuffer = pVertexAlphaImageBuffer;
	}
	hr = pD3DDevice->DrawPrimitive(D3DPT_TRIANGLELIST, imageAlphaVertexPos, numRects * 2);
	TEST_AND_BAIL

	imageAlphaVertexPos += numVertices;
	
	hr = pD3DDevice->SetTransform(D3DTS_WORLDMATRIX(0), &IDENTITY_MATRIX);
	TEST_AND_PRINT
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return JNI_TRUE;
	JNI_RT_CATCH_RET(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    textureDiffuseMap0
 * Signature: (JJFFFFFFFFFFFFIII[D)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_textureDiffuseMap0
  (JNIEnv *env, jobject jo, jlong srcTexturePtr, jlong diffuseTexturePtr, jfloat srcClipX, jfloat srcClipY, 
	jfloat srcClipW, jfloat srcClipH, jfloat diffuseSrcX, jfloat diffuseSrcY, jfloat diffuseSrcW,
	jfloat diffuseSrcH, jfloat destClipX, jfloat destClipY,
	jfloat destClipW, jfloat destClipH, jint scaleHint, jint compositing, jint textureColor,
	jdoubleArray affineMatCoefs)
{
	HRESULT hr;
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;
	if (affineMatCoefs)
	{
		jdouble* nativeM = (jdouble*) env->GetPrimitiveArrayCritical(affineMatCoefs, NULL);
	/*	D3DMATRIX transform = {
			(float)(2.0*nativeM[0]/bufW), 0.0f, 0.0f,            0.0f,
			0.0f,                          (float)(-2.0*nativeM[3]/bufH), 0.0f,            0.0f,
			0.0f,			   0.0f,              1.0f,            0.0f,
			(float)(((nativeM[4])*2.0/bufW)-1.0-(1.0/(2*bufW))),
			(float)(((nativeM[5])*-2.0/bufH)+1.0+(1.0/(2*bufH))), 0.0f,            1.0f
		};*/

		D3DMATRIX* transformPtr;
		if (env->GetArrayLength(affineMatCoefs) == 6)
		{
			D3DMATRIX transform = {
			(float)nativeM[0], (float)nativeM[1], 0.0f,            0.0f,
			(float)nativeM[2], (float)nativeM[3], 0.0f,            0.0f,
			0.0f,			   0.0f,              1.0f,            0.0f,
			(float)nativeM[4],(float)nativeM[5], 0.0f,            1.0f
			};
			transformPtr = &transform;
		}
		else
		{
			D3DMATRIX transform = {
			(float)nativeM[0], (float)nativeM[1], (float)nativeM[2], (float)nativeM[3],
			(float)nativeM[4], (float)nativeM[5], (float)nativeM[6], (float)nativeM[7],
			(float)nativeM[8], (float)nativeM[9], (float)nativeM[10], (float)nativeM[11],
			(float)nativeM[12]-0.5f, (float)nativeM[13]+0.5f, (float)nativeM[14], (float)nativeM[15],
			};
			transformPtr = &transform;
		}
		env->ReleasePrimitiveArrayCritical(affineMatCoefs, nativeM, JNI_ABORT);
		hr = pD3DDevice->SetTransform(D3DTS_WORLDMATRIX(0), transformPtr);
		TEST_AND_PRINT
	}

	IDirect3DTexture9* srcTexture;
	IDirect3DTexture9* diffuseTexture;
	
	int numVertices = 6;
	if (!g_supportsLinearMagMin)
		scaleHint = 0;

	if (textSpriteActive)
	{
		textSpriteActive = 0;
		gtextSprite->End();
	}
	if (lastScaleHint != scaleHint)
	{
		dx9log((env, "textureDiffuseMap Changing DX9 scaling state tp %d\r\n", scaleHint));
		hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_MAGFILTER, scaleHint ? D3DTEXF_LINEAR : D3DTEXF_POINT);
		TEST_AND_PRINT
		hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_MINFILTER, scaleHint ? D3DTEXF_LINEAR : D3DTEXF_POINT);
		TEST_AND_PRINT
		lastScaleHint = scaleHint;
	}

	if (scissoredLast)
	{
		scissoredLast = false;
		SetRenderState(pD3DDevice, D3DRS_SCISSORTESTENABLE, FALSE);
	}

	srcTexture = (IDirect3DTexture9*) srcTexturePtr;
	diffuseTexture = (IDirect3DTexture9*) diffuseTexturePtr;

	if (lastTexturePtr != srcTexture)
	{
		dx9log((env, "textureDiffuseMap Changing DX9 texture state to %d\r\n", (int)srcTexture));
		hr = pD3DDevice->SetTexture(0, srcTexture);
		TEST_AND_BAIL
		lastTexturePtr = srcTexture;
	}

	if (lastDiffuseTexturePtr != diffuseTexture)
	{
		dx9log((env, "textureDiffuseMap Changing DX9 diffuse texture state to %d\r\n", (int)diffuseTexture));
		hr = pD3DDevice->SetTexture(1, diffuseTexture);
		TEST_AND_BAIL
		lastDiffuseTexturePtr = diffuseTexture;
	}

	if (lastBlendMode != 0)
	{
		dx9log((env, "textureDiffuseMap changed blend mode to 0\r\n"));
		hr = SetRenderState(pD3DDevice, D3DRS_SRCBLEND, SRC_BLEND_ALPHA);
		hr = SetRenderState(pD3DDevice, D3DRS_DESTBLEND, DST_BLEND_ALPHA);
		lastBlendMode = 0;
	}

	if (!env->GetBooleanField(jo, fid_rerenderedDL))
	{
		dx9log((env, "textureDiffuseMap writing to vertex buffer\r\n"));

		float drx0 = destClipX;
		float dry0 = destClipY;
		float drx1 = destClipX + destClipW;
		float dry1 = destClipY + destClipH;
		if (!affineMatCoefs)
		{
			D3DVIEWPORT9 viewport;
			pD3DDevice->GetViewport(&viewport);
			float bufW = (float)viewport.Width;
			float bufH = (float)viewport.Height;
			drx0 = ((destClipX)*2/bufW) - 1.0f - (0.5f/bufW);
			dry0 = (2.0f - (destClipY)*2/bufH) - 1.0f + (0.5f/bufH);
			drx1 = ((destClipX+destClipW)*2/bufW) - 1.0f - (0.5f/bufW);
			dry1 = (2.0f - (destClipY+destClipH)*2/bufH) - 1.0f + (0.5f/bufH);
		}
		
		DWORD pix = textureColor & 0xFFFFFFFF;

		D3DSURFACE_DESC textDesc;
		srcTexture->GetLevelDesc(0, &textDesc);
		
		float tw = (float)textDesc.Width;
		float th = (float)textDesc.Height;

		float x0 = srcClipX/ tw;
		float y0 = srcClipY/ th;
		float x1 = (srcClipX + srcClipW) / tw;
		float y1 = (srcClipY + srcClipH) / th;

		image_alpha2_vertices[0].position = CUSTOMALPHATEXTURE2VERTEX::Position(drx0, dry0, 0.0f); // top left
		image_alpha2_vertices[1].position = CUSTOMALPHATEXTURE2VERTEX::Position(drx1, dry1, 0.0f); // bottom right
		image_alpha2_vertices[2].position = CUSTOMALPHATEXTURE2VERTEX::Position(drx0, dry1, 0.0f); // bottom left
		image_alpha2_vertices[3].position = CUSTOMALPHATEXTURE2VERTEX::Position(drx1, dry0, 0.0f); // top right
		image_alpha2_vertices[4].position = CUSTOMALPHATEXTURE2VERTEX::Position(drx1, dry1, 0.0f); // bottom right
		image_alpha2_vertices[5].position = CUSTOMALPHATEXTURE2VERTEX::Position(drx0, dry0, 0.0f); // top left

		// set up diffusion:
		image_alpha2_vertices[0].color = pix;
		image_alpha2_vertices[1].color = pix;
		image_alpha2_vertices[2].color = pix;
		image_alpha2_vertices[3].color = pix;
		image_alpha2_vertices[4].color = pix;
		image_alpha2_vertices[5].color = pix;

		// set up texture coordinates
		image_alpha2_vertices[0].tu1 = x0; image_alpha2_vertices[0].tv1 = y0; // top left
		image_alpha2_vertices[1].tu1 = x1; image_alpha2_vertices[1].tv1 = y1; // bottom right
		image_alpha2_vertices[2].tu1 = x0; image_alpha2_vertices[2].tv1 = y1; // bottom left
		image_alpha2_vertices[3].tu1 = x1; image_alpha2_vertices[3].tv1 = y0; // top right
		image_alpha2_vertices[4].tu1 = x1; image_alpha2_vertices[4].tv1 = y1; // bottom right
		image_alpha2_vertices[5].tu1 = x0; image_alpha2_vertices[5].tv1 = y0; // top left
		
		diffuseTexture->GetLevelDesc(0, &textDesc);
		
		tw = (float)textDesc.Width;
		th = (float)textDesc.Height;

		x0 = diffuseSrcX/ tw;
		y0 = diffuseSrcY/ th;
		x1 = (diffuseSrcX + diffuseSrcW) / tw;
		y1 = (diffuseSrcY + diffuseSrcH) / th;

		// set up diffuse texture coordinates
		image_alpha2_vertices[0].tu2 = x0; image_alpha2_vertices[0].tv2 = y0; // top left
		image_alpha2_vertices[1].tu2 = x1; image_alpha2_vertices[1].tv2 = y1; // bottom right
		image_alpha2_vertices[2].tu2 = x0; image_alpha2_vertices[2].tv2 = y1; // bottom left
		image_alpha2_vertices[3].tu2 = x1; image_alpha2_vertices[3].tv2 = y0; // top right
		image_alpha2_vertices[4].tu2 = x1; image_alpha2_vertices[4].tv2 = y1; // bottom right
		image_alpha2_vertices[5].tu2 = x0; image_alpha2_vertices[5].tv2 = y0; // top left
		
		DWORD lockFlags = D3DLOCK_NOOVERWRITE;
		if (imageAlpha2VertexPos + numVertices >= NUM_VERTICES)
		{
			dx9log((env, "textureDiffuseMap Reallocating vertex buffer for shapes\r\n"));
			imageAlpha2VertexPos = 0;
			lockFlags = D3DLOCK_DISCARD;
		}

		// write the new vertex information into the buffer
		void* pData;
		hr = pVertexAlpha2ImageBuffer->Lock(imageAlpha2VertexPos*sizeof(CUSTOMALPHATEXTURE2VERTEX),
			sizeof(CUSTOMALPHATEXTURE2VERTEX)*numVertices, &pData, lockFlags);
		TEST_AND_BAIL
		memcpy(pData, image_alpha2_vertices, sizeof(CUSTOMALPHATEXTURE2VERTEX)*numVertices);
		hr = pVertexAlpha2ImageBuffer->Unlock();  
		TEST_AND_BAIL
	}

	if (lastVertexBuffer != pVertexAlpha2ImageBuffer)
	{
		dx9log((env, "textureDiffuseMap Changing stream source to image alpha2 vertex buffer\r\n"));
		hr = pD3DDevice->SetStreamSource(0, pVertexAlpha2ImageBuffer, 0, sizeof(CUSTOMALPHATEXTURE2VERTEX));            //set next source ( NEW )
		TEST_AND_BAIL
		hr = pD3DDevice->SetFVF(D3DFVF_CUSTOMALPHATEXTURE2VERTEX);
		TEST_AND_BAIL
		lastVertexBuffer = pVertexAlpha2ImageBuffer;
	}
	hr = pD3DDevice->DrawPrimitive(D3DPT_TRIANGLELIST, imageAlpha2VertexPos, 2);
	TEST_AND_BAIL

	imageAlpha2VertexPos += numVertices;
	
	hr = pD3DDevice->SetTransform(D3DTS_WORLDMATRIX(0), &IDENTITY_MATRIX);
	TEST_AND_PRINT
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return JNI_TRUE;
	JNI_RT_CATCH_RET(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    textureMultiMap0
 * Signature: (J[F[F[F[F[F[F[F[FII[II)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_textureMultiMap0__J_3F_3F_3F_3F_3F_3F_3F_3FII_3II
  (JNIEnv *env, jobject jo, jlong srcTexturePtr, jfloatArray srcClipX, jfloatArray srcClipY, 
	jfloatArray srcClipW, jfloatArray srcClipH, jfloatArray destClipX, jfloatArray destClipY,
	jfloatArray destClipW, jfloatArray destClipH, jint scaleHint, jint compositing, jintArray textureColor,
	jint numRects)

{
	HRESULT hr;
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;
	IDirect3DTexture9* srcTexture;
	
	dx9log((env, "textureMultiMap doing %d rects at once\r\n", numRects));
	int numVertices = numRects * 6;
	if (!g_supportsLinearMagMin)
		scaleHint = 0;

	if (textSpriteActive)
	{
		textSpriteActive = 0;
		gtextSprite->End();
	}
	if (lastScaleHint != scaleHint)
	{
		dx9log((env, "textureMultiMap Changing DX9 scaling state tp %d\r\n", scaleHint));
		hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_MAGFILTER, scaleHint ? D3DTEXF_LINEAR : D3DTEXF_POINT);
		TEST_AND_PRINT
		hr = SetSamplerState(pD3DDevice, 0, D3DSAMP_MINFILTER, scaleHint ? D3DTEXF_LINEAR : D3DTEXF_POINT);
		TEST_AND_PRINT
		lastScaleHint = scaleHint;
	}

	if (scissoredLast)
	{
		scissoredLast = false;
		SetRenderState(pD3DDevice, D3DRS_SCISSORTESTENABLE, FALSE);
	}

	srcTexture = (IDirect3DTexture9*) srcTexturePtr;

	if (lastTexturePtr != srcTexture)
	{
		dx9log((env, "textureMultiMap Changing DX9 texture state to %d\r\n", (int)srcTexture));
		hr = pD3DDevice->SetTexture(0, srcTexture);
		TEST_AND_BAIL
		lastTexturePtr = srcTexture;
	}
	if (lastDiffuseTexturePtr)
		pD3DDevice->SetTexture(1, lastDiffuseTexturePtr = NULL);

	if (lastBlendMode != 0)
	{
		dx9log((env, "textureMultiMap changed blend mode to 0\r\n"));
		hr = SetRenderState(pD3DDevice, D3DRS_SRCBLEND, SRC_BLEND_ALPHA);
		hr = SetRenderState(pD3DDevice, D3DRS_DESTBLEND, DST_BLEND_ALPHA);
		lastBlendMode = 0;
	}

	if (!env->GetBooleanField(jo, fid_rerenderedDL))
	{
		D3DVIEWPORT9 viewport;
		pD3DDevice->GetViewport(&viewport);
		float bufW = (float)viewport.Width;
		float bufH = (float)viewport.Height;

		D3DSURFACE_DESC textDesc;
		srcTexture->GetLevelDesc(0, &textDesc);
		
		float tw = (float)textDesc.Width;
		float th = (float)textDesc.Height;

		dx9log((env, "textureMultiMap writing to vertex buffer\r\n"));
		jfloat* nsx = (jfloat*) env->GetPrimitiveArrayCritical(srcClipX, NULL);
		jfloat* nsy = (jfloat*) env->GetPrimitiveArrayCritical(srcClipY, NULL);
		jfloat* nsw = (jfloat*) env->GetPrimitiveArrayCritical(srcClipW, NULL);
		jfloat* nsh = (jfloat*) env->GetPrimitiveArrayCritical(srcClipH, NULL);
		jfloat* ndx = (jfloat*) env->GetPrimitiveArrayCritical(destClipX, NULL);
		jfloat* ndy = (jfloat*) env->GetPrimitiveArrayCritical(destClipY, NULL);
		jfloat* ndw = (jfloat*) env->GetPrimitiveArrayCritical(destClipW, NULL);
		jfloat* ndh = (jfloat*) env->GetPrimitiveArrayCritical(destClipH, NULL);
		jint* nativeC = (jint*) env->GetPrimitiveArrayCritical(textureColor, NULL);

		for (int i = 0; i < numRects; i++)
		{
			float drx0 = ((ndx[i])*2/bufW) - 1.0f - (0.5f/bufW);
			float dry0 = (2.0f - (ndy[i])*2/bufH) - 1.0f + (0.5f/bufH);
			float drx1 = ((ndx[i]+ndw[i])*2/bufW) - 1.0f - (0.5f/bufW);
			float dry1 = (2.0f - (ndy[i]+ndh[i])*2/bufH) - 1.0f + (0.5f/bufH);
			
			DWORD pix = nativeC[i] & 0xFFFFFFFF;

			float x0 = nsx[i]/ tw;
			float y0 = nsy[i]/ th;
			float x1 = (nsx[i] + nsw[i]) / tw;
			float y1 = (nsy[i] + nsh[i]) / th;

			image_alpha_vertices[i*6].position = CUSTOMALPHATEXTUREVERTEX::Position(drx0, dry0, 0.0f); // top left
			image_alpha_vertices[i*6+1].position = CUSTOMALPHATEXTUREVERTEX::Position(drx1, dry0, 0.0f); // top right
			image_alpha_vertices[i*6+2].position = CUSTOMALPHATEXTUREVERTEX::Position(drx0, dry1, 0.0f); // bottom left
			image_alpha_vertices[i*6+3].position = CUSTOMALPHATEXTUREVERTEX::Position(drx0, dry1, 0.0f); // bottom left
			image_alpha_vertices[i*6+4].position = CUSTOMALPHATEXTUREVERTEX::Position(drx1, dry0, 0.0f); // top right
			image_alpha_vertices[i*6+5].position = CUSTOMALPHATEXTUREVERTEX::Position(drx1, dry1, 0.0f); // bottom right

			// set up diffusion:
			image_alpha_vertices[i*6].color = pix;
			image_alpha_vertices[i*6+1].color = pix;
			image_alpha_vertices[i*6+2].color = pix;
			image_alpha_vertices[i*6+3].color = pix;
			image_alpha_vertices[i*6+4].color = pix;
			image_alpha_vertices[i*6+5].color = pix;

			// set up texture coordinates
			image_alpha_vertices[i*6].tu = x0; image_alpha_vertices[i*6].tv = y0; // top left
			image_alpha_vertices[i*6+1].tu = x1; image_alpha_vertices[i*6+1].tv = y0; // top right
			image_alpha_vertices[i*6+2].tu = x0; image_alpha_vertices[i*6+2].tv = y1; // bottom left
			image_alpha_vertices[i*6+3].tu = x0; image_alpha_vertices[i*6+3].tv = y1; // bottom left
			image_alpha_vertices[i*6+4].tu = x1; image_alpha_vertices[i*6+4].tv = y0; // top right
			image_alpha_vertices[i*6+5].tu = x1; image_alpha_vertices[i*6+5].tv = y1; // bottom right
		}
		
		env->ReleasePrimitiveArrayCritical(srcClipX, nsx, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(srcClipY, nsy, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(srcClipW, nsw, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(srcClipH, nsh, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(destClipX, ndx, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(destClipY, ndy, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(destClipW, ndw, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(destClipH, ndh, JNI_ABORT);
		env->ReleasePrimitiveArrayCritical(textureColor, nativeC, JNI_ABORT);

		DWORD lockFlags = D3DLOCK_NOOVERWRITE;
		if (imageAlphaVertexPos + numVertices >= NUM_VERTICES)
		{
			dx9log((env, "textureMultiMap Reallocating vertex buffer for shapes\r\n"));
			imageAlphaVertexPos = 0;
			lockFlags = D3DLOCK_DISCARD;
		}

		// write the new vertex information into the buffer
		void* pData;
		hr = pVertexAlphaImageBuffer->Lock(imageAlphaVertexPos*sizeof(CUSTOMALPHATEXTUREVERTEX),
			sizeof(CUSTOMALPHATEXTUREVERTEX)*numVertices, &pData, lockFlags);
		TEST_AND_BAIL
		memcpy(pData, image_alpha_vertices, sizeof(CUSTOMALPHATEXTUREVERTEX)*numVertices);
		hr = pVertexAlphaImageBuffer->Unlock();  
		TEST_AND_BAIL
	}

	if (lastVertexBuffer != pVertexAlphaImageBuffer)
	{
		dx9log((env, "textureMultiMap Changing stream source to image alpha vertex buffer\r\n"));
		hr = pD3DDevice->SetStreamSource(0, pVertexAlphaImageBuffer, 0, sizeof(CUSTOMALPHATEXTUREVERTEX));            //set next source ( NEW )
		TEST_AND_BAIL
		hr = pD3DDevice->SetFVF(D3DFVF_CUSTOMALPHATEXTUREVERTEX);
		TEST_AND_BAIL
		lastVertexBuffer = pVertexAlphaImageBuffer;
	}
	hr = pD3DDevice->DrawPrimitive(D3DPT_TRIANGLELIST, imageAlphaVertexPos, numRects * 2);
	TEST_AND_BAIL

	imageAlphaVertexPos += numVertices;
	
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return JNI_TRUE;
	JNI_RT_CATCH_RET(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    getVideoSnapshot0
 * Signature: (JII[I)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_getVideoSnapshot0
  (JNIEnv *env, jobject jo, jlong videoSurfacePtr, jint videoWidth, jint videoHeight,
	jintArray jimageData)
{
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;
	HRESULT hr;
	if (!pSnapshotSurf)
	{
		hr = pD3DDevice->CreateOffscreenPlainSurface(videoWidth, videoHeight, D3DFMT_X8R8G8B8,
			D3DPOOL_SYSTEMMEM, &pSnapshotSurf, NULL);
		TEST_AND_BAIL
	}

	hr = D3DXLoadSurfaceFromSurface(pSnapshotSurf, NULL, NULL, (IDirect3DSurface9*) videoSurfacePtr,
		NULL, NULL, D3DX_FILTER_NONE, 0);
	TEST_AND_BAIL

	D3DLOCKED_RECT lockedRect;
	jint* nativeImageData = (jint*) env->GetPrimitiveArrayCritical(jimageData, NULL);
	hr = pSnapshotSurf->LockRect(&lockedRect, NULL, D3DLOCK_READONLY);
	if (lockedRect.Pitch != 4*videoWidth)
	{
// NOTE: WE SHOULD FIX THIS SO ITS NOT A PROBLEM
		pSnapshotSurf->UnlockRect();
		hr = -1;
		TEST_AND_BAIL
	}
	memcpy(nativeImageData, lockedRect.pBits, 4*videoWidth*videoHeight);
	pSnapshotSurf->UnlockRect();
	env->ReleasePrimitiveArrayCritical(jimageData, nativeImageData, 0);
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return JNI_TRUE;
	JNI_RT_CATCH_RET(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    getAvailableVideoMemory0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_DirectX9SageRenderer_getAvailableVideoMemory0
  (JNIEnv *env, jobject jo)
{
	JNI_TRY;
	IDirect3DDevice9* pD3DDevice = (IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return 0;
	return (jlong)pD3DDevice->GetAvailableTextureMem();
	JNI_RT_CATCH_RET_NODEV(0);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    getMaximumTextureDimension0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sage_DirectX9SageRenderer_getMaximumTextureDimension0
  (JNIEnv *env, jobject jo)
{
	JNI_TRY;
	IDirect3DDevice9* pD3DDevice = (IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return 0;
    D3DCAPS9 d3dcaps;
    pD3DDevice->GetDeviceCaps( &d3dcaps );
	slog((env, "DX9 max texture dimensions is %dx%d\r\n", d3dcaps.MaxTextureWidth, d3dcaps.MaxTextureHeight));
	if (d3dcaps.MaxTextureHeight <= d3dcaps.MaxTextureWidth)
		return d3dcaps.MaxTextureHeight;
	else
		return d3dcaps.MaxTextureWidth;
	JNI_RT_CATCH_RET_NODEV(0);
}

BOOL APIENTRY DllMain( HANDLE hModule, 
                       DWORD  ul_reason_for_call, 
                       LPVOID lpReserved
					 )
{
    switch (ul_reason_for_call)
	{
		case DLL_PROCESS_ATTACH:
		case DLL_THREAD_ATTACH:
		case DLL_THREAD_DETACH:
		case DLL_PROCESS_DETACH:
			break;
    }
    return TRUE;
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    create3DFont0
 * Signature: (Ljava/lang/String;IZZ)J
 */
JNIEXPORT jlong JNICALL Java_sage_DirectX9SageRenderer_create3DFont0
  (JNIEnv *env, jobject jo, jstring jfont, jint fontSize, jboolean bold, jboolean italic)
{
	HRESULT hr;
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;

	// Create the DX9 Font Object
	ID3DXFont *myFont = NULL;
	const jchar* cfontName = env->GetStringChars(jfont, NULL);
	hr = D3DXCreateFontW(pD3DDevice, -fontSize, 0, bold ? FW_BOLD : 0, 1, italic,
		DEFAULT_CHARSET, OUT_TT_ONLY_PRECIS, DEFAULT_QUALITY, DEFAULT_PITCH | FF_DONTCARE,
		reinterpret_cast<LPCWSTR>(cfontName), &myFont);
	env->ReleaseStringChars(jfont, cfontName);
	TEST_AND_BAIL
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return (jlong) myFont;
	JNI_RT_CATCH_RET(JNI_FALSE);
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    free3DFont0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sage_DirectX9SageRenderer_free3DFont0
  (JNIEnv *env, jobject jo, jlong fontPtr)
{
	if (fontPtr)
	{
		((ID3DXFont*) fontPtr)->Release();
	}
}

/*
 * Class:     sage_DirectX9SageRenderer
 * Method:    renderText0
 * Signature: (Ljava/lang/String;Ljava/lang/String;IZZFFFFI)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_DirectX9SageRenderer_renderText0
  (JNIEnv *env, jobject jo, jstring jtext, jlong fontPtr,
	jfloat textX, jfloat textY, jfloat textWidth, jfloat textHeight, jint textColor)
{
	HRESULT hr;
	IDirect3DDevice9* pD3DDevice = GetSharedD3DDevice(env, jo);//(IDirect3DDevice9*) env->GetLongField(jo, fid_pD3DDevice);
	if (!pD3DDevice) return JNI_FALSE;
	JNI_TRY;

	ID3DXFont *myFont = (ID3DXFont*) fontPtr;

	dx9log((env, "DX9 renderText\r\n"));
	/*if (!gTextSprite)
	{
		hr = D3DXCreateSprite(pD3DDevice, &gTextSprite);
		TEST_AND_PRINT
	}*/
	if (scissoredLast)
	{
		scissoredLast = false;
		SetRenderState(pD3DDevice, D3DRS_SCISSORTESTENABLE, FALSE);
	}

	if (lastBlendMode != 0)
	{
		dx9log((env, "renderText0 changed blend mode to 0\r\n"));
		hr = SetRenderState(pD3DDevice, D3DRS_SRCBLEND, SRC_BLEND_ALPHA);
		hr = SetRenderState(pD3DDevice, D3DRS_DESTBLEND, DST_BLEND_ALPHA);
		lastBlendMode = 0;
	}
	if (lastTexturePtr)
	{
		dx9log((env, "renderText0 Clearing texture for text rendering\r\n"));
		lastTexturePtr = NULL;
		hr = pD3DDevice->SetTexture( 0, NULL);
		TEST_AND_BAIL
	}
	if (lastDiffuseTexturePtr)
		pD3DDevice->SetTexture(1, lastDiffuseTexturePtr = NULL);
/* Don't use Sprites, they just don't work right
	if (textSpriteActive != fontPtr)
	{
		if (!gtextSprite)
		{
			hr = D3DXCreateSprite(pD3DDevice, &gtextSprite);
			TEST_AND_PRINT
		}
		if (gtextSprite)
		{
			if (textSpriteActive)
				gtextSprite->End();
			textSpriteActive = fontPtr;
			gtextSprite->Begin(D3DXSPRITE_ALPHABLEND | D3DXSPRITE_SORT_TEXTURE);
		}
	}
*/
	const jchar* uniText = env->GetStringChars(jtext, NULL);
	RECT rc;
	rc.bottom = (int) (textY + textHeight);
	rc.top = (int) textY;
	rc.left = (int) textX;
	rc.right = (int) (textX + textWidth);
	myFont->DrawTextW(NULL, reinterpret_cast<LPCWSTR>(uniText), -1, &rc, DT_NOCLIP, textColor);
	env->ReleaseStringChars(jtext, uniText);
	ReleaseSharedD3DDevice(env, jo, pD3DDevice);
	return JNI_TRUE;
	JNI_RT_CATCH_RET(JNI_FALSE);
}

static JNINativeMethod methods[] = {
    {"initDX9SageRenderer0", 
     "(IIJ)Z",
     (void *)&Java_sage_DirectX9SageRenderer_initDX9SageRenderer0},
    {"cleanupDX9SageRenderer0", 
     "()V",
     (void *)&Java_sage_DirectX9SageRenderer_cleanupDX9SageRenderer0},
    {"createD3DTextureFromMemory0", 
     "(IILjava/nio/ByteBuffer;)J",
     (void *)&Java_sage_DirectX9SageRenderer_createD3DTextureFromMemory0__IILjava_nio_ByteBuffer_2},
    {"freeD3DTexturePointer0", 
     "(J)V",
     (void *)&Java_sage_DirectX9SageRenderer_freeD3DTexturePointer0},
    {"present0", 
     "(IIII)Z",
     (void *)&Java_sage_DirectX9SageRenderer_present0},
    {"clearScene0", 
     "(Z)V",
     (void *)&Java_sage_DirectX9SageRenderer_clearScene0},
    {"beginScene0", 
     "(II)Z",
     (void *)&Java_sage_DirectX9SageRenderer_beginScene0},
    {"endScene0", 
     "()V",
     (void *)&Java_sage_DirectX9SageRenderer_endScene0},
    {"stretchBlt0", 
     "(JIIIIJIIIII)Z",
     (void *)&Java_sage_DirectX9SageRenderer_stretchBlt0},
    {"textureMap0", 
     "(JFFFFFFFFIIIZ)Z",
     (void *)&Java_sage_DirectX9SageRenderer_textureMap0},
    {"textureMultiMap0", 
     "(J[F[F[F[F[F[F[F[FII[II[D)Z",
     (void *)&Java_sage_DirectX9SageRenderer_textureMultiMap0__J_3F_3F_3F_3F_3F_3F_3F_3FII_3II_3D},
    {"textureDiffuseMap0", 
     "(JJFFFFFFFFFFFFIII[D)Z",
     (void *)&Java_sage_DirectX9SageRenderer_textureDiffuseMap0},
    {"fillShape0", 
     "([F[F[IIILjava/awt/Rectangle;)Z",
     (void *)&Java_sage_DirectX9SageRenderer_fillShape0___3F_3F_3IIILjava_awt_Rectangle_2},
    {"fillShape0", 
     "([F[F[IIILjava/awt/Rectangle;[D)Z",
     (void *)&Java_sage_DirectX9SageRenderer_fillShape0___3F_3F_3IIILjava_awt_Rectangle_2_3D},
    {"getAvailableVideoMemory0", 
     "()J",
     (void *)&Java_sage_DirectX9SageRenderer_getAvailableVideoMemory0},
    {"createD3DRenderTarget0", 
     "(II)J",
     (void *)&Java_sage_DirectX9SageRenderer_createD3DRenderTarget0},
    {"setRenderTarget0", 
     "(J)Z",
     (void *)&Java_sage_DirectX9SageRenderer_setRenderTarget0},
    {"asyncVideoRender0", 
     "(Ljava/lang/String;)V",
     (void *)&Java_sage_DirectX9SageRenderer_asyncVideoRender0},
};
/*
 * Class:     sage_miniclient_DirectX9GFXCMD
 * Method:    registerMiniClientNatives0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_miniclient_DirectX9GFXCMD_registerMiniClientNatives0
  (JNIEnv *env, jclass jc)
{
	env->RegisterNatives(jc, methods, 18);
}

