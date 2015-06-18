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

public enum DTVCCDirection {
  LEFT_TO_RIGHT,
  RIGHT_TO_LEFT,
  TOP_TO_BOTTOM,
  BOTTOM_TO_TOP,
  NONE;

  public static DTVCCDirection from(int data) {
    switch (data) {
      case 0: return LEFT_TO_RIGHT;
      case 1: return RIGHT_TO_LEFT;
      case 2: return TOP_TO_BOTTOM;
      case 3: return BOTTOM_TO_TOP;
    }
    return NONE;
  }
}