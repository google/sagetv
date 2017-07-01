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
 * Synchronization for this class is for when you modify where events get routed.
 * Since event delivery will be synchronized on the tree lock (I'm pretty sure)
 * we don't need to worry about UI mods during this event stuff.
 */
public class EventRouter implements	java.awt.event.MouseListener,
    java.awt.event.MouseMotionListener,	java.awt.event.KeyListener,
    java.awt.event.MouseWheelListener, Runnable, SageTVInputCallback2
{
  public static final String HIDE_WAIT_TIME = "osd_autohide_time";;
  public static final String SCREEN_SAVER_TIME = "screen_saver_wait_time";
  private static final String KEYBOARD_ACCELERATORS = "keyboard_accelerators";
  private static final String EVENT_DELAY_TO_DISCARD_MIN = "event_delay_to_discard_min";
  private static final String EVENT_DELAY_TO_DISCARD_MAX = "event_delay_to_discard_max";

  public EventRouter(UIManager inUIMgr)
  {
    uiMgr = inUIMgr;
    irRepeatDelay = uiMgr.getInt("infrared_receive_repeat_delay", 250);
    irRepeatDelayRC5 = uiMgr.getInt("infrared_receive_repeat_delay_rc5", 350);
    irRepeatRate = uiMgr.getInt("infrared_receive_repeat_period", 120);
    useAwtEventQueue = uiMgr.getBoolean("sync_input_events_with_awt", true);

    theMap = new java.util.HashMap();

    java.util.HashMap defaultsMap = new java.util.HashMap();
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_UP),
        new UserEvent(0, UserEvent.UP, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_LEFT),
        new UserEvent(0, UserEvent.LEFT, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_RIGHT),
        new UserEvent(0, UserEvent.RIGHT, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_DOWN),
        new UserEvent(0, UserEvent.DOWN, -1));

    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_S, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.PAUSE, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_S, java.awt.event.KeyEvent.CTRL_MASK |
        java.awt.event.KeyEvent.SHIFT_MASK),
        new UserEvent(0, UserEvent.PLAY_PAUSE, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_D, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.PLAY, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_F, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.FF, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_A, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.REW, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_G, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.STOP, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_PAGE_UP),
        new UserEvent(0, UserEvent.CHANNEL_UP, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_PAGE_DOWN),
        new UserEvent(0, UserEvent.CHANNEL_DOWN, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_R, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.VOLUME_UP, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_E, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.VOLUME_DOWN, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_V, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.TV, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_M, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.FASTER, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_N, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.SLOWER, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_X, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.GUIDE, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_Z, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.POWER, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_ENTER),
        new UserEvent(0, UserEvent.SELECT, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_W, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.WATCHED, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_K, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.RATE_UP, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_J, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.RATE_DOWN, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_I, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.INFO, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_Y, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.RECORD, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_F, java.awt.event.KeyEvent.CTRL_MASK |
        java.awt.event.KeyEvent.SHIFT_MASK),
        new UserEvent(0, UserEvent.FULL_SCREEN, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_HOME),
        new UserEvent(0, UserEvent.HOME, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_O, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.OPTIONS, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_ESCAPE),
        new UserEvent(0, UserEvent.OPTIONS, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_M, java.awt.event.KeyEvent.CTRL_MASK |
        java.awt.event.KeyEvent.SHIFT_MASK),
        new UserEvent(0, UserEvent.MUTE, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_DELETE),
        new UserEvent(0, UserEvent.DELETE, -1));

    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_3, java.awt.event.KeyEvent.SHIFT_MASK ),
        new UserEvent(0, UserEvent.DASH, -1));     //remote contro '#"
    //		defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_8, java.awt.event.InputEvent.SHIFT_MASK ),
    //                      new UserEvent(0, UserEvent.DASH, -1));     //remote contro '*"

    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_0),
        new UserEvent(0, UserEvent.NUM0, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_NUMPAD0),
        new UserEvent(0, UserEvent.NUM0, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_1),
        new UserEvent(0, UserEvent.NUM1, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_NUMPAD1),
        new UserEvent(0, UserEvent.NUM1, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_2),
        new UserEvent(0, UserEvent.NUM2, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_NUMPAD2),
        new UserEvent(0, UserEvent.NUM2, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_3),
        new UserEvent(0, UserEvent.NUM3, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_NUMPAD3),
        new UserEvent(0, UserEvent.NUM3, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_4),
        new UserEvent(0, UserEvent.NUM4, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_NUMPAD4),
        new UserEvent(0, UserEvent.NUM4, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_5),
        new UserEvent(0, UserEvent.NUM5, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_NUMPAD5),
        new UserEvent(0, UserEvent.NUM5, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_6),
        new UserEvent(0, UserEvent.NUM6, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_NUMPAD6),
        new UserEvent(0, UserEvent.NUM6, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_7),
        new UserEvent(0, UserEvent.NUM7, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_NUMPAD7),
        new UserEvent(0, UserEvent.NUM7, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_8),
        new UserEvent(0, UserEvent.NUM8, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_NUMPAD8),
        new UserEvent(0, UserEvent.NUM8, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_9),
        new UserEvent(0, UserEvent.NUM9, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_NUMPAD9),
        new UserEvent(0, UserEvent.NUM9, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_MINUS),
        new UserEvent(0, UserEvent.DASH, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_SUBTRACT),
        new UserEvent(0, UserEvent.DASH, -1));

    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_UP, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.CHANNEL_UP2, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_DOWN, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.CHANNEL_DOWN2, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.VOLUME_DOWN2, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.VOLUME_UP2, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_F5),
        new UserEvent(0, UserEvent.PAGE_UP, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_F6),
        new UserEvent(0, UserEvent.PAGE_DOWN, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_F7),
        new UserEvent(0, UserEvent.PAGE_LEFT, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_F8),
        new UserEvent(0, UserEvent.PAGE_RIGHT, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_F7, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.REW_2, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_F8, java.awt.event.KeyEvent.CTRL_MASK),
        new UserEvent(0, UserEvent.FF_2, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.ALT_MASK),
        new UserEvent(0, UserEvent.BACK, -1));
    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.ALT_MASK),
        new UserEvent(0, UserEvent.FORWARD, -1));

    defaultsMap.put(new Keyie(java.awt.event.KeyEvent.VK_F12, java.awt.event.KeyEvent.CTRL_MASK |
        java.awt.event.KeyEvent.SHIFT_MASK), new UserEvent(0, UserEvent.CUSTOMIZE, -1));

    theMap.put(null, new UserEvent(0, UserEvent.ANYTHING, -1));

    loadKBPrefs();
    // This removes any keystrokes that are bound to other things
    defaultsMap.keySet().removeAll(theMap.keySet());
    // This ensures that every command that has a default keymapping has a mapping assigned to it.
    // So a default can be removed, but something needs to be put back in its place
    defaultsMap.values().removeAll(theMap.values());
    theMap.putAll(defaultsMap);

    // Do this after we establish the defaults or these will remove the defaults for PGUG/DN & OPTIONS
    theMap.put(new Wheelie(1, -1), new UserEvent(0, uiMgr.getInt("wheel_down_event_code", UserEvent.CHANNEL_DOWN), -1));
    theMap.put(new Wheelie(-1, -1), new UserEvent(0, uiMgr.getInt("wheel_up_event_code", UserEvent.CHANNEL_UP), -1));
    theMap.put(new Mousie(java.awt.event.MouseEvent.BUTTON3, -1), new UserEvent(0, UserEvent.OPTIONS, -1));
    theMap.put(new Mousie(java.awt.event.MouseEvent.BUTTON1, -1), new UserEvent(0, UserEvent.ANYTHING, -1));

    repeatKeys = new java.util.HashSet();
    repeatKeys.add(new Integer(UserEvent.CHANNEL_DOWN));
    repeatKeys.add(new Integer(UserEvent.CHANNEL_UP));
    repeatKeys.add(new Integer(UserEvent.DOWN));
    repeatKeys.add(new Integer(UserEvent.FF));
    repeatKeys.add(new Integer(UserEvent.FF_2));
    repeatKeys.add(new Integer(UserEvent.LEFT));
    repeatKeys.add(new Integer(UserEvent.RIGHT));
    repeatKeys.add(new Integer(UserEvent.REW));
    repeatKeys.add(new Integer(UserEvent.REW_2));
    repeatKeys.add(new Integer(UserEvent.UP));
    repeatKeys.add(new Integer(UserEvent.VOLUME_DOWN));
    repeatKeys.add(new Integer(UserEvent.VOLUME_UP));
    repeatKeys.add(new Integer(UserEvent.PAUSE));
    repeatKeys.add(new Integer(UserEvent.VOLUME_DOWN2));
    repeatKeys.add(new Integer(UserEvent.VOLUME_UP2));
    repeatKeys.add(new Integer(UserEvent.CHANNEL_DOWN2));
    repeatKeys.add(new Integer(UserEvent.CHANNEL_UP2));
    repeatKeys.add(new Integer(UserEvent.PAGE_DOWN));
    repeatKeys.add(new Integer(UserEvent.PAGE_LEFT));
    repeatKeys.add(new Integer(UserEvent.PAGE_RIGHT));
    repeatKeys.add(new Integer(UserEvent.PAGE_UP));
    repeatKeys.add(new Integer(UserEvent.LEFT_REW));
    repeatKeys.add(new Integer(UserEvent.RIGHT_FF));
    repeatKeys.add(new Integer(UserEvent.UP_VOL_UP));
    repeatKeys.add(new Integer(UserEvent.DOWN_VOL_DOWN));

    VideoFrame vf = uiMgr.getVideoFrame();
    if (uiMgr.getGlobalFrame() != null)
    {
      uiMgr.getGlobalFrame().addMouseWheelListener(this);
      uiMgr.getGlobalFrame().addKeyListener(this);
    }
    vf.addKeyListeners(this);
    vf.addMouseListeners(this);
    vf.addMouseMotionListeners(this);
    loadInputPlugins();
  }

  public void loadInputPlugins()
  {
    if (Sage.WINDOWS_OS)
    {
      if (uiMgr.getUIClientType() == UIClient.LOCAL)
        inputPlugins.add(new SageTVInfraredReceive2());
    }
    else if (Sage.LINUX_OS && uiMgr.getBoolean("linux/load_default_input_plugins", true) && uiMgr.getUIClientType() == UIClient.LOCAL)
    {
      try
      {
        inputPlugins.add(new PVR150Input());
      }
      catch (Throwable t)
      {
        System.out.println("ERROR loading PVR150Input plugin of:" + t);
        t.printStackTrace();
      }
      inputPlugins.add(new LinuxPowerInput());
    }
    else if (Sage.MAC_OS_X && ((uiMgr.getUIClientType() == UIClient.REMOTE_UI && MiniClientSageRenderer.isClientLocalhost(uiMgr.getLocalUIClientName())) || Sage.client))
    {
      // Check if it's a local PS UI which means we load the IR input plugins for it
      if (Sage.DBG) System.out.println("Loading Mac input plugins for local PS UI");
      try
      {
        inputPlugins.add(new MacIRC());
      }
      catch (Throwable t)
      {
        System.out.println("ERROR loading MacIRC plugin of:" + t);
        t.printStackTrace();
      }
    }
    //if (uiMgr.getUIClientType() == UIClient.REMOTE_UI)
    //	inputPlugins.add(new MiniInput());
    String extraPlugins = uiMgr.get("input_plugin_classes", "");
    if (extraPlugins.length() > 0)
    {
      java.util.StringTokenizer toker = new java.util.StringTokenizer(extraPlugins, ",");
      while (toker.hasMoreTokens())
      {
        try
        {
          String className = toker.nextToken();
          if (!Sage.WINDOWS_OS && (className.equals("sage.LinuxInput") || className.equals("sage.PVR150Input")))
            continue;
          Class pluginClass = Class.forName(className);
          SageTVInputPlugin pluggy = (SageTVInputPlugin) pluginClass.newInstance();
          inputPlugins.add(pluggy);
        }
        catch (Throwable e)
        {
          System.out.println("ERROR creating input plugin:" + e);
          e.printStackTrace();
        }
      }
    }
  }

  public void startInputPlugins()
  {
    for (int i = 0; i < inputPlugins.size(); i++)
    {
      try
      {
        if (!((SageTVInputPlugin) inputPlugins.get(i)).openInputPlugin(this))
          inputPlugins.removeElementAt(i--);
      }
      catch (Throwable t)
      {
        if (Sage.DBG)
        {
          System.out.println("ERROR opening input plugin of:" + t);
          t.printStackTrace();
        }
        inputPlugins.removeElementAt(i--);
      }
    }
  }

  public void startRouter()
  {
    startInputPlugins();

    alive = true;

    if (!useAwtEventQueue)
    {
      Thread t = new Thread(this, "EventRouter-" + uiMgr.getLocalUIClientName());
      t.setDaemon(true);
      t.start();
    }
    // So the screen saver can fire w/out an initial event occuring
    resetSSTimer();
  }

  public void kill()
  {
    if (alive)
    {
      alive = false;
      cancelSSTimer();
      cancelHideTimer();
      for (int i = 0; i < inputPlugins.size(); i++)
        ((SageTVInputPlugin) inputPlugins.get(i)).closeInputPlugin();
      inputPlugins.clear();
      synchronized (evtQueue)
      {
        evtQueue.notifyAll();
      }
      if (Sage.DBG) System.out.println("Killed EventRouter");
    }
  }

  private void loadKBPrefs()
  {
    String kbStr = uiMgr.get(KEYBOARD_ACCELERATORS, "");
    java.util.StringTokenizer toker = new java.util.StringTokenizer(kbStr, ";");
    while (toker.hasMoreTokens())
    {
      java.util.StringTokenizer toker2 = new java.util.StringTokenizer(toker.nextToken(), ",");
      if (toker2.countTokens() == 3)
      {
        try
        {
          theMap.put(new Keyie(Integer.parseInt(toker2.nextToken()), Integer.parseInt(toker2.nextToken())),
              new UserEvent(0, Integer.parseInt(toker2.nextToken()), -1));
        }
        catch (Exception e){}
      }
    }
  }

  private void saveKBPrefs()
  {
    StringBuffer sb = new StringBuffer();
    java.util.Iterator walker = theMap.entrySet().iterator();
    while (walker.hasNext())
    {
      java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
      if (ent.getKey() instanceof Keyie)
      {
        Keyie k = (Keyie) ent.getKey();
        UserEvent ue = (UserEvent) ent.getValue();
        sb.append(k.code);
        sb.append(',');
        sb.append(k.mods);
        sb.append(',');
        sb.append(ue.getType());
        sb.append(';');
      }
    }
    uiMgr.put(KEYBOARD_ACCELERATORS, sb.toString());
  }

  public void setKBPrefs(int keyCode, int mods, int ueID, boolean enable)
  {
    if (enable)
      theMap.put(new Keyie(keyCode, mods), new UserEvent(0, ueID, -1, keyCode, mods, (char)0));
    else
      theMap.remove(new Keyie(keyCode, mods));
    saveKBPrefs();
  }

  public java.util.ArrayList getKBAccel(int ueID)
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    java.util.Iterator walker = theMap.entrySet().iterator();
    while (walker.hasNext())
    {
      java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
      if (ent.getKey() instanceof Keyie && ((UserEvent) ent.getValue()).getType() == ueID)
      {
        Keyie k = (Keyie) ent.getKey();
        rv.add(new int[] { k.code, k.mods });
      }
    }
    Object[] sortie = rv.toArray();
    java.util.Arrays.sort(sortie, new java.util.Comparator()
    {
      public int compare(Object o1, Object o2)
      {
        int[] i1 = (int[]) o1;
        int[] i2 = (int[]) o2;
        if (i1[0] != i2[0])
          return i1[0] - i2[0];
        else
          return i1[1] - i1[1];
      }
    });
    return new java.util.ArrayList(java.util.Arrays.asList(sortie));
  }

  public int getUEIDForKB(int keyCode, int mods)
  {
    UserEvent ue = (UserEvent) theMap.get(new Keyie(keyCode, mods));
    if (ue == null)
      return -1;
    else
      return ue.getType();
  }

  public void setHandler(EventHandler inHandler)
  {
    synchronized (handlerLock)
    {
      handler = inHandler;
    }
  }

  public EventHandler getHandler() { return handler; }

  public int getCounter() { return evtCounter; }

  public void mousePressed(java.awt.event.MouseEvent evt)
  {
    lastEventTime = Sage.eventTime();
    cancelHideTimer();
  }

  public void mouseReleased(java.awt.event.MouseEvent evt)
  {
    lastEventTime = Sage.eventTime();
    if (uiMgr.isIgnoringAllEvents()) return;
    if (!uiMgr.getBoolean("disable_auto_mouse_events", false))
      processEvent(evt);
  }

  public void mouseClicked(java.awt.event.MouseEvent evt)
  {
  }

  public void mouseEntered(java.awt.event.MouseEvent evt)
  {
  }

  public void mouseExited(java.awt.event.MouseEvent evt)
  {
  }

  public void mouseMoved(java.awt.event.MouseEvent evt)
  {
    if (uiMgr.isIgnoringAllEvents()) return;
    java.awt.Point currPt = evt.getPoint();
    convertPointToScreen(currPt, (java.awt.Component) evt.getSource());
    if (lastMousePt != null)
    {
      if (currPt.equals(lastMousePt))
        return;
    }
    //System.out.println("Mouser=" + evt + " pos=" + currPt  + " lastPos=" + lastMousePt);
    lastMousePt = currPt;

    if (uiMgr.getBoolean("ui/disable_mouse_motion_trigger", true))
    {
      uiMgr.setHidden(false, true);
    }
    else
    {
      processEvent(null);
    }
  }

  public void mouseDragged(java.awt.event.MouseEvent evt)
  {
  }

  private int sign(int x)
  {
    return (x < 0) ? -1 : ((x > 0) ? 1 : 0);
  }

  public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt)
  {
    if (uiMgr.isIgnoringAllEvents()) return;
    if (lastEvent instanceof java.awt.event.MouseWheelEvent)
    {
      java.awt.event.MouseWheelEvent lastWheel =
          (java.awt.event.MouseWheelEvent) lastEvent;
      if ((lastWheel.getWhen() + WHEEL_TIME_SPACING > evt.getWhen()) /*&&
				(sign(lastWheel.getWheelRotation()) != sign(evt.getWheelRotation()))*/)
      {
        return;
      }
    }

    processEvent(evt);
  }

  private boolean lastWasPressed;
  private boolean ignoreNextTyped;
  private int lastKeyCode;
  private int lastModifiers;
  public void keyPressed(java.awt.event.KeyEvent evt)
  {
    if (Sage.DBG && uiMgr.getBoolean("debug_input_events", false))
      System.out.println("keyPressed=" + evt);
    lastWasPressed = true;
    lastKeyCode = evt.getKeyCode();
    lastModifiers = evt.getModifiers();

    if (uiMgr.isIgnoringAllEvents()) return;
    if (Sage.DBG && !Sage.WINDOWS_OS && lastKeyCode == java.awt.event.KeyEvent.VK_Q &&
        (lastModifiers & (java.awt.event.KeyEvent.ALT_MASK | java.awt.event.KeyEvent.SHIFT_MASK | java.awt.event.KeyEvent.CTRL_MASK)) ==
        (java.awt.event.KeyEvent.ALT_MASK | java.awt.event.KeyEvent.SHIFT_MASK | java.awt.event.KeyEvent.CTRL_MASK))
    {
      SageTV.exit();
      return;
    }
    // If the keystroke is mapped, then send the event down once the press is detected.
    // Otherwise, we have to wait for the first release to determine what the keystroke was.
    Keyie convEvt = new Keyie(evt.getKeyCode(),
        evt.getModifiers());
    UserEvent ue = (UserEvent) theMap.get(convEvt);
    if (ue != null)
    {
      // Clear the event so we don't double fire it on the release
      lastWasPressed = false;
      ignoreNextTyped = true;

      uiMgr.setHidden(false, false);

      // We used to send 0 instead of the keyChar here. But that resulted in numeric events haven't no keychar which would
      // be incorrect. I couldn't find anything wrong with doing this; but we'll have to see. Narflex 10/11/2005
      submitUserEvent(new UserEvent(evt.getWhen(), ue.getType(), -1, lastKeyCode, lastModifiers, evt.getKeyChar() !=
          java.awt.event.KeyEvent.CHAR_UNDEFINED ? evt.getKeyChar() : (char)0));
    }
    else
      ignoreNextTyped = false;
  }

  public void keyTyped(java.awt.event.KeyEvent evt)
  {
    if (uiMgr.isIgnoringAllEvents()) return;
    if (Sage.DBG && uiMgr.getBoolean("debug_input_events", false))
      System.out.println("keyTyped=" + evt);
    if (lastWasPressed)
    {
      lastWasPressed = false;
      submitUserEvent(new UserEvent(evt.getWhen(), UserEvent.ANYTHING, -1, lastKeyCode, lastModifiers,
          evt.getKeyChar()));
    }
    else if (!ignoreNextTyped && evt.getKeyCode() == 0 && evt.getKeyChar() != 0)
    {
      // This is used for input methods. Specifically for Chinese on Windows
      if (Sage.DBG) System.out.println("Special Key Typed: " + evt);
      submitUserEvent(new UserEvent(evt.getWhen(), UserEvent.ANYTHING, -1, 0, 0,
          evt.getKeyChar()));
    }
    ignoreNextTyped = false;
  }

  public void keyReleased(java.awt.event.KeyEvent evt)
  {
    if (Sage.DBG && uiMgr.getBoolean("debug_input_events", false))
      System.out.println("keyReleased=" + evt);
    if (lastWasPressed)
    {
      lastWasPressed = false;
      if (uiMgr.isIgnoringAllEvents()) return;
      submitUserEvent(new UserEvent(evt.getWhen(), UserEvent.ANYTHING, -1, lastKeyCode, lastModifiers, (char)0));
    }
    // This is to fix a problem where the first character on an input was being ignored if we did a valid press release on a key
    // that didn't fire a keyTyped event. (like up/down/left/right)
    ignoreNextTyped = false;
  }

  private void processEvent(java.awt.event.InputEvent evt)
  {
    if (uiMgr.isIgnoringAllEvents()) return;
    Object convEvt = null;
    int count = ((lastEvent != null) && (evt != null) && (evt.getWhen() -
        lastEvent.getWhen() < DOUBLE_CLICK)) ? 2 : 1;
    lastEvent = evt;
    if (evt instanceof java.awt.event.KeyEvent)
    {
      convEvt = new Keyie(((java.awt.event.KeyEvent)evt).getKeyCode(),
          evt.getModifiers());
      uiMgr.setHidden(false, false);
    }
    else if (evt instanceof java.awt.event.MouseWheelEvent)
    {
      convEvt = new Wheelie(sign(
          ((java.awt.event.MouseWheelEvent)evt).getWheelRotation()), count);
      uiMgr.setHidden(false, true);
    }
    else if (evt instanceof java.awt.event.MouseEvent)
    {
      int clickCount =
          ((((java.awt.event.MouseEvent)evt).getClickCount()+1) % 2) + 1;
      convEvt = new Mousie(((java.awt.event.MouseEvent)evt).getButton(),
          clickCount);
      uiMgr.setHidden(false, true);
    }
    else if (evt == null)
      uiMgr.setHidden(false, true);

    UserEvent theUE = (UserEvent) theMap.get(convEvt);
    if (theUE != null)
    {
      theUE = new UserEvent((evt != null) ? evt.getWhen() : getEventCreationTime(),
          theUE.getType(), -1);
    }
    submitUserEvent(theUE);
  }

  public void processIREvent(byte[] irCode)
  {
    if (uiMgr.isIgnoringAllEvents()) return;
    long coded = new java.math.BigInteger(irCode).longValue();
    //System.out.println("IR CODE RECEIVED:" + coded);

    if (Sage.DBG && uiMgr.getBoolean("debug_input_events", false))
      System.out.println("Recvd Event IR:" + coded);
    // NOTE: For the USB-UIRT this is a 12-char code, but I need to put it into 8 bytes. So I
    // need to convert it back to a string and then parse it as hex. But I also need to maintain backwards
    // compatability if there's an old code defined for something but not a new one.
    if (irCode.length > 8)
    {
      String hexString = new String(irCode);
      try
      {
        long altCoded = new java.math.BigInteger(hexString, 16).longValue();
        if (uiMgr.getUECodeForIRCode(altCoded) != UserEvent.ANYTHING ||
            uiMgr.getUECodeForIRCode(coded) == UserEvent.ANYTHING)
        {
          // New 12-byte code defined, use that instead.
          coded = altCoded;
        }
      }
      catch (Exception e)
      {
        if (Sage.DBG) System.out.println("ERROR parsing ir hex string:" + hexString);
      }
    }

    boolean buttonState = false;
    if (rc5)
    {
      buttonState = (coded & 0x800) != 0;
      coded = coded & ~0x800;
    }

    UserEvent evt = new UserEvent(getEventCreationTime(), uiMgr.getUECodeForIRCode(coded), coded);
    synchronized (irRecvLock)
    {
      if (evt.getType() == UserEvent.ANYTHING)
      {
        lastIRTime = Sage.eventTime();
        lastIREventTime = Sage.eventTime();
        lastIRCode = coded;
        repeatingIRCode = false;
      }
      else if (rc5)
      {
        // We can do much more optimized repeat detection with rc5
        if (buttonState != lastButtonState || lastIRCode != coded)
        {
          lastButtonState = buttonState;
          // Definitely a new physical button press
          repeatingIRCode = false;
          lastIRCode = coded;
          lastIREventTime = lastIRTime = lastIRCodeStartTime = Sage.eventTime();
          evt.setDiscardable(false);
        }
        else
        {
          lastButtonState = buttonState;
          // If they haven't held it down long enough to start repeating (or if this command doesn't repeat), then just return...
          // but if we've had no IR activity for the past 500 msec, consider this a new IR evnt and take it
          if (Sage.eventTime() - lastIRCodeStartTime < irRepeatDelayRC5 || !repeatKeys.contains(new Integer(evt.getType())))
          {
            if (Sage.eventTime() - lastIREventTime < 500)
            {
              lastIREventTime = Sage.eventTime();
              return;
            }
          }
          if (Sage.eventTime() - lastIREventTime >= 500)
            lastIRCodeStartTime = Sage.eventTime();
          lastIREventTime = lastIRTime = Sage.eventTime();
        }
      }
      else if (lastIRCode == coded)
      {
        if (!repeatingIRCode)
        {
          if (Sage.eventTime() - lastIRCodeStartTime < irRepeatDelay)
          {
            repeatingIRCode = true;
            lastIREventTime = Sage.eventTime();
            return;
          }
          else
          {
            lastIRCodeStartTime = lastIRTime = Sage.eventTime();
          }
        }
        else if (Sage.eventTime() - lastIRTime < irRepeatRate) // && repeatingIRCode
        {
          lastIREventTime = Sage.eventTime();
          return;
        }
        else if (Sage.eventTime() - lastIREventTime >= irRepeatDelay)
        {
          repeatingIRCode = false;
          lastIRCodeStartTime = lastIRTime = Sage.eventTime();
        }
        lastIRTime = lastIREventTime = Sage.eventTime();
        if (repeatingIRCode && !repeatKeys.contains(new Integer(evt.getType())))
          return;
      }
      else
      {
        lastIREventTime = lastIRTime = lastIRCodeStartTime = Sage.eventTime();
        lastIRCode = coded;
        repeatingIRCode = false;
        evt.setDiscardable(false);
      }
    }
    submitUserEvent(evt);
  }

  void ssTimerRestart() { resetSSTimer(); }
  public void resetInactivityTimers()
  {
    resetHideTimer();
    resetSSTimer();
  }

  // the smallest time difference we've ever seen, we can't expect anything to do better
  private long minTimeDiff = Long.MAX_VALUE;
  private int successiveDiscards = 0;
  private int lastEventType = -1;
  private int lastDiscardType = -1;
  public long getEventCreationTime()
  {
    return Sage.eventTime() - ((minTimeDiff == Long.MAX_VALUE) ?  0 : minTimeDiff);
  }
  private void processUserEvent(UserEvent ue)
  {
    lastEventTime = Sage.eventTime();
    if (Sage.DBG) System.out.println("processUserEvent-" + ue + " evtTime=" + Sage.df(ue != null ? ue.getWhen() : Sage.time()));
    if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
      ServerPowerManagement.getInstance().kick();

    if (ue != null && ue.isDiscardable())
    {
      minTimeDiff = Math.min(minTimeDiff, Sage.eventTime() - ue.getWhen());
      // Special case here for allowing keystroke events through if they're not linked to an event (except for numbers)
      // This is to fix the problem in placeshifter login where keystrokes are sometimes lost
      if (ue.getType() != lastEventType ||
          ((ue.getType() == UserEvent.ANYTHING || (ue.getType() >= UserEvent.NUM0 && ue.getType() <= UserEvent.NUM9)) &&
              ue.getKeyChar() != 0 && ue.getKeyChar() != java.awt.event.KeyEvent.CHAR_UNDEFINED))
      {
        lastEventType = ue.getType();
        minTimeDiff = Long.MAX_VALUE;
        successiveDiscards = 0;
        lastDiscardType = -1;
      }
      // NOTE: We tried to increase these but it just caused old problems to come back; this was
      // done with extensive testing a few years back so let's just leave it alone
      else if ((getEventCreationTime() - ue.getWhen() > uiMgr.getLong(EVENT_DELAY_TO_DISCARD_MIN, 100) &&
          successiveDiscards > 0) ||
          (getEventCreationTime() - ue.getWhen() > uiMgr.getLong(EVENT_DELAY_TO_DISCARD_MAX, 100) &&
              successiveDiscards == 0))
      {
        if (successiveDiscards == 0 && getEventCreationTime() - ue.getWhen() > 1000)
        {
          // Reset the minimum time diff in case we were a jump in time. This'll protect event
          // loss when the clock shifts.
          successiveDiscards++;
          minTimeDiff = Sage.eventTime() - ue.getWhen();
        }
        else
        {
          if (Sage.DBG && uiMgr.getBoolean("debug_input_events", false)) System.out.println("Discarding event evtTime=" + Sage.df(Sage.eventTime()) +
              " when=" + Sage.df(ue.getWhen()) + " minDiff=" + minTimeDiff);
          successiveDiscards++;
          if (successiveDiscards > 3 && lastDiscardType == -1)
          {
            minTimeDiff = Long.MAX_VALUE;
            successiveDiscards = 0;
            lastDiscardType = ue.getType();
          }
          evtCounter = (evtCounter + 1) % 100000;
          return;
        }
      }
      else
        successiveDiscards = 0;
    }
    else
      successiveDiscards = 0;
    uiMgr.setHidden(false, false);

    resetSSTimer();
    EventHandler currHandler;
    synchronized (handlerLock)
    {
      currHandler = handler;
    }
    if (currHandler == null)
    {
      resetHideTimer();
      evtCounter = (evtCounter + 1) % 100000;
      return;
    }

    if (ue != null)
    {
      try
      {
        processingEvent = true;
        if (uiMgr.isAsleep())
        {
          if (uiMgr.isCurrUIScreenSaver() && ue.getType() != UserEvent.POWER_OFF &&
              (ue.getType() != UserEvent.ANYTHING || ue.isKB()))
          {
            uiMgr.gotoSleep(false);
          }
          else
            currHandler.action(ue);
        }
        else if (uiMgr.isCurrUIScreenSaver())
          uiMgr.backupUI();
        else// if (currHandler instanceof PseudoMenu)
        {
          long eventProcessStart = Sage.time();
          if (SageTV.PERF_TIMING)
            System.out.println("PERF: Event Starting");
          currHandler.action(ue);
          if (SageTV.PERF_TIMING)
            System.out.println("PERF: Event: " + (Sage.time() - eventProcessStart));
          uiMgr.getRootPanel().kickActiveRenderer();
        }
      }
      finally
      {
        processingEvent = false;
      }
    }

    resetHideTimer();

    // Do this after we do the kickActiveRenderer so that it doesn't think this event caused
    // a background animation that's *just* about to occur
    evtCounter = (evtCounter + 1) % 100000;

    // This is always safe to call
    //if (ZComp.defaultReality != null)
    //	ZComp.defaultReality.renderOnceIfDirty();
  }

  public boolean isProcessingEvent()
  {
    return processingEvent;
  }

  private void postUserEvent(UserEvent ue)
  {
    synchronized (evtQueue)
    {
      evtQueue.addElement(ue);
      evtQueue.notifyAll();
    }
  }

  public void run()
  {
    while (alive)
    {
      synchronized (evtQueue)
      {
        if (evtQueue.isEmpty())
        {
          try
          {
            evtQueue.wait();
          }
          catch (Exception e){}
          continue;
        }
      }
      while (!evtQueue.isEmpty())
      {
        Object nextEvt = null;
        try
        {
          nextEvt = evtQueue.firstElement();
          if (nextEvt instanceof UserEvent)
            processUserEvent((UserEvent) nextEvt);
          else if (nextEvt instanceof Runnable)
            ((Runnable) nextEvt).run();
          else if (nextEvt instanceof java.awt.event.MouseEvent)
          {
            ZRoot evtSink = uiMgr.getRootPanel();
            if (evtSink != null)
              evtSink.mouseEntered((java.awt.event.MouseEvent) nextEvt);
          }
        }
        catch (Throwable loser)
        {
          System.out.println("EventRouter Threw An Exception:" + loser);
          loser.printStackTrace();
        }
        evtQueue.remove(nextEvt);
      }
    }
  }

  public void submitUserEvent(final UserEvent evt)
  {
    lastEventTime = Sage.eventTime();
    if (uiMgr.isIgnoringAllEvents()) return;
    if (!useAwtEventQueue)
      postUserEvent(evt);
    else if (!java.awt.EventQueue.isDispatchThread())
      java.awt.EventQueue.invokeLater(new Runnable()
      {
        public void run() { processUserEvent(evt); }
      });
    else
      processUserEvent(evt);
  }

  public void invokeLater(Runnable runny)
  {
    if (useAwtEventQueue)
      java.awt.EventQueue.invokeLater(runny);
    else
    {
      synchronized (evtQueue)
      {
        evtQueue.addElement(runny);
        evtQueue.notifyAll();
      }

    }
  }

  public void invokeAndWait(Runnable runny) throws InterruptedException, java.lang.reflect.InvocationTargetException
  {
    if (useAwtEventQueue)
      java.awt.EventQueue.invokeAndWait(runny);
    else
    {
      synchronized (evtQueue)
      {
        evtQueue.addElement(runny);
        while (evtQueue.contains(runny))
        {
          evtQueue.wait();
        }
      }

    }
  }

  static class Keyie
  {
    public Keyie(int inCode) { this (inCode, 0); }
    public Keyie(int inCode, int inMods)
    {
      code = inCode;
      mods = inMods;
    }

    public boolean equals(Object o)
    {
      return ((o instanceof Keyie) && (((Keyie) o).code == code) && (((Keyie) o).mods == mods));
    }

    public int hashCode()
    {
      return code*13 + mods*41 + 97;
    }

    private int code;
    private int mods;
  }

  private static class Mousie
  {
    public Mousie(int inButton, int inCount)
    {
      button = inButton;
      count = inCount;
    }

    public boolean equals(Object o)
    {
      if ((o instanceof Mousie) && (((Mousie) o).button == button))
      {
        Mousie m = (Mousie) o;
        return (count == m.count) || (count == -1) ||
            (m.count == -1);
      }
      else
      {
        return false;
      }
    }

    public int hashCode()
    {
      return button*13;
    }

    private int button;
    private int count;
  }

  private static class Wheelie
  {
    public Wheelie(int inDirection, int inCount)
    {
      direction = inDirection;
      count = inCount;
    }

    public boolean equals(Object o)
    {
      if ((o instanceof Wheelie) && (((Wheelie) o).direction == direction))
      {
        Wheelie m = (Wheelie) o;
        return (count == m.count) || (count == -1) ||
            (m.count == -1);
      }
      else
      {
        return false;
      }
    }

    public int hashCode()
    {
      return direction*271 + 37;
    }

    private int count;
    private int direction;
  }

  private Object timerLocks = new Object();
  // Used for when mouse buttons are being held down because those are infinite timeouts
  private void cancelHideTimer()
  {
    synchronized (timerLocks)
    {
      if (hideTimer != null)
        hideTimer.cancel();
    }
  }
  public void resetHideTimer()
  {
    if (!alive) return;
    synchronized (timerLocks)
    {
      if (hideTimer != null)
        hideTimer.cancel();
      uiMgr.addTimerTask(hideTimer = new HideTimerTask(), uiMgr.getInt(HIDE_WAIT_TIME, 5000), 0);
    }
  }

  // Needed at cleanup time to remove thread references to a client!
  private void cancelSSTimer()
  {
    synchronized (timerLocks)
    {
      if (ssTimer != null)
        ssTimer.cancel();
    }
  }
  public void resetSSTimer()
  {
    if (!alive) return;
    synchronized (timerLocks)
    {
      if (ssTimer != null)
        ssTimer.cancel();
      /*
       * We had to make the ss timer repeating. This is because of:
       * 1. Watching a show, use the remote control...stop using it, keep watching, fall asleep.
       * 2. Show ends 1 hr later, ss timer has already expired, UI is showing a prompt
       * 3. We NEED to make the ss timer repeat, so it can fire again and notice the inactivity
       *    in a state that the screen saver can activate from
       */
      ssTimer = null;
      // Add the 2345 here so we don't align on a show's end time. i.e. 60 mins & 20min*3. Because
      // there's a rare bug that can popup where we hit the screensaver timeout on that 50msec between a fileswitch
      // and it thinks there's no file loaded so it'll kick on the screensaver.
      long delay = uiMgr.getInt(SCREEN_SAVER_TIME, (int) (Sage.MILLIS_PER_MIN*20)) + 2345;
      if (delay > 15000)
        uiMgr.addTimerTask(ssTimer = new SSTimerTask(), delay, delay);
    }
  }

  public static void convertPointToScreen(java.awt.Point p,java.awt.Component c) {
    java.awt.Rectangle b;
    int x,y;
    do {
      if(c instanceof java.awt.Window) {
        try {
          java.awt.Point pp = c.getLocationOnScreen();
          x = pp.x;
          y = pp.y;
        } catch (java.awt.IllegalComponentStateException icse) {
          x = c.getX();
          y = c.getY();
        }
      } else {
        x = c.getX();
        y = c.getY();
      }

      p.x += x;
      p.y += y;

      if(c instanceof java.awt.Window)
        break;
      c = c.getParent();
    } while(c != null);
  }

  public void recvCommand(int sageCommandID)
  {
    recvCommand(sageCommandID, (String[])null);
  }

  public void recvCommand(int sageCommandID, String payload)
  {
    recvCommand(sageCommandID, new String[] { payload });
  }

  public void recvCommand(int sageCommandID, String[] payloads)
  {
    if (uiMgr.isIgnoringAllEvents()) return;
    if (Sage.DBG && uiMgr.getBoolean("debug_input_events", false))
      System.out.println("Recvd Event Cmd:" + sageCommandID);
    UserEvent ue = new UserEvent(getEventCreationTime(), sageCommandID, -1);
    ue.setPayloads(payloads);
    submitUserEvent(ue);
  }

  public void recvInfrared(byte[] infraredCode)
  {
    if (uiMgr.isIgnoringAllEvents()) return;
    processIREvent(infraredCode);
  }

  public void recvKeystroke(char keyChar, int keyCode, int keyModifiers)
  {
    if (uiMgr.isIgnoringAllEvents()) return;
    if (Sage.DBG && uiMgr.getBoolean("debug_input_events", false))
      System.out.println("Recvd Event Key:" + keyChar + ' ' + keyCode + ' ' + keyModifiers);
    Keyie convEvt = new Keyie(keyCode, keyModifiers);
    if (Sage.DBG && !Sage.WINDOWS_OS && keyCode == java.awt.event.KeyEvent.VK_Q &&
        (keyModifiers == (java.awt.event.KeyEvent.ALT_MASK | java.awt.event.KeyEvent.SHIFT_MASK | java.awt.event.KeyEvent.CTRL_MASK)))
    {
      SageTV.exit();
      return;
    }
    UserEvent ue = (UserEvent) theMap.get(convEvt);
    if (ue != null)
      submitUserEvent(new UserEvent(getEventCreationTime(), ue.getType(), -1, keyCode, keyModifiers,
          keyChar));
    else
      submitUserEvent(new UserEvent(getEventCreationTime(), UserEvent.ANYTHING, -1, keyCode, keyModifiers,
          keyChar));
  }

  public SageTVInputPlugin getDefaultInputPlugin()
  {
    return (SageTVInputPlugin)inputPlugins.firstElement();
  }

  public void recvMouse(int eventID, int modifiers, int x, int y, int clickCount, int button)
  {
    if (uiMgr.isIgnoringAllEvents()) return;
    //System.out.println("recvMouse evtId=" + eventID + " mods=" + modifiers + " x=" + x + " y=" + y + " click=" + clickCount + " button=" + button);
    ZRoot evtSink = uiMgr.getRootPanel();
    if (evtSink == null) return;
    if (eventID == java.awt.event.MouseEvent.MOUSE_WHEEL)
    {
      java.awt.event.MouseWheelEvent mwe = new java.awt.event.MouseWheelEvent(evtSink, eventID, Sage.eventTime(), modifiers, x, y, 1, false,
          java.awt.event.MouseWheelEvent.WHEEL_BLOCK_SCROLL, 0, clickCount);
      mouseWheelMoved(mwe);
    }
    else
    {
      java.awt.event.MouseEvent me = new java.awt.event.MouseEvent(evtSink, eventID, Sage.eventTime(), modifiers, x, y, clickCount, false, button);
      switch (eventID)
      {
        case java.awt.event.MouseEvent.MOUSE_CLICKED:
          mouseClicked(me);
          break;
        case java.awt.event.MouseEvent.MOUSE_PRESSED:
          mousePressed(me);
          break;
        case java.awt.event.MouseEvent.MOUSE_RELEASED:
          mouseReleased(me);
          break;
        case java.awt.event.MouseEvent.MOUSE_DRAGGED:
          mouseDragged(me);
          break;
        case java.awt.event.MouseEvent.MOUSE_MOVED:
          mouseMoved(me);
          break;
      }
      // all the mouse listeners do the same thing in ZRoot
      if (useAwtEventQueue) {
        java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(me);
      } else {
        synchronized (evtQueue)
        {
          evtQueue.addElement(me);
          evtQueue.notifyAll();
        }
      }
    }
  }

  public UIManager getUIMgr() { return uiMgr; }

  public boolean isRC5() { return rc5; }
  public void setRC5(boolean x) { rc5 = x; }

  // This uses the eventTime timeline
  public long getLastEventTime()
  {
    return lastEventTime;
  }

  public void updateLastEventTime()
  {
    lastEventTime = Sage.eventTime();
  }

  private boolean useAwtEventQueue;
  private UIManager uiMgr;
  private EventHandler handler;
  private java.util.Map theMap;
  private java.util.Set repeatKeys;
  private java.awt.event.InputEvent lastEvent = null;
  private Object handlerLock = new Object();
  private int evtCounter;

  private boolean processingEvent;

  private java.awt.Point lastMousePt;

  private java.util.TimerTask hideTimer;
  private java.util.TimerTask ssTimer;

  private long lastIRCode;
  private long lastIRTime;
  private long lastIREventTime;
  private long lastIRCodeStartTime;
  private int irRepeatDelay;
  private int irRepeatDelayRC5;
  private int irRepeatRate;
  private boolean repeatingIRCode;
  private Object irRecvLock = new Object();
  private long lastEventTime = Sage.eventTime(); // a better default then zero

  private boolean rc5;
  private boolean lastButtonState;

  private java.util.Vector evtQueue = new java.util.Vector();

  private java.util.Vector inputPlugins = new java.util.Vector();

  private boolean alive;

  private static final long WHEEL_TIME_SPACING = 100;
  private static final long DOUBLE_CLICK = 250L;
  private static final int MAX_COUNT = 2;

  private class HideTimerTask extends java.util.TimerTask
  {
    public void run()
    {
      uiMgr.setHidden(true, false);
      resetSSTimer();
      Catbert.processUISpecificHook("InactivityTimeout", null, uiMgr, true);
    }
  }

  private class SSTimerTask extends java.util.TimerTask
  {
    private long initTime;
    public SSTimerTask()
    {
      initTime = Sage.eventTime();
    }
    public void run()
    {
      if (Sage.eventTime() - initTime < uiMgr.getInt(SCREEN_SAVER_TIME, (int) (Sage.MILLIS_PER_MIN*20)) - 60000)
      {
        if (Sage.DBG) System.out.println("Detected premature firing of screen saver (due to clock shift)...restart its timer");
        resetSSTimer();
        return;
      }
      boolean isTV = uiMgr.isTV();
      // Don't think we're playing if we're at the EOS!
      boolean isPlayin = uiMgr.getVideoFrame().getPlayerState() == MediaPlayer.PLAY_STATE;
      boolean fs = uiMgr.isFullScreen();
      boolean sleepy = uiMgr.isAsleep();
      boolean currSS = uiMgr.isCurrUIScreenSaver();
      boolean fullScreenVideo = uiMgr.getRootPanel().didLastRenderHaveFullScreenVideo();
      if (Sage.DBG) System.out.println("Screen Saver Timeout expired....tv=" + isTV + " playin=" + isPlayin +
          " fs=" + fs + " sleepy=" + sleepy + " currSS=" + currSS + " fsVideo=" + fullScreenVideo);
      if ((!isTV || (isTV && !isPlayin)) && fs && !sleepy && !currSS)
      {
        if (Sage.DBG) System.out.println("Launching screen saver due to inactivity");
        uiMgr.advanceUI("Screen Saver");
        cancel();
      }
      else if (isTV && fs && !sleepy && !currSS && !fullScreenVideo &&
          uiMgr.getBoolean("ui/enable_ss_on_windowed_video", true))
      {
        // If we're not watching full screen video then issue the command to exit to full screen video
        if (Sage.DBG) System.out.println("Switching to full screen video due to inactivity");
        uiMgr.doUserEvent(UserEvent.TV);
        cancel();
      }
    }
  }
}
