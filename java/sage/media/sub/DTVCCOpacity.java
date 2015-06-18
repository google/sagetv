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

public enum DTVCCOpacity {
  SOLID,
  FLASH,
  TRANSLUCENT,
  TRANSPARENT,
  INVALID;

  static DTVCCOpacity from(int data) {
    switch(data) {
      case 0: return DTVCCOpacity.SOLID;
      case 1: return DTVCCOpacity.FLASH;
      case 2: return DTVCCOpacity.TRANSLUCENT;
      case 3: return DTVCCOpacity.TRANSPARENT;
    }
    return INVALID;
  }

  /**
   * Return a 0.0 to 1.0 value for this opacity
   */
  public float toFloat() {
    switch (this) {
      case FLASH:
      case SOLID:
        return 1.0f;
      case TRANSLUCENT:
        return 0.6f;
      default:
      case TRANSPARENT:
        return 0.0f;
    }
  }

  /**
   * Return the 0 to 255 value for this opacity
   */
  public int toInt() {
    switch (this) {
      case FLASH:
      case SOLID:
        return 255;
      case TRANSLUCENT:
        return 153; // 60% 256
      default:
      case TRANSPARENT:
        return 0;
    }
  }
}
