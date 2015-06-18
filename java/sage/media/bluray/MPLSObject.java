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

import sage.Sage;

/**
 *
 * @author Narflex
 */
public class MPLSObject
{

	/** Creates a new instance of MPLSObject */
	public MPLSObject(sage.FasterRandomFile inStream) throws java.io.IOException
	{
		byte[] strHolder = new byte[4];
		inStream.readFully(strHolder);
		String fileType = new String(strHolder, BluRayParser.BLURAY_CHARSET);
		if (!"MPLS".equals(fileType))
			throw new java.io.IOException("Invalid BluRay structure: MPLS file is missing the MPLS header!");
		inStream.readFully(strHolder);
		version = new String(strHolder, BluRayParser.BLURAY_CHARSET);
		int playlistOffset = inStream.readInt();
		int markOffset = inStream.readInt();
		int extensionOffset = inStream.readInt();
		inStream.skipBytes(20);

		// PlaylistAppInfo
		int appInfoLength = inStream.readInt();
		inStream.skipBytes(1);
		playbackType = inStream.read();
		inStream.skipBytes(2); // playback_count
		inStream.skipBytes(8); // UOMaskTable
		inStream.skipBytes(2); // flags + reserved

		// N1 padding
		inStream.seek(playlistOffset);

		// Playlist data
		inStream.skipBytes(6); // playlist data length + reserved
		int playlistLength = inStream.readUnsignedShort();
		int numSubPaths = inStream.readUnsignedShort();
		playlistItems = new PlaylistItem[playlistLength];
		java.util.HashSet uniqueClips = new java.util.HashSet();
		for (int i = 0; i < playlistLength; i++)
		{
			playlistItems[i] = new PlaylistItem(inStream);
			totalDuration += playlistItems[i].duration;
			if (uniqueClips.add(playlistItems[i].itemClips[0].clipName))
				totalUniqueDuration += playlistItems[i].duration;
		}
		// Skip the SubPath stuff

		// N2 padding
		inStream.seek(markOffset);
		inStream.skipBytes(4); // mark data length
		int numMarks = inStream.readUnsignedShort();
		playlistMarks = new PlaylistMark[numMarks];
		for (int i = 0; i < numMarks; i++)
			playlistMarks[i] = new PlaylistMark(inStream);

	}

	public void dumpInfo()
	{
		System.out.println("version=" + version + " type=" + (playbackType==1?"Sequential":(playbackType==2?"Random":"Shuffle")) + " duration=" + sage.Sage.durFormat(totalDuration) +
			" uniqueDur=" + sage.Sage.durFormat(totalUniqueDuration));
		System.out.println("PlaylistItems count=" + playlistItems.length);
		for (int i = 0; i < playlistItems.length; i++)
			playlistItems[i].dumpInfo();
		System.out.println("PlaylistMarks count=" + playlistMarks.length);
		for (int i = 0; i < playlistMarks.length; i++)
			System.out.println("Marks[" + i + "]=" + playlistMarks[i].toString());
	}

	public String version;
	public int playbackType; // 1 - Sequential, 2 - Random, 3 - Shuffle
	public PlaylistItem[] playlistItems;
	public PlaylistMark[] playlistMarks;
	public long totalDuration; // playlist duration from adding up all item lengths
	public long totalUniqueDuration; // from unique clips, helps prevent misidentification of looping content as the main movie
}
