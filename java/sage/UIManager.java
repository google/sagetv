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

import sage.media.format.VideoFormat;
import sage.plugin.CorePluginManager;
import tv.sage.ModuleGroup;
import tv.sage.ModuleManager;
import tv.sage.SageException;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.LayoutManager;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.WeakHashMap;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class UIManager implements Runnable, UIClient
{
  public static final boolean ENABLE_STUDIO = true;

  boolean repaintNextRegionChange = false;
  private Set<Rectangle> lastRects = new HashSet<Rectangle>();
  private Rectangle lastRectBounds = null;
  void setCompoundWindowRegion2(long winID, Rectangle[] rects, int[] roundness)
  {
    if (!Sage.WINDOWS_OS) return;
    Set<Rectangle> newRects = new HashSet<Rectangle>(Arrays.asList(rects));
    if (lastRects != null && lastRects.equals(newRects))
    {
      // If it's still the same, just bail out
      return;
    }
    // dontRepaint is false only if there was something removed
    boolean dontRepaint = true;
    if (lastRects != null)
    {
      // Find the new rect bounds
      Rectangle newRectBounds = new Rectangle();
      if (rects.length > 0)
        newRectBounds.setFrame(rects[0]);
      for (int i = 1; i < rects.length; i++)
        newRectBounds.add(rects[i]);
      dontRepaint = lastRectBounds != null && lastRectBounds.equals(newRectBounds);
      lastRectBounds = newRectBounds;
    }
    // Since I've added color keying this should be less of a problem, and it caused lots of flashing artifacts
    dontRepaint = !repaintNextRegionChange; // FOR DEBUG TESTING
    repaintNextRegionChange = false;
    lastRects = newRects;
    lastRectBounds = new Rectangle();
    for (int i = 0; i < rects.length; i++)
      lastRectBounds.add(rects[i]);
    setCompoundWindowRegion(winID, rects, roundness, dontRepaint);
  }

  void clearWindowRegion2(long winID)
  {
    if (!Sage.WINDOWS_OS) return;
    if (lastRects != null)
    {
      clearWindowRegion(winID);
    }
    lastRects = null;
  }

  private static native void setCompoundWindowRegion(long winID, Rectangle[] rects, int[] roundness, boolean dontRepaint);
  private static native void clearWindowRegion(long winID);
  public static native void setCursorClip(Rectangle r);

  public static boolean sendMessage(long winID, int msgID)
  {
    if (sendMessage(winID, msgID, 0)) return true;
    else
    {
      if (Sage.DBG) System.out.println("FAILURE in sendMessage(" + winID + ", " + msgID +
          ")");
      return false;
    }
  }

  public static native boolean sendMessage(long winID, int msgID, int msgData);

  public static long findWindow(String winName)
  {
    return findWindow(winName, null);
  }

  public static native long findWindow(String winName, String winClass);

  public static long getHWND(Canvas canvas)
  {
    return UIUtils.getHWND(canvas);
  }

  public static native void setAlwaysOnTop(long winID, boolean state);

  private static native void setAppTaskbarState0(long winID, boolean state);

  public static void setLocalAppTaskbarState(boolean visible)
  {
    if (Sage.WINDOWS_OS)
    {
      UIManager localUI = getLocalUI();
      if (localUI != null)
      {
        setAppTaskbarState0(getHWND(localUI.getRootPanel()), visible);
      }
    }
  }

  public static int getCursorPosX()
  {
    if (Sage.WINDOWS_OS)
    {
      return getCursorPosX0();
    }
    else
      return -1;
  }

  public static int getCursorPosY()
  {
    if (Sage.WINDOWS_OS)
    {
      return getCursorPosY0();
    }
    else
      return -1;
  }

  private static native int getCursorPosX0();

  private static native int getCursorPosY0();

  public static final boolean ANTIALIAS = true;
  public static final String SAGE = ("SageTV V" + Version.VERSION);
  public static final DecimalFormat floatFormat = new DecimalFormat("0.###");

  public static final int NO_STARTUP = 0;
  public static final int STARTUP_FS = 1;
  public static final int STARTUP_SLEEP = 2;

  public static final String UI_KEY = "ui";
  private static final String SHOW_RATINGS = "show_ratings";
  private static final String LAST_WIN_POSX = "last_win_posx";
  private static final String LAST_WIN_POSY = "last_win_posy";
  private static final String LAST_WIN_WIDTH = "last_win_width";
  private static final String LAST_WIN_HEIGHT = "last_win_height";
  private static final String LAST_WIN_FS = "last_win_fs";
  private static final String USE_VOLATILE_IMAGE = "use_volatile_image";
  public static final String BUTTONS = "buttons";
  private static final String SCREEN_SAVER_ON_SLEEP = "screen_saver_on_sleep";
  public static final String STARTUP_TYPE = "startup_type";
  public static final String DISPOSE_WINDOWS = "dispose_windows";
  public static final String UI_OVERSCAN_CORRECTION_PERCT_WIDTH = "ui_overscan_correction_perct_width";
  public static final String UI_OVERSCAN_CORRECTION_PERCT_HEIGHT = "ui_overscan_correction_perct_height";
  public static final String UI_OVERSCAN_CORRECTION_OFFSET_X = "ui_overscan_correction_offset_x";
  public static final String UI_OVERSCAN_CORRECTION_OFFSET_Y = "ui_overscan_correction_offset_y";
  public static final String DISABLE_SCREEN_SAVER_AND_PM = "disable_screen_saver_and_pm";
  public static final String UI_HISTORY_DEPTH = "ui_history_depth";
  public static final String EPG_ANIMATION = "epg_animation";
  public static final String REVERSE_EPG_CHANNELS = "reverse_epg_channels";
  public static final String BACKGROUND_IMAGE = "background_image";
  private static UIManager chosenOne;
  public static final String RC_MAPPINGS = "rc_mappings";
  public static final String MAIN_MENU = "Main Menu";
  public static final String UNITIALIZED_MAIN_MENU = "Uninitialized Main Menu";
  private static Object localUISyncLock = new Object();
  private static Set<UIManager> allUIs = Collections.synchronizedSet(new HashSet<UIManager>());

  public static UIManager getLocalUI()
  {
    synchronized (localUISyncLock)
    {
      if (chosenOne != null)
      {
        return chosenOne;
      }
      chosenOne = createLocalUI();
      return chosenOne;
    }
  }

  public static Iterator<UIManager> getUIIterator()
  {
    // We don't want iteration over this to fail with a concurrent mod exception
    return new HashSet<UIManager>(allUIs).iterator();
  }

  public static UIManager getLocalUIByName(String name)
  {
    synchronized (allUIs)
    {
      for (UIManager uiMgr : allUIs)
      {
        if (uiMgr.getLocalUIClientName().equals(name))
          return uiMgr;
      }
    }
    return null;
  }

  public static int getNonLocalUICount()
  {
    return (allUIs.size() - (chosenOne == null ? 0 : 1));
  }

  private static UIManager createLocalUI()
  {
    UIManager uiMgr = new UIManager(Seeker.LOCAL_PROCESS_CLIENT);
    uiMgr.init();
    return uiMgr;
  }

  private UIManager(String inUIClientName)
  {
    uiClientName = inUIClientName;
    if (!Seeker.LOCAL_PROCESS_CLIENT.equals(uiClientName))
    {
      uiProps = new SageProperties(Sage.client);
      new File("clients").mkdirs();
      uiProps.setupPrefs("clients" + File.separator + uiClientName + ".properties",
          Sage.getPath("core","RemoteClients.properties.defaults"), Sage.getRawProperties());
    }
    vf = new VideoFrame(this);
    // NOTE: Once we add ourself to the UI map we MUST mark us as alive. Otherwise when we call goodbye() we won't
    // remove ourself from that map.
    allUIs.add(this);
    alive = true;
  }

  public boolean isAlive() {
    return alive;
  }

  public static UIManager createAltUI(String uiName)
  {
    if (Sage.DBG) System.out.println("Creating new UI for client:" + uiName);
    UIManager uiMgr = new UIManager(uiName);
    if (Sage.DBG) System.out.println("Creating-2 new UI for client:" + uiName + " " + uiMgr);
    try
    {
      uiMgr.init();
    }
    catch (Throwable thr)
    {
      if (Sage.DBG)
      {
        System.out.println("Error creating new UI:" + thr);
        thr.printStackTrace();
      }
      uiMgr.goodbye();
      return null;
    }
    // Thread this on the same call or the UIManager may end up getting killed before it finished its init...
    // BUT that can still happen!!! So if we did get killed then mark us as alive again (so we can be cleaned up properly)
    // and re-kill this UI
    if (!uiMgr.alive)
    {
      if (Sage.DBG) System.out.println("UIMgr was asynchronously killed during construction, re-destroy it now that init is done");
      uiMgr.alive = true;
      uiMgr.goodbye();
      return null;
    }
    if (Sage.DBG) System.out.println("Creating-3 new UI for client:" + uiName + " " + uiMgr);
    uiMgr.run();
    if (Sage.DBG) System.out.println("Creating-4 new UI for client:" + uiName + " " + uiMgr);
    //Thread t = new Thread(uiMgr);
    //t.start();
    if (!uiMgr.alive)
    {
      if (Sage.DBG) System.out.println("UIMgr was asynchronously killed during construction, re-destroy it now that init is done");
      uiMgr.alive = true;
      uiMgr.goodbye();
      return null;
    }
    return uiMgr;
  }

  private void init()
  {
    mmc = MMC.getInstance();
    prefs = UI_KEY + '/';
    ueToIRCodesMap = new HashMap<Integer, long[]>();
    irCodeToNameMap = new HashMap<Long, String>();
    irCodeToUEMap = new HashMap<Long, Integer>();
    imageCacheMap = new HashMap<String, Object>();
    strImageCacheMap = new Hashtable<String, MetaImage>();
    cursorCacheMap = new HashMap<String, Cursor>();
    iconCacheMap = new HashMap<String, Object>();
    audioClipCache = new HashMap<String, SoftReference<WaveFile>>();
    // This one is Weak on the Widget references so that unloading a UI can kill that part of the cache.
    // It is NOT Weak on the PseudoMenus; so it will essentially cache the whole UI structure
    uiWidgMap = new WeakHashMap<Widget, LinkedHashMap<Map<String, Object>, PseudoMenu>>();
    screenSaverOnSleep = getBoolean(prefs + SCREEN_SAVER_ON_SLEEP, true);
    uiHistoryDepth = getInt(prefs + UI_HISTORY_DEPTH, 10);
    uiHistory = new Vector<PseudoMenu>(uiHistoryDepth);
    windowless = getBoolean("ui/windowless", false);
    uiLockDebug = getBoolean("ui/lock_debug", false);
    coreAnimsEnabled = getBoolean("ui/animation/core_enabled", true);

    overscanX = getInt(prefs + UI_OVERSCAN_CORRECTION_OFFSET_X, 0);
    overscanY = getInt(prefs + UI_OVERSCAN_CORRECTION_OFFSET_Y, 0);
    overscanWidth = getFloat(prefs + UI_OVERSCAN_CORRECTION_PERCT_WIDTH, 1.0f);
    overscanHeight = getFloat(prefs + UI_OVERSCAN_CORRECTION_PERCT_HEIGHT, 1.0f);
    String startupSound = Sage.get("startup_sound", "");
    if (startupSound.length() > 0)
    {
      playSound(startupSound);
    }

    if (Sage.isHeadless() && uiClientName.equals(Seeker.LOCAL_PROCESS_CLIENT)) return;
    if (!windowless)
    {
      if (Sage.masterWindow == null)
        Sage.masterWindow = win = new SpecialWindow(Sage.rez("SageTV") + (Sage.isTrueClient() ? " Client" : ""),
            Sage.getInt("ui/window_title_style", Sage.VISTA_OS ? SageTVWindow.PLATFORM_TITLE_STYLE : 0));
      else if (uiClientName.equals(Seeker.LOCAL_PROCESS_CLIENT))
      {
        win = Sage.masterWindow;
        win.setTitle(Sage.rez("SageTV") + (Sage.isTrueClient() ? " Client" : ""));
      }
      else
      {
        // NOTE: TEMPORARY HACK FOR TESTING MULTIPLE UIS ON ONE DESKTOP
        // Secondary client running with a windowed UI
        win = new SpecialWindow(Sage.rez("SageTV") + "-" + uiClientName, Sage.getInt("ui/window_title_style", Sage.VISTA_OS ? SageTVWindow.PLATFORM_TITLE_STYLE : 0));
      }
      win.setUIMgr(this);
      win.setIconImage(ImageUtils.fullyLoadImage("images/tvicon.gif"));
      win.setBackground(Color.black);
      win.setDecorationState(getInt("ui/frame_decoration", 0));
      win.addNotify();
      WindowAdapter focusAndClose =
          new WindowAdapter()
      {
        public void windowClosing(WindowEvent evt)
        {
          if (getBoolean("ignore_window_close", false))
          {
          }
          else if (getBoolean("sleep_on_close", false))
            gotoSleep(true);
          else if (uiClientName.equals(Seeker.LOCAL_PROCESS_CLIENT))
            SageTV.exit();
          else
            goodbye();
        }

        public void windowGainedFocus(WindowEvent evt)
        {
          win.toFront();
        }

      };
      win.addWindowListener(focusAndClose);
      win.addWindowFocusListener(focusAndClose);
      String cursorProp = get("ui/cursor_image", "");
      if (cursorProp.length() > 0)
      {
        customCursor = ImageUtils.createCursor(cursorProp, "CustomCursor",
            getInt("ui/cursor_hotx", 4), getInt("ui/cursor_hoty", 4), false, false);
        if (customCursor != null)
        {
          EventQueue.invokeLater(new Runnable()
          {
            public void run()
            {
              win.setCursor(customCursor);
            }
          });
        }
      }
    }
    else
      fakePanel = new Panel();

    router = new EventRouter(this);

    rootPanel = new ZRoot(this);
    rootPanel.addKeyListener(router);
    rootPanel.addMouseListener(router);
    rootPanel.addMouseMotionListener(router);
    rootPanel.setFocusTraversalKeysEnabled(false);
    basePanel = new ZComp(rootPanel)
    {
      public void doLayoutNow()
      {
        if (numKids == 0) return;
        kids[0].setBounds(0, 0, size.width, size.height);
        kids[0].doLayout();
      }
    };
    rootPanel.setRoot(basePanel);

    LayoutManager layer = new LayoutManager()
    {
      public void addLayoutComponent(String name, Component comp)
      {}
      public Dimension minimumLayoutSize(Container parent)
      {
        return preferredLayoutSize(parent);
      }
      public Dimension preferredLayoutSize(Container parent)
      {
        return parent.getPreferredSize();
      }
      public void removeLayoutComponent(Component comp)
      {}
      public void layoutContainer(Container parent)
      {
        if (embeddedPanel != null)
        {
          if (Sage.MAC_OS_X)
          {
            // This is actually used on Mac for part of the UI architecture, so don't rely on the embedded bounds
            embeddedPanel.setBounds(0, 0, parent.getWidth(), parent.getHeight());
          }
          else
          {
            // NOTE: For now, just make the embedded panel full size if it has any children
            if (embeddedBounds == null || embeddedPanel.getComponentCount() == 0 || embeddedBounds.width == 0 || embeddedBounds.height == 0)
              embeddedPanel.setBounds(0, 0, 0, 0);
            else
            {
              Rectangle embedBounds = new Rectangle(
                  (int)(embeddedBounds.x * (parent.getWidth() - embeddedBounds.getWidth() * parent.getWidth())),
                  (int)(embeddedBounds.y * (parent.getHeight() - embeddedBounds.getHeight() * parent.getHeight())),
                  (int)(embeddedBounds.getWidth() * parent.getWidth()),
                  (int)(embeddedBounds.getHeight() * parent.getHeight()));
              embeddedPanel.setBounds(embedBounds);//0, 0, parent.getWidth(), parent.getHeight());
            }
          }
        }
        rootPanel.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        Rectangle theVBounds = videoBounds;
        if (theVBounds == null)
          vf.setBounds(0, 0, parent.getWidth(), parent.getHeight());//vf.setBounds(x, y, w, h);
        else
          vf.setBounds(theVBounds);
      }
    };
    if (!windowless)
    {
      win.getContentPane().setLayout(layer);
      embeddedPanel = new Panel();
      embeddedPanel.setLayout(new BorderLayout());
      win.getContentPane().add(embeddedPanel);
      embeddedPanel.setVisible(false);
      win.getContentPane().add(rootPanel, "Center");
    }
    else
    {
      fakePanel.setLayout(layer);
      fakePanel.add(rootPanel, "Center");
    }
    if (!windowless)
      win.getContentPane().add(vf);
    else
      fakePanel.add(vf);

    Sage.setSplashText(Sage.rez("Module_Init", new Object[]
        { Sage.rez("Rendering_Engine") }));
    rootPanel.initializeRenderer();
  }

  public Dimension getScreenSize()
  {
    if (windowless)
    {
      Dimension scrSize = new Dimension();
      scrSize.width = getInt("osd_rendering_width", 720);
      scrSize.height = MMC.getInstance().isNTSCVideoFormat() ?
          getInt("osd_rendering_height_ntsc", 480) : getInt("osd_rendering_height_pal", 576);
          return scrSize;
    }
    else if (fsExMode && fsWin != null)
    {
      Rectangle rect = fsWin.getCurrScreenBounds();
      return new Dimension(rect.width, rect.height);
    }
    else if (win != null)
    {
      Rectangle rect = win.getCurrScreenBounds();
      return new Dimension(rect.width, rect.height);
    }
    else
    {
      return Toolkit.getDefaultToolkit().getScreenSize();
    }
  }

  // Returns true if the current screen size matches the passed in structure; otherwise returns false and updates
  // the structure with the new size
  public boolean doesMatchScreenSize(Dimension dims)
  {
    Dimension scrSize = getScreenSize();
    if (dims.width == scrSize.width && dims.height == scrSize.height)
      return true;
    dims.width = scrSize.width;
    dims.height = scrSize.height;
    return false;
  }

  private void loadIRButtonMappings()
  {
    String[] existingButtons = keys(prefs + BUTTONS);
    for (int i = 0; i < existingButtons.length; i++)
    {
      try
      {
        irCodeToNameMap.put(new Long(existingButtons[i]), get(prefs + BUTTONS + '/' +
            existingButtons[i], null));
      }catch (NumberFormatException e)
      {}
    }
    if (existingButtons.length == 0)
    {
    }
  }

  private void loadUEIRMappings()
  {
    for (int i = UserEvent.MIN_EVT_ID; i <= UserEvent.MAX_EVT_ID; i++)
    {
      String str = get(prefs + RC_MAPPINGS + '/' + UserEvent.getEvtName(i), null);
      List<Long> validCodes = new ArrayList<Long>();
      if (str != null)
      {
        StringTokenizer toker = new StringTokenizer(str, ",");
        while (toker.hasMoreTokens())
        {
          try
          {
            validCodes.add(new Long(toker.nextToken()));
          }catch (NumberFormatException e)
          {}
        }
      }
      else
      {
        // Hasn't been set yet, do the default.
        long[] defaultIR = UserEvent.getDefaultIRCodes(i);
        for (int j = 0; j < defaultIR.length; j++)
          validCodes.add(defaultIR[j]);
      }

      // Check to see if these codes were used anywhere yet.
      for (int j = 0; j < validCodes.size(); j++)
      {
        if (irCodeToUEMap.get(validCodes.get(j)) != null)
          validCodes.remove(j--);
        else
          irCodeToUEMap.put(validCodes.get(j), i);
      }

      long[] currCodes = new long[validCodes.size()];
      for (int j = 0; j < currCodes.length; j++)
        currCodes[j] = validCodes.get(j);
      ueToIRCodesMap.put(i, currCodes);
    }

    if (getUIClientType() == REMOTE_UI && uiProps != null)
    {
      // Now load the IR codes again from our properties directly without using any defaults. This will ensure
      // that a default doesn't override a local setting while still allowing defaults for IR codes
      for (int i = UserEvent.MIN_EVT_ID; i <= UserEvent.MAX_EVT_ID; i++)
      {
        Object o = uiProps.getWithNoDefault(prefs + RC_MAPPINGS + '/' + UserEvent.getEvtName(i));
        if (o == null) continue;
        String str = o.toString();
        List<Long> validCodes = new ArrayList<Long>();
        StringTokenizer toker = new StringTokenizer(str, ",");
        while (toker.hasMoreTokens())
        {
          try
          {
            validCodes.add(new Long(toker.nextToken()));
          }catch (NumberFormatException e)
          {}
        }

        for (int j = 0; j < validCodes.size(); j++)
        {
          irCodeToUEMap.put(validCodes.get(j), i);
        }

        long[] currCodes = new long[validCodes.size()];
        for (int j = 0; j < currCodes.length; j++)
          currCodes[j] = validCodes.get(j);
        ueToIRCodesMap.put(i, currCodes);
      }
    }
  }

  public int getUECodeForIRCode(long irCode)
  {
    Integer inty = irCodeToUEMap.get(irCode);
    if (inty == null) return UserEvent.ANYTHING;
    else return inty;
  }

  public String getNameForIRCode(long irCode)
  {
    String rv = irCodeToNameMap.get(irCode);
    return (rv == null) ? Long.toString(irCode) : rv;
  }

  public long[] getIRCodesForUE(int ueCode)
  {
    long[] rv = ueToIRCodesMap.get(ueCode);
    if (rv == null) return new long[0];
    else return rv;
  }

  private native boolean setHidden0(boolean state, boolean fromMouseAction);

  public void setHidden(boolean x, boolean fromMouseAction)
  {
    if (!windowless && Sage.MAC_OS_X && !Sage.isHeadless()) {
      // this will hide until the mouse is moved, otherwise we have to balance hide/unhide calls
      cursorHidden = setHidden0(x, fromMouseAction);
      return;
    }

    if (windowless) return;
    boolean layVF = false;
    synchronized (vf.getTreeLock())
    {
      if (x)
      {
        if (isFullScreen())
        {
          layVF = vf.setHideCursor(true);
          if (hiddenCursor == null)
            hiddenCursor = Toolkit.getDefaultToolkit().
            createCustomCursor(new BufferedImage(1,1,
                BufferedImage.TYPE_4BYTE_ABGR),(new Point(0,0)),"HiddenM");
          EventQueue.invokeLater(new Runnable()
          {
            public void run()
            {
              if (fsWin != null)
                fsWin.setCursor(hiddenCursor);
              win.setCursor(hiddenCursor);
            }
          });
        }
        cursorHidden = true;
      }
      else
      {
        if (fromMouseAction)
        {
          layVF = vf.setHideCursor(false);
          EventQueue.invokeLater(new Runnable()
          {
            public void run()
            {
              if (fsWin != null)
                fsWin.setCursor(customCursor);
              win.setCursor(customCursor);
            }
          });
        }
        cursorHidden = false;
      }
    }

    // I think this was causing the black flicker of the screen, 11/27/02
    // 12/4 I think I need it so mouse hiding gets triggered by the resize event
    // 9/13/05 - I moved this out of the getTreeLock() sync block because it caused a deadlock
    // when getting the size from the player.

    if (layVF)
    {
      router.invokeLater(new Runnable()
      {
        public void run()
        {
          vf.doLayout();
        }
      });
    }
  }

  public boolean isCurrUIScreenSaver()
  {
    return (currUI != null) && (currUI.getBlueprint().getName().equals("Screen Saver"));
  }

  public boolean isAsleep()
  {
    return isTaskbar;
  }

  public void gotoSleep(boolean sleepy)
  {
    if (Sage.DBG) System.out.println("UIManager.gotoSleep(" + sleepy + ") isTaskbar=" + isTaskbar);
    if (sleepy == isTaskbar) // redundant call
    {
      if (!sleepy && !windowless)
      {
        router.invokeLater(new Runnable()
        {
          public void run()
          {
            if (fsExMode && fsWin != null)
              fsWin.toFront();
            else
              win.toFront();
          }
        });
      }
      return;
    }
    boolean clearMenus = getBoolean("ui/reset_menu_history_on_sleep", true);
    if (!sleepy)
    {
      isTaskbar = false;
      if (!windowless)
      {
        // Be sure it's not minimized when it's restored!
        if (fsExMode && fsWin != null)
          fsWin.setExtendedState(oldWinState & ~Frame.ICONIFIED);
        win.setExtendedState(oldWinState & ~Frame.ICONIFIED);
        if (Sage.WINDOWS_OS || !getBoolean("hide_java_ui_window", Sage.LINUX_OS))
        {
          if (fsExMode && fsWin != null)
            fsWin.setVisible(true);
          else
            win.setVisible(true);
        }
        if (Sage.WINDOWS_OS && uiClientName.equals(Seeker.LOCAL_PROCESS_CLIENT))
        {
          Sage.removeTaskbarIcon0(Sage.mainHwnd);
          Sage.setupHooksSync();
        }
      }
      Sage.gc(true);
      if (clearMenus)
        advanceUI(MAIN_MENU);
      else
        backupUI();
      if (!windowless)
      {
        if (killedAcceleratedRenderingForSleep)
        {
          killedAcceleratedRenderingForSleep = false;
          rootPanel.setAcceleratedDrawing(true, false);
          // NOTE: We need to refresh here because the rendering engine change could effect layout.
          if (currUI != null)
          {
            router.invokeLater(new Runnable()
            {
              public void run()
              {
                currUI.refresh();
              }
            });
          }
        }
        router.invokeLater(new Runnable()
        {
          public void run()
          {
            if (fsExMode && fsWin != null)
              fsWin.toFront();
            else
              win.toFront();
          }
        });
      }
    }
    else
    {
      if (clearMenus)
        uiHistory.clear();
      if (Sage.DBG) System.out.println("Sage is going to SLEEP");
      isTaskbar = true;
      vf.sleep();
      boolean usingOSDRender = false;
      if (rootPanel != null && rootPanel.getRenderEngine() instanceof Java2DSageRenderer &&
          ((Java2DSageRenderer) rootPanel.getRenderEngine()).hasOSDRenderer())
        usingOSDRender = true;
      if (Sage.WINDOWS_OS && (!screenSaverOnSleep || (!windowless && !isFullScreen() && !usingOSDRender)))
      {
        advanceUI(MAIN_MENU);
        // Kill the 3D system if it's active, then reload it after we wakeup
        if (getBoolean("ui/unload_3d_on_sleep", true) && rootPanel.isAcceleratedDrawing())
        {
          rootPanel.setAcceleratedDrawing(false, false);
          killedAcceleratedRenderingForSleep = true;
        }
        else if (getBoolean("ui/clear_vram_on_sleep", false) && uiClientName.equals(Seeker.LOCAL_PROCESS_CLIENT))
        {
          final SageRenderer renderEngine = rootPanel.getRenderEngine();
          if (renderEngine instanceof NativeImageAllocator)
          {
            router.invokeLater(new Runnable()
            {
              public void run()
              { MetaImage.clearNativeCache((NativeImageAllocator) renderEngine); }
            });
          }
        }
        if (!windowless)
        {
          if (fsExMode && fsWin != null)
          {
            // Wait here so that FSE can get cleaned up properly before we hide the windows and cause the device to be lost
            try{Thread.sleep(1000);}catch(Exception e3){}
            oldWinState = fsWin.getExtendedState();
            fsWin.setExtendedState(Frame.ICONIFIED);
          }
          else
          {
            oldWinState = win.getExtendedState();
            win.setExtendedState(Frame.ICONIFIED);
          }
          if (uiClientName.equals(Seeker.LOCAL_PROCESS_CLIENT))
          {
            Sage.addTaskbarIcon0(Sage.mainHwnd);
            Sage.releaseSystemHooks0(Sage.mainHwnd);
          }
          if (fsExMode && fsWin != null)
            hideWindow(fsWin);
          else
            hideWindow(win);
        }
      }
      else
      {
        advanceUI("Screen Saver");
      }

      if (clearMenus)
      {
        uiHistory.clear();
        uiHistoryIndex = 0;
      }
      Sage.gc(true);
    }
  }

  public void reloadSystemHooks()
  {
    if (Sage.WINDOWS_OS && uiClientName.equals(Seeker.LOCAL_PROCESS_CLIENT))
    {
      Sage.releaseSystemHooks0(Sage.mainHwnd);
      try{Thread.sleep(500);}catch(Exception e){}
      Sage.setupHooksSync();
    }
  }

  public long getLastDeepSleepAwakeTime()
  {
    return lastDeepSleepTime;
  }

  // This is for power management when the system shuts down
  public void deepSleep(boolean x)
  {
    if (Sage.DBG) System.out.println("UIManager.deepSleep(" + x + ")");
    if (!x)
    {
      lastDeepSleepTime = Sage.eventTime();
      // On power resumption the Scheduler will automatically reload the devices. The 3D system will
      // also auto-rebuild itself when it detects the device failure after power resume.
      // IR xmt devs will get reloaded by the cap devs. We also need to reload IR recv devices.
      if (router != null)
      {
        router.loadInputPlugins();
        router.startInputPlugins();
      }
      if (rootPanel != null && killedAcceleratedRenderingForSleep)
      {
        killedAcceleratedRenderingForSleep = false;
        rootPanel.setAcceleratedDrawing(true, false);
      }
      trueValidate();
    }
    else
    {
      if (Sage.DBG) System.out.println("UIManager System power is suspending");
      if (!Sage.isHeadless() || !uiClientName.equals(Seeker.LOCAL_PROCESS_CLIENT))
      {
        if (!EventQueue.isDispatchThread() && vf != null && Sage.getBoolean("ui/close_media_player_on_standby", true))
          vf.closeAndWait();
      }

      // Kill the 3D system if it's active, then reload it after we wakeup
      if (rootPanel != null && rootPanel.isAcceleratedDrawing())
      {
        rootPanel.setAcceleratedDrawing(false, false);
        killedAcceleratedRenderingForSleep = true;
        rootPanel.waitForRenderEngineCleanup();
      }
    }
  }

  boolean getShowRatings()
  {
    return getBoolean(prefs + SHOW_RATINGS, false);
  }

  public void run()
  {
    if (Sage.isHeadless() && uiClientName.equals(Seeker.LOCAL_PROCESS_CLIENT)) return;
    // Don't do this in init or the MMC won't have loaded yet and we can't detect the IR hardware
    loadIRButtonMappings();
    loadUEIRMappings();

    Pooler.execute(vf, "VideoFrame-" + getLocalUIClientName());


    // make sure we're using a valid security profile
    if (!Permissions.isValidSecurityProfile(getActiveSecurityProfile()))
    {
      if (Sage.DBG) System.out.println("ERROR Invalid security profile \"" + getActiveSecurityProfile() + "\" was being used; set it to the default profile instead of: " +
          Permissions.getDefaultSecurityProfile());
      setActiveSecurityProfile(Permissions.getDefaultSecurityProfile());
    }

    int startupType = getInt(prefs + STARTUP_TYPE, 0);
    if (!windowless)
    {
      GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().
          getScreenDevices();
      if ((Sage.WINDOWS_OS || Sage.MAC_OS_X) && screens.length > 0)
      {
        // NOTE: Check for 24/32-bit color depth. There's lots of problems that can occur if the
        // user doesn't have their system setup that way. This warrants a real popup since
        // they need to change the system at this point.
        if (getBoolean("warn_on_non_32bit_color_depth", true) &&
            screens[0].getDisplayMode().getBitDepth() < 24)
        {
          MySwingUtils.showWrappedMessageDialog(Sage.rez("Incorrect_Color_Depth"), Sage.rez("Error"),
              JOptionPane.ERROR_MESSAGE);
        }
      }
      // Check to make sure the window appears on at least one monitor
      Point newPos = new Point(getInt(prefs + LAST_WIN_POSX, 150),
          getInt(prefs + LAST_WIN_POSY, 150));
      boolean foundScreen = UIUtils.isPointOnAScreen(newPos);
      if (!foundScreen)
      {
        newPos.x = 150;
        newPos.y = 150;
      }
      Dimension newSize = new Dimension(Math.max(getInt(prefs + LAST_WIN_WIDTH, 640), 320),
          Math.max(getInt(prefs + LAST_WIN_HEIGHT, 480), 240));
      win.setLocation(newPos);
      win.setSize(newSize);

      if (Sage.WINDOWS_OS)
      {
        // old startup key which is wrong because it should be per-user
        Sage.removeRegistryValue(Sage.HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
            "SageTV");
        if (startupType != NO_STARTUP)
        {
          File exeFile = new File(System.getProperty("user.dir"),
              Sage.isTrueClient() ? "SageTVClient.exe" : "SageTV.exe");
          if (exeFile.isFile())
          {
            Sage.writeStringValue(Sage.HKEY_CURRENT_USER, "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                Sage.isTrueClient() ? "SageTVClient" : "SageTV",
                    "\"" + exeFile.getAbsolutePath() + "\" -startup");
          }
        }
        else
        {
          Sage.removeRegistryValue(Sage.HKEY_CURRENT_USER, "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
              Sage.isTrueClient() ? "SageTVClient" : "SageTV");
        }
      }

      // We don't want to prevent full screen if it was in full screen last time even if this is startup. So then
      // it can start in the system tray and then be full screen when taken out of the system tray.
      if ((getBoolean(prefs + LAST_WIN_FS, false)) ||
          (startupType == STARTUP_FS && Sage.systemStartup))
        setFullScreen(true);
    }
    // 601 HERE?!?

    SageProperties theProps = uiProps == null ? Sage.getRawProperties() : uiProps;
    try
    {
      String originalSTV = null;
      String fileStr = theProps.get("STV", null);
      if (Sage.initialSTV != null)
      {
        originalSTV = theProps.get("STV", null);
        theProps.put("STV", fileStr = Sage.initialSTV);
      }
      else if (theProps.get("STV", null) == null && theProps.get("modules", null) == null &&
          theProps.get(Wizard.WIZARD_KEY + "/" + Wizard.WIDGET_DB_FILE, null) != null)
      {
        theProps.put("STV", fileStr = theProps.get(Wizard.WIZARD_KEY + "/" + Wizard.WIDGET_DB_FILE, null));
      }
      // Don't reset MVP/PS UIs on upgrade of a server or they'll keep getting reset every time they connect
      // until the server is restarted!
      if ((fileStr == null || (Sage.getBoolean("wizard/revert_stv_on_upgrade", false) && ((SageTV.upgrade &&
          getUIClientType() != UIClient.REMOTE_UI) || (getUIClientType() == UIClient.REMOTE_UI && !SAGE.equals(get("ui/last_version", "")))))))
      {
        fileStr = new File(System.getProperty("user.dir"),
            "STVs" + File.separatorChar + ("SageTV7" + File.separatorChar + "SageTV7.xml")).toString();
        theProps.put("STV", fileStr);
      }
      if (getUIClientType() == UIClient.REMOTE_UI)
      {
        put("ui/last_version", SAGE);
      }

      // Check for remote MiniClient login that requires the Login STV
      if (getUIClientType() == UIClient.REMOTE_UI && rootPanel.getRenderEngine() instanceof MiniClientSageRenderer &&
          ((MiniClientSageRenderer) rootPanel.getRenderEngine()).connectionRequiresAuthentication())
      {
        originalSTV = theProps.get("STV", originalSTV);
        fileStr = new File(System.getProperty("user.dir"),
            "STVs" + File.separatorChar + ("SageTV7" + File.separatorChar + "SecureLogin.xml")).toString();
        theProps.put("STV", theProps.get("secure_remote_login_stv7", fileStr.toString()));
      }
      if (Sage.DBG) System.out.println("UIMgr loading UI from: " + theProps.get("STV", null));
      moduleGroup = ModuleManager.loadModuleGroup(theProps.getAllPrefs());

      if (Sage.DBG) System.out.println("UIMgr done loading UI from: " + theProps.get("STV", null));

      // We don't want double-clicking of files to change the STV we load by default
      if (originalSTV != null)
        theProps.put("STV", originalSTV);

    }
    catch (Exception sx)
    {
      // 601 what to do?
      System.out.println("ERROR loading STV of:" + sx);
      sx.printStackTrace();

      // The STV load failed, so revert to the default STV property and try to load it again
      String fileStr;
      fileStr = new File(System.getProperty("user.dir"),
          "STVs" + File.separatorChar + ("SageTV7" + File.separatorChar + "SageTV7.xml")).toString();
      theProps.put("STV", fileStr);
      try
      {
        moduleGroup = ModuleManager.loadModuleGroup(theProps.getAllPrefs());
      }
      catch (SageException sx1)
      {
        System.out.println("ERROR: Cannot find default STV file to load:" + fileStr);
      }
    }
    //        }
    // Now handle any STVI plugins that are configured
    updateSTVVerInfo();
    loadSTVImports();
    updateCapabilities();
    if (Sage.client && SageTV.neddy != null && SageTV.neddy.isClientConnected()) {
      if (Sage.DBG) System.out.println("Sending client capabilities to the server (run)");
      NetworkClient.getSN().sendCapabilties(this, capabilities.entrySet());
    }
    Catbert.processUISpecificHook("ApplicationStarted", null, this, false);
    updateXBMCCompatible();
    if (Sage.client && !SageTV.neddy.isClientConnected())
    {
      // This'll bring up its own connection failure UI so don't do anything here.
    }
    else if (currUI == null) // appstarted hook can load a menu
    {
      // Check for a Main Widget Menu
      boolean didMenu = false;
      Widget[] widgets = moduleGroup.getWidgets(Widget.MENU);
      for (int i = 0; i < widgets.length; i++)
      {
        if (MAIN_MENU.equals(widgets[i].getName()))
        {
          if (widgets[i].contents().length == 0)
          {
            // skip it, its an empty menu
            continue;
          }
          didMenu = true;
          PseudoMenu mm = new PseudoMenu(this, widgets[i]);
          advanceUI(mm);
          break;
        }
      }
      if (!didMenu)
      {
        if (EPG.getInstance().getDataSources().length == 0 && !Sage.client)
          advanceUI(UNITIALIZED_MAIN_MENU);
        else
          advanceUI(MAIN_MENU);
      }
    }

    if (getUIClientType() == LOCAL)
      Sage.hideSplash();

    router.startRouter();

    if (!windowless)
    {
      if (Sage.WINDOWS_OS || !getBoolean("hide_java_ui_window", false))
      {
        if (Sage.systemStartup && startupType == STARTUP_SLEEP)
        {
          isTaskbar = true;
          vf.sleep();
          Sage.addTaskbarIcon0(Sage.mainHwnd);
        }
        else
        {
          if (fsExMode && fsWin != null)
          {
            fsWin.setVisible(true);
            fsWin.toFront();
          }
          else
          {
            win.setVisible(true);
            win.toFront();
          }
          requestDelayedFocus();
        }
      }
      else
        win.getContentPane().getLayout().layoutContainer(win);
    }
    else
    {
      Dimension scrSize = getScreenSize();
      fakePanel.setSize(scrSize);
      fakePanel.getLayout().layoutContainer(fakePanel);
    }
    Sage.gc(true);
    // NOTE(codefu): This is used locally for determining the state of the box.
    setCapability("AcceptingAPICalls", "true");
  }

  private Widget[] menuWidgetCache;
  private long menuWidgetLastCached;
  PseudoMenu getUI(String uiName)
  {
    // Check the DB for a Menu with this name
    Widget[] menus;
    if (menuWidgetCache == null || (moduleGroup.lastModified() > menuWidgetLastCached))
    {
      menuWidgetLastCached = moduleGroup.lastModified();
      menus = menuWidgetCache = moduleGroup.getWidgets(Widget.MENU);
    }
    else
      menus = menuWidgetCache;
    for (int i = 0; i < menus.length; i++)
    {
      if (menus[i].getName().equals(uiName))
      {
        return getUI(menus[i]);
      }
    }
    Widget newMenuWidg = moduleGroup.addWidget(Widget.MENU);
    // 601 newMenuWidg.setName(uiName);
    WidgetFidget.setName(newMenuWidg, uiName);
    return getUI(newMenuWidg);
  }

  private long uiWidgMapLastCached;

  PseudoMenu getUI(Widget uiWidg)
  {
    if (moduleGroup.lastModified() > uiWidgMapLastCached)
    {
      uiWidgMap.clear();
      uiWidgMapLastCached = moduleGroup.lastModified();
    }
    LinkedHashMap<Map<String, Object>, PseudoMenu> hashie = uiWidgMap.get(uiWidg);
    if (hashie == null)
    {
      uiWidgMap.put(uiWidg, hashie =
          new LinkedHashMap<Map<String, Object>, PseudoMenu>(16, 0.75f, true)
          {
            private static final int STATIC_CONTEXT_MENU_MAP_SIZE = 5;
            protected boolean removeEldestEntry(Map.Entry<Map<String, Object>, PseudoMenu> ent)
            {
              return size() >= STATIC_CONTEXT_MENU_MAP_SIZE;
            }
          });
    }
    Map<String, Object> statty = new HashMap<String, Object>();
    statty.putAll(staticContext);
    PseudoMenu rv = hashie.get(statty);
    if (rv == null)
    {
      rv = new PseudoMenu(this, uiWidg);
      hashie.put(statty, rv);
    }
    else
      rv.xferStaticContext();
    return rv;
  }

  // This also clears all caches
  void fullyRefreshCurrUI()
  {
    uiHistory.clear();
    uiHistoryIndex = 0;
    uiWidgMap.clear();
    menuWidgetCache = null;
    hookCache = null;
    PseudoMenu currUI = getCurrUI();
    if (currUI != null)
    {
      if (currUI.getUI().getRelatedContext() != null && currUI.getUI().getRelatedContext().getParent() != null)
        staticContext.putAll(currUI.getUI().getRelatedContext().getParent().getMap());
      advanceUI(new PseudoMenu(this, currUI.getBlueprint()));
    }
  }

  public void freshStartup()
  {
    uiHistory.clear();
    uiHistoryIndex = 0;
    uiWidgMap.clear();
    menuWidgetCache = null;
    hookCache = null;
    advanceUI(MAIN_MENU);
  }

  public void clearMenuCache()
  {
    uiWidgMap.clear();
    uiHistory.clear();
    uiHistoryIndex = 0;
  }

  public void freshStartup(File moduleFilename)
  {
    if (moduleGroup != null)
      Catbert.processUISpecificHook("ApplicationExiting", null, this, false);
    try
    {
      SageProperties theProps = uiProps == null ? Sage.getRawProperties() : uiProps;
      if (moduleFilename == null)
      {
        moduleGroup = ModuleManager.newModuleGroup();
      }
      else
      {
        theProps.put("STV", moduleFilename.toString());
        if (Sage.DBG) System.out.println("UIMgr loading UI from: " + moduleFilename);
        moduleGroup = ModuleManager.loadModuleGroup(theProps.getAllPrefs());
        if (Sage.DBG) System.out.println("UIMgr done loading UI from: " + theProps.get("STV", null));
      }
    }
    catch (SageException sx)
    {
      System.out.println("ERROR loading STV of:" + sx);
      // 601 what to do?
      sx.printStackTrace();
    }
    strImageCacheMap.clear();
    uiHistory.clear();
    uiHistoryIndex = 0;
    uiWidgMap.clear();
    menuWidgetCache = null;
    hookCache = null;
    if (currUI != null)
    {
      currUI.terminate(false);
      if (currUI.getUI() != null)
        basePanel.remove(currUI.getUI());
      router.setHandler(null);
      currUI = null;
    }
    // Now handle any STVI plugins that are configured
    updateSTVVerInfo();
    loadSTVImports();
    updateCapabilities();
    if (Sage.client && SageTV.neddy != null && SageTV.neddy.isClientConnected()) {
      if (Sage.DBG) System.out.println("Sending client capabilities to the server (fresh)");
      NetworkClient.getSN().sendCapabilties(this, capabilities.entrySet());
    }
    Catbert.processUISpecificHook("ApplicationStarted", null, this, false);
    updateXBMCCompatible();
    advanceUI(MAIN_MENU);
    savePrefs();

    STVEditor study = getStudio();
    if (study != null)
      study.notifyOfExternalSTVChange();
  }

  // Loads any STVIs enabled for this STV into the currently loaded STV
  private void loadSTVImports()
  {
    pluginImportsActive = false;
    List<File> imgFoldList = new ArrayList<File>();
    imgFoldList.add(new File(getModuleGroup().defaultModule.description()).getParentFile());
    String[] imports = CorePluginManager.getInstance().getEnabledSTVImports(this);
    if (imports.length > 0)
    {
      if (Sage.DBG) System.out.println("Importing all currently enabled STVIs into the loaded STV...");
      pluginImportsActive = true;
      long oldModTime = getModuleGroup().defaultModule.lastModified();
      for (int i = 0; i < imports.length; i++)
      {
        if (Sage.DBG) System.out.println("Processing STV import: " + imports[i]);
        try
        {
          File stviFile = new File(imports[i]);
          getModuleGroup().importXML(stviFile, this);
          File parentFile = stviFile.getParentFile();
          if (!imgFoldList.contains(parentFile))
            imgFoldList.add(parentFile);
        }
        catch (Exception e)
        {
          if (Sage.DBG) System.out.println("ERROR Could not process STV Import properly: " + imports[i] + " cause: " + e);
        }
      }
      getModuleGroup().defaultModule.forceLastModified(oldModTime);
      if (Sage.DBG) System.out.println("Done importing all currently enabled STVIs into the loaded STV");
    }
    imgSearchRoots = imgFoldList.toArray(new File[0]);
  }

  public File[] getImageSearchRoots()
  {
    return imgSearchRoots;
  }

  public Widget[] getUIHistoryWidgets()
  {

    synchronized (uiHistory)
    {
      Widget[] rv = new Widget[uiHistory.size()];
      for (int i = 0; i < uiHistory.size(); i++)
      {
        rv[i] = uiHistory.get(i).getBlueprint();
      }
      return rv;
    }
  }

  public Widget[] getUIBackHistoryWidgets()
  {
    synchronized (uiHistory)
    {
      Widget[] rv = new Widget[Math.min(uiHistory.size(), uiHistoryIndex)];
      for (int i = 0; i < uiHistoryIndex && i < uiHistory.size(); i++)
      {
        rv[i] = uiHistory.get(i).getBlueprint();
      }
      return rv;
    }
  }

  void advanceUI(PseudoMenu newUI)
  {
    if (performingTermination) return;
    if (!uiHistory.isEmpty() && uiHistory.get(uiHistoryIndex) == newUI)
    {
      // Refreshes don't get added to history
      setUI(newUI, true);
      return;
    }

    // Clear the forward history
    while (uiHistoryIndex + 1 < uiHistory.size())
      uiHistory.remove(uiHistoryIndex + 1);

    if (Boolean.TRUE.equals(performingActivation.get()))
    {
      uiHistory.remove(uiHistory.size() - 1);
    }
    uiHistory.add(newUI);
    uiHistoryIndex = Math.min(uiHistoryDepth, uiHistory.size()) - 1;
    while (uiHistory.size() > uiHistoryDepth)
      uiHistory.remove(0);
    setUI(newUI, false);
  }

  public void advanceUI(String uiName)
  {
    advanceUI(getUI(uiName));
  }

  public void advanceUI(Widget uiName)
  {
    advanceUI(getUI(uiName));
  }

  public boolean backupUI()
  {
    if (performingTermination) return false;
    if (canBackupUI())
    {
      uiHistoryIndex--;
      setUI(uiHistory.get(uiHistoryIndex), true);
      return true;
    }
    else if (currUI != null && !MAIN_MENU.equals(currUI.getBlueprint().getName()))
    {
      uiHistory.clear();
      uiHistoryIndex = 0;
      advanceUI(MAIN_MENU);
      return false;
    }
    else
      return false;
  }

  public boolean canBackupUI()
  {
    return (uiHistory.size() > 0 && uiHistoryIndex > 0);
  }

  public boolean forwardUI()
  {
    if (performingTermination) return false;
    if (uiHistoryIndex < uiHistory.size() - 1)
    {
      uiHistoryIndex++;
      setUI(uiHistory.get(uiHistoryIndex), true);
      return true;
    }
    else
      return false;
  }

  private boolean processingWatchTVRequest = false;
  private boolean isOKToViewAiring(Airing a, int searchLevel)
  {
    // searchLevel - 0 (ignore anything violating PC or possible spoilers)
    // searchLevel - 1 (ignore possible spoilers)
    // searchLevel - 2 (ignore nothing)
    if (searchLevel >= 2)
      return true;
    if (searchLevel == 0 && vf.doesAiringRequirePC(a))
    {
      if (Sage.DBG) System.out.println("Skip airing due to PC restrictions: " + a);
      return false;
    }
    boolean spoiler = BigBrother.isPossibleSpoiler(a);
    if (spoiler && Sage.DBG)
    {
      System.out.println("Skip airing due to possible spoiler: " + a);
    }
    return !spoiler;
  }

  public void watchTV(boolean forceLive)
  {
    try
    {
      if (processingWatchTVRequest) return;
      processingWatchTVRequest = true;
      if (!vf.isLoadingOrLoadedFile() || forceLive)
      {
        if (Sage.DBG) System.out.println("UIManager is finding a default TV program to watch...");
        // Prefer to default watch something that doesn't require parental controls checking.
        // But if we exhaust all options this way; then go back through again with allowing PC restricted content (it'll still prompt before viewing)

        // If we have more encoders available; then we can check if there's other airings
        // to view that aren't being currently recorded.
        int numEncoders = MMC.getInstance().getConfiguredInputs().length;
        int lastStationID = vf.getBestLastStationID();
        MediaFile[] currFile = SeekerSelector.getInstance().getCurrRecordFiles();
        if (Sage.DBG) System.out.println("#CurrRecs=" + currFile.length + " #Encoders=" + numEncoders);
        for (int z = VideoFrame.getEnablePC() ? 0 : 1; z < 3; z++)
        {
          if (currFile.length < numEncoders)
          {
            // First try the last viewed station
            if (getBoolean("live_playback_is_default", false))
            {
              // Find the preferred capture device and use that, tuned to the last channel
              CaptureDevice cdev = mmc.getPreferredCaptureDevice();
              if (cdev != null)
              {
                CaptureDeviceInput cdi = cdev.getDefaultInput();
                if (cdi != null)
                {
                  long pauseBuffSize = Sage.getLong("default_pause_buffer_size", 16*1024*1024);
                  boolean trueLive = cdev.isCaptureFeatureSupported(MMC.LIVE_PREVIEW_MASK) && !Sage.isTrueClient();
                  if (trueLive || cdev.canEncode())
                  {
                    MediaFile liveFile = trueLive ? MediaFile.getLiveMediaFileForInput(cdi) :
                      MediaFile.getLiveBufferedMediaFileForInput(cdi);
                    if (!trueLive)
                    {
                      liveFile.setStreamBufferSize(pauseBuffSize);
                    }
                    if (vf.watch(liveFile) == VideoFrame.WATCH_OK)
                      return;
                  }
                }
              }
            }
            else
            {
              // Check through the recently viewed channel list and find something there
              Wizard wiz = Wizard.getInstance();
              Airing[] airs;
              if (lastStationID != 0)
              {
                if (Sage.DBG) System.out.println("Checking for current airing on last viewed channel...");
                airs = wiz.getAirings(lastStationID, Sage.time(), Sage.time() + 1, false);
                if (airs.length > 0 && isOKToViewAiring(airs[0], z))
                {
                  int rv = vf.watch(airs[0], forceLive);
                  if (rv == VideoFrame.WATCH_OK || VideoFrame.isGlobalWatchError(rv))
                    return;
                }
              }
              if (Sage.DBG) System.out.println("Checking for current airing on last viewed channels...");
            }
          }

          if (currFile.length > 0)
          {
            // This ends up calling this again, but there'll be a file at that point
            if (Sage.DBG) System.out.println("Checking for last viewed channel matching a current recording...");
            for (int i = 0; i < currFile.length; i++)
            {
              if (currFile[i].getContentAiring().getStationID() == lastStationID &&
                  vf.hasMediaPlayer(currFile[i]) && isOKToViewAiring(currFile[i].getContentAiring(), z))
              {
                vf.watch(currFile[i].getContentAiring(), forceLive);
                return;
              }
            }
            if (Sage.DBG) System.out.println("Checking for a current recording...");
            for (int i = 0; i < currFile.length; i++)
            {
              if (vf.hasMediaPlayer(currFile[i]) &&
                  isOKToViewAiring(currFile[i].getContentAiring(), z))
              {
                vf.watch(currFile[i].getContentAiring(), forceLive);
                return;
              }
            }
          }

          if (currFile.length < numEncoders && !getBoolean("live_playback_is_default", false))
          {
            // Check through the recently viewed channel list and find something there
            Wizard wiz = Wizard.getInstance();
            Airing[] airs;
            if (Sage.DBG) System.out.println("Checking for current airing on last viewed channels...");
            String[] recentStationIDs = Sage.getRawProperties().getMRUList("recent_channels", 10);
            for (int i = 0; recentStationIDs != null && i < recentStationIDs.length; i++)
            {
              try
              {
                int id = Integer.parseInt(recentStationIDs[i]);
                airs = wiz.getAirings(id, Sage.time(), Sage.time() + 1, false);
                if (airs.length > 0 && isOKToViewAiring(airs[0], z))
                {
                  int rv = vf.watch(airs[0], forceLive);
                  if (rv == VideoFrame.WATCH_OK || VideoFrame.isGlobalWatchError(rv))
                    return;
                }
              }
              catch (NumberFormatException nfe){}
            }
            if (Sage.DBG) System.out.println("Checking for any airing on channels...");
            // We need to find something default to watch, goto the first EPG listing that exists
            Channel[] currChans = wiz.getChannels();
            for (int j = 0; j < currChans.length; j++)
            {
              if (currChans[j].isViewable())
              {
                // See if there's an airing on this channel
                airs = wiz.getAirings(currChans[j].stationID, Sage.time(), Sage.time() + 1,
                    false);
                if (airs.length > 0 && isOKToViewAiring(airs[0], z))
                {
                  int rv = vf.watch(airs[0], forceLive);
                  if (rv == VideoFrame.WATCH_OK || VideoFrame.isGlobalWatchError(rv))
                    return;
                }
              }
            }
          }
        }
        return;
      }
    }
    finally
    {
      processingWatchTVRequest = false;
    }
  }

  public static class WaveFile
  {
    private javax.sound.sampled.Clip m_clip;
    private javax.sound.sampled.AudioInputStream m_stream;

    /** constructor */
    public WaveFile(String path)
    {
      try
      {
        File file=new File(path);
        javax.sound.sampled.AudioFileFormat audioFileFormat=javax.sound.sampled.AudioSystem.getAudioFileFormat(file);

        m_stream=javax.sound.sampled.AudioSystem.getAudioInputStream(file);
        javax.sound.sampled.AudioFormat format=m_stream.getFormat();
        javax.sound.sampled.DataLine.Info info=new javax.sound.sampled.DataLine.Info(javax.sound.sampled.Clip.class,format,((int)m_stream.getFrameLength()*format.getFrameSize()));
        m_clip=(javax.sound.sampled.Clip)javax.sound.sampled.AudioSystem.getLine(info);
        m_clip.open(m_stream);
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
    }

    /** play the clip synchronously */
    void play()
    {
      if (m_clip == null) return;
      try
      {
        m_clip.setFramePosition(0);
        m_clip.start();
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
    }
  }

  private int clipPlayCount = 0;
  public void playSound(String soundName)
  {
    SoftReference<WaveFile> wr = audioClipCache.get(soundName);
    WaveFile audio1 = wr == null ? null : wr.get();
    if (audio1 == null)
    {
      try
      {
        File soundFile = new File(soundName);
        if (!soundFile.isFile())
        {
          File widgFile = null;
          if (getModuleGroup() != null && getModuleGroup().defaultModule != null)
            widgFile = new File(getModuleGroup().defaultModule.description());
          if (widgFile != null)
          {
            widgFile = widgFile.getParentFile();
            soundFile = new File(widgFile, soundName);
          }
        }
        audio1 = new WaveFile(soundFile.getAbsolutePath());
        audioClipCache.put(soundName, new SoftReference<WaveFile>(audio1));
      }
      catch (Exception e)
      {
        System.out.println("ERROR loading audio clip of:" + e);
        return;
      }
    }
    audio1.play();
    clipPlayCount++;
  }

  public int getClipPlayCount()
  { return clipPlayCount; }

  public Widget getMenuTransitionSource()
  {
    return menuXtnSrc;
  }

  public Widget getMenuTransitionTarget()
  {
    return menuXtnTarget;
  }

  public PseudoMenu getCurrUI()
  { return currUI; }

  private int uiMgrLockCount = 0;
  private final Object uiMgrLockCountSync = new Object();
  private void setUI(PseudoMenu newUI, final boolean definitelyARedo)
  {
    if (debugger != null) debugger.traceMenu(newUI.getBlueprint());
    if (Sage.DBG) System.out.println("setUI(" + newUI + ") histIdx=" + uiHistoryIndex + " uiHistory=" + uiHistory + " redo=" + definitelyARedo +
        " performingActivation=" + performingActivation.get());
    // The tree lock synchronizes with the active renderer
    if (!Boolean.TRUE.equals(performingActivation.get()))
    {
      while (true)
      {
        synchronized (uiMgrLockCountSync)
        {
          while (uiMgrLockCount > 0 && !Thread.holdsLock(this))
          {
            try{uiMgrLockCountSync.wait(50);}catch (Exception e){}
          }
        }
        getLock(true, null);
        synchronized (uiMgrLockCountSync)
        {
          if (uiMgrLockCount == 0 || Thread.holdsLock(this))
            break;
          else
            clearLock();
        }
      }
    }

    // NOTE: Narflex - 05/02/2012 - Previously the setUI method itself was marked as synchronized. The problem with that is there's various
    // places in the code that call advanceUI/backupUI that do NOT have the UI lock when making those calls...but some places that do. This
    // was then resulting in a deadlock where one thread had the UI lock, then another came into this method call and grabbed the
    // UIManager lock and then waited for the UI lock...then the thread with the UI lock came into this method and was blocked on the
    // UIManager lock. Changing all of the calls into here to try to acquire the UI lock before making those calls is a lot of changes.
    // So we've changed it so that they always get the UI lock before locking UIManager. This is the only method that locks on UIManager, so
    // this change should be completely safe. The case when performingActivation is true already has both locks at this point, so that
    // won't cause any hangups.
    // NOTE: Narflex - 05/08/2012 - Hah! This was not safe. Apparently in PseudoMenu we release the UI lock during termination to let
    // the closing effects run and then reacquire it. We do this while we have the UIManager lock. So another thread can come in and
    // steal the UI lock and then block on this synchronized lock for the UIManager. Sooo...what we need to do now is prevent a thread
    // from getting the UI lock if another thread already has the UIManager lock. The uiMgrLockCount[sync] stuff above is what protects against
    // that...it does look ugly, but I'm fairly sure the logic is sound around it.
    try
    {
      synchronized (this)
      {
        uiMgrLockCount++;
        if (currUI != null)
        {
          menuXtnTarget = newUI.getBlueprint();
          performingTermination = true;
          // On reloads from the system tray we could be going back to the same menu we have loaded; so don't mess up the
          // load/unload anims
          currUI.terminate(!Boolean.TRUE.equals(performingActivation.get()) && (currUI != newUI));
          performingTermination = false;
          menuXtnTarget = null;
          if (currUI.getUI() != null)
            basePanel.remove(currUI.getUI());
          router.setHandler(null);
        }
        if (!Boolean.TRUE.equals(performingActivation.get()))
          menuXtnSrc = (currUI != null) ? currUI.getBlueprint() : null;
        performingActivation.set(Boolean.TRUE);

        PseudoMenu oldUI = currUI;
        currUI = newUI;
        currUI.activate(definitelyARedo);
        if (performingActivation.get() == null) {
          System.out.println("VM ERROR: performingActivation is null which should not happen!");
        }
        if (!Boolean.TRUE.equals(performingActivation.get()))
        {
          // Another menu got activated on top of us so ignore what we did
          return;
        }
        performingActivation.set(Boolean.FALSE);

        router.setHandler(currUI.getEV());
        if (currUI.getUI() != null)
          basePanel.add(currUI.getUI());

        clearLock();
        repaintNextRegionChange = true;
        if (currUI != null)
        {
          router.invokeLater(new Runnable()
          {
            public void run()
            {
              currUI.postactivate(definitelyARedo);
            }
          });
        }
        if (!Sage.getBoolean("ui/disable_focus_gain_on_menu_change", false))
          requestDelayedFocus();
        router.ssTimerRestart();
      }
    }
    finally
    {
      synchronized (uiMgrLockCountSync)
      {
        uiMgrLockCount--;
        uiMgrLockCountSync.notify();
      }
    }
  }

  private void requestDelayedFocus()
  {
    router.invokeLater(new Runnable()
      {
        public void run()
        {
          rootPanel.requestFocus();
        }
      });
  }

  public boolean isTV()
  {
    PseudoMenu testUI = currUI;
    return (testUI != null) && testUI.isTV();
  }

  public boolean isMainTV()
  {
    return isTV() && rootPanel.didLastRenderHaveFullScreenVideo();
  }

  public void goodbye()
  {
    if (!alive) return;
    alive = false;
    arMustGetLockNext = false;
    if (Sage.DBG) System.out.println("Killing UIMgr " + this);
    allUIs.remove(this);
    if (uiTimer != null)
    {
      uiTimer.cancel();
      if (Sage.DBG && uiTimer != null) System.out.println("Killed UI Timers");
    }
    if (vf != null)
    {
      vf.goodbye();
      if (Sage.DBG) System.out.println("Killed VideoFrame");
    }
    if (moduleGroup != null)
      Catbert.processUISpecificHook("ApplicationExiting", null, this, false);
    if (router != null) router.kill();
    if (ENABLE_STUDIO)
    {
      STVEditor study = getStudio();
      if (study != null)
      {
        study.kill();
        if (Sage.DBG) System.out.println("Killed Studio");
      }
    }
    if (rootPanel != null)
    {
      rootPanel.kill();
      if (Sage.DBG) System.out.println("Killed RootPanel");
    }
    if (isTaskbar && uiClientName.equals(Seeker.LOCAL_PROCESS_CLIENT))
      Sage.removeTaskbarIcon0(Sage.mainHwnd);
    if (currUI != null)
      currUI.terminate(false);
    if (!windowless && win != null)
    {
      if (!win.isFullScreen() && win.getExtendedState() != Frame.MAXIMIZED_BOTH)
      {
        putInt(prefs + LAST_WIN_POSX, win.getX());
        putInt(prefs + LAST_WIN_POSY, win.getY());
        putInt(prefs + LAST_WIN_WIDTH, win.getWidth());
        putInt(prefs + LAST_WIN_HEIGHT, win.getHeight());
      }
      putBoolean(prefs + LAST_WIN_FS, isFullScreen());
      win.dispose();
      if (fsWin != null)
        fsWin.dispose();
    }
    if (moduleGroup != null)
      moduleGroup.dispose();

    savePrefs();

    MetaImage.notifyOfDeadUIManager(this);

    if (Sage.DBG) System.out.println("Disposed Window");
  }

  void trueValidate()
  {
    if (rootPanel != null && basePanel != null)
    {
      synchronized (rootPanel.getTreeLock())
      {
        basePanel.appendToDirty(true);
      }
    }
  }

  public EventRouter getRouter()
  { return router; }

  public SpecialWindow getGlobalFrame()
  { return win; }

  public SpecialWindow getGlobalFSFrame()
  { return fsWin; }

  public Catbert.ExecutionPosition addPopup(ZPseudoComp magicPopupComp, Catbert.Context resumeContext)
  {
    PseudoMenu theUI = currUI;
    if (theUI != null)
    {
      return theUI.addPopup(magicPopupComp, resumeContext);
    }
    return null;
  }

  public void removePopup()
  {
    PseudoMenu theUI = currUI;
    if (theUI != null)
    {
      theUI.removePopup();
    }
  }

  public boolean removePopup(String widgName, boolean waitForClose)
  {
    PseudoMenu theUI = currUI;
    if (theUI != null)
    {
      return theUI.removePopup(widgName, waitForClose);
    }
    return true;
  }

  public void doUserEvent2(int evtID, Object payload)
  {
    if (Boolean.TRUE.equals(performingActivation.get()) && (evtID == UserEvent.BACK || evtID == UserEvent.FORWARD))
    {
      // These can't be done later because we have to override this menu change
      if (currUI != null)
      {
        UserEvent ue = new UserEvent(router.getEventCreationTime(), evtID, -1);
        ue.setPayloads(payload);
        currUI.action(ue);
      }
    }
    else if (evtID >= UserEvent.MIN_EVT_ID && evtID <= UserEvent.MAX_EVT_ID)
    {
      UserEvent ue = new UserEvent(router.getEventCreationTime(), evtID, -1);
      ue.setPayloads(payload);
      ue.setDiscardable(false);
      router.submitUserEvent(ue);
    }
  }

  public void doUserEvent(int evtID)
  {
    if (evtID >= UserEvent.MIN_EVT_ID && evtID <= UserEvent.MAX_EVT_ID)
      router.submitUserEvent(new UserEvent(router.getEventCreationTime(), evtID, -1));
  }

  public void displayChange(int width, int height)
  {
    if (Sage.DBG) System.out.println("Received WM_DISPLAYCHANGE event from the OS of " + width + "x" + height + "...check the renderer");
    if (rootPanel != null)
    {
      final int newWidth = width;
      final int newHeight = height;
      Pooler.execute(new Runnable()
      {
        public void run()
        {
          // Due to the async nature of how AWT responds to display change events, we must delay processing this
          // in order to get the correct new screen size.
          try{Thread.sleep(2000);}catch(Exception e){}
          SageRenderer renderEngine = rootPanel.getRenderEngine();
          if (renderEngine instanceof DirectX9SageRenderer)
          {
            ((DirectX9SageRenderer) renderEngine).checkForDisplayChange(newWidth, newHeight);
          }
        }
      });
    }
  }

  public boolean getReverseEPGChannels()
  { return getBoolean(prefs + REVERSE_EPG_CHANNELS, false); }

  public void setReverseEPGChannels(boolean x)
  { putBoolean(prefs + REVERSE_EPG_CHANNELS, x); }

  public boolean getScreenSaverOnSleep()
  { return screenSaverOnSleep; }

  public void setScreenSaverOnSleep(boolean x)
  { putBoolean(prefs + SCREEN_SAVER_ON_SLEEP, screenSaverOnSleep = x); }

  public HashMap<Integer, long[]> getUEToIRCodesMap()
  { return new HashMap<Integer, long[]>(ueToIRCodesMap); }

  public HashMap<Long, Integer> getIRCodeToUEMap()
  { return new HashMap<Long, Integer>(irCodeToUEMap); }

  public HashMap<Long, String> getIRCodeToNameMap()
  { return new HashMap<Long, String>(irCodeToNameMap); }

  public void setUEToIRCodesMap(HashMap<Integer, long[]> newMap)
  {
    ueToIRCodesMap = new HashMap<Integer, long[]>(newMap);
    for (Map.Entry<Integer, long[]> ent : ueToIRCodesMap.entrySet())
    {
      long[] codes = ent.getValue();
      StringBuilder codeStr = new StringBuilder();
      for (int i = 0; i < codes.length; i++) {
        codeStr.append(codes[i]);
        codeStr.append(",");
      }
      put(prefs + RC_MAPPINGS + '/' + UserEvent.getEvtName(ent.getKey()), codeStr.toString());
    }
  }

  public void setIRCodeToUEMap(HashMap<Long, Integer> newMap)
  {
    irCodeToUEMap = new HashMap<Long, Integer>(newMap);
  }

  public void addIRCodeForUE(long code, int ue)
  {
    Integer oldUE = irCodeToUEMap.get(code);
    if (oldUE != null)
    {
      removeIRCodeForUE(code, oldUE);
    }
    irCodeToUEMap.put(code, ue);
    long[] oldCodes = ueToIRCodesMap.get(ue);
    if (oldCodes == null)
    {
      oldCodes = new long[1];
      oldCodes[0] = code;
    }
    else
    {
      long[] tempCodes = oldCodes;
      oldCodes = new long[oldCodes.length + 1];
      System.arraycopy(tempCodes, 0, oldCodes, 0, tempCodes.length);
      oldCodes[oldCodes.length - 1] = code;
    }
    ueToIRCodesMap.put(ue, oldCodes);
    String codeStr = "";
    for (int i = 0; i < oldCodes.length; i++)
      codeStr += oldCodes[i] + ",";
    put(prefs + RC_MAPPINGS + '/' + UserEvent.getEvtName(ue), codeStr);
  }

  public void removeIRCodeForUE(long code, int ue)
  {
    irCodeToUEMap.remove(code);
    long[] oldCodes = ueToIRCodesMap.get(ue);
    if (oldCodes == null || oldCodes.length == 0)
      return;
    int oldIdx = -1;
    for (int i = 0; i < oldCodes.length; i++)
      if (oldCodes[i] == code)
      {
        oldIdx = i;
        break;
      }
    if (oldIdx == -1)
      return;
    if (oldCodes.length == 1)
    {
      ueToIRCodesMap.remove(ue);
      put(prefs + RC_MAPPINGS + '/' + UserEvent.getEvtName(ue), null);
      return;
    }
    long[] newCodes = new long[oldCodes.length - 1];
    System.arraycopy(oldCodes, 0, newCodes, 0, oldIdx);
    System.arraycopy(oldCodes, oldIdx + 1, newCodes, oldIdx, newCodes.length - oldIdx);
    ueToIRCodesMap.put(ue, newCodes);
    String codeStr = "";
    for (int i = 0; i < newCodes.length; i++)
      codeStr += newCodes[i] + ",";
    put(prefs + RC_MAPPINGS + '/' + UserEvent.getEvtName(ue), codeStr);
  }

  public void setIRCodeToNameMap(HashMap<Long, String> newMap)
  {
    irCodeToNameMap = new HashMap<Long, String>(newMap);
    for (Map.Entry<Long, String> ent : irCodeToNameMap.entrySet())
    {
      put(prefs + BUTTONS + '/' + ent.getKey(), ent.getValue());
    }
  }

  public void setNameForIRCode(long code, String name)
  {
    if (name == null)
      irCodeToNameMap.remove(code);
    else
      irCodeToNameMap.put(code, name);
    put(prefs + BUTTONS + '/' + code, name);
  }

  public void setEmbeddedBounds(float x, float y, float w, float h)
  {
    if ((embeddedBounds == null || embeddedBounds.x != x || embeddedBounds.y != y || embeddedBounds.width != w || embeddedBounds.height != h))
    {
      if (embeddedBounds == null)
        embeddedBounds = new Rectangle2D.Float();
      embeddedBounds.setRect(x, y, w, h);
      if (win != null)
      {
        win.getContentPane().invalidate();
        router.invokeLater(new Runnable()
        {
          public void run()
          {
            win.validate();
          }
        });
      }
    }
  }

  public Panel getEmbeddedPanel()
  {
    return embeddedPanel;
  }

  ZComp getCurrPseudoPopup()
  {
    PseudoMenu theUI = currUI;
    if (theUI != null)
      return theUI.getTopPopup();
    else
      return null;
  }

  void killPopupNextEvent()
  {
    popupDie = true;
  }

  void forcePopupResult(Object res)
  {
    popupResult = res;
  }

  public void updateTaskbarIcon()
  {
    if (isTaskbar)
    {
      MediaFile[] currRecs = SeekerSelector.getInstance().getCurrRecordFiles();
      String tipString = (currRecs.length > 0 ?
          (Sage.rez("SageTV") + "-" + Sage.rez("Recording") + " " + currRecs[0].getContentAiring().getShortString()) :
            Sage.rez("SageTV"));
      for (int i = 1; i < currRecs.length; i++)
        tipString += " & " + currRecs[i].getContentAiring().getShortString();
      if (tipString.length() > 63)
        tipString = tipString.substring(0, 63);
      Sage.updateTaskbarIcon0(Sage.mainHwnd, tipString);
    }
  }

  public boolean getDisposeWindows()
  { return Sage.getBoolean(DISPOSE_WINDOWS, false); }

  void hideWindow(Window hideMe)
  {
    if (getDisposeWindows())
      hideMe.dispose();
    else
      hideMe.setVisible(false);
  }

  public BufferedImage getImage(String imageName)
  {
    if (imageName == null || imageName.length() == 0)
      return ImageUtils.getNullImage();
    // check the cache first
    Object cacheObj = imageCacheMap.get(imageName);
    BufferedImage rv = null;
    if (cacheObj instanceof SoftReference)
      rv = ((SoftReference<BufferedImage>) cacheObj).get();
    else
      rv = (BufferedImage) cacheObj;
    if (rv != null)
      return rv;
    // resolve the name to the resource
    if (imageName.indexOf('.') == -1)
      imageName += ".gif";
    // check for the full path, or relative to the working path
    File widgFile = Wizard.getInstance().getWidgetDBFile();
    if (widgFile != null)
      widgFile = widgFile.getParentFile();
    if (new File(imageName).isFile())
      rv = ImageUtils.fullyLoadImage(new File(imageName));
    // check for a path relative to the location of the STV file
    else if (widgFile != null && new File(widgFile, imageName).isFile())
      rv = ImageUtils.fullyLoadImage(new File(widgFile, imageName));
    // check for an image with the same name as the folder where the STV file is
    else if (widgFile != null && new File(widgFile, new File(imageName).getName()).isFile())
      rv = ImageUtils.fullyLoadImage(
          new File(widgFile, new File(imageName).getName()));
    // check the working directory for a file with the same name as this image
    else if (new File(new File(imageName).getName()).isFile())
      rv = ImageUtils.fullyLoadImage(new File(new File(imageName).getName()));
    // could be ftp or http
    else
      rv = ImageUtils.fullyLoadImage(imageName);
    if (rv != ImageUtils.getNullImage())
    {
      imageCacheMap.put(imageName, new SoftReference<BufferedImage>(rv));
      if (Sage.DBG) System.out.println("UIManager cached new image " + imageName);
    }
    return rv;
  }

  void setImage(String imageName, BufferedImage image)
  {
    imageCacheMap.put(imageName, image);
  }

  private static HashMap<String, SoftReference<Font>> javaFontCacheMap =
      new HashMap<String, SoftReference<Font>>();
  private static HashMap<String, SoftReference<Font>> javaFontFileCacheMap =
      new HashMap<String, SoftReference<Font>>();

  public static Font getCachedJavaFont(String fontFaceProp, int fontStyle, int fontSize)
  {
    String fontKey = fontFaceProp + "|" + fontStyle + "|" + fontSize;
    SoftReference<Font> wr = javaFontCacheMap.get(fontKey);
    Font rv = wr == null ? null : wr.get();
    if (rv == null)
    {
      if (Sage.getBoolean("ui/load_fonts_from_filesystem", false))
      {
        SoftReference<Font> wr2 = javaFontFileCacheMap.get(fontFaceProp);
        Font baseFont = wr2 == null ? null : wr2.get();
        if (baseFont == null)
        {
          File fontFile = new File(fontFaceProp);
          if (fontFile.isFile())
          {
            InputStream fontStream = null;
            try
            {
              fontStream = new BufferedInputStream(new FileInputStream(fontFile));
              baseFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
              if (Sage.DBG) System.out.println("Loaded filesystem font: " + baseFont);
              javaFontFileCacheMap.put(fontFaceProp, new SoftReference<Font>(baseFont));
              rv = baseFont.deriveFont(fontStyle, fontSize);
              javaFontCacheMap.put(fontKey, new SoftReference<Font>(rv));
              return rv;
            }
            catch (Exception e)
            {
              System.out.println("ERROR loading font file " + fontFaceProp + " of:" + e);
            }
            finally
            {
              if (fontStream != null)
              {
                try{fontStream.close();}catch(Exception e){}
                fontStream = null;
              }
            }
          }
        }
        else
        {
          rv = baseFont.deriveFont(fontStyle, fontSize);
          javaFontCacheMap.put(fontKey, new SoftReference<Font>(rv));
          return rv;
        }
      }
      rv = new Font(fontFaceProp, fontStyle, fontSize);
      javaFontCacheMap.put(fontKey, new SoftReference<Font>(rv));
    }
    return rv;
  }

  private static HashMap<String, SoftReference<MetaFont>> metaFontCacheMap =
      new HashMap<String, SoftReference<MetaFont>>();

  public static MetaFont getCachedMetaFont(String fontFaceProp, int fontStyle, int fontSize, UIManager uiLoader/*for relative paths*/)
  {
    String fontKey = fontFaceProp + "|" + fontStyle + "|" + fontSize;
    SoftReference<MetaFont> wr = metaFontCacheMap.get(fontKey);
    MetaFont rv = (wr == null ? null : wr.get());
    if (rv == null)
    {
      synchronized (metaFontCacheMap)
      {
        // Check to make sure its not in the cache now since we've got the sync lock now
        wr = metaFontCacheMap.get(fontKey);
        rv = wr == null ? null : wr.get();
        if (rv != null)
          return rv;
        fontFaceProp = IOUtils.convertPlatformPathChars(fontFaceProp);
        if (uiLoader != null && fontFaceProp.toLowerCase().endsWith(".ttf"))
        {
          if (!new File(fontFaceProp).isFile())
            fontFaceProp = new File(new File(uiLoader.getModuleGroup().defaultModule.description()).getParentFile(), fontFaceProp).getAbsolutePath();
        }
        rv = MetaFont.getFont(fontFaceProp, fontStyle, fontSize);
        SoftReference<MetaFont> softy;
        metaFontCacheMap.put(fontKey, softy = new SoftReference<MetaFont>(rv));
        fontKey = IOUtils.convertPlatformPathChars(fontKey);
        // In case there's a difference between the two which the MetaFont constructor would coalesce
        metaFontCacheMap.put(fontKey, softy);
      }
    }
    return rv;
  }

  Cursor getCursor(String cursorName)
  {
    // check the cache first
    Cursor rv = cursorCacheMap.get(cursorName);
    if (rv != null)
      return rv;
    // resolve the name to the resource
    rv = ImageUtils.createCursor(cursorName, cursorName, 8, 8, false, false);
    cursorCacheMap.put(cursorName, rv);
    return rv;
  }

  void setCursor(String cursorName, Cursor curse)
  {
    cursorCacheMap.put(cursorName, curse);
  }

  ImageIcon getIcon(String iconName)
  {
    // check the cache first
    Object cacheObj = iconCacheMap.get(iconName);
    ImageIcon rv = null;
    if (cacheObj instanceof SoftReference)
      rv = ((SoftReference<ImageIcon>) cacheObj).get();
    else
      rv = (ImageIcon) cacheObj;
    if (rv != null)
      return rv;
    // resolve the name to the resource
    rv = new ImageIcon(getImage(iconName));
    iconCacheMap.put(iconName, new SoftReference<ImageIcon>(rv));
    return rv;
  }

  void setIcon(String iconName, ImageIcon ike)
  {
    iconCacheMap.put(iconName, ike);
  }

  private Tracer debugger;

  public void setTracer(Tracer t)
  {
    debugger = t;
  }

  public Tracer getTracer() { return debugger; }

  /*
   * 7/21/08 - Narflex - The flag 'arMustGetLockNext' is here to ensure that the ActiveRendering thread will be the next
   * thing to render after the completion of another UI operaiton. For example; a thread can get the UI lock and then do
   * some things to the UI and then call Refresh or something else that would trigger a redraw. Normally it would just
   * release the lock at the end and then the active rendering thread would notice the change and update the UI. BUT what
   * can go wrong is that something else can run inbetween the time that the lock is released and the time the active renderer
   * actually executes which could cause the UI state to get modified in an undesirable way. By setting this flag we indicate
   * that the next thread to receive the UI lock must be from the active renderer (marked by the parameter to getLock) which ensures
   * that the rendering will complete for the state we had the UI in when we released the lock. This is intended to solve problems
   * related to spawning animations from Forked threads.
   */
  public boolean lockPendingOnARRun()
  {
    return arMustGetLockNext;
  }

  // Returns true if the caller has the lock now
  public boolean getLock(boolean shouldWait, boolean[] acquiredLock)
  {
    return getLock(shouldWait, acquiredLock, false);
  }

  public boolean getLock(boolean shouldWait, boolean[] acquiredLock, boolean dontWaitIfDebug)
  {
    return getLock(shouldWait, acquiredLock, dontWaitIfDebug, false);
  }

  public boolean getLock(boolean shouldWait, boolean[] acquiredLock, boolean dontWaitIfDebug, boolean activeRenderCall)
  {
    Object keyer = Thread.currentThread();
    if (uiLockDebug && Sage.DBG) System.out.println("UIManager.getLock(" + keyer + ", wait=" + shouldWait + ", arMustGetLockNext=" + arMustGetLockNext + ")");
    synchronized (syncLock)
    {
      if (keyer.equals(locker))
      {
        if (uiLockDebug && Sage.DBG) System.out.println("UIManager.getLock - already has lock keyer=" + keyer);
        if (acquiredLock != null)
          acquiredLock[0] = false;
        return true;
      }
      if (arMustGetLockNext && Sage.time() - arMustGetLockNextSetTime > 10000)
        arMustGetLockNext = false;
      if ((locker != null ||
          (!activeRenderCall && arMustGetLockNext && alive) ||
          (mustGetLockNext != null && !arMustGetLockNext && mustGetLockNext != keyer && alive)) &&
          !shouldWait)
      {
        if (uiLockDebug && Sage.DBG) System.out.println("UIManager.getLock - lock already owned keyer=" + keyer + " locker=" + locker);
        if (acquiredLock != null)
          acquiredLock[0] = false;
        return false;
      }
      while (locker != null || (!activeRenderCall && arMustGetLockNext && alive) ||
          (mustGetLockNext != null && !arMustGetLockNext && mustGetLockNext != keyer && alive))
      {
        if (uiLockDebug && Sage.DBG) System.out.println("UIManager.getLock - waiting for lock to be freed keyer=" + keyer + " locker=" + locker);
        try
        {syncLock.wait(10);}catch(Exception e)
        {}
        if (dontWaitIfDebug && ignoreAllEvents)
        {
          if (acquiredLock != null)
            acquiredLock[0] = false;
          return false;
        }
        if (arMustGetLockNext && Sage.time() - arMustGetLockNextSetTime > 10000)
          arMustGetLockNext = false;
      }
      locker = keyer;
      //syncLock.notifyAll();
      if (uiLockDebug && Sage.DBG) System.out.println("UIManager.getLock - LOCK ACQUIRED keyer=" + keyer);
      if (acquiredLock != null)
        acquiredLock[0] = true;
      if (activeRenderCall && arMustGetLockNext)
        arMustGetLockNext = false;
      if (mustGetLockNext == keyer)
        mustGetLockNext = null;
      return true;
    }
  }

  public boolean clearLock()
  {
    return clearLock(false, false);
  }

  // Narflex: 11/29/12 - There's been a hole in our UI locking system for awhile where we need to release it in order
  // to let the ActiveRender thread run to finish up any closing effects on menu or popup termination. The trick we were using
  // there was to require the ActiveRender to run next..but that did not then prevent another thread from coming in and stealing
  // the lock while we were waiting on the AR. So now we also specify that we need to get it ourselves right after the AR does to
  // prevent someone else from stealing it. This is why we added callerMustAcquireLockAfterAR.
  public boolean clearLock(boolean setActiveRenderNext, boolean callerMustAcquireLockAfterAR)
  {
    if (Thread.currentThread().equals(locker))
    {
      if (setActiveRenderNext && rootPanel != null && rootPanel.isActiveRenderGoingToRun(false))
      {
        arMustGetLockNext = true;
        arMustGetLockNextSetTime = Sage.time();
      }
      if (callerMustAcquireLockAfterAR)
        mustGetLockNext = locker;
      if (uiLockDebug && Sage.DBG) System.out.println("UIManager.clearLock " + locker + " arMustGetLockNext=" + arMustGetLockNext + " setActiveRenderNext=" + setActiveRenderNext);
      locker = null;
      return true;
    }
    else
      return false;
  }

  public Object getUILockHolder() { return locker; }

  public ZRoot getRootPanel()
  { return rootPanel; }

  public int getOverscanOffsetX()
  { return overscanX; }

  public int getOverscanOffsetY()
  { return overscanY; }

  public float getOverscanScaleWidth()
  { return overscanWidth; }

  public float getOverscanScaleHeight()
  { return overscanHeight; }

  public void setOverscanOffsetX(int x)
  {
    if (x != overscanX)
      putInt("ui/" + UI_OVERSCAN_CORRECTION_OFFSET_X, overscanX = x);
  }

  public void setOverscanOffsetY(int x)
  {
    if (x != overscanY)
      putInt("ui/" + UI_OVERSCAN_CORRECTION_OFFSET_Y, overscanY = x);
  }

  public void setOverscanScaleWidth(float x)
  {
    if (x != overscanWidth)
      putFloat("ui/" + UI_OVERSCAN_CORRECTION_PERCT_WIDTH, overscanWidth = x);
  }

  public void setOverscanScaleHeight(float x)
  {
    if (x != overscanHeight)
      putFloat("ui/" + UI_OVERSCAN_CORRECTION_PERCT_HEIGHT, overscanHeight = x);
  }

  public static boolean shouldAntialiasFont(int fontSize)
  {
    return Sage.getBoolean("antialias_text", true);// && (fontSize > Sage.getInt("antialias_text_point_size_min", Sage.WINDOWS_OS ? 24 : 0));
  }

  public boolean isFullScreen()
  {
    if (windowless)
    {
      if (rootPanel.getRenderEngine() instanceof MiniClientSageRenderer)
      {
        MiniClientSageRenderer miniRender = (MiniClientSageRenderer)rootPanel.getRenderEngine();
        if (miniRender.isFullScreen())
          return true;
        Dimension maxSize = miniRender.getMaxClientResolution();
        if (maxSize != null)
        {
          return maxSize.width <= rootPanel.getRoot().getWidth() && maxSize.height <= rootPanel.getRoot().getHeight();
        }
      }
      return true;
    }
    else
    {
      if (fsWin != null)
        return fsMode;
      else
        return (win != null && win.isFullScreen());
    }
  }

  public void checkFSSizing()
  {
    // This is used on Windows when the resolution gets changed on us and we somehow then end up out of sync w/ the proper window size vs. render size
    // This should get taken care of automatically when we get the display change events, but for some reason it doesn't always happen properly
    if (!windowless && Sage.WINDOWS_OS && isFullScreen() && !isAsleep())
    {
      boolean doTheRefresh = false;
      if (fsWin != null && fsExMode && (fsWin.getWidth() != rootPanel.getWidth() || fsWin.getHeight() != rootPanel.getHeight()))
      {
        if (Sage.DBG) System.out.println("UIManager is updating the window size-1 of " + fsWin.getWidth() + "x" + fsWin.getHeight() +
            " because we're in FS Mode and the window size doesn't match our rendering size of " + rootPanel.getWidth() + "x" + rootPanel.getHeight());
        doTheRefresh = true;
      }
      else if (win != null && !fsExMode && (win.getWidth() != rootPanel.getWidth() || win.getHeight() != rootPanel.getHeight()))
      {
        if (Sage.DBG) System.out.println("UIManager is updating the window size-2 of " + win.getWidth() + "x" + win.getHeight() +
            " because we're in FS Mode and the window size doesn't match our rendering size of " + rootPanel.getWidth() + "x" + rootPanel.getHeight());
        doTheRefresh = true;
      }
      if (doTheRefresh)
      {
        if (win != null && !fsExMode && isFullScreen() && !isAsleep())
        {
          // Make sure the window size is correct
          Rectangle screenBounds = win.getCurrScreenBounds();
          if (screenBounds.width != win.getWidth() || screenBounds.height != win.getHeight())
          {
            if (Sage.DBG) System.out.println("UIManager is resizing window because it's size of " + win.getWidth() + "x" + win.getHeight() + " does not match the screen size of " + screenBounds);
            win.setBounds(screenBounds);
          }
        }
        rootPanel.invalidate();
        rootPanel.getParent().invalidate();
        EventQueue.invokeLater(new Runnable()
        {
          public void run()
          {
            SwingUtilities.getAncestorOfClass(Frame.class, rootPanel.getParent()).validate();
          }
        });
        trueValidate();
      }
    }
  }

  public boolean isFSEXMode() { return fsExMode; }

  private boolean fsMode;
  private boolean fsExMode;
  private SpecialWindow fsWin;

  public void setFullScreen(boolean x)
  {
    if (win != null)
    {
      fsMode = x;
      // IMPORTANT: We have to use a separate window for full screen when we're using exclusive mode on windows or it
      // messes everything up!!!
      if (Sage.WINDOWS_OS && getUIClientType() == UIClient.LOCAL && ((rootPanel.isAcceleratedDrawing() && !getBoolean("ui/disable_dx9_full_screen_ex", true)) ||
          fsExMode))
      {
        if (x)
        {
          if (fsWin == null)
          {
            fsWin = new SpecialWindow("SageTV", Sage.getInt("ui/window_title_style", Sage.VISTA_OS ? SageTVWindow.PLATFORM_TITLE_STYLE : 0));
            fsWin.setUIMgr(this);
            fsWin.setIconImage(ImageUtils.fullyLoadImage("images/tvicon.gif"));
            fsWin.setBackground(Color.black);
            fsWin.setDecorationState(getInt("ui/frame_decoration", 0));
            WindowAdapter focusAndClose = new WindowAdapter()
            {
              public void windowClosing(WindowEvent evt)
              {
                if (getBoolean("ignore_window_close", false))
                {
                }
                else if (getBoolean("sleep_on_close", false))
                  gotoSleep(true);
                else if (uiClientName.equals(Seeker.LOCAL_PROCESS_CLIENT))
                  SageTV.exit();
                else
                  goodbye();
              }
              public void windowGainedFocus(WindowEvent evt)
              {
                fsWin.toFront();
              }

              public void windowLostFocus(WindowEvent evt)
              {
                if (fsMode && !isAsleep() && getBoolean("disable_fse_on_focus_lost", true) && fsExMode)
                {
                  // Make sure the DX9Renderer is actually in FSE mode already so we don't falsely trigger this before its loaded
                  SageRenderer sr = rootPanel.getRenderEngine();
                  if (sr instanceof DirectX9SageRenderer)
                  {
                    if (!((DirectX9SageRenderer) sr).isFSELoaded())
                      return;
                  }
                  if (Sage.DBG) System.out.println("Switching back to non-FSE window due to focus loss...");
                  fsWin.setCursor(customCursor);
                  fsWin.getContentPane().remove(rootPanel);
                  fsWin.getContentPane().remove(vf);
                  rootPanel.resetHWND();
                  win.getContentPane().add(rootPanel);
                  win.getContentPane().add(vf);
                  fsExMode = false;
                  win.setVisible(true);
                  win.setExtendedState(Frame.ICONIFIED);
                  win.setFullScreen(true);
                  // This is for disabling full screen exclusive mode when we lost focus
                  router.invokeLater(new Runnable()
                  {
                    public void run()
                    {
                      trueValidate();
                    }
                  });
                }
              }
            };
            fsWin.addWindowListener(focusAndClose);
            fsWin.addWindowFocusListener(focusAndClose);
            LayoutManager layer = new LayoutManager()
            {
              public void addLayoutComponent(String name, Component comp)
              {}
              public Dimension minimumLayoutSize(Container parent)
              {
                return preferredLayoutSize(parent);
              }
              public Dimension preferredLayoutSize(Container parent)
              {
                return parent.getPreferredSize();
              }
              public void removeLayoutComponent(Component comp)
              {}
              public void layoutContainer(Container parent)
              {
                rootPanel.setBounds(0, 0, parent.getWidth(), parent.getHeight());
                Rectangle theVBounds = videoBounds;
                if (theVBounds == null)
                  vf.setBounds(0, 0, parent.getWidth(), parent.getHeight());//vf.setBounds(x, y, w, h);
                else
                  vf.setBounds(theVBounds);
              }
            };
            fsWin.getContentPane().setLayout(layer);
            fsWin.addMouseWheelListener(router);
            fsWin.addKeyListener(router);
          }
          win.setVisible(false);
          win.getContentPane().remove(rootPanel);
          win.getContentPane().remove(vf);
          rootPanel.resetHWND();
          fsWin.getContentPane().add(rootPanel, "Center");
          fsWin.getContentPane().add(vf);
          fsExMode = true;
          fsWin.setVisible(true);
          fsWin.setFullScreen(true);
        }
        else
        {
          if (fsWin == null)
          {
            fsExMode = false;
            win.setFullScreen(x);
            win.setVisible(true);
          }
          else if (fsWin != null && fsExMode)
          {
            fsWin.getContentPane().remove(rootPanel);
            fsWin.getContentPane().remove(vf);
            rootPanel.resetHWND();
            win.getContentPane().add(rootPanel);
            win.getContentPane().add(vf);
            fsExMode = false;
            win.setVisible(true);
          }
          else
          {
            fsExMode = false;
            win.setFullScreen(x);
          }
        }
      }
      else
      {
        fsExMode = false;
        win.setFullScreen(x);
      }
    }
    else if (rootPanel.getRenderEngine() instanceof MiniClientSageRenderer)
    {
      ((MiniClientSageRenderer) rootPanel.getRenderEngine()).pushFullScreenMode(x);
      trueValidate();
    }
  }

  public void setIgnoreAllEvents(boolean x)
  {
    ignoreAllEvents = x;
  }

  public boolean isIgnoringAllEvents() { return ignoreAllEvents; }

  public VideoFrame getVideoFrame()
  {
    return vf;
  }

  public STVEditor getStudio()
  {
    return getStudio(false);
  }

  public STVEditor getStudio(boolean createIfNotThere)
  {
    if (ENABLE_STUDIO
        && (!Sage.isHeadless() || Sage.getBoolean("allow_studio_in_headless",false))
        && Permissions.hasPermission(Permissions.PERMISSION_STUDIO, this))
    {
      if (myStudio != null || !createIfNotThere)
        return myStudio;
      try
      {
        myStudio = (STVEditor) Class.forName("sage.StudioFrame").newInstance();
        myStudio.setUIMgr(this);
        return myStudio;
      }
      catch (Throwable e)
      {
        if (Sage.DBG) System.out.println("ERROR Launching Studio: " + e);
        if (Sage.DBG) e.printStackTrace();
        return null;
      }
    }
    return null;
  }

  public String getFullUIClientName()
  {
    return "localhost@@" + uiClientName;
  }

  public String getLocalUIClientName()
  {
    return uiClientName;
  }

  public int getUIClientType()
  {
    return ((rootPanel != null && rootPanel.getRenderType() == ZRoot.REMOTE2DRENDER) ||
        getBoolean("ui/remote_render", false)) ? REMOTE_UI : LOCAL;
  }

  public Object processUIClientHook(String hookName, Object[] hookVars)
  {
    return Catbert.processUISpecificHook(hookName, hookVars, this, false);
  }

  public String getUIClientHostname()
  {
    return "localhost";
  }

  public String getCapability(String capability) {
    return capabilities.get(capability);
  }

  public Map<String, String> getCapabilities() {
    return capabilities;
  }

  void setCapability(String capability, String value) {
    capabilities.put(capability, value);
  }

  private void updateCapabilities() {
    capabilities.clear();
    final List<Widget> currentHooks = Arrays.asList(getGlobalHooks());
    for(Widget hook : currentHooks) {
      final String name = hook.getName();
      if (Sage.DBG) System.out.println("ADDING: " + name + "=true");
      setCapability(name, "true");
    }
  }

  public boolean isServerUI()
  {
    if (Sage.isTrueClient())
      return false;
    int uiType = getUIClientType();
    // We're the placeshifter or an extender, see if the IP address is local (only could be if it's a placeshifter)
    if (uiType == UIClient.REMOTE_UI)
    {
      if (getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
      {
        return ((MiniClientSageRenderer)getRootPanel().getRenderEngine()).isLoopbackConnection();
      }
      return false;
    }
    return (uiType == UIClient.LOCAL);
  }

  public String toString() { return "UIManager:" + getFullUIClientName() + "-" + Integer.toString(hashCode(), 16); }

  public Map<String, Object> getStaticContext()
  {
    return staticContext;
  }

  public Map<String, Object> getGlobalContext()
  {
    return globalContext;
  }

  // UI Properties
  public int getInt(String name, int d)
  {
    if (uiProps == null) return Sage.getInt(name, d);
    return uiProps.getInt(name, d);
  }

  public boolean getBoolean(String name, boolean d)
  {
    if (uiProps == null) return Sage.getBoolean(name, d);
    return uiProps.getBoolean(name, d);
  }

  public long getLong(String name, long d)
  {
    if (uiProps == null) return Sage.getLong(name, d);
    return uiProps.getLong(name, d);
  }

  public float getFloat(String name, float d)
  {
    if (uiProps == null) return Sage.getFloat(name, d);
    return uiProps.getFloat(name, d);
  }

  public String get(String name, String d)
  {
    if (uiProps == null) return Sage.get(name, d);
    return uiProps.get(name, d);
  }

  public void putInt(String name, int x)
  {
    if (uiProps != null) uiProps.putInt(name, x);
    else Sage.putInt(name, x);
  }

  public void putBoolean(String name, boolean x)
  {
    if (uiProps != null) uiProps.putBoolean(name, x);
    else Sage.putBoolean(name, x);
  }

  public void putLong(String name, long x)
  {
    if (uiProps != null) uiProps.putLong(name, x);
    else Sage.putLong(name, x);
  }

  public void putFloat(String name, float x)
  {
    if (uiProps != null) uiProps.putFloat(name, x);
    else Sage.putFloat(name, x);
  }

  public void put(String name, String x)
  {
    if (uiProps != null) uiProps.put(name, x);
    else Sage.put(name, x);
  }

  public void remove(String name)
  {
    if (uiProps != null) uiProps.remove(name);
    else Sage.remove(name);
  }

  public String[] childrenNames(String name)
  {
    if (uiProps == null) return Sage.childrenNames(name);
    return uiProps.childrenNames(name);
  }

  public String[] keys(String name)
  {
    if (uiProps == null) return Sage.keys(name);
    return uiProps.keys(name);
  }

  public void removeNode(String name)
  {
    if (uiProps != null) uiProps.removeNode(name);
    else Sage.removeNode(name);
  }

  public void savePrefs()
  {
    if (uiProps != null) uiProps.savePrefs();
    // Saving the Sage.prefs is redundant since everywhere this is called
    // that's always called in conjunction with it
  }

  SageProperties getProperties()
  {
    return uiProps;
  }

  public ModuleGroup getModuleGroup() { return moduleGroup; }

  public void addTimerTask(TimerTask addMe, long delay, long period)
  {
    if (!alive) return;
    if (uiTimer == null)
      uiTimer = new Timer(true);
    if (period == 0)
      uiTimer.schedule(addMe, delay);
    else
      uiTimer.schedule(addMe, delay, period);
  }

  public Widget[] getGlobalHooks()
  {
    if (hookCache == null || (hookLastCached < moduleGroup.lastModified()))
    {
      hookLastCached = moduleGroup.lastModified();
      hookCache = moduleGroup.getWidgets(Widget.HOOK);
      List<Widget> fixedCache = new ArrayList<Widget>();
      for (int i = 0; i < hookCache.length; i++)
        if (hookCache[i].numContainers() == 0)
          fixedCache.add(hookCache[i]);
      hookCache = fixedCache.toArray(new Widget[0]);
    }
    return hookCache;
  }

  public boolean areLayersEnabled()
  {
    PseudoMenu tempUI = currUI;
    // Animations will be forced off if the client does not support layers & Hw scaling, which is only the MVP
    return coreAnimsEnabled && rootPanel != null && (coreAnimsEnabled = rootPanel.getRenderEngine().canSupportAnimations()) &&
        (!appHasEffects || (tempUI != null && tempUI.areLayersForced()));
  }

  public boolean areCoreAnimationsEnabled()
  {
    return coreAnimsEnabled;
  }

  public void setCoreAnimationsEnabled(boolean x)
  {
    if (coreAnimsEnabled != x)
    {
      coreAnimsEnabled = x;
      putBoolean("ui/animation/core_enabled", x);
    }
  }

  public void setUIResolution(int width, int height)
  {
    fakePanel.setSize(width, height);
    fakePanel.getLayout().layoutContainer(fakePanel);
  }

  public boolean areEffectsEnabled()
  {
    PseudoMenu tempUI = currUI;
    // Animations will be forced off if the client does not support layers & Hw scaling, which is only the MVP
    return coreAnimsEnabled && appHasEffects && rootPanel != null && (coreAnimsEnabled = rootPanel.getRenderEngine().canSupportAnimations()) &&
        (tempUI == null || !tempUI.areLayersForced());
  }

  /*
   * XBMCCompatability - refreshes menu on popup loads, enables faster searching for XMBCID tagged components, don't move table focus to scrollbars automatically from table cells,
   * ensures uniqueness in table data cells, paging listeners are evaluated at the top level rather than locally, alternate text alignment/scaling rules,
   * rescales fixed size UI components, process actions for internal navigation before the explicit listeners,
   * focused effect triggers apply to the upward & downward hierarchy that component is in instead of just the downward,
   * component hierarchy searching goes from the last component to the first, enables use of the 'ListFocusIndex' attribute for identifiying a fixed focus position in a table
   */
  public boolean isXBMCCompatible()
  {
    return xbmcMode;
  }

  // DisableParentClip - ignores parent bounds for clipping children unless attribute 'EnforceBounds' is set
  public boolean disableParentClip()
  {
    if (disableParentClip)
      return true;
    PseudoMenu tempUI = currUI;
    if (tempUI != null && tempUI.disableParentClip())
      return true;
    return false;
  }

  // SingularMouseTransparency - the mouse transparency property for components only applies to themself and not to their hierarchy
  public boolean isSingularMouseTransparency()
  {
    return singularMouseTransparency;
  }

  // AllowHiddenFocus - enables checking for the AllowHiddenFocus attribute in components
  public boolean allowHiddenFocus()
  {
    return allowHiddenFocus;
  }

  private void updateSTVVerInfo()
  {
    stvVersion = null;
    stvName = null;
    Widget globalThemeWidget = null;
    Widget[] widgs = getModuleGroup().getWidgets(Widget.THEME);
    for (int i = 0; i < widgs.length; i++)
      if ("Global".equals(widgs[i].getName()))
      {
        globalThemeWidget = widgs[i];
        break;
      }
    if (globalThemeWidget != null)
    {
      Widget[] attWidgets = globalThemeWidget.contents(Widget.ATTRIBUTE);
      for (int i = 0; i < attWidgets.length; i++)
      {
        if ("STVVersion".equals(attWidgets[i].getName()))
        {
          try
          {
            Object obj = Catbert.evaluateExpression(attWidgets[i].getProperty(Widget.VALUE), new Catbert.Context(this), null, attWidgets[i]);
            if (obj != null)
              stvVersion = obj.toString();
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR Evaluating STVVersion expression of: " + e);
          }
        }
        else if ("STVName".equals(attWidgets[i].getName()))
        {
          try
          {
            Object obj = Catbert.evaluateExpression(attWidgets[i].getProperty(Widget.VALUE), new Catbert.Context(this), null, attWidgets[i]);
            if (obj != null)
              stvName = obj.toString();
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR Evaluating STVVersion expression of: " + e);
          }
        }
      }
    }
  }

  private void updateXBMCCompatible()
  {
    xbmcMode = false;
    allowHiddenFocus = false;
    singularMouseTransparency = false;
    disableParentClip = false;
    appHasEffects = false;
    Widget globalThemeWidget = null;
    Widget[] widgs = getModuleGroup().getWidgets(Widget.THEME);
    for (int i = 0; i < widgs.length; i++)
      if ("Global".equals(widgs[i].getName()))
      {
        globalThemeWidget = widgs[i];
        break;
      }
    if (globalThemeWidget != null)
    {
      Widget[] attWidgets = globalThemeWidget.contents(Widget.ATTRIBUTE);
      for (int i = 0; i < attWidgets.length; i++)
      {
        if ("XBMCCompatability".equals(attWidgets[i].getName()))
          xbmcMode = Catbert.evalBool(attWidgets[i].getProperty(Widget.VALUE));
        else if ("AllowHiddenFocus".equals(attWidgets[i].getName()))
          allowHiddenFocus = Catbert.evalBool(attWidgets[i].getProperty(Widget.VALUE));
        else if ("SingularMouseTransparency".equals(attWidgets[i].getName()))
          singularMouseTransparency = Catbert.evalBool(attWidgets[i].getProperty(Widget.VALUE));
        else if ("DisableParentClip".equals(attWidgets[i].getName()))
          disableParentClip = Catbert.evalBool(attWidgets[i].getProperty(Widget.VALUE));
      }
    }
    appHasEffects = getModuleGroup().getWidgets(Widget.EFFECT).length > 0;
  }

  // For optimizing image caching for relative path images
  public MetaImage getUICachedMetaImage(String src)
  {
    return strImageCacheMap.get(src);
  }

  public void saveUICachedMetaImage(String src, MetaImage mi)
  {
    strImageCacheMap.put(src, mi);
  }

  public BGResourceLoader getBGLoader()
  {
    return rootPanel.getRenderEngine().getBGLoader();
  }

  public String getSTVName()
  {
    return stvName;
  }

  public String getSTVVersion()
  {
    return stvVersion;
  }

  public boolean arePluginImportsActive()
  {
    return pluginImportsActive;
  }

  // Security/Permissions related calls (basically just properties wrappers)
  public String getActiveSecurityProfile()
  {
    return get("security/active_profile", Permissions.getDefaultSecurityProfile());
  }

  public boolean setActiveSecurityProfile(String profile)
  {
    if (!Permissions.isValidSecurityProfile(profile))
      return false;
    put("security/active_profile", profile);
    return true;
  }

  public boolean areUIActionsBeingProcessed(boolean requireUIUpdateInAction)
  {
    EventRouter evtRtr = router;
    ZRoot rooter = rootPanel;
    return (evtRtr != null && rooter != null && (evtRtr.isProcessingEvent() || rooter.isActiveRenderGoingToRun(!requireUIUpdateInAction)));
  }

  private Widget[] hookCache;
  private long hookLastCached;
  private STVEditor myStudio;
  private Panel embeddedPanel;

  private SageProperties uiProps;

  private EventRouter router;
  private VideoFrame vf;
  private MMC mmc;
  private String prefs;
  private Timer uiTimer;

  private Map<String, Object> staticContext = new Catbert.ThreadSafeHashMap<String, Object>();
  private Map<String, Object> globalContext = new Catbert.ThreadSafeHashMap<String, Object>();

  private String uiClientName;

  private boolean windowless;

  private Map<Widget, LinkedHashMap<Map<String, Object>, PseudoMenu>> uiWidgMap;

  private boolean xbmcMode;
  private boolean allowHiddenFocus;
  private boolean disableParentClip;
  private boolean singularMouseTransparency;
  private String stvName;
  private String stvVersion;

  private int overscanX;
  private int overscanY;
  private float overscanWidth;
  private float overscanHeight;

  private PseudoMenu currUI;

  // Widgets for the menu we're coming from and going to; used during menu transition animations
  private Widget menuXtnSrc;
  private Widget menuXtnTarget;

  private boolean uiLockDebug;

  private HashMap<Integer, long[]> ueToIRCodesMap;  // mappings
  private HashMap<Long, Integer> irCodeToUEMap;  // mapping2
  private HashMap<Long, String> irCodeToNameMap; // buttons

  private ZRoot rootPanel;
  private ZComp basePanel;
  private SpecialWindow win;

  private Panel fakePanel;

  private boolean popupDie;
  private Object popupResult;

  private boolean screenSaverOnSleep;

  private boolean coreAnimsEnabled;
  private boolean appHasEffects;

  private boolean isTaskbar;

  private boolean killedAcceleratedRenderingForSleep;

  private long lastDeepSleepTime;

  private Rectangle videoBounds;
  private Rectangle2D.Float embeddedBounds; // relative sizing information

  private boolean autoShowOSDOnMouseMove;

  private Map<String, Object> imageCacheMap; // image name->image object, the Skin resolves name->resource
  private Map<String, Cursor> cursorCacheMap;
  private Map<String, Object> iconCacheMap;
  private Map<String, MetaImage> strImageCacheMap; // resolves String->MetaImage for any relative path images in the STV; cleared when a new UI is loaded

  private Map<String, SoftReference<WaveFile>> audioClipCache;

  private Object syncLock = new Object();
  private Object locker;
  private boolean arMustGetLockNext;
  private long arMustGetLockNextSetTime;
  private Object mustGetLockNext;

  private int uiHistoryDepth;
  private int uiHistoryIndex;
  private Vector<PseudoMenu> uiHistory;

  private File[] imgSearchRoots;

  private boolean pluginImportsActive;

  private ThreadLocal<Boolean> performingActivation = new ThreadLocal<Boolean>();

  // When we're terminating a menu; its possible for a MenuUnload hook or the cleanup actions for
  // an OptionsMenu to cause another menu to launch. But in that case we do NOT want the menu
  // transition to actually occur since this really only happens when going to the Screen Saver.
  // Any other occurrences of it would be incorrect programming in the Studio.
  private boolean performingTermination;

  private Cursor hiddenCursor;
  private boolean cursorHidden;
  private int oldWinState;
  private boolean ignoreAllEvents;

  private boolean alive;

  private Cursor customCursor;

  // do not remove - Jeff
  static Object a;

  /* Track the optional capabilities of the clients to dynamically alter the core */
  private Map<String, String> capabilities = new HashMap<String, String>();

  // 601
  private ModuleGroup moduleGroup;

  // This was moved here because it's a UI dependent thing since a user can have it configured either way

  // 601
  protected static final String zeros = "00000000";
  public static String leadZero(String chNumber)
  {
    if (chNumber == null || chNumber.length() == 0) return ("");

    // result must start with zeros.length() digits by adding leading zeros
    // And all subsequent section of the channel number which are numeric must follow the same rule
    StringBuffer sb = new StringBuffer(chNumber.length() + 10);

    int idxNumStart = -1;
    int lenNumRun = 0;
    for (int i = 0; i < chNumber.length(); i++)
    {
      char c = chNumber.charAt(i);
      if (Character.isDigit(c))
      {
        if (idxNumStart == -1)
          idxNumStart = i;
        lenNumRun++;
      }
      else
      {
        if (lenNumRun > 0)
        {
          sb.append(zeros.substring(0, Math.max(0, zeros.length() - lenNumRun)));
          sb.append(chNumber.substring(idxNumStart, i + 1));
          idxNumStart = -1;
          lenNumRun = 0;
        }
        else
          sb.append(c);
      }
    }
    if (lenNumRun > 0)
    {
      sb.append(zeros.substring(0, Math.max(0, zeros.length() - lenNumRun)));
      sb.append(chNumber.substring(idxNumStart));
    }

    return sb.toString();
  }

  // This can return if it doesn't know, in which case the SD resolution will be used
  public VideoFormat getDisplayResolution()
  {
    if (rootPanel == null) return null;
    if (getUIClientType() == REMOTE_UI)
    {
      MiniClientSageRenderer miniRender = (MiniClientSageRenderer)rootPanel.getRenderEngine();
      return miniRender.getDisplayResolution();
    }
    else if (!windowless && win != null)
    {
      Rectangle r = win.getCurrScreenBounds();
      VideoFormat vidForm = new VideoFormat();
      vidForm.setWidth(r.width);
      vidForm.setHeight(r.height);
      vidForm.setFormatName(r.width + "x" + r.height);
      return vidForm;
    }
    else
      return null;
  }

  // This can return if it doesn't know, in which case the SD resolution will be used
  // This will be different then the display resolution because the UI might be drawn
  // into a frame buffer at a certain resolution which is then mapped to the actual output resolution.
  // We actually care about the framebuffer screen resolution in this case.
  public Dimension getUIDisplayResolution()
  {
    if (rootPanel == null) return null;
    if (getUIClientType() == REMOTE_UI)
    {
      MiniClientSageRenderer miniRender = (MiniClientSageRenderer)rootPanel.getRenderEngine();
      if (miniRender.isMediaExtender())
        return new Dimension(rootPanel.getWidth(), rootPanel.getHeight());
      else
        return miniRender.getMaxClientResolution();
    }
    else if (!windowless && win != null)
    {
      Rectangle r = win.getCurrScreenBounds();
      return new Dimension(r.width, r.height);
    }
    else
      return null;
  }

  public boolean hasRemoteFSSupport()
  {
    if (getUIClientType() != REMOTE_UI || rootPanel == null) return false;
    MiniClientSageRenderer mcsr = (MiniClientSageRenderer) rootPanel.getRenderEngine();
    return mcsr != null && mcsr.hasRemoteFSSupport();
  }

  // 601 revised version
  public final Comparator<Object> channelNumSorter = new Comparator<Object>()
  {
    public int compare(Object o1, Object o2)
    {
      int rv;
      if (o1 == o2) rv = 0;
      else if (o1 == null) rv = (1);
      else if (o2 == null) rv = (-1);
      else
      {
        String s1, s2;
        if (o1 instanceof Channel && o2 instanceof Channel)
        {
          Channel c1 = (Channel) o1;
          Channel c2 = (Channel) o2;

          s1 = leadZero(c1.getNumber(0));
          s2 = leadZero(c2.getNumber(0));
        }
        else
        {
          s1 = leadZero(o1.toString());
          s2 = leadZero(o2.toString());
        }

        rv = (s1.compareTo(s2));
      }
      if (getReverseEPGChannels())
        rv *= -1;
      return rv;
    }
  };
}
