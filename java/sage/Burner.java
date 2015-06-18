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

public class Burner extends SystemTask
{
  public static final int DVD_VIDEO = 1;
  public static final int CDDA_AUDIO = 2;
  public static final int MP3_AUDIO = 3;

  private static Burner globalBurner;
  public static Burner getGlobalBurner()
  {
    if (globalBurner != null)
      return globalBurner;
    return globalBurner = new Burner(Sage.get("default_burner_device", "/dev/cdrom"));
  }
  /** Creates a new instance of Burner */
  public Burner(String devName)
  {
    this.devName = devName;
  }

  public synchronized Object burnFilesToCD(MediaFile[] burnFiles)
  {
    if (!Sage.LINUX_OS) return Boolean.FALSE;
    int currDiscType = LinuxUtils.getOpticalDiscType();
    if (currDiscType != LinuxUtils.BLANK_CD)
    {
      if (currDiscType == LinuxUtils.NO_DISC)
      {
        return Sage.rez("No_Disc_Inserted_Please_Insert_a_blank_CD");
      }
      else
      {
        return Sage.rez("Invalid_Disc_Inserted_Please_Insert_a_blank_CD");
      }
    }
    burnType = CDDA_AUDIO;
    java.util.ArrayList rawFiles = new java.util.ArrayList();
    for (int i = 0; i < burnFiles.length; i++)
      rawFiles.addAll(java.util.Arrays.asList(burnFiles[i].getFiles()));
    rawVideoFiles = (java.io.File[])rawFiles.toArray(new java.io.File[0]);
    if (Sage.DBG) System.out.println("CD Burn requested for files: " + java.util.Arrays.asList(rawVideoFiles));

    // Find the total size of the files
    totalRawFileSize = 0;
    for (int i = 0; i < rawVideoFiles.length; i++)
      totalRawFileSize += rawVideoFiles[i].length();

    if (Sage.DBG) System.out.println("Total size of original source files for burn: " + totalRawFileSize);

    // For raw audio, we encode at 44.1kHz, 16 bits/channel = 1.411 Mbps = 176.4 kBytes/sec
    // Now we figure out how much space this'll use
    long totalDuration = 0;
    for (int i = 0; i < burnFiles.length; i++)
      totalDuration += burnFiles[i].getRecordDuration();

    long estimateSize = Math.round(totalDuration * 176.4);
    if (Sage.DBG) System.out.println("Estimated size of RAW audio data: " + estimateSize);

    // Find the temp directory where we can store the decompressed music files before we burn them
    allocateScratchSpace(estimateSize);
    if (Sage.DBG) System.out.println("Temp dir for burn data: " + scratchDir);

    startTaskThread("CDBurn");

    return Boolean.TRUE;
  }

  public synchronized Object burnFilesToDVD(java.io.File[] burnFiles)
  {
    if (!Sage.LINUX_OS) return Boolean.FALSE;
    int currDiscType = LinuxUtils.getOpticalDiscType();
    if (currDiscType != LinuxUtils.BLANK_DVD_MINUS_R && currDiscType != LinuxUtils.BLANK_DVD_PLUS_R)
    {
      if (currDiscType == LinuxUtils.NO_DISC)
      {
        return Sage.rez("No_Disc_Inserted_Please_Insert_a_blank_DVD");
      }
      else
      {
        return Sage.rez("Invalid_Disc_Inserted_Please_Insert_a_blank_DVD");
      }
    }
    rawVideoFiles = burnFiles;
    burnType = DVD_VIDEO;
    if (Sage.DBG) System.out.println("DVD Burn requested for files: " + java.util.Arrays.asList(rawVideoFiles));

    // Find the total size of the files
    totalRawFileSize = 0;
    for (int i = 0; i < rawVideoFiles.length; i++)
      totalRawFileSize += rawVideoFiles[i].length();

    if (Sage.DBG) System.out.println("Total size of original source files for burn: " + totalRawFileSize);

    // Find the temp directory where we can store the DVD folder structure we've created
    allocateScratchSpace(totalRawFileSize);
    if (Sage.DBG) System.out.println("Temp dir for burn data: " + scratchDir);

    startTaskThread("DVDBurn");
    return Boolean.TRUE;
  }

  public synchronized void taskRun() // only one at a time!
  {
    if (burnType == DVD_VIDEO)
      dvdBurnMagic();
    else
      cdBurnMagic();
  }

  private void cdBurnMagic()
  {
    statusMessage = "";
    // Get the essential programs for doing this
    String decoderPath = Sage.get("linux/audio_decoder", "lame");
    String burnPath = Sage.get("linux/cd_burn", "cdrecord");

    if (abort) return;
    java.io.File dvdImageDir = new java.io.File(scratchDir, "BURNIMAGE");
    if (Sage.DBG) System.out.println("Wiping out previous CD image:" + dvdImageDir);
    clearScratch();
    dvdImageDir.mkdirs();

    if (abort) return;
    if (Sage.DBG) System.out.println("Setting up raw file creation...");
    // For each file we're burning, we need to setup the FIFOs
    java.io.File[] decodedFiles = new java.io.File[rawVideoFiles.length];
    for (int i = 0; i < rawVideoFiles.length; i++)
    {
      try
      {
        decodedFiles[i] = java.io.File.createTempFile("burnprep", ".wav", dvdImageDir);
        if (!launchMonitoredProcess(new String[] { decoderPath, "--decode", rawVideoFiles[i].getAbsolutePath(), decodedFiles[i].getAbsolutePath() }))
        {
          statusMessage = "Error: " + decoderPath + " " + rawVideoFiles[i].getAbsolutePath() + " " +
              decodedFiles[i].getAbsolutePath();
          return;
        }
      }
      catch (java.io.IOException e)
      {
        statusMessage = "Error: " + e.toString();
        return;
      }
    }
    if (abort) return;

    if (Sage.DBG) System.out.println("Executing CD burn...");
    boolean burnSuccess;
    if (Sage.getBoolean("linux/cd_burn_test_only", false))
      burnSuccess = launchMonitoredProcess(new String[] { "sh", "-c", burnPath + " -v -dummy -dao " +
          "dev=" + devName + " speed=8 -pad -audio " + dvdImageDir.getAbsolutePath() + java.io.File.separatorChar +
      "*.wav" });
    else
      burnSuccess = launchMonitoredProcess(new String[] { "sh", "-c", burnPath + " -v -dao " +
          "dev=" + devName + " speed=8 -pad -audio " + dvdImageDir.getAbsolutePath() + java.io.File.separatorChar +
      "*.wav" });
    if (!burnSuccess) return;

    if (Sage.DBG) System.out.println("CD BURNING IS COMPLETE!!!");
    succeeded();

    // Now delete our temporary burn image
    clearScratch();
  }

  private void dvdBurnMagic()
  {
    statusMessage = "";
    // Get the essential programs for doing this
    //		String demuxPath = Sage.get("linux/mpeg_demuxer", "mpeg2desc");
    //		String muxPath = Sage.get("linux/mpeg_nav_muxer", "mplex");
    String authorPath = Sage.get("linux/dvd_author", new java.io.File("dvdauthor").isFile() ? "./dvdauthor" : "dvdauthor");
    String burnPath = Sage.get("linux/dvd_burn", "growisofs");

    if (abort) return;
    java.io.File dvdImageDir = new java.io.File(scratchDir, "BURNIMAGE");
    // Clear out the DVD image folder so dvdauthor doesn't do an append
    if (Sage.DBG) System.out.println("Wiping out previous DVD image:" + dvdImageDir);
    clearScratch();
    dvdImageDir.mkdirs();
    java.io.File authorXML = new java.io.File(scratchDir, "dvd.xml");
    // Create the XML file for the DVD burn
    try
    {
      java.io.PrintWriter xmlStream = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(authorXML)));
      xmlStream.println("<dvdauthor dest=\"" + dvdImageDir.getAbsolutePath() + "\">");
      xmlStream.println("<vmgm />");
      xmlStream.println("<titleset>");
      xmlStream.println("<titles>");
      xmlStream.println("<pgc>");
      for (int i = 0; i < rawVideoFiles.length; i++)
      {
        xmlStream.println("<vob file=\"" + new java.io.File(scratchDir, "dvdmpg" + i).getAbsolutePath() + "\" />");
      }
      xmlStream.println("</pgc>");
      xmlStream.println("</titles>");
      xmlStream.println("</titleset>");
      xmlStream.println("</dvdauthor>");
      xmlStream.close();
      if (Sage.DBG) System.out.println("Wrote XML dvdauthor file:" + authorXML);
    }
    catch (java.io.IOException e)
    {
      statusMessage = "Error: Error creating XML file " + e.toString();
      return;
    }

    if (abort) return;
    /*
		if (Sage.DBG) System.out.println("Setting up demux/mux/author creation...");
		// For each file we're burning, we need to setup the FIFOs
		for (int i = 0; i < rawVideoFiles.length; i++)
		{
			if (IOUtils.exec2("mkfifo " + new java.io.File(scratchDir, "aud" + i).getAbsolutePath()) != 0)
			{
				statusMessage = "Error: mkfifo " + new java.io.File(scratchDir, "aud" + i).getAbsolutePath();
				return;
			}
			if (IOUtils.exec2("mkfifo " + new java.io.File(scratchDir, "vid" + i).getAbsolutePath()) != 0)
			{
				statusMessage = "Error: mkfifo " + new java.io.File(scratchDir, "vid" + i).getAbsolutePath();
				return;
			}
			if (IOUtils.exec2("mkfifo " + new java.io.File(scratchDir, "dvdmpg" + i).getAbsolutePath()) != 0)
			{
				statusMessage = "Error: mkfifo " + new java.io.File(scratchDir, "dvdmpg" + i).getAbsolutePath();
				return;
			}
			// These are background tasks so we don't know if they succeeded or not
			IOUtils.exec2(new String[] { "sh", "-c", demuxPath + " -a0 < \"" + rawVideoFiles[i].getAbsolutePath() +
				"\" > " + new java.io.File(scratchDir, "aud" + i).getAbsolutePath()}, false);
			IOUtils.exec2(new String[] { "sh", "-c", demuxPath + " -v0 < \"" + rawVideoFiles[i].getAbsolutePath() +
				"\" > " + new java.io.File(scratchDir, "vid" + i).getAbsolutePath()}, false);
			IOUtils.exec2(new String[] { muxPath , "-f", "8", "-V", "-S", "0", "-o", new java.io.File(scratchDir, "dvdmpg" + i).getAbsolutePath(),
				new java.io.File(scratchDir, "aud" + i).getAbsolutePath(), new java.io.File(scratchDir, "vid" + i).getAbsolutePath()}, false);
		}*/
    if (Sage.DBG) System.out.println("Remuxing file into DVD nav compatible format...");
    for (int i = 0; i < rawVideoFiles.length; i++)
    {
      if (!launchMonitoredProcess(new String[] { FFMPEGTranscoder.getTranscoderPath(), "-y", "-i", rawVideoFiles[i].getAbsolutePath(),
          "-f", "dvd", "-vcodec", "copy", "-acodec", "copy", "-copyts",
          new java.io.File(scratchDir, "dvdmpg" + i).getAbsolutePath()}))
      {
        statusMessage = "Error: DVD remuxing failed for " + rawVideoFiles[i];
        return;
      }
    }

    if (Sage.DBG) System.out.println("Executing authoring process...");
    if (abort) return;
    if (!launchMonitoredProcess(new String[] { authorPath, "-o",
        dvdImageDir.getAbsolutePath(), "-x", authorXML.getAbsolutePath() }))
      return;

    // Now we can remove all of the fifos
    /*		for (int i = 0; i < rawVideoFiles.length; i++)
		{
			IOUtils.exec2(new String[] { "rm", new java.io.File(scratchDir, "aud" + i).getAbsolutePath() });
			IOUtils.exec2(new String[] { "rm", new java.io.File(scratchDir, "vid" + i).getAbsolutePath() });
			IOUtils.exec2(new String[] { "rm", new java.io.File(scratchDir, "dvdmpg" + i).getAbsolutePath() });
		}*/

    if (abort) return;

    if (Sage.DBG) System.out.println("Executing DVD burn...");
    boolean burnRes;
    if (Sage.getBoolean("linux/dvd_burn_test_only", false))
      burnRes = launchMonitoredProcess(new String[] { burnPath, "-dry-run",
          "-dvd-compat", "-Z", devName, "-dvd-video", dvdImageDir.getAbsolutePath() });
    else
      burnRes = launchMonitoredProcess(new String[] { burnPath,
          "-dvd-compat", "-Z", devName, "-dvd-video", dvdImageDir.getAbsolutePath() });

    if (!burnRes) return;

    if (Sage.DBG) System.out.println("DVD BURNING IS COMPLETE!!!");
    succeeded();

    // Now delete our temporary burn image
    if (Sage.DBG) System.out.println("Deleting temporary burn dir " + scratchDir.getAbsolutePath());
    clearScratch();
  }

  protected String devName;
  protected long totalRawFileSize;
  protected java.io.File[] rawVideoFiles;

  protected int burnType;
}
