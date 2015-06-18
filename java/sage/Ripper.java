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

/**
 *
 * @author  Narflex
 */
public class Ripper extends SystemTask
{
  private static Ripper globalRipper;
  public static Ripper getGlobalRipper()
  {
    if (globalRipper != null)
      return globalRipper;
    return globalRipper = new Ripper(Sage.get("default_ripper_device", "/dev/cdrom"));
  }

  /** Creates a new instance of Burner */
  public Ripper(String devName)
  {
    this.devName = devName;
  }

  public synchronized Object ripFilesFromCD(java.io.File targetDir, String ripBitrate)
  {
    ripDir = targetDir;

    bitrate = ripBitrate;
    int bitrateVal = 256000;
    try
    {
      bitrateVal = Integer.parseInt(bitrate) * 1000;
    }catch (Exception e){ System.out.println("Invalid bitrate:" + e);}

    // Assume it's a 74 minute CD so we have enough space
    long estimateSize = bitrateVal * 74 * 60 / 8;
    if (Sage.DBG) System.out.println("Estimated size for CD rip is: " + estimateSize);

    if (Sage.getBoolean("ripper/music_and_tv_share_partition", !Sage.WINDOWS_OS))
    {
      // Allocate space for the cd ripping
      allocateScratchSpace(estimateSize);
    }

    startTaskThread("CDRip");

    return Boolean.TRUE;
  }

  protected synchronized void taskRun()
  {
    // Get the essential programs for doing this
    String ripperPath = Sage.get("linux/cd_ripper", "cda");
    try
    {
      if (IOUtils.exec2(new String[] { ripperPath, "on" }) != 0)
      {
        statusMessage = "Error: " + ripperPath + " on";
        return;
      }

      if (abort) return;

      // Check to see if there's a disc in the drive
      String discMesg = IOUtils.exec(new String[] { ripperPath, "-batch", "status" });
      if (Sage.DBG) System.out.println("RipDiscStatus: " + discMesg);
      if (discMesg != null && discMesg.startsWith("No_Disc"))
      {
        statusMessage = "Error: " + Sage.rez("No_Disc");
        return;
      }

      if (abort) return;
      String s = IOUtils.exec(new String[] { ripperPath, "-batch", "mode", "standard" });
      s = IOUtils.exec(new String[] { ripperPath, "-batch", "mode", "cdda-save" });
      if (Sage.DBG) System.out.println("Set rip mode: " + s);

      // NOTE: The commented out settings are done in the configuration file for CDA

      //			s = Sage.exec(new String[] { ripperPath, "-batch", "trackfile", "on" });
      //CFG		if (Sage.DBG) System.out.println("Set track mode: " + s);

      //			s = Sage.exec(new String[] { ripperPath, "-batch", "filefmt", "mp3" });
      //CFG		if (Sage.DBG) System.out.println("Set format mode: " + s);
      if (abort) return;

      s = IOUtils.exec(new String[] { ripperPath, "-batch", "outfile", ripDir.getAbsolutePath() + java.io.File.separatorChar +
          "%A" + java.io.File.separatorChar + "%D" + java.io.File.separatorChar +
      "%#-%T.mp3"});
      if (Sage.DBG) System.out.println("Set outfile mode: " + s);

      //			s = IOUtils.exec(new String[] { ripperPath, "-batch", "compress", "2"/*VBR*/ });
      //			if (Sage.DBG) System.out.println("Set outfile mode: " + s);

      s = IOUtils.exec(new String[] { ripperPath, "-batch", "max-bitrate", bitrate });
      if (Sage.DBG) System.out.println("Set max bitrate: " + s);

      //			s = IOUtils.exec(new String[] { ripperPath, "-batch", "tag", "both"});
      //			if (Sage.DBG) System.out.println("Set tag mode: " + s);
      if (abort) return;

      if (Sage.DBG) System.out.println("Executing CD rip...");
      s = IOUtils.exec(new String[] { ripperPath, "-batch", "play" });
      if (Sage.DBG) System.out.println("Rip started: " + s);

      try{Thread.sleep(2000);}catch(Exception e){} // wait for status to update past stopped

      if (!launchMonitoredProcess(new String[] { ripperPath, "-batch", "status", "cont" }, "CD_Stopped"))
        return;
      if (abort) return;

      IOUtils.exec(new String[] { ripperPath, "-batch", "stop" });
      if (abort) return;
      if (Sage.DBG) System.out.println("CD BURNING IS COMPLETE!!!");
      succeeded();

      Seeker.getInstance().scanLibrary(false);

      // Now delete our temporary burn image
      clearScratch();
    }
    finally
    {
      IOUtils.exec(new String[] { ripperPath, "off" });
    }
  }

  protected String devName;

  protected java.io.File ripDir;
  protected String bitrate;
}
