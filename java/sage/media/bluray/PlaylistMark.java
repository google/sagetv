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
public class PlaylistMark
{

	/** Creates a new instance of PlaylistMark */
	public PlaylistMark(sage.FasterRandomFile inStream) throws java.io.IOException
	{
		inStream.skipBytes(1); // reserved
		type = inStream.read();
		playItemIdRef = inStream.readUnsignedShort();
		timestamp = inStream.readInt();
		entryESPID = inStream.readUnsignedShort();
		duration = inStream.readInt();
	}

	public String toString()
	{
		return "PlaylistMark[type=" + type + " itemRef=" + playItemIdRef + " time=" + sage.Sage.durFormat(timestamp/45) + " dur=" + sage.Sage.durFormat(duration/45) +
			" entryPID=0x" + Integer.toString(entryESPID, 16) + "]";
	}

	public int type;
	public int playItemIdRef;
	public int timestamp;
	public int entryESPID;
	public int duration;
}
