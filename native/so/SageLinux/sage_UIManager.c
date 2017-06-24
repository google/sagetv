/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <jni.h>
#include <jni_md.h>
#include "sage_UIManager.h"
#include "sage_UIUtils.h"
#ifdef WITH_X11
#include <jawt_md.h>
#include <X11/Xmd.h>
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <X11/Xatom.h>
#endif

static void sysOutPrint(JNIEnv *env, const char* cstr, ...) 
{
    static jclass cls=NULL;
    static jfieldID outField=NULL;
    static jmethodID printMeth=NULL;
    if(env == NULL) return;
    jthrowable oldExcept = (*env)->ExceptionOccurred(env);
    if (oldExcept)
        (*env)->ExceptionClear(env);
    va_list args;
    va_start(args, cstr);
    char buf[1024*2];
    vsnprintf(buf, sizeof(buf), cstr, args);
    va_end(args);
    jstring jstr = (*env)->NewStringUTF(env, buf);
    if(cls==NULL)
    {
        cls = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/System"));
        outField = (*env)->GetStaticFieldID(env, cls, "out", "Ljava/io/PrintStream;");
        printMeth = (*env)->GetMethodID(env, (*env)->FindClass(env, "java/io/PrintStream"),
        "print", "(Ljava/lang/String;)V");
    }
    jobject outObj = (*env)->GetStaticObjectField(env, cls, outField);
    (*env)->CallVoidMethod(env, outObj, printMeth, jstr);
    (*env)->DeleteLocalRef(env, jstr);
    if (oldExcept)
        (*env)->Throw(env, oldExcept);
}

/*
 * Class:     sage_UIManager
 * Method:    setCompoundWindowRegion
 * Signature: (I[Ljava/awt/Rectangle;[IZ)V
 */
JNIEXPORT void JNICALL Java_sage_UIManager_setCompoundWindowRegion(JNIEnv *env, jclass jc,
																   jlong winID, jobjectArray jrects, jintArray roundness,
																   jboolean dontRepaint)
{
}

/*
 * Class:     sage_UIManager
 * Method:    clearWindowRegion
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sage_UIManager_clearWindowRegion(JNIEnv *env, jclass jc, jlong winID)
{
}

/*
 * Class:     sage_UIManager
 * Method:    sendMessage
 * Signature: (III)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_UIManager_sendMessage(
	JNIEnv *env, jclass jc, jlong winID, jint msgID, jint msgData)
{
	return JNI_TRUE;
}

/*
 * Class:     sage_UIManager
 * Method:    findWindow
 * Signature: (Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jlong JNICALL Java_sage_UIManager_findWindow(
	JNIEnv *env, jclass jc, jstring jname, jstring jwinclass)
{
	return 0;
}

/*
 * Class:     sage_UIUtils
 * Method:    getHWND
 * Signature: (Ljava/awt/Canvas;)J
 */
JNIEXPORT jlong JNICALL Java_sage_UIUtils_getHWND
  (JNIEnv *env, jclass jc, jobject canvas)
{
#ifdef WITH_X11
    JAWT awt;
    JAWT_DrawingSurface* ds;
    JAWT_DrawingSurfaceInfo* dsi;
    JAWT_X11DrawingSurfaceInfo* dsi_x11;
    jboolean result;
    jint lock;
    GC gc;

    sysOutPrint(env, "getHWND\n");
    awt.version = JAWT_VERSION_1_3;
    if (JAWT_GetAWT(env, &awt) == JNI_FALSE) {
        sysOutPrint(env, "AWT Not found\n");
        return 0;
    }

    ds = awt.GetDrawingSurface(env, canvas);
    if (ds == NULL) {
        sysOutPrint(env, "NULL drawing surface\n");
        return 0;
    }

    lock = ds->Lock(ds);
    if((lock & JAWT_LOCK_ERROR) != 0) {
        sysOutPrint(env, "Error locking surface\n");
        awt.FreeDrawingSurface(ds);
        return 0;
    }

    dsi = ds->GetDrawingSurfaceInfo(ds);
    if (dsi == NULL) {
        sysOutPrint(env, "Error getting surface info\n");
        awt.FreeDrawingSurface(ds);
        return 0;
    }

    dsi_x11 = (JAWT_X11DrawingSurfaceInfo*)dsi->platformInfo;

    jlong rv = (jlong) dsi_x11->drawable;
    sysOutPrint(env, "WindowID=%d\n", rv);

    ds->FreeDrawingSurfaceInfo(dsi);

    ds->Unlock(ds);

    awt.FreeDrawingSurface(ds);

    return rv;
#else
    return 0;
#endif
}

JNIEXPORT jlong JNICALL Java_sage_UIUtils_setFullScreenMode
  (JNIEnv *env, jclass jc, jobject canvas, jboolean fullscreen)
{
#ifdef WITH_X11
    JAWT awt;
    JAWT_DrawingSurface* ds;
    JAWT_DrawingSurfaceInfo* dsi;
    JAWT_X11DrawingSurfaceInfo* dsi_x11;
    jboolean result;
    jint lock;
    GC gc;
    Display *dpy;
    Window win;
    Window parent;
    Window root;
    Window *children;
    int nchildren;
    jlong rv=0;
    
    awt.version = JAWT_VERSION_1_3;
    if (JAWT_GetAWT(env, &awt) == JNI_FALSE) {
        sysOutPrint(env, "AWT Not found\n");
        return 0;
    }

    ds = awt.GetDrawingSurface(env, canvas);
    if (ds == NULL) {
        sysOutPrint(env, "NULL drawing surface\n");
        return 0;
    }

    lock = ds->Lock(ds);
    if((lock & JAWT_LOCK_ERROR) != 0) {
        sysOutPrint(env, "Error locking surface\n");
        awt.FreeDrawingSurface(ds);
        return 0;
    }

    dsi = ds->GetDrawingSurfaceInfo(ds);
    if (dsi == NULL) {
        sysOutPrint(env, "Error getting surface info\n");
        awt.FreeDrawingSurface(ds);
        return 0;
    }

    dsi_x11 = (JAWT_X11DrawingSurfaceInfo*)dsi->platformInfo;

    win = dsi_x11->drawable;
    dpy = dsi_x11->display;
    
    //printf("Trying to set fullscreen mode %d to drawable %d\n",fullscreen, win);
    while(root!=parent)
    {
        XQueryTree(dpy, win, &root, &parent, &children, &nchildren);
        if(children!=NULL)
        {
            XFree(children);
            children=NULL;
            nchildren=0;
        }
        //printf("Parent: %d Root: %d\n",parent, root);
        if(root!=parent)
        {
            win=parent;
            Atom _NET_WM_STATE;
            int _NET_WM_STATE_REMOVE;
            int _NET_WM_STATE_ADD;
            Atom _NET_WM_STATE_FULLSCREEN;
            
            _NET_WM_STATE = XInternAtom(dpy, "_NET_WM_STATE", True);
            _NET_WM_STATE_REMOVE = 0;
            _NET_WM_STATE_ADD = 1;
            _NET_WM_STATE_FULLSCREEN = XInternAtom(dpy, "_NET_WM_STATE_FULLSCREEN", True);
            
            //printf("Atoms: %d %d %d %d\n",_NET_WM_STATE,
            //    _NET_WM_STATE_REMOVE,_NET_WM_STATE_ADD,_NET_WM_STATE_FULLSCREEN);
            if ( _NET_WM_STATE != None ) 
            {
                //printf("Sending fullscreen event %d for window %d\n",
                //    fullscreen ? _NET_WM_STATE_ADD : _NET_WM_STATE_REMOVE,
                //    win);
                XEvent e;
                e.xany.type = ClientMessage;
                e.xany.window = win;
                e.xclient.message_type = _NET_WM_STATE;
                e.xclient.format = 32;
                e.xclient.data.l[0] = fullscreen ? _NET_WM_STATE_ADD : _NET_WM_STATE_REMOVE;
                e.xclient.data.l[1] = (long)_NET_WM_STATE_FULLSCREEN;
                e.xclient.data.l[2] = (long)0;
                e.xclient.data.l[3] = (long)0;
                e.xclient.data.l[4] = (long)0;
                XSendEvent(dpy, root, 0,
                    SubstructureNotifyMask|SubstructureRedirectMask, &e);
            }
        }
    }
        
        
    ds->FreeDrawingSurfaceInfo(dsi);

    ds->Unlock(ds);

    awt.FreeDrawingSurface(ds);

    return rv;
#else
    return 0;
#endif
}
