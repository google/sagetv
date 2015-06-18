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
package sage;

public class ImageUtils
{
  public static final int DEFAULT_TRANSPARENCY = java.awt.Transparency.TRANSLUCENT;
  // Java 1.5.0_04 even has bugs in it with JPEG image loading from some digital cameras. This works OK
  // if we use the native image loader though, so we default to that now to prevent unhappy picture library
  // users in the future. :)
  private static final boolean USE_AWT_TOOLKIT_IMAGE_LOADING = Sage.getBoolean("ui/load_images_with_awt_toolkit", Sage.WINDOWS_OS);
  private static java.awt.image.BufferedImage nothingImage;
  public static java.awt.image.BufferedImage getNullImage()
  {
    if (nothingImage == null)
    {
      nothingImage = createNullImage();
    }
    return nothingImage;
  }
  public static java.awt.image.BufferedImage createNullImage()
  {
    return new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
  }
  public static java.awt.image.BufferedImage scaleColorsToNTSC(java.awt.image.BufferedImage inImage)
  {
    java.awt.image.RescaleOp ntscScaler = new java.awt.image.RescaleOp(196f/256f, 30f, null);
    return ntscScaler.filter(inImage, inImage);
  }
  public static java.awt.image.BufferedImage fullyLoadImage(String imageName)
  {
    if (imageName.startsWith("http:") || imageName.startsWith("ftp:"))
    {
      try
      {
        return fullyLoadImage(new java.net.URL(imageName));
      }
      catch (java.net.MalformedURLException e)
      {
        return getNullImage();
      }
    }
    java.net.URL imageURL = ImageUtils.class.getClassLoader().
        getResource(imageName);
    if (imageURL == null)
    {
      //if (Sage.DBG) System.out.println("ERROR loading image: \"" + imageName + '"');
      return getNullImage();
    }
    return fullyLoadImage(imageURL);
  }
  public static java.awt.image.BufferedImage fullyLoadImage(java.io.File imageFile)
  {
    if (Sage.DBG) System.out.println("ImageUtils loading file " + imageFile);
    try
    {
      java.awt.Image rv = USE_AWT_TOOLKIT_IMAGE_LOADING ?
          java.awt.Toolkit.getDefaultToolkit().createImage(imageFile.toString()) :
            javax.imageio.ImageIO.read(imageFile);
          Sage.gc();
          return (rv == null) ? getNullImage() : createBestImage(rv);
    }
    catch (Throwable e)
    {
      System.out.println("ERROR loading image of:" + e);
      return getNullImage();
    }
  }
  public static java.awt.image.BufferedImage fullyLoadImage(java.net.URL imageURL)
  {
    if (Sage.DBG) System.out.println("ImageUtils loading URL " + imageURL);
    try
    {
      java.awt.Image rv = USE_AWT_TOOLKIT_IMAGE_LOADING ?
          java.awt.Toolkit.getDefaultToolkit().createImage(imageURL) :
            javax.imageio.ImageIO.read(imageURL);
          Sage.gc();
          return (rv == null) ? getNullImage() : createBestImage(rv);
    }
    catch (Throwable e)
    {
      System.out.println("ERROR loading image of:" + e);
      return getNullImage();
    }
  }
  public static java.awt.image.BufferedImage fullyLoadImage(byte[] imageData, int offset, int length)
  {
    if (Sage.DBG) System.out.println("ImageUtils loading binary data length=" + length);
    try
    {
      java.awt.Image rv = USE_AWT_TOOLKIT_IMAGE_LOADING ?
          java.awt.Toolkit.getDefaultToolkit().createImage(imageData, offset, length) :
            javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageData,
                offset, length));
          Sage.gc();
          return (rv == null) ? getNullImage() : createBestImage(rv);
    }
    catch (Throwable e)
    {
      System.out.println("ERROR loading image of:" + e);
      return getNullImage();
    }
  }

  public static void ensureImageIsLoaded(java.awt.Image theImage)
  {
    // To actually get an image to fully load is a multi-step process.
    // First you have to create the image resource through the toolkit.
    // Second you have to call prepareImage on the toolkit and register
    // an ImageObserver to be notified of state changes.
    // Lastly you have to listen for the final loaded state change.
    final Object imageLock = new Object();
    final boolean[] imageStats = new boolean[2];
    imageStats[0] = false;
    java.awt.image.ImageObserver watcher = new java.awt.image.ImageObserver()
    {
      public boolean imageUpdate(java.awt.Image img, int infoflags,
          int x, int y, int width, int height)
      {
        synchronized (imageLock)
        {
          if (((infoflags & ALLBITS) == ALLBITS) ||
              ((infoflags & FRAMEBITS) == FRAMEBITS))
          {
            imageLock.notify();
            imageStats[0] = true;
            imageStats[1] = true;
            return false;
          }
          else if (((infoflags & ERROR) == ERROR) ||
              ((infoflags & ABORT) == ABORT))
          {
            imageLock.notify();
            imageStats[0] = true;
            imageStats[1] = false;
            return true;
          }
          else
          {
            return true;
          }
        }
      }
    };
    if (!java.awt.Toolkit.getDefaultToolkit().prepareImage(theImage,
        -1, -1, watcher))
    {
      long startWait = Sage.eventTime();
      synchronized (imageLock)
      {
        while (!imageStats[0] && Sage.eventTime() - startWait < 30000)
        {
          try
          {
            imageLock.wait(5000);
          }
          catch (InterruptedException e)
          {}
        }
      }
    }
    if (!imageStats[1])
    {
      //if (Sage.DBG) System.out.println("Error ensuring image loading for:" + theImage);
    }
  }

  // This is because when you call coerceData on a BufferedImage it doesn't update the 'type' of that
  // image to reflect the pre-multiplication state. This can mess up Java2D rendering.
  public static void fixAlphaInconsistency(java.awt.Image img)
  {
    if (img instanceof java.awt.image.BufferedImage)
    {
      java.awt.image.BufferedImage bi = (java.awt.image.BufferedImage) img;
      if (bi.getType() == java.awt.image.BufferedImage.TYPE_INT_ARGB ||
          bi.getType() == java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE)
      {
        if ((bi.getType() == java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE) != bi.isAlphaPremultiplied())
        {
          if (Sage.DBG) System.out.println("Fixing alpha state for image so type matches color model:" + img);
          bi.coerceData(bi.getType() == java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
        }
      }
    }
  }

  public static java.awt.image.BufferedImage createBestImage(java.awt.Image myImage)
  {
    try
    {
      fixAlphaInconsistency(myImage);
      if (myImage instanceof java.awt.image.BufferedImage && (((java.awt.image.BufferedImage) myImage).getType() ==
          java.awt.image.BufferedImage.TYPE_INT_ARGB ||
          ((java.awt.image.BufferedImage) myImage).getType() ==
          java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE))
        return (java.awt.image.BufferedImage) myImage;
      if (USE_AWT_TOOLKIT_IMAGE_LOADING)
        ensureImageIsLoaded(myImage);

      if (Sage.DBG) System.out.println("ImageUtils creating BI copy " + myImage + " w=" + myImage.getWidth(null) +
          " h=" + myImage.getHeight(null) + " freeMem=" + Runtime.getRuntime().freeMemory() + " totalMem=" + Runtime.getRuntime().totalMemory());
      if (myImage.getWidth(null) <= 0 || myImage.getHeight(null) <= 0)
        myImage = getNullImage();
      java.awt.image.BufferedImage retVal = new java.awt.image.BufferedImage(myImage.getWidth(null),
          myImage.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_ARGB);
      java.awt.Graphics2D g2 = retVal.createGraphics();
      g2.setComposite(java.awt.AlphaComposite.Src);
      g2.drawImage(myImage, 0, 0, null);
      g2.dispose();
      g2 = null;
      Sage.gc();
      return retVal;
    }
    catch (Throwable e)
    {
      System.out.println("ERROR loading image of:" + e);
      return getNullImage();
    }
  }

  public static java.awt.image.BufferedImage cloneImage(java.awt.Image myImage)
  {
    try
    {
      if (USE_AWT_TOOLKIT_IMAGE_LOADING)
        ensureImageIsLoaded(myImage);

      if (Sage.DBG) System.out.println("ImageUtils creating BI copy " + myImage);
      fixAlphaInconsistency(myImage);
      if (myImage.getWidth(null) <= 0 || myImage.getHeight(null) <= 0)
        myImage = getNullImage();
      java.awt.image.BufferedImage retVal = new java.awt.image.BufferedImage(myImage.getWidth(null),
          myImage.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_ARGB);
      java.awt.Graphics2D g2 = retVal.createGraphics();
      g2.setComposite(java.awt.AlphaComposite.Src);
      g2.drawImage(myImage, 0, 0, null);
      g2.dispose();
      g2 = null;
      Sage.gc();
      return retVal;
    }
    catch (Throwable e)
    {
      System.out.println("ERROR loading image of:" + e);
      return getNullImage();
    }
  }

  public static java.awt.image.BufferedImage createBestScaledImage(java.awt.Image myImage,
      int width, int height)
  {
    try
    {
      if (Sage.DBG) System.out.println("ImageUtils creating scaled copy width=" + width + " height=" + height + " " + myImage);
      fixAlphaInconsistency(myImage);
      if (myImage.getWidth(null) <= 0 || myImage.getHeight(null) <= 0)
        myImage = getNullImage();
      java.awt.image.BufferedImage rv = new java.awt.image.BufferedImage(width,
          height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
      java.awt.Graphics2D g2 = rv.createGraphics();
      g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
          java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.setRenderingHint(java.awt.RenderingHints.KEY_ALPHA_INTERPOLATION,
          java.awt.RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
      g2.setComposite(java.awt.AlphaComposite.Src);
      g2.drawImage(myImage, 0, 0, width, height, null);
      g2.dispose();

      Sage.gc();
      return rv;
    }
    catch (Throwable e)
    {
      System.out.println("ERROR loading image of:" + e);
      return getNullImage();
    }
  }

  public static java.awt.image.BufferedImage createBestScaledImage(java.awt.Image myImage,
      int width, int height, Object imageOption)
  {
    try
    {
      if (Sage.DBG) System.out.println("ImageUtils creating scaled masked copy width=" + width + " height=" +
          height + " option=" + imageOption + " " + myImage);
      fixAlphaInconsistency(myImage);
      if (myImage.getWidth(null) <= 0 || myImage.getHeight(null) <= 0)
        myImage = getNullImage();
      java.awt.image.BufferedImage rv = new java.awt.image.BufferedImage(width,
          height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
      java.awt.Graphics2D g2 = rv.createGraphics();
      if (imageOption instanceof java.awt.Shape)
      {
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setComposite(java.awt.AlphaComposite.Src);
        g2.setColor(java.awt.Color.white);
        g2.fill((java.awt.Shape)imageOption);
        g2.setComposite(java.awt.AlphaComposite.SrcAtop);
      }
      else
        g2.setComposite(java.awt.AlphaComposite.Src);
      g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
          java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.setRenderingHint(java.awt.RenderingHints.KEY_ALPHA_INTERPOLATION,
          java.awt.RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
      if (imageOption instanceof java.awt.Insets[])
      {
        // Scaling insets. This is 9 separate copies
        java.awt.Insets[] scalingInsets = (java.awt.Insets[]) imageOption;
        // top left
        g2.drawImage(myImage, 0, 0, scalingInsets[1].left, scalingInsets[1].top, 0, 0, scalingInsets[0].left, scalingInsets[0].top, null);
        // top right
        g2.drawImage(myImage, width - scalingInsets[1].right, 0, width - 1, scalingInsets[1].top,
            myImage.getWidth(null) - scalingInsets[0].right, 0, myImage.getWidth(null) - 1, scalingInsets[0].top, null);
        // bottom left
        g2.drawImage(myImage, 0, height - scalingInsets[1].bottom, scalingInsets[1].left, height - 1,
            0, myImage.getHeight(null) - scalingInsets[0].bottom, scalingInsets[0].left, myImage.getHeight(null) - 1, null);
        // bottom right
        g2.drawImage(myImage, width - scalingInsets[1].right, height - scalingInsets[1].bottom, width - 1, height - 1,
            myImage.getWidth(null) - scalingInsets[0].right, myImage.getHeight(null) - scalingInsets[0].bottom,
            myImage.getWidth(null) - 1, myImage.getHeight(null) - 1, null);
        // top
        g2.drawImage(myImage, scalingInsets[1].left, 0, width - scalingInsets[1].right, scalingInsets[1].top,
            scalingInsets[0].left, 0, myImage.getWidth(null) - scalingInsets[0].right, scalingInsets[0].top, null);
        // bottom
        g2.drawImage(myImage, scalingInsets[1].left, height - scalingInsets[1].bottom, width - scalingInsets[1].right, height - 1,
            scalingInsets[0].left, myImage.getHeight(null) - scalingInsets[0].bottom,
            myImage.getWidth(null) - scalingInsets[0].right, myImage.getHeight(null) - 1, null);
        // left
        g2.drawImage(myImage, 0, scalingInsets[1].top, scalingInsets[1].left, height - scalingInsets[1].bottom,
            0, scalingInsets[0].top, scalingInsets[0].left, myImage.getHeight(null) - scalingInsets[0].bottom, null);
        // right
        g2.drawImage(myImage, width - scalingInsets[1].right, scalingInsets[1].top, width - 1, height - scalingInsets[1].bottom,
            myImage.getWidth(null) - scalingInsets[0].right, scalingInsets[0].top,
            myImage.getWidth(null) - 1, myImage.getHeight(null) - scalingInsets[0].bottom, null);
        // center
        g2.drawImage(myImage, scalingInsets[1].left, scalingInsets[1].top, width - scalingInsets[1].right,
            height - scalingInsets[1].bottom,
            scalingInsets[0].left, scalingInsets[0].top, myImage.getWidth(null) - scalingInsets[0].right,
            myImage.getHeight(null) - scalingInsets[0].bottom, null);
      }
      else
        g2.drawImage(myImage, 0, 0, width, height, null);
      g2.dispose();

      Sage.gc();
      return rv;
    }
    catch (Throwable e)
    {
      System.out.println("ERROR loading image of:" + e);
      return getNullImage();
    }
  }

  public static java.awt.image.BufferedImage createBestOpaqueScaledImage(java.awt.Image myImage, int width, int height)
  {
    try
    {
      if (Sage.DBG) System.out.println("ImageUtils creating opaque scaled copy width=" + width + " height=" + height + " " + myImage);
      if (myImage.getWidth(null) <= 0 || myImage.getHeight(null) <= 0)
        myImage = getNullImage();
      java.awt.image.BufferedImage rv = new java.awt.image.BufferedImage(width,
          height, java.awt.image.BufferedImage.TYPE_INT_RGB);
      java.awt.Graphics2D g2 = rv.createGraphics();
      g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
          java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.setComposite(java.awt.AlphaComposite.Src);
      g2.drawImage(myImage, 0, 0, width, height, null);
      g2.dispose();
      Sage.gc();
      return rv;
    }
    catch (Throwable e)
    {
      System.out.println("ERROR loading image of:" + e);
      return getNullImage();
    }
  }

  public static java.awt.image.BufferedImage rotateImage(java.awt.image.BufferedImage srcImage, int rotateAmount)
  {
    if (rotateAmount == 0) return srcImage;
    rotateAmount = (rotateAmount + 3600) % 360;
    if (rotateAmount != 90 && rotateAmount != 270)
      throw new IllegalArgumentException("Invalid rotateAmount in rotateImage of: " + rotateAmount);
    int width = srcImage.getWidth();
    int height = srcImage.getHeight();
    java.awt.image.BufferedImage rv = new java.awt.image.BufferedImage(height, width, srcImage.getType());
    if (rotateAmount == 90)
    {
      for (int x = 0; x < width; x++)
      {
        for (int y = 0; y < height; y++)
        {
          rv.setRGB(height - y - 1, x, srcImage.getRGB(x, y));
        }
      }
    }
    else
    {
      for (int x = 0; x < width; x++)
      {
        for (int y = 0; y < height; y++)
        {
          rv.setRGB(y, width - x - 1, srcImage.getRGB(x, y));
        }
      }
    }
    return rv;
  }

  public static java.awt.image.BufferedImage createDiffusedImage(java.awt.Image srcImage, java.awt.Image diffuseImage, java.awt.Rectangle srcRect, boolean flipX, boolean flipY,
      int diffuseColor)
  {
    try
    {
      java.awt.image.BufferedImage biSrc = createBestImage(srcImage);
      if (srcRect != null)
        biSrc = biSrc.getSubimage(srcRect.x, srcRect.y, srcRect.width, srcRect.height);
      java.awt.image.BufferedImage biDiffuse = diffuseImage == null ? null : createBestScaledImage(diffuseImage, biSrc.getWidth(), biSrc.getHeight());
      java.awt.image.BufferedImage rv = new java.awt.image.BufferedImage(biSrc.getWidth(), biSrc.getHeight(),
          java.awt.image.BufferedImage.TYPE_INT_ARGB);
      int width = biSrc.getWidth();
      int height = biSrc.getHeight();
      if (Sage.DBG) System.out.println("ImageUtils creating diffused image width=" + width + " height=" + height + " srcImage=" + srcImage);
      if (biDiffuse != null)
      {
        for (int x = 0; x < width; x++)
        {
          for (int y = 0; y < height; y++)
          {
            int srcPix = biSrc.getRGB(x, y);
            int diffusePix = biDiffuse.getRGB(x, y);
            int res = (((((srcPix >>> 24) & 0xFF) * ((diffusePix >>> 24) & 0xFF)) / 255) << 24) |
                (((((srcPix >>> 16) & 0xFF) * ((diffusePix >>> 16) & 0xFF)) / 255) << 16) |
                (((((srcPix >>> 8) & 0xFF) * ((diffusePix >>> 8) & 0xFF)) / 255) << 8) |
                (((srcPix & 0xFF) * (diffusePix & 0xFF)) / 255);
            if (diffuseColor != 0xFFFFFF)
            {
              res = (res & 0xFF000000) |
                  (((((res >>> 16) & 0xFF) * ((diffuseColor >>> 16) & 0xFF)) / 255) << 16) |
                  (((((res >>> 8) & 0xFF) * ((diffuseColor >>> 8) & 0xFF)) / 255) << 8) |
                  (((res & 0xFF) * (diffuseColor & 0xFF)) / 255);
            }
            rv.setRGB(flipX ? (width - x - 1) : x, flipY ? (height - y - 1) : y, res);
          }
        }
      }
      else
      {
        int wn = width - 1;
        int hn = height - 1;
        for (int x = 0; x < width; x++)
        {
          for (int y = 0; y < height; y++)
          {
            int res = biSrc.getRGB(x, y);
            if (diffuseColor != 0xFFFFFF)
            {
              res = (res & 0xFF000000) |
                  (((((res >>> 16) & 0xFF) * ((diffuseColor >>> 16) & 0xFF)) / 255) << 16) |
                  (((((res >>> 8) & 0xFF) * ((diffuseColor >>> 8) & 0xFF)) / 255) << 8) |
                  (((res & 0xFF) * (diffuseColor & 0xFF)) / 255);
            }
            rv.setRGB(flipX ? (wn - x) : x, flipY ? (hn - y) : y, res);
          }
        }
      }
      return rv;
    }
    catch (Throwable e)
    {
      System.out.println("ERROR loading image of:" + e);
      return getNullImage();
    }
  }

  public static java.awt.image.BufferedImage addImageEffect(java.awt.Image src,
      int type)
  {
    try
    {
      // Create the image that is matched to the graphics rendering context
      // that we're in.
      java.awt.image.BufferedImage retVal = new java.awt.image.BufferedImage(src.getWidth(null),
          src.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_ARGB);

      java.awt.Graphics2D g2 = retVal.createGraphics();
      if ((type & CIRCLE_SLASH) == CIRCLE_SLASH)
      {
        g2.drawImage(src, 0, 0, null);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_DITHERING,
            java.awt.RenderingHints.VALUE_DITHER_ENABLE);
        g2.setColor(java.awt.Color.red);
        java.awt.Stroke oldStroke = g2.getStroke();
        g2.setStroke(new java.awt.BasicStroke(2f));
        java.awt.geom.Ellipse2D circle = new java.awt.geom.Ellipse2D.Double(
            1, 1, retVal.getWidth() - 2, retVal.getHeight() - 2);
        g2.draw(circle);
        g2.clip(circle);
        g2.drawLine(2, retVal.getHeight() - 4, retVal.getWidth() - 4, 2);
        g2.setStroke(oldStroke);
      }
      if ((type & OFFSET_PLUS) == OFFSET_PLUS)
      {
        g2.drawImage(src, 0, 0, null);
        int xOrigin = retVal.getWidth() - 16;
        int yOrigin = retVal.getHeight() - 16;
        int edge = 8;
        int plusOffset = 2;
        g2.setColor(java.awt.Color.white);
        g2.fillRect(xOrigin, yOrigin, edge, edge);
        g2.setColor(java.awt.Color.gray);
        g2.drawLine(xOrigin, yOrigin, xOrigin + edge, yOrigin);
        g2.drawLine(xOrigin, yOrigin, xOrigin, yOrigin + edge);
        g2.setColor(java.awt.Color.black);
        g2.drawLine(xOrigin + edge, yOrigin, xOrigin + edge, yOrigin + edge);
        g2.drawLine(xOrigin, yOrigin + edge, xOrigin + edge, yOrigin + edge);
        g2.drawLine(xOrigin + edge/2, yOrigin + plusOffset, xOrigin + edge/2,
            yOrigin + edge - plusOffset);
        g2.drawLine(xOrigin + plusOffset, yOrigin + edge/2, xOrigin + edge - plusOffset,
            yOrigin + edge/2);
      }
      if ((type & OFFSET_ARROW) == OFFSET_ARROW)
      {
        g2.drawImage(src, 0, 0, null);
        int xOrigin = retVal.getWidth() - 16;
        int yOrigin = retVal.getHeight() - 16;
        int edge = 8;
        int plusOffset = 2;
        g2.setColor(java.awt.Color.white);
        g2.fillRect(xOrigin, yOrigin, edge, edge);
        g2.setColor(java.awt.Color.gray);
        g2.drawLine(xOrigin, yOrigin, xOrigin + edge, yOrigin);
        g2.drawLine(xOrigin, yOrigin, xOrigin, yOrigin + edge);
        g2.setColor(java.awt.Color.black);
        g2.drawLine(xOrigin + edge, yOrigin, xOrigin + edge, yOrigin + edge);
        g2.drawLine(xOrigin, yOrigin + edge, xOrigin + edge, yOrigin + edge);
        g2.drawLine(xOrigin + plusOffset, yOrigin + plusOffset, xOrigin + edge - plusOffset,
            yOrigin + edge - plusOffset);
        g2.drawLine(xOrigin + edge - plusOffset, yOrigin + edge/2, xOrigin + edge - plusOffset,
            yOrigin + edge - plusOffset);
        g2.drawLine(xOrigin + plusOffset, yOrigin + edge - plusOffset, xOrigin + edge - plusOffset,
            yOrigin + edge - plusOffset);
      }
      g2.dispose();
      g2 = null;
      return retVal;
    }
    catch (Throwable e)
    {
      System.out.println("ERROR loading image of:" + e);
      return getNullImage();
    }
  }

  // NOTE: This is only valid for images created through createBestImage()
  public static java.awt.image.BufferedImage performColorSwap(java.awt.image.BufferedImage srcImage,
      final java.awt.Color inColor, final java.awt.Color outColor)
  {
    java.awt.image.BufferedImage retVal = new java.awt.image.BufferedImage(srcImage.getWidth(),
        srcImage.getHeight(), srcImage.getType());
    java.awt.image.LookupTable conversion = new java.awt.image.LookupTable(0, 3)
    {
      public int[] lookupPixel(int[] src, int[] dest)
      {
        if (dest == null)
        {
          dest = new int[3];
        }
        if ((inRed == src[0]) &&
            (inGreen == src[1]) &&
            (inBlue == src[2]))
        {
          dest[0] = outRed;
          dest[1] = outGreen;
          dest[2] = outBlue;
        }
        else
        {
          dest[0] = src[0];
          dest[1] = src[1];
          dest[2] = src[2];
        }
        return dest;
      }

      private int inRed = inColor.getRed();
      private int inGreen = inColor.getGreen();
      private int inBlue = inColor.getBlue();
      private int outRed = outColor.getRed();
      private int outGreen = outColor.getGreen();
      private int outBlue = outColor.getBlue();
    };
    java.awt.Graphics2D g2 = retVal.createGraphics();
    g2.drawImage(srcImage, new java.awt.image.LookupOp(conversion, null), 0, 0);
    g2.dispose();
    g2 = null;
    return retVal;
  }

  public static final int CIRCLE_SLASH = 0x01;
  public static final int TRANSPARENT = 0x02;
  public static final int OFFSET_PLUS = 0x04;
  public static final int OFFSET_ARROW = 0x08;

  public static java.awt.Cursor createCursor(String imageName, String cursorName,
      int hotX, int hotY, boolean isCopy, boolean isLink)
  {
    return createCursor(fullyLoadImage(imageName), cursorName, hotX, hotY, isCopy, isLink);
  }

  public static java.awt.Cursor createCursor(java.awt.Image icon,
      String cursorName, int hotX, int hotY, boolean isCopy, boolean isLink)
  {
    java.awt.Toolkit myToolkit = java.awt.Toolkit.getDefaultToolkit();

    int imageWidth = icon.getWidth(null);
    int imageHeight = icon.getHeight(null);

    // Figure out the size for this cursor.
    java.awt.Dimension cursorSize = myToolkit.
        getBestCursorSize(imageWidth, imageHeight);

    if (((cursorSize.width != imageWidth) ||
        (cursorSize.height != imageHeight)) &&
        (imageWidth < cursorSize.width) &&
        (imageHeight < cursorSize.height))
    {
      // Now we have to make a new image that is transparent
      // everywhere else in it and the size of the cursor.
      java.awt.image.BufferedImage newIcon =
          new java.awt.image.BufferedImage(cursorSize.width,
              cursorSize.height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
      java.awt.Graphics2D g2 = newIcon.createGraphics();
      g2.drawImage(icon, 0, 0, null);
      g2.dispose();
      g2 = null;
      icon = newIcon;
    }

    if (isCopy)
    {
      icon = addImageEffect(icon, ImageUtils.TRANSPARENT | ImageUtils.OFFSET_PLUS);
    }
    if (isLink)
    {
      icon = addImageEffect(icon, ImageUtils.TRANSPARENT | ImageUtils.OFFSET_ARROW);
    }

    return java.awt.Toolkit.getDefaultToolkit().createCustomCursor(icon,
        new java.awt.Point(hotX, hotY), cursorName);
  }

}