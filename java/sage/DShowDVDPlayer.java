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

public class DShowDVDPlayer extends DShowMediaPlayer implements DVDMediaPlayer
{
  protected static final String DVD_VIDEO_DECODER_FILTER = "dvd_video_decoder_filter";
  protected static final String DVD_AUDIO_DECODER_FILTER = "dvd_audio_decoder_filter";
  private static final String DVD_AUDIO_RENDER_FILTER = "dvd_audio_render_filter";
  private static final String DVD_DXVA_MPEG_MODE = "dvd_dxva_mpeg_mode";
  private static final String DVD_FORCE_DEINTERLACE = "dvd_force_deinterlace";
  private static final String DVD_VIDEO_RENDER_FILTER = "dvd_video_render_filter";

  public static int getDVDForceDeinterlace() { return Sage.getInt(PREFS_ROOT + DVD_FORCE_DEINTERLACE, 0); }
  public static void setDVDForceDeinterlace(int x) { Sage.putInt(PREFS_ROOT + DVD_FORCE_DEINTERLACE, x); }
  public static int getDVDDxvaMpegMode() { return Sage.getInt(PREFS_ROOT + DVD_DXVA_MPEG_MODE, 0); }
  public static void setDVDDxvaMpegMode(int x) { Sage.putInt(PREFS_ROOT + DVD_DXVA_MPEG_MODE, x); }
  public static String getDVDAudioRenderFilter()
  {
    String rv = Sage.get(PREFS_ROOT + DVD_AUDIO_RENDER_FILTER, "");
    if (rv.length() == 0)
      return Sage.rez("Default");
    else
      return rv;
  }
  public static void setDVDAudioRenderFilter(String x) { Sage.put(PREFS_ROOT + DVD_AUDIO_RENDER_FILTER, x); }
  public static String getDVDVideoRenderFilter()
  {
    // Default to using the OverlayMixer for video rendering because it supports the mouse in DVD menus,
    // that currently doesn't work in VMR9
    String rv = Sage.get(PREFS_ROOT + DVD_VIDEO_RENDER_FILTER, "Overlay");
    if (rv.length() == 0)
      return Sage.rez("Default");
    else
      return rv;
  }
  public static void setDVDVideoRenderFilter(String x) { Sage.put(PREFS_ROOT + DVD_VIDEO_RENDER_FILTER, x); }
  public static String getDVDAudioDecoderFilter()
  {
    String rv = Sage.get(PREFS_ROOT + DVD_AUDIO_DECODER_FILTER, "");
    if (rv.length() == 0)
      return Sage.rez("Default");
    else
      return rv;
  }
  public static void setDVDAudioDecoderFilter(String x) { Sage.put(PREFS_ROOT + DVD_AUDIO_DECODER_FILTER, x); }
  public static String getDVDVideoDecoderFilter()
  {
    String rv = Sage.get(PREFS_ROOT + DVD_VIDEO_DECODER_FILTER, "");
    if (rv.length() == 0)
      return Sage.rez("Default");
    else
      return rv;
  }
  public static void setDVDVideoDecoderFilter(String x) { Sage.put(PREFS_ROOT + DVD_VIDEO_DECODER_FILTER, x); }

  public DShowDVDPlayer()
  {
    super();
  }

  public int getPlaybackCaps()
  {
    return super.getPlaybackCaps() | PLAY_REV_CAP | PLAYRATE_FAST_CAP | PLAYRATE_SLOW_CAP |
        PLAYRATE_SLOW_REV_CAP | PLAYRATE_FAST_REV_CAP;
  }

  public float getPlaybackRate()
  {
    return dvdPlayRate;
  }

  /*
   * NOTE NOTE NOTE: The DVD player needs to go through Sage when it makes the initial call to start the graph or
   * it will hang.
   */
  public synchronized void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    // Convert network shares from a non-Windows server to be UNC paths so the DVD navigator can access them
    if (file != null && Sage.WINDOWS_OS && file.toString().startsWith("\\tmp\\sagetv_shares\\"))
    {
      file = new java.io.File("\\\\" + file.toString().substring("\\tmp\\sagetv_shares\\".length()));
    }
    if (file != null && file.isFile())
    {
      // This is an ISO image instead of a DVD directory; so mount it and then change the file path to be the image
      java.io.File mountDir = FSManager.getInstance().requestISOMount(file, VideoFrame.getVideoFrameForPlayer(this).getUIMgr());
      if (mountDir == null)
      {
        if (Sage.DBG) System.out.println("FAILED mounting ISO image for DVD playback");
        throw new PlaybackException(PlaybackException.FILESYSTEM, 0);
      }
      unmountRequired = mountDir;
      if (new java.io.File(mountDir, "video_ts").isDirectory())
        file = new java.io.File(mountDir, "video_ts");
      else if (new java.io.File(mountDir, "VIDEO_TS").isDirectory())
        file = new java.io.File(mountDir, "VIDEO_TS");
      else
        file = mountDir;
    }
    super.load(majorTypeHint, minorTypeHint, encodingHint, file, hostname, timeshifted, bufferSize);
    dvdPlayRate = 1.0f;
  }

  public void free()
  {
    super.free();
    if (unmountRequired != null)
    {
      java.io.File removeMe = unmountRequired;
      unmountRequired = null;
      FSManager.getInstance().releaseISOMount(removeMe);
    }
  }

  public boolean playControlEx(int playCode, long param1, long param2) throws PlaybackException
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      synchronized (this)
      {
        if (processEvents0(pHandle) == 1)
          eos = true;
        boolean rv = playbackControlMessage0(pHandle, playCode, param1, param2);
        lastAspectRatio = getDVDAspectRatio0(pHandle);
        return rv;
      }
    }
    return false;
  }

  public long seek(long seekTimeMillis) throws PlaybackException
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        seekDVD0(pHandle, seekTimeMillis);
        // We have to run the graph when seeking a DVD or it doesn't render the new frames
        if (currState == PAUSE_STATE)
          play();
        return getMediaTimeMillis();
      }
    }
    else
      return 0;
  }

  public float setPlaybackRate(float newRate)
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        if (setDVDRate0(pHandle, newRate))
          dvdPlayRate = newRate;
        return dvdPlayRate;
      }
    }
    else
      return 1.0f;
  }

  protected void setFilters() throws PlaybackException
  {
    String vidDec = getDVDVideoDecoderFilter();
    java.util.Map renderOptions = new java.util.HashMap();
    int dxvaMode = getDVDDxvaMpegMode();
    if (dxvaMode != 0)
      renderOptions.put(DVD_DXVA_MPEG_MODE, new Integer(dxvaMode));
    int deinterlaceMode = getDVDForceDeinterlace();
    if (deinterlaceMode != 0)
      renderOptions.put(DVD_FORCE_DEINTERLACE, new Integer(deinterlaceMode));
    String videoRender = getDVDVideoRenderFilter();
    nullVideoDim = false;
    if (vidDec.indexOf("NVIDIA") != -1 && Sage.get("videoframe/dvd_video_postprocessing_filter", "").length() == 0 &&
        Sage.getBoolean("videoframe/use_nvidia_pp_when_decoder_selected", false))
    {
      setVideoPostProcessingFilter0(pHandle, "NVIDIA Video Post Processor", null);
    }
    else if (Sage.get("videoframe/dvd_video_postprocessing_filter", "").length() > 0)
    {
      setVideoPostProcessingFilter0(pHandle, Sage.get("videoframe/dvd_video_postprocessing_filter", ""), null);
    }
    if ("VMR9".equals(videoRender) || "EVR".equals(videoRender))
    {
      if (DirectX9SageRenderer.getD3DObjectPtr() != 0 && DirectX9SageRenderer.getD3DDevicePtr() != 0 &&
          ("VMR9".equals(videoRender) || DirectX9SageRenderer.getD3DDeviceMgr() != 0))
      {
        renderOptions.put("d3d_device_ptr", new Long(DirectX9SageRenderer.getD3DDevicePtr()));
        renderOptions.put("d3d_object_ptr", new Long(DirectX9SageRenderer.getD3DObjectPtr()));
        renderOptions.put("d3d_device_mgr", new Long(DirectX9SageRenderer.getD3DDeviceMgr()));
        setVideoRendererFilter0(pHandle, "EVR".equals(videoRender) ? EVR_GUID : VMR9_GUID, renderOptions);
        nullVideoDim = true;
        transparency = TRANSLUCENT;
      }
      else
      {
        setVideoRendererFilter0(pHandle, OVERLAY_GUID, renderOptions);
        transparency = BITMASK;
      }
    }
    else if ("Overlay".equals(videoRender))
    {
      setVideoRendererFilter0(pHandle, OVERLAY_GUID, renderOptions);
      transparency = BITMASK;
    }
    else
      transparency = OPAQUE;
    String audDec = getDVDAudioRenderFilter();
    if (audDec.length() > 0 && !"Default".equals(audDec) && !Sage.rez("Default").equals(audDec))
      setAudioRendererFilter0(pHandle, audDec, null);

    if (vidDec.length() > 0 && !"Default".equals(vidDec) && !Sage.rez("Default").equals(vidDec))
      setVideoDecoderFilter0(pHandle, vidDec, null);
    audDec = getDVDAudioDecoderFilter();
    if (audDec.length() > 0 && !"Default".equals(audDec) && !Sage.rez("Default").equals(audDec))
      setAudioDecoderFilter0(pHandle, audDec, null);
  }

  public boolean areDVDButtonsVisible()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return areDVDButtonsVisible0(pHandle);
      }
    }
    return false;
  }

  public int getDVDAngle()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDAngle0(pHandle);
      }
    }
    return 0;
  }

  public String[] getDVDAvailableLanguages()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDAvailableLanguages0(pHandle);
      }
    }
    return new String[0];
  }

  public String[] getDVDAvailableSubpictures()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDAvailableSubpictures0(pHandle);
      }
    }
    return new String[0];
  }

  public int getDVDChapter()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDChapter0(pHandle);
      }
    }
    return 0;
  }

  public int getDVDTotalChapters()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDTotalChapters0(pHandle);
      }
    }
    return 0;
  }

  public int getDVDDomain()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDDomain0(pHandle);
      }
    }
    return 0;
  }

  public String getDVDLanguage()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDLanguage0(pHandle);
      }
    }
    return "";
  }

  public String getDVDSubpicture()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDSubpicture0(pHandle);
      }
    }
    return "";
  }

  public int getDVDTitle()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDTitle0(pHandle);
      }
    }
    return 0;
  }

  public int getDVDTotalAngles()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDTotalAngles0(pHandle);
      }
    }
    return 0;
  }

  public int getDVDTotalTitles()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      synchronized (this)
      {
        return getDVDTotalTitles0(pHandle);
      }
    }
    return 0;
  }

  public float getCurrentAspectRatio()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE)
    {
      // If we make the native call now it'll deadlock against the rendering threads, so do it when we process events instead.
      return lastAspectRatio;
    }
    return 0;
  }

  public void setVideoRectangles(java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor)
  {
    // For VMR9 this updates the mouse coordinates, so leave the call there in all cases for DVD playback
    if (currState != NO_STATE)
    {
      synchronized (this)
      {
        resizeVideo0(pHandle, videoSrcRect, videoDestRect, hideCursor);
      }
    }
  }

  private native int getDVDTitle0(long ptr);
  private native int getDVDTotalTitles0(long ptr);
  private native int getDVDChapter0(long ptr);
  private native int getDVDTotalChapters0(long ptr);
  private native int getDVDDomain0(long ptr);
  private native boolean areDVDButtonsVisible0(long ptr);
  private native int getDVDAngle0(long ptr);
  private native int getDVDTotalAngles0(long ptr);
  private native String getDVDLanguage0(long ptr);
  private native String[] getDVDAvailableLanguages0(long ptr);
  private native String getDVDSubpicture0(long ptr);
  private native String[] getDVDAvailableSubpictures0(long ptr);
  protected native boolean setDVDRate0(long ptr, float newRate);
  protected native boolean seekDVD0(long ptr, long time) throws PlaybackException;
  protected native long getMediaTimeMillis0(long ptr);
  protected native long getDurationMillis0(long ptr);
  protected native boolean playbackControlMessage0(long ptr, int code, long param1, long param2) throws PlaybackException;
  protected native int processEvents0(long ptr) throws PlaybackException;

  // createGraph0 will create the peer native object and create the initial filter graph
  protected native long createGraph0() throws PlaybackException;
  // setupGraph0 adds all of the filters to the graph and connects it up appropriately
  protected native void setupGraph0(long ptr, String filePath, String remoteHost, boolean renderVideo, boolean renderAudio) throws PlaybackException;

  protected native float getDVDAspectRatio0(long ptr);

  private float dvdPlayRate = 1.0f;

  protected int currDVDTitle;
  protected int currDVDTotalTitles;
  protected int currDVDChapter;
  protected int currDVDTotalChapters;
  protected int currDVDDomain;
  protected long currDVDNotifyHwnd;
  protected boolean dvdButtonsVisible;
  protected int currDVDAngle;
  protected int currDVDTotalAngles;
  protected String currDVDLanguage;
  protected String[] currDVDAvailableLanguages;
  protected String currDVDSubpicture;
  protected String[] currDVDAvailableSubpictures;
  protected float lastAspectRatio;
  protected java.io.File unmountRequired;
}
