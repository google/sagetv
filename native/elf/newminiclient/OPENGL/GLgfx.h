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
#include <EGL/egl.h>
#include <GLES2/gl2.h>

typedef struct {
    int hasAlpha; // Is there a third plane for alpha?
    unsigned int uWidth;
    unsigned int uHeight;
    unsigned int uFormat;
    GLuint texture;
    GLuint buffer;
}GLImage_t;

typedef struct {
    ACL_mutex * GFXMutex;
    void *nativeState;
    EGLNativeDisplayType nativeDisplay;
    EGLNativeWindowType nativeWindow;
    EGLDisplay eglDisplay;
    EGLConfig eglConfig;
    EGLSurface eglSurface;
    EGLContext eglContext;
    long long rendertime;
    long long startframetime;
    long long optimes[9];
    GLuint mainBuffer;
    GLuint mainTexture;
    GLuint mainWidth;
    GLuint mainHeight;
    GLuint currentBuffer;
    int currentShader; // 1: base 2: baseTextured
    float currentViewMatrix[16];
    int matrixLocation;
}GLRenderContext_t;
