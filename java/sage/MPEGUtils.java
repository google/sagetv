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

public class MPEGUtils
{
	private MPEGUtils()
	{
	}

	public static boolean isStartCode(byte[] data, int off)
	{
		return data[off] == 0 && data[off + 1] == 0 && data[off + 2] == 1;
	}

	public static long getMPEG2InitialTimestamp(java.io.File file)
	{
		if (file == null) return 0;
		long firstPTS = 0;
		FasterRandomFile frf = null;
		try
		{
			if (Sage.DBG) System.out.println("Finding initial PTS for MPEG2 PS file:" + file);
			frf = new FasterRandomFile(file, "r", Sage.I18N_CHARSET);
			// Find the first PTS to get a baseline
			byte[] pbData = new byte[65536];
			long dwLeft = Math.min(frf.length(), pbData.length);
			frf.readFully(pbData);
			int off = 0;
			while (dwLeft > 13)
			{
				/*  Find a start code */
				if (isStartCode(pbData, off))
				{
//					if (Sage.DBG) System.out.println("Found a start code 0x" + Integer.toString(pbData[off+3] & 0xFF, 16));
					// Check for a video or audio stream
					if ((pbData[off + 3] & 0xFF) == 0xE0 || (pbData[off + 3] & 0xFF) == 0xC0 ||
						 (pbData[off + 3] & 0xFF) == 0xBD)
					{
						// Check for a valid PES packet
						if ((pbData[off + 6] & 0xC0) == 0x80)
						{
							// Check for the PTS flag
							int ptsFlag = (pbData[off + 7] & 0xC0) >> 6;
							if (ptsFlag == 2 || ptsFlag == 3)
							{
								// Check for valid PTS marker bits
								if (((pbData[off + 9] & 0xF0) >> 4) == ptsFlag &&
									(pbData[off + 9] & 0x01) == 1 &&
									(pbData[off + 11] & 0x01) == 1 &&
									(pbData[off + 13] & 0x01) == 1)
								{
									// Read the PTS bits
									firstPTS += (((long)(pbData[off + 9] & 0x0E)) << 29);
									firstPTS += ((pbData[off + 10] & 0xFF) << 22);
									firstPTS += ((pbData[off + 11] & 0xFE) << 14);
									firstPTS += ((pbData[off + 12] & 0xFF) << 7);
									firstPTS += ((pbData[off + 13] & 0xFE) >> 1);
		//							if (Sage.DBG) System.out.println("Found PTS:" + firstPTS);
									break;
								}
							}
						}
						dwLeft -= 14;
						off += 14;
					}
					else
					{
						dwLeft -= 4;
						off += 4;
					}
				}
				else
				{
					dwLeft--;
					off++;
				}
			}
		}
		catch (Exception e)
		{
			System.out.println("ERROR parsing MPEG2 file of:" + e);
			e.printStackTrace();
		}
		finally
		{
			if (frf != null)
			{
				try
				{
					frf.close();
					frf = null;
				}
				catch (Exception e){}
			}
		}
		return firstPTS/90;
	}
	public static long getMPEG2PSFileDuration(java.io.File file)
	{
		if (file == null) return 0;
		long filelen = file.length();
		if (filelen == 0) return 0;
		// Calculate the duration since we have the reader to get the length, that's for
		// active files. For completed files, we read the last PTS that's in the file
		// and use that, much more accurate.
		long maxPTS = 0;
		long firstPTS = 0;
		FasterRandomFile frf = null;
		try
		{
			if (Sage.DBG) System.out.println("Finding duration for MPEG2 PS file:" + file);
			frf = new FasterRandomFile(file, "r", Sage.I18N_CHARSET);
			// Find the first PTS to get a baseline
			byte[] pbData = new byte[65536];
			long dwLeft = Math.min(pbData.length, frf.length());
			frf.readFully(pbData, 0, (int)dwLeft);
			int off = 0;
			while (dwLeft > 13)
			{
				/*  Find a start code */
				if (pbData[off] == 0 && pbData[off + 1] == 0 && pbData[off + 2] == 1)
				{
//					if (Sage.DBG) System.out.println("Found a start code 0x" + Integer.toString(pbData[off+3] & 0xFF, 16));
					// Check for a video or audio stream
					if ((pbData[off + 3] & 0xFF) == 0xE0 || (pbData[off + 3] & 0xFF) == 0xC0 ||
						 (pbData[off + 3] & 0xFF) == 0xBD)
					{
						// Check for a valid PES packet
						if ((pbData[off + 6] & 0xC0) == 0x80)
						{
							// Check for the PTS flag
							int ptsFlag = (pbData[off + 7] & 0xC0) >> 6;
							if (ptsFlag == 2 || ptsFlag == 3)
							{
								// Check for valid PTS marker bits
								if (((pbData[off + 9] & 0xF0) >> 4) == ptsFlag &&
									(pbData[off + 9] & 0x01) == 1 &&
									(pbData[off + 11] & 0x01) == 1 &&
									(pbData[off + 13] & 0x01) == 1)
								{
									// Read the PTS bits
									firstPTS += (((long)(pbData[off + 9] & 0x0E)) << 29);
									firstPTS += ((pbData[off + 10] & 0xFF) << 22);
									firstPTS += ((pbData[off + 11] & 0xFE) << 14);
									firstPTS += ((pbData[off + 12] & 0xFF) << 7);
									firstPTS += ((pbData[off + 13] & 0xFE) >> 1);
									if (Sage.DBG) System.out.println("Found PTS:" + firstPTS);
									break;
								}
							}
						}
						dwLeft -= 14;
						off += 14;
					}
					else
					{
						dwLeft -= 4;
						off += 4;
					}
				}
				else
				{
					dwLeft--;
					off++;
				}
			}
			long skipAmount = Math.max(0, filelen - 7*65536);
			while (skipAmount > 0)
			{
				// This gets around another 32 bit bug with Java and files on Linux....actually, it may be a Samba bug.
				long currSkip = Math.min(skipAmount, Integer.MAX_VALUE/2);
				currSkip = frf.skipBytes((int)currSkip);
				skipAmount -= currSkip;
			}
			for (int county = 8; county > 0; county--)
			{
				dwLeft = Math.min(pbData.length, frf.length() - frf.getFilePointer());
				frf.readFully(pbData, 0, (int)dwLeft);
				off = 0;
				while (dwLeft > 13)
				{
					/*  Find a start code */
					if (pbData[off] == 0 && pbData[off + 1] == 0 && pbData[off + 2] == 1)
					{
//						if (Sage.DBG) System.out.println("Found a start code 0x" + Integer.toString(pbData[off+3] & 0xFF, 16));
						// Check for a video or audio stream
						if ((pbData[off + 3] & 0xFF) == 0xE0 || (pbData[off + 3] & 0xFF) == 0xC0 ||
							 (pbData[off + 3] & 0xFF) == 0xBD)
						{
							// Check for a valid PES packet
							if ((pbData[off + 6] & 0xC0) == 0x80)
							{
								// Check for the PTS flag
								int ptsFlag = (pbData[off + 7] & 0xC0) >> 6;
								if (ptsFlag == 2 || ptsFlag == 3)
								{
									// Check for valid PTS marker bits
									if (((pbData[off + 9] & 0xF0) >> 4) == ptsFlag &&
										(pbData[off + 9] & 0x01) == 1 &&
										(pbData[off + 11] & 0x01) == 1 &&
										(pbData[off + 13] & 0x01) == 1)
									{
										// Read the PTS bits
										long currPTS = 0;
										currPTS += (((long)(pbData[off + 9] & 0x0E)) << 29);
										currPTS += ((pbData[off + 10] & 0xFF) << 22);
										currPTS += ((pbData[off + 11] & 0xFE) << 14);
										currPTS += ((pbData[off + 12] & 0xFF) << 7);
										currPTS += ((pbData[off + 13] & 0xFE) >> 1);
										maxPTS = Math.max(currPTS, maxPTS);
										if (Sage.DBG) System.out.println("Found PTS:" + currPTS);
									}
								}
								int packetLength = ((pbData[off + 4] & 0xFF) << 8) + (pbData[off + 5] & 0xFF);
								// TS has zero length size in the PES header so don't let those cause us to loop forever
								packetLength = Math.max(packetLength, 14);
								dwLeft -= packetLength;
								off += packetLength;
							}
							else
							{
								dwLeft -= 4;
								off += 4;
							}
						}
						else
						{
							dwLeft -= 4;
							off += 4;
						}
					}
					else
					{
						dwLeft--;
						off++;
					}
				}
			}
		}
		catch (Exception e)
		{
			System.out.println("ERROR parsing MPEG2 file of:" + e);
			e.printStackTrace();
			return 0;
		}
		finally
		{
			if (frf != null)
			{
				try
				{
					frf.close();
					frf = null;
				}
				catch (Exception e){}
			}
		}

	    if ( maxPTS < firstPTS )
        {   long cyc=0x200000000L;
            maxPTS = cyc + maxPTS; //ZQ, PTS wrap around
            if (Sage.DBG) System.out.println("PTS round :" + cyc + " " + maxPTS );
        }

		if (Sage.DBG) System.out.println("MPEG2 PS file duration: " + ((maxPTS-firstPTS)/90) + " msec");
		return (maxPTS - firstPTS)/90; // convert from 1/90000 of a second to milliseconds
	}
}
