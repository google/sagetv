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
public class ClipInfo
{

	/** Creates a new instance of ClipInfo */
	public ClipInfo(int inAngleId, String inClipName, String codecId, int inStcId)
	{
		if (!"M2TS".equals(codecId))
			throw new IllegalArgumentException("Invalid BluRay Structure: codec ID for PlayList Item should be M2TS and we found: " + codecId);
		angleId = inAngleId;
		clipName = inClipName;
		stcId = inStcId;
	}

	public String toString()
	{
		return "ClipInfo[" + angleId + ", stcId=" + stcId + ", name=" + clipName + "]";
	}

	public int angleId;
	public int stcId;
	public String clipName;
}
