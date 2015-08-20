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

public interface OSDRenderingPlugin2 extends OSDRenderingPlugin
{
	/*
	 * Called to initialize the OSD.
	 *
	 * width - width of the OSD buffer
	 * height - height of the OSD buffer
	 *
	 * Returns true if succesful, false otherwise
	 */
	public boolean openOSD2(int width, int height);

	/*
	 * This is where everything happens. This is called whenever the OSD graphic changes in any
	 * way, or if the positioning of the video rectangle gets changed.
	 *
	 * image - the ARGB BufferedImage that holds the renderered UI image
	 * updateAreaRectangle - UI coordinates of the dirty area in the UI image
	 * videoRectangle - UI coordinates where the video should be placed
	 *
	 * Returns true if the OSD update was successful, false otherwise.
	 */
	public boolean updateOSD2(java.awt.image.BufferedImage image, java.awt.Rectangle updateAreaRectangle,
		java.awt.Rectangle videoRectangle);
}
