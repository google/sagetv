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

import sage.media.sub.RollupAnimation;

import java.util.ArrayList;
import java.util.List;

/*
 * NOTE: I use Sage.preferredServer when specifying the hostname to get around the
 * name/IP address resolution issue. This should be done in more of a SageDNS like
 * manner. But since there's no way currently for a Sage app to have files from more than
 * one host, it's not an issue yet.
 */
public final class VideoFrame extends BasicVideoFrame implements Runnable
{
  public static final String DEFAULT_MEDIA_LANGUAGE_OPTIONS = "en;English;eng|ar;Arabic;ara|bg;Bulgarian;bul|zh;Chinese;chi,zho|cs;Czech;ces,cze|da;Danish;dan|nl;Dutch;nld,dut,nla|fi;Finnish;fin|fr;French;fra,fre|de;German;deu,ger|el;Greek;gre,ell|he;Hebrew;heb|hu;Hungarian;hun|it;Italian;ita|ja;Japanese;jpn|ko;Korean;kor|no;Norwegian;nor|pl;Polish;pol|pt;Portugese;por|ru;Russian;rus|sl;Slovenian;slv|es;Spanish;esl,spa|sv;Swedish;sve,swe|to;Tonga;ton,tog|tr;Turkish;tur";
  private static java.util.Map mediaLangMap;
  private static class MediaLangInfo
  {
    public MediaLangInfo(String fullName, String twoChar, String[] threeChar)
    {
      this.fullName = fullName;
      this.twoChar = twoChar;
      this.threeChar = threeChar;
    }

    public String fullName;
    public String twoChar;
    public String[] threeChar;
  }

  static
  {
    if (Sage.WINDOWS_OS)
    {
      sage.Native.loadLibrary("DShowPlayer");
    }

    if (mediaLangMap == null)
    {
      mediaLangMap = new java.util.HashMap();
      java.util.StringTokenizer toker = new java.util.StringTokenizer(Sage.get("media_language_options", DEFAULT_MEDIA_LANGUAGE_OPTIONS), "|");
      while (toker.hasMoreTokens())
      {
        String currToke = toker.nextToken();
        int semiIdx1 = currToke.indexOf(';');
        int semiIdx2 = currToke.lastIndexOf(';');
        if (semiIdx1 == -1 || semiIdx2 == -1 || semiIdx1 == semiIdx2)
          continue; // bad data
        String fullLang = currToke.substring(semiIdx1 + 1, semiIdx2);
        String twoChar = currToke.substring(0, semiIdx1);
        java.util.StringTokenizer threeTokes = new java.util.StringTokenizer(currToke.substring(semiIdx2 + 1), ",");
        String[] threeLangs = new String[threeTokes.countTokens()];
        if (threeLangs.length == 0)
          continue; // bad data
        int idx = 0;
        while (threeTokes.hasMoreTokens())
          threeLangs[idx++] = threeTokes.nextToken();
        mediaLangMap.put(fullLang, new MediaLangInfo(fullLang, twoChar, threeLangs));
      }
    }
  }

  public static final String SEGMENT_MIN_BACKUP = "segment_min_backup";
  public static final String ENABLE_PC = "enable_pc";
  public static final String PC_CODE = "pc_code";
  public static final String PC_RESTRICT = "pc_restrict";
  public static final String RANDOM_MUSIC_PLAYBACK = "random_music_playback";
  public static final String REPEAT_MUSIC_PLAYBACK = "music/repeat_playback";
  public static final String RANDOM_VIDEO_PLAYBACK = "random_video_playback";
  public static final String REPEAT_VIDEO_PLAYBACK = "video_lib/repeat_playback";
  public static final String SKIP_DVD_MENUS = "skip_dvd_menus";
  public static final String TIME_BEHIND_LIVE_TO_DISABLE_SKIP_FORWARD = "time_behind_live_to_disable_skip_forward";
  public static final String TIME_BEHIND_LIVE_TO_DISABLE_FAST_FORWARD = "time_behind_live_to_disable_fast_forward";
  public static final String LOCAL_ENCODING_TO_PLAYBACK_DELAY = "local_encoding_to_playback_delay";
  public static final String NETWORK_ENCODING_TO_PLAYBACK_DELAY = "network_encoding_to_playback_delay";
  public static final String TIME_TO_START_INTO_TV_FILE_PLAYBACK = "time_to_start_into_tv_file_playback";
  public static final String LAST_CC_STATE = "last_cc_state";
  private static final String FF_TIME = "ff_time";
  private static final String REW_TIME = "rew_time";
  private static final String FF_TIME2 = "ff_time2";
  private static final String REW_TIME2 = "rew_time2";

  private static final long CUT_JUMP_BACKP = 2000L;
  private static final long LOAD_FILE_WAIT = 200L;
  private static final long NEWFILE_INIT_WAIT = 500L;
  protected static long PLAY_SEAM_ADVANCE = 250L;

  private static final float MAX_BITRATE_CHANGE = 0.1f;

  private static final int SEEK_COMPLETE_HOOK_DELAY = 0;

  private static final int WATCH_MF = 100;
  private static final int LOAD_MF = 101;
  private static final int SLEEP = 102;
  private static final int CLOSE_MF = 103;
  private static final int STD_COMPLETE = 104;
  private static final int SEAM_TO_NEXT = 105;
  private static final int PAUSE = 106;
  private static final int PLAY = 107;
  private static final int TIME_ADJUST = 108;
  private static final int TIME_SET = 109;
  private static final int RATE_ADJUST = 110;
  private static final int RATE_SET = 111;
  private static final int SEGMENT_ADJUST = 112;
  private static final int INACTIVE_FILE = 113;
  private static final int WATCH_COMPLETE_CHECK = 114;
  private static final int PLAY_PAUSE = 115;
  private static final int DIRECT_CONTROL_MSG = 116;
  private static final int START_PLAYLIST = 117;
  private static final int RELOAD_MF = 118;
  private static final int CHANNEL_TUNE = 119;
  private static final int SMOOTH_FORWARD = 120;
  private static final int SMOOTH_REVERSE = 121;
  private static final int RELOAD_PREPARE_MF = 122;

  public static final int DVD_CONTROL_MENU = 201; // 1 for title, 2 for root
  public  static final int DVD_CONTROL_TITLE_SET = 202;
  public  static final int DVD_CONTROL_CHAPTER_SET = 205;
  public  static final int DVD_CONTROL_CHAPTER_NEXT = 206;
  public  static final int DVD_CONTROL_CHAPTER_PREV = 207;
  public  static final int DVD_CONTROL_ACTIVATE_CURRENT = 208;
  public  static final int DVD_CONTROL_RETURN = 209;
  public  static final int DVD_CONTROL_BUTTON_NAV = 210; // 1up,2right,3down,4left
  public  static final int DVD_CONTROL_MOUSE_HOVER = 211;
  public  static final int DVD_CONTROL_MOUSE_CLICK = 212;
  public  static final int DVD_CONTROL_ANGLE_CHANGE = 213;
  public  static final int DVD_CONTROL_SUBTITLE_CHANGE = 214;
  public  static final int DVD_CONTROL_SUBTITLE_TOGGLE = 215;
  public  static final int DVD_CONTROL_AUDIO_CHANGE = 216;
  public  static final int DVD_CONTROL_RESUME_PLAYBACK = 217; // return to where we stopped when last played

  // watch request failure codes
  public static final int WATCH_OK = 0;
  public static final int WATCH_FAILED_NO_PICTURE_FILES = -1;
  public static final int WATCH_FAILED_FILES_NOT_ON_DISK = -2;
  public static final int WATCH_FAILED_PARENTAL_CHECK_FAILED = -3;
  public static final int WATCH_FAILED_AIRING_EXPIRED = -4;
  public static final int WATCH_FAILED_AIRING_NOT_STARTED = -5;
  public static final int WATCH_FAILED_LIVE_STREAM_UKNOWN_INPUT = -6;
  public static final int WATCH_FAILED_GENERAL_SEEKER = -7;
  public static final int WATCH_FAILED_USER_REJECTED_CONFLICT = -8;
  public static final int WATCH_FAILED_ALL_ENCODERS_UNDER_LIVE_CONTROL = -9;
  public static final int WATCH_FAILED_NO_ENCODERS_HAVE_STATION = -10;
  public static final int WATCH_FAILED_GENERAL_CANT_FIND_ENCODER = -11;
  public static final int WATCH_FAILED_NULL_AIRING = -12;
  public static final int WATCH_FAILED_UNKNOWN = -13;
  public static final int WATCH_FAILED_NO_PLAYLIST_RANDOM_ACCESS = -14;
  public static final int WATCH_FAILED_PLAYLIST_OVER = -15;
  public static final int WATCH_FAILED_SURF_CONTEXT = -16;
  public static final int WATCH_FAILED_NETWORK_ERROR = -17;
  public static final int WATCH_FAILED_FORCE_REQUEST_WITHOUT_CONTROL = -18;
  public static final int WATCH_FAILED_NO_MEDIA_PLAYER_FOR_FILE = -19;
  public static final int WATCH_FAILED_NO_MEDIA_PLAYER_FOR_TIMESHIFTED_FILE = -20;
  public static final int WATCH_FAILED_INSUFFICIENT_RESOURCES_WHILE_RECORDING = -21;
  public static final int WATCH_FAILED_INSUFFICIENT_PERMISSIONS = -22;
  public static final int WATCH_FAILED_INSUFFICIENT_RESOURCES_FOR_EXTERNAL_CLIENT = -23;

  public static final int DEFAULT_MEDIA_PLAYER_SETUP = 0;
  public static final int STBX25XX_MEDIA_PLAYER_SETUP = 2;

  private static final String LAST_STATION_ID = "last_station_id";

  protected final Object volumeLock = new Object();

  public static boolean isGlobalWatchError(int x)
  {
    return (x == WATCH_FAILED_INSUFFICIENT_RESOURCES_WHILE_RECORDING) || (x == WATCH_FAILED_ALL_ENCODERS_UNDER_LIVE_CONTROL) ||
        (x == WATCH_FAILED_INSUFFICIENT_PERMISSIONS);
  }

  private static final String getNameForVFJobID(int x)
  {
    switch (x)
    {
      case WATCH_MF:
        return "WatchMF";
      case LOAD_MF:
        return "LoadMF";
      case RELOAD_MF:
        return "ReloadMF";
      case RELOAD_PREPARE_MF:
        return "ReloadPrepareMF";
      case SLEEP:
        return "Sleep";
      case CLOSE_MF:
        return "CloseMF";
      case STD_COMPLETE:
        return "StdComplete";
      case SEAM_TO_NEXT:
        return "SeamToNext";
      case PAUSE:
        return "Pause";
      case PLAY:
        return "Play";
      case TIME_ADJUST:
        return "TimeAdjust";
      case TIME_SET:
        return "TimeSet";
      case RATE_ADJUST:
        return "RateAdjust";
      case RATE_SET:
        return "RateSet";
      case SEGMENT_ADJUST:
        return "SegmentAdjust";
      case INACTIVE_FILE:
        return "InactiveFile";
      case WATCH_COMPLETE_CHECK:
        return "WatchCompleteCheck";
      case PLAY_PAUSE:
        return "PlayPause";
      case DIRECT_CONTROL_MSG:
        return "DirectControl";
      case START_PLAYLIST:
        return "StartPlaylist";
      case CHANNEL_TUNE:
        return "ChannelTune";
      case SMOOTH_FORWARD:
        return "SmoothFwd";
      case SMOOTH_REVERSE:
        return "SmoothRev";
      default:
        return "UnknownJob";
    }
  }

  // For backwards compatability with legacy media player plugins
  public static VideoFrame getInstance()
  {
    UIManager localUI = UIManager.getLocalUI();
    return (localUI != null) ? localUI.getVideoFrame() : null;
  }

  private static final java.util.Vector activeVFs = new java.util.Vector();

  public static VideoFrame getVideoFrameForPlayer(MediaPlayer mp)
  {
    synchronized (activeVFs)
    {
      for (int i = 0; i < activeVFs.size(); i++)
      {
        VideoFrame vf = (VideoFrame) activeVFs.get(i);
        if (vf.player == mp ||
            (vf.player instanceof MetaMediaPlayer && ((MetaMediaPlayer) vf.player).getCurrMediaPlayer() == mp))
          return vf;
      }
    }
    return null;
  }

  public static MediaFile getMediaFileForPlayer(MediaPlayer mp)
  {
    synchronized (activeVFs)
    {
      for (int i = 0; i < activeVFs.size(); i++)
      {
        VideoFrame vf = (VideoFrame) activeVFs.get(i);
        if (vf.player == mp ||
            (vf.player instanceof MetaMediaPlayer && ((MetaMediaPlayer) vf.player).getCurrMediaPlayer() == mp))
          return vf.getCurrFile();
      }
    }
    return null;
  }

  public static void kickAll()
  {
    synchronized (activeVFs)
    {
      for (int i = 0; i < activeVFs.size(); i++)
      {
        ((VideoFrame) activeVFs.get(i)).kick();
      }
    }
  }

  public static boolean hasFileAny()
  {
    synchronized (activeVFs)
    {
      for (int i = 0; i < activeVFs.size(); i++)
      {
        if (((VideoFrame) activeVFs.get(i)).hasFile())
          return true;
      }
    }
    return false;
  }

  public static boolean isPlayinAny()
  {
    synchronized (activeVFs)
    {
      for (int i = 0; i < activeVFs.size(); i++)
      {
        if (((VideoFrame) activeVFs.get(i)).getPlayerState() == MediaPlayer.PLAY_STATE)
          return true;
      }
    }
    return false;
  }

  public static void goodbyeAll()
  {
    synchronized (activeVFs)
    {
      for (int i = 0; i < activeVFs.size(); i++)
      {
        ((VideoFrame) activeVFs.get(i)).goodbye();
      }
    }
  }

  public static void clearSortedChannelLists()
  {
    synchronized (activeVFs)
    {
      for (int i = 0; i < activeVFs.size(); i++)
      {
        ((VideoFrame) activeVFs.get(i)).sortedViewableChans = null;
      }
    }
  }

  public static void inactiveFileAll(String filename)
  {
    synchronized (activeVFs)
    {
      for (int i = 0; i < activeVFs.size(); i++)
      {
        ((VideoFrame) activeVFs.get(i)).inactiveFile(filename);
      }
    }
  }

  public static void closeAndWaitAll()
  {
    // We don't sync this because the close methods may end up calling
    // back into the VF to find their respective UIMgr which uses the same sync lock and that
    // would deadlock. This is also only called in a disconnect scenario so there should be no new connections anyways
    //		synchronized (activeVFs)
    {
      for (int i = 0; i < activeVFs.size(); i++)
      {
        ((VideoFrame) activeVFs.get(i)).closeAndWait();
      }
    }
  }

  public static boolean isAnyPlayerUsingFile(java.io.File testFile)
  {
    synchronized (activeVFs)
    {
      for (int i = 0; i < activeVFs.size(); i++)
      {
        VideoFrame vf = (VideoFrame) activeVFs.get(i);
        MediaFile mf = vf.currFile;
        if (mf != null && mf.hasFile(testFile))
          return true;
        if (vf.player instanceof MetaMediaPlayer)
        {
          MediaPlayer checkMe = ((MetaMediaPlayer) vf.player).getCurrMediaPlayer();
          if (checkMe != null && checkMe.getFile() != null && checkMe.getFile().equals(testFile))
            return true;
        }
      }
    }
    return false;
  }

  public static java.util.ArrayList getVFsUsingMediaFile(MediaFile testFile)
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    synchronized (activeVFs)
    {
      for (int i = 0; i < activeVFs.size(); i++)
      {
        VideoFrame vf = (VideoFrame) activeVFs.get(i);
        MediaPlayer playa = vf.player;
        if (vf.currFile == testFile && playa != null && playa.getState() != MediaPlayer.NO_STATE)
          rv.add(vf);
      }
    }
    return rv;
  }

  public VideoFrame(UIManager inUIMgr)
  {
    super(inUIMgr);
    alive = true;
    activeVFs.add(this);
    watchQueue = new java.util.Vector();
    playlistChain = new java.util.Vector();
    playlistChainIndices = new java.util.Vector();
    alreadyPlayedRandomSongs = new java.util.HashSet();

    // NOTE: JK This is a setting we've always had but it was hardcoded into here.
    // I think this might be useful for fixing the 350 EOF problem by having users just increase this to a few seconds...
    // it'll at least give them something to play with.
    PLAY_SEAM_ADVANCE = uiMgr.getLong(prefs + "play_seam_advance", 250L);

    segmentMinBackup = uiMgr.getLong(prefs + SEGMENT_MIN_BACKUP, 5000L);
    lastStationID = uiMgr.getInt(prefs + LAST_STATION_ID, 0);

    subtitleDelay = uiMgr.getLong("subtitle/delay", 0);

    liveControl = false;
    setBackground(java.awt.Color.black);

    if (Sage.WINDOWS_OS)
    {
      // NOTE NOTE NOTE WE SHOULD ENSURE THAT ANY LIVE INPUTS ARE MUTED AT THIS POINT
      // ZQ. I mute mixer src on DShowCapture, if TV audio goes through audio card ZQ.
      // ZQ.
      if (uiMgr.getBoolean("videoframe/mute_line_in_on_startup", false))
      {
        System.out.println( "setLiveMute Line In got called" );
        DShowLivePlayer.setLiveMute0(-1, true);
      }

    }
    dvdMouseListener = new java.awt.event.MouseAdapter()
    {
      public void mouseReleased(java.awt.event.MouseEvent evt)
      {
        if (areDvdButtonsVisible())
          playbackControl(DVD_CONTROL_MOUSE_CLICK, evt.getX(), evt.getY());
      }
    };
    dvdMouseMotionListener = new java.awt.event.MouseMotionAdapter()
    {
      public void mouseMoved(java.awt.event.MouseEvent evt)
      {
        if (areDvdButtonsVisible())
          playbackControl(DVD_CONTROL_MOUSE_HOVER, evt.getX(), evt.getY());
      }
    };

    if (uiMgr.getUIClientType() == UIClient.REMOTE_UI)
    {
      mediaPlayerSetup = STBX25XX_MEDIA_PLAYER_SETUP;
    }
  }

  private native void init0();
  private native void goodbye0();
  void goodbye()
  {
    try
    {
      closeMediaFile(true, true, false);
    }catch(Exception e)
    {
      System.out.println("Error calling closeMediaFile from VF goodbye:" + e);
    }
    // With multi-UI clients we now need to call this on goodbye in order to clean up any watches we have
    try
    {
      seek.finishWatch(uiMgr);
    }catch(Exception e)
    {
      System.out.println("Error calling finishWatch from VF goodbye:" + e);
    }
    subtitleComp = null;
    ccComp = null;
    alive = false;
    activeVFs.remove(this);
    kick();
  }

  void sleep()
  {
    submitJob(new VFJob(SLEEP));
  }

  public void kick()
  {
    synchronized (queueLock)
    {
      queueLock.notifyAll();
      kicked = true;
    }
  }

  public int startPlaylist(Playlist newPlaylist, int startIdx)
  {
    if (Sage.DBG) System.out.println("VF.startPlaylist " + newPlaylist + " startIdx=" + startIdx);
    if (newPlaylist.getNumSegments() == 0 || !newPlaylist.verifyPlaylist()) return WATCH_OK;
    // Check the entire playlist for parental consent
    String pcCheck = newPlaylist.doesRequirePCAccess(uiMgr);
    boolean passesPC = false;
    if (pcCheck != null)
    {
      Object rv = Catbert.processUISpecificHook("RequestToExceedParentalRestrictions", new Object[] { newPlaylist, pcCheck }, uiMgr, false);
      if (!Catbert.evalBool(rv))
        return WATCH_FAILED_PARENTAL_CHECK_FAILED;
      passesPC = true;
    }
    submitJob(new VFJob(newPlaylist, startIdx, passesPC));
    return WATCH_OK;
  }

  public void setAboutToCallWatch()
  {
    aboutToDoWatchRequest = true;
  }
  public void clearAboutToCallWatch()
  {
    aboutToDoWatchRequest = false;
  }

  public int watch(MediaFile watchFile)
  {
    // Keep it all airing based even though we undo this afterwards
    if (watchFile == null) return WATCH_FAILED_NULL_AIRING;
    return watch(watchFile.getContentAiring(), false, null, watchFile, false);
  }
  public int watch(Airing watchAir)
  {
    return watch(watchAir, false, null, null, false);
  }
  public int watch(Airing watchAir, boolean forceLive)
  {
    return watch(watchAir, forceLive, null, null, false);
  }
  public int lockTuner(MediaFile liveFile)
  {
    if (liveFile == null) return WATCH_FAILED_NULL_AIRING;
    return watch(liveFile.getContentAiring(), true, null, liveFile, true);
  }
  private int watch(Airing watchAir, boolean forceLive, Playlist sourcePlaylist, MediaFile watchThisFile)
  {
    return watch(watchAir, forceLive, sourcePlaylist, watchThisFile, false);
  }
  private int watch(Airing watchAir, boolean forceLive, Playlist sourcePlaylist, MediaFile watchThisFile, boolean lockTunerOnly)
  {
    if (Sage.DBG) System.out.println("VideoFrame.watch(" + watchAir + ")");
    if (watchAir == null) return WATCH_FAILED_NULL_AIRING;
    try
    {
      processingWatchRequest = true;
      aboutToDoWatchRequest = false;
      if (watchThisFile == null)
        watchThisFile = Wizard.getInstance().getFileForAiring(watchAir);
      if (Sage.DBG) System.out.println("watchThisFile=" + watchThisFile);
      iWantToBeLive = false;

      if (watchThisFile != null && watchThisFile.isAnyLiveStream())
        forceLive = true;

      if (lockTunerOnly && (watchThisFile == null || !watchThisFile.isAnyLiveStream()))
        return WATCH_FAILED_LIVE_STREAM_UKNOWN_INPUT;

      // Quick check for reselecting what we're already on
      // 1/16/2004 NOTE: I removed the watchQueue.isEmpty() because its not thread
      // synced at all. Its purpose is to not drop watch requests if there's ones already
      // in the queue, but FF/REW and other ops could also cause redundantly doing this
      if (watchThisFile != null && watchThisFile == currFile && watchThisFile.generalType != MediaFile.MEDIAFILE_LOCAL_PLAYBACK/* && watchQueue.isEmpty()*/)
      {
        boolean mustWatchThis = false;
        synchronized (queueLock)
        {
          for (int i = 0; i < watchQueue.size(); i++)
          {
            int jobID = ((VFJob) watchQueue.get(i)).id;
            if (jobID == LOAD_MF || jobID == WATCH_MF || jobID == CLOSE_MF || jobID == START_PLAYLIST)
            {
              mustWatchThis = true;
              break;
            }
          }
        }
        if (!mustWatchThis)
        {
          if (forceLive)
          {
            submitJob(new VFJob(TIME_SET, Long.MAX_VALUE));
            // If we're live we need to be playing too!
            submitJob(new VFJob(PLAY));
          }
          else if (restartOnRedundantWatch)
          {
            submitJob(new VFJob(TIME_SET, 0));
            submitJob(new VFJob(PLAY));
          }
          restartOnRedundantWatch = false;
          return WATCH_OK;
        }
      }
      restartOnRedundantWatch = false;

      // Check for an accidental picture request
      if (watchThisFile != null && watchThisFile.isPicture())
      {
        //2uiMgr.advanceUI(new PictureViewer(watchThisFile));
        watchThisFile.cleanupLocalFile();
        return WATCH_FAILED_NO_PICTURE_FILES;
      }

      // don't forget about ones that may have started early because of lead time
      boolean alreadyStarted = (watchAir.getSchedulingStart() <= Sage.time()) || (watchThisFile != null);
      // Imported files might have invalid time stamps which are in the future so it may not be over
      // according to a time check
      boolean airingDone = (watchAir.getSchedulingEnd() <= Sage.time()) || (watchAir instanceof MediaFile.FakeAiring) ||
          (watchThisFile != null && !watchThisFile.isTV());

      if (watchThisFile != null && watchThisFile.isAnyLiveStream())
        airingDone = false;
      boolean currDvdPlayback = (watchThisFile != null) &&
          watchThisFile.generalType == MediaFile.MEDIAFILE_DEFAULT_DVD_DRIVE;

      // Check if it's a file
      if ((watchThisFile != null) && (airingDone || currDvdPlayback || watchThisFile.isDVD() || watchThisFile.isBluRay()))
      {
        // Check for resource availability
        if (!Sage.isTrueClient())
        {
          if (MMC.getInstance().getTotalCaptureDeviceResourceUsage() > 85)
          {
            watchThisFile.cleanupLocalFile();
            return WATCH_FAILED_INSUFFICIENT_RESOURCES_WHILE_RECORDING;
          }
        }
        if (!hasMediaPlayer(watchThisFile))
        {
          watchThisFile.cleanupLocalFile();
          return watchThisFile.isRecording() ? WATCH_FAILED_NO_MEDIA_PLAYER_FOR_TIMESHIFTED_FILE :
            WATCH_FAILED_NO_MEDIA_PLAYER_FOR_FILE;
        }
        if (!watchThisFile.verifyFiles(true))
        {
          if (Sage.DBG) System.out.println("BAD WATCH Selected watch failed file verification.");
          watchThisFile.cleanupLocalFile();
          return WATCH_FAILED_FILES_NOT_ON_DISK;
        }
        if (Sage.DBG) System.out.println("Watch airing is a file & over, do that instead dvd=" + currDvdPlayback);
        if (pcAiringCheck(watchAir))
        {
          liveControl = false;
          controlledInput = null;
          //seek.finishWatch(); finishWatch is done in requestWatch automatically
          // For DVD drive playback on the client, don't send the request to the server because
          // it's not involved in it
          int[] seekerError = new int[1];
          if ((Sage.client && currDvdPlayback) || seek.requestWatch(watchThisFile, seekerError, uiMgr) != null)
          {
            // Update the channel MRU list
            if (watchAir.stationID != 0)
              Sage.getRawProperties().updateMRUList("recent_channels", Integer.toString(watchAir.stationID), 10);
            submitJob(new VFJob(WATCH_MF, watchThisFile, sourcePlaylist));
            iWantToBeLive = forceLive;
          }
          return seekerError[0];
        }
        else
        {
          watchThisFile.cleanupLocalFile();
          return WATCH_FAILED_PARENTAL_CHECK_FAILED;
        }
      }

      // If it's over and not a file then were done with it
      if (airingDone) return WATCH_FAILED_AIRING_EXPIRED;

      // We can't watch it if it hasn't started yet.
      if (!alreadyStarted) return WATCH_FAILED_AIRING_NOT_STARTED;

      // Check permissions for live TV viewing
      if (!Permissions.hasPermission(Permissions.PERMISSION_LIVETV, uiMgr)) return WATCH_FAILED_INSUFFICIENT_PERMISSIONS;

      if (!pcAiringCheck(watchAir))
      {
        if (watchThisFile != null)
          watchThisFile.cleanupLocalFile();
        return WATCH_FAILED_PARENTAL_CHECK_FAILED;
      }

      // We need to maintain live control even through the asynchronous stuff
      // in the VF and Seeker
      // NOTE: We don't need to check for resource usage in this case because the file isn't being recorded yet
      // so we're not going to create any new resource usage by this and the encoding will swap encoders
      // if it is a new record and then we'll end up watching a live stream instead.
      int[] seekerError = new int[1];
      watchThisFile = seek.requestWatch(watchAir, seekerError, uiMgr);
      if (watchThisFile == null && !Wizard.getInstance().ok(watchAir)) {
        // This can happen due to the database updating and replacing an Airing while the user stays
        // on that part of the UI.
        System.out.println("WARNING: Airing no longer exists in database for watch request; try to reconcile");
        long currTime = Sage.time();
        Airing[] matchedAirs = Wizard.getInstance().getAirings(watchAir.getStationID(), currTime, currTime + 1, false);
        if (matchedAirs.length > 0 && matchedAirs[0] != watchAir) {
          System.out.println("Swapping requested airing of: " + watchAir + " with replacement: " + matchedAirs[0]);
          watchAir = matchedAirs[0];
          watchThisFile = seek.requestWatch(watchAir, seekerError, uiMgr);
        }
      }
      if (watchThisFile != null)
      {
        if (!lockTunerOnly && !hasMediaPlayer(watchThisFile))
        {
          processingWatchRequest = false;
          closeAndWait();
          if (watchThisFile != null)
            watchThisFile.cleanupLocalFile();
          return WATCH_FAILED_NO_MEDIA_PLAYER_FOR_TIMESHIFTED_FILE;
        }
        liveControl = true;
        iWantToBeLive = forceLive;
        if (watchThisFile.isAnyLiveStream())
          controlledInput = mmc.getCaptureDeviceInputNamed(watchThisFile.encodedBy);
        uiMgr.putInt(prefs + LAST_STATION_ID, lastStationID = watchAir.stationID);
        // Update the channel MRU list
        if (watchAir.stationID != 0)
          Sage.getRawProperties().updateMRUList("recent_channels", Integer.toString(watchAir.stationID), 10);
        if (!lockTunerOnly)
          submitJob(new VFJob(WATCH_MF, watchThisFile, sourcePlaylist));
        return WATCH_OK;
      }
      else
      {
        if (watchThisFile != null)
          watchThisFile.cleanupLocalFile();
        return seekerError[0];
      }
    }
    finally
    {
      processingWatchRequest = false;
    }
  }

  int getLastStationID() { return lastStationID; }

  private void submitJob(VFJob newJob)
  {
    if (Sage.DBG) System.out.println("VF.submitJob(" + newJob + ")");
    synchronized (queueLock)
    {
      watchQueue.addElement(newJob);
      queueLock.notifyAll();
    }
  }

  public void run()
  {
    seek = SeekerSelector.getInstance();
    if (Sage.DBG) System.out.println("VF thread is now running...");
    long waitTime = 0;
    long fileReadyTime = 0;
    int waitQueueSize = -1;
    boolean rethreadVF = uiMgr.getBoolean("videoframe/rethread", true);
    if (Sage.WINDOWS_OS)
    {
      init0();
      java.awt.EventQueue.invokeLater(new Runnable()
      {
        public void run() { init0(); }
      });
    }
    while (alive)
    {
      try{

        VFJob currJob = null;
        synchronized (queueLock)
        {
          queueLock.notifyAll();
          // 7/25/03 Changed the 1000 to be 250. This is more or less just wasting time so
          // we're not being inneffecient continually checking for data in the file, so don't
          // be too wasteful
          if ((watchQueue.isEmpty() || waitQueueSize == watchQueue.size()) && waitTime >= 0 && !kicked)
          {
            if (Sage.DBG) System.out.println("VF thread is now waiting for " + Sage.durFormatMillis(waitTime));
            try { queueLock.wait(waitTime); } catch(InterruptedException e){}
          }
          kicked = false;
          if (!watchQueue.isEmpty())
          {
            currJob = (VFJob) watchQueue.firstElement();
            if (currJob == null) watchQueue.remove(0);
          }
        }
        waitQueueSize = -1;
        if (Sage.DBG) System.out.println("VF processing job " + currJob + " nPlayin=" + isPlayin());

        // We can only have signal loss while loading a file so clear it here to be safe
        if (currJob == null || currJob.id != LOAD_MF)
        {
          if (signalLost)
            Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
          signalLost = false;
        }
        waitTime = 0;
        if (currJob == null && currFile != null)
        {
          // Figure out the new wait time for this situation
          long baseTime = getBaseMediaTimeMillis(false);
          // Do the EOS check after we get the time because that's how the EOS is sent back for pull-mode MiniPlayer clients.
          boolean eos = player != null && player.getState() == MediaPlayer.EOS_STATE;
          if (Sage.DBG) System.out.println("isRec=" + currFile.isRecording() + " rd=" + getRealDurMillis() + " base=" +
              baseTime + " eos=" + eos);
          if (currFile.isAnyLiveStream())
          {
            // No seaming for live playback
            continue;
          }
          else if (!currFile.isDVD() && !currFile.isBluRay() && (segment < currFile.getNumSegments() - 1) && isPlayin())
          {
            waitTime = getRealDurMillis() - getBaseMediaTimeMillis(false) -
                PLAY_SEAM_ADVANCE;
            if (eos || waitTime <= 0)
            {
              // Check if this is a current recording and to be sure the new segment we're moving over to actually
              // has data in it. If it doesn't...then spin on that switch.
              if (!currFile.isRecording() || segment != currFile.getNumSegments() - 2 || !currFile.shouldIWaitForData())
              {
                watchQueue.insertElementAt(new VFJob(SEAM_TO_NEXT), 0);
                waitTime = -1;
                continue;
              }
              else
              {
                if (Sage.DBG) System.out.println("Waiting to seam to the next segment because it contains " +
                    "no data currently and we are currently recording it segment=" + segment + " numSegments=" +
                    currFile.getNumSegments());
                waitTime = 1500;
              }
            }
          }
          else if (isPlayin())
          {
            if (currFile.isRecording())
            {
              // This will end up with short, repetitive waits for recordings that
              // go beyond the airing's normal end time. No big deal, just a bunch
              // of junk in the log files.
              waitTime = Math.max(0, currFile.getContentAiring().getSchedulingEnd() - Sage.time()) +
                  currFile.getDuration(segment) - getBaseMediaTimeMillis(false) -
                  PLAY_SEAM_ADVANCE;
              /*
               * NOTE: IF WE'VE GOT TOO MUCH DELAY IN THE SEAMER THEN THIS IS A GOOD
               * PLACE TO MAKE SOME OPTIMIZATIONS
               *
               * THIS IS ANALAGOUS TO THE DATASTREAM BEING CUT OFF BY THE SERVER,
               * WE DON'T KNOW WHEN THIS RECORDING IS GOING TO END...maybe it's analagous
               */
              waitTime = Math.max(waitTime, 1);
            }
            else
            {
              // Don't wait until the end of the airing to switch, do it at the end of the
              // file!
              waitTime = getRealDurMillis() - getBaseMediaTimeMillis(false) -
                  PLAY_SEAM_ADVANCE;
            }
            // Don't use the waitTime calc for this since it'll be totally off
            // 8/14/09 - Narflex - Setting the waitTime to zero when we've continued onto the next
            // file in live TV playback can cause us to reload that file immediately which is incorrect behavior
            // so disable the < 1000 hack in that case
            if (currFile.isDVD() || (getRealDurMillis() < 1000 && !currFile.isRecording()))
              waitTime = 0;
            // NOTE: The liveControl could be from a pause a LONG time ago, and the current
            // Seeker record could possibly not be what's on next after the current file!
            // For music files, only go on EOS since duration can be totally whacked
            // Same goes for files that are being downloaded or have invalid durations
            // Narflex - 10/28/10 - DTS files on Windows are a special case where we don't always get the EOS properly
            // Narflex - 2/6/13 - There was an issue where we would do a seamless transition to the next file before
            // the server had actually pushed the EOS data because the current media time was within 250msec of the calculated duration.
            // So I added a '!liveControl' to the below conditional to prevent us from moving to the next airing if we have live control
            // and we have no hit the EOS yet.
            // Narflex - 1/9/17 - Conditionalize the !liveControl so it doesn't apply on Windows DShow playback because there's
            // some other bug which is preventing the demux EOS message from coming back which is there in V7 as well. Ideally,
            // that issue gets fixed...but for the meantime, in order to match V7 behavior...do this instead.
            if (eos || (waitTime <= 0 && (!currFile.isMusic() ||
                (sage.media.format.MediaFormat.DTS.equals(currFile.getContainerFormat()) && uiMgr.getUIClientType() == UIClient.LOCAL && Sage.WINDOWS_OS)) &&
                getRealDurMillis() > 1 && !currFile.isDVD() && (!liveControl || player instanceof DShowMediaPlayer)))
            {
              if (processingWatchRequest)
              {
                waitTime = LOAD_FILE_WAIT;
                continue;
              }
              else if (liveControl && !playbackDone) // playbackDone could be true if the PC check failed for the next live airing
              {
                // Check to make sure we don't have any WATCH_MF or START_PLAYLIST item in the queue which would
                // override our default selection of what to watch next. We now receive the InactiveFile message
                // very quickly so we may hit this if they just pick something else to watch.
                if (Sage.DBG) System.out.println("VideoFrame watching a live old airing, fixing it.");
                boolean skipThisOne = false;
                for (int i = watchQueue.size() - 1; i >= 0; i--)
                {
                  int testid = ((VFJob) watchQueue.elementAt(i)).id;
                  if (testid == WATCH_MF || testid == START_PLAYLIST)
                  {
                    if (Sage.DBG) System.out.println("Aborting selection of the next live program because we have a watch job in the queue");
                    skipThisOne = true;
                    break;
                  }
                }
                if (skipThisOne)
                  continue;
                // We want to play the next available airing; some airings between here and there
                // can be missing, so skip ahead. Eventually, we should hit the head one. This will
                // give the user the expected experience of playing through to the next airing.
                MediaFile watchMe = null;
                boolean nextHasEnded = false;
                Airing currAiring = currFile.getContentAiring();
                if(Sage.DBG) System.out.println("Current airing has ended, looking for next." + currAiring);
                long now = Sage.time();
                Airing nextAir = Wizard.getInstance().getTimeRelativeAiring(currAiring, 1);
                long altStartTime = 0;
                while(nextAir!=currAiring) {
                  if((watchMe = Wizard.getInstance().getFileForAiring(nextAir)) != null) {
                    if(nextAir.getSchedulingEnd() <= Sage.time()) {
                      nextHasEnded = true;
                    }
                    altStartTime = Math.max(currFile.getRecordEnd(), watchMe.getRecordTime());
                    if(Sage.DBG) System.out.println("Found airing with files:" + nextAir + "and is live:" + !nextHasEnded +
                        " watchMe=" + watchMe);
                    break;
                  }
                  if(nextAir.getSchedulingEnd() > now) {
                    if(Sage.DBG) System.out.println("Crossed live and couldn't find anything to play: " + nextAir);
                    break;
                  }
                  if(Sage.DBG) System.out.println("Next airing has is missing; searching ahead:" + nextAir);
                  currAiring = nextAir;
                  nextAir = Wizard.getInstance().getTimeRelativeAiring(currAiring, 1);
                }

                // Fall-back; if we don't have something to watch, watch the currently recording file.
                if(watchMe == null) {
                  watchMe = seek.getCurrRecordFileForClient(uiMgr, false);
                  if (watchMe == currFile)
                  {
                    if (Sage.DBG) System.out.println("Getting the current record file from Seeker yielded the same file w/out the sync lock, retry with the lock...");
                    watchMe = seek.getCurrRecordFileForClient(uiMgr);
                  }
                }
                if (Sage.DBG) System.out.println("watchMe=" + watchMe + " currFile=" + currFile);

                // NOTE: There's a serious problem here. If the VF detects a file is stopped
                // by its request to watch something new before that job is put into the watch queue
                // then it may end up requesting that new watch automatically here along with
                // a successive request when the actual VF.watch call completes above.
                // UPDATE: 2/7/06 - If it's a short show, don't lose live control because of that.
                // This fix would require changing 10000 to 30*60*1000, but we need to test it first!!!!
                if (watchMe != currFile && watchMe != null)
                {
                  // Playlists can potentially have live content in them, so we still need
                  // to check on that
                  if (pcAiringCheck(watchMe.getContentAiring()))
                  {
                    notifyPlaybackFinished();
                    int[] error = new int[1];
                    if(!nextHasEnded)
                      seek.requestWatch(watchMe.getContentAiring(), error, uiMgr);
                    else
                      seek.requestWatch(watchMe, error, uiMgr);
                    if(error[0] == WATCH_FAILED_PARENTAL_CHECK_FAILED) {
                      watchQueue.insertElementAt(new VFJob(STD_COMPLETE), 0);
                    } else {
                      // When we go to the next file live we want to be at the start of it
                      watchQueue.insertElementAt(new VFJob(TIME_SET, (altStartTime == 0) ? watchMe.getRecordTime() : altStartTime), 0);
                      watchQueue.insertElementAt(new VFJob(WATCH_MF, watchMe, playlistChain.isEmpty() ?
                          null : ((Playlist) playlistChain.firstElement())), 0);
                    }
                  }
                  else
                    watchQueue.insertElementAt(new VFJob(STD_COMPLETE), 0);
                  waitTime = -1;
                }
                else if (!playbackDone && segment == currFile.getNumSegments() - 1)
                {
                  // MR start/stop w/ extenders can cause this to get hit, so if we're not on the last segment don't do the std complete
                  watchQueue.insertElementAt(new VFJob(STD_COMPLETE), 0);
                  waitTime = -1;
                }
                else // Don't resubmit STD_COMPLETE jobs if we've already done one for them.
                  waitTime = 0;
              }
              else if (!playbackDone)
              {
                watchQueue.insertElementAt(new VFJob(STD_COMPLETE), 0);
                waitTime = -1;
              }
              else // Don't resubmit STD_COMPLETE jobs if we've already done one for them.
              {
                waitTime = 0;
                // Check if we're at the end of a playlist's playback and if there's new files that have
                // been added to this playlist
                if (watchQueue.isEmpty() && playbackDone && !playlistChain.isEmpty())
                {
                  if (Sage.DBG) System.out.println("Playlist playback is done....checking for more items in the Playlist");
                  boolean currRandom = isRandomPlaylistPlayback();
                  boolean currRepeat = isRepeatPlaylistPlayback();
                  if (currRandom)
                    playbackNextRandomPlaylist();
                  else
                    watchNextInPlaylist();
                }
              }
              /*
               * NOTE ON ABOVE
               * 12/24/04 - I added back the !playbackDone check because when you hit an EOS isPlayin() still
               * returns true, and we'd keep cycling through this loop infinitely.
               */
              continue;
            }
            else if (waitTime <= 0 && currFile.isMusic())
              waitTime = 50;// don't spin so much
          }


          if (player != null)
          {
            // Account for subtitle handler updates in the wait time
            if (subtitleComp != null)
            {
              if (subHandler == null || !subHandler.isEnabled())
              {
                subtitleComp.setText("");
              }
              else
              {
                // bitmap subtitles want to be prebuffered on the client, so send them 2 seconds ahead
                final long subTime = baseTime + (subHandler.areTextBased() ? 0 : 2000);
                long ttu = subHandler.getTimeTillUpdate(subTime);
                if (Sage.DBG) System.out.println("VF ttu for sub=" + ttu);
                if (ttu <= 0)
                {
                  if (subHandler.areTextBased())
                  {
                    final String newText = subHandler.getSubtitleText(subTime, true);
                    if (Sage.DBG && ccSubDebug) System.out.println("VF is updating the subtitle text now: " + newText);
                    uiMgr.getRouter().invokeLater(new Runnable()
                    {
                      public void run()
                      {
                        ZLabel temp = subtitleComp;
                        if (temp != null)
                        {
                          boolean[] acquiredLock = new boolean[1];
                          uiMgr.getLock(true, acquiredLock, false);
                          try
                          {
                            temp.setText(newText);
                          }
                          finally
                          {
                            if (acquiredLock[0])
                              uiMgr.clearLock();
                          }
                        }
                      }
                    });
                  }
                  else if (isPlayin()) // don't do dvdsub updates if we're paused cause they can backup
                  {
                    if (subpicBuff == null)
                      subpicBuff = new byte[8192];
                    int currSize = subHandler.getSubtitleBitmap(subTime, true, subpicBuff, true);
                    if (currSize > subpicBuff.length)
                    {
                      if (Sage.DBG && ccSubDebug) System.out.println("Reallocating subpicture bitmap buffer to be larger than " + currSize);
                      subpicBuff = new byte[currSize * 3 / 2];
                      subHandler.getSubtitleBitmap(subTime, true, subpicBuff, true);
                    }
                    if (player instanceof MiniPlayer)
                    {
                      MiniPlayer mp = (MiniPlayer) player;
                      if (!subHandler.isPaletteInitialized())
                      {
                        if (Sage.DBG && ccSubDebug) System.out.println("VF is sending the palette to initilize the bitmap subpicture display");
                        byte[] palette = subHandler.getPaletteData();
                        mp.sendSubpicPalette(palette);
                      }
                      if (Sage.DBG && ccSubDebug) System.out.println("VF sending new bitmap subtitle data of size " + currSize + " firstData=" +
                          Integer.toString(subpicBuff[0] & 0xFF, 16) + "," + Integer.toString(subpicBuff[1] & 0xFF, 16) + "," +
                          Integer.toString(subpicBuff[2] & 0xFF, 16) + "," + Integer.toString(subpicBuff[3] & 0xFF, 16));
                      mp.sendSubpicBitmap(subpicBuff, currSize, subHandler.getSubtitleBitmapFlags());
                    }
                  }
                  ttu = Math.max(1, subHandler.getTimeTillUpdate(subTime));
                  if (Sage.DBG && ccSubDebug) System.out.println("VF ttu after for sub=" + ttu);
                }
                if (waitTime == 0 && isPlayin())
                  waitTime = ttu;
                else
                  waitTime = Math.min(waitTime, ttu);
              }
            }
            // Account for subtitle handler updates in the wait time
            if (ccComp != null)
            {
              if (ccHandler == null || !ccHandler.isEnabled())
                ccComp.clearCC();
              else
              {
                final long subTime = baseTime;
                long ttu = ccHandler.getTimeTillUpdate(subTime);
                if (Sage.DBG) System.out.println("VF ttu for cc=" + ttu);
                if (ttu <= 0)
                {
                  if (Sage.DBG && ccSubDebug) System.out.println("VF is updating the CC text now");
                  final boolean is708 = ccHandler.is708();
                  final char[][] ccData = ccHandler.getCCDisplayData(subTime);
                  final long[][] cellData = ccHandler.get708CellFormat(subTime);
                  final RollupAnimation animationObject = ccHandler.getAnimationObject();
                  uiMgr.getRouter().invokeLater(new Runnable()
                  {
                    public void run()
                    {
                      ZCCLabel temp = ccComp;
                      if (temp != null)
                      {
                        boolean[] acquiredLock = new boolean[1];
                        uiMgr.getLock(true, acquiredLock, false);
                        try
                        {
                          temp.setCCData(ccData, cellData, animationObject, is708);
                        }
                        finally
                        {
                          if (acquiredLock[0])
                            uiMgr.clearLock();
                        }
                        //temp.setText(newText);
                      }
                    }
                  });
                  ttu = Math.max(1, ccHandler.getTimeTillUpdate(subTime));
                  if (Sage.DBG && ccSubDebug) System.out.println("VF ttu after for cc=" + ttu);
                }
                if (waitTime == 0 && isPlayin())
                  waitTime = ttu;
                else
                  waitTime = Math.min(waitTime, ttu);
              }
            }

            double rateTimer = player.getPlaybackRate();
            if (waitTime > 1 && rateTimer != 1 && rateTimer > 0.001)
            {
              if (Sage.DBG) System.out.println("VF applying rate adjustment to wait of " + rateTimer);
              if (currFile.isRecording())
              {
                // Recalculate the rate because it's how long it'll take us to get to live, not how long it'll
                // take us to get to the end of the program
                waitTime = getRealDurMillis() - getBaseMediaTimeMillis(false);
                // subtract one because the current time is always increasing
                waitTime = Math.round(waitTime/(rateTimer - 1));
              }
              else
                waitTime = Math.round(waitTime/rateTimer);
              if (waitTime > 500)
              {
                // Since we're not too accurate on this
                waitTime /= 2;
              }
              waitTime = Math.max(1, waitTime);

              // Check for getting close to live in FF mode which means we go back to x1 speed play
              if (player instanceof MiniPlayer && ((MiniPlayer) player).supportsTrickPlayEOS())
              {
                if (((MiniPlayer) player).hitTrickPlayEOS())
                {
                  // Retain speed if we're not hitting the end of the stream; go ahead and seam.
                  if (segment < (currFile.getNumSegments() - 1) && !currFile.shouldIWaitForData())
                  {
                    if (Sage.DBG) System.out.println(
                        "Remote pusher signaled the end; seaming to next (isRec="
                            + currFile.isRecording() + ", seg=" + segment + ", numSegs="
                            + currFile.getNumSegments() + ", waitData="
                            + currFile.shouldIWaitForData());
                    watchQueue.insertElementAt(new VFJob(SEAM_TO_NEXT), 0);
                    waitTime = -1;
                    continue;
                  }
                  else
                  {
                    if (Sage.DBG) System.out.println("Setting playback rate to 1 from FF/REW since the remote pusher signaled the end");
                    setRate(1);
                  }
                }
              }
              else if (currFile.isRecording() && rateTimer > 1 && getMediaTimeMillis() >= Sage.time() -
                  Math.max(getEncodeToPlaybackDelay()+2000, uiMgr.getLong(prefs + TIME_BEHIND_LIVE_TO_DISABLE_FAST_FORWARD, 6000)))
              {
                if (Sage.DBG) System.out.println("Setting playback rate to 1 since FF got close to live");
                setRate(1);
              }
            }
            else if (waitTime > 1 && rateTimer < -0.001)
            {
              if (Sage.DBG) System.out.println("VF applying rate adjustment to wait of " + rateTimer);
              // Recalculate the wait time since it'll be wrong because it assumes forward play
              baseTime = getBaseMediaTimeMillis(false);
              waitTime = Math.round(baseTime / -rateTimer);
              if (waitTime > 2000)
              {
                // Since we're not too accurate on this
                waitTime /= 2;
              }
              waitTime = Math.max(1, waitTime);
              // Check for getting close to 0 during a rewind, and in that case pause, unless we're not the first segment
              // which means we should move back a segment
              if (baseTime < 1000 || (player instanceof MiniPlayer && ((MiniPlayer) player).supportsTrickPlayEOS() &&
                  ((MiniPlayer)player).hitTrickPlayEOS()))
              {
                if (segment > 0)
                {
                  if (Sage.DBG) System.out.println("Jumping to previous segment so REW can continue");
                  timeJump(currFile.getEnd(segment - 1) - 1);
                  setRate((float)rateTimer);
                }
                else
                {
                  if (Sage.DBG) System.out.println("Resetting play rate to 1 since the beginning of the file was reached");
                  setRate(1);
                  play();
                }
              }
            }
          }
        }

        if (currJob == null) continue;

        // Clear out all requests that appear if there's another WATCH_MF or CLOSE_MF later in the queue
        int lastWatchIdx = -1;
        for (int i = watchQueue.size() - 1; i >= 0; i--)
        {
          int testid = ((VFJob) watchQueue.elementAt(i)).id;
          if (testid == WATCH_MF || testid == CLOSE_MF || testid == START_PLAYLIST)
          {
            lastWatchIdx = i;
            break;
          }
        }
        int extraJobs = 0;
        while (lastWatchIdx - extraJobs > 0)
        {
          VFJob loser = (VFJob) watchQueue.firstElement();
          // Don't remove the channel tune requests because this may be part of channel setup where we have to
          // tune, then close the file and then reopen it in order to access the live stream properly. So we don't
          // want to remove a channel tune that's before that close operation in that case.
          if (loser.id != CHANNEL_TUNE)
          {
            if (Sage.DBG) System.out.println("VF clearing out job because of new watch " + loser);
            if (loser.id == WATCH_MF || loser.id == LOAD_MF)
              loser.file.cleanupLocalFile();
            watchQueue.remove(0);
            lastWatchIdx--;
          }
          else
            extraJobs++;
        }
        if (lastWatchIdx == 0)
          currJob = (VFJob) watchQueue.firstElement();

        waitTime = -1;
        if (currJob.id == WATCH_MF)
        {
          if (currFile == currJob.file && currFileLoaded && currFile.generalType != MediaFile.MEDIAFILE_LOCAL_PLAYBACK)
          {
            if (Sage.DBG) System.out.println("VF Removing redundant watch request in queue processing");
            watchQueue.remove(currJob);
            continue;
          }
          if (!currJob.file.verifyFiles(true))
          {
            if (Sage.DBG) System.out.println("Aborting watch because files are invalid for " + currJob.file);
            watchQueue.remove(currJob);
            currJob.file.cleanupLocalFile();
            continue;
          }
          fileReadyTime = 0;
        }
        else if (currJob.id == LOAD_MF)
        {
          // Check for network encoder playback to change the timeshift delay
          networkEncoderPlayback = false;
          CaptureDevice matchingDevice = currJob.file.guessCaptureDeviceFromEncoding();
          extraEncodingDelay = 0;
          if (matchingDevice != null)
          {
            networkEncoderPlayback = matchingDevice.isNetworkEncoder();
            if (Sage.DBG) System.out.println("VF network encoder playback detected: " + networkEncoderPlayback);
          }

          // Check if the file's ready to be loaded yet. It's ready if it's not recording, or
          // it is recording and the newest segment has data on disk already.
          if (currJob.file.isRecording() && !isLivePreview())
          {
            long fixedRecordTime = currJob.file.getStart(currJob.file.getNumSegments() - 1);
            if (currJob.file == lookForRecordTimeAdjust && recordTimeAdjust > 0)
              fixedRecordTime = recordTimeAdjust;
            // First check is if encoder hasn't switched over to the new file yet, second is for size
            long liveDelayWait = getEncodeToPlaybackDelay() - (Sage.time() - fixedRecordTime);

            if (liveDelayWait > 0 || currJob.file.shouldIWaitToStartPlayback())
            {
              if (currJob.file != lookForRecordTimeAdjust)
              {
                recordTimeAdjust = 0;
                lookForRecordTimeAdjust = currJob.file;
              }
              else
              {
                if (recordTimeAdjust == 0 && !currJob.file.shouldIWaitToStartPlayback())
                {
                  recordTimeAdjust = Sage.time();
                  if (Sage.DBG) System.out.println("Actual record time was:" + Sage.df(recordTimeAdjust));
                  extraEncodingDelay = recordTimeAdjust - currJob.file.getStart(currJob.file.getNumSegments() - 1);
                  if (Sage.DBG) System.out.println("Set the additional encoding delay to be:" + extraEncodingDelay);
                }
              }
              if (!currJob.file.verifyFiles(true))
              {
                if (Sage.DBG) System.out.println("VF Removing LOAD_MF request because its files failed verification");
                watchQueue.remove(currJob);
                currJob.file.cleanupLocalFile();
                // NOTE: This can happen if you try to select something to watch at the last second. Only a few KB will
                // get recorded and the file will fail verification. But since we're under live control we should move
                // onto what's next instead of just abandoning the file load altogether
                if (liveControl)
                {
                  if (Sage.DBG) System.out.println("VideoFrame watching a live old airing(2), fixing it.");
                  MediaFile watchMe = seek.getCurrRecordFileForClient(uiMgr, false);
                  if (watchMe == currFile)
                  {
                    if (Sage.DBG) System.out.println("Getting the current record file from Seeker yielded the same file w/out the sync lock, retry with the lock...");
                    watchMe = seek.getCurrRecordFileForClient(uiMgr);
                  }
                  long altStartTime = 0;
                  if (watchMe == null)
                  {
                    // Maybe another tuner is recording the next file on that we should be showing...so check for that as well.
                    Airing nextAir = Wizard.getInstance().getTimeRelativeAiring(currFile.getContentAiring(), 1);
                    if (Sage.DBG) System.out.println("Didn't find next MediaFile being recorded on current tuner; check for another MediaFile for the airing=" + nextAir);
                    if (nextAir != null)
                    {
                      watchMe = Wizard.getInstance().getFileForAiring(nextAir);
                      // Since we're switching tuners there may be start padding on the other show which we should skip over...so check for that difference here
                      if (watchMe != null)
                        altStartTime = Math.max(currFile.getRecordEnd(), watchMe.getRecordTime());
                    }
                  }
                  if (Sage.DBG) System.out.println("watchMe=" + watchMe);

                  // NOTE: There's a serious problem here. If the VF detects a file is stopped
                  // by its request to watch something new before that job is put into the watch queue
                  // then it may end up requesting that new watch automatically here along with
                  // a successive request when the actual VF.watch call completes above.
                  // UPDATE: 2/7/06 - If it's a short show, don't lose live control because of that.
                  // This fix would require changing 10000 to 30*60*1000, but we need to test it first!!!!
                  if (watchMe != currFile && watchMe != null &&
                      watchMe.getRecordTime() - currFile.getRecordEnd() < 30*60*1000)
                  {
                    // The 10sec diff covers the NOTE from above
                    // Playlists can potentially have live content in them, so we still need
                    // to check on that
                    // we need to call the Airing version so live watch tracking works right
                    if (pcAiringCheck(watchMe.getContentAiring()))
                    {
                      int[] error = new int[1];
                      seek.requestWatch(watchMe.getContentAiring(), error, uiMgr);
                      if(error[0] == WATCH_FAILED_PARENTAL_CHECK_FAILED) {
                        watchQueue.insertElementAt(new VFJob(STD_COMPLETE), 0);
                      } else {
                        // When we go to the next file live we want to be at the start of it
                        watchQueue.insertElementAt(new VFJob(TIME_SET, (altStartTime == 0) ? watchMe.getRecordTime() : altStartTime), 0);
                        watchQueue.insertElementAt(new VFJob(WATCH_MF, watchMe, playlistChain.isEmpty() ?
                            null : ((Playlist) playlistChain.firstElement())), 0);
                      }
                    }
                    else
                      watchQueue.insertElementAt(new VFJob(STD_COMPLETE), 0);
                    waitTime = -1;
                  }
                  else if (!playbackDone && segment == currFile.getNumSegments() - 1)
                  {
                    watchQueue.insertElementAt(new VFJob(STD_COMPLETE), 0);
                    waitTime = -1;
                  }
                  else // Don't resubmit STD_COMPLETE jobs if we've already done one for them.
                    waitTime = 0;
                }
                else if (!playbackDone)
                {
                  watchQueue.insertElementAt(new VFJob(STD_COMPLETE), 0);
                  waitTime = -1;
                }
                continue;
              }
              /*
               * NOTE: If we get stuck in this loop, then there's something wrong with the
               * encoder since it's not producing any data.
               */
              if (Sage.DBG) System.out.println("VF waiting for data to appear in new file...liveWait=" + liveDelayWait);
              if (liveDelayWait < -1*uiMgr.getInt("videoframe/time_wait_for_signal_loss", 7500) && !signalLost)
              {
                if (Sage.DBG) System.out.println("SIGNAL LOSS has been detected by the MediaPlayer");
                signalLost = true;
                Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
              }
              else
              {
                // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
                // in this situation
                uiMgr.getRouter().resetInactivityTimers();
              }
              if (recordTimeAdjust == 0)
                waitTime = LOAD_FILE_WAIT;
              else
                waitTime = Math.max(LOAD_FILE_WAIT, liveDelayWait);
              synchronized (queueLock)
              {
                // Check for another job after us that we want to promote, inactive file is the only one
                // JK 8/8/05 - I added CHANNEL_TUNE in case we're trying to load a TS file from an unavailable channel
                waitQueueSize = watchQueue.size();
                for (int i = 1; i < watchQueue.size(); i++)
                {
                  VFJob testJob = (VFJob) watchQueue.get(i);
                  if (testJob != null && (testJob.id == INACTIVE_FILE || testJob.id == CHANNEL_TUNE))
                  {
                    watchQueue.removeElementAt(i);
                    watchQueue.insertElementAt(testJob, 0);
                    waitQueueSize = -1;
                    break;
                  }
                }
              }
              continue;
            }

            if (matchingDevice != null && recordTimeAdjust == 0)
            {
              // We can't use instanceof because on SageTVClient the capture device object is different; so check the name instead
              String cdevname = matchingDevice.getCaptureDeviceName();
              if (cdevname.indexOf("MyPVR") != -1 || cdevname.indexOf("myTV.pvr") != -1)
              {
                //							boolean isFresh = ((MacTrinityCaptureDevice)matchingDevice).isFreshCapture();
                if (Sage.DBG) System.out.println("Mac capture detected");// fresh=" + isFresh);
                extraEncodingDelay = 4000; //isFresh ? 4000 : 0;
              }
            }
            /*else if (fileReadyTime == 0)
					{
						fileReadyTime = System.currentTimeMillis();
						waitTime = 500L;//NEWFILE_INIT_WAIT;
						continue;
					}
					else
					{
						long tdiff = System.currentTimeMillis() - fileReadyTime;
						if (tdiff < NEWFILE_INIT_WAIT)
						{
							waitTime = Math.min(NEWFILE_INIT_WAIT - tdiff, 500L);//NEWFILE_INIT_WAIT - tdiff;
							continue;
						}
					}*/
          }
          else if (currJob.file.getNumSegments() > 0 && FileDownloader.isDownloading(currJob.file.getFile(0)) && currJob.file.shouldIWaitToStartPlayback())
          {
            if (Sage.DBG) System.out.println("VF waiting on format detection of downloaded file before proceeding...");
            waitTime = LOAD_FILE_WAIT;
            continue;
          }
          if (signalLost)
            Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
          signalLost = false;
          if (!currJob.file.verifyFiles(true))
          {
            if (Sage.DBG) System.out.println("VF Removing LOAD_MF request because its files failed verification");
            currJob.file.cleanupLocalFile();
            watchQueue.remove(currJob);
            continue;
          }
        }
        else if (currJob.id == WATCH_COMPLETE_CHECK)
        {
          // This doesn't need to be done on the UI thread for any reason
          if (Sage.DBG) System.out.println("VF Watch Complete Check currFile=" + currFile + " loggedWatch=" + loggedWatch);
          if (currFile != null && !loggedWatch && !currFile.isAnyLiveStream() && Permissions.hasPermission(Permissions.PERMISSION_WATCHEDTRACKING, uiMgr))
          {
            Airing doneAir = currFile.getContentAiring();
            if (doneAir != null && currFile.isTV())
            {
              if (BigBrother.setWatched(doneAir, currFile.getRecordTime(), getMediaTimeMillis(),
                  realWatchStart, Sage.time(), true))
              {
                loggedWatch = true;
                BigBrother.setWatched(doneAir, currFile.getRecordTime(), getMediaTimeMillis(),
                    realWatchStart, Sage.time(), false);
              }
            }
          }
          watchQueue.remove(currJob);
          continue;
        }
        else if (currJob.id == CHANNEL_TUNE)
        {
          // This doesn't need to be done on the UI thread for any reason
          // NOTE: I DETERMINED THE SYNC ON THE SEEKER TO BE SAFE BECAUSE ITS NO different
          // than calling requestWatch from this thread; and we've been doing that safely for a long time
          while (watchQueue.size() > 1 && (((VFJob) watchQueue.get(1)).id == CHANNEL_TUNE))
          {
            watchQueue.remove(0);
            currJob = (VFJob) watchQueue.firstElement();
          }

          if (currFile == currJob.file)
          {
            // If there's successive tune requests, then skip them and just go to the last one.
            seek.forceChannelTune(currFile.encodedBy, currJob.inactiveFilename/*chanNum*/, uiMgr);
            Catbert.processUISpecificHook("MediaPlayerFileLoadComplete", new Object[] { currFile, Boolean.TRUE }, uiMgr, true);
            // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
            // in this situation
            uiMgr.getRouter().resetInactivityTimers();
            watchQueue.remove(currJob);
            continue;
          }
        }

        // Remove bogus operations
        if (currFile == null && (currJob.id == PAUSE || currJob.id == PLAY || currJob.id == TIME_SET ||
            currJob.id == TIME_ADJUST || currJob.id == RATE_SET || currJob.id == RATE_ADJUST ||
            currJob.id == SEAM_TO_NEXT || currJob.id == SEGMENT_ADJUST || currJob.id == INACTIVE_FILE ||
            currJob.id == WATCH_COMPLETE_CHECK || currJob.id == PLAY_PAUSE || currJob.id == DIRECT_CONTROL_MSG ||
            currJob.id == RELOAD_MF || currJob.id == CHANNEL_TUNE || currJob.id == SMOOTH_FORWARD || currJob.id == SMOOTH_REVERSE ||
            currJob.id == RELOAD_PREPARE_MF))
        {
          if (Sage.DBG) System.out.println("VF discarding job " + currJob);
          watchQueue.remove(currJob);
          continue;
        }

        while ((currJob.id == TIME_ADJUST || currJob.id == TIME_SET) && watchQueue.size() > 1)
        {
          VFJob nextJob = (VFJob) watchQueue.get(1);
          if (nextJob.id == currJob.id)
          {
            // Coalesce the jobs
            if (currJob.id == TIME_SET)
              currJob.time = nextJob.time;
            else
              currJob.time += nextJob.time;
          }
          else
            break;
          synchronized (queueLock)
          {
            watchQueue.remove(1);
            queueLock.notifyAll();
          }
        }

        final VFJob finalJob = currJob;
        if (Sage.WINDOWS_OS && rethreadVF /*&& !(uiMgr.getRootPanel().getRenderEngine() instanceof JOGLSageRenderer)*/ &&
            !(player instanceof LinuxMPlayerPlugin))
        {
          if (finalJob.useSyncClose)
          {
            syncCloseToExecuteNow = finalJob;
            synchronized (queueLock)
            {
              queueLock.notifyAll();
              while (watchQueue.contains(finalJob))
              {
                try{queueLock.wait(1000);}catch(Exception e){}
              }
            }
          }
          else
          {
            try
            {
              asyncJobInWait = finalJob;
              java.awt.EventQueue.invokeAndWait(new Runnable()
              {
                public void run() { processJob(finalJob); }
              });
            }
            catch (InterruptedException e){}
            catch (java.lang.reflect.InvocationTargetException e1)
            {
              System.out.println("VF ERROR in processing:" + e1 + " target=" + e1.getTargetException());
              e1.printStackTrace();
            }
            finally
            {
              asyncJobInWait = null;
            }
          }
        }
        else
          processJob(finalJob);

      }
      catch (Throwable terr)
      {
        System.out.println("VIDEO FRAME EXCEPTION THROWN:" + terr);
        Sage.printStackTrace(terr);
      }
    }
    if (Sage.DBG) System.out.println("VideoFrame thread is now exiting");
    if (Sage.WINDOWS_OS)
    {
      goodbye0();
      java.awt.EventQueue.invokeLater(new Runnable()
      {
        public void run() { goodbye0(); }
      });
    }
  }

  private long getEncodeToPlaybackDelay()
  {
    /*return (networkEncoderPlayback ?
				uiMgr.getLong(prefs + NETWORK_ENCODING_TO_PLAYBACK_DELAY, Sage.WINDOWS_OS ? 1500 : 2500) :
				uiMgr.getLong(prefs + LOCAL_ENCODING_TO_PLAYBACK_DELAY, Sage.WINDOWS_OS ? 0 : 2000));*/
    long rv = 0;
    if (networkEncoderPlayback)
    {
      rv = uiMgr.getLong(prefs + NETWORK_ENCODING_TO_PLAYBACK_DELAY, 1500);
    }
    else
    {
      rv = uiMgr.getLong(prefs + LOCAL_ENCODING_TO_PLAYBACK_DELAY, 0);
    }

    rv += extraEncodingDelay;

    // Now add the delay from our player
    if (uiMgr.getUIClientType() == UIClient.REMOTE_UI &&
        uiMgr.getRootPanel() != null && uiMgr.getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
    {
      rv += ((MiniClientSageRenderer)uiMgr.getRootPanel().getRenderEngine()).getMediaPlayerDelay();
    }
    else if (Sage.MAC_OS_X && uiMgr.getUIClientType() == UIClient.LOCAL)
    {
      // FIXME: eliminate for MacNativeCaptureDevices, this is only needed for the slow Trinity devices
      rv += 3000; // this delay was in the MiniMPlayerPlugin and is lost in local mode since we don't have the above setting
    }
    return rv;
  }

  private void processJob(VFJob daJob)
  {
    if (!watchQueue.contains(daJob))
    {
      if (Sage.DBG) System.out.println("VF ignoring job because it's already been removed from the queue:" + daJob);
      return;
    }
    if (Sage.DBG) System.out.println("VF processing on UI Thread " + daJob);
    if (daJob.id == SEAM_TO_NEXT)
    {
      // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
      // in this situation
      uiMgr.getRouter().resetInactivityTimers();
      //System.out.println("VideoFrame seamer is engaging.");
      // Verify the file first in case there are invalid segments in it that need to be removed
      currFile.verifyFiles(true);
      float oldRate = getRate();
      timeSelected(currFile.getStart(segment + 1), true);
      if (oldRate != 1.0f)
        setRate(oldRate);
      lastVideoOpTime = Sage.eventTime();

      watchQueue.remove(daJob);
      sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYBACK_SEGMENTCHANGE,
          new Object[] {
          sage.plugin.PluginEventManager.VAR_MEDIAFILE, currFile,
          sage.plugin.PluginEventManager.VAR_UICONTEXT, uiMgr.getLocalUIClientName(),
          sage.plugin.PluginEventManager.VAR_DURATION, new Long(getDurationMillis()),
          sage.plugin.PluginEventManager.VAR_MEDIATIME, new Long(getMediaTimeMillis()),
          sage.plugin.PluginEventManager.VAR_MEDIASEGMENT, new Integer(segment),
          sage.plugin.PluginEventManager.VAR_CHAPTERNUM, new Integer(getDVDChapter()),
          sage.plugin.PluginEventManager.VAR_TITLENUM, new Integer(getDVDTitle())
      });
    }
    else if (daJob.id == PAUSE)
    {
      if (!currFile.isLiveStream() && player != null)
      {
        // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
        // in this situation
        uiMgr.getRouter().resetInactivityTimers();
        lastVideoOpTime = Sage.eventTime();
        if (player.getState() != MediaPlayer.PAUSE_STATE)
        {
          player.setPlaybackRate(1);
          player.pause();
          notifyPlaybackPaused();
        }
        else // frame step
        {
          if (muteOnAlt)
            player.setMute(true);
          player.setPlaybackRate(1);
          player.frameStep(1);
        }
        Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
      }
      watchQueue.remove(daJob);
    }
    else if (daJob.id == PLAY)
    {
      if (!currFile.isLiveStream() && player != null)
      {
        if (player.getState() != MediaPlayer.PLAY_STATE)
        {
          lastVideoOpTime = Sage.eventTime();
          player.setPlaybackRate(1);
          player.setMute(globalMute);
          player.play();
          notifyPlaybackResumed();
        }
        else if (player.getPlaybackRate() != 1)
        {
          lastVideoOpTime = Sage.eventTime();
          player.setMute(globalMute);
          player.setPlaybackRate(1);
          notifyPlaybackRateChange(player.getPlaybackRate());
        }
        Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
        // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
        // in this situation
        uiMgr.getRouter().resetInactivityTimers();
      }
      watchQueue.remove(daJob);
    }
    else if (daJob.id == PLAY_PAUSE)
    {
      if (!currFile.isLiveStream() && player != null)
      {
        lastVideoOpTime = Sage.eventTime();
        if (player.getState() == MediaPlayer.PLAY_STATE)
        {
          // Without an independent play button, this is the only way
          // to get back to x1 play easily, so check for that.
          if (player.getPlaybackRate() != 1)
          {
            player.setMute(globalMute);
            player.setPlaybackRate(1);
            notifyPlaybackRateChange(player.getPlaybackRate());
          }
          else
          {
            player.pause();
            notifyPlaybackPaused();
          }
        }
        else
        {
          player.setPlaybackRate(1);
          player.setMute(globalMute);
          player.play();
          notifyPlaybackResumed();
        }
        Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
        // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
        // in this situation
        uiMgr.getRouter().resetInactivityTimers();
      }
      watchQueue.remove(daJob);
    }
    else if (daJob.id == TIME_ADJUST)
    {
      if (!currFile.isLiveStream() && player != null)
      {
        if (player.getPlaybackRate() != 1 && Sage.getBoolean("videoframe/reset_playback_rate_on_seek", true))
        {
          lastVideoOpTime = Sage.eventTime();
          player.setMute(globalMute);
          player.setPlaybackRate(1);
          notifyPlaybackRateChange(player.getPlaybackRate());
          Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
        }
        if (player.getState() == MediaPlayer.PAUSE_STATE && muteOnAlt)
          player.setMute(true);
        if (daJob.time > 0)
        {
          if (getMediaTimeMillis() <= Sage.time() - Math.max(getEncodeToPlaybackDelay()+ 2000, uiMgr.getLong(prefs + TIME_BEHIND_LIVE_TO_DISABLE_SKIP_FORWARD,4000))
              || !currFile.isRecording()) {
            lastVideoOpTime = Sage.eventTime();
            timeSelected(getMediaTimeMillis() + daJob.time, true);
          }
        }
        else
        {
          lastVideoOpTime = Sage.eventTime();
          timeSelected(getMediaTimeMillis() + daJob.time, false);
        }
        notifyPlaybackSeek();
        Catbert.processUISpecificHook("MediaPlayerSeekCompleted", null, uiMgr, true, SEEK_COMPLETE_HOOK_DELAY);
        // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
        // in this situation
        uiMgr.getRouter().resetInactivityTimers();
      }
      watchQueue.remove(daJob);
    }
    else if (daJob.id == TIME_SET)
    {
      if (!currFile.isLiveStream() && player != null)
      {
        if (player.getPlaybackRate() != 1 && Sage.getBoolean("videoframe/reset_playback_rate_on_seek", true))
        {
          lastVideoOpTime = Sage.eventTime();
          player.setMute(globalMute);
          player.setPlaybackRate(1);
          Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
        }
        if (player.getState() == MediaPlayer.PAUSE_STATE && muteOnAlt)
          player.setMute(true);
        long currTime = getMediaTimeMillis();

        if (daJob.time < currTime || currTime <=
            Sage.time() - Math.max(getEncodeToPlaybackDelay() + 2000, uiMgr.getLong(prefs + TIME_BEHIND_LIVE_TO_DISABLE_SKIP_FORWARD, 4000))
            || !currFile.isRecording()) {
          lastVideoOpTime = Sage.eventTime();
          timeSelected(daJob.time, daJob.time > getMediaTimeMillis());
        }
        notifyPlaybackSeek();
        Catbert.processUISpecificHook("MediaPlayerSeekCompleted", null, uiMgr, true, SEEK_COMPLETE_HOOK_DELAY);
        // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
        // in this situation
        uiMgr.getRouter().resetInactivityTimers();
      }
      watchQueue.remove(daJob);
    }
    else if (daJob.id == DIRECT_CONTROL_MSG)
    {
      if (subHandler != null && !subHandler.mpControlled() &&
          (daJob.dvdControlCode == DVD_CONTROL_SUBTITLE_CHANGE || daJob.dvdControlCode == DVD_CONTROL_SUBTITLE_TOGGLE))
      {
        // Intercept this subtitle control command since we're handling these
        if (daJob.dvdControlCode == DVD_CONTROL_SUBTITLE_TOGGLE)
        {
          subHandler.setEnabled(!subHandler.isEnabled());
        }
        else
        {
          subHandler.setEnabled(true);
          String[] langs = subHandler.getLanguages();
          if (daJob.dvdParam1 >= 0)
          {
            subHandler.setCurrLanguage(langs[(int)daJob.dvdParam1]);
          }
          else
          {
            int currIdx = 0;
            for (int i = 0; i < langs.length; i++)
            {
              if (langs[i].equals(subHandler.getCurrLanguage()))
              {
                currIdx = i;
                break;
              }
            }
            currIdx = (currIdx + 1) % langs.length;
            subHandler.setCurrLanguage(langs[currIdx]);
          }
        }
        watchQueue.remove(daJob);
        Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
      }
      else if (chapterPoints != null && (daJob.dvdControlCode == DVD_CONTROL_CHAPTER_SET || daJob.dvdControlCode == DVD_CONTROL_CHAPTER_NEXT ||
          daJob.dvdControlCode == DVD_CONTROL_CHAPTER_PREV))
      {
        int targetChapter;
        if (daJob.dvdControlCode == DVD_CONTROL_CHAPTER_SET)
        {
          targetChapter = (int)daJob.dvdParam1;
        }
        else
        {
          int currChapter = getDVDChapter();
          if (daJob.dvdControlCode == DVD_CONTROL_CHAPTER_NEXT)
            targetChapter = currChapter + 1;
          else if (currChapter == 1 || getBaseMediaTimeMillis(true) - chapterPoints[currChapter - 1] > 7000)
            targetChapter = currChapter;
          else
            targetChapter = currChapter - 1;
        }
        watchQueue.remove(daJob);
        if (targetChapter <= chapterPoints.length) // If we're not actually going up a chapter, then don't do anything
        {
          targetChapter = Math.max(1, Math.min(chapterPoints.length, targetChapter)) - 1;
          long targetTime = chapterPoints[targetChapter] + currFile.getRecordTime();
          // We process the time seek this way so it works like they normally would and so its done before we call the hook below
          VFJob timeSetJob = new VFJob(TIME_SET, targetTime);
          watchQueue.add(timeSetJob);
          processJob(timeSetJob);
        }
        Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
      }
      else if (player instanceof DVDMediaPlayer)
      {
        DVDMediaPlayer dplayer = (DVDMediaPlayer) player;
        // Track the state so we can fire the hook if necessary
        if (oldcurrDVDTitle == -1)
        {
          oldcurrDVDTitle = dplayer.getDVDTitle();
          oldcurrDVDChapter = dplayer.getDVDChapter();
          oldcurrDVDDomain = dplayer.getDVDDomain();
          olddvdButtonsVisible = dplayer.areDVDButtonsVisible();
          oldcurrDVDAngle = dplayer.getDVDAngle();
          oldcurrDVDTotalAngles = dplayer.getDVDTotalAngles();
          oldcurrDVDLanguage = dplayer.getDVDLanguage();
          oldcurrDVDAvailableLanguages = dplayer.getDVDAvailableLanguages();
          oldcurrDVDSubpicture = dplayer.getDVDSubpicture();
          oldcurrDVDAvailableSubpictures = dplayer.getDVDAvailableSubpictures();
        }
        // If there were any user actions yet, skip the menu skipping
        if (daJob.dvdControlCode >= DVD_CONTROL_MENU && daJob.dvdControlCode <= DVD_CONTROL_CHAPTER_PREV)
          alreadySkippedDVDMenus = true;
        try
        {
          lastVideoOpTime = Sage.eventTime();
          String oldStream = null;
          long oldTime = -1;
          if (dplayer instanceof MiniDVDPlayerIdentifier && (daJob.dvdControlCode == DVD_CONTROL_AUDIO_CHANGE ||
              daJob.dvdControlCode == DVD_CONTROL_SUBTITLE_CHANGE || daJob.dvdControlCode == DVD_CONTROL_SUBTITLE_TOGGLE))
          {
            oldTime = getMediaTimeMillis();
            oldStream = (daJob.dvdControlCode == DVD_CONTROL_AUDIO_CHANGE) ? dplayer.getDVDLanguage() : dplayer.getDVDSubpicture();
          }
          dplayer.playControlEx(daJob.dvdControlCode, daJob.dvdParam1, daJob.dvdParam2);
          if (dplayer instanceof MiniDVDPlayerIdentifier && (daJob.dvdControlCode == DVD_CONTROL_AUDIO_CHANGE ||
              daJob.dvdControlCode == DVD_CONTROL_SUBTITLE_CHANGE || daJob.dvdControlCode == DVD_CONTROL_SUBTITLE_TOGGLE))
          {
            // if the audio/subtitle stream changed, then reseek back to where we were since it will have flushed the buffers as part of this
            String newStream = (daJob.dvdControlCode == DVD_CONTROL_AUDIO_CHANGE) ? dplayer.getDVDLanguage() : dplayer.getDVDSubpicture();
            if (newStream != oldStream && (newStream == null || !newStream.equals(oldStream)))
            {
              dplayer.seek(oldTime);
            }
          }
          // Check if we need to do the menu skip yet (mainly, if we even CAN do it)
          if (dvdResumeTarget != null)
          {
            if (Sage.DBG) System.out.println("Seeking DVD playback to the last watched point...." + dvdResumeTarget);
            if (uiMgr.getUIClientType() == UIClient.LOCAL && Sage.WINDOWS_OS)
            {
              dplayer.playControlEx(DVD_CONTROL_TITLE_SET, dvdResumeTarget.getTitleNum(), dvdResumeTarget.getTitleNum());
              dplayer.playControlEx(0, 0, 0); // to process any events
              timeSelected(dvdResumeTarget.getWatchEnd(), true);
              dvdResumeTarget = null;
            }
            else if (Sage.client && Sage.MAC_OS_X)
            {
              dplayer.playControlEx(DVD_CONTROL_RESUME_PLAYBACK, 0, 0);
              dvdResumeTarget = null;
            }
            else if (!dvdResumeTitleSetDone)
            {
              dplayer.playControlEx(DVD_CONTROL_TITLE_SET, dvdResumeTarget.getTitleNum(), dvdResumeTarget.getTitleNum());
              // Once the change from the title set hits then we'll get called again and can do the seek
              dvdResumeTitleSetDone = true;
            }
            else
            {
              timeSelected(dvdResumeTarget.getWatchEnd(), true);
              dvdResumeTarget = null;
              dvdResumeTitleSetDone = false;
            }
          }
          else if (currFile != null && currFile.isDVD() && !alreadySkippedDVDMenus && uiMgr.getBoolean(prefs + SKIP_DVD_MENUS, false))
          {
            if (Sage.DBG) System.out.println("DVD MENU SKIP attempt");
            // Attempt to do the chapter/title skip to the beginning of the DVD
            dplayer.playControlEx(DVD_CONTROL_TITLE_SET, 1, 1);
            alreadySkippedDVDMenus = true;
          }
        }
        catch (PlaybackException e)
        {
          if (Sage.DBG) e.printStackTrace();
          Catbert.processUISpecificHook("MediaPlayerError", new Object[] { Sage.rez("DVD"), e.getMessage() }, uiMgr, true);
        }
        watchQueue.remove(daJob);

        // If the info fields for the DVD have changed then fire the hook
        int newDVDTitle = dplayer.getDVDTitle();
        int newDVDChapter = dplayer.getDVDChapter();
        int newDVDDomain = dplayer.getDVDDomain();
        boolean newdvdButtonsVisible = dplayer.areDVDButtonsVisible();
        int newDVDAngle = dplayer.getDVDAngle();
        int newDVDTotalAngles = dplayer.getDVDTotalAngles();
        String newLang = dplayer.getDVDLanguage();
        String newSubpic = dplayer.getDVDSubpicture();
        String[] newLangs = dplayer.getDVDAvailableLanguages();
        String[] newSubs = dplayer.getDVDAvailableSubpictures();
        if (!alreadySelectedDefaultDVDSub && (newSubs != null && (oldcurrDVDAvailableSubpictures == null || oldcurrDVDAvailableSubpictures.length != newSubs.length)))
        {
          alreadySelectedDefaultDVDSub = selectDefaultSubpicLanguage();
        }
        if (!alreadySelectedDefaultDVDAudio && (newLangs != null && (oldcurrDVDAvailableLanguages == null || oldcurrDVDAvailableLanguages.length != newLangs.length)))
        {
          alreadySelectedDefaultDVDAudio = selectDefaultAudioLanguage();
        }
        if (newDVDTitle != oldcurrDVDTitle || newDVDChapter != oldcurrDVDChapter ||
            newDVDDomain != oldcurrDVDDomain || newdvdButtonsVisible != olddvdButtonsVisible ||
            newDVDAngle != oldcurrDVDAngle || newDVDTotalAngles != oldcurrDVDTotalAngles ||
            (newLang != oldcurrDVDLanguage && (newLang == null || !newLang.equals(oldcurrDVDLanguage))) ||
            (newSubpic != oldcurrDVDSubpicture && (newSubpic == null || !newSubpic.equals(oldcurrDVDSubpicture))) ||
            (oldcurrDVDAvailableLanguages != null && newLangs != null &&
            oldcurrDVDAvailableLanguages.length != newLangs.length) ||
            (oldcurrDVDAvailableSubpictures != null && newSubs != null &&
            oldcurrDVDAvailableSubpictures.length != newSubs.length))
        {
          Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
          // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
          // in this situation
          uiMgr.getRouter().resetInactivityTimers();
        }
        oldcurrDVDTitle = newDVDTitle;
        oldcurrDVDChapter = newDVDChapter;
        oldcurrDVDDomain = newDVDDomain;
        olddvdButtonsVisible = newdvdButtonsVisible;
        oldcurrDVDAngle = newDVDAngle;
        oldcurrDVDTotalAngles = newDVDTotalAngles;
        oldcurrDVDLanguage = newLang;
        oldcurrDVDSubpicture = newSubpic;
        oldcurrDVDAvailableLanguages = newLangs;
        oldcurrDVDAvailableSubpictures = newSubs;
      }
      else if (player instanceof DShowMediaPlayer)
      {
        // This is for detecting EOS events
        try
        {
          ((DShowMediaPlayer) player).processEvents();
        }
        catch (PlaybackException pe)
        {
          if (Sage.DBG) pe.printStackTrace();
          Catbert.processUISpecificHook("MediaPlayerError", new Object[] {
              Sage.rez("Playback"), pe.toString() }, uiMgr, true);
        }
        watchQueue.remove(daJob);
      }
      else
        watchQueue.remove(daJob);
    }
    else if (daJob.id == RATE_ADJUST || daJob.id == RATE_SET)
    {
      if (!currFile.isLiveStream() && player != null)
      {
        float newRate = (daJob.id == RATE_ADJUST) ? (player.getPlaybackRate() * daJob.rate) : daJob.rate;

        if (player.getState() == MediaPlayer.PLAY_STATE || (player.getState() == MediaPlayer.EOS_STATE && newRate < 1.0f))
        {
          if (newRate != 1 && muteOnAlt)
            player.setMute(true);
          lastVideoOpTime = Sage.eventTime();
          newRate = player.setPlaybackRate(newRate);
          if (newRate == 1 && !globalMute)
            player.setMute(false);
          notifyPlaybackRateChange(newRate);
          Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
          // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
          // in this situation
          uiMgr.getRouter().resetInactivityTimers();
        }
      }
      watchQueue.remove(daJob);
    }
    else if (daJob.id == SMOOTH_FORWARD || daJob.id == SMOOTH_REVERSE)
    {
      if (!currFile.isLiveStream() && player != null)
      {
        // For variable rate playback, we'll do 4, 16, 64 & 256 times for the four options
        float currRate = player.getPlaybackRate();
        float newRate;
        if (daJob.id == SMOOTH_FORWARD)
        {
          if (currRate < 4.0f)
            newRate = 4.0f;
          else if (currRate < 16.0f)
            newRate = 16.0f;
          else if (currRate < 64.0f)
            newRate = 64.0f;
          else if (currRate < 256.0f)
            newRate = 256.0f;
          else
            newRate = 1.0f;
        }
        else
        {
          if (currRate > -4.0f)
            newRate = -4.0f;
          else if (currRate > -16.0f)
            newRate = -16.0f;
          else if (currRate > -64.0f)
            newRate = -64.0f;
          else if (currRate > -256.0f)
            newRate = -256.0f;
          else
            newRate = 1.0f;
        }
        if (player.getState() == MediaPlayer.PLAY_STATE || (player.getState() == MediaPlayer.EOS_STATE && newRate < 1.0f))
        {
          if (newRate != 1 && muteOnAlt)
            player.setMute(true);
          lastVideoOpTime = Sage.eventTime();
          newRate = player.setPlaybackRate(newRate);
          if (newRate == 1 && !globalMute)
            player.setMute(false);
          notifyPlaybackRateChange(newRate);
          Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
          // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
          // in this situation
          uiMgr.getRouter().resetInactivityTimers();
        }
      }
      watchQueue.remove(daJob);
    }
    else if (daJob.id == SEGMENT_ADJUST)
    {
      if (!currFile.isLiveStream() && player != null)
      {
        /*				if (daJob.time > 0)
				{
					long nextTime = currFile.nextDeadAir(getMediaTimeMillis());
					if (nextTime != 0)
					{
						lastVideoOpTime = Sage.eventTime();
						timeSelected(nextTime, nextTime > getMediaTimeMillis());
					}
				}
				else
				{
					// Subtract a second so we don't keep hitting the same one repeatedly
					long prevTime = currFile.previousDeadAir(getMediaTimeMillis() -
						CUT_JUMP_BACKP);
					if (prevTime != 0)
					{
						lastVideoOpTime = Sage.eventTime();
						timeSelected(prevTime, prevTime > getMediaTimeMillis());
					}
				}
				Catbert.processUISpecificHook("MediaPlayerSeekCompleted", null, uiMgr, true, SEEK_COMPLETE_HOOK_DELAY);
				// Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
				// in this situation
				uiMgr.getRouter().resetInactivityTimers();*/
      }
      watchQueue.remove(daJob);
    }
    else if (daJob.id == CLOSE_MF)
    {
      if (currFile != null && !currFile.isAnyLiveStream())
        setPreviousAiring(currFile.getContentAiring(), null);
      closeMediaFile(true, true, true);
      alreadyPlayedRandomSongs.clear();
      playlistChain.clear();
      playlistChainIndices.clear();
      watchQueue.remove(daJob);
    }
    else if (daJob.id == START_PLAYLIST)
    {
      if (currFile != null)
      {
        closeMediaFile(false, true, false);
      }
      playlistChain.clear();
      playlistChainIndices.clear();
      playlistChain.add(daJob.playa);
      playlistChainIndices.add(new Integer(-1));
      currPlaylistPassesPC = daJob.passesPC;
      watchQueue.remove(daJob);
      alreadyPlayedRandomSongs.clear();
      // Be sure we randomize the first track of the music playlist if we're doing that
      if (isRandomPlaylistPlayback() && daJob.playaIdx < 0)
      {
        playbackNextRandomPlaylist();
      }
      else if (daJob.playaIdx <= 0)
      {
        daJob.playaIdx = 0;
        watchNextInPlaylist();
      }
      else
      {
        jumpToPlaylistIndex(daJob.playaIdx);
      }
    }
    else if (daJob.id == WATCH_MF)
    {
      MediaFile previousFile = currFile;
      if (currFile != null && !currFile.isAnyLiveStream())
      {
        setPreviousAiring(currFile.getContentAiring(), daJob.file.getContentAiring());
        closeMediaFile(false, true, false);
      }

      if (daJob.playa == null)
      {
        playlistChain.clear();
        playlistChainIndices.clear();
      }
      currFileLoaded = false;
      realDurMillis = 0;
      loggedWatch = false;
      playbackDone = false;
      lookForRecordTimeAdjust = null;
      recordTimeAdjust = 0;
      preparedForReload = false;

      // Get the correct MediaPlayer object for the new file. This may require unloading the current player.
      // Check if we have to close down the file due to an encoding change
      if (player != null && (!Sage.getBoolean("videoframe/safe_fast_file_switching", true) ||
          !player.canFastLoad(daJob.file.getLegacyMediaType(), daJob.file.getLegacyMediaSubtype(), daJob.file.encodedBy, daJob.file.getFile(0)) ||
          !areMediaFileFormatsSwitchable(previousFile, daJob.file)))
      {
        if (Sage.DBG) System.out.println("VIDEOFRAME Needs to do A FULL SWITCH on the file due to ENCODING CHANGE fastSwitchEnabled=" + Sage.getBoolean("videoframe/safe_fast_file_switching", true));
        closeMediaFile(true, false, false);
      }

      currFile = daJob.file;
      if (FileDownloader.isDownloading(null, currFile.getFile(0)))
        downer = FileDownloader.getFileDownloader(currFile.getFile(0));
      else
        downer = null;

      watchQueue.remove(daJob);
      watchQueue.insertElementAt(new VFJob(LOAD_MF, daJob.file, (Playlist)null), 0);
      autoplaying = false;

      // Since we've got the new file considered loaded we should trigger a UI refresh now.
      Catbert.processUISpecificHook("MediaPlayerFileLoadComplete", new Object[] { currFile, Boolean.FALSE }, uiMgr, true);
      // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
      // in this situation
      uiMgr.getRouter().resetInactivityTimers();
    }
    else if (daJob.id == LOAD_MF)
    {
      // Create the player now in case we didn't have all the format information to do it in WATCH_MF processing
      if (player == null)
      {
        // Now we create the appropriate player for this file
        player = createMediaPlayer(currFile);
      }

      // Start from where the Airing begins, and not from the front
      // of the file. If it's currently airing, do it from the latest
      // watched time + 1 sec & forward.
      // Set the time after the file is loaded
      MediaFile watchMe = daJob.file;
      long targetTime = (!playlistChain.isEmpty() && uiMgr.getBoolean("videoframe/start_playlist_items_from_beginning", true)) ?
          watchMe.getContentAiring().getStartTime() : BigBrother.getLatestWatch(watchMe.getContentAiring());
          targetTime = Math.max(targetTime, watchMe.getRecordTime() +
              (watchMe.isTV() ? uiMgr.getLong(prefs + TIME_TO_START_INTO_TV_FILE_PLAYBACK, 0) : 0));
          if (watchMe.isMusic()) // always start music from the beginning of the song or when we're going through a playlist
            targetTime = watchMe.getStart(0);
          dvdResumeTarget = null;
          if (watchMe.isDVD() || watchMe.isBluRay())
          {
            dvdResumeTarget = Wizard.getInstance().getWatch(watchMe.getContentAiring());
            if (dvdResumeTarget != null && dvdResumeTarget.getTitleNum() == 0)
            {
              // bad watched info from 7.0.13/14
              dvdResumeTarget = null;
            }
            dvdResumeTitleSetDone = false;
            oldcurrDVDTitle = -1;
            alreadySelectedDefaultDVDAudio = alreadySelectedDefaultDVDSub = false;
          }
          else
            alreadySelectedDefaultDVDAudio = alreadySelectedDefaultDVDSub = true;
          disableTimeScaling = Sage.getBoolean("videoframe/disable_timescaling_on_seek", false);

          // For jumping to live automatically, we want to do this as part of channel surfing
          // which means they've just tuned into this channel. So if the latest segment
          // is really short, then it means it fits the criteria!
          if (watchMe.isRecording())
          {
            long newDur = watchMe.getDuration(watchMe.getRecordingSegment());
            if (newDur != 0 && newDur < 7000)
            {
              targetTime = Sage.time() - newDur;
            }

            if (Sage.getBoolean("videoframe/force_live_playback_on_currently_airing_programs", false))
            {
              if (Sage.DBG) System.out.println("Disabling resume from last watched point and force to live");
              targetTime = Sage.time();
            }
          }

          if (iWantToBeLive || watchMe.isAnyLiveStream())
          {
            if (Sage.DBG) System.out.println("VF forcing to live.");
            targetTime = Sage.time();
          }

          // Check for any subsequent TIME_SET operations in the queue and get the
          // target time from them
          while (watchQueue.size() > 1)
          {
            VFJob nextJob = (VFJob) watchQueue.get(1);
            if (nextJob.id == TIME_ADJUST || nextJob.id == TIME_SET)
            {
              // get target time and remove from queue
              if (nextJob.id == TIME_SET)
                targetTime = nextJob.time;
              else
                targetTime += nextJob.time;
              synchronized (queueLock)
              {
                watchQueue.remove(1);
                queueLock.notifyAll();
              }
            } else {
              // next job is not a time set op, bail out of loop.
              break;
            }
          }

          // We do this before we load w/ MPlayer because we need to know about the external idx/sub files when
          // MPlayer loads
          boolean alreadyDidSubCheck = false;
          if (player instanceof LinuxMPlayerPlugin)
          {
            alreadyDidSubCheck = true;
            if (downer == null)
              currFile.checkForSubtitles();
          }

          if (Sage.DBG) System.out.println("VF file=" + watchMe + " targetTime = " + Sage.df(targetTime));
          mightWait = targetTime;
          realWatchStart = Sage.time();
          segment = -1; // This is needed because we DON'T do the load below
          alreadySkippedDVDMenus = false;

          // I don't need to load the file here because it gets done in timeSelected,
          // otherwise I'm unnecessarily loading a file in some cases

          // The MediaPlayer is already created, that's done in the LOAD_MF processing
          if (player == null)
            throw new InternalError("VideoFrame has a null MediaPlayer in WATCH_MF processing!");

          if (currFile.isDVD() && !currFile.isBluRay())
          {
            addMouseListeners(dvdMouseListener);
            addMouseMotionListeners(dvdMouseMotionListener);
            targetTime = 0;
          }
          else if (currFile.isBluRay())
          {
            targetTime = 0;
            // For BluRays we want to start off seeked to the proper position; we don't want it to load and then seek a split second
            // later since we don't process them w/ a BluRay VM yet
            if (dvdResumeTarget != null)
            {
              targetTime = dvdResumeTarget.getWatchEnd();
              blurayTargetTitle = dvdResumeTarget.getTitleNum();
              dvdResumeTarget = null;
            }
          }

          // Do any native resolution switching if it's setup
          MiniClientSageRenderer render = null;
          if (uiMgr.getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
          {
            render = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
            render.resetAdvancedAR(); // this gets reset when we load a new file
          }

          // Do any native video format resolution switching that's required
          if (render != null && (render.getResolutionOptions()) != null &&
              (render.getResolutionOptions().length) > 0 &&
              uiMgr.getBoolean("native_output_resolution_switching", false) && !watchMe.isMusic())
          {
            sage.media.format.VideoFormat targetFormat = watchMe.getFileFormat() != null ? watchMe.getFileFormat().getVideoFormat() : null;
            // We choose a format that matches i/p and the vertical resolution exactly. If no such format
            // exists then we choose the format with the highest vertical resolution preferring p over i.
            sage.media.format.VideoFormat[] resOptions = render.getResolutionFormats();
            sage.media.format.VideoFormat bestMatchedChoice = null;
            sage.media.format.VideoFormat bestCloseChoice = null;
            for (int i = 0; i < resOptions.length; i++)
            {
              // See if we match exactly
              if ((targetFormat != null && resOptions[i].isInterlaced() == targetFormat.isInterlaced() &&
                  (resOptions[i].getHeight() == targetFormat.getHeight() || resOptions[i].getWidth() == targetFormat.getWidth())) ||
                  (watchMe.isDVD() && targetFormat == null && resOptions[i].isInterlaced() &&
                  resOptions[i].getHeight() == (MMC.getInstance().isNTSCVideoFormat() ? 480 : 576)))
              {
                // Prefer the format with a FPS closest to the target format if it is within 0.5 FPS (also check double rates). Otherwise choose the highest framerate. Otherwise we could
                // prefer 24p over 60p for 30fps content. And likewise choose 24p over 50p for 25fps content.
                if (bestMatchedChoice == null || targetFormat == null)
                  bestMatchedChoice = resOptions[i];
                else
                {
                  float bestFPSdiff = Math.min(Math.abs(targetFormat.getFps() - bestMatchedChoice.getFps()),
                      Math.abs(2*targetFormat.getFps() - bestMatchedChoice.getFps()));
                  float currFPSdiff = Math.min(Math.abs(targetFormat.getFps() - resOptions[i].getFps()),
                      Math.abs(2*targetFormat.getFps() - resOptions[i].getFps()));
                  if ((bestFPSdiff > currFPSdiff && currFPSdiff < 0.5f) || (bestFPSdiff >= 0.5f && resOptions[i].getFps() > bestMatchedChoice.getFps()))
                    bestMatchedChoice = resOptions[i];
                }
                continue;
              }
              if (bestCloseChoice == null)
                bestCloseChoice = resOptions[i];
              else
              {
                if (bestCloseChoice.getHeight() < resOptions[i].getHeight() ||
                    (bestCloseChoice.getHeight() == resOptions[i].getHeight() && bestCloseChoice.isInterlaced() && !resOptions[i].isInterlaced()) ||
                    (bestCloseChoice.getHeight() == resOptions[i].getHeight() && !bestCloseChoice.isInterlaced() && !resOptions[i].isInterlaced() &&
                    bestCloseChoice.getFps() < resOptions[i].getFps()))
                {
                  bestCloseChoice = resOptions[i];
                }
              }
            }
            sage.media.format.VideoFormat bestChoice = (bestMatchedChoice != null) ? bestMatchedChoice : bestCloseChoice;
            if (Sage.DBG) System.out.println("Using native resolution matching to switch output resolution; targetFormat=" + targetFormat + " newResolution=" + bestChoice);
            render.pushResolutionChange(bestChoice.getFormatName());
          }

          if (!currFile.isLiveStream())
          {
            // We need to hold the player lock for the open/close if the open fails so no
            // calls come through on the dead file
            boolean timeSelRes;
            synchronized (player)
            {
              timeSelRes = timeSelected(targetTime, true);
              lastVideoOpTime = Sage.eventTime();
              if (!timeSelRes)
              {
                System.out.println("VideoFrame had an error loading the file. It must abort the file load.");
                mightWait = 0;
                closeMediaFile(true, false, true);
              }
            }
          }
          else
          {
            if (player.getState() != MediaPlayer.NO_STATE &&
                player.canFastLoad(currFile.getLegacyMediaType(), currFile.getLegacyMediaSubtype(), currFile.encodedBy, null))
            {
              try
              {
                player.fastLoad(currFile.getLegacyMediaType(), currFile.getLegacyMediaSubtype(), currFile.encodedBy, null,
                    currFile.getServerName(), true, 0, false);
                player.play();
                realDurMillis = player.getDurationMillis();
              }
              catch (PlaybackException e)
              {
                Catbert.processUISpecificHook("MediaPlayerError", new Object[] {
                    Sage.rez("Playback"), e.toString() }, uiMgr, true);
              }
            }
            else
            {
              if (player.getState() != MediaPlayer.NO_STATE)
                player.free();
              try
              {
                player.load(currFile.getLegacyMediaType(), currFile.getLegacyMediaSubtype(), currFile.encodedBy, null, null, false, 0);
                lastVideoOpTime = Sage.eventTime();
                player.play();
                player.setClosedCaptioningState(uiMgr.getInt(prefs + LAST_CC_STATE, MediaPlayer.CC_DISABLED));
                realDurMillis = player.getDurationMillis();
              }
              catch (PlaybackException e)
              {
                if (Sage.DBG) e.printStackTrace();
                Catbert.processUISpecificHook("MediaPlayerError", new Object[] {
                    Sage.rez("Playback"), e.toString() }, uiMgr, true);
              }
            }
          }

          if (Sage.DBG) System.out.println("Channel Change Time=" + (Sage.time() - channelChangeTime) + " NOTE: Not valid if this was not a direct channel change!");
          if (currFile != null)
          {
            currFileLoaded = true;
            // Now load any subtitle handler that we need
            if (!alreadyDidSubCheck && downer == null)
              currFile.checkForSubtitles();
            sage.media.format.ContainerFormat cf = currFile.getFileFormat();
            if (cf != null && cf.hasExternalSubtitles())
            {
              // Check if these are bitmap based and if so only do it with a client that supports it
              if (cf.areExternalSubsBitmaps())
              {
                if ((uiMgr.getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer) &&
                    ((MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine()).hasSubtitleSupport())
                {
                  subHandler = sage.media.sub.SubtitleHandler.createSubtitleHandler(this, currFile);
                }
              }
              else
                subHandler = sage.media.sub.SubtitleHandler.createSubtitleHandler(this, currFile);
              if (subHandler != null)
                subHandler.setDelay(subtitleDelay);
            }
            if (cf != null && cf.hasChapters())
              chapterPoints = cf.getChapterStarts();
          }

          // Now change to the configured default audio track and the default subtitle track
          selectDefaultAudioLanguage();
          selectDefaultSubpicLanguage();

          // Don't select TrueHD audio in Matroska files by default for media extenders unless HD audio output is enabled
          if (currFile != null && sage.media.format.MediaFormat.MATROSKA.equals(currFile.getContainerFormat()) &&
              uiMgr.getUIClientType() == UIClient.REMOTE_UI && uiMgr.getRootPanel() != null)
          {
            MiniClientSageRenderer mcsr = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
            sage.media.format.AudioFormat currAudFormat = ((MiniPlayer) player).getCurrAudioFormat();
            if (currAudFormat != null && mcsr != null && mcsr.isMediaExtender() && mcsr.getAudioOutput() != null && !"HDMIHBR".equals(mcsr.getAudioOutput()) &&
                sage.media.format.MediaFormat.DOLBY_HD.equals(currAudFormat.getFormatName()))
            {
              if (Sage.DBG) System.out.println("Detected selection of TrueHD audio in Matroska yet playback device does not support HD Audio output....finding alternate track");
              sage.media.format.ContainerFormat cf = currFile.getFileFormat();
              if (cf != null)
              {
                MediaLangInfo mli = (MediaLangInfo) mediaLangMap.get(uiMgr.get("default_audio_language", "English"));
                sage.media.format.AudioFormat[] afs = cf.getAudioFormats();
                boolean foundMatch = false;
                // Do preferred language first, then anything second if that doesn't work'
                for (int i = 0; i < afs.length; i++)
                {
                  if (!sage.media.format.MediaFormat.DOLBY_HD.equals(afs[i].getFormatName()) && (mli == null || getMatchingLangIndex(mli.threeChar, afs[i].getLanguage()) != -1))
                  {
                    if (Sage.DBG) System.out.println("1-Selecting non-TrueHD audio track at index " + i + " of " + afs[i]);
                    playbackControl(DVD_CONTROL_AUDIO_CHANGE, i, -1);
                    foundMatch = true;
                    break;
                  }
                }
                if (!foundMatch)
                {
                  for (int i = 0; i < afs.length; i++)
                  {
                    if (!sage.media.format.MediaFormat.DOLBY_HD.equals(afs[i].getFormatName()))
                    {
                      if (Sage.DBG) System.out.println("2-Selecting non-TrueHD audio track at index " + i + " of " + afs[i]);
                      playbackControl(DVD_CONTROL_AUDIO_CHANGE, i, -1);
                      foundMatch = true;
                      break;
                    }
                  }
                }
              }
            }
          }

          videoComp.setVisible(true);

          if (player != null)
            player.setMute(globalMute);

          watchQueue.remove(daJob);

          //uiMgr.trueValidate();
          Catbert.processUISpecificHook("MediaPlayerFileLoadComplete", new Object[] { currFile, Boolean.TRUE }, uiMgr, true);
          sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYBACK_STARTED,
              new Object[] { sage.plugin.PluginEventManager.VAR_MEDIAFILE, currFile, sage.plugin.PluginEventManager.VAR_UICONTEXT, uiMgr.getLocalUIClientName(),
              sage.plugin.PluginEventManager.VAR_DURATION, new Long(getDurationMillis()), sage.plugin.PluginEventManager.VAR_MEDIATIME, new Long(getMediaTimeMillis()),
              sage.plugin.PluginEventManager.VAR_CHAPTERNUM, new Integer(getDVDChapter()), sage.plugin.PluginEventManager.VAR_TITLENUM, new Integer(getDVDTitle()) });
          // Reset the inactivity timer since the user is basically in a wait state so we don't want the OSD to autohide
          // in this situation
          uiMgr.getRouter().resetInactivityTimers();
    }
    else if (daJob.id == RELOAD_PREPARE_MF)
    {
      if (player != null) // due to async nature, this can occur
      {
        wasPausedBeforeReload = player.getState() == MediaPlayer.PAUSE_STATE;
        targetTimeForReload = getMediaTimeMillis();
        mightWait = targetTimeForReload;
        // This is needed to prevent hangs in the stop call
        player.inactiveFile();
        player.free();
        preparedForReload = true;
      }
      watchQueue.remove(daJob);
    }
    else if (daJob.id == RELOAD_MF)
    {
      if (player != null) // due to async nature, this can occur
      {
        if (!preparedForReload)
        {
          wasPausedBeforeReload = player.getState() == MediaPlayer.PAUSE_STATE;
          targetTimeForReload = getMediaTimeMillis();
          mightWait = targetTimeForReload;
          // This is needed to prevent hangs in the stop call
          player.inactiveFile();
          player.free();
        }
        if (blurayTargetTitle > 0)
        {
          wasPausedBeforeReload = false;
          targetTimeForReload = 0;
          mightWait = 0;
          loggedWatch = false;
        }
        if (getPluginClassForFile(currFile).length() > 0)
        {
          // Recreate the player if its plugin based
          player = createMediaPlayer(currFile);
        }
        segment = -1; // This is needed to force timeSelected to load the file on the seek
        if (!currFile.isLiveStream())
          timeSelected(targetTimeForReload, false);
        else
        {
          try
          {
            player.load(currFile.getLegacyMediaType(), currFile.getLegacyMediaSubtype(), currFile.encodedBy, null, null, false, 0);
            lastVideoOpTime = Sage.eventTime();
            player.setMute(globalMute);
            player.play();
            player.setClosedCaptioningState(uiMgr.getInt(prefs + LAST_CC_STATE, MediaPlayer.CC_DISABLED));
            realDurMillis = player.getDurationMillis();
          }
          catch (PlaybackException e)
          {
            if (Sage.DBG) e.printStackTrace();
            Catbert.processUISpecificHook("MediaPlayerError", new Object[] {
                Sage.rez("Playback"), e.toString() }, uiMgr, true);
          }
        }
        mightWait = 0;
        if (!wasPausedBeforeReload || currFile.isLiveStream())
          player.play();
        else
          player.pause();
      }
      watchQueue.remove(daJob);
      preparedForReload = false;
    }
    else if (daJob.id == STD_COMPLETE)
    {
      if (player != null)
        player.setPlaybackRate(1); // Clear the playback rate to 1x so it doesn't linger if we fast switch to a new file
      standardFileComplete();
      watchQueue.remove(daJob);
    }
    else if (daJob.id == SLEEP)
    {
      liveControl = false;
      controlledInput = null;
      seek.finishWatch(uiMgr);
      if (currFile != null && !currFile.isAnyLiveStream())
        setPreviousAiring(currFile.getContentAiring(), null);
      closeMediaFile(true, true, true);
      watchQueue.remove(daJob);
      seek.kick();
    }
    else if (daJob.id == INACTIVE_FILE)
    {
      java.io.File segFile = currFile.getFile(segment);
      if (segFile != null && (player instanceof MetaMediaPlayer || (segFile.toString().equals(daJob.inactiveFilename) && player != null)))
      {
        if (Sage.DBG) System.out.println("VF notified of Inactive File");
        if (player instanceof MetaMediaPlayer)
          ((MetaMediaPlayer) player).inactiveFile(daJob.inactiveFilename);
        else
          player.inactiveFile();
      }
      watchQueue.remove(daJob);
    }
  }

  private int watchNextInPlaylist()
  {
    if (Sage.DBG) System.out.println("VF.watchNextInPlaylist playlistChain=" + playlistChain + " playindices=" +
        playlistChainIndices + " currFile=" + currFile);
    java.util.Vector oldPlayChainIndices = (java.util.Vector) playlistChainIndices.clone();
    java.util.Vector oldPlayChain = (java.util.Vector) playlistChain.clone();
    do
    {
      Object currPlaylist = playlistChain.lastElement();
      int currPlayIdx = ((Integer) playlistChainIndices.lastElement()).intValue();
      currPlayIdx++;
      if (currPlaylist instanceof Airing[])
      {
        Airing[] currAirs = (Airing[]) currPlaylist;
        if (currPlayIdx >= currAirs.length)
        {
          // Recurse up a level
          playlistChainIndices.remove(playlistChainIndices.size() - 1);
          playlistChain.remove(playlistChain.size() - 1);
        }
        else
        {
          playlistChainIndices.setElementAt(new Integer(currPlayIdx), playlistChainIndices.size() - 1);
          if (watch(currAirs[currPlayIdx], false, (Playlist) playlistChain.firstElement(), null) == WATCH_OK)
            return WATCH_OK;
        }
      }
      else // instanceof Playlist
      {
        Playlist playboy = (Playlist) currPlaylist;
        if (playboy.getNumSegments() <= currPlayIdx)
        {
          if (playlistChain.size() == 1)
          {
            // Done with the playlist, just terminate
            // No reason to set this, it'll just make the UI incorrect
            //playlistChainIndices.setElementAt(new Integer(playboy.getNumSegments()), 0);
            // Reset the playlist back to where it was before this request since this might have been
            // destructive
            playlistChainIndices = oldPlayChainIndices;
            playlistChain = oldPlayChain;
            return WATCH_FAILED_PLAYLIST_OVER;
          }
          else
          {
            // Recurse up a level
            playlistChainIndices.remove(playlistChainIndices.size() - 1);
            playlistChain.remove(playlistChain.size() - 1);
          }
        }
        else
        {
          playlistChainIndices.setElementAt(new Integer(currPlayIdx), playlistChainIndices.size() - 1);
          Object newPlaySeg = playboy.getSegment(currPlayIdx);
          if (newPlaySeg instanceof Airing)
          {
            Airing airSeg = (Airing) newPlaySeg;
            if (watch(airSeg, false, (Playlist) playlistChain.firstElement(), null) == WATCH_OK)
              return WATCH_OK;
          }
          else if (newPlaySeg instanceof Playlist)
          {
            // Ignore empty sub playlists
            if (((Playlist) newPlaySeg).getNumSegments() == 0) continue;

            // If there's looping, be aware of it and prevent the playlistChain
            // from growing on and on forever
            int loopIdx = playlistChain.indexOf(newPlaySeg);
            if (loopIdx != -1)
            {
              while (playlistChain.size() > loopIdx)
              {
                playlistChain.remove(playlistChain.size() - 1);
                playlistChainIndices.remove(playlistChainIndices.size() - 1);
              }
            }
            playlistChain.add(newPlaySeg);
            playlistChainIndices.add(new Integer(-1));
          }
          else if (newPlaySeg instanceof Album)
          {
            playlistChain.add(((Album) newPlaySeg).getAirings());
            playlistChainIndices.add(new Integer(-1));
          }
          else if (newPlaySeg instanceof MediaFile)
          {
            if (watch(((MediaFile)newPlaySeg).getContentAiring(), false, (Playlist) playlistChain.firstElement(), (MediaFile) newPlaySeg) == WATCH_OK)
              return WATCH_OK;
          }
        }
      }
    } while (true);
  }

  private int watchPrevInPlaylist()
  {
    if (Sage.DBG) System.out.println("VF.watchPrevInPlaylist playlistChain=" + playlistChain + " playindices=" +
        playlistChainIndices + " currFile=" + currFile);
    java.util.Vector oldPlayChainIndices = (java.util.Vector) playlistChainIndices.clone();
    java.util.Vector oldPlayChain = (java.util.Vector) playlistChain.clone();
    do
    {
      Object currPlaylist = playlistChain.lastElement();
      int currPlayIdx = ((Integer) playlistChainIndices.lastElement()).intValue();
      currPlayIdx--;
      if (currPlaylist instanceof Airing[])
      {
        Airing[] currAirs = (Airing[]) currPlaylist;
        if (currPlayIdx < 0 || currAirs.length == 0)
        {
          // Recurse up a level
          playlistChainIndices.remove(playlistChainIndices.size() - 1);
          playlistChain.remove(playlistChain.size() - 1);
        }
        else
        {
          playlistChainIndices.setElementAt(new Integer(currPlayIdx), playlistChainIndices.size() - 1);
          if (watch(currAirs[currPlayIdx], false, (Playlist) playlistChain.firstElement(), null) == WATCH_OK)
            return WATCH_OK;
        }
      }
      else // instanceof Playlist
      {
        Playlist playboy = (Playlist) currPlaylist;
        if (currPlayIdx < 0 || playboy.getNumSegments() == 0)
        {
          if (playlistChain.size() == 1)
          {
            // Done with the playlist, just terminate
            // No reason to set this, it'll just make the UI incorrect
            //playlistChainIndices.setElementAt(new Integer(-1), 0);
            playlistChainIndices = oldPlayChainIndices;
            playlistChain = oldPlayChain;
            return WATCH_FAILED_PLAYLIST_OVER;
          }
          else
          {
            // Recurse up a level
            playlistChainIndices.remove(playlistChainIndices.size() - 1);
            playlistChain.remove(playlistChain.size() - 1);
          }
        }
        else
        {
          playlistChainIndices.setElementAt(new Integer(currPlayIdx), playlistChainIndices.size() - 1);
          Object newPlaySeg = playboy.getSegment(currPlayIdx);
          if (newPlaySeg instanceof Airing)
          {
            Airing airSeg = (Airing) newPlaySeg;
            if (watch(airSeg, false, (Playlist) playlistChain.firstElement(), null) == WATCH_OK)
              return WATCH_OK;
          }
          else if (newPlaySeg instanceof Playlist)
          {
            playlistChain.add(newPlaySeg);
            playlistChainIndices.add(new Integer(((Playlist) newPlaySeg).getNumSegments()));
          }
          else if (newPlaySeg instanceof Album)
          {
            Airing[] subAirs = ((Album) newPlaySeg).getAirings();
            playlistChain.add(subAirs);
            playlistChainIndices.add(new Integer(subAirs.length));
          }
          else if (newPlaySeg instanceof MediaFile)
          {
            if (watch(((MediaFile)newPlaySeg).getContentAiring(), false, (Playlist) playlistChain.firstElement(), (MediaFile) newPlaySeg) == WATCH_OK)
              return WATCH_OK;
          }
        }
      }
    } while (true);
  }

  private void prepareForRepeatPlay()
  {
    if (Sage.DBG) System.out.println("Repeat play is occuring in the media player");
    if (!playlistChain.isEmpty())
    {
      Playlist currPlaya = (Playlist) playlistChain.firstElement();
      playlistChain.clear();
      playlistChainIndices.clear();
      playlistChain.add(currPlaya);
      playlistChainIndices.add(new Integer(-1));
    }
    alreadyPlayedRandomSongs.clear();
    restartOnRedundantWatch = true;
  }

  private boolean isRandomPlaylistPlayback()
  {
    return !playlistChain.isEmpty() && uiMgr.getBoolean(isPlayingMusicPlaylist() ? RANDOM_MUSIC_PLAYBACK : RANDOM_VIDEO_PLAYBACK, false);
  }

  private boolean isRepeatPlaylistPlayback()
  {
    return !playlistChain.isEmpty() && uiMgr.getBoolean(isPlayingMusicPlaylist() ? REPEAT_MUSIC_PLAYBACK : REPEAT_VIDEO_PLAYBACK, false);
  }

  private void standardFileComplete()
  {
    if (Sage.WINDOWS_OS) // mplayer doesn't like this
      pause();

    if (playbackDone) return;
    // If it's already watched, then we're just doing something redundant here....11/5/03 not true, this is wrong
    //if (currFile != null && currFile.getContentAiring().isWatched())
    //	return;
    logFileWatch();
    notifyPlaybackFinished();
    Wizard wiz = Wizard.getInstance();
    boolean callFinishedHook = true;
    if (uiMgr.getBoolean(prefs + "always_call_fileplaybackfinished", false))
    {
      callFinishedHook = false;
      Catbert.processUISpecificHook("FilePlaybackFinished", new Object[] { currFile }, uiMgr, true);
    }
    if (playlistChain.size() > 0)
    {
      boolean currRandom = isRandomPlaylistPlayback();
      boolean currRepeat = isRepeatPlaylistPlayback();
      if (currRandom)
      {
        if (playbackNextRandomPlaylist() == WATCH_OK)
          return;
      }
      else if (watchNextInPlaylist() == WATCH_OK)
        return;
      if (currRepeat)
      {
        prepareForRepeatPlay();
        if (currRandom)
        {
          if (playbackNextRandomPlaylist() == WATCH_OK)
            return;
        }
        else if (watchNextInPlaylist() == WATCH_OK)
          return;
      }
    }
    else if (currFile.isMusic() && !currFile.isAnyLiveStream() &&
        uiMgr.getBoolean("videoframe/autoplay_next_music_file", true) &&
        currFile.generalType != MediaFile.MEDIAFILE_LOCAL_PLAYBACK)
    {
      // Play the next song in the list, unless its random
      if (uiMgr.getBoolean(RANDOM_MUSIC_PLAYBACK, false))
      {
        if (playbackNextRandomMusic() == WATCH_OK)
          return;
        else if (uiMgr.getBoolean(REPEAT_MUSIC_PLAYBACK, false))
        {
          if (playbackNextRandomMusic() == WATCH_OK)
            return;
        }
      }
      else
      {
        MediaFile[] mfs;
        if (cachedSortedMusicFiles == null || cachedSortedMusicFilesTimestamp < wiz.getLastModified(DBObject.MEDIA_MASK_MUSIC))
        {
          long newTimestamp = Wizard.getInstance().getLastModified(DBObject.MEDIA_MASK_MUSIC);
          mfs = wiz.getFiles(DBObject.MEDIA_MASK_MUSIC, true);
          java.util.Arrays.sort(mfs, musicFileComparator);
          cachedSortedMusicFiles = mfs;
          cachedSortedMusicFilesTimestamp = newTimestamp;
        }
        else
          mfs = cachedSortedMusicFiles;
        int idx = java.util.Arrays.binarySearch(mfs, currFile, musicFileComparator);
        boolean canRepeat = uiMgr.getBoolean(REPEAT_MUSIC_PLAYBACK, false);
        while (idx >= 0 && true)
        {
          idx++;
          if (mfs[idx].isMusic() && !mfs[idx].isAnyLiveStream())
          {
            final MediaFile playMeNow = mfs[idx];
            if (Sage.DBG) System.out.println("VF is autoplaying next file of:" + playMeNow);
            //java.awt.EventQueue.invokeLater(new Runnable()
            //{
            //	public void run()
            //	{
            autoplaying = true;
            watch(playMeNow.getContentAiring());
            //	}
            //});
            return;
          }
          if (idx >= 0 && idx + 1 < mfs.length)
          {
            if (canRepeat)
            {
              canRepeat = false;
              idx = 0;
            }
            else
              break;
          }
        }
      }
    }

    if (currFile != null && currFile.isDVD())
    {
      // When a DVD completes it must be stopped since the whole state machine has terminated at that point
      // This block was copied from the CLOSE_MF block above
      // We need to do this before we call the finished hook so the menu refreshes properly
      closeMediaFile(true, false, true);
      alreadyPlayedRandomSongs.clear();
      playlistChain.clear();
      playlistChainIndices.clear();
    }
    if (callFinishedHook)
    {
      // This is only when there's nothing to play next. This is not called at the
      // end of every file.
      Catbert.processUISpecificHook("FilePlaybackFinished", new Object[] { currFile }, uiMgr, true);
    }
    playbackDone = true;
  }

  private int jumpToPlaylistIndex(int playIdx)
  {
    if (Sage.DBG) System.out.println("VF.jumpToPlaylistIndex " + playIdx + " playlistChain=" + playlistChain + " playindices=" +
        playlistChainIndices + " currFile=" + currFile);
    boolean firstTry = true;
    do
    {
      Object currPlaylist = playlistChain.lastElement();
      int currPlayIdx = ((Integer) playlistChainIndices.lastElement()).intValue();
      if (firstTry)
      {
        currPlayIdx = playIdx - 1; // we use 0-based indices
        firstTry = false;
      }
      else
        currPlayIdx++;
      if (currPlaylist instanceof Airing[])
      {
        Airing[] currAirs = (Airing[]) currPlaylist;
        if (currPlayIdx >= currAirs.length)
        {
          // Recurse up a level
          playlistChainIndices.remove(playlistChainIndices.size() - 1);
          playlistChain.remove(playlistChain.size() - 1);
        }
        else
        {
          playlistChainIndices.setElementAt(new Integer(currPlayIdx), playlistChainIndices.size() - 1);
          if (watch(currAirs[currPlayIdx], false, (Playlist) playlistChain.firstElement(), null) == WATCH_OK)
            return WATCH_OK;
        }
      }
      else // instanceof Playlist
      {
        Playlist playboy = (Playlist) currPlaylist;
        if (playboy.getNumSegments() <= currPlayIdx)
        {
          if (playlistChain.size() == 1)
          {
            // Done with the playlist, just terminate
            playlistChainIndices.setElementAt(new Integer(playboy.getNumSegments()), 0);
            return WATCH_FAILED_PLAYLIST_OVER;
          }
          else
          {
            // Recurse up a level
            playlistChainIndices.remove(playlistChainIndices.size() - 1);
            playlistChain.remove(playlistChain.size() - 1);
          }
        }
        else
        {
          playlistChainIndices.setElementAt(new Integer(currPlayIdx), playlistChainIndices.size() - 1);
          Object newPlaySeg = playboy.getSegment(currPlayIdx);
          if (newPlaySeg instanceof Airing)
          {
            Airing airSeg = (Airing) newPlaySeg;
            if (watch(airSeg, false, (Playlist) playlistChain.firstElement(), null) == WATCH_OK)
              return WATCH_OK;
          }
          else if (newPlaySeg instanceof Playlist)
          {
            // Ignore empty sub playlists
            if (((Playlist) newPlaySeg).getNumSegments() == 0) continue;

            // If there's looping, be aware of it and prevent the playlistChain
            // from growing on and on forever
            int loopIdx = playlistChain.indexOf(newPlaySeg);
            if (loopIdx != -1)
            {
              while (playlistChain.size() > loopIdx)
              {
                playlistChain.remove(playlistChain.size() - 1);
                playlistChainIndices.remove(playlistChainIndices.size() - 1);
              }
            }
            playlistChain.add(newPlaySeg);
            playlistChainIndices.add(new Integer(-1));
          }
          else if (newPlaySeg instanceof Album)
          {
            playlistChain.add(((Album) newPlaySeg).getAirings());
            playlistChainIndices.add(new Integer(-1));
          }
          else if (newPlaySeg instanceof MediaFile)
          {
            if (watch(((MediaFile)newPlaySeg).getContentAiring(), false, (Playlist) playlistChain.firstElement(), (MediaFile) newPlaySeg) == WATCH_OK)
              return WATCH_OK;
          }
        }
      }
    } while (true);
  }

  private int playbackNextRandomPlaylist()
  {
    if (Sage.DBG) System.out.println("VF.playbackNextRandomPlaylist playlistChain=" + playlistChain + " playindices=" +
        playlistChainIndices + " currFile=" + currFile);
    // Clear all of the playlist elements but the first one and then go from there, retain it in case
    // random gets turned off and they're in a playlist
    while (playlistChain.size() > 1)
    {
      playlistChain.removeElementAt(playlistChain.size() - 1);
      playlistChainIndices.removeElementAt(playlistChainIndices.size() - 1);
    }
    do
    {
      Object currPlaylist = playlistChain.lastElement();
      if (alreadyPlayedRandomSongs.contains(currPlaylist))
      {
        if (playlistChain.size() == 1)
          return WATCH_FAILED_PLAYLIST_OVER;
        else
        {
          playlistChain.removeElementAt(playlistChain.size() - 1);
          playlistChainIndices.removeElementAt(playlistChainIndices.size() - 1);
          continue;
        }
      }
      // Randomly determine the currPlayIdx
      int currPlayIdx;
      if (currPlaylist instanceof Airing[])

      {
        Airing[] currAirs = (Airing[]) currPlaylist;
        currPlayIdx = (int) Math.min(Math.floor(Math.random() * currAirs.length), currAirs.length - 1);
        if (alreadyPlayedRandomSongs.contains(currAirs[currPlayIdx]))
        {
          // Walk through them and see if there's any we haven't played yet.
          int testIdx = (currPlayIdx + 1) % currAirs.length;
          for (; testIdx != currPlayIdx; testIdx = (testIdx + 1) % currAirs.length)
          {
            if (!alreadyPlayedRandomSongs.contains(currAirs[currPlayIdx]))
              break;
          }
          if (testIdx == currPlayIdx)
          {
            // This shouldn't really happen because we check to make sure there are unplayed airings in the album before playing it
            if (Sage.DBG) System.out.println("ERROR in playlist management, random play of an album in a playlist didn't find an unplayed track");
            playlistChain.removeElementAt(playlistChain.size() - 1);
            playlistChainIndices.removeElementAt(playlistChainIndices.size() - 1);
            continue;
          }
          currPlayIdx = testIdx;
        }
        playlistChainIndices.setElementAt(new Integer(currPlayIdx), playlistChainIndices.size() - 1);
        alreadyPlayedRandomSongs.add(currAirs[currPlayIdx]);
        if (watch(currAirs[currPlayIdx], false, (Playlist) playlistChain.firstElement(), null) == WATCH_OK)
          return WATCH_OK;
      }
      else // instanceof Playlist
      {
        Playlist playboy = (Playlist) currPlaylist;
        currPlayIdx = (int) Math.min(Math.floor(Math.random() * playboy.getNumSegments()), playboy.getNumSegments() - 1);
        Object newPlaySeg = playboy.getSegment(currPlayIdx);
        // Check to make sure this segment isn't already done and that it's not infinite recursion
        if (alreadyPlayedRandomSongs.contains(newPlaySeg) || playlistChain.indexOf(newPlaySeg) != -1)
        {
          // Walk through them and see if there's any we haven't played yet.
          int testIdx = (currPlayIdx + 1) % playboy.getNumSegments();
          for (; testIdx != currPlayIdx; testIdx = (testIdx + 1) % playboy.getNumSegments())
          {
            Object mySeg = playboy.getSegment(testIdx);
            if (!alreadyPlayedRandomSongs.contains(mySeg) && playlistChain.indexOf(mySeg) == -1)
              break;
          }
          if (testIdx == currPlayIdx)
          {
            // Nothing in this Playlist that matches, remove it and go up a level
            alreadyPlayedRandomSongs.add(playboy);
            continue;
          }
          currPlayIdx = testIdx;
          newPlaySeg = playboy.getSegment(currPlayIdx);
        }
        playlistChainIndices.setElementAt(new Integer(currPlayIdx), playlistChainIndices.size() - 1);
        if (newPlaySeg instanceof Airing)
        {
          alreadyPlayedRandomSongs.add(newPlaySeg);
          Airing airSeg = (Airing) newPlaySeg;
          if (watch(airSeg, false, (Playlist) playlistChain.firstElement(), null) == WATCH_OK)
            return WATCH_OK;
        }
        else if (newPlaySeg instanceof Playlist)
        {
          // Ignore empty sub playlists
          if (((Playlist) newPlaySeg).getNumSegments() == 0)
          {
            alreadyPlayedRandomSongs.add(newPlaySeg);
            continue;
          }

          playlistChain.add(newPlaySeg);
          playlistChainIndices.add(new Integer(-1));
        }
        else if (newPlaySeg instanceof Album)
        {
          // Check if all of the Airings for this Album are used up. If so then mark it as done too
          Airing[] alAirs = ((Album) newPlaySeg).getAirings();
          int i = 0;
          for (; i < alAirs.length; i++)
            if (!alreadyPlayedRandomSongs.contains(alAirs[i]))
              break;
          if (i == alAirs.length)
          {
            alreadyPlayedRandomSongs.add(newPlaySeg);
            continue;
          }
          playlistChain.add(alAirs);
          playlistChainIndices.add(new Integer(-1));
        }
        else if (newPlaySeg instanceof MediaFile)
        {
          alreadyPlayedRandomSongs.add(newPlaySeg);
          if (watch(((MediaFile)newPlaySeg).getContentAiring(), false, (Playlist) playlistChain.firstElement(), (MediaFile) newPlaySeg) == WATCH_OK)
            return WATCH_OK;
        }
      }
    } while (true);
  }

  private int playbackNextRandomMusic()
  {
    if (!playlistChain.isEmpty() && (nowPlayingPlaylist == null || !nowPlayingPlaylist.isSingleItemPlaylist()))
    {
      return playbackNextRandomPlaylist();
    }
    else
    {
      MediaFile[] mfs = Wizard.getInstance().getFiles();
      java.util.ArrayList remFiles = new java.util.ArrayList(java.util.Arrays.asList(mfs));
      while (!remFiles.isEmpty())
      {
        int idx = (int)Math.min(Math.floor(remFiles.size() * Math.random()), remFiles.size() - 1);
        final MediaFile playMe = (MediaFile) remFiles.get(idx);
        if (playMe.isMusic() && !playMe.isAnyLiveStream() && currFile != playMe && alreadyPlayedRandomSongs.add(playMe.getContentAiring()))
        {
          if (Sage.DBG) System.out.println("VF is random autoplaying next file of:" + playMe);
          //java.awt.EventQueue.invokeLater(new Runnable()
          //{
          //	public void run()
          //	{
          autoplaying = true;
          return watch(playMe.getContentAiring());
          //	}
          //});
          //return;
        }
        remFiles.remove(idx);
      }
    }
    return WATCH_FAILED_PLAYLIST_OVER;
  }

  protected boolean timeSelected(long milliTime, boolean forward)
  {
    if (currFile == null || player == null) return false;
    long currTime = Sage.time() - getEncodeToPlaybackDelay();
    if (Sage.DBG) System.out.println("VideoFrame.timeSelected(" + Sage.df(milliTime) + ", " + forward+ ") currFile="+currFile);

    // Apply the bounds of the file's beginning and end to the seek time
    int newSeg = 0;
    java.io.File newSegFile = null;
    boolean sharePtr = false;
    long circSize = 0;
    if (currFile.isDVD() || currFile.isBluRay())
    {
      // Update this milliTime after its initially loaded so we can use the proper bluray title
      newSeg = 0;
      newSegFile = currFile.getFile(newSeg);
    }
    else
    {
      // Get the segment that the new time lies in
      synchronized (currFile)
      {
        milliTime = Math.max(currFile.getRecordTime(), milliTime);
        if (currFile.isRecording())
          milliTime = Math.min(currTime, milliTime);
        else
          milliTime = Math.min(currFile.getRecordEnd(), milliTime);

        if (downer != null && downer.needsTracking())
        {
          milliTime = Math.min(currFile.getRecordTime() + downer.getMaxSeekTimestamp(), milliTime);
          milliTime = Math.max(currFile.getRecordTime() + downer.getMinSeekTimestamp(), milliTime);
          circSize = downer.getCircularDownloadSize();
          downer.notifyForPlayerSeek(Math.max(0, milliTime - currFile.getRecordTime()));
        }

        newSeg = currFile.segmentLocation(milliTime, forward);
        newSegFile = currFile.getFile(newSeg);
        sharePtr = currFile.isRecording(newSeg) || (downer != null && downer.needsTracking());

        // Apply the bounds of the segment to the seek time, keep us from starting
        // right at the end of a segment.
        milliTime = Math.max(currFile.getStart(newSeg), milliTime);
        if (forward)
          milliTime = Math.min(currFile.getEnd(newSeg), milliTime);
        else
          milliTime = Math.min(Math.max(currFile.getStart(newSeg),
              currFile.getEnd(newSeg) - segmentMinBackup), milliTime);
      }
      if (currFile.isLiveBufferedStream())
        circSize = currFile.getStreamBufferSize();
    }
    mightWait = milliTime;

    //System.out.println("SEGMENT Shift from " + segment + " to " + newSeg + " for time " + Sage.df(milliTime));
    int oldState = player.getState();
    boolean loadedFile = false;
    boolean didFastLoad = false;
    synchronized (player)
    {
      if (newSeg != segment)
      {
        loadedFile = true;
        int oldSegment = segment;
        segment = newSeg;
        // Don't allow fast switching between segments of a media file; only allow it on switching between different files
        // since that's the only real time a seamless transition will occur.
        if (player.getState() != MediaPlayer.NO_STATE && oldSegment == -1 &&
            player.canFastLoad(currFile.getLegacyMediaType(), currFile.getLegacyMediaSubtype(), currFile.encodedBy, newSegFile))
        {
          try
          {
            player.fastLoad(currFile.getLegacyMediaType(), currFile.getLegacyMediaSubtype(), currFile.encodedBy, newSegFile,
                currFile.getServerName(), sharePtr,
                circSize, false);
            realDurMillis = player.getDurationMillis();
            didFastLoad = true;
          }
          catch (PlaybackException e)
          {
            if (Sage.DBG) e.printStackTrace();
            if (currFile.isTV())
            {
              closeMediaFile(true, false, false);
              if (Sage.DBG) System.out.println("VF detected file state change after load, reloading it...");
              // File has changed since tried to load it, do it again. This can
              // happen due to asynchronous verification between the client & server
              return timeSelected(milliTime, forward);
            }
            if (!hasPendingContentJob())
            {
              Catbert.processUISpecificHook("MediaPlayerError", new Object[] {
                  Sage.rez("Playback"), e.toString() }, uiMgr, true);
            }
            else if (Sage.DBG)
              System.out.println("Ignoring MediaPlayerError because we have another pending content loading job: " + e);
            mightWait = 0;
            return false;
          }
        }
        else
        {
          if (player.getState() != MediaPlayer.NO_STATE)
            player.free();
          if (currFile.isDVD() || currFile.isBluRay())
          {
            // DO NOT Strip off the VIDEO_TS subdir to get the parent DVD volume
            try
            {
              player.load(currFile.getLegacyMediaType(), currFile.getLegacyMediaSubtype(), currFile.encodedBy, newSegFile,
                  currFile.getServerName(), false, 0);
              realDurMillis = player.getDurationMillis();
            }
            catch (PlaybackException e)
            {
              if (Sage.DBG) e.printStackTrace();
              if (!hasPendingContentJob())
              {
                Catbert.processUISpecificHook("MediaPlayerError", new Object[] {
                    Sage.rez("Playback"), e.toString() }, uiMgr, true);
              }
              else if (Sage.DBG)
                System.out.println("Ignoring MediaPlayerError because we have another pending content loading job: " + e);
              mightWait = 0;
              blurayTargetTitle = 0;
              return false;
            }
            // These are bound simply
            mightWait = milliTime = Math.min(Math.max(0, milliTime), getDurationMillis());
            blurayTargetTitle = 0;
          }
          else
          {
            try
            {
              player.load(currFile.getLegacyMediaType(), currFile.getLegacyMediaSubtype(), currFile.encodedBy, newSegFile,
                  currFile.getServerName(), sharePtr,
                  circSize);
              realDurMillis = player.getDurationMillis();
              player.setMute(globalMute); // be sure this is synchronized after reloading a player
            }
            catch (PlaybackException e)
            {
              if (Sage.DBG) e.printStackTrace();
              if (currFile.isTV() && (currFile.getNumSegments() <= newSeg ||
                  !currFile.getFile(newSeg).equals(newSegFile)))
              {
                if (Sage.DBG) System.out.println("VF detected file state change after load, reloading it...");
                closeMediaFile(true, false, false);
                // File has changed since tried to load it, do it again. This can
                // happen due to asynchronous verification between the client & server
                return timeSelected(milliTime, forward);
              }
              if (!hasPendingContentJob())
              {
                Catbert.processUISpecificHook("MediaPlayerError", new Object[] {
                    Sage.rez("Playback"), e.toString() }, uiMgr, true);
              }
              else if (Sage.DBG)
                System.out.println("Ignoring MediaPlayerError because we have another pending content loading job: " + e);
              mightWait = 0;
              return false;
            }
          }
        }

        // Be sure our new file is scaled properly
        refreshVideoSizing();
      }
    }
    if (Sage.DBG) System.out.println("VideoFrame.timeSelected2(" + Sage.df(milliTime) + ", " + forward+ ") currFile="+currFile + " realDur=" + realDurMillis);
    // This can't be synced around the player because the async callback gets the sync
    try
    {
      // NOTE: We do the max against 0 below because sometimes we'll end up with negative times due to clocks
      // being out of sync between client and server. This just ensures we're not passing a time to the media player
      // which may cause it to choke.
      // This can happen on the initial seek after the load, but that one doesn't cause problems
      if (currFile.isDVD())
      {
        if (!loadedFile) // don't seek on the initial DVD load
          player.seek(milliTime);
      }
      else if (currFile.isBluRay())
        player.seek(milliTime);
      else
      {
        long theTarget = matchTimeScale(Math.max(0, milliTime - currFile.getStart(segment)), true);
        // Don't do the seek to zero on a fast file switch'
        if (!didFastLoad)
          player.seek(theTarget);
      }
    }
    catch (Exception e)
    {
      if (!currFile.isTV())
      {
        if (Sage.DBG) e.printStackTrace();
        // time selection failed with an exception, we need to kill this file with an error
        // 1/26/07 - Narflex: I changed this to send the actual error...don't know why it was hiding it.
        Catbert.processUISpecificHook("MediaPlayerError", new Object[] {
            Sage.rez("Playback"), e.toString() }, uiMgr, true);
        //				Catbert.processUISpecificHook("MediaPlayerError", new Object[] { Sage.rez("Playback"),
        //					new PlaybackException(PlaybackException.VIDEO_RENDER, 0)}, uiMgr,
        //					true);
      }
      mightWait = 0;
      return false;
    }
    if (loadedFile)
    {
      if (oldState != MediaPlayer.PAUSE_STATE)
      {
        // If we loaded up a new file we need to start playing it if it already was playing
        player.play();
      }
      else player.pause();
      player.setClosedCaptioningState(uiMgr.getInt(prefs + LAST_CC_STATE, MediaPlayer.CC_DISABLED));
    }
    mightWait = 0;
    return true;
  }

  void asyncSeek(MediaPlayer mPlayer, long milliTime) throws PlaybackException
  {
    mPlayer.seek(milliTime);
  }

  public long getBaseMediaTimeMillis(boolean makeItQuick)
  {
    // We want to keep the UI responsive and this gets called a lot when that's doing stuff,
    MediaFile theFile = currFile;
    if (theFile == null) return 0;
    if (makeItQuick)
    {
      if (mightWait != 0) return mightWait - theFile.getStart(segment);
    }
    /*
     * NOTE: Scaling this automatically with matchTimeScale screws up the music visualizations because
     * we're not reporting accurate time then!!!!
     */
    MediaPlayer mp = player;
    if (mp != null && ((mp.getPlaybackCaps() & MediaPlayer.LIVE_CAP) != 0))
    {
      return Sage.time() - theFile.getStart(segment);
    }
    long offsetTime = mp != null && isMediaPlayerLoaded() ? mp.getMediaTimeMillis() : 0;
    // Protect against erroneously large current time values
    long recDur = theFile.getRecordDuration();
    if (recDur > 10000 && offsetTime > recDur * 1.5)
      return 0;
    else
      return offsetTime;
  }

  public long getMediaTimeMillis() { return getMediaTimeMillis(false); }
  public long getMediaTimeMillis(boolean makeItQuick)
  {
    // We want to keep the UI responsive and this gets called a lot when that's doing stuff,
    if (makeItQuick)
    {
      if (mightWait != 0) return mightWait;
    }
    MediaFile theFile = currFile;
    if (theFile == null) return 0;
    if (theFile.isLiveStream()) return Sage.time();
    MediaPlayer mp = player;
    if (theFile.isDVD() || theFile.isBluRay())
    {
      if (mp != null && isMediaPlayerLoaded())
        return mp.getMediaTimeMillis();
      else if (preparedForReload)
        return targetTimeForReload;
      else
        return 0;
    }

    long offsetTime;
    if (mp != null && isMediaPlayerLoaded())
    {
      offsetTime = mp.getMediaTimeMillis();
      offsetTime = matchTimeScale(offsetTime, false);
      // Protect against erroneously large current time values
      if (((mp.getPlaybackCaps() & MediaPlayer.LIVE_CAP) == 0) && (offsetTime > theFile.getRecordDuration() * 1.5))
        offsetTime = theFile.getDuration(segment);
    }
    else if (preparedForReload)
    {
      offsetTime = matchTimeScale(targetTimeForReload - theFile.getStart(segment), false);
    }
    else
      offsetTime = matchTimeScale(0, false);
    if (offsetTime + theFile.getStart(segment) > theFile.getEnd(segment) + 12*Sage.MILLIS_PER_HR)
      return theFile.getEnd(segment);
    else
      return offsetTime + theFile.getStart(segment);
  }

  private boolean hasValidRealDur()
  {
    // The 15 hours is the longest duration for a 100GB file, which is bigger than anything we'd ever want
    // to handle. (hopefully) Changed it to 14, someone had a log that caused an issue cause they had a 12.5Mbps quality.
    return (realDurMillis > 0 && realDurMillis < 14*Sage.MILLIS_PER_HR && isMediaPlayerLoaded());
  }

  private long matchTimeScale(long myMilliTime, boolean myTime)
  {
    MediaFile theFile = currFile;
    if (theFile == null || !hasValidRealDur() || theFile.isRecording(segment) ||
        (theFile.isMusic() && Sage.WINDOWS_OS) || disableTimeScaling) // fix for VBR MP3s on Windows
    {
      return myMilliTime;
    }
    long myDur = currFile.getDuration(segment);
    double durScale = ((double)realDurMillis)/myDur;
    //System.out.println("VideoFrame duration Scaling=" + durScale + " myDur=" +
    //	myDur + " realDur=" + realDurMillis + " myMilliTime=" + myMilliTime);
    if (myTime)
      return Math.round(myMilliTime * durScale);
    else
      return Math.round(myMilliTime / durScale);
  }

  private void logFileWatch()
  {
    if (currFile != null && !loggedWatch && Permissions.hasPermission(Permissions.PERMISSION_WATCHEDTRACKING, uiMgr))
    {
      if (currFile.isDVD() || currFile.isBluRay())
      {
        Airing doneAir = currFile.getContentAiring();
        if (doneAir != null)
        {
          if (getDVDDomain() != 4)
          {
            if (Sage.DBG) System.out.println("Clearing DVD watched info for " + currFile);
            BigBrother.clearWatched(doneAir);
          }
          else
          {
            long theTime = getMediaTimeMillis();
            if (Sage.DBG) System.out.println("Setting DVD watched info for " + currFile + " title=" + getDVDTitle() + " Time=" + theTime);
            BigBrother.setWatched(doneAir, 0, theTime, realWatchStart, Sage.time(), getDVDTitle(), false);
            loggedWatch = true;
          }
        }
      }
      else
      {
        Airing doneAir = currFile.getContentAiring();
        if (doneAir != null)
        {
          long recTime = currFile.getRecordTime();
          long theTime = getMediaTimeMillis();
          BigBrother.setWatched(doneAir, recTime, theTime,
              realWatchStart, Sage.time(), false);
          loggedWatch = true;
          if (Sage.DBG) System.out.println("VF logFileWatch doneAir=" + doneAir + " theTime=" + Sage.df(theTime) +
              " recTime=" + Sage.df(recTime));
        }
      }
      //System.out.println("VideoFrame logging file watch for " + doneAir + " start=" + Sage.df(currFile.getRecordTime()) +
      //	" end=" + Sage.df(currFile.getRecordEnd()));
    }
  }

  private void closeMediaFile(boolean killFile, boolean logThisWatch, boolean fixNativeResolution)
  {
    if (player == null)
    {
      if (currFile != null)
        currFile.cleanupLocalFile();
      currFile = null;
      downer = null;
      return;
    }
    if (logThisWatch)
      logFileWatch();
    // This will prevent stop from hanging waiting for more data on the async read
    // There are asynchronous cases where this is called if a client dies during a close_mf call; so protect
    // against NPE errors when that happens
    MediaPlayer myPlaya = player;
    if (myPlaya != null)
      myPlaya.inactiveFile();
    videoComp.setVisible(false);
    if (isDVD())
    {
      removeMouseListeners(dvdMouseListener);
      removeMouseMotionListeners(dvdMouseMotionListener);
    }
    long theDur = getDurationMillis();
    long theTime = getMediaTimeMillis();
    int chapNum = getDVDChapter();
    int titleNum = getDVDTitle();
    myPlaya = player;
    if (killFile)
    {
      if (myPlaya != null)
      {
        myPlaya.free();
        player = null;
      }
    }
    else if (myPlaya != null)
    {
      // Don't stop the player here because that'll break fast file switching.
      //			myPlaya.stop();
    }
    if (fixNativeResolution)
    {
      MiniClientSageRenderer render = null;
      if (uiMgr.getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
      {
        render = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
        if (render != null && (render.getResolutionOptions()) != null &&
            (render.getResolutionOptions().length) > 0 &&
            uiMgr.getBoolean("native_output_resolution_switching", false) && (currFile == null || !currFile.isMusic()))
        {
          sage.media.format.VideoFormat[] resOptions = render.getResolutionFormats();
          sage.media.format.VideoFormat bestChoice = null;
          for (int i = 0; i < resOptions.length; i++)
          {
            if (bestChoice == null)
              bestChoice = resOptions[i];
            else
            {
              if (bestChoice.getHeight() < resOptions[i].getHeight() ||
                  (bestChoice.getHeight() == resOptions[i].getHeight() && bestChoice.isInterlaced() && !resOptions[i].isInterlaced()) ||
                  (bestChoice.getHeight() == resOptions[i].getHeight() && !bestChoice.isInterlaced() && !resOptions[i].isInterlaced() &&
                  bestChoice.getFps() < resOptions[i].getFps()))
              {
                bestChoice = resOptions[i];
              }
            }
          }
          if (Sage.DBG) System.out.println("Using native resolution matching to switch output resolution back to optimal UI resolution=" + bestChoice);
          render.pushResolutionChange(bestChoice.getFormatName());
        }
      }
    }
    if (currFile != null)
      currFile.cleanupLocalFile();
    if (subHandler != null)
      subHandler.cleanup();
    if (ccHandler != null)
      ccHandler.cleanup();
    if ( currFile != null ) {
      sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYBACK_STOPPED,
          new Object[] { sage.plugin.PluginEventManager.VAR_MEDIAFILE, currFile, sage.plugin.PluginEventManager.VAR_UICONTEXT, uiMgr.getLocalUIClientName(),
          sage.plugin.PluginEventManager.VAR_DURATION, new Long(theDur), sage.plugin.PluginEventManager.VAR_MEDIATIME, new Long(theTime),
          sage.plugin.PluginEventManager.VAR_CHAPTERNUM, new Integer(chapNum), sage.plugin.PluginEventManager.VAR_TITLENUM, new Integer(titleNum) });
    }
    subHandler = null;
    chapterPoints = null;
    ccHandler = null;
    lastSubType = null;
    embeddedSubStreamType = null;
    currFile = null;
    downer = null;
  }

  private long getRealDurMillis()
  {
    if (!hasValidRealDur())
    {
      return currFile.getDuration(segment);
    }
    else
      return realDurMillis;
  }

  // When changing the title for a BluRay; we set our target title and then reload the media player
  public void setBluRayTargetTitle(int target)
  {
    blurayTargetTitle = target;
    reloadFile();
  }

  public int getBluRayTargetTitle()
  {
    return blurayTargetTitle;
  }

  public void play() { submitJob(new VFJob(PLAY)); }
  public void pause() { submitJob(new VFJob(PAUSE)); }
  public void playPause() { submitJob(new VFJob(PLAY_PAUSE)); }
  public void ff() { submitJob(new VFJob(TIME_ADJUST, uiMgr.getLong(prefs + FF_TIME, 10000L))); }
  public void rew() { submitJob(new VFJob(TIME_ADJUST, uiMgr.getLong(prefs + REW_TIME, -10000L))); }
  public void ff2() { submitJob(new VFJob(TIME_ADJUST, uiMgr.getLong(prefs + FF_TIME2, 150000L))); }
  public void rew2() { submitJob(new VFJob(TIME_ADJUST, uiMgr.getLong(prefs + REW_TIME2, -150000L))); }
  public void timeJump(long timeMillis) { submitJob(new VFJob(TIME_SET, timeMillis)); }
  public void playbackControl(int code) { playbackControl(code, 0, 0); }
  public void playbackControl(int code, long param1, long param2)
  {
    if (code == DVD_CONTROL_MENU && !isPlayin() && currFile != null && currFile.isDVD())
    {
      // If we're paused in a DVD and they want to go to the menu we should start playing it
      if (Sage.DBG) System.out.println("Resuming DVD playback before transitioning to DVD menu");
      play();
    }
    submitJob(new VFJob(DIRECT_CONTROL_MSG, code, param1, param2));
  }
  public void reloadFile() { submitJob(new VFJob(RELOAD_MF)); }
  public void prepareForReload()
  {
    if (preparedForReload) return; // redundant
    if (!hasFile()) return; // pointless
    if (Sage.DBG) System.out.println("Preparing the media player for a reload...");
    // We need to wait until this job is processed so the resources are cleaned up in the player
    if (Sage.WINDOWS_OS && java.awt.EventQueue.isDispatchThread())
      throw new InternalError("Cannot call VideoFrame.prepareForReload from the Event Thread!");
    // If Watch() was called from the STV and then CloseAndWaitUntilClosed() was called it's possible that
    // submitting the Close_MF job won't do anything and will be ignored. Therefore we need to hold up
    // performing the close until the watch request is completely processed.
    while (processingWatchRequest && alive)
    {
      if (Sage.DBG) System.out.println("Waiting on processing of watch request before file is reloaded...");
      try{Thread.sleep(50);}catch(Exception e){}
    }
    VFJob newJob = new VFJob(RELOAD_PREPARE_MF);
    submitJob(newJob);
    synchronized (queueLock)
    {
      while (watchQueue.contains(newJob) && alive)
      {
        try{ queueLock.wait(5000); } catch(InterruptedException e){}
      }
    }
    if (Sage.DBG) System.out.println("MediaPlayer is ready for the reload.");
  }
  public void faster()
  {
    setRate(getRate() * 2);
  }
  public void slower()
  {
    setRate(getRate() / 2);
  }
  public void setRate(float d) { submitJob(new VFJob(RATE_SET, d)); }
  public float getRate()
  {
    MediaPlayer currPlayer = player;
    return (currPlayer == null) ? 1.0f : currPlayer.getPlaybackRate();
  }
  public void smoothFF() { submitJob(new VFJob(SMOOTH_FORWARD)); }
  public void smoothRew() { submitJob(new VFJob(SMOOTH_REVERSE)); }

  public java.awt.Dimension getVideoSize()
  {
    MediaPlayer mp = player;
    if (mp != null)
      return mp.getVideoDimensions();
    else
      return null;
  }
  // Returns 0 for fill aspect ratio mode or unknown aspect ratio
  public float getCurrentAspectRatio()
  {
    return getCurrentAspectRatio(0, 0);
  }
  public float getCurrentAspectRatio(int knownARx, int knownARy)
  {
    switch (aspectRatioMode)
    {
      case BasicVideoFrame.ASPECT_16X9:
        return (16.0f/9.0f) / getPixelAspectRatio();
      case BasicVideoFrame.ASPECT_4X3:
        return (4.0f/3.0f) / getPixelAspectRatio();
      case BasicVideoFrame.ASPECT_SOURCE:
        MediaPlayer mp = player;
        if (mp instanceof DVDMediaPlayer)
        {
          float mpAR = (knownARx != 0 && knownARy != 0) ? ((float)knownARx/((float)knownARy)) : ((DVDMediaPlayer)mp).getCurrentAspectRatio();
          if (mpAR != 0)
          {
            return mpAR / getPixelAspectRatio();
          }
        }
        MediaFile mf = currFile;
        if (mf != null)
          return ((knownARx != 0 && knownARy != 0) ? ((float)knownARx/((float)knownARy)) : mf.getPrimaryVideoAspectRatio(false)) / getPixelAspectRatio();
    }
    return 0;
  }

  public void volumeUp()
  {
    setVolume(getVolume() + volumeStep);
  }
  public void volumeDown()
  {
    setVolume(getVolume() - volumeStep);
  }

  public float getVolume() // between 0 and 1
  {
    MediaPlayer playa = player;
    if (!uiMgr.getBoolean("media_player_uses_system_volume", Sage.WINDOWS_OS && !Sage.VISTA_OS))
    {
      if (playa != null)
        return playa.getVolume();
      else
        return 1;
    }
    else// if (Sage.WINDOWS_OS)
    {
      synchronized (volumeLock)
      {
        return getSystemVolume0();
      }
    }
    //else
    //	return 1;
  }

  public void setVolume(float x)
  {
    x = Math.min(1.0f, Math.max(0.0f, x));
    MediaPlayer playa = player;
    if (!uiMgr.getBoolean("media_player_uses_system_volume", Sage.WINDOWS_OS && !Sage.VISTA_OS))
    {
      if (playa != null)
        playa.setVolume(x);
    }
    else// if (Sage.WINDOWS_OS)
    {
      synchronized (volumeLock)
      {
        setSystemVolume0(x);
      }
    }
  }

  public int getPlayerState()
  {
    MediaPlayer mp = player;
    if (mp == null)
      return MediaPlayer.NO_STATE;
    else
      return mp.getState();
  }

  public boolean isPlayin()
  {
    int ps = getPlayerState();
    return ps == MediaPlayer.PLAY_STATE || ps == MediaPlayer.EOS_STATE;
  }

  public boolean isMediaPlayerLoaded()
  {
    int state = getPlayerState();
    return (state == MediaPlayer.LOADED_STATE) || (state == MediaPlayer.PAUSE_STATE) || (state == MediaPlayer.PLAY_STATE) ||
        (state == MediaPlayer.EOS_STATE);
  }

  public boolean isNonBlendableVideoPlayerLoaded()
  {
    MediaPlayer mp = player;
    return mp != null && mp.getTransparency() != MediaPlayer.TRANSLUCENT;
  }

  public boolean isColorKeyingEnabled()
  {
    MediaPlayer mp = player;
    // For miniclients we let them force the colorkeying to on or off and don't let property control it
    return mp != null && mp.getTransparency() == MediaPlayer.BITMASK && mp.getColorKey() != null &&
        (uiMgr.getBoolean("overlay_color_keying_enabled", true) || uiMgr.getUIClientType() == UIClient.REMOTE_UI);
  }

  public java.awt.Color getColorKey()
  {
    MediaPlayer mp = player;
    if (mp != null)
      return mp.getColorKey();
    else
      return null;
  }

  public int getCCState()
  {
    // Narflex 02/28/12 - Just return the set value here...it shouldn't depend on whether we actually have a real CC
    // decoder loaded or not
    return uiMgr.getInt(prefs + LAST_CC_STATE, MediaPlayer.CC_DISABLED);
    /*MediaPlayer mp = player;
		if (ccHandler != null)
			return (ccHandler.isEnabled() ? BasicVideoFrame.getCCStateCode(ccHandler.getCurrLanguage()) : MediaPlayer.CC_DISABLED);
		else if (mp != null)
			return mp.getClosedCaptioningState();
		else
			return MediaPlayer.CC_DISABLED;*/
  }

  public boolean setCCState(int ccState)
  {
    uiMgr.putInt(prefs + LAST_CC_STATE, ccState);
    MediaPlayer mp = player;
    if (ccHandler != null)
    {
      boolean rv = false;
      if (ccState == MediaPlayer.CC_DISABLED)
      {
        if (ccHandler.isEnabled())
        {
          ccHandler.setEnabled(false);
          rv = true;
        }
      }
      else
      {
        String newLang = BasicVideoFrame.getCCStateName(ccState);
        if (!ccHandler.isEnabled() || !newLang.equals(ccHandler.getCurrLanguage()))
        {
          ccHandler.setEnabled(true);
          ccHandler.setCurrLanguage(newLang);
          rv = true;
        }
      }
      if (rv)
      {
        Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
        kick();
      }
      return rv;
    }
    else if (mp != null)
    {
      boolean rv = mp.setClosedCaptioningState(ccState);
      if (rv)
        Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
      return rv;
    }
    else
      return false;
  }

  public void setMute(boolean x)
  {
    globalMute = x;
    MediaPlayer mp = player;
    if (uiMgr.getBoolean("media_player_uses_system_mute", false))
    {
      try
      {
        setSystemMute0(x);
      }catch (Throwable t){ System.out.println("ERROR:" + t);}
    }
    else if (mp != null)
      mp.setMute(globalMute || (muteOnAlt && getRate() != 1));
  }

  public boolean getMute()
  {
    if (uiMgr.getBoolean("media_player_uses_system_mute", false))
    {
      try
      {
        return getSystemMute0();
      }catch (Throwable t){ System.out.println("ERROR:" + t);}
    }
    return globalMute;
  }

  public boolean isFileRecording()
  {
    MediaFile mf = currFile;
    return (mf != null) && mf.isRecording();
  }

  public boolean isDVD()
  {
    MediaFile mf = currFile;
    return (mf != null) && mf.isDVD();
  }

  public boolean isBluRay()
  {
    MediaFile mf = currFile;
    return (mf != null) && mf.isBluRay();
  }

  public long getDurationMillis()
  {
    MediaFile mf = currFile;
    MediaPlayer currPlayer = player;
    if (mf != null)
    {
      if (mf.isDVD() || mf.isBluRay())
      {
        if (currPlayer != null)
          return currPlayer.getDurationMillis();
        return 0;
      }
      long rv = mf.getDuration(segment >= 0 ? segment : 0);
      if (rv > 1)
        return rv;
    }
    if (currPlayer != null)
      return currPlayer.getDurationMillis();
    return 0;
  }

  public boolean isShowingDVDMenu()
  {
    return isDVD() && areDvdButtonsVisible(); // (currDVDDomain == 2 || currDVDDomain == 3) &&
  }

  public int getDVDTitle()
  {
    MediaPlayer mp = player;
    if (mp instanceof DVDMediaPlayer)
      return ((DVDMediaPlayer) mp).getDVDTitle();
    else
      return 0;
  }
  public int getDVDTotalTitles()
  {
    MediaPlayer mp = player;
    if (mp instanceof DVDMediaPlayer)
      return ((DVDMediaPlayer) mp).getDVDTotalTitles();
    else
      return 0;
  }
  public int getDVDChapter()
  {
    long[] chps = chapterPoints;
    if (chps != null)
    {
      long currTime = getBaseMediaTimeMillis(true);
      for (int i = 0; i < chapterPoints.length; i++)
        if (chapterPoints[i] > currTime)
          return i;
      return chapterPoints.length;
    }
    MediaPlayer mp = player;
    if (mp instanceof DVDMediaPlayer)
      return ((DVDMediaPlayer) mp).getDVDChapter();
    else
      return 0;
  }
  public int getDVDTotalChapters()
  {
    long[] chps = chapterPoints;
    if (chps != null)
    {
      return chps.length;
    }
    MediaPlayer mp = player;
    if (mp instanceof DVDMediaPlayer)
      return ((DVDMediaPlayer) mp).getDVDTotalChapters();
    else
      return 0;
  }
  public int getDVDDomain()
  {
    MediaPlayer mp = player;
    if (mp instanceof DVDMediaPlayer)
      return ((DVDMediaPlayer) mp).getDVDDomain();
    else
      return 0;
  }
  public int getDVDAngle()
  {
    MediaPlayer mp = player;
    if (mp instanceof DVDMediaPlayer)
      return ((DVDMediaPlayer) mp).getDVDAngle();
    else
      return 0;
  }
  public int getDVDTotalAngles()
  {
    MediaPlayer mp = player;
    if (mp instanceof DVDMediaPlayer)
      return ((DVDMediaPlayer) mp).getDVDTotalAngles();
    else
      return 0;
  }
  public String getDVDLanguage()
  {
    MediaPlayer mp = player;
    if (mp instanceof DVDMediaPlayer)
      return ((DVDMediaPlayer) mp).getDVDLanguage();
    else
      return "";
  }
  public String[] getDVDAvailableLanguages()
  {
    MediaPlayer mp = player;
    if (mp instanceof DVDMediaPlayer)
      return ((DVDMediaPlayer) mp).getDVDAvailableLanguages();
    else
      return Pooler.EMPTY_STRING_ARRAY;
  }
  public String getDVDSubpicture()
  {
    sage.media.sub.SubtitleHandler subby = subHandler;
    if (subby != null && !subby.mpControlled())
    {
      return subby.isEnabled() ? subby.getCurrLanguage() : null;
    }
    MediaPlayer mp = player;
    if (mp instanceof DVDMediaPlayer)
      return ((DVDMediaPlayer) mp).getDVDSubpicture();
    else
      return "";
  }
  public String[] getDVDAvailableSubpictures()
  {
    sage.media.sub.SubtitleHandler subby = subHandler;
    if (subby != null && !subby.mpControlled())
    {
      return subby.getLanguages();
    }
    MediaPlayer mp = player;
    if (mp instanceof DVDMediaPlayer)
      return ((DVDMediaPlayer) mp).getDVDAvailableSubpictures();
    else
      return Pooler.EMPTY_STRING_ARRAY;
  }

  public long getAvailMediaStart()
  {
    MediaFile mf = currFile;
    if (mf != null)
    {
      if (downer != null && downer.needsTracking())
        return downer.getMinSeekTimestamp() + mf.getRecordTime();
      else
        return (mf.isDVD() || mf.isBluRay()) ? 0 : mf.getRecordTime();
    }
    else
      return 0;
  }

  public long getAvailMediaEnd()
  {
    MediaFile mf = currFile;
    if (mf != null)
    {
      if (downer != null && downer.needsTracking())
        return Math.min(mf.getContentAiring().getEndTime(),
            downer.getMaxSeekTimestamp() + mf.getRecordTime());
      else if (mf.isRecording())
        return Math.min(mf.getRecordEnd(), Sage.time() - getEncodeToPlaybackDelay());
      else if (mf.isDVD() || mf.isBluRay())
        return getDurationMillis();
      else
        return mf.getRecordEnd();
    }
    else
      return Sage.time() - getEncodeToPlaybackDelay();
  }

  public String getMediaTitle()
  {
    MediaFile mf = currFile;
    if (mf == null)
      return "";
    else
      return mf.getMediaTitle();
  }

  public MediaFile getCurrFile()
  {
    return currFile;
  }

  public int getCurrSegment()
  {
    return segment;
  }

  public Playlist getPlaylist()
  {
    synchronized (playlistChain)
    {
      if (playlistChain.isEmpty())
        return null;
      else
        return (Playlist) playlistChain.firstElement();
    }
  }

  public int getPlaylistIndex()
  {
    synchronized (playlistChainIndices)
    {
      if (playlistChainIndices.isEmpty())
        return 0;
      else
        return ((Integer) playlistChainIndices.firstElement()).intValue();
    }
  }

  public boolean isLivePreview()
  {
    MediaPlayer playa = player;
    if (playa != null && (playa instanceof DShowLivePlayer || playa instanceof DShowSharedLiveMediaPlayer || playa instanceof MacLivePlayer))
      return true;
    else
      return false;
  }

  public boolean hasFile() { return currFile != null; }
  public boolean isLoadingOrLoadedFile()
  {
    if (currFile != null || processingWatchRequest || aboutToDoWatchRequest) return true;
    synchronized (queueLock)
    {
      for (int i = watchQueue.size() - 1; i >= 0; i--)
      {
        if(watchQueue.elementAt(i) == null) continue;
        int currid = ((VFJob) watchQueue.elementAt(i)).id;
        if (currid == WATCH_MF || currid == START_PLAYLIST)
          return true;
      }
    }
    return (currFile != null);
  }

  // When doing channel surfing, the source file is the one that'll be loaded, not necessarily
  // the one that is currently loaded
  private MediaFile getSurfFile()
  {
    synchronized (queueLock)
    {
      for (int i = watchQueue.size() - 1; i >= 0; i--)
      {
        VFJob job = (VFJob) watchQueue.get(i);
        if (job.id == LOAD_MF || job.id == WATCH_MF)
          return job.file;
      }
      return currFile;
    }
  }

  private boolean isPlayingMusicPlaylist()
  {
    try
    {
      return !playlistChain.isEmpty() && (playlistChain.firstElement() instanceof Playlist) &&
          ((Playlist) playlistChain.firstElement()).isMusicPlaylist();
    }
    catch (Exception e)
    {
      System.out.println("Warning in VF:" + e);
      e.printStackTrace();
      // simultaneous access could cause this
      return false;
    }
  }

  public int surfUp(){ return channelSurf(false, null);}
  public int surfDown(){return channelSurf(true, null);}
  private boolean doingChannelSurf = false;
  private int channelSurf(boolean isDown, String targetChan)
  {
    if (doingChannelSurf) return WATCH_FAILED_SURF_CONTEXT;
    doingChannelSurf = true;
    channelChangeTime = Sage.time();
    try
    {
      // NARFLEX: 6/26/08 - If this is the now playing playlist with a single song in it; then treat it just like
      // only that song was played so we can go track up/down correctly.
      if (!playlistChain.isEmpty() && (nowPlayingPlaylist == null || playlistChain.firstElement() != getNowPlayingList() ||
          !nowPlayingPlaylist.isSingleItemPlaylist()))
      {
        // We don't support random access within playlists at the present time, mainly
        // because of playlist recursion
        if (targetChan != null)
        {
          try{ return jumpToPlaylistIndex(Integer.parseInt(targetChan)); }catch(Exception e){}
        }

        if (isRandomPlaylistPlayback())
        {
          return playbackNextRandomPlaylist();
        }

        if (isDown)
          return watchPrevInPlaylist();
        else
          return watchNextInPlaylist();
      }
      MediaFile surfFile = getSurfFile();
      if (surfFile == null || (!isLiveControl() && !surfFile.isMusic() && targetChan == null)) return WATCH_FAILED_SURF_CONTEXT;

      if (surfFile.isMusic())
      {
        if (surfFile.isAnyLiveStream())
        {
          // This is a radio input, do the surfing in radio frequency
          Airing startFrom = surfFile.getContentAiring();
          int currChan = 88100000;
          try
          {
            currChan = Integer.parseInt(targetChan == null ? startFrom.getChannelNum(0) : targetChan);
          }
          catch (Exception e){}
          currChan = Math.max(88100000, Math.min(108100000, currChan));
          if (targetChan == null)
          {
            if (isDown)
            {
              if (currChan < 88300000)
                currChan = 108100000;
              else
                currChan -= 200000;
            }
            else
            {
              if (currChan > 107900000)
                currChan = 88100000;
              else
                currChan += 200000;
            }
          }
          targetChan = Integer.toString(currChan);
          submitJob(new VFJob(CHANNEL_TUNE, surfFile, targetChan));
          return WATCH_OK;
        }
        // if there's a target chan, it's a track number, otherwise do a file up/down

        // Play the next song in the list
        if (targetChan == null && uiMgr.getBoolean(RANDOM_MUSIC_PLAYBACK, false))
        {
          return playbackNextRandomMusic();
        }
        MediaFile[] mfs;
        if (cachedSortedMusicFiles == null || cachedSortedMusicFilesTimestamp < Wizard.getInstance().getLastModified(DBObject.MEDIA_MASK_MUSIC))
        {
          long newTimestamp = Wizard.getInstance().getLastModified(DBObject.MEDIA_MASK_MUSIC);
          mfs = Wizard.getInstance().getFiles(DBObject.MEDIA_MASK_MUSIC, true);
          java.util.Arrays.sort(mfs, musicFileComparator);
          cachedSortedMusicFiles = mfs;
          cachedSortedMusicFilesTimestamp = newTimestamp;
        }
        else
          mfs = cachedSortedMusicFiles;
        int idx = java.util.Arrays.binarySearch(mfs, surfFile, musicFileComparator);
        if (targetChan != null)
        {
          try
          {
            isDown = Integer.parseInt(targetChan) < mfs[idx].getContentAiring().partsB;
          }
          catch (Exception e){}
        }
        while (idx >= 0 && idx + 1 < mfs.length)
        {
          idx += (isDown ? -1 : 1);
          if ((targetChan != null && !surfFile.isSameAlbum(mfs[idx])) || idx < 0)
            return WATCH_FAILED_PLAYLIST_OVER;
          if (mfs[idx].isMusic() && !mfs[idx].isAnyLiveStream() &&
              (targetChan == null || targetChan.equals(Byte.toString(mfs[idx].getContentAiring().partsB))))
          {
            final MediaFile playMeNow = mfs[idx];
            if (Sage.DBG) System.out.println("VF is playing next file of:" + playMeNow + " targetChan=" + targetChan);
            //java.awt.EventQueue.invokeLater(new Runnable()
            //{
            //	public void run()
            //	{
            return watch(playMeNow.getContentAiring());
            //	}
            //});
            //return;
          }
        }
        return WATCH_FAILED_PLAYLIST_OVER;
      }

      if (uiMgr.getBoolean("ui/reverse_channel_surf", false))
        isDown = !isDown;

      Airing startFrom = surfFile.getContentAiring();
      if (targetChan != null)
      {
        if (targetChan.equals(startFrom.getChannelNum(0)))
          return WATCH_OK;
        isDown = uiMgr.channelNumSorter.compare(targetChan, startFrom.getChannelNum(0)) < 0;
      }
      // I do the new Airing(0).getClass() so the obfuscator can obfuscate the Airing class
      // 1/15/04 I removed the Airing class comparison so live MediaFiles get through,
      // I'm not sure why I had this there in the first place.
      if (startFrom != null/* && startFrom.getClass() == new Airing(0).getClass()*/)
      {
        if (Sage.DBG) System.out.println("Processing channel change request isDown=" + isDown + " targetChan=" + targetChan);
        Channel[] chans = sortedViewableChans;
        Wizard wiz = Wizard.getInstance();
        if (chans == null)
        {
          // Generate the list of Channels that match the EPG
          java.util.ArrayList goodChans = new java.util.ArrayList();
          Channel[] currChans = wiz.getChannels();
          for (int j = 0; j < currChans.length; j++)
            if (currChans[j].isViewable())
              goodChans.add(currChans[j]);

          chans = (Channel[]) goodChans.toArray(new Channel[0]);
          java.util.Arrays.sort(chans, uiMgr.channelNumSorter);
          sortedViewableChans = chans;
        }
        String currChan = startFrom.getChannelNum(0);
        int currStat = startFrom.getStationID();
        int chanIdx = -1;
        for (int i = 0; i < chans.length; i++)
        {
          if (chans[i].stationID == currStat)
          {
            chanIdx = i;
            break;
          }
        }
        if (chanIdx == -1)
        {
          for (int i = 0; i < chans.length; i++)
          {
            if (chans[i].getNumber(0).equals(currChan))
            {
              chanIdx = i;
              break;
            }
          }
        }
        int startIdx = chanIdx;
        startFrom = null;
        if (chans.length > 0)
        {
          do
          {
            chanIdx += (isDown ? -1 : 1);
            if (chanIdx < 0)
              chanIdx = chans.length - 1;
            else if (chanIdx >= chans.length)
              chanIdx = 0;
            if (targetChan != null && uiMgr.channelNumSorter.compare(targetChan, chans[chanIdx].getNumber(0)) != 0)
              continue;
            // Skip the channel surf if the channel number is identical since its pointless.
            // THIS IS NOT ALWAYS TRUE, especially for digital TV...so don't skip it!
            //if (currChan.equals(chans[chanIdx].getNumber(0)))
            //	continue;
            Airing[] airs = wiz.getAirings(chans[chanIdx].stationID,
                Sage.time(), Sage.time() + 1,
                false);
            if (airs.length > 0)
              startFrom = airs[0];
            else
              startFrom = null;
          } while ((startFrom == null) && (chanIdx != startIdx));
        }
        if (startFrom != null)
        {
          if (surfFile.isAnyLiveStream() && !surfFile.isLiveNetworkStream())
          {
            // Forced channel tune
            submitJob(new VFJob(CHANNEL_TUNE, surfFile, startFrom.getChannelNum(0)));
            return WATCH_OK;
          }
          else
            return watch(startFrom);
        }
        else if (surfFile.isAnyLiveStream() && !surfFile.isLiveNetworkStream())
        {
          if (targetChan != null)
          {
            // Forced channel tune
            submitJob(new VFJob(CHANNEL_TUNE, surfFile, targetChan));
            return WATCH_OK;
          }
          else
          {
            CaptureDeviceInput liveInput = mmc.getCaptureDeviceInputNamed(surfFile.encodedBy);
            if (liveInput != null)
            {
              int maxChan = liveInput.getMaxChannel();
              int minChan = liveInput.getMinChannel();
              try
              {
                int theChan = Integer.parseInt(currChan);
                if (theChan > maxChan)
                  theChan = maxChan;
                else if (theChan < minChan)
                  theChan = minChan;
                else
                {
                  theChan += (isDown ? -1 : 1);
                  if (theChan > maxChan)
                    theChan = minChan;
                  else if (theChan < minChan)
                    theChan = maxChan;
                }
                submitJob(new VFJob(CHANNEL_TUNE, surfFile, Integer.toString(theChan)));
                return WATCH_OK;
              }
              catch (Exception e)
              {
                submitJob(new VFJob(CHANNEL_TUNE, surfFile, Integer.toString(minChan)));
                return WATCH_OK;
              }
            }
          }
        }
      }
      return WATCH_FAILED_SURF_CONTEXT;
    }
    finally
    {
      doingChannelSurf = false;
    }
  }
  public int surfToChan(String chanNum)
  {
    MediaFile surfFile = getSurfFile();
    if (surfFile != null && surfFile.isAnyLiveStream() && !surfFile.isLiveNetworkStream())
    {
      submitJob(new VFJob(CHANNEL_TUNE, surfFile, chanNum));
      return WATCH_OK;
    }
    return channelSurf(false, chanNum);
  }

  public boolean isLiveControl()
  {
    return liveControl;
  }

  public void inactiveFile(String inactiveFilename)
  {
    submitJob(new VFJob(INACTIVE_FILE, inactiveFilename));
  }

  void checkWatchComplete()
  {
    submitJob(new VFJob(WATCH_COMPLETE_CHECK));
  }

  public void closeAndWait()
  {
    //		if (Sage.WINDOWS_OS && java.awt.EventQueue.isDispatchThread())
    //			throw new InternalError("Cannot call VideoFrame.closeAndWait from the Event Thread!");
    // If Watch() was called from the STV and then CloseAndWaitUntilClosed() was called it's possible that
    // submitting the Close_MF job won't do anything and will be ignored. Therefore we need to hold up
    // performing the close until the watch request is completely processed.
    while (processingWatchRequest && alive)
    {
      if (Sage.DBG) System.out.println("Waiting on processing of watch request before file is closed...");
      try{Thread.sleep(50);}catch(Exception e){}
    }
    VFJob newJob = new VFJob(CLOSE_MF);
    newJob.useSyncClose = (Sage.WINDOWS_OS && java.awt.EventQueue.isDispatchThread());
    synchronized (queueLock)
    {
      submitJob(newJob);
    }
    while (true)
    {
      synchronized (queueLock)
      {
        if (!watchQueue.contains(newJob) || !alive)
          break;
      }
      if (Sage.WINDOWS_OS && asyncJobInWait != null && java.awt.EventQueue.isDispatchThread())
      {
        // This is a deadlock situation unless we do something about it. The best approach is
        // to process the job that the VF is waiting on, and then process our close job (and also remove it from the queue).
        // NOTE: 2/26/07 - Narflex - If the asyncJobInWait is a WATCH_MF job then we're going to do the close
        // after the WATCH which will cause the inserted LOAD_MF job to fail horribly. We'll check here if it's
        // a WATCH_MF, and if so we'll just discard it instead of processing it
        if (asyncJobInWait.id == WATCH_MF)
        {
          if (Sage.DBG) System.out.println("CloseAndWait is discarding the pending WATCH_MF job:" + asyncJobInWait);
          watchQueue.remove(asyncJobInWait);
        }
        else
        {
          if (Sage.DBG) System.out.println("CloseAndWait is executing the pending job synchronously now");
          processJob(asyncJobInWait);
        }
        if (Sage.DBG) System.out.println("CloseAndWait is executing the close job synchronously now");
        processJob(newJob);
        synchronized (queueLock)
        {
          syncCloseToExecuteNow = null;
          queueLock.notifyAll();
        }
        break;
      }
      else if (syncCloseToExecuteNow == newJob)
      {
        if (Sage.DBG) System.out.println("CloseAndWait is executing the close job synchronously now");
        processJob(newJob);
        synchronized (queueLock)
        {
          syncCloseToExecuteNow = null;
          queueLock.notifyAll();
        }
        break;
      }
      else
      {
        synchronized (queueLock)
        {
          if (!watchQueue.contains(newJob) || !alive)
            break;
          try{ queueLock.wait(5000); } catch(InterruptedException e){}
        }
      }
    }
    liveControl = false;
    controlledInput = null;
    seek.finishWatch(uiMgr);
  }

  public static boolean getEnablePC() { return Sage.getBoolean(VIDEOFRAME_KEY + '/' + ENABLE_PC, false); }
  public void setEnablePC(boolean x)
  {
    Sage.putBoolean(prefs + ENABLE_PC, x);
    NetworkClient.distributePropertyChange(prefs + ENABLE_PC);
  }

  public void setPCCode(String x)
  {
    Sage.put(prefs + PC_CODE, SageTV.oneWayEncrypt(x));
    NetworkClient.distributePropertyChange(prefs + PC_CODE);
  }
  public boolean checkPCCode(String x)
  {
    return SageTV.oneWayEncrypt(x).equals(Sage.get(prefs + PC_CODE, ""));
  }
  public boolean hasPCCode() { return Sage.get(prefs + PC_CODE, "").length() > 0; }
  public static String[] getPCRestrictions()
  {
    String str = Sage.get(VIDEOFRAME_KEY + '/' + PC_RESTRICT, "");
    java.util.StringTokenizer toker = new java.util.StringTokenizer(str, ",");
    String[] pcRestrict = new String[toker.countTokens()];
    for (int i = 0; i < pcRestrict.length; i++)
      pcRestrict[i] = toker.nextToken();
    java.util.Arrays.sort(pcRestrict);
    return pcRestrict;
  }
  public static void setPCRestrictions(String[] newRestrict)
  {
    String str = "";
    for (int i = 0; i < newRestrict.length; i++)
      str += newRestrict[i] + ",";
    Sage.put(VIDEOFRAME_KEY + '/' + PC_RESTRICT, str);
    NetworkClient.distributePropertyChange(VIDEOFRAME_KEY + '/' + PC_RESTRICT);
  }

  private boolean pcAiringCheck(Airing pcAir)
  {
    // Don't do parental controls checks for music files
    if (pcAir.isMusic())
      return true;
    if (!getEnablePC()) return true;
    String[] pcRestrict = getPCRestrictions();
    if (pcRestrict.length == 0) return true;
    String[] airDanger = pcAir.getRatingRestrictables();
    String limitsExceeded = "";
    for (int i = 0; i < airDanger.length; i++)
    {
      if (java.util.Arrays.binarySearch(pcRestrict, airDanger[i]) >= 0)
      {
        String currLimit = Channel.convertPotentialStationIDToName(airDanger[i]);
        if (limitsExceeded.length() == 0)
          limitsExceeded = currLimit;
        else
          limitsExceeded += ", " + currLimit;
      }
    }
    if (limitsExceeded.length() == 0)
      return true;

    if (!playlistChain.isEmpty())
    {
      if (!currPlaylistPassesPC)
      {
        if (Sage.DBG) System.out.println("Playlist being played back has been modified with PC content but PC access was not granted. Do not continue playback.");
        Catbert.processUISpecificHook("MediaPlayerError", new Object[] {
            Sage.rez("Playback"), Catbert.getWatchFailureObject(WATCH_FAILED_PARENTAL_CHECK_FAILED) }, uiMgr, true);
        return false;
      }
      else
        return true;
    }
    Object rv = Catbert.processUISpecificHook("RequestToExceedParentalRestrictions", new Object[] { pcAir, limitsExceeded }, uiMgr, false);
    return Catbert.evalBool(rv);
  }

  public static boolean pcAiringCheckExternal(Airing pcAir, UIClient callbackHandler)
  {
    // Don't do parental controls checks for music files
    if (pcAir.isMusic())
      return true;
    if (!getEnablePC()) return true;
    String[] pcRestrict = getPCRestrictions();
    if (pcRestrict.length == 0) return true;
    String[] airDanger = pcAir.getRatingRestrictables();
    String limitsExceeded = "";
    for (int i = 0; i < airDanger.length; i++)
    {
      if (java.util.Arrays.binarySearch(pcRestrict, airDanger[i]) >= 0)
      {
        String currLimit = Channel.convertPotentialStationIDToName(airDanger[i]);
        if (limitsExceeded.length() == 0)
          limitsExceeded = currLimit;
        else
          limitsExceeded += ", " + currLimit;
      }
    }
    if (limitsExceeded.length() == 0)
      return true;

    Object rv = callbackHandler.processUIClientHook("RequestToExceedParentalRestrictions", new Object[] { pcAir, limitsExceeded });
    return Catbert.evalBool(rv);
  }

  public boolean doesAiringRequirePC(Airing pcAir)
  {
    if (!getEnablePC()) return false;
    String[] pcRestrict = getPCRestrictions();
    if (pcRestrict.length == 0) return false;
    String[] airDanger = pcAir.getRatingRestrictables();
    String limitsExceeded = "";
    for (int i = 0; i < airDanger.length; i++)
    {
      if (java.util.Arrays.binarySearch(pcRestrict, airDanger[i]) >= 0)
      {
        if (limitsExceeded.length() == 0)
          limitsExceeded = airDanger[i];
        else
          limitsExceeded += ", " + airDanger[i];
      }
    }
    if (limitsExceeded.length() == 0)
      return false;
    else
      return true;
  }

  /**
   * Return an array of exceeded parental ratings for the given airing
   * @param pcAir Airing to check ratings against
   * @return String[] of exceeded ratings
   */
  public static String[] getPCRestrictionsForAiring(Airing pcAir) {
    // Don't do parental controls checks for music files
    if (pcAir.isMusic()) return Pooler.EMPTY_STRING_ARRAY;
    if (!getEnablePC()) return Pooler.EMPTY_STRING_ARRAY;
    String[] pcRestrict = getPCRestrictions();
    if (pcRestrict.length == 0) return Pooler.EMPTY_STRING_ARRAY;
    String[] airDanger = pcAir.getRatingRestrictables();
    List<String> limits = new ArrayList<String>();
    for (int i = 0; i < airDanger.length; i++) {
      if (java.util.Arrays.binarySearch(pcRestrict, airDanger[i]) >= 0) {
        String currLimit = Channel.convertPotentialStationIDToName(airDanger[i]);
        limits.add(currLimit);
      }
    }
    return limits.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public CaptureDeviceInput getControlledInput() { return controlledInput; }

  public boolean isCaptureDeviceControlled(CaptureDevice capDev)
  {
    CaptureDeviceInput testInput = controlledInput;
    return (testInput != null) && testInput.getCaptureDevice().equals(capDev);
  }

  // This returns true if there is another operation sitting in the job queue that would terminate
  // the currently processing playback request. This is used to avoid error situations where we are
  // transitioning between pieces of content quickly and one that we are trying to watch may have already
  // transitioned out to a state that is no longer playable
  private boolean hasPendingContentJob()
  {
    synchronized (queueLock)
    {
      // Start at index 1 because we are currently processing the one at position 0
      for (int i = 1; i < watchQueue.size(); i++)
      {
        VFJob testJob = (VFJob) watchQueue.get(i);
        int jobID = testJob.id;
        if (jobID == LOAD_MF || jobID == WATCH_MF || jobID == CLOSE_MF || jobID == START_PLAYLIST)
        {
          return true;
        }
      }
    }
    return false;
  }

  // Checks to make sure it's on a different channel since we use this for the prev channel command
  // 9/3/8 - Narflex - When we're going from one Airing to the next during live mode, the previousAiring
  // isn't going to be set to the current channel until the show ends....so we need to also check against the next show and
  // not just the last one (because the next one will get set correctly when it is closed)
  private void setPreviousAiring(Airing a, Airing dontSetIfSameChan)
  {
    if (previousAiring == null)
      previousAiring = a;
    else
    {
      if (a == null)
        return;
      if (a.getChannel() == previousAiring.getChannel() ||
          (dontSetIfSameChan != null && dontSetIfSameChan.getChannel() == a.getChannel()))
        return;
      previousAiring = a;
    }
  }

  public int surfPreviousChannel()
  {
    Airing prevAir = previousAiring;
    if (prevAir != null && prevAir.getSchedulingEnd() < Sage.time())
    {
      MediaFile mf = Wizard.getInstance().getFileForAiring(prevAir);
      if (mf == null)
      {
        Airing[] airs = Wizard.getInstance().getAirings(prevAir.getStationID(), Sage.time(), Sage.time() + 1, false);
        if (airs.length > 0)
          prevAir = airs[0];
      }
      else
      {
        // If we've watched the previous recording to the end then don't flip back to it, go to
        // what's currently on that channel instead.
        Watched w = Wizard.getInstance().getWatch(prevAir);
        if (w != null && w.getWatchEnd() > mf.getRecordEnd() - 5000)
        {
          Airing[] airs = Wizard.getInstance().getAirings(prevAir.getStationID(), Sage.time(), Sage.time() + 1, false);
          if (airs.length > 0)
            prevAir = airs[0];
        }
      }
    }
    return watch(prevAir);
  }
  java.awt.event.MouseListener getDvdMouseListener() { return dvdMouseListener; }
  java.awt.event.MouseMotionListener getDvdMouseMotionListener() { return dvdMouseMotionListener; }

  protected void createVideoHShiftTimer()
  {
    videoHShiftTimer = new java.util.TimerTask()
    {
      public void run()
      {
        java.awt.EventQueue.invokeLater(new Runnable()
        {
          public void run()
          {
            videoComp.invalidate();
            validate();
            uiMgr.trueValidate();
          }
        });
      }
    };
    uiMgr.addTimerTask(videoHShiftTimer, 0, videoHShiftFreq/1000);
  }

  public boolean areDvdButtonsVisible()
  {
    MediaPlayer mp = player;
    return (mp instanceof DVDMediaPlayer) && ((DVDMediaPlayer) mp).areDVDButtonsVisible();
  }

  private String getPluginClassForFile(MediaFile mf)
  {
    // Check for a file specific plugin
    String fileExt = IOUtils.getFileExtension(mf.getFile(0));
    if (mf.isDVD())
      fileExt = "dvd";
    if (mf.isBluRay())
      fileExt = "bluray";
    if (fileExt.length() > 0)
    {
      String mpExtPlugin = uiMgr.get("media_player_plugin_class/" + fileExt.toLowerCase(), "");
      if (mpExtPlugin.length() > 0)
      {
        return mpExtPlugin;
      }
    }
    return uiMgr.get("media_player_plugin_class", "");
  }

  public boolean hasMediaPlayer(MediaFile theFile)
  {
    String mpPlugin = getPluginClassForFile(theFile);
    if (mpPlugin.length() > 0)
    {
      return true;
    }
    else
    {
      if (mediaPlayerSetup == STBX25XX_MEDIA_PLAYER_SETUP)
      {
        if (theFile.isDVD())
        {
          // Check if it's a Placeshifter, if it is then DVD playback is not supported
          if (!Sage.getBoolean("enable_ps_dvd_playback", false) && uiMgr.getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
          {
            String ipdp = ((MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine()).getInputDevsProp();
            if (ipdp != null && ipdp.indexOf("MOUSE") != -1)
              return false;
          }
          return true;
        }
        else if (theFile.isBluRay())
        {
          // Only true for HD media extenders currently
          return (uiMgr.getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer) &&
              ((MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine()).isMediaExtender() &&
              ((MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine()).isSupportedVideoCodec("MPEG2-VIDEO@HL");
        }
        else if (theFile.getLegacyMediaSubtype() == MediaFile.MEDIASUBTYPE_MPEG2_PS ||
            theFile.getLegacyMediaSubtype() == MediaFile.MEDIASUBTYPE_MP3)
          return true;
        else if (//sage.media.format.MediaFormat.WMV9.equals(theFile.getPrimaryVideoFormat()) ||
            sage.media.format.MediaFormat.WMA9LOSSLESS.equals(theFile.getPrimaryAudioFormat()) ||
            (theFile.getFileFormat() != null && theFile.getFileFormat().isDRMProtected()))
          return false; // Transcode formats we can't support yet
        else if (theFile.isLiveStream())
        {
          // We can only watch these if they're from a multicast source
          CaptureDevice capdev = theFile.guessCaptureDeviceFromEncoding();
          if (capdev != null && capdev instanceof MulticastCaptureDevice)
            return true;
          else
            return false;
        }
        else
          return true; //Sage.getBoolean("enable_transcode_streaming", true);
      }
      else if (Sage.WINDOWS_OS)
      {
        if (theFile.getFileFormat() != null && theFile.getFileFormat().isDRMProtected())
          return false;
        if (theFile.isLiveStream())
          return true;//new DShowLivePlayer();
        else if (theFile.isDVD())
          return true;//new DShowDVDPlayer();
        else if (theFile.isLiveBufferedStream() && theFile.isMusic())
          return true;//new DShowRadioPlayer();
        else if (theFile.isMusic())
          return true;//new DShowMusicPlayer();
        else if (theFile.isVideo() && MediaFile.isMPEG2LegacySubtype(theFile.getLegacyMediaSubtype()))
          return true; //new DShowTVPlayer();
        else if (theFile.isBluRay())
          return true; // DShowTVPlayer
        else if (!theFile.isRecording())
          return true;//new DShowMediaPlayer();
        else
        {
          // Check if it's a recording with a live preview we can access
          if (theFile.isLocalFile() && theFile.isRecording() && !Sage.client)
          {
            CaptureDeviceInput cdi = SeekerSelector.getInstance().getInputForCurrRecordingFile(theFile);
            if (cdi != null && cdi.getCaptureDevice().getNativeVideoPreviewConfigHandle() != 0)
            {
              return true;
            }
          }
          return false;
        }
      }
      else
      {
        if (theFile.isBluRay())
          return false;
        if (Sage.MAC_OS_X) {
          if(theFile.isDVD())
            return true;//new MacDVDPlayer();
          else if (theFile.isLiveStream())
            return true;//new MacLivePlayer()
          else if (QTMediaPlayer.canLoadMediaFile(theFile))
            return true;//new QTMediaPlayer
        }
        if (/*sage.media.format.MediaFormat.WMV9.equals(theFile.getPrimaryVideoFormat()) ||*/
            sage.media.format.MediaFormat.WMA9LOSSLESS.equals(theFile.getPrimaryAudioFormat()) ||
            (theFile.getFileFormat() != null && theFile.getFileFormat().isDRMProtected()))
          return false;
        else
          return true; // We've got the MPlayer plugin now!
      }
    }
  }

  public MediaPlayer createMediaPlayer(MediaFile theFile)
  {
    if (Sage.DBG) System.out.println("VideoFrame creating new media player for file:" + theFile);
    String mpPlugin = getPluginClassForFile(theFile);
    if (mpPlugin.length() > 0)
    {
      try
      {
        return (MediaPlayer) Class.forName(mpPlugin, true, Sage.extClassLoader).newInstance();
      }
      catch (Throwable e)
      {
        if (Sage.DBG) System.out.println("ERROR Creating MediaPlayer plugin:" + e);
      }
    }
    /*
		if (sage.media.format.MediaFormat.SWF.equals(theFile.getContainerFormat()))
		{
			return new SWFMediaPlayer();
		}*/
    if (mediaPlayerSetup == STBX25XX_MEDIA_PLAYER_SETUP)
    {
      if(theFile.isDVD())
      {
        if (uiMgr.getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
        {
          String ipdp = ((MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine()).getInputDevsProp();
          if (ipdp != null && ipdp.indexOf("MOUSE") != -1)
            return new MiniPlayer();
        }
        try
        {
          return (MediaPlayer) Class.forName("sage.MiniDVDPlayer").newInstance();
        }
        catch (Throwable t)
        {
          System.out.println("ERROR Could not create MiniDVDPlayer!");
          return null;
        }
      }
      else if (theFile.isLiveStream())
      {
        return new MiniMulticastPlayer();
      }
      else
      {
        return new MiniPlayer();
      }
    }
    else if (Sage.WINDOWS_OS/* && !(uiMgr.getRootPanel().getRenderEngine() instanceof JOGLSageRenderer)*/)
    {
      if (theFile.isLiveStream())
        return new DShowLivePlayer();
      else if (theFile.isDVD())
        return new DShowDVDPlayer();
      else if (theFile.isLiveBufferedStream() && theFile.isMusic())
        return new DShowRadioPlayer();
      else if (theFile.isMusic() && ((!sage.media.format.MediaFormat.AAC.equals(theFile.getPrimaryAudioFormat()) &&
          !sage.media.format.MediaFormat.VORBIS.equals(theFile.getPrimaryAudioFormat()) &&
          !sage.media.format.MediaFormat.FLAC.equals(theFile.getPrimaryAudioFormat()) &&
          !sage.media.format.MediaFormat.ALAC.equals(theFile.getPrimaryAudioFormat()) &&
          (theFile.getFileFormat() == null || !theFile.getFileFormat().isDRMProtected())) ||
          Sage.getBoolean("always_use_dshow_player", true)))
        return new DShowMusicPlayer();
      else if (theFile.isBluRay() || (theFile.isVideo() && MediaFile.isMPEG2LegacySubtype(theFile.getLegacyMediaSubtype())))
      {
        return new DShowTVPlayer();
      }
      else
      {
        // Check if it's a recording with a live preview we can access
        if (theFile.isLocalFile() && theFile.isRecording() && !Sage.client)
        {
          CaptureDeviceInput cdi = SeekerSelector.getInstance().getInputForCurrRecordingFile(theFile);
          if (cdi != null && cdi.getCaptureDevice().getNativeVideoPreviewConfigHandle() != 0)
          {
            return new DShowSharedLiveMediaPlayer(cdi.toString());
          }
        }
        if (Sage.getBoolean("mplayer/use_for_online_content", true) && downer != null)
        {
          return new LinuxMPlayerPlugin();
        }
        // Check if we use MPlayer or the DShowPlayer
        if (!Sage.getBoolean("always_use_dshow_player", true) &&
            (sage.media.format.MediaFormat.AAC.equals(theFile.getPrimaryAudioFormat()) ||
                sage.media.format.MediaFormat.FLAC.equals(theFile.getPrimaryAudioFormat()) ||
                sage.media.format.MediaFormat.ALAC.equals(theFile.getPrimaryAudioFormat()) ||
                sage.media.format.MediaFormat.VORBIS.equals(theFile.getPrimaryAudioFormat()) ||
                sage.media.format.MediaFormat.FLASH_VIDEO.equals(theFile.getPrimaryVideoFormat()) ||
                sage.media.format.MediaFormat.QUICKTIME.equals(theFile.getContainerFormat()) ||
                (sage.media.format.MediaFormat.MATROSKA.equals(theFile.getContainerFormat()) &&
                    Sage.getBoolean("mplayer/use_for_mkv_playback", true)) ||
                    sage.media.format.MediaFormat.FLASH_VIDEO.equals(theFile.getContainerFormat()) ||
                    (sage.media.format.MediaFormat.MPEG4_VIDEO.equals(theFile.getPrimaryVideoFormat()) &&
                        Sage.getBoolean("mplayer/use_for_all_mpeg4_playback", false)) ||
                        sage.media.format.MediaFormat.H264.equals(theFile.getPrimaryVideoFormat()) ||
                        sage.media.format.MediaFormat.OGG.equals(theFile.getContainerFormat())))
        {
          return new LinuxMPlayerPlugin();
        }
        else
        {
          return new DShowMediaPlayer();
        }
      }
    }
    else
    {
      if (Sage.MAC_OS_X) {
        if(theFile.isDVD())
          return new MacDVDPlayer();
        else if (theFile.isLiveStream())
          return new MacLivePlayer();
        else if (QTMediaPlayer.canLoadMediaFile(theFile))	// check if it's supported first...
          return new QTMediaPlayer();
      }
      return new LinuxMPlayerPlugin();
    }
  }

  protected void resizeVideo(final java.awt.Rectangle videoSrcRect, final java.awt.Rectangle videoDestRect)
  {
    MediaPlayer mp = player;
    if (videoDestRect.width == 0 || videoDestRect.height == 0 || mp == null) return;
    // NOTE: Do not run this async on the UI thread. Sometimes it doesn't refresh right if we do
    // it that way. But if we run it serially then we don't have any problems.
    // UPDATE: 7/22/04 I just had the program lockup in this method when it was called from the FinalRender
    // thread. The UI thread is waiting on it now indefinitely. I'm going to make this do the call async
    // only if its not on the UI thread already.
    // UPDATE: 7/27/04 I had the issue where it wasn't refreshing right come up again.
    // For VMR9 this doesn't do any work, it just updates some variables so don't bother with
    // the heavy UI sync in that case
    // UPDATE: 1/17/05 - I put this synhronously again. This fixed the issues with the window not maintaining the right size.
    // We'll consider the one lockup mentioned above an anomally and see how it goes.
    // TEMP FOR DEBUG TESTING
    /*if (Sage.getBoolean("rethread_resize", true) && Sage.WINDOWS_OS)
{
	java.awt.EventQueue.invokeLater(new Runnable()
	{
		public void run()
		{
			MediaPlayer mp = player;
			if (mp != null)
				Sage.setVideoRectangles(mp, videoSrcRect, videoDestRect, hideCursor);
		}
	});

}
		else if (!java.awt.EventQueue.isDispatchThread() && isNonBlendableVideoPlayerLoaded() && Sage.WINDOWS_OS)
		{
			java.awt.EventQueue.invokeLater(new Runnable()
			{
				public void run()
				{
					MediaPlayer mp = player;
					if (mp != null)
						mp.setVideoRectangles(videoSrcRect, videoDestRect, hideCursor);
				}
			});
		}
		else*/
    {
      if (mp != null)
        mp.setVideoRectangles(videoSrcRect, videoDestRect, hideCursor);
    }
  }

  private boolean ejected = false; // assume it starts off with the tray closed
  public boolean isCdromEjected() { return ejected; }
  public void ejectCdrom(String deviceName)
  {
    if (Sage.LINUX_OS)
    {
      if (ejected)
      {
        ejected = !ejected;
        if (Sage.DBG) System.out.println("Retracting CDROM drive...");
        IOUtils.exec(new String[] { "eject", "-t" });
      }
      else
      {
        ejected = !ejected;
        if (Sage.DBG) System.out.println("Ejecting CDROM drive...");
        IOUtils.exec(new String[] { "eject" });
      }
    }
    // not implemented on Windows
  }

  protected static native float setSystemVolume0(float volume);
  protected static native float getSystemVolume0();
  protected static native void setSystemMute0(boolean muteNow);
  protected static native boolean getSystemMute0();

  public long getLastVideoChangeTime() { return lastVideoOpTime; }

  public boolean isMediaPlayerSignaLost()
  {
    if (signalLost) return true;
    CaptureDeviceInput testInput = controlledInput;
    if (testInput != null)
      return testInput.getSignalStrength() < uiMgr.getInt("videoframe/max_strength_for_live_signal_loss", 30);
    else return false;
  }

  public UIManager getUIMgr() { return uiMgr; }

  public int getBestLastStationID()
  {
    Airing a = previousAiring;
    if (a != null && EPG.getInstance().canViewStation(a.stationID))
      return a.stationID;
    return lastStationID;
  }

  public Playlist getNowPlayingList()
  {
    if (nowPlayingPlaylist == null)
    {
      nowPlayingPlaylist = new Playlist(0);
      nowPlayingPlaylist.setName(Sage.rez("Now_Playing"));
    }
    return nowPlayingPlaylist;
  }

  public boolean areMediaFileFormatsSwitchable(MediaFile previousFile, MediaFile file)
  {
    if (previousFile == null || file == null)
    {
      if (Sage.DBG) System.out.println("No fast switching due to NULL file previous=" + previousFile + " file=" + file);
      return false;
    }

    // Narflex - 1/9/17 - I've seen various issues happen when we try to fast switch during playlist playback
    // so I'm just going to disallow it. It gets confused because you're not necessarily live, but the other checks
    // here pass for the files being fast switchable.
    if (!playlistChain.isEmpty()) {
      System.out.println("No fast switching because we are executing a playlist");
      return false;
    }

    // New Rules!!
    // This is now only allowed for TV recordings; and only if the two were recorded back to back on
    // the same channel; and the capture device they were recorded w/ supports fast switching; and they
    // were both recorded with the same capture device
    if (!previousFile.isTV() || !file.isTV())
    {
      if (Sage.DBG) System.out.println("No fast switching due to non-TV file previous=" + previousFile + " file=" + file);
      return false;
    }
    if (previousFile.getEncodedBy() == null || !previousFile.getEncodedBy().equals(file.getEncodedBy()))
    {
      if (Sage.DBG) System.out.println("No fast switching due to encoding change previous=" + previousFile.getEncodedBy() + " file=" + file.getEncodedBy());
      return false;
    }
    // The segment end/start times will match exactly because the Seeker uses the same value for them both
    if (previousFile.getRecordEnd() != file.getRecordTime())
    {
      if (Sage.DBG) System.out.println("No fast switching due to mismatched times previous=" + previousFile.getRecordEnd() + " file=" + file.getRecordTime());
      return false;
    }
    if (previousFile.getContentAiring().getStationID() != file.getContentAiring().getStationID())
    {
      if (Sage.DBG) System.out.println("No fast switching due to mismatched station IDs prevous=" + previousFile.getContentAiring().getStationID() + " file=" +
          file.getContentAiring().getStationID());
      return false;
    }
    CaptureDevice prevDev = previousFile.guessCaptureDeviceFromEncoding();
    CaptureDevice newDev = file.guessCaptureDeviceFromEncoding();
    // Don't check the fast mux switch here since SageTVClient won't know the proper value for it!
    if (prevDev == null || newDev == null || prevDev != newDev/* || !prevDev.supportsFastMuxSwitch()*/)
    {
      if (Sage.DBG) System.out.println("No fast switching due to mismatched capture devices prevDev=" + prevDev + " newDev=" + newDev);
      return false;
    }
    /*	sage.media.format.ContainerFormat prevForm = previousFile.getFileFormat();
		sage.media.format.ContainerFormat newForm = file.getFileFormat();
		// DVDs have a null media format currently
		if (prevForm == null || newForm == null)
			return false;
		// Dissimilar container format can never be switched
		if (!prevForm.getFormatName().equals(newForm.getFormatName()))
			return false;

		// A different number of streams will prevent switching due to demux reconfig
		if (prevForm.getNumberOfStreams() != newForm.getNumberOfStreams())
			return false;

		// Don't allow fast switching of Flash video content; it doesn't need to be seamless
		if (sage.media.format.MediaFormat.FLASH_VIDEO.equals(prevForm.getFormatName()) ||
			sage.media.format.MediaFormat.FLASH_VIDEO.equals(prevForm.getPrimaryVideoFormat()))
			return false;

		// Check for video format compatability
		sage.media.format.VideoFormat[] preVidForms = prevForm.getVideoFormats();
		sage.media.format.VideoFormat[] newVidForms = newForm.getVideoFormats();
		if (newVidForms.length > 1 || preVidForms.length != newVidForms.length)
		{
			// We don't know what to do if we've got a file like this yet!
			return false;
		}
		if (preVidForms.length > 0)
		{
			// Incompatible video formats
			if (!preVidForms[0].getFormatName().equals(newVidForms[0].getFormatName()))
				return false;
			// incompatible video dimensions
			if (preVidForms[0].getWidth() != newVidForms[0].getWidth() || preVidForms[0].getHeight() != newVidForms[0].getHeight())
				return false;
			if (preVidForms[0].isInterlaced() != newVidForms[0].isInterlaced())
				return false;
			if (preVidForms[0].getFpsNum() != newVidForms[0].getFpsNum() || preVidForms[0].getFpsDen() != newVidForms[0].getFpsDen())
				return false;
			String id1 = preVidForms[0].getId();
			String id2 = newVidForms[0].getId();
			if (id1 != id2 && (id1 == null || !id1.equals(id2)))
				return false;
		}

		// Check for audio stream format compatability
		sage.media.format.AudioFormat[] preAudForms = prevForm.getAudioFormats();
		sage.media.format.AudioFormat[] newAudForms = newForm.getAudioFormats();
		if (preAudForms.length != newAudForms.length)
			return false;
		for (int i = 0; i < newAudForms.length; i++)
		{
			if (!preAudForms[i].getFormatName().equals(newAudForms[i].getFormatName()))
				return false;
			String id1 = preAudForms[i].getId();
			String id2 = newAudForms[i].getId();
			if (id1 != id2 && (id1 == null || !id1.equals(id2)))
				return false;
		}
     */
    return true;
  }

  public long getRealWatchStart()
  {
    return realWatchStart;
  }

  public void registerSubtitleComponent(ZLabel uiSub)
  {
    if (uiSub != subtitleComp)
    {
      if (Sage.DBG) System.out.println("VideoFrame got registration of a subtitle UI component: " + uiSub);
      subtitleComp = uiSub;
      if (subtitleComp != null)
      {
        // Clear the subtitle text when we're setting it as the new subtitle rendering component
        subtitleComp.setText("");
      }
    }
  }

  public ZLabel getRegisteredSubtitleComponent()
  {
    return subtitleComp;
  }

  public void registerCCComponent(ZCCLabel uiSub)
  {
    if (uiSub != ccComp)
    {
      if (Sage.DBG) System.out.println("VideoFrame got registration of a cc UI component: " + uiSub);
      ccComp = uiSub;
      if (ccComp != null)
      {
        // Clear the subtitle text when we're setting it as the new subtitle rendering component
        ccComp.clearCC();
      }
    }
  }

  public ZLabel getRegisteredCCComponent()
  {
    return ccComp;
  }

  public void postSubtitleInfo(long startTime, long duration, final byte[] rawText, int flags)
  {
    // Don't let errors in this propogate out to the rest of the system
    try
    {
      // Handle the flush case
      if ((flags & sage.media.sub.SubtitleHandler.FLUSH_SUBTITLE_QUEUE) == sage.media.sub.SubtitleHandler.FLUSH_SUBTITLE_QUEUE)
      {
        // When we flush; also clear the subtitle display since it might now be disabled; and because its content would have changed as well
        if (subtitleComp != null || ccComp != null)
        {
          uiMgr.getRouter().invokeLater(new Runnable()
          {
            public void run()
            {
              if (subtitleComp != null)
                subtitleComp.setText("");
              if (ccComp != null)
                ccComp.clearCC();
            }
          });
        }
      }
      // We treat CC data differently then sub data; so separate those here
      if ((flags & sage.media.sub.SubtitleHandler.CC_SUBTITLE_MASK) == sage.media.sub.SubtitleHandler.CC_SUBTITLE_MASK)
      {
        // Ignore empty subs if we don't have a handler setup yet
        if (ccHandler == null && rawText.length == 0)
          return;
        //if (Sage.DBG) System.out.println("VideoFrame Received cc update time=" + startTime + " dur=" + duration + " flags=0x" + Integer.toString(flags, 16) + " len=" + rawText.length);
        if (ccHandler == null && currFile != null)
        {
          ccHandler = (sage.media.sub.CCSubtitleHandler) sage.media.sub.SubtitleHandler.createSubtitleHandlerDirect(this, rawText, flags, currFile.getFileFormat(), null);
          // CC is based on current media player state for CC
          int ccState = uiMgr.getInt(prefs + LAST_CC_STATE, MediaPlayer.CC_DISABLED);
          ccHandler.setEnabled(ccState != MediaPlayer.CC_DISABLED);
          if (ccState != MediaPlayer.CC_DISABLED)
          {
            ccHandler.setCurrLanguage(BasicVideoFrame.getCCStateName(ccState));
          }
        }
        if (ccHandler != null && ccHandler.postSubtitleInfo(startTime, duration, rawText, flags) && ccHandler.isEnabled())
          kick();
      }
      else
      {
        // Ignore empty subs if we don't have a handler setup yet
        if (subHandler == null && rawText.length == 0)
          return;
        if (Sage.DBG) System.out.println("VideoFrame Received subtitle update time=" + startTime + " dur=" + duration + " flags=0x" + Integer.toString(flags, 16) + " len=" + rawText.length);
        if (subHandler != null && subHandler.hasExternalSubtitles()) return; // ignore these messages if we're doing our own sub processing w/ external files
        // See what the format type is for these subs
        String[] subOpts = getDVDAvailableSubpictures();
        String currSubOpt = getDVDSubpicture();
        String currSubFormat = null;
        if (currFile != null && subOpts != null && currSubOpt != null && (lastSubType == null || !lastSubType.equals(currSubOpt)))
        {
          for (int i = 0; i < subOpts.length; i++)
            if (subOpts[i].equals(currSubOpt))
            {
              currSubFormat = currFile.getFileFormat().getSubpictureFormats()[i].getFormatName();
              break;
            }
        }
        if (currSubFormat != null && embeddedSubStreamType != null && !currSubFormat.equals(embeddedSubStreamType) && subHandler != null)
        {
          if (Sage.DBG) System.out.println("Detected change in embedded subtitle stream type; load a new subtitle handler");
          subHandler.cleanup();
          subHandler = null;
          lastSubType = null;
          embeddedSubStreamType = null;
          if (rawText.length == 0)
            return;
        }
        lastSubType = currSubOpt;
        if (subHandler == null && currFile != null)
        {
          subHandler = sage.media.sub.SubtitleHandler.createSubtitleHandlerDirect(this, rawText, flags, currFile.getFileFormat(), currSubFormat);
          // When we get subtitle messages; we always display the content. The messages will stop if we should no longer display it.
          subHandler.setEnabled(true);
          embeddedSubStreamType = currSubFormat;
        }
        if (subHandler != null && subHandler.postSubtitleInfo(startTime, duration, rawText, flags))
          kick();
      }
    }
    catch (Throwable t)
    {
      if (Sage.DBG) System.out.println("ERROR in VideoFrame subtitle handling of: " + t);
      if (Sage.DBG) t.printStackTrace();
    }
  }

  public long getSubtitleDelay()
  {
    return subtitleDelay;
  }

  public void setSubtitleDelay(long newDelay)
  {
    if (newDelay != subtitleDelay)
      uiMgr.putLong("subtitle/delay", newDelay);
    subtitleDelay = newDelay;
    if (subHandler != null && subHandler.hasExternalSubtitles())
    {
      subHandler.setDelay(subtitleDelay);
      subHandler.forceSubRefresh();
      kick();
    }
  }

  public boolean canAdjustSubtitleDelay()
  {
    return subHandler != null && subHandler.hasExternalSubtitles();
  }

  public void applyRelativeSubAdjustment(int adjustAmount)
  {
    if (subHandler != null && subHandler.hasExternalSubtitles())
    {
      setSubtitleDelay(subtitleDelay + subHandler.getOffsetToRelativeSub(adjustAmount, getBaseMediaTimeMillis(true)));
    }
  }

  public String getBluRayTitleDesc(int titleNum)
  {
    if (player instanceof MiniPlayer)
      return ((MiniPlayer) player).getBluRayTitleDesc(titleNum);
    else if (player instanceof DShowTVPlayer)
      return ((DShowTVPlayer) player).getBluRayTitleDesc(titleNum);
    else
      return "";
  }

  public boolean isMPlayerLoaded()
  {
    return player instanceof LinuxMPlayerPlugin;
  }

  private boolean selectDefaultAudioLanguage()
  {
    return performLanguageMatch(uiMgr.get("default_audio_language", "English"), getDVDAvailableLanguages(), DVD_CONTROL_AUDIO_CHANGE, getDVDLanguage());
  }
  private boolean performLanguageMatch(String defaultLang, String[] availLangs, int playerControlCode, String currLang)
  {
    if (availLangs == null || availLangs.length <= 1)
      return false; // nothing to select

    MediaLangInfo mli = (MediaLangInfo) mediaLangMap.get(defaultLang);
    if (mli == null)
      return false; // no language details
    if (isDVD())
    {
      // First check if we already have the right language selected (there may be multiple tracks with the same language)
      String altLang = new java.util.Locale(mli.twoChar).getDisplayLanguage();
      if (currLang != null && (currLang.toLowerCase().indexOf(defaultLang) != -1 || currLang.toLowerCase().indexOf(altLang) != -1))
      {
        if (Sage.DBG) System.out.println("Default " + (playerControlCode == DVD_CONTROL_AUDIO_CHANGE ? "audio" : "subpic") + " language is already selected");
        return true;
      }

      // Check for the English variant first, then the localized next
      int matchIdx = getMatchingLangIndex(availLangs, defaultLang);
      if (matchIdx >= 0)
      {
        if (Sage.DBG) System.out.println("Selecting default DVD " + (playerControlCode == DVD_CONTROL_AUDIO_CHANGE ? "audio" : "subpic") + " language of " + defaultLang + " at index " + matchIdx);
        playbackControl(playerControlCode, matchIdx, -1);
        return true;
      }
      // Now check the localized language code based on the 2-letter variant
      if (!defaultLang.equals(altLang))
      {
        matchIdx = getMatchingLangIndex(availLangs, altLang);
        if (matchIdx >= 0)
        {
          if (Sage.DBG) System.out.println("Selecting default DVD " + (playerControlCode == DVD_CONTROL_AUDIO_CHANGE ? "audio" : "subpic") + " language of " + defaultLang + " at index " + matchIdx);
          playbackControl(playerControlCode, matchIdx, -1);
          return true;
        }
      }
      return false; // didn't find a match
    }
    else
    {
      // First check if we already have the right language selected (there may be multiple tracks with the same language)
      // Try each three letter code option to find a match
      for (int i = 0; i < mli.threeChar.length; i++)
      {
        if (currLang != null && currLang.toLowerCase().indexOf(mli.threeChar[i]) != -1)
        {
          if (Sage.DBG) System.out.println("Default " + (playerControlCode == DVD_CONTROL_AUDIO_CHANGE ? "audio" : "subpic") + " language is already selected");
          return true;
        }
      }
      // Try each three letter code option to find a match
      for (int i = 0; i < mli.threeChar.length; i++)
      {
        int matchIdx = getMatchingLangIndex(availLangs, mli.threeChar[i]);
        if (matchIdx >= 0)
        {
          if (Sage.DBG) System.out.println("Selecting default " + (playerControlCode == DVD_CONTROL_AUDIO_CHANGE ? "audio" : "subpic") + " language of " + defaultLang + " at index " + matchIdx);
          playbackControl(playerControlCode, matchIdx, -1);
          return true;
        }
      }
      return false; // no match
    }
  }

  private int getMatchingLangIndex(String[] langOptions, String targetLang)
  {
    targetLang = targetLang.toLowerCase();
    for (int i = 0; i < langOptions.length; i++)
    {
      if (langOptions[i].toLowerCase().indexOf(targetLang) != -1)
        return i;
    }
    return -1;
  }

  private boolean selectDefaultSubpicLanguage()
  {
    String defaultLang = uiMgr.get("default_subpic_language", "");
    if (defaultLang == null || defaultLang.length() == 0)
      return true; // preferred no subtitles

    return performLanguageMatch(defaultLang, getDVDAvailableSubpictures(), DVD_CONTROL_SUBTITLE_CHANGE, getDVDSubpicture());
  }

  private void notifyPlaybackFinished()
  {
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYBACK_FINISHED,
        new Object[] {
        sage.plugin.PluginEventManager.VAR_MEDIAFILE, currFile,
        sage.plugin.PluginEventManager.VAR_UICONTEXT, uiMgr.getLocalUIClientName(),
        sage.plugin.PluginEventManager.VAR_DURATION, new Long(getDurationMillis()),
        sage.plugin.PluginEventManager.VAR_MEDIATIME, new Long(getMediaTimeMillis()),
        sage.plugin.PluginEventManager.VAR_CHAPTERNUM, new Integer(getDVDChapter()),
        sage.plugin.PluginEventManager.VAR_TITLENUM, new Integer(getDVDTitle())
    });
  }

  private void notifyPlaybackResumed()
  {
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYBACK_RESUMED,
        new Object[] {
        sage.plugin.PluginEventManager.VAR_MEDIAFILE, currFile,
        sage.plugin.PluginEventManager.VAR_UICONTEXT, uiMgr.getLocalUIClientName(),
        sage.plugin.PluginEventManager.VAR_DURATION, new Long(getDurationMillis()),
        sage.plugin.PluginEventManager.VAR_MEDIATIME, new Long(getMediaTimeMillis()),
        sage.plugin.PluginEventManager.VAR_CHAPTERNUM, new Integer(getDVDChapter()),
        sage.plugin.PluginEventManager.VAR_TITLENUM, new Integer(getDVDTitle())
    });
  }

  private void notifyPlaybackSeek()
  {
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYBACK_SEEK,
        new Object[] {
        sage.plugin.PluginEventManager.VAR_MEDIAFILE, currFile,
        sage.plugin.PluginEventManager.VAR_UICONTEXT, uiMgr.getLocalUIClientName(),
        sage.plugin.PluginEventManager.VAR_DURATION, new Long(getDurationMillis()),
        sage.plugin.PluginEventManager.VAR_MEDIATIME, new Long(getMediaTimeMillis()),
        sage.plugin.PluginEventManager.VAR_CHAPTERNUM, new Integer(getDVDChapter()),
        sage.plugin.PluginEventManager.VAR_TITLENUM, new Integer(getDVDTitle())
    });
  }

  private void notifyPlaybackPaused()
  {
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYBACK_PAUSED,
        new Object[] {
        sage.plugin.PluginEventManager.VAR_MEDIAFILE, currFile,
        sage.plugin.PluginEventManager.VAR_UICONTEXT, uiMgr.getLocalUIClientName(),
        sage.plugin.PluginEventManager.VAR_DURATION, new Long(getDurationMillis()),
        sage.plugin.PluginEventManager.VAR_MEDIATIME, new Long(getMediaTimeMillis()),
        sage.plugin.PluginEventManager.VAR_CHAPTERNUM, new Integer(getDVDChapter()),
        sage.plugin.PluginEventManager.VAR_TITLENUM, new Integer(getDVDTitle())
    });
  }

  private void notifyPlaybackRateChange(float newRate)
  {
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYBACK_RATECHANGE,
        new Object[] {
        sage.plugin.PluginEventManager.VAR_MEDIAFILE, currFile,
        sage.plugin.PluginEventManager.VAR_UICONTEXT, uiMgr.getLocalUIClientName(),
        sage.plugin.PluginEventManager.VAR_DURATION, new Long(getDurationMillis()),
        sage.plugin.PluginEventManager.VAR_MEDIATIME, new Long(getMediaTimeMillis()),
        sage.plugin.PluginEventManager.VAR_PLAYBACKRATE, new Float(newRate),
        sage.plugin.PluginEventManager.VAR_CHAPTERNUM, new Integer(getDVDChapter()),
        sage.plugin.PluginEventManager.VAR_TITLENUM, new Integer(getDVDTitle())
    });
  }

  private Hunter seek;
  private MediaFile currFile;
  private int mediaPlayerSetup;
  private boolean currFileLoaded;
  private Airing previousAiring;
  private long realWatchStart;
  private long mightWait = 0;

  private int pSwitchGraph;
  private long segmentMinBackup;
  private volatile boolean loggedWatch;
  private volatile boolean playbackDone;

  private boolean signalLost;

  // If this is set to true, then the software has been cracked!!!!
  static boolean a;

  private int lastStationID;

  private java.awt.event.MouseListener dvdMouseListener;
  private java.awt.event.MouseMotionListener dvdMouseMotionListener;

  private boolean alreadySkippedDVDMenus;
  private boolean alreadySelectedDefaultDVDAudio;
  private boolean alreadySelectedDefaultDVDSub;

  // liveControl is when the user has selected a file that was live at
  // iniitial viewing start. In this mode, autopilot is off. Files are also
  // seamed together automatically to give a fluid live timshift. When those
  // seamings occur, liveControl maintains its true state.
  private boolean liveControl;
  private boolean networkEncoderPlayback;
  private long extraEncodingDelay;

  private boolean disableTimeScaling;

  // This is when the user takes control of the tuner hardware itself, for
  // doing live & buffered live streams from the hardware directly. This
  // means the Scheduler isn't allowed to use this tuner in its set of Encoders.
  private CaptureDeviceInput controlledInput;

  private int segment;

  private java.util.Vector watchQueue;

  private boolean autoplaying;

  private MediaFile watchThisNow;

  private boolean iWantToBeLive;

  private java.util.Vector playlistChain;
  private java.util.Vector playlistChainIndices;
  private java.util.Set alreadyPlayedRandomSongs;

  private boolean processingWatchRequest;
  private boolean aboutToDoWatchRequest;

  private MediaPlayer player;
  protected boolean alive;

  protected long realDurMillis;

  protected boolean globalMute = false;

  protected MediaFile lookForRecordTimeAdjust;
  protected long recordTimeAdjust;

  private boolean wasPausedBeforeReload;
  private long targetTimeForReload;
  private boolean preparedForReload;

  // Used to detect whether or not the video rendering system may be pushing back a new video frame
  // soon so the final renderer will wait a bit so there's not sync issues in the 2 rendering pipelines
  protected long lastVideoOpTime;

  protected Playlist nowPlayingPlaylist;

  // This indicates that a watch request for what we're currently watching should be allowed
  // to pass through (like with local file playback). This is needed for repeat play of a single
  // item to work correctly.
  protected boolean restartOnRedundantWatch;

  protected Watched dvdResumeTarget;
  protected boolean dvdResumeTitleSetDone;
  protected int blurayTargetTitle;

  private VFJob syncCloseToExecuteNow;

  protected FileDownloader downer;

  private long[] chapterPoints;

  private int oldcurrDVDTitle;
  private int oldcurrDVDChapter;
  private int oldcurrDVDDomain;
  private boolean olddvdButtonsVisible;
  private int oldcurrDVDAngle;
  private int oldcurrDVDTotalAngles;
  private String oldcurrDVDLanguage;
  private String[] oldcurrDVDAvailableLanguages;
  private String oldcurrDVDSubpicture;
  private String[] oldcurrDVDAvailableSubpictures;

  private VFJob asyncJobInWait;

  private boolean currPlaylistPassesPC;

  private ZLabel subtitleComp;
  private ZCCLabel ccComp;
  private sage.media.sub.SubtitleHandler subHandler;
  private sage.media.sub.CCSubtitleHandler ccHandler;
  private byte[] subpicBuff;
  private long subtitleDelay;
  private String embeddedSubStreamType;
  private String lastSubType; // for optimization

  private Channel[] sortedViewableChans; // for optimization

  private long channelChangeTime;
  private boolean kicked;

  private final boolean ccSubDebug = Sage.getBoolean("debug_cc_sub", false);

  private static class VFJob
  {
    public VFJob(int id)
    {
      this.id = id;
    }
    public VFJob(int id, MediaFile file, Playlist thePlaya)
    {
      this.id = id;
      this.file = file;
      this.playa = thePlaya;
    }
    public VFJob(int id, long time)
    {
      this.id = id;
      this.time = time;
    }
    public VFJob(int id, float rate)
    {
      this.id = id;
      this.rate = rate;
    }
    public VFJob(int id, String fn)
    {
      this.id = id;
      this.inactiveFilename = fn;
    }
    public VFJob(int id, MediaFile file, String fn)
    {
      this.id = id;
      this.file = file;
      this.inactiveFilename = fn;
    }
    public VFJob(int id, int dvdCode, long dvdPar1, long dvdPar2)
    {
      this.id = id;
      this.dvdControlCode = dvdCode;
      this.dvdParam1 = dvdPar1;
      this.dvdParam2 = dvdPar2;
    }
    public VFJob(Playlist newPlaya, int playlistIdx, boolean passesPC)
    {
      this.id = START_PLAYLIST;
      this.playa = newPlaya;
      this.playaIdx = playlistIdx;
      this.passesPC = passesPC;
    }

    public String toString()
    {
      return "VFJob[" + getNameForVFJobID(id) + " r=" + rate + " t=" + time +
          " file=" + file + " ifn=" + inactiveFilename + ']';
    }

    int id;
    float rate;
    long time;
    MediaFile file;
    String inactiveFilename;

    int dvdControlCode;
    long dvdParam1;
    long dvdParam2;

    Playlist playa;
    int playaIdx;
    boolean useSyncClose;
    boolean passesPC;
  }

  private static MediaFile[] cachedSortedMusicFiles;
  private static long cachedSortedMusicFilesTimestamp;
  private static final boolean USE_COLLATOR_SORTING = Sage.getBoolean("use_collator_sorting", true);
  public static final java.util.Comparator musicFileComparator =
      new java.util.Comparator()
  {
    public int compare(Object o1, Object o2)
    {
      Airing a1;
      if (o1 instanceof MediaFile)
        a1 = ((MediaFile) o1).getContentAiring();
      else
        a1 = (Airing) o1;
      Airing a2;
      if (o2 instanceof MediaFile)
        a2 = ((MediaFile) o2).getContentAiring();
      else
        a2 = (Airing) o2;
      if (a1 == a2) return 0;
      if (a1 == null) return 1;
      if (a2 == null) return -1;
      Show s1 = a1.getShow();
      Show s2 = a2.getShow();
      if (s1 == s2) return 0;
      if (s1 == null) return 1;
      if (s2 == null) return -1;
      int x;
      if (USE_COLLATOR_SORTING)
      {
        if (Sage.userLocale != lastLocale)
        {
          collie = java.text.Collator.getInstance(Sage.userLocale);
          collie.setStrength(java.text.Collator.PRIMARY);
          lastLocale = Sage.userLocale;
        }
        // If they're the same album, then we skip the artist
        x = collie.compare(s1.getTitle(), s2.getTitle());
      }
      else
      {
        x = s1.getTitle().compareToIgnoreCase(s2.getTitle());
      }
      /*			if (x != 0)
			{
				// by artist
				x = s1.getPersonInRole(Show.ARTIST_ROLE).compareTo(s2.getPersonInRole(Show.ARTIST_ROLE));
				if (x != 0) return x;
			}
			// then by album
			x = s1.getTitle().compareTo(s2.getTitle());*/
      if (x != 0) return x;
      // then by track
      x = a1.partsB - a2.partsB;
      if (x != 0) return x;
      if (USE_COLLATOR_SORTING)
        x = collie.compare(s1.getEpisodeName(), s2.getEpisodeName());
      else
        x = s1.getEpisodeName().compareToIgnoreCase(s2.getEpisodeName());
      if (x != 0) return x;
      return a1.id - a2.id; // for consistency
    }
    private java.text.Collator collie;
    private java.util.Locale lastLocale;
    {
      if (USE_COLLATOR_SORTING)
      {
        collie = java.text.Collator.getInstance(Sage.userLocale);
        collie.setStrength(java.text.Collator.PRIMARY);
        lastLocale = Sage.userLocale;
      }
    }
  };

}
