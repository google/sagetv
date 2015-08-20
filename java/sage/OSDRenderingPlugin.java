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

public interface OSDRenderingPlugin
{
  /*
   * Called to initialize the OSD.
   *
   * Returns true if succesful, false otherwise
   */
  public boolean openOSD();

  /*
   * Called to close the OSD. Resource cleanup should be done here.
   */
  public void closeOSD();

  /*
   * Returns false if this plugin requires the entire OSD bitmap when updates occur.
   * If this returns true, then the updateOSD function may be called with an updateAreaRectangle
   * that is smaller than the size of the entire UI.
   */
  public boolean supportsRegionUpdates();

  /*
   * This is where everything happens. This is called whenever the OSD graphic changes in any
   * way, or if the positioning of the video rectangle gets changed.
   *
   * argbBitmapData - An array of integers which contains the pixel values for the new OSD bitmap
   *                  in ARGB format. The length of this array will be 4*bitmapWidth*bitmapHeight
   * bitmapWidth - the width in pixels of the bitmap data passed in
   * bitmapHeight - the height in pixels of the bitmap data passed in
   * updateAreaRectangle - UI coordinates where the OSD bitmap should be placed. The width & height of
   *                       this rectangle will equal bitmapWidth & bitmapHeight respectively.
   * videoRectangle - UI coordinates where the video should be placed
   *
   * Returns true if the OSD update was successful, false otherwise.
   */
  public boolean updateOSD(int[] argbBitmapData, int bitmapWidth, int bitmapHeight,
      java.awt.Rectangle updateAreaRectangle, java.awt.Rectangle videoRectangle);
}
