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

import sage.media.sub.CCSubtitleHandler;
import sage.media.sub.CellFormat;
import sage.media.sub.DTVCCBorderType;
import sage.media.sub.DTVCCFontType;
import sage.media.sub.DTVCCOpacity;
import sage.media.sub.DTVCCSize;
import sage.media.sub.DTVCCWindow;
import sage.media.sub.RollupAnimation;
import sage.media.sub.RollupAnimation.RollupWindow;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Narflex
 */
public class ZCCLabel extends ZLabel
{
  /** Creates a new instance of ZCCLabel */
  public ZCCLabel(ZRoot inReality, boolean isCC)
  {
    this(inReality, isCC, null);
  }
  public ZCCLabel(ZRoot inReality, boolean isCC, MetaFont inFont)
  {
    super(inReality, "", inFont);
    loadCCColors();
    cached708WindowOps = new ArrayList<List<RenderingOp>>(DTVCCWindow.MAX_WINDOWS);
    cached708WindowRects = new ArrayList<java.awt.geom.Rectangle2D.Float>(DTVCCWindow.MAX_WINDOWS);
    for(int i = 0; i < DTVCCWindow.MAX_WINDOWS; i++) {
      cached708WindowOps.add(i, new ArrayList<RenderingOp>());
      cached708WindowRects.add(i, null);
    }

    cc = isCC;
    subtitleText = true;
    mouseTransparency = true;
    if (cc)
    {
      setTextShadow(false);
      setSingleLine(true);
      setDisableFontScaling(false);
      setFont(uiMgr.getCachedMetaFont(uiMgr.get("cc/font_name", Sage.WINDOWS_OS ? "Lucida Console" :
        (Sage.MAC_OS_X ? "Monaco" : "Monospaced")), uiMgr.getInt("cc/font_style", MetaFont.BOLD),
        uiMgr.getInt("cc/font_size", 20), uiMgr));
      setHAlign(0);
      setVAlign(0.5f);
    }
    else
    {
      setForegroundShadow(java.awt.Color.decode(uiMgr.get("subtitle/text_shadow_color", "0x000000")));
      setSingleLine(false);
      setTextShadow(uiMgr.getBoolean("subtitle/shadow_text", true));
      setDisableFontScaling(true);
      setFont(uiMgr.getCachedMetaFont(uiMgr.get("subtitle/font_name", "Arial"), uiMgr.getInt("subtitle/font_style", MetaFont.BOLD),
          uiMgr.getInt("subtitle/font_size", 26), uiMgr));
      setVAlign(uiMgr.getFloat("subtitle/valign", 0.5f));
    }
    debugCC = sage.Sage.getBoolean("cc_debug", false);
  }

  public boolean setText(String inText)
  {
    // Check for font size changes right now
    int targetSize;
    int targetStyle;
    String targetFace;
    if (cc)
    {
      targetFace = uiMgr.get("cc/font_name", Sage.WINDOWS_OS ? "Lucida Console" : (Sage.MAC_OS_X ? "Monaco" : "Monospaced"));
      targetStyle = uiMgr.getInt("cc/font_style", MetaFont.BOLD);
      targetSize = uiMgr.getInt("cc/font_size", 20);
    }
    else
    {
      targetFace = uiMgr.get("subtitle/font_name", "Arial");
      targetStyle = uiMgr.getInt("subtitle/font_style", MetaFont.BOLD);
      targetSize = uiMgr.getInt("subtitle/font_size", 26);
    }
    boolean rv = false;
    if (myFont == null || (!targetFace.equals(myFont.getName()) || targetStyle != myFont.getStyle() || targetSize != myFont.getSize()))
    {
      setFont(uiMgr.getCachedMetaFont(targetFace, targetStyle, targetSize, uiMgr));
      rv = true;
    }
    boolean superRv = super.setText(inText);
    if (!superRv && rv)
    {
      // Text didn't change, but the font did so we need to force a refresh here
      synchronized (layoutCacheLock)
      {
        theString = inText;
        layoutVisibleAdvance = null;
        appendToDirty(false);
      }
    }
    return superRv || rv;
  }

  // true if this is for rendering CC text instead of subtitle text
  public boolean isCC() { return cc; }

  @Override
  public void buildRenderingOps(java.util.ArrayList oldOpList, java.awt.geom.Rectangle2D.Float clipRect,
      int diffuseColor, float alphaFactor, float xoff, float yoff, int flags)
  {
    @SuppressWarnings("unchecked")
    ArrayList<RenderingOp> opList = oldOpList;
    if (!cc)
    {
      if (theString == null || theString.length() == 0) return;
      setHAlign(uiMgr.getFloat("subtitle/halign", 0.5f));
      setForeground(java.awt.Color.decode(uiMgr.get("subtitle/text_color", "0xF0F0F0")));
      super.buildRenderingOps(opList, clipRect, diffuseColor, alphaFactor, xoff, yoff, flags);
      return;
    }
    // If we're not at least 40% of the display area, then disable CC display
    if (getWidth() < reality.getWidth()/2 || getHeight() < reality.getHeight()/2)
      return;
    FloatInsets ins = getInsets();
    boolean dothecalc;
    RollupAnimation rollem = null;
    char[][] screenData;
    long[][] screenCellData;
    synchronized (layoutCacheLock)
    {
      rollem = rollupAnimation;
      if (debugCC) System.out.println("Processing animations: " + rollem);
      rollupAnimation = null;
      screenData = this.ccdata;
      screenCellData = this.ccCellData;
      dothecalc = layoutVisibleAdvance == null || (lastRealityHeight != reality.getRoot().getHeight()) ||
          cachedWidth != Math.round(boundsf.width) || cachedHeight != Math.round(boundsf.height);/* || (cachedWidth < parent.boundsf.width && cacheWrapped) ||
				(prefSize.width > parent.boundsf.width) || (cachedHeight > parent.boundsf.height);*/

      if(abortRollup) {
        if(debugCC) System.out.println("ROLLUP ABORTING; FALLING BEHIND?");
        rollupEffect = null;
        rollupOps = null;
        clearCachedOps();
        screenData = oldccdata;
        screenCellData = oldccCellData;
        dothecalc = true; // Roll-ups were done last, force a refresh of the display memory
      }
      if (rollupEffect != null) {
        if (!rollupEffect.isActive()) {
          if(debugCC) System.out.println("ROLLUP COMPLETE; FALLING THROUGH.");
          rollupEffect = null;
          rollupOps = null;
          dothecalc = true; // Roll-ups were done last, force a refresh of the display memory
        } else {
          // Don't draw till the roll up has been handled.
          if(debugCC) System.out.println("ROLLUP CONTINUED.");
          opList.add(new RenderingOp(rollupEffect, clipRect608, 0f, 0f));
          opList.addAll(rollupOps);
          opList.add(new RenderingOp(null));
          // Make sure to perform some post-rollup/abort logic and drawing.
          if(lastDrawModification != modificationCount) appendToDirty(false);
          return;
        }
      }
      if (dothecalc)
      {
        if (rollem != null && !abortRollup) {
          if (debugCC) System.out.println("ROLLUP SIGNALED!");
          generateRollupOps(opList, rollem);
          rollupAnimationLast = rollem;
          return; // wait till we're done roll'n up.
        }
        abortRollup = false;

        java.util.ArrayList<Float> layoutVec = Pooler.getPooledArrayListType();
        java.util.ArrayList<String> textVec = Pooler.getPooledArrayListType();
        java.util.ArrayList<MetaFont.GlyphVector> gvecList = Pooler.getPooledArrayListType();
        java.util.ArrayList<Float> xOffVec = Pooler.getPooledArrayListType();
        java.util.ArrayList<Float> yOffVec = Pooler.getPooledArrayListType();
        java.util.ArrayList<Long> textStyleVec = Pooler.getPooledArrayListType();
        java.util.ArrayList<java.util.ArrayList<Integer>> transparentSpaceVec = Pooler.getPooledArrayListType();
        cacheWrapped = false;

        if (cachedFontMate != dynamicFont)
        {
          cachedItalicFont = dynamicFont.deriveFont(MetaFont.ITALIC | dynamicFont.getStyle(), reality.getUIMgr());
        }

        if(is708data) {
          layout708(screenData, screenCellData, layoutVec, textVec, gvecList, xOffVec, yOffVec,
              textStyleVec, transparentSpaceVec);
        } else {
          layout608(screenData, layoutVec, textVec, gvecList, xOffVec, yOffVec,
              textStyleVec, transparentSpaceVec); //screenCellData
        }

        layoutVisibleAdvance = layoutVec.toArray(new Float[0]);
        textMatch = textVec.toArray(Pooler.EMPTY_STRING_ARRAY);
        glyphVectorMatch = gvecList.toArray(new MetaFont.GlyphVector[0]);
        textStyles = textStyleVec.toArray(new Long[0]);
        xOffsets = xOffVec.toArray(new Float[0]);
        yOffsets = yOffVec.toArray(new Float[0]);
        transSpaces = transparentSpaceVec.toArray(new java.util.ArrayList[0]);
        Pooler.returnPooledArrayList(layoutVec);
        Pooler.returnPooledArrayList(textVec);
        Pooler.returnPooledArrayList(gvecList);
        Pooler.returnPooledArrayList(xOffVec);
        Pooler.returnPooledArrayList(yOffVec);
        Pooler.returnPooledArrayList(textStyleVec);
        Pooler.returnPooledArrayList(transparentSpaceVec);

        truncatedWidth = false;
        if (reality.isIntegerPixels())
        {
          prefSize.height = (float)Math.ceil(prefSize.height);
          prefSize.width = (float)Math.ceil(prefSize.width);
        }
        cachedWidth = Math.round(boundsf.width);
        cachedHeight = Math.round(boundsf.height);
        clearCachedOps();
      }
      if (layoutVisibleAdvance.length == 0)
        return;
    }
    setForeground(java.awt.Color.decode(uiMgr.get("cc/text_color", "0xF0F0F0")));
    java.awt.geom.Rectangle2D.Float trueBoundsf = getTrueBoundsf();
    java.awt.geom.Rectangle2D viewableArea = trueBoundsf;
    java.awt.Color textColor = fgColor;
    // NOTE: This won't refresh the ops on only a shadow color change, but that should be OK
    if (!cachedRenderOps.isEmpty() && SageRenderer.floatRectEquals(trueBoundsf, cachedBounds) &&
        SageRenderer.floatRectEquals(clipRect, cachedClipRect) &&
        (textColor == null || fgColor.equals(textColor)) &&
        //			MathUtils.isTranslateOnlyMatrix(renderXform) &&
        alphaFactor == 1.0f)
    {
      opList.addAll(cachedRenderOps);
      return;
    }
    clearCachedOps();
    xoff += boundsf.x;
    yoff += boundsf.y;
    //		renderXform = (javax.vecmath.Matrix4f) renderXform.clone();
    //		MathUtils.translateMatrix(renderXform, boundsf.x, boundsf.y);
    //		float orgclipx = clipRect.x, orgclipy = clipRect.y;
    //		clipRect.x -= boundsf.x;
    //		clipRect.y -= boundsf.y;
    float trueY = trueBoundsf.y;
    // Each row of text is drawn vertically centered in a row that is 1/15 the height of the total area
    float rowHeight = trueBoundsf.height / sage.media.sub.CCSubtitleHandler.CC_ROWS;
    if (reality.isIntegerPixels())
      rowHeight = (float)Math.floor(rowHeight);
    float currY = (boundsf.height - sage.media.sub.CCSubtitleHandler.CC_ROWS * rowHeight)*0.5f + ins.top;
    int cols = sage.media.sub.CCSubtitleHandler.CC_COLS;
    if(is708data && VideoFrame.getInstance().getAspectRatioMode() > 1.40) {
      cols = sage.media.sub.CCSubtitleHandler.CC_HD_COLS;
    }
    float charWidth = trueBoundsf.width / (cols + 2); // for the blank at each end
    if (reality.isIntegerPixels())
      charWidth = (float)Math.floor(charWidth);
    float currX = ins.left;
    synchronized (layoutCacheLock)
    {
      if(is708data) {
        generateOps708(screenData, screenCellData, clipRect, alphaFactor, xoff, yoff, rowHeight, currY, charWidth, currX);
      } else {
        generateOps608(clipRect, alphaFactor, xoff, yoff, rowHeight, currY, charWidth, currX);
      }
    }

    if (rollem != null) {
      generateRollupOps(opList, rollem);
      rollupAnimationLast = rollem;
      appendToDirty(false); // we got here due to an abort, which is itself a roll up.
    } else {
      opList.addAll(cachedRenderOps);
      lastDrawModification = modificationCount;
    }

    cachedBounds.setFrame(trueBoundsf);
    cachedClipRect.setFrame(clipRect);
    //		clipRect.x = orgclipx;
    //		clipRect.y = orgclipy;

    // Clear our cached ops if they were from a scale or we have modified transparency from a parent
    if (/*!MathUtils.isTranslateOnlyMatrix(renderXform) ||*/ alphaFactor < 1.0f)
    {
      clearCachedOps();
    }
  }

  /**
   * Clear out the cached rendering ops and any window-associated ops.
   */
  private void clearCachedOps() {
    cachedRenderOps.clear();
    for(List<RenderingOp> ops : cached708WindowOps) {
      ops.clear();
    }
    for(int i = 0; i < 8; i++) {
      cached708WindowRects.set(i, null);
    }
  }

  private void generateOps608(java.awt.geom.Rectangle2D.Float clipRect, float alphaFactor,
      float xoff, float yoff, float rowHeight, float currY, float charWidth, float currX) {
    if (reality.isIntegerPixels())
      currY = (float)Math.floor(currY);
    // First go through and draw all of the background regions
    float lastYValue = -1;
    float startX = -1;
    float lastEndX = -1;
    for (int i = 0; i < glyphVectorMatch.length; i++)
    {
      if (lastYValue != yOffsets[i].floatValue())
      {
        // We're on a different row, close out the last one
        if (startX >= 0)
        {
          cachedRenderOps.add(new RenderingOp(new SageRenderer.ShapeDescription(
              2 * charWidth + (lastEndX - startX),
              rowHeight, java.awt.Color.black),
              0.6f,
              clipRect, xoff + currX + Math.max(0, startX - charWidth), yoff + currY + lastYValue));
        }
        startX = xOffsets[i].floatValue();
        lastYValue = yOffsets[i].floatValue();
        lastEndX = startX + layoutVisibleAdvance[i].floatValue();
      }
      else
      {
        lastEndX = xOffsets[i].floatValue() + layoutVisibleAdvance[i].floatValue();
      }
      java.util.ArrayList currTrans = transSpaces[i];
      if (currTrans != null)
      {
        // We have a transparent space in this current text run so we render the first part
        // of it now and update the values to handle the end part of it
        for (int j = 0; j < currTrans.size(); j++)
        {
          int charOffset = ((Integer) currTrans.get(j)).intValue();
          if (charOffset == 0 && startX == xOffsets[i].floatValue())
          {
            int runLength = findRunLength(currTrans, j);
            // Space at the beginning, just shift over the startX by a char
            // 1 pixel offset so we don't have the space go right up to the edge of the next char
            startX += (glyphVectorMatch[i].getNumGlyphs() == 1) ?
                glyphVectorMatch[i].getAdvance() : glyphVectorMatch[i].getGlyphPosition(runLength) - 1 +
                charWidth;
                j += runLength - 1;
                if (reality.isIntegerPixels())
                  startX = (float) Math.floor(startX);
          }
          else
          {
            // Now render the background up until the transparent space
            float transXStart = lastEndX - layoutVisibleAdvance[i].floatValue() +
                glyphVectorMatch[i].getGlyphPosition(charOffset);
            if (reality.isIntegerPixels())
              transXStart = (float) Math.floor(transXStart);
            cachedRenderOps.add(new RenderingOp(new SageRenderer.ShapeDescription(
                charWidth + (transXStart - startX),
                rowHeight, java.awt.Color.black),
                0.6f,
                clipRect, xoff + currX + Math.max(0, startX - charWidth), yoff + currY + lastYValue));
            // If it's the last char, then clear the current text progress..otherwise adjust it
            if (charOffset + 1 == glyphVectorMatch[i].getNumGlyphs())
            {
              // Trailing transparent space...just terminate our progress
              lastYValue = lastEndX = startX = -1;
              break; // there shouldn't be anything else in the list anyways
            }
            else
            {
              int runLength = findRunLength(currTrans, j);
              startX = transXStart + (glyphVectorMatch[i].getGlyphPosition(charOffset + runLength) -
                  glyphVectorMatch[i].getGlyphPosition(charOffset)) - 1 + charWidth;
              if (reality.isIntegerPixels())
                startX = (float) Math.floor(startX);
              j += runLength - 1;
            }
          }
        }
      }
    }
    // Close out the last one
    if (startX >= 0)
    {
      cachedRenderOps.add(new RenderingOp(new SageRenderer.ShapeDescription(
          2 * charWidth + (lastEndX - startX), rowHeight, java.awt.Color.black),
          0.6f,
          clipRect, xoff + currX + Math.max(0, startX - charWidth), yoff + currY + lastYValue));
    }
    // Center the text vertically within each row if there's extra room
    if (dynamicFont.getHeight() < rowHeight)
      currY += (rowHeight - dynamicFont.getHeight()) / 2;
    if (reality.isIntegerPixels())
      currY = (float)Math.floor(currY);
    // Now we go through and render the text
    for (int i = 0; i < glyphVectorMatch.length; i++)
    {
      // Now add the op for the text
      java.awt.Color currColor = fgColor;
      long currStyle = textStyles[i].longValue();
      if ((currStyle & sage.media.sub.CCSubtitleHandler.WHITE_MASK) == sage.media.sub.CCSubtitleHandler.WHITE_MASK)
        currColor = java.awt.Color.white;
      else if ((currStyle & sage.media.sub.CCSubtitleHandler.BLUE_MASK) == sage.media.sub.CCSubtitleHandler.BLUE_MASK)
        currColor = java.awt.Color.blue;
      else if ((currStyle & sage.media.sub.CCSubtitleHandler.CYAN_MASK) == sage.media.sub.CCSubtitleHandler.CYAN_MASK)
        currColor = java.awt.Color.cyan;
      else if ((currStyle & sage.media.sub.CCSubtitleHandler.MAGENTA_MASK) == sage.media.sub.CCSubtitleHandler.MAGENTA_MASK)
        currColor = java.awt.Color.magenta;
      else if ((currStyle & sage.media.sub.CCSubtitleHandler.GREEN_MASK) == sage.media.sub.CCSubtitleHandler.GREEN_MASK)
        currColor = java.awt.Color.green;
      else if ((currStyle & sage.media.sub.CCSubtitleHandler.YELLOW_MASK) == sage.media.sub.CCSubtitleHandler.YELLOW_MASK)
        currColor = java.awt.Color.yellow;
      else if ((currStyle & sage.media.sub.CCSubtitleHandler.RED_MASK) == sage.media.sub.CCSubtitleHandler.RED_MASK)
        currColor = java.awt.Color.red;
      cachedRenderOps.add(new RenderingOp(new SageRenderer.TextDescription(glyphVectorMatch[i].font,
          glyphVectorMatch[i], textMatch[i]), currColor, alphaFactor, clipRect,
          xoff + currX + xOffsets[i].floatValue(), yoff + currY + yOffsets[i].floatValue()));
      // Add the underline if there
      if ((currStyle & sage.media.sub.CCSubtitleHandler.UNDERLINE_MASK) == sage.media.sub.CCSubtitleHandler.UNDERLINE_MASK)
      {
        float underY = currY + yOffsets[i].floatValue() + rowHeight - (rowHeight - glyphVectorMatch[i].font.getAscent())/2;
        if (reality.isIntegerPixels())
          underY = (float) Math.floor(underY);
        cachedRenderOps.add(new RenderingOp(new SageRenderer.ShapeDescription(
            layoutVisibleAdvance[i].floatValue(), 2, currColor), 1.0f,
            clipRect, xoff + currX + xOffsets[i].floatValue(), yoff + underY));
      }
    }
  }

  /**
   * Render the 708 caption text to the screen.
   * BIG NOTE(codefu): This wont draw perfectly square boxes with text on it. e.g.:
   * back-filled with black, 2 rows, 32 columns.  Text on second row "testing" will produce
   * and image that is stepped.  If we want to draw squares, we need to track the widest character
   * from any font/size that is used.
   */
  private void generateOps708(char[][] screenData, long[][] screenCellData, java.awt.geom.Rectangle2D.Float clipRect, float alphaFactor,
      float xoff, float yoff, float rowHeight, float currY, float charWidth, float currX) {
    if (reality.isIntegerPixels())
      currY = (float)Math.floor(currY);

    // This data should always be different than what we have
    StringBuffer sb = new StringBuffer();
    printCCBuffer("708 CCData to render", screenData, sb, screenCellData, new StringBuffer());
    sb.setLength(0);

    for (int row = 0; row < sage.media.sub.CCSubtitleHandler.CC_ROWS && screenData != null; row++) {
      // start off every row with a reset format
      long lastCellFormat = screenCellData[row][0];

      if (CellFormat.getBackgroundOpacity(lastCellFormat) == DTVCCOpacity.TRANSPARENT
          && screenData[row][0] == 0) {
        lastCellFormat = CellFormat.setForeground(
            lastCellFormat, (byte) CellFormat.getForeground(lastCellFormat), DTVCCOpacity.TRANSPARENT);
      }

      rowOffsets[row] = -1; // meh?
      // translated from older code; basically "where the hell are we"
      float rowStartY = rowHeight * row;
      float textOffset = charWidth; // start one character off the edge for safety

      int maxCols = screenCellData[row].length - 1;
      int lastRenderedCol = -1;
      // Walk every character on a line and generate rendering ops for it.
      for (int col = 0; col < maxCols; col++)
      {
        //|| screenData[row][col] == sage.media.sub.CCSubtitleHandler.TRANSPARENT_SPACE_708
        if (lastCellFormat != screenCellData[row][col]) {
          int windowID = CellFormat.getWindowID(lastCellFormat);
          // Check to see if the previous segment was visible
          if((CellFormat.getForegroundOpacity(lastCellFormat) != DTVCCOpacity.TRANSPARENT ||
              CellFormat.getBackgroundOpacity(lastCellFormat) != DTVCCOpacity.TRANSPARENT)) {
            // Style has changed and we already have text so add the info for this part
            textOffset += render708ops(screenData, screenCellData, clipRect, alphaFactor, xoff,
                yoff, rowHeight, currY, charWidth, currX, sb, lastCellFormat, rowStartY, textOffset,
                lastRenderedCol, row, col);
            lastRenderedCol = col - 1;
          } else {
            // If the last segment wasn't visible, advance forward some set number of spaces.
            addTo708WindowRect(windowID, xoff + currX + textOffset, yoff + rowStartY,
                sb.length() * charWidth, rowHeight);
            textOffset += sb.length() * charWidth;
          }
          sb.setLength(0);
        }

        lastCellFormat = screenCellData[row][col];
        if (CellFormat.getBackgroundOpacity(lastCellFormat) == DTVCCOpacity.TRANSPARENT
            && screenData[row][col] == 0) {
          lastCellFormat = CellFormat.setForeground(
              lastCellFormat, (byte) CellFormat.getForeground(lastCellFormat), DTVCCOpacity.TRANSPARENT);
        }

        // Note: Transparent characters can "cut" into non-transparent backgrounds.
        if (CellFormat.getBackgroundOpacity(lastCellFormat) == DTVCCOpacity.TRANSPARENT
            && (CellFormat.getForegroundOpacity(lastCellFormat) == DTVCCOpacity.TRANSPARENT || screenData[row][col] == 0)) {
          textOffset += charWidth;
        } else if (screenData[row][col] != 0) {
          if (rowOffsets[row] == -1) {
            rowOffsets[row] = col;
          }
          sb.append(screenData[row][col]);
        } else {
          // TODO(codefu) non-printable
          sb.append(' ');
        }
      }
      // See if there was text data left on the row (usually there is)
      if (sb.length() > 0)
      {
        // Ignore empty / transparent strings (could be entire rows)
        if(CellFormat.getForegroundOpacity(lastCellFormat) != DTVCCOpacity.TRANSPARENT ||
            CellFormat.getBackgroundOpacity(lastCellFormat) != DTVCCOpacity.TRANSPARENT) {
          render708ops(screenData, screenCellData, clipRect, alphaFactor, xoff,
              yoff, rowHeight, currY, charWidth, currX, sb, lastCellFormat, rowStartY, textOffset,
              lastRenderedCol, row, maxCols);
        }
      }
      sb.setLength(0);
    }

    // Take all the window-based operations and generate the rendering list.
    for(List<RenderingOp> ops : cached708WindowOps) {
      cachedRenderOps.addAll(ops);
    }
  }

  /**
   * Actually build the rendering ops for the line of text.
   */
  private float render708ops(char[][] screenData, long[][] screenCellData,
      java.awt.geom.Rectangle2D.Float clipRect, float alphaFactor, float xoff, float yoff,
      float rowHeight, float currY, float charWidth, float currX, StringBuffer sb,
      long cellFormat, float rowStartY, float textOffset, int lastRenderedCol, int row,
      int col) {
    int windowID = CellFormat.getWindowID(cellFormat);
    if(windowID == -1) {
      return sb.length() * charWidth;
    }

    DTVCCFontType font = CellFormat.getFont(cellFormat);
    DTVCCSize font_size = CellFormat.getSize(cellFormat);

    Color bgColor = CellFormat.getBackgroundAwtColor(cellFormat);
    Color fgColor = CellFormat.getForegroundAwtColor(cellFormat);
    DTVCCOpacity bgOpacity = CellFormat.getBackgroundOpacity(cellFormat);
    DTVCCOpacity fgOpacity = CellFormat.getForegroundOpacity(cellFormat);
    DTVCCBorderType edgeStyle = CellFormat.getEdgeType(cellFormat);
    Color edgeColor = CellFormat.getEdgeAwtColor(cellFormat);

    if(uiMgr.getBoolean("cc/708/user_override", false) == true) {
      font_size = getUserSizeOverride(font_size, uiMgr.get("cc/708/font_size", null));
      font = getUserFontOverride(font, uiMgr.get("cc/708/font", null));
      fgColor = getUserColorOverride(fgColor, uiMgr.get("cc/708/foreground_color", null));
      fgOpacity = getUserOpacityOverride(fgOpacity, uiMgr.get("cc/708/foreground_opacity", null));
      bgColor = getUserColorOverride(bgColor, uiMgr.get("cc/708/background_color", null));
      bgOpacity = getUserOpacityOverride(bgOpacity, uiMgr.get("cc/708/background_opacity", null));
      edgeStyle = getEdgeTypeOverride(edgeStyle, uiMgr.get("cc/708/edge_style", null));
      edgeColor = getUserColorOverride(edgeColor, uiMgr.get("cc/708/edge_color", null));
    }
    MetaFont currFont =
        fonts708[font.ordinal()][font_size.ordinal()][CellFormat.getItalic(cellFormat) ? 1 : 0];

    String currText = sb.toString();
    MetaFont.GlyphVector gvec = currFont.createGlyphVector(currText);

    // note; advance is the width of the whole string
    float visAdv = gvec.getAdvance();

    if (reality.isIntegerPixels())
      visAdv = (float)Math.floor(visAdv);
    int trimLength = currText.trim().length();
    if(trimLength == 0) {
      visAdv = charWidth * currText.length();
    }

    float leadingSpace = 0;
    float rectExtraWidth = charWidth;
    // Check to see if there was text immediatly prior to us.
    if(lastRenderedCol < 0 || lastRenderedCol != (col - currText.length() - 1)) {
      leadingSpace = charWidth/2;
      if (reality.isIntegerPixels())
        leadingSpace = (float)Math.floor(leadingSpace);
    } else {
      rectExtraWidth /= 2;
      if (reality.isIntegerPixels())
        rectExtraWidth = (float)Math.floor(rectExtraWidth);
    }

    // Check to see if there is text immediately following
    if ((CellFormat.getForegroundOpacity(screenCellData[row][col]) != DTVCCOpacity.TRANSPARENT
        || CellFormat.getBackgroundOpacity(screenCellData[row][col]) != DTVCCOpacity.TRANSPARENT)
        && screenData[row][col] != 0) {
      rectExtraWidth -= charWidth / 2;
      if (reality.isIntegerPixels()) rectExtraWidth = (float) Math.floor(rectExtraWidth);
      rectExtraWidth = Math.max(0.0f, rectExtraWidth);
    }

    // Shape(w, h, color)
    // RenderingOp(shape, alpha, cliprect, translatex, translatey)
    currY += yoff + rowStartY;
    cached708WindowOps.get(windowID).add(new RenderingOp(
        new SageRenderer.ShapeDescription(rectExtraWidth + visAdv, rowHeight, bgColor),
        bgOpacity.toFloat(), clipRect, xoff + currX + Math.max(0, textOffset - leadingSpace),
        currY));
    addTo708WindowRect(windowID, xoff + currX + Math.max(0, textOffset - leadingSpace),
        currY, rectExtraWidth + visAdv, rowHeight);

    if(trimLength > 0) {
      // Center the text vertically within each row if there's extra room
      if (currFont.getHeight() < rowHeight)
        currY += (rowHeight - currFont.getHeight()) / 2;
      if (reality.isIntegerPixels())
        currY = (float)Math.floor(currY);

      float transX = xoff + currX + textOffset;

      float textOpacity = fgOpacity.toFloat();
      switch(fgOpacity) {
        case TRANSLUCENT:
          textOpacity *= -1;
          break;
        case TRANSPARENT:
          textOpacity *= -1;
          break;
        default:
          break;
      }
      // Render edge before rendering main font.  Note; transparency is shared with font.
      switch (edgeStyle) {
        case DEPRESSED:
          cached708WindowOps.get(windowID).add(renderFont(
              clipRect, textOpacity, edgeColor, currFont, currText, gvec, transX + 1,
              currY + 1));
          break;
        case RAISED:
          cached708WindowOps.get(windowID).add(renderFont(
              clipRect, textOpacity, edgeColor, currFont, currText, gvec, transX - 1,
              currY - 1));
          break;
        case SHADOW_LEFT:
          cached708WindowOps.get(windowID).add(renderFont(
              clipRect, textOpacity, edgeColor, currFont, currText, gvec, transX - 2,
              currY + 2));
          break;
        case SHADOW_RIGHT:
          cached708WindowOps.get(windowID).add(renderFont(
              clipRect, textOpacity, edgeColor, currFont, currText, gvec, transX + 2,
              currY + 2));
          break;
        case UNIFORM:
          cached708WindowOps.get(windowID).add(renderFont(
              clipRect, textOpacity, edgeColor, currFont, currText, gvec, transX + 1,
              currY + 1));
          cached708WindowOps.get(windowID).add(renderFont(
              clipRect, textOpacity, edgeColor, currFont, currText, gvec, transX + -1,
              currY - 1));
          break;

        case INVALID:
        case NONE:
        default:
          break;
      }

      // render the text centered in the region... ?
      // starting from textOffset, running visAdvance, for gvec
      cached708WindowOps.get(windowID).add(
          renderFont(clipRect, textOpacity, fgColor, currFont, currText, gvec, transX, currY));
      // Add the underline if there]
      if (CellFormat.getUnderline(cellFormat)) {
        float underY = currY + rowHeight - (rowHeight - currFont.getAscent())/2;
        if (reality.isIntegerPixels())
          underY = (float) Math.floor(underY);
        cached708WindowOps.get(windowID).add(new RenderingOp(
            new SageRenderer.ShapeDescription(visAdv, 2, fgColor), alphaFactor, clipRect,
            transX, yoff + underY));
      }
    }
    return visAdv;
  }

  /**
   * Render a font as some offset.
   * @return
   */
  private RenderingOp renderFont(java.awt.geom.Rectangle2D.Float clipRect, float alphaFactor,
      Color fgColor, MetaFont currFont, String currText, MetaFont.GlyphVector gvec, float transX,
      float transY) {
    return new RenderingOp(
        new SageRenderer.TextDescription(currFont, gvec, currText), fgColor, alphaFactor,
        clipRect, transX, transY);
  }

  /**
   * Expand the 708 window's rectangle to include the new rectangle
   */
  private void addTo708WindowRect(int windowID, float x, float y, float w, float h) {
    if(windowID < 0 || windowID > 7) return;
    java.awt.geom.Rectangle2D.Float rect = cached708WindowRects.get(windowID);
    java.awt.geom.Rectangle2D.Float newRect = new java.awt.geom.Rectangle2D.Float(x, y, w, h);
    if(rect == null) {
      cached708WindowRects.add(windowID, newRect);
    } else {
      rect.add(newRect);
    }
  }

  private DTVCCSize getUserSizeOverride(DTVCCSize size, String override) {
    if (override != null) {
      override = override.toLowerCase();
      if ("standard".equals(override.toLowerCase())) {
        size = DTVCCSize.STANDARD;
      } else if ("small".equals(override.toLowerCase())) {
        size = DTVCCSize.SMALL;
      } else if ("large".equals(override.toLowerCase())) {
        size = DTVCCSize.LARGE;
      }
    }
    return size;
  }

  private DTVCCFontType getUserFontOverride(DTVCCFontType font, String override) {
    if (override != null) {
      override = override.toLowerCase();
      if ("mono_sans".equals(override)) {
        font = DTVCCFontType.MONOSPACED_SANS;
      } else if ("mono_serif".equals(override)) {
        font = DTVCCFontType.MONOSPACED_SERIF;
      } else if ("prop_sans".equals(override)) {
        font = DTVCCFontType.PROPORTIONAL_SANS;
      } else if ("prop_serif".equals(override)) {
        font = DTVCCFontType.PROPORTIONAL_SERIF;
      } else if ("cursive".equals(override)) {
        font = DTVCCFontType.CURSIVE;
      } else if ("casual".equals(override)) {
        font = DTVCCFontType.CASUAL;
      } else if ("smallcaps".equals(override)) {
        font = DTVCCFontType.SMALLCAPS;
      }
    }
    return font;
  }

  private Color getUserColorOverride(Color defaultColor, String selectedColor) {
    // Looks up cc/708/color/<ColorName> so we can tweak colors on the fly
    if (selectedColor != null) {
      selectedColor = uiMgr.get("cc/708/color/" + selectedColor, null);
      if (selectedColor != null) {
        long color;
        try {
          if (selectedColor.toLowerCase().startsWith("0x")) {
            color = Long.parseLong(selectedColor.substring(2), 16);
          } else {
            color = Long.parseLong(selectedColor);
          }
        } catch (NumberFormatException e) {
          color = -1;
        }
        if (color != -1) {
          defaultColor = new Color((int) (color & 0xFFFFFF));
        }
      }
    }
    return defaultColor;
  }

  private DTVCCOpacity getUserOpacityOverride(DTVCCOpacity opacity, String override) {
    if (override != null) {
      override = override.toLowerCase();
      if ("transparent".equals(override)) {
        opacity = DTVCCOpacity.TRANSPARENT;
      } else if ("translucent".equals(override)) {
        opacity = DTVCCOpacity.TRANSLUCENT;
      } else if ("solid".equals(override)) {
        opacity = DTVCCOpacity.SOLID;
      }
    }
    return opacity;
  }

  private DTVCCBorderType getEdgeTypeOverride(DTVCCBorderType edge, String override) {
    if (override != null) {
      override = override.toLowerCase();
      if ("none".equals(override)) {
        edge = DTVCCBorderType.NONE;
      } else if ("raised".equals(override)) {
        edge = DTVCCBorderType.RAISED;
      } else if ("depressed".equals(override)) {
        edge = DTVCCBorderType.DEPRESSED;
      } else if ("drop_shadow_left".equals(override)) {
        edge = DTVCCBorderType.SHADOW_LEFT;
      } else if ("drop_shadow_right".equals(override)) {
        edge = DTVCCBorderType.SHADOW_RIGHT;
      } else if ("uniform".equals(override)) {
        edge = DTVCCBorderType.UNIFORM;
      }
    }
    return edge;
  }

  private void layout608(char[][] screenData, java.util.ArrayList<Float> layoutVec,
      java.util.ArrayList<String> textVec, java.util.ArrayList<MetaFont.GlyphVector> gvecList,
      java.util.ArrayList<Float> xOffVec, java.util.ArrayList<Float> yOffVec,
      java.util.ArrayList<Long> textStyleVec,
      java.util.ArrayList<java.util.ArrayList<Integer>> transparentSpaceVec) {
    float rowHeight = boundsf.height / sage.media.sub.CCSubtitleHandler.CC_ROWS;
    if (reality.isIntegerPixels()) {
      rowHeight = (float) Math.floor(rowHeight);
    }
    float colWidth = boundsf.width / (sage.media.sub.CCSubtitleHandler.CC_COLS + 2);
    if (reality.isIntegerPixels())
    {
      colWidth = (float) Math.floor(colWidth);
    }
    // This data should always be different than what we have
    StringBuffer sb = new StringBuffer();
    printCCBuffer("CCData to process", screenData, sb);

    for (int row = 0; row < sage.media.sub.CCSubtitleHandler.CC_ROWS && screenData != null; row++)
    {
      int lastCharThisRow = -1;
      rowOffsets[row] = -1;
      char currStyle = 0;
      sb.setLength(0);
      // As we progress through a row; if we go over an entry that doesn't add to the text buffer, then we need to increase the text offset
      // and when we finish with the current text buffer we need to add its length to the offset so far
      float textOffset = colWidth;
      java.util.ArrayList<Integer> pendingTransSpaces = null;
      for (int col = 0; col <= sage.media.sub.CCSubtitleHandler.CC_COLS; col++)
      {
        if (col == 0)
          currStyle = 0;
        if ((screenData[row][col] & sage.media.sub.CCSubtitleHandler.FORMAT_MASK) == sage.media.sub.CCSubtitleHandler.FORMAT_MASK)
        {
          // We have a format character; if its the first column then it is the new style.
          // If it's not the first column; then it adds to the style.
          // NOTE: The style 'adding' isn't correct, we need to write something to do this part properly
          char lastStyle = currStyle;
          if (col == 0)
            currStyle = screenData[row][col];
          else
            currStyle = sage.media.sub.CCSubtitleHandler.applyMidRowToFormat(currStyle, screenData[row][col]);
          if (sb.length() > 0)
          {
            if (lastStyle != currStyle)
            {
              // Style has changed and we already have text so add the info for this part
              MetaFont currFont = ((lastStyle & sage.media.sub.CCSubtitleHandler.ITALICS_MASK) == sage.media.sub.CCSubtitleHandler.ITALICS_MASK) ?
                  cachedItalicFont : dynamicFont;
              String currText = sb.toString();
              MetaFont.GlyphVector gvec = currFont.createGlyphVector(currText);
              float visAdv = gvec.getAdvance();
              if (reality.isIntegerPixels())
                visAdv = (float)Math.floor(visAdv);
              if (currText.trim().length() > 0)
              {
                textVec.add(currText);
                gvecList.add(gvec);
                textStyleVec.add(new Long(lastStyle));
                layoutVec.add(new Float(visAdv));
                yOffVec.add(new Float(rowHeight * row));
                xOffVec.add(new Float(textOffset));
                transparentSpaceVec.add(pendingTransSpaces);
                pendingTransSpaces = null;
              } else if(pendingTransSpaces.size() > 0) {
                // no text to display, don't keep this data around.
                pendingTransSpaces = null;
              }
              textOffset += visAdv + colWidth;
              sb.setLength(0);
            }
            else
              sb.append(' ');
          }
          else
            textOffset += colWidth;
        }
        else if (screenData[row][col] != 0)
        {
          if (rowOffsets[row] == -1)
            rowOffsets[row] = col;
          if (screenData[row][col] == sage.media.sub.CCSubtitleHandler.TRANSPARENT_SPACE)
          {
            if (pendingTransSpaces == null)
              pendingTransSpaces = new java.util.ArrayList<Integer>();
            pendingTransSpaces.add(new Integer(sb.length()));
            sb.append(' ');
          }
          else
            sb.append(screenData[row][col]);
          lastCharThisRow = col - 1;
        }
        else if (sb.length() > 0)
          sb.append(' ');
        else
          textOffset += colWidth;
      }
      // See if there was text data left on the row (usually there is)
      if (sb.length() > 0)
      {
        // Strip any blanks off after the last char
        sb.setLength(sb.length() - (sage.media.sub.CCSubtitleHandler.CC_COLS - (lastCharThisRow + 1)));
        if (sb.length() > 0) // make sure its still long enough to use
        {
          String currText = sb.toString();
          MetaFont currFont = ((currStyle & sage.media.sub.CCSubtitleHandler.ITALICS_MASK) == sage.media.sub.CCSubtitleHandler.ITALICS_MASK) ?
              cachedItalicFont : dynamicFont;
          MetaFont.GlyphVector gvec = currFont.createGlyphVector(currText);
          float visAdv = gvec.getAdvance();
          if (reality.isIntegerPixels())
            visAdv = (float)Math.floor(visAdv);
          if (currText.trim().length() > 0)
          {
            textVec.add(currText);
            gvecList.add(gvec);
            textStyleVec.add(new Long(currStyle));
            layoutVec.add(new Float(visAdv));
            yOffVec.add(new Float(rowHeight * row));
            xOffVec.add(new Float(textOffset));
            transparentSpaceVec.add(pendingTransSpaces);
          }
          textOffset += visAdv + colWidth;
        }
      }
    }
  }

  private void layout708(char[][] screenData, long[][] screenCellData,
      java.util.ArrayList<Float> layoutVec, java.util.ArrayList<String> textVec,
      java.util.ArrayList<MetaFont.GlyphVector> gvecList, java.util.ArrayList<Float> xOffVec,
      java.util.ArrayList<Float> yOffVec, java.util.ArrayList<Long> textStyleVec,
      java.util.ArrayList<java.util.ArrayList<Integer>> transparentSpaceVec) {

    // 708 doens't use this function, see generateOps708()
    layoutVec.add(new Float(1));
    return;
  }

  private void generateRollupOps(ArrayList<RenderingOp> opList, RollupAnimation rollem) {
    int rollUpTime = sage.Sage.getInt("cc_rollup_time", 300);
    float rowHeight = boundsf.height / sage.media.sub.CCSubtitleHandler.CC_ROWS;
    if (reality.isIntegerPixels()) {
      rowHeight = (float) Math.floor(rowHeight);
    }

    // NOTE: There is a 99.9% chance of only one roll up animation at a time, even if
    // its 708.

    // Goal: If 708, run through the window-based rendering ops and roll them up.
    // We still run through the other windows (if they so exist) and re-render them.

    boolean is708 = false;
    for(int i = 0; i < rollem.getCount(); i++){
      RollupWindow rollup = rollem.getRollup(i);
      int windowID = rollup.getWindowID();
      if(windowID != -1) {
        is708 = true;
        if(cached708WindowOps.get(windowID).size() == 0) continue;
        // TODO(codefu) : verify there aren't more than one operation per windowid?
        EffectTracker windowAnimation = new EffectTracker((ZPseudoComp) parent, 0, rollUpTime, (byte)0, EffectTracker.SCALE_LINEAR);
        windowAnimation.setClipped(true);
        windowAnimation.setTranslationEffect(0, 0, 0, 0 - (rowHeight)); // rollup by one row
        windowAnimation.setInitialPositivity(false);
        windowAnimation.setPositivity(true);
        java.awt.geom.Rectangle2D.Float clipRect = cached708WindowRects.get(windowID);
        if(debugCC) System.out.println("Window[" + windowID +"] Clip-rect: " + clipRect);
        opList.add(new RenderingOp(windowAnimation, clipRect, 0f, 0f));
        opList.addAll(cached708WindowOps.get(windowID));
        opList.add(new RenderingOp(null));
      } else {
        break;
      }
    }
    // For now, just lump everything together for 608 since line's are recorded yet.
    if(!is708) {
      rollupOps = cachedRenderOps;
    } else {
      return;
    }
    if(rollupOps.isEmpty()) return;
    cachedRenderOps = new ArrayList();
    rollupEffect = new EffectTracker((ZPseudoComp) parent, 0, rollUpTime, (byte)0, EffectTracker.SCALE_LINEAR);
    rollupEffect.setClipped(true);
    rollupEffect.setTranslationEffect(0, 0, 0, 0 - (rowHeight));
    rollupEffect.setInitialPositivity(false);
    rollupEffect.setPositivity(true);
    java.awt.geom.Rectangle2D.Float clipRect = null;
    // TODO(codefu): Figure out 608 clipping rects that make sense.
    for(RenderingOp op : rollupOps) {
      if(clipRect == null) {
        clipRect = op.getDestination();
      } else {
        clipRect.add(op.getDestination());
      }
    }
    if(debugCC) System.out.println("608 Clip-rect: " + clipRect);
    clipRect608 = clipRect;
    opList.add(new RenderingOp(rollupEffect, clipRect, 0f, 0f));
    opList.addAll(rollupOps);
    opList.add(new RenderingOp(null));
  }

  private void printCCBuffer(String header, char[][] screenData, StringBuffer sb) {
    printCCBuffer(header, screenData, sb, null, null);
  }

  private void printCCBuffer(String header, char[][] screenData, StringBuffer sb, long[][] cellData, StringBuffer sbFormat) {
    if(!debugCC || screenData == null) return;
    // Note(codefu): for my own edification and future CC hacking; dump the data.
    final char[] empty = new char[screenData[0].length];
    for (int row = 0; row < screenData.length && screenData != null; row++) {
      if(java.util.Arrays.equals(screenData[row], empty)) continue;
      sb.append(String.format("\r\nRow[%2d]: ", row));
      if(sbFormat != null) {
        sbFormat.append("\r\nRow Format[" + row + "]: ");
      }
      for (int col = 0; col < screenData[row].length; col++) {
        sb.append(Integer.toHexString(screenData[row][col]) + " ");
        if(sbFormat != null) {
          sbFormat.append(Long.toHexString(cellData[row][col]) + " ");
        }
      }
    }
    for (int row = 0; row < screenData.length && screenData != null; row++) {
      if(java.util.Arrays.equals(screenData[row], empty)) continue;
      sb.append(String.format("\r\nRow[%2d]: ", row));
      for (int col = 0; col < screenData[row].length; col++) {
        if (screenData[row][col] >= 32 && screenData[row][col] < 127) {
          sb.append(screenData[row][col]);
        } else if ((!is708data && screenData[row][col] == 0xFF) || (is708data && screenData[row][col] == 0xFFFF)) {
          sb.append("*"); // TS
        } else {
          sb.append("_");
        }
      }
    }
    System.out.println(header + sb.toString());
    if(sbFormat != null) {
      System.out.println(header + sbFormat.toString());
    }
  }

  /**
   * Find the number of neighbor spaces, starting at offset.
   * @param currTrans
   * @return number of neighbor spaces including offset.
   */
  private int findRunLength(java.util.ArrayList currTrans, int offset) {
    int lastOffset = ((Integer) currTrans.get(offset)).intValue();
    int j = offset+1;
    for(; j < currTrans.size(); j++) {
      int thisOffset = ((Integer) currTrans.get(j)).intValue();
      if(thisOffset != lastOffset+1) {
        break;
      }
      lastOffset = thisOffset;
    }
    return j - offset;
  }

  public void setCCData(char[][] ccdata) {
    setCCData(ccdata, null, null, false);
  }

  public void loadCCColors() {
    defaultColor("cc/708/color/Black", "0x000000");
    defaultColor("cc/708/color/White", "0xAAAAAA");
    defaultColor("cc/708/color/Red", "0xAA0000");
    defaultColor("cc/708/color/Green", "0x00AA00");
    defaultColor("cc/708/color/Blue", "0x0000AA");
    defaultColor("cc/708/color/Yellow", "0xAAAA00");
    defaultColor("cc/708/color/Magenta", "0xAA00AA");
    defaultColor("cc/708/color/Cyan", "0x00AAAA");
  }

  private void defaultColor(String colorProp, String defaultColor) {
    if(uiMgr.get(colorProp, null) == null) {
      uiMgr.put(colorProp,defaultColor);
    }
  }

  public void loadFonts() {
    for (DTVCCFontType type : DTVCCFontType.values()) {
      int i = type.ordinal();
      String targetFace = uiMgr.get("cc/708/" + i + "/name", default708FontNames[i]);
      int targetStyle = uiMgr.getInt("cc/708/" + i + "/style", MetaFont.PLAIN);
      for (int size = 0; size < default708FontSizeNames.length; size++) {
        int targetSize = uiMgr.getInt(
            "cc/708/" + i + "/size/" + default708FontSizeNames[size], default708FontSizes[size]);
        if (fonts708[i][size][0] == null || (!targetFace.equals(fonts708[i][size][0].getName())
            || targetStyle != fonts708[i][size][0].getStyle()
            || targetSize != fonts708[i][size][0].getSize())) {
          synchronized (layoutCacheLock) {
            fonts708[i][size][0] =
                UIManager.getCachedMetaFont("fonts/" + targetFace, targetStyle, targetSize, uiMgr);
            // TODO: Get italic version or derive it.
            fonts708[i][size][1] = fonts708[i][size][0].deriveFont(
                MetaFont.ITALIC | fonts708[i][size][0].getStyle(), reality.getUIMgr());
          }
        }
      }
    }
  }

  public void setCCData(char[][] ccdata, long[][] cellData, RollupAnimation animation, boolean is708) {
    // Check for font size changes right now
    int targetSize;
    int targetStyle;
    String targetFace;
    targetFace = uiMgr.get("cc/font_name", Sage.WINDOWS_OS ? "Lucida Console" : (Sage.MAC_OS_X ? "Monaco" : "Monospaced"));
    targetStyle = uiMgr.getInt("cc/font_style", MetaFont.BOLD);
    targetSize = uiMgr.getInt("cc/font_size", 20);
    boolean rv = false;
    if (myFont == null || (!targetFace.equals(myFont.getName()) || targetStyle != myFont.getStyle() || targetSize != myFont.getSize()))
    {
      setFont(uiMgr.getCachedMetaFont(targetFace, targetStyle, targetSize, uiMgr));
    }
    loadFonts();

    synchronized (layoutCacheLock)
    {
      if(animation != null) {
        // TODO(codefu): Check that the animation object doesn't specify overlapping animations;
        // if its new animations and 708 captions, this are just new animations.  For now, we're
        // going with one set of rollups...
        if(rollupEffect != null && rollupEffect.isActive()) {
          // Prior to signaling the abort, make sure we know what the screen should have looked like
          for (int y = 0; y < sage.media.sub.CCSubtitleHandler.CC_ROWS; y++) {
            System.arraycopy(this.ccdata[y], 0, this.oldccdata[y], 0, this.ccdata[y].length);
            System.arraycopy(this.ccCellData[y], 0, this.oldccCellData[y], 0, this.ccdata[y].length);
          }
          this.abortRollup = true;
          if (debugCC) System.out.println("From VF: ABORTING OLD ROLLUP; FALLING BEHIND?");
          printCCBuffer("OLD CC DATA:", oldccdata, new StringBuffer());
        }
        this.rollupAnimation = animation;
        if (debugCC) System.out.println("Received animations: " + animation);
      }

      modificationCount++;
      for (int y = 0; y < sage.media.sub.CCSubtitleHandler.CC_ROWS; y++) {
        System.arraycopy(ccdata[y], 0, this.ccdata[y], 0, this.ccdata[y].length);
        if(cellData != null) {
          System.arraycopy(cellData[y], 0, this.ccCellData[y], 0, this.ccdata[y].length);
        }
      }
      is708data = is708;

      printCCBuffer("NEW CC DATA:", ccdata, new StringBuffer(), is708 ? cellData : null, is708 ? new StringBuffer() : null);

      layoutVisibleAdvance = null;
      appendToDirty(false);
    }
  }

  protected void calculatePrefSize(int availableWidth, int availableHeight)
  {
    if (!cc)
    {
      super.calculatePrefSize(availableWidth, availableHeight);
      return;
    }
    updateDynamicFont();
    FloatInsets ins = getInsets();
    // We use the whole area for CC...but match this to the aspect ratio of the video since this essentially
    // forces any video component to take up the full size....which is fine; but we want to link this with AR
    float currVideoAR = uiMgr.getVideoFrame().getCurrentAspectRatio();
    if (currVideoAR != 0)
    {
      float uiAR = availableWidth / ((float) availableHeight);
      if (currVideoAR < uiAR)
      {
        prefSize.height = availableHeight;
        prefSize.width = Math.round(availableHeight * currVideoAR);
      }
      else
      {
        prefSize.width = availableWidth;
        prefSize.height = Math.round(availableWidth / currVideoAR);
      }
    }
    else
    {
      prefSize.width = availableWidth;
      prefSize.height = availableHeight;
    }
  }

  public void clearCC()
  {
    synchronized (layoutCacheLock)
    {
      boolean isDirty = false;
      for (int y = 0; y < sage.media.sub.CCSubtitleHandler.CC_ROWS; y++)
        for (int x = 0; x <= sage.media.sub.CCSubtitleHandler.CC_COLS; x++)
        {
          if (this.ccdata[y][x] != 0)
          {
            isDirty = true;
            this.ccdata[y][x] = 0;
          }
        }
      isDirty = CCSubtitleHandler.clearFormat(ccCellData) || isDirty;
      if (isDirty)
      {
        layoutVisibleAdvance = null;
        appendToDirty(false);
      }
    }
  }

  public void setDebugCC(boolean bool) {debugCC = bool;}
  public boolean getDebugCC() {return debugCC;}
  private boolean cc;
  private int[] rowOffsets = new int[sage.media.sub.CCSubtitleHandler.CC_ROWS];
  private char[] rowStyles = new char[sage.media.sub.CCSubtitleHandler.CC_ROWS];
  private MetaFont cachedFontMate;
  private MetaFont cachedItalicFont;

  /* 708 Fonts */
  // NOTE: This should match up with the DTVCCFontStyle; default, mono, prop, mono-sans, prop-sans, casual, cursive, smallcaps.  Default is junk.
  boolean is708data = false;
  private String[] default708FontNames = { "CutiveMono-Regular", "CutiveMono-Regular", "PT_Serif-Caption-Web-Regular", "PTM55FT", "PT_Sans-Caption-Web-Regular", "Handlee-Regular", "DancingScript-Regular", "MarcellusSC-Regular"};
  private String[] default708FontSizeNames = { "small", "standard", "large"};
  private int[] default708FontSizes = { 20, 28, 32 }; //TODO(codefu): verify these aren't oversized...
  private MetaFont[][][] fonts708 = new MetaFont[8][3][2]; // [Fonts][Size][Normal/Italic]
  private ArrayList<List<RenderingOp>> cached708WindowOps;
  private ArrayList<java.awt.geom.Rectangle2D.Float> cached708WindowRects;

  private char[][] ccdata = new char[sage.media.sub.CCSubtitleHandler.CC_ROWS][sage.media.sub.CCSubtitleHandler.CC_HD_COLS + 1];
  private char[][] oldccdata = new char[sage.media.sub.CCSubtitleHandler.CC_ROWS][sage.media.sub.CCSubtitleHandler.CC_HD_COLS + 1];

  private long[][] ccCellData = new long[sage.media.sub.CCSubtitleHandler.CC_ROWS][sage.media.sub.CCSubtitleHandler.CC_HD_COLS + 1];
  private long[][] oldccCellData = new long[sage.media.sub.CCSubtitleHandler.CC_ROWS][sage.media.sub.CCSubtitleHandler.CC_HD_COLS + 1];

  private Long[] textStyles = null;
  private Float[] xOffsets = null;
  private Float[] yOffsets = null;
  private java.util.ArrayList[] transSpaces = null;
  boolean debugCC;

  // Support roll-up animations
  private RollupAnimation rollupAnimation;
  private RollupAnimation rollupAnimationLast; // Last processed animation
  ArrayList<RenderingOp> rollupOps;
  private EffectTracker rollupEffect;
  private java.awt.geom.Rectangle2D.Float clipRect608;
  private boolean abortRollup;
  private int modificationCount = 0;
  private int lastDrawModification = 0;
}
