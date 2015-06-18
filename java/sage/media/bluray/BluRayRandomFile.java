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
public class BluRayRandomFile extends sage.BufferedFileChannel implements BluRayStreamer
{

	/** Creates a new instance of BluRayRandomFile */
    public BluRayRandomFile(java.io.File bdmvDir, boolean directBuffer, int inTargetTitle) throws java.io.IOException
    {
		super(sage.media.bluray.BluRayParser.BLURAY_CHARSET, directBuffer);
		this.bdmvDir = bdmvDir;
		targetTitle = inTargetTitle;
		bdp = new sage.media.bluray.BluRayParser(bdmvDir);
		bdp.fullyAnalyze();
		if (targetTitle <= 0)
			targetTitle = bdp.getMainPlaylistIndex() + 1;
		targetTitle = Math.max(1, Math.min(targetTitle, bdp.getNumPlaylists()));
		currPlaylist = bdp.getPlaylist(targetTitle - 1);

		fileSequence = new java.io.File[currPlaylist.playlistItems.length];
		fileOffsets = new long[fileSequence.length];
		ptsOffsets = new long[fileSequence.length];
		streamDir = new java.io.File(bdmvDir, "STREAM");
		totalSize = 0;
		long[] totalPts = new long[fileSequence.length];
		for (int i = 0; i < fileSequence.length; i++)
		{
			fileSequence[i] = new java.io.File(streamDir, currPlaylist.playlistItems[i].itemClips[0].clipName + (bdp.doesUseShortFilenames() ? ".MTS" : ".m2ts"));
			fileOffsets[i] = totalSize;
			ptsOffsets[i] = (i == 0 ? 0 : totalPts[i - 1]) - currPlaylist.playlistItems[i].inTime;
			totalSize += fileSequence[i].length();
			totalPts[i] = (i == 0 ? 0 : totalPts[i - 1]) + (currPlaylist.playlistItems[i].outTime - currPlaylist.playlistItems[i].inTime);
		}
		if (sage.Sage.DBG) System.out.println("Established BluRay file sequence with " + fileSequence.length + " segments and total size=" + totalSize);
		currFileIndex = 0;
		fc = new java.io.FileInputStream(fileSequence[currFileIndex]).getChannel();

		chapterOffsets = new long[currPlaylist.playlistMarks.length];
		for (int i = 0; i < chapterOffsets.length; i++)
		{
			int itemRef = currPlaylist.playlistMarks[i].playItemIdRef;
			chapterOffsets[i] = (itemRef == 0 ? 0 : totalPts[itemRef - 1]) + currPlaylist.playlistMarks[i].timestamp - currPlaylist.playlistItems[itemRef].inTime;
		}
	}

	public long length() throws java.io.IOException
    {
		return totalSize;
    }

	public int getCurrClipIndex()
	{
		return currFileIndex;
	}

	public int getClipIndexForNextRead()
	{
		if (getBytesLeftInClip() == 0 && currFileIndex < fileOffsets.length - 1)
			return currFileIndex + 1;
		else
		return currFileIndex;
	}

	public long getBytesLeftInClip()
	{
		return (currFileIndex < fileOffsets.length - 1) ? (fileOffsets[currFileIndex + 1] - fp) : (totalSize - fp);
	}

	public long getClipPtsOffset(int index)
	{
		return ptsOffsets[index];
	}

	public int getNumClips()
	{
		return fileOffsets.length;
	}

    public void seek(long newfp) throws java.io.IOException
    {
		if (newfp == fp) return;
		// Write any pending data before we seek
		flush();
		// See if we can do this seek within the read buffer we have
		if (rb != null)
		{
			if (newfp > fp && newfp < (fp + rb.remaining()))
			{
				rb.position(rb.position() + (int)(newfp - fp));
				fp = newfp;
			}
			else
			{
				rb.clear().limit(0); // no valid data in buffer
				fp = newfp;
				ensureProperFile(true, false);
			}
		}
		else
		{
			fp = newfp;
			ensureProperFile(true, false);
		}
    }

	protected void ensureProperFile(boolean alwaysSeek, boolean forceOpen) throws java.io.IOException
	{
		// Check to see if we need to move to a different file
		if (forceOpen || fp < fileOffsets[currFileIndex] || (currFileIndex < fileOffsets.length - 1 && fp >= fileOffsets[currFileIndex + 1]))
		{
			int oldIndex = currFileIndex;
			fc.close();
			for (currFileIndex = 0; currFileIndex < fileOffsets.length; currFileIndex++)
			{
				if (fileOffsets[currFileIndex] > fp)
					break;
			}
			currFileIndex--;
			if (sage.Sage.DBG) System.out.println("Switching BluRay source file from index " + oldIndex + " to " + currFileIndex);
			int currAngle = Math.min(prefAngle, currPlaylist.playlistItems[currFileIndex].itemClips.length - 1);
			fc = new java.io.FileInputStream(new java.io.File(streamDir, currPlaylist.playlistItems[currFileIndex].itemClips[currAngle].clipName +
				(bdp.doesUseShortFilenames() ? ".MTS" : ".m2ts"))).getChannel();
			alwaysSeek = true;
			rb.clear().limit(0); // no valid data in buffer
		}
		if (alwaysSeek)
			fc.position(fp - fileOffsets[currFileIndex]);
	}

	protected void ensureBuffer() throws java.io.IOException
	{
		if (rb == null)
		{
			rb = direct ? java.nio.ByteBuffer.allocateDirect(bufSize) : java.nio.ByteBuffer.allocate(bufSize);
			rb.clear().limit(0);
		}
		if (rb.remaining() <= 0)
		{
			ensureProperFile(false, false);
			rb.clear();
			if (fc.read(rb) < 0)
				throw new java.io.EOFException();
			rb.flip();
		}
	}

	public void read(java.nio.ByteBuffer b) throws java.io.IOException
	{
		if (rb != null && rb.remaining() > 0)
		{
			int currRead = Math.min(rb.remaining(), b.remaining());
			int oldLimit = rb.limit();
			rb.limit(currRead + rb.position());
			b.put(rb);
			rb.limit(oldLimit);
			fp += currRead;
		}
		int leftToRead = b.remaining();
		while (leftToRead > 0)
		{
			ensureProperFile(false, false);
			int currRead = fc.read(b);
			if (currRead < 0)
				throw new java.io.EOFException();
			fp += currRead;
			leftToRead -= currRead;
		}
	}

	public int transferTo(java.nio.channels.WritableByteChannel out, long length) throws java.io.IOException
	{
		long startFp = fp;
		if (rb != null && rb.remaining() > 0)
		{
			int currRead = (int)Math.min(rb.remaining(), length);
			int oldLimit = rb.limit();
			rb.limit(currRead + rb.position());
			out.write(rb);
			rb.limit(oldLimit);
			fp += currRead;
			length -= currRead;
		}
		while (length > 0)
		{
			ensureProperFile(false, false);
			long curr = fc.transferTo(fp - fileOffsets[currFileIndex], length, out);
			length -= curr;
			fp += curr;
			if (curr <= 0)
				break;
		}
		fc.position(fp - fileOffsets[currFileIndex]);
		return (int)(fp - startFp);
    }

	public void readFully(byte b[], int off, int len) throws java.io.IOException
	{
		if (rb != null && rb.remaining() > 0)
		{
			int currRead = Math.min(rb.remaining(), len);
			rb.get(b, off, currRead);
			fp += currRead;
			len -= currRead;
			off += currRead;
		}
		while (len > 0)
		{
			ensureProperFile(false, false);
			int currRead = fc.read(java.nio.ByteBuffer.wrap(b, off, len));
			if (currRead < 0)
				throw new java.io.EOFException();
			fp += currRead;
			off += currRead;
			len -= currRead;
	    }
	}

	public void write(int b) throws java.io.IOException
	{
		throw new java.io.IOException("Unsupported operation");
	}

    public void write(byte b) throws java.io.IOException
    {
		throw new java.io.IOException("Unsupported operation");
    }

    public void writeUnencryptedByte(byte b) throws java.io.IOException
    {
		throw new java.io.IOException("Unsupported operation");
    }

    public void write(byte b[], int off, int len) throws java.io.IOException
    {
		throw new java.io.IOException("Unsupported operation");
    }

    public void write(byte b[])	throws java.io.IOException
    {
		throw new java.io.IOException("Unsupported operation");
    }

    public void writeUTF(String s) throws java.io.IOException
    {
		throw new java.io.IOException("Unsupported operation");
    }

    public void writeBoolean(boolean b)	throws java.io.IOException
    {
		throw new java.io.IOException("Unsupported operation");
    }

    public void writeByte(int b) throws java.io.IOException
    {
		throw new java.io.IOException("Unsupported operation");
    }

	public final void setLength(long len) throws java.io.IOException
	{
		throw new java.io.IOException("Unsupported operation");
	}

	public int getChapter(long pts45)
	{
		for (int i = 0; i < chapterOffsets.length; i++)
			if (chapterOffsets[i] > pts45)
				return i;
		return chapterOffsets.length;
	}

	public long getChapterStartMsec(int chapter)
	{
		return chapterOffsets[Math.max(0, Math.min(chapter - 1, chapterOffsets.length - 1))] / 45;
	}

	public int getNumChapters()
	{
		return chapterOffsets.length;
	}

	public int getNumAngles()
	{
		return currPlaylist.playlistItems[currFileIndex].itemClips.length;
	}

	public int getNumTitles()
	{
		return bdp.getNumPlaylists();
	}

	public int getTitle()
	{
		return targetTitle;
	}

	public void setAngle(int currBDAngle)
	{
		// Narflex - This does NOT work because the files for each angle may be a different length; so our offsets would be off.
/*		if (prefAngle != currBDAngle - 1)
		{
			prefAngle = currBDAngle - 1;
			prefAngle = Math.max(0, prefAngle);
			try
			{
				ensureProperFile(true, true);
			}
			catch (java.io.IOException e)
			{
				System.out.println("ERROR in BluRay angle change:" + e);
			}
		}*/
	}

	public String getTitleDesc(int titleNum)
	{
		return bdp.getPlaylistDesc(titleNum - 1);
	}

	public sage.media.format.ContainerFormat getFileFormat()
	{
		return bdp.getFileFormat(targetTitle - 1);
	}

	protected long totalSize;
	protected java.io.File bdmvDir;
	protected sage.media.bluray.BluRayParser bdp;
	protected sage.media.bluray.MPLSObject currPlaylist;
	protected java.io.File[] fileSequence;
	protected long[] fileOffsets; // bytes
	protected long[] ptsOffsets; // 45kHz
	protected long[] chapterOffsets; // 45kHz
	protected int currFileIndex;
	protected java.io.File streamDir;
	protected int prefAngle;
	protected int targetTitle;
}
