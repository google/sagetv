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

import sage.NetworkClient;

import sage.UIClient;
import sage.UIManager;

import sage.*;

/**
 * Calls for playing back media in SageTV and for controlling that playback
 */
public class MediaPlayerAPI {
  private MediaPlayerAPI() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("MediaPlayer", "IsMediaPlayerFullyLoaded")
    {
      /**
       * Returns true if the MediaPlayer is fully loaded. This means it has the meta information for the
       * file loaded as well as the native media player.
       * @return true if the MediaPlayer is fully loaded, false otherwise
       *
       * @declaration public boolean IsMediaPlayerFullyLoaded();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().isMediaPlayerLoaded());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "IsMediaPlayerLoading")
    {
      /**
       * Returns true if the MediaPlayer is loading. This is true from the point that a Watch() API call
       * is made until the point that the native media player is loaded or there is a failure loading the file.
       * @return true if the MediaPlayer is loading, false otherwise
       *
       * @declaration public boolean IsMediaPlayerLoading();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().isLoadingOrLoadedFile() && !stack.getUIMgrSafe().getVideoFrame().isMediaPlayerLoaded());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "PlayFaster")
    {
      /**
       * Sets the playback rate of the MediaPlayer to be twice the current playback rate. Not supported
       * on all platforms or with all media formats.
       *
       * @declaration public void PlayFaster();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().faster(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "SkipForward")
    {
      /**
       * Performs a time seek forward in the MediaPlayer. The amount of time skipped will be equivalent
       * to the value of the property videoframe/ff_time in milliseconds. (the default is 10 seconds)
       *
       * @declaration public void SkipForward();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().ff(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "SkipForward2")
    {
      /**
       * Performs a time seek forward in the MediaPlayer. The amount of time skipped will be equivalent
       * to the value of the property videoframe/ff_time2 in milliseconds. (the default is 2 1/2 minutes)
       *
       * @declaration public void SkipForward2();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().ff2(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "Seek", 1, new String[]{"Time"})
    {
      /**
       * Performs a time seek in the MediaPlayer to the specified time. This time is relative to the
       * start time of the metadata for the MediaFile unless a DVD is being played back. In the case of a DVD
       * the time is absolute.
       * @param Time the time to seek the MediaPlayer to in milliseconds
       *
       * @declaration public void Seek(long Time);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().timeJump(getLong(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "Pause")
    {
      /**
       * Pauses playback in the MediaPlayer. If the MediaPlayer is currently paused this will perform a frame step.
       *
       * @declaration public void Pause();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().pause(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "Play")
    {
      /**
       * Resumes playback in the MediaPlayer. If the MediaPlayer is playing at a speed other than x1, the playback speed will be
       * reset to x1.
       *
       * @declaration public void Play();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().play(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "PlaySlower")
    {
      /**
       * Sets the playback rate of the MediaPlayer to be half the current playback rate. Not supported
       * on all platforms or with all media formats.
       *
       * @declaration public void PlaySlower();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().slower(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "PlayPause")
    {
      /**
       * Pauses playback of the MediaPlayer if it is currently playing or resumes playback of the MediaPlayer
       * if it is currently paused.
       *
       * @declaration public void PlayPause();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().playPause(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "SkipBackwards")
    {
      /**
       * Performs a time seek backwards in the MediaPlayer. The amount of time skipped will be equivalent
       * to the value of the property videoframe/rew_time in milliseconds. (the default is 10 seconds)
       *
       * @declaration public void SkipBackwards();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().rew(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "SkipBackwards2")
    {
      /**
       * Performs a time seek backwards in the MediaPlayer. The amount of time skipped will be equivalent
       * to the value of the property videoframe/rew_time2 in milliseconds. (the default is 2 1/2 minutes)
       *
       * @declaration public void SkipBackwards2();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().rew2(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetPlaybackRate")
    {
      /**
       * Returns the current playback rate as a floating point number. 1.0 is normal speed forward playback.
       * Negative numbers indicate reverse playback.
       * @return the current playback rate of the MediaPlayer
       *
       * @declaration public float GetPlaybackRate();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Float(stack.getUIMgrSafe().getVideoFrame().getRate());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "SetPlaybackRate", 1, new String[]{"PlaybackRate"})
    {
      /**
       * Sets the playback rate of the MediaPlayer to the specified value. 1.0 is normal speed forward playback.
       * Negative numbers indicate reverse playback. Not all values are supported on all platforms or for all formats.
       * @param PlaybackRate the playback rate to set the MediaPlayer to
       *
       * @declaration public void SetPlaybackRate(float PlaybackRate);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setRate(getFloat(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "VolumeUp")
    {
      /**
       * Increases the volume in the MediaPlayer. This may also effect the 'system' volume depending upon the
       * configuration of SageTV.
       *
       * @declaration public void VolumeUp();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().volumeUp(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "VolumeDown")
    {
      /**
       * Decreases the volume in the MediaPlayer. This may also effect the 'system' volume depending upon the
       * configuration of SageTV.
       *
       * @declaration public void VolumeDown();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().volumeDown(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetVolume")
    {
      /**
       * Returns the current volume level of the MediaPlayer. If no MediaPlayer is loaded this will return
       * the system volume.
       * @return the current volume level of the MediaPlayer; if no MediaPlayer is loaded this will return
       *         the system volume. The value will be between 0.0 and 1.0
       *
       * @declaration public float GetVolume();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Float(stack.getUIMgrSafe().getVideoFrame().getVolume());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "ChannelUp")
    {
      /**
       * Performs a logical channel up in the MediaPlayer. This only has effect if the content that is
       * currently being viewed has the concept of channels, tracks, chapters, etc.
       *
       * @declaration public void ChannelUp();
       */
      public Object runSafely(final Catbert.FastStack stack) throws Exception{
        final Object taskID = Catbert.getNewTaskID();
        final UIManager uiMgr = stack.getUIMgrSafe();
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            Catbert.asyncTaskComplete(taskID, Catbert.getWatchFailureObject(uiMgr.getVideoFrame().surfUp()));
          }
        }, "AsyncChannelUp", Thread.MAX_PRIORITY); // Make it high priority so channel changes execute faster
        return taskID;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "ChannelDown")
    {
      /**
       * Performs a logical channel down in the MediaPlayer. This only has effect if the content that is
       * currently being viewed has the concept of channels, tracks, chapters, etc.
       *
       * @declaration public void ChannelDown();
       */
      public Object runSafely(final Catbert.FastStack stack) throws Exception{
        final Object taskID = Catbert.getNewTaskID();
        final UIManager uiMgr = stack.getUIMgrSafe();
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            Catbert.asyncTaskComplete(taskID, Catbert.getWatchFailureObject(uiMgr.getVideoFrame().surfDown()));
          }
        }, "AsyncChannelDown", Thread.MAX_PRIORITY); // Make it high priority so channel changes execute faster
        return taskID;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "ChannelSet", 1, new String[]{"ChannelNumber"})
    {
      /**
       * Performs a logical channel set in the MediaPlayer. This only has effect if the content that is
       * currently being viewed has the concept of channels, tracks, chapters, etc.
       * @param ChannelNumber the new channel/track/chapter to playback
       *
       * @declaration public void ChannelSet(String ChannelNumber);
       */
      public Object runSafely(final Catbert.FastStack stack) throws Exception{
        final Object taskID = Catbert.getNewTaskID();
        final String chan = getString(stack);
        final UIManager uiMgr = stack.getUIMgrSafe();
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            Catbert.asyncTaskComplete(taskID, Catbert.getWatchFailureObject(uiMgr.getVideoFrame().surfToChan(chan)));
          }
        }, "AsyncChannelSet", Thread.MAX_PRIORITY); // Make it high priority so channel changes execute faster
        return taskID;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "VolumeAdjust", 1, new String[] { "Amount" })
    {
      /**
       * Adjusts the volume in the MediaPlayer be the specified amount. The overall volume for the
       * player is between 0.0 and 1.0. This may also effect the 'system' volume depending upon the
       * configuration of SageTV.
       * @param Amount the amount to adjust the volume by
       *
       * @declaration public void VolumeAdjust(float Amount);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setVolume(stack.getUIMgrSafe().getVideoFrame().getVolume() + getFloat(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "SetVolume", 1, new String[] { "Amount" })
    {
      /**
       * Explicitly sets the volume in the MediaPlayer to be the specified amount. This should be between 0.0 and 1.0
       * This may also effect the 'system' volume depending upon the configuration of SageTV.
       * @param Amount the level to set the volume to
       *
       * @declaration public void SetVolume(float Amount);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setVolume(getFloat(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "Watch", 1, new String[] { "Content" })
    {
      /**
       * Instructs SageTV to playback the specified media content. The argument can be either an
       * Airing, a MediaFile or a file path.  For Airings, it can correspond to a MediaFile (which has the
       * same effect as just calling this with the MediaFile itself) or it can correspond to a live
       * television Airing. For live TV airings, the appropriate work will be done to tune, record and start
       * playback of the requested content.  For MediaFiles or file paths, this will simply playback the specified content.
       * @param Content the Airing, MediaFile or file path to being playback of
       * @return true if the request was successful, a localized error message otherwise
       *
       * @declaration public Object Watch(Object Content);
       */
      public Object runSafely(final Catbert.FastStack stack) throws Exception{
        Object stacker = stack.pop();
        final Object o = (stacker instanceof sage.vfs.MediaNode) ? ((sage.vfs.MediaNode)stacker).getDataObject() : stacker;
        final UIClient uiClient = (Sage.client ? stack.getUIMgrSafe() : stack.getUIMgr());
        final UIManager uiMgr = stack.getUIMgrSafe();
        final Object taskID = Catbert.getNewTaskID();
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            int watchCode = 0;
            if (o instanceof Airing)
            {
              Airing a = (Airing) o;
              watchCode = uiMgr.getVideoFrame().watch(a);
            }
            else if (o instanceof MediaFile) {
              MediaFile mf = (MediaFile)o;
              watchCode = uiMgr.getVideoFrame().watch(mf);
            }
            else if (o != null)
            {
              // Filename
              String fname = o.toString();
              // Since this can take time we need to set another flag in the VideoFrame to indicate we are just about to start a Watch command
              try
              {
                uiMgr.getVideoFrame().setAboutToCallWatch();
                MediaFile mf = Wizard.getInstance().getPlayableMediaFile(fname);
                if (mf != null)
                    watchCode = uiMgr.getVideoFrame().watch(mf);
                else
                  watchCode = VideoFrame.WATCH_FAILED_FILES_NOT_ON_DISK;
              }
              finally
              {
                // This will get automatically cleared when watch is called, but this also covers the failure case then too.
                uiMgr.getVideoFrame().clearAboutToCallWatch();
              }
            }
            else
              watchCode = VideoFrame.WATCH_FAILED_NULL_AIRING;
            Object rv = Catbert.getWatchFailureObject(watchCode);
            Catbert.asyncTaskComplete(taskID, rv);
          }
        }, "AsyncWatch");
        return taskID;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "WatchLive", -1, new String[] { "CaptureDeviceInput", "PauseBufferSize", "PhysicalChannel" })
    {
      /**
       * Instructs SageTV to begin playback of content streamed from the specified CaptureDeviceInput. The content
       * may or may not be buffered to a file first depending upon the PauseBufferSize parameter as well as the
       * capabilities of the capture hardware and the network configuration.  NOTE: This is NOT the same as jumping
       * to live when playing back buffered TV content; to do that simply call Seek(Time())
       * @param CaptureDeviceInput the capture input to playback content directly from
       * @param PauseBufferSize the size in bytes of the buffer SageTV should use to buffer the content for playback, this will
       *         also allow pausing of this stream upto the size of the PauseBuffer; use 0 to request no buffering
       *         (although SageTV may still decide to use buffering if it deems it necessary)
       * @return true if the request was successful, a localized error message otherwise
       *
       * @declaration public Object WatchLive(String CaptureDeviceInput, long PauseBufferSize);
       */

      /**
       * Instructs SageTV to begin playback of content streamed from the specified CaptureDeviceInput. The content
       * may or may not be buffered to a file first depending upon the PauseBufferSize parameter as well as the
       * capabilities of the capture hardware and the network configuration.  The channel to view is also
       * specified in this form of the call. NOTE: This is NOT the same as jumping
       * to live when playing back buffered TV content; to do that simply call Seek(Time())
       * @param CaptureDeviceInput the capture input to playback content directly from
       * @param PauseBufferSize the size in bytes of the buffer SageTV should use to buffer the content for playback, this will
       *         also allow pausing of this stream upto the size of the PauseBuffer; use 0 to request no buffering
       *         (although SageTV may still decide to use buffering if it deems it necessary)
       * @param PhysicalChannel the physical channel number that should be tuned to before starting viewing
       * @return true if the request was successful, a localized error message otherwise
       * @since 6.6
       *
       * @declaration public Object WatchLive(String CaptureDeviceInput, long PauseBufferSize, String PhysicalChannel);
       */
      public Object runSafely(final Catbert.FastStack stack) throws Exception{
        String physChan = "";
        if (curNumberOfParameters == 3)
          physChan = getString(stack);
        long pauseBuffSize = getLong(stack);
        final CaptureDeviceInput thisConn = getCapDevInput(stack);
        if (thisConn == null) return Catbert.getWatchFailureObject(VideoFrame.WATCH_FAILED_LIVE_STREAM_UKNOWN_INPUT);
        boolean trueLive = (pauseBuffSize == 0) && thisConn.getCaptureDevice().
            isCaptureFeatureSupported(MMC.LIVE_PREVIEW_MASK) && (!Sage.client || Sage.MAC_OS_X) &&
            thisConn.getType() != CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX;
        if (!trueLive && !thisConn.getCaptureDevice().canEncode())
          return Catbert.getWatchFailureObject(VideoFrame.WATCH_FAILED_NO_ENCODERS_HAVE_STATION);
        final MediaFile liveFile = trueLive ? MediaFile.getLiveMediaFileForInput(thisConn) :
          MediaFile.getLiveBufferedMediaFileForInput(thisConn);
        if (pauseBuffSize > 0 || !trueLive)
        {
          if (pauseBuffSize <= 0)
          {
            if (thisConn.getType() == CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX ||
                thisConn.getCaptureDevice().isCaptureFeatureSupported(CaptureDevice.HDPVR_ENCODER_MASK))
              pauseBuffSize = Sage.getLong("default_pause_buffer_size_dtv", 5*16*1024*1024);
            else
              pauseBuffSize = Sage.getLong("default_pause_buffer_size", 16*1024*1024);
          }
          // NOTE: This is not really correct since this can be called from the client and the server has no idea
          // that this has actually occurred.
          liveFile.setStreamBufferSize(pauseBuffSize);
        }
        final Object taskID = Catbert.getNewTaskID();
        final UIManager uiMgr = stack.getUIMgrSafe();
        final String oldPhysChan = (physChan != null && physChan.length() > 0) ? thisConn.getChannel() : null;
        final String newPhysChan = physChan;
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            if (oldPhysChan != null)
              thisConn.setLastChannel(newPhysChan);
            Object rv = Catbert.getWatchFailureObject(uiMgr.getVideoFrame().watch(liveFile));
            if (rv != Boolean.TRUE && oldPhysChan != null)
              thisConn.setLastChannel(oldPhysChan);
            Catbert.asyncTaskComplete(taskID, rv);
          }
        }, "AsyncWatch");
        return taskID;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "LockTuner", new String[] { "CaptureDeviceInput" })
    {
      /**
       * Instructs SageTV to take control of the specified CaptureDeviceInput. The device may then be used
       * for channel scanning. This will cause any prompts to occur that are a result of taking control of the device.
       * When done using it; CloseAndWaitUntilClosed() should be called.
       * @param CaptureDeviceInput the capture input to control
       * @return true if the request was successful, a localized error message otherwise
       * @since 6.6
       *
       * @declaration public Object LockTuner(String CaptureDeviceInput);
       */
      public Object runSafely(final Catbert.FastStack stack) throws Exception{
        CaptureDeviceInput thisConn = getCapDevInput(stack);
        if (thisConn == null) return Catbert.getWatchFailureObject(VideoFrame.WATCH_FAILED_LIVE_STREAM_UKNOWN_INPUT);
        boolean trueLive = thisConn.getCaptureDevice().
            isCaptureFeatureSupported(MMC.LIVE_PREVIEW_MASK) && (!Sage.client || Sage.MAC_OS_X) &&
            thisConn.getType() != CaptureDeviceInput.DIGITAL_TUNER_CROSSBAR_INDEX;
        if (!trueLive && !thisConn.getCaptureDevice().canEncode())
          return Catbert.getWatchFailureObject(VideoFrame.WATCH_FAILED_NO_ENCODERS_HAVE_STATION);
        final MediaFile liveFile = trueLive ? MediaFile.getLiveMediaFileForInput(thisConn) :
          MediaFile.getLiveBufferedMediaFileForInput(thisConn);
        final Object taskID = Catbert.getNewTaskID();
        final UIManager uiMgr = stack.getUIMgrSafe();
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            Object rv = Catbert.getWatchFailureObject(uiMgr.getVideoFrame().lockTuner(liveFile));
            Catbert.asyncTaskComplete(taskID, rv);
          }
        }, "AsyncWatch");
        return taskID;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "WatchLocalFile", 1, new String[] { "File" })
    {
      /**
       * Instructs SageTV to playback the specified file path that's local to this client
       * @param file path to playback
       * @return true if the request was successful, a localized error message otherwise
       * @since 6.4
       *
       * @declaration public Object WatchLocalFile(java.io.File file);
       */
      public Object runSafely(final Catbert.FastStack stack) throws Exception{
        final Object o = stack.pop();
        final Object taskID = Catbert.getNewTaskID();
        final UIManager uiMgr = stack.getUIMgrSafe();
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            int watchCode = 0;
            if (o != null)
            {
              // Filename
              String fname = o.toString();
              if (uiMgr != null && uiMgr.hasRemoteFSSupport())
              {
                // Check to make sure the file exists
                MiniClientSageRenderer mcsr = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
                if ((mcsr.fsGetPathAttributes(fname) & MiniClientSageRenderer.FS_PATH_FILE) != 0)
                {
                  MediaFile remoteMF = Wizard.getInstance().getPlayableMediaFile("file://" + fname);
                  watchCode = uiMgr.getVideoFrame().watch(remoteMF);
                }
                else
                  watchCode = VideoFrame.WATCH_FAILED_FILES_NOT_ON_DISK;
              }
              else
              {
                MediaFile mf = Wizard.getInstance().getPlayableMediaFile(fname);
                if (mf != null)
                  watchCode = uiMgr.getVideoFrame().watch(mf);
                else
                  watchCode = VideoFrame.WATCH_FAILED_FILES_NOT_ON_DISK;
              }
            }
            else
              watchCode = VideoFrame.WATCH_FAILED_NULL_AIRING;
            Object rv = Catbert.getWatchFailureObject(watchCode);
            Catbert.asyncTaskComplete(taskID, rv);
          }
        }, "AsyncWatch");
        return taskID;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "StartPlaylist", 1, new String[]{"Playlist"})
    {
      /**
       * Starts playback of the specified Playlist. The MediaPlayer will playback everything in the Playlist
       * sequentially until it is done.
       * @param Playlist the Playlist to being playback of
       * @return true if the request was successful, a localized error message otherwise (failure will only occur due to parental control issues)
       *
       * @declaration public Object StartPlaylist(Playlist Playlist);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        final Playlist p = (Playlist) stack.pop();
        final Object taskID = Catbert.getNewTaskID();
        final UIManager uiMgr = stack.getUIMgrSafe();
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            Object rv = Catbert.getWatchFailureObject(uiMgr.getVideoFrame().startPlaylist(p, -1));
            Catbert.asyncTaskComplete(taskID, rv);
          }
        }, "AsyncStartPlaylist");
        return taskID;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "StartPlaylistAt", 2, new String[]{"Playlist", "StartIndex"})
    {
      /**
       * Starts playback of the specified Playlist. The MediaPlayer will playback everything in the Playlist
       * sequentially until it is done. Playback will begin at the item at the specified by the passed in index.
       * @param Playlist the Playlist to being playback of
       * @param StartIndex the index in the playlist to start playing at (1-based index)
       * @return true if the request was successful, a localized error message otherwise (failure will only occur due to parental control issues)
       *
       * @declaration public Object StartPlaylistAt(Playlist Playlist, int StartIndex);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        final int idx = getInt(stack);
        final Playlist p = (Playlist) stack.pop();
        final Object taskID = Catbert.getNewTaskID();
        final UIManager uiMgr = stack.getUIMgrSafe();
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            Object rv = Catbert.getWatchFailureObject(uiMgr.getVideoFrame().startPlaylist(p, idx));
            Catbert.asyncTaskComplete(taskID, rv);
          }
        }, "AsyncStartPlaylistAt");
        return taskID;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "CloseAndWaitUntilClosed")
    {
      /**
       * Closes the file that is currently loaded by the MediaPlayer and waits for the MediaPlayer to
       * completely free all of its resources before returning.
       *
       * @declaration public void CloseAndWaitUntilClosed();
       */
      public Object runSafely(final Catbert.FastStack stack) throws Exception{
        //			final Object taskID = Catbert.getNewTaskID();
        //			Thread asyncThread = new Thread("AsyncCloseAndWaitUntilClosed")
        //			{
        //				public void run()
        //				{
                    stack.getUIMgrSafe().getVideoFrame().closeAndWait();
        //					Catbert.asyncTaskComplete(taskID, null);
        //				}
        //			};
        //			asyncThread.setDaemon(true);
        //			asyncThread.start();
        //			return taskID;
        return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "IsMuted")
    {
      /**
       * Returns true if the MediaPlayer is currently in a muted state. This will not affect the system volume.
       * @return true if the MediaPlayer is muted, false otherwise
       *
       * @declaration public boolean IsMuted();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().getMute());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "SetMute", 1, new String[] {"Muted"})
    {
      /**
       * Sets the mute state for the MediaPlayer. This does not affect the system volume.
       * @param Muted true if the MediaPlayer should be muted, false otherwise
       *
       * @declaration public void SetMute(boolean Muted);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setMute(evalBool(stack.pop())); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetCurrentMediaTitle")
    {
      /**
       * Returns the title of the content that is currently loaded by the MediaPlayer.
       * @return the title of the content that is currently loaded by the MediaPlayer
       *
       * @declaration public String GetCurrentMediaTitle();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getVideoFrame().getMediaTitle();
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetMediaTime")
    {
      /**
       * Gets the current playback time of the MediaPlayer. For DVD content this time will return a value appropriate for
       * a current time display (starting at zero). For all other content types, this value will be the time in java.lang.System.currentTimeMillis() units
       * and is relative to the start time of the Airing metadata which represents the currently loaded file. So for a current time display you
       * should subtract the airing start time of the current media file from the returned value.
       * @return the current playback time of the MediaPlayer in milliseconds
       *
       * @declaration public long GetMediaTime();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(stack.getUIMgrSafe().getVideoFrame().getMediaTimeMillis(true));
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetRawMediaTime")
    {
      /**
       * Gets the current playback time of the MediaPlayer. This is the current playback time relative to the
       * start of the current playing file. For multiple segment files; this will be relative to the start of the currently
       * playing segment. This is intended to be used by plugin developers for linking events with the media time in the file.
       * @return the current playback time of the MediaPlayer in milliseconds
       * @since 7.0
       *
       * @declaration public long GetRawMediaTime();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(stack.getUIMgrSafe().getVideoFrame().getBaseMediaTimeMillis(true));
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetMediaDuration")
    {
      /**
       * Returns the duration of the currently loaded MediaFile in milliseconds.
       * @return the duration of the currently loaded MediaFile in milliseconds
       *
       * @declaration public long GetMediaDuration();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(stack.getUIMgrSafe().getVideoFrame().getDurationMillis());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetCurrentMediaFile")
    {
      /**
       * Returns the MediaFile object that is currently loaded (or loading) by the MediaPlayer
       * @return the MediaFile object that is currently loaded (or loading) by the MediaPlayer
       *
       * @declaration public MediaFile GetCurrentMediaFile();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getVideoFrame().getCurrFile();
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "HasMediaFile")
    {
      /**
       * Returns true if the MediaPlayer currently has a file that is loading or loaded.
       * @return true if the MediaPlayer currently has a file that is loading or loaded, false otherwise
       *
       * @declaration public boolean HasMediaFile();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().hasFile());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DoesCurrentMediaFileHaveVideo")
    {
      /**
       * Returns true if the MediaPlayer has a file loading or loaded that has video content in it
       * @return true if the MediaPlayer has a file loading or loaded that has video content in it, false otherwise
       *
       * @declaration public boolean DoesCurrentMediaFileHaveVideo();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = stack.getUIMgrSafe().getVideoFrame().getCurrFile();
        return (mf != null) && (mf.hasVideoContent()) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "IsCurrentMediaFileMusic")
    {
      /**
       * Returns true if the MediaPlayer has a file loading or loaded, and that file is a music file
       * @return true if the MediaPlayer has a file loading or loaded, and that file is a music file
       *
       * @declaration public boolean IsCurrentMediaFileMusic();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = stack.getUIMgrSafe().getVideoFrame().getCurrFile();
        return (mf != null) && (mf.isMusic()) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "IsCurrentMediaFileDVD")
    {
      /**
       * Returns true if the MediaPlayer has a file loading or loaded, and that file is a DVD
       * @return true if the MediaPlayer has a file loading or loaded, and that file is a DVD
       *
       * @declaration public boolean IsCurrentMediaFileDVD();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().isDVD() ||
            (MediaFile.INCLUDE_BLURAYS_AS_DVDS && stack.getUIMgrSafe().getVideoFrame().isBluRay()));
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "IsCurrentMediaFileRecording")
    {
      /**
       * Returns true if the MediaPlayer has a file loading or loaded, and that file is currently being recorded
       * @return true if the MediaPlayer has a file loading or loaded, and that file is currently being recorded
       *
       * @declaration public boolean IsCurrentMediaFileRecording();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().isFileRecording());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "IsPlaying")
    {
      /**
       * Returns true if the MediaPlayer is currently playing back content (i.e. content is fully loaded and not in the paused state)
       * @return true if the MediaPlayer is currently playing back content, false otherwise
       *
       * @declaration public boolean IsPlaying();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().isPlayin());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "IsShowingDVDMenu")
    {
      /**
       * Returns true if the MediaPlayer currently has DVD content loaded and that content is showing a DVD menu that can have user interaction
       * @return true if the MediaPlayer currently has DVD content loaded and that content is showing a DVD menu that can have user interaction, false otherwise
       *
       * @declaration public boolean IsShowingDVDMenu();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Sage.userLocale == null) return Boolean.FALSE;
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().isShowingDVDMenu());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetCurrentPlaylist")
    {
      /**
       * Returns the current Playlist that is being played back by the MediaPlayer. Playlists can be played back
       * using the call {@link #StartPlaylist StartPlaylist()}
       * @return the current Playlist that is being played back by the MediaPlayer, null otherwise
       *
       * @declaration public Playlist GetCurrentPlaylist();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getVideoFrame().getPlaylist();
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetCurrentPlaylistIndex")
    {
      /**
       * Returns the 0-based index into the root Playlist that is currently being played back by the MediaPlayer. 0 is returned
       * if no Playlist is currently being played back.
       * @return the 0-based index into the root Playlist that is currently being played back by the MediaPlayer. 0 is returned
       *           if no Playlist is currently being played back.
       *
       * @declaration public int GetCurrentPlaylistIndex();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getVideoFrame().getPlaylistIndex());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetAvailableSeekingStart")
    {
      /**
       * Gets the earliest time that the current media can be seeked to using the {@link #Seek Seek()} call. This
       * will be in absolute time.
       * @return the earliest time that the current media can be seeked to in milliseconds
       *
       * @declaration public long GetAvailableSeekingStart();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(stack.getUIMgrSafe().getVideoFrame().getAvailMediaStart());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetAvailableSeekingEnd")
    {
      /**
       * Gets the latest time that the current media can be seeked to using the {@link #Seek Seek()} call. This
       * will be in absolute time.
       * @return the latest time that the current media can be seeked to in milliseconds
       *
       * @declaration public long GetAvailableSeekingEnd();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(stack.getUIMgrSafe().getVideoFrame().getAvailMediaEnd());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "IsCorrectParentalLockCode", 1, new String[]{"ParentalLockCode"})
    {
      /**
       * Returns true if the argument passed in matches the parental lock code in the system
       * @param ParentalLockCode the code to test
       * @return true if the specified ParentalLockCode matches the parental lock code SageTV is configured to use
       *
       * @declaration public boolean IsCorrectParentalLockCode(String ParentalLockCode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().checkPCCode(getString(stack)));
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "SetVideoAlwaysOnTop", 1, new String[] {"OnTop"})
    {
      /**
       * Sets the video portion of SageTV to always be on top of other windows in the desktop (Windows only).
       * @param OnTop true if the video window of SageTV should be on top of all other windows in the system, false otherwise
       *
       * @declaration public void SetVideoAlwaysOnTop(boolean OnTop);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean x = evalBool(stack.pop());
        UIManager.setAlwaysOnTop(stack.getUIMgrSafe().getVideoFrame().getVideoHandle(), x);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDChapterNext")
    {
      /**
       * Informs the MediaPlayer to start playback of the next chapter in the current DVD content.
       *
       * @declaration public void DVDChapterNext();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_CHAPTER_NEXT); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDChapterPrevious")
    {
      /**
       * Informs the MediaPlayer to start playback of the previous chapter in the current DVD content.
       *
       * @declaration public void DVDChapterPrevious();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_CHAPTER_PREV); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDChapterSet", 1, new String[]{"ChapterNumber"})
    {
      /**
       * Informs the MediaPlayer to start playback of the specified chapter in the current DVD content.
       * @param ChapterNumber the chapter number to start playback of in the current DVD
       *
       * @declaration public void DVDChapterSet(int ChapterNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_CHAPTER_SET, getInt(stack), 0);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDEnter")
    {
      /**
       * Performs the 'Enter' operation when using a menu system in DVD content.
       *
       * @declaration public void DVDEnter();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_ACTIVATE_CURRENT); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDMenu")
    {
      /**
       * Performs the 'Menu' operation when playing back a DVD which should bring up the root menu of the DVD
       *
       * @declaration public void DVDMenu();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_MENU, 2, 0); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDTitleMenu")
    {
      /**
       * Performs the 'Menu' operation when playing back a DVD which should bring up the title menu of the DVD
       *
       * @declaration public void DVDTitleMenu();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_MENU, 1, 0); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDReturn")
    {
      /**
       * Performs the 'Return' operation when playing back a DVD which should bring the user back to the last DVD menu they were at
       *
       * @declaration public void DVDReturn();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_RETURN); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDTitleNext")
    {
      /**
       * Informs the MediaPlayer to start playback of the next title in the current DVD content.
       *
       * @declaration public void DVDTitleNext();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_TITLE_SET,
            stack.getUIMgrSafe().getVideoFrame().getDVDTitle() + 1, 1); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDTitlePrevious")
    {
      /**
       * Informs the MediaPlayer to start playback of the previous title in the current DVD content.
       *
       * @declaration public void DVDTitlePrevious();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_TITLE_SET,
            stack.getUIMgrSafe().getVideoFrame().getDVDTitle() - 1, 1); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDSubtitleToggle")
    {
      /**
       * Toggles the state for subtitle display in the DVD content being played back.
       *
       * @declaration public void DVDSubtitleToggle();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_SUBTITLE_TOGGLE); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDSubtitleChange", -1, new String[] { "SubtitleNum" })
    {
      /**
       * Sets the subtitle that should be displayed in the current DVD content. The names of the languages for the
       * corresponding subtitles are obtained from a call to {@link #GetDVDAvailableSubpictures GetDVDAvailableSubpictures()}.
       * If no arguments are given to this function then the currently displayed subtitle will be changed to the next one
       * @param SubtitleNum the 0-based index into the list of subtitles that should be displayed
       *
       * @declaration public void DVDSubtitleChange(int SubtitleNum);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 1)
          stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_SUBTITLE_CHANGE,
              getInt(stack), -1);
        else
          stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_SUBTITLE_CHANGE, -1, -1);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDAudioChange", -1, new String[] { "AudioNum" })
    {
      /**
       * Sets the audio language that should be used in the current DVD content. The names of the languages
       * are obtained from a call to {@link #GetDVDAvailableLanguages GetDVDAvailableLanguages()}.
       * If no arguments are given to this function then the current audio language will be changed to the next available language
       * @param AudioNum the 0-based index into the list of audio languages that should be used
       *
       * @declaration public void DVDAudioChange(int AudioNum);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 1)
          stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_AUDIO_CHANGE,
              getInt(stack), -1);
        else
          stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_AUDIO_CHANGE, -1, -1);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DVDAngleChange", -1, new String[] { "AngleNum" })
    {
      /**
       * Sets the 'Angle' for playback of the current DVD content. The number of angels
       * are obtained from a call to {@link #GetDVDNumberOfAngles GetDVDNumberOfAngles()}.
       * If no arguments are given to this function then the current angle will be changed to the next available angle
       * @param AngleNum the 1-based index that indicates which angle should be used for playback
       *
       * @declaration public void DVDAngleChange(int AngleNum);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 1)
          stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_ANGLE_CHANGE,
              getInt(stack), -1);
        else
          stack.getUIMgrSafe().getVideoFrame().playbackControl(VideoFrame.DVD_CONTROL_ANGLE_CHANGE, -1, -1);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "ReloadCurrentFile")
    {
      /**
       * Reloads the current file that is loaded by the MediaPlayer. This is useful when changing configuration options
       * for the MediaPlayer and then showing playback with those changes.
       *
       * @declaration public void ReloadCurrentFile();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().reloadFile(); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "DirectPlaybackControl", new String[] { "Code", "Param1", "Param2" })
    {
      /**
       * Submits an explicit playback control request to the MediaPlayer if it supports it. (Only DVD-based media players support this call)
       * @param Code this is the value of the control command to be sent to the player, must be one of the following:
       *        MENU = 201; Param1 should be 1 for title, 2 for root
       *        TITLE_SET = 202; Param1 should be the title number
       *        CHAPTER_SET = 205; Param1 should be the chapter number
       *        CHAPTER_NEXT = 206;
       *        CHAPTER_PREV = 207;
       *        ACTIVATE_CURRENT = 208;
       *        RETURN = 209;
       *        BUTTON_NAV = 210; Param1 should be 1(up), 2(right), 3(down) or 4(left)
       *        MOUSE_HOVER = 211; Param1 should be x and Param2 should be y
       *        MOUSE_CLICK = 212; Param1 should be x and Param2 should be y
       *        ANGLE_CHANGE = 213; Param1 should be the angle number (1-based)
       *        SUBTITLE_CHANGE = 214; Param1 should be the subtitle number (0-based)
       *        SUBTITLE_TOGGLE = 215;
       *        AUDIO_CHANGE = 216; Param1 should be the audio number (0-based)
       * @param Param1 the first parameter for the control command (see above)
       * @param Param2 the second parameter for the control command (see above)
       *
       * @declaration public void DirectPlaybackControl(int Code, long Param1, long Param2);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long p2 = getLong(stack);
        long p1 = getLong(stack);
        stack.getUIMgrSafe().getVideoFrame().playbackControl(getInt(stack), p1, p2); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetDVDCurrentTitle")
    {
      /**
       * Gets the current title number that is being played back for DVD content.
       * @return the current title number that is being played back for DVD content, 0 otherwise
       *
       * @declaration public int GetDVDCurrentTitle();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getVideoFrame().getDVDTitle());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetBluRayTitleDescription", new String[] { "TitleNum" })
    {
      /**
       * Returns a description of the specified title number if playing back a BluRay. This description will have
       * the total unique duration of the specified title and an asterisk if it is considered to be the 'main' title.
       * @param TitleNum the title number (1-based) to retrieve a description of
       * @return a description of the specified title number if playing back a BluRay; the empty string otherwise
       * @since 7.0
       *
       * @declaration public String GetBluRayTitleDescription(int TitleNum);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getVideoFrame().getBluRayTitleDesc(getInt(stack));
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetDVDNumberOfTitles")
    {
      /**
       * Gets the total number of titles in the current DVD content
       * @return the total number of titles in the current DVD content
       *
       * @declaration public int GetDVDNumberOfTitles();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getVideoFrame().getDVDTotalTitles());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetDVDCurrentChapter")
    {
      /**
       * Gets the current chapter number that is being played back for DVD content.
       * @return the current chapter number that is being played back for DVD content
       *
       * @declaration public int GetDVDCurrentChapter();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getVideoFrame().getDVDChapter());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetDVDNumberOfChapters")
    {
      /**
       * Gets the total number of chapters in the current title in the current DVD content
       * @return the total number of chapters in the current title in the current DVD content
       *
       * @declaration public int GetDVDNumberOfChapters();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getVideoFrame().getDVDTotalChapters());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetDVDCurrentDomain")
    {
      /**
       * Gets the current 'domain' that the DVD playback is in.
       * @return the current 'domain' that the DVD playback is in, uses the following values:
       *         1 = DVD initialization, 2 = disc menus, 3 = title menus, 4 = playback, 5 = stopped
       *
       * @declaration public int GetDVDCurrentDomain();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getVideoFrame().getDVDDomain());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetDVDCurrentAngle")
    {
      /**
       * Gets the current angle number that is being played back for DVD content
       * @return the current angle number that is being played back for DVD content
       *
       * @declaration public int GetDVDCurrentAngle();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getVideoFrame().getDVDAngle());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetDVDNumberOfAngles")
    {
      /**
       * Gets the total number of angles that are currently available to select from in the current DVD content
       * @return the total number of angles that are currently available to select from in the current DVD content
       *
       * @declaration public int GetDVDNumberOfAngles();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(stack.getUIMgrSafe().getVideoFrame().getDVDTotalAngles());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetDVDCurrentLanguage")
    {
      /**
       * Gets the current audio playback language that is being used for the current DVD content
       * @return the current audio playback language that is being used for the current DVD content
       *
       * @declaration public String GetDVDCurrentLanguage();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getVideoFrame().getDVDLanguage();
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetDVDAvailableLanguages")
    {
      /**
       * Gets a list of all of the audio languages that are currently available in the current DVD content
       * @return a list of all of the audio languages that are currently available in the current DVD content
       *
       * @declaration public String[] GetDVDAvailableLanguages();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getVideoFrame().getDVDAvailableLanguages();
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetDVDCurrentSubpicture")
    {
      /**
       * Gets the current subtitle that is being used for the current DVD content
       * @return the current subtitle that is being used for the current DVD content, null if subtitles are currently disabled
       *
       * @declaration public String[] GetDVDCurrentSubpicture();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getVideoFrame().getDVDSubpicture();
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetDVDAvailableSubpictures")
    {
      /**
       * Gets a list of all of the subtitles that are currently available in the current DVD content
       * @return a list of all of the subtitles that are currently available in the current DVD content
       *
       * @declaration public String[] GetDVDAvailableSubpictures();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getVideoFrame().getDVDAvailableSubpictures();
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetMediaPlayerClosedCaptionState")
    {
      /**
       * Gets the current state that MediaPlayer close captioning is set to use. This can be either a localized
       * version of "Captions Off" or one of the strings: "CC1", "CC2", "Text1", "Text2"
       * @return the current state that MediaPlayer close captioning is set to use
       *
       * @declaration public String GetMediaPlayerClosedCaptionState();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return BasicVideoFrame.getCCStateName(stack.getUIMgrSafe().getVideoFrame().getCCState());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "SetMediaPlayerClosedCaptionState", new String[] { "CCType" })
    {
      /**
       * Sets the current state that MediaPlayer close captioning should use. This can be one of the strings: "CC1", "CC2", "Text1", "Text2".
       * If any other value is used then closed captioning will be turned off.
       * @param CCType the new state that MediaPlayer close captioning should use
       *
       * @declaration public void SetMediaPlayerClosedCaptionState(String CCType);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setCCState(BasicVideoFrame.getCCStateCode(getString(stack))); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "IsMediaPlayerSignalLost")
    {
      /**
       * Returns true if the source the MediaPlayer is trying to playback from indicates a signal loss.
       * This can happen when trying to watch digital TV stations.
       * @return true if the MediaPlayer detects signal loss from the source it's playing back, false otherwise
       *
       * @declaration public boolean IsMediaPlayerSignalLost();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().isMediaPlayerSignaLost());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetSubtitleDelay")
    {
      /**
       * Gets the delay in milliseconds that is applied to external subtitle files when they are used during playback (can be positive or negative)
       * @return the delay in milliseconds that is applied to external subtitle files when they are used during playback.
       * @since 6.6
       *
       * @declaration public long GetSubtitleDelay();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(stack.getUIMgrSafe().getVideoFrame().getSubtitleDelay());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "SetSubtitleDelay", new String[] { "DelayMsec" })
    {
      /**
       * Sets the delay in milliseconds that is applied to external subtitle files when they are used during playback (can be positive or negative)
       * @param DelayMsec the delay in milliseconds that is applied to external subtitle files when they are used during playback
       * @since 6.6
       *
       * @declaration public void SetSubtitleDelay(long DelayMsec);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().setSubtitleDelay(getLong(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "CanAdjustSubtitleTiming")
    {
      /**
       * Returns true if the subtitles for the currently loaded file can have their timing adjusted. This is true for subtitles
       * that come from external files
       * @return true if the subtitles for the currently loaded file can have their timing adjusted; false otherwise
       * @since 6.6
       *
       * @declaration public boolean CanAdjustSubtitleTiming();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(stack.getUIMgrSafe().getVideoFrame().canAdjustSubtitleDelay());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "ApplyRelativeSubtitleAdjustment", new String[] { "SubCount" })
    {
      /**
       * Adjusts the timing for subtitle display by increasing/decreasing the delay so that the sub at the relative SubCount position
       * would be currently displayed.
       * @param SubCount the relative position from the current sub of the sub that should be displayed now
       * @return the value in milliseconds of the current subtitle delay (same as return from GetSubtitleDelay())
       * @since 6.6
       *
       * @declaration public long ApplyRelativeSubtitleAdjustment(int SubCount);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().getVideoFrame().applyRelativeSubAdjustment(getInt(stack));
        return new Long(stack.getUIMgrSafe().getVideoFrame().getSubtitleDelay());
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetRecentChannels")
    {
      /**
       * Returns an array of recently viewed channels. This tracks channels viewed from any mechanism; live or DVR'd.
       * @return an array of the most recently viewed channels, most recently viewed are at the head of the array
       * @since 8.0
       *
       * @declaration public Channel[] GetRecentChannels();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] recentStationIDs = Sage.getRawProperties().getMRUList("recent_channels", 10);
        java.util.ArrayList rv = new java.util.ArrayList();
        for (int i = 0; recentStationIDs != null && i < recentStationIDs.length; i++)
        {
          try
          {
            int id = Integer.parseInt(recentStationIDs[i]);
            if (id != 0)
            {
              Channel c = Wizard.getInstance().getChannelForStationID(id);
              if (c != null && c.isViewable() && Wizard.getInstance().getAirings(id, Sage.time(), Sage.time() + 1, false).length > 0)
                rv.add(c);
            }
          }
          catch (NumberFormatException nfe){}
        }
        return (Channel[]) rv.toArray(new Channel[0]);
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "ClearRecentChannels")
    {
      /**
       * Clears the list of recently viewed channels.
       * @since 8.0
       *
       * @declaration public void ClearRecentChannels();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Sage.removeNode("mru/recent_channels");
        return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaPlayer", "GetVideoSnapshot")
    {
      /**
       * Returns an image which is a frame grab of the currently rendered video frame. This is currently only
       * supported on Windows when using VMR9 with 3D acceleration
       * @return a java.awt.image.BufferedImage which holds the last rendered video frame, or null if the call cannot be completed
       *
       * @declaration public java.awt.image.BufferedImage GetVideoSnapshot();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SageRenderer renderEngine = stack.getUIMgrSafe().getRootPanel().getRenderEngine();
        if (renderEngine instanceof DirectX9SageRenderer)
          return ((DirectX9SageRenderer) renderEngine).getVideoSnapshot();
        else
          return null;
      }});
    /*
		rft.put(new PredefinedJEPFunction("MediaPlayer", "", 0)
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 stack.getUIMgr().getVideoFrame().; return null;
			}});
     */
  }
}
