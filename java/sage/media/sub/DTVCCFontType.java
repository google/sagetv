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

public enum DTVCCFontType {
  DEFAULT,
  MONOSPACED_SERIF,
  PROPORTIONAL_SERIF,
  MONOSPACED_SANS,
  PROPORTIONAL_SANS,
  CASUAL,
  CURSIVE,
  SMALLCAPS;

  public static DTVCCFontType from(int data) {
    switch (data) {
      case 0: return DEFAULT;
      case 1: return MONOSPACED_SERIF;
      case 2: return PROPORTIONAL_SERIF;
      case 3: return MONOSPACED_SANS;
      case 4: return PROPORTIONAL_SANS;
      case 5: return CASUAL;
      case 6: return CURSIVE;
      case 7: return SMALLCAPS;
    }
    return DEFAULT;
  }
}