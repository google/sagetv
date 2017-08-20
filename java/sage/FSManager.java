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
 * @author Narflex
 */
public class FSManager implements Runnable
{
  private static final String DELETE_QUEUE_PROP = "linux/delete_queue";
  private static final long PROGRESSIVE_DELETE_INCREMENT = Sage.getLong("linux/progressive_delete_increment", 250*1024*1024);
  private static final long PROGRESSIVE_DELETE_WAIT = Sage.getLong("linux/progressive_delete_wait", 1500);

  /**
   * Threadsafe implementation of Singleton
   */
  private static class FSManagerHolder
  {
    public static final FSManager instance = new FSManager();
  }

  public static FSManager getInstance()
  {
    return FSManagerHolder.instance;
  }

  /** Creates a new instance of FSManager */
  private FSManager()
  {
    // See if we've got anything in the delete queue that we need to go back to work on
    // Do this now so that the Seeker has the ignore files set properly when it initializes
    String delQueueProp = Sage.get(DELETE_QUEUE_PROP, "");
    if (delQueueProp.length() > 0)
    {
      java.util.Set delSet = Sage.parseDelimSet(delQueueProp, ";");
      java.util.Iterator walker = delSet.iterator();
      while (walker.hasNext())
      {
        java.io.File newF = new java.io.File(walker.next().toString());

        // There are some initialization ordering issues if we route through the selector here
        // instead of using Library directly. The Seeker2 singleton is always loaded before the
        // Library singleton in the selector and Seeker2 loads FSManager (hence why we are here). If
        // we call back to the selector from here the Library reference doesn't exist yet and we
        // will get a null pointer exception. It is however not a problem to directly get the
        // Library singleton from here. It's important to note that while this sounds a little hacky
        // the selector is there for performance reasons and this is the only case where we have an
        // exception. I could just re-order a few things, but then I might accidentally change how
        // something worked in the old Seeker and I do not want to create any new problems to
        // address one exception.
        // TODO: Will be enabled in a future commit.
        /*if (SeekerSelector.USE_BETA_SEEKER)
          Library.getInstance().addIgnoreFile(newF);
        else*/
          Seeker.getInstance().addIgnoreFile(newF);
      }
    }
  }

  public void goodbye()
  {
    if (Sage.DBG) System.out.println("FSManager goodbye()");
    alive = false;
    // Unmount everything
    java.util.Iterator walker = mountPoints.keySet().iterator();
    while (walker.hasNext())
    {
      if (!Sage.WINDOWS_OS)
        IOUtils.undoMount(walker.next().toString());
    }
  }

  public void run()
  {
    alive = true;
    if (Sage.DBG) System.out.println("FSManager is running...");
    // See if we've got anything in the delete queue that we need to go back to work on
    String delQueueProp = Sage.get(DELETE_QUEUE_PROP, "");
    if (delQueueProp.length() > 0)
    {
      addToDeleteQueue(null);
      java.util.Set delSet = Sage.parseDelimSet(delQueueProp, ";");
      synchronized (deleteQueue)
      {
        java.util.Iterator walker = delSet.iterator();
        while (walker.hasNext())
        {
          java.io.File newF = new java.io.File(walker.next().toString());
          // Seeker ignore files get setup in the constructor
          deleteQueue.add(new DeleteInfo(newF));
        }
        deleteQueue.notifyAll();
      }
    }
    Hunter seeky = SeekerSelector.getInstance();
    long checkPeriod = Sage.getLong("seeker/fs_mgr_wait_period", 15000);
    while (alive)
    {
      // Check to see if there's any expired mounts to remove
      synchronized (mountPoints)
      {
        java.util.Iterator walker = mountPoints.entrySet().iterator();
        while (walker.hasNext())
        {
          java.util.Map.Entry currEnt = (java.util.Map.Entry) walker.next();
          MountData md = (MountData) currEnt.getValue();
          if (md.refCount == 0 && Sage.time() - md.lastReleaseTime > 180000)
          {
            if (Sage.DBG) System.out.println("Removing expired mount point: " + currEnt.getKey());
            if (!Sage.WINDOWS_OS && IOUtils.undoMount(currEnt.getKey().toString()))
              walker.remove();
          }
        }
      }
      if (!Sage.client)
      {
        // Do our encoder HALT detection here so we can run it more frequent than what the Seeker does
        seeky.checkForEncoderHalts();

        // Also do our RAID array check as well
        systemStorageScan();
      }
      try{Thread.sleep(checkPeriod);}catch(Exception e){}
    }
  }

  // Requests that an SMB path be mounted so that we can access it as a local filesystem.
  // This will automatically determine a valid point to mount it at.
  // The caller should call releaseLocalSMBAccess() when done using the resource so it can be unmounted later
  public String requestLocalSMBAccess(String smbPath)
  {
    if (Sage.DBG) System.out.println("RequestLocalSMBAccess called smbPath=" + smbPath);
    if (smbPath == null || !smbPath.startsWith("smb://")) return null;
    if (Sage.WINDOWS_OS)
    {
      return IOUtils.convertSMBURLToUNCPath(smbPath);
    }
    String smbPrefix = Sage.get("linux/smb_mount_root", "/tmp/sagetv_shares/");
    smbPath = smbPath.substring("smb://".length());
    String smbAuthInfo = "";
    int authIdx = smbPath.indexOf('@');
    if (authIdx != -1)
    {
      smbAuthInfo = smbPath.substring(0, authIdx + 1);
      smbPath = smbPath.substring(authIdx + 1);
    }
    String localPath = smbPrefix + smbPath;
    // Now modify the paths so it's only the share, not the subdirectory too
    int s1 = smbPath.indexOf('/');
    int s2 = smbPath.indexOf('/', s1 + 1);
    // SMB is case insensitive for share names, so convert those to LC
    String smbMountPath = "//" + smbAuthInfo + smbPath.substring(0, s2).toLowerCase();
    localPath = smbPrefix + smbPath.substring(0, s2).toLowerCase();
    String fullLocalPath = localPath + smbPath.substring(s2);
    java.io.File f = new java.io.File(localPath);
    f.mkdirs();

    int mountRez = IOUtils.doSMBMount(smbMountPath, localPath);
    if (mountRez == IOUtils.SMB_MOUNT_FAILED)
      return null;
    else
    {
      synchronized (mountPoints)
      {
        MountData md = (MountData) mountPoints.get(localPath);
        if (md == null)
        {
          if (mountRez == IOUtils.SMB_MOUNT_SUCCEEDED)
            mountPoints.put(localPath, new MountData());
        }
        else
          md.refCount++;
      }
    }

    return fullLocalPath;
  }

  public void releaseLocalSMBAccess(String smbPath)
  {
    if (Sage.DBG) System.out.println("ReleaseLocalSMBAccess called smbPath=" + smbPath);
    if (Sage.WINDOWS_OS) return;
    String smbPrefix = Sage.get("linux/smb_mount_root", "/tmp/sagetv_shares/");
    smbPath = smbPath.substring("smb://".length());
    // SMB is case insensitive
    String localPath = smbPrefix + smbPath.toLowerCase();
    // Now modify the paths so it's only the share, not the subdirectory too
    int s1 = smbPath.indexOf('/');
    int s2 = smbPath.indexOf('/', s1 + 1);
    localPath = smbPrefix + smbPath.substring(0, s2).toLowerCase();
    synchronized (mountPoints)
    {
      MountData md = (MountData) mountPoints.get(localPath);
      if (md != null)
      {
        md.refCount--;
        md.lastReleaseTime = Sage.time();
      }
    }
  }

  // This may also return an smb:// URL for network paths
  public String requestLargeTempStorageDir(long minFreeSpace)
  {
    return System.getProperty("java.io.tmpdir");
  }

  // Returns the mount point for the ISO file
  private Object vcdLock = new Object();
  private java.io.File lastVcdMount; // if we use a different one each time it goes faster
  public java.io.File requestISOMount(java.io.File isoFile)
  {
    return requestISOMount(isoFile, null, null);
  }
  public java.io.File requestISOMount(java.io.File isoFile, UIManager uiOrigin)
  {
    return requestISOMount(isoFile, uiOrigin, null);
  }
  public java.io.File requestISOMount(java.io.File isoFile, java.io.File mountDir)
  {
    return requestISOMount(isoFile, null, mountDir);
  }
  public java.io.File requestISOMount(java.io.File isoFile, UIManager uiOrigin, java.io.File mountDir)
  {
    if (Sage.WINDOWS_OS)
    {
      // Use Virtual Clone Drive
      java.io.File vcdMountExe = getVCDMountEXEPath();
      if (vcdMountExe == null || !vcdMountExe.isFile())
      {
        if (Sage.DBG) System.out.println("WARNING Cannot find VirtualCloneDrive EXE path to mount an ISO!");
        return null;
      }
      // Find out which drive letters are used by VirtualCloneDrive
      int driveMask = Sage.readDwordValue(Sage.HKEY_CURRENT_USER, "Software\\Elaborate Bytes\\VirtualCloneDrive", "VCDDriveMask");
      // The service may have to enumerate all of these to find it
      if (driveMask == 0 && Sage.isHeadless())
      {
        if (Sage.DBG) System.out.println("Didn't find VCDDriveMask in HKCU, check all users in the registry to find it");
        String[] userKeys = Sage.getRegistrySubkeys(Sage.HKEY_USERS, "");
        for (int i = 0; userKeys != null && i < userKeys.length; i++)
        {
          driveMask = Sage.readDwordValue(Sage.HKEY_USERS, userKeys[i] + "\\Software\\Elaborate Bytes\\VirtualCloneDrive", "VCDDriveMask");
          if (driveMask != 0)
          {
            if (Sage.DBG) System.out.println("Found VCDDriveMask key under user " + userKeys[i]);
            break;
          }
        }
      }
      if (driveMask == 0)
      {
        driveMask = Sage.getInt("forced_vcd_drivemask", 0);
        if (Sage.DBG) System.out.println("Could not get VCDDriveMask from registry, using forced value from properties file of: 0x" + Integer.toString(driveMask, 16));
      }
      if (Sage.DBG) System.out.println("Trying to mount ISO path using VirtualCloneDrive: " + isoFile + " driveMask=0x" + Integer.toString(driveMask, 16));
      synchronized (vcdLock)
      {
        java.io.File targetMount = null;
        for (int i = 0; i < 32; i++)
        {
          if ((driveMask & (0x1 << i)) != 0)
          {
            java.io.File testDir = new java.io.File("" + (char)('A' + i) + ":");
            if (testDir.isDirectory())
            {
              if (Sage.DBG) System.out.println("Can't use drive " + testDir + " for VCD mount because its already in use");
            }
            else
            {
              if (Sage.DBG) System.out.println("Found a valid drive to use for VCDMount of " + testDir);
              targetMount = testDir;
              if (!targetMount.equals(lastVcdMount))
                break;
            }
          }
        }
        if (targetMount == null)
        {
          if (Sage.DBG) System.out.println("ERROR Could not find a free drive to use as a mount point with VCDMount");
          return null;
        }
        else
        {
          IOUtils.exec(new String[] { vcdMountExe.getAbsolutePath(), "/l=" + targetMount.getAbsolutePath().charAt(0), isoFile.getAbsolutePath() });
          int waitCount = 80;
          while (!targetMount.isDirectory() && waitCount-- > 0)
          {
            if (Sage.DBG) System.out.println("Waiting for mount to appear...");
            try{Thread.sleep(250);}catch(Exception e){}
          }
          lastVcdMount = targetMount;
          return targetMount;
        }
      }
    }
    else
    {
      if (mountDir == null)
      {
        if (uiOrigin == null)
          mountDir = sage.media.format.FormatParser.FORMAT_DETECT_MOUNT_DIR;
        else
          mountDir = new java.io.File(System.getProperty("java.io.tmpdir"), "dvdmount-" + uiOrigin.getLocalUIClientName());
      }
      mountDir.mkdirs();
      if (!Sage.LINUX_IS_ROOT)
      {
        // mount requires root permissions
        // the sagetv user needs to be in the sudoers list
        // NOTE mount command is NOT resolved in properties because sudo is being used
        if (IOUtils.exec2(new String[]{"sudo","-n", "mount", isoFile.getAbsolutePath(), mountDir.getAbsolutePath(), "-o", "loop,ro"}, true) != 0)
        {
          if (Sage.DBG) System.out.println("FAILED mounting ISO image " + isoFile + " to " + mountDir);
          return null;
        }
      }
      else
      {
        if (IOUtils.exec2(new String[]{Sage.get("linux/dvdmounter", "mount"), isoFile.getAbsolutePath(), mountDir.getAbsolutePath(), "-o", "loop"}, true) != 0)
        {
          if (Sage.DBG) System.out.println("FAILED mounting ISO image " + isoFile + " to " + mountDir);
          return null;
        }
      }
      return mountDir;
    }
  }

  private java.io.File getVCDMountEXEPath()
  {
    String regVal = Sage.readStringValue(Sage.HKEY_LOCAL_MACHINE, "SOFTWARE\\Elaborate Bytes\\VirtualCloneDrive", "Install_Dir");
    if (regVal == null)
      return null;
    else
      return new java.io.File(regVal, "VCDMount.exe");
  }

  public void releaseISOMount(java.io.File mountDir)
  {
    if (Sage.WINDOWS_OS)
    {
      // Use Virtual Clone Drive to release the mount
      java.io.File vcdMountExe = getVCDMountEXEPath();
      if (vcdMountExe == null || !vcdMountExe.isFile())
      {
        if (Sage.DBG) System.out.println("WARNING Cannot find VirtualCloneDrive EXE path so we can't unmount: " + mountDir);
        return;
      }
      if (Sage.DBG) System.out.println("Releasing VCDMount point at " + mountDir);
      synchronized (vcdLock)
      {
        IOUtils.exec(new String[] { vcdMountExe.getAbsolutePath(), "/l=" + mountDir.getAbsolutePath().charAt(0), "/u" });
      }
    }
    else
    {
      // Figure out which loop device is being used so we can properly stop it so the unmount
      // actually works
      if (Sage.LINUX_IS_ROOT)
        IOUtils.exec2(new String[]{"umount", mountDir.getAbsolutePath()}, false);
      else
        IOUtils.exec2(new String[]{"sudo", "-n", "umount", mountDir.getAbsolutePath()}, false);
      mountDir.delete();
    }
  }

  /*
   * This function will delete the specified file and return true if successful. One IMPORTANT
   * feature of this call is that if the property linux/enable_progressive_deletes=true is set then
   * it'll progressively delete the file at a rate of 500MB + 500msec of sleep until its done.
   */
  public boolean deleteFile(java.io.File f)
  {
    if (Sage.getBoolean("linux/async_file_deletion", true) || (
        Sage.getBoolean("linux/enable_progressive_deletes", false)
        && f.length() > PROGRESSIVE_DELETE_INCREMENT))
    {
      return addToDeleteQueue(f);
    }
    else
    {
      if (!f.delete())
      {
        // Don't use a delete queue for failed deletions on embedded systems since we're not managing recordings there
        if (f.isFile() && f.canWrite())
        {
          if (Sage.DBG) System.out.println("Failed deleting file; put it into the async delete queue and try to do it later: " + f);
          return addToDeleteQueue(f);
        }
        else
          return false;
      }
      else
        return true;
    }
  }

  private boolean addToDeleteQueue(java.io.File f)
  {
    if (deleteQueue == null)
    {
      deleteQueue = new java.util.Vector();
      Thread deleterThread = new Thread("ProgressiveDeleter")
      {
        public void run()
        {
          while (alive)
          {
            if (deleteQueue.isEmpty())
            {
              synchronized (deleteQueue)
              {
                try{deleteQueue.wait(60000);}catch(InterruptedException e){}
                continue;
              }
            }
            try
            {
              DeleteInfo currFile = (DeleteInfo) deleteQueue.firstElement();

              if (!currFile.f.isFile() || progressiveDelete(currFile))
              {
                synchronized (deleteQueue)
                {
                  deleteQueue.remove(0);
                  Sage.put(DELETE_QUEUE_PROP, Sage.createDelimSetString(new java.util.HashSet(deleteQueue), ";"));
                  SeekerSelector.getInstance().removeIgnoreFile(currFile.f);
                }
                Sage.savePrefs();
                if (Sage.DBG) System.out.println("Completed progressive deletion of: " + currFile);
              }
              else
              {
                // Deletion failed!!!! Put it back in the queue at the end!!
                synchronized (deleteQueue)
                {
                  deleteQueue.add(deleteQueue.remove(0));
                }
                if (currFile.firstFailTime == 0)
                  currFile.firstFailTime = Sage.time();
                if (Sage.DBG) System.out.println("Progressive deletion failed for:" + currFile + " reinserting it into the queue...");
              }
            }
            catch (Throwable t)
            {
              if (Sage.DBG) System.out.println("Internal error in the progressive deleter of:" + t);
            }

            try{Thread.sleep(5000);}catch(Exception e){}
          }
        }
      };
      deleterThread.setDaemon(true);
      deleterThread.start();
    }
    if (f == null) return false;
    java.io.File newF = new java.io.File(f.getAbsolutePath() + ".delete");
    while (newF.isFile())
    {
      newF = new java.io.File(newF.getAbsolutePath() + ".delete");
    }
    // We put it in the prop file first as a safeguard in case the rename works and then we terminate right after that.
    // Sync this whole operation here so the deleter thread doesn't clobber our properties update
    synchronized (deleteQueue)
    {
      String currDelQProp = Sage.get(DELETE_QUEUE_PROP, "");
      Sage.put(DELETE_QUEUE_PROP, currDelQProp + ";" + newF.toString());
      // Do the property save async since this could be coming from the Seeker and we don't want to slow that down
      Pooler.execute(new Runnable(){ public void run() { Sage.savePrefs(); }});
      if (f.renameTo(newF))
      {
        if (Sage.DBG) System.out.println("Added file to delete queue: " + f);
        deleteQueue.add(new DeleteInfo(newF));
        deleteQueue.notifyAll();
        return true;
      }
      else
      {
        if (Sage.DBG) System.out.println("Failed renaming file to move into delete queue: " + f + " target=" + newF + " leave it with its current filename and putting it into the queue.");
        // We still want to queue this up for deletion even though something may have a lock on it. We can easily register these files
        // with the Seeker so nothing else happens to them that we don't want since we didn't rename them to something innocuous
        Sage.put(DELETE_QUEUE_PROP, currDelQProp + ";" + f.getAbsolutePath());
        // Do the property save async since this could be coming from the Seeker and we don't want to slow that down
        Pooler.execute(new Runnable(){ public void run() { Sage.savePrefs(); }});
        DeleteInfo dinf = new DeleteInfo(f);
        dinf.firstFailTime = Sage.time();
        deleteQueue.add(dinf);
        deleteQueue.notifyAll();
        SeekerSelector.getInstance().addIgnoreFile(f);
        return true;
      }
    }
  }

  private boolean progressiveDelete(DeleteInfo dinf)
  {
    java.io.File f = dinf.f;
    java.io.RandomAccessFile fos = null;
    java.nio.channels.FileChannel fc = null;
    // NOTE(codefu): If progressive delete is disabled but async isn't, perform direct delete here.
    boolean progressiveDelete = Sage.getBoolean("linux/enable_progressive_deletes", false);
    if (Sage.DBG) System.out.println(
        "Starting " + (progressiveDelete ? "progressive" : "async") + " delete for:" + f);
    if(progressiveDelete) {
      try
      {
        fos = new java.io.RandomAccessFile(f, "rw");
        fc = fos.getChannel();
        long newLength = fc.size() - PROGRESSIVE_DELETE_INCREMENT;
        while (newLength > 0)
        {
          fc.truncate(newLength);
          fc.force(true);
          try{Thread.sleep(PROGRESSIVE_DELETE_WAIT);}catch(Exception e){}
          newLength = fc.size() - PROGRESSIVE_DELETE_INCREMENT;
        }
      }
      catch (Throwable e)
      {
        if (Sage.DBG) System.out.println("ERROR in progressive deletion of file " + f + " of:" + e);
        if (dinf.firstFailTime == 0)
          dinf.firstFailTime = Sage.time();
        return false;
      }
      finally
      {
        if (fc != null)
          try{fc.close();}catch(Exception e){}
        if (fos != null)
          try{fos.close();}catch(Exception e){}
        // Wait to ensure the file is closed properly...there's been issues with this and UNC paths on Windows
        try{Thread.sleep(PROGRESSIVE_DELETE_WAIT);}catch(Exception e){}
      }
    }
    if (f.delete())
      return true;
    else
    {
      if (dinf.firstFailTime == 0)
        dinf.firstFailTime = Sage.time();
      return false;
    }
  }

  /*
   * This takes account into any progressive file deletions were working on in the background
   */
  public long getDiskFreeSpace(String disk)
  {
    long actualFreeSpace = Sage.getDiskFreeSpace(disk);
    if (deleteQueue != null && !deleteQueue.isEmpty())
    {
      // Account for this space which WILL become free very soon
      long orgFreeSpace = actualFreeSpace;
      synchronized (deleteQueue)
      {
        for (int i = 0; i < deleteQueue.size(); i++)
        {
          DeleteInfo dinf = (DeleteInfo) deleteQueue.get(i);
          // If there's any issues with not being able to actually delete the file then DO NOT count it towards
          // free diskspace. Otherwise we can easily end up with situations where we run out of diskspace due to
          // file locking issues (which undoubtedly would occur w/ commercial detection people run).
          if (dinf.firstFailTime == 0)
          {
            java.io.File currFile = dinf.f;
            if (currFile.getAbsolutePath().startsWith(disk))
              actualFreeSpace += currFile.length();
          }
        }
      }
      if (Sage.DBG && orgFreeSpace != actualFreeSpace) System.out.println("Adjusted disk free space calculation due to progressive deletes in wait org=" +
          orgFreeSpace + " returned=" + actualFreeSpace);
    }
    return actualFreeSpace;
  }

  public void systemStorageScan()
  {
    if (!Sage.LINUX_OS || !Sage.getBoolean("linux/enable_nas", false))
      return;
    //		if (Sage.DBG) System.out.println("Doing the scan for existing storage devices on the system...");
    String[] blockDevs = new java.io.File("/sys/block/").list();
    //		java.util.Vector extraArrays = new java.util.Vector();
    //		java.util.Vector extraDisks = new java.util.Vector();
    //		raidDiskMap = new java.util.HashMap();
    for (int i = 0; i < blockDevs.length; i++)
    {
      if (blockDevs[i].startsWith("md"))
      {
        //				if (Sage.DBG) System.out.println("Found RAID array " + blockDevs[i]);
        // See if this Array has a parent; if not then it's an extra array
        java.io.File currArray = new java.io.File("/sys/block", blockDevs[i]);
        /*				String[] currHolders = new java.io.File(currArray, "holders").list();
				if (currHolders == null || currHolders.length == 0)
				{
					if (Sage.DBG) System.out.println("Array is outside of main storage pool: " + blockDevs[i]);
					extraArrays.add(currArray);
				}
				// Now setup the map for the drives within the array
				String[] arrayDevs = new java.io.File(currArray, "slaves").list();
				java.util.Vector currArrayDevs = new java.util.Vector();
				for (int j = 0; j < arrayDevs.length; j++)
				{
					// These will be the partitions, so strip off the partition number
					currArrayDevs.add(new java.io.File("/sys/block/" + arrayDevs[j].substring(0, 3)));
				}
				if (Sage.DBG) System.out.println("Found drives within array " + blockDevs[i] + " of " + currArrayDevs);
				raidDiskMap.put(currArray, currArrayDevs);

				// We need the UUID of the array to look it up in the properties file
				String arrayUUID = IOUtils.exec(new String[] { "sh", "-c", "mdadm --detail /dev/" + blockDevs[i] + " | grep UUID"});
				arrayUUID = arrayUUID.substring(arrayUUID.indexOf(":") + 1).trim();
				if (Sage.DBG) System.out.println("Found UUID for array of: " + arrayUUID);
         */
        // We also need to store information in the properties file about which disks are contained in the array wrt model, serial # & size so that if they
        // fail we can indicate them in the UI. These will be removed from our information when the array is no longer in a degraded state; but we also need
        // to be sure all the existing drives are in the properties file as well.
        // WHEN THE ARRAY IS REBUILDING ITSELF FROM ADDING A DEVICE; DEGRADED WILL BE NON-ZERO DURING THAT TIME PERIOD. but
        // sync_action is 'recover' at that time and sync_completed is '0 / 2930271744'
        // We should also post another status message when this action is completed as well that's not an error state message.
        // There should also be a warning message posted during the rebuild phase as well.
        if ("0".equals(IOUtils.getFileAsString(new java.io.File(currArray, "md/degraded")).trim()))
        {
          if (knownDegradedArrays.remove(currArray.getName()))
          {
            if (Sage.DBG) System.out.println("Previously degraded array is stable;  device:" + currArray.getName());
            knownRecoveringArrays.remove(currArray.getName());
            sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createCleanRAIDMsg(currArray.getName()));
          }
          /*					String newDriveProp = "";
					for (int j = 0; j < currArrayDevs.size(); j++)
					{
						java.io.File diskDev = (java.io.File) currArrayDevs.get(j);
						String diskModel = IOUtils.getFileAsString(new java.io.File(diskDev, "device/model"));
						long diskSize = Long.parseLong(IOUtils.getFileAsString(new java.io.File(diskDev, "size"))) * 512;
						String diskSerial = IOUtils.exec(new String[] { "sh", "-c", "hdparm -I /dev/" + diskDev.getName() + " | grep uuid"});
						diskSerial = diskSerial.substring(diskSerial.indexOf(":") + 1).trim();
					}
           */				}
        else
        {
          String syncMode = IOUtils.getFileAsString(new java.io.File(currArray, "md/sync_action")).trim();
          if ("recover".equals(syncMode))
          {
            if (knownRecoveringArrays.add(currArray.getName()))
            {
              if (Sage.DBG) System.out.println("ARRAY IS RECOVERING!!!! dev:" + currArray.getName());
              sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createRecoveringRAIDMsg(currArray.getName()));
            }
          }
          else if (knownDegradedArrays.add(currArray.getName()))
          {
            if (Sage.DBG) System.out.println("ARRAY IS DEGRADED!!!! dev:" + currArray.getName());
            sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createDegradedRAIDMsg(currArray.getName()));
          }
          //					String arrayDriveProps = Sage.get("linux/storage/arrays/" + arrayUUID + "/disks", "");
        }
      }
      /*			else if (blockDevs[i].startsWith("sd"))
			{
				if (Sage.DBG) System.out.println("Found hard disk drive " + blockDevs[i]);
			}
			else if (blockDevs[i].startsWith("dm-"))
			{
				if (Sage.DBG) System.out.println("Found LVM group " + blockDevs[i]);
				String lvmName = IOUtils.getFileAsString(new java.io.File("/sys/block/" + blockDevs[i] + "/dm/name"));
				String lvmUUID = IOUtils.getFileAsString(new java.io.File("/sys/block/" + blockDevs[i] + "/dm/uuid"));
				if (Sage.DBG) System.out.println("LVM group " + blockDevs[i] + " has name=" + lvmName + " and uuid=" + lvmUUID);

				// The elements of the LVM are then listed in /sys/block/blockDevs[i]/slaves
				// We only care about our main LVM group though; so check to see if its that
				if (lvmName.equals("STVLVG-varmedia"))
				{
					if (Sage.DBG) System.out.println("Found our main LVM storage group!");
					if (!Sage.get("linux/storage/main_lvm_uuid", "").equals(lvmUUID))
					{
						if (Sage.DBG) System.out.println("New LVM storage group found; setting it as our main storage config");
						Sage.put("linux/storage/main_lvm_uuid", lvmUUID);
					}
					mainLVMDev = new java.io.File("/sys/block", blockDevs[i]);
					String[] elems = new java.io.File(mainLVMDev, "slaves").list();
					if (elems != null)
					{
						mainArrayDevs = new java.io.File[elems.length];
						for (int j = 0; j < elems.length; j++)
						{
							if (Sage.DBG) System.out.println("Found array " + elems[j] + " as a component of the main LVM group");
							mainArrayDevs[j] = new java.io.File("/sys/block/", elems[j]);
						}
					}
				}
			}*/
    }

    //		auxArrayDevs = (java.io.File[]) extraArrays.toArray(new java.io.File[0]);
    //		auxDiskDevs = (java.io.File[]) extraDisks.toArray(new java.io.File[0]);
  }
  /*
	public java.io.File getMainLVMDev()
	{
		return mainLVMDev;
	}

	public long getMainLVMSize()
	{
		return Long.parseLong(IOUtils.getFileAsString(new java.io.File(mainLVMDev, "size"))) * 512;
	}

	public java.io.File[] getMainLVMArrays()
	{
		return mainArrayDevs;
	}

	// for linux NAS only
	private java.io.File mainLVMDev;
	private java.io.File[] mainArrayDevs;
	private java.io.File[] auxArrayDevs;
	private java.io.File[] auxDiskDevs;
	private java.util.Map raidDiskMap; // raidArray->Vector(diskDev), files on both ends

	private String[] failedDisks; // each entry is model,serial,size
   */
  private java.util.Set knownDegradedArrays = new java.util.HashSet();
  private java.util.Set knownRecoveringArrays = new java.util.HashSet();

  private java.io.File localHDD;

  private java.util.Map mountPoints = new java.util.HashMap();
  private boolean alive;

  private java.util.Vector deleteQueue;

  private static class MountData
  {
    public MountData()
    {
      refCount = 1;
    }
    public int refCount;
    public long lastReleaseTime;
  }

  private static class DeleteInfo
  {
    public DeleteInfo(java.io.File f)
    {
      this.f = f;
    }
    public String toString()
    {
      return f.getAbsolutePath();
    }
    public java.io.File f;
    // NOTE: Narflex - 6/8/09 - It's very important that if a file is locked for too long that we don't continue
    // to allow it to contribute to the disk free space; otherwise large files that are locked for things like commercial
    // detection could easily cause us to run out of diskspace since we think we have free space when in reality we don't.
    public long firstFailTime;
  }
}
