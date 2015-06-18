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
package sage.media.bluray;

/**
 *
 * @author Narflex
 */
public interface BluRayStreamer
{
	public void setBufferSize(int x);
	public long getBytesLeftInClip();
	public int getCurrClipIndex();
	public long getClipPtsOffset(int index);
	public int getClipIndexForNextRead();
	public sage.media.format.ContainerFormat getFileFormat();
	public int getTitle();
	public long getChapterStartMsec(int chapter);
	public int getNumTitles();
	public int getNumChapters();
	public int getChapter(long pts45);
	public String getTitleDesc(int titleNum);
	public int getNumAngles();

}
