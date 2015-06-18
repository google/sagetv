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


public class DTVCCPen {
  // 4x4x4 RGB color
  byte foregroundColor;
  byte backgroundColor;
  byte edgeColor;

  DTVCCOpacity foregroundOpacity;
  DTVCCOpacity backgroundOpacity;
  // Note: EdgeOpacity is the same as foreground.

  @Override
  public String toString() {
    return new String("pen(foregroundColor:"
        + Integer.toHexString(foregroundColor) + " opacity:" + foregroundOpacity.name()
        + " backgroundColor:" + Integer.toHexString(backgroundColor) + " opacity:"
        + backgroundOpacity.name() + ")");
  }

  DTVCCPen(int foregroundColor, int backgroundColor, int edgeColor,
      DTVCCOpacity foregroundOpacity, DTVCCOpacity backgroundOpacity) {
    this.foregroundColor = (byte) foregroundColor;
    this.foregroundOpacity = foregroundOpacity;
    this.backgroundColor = (byte) backgroundColor;
    this.backgroundOpacity = backgroundOpacity;
    this.edgeColor = (byte) edgeColor;
  }

  // Table 20 CEA-708 default pen styles.
  static final DTVCCPen[] defaultPens = {
    // Default NTSC style
    new DTVCCPen(0x2A, 0, 0, DTVCCOpacity.SOLID, DTVCCOpacity.SOLID),
    // NTSC style mono w/serif
    new DTVCCPen(0x2A, 0, 0, DTVCCOpacity.SOLID, DTVCCOpacity.SOLID),
    // NTSC style proportional w/serif
    new DTVCCPen(0x2A, 0, 0, DTVCCOpacity.SOLID, DTVCCOpacity.SOLID),
    // NTSC style mono sans
    new DTVCCPen(0x2A, 0, 0, DTVCCOpacity.SOLID, DTVCCOpacity.SOLID),
    // NTSC style proportional sans
    new DTVCCPen(0x2A, 0, 0, DTVCCOpacity.SOLID, DTVCCOpacity.SOLID),
    // Mono sans, bordered text, no background
    new DTVCCPen(0x2A, 0, 0, DTVCCOpacity.SOLID, DTVCCOpacity.TRANSPARENT),
    // Proportional sans, bordered text, no background
    new DTVCCPen(0x2A, 0, 0, DTVCCOpacity.SOLID, DTVCCOpacity.TRANSPARENT)
  };
}