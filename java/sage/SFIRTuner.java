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

/*
 * This class is a wrapper for the IRTuner dll interface from SourceForge.
 */
public class SFIRTuner implements Runnable
{
  static
  {
    //sage.Native.loadLibrary("Sage");
  }
  public static final String REMOTE_DIR = "remote_dir";
  public static final String IRTUNE_REPEAT_FACTOR = "irtune_repeat_factor";
  public static final String IRTUNE_PREFIX_EXTRA_DELAY = "irtune_prefix_extra_delay";
  public static final String USB_IRTUNE_REPEAT_FACTOR = "usb_irtune_repeat_factor";
  public static final String IRTUNE_GLOBAL_PREROLL = "actisys_irtune_global_preroll";
  public static final String USBIRTUNE_GLOBAL_PREROLL = "usbuirt_irtune_global_preroll";
  public static final String ASYNC_TUNING = "async_tuning";
  public static class Pattern
  {
    public int bit_length;
    public int length;
    public char r_flag;
    public byte[] bytes;
    public Pattern next;

    public String toString()
    {
      return "Pattern[bit_length=" + bit_length + ", length=" + length + ", r_flag=" + r_flag +
          ", next=" + next + ']';
    }
  }

  public static class Command
  {
    public String name;
    public Pattern pattern;
    public Command next;

    public String toString()
    {
      return "Command[name=" + name + ", pattern=" + pattern + ", next=" + next + ']';
    }
  }

  public static class Remote
  {
    public String name;
    public long carrier_freq;
    public long bit_time;
    public Command command;
    public Remote next;

    // SageTV added fields
    public int channelDigits;
    public String confirmCmd;
    public int buttonDelay;
    public int sequenceDelay;
    public String prefixCmd;

    public String toString()
    {
      return "Remote[name=" + name + ", carrier=" + carrier_freq + ", bit_time=" + bit_time +
          ", command=" + command + ", next=" + next + ']';
    }
  }

  public static native String[] getValidDeviceFiles(String[] tryFiles);
  public static native String[] getPrettyDeviceNames(String[] validFiles);

  public static String getSFIRTunerPluginDir()
  {
    String theDir = null;

    if(Sage.WINDOWS_OS)
      theDir = Sage.readStringValue(Sage.HKEY_LOCAL_MACHINE, "SOFTWARE\\Frey Technologies\\Common", "IRTunerPluginsDir");
    else if(Sage.LINUX_OS)
      theDir = Sage.get("irtuner_plugins_dir", "irtunerplugins");

    // this guarantees we return a valid path (user.dir or Plug-Ins on Mac OS X)
    if(theDir == null) theDir = Sage.getPath("plugins");

    //		System.out.println("getSFIRTunerPluginDir: dir = " + theDir);
    return theDir;
  }

  public static String getPrettyNameForFile(String inFilename)
  {
    java.io.File testFile = new java.io.File(inFilename);
    if (!testFile.isFile())
    {
      // Look in the global directory
      String globalIRDir = getSFIRTunerPluginDir();
      if (globalIRDir != null)
        testFile = new java.io.File(globalIRDir, inFilename);
    }
    inFilename = testFile.getAbsolutePath();
    String[] rv = getPrettyDeviceNames(new String[] { inFilename });
    return (rv != null && rv.length > 0) ? rv[0] : inFilename;
  }
  private static final java.util.Map prettyNameMap = java.util.Collections.synchronizedMap(new java.util.HashMap());
  public static String getFileForPrettyDeviceName(String prettyName)
  {
    if (prettyNameMap.get(prettyName) != null)
      return prettyNameMap.get(prettyName).toString();
    String irPluginDir = getSFIRTunerPluginDir();
    java.io.File[] suspectDLLFiles = new java.io.File(irPluginDir).
        listFiles(new java.io.FilenameFilter(){
          public boolean accept(java.io.File dir,String filename){return filename.toLowerCase().endsWith(Sage.WINDOWS_OS ? ".dll" :
            (Sage.LINUX_OS ? ".so" : ".dylib"));}});
    String[] suspectDLLs = (suspectDLLFiles == null) ? Pooler.EMPTY_STRING_ARRAY : new String[suspectDLLFiles.length];
    for (int i = 0; i < suspectDLLs.length; i++)
      suspectDLLs[i] = suspectDLLFiles[i].getAbsolutePath();
    String[] irDevFiles = getValidDeviceFiles(suspectDLLs);
    String[] allPretty = getPrettyDeviceNames(irDevFiles);
    for (int i = 0; i < allPretty.length; i++)
      if (allPretty[i].equals(prettyName))
      {
        prettyNameMap.put(prettyName, irDevFiles[i]);
        return irDevFiles[i];
      }
    prettyNameMap.put(prettyName, prettyName);
    return prettyName;
  }

  /*
   * Cmd line params
   * -r name : create remote with name, calculates bitrate & carrier
   * -c rname cname : record command 'cname' to the remote 'rname'
   * -l name : load the remotes from this filename
   * -s name : save the remotes to this filename
   * -p rname cname repeat : play the command 'cname' from remote 'rname' repeat times
   * -w time : wait for time seconds
   * -i : run initDevice
   * -x comport : open comport #
   */
  /*public static void main(String[] args)
	{
		if (args.length == 0)
		{
			System.out.println("Usage:");
			System.out.println("-r name : create remote with name, calculates bitrate & carrier");
			System.out.println("-c rname cname : record command 'cname' to the remote 'rname'");
			System.out.println("-l name : load the remotes from this filename");
			System.out.println("-s name : save the remotes to this filename");
			System.out.println("-p rname cname repeat : play the command 'cname' from remote 'rname' repeat times");
			System.out.println("-w time : wait for time seconds");
			System.out.println("-i : run initdevice");
			System.out.println("-x comport : open comport #");
			return;
		}
		String[] dllFiles = new java.io.File(System.getProperty("user.dir")).list(new java.io.FilenameFilter()
		{
			public boolean accept(java.io.File dir,String filename){return filename.endsWith(".dll");}
		});
		System.out.println("dllFiles=" + java.util.Arrays.asList(dllFiles));
		String[] validFiles = getValidDeviceFiles(dllFiles);
		System.out.println("validFiles=" + java.util.Arrays.asList(validFiles));

		SFIRTuner tuney = new SFIRTuner(validFiles[0]);
		for (int i = 0; i < args.length; i++)
		{
			if (args[i].equals("-r"))
			{
				String rname = args[++i];
				System.out.println("Create remote named " + rname);
				long carrier=0, bitrate=0;
				if (tuney.needCarrierFrequency())
				{
					while (carrier == 0)
					{
						System.out.println("Hold a remote button down for a while. Scanning for frequency...");
						carrier = tuney.findCarrierFrequency();
						System.out.println("Carrier frequency=" + carrier);
						if (carrier > 100000)
						{
							System.out.println("BAD CARRIER, do it again!");
							carrier = 0;
						}
					}
				}
				if (tuney.needBitrate())
				{
					System.out.println("Hold a remote button down for a while. Calculating bitrate...");
					bitrate = tuney.findBitRate();
					System.out.println("Bitrate=" + bitrate);
				}
				Remote newRem = tuney.createRemote(rname, carrier, bitrate, null);
				tuney.addRemote(newRem);
				System.out.println("Created & added remote " + newRem);
			}
			else if (args[i].equals("-c"))
			{
				String rname = args[++i];
				String cname = args[++i];
				Remote rem = tuney.findRemote(rname);
				if (rem == null)
				{
					System.out.println("Can't find remote named:" + rname);
					continue;
				}
				System.out.println("Hit the " + cname + " key for remote " + rname);
				Command cmd = tuney.recordCommand(cname);
				System.out.println("Recorded command:" + cmd);
				tuney.addCommand(rem, cmd);
			}
			else if (args[i].equals("-l"))
			{
				String fname = args[++i];
				System.out.println("Loading remotes from filename:" + fname);
				tuney.loadRemotes(fname);
				System.out.println("Remotes=" + tuney.baseRemote);
			}
			else if (args[i].equals("-s"))
			{
				String fname = args[++i];
				System.out.println("Saving remotes to filename:" + fname);
				tuney.saveRemotes(fname);
				System.out.println("Remotes=" + tuney.baseRemote);
			}
			else if (args[i].equals("-p"))
			{
				String rname = args[++i];
				String cname = args[++i];
				int rep = Integer.parseInt(args[++i]);
				System.out.println("Starting to play command " + cname + " for remote " + rname + " " + rep + " times");
				tuney.playCommand(tuney.findRemote(rname), cname, rep);
				System.out.println("Done playing command");
			}
			else if (args[i].equals("-w"))
			{
				try{Thread.sleep(1000*Integer.parseInt(args[++i]));}catch(Exception e){}
			}
			else if (args[i].equals("-i"))
				tuney.initDevice();
			else if (args[i].equals("-x"))
			{
				int comport = Integer.parseInt(args[++i]);
				boolean openD = tuney.openDevice(comport);
				if (!openD)
				{
					System.out.println("Failed opening COM port. Trying again!");
					tuney.closeDevice();
					openD = tuney.openDevice(comport);
					if (!openD)
					{
						System.out.println("Failed opening COM port. Darn!");
						return;
					}
				}
				System.out.println("Opened com port " + openD);
			}
		}

		tuney.closeDevice();
		System.out.println("Closed COM port");
	}*/

  private static java.util.Vector loadedTuneys = new java.util.Vector();

  /*
   * The carrier & bit timing for the hardware gets set in initDevice. The values
   * it uses are from the last remote it loaded via a call to loadRemotes
   */
  public SFIRTuner(String inFilename)
  {
    java.io.File testFile = new java.io.File(inFilename);
    String globalIRDir = getSFIRTunerPluginDir();
    if (!testFile.isFile())
    {
      // Look in the global directory
      if (globalIRDir != null)
        testFile = new java.io.File(globalIRDir, inFilename);
    }
    inFilename = testFile.getAbsolutePath();

    if (Sage.WINDOWS_OS && getValidDeviceFiles(new String[] { inFilename} ).length == 0)
    {
      System.err.println("Invalid device filename for IRTuner: " + inFilename);
    }
    devFilename = inFilename;
    if (globalIRDir != null)
      remoteDir = new java.io.File(globalIRDir, "RemoteCodes");
    else
      remoteDir = new java.io.File(Sage.getPath("plugins")/*System.getProperty("user.dir")*/, "RemoteCodes");

    if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
    {
      remoteDir.mkdirs();
      String[] prettyNames = getPrettyDeviceNames(new String[] { devFilename });
      if (prettyNames != null && prettyNames.length > 0)
      {
        remoteDir2 = new java.io.File(remoteDir, prettyNames[0]);
        remoteDir2.mkdirs();
      }
    }
    else
      remoteDir2 = new java.io.File(remoteDir, devFilename);

    asyncTuning = Sage.getBoolean(ASYNC_TUNING, true);
    tuneVec = new java.util.Vector();
    globalPreroll = 0;

    initialize();
  }

  private void initialize()
  {
    checkForTuneyConflicts();
    init0();

    alive = true;
    if (asyncTuning)
    {
      asyncThread = new Thread(this, "AsyncTuner");
      asyncThread.setDaemon(true);
      asyncThread.setPriority(Thread.MAX_PRIORITY - 3);
      asyncThread.start();
      if (Sage.WINDOWS_OS)
      {
        globalPreroll = (devFilename.toLowerCase().indexOf("uu_irsage") == -1) ? Sage.getLong(IRTUNE_GLOBAL_PREROLL, 2000L) :
          Sage.getLong(USBIRTUNE_GLOBAL_PREROLL, 150);
      }
      else
        globalPreroll = Sage.getLong(USBIRTUNE_GLOBAL_PREROLL, 0);
    }
    loadedTuneys.add(this);
  }

  private void checkForTuneyConflicts()
  {
    // We can't have more than one Actisys plugin open at once, so shut down any others if this is one
    if (devFilename.startsWith("as_ir200l"))
    {
      for (int i = 0; i < loadedTuneys.size(); i++)
      {
        SFIRTuner tuney = (SFIRTuner) loadedTuneys.get(i);
        if (tuney.devFilename.equals(devFilename))
        {
          System.out.println("SFIRTuner shutting down tuning plugin due to conflict");
          tuney.goodbye();
        }
      }
    }
  }

  public boolean isConfigurable()
  {
    return !canMacroTune();
  }

  public void run()
  {
    Object[] tuneData = null;
    while (alive)
    {
      String nextTune = null;
      String nextRemote = null;
      synchronized (tuneVec)
      {
        if (tuneData != null)
        {
          tuneVec.remove(tuneData);
          tuneData = null;
        }
        if (tuneVec.isEmpty())
        {
          tuneVec.notifyAll();
          try{tuneVec.wait(0);}catch(InterruptedException e){}
          continue;
        }
        tuneData = (Object[]) tuneVec.lastElement();
        nextRemote = (String) tuneData[0];
        nextTune = (String) tuneData[1];
        // Only send the last channel change command for a given remote since any prior
        // ones will be overidden by it.
        for (int i = tuneVec.size() - 2; i >= 0; i--)
        {
          Object[] tempTuneData = (Object[]) tuneVec.get(i);
          if (tempTuneData[0].equals(nextRemote))
            tuneVec.removeElementAt(i);
        }
      }
      if (globalPreroll != 0 && !canMacroTune())
        try{ Thread.sleep(globalPreroll); } catch(Exception e){}
      playTuneString(nextRemote, nextTune);
    }
  }

  public String[] getRemoteNames()
  {
    if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
    {
      java.util.ArrayList rv = new java.util.ArrayList();
      String[] irFiles = remoteDir.list(new java.io.FilenameFilter()
      {
        public boolean accept(java.io.File dir,String filename){return filename.endsWith(".ir") &&
            hasRemoteFileData(filename.substring(0, filename.length() - 3));}
      });
      if (irFiles != null)
      {
        for (int i = 0; i < irFiles.length; i++)
          rv.add(irFiles[i].substring(0, irFiles[i].length() - 3));
      }
      irFiles = remoteDir2.list(new java.io.FilenameFilter()
      {
        public boolean accept(java.io.File dir,String filename){return filename.endsWith(".ir") &&
            hasRemoteFileData(filename.substring(0, filename.length() - 3));}
      });
      if (irFiles != null)
      {
        for (int i = 0; i < irFiles.length; i++)
          rv.add(irFiles[i].substring(0, irFiles[i].length() - 3));
      }
      return (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
    }
    else
    {
      // This means load the whole remote list
      synchronized (this)
      {
        loadRemotes(null);
        java.util.ArrayList rv = new java.util.ArrayList();
        Remote tempRemote = baseRemote;
        while (tempRemote != null)
        {
          rv.add(tempRemote.name);
          tempRemote = tempRemote.next;
        }
        baseRemote = null; // since they don't load fully this way
        String[] rvArray = (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
        java.util.Arrays.sort(rvArray);
        return rvArray;
      }
    }
  }

  public synchronized void goodbye()
  {
    boolean needToKill = alive; // Don't close the hardware if it's not active or it may crash!
    alive = false;
    synchronized (tuneVec)
    {
      tuneVec.notifyAll();
    }
    if (needToKill)
    {
      closeDevice();
      goodbye0();
    }
    loadedTuneys.remove(this);
  }

  private void addCommand(Remote daRemote, Command addMe)
  {
    Command cmdList = daRemote.command;
    if (cmdList == null)
    {
      daRemote.command = addMe;
      return;
    }
    while (cmdList.next != null)
      cmdList = cmdList.next;
    cmdList.next = addMe;
  }

  /*	private void addRemote(Remote addMe)
	{
		if (baseRemote == null)
			baseRemote = addMe;
		else
		{
			Remote currRemote = baseRemote;
			while (currRemote.next != null)
				currRemote = currRemote.next;
			currRemote.next = addMe;
		}
	}

/*	private Remote findRemote(String name)
	{
		Remote rem = baseRemote;
		while (rem != null)
		{
			if (name.equals(rem.name))
				return rem;
			rem = rem.next;
		}
		return null;
	}
   */
  private native void closeDevice();
  private Remote createRemote(String remoteName, long carrier, long bitrate, Command commands)
  {
    Remote rv = new Remote();
    rv.name = remoteName;
    rv.carrier_freq = carrier;
    rv.bit_time = bitrate;
    rv.command = commands;
    rv.buttonDelay = Sage.WINDOWS_OS ? 600 : 800;
    rv.sequenceDelay = 800;
    rv.channelDigits = 3;
    return rv;
  }

  public synchronized void playCommand(String remoteName, String cmdName, int repeats, boolean sleepAfter)
  {
    if (!ensureRemoteLoaded(remoteName)) return;
    long waitNow = baseRemote.buttonDelay + baseRemote.sequenceDelay - (Sage.eventTime() - lastIRTime);
    if (waitNow > 0)
    {
      try
      {
        Thread.sleep(waitNow);
      } catch(Exception e){}
    }
    if (!Sage.WINDOWS_OS && devFilename.endsWith("PVR150Tuner.so") && UIManager.getLocalUI() != null)
    {
      // Sync the PVR150 xmt & recv
      if (UIManager.getLocalUI().getRouter() == null)
      {
        playCommand(baseRemote, cmdName, repeats);
        //				if (sleepAfter) // We get I2C failures if we don't wait at least 350 msec after a send
        {
          try
          {
            Thread.sleep(baseRemote.buttonDelay);
          } catch(Exception e){}
        }
      }
      else
      {
        synchronized (UIManager.getLocalUI().getRouter().getDefaultInputPlugin())
        {
          //System.out.println("PVR150 SyncBlock Enter");
          playCommand(baseRemote, cmdName, repeats);
          //					if (sleepAfter) // We get I2C failures if we don't wait at least 350 msec after a send
          {
            try
            {
              Thread.sleep(baseRemote.buttonDelay);
            } catch(Exception e){}
          }
          //System.out.println("PVR150 SyncBlock Exit");
        }
      }
    }
    else
    {
      playCommand(baseRemote, cmdName, repeats);
      if (sleepAfter)
      {
        try
        {
          Thread.sleep(baseRemote.buttonDelay);
        } catch(Exception e){}
      }
    }
  }

  private int getRepeatFactor()
  {
    if (devFilename.toLowerCase().indexOf("uu_irsage") != -1)
      return Sage.getInt(USB_IRTUNE_REPEAT_FACTOR, 2);
    else
      return Sage.getInt(IRTUNE_REPEAT_FACTOR, Sage.LINUX_OS ? 1 : 2);
  }

  public void playTuneString(String remoteName, String cmdString)
  {
    playTuneString(remoteName, cmdString, false);
  }
  public void playTuneString(String remoteName, String cmdString, boolean forceSynchronous)
  {
    if (cmdString == null || cmdString.length() == 0) return;
    if (!forceSynchronous && asyncTuning && Thread.currentThread() != asyncThread)
    {
      synchronized (tuneVec)
      {
        tuneVec.addElement(new Object[] { remoteName, cmdString });
        tuneVec.notifyAll();
      }
      return;
    }
    synchronized (this)
    {
      if (Sage.DBG) System.out.println("Playing IR tune command of " + cmdString);
      if (!ensureRemoteLoaded(remoteName)) return;

      if (canMacroTune())
      {
        try {
          int cmdNum = Integer.parseInt(cmdString);
        } catch (Exception e) {
          String cmdStringNumeric = cmdString.replaceAll("\\D", ""); // remove all non-digits
          cmdString = cmdStringNumeric;
          if (Sage.DBG) System.out.println("IR tune command was not all digits; converted to: " + cmdString);
        }

         try {
            macroTune(Integer.parseInt(cmdString));
         } 
         catch (Exception e){
            if (Sage.DBG) System.out.println("Exception in playTuneString");
         }
      }
      else
      {
        // To deal with reinitializing the IR XMT for the 150 on Linux after the receive fails
        if (!Sage.WINDOWS_OS && devFilename.endsWith("PVR150Tuner.so"))
        {
          closeDevice();
          openDevice(currPortNum);
          // Wait for the init to complete
          try{Thread.sleep(Sage.getInt("linux/pvr150_ir_reset_wait", 750));}catch (Exception e){}
        }
        try
        {
          // channelDigits corresponds to 'Digits per Channel' in the UI
          if (baseRemote.channelDigits > 0)
          {
            // cmdString may include non-numeric chars, eg. '-' or '.'
            int digitCnt = 0;
            for (int i = 0; i < cmdString.length(); i++)
            {
              if (Character.isDigit(cmdString.charAt(i)))
                digitCnt++;
            }               

            while (digitCnt < baseRemote.channelDigits)
            {
              cmdString = "0" + cmdString;
              digitCnt++;
            }
          }
        }catch (Exception e){}
        if (baseRemote.prefixCmd != null && baseRemote.prefixCmd.length() > 0)
        {
          playCommand(remoteName, baseRemote.prefixCmd, getRepeatFactor(), true);
          long extraPrefixDelay = Sage.getLong(IRTUNE_PREFIX_EXTRA_DELAY, 0);
          if (extraPrefixDelay > 0)
          {
            try{Thread.sleep(extraPrefixDelay);}catch(Exception e){}
          }
        }
        boolean needsConfirm = baseRemote.confirmCmd != null && baseRemote.confirmCmd.length() > 0;
        for (int i = 0; i < cmdString.length(); i++)
          playCommand(remoteName, "" + cmdString.charAt(i), getRepeatFactor(),
              needsConfirm ? true : (i < cmdString.length() - 1));
        if (needsConfirm)
          playCommand(remoteName, baseRemote.confirmCmd, getRepeatFactor(), false);
        lastIRTime = Sage.eventTime();
      }
    }
  }

  public void waitForCompletion()
  {
    synchronized (tuneVec)
    {
      while (!tuneVec.isEmpty())
      {
        try
        {
          tuneVec.wait(5000);
        }
        catch (InterruptedException e){}
      }
    }
  }

  public synchronized String addNewRemote(String name)
  {
    name = createValidRemoteName(name);
    if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
    {
      if (new java.io.File(remoteDir2, name + ".ir").isFile()) return null;
    }
    if (Sage.DBG) System.out.println("Creating remote named " + name);
    long carrier=0, bitrate=0;
    if (needCarrierFrequency())
    {
      while (carrier == 0)
      {
        if (Sage.DBG) System.out.println("Hold a remote button down for a while. Scanning for frequency...");
        carrier = findCarrierFrequency();
        if (Sage.DBG) System.out.println("Carrier frequency=" + carrier);
        if (carrier > 100000)
        {
          if (Sage.DBG) System.out.println("BAD CARRIER, do it again!");
          carrier = 0;
        }
      }
    }
    if (needBitrate())
    {
      if (Sage.DBG) System.out.println("Hold a remote button down for a while. Calculating bitrate...");
      bitrate = findBitRate();
      if (Sage.DBG) System.out.println("Bitrate=" + bitrate);
    }
    Remote newRem = createRemote(name, carrier, bitrate, null);
    baseRemote = newRem;
    saveRemotes(new java.io.File(remoteDir2, baseRemote.name + ".ir").toString());
    return name;
  }

  private static String createValidRemoteName(String tryMe)
  {
    int len = tryMe.length();
    StringBuffer sb = new StringBuffer(len);
    for (int i = 0; i < len; i++)
    {
      char c = tryMe.charAt(i);
      if (Character.isLetterOrDigit(c))
        sb.append(c);
    }
    return sb.toString();
  }

  public synchronized boolean recordNewCommand(String remoteName, String cmdName)
  {
    if (!ensureRemoteLoaded(remoteName)) return false;

    // If it's already there, remove it so we can reprogram it
    removeCommand(remoteName, cmdName);

    Command cmd = recordCommand(cmdName);
    if (cmd != null)
      addCommand(baseRemote, cmd);
    return (cmd != null);
  }

  private boolean ensureRemoteLoaded(String remoteName)
  {
    if (baseRemote == null || !baseRemote.name.equals(remoteName))
    {
      if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
      {
        java.io.File remFile = new java.io.File(remoteDir, remoteName + ".ir");
        if (!remFile.isFile())
          remFile = new java.io.File(remoteDir2, remoteName + ".ir");
        loadRemotes(remFile.toString());
        java.io.BufferedReader inStream = null;
        try
        {
          inStream = new java.io.BufferedReader(new java.io.FileReader(remFile));
          String str = inStream.readLine();
          if (str != null)
          {
            java.util.StringTokenizer toker = new java.util.StringTokenizer(str, " \t");
            if (toker.countTokens() > 3)
            {
              toker.nextToken();
              toker.nextToken();
              toker.nextToken();
              if (toker.hasMoreTokens())
                baseRemote.channelDigits = Integer.parseInt(toker.nextToken());
              if (toker.hasMoreTokens())
                baseRemote.buttonDelay = Integer.parseInt(toker.nextToken());
              if (toker.hasMoreTokens())
                baseRemote.sequenceDelay = Integer.parseInt(toker.nextToken());
              if (toker.hasMoreTokens())
              {
                baseRemote.confirmCmd = toker.nextToken();
                if ("|".equals(baseRemote.confirmCmd))
                  baseRemote.confirmCmd = null;
              }
              if (toker.hasMoreTokens())
                baseRemote.prefixCmd = toker.nextToken();
            }
          }
        }
        catch (Exception e)
        {
          System.err.println("I/O Error loading remote control data of:" + e);
        }
        finally
        {
          if (inStream != null)
            try{inStream.close();}catch(Exception e){}
        }
        if (baseRemote != null)
        {
          if (baseRemote.buttonDelay <= 0)
            baseRemote.buttonDelay = Sage.WINDOWS_OS ? 600 : 800;
          if (baseRemote.sequenceDelay <= 0)
            baseRemote.sequenceDelay = 800;
          initDevice();
        }
      }
      else
      {
        loadRemotes(remoteName);
        if (baseRemote != null)
        {
          baseRemote.channelDigits = Sage.getInt("lirc/remotes/" + remoteName + "/channel_digits", 3);
          baseRemote.buttonDelay = Sage.getInt("lirc/remotes/" + remoteName + "/button_delay", 800);
          baseRemote.sequenceDelay = Sage.getInt("lirc/remotes/" + remoteName + "/sequence_delay", 800);
          baseRemote.confirmCmd = Sage.get("lirc/remotes/" + remoteName + "/confirm_cmd", "");
          baseRemote.prefixCmd = Sage.get("lirc/remotes/" + remoteName + "/prefix_cmd", "");
          initDevice();
        }
      }
    }
    return baseRemote != null;
  }

  // DO NOT MODIFY THE RETURNED DATA STRUCTURE!!
  public synchronized Remote getRemoteInfo(String remoteName)
  {
    ensureRemoteLoaded(remoteName);
    return baseRemote;
  }

  public synchronized void renameCommand(String remoteName, String oldCmdName, String newCmdName)
  {
    if (!ensureRemoteLoaded(remoteName)) return;
    Command currCmd = baseRemote.command;
    while (currCmd != null)
    {
      if (currCmd.name.equals(oldCmdName))
      {
        currCmd.name = newCmdName;
        break;
      }
      currCmd = currCmd.next;
    }
  }

  public synchronized void removeCommand(String remoteName, String cmdName)
  {
    if (!ensureRemoteLoaded(remoteName)) return;
    Command currCmd = baseRemote.command;
    Command lastCmd = null;
    while (currCmd != null)
    {
      if (currCmd.name.equals(cmdName))
      {
        if (lastCmd == null)
          baseRemote.command = currCmd.next;
        else
          lastCmd.next = currCmd.next;
        break;
      }
      lastCmd = currCmd;
      currCmd = currCmd.next;
    }
  }

  private boolean hasRemoteFileData(String remoteName)
  {
    java.io.File remFile = new java.io.File(remoteDir, remoteName + ".ir");
    if (remFile.isFile() && remFile.length() > 0)
      return true;
    remFile = new java.io.File(remoteDir2, remoteName + ".ir");
    return (remFile.isFile() && remFile.length() > 0);
  }

  public synchronized void saveChanges()
  {
    if (baseRemote != null)
    {
      if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
      {
        java.io.File remFile = new java.io.File(remoteDir, baseRemote.name + ".ir");
        if (!remFile.isFile())
          remFile = new java.io.File(remoteDir2, baseRemote.name + ".ir");
        saveRemotes(remFile.toString());

        // Load back the file data, and then rewrite the first line in our format
        java.io.BufferedReader inStream = null;
        java.io.PrintWriter outStream = null;
        try
        {
          inStream = new java.io.BufferedReader(new java.io.FileReader(remFile));
          StringBuffer sb = new StringBuffer();
          sb.append(inStream.readLine());
          sb.append(' ');
          sb.append(baseRemote.channelDigits);
          sb.append(' ');
          sb.append(baseRemote.buttonDelay);
          sb.append(' ');
          sb.append(baseRemote.sequenceDelay);
          if (baseRemote.confirmCmd != null && baseRemote.confirmCmd.length() > 0)
            sb.append(" " + baseRemote.confirmCmd);
          else if (baseRemote.prefixCmd != null && baseRemote.prefixCmd.length() > 0)
            sb.append(" |"); // delimiter to separate prefixCmd
          if (baseRemote.prefixCmd != null && baseRemote.prefixCmd.length() > 0)
            sb.append(" " + baseRemote.prefixCmd);
          sb.append("\r\n");
          char[] buf = new char[1024];
          int numRead = inStream.read(buf);
          while (numRead != -1)
          {
            sb.append(buf, 0, numRead);
            numRead = inStream.read(buf);
          }
          inStream.close();
          inStream = null;
          outStream = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(remFile)));
          outStream.print(sb.toString());
        }
        catch (java.io.IOException e)
        {
          System.err.println("I/O Error resaving remote control data of:" + e);
        }
        finally
        {
          if (inStream != null)
            try{inStream.close();}catch(Exception e){}
          if (outStream != null)
            try{outStream.close();}catch(Exception e){}
        }
      }
      else
      {
        Sage.putInt("lirc/remotes/" + baseRemote.name + "/channel_digits", baseRemote.channelDigits);
        Sage.putInt("lirc/remotes/" + baseRemote.name + "/button_delay", baseRemote.buttonDelay);
        Sage.putInt("lirc/remotes/" + baseRemote.name + "/sequence_delay", baseRemote.sequenceDelay);
        Sage.put("lirc/remotes/" + baseRemote.name + "/confirm_cmd", baseRemote.confirmCmd);
        Sage.put("lirc/remotes/" + baseRemote.name + "/prefix_cmd", baseRemote.prefixCmd);
      }
    }
  }

  public synchronized void cancelChanges()
  {
    baseRemote = null;
  }

  public synchronized void removeRemote(String remoteName)
  {
    if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
    {
      // This just erases the file
      if (baseRemote != null && baseRemote.name.equals(remoteName))
        baseRemote = null;
      java.io.File remFile = new java.io.File(remoteDir, remoteName + ".ir");
      if (!remFile.isFile())
        remFile = new java.io.File(remoteDir2, remoteName + ".ir");
      if (remFile.canWrite()) // read-only files are devices that can't be removed
        remFile.delete();
    }
  }

  public synchronized void setChannelDigits(int x)
  {
    if (baseRemote != null)
      baseRemote.channelDigits = x;
  }

  public synchronized void setButtonDelay(int millis)
  {
    if (baseRemote != null)
      baseRemote.buttonDelay = millis;
  }

  public synchronized void setSequenceDelay(int millis)
  {
    if (baseRemote != null)
      baseRemote.sequenceDelay = millis;
  }

  public synchronized void setConfirmKey(String x)
  {
    if (baseRemote != null)
      baseRemote.confirmCmd = x;
  }

  public synchronized void setPrefixKey(String x)
  {
    if (baseRemote != null)
      baseRemote.prefixCmd = x;
  }

  public boolean isAlive() { return alive; }

  public Remote getDefaultRemoteInfo() { return baseRemote; }

  public int getMinChannel()
  {
    return 1;
  }

  public int getMaxChannel()
  {
    if (baseRemote != null)
      if (baseRemote.channelDigits == 0)
        return 999;
      else return (int)Math.round(Math.pow(10, baseRemote.channelDigits)) - 1;
    return 999;
  }

  public native String deviceName();
  private native long findBitRate();
  private native long findCarrierFrequency();
  private native void initDevice(); // init before playback
  private native void loadRemotes(String filename);
  private native boolean needBitrate();
  private native boolean needCarrierFrequency();
  public synchronized boolean openPort(int portNum)
  {
    currPortNum = portNum;
    if (!alive)
      initialize();
    if (!Sage.WINDOWS_OS && devFilename.endsWith("PVR150Tuner.so") && UIManager.getLocalUI() != null)
    {
      if (UIManager.getLocalUI().getRouter() == null)
      {
        boolean openD = openDevice(portNum);
        if (!openD)
        {
          if (Sage.DBG) System.out.println("Failed opening IR port " + portNum + ". Darn!");
          return false;
        }
      }
      else
      {
        // Sync the PVR150 xmt & recv
        synchronized (UIManager.getLocalUI().getRouter().getDefaultInputPlugin())
        {
          boolean openD = openDevice(portNum);
          if (!openD)
          {
            if (Sage.DBG) System.out.println("Failed opening IR port " + portNum + ". Darn!");
            return false;
          }
        }
      }
      if (Sage.DBG) System.out.println("SUCCESSFULLY opened IRTuner on port " + portNum);
      return true;
    }
    boolean openD = openDevice(portNum);
    if (!openD)
    {
      if (Sage.DBG) System.out.println("Failed opening COM port " + portNum + ". Trying again!");
      closeDevice();
      openD = openDevice(portNum);
      if (!openD)
      {
        if (Sage.DBG) System.out.println("Failed opening COM port " + portNum + ". Darn!");
        return false;
      }
    }
    if (Sage.DBG) System.out.println("SUCCESSFULLY opened IRTuner on port " + portNum);
    return true;
  }
  private native boolean openDevice(int portNum);
  private native void playCommand(Remote theRemote, String cmdName, int repeat);
  private native Command recordCommand(String commandName);
  private native void saveRemotes(String filename);
  private native void init0();
  private native void goodbye0();
  private native boolean canMacroTune();
  private native void macroTune(int number);

  private String devFilename;
  private Remote baseRemote;
  private long nativePort;
  private long nativeDllHandle;
  private java.io.File remoteDir;
  private java.io.File remoteDir2;
  private java.util.Vector tuneVec;
  private boolean asyncTuning;
  private Thread asyncThread;
  private int currPortNum;

  private long lastIRTime;
  private long globalPreroll;
  private boolean alive;
}
