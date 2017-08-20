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

import sage.Catbert.AsyncTaskID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SageTV implements Runnable
{
  public static final String UPTIME = "uptime";
  public static final String MAX_UPTIME = "max_uptime";
  public static String hostname = null;
  public static final String SAGETV_PORT = "sagetv_port";
  public static final String AUTOCONNECT_SERVER_HOSTNAME = "autoconnect_server_hostname";
  public static final String SINGLE_CONNECT_SERVER_HOSTNAME = "single_connect_server_hostname";
  public static final int DEFAULT_SAGETV_PORT = 42024;
  private static final String SYNC_SYSTEM_CLOCK = "sync_system_clock";

  // External status strings written out by SageTV server mode
  public static final String SAGETV_SERVER_LOADING = "SAGETV_SERVER_LOADING";
  public static final String SAGETV_SERVER_LOADED = "SAGETV_SERVER_LOADED";

  // External status strings written out by SageTV client mode
  public static final String SAGETV_CLIENT_LOADING = "SAGETV_CLIENT_LOADING";
  public static final String SAGETV_CLIENT_SERVER_NOT_FOUND = "SAGETV_CLIENT_SERVER_NOT_FOUND";
  public static final String SAGETV_CLIENT_SERVER_LOADING = "SAGETV_CLIENT_SERVER_LOADING";
  public static final String SAGETV_CLIENT_CONNECTING = "SAGETV_CLIENT_CONNECTING";
  public static final String SAGETV_CLIENT_UNAUTHORIZED = "SAGETV_CLIENT_UNAUTHORIZED";
  public static final String SAGETV_CLIENT_VERSION_MISMATCH = "SAGETV_CLIENT_VERSION_MISMATCH";
  public static final String SAGETV_CLIENT_CONNECTED = "SAGETV_CLIENT_CONNECTED";

  // Details for external status states written out by SageTV client mode
  public static final String SYNCING_PROPERTIES = "SYNCING_PROPERTIES";
  public static final String DOWNLOADING_DB = "DOWNLOADING_DB";
  public static final String SYNCING_EPG_PROFILE = "SYNCING_EPG_PROFILE";
  public static final String FINALIZING_CONNECTION = "FINALIZING_CONNECTION";

  public static boolean PERF_TIMING = Sage.PERF_ANALYSIS;

  public static final String LINUX_ROOT_MEDIA_PATH = "/var/media";
  //public static final String LINUX_ROOT_CONFIG_PATH = "/opt/sagetv/usr";

  public static final boolean ALLOW_JAVA_KEYS = true;

  // CHANGE THIS TO FALSE FOR 3RD PARTY BUILDS
  public static final boolean ENABLE_EXT_API = true;


  public static EPGDBPublic getPublicDB()
  {
    return Wizard.getInstance();
  }
  public static Object api(String methodName, Object[] methodArgs) throws java.lang.reflect.InvocationTargetException
  {
    if (!ENABLE_EXT_API) throw new IllegalArgumentException();
    try
    {
      return Catbert.evaluateAction(null, methodName, methodArgs);
    }
    catch (Throwable e)
    {
      throw new java.lang.reflect.InvocationTargetException(e);
    }
  }
  public static Object apiUI(String uiContext, String methodName, Object[] methodArgs) throws java.lang.reflect.InvocationTargetException
  {
    if (!ENABLE_EXT_API) throw new IllegalArgumentException();
    // See if this is for a SageTVClient or not
    if (uiContext != null)
    {
      if(!Sage.client && NetworkClient.isConnectedClientContext(uiContext)) {
        try
        {
          return NetworkClient.clientEvaluateAction(uiContext, methodName, methodArgs);
        }
        catch (Throwable e)
        {
          throw new java.lang.reflect.InvocationTargetException(e);
        }
      }
    }
    try
    {
      return Catbert.evaluateAction(uiContext, methodName, methodArgs);
    }
    catch (Throwable e)
    {
      throw new java.lang.reflect.InvocationTargetException(e);
    }
  }

  private static SageTV chosenOne = null;
  public static SageTV getInstance() { return chosenOne; }
  public static boolean upgrade = false;
  public static String upgradedFromVersion = "";
  public static String system = "SageTV";
  public static boolean knownLocalhostClient = false;
  public SageTV()
  {
    chosenOne = this;
    writeOutStateInfo(Sage.client ? SageTV.SAGETV_CLIENT_LOADING : SageTV.SAGETV_SERVER_LOADING, null, null);
    writeOutWatchdogFile();

    if (!Sage.WINDOWS_OS)
    {
      if(!Sage.client) {
        new java.io.File("/var/run/sagetv.pid").deleteOnExit();
      } else {
        new java.io.File("/var/run/sagetvclient.pid").deleteOnExit();
      }
    }
    upgrade = !UIManager.SAGE.equals(Sage.get("version", ""));
    if (upgrade && !"".equals(Sage.get("version", "")))
    {
      upgradedFromVersion = Sage.get("version", "");
      if (Sage.DBG) System.out.println("Backing up properties file for SageTV upgrade...");
      Sage.backupPrefs("." + upgradedFromVersion);
    }

    system = Sage.get("system", "SageTV");
    alive = true;
    startTime = Sage.time();
    Sage.put("version", UIManager.SAGE);
    if (Sage.DBG) System.out.println(UIManager.SAGE);

    String localIP = "";
    try
    {
      localIP = java.net.InetAddress.getLocalHost().getHostAddress();
      hostname = java.net.InetAddress.getLocalHost().getHostName();
    }
    catch (Exception e)
    {
      System.out.println("ERROR resolving hostname:" + e);
    }
    if (hostname == null)
      hostname = "localhost";
    if (Sage.get("hostname", "").length() > 0)
      hostname = Sage.get("hostname", "");
    if (Sage.DBG) System.out.println("hostname=" + hostname);
    syncSystemClock = Sage.getBoolean(SYNC_SYSTEM_CLOCK, true);

    PERF_TIMING = Sage.getBoolean("performance_timing", false);

    netConfigDone = true;

    String serverIP = null;
    // We want to use the full video frame class
    // Seeker & MMC must come after the Wizard because their prime accesses the DB
    // MMC must also be before Seeker because recording techniques depend on the hardware
    // The perfect spot to sync us up with the server for client mode....
    boolean goAheadWithDeadServer = false;
    if (Sage.client)
    {
      if (Sage.preferredServer != null)
      {
        serverIP = Sage.preferredServer;
        goAheadWithDeadServer = true;
      }
      else if (Sage.get(AUTOCONNECT_SERVER_HOSTNAME, "").length() > 0)
      {
        serverIP = Sage.get(AUTOCONNECT_SERVER_HOSTNAME, "");
        goAheadWithDeadServer = true;
      }
      else if (Sage.get(SINGLE_CONNECT_SERVER_HOSTNAME, "").length() > 0)
      {
        serverIP = Sage.get(SINGLE_CONNECT_SERVER_HOSTNAME, "");
        goAheadWithDeadServer = true;
        Sage.put(SINGLE_CONNECT_SERVER_HOSTNAME, "");
      }
      else
      {
        Sage.setSplashText(Sage.rez("Discovering_SageTV_Servers"));
        NetworkClient.ServerInfo[] servers = NetworkClient.discoverServers(Sage.getInt("discovery_timeout", 5000));
        if (Sage.getBoolean("autodiscover_servers", true) && servers.length > 0)
        {
          String[] selectionValues = new String[servers.length];
          for (int i = 0; i < servers.length; i++)
            selectionValues[i] = servers[i].toString();

          javax.swing.JComboBox jcb = new javax.swing.JComboBox(selectionValues);
          jcb.setEditable(true);
          javax.swing.JPanel jpan = new javax.swing.JPanel();
          jpan.setLayout(new java.awt.GridLayout(2, 1));
          jpan.add(new javax.swing.JLabel(Sage.rez("Select_SageTV_Server_or_Enter_Server_Address")));
          jpan.add(jcb);
          if (javax.swing.JOptionPane.showOptionDialog(null, jpan,
              Sage.rez("Enter_Server_Address"), javax.swing.JOptionPane.OK_CANCEL_OPTION,
              javax.swing.JOptionPane.QUESTION_MESSAGE, null, null, null) == javax.swing.JOptionPane.OK_OPTION)
          {
            Object selIP = jcb.getSelectedItem();
            if (selIP != null)
            {
              serverIP = selIP.toString();
              serverIP = serverIP.substring(serverIP.lastIndexOf(',') + 1);
            }
            else
              exit();
          }
          else
            exit();
        }
        else
        {
          serverIP = javax.swing.JOptionPane.showInputDialog(Sage.rez("Enter_Server_Address") + ":",
              Sage.get(AUTOCONNECT_SERVER_HOSTNAME, ""));
        }
        if (serverIP == null || serverIP.length() == 0)
        {
          exit();
        }
      }
      // NOTE: JAK 8/12/05 We have to init the UIManager before we load the DB. Otherwise on Linux the
      // security key won't have been set yet and the STV won't load correctly AND the client/server
      // connection won't establish.
      //VideoFrame.getInstance();
      Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("User_Interface_Manager") }));
      // If we're not headless then we always create a local UI
      // But we need to do this in order for the key loading to work!
      //			if (!Sage.isHeadless())
      UIManager.getLocalUI();
      Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("Object_Database") }));
      Wizard.prime();
      Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.isTrueClient() ?
          Sage.rez("SageTV_Connection") : Sage.rez("SageTV_Service")}));
      if (goAheadWithDeadServer)
      {
        if (Sage.isTrueClient())
        {
          // See if we have the server MAC information in order to send out WOL packets to it
          // Do this before we try to connect, because the server can come up in time in response to this request in order for the connection
          // to succeed on the initial attempt
          String serverMac = Sage.get("server_mac/" + serverIP, null);
          if (serverMac != null)
            IOUtils.sendWOLPacket(serverMac);
          neddy = NetworkClient.connectToServer(serverIP, false, true);
          if (!neddy.isClientConnected() && serverMac != null && serverMac.length() > 0)
          {
            // Do this 2 more times in case it dropped the UDP packets
            IOUtils.sendWOLPacket(serverMac);
            try{Thread.sleep(50);}catch(Exception e){}
            IOUtils.sendWOLPacket(serverMac);
          }
        }
        else
        {
          // Running it locally to connect to service, just keep trying to connect.
          while ((neddy = NetworkClient.connectToServer(serverIP, false, false)) == null)
          {
            try{Thread.sleep(2500);}catch(Exception e){}
          }
        }
      }
      else
      {
        while ((neddy = NetworkClient.connectToServer(serverIP, true, false)) == null)
        {
          serverIP = javax.swing.JOptionPane.showInputDialog(Sage.rez("Enter_Server_Address") + ":",
              Sage.get(AUTOCONNECT_SERVER_HOSTNAME, ""));
          if (serverIP == null || serverIP.length() == 0)
          {
            exit();
          }
        }
      }

      Sage.preferredServer = serverIP;

      // Determine if we're a client connected to a server on a different machine or not
      Sage.nonLocalClient = (!"127.0.0.1".equals(serverIP) && !"localhost".equals(serverIP) && !localIP.equals(serverIP) &&
          !hostname.equals(serverIP));

      if (Sage.isTrueClient())
      {
        // Get the MAC address of the server and store that so we can do WOL later
        Thread macT = new Thread()
        {
          public void run()
          {
            String serverMac = IOUtils.getServerMacAddress(Sage.preferredServer);
            if (serverMac == null)
            {
              // Try one more time
              serverMac = IOUtils.getServerMacAddress(Sage.preferredServer);
            }
            if (serverMac != null)
            {
              // Store this for later usage
              Sage.put("server_mac/" + Sage.preferredServer, serverMac);
            }
          }
        };
        macT.setDaemon(true);
        macT.start();
      }
    }
    else
    {
      //VideoFrame.getInstance();
      Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("User_Interface_Manager") }));
      //if (!Sage.isHeadless())
      UIManager.getLocalUI();
      Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("Object_Database") }));
      Wizard.prime();
    }

    writeOutWatchdogFile();

    // Do this AFTER the DB initialization so we don't screw around with the MAC address
    if (!Sage.WINDOWS_OS && Sage.getBoolean("linux/configure_networking", false))
    {
      if (Sage.DBG) System.out.println("Establishing network setup...");
      LinuxUtils.reconfigureNetworking();
    }

    // We want to build the plugin event system early so it doesn't lose any events. Then we start it up after
    // all of the plugin objects are actually loaded.
    sage.plugin.PluginEventManager.getInstance();

    Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("EPG") }));
    guide = EPG.prime();
    //		System.gc();
    Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("Profiler") }));
    god = Carny.prime();
    sage.msg.MsgManager.getInstance();
    System.gc();
    Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("Acquisition_System") }));
    mmc = MMC.getInstance();

    if (Sage.client)
      mmc.addCaptureDeviceManager(new ClientCaptureManager());
    else
    {
      mmc.addCaptureDeviceManager(new NetworkEncoderManager());
      if ((Sage.MAC_OS_X || Sage.LINUX_OS)) {
        try {
          mmc.addCaptureDeviceManager(new HDHomeRunCaptureManager());
        } catch (Throwable t) {
          System.out.println("ERROR instantiating HDHomeRun capture manager: " + t);
        }
      }
      if (Sage.WINDOWS_OS)
        mmc.addCaptureDeviceManager(new DShowCaptureManager());
      else if (Sage.LINUX_OS)
      {
        if (!Sage.getBoolean("linux/enable_vweb_capture",false))
        {
          mmc.addCaptureDeviceManager(new LinuxIVTVCaptureManager());
        }
        if (!Sage.getBoolean("linux/disable_dvb_capture",false))
        {
          mmc.addCaptureDeviceManager(new LinuxDVBCaptureManager());
        }
        if (Sage.getBoolean("linux/enable_firewire_capture",false))
        {
          mmc.addCaptureDeviceManager(new LinuxFirewireCaptureManager());
        }
      }
      else if (Sage.MAC_OS_X)
      {
        // TODO: roll Trinity into MacNative...
        mmc.addCaptureDeviceManager(new MacTrinityCaptureManager());
        try {
          mmc.addCaptureDeviceManager(new MacNativeCaptureManager());
        } catch (Throwable t) {
          System.out.println("ERROR instantiating Mac native capture manager: " + t);
        }
      }
      else
      {
        String customCapDevMgr = Sage.get("mmc/custom_capture_device_mgr", "");
        if (customCapDevMgr.length() > 0)
        {
          try
          {
            mmc.addCaptureDeviceManager((CaptureDeviceManager)Class.forName(customCapDevMgr).newInstance());
          }
          catch (Exception e)
          {
            System.out.println("ERROR instantiating custom capture device mgr \"" + customCapDevMgr + "\" of " + e);
          }
        }
      }
    }
    mmc.prime();

    //		System.gc();
    Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("SageTV_Core") }));
    mySeeker = SeekerSelector.prime();
    //		System.gc();

    Ministry.getInstance();
    if (Sage.WINDOWS_OS)
      DShowTVPlayer.autoOptimize(true);

    Thread t = new Thread(this, "SageTV");
    t.setDaemon(true);
    t.start();

    if ((Sage.LINUX_OS && (UIManager.getLocalUI() == null || UIManager.getLocalUI().getGlobalFrame() == null)))
    {
      // NOTE: Keep this thread alive or the JVM will terminate!!!
      while (!dead)
      {
        try
        {
          Thread.sleep(2000);
        }
        catch (Exception e){}
      }
    }
  }

  public void run()
  {
    Runtime.getRuntime().addShutdownHook(new Thread("SageTV Shutdown")
    {
      public void run()
      {
        if (Sage.DBG) System.out.println("SageTV SHUTDOWN is activating!");
        if (Sage.WINDOWS_OS)
        {
          java.awt.EventQueue.invokeLater(new Runnable()
          {
            public void run()
            {
              SageTV.this.exit(false);
            }
          });
        }
        else
        {
          SageTV.this.exit(false);
        }
      }
    });

    if (!Sage.getBoolean("disable_carny_init", false))
      god.lengthyInit(true);
    //		System.gc();

    Thread t = new Thread(mySeeker, "Seeker");
    //t.setDaemon(true);
    t.start();

    sage.msg.MsgManager.getInstance().init();
    t = new Thread(sage.msg.MsgManager.getInstance(), "MsgManager");
    t.setDaemon(true);
    t.start();


    if (!Sage.client)
      Ministry.getInstance().spawn();

    /*		t = new Thread(VideoFrame.getInstance(), "VideoFrame");
		t.setDaemon(true);
		t.start();
     */
    //t = new Thread(mmc, "MMC");
    //t.setDaemon(true);
    //t.start();

    t = new Thread(guide, "EPG");
    t.setDaemon(true);
    t.setPriority(Thread.MIN_PRIORITY);
    t.start();

    t = new Thread(god, "Carny");
    t.setDaemon(true);
    t.setPriority(Thread.MIN_PRIORITY);
    t.start();

    /*
     * NOTE: We have to launch the MediaServer before the Seeker finished it's startup. Otherwise a network encoder could try
     * to start a recording and the MediaServer won't be available to get the upload ID yet and all will go to hell
     */
    // We always need the local server for the cases when we have to send active files through the MediaServer
    localServerEnabled = !Sage.client;//Sage.isHeadless();
    // Disable the non-local server for now for the embedded systems
    serverEnabled = !Sage.client && Sage.getBoolean("enable_server", true);
    // Narflex - 02/24/2012 - Since we now have Qian's server code handling all the streaming there should no
    // longer be any reason why we run these servers on SageTVClient on embedded aside from if we added
    // filedownloader playback support later (7818 and 42024 servers)
    if (serverEnabled || localServerEnabled)
    {
      launchMediaServer();
    }

    t = new Thread(myScheduler = SchedulerSelector.getInstance(), "Scheduler");
    t.setDaemon(true);
    t.setPriority(Thread.MIN_PRIORITY);
    t.start();

    if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
    {
      PowerManagement pm = ServerPowerManagement.getInstance();
      pm.setLogging(Sage.DBG && Sage.getBoolean("debug_pm", false));
      // We need to make this small enough so that temporary states where we say we don't need power
      // can happen; but the next update will set the state properly (things like file transitions where we're temporarily not playing can do this)
      pm.setWaitTime(Sage.getLong("power_management_wait_time_new", Math.min(Sage.getLong("power_management_wait_time", 30000), 10000)));
      pm.setIdleTimeout(Sage.getLong("power_management_idle_timeout", 120000));
      pm.setPrerollTime(Sage.getLong("power_management_wakeup_preroll", 120000));
      t = new Thread(pm, "PowerManagement");
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
    }

    Sage.setSplashText(Sage.rez("SageTV_Init_Wait"));
    int maxSeekWait = 30000; // Up this back to 30 seconds from 10....if there's a really nasty schedule I want to be sure it's generated
    // before we go into the UI and fail to display default video.
    while (!Sage.client && !mySeeker.isPrepped() && maxSeekWait > 0 && mmc.getConfiguredCaptureDevices().length > 0)
    {
      try{Thread.sleep(250);}catch(Exception e){}
      maxSeekWait -= 250;
    }

    // Don't launch the servers that could allow a UI connection until we've got the seeker intialized
    if (serverEnabled || localServerEnabled)
    {
      launchServer();
      launchExtraServers();
    }
    Sage.setSplashText(Sage.rez("Module_Init", new Object[] { Sage.rez("User_Interface_Manager") }));
    if (!Sage.isHeadless())
      java.awt.EventQueue.invokeLater(UIManager.getLocalUI());

    if (!Sage.client && Sage.getBoolean("enable_encoding_server", false))
    {
      String portsString = Sage.get("encoding_server_port", "6969");
      java.util.StringTokenizer toker = new java.util.StringTokenizer(portsString, ",;");
      while (toker.hasMoreTokens())
      {
        try
        {
          int porty = Integer.parseInt(toker.nextToken());
          t = new Thread(new EncodingServer(porty), "EncodingServer");
          t.setDaemon(true);
          t.start();
        }
        catch (NumberFormatException e)
        {
        }
        // We no longer do this on multiple ports
        break;
      }
    }

    //		System.gc();
    if (Sage.client && neddy != null)
    {
      neddy.clientIsActive();
      if (!neddy.isClientConnected())
      {
        java.awt.EventQueue.invokeLater(new Runnable()
          {
            public void run() { NetworkClient.communicationFailure(null); }
          });
      }
    }

    readyForClientConnections = true;

    // Setup the 'new device' scanner for hotplugged storage devices
    hotplugStorageDetector = NewStorageDeviceDetector.getInstance();
    if (Sage.getBoolean("enable_hotplug_storage_detector", true))
    {
      t = new Thread(hotplugStorageDetector, "HotplugStorage");
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
    }

    // Now instantiate the main objects that handle generalized SageTV Plugins
    if (Sage.DBG) System.out.println("Loading all core plugins...");
    sage.plugin.CorePluginManager.getInstance();
    if (Sage.DBG) System.out.println("Starting all core plugins...");
    sage.plugin.CorePluginManager.getInstance().startAllPlugins();
    if (Sage.DBG) System.out.println("Done starting core plugins.");

    // Launch any extra threads to load services that the user defined
    String extraThreads = Sage.get("load_at_startup_runnable_classes", "");
    java.util.StringTokenizer toker = new java.util.StringTokenizer(extraThreads, ";");
    while (toker.hasMoreTokens())
    {
      String x = toker.nextToken();
      x = x.replaceAll(" ", ""); // bugfix for nielm because this is a common config error
      try
      {
        if (Sage.DBG) System.out.println("Loading startup runnable:" + x);
        Runnable r = (Runnable) Class.forName(x, true, Sage.extClassLoader).newInstance();
        t = new Thread(r, "Startup-" + x);
        t.setDaemon(true);
        t.start();
        if (Sage.DBG) System.out.println("Loaded startup runnable:" + x);
      }
      catch (Throwable e)
      {
        System.out.println("ERROR Loading startup runnable extension of:" + e);
      }
    }
    sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.ALL_PLUGINS_LOADED, (Object[]) null);

    sage.plugin.PluginEventManager.getInstance().startup();
    allPluginsLoaded = true;

		// Delay the startup of this since its use of commons-logging may interfer with the log4j plugin loading
		if (psnatmgr != null)
			psnatmgr.start();
    if (!Sage.client)
      writeOutStateInfo(SageTV.SAGETV_SERVER_LOADED, null, null);
  }

  public static boolean isPluginStartupComplete() { return allPluginsLoaded; }
  private static boolean syncSystemClock;
  private static boolean allPluginsLoaded;

  public static boolean getSyncSystemClock()
  { return syncSystemClock; }
  public static void setSyncSystemClock(boolean x)
  { Sage.putBoolean(SYNC_SYSTEM_CLOCK, syncSystemClock = x); }

  public static MediaServer getMediaServer() { return mediaServer; }
  public static void forceLocatorUpdate()
  {
    if (Sage.DBG) System.out.println("Locator update system was kicked");
    if (locatorClient != null)
      locatorClient.kickLocator();
  }

  public static sage.locator.LocatorRegistrationClient getLocatorClient()
  {
    return locatorClient;
  }

  public static NewStorageDeviceDetector getHotplugStorageDetector()
  {
    return hotplugStorageDetector;
  }

  private static boolean localServerEnabled;
  private static boolean serverEnabled;
  private static java.net.ServerSocket serverSocket;
  private static java.net.DatagramSocket discoverySocket;
  private static java.net.DatagramSocket miniDiscoverySocket;
  private static MediaServer mediaServer;
  private static MiniServer miniServer;
  private static NewStorageDeviceDetector hotplugStorageDetector;
  private static void launchMediaServer()
  {
    mediaServer = new MediaServer();
    Thread t = new Thread(mediaServer, "MediaServer");
    t.setDaemon(true);
    t.setPriority(Thread.NORM_PRIORITY + Sage.getInt("media_server_thread_priority_offset", 2));
    t.start();
  }
  private static void launchServer()
  {
    Thread t = new Thread("SageTVServer")
    {
      public void run()
      {
        do
        {
          serverSocket = null;
          try
          {
            serverSocket = new java.net.ServerSocket(Sage.getInt(SAGETV_PORT, DEFAULT_SAGETV_PORT));
          }
          catch (java.io.IOException e)
          {
            System.out.println("Error creating server socket:" + e);
            if (!alive || (!serverEnabled && !localServerEnabled))
              return;
            else
            {
              if (Sage.DBG) System.out.println("Waiting to retry to open SageTV Server socket...");
              try{Thread.sleep(15000);}catch(Exception e1){}
            }
          }
        } while (serverSocket == null);
        if (Sage.DBG) System.out.println("SageTVServer was instantiated loadDelay=" + (Sage.time() - startTime)/1000.0 +
            " sec");
        while (alive && (serverEnabled || localServerEnabled) && serverSocket != null)
        {
          try
          {
            java.net.Socket sake = serverSocket.accept();
            // Check for a local connection
            if (alive && (serverEnabled ||
                (localServerEnabled && IOUtils.isLocalhostSocket(sake))))
            {
              NetworkClient.acceptFromClient(sake);
            }
            else
            {
              if (Sage.DBG) System.out.println("Server rejecting connection from: " + sake);
              sake.close();
              //serverSocket.close();
            }
          }
          catch (java.io.IOException e)
          {
            if (alive)
              System.out.println("Error w/SageTV client connection:" + e);
            try{Thread.sleep(100);}catch(Exception e1){} // if its closing, let it close
          }
        }
        try
        {
          serverSocket.close();
        }catch (Exception e){}
      }
    };
    t.setDaemon(true);
    t.start();
  }
  private static void launchExtraServers()
  {
    Thread t = new Thread("SageTVDiscoveryServer")
    {
      public void run()
      {
        discoverySocket = null;
        while (alive && (serverEnabled || localServerEnabled) && discoverySocket == null)
        {
          try
          {
            discoverySocket = new java.net.DatagramSocket(Sage.getInt("discovery_port", 8270));
            //discoverySocket.setBroadcast(true);
          }
          catch (java.io.IOException e)
          {
            System.out.println("Error creating discovery socket:" + e);
            try{Thread.sleep(15000);}catch(Exception e1){}
          }
        }
        if (Sage.DBG) System.out.println("SageTVDiscoveryServer was instantiated.");
        while (alive && (serverEnabled || localServerEnabled) && discoverySocket != null)
        {
          try
          {
            java.net.DatagramPacket packet = new java.net.DatagramPacket(new byte[512], 512);
            discoverySocket.receive(packet);
            if (Sage.DBG) System.out.println("Server got broadcast packet: " + packet);
            if (alive && serverEnabled)
            {
              // The first 3 bytes should be STV, and then the next 3 bytes are the version info.
              // It sends 32 bytes so we don't have issues with not having enough data for it to flush or whatever
              if (packet.getLength() >= 6)
              {
                byte[] data = packet.getData();
                if (data[0] == 'S' && data[1] == 'T' && data[2] == 'V')
                {
                  byte majVer = data[3];
                  byte minVer = data[4];
                  byte buildVer = data[5];
                  if (majVer > Sage.CLIENT_COMPATIBLE_MAJOR_VERSION ||
                      (majVer == Sage.CLIENT_COMPATIBLE_MAJOR_VERSION &&
                      (minVer > Sage.CLIENT_COMPATIBLE_MINOR_VERSION ||
                          (minVer == Sage.CLIENT_COMPATIBLE_MINOR_VERSION &&
                          buildVer >= Sage.CLIENT_COMPATIBLE_MICRO_VERSION))))
                  {
                    // Compatible version, send back the response with our version info in there
                    data[3] = Version.MAJOR_VERSION;
                    data[4] = Version.MINOR_VERSION;
                    data[5] = Version.MICRO_VERSION;
                    // 2 bytes for the port
                    int sagetvport = Sage.getInt(SAGETV_PORT, DEFAULT_SAGETV_PORT);
                    data[6] = (byte)((sagetvport >> 8) & 0xFF);
                    data[7] = (byte)(sagetvport & 0xFF);
                    // Use the hostname for our description currently
                    String desc = SageTV.hostname;
                    byte[] descBytes = desc.getBytes(Sage.I18N_CHARSET);
                    data[8] = (byte)descBytes.length;
                    System.arraycopy(descBytes, 0, data, 9, descBytes.length);
                    data[9+descBytes.length]=1;
                    packet.setLength(9 + descBytes.length + 1);
                    if (Sage.DBG) System.out.println("Server sent back discovery data:" + packet);
                    discoverySocket.send(packet);
                  }
                }
              }
            }
          }
          catch (java.io.IOException e)
          {
            if (alive)
              System.out.println("Error w/SageTV client connection:" + e);
            try{Thread.sleep(100);}catch(Exception e1){} // if its closing, let it close
          }
        }
        try
        {
          discoverySocket.close();
        }catch (Exception e){}
      }
    };
    t.setDaemon(true);
    t.start();

    if (serverEnabled && Sage.getBoolean("enable_media_extender_server", true))
    {
      launchMiniDiscoveryServer();
      miniServer = new MiniServer();
      miniServer.StartServer();

      // Does the registration with the Locator system. It has a property which it reads that allows disabling of it.
      try
      {
        locatorClient = new sage.locator.LocatorRegistrationClient();
      }
      catch (Exception e)
      {
        System.out.println("ERROR: Unable to create Locator registration client:" + e);
      }

      // Setup our UPnP router manager
      if (psnatmgr == null)
      {
        try
        {
          psnatmgr = (AbstractedService)Class.forName("sage.upnp.PlaceshifterNATManager").newInstance();
          psnatmgr.start();
        }
        catch (Exception e)
        {
          System.out.println("ERROR instantiating sage.upnp.PlaceshifterNATManager of " + e);
        }
      }
    }
    else if (localServerEnabled)
    {
      MiniClientSageRenderer.startUIServer(serverEnabled);
    }
  }

  private static void launchMiniDiscoveryServer()
  {
    Thread t = new Thread("SageTVMiniDiscoveryServer")
    {
      public void run()
      {
        miniDiscoverySocket = null;
        while (alive && serverEnabled && miniDiscoverySocket == null)
        {
          try
          {
            miniDiscoverySocket = new java.net.DatagramSocket(Sage.getInt("mini_discovery_port", 31100));
            //discoverySocket.setBroadcast(true);
          }
          catch (java.io.IOException e)
          {
            System.out.println("Error creating discovery socket:" + e);
            try{Thread.sleep(15000);}catch(Exception e1){}
          }
        }
        if (Sage.DBG) System.out.println("SageTVMiniDiscoveryServer was instantiated.");
        MiniClientSageRenderer.startUIServer(serverEnabled);
        while (alive && serverEnabled && miniDiscoverySocket != null)
        {
          try
          {
            java.net.DatagramPacket packet = new java.net.DatagramPacket(new byte[512], 512);
            miniDiscoverySocket.receive(packet);
            if (Sage.DBG) System.out.println("Server got broadcast packet: " + packet);
            // The first 3 bytes should be STV, and then the next 3 bytes are the version info.
            if (packet.getLength() >= 10)
            {
              byte[] data = packet.getData();
              // If we're on EMBEDDED this is also fat client discovery if [4]==99
              if (data[0] == 'S' && data[1] == 'T' && data[2] == 'V' && data[3] == 1)
              {
                data[3]= (byte)2;
                data[4] = 1;
                int packLength = 15;
                long systemGuid = sage.locator.LocatorRegistrationClient.getSystemGuid();
                data[5] = (byte)((systemGuid >> 56) & 0xFF);
                data[6] = (byte)((systemGuid >> 48) & 0xFF);
                data[7] = (byte)((systemGuid >> 40) & 0xFF);
                data[8] = (byte)((systemGuid >> 32) & 0xFF);
                data[9] = (byte)((systemGuid >> 24) & 0xFF);
                data[10] = (byte)((systemGuid >> 16) & 0xFF);
                data[11] = (byte)((systemGuid >> 8) & 0xFF);
                data[12] = (byte)(systemGuid & 0xFF);
                // We're also going to add another byte for version (currently 1)
                // Then we'll add another 8 bytes for the server ID (which is the locator ID)
                // Then we'll also add another 2 bytes for the port (usually 31099)
                int thePort = Sage.getInt("extender_and_placeshifter_server_port", 31099);
                data[13] = (byte)((thePort >> 8) & 0xFF);
                data[14] = (byte)(thePort & 0xFF);
                packet.setLength(packLength);
                if (Sage.DBG) System.out.println("Server sent back mini discovery data:" +
                    packet + " to " + packet.getAddress() + " " + packet.getPort());
                miniDiscoverySocket.send(packet);
                // Convert the MAC address to string
                /*String str = "";
								for(int i=0;i<6;i++)
								{
									int val=data[4+i]&0xFF;
									if(val<0x10) str+="0";
									if(val==0)
									{
										str+="0";
									}
									else
									{
										str+=Integer.toHexString(val);
									}
									//if(i<5)
									//str+=":";
								}*/
                //UIManager.createAltUI(str);
              }
            }
          }
          catch (java.io.IOException e)
          {
            System.out.println("Error w/SageTV client connection:" + e);
            try{Thread.sleep(100);}catch(Exception e1){} // if its closing, let it close
          }
        }
        try
        {
          miniDiscoverySocket.close();
        }catch (Exception e){}
      }
    };
    t.setDaemon(true);
    t.start();
  }

  private static void killServer()
  {
    if (serverSocket != null)
    {
      try
      {
        serverSocket.close();
      }catch(Exception e){}
      serverSocket = null;
    }
    if (mediaServer != null)
    {
      mediaServer.kill();
      mediaServer = null;
    }
    NetworkClient.killAll();
  }

  private static void killExtraServers()
  {
    if (discoverySocket != null)
    {
      try
      {
        discoverySocket.close();
      }catch(Exception e){}
      discoverySocket = null;
    }
    if (miniDiscoverySocket != null)
    {
      try
      {
        miniDiscoverySocket.close();
      }catch(Exception e){}
      miniDiscoverySocket = null;
    }
    if (miniServer != null)
    {
      miniServer.killServer();
      miniServer = null;
    }
    if (locatorClient != null)
    {
      locatorClient.kill();
      locatorClient = null;
    }

    MiniClientSageRenderer.stopUIServer();
  }

  public static void enableServer(boolean x)
  {
    if (Sage.client) return;
    if (x == chosenOne.serverEnabled) return;
    chosenOne.serverEnabled = x;
    Sage.putBoolean("enable_server", x);
    if (!chosenOne.localServerEnabled)
    {
      if (x)
      {
        chosenOne.launchMediaServer();
        chosenOne.launchServer();
        chosenOne.launchExtraServers();
      }
      else
      {
        chosenOne.killServer();
        chosenOne.killExtraServers();
      }
    }
    else
    {
      // Reload the extra servers so they can acknowledge the state change
      chosenOne.killExtraServers();
      chosenOne.launchExtraServers();
    }
  }

  private static final Object stateInfoLock = new Object();
  private static String lastMainStatus = null;
  public static void writeOutStateInfo(String mainStatus, String detail0, String detail1)
  {
    return;
  }

  public static void deepSleep(boolean x)
  {
    try
    {
      if (Sage.DBG) System.out.println("SageTV.deepSleep(" + x + ")");
      if (x == poweringDown) return; // same state
      if (!x)
      {
        if (!Sage.client && Sage.WINDOWS_OS && Sage.getBoolean("restart_after_standby_resume", false))
        {
          if (Sage.DBG) System.out.println("Forcing SageTV restart after resuming from standby");
          Pooler.execute(new Runnable(){public void run(){ restart(); }});
          return;
        }
        if (!Sage.client)
        {
          if (Sage.DBG) System.out.println("SageTV pausing on wakeup to let drivers load properly...");
          try{Thread.sleep(Sage.getLong("pm_resume_load_delay", 15000)); }catch(Exception e){}
          if (Sage.DBG) System.out.println("SageTV done waiting on wakeup to let drivers load properly.");
        }
        // On power resumption the Scheduler will automatically reload the devices. The 3D system will
        // also auto-rebuild itself when it detects the device failure after power resume.
        // IR xmt devs will get reloaded by the cap devs. We also need to reload IR recv devices.
        poweringDown = x;
        if (Sage.client)
        {
          if (SageTV.neddy != null)
          {
            Pooler.execute(new Runnable(){public void run(){ NetworkClient.communicationFailure(SageTV.neddy.getSN()); } });
          }
        }
        else
        {
          SchedulerSelector.getInstance().kick(false);
          SeekerSelector.getInstance().kick();
        }
        java.util.Iterator walker = UIManager.getUIIterator();
        while (walker.hasNext())
          ((UIManager) walker.next()).deepSleep(x);
      }
      else
      {
        poweringDown = x;
        if (Sage.DBG) System.out.println("System power is suspending");
        Sage.savePrefs();
        java.util.Iterator walker = UIManager.getUIIterator();
        while (walker.hasNext())
          ((UIManager) walker.next()).deepSleep(x);
        if (!Sage.client)
        {
          SeekerSelector.getInstance().releaseAllEncoding();
          SchedulerSelector.getInstance().prepareForStandby();
          // Also release all IR xmt devices
          ExternalTuningManager.goodbye();
        }
      }
      if (Sage.DBG) System.out.println("SageTV.deepSleep(" + x + ") is done");
    }
    catch (Throwable t)
    {
      System.out.println("ERROR w/ deep sleep of:" + t);
      t.printStackTrace();
    }
  }

  public static boolean isAlive()
  {
    return alive;
  }

  public static boolean poweringDown;
  public static boolean isPoweringDown() { return poweringDown; }
  public static void reboot()
  {
    exit(true, 26);
  }
  public static void shutdown()
  {
    exit(true, 22);
  }
  // Creates the restart indicator file in order to ensure proper pseudo-client/server restart synchronization
  public static void createRestartFile()
  {
    try
    {
      java.io.FileOutputStream fos = new java.io.FileOutputStream(Sage.isHeadless() ? "restartsvc" : (Sage.client ? "restartclient" : "restart"));
      fos.getFD().sync();
      fos.close();
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR cannot create '" + (Sage.isHeadless() ? "restartsvc" : (Sage.client ? "restartclient" : "restart")) + "' file! " + e);
    }
  }
  // Restart application (reboot restarts the system)
  public static void restart()
  {
    // Create the indicator file to know we want to be restarted
    createRestartFile();
    // See if we need to force a restart on a locally running client as well
    if (!Sage.client && (new java.io.File("stagedrenames.txt").isFile() || new java.io.File("stageddeletes.txt").isFile()))
    {
      if (Sage.DBG) System.out.println("Sending restart command to any localhost connected clients...");
      NetworkClient.sendRestartToLocalClients();
    }
    if (Sage.client)
    {
      // Set the property to indicate which server we should reconnect to after the restart
      if (Sage.get(SageTV.AUTOCONNECT_SERVER_HOSTNAME, "").length() == 0)
        Sage.put(SageTV.SINGLE_CONNECT_SERVER_HOSTNAME, Sage.preferredServer);
    }
    exit(true, 0);
  }
  public static void exit() { exit(true); }
  public static void exit(boolean killSys)
  {
    exit(killSys, 0);
  }
  public static void exit(boolean killSys, int exitCode)
  {
    if (!alive)
      return;
    alive = false;
    if (Sage.WINDOWS_OS)
      Sage.releaseSystemHooks0(Sage.mainHwnd);
    if (Sage.DBG) System.out.println("Sage.exit() called.");
    if (Sage.DBG) System.out.println("Stopping all core plugins");
    sage.plugin.CorePluginManager.getInstance().stopAllPlugins();
    if (Sage.DBG) System.out.println("Destroying all core plugins");
    sage.plugin.CorePluginManager.getInstance().destroyAllPlugins();
    if (Sage.poppy != null)
      Sage.poppy.dispose();
    if (!Sage.client && neddy != null)
      neddy.startCleanup();
    if (Sage.DBG) System.out.println("Cleaning up servers");
    if (!Sage.client)
    {
      killServer();
      killExtraServers();
    }
    if (Sage.DBG) System.out.println("Cleaning up PM");
    if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
      ServerPowerManagement.getInstance().goodbye();
    if (psnatmgr != null)
      psnatmgr.kill();
    if (god != null)
      god.goodbye();
    if (Sage.DBG) System.out.println("Killed Carny.");
    if (myScheduler != null)
      myScheduler.goodbye();
    if (Sage.DBG) System.out.println("Killed Scheduler.");
    if (mySeeker != null)
      mySeeker.goodbye();
    if (Sage.DBG) System.out.println("Killed Seeker.");
    if (Sage.client && neddy != null)
      neddy.startCleanup();
    if (guide != null)
      guide.goodbye();
    if (Sage.DBG) System.out.println("Killed EPG.");
    if (mmc != null)
      mmc.goodbye();
    if (Sage.DBG) System.out.println("Killed MMC.");
    sage.msg.MsgManager.getInstance().kill();
    java.util.Iterator uiWalker = UIManager.getUIIterator();
    while (uiWalker.hasNext())
      ((UIManager) uiWalker.next()).goodbye();
    if (Sage.DBG) System.out.println("Killed UIManager.");
    Wizard.getInstance().goodbye();
    if (Sage.DBG) System.out.println("Killed Wizard.");
    if (neddy != null)
      neddy.finishCleanup();
    Sage.disconnectInternet();
    Sage.putLong(UPTIME, Math.max(0, Sage.getLong(UPTIME, 0) + Math.max(0, (Sage.time() - startTime))));
    Sage.putLong(MAX_UPTIME, Math.max(Sage.getLong(MAX_UPTIME, 0), Sage.getLong(UPTIME, 0)));
    Sage.savePrefs();
    dead = true;
    if (Sage.DBG) System.out.println("Bye-bye.");
    Sage.postKillMsg();
    if (Sage.getBoolean("quit_jvm_on_exit", true) && killSys)
      System.exit(exitCode);
  }

  public static final String oneWayEncrypt(String x)
  {
    if (x == null)
    {
      return "";
    }
    else if (x.length() == 0)
    {
      return "";
    }
    byte[] buf = x.getBytes();
    java.security.MessageDigest algorithm = null;
    try
    {
      algorithm = java.security.MessageDigest.getInstance("MD5");
    }
    catch (java.security.NoSuchAlgorithmException e)
    {
      // No encryption algorithm available
      //throw new InternalError("Unable to encrypt with MD5!");
      return x;
    }
    algorithm.reset();
    algorithm.update(buf);
    byte[] digest = algorithm.digest();
    StringBuffer cryptPass = new StringBuffer();
    for (int i = 0; i < digest.length; i++)
    {
      if ((digest[i] & 0xFF) <= 0x0F)
      {
        cryptPass.append('0');
      }
      cryptPass.append(Integer.toHexString(digest[i] & 0xFF));
    }
    return cryptPass.toString().toUpperCase();
  }

  public static long getInstanceStartTime() { return startTime; }

  public static long getGlobalQuanta()
  {
    return quanta;
  }

  public static void incrementQuanta()
  {
    synchronized (quantaLock)
    {
      quanta++;
    }
  }

  public static void writeOutWatchdogFile()
  {
  }

  // quanta is a global counter that we increment everytime we need to do a sync update with our clients
  private static long quanta;
  private static final Object quantaLock = new Object();

  private static EPG guide;
  private static Hunter mySeeker;
  private static SchedulerInterface myScheduler;
  private static Carny god;
  private static MMC mmc;
  private static boolean alive;
  private static boolean dead;
  private boolean netConfigDone = true;
  private static boolean readyForClientConnections = false;

  public static NetworkClient neddy;

  private static sage.locator.LocatorRegistrationClient locatorClient;
  private static AbstractedService psnatmgr;
  public static long startTime;

  // If this number is negative or zero then the MVP trial is expired and they have no valid licenses.
  // Otherwise, take this number and do a mod 100 to get the number of valid licenses that are installed.
  // If the number is greater than or equal to 100 then the trial is still active
  public static int c;
  // If this is set to true, then MVP clients are in use. This tells the native layer that its time to
  // start the MVP trial if it hasn't already.
  public static boolean d;

  // force this to link in at compile time
  private sage.pluginmanager.PluginManager forcedLink;
  private sage.plugin.StageCleaner forcedLink2;
}
