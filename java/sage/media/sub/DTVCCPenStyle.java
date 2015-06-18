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


public class DTVCCPenStyle {
  public DTVCCSize getPenSize() {
    return penSize;
  }

  public DTVCCOffset getdTVCCOffset() {
    return dTVCCOffset;
  }

  public DTVCCTextType getTextInfo() {
    return textInfo;
  }

  public DTVCCFontType getFont() {
    return font;
  }

  public DTVCCBorderType getEdge() {
    return edge;
  }

  public boolean isUnderline() {
    return underline;
  }

  public boolean isItalic() {
    return italic;
  }

  DTVCCSize penSize;
  DTVCCOffset dTVCCOffset;
  DTVCCTextType textInfo;
  DTVCCFontType font;
  DTVCCBorderType edge;
  boolean underline;
  boolean italic;

  @Override
  public String toString() {
    return new String("penStyle(size:" + penSize.name() + " offset:" + dTVCCOffset.name()
        + " textType:" + textInfo.name() + " font:" + font.name() + " edge:" + edge.name()
        + " underline:" + underline + " italic:" + italic + ")");
  }

  public DTVCCPenStyle(DTVCCSize dTVCCSize, DTVCCFontType font, DTVCCOffset dTVCCOffset, boolean italics, boolean underline, DTVCCBorderType edge) {
    this.penSize = dTVCCSize;
    this.font = font;
    this.dTVCCOffset = dTVCCOffset;
    this.italic = italics;
    this.underline = underline;
    this.edge = edge;
    this.textInfo = DTVCCTextType.DIALOG; // not specified in default pen style
  }

  public static final DTVCCPenStyle[] defaultPenStyle = {
    // Default NTSC style
    new DTVCCPenStyle(
        DTVCCSize.STANDARD, DTVCCFontType.DEFAULT,
        DTVCCOffset.NORMALSCRIPT, false, false, DTVCCBorderType.NONE),
    // NTSC style mono w/serif
    new DTVCCPenStyle(
        DTVCCSize.STANDARD, DTVCCFontType.MONOSPACED_SERIF,
        DTVCCOffset.NORMALSCRIPT, false, false, DTVCCBorderType.NONE),
    // NTSC style proportional w/serif
    new DTVCCPenStyle(
        DTVCCSize.STANDARD, DTVCCFontType.PROPORTIONAL_SERIF,
        DTVCCOffset.NORMALSCRIPT, false, false, DTVCCBorderType.NONE),
    // NTSC style mono sans
    new DTVCCPenStyle(
        DTVCCSize.STANDARD, DTVCCFontType.MONOSPACED_SANS,
        DTVCCOffset.NORMALSCRIPT, false, false, DTVCCBorderType.NONE),
    // NTSC style proportional sans
    new DTVCCPenStyle(
        DTVCCSize.STANDARD, DTVCCFontType.PROPORTIONAL_SANS,
        DTVCCOffset.NORMALSCRIPT, false, false, DTVCCBorderType.NONE),
    // Mono sans, bordered text, no background
    new DTVCCPenStyle(
        DTVCCSize.STANDARD, DTVCCFontType.MONOSPACED_SANS,
        DTVCCOffset.NORMALSCRIPT, false, false, DTVCCBorderType.UNIFORM),
    // Proportional sans, bordered text, no background
    new DTVCCPenStyle(
        DTVCCSize.STANDARD, DTVCCFontType.PROPORTIONAL_SANS,
        DTVCCOffset.NORMALSCRIPT, false, false, DTVCCBorderType.UNIFORM)
  };

}