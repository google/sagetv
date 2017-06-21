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

#include <stdint.h>
#include <ft2build.h>
#include FT_FREETYPE_H
#include "sage_FreetypeFont.h"

typedef struct
{
	FT_Face facePtr;
	FT_Size sizePtr;
	int style;
} FTDataStruct;

void sysOutPrint(JNIEnv* env, const char* cstr, ...)
{
	jthrowable oldExcept = (*env)->ExceptionOccurred(env);
	if (oldExcept)
		(*env)->ExceptionClear(env);
    va_list args;
    va_start(args, cstr);
    char buf[1024];
    vsnprintf(buf, sizeof(buf), cstr, args);
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
 * Class:     sage_FreetypeFont
 * Method:    loadFreetypeLib0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sage_FreetypeFont_loadFreetypeLib0
  (JNIEnv *env, jclass jc)
{
	FT_Library library;
	int error = FT_Init_FreeType(&library);
	if (error)
	{
		sysOutPrint(env, "Error loading FreeType of %d\n", error);
		return 0;
	}
 	return (jlong)(intptr_t) library;
}

/*
 * Class:     sage_FreetypeFont
 * Method:    closeFreetypeLib0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_FreetypeFont_closeFreetypeLib0
  (JNIEnv *env, jclass jc, jlong ptr)
{
	FT_Library library = (FT_Library)(intptr_t) ptr;
	if (library)
	{
		if (FT_Done_FreeType(library))
			return JNI_FALSE;
		else
			return JNI_TRUE;
	}
	return JNI_TRUE;
}

/*
 * Class:     sage_FreetypeFont
 * Method:    closeFontFace0
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sage_FreetypeFont_closeFontFace0
  (JNIEnv *env, jobject jo, jlong fontPtr)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	// NOTE: WE CAN ONLY CLOSE THE SIZE SINCE OTHERS MIGHT STILL BE USING THE FACE!!!!
	// THIS LEAKS MEMORY IF YOU THINK IT'LL CLOSE THE FONT FACE!!!!!
	// THIS LEAKS MEMORY IF YOU THINK IT'LL CLOSE THE FONT FACE!!!!!
	//FT_Face face = (FT_Face) facePtr;
	jboolean rv = FT_Done_Size(fontData->sizePtr) == 0;//FT_Done_Face(face) == 0;
	free(fontData);
	return rv;
}

/*
 * Class:     sage_FreetypeFont
 * Method:    loadFontFace0
 * Signature: (JLjava/lang/String;I)J
 */
JNIEXPORT jlong JNICALL Java_sage_FreetypeFont_loadFontFace0
  (JNIEnv *env, jobject jo, jlong ftLibPtr, jstring jstr, jint ptSize, jint style)
{

	FT_Face face;
	FT_Library library = (FT_Library)(intptr_t) ftLibPtr;
	const char* cstr = (*env)->GetStringUTFChars(env, jstr, NULL);
	int error = FT_New_Face(library, cstr, 0, &face);

	(*env)->ReleaseStringUTFChars(env, jstr, cstr);
	if (error)
	{
		sysOutPrint(env, "Error loading freetype font of %d\r\n", error);
		return 0;
	}
	int scrnDPI = 72;//96;
	error = FT_Set_Char_Size(
            face,    /* handle to face object           */
            0,       /* char_width in 1/64th of points  */
            (int)(ptSize*64 + 0.5f),   /* char_height in 1/64th of points */
            scrnDPI,     /* horizontal device resolution    */
            scrnDPI );   /* vertical device resolution      */
	if (error)
	{
		sysOutPrint(env, "Error setting freetype char size of %d\r\n", error);
		return 0;
	}

	FTDataStruct* rv = (FTDataStruct*)malloc(sizeof(FTDataStruct));
	rv->sizePtr = face->size;
	rv->facePtr = face;
	rv->style = 0;
	// Remove styles already applied to the TTF itself
	if ((style & sage_FreetypeFont_BOLD) != 0)
	{
		if ((face->style_flags & FT_STYLE_FLAG_BOLD) == 0)
		{
			rv->style = rv->style | FT_STYLE_FLAG_BOLD;
		}
	}
	if ((style & sage_FreetypeFont_ITALIC) != 0)
	{
		if ((face->style_flags & FT_STYLE_FLAG_ITALIC) == 0)
		{
			rv->style = rv->style | FT_STYLE_FLAG_ITALIC;
		}
	}
	
    	
	return (jlong)(intptr_t) rv;
}


/*
 * Class:     sage_FreetypeFont
 * Method:    deriveFontFace0
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL Java_sage_FreetypeFont_deriveFontFace0
  (JNIEnv *env, jobject jo, jlong fontPtr, jint ptSize, jint style)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	FT_Size newSize;
	int error = FT_New_Size(fontData->facePtr, &newSize);
	if (error)
	{
		sysOutPrint(env, "Error deriving freetype font of %d\r\n", error);
		return 0;
	}
	FT_Activate_Size(newSize);
	int scrnDPI = 72;//96;
	error = FT_Set_Char_Size(
            fontData->facePtr,    /* handle to face object           */
            0,       /* char_width in 1/64th of points  */
            (int)(ptSize*64 + 0.5f),   /* char_height in 1/64th of points */
            scrnDPI,     /* horizontal device resolution    */
            scrnDPI );   /* vertical device resolution      */
	if (error)
	{
		sysOutPrint(env, "Error setting freetype char size of %d\r\n", error);
		return 0;
	}

	FTDataStruct* rv = (FTDataStruct*)malloc(sizeof(FTDataStruct));
	rv->sizePtr = newSize;
	rv->facePtr = fontData->facePtr;
	rv->style = 0;
	// Remove styles already applied to the TTF itself
	if ((style & sage_FreetypeFont_BOLD) != 0)
	{
		if ((fontData->facePtr->style_flags & FT_STYLE_FLAG_BOLD) == 0)
		{
			rv->style = rv->style | FT_STYLE_FLAG_BOLD;
		}
	}
	if ((style & sage_FreetypeFont_ITALIC) != 0)
	{
		if ((fontData->facePtr->style_flags & FT_STYLE_FLAG_ITALIC) == 0)
		{
			rv->style = rv->style | FT_STYLE_FLAG_ITALIC;
		}
	}
	return (jlong)(intptr_t) rv;
}

/*
 * Class:     FreetypeFont
 * Method:    getGlyphForChar0
 * Signature: (JC)I
 */
JNIEXPORT jint JNICALL Java_sage_FreetypeFont_getGlyphForChar0
  (JNIEnv *env, jobject jo, jlong fontPtr, jchar c)
{
	//FT_Face face = (FT_Face) facePtr;
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	return FT_Get_Char_Index(fontData->facePtr, c);
}

/*
 * Class:     FreetypeFont
 * Method:    loadGlyph0
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_sage_FreetypeFont_loadGlyph0
  (JNIEnv *env, jobject jo, jlong fontPtr, jint glyphCode)
{
	//FT_Face face = (FT_Face) facePtr;
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	FT_Activate_Size(fontData->sizePtr);
	int error = FT_Load_Glyph(fontData->facePtr, glyphCode, FT_LOAD_FORCE_AUTOHINT);
	if ((fontData->style & FT_STYLE_FLAG_BOLD) != 0)
	{
		// Apply bold effect
		if (fontData->facePtr->glyph->format == FT_GLYPH_FORMAT_OUTLINE)
		{
			/* some reasonable strength */
			FT_Pos strength = FT_MulFix(fontData->facePtr->units_per_EM,
				fontData->facePtr->size->metrics.y_scale ) / 42;

			FT_BBox bbox_before, bbox_after;
			// The bounding box code was what XBMC was using to do this calculation; but the
			// examples in the freetype library use the *4 math below which then doesn't clip
			// the text when we render it.
//			FT_Outline_Get_CBox(&fontData->facePtr->glyph->outline, &bbox_before);
			FT_Outline_Embolden(&fontData->facePtr->glyph->outline, strength);  // ignore error
//			FT_Outline_Get_CBox(&fontData->facePtr->glyph->outline, &bbox_after);

//			FT_Pos dx = bbox_after.xMax - bbox_before.xMax;
//			FT_Pos dy = bbox_after.yMax - bbox_before.yMax;
FT_Pos dx = strength * 4;
FT_Pos dy = dx;
			if (fontData->facePtr->glyph->advance.x)
				fontData->facePtr->glyph->advance.x += dx;

			if (fontData->facePtr->glyph->advance.y)
				fontData->facePtr->glyph->advance.y += dy;

			fontData->facePtr->glyph->metrics.width        += dx;
			fontData->facePtr->glyph->metrics.height       += dy;
			fontData->facePtr->glyph->metrics.horiBearingY += dy;
			fontData->facePtr->glyph->metrics.horiAdvance  += dx;
			fontData->facePtr->glyph->metrics.vertBearingX -= dx / 2;
			fontData->facePtr->glyph->metrics.vertBearingY += dy;
			fontData->facePtr->glyph->metrics.vertAdvance  += dy;
		}
	}
	if ((fontData->style & FT_STYLE_FLAG_ITALIC) != 0)
	{
		// Apply italics effect
		if (fontData->facePtr->glyph->format == FT_GLYPH_FORMAT_OUTLINE)
		{
			/* For italic, simply apply a shear transform, with an angle */
			/* of about 12 degrees.                                      */

			FT_Matrix    transform;
			transform.xx = 0x10000L;
			transform.yx = 0x00000L;

			transform.xy = 0x06000L;
			transform.yy = 0x10000L;

			FT_BBox bbox_before, bbox_after;
			FT_Outline_Get_CBox(&fontData->facePtr->glyph->outline, &bbox_before);
			FT_Outline_Transform(&fontData->facePtr->glyph->outline, &transform);
			FT_Outline_Get_CBox(&fontData->facePtr->glyph->outline, &bbox_after);

			FT_Pos dx = bbox_after.xMax - bbox_before.xMax;
			FT_Pos dy = bbox_after.yMax - bbox_before.yMax;

			fontData->facePtr->glyph->metrics.width        += dx;
			fontData->facePtr->glyph->metrics.height       += dy;
		}
	}
}

/*
 * Class:     FreetypeFont
 * Method:    renderGlyph0
 * Signature: (JLjava/awt/image/BufferedImage;II)I
 */
JNIEXPORT jint JNICALL Java_sage_FreetypeFont_renderGlyph0
  (JNIEnv *env, jobject jo, jlong fontPtr, jobject buffImage, jint imageX, jint imageY)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	FT_Face face = fontData->facePtr;
	FT_Activate_Size(fontData->sizePtr);
	int error = FT_Render_Glyph(face->glyph, ft_render_mode_normal);
	if (error)
		return error;
	static jmethodID biSetRGB = 0;
	static jmethodID biGetWidth = 0;
	static jmethodID biGetHeight = 0;
	if (!biSetRGB)
	{
		biSetRGB = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, buffImage), "setRGB", "(III)V");
		biGetWidth = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, buffImage), "getWidth", "()I");
		biGetHeight = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, buffImage), "getHeight", "()I");
	}
	int width = face->glyph->bitmap.width;
	int height = face->glyph->bitmap.rows;
	int x=0,y=0;
	imageX += face->glyph->bitmap_left;
	imageY -= face->glyph->bitmap_top;
//	imageY += (face->size->metrics.ascender >> 6);
	int biWidth = (*env)->CallIntMethod(env, buffImage, biGetWidth);
	int biHeight = (*env)->CallIntMethod(env, buffImage, biGetHeight);
	unsigned char* bufPtr = face->glyph->bitmap.buffer;
	for (;y < height; y++, bufPtr += face->glyph->bitmap.pitch)
	{
		if (imageY + y < 0 || imageY + y >= biHeight)
			continue;
		for (x = 0; x < width; x++)
		{
			if (imageX + x < 0 || imageX + x >= biWidth)
				continue;
			unsigned char bv = *(bufPtr + x);
			int color = (bv << 24) | (bv << 16) | (bv << 8) | bv;
			(*env)->CallVoidMethod(env, buffImage, biSetRGB, x + imageX, y + imageY, color);
		}
	}
	return 0;
}

/*
 * Class:     sage_FreetypeFont
 * Method:    renderGlyphRaw0
 * Signature: (JLsage/media/image/RawImage;IIII)Lsage/media/image/RawImage;
 */
JNIEXPORT jobject JNICALL Java_sage_FreetypeFont_renderGlyphRaw0
  (JNIEnv *env, jobject jo, jlong fontPtr, jobject inRawImage, jint rawWidth, jint rawHeight, jint imageX, jint imageY)
{
	static jclass rawImageClass = 0;
	static jmethodID rawImageConstruct = 0;
	static jmethodID getDataMeth = 0;
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
		{
			return NULL;
		}
	}
	jobject rv = inRawImage;
	unsigned char* myImageData = NULL;
	if (!rv)
	{
		sysOutPrint(env, "Creating new RawImage for font rendering w=%d h=%d\r\n", rawWidth, rawHeight);
		// Create the RawImage object and the native buffer for it if it doesn't exist yet
		myImageData = (unsigned char*) calloc(4*rawWidth*rawHeight, 1);
		jobject dbuf = (*env)->NewDirectByteBuffer(env, (void*)myImageData, 4*rawWidth*rawHeight);
		if ((*env)->ExceptionOccurred(env))
		{
			free(myImageData);
			return NULL;
		}

		rv = (*env)->NewObject(env, rawImageClass, rawImageConstruct, rawWidth, rawHeight, 
			dbuf, JNI_TRUE, 4*rawWidth);
		if ((*env)->ExceptionOccurred(env))
		{
			free(myImageData);
			return NULL;
		}
	}
	else
	{
		// Get the address to the native image buffer so we can write to it
		jobject bb = (*env)->CallObjectMethod(env, rv, getDataMeth);
		if ((*env)->ExceptionOccurred(env))
		{
			return NULL;
		}
		myImageData = (unsigned char*) (*env)->GetDirectBufferAddress(env, bb);
	}

	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	FT_Face face = fontData->facePtr;
	FT_Activate_Size(fontData->sizePtr);
	// Render the glyph to a FT buffer
	int error = FT_Render_Glyph(face->glyph, ft_render_mode_normal);
	if (error)
		return NULL;
	int width = face->glyph->bitmap.width;
	int height = face->glyph->bitmap.rows;
	int x=0,y=0;
	imageX += face->glyph->bitmap_left;
	imageY -= face->glyph->bitmap_top;
//	imageY += (face->size->metrics.ascender >> 6);
	unsigned char* bufPtr = face->glyph->bitmap.buffer;
	int yOff, off;
	// Copy the glyph to our image buffer
	for (;y < height; y++, bufPtr += face->glyph->bitmap.pitch)
	{
		if (imageY + y < 0 || imageY + y >= rawHeight)
			continue;
		yOff = (imageY + y)*rawWidth*4;
		for (x = 0; x < width; x++)
		{
			if (imageX + x < 0 || imageX + x >= rawWidth)
				continue;
			off = yOff + (imageX + x)*4;
			unsigned char bv = *(bufPtr + x);
			myImageData[off] = bv;
			myImageData[off + 1] = bv;
			myImageData[off + 2] = bv;
			myImageData[off + 3] = bv;
		}
	}
	return rv;
}

/*
 * Class:     FreetypeFont
 * Method:    getNumGlyphs0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_FreetypeFont_getNumGlyphs0
  (JNIEnv *env, jobject jo, jlong fontPtr)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	return fontData->facePtr->num_glyphs;
}
/*
 * Class:     FreetypeFont
 * Method:    getGlyphWidth0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_FreetypeFont_getGlyphWidth0
  (JNIEnv *env, jobject jo, jlong fontPtr)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	return fontData->facePtr->glyph->metrics.width;
}

/*
 * Class:     FreetypeFont
 * Method:    getGlyphHeight0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_FreetypeFont_getGlyphHeight0
  (JNIEnv *env, jobject jo, jlong fontPtr)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	return fontData->facePtr->glyph->metrics.height;
}

/*
 * Class:     sage_FreetypeFont
 * Method:    getGlyphBearingX0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_FreetypeFont_getGlyphBearingX0
  (JNIEnv *env, jobject jo, jlong fontPtr)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	return fontData->facePtr->glyph->metrics.horiBearingX;
}

/*
 * Class:     sage_FreetypeFont
 * Method:    getGlyphBearingY0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_FreetypeFont_getGlyphBearingY0
  (JNIEnv *env, jobject jo, jlong fontPtr)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	return fontData->facePtr->glyph->metrics.horiBearingY;
}

/*
 * Class:     sage_FreetypeFont
 * Method:    getGlyphAdvance0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_FreetypeFont_getGlyphAdvance0
  (JNIEnv *env, jobject jo, jlong fontPtr)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	return fontData->facePtr->glyph->advance.x;
}

/*
 * Class:     sage_FreetypeFont
 * Method:    getFontHeight0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_FreetypeFont_getFontHeight0
  (JNIEnv *env, jobject jo, jlong fontPtr)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	return fontData->sizePtr->metrics.height;
}

/*
 * Class:     sage_FreetypeFont
 * Method:    getFontAscent0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_FreetypeFont_getFontAscent0
  (JNIEnv *env, jobject jo, jlong fontPtr)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	return fontData->sizePtr->metrics.ascender;
}

/*
 * Class:     sage_FreetypeFont
 * Method:    getFontDescent0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sage_FreetypeFont_getFontDescent0
  (JNIEnv *env, jobject jo, jlong fontPtr)
{
	FTDataStruct* fontData = (FTDataStruct*)(intptr_t) fontPtr;
	return fontData->sizePtr->metrics.descender;
}
