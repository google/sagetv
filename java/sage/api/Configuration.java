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
package sage.api;

import sage.BasicVideoFrame;
import sage.CaptureDevice;
import sage.Catbert;
import sage.DShowCaptureDevice;
import sage.DShowCaptureManager;
import sage.DShowDVDPlayer;
import sage.DShowMediaPlayer;
import sage.DShowTVPlayer;
import sage.DirectX9SageRenderer;
import sage.EPG;
import sage.EventRouter;
import sage.ExternalTuningManager;
import sage.SchedulerSelector;
import sage.SeekerSelector;
import sage.Java2DSageRenderer;
import sage.MMC;
import sage.MiniClientSageRenderer;
import sage.NetworkClient;
import sage.Pooler;
import sage.PredefinedJEPFunction;
import sage.SFIRTuner;
import sage.Sage;
import sage.SageConstants;
import sage.SageTV;
import sage.SageTVInfraredReceive;
import sage.Scheduler;
import sage.Seeker;
import sage.TVTuningFrequencies;
import sage.UIClient;
import sage.UIManager;
import sage.UserEvent;
import sage.VideoFrame;

/**
 * Configuration and properties for the whole system
 */
public class Configuration{
  private Configuration() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Configuration", "SaveProperties")
    {
      /**
       * Saves the Sage.properties file to disk
       *
       * @declaration public void SaveProperties();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null)
          stack.getUIMgr().savePrefs();
        Sage.savePrefs();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetLibraryImportPaths", true)
    {
      /**
       * Returns all of the library import paths that are currently configured for all media types
       * @return the library import paths that are currently configured for all media types
       *
       * @declaration public java.io.File[] GetLibraryImportPaths();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getArchiveDirectories(Seeker.ALL_DIR_MASK);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetPictureLibraryImportPaths", true)
    {
      /**
       * Returns the library import paths that are currently configured for picture files
       * @return the library import paths that are currently configured for picture files
       *
       * @declaration public java.io.File[] GetPictureLibraryImportPaths();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getArchiveDirectories(Seeker.PICTURE_DIR_MASK);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetMusicLibraryImportPaths", true)
    {
      /**
       * Returns the library import paths that are currently configured for music files
       * @return the library import paths that are currently configured for music files
       *
       * @declaration public java.io.File[] GetMusicLibraryImportPaths();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getArchiveDirectories(Seeker.MUSIC_DIR_MASK);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetVideoLibraryImportPaths", true)
    {
      /**
       * Returns the library import paths that are currently configured for video files
       * @return the library import paths that are currently configured for video files
       *
       * @declaration public java.io.File[] GetVideoLibraryImportPaths();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getArchiveDirectories(Seeker.VIDEO_DIR_MASK);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsMajorMinorDTVChannelTuningEnabled", true)
    {
      /**
       * Returns true if the legacy major/minor channel tuning is enabled. Do not use anymore
       * @deprecated
       * @return true if the legacy major/minor channel tuning is enabled
       *
       * @declaration public boolean IsMajorMinorDTVChannelTuningEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(SeekerSelector.getInstance().getUseDTVMajorMinorChans());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAutodialForInternet", true)
    {
      /**
       * Returns true if SageTV should attempt to autodial before accessing the Internet
       * @return true if SageTV should attempt to autodial before accessing the Internet
       *
       * @declaration public boolean GetAutodialForInternet();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(EPG.getInstance().getAutodial());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetAutodialForInternet", 1, new String[]{"Autodial"}, true)
    {
      /**
       * Sets whether or not SageTV should autodial before accessing the Internet
       * @param Autodial true if SageTV should autodial before accessing the Internet
       *
       * @declaration public void SetAutodialForInternet(boolean Autodial);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        EPG.getInstance().setAutodial(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsProvideoPentium4FixEnabled", true)
    {
      /**
       * Returns true if the Provideo 256 Pentium 4 fix is enabled
       * @return true if the Provideo 256 Pentium 4 fix is enabled
       * @deprecated
       *
       * @declaration public boolean IsProvideoPentium4FixEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(Sage.getBoolean("mmc/pentium_4_fix", false));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetProvideoPentium4FixEnabled", 1, new String[]{"Enabled"}, true)
    {
      /**
       * Sets whether or not the Provideo 256 Pentium 4 fix should be enabled
       * @param Enabled true if the Provideo 256 Pentium 4 fix should be enabled
       * @deprecated
       *
       * @declaration public void SetProvideoPentium4FixEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Sage.putBoolean("mmc/pentium_4_fix", evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetInfraredTuningPlugins", true)
    {
      /**
       * Gets the list of infrared tuning plugins that are available for use with SageTV
       * @return the list of infrared tuning plugins that are available for use with SageTV
       *
       * @declaration public String[] GetInfraredTuningPlugins();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String irPluginDir = SFIRTuner.getSFIRTunerPluginDir();
        if (irPluginDir == null)
          irPluginDir = System.getProperty("user.dir");
        java.io.File[] suspectDLLFiles = new java.io.File(irPluginDir).
            listFiles(new java.io.FilenameFilter(){
              public boolean accept(java.io.File dir,String filename){return filename.toLowerCase().endsWith(Sage.WINDOWS_OS ? ".dll" :
                (Sage.LINUX_OS ? ".so" : ".dylib"));}});
        String[] suspectDLLs = (suspectDLLFiles == null) ? Pooler.EMPTY_STRING_ARRAY : new String[suspectDLLFiles.length];
        for (int i = 0; i < suspectDLLs.length; i++)
          suspectDLLs[i] = suspectDLLFiles[i].getAbsolutePath();
        String[] irDevFiles = SFIRTuner.getValidDeviceFiles(suspectDLLs);
        if (irDevFiles.length > 0)
        {
          java.util.Set helpSet = new java.util.HashSet(java.util.Arrays.asList(irDevFiles));
          java.util.Iterator walker = helpSet.iterator();
          while (walker.hasNext())
          {
            String temp = (String) walker.next();
            if (temp.toLowerCase().indexOf("tunerstub.dll") != -1)
            {
              walker.remove();
              break;
            }
          }
          irDevFiles = (String[]) helpSet.toArray(Pooler.EMPTY_STRING_ARRAY);
        }
        return SFIRTuner.getPrettyDeviceNames(irDevFiles);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetRemotesForInfraredTuningPlugin", new String[] {"PluginName","PluginPortNumber"}, true)
    {
      /**
       * Gets the names of the 'Remote Controls' which correspond to external devices that can be controlled with the specified plugin
       * @param PluginName the name of the infrared tuning plugin to get the remotes for
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @return the names of the 'Remotes' which correspond to external devices that can be controlled by the specified plugin on the specified port
       *
       * @declaration public String[] GetRemotesForInfraredTuningPlugin(String PluginName, int PluginPortNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int portNum = getInt(stack);
        SFIRTuner tuney = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        return tuney != null ? tuney.getRemoteNames() : null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "AddRemoteForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName"}, true)
    {
      /**
       * Creates a new 'Remote Control' with the specified name for the specified plugin. Depending upon the plugin, this may require
       * the user to perform some interaction before it returns.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name to use for the newly created 'Remote Control'
       * @return the RemoteName parameter will be returned if a new 'Remote Control' is created, null will be returned if one already exists with that name
       *
       * @declaration public String AddRemoteForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        final String remoteName = getString(stack);
        int portNum = getInt(stack);
        final SFIRTuner tuney = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tuney == null) return Boolean.FALSE;
        return tuney.addNewRemote(remoteName);
        /*final Object taskID = Catbert.getNewTaskID();
			Thread asyncThread = new Thread("AsyncAddRemoteForInfraredTuningPlugin")
			{
				public void run()
				{
					Object rv;
					rv = tuney.addNewRemote(remoteName);
					Catbert.asyncTaskComplete(taskID, rv);
				}
			};
			asyncThread.setDaemon(true);
			asyncThread.start();
			return taskID;*/
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "LearnCommandForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName", "CommandName"}, true)
    {
      /**
       * Informs an infrared tuning plugin that it should go into the learn mode to learn a new command. This call will return after the user has
       * given the hardware the appropriate input it is looking for.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control' that the command is being learned from
       * @param CommandName the name of the command being sent from the remote control
       * @return true if the command was successfully learned, false otherwise
       *
       * @declaration public boolean LearnCommandForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName, String CommandName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        final String cmd = getString(stack);
        final String remoteName = getString(stack);
        int portNum = getInt(stack);
        final SFIRTuner tuney = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tuney == null) return Boolean.FALSE;
        Object rv;
        synchronized (tuney)
        {
          rv = Boolean.valueOf(tuney.recordNewCommand(remoteName, cmd));
          tuney.saveChanges();
        }
        return rv;
        /*final Object taskID = Catbert.getNewTaskID();
			Thread asyncThread = new Thread("AsyncLearnCommandForInfraredTuningPlugin")
			{
				public void run()
				{
					Object rv;
					synchronized (tuney)
					{
						 rv = Boolean.valueOf(tuney.recordNewCommand(remoteName, cmd));
						 tuney.saveChanges();
					}
					Catbert.asyncTaskComplete(taskID, rv);
				}
			};
			asyncThread.setDaemon(true);
			asyncThread.start();
			return taskID;*/
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RenameCommandForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName", "OldCommandName", "NewCommandName"}, true)
    {
      /**
       * Renames a command for a remote control on an infrared tuning plugin
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control' that the command is from
       * @param OldCommandName the current name of the command
       * @param NewCommandName the new name to rename this command to
       *
       * @declaration public void RenameCommandForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName, String OldCommandName, String NewCommandName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String cmd2 = getString(stack);
        String cmd1 = getString(stack);
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tuney = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tuney != null)
        {
          synchronized (tuney)
          {
            tuney.renameCommand(remoteName, cmd1, cmd2);
            tuney.saveChanges();
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RemoveRemoteForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName"}, true)
    {
      /**
       * Removes a 'Remote Control' from an infrared tuning plugin. This will also delete all commands learned by that 'Remote Control'.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control' to remove
       *
       * @declaration public void RemoveRemoteForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tuney = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tuney != null)
          tuney.removeRemote(remoteName);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RemoveCommandForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName", "CommandName"}, true)
    {
      /**
       * Removes a command from a 'Remote Control' for an infrared tuning plugin. If the command had a learned code, that code will be lost.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @param CommandName the name of the command to remove
       *
       * @declaration public void RemoveCommandForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName, String CommandName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String cmd = getString(stack);
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
        {
          synchronized (tun)
          {
            tun.removeCommand(remoteName, cmd);
            tun.saveChanges();
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetMaxChannelDigitsForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName", "NumberOfDigits"}, true)
    {
      /**
       * Sets the maximum number of digits that are needed to tune a channel on the device that corresponds to the specified 'Remote Control'.
       * For example, if you always need to push 3 numeric keys before the device would tune a channel (i.e. channels are from 1-999) then you
       * would specify 3 here.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @param NumberOfDigits the maximum number of digits needed to tune a channel using the specified remote on this plugin
       *
       * @declaration public void SetMaxChannelDigitsForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName, int NumberOfDigits);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int num = getInt(stack);
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
        {
          synchronized (tun)
          {
            tun.getRemoteInfo(remoteName);
            tun.setChannelDigits(num);
            tun.saveChanges();
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetMaxChannelDigitsForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName"}, true)
    {
      /**
       * Gets the maximum number of digits that are needed to tune a channel on the device that corresponds to the specified 'Remote Control'.
       * For example, if you always need to push 3 numeric keys before the device would tune a channel (i.e. channels are from 1-999) then this
       * should be 3.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @return the maximum number of digits needed to tune a channel using the specified remote on this plugin
       *
       * @declaration public int GetMaxChannelDigitsForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
          return new Integer(tun.getRemoteInfo(remoteName).channelDigits);
        else
          return new Integer(-1);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetButtonDelayForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName", "Delay(msec)"}, true)
    {
      /**
       * Sets the minimum delay in milliseconds that is required between sending consecutive commands using the specified 'Remote Control'.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @param MsecDelay the minimum required delay between commands in milliseconds
       *
       * @declaration public void SetButtonDelayForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName, int MsecDelay);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int num = getInt(stack);
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
        {
          synchronized (tun)
          {
            tun.getRemoteInfo(remoteName);
            tun.setButtonDelay(num);
            tun.saveChanges();
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetButtonDelayForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName"}, true)
    {
      /**
       * Gets the minimum delay in milliseconds that is required between sending consecutive commands using the specified 'Remote Control'.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @return the minimum required delay between commands in milliseconds
       *
       * @declaration public int GetButtonDelayForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
          return new Integer(tun.getRemoteInfo(remoteName).buttonDelay);
        else
          return new Integer(-1);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetSequenceDelayForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName", "Delay(msec)"}, true)
    {
      /**
       * Sets the minimum delay in milliseconds that is required between the completion of a sequence of commands that represent a channel change
       * and the start of a new sequence of commands that represent a channel change
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @param MsecDelay the minimum delay in milliseconds between finishing one channel change sequence and starting another when using the specified 'Remote Control'
       *
       * @declaration public void SetSequenceDelayForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName, int MsecDelay);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int num = getInt(stack);
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
        {
          synchronized (tun)
          {
            tun.getRemoteInfo(remoteName);
            tun.setSequenceDelay(num);
            tun.saveChanges();
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetSequenceDelayForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName"}, true)
    {
      /**
       * Gets the minimum delay in milliseconds that is required between the completion of a sequence of commands that represent a channel change
       * and the start of a new sequence of commands that represent a channel change
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @return the minimum delay in milliseconds between finishing one channel change sequence and starting another when using the specified 'Remote Control'
       *
       * @declaration public int GetSequenceDelayForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
          return new Integer(tun.getRemoteInfo(remoteName).sequenceDelay);
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetConfirmCommandForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName", "Command"}, true)
    {
      /**
       * Sets the command that is to be sent as the last command to complete a channel change sequence. This is for devices that require an
       * 'Enter' or other type of confirmation key to be pressed after the numeric channel codes are sent.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @param Command the name of the command from this 'Remote Control' to use to confirm a channel change
       *
       * @declaration public void SetConfirmCommandForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName, String Command);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String cmd = getString(stack);
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
        {
          synchronized (tun)
          {
            tun.getRemoteInfo(remoteName);
            tun.setConfirmKey(cmd);
            tun.saveChanges();
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetPrefixCommandForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName", "Command"}, true)
    {
      /**
       * Sets the command that is to be sent before a channel change sequence is started. This is for devices that require a
       * 'Power On' or 'Cancel' type of function to ensure the device is a responsive state.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @param Command the name of the command from this 'Remote Control' to use to prefix a channel change
       *
       * @since 6.2
       *
       * @declaration public void SetPrefixCommandForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName, String Command);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String cmd = getString(stack);
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
        {
          synchronized (tun)
          {
            tun.getRemoteInfo(remoteName);
            tun.setPrefixKey(cmd);
            tun.saveChanges();
          }
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetConfirmCommandForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName"}, true)
    {
      /**
       * Gets the command that is to be sent as the last command to complete a channel change sequence. This is for devices that require an
       * 'Enter' or other type of confirmation key to be pressed after the numeric channel codes are sent.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @return the name of the command from this 'Remote Control' to use to confirm a channel change
       *
       * @declaration public String GetConfirmCommandForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
          return tun.getRemoteInfo(remoteName).confirmCmd;
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetPrefixCommandForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName"}, true)
    {
      /**
       * Gets the command that is to be sent before a channel change sequence is started. This is for devices that require a
       * 'Power On' or 'Cancel' type of function to ensure the device is a responsive state.
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @return the name of the command from this 'Remote Control' to use to prefix a channel change
       *
       * @since 6.2
       *
       * @declaration public String GetPrefixCommandForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
          return tun.getRemoteInfo(remoteName).prefixCmd;
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetCarrierFrequencyForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName"}, true)
    {
      /**
       * Gets the carrier frequency detected by this plugin for the specified remote
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @return the carrier frequency of the specified 'Remote Control', or zero if the plugin doesn't have that information
       *
       * @declaration public long GetCarrierFrequencyForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
          return new Long(tun.getRemoteInfo(remoteName).carrier_freq);
        else
          return new Long(0);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetBitTimeForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName"}, true)
    {
      /**
       * Gets the 'bit time' detected by this plugin for the specified remote
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @return the 'bit time' of the specified 'Remote Control', or zero if the plugin doesn't have that information
       *
       * @declaration public long GetBitTimeForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        if (tun != null)
          return new Long(tun.getRemoteInfo(remoteName).bit_time);
        else
          return new Long(0);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetCommandsForInfraredTuningPlugin", new String[] { "PluginName","PluginPortNumber","RemoteName"}, true)
    {
      /**
       * Gets the list of commands for the specified 'Remote Control' for the specified plugin
       * @param PluginName the name of the infrared tuning plugin
       * @param PluginPortNumber the port number of the tuning plugin, 0 if there is no port for it
       * @param RemoteName the name of the 'Remote Control'
       * @return the list of commands for the specified 'Remote Control' for the specified plugin
       *
       * @declaration public java.util.Vector GetCommandsForInfraredTuningPlugin(String PluginName, int PluginPortNumber, String RemoteName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String remoteName = getString(stack);
        int portNum = getInt(stack);
        SFIRTuner tun = ExternalTuningManager.getIRTunerPlugin(getString(stack), portNum);
        java.util.Vector rv = new java.util.Vector();
        if (tun != null)
        {
          SFIRTuner.Remote rem = tun.getRemoteInfo(remoteName);
          if (rem != null)
          {
            SFIRTuner.Command cmd = rem.command;
            while (cmd != null)
            {
              rv.add(cmd.name);
              cmd = cmd.next;
            }
          }
        }
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetSubpropertiesThatAreBranches", 1, new String[] {"PropertyName"})
    {
      /**
       * Returns the list of properties underneath the specified property that themselves contain further subproperties.
       * For example, if the following were the properties:
       * animal/pig=0
       * animal/fish/salmon=1
       * animal/fish/trout=2
       * animal/reptile/green/alligator=4
       *
       * and this call was made with "animal" as the argument, then the returned values would be "fish" and "reptile"
       * @param PropertyName the root property to check for subproperty branches under
       * @return the list of properties underneath the specified property that themselves contain further subproperties.
       *
       * @declaration public String[] GetSubpropertiesThatAreBranches(String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() == null)
          return Sage.childrenNames(getString(stack));
        else
          return stack.getUIMgr().childrenNames(getString(stack));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetSubpropertiesThatAreLeaves", 1, new String[] {"PropertyName"})
    {
      /**
       * Returns the list of properties underneath the specified property that do not contain further subproperties.
       * For example, if the following were the properties:
       * animal/pig=0
       * animal/fish/salmon=1
       * animal/fish/trout=2
       * animal/reptile/green/alligator=4
       *
       * and this call was made with "animal" as the argument, then the returned values would only be "pig"
       * @param PropertyName the root property to check for subproperty leaves under
       * @return the list of properties underneath the specified property that do not contain further subproperties
       *
       * @declaration public String[] GetSubpropertiesThatAreLeaves(String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() == null)
          return Sage.keys(getString(stack));
        else
          return stack.getUIMgr().keys(getString(stack));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetServerSubpropertiesThatAreBranches", 1, new String[] {"PropertyName"}, true)
    {
      /**
       * Returns the list of properties underneath the specified property that themselves contain further subproperties.
       * For example, if the following were the properties:
       * animal/pig=0
       * animal/fish/salmon=1
       * animal/fish/trout=2
       * animal/reptile/green/alligator=4
       *
       * and this call was made with "animal" as the argument, then the returned values would be "fish" and "reptile"
       * If this is called from a client instance then it will use the properties on the server system for this call.
       * @param PropertyName the root property to check for subproperty branches under
       * @return the list of properties underneath the specified property that themselves contain further subproperties.
       *
       * @since 6.1
       *
       * @declaration public String[] GetServerSubpropertiesThatAreBranches(String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.childrenNames(getString(stack));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetServerSubpropertiesThatAreLeaves", 1, new String[] {"PropertyName"}, true)
    {
      /**
       * Returns the list of properties underneath the specified property that do not contain further subproperties.
       * For example, if the following were the properties:
       * animal/pig=0
       * animal/fish/salmon=1
       * animal/fish/trout=2
       * animal/reptile/green/alligator=4
       *
       * and this call was made with "animal" as the argument, then the returned values would only be "pig"
       * If this is called from a client instance then it will use the properties on the server system for this call.
       * @param PropertyName the root property to check for subproperty leaves under
       * @return the list of properties underneath the specified property that do not contain further subproperties
       *
       * @since 6.1
       *
       * @declaration public String[] GetServerSubpropertiesThatAreLeaves(String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.keys(getString(stack));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetProperty", 2, new String[] {"PropertyName", "DefaultValue"})
    {
      /**
       * Gets the property with the specified name. If the property is not set yet, it will be set to the DefaultValue
       * that is passed in unless that value is null
       * @param PropertyName the name of the property to retrieve
       * @param DefaultValue the default value to set the property to if it does not exist, null if it should not be set
       * @return the value of the specified property, or the DefaultValue if the property was not set prior to this call
       *
       * @declaration public String GetProperty(String PropertyName, String DefaultValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String def = getString(stack);
        String s = getString(stack);
        return (stack.getUIMgr() != null) ? stack.getUIMgr().get(s, def) : Sage.get(s, def);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetProperty", 2, new String[] {"PropertyName", "PropertyValue"})
    {
      /**
       * Sets the property with the specified name to the specified value.
       * @param PropertyName the name of the property to set
       * @param PropertyValue the value to set the property to
       *
       * @declaration public void SetProperty(String PropertyName, String PropertyValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String val = getString(stack);
        String s = getString(stack);
        if (stack.getUIMgr() == null)
          Sage.put(s, val);
        else
          stack.getUIMgr().put(s, val);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetServerProperty", new String[] {"PropertyName", "DefaultValue"}, true)
    {
      /**
       * Gets the property with the specified name. If the property is not set yet, it will be set to the DefaultValue
       * that is passed in unless that value is null. If this is called from a client instance then it will use the
       * properties on the server system for this call.
       * @param PropertyName the name of the property to retrieve
       * @param DefaultValue the default value to set the property to if it does not exist, null if it should not be set
       * @return the value of the specified property, or the DefaultValue if the property was not set prior to this call
       *
       * @declaration public String GetServerProperty(String PropertyName, String DefaultValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String def = getString(stack);
        return Sage.get(getString(stack), def);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetServerProperty", new String[] {"PropertyName", "PropertyValue"}, true)
    {
      /**
       * Sets the property with the specified name to the specified value. If this is called from a client instance then it will use the
       * properties on the server system for this call and the change will be made on the server system.
       * @param PropertyName the name of the property to set
       * @param PropertyValue the value to set the property to
       *
       * @declaration public void SetServerProperty(String PropertyName, String PropertyValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String val = getString(stack);
			 Sage.put(getString(stack), val); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RemoveProperty", 1, new String[] {"PropertyName"})
    {
      /**
       * Removes the specified property from the property map
       * @param PropertyName the name of the property to remove
       *
       * @declaration public void RemoveProperty(String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() == null)
				Sage.remove(getString(stack));
        else
				stack.getUIMgr().remove(getString(stack));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RemovePropertyAndChildren", 1, new String[] {"PropertyName"})
    {
      /**
       * Removes the specified property and any subproperties that start with the same prefix
       * @param PropertyName the name of the property that should be removed along with all of its subproperties
       *
       * @declaration public void RemovePropertyAndChildren(String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() == null)
				Sage.removeNode(getString(stack));
        else
				stack.getUIMgr().removeNode(getString(stack));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RemoveServerProperty", 1, new String[] {"PropertyName"}, true)
    {
      /**
       * Removes the specified property from the property map. If this is called from a client instance then it will use the
       * properties on the server system for this call and the change will be made on the server system.
       * @param PropertyName the name of the property to remove
       *
       * @since 6.1
       *
       * @declaration public void RemoveServerProperty(String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
			Sage.remove(getString(stack));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RemoveServerPropertyAndChildren", 1, new String[] {"PropertyName"}, true)
    {
      /**
       * Removes the specified property and any subproperties that start with the same prefix.
       * If this is called from a client instance then it will use the
       * properties on the server system for this call and the change will be made on the server system.
       * @param PropertyName the name of the property that should be removed along with all of its subproperties
       *
       * @since 6.1
       *
       * @declaration public void RemoveServerPropertyAndChildren(String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
				Sage.removeNode(getString(stack));
        return null;
      }});
    /*rft.put(new PredefinedJEPFunction("Configuration", "SetSystemScreenSaverAndPowerManagementEnabled", 1, new String[]{"True/False"})
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 return null;
			}});*/
    rft.put(new PredefinedJEPFunction("Configuration", "GetDefaultRecordingQuality", true)
    {
      /**
       * Gets the name of the default quality that is used to record television.
       * @return the name of the default quality that is used to record television
       *
       * @declaration public String GetDefaultRecordingQuality();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getDefaultQuality();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsIntelligentRecordingDisabled", true)
    {
      /**
       * Returns true if the 'Intelligent Recording' feature of SageTV is disabled. The default is disabled.
       * @return true if the 'Intelligent Recording' feature of SageTV is disabled, false otherwise
       *
       * @declaration public boolean IsIntelligentRecordingDisabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(SeekerSelector.getInstance().getDisableProfilerRecording());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "AddLibraryImportPath", 1, new String[]{"NewPath"}, true)
    {
      /**
       * Adds a new file path to the list of paths that SageTV checks for library import files. This path will
       * be used to search for music, video and picture files.
       * @param NewPath the file path to add to the list of library import paths
       *
       * @declaration public void AddLibraryImportPath(String NewPath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String f = getString(stack);
        if (f != null)
          SeekerSelector.getInstance().addArchiveDirectory(f, Seeker.ALL_DIR_MASK);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "AddPictureLibraryImportPath", 1, new String[]{"NewPath"}, true)
    {
      /**
       * Adds a new file path to the list of paths that SageTV checks for library import files. This path will
       * be used to search for picture files only.
       * @param NewPath the file path to add to the list of library import paths for picture files
       *
       * @declaration public void AddPictureLibraryImportPath(String NewPath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String f = getString(stack);
        if (f != null)
          SeekerSelector.getInstance().addArchiveDirectory(f, Seeker.PICTURE_DIR_MASK);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "AddMusicLibraryImportPath", 1, new String[]{"NewPath"}, true)
    {
      /**
       * Adds a new file path to the list of paths that SageTV checks for library import files. This path will
       * be used to search for music files only.
       * @param NewPath the file path to add to the list of library import paths for music files
       *
       * @declaration public void AddMusicLibraryImportPath(String NewPath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String f = getString(stack);
        if (f != null)
          SeekerSelector.getInstance().addArchiveDirectory(f, Seeker.MUSIC_DIR_MASK);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "AddVideoLibraryImportPath", 1, new String[]{"NewPath"}, true)
    {
      /**
       * Adds a new file path to the list of paths that SageTV checks for library import files. This path will
       * be used to search for video files only.
       * @param NewPath the file path to add to the list of library import paths for video files
       *
       * @declaration public void AddVideoLibraryImportPath(String NewPath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String f = getString(stack);
        if (f != null)
          SeekerSelector.getInstance().addArchiveDirectory(f, Seeker.VIDEO_DIR_MASK);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RemoveLibraryImportPath", 1, new String[]{"RemovePath"}, true)
    {
      /**
       * Removes a path from the list of library import paths. This will affect all import types.
       * @param RemovePath the path to remove from the list of library import paths
       *
       * @declaration public void RemoveLibraryImportPath(java.io.File RemovePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        if (f != null)
          SeekerSelector.getInstance().removeArchiveDirectory(f, Seeker.ALL_DIR_MASK);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RemovePictureLibraryImportPath", 1, new String[]{"RemovePath"}, true)
    {
      /**
       * Removes a path from the list of library import paths for pictures
       * @param RemovePath the path to remove from the list of library import paths for pictures
       *
       * @declaration public void RemovePictureLibraryImportPath(java.io.File RemovePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        if (f != null)
          SeekerSelector.getInstance().removeArchiveDirectory(f, Seeker.PICTURE_DIR_MASK);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RemoveMusicLibraryImportPath", 1, new String[]{"RemovePath"}, true)
    {
      /**
       * Removes a path from the list of library import paths for music
       * @param RemovePath the path to remove from the list of library import paths for music
       *
       * @declaration public void RemoveMusicLibraryImportPath(java.io.File RemovePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        if (f != null)
          SeekerSelector.getInstance().removeArchiveDirectory(f, Seeker.MUSIC_DIR_MASK);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RemoveVideoLibraryImportPath", 1, new String[]{"RemovePath"}, true)
    {
      /**
       * Removes a path from the list of library import paths for videos
       * @param RemovePath the path to remove from the list of library import paths for videos
       *
       * @declaration public void RemoveVideoLibraryImportPath(java.io.File RemovePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        if (f != null)
          SeekerSelector.getInstance().removeArchiveDirectory(f, Seeker.VIDEO_DIR_MASK);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDefaultRecordingQuality", 1, new String[]{"Quality"}, true)
    {
      /**
       * Sets the default quality that is used to record television
       * @param Quality the name of the quality setting to use to record television by default
       *
       * @declaration public void SetDefaultRecordingQuality(String Quality);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeekerSelector.getInstance().setDefaultQuality(getString(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetIntelligentRecordingDisabled", 1, new String[]{"Disabled"}, true)
    {
      /**
       * Sets whether or not 'Intelligent Recording' should be disabled.
       * @param Disabled should be true to disable 'Intelligent Recording', false to enable it
       *
       * @declaration public void SetIntelligentRecordingDisabled(boolean Disabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeekerSelector.getInstance().setDisableProfilerRecording(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetMajorMinorDTVChannelTuningEnabled", 1, new String[]{"Enabled"}, true)
    {
      /**
       * Sets whether or not SageTV should use legacy major-minor DTV channels. Not recommend for use anymore.
       * @param Enabled true if SageTV should use legacy major-minor DTV channel tuning
       * @deprecated
       *
       * @declaration public void SetMajorMinorDTVChannelTuningEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeekerSelector.getInstance().setUseDTVMajorMinorChans(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsScreenSaverOnSleepEnabled")
    {
      /**
       * Returns true if SageTV will go to its screen saver when it is put to sleep. If SageTV is not in full screen mode
       * then it will go to the system tray regardless of this setting.
       * @return true if SageTV will go to its screen saver when put to sleep, false otherwise
       *
       * @declaration public boolean IsScreenSaverOnSleepEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getScreenSaverOnSleep());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetScreenSaverOnSleepEnabled", 1, new String[]{"Enabled"})
    {
      /**
       * Sets whether or not SageTV should go to its screen saver when put to sleep. If SageTV is not in full screen mode
       * then it will go to the system tray regardless of this setting.
       * @param Enabled true if SageTV should go to the screen saver when put to sleep, false otherwise
       *
       * @declaration public void SetScreenSaverOnSleepEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().setScreenSaverOnSleep(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsSyncSystemClockWithServerEnabled")
    {
      /**
       * Returns true if SageTV should synchronize the system clock with the server. For SageTV Client this
       * will synchronize with its SageTV Server. For a SageTV system, this will synchronize with the EPG data server.
       * @return true if SageTV should synchronize the system clock with the server
       *
       * @declaration public boolean IsSyncSystemClockWithServerEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Sage.client && !Sage.isTrueClient())
          return makeNetworkedCall(stack);
        else
          return Boolean.valueOf(SageTV.getSyncSystemClock());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetSyncSystemClockWithServerEnabled", 1, new String[]{"Enabled"})
    {
      /**
       * Sets if SageTV should synchronize the system clock with the server. For SageTV Client this
       * will synchronize with its SageTV Server. For a SageTV system, this will synchronize with the EPG data server.
       * @param Enabled true if SageTV should synchronize the system clock with the server
       *
       * @declaration public void SetSyncSystemClockWithServerEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Sage.client && !Sage.isTrueClient())
          makeNetworkedCall(stack);
        else
          SageTV.setSyncSystemClock(evalBool(stack.pop()));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAspectRatioMode")
    {
      /**
       * Returns the localized name of the aspect ratio mode currently in use.
       * @return the localized name of the aspect ratio mode currently in use
       *
       * @declaration public String GetAspectRatioMode();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          // See if we do advanced aspect ratios
          MiniClientSageRenderer mcsr = (MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine();
          if (mcsr.supportsAdvancedAspectRatios())
          {
            return mcsr.getAdvancedAspectRatioMode();
          }
        }
        return BasicVideoFrame.getAspectRatioName(stack.getUIMgrSafe().getVideoFrame().getAspectRatioMode());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetAspectRatioMode", 1, new String[]{"AspectRatioMode"})
    {
      /**
       * Sets the aspect ratio mode that should currently be used.
       * @param AspectRatioMode the localized name of the aspect ratio mode that should currently be used
       *
       * @declaration public void SetAspectRatioMode(String AspectRatioMode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          // See if we do advanced aspect ratios
          MiniClientSageRenderer mcsr = (MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine();
          if (mcsr.supportsAdvancedAspectRatios())
          {
            mcsr.setAdvancedARMode(getString(stack));
            return null;
          }
        }
        stack.getUIMgrSafe().getVideoFrame().setAspectRatioMode(BasicVideoFrame.getAspectRatioCode(getString(stack))); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAspectRatioModes")
    {
      /**
       * Gets the list of localized names for the aspect ratio modes
       * @return the list of localized names for the aspect ratio modes
       *
       * @declaration public String[] GetAspectRatioModes();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          // See if we do advanced aspect ratios
          MiniClientSageRenderer mcsr = (MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine();
          if (mcsr.supportsAdvancedAspectRatios())
          {
            return mcsr.getAdvancedAspectRatioOptions();
          }
          else if (mcsr.isMediaExtender())
            return Pooler.EMPTY_STRING_ARRAY;
        }
        return BasicVideoFrame.getAspectRatioModes();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDisplayAspectRatio")
    {
      /**
       * Returns the display aspect ratio that is set for the current display device being used
       * @return the display aspect ratio that is set for the current display device being used
       * @since 5.1
       *
       * @declaration public float GetDisplayAspectRatio();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Float(stack.getUIMgrSafe().getVideoFrame().getDisplayAspectRatio());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDisplayAspectRatio", 1, new String[]{"PixelAspectRatio"})
    {
      /**
       * Sets the aspect ratio of the current display device
       * @param DisplayAspectRatio the aspect ratio of the current display device
       * @since 5.1
       *
       * @declaration public void SetDisplayAspectRatio(float DisplayAspectRatio);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setDisplayAspectRatio(getFloat(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsParentalControlEnabled")
    {
      /**
       * Returns true if parental controls are enabled on the system
       * @return true if parental controls are enabled on the system
       *
       * @declaration public boolean IsParentalControlEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().getEnablePC());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetParentalControlEnabled", 1, new String[]{"Enabled"}, true)
    {
      /**
       * Sets whether or not parental controls are enabled for the system
       * @param Enabled true if parental controls should be enabled, false otherwise
       *
       * @declaration public void SetParentalControlEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setEnablePC(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsMutedOnAlternateSpeedPlayback")
    {
      /**
       * Returns true if SageTV should mute the volume when media is played back at speeds besides x1
       * @return true if SageTV should mute the volume when media is played back at speeds besides x1, false otherwise
       *
       * @declaration public boolean IsMutedOnAlternateSpeedPlayback();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().getMuteOnAltSpeedPlay());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetMuteOnAlternateSpeedPlayback", 1, new String[]{"Enabled"})
    {
      /**
       * Sets whether or not SageTV should mute the sound when media is played back at a speed other than x1
       * @param Enabled true if SageTV should mute the sound when media is played at a speed other x1, false otherwise
       *
       * @declaration public void SetMuteOnAlternateSpeedPlayback(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setMuteOnAltSpeedPlay(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "HasParentalLockCode")
    {
      /**
       * Returns true if a parental lock code has been configured
       * @return true if a parental lock code has been configured, false otherwise
       *
       * @declaration public boolean HasParentalLockCode();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().hasPCCode());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetRatingsUnderParentalLock")
    {
      /**
       * Gets the list of content ratings that are under parental lock. When any content that has ratings that match anything
       * in the restricted list is requested for playback; the parental lock code must be entered before playback will begin.
       * @return the list of content ratings that are under parental lock
       *
       * @declaration public String[] GetRatingsUnderParentalLock();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getVideoFrame().getPCRestrictions();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsRatingUnderParentalLock", 1, new String[] { "Rating" })
    {
      /**
       * Returns true if the specified rating is in the list that is under parental control
       * @param Rating the rating to check
       * @return true if the specified rating is in the list that is under parental control, false otherwise
       *
       * @declaration public boolean IsRatingUnderParentalLock(String Rating);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(java.util.Arrays.asList(stack.getUIMgrSafe().getVideoFrame().getPCRestrictions()).contains(getString(stack)));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetVideoBackgroundColor")
    {
      /**
       * Returns the color that should be used for the video background. This is only shown when the video's aspect ratio doesn't match the area that it's playing back in.
       * @return the color that should be used for the video background
       *
       * @declaration public java.awt.Color GetVideoBackgroundColor();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getVideoFrame().getVideoBGColor();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetVideoBackgroundColor", 1, new String[]{"Color"})
    {
      /**
       * Sets the color that should be used for the video background. This is only shown when the video's aspect ratio doesn't match the area that it's playing back in.
       * @param Color the color that should be used for the video background
       *
       * @declaration public void SetVideoBackgroundColor(java.awt.Color Color);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setVideoBGColor(getColor(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetVideoOrbitingDuration")
    {
      /**
       * Gets the video orbiting duration in milliseconds. This is used to shift the video left-to-right very slowly over time. It
       * is used on widescreen displays to prevent screen burn when watching 4:3 content.
       * @return the video orbiting duration in milliseconds
       *
       * @declaration public long GetVideoOrbitingDuration();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(stack.getUIMgrSafe().getVideoFrame().getVideoHShiftFreq());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetVideoOrbitingDuration", 1, new String[]{"Duration"})
    {
      /**
       * Sets the video orbiting duration in milliseconds. This is used to shift the video left-to-right very slowly over time. It
       * is used on widescreen displays to prevent screen burn when watching 4:3 content.
       * @param Duration the video orbiting duration in milliseconds
       *
       * @declaration public void SetVideoOrbitingDuration(long Duration);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setVideoHShiftFreq(getLong(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetVideoZoomX", 1, new String[] {"AspectRatioMode"})
    {
      /**
       * Gets the horizontal scale factor for the video for the specified aspect ratio mode.
       * @param AspectRatioMode the localized name of the aspect ratio mode
       * @return the horizontal scale factor for the video for the specified aspect ratio mode
       *
       * @declaration public float GetVideoZoomX(String AspectRatioMode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Float(stack.getUIMgrSafe().getVideoFrame().getVideoZoomX(VideoFrame.getAspectRatioCode(getString(stack))));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetVideoZoomX", 2, new String[] {"AspectRatioMode", "ScaleFactor"})
    {
      /**
       * Sets the horizontal scale factor for the video for the specified aspect ratio mode.
       * @param AspectRatioMode the localized name of the aspect ratio mode
       * @param ScaleFactor the horizontal scale factor for the video for the specified aspect ratio mode
       *
       * @declaration public void SetVideoZoomX(String AspectRatioMode, float ScaleFactor);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        float f = getFloat(stack);
        String ass = getString(stack);
        stack.getUIMgrSafe().getVideoFrame().setVideoZoomX(f, VideoFrame.getAspectRatioCode(ass));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetVideoZoomY", 1, new String[] {"AspectRatioMode"})
    {
      /**
       * Gets the vertical scale factor for the video for the specified aspect ratio mode.
       * @param AspectRatioMode the localized name of the aspect ratio mode
       * @return the vertical scale factor for the video for the specified aspect ratio mode
       *
       * @declaration public float GetVideoZoomY(String AspectRatioMode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Float(stack.getUIMgrSafe().getVideoFrame().getVideoZoomY(VideoFrame.getAspectRatioCode(getString(stack))));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetVideoZoomY", 2, new String[] {"AspectRatioMode", "ScaleFactor"})
    {
      /**
       * Sets the vertical scale factor for the video for the specified aspect ratio mode.
       * @param AspectRatioMode the localized name of the aspect ratio mode
       * @param ScaleFactor the vertical scale factor for the video for the specified aspect ratio mode
       *
       * @declaration public void SetVideoZoomY(String AspectRatioMode, float ScaleFactor);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        float f = getFloat(stack);
        String ass = getString(stack);
        stack.getUIMgrSafe().getVideoFrame().setVideoZoomY(f, VideoFrame.getAspectRatioCode(ass));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetVideoOffsetX", 1, new String[] {"AspectRatioMode"})
    {
      /**
       * Gets of the offset in pixels that should be applied horizontally to the video. Positive numbers move it to the right.
       * @param AspectRatioMode the localized name of the aspect ratio mode
       * @return the offset in pixels that should be applied horizontally to the video
       *
       * @declaration public int GetVideoOffsetX(String AspectRatioMode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getVideoFrame().getVideoOffsetX(VideoFrame.getAspectRatioCode(getString(stack))));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetVideoOffsetX", 2, new String[] {"AspectRatioMode", "PixelOffset"})
    {
      /**
       * Sets of the offset in pixels that should be applied horizontally to the video. Positive numbers move it to the right.
       * @param AspectRatioMode the localized name of the aspect ratio mode
       * @param PixelOffset the offset in pixels that should be applied horizontally to the video
       *
       * @declaration public void SetVideoOffsetX(String AspectRatioMode, int PixelOffset);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int f = getInt(stack);
        String ass = getString(stack);
        stack.getUIMgrSafe().getVideoFrame().setVideoOffsetX(f, VideoFrame.getAspectRatioCode(ass));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetVideoOffsetY", 1, new String[] {"AspectRatioMode"})
    {
      /**
       * Gets of the offset in pixels that should be applied vertically to the video. Positive numbers move it down.
       * @param AspectRatioMode the localized name of the aspect ratio mode
       * @return the offset in pixels that should be applied vertically to the video
       *
       * @declaration public int GetVideoOffsetY(String AspectRatioMode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getVideoFrame().getVideoOffsetY(VideoFrame.getAspectRatioCode(getString(stack))));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetVideoOffsetY", 2, new String[] {"AspectRatioMode", "PixelOffset"})
    {
      /**
       * Sets of the offset in pixels that should be applied vertically to the video. Positive numbers move it down.
       * @param AspectRatioMode the localized name of the aspect ratio mode
       * @param PixelOffset the offset in pixels that should be applied vertically to the video
       *
       * @declaration public void SetVideoOffsetY(String AspectRatioMode, int PixelOffset);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int f = getInt(stack);
        String ass = getString(stack);
        stack.getUIMgrSafe().getVideoFrame().setVideoOffsetY(f, VideoFrame.getAspectRatioCode(ass));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetParentalLockCode", 1, new String[]{"ParentalLockCode"}, true)
    {
      /**
       * Sets the code that is to be used in order to unlock the parental controls.
       * @param ParentalLockCode the new value to set the parental lock code to
       *
       * @declaration public void SetParentalLockCode(String ParentalLockCode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setPCCode(getString(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetRecordingQualities", true)
    {
      /**
       * Returns a list of the different recording quality names available in the system
       * @return a list of the different recording quality names available in the system
       *
       * @declaration public String[] GetRecordingQualities();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        CaptureDevice[] capdevs = SchedulerSelector.getInstance().getMyEncoders();
        if (capdevs.length == 0)
          return MMC.getInstance().getAllEncodingQualities();
        else
        {
          java.util.Set rv = new java.util.HashSet();
          for (int i = 0; i < capdevs.length; i++)
            rv.addAll(java.util.Arrays.asList(capdevs[i].getEncodingQualities()));
          String[] rvs = (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
          java.util.Arrays.sort(rvs);
          return rvs;
        }
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetRecordingQualityBitrate", new String[] { "Quality" }, true)
    {
      /**
       * Gets the estimated overall bitrate for a given recording quality. The returned value is in Megabits per second.
       * @param Quality the name of the recording quality
       * @return the estimated overall bitrate for the specified recording quality in Megabits per second
       *
       * @declaration public long GetRecordingQualityBitrate(String Quality);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String qual = getString(stack);
        return new Long(MMC.getInstance().getQualityOverallBitrate(qual));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetRecordingQualityFormat", new String[] { "Quality" }, true)
    {
      /**
       * Gets the format for a given recording quality. Returns values like "MPEG-2 PS", "DivX", etc.
       * @param Quality the name of the recording quality
       * @return the format for the specified recording quality
       *
       * @declaration public String GetRecordingQualityFormat(String Quality);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String qual = getString(stack);
        return MMC.getInstance().getQualityFormat(qual);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetVideoDirectories", true)
    {
      /**
       * Gets the list of directories that SageTV is configured to record television to
       * @return the list of directories that SageTV is configured to record television to
       *
       * @declaration public java.io.File[] GetVideoDirectories();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getVideoStoreDirectories();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDiskspaceRuleForVideoDirectory", 1, new String[] {"Directory"}, true)
    {
      /**
       * Returns the diskspace rule that SageTV should use when managing the disk space in a given directory.
       * The names for the rules are localized versions of the strings "Use Only", "Use All" and "Leave Free"
       * @param Directory the video directory to get the diskspace rule for
       * @return the diskspace rule for the specified video directory
       *
       * @declaration public String GetDiskspaceRuleForVideoDirectory(java.io.File Directory);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getInstance().getRuleForDirectory(getFile(stack));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDiskspaceSizeForVideoDirectory", 1, new String[] {"Directory"}, true)
    {
      /**
       * Returns the size (in bytes) that should be applied to the specified directory for its configured diskspace rule
       * @param Directory the video directory to get the size for that applies to its rule
       * @return the size (in bytes) that should be applied to the specified directory for its configured diskspace rule
       *
       * @declaration public long GetDiskspaceSizeForVideoDirectory(java.io.File Directory);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(SeekerSelector.getInstance().getRuleSizeForDirectory(getFile(stack)));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RemoveVideoDirectory", 1, new String[] {"Directory"}, true)
    {
      /**
       * Removes a directory from SageTV's list of directories that it can record television to. If you delete all of the directories;
       * SageTV will be unable to record television. This does NOT delete the directory or any of its files from disk. They will still be fully
       * accessible in SageTV.
       * @param Directory the directory that SageTV should no longer record television content to
       *
       * @declaration public void RemoveVideoDirectory(java.io.File Directory);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeekerSelector.getInstance().removeVideoDirectory(getFile(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "AddVideoDirectory", 3, new String[] {"Directory", "Rule", "Size"}, true)
    {
      /**
       * Adds a new directory to SageTV's list of directories that it can record video to. Also specifies what rule &amp; size to use
       * when managing the diskspace for this directory.
       * @param Directory the directory to add as a video directory
       * @param Rule the diskspace rule to use for this directory see {@link #GetVideoDiskspaceRules GetVideoDiskspaceRules()}
       * @param Size the size to use for the specified diskspace rule and directory
       *
       * @declaration public void AddVideoDirectory(String Directory, String Rule, long Size);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long l = getLong(stack);
        String s = getString(stack);
        SeekerSelector.getInstance().addVideoDirectory(getString(stack), s, l); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "ChangeVideoDirectory", 4, new String[] { "OldDirectory", "NewDirectory", "NewRule", "NewSize"}, true)
    {
      /**
       * Changes the location of a given video directory, or modifies the diskspace rule or diskspace rule size for a video directory.
       * @param OldDirectory the currently configured path for the video directory that is to have its path or rules modified
       * @param NewDirectory the new file path to use for the video directory (this can be the same as OldDirectory)
       * @param NewRule the diskspace rule to use for this video directory, see {@link #GetVideoDiskspaceRules GetVideoDiskspaceRules()}
       * @param NewSize the size to use for this diskspace rule
       *
       * @declaration public void ChangeVideoDirectory(java.io.File OldDirectory, java.io.File NewDirectory, String NewRule, long NewSize);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long l = getLong(stack);
        String s = getString(stack);
        java.io.File newDir = getFile(stack);
        SeekerSelector.getInstance().changeVideoDirectory(getFile(stack), newDir, s, l); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetVideoDiskspaceRules")
    {
      /**
       * Returns the list of valid diskspace rules. Will be localized versions of the strings "Use All", "Use Only" and "Leave Free"
       * @return the list of valid diskspace rules
       *
       * @declaration public String[] GetVideoDiskspaceRules();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return SeekerSelector.getVideoDiskspaceRules();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetIRCodesLinkedToSageCommand", 1, new String[] {"SageCommand"})
    {
      /**
       * Returns the infrared codes that are linked to the given SageTV Command. When one of these IR codes is received; that will cause the
       * specified SageTV Command to get executed.
       * @param SageCommand the SageTV Command for which the IR codes should be retrieved
       * @return the infrared codes that are linked to the specified SageTV Command
       *
       * @declaration public long[] GetIRCodesLinkedToSageCommand(String SageCommand);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long[] ls = stack.getUIMgrSafe().getIRCodesForUE(UserEvent.getEvtCodeForName(getString(stack)));
        Long[] rv = new Long[ls.length];
        for (int i = 0; i < ls.length; i++)
          rv[i] = new Long(ls[i]);
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetKeystrokesLinkedToSageCommand", 1, new String[] {"SageCommand"})
    {
      /**
       * Returns the keystroke names that are linked to the given SageTV Command. When one of these keystrokes is received; that will cause the
       * specified SageTV Command to get executed.
       * @param SageCommand the SageTV Command for which the keystrokes should be retrieved
       * @return the keystrokes that are linked to the specified SageTV Command
       *
       * @declaration public String[] GetKeystrokesLinkedToSageCommand(String SageCommand);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.util.ArrayList v = stack.getUIMgrSafe().getRouter().
            getKBAccel(UserEvent.getEvtCodeForName(getString(stack)));
        String[] rv = new String[v.size()];
        for (int i = 0; i < v.size(); i++)
        {
          int[] ia = (int[]) v.get(i);
          rv[i] = Catbert.getStringFromKeystroke(ia[0], ia[1]);
        }
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetNameForIRCode", 1, new String[] {"IRCode"})
    {
      /**
       * Gets the name that is associated with an IR code. This has no functional purpose and is purely for display purposes.
       * @param IRCode the IRCode to get the associated name for, see {@link #SetNameForIRCode SetNameForIRCode()}
       * @return the name for the specified IRCode
       *
       * @declaration public String GetNameForIRCode(long IRCode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getNameForIRCode(getLong(stack));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetSageCommandLinkedToIRCode", 1, new String[] {"IRCode"})
    {
      /**
       * Returns the SageTV Command that is linked to the specified infrared code.
       * @param IRCode the infrared code
       * @return the SageTV Command that is linked to the specified infrared code
       *
       * @declaration public String GetSageCommandLinkedToIRCode(long IRCode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return UserEvent.getPrettyEvtName(stack.getUIMgrSafe().getUECodeForIRCode(getLong(stack)));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetSageCommandLinkedToKeystroke", 1, new String[] {"Keystroke"})
    {
      /**
       * Returns the SageTV Command that is linked to the specified keystroke.
       * @param Keystroke the name of the keystroke
       * @return the SageTV Command that is linked to the specified keystroke
       *
       * @declaration public String GetSageCommandLinkedToKeystroke(String Keystroke);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int[] k = Catbert.getKeystrokeFromString(getString(stack));
        return UserEvent.getPrettyEvtName(stack.getUIMgrSafe().getRouter().getUEIDForKB(k[0], k[1]));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetNameForIRCode", 1, new String[] {"IRCode", "Name"})
    {
      /**
       * Sets the name for a given IR code. This has no functional purpose, and is purely for display purposes.
       * @param IRCode the infrared code to attach a name to
       * @param Name the name to attach to the specified infrared code
       *
       * @declaration public void SetNameForIRCode(long IRCode, String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        stack.getUIMgrSafe().setNameForIRCode(getLong(stack), s); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "LinkIRCodeToSageCommand", 2, new String[] {"IRCode", "SageCommand"})
    {
      /**
       * Links an infrared code to a SageTV Command. The specified SageTV Command will then be executed whenever the specified IRCode is received.
       * @param IRCode the infrared code to link to the SageTV Command
       * @param SageCommand the SageTVCommand to link the infrared code to
       *
       * @declaration public void LinkIRCodeToSageCommand(long IRCode, String SageCommand);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        stack.getUIMgrSafe().addIRCodeForUE(getLong(stack), UserEvent.getEvtCodeForName(s));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "LinkKeystrokeToSageCommand", 2, new String[] {"Keystroke", "SageCommand"})
    {
      /**
       * Links a keystroke to a SageTV Command. The specified SageTV Command will then be executed whenever the specified keystroke is received.
       * @param Keystroke the name of the keystroke to link to the SageTV Command
       * @param SageCommand the SageTVCommand to link the keystroke to
       *
       * @declaration public void LinkKeystrokeToSageCommand(String Keystroke, String SageCommand);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        int[] kc = Catbert.getKeystrokeFromString(getString(stack));
        stack.getUIMgrSafe().getRouter().setKBPrefs(kc[0], kc[1], UserEvent.getEvtCodeForName(s), true);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "UnlinkIRCodeFromSageCommands", 1, new String[] {"IRCode"})
    {
      /**
       * Unlinks an infrared code from any SageTV commands it is linked to
       * @param IRCode the infrared code to unlink from any SageTV Commands that it is linked with
       *
       * @declaration public void UnlinkIRCodeFromSageCommands(long IRCode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long code = getLong(stack);
        stack.getUIMgrSafe().removeIRCodeForUE(code, stack.getUIMgrSafe().getUECodeForIRCode(code));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "UnlinkKeystrokeFromSageCommands", 1, new String[] {"Keystroke"})
    {
      /**
       * Unlinks a keystroke from any SageTV commands it is linked to
       * @param Keystroke the keystroke to unlink from any SageTV Commands that it is linked with
       *
       * @declaration public void UnlinkKeystrokeFromSageCommands(String Keystroke);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int[] kc = Catbert.getKeystrokeFromString(getString(stack));
        stack.getUIMgrSafe().getRouter().setKBPrefs(kc[0], kc[1], 0, false);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetRatingsUnderParentalControl", 1, new String[]{"RestrictionsList"}, true)
    {
      /*
       * Sets the list of ratings that are to be restricted by parental controls.
       * @param RestrictionsList the list of ratings that are to be restricted by parental controls. This can be a String[], an Object[] or a String with semicolon (;) delimited values.
       *
       * @declaration public SetRatingsUnderParentalControl(Object RestrictionsList);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setPCRestrictions(getStringList(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetRatingUnderParentalControl", 2, new String[]{"Rating", "Restricted"}, true)
    {
      /**
       * Sets whether or not a given rating should be restricted under parental control
       * @param Rating the name of the rating
       * @param Restricted true if the specified Rating should be restricted by parental control, false otherwise
       *
       * @declaration public void SetRatingUnderParentalControl(String Rating, boolean Restricted);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.util.HashSet curr = new java.util.HashSet(java.util.Arrays.asList(stack.getUIMgrSafe().getVideoFrame().getPCRestrictions()));
        if (evalBool(stack.pop()))
        {
          if (curr.add(getString(stack)))
            stack.getUIMgrSafe().getVideoFrame().setPCRestrictions((String[]) curr.toArray(Pooler.EMPTY_STRING_ARRAY));
        }
        else if (curr.remove(getString(stack)))
          stack.getUIMgrSafe().getVideoFrame().setPCRestrictions((String[]) curr.toArray(Pooler.EMPTY_STRING_ARRAY));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetInfraredReceiverPort")
    {
      /**
       * Gets the name of the port that the infrared receiver is connected to.
       * @return the name of the port that the infrared receiver is connected to; will be a value like: "COM1", "COM2" or "USB"
       *
       * @declaration public String GetInfraredReceiverPort();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.WINDOWS_OS ? ((SageTVInfraredReceive)stack.getUIMgrSafe().getRouter().getDefaultInputPlugin()).getIRReceivePort() : "";
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetInfraredReceiverPort", 1, new String[] { "Port" })
    {
      /**
       * Sets the name of the port that the infrared receiver is connected to.
       * @param Port the name of the port that the infrared receiver is connected to; should be a value like: "COM1", "COM2" or "USB"
       * @return true on success, false otherwise
       *
       * @declaration public boolean SetInfraredReceiverPort(String Port);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.WINDOWS_OS ? Boolean.valueOf(((SageTVInfraredReceive)stack.getUIMgrSafe().getRouter().getDefaultInputPlugin()).
            setIRReceivePort(getString(stack))) : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetInactivityTimeout")
    {
      /**
       * Gets the inactivity timeout in milliseconds. The hook InactivityTimeout() will be called whenever the system experiences inactivity
       * for this amount of time.
       * @return the inactivity timeout in milliseconds
       *
       * @declaration public int GetInactivityTimeout();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getInt(EventRouter.HIDE_WAIT_TIME, 5000));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetInactivityTimeout", 1, new String[] { "Timeout"})
    {
      /**
       * Sets the inactivity timeout in milliseconds. The hook InactivityTimeout() will be called whenever the system experiences inactivity
       * for this amount of time.
       * @param Timeout the inactivity timeout in milliseconds
       *
       * @declaration public void SetInactivityTimeout(int Timeout);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().putInt(EventRouter.HIDE_WAIT_TIME, getInt(stack));
        stack.getUIMgrSafe().getRouter().resetHideTimer();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetScreenSaverTimeout")
    {
      /**
       * Gets the screen saver timeout in milliseconds. The "Screen Saver" menu will be launched after this period of user inactivity if the
       * program is in full screen mode.
       * @return the screen saver timeout in milliseconds
       *
       * @declaration public int GetScreenSaverTimeout();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getInt(EventRouter.SCREEN_SAVER_TIME, (int)(Sage.MILLIS_PER_MIN*20)));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetScreenSaverTimeout", 1, new String[] { "Timeout"})
    {
      /**
       * Sets the screen saver timeout in milliseconds. The "Screen Saver" menu will be launched after this period of user inactivity if the
       * program is in full screen mode.
       * @param Timeout the screen saver timeout in milliseconds
       *
       * @declaration public void SetScreenSaverTimeout(int Timeout);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().putInt(EventRouter.SCREEN_SAVER_TIME, getInt(stack));
        stack.getUIMgrSafe().getRouter().resetSSTimer();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsSageTVServerEnabled", true)
    {
      /**
       * Returns true if the SageTV server is enabled. This allows SageTV Clients to connect.
       * @return true if the SageTV server is enabled, false otherwise
       *
       * @declaration public boolean IsSageTVServerEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(Sage.getBoolean("enable_server", true));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetSageTVServerEnabled", 1, new String[]{"Enabled"}, true)
    {
      /**
       * Sets whether or not the SageTV server should be enabled. This call will take effect immediately (i.e. the server can
       * be started/stopped on demand)
       * @param Enabled true if the SageTV server should be enabled, false otherwise
       *
       * @declaration public void SetSageTVServerEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SageTV.enableServer(evalBool(stack.pop()));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsAcceleratedDrawingEnabled")
    {
      /**
       * Returns true if 3D acceleration is enabled for the rendering engine. This uses DirectX9-3D on Windows and OpenGL on Linux.
       * @return true if 3D acceleration is enabled for the rendering engine
       *
       * @declaration public boolean IsAcceleratedDrawingEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getRootPanel().isAcceleratedDrawing());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetAcceleratedDrawingEnabled", 1, new String[]{"Enabled"})
    {
      /**
       * Specifies whether or not 3D acceleration should be enabled for the rendering engine. This uses DirectX9 on Windows and OpenGL on Linux.
       * This call takes effect immediately.
       * @param Enabled true if 3D acceleration should be enabled, false otherwise.
       * @return true if the change succeeded, false otherwise
       *
       * @declaration public boolean SetAcceleratedDrawingEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getRootPanel().setAcceleratedDrawing(evalBool(stack.pop()), true));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetOverscanOffsetX")
    {
      /**
       * Returns the horizontal offset in pixels that should be applied to the UI to account for overscan. This moves the UI to fit on a TV screen in the
       * case where it goes off the edges of the screen due to what is known as 'Overscan'.
       * @return the horizontal offset in pixles that should be applied to the UI to account for overscan, positive values move it to the right
       *
       * @declaration public int GetOverscanOffsetX();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getOverscanOffsetX());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetOverscanOffsetY")
    {
      /**
       * Returns the vertical offset in pixels that should be applied to the UI to account for overscan. This moves the UI to fit on a TV screen in the
       * case where it goes off the edges of the screen due to what is known as 'Overscan'.
       * @return the vertical offset in pixles that should be applied to the UI to account for overscan, positive values move it down
       *
       * @declaration public int GetOverscanOffsetY();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getOverscanOffsetY());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetOverscanScaleWidth")
    {
      /**
       * Returns the horizontal scale factor that should be applied to the UI to account for overscan. This shrinks the UI to fit on a TV screen in the
       * case where it goes off the edges of the screen due to what is known as 'Overscan'.
       * @return the horizontal scale factor that should be applied to the UI to account for overscan
       *
       * @declaration public float GetOverscanScaleWidth();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Float(stack.getUIMgrSafe().getOverscanScaleWidth());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetOverscanScaleHeight")
    {
      /**
       * Returns the vertical scale factor that should be applied to the UI to account for overscan. This shrinks the UI to fit on a TV screen in the
       * case where it goes off the edges of the screen due to what is known as 'Overscan'.
       * @return the vertical scale factor that should be applied to the UI to account for overscan
       *
       * @declaration public float GetOverscanScaleHeight();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Float(stack.getUIMgrSafe().getOverscanScaleHeight());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetOverscanOffsetX", new String[] { "Amount" })
    {
      /**
       * Sets the horizontal offset in pixels that should be applied to the UI to account for overscan. This moves the UI to fit on a TV screen in the
       * case where it goes off the edges of the screen due to what is known as 'Overscan'.
       * @param Amount the horizontal offset in pixles that should be applied to the UI to account for overscan, positive values move it to the right
       *
       * @declaration public void SetOverscanOffsetX(int Amount);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().setOverscanOffsetX(getInt(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetOverscanOffsetY", new String[] { "Amount" })
    {
      /**
       * Sets the vertical offset in pixels that should be applied to the UI to account for overscan. This moves the UI to fit on a TV screen in the
       * case where it goes off the edges of the screen due to what is known as 'Overscan'.
       * @param Amount the vertical offset in pixles that should be applied to the UI to account for overscan, positive values move it down
       *
       * @declaration public void SetOverscanOffsetY(int Amount);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().setOverscanOffsetY(getInt(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetOverscanScaleWidth", new String[] { "Amount" })
    {
      /**
       * Sets the horizontal scale factor that should be applied to the UI to account for overscan. This shrinks the UI to fit on a TV screen in the
       * case where it goes off the edges of the screen due to what is known as 'Overscan'.
       * @param Amount the horizontal scale factor that should be applied to the UI to account for overscan
       *
       * @declaration public void SetOverscanScaleWidth(float Amount);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().setOverscanScaleWidth(getFloat(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetOverscanScaleHeight", new String[] { "Amount" })
    {
      /**
       * Sets the vertical scale factor that should be applied to the UI to account for overscan. This shrinks the UI to fit on a TV screen in the
       * case where it goes off the edges of the screen due to what is known as 'Overscan'.
       * @param Amount the vertical scale factor that should be applied to the UI to account for overscan
       *
       * @declaration public void SetOverscanScaleHeight(float Amount);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().setOverscanScaleHeight(getFloat(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetOverscanInsetLeft")
    {
      /**
       * Returns a floating point value which specifies the percent of the UI that is taken up by overscan on the left side.
       * Equivalent to (GetOverscanOffsetX() * 1.0 / (GetFullUIWidth() * GetOverscanScaleWidth()))
       * @return a floating point value which specifies the percent of the UI that is taken up by overscan on the left side
       * @since 7.0
       *
       * @declaration public float GetOverscanInsetLeft();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null)
          return new Float(0);
        else
          return new Float(uiMgr.getOverscanOffsetX() * 1.0f / (uiMgr.getRootPanel().getWidth() * uiMgr.getOverscanScaleWidth()));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetOverscanInsetRight")
    {
      /**
       * Returns a floating point value which specifies the percent of the UI that is taken up by overscan on the right side.
       * Equivalent to (1.0 - (GetOverscanOffsetX() * 1.0 / GetFullUIWidth()) - GetOverscanScaleWidth())/GetOverscanScaleWidth()
       * @return a floating point value which specifies the percent of the UI that is taken up by overscan on the right side
       * @since 7.0
       *
       * @declaration public float GetOverscanInsetRight();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null)
          return new Float(0);
        else
          return new Float((1.0f - (uiMgr.getOverscanOffsetX() * 1.0f / (uiMgr.getRootPanel().getWidth())) - uiMgr.getOverscanScaleWidth())/uiMgr.getOverscanScaleWidth());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetOverscanInsetTop")
    {
      /**
       * Returns a floating point value which specifies the percent of the UI that is taken up by overscan on the top
       * Equivalent to (GetOverscanOffsetY() * 1.0 / (GetFullUIHeight() * GetOverscanScaleHeight())))
       * @return a floating point value which specifies the percent of the UI that is taken up by overscan on the top
       * @since 7.0
       *
       * @declaration public float GetOverscanInsetTop();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null)
          return new Float(0);
        else
          return new Float(uiMgr.getOverscanOffsetY() * 1.0f / (uiMgr.getRootPanel().getHeight() * uiMgr.getOverscanScaleHeight()));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetOverscanInsetBottom")
    {
      /**
       * Returns a floating point value which specifies the percent of the UI that is taken up by overscan on the bottom
       * Equivalent to (1.0 - (GetOverscanOffsetY() * 1.0 / GetFullUIHeight()) - GetOverscanScaleHeight())/GetOverscanScaleHeight()
       * @return a floating point value which specifies the percent of the UI that is taken up by overscan on the bottom
       * @since 7.0
       *
       * @declaration public float GetOverscanInsetBottom();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null)
          return new Float(0);
        else
          return new Float((1.0f - (uiMgr.getOverscanOffsetY() * 1.0f / (uiMgr.getRootPanel().getHeight())) - uiMgr.getOverscanScaleHeight())/uiMgr.getOverscanScaleHeight());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetOverscanInsets")
    {
      /**
       * Returns a length 4 array of floating point values which specifies the percent of the UI that is taken up by overscan on the 4 sides.
       * The order is top, left, bottom, right in the returned array. Each value comes from GetOverscanInsetTop/Left/Bottom/Right() API calls.
       * @return a length 4 array of floating point values which specifies the percent of the UI that is taken up by overscan on the 4 sides
       * @since 7.0
       *
       * @declaration public Float[] GetOverscanInsets();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null)
          return new Float[] { new Float(0), new Float(0), new Float(0), new Float(0) };
        else
        {
          return new Float[] {
              new Float(uiMgr.getOverscanOffsetY() * 1.0f / (uiMgr.getRootPanel().getHeight() * uiMgr.getOverscanScaleHeight())),
              new Float(uiMgr.getOverscanOffsetX() * 1.0f / (uiMgr.getRootPanel().getWidth() * uiMgr.getOverscanScaleWidth())),
              new Float((1.0f - (uiMgr.getOverscanOffsetY() * 1.0f / uiMgr.getRootPanel().getHeight()) - uiMgr.getOverscanScaleHeight())/uiMgr.getOverscanScaleHeight()),
              new Float((1.0f - (uiMgr.getOverscanOffsetX() * 1.0f / uiMgr.getRootPanel().getWidth()) - uiMgr.getOverscanScaleWidth())/uiMgr.getOverscanScaleWidth())
          };
        }
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAvailableCountries")
    {
      /**
       * Gets the names of the countries that can be used in the {@link #SetCountry SetCountry()} call.
       * @return the possible names for country selection
       *
       * @declaration public String[] GetAvailableCountries();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return TVTuningFrequencies.COUNTRIES.clone();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetCountry", new String[] {"Country"}, true)
    {
      /**
       * Sets the country that the user is in. This is used for determining the frequencies used for tuning television signals.
       * @param Country the name of the country the user is in, should be a value from {@link #GetAvailableCountries GetAvailableCountries()}
       * @return the passed in Country variable
       *
       * @declaration public String SetCountry(String Country);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String cnt = getString(stack);
        Sage.put("mmc/country", cnt);
        Sage.putInt("mmc/country_code", TVTuningFrequencies.getCountryCode(cnt));
        return cnt;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetCountry", true)
    {
      /**
       * Gets the country that SageTV is configured for. This is used for determining the frequencies used for tuning television signals.
       * @return the name of the country that SageTV is configured for
       *
       * @declaration public String GetCountry();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.get("mmc/country", Sage.rez("default_country"));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetAnalogVideoFormat", new String[] { "Format" }, true)
    {
      /**
       * Sets the analog video format that should be used for capture and playback. Valid values are "NTSC" or "PAL"
       * @param Format the analog video format that should be used for capture and playback; valid values are "NTSC" or "PAL"
       *
       * @declaration public void SetAnalogVideoFormat(String Format);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean isPAL;
        Sage.putInt("mmc/video_format_code", (isPAL = "PAL".equalsIgnoreCase(getString(stack))) ? 8 : 1);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAnalogVideoFormat", true)
    {
      /**
       * Gets the analog video format that should be used for capture and playback. Valid values are "NTSC" or "PAL"
       * @return the analog video format that should be used for capture and playback
       *
       * @declaration public String GetAnalogVideoFormat();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return MMC.getInstance().isNTSCVideoFormat() ? "NTSC" : "PAL";
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetUILanguage")
    {
      /**
       * Gets the language that the user interface should be displayed in.
       * @return the language that the user interface should be displayed in
       *
       * @declaration public String GetUILanguage();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Sage.userLocale == null) return null;
        // If the display name matches a language choice, use that. Otherwise use the display language
        String lang = Sage.userLocale.getDisplayName(Sage.userLocale);
        String availLangCodes = Sage.get("i18n_options", "en,fr,it,de,es,nl");
        java.util.StringTokenizer toker = new java.util.StringTokenizer(availLangCodes, ",;");
        String[] langNames = new String[toker.countTokens()];
        for (int i = 0; i < langNames.length; i++)
        {
          String currLangCode = toker.nextToken();
          String currContCode = "";
          int undIdx = currLangCode.indexOf("_");
          if (undIdx != -1)
          {
            currContCode = currLangCode.substring(undIdx + 1);
            currLangCode = currLangCode.substring(0, undIdx);
          }
          java.util.Locale testLocale = new java.util.Locale(currLangCode, currContCode);
          if (lang.equalsIgnoreCase(testLocale.getDisplayName(testLocale)))
          {
            return (lang == null || lang.length() < 3) ? lang :
              ((lang.substring(0, 1).toUpperCase()) + lang.substring(1));
          }
        }
        lang = Sage.userLocale.getDisplayLanguage(Sage.userLocale);
        return (lang == null || lang.length() < 3) ? lang :
          ((lang.substring(0, 1).toUpperCase()) + lang.substring(1));

      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetUILanguage", new String[] { "Language" })
    {
      /**
       * Sets the language that the user interface should be displayed in. This takes effect immediately.
       * Use the {@link #GetAvailableUILanguages GetAvailableUILanguages()} call to get the options for this.
       * @param Language the language that the user interface should be displayed in
       *
       * @declaration public void SetUILanguage(String Language);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        // Figure out what code this should be
        String newLang = getString(stack);
        String availLangCodes = Sage.get("i18n_options", "en,fr,it,de,es,nl");
        java.util.StringTokenizer toker = new java.util.StringTokenizer(availLangCodes, ",;");
        String[] langNames = new String[toker.countTokens()];
        for (int i = 0; i < langNames.length; i++)
        {
          String currLangCode = toker.nextToken();
          String currContCode = "";
          int undIdx = currLangCode.indexOf("_");
          if (undIdx != -1)
          {
            currContCode = currLangCode.substring(undIdx + 1);
            currLangCode = currLangCode.substring(0, undIdx);
          }
          java.util.Locale testLocale = new java.util.Locale(currLangCode, currContCode);
          if (newLang.equalsIgnoreCase(testLocale.getDisplayName(testLocale)))
          {
            Sage.setLanguageCode(currLangCode, currContCode);
            return null;
          }
        }
        // If we didn't find it then just try the arg itself
        Sage.setLanguageCode(newLang, "");
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAvailableUILanguages")
    {
      /**
       * Gets the list of languages that are available to be used for the user interface.
       * @return the list of languages that are available to be used for the user interface
       *
       * @declaration public String[] GetAvailableUILanguages();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String availLangCodes = Sage.get("i18n_options", "en,fr,it,de,es,nl");
        java.util.StringTokenizer toker = new java.util.StringTokenizer(availLangCodes, ",;");
        String[] langNames = new String[toker.countTokens()];
        for (int i = 0; i < langNames.length; i++)
        {
          String currLangCode = toker.nextToken();
          String currContCode = "";
          int undIdx = currLangCode.indexOf("_");
          if (undIdx != -1)
          {
            currContCode = currLangCode.substring(undIdx + 1);
            currLangCode = currLangCode.substring(0, undIdx);
          }
          java.util.Locale testLocale = new java.util.Locale(currLangCode, currContCode);
          langNames[i] = testLocale.getDisplayName(testLocale);
          langNames[i] = (langNames[i] == null || langNames[i].length() < 3) ? langNames[i] :
            ((langNames[i].substring(0, 1).toUpperCase()) + langNames[i].substring(1));
        }
        return langNames;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetSubpicAudioLanguageOptions")
    {
      /**
       * Gets the list of languages that are available to be used as presets for Audio tracks or Subpicture.
       * @return the list of languages that are available to be used as presets for Audio tracks or Subpicture.
       *
       * @declaration public String[] GetSubpicAudioLanguageOptions();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String availLangCodes = Sage.get("media_language_options", VideoFrame.DEFAULT_MEDIA_LANGUAGE_OPTIONS);
        java.util.StringTokenizer toker = new java.util.StringTokenizer(availLangCodes, "|");
        String[] langNames = new String[toker.countTokens()];
        for (int i = 0; i < langNames.length; i++)
        {
          String currLangCode = toker.nextToken();
          int semiIdx1 = currLangCode.indexOf(';');
          int semiIdx2 = currLangCode.lastIndexOf(';');
          langNames[i] = currLangCode.substring(semiIdx1 + 1, semiIdx2);
        }
        return langNames;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDefaultAudioLanguage")
    {
      /**
       * Gets the name of the preferred default language when selecting which audio stream to playback.
       * @return the name of the preferred default language when selecting which audio stream to playback
       *
       * @declaration public String GetDefaultAudioLanguage();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().get("default_audio_language", "English");
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDefaultAudioLanguage", new String[] { "Language" })
    {
      /**
       * Sets the name of the preferred default language when selecting which audio stream to playback. The values for this should be
       * obtained from GetSubpicAudioLanguageOptions.
       * @param Language the name of the preferred default language when selecting which audio stream to playback
       *
       * @declaration public void SetDefaultAudioLanguage(String Language);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        if (s == null)
          s = "English"; // default
        stack.getUIMgrSafe().put("default_audio_language", s);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDefaultSubpicLanguage")
    {
      /**
       * Gets the name of the preferred default language when selecting which subpicture stream to playback. This
       * will be the empty string if subtitles are preferred to be off by default.
       * @return the name of the preferred default language when selecting which subpicture stream to playback or the empty string for no subtitles
       *
       * @declaration public String GetDefaultSubpicLanguage();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().get("default_subpic_language", "");
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDefaultSubpicLanguage", new String[] { "Language" })
    {
      /**
       * Sets the name of the preferred default language when selecting which subpicture stream to playback. The values for this should be
       * obtained from GetSubpicAudioLanguageOptions, the value of null or the empty string is also allowed to indicate no subtitle track should be selected by default.
       * @param Language the name of the preferred default language when selecting which subpicture stream to playback, null or the empty string can be used to prefer no subtitles
       *
       * @declaration public void SetDefaultSubpicLanguage(String Language);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        if (s == null)
          s = ""; // default
        stack.getUIMgrSafe().put("default_subpic_language", s);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetTimeZone")
    {
      /**
       * Gets the name of the time zone that the application is currently configured to use
       * @return the name of the time zone that the application is currently configured to use
       *
       * @declaration public String GetTimeZone();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String tzprop = Sage.get("time_zone", "");
        java.util.TimeZone tz = (tzprop != null && tzprop.length() > 0) ? java.util.TimeZone.getTimeZone(tzprop) :
          java.util.TimeZone.getDefault();
        return tz.getDisplayName(true, java.util.TimeZone.SHORT, Sage.userLocale) + " " +
        tz.getDisplayName(Sage.userLocale) + " (" + tz.getID() + ")";
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetTimeZone", new String[] { "TimeZone" })
    {
      /**
       * Sets the name of the time zone that the application should use. This takes effect immediately.
       * @param TimeZone the name of the time zone to use, see {@link #GetAllTimeZones GetAllTimeZones()}
       *
       * @declaration public void SetTimeZone(String TimeZone);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        // Figure out what code this should be
        String newTZ = getString(stack).trim();
        int parIdx = newTZ.lastIndexOf('(');
        if (parIdx != -1 && newTZ.lastIndexOf(')') > parIdx)
          Sage.setTimeZone(newTZ.substring(Math.max(parIdx + 1, newTZ.lastIndexOf(',') + 1), newTZ.lastIndexOf(')')).trim());
        else
          Sage.setTimeZone(newTZ);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAllTimeZones")
    {
      /**
       * Gets the list of all of the valid time zone names
       * @return the list of all of the valid time zone names
       *
       * @declaration public java.util.Vector GetAllTimeZones();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] tzids = java.util.TimeZone.getAvailableIDs();
        java.util.Vector rv = new java.util.Vector();
        java.util.TimeZone lastTZ = null;
        String lastTZStr = "";
        // NOTE: This needs to be better consolidated
        for (int i = 0; i < tzids.length; i++)
        {
          java.util.TimeZone tz = java.util.TimeZone.getTimeZone(tzids[i]);
          if (lastTZ == null || !lastTZ.hasSameRules(tz))
          {
            if (lastTZ != null)
            {
              lastTZStr += ")";
              rv.add(lastTZStr);
            }
            lastTZ = tz;
            lastTZStr = tz.getDisplayName(true, java.util.TimeZone.SHORT, Sage.userLocale) + " " +
                tz.getDisplayName(Sage.userLocale) + " (" + tzids[i];
          }
          else
          {
            lastTZStr += ", " + tzids[i];
          }
        }
        rv.add(lastTZStr + ")");
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVBTRegions", true)
    {
      /**
       * Gets the names of the regions for the configured country which are used for dividing it up into
       * different areas for DVB-T reception. Returns a zero-length list if there are no sub-regions within the country.
       * Use the results from this in a call to {@link #SetDVBTRegion SetDVBTRegion(DVBTRegion)}
       * @return the region names in the country to use for DVBT reception differentiation
       *
       * @since 5.1
       *
       * @declaration public String[] GetDVBTRegions();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return TVTuningFrequencies.getDVBTRegionsForCountry(TVTuningFrequencies.getCountryCode(
            Sage.get("mmc/country", Sage.rez("default_country"))));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDVBTRegion", new String[] {"DVBTRegion"}, true)
    {
      /**
       * Sets the the region within the current country that the user is in.
       * @param DVBTRegion the name of the region the user is in, should be a value from {@link #GetDVBTRegions GetDVBTRegions()}
       *
       * @since 5.1
       *
       * @declaration public void SetDVBTRegion(String DVBTRegion);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String cnt = getString(stack);
        Sage.put("mmc/dvbt_region", cnt);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVBTRegion", true)
    {
      /**
       * Gets the the region within the current country that the user is in for DVB-T tuning purposes.
       * Must have been set with a call to {@link #SetDVBTRegion SetDVBTRegion(DVBTRegion)}
       * @return the name of the region the user is in
       *
       * @since 5.1
       *
       * @declaration public String GetDVBTRegion();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.get("mmc/dvbt_region", "");
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVBCRegions", true)
    {
      /**
       * Gets the names of the DVB-C providers for the configured country which are used for dividing it up into
       * different areas for DVB-C reception. Returns a zero-length list if there are one or no providers in this country.
       * Use the results from this in a call to {@link #SetDVBCRegion SetDVBCRegion(DVBCRegion)}
       * @return the provider names in the country to use for DVBC reception differentiation
       *
       * @since 5.1
       *
       * @declaration public String[] GetDVBCRegions();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return TVTuningFrequencies.getDVBCRegionsForCountry(TVTuningFrequencies.getCountryCode(
            Sage.get("mmc/country", Sage.rez("default_country"))));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDVBCRegion", new String[] {"DVBCRegion"}, true)
    {
      /**
       * Sets the the provider within the current country that the user has for DVBC reception.
       * @param DVBCRegion the name of the provider the user has, should be a value from {@link #GetDVBCRegions GetDVBCRegions()}
       *
       * @since 5.1
       *
       * @declaration public void SetDVBCRegion(String DVBCRegion);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String cnt = getString(stack);
        Sage.put("mmc/dvbc_region", cnt);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVBCRegion", true)
    {
      /**
       * Gets the the provider within the current country that the user has for DVB-C tuning purposes.
       * Must have been set with a call to {@link #SetDVBCRegion SetDVBCRegion(DVBCRegion)}
       * @return the name of the provider the user has for DVB-C
       *
       * @since 5.1
       *
       * @declaration public String GetDVBCRegion();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.get("mmc/dvbc_region", "");
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVBSRegions", true)
    {
      /**
       * Gets the names of the providers for the configured country which are used for dividing it up into
       * different areas for DVB-S reception. Returns a zero-length list if there are one or no providers within the country.
       * Use the results from this in a call to {@link #SetDVBSRegion SetDVBSRegion(DVBSRegion)}
       * @return the providers names in the country to use for DVBS reception differentiation
       *
       * @since 5.1
       *
       * @declaration public String[] GetDVBSRegions();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return TVTuningFrequencies.getDVBSRegionsForCountry(TVTuningFrequencies.getCountryCode(
            Sage.get("mmc/country", Sage.rez("default_country"))));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDVBSRegion", new String[] {"DVBSRegion"}, true)
    {
      /**
       * Sets the the provider within the current country that the user has for DVBS reception.
       * @param DVBSRegion the name of the provider the user has, should be a value from {@link #GetDVBSRegions GetDVBSRegions()}
       *
       * @since 5.1
       *
       * @declaration public void SetDVBSRegion(String DVBSRegion);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String cnt = getString(stack);
        Sage.put("mmc/dvbs_region", cnt);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVBSRegion", true)
    {
      /**
       * Gets the the provider within the current country that the user has for DVB-S tuning purposes.
       * Must have been set with a call to {@link #SetDVBSRegion SetDVBSRegion(DVBSRegion)}
       * @return the name of the provider the user has for DVB-S
       *
       * @since 5.1
       *
       * @declaration public String GetDVBSRegion();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.get("mmc/dvbs_region", "");
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "CanDoNativeOutputResolution")
    {
      /**
       * Returns true if this device can do native output resolutions (where output resolution
       * matches the format of the media being played)
       * @return true if this device can do native output resolutions
       *
       * @since 6.3
       *
       * @declaration public boolean CanDoNativeOutputResolution();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          String[] resOpts = ((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).getResolutionOptions();
          return (resOpts != null && resOpts.length > 1) ? Boolean.TRUE : Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsNativeOutputResolutionEnabled")
    {
      /**
       * Returns true if this device is configured to match the output resolution to the media format's resolution
       * @return true if this device is configured to match the output resolution to the media format's resolution
       *
       * @since 6.3
       *
       * @declaration public boolean IsNativeOutputResolutionEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null)
          return stack.getUIMgr().getBoolean("native_output_resolution_switching", false) ? Boolean.TRUE : Boolean.FALSE;
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetNativeOutputResolutionEnabled", new String[] { "Enabled" })
    {
      /**
       * Sets whether or not this device should try to match its output resolution to the media format's resolution
       * @param Enabled true if this device should be configured to match the output resolution to the media format's resolution; false to use a fixed resolution
       *
       * @since 6.3
       *
       * @declaration public void SetNativeOutputResolutionEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean x = evalBool(stack.pop());
        if (stack.getUIMgr() != null)
          stack.getUIMgr().putBoolean("native_output_resolution_switching", x);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAudioOutput")
    {
      /**
       * Returns a string describing the currently selected audio output (empty string if this can't be configured)
       * @return a string describing the currently selected audio output (empty string if this can't be configured)
       *
       * @since 6.3
       *
       * @declaration public String GetAudioOutput();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          return ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).getAudioOutput();
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAudioOutputOptions")
    {
      /**
       * Returns a list of the possible audio outputs. This is currently only used
       * on media extender devices that have adjustable audio outputs.
       * @return an array of the audio outputs that can be used, null if this change is not supported
       *
       * @since 6.3
       *
       * @declaration public String[] GetAudioOutputOptions();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          return ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).getAudioOutputOptions();
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetAudioOutput", new String[] { "AudioOutput" })
    {
      /**
       * Sets the current audio output. This is currently only used
       * on media extender devices that have configurable audio outputs.
       * @param AudioOutput this must be a value from {@link #GetAudioOutputOptions GetAudioOutputOptions()} and should be the new desired audio output
       *
       * @since 6.3
       *
       * @declaration public void SetAudioOutput(String AudioOutput);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String newRes = getString(stack);
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).setAudioOutput(newRes);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetHDMIAutodetectedConnector")
    {
      /**
       * Returns a string describing the type of connection autodetected on HDMI (currently only used on media extenders)
       * @return a string describing the type of connection autodetected on HDMI (empty string if this isn't supported)
       *
       * @since 6.3
       *
       * @declaration public String GetHDMIAutodetectedConnector();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          return ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).getHDMIConnectorAutodetect();
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsAdvancedDeinterlacingSupported")
    {
      /**
       * Returns true if the client supports doing advanced deinterlacing. This is only available on the HD300.
       * @return true if the client supports doing advanced deinterlacing, false otherwise
       *
       * @declaration public boolean IsAdvancedDeinterlacingSupported();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          return ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).supportsAdvancedDeinterlacing() ? Boolean.TRUE : Boolean.FALSE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsAdvancedDeinterlacingEnabled")
    {
      /**
       * Returns true if the client supports doing advanced deinterlacing and it is enabled. This is only available on the HD300.
       * @return true if the client supports doing advanced deinterlacing and it is enabled, false otherwise
       *
       * @declaration public boolean IsAdvancedDeinterlacingEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          return ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).isAdvancedDeinterlacingEnabled() ? Boolean.TRUE : Boolean.FALSE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetAdvancedDeinterlacingEnabled", new String[] { "Enabled" })
    {
      /**
       * Allows enabling/disabling of advanced deinterlacing support. This is only available on the HD300.
       * @param Enabled true if advanced deinterlacing should be enabled, false if it should be disabled
       *
       * @declaration public void SetAdvancedDeinterlacingEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean x = evalBool(stack.pop());
        if (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.REMOTE_UI &&
            stack.getUIMgr().getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          ((MiniClientSageRenderer)stack.getUIMgr().getRootPanel().getRenderEngine()).setAdvancedDeinterlacing(x);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "RunFilterAutoconfiguration")
    {
      /**
			 * Sets the DirectShow audio &amp; video decoders on windows to acceptable values
       * @deprecated
       *
       * @declaration public void RunFilterAutoconfiguration();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Sage.WINDOWS_OS) DShowTVPlayer.autoOptimize(false); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDXVAMpegMode")
    {
      /**
       * Gets the preferred DXVA MPEG Mode configuration setting (Windows only). This will be A, B, C or D (or Default if not set)
       * This is used when playing back MPEG2 files.
       * @return the preferred DXVA MPEG Mode configuration setting
       *
       * @declaration public String GetDXVAMpegMode();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.getDXVAName(DShowTVPlayer.getDxvaMpegMode());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDXVAMpegMode", 1, new String[]{"DXVAMode"})
    {
      /**
       * Sets the preferred DXVA MPEG Mode configuration setting (Windows only). This will be A, B, C or D.
       * This is used when playing back MPEG2 files.
       * @param DXVAMode the preferred DXVA MPEG Mode configuration setting, use the empty string or "Default" to disable this
       *
       * @declaration public void SetDXVAMpegMode(String DXVAMode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowTVPlayer.setDxvaMpegMode(DShowTVPlayer.getDXVACode(getString(stack))); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVDDXVAMpegMode")
    {
      /**
       * Gets the preferred DXVA MPEG Mode configuration setting (Windows only). This will be A, B, C or D (or Default if not set)
       * This is used when playing back DVDs.
       * @return the preferred DXVA MPEG Mode configuration setting
       *
       * @declaration public String GetDVDDXVAMpegMode();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.getDXVAName(DShowDVDPlayer.getDVDDxvaMpegMode());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDVDDXVAMpegMode", 1, new String[]{"DXVAMode"})
    {
      /**
       * Sets the preferred DXVA MPEG Mode configuration setting (Windows only). This will be A, B, C or D.
       * This is used when playing back DVDs.
       * @param DXVAMode the preferred DXVA MPEG Mode configuration setting, use the empty string or "Default" to disable this
       *
       * @declaration public void SetDVDDXVAMpegMode(String DXVAMode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowDVDPlayer.setDVDDxvaMpegMode(DShowTVPlayer.getDXVACode(getString(stack))); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDXVAMpegModes")
    {
      /**
       * Gets the list of available DXVA MPEG Modes. This will return {"A", "B", "C", "D"}
       * @return the list of available DXVA MPEG Modes: {"A", "B", "C", "D"}
       *
       * @declaration public String[] GetDXVAMpegModes();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.DXVA_MPEG_MODE_NAMES.clone();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDXVADeinterlaceMode")
    {
      /**
       * Gets the preferred DXVA Deinterlacing configuration setting (Windows only).
       * This is used when playing back MPEG2 files.
       * @return the preferred DXVA Deinterlacing configuration setting
       *
       * @declaration public String GetDXVADeinterlaceMode();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.getDeinterlaceName(DShowTVPlayer.getForceDeinterlace());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDXVADeinterlaceMode", 1, new String[]{"DeinterlaceMode"})
    {
      /**
       * Sets the preferred DXVA Deinterlacing configuration setting (Windows only).
       * This is used when playing back MPEG2 files.
       * @param DeinterlaceMode the preferred DXVA Deinterlacing configuration setting
       *
       * @declaration public void SetDXVADeinterlaceMode(String DeinterlaceMode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowTVPlayer.setForceDeinterlace(DShowTVPlayer.getDeinterlaceCode(getString(stack))); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVDDXVADeinterlaceMode")
    {
      /**
       * Gets the preferred DXVA Deinterlacing configuration setting (Windows only).
       * This is used when playing back DVDs.
       * @return the preferred DXVA Deinterlacing configuration setting
       *
       * @declaration public String GetDVDDXVADeinterlaceMode();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.getDeinterlaceName(DShowDVDPlayer.getDVDForceDeinterlace());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDVDDXVADeinterlaceMode", 1, new String[]{"DeinterlaceMode"})
    {
      /**
       * Sets the preferred DXVA Deinterlacing configuration setting (Windows only).
       * This is used when playing back DVDs.
       * @param DeinterlaceMode the preferred DXVA Deinterlacing configuration setting
       *
       * @declaration public void SetDVDDXVADeinterlaceMode(String DeinterlaceMode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowDVDPlayer.setDVDForceDeinterlace(DShowTVPlayer.getDeinterlaceCode(getString(stack))); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDXVADeinterlaceModes")
    {
      /**
       * Gets the list of potential DXVA Deinterlacing Capabilities (Widows only)
       * @return the list of potential DXVA Deinterlacing Capabilities
       *
       * @declaration public String[] GetDXVADeinterlaceModes();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.DEINTERLACE_NAMES.clone();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDscalerMode")
    {
      /**
       * Gets the mode that DScaler is currently configured to run in when it is used (Windows only)
       * @return the mode that DScaler is currently configured to run in when it is used
       *
       * @declaration public String GetDscalerMode();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.getDscalerName(DShowTVPlayer.getDscalerMode());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDscalerMode", 1, new String[]{"DscalerMode"})
    {
      /**
       * Sets the mode that DScaler should be configured to run in when it is used (Windows only)
       * @param DscalerMode the mode that DScaler should be configured to run in when it is used
       *
       * @declaration public void SetDscalerMode(String DscalerMode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowTVPlayer.setDscalerMode(DShowTVPlayer.getDscalerCode(getString(stack))); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDscalerModes")
    {
      /**
       * Gets the list of the available modes that DScaler can be run in
       * @return the list of the available modes that DScaler can be run in
       *
       * @declaration public String[] GetDscalerModes();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.DSCALER_MODE_NAMES;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAudioDecoderFilter")
    {
      /**
       * Gets the name of the DirectShow audio decoder filter that's used for MPEG2 playback (Windows only)
       * @return the name of the DirectShow audio decoder filter that's used for MPEG2 playback
       *
       * @declaration public String GetAudioDecoderFilter();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.getAudioDecoderFilter();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetAudioDecoderFilter", 1, new String[]{"FilterName"})
    {
      /**
       * Sets the name of the DirectShow audio decoder filter that's used for MPEG2 playback (Windows only)
       * @param FilterName the name of the DirectShow audio decoder filter to use for MPEG2 playback
       *
       * @declaration public void SetAudioDecoderFilter(String FilterName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowTVPlayer.setAudioDecoderFilter(getString(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVDAudioDecoderFilter")
    {
      /**
       * Gets the name of the DirectShow audio decoder filter that's used for DVD playback (Windows only)
       * @return the name of the DirectShow audio decoder filter that's used for DVD playback
       *
       * @declaration public String GetDVDAudioDecoderFilter();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowDVDPlayer.getDVDAudioDecoderFilter();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDVDAudioDecoderFilter", 1, new String[]{"FilterName"})
    {
      /**
       * Sets the name of the DirectShow audio decoder filter that's used for DVD playback (Windows only)
       * @param FilterName the name of the DirectShow audio decoder filter to use for DVD playback
       *
       * @declaration public void SetDVDAudioDecoderFilter(String FilterName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowDVDPlayer.setDVDAudioDecoderFilter(getString(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAudioRenderFilter")
    {
      /**
       * Gets the name of the DirectShow audio renderer filter that's used for all playback except DVDs (Windows only)
       * @return the name of the DirectShow audio renderer filter that's used for playback except DVDs
       *
       * @declaration public String GetAudioRenderFilter();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowMediaPlayer.getAudioRenderFilter();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetAudioRenderFilter", 1, new String[]{"FilterName"})
    {
      /**
       * Sets the name of the DirectShow audio renderer filter that's used for playback except DVDs (Windows only)
       * @param FilterName the name of the DirectShow audio renderer filter to use for playback except DVDs
       *
       * @declaration public void SetAudioRenderFilter(String FilterName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowMediaPlayer.setAudioRenderFilter(getString(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVDAudioRenderFilter")
    {
      /**
       * Gets the name of the DirectShow audio renderer filter that's used for DVD playback (Windows only)
       * @return the name of the DirectShow audio renderer filter that's used for DVD playback
       *
       * @declaration public String GetDVDAudioRenderFilter();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowDVDPlayer.getDVDAudioRenderFilter();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDVDAudioRenderFilter", 1, new String[]{"FilterName"})
    {
      /**
       * Sets the name of the DirectShow audio renderer filter that's used for DVD playback (Windows only)
       * @param FilterName the name of the DirectShow audio renderer filter to use for DVD playback
       *
       * @declaration public void SetDVDAudioRenderFilter(String FilterName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowDVDPlayer.setDVDAudioRenderFilter(getString(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVDVideoRenderFilter")
    {
      /**
       * Gets the video renderer filter that should be used for DVD playback. Possible values are "VMR9",
       * "Overlay" and the localized "Default" string
       * @return the video renderer filter that should be used for DVD playback
       *
       * @declaration public String GetDVDVideoRenderFilter();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowDVDPlayer.getDVDVideoRenderFilter();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDVDVideoRenderFilter", 1, new String[]{"FilterName"})
    {
      /**
       * Sets the video renderer filter that should be used for DVD playback. Possible values are "VMR9",
       * "Overlay" and the localized "Default" string
       * @param FilterName the video renderer filter that should be used for DVD playback
       *
       * @declaration public void SetDVDVideoRenderFilter(String FilterName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowDVDPlayer.setDVDVideoRenderFilter(getString(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetVideoDecoderFilter")
    {
      /**
       * Gets the name of the DirectShow video decoder filter that's used for MPEG2 playback (Windows only)
       * @return the name of the DirectShow video decoder filter that's used for MPEG2 playback
       *
       * @declaration public String GetVideoDecoderFilter();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.getVideoDecoderFilter();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetVideoDecoderFilter", 1, new String[]{"FilterName"})
    {
      /**
       * Sets the name of the DirectShow video decoder filter that's used for MPEG2 playback (Windows only)
       * @param FilterName the name of the DirectShow video decoder filter to use for MPEG2 playback
       *
       * @declaration public void SetVideoDecoderFilter(String FilterName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowTVPlayer.setVideoDecoderFilter(getString(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDVDVideoDecoderFilter")
    {
      /**
       * Gets the name of the DirectShow video decoder filter that's used for DVD playback (Windows only)
       * @return the name of the DirectShow video decoder filter that's used for DVD playback
       *
       * @declaration public String GetDVDVideoDecoderFilter();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowDVDPlayer.getDVDVideoDecoderFilter();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDVDVideoDecoderFilter", 1, new String[]{"FilterName"})
    {
      /**
       * Sets the name of the DirectShow video decoder filter that's used for DVD playback (Windows only)
       * @param FilterName the name of the DirectShow video decoder filter to use for DVD playback
       *
       * @declaration public void SetDVDVideoDecoderFilter(String FilterName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowDVDPlayer.setDVDVideoDecoderFilter(getString(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAudioDelay")
    {
      /**
       * Gets the audio delay in milliseconds to apply when playing back MPEG2 files (Windows only)
       * @return the audio delay in milliseconds to apply when playing back MPEG2 files
       *
       * @declaration public int GetAudioDelay();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(!Sage.WINDOWS_OS ? 0 : Sage.readDwordValue(Sage.HKEY_LOCAL_MACHINE,
            "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDeMux", "AudioDelay"));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetAudioDelay", 1, new String[]{"Delay"})
    {
      /**
       * Sets the audio delay in milliseconds to apply when playing back MPEG2 files (Windows only)
       * @param Delay the audio delay in milliseconds to apply when playing back MPEG2 files
       *
       * @declaration public void SetAudioDelay(int Delay);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int x = getInt(stack);
        if (Sage.WINDOWS_OS)
          Sage.writeDwordValue(Sage.HKEY_LOCAL_MACHINE,
              "SOFTWARE\\Frey Technologies\\Common\\DSFilters\\MpegDeMux", "AudioDelay", x);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAudioDecoderFilters")
    {
      /**
       * Gets the list of the audio decoder filters that are available in the system for MPEG2 playback (Windows only)
       * @return the list of the audio decoder filters that are available in the system for MPEG2 playback
       *
       * @declaration public String[] GetAudioDecoderFilters();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] audioFilters = DShowMediaPlayer.getAudioDecoderFilters();
        java.util.ArrayList rv = new java.util.ArrayList();
        rv.add(Sage.rez("Default"));
        rv.addAll(java.util.Arrays.asList(audioFilters));
			return rv.toArray(Pooler.EMPTY_STRING_ARRAY);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetVideoDecoderFilters")
    {
      /**
       * Gets the list of the video decoder filters that are available in the system for MPEG2 playback (Windows only)
       * @return the list of the video decoder filters that are available in the system for MPEG2 playback
       *
       * @declaration public String[] GetVideoDecoderFilters();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] videoFilters = DShowMediaPlayer.getVideoDecoderFilters();
        java.util.ArrayList rv = new java.util.ArrayList();
        rv.add(Sage.rez("Default"));
        rv.addAll(java.util.Arrays.asList(videoFilters));
			return rv.toArray(Pooler.EMPTY_STRING_ARRAY);
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsSoftwareDecodingDisabled")
    {
      /**
       * Returns true if software decoding is disabled. This only applies to the TV output of the Hauppauge WinTV PVR-350 PCI card on Windows
       * @return true if software decoding is disabled
       *
       * @declaration public boolean IsSoftwareDecodingDisabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(DShowTVPlayer.getDisableSWDecoding());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetSoftwareDecodingDisabled", 1, new String[]{"Disabled"})
    {
      /**
       * Sets if software decoding is disabled. This only applies to the TV output of the Hauppauge WinTV PVR-350 PCI card on Windows
       * @param Disabled true if software decoding is disabled
       *
       * @declaration public void SetSoftwareDecodingDisabled(boolean Disabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowTVPlayer.setDisableSWDecoding(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsDscalerDoubleRefresh")
    {
      /**
       * Returns true if DScaler is set to double the refresh rate when used. This means it'll run at 60fps instead of 30fps.
       * @return true if DScaler is set to double the refresh rate when used.
       *
       * @declaration public boolean IsDscalerDoubleRefresh();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(DShowTVPlayer.getDscalerDoubleRefresh());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDscalerDoubleRefresh", 1, new String[]{"Enabled"})
    {
      /**
       * Sets whether or not DScaler should double the refresh rate. This means it'll run at 60fps instead of 30fps.
       * @param Enabled true if DScaler should double the refresh rate when used, false otherwise
       *
       * @declaration public void SetDscalerDoubleRefresh(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowTVPlayer.setDscalerDoubleRefresh(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsDscalerOddFieldFirst")
    {
      /**
       * Returns true if DScaler is configured to display the odd field first (fields are even or odd)
       * @return true if DScaler is configured to display the odd field first
       *
       * @declaration public boolean IsDscalerOddFieldFirst();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(DShowTVPlayer.getDscalerOddFieldFirst());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDscalerOddFieldFirst", 1, new String[]{"Enabled"})
    {
      /**
       * Sets if DScaler should display the odd field first (fields are even or odd)
       * @param Enabled true if DScaler should display the odd field first
       *
       * @declaration public void SetDscalerOddFieldFirst(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowTVPlayer.setDscalerOddFieldFirst(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "HasPVR350TVOutput")
    {
      /**
       * Returns true if the system has a Hauppauge WinTV PVR-350 card installed which can produce a TV output (Windows only)
       * @return true if the system has a Hauppauge WinTV PVR-350 card installed which can produce a TV output
       *
       * @declaration public boolean HasPVR350TVOutput();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(DShowTVPlayer.hasPVR350HWDecoder());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsPVR350TVOutputEnabled")
    {
      /**
       * Returns true if the TV output on a Hauppauge WinTV PVR-350 card is enabled (Windows only)
       * @return true if the TV output on a Hauppauge WinTV PVR-350 card is enabled
       *
       * @declaration public boolean IsPVR350TVOutputEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(DShowTVPlayer.getEnableHWDecoder());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetPVR350TVOutputEnabled", 1, new String[]{"Enabled"})
    {
      /**
       * Sets whether or not the TV output on a Hauppauge WinTV PVR-350 should be enabled (Windows only)
       * @param Enabled true if the TV output of a Hauppauge WinTV PVR-350 should be enabled
       *
       * @declaration public void SetPVR350TVOutputEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowTVPlayer.setEnableHWDecoder(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsPVR350OSDEnabled")
    {
      /**
       * Returns true if the user interface (OSD) display on a Hauppauge WinTV PVR-350 should be shown through the tv output (Windows only)
       * @return true if the user interface (OSD) display on a Hauppauge WinTV PVR-350 should be shown through the tv output
       *
       * @declaration public boolean IsPVR350OSDEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(Java2DSageRenderer.hasOSDRenderer(stack.getUIMgrSafe()));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetPVR350OSDEnabled", 1, new String[]{"Enabled"})
    {
      /**
       * Sets whether or not the user interface (OSD) should be displayed on the TV output of a Hauppauge WinTV PVR-350 (Windows only)
       * @param Enabled true if the user interface (OSD) should be displayed on the TV output of a Hauppauge WinTV PVR-350, false otherwise
       *
       * @declaration public void SetPVR350OSDEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean x = evalBool(stack.pop()) && Sage.WINDOWS_OS;
        // We can't use accelerated drawing with the 350 OSD, so disable that now
        stack.getUIMgr().put(Java2DSageRenderer.OSD_RENDERING_PLUGIN_CLASS, x ? "sage.PVR350OSDRenderingPlugin" : "");
        if (x)
        {
          Sage.writeDwordValue(Sage.HKEY_LOCAL_MACHINE,
              "SYSTEM\\CurrentControlSet\\Services\\Globespan\\Parameters\\ivac15\\Driver",
              "HcwTVOutColorBars", 0);
          stack.getUIMgr().getRootPanel().setAcceleratedDrawing(false, false);
        }
        else if (!stack.getUIMgr().getRootPanel().isAcceleratedDrawing() &&
            stack.getUIMgr().getBoolean("ui/accelerated_rendering", true))
        {
          stack.getUIMgr().getRootPanel().setAcceleratedDrawing(true, false);
        }
        else
        {
          stack.getUIMgr().getRootPanel().reprepRenderer();
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsDscalerEnabled")
    {
      /**
       * Returns true if the DScaler deinterlacing filter is enabled (Windows only)
       * @return true if the DScaler deinterlacing filter is enabled
       * @declaration public boolean IsDscalerEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(DShowTVPlayer.getUseDscaler());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDscalerEnabled", 1, new String[]{"Enabled"})
    {
      /**
       * Sets whether or not the DScaler deinterlacing filter should be used (Windows only)
       * @param Enabled true if the DScaler deinterlacing filter should be used, false otherwise
       *
       * @declaration public void SetDscalerEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowTVPlayer.setUseDscaler(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDscalerPlugin")
    {
      /**
       * Gets the plugin that DScaler is currently configured to use when it is in "Plugin" mode.
       * @return the plugin that DScaler is currently configured to use when it is in "Plugin" mode
       *
       * @declaration public String GetDscalerPlugin();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.getDscalerPlugin();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetDscalerPlugin", 1, new String[] {"PluginName"})
    {
      /**
       * Sets the plugin that should be used by DScaler. DScaler must also be set into "Plugin" mode.
       * @param PluginName the name of the plugin to use with DScaler
       *
       * @declaration public void SetDscalerPlugin(String PluginName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowTVPlayer.setDscalerPlugin(getString(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetDscalerPlugins")
    {
      /**
       * Returns the list of plugins that are supported by DScaler. These are used when DScaler is in "Plugin" mode. (Windows only)
       * @return the list of plugins that are supported by DScaler
       *
       * @declaration public String[] GetDscalerPlugins();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return DShowTVPlayer.getDscalerPlugins();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "HasDirectX9")
    {
      /**
       * Returns true if the system has DirectX 9 installed (Windows Only)
       * @return true if the system has DirectX 9 installed, false otherwise
       *
       * @declaration public boolean HasDirectX9();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(DirectX9SageRenderer.hasDirectX9());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "HasEVRSupport")
    {
      /**
       * Returns true if the system has support for the Enhanced Video Renderer (EVR) (Windows Only)
       * @return true if the system has support for the Enhanced Video Renderer (EVR), false otherwise
       *
       * @declaration public boolean HasEVRSupport();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(DShowMediaPlayer.isEVRSupported());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsVideoRendererOverlay")
    {
      /**
       * Returns true if the video renderer is configured to use the Overlay surface (Windows Only) Applies to all files except DVDs.
       * @return true if the video renderer is configured to use the Overlay surface, false otherwise
       *
       * @declaration public boolean IsVideoRendererOverlay();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(DShowMediaPlayer.getUseOverlay());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsVideoRendererVMR9")
    {
      /**
       * Returns true if the video renderer is configured to use the Video Mixing Renderer 9 (Windows Only). This
       * will only work if accelerated rendering is enabled (3D acceleration). If it's not then Overlay will be used
       * as the video renderer instead of VMR9. Applies to all files except DVDs.
       * @return true if the video renderer is configured to use VMR9, false otherwise
       *
       * @declaration public boolean IsVideoRendererVMR9();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(DShowMediaPlayer.getUseVmr());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsVideoRendererEVR")
    {
      /**
       * Returns true if the video renderer is configured to use the Enhanced Video Renderer (Windows Only). This
       * will only work if accelerated rendering is enabled (3D acceleration). If it's not then Overlay will be used
       * as the video renderer instead of EVR. Applies to all files except DVDs.
       * @return true if the video renderer is configured to use EVR, false otherwise
       * @since 6.5
       *
       * @declaration public boolean IsVideoRendererEVR();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(DShowMediaPlayer.getUseEvr());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsVideoRendererDefault")
    {
      /**
       * Returns true if the video renderer is configured to use the Default renderer (Windows Only). This will usually be the Video Mixing
       * Renderer 7 if using Windows XP; but DirectShow ultimately decides this itself. Applies to all files except DVDs.
       * @return true if the video renderer is configured to use the Default renderer
       *
       * @declaration public boolean IsVideoRendererDefault();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(!DShowMediaPlayer.getUseOverlay() && !DShowMediaPlayer.getUseVmr() && !DShowMediaPlayer.getUseEvr());
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetVideoRendererToDefault")
    {
      /**
       * Sets the video renderer to use the default renderer (Windows Only). This will usually be the Video Mixing
       * Renderer 7 if using Windows XP; but DirectShow ultimately decides this itself. Applies to all files except DVDs.
       *
       * @declaration public void SetVideoRendererToDefault();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowMediaPlayer.setUseDefaultVideoRenderer(); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetVideoRendererToOverlay")
    {
      /**
       * Sets the video renderer to use the video Overlay surface (Windows Only). Applies to all files except DVDs.
       *
       * @declaration public void SetVideoRendererToOverlay();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowMediaPlayer.setUseOverlay(); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetVideoRendererToVMR9")
    {
      /**
       * Sets the video renderer to use the Video Mixing Renderer 9 (Windows Only). This
       * will only work if accelerated rendering is enabled (3D acceleration). If it's not then Overlay will be used
       * as the video renderer instead of VMR9. Applies to all files except DVDs.
       *
       * @declaration public void SetVideoRendererToVMR9();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowMediaPlayer.setUseVmr(); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetVideoRendererToEVR")
    {
      /**
       * Sets the video renderer to use the Enhanced Video Renderer (Windows Only). This
       * will only work if accelerated rendering is enabled (3D acceleration). If it's not then Overlay will be used
       * as the video renderer instead of EVR. Applies to all files except DVDs.
       * @since 6.5
       *
       * @declaration public void SetVideoRendererToEVR();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        DShowMediaPlayer.setUseEvr(); return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetAudioRendererFilters")
    {
      /**
       * Gets the list of DirectShow audio renderer filters that are available on the system (Windows Only).
       * @return the list of DirectShow audio renderer filters that are available on the system
       *
       * @declaration public java.util.Vector GetAudioRendererFilters();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.util.Vector rv = new java.util.Vector();
        rv.add(Sage.rez("Default"));
        if (Sage.WINDOWS_OS)
          rv.addAll(java.util.Arrays.asList(
              DShowCaptureDevice.getDevicesInCategory0(DShowCaptureManager.AUDIO_RENDERER_CATEGORY_GUID)));
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetMixerAudioInputPaths")
    {
      /**
       * Gets the different input connectors that are available on the system sound mixer (Windows Only)
       * @return the different input connectors that are available on the system sound mixer
       *
       * @declaration public String[] GetMixerAudioInputPaths();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (!Sage.WINDOWS_OS) return null;
        return DShowCaptureDevice.getAudioInputPaths0();
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetAllowPlaceshifterLoginCaching", 1, new String[]{"Enabled"}, true)
    {
      /**
       * Sets whether or not placeshifter connections should be allowed to cache their login information. Disabling
       * this setting will clear the current authentication cache on the server.
       * @param Enabled true if the server should allow caching of placeshifter logins; false otherwise (false also clears the cache)
       * @since 6.6
       *
       * @declaration public void SetAllowPlaceshifterLoginCaching(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (evalBool(stack.pop()))
        {
          Sage.putBoolean("miniclient/enable_cached_authentication", true);
        }
        else
        {
          Sage.putBoolean("miniclient/enable_cached_authentication", false);
          Sage.removeNode("miniclient/cached_authentication");
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "IsPlaceshifterLoginCachingAllowed", true)
    {
      /**
       * Returns true if this server allows caching of placeshifter logins, false otherwise
       * @return true if this server allows caching of placeshifter logins, false otherwise
       * @since 6.6
       *
       * @declaration public boolean IsPlaceshifterLoginCachingAllowed();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(Sage.getBoolean("miniclient/enable_cached_authentication", true));
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "SetCurrentlyAiringProgramsStartLive", 1, new String[]{"Enabled"}, true)
    {
      /**
       * Sets whether or not when playing back a program that is currently airing whether we should start from live TV or not
       * @param Enabled true if we should start from live for anything currently airing
       * @since 8.1
       *
       * @declaration public void SetCurrentlyAiringProgramsStartLive(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Sage.putBoolean("videoframe/force_live_playback_on_currently_airing_programs", evalBool(stack.pop()));
        NetworkClient.distributePropertyChange("videoframe/force_live_playback_on_currently_airing_programs");
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Configuration", "GetCurrentlyAiringProgramsStartLive")
    {
      /**
       * Returns true if the system is configured to start any program that is currently airing
       * from live, false otherwise
       * @return true if the system is configured to start any program that is currently airing from live, false otherwise
       * @since 8.1
       *
       * @declaration public boolean GetCurrentlyAiringProgramsStartLive();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(Sage.getBoolean("videoframe/force_live_playback_on_currently_airing_programs", false));
      }});
    /*
		rft.put(new PredefinedJEPFunction("Configuration", "", 0)
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			}});
     */
  }
}
