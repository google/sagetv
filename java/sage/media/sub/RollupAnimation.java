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

import java.util.ArrayList;
import java.util.List;

import sage.media.sub.DTVCCWindow.Rectangle;

/**
 * All animations and their directions for closed captioning.
 */
public class RollupAnimation {
  public static class RollupWindow {
    /**
     * Get the 708 direction of animation. Not relevant to 608 as all roll ups are "up"
     */
    public DTVCCDirection getDirection() {
      return direction;
    }

    /**
     * Get the 708 window id that is being rolled up. Not relevant to 608 captions
     */
    public int getWindowID() {
      return windowID;
    }

    /**
     * Get the rectangle (rows/columns) to roll up; valid for 608 captions, not relevant for 708
     * since WindowID should be valid.
     */
    public Rectangle getRectangle() {
      return rect;
    }

    /**
     * 608 animation
     */
    RollupWindow(Rectangle rect) {
      this.rect = rect;
      this.windowID = -1;
      this.direction = DTVCCDirection.BOTTOM_TO_TOP;
    }

    /**
     * 708 animation
     */
    RollupWindow(int windowID, DTVCCDirection direction) {
      this.rect = null;
      this.windowID = windowID;
      this.direction = direction;
    }

    @Override
    public String toString() {
      if(rect != null) {
        return "(608, " + rect.toString() + ")";
      }
      return "(708, id:" + windowID + ", dir:" + direction.toString()  + ")";
    }

    int windowID;
    DTVCCDirection direction;
    Rectangle rect;
  }

  RollupAnimation() {
    // NOTE: 708 captions limit the number of windows to 4; so it seemed like a good number
    // to pick, when it should only ever be 1.
    rollups = new ArrayList<RollupWindow>(4);
  }

  /**
   * Add a 608 animation.  There should only be one at a time, otherwise we interrupt animations.
   */
  void add(Rectangle rect) {
    rollups.add(new RollupWindow(rect));
  }

  /**
   * Add a 708 animation. There can be multiple ones, assuming each are for different windows.
   */
  void add(int windowID, DTVCCDirection dirrection) {
    rollups.add(new RollupWindow(windowID, dirrection));
  }

  public RollupWindow getRollup(int i) {
    return rollups.get(i);
  }

  public int getCount() {
    return rollups.size();
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("[RollupAnimation{ count=" + rollups.size() + " {");
    for(RollupWindow win : rollups) {
      sb.append(win.toString());
      sb.append(", ");
    }
    sb.append("}}");
    return sb.toString();
  }

  private List<RollupWindow> rollups;
}
