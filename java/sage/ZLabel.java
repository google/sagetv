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

public class ZLabel extends ZComp
{
  public static final MetaFont DEFAULT_FONT = UIManager.getCachedMetaFont("Arial", 0, 18, null);
  public ZLabel(ZRoot inReality)
  {
    this(inReality, null);
  }
  public ZLabel(ZRoot inReality, String inText)
  {
    this(inReality, inText, null);
  }
  protected ZLabel(ZRoot inReality, String inText, MetaFont inFont)
  {
    super(inReality);
    theString = (inText == null) ? "" : inText;
    insertionPoint = theString.length();
    uiMgr = getReality().getUIMgr();

    myFont = inFont == null ? DEFAULT_FONT : inFont;
    //myMetrics = reality.getFontMetrics(myFont);
    //calculatePrefSize();

    cachedRenderOps = new java.util.ArrayList();
    cachedBounds = new java.awt.geom.Rectangle2D.Float();
    cachedClipRect = new java.awt.geom.Rectangle2D.Float();
  }

  protected float lastFontScaleState;
  protected int lastRealityHeight;
  protected boolean updateDynamicFont()
  {
    if (disableFontScaling)
    {
      if (dynamicFont == null || myFont.getSize() != dynamicFont.getSize())
      {
        dynamicFont = myFont;
        //myMetrics = reality.getFontMetrics(dynamicFont);
        return true;
      }
      return false;
    }
    int realityHeight = reality.getRoot().getHeight();
    lastRealityHeight = realityHeight;
    float osh = uiMgr.getOverscanScaleHeight();
    int minFontSize = reality.getMinFontSize();
    float fontScaleState = realityHeight * minFontSize * osh;
    if (fontScaleState == lastFontScaleState && dynamicFont != null)
      return false;
    lastFontScaleState = fontScaleState;
    if ((reality.getRenderEngine() instanceof Java2DSageRenderer &&
        ((Java2DSageRenderer) reality.getRenderEngine()).hasOSDRenderer()) || reality.getRenderType() == ZRoot.REMOTE2DRENDER)
    {
      realityHeight = reality.getHeight();
    }
    // Scale XBMC fonts based on its target UI size; not our 480 default
    int baseHeight = reality.getUIMgr().isXBMCCompatible() ? reality.getUIMgr().getInt("ui/scaling_insets_base_height", 480) : 480;//Sage.getInt("ui/fixed_resolution_height", 480);
    float scale = (realityHeight <= 0) ? 1.0f : ((realityHeight * osh)/((float)baseHeight));
    int newPoint = (int)(myFont.getSize()*scale);
    newPoint = Math.max(minFontSize, newPoint);
    // mod two the font size
    //newPoint = ((newPoint - myFont.getSize())/2)*2 + myFont.getSize();
    if (dynamicFont != null && newPoint == dynamicFont.getSize())
      return false;
    dynamicFont = myFont.deriveFontSize(newPoint, reality.getUIMgr());
    //myMetrics = reality.getFontMetrics(dynamicFont);
    return true;
  }

  protected void recalculateDynamicFonts()
  {
    if (updateDynamicFont())
    {
      synchronized (layoutCacheLock)
      {
        layoutVisibleAdvance = null;
        appendToDirty(subtitleText ? false : true);
      }
    }
  }

  public void setFont(MetaFont f)
  {
    synchronized (layoutCacheLock)
    {
      if (!f.equals(myFont))
      {
        myFont = f;
        dynamicFont = null;
        updateDynamicFont();
        layoutVisibleAdvance = null;
        appendToDirty(subtitleText ? false : true);
      }
    }
  }

  public boolean setText(String inText)
  {
    if (!inText.equals(theString))
    {
      synchronized (layoutCacheLock)
      {
        theString = inText;
        layoutVisibleAdvance = null;
        if (editable)
          insertionPoint = Math.max(0, Math.min(insertionPoint, theString.length()));
        appendToDirty(subtitleText ? false : true);
        if (crossFadeDuration > 0)
          crossFadeNow = true;
      }
      return true;
    }
    return false;
  }

  protected void calculatePrefSize(int availableWidth, int availableHeight)
  {
    //System.out.println("ZLabel CALCULATING " + theString + " getPreferredSize(pw=" + availableWidth + " ph=" + availableHeight + ") ps=" + prefSize + " cw=" + cachedWidth + " ch=" + cachedHeight);
    //Thread.dumpStack();
    updateDynamicFont();
    FloatInsets ins = getInsets();
    prefSize.width = availableWidth;

    prefSize.height = ins.top + ins.bottom;
    float maxWidth = 0;

    java.util.ArrayList layoutVec = Pooler.getPooledArrayList();
    java.util.ArrayList textVec = Pooler.getPooledArrayList();
    float wrapWidth = singleLine ? Float.MAX_VALUE : (prefSize.width - ins.left - ins.right);
    if (shadowText)
    {
      wrapWidth -= 2;
    }
    wrapWidth = Math.max(0, wrapWidth);
    // Also account for the natural line breaks.
    String renderString;
    if (hideText)
    {
      renderString = "";
      for (int i = 0; i < theString.length(); i++)
        renderString += '*';
    }
    else
      renderString = theString;
    java.util.StringTokenizer toker = new java.util.StringTokenizer(
        (renderString.length() == 0 ? " " : renderString), "\r\n");
    cacheWrapped = false;
    java.util.ArrayList gvecList = Pooler.getPooledArrayList();
    while (toker.hasMoreTokens())
    {
      String currToken = toker.nextToken();
      // NOTE: Narflex - 6/24/2010 - There are cases where the height of the font is LESS than the maximum height of a glyph in that font.
      // In that case, we need to ensure we add that difference to our preferred size height so that the last line of text does not have
      // the bottom of descenders cut off. NOTE: We disabled this fix because we did it in MetaFont instead.
      if (singleLine)
      {
        MetaFont.GlyphVector currgvec = dynamicFont.createGlyphVector(currToken);
        gvecList.add(currgvec);
        layoutVec.add(new Float(showTrailingWhitespace ? currgvec.getAdvance() : currgvec.getVisibleAdvance()));
        textVec.add(currToken);
        prefSize.height += dynamicFont.getHeight();
        // This is including the size of extra whitespace at the end of the line
        maxWidth = Math.max(maxWidth, (showTrailingWhitespace ? currgvec.getAdvance() : currgvec.getVisibleAdvance()) + ins.left + ins.right);
      }
      else
      {
        MetaFont.GlyphVector[] gvecs = dynamicFont.createGlyphVectors(currToken, (int)wrapWidth);
        gvecList.addAll(java.util.Arrays.asList(gvecs));
        if (gvecs.length > 1)
          cacheWrapped = true;
        for (int i = 0; i < gvecs.length; i++)
        {
          layoutVec.add(new Float(showTrailingWhitespace ? gvecs[i].getAdvance() : gvecs[i].getVisibleAdvance()));
          textVec.add(gvecs[i].getText());
          if (reality.isIntegerPixels())
            prefSize.height += Math.ceil(dynamicFont.getHeight());
          else
            prefSize.height += dynamicFont.getHeight();
          // This is including the size of extra whitespace at the end of the line
          maxWidth = Math.max(maxWidth, (showTrailingWhitespace ? gvecs[i].getAdvance() : gvecs[i].getVisibleAdvance()) + ins.left + ins.right);
        }
      }
    }

    // Account for the difference of the ascent + descent being greater than the font height
    float extraSpace = dynamicFont.getAscent() + dynamicFont.getDescent() - dynamicFont.getHeight();
    if (extraSpace > 0 && prefSize.height > 0)
      prefSize.height += extraSpace;
    synchronized (layoutCacheLock)
    {
      layoutVisibleAdvance = (Float[]) layoutVec.toArray(new Float[0]);
      textMatch = (String[]) textVec.toArray(new String[0]);
      glyphVectorMatch = (MetaFont.GlyphVector[]) gvecList.toArray(new MetaFont.GlyphVector[0]);
    }
    Pooler.returnPooledArrayList(layoutVec);
    Pooler.returnPooledArrayList(textVec);
    Pooler.returnPooledArrayList(gvecList);

    if (shadowText)
    {
      maxWidth += 2;
    }
    truncatedWidth = (prefSize.width < maxWidth);
    if (reality.isIntegerPixels())
    {
      maxWidth = (float)Math.ceil(maxWidth);
      prefSize.height = (float)Math.ceil(prefSize.height);
      prefSize.width = (float)Math.ceil(prefSize.width);
    }
    actualPreferredWidth = maxWidth;
    if (!cacheWrapped)
    {
      // NARFLEX - 06/11/2010 - We used to not have the !cacheWrapped conditional above. The reason we should have it is because if the text has been wrapped, then
      // its preferred size is actually bigger than the available width, so we shouldn't indicate that its less than that. Otherwise if the parent has a relative sized
      // width (i.e. 0.97) then each time pref size is checked it'll end up reducing its width slightly because we report that we don't need as much width as we'd actually
      // want. By indicating we want the max available width instead we can keep the layout from having to recalculate multiple times which can lead to inconsistent results.
      prefSize.width = Math.min(maxWidth, prefSize.width);
    }
    cachedWidth = availableWidth;
    cachedHeight = availableHeight;
    validRenderCache = false;
    //cachedRenderOps.clear();
    //System.out.println("ZLabel CALCULATED " + theString + " ps=" + prefSize + " cw=" + cachedWidth + " ch=" + cachedHeight);
  }

  public String getText()
  {
    return theString;
  }

  public java.awt.geom.Rectangle2D.Float getPreferredSize(float availableWidth, float availableHeight,
      float parentWidth, float parentHeight, int depth)
  {
    if (layoutVisibleAdvance == null || (cachedWidth < Math.round(availableWidth) && (cacheWrapped || truncatedWidth)) ||
        (prefSize.width > Math.round(availableWidth)) || (cachedHeight > Math.round(availableHeight) && prefSize.height > cachedHeight))
    {
      calculatePrefSize(Math.round(availableWidth), Math.round(availableHeight));
    }
    //		else
    //System.out.println("ZLabel  " + theString + " getPreferredSize(pw=" + parentWidth + " ph=" + parentHeight + ") ps=" + prefSize);
    return prefSize;
  }

  public void buildRenderingOps(java.util.ArrayList opList, java.awt.geom.Rectangle2D.Float clipRect,
      int diffuseColor, float alphaFactor, float xoff, float yoff, int flags)
  {
    if (crossFadeNow)
    {
      // Switching to a new cross-fade target
      fadeOutRops = cachedRenderOps;
      cachedRenderOps = new java.util.ArrayList();
      float lastFadeProgress = 1.0f;
      if (crossEffectOut != null && crossEffectOut.isActive())
        lastFadeProgress = crossEffectOut.getCurrentFade();
      if (!fadeOutRops.isEmpty())
      {
        crossEffectIn = new EffectTracker((ZPseudoComp) parent, crossFadeDuration/3, 2*crossFadeDuration/3, (byte)0, EffectTracker.SCALE_LINEAR);
        crossEffectOut = new EffectTracker((ZPseudoComp) parent, 0, 2*crossFadeDuration/3, (byte)0, EffectTracker.SCALE_LINEAR);
      }
      else
      {
        crossEffectIn = new EffectTracker((ZPseudoComp) parent, 0, 2*crossFadeDuration/3, (byte)0, EffectTracker.SCALE_LINEAR);
        crossEffectOut = new EffectTracker((ZPseudoComp) parent, 0, 2*crossFadeDuration/3, (byte)0, EffectTracker.SCALE_LINEAR);
      }
      crossEffectIn.setFadeEffect(0, 1);
      crossEffectOut.setFadeEffect(lastFadeProgress, 0);
      crossEffectIn.setInitialPositivity(false);
      crossEffectOut.setInitialPositivity(false);
      crossEffectIn.setPositivity(true);
      crossEffectOut.setPositivity(true);
      crossFadeNow = false;
    }

    if (crossEffectIn != null)
    {
      if (!crossEffectIn.isActive())
      {
        crossEffectOut = null;
        crossEffectIn = null;
        fadeOutRops = null;
      }
      else if (crossEffectOut.isActive())
      {
        opList.add(new RenderingOp(crossEffectOut));
        opList.addAll(fadeOutRops);
        opList.add(new RenderingOp(null));
      }
    }
    if (crossEffectIn != null)
      opList.add(new RenderingOp(crossEffectIn));
    if (!editable && (theString == null || theString.length() == 0))
    {
      if (crossEffectIn != null)
        opList.add(new RenderingOp(null));
      return;
    }
    FloatInsets ins = getInsets();
    boolean dothecalc;
    // Narflex - 06/11/2010 - I added redoing the calculation again if we're wrapping and the width we used for the last layout calc
    // exceeds our bounds by more than 1. This can happen due to the way relative layout sizing calculations work in that it matters
    // how many times the prefSize calc was done for what the resulting value is. Rather than invalidating many layouts in STVs we're just
    // going to redo the text calculation size if its wrong. We're also going to use our bounds for the available size since
    // using the parents bounds wouldn't account for insets...and our size is essentially just the parents minus insets.
    synchronized (layoutCacheLock)
    {
      dothecalc = layoutVisibleAdvance == null || (lastRealityHeight != reality.getRoot().getHeight()) ||
          (cacheWrapped && (prefSize.width - boundsf.width > 1));/* || (cachedWidth < parent.boundsf.width && cacheWrapped) ||
				(prefSize.width > parent.boundsf.width) || (cachedHeight > parent.boundsf.height);*/
    }
    if (dothecalc)
    {
      if (subtitleText)
        calculatePrefSize(Math.round(boundsf.width), Math.round(boundsf.height));
      else
        calculatePrefSize(Math.round(/*parent.*/boundsf.width), Math.round(/*parent.*/boundsf.height));
    }

    // If less than half the height of a line of text is showing, don't render anything
    ZPseudoComp scrollParent = (parent instanceof ZPseudoComp) ? ((ZPseudoComp) parent).getScrollingParent() : null;
    java.awt.geom.Rectangle2D.Float trueBoundsf = getTrueBoundsf();
    java.awt.geom.Rectangle2D viewableArea = (scrollParent == null) ?
        trueBoundsf : scrollParent.getTrueBoundsf().createIntersection(trueBoundsf);
    boolean hideCroppedText = !(reality.getUIMgr().getBoolean("ui/show_cropped_text", true));
    if (hideCroppedText &&
        ((viewableArea.getHeight() < dynamicFont.getHeight()/2 && !fitToSize) ||
            (scrollParent != null && viewableArea.getHeight() < dynamicFont.getHeight()*0.9f)))
    {
      if (crossEffectIn != null)
        opList.add(new RenderingOp(null));
      return;
    }


    java.awt.Color textColor = java.awt.Color.white;
    java.awt.Color shadowColor = null;
    //int shadowOffX = 2;
    //int shadowOffY = 2;
    boolean currShadow = isShadowingText();
    if (subtitleText)
    {
      shadowColor = fgShadowColor;
      textColor = fgColor;
    }
    else if (parent instanceof ZPseudoComp)
    {
      ZPseudoComp zpp = (ZPseudoComp) parent;
      if (zpp.focusFgColor != null && doesAncestorOrMeHaveFocus() && ((flags & ZPseudoComp.RENDER_FLAG_SKIP_FOCUSED) == 0))
      {
        if (zpp.dynamicFocusFgColor)
        {
          textColor = zpp.getColorPropertyFromWidgetChain(Widget.FOREGROUND_SELECTED_COLOR, null, zpp.defaultThemes);
          if (textColor != null)
          {
            int fgSelAlphaProp = zpp.getIntPropertyFromWidgetChain(Widget.FOREGROUND_SELECTED_ALPHA, null, zpp.defaultThemes, -1);
            if (fgSelAlphaProp >= 0)
              textColor= new java.awt.Color(((fgSelAlphaProp & 0xFF) << 24) | (textColor.getRGB() & 0xFFFFFF), true);
          }
          else
            textColor = zpp.focusFgColor;
        }
        else
          textColor = zpp.focusFgColor;
      }
      else
      {
        if (zpp.dynamicFgColor)
        {
          textColor = zpp.getColorPropertyFromWidgetChain(Widget.FOREGROUND_COLOR, null, zpp.defaultThemes);
          if (textColor != null)
          {
            int fgSelAlphaProp = zpp.getIntPropertyFromWidgetChain(Widget.FOREGROUND_ALPHA, null, zpp.defaultThemes, -1);
            if (fgSelAlphaProp >= 0)
              textColor= new java.awt.Color(((fgSelAlphaProp & 0xFF) << 24) | (textColor.getRGB() & 0xFFFFFF), true);
          }
          else
            textColor = zpp.originalFgColor;
        }
        else
          textColor = zpp.originalFgColor;
      }
      if (currShadow)
      {
        if (zpp.focusFgShadowColor != null && doesAncestorOrMeHaveFocus() && ((flags & ZPseudoComp.RENDER_FLAG_SKIP_FOCUSED) == 0))
        {
          if (zpp.dynamicFocusFgShadowColor)
          {
            shadowColor = zpp.getColorPropertyFromWidgetChain(Widget.FOREGROUND_SHADOW_SELECTED_COLOR, null, zpp.defaultThemes);
            if (shadowColor != null)
            {
              int fgSelAlphaProp = zpp.getIntPropertyFromWidgetChain(Widget.FOREGROUND_SHADOW_SELECTED_ALPHA, null, zpp.defaultThemes, -1);
              if (fgSelAlphaProp >= 0)
                shadowColor = new java.awt.Color(((fgSelAlphaProp & 0xFF) << 24) | (shadowColor.getRGB() & 0xFFFFFF), true);
            }
            else
              shadowColor = zpp.focusFgShadowColor;
          }
          else
            shadowColor = zpp.focusFgShadowColor;
        }
        else
        {
          if (zpp.dynamicFgShadowColor)
          {
            shadowColor = zpp.getColorPropertyFromWidgetChain(Widget.FOREGROUND_SHADOW_COLOR, null, zpp.defaultThemes);
            if (shadowColor != null)
            {
              int fgSelAlphaProp = zpp.getIntPropertyFromWidgetChain(Widget.FOREGROUND_SHADOW_ALPHA, null, zpp.defaultThemes, -1);
              if (fgSelAlphaProp >= 0)
                shadowColor = new java.awt.Color(((fgSelAlphaProp & 0xFF) << 24) | (shadowColor.getRGB() & 0xFFFFFF), true);
            }
            else
              shadowColor = zpp.originalFgShadowColor;
          }
          else
            shadowColor = zpp.originalFgShadowColor;
        }
      }
    }
    if (diffuseColor != 0xFFFFFF)
    {
      textColor = new java.awt.Color(((textColor.getAlpha() & 0xFF) << 24) | MathUtils.compositeColors(diffuseColor, textColor.getRGB()));
      if (shadowColor != null)
        shadowColor = new java.awt.Color(((shadowColor.getAlpha() & 0xFF) << 24) | MathUtils.compositeColors(diffuseColor, shadowColor.getRGB()));
    }

    // Render our cursor
    float orgclipx=clipRect.x, orgclipy=clipRect.y, orgclipw=clipRect.width, orgcliph=clipRect.height;
    if (editable)
    {
      float cursorOffset = 0;
      if (glyphVectorMatch == null || glyphVectorMatch.length == 0 || theString == null || theString.length() == 0)
        cursorOffset = 0;
      else if (insertionPoint >= glyphVectorMatch[0].getNumGlyphs())
        cursorOffset = layoutVisibleAdvance[0].floatValue();
      else
        cursorOffset = (float)glyphVectorMatch[0].getGlyphPosition(insertionPoint);
      // Make sure that we have the cursor positioned so that it is visible, and if it's not then make the correction here for that
      float availWidth = boundsf.width - ins.left - ins.right;
      if (actualPreferredWidth > availWidth)
      {
        // This is needed to ensure proper clipping of the text if its wider than the bounds
        clipRectToBounds(clipRect, xoff, yoff);

        // If the cursor is beyond the bounds, then we shift the text 1/4 of the width to make it fit
        if (cursorOffset < textRenderOffset)
        {
          // cursor is too far off the left
          textRenderOffset = Math.max(0, cursorOffset - availWidth/4);
          validRenderCache = false;
        }
        else if (cursorOffset - textRenderOffset > availWidth - 2)
        {
          // cursor is too far off the right
          textRenderOffset = cursorOffset - availWidth*3/4;
          validRenderCache = false;
        }
        // Make sure we don't have whitespace on the right
        if (actualPreferredWidth - availWidth + 1 < textRenderOffset)
        {
          textRenderOffset = actualPreferredWidth - availWidth + 1;
          validRenderCache = false;
        }
      }
      else
      {
        if (textRenderOffset != 0)
          validRenderCache = false;
        textRenderOffset = 0;
      }
      PseudoMenu currUI = uiMgr.getCurrUI();
      if ((Sage.time() - lastCursorChangeTime) % 1000 < 500 && (currUI == null || !currUI.hasMultipleTextInputs() || doesAncestorOrMeHaveFocus()))
      {
        // On for a half second, then off for a half second
        SageRenderer.ShapeDescription cursorLine = new SageRenderer.ShapeDescription(1.0f, dynamicFont.getHeight(), textColor);
        cursorLine.fill = true;
        cursorLine.shapeType = "Rectangle";
        opList.add(new RenderingOp(cursorLine, 1.0f, clipRect, xoff + boundsf.x + Math.max(0, cursorOffset - textRenderOffset) +
            hAlign*(Math.max(0, availWidth - actualPreferredWidth)), yoff + boundsf.y + vAlign*(boundsf.height - dynamicFont.getHeight())));
      }
    }
    if (theString == null || theString.length() == 0)
    {
      clipRect.setFrame(orgclipx, orgclipy, orgclipw, orgcliph);
      // This is just for rendering the cursor only
      if (crossEffectIn != null)
        opList.add(new RenderingOp(null));
      return;
    }

    // NOTE: This won't refresh the ops on only a shadow color change, but that should be OK
    if (!cachedRenderOps.isEmpty() && validRenderCache &&
        SageRenderer.floatRectEquals(trueBoundsf, cachedBounds, xoff, yoff) &&
        SageRenderer.floatRectEquals(clipRect, cachedClipRect) &&
        (textColor == null || fgColor.equals(textColor)) &&
        //			MathUtils.isTranslateOnlyMatrix(renderXform) &&
        alphaFactor == 1.0f)
    {
      if (crossEffectIn != null)
        opList.add(new RenderingOp(null));
      opList.addAll(cachedRenderOps);
      clipRect.setFrame(orgclipx, orgclipy, orgclipw, orgcliph);
      return;
    }
    if (currShadow && shadowColor == null)
    {
      shadowColor = new java.awt.Color(0, 0, 0, fgColor.getAlpha());
    }
    boolean fullOutline = subtitleText && currShadow;
    int[] shadowOffsets = new int[currShadow ? (fullOutline ? 8 : 2) : 0];
    java.awt.Color[] shadowColors = new java.awt.Color[currShadow ? (fullOutline ? 4 : 1) : 0];
    if (currShadow)
    {
      shadowColors[0] = shadowColor;
      int shadowOff = uiMgr.isXBMCCompatible() ? 1 : 2;
      shadowOffsets[0] = shadowOff; // x
      shadowOffsets[1] = shadowOff; // y
      if (fullOutline)
      {
        // We slightly change the color in the shadow outlines so that rendering optimizations don't interfere with it
        shadowColors[1] = shadowColor.getRed() == 255 ? (new java.awt.Color(shadowColor.getRed() - 1, shadowColor.getGreen(), shadowColor.getBlue(), shadowColor.getAlpha())) :
          (new java.awt.Color(shadowColor.getRed() + 1, shadowColor.getGreen(), shadowColor.getBlue(), shadowColor.getAlpha()));
        shadowColors[2] = shadowColor.getGreen() == 255 ? (new java.awt.Color(shadowColor.getRed(), shadowColor.getGreen() - 1, shadowColor.getBlue(), shadowColor.getAlpha())) :
          (new java.awt.Color(shadowColor.getRed(), shadowColor.getGreen() + 1, shadowColor.getBlue(), shadowColor.getAlpha()));
        shadowColors[3] = shadowColor.getBlue() == 255 ? (new java.awt.Color(shadowColor.getRed(), shadowColor.getGreen(), shadowColor.getBlue() - 1, shadowColor.getAlpha())) :
          (new java.awt.Color(shadowColor.getRed(), shadowColor.getGreen(), shadowColor.getBlue() + 1, shadowColor.getAlpha()));
        shadowOffsets[2] = 2;
        shadowOffsets[3] = -2;
        shadowOffsets[4] = -2;
        shadowOffsets[5] = 2;
        shadowOffsets[6] = -2;
        shadowOffsets[7] = -2;
      }
    }
    //shadowOffX = 2;//Sage.getInt("text_shadow_offset_x", 2);
    //shadowOffY = 2;//Sage.getInt("text_shadow_offset_y", 2);
    fgColor = textColor;
    cachedRenderOps.clear();
    xoff += boundsf.x;
    yoff += boundsf.y;
    float trueY = trueBoundsf.y;
    float currY = (boundsf.height - prefSize.height)*vAlign + ins.top;
    if (uiMgr.isXBMCCompatible())
    {
      //currY += dynamicFont.getDescent()*vAlign;
      //currY += Math.max(0, Math.min(boundsf.height - prefSize.height - ins.top - ins.bottom, dynamicFont.getDescent()*vAlign));
      currY += (dynamicFont.getDescent() + dynamicFont.getLeading())*vAlign;
    }
    if ((currY < -1 || actualPreferredWidth > boundsf.width - ins.left - ins.right + 1 ||
        prefSize.height > boundsf.height - ins.top - ins.bottom + 1 || truncatedWidth) && !editable)
    {
      if (fitToSize)
      {
        float usedY = prefSize.height;
        float availY = boundsf.height - ins.top - ins.bottom;
        float usedX = actualPreferredWidth;
        float availX = boundsf.width - ins.left - ins.right;
        float scaleY = Math.min(1, availY/usedY);
        float scaleX = Math.min(1, availX/usedX);
        //System.out.println("Oversized by X:" + scaleX + " Y:" + scaleY);
        float fontScale = Math.min(scaleX, scaleY);
        int newPoint = (int)Math.floor(dynamicFont.getSize()*fontScale) - 1;
        int minFontSize = Math.min(reality.getMinShrunkFontSize(), dynamicFont.getSize());
        boolean restrictedShrink = false;
        if (newPoint < minFontSize)
        {
          newPoint = minFontSize;
          restrictedShrink = true;
        }
        fontScale = ((float) newPoint) / dynamicFont.getSize();
        //System.out.println("oldPoint= " + dynamicFont.getSize() + " newPoint=" + newPoint);
        currY = Math.max(0, (boundsf.height - prefSize.height*fontScale))*vAlign + ins.top;
        if (reality.isIntegerPixels())
          currY = (float)Math.floor(currY);
        MetaFont shrunkFont = dynamicFont.deriveFontSize(newPoint, reality.getUIMgr());
        //java.awt.FontMetrics scaledMetrics = reality.getFontMetrics(shrunkFont);
        int clipStringWidth = 0;
        String clipString = "...";
        if (restrictedShrink)
          clipStringWidth = dynamicFont.stringWidth(clipString);
        synchronized (layoutCacheLock)
        {
          for (int i = 0; i < layoutVisibleAdvance.length; i++)
          {
            if (currY + trueY + shrunkFont.getHeight()/(hideCroppedText ? 2 : 1) > viewableArea.getY())
            {
              MetaFont.GlyphVector shrunkGVec = shrunkFont.createGlyphVector(textMatch[i]);
              float currStrWidth = shrunkGVec.getVisibleAdvance();
              // Append an elipsis to the text if we weren't able to shrink it as much as we wanted to
              if (restrictedShrink && ((i < layoutVisibleAdvance.length - 1 &&
                  (currY + 2*shrunkFont.getHeight()) > boundsf.height - ins.bottom) ||
                  (currStrWidth > boundsf.width - ins.left - ins.right)))
              {
                // The next line will go over our vertical boundary or we're too big on this line,
                // so clip the right edge of the text on this line
                int totalWidth = clipStringWidth;
                int nChars;
                String subtext = textMatch[i];
                for(nChars = 0; nChars < subtext.length(); nChars++)
                {
                  totalWidth += shrunkFont.charWidth(subtext.charAt(nChars));
                  if (totalWidth > getWidth() - ins.left - ins.right)
                    break;
                }
                subtext = subtext.substring(0, nChars) + clipString;
                int subtextWidth = shrunkFont.stringWidth(subtext);
                MetaFont.GlyphVector subtextGlyphVec = shrunkFont.createGlyphVector(subtext);
                if (currY + trueY + shrunkFont.getHeight()/(hideCroppedText ? 2 : 1) > viewableArea.getY())
                {
                  float currX = (boundsf.width - ins.left - ins.right - subtextWidth)*hAlign + ins.left;
                  if (reality.isIntegerPixels())
                    currX = Math.round(currX);
                  if (currShadow)
                  {
                    for (int shad = 0; shad < shadowOffsets.length; shad += 2)
                    {
                      cachedRenderOps.add(new RenderingOp(new SageRenderer.TextDescription(
                          shrunkFont, subtextGlyphVec, subtext), shadowColors[shad/2],
                          alphaFactor, clipRect,
                          xoff + currX + shadowOffsets[shad],
                          yoff + currY + shadowOffsets[shad + 1]));
                    }
                  }
                  cachedRenderOps.add(new RenderingOp(new SageRenderer.TextDescription(shrunkFont,
                      subtextGlyphVec, subtext), fgColor, alphaFactor, clipRect,
                      xoff + currX, yoff + currY));
                }
                break;
              }
              float currX = (boundsf.width - ins.left - ins.right - currStrWidth)*hAlign + ins.left;
              if (reality.isIntegerPixels())
                currX = Math.round(currX);
              if (currShadow)
              {
                for (int shad = 0; shad < shadowOffsets.length; shad += 2)
                {
                  cachedRenderOps.add(new RenderingOp(new SageRenderer.TextDescription(shrunkFont,
                      shrunkGVec, textMatch[i]), shadowColors[shad/2], alphaFactor, clipRect,
                      xoff + currX + shadowOffsets[shad], yoff + currY + shadowOffsets[shad + 1]));
                }
              }
              cachedRenderOps.add(new RenderingOp(new SageRenderer.TextDescription(shrunkFont,
                  shrunkGVec, textMatch[i]), fgColor, alphaFactor, clipRect,
                  xoff + currX, yoff + currY));
            }

            currY += (reality.isIntegerPixels() ? ((float)Math.ceil(shrunkFont.getHeight())) : shrunkFont.getHeight());
            if (currY + trueY + (hideCroppedText ? shrunkFont.getHeight()/2 : 0) > viewableArea.getY() + viewableArea.getHeight())
              break;
          }
        }
      }
      else
      {
        String clipString = "...";
        int clipStringWidth = dynamicFont.stringWidth(clipString);
        synchronized (layoutCacheLock)
        {
          // Figure out how much y space we're going to actually use
          float testY = 0;
          for (int i = 0; i < layoutVisibleAdvance.length; i++)
          {
            testY += dynamicFont.getHeight();
            if (i < layoutVisibleAdvance.length - 1 &&
                (testY + dynamicFont.getHeight()) > boundsf.height - ins.bottom)
            {
              // The next line will go over our vertical boundary or we're too big on this line,
              // so clip the right edge of the text on this line
              break;
            }
          }
          currY = Math.max(uiMgr.isXBMCCompatible() ? -Float.MAX_VALUE : 0, (boundsf.height - ins.top - ins.bottom - testY)*vAlign) + ins.top;
          if (uiMgr.isXBMCCompatible())
          {
            //						if (prefSize.height > boundsf.height - ins.top - ins.bottom + 1)
            //							currY += dynamicFont.getDescent();
            //						else
            currY += (dynamicFont.getDescent() + dynamicFont.getLeading())*vAlign;
          }
          if (reality.isIntegerPixels())
            currY = (float)Math.floor(currY);
          for (int i = 0; i < layoutVisibleAdvance.length; i++)
          {
            if ((i < layoutVisibleAdvance.length - 1 &&
                (currY + 2*dynamicFont.getHeight()) > boundsf.height - ins.bottom) ||
                (layoutVisibleAdvance[i].floatValue() > boundsf.width - ins.left - ins.right))
            {
              // The next line will go over our vertical boundary or we're too big on this line,
              // so clip the right edge of the text on this line
              int totalWidth = clipStringWidth;
              int nChars;
              String subtext = textMatch[i];
              for(nChars = 0; nChars < subtext.length(); nChars++)
              {
                totalWidth += dynamicFont.charWidth(subtext.charAt(nChars));
                if (totalWidth > getWidth() - ins.left - ins.right)
                  break;
              }
              subtext = subtext.substring(0, nChars) + clipString;
              int subtextWidth = dynamicFont.stringWidth(subtext);
              MetaFont.GlyphVector subtextGlyphVec = dynamicFont.createGlyphVector(subtext);
              if (currY + trueY + dynamicFont.getHeight()/(hideCroppedText ? 2 : 1) > viewableArea.getY())
              {
                float currX = (boundsf.width - ins.left - ins.right - subtextWidth)*hAlign + ins.left;
                if (reality.isIntegerPixels())
                  currX = Math.round(currX);
                if (currShadow)
                {
                  for (int shad = 0; shad < shadowOffsets.length; shad += 2)
                  {
                    cachedRenderOps.add(new RenderingOp(new SageRenderer.TextDescription(
                        dynamicFont, subtextGlyphVec, subtext), shadowColors[shad/2],
                        alphaFactor, clipRect,
                        xoff + currX + shadowOffsets[shad], yoff + currY + shadowOffsets[shad + 1]));
                  }
                }
                cachedRenderOps.add(new RenderingOp(new SageRenderer.TextDescription(dynamicFont,
                    subtextGlyphVec, subtext), fgColor, alphaFactor, clipRect,
                    xoff + currX, yoff + currY));
              }
              break;
            }
            else
            {
              if (currY + trueY + dynamicFont.getHeight()/(hideCroppedText ? 2 : 1) > viewableArea.getY())
              {
                float currX = (boundsf.width - ins.left - ins.right - layoutVisibleAdvance[i].floatValue())*hAlign + ins.left;
                if (reality.isIntegerPixels())
                  currX = Math.round(currX);
                if (currShadow)
                {
                  for (int shad = 0; shad < shadowOffsets.length; shad += 2)
                  {
                    cachedRenderOps.add(new RenderingOp(new SageRenderer.TextDescription(dynamicFont,
                        glyphVectorMatch[i], textMatch[i]), shadowColors[shad/2], alphaFactor, clipRect,
                        xoff + currX + shadowOffsets[shad], yoff + currY + shadowOffsets[shad + 1]));
                  }
                }
                cachedRenderOps.add(new RenderingOp(new SageRenderer.TextDescription(dynamicFont,
                    glyphVectorMatch[i], textMatch[i]), fgColor, alphaFactor, clipRect,
                    xoff + currX, yoff + currY));
              }
            }
            currY += (reality.isIntegerPixels() ? ((float)Math.ceil(dynamicFont.getHeight())) : dynamicFont.getHeight());
            if (currY + trueY + (hideCroppedText ? dynamicFont.getHeight()/2 : 0) > viewableArea.getY() + viewableArea.getHeight())
            {
              break;
            }
          }
        }
      }
    }
    else
    {
      if (reality.isIntegerPixels())
        currY = (float)Math.floor(currY);
      synchronized (layoutCacheLock)
      {
        for (int i = 0; i < layoutVisibleAdvance.length; i++)
        {
          if (currY + trueY + dynamicFont.getHeight()/(hideCroppedText ? 2 : 1) > viewableArea.getY())
          {
            float currX = (boundsf.width - ins.left - ins.right - layoutVisibleAdvance[i].floatValue())*(textRenderOffset != 0 ? 0 : hAlign) + ins.left - textRenderOffset;
            if (reality.isIntegerPixels())
              currX = Math.round(currX);
            if (currShadow)
            {
              for (int shad = 0; shad < shadowOffsets.length; shad += 2)
              {
                cachedRenderOps.add(new RenderingOp(new SageRenderer.TextDescription(dynamicFont,
                    glyphVectorMatch[i], textMatch[i]), shadowColors[shad/2], alphaFactor, clipRect,
                    xoff + currX + shadowOffsets[shad], yoff + currY + shadowOffsets[shad + 1]));
              }
            }
            cachedRenderOps.add(new RenderingOp(new SageRenderer.TextDescription(dynamicFont,
                glyphVectorMatch[i], textMatch[i]), fgColor, alphaFactor, clipRect,
                xoff + currX, yoff + currY));
          }

          currY += (reality.isIntegerPixels() ? ((float)Math.ceil(dynamicFont.getHeight())) : dynamicFont.getHeight());
          if (currY + trueY + (hideCroppedText ? dynamicFont.getHeight()/2 : 0) > viewableArea.getY() + viewableArea.getHeight())
            break;
        }
      }
    }
    opList.addAll(cachedRenderOps);
    cachedBounds.setFrame(trueBoundsf);
    cachedBounds.x += xoff;
    cachedBounds.y += yoff;
    cachedClipRect.setFrame(clipRect);
    if (crossEffectIn != null)
      opList.add(new RenderingOp(null));
    clipRect.setFrame(orgclipx, orgclipy, orgclipw, orgcliph);

    // Clear our cached ops if they were from modified transparency from a parent
    if (alphaFactor < 1.0f)
    {
      //cachedRenderOps.clear();
      validRenderCache = false;
    }
  }

  public void setHAlign(float x)
  {
    hAlign = x;
  }
  public void setVAlign(float x)
  {
    vAlign = x;
  }

  public void setFitToSize(boolean x) { fitToSize = x; }

  public void setSingleLine(boolean x) { singleLine = x; }

  public boolean getHideText() { return hideText; }
  public void setHideText(boolean x) { hideText = x; }
  public boolean getTextShadow() { return shadowText; }
  public void setTextShadow(boolean x) { shadowText = x; }
  public void setForeground(java.awt.Color inColor)
  {
    if (!inColor.equals(getForeground()))
      cachedRenderOps.clear();
    super.setForeground(inColor);
  }
  public void setDisableFontScaling(boolean x) { disableFontScaling = x; }

  private boolean isShadowingText()
  {
    if (subtitleText) return shadowText;
    if (reality.getUIMgr().getBoolean("ui/text_shadow_always", false))
      return true;
    if (reality.getTextShadowNever())
      return false;
    return shadowText;
  }

  public void setShowTrailingWhitespace(boolean x)
  {
    showTrailingWhitespace = x;
  }

  public void setSubtitleText(boolean x)
  {
    subtitleText = x;
  }

  public void setCrossFadeDuration(int x)
  {
    crossFadeDuration = x;
  }

  protected boolean processHideEffects(boolean validRegion)
  {
    // Prevent cross-fade in from occurring
    cachedRenderOps.clear();
    crossFadeNow = false;
    return false;
  }

  public void setEditable(boolean x)
  {
    editable = x;
  }
  public boolean isEditable() { return editable; }

  public boolean processInput(UserEvent evt)
  {
    int evtType = evt.getType();
    int numericText = evt.getNumCode(evtType);
    if (numericText != -1 || evtType == UserEvent.DASH)
    {
      setText(theString.substring(0, insertionPoint) + ((evtType == UserEvent.DASH) ? "-" : Integer.toString(numericText)) +
          theString.substring(insertionPoint));
      insertionPoint++;
      lastCursorChangeTime = Sage.time();
      return true;
    }
    // Check for cursor movement
    else if (evtType == UserEvent.LEFT || evt.getSecondaryType() == UserEvent.LEFT || evt.getTernaryType() == UserEvent.LEFT)
    {
      if (insertionPoint > 0)
        insertionPoint--;
      lastCursorChangeTime = Sage.time();
      appendToDirty(true);
      return true;
    }
    else if (evtType == UserEvent.RIGHT || evt.getSecondaryType() == UserEvent.RIGHT || evt.getTernaryType() == UserEvent.RIGHT)
    {
      if (theString != null && insertionPoint < theString.length())
        insertionPoint++;
      lastCursorChangeTime = Sage.time();
      appendToDirty(true);
      return true;
    }
    else if (evt.isKB())
    {
      if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE)
      {
        if (theString.length() > 0 && insertionPoint < theString.length())
        {
          setText(theString.substring(0, insertionPoint) + theString.substring(insertionPoint + 1));
        }
        lastCursorChangeTime = Sage.time();
        return true;
      }
      else if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_HOME)
      {
        if (insertionPoint != 0)
        {
          insertionPoint = 0;
          lastCursorChangeTime = Sage.time();
          appendToDirty(true);
        }
        return true;
      }
      else if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_END)
      {
        insertionPoint = theString.length();
        lastCursorChangeTime = Sage.time();
        appendToDirty(true);
        return true;
      }
      else if (evt.getKeyChar() != 0)
      {
        char keyChar = evt.getKeyChar();
        if (keyChar != '\r' && keyChar != '\n')
        {
          if (keyChar == '\b')
          {
            if (theString.length() > 0 && insertionPoint > 0)
            {
              char removeChar = theString.charAt(insertionPoint - 1);
              insertionPoint--;
              setText(theString.substring(0, insertionPoint) + theString.substring(insertionPoint + 1));
              if (textRenderOffset > 0)
                textRenderOffset -= dynamicFont.charWidth(removeChar);
            }
            lastCursorChangeTime = Sage.time();
            return true;
          }
          else if (!Character.isISOControl(keyChar))// we want to skip control characters
          {
            setText(theString.substring(0, insertionPoint) + keyChar + theString.substring(insertionPoint));
            insertionPoint++;
            lastCursorChangeTime = Sage.time();
            return true;
          }
        }
      }
      else if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_INSERT && evt.getKeyModifiers() == java.awt.event.KeyEvent.SHIFT_MASK)
      {
        // Paste test into the text widget
        try
        {
          java.awt.datatransfer.Transferable xfer = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
          if (xfer != null)
          {
            Object strData = xfer.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
            if (strData != null)
            {
              String insStr = strData.toString();
              setText(theString.substring(0, insertionPoint) + insStr + theString.substring(insertionPoint));
              insertionPoint += insStr.length();
              lastCursorChangeTime = Sage.time();
              return true;
            }
          }
        }
        catch (Exception e)
        {
          System.out.println("Error pasting from clipboard of:" + e);
          e.printStackTrace();
        }
      }
    }
    else if (evtType == UserEvent.DELETE)
    {
      // I changed the DELETE commmand to operate as backspace instead since that's far more useful
      if (theString.length() > 0 && insertionPoint > 0)
      {
        char removeChar = theString.charAt(insertionPoint - 1);
        insertionPoint--;
        setText(theString.substring(0, insertionPoint) + theString.substring(insertionPoint + 1));
        if (textRenderOffset > 0)
          textRenderOffset -= dynamicFont.charWidth(removeChar);
      }
      lastCursorChangeTime = Sage.time();
      return true;
    }
    return false;
  }

  public void setCursorLocation(int x, int y)
  {
    if (theString != null && theString.length() > 0 && glyphVectorMatch != null && glyphVectorMatch.length > 0)
    {
      x += textRenderOffset;
      FloatInsets fins = getInsets();
      double lastPosition = 0;
      for (int i = 0; i < glyphVectorMatch[0].getNumGlyphs(); i++)
      {
        if (lastPosition + (glyphVectorMatch[0].getGlyphPosition(i) - lastPosition)/2 + fins.left > x)
        {
          insertionPoint = Math.max(0, i - 1);
          lastCursorChangeTime = Sage.time();
          appendToDirty(true);
          return;
        }
        lastPosition = glyphVectorMatch[0].getGlyphPosition(i);
      }
      if (layoutVisibleAdvance[0].floatValue() - (layoutVisibleAdvance[0].floatValue() - lastPosition)/2 + fins.left > x)
        insertionPoint = theString.length() - 1;
      else
        insertionPoint = theString.length();
      lastCursorChangeTime = Sage.time();
      appendToDirty(true);
    }
  }

  // This should be set to true for text input widgets so the cursor shows up after any trailing whitespace
  protected boolean showTrailingWhitespace = false;

  protected UIManager uiMgr;
  protected String theString;
  protected float vAlign = 0.5f;
  protected float hAlign = 0.5f;
  protected MetaFont myFont;
  protected MetaFont dynamicFont;
  //	protected java.awt.FontMetrics myMetrics;
  protected Float[] layoutVisibleAdvance;
  protected String[] textMatch;
  protected MetaFont.GlyphVector[] glyphVectorMatch;
  protected Object layoutCacheLock = new Object();
  protected int cachedWidth;
  protected int cachedHeight;
  protected boolean cacheWrapped;
  protected boolean truncatedWidth;
  protected boolean singleLine = false;
  protected boolean fitToSize = false;
  protected int borderSize = 0;
  protected int dynFontCode;
  protected boolean hideText;
  protected boolean shadowText;
  private int crossFadeDuration;

  protected boolean disableFontScaling;

  protected float actualPreferredWidth;

  protected java.util.ArrayList cachedRenderOps;
  protected boolean validRenderCache;
  protected java.awt.geom.Rectangle2D.Float cachedBounds;
  protected java.awt.geom.Rectangle2D.Float cachedClipRect;
  private java.util.ArrayList fadeOutRops; // used for cross-fade effect
  private boolean crossFadeNow;
  private EffectTracker crossEffectOut;
  private EffectTracker crossEffectIn;

  // A couple things are slightly different in this case
  protected boolean subtitleText;

  // Text input stuff
  protected boolean editable;
  protected int insertionPoint;
  protected long lastCursorChangeTime;
  // This is the horizontal shift we're applying to the text in order to get the cursor to render in a visible location
  protected float textRenderOffset;
}
