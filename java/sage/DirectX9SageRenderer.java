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

public class DirectX9SageRenderer extends SageRenderer implements NativeImageAllocator
{
  private static DirectX9SageRenderer defaultDX9Renderer;
  private static final int ELLIPTICAL_DIVISIONS = 50;
  private static final int RECT_CORNER_DIVISIONS = 32;
  private static final int MIN_DIVISIONS = 4;
  private static final boolean D3D_DEBUG = false;
  private static final int VERTEX_CACHE_SIZE = 8192;
  private static boolean loadedDX9Lib = false;
  private static Boolean dx9Exists;
  private static final int VRAM_USAGE_PERCENT = 80;
  private static boolean workedAtLeastOnce = false;

  private static final boolean PREMULTIPLY_ALPHA = true;

  public static boolean hasDirectX9()
  {
    //		if (!Sage.WINDOWS_OS) return false;
    // the hasDirectX90 method is defined in SageTVWin32.dll so we don't
    // need the DX9 libs to find out if its there
    if (dx9Exists == null)
      dx9Exists = Boolean.valueOf(hasDirectX90());
    return dx9Exists.booleanValue();
  }

  public DirectX9SageRenderer(ZRoot inMaster)
  {
    super(inMaster);
    defaultDX9Renderer = this;
    vf = uiMgr.getVideoFrame();
  }

  public boolean allocateBuffers(int width, int height)
  {
    //		if (!Sage.WINDOWS_OS)
    //			return false;
    if (!loadedDX9Lib)
    {
      if (Sage.WINDOWS_OS)
      {
        sage.Native.loadLibrary("SageTVDX93D");
        loadedDX9Lib = true;
      }
      else
      {
        sage.Native.loadLibrary("SageTVOGL");
        loadedDX9Lib = true;
      }
    }
    Sage.gc();
    boolean rv;
    wasInFullScreen = uiMgr.isFullScreen();
    java.awt.Dimension maxScrSize = getMaxScreenSize();
    fullScreenExMode = false;
    do
    {
      if (Sage.WINDOWS_OS && uiMgr.isFullScreen() &&
          !uiMgr.getBoolean("ui/disable_dx9_full_screen_ex", true))
      {
        if (Sage.DBG) System.out.println("About to intialize DX9 renderer(1) in FSE mode with size " + width + "x" + height);
        rv = initDX9SageRenderer0(-width, -height, currHWND = master.getHWND());
        if (!rv)
        {
          cleanupRenderer(true);
          if (Sage.DBG) System.out.println("About to intialize DX9 renderer(1) in standard mode with size " + maxScrSize.width + "x" + maxScrSize.height);
          rv = initDX9SageRenderer0(maxScrSize.width, maxScrSize.height, currHWND = master.getHWND());
        }
        else
          fullScreenExMode = true;
      }
      else
      {
        if (Sage.DBG) System.out.println("About to intialize DX9 renderer(1) in standard mode with size " + maxScrSize.width + "x" + maxScrSize.height);
        rv = initDX9SageRenderer0(maxScrSize.width, maxScrSize.height, currHWND = master.getHWND());
      }
      if (workedAtLeastOnce)
      {
        if (!rv)
        {
          if (Sage.DBG) System.out.println("DX9 renderer creation failed; but it worked in the past; so just wait and see if it'll work again...");
          cleanupRenderer();
          try{Thread.sleep(500);}catch(Exception e){}
        }
      }
    }while (workedAtLeastOnce && !rv);
    bufferWidth = fullScreenExMode ? width : maxScrSize.width;
    bufferHeight = fullScreenExMode ? height : maxScrSize.height;
    long vram = getAvailableVideoMemory0();
    if (vram < uiMgr.getLong("minimum_video_memory_for_dx9", 48000000))
    {
      if (Sage.DBG) System.out.println("Not using DirectX9 because there's not enough video memory (" + vram + ")");
      rerenderedDL = false;
      cleanupRenderer();
      return false;
    }
    videoMemoryLimit = vram*VRAM_USAGE_PERCENT/100;
    videoMemoryLimit = Math.min(videoMemoryLimit, Sage.getInt("ui/max_d3d_vram_usage", 150000000));
    maxTextureDim = 1024;
    if (vram > 100000000)
      maxTextureDim = 2048; // never go above this for now since no display is that rez
    int nativeMinDim = getMaximumTextureDimension0();
    int myMinDim = maxTextureDim;
    // Take the min based on what the hardware supports; and what we should do based on total memory
    maxTextureDim = Math.min(nativeMinDim, maxTextureDim);
    if (Sage.DBG) System.out.println("Max texture dimension was set as " + maxTextureDim + " mine=" + myMinDim + " native=" + nativeMinDim +
        " vramlimit=" + videoMemoryLimit + " native vram=" + vram);
    srcXCache = new float[VERTEX_CACHE_SIZE];
    srcYCache = new float[VERTEX_CACHE_SIZE];
    srcWCache = new float[VERTEX_CACHE_SIZE];
    srcHCache = new float[VERTEX_CACHE_SIZE];
    dstXCache = new float[VERTEX_CACHE_SIZE];
    dstYCache = new float[VERTEX_CACHE_SIZE];
    dstWCache = new float[VERTEX_CACHE_SIZE];
    dstHCache = new float[VERTEX_CACHE_SIZE];
    cCache = new int[VERTEX_CACHE_SIZE];

    // In case there's a file reload waiting for us in the media player
    if (!Sage.getBoolean("ui/close_media_player_on_standby", true))
      vf.reloadFile();
    // Set flag to indicate we can load the 3D system OK...which means failures later should be retried against
    if (rv)
      workedAtLeastOnce = true;
    return rv;
  }

  public boolean useTextureMapsForText()
  {
    // Using DX9 fonts is faster, but the text measuring isn't always right. The spacing
    // between the glyphs is a little different from Java which makes the bounding boxes different
    return !Sage.WINDOWS_OS || uiMgr.getBoolean("ui/use_texture_maps_for_3d_text", true);
  }

  private boolean areAllGlyphsRecognized(MetaFont.GlyphVector glyphVector)
  {
    int ng = glyphVector.getNumGlyphs();
    for (int i = 0; i < ng; i++)
    {
      if (glyphVector.getGlyphCode(i) == 0)
        return false;
    }
    return true;
  }

  public void preprocessNextDisplayList(java.util.ArrayList v)
  {
    cullDisplayList(v);
    for (int i = 0; i < v.size(); i++)
    {
      RenderingOp op = (RenderingOp) v.get(i);
      if (op.isTextOp() && useTextureMapsForText() && (Sage.getBoolean("ui/load_complete_glyph_maps", false) ||
          areAllGlyphsRecognized(op.text.glyphVector)))
      {
        convertGlyphsToCachedImages(op);
        continue;
      }
      else if (op.isImageOp())
      {
        // Check for valid image bounds first
        // Negative destination rectangles are valid for flipping images
        if (op.srcRect.width <= 0 || op.srcRect.height <= 0/* || op.destRect.width <= 0 || op.destRect.height <= 0*/)
        {
          v.remove(i--);
        }
        else
        {
          // This'll have them all loaded before we execute the DL. If we can't fit them
          // all into memory, then they'll load individually as needed while rendering the DL

          // Limit the maximum texture size or megapixel pictures from the image library can kill us.
          // Don't do this before we've checked the max dim so we don't make extra unnecessary copies.
          if (pD3DDevice != 0 && (op.texture.getWidth(op.textureIndex) > maxTextureDim ||
              op.texture.getHeight(op.textureIndex) > maxTextureDim))
          {
            float xScale = Math.min(maxTextureDim,
                op.texture.getWidth(op.textureIndex))/((float)op.texture.getWidth(op.textureIndex));
            float yScale = Math.min(maxTextureDim,
                op.texture.getHeight(op.textureIndex))/((float)op.texture.getHeight(op.textureIndex));
            xScale = yScale = Math.min(xScale, yScale);
            op.scaleSrc(xScale, yScale);
            op.textureIndex = op.texture.getImageIndex(Math.round(xScale *
                op.texture.getWidth(op.textureIndex)), Math.round(yScale *
                    op.texture.getHeight(op.textureIndex)));
          }
          // Disable mipmapping for now until we have the whole technique figured out. Main issue is
          // that this image won't necessarily be cached so we could be causing a lot of UI slowdown due to
          // this. Ideally we should load it by scaling the existing native texture since that will be a very
          // fast operation.
          /*					else if (op.texture.getWidth(op.textureIndex) > 128 && op.texture.getHeight(op.textureIndex) > 128 &&
						op.srcRect.width >= op.destRect.width*3/2 && op.srcRect.height >= op.destRect.height*3/2 &&
						op.srcRect.width > 128 && op.srcRect.height > 128)
					{
						// Source image is over twice the size of the target; so create a smaller version for faster rendering
						int newWidth = op.texture.getWidth(op.textureIndex)/2;
						int newHeight = op.texture.getHeight(op.textureIndex)/2;
						float scaleFactor = 2;
						while (newWidth/2 > op.destRect.width && newHeight/2 > op.destRect.height)
						{
							newWidth = newWidth/2;
							newHeight = newHeight/2;
							scaleFactor *= 2;
						}
						op.scaleSrc(1.0f/scaleFactor, 1.0f/scaleFactor);
						op.textureIndex = op.texture.getImageIndex(newWidth, newHeight);
					}*/
        }
      }
    }
  }

  public static int getCompositedColor(java.awt.Color color, float alpha)
  {
    if (color == null)
    {
      return ((int)(alpha*255) << 24);
    }
    alpha = color.getAlpha() * alpha / 255.0f;
    if (PREMULTIPLY_ALPHA)
    {
      return (((int)(alpha * 255) & 0xFF) << 24) +
          (((int)(alpha * color.getRed()) & 0xFF) << 16) +
          (((int)(alpha * color.getGreen()) & 0xFF) << 8) +
          ((int)(alpha * color.getBlue()) & 0xFF);
    }
    else
    {
      return ((int)(alpha*255) << 24) + (color.getRGB() & 0xFFFFFF);
    }
  }

  public static int getCompositedColor(int color, float alpha)
  {
    alpha = ((color & 0xFF000000) >>> 24) * alpha / 255.0f;
    if (PREMULTIPLY_ALPHA)
    {
      return (((int)(alpha * 255) & 0xFF) << 24) +
          (((int)(alpha * ((color >> 16) & 0xFF)) & 0xFF) << 16) +
          (((int)(alpha * ((color >> 8) & 0xFF)) & 0xFF) << 8) +
          ((int)(alpha * (color & 0xFF)) & 0xFF);
    }
    else
    {
      return ((int)(alpha*255) << 24) + (color & 0xFFFFFF);
    }
  }

  private static int getShadingColor(java.awt.Color c, float alpha)
  {
    if (PREMULTIPLY_ALPHA)
    {
      if (c == null)
      {
        alpha = Math.min(1.0f, alpha);
        int val = (int) (255*alpha);
        return ((val & 0xFF) << 24) + ((val & 0xFF) << 16) + ((val & 0xFF) << 8) + (val & 0xFF);
      }
      else
      {
        int calpha = c.getAlpha();
        if (calpha == 255 && alpha == 1.0f)
          return c.getRGB();
        alpha *= (calpha / 255.0f); // since the color may already have alpha that is not premultiplied
        alpha = Math.min(1.0f, alpha);
        return (((int)(alpha * 255) & 0xFF) << 24) +
            (((int)(alpha * c.getRed()) & 0xFF) << 16) +
            (((int)(alpha * c.getGreen()) & 0xFF) << 8) +
            ((int)(alpha * c.getBlue()) & 0xFF);
      }
    }
    else
    {
      if (c == null)
      {
        alpha = Math.min(1.0f, alpha);
        return (((int)(255*alpha) & 0xFF) << 24) + 0xFFFFFF;
      }
      else
        return ((Math.min(255, (int)(alpha * c.getAlpha())) & 0xFF) << 24) + (c.getRGB() & 0xFFFFFF);
    }
  }

  protected boolean compositeSurfaces(Object targetSurface, Object srcSurface, float alphaFactor, java.awt.geom.Rectangle2D region)
  {
    Long target = (Long) targetSurface;
    Long src = (Long) srcSurface;
    if (src != null && target != null)
    {
      long lastRT = currRT;
      float x = (float)region.getX();
      float y = (float)region.getY();
      float w = (float)region.getWidth();
      float h = (float)region.getHeight();
      if (!beganScene)
      {
        if (!beginScene0(master.getWidth(), master.getHeight()))
          return false;
        beganScene = true;
      }
      setRenderTarget0(target.longValue());
      // Since it's the same dimensions just use nearest neighbor scaling
      textureMap0(src.longValue(), x, y, w, h, x, y, w, h, 1, 0, getCompositedColor(0xFFFFFFFF, alphaFactor), false);
      setRenderTarget0(lastRT);
      return true;
    }
    return false;
  }

  private void precacheImages()
  {
    // Preload all images used for rendering this DL so this doesn't effect animation timing
    // This is a VERY inefficient way of doing this since we make repeated calls to the same texture
    // for each glyph used (since multiple glyphs will use the same texture). And this has to get multiple sync
    // locks to do the get/remove. We already compensate for delays in rendering after we're done by adjusting
    // the animation start times; and that's the only purpose for preloading these. Testing indicated a 25%
    // increase in frame rate by removing this.
    if (pD3DDevice != 0 && false)
    {
      for (int i = 0; i < currDisplayList.size(); i++)
      {
        RenderingOp op = (RenderingOp) currDisplayList.get(i);
        if (op.isTextOp())
        {
          MetaImage fontImage = op.text.fontImage;
          int[] imgNumCache = op.text.renderImageNumCache;
          if (fontImage != null && imgNumCache != null)
          {
            for (int j = 0; j < imgNumCache.length; j++)
            {
              int x = imgNumCache[j];
              if (x != -1)
              {
                fontImage.getNativeImage(this, x);
                fontImage.removeNativeRef(this, x);
              }
            }
          }
        }
        else if (op.isImageOp())
        {
          // Since we're not guaranteed to be allocated before this call
          op.texture.getNativeImage(this, op.textureIndex);
          op.texture.removeNativeRef(this, op.textureIndex);
        }
      }
    }
  }

  private void processEffectOp(RenderingOp op)
  {
    if (op.effectTracker != null)
    {
      hadVirginEffects |= op.effectTracker.processEffectState(renderStartTime, usingFreshDL);
      //System.out.println("Current Effect=" + op.effectTracker.getWidget() + " alpha=" + currEffectAlpha + " active=" + op.effectTracker.isActive() +
      //	" pos=" + op.effectTracker.isPositive() + " comp=" + System.identityHashCode(op.effectTracker.getSrcComp()) + " prog=" + op.effectTracker.getCurrProgress());
      EffectStackItem stackItem = new EffectStackItem(null, null, currEffectAlpha, currEffectClip);
      effectStack.push(stackItem);
      if (op.effectTracker.hasFade())
      {
        float nextFade = op.effectTracker.getCurrentFade();
        currEffectAlpha *= nextFade;
      }
      if (op.effectTracker.isKiller())
      {
        //System.out.println("KILLER EFFECT");
        currEffectAlpha = 0;
      }
      if (op.effectTracker.getCurrentTransform(tempMat, op.srcRect))
      {
        if (op.destRect != null)
        {
          // Now apply the effect to the clipping rectangle to account for the actual effect; and then
          // reclip against what the rect actually is
          currEffectClip = MathUtils.transformRectCoords(op.destRect, MathUtils.createInverse(tempMat));
          currEffectClip.intersect(currEffectClip, op.destRect, currEffectClip);
        }
        if (op.effectTracker.hasCameraOffset())
        {
          stackItem.projxform = projMat;
          stackItem.xform = currEffectMat;
          projMat = generateProjectionMatrix(op.effectTracker.getCameraOffsetX(), op.effectTracker.getCameraOffsetY());
          currEffectMat = new javax.vecmath.Matrix4f(tempMat);
          currEffectMat.mul(stackItem.xform, tempMat);
          currMat.mul(projMat, currEffectMat);
        }
        else
        {
          stackItem.xform = currEffectMat;
          currEffectMat = new javax.vecmath.Matrix4f(tempMat);
          currEffectMat.mul(stackItem.xform, tempMat);
          tempMat.mul(currMat, tempMat);
          currMat = new javax.vecmath.Matrix4f(tempMat);
        }
        for (int r = 0; r < 4; r++)
          for (int c = 0; c < 4; c++)
            currCoords[r + c*4] = currMat.getElement(r, c); // TRANSPOSED
      }
      if (op.effectTracker.isActive())
      {
        //System.out.println("Active Effect=" + op.effectTracker.getWidget() + " alpha=" + currEffectAlpha);
        effectAnimationsActive = true;
        if (op.effectTracker.requiresCompletion())
          effectAnimationsLocked = true;
      }
    }
    else
    {
      //System.out.println("Effect pop");
      EffectStackItem stackItem = (EffectStackItem) effectStack.pop();
      if (stackItem.xform != null)
      {
        currEffectMat = stackItem.xform;
        if (stackItem.projxform != null)
          projMat = stackItem.projxform;
        currMat.mul(projMat, currEffectMat);
        for (int r = 0; r < 4; r++)
          for (int c = 0; c < 4; c++)
            currCoords[r + c*4] = currMat.getElement(r, c); // TRANSPOSED
      }
      currEffectAlpha = stackItem.alpha;
      currEffectClip = (java.awt.geom.Rectangle2D.Float) stackItem.clip;
    }
  }

  private void renderTextTextureOp(RenderingOp op)
  {
    int numFontImages = op.text.fontImage.getNumImages();
    int numGlyphs = op.text.renderImageNumCache.length;
    if (glyphDrawnTracker == null || glyphDrawnTracker.length < numGlyphs)
      glyphDrawnTracker = new boolean[numGlyphs + 10];
    else
      java.util.Arrays.fill(glyphDrawnTracker, false);
    int firstUndrawnGlyph = 0;
    int color = getShadingColor(op.renderColor, op.alphaFactor * currEffectAlpha);
    float transX = op.copyImageRect.x;
    float transY = op.copyImageRect.y;
    boolean textScaling = (Math.abs(MathUtils.getScaleX(currEffectMat)) != 1 || Math.abs(MathUtils.getScaleY(currEffectMat)) != 1);
    while (firstUndrawnGlyph < numGlyphs)
    {
      long texturePtr = -1;
      int rectCoordIndex = 0;
      int currImageNum = op.text.renderImageNumCache[firstUndrawnGlyph];
      if (currImageNum < 0)
      {
        firstUndrawnGlyph++;
        while (firstUndrawnGlyph < numGlyphs)
        {
          currImageNum = op.text.renderImageNumCache[firstUndrawnGlyph];
          if (currImageNum >= 0)
            break;
          firstUndrawnGlyph++;
        }
      }
      for (int k = firstUndrawnGlyph; k < numGlyphs; k++)
      {
        if (op.text.renderImageNumCache[k] == currImageNum)
        {
          // NOTE: The addition of 0.01f is very important. It prevents having boundary
          // alignment issues where the nearest-neighbor pixel samplings would skip pixels
          // However, this was only one case I had when I fixed this so I can't be sure it
          // actually fixes all cases.
          // NOTE: 8/15/07 - JK - I found a case where we don't want the 0.01f. If you use
          // a 942x508 resolution and select the audio renderer filter the left side of the 'f'
          // in Default for Default WaveOut Renderer was truncated; as was the left of the 't' and 'W'
          // NOTE: 8/20/07 - JK - I found a case where we need it again; but if we make it 0.001f instead
          // of 0.01f then it satisfies the test case above as well. This problem shows up in Channel Setup
          // at 950x565 with nearly all of the text looking corrupted due to nearest neighbor sampling not
          // working right for some reason. But I did find it was only needed in 'x' and not in 'y'
          // NOTE: 9/14/07 - JK - I had a bug submission of some corruption that looked
          // like a one pixel shift in the y dimension for one corner of the texture; adding the .001f to the
          // y dimension below fixed the bug.
          // NOTE: 1/18/10 - JK - There was a bunch of weird issues where the text in system information
          // at 1323x758 was having some glyphs drawn with an extra pixel stretch in one dimension. Adding the
          // .01f offset in X & Y fixed the problem for this case. This is still NOT correct overall as I can
          // still find some issues if I resize the window and really look for it. I also added the ceil to
          // the width/height since they were not always integers. I'm also doing a floor for x/y so they
          // align better...but it still needed the 0.01f to reduce the number of noticable issues. Maybe
          // one day we'll figure out how to do this properly.....
          srcXCache[rectCoordIndex] = op.text.renderGlyphRectCache[k].x + 0.001f;
          srcYCache[rectCoordIndex] = op.text.renderGlyphRectCache[k].y + 0.001f;
          srcWCache[rectCoordIndex] = op.text.renderGlyphRectCache[k].width;
          srcHCache[rectCoordIndex] = op.text.renderGlyphRectCache[k].height;
          srcWCache[rectCoordIndex] = (float) Math.ceil(srcWCache[rectCoordIndex]);
          srcHCache[rectCoordIndex] = (float) Math.ceil(srcHCache[rectCoordIndex]);
          dstXCache[rectCoordIndex] = op.text.renderRectCache[2*k] + transX;
          dstYCache[rectCoordIndex] = op.text.renderRectCache[2*k+1] + transY;
          if (!textScaling)
          {
            dstXCache[rectCoordIndex] = (float)Math.floor(dstXCache[rectCoordIndex]) + 0.01f;
            dstYCache[rectCoordIndex] = (float)Math.floor(dstYCache[rectCoordIndex]) + 0.01f;
          }
          dstWCache[rectCoordIndex] = srcWCache[rectCoordIndex];
          dstHCache[rectCoordIndex] = srcHCache[rectCoordIndex];
          cCache[rectCoordIndex] = color;
          boolean skipMe = false;
          if (currEffectClip != null)
          {
            if (!clipSrcDestCache(rectCoordIndex, currEffectClip))
            {
              skipMe = true;
              rectCoordIndex--;
            }
          }
          if (!skipMe)
          {
            lastPixelRenderCount += dstWCache[rectCoordIndex] * dstHCache[rectCoordIndex];
            lastPixelInputCount += dstWCache[rectCoordIndex] * dstHCache[rectCoordIndex];
          }
          rectCoordIndex++;
          glyphDrawnTracker[k] = true;
          if (k == firstUndrawnGlyph)
            firstUndrawnGlyph++;
        }
        else if ((glyphDrawnTracker[k] || op.text.renderImageNumCache[k] < 0) && k == firstUndrawnGlyph)
          firstUndrawnGlyph++;
      }
      if (rectCoordIndex > 0)
      {
        if (texturePtr == -1)
          texturePtr = op.text.fontImage.getNativeImage(this, currImageNum);
        boolean texMapRes = textureMultiMap0(texturePtr, srcXCache, srcYCache, srcWCache, srcHCache, dstXCache,
            dstYCache, dstWCache, dstHCache,
            textScaling ? 1 : 0, 0, cCache,
                rectCoordIndex, currCoords);
        op.text.fontImage.removeNativeRef(this, currImageNum);
        // If there was a failing using this texture then remove it so we can re-create it
        if (!texMapRes)
          op.text.fontImage.setNativePointer(this, currImageNum, 0, 0);
      }
    }
  }

  private void processPrimitiveOp(RenderingOp op)
  {
    if (currEffectClip != null/* || op.primitive.shapeWidth != op.srcRect.width || op.primitive.shapeHeight != op.srcRect.height*/)
    {
      shapeClipRect.setRect(op.destRect);
      if (currEffectClip != null)
        shapeClipRect.intersect(shapeClipRect, currEffectClip, shapeClipRect);
    }
    else
    {
      shapeClipRect.setRect(op.destRect);
    }
    // Skip shapes that are entirely clipped
    if (shapeClipRect.width <= 0 || shapeClipRect.height <= 0)
      return;
    float shapeMinX = op.copyImageRect.x;
    float shapeMinY = op.copyImageRect.y;
    float shapeMaxX = shapeMinX + op.primitive.shapeWidth;
    float shapeMaxY = shapeMinY + op.primitive.shapeHeight;
    float ropAlpha = Math.min(1.0f, op.alphaFactor * currEffectAlpha);
    lastPixelRenderCount += shapeClipRect.width * shapeClipRect.height;
    lastPixelInputCount += shapeClipRect.width * shapeClipRect.height;
    if (op.primitive.fill)
    {
      processFillOp(op, shapeMinX, shapeMinY, shapeMaxX, shapeMaxY, ropAlpha);
    }
    else
      processLineOp(op, shapeMinX, shapeMinY, shapeMaxX, shapeMaxY, ropAlpha);
  }

  private void processFillOp(RenderingOp op, float shapeMinX, float shapeMinY, float shapeMaxX, float shapeMaxY, float ropAlpha)
  {
    if (op.primitive.shapeType.equals("Rectangle"))
    {
      if (op.primitive.cornerArc == 0)
      {
        if (rerenderedDL)
        {
          fillShape0(null, null, null, 0, 4, null, currCoords);
          return;
        }
        srcXCache[0] = shapeClipRect.x;
        srcXCache[1] = shapeClipRect.x + shapeClipRect.width;//op.primitive.shapeWidth;
        srcXCache[2] = shapeClipRect.x + shapeClipRect.width;//op.primitive.shapeWidth;
        srcXCache[3] = shapeClipRect.x;
        srcYCache[0] = shapeClipRect.y;
        srcYCache[1] = shapeClipRect.y;
        srcYCache[2] = shapeClipRect.y + shapeClipRect.height;//op.primitive.shapeHeight;
        srcYCache[3] = shapeClipRect.y + shapeClipRect.height;//op.primitive.shapeHeight;
        if (op.primitive.gradc1 != null)
        {
          for (int j = 0; j < 4; j++)
          {
            cCache[j] = getCompositedColor(
                getGradientColor(op.primitive, srcXCache[j] - shapeMinX, srcYCache[j] - shapeMinY), ropAlpha);
          }
        }
        else
          java.util.Arrays.fill(cCache, 0, 4, getCompositedColor(op.primitive.color, ropAlpha));
        fillShape0(srcXCache, srcYCache, cCache, 0, 4, null, currCoords);
      }
      else
      {
        // limit the corner arc based on overall width/height
        op.primitive.cornerArc = Math.min(op.primitive.cornerArc, (int)Math.floor(Math.min(
            op.primitive.shapeWidth/2, op.primitive.shapeHeight/2)));
        int circum = (int) ((op.primitive.cornerArc)*Math.PI/4);
        int numDivs = Math.max(MIN_DIVISIONS, Math.min(circum/3, RECT_CORNER_DIVISIONS));
        // move ourself around the circle to get each point
        int numVerts = 4*numDivs + 2;
        if (rerenderedDL)
        {
          fillShape0(null, null, null, 0, numVerts, null, currCoords);
          return;
        }
        float centerX = op.primitive.cornerArc/2.0f + shapeMinX;
        float centerY = op.primitive.cornerArc/2.0f + shapeMinY;
        srcXCache[0] = (shapeMinX + shapeMaxX)/2;
        srcYCache[0] = (shapeMinY + shapeMaxY)/2;
        if (op.primitive.gradc1 == null)
          java.util.Arrays.fill(cCache, 0, numVerts, getCompositedColor(op.primitive.color, ropAlpha));
        for (int j = 0; j < numDivs; j++)
        {
          srcXCache[j+1] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1) + 3*Math.PI/2.0f)*op.primitive.cornerArc/2.0f + centerX;
          srcYCache[j+1] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1) + 3*Math.PI/2.0f)*op.primitive.cornerArc/2.0f + centerY;
        }
        centerX = op.primitive.cornerArc/2.0f + shapeMinX;
        centerY = op.primitive.shapeHeight - op.primitive.cornerArc/2.0f + shapeMinY;
        for (int j = 0; j < numDivs; j++)
        {
          srcXCache[j+1+numDivs] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI)*op.primitive.cornerArc/2.0f + centerX;
          srcYCache[j+1+numDivs] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI)*op.primitive.cornerArc/2.0f + centerY;
        }
        centerX = op.primitive.shapeWidth - op.primitive.cornerArc/2.0f + shapeMinX;
        centerY = op.primitive.shapeHeight - op.primitive.cornerArc/2.0f + shapeMinY;
        for (int j = 0; j < numDivs; j++)
        {
          srcXCache[j+1+2*numDivs] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI/2.0f)*op.primitive.cornerArc/2.0f + centerX;
          srcYCache[j+1+2*numDivs] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI/2.0f)*op.primitive.cornerArc/2.0f + centerY;
        }
        centerX = op.primitive.shapeWidth - op.primitive.cornerArc/2.0f + shapeMinX;
        centerY = op.primitive.cornerArc/2.0f + shapeMinY;
        for (int j = 0; j < numDivs; j++)
        {
          srcXCache[j+numDivs*3+1] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1))*op.primitive.cornerArc/2.0f + centerX;
          srcYCache[j+numDivs*3+1] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1))*op.primitive.cornerArc/2.0f + centerY;
        }
        srcXCache[numVerts - 1] = srcXCache[1];
        srcYCache[numVerts - 1] = srcYCache[1];
        float orgShapeX = shapeMinX;
        float orgShapeY = shapeMinY;
        shapeMinX = shapeClipRect.x;
        shapeMaxX = shapeClipRect.x + shapeClipRect.width;
        shapeMinY = shapeClipRect.y;
        shapeMaxY = shapeClipRect.y + shapeClipRect.height;
        for (int j = 0; j < numVerts; j++)
        {
          if (srcXCache[j] < shapeMinX)
            srcXCache[j] = shapeMinX;
          else if (srcXCache[j] > shapeMaxX)
            srcXCache[j] = shapeMaxX;
          if (srcYCache[j] < shapeMinY)
            srcYCache[j] = shapeMinY;
          else if (srcYCache[j] > shapeMaxY)
            srcYCache[j] = shapeMaxY;
          if (op.primitive.gradc1 != null)
            cCache[j] = getCompositedColor(getGradientColor(op.primitive, srcXCache[j] - orgShapeX, srcYCache[j] - orgShapeY),
                ropAlpha);
        }
        fillShape0(srcXCache, srcYCache, cCache, 0, numVerts, null, currCoords);
      }
    }
    else if (op.primitive.shapeType.equals("Oval"))
    {
      int circum = (int) ((op.primitive.shapeWidth + op.primitive.shapeHeight)*Math.PI/2.0f);
      int numDivs = Math.max(MIN_DIVISIONS, Math.min(circum/3, RECT_CORNER_DIVISIONS));
      // move ourself around the circle to get each point
      int numVerts = numDivs + 1;
      if (rerenderedDL)
      {
        fillShape0(null, null, null, 0, numVerts, null, currCoords);
        return;
      }
      srcXCache[0] = (shapeMinX + shapeMaxX)/2;
      srcYCache[0] = (shapeMinY + shapeMaxY)/2;
      float centerX = srcXCache[0];
      float centerY = srcYCache[0];
      if (op.primitive.gradc1 != null)
        cCache[0] = getCompositedColor(getGradientColor(op.primitive,
            srcXCache[0] - shapeMinX, srcYCache[0] - shapeMinY), ropAlpha);
      else
        java.util.Arrays.fill(cCache, 0, numVerts, getCompositedColor(op.primitive.color, ropAlpha));
      for (int j = 0; j < numDivs; j++)
      {
        srcXCache[j+1] = (float)Math.cos(-(2*Math.PI*j)/(numDivs-1))*op.primitive.shapeWidth/2.0f + centerX;
        srcYCache[j+1] = (float)Math.sin(-(2*Math.PI*j)/(numDivs-1))*op.primitive.shapeHeight/2.0f + centerY;
        if (op.primitive.gradc1 != null)
          cCache[j+1] = getCompositedColor(getGradientColor(op.primitive,
              srcXCache[j+1] - shapeMinX, srcYCache[j+1] - shapeMinY),
              ropAlpha);
      }
      shapeMinX = shapeClipRect.x;
      shapeMaxX = shapeClipRect.x + shapeClipRect.width;
      shapeMinY = shapeClipRect.y;
      shapeMaxY = shapeClipRect.y + shapeClipRect.height;
      for (int j = 0; j < numVerts; j++)
      {
        if (srcXCache[j] < shapeMinX)
          srcXCache[j] = shapeMinX;
        else if (srcXCache[j] > shapeMaxX)
          srcXCache[j] = shapeMaxX;
        if (srcYCache[j] < shapeMinY)
          srcYCache[j] = shapeMinY;
        else if (srcYCache[j] > shapeMaxY)
          srcYCache[j] = shapeMaxY;
      }
      fillShape0(srcXCache, srcYCache, cCache, 0, numVerts, null, currCoords);
    }
  }

  private void processLineOp(RenderingOp op, float shapeMinX, float shapeMinY, float shapeMaxX, float shapeMaxY, float ropAlpha)
  {
    if (op.primitive.shapeType.equals("Rectangle"))
    {
      // Limit the corner arc based on thickness, otherwise it doesn't make sense
      if (op.primitive.cornerArc == 0)
      {
        if (rerenderedDL)
        {
          fillShape0(null, null, null, 0, 4, null, currCoords);
          fillShape0(null, null, null, 0, 4, null, currCoords);
          fillShape0(null, null, null, 0, 4, null, currCoords);
          fillShape0(null, null, null, 0, 4, null, currCoords);
          return;
        }
        if (op.primitive.gradc1 == null)
          java.util.Arrays.fill(cCache, 0, 4, getCompositedColor(op.primitive.color, ropAlpha));
        if (shapeMinX < shapeClipRect.x + shapeClipRect.width && shapeMaxX > shapeClipRect.x &&
            shapeMinY < shapeClipRect.y + shapeClipRect.height && shapeMinY + op.primitive.strokeSize > shapeClipRect.y)
        {
          srcXCache[0] = Math.max(shapeMinX, shapeClipRect.x);
          srcXCache[1] = Math.min(shapeMaxX, shapeClipRect.x + shapeClipRect.width);//op.primitive.shapeWidth;
          srcXCache[2] = srcXCache[1];
          srcXCache[3] = srcXCache[0];
          srcYCache[0] = Math.max(shapeMinY, shapeClipRect.y);
          srcYCache[1] = srcYCache[0];
          srcYCache[2] = Math.min(shapeMinY + op.primitive.strokeSize, shapeClipRect.y + shapeClipRect.height);
          srcYCache[3] = srcYCache[2];
          if (op.primitive.gradc1 != null)
          {
            for (int j = 0; j < 4; j++)
            {
              cCache[j] = getCompositedColor(
                  getGradientColor(op.primitive, srcXCache[j] - shapeMinX, srcYCache[j] - shapeMinY), ropAlpha);
            }
          }
          fillShape0(srcXCache, srcYCache, cCache, 0, 4, null, currCoords);
        }
        if (shapeMinX < shapeClipRect.x + shapeClipRect.width && shapeMaxX > shapeClipRect.x &&
            shapeMaxY - op.primitive.strokeSize < shapeClipRect.y + shapeClipRect.height && shapeMaxY > shapeClipRect.y)
        {
          srcXCache[0] = Math.max(shapeMinX, shapeClipRect.x);
          srcXCache[1] = Math.min(shapeMaxX, shapeClipRect.x + shapeClipRect.width);//op.primitive.shapeWidth;
          srcXCache[2] = srcXCache[1];
          srcXCache[3] = srcXCache[0];
          srcYCache[0] = Math.max(shapeMaxY - op.primitive.strokeSize, shapeClipRect.y);
          srcYCache[1] = srcYCache[0];
          srcYCache[2] = Math.min(shapeMaxY, shapeClipRect.y + shapeClipRect.height);
          srcYCache[3] = srcYCache[2];
          if (op.primitive.gradc1 != null)
          {
            for (int j = 0; j < 4; j++)
            {
              cCache[j] = getCompositedColor(
                  getGradientColor(op.primitive, srcXCache[j] - shapeMinX, srcYCache[j] - shapeMinY), ropAlpha);
            }
          }
          fillShape0(srcXCache, srcYCache, cCache, 0, 4, null, currCoords);
        }
        if (shapeMinX < shapeClipRect.x + shapeClipRect.width && shapeMinX + op.primitive.strokeSize > shapeClipRect.x &&
            shapeMinY + op.primitive.strokeSize < shapeClipRect.y + shapeClipRect.height && shapeMaxY - op.primitive.strokeSize > shapeClipRect.y)
        {
          srcXCache[0] = Math.max(shapeMinX, shapeClipRect.x);
          srcXCache[1] = Math.min(shapeMinX + op.primitive.strokeSize, shapeClipRect.x + shapeClipRect.width);
          srcXCache[2] = srcXCache[1];
          srcXCache[3] = srcXCache[0];
          srcYCache[0] = Math.max(shapeMinY + op.primitive.strokeSize, shapeClipRect.y);
          srcYCache[1] = srcYCache[0];
          srcYCache[2] = Math.min(shapeMaxY - op.primitive.strokeSize, shapeClipRect.y + shapeClipRect.height);
          srcYCache[3] = srcYCache[2];
          if (op.primitive.gradc1 != null)
          {
            for (int j = 0; j < 4; j++)
            {
              cCache[j] = getCompositedColor(
                  getGradientColor(op.primitive, srcXCache[j] - shapeMinX, srcYCache[j] - shapeMinY), ropAlpha);
            }
          }
          fillShape0(srcXCache, srcYCache, cCache, 0, 4, null, currCoords);
        }
        if (shapeMaxX - op.primitive.strokeSize < shapeClipRect.x + shapeClipRect.width && shapeMaxX > shapeClipRect.x &&
            shapeMinY + op.primitive.strokeSize < shapeClipRect.y + shapeClipRect.height && shapeMaxY - op.primitive.strokeSize > shapeClipRect.y)
        {
          srcXCache[0] = Math.max(shapeMaxX - op.primitive.strokeSize, shapeClipRect.x);
          srcXCache[1] = Math.min(shapeMaxX, shapeClipRect.x + shapeClipRect.width);
          srcXCache[2] = srcXCache[1]; srcXCache[3] = srcXCache[0];
          srcYCache[0] = Math.max(shapeMinY + op.primitive.strokeSize, shapeClipRect.y);
          srcYCache[1] = srcYCache[0];
          srcYCache[2] = Math.min(shapeMaxY - op.primitive.strokeSize, shapeClipRect.y + shapeClipRect.height);
          srcYCache[3]=srcYCache[2];
          if (op.primitive.gradc1 != null)
          {
            for (int j = 0; j < 4; j++)
            {
              cCache[j] = getCompositedColor(
                  getGradientColor(op.primitive, srcXCache[j] - shapeMinX, srcYCache[j] - shapeMinY), ropAlpha);
            }
          }
          fillShape0(srcXCache, srcYCache, cCache, 0, 4, null, currCoords);
        }
      }
      else
      {
        // limit the corner arc based on overall width/height
        op.primitive.cornerArc = Math.min(op.primitive.cornerArc, (int)Math.floor(Math.min(
            op.primitive.shapeWidth/2, op.primitive.shapeHeight/2)));
        int circum = (int) ((op.primitive.cornerArc)*Math.PI/4);
        int numDivs = Math.max(MIN_DIVISIONS, Math.min(circum/3, RECT_CORNER_DIVISIONS/8));
        // move ourself around the circle to get each point
        int numVerts = numDivs*8 + 2;
        /*if (rerenderedDL)
				{
					fillShape0(null, null, null, 1, numVerts);
					continue;
				}*/
        if (op.primitive.gradc1 == null)
          java.util.Arrays.fill(cCache, 0, numVerts, getCompositedColor(op.primitive.color, ropAlpha));
        // top left
        float centerX = op.primitive.cornerArc/2.0f + shapeMinX;
        float centerY = op.primitive.cornerArc/2.0f + shapeMinY;
        for (int j = 0; j < numDivs; j++)
        {
          srcXCache[j*2] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + 3*Math.PI/2)*op.primitive.cornerArc/2.0f + centerX;
          srcYCache[j*2] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + 3*Math.PI/2)*op.primitive.cornerArc/2.0f + centerY;
          srcXCache[j*2+1] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + 3*Math.PI/2)*(op.primitive.cornerArc/2.0f-op.primitive.strokeSize) + centerX;
          srcYCache[j*2+1] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + 3*Math.PI/2)*(op.primitive.cornerArc/2.0f-op.primitive.strokeSize) + centerY;
        }
        // bottom left
        centerX = op.primitive.cornerArc/2.0f + shapeMinX;
        centerY = shapeMaxY - op.primitive.cornerArc/2.0f;
        for (int j = 0; j < numDivs; j++)
        {
          srcXCache[j*2+2*numDivs] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + Math.PI)*op.primitive.cornerArc/2.0f + centerX;
          srcYCache[j*2+2*numDivs] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + Math.PI)*op.primitive.cornerArc/2.0f + centerY;
          srcXCache[j*2+2*numDivs+1] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + Math.PI)*(op.primitive.cornerArc/2.0f-op.primitive.strokeSize) + centerX;
          srcYCache[j*2+2*numDivs+1] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + Math.PI)*(op.primitive.cornerArc/2.0f-op.primitive.strokeSize) + centerY;
        }
        // bottom right
        centerX = shapeMaxX - op.primitive.cornerArc/2.0f;
        centerY = shapeMaxY - op.primitive.cornerArc/2.0f;
        for (int j = 0; j < numDivs; j++)
        {
          srcXCache[j*2+4*numDivs] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + Math.PI/2)*op.primitive.cornerArc/2.0f + centerX;
          srcYCache[j*2+4*numDivs] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + Math.PI/2)*op.primitive.cornerArc/2.0f + centerY;
          srcXCache[j*2+4*numDivs+1] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + Math.PI/2)*(op.primitive.cornerArc/2.0f-op.primitive.strokeSize) + centerX;
          srcYCache[j*2+4*numDivs+1] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + Math.PI/2)*(op.primitive.cornerArc/2.0f-op.primitive.strokeSize) + centerY;
        }
        // top right
        centerX = shapeMaxX - op.primitive.cornerArc/2.0f;
        centerY = op.primitive.cornerArc/2.0f + shapeMinY;
        for (int j = 0; j < numDivs; j++)
        {
          srcXCache[j*2+6*numDivs] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1))*op.primitive.cornerArc/2.0f + centerX;
          srcYCache[j*2+6*numDivs] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1))*op.primitive.cornerArc/2.0f + centerY;
          srcXCache[j*2+6*numDivs+1] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1))*(op.primitive.cornerArc/2.0f-op.primitive.strokeSize) + centerX;
          srcYCache[j*2+6*numDivs+1] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1))*(op.primitive.cornerArc/2.0f-op.primitive.strokeSize) + centerY;
        }
        srcXCache[8*numDivs] = srcXCache[0];
        srcYCache[8*numDivs] = srcYCache[0];
        srcXCache[8*numDivs+1] = srcXCache[1];
        srcYCache[8*numDivs+1] = srcYCache[1];
        if (op.primitive.gradc1 != null)
        {
          for (int j = 0; j < numVerts; j++)
            cCache[j] = getCompositedColor(getGradientColor(op.primitive,
                srcXCache[j] - shapeMinX, srcYCache[j] - shapeMinY), ropAlpha);
        }
        shapeMinX = shapeClipRect.x;
        shapeMaxX = shapeClipRect.x + shapeClipRect.width;
        shapeMinY = shapeClipRect.y;
        shapeMaxY = shapeClipRect.y + shapeClipRect.height;
        for (int j = 0; j < numVerts; j++)
        {
          if (srcXCache[j] < shapeMinX)
            srcXCache[j] = shapeMinX;
          else if (srcXCache[j] > shapeMaxX)
            srcXCache[j] = shapeMaxX;
          if (srcYCache[j] < shapeMinY)
            srcYCache[j] = shapeMinY;
          else if (srcYCache[j] > shapeMaxY)
            srcYCache[j] = shapeMaxY;
        }
        fillShape0(srcXCache, srcYCache, cCache, 1, numVerts, null, currCoords);
      }
    }
    else if (op.primitive.shapeType.equals("Oval"))
    {
      int circum = (int) ((op.primitive.shapeWidth + op.primitive.shapeHeight)*Math.PI/2);
      int numDivs = Math.max(MIN_DIVISIONS, Math.min(circum, ELLIPTICAL_DIVISIONS/2));
      // move ourself around the circle to get each point
      int numVerts = numDivs*2;
      if (rerenderedDL)
      {
        fillShape0(null, null, null, 1, numVerts, null, currCoords);
        return;
      }
      float centerX = (shapeMinX + shapeMaxX)/2.0f;
      float centerY = (shapeMinY + shapeMaxY)/2.0f;
      for (int j = 0; j < numDivs; j++)
      {
        srcXCache[j*2] = (float)Math.cos((2*Math.PI*j)/(numDivs-1))*op.primitive.shapeWidth/2.0f + centerX;
        srcYCache[j*2] = (float)Math.sin((2*Math.PI*j)/(numDivs-1))*op.primitive.shapeHeight/2.0f + centerY;
        srcXCache[j*2+1] = (float)Math.cos((2*Math.PI*j)/(numDivs-1))*(op.primitive.shapeWidth/2.0f -
            op.primitive.strokeSize) + centerX;
        srcYCache[j*2+1] = (float)Math.sin((2*Math.PI*j)/(numDivs-1))*(op.primitive.shapeHeight/2.0f -
            op.primitive.strokeSize) + centerY;
      }
      if (op.primitive.gradc1 != null)
      {
        for (int j = 0; j < numVerts; j++)
          cCache[j] = getCompositedColor(getGradientColor(op.primitive,
              srcXCache[j] - shapeMinX, srcYCache[j] - shapeMinY), ropAlpha);
      }
      else
        java.util.Arrays.fill(cCache, 0, numVerts, getCompositedColor(op.primitive.color, ropAlpha));
      shapeMinX = shapeClipRect.x;
      shapeMaxX = shapeClipRect.x + shapeClipRect.width;
      shapeMinY = shapeClipRect.y;
      shapeMaxY = shapeClipRect.y + shapeClipRect.height;
      for (int j = 0; j < numVerts; j++)
      {
        if (srcXCache[j] < shapeMinX)
          srcXCache[j] = shapeMinX;
        else if (srcXCache[j] > shapeMaxX)
          srcXCache[j] = shapeMaxX;
        if (srcYCache[j] < shapeMinY)
          srcYCache[j] = shapeMinY;
        else if (srcYCache[j] > shapeMaxY)
          srcYCache[j] = shapeMaxY;
      }
      fillShape0(srcXCache, srcYCache, cCache, 1, numVerts, null, currCoords);
    }
  }

  // We need to allocate the 3D engine to the size of the largest screen dimensions in use
  private java.awt.Dimension getMaxScreenSize()
  {
    java.awt.GraphicsDevice[] screens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    int maxWidth = 0;
    int maxHeight = 0;
    for (int i = 0; i < screens.length; i++)
    {
      java.awt.Rectangle sb = screens[i].getDefaultConfiguration().getBounds();
      if (sb.width > maxWidth)
        maxWidth = sb.width;
      if (sb.height > maxHeight)
        maxHeight = sb.height;
    }
    return new java.awt.Dimension(maxWidth, maxHeight);
  }

  public boolean executeDisplayList(java.awt.Rectangle clipRect)
  {
    if (D3D_DEBUG && Sage.DBG) System.out.println("DirectX9SageRenderer is executing displayList vs=" + videoSurface +
        " list=" + (currDisplayList == null ? 0 : currDisplayList.size()));
    if (currDisplayList == null)
      return true;

    synchronized (displayChangeLock)
    {
      if (lastNotifiedDisplayWidth != 0 && lastNotifiedDisplayHeight != 0 && (bufferWidth != lastNotifiedDisplayWidth || lastNotifiedDisplayHeight != bufferHeight))
      {
        if (Sage.DBG) System.out.println("Forcing device lost because last notified display size of " + lastNotifiedDisplayWidth + "x" + lastNotifiedDisplayHeight +
            " does not match buffer size of " + bufferWidth + "x" + bufferHeight);
        deviceLost = true;
        lastNotifiedDisplayWidth = lastNotifiedDisplayHeight = 0;
      }
    }

    // It's possible that the DX9 device is lost and we may need to rebuild it
    if (Sage.WINDOWS_OS && deviceLost)
    {
      // If we're in a VMR9 thread then we can't destroy the 3D system. We have to wait until
      // the player is closed and then destroy it.
      stopVmr9Callback = true;
      if (almostInVmr9Callback || inVmr9Callback)
      {
        return false;
      }
      boolean rv = false;
      wasInFullScreen = uiMgr.isFullScreen();
      fullScreenExMode = false;
      while (!rv)
      {
        cleanupRenderer(true);
        java.awt.Dimension scrSize = uiMgr.getScreenSize();
        java.awt.Dimension maxScrSize = getMaxScreenSize();
        if (Sage.WINDOWS_OS && uiMgr.isFullScreen() &&
            !uiMgr.getBoolean("ui/disable_dx9_full_screen_ex", true))
        {
          if (Sage.DBG) System.out.println("About to intialize DX9 renderer(2) in FSE mode with size " + scrSize.width + "x" + scrSize.height);
          rv = initDX9SageRenderer0(-(bufferWidth = scrSize.width), -(bufferHeight = scrSize.height), currHWND = master.getHWND());
          if (!rv)
          {
            cleanupRenderer(true);
            if (Sage.DBG) System.out.println("About to intialize DX9 renderer(2) in standard mode with size " + maxScrSize.width + "x" + maxScrSize.height);
            rv = initDX9SageRenderer0(bufferWidth = maxScrSize.width, bufferHeight = maxScrSize.height, currHWND = master.getHWND());
          }
          else
            fullScreenExMode = true;
        }
        else
        {
          if (Sage.DBG) System.out.println("About to intialize DX9 renderer(2) in standard mode with size " + maxScrSize.width + "x" + maxScrSize.height);
          rv = initDX9SageRenderer0(bufferWidth = maxScrSize.width, bufferHeight = maxScrSize.height, currHWND = master.getHWND());
        }
        if (!rv)
        {
          if (Sage.DBG) System.out.println("Failed allocating DirectX9 renderer...it worked before, so just wait until it works again...");
          try{Thread.sleep(500);}catch(Exception e){}
        }
      }
      deviceLost = false;
      // We may need to reload the video player when reloading the 3D system, so do that as well
      vf.reloadFile();
    }

    boolean hasVideo = false;
    boolean hasGraphics = false;
    for (int i = 0; i < currDisplayList.size(); i++)
    {
      RenderingOp op = (RenderingOp) currDisplayList.get(i);
      if (op.isVideoOp())
      {
        hasVideo = true;
        if (hasGraphics)
          break;
      }
      else if (op.isImageOp() || op.isPrimitiveOp() || op.isTextOp())
      {
        hasGraphics = true;
        if (hasVideo)
          break;
      }
    }

    disableUIBackingSurface = Sage.getBoolean("ui/disable_ui_composite_surface", false);

    precacheImages();
    // NOW EXECUTE THE DISPLAY LIST
    establishRenderThread();
    lastPixelRenderCount = 0;
    lastPixelInputCount = 0;
    boolean masterSizeChange = !master.getSize().equals(lastMasterSize);
    boolean didFullClear = false;
    clearScene0(didFullClear = (uiMgr.getCurrUI() != lastUI || masterSizeChange));
    lastUI = uiMgr.getCurrUI();
    lastMasterSize = master.getSize();
    currRT = 0;
    rtStack.clear();
    animsThatGoAfterSurfPop.clear();

    if (backSurf == 0 && !uiMgr.areLayersEnabled() && !disableUIBackingSurface)
    {
      if (Sage.DBG) System.out.println("DX9 allocating the backing surface to cache all UI rendering while video is playing");
      backSurf = createD3DRenderTarget0(bufferWidth, bufferHeight);
    }
    rerenderedDL = (currDisplayList == lastDL) && Sage.WINDOWS_OS && !waitIndicatorState &&
        Sage.getBoolean("ui/enable_display_list_vram_caching", false);
    beganScene = false;
    int viewportWidth = master.getWidth();
    int viewportHeight = master.getHeight();
    projMat = generateProjectionMatrix(0, 0);
    effectStack.clear();
    // This is the view-projection matrix currently
    currMat.set(projMat);
    currEffectMat = new javax.vecmath.Matrix4f();
    currEffectMat.setIdentity();
    effectStack.push(new EffectStackItem(currEffectMat, projMat, 1.0f, null));
    for (int r = 0; r < 4; r++)
      for (int c = 0; c < 4; c++)
        currCoords[r + c*4] = currMat.getElement(r, c); // TRANSPOSED
    renderStartTime = Sage.eventTime();
    effectAnimationsWereActive = effectAnimationsActive;
    effectAnimationsActive = false;
    effectAnimationsLocked = false;
    hadVirginEffects = false;
    currEffectAlpha = 1.0f;
    currEffectClip = null;
    try
    {
      boolean setVideoRegion = false;
      // If the video aspect changes we have to redo the black bars so re-render this DL
      boolean videoARChange = (lastVideoARx != videoARx || lastVideoARy != videoARy ||
          lastAssMode != vf.getAspectRatioMode() || lastVideoWidth != videoWidth || lastVideoHeight != videoHeight);
      if (videoARChange || lastDL != currDisplayList || effectAnimationsWereActive || waitIndicatorState || disableUIBackingSurface)
      {
        usingFreshDL = lastDL != currDisplayList;
        lastDL = null;
        lastVideoARx = videoARx;
        lastVideoARy = videoARy;
        lastVideoWidth = videoWidth;
        lastVideoHeight = videoHeight;
        lastAssMode = vf.getAspectRatioMode();

        if (uiMgr.areLayersEnabled())
        {
          // This is all of the surface names that have been used in Out animation operations. Therefore
          // they should NOT also be used for any In operation. If they are we should duplicate
          // the surface and use that for the Out operation.
          fixDuplicateAnimSurfs(currDisplayList, clipRect);
        }
        else if (hasVideo && !disableUIBackingSurface)
        {
          setRenderTarget0(backSurf);
          if (didFullClear)
          {
            cCache[0] = 0;
            fillShape0(null, null, cCache, -1, 4, clipRect);
          }
        }

        compositeOps.clear();
        java.util.Set clearedSurfs = new java.util.HashSet();

        for (int i = 0; i <= currDisplayList.size(); i++)
        {
          RenderingOp op;
          if (i == currDisplayList.size())
          {
            if (waitIndicatorState && waitIndicatorRops != null)
            {
              op = (RenderingOp) waitIndicatorRops.get((++waitIndicatorRopsIndex) % waitIndicatorRops.size());
            }
            else
              break;
          }
          else
            op = (RenderingOp) currDisplayList.get(i);
          // Skip transparent rendering ops
          if (!op.isEffectOp() && currEffectAlpha == 0)
            continue;
          if (op.isEffectOp())
          {
            processEffectOp(op);
          }
          else if (op.isImageOp())
          {
            if (!beganScene)
            {
              if (!beginScene0(viewportWidth, viewportHeight))
                return false;
              beganScene = true;
            }
            // Process operations on the same texture that follow this one
            int rectCoordIndex = 0;
            srcXCache[rectCoordIndex] = op.srcRect.x;
            srcYCache[rectCoordIndex] = op.srcRect.y;
            srcWCache[rectCoordIndex] = op.srcRect.width;
            srcHCache[rectCoordIndex] = op.srcRect.height;
            dstXCache[rectCoordIndex] = op.destRect.x;
            dstYCache[rectCoordIndex] = op.destRect.y;
            dstWCache[rectCoordIndex] = op.destRect.width;
            dstHCache[rectCoordIndex] = op.destRect.height;
            cCache[rectCoordIndex] = getShadingColor(op.renderColor, op.alphaFactor * currEffectAlpha);
            if (currEffectClip != null)
            {
              // NOTE: WE NEED TO PROPERLY CLIP THE SRC RECT FOR THE DIFFUSE TEXTURE AS WELL!!!!!
              if (!clipSrcDestCache(rectCoordIndex, currEffectClip))
              {
                continue;
              }
            }
            lastPixelRenderCount += op.destRect.width * op.destRect.height;
            lastPixelInputCount += op.srcRect.width * op.srcRect.height;
            long texturePtr = op.texture.getNativeImage(this, op.textureIndex);
            rectCoordIndex++;
            boolean interpMode = (op.texture.getSource() instanceof MetaFont ||
                (Math.round(op.srcRect.width) == Math.round(op.destRect.width) &&
                Math.round(op.srcRect.height) == Math.round(op.destRect.height)));
            while (i + 1 < currDisplayList.size())
            {
              RenderingOp nextOp = (RenderingOp) currDisplayList.get(i + 1);
              if (!nextOp.isImageOp() || op.diffuseTexture != null || nextOp.diffuseTexture != null)
                break;
              if (nextOp.texture != op.texture || nextOp.textureIndex != op.textureIndex)
                break;
              if (interpMode != (nextOp.texture.getSource() instanceof MetaFont ||
                  (Math.round(nextOp.srcRect.width) == Math.round(nextOp.destRect.width) &&
                  Math.round(nextOp.srcRect.height) == Math.round(nextOp.destRect.height))))
                break;
              srcXCache[rectCoordIndex] = nextOp.srcRect.x;
              srcYCache[rectCoordIndex] = nextOp.srcRect.y;
              srcWCache[rectCoordIndex] = nextOp.srcRect.width;
              srcHCache[rectCoordIndex] = nextOp.srcRect.height;
              dstXCache[rectCoordIndex] = nextOp.destRect.x;
              dstYCache[rectCoordIndex] = nextOp.destRect.y;
              dstWCache[rectCoordIndex] = nextOp.destRect.width;
              dstHCache[rectCoordIndex] = nextOp.destRect.height;
              cCache[rectCoordIndex] = getShadingColor(nextOp.renderColor, nextOp.alphaFactor * currEffectAlpha);
              boolean skipMe = false;
              if (currEffectClip != null)
              {
                if (!clipSrcDestCache(rectCoordIndex, currEffectClip))
                {
                  rectCoordIndex--;
                  skipMe = true;
                }
              }
              if (!skipMe)
              {
                lastPixelRenderCount += nextOp.destRect.width * nextOp.destRect.height;
                lastPixelInputCount += nextOp.srcRect.width * nextOp.srcRect.height;
              }
              i++;
              rectCoordIndex++;
            }
            boolean texMapRes;
            if (op.diffuseTexture != null)
            {
              long diffusePtr = op.diffuseTexture.getNativeImage(this, 0);
              texMapRes = textureDiffuseMap0(texturePtr, diffusePtr, srcXCache[0], srcYCache[0],
                  srcWCache[0], srcHCache[0], op.diffuseSrcRect.x, op.diffuseSrcRect.y, op.diffuseSrcRect.width,
                  op.diffuseSrcRect.height, dstXCache[0], dstYCache[0], dstWCache[0],
                  dstHCache[0], (op.texture.getSource() instanceof MetaFont ||
                      (Math.round(op.srcRect.width) == Math.round(op.destRect.width) &&
                      Math.round(op.srcRect.height) == Math.round(op.destRect.height))) ? 0 : 1, 0,
                          cCache[0], currCoords);
              op.diffuseTexture.removeNativeRef(this, 0);
            }
            else
            {
              texMapRes = textureMultiMap0(texturePtr, srcXCache, srcYCache, srcWCache, srcHCache, dstXCache,
                  dstYCache, dstWCache, dstHCache,
                  (interpMode && Math.abs(MathUtils.getScaleX(currEffectMat)) == 1 && Math.abs(MathUtils.getScaleY(currEffectMat)) == 1) ? 0 : 1, 0, cCache,
                      rectCoordIndex, currCoords);
            }
            op.texture.removeNativeRef(this, op.textureIndex);
            // If there was a failing using this texture then remove it so we can re-create it
            if (!texMapRes)
              op.texture.setNativePointer(this, op.textureIndex, 0, 0);
          }
          else if (op.isTextOp())
          {
            if (!beganScene)
            {
              if (!beginScene0(viewportWidth, viewportHeight))
                return false;
              beganScene = true;
            }
            if (op.text.fontImage != null && op.text.renderRectCache != null && op.text.renderImageNumCache.length > 0)
            {
              renderTextTextureOp(op);
            }
            else
            {
              // Render using 3D text operations directly, required for Unicode text
              Long fontPtr = (Long) fontCacheMap.get(op.text.font);
              if (fontPtr == null)
              {
                if (masterSizeChange)
                {
                  // If we don't clear the fonts somewhere, it's too easy to run out of vram. So
                  // do it here. Changing the frame size is the most likely thing that'll cause
                  // the allocation of a large number of fonts so this protects against that.
                  freeAllFonts();
                }
                //java.awt.FontMetrics fm = master.getFontMetrics(op.text.font);
                long newFontPtr = create3DFont0(MetaFont.getJavaFont(op.text.font).getFamily(), op.text.font.getSize(),
                    (op.text.font.getStyle() & MetaFont.BOLD) != 0,
                    (op.text.font.getStyle() & MetaFont.ITALIC) != 0);
                if (newFontPtr != 0)
                {
                  if (Sage.DBG) System.out.println("Added font to 3D cache " + op.text.font);
                  fontCacheMap.put(op.text.font, fontPtr = new Long(newFontPtr));
                }
              }
              if (fontPtr != null)
              {
                // NOTE: Only issue here still is with clipping since the renderText0 call will not clip the text itself. The clip rect below
                // does work properly for scrolling; but if only part of the text is to be rendered; that's where it fails.
                if (currEffectClip != null)
                {
                  dstXCache[0] = op.destRect.x;
                  dstYCache[0] = op.destRect.y;
                  dstWCache[0] = op.destRect.width;
                  dstHCache[0] = op.destRect.height;
                  srcXCache[0] = op.srcRect.x;
                  srcYCache[0] = op.srcRect.y;
                  srcWCache[0] = op.srcRect.width;
                  srcHCache[0] = op.srcRect.height;
                  if (!clipSrcDestCache(0, currEffectClip))
                    continue;
                  shapeClipRect.setRect(dstXCache[0], dstYCache[0], dstWCache[0], dstHCache[0]);
                }
                else
                  shapeClipRect.setRect(op.destRect.x, op.destRect.y, op.destRect.width, op.destRect.height);
                if (currEffectMat != null)
                  MathUtils.transformRectCoords(shapeClipRect, currEffectMat, shapeClipRect);
                renderText0(op.text.string, fontPtr.longValue(),
                    shapeClipRect.x, shapeClipRect.y, shapeClipRect.width, shapeClipRect.height,
                    getShadingColor(op.renderColor, op.alphaFactor * currEffectAlpha));
              }
            }
          }
          else if (op.isVideoOp())
          {
            if (vf.isNonBlendableVideoPlayerLoaded())
            {
              if (lastVideoRect == null || !SageRenderer.floatRectEquals(op.destRect, lastVideoRect))
              {
                uiMgr.repaintNextRegionChange = true;
              }

              // Convert from UI coordinates to screen coordinates
              if (Math.round(op.destRect.width) == master.getWidth() &&
                  Math.round(op.destRect.height) == master.getHeight())
              {
                vf.setVideoBounds(null);
                if (!vf.isColorKeyingEnabled() && vf.isNonBlendableVideoPlayerLoaded())
                {
                  java.util.ArrayList convertedRegions = getCoveredRegions(currDisplayList, op);
                  java.awt.Rectangle[] fixedRects = new java.awt.Rectangle[convertedRegions.size()];
                  int[] rectRounds = new int[convertedRegions.size()];
                  for (int j = 0; j < convertedRegions.size(); j++)
                  {
                    java.awt.geom.RoundRectangle2D.Float foofer = (java.awt.geom.RoundRectangle2D.Float)
                        convertedRegions.get(j);
                    fixedRects[j] = foofer.getBounds();
                    rectRounds[j] = Math.round(foofer.arcwidth);
                  }
                  uiMgr.setCompoundWindowRegion2(master.getHWND(),
                      fixedRects, rectRounds);
                  setVideoRegion = true;
                }
              }
              else
              {
                vf.setVideoBounds(op.destRect);
                if (!vf.isColorKeyingEnabled() && vf.isNonBlendableVideoPlayerLoaded())
                {
                  java.util.ArrayList convertedRegions = getCoveredRegions(currDisplayList, op);
                  java.awt.Rectangle[] fixedRects = new java.awt.Rectangle[convertedRegions.size() +
                                                                           op.legacyRegions.size()];
                  int[] rectRounds = new int[convertedRegions.size() + op.legacyRegions.size()];
                  for (int j = 0; j < op.legacyRegions.size(); j++)
                  {
                    java.awt.Rectangle r = (java.awt.Rectangle) op.legacyRegions.get(j);
                    fixedRects[j] = r;
                    rectRounds[j] = 0;
                  }
                  for (int j = 0; j < convertedRegions.size(); j++)
                  {
                    java.awt.geom.RoundRectangle2D.Float foofer = (java.awt.geom.RoundRectangle2D.Float)
                        convertedRegions.get(j);
                    fixedRects[j + op.legacyRegions.size()] = new java.awt.Rectangle();
                    fixedRects[j + op.legacyRegions.size()] = foofer.getBounds();
                    rectRounds[j + op.legacyRegions.size()] = Math.round(foofer.arcwidth);
                  }
                  uiMgr.setCompoundWindowRegion2(master.getHWND(), fixedRects, rectRounds);
                  setVideoRegion = true;
                }
              }
              vf.refreshVideoSizing();
            }
            // We need to refresh the video whenever we redo the regions so always do this.
            // This also can send up update of the video size to the media player which we
            // need for mouse positioning in VMR9 DVD playback so always do it.
            else if (lastVideoRect == null || !SageRenderer.floatRectEquals(op.destRect, lastVideoRect))
            {
              vf.refreshVideoSizing(new java.awt.Dimension(videoWidth, videoHeight));
            }
            lastVideoRect = op.destRect;

            if (!beganScene)
              beganScene = beginScene0(viewportWidth, viewportHeight);

            // If we're doing a full render of a scene that has video in it; we need to indicate
            // that there's graphics for this frame so the black bars get drawn in the case of the only
            // op being a video op. When we come back to render more frames of the video; this will get turned false
            // and then the optmization for only rendering video will occur
            hasGraphics = true;
            processVideoOp(op);
            if (uiMgr.areLayersEnabled())
              compositeOps.add(op);
          }
          else if (op.isPrimitiveOp())
          {
            if (!beganScene)
            {
              if (!beginScene0(viewportWidth, viewportHeight))
                return false;
              beganScene = true;
            }
            processPrimitiveOp(op);
          }
          else if (op.isSurfaceOp() && uiMgr.areLayersEnabled())
          {
            if (ANIM_DEBUG) System.out.println("Surface Op surf=" + op.surface + " on=" + op.isSurfaceOpOn() + " " + op);
            if (op.isSurfaceOpOn())
            {
              if (currRT != 0)
              {
                rtStack.push(new Long(currRT));
              }
              Long newSurfaceObj = (Long) surfaceCache.get(op.surface);
              if (newSurfaceObj == null)
              {
                currRT = createD3DRenderTarget0(bufferWidth, bufferHeight);
                newSurfaceObj = new Long(currRT);
                surfaceCache.put(op.surface, newSurfaceObj);
                rtMemUse += bufferWidth * bufferHeight * 4;
              }
              else
                currRT = newSurfaceObj.longValue();
              if (ANIM_DEBUG) System.out.println("Switched rendering surface to " + op.surface + " " + newSurfaceObj);
              // Don't clear the area if this surface was already used
              setRenderTarget0(currRT);
              if (clearedSurfs.add(newSurfaceObj))
              {
                cCache[0] = 0;
                if (ANIM_DEBUG) System.out.println("Clearing region on surface of " + clipRect);
                fillShape0(null, null, cCache, -1, 4, clipRect);
              }
            }
            else
            {
              // Avoid double compositing operations from nested surface usage
              if (!rtStack.contains(new Long(currRT)))
              {
                compositeOps.add(op);
                java.util.ArrayList remnantAnims = (java.util.ArrayList) animsThatGoAfterSurfPop.remove(new Long(currRT));
                if (remnantAnims != null)
                {
                  if (ANIM_DEBUG) System.out.println("Adding animation ops into composite list now from prior nested surfs:" + remnantAnims);
                  compositeOps.addAll(remnantAnims);
                }
              }
              if (rtStack.isEmpty())
                currRT = 0;
              else
                currRT = ((Long) rtStack.pop()).longValue();
              setRenderTarget0(currRT);
            }
          }
          else if (op.isAnimationOp() && uiMgr.areLayersEnabled())
          {
            processAnimOp(op, i, clipRect);
            if (new Long(currRT).equals(surfaceCache.get(op.surface)) || rtStack.contains(surfaceCache.get(op.surface)))
            {
              if (ANIM_DEBUG) System.out.println("Putting animation op in surf pop map because we're nested in the current surface");
              java.util.ArrayList vecy = (java.util.ArrayList) animsThatGoAfterSurfPop.get(new Long(currRT));
              if (vecy == null)
                animsThatGoAfterSurfPop.put(new Long(currRT), vecy = new java.util.ArrayList());
              vecy.add(compositeOps.remove(compositeOps.size() - 1));
            }
          }
        }
        if (uiMgr.areLayersEnabled())
        {
          java.util.Collections.sort(compositeOps, COMPOSITE_LIST_SORTER);

          fixSurfacePostCompositeRegions();
        }
        if (!setVideoRegion)
        {
          uiMgr.clearWindowRegion2(master.getHWND());
        }
        if (!hasVideo)
          lastVideoRect = null;
      }
      else
      {
        if (ANIM_DEBUG) System.out.println("OPTIMIZATION Skip DL render & composite only! dlSize=" + currDisplayList.size() +
            " optSize=" + compositeOps.size());
      }
      // If rerenderedDL is true then on cleanup the devices aren't destroyed, this helps with FSEX
      // NOTE: It no longer helps since we fixed FSE support
      rerenderedDL = false;

      //if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("DirectX9SageRenderer finished display list, waiting to present...lastPresentDiff=" + (Sage.time() - lastPresentTime));

      lastDL = currDisplayList;
      if (uiMgr.areLayersEnabled())
      {
        if (!performCompositing(hasVideo, viewportWidth, viewportHeight, clipRect))
          return false;
      }
      else if (hasVideo && !disableUIBackingSurface)
      {
        setRenderTarget0(0);
        // Do video first if there is one
        for (int i = 0; i < currDisplayList.size(); i++)
        {
          RenderingOp op = (RenderingOp) currDisplayList.get(i);
          if (op.isVideoOp())
          {
            hasVideo = true;
            synchronized (videoSurfaceLock)
            {
              long myvsurf = videoSurface;
              if (myvsurf != videoSurface)
              {
                if (Sage.DBG) System.out.println("Aborting D3D video render because surface pointer changed!");
                continue;
              }
              if (ANIM_DEBUG) System.out.println("DX9SageRenderer is rendering the video surface " + myvsurf + " to " + op.destRect);
              if (!Sage.WINDOWS_OS || myvsurf != 0)
              {
                if (asyncMplayerRenderCount > 0)
                {
                  if (vf.isMPlayerLoaded())
                  {
                    if (!beganScene)
                    {
                      if (!beginScene0(viewportWidth, viewportHeight))
                        return false;
                      beganScene = true;
                    }
                    stretchBlt0(myvsurf, srcVideoRect.x, srcVideoRect.y, srcVideoRect.width,
                        srcVideoRect.height, 0, usedVideoRect.x, usedVideoRect.y,
                        usedVideoRect.width, usedVideoRect.height, -1);
                  }
                }
                else
                {
                  if (beganScene)
                  {
                    endScene0();
                    beganScene = false;
                  }
                  stretchBlt0(myvsurf, srcVideoRect.x, srcVideoRect.y, srcVideoRect.width,
                      srcVideoRect.height, 0, usedVideoRect.x, usedVideoRect.y,
                      usedVideoRect.width, usedVideoRect.height, inVmr9Callback ? 0 : 1);
                }
              }
              //						if (D3D_DEBUG && Sage.DBG) System.out.println("DX9SageRenderer is DONE rendering the video surface " + myvsurf + " to " + fullVideoRect +
              //							" time=" + myvtime);
            }
          }
        }
        // We not only need to deal with the current one; but if we have graphics from the last render outside of
        // the video area the whole thing needs to be redrawn as well.
        if (hasGraphics || lastHadGraphics)
        {
          // Now we draw the backing UI surface
          if (!beganScene)
          {
            if (!beginScene0(viewportWidth, viewportHeight))
              return false;
            beganScene = true;
          }
          textureMap0(backSurf, clipRect.x, clipRect.y, clipRect.width, clipRect.height,
              clipRect.x, clipRect.y, clipRect.width, clipRect.height,
              0, java.awt.AlphaComposite.SRC_OVER, getCompositedColor(0xFFFFFFFF, 1.0f), false);
        }
      }
      lastHadGraphics = hasGraphics;
      if (beganScene)
        endScene0();

      // Present will spin the CPU waiting for its turn to flip the page, if we know it's going to do this then
      // do a sleep to make it take a little longer
      if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("DirectX9SageRenderer finished display list, now presenting, beganScene=" + beganScene +
          "  lastPresentTimeDiff=" + (Sage.eventTime() - lastPresentTime));

      boolean currFullScreen = uiMgr.isFullScreen();
      if (fullScreenExMode && Sage.WINDOWS_OS && (!currFullScreen || !uiMgr.isFSEXMode()))
      {
        // If we're in a VMR9 thread then we can't destroy the 3D system. We have to wait until
        // the player is closed and then destroy it.
        stopVmr9Callback = true;
        if (almostInVmr9Callback || inVmr9Callback)
        {
          return false;
        }
        if (Sage.DBG) System.out.println("Disabling full screen exclusive mode");
        java.awt.Dimension scrSize = uiMgr.getScreenSize();
        java.awt.Dimension maxScrSize = getMaxScreenSize();
        fullScreenExMode = false;
        cleanupRenderer(true);
        synchronized (this)
        {
          //rerenderedDL = true;
          SpecialWindow sw = uiMgr.getGlobalFSFrame();
          if (sw != null)
            sw.setVisible(false);
          deviceLost = true;
          // The screen resolution may have changed, so recalculate the width & height
          //int rebuildTries = 3;
          if (Sage.DBG) System.out.println("About to intialize DX9 renderer(3) in standard mode with size " + maxScrSize.width + "x" + maxScrSize.height);
          while (!initDX9SageRenderer0(bufferWidth = maxScrSize.width, bufferHeight = maxScrSize.height, currHWND = master.getHWND()) ||
              !uiMgr.doesMatchScreenSize(scrSize))
          {
            if (Sage.DBG) System.out.println("DX9Renderer creation failed. Wait and then try again, it worked before...size " + scrSize.width + "x" + scrSize.height);
            try
            {Thread.sleep(500);}
            catch(Exception e)
            {}
            cleanupRenderer(true);
            //if (rebuildTries-- <= 0)
            //	return false;
            maxScrSize = getMaxScreenSize();
          }
          deviceLost = false;
        }
        // We may need to reload the video player when reloading the 3D system, so do that as well
        vf.reloadFile();
        // We need to redraw the UI since we just rebuilt the rendering core so do a full refresh.
        // We also need to disable the always on top feature of the window
        uiMgr.trueValidate();
        wasInFullScreen = currFullScreen;
        return true;
      }

      releaseRenderThread();
      boolean presentRes = present0(clipRect.x, clipRect.y, clipRect.width, clipRect.height);
      lastPresentTime = Sage.eventTime();

      // NOTE: A failure may be a FSE switch due to the new window architecture we use
      if (!wasInFullScreen && Sage.WINDOWS_OS && currFullScreen &&
          !uiMgr.getBoolean("ui/disable_dx9_full_screen_ex", true))
      {
        // If we're in a VMR9 thread then we can't destroy the 3D system. We have to wait until
        // the player is closed and then destroy it.
        stopVmr9Callback = true;
        if (almostInVmr9Callback || inVmr9Callback)
        {
          return false;
        }
        if (Sage.DBG) System.out.println("Switching to DX9 full screen mode...");
        java.awt.Dimension scrSize = uiMgr.getScreenSize();
        java.awt.Dimension maxScrSize = getMaxScreenSize();
        cleanupRenderer(true);
        synchronized (this)
        {
          //rerenderedDL = true;
          deviceLost = true;
          // The screen resolution may have changed, so recalculate the width & height
          bufferWidth = scrSize.width;
          bufferHeight = scrSize.height;
          if (Sage.DBG) System.out.println("About to intialize DX9 renderer(5) in FSE mode with size " + bufferWidth + "x" + bufferHeight);
          if (!initDX9SageRenderer0(-bufferWidth, -bufferHeight, currHWND = master.getHWND()) ||
              !uiMgr.doesMatchScreenSize(scrSize))
          {
            bufferWidth = scrSize.width;
            bufferHeight = scrSize.height;
            fullScreenExMode = false;
            if (Sage.DBG) System.out.println("DX9Renderer creation failed. Going back to non-full screen mode");
            cleanupRenderer(true);
            try
            {Thread.sleep(50);}
            catch(Exception e)
            {}
            maxScrSize = getMaxScreenSize();
            //int rebuildTries = 3;
            if (Sage.DBG) System.out.println("About to intialize DX9 renderer(5) in standard mode with size " + maxScrSize.width + "x" + maxScrSize.height);
            while (!initDX9SageRenderer0(bufferWidth = maxScrSize.width, bufferHeight = maxScrSize.height, currHWND = master.getHWND()) ||
                !uiMgr.doesMatchScreenSize(scrSize))
            {
              if (Sage.DBG) System.out.println("DX9Renderer creation failed. Wait and then try again, it worked before...");
              try
              {Thread.sleep(500);}
              catch(Exception e)
              {}
              cleanupRenderer(true);
              //if (rebuildTries-- <= 0)
              //	return false;
              maxScrSize = getMaxScreenSize();
            }
          }
          else
            fullScreenExMode = true;
          deviceLost = false;
        }
        // We may need to reload the video player when reloading the 3D system, so do that as well
        vf.reloadFile();
        // We need to redraw the UI since we just rebuilt the rendering core so do a full refresh
        uiMgr.trueValidate();
      }
      else if (currHWND != master.getHWND() || (!presentRes && (wasInFullScreen || !Sage.WINDOWS_OS || !currFullScreen)))
      {
        // If we're in a VMR9 thread then we can't destroy the 3D system. We have to wait until
        // the player is closed and then destroy it.
        stopVmr9Callback = true;
        if (almostInVmr9Callback || inVmr9Callback)
        {
          return false;
        }
        // the DX9 device has been lost we need to reallocate it
        if (Sage.DBG) System.out.println("DirectX9 device is lost. Rebuilding it....");
        java.awt.Dimension scrSize = uiMgr.getScreenSize();
        java.awt.Dimension maxScrSize = getMaxScreenSize();
        cleanupRenderer(true);
        synchronized (this)
        {
          //rerenderedDL = true;
          SpecialWindow sw = uiMgr.getGlobalFSFrame();
          if (sw != null)
            sw.setVisible(false);
          deviceLost = true;
          // The screen resolution may have changed, so recalculate the width & height
          fullScreenExMode = false;
          //int rebuildTries = 3;
          if (Sage.DBG) System.out.println("About to intialize DX9 renderer(4) in standard mode with size " + maxScrSize.width + "x" + maxScrSize.height);
          while (!initDX9SageRenderer0(bufferWidth = maxScrSize.width, bufferHeight = maxScrSize.height, currHWND = master.getHWND()) ||
              !uiMgr.doesMatchScreenSize(scrSize))
          {
            if (Sage.DBG) System.out.println("DX9Renderer creation failed. Wait and then try again, it worked before...size " + scrSize.width + "x" + scrSize.height);
            try
            {Thread.sleep(500);}
            catch(Exception e)
            {}
            cleanupRenderer(true);
            //if (rebuildTries-- <= 0)
            //	return false;
            maxScrSize = getMaxScreenSize();
          }
          deviceLost = false;
        }
        // We may need to reload the video player when reloading the 3D system, so do that as well
        vf.reloadFile();
        // We need to redraw the UI since we just rebuilt the rendering core so do a full refresh
        uiMgr.trueValidate();
      }
      wasInFullScreen = currFullScreen;
    }
    catch (Throwable thr)
    {
      System.out.println("Exception in native 3D system of:" + thr);
      Sage.printStackTrace(thr);
      // If we're in a VMR9 thread then we can't destroy the 3D system. We have to wait until
      // the player is closed and then destroy it.
      stopVmr9Callback = true;
      if (almostInVmr9Callback || inVmr9Callback)
      {
        return false;
      }
      if (Sage.DBG) System.out.println("DirectX9 device is trashed. Rebuilding it....");
      java.awt.Dimension scrSize = uiMgr.getScreenSize();
      java.awt.Dimension maxScrSize = getMaxScreenSize();
      cleanupRenderer(true);
      synchronized (this)
      {
        //rerenderedDL = true;
        SpecialWindow sw = uiMgr.getGlobalFSFrame();
        if (sw != null)
          sw.setVisible(false);
        deviceLost = true;
        // The screen resolution may have changed, so recalculate the width & height
        fullScreenExMode = false;
        //int rebuildTries = 3;
        if (Sage.DBG) System.out.println("About to intialize DX9 renderer(6) in standard mode with size " + maxScrSize.width + "x" + maxScrSize.height);
        while (!initDX9SageRenderer0(bufferWidth = maxScrSize.width, bufferHeight = maxScrSize.height, currHWND = master.getHWND()) ||
            !uiMgr.doesMatchScreenSize(scrSize))
        {
          if (Sage.DBG) System.out.println("DX9Renderer creation failed. Wait and then try again, it worked before...size " + scrSize.width + "x" + scrSize.height);
          try
          {Thread.sleep(500);}
          catch(Exception e)
          {}
          cleanupRenderer(true);
          //if (rebuildTries-- <= 0)
          //	return false;
          maxScrSize = getMaxScreenSize();
        }
        deviceLost = false;
      }
      // We may need to reload the video player when reloading the 3D system, so do that as well
      vf.reloadFile();
      // We need to redraw the UI since we just rebuilt the rendering core so do a full refresh
      uiMgr.trueValidate();
    }
    if (effectAnimationsActive)
    {
      master.effectsNeedProcessing(effectAnimationsLocked);
      // Fix all of the start times for the animations that account for the delay in rendering this frame
      if (hadVirginEffects)
      {
        long startDiff = Sage.eventTime() - renderStartTime;
        for (int i = 0; i < currDisplayList.size(); i++)
        {
          RenderingOp op = (RenderingOp) currDisplayList.get(i);
          if (op.isEffectOp() && op.effectTracker != null)
            op.effectTracker.fixStartTime(startDiff);
        }
      }
    }
    releaseRenderThread();
    uiMgr.checkFSSizing();
    //if (Sage.DBG) System.out.println("DX9 PRESENT DELAY=" + (Sage.time() - lastPresentTime));
    return true;
  }

  private boolean performCompositing(boolean hasVideo, int viewportWidth, int viewportHeight, java.awt.Rectangle clipRect)
  {
    if (ANIM_DEBUG) System.out.println("Performing the surface compositing operations now");
    // Do video first if there is one
    for (int i = 0; i < compositeOps.size(); i++)
    {
      RenderingOp op = (RenderingOp) compositeOps.get(i);
      if (op.isVideoOp())
      {
        hasVideo = true;
        synchronized (videoSurfaceLock)
        {
          long myvsurf = videoSurface;
          if (myvsurf != videoSurface)
          {
            if (Sage.DBG) System.out.println("Aborting D3D video render because surface pointer changed!");
            continue;
          }
          if (ANIM_DEBUG) System.out.println("DX9SageRenderer is rendering the video surface " + myvsurf + " to " + op.destRect);
          if (!Sage.WINDOWS_OS || myvsurf != 0)
          {
            if (asyncMplayerRenderCount > 0)
            {
              if (vf.isMPlayerLoaded())
              {
                if (!beganScene)
                {
                  if (!beginScene0(viewportWidth, viewportHeight))
                    return false;
                  beganScene = true;
                }
                stretchBlt0(myvsurf, srcVideoRect.x, srcVideoRect.y, srcVideoRect.width,
                    srcVideoRect.height, 0, usedVideoRect.x, usedVideoRect.y,
                    usedVideoRect.width, usedVideoRect.height, -1);
              }
            }
            else
            {
              if (beganScene)
              {
                endScene0();
                beganScene = false;
              }
              stretchBlt0(myvsurf, srcVideoRect.x, srcVideoRect.y, srcVideoRect.width,
                  srcVideoRect.height, 0, usedVideoRect.x, usedVideoRect.y,
                  usedVideoRect.width, usedVideoRect.height, inVmr9Callback ? 0 : 1);
            }
          }
          //						if (D3D_DEBUG && Sage.DBG) System.out.println("DX9SageRenderer is DONE rendering the video surface " + myvsurf + " to " + fullVideoRect +
          //							" time=" + myvtime);
        }
      }
    }
    for (int i = 0; i <= compositeOps.size(); i++)
    {
      RenderingOp op = null;
      if (i == compositeOps.size())
      {
        if (waitIndicatorState && waitIndicatorRops != null)
        {
          op = (RenderingOp) waitIndicatorRops.get((++waitIndicatorRopsIndex) % waitIndicatorRops.size());
        }
        else
          break;
      }
      else
        op = (RenderingOp) compositeOps.get(i);
      if (op.isSurfaceOp())
      {
        if (ANIM_DEBUG) System.out.println("Surface Op surf=" + op.surface + " on=" + op.isSurfaceOpOn() + " " + op);
        if (op.isSurfaceOpOff())
        {
          if (!beganScene)
          {
            if (!beginScene0(viewportWidth, viewportHeight))
              return false;
            beganScene = true;
          }
          Long currSurface = (Long) surfaceCache.get(op.surface);
          int compositeMode;
          if ((!hasVideo || videoSurface == 0) && uiMgr.get("ui/animation/background_surface_name", "BG").equals(op.surface))
            compositeMode = java.awt.AlphaComposite.SRC;
          else
            compositeMode = java.awt.AlphaComposite.SRC_OVER;
          java.awt.geom.Rectangle2D validRegion = op.destRect.createIntersection(clipRect);
          textureMap0(currSurface.longValue(), (float)validRegion.getX(), (float)validRegion.getY(),
              (float)validRegion.getWidth(), (float)validRegion.getHeight(),
              (float)validRegion.getX(), (float)validRegion.getY(),
              (float)validRegion.getWidth(), (float)validRegion.getHeight(),
              0, compositeMode, getCompositedColor(0xFFFFFFFF, op.alphaFactor), false);
          if (ANIM_DEBUG) System.out.println("Finished cached surface rendering and re-composited it with the main surface");
        }
      }
      else if (op.isImageOp())
      {
        if (!beganScene)
        {
          if (!beginScene0(viewportWidth, viewportHeight))
            return false;
          beganScene = true;
        }
        // Process operations on the same texture that follow this one
        long texturePtr = op.texture.getNativeImage(this, op.textureIndex);
        boolean texMapRes = textureMap0(texturePtr, op.srcRect.x, op.srcRect.y,
            op.srcRect.width, op.srcRect.height, op.destRect.x, op.destRect.y, op.destRect.width,
            op.destRect.height, (op.texture.getSource() instanceof MetaFont ||
                (Math.round(op.srcRect.width) == Math.round(op.destRect.width) &&
                Math.round(op.srcRect.height) == Math.round(op.destRect.height))) ? 0 : 1, 0,
                    getShadingColor(op.renderColor, op.alphaFactor), true);
        op.texture.removeNativeRef(this, op.textureIndex);
        // If there was a failing using this texture then remove it so we can re-create it
        if (!texMapRes)
          op.texture.setNativePointer(this, op.textureIndex, 0, 0);
      }
      else if (op.isAnimationOp())
      {
        RenderingOp.Animation anime = op.anime;
        if (ANIM_DEBUG) System.out.println("Animation operation found! ANIMAIL ANIMAIL!!! " + op + " scrollSrcRect=" + anime.altSrcRect +
            " scrollDstRect=" + anime.altDestRect);
        // Find the cached surface first
        Long cachedSurface = (Long) surfaceCache.get(op.surface);
        if (cachedSurface != null)
        {
          if (!beganScene)
          {
            if (!beginScene0(viewportWidth, viewportHeight))
              return false;
            beganScene = true;
          }
          if (ANIM_DEBUG) System.out.println("Cached animation surface found: " + op.surface);
          if (ANIM_DEBUG) System.out.println("Rendering Animation " + anime.animation);
          java.awt.geom.Rectangle2D.Float clippedSrcRect = new java.awt.geom.Rectangle2D.Float();
          clippedSrcRect.setRect(op.srcRect);
          java.awt.geom.Rectangle2D.Float clippedDstRect = new java.awt.geom.Rectangle2D.Float();
          clippedDstRect.setRect(op.destRect);
          java.awt.geom.Rectangle2D.Float clonedClipRect = new java.awt.geom.Rectangle2D.Float();
          clonedClipRect.setRect(clipRect);
          Sage.clipSrcDestRects(clonedClipRect, clippedSrcRect, clippedDstRect);
          textureMap0(cachedSurface.longValue(), clippedSrcRect.x, clippedSrcRect.y, clippedSrcRect.width, clippedSrcRect.height,
              clippedDstRect.x, clippedDstRect.y, clippedDstRect.width, clippedDstRect.height,
              (Math.round(clippedSrcRect.width) == Math.round(clippedDstRect.width) &&
              Math.round(clippedSrcRect.height) == Math.round(clippedDstRect.height)) ? 0 : 1, 0,
                  getCompositedColor(0xFFFFFFFF, op.alphaFactor), false);
          if (anime.isDualSurfaceOp())
          {
            // We need to render the other scrolling position
            cachedSurface = (Long) surfaceCache.get(op.anime.altSurfName);
            if (cachedSurface != null)
            {
              if (ANIM_DEBUG) System.out.println("Rendering second scroll surface scrollSrcRect=" + anime.altSrcRect +
                  " scrollDstRect=" + anime.altDestRect);
              clippedSrcRect.setRect(op.anime.altSrcRect);
              clippedDstRect.setRect(op.anime.altDestRect);
              Sage.clipSrcDestRects(clonedClipRect, clippedSrcRect, clippedDstRect);
              textureMap0(cachedSurface.longValue(), clippedSrcRect.x, clippedSrcRect.y, clippedSrcRect.width, clippedSrcRect.height,
                  clippedDstRect.x, clippedDstRect.y, clippedDstRect.width, clippedDstRect.height,
                  (Math.round(clippedSrcRect.width) == Math.round(clippedDstRect.width) &&
                  Math.round(clippedSrcRect.height) == Math.round(clippedDstRect.height)) ? 0 : 1, 0,
                      getCompositedColor(0xFFFFFFFF, op.anime.altAlphaFactor), false);
            }
          }
        }
        else
        {
          if (ANIM_DEBUG) System.out.println("ERROR: Could not find cached animation surface:" + op.surface);
        }
        if (!anime.expired)
          master.setActiveAnimation(op);
      }
    }
    return true;
  }

  private void processVideoOp(RenderingOp op)
  {
    long myvsurf = videoSurface;
    long myvtime = videoFrameTime;
    int vw, vh;
    vw = videoWidth;
    vh = videoHeight;
    java.awt.Dimension vfVidSize = null;
    if (vw <= 0 || vh <= 0) vfVidSize = vf.getVideoSize();
    if (vw <= 0)
    {
      vw = vfVidSize != null ? vfVidSize.width : 0;
      if (vw <= 0)
        vw = 720;
    }
    if (vh <= 0)
    {
      vh = vfVidSize != null ? vfVidSize.height : 0;
      if (vh <= 0)
        vh = MMC.getInstance().isNTSCVideoFormat() ? 480 : 576;
    }
    int assMode = vf.getAspectRatioMode();
    float targetX = op.destRect.x;
    float targetY = op.destRect.y;
    float targetW = op.destRect.width;
    float targetH = op.destRect.height;
    float forcedRatio = (Sage.WINDOWS_OS && myvsurf != 0) ? vf.getCurrentAspectRatio(videoARx, videoARy) : vf.getCurrentAspectRatio();
    if (forcedRatio != 0)
    {
      if (targetW/targetH < forcedRatio)
      {
        float shrink = targetH - targetW/forcedRatio;
        targetH -= shrink;
        targetY += shrink/2;
      }
      else
      {
        float shrink = targetW - targetH*forcedRatio;
        targetW -= shrink;
        targetX += shrink/2;
      }
    }
    float zoomX = vf.getVideoZoomX(assMode);
    float zoomY = vf.getVideoZoomY(assMode);
    float transX = vf.getVideoOffsetX(assMode) * targetW / lastMasterSize.width;
    float transY = vf.getVideoOffsetY(assMode) * targetH / lastMasterSize.height;

    float widthAdjust = (zoomX - 1.0f)*targetW;
    float heightAdjust = (zoomY - 1.0f)*targetH;
    targetX -= widthAdjust/2;
    targetY -= heightAdjust/2;
    targetW += widthAdjust;
    targetH += heightAdjust;

    targetX += transX;
    targetY += transY;

    long videoHShiftFreq =  vf.getVideoHShiftFreq();
    if (videoHShiftFreq != 0)
    {
      float maxHShift = (op.destRect.width - targetW)/2;
      long timeDiff = Sage.time();
      timeDiff %= videoHShiftFreq;
      if (timeDiff < videoHShiftFreq/2)
      {
        if (timeDiff < videoHShiftFreq/4)
          targetX -= maxHShift*timeDiff*4/videoHShiftFreq;
        else
          targetX -= maxHShift - (maxHShift*(timeDiff - videoHShiftFreq/4)*4/videoHShiftFreq);
      }
      else
      {
        timeDiff -= videoHShiftFreq/2;
        if (timeDiff < videoHShiftFreq/4)
          targetX += maxHShift*timeDiff*4/videoHShiftFreq;
        else
          targetX += maxHShift - (maxHShift*(timeDiff - videoHShiftFreq/4)*4/videoHShiftFreq);
      }
    }

    videoSrc.setRect(0, 0, vw, vh);
    videoDest.setRect(targetX, targetY, targetW, targetH);
    clipArea.setRect(0, 0, bufferWidth, bufferHeight);
    Sage.clipSrcDestRects(op.destRect, videoSrc, videoDest);
    srcVideoRect.setFrame(videoSrc);
    srcVideoRect.x = Math.max(0, srcVideoRect.x);
    srcVideoRect.y = Math.max(0, srcVideoRect.y);
    srcVideoRect.width = Math.min(vw - srcVideoRect.x, srcVideoRect.width);
    srcVideoRect.height = Math.min(vh - srcVideoRect.y, srcVideoRect.height);
    usedVideoRect.setFrame(videoDest);
    usedVideoRect.x = Math.max(0, usedVideoRect.x);
    usedVideoRect.y = Math.max(0, usedVideoRect.y);
    usedVideoRect.width = Math.min(bufferWidth - usedVideoRect.x, usedVideoRect.width);
    usedVideoRect.height = Math.min(bufferHeight - usedVideoRect.y, usedVideoRect.height);
    fullVideoRect.setFrame(op.destRect);
    fullVideoRect.x = Math.max(0, fullVideoRect.x);
    fullVideoRect.y = Math.max(0, fullVideoRect.y);
    fullVideoRect.width = Math.min(bufferWidth - fullVideoRect.x, fullVideoRect.width);
    fullVideoRect.height = Math.min(bufferHeight - fullVideoRect.y, fullVideoRect.height);

    // Clear the video rect area; the VMR isn't updating us yet so we need to clear its rectangle
    java.awt.Rectangle tempVideoRect = new java.awt.Rectangle();
    if (Sage.WINDOWS_OS)
    {
      tempVideoRect.setFrame(op.destRect);
      if (uiMgr.areLayersEnabled())
      {
        // Clear the portion of the UI to transparent so the video can show through properly in post-compositing;
        // unless we're using color keying; in which case we should clear it to the color key instead
        cCache[0] = (vf.getColorKey() == null || !vf.isColorKeyingEnabled()) ? 0 : vf.getColorKey().getRGB();
        fillShape0(null, null, cCache, -1, 4, tempVideoRect);
      }
      else if (myvsurf == 0)
      {
        cCache[0] = (vf.getColorKey() == null || !vf.isColorKeyingEnabled()) ? 0xFF000000 : vf.getColorKey().getRGB();
        fillShape0(null, null, cCache, -1, 4, tempVideoRect);
      }
      else
      {
        // Clear the portion of the UI to transparent so the video can show through properly in post-compositing;
        // unless we're using color keying; in which case we should clear it to the color key instead
        cCache[0] = (vf.getColorKey() == null || !vf.isColorKeyingEnabled()) ? 0 : vf.getColorKey().getRGB();
        fillShape0(null, null, cCache, -1, 4, tempVideoRect);
      }
    }

    cCache[0] = vf.getVideoBGColor().getRGB();
    // Need to clear the left edge of the video region
    if (usedVideoRect.x > fullVideoRect.x)
    {
      tempVideoRect.setFrame(fullVideoRect.x, fullVideoRect.y, usedVideoRect.x - fullVideoRect.x,
          fullVideoRect.height);
      fillShape0(null, null, cCache, -1, 4, tempVideoRect);
    }
    // Need to clear the top edge of the video region
    if (usedVideoRect.y > fullVideoRect.y)
    {
      tempVideoRect.setFrame(fullVideoRect.x, fullVideoRect.y, fullVideoRect.width,
          usedVideoRect.y - fullVideoRect.y);
      fillShape0(null, null, cCache, -1, 4, tempVideoRect);
    }
    // Need to clear the right edge of the video region
    if (usedVideoRect.x + usedVideoRect.width < fullVideoRect.x + fullVideoRect.width)
    {
      int adjust = (fullVideoRect.x + fullVideoRect.width) - (usedVideoRect.x + usedVideoRect.width);
      tempVideoRect.setFrame(fullVideoRect.x + fullVideoRect.width - adjust, fullVideoRect.y, adjust,
          fullVideoRect.height);
      fillShape0(null, null, cCache, -1, 4, tempVideoRect);
    }
    // Need to clear the bottom edge of the video region
    if (usedVideoRect.y + usedVideoRect.height < fullVideoRect.y + fullVideoRect.height)
    {
      int adjust = (fullVideoRect.y + fullVideoRect.height) - (usedVideoRect.y + usedVideoRect.height);
      tempVideoRect.setFrame(fullVideoRect.x, fullVideoRect.y + fullVideoRect.height - adjust, fullVideoRect.width,
          adjust);
      fillShape0(null, null, cCache, -1, 4, tempVideoRect);
    }
    // If we're not doing compositing then render the video surface now!
    if (disableUIBackingSurface && !uiMgr.areLayersEnabled())
    {
      synchronized (videoSurfaceLock)
      {
        if (myvsurf != videoSurface)
        {
          if (Sage.DBG) System.out.println("Aborting D3D video render because surface pointer changed!");
          return;
        }
        if (D3D_DEBUG && Sage.DBG) System.out.println("DX9SageRenderer is rendering the video surface " + myvsurf + " to " + op.destRect +
            " time=" + myvtime);
        if (!Sage.WINDOWS_OS || myvsurf != 0)
        {
          if (asyncMplayerRenderCount > 0 && vf.isMPlayerLoaded())
          {
            stretchBlt0(myvsurf, srcVideoRect.x, srcVideoRect.y, srcVideoRect.width,
                srcVideoRect.height, 0, usedVideoRect.x, usedVideoRect.y,
                usedVideoRect.width, usedVideoRect.height, -1);
          }
          else
          {
            if (beganScene)
            {
              endScene0();
              beganScene = false;
            }
            stretchBlt0(myvsurf, srcVideoRect.x, srcVideoRect.y, srcVideoRect.width,
                srcVideoRect.height, 0, usedVideoRect.x, usedVideoRect.y,
                usedVideoRect.width, usedVideoRect.height, inVmr9Callback ? 0 : 1);
          }
        }
        if (D3D_DEBUG && Sage.DBG) System.out.println("DX9SageRenderer is DONE rendering the video surface " + myvsurf + " to " + fullVideoRect +
            " time=" + myvtime);
      }
    }
  }

  public static int getGradientColor(ShapeDescription sd, float x, float y)
  {
    // Calculate the projection of the point onto the vector, and then we use that distance relative to the
    // length of the vector to determine what proportionality of each color to use.
    float frac2 = Math.abs((x-sd.fx1)*(sd.fx2-sd.fx1) + (y-sd.fy1)*(sd.fy2-sd.fy1)) /
        ((sd.fx2-sd.fx1)*(sd.fx2-sd.fx1) + (sd.fy2-sd.fy1)*(sd.fy2-sd.fy1));
    if (Math.abs(frac2 - 1.0f) < 0.01)
      frac2 = 1.0f;
    else if (Math.abs(frac2) < 0.01)
      frac2 = 0f;
    if (frac2 > 1.0f || frac2 < 0) // don't convert 1.0 to 0
      frac2 = frac2 % 1.0f;
    float frac1 = 1.0f - frac2;
    return 0xFF000000 | ((int)(sd.gradc1.getRed()*frac1 + sd.gradc2.getRed()*frac2) << 16) |
        ((int)(sd.gradc1.getGreen()*frac1 + sd.gradc2.getGreen()*frac2) << 8) |
        ((int)(sd.gradc1.getBlue()*frac1 + sd.gradc2.getBlue()*frac2));
  }

  public void present(java.awt.Rectangle clipRect)
  {
    //		present0(clipRect.x, clipRect.y, clipRect.width, clipRect.height);
    markAnimationStartTimes();
  }

  private void freeAllFonts()
  {
    java.util.Iterator walker = fontCacheMap.values().iterator();
    while (walker.hasNext())
    {
      free3DFont0(((Long) walker.next()).longValue());
    }
    fontCacheMap.clear();
  }

  public long getLastPixelRenderCount()
  { return lastPixelRenderCount; }
  public long getLastPixelInputCount()
  { return lastPixelInputCount; }

  public void cleanupRenderer()
  {
    cleanupRenderer(false);
  }
  public void cleanupRenderer(boolean dontClearDLs)
  {
    /*		if (wasInFullScreen)
		{
			new Thread()
			{
				public void run()
				{
					// This was causing issues sometimes so delay it to avoid interacting while doing the full screen switch
					try{Thread.sleep(3000);}catch(Exception e){}
					UIManager.setAlwaysOnTop(vf.getVideoHandle(), false);
				}
			}.start();
		}*/
    // Do NOT sync before we call prepareForReload or we can deadlock because that has to wait on the AWT thread which
    // can link back around to the active thread due to events coming in.
    vf.prepareForReload();
    synchronized (refCountLock)
    {
      if (Sage.DBG && nativeDeviceRefs > 0) System.out.println("DX9 renderer is waiting for refs to be released before continuing w/ device cleanup...");
      while (nativeDeviceRefs > 0)
      {
        try{refCountLock.wait(100);}catch(Exception e){}
      }
      cleanupLock = true;
    }
    // Also, don't sync before we clear the native cache or we can deadlock as well
    if (!Thread.holdsLock(this))
      MetaImage.clearNativeCache(this);
    synchronized (this)
    {
      wasInFullScreen = false;
      freeAllFonts();

      // Clear out the alternate render targets
      java.util.Iterator walker = surfaceCache.values().iterator();
      while (walker.hasNext())
        freeD3DTexturePointer0(((Long) walker.next()).longValue());
      if (backSurf != 0)
        freeD3DTexturePointer0(backSurf);
      backSurf = 0;
      surfaceCache.clear();
      rtMemUse = 0;

      // It's possible to clean this up without ever allocating it, so if we never loaded the native libs
      // then don't try to do any native freeing (the prior calls won't do anything if we've never loaded anything
      // but trying to call this can cause an UnsatisfiedLinkError
      if (loadedDX9Lib)
        cleanupDX9SageRenderer0();
      if (!dontClearDLs)
        super.cleanupRenderer();
      stopVmr9Callback = false;
      videoSurface = 0;
      altVideoSurface = 0;
    }
    synchronized (refCountLock)
    {
      cleanupLock = false;
      refCountLock.notifyAll();
    }
  }

  public static void vmr9RenderNotify(int flags, long pSurface, long pTexture,
      long startTime, long endTime, int width, int height)
  {
    if (defaultDX9Renderer != null)
      defaultDX9Renderer.vmr9RenderUpdate(flags, pSurface, pTexture, startTime, endTime, width, height, 0, 0);
  }
  public static void vmr9RenderNotify(int flags, long pSurface, long pTexture,
      long startTime, long endTime, int width, int height, int arx, int ary)
  {
    if (defaultDX9Renderer != null)
      defaultDX9Renderer.vmr9RenderUpdate(flags, pSurface, pTexture, startTime, endTime, width, height, arx, ary);
  }
  public void vmr9RenderUpdate(int flags, long pSurface, long pTexture, long startTime, long endTime, int width,
      int height, int arx, int ary)
  {
    if (stopVmr9Callback) return;
    if (pSurface == 0)
    {
      // Its called this way at cleanup so we don't try to use an old pointer
      synchronized (videoSurfaceLock)
      {
        if (flags == 0xCAFEBABE)
        {
          //if (videoSurface != 0)
          //	if (Sage.DBG) System.out.println("VMR9 rendering done VRAM free=" + getAvailableVideoMemory0());
          videoSurface = 0;
        }
        else
        {
          //if (altVideoSurface != 0)
          //	if (Sage.DBG) System.out.println("VMR9 rendering done ALT VRAM free=" + getAvailableVideoMemory0());
          altVideoSurface = 0;
        }
      }
      return;
    }
    if (!master.isAlive()) return;
    // We need to to be the highest priority thread so we return control back to the VMR so it can keep
    // the frame rate up. Most importantly, when we wakeup the FR it won't take control because its a lower
    // priority than us.
    if (arx == width && ary == height)
    {
      // NOTE: clear the aspect ratio information we're getting if it just matches the video size; that happens
      // if it doesn't really know what's correct and then our estimate would be better
      arx = ary = 0;
    }
    if (flags != 0xCAFEBABE)
    {
      //if (altVideoSurface == 0)
      //	if (Sage.DBG) System.out.println("VMR9 rendering began ALT VRAM free=" + getAvailableVideoMemory0());
      altVideoSurface = pSurface;
      altVideoWidth = width;
      altVideoHeight = height;
      altVideoFrameTime = startTime;
      altVideoARx = arx;
      altVideoARy = ary;
      // Preview rendering only drives the display when the video stream is paused.
      if (vf.isPlayin())
      {
        if (D3D_DEBUG && Sage.DBG) System.out.println("DirectX9SageRenderer ALT vmr9RenderNotify flags=" + flags + " pSurface=" + pSurface +
            " pTexture=" + pTexture + " start=" + startTime + " end=" + endTime +
            " asx=" +width+" asy="+height);
        return;
      }
    }
    else
    {
      //if (videoSurface == 0)
      //	if (Sage.DBG) System.out.println("VMR9 rendering began VRAM free=" + getAvailableVideoMemory0());
      if (Sage.DBG && (videoWidth != width || videoHeight != height || videoARx != arx || videoARy != ary)) System.out.println("VMR9 info has changed width=" + width + " height=" + height
          + " arx=" + arx+ " ary=" + ary);
      synchronized (videoSurfaceLock)
      {
        videoSurface = pSurface;
        videoWidth = width;
        videoHeight = height;
        videoFrameTime = startTime;
        videoARx = arx;
        videoARy = ary;
      }
    }
    if (!doesNextDisplayListHaveVideo())
    {
      // Video frame rate DOES NOT drive the display if there's no video being shown
      return;
    }
    int oldPriority = Thread.currentThread().getPriority();
    if (oldPriority != Thread.MAX_PRIORITY)
      Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    // NARFLEX: 4/1/09 - I moved this to be before we acquire the lock because if the VMR callback thread is waiting for this
    // lock and the 3D system fails in rendering; it'll then try to reload the 3D device if this flag is not set. If it does that; then
    // when it tries to cleanup the media player it will deadlock because this thread is still running w/ media player resources. But if this
    // flags is set; then the thread which has the finalRenderExecutionLock will just bail out of rendering and wait for the next pass to
    // do the device reload.
    almostInVmr9Callback = true;
    synchronized (master.finalRenderExecutionLock)
    {
      inVmr9Callback = true;
      if (D3D_DEBUG && Sage.DBG) System.out.println("DirectX9SageRenderer " +
          (flags == 0xCAFEBABE ? "" : "ALT ") + "vmr9RenderNotify flags=" + flags + " pSurface=" + pSurface +
          " pTexture=" + pTexture + " start=" + startTime + " end=" + endTime +
          " asx=" +width+" asy="+height);
      /*
       * THIS IS THE MAIN CULPRIT!!!!!!!!!!!!!!!!!!!!!!!!!!!
       * THIS IS WHAT'S CAUSING CORRUPTION IN THE GRAPHICS WHEN THE VIDEO IS PLAYING, AND ITS
       * ALSO WHAT'S PUSHING THE GPU OVER ITS LIMIT AND CAUSING IT TO NOT BE ABLE TO MEET THE
       * 60 FPS REQUIREMENT
       * THE CORRUPTION MAKES ME ALMOST POSITIVE THAT IT HAS TO DO WITH SOME SORT OF SYNC
       * BETWEEN THE FR THREAD AND THE VMR NOTIFY.
       *
       * THE BEST SOLUTION IS TO EXECUTE THE FINAL RENDER FROM THIS THREAD AND THERE'S NO
       * PROBLEMS WITH THREADING. WE GET ZERO CORRUPTION OF THE UI WHEN THE VIDEO IS PLAYING
       * IF WE DO IT THIS WAY.
       */
      //stretchBlt0(videoSurface, 0, 0, videoWidth, videoHeight, videoTexture, 0, 0, videoWidth, videoHeight, 0);
      //videoTexture = videoSurface;
      //if (D3D_DEBUG && Sage.DBG) System.out.println("DirectX9SageRenderer vmr9RenderNotify is done with the stretchBlt");
      master.executeFinalRenderCycle(null, this);

      inVmr9Callback = false;
      almostInVmr9Callback = false;
    }
    if (Thread.MAX_PRIORITY != oldPriority)
      Thread.currentThread().setPriority(oldPriority);
    if (D3D_DEBUG && Sage.DBG) System.out.println("DirectX9SageRenderer vmr9RenderNotify is returning");
  }

  public boolean createNativeImage(MetaImage image, int imageIndex)
  {
    // First try to get the information directly
    String imgSrcName = image.getLcSourcePathname();
    boolean canLoadCompressed = false;
    if (imageIndex == 0 && (imgSrcName.endsWith(".jpg") || imgSrcName.endsWith(".jpeg") || /*imgSrcName.endsWith(".png") ||*/ imgSrcName.endsWith(".bmp")) &&
        image.getRotation() == 0)
      canLoadCompressed = true;
    // We're WAY faster than DX9 at this now
    if (canLoadCompressed && Sage.getBoolean("ui/enable_d3d_compressed_loading_new", false))
    {
      long nativePtr = 0;
      int width = image.getWidth(imageIndex);
      int height = image.getHeight(imageIndex);
      int pow2W, pow2H;
      pow2W = pow2H = 1;
      while (pow2W < width)
        pow2W = pow2W << 1;
      while (pow2H < height)
        pow2H = pow2H << 1;
      int nativeMemUse = pow2W*pow2H*4;

      long availableLimit = 8000000;//Sage.getLong("ui/video_memory_available_minimum_limit", 2000000);
      MediaFile mf = vf.getCurrFile();
      // If there's a video file loaded then we can reduce the minimum limit
      if (vf.isMediaPlayerLoaded() && mf != null && mf.hasVideoContent())
        availableLimit = Math.max(0, availableLimit - 1500000);
      try
      {
        incDevRefCount();
        if (pD3DDevice == 0)
          return false;
        synchronized (MetaImage.getNiaCacheLock(this))
        {
          long newLimit = getAvailableVideoMemory0()*VRAM_USAGE_PERCENT/100;
          newLimit = Math.min(newLimit, Sage.getInt("ui/max_d3d_vram_usage", 120000000));
          if (newLimit > videoMemoryLimit)
          {
            videoMemoryLimit = newLimit;
            if (Sage.DBG) System.out.println("New video memory limit=" + videoMemoryLimit);
          }
          while (MetaImage.getNativeImageCacheSize(this) + rtMemUse + nativeMemUse > videoMemoryLimit ||
              getAvailableVideoMemory0() < availableLimit + nativeMemUse)
          {
            Object[] oldestImage = MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
            if (oldestImage == null)
            {
              if (Sage.DBG) System.out.println("DX9 is unable to find an image to release to add " + image.getSource() +
                  " cacheSize=" + MetaImage.getNativeImageCacheSize(this) + " rtMemUse=" + rtMemUse + " vramAvail=" + getAvailableVideoMemory0());
              // Don't return false, let DX9 try to do some memory management to clean
              // this up and give us some back.
              break;
            }
            if (Sage.DBG) System.out.println("DX9 is freeing texture memory to make room size=" + MetaImage.getNativeImageCacheSize(this) +
                " rtMemUse=" + rtMemUse + " availMem=" + getAvailableVideoMemory0() + " src=" + ((MetaImage) oldestImage[0]).getSource());
            ((MetaImage) oldestImage[0]).clearNativePointer(this, ((Integer) oldestImage[1]).intValue());
          }
          MetaImage.reserveNativeCache(this, nativeMemUse);
        }

        byte[] imageBytes = image.getSourceAsBytes();
        pauseIfNotRenderingThread();
        if (Sage.DBG) System.out.println("Creating DX9 texture from compressed resource " + image.getSource());
        if (imageBytes != null)
          nativePtr = createD3DTextureFromMemory0(width, height, imageBytes);
        MetaImage.returnNativeCacheReserve(this, nativeMemUse);
        if (nativePtr != 0)
        {
          image.setNativePointer(this, imageIndex, nativePtr, nativeMemUse);
          if (D3D_DEBUG) System.out.println("VRAM State free=" + getAvailableVideoMemory0() +
              " cache=" + MetaImage.getNativeImageCacheSize(this) + " rtMemUse=" + rtMemUse);
          return true;
        }
      }
      finally
      {
        decDevRefCount();
      }

      // If we fail loading it compressed then continue on to load it through a Java image
    }

    sage.media.image.RawImage tightImage = null;
    long nativePtr = 0;
    if (!Sage.getBoolean("ui/disable_native_image_loader", false))
    {
      try
      {
        incDevRefCount();
        if (pD3DDevice == 0)
          return false;
        pauseIfNotRenderingThread();
        tightImage = image.getRawImage(imageIndex);
        int width = image.getWidth(imageIndex);
        int height = image.getHeight(imageIndex);
        int pow2W, pow2H;
        pow2W = pow2H = 1;
        while (pow2W < width)
          pow2W = pow2W << 1;
        while (pow2H < height)
          pow2H = pow2H << 1;
        int nativeMemUse = pow2W*pow2H*4;

        long availableLimit = 8000000;//Sage.getLong("ui/video_memory_available_minimum_limit", 2000000);
        MediaFile mf = vf.getCurrFile();
        // If there's a video file loaded then we can reduce the minimum limit
        if (vf.isMediaPlayerLoaded() && mf != null && mf.hasVideoContent())
          availableLimit = Math.max(0, availableLimit - 1500000);
        synchronized (MetaImage.getNiaCacheLock(this))
        {
          long newLimit = getAvailableVideoMemory0()*VRAM_USAGE_PERCENT/100;
          newLimit = Math.min(newLimit, Sage.getInt("ui/max_d3d_vram_usage", 120000000));
          if (newLimit > videoMemoryLimit)
          {
            videoMemoryLimit = newLimit;
            if (Sage.DBG) System.out.println("New video memory limit=" + videoMemoryLimit);
          }
          while (MetaImage.getNativeImageCacheSize(this) + rtMemUse + nativeMemUse > videoMemoryLimit ||
              getAvailableVideoMemory0() < availableLimit + nativeMemUse)
          {
            Object[] oldestImage = MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
            if (oldestImage == null)
            {
              if (Sage.DBG) System.out.println("DX9 is unable to find an image to release to add " + image.getSource() +
                  " cacheSize=" + MetaImage.getNativeImageCacheSize(this) + " rtMemUse=" + rtMemUse + " vramAvail=" + getAvailableVideoMemory0());
              // Don't return false, let DX9 try to do some memory management to clean
              // this up and give us some back.
              break;
            }
            if (Sage.DBG) System.out.println("DX9 is freeing texture memory to make room size=" + MetaImage.getNativeImageCacheSize(this) +
                " rtMemUse=" + rtMemUse + " availMem=" + getAvailableVideoMemory0() + " src=" + ((MetaImage) oldestImage[0]).getSource());
            ((MetaImage) oldestImage[0]).clearNativePointer(this, ((Integer) oldestImage[1]).intValue());
          }
          MetaImage.reserveNativeCache(this, nativeMemUse);
        }

        pauseIfNotRenderingThread();
        nativePtr = createD3DTextureFromMemory0(width, height, tightImage.getROData());
        MetaImage.returnNativeCacheReserve(this, nativeMemUse);
        if (nativePtr != 0)
        {
          image.setNativePointer(this, imageIndex, nativePtr, nativeMemUse);
          if (D3D_DEBUG) System.out.println("VRAM State free=" + getAvailableVideoMemory0() +
              " cache=" + MetaImage.getNativeImageCacheSize(this) + " rtMemUse=" + rtMemUse);
        }
      }
      finally
      {
        decDevRefCount();
        image.removeRawRef(imageIndex);
      }
    }
    else
    {
      // if we don't have this as a buffered image, we need to convert it
      java.awt.image.BufferedImage tempBuf;
      pauseIfNotRenderingThread();
      java.awt.Image javaImage = image.getJavaImage(imageIndex);
      try
      {
        incDevRefCount();
        if (pD3DDevice == 0)
          return false;
        // NOTE: On 8/11/2004 I made it so it only takes ARGB again. Otherwise my video snapshots wouldn't appear.
        if (!(javaImage instanceof java.awt.image.BufferedImage) ||
            (((java.awt.image.BufferedImage) javaImage).getType() != java.awt.image.BufferedImage.TYPE_INT_ARGB &&
            ((java.awt.image.BufferedImage) javaImage).getType() != java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE))
        {
          if (!(javaImage instanceof java.awt.image.BufferedImage))
            ImageUtils.ensureImageIsLoaded(javaImage);
          tempBuf = ImageUtils.createBestImage(javaImage);
        }
        else
        {
          tempBuf = (java.awt.image.BufferedImage)javaImage;
        }
        if (!tempBuf.isAlphaPremultiplied())
        {
          if (Sage.DBG) System.out.println("Premultiplying alpha for BuffImage...");
          pauseIfNotRenderingThread();
          tempBuf.coerceData(true);
        }
        int width = tempBuf.getWidth();
        int height = tempBuf.getHeight();
        int pow2W, pow2H;
        pow2W = pow2H = 1;
        while (pow2W < width)
          pow2W = pow2W << 1;
        while (pow2H < height)
          pow2H = pow2H << 1;
        int nativeMemUse = pow2W*pow2H*4;

        long availableLimit = 8000000;//Sage.getLong("ui/video_memory_available_minimum_limit", 2000000);
        MediaFile mf = vf.getCurrFile();
        // If there's a video file loaded then we can reduce the minimum limit
        if (vf.isMediaPlayerLoaded() && mf != null && mf.hasVideoContent())
          availableLimit = Math.max(0, availableLimit - 1500000);
        synchronized (MetaImage.getNiaCacheLock(this))
        {
          long newLimit = getAvailableVideoMemory0()*VRAM_USAGE_PERCENT/100;
          newLimit = Math.min(newLimit, Sage.getInt("ui/max_d3d_vram_usage", 120000000));
          if (newLimit > videoMemoryLimit)
          {
            videoMemoryLimit = newLimit;
            if (Sage.DBG) System.out.println("New video memory limit=" + videoMemoryLimit);
          }
          while (MetaImage.getNativeImageCacheSize(this) + rtMemUse + nativeMemUse > videoMemoryLimit ||
              getAvailableVideoMemory0() < availableLimit + nativeMemUse)
          {
            Object[] oldestImage = MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
            if (oldestImage == null)
            {
              if (Sage.DBG) System.out.println("DX9 is unable to find an image to release to add " + image.getSource() +
                  " cacheSize=" + MetaImage.getNativeImageCacheSize(this) + " rtMemUse=" + rtMemUse + " vramAvail=" + getAvailableVideoMemory0());
              // Don't return false, let DX9 try to do some memory management to clean
              // this up and give us some back.
              break;
            }
            if (Sage.DBG) System.out.println("DX9 is freeing texture memory to make room size=" + MetaImage.getNativeImageCacheSize(this) +
                " rtMemUse=" + rtMemUse + " availMem=" + getAvailableVideoMemory0() + " src=" + ((MetaImage) oldestImage[0]).getSource());
            ((MetaImage) oldestImage[0]).clearNativePointer(this, ((Integer) oldestImage[1]).intValue());
          }
          MetaImage.reserveNativeCache(this, nativeMemUse);
        }

        pauseIfNotRenderingThread();
        nativePtr = createD3DTextureFromMemory0(width, height,
            ((java.awt.image.DataBufferInt)tempBuf.getRaster().getDataBuffer()).getData());
        MetaImage.returnNativeCacheReserve(this, nativeMemUse);
        if (nativePtr != 0)
        {
          image.setNativePointer(this, imageIndex, nativePtr, nativeMemUse);
          if (D3D_DEBUG) System.out.println("VRAM State free=" + getAvailableVideoMemory0() +
              " cache=" + MetaImage.getNativeImageCacheSize(this) + " rtMemUse=" + rtMemUse);
        }
      }
      finally
      {
        decDevRefCount();
        image.removeJavaRef(imageIndex);
      }
    }
    return (nativePtr != 0);
  }

  public void releaseNativeImage(long nativePointer)
  {
    freeD3DTexturePointer0(nativePointer);
  }

  public void preloadImage(MetaImage mi)
  {
    if (mi.getWidth(0) > maxTextureDim ||
        mi.getHeight(0) > maxTextureDim)
    {
      float xScale = Math.min(maxTextureDim,
          mi.getWidth(0))/((float)mi.getWidth(0));
      float yScale = Math.min(maxTextureDim,
          mi.getHeight(0))/((float)mi.getHeight(0));
      xScale = yScale = Math.min(xScale, yScale);
      int newIdex = mi.getImageIndex(Math.round(xScale *
          mi.getWidth(0)), Math.round(yScale * mi.getHeight(0)));
      try
      {
        incDevRefCount();
        try
        {
          mi.getNativeImage(this, newIdex);
        }
        finally
        {
          mi.removeNativeRef(this, newIdex);
        }
      }
      finally
      {
        decDevRefCount();
      }
    }
    else
    {
      try
      {
        incDevRefCount();
        try
        {
          mi.getNativeImage(this, 0);
        }
        finally
        {
          mi.removeNativeRef(this, 0);
        }
      }
      finally
      {
        decDevRefCount();
      }
    }
  }

  public java.awt.image.BufferedImage getVideoSnapshot()
  {
    if (videoSurface == 0)
      return null;
    java.awt.image.BufferedImage snapshot = new java.awt.image.BufferedImage(videoWidth, videoHeight,
        java.awt.image.BufferedImage.TYPE_INT_RGB);
    if (getVideoSnapshot0(videoSurface, videoWidth, videoHeight,
        ((java.awt.image.DataBufferInt)snapshot.getRaster().getDataBuffer()).getData()))
      return snapshot;
    else
      return null;
  }

  public boolean supportsPartialUIUpdates()
  {
    return false;
  }

  public boolean isFSELoaded()
  {
    return fullScreenExMode;
  }

  // Applies the clipping rectangle to the values in src/destX/Y/W/Hcache arrays at the specified index.
  // Returns true if there's actually something to render after the clip has been applied
  private boolean clipSrcDestCache(int rectCoordIndex, java.awt.geom.Rectangle2D.Float clipRect)
  {
    float scaleX = dstWCache[rectCoordIndex] / srcWCache[rectCoordIndex];
    float scaleY = dstHCache[rectCoordIndex] / srcHCache[rectCoordIndex];
    if (dstXCache[rectCoordIndex] < clipRect.x)
    {
      if (dstXCache[rectCoordIndex] + dstWCache[rectCoordIndex] <= clipRect.x) return false;
      float xDiff = clipRect.x - dstXCache[rectCoordIndex];
      dstWCache[rectCoordIndex] -= xDiff;
      srcXCache[rectCoordIndex] += xDiff/scaleX;
      srcWCache[rectCoordIndex] -= xDiff/scaleX;
      dstXCache[rectCoordIndex] = clipRect.x;
    }
    if (dstYCache[rectCoordIndex] < clipRect.y)
    {
      if (dstYCache[rectCoordIndex] + dstHCache[rectCoordIndex] <= clipRect.y) return false;
      float yDiff = clipRect.y - dstYCache[rectCoordIndex];
      dstHCache[rectCoordIndex] -= yDiff;
      srcYCache[rectCoordIndex] += yDiff/scaleY;
      srcHCache[rectCoordIndex] -= yDiff/scaleY;
      dstYCache[rectCoordIndex] = clipRect.y;
    }
    if (dstXCache[rectCoordIndex] + dstWCache[rectCoordIndex] > clipRect.x + clipRect.width)
    {
      if (dstXCache[rectCoordIndex] >= clipRect.x + clipRect.width) return false;
      float over = (dstXCache[rectCoordIndex] + dstWCache[rectCoordIndex]) - (clipRect.x + clipRect.width);
      dstWCache[rectCoordIndex] -= over;
      srcWCache[rectCoordIndex] -= over/scaleX;
    }
    if (dstYCache[rectCoordIndex] + dstHCache[rectCoordIndex] > clipRect.y + clipRect.height)
    {
      if (dstYCache[rectCoordIndex] >= clipRect.y + clipRect.height) return false;
      float over = (dstYCache[rectCoordIndex] + dstHCache[rectCoordIndex]) - (clipRect.y + clipRect.height);
      dstHCache[rectCoordIndex] -= over;
      srcHCache[rectCoordIndex] -= over/scaleY;
    }
    return true;
  }

  private void incDevRefCount()
  {
    synchronized (refCountLock)
    {
      if (cleanupLock)
      {
        while (cleanupLock && master.isAlive())
        {
          try{refCountLock.wait(100);}catch(Exception e){}
        }
      }
      nativeDeviceRefs++;
    }
  }

  private void decDevRefCount()
  {
    synchronized (refCountLock)
    {
      nativeDeviceRefs--;
      refCountLock.notifyAll();
    }
  }

  public void checkForDisplayChange(int newWidth, int newHeight)
  {
    if (Sage.DBG) System.out.println("DX9 renderer checking new display size of " + newWidth + "x" + newHeight + " against buffer of " + bufferWidth + "x" + bufferHeight);
    boolean redoLayout = false;
    synchronized (displayChangeLock)
    {
      lastNotifiedDisplayWidth = newWidth;
      lastNotifiedDisplayHeight = newHeight;
    }
    if (newWidth != bufferWidth || newHeight != bufferHeight)
    {
      redoLayout = true;
    }
    else if (master.getWidth() != bufferWidth || master.getHeight() != bufferHeight)
      redoLayout = true;

    if (redoLayout)
    {
      if (Sage.DBG) System.out.println("DX9 device was notified of display change and it doesn't match our current resolution...redo the window layout");
      fullUIRefresh();
    }
  }

  private void fullUIRefresh()
  {
    master.invalidate();
    master.getParent().invalidate();
    java.awt.EventQueue.invokeLater(new Runnable()
    {
      public void run()
      {
        javax.swing.SwingUtilities.getAncestorOfClass(java.awt.Frame.class, master.getParent()).validate();
      }
    });
    uiMgr.trueValidate();
  }

  static long getD3DObjectPtr()
  { return (defaultDX9Renderer == null) ? 0 : defaultDX9Renderer.pD3DObject; }
  static long getD3DDevicePtr()
  { return (defaultDX9Renderer == null) ? 0 : defaultDX9Renderer.pD3DDevice; }
  static long getD3DDeviceMgr()
  { return (defaultDX9Renderer == null) ? 0 : defaultDX9Renderer.pD3DDevMgr; }

  // SYNCHRONIZE ALL CALLS TO THE NATIVE LAYER BECAUSE WE'RE USING THE SAME NATIVE OBJECTS
  private synchronized native boolean initDX9SageRenderer0(int width, int height, long hwnd);

  private synchronized native void cleanupDX9SageRenderer0();

  private native long createD3DTextureFromMemory0(int width, int height, int[] data);

  private native long createD3DTextureFromMemory0(int width, int height, byte[] data);

  private native long createD3DTextureFromMemory0(int width, int height, java.nio.ByteBuffer data);

  private native void freeD3DTexturePointer0(long nativePtr);

  private synchronized native boolean present0(int clipX, int clipY, int clipWidth, int clipHeight);

  private synchronized native void clearScene0(boolean fullClear);
  private synchronized native boolean beginScene0(int viewportWidth, int viewportHeight);
  private synchronized native void endScene0();

  private synchronized native boolean stretchBlt0(long srcSurface, int srcRectX, int srcRectY, int srcRectW, int srcRectH,
      long destTexture, int destRectX, int destRectY, int destRectW, int destRectH, int scaleHint);
  private synchronized native boolean textureMap0(long srcTexture, float srcRectX, float srcRectY, float srcRectW, float srcRectH,
      float destRectX, float destRectY, float destRectW, float destRectH, int scaleHint, int compositing,
      int textureColor, boolean spritesOK);
  private synchronized native boolean textureMultiMap0(long srcTexture, float[] srcRectX, float[] srcRectY,
      float[] srcRectW, float[] srcRectH,	float[] destRectX, float[] destRectY, float[] destRectW,
      float[] destRectH, int scaleHint, int compositing, int[] textureColor, int numRects);
  private synchronized native boolean textureDiffuseMap0(long srcTexture, long diffuseTexture, float srcRectX, float srcRectY,
      float srcRectW, float srcRectH, float diffuseSrcX, float diffuseSrcY, float diffuseSrcW, float diffuseSrcH,
      float destRectX, float destRectY, float destRectW, float destRectH, int scaleHint, int compositing, int textureColor, double[] matrixCoords);
  private synchronized native boolean textureMultiMap0(long srcTexture, float[] srcRectX, float[] srcRectY,
      float[] srcRectW, float[] srcRectH,	float[] destRectX, float[] destRectY, float[] destRectW,
      float[] destRectH, int scaleHint, int compositing, int[] textureColor, int numRects, double[] matrixCoords);
  private synchronized native boolean fillShape0(float[] xcoords, float[] ycoords, int[] colors, int triangleStrip,
      int numVertices, java.awt.Rectangle clipRect);
  private synchronized native boolean fillShape0(float[] xcoords, float[] ycoords, int[] colors, int triangleStrip,
      int numVertices, java.awt.Rectangle clipRect, double[] matrixCoords);
  private synchronized native boolean getVideoSnapshot0(long videoSurface, int videoWidth, int videoHeight,
      int[] snapshotData);
  private synchronized native long create3DFont0(String fontFact, int fontSize, boolean bold, boolean italic);
  private synchronized native void free3DFont0(long fontPtr);
  private synchronized native boolean renderText0(String text, long fontPtr,
      float rectX, float rectY, float rectW, float rectH, int color);
  private native long getAvailableVideoMemory0();
  private native int getMaximumTextureDimension0();
  private static native boolean hasDirectX90();

  private synchronized native long createD3DRenderTarget0(int width, int height);
  // Use 0 for the back buffer
  private synchronized native boolean setRenderTarget0(long targetPtr);

  protected void asyncVideoRender(String shMemPrefix)
  {
    synchronized (asyncMplayerRenderLock)
    {
      asyncMplayerRenderCount++;
    }
    asyncVideoRender0(shMemPrefix);
    synchronized (asyncMplayerRenderLock)
    {
      asyncMplayerRenderCount--;
    }
  }
  protected native void asyncVideoRender0(String shMemPrefix);

  private long pD3DObject;
  private long pD3DDevice;
  private long pD3DRenderTarget;
  private int bufferWidth;
  private int bufferHeight;
  private long pD3DDevMgr;
  private long pD3DDevMgrToken;
  private long hD3DMgrHandle;

  private long videoSurface;
  private int videoWidth;
  private int videoHeight;
  private long videoFrameTime;
  private int videoARx;
  private int videoARy;
  private int lastVideoARx;
  private int lastVideoARy;
  private int lastVideoWidth;
  private int lastVideoHeight;
  private int lastAssMode;

  private long altVideoSurface;
  private int altVideoWidth;
  private int altVideoHeight;
  private long altVideoFrameTime;
  private int altVideoARx;
  private int altVideoARy;

  private PseudoMenu lastUI;

  // DEBUGGING
  private boolean rerenderedDL;
  private java.util.ArrayList lastDL;

  private java.awt.Dimension lastMasterSize;

  private float[] srcXCache;
  private float[] srcYCache;
  private float[] srcWCache;
  private float[] srcHCache;
  private float[] dstXCache;
  private float[] dstYCache;
  private float[] dstWCache;
  private float[] dstHCache;
  private int[] cCache;

  private boolean beganScene;

  private VideoFrame vf;
  private java.awt.geom.Rectangle2D.Float lastVideoRect;

  private boolean almostInVmr9Callback;
  private boolean inVmr9Callback;

  private java.util.Map fontCacheMap = new java.util.HashMap();

  private boolean wasInFullScreen = false;
  private boolean fullScreenExMode = false;

  private boolean deviceLost = false;

  private boolean stopVmr9Callback;

  private long videoMemoryLimit;

  private int asyncMplayerRenderCount = 0;
  private Object asyncMplayerRenderLock = new Object();

  private Object videoSurfaceLock = new Object();

  // Cache these so we don't reallocate them every video frame
  private java.awt.geom.Rectangle2D.Float videoSrc = new java.awt.geom.Rectangle2D.Float();
  private java.awt.geom.Rectangle2D.Float videoDest = new java.awt.geom.Rectangle2D.Float();
  private java.awt.geom.Rectangle2D.Float clipArea = new java.awt.geom.Rectangle2D.Float();
  private java.awt.Rectangle srcVideoRect = new java.awt.Rectangle();
  private java.awt.Rectangle usedVideoRect = new java.awt.Rectangle();
  private java.awt.Rectangle fullVideoRect = new java.awt.Rectangle();
  private double[] currMatCoords = new double[6];

  private int maxTextureDim = 1024;
  private java.util.Stack rtStack = new java.util.Stack();
  private long rtMemUse;
  private long currRT;
  private java.util.Map animsThatGoAfterSurfPop = new java.util.HashMap();
  private long backSurf;
  private boolean effectAnimationsActive = false;
  private boolean effectAnimationsWereActive;
  private boolean effectAnimationsLocked = false;
  private boolean hadVirginEffects = false;
  private long renderStartTime;
  private java.util.Stack effectStack = new java.util.Stack();
  private javax.vecmath.Matrix4f tempMat = new javax.vecmath.Matrix4f();
  private javax.vecmath.Matrix4f currMat = new javax.vecmath.Matrix4f();
  private javax.vecmath.Matrix4f projMat;
  private java.awt.geom.Rectangle2D.Float shapeClipRect = new java.awt.geom.Rectangle2D.Float();

  private boolean[] glyphDrawnTracker;

  private long lastPixelRenderCount;
  private long lastPixelInputCount;
  private javax.vecmath.Matrix4f currEffectMat;
  private float currEffectAlpha;
  private java.awt.geom.Rectangle2D.Float currEffectClip;
  private double[] currCoords = new double[16];
  private boolean lastHadGraphics;

  // This is used to track multiple threads interacting with the Direct3D device so we can synchronize creation/destruction of it properly
  private Object refCountLock = new Object();
  private int nativeDeviceRefs;
  private boolean cleanupLock;

  // This is to workaround a bug in the ATI 4xxx series cards where changing the render target while using EVR w/ SD video
  // causes problems
  private boolean disableUIBackingSurface;

  // For tracking changes which should force a device lost state (it happens automatially, except when Aero is enabled)
  private long currHWND;
  private Object displayChangeLock = new Object();
  private int lastNotifiedDisplayWidth;
  private int lastNotifiedDisplayHeight;

  private boolean usingFreshDL;
}
