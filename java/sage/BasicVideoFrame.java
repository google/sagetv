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

public abstract class BasicVideoFrame extends java.awt.Panel
{
  protected static final String VIDEOFRAME_KEY = "videoframe";
  protected static final String VOLUME_STEP = "volume_step";
  protected static final String MUTE_ON_ALT_SPEED_PLAY = "mute_on_alt_speed_play";

  protected static final long HNAN2MIL = 10000L; // convert 100nano to milli
  protected static final String VIDEO_BG_COLOR = "video_bg_color";
  protected static final String VIDEO_HORIZONTAL_SHIFT_FREQ = "video_horizontal_shift_freq";
  private static final String ASPECT_RATIO_MODE = "aspect_ratio_mode";
  private static final String VIDEO_TRANSLATE_X = "video_translate_x";
  private static final String VIDEO_TRANSLATE_Y = "video_translate_y";
  private static final String VIDEO_ZOOM_X = "video_zoom_x";
  private static final String VIDEO_ZOOM_Y = "video_zoom_y";
  private static final String DISPLAY_ASPECT_RATIO = "display_aspect_ratio";

  public static final int ASPECT_FILL = 0;
  public static final int ASPECT_SOURCE = 1;
  public static final int ASPECT_4X3 = 2;
  public static final int ASPECT_16X9 = 3;
  public static final int NUM_ASPECT_RATIO_CODES = 4;
  public static final String[] ASPECT_RATIO_NAMES = {"Fill", "Source", "4x3", "16x9"};
  public static String[] getAspectRatioModes()
  {
    String[] rv = new String[ASPECT_RATIO_NAMES.length];
    rv[0] = Sage.rez(ASPECT_RATIO_NAMES[0]);
    rv[1] = Sage.rez(ASPECT_RATIO_NAMES[1]);
    rv[2] = ASPECT_RATIO_NAMES[2];
    rv[3] = ASPECT_RATIO_NAMES[3];
    return rv;
  }
  public static String getAspectRatioName(int x)
  {
    if (x == ASPECT_FILL)
      return Sage.rez("Fill");
    else if (x == ASPECT_SOURCE)
      return Sage.rez("Source");
    else if (x == ASPECT_4X3)
      return "4x3";
    else if (x == ASPECT_16X9)
      return "16x9";
    else
      return "";
  }
  public static int getAspectRatioCode(String x)
  {
    if (Sage.rez("Fill").equals(x))
      return ASPECT_FILL;
    else if (Sage.rez("Source").equals(x))
      return ASPECT_SOURCE;
    else if ("4x3".equals(x))
      return ASPECT_4X3;
    else if ("16x9".equals(x))
      return ASPECT_16X9;
    else
      return 0;
  }

  public static String getCCStateName(int x)
  {
    switch (x)
    {
      case MediaPlayer.CC_DISABLED:
        return Sage.rez("Closed_Caption_Off");
      case MediaPlayer.CC_ENABLED_CAPTION1:
        return "CC1";
      case MediaPlayer.CC_ENABLED_CAPTION2:
        return "CC2";
      case MediaPlayer.CC_ENABLED_TEXT1:
        return "CC3";
      case MediaPlayer.CC_ENABLED_TEXT2:
        return "CC4";
    }
    if (x >= MediaPlayer.CC_ENABLED_DTV_BASE && x < (MediaPlayer.CC_ENABLED_DTV_BASE + MediaPlayer.CC_DTV_COUNT))
      return "DTVCC" + (x + 1 - MediaPlayer.CC_ENABLED_DTV_BASE);
    return Sage.rez("Closed_Caption_Off");
  }
  public static int getCCStateCode(String x)
  {
    if ("CC1".equals(x))
      return MediaPlayer.CC_ENABLED_CAPTION1;
    else if ("CC2".equals(x))
      return MediaPlayer.CC_ENABLED_CAPTION2;
    else if ("Text1".equals(x) || "CC3".equals(x))
      return MediaPlayer.CC_ENABLED_TEXT1;
    else if ("Text2".equals(x) || "CC4".equals(x))
      return MediaPlayer.CC_ENABLED_TEXT2;
    else if (x != null && x.startsWith("DTVCC"))
    {
      try
      {
        int i = Integer.parseInt(x.substring(5));
        if (i > 0 && i <= MediaPlayer.CC_DTV_COUNT)
          return MediaPlayer.CC_ENABLED_DTV_BASE + i - 1;
      }
      catch (NumberFormatException nfe)
      {
        if (Sage.DBG) System.out.println("ERROR parsing CC format name " + x + " of:" + nfe);
      }
    }
    return MediaPlayer.CC_DISABLED;
  }

  protected static final long TS_DELAY = 1500L;

  public BasicVideoFrame(UIManager inUIMgr)
  {
    super();
    prefs = VIDEOFRAME_KEY + '/';
    uiMgr = inUIMgr;

    volumeStep = uiMgr.getInt(prefs + VOLUME_STEP, 5)/100f;
    muteOnAlt = uiMgr.getBoolean(prefs + MUTE_ON_ALT_SPEED_PLAY, true);
    aspectRatioMode = uiMgr.getInt(prefs + ASPECT_RATIO_MODE, ASPECT_SOURCE);
    videoTranslateX = uiMgr.getInt(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_TRANSLATE_X, 0);
    videoTranslateY = uiMgr.getInt(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_TRANSLATE_Y, 0);
    videoZoomX = uiMgr.getFloat(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_ZOOM_X, 1.0f);
    videoZoomY = uiMgr.getFloat(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_ZOOM_Y, 1.0f);
    displayAspectRatio = uiMgr.getFloat(prefs + DISPLAY_ASPECT_RATIO, 16.0f/9.0f);
    String colorStr = uiMgr.get(prefs + VIDEO_BG_COLOR, "0,0,0");
    java.util.StringTokenizer toker = new java.util.StringTokenizer(colorStr, ",");
    try
    {
      videoBGColor = new java.awt.Color(Integer.parseInt(toker.nextToken()),
          Integer.parseInt(toker.nextToken()), Integer.parseInt(toker.nextToken()));
    }
    catch (Exception e)
    {
      videoBGColor = new java.awt.Color(30, 30, 30);
    }
    videoHShiftFreq = uiMgr.getLong(prefs + VIDEO_HORIZONTAL_SHIFT_FREQ, 0);
    if (videoHShiftFreq != 0)
    {
      createVideoHShiftTimer();
    }

    setLayout(new java.awt.LayoutManager()
    {
      public void addLayoutComponent(String name, java.awt.Component comp){}
      public java.awt.Dimension minimumLayoutSize(java.awt.Container parent)
      {
        return preferredLayoutSize(parent);
      }
      public java.awt.Dimension preferredLayoutSize(java.awt.Container parent)
      {
        return parent.getPreferredSize();
      }
      public void removeLayoutComponent(java.awt.Component comp){}
      public void layoutContainer(java.awt.Container parent)
      {
        videoComp.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        refreshVideoSizing();
      }
    });
    setFocusTraversalKeysEnabled(false);
    setBackground(java.awt.Color.gray);
    videoComp = new VideoCanvas();
    videoComp.setFocusTraversalKeysEnabled(false);
    add(videoComp);
    videoComp.setVisible(false);
    mmc = MMC.getInstance();
    forcedPAR = uiMgr.getFloat("ui/forced_pixel_aspect_ratio", 0);
  }

  public void setVideoBounds(java.awt.geom.Rectangle2D.Float x)
  {
    videoBounds = x;
  }

  public abstract java.awt.Dimension getVideoSize();

  private long cachedVideoHandle;
  public void resetVideoHandleCache()
  {
    cachedVideoHandle = 0;
  }
  public long getVideoHandle()
  {
    return getVideoHandle(false);
  }
  public long getVideoHandle(boolean noCache)
  {
    return (cachedVideoHandle == 0 || noCache) ? (cachedVideoHandle = UIUtils.getHWND(videoComp)) : cachedVideoHandle;
  }

  void addMouseListeners(java.awt.event.MouseListener listy)
  {
    addMouseListener(listy);
    videoComp.addMouseListener(listy);
  }

  void addMouseMotionListeners(java.awt.event.MouseMotionListener listy)
  {
    addMouseMotionListener(listy);
    videoComp.addMouseMotionListener(listy);
  }

  void removeMouseListeners(java.awt.event.MouseListener listy)
  {
    removeMouseListener(listy);
    videoComp.removeMouseListener(listy);
  }

  void removeMouseMotionListeners(java.awt.event.MouseMotionListener listy)
  {
    removeMouseMotionListener(listy);
    videoComp.removeMouseMotionListener(listy);
  }

  void addKeyListeners(java.awt.event.KeyListener listy)
  {
    addKeyListener(listy);
    videoComp.addKeyListener(listy);
  }

  public boolean getMuteOnAltSpeedPlay() { return muteOnAlt; }
  public void setMuteOnAltSpeedPlay(boolean x) { uiMgr.putBoolean(prefs + MUTE_ON_ALT_SPEED_PLAY, muteOnAlt = x); }

  private static final int VIDEO_TRANSLATE_X_PROP_INDEX = 0;
  private static final int VIDEO_TRANSLATE_Y_PROP_INDEX = 1;
  private static final int VIDEO_ZOOM_X_PROP_INDEX = 2;
  private static final int VIDEO_ZOOM_Y_PROP_INDEX = 3;
  protected static final String[][] AR_MODES_PROPS = {
    { VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 0 + '/' + VIDEO_TRANSLATE_X,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 0 + '/' + VIDEO_TRANSLATE_Y,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 0 + '/' + VIDEO_ZOOM_X,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 0 + '/' + VIDEO_ZOOM_Y
    },{ VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 1 + '/' + VIDEO_TRANSLATE_X,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 1 + '/' + VIDEO_TRANSLATE_Y,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 1 + '/' + VIDEO_ZOOM_X,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 1 + '/' + VIDEO_ZOOM_Y
    },{ VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 2 + '/' + VIDEO_TRANSLATE_X,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 2 + '/' + VIDEO_TRANSLATE_Y,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 2 + '/' + VIDEO_ZOOM_X,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 2 + '/' + VIDEO_ZOOM_Y
    },{ VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 3 + '/' + VIDEO_TRANSLATE_X,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 3 + '/' + VIDEO_TRANSLATE_Y,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 3 + '/' + VIDEO_ZOOM_X,
      VIDEOFRAME_KEY + '/' + ASPECT_RATIO_MODE + '/' + 3 + '/' + VIDEO_ZOOM_Y
    }};

  public float getVideoZoomX(int assMode)
  {
    return uiMgr.getFloat(AR_MODES_PROPS[assMode][VIDEO_ZOOM_X_PROP_INDEX], 1.0f);
  }
  public float getVideoZoomY(int assMode)
  {
    return uiMgr.getFloat(AR_MODES_PROPS[assMode][VIDEO_ZOOM_Y_PROP_INDEX], 1.0f);
  }
  public int getVideoOffsetX(int assMode)
  {
    return uiMgr.getInt(AR_MODES_PROPS[assMode][VIDEO_TRANSLATE_X_PROP_INDEX], 0);
  }
  public int getVideoOffsetY(int assMode)
  {
    return uiMgr.getInt(AR_MODES_PROPS[assMode][VIDEO_TRANSLATE_Y_PROP_INDEX], 0);
  }
  public int getAspectRatioMode() { return aspectRatioMode; }
  public float getDisplayAspectRatio() { return displayAspectRatio; }
  public void setPixelAspectRatio(float f) { forcedPAR = f; }
  public float getPixelAspectRatio()
  {
    if (forcedPAR > 0)
      return forcedPAR;
    // We need to determine the full size of the UI display and then we can get the PAR from the DAR + DisplayResolution
    java.awt.Dimension displayRes = uiMgr.getUIDisplayResolution();
    if (displayRes == null)
      return displayAspectRatio * (MMC.getInstance().isNTSCVideoFormat() ? 480 : 576) / (720.0f);
    return (displayAspectRatio * displayRes.height) / (displayRes.width);
  }
  public void setVideoSizingParams(int transX, int transY, float zoomX, float zoomY, int aspectMode)
  {
    if (aspectRatioMode != aspectMode || transX != videoTranslateX || transY != videoTranslateY ||
        zoomX != videoZoomX || zoomY != videoZoomY)
    {
      uiMgr.putInt(prefs + ASPECT_RATIO_MODE, aspectRatioMode = aspectMode);
      uiMgr.putFloat(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_ZOOM_X, videoZoomX = zoomX);
      uiMgr.putFloat(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_ZOOM_Y, videoZoomY = zoomY);
      uiMgr.putInt(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_TRANSLATE_X, videoTranslateX = transX);
      uiMgr.putInt(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_TRANSLATE_Y, videoTranslateY = transY);
      refreshVideoSizing();
    }
  }
  public void setVideoZoomX(float zoomX, int aspectMode)
  {
    uiMgr.putFloat(prefs + ASPECT_RATIO_MODE + '/' + aspectMode + '/' + VIDEO_ZOOM_X, zoomX);
    if (aspectRatioMode == aspectMode && zoomX != videoZoomX)
    {
      videoZoomX = zoomX;
      refreshVideoSizing();
    }
  }
  public void setVideoZoomY(float zoomY, int aspectMode)
  {
    uiMgr.putFloat(prefs + ASPECT_RATIO_MODE + '/' + aspectMode + '/' + VIDEO_ZOOM_Y, zoomY);
    if (aspectRatioMode == aspectMode && zoomY != videoZoomY)
    {
      videoZoomY = zoomY;
      refreshVideoSizing();
    }
  }
  public void setVideoOffsetX(int offX, int aspectMode)
  {
    uiMgr.putInt(prefs + ASPECT_RATIO_MODE + '/' + aspectMode + '/' + VIDEO_TRANSLATE_X, offX);
    if (aspectRatioMode == aspectMode && videoTranslateX != offX)
    {
      videoTranslateX = offX;
      refreshVideoSizing();
    }
  }
  public void setVideoOffsetY(int offY, int aspectMode)
  {
    uiMgr.putInt(prefs + ASPECT_RATIO_MODE + '/' + aspectMode + '/' + VIDEO_TRANSLATE_Y, offY);
    if (aspectRatioMode == aspectMode && videoTranslateY != offY)
    {
      videoTranslateY = offY;
      refreshVideoSizing();
    }
  }
  public void setAspectRatioMode(int aspectMode)
  {
    if (aspectRatioMode != aspectMode)
    {
      uiMgr.putInt(prefs + ASPECT_RATIO_MODE, aspectRatioMode = aspectMode);
      videoZoomX = uiMgr.getFloat(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_ZOOM_X, 1.0f);
      videoZoomY = uiMgr.getFloat(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_ZOOM_Y, 1.0f);
      videoTranslateX = uiMgr.getInt(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_TRANSLATE_X, 0);
      videoTranslateY = uiMgr.getInt(prefs + ASPECT_RATIO_MODE + '/' + aspectRatioMode + '/' + VIDEO_TRANSLATE_Y, 0);
      refreshVideoSizing();
      // This will allow the UI to be refreshed by the user
      Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
    }
  }

  public void setDisplayAspectRatio(float f)
  {
    if (f != displayAspectRatio)
    {
      uiMgr.putFloat(prefs + DISPLAY_ASPECT_RATIO, displayAspectRatio = f);
      refreshVideoSizing();
    }
  }

  public abstract float getCurrentAspectRatio();
  public void refreshVideoSizing()
  {
    if (getParent() == null) return;
    refreshVideoSizing(getVideoSize());
  }
  public void refreshVideoSizing(java.awt.Dimension vsize)
  {
    if (getParent() == null) return;
    sage.geom.Rectangle myBounds;
    if (videoBounds == null)
    {
      myBounds = new sage.geom.Rectangle(0, 0, getParent().getWidth(), getParent().getHeight());
    }
    else
      myBounds = new sage.geom.Rectangle(videoBounds);
    int vw, vh;
    vw = vsize == null ? 0 : vsize.width;
    vh = vsize == null ? 0 : vsize.height;
    if (vw <= 0)
      vw = 720;
    if (vh <= 0)
      vh = MMC.getInstance().isNTSCVideoFormat() ? 480 : 576;
    sage.geom.Rectangle videoSrc = new sage.geom.Rectangle(0, 0, vw, vh);
    sage.geom.Rectangle videoDest = new sage.geom.Rectangle(myBounds.x, myBounds.y, myBounds.width, myBounds.height);
    int assMode = getAspectRatioMode();
    float forcedRatio = getCurrentAspectRatio();
    if (forcedRatio != 0)
    {
      if (videoDest.width/videoDest.height < forcedRatio)
      {
        float shrink = videoDest.height - videoDest.width/forcedRatio;
        videoDest.height -= shrink;
        videoDest.y += shrink/2;
      }
      else
      {
        float shrink = videoDest.width - videoDest.height*forcedRatio;
        videoDest.width -= shrink;
        videoDest.x += shrink/2;
      }
    }
    float zoomX = getVideoZoomX(assMode);
    float zoomY = getVideoZoomY(assMode);
    int transX = getVideoOffsetX(assMode);
    int transY = getVideoOffsetY(assMode);

    float widthAdjust = (zoomX - 1.0f)*videoDest.width;
    float heightAdjust = (zoomY - 1.0f)*videoDest.height;
    videoDest.x -= widthAdjust/2;
    videoDest.y -= heightAdjust/2;
    videoDest.width += widthAdjust;
    videoDest.height += heightAdjust;

    videoDest.x += transX;
    videoDest.y += transY;

    if (videoHShiftFreq != 0)
    {
      float maxHShift = (myBounds.width - videoDest.width)/2;
      long timeDiff = Sage.eventTime();
      timeDiff %= videoHShiftFreq;
      if (timeDiff < videoHShiftFreq/2)
      {
        if (timeDiff < videoHShiftFreq/4)
          videoDest.x -= maxHShift*timeDiff*4/videoHShiftFreq;
        else
          videoDest.x -= maxHShift - (maxHShift*(timeDiff - videoHShiftFreq/4)*4/videoHShiftFreq);
      }
      else
      {
        timeDiff -= videoHShiftFreq/2;
        if (timeDiff < videoHShiftFreq/4)
          videoDest.x += maxHShift*timeDiff*4/videoHShiftFreq;
        else
          videoDest.x += maxHShift - (maxHShift*(timeDiff - videoHShiftFreq/4)*4/videoHShiftFreq);
      }
    }

    Sage.clipSrcDestRects(myBounds, videoSrc, videoDest);
    /*EMBEDDED_SWITCH*/
    java.awt.Rectangle srcVideoRect = new java.awt.Rectangle();
    java.awt.Rectangle usedVideoRect = new java.awt.Rectangle();
    srcVideoRect.setRect(videoSrc.x, videoSrc.y, videoSrc.width, videoSrc.height);
    usedVideoRect.setRect(videoDest.x, videoDest.y, videoDest.width, videoDest.height);
    resizeVideo(srcVideoRect, usedVideoRect);
    /*/
		resizeVideo(videoSrc, videoDest);
/**/
    /*		// NOTE: If this is not run async on the UI thread it can deadlock because validate goes down into
		// the UI system and comes back up through the layout method above which locks things
		// that can conflict with the final renderer
		java.awt.EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				videoComp.invalidate();
				validate();
			}
		});
     */
  }

  public java.awt.Color getVideoBGColor() { return videoBGColor; }
  public void setVideoBGColor(java.awt.Color x) { uiMgr.put(prefs + VIDEO_BG_COLOR, Integer.toString(x.getRed()) +
      "," + Integer.toString(x.getGreen()) + "," + Integer.toString(x.getBlue())); videoBGColor = x; }

  public long getVideoHShiftFreq() { return videoHShiftFreq; }
  public void setVideoHShiftFreq(long x)
  {
    if (x == videoHShiftFreq) return;
    uiMgr.putLong(prefs + VIDEO_HORIZONTAL_SHIFT_FREQ, (videoHShiftFreq = x));
    if (videoHShiftTimer != null)
    {
      videoHShiftTimer.cancel();
      videoHShiftTimer = null;
    }
    if (x != 0)
    {
      createVideoHShiftTimer();
    }
  }

  protected abstract void createVideoHShiftTimer();

  public abstract void inactiveFile(String inactiveFilename);

  public abstract boolean hasFile();

  protected abstract void resizeVideo(final java.awt.Rectangle videoSrcRect, final java.awt.Rectangle videoDestRect);

  boolean setHideCursor(boolean x)
  {
    boolean rv = (x != hideCursor);
    hideCursor = x;
    return rv;
  }

  java.awt.Canvas getVideoComp() { return videoComp; }

  protected MMC mmc;
  protected UIManager uiMgr;
  protected String prefs;
  protected float volumeStep;

  protected final Object queueLock = new Object();

  protected VideoCanvas videoComp;

  private int videoTranslateX;
  private int videoTranslateY;
  private float videoZoomX;
  private float videoZoomY;
  protected int aspectRatioMode;

  private float displayAspectRatio;

  protected boolean muteOnAlt;

  protected long videoHShiftFreq;
  protected java.awt.Color videoBGColor;
  protected java.util.TimerTask videoHShiftTimer;

  protected boolean hideCursor;

  protected java.awt.geom.Rectangle2D.Float videoBounds;

  protected float forcedPAR;

  static class VideoCanvas extends java.awt.Canvas
  {
    public void update(java.awt.Graphics g) { }
    public void paint(java.awt.Graphics g) { }
  }
}
