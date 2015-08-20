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
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <fcntl.h>

#include "sage_media_image_ImageLoader.h"
#include "swscale.h"

//#define DEBUG_SCALING_INSETS

// Mac OS X image loader/scaler code (and sysOutPrint) is defined in darwin/Source/sage_media_image_ImageLoader.m
#if !defined(__APPLE__)

#include "imageload.h"

void sysOutPrint(JNIEnv* env, const char* cstr, ...)
{
	jthrowable oldExcept = (*env)->ExceptionOccurred(env);
	if (oldExcept)
		(*env)->ExceptionClear(env);
    va_list args;
    va_start(args, cstr);
    char buf[1024];
    vsprintf(buf, cstr, args);
    va_end(args);
	jstring jstr = (*env)->NewStringUTF(env, buf);
	static jclass cls = 0;
	static jfieldID outField = 0;
	static jmethodID printMeth = 0;
	if (!cls)
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
 * Class:     sage_media_image_ImageLoader
 * Method:    createThumbnail
 * Signature: (Ljava/lang/String;Ljava/lang/String;II)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_media_image_ImageLoader_createThumbnail
  (JNIEnv *env, jclass jc, jstring jfilename, jstring joutfilename, jint imagewidth, jint imageheight)
{
	// The scaler can't deal with a width smaller than 8
	if (imagewidth > 0 && imagewidth < 8)
		return JNI_FALSE;
	FILE* infile = NULL;
#ifdef __MINGW32__
	const jchar* wFilename = (*env)->GetStringChars(env, jfilename, NULL);
	sysOutPrint(env, "Creating %dx%d image file from file %ls\r\n", imagewidth, imageheight, wFilename);
	infile = _wfopen(wFilename, L"rb");
	(*env)->ReleaseStringChars(env, jfilename, wFilename);
#else
	const char* cFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
	sysOutPrint(env, "Creating %dx%d image file from file %s\r\n", imagewidth, imageheight, cFilename);
	infile = fopen(cFilename, "rb");
	(*env)->ReleaseStringUTFChars(env, jfilename, cFilename);
#endif
	if (!infile)
	{
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed opening file");
		return JNI_FALSE;
	}

    unsigned char header[8];
    if(fread(header, 8, 1, infile)!=1)
	{
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed detecting file type for image, couldn't read header");
		fclose(infile);
		return JNI_FALSE;
	}

	// Reset our position back to the beginning
	fseek(infile, 0, SEEK_SET);

	RawImage_t* myImage = NULL;

	if (header[0] == 0xFF && header[1] == 0xD8 && header[2] == 0xFF)
	{
		// JPEG file
		myImage = LoadJPEG(infile, imagewidth, imageheight, 24, 0);
	}
	else if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8')
	{
		fclose(infile);
		infile = 0;
		// The GIF decoder wants an int file handle, not a FILE*
#ifdef __MINGW32__
		wFilename = (*env)->GetStringChars(env, jfilename, NULL);
		int infp = _wopen(wFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringChars(env, jfilename, wFilename);
#else
		cFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
		int infp = open(cFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringUTFChars(env, jfilename, cFilename);
#endif
		if (!infp)
		{
			(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed opening file");
			return JNI_FALSE;
		}
		// GIF file
		myImage = LoadGIF(infp, imagewidth, imageheight);
		close(infp);
	}
	else if (header[0] == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47 &&
		header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A)
	{
		// PNG file
		myImage = LoadPNG(infile, imagewidth, imageheight);
	}
	else if ((header[0] == 0x49 && header[1] == 0x49 && header[2] == 0x2A && header[3] == 0) ||
		(header[0] == 0x4D && header[1] == 0x4D && header[2] == 0 && header[3] == 0x2A))
	{
		fclose(infile);
		infile = 0;
		// The TIFF decoder wants an int file handle, not a FILE*
#ifdef __MINGW32__
		wFilename = (*env)->GetStringChars(env, jfilename, NULL);
		int infp = _wopen(wFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringChars(env, jfilename, wFilename);
#else
		cFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
		int infp = open(cFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringUTFChars(env, jfilename, cFilename);
#endif
		if (!infp)
		{
			(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed opening file");
			return JNI_FALSE;
		}
		// TIFF file
		const char* tFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
		myImage = LoadTIFF(infp, tFilename, imagewidth, imageheight);
		(*env)->ReleaseStringUTFChars(env, jfilename, tFilename);
		close(infp);
	}
	else
	{
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), "Unsupported file type was requested to load");
		fclose(infile);
		return JNI_FALSE;
	}
	if (infile)
		fclose(infile);

	if (!myImage)
		return JNI_FALSE;

	FILE* outfile = NULL;
#ifdef __MINGW32__
	wFilename = (*env)->GetStringChars(env, joutfilename, NULL);
	outfile = _wfopen(wFilename, L"wb");
	(*env)->ReleaseStringChars(env, joutfilename, wFilename);
#else
	cFilename = (*env)->GetStringUTFChars(env, joutfilename, NULL);
	outfile = fopen(cFilename, "wb");
	(*env)->ReleaseStringUTFChars(env, joutfilename, cFilename);
#endif
	if (!outfile)
	{
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed opening output file");
		free(myImage->pPlane);
		free(myImage);
		return JNI_FALSE;
	}
	int rv = SaveJPEG(myImage, outfile);
	free(myImage->pPlane);
	free(myImage);
	fclose(outfile);
	if (rv)
		return JNI_FALSE;
	else
		return JNI_TRUE;
}

/*
 * Class:     sage_media_image_ImageLoader
 * Method:    loadScaledImageFromFile
 * Signature: (Ljava/lang/String;IIII)Lsage/media/image/RawImage;
 */
JNIEXPORT jobject JNICALL Java_sage_media_image_ImageLoader_loadScaledImageFromFile
  (JNIEnv *env, jclass jc, jstring jfilename, jint imagewidth, jint imageheight, jint bpp, jint rotation)
{
	// The scaler can't deal with a width smaller than 8
	if (imagewidth > 0 && imagewidth < 8)
		return NULL;
	static jclass rawImageClass = 0;
	static jmethodID rawImageConstruct = 0;
	if (!rawImageClass)
	{
		rawImageClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "sage/media/image/RawImage"));
		if ((*env)->ExceptionOccurred(env))
		{
			return NULL;
		}
		rawImageConstruct = (*env)->GetMethodID(env, rawImageClass, "<init>", "(IILjava/nio/ByteBuffer;ZI)V");
		if ((*env)->ExceptionOccurred(env))
		{
			return NULL;
		}
	}

	FILE* infile = NULL;
#ifdef __MINGW32__
	const jchar* wFilename = (*env)->GetStringChars(env, jfilename, NULL);
	sysOutPrint(env, "Loading %dx%d image from file %ls\r\n", imagewidth, imageheight, wFilename);
	infile = _wfopen(wFilename, L"rb");
	(*env)->ReleaseStringChars(env, jfilename, wFilename);
#else
	const char* cFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
	sysOutPrint(env, "Loading %dx%d image from file %s\r\n", imagewidth, imageheight, cFilename);
	infile = fopen(cFilename, "rb");
	(*env)->ReleaseStringUTFChars(env, jfilename, cFilename);
#endif
	if (!infile)
	{
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed opening file");
		return NULL;
	}
    unsigned char header[8];
    if(fread(header, 8, 1, infile)!=1)
	{
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed detecting file type for image, couldn't read header");
		fclose(infile);
		return NULL;
	}

	// Reset our position back to the beginning
	fseek(infile, 0, SEEK_SET);

	RawImage_t* myImage = NULL;

	if (header[0] == 0xFF && header[1] == 0xD8 && header[2] == 0xFF)
	{
		// JPEG file
		myImage = LoadJPEG(infile, imagewidth, imageheight, bpp, rotation);
	}
	else if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8')
	{
		fclose(infile);
		infile = 0;
		// The GIF decoder wants an int file handle, not a FILE*
#ifdef __MINGW32__
		wFilename = (*env)->GetStringChars(env, jfilename, NULL);
		int infp = _wopen(wFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringChars(env, jfilename, wFilename);
#else
		cFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
		int infp = open(cFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringUTFChars(env, jfilename, cFilename);
#endif
		if (!infp)
		{
			(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed opening file");
			return NULL;
		}
		// GIF file
		myImage = LoadGIF(infp, imagewidth, imageheight);
		close(infp);
	}
	else if (header[0] == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47 &&
		header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A)
	{
		// PNG file
		myImage = LoadPNG(infile, imagewidth, imageheight);
	}
	else if ((header[0] == 0x49 && header[1] == 0x49 && header[2] == 0x2A && header[3] == 0) ||
		(header[0] == 0x4D && header[1] == 0x4D && header[2] == 0 && header[3] == 0x2A))
	{
		fclose(infile);
		infile = 0;
		// The TIFF decoder wants an int file handle, not a FILE*
#ifdef __MINGW32__
		wFilename = (*env)->GetStringChars(env, jfilename, NULL);
		int infp = _wopen(wFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringChars(env, jfilename, wFilename);
#else
		cFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
		int infp = open(cFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringUTFChars(env, jfilename, cFilename);
#endif
		if (!infp)
		{
			(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed opening file");
			return JNI_FALSE;
		}
		// TIFF file
		const char* tFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
		myImage = LoadTIFF(infp, tFilename, imagewidth, imageheight);
		(*env)->ReleaseStringUTFChars(env, jfilename, tFilename);
		close(infp);
	}
	else
	{
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), "Unsupported file type was requested to load");
		fclose(infile);
		return NULL;
	}
	if (infile)
		fclose(infile);

	if (!myImage)
	{
		sysOutPrint(env, "FAILED to load the image file!\r\n");
		return NULL;
	}

	jobject dbuf = (*env)->NewDirectByteBuffer(env, myImage->pPlane, myImage->uHeight*myImage->uBytePerLine);
	if ((*env)->ExceptionOccurred(env))
	{
		return NULL;
	}

	jobject rv = (*env)->NewObject(env, rawImageClass, rawImageConstruct, myImage->uWidth, myImage->uHeight, 
		dbuf, myImage->hasAlpha ? JNI_TRUE : JNI_FALSE, myImage->uBytePerLine);
	free(myImage); // this doesn't free the data, only the structure
	return rv;
}

/*
 * Class:     sage_media_image_ImageLoader
 * Method:    freeImage0
 * Signature: (Ljava/nio/ByteBuffer;)V
 */
JNIEXPORT void JNICALL Java_sage_media_image_ImageLoader_freeImage0
  (JNIEnv *env, jclass jc, jobject img)
{
	// Just deallocate the native image data used
	void* myMemory = (*env)->GetDirectBufferAddress(env, img);
	if (myMemory)
		free(myMemory);
}

/*
 * Class:     sage_media_image_ImageLoader
 * Method:    compressImageToFile
 * Signature: (Lsage/media/image/RawImage;Ljava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_media_image_ImageLoader_compressImageToFile
  (JNIEnv *env, jclass jc, jobject rawImage, jstring jfilepath, jstring jformat)
{
	FILE* outfile = NULL;
#ifdef __MINGW32__
	const jchar* wFilename = (*env)->GetStringChars(env, jfilepath, NULL);
	outfile = _wfopen(wFilename, L"wb");
	(*env)->ReleaseStringChars(env, jfilepath, wFilename);
#else
	const char* cFilename = (*env)->GetStringUTFChars(env, jfilepath, NULL);
	outfile = fopen(cFilename, "wb");
	(*env)->ReleaseStringUTFChars(env, jfilepath, cFilename);
#endif
	if (!outfile)
	{
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed opening output file");
		return JNI_FALSE;
	}
	static jclass rawImageClass = 0;
	static jmethodID getWidthMeth = 0;
	static jmethodID getHeightMeth = 0;
	static jmethodID getDataMeth = 0;
	static jmethodID getAlphaMeth = 0;
	static jmethodID getStrideMeth = 0;
	if (!rawImageClass)
	{
		rawImageClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "sage/media/image/RawImage"));
		if ((*env)->ExceptionOccurred(env))
		{
			fclose(outfile);
			return JNI_FALSE;
		}
		getWidthMeth = (*env)->GetMethodID(env, rawImageClass, "getWidth", "()I");
		getHeightMeth = (*env)->GetMethodID(env, rawImageClass, "getHeight", "()I");
		getDataMeth = (*env)->GetMethodID(env, rawImageClass, "getData", "()Ljava/nio/ByteBuffer;");
		getAlphaMeth = (*env)->GetMethodID(env, rawImageClass, "hasAlpha", "()Z");
		getStrideMeth = (*env)->GetMethodID(env, rawImageClass, "getStride", "()I");
	}
	RawImage_t myImage;
	myImage.hasAlpha = (*env)->CallBooleanMethod(env, rawImage, getAlphaMeth);
	myImage.uBytePerLine = (*env)->CallIntMethod(env, rawImage, getStrideMeth);
	myImage.uWidth = (*env)->CallIntMethod(env, rawImage, getWidthMeth);
	myImage.uHeight = (*env)->CallIntMethod(env, rawImage, getHeightMeth);
	myImage.pPlane = (*env)->GetDirectBufferAddress(env, (*env)->CallObjectMethod(env, rawImage, getDataMeth));

	const char* cFormat = (*env)->GetStringUTFChars(env, jformat, NULL);

	int rv;
	if (!stricmp(cFormat, "png"))
		rv = SavePNG(&myImage, outfile);
	else
		rv = SaveJPEG(&myImage, outfile);
	(*env)->ReleaseStringUTFChars(env, jformat, cFormat);
	fclose(outfile);
	if (rv)
		return JNI_FALSE;
	else
		return JNI_TRUE;
}

/*
 * Class:     sage_media_image_ImageLoader
 * Method:    loadImageDimensionsFromFile
 * Signature: (Ljava/lang/String;)Lsage/media/image/RawImage;
 */
JNIEXPORT jobject JNICALL Java_sage_media_image_ImageLoader_loadImageDimensionsFromFile
  (JNIEnv *env, jclass jc, jstring jfilename)
{
	static jclass rawImageClass = 0;
	static jmethodID rawImageConstruct = 0;
	if (!rawImageClass)
	{
		rawImageClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "sage/media/image/RawImage"));
		if ((*env)->ExceptionOccurred(env))
		{
			return NULL;
		}
		rawImageConstruct = (*env)->GetMethodID(env, rawImageClass, "<init>", "(IILjava/nio/ByteBuffer;ZI)V");
		if ((*env)->ExceptionOccurred(env))
		{
			return NULL;
		}
	}

	FILE* infile = NULL;
#ifdef __MINGW32__
	const jchar* wFilename = (*env)->GetStringChars(env, jfilename, NULL);
	infile = _wfopen(wFilename, L"rb");
	(*env)->ReleaseStringChars(env, jfilename, wFilename);
#else
	const char* cFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
	infile = fopen(cFilename, "rb");
	(*env)->ReleaseStringUTFChars(env, jfilename, cFilename);
#endif
	if (!infile)
	{
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed opening file");
		return NULL;
	}
    unsigned char header[26];
    if(fread(header, 26, 1, infile)!=1)
	{
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed detecting file type for image, couldn't read header");
		fclose(infile);
		return NULL;
	}

	// Reset our position back to the beginning
	fseek(infile, 0, SEEK_SET);

	int imageWidth=0,imageHeight=0;
	int hasAlpha = 1;

	if (header[0] == 0xFF && header[1] == 0xD8 && header[2] == 0xFF)
	{
		// JPEG file
		LoadJPEGDimensions(infile, &imageWidth, &imageHeight);
		hasAlpha = 0;
	}
	else if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8')
	{
		fclose(infile);
		infile = 0;
		// The GIF decoder wants an int file handle, not a FILE*
#ifdef __MINGW32__
		wFilename = (*env)->GetStringChars(env, jfilename, NULL);
		int infp = _wopen(wFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringChars(env, jfilename, wFilename);
#else
		cFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
		int infp = open(cFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringUTFChars(env, jfilename, cFilename);
#endif
		if (!infp)
		{
			(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed opening file");
			return NULL;
		}
		// GIF file
		LoadGIFDimensions(infp, &imageWidth, &imageHeight);
		close(infp);
	}
	else if (header[0] == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47 &&
		header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A)
	{
		// PNG file
		LoadPNGDimensions(infile, &imageWidth, &imageHeight);
	}
	else if ((header[0] == 0x49 && header[1] == 0x49 && header[2] == 0x2A && header[3] == 0) ||
		(header[0] == 0x4D && header[1] == 0x4D && header[2] == 0 && header[3] == 0x2A))
	{
		fclose(infile);
		infile = 0;
		// The TIFF decoder wants an int file handle, not a FILE*
#ifdef __MINGW32__
		wFilename = (*env)->GetStringChars(env, jfilename, NULL);
		int infp = _wopen(wFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringChars(env, jfilename, wFilename);
#else
		cFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
		int infp = open(cFilename, O_RDONLY | O_BINARY);
		(*env)->ReleaseStringUTFChars(env, jfilename, cFilename);
#endif
		if (!infp)
		{
			(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/FileNotFoundException"), "Failed opening file");
			return JNI_FALSE;
		}
		// TIFF file
		const char* tFilename = (*env)->GetStringUTFChars(env, jfilename, NULL);
		LoadTIFFDimensions(infp, tFilename, &imageWidth, &imageHeight);
		(*env)->ReleaseStringUTFChars(env, jfilename, tFilename);
		close(infp);
	}
	else if (header[0] == 'B' && header[1] == 'M')
	{
		// BMP file (dimensions is easy to get)
		imageWidth = ((header[21] & 0xFF) << 24) | ((header[20] & 0xFF) << 16) | ((header[19] & 0xFF) << 8) |
			(header[18] & 0xFF);
		imageHeight = ((header[25] & 0xFF) << 24) | ((header[24] & 0xFF) << 16) | ((header[23] & 0xFF) << 8) |
			(header[22] & 0xFF);
	}
	else
	{
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), "Unsupported file type was requested to load");
		fclose(infile);
		return NULL;
	}
	if (infile)
		fclose(infile);

	if (imageWidth == 0 || imageHeight == 0)
	{
		sysOutPrint(env, "FAILED to load the image file!\r\n");
		return NULL;
	}

	jobject rv = (*env)->NewObject(env, rawImageClass, rawImageConstruct, imageWidth, imageHeight, 
		0, hasAlpha ? JNI_TRUE : JNI_FALSE, 0);
	return rv;
}

#endif // !defined(__APPLE__)

// FIXME: This needs to be moved to a different file so we can remove the conditional above, it seems like it should be in RawImage anyways...

/*
 * Class:     sage_media_image_ImageLoader
 * Method:    scaleRawImage
 * Signature: (Lsage/media/image/RawImage;II[I)Lsage/media/image/RawImage;
 */
JNIEXPORT jobject JNICALL Java_sage_media_image_ImageLoader_scaleRawImage
  (JNIEnv *env, jclass jc, jobject srcImage, jint imageWidth, jint imageHeight, jintArray jscaledInsets)
{
	// The scaler can't deal with a width smaller than 8
	if (imageWidth > 0 && imageWidth < 8)
		return NULL;
	static jclass rawImageClass = 0;
	static jmethodID rawImageConstruct = 0;
	static jmethodID getDataMeth = 0;
	static jmethodID getWidthMeth = 0;
	static jmethodID getHeightMeth = 0;
	if (!rawImageClass)
	{
		rawImageClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "sage/media/image/RawImage"));
		if ((*env)->ExceptionOccurred(env))
		{
			return NULL;
		}
		rawImageConstruct = (*env)->GetMethodID(env, rawImageClass, "<init>", "(IILjava/nio/ByteBuffer;ZI)V");
		if ((*env)->ExceptionOccurred(env))
		{
			return NULL;
		}
		getDataMeth = (*env)->GetMethodID(env, rawImageClass, "getData", "()Ljava/nio/ByteBuffer;");
		if ((*env)->ExceptionOccurred(env))
			return NULL;
		getWidthMeth = (*env)->GetMethodID(env, rawImageClass, "getWidth", "()I");
		if ((*env)->ExceptionOccurred(env))
			return NULL;
		getHeightMeth = (*env)->GetMethodID(env, rawImageClass, "getHeight", "()I");
		if ((*env)->ExceptionOccurred(env))
			return NULL;
	}

	unsigned char* srcImageData = (*env)->GetDirectBufferAddress(env, (*env)->CallObjectMethod(env, srcImage, getDataMeth));
	if ((*env)->ExceptionOccurred(env))
		return NULL;
	int srcWidth, srcHeight;
	srcWidth = (*env)->CallIntMethod(env, srcImage, getWidthMeth);
	if ((*env)->ExceptionOccurred(env))
		return NULL;
	srcHeight = (*env)->CallIntMethod(env, srcImage, getHeightMeth);
	if ((*env)->ExceptionOccurred(env))
		return NULL;

	unsigned char* destImageData = malloc(imageWidth * imageHeight * 4);
	if (!destImageData)
	{
		sysOutPrint(env, "Out of memory allocating new raw image!\r\n");
		return NULL;
	}
#ifdef DEBUG_SCALING_INSETS
sysOutPrint(env, "raw scaling insets srcWidth=%d srcHeight=%d destWidth=%d destHeight=%d\r\n", srcWidth, srcHeight, imageWidth, imageHeight);
#endif
	if (jscaledInsets == NULL)
	{
		struct SwsContext *sws = sws_getContext(srcWidth, srcHeight, 
			PIX_FMT_RGB32, imageWidth, imageHeight, PIX_FMT_RGB32, 0x0002, NULL);
		if (!sws)
		{
			free(destImageData);
			return 0;
		}
		sws_scale(sws, srcImageData, srcWidth*4, 0, srcHeight, 
			destImageData, imageWidth*4);
		sws_freeContext(sws);
	}
	else
	{
		struct SwsContext *sws;
		int tempScale = 1; // in case something is smaller than 8 in width we have to do extra scaling to compensate, but then
		// we overwrite that info later with another scaling operation since there's no blending in this scaler
		jint insar[8];
		(*env)->GetIntArrayRegion(env, jscaledInsets, 0, 8, insar);
		int st=insar[0],sr=insar[1],sb=insar[2],sl=insar[3],dt=insar[4],dr=insar[5],db=insar[6],dl=insar[7];
#ifdef DEBUG_SCALING_INSETS
sysOutPrint(env, "raw scaling insets st=%d sr=%d sb=%d sl=%d dt=%d dr=%d db=%d dl=%d\r\n", st, sr, db, sl, dt, dr, db, dl);
#endif
		// This is done as 9 separate scaling operations
		// Top left
		if (sl > 0 && dl > 0 && st > 0 && dt > 0)
		{
			if (sl < 8 || dl < 8)
				tempScale = (8/(sl < dl ? sl : dl)) + 1;
			else
				tempScale = 1;
#ifdef DEBUG_SCALING_INSETS
sysOutPrint(env, "raw scaling insets line=%d tempScale=%d\r\n", __LINE__, tempScale);
#endif
			if (sl*tempScale <= srcWidth && dl*tempScale <= imageWidth)
			{
				sws = sws_getContext(sl*tempScale, st, PIX_FMT_RGB32, dl*tempScale, dt, PIX_FMT_RGB32, 0x0002, NULL);
				if (!sws)
				{
					free(destImageData);
					return 0;
				}
				sws_scale(sws, srcImageData, srcWidth*4, 0, st, destImageData, imageWidth*4);
				sws_freeContext(sws);
			}
		}
		// Top right
		if (sr > 0 && dr > 0 && st > 0 && dt > 0)
		{
			if (sr < 8 || dr < 8)
				tempScale = (8/(sr < dr ? sr : dr)) + 1;
			else
				tempScale = 1;
#ifdef DEBUG_SCALING_INSETS
sysOutPrint(env, "raw scaling insets line=%d tempScale=%d\r\n", __LINE__, tempScale);
#endif
			if (sr*tempScale <= srcWidth && dr*tempScale <= imageWidth)
			{
				sws = sws_getContext(sr*tempScale, st, PIX_FMT_RGB32, dr*tempScale, dt, PIX_FMT_RGB32, 0x0002, NULL);
				if (!sws)
				{
					free(destImageData);
					return 0;
				}
				sws_scale(sws, srcImageData + 4*(srcWidth-sr*tempScale), srcWidth*4, 0, st, 
					destImageData + 4*(imageWidth-dr*tempScale), imageWidth*4);
				sws_freeContext(sws);
			}
		}
		//Bottom Left
		if (sl > 0 && dl > 0 && sb > 0 && db > 0)
		{
			if (sl < 8 || dl < 8)
				tempScale = (8/(sl < dl ? sl : dl)) + 1;
			else
				tempScale = 1;
#ifdef DEBUG_SCALING_INSETS
sysOutPrint(env, "raw scaling insets line=%d tempScale=%d\r\n", __LINE__, tempScale);
#endif
			if (sl*tempScale <= srcWidth && dl*tempScale <= imageWidth)
			{
				sws = sws_getContext(sl*tempScale, sb, PIX_FMT_RGB32, dl*tempScale, db, PIX_FMT_RGB32, 0x0002, NULL);
				if (!sws)
				{
					free(destImageData);
					return 0;
				}
				sws_scale(sws, srcImageData + 4*srcWidth*(srcHeight-sb), srcWidth*4, 0, sb, 
					destImageData + 4*imageWidth*(imageHeight - db), imageWidth*4);
				sws_freeContext(sws);
			}
		}
		//Bottom Right
		if (sr > 0 && dr > 0 && sb > 0 && db > 0)
		{
			if (sr < 8 || dr < 8)
				tempScale = (8/(sr < dr ? sr : dr)) + 1;
			else
				tempScale = 1;
#ifdef DEBUG_SCALING_INSETS
sysOutPrint(env, "raw scaling insets line=%d tempScale=%d\r\n", __LINE__, tempScale);
#endif
			if (sr*tempScale <= srcWidth && dr*tempScale <= imageWidth)
			{
				sws = sws_getContext(sr*tempScale, sb, PIX_FMT_RGB32, dr*tempScale, db, PIX_FMT_RGB32, 0x0002, NULL);
				if (!sws)
				{
					free(destImageData);
					return 0;
				}
				sws_scale(sws, srcImageData + 4*srcWidth*(srcHeight-sb) + 4*(srcWidth-sr*tempScale), srcWidth*4, 0, sb, 
					destImageData + 4*(imageWidth-dr*tempScale) + 4*imageWidth*(imageHeight - db), imageWidth*4);
				sws_freeContext(sws);
			}
		}
		// Top
		if (st > 0 && dt > 0 && (imageWidth - dl - dr) >= 8)
		{
#ifdef DEBUG_SCALING_INSETS
sysOutPrint(env, "raw scaling insets line=%d tempScale=%d\r\n", __LINE__, tempScale);
#endif
			sws = sws_getContext(srcWidth - sl - sr, st, PIX_FMT_RGB32, imageWidth - dl - dr, dt, PIX_FMT_RGB32, 0x0002, NULL);
			if (!sws)
			{
				free(destImageData);
				return 0;
			}
			sws_scale(sws, srcImageData + 4*sl, srcWidth*4, 0, st, destImageData + 4*dl, imageWidth*4);
			sws_freeContext(sws);
		}
		// Bottom
		if (sb > 0 && db > 0 && (imageWidth - dl - dr) >= 8)
		{
#ifdef DEBUG_SCALING_INSETS
sysOutPrint(env, "raw scaling insets line=%d tempScale=%d\r\n", __LINE__, tempScale);
#endif
			sws = sws_getContext(srcWidth - sl - sr, sb, PIX_FMT_RGB32, imageWidth - dl - dr, db, PIX_FMT_RGB32, 0x0002, NULL);
			if (!sws)
			{
				free(destImageData);
				return 0;
			}
			sws_scale(sws, srcImageData + 4*sl + 4*srcWidth*(srcHeight - sb), srcWidth*4, 0, sb, 
				destImageData + 4*dl + 4*imageWidth*(imageHeight - db), imageWidth*4);
			sws_freeContext(sws);
		}
		// Left
		if (sl > 0 && dl > 0)
		{
#ifdef DEBUG_SCALING_INSETS
sysOutPrint(env, "raw scaling insets line=%d tempScale=%d\r\n", __LINE__, tempScale);
#endif
			if (sl < 8 || dl < 8)
				tempScale = (8/(sl < dl ? sl : dl)) + 1;
			else
				tempScale = 1;
			if (sl*tempScale <= srcWidth && dl*tempScale <= imageWidth)
			{
				sws = sws_getContext(sl*tempScale, srcHeight-st-sb, PIX_FMT_RGB32, dl*tempScale, 
					imageHeight-db-dt, PIX_FMT_RGB32, 0x0002, NULL);
				if (!sws)
				{
					free(destImageData);
					return 0;
				}
				sws_scale(sws, srcImageData + srcWidth*4*st, srcWidth*4, 0, srcHeight-st-sb, 
					destImageData + imageWidth*4*dt, imageWidth*4);
				sws_freeContext(sws);
			}
		}
		// Right
		if (sr > 0 && dr > 0)
		{
#ifdef DEBUG_SCALING_INSETS
sysOutPrint(env, "raw scaling insets line=%d tempScale=%d\r\n", __LINE__, tempScale);
#endif
			if (sr < 8 || dr < 8)
				tempScale = (8/(sr < dr ? sr : dr)) + 1;
			else
				tempScale = 1;
			if (sr*tempScale <= srcWidth && dr*tempScale <= imageWidth)
			{
				sws = sws_getContext(sr*tempScale, srcHeight-st-sb, PIX_FMT_RGB32, dr*tempScale, 
					imageHeight-db-dt, PIX_FMT_RGB32, 0x0002, NULL);
				if (!sws)
				{
					free(destImageData);
					return 0;
				}
				sws_scale(sws, srcImageData + srcWidth*4*st + 4*(srcWidth - sr*tempScale), srcWidth*4, 0, srcHeight-st-sb, 
					destImageData + imageWidth*4*dt + 4*(imageWidth - dr*tempScale), imageWidth*4);
				sws_freeContext(sws);
			}
		}
		// Center
		if (imageWidth - dl - dr >= 8)
		{
#ifdef DEBUG_SCALING_INSETS
sysOutPrint(env, "raw scaling insets line=%d tempScale=%d\r\n", __LINE__, tempScale);
#endif
			sws = sws_getContext(srcWidth - sr - sl, srcHeight - st - sb, PIX_FMT_RGB32, imageWidth - dr - dl, 
				imageHeight - db - dt, PIX_FMT_RGB32, 0x0002, NULL);
			if (!sws)
			{
				free(destImageData);
				return 0;
			}
			sws_scale(sws, srcImageData + srcWidth*4*st + 4*sl, srcWidth*4, 0, srcHeight - st - sb, 
				destImageData + imageWidth*4*dt + 4*dl, imageWidth*4);
			sws_freeContext(sws);
		}
	}

	jobject destImageBuf = (*env)->NewDirectByteBuffer(env, destImageData, imageWidth * imageHeight * 4);
	if ((*env)->ExceptionOccurred(env))
	{
		free(destImageData);
		return NULL;
	}

	jobject rv = (*env)->NewObject(env, rawImageClass, rawImageConstruct, imageWidth, imageHeight, 
		destImageBuf, JNI_TRUE, imageWidth*4);
	return rv;
}
