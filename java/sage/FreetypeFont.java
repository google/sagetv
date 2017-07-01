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

import sage.io.BufferedSageFile;
import sage.io.LocalSageFile;
import sage.io.SageDataFile;

public class FreetypeFont extends MetaFont
{
  // Freetype is NOT designed for multi-threading so we need to ensure not more than a single thread goes into the native freetype code at one time
  private static final Object ftLock = new Object();
  private static long ftLibPtr;
  private static java.util.Map faceCacheMap = java.util.Collections.synchronizedMap(new java.util.HashMap());
  private static void ensureLibLoaded()
  {
    if (ftLibPtr == 0)
    {
      synchronized (ftLock)
      {
        if (ftLibPtr != 0) return;
        sage.Native.loadLibrary("FreetypeFontJNI");
        ftLibPtr = loadFreetypeLib0();
        if (ftLibPtr == 0)
        {
          throw new RuntimeException("Can't load freetype lib!");
        }
      }
    }
  }

  public FreetypeFont(String fontName, int fontStyle, int fontSize) throws java.io.FileNotFoundException
  {
    super(fontName, fontStyle, fontSize);
    ensureLibLoaded();
    if (new java.io.File(fontName).isFile())
      this.fontPath = fontName;
    else if (fontStyle == PLAIN)
      this.fontPath = Sage.getPath("fonts") + fontName + ".ttf";
    else if (fontStyle == ITALIC)
    {
      this.fontPath = Sage.getPath("fonts") + fontName + "Italic.ttf";
      if (!new java.io.File(fontPath).isFile())
        this.fontPath = Sage.getPath("fonts") + fontName + "I.ttf";
      fontStyle = 0;
    }
    else if (fontStyle == BOLD)
    {
      this.fontPath = Sage.getPath("fonts") + fontName + "Bold.ttf";
      if (!new java.io.File(fontPath).isFile())
      {
        this.fontPath = Sage.getPath("fonts") + fontName + "bd.ttf";
        if (!new java.io.File(fontPath).isFile())
          this.fontPath = Sage.getPath("fonts") + fontName + "B.ttf";
      }
      fontStyle = 0;
    }
    else // BOLD + ITALIC
    {
      this.fontPath = Sage.getPath("fonts") + fontName + "BoldItalic.ttf";
      if (!new java.io.File(fontPath).isFile())
        this.fontPath = Sage.getPath("fonts") + fontName + "BI.ttf";
      fontStyle = 0;
    }
    synchronized (ftLock)
    {
      if (faceCacheMap.containsKey(fontPath))
      {
        if (Sage.DBG) System.out.println("Deriving FreeType font face for " + fontName + " size=" + fontSize + " style=" + fontStyle + " from=" + fontPath);
        parentFont = (FreetypeFont) faceCacheMap.get(fontPath);
        fontFacePtr = deriveFontFace0(parentFont.fontFacePtr, size, fontStyle);
        if (fontFacePtr == 0)
        {
          throw new RuntimeException("Can't derive freetype font name=" + fontName + " style=" + fontStyle + " size=" + fontSize +
              " path=" + fontPath + " err=" + ftErr);
        }
      }
      else
      {
        if (!new java.io.File(fontPath).isFile())
        {
          throw new java.io.FileNotFoundException();
        }
        if (Sage.DBG) System.out.println("Creating new FreeType font face for " + fontName + " size=" + fontSize + " style=" + fontStyle + " from=" + fontPath);
        fontFacePtr = loadFontFace0(ftLibPtr, fontPath, size, fontStyle);
        if (fontFacePtr == 0)
        {
          throw new RuntimeException("Can't load freetype font name=" + fontName + " style=" + fontStyle + " size=" + fontSize +
              " path=" + fontPath + " err=" + ftErr);
        }
        faceCacheMap.put(fontPath, this);
        parentFont = this;
      }
    }
    height = getFontHeight0(fontFacePtr) >> 6;
    descent = -1 * (getFontDescent0(fontFacePtr) >> 6);
    ascent = getFontAscent0(fontFacePtr) >> 6;
    leading = height - ascent - descent;

    // Ensure our sizing information is fully loaded...this corrects issues with incorrect font heights
    SageRenderer.getAcceleratedFont(this);
  }
  public MetaFont deriveFontSize(int newSize, UIManager uiLoader)
  {
    return UIManager.getCachedMetaFont(name, style, newSize, uiLoader);
  }
  public MetaFont deriveFont(int newStyle, UIManager uiLoader)
  {
    return UIManager.getCachedMetaFont(name, newStyle, size, uiLoader);
  }

  public int stringWidth(String str)
  {
    int rv = 0;
    for (int i = 0; i < str.length(); i++)
    {
      int glyphCode = getGlyphForChar(str.charAt(i));
      int gwidth = getGlyphAdvance(glyphCode);
      rv += gwidth;
    }
    return rv;
  }

  // UGLY---FINALIZE!!!! BAD FOR PERFORMANCE
  protected void finalize()
  {
    releaseNativeFont();
  }

  // Override to optimize
  public /*EMBEDDED_SWITCH*/float/*/int/**/ charWidth(char c)
  {
    return getGlyphAdvance(getGlyphForChar(c));
  }

  public int getNumGlyphs()
  {
    return getNumGlyphs0(fontFacePtr);
  }
  public int getGlyphForChar(char c)
  {
    return getGlyphForChar0(fontFacePtr, c);
  }
  public int renderGlyph(int glyphCode, java.awt.image.BufferedImage bi, int x, int y)
  {
    synchronized (ftLock)
    {
      loadGlyph(glyphCode);
      return renderGlyph0(fontFacePtr, bi, x, y);
    }
  }
  public int getGlyphAdvance(int glyphCode)
  {
    if (accelerator != null)
    {
      java.awt.geom.Rectangle2D.Float grect = accelerator.getLogicalRect(glyphCode);
      if (grect != null)
        return (int)grect.width;
    }
    synchronized (ftLock)
    {
      loadGlyph(glyphCode);
      return getGlyphAdvance0(fontFacePtr) >> 6;
    }
  }
  public int getGlyphPixWidth(int glyphCode)
  {
    if (accelerator != null)
    {
      java.awt.geom.Rectangle2D.Float grect = accelerator.getPixelRect(glyphCode);
      if (grect != null)
        return (int)grect.width;
    }
    synchronized (ftLock)
    {
      loadGlyph(glyphCode);
      return getGlyphWidth0(fontFacePtr) >> 6;
    }
  }
  public int getGlyphHeight(int glyphCode)
  {
    if (accelerator != null)
    {
      java.awt.geom.Rectangle2D.Float grect = accelerator.getPixelRect(glyphCode);
      if (grect != null)
        return (int)grect.height;
    }
    synchronized (ftLock)
    {
      loadGlyph(glyphCode);
      return getGlyphHeight0(fontFacePtr) >> 6;
    }
  }
  private void loadGlyph(int glyphCode)
  {
    if (currLoadedGlyph != glyphCode)
    {
      currLoadedGlyph = glyphCode;
      loadGlyph0(fontFacePtr, glyphCode);
    }
  }
  public MetaFont.GlyphVector createGlyphVector(String str)
  {
    int advance = 0;
    int visAdvance = 0;
    int[] glyphCodes = new int[str.length()];
    float[] glyphPos = new float[glyphCodes.length];
    java.awt.geom.Rectangle2D.Float bounder = new java.awt.geom.Rectangle2D.Float();
    int trailingWS = 0;
    int strlen = str.length();
    for (int i = 0; i < strlen; i++)
    {
      char c = str.charAt(i);
      glyphCodes[i] = getGlyphForChar(c);
      java.awt.geom.Rectangle2D.Float pixRect = accelerator != null ? accelerator.getPixelRect(glyphCodes[i]) : null;
      java.awt.geom.Rectangle2D.Float logRect = accelerator != null ? accelerator.getLogicalRect(glyphCodes[i]) : null;
      int gwidth = getGlyphAdvance(glyphCodes[i]);
      int gheight = getGlyphHeight(glyphCodes[i]);
      if (pixRect != null && logRect != null)
        gheight += pixRect.y - logRect.y + getAscent();
      glyphPos[i] = advance;
      advance += gwidth;
      if (c == ' ')
      {
        // If we're whitespace then add us to the whitespace list
        trailingWS += gwidth;
      }
      else
      {
        // We're a char; so if there was trailing whitespace we should add it in
        visAdvance += trailingWS + gwidth;
        trailingWS = 0;
      }
      bounder.height = Math.max(bounder.height, gheight);
    }
    bounder.width = advance;
    return new FreetypeGlyphVector(advance, visAdvance, str, glyphCodes, glyphPos, bounder);
  }

  public MetaFont.GlyphVector[] createGlyphVectors(String s, int wrapWidth)
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    int advance = 0;
    int visAdvance = 0;
    int[] glyphCodes = new int[s.length()];
    float[] glyphPos = new float[glyphCodes.length];
    java.awt.geom.Rectangle2D.Float bounder = new java.awt.geom.Rectangle2D.Float();
    int lastStart = 0;
    int trailingWS = 0;
    int lastWrapIndex = 0;
    int wsWidth = 0;
    int strlen = s.length();
    for (int i = 0; i < strlen; i++)
    {
      char c = s.charAt(i);
      glyphCodes[i] = getGlyphForChar(c);
      int gwidth = getGlyphAdvance(glyphCodes[i]);
      java.awt.geom.Rectangle2D.Float pixRect = accelerator != null ? accelerator.getPixelRect(glyphCodes[i]) : null;
      java.awt.geom.Rectangle2D.Float logRect = accelerator != null ? accelerator.getLogicalRect(glyphCodes[i]) : null;
      int gheight = getGlyphHeight(glyphCodes[i]);
      if (pixRect != null && logRect != null)
        gheight += pixRect.y - logRect.y + getAscent();
      if (lastStart < i && gwidth + advance > wrapWidth)
      {
        // There is another case where whitespace will be the first char on the next line. We want
        // to avoid that and put it at the end of the prior line. So we need to consume that char in this case.
        boolean consumedChar = false;
        if (c == ' ')
        {
          // Consume this char now
          trailingWS += gwidth;
          glyphPos[i] = advance;
          advance += gwidth;
          bounder.height = Math.max(bounder.height, gheight);
          bounder.width = advance;
          consumedChar = true;
          lastWrapIndex = 0;
        }

        // Wrap onto another line. Find the best point for this.
        if (lastWrapIndex > 0 && lastWrapIndex < i - 1)
        {
          // soft line wrapping
          // This line we're adding goes from lastStart until lastWrapIndex(inclusive).
          // Then we have to take the chars after lastWrapIndex and put them onto the 'new' current line
          // Move those chars onto the next line first and figure out how much of the advance to subtract in the process
          int xoff = (int)(advance - glyphPos[lastWrapIndex + 1]);
          int xshift = (int)glyphPos[lastWrapIndex + 1];
          for (int j = lastWrapIndex + 1; j < i; j++)
          {
            glyphPos[j] -= xshift;
          }
          int[] newgc = new int[lastWrapIndex + 1 - lastStart];
          System.arraycopy(glyphCodes, lastStart, newgc, 0, newgc.length);
          float[] newgp = new float[newgc.length];
          System.arraycopy(glyphPos, lastStart, newgp, 0, newgp.length);
          rv.add(new FreetypeGlyphVector(xshift, xshift - wsWidth, s.substring(lastStart, lastWrapIndex + 1), newgc, newgp,
              new java.awt.geom.Rectangle2D.Float(bounder.x, bounder.y, advance, bounder.height)));
          bounder.y += height;
          bounder.height = 0;
          advance = visAdvance = xoff/* - wsWidth*/;
          lastStart = lastWrapIndex + 1;
          trailingWS = 0; // we wrapped so there's nothing trailing anymore
        }
        else
        {
          // hard line wrapping
          int theEnd = consumedChar ? (i + 1) : i;
          int[] newgc = new int[theEnd - lastStart];
          System.arraycopy(glyphCodes, lastStart, newgc, 0, newgc.length);
          float[] newgp = new float[newgc.length];
          System.arraycopy(glyphPos, lastStart, newgp, 0, newgp.length);
          rv.add(new FreetypeGlyphVector(advance, visAdvance, s.substring(lastStart, theEnd), newgc, newgp,
              new java.awt.geom.Rectangle2D.Float(bounder.x, bounder.y, advance, bounder.height)));
          bounder.y += height;
          bounder.height = 0;
          advance = visAdvance = 0;
          lastStart = theEnd;
          trailingWS = 0; // we wrapped so there's nothing trailing anymore
        }
        lastWrapIndex = 0;
        wsWidth = 0;
        if (consumedChar)
          continue;
      }
      if (c == ' ')
      {
        // We found whitespace. Add this to the trailingWS count
        trailingWS += gwidth;
        // Set this point as the last wrap
        lastWrapIndex = i;
        wsWidth = gwidth;
      }
      else
      {
        // We're a char; so if there was trailing whitespace we should add it in
        visAdvance += trailingWS + gwidth;
        trailingWS = 0;
        if (c == '-')
        {
          // Set this point as the last wrap
          lastWrapIndex = i;
          wsWidth = 0;
        }
      }
      glyphPos[i] = advance;
      advance += gwidth;
      bounder.height = Math.max(bounder.height, gheight);
      bounder.width = advance;
    }
    int[] newgc = new int[s.length() - lastStart];
    System.arraycopy(glyphCodes, lastStart, newgc, 0, newgc.length);
    float[] newgp = new float[newgc.length];
    System.arraycopy(glyphPos, lastStart, newgp, 0, newgp.length);
    rv.add(new FreetypeGlyphVector(advance, visAdvance, s.substring(lastStart), newgc, newgp,
        new java.awt.geom.Rectangle2D.Float(bounder.x, bounder.y, advance, bounder.height)));
    return (MetaFont.GlyphVector[]) rv.toArray(new MetaFont.GlyphVector[0]);
  }

  private static native long loadFreetypeLib0();
  private static native boolean closeFreetypeLib0(long libPtr);
  private native boolean closeFontFace0(long facePtr);
  private native long loadFontFace0(long libPtr, String fontPath, int ptSize, int style);
  private native long deriveFontFace0(long parentFacePtr, int ptSize, int style);
  private native int getGlyphForChar0(long facePtr, char c);
  private native void loadGlyph0(long facePtr, int glyphCode);
  private native int renderGlyph0(long facePtr, java.awt.image.BufferedImage bi,
      int x, int y);
  private native sage.media.image.RawImage renderGlyphRaw0(long facePtr, sage.media.image.RawImage img, int imgWidth, int imgHeight, int x, int y);
  private native int getNumGlyphs0(long facePtr);
  private native int getGlyphWidth0(long facePtr);
  private native int getGlyphHeight0(long facePtr);
  private native int getGlyphBearingX0(long facePtr);
  private native int getGlyphBearingY0(long facePtr);
  private native int getGlyphAdvance0(long facePtr);
  private native int getFontHeight0(long facePtr);
  private native int getFontAscent0(long facePtr);
  private native int getFontDescent0(long facePtr);

  public /*EMBEDDED_SWITCH*/float/*/int/**/ getHeight()
  {
    return height;
  }

  public /*EMBEDDED_SWITCH*/float/*/int/**/ getAscent()
  {
    return ascent;
  }

  public /*EMBEDDED_SWITCH*/float/*/int/**/ getDescent()
  {
    return descent;
  }

  public /*EMBEDDED_SWITCH*/float/*/int/**/ getLeading()
  {
    return leading;
  }

  public void releaseNativeFont()
  {
    if (fontFacePtr != 0)
      closeFontFace0(fontFacePtr);
    fontFacePtr = 0;
    //closeFreetypeLib(ftLibPtr);
  }

  public long getNativeFontHandle()
  {
    return fontFacePtr;
  }

  public SageRenderer.CachedFontGlyphs loadAcceleratedFont(int maxRequiredGlyphCode, int width, int height)
  {
    int x = 0;
    int y = 0;
    int maxHeightForRow = 0;
    int maxWidthForGlyph = 0;
    boolean fixedGlyphCacheWidth = !Sage.getBoolean("ui/load_complete_glyph_maps", false);
    if (fixedGlyphCacheWidth)
    {
      synchronized (ftLock)
      {
        loadGlyph(getGlyphForChar('W'));
        maxWidthForGlyph = (getGlyphAdvance0(fontFacePtr) >> 6) + 4;
        loadGlyph(getGlyphForChar('\u5355'));
        maxWidthForGlyph = Math.max(maxWidthForGlyph, (getGlyphAdvance0(fontFacePtr) >> 6) + 4);
      }
    }
    int orgMaxRequiredGlyphCode = maxRequiredGlyphCode;
    maxRequiredGlyphCode = Integer.MAX_VALUE;
    SageRenderer.CachedFontGlyphs rv = new SageRenderer.CachedFontGlyphs();
    rv.font = this;
    rv.width = width;
    rv.height = height;
    int imageCount = 0;
    int numCachedGlyphs = 0;
    int numGlyphs = getNumGlyphs();
    rv.numGlyphs = numGlyphs;
    rv.imageIndexByGlyphCode = new int[numGlyphs];
    java.util.Arrays.fill(rv.imageIndexByGlyphCode, -1);
    rv.pixelRectByGlyphCode = new java.awt.geom.Rectangle2D.Float[numGlyphs];
    rv.logicalRectByGlyphCode = new java.awt.geom.Rectangle2D.Float[numGlyphs];
    int i = 0;
    // See if the cache file exists :
    java.io.File cacheFile;
    cacheFile = new java.io.File(System.getProperty("user.dir"), "fontcache" + java.io.File.separator + width+"x"+height+"_"+hashCode()+"_size_"+size+"_style_"+style);
    if (cacheFile.isFile())
    {
      // Verify the number of glyph
      SageDataFile cacheIn = null;
      try
      {
        cacheIn = new SageDataFile(new BufferedSageFile(new LocalSageFile(cacheFile, true)), Sage.I18N_CHARSET);
        if(numGlyphs==cacheIn.readInt())
        {
          imageCount=cacheIn.readInt()-1;
          rv.glyphCounts = new int[imageCount+1];
          rv.glyphCounts[imageCount]=numGlyphs;
          for (int j=0; j < numGlyphs; j++)
          {
            rv.imageIndexByGlyphCode[j]=cacheIn.readInt();
            rv.pixelRectByGlyphCode[j] = new java.awt.geom.Rectangle2D.Float();
            rv.pixelRectByGlyphCode[j].x=cacheIn.readFloat();
            rv.pixelRectByGlyphCode[j].y=cacheIn.readFloat();
            rv.pixelRectByGlyphCode[j].width=cacheIn.readFloat();
            rv.pixelRectByGlyphCode[j].height=cacheIn.readFloat();
            rv.logicalRectByGlyphCode[j] = new java.awt.geom.Rectangle2D.Float();
            rv.logicalRectByGlyphCode[j].x=cacheIn.readFloat();
            rv.logicalRectByGlyphCode[j].y=cacheIn.readFloat();
            rv.logicalRectByGlyphCode[j].width=cacheIn.readFloat();
            rv.logicalRectByGlyphCode[j].height=cacheIn.readFloat();
          }
          for(int j=0; j < imageCount+1; j++)
          {
            rv.glyphCounts[j]=cacheIn.readInt();
          }
          accelerator = rv;
          return rv;
        }
      }
      catch (Exception e)
      {
        System.out.println("Error reading font cache : " + e);
        e.printStackTrace(System.out);
      }
      finally
      {
        if (cacheIn != null)
        {
          try{cacheIn.close();}catch(Exception e1){}
        }
      }
    }
    int[] tmpGlyphCounts = new int[1024]; // way more then we'd ever have
    synchronized (ftLock)
    {
      for (; i < numGlyphs; i++)
      {
        int glyphPixWidth;
        int glyphHeight;
        int glyphAdvance;
        int glyphBearingX;
        int glyphBearingY;
        if (fixedGlyphCacheWidth && i > orgMaxRequiredGlyphCode)
        {
          glyphPixWidth = glyphAdvance = maxWidthForGlyph;
          glyphBearingX = -2;
          glyphBearingY = (int)getAscent();
          glyphHeight = (int)getHeight();
        }
        else
        {
          loadGlyph(i);
          glyphPixWidth = getGlyphWidth0(fontFacePtr) >> 6;
          glyphHeight = getGlyphHeight0(fontFacePtr) >> 6;
          glyphAdvance = getGlyphAdvance0(fontFacePtr) >> 6;
          glyphBearingX = getGlyphBearingX0(fontFacePtr) >> 6;
          glyphBearingY = getGlyphBearingY0(fontFacePtr) >> 6;
        }
        if (x + glyphPixWidth >= width)
        {
          // Move us on to the next line
          x = 0;
          y += maxHeightForRow + 1;
          maxHeightForRow = 0;
        }
        if (y + glyphHeight >= height)
        {
          // Move on to the next image
          if (i > maxRequiredGlyphCode)
            break;
          tmpGlyphCounts[imageCount] = i;
          imageCount++;
          if (tmpGlyphCounts.length <= imageCount)
          {
            int[] newTemp = new int[tmpGlyphCounts.length * 2];
            System.arraycopy(tmpGlyphCounts, 0, newTemp, 0, tmpGlyphCounts.length);
            tmpGlyphCounts = newTemp;
          }
          x = 0;
          y = 0;
          maxHeightForRow = 0;
        }
        x -= glyphBearingX; // skip over the blank space on the left of the glyph, or if it goes over the left then move us right
        numCachedGlyphs = i;
        //renderGlyph(i, currImage, x, y);
        rv.imageIndexByGlyphCode[i] = imageCount;
        rv.pixelRectByGlyphCode[i] =  new java.awt.geom.Rectangle2D.Float(x + glyphBearingX, y,
            glyphPixWidth, glyphHeight);
        rv.logicalRectByGlyphCode[i] = new java.awt.geom.Rectangle2D.Float(x, y + glyphBearingY,
            glyphAdvance, glyphHeight);
        // Adjust font height for any glyphs that are larger than it
        this.height = Math.max(this.height, (int)Math.ceil(glyphHeight - glyphBearingY + getAscent()));
        maxHeightForRow = Math.max(maxHeightForRow, glyphHeight);
        x += glyphPixWidth + glyphBearingX + 1;
      }
      if (Sage.DBG) System.out.println("There are "+numGlyphs+" glyphs");
    }
    tmpGlyphCounts[imageCount] = i;
    rv.glyphCounts = new int[imageCount + 1];
    System.arraycopy(tmpGlyphCounts, 0, rv.glyphCounts, 0, imageCount + 1);
    {
      if (Sage.DBG) System.out.println("Saving cache version");
      cacheFile.getParentFile().mkdirs();
      SageDataFile cacheOut = null;
      try
      {
        // assumes font name is valid file string
        cacheOut = new SageDataFile(new BufferedSageFile(new LocalSageFile(cacheFile, false)), Sage.I18N_CHARSET);
        cacheOut.writeInt(numGlyphs);
        cacheOut.writeInt(imageCount+1);
        int j;
        for (j=0; j < numGlyphs; j++)
        {
          cacheOut.writeInt(rv.imageIndexByGlyphCode[j]);
          cacheOut.writeFloat(rv.pixelRectByGlyphCode[j].x);
          cacheOut.writeFloat(rv.pixelRectByGlyphCode[j].y);
          cacheOut.writeFloat(rv.pixelRectByGlyphCode[j].width);
          cacheOut.writeFloat(rv.pixelRectByGlyphCode[j].height);
          cacheOut.writeFloat(rv.logicalRectByGlyphCode[j].x);
          cacheOut.writeFloat(rv.logicalRectByGlyphCode[j].y);
          cacheOut.writeFloat(rv.logicalRectByGlyphCode[j].width);
          cacheOut.writeFloat(rv.logicalRectByGlyphCode[j].height);
        }
        for(j=0; j < imageCount+1; j++)
        {
          cacheOut.writeInt(rv.glyphCounts[j]);
        }
        cacheOut.close();
      }
      catch (Exception e)
      {
        System.out.println("Error writing font cache : " + e);
      }
      finally
      {
        if (cacheOut != null)
        {
          try{cacheOut.close();}catch(Exception e){}
        }
      }
    }
    accelerator = rv;
    return rv;
  }

  public java.awt.image.BufferedImage loadJavaFontImage(SageRenderer.CachedFontGlyphs cacheData, int imageIndex)
  {
    int x = 0;
    int y = 0;
    int maxHeightForRow = 0;
    java.awt.image.BufferedImage currImage = new java.awt.image.BufferedImage(cacheData.width, cacheData.height,
        java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
    int numGlyphs = getNumGlyphs();
    int startGlyph = (imageIndex == 0) ? 0 : cacheData.glyphCounts[imageIndex - 1];
    int endGlyph = Math.min(numGlyphs, cacheData.glyphCounts[imageIndex] - 1);
    synchronized (ftLock)
    {
      for (int i = startGlyph; i <= endGlyph; i++)
      {
        renderGlyph(i, currImage, (int)cacheData.logicalRectByGlyphCode[i].x, (int)cacheData.logicalRectByGlyphCode[i].y);
      }
    }

    // Fix the alpha for the image
    if (UIManager.shouldAntialiasFont(getSize()) && !Sage.getBoolean("ui/dont_premultiply_alpha_for_3dfontmaps", false))
    {
      int[] texturedata = ((java.awt.image.DataBufferInt) currImage.getRaster().getDataBuffer()).getData();
      for (int j = 0; j < texturedata.length; j++)
      {
        int tmp = (texturedata[j] >> 24) & 0xFF;
        if (tmp != 0)
        {
          tmp = tmp | (tmp << 8);
          texturedata[j] = tmp | (tmp << 16);
        }
      }
    }


    if (Sage.DBG) System.out.println("Rendered new font to cache index=" + imageIndex + " font=" + this);
    if (Sage.getBoolean("ui/dump_font_cache", false))
    {
      try
      {
        java.io.FileOutputStream os = new java.io.FileOutputStream(getName() + "-" + getStyle() + "-" +  getSize() + "_" + imageIndex + ".png");
        javax.imageio.ImageIO.write(currImage, "png", os);
        os.close();
      }catch(Exception e){}
    }
    return currImage;
  }

  public sage.media.image.RawImage loadRawFontImage(SageRenderer.CachedFontGlyphs cacheData, int imageIndex)
  {
    int x = 0;
    int y = 0;
    int maxHeightForRow = 0;
    sage.media.image.RawImage rv = null;
    int numGlyphs = getNumGlyphs();
    int startGlyph = (imageIndex == 0) ? 0 : cacheData.glyphCounts[imageIndex - 1];
    int endGlyph = Math.min(numGlyphs, cacheData.glyphCounts[imageIndex] - 1);
    synchronized (ftLock)
    {
      for (int i = startGlyph; i <= endGlyph; i++)
      {
        loadGlyph(i);
        rv = renderGlyphRaw0(fontFacePtr, rv, cacheData.width, cacheData.height, (int)cacheData.logicalRectByGlyphCode[i].x,
            (int)cacheData.logicalRectByGlyphCode[i].y);
      }
    }

    if (Sage.DBG) System.out.println("Rendered new font to raw cache index=" + imageIndex + " font=" + this);

    if (Sage.getBoolean("ui/dump_font_cache", false))
    {
      try
      {
        java.io.FileOutputStream os = new java.io.FileOutputStream(getName() + "-" + getStyle() + "-" + getSize() + "_" + imageIndex + ".png");
        javax.imageio.ImageIO.write(rv.convertToBufferedImage(), "png", os);
        os.close();
      }catch(Exception e){}
    }
    return rv;
  }
  private String fontPath;
  private long fontFacePtr;
  private int currLoadedGlyph = -1;
  private int ftErr; // error code from native code
  private FreetypeFont parentFont; // for shared font face information
  private SageRenderer.CachedFontGlyphs accelerator;

  public class FreetypeGlyphVector extends MetaFont.GlyphVector
  {
    public FreetypeGlyphVector(int inAdvance, int inVisAdvance, String inText,
        int[] inGlyphCodes, float[] inPos, java.awt.geom.Rectangle2D.Float inBox)
    {
      advance = inAdvance;
      visAdvance = inVisAdvance;
      text = inText;
      glyphCodes = inGlyphCodes;
      glyphPos = inPos;
      boundingBox = inBox;
      font = FreetypeFont.this;
    }
  }
}
