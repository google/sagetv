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

public enum DTVCCEffect {
  SNAP, // Window open at full opacity, immediately
  FADE, // Fade in or out at a given speed
  WIPE, // Fly on or off from a border at a given speed
  NONE;

  public static DTVCCEffect from(int data) {
    switch (data) {
      case 0:
        return SNAP;
      case 1:
        return FADE;
      case 2:
        return WIPE;
    }
    return NONE;
  }
}