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

public abstract class MetaFont
{
  /**
   * The plain style constant.
   */
  public static final int PLAIN	= 0;

  /**
   * The bold style constant.  This can be combined with the other style
   * constants (except PLAIN) for mixed styles.
   */
  public static final int BOLD	= 1;

  /**
   * The italicized style constant.  This can be combined with the other
   * style constants (except PLAIN) for mixed styles.
   */
  public static final int ITALIC	= 2;

  private static final boolean USE_JAVA_FONTS = Sage.getBoolean("ui/disable_freetype_fonts", false);
  public static MetaFont getFont(String fontName, int fontStyle, int fontSize)
  {
    if (USE_JAVA_FONTS)
      return new JavaFont(fontName, fontStyle, fontSize);
    else
    {
      try
      {
        return new FreetypeFont(fontName, fontStyle, fontSize);
      }
      catch (Throwable e)
      {
        if (Sage.DBG && !(e instanceof java.io.FileNotFoundException)) System.out.println("Error loading Freetype Font; trying to load Java font instead; error=" + e);
        return new JavaFont(fontName, fontStyle, fontSize);
      }
    }
  }
  public MetaFont(String fontName, int fontStyle, int fontSize)
  {
    size = fontSize;
    name = fontName;
    style = fontStyle;
  }
  public abstract MetaFont deriveFontSize(int newSize, UIManager uiLoader);
  public abstract MetaFont deriveFont(int newStyle, UIManager uiLoader);
  public abstract /*EMBEDDED_SWITCH*/float/*/int/**/ getHeight();
  public abstract int stringWidth(String s);
  public /*EMBEDDED_SWITCH*/float/*/int/**/ charWidth(char c)
  {
    return stringWidth("" + c);
  }
  public abstract /*EMBEDDED_SWITCH*/float/*/int/**/ getAscent();
  public abstract /*EMBEDDED_SWITCH*/float/*/int/**/ getDescent();

  public abstract GlyphVector createGlyphVector(String s);
  public abstract GlyphVector[] createGlyphVectors(String s, int wrapWidth);

  public abstract /*EMBEDDED_SWITCH*/float/*/int/**/ getLeading();
  public String getName() { return name; }
  public int getSize() { return size; }
  public int getStyle() { return style; }
  public abstract void releaseNativeFont();
  public abstract long getNativeFontHandle();
  public abstract int getNumGlyphs();
  // Creates the rect maps for the glyphs for the accelerated font. This does not actually do the rendering.
  public abstract SageRenderer.CachedFontGlyphs loadAcceleratedFont(int maxRequiredGlyphCode, int width, int height);
  // These methods will create the actual image to be used in the caching system
  public abstract java.awt.image.BufferedImage loadJavaFontImage(SageRenderer.CachedFontGlyphs cacheData, int imageIndex);
  public abstract sage.media.image.RawImage loadRawFontImage(SageRenderer.CachedFontGlyphs cacheData, int imageIndex);
  public String toString()
  {
    return "MetaFont[" + name + ", size=" + size + ", style=" + style + ", height=" + height +
        ", ascent=" + ascent + ", descent=" + descent + ", leading=" + leading + ", class=" + getClass() + "]";
  }

  public boolean equals(Object o)
  {
    return (o instanceof MetaFont) && ((MetaFont) o).name.equals(name) && (((MetaFont) o).size == size) && (((MetaFont) o).style == style);
  }

  public int hashCode()
  {
    return name.hashCode() ^ 7*size ^ 273*style;
  }

  public static java.awt.Font getJavaFont(MetaFont font)
  {
    return UIManager.getCachedJavaFont(font.name, font.style, font.size);
  }

  protected String name;
  protected int size;
  protected int style;
  protected long nativeHandle;
  protected float height;
  protected float ascent;
  protected float descent;
  protected float leading;

  public static class JavaFont extends MetaFont
  {
    public JavaFont(String fontName, int fontStyle, int fontSize)
    {
      super(fontName, fontStyle, fontSize);
      myFont = UIManager.getCachedJavaFont(fontName, fontStyle, fontSize);
      frc = new java.awt.font.FontRenderContext(null, true, false);
      java.awt.font.LineMetrics metrics = myFont.getLineMetrics("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopwrstuvwxyz0123456789", frc);
      height = metrics.getHeight();
      ascent = metrics.getAscent();
      leading = metrics.getLeading();
      descent = metrics.getDescent();
    }
    public MetaFont deriveFontSize(int newSize, UIManager uiLoader)
    {
      return new JavaFont(name, style, newSize);
    }
    public MetaFont deriveFont(int newStyle, UIManager uiLoader)
    {
      return new JavaFont(name, newStyle, size);
    }

    public float getHeight()
    {
      return height;
    }

    public int stringWidth(String s)
    {
      return (int)myFont.getStringBounds(s, frc).getWidth();
    }

    public float getAscent()
    {
      return ascent;
    }

    public float getDescent()
    {
      return descent;
    }

    public float getLeading()
    {
      return leading;
    }

    public void releaseNativeFont()
    {
    }

    public long getNativeFontHandle()
    {
      return 0;
    }

    public MetaFont.GlyphVector createGlyphVector(String s)
    {
      return new JavaGlyphVector(this, s, new java.awt.font.TextLayout(s, myFont, frc), frc);
    }

    public MetaFont.GlyphVector[] createGlyphVectors(String s, int wrapWidth)
    {
      java.util.ArrayList rv = new java.util.ArrayList();
      java.text.AttributedString attStr = new java.text.AttributedString(s);
      attStr.addAttribute(java.awt.font.TextAttribute.FONT, myFont);
      java.awt.font.LineBreakMeasurer lbm =
          new java.awt.font.LineBreakMeasurer(attStr.getIterator(), frc);
      int idx0 = 0;
      java.awt.font.TextLayout currLay = lbm.nextLayout(wrapWidth);
      while (currLay != null)
      {
        rv.add(new JavaGlyphVector(this, s.substring(idx0, lbm.getPosition()), currLay, frc));
        idx0 = lbm.getPosition();
        currLay = lbm.nextLayout(wrapWidth);
      }
      return (MetaFont.GlyphVector[]) rv.toArray(new MetaFont.GlyphVector[0]);
    }

    public int getNumGlyphs()
    {
      return myFont.getNumGlyphs();
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
        maxWidthForGlyph = stringWidth("W") + 4;
        maxWidthForGlyph = Math.max(maxWidthForGlyph, stringWidth("" + '\u5355') + 4);
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
      int[] tmpGlyphCounts = new int[1024]; // way more then we'd ever have
      int i = 0;
      for (; i < numGlyphs; i++)
      {
        java.awt.geom.Rectangle2D glyphBounds;
        java.awt.Rectangle pixelBounds;
        if (fixedGlyphCacheWidth && i > orgMaxRequiredGlyphCode)
        {
          glyphBounds = new java.awt.geom.Rectangle2D.Float(0, 0, maxWidthForGlyph, getHeight());
          pixelBounds = new java.awt.Rectangle(-2, (int)Math.floor(-getAscent()), maxWidthForGlyph, (int)Math.ceil(getHeight()));
        }
        else
        {
          java.awt.font.GlyphVector currGlyph = myFont.createGlyphVector(frc, new int[] { i });
          glyphBounds = currGlyph.getLogicalBounds();
          pixelBounds = currGlyph.getGlyphPixelBounds(0, frc, 0, 0);
        }
        if (x + pixelBounds.width >= width)
        {
          // Move us on to the next line
          x = 0;
          y += maxHeightForRow + 1;
          maxHeightForRow = 0;
        }
        if (y + glyphBounds.getHeight() >= height)
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
        // pixelBounds tells us what pixels the glyph will cover in the image
        // If we've got a negative x; then we need to shift over more in x so we don't overlap prior glyphs
        // If we've got a positive y; then we've got extra wasted space on the left side and we can decrease x
        x -= pixelBounds.x;
        numCachedGlyphs = i;
        rv.imageIndexByGlyphCode[i] = imageCount;
        rv.pixelRectByGlyphCode[i] = new java.awt.geom.Rectangle2D.Float(x + pixelBounds.x, y,
            pixelBounds.width, pixelBounds.height);
        rv.logicalRectByGlyphCode[i] = new java.awt.geom.Rectangle2D.Float(x, y - pixelBounds.y,
            (float)glyphBounds.getWidth(), (float)glyphBounds.getHeight());
        maxHeightForRow = Math.max(maxHeightForRow, pixelBounds.height);
        x += pixelBounds.width + pixelBounds.x + 1;
      }
      tmpGlyphCounts[imageCount] = i;
      rv.glyphCounts = new int[imageCount + 1];
      System.arraycopy(tmpGlyphCounts, 0, rv.glyphCounts, 0, imageCount + 1);
      if (Sage.DBG) System.out.println("Loaded font cache info for " + this + " numGlyphs=" + numGlyphs + " maxGlyph=" + maxRequiredGlyphCode);
      return rv;
    }

    public java.awt.image.BufferedImage loadJavaFontImage(SageRenderer.CachedFontGlyphs cacheData, int imageIndex)
    {
      int x = 1; // to avoid overlap on the leftmost pixel
      int y = 0;
      int maxHeightForRow = 0;
      java.awt.image.BufferedImage currImage = new java.awt.image.BufferedImage(cacheData.width, cacheData.height,
          java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
      java.awt.Graphics2D g2 = currImage.createGraphics();
      g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setColor(java.awt.Color.white);
      int numGlyphs = getNumGlyphs();
      int startGlyph = (imageIndex == 0) ? 0 : cacheData.glyphCounts[imageIndex - 1];
      int endGlyph = Math.min(numGlyphs, cacheData.glyphCounts[imageIndex] - 1);
      for (int i = startGlyph; i <= endGlyph; i++)
      {
        java.awt.font.GlyphVector currGlyph = myFont.createGlyphVector(frc, new int[] { i });
        g2.setClip(cacheData.pixelRectByGlyphCode[i]);// make sure the glyph doesn't overwrite other glyphs
        g2.drawGlyphVector(currGlyph, cacheData.logicalRectByGlyphCode[i].x, cacheData.logicalRectByGlyphCode[i].y);
      }
      g2.dispose();

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

      if (Sage.DBG) System.out.println("Rendered new font to cache index=" + imageIndex + " font=" + myFont);
      if (Sage.getBoolean("ui/dump_font_cache", false))
      {
        try
        {
          java.io.FileOutputStream os = new java.io.FileOutputStream(getName() + getSize() + "_" + imageIndex + ".png");
          javax.imageio.ImageIO.write(currImage, "png", os);
          os.close();
        }catch(Exception e){}
      }
      return currImage;
    }

    public sage.media.image.RawImage loadRawFontImage(SageRenderer.CachedFontGlyphs cacheData, int imageIndex)
    {
      return new sage.media.image.RawImage(loadJavaFontImage(cacheData, imageIndex));
    }

    private java.awt.Font myFont;
    private java.awt.font.FontRenderContext frc;
  }

  public static abstract class GlyphVector
  {
    public /*EMBEDDED_SWITCH*/float/*/int/**/ getAdvance()
    {
      return advance;
    }
    public /*EMBEDDED_SWITCH*/float/*/int/**/ getVisibleAdvance()
    {
      return visAdvance;
    }
    public String getText()
    {
      return text;
    }
    public int getNumGlyphs()
    {
      return glyphCodes.length;
    }
    public int getGlyphCode(int i)
    {
      return glyphCodes[i];
    }
    public float getGlyphPosition(int i)
    {
      return glyphPos[i];
    }
    public java.awt.geom.Rectangle2D getLogicalBounds()
    {
      return boundingBox;
    }
    public /*EMBEDDED_SWITCH*/float/*/int/**/ getHeight()
    {
      // With Freetype, sometimes the ascent + descent is greater than the height, and this would then cause the bottom
      // of glyphs to get cutoff since we would use this height as the source bounding box for the rendering operation
      return Math.max(font.getHeight(), font.getAscent() + font.getDescent());
    }
    protected /*EMBEDDED_SWITCH*/float/*/int/**/ advance;
    protected /*EMBEDDED_SWITCH*/float/*/int/**/ visAdvance;
    protected String text;
    protected int[] glyphCodes;
    protected float[] glyphPos;
    protected MetaFont font;
    protected java.awt.geom.Rectangle2D.Float boundingBox; // relative to a baseline at the origin
  }
  public static final boolean ENABLE_BIDI_ANALYSIS = Sage.getBoolean("ui/load_complete_glyph_maps", false);

  public static class JavaGlyphVector extends GlyphVector
  {
    public JavaGlyphVector(MetaFont inFont, String inText, java.awt.font.TextLayout inLayer, java.awt.font.FontRenderContext frc)
    {
      font = inFont;
      if (ENABLE_BIDI_ANALYSIS)
      {
        // Perform Bidi analysis to reverse the proper portions of the text
        java.text.Bidi booty = new java.text.Bidi(inText, java.text.Bidi.DIRECTION_LEFT_TO_RIGHT);
        if (booty.isLeftToRight())
        {
          javaGV = MetaFont.getJavaFont(font).createGlyphVector(frc, inText);
        }
        else
        {
          if (booty.isRightToLeft())
          {
            javaGV = MetaFont.getJavaFont(font).createGlyphVector(frc, new StringBuffer(inText).reverse().toString().trim());
          }
          else
          {
            int runCounts = booty.getRunCount();
            byte[] levelInfo = new byte[runCounts];
            Object[] textObjs = new Object[runCounts];
            for (int i = 0; i < runCounts; i++)
            {
              levelInfo[i] = (byte)booty.getRunLevel(i);
              textObjs[i] = inText.substring(booty.getRunStart(i), booty.getRunLimit(i));
              textObjs[i] = (levelInfo[i] % 2) == booty.getBaseLevel() ? textObjs[i].toString() :
                new StringBuffer(textObjs[i].toString()).reverse().toString();
            }
            java.text.Bidi.reorderVisually(levelInfo, 0, textObjs, 0, runCounts);
            StringBuffer newText = new StringBuffer(inText.length() + 1);
            for (int i = 0; i < textObjs.length; i++)
              newText.append(textObjs[i].toString());
            javaGV = MetaFont.getJavaFont(font).createGlyphVector(frc, newText.toString());
          }
        }
      }
      else
        javaGV = MetaFont.getJavaFont(font).createGlyphVector(frc, inText);
      advance = inLayer.getAdvance();
      visAdvance = inLayer.getVisibleAdvance();
      text = inText;
    }

    public int getNumGlyphs()
    {
      return javaGV.getNumGlyphs();
    }

    public int getGlyphCode(int i)
    {
      return javaGV.getGlyphCode(i);
    }

    public float getGlyphPosition(int i)
    {
      return (float)javaGV.getGlyphPosition(i).getX();
    }

    public java.awt.font.GlyphVector getJavaGV()
    {
      return javaGV;
    }
    public java.awt.geom.Rectangle2D getLogicalBounds()
    {
      return javaGV.getLogicalBounds();
    }
    private java.awt.font.GlyphVector javaGV;
  }
}
