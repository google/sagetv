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
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <unistd.h>
#include <signal.h>
#include <float.h>
#include <math.h>

#include <unistd.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <linux/fb.h>
#include <sys/time.h>

#include "../utils/MiniImage.h"
#include "../thread_util.h"

#include "GLgfx.h"

#define DEBUGRENDER

void GFX_SetMode(int mode);

static long long get_timebase()
{
  struct timeval tm;
  gettimeofday(&tm, 0);
  return tm.tv_sec * 1000000LL + (tm.tv_usec);
}

#include "../logo.h"

extern ACL_mutex *MediaCmdMutex;

int GFX_flipBuffer();

GLRenderContext_t * RC=NULL;

inline unsigned int FlipRGBA (unsigned int ToFlip) {
    return ((ToFlip&0xFF000000)>>8)|((ToFlip&0x00FF0000)>>8)|
        ((ToFlip&0x0000FF00)>>8)|((ToFlip&0x000000FF)<<24);
}

#define VERTEX_ARRAY 0
#define COLOR_ARRAY 1
#define COORD_ARRAY 2

float IdentityMatrix[] =
{
    1.0f,0.0f,0.0f,0.0f,
    0.0f,1.0f,0.0f,0.0f,
    0.0f,0.0f,1.0f,0.0f,
    0.0f,0.0f,0.0f,1.0f
};

const char* baseFragShaderSrc = "\
    varying mediump vec4 myColorOut;\
    void main(void)\
    {\
        gl_FragColor = myColorOut;\
    }";

const char* baseVertShaderSrc = "\
    attribute highp vec4 myVertex;\
    attribute mediump vec4 myColor;\
    uniform mediump mat4 myPMVMatrix;\
    varying mediump vec4 myColorOut;\
    void main(void)\
    {\
        gl_Position = myPMVMatrix * myVertex;\
        myColorOut = myColor;\
    }";

const char* baseTexturedFragShaderSrc = "\
    uniform sampler2D sampler2d;\
    varying mediump vec2 myTexCoord;\
    varying mediump vec4 myColorOut;\
    void main (void)\
    {\
        gl_FragColor = myColorOut * texture2D(sampler2d,myTexCoord);\
    }";

const char* baseTexturedVertShaderSrc = "\
    attribute highp vec4 myVertex;\
    attribute mediump vec4 myUV;\
    attribute mediump vec4 myColor;\
    uniform mediump mat4 myPMVMatrix;\
    varying mediump vec2 myTexCoord;\
    varying mediump vec4 myColorOut;\
    void main(void)\
    {\
        gl_Position = myPMVMatrix * myVertex;\
        myTexCoord = myUV.st;\
        myColorOut = myColor;\
    }";

GLuint baseFragShader=0, baseVertShader=0;
GLuint baseProgram=0;
GLint baseProgramPMVLocation=0;
GLuint baseTexturedFragShader=0, baseTexturedVertShader=0;
GLuint baseTexturedProgram=0;
GLint baseTexturedProgramPMVLocation=0;

GLuint currentProgram;
/*    // Actually use the created program
    glUseProgram(uiProgramObject);*/


GLuint CompileShader(GLenum type, const char *program)
{
    GLuint Shader = glCreateShader(type);
    glShaderSource(Shader, 1, (const char**)&program, NULL);
    glCompileShader(Shader);
    GLint bShaderCompiled;
    glGetShaderiv(Shader, GL_COMPILE_STATUS, &bShaderCompiled);
    if (!bShaderCompiled)
    {
        // An error happened, first retrieve the length of the log message
        int i32InfoLogLength, i32CharsWritten;
        glGetShaderiv(Shader, GL_INFO_LOG_LENGTH, &i32InfoLogLength);

        // Allocate enough space for the message and retrieve it
        char* pszInfoLog = malloc(i32InfoLogLength);
        if(pszInfoLog!=NULL)
        {
            glGetShaderInfoLog(Shader, i32InfoLogLength, &i32CharsWritten, pszInfoLog);

            fprintf(stderr, "Failed to compile fragment shader: %s\n", pszInfoLog);
            free(pszInfoLog);
        }
        return 0;
    }
    return Shader;
}

// TODO: improve error cases
int CreateShaders()
{
    fprintf(stderr,"Building base shaders\n");
    baseFragShader=CompileShader(GL_FRAGMENT_SHADER, baseFragShaderSrc);
    baseVertShader=CompileShader(GL_VERTEX_SHADER, baseVertShaderSrc);

    baseProgram = glCreateProgram();
    glAttachShader(baseProgram, baseFragShader);
    glAttachShader(baseProgram, baseVertShader);

    // Bind the custom vertex attribute "myVertex" to location VERTEX_ARRAY
    glBindAttribLocation(baseProgram, VERTEX_ARRAY, "myVertex");
    glBindAttribLocation(baseProgram, COLOR_ARRAY, "myColor");

    // Link the program
    glLinkProgram(baseProgram);

    // Check if linking succeeded in the same way we checked for compilation success
    GLint bLinked;
    glGetProgramiv(baseProgram, GL_LINK_STATUS, &bLinked);

    if (!bLinked)
    {
        int ui32InfoLogLength, ui32CharsWritten;
        glGetProgramiv(baseProgram, GL_INFO_LOG_LENGTH, &ui32InfoLogLength);
        char* pszInfoLog = malloc(ui32InfoLogLength);
        glGetProgramInfoLog(baseProgram, ui32InfoLogLength, &ui32CharsWritten, pszInfoLog);
        fprintf(stderr, "Failed to link program: %s\n", pszInfoLog);
        free(pszInfoLog);
        return 0;
    }

    baseProgramPMVLocation=glGetUniformLocation(baseProgram, "myPMVMatrix");

    fprintf(stderr,"Building textured shaders\n");

    baseTexturedFragShader=CompileShader(GL_FRAGMENT_SHADER, baseTexturedFragShaderSrc);
    baseTexturedVertShader=CompileShader(GL_VERTEX_SHADER, baseTexturedVertShaderSrc);

    baseTexturedProgram = glCreateProgram();
    glAttachShader(baseTexturedProgram, baseTexturedFragShader);
    glAttachShader(baseTexturedProgram, baseTexturedVertShader);

    // Bind the custom vertex attribute "myVertex" to location VERTEX_ARRAY
    glBindAttribLocation(baseTexturedProgram, VERTEX_ARRAY, "myVertex");
    glBindAttribLocation(baseTexturedProgram, COLOR_ARRAY, "myColor");
    glBindAttribLocation(baseTexturedProgram, COORD_ARRAY, "myUV");

    // Link the program
    glLinkProgram(baseTexturedProgram);

    // Check if linking succeeded in the same way we checked for compilation success
    glGetProgramiv(baseTexturedProgram, GL_LINK_STATUS, &bLinked);

    if (!bLinked)
    {
        int ui32InfoLogLength, ui32CharsWritten;
        glGetProgramiv(baseTexturedProgram, GL_INFO_LOG_LENGTH, &ui32InfoLogLength);
        char* pszInfoLog = malloc(ui32InfoLogLength);
        glGetProgramInfoLog(baseTexturedProgram, ui32InfoLogLength, &ui32CharsWritten, pszInfoLog);
        fprintf(stderr, "Failed to link program: %s\n", pszInfoLog);
        free(pszInfoLog);
        return 0;
    }

    baseTexturedProgramPMVLocation=glGetUniformLocation(baseProgram, "myPMVMatrix");

    return 1;
}


// 0: base 1: baseTextured
int SetShaders(int mode)
{
    if(mode!=RC->currentShader)
    {
        switch(mode)
        {
            case 1:
                glUseProgram(baseProgram);
                RC->matrixLocation=baseProgramPMVLocation;
                break;
            case 2:
                glUseProgram(baseTexturedProgram);
                RC->matrixLocation=baseTexturedProgramPMVLocation;
                break;
            default:
                fprintf(stderr, "Unknown shader %d\n",mode);
                return -1;
        }
        RC->currentShader=mode;
    }
    return 0;
}

int SetShaderParams()
{
    glUniformMatrix4fv( RC->matrixLocation, 1, GL_FALSE, RC->currentViewMatrix);
    return 0;
}

#define WINDOW_WIDTH 1280
#define WINDOW_HEIGHT 720

int TestEGLError(const char* pszLocation)
{
    EGLint iErr = eglGetError();
    if (iErr != EGL_SUCCESS)
    {
        fprintf(stderr, "%s failed (%d).\n", pszLocation, iErr);
        return 0;
    }

    return 1;
}

#ifdef GLX11
#include <X11/Xlib.h>
#include <X11/Xutil.h>


typedef struct
{
    Window x11Window;
    Display* x11Display;
    long x11Screen;
    XVisualInfo x11Visual;
    Colormap x11Colormap;
}nativeStateX11;


int CreateNativeWindow(GLRenderContext_t *RC)
{
    Window sRootWindow;
    XSetWindowAttributes sWA;
    unsigned int ui32Mask;
    int i32Depth;

    RC->nativeState = (void *) malloc(sizeof(nativeStateX11));
    if(RC->nativeState == NULL)
    {
        fprintf(stderr,"Failed allocating native state\n");
        return 0;
    }

    ((nativeStateX11 *)RC->nativeState)->x11Display = XOpenDisplay(0);
    if (!((nativeStateX11 *)RC->nativeState)->x11Display)
    {
        fprintf(stderr, "Unable to open X display\n");
        goto cleanup;
    }

    ((nativeStateX11 *)RC->nativeState)->x11Screen = 
        XDefaultScreen( ((nativeStateX11 *)RC->nativeState)->x11Display );

    // Gets the window parameters
    sRootWindow = RootWindow(((nativeStateX11 *)RC->nativeState)->x11Display, 
        ((nativeStateX11 *)RC->nativeState)->x11Screen);
    i32Depth = DefaultDepth(((nativeStateX11 *)RC->nativeState)->x11Display, 
        ((nativeStateX11 *)RC->nativeState)->x11Screen);
    if (!XMatchVisualInfo( ((nativeStateX11 *)RC->nativeState)->x11Display, 
        ((nativeStateX11 *)RC->nativeState)->x11Screen, i32Depth, TrueColor, 
            &((nativeStateX11 *)RC->nativeState)->x11Visual))
    {
        fprintf(stderr, "Unable to match visual\n");
        goto cleanup;
    }

    ((nativeStateX11 *)RC->nativeState)->x11Colormap = 
        XCreateColormap( ((nativeStateX11 *)RC->nativeState)->x11Display, sRootWindow, 
        ((nativeStateX11 *)RC->nativeState)->x11Visual.visual, AllocNone );
    sWA.colormap = ((nativeStateX11 *)RC->nativeState)->x11Colormap;

    // Add to these for handling other events
    sWA.event_mask = StructureNotifyMask | ExposureMask | ButtonPressMask | ButtonReleaseMask | KeyPressMask | KeyReleaseMask;
    ui32Mask = CWBackPixel | CWBorderPixel | CWEventMask | CWColormap;

    // Creates the X11 window
    ((nativeStateX11 *)RC->nativeState)->x11Window = XCreateWindow( 
        ((nativeStateX11 *)RC->nativeState)->x11Display, 
        RootWindow(((nativeStateX11 *)RC->nativeState)->x11Display, 
            ((nativeStateX11 *)RC->nativeState)->x11Screen), 
        0, 0, WINDOW_WIDTH, WINDOW_HEIGHT,
        0, CopyFromParent, InputOutput, CopyFromParent, ui32Mask, &sWA);
    XMapWindow(((nativeStateX11 *)RC->nativeState)->x11Display, 
        ((nativeStateX11 *)RC->nativeState)->x11Window);
    XFlush(((nativeStateX11 *)RC->nativeState)->x11Display);
    RC->nativeDisplay=(EGLNativeDisplayType) ((nativeStateX11 *)RC->nativeState)->x11Display;
    RC->nativeWindow=(EGLNativeWindowType) ((nativeStateX11 *)RC->nativeState)->x11Window;
    return 1;
cleanup:
    if(((nativeStateX11 *)RC->nativeState)->x11Window)
        XDestroyWindow(((nativeStateX11 *)RC->nativeState)->x11Display, 
        ((nativeStateX11 *)RC->nativeState)->x11Window);
    if(((nativeStateX11 *)RC->nativeState)->x11Colormap)
        XFreeColormap( ((nativeStateX11 *)RC->nativeState)->x11Display, 
        ((nativeStateX11 *)RC->nativeState)->x11Colormap);
    if(((nativeStateX11 *)RC->nativeState)->x11Display)
        XCloseDisplay(((nativeStateX11 *)RC->nativeState)->x11Display);
    free(RC->nativeState);
    RC->nativeState=NULL;
    return 0;
}

DestroyNativeWindow()
{
    if(((nativeStateX11 *)RC->nativeState)->x11Window)
        XDestroyWindow(((nativeStateX11 *)RC->nativeState)->x11Display, 
        ((nativeStateX11 *)RC->nativeState)->x11Window);
    if(((nativeStateX11 *)RC->nativeState)->x11Colormap)
        XFreeColormap( ((nativeStateX11 *)RC->nativeState)->x11Display, 
        ((nativeStateX11 *)RC->nativeState)->x11Colormap);
    if(((nativeStateX11 *)RC->nativeState)->x11Display)
        XCloseDisplay(((nativeStateX11 *)RC->nativeState)->x11Display);
    free(RC->nativeState);
    RC->nativeState=NULL;
}
#else
#ifdef GLGDL

#include "libgdl.h"

CreateNativeWindow(GLRenderContext_t *RC)
{
    gdl_plane_id_t plane = GDL_PLANE_ID_UPP_C;

    gdl_pixel_format_t pix_fmt = GDL_PF_ARGB_32;
    gdl_color_space_t color_space = GDL_COLOR_SPACE_RGB;
    gdl_rectangle_t src_rect;
    gdl_rectangle_t dst_rect;
    gdl_ret_t rc;

    gdl_init(0);

    dst_rect.origin.x = 0;
    dst_rect.origin.y = 0;
    dst_rect.width = WINDOW_WIDTH;
    dst_rect.height = WINDOW_HEIGHT;

    src_rect.origin.x = 0;
    src_rect.origin.y = 0;
    src_rect.width = WINDOW_WIDTH;
    src_rect.height = WINDOW_HEIGHT;
    rc = gdl_plane_config_begin(plane);
    if (GDL_SUCCESS != rc)
    {
        printf("gdl_plane_config_begin failed!\n");
        return;
    }

    rc = gdl_plane_set_attr(GDL_PLANE_SRC_COLOR_SPACE, &color_space);
    if (GDL_SUCCESS != rc)
    {
        printf("gdl_plane_set_attr(GDL_PLANE_SRC_COLOR_SPACE) failed!\n");
        return;
    }

    rc = gdl_plane_set_attr(GDL_PLANE_PIXEL_FORMAT, &pix_fmt);
    if (GDL_SUCCESS != rc)
    {
       printf("gdl_plane_set_attr(GDL_PLANE_PIXEL_FORMAT) failed!\n");
        return;
    }

    rc = gdl_plane_set_attr(GDL_PLANE_DST_RECT, &dst_rect);
    if (GDL_SUCCESS != rc)
    {
        printf("gdl_plane_set_attr(GDL_PLANE_DEST_RECT) failed!\n");
        return;
    }

    rc = gdl_plane_set_attr(GDL_PLANE_SRC_RECT, &src_rect);
    if (GDL_SUCCESS != rc)
    {
        printf("gdl_plane_set_attr(GDL_PLANE_SRC_RECT) failed!\n");
        return;
    }

    rc = gdl_plane_config_end(GDL_FALSE);
    if (GDL_SUCCESS != rc)
    {
        printf("gdl_plane_config_end failed!\n");
        return;
    }

    RC->nativeDisplay=(EGLNativeDisplayType) EGL_DEFAULT_DISPLAY;
    RC->nativeWindow=(EGLNativeWindowType) plane;
}

DestroyNativeWindow()
{

}

#else
You must specify the rendering system (X11,GDL, ...)
#endif // GLGDL
#endif // GLX11


int CreateSurface(GLuint *frameBuffer, GLuint *frameTexture, GLuint width, GLuint height)
{
    if(*frameTexture!=0)
    {
        glDeleteTextures(1, frameTexture);
    }
    if(*frameBuffer!=0)
    {
        glDeleteFramebuffers(1, frameBuffer);
    }
    glGenFramebuffers(1, frameBuffer);
    glGenTextures(1, frameTexture);
    // Binds this texture handle so we can load the data into it
    glBindTexture(GL_TEXTURE_2D, *frameTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 
        WINDOW_WIDTH, WINDOW_HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindFramebuffer(GL_FRAMEBUFFER, *frameBuffer);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, *frameTexture, 0);
    glClearColor(0, 0, 0, 0);
    glClear(GL_COLOR_BUFFER_BIT);
    glBindFramebuffer(GL_FRAMEBUFFER, RC->currentBuffer);
    return 1;
}

int SetSurface(GLuint frameBuffer, GLuint frameTexture, GLuint width, GLuint height)
{
    RC->currentViewMatrix[0]=2.0f/width;
    RC->currentViewMatrix[1]=0.0f;
    RC->currentViewMatrix[2]=0.0f;
    RC->currentViewMatrix[3]=0.0f;
    RC->currentViewMatrix[4]=0.0f;
    RC->currentViewMatrix[5]=2.0f/height;
    RC->currentViewMatrix[6]=0.0f;
    RC->currentViewMatrix[7]=0.0f;
    RC->currentViewMatrix[8]=0.0f;
    RC->currentViewMatrix[9]=0.0f;
    RC->currentViewMatrix[10]=1.0f;
    RC->currentViewMatrix[11]=0.0f;
    RC->currentViewMatrix[12]=-1.0f;
    RC->currentViewMatrix[13]=-1.0f;
    RC->currentViewMatrix[14]=0.0f;
    RC->currentViewMatrix[15]=1.0f;
    RC->currentBuffer=frameBuffer;
    glBindFramebuffer(GL_FRAMEBUFFER, RC->currentBuffer);
    return 1;
}



int GFX_init()
{
    int rtn = 0;
    int i,j;
    int error;
    GLRenderContext_t* RenderContext=NULL;

#ifdef DEBUGRENDER
    fprintf(stderr, "Initialising GL Renderer\n");
    fflush(stdout);
#endif

    RenderContext = (GLRenderContext_t *) malloc(sizeof(GLRenderContext_t));

    if(RenderContext == NULL)
    {
        rtn=0;
        goto GFX_end;
    }

    memset(RenderContext, 0, sizeof(GLRenderContext_t));

    fprintf(stderr, "Before CreativeNativeWindow\n");
    CreateNativeWindow(RenderContext);
    fprintf(stderr, "After CreativeNativeWindow\n");

    EGLint ai32ContextAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };

    RenderContext->eglDisplay = eglGetDisplay((EGLNativeDisplayType)RenderContext->nativeDisplay);
    fprintf(stderr, "Display is %X\n");
    EGLint iMajorVersion, iMinorVersion;
    if (!eglInitialize(RenderContext->eglDisplay, &iMajorVersion, &iMinorVersion))
    {
        fprintf(stderr, "eglInitialize() failed.\n");
        goto cleanup;
    }

    eglBindAPI(EGL_OPENGL_ES_API);

    if (!TestEGLError("eglBindAPI"))
    {
        goto cleanup;
    }

    EGLint pi32ConfigAttribs[15];
    pi32ConfigAttribs[0] = EGL_SURFACE_TYPE;
    pi32ConfigAttribs[1] = EGL_WINDOW_BIT;
    pi32ConfigAttribs[2] = EGL_RENDERABLE_TYPE;
    pi32ConfigAttribs[3] = EGL_OPENGL_ES2_BIT;	
    pi32ConfigAttribs[4] = EGL_COLOR_BUFFER_TYPE;
    pi32ConfigAttribs[5] = EGL_RGB_BUFFER;
    pi32ConfigAttribs[6] = EGL_RED_SIZE;
    pi32ConfigAttribs[7] = 8;
    pi32ConfigAttribs[8] = EGL_GREEN_SIZE;
    pi32ConfigAttribs[9] = 8;
    pi32ConfigAttribs[10] = EGL_BLUE_SIZE;
    pi32ConfigAttribs[11] = 8;
    pi32ConfigAttribs[12] = EGL_ALPHA_SIZE;
    pi32ConfigAttribs[13] = 8;
    pi32ConfigAttribs[14] = EGL_NONE;

    int iConfigs;
    if (!eglChooseConfig(RenderContext->eglDisplay, pi32ConfigAttribs, 
        &RenderContext->eglConfig, 1, &iConfigs) || (iConfigs != 1))
    {
        fprintf(stderr, "eglChooseConfig() failed.\n");
        goto cleanup;
    }
    if (!TestEGLError("eglChooseConfig"))
    {
        goto cleanup;
    }

    RenderContext->eglSurface = eglCreateWindowSurface(RenderContext->eglDisplay, 
        RenderContext->eglConfig, (EGLNativeWindowType)RenderContext->nativeWindow, NULL);

    if (!TestEGLError("eglCreateWindowSurface"))
    {
        goto cleanup;
    }

    RenderContext->eglContext = eglCreateContext(RenderContext->eglDisplay, 
        RenderContext->eglConfig, NULL, ai32ContextAttribs);
    if (!TestEGLError("eglCreateContext"))
    {
        goto cleanup;
    }

    eglMakeCurrent(RenderContext->eglDisplay, RenderContext->eglSurface, 
        RenderContext->eglSurface, RenderContext->eglContext);
    if (!TestEGLError("eglMakeCurrent"))
    {
        goto cleanup;
    }
    RenderContext->GFXMutex = ACL_CreateMutex();

    RC=RenderContext;
    ACL_LockMutex(RC->GFXMutex);

    fprintf(stderr,"Vendor: %s\nRenderer: %s\nVersion: %s\nExtensions: %s\n",
        glGetString(GL_VENDOR),glGetString(GL_RENDERER),
        glGetString(GL_VERSION),glGetString(GL_EXTENSIONS));

    CreateShaders();
    glClearColor(0, 0, 0.5f, 0);
    glClear(GL_COLOR_BUFFER_BIT);

    RC->mainWidth=WINDOW_WIDTH;
    RC->mainHeight=WINDOW_HEIGHT;
    // We need to create a render framebuffer since after swap the current buffer status is undefined...
    CreateSurface(&RC->mainBuffer, &RC->mainTexture, RC->mainWidth, RC->mainHeight);
    SetSurface(RC->mainBuffer, RC->mainTexture, RC->mainWidth, RC->mainHeight);

    // Small test...
    glUseProgram(baseProgram);
    int i32Location = glGetUniformLocation(baseProgram, "myPMVMatrix");
    float viewMatrix[] =
    {
        2.0f/WINDOW_WIDTH,0.0f,0.0f,0.0f,
        0.0f,2.0f/WINDOW_HEIGHT,0.0f,0.0f,
        0.0f,0.0f,1.0f,0.0f,
        -1.0f,-1.0f,0.0f,1.0f
    };
    glUniformMatrix4fv( i32Location, 1, GL_FALSE, viewMatrix);

    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

    // Vertex position (x,y,z)
    GLshort pVertices[] = {80, 400, 640, 400, 360, 80};
    GLuint pColors[] = {0x000000FF, 0xFF0000FF, 0x80FF0000 };
    glEnableVertexAttribArray(VERTEX_ARRAY);
    glEnableVertexAttribArray(COLOR_ARRAY);

    // Sets the vertex data to this attribute index
    glVertexAttribPointer(VERTEX_ARRAY, 2, GL_SHORT, GL_FALSE, 0, pVertices);
    glVertexAttribPointer(COLOR_ARRAY, 4, GL_UNSIGNED_BYTE, GL_TRUE, 0, pColors);
    glDrawArrays(GL_TRIANGLES, 0, 3);

    GFX_flipBuffer();
    return 1;
cleanup:
    eglMakeCurrent(RenderContext->eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT) ;
    eglTerminate(RenderContext->eglDisplay);

    DestroyNativeWindow();
GFX_end:

    if(RenderContext!=NULL)
    {
        free(RenderContext);
    }
    return rtn;
}

void GFX_deinit()
{
#ifdef DEBUGRENDER
    fprintf(stderr, "Stopping GLRenderer\n");
#endif

    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }

    eglMakeCurrent(RC->eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT) ;
    eglTerminate(RC->eglDisplay);

    DestroyNativeWindow();

    if(RC!=NULL)
    {
        free(RC);
        RC=NULL;
    }

    return;
}

int GFX_startFrame()
{
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }
#ifdef GLPROFILE
    RC->startframetime=get_timebase();
#endif
    ACL_LockMutex(RC->GFXMutex);
    SetSurface(RC->mainBuffer, RC->mainTexture, RC->mainWidth, RC->mainHeight);
    return 0;
}

int GFX_flipBuffer(int notInAnim)
{
    static int framecount=0;

    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return 0;
    }
#ifdef GLPROFILE
    fprintf(stderr, "Entering flipBuffer at %lld (start was %lld)\n",get_timebase(),RC->startframetime);
#endif

    // Flip code...
    float viewMatrix[] =
    {
        2.0f/RC->mainWidth,0.0f,0.0f,0.0f,
        0.0f,-2.0f/RC->mainHeight,0.0f,0.0f,
        0.0f,0.0f,1.0f,0.0f,
        -1.0f,1.0f,0.0f,1.0f
    };
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glUseProgram(baseTexturedProgram);
    glUniformMatrix4fv( baseTexturedProgramPMVLocation, 1, GL_FALSE, viewMatrix);

    glEnable(GL_TEXTURE_2D);
    glDisable(GL_BLEND);
    glBindTexture(GL_TEXTURE_2D, RC->mainTexture);
    GLshort pVertices2[] = {0,0, RC->mainWidth,0, 
        RC->mainWidth,RC->mainHeight, RC->mainWidth, RC->mainHeight, 0, RC->mainHeight, 0, 0};
    GLuint pColors2[] = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};
    GLfloat pCoords2[] = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f};
    glEnableVertexAttribArray(VERTEX_ARRAY);
    glEnableVertexAttribArray(COLOR_ARRAY);
    glEnableVertexAttribArray(COORD_ARRAY);

    // Sets the vertex data to this attribute index
    glVertexAttribPointer(VERTEX_ARRAY, 2, GL_SHORT, GL_FALSE, 0, pVertices2);
    glVertexAttribPointer(COLOR_ARRAY, 4, GL_UNSIGNED_BYTE, GL_TRUE, 0, pColors2);
    glVertexAttribPointer(COORD_ARRAY, 2, GL_FLOAT, GL_FALSE, 0, pCoords2);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glBindTexture(GL_TEXTURE_2D, 0);

    eglSwapBuffers(RC->eglDisplay, RC->eglSurface);

    ACL_UnlockMutex(RC->GFXMutex);


#ifdef GLPROFILE
    fprintf(stderr, "Flipping, render time was : %lld, total frame time was: %lld\n",RC->rendertime,
        (get_timebase()-RC->startframetime));
    RC->rendertime=0;
    fprintf(stderr, "optimes: %lld %lld %lld %lld %lld %lld %lld %lld %lld\n",
        RC->optimes[0],RC->optimes[1],RC->optimes[2],RC->optimes[3],
        RC->optimes[4],RC->optimes[5],RC->optimes[6],RC->optimes[7], 
        RC->optimes[8]);
    RC->optimes[0]=0; RC->optimes[1]=0; RC->optimes[2]=0; RC->optimes[3]=0;
    RC->optimes[4]=0; RC->optimes[5]=0; RC->optimes[6]=0; RC->optimes[7]=0;
    RC->optimes[8]=0;
#endif
    return 0;
}

void GFX_drawRect(int x, int y, int width, int height, 
    int thickness, int argbTL, int argbTR, int argbBR, int argbBL)
{
#ifdef GLPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    

#ifdef DEBUGRENDER
    fprintf(stderr, "Drawing rectangle %d %d %d %d\n",x,y,width,height);
#endif
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }

    unsigned int colors[5] = {argbTL,argbTR,argbBR,argbBL,argbTL};
    short verts[10] = {x,y,x+width,y,x+width,y+height,x,y+height,x,y};

    glLineWidth(thickness);
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

/*    gl.glBegin(GL_LINE_STRIP);
    setGLColor(gl, argbTL);
    gl.glVertex2f(x,y);
    setGLColor(gl, argbTR);
    gl.glVertex2f(x+width,y);
    setGLColor(gl, argbBR);
    gl.glVertex2f(x+width,y+height);
    setGLColor(gl, argbBL);
    gl.glVertex2f(x,y+height);
    setGLColor(gl, argbTL);
    gl.glVertex2f(x,y);
    gl.glEnd();*/


#ifdef GLPROFILE
    t2=get_timebase();
#ifdef GLPROFILE2
    fprintf(stderr,"DR time: %d\n",(int)(t2-t1));
#endif
    RC->rendertime+=(t2-t1);
    RC->optimes[0]+=(t2-t1);
#endif
    return;
}

void GFX_fillRect(int x, int y, int width, int height, 
    int argbTL, int argbTR, int argbBR, int argbBL)
{
#ifdef GLPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    

#ifdef DEBUGRENDER
    fprintf(stderr, "Filling rectangle %d %d %d %d %08X\n",x,y,width,height,argbTL);
#endif

    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }



#ifdef GLPROFILE
    t2=get_timebase();
#ifdef GLPROFILE2
    fprintf(stderr,"FR time: %d\n",(int)(t2-t1));
#endif
    RC->rendertime+=(t2-t1);
    RC->optimes[1]+=(t2-t1);
#endif
}

void GFX_clearRect(int x, int y, int width, int height, 
    int argbTL, int argbTR, int argbBR, int argbBL)
{
#ifdef GLPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    
    
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }
#ifdef DEBUGRENDER
    fprintf(stderr, "Clearing rectangle %d %d %d %d %X %X %X %X\n",x,y,width,height, 
        argbTL, argbTR, argbBR, argbBL);
#endif

    argbTL=FlipRGBA(argbTL);
    argbTR=FlipRGBA(argbTR);
    argbBR=FlipRGBA(argbBR);
    argbBL=FlipRGBA(argbBL);
    SetShaders(1);
    SetShaderParams();
    glDisable(GL_BLEND);
    glDisable(GL_TEXTURE_2D);

    GLshort pVertices2[] = {x, y, x+width, y, x+width, y+height, 
        x+width, y+height, x, y+height, x, y};
    GLuint pColors2[] = {argbTL, argbTR, argbBR, argbBR, argbBL, argbTL};
    glEnableVertexAttribArray(VERTEX_ARRAY);
    glEnableVertexAttribArray(COLOR_ARRAY);
    glDisableVertexAttribArray(COORD_ARRAY);

    // Sets the vertex data to this attribute index
    glVertexAttribPointer(VERTEX_ARRAY, 2, GL_SHORT, GL_FALSE, 0, pVertices2);
    glVertexAttribPointer(COLOR_ARRAY, 4, GL_UNSIGNED_BYTE, GL_TRUE, 0, pColors2);
    glDrawArrays(GL_TRIANGLES, 0, 6);

#ifdef GLPROFILE
    t2=get_timebase();
#ifdef GLPROFILE2
    fprintf(stderr,"CR time: %d\n",(int)(t2-t1));
#endif
    RC->rendertime+=(t2-t1);
    RC->optimes[2]+=(t2-t1);
#endif
}

void GFX_drawOval(int x, int y, int width, int height, int thickness, 
    int argbTL, int argbTR, int argbBR, int argbBL,
    int clipX, int clipY, int clipW, int clipH)
{
#ifdef GLPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    

#ifdef DEBUGRENDER
    fprintf(stderr, "Drawing oval %d %d %d %d %d %d %d %d\n",
        x,y,width,height,clipX,clipY,clipW,clipH);
    fprintf(stderr, "%08X %08X %08X %08X\n",argbTL, argbTR, argbBR, argbBL);
#endif

    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }


#ifdef GLPROFILE
    t2=get_timebase();
#ifdef GLPROFILE2
    fprintf(stderr,"DO time: %d\n",(int)(t2-t1));
#endif
    RC->rendertime+=(t2-t1);
    RC->optimes[3]+=(t2-t1);
#endif
}

void GFX_fillOval(int x, int y, int width, int height, 
    int argbTL, int argbTR, int argbBR, int argbBL,
    int clipX, int clipY, int clipW, int clipH)
{
#ifdef GLPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif

#ifdef DEBUGRENDER
    fprintf(stderr, "Drawing oval %d %d %d %d %d %d %d %d\n",
        x,y,width,height,clipX,clipY,clipW,clipH);
    fprintf(stderr, "%08X %08X %08X %08X\n",argbTL, argbTR, argbBR, argbBL);
#endif

    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }




#ifdef GLPROFILE
    t2=get_timebase();
#ifdef GLPROFILE2
    fprintf(stderr,"FO time: %d\n",(int)(t2-t1));
#endif
    RC->rendertime+=(t2-t1);
    RC->optimes[4]+=(t2-t1);
#endif
}

void GFX_drawRoundRect(int x, int y, int width, int height, 
    int thickness, int arcRadius, 
    int argbTL, int argbTR, int argbBR, int argbBL,
    int clipX, int clipY, int clipW, int clipH)
{
#ifdef GLPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    

#ifdef DEBUGRENDER
    fprintf(stderr, "Drawing round rectangle %d %d %d %d %d %d %d %d %08X %08X %08X %08X\n",
        x,y,width,height,clipX,clipY,clipW,clipH, argbTL, argbTR, argbBR, argbBL);
#endif

    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }


#ifdef GLPROFILE
    t2=get_timebase();
#ifdef GLPROFILE2
    fprintf(stderr,"DRR time: %d\n",(int)(t2-t1));
#endif
    RC->rendertime+=(t2-t1);
    RC->optimes[5]+=(t2-t1);
#endif
    return;
}

void GFX_fillRoundRect(int x, int y, int width, int height, int arcRadius, 
    int argbTL, int argbTR, int argbBR, int argbBL,
    int clipX, int clipY, int clipW, int clipH)
{
#ifdef GLPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif

#ifdef DEBUGRENDER
    fprintf(stderr, "Filling round rectangle %d %d %d %d %d %d %d %d\n",
        x,y,width,height,clipX,clipY,clipW,clipH);
    fprintf(stderr, "%08X %08X %08X %08X\n",argbTL, argbTR, argbBR, argbBL);
#endif

    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }


#ifdef GLPROFILE
    t2=get_timebase();
#ifdef GLPROFILE2
    fprintf(stderr,"FRR time: %d\n",(int)(t2-t1));
#endif
    RC->rendertime+=(t2-t1);
    RC->optimes[6]+=(t2-t1);
#endif    
}


// TODO: Verify that some more, it has been a while since I have done line drawing in software
void GFX_drawLine(int x1, int y1, int x2, int y2, int argb1, int argb2)
{
#ifdef GLPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif

#ifdef DEBUGRENDER    
    fprintf(stderr, "Drawing line %d %d %d %d\n", x1,y1,x2,y2);
#endif

    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }


#ifdef GLPROFILE
    t2=get_timebase();
#ifdef GLPROFILE2
    fprintf(stderr,"DL time: %d\n",(int)(t2-t1));
#endif
    RC->rendertime+=(t2-t1);
#endif
}

int GFX_loadImage(int width, int height, int format)
{
    GLImage_t *newimage;
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return 0;
    }

//#ifdef DEBUGRENDER
    fprintf(stderr, "Trying to create image of size %dx%d type %d\n",width,height,format);
//#endif

    newimage=(GLImage_t *) malloc(sizeof(GLImage_t));

    if(newimage==NULL)
    {
        fprintf(stderr, "Couldn't allocate GLImage_t for new image\n");
        return 0;
    }
    memset(newimage, 0, sizeof(GLImage_t));

    fprintf(stderr,"Allocated image %dx%d with handle %X\n",width, height, newimage);

    newimage->uWidth=width;
    newimage->uHeight=height;
    newimage->uFormat=format;
    glGenTextures(1, &newimage->texture);
    glBindTexture(GL_TEXTURE_2D, newimage->texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 
        width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);

    return (int) newimage;
}

void GFX_loadImageLine(int handle, int line, int len, unsigned char *buffer)
{
    int i;
    GLImage_t *img;
    unsigned int *buffer32;

    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }

    if(handle==0) return;

    img=(GLImage_t *) (unsigned int) handle;

    buffer32=(unsigned int *) buffer;
    for(i=0;i<len/4;i++)
    {
        buffer32[i]=FlipRGBA(buffer32[i]);
    }
    glBindTexture(GL_TEXTURE_2D, img->texture);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, line, len/4, 1,
        GL_RGBA , GL_UNSIGNED_BYTE, buffer);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void GFX_unloadImage(int imghandle)
{
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
        fflush(stdout);
#endif
        return;
    }

    if(imghandle!=0)
    {
        GLImage_t *newimage=(GLImage_t *) (unsigned int) imghandle;
        fprintf(stderr, "Trying to release image handle %X\n",imghandle);
        glDeleteTextures(1, &newimage->texture);
        free(newimage);
    }
    return;
}

void GFX_drawTexturedRect(int x, int y, int width, int height, int handle,
    int srcx, int srcy, int srcwidth, int srcheight, int blend)
{
#ifdef GLPROFILE
    long long t1=get_timebase();
    long long t2=0;
#endif    
#ifdef DEBUGRENDER
/*    fprintf(stderr, "Drawing textured %X at %d,%d size %dx%d from %d,%d size %dx%d %08X\n",
        handle, x,y,width,height, 
        srcx, srcy, srcwidth, srcheight, blend);*/
#endif

    if(srcwidth==0||srcheight==0||width==0||height==0)
    {
        fprintf(stderr, "Warning, discarding textured rectangle with size parameter 0\n");
        return;
    }
    GLImage_t *srcimage;
    if(handle==0) return;
    srcimage=(GLImage_t *) handle;

    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }

    SetShaders(2);
    SetShaderParams();
    glEnable(GL_BLEND);
    glEnable(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D,srcimage->texture);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    if(height<0)
    {
        glBlendFunc(GL_ONE, GL_ZERO);
        height*=-1;
    }

    if(width<0) width*=-1;
    {
        // Images
        GLshort pVertices2[] = {x, y, x+width, y, x+width, y+height, 
            x+width, y+height, x, y+height, x, y};
        GLuint pColors2[] = {blend, blend, blend, blend, blend, blend};
        GLfloat pCoords2[] = {
            1.0f*srcx/srcimage->uWidth, 1.0f*srcy/srcimage->uHeight,
            1.0f*(srcx+srcwidth)/srcimage->uWidth, 1.0f*srcy/srcimage->uHeight,
            1.0f*(srcx+srcwidth)/srcimage->uWidth, 1.0f*(srcy+srcheight)/srcimage->uHeight,
            1.0f*(srcx+srcwidth)/srcimage->uWidth, 1.0f*(srcy+srcheight)/srcimage->uHeight,
            1.0f*srcx/srcimage->uWidth, 1.0f*(srcy+srcheight)/srcimage->uHeight,
            1.0f*srcx/srcimage->uWidth, 1.0f*srcy/srcimage->uHeight};
        glEnableVertexAttribArray(VERTEX_ARRAY);
        glEnableVertexAttribArray(COLOR_ARRAY);
        glEnableVertexAttribArray(COORD_ARRAY);
    
        // Sets the vertex data to this attribute index
        glVertexAttribPointer(VERTEX_ARRAY, 2, GL_SHORT, GL_FALSE, 0, pVertices2);
        glVertexAttribPointer(COLOR_ARRAY, 4, GL_UNSIGNED_BYTE, GL_TRUE, 0, pColors2);
        glVertexAttribPointer(COORD_ARRAY, 2, GL_FLOAT, GL_FALSE, 0, pCoords2);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        #ifdef GLPROFILE
            t2=get_timebase();
        #ifdef GLPROFILE2
            fprintf(stderr,"DTR time: %d\n",(int)(t2-t1));
        #endif
            RC->rendertime+=(t2-t1);
            RC->optimes[8]+=(t2-t1);
        #endif
    }
}

void GFX_drawText(int x, int y, int len, short *text, int handle, int argb,
    int clipX, int clipY, int clipW, int clipH)
{
}

int GFX_loadFont(char *name, int style, int size)
{
    return (int) 0;
}

void GFX_unloadFont(int handle)
{
}

void GFX_SetMode(int mode)
{
    char strmode[16];
    fprintf(stderr,"Setting mode %d\n",mode);
    sprintf(strmode,"%d",mode);
}

int GFX_createSurface(int width, int height)
{
    
    GLImage_t *newimage;
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return 0;
    }

#ifdef DEBUGRENDER    
    fprintf(stderr, "Trying to create surface of size %dx%d\n",width,height);
#endif

    newimage=(GLImage_t *) malloc(sizeof(GLImage_t));

    if(newimage==NULL)
    {
        fprintf(stderr, "Couldn't allocate GLImage_t for new image\n");
        return 0;
    }
    memset(newimage, 0, sizeof(GLImage_t));

    fprintf(stderr,"Allocated image %dx%d with handle %X\n",width, height, newimage);

    newimage->uWidth=width;
    newimage->uHeight=height;
    newimage->uFormat=128;
    CreateSurface(&newimage->buffer, &newimage->texture, width, height);

    return (int) newimage;
}

void GFX_SetTargetSurface(int surface)
{
#ifdef DEBUGRENDER    
    fprintf(stderr, "Trying to set surface %X\n",surface);
#endif
    if(RC==NULL)
    {
#ifdef DEBUGRENDER
        fprintf(stderr, "Invalid context\n");
#endif
        return;
    }
    GLImage_t *newimage;
    newimage=(GLImage_t *)surface;
    if(newimage!=NULL)
    {
        SetSurface(newimage->buffer, newimage->texture, newimage->uWidth, newimage->uHeight);
    }
    else
    {
        SetSurface(RC->mainBuffer, RC->mainTexture, RC->mainWidth, RC->mainHeight);
    }
}

int GFX_SetAspect(int aspect)
{
    if(RC==NULL)
    {
        fprintf(stderr, "Invalid context in SetAspect\n");
        return 0;
    }
    fprintf(stderr,"Setting aspect %d\n",aspect);
    return (720<<16)|1280;
}

int GFX_PrepImage(int width, int height)
{
    int handle;
    handle=GFX_loadImage(width, height, 0);
    fprintf(stderr, "PrepImage %d by %d output handle %X\n",width, height, handle);
    return handle;
}

int GFXCMD_LoadImageCompressed(int handle, int len, int sd, unsigned char *buffer,
    int bufferlevel, int buffersize, int bufferoffset)
{
    GLImage_t *img;
    if(handle==0) return;

    img=(GLImage_t *) (unsigned int) handle;

    fprintf(stderr,"Calling loadMiniImage for data length %d\n", len);
    return handle;
}

// activewin 0 don't change anything for active surface,
// activewin -1 unconnect the jpeg surface
// mode bits: 0=source 1=output, 2=alpha 3=activewin
int GFX_SetVideoProp(int mode, int sx, int sy, int swidth, int sheight, 
    int ox, int oy, int owidth, int oheight, int alpha, int activewin)
{
}
