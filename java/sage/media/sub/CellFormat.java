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
package sage.media.sub;

import java.awt.Color;

/**
 * Describe the format information used by 708 captions on a cell-by-cell basis.
 *
 * @author codefu@google.com (John McDole)
 */
public class CellFormat {
  /*
   * Inner notes about layout; we're using 64 bit (long) because we pop the 32 bit boundary fast,
   * and I can see that going further with borders, etc. All setters are package specific for a
   * reason, while all getters are public so other packages can read format values.
   *
   * [Name : Bits]
   * Foreground : 6 -- byte 0
   * ForeOpacity: 2
   * Background : 6 -- byte 1
   * BackOpacity: 2
   * Edge Color : 6 -- byte 2
   * Underline  : 1
   * Italic     : 1
   * Edge Type  : 3 -- byte 3
   * Font       : 3
   * Size       : 2
   * Offset     : 2 -- byte 4
   * WindowId   : 4 // Doesn't change substance, is just a "signal" in the raster. 0 == invalid
   *
   * TODO(codefu): Future bit planning border : 4 - up, right, down, left bit field for drawing a
   * border, in which case: borderColor: 6
   */

  public static int getBackground(long cellFormat) {
    return (int) ((cellFormat >> 8) & 0x3F);
  }

  private static Color getColor(int internal, int opacity) {
    // scale from 2 bit to 8 bit.
    int r = (((internal >> 4) & 0x3) * 255) / 3;
    int g = (((internal >> 2) & 0x3) * 255) / 3;
    int b = (((internal) & 0x3) * 255) / 3;
    return new Color(r, g, b, opacity);
  }

  public static Color getBackgroundAwtColor(long cellFormat) {
    return getColor(getBackground(cellFormat), getBackgroundOpacity(cellFormat).toInt());
  }

  public static DTVCCOpacity getBackgroundOpacity(long cellFormat) {
    return DTVCCOpacity.from((int) ((cellFormat >> 14) & 0x3));
  }

  public static int getEdgeColor(long cellFormat) {
    return (int) ((cellFormat >> 16) & 0x3F);
  }

  public static Color getEdgeAwtColor(long cellFormat) {
    // 708 note; Edge shares opaicity with foreground
    return getColor(getEdgeColor(cellFormat), getForegroundOpacity(cellFormat).toInt());
  }

  public static DTVCCBorderType getEdgeType(long cellFormat) {
    return DTVCCBorderType.from((int) ((cellFormat >> 24) & 0x7));
  }

  public static DTVCCFontType getFont(long cellFormat) {
    return DTVCCFontType.from((int) ((cellFormat >> 27) & 0x7));
  }

  public static int getForeground(long cellFormat) {
    return (int) (cellFormat & 0x3F);
  }

  public static Color getForegroundAwtColor(long cellFormat) {
    return getColor(getForeground(cellFormat), getForegroundOpacity(cellFormat).toInt());
  }

  public static DTVCCOpacity getForegroundOpacity(long cellFormat) {
    return DTVCCOpacity.from((int) ((cellFormat >> 6) & 0x3));
  }

  public static boolean getItalic(long cellFormat) {
    return (cellFormat & 0x800000) == 0x800000;
  }

  public static DTVCCOffset getOffset(long cellFormat) {
    return DTVCCOffset.from((int) ((cellFormat >> 32) & 0x3));
  }

  public static DTVCCSize getSize(long cellFormat) {
    return DTVCCSize.from((int) ((cellFormat >> 30) & 0x3));
  }

  public static boolean getUnderline(long cellFormat) {
    return (cellFormat & 0x400000) == 0x400000;
  }

  /**
   * Return the window ID for this cell, give as a range from -1,0..8 (-1 == invalid)
   * @param cellFormat
   * @return window id
   */
  public static int getWindowID(long cellFormat) {
    return ((int) ((cellFormat >> 34) & 0xF)) - 1;
  }

  public static long setBackground(long cellFormat, byte bgColor, DTVCCOpacity bgOpacity) {
    return cellFormat & ~0xFF00L | (bgColor & 0x3f) << 8 | (bgOpacity.ordinal() & 0x3) << 14;
  }

  public static long setEdgeColor(long cellFormat, byte edgeColor) {
    return cellFormat & ~0x3F0000L | (edgeColor & 0x3f) << 16;
  }

  public static long setEdgeType(long cellFormat, DTVCCBorderType edgeType) {
    return cellFormat & ~0x7000000L | (edgeType.ordinal() & 0x7) << 24;
  }

  public static long setForeground(long cellFormat, byte fgColor, DTVCCOpacity fgOpacity) {
    return cellFormat & ~(long) 0xFF | fgColor & 0x3f | (fgOpacity.ordinal() & 0x3) << 6;
  }

  public static long setFormatting(long cellFormat, boolean underline, boolean italic) {
    return cellFormat & ~0xC00000L | (underline ? 0x400000 : 0) | (italic ? 0x800000 : 0);
  }

  public static long setPen(
      long cellFormat, DTVCCFontType font, DTVCCSize dTVCCSize, DTVCCOffset dTVCCOffset) {
    return cellFormat & ~0x3F8000000L | (font.ordinal() & 0x7) << 27
        | (long)(dTVCCSize.ordinal() & 0x3) << 30 | (long)(dTVCCOffset.ordinal() & 0x3) << 32;
  }

  /**
   * This format shouldn't affect rendering, other than to give it a hint at boundaries
   */
  static long setWindowID(long cellFormat, int id) {
    id++; // switch to ones-based id's, zero is used as invalid.
    return cellFormat & ~0x3C00000000L | (long)(id & 0xF) << 34;
  }
}
