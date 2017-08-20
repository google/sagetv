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
public class RawImage
{
  // These images have premultiplied alpha and 4 bytes per pixel in ARGB format.
  public RawImage(int width, int height, java.nio.ByteBuffer data, boolean hasAlpha, int stride)
  {
    this.width = width;
    this.height = height;
    this.dataBuff = data;
    this.hasAlpha = hasAlpha;
    this.stride = stride;
  }

  public RawImage(int width, int height, java.nio.ByteBuffer data, boolean hasAlpha, int stride, boolean javaAlloced)
  {
    this.width = width;
    this.height = height;
    this.dataBuff = data;
    this.hasAlpha = hasAlpha;
    this.stride = stride;
    internalAlloc = javaAlloced;
  }

  public static boolean canCreateRawFromJava(java.awt.image.BufferedImage bi)
  {
    return (bi.getType() == java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE) ||
        (bi.getType() == java.awt.image.BufferedImage.TYPE_INT_ARGB);
  }

  public RawImage(java.awt.image.BufferedImage bi)
  {
    if (bi.getType() != java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE && bi.getType() != java.awt.image.BufferedImage.TYPE_INT_ARGB)
    {
      throw new IllegalArgumentException("Can only create RawImages from ARGB_PRE BIs, was given: " + bi.getType());
    }
    if (!bi.isAlphaPremultiplied())
    {
      //if (sage.Sage.DBG) System.out.println("Premultiplying alpha channel for RawImage creation from Java image");
      bi.coerceData(true);
    }
    width = bi.getWidth();
    height = bi.getHeight();
    hasAlpha = true;
    stride = 4*width;
    dataBuff = java.nio.ByteBuffer.allocateDirect(4*width*height);
    dataBuff.asIntBuffer().put(((java.awt.image.DataBufferInt) bi.getRaster().getDataBuffer()).getData());
    dataBuff.limit(4*width*height);
    internalAlloc = true;
  }

  public int getWidth()
  {
    return width;
  }

  public int getHeight()
  {
    return height;
  }

  public java.nio.ByteBuffer getData()
  {
    return dataBuff;
  }

  // For multithreading we need to give each caller a new buffer object to handle the positions; but share the same data
  public java.nio.ByteBuffer getROData()
  {
    dataBuff.mark(); // GCJ needs this or it throws an exception
    java.nio.ByteBuffer rv = dataBuff.asReadOnlyBuffer();
    rv.rewind();
    return rv;
  }

  public boolean hasAlpha()
  {
    return hasAlpha;
  }

  public int getStride()
  {
    return stride;
  }

  public java.awt.image.BufferedImage convertToBufferedImage()
  {
    java.awt.image.BufferedImage rv = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
    // NOTE: THIS MAY HAVE ISSUES WITH DIFFERENT ENDIANS ON VARIOUS PLATFORMS
    java.nio.ByteBuffer tempData = getROData();
    tempData.rewind();
    tempData.asIntBuffer().get(((java.awt.image.DataBufferInt) rv.getRaster().getDataBuffer()).getData());
    return rv;
  }

  // If true, the native buffer data should NOT be freed
  public boolean isInternalAlloc()
  {
    return internalAlloc;
  }

  public String toString()
  {
    return "RawImage[" + width + "x" + height + " alpha=" + hasAlpha + " stride=" + stride + " bufferCapacity=" +
      (dataBuff != null ? dataBuff.capacity() : 0) + "]";
  }

  private int width;
  private int height;
  private java.nio.ByteBuffer dataBuff;
  private boolean hasAlpha;
  private int stride; // in bytes
  private boolean internalAlloc = false;
}
