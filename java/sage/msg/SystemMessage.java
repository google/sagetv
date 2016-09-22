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
package sage.msg;

import sage.epg.sd.SDRipper;

/**
 *
 * @author Narflex
 */
public class SystemMessage extends SageMsg
{
  private static final long CONSOLIDATION_WINDOW = sage.Sage.MILLIS_PER_DAY;
  // Message priorities
  public static final int ERROR_PRIORITY = 3;
  public static final int WARNING_PRIORITY = 2;
  public static final int INFO_PRIORITY = 1;
  // System messages types
  // EPG related
  public static final int NEW_CHANNEL_ON_LINEUP_MSG = 1001;
  public static final int LINEUP_LOST_FROM_SERVER_MSG = 1002;
  public static final int CHANNEL_SCAN_NEEDED_MSG = 1003;
  public static final int EPG_UPDATE_FAILURE_MSG = 1004;
  public static final int EPG_LINKAGE_FOR_MR_CHANGED_MSG = 1005;
  public static final int EPG_SERVER_NO_KEY_MSG = 1006;
  public static final int EPG_SERVER_INVALID_KEY_MSG = 1007;
  public static final int LINEUP_MISSING_FROM_SD_ACCOUNT_MSG = 1008;
  public static final int LINEUP_SD_ACCOUNT_AUTH_FAILED_MSG = 1009;
  public static final int LINEUP_SD_ACCOUNT_DISABLED_MSG = 1010;
  public static final int LINEUP_SD_ACCOUNT_EXPIRED_MSG = 1011;
  public static final int LINEUP_SD_ACCOUNT_LOCKOUT_MSG = 1012;

  // Scheduler related
  public static final int MISSED_RECORDING_FROM_CONFLICT_MSG = 1050;
  public static final int CAPTURE_DEVICE_LOAD_ERROR_MSG = 1051;
  public static final int PARTIAL_RECORDING_FROM_CONFLICT_MSG = 1052;
  // Seeker related
  public static final int ENCODER_HALT_MSG = 1100;
  public static final int CAPTURE_DEVICE_RECORD_ERROR_MSG = 1101;
  public static final int MISSED_RECORDING_FROM_CAPTURE_FAILURE_MSG = 1102;
  public static final int DISKSPACE_INADEQUATE_MSG = 1103;
  public static final int CAPTURE_DEVICE_DATASCAN_ERROR_MSG = 1104;
  public static final int VIDEO_DIRECTORY_OFFLINE_MSG = 1105;
  public static final int PLAYLIST_MISSING_SEGMENT = 1106;
  public static final int RECORDING_BITRATE_TOO_LOW_ERROR_MSG = 1107;
  // General
  public static final int SYSTEM_LOCKUP_DETECTION_MSG = 1200;
  public static final int OUT_OF_MEMORY_MSG = 1201;
  public static final int SOFTWARE_UPDATE_AVAILABLE_MSG = 1202;
  public static final int STORAGE_MONITOR_MSG = 1203;
  public static final int GENERAL_MSG = 1204;
  public static final int PLUGIN_INSTALL_MISSING_FILE_MSG = 1205;

  public static String getNameForMsgType(int msgType)
  {
    switch (msgType)
    {
      case NEW_CHANNEL_ON_LINEUP_MSG:
        return sage.Sage.rez("NEW_CHANNEL_ON_LINEUP");
      case LINEUP_LOST_FROM_SERVER_MSG:
        return sage.Sage.rez("LINEUP_LOST_FROM_SERVER");
      case CHANNEL_SCAN_NEEDED_MSG:
        return sage.Sage.rez("CHANNEL_SCAN_NEEDED");
      case EPG_UPDATE_FAILURE_MSG:
        return sage.Sage.rez("EPG_UPDATE_FAILURE");
      case EPG_LINKAGE_FOR_MR_CHANGED_MSG:
        return sage.Sage.rez("EPG_LINKAGE_FOR_MR_CHANGED");
      case EPG_SERVER_NO_KEY_MSG:
        return sage.Sage.rez("EPG_SERVER_NO_KEY");
      case EPG_SERVER_INVALID_KEY_MSG:
        return sage.Sage.rez("EPG_SERVER_INVALID_KEY");
      case LINEUP_MISSING_FROM_SD_ACCOUNT_MSG:
        return sage.Sage.rez("LINEUP_MISSING_FROM_SD_ACCOUNT");
      case LINEUP_SD_ACCOUNT_AUTH_FAILED_MSG:
        return sage.Sage.rez("LINEUP_SD_ACCOUNT_AUTH_FAILED");
      case LINEUP_SD_ACCOUNT_DISABLED_MSG:
        return sage.Sage.rez("LINEUP_SD_ACCOUNT_DISABLED");
      case LINEUP_SD_ACCOUNT_EXPIRED_MSG:
        return sage.Sage.rez("LINEUP_SD_ACCOUNT_EXPIRED");
      case LINEUP_SD_ACCOUNT_LOCKOUT_MSG:
        return sage.Sage.rez("LINEUP_SD_ACCOUNT_LOCKOUT");
      case MISSED_RECORDING_FROM_CONFLICT_MSG:
        return sage.Sage.rez("MISSED_RECORDING_FROM_CONFLICT");
      case CAPTURE_DEVICE_LOAD_ERROR_MSG:
        return sage.Sage.rez("CAPTURE_DEVICE_LOAD_ERROR");
      case ENCODER_HALT_MSG:
        return sage.Sage.rez("ENCODER_HALT");
      case CAPTURE_DEVICE_RECORD_ERROR_MSG:
        return sage.Sage.rez("CAPTURE_DEVICE_RECORD_ERROR");
      case MISSED_RECORDING_FROM_CAPTURE_FAILURE_MSG:
        return sage.Sage.rez("MISSED_RECORDING_FROM_CAPTURE_FAILURE");
      case DISKSPACE_INADEQUATE_MSG:
        return sage.Sage.rez("DISKSPACE_INADEQUATE");
      case SYSTEM_LOCKUP_DETECTION_MSG:
        return sage.Sage.rez("SYSTEM_LOCKUP_DETECTION");
      case OUT_OF_MEMORY_MSG:
        return sage.Sage.rez("OUT_OF_MEMORY");
      case RECORDING_BITRATE_TOO_LOW_ERROR_MSG:
        return sage.Sage.rez("RECORDING_BITRATE_TOO_LOW_ERROR");
      case SOFTWARE_UPDATE_AVAILABLE_MSG:
        return sage.Sage.rez("SOFTWARE_UPDATE_AVAILABLE");
      case STORAGE_MONITOR_MSG:
        return sage.Sage.rez("STORAGE_MONITOR");
      case CAPTURE_DEVICE_DATASCAN_ERROR_MSG:
        return sage.Sage.rez("CAPTURE_DEVICE_DATASCAN_ERROR");
      case GENERAL_MSG:
        return sage.Sage.rez("GENERAL_MSG");
      case PARTIAL_RECORDING_FROM_CONFLICT_MSG:
        return sage.Sage.rez("PARTIAL_RECORDING_FROM_CONFLICT");
      case VIDEO_DIRECTORY_OFFLINE_MSG:
        return sage.Sage.rez("VIDEO_DIRECTORY_OFFLINE");
      case PLAYLIST_MISSING_SEGMENT:
        return sage.Sage.rez("PLAYLIST_IMPORT_MISSING_SEGMENT");
      case PLUGIN_INSTALL_MISSING_FILE_MSG:
        return sage.Sage.rez("PLUGIN_INSTALL_MISSING_FILE");
    }
    return "";
  }

  public static SystemMessage createNewChannelMsg(sage.EPGDataSource lineup, sage.Channel chan, String newLogicalChans)
  {
    java.util.Properties props = new java.util.Properties();
    String lineupName = "";
    String chanName = "";
    String chanDesc = "";
    if (lineup != null)
      lineupName = lineup.getName();
    props.setProperty("Lineup", lineupName);
    if (chan != null)
    {
      chanName = chan.getName();
      chanDesc = chan.getLongName();
    }
    props.setProperty("ChannelName", chanName);
    props.setProperty("LogicalChannels", newLogicalChans);
    props.setProperty("ChannelDesc", chanDesc);
    return new SystemMessage(NEW_CHANNEL_ON_LINEUP_MSG, INFO_PRIORITY,
        sage.Sage.rez("NEW_CHANNEL_ON_LINEUP_MSG", new Object[] { chanName, chanDesc, newLogicalChans, lineupName }), props);
  }

  public static SystemMessage createLineupLostMsg(sage.EPGDataSource lineup)
  {
    java.util.Properties props = new java.util.Properties();
    String lineupName = "";
    if (lineup != null)
      lineupName = lineup.getName();
    props.setProperty("Lineup", lineupName);
    return new SystemMessage(LINEUP_LOST_FROM_SERVER_MSG, WARNING_PRIORITY,
        sage.Sage.rez("LINEUP_LOST_FROM_SERVER_MSG", new Object[] { lineupName }), props);
  }

  public static SystemMessage createSDLineupMissingMsg(sage.EPGDataSource lineup)
  {
    java.util.Properties props = new java.util.Properties();
    String lineupName = "";
    if (lineup != null)
    {
      lineupName = lineup.getName();
      // Remove the lineup SD specific appended label because here we are talking about the lineup
      // within the Schedules Direct account, not the name SageTV uses to describe it.
      if (lineupName.endsWith(SDRipper.SOURCE_LABEL))
        lineupName = lineupName.substring(0, lineupName.length() - SDRipper.SOURCE_LABEL.length());
    }
    props.setProperty("Lineup", lineupName);
    return new SystemMessage(LINEUP_MISSING_FROM_SD_ACCOUNT_MSG, ERROR_PRIORITY,
        sage.Sage.rez("LINEUP_MISSING_FROM_SD_ACCOUNT_MSG", new Object[] { lineupName }), props);
  }

  public static SystemMessage createPlaylistMissingSegmentMsg(String playlistPath, String segmentPath)
  {
    java.util.Properties props = new java.util.Properties();
    props.setProperty("PlaylistPath", playlistPath);
    props.setProperty("SegmentPath", segmentPath);
    return new SystemMessage(PLAYLIST_MISSING_SEGMENT, INFO_PRIORITY,
        sage.Sage.rez("PLAYLIST_IMPORT_MISSING_SEGMENT_MSG", new Object[] { playlistPath, segmentPath }), props);
  }

  public static SystemMessage createPluginUpdateMsg(String pluginID, String pluginName, String version)
  {
    java.util.Properties props = new java.util.Properties();
    props.setProperty("PluginID", pluginID);
    props.setProperty("PluginName", pluginName);
    props.setProperty("Version", version);
    props.setProperty("IsPluginUpdate", "true");
    return new SystemMessage(SOFTWARE_UPDATE_AVAILABLE_MSG, INFO_PRIORITY,
        sage.Sage.rez("PLUGIN_UPDATE_AVAILABLE_MSG", new Object[] { pluginName, version }), props);
  }

  public static SystemMessage createPluginMissingFileMsg(String pluginName, String filename)
  {
    java.util.Properties props = new java.util.Properties();
    props.setProperty("PluginName", pluginName);
    props.setProperty("Filename", filename);
    return new SystemMessage(PLUGIN_INSTALL_MISSING_FILE_MSG, WARNING_PRIORITY,
        sage.Sage.rez("PLUGIN_INSTALL_MISSING_FILE_MSG", new Object[] { pluginName, filename }), props);
  }

  public static SystemMessage createOfflineVideoDirectoryMessage(java.io.File videoDir, boolean seriousError)
  {
    java.util.Properties props = new java.util.Properties();
    String dirPath = "";
    if (videoDir != null)
      dirPath = videoDir.toString();
    props.setProperty("Directory", dirPath);
    return new SystemMessage(VIDEO_DIRECTORY_OFFLINE_MSG, seriousError ? ERROR_PRIORITY : WARNING_PRIORITY,
        sage.Sage.rez("VIDEO_DIRECTORY_OFFLINE_MSG", new Object[] { dirPath }), props);
  }

  public static SystemMessage createEncoderHaltMsg(sage.CaptureDevice capDev, sage.CaptureDeviceInput cdi, sage.Airing currAir, sage.Channel chan,
      String physicalChannel, int haltCount)
  {
    java.util.Properties props = new java.util.Properties();
    String cdiName = "";
    String capDevName = "";
    String chanName = "";
    String tit = "";
    int airID = 0;
    if (capDev != null)
      capDevName = capDev.getName();
    if (cdi != null)
      cdiName = cdi.toString();
    if (chan != null)
      chanName = chan.getName();
    if (currAir != null)
    {
      tit = currAir.getTitle();
      airID = currAir.getID();
    }
    props.setProperty("CaptureDevice", capDevName);
    props.setProperty("CaptureDeviceInput", cdiName);
    props.setProperty("ChannelName", chanName);
    props.setProperty("PhysicalChannel", physicalChannel);
    props.setProperty("Title", tit);
    props.setProperty("AiringID", Integer.toString(airID));
    // Narflex 1/14/16 - I changed this so that we get the number of consecutive halts in a recording rather than
    // whether it was just the first or not. This will allow customizing how many halts mean an error state; and then I also
    // made another change for the code that disables messages from raising the global alerts level so it can be done based
    // on the priority of the message as well. The reason was because sometimes my HDPVR gets in a bad state and will have hundreds
    // of halts in a recording...but having a couple of them is normal; but when it's having hundreds...I wanted to know about it
    // so I could restart the HDPVR to fix the problem. :)
    return new SystemMessage(ENCODER_HALT_MSG, (haltCount >= sage.Sage.getInt("msg/halt_count_for_error", 2)) ? ERROR_PRIORITY : WARNING_PRIORITY,
        sage.Sage.rez("ENCODER_HALT_MSG", new Object[] { cdiName, tit, chanName, physicalChannel }), props);
  }

  public static SystemMessage createBitrateTooLowMsg(sage.CaptureDevice capDev, sage.CaptureDeviceInput cdi, sage.Airing currAir, sage.Channel chan,
      String physicalChannel)
  {
    java.util.Properties props = new java.util.Properties();
    String cdiName = "";
    String capDevName = "";
    String chanName = "";
    String tit = "";
    int airID = 0;
    if (capDev != null)
      capDevName = capDev.getName();
    if (cdi != null)
      cdiName = cdi.toString();
    if (chan != null)
      chanName = chan.getName();
    if (currAir != null)
    {
      tit = currAir.getTitle();
      airID = currAir.getID();
    }
    props.setProperty("CaptureDevice", capDevName);
    props.setProperty("CaptureDeviceInput", cdiName);
    props.setProperty("ChannelName", chanName);
    props.setProperty("PhysicalChannel", physicalChannel);
    props.setProperty("Title", tit);
    props.setProperty("AiringID", Integer.toString(airID));
    return new SystemMessage(RECORDING_BITRATE_TOO_LOW_ERROR_MSG, ERROR_PRIORITY,
        sage.Sage.rez("RECORDING_BITRATE_TOO_LOW_ERROR_MSG", new Object[] { cdiName, tit, chanName, physicalChannel }), props);
  }

  public static SystemMessage createDiskspaceMsg(boolean error)
  {
    return new SystemMessage(DISKSPACE_INADEQUATE_MSG, error ? ERROR_PRIORITY : WARNING_PRIORITY,
        sage.Sage.rez(error ? "DISKSPACE_INADEQUATE_ERR_MSG" : "DISKSPACE_INADEQUATE_WARN_MSG"), null);
  }

  public static SystemMessage createEPGUpdateFailureMsg()
  {
    return new SystemMessage(EPG_UPDATE_FAILURE_MSG, WARNING_PRIORITY, sage.Sage.rez("EPG_UPDATE_FAILURE_MSG"), null);
  }

  public static SystemMessage createEPGServerNoKeyMsg()
  {
    return new SystemMessage(EPG_SERVER_NO_KEY_MSG, ERROR_PRIORITY, sage.Sage.rez("EPG_SERVER_NO_KEY_MSG"), null);
  }

  public static SystemMessage createEPGServerInvalidKeyMsg()
  {
    return new SystemMessage(EPG_SERVER_INVALID_KEY_MSG, ERROR_PRIORITY, sage.Sage.rez("EPG_SERVER_INVALID_KEY_MSG"), null);
  }

  public static SystemMessage createSDInvalidUsernamePasswordMsg()
  {
    return new SystemMessage(LINEUP_SD_ACCOUNT_AUTH_FAILED_MSG, ERROR_PRIORITY,
        sage.Sage.rez("LINEUP_SD_ACCOUNT_AUTH_FAILED_MSG"), null);
  }

  public static SystemMessage createSDAccountDisabledMsg()
  {
    return new SystemMessage(LINEUP_SD_ACCOUNT_DISABLED_MSG, ERROR_PRIORITY,
        sage.Sage.rez("LINEUP_SD_ACCOUNT_DISABLED_MSG"), null);
  }

  public static SystemMessage createSDAccountExpiredMsg()
  {
    return new SystemMessage(LINEUP_SD_ACCOUNT_EXPIRED_MSG, ERROR_PRIORITY,
        sage.Sage.rez("LINEUP_SD_ACCOUNT_EXPIRED_MSG"), null);
  }

  public static SystemMessage createSDAccountLockOutMsg()
  {
    return new SystemMessage(LINEUP_SD_ACCOUNT_LOCKOUT_MSG, ERROR_PRIORITY,
        sage.Sage.rez("LINEUP_SD_ACCOUNT_LOCKOUT_MSG"), null);
  }

  public static SystemMessage createOOMMsg()
  {
    return new SystemMessage(OUT_OF_MEMORY_MSG, ERROR_PRIORITY, sage.Sage.rez("OUT_OF_MEMORY_MSG"), null);
  }

  public static SystemMessage createFailedRecordingMsg(sage.CaptureDeviceInput cdi, sage.Airing currAir, sage.Channel chan, String physicalChannel)
  {
    java.util.Properties props = new java.util.Properties();
    String cdiName = "";
    String capDevName = "";
    String chanName = "";
    String tit = "";
    long airStart = 0;
    int airID = 0;
    if (cdi != null)
    {
      cdiName = cdi.toString();
      capDevName = cdi.getCaptureDevice().getName();
    }
    if (chan != null)
      chanName = chan.getName();
    if (currAir != null)
    {
      tit = currAir.getTitle();
      airStart = currAir.getStartTime();
      airID = currAir.getID();
    }
    props.setProperty("CaptureDevice", capDevName);
    props.setProperty("CaptureDeviceInput", cdiName);
    props.setProperty("ChannelName", chanName);
    props.setProperty("PhysicalChannel", physicalChannel);
    props.setProperty("Title", tit);
    props.setProperty("StartTime", Long.toString(airStart));
    props.setProperty("AiringID", Integer.toString(airID));
    return new SystemMessage(MISSED_RECORDING_FROM_CAPTURE_FAILURE_MSG, ERROR_PRIORITY,
        sage.Sage.rez("MISSED_RECORDING_FROM_CAPTURE_FAILURE_MSG", new Object[] { cdiName, tit, sage.Sage.dfClean(airStart), chanName, physicalChannel }), props);
  }

  public static SystemMessage createMissedRecordingFromConflictMsg(sage.Airing currAir, sage.Channel chan)
  {
    java.util.Properties props = new java.util.Properties();
    String chanName = "";
    String tit = "";
    long airStart = 0;
    if (chan != null)
      chanName = chan.getName();
    int airID = 0;
    if (currAir != null)
    {
      tit = currAir.getTitle();
      airStart = currAir.getStartTime();
      airID = currAir.getID();
    }
    props.setProperty("ChannelName", chanName);
    props.setProperty("Title", tit);
    props.setProperty("StartTime", Long.toString(airStart));
    props.setProperty("AiringID", Integer.toString(airID));
    return new SystemMessage(MISSED_RECORDING_FROM_CONFLICT_MSG, INFO_PRIORITY,
        sage.Sage.rez("MISSED_RECORDING_FROM_CONFLICT_MSG", new Object[] { tit, chanName, sage.Sage.dfClean(airStart) }), props);
  }

  public static SystemMessage createPartialRecordingFromConflictMsg(sage.Airing currAir, sage.Channel chan)
  {
    java.util.Properties props = new java.util.Properties();
    String chanName = "";
    String tit = "";
    long airStart = 0;
    int airID = 0;
    if (chan != null)
      chanName = chan.getName();
    if (currAir != null)
    {
      tit = currAir.getTitle();
      airStart = currAir.getStartTime();
      airID = currAir.getID();
    }
    props.setProperty("ChannelName", chanName);
    props.setProperty("Title", tit);
    props.setProperty("StartTime", Long.toString(airStart));
    props.setProperty("AiringID", Integer.toString(airID));
    return new SystemMessage(PARTIAL_RECORDING_FROM_CONFLICT_MSG, INFO_PRIORITY,
        sage.Sage.rez("PARTIAL_RECORDING_FROM_CONFLICT_MSG", new Object[] { tit, chanName, sage.Sage.dfClean(airStart) }), props);
  }

  public static SystemMessage createDeviceLoadFailureMsg(sage.CaptureDevice capDev)
  {
    java.util.Properties props = new java.util.Properties();
    String capDevName = "";
    if (capDev != null)
      capDevName = capDev.getName();
    props.setProperty("CaptureDevice", capDevName);
    return new SystemMessage(CAPTURE_DEVICE_LOAD_ERROR_MSG, ERROR_PRIORITY,
        sage.Sage.rez("CAPTURE_DEVICE_LOAD_ERROR_MSG", new Object[] { capDevName }), props);
  }

  public static SystemMessage createDeviceRecordFailureMsg(sage.CaptureDeviceInput cdi, sage.Airing currAir, sage.Channel chan, String physicalChannel)
  {
    java.util.Properties props = new java.util.Properties();
    String cdiName = "";
    String capDevName = "";
    String chanName = "";
    String tit = "";
    long airStart = 0;
    int airID = 0;
    if (cdi != null)
    {
      cdiName = cdi.toString();
      capDevName = cdi.getCaptureDevice().getName();
    }
    if (chan != null)
      chanName = chan.getName();
    if (currAir != null)
    {
      tit = currAir.getTitle();
      airStart = currAir.getStartTime();
      airID = currAir.getID();
    }
    props.setProperty("CaptureDevice", capDevName);
    props.setProperty("CaptureDeviceInput", cdiName);
    props.setProperty("ChannelName", chanName);
    props.setProperty("PhysicalChannel", physicalChannel);
    props.setProperty("Title", tit);
    props.setProperty("StartTime", Long.toString(airStart));
    props.setProperty("AiringID", Integer.toString(airID));
    return new SystemMessage(CAPTURE_DEVICE_RECORD_ERROR_MSG, ERROR_PRIORITY,
        sage.Sage.rez("CAPTURE_DEVICE_RECORD_ERROR_MSG", new Object[] { cdiName, tit, sage.Sage.dfClean(airStart), chanName, physicalChannel }), props);
  }

  public static SystemMessage createDataScanFailureMsg(sage.CaptureDeviceInput capDev)
  {
    java.util.Properties props = new java.util.Properties();
    String capDevName = "";
    if (capDev != null)
      capDevName = capDev.toString();
    props.setProperty("CaptureDeviceInput", capDevName);
    return new SystemMessage(CAPTURE_DEVICE_DATASCAN_ERROR_MSG, WARNING_PRIORITY,
        sage.Sage.rez("CAPTURE_DEVICE_DATASCAN_ERROR_MSG", new Object[] { capDevName }), props);
  }

  public static SystemMessage createEpgLinkageChangedMsg(String orgTitle, long recTime, long duration, sage.Channel chan, String newTitle)
  {
    java.util.Properties props = new java.util.Properties();
    if (orgTitle == null)
      orgTitle = "";
    if (newTitle == null)
      newTitle = "";
    props.put("OriginalTitle", orgTitle);
    props.put("NewTitle", newTitle);
    props.setProperty("StartTime", Long.toString(recTime));
    props.setProperty("Duration", Long.toString(duration));
    String chanName = "";
    if (chan != null)
      chanName = chan.getName();
    props.setProperty("ChannelName", chanName);
    return new SystemMessage(EPG_LINKAGE_FOR_MR_CHANGED_MSG, INFO_PRIORITY,
        sage.Sage.rez("EPG_LINKAGE_FOR_MR_CHANGED_MSG", new Object[] { orgTitle, chanName, sage.Sage.dfClean(recTime), newTitle }), props);
  }

  public static SystemMessage createDegradedRAIDMsg(String raidDevice)
  {
    java.util.Properties props = new java.util.Properties();
    props.setProperty("BlockDevice", raidDevice);
    return new SystemMessage(STORAGE_MONITOR_MSG, ERROR_PRIORITY,
        sage.Sage.rez("STORAGE_MONITOR_MSG", new Object[] { raidDevice }), props);
  }

  public static SystemMessage createRecoveringRAIDMsg(String raidDevice)
  {
    java.util.Properties props = new java.util.Properties();
    props.setProperty("BlockDevice", raidDevice);
    return new SystemMessage(STORAGE_MONITOR_MSG, WARNING_PRIORITY,
        sage.Sage.rez("STORAGE_MONITOR_RECOVER_MSG", new Object[] { raidDevice }), props);
  }

  public static SystemMessage createCleanRAIDMsg(String raidDevice)
  {
    java.util.Properties props = new java.util.Properties();
    props.setProperty("BlockDevice", raidDevice);
    return new SystemMessage(STORAGE_MONITOR_MSG, INFO_PRIORITY,
        sage.Sage.rez("STORAGE_MONITOR_FINISHED_MSG", new Object[] { raidDevice }), props);
  }

  /** Creates a new instance of SystemMessage */
  public SystemMessage()
  {
  }

  public SystemMessage(int type, int priority, String msgString, java.util.Properties msgVars)
  {
    super(type, msgString, msgVars, priority);
    endTimestamp = timestamp;
  }

  // Check to see if the passed in message can be tacked onto this one as a repeat of it
  public boolean canConsolidateMessage(SystemMessage testMsg)
  {
    // We don't need to compare the variable map as well since the information in there will be present in
    // the message text in some wa that'll make that comparison enough
    return (testMsg.type == type && testMsg.priority == priority && (testMsg.timestamp < timestamp + CONSOLIDATION_WINDOW) &&
        (testMsg.source == source || (source != null && source.equals(testMsg.source))));
  }

  public void consolidateMessage(SystemMessage testMsg)
  {
    if (repeats < 2)
      repeats = 2;
    else
      repeats++;
    endTimestamp = Math.max(endTimestamp, testMsg.timestamp);
  }

  public String getMessageText()
  {
    return (source instanceof String) ? ((String) source) : ((String) null);
  }

  public String[] getMessageVarNames()
  {
    if (data instanceof java.util.Properties)
    {
      java.util.Properties varProps = (java.util.Properties) data;
      return (String[]) varProps.keySet().toArray(sage.Pooler.EMPTY_STRING_ARRAY);
    }
    else
      return sage.Pooler.EMPTY_STRING_ARRAY;
  }

  public String getMessageVarValue(String name)
  {
    if (data instanceof java.util.Properties)
      return ((java.util.Properties) data).getProperty(name);
    else
      return null;
  }

  // For system messages we just give the message text here to make them easier to use
  public String toString()
  {
    return (source == null) ? "Null System Message" : source.toString();
  }

  // Used for saving it to the log file
  public String getPersistentString()
  {
    // We do name=property and delimit each pair with a ';' character. We also allow escaping the = or ; or \ character
    // with a backslash.
    StringBuffer sb = new StringBuffer();
    sb.append("pri=");
    sb.append(priority);
    sb.append(';');
    sb.append("type=");
    sb.append(type);
    sb.append(';');
    sb.append("time=");
    sb.append(timestamp);
    sb.append(';');
    String msgText = getMessageText();
    if (msgText != null)
    {
      sb.append("msg=");
      sb.append(sage.media.format.MediaFormat.escapeString(msgText));
      sb.append(';');
    }
    if (endTimestamp > timestamp)
    {
      sb.append("end=");
      sb.append(endTimestamp);
      sb.append(';');
    }
    if (repeats > 1)
    {
      sb.append("rep=");
      sb.append(repeats);
      sb.append(';');
    }
    if (data instanceof java.util.Properties)
    {
      java.util.Iterator walker = ((java.util.Properties) data).entrySet().iterator();
      while (walker.hasNext())
      {
        java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
        sb.append("V"); // We prefix all variable properties with an uppercase V
        sb.append(sage.media.format.MediaFormat.escapeString(ent.getKey().toString()));
        sb.append('=');
        if (ent.getValue() != null)
          sb.append(sage.media.format.MediaFormat.escapeString(ent.getValue().toString()));
        sb.append(';');
      }
    }
    return sb.toString();
  }

  public static SystemMessage buildMsgFromString(String str)
  {
    if (str == null || str.length() == 0) return null;
    SystemMessage rv = new SystemMessage();
    // This is based off the media format parsing code since we use a similar technique of escaping strings and having
    // unknown name/value combinations
    int currNameStart = 0;
    int currValueStart = -1;
    for (int i = 0; i < str.length(); i++)
    {
      char c = str.charAt(i);
      if (c == '\\')
      {
        // Escaped character, so skip the next one
        i++;
        continue;
      }
      else if (c == '=')
      {
        // We found the name=value delimeter, set the value start position
        currValueStart = i + 1;
      }
      else if (c == ';' && currValueStart != -1)
      {
        // We're at the end of the name value pair, get their values!
        String name = str.substring(currNameStart, currValueStart - 1);
        String value = str.substring(currValueStart, i);
        currNameStart = i + 1;
        currValueStart = -1;
        try
        {
          if ("msg".equals(name))
            rv.source = sage.media.format.MediaFormat.unescapeString(value);
          else if ("time".equals(name))
            rv.timestamp = Long.parseLong(value);
          else if ("pri".equals(name))
            rv.priority = Integer.parseInt(value);
          else if ("type".equals(name))
            rv.type = Integer.parseInt(value);
          else if ("end".equals(name))
            rv.endTimestamp = Long.parseLong(value);
          else if ("rep".equals(name))
            rv.repeats = Integer.parseInt(value);
          else if (name.startsWith("V"))
          {
            if (rv.data == null)
              rv.data = new java.util.Properties();
            ((java.util.Properties) rv.data).setProperty(sage.media.format.MediaFormat.unescapeString(name.substring(1)),
                sage.media.format.MediaFormat.unescapeString(value));
          }
        }
        catch (Exception e)
        {
          System.out.println("ERROR parsing container format info " + str + " of:" + e);
        }
      }
    }
    if (rv.endTimestamp == 0)
      rv.endTimestamp = rv.timestamp;
    return rv;
  }

  public long getEndTimestamp()
  {
    return endTimestamp;
  }

  public int getRepeatCount()
  {
    return repeats;
  }

  // the java.util.Map of vars we store in the 'data' field
  // the String for the msg text we store in the 'source' field
  private long endTimestamp;
  private int repeats; // 0 & 1 mean the same thing
}
