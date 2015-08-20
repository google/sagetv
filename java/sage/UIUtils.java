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

public class UIUtils
{
  public static boolean isPointOnAScreen(java.awt.Point pos)
  {
    java.awt.GraphicsDevice[] screens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().
        getScreenDevices();
    // Check to make sure the window appears on at least one monitor
    for (int i = 0; i < screens.length; i++)
    {
      java.awt.Rectangle scrBounds = screens[i].getDefaultConfiguration().getBounds();
      java.awt.Insets scrInsets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(screens[i].getDefaultConfiguration());
      scrBounds.x += scrInsets.left;
      scrBounds.y += scrInsets.top;
      scrBounds.width -= scrInsets.left + scrInsets.right + 50;
      scrBounds.height -= scrInsets.top + scrInsets.bottom + 50;
      if (scrBounds.contains(pos.x, pos.y))
        return true;
    }
    return false;
  }

  public static native long getHWND(java.awt.Canvas canvas);
  public static native long setFullScreenMode(java.awt.Canvas canvas, boolean fullscreen);
}
