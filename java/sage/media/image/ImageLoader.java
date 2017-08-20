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
package sage.media.image;

/**
 *
 * @author Narflex
 */
public class ImageLoader
{
  // Replicate this here since the miniclient uses this class as well and we don't
  // want to pull in the whole SageTV codebase
  static
  {
    try
    {
      sage.Native.loadLibrary("ImageLoader");
    }
    catch (Throwable t)
    {
      System.out.println("ERROR loading native library of:" + t);
      t.printStackTrace();
    }
  }
  private ImageLoader()
  {
  }

  // NOTE: The returned image must be freed with a call to freeImage
  public static RawImage loadImageFromFile(String filePath) throws java.io.IOException
  {
    return loadScaledImageFromFile(filePath, 0, 0, 32, 0);
  }
  public static RawImage loadResizedImageFromFile(String filePath, int width, int height) throws java.io.IOException
  {
    return loadScaledImageFromFile(filePath, width, height, 32, 0);
  }
  public static RawImage loadResizedRotatedImageFromFile(String filePath, int width, int height, int bpp, int rotation) throws java.io.IOException
  {
    return loadScaledImageFromFile(filePath, width, height, bpp, rotation);
  }
  public static RawImage loadImageFromMemory(byte[] imgdata) throws java.io.IOException
  {
    return loadScaledImageFromMemory(imgdata, 0, 0);
  }
  public static RawImage loadScaledImageFromMemory(byte[] imgdata, int imageWidth, int imageHeight) throws java.io.IOException
  {
    return loadScaledImageFromMemory(imgdata, imageWidth, imageHeight, 32, 0);
  }
  // bpp and rotateAmount are ONLY valid for JPEG files
  public static RawImage loadScaledImageFromMemory(byte[] imgdata, int imageWidth, int imageHeight, int bpp, int rotation) throws java.io.IOException
  {
    if (imgdata == null) return null;
    java.io.File tempFile = java.io.File.createTempFile("stv", ".img");
    java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
    try
    {
      fos.write(imgdata);
      fos.close();
      fos = null;
      RawImage rv;
      rv = loadScaledImageFromFile(tempFile.toString(), imageWidth, imageHeight, bpp, rotation);
      return rv;
    }
    finally
    {
      if (fos != null)
        fos.close();
      tempFile.delete();
    }
  }

  public static RawImage loadImageDimensionsFromMemory(byte[] imgdata) throws java.io.IOException
  {
    if (imgdata == null) return null;
    java.io.File tempFile = java.io.File.createTempFile("stv", ".img");
    java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
    try
    {
      fos.write(imgdata);
      fos.close();
      fos = null;
      RawImage rv = loadImageDimensionsFromFile(tempFile.toString());
      return rv;
    }
    finally
    {
      if (fos != null)
        fos.close();
      tempFile.delete();
    }
  }

  private static RawImage nothingImage;
  public static RawImage getNullImage()
  {
    if (nothingImage == null)
    {
      nothingImage = createNullImage();
    }
    return nothingImage;
  }
  public static RawImage createNullImage()
  {
    return createNullImage(16, 16);
  }
  public static RawImage createNullImage(int width, int height)
  {
    java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(width*height*4);
    if (bb.hasArray())
      java.util.Arrays.fill(bb.array(), (byte)0);
    return new RawImage(width, height, bb, true, width*4, true);
  }

  // Creates a scaled copy of a RawImage with optional scaling insets (order is top,left,bottom,right w/ src and then dest; length of array is 8)
  // NOTE: The returned image must be freed with a call to freeImage
  public static RawImage scaleRawImage(RawImage srcImage, int imageWidth, int imageHeight)
  {
    return scaleRawImage(srcImage, imageWidth, imageHeight, null);
  }
  public static RawImage scaleRawImageWithInsets(RawImage srcImage, int imageWidth, int imageHeight, int[] insets)
  {
    return scaleRawImage(srcImage, imageWidth, imageHeight, insets);
  }

  public static void freeImage(RawImage img)
  {
    if (!img.isInternalAlloc())
      freeImage0(img.getData());
  }

  public static byte[] compressImageToMemory(RawImage img, String format)
  {
    byte[] rv = null;
    java.io.DataInputStream fis = null;
    java.io.File tempFile = null;
    try
    {
      tempFile = java.io.File.createTempFile("stv", ".img");
      if (!compressImageToFile(img, tempFile.toString(), format))
        return null;
      rv = new byte[(int)tempFile.length()];
      fis = new java.io.DataInputStream(new java.io.FileInputStream(tempFile));
      fis.readFully(rv);
    }
    catch (Exception e)
    {
      System.out.println("ERROR compressing image to memory of:" + e);
      return null;
    }
    finally
    {
      if (fis != null)
      {
        try
        {
          fis.close();
        }catch (Exception e)
        {}
      }
      if (tempFile != null)
        tempFile.delete();
    }
    return rv;
  }

  public static boolean compressImageToFilePath(RawImage img, String filePath, String format)
  {
    return compressImageToFile(img, filePath, format);
  }

  // NOTE: These are native methods for desktop & embedded
  // NOTE: These are native methods for desktop & embedded
  public static native boolean createThumbnail(String imageFilePath,
      String thumbnailPath, int thumbWidth, int thumbHeight, int rotateAmount) throws java.io.IOException;

  // Creates a RawImage object with just the width & height set in it.
  public static native RawImage loadImageDimensionsFromFile(String filePath) throws java.io.IOException;


  // NOTE: These are native methods for DESKTOP ONLY
  // NOTE: These are native methods for DESKTOP ONLY
  private static native boolean compressImageToFile(RawImage img, String filePath, String format);

  private static native void freeImage0(java.nio.ByteBuffer bb);

  // NOTE: The returned image must be freed with a call to freeImage
  // bpp and rotateAmount are ONLY valid for JPEG files
  private static native RawImage loadScaledImageFromFile(String filePath, int imageWidth, int imageHeight, int bpp, int rotateAmount) throws java.io.IOException;

  private static native RawImage scaleRawImage(RawImage srcImage, int imageWidth, int imageHeight, int[] scalingInsets);

  // NOTE: These are native methods for EMBEDDED ONLY
  // NOTE: These are native methods for EMBEDDED ONLY
  private static native RawImage loadScaledImageFromFile(String filePath, int imageWidth, int imageHeight) throws java.io.IOException;

}
