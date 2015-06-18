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
public class PlaylistItem
{

	/** Creates a new instance of PlaylistItem */
	public PlaylistItem(sage.FasterRandomFile inStream) throws java.io.IOException
	{
		java.util.ArrayList angles = new java.util.ArrayList();
		int dataLength = inStream.readUnsignedShort();
		long startPos = inStream.getFilePointer();
		byte[] strHolder = new byte[5];
		inStream.readFully(strHolder);
		clipInfoName = new String(strHolder, BluRayParser.BLURAY_CHARSET);
		inStream.readFully(strHolder, 0, 4);
		String codecID = new String(strHolder, 0, 4, BluRayParser.BLURAY_CHARSET);
		int flags = inStream.readUnsignedShort();
		boolean multiAngle = (flags & 0x10) != 0;
		int stcId = inStream.read();
		angles.add(new ClipInfo(0, clipInfoName, codecID, stcId));
		inTime = inStream.readInt();
		outTime = inStream.readInt();
		duration = (outTime - inTime) / 45; // msec
		inStream.skipBytes(12); // UOMaskTable + flags + stillmodeinfo
		if (multiAngle)
		{
			// Read the other angle information
			int numAngles = inStream.read();
			inStream.skipBytes(1); // flags - differentAudio & seamlessAngleChange
			for (int i = 1; i < numAngles; i++)
			{
				inStream.readFully(strHolder);
				String angleInfoName = new String(strHolder, BluRayParser.BLURAY_CHARSET);
				inStream.readFully(strHolder, 0, 4);
				codecID = new String(strHolder, 0, 4, BluRayParser.BLURAY_CHARSET);
				stcId = inStream.read();
				angles.add(new ClipInfo(i, angleInfoName, codecID, stcId));
			}
		}
		itemClips = (ClipInfo[]) angles.toArray(new ClipInfo[0]);

		// Now we need to parse the stream table
		int numStreams = inStream.readUnsignedShort();
		inStream.skipBytes(2);
		int primaryVideoStreamLength = inStream.readByte();
		int primaryAudioStreamLength = inStream.readByte();
		int pgTextStStreamLength = inStream.readByte();
		int igStreamLength = inStream.readByte();
		int secondaryAudioStreamLength = inStream.readByte();
		int secondaryVideoStreamLength = inStream.readByte();
		int pipPGTextStStreamLength = inStream.readByte();
		inStream.skipBytes(5);

		primaryVideoStreams = new sage.media.format.VideoFormat[primaryVideoStreamLength];
		primaryAudioStreams = new sage.media.format.AudioFormat[primaryAudioStreamLength];
		java.util.ArrayList subtitleVec = new java.util.ArrayList();
		for (int i = 0; i < primaryVideoStreams.length; i++)
		{
			primaryVideoStreams[i] = new sage.media.format.VideoFormat();
			inStream.skipBytes(1); // reserved
			int entryType = inStream.read();
			if (entryType == 1) // for PlayItem
			{
				int pid = inStream.readUnsignedShort();
				primaryVideoStreams[i].setId(Integer.toString(pid, 16));
				inStream.skipBytes(6); // reserved
				inStream.skipBytes(1); // data length
				int streamType = inStream.read();
				switch (streamType)
				{
					case 0x02:
						primaryVideoStreams[i].setFormatName(sage.media.format.MediaFormat.MPEG2_VIDEO);
						break;
					case 0x1b:
						primaryVideoStreams[i].setFormatName(sage.media.format.MediaFormat.H264);
						break;
					case 0xea:
						primaryVideoStreams[i].setFormatName(sage.media.format.MediaFormat.VC1);
						break;
					default:
						primaryVideoStreams[i].setFormatName("Video");
						if (sage.Sage.DBG) System.out.println("ERROR in BluRay parsing, unknown video stream type of " + streamType);
						break;
				}
				int formatFlags = inStream.read();
				int frameRate = formatFlags & 0xF;
				int dimen = formatFlags >> 4;
				switch (frameRate)
				{
					case 1:
						primaryVideoStreams[i].setFpsNum(24000);
						primaryVideoStreams[i].setFpsDen(1001);
						primaryVideoStreams[i].setFps(24000.0f/1001.0f);
						break;
					case 2:
						primaryVideoStreams[i].setFpsNum(24);
						primaryVideoStreams[i].setFpsDen(1);
						primaryVideoStreams[i].setFps(24);
						break;
					case 3:
						primaryVideoStreams[i].setFpsNum(25);
						primaryVideoStreams[i].setFpsDen(1);
						primaryVideoStreams[i].setFps(25);
						break;
					case 4:
						primaryVideoStreams[i].setFpsNum(30000);
						primaryVideoStreams[i].setFpsDen(1001);
						primaryVideoStreams[i].setFps(30000.0f/1001.0f);
						break;
					case 6:
						primaryVideoStreams[i].setFpsNum(50);
						primaryVideoStreams[i].setFpsDen(1);
						primaryVideoStreams[i].setFps(50);
						break;
					case 7:
						primaryVideoStreams[i].setFpsNum(60000);
						primaryVideoStreams[i].setFpsDen(1001);
						primaryVideoStreams[i].setFps(60000.0f/1001.0f);
						break;
					default:
						if (sage.Sage.DBG) System.out.println("ERROR in BluRay parsing; invalid frame rate of " + frameRate);
						break;
				}
				switch (dimen)
				{
					case 1:
						primaryVideoStreams[i].setWidth(720);
						primaryVideoStreams[i].setHeight(480);
						primaryVideoStreams[i].setInterlaced(true);
						break;
					case 2:
						primaryVideoStreams[i].setWidth(720);
						primaryVideoStreams[i].setHeight(576);
						primaryVideoStreams[i].setInterlaced(true);
						break;
					case 3:
						primaryVideoStreams[i].setWidth(720);
						primaryVideoStreams[i].setHeight(480);
						primaryVideoStreams[i].setInterlaced(false);
						break;
					case 4:
						primaryVideoStreams[i].setWidth(1920);
						primaryVideoStreams[i].setHeight(1080);
						primaryVideoStreams[i].setInterlaced(true);
						break;
					case 5:
						primaryVideoStreams[i].setWidth(1280);
						primaryVideoStreams[i].setHeight(720);
						primaryVideoStreams[i].setInterlaced(false);
						break;
					case 6:
						primaryVideoStreams[i].setWidth(1920);
						primaryVideoStreams[i].setHeight(1080);
						primaryVideoStreams[i].setInterlaced(false);
						break;
					case 7:
						primaryVideoStreams[i].setWidth(720);
						primaryVideoStreams[i].setHeight(576);
						primaryVideoStreams[i].setInterlaced(false);
						break;
					default:
						if (sage.Sage.DBG) System.out.println("ERROR in BluRay parsing; invalid frame size of " + dimen);
						break;
				}
				inStream.skipBytes(3); // reserved
			}
			else
			{
				// Skip these???
				inStream.skipBytes(14);
			}
		}
		for (int i = 0; i < primaryAudioStreams.length; i++)
		{
			primaryAudioStreams[i] = new sage.media.format.AudioFormat();
			primaryAudioStreams[i].setOrderIndex(i);
			inStream.skipBytes(1); // reserved
			int entryType = inStream.read();
			if (entryType == 1) // for PlayItem
			{
				int pid = inStream.readUnsignedShort();
				primaryAudioStreams[i].setId(Integer.toString(pid, 16));
				inStream.skipBytes(6); // reserved
				inStream.skipBytes(1); // data length
				int streamType = inStream.read();
				switch (streamType)
				{
					case 0x80:
						primaryAudioStreams[i].setFormatName("PCM_BD");
						break;
					case 0x81:
						primaryAudioStreams[i].setFormatName(sage.media.format.MediaFormat.AC3);
						break;
					case 0x82:
						primaryAudioStreams[i].setFormatName(sage.media.format.MediaFormat.DTS);
						break;
					case 0x83:
						primaryAudioStreams[i].setFormatName(sage.media.format.MediaFormat.DOLBY_HD);
						break;
					case 0x84:
						primaryAudioStreams[i].setFormatName(sage.media.format.MediaFormat.EAC3);
						break;
					case 0x85:
						primaryAudioStreams[i].setFormatName(sage.media.format.MediaFormat.DTS_HD);
						break;
					case 0x86:
						primaryAudioStreams[i].setFormatName(sage.media.format.MediaFormat.DTS_MA);
						break;
					default:
						primaryAudioStreams[i].setFormatName("Audio");
						if (sage.Sage.DBG) System.out.println("ERROR in BluRay parsing, unknown audio stream type of " + streamType);
						break;
				}
				int formatFlags = inStream.read();
				int sampling = formatFlags & 0xF;
				int channels = formatFlags >> 4;
				if (channels == 3)
					channels = 2;
				// There can also be 12 for Stereo+6channel
				// And for 6; it just means multi which could be 6 or 8
				primaryAudioStreams[i].setChannels(channels);
				switch (sampling)
				{
					case 1:
						primaryAudioStreams[i].setSamplingRate(48000);
						break;
					case 4:
						primaryAudioStreams[i].setSamplingRate(96000);
						break;
					case 5:
						primaryAudioStreams[i].setSamplingRate(192000);
						break;
					case 12:
						primaryAudioStreams[i].setSamplingRate(192000);
						// plus a 48kHz stereo track
						break;
					case 14:
						primaryAudioStreams[i].setSamplingRate(96000);
						// plus a 48kHz stereo track
						break;
					default:
						if (sage.Sage.DBG) System.out.println("ERROR in BluRay parsing; invalid audio sampling of " + sampling);
						break;
				}
				inStream.readFully(strHolder, 0, 3);
				primaryAudioStreams[i].setLanguage(new String(strHolder, 0, 3, BluRayParser.BLURAY_CHARSET));
			}
			else
			{
				// Skip these???
				inStream.skipBytes(14);
			}
		}
		for (int i = 0; i < pipPGTextStStreamLength + pgTextStStreamLength; i++)
		{
			inStream.skipBytes(1); // reserved
			int entryType = inStream.read();
			if (entryType == 1) // for PlayItem
			{
				int pid = inStream.readUnsignedShort();
				inStream.skipBytes(6); // reserved
				inStream.skipBytes(1); // data length
				int streamType = inStream.read();
				if (streamType == 0x93 || streamType == 0x90)
				{
					// Subtitle text stream
					sage.media.format.SubpictureFormat subpicFormat = new sage.media.format.SubpictureFormat();
					subtitleVec.add(subpicFormat);
					subpicFormat.setId(Integer.toString(pid, 16));
					if (streamType == 0x90)
					{
						inStream.readFully(strHolder, 0, 3);
						subpicFormat.setLanguage(new String(strHolder, 0, 3, BluRayParser.BLURAY_CHARSET));
						inStream.skipBytes(1);
					}
					else
					{
						int charCode = inStream.read();
						inStream.readFully(strHolder, 0, 3);
						subpicFormat.setLanguage(new String(strHolder, 0, 3, BluRayParser.BLURAY_CHARSET));
						if (sage.Sage.DBG) System.out.println("Read char code for subpicstream of " + charCode + " lang=" + subpicFormat.getLanguage() + " pid=" + subpicFormat.getId());
					}
				}
				else
				{
					inStream.skipBytes(4);
				}
			}
			else
			{
				// Skip these???
				inStream.skipBytes(14);
			}
		}
		subtitleStreams = (sage.media.format.SubpictureFormat[]) subtitleVec.toArray(new sage.media.format.SubpictureFormat[0]);

		// Now we're supposed to read the IG streams and also the secondary audio/video streams
		inStream.skipBytes((int)(dataLength - (inStream.getFilePointer() - startPos)));
	}

	public void dumpInfo()
	{
		System.out.println("PlaylistItem inTime=" + sage.Sage.durFormat(inTime/45) + " outTime=" + sage.Sage.durFormat(outTime/45) + " duration=" +
			sage.Sage.durFormat(duration));
		System.out.println("Clips count=" + itemClips.length);
		for (int i = 0; i < itemClips.length; i++)
			System.out.println(itemClips[i].toString());
		System.out.println("Streams:");
		for (int i = 0; i < primaryVideoStreams.length; i++)
			System.out.println("Video[" + i + "]=" + primaryVideoStreams[i]);
		for (int i = 0; i < primaryAudioStreams.length; i++)
			System.out.println("Audio[" + i + "]=" + primaryAudioStreams[i]);
		for (int i = 0; i < subtitleStreams.length; i++)
			System.out.println("Subtitle[" + i + "]=" + subtitleStreams[i]);
	}

	public String clipInfoName;
	public ClipInfo[] itemClips;
	public int inTime;
	public int outTime;
	public int duration;
	public sage.media.format.VideoFormat[] primaryVideoStreams;
	public sage.media.format.AudioFormat[] primaryAudioStreams;
	public sage.media.format.SubpictureFormat[] subtitleStreams;
}
