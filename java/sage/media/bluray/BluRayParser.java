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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;

/**
 *
 * @author Narflex
 */
public class BluRayParser
{
	public static final String BLURAY_CHARSET = "ISO646-US";

	/** Creates a new instance of BluRayParser */
	public BluRayParser(java.io.File inBdmvDir)
	{
		this(inBdmvDir, null);
	}
	public BluRayParser(java.io.File inBdmvDir, String inRemoteHostname)
	{
		bdmvDir = inBdmvDir;
		remoteHostname = inRemoteHostname;
		if (remoteHostname == null && !bdmvDir.isDirectory())
			throw new IllegalArgumentException("Invalid BluRay folder structure: directory does not exist");
		// Check for the necessary folder structure
		indexFile = new java.io.File(bdmvDir, "index.bdmv");
		if (!indexFile.isFile())
			indexFile = new java.io.File(bdmvDir, "INDEX.BDM");
		if (remoteHostname == null && !indexFile.isFile())
			throw new IllegalArgumentException("Invalid BluRay folder structure: index.bdm[v] was not found");
		movieObjectFile = new java.io.File(bdmvDir, "MovieObject.bdmv");
		if (!movieObjectFile.isFile())
			movieObjectFile = new java.io.File(bdmvDir, "MOVIEOBJ.BDM");
		if (remoteHostname == null && !movieObjectFile.isFile())
			throw new IllegalArgumentException("Invalid BluRay folder structure: MovieObject.bdm[v] was not found");
		streamDir = new java.io.File(bdmvDir, "STREAM");
		if (remoteHostname == null && !streamDir.isDirectory())
			throw new IllegalArgumentException("Invalid BluRay folder structure: STREAM directory was not found");
		playlistDir = new java.io.File(bdmvDir, "PLAYLIST");
		if (remoteHostname == null && !playlistDir.isDirectory())
			throw new IllegalArgumentException("Invalid BluRay folder structure: PLAYLIST directory was not found");
	}

	// This returns the Container format to be used for associated BluRay files in SageTV
	public sage.media.format.ContainerFormat getFileFormat()
	{
		return getFileFormat(mainPlaylistIndex);
	}

	public sage.media.format.ContainerFormat getFileFormat(int index)
	{
		index = Math.max(0, Math.min(playlistFormats.length - 1, index));
		if (playlistFormats[index] == null)
			playlistFormats[index] = buildContainerFormat(index);
		return playlistFormats[index];
	}

	public MPLSObject getMainPlaylist()
	{
		return playlists[mainPlaylistIndex];
	}

	public MPLSObject getPlaylist(int index)
	{
		return playlists[index];
	}

	public int getNumPlaylists()
	{
		return playlists.length;
	}

	public int getMainPlaylistIndex()
	{
		return mainPlaylistIndex;
	}

	public String getPlaylistDesc(int index)
	{
		index = Math.max(0, Math.min(playlists.length - 1, index));
		return Integer.toString(index + 1) + " - " + sage.Sage.durFormat(playlists[index].totalUniqueDuration) + (index == mainPlaylistIndex ? " (*)" : "");
	}

	// This reads from the filesystem and builds the corresponding structures in memory for easier analysis
	public void fullyAnalyze() throws java.io.IOException
	{
		if (sage.Sage.DBG) System.out.println("Analyzing BluRay structure at: " + bdmvDir);
		// Get all of the MPLS files in the playlist dir
		String[] mplsFilenames;
		if (remoteHostname == null)
		{
			mplsFilenames = playlistDir.list(new java.io.FilenameFilter()
			{
				public boolean accept(java.io.File dir, String name)
				{
					return name.toLowerCase().endsWith(".mpls") || name.toLowerCase().endsWith(".mpl");
				}
			});
		}
		else
		{
			java.net.Socket sock = null;
			java.io.DataOutputStream outStream = null;
			java.io.DataInputStream inStream = null;
			try
			{
				sock = new java.net.Socket();
				sock.connect(new java.net.InetSocketAddress(remoteHostname, 7818), 5000);
				sock.setSoTimeout(30000);
				//sock.setTcpNoDelay(true);
				outStream = new java.io.DataOutputStream(new java.io.BufferedOutputStream(sock.getOutputStream()));
				inStream = new java.io.DataInputStream(new java.io.BufferedInputStream(sock.getInputStream()));
				outStream.write("LISTW ".getBytes(sage.Sage.BYTE_CHARSET));
				outStream.write(playlistDir.toString().getBytes("UTF-16BE"));
				outStream.write("\r\n".getBytes(sage.Sage.BYTE_CHARSET));
				outStream.flush();
				String str = sage.Sage.readLineBytes(inStream);
				if (!"OK".equals(str))
					throw new java.io.IOException("Error opening remote file of:" + str);
				// get the size
				str = sage.Sage.readLineBytes(inStream);
				int numFiles = Integer.parseInt(str);
				java.util.ArrayList fileVec = new java.util.ArrayList();
				for (int i = 0; i < numFiles; i++)
				{
					String currFilename = sage.MediaServer.convertToUnicode(sage.Sage.readLineBytes(inStream));
					if (currFilename.toLowerCase().endsWith(".mpls") || currFilename.toLowerCase().endsWith(".mpl"))
						fileVec.add(currFilename);
				}
				mplsFilenames = (String[]) fileVec.toArray(new String[0]);
			}
			finally
			{
				try{
					if (sock != null)
						sock.close();
				}catch (Exception e1){}
				try{
				if (outStream != null)
					outStream.close();
				}catch (Exception e2){}
				try{
				if (inStream != null)
					inStream.close();
				}catch (Exception e3){}
			}
		}
		if (mplsFilenames == null || mplsFilenames.length == 0)
			throw new java.io.IOException("Invalid BluRay structure; there are no .mpls files in the PLAYLIST directory");
		if (sage.Sage.DBG) System.out.println("Found " + mplsFilenames.length + " MPLS files in the Playlist directory");
		int maxDurIndex = -1;
		long maxDuration = 0;
		int maxTracks = 0;
		java.util.Arrays.sort(mplsFilenames);
		playlists = new MPLSObject[mplsFilenames.length];
		playlistIDs = new String[playlists.length];
		playlistFormats = new sage.media.format.ContainerFormat[playlists.length];
		for (int i = 0; i < mplsFilenames.length; i++)
		{
			int idx = mplsFilenames[i].indexOf(".");
			if (mplsFilenames[i].toLowerCase().endsWith(".mpl"))
				usesShortFilenames = true;
			String mplsId = mplsFilenames[i].substring(0, idx);
			java.io.File mplsFile = new java.io.File(playlistDir, mplsFilenames[i]);
			sage.io.SageDataFile dis = null;
			try
			{
				if (remoteHostname != null)
					dis = new sage.io.SageDataFile(new sage.io.BufferedSageFile(new sage.io.RemoteSageFile(remoteHostname, mplsFile), 65536), BLURAY_CHARSET);
				else
					dis = new sage.io.SageDataFile(new sage.io.BufferedSageFile(new sage.io.LocalSageFile(mplsFile, true), 65536), BLURAY_CHARSET);;
				MPLSObject mplsObj = new MPLSObject(dis);
				playlistIDs[i] = mplsId;
				playlists[i] = mplsObj;
				if (maxDurIndex == -1 || isNewPlaylistBetter(maxDuration, maxTracks, mplsObj.totalUniqueDuration, mplsObj.playlistItems[0].primaryAudioStreams.length))
				{
					maxDurIndex = i;
					maxDuration = mplsObj.totalUniqueDuration;
					maxTracks = mplsObj.playlistItems[0].primaryAudioStreams.length;
				}
			}
			finally
			{
				if (dis != null)
				{
					try{dis.close();} catch(Exception e){}
				}
			}
		}
		mainPlaylistIndex = maxDurIndex;
	}

	public void dumpInfo()
	{
		System.out.println("Dumping BluRay info for structure at " + bdmvDir);
		System.out.println("Main playlist ID is " + playlistIDs[mainPlaylistIndex]);
		System.out.println("Main Format " + getFileFormat());
		System.out.println("All Playlists:");
		for (int i = 0; i < playlists.length; i++)
		{
			System.out.println("Playlist " + playlistIDs[i]);
			playlists[i].dumpInfo();
		}
	}

	private boolean isNewPlaylistBetter(long currDur, int currTracks, long newDur, int newTracks)
	{
		// Compare the number of audio tracks and also the duration. If the duration is longer, and has the same number of audio tracks or more, then it's good.
		// But if the duration is shorter, then only take the new one if it has more tracks AND it's within 10% of the duration
		if (newDur > currDur && newTracks >= currTracks)
			return true;
		if (newDur > currDur*9/10 && newTracks > currTracks)
			return true;
		// Ratatouille had a secondary playlist with more audio tracks than the main playlist; but much shorter in duration. So if we have one that's
		// significantly longer; then we just go with that
		if (newDur > currDur*4/3)
			return true;
		return false;
	}

	// Build a new ContainerFormat object repsresenting this BluRay format
	private sage.media.format.ContainerFormat buildContainerFormat(int index)
	{
		sage.media.format.ContainerFormat cf = new sage.media.format.ContainerFormat();
		cf.setFormatName(sage.media.format.MediaFormat.MPEG2_TS);
		MPLSObject mplsObj = playlists[index];
		cf.setDuration(mplsObj.totalDuration);
		int maxNumStreams = 0;
		int maxStreamIndex = -1;
		for (int i = 0; i < mplsObj.playlistItems.length; i++)
		{
			int currNumStreams = mplsObj.playlistItems[i].primaryVideoStreams.length +
				mplsObj.playlistItems[i].primaryAudioStreams.length + mplsObj.playlistItems[i].subtitleStreams.length;
			if (maxStreamIndex < 0 || currNumStreams > maxNumStreams)
			{
				maxNumStreams = currNumStreams;
				maxStreamIndex = i;
			}
		}
		sage.media.format.BitstreamFormat[] streams = new sage.media.format.BitstreamFormat[mplsObj.playlistItems[maxStreamIndex].primaryVideoStreams.length +
				mplsObj.playlistItems[maxStreamIndex].primaryAudioStreams.length + mplsObj.playlistItems[maxStreamIndex].subtitleStreams.length];
		System.arraycopy(mplsObj.playlistItems[maxStreamIndex].primaryVideoStreams, 0, streams, 0, mplsObj.playlistItems[maxStreamIndex].primaryVideoStreams.length);
		System.arraycopy(mplsObj.playlistItems[maxStreamIndex].primaryAudioStreams, 0, streams,
			mplsObj.playlistItems[maxStreamIndex].primaryVideoStreams.length, mplsObj.playlistItems[maxStreamIndex].primaryAudioStreams.length);
		System.arraycopy(mplsObj.playlistItems[maxStreamIndex].subtitleStreams, 0, streams,
			mplsObj.playlistItems[maxStreamIndex].primaryVideoStreams.length + mplsObj.playlistItems[maxStreamIndex].primaryAudioStreams.length,
			mplsObj.playlistItems[maxStreamIndex].subtitleStreams.length);
		cf.setStreamFormats(streams);
		cf.setPacketSize(192);

		// Check to see if there's any 6 channel audio sources, and if so we need to run our own MPEG format detector to distinguish between 7.1 and 5.1 audio.
		boolean needAudioRedetect = false;
		for (int i = 0; i < streams.length; i++)
		{
			if (streams[i] instanceof sage.media.format.AudioFormat &&
				((sage.media.format.AudioFormat)streams[i]).getChannels() == 6)
				needAudioRedetect = true;
		}
		if (needAudioRedetect)
		{
			if (sage.Sage.DBG) System.out.println("Redetecting format on BluRay audio to distinguish between 5.1 and 7.1 channels");
			// We need to use FFMPEG here because our internal detector doesn't deal with the stv:// protocol
			sage.media.format.ContainerFormat ourCf;
			// NOTE: THIS WILL NOT WORK PROPERLY IN FAT CLIENT EMBEDDED MODE; BUT WE CAN FIX THAT LATER!
      ourCf = sage.media.format.FormatParser.getFFMPEGFileFormat((remoteHostname == null ? "" : ("stv://" + remoteHostname + "/")) +
        new java.io.File(streamDir, mplsObj.playlistItems[0].itemClips[0].clipName + (doesUseShortFilenames() ? ".MTS" : ".m2ts")).getAbsolutePath());
			if (ourCf != null)
			{
				sage.media.format.AudioFormat[] ffAudios = ourCf.getAudioFormats();
				// Now go through and find all the audio streams that we need to locate a match for, and then match them
				for (int i = 0; i < streams.length; i++)
				{
					if (streams[i] instanceof sage.media.format.AudioFormat)
					{
						sage.media.format.AudioFormat origAudioFormat = (sage.media.format.AudioFormat)streams[i];
						if (origAudioFormat.getChannels() == 6 && origAudioFormat.getId() != null)
						{
							for (int j = 0; j < ffAudios.length; j++)
							{
								if (origAudioFormat.getId().equals(ffAudios[j].getId()))
								{
									if (sage.Sage.DBG && origAudioFormat.getChannels() != ffAudios[j].getChannels()) System.out.println("Fixing BD Audio channels for " + origAudioFormat + " to be " + ffAudios[j]);
									origAudioFormat.setChannels(ffAudios[j].getChannels());
									break;
								}
							}
						}
					}
				}
			}
		}
		if (sage.Sage.getBoolean("enable_bluray_metadata_extraction", true))
			extractBDMetadata(cf);
		if (sage.Sage.DBG) System.out.println("Built BluRay format of:" + cf);
		return cf;
	}

	// This will try to find Title & Thumbnail information from the META folder in the BDMV
	private void extractBDMetadata(sage.media.format.ContainerFormat cf)
	{
		java.io.File metaXml = new java.io.File(bdmvDir, "META" + java.io.File.separator + "DL" + java.io.File.separator + "bdmt_eng.xml");
		if (metaXml.isFile())
		{
			if (factory == null)
				factory = SAXParserFactory.newInstance();
			// We should use XML parsing even though its simple in case there's special chars in the title or other funky stuff
			java.io.InputStream inStream = null;
			try
			{
				inStream = new java.io.BufferedInputStream(new java.io.FileInputStream(metaXml));
				factory.setValidating(false);
				factory.newSAXParser().parse(inStream, new BDMVSAXHandler());
			}
			catch (Exception e)
			{
			}
			if (inStream != null)
			{
				try
				{
					inStream.close();
				}
				catch (Exception e){}
			}
			if (metaTitle != null)
				cf.addMetadata(sage.media.format.MediaFormat.META_TITLE, metaTitle);
			if (metaThumbnail != null)
				cf.addMetadata(sage.media.format.MediaFormat.META_THUMBNAIL_FILE, metaThumbnail);
		}
	}

	public boolean doesUseShortFilenames()
	{
		return usesShortFilenames;
	}

	private java.io.File bdmvDir;
	private java.io.File indexFile;
	private java.io.File movieObjectFile;
	private java.io.File streamDir;
	private java.io.File playlistDir;
	private String remoteHostname; // for retrieving from a SageTV Server
	private String metaTitle;
	private String metaThumbnail;

	private sage.media.format.ContainerFormat[] playlistFormats;

	private int mainPlaylistIndex;
	private MPLSObject[] playlists;
	private String[] playlistIDs;
	private boolean usesShortFilenames;

	private static SAXParserFactory factory = null;
	private class BDMVSAXHandler extends DefaultHandler
	{
		private String current_tag;
		private StringBuffer buff = new StringBuffer();
		private boolean insideTitle;
		private boolean insideDescription;
		private int maxThumbSize = -1;
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
		{
			if ("di:title".equalsIgnoreCase(qName))
			{
				insideTitle = true;
			}
			else if ("di:description".equalsIgnoreCase(qName))
			{
				insideDescription = true;
			}
			else if (insideDescription && "di:thumbnail".equals(qName))
			{
				String thumbStr = attributes.getValue("href");
				String sizeStr = attributes.getValue("size");
				if (thumbStr != null && sizeStr != null)
				{
					int xidx = sizeStr.indexOf('x');
					if (xidx != -1)
					{
						int currSize = 0;
						try
						{
							currSize = Integer.parseInt(sizeStr.substring(0, xidx)) * Integer.parseInt(sizeStr.substring(xidx + 1));
						}
						catch (NumberFormatException nfe)
						{
							if (sage.Sage.DBG) System.out.println("ERROR could not extract BDMV thumbnail size of :" + nfe + " from " + sizeStr);
						}
						if (currSize > maxThumbSize)
						{
							metaThumbnail = new java.io.File(new java.io.File(bdmvDir, "META" + java.io.File.separator + "DL"), thumbStr).getAbsolutePath();
						}
					}
				}
			}
			current_tag = qName;
		}
		public void characters(char[] ch, int start, int length)
		{
			String data = new String(ch,start,length);

			//Jump blank chunk
			if (data.trim().length() == 0)
				return;
			buff.append(data);
		}
		public void endElement(String uri, String localName, String qName)
		{
			String data = buff.toString().trim();

			if (qName.equals(current_tag))
				buff = new StringBuffer();
			if ("di:title".equals(qName))
				insideTitle = false;
			else if ("di:description".equals(qName))
				insideDescription = false;
			else if (insideTitle && "di:name".equals(qName))
			{
				metaTitle = data;
			}
		}
	}

}
