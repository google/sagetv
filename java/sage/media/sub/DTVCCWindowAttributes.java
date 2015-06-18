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


public class DTVCCWindowAttributes {
  static final DTVCCWindowAttributes[] defaultWindows = {
    // NTSC Style PopUp Captions
    new DTVCCWindowAttributes(DTVCCJustify.LEFT, DTVCCDirection.LEFT_TO_RIGHT, DTVCCDirection.BOTTOM_TO_TOP,
        false, DTVCCEffect.SNAP, DTVCCDirection.NONE, 0, 0, DTVCCOpacity.SOLID, DTVCCBorderType.NONE, 0),
        // PopUp cpations w/o black background
        new DTVCCWindowAttributes(DTVCCJustify.LEFT, DTVCCDirection.LEFT_TO_RIGHT, DTVCCDirection.BOTTOM_TO_TOP,
            false, DTVCCEffect.SNAP, DTVCCDirection.NONE, 0, 0, DTVCCOpacity.TRANSPARENT,
            DTVCCBorderType.NONE, 0),
            // NTSC style centered PopUp captions
            new DTVCCWindowAttributes(DTVCCJustify.CENTER, DTVCCDirection.LEFT_TO_RIGHT, DTVCCDirection.BOTTOM_TO_TOP,
                false, DTVCCEffect.SNAP, DTVCCDirection.NONE, 0, 0, DTVCCOpacity.SOLID, DTVCCBorderType.NONE, 0),
                // NTSC style RollUp captions
                new DTVCCWindowAttributes(DTVCCJustify.LEFT, DTVCCDirection.LEFT_TO_RIGHT, DTVCCDirection.BOTTOM_TO_TOP,
                    true, DTVCCEffect.SNAP, DTVCCDirection.NONE, 0, 0, DTVCCOpacity.SOLID, DTVCCBorderType.NONE, 0),
                    // RollUp captions w/o black background
                    new DTVCCWindowAttributes(DTVCCJustify.LEFT, DTVCCDirection.LEFT_TO_RIGHT, DTVCCDirection.BOTTOM_TO_TOP,
                        true, DTVCCEffect.SNAP, DTVCCDirection.NONE, 0, 0, DTVCCOpacity.TRANSPARENT,
                        DTVCCBorderType.NONE, 0),
                        // NTSC style centered RollUp captions
                        new DTVCCWindowAttributes(DTVCCJustify.CENTER, DTVCCDirection.LEFT_TO_RIGHT, DTVCCDirection.BOTTOM_TO_TOP,
                            true, DTVCCEffect.SNAP, DTVCCDirection.NONE, 0, 0, DTVCCOpacity.SOLID, DTVCCBorderType.NONE, 0),
                            // Ticker Tape
                            new DTVCCWindowAttributes(DTVCCJustify.LEFT, DTVCCDirection.TOP_TO_BOTTOM, DTVCCDirection.RIGHT_TO_LEFT,
                                false, DTVCCEffect.SNAP, DTVCCDirection.NONE, 0, 0, DTVCCOpacity.SOLID, DTVCCBorderType.NONE, 0)
  };

  public DTVCCWindowAttributes(DTVCCJustify justification, DTVCCDirection printDirection,
      DTVCCDirection scrollDirection, boolean wordWrap, DTVCCEffect displayEffect,
      DTVCCDirection effectDirection, int effectTimeMS, int fillColor, DTVCCOpacity fillOpacity,
      DTVCCBorderType dTVCCBorderType, int borderColor) {
    this.justification = justification;
    this.print = printDirection;
    this.scroll = scrollDirection;
    this.wordWrap = wordWrap;
    this.effect = displayEffect;
    this.effectDirection = effectDirection;
    this.effectTimeMS = (char) (effectTimeMS < 0 ? 0 : Math.min(effectTimeMS, 25500));
    this.fillColor = (byte) (fillColor & 0x3F);
    this.opacity = fillOpacity;
    this.borderType = dTVCCBorderType;
    this.borderColor = (byte) (borderColor & 0x3F);
  }

  public byte getBorderColor() {
    return borderColor;
  }

  public DTVCCBorderType getBorderType() {
    return borderType;
  }

  public DTVCCEffect getEffect() {
    return effect;
  }

  public DTVCCDirection getEffectDirection() {
    return effectDirection;
  }

  public char getEffectTimeMS() {
    return effectTimeMS;
  }

  public byte getFillColor() { return fillColor; }

  public DTVCCOpacity getFillOpacity() {
    return opacity;
  }
  public DTVCCJustify getJustification() {
    return justification;
  }
  public DTVCCOpacity getOpacity() {
    return opacity;
  }

  public DTVCCDirection getPrint() {
    return print;
  }
  public DTVCCDirection getScroll() {
    return scroll;
  }
  public boolean isWordWrap() {
    return wordWrap;
  }
  @Override
  public String toString() {
    return new String("attr(justify:" + justification.name() + " print:" + print.name()
        + " scroll:" + scroll.name() + " wrap:" + wordWrap + " effect:" + effect.name()
        + " effectDir:" + effectDirection.name() + " speed:" + Integer.toString(effectTimeMS)
        + " fill:" + Integer.toHexString(fillColor) + " opacity:" + opacity.name() + " border:"
        + borderType.name() + " borderColor:" + Integer.toHexString(borderColor) + ")");
  }

  byte borderColor;
  DTVCCBorderType borderType;

  DTVCCEffect effect;
  DTVCCDirection effectDirection;

  char effectTimeMS; // Effect time is specified in half seconds, max 7.5.

  // 4x4x4 RGB color
  byte fillColor;

  DTVCCJustify justification;

  DTVCCOpacity opacity;

  DTVCCDirection print;

  DTVCCDirection scroll;

  boolean wordWrap;
}