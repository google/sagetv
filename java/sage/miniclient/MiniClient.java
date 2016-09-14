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
package sage.miniclient;

public class MiniClient
{
  public static boolean WINDOWS_OS = false;
  public static boolean MAC_OS_X = false;
  public static boolean LINUX_OS = false;
  public static java.util.Properties myProperties;
  public static boolean fsStartup = false;
  public static Integer irKillCode = null;
  public static final String BYTE_CHARSET = "ISO8859_1";
  static
  {
    System.out.println("Starting MiniClient");
    WINDOWS_OS = System.getProperty("os.name").toLowerCase().indexOf("windows") != -1;
    MAC_OS_X = System.getProperty("os.name").toLowerCase().indexOf("mac os x") != -1;
    LINUX_OS = !WINDOWS_OS && !MAC_OS_X;
  }
  public static String cryptoFormats = "";
  private static int ConnectionError=0;
  public static String[] mainArgs;
  public static java.text.DateFormat DF = new java.text.SimpleDateFormat("EE M/d H:mm:ss.SSS");;
  public static final String df()
  {
    return df(System.currentTimeMillis());
  }
  public static final String df(long time)
  {
    synchronized (DF)
    {
      return DF.format(new java.util.Date(time));
    }
  }

  private static java.io.File configDir;
  public static void saveConfig()
  {
    java.io.OutputStream os = null;
    //java.io.File configDir = new java.io.File(System.getProperty("user.home"), ".sagetv");
    try
    {
      os = new java.io.FileOutputStream(new java.io.File(configDir, "SageTVPlaceshifter.properties.tmp"));
      myProperties.store(os, "SageTV Placeshifter Properties");
      os.close();
      new java.io.File(configDir, "SageTVPlaceshifter.properties").delete();
      new java.io.File(configDir, "SageTVPlaceshifter.properties.tmp").renameTo(new java.io.File(configDir, "SageTVPlaceshifter.properties"));
    }
    catch (java.io.IOException e)
    {
      // Attempting to show a dialog in the shutdown hook is a bad idea, so don't do that here
      System.out.println("Error saving configuration properties of:" + e.toString());
    }
    finally
    {
      try
      {
        if (os != null)
          os.close();
      }
      catch (Exception e){}
      os = null;
    }
  }
  private static boolean shuttingDown = false;
  public static boolean forcedServer = false;
  public static String forcedMAC = null;
  public static void main(final String[] args)
  {
    if (!LINUX_OS)
    {
      // We need to return control to the launcher on Windows so we can run the message loop
      Thread t = new Thread()
      {
        public void run()
        {
          startup(args);
        }
      };
      t.start();
    }
    else
      startup(args);
  }
  public static void startup(String[] args)
  {
    if(MAC_OS_X) {
      try {
        sage.Native.loadLibrary("MiniClient");
      } catch (Throwable t) {
        System.out.println("Exception occured loading MiniClient library: "+t);
      }
    }

    if (args != null && args.length > 0)
      splash();
    myProperties = new java.util.Properties();
    if (new java.io.File("SageTVPlaceshifter.properties").isFile())
      configDir = new java.io.File(System.getProperty("user.dir"));
    else
      configDir = new java.io.File(System.getProperty("user.home"), ".sagetv");
    configDir.mkdirs();
    // If the properties file is in the working directory; then use that one and save it back there. Otherwise
    // use the one in the user's home directory
    java.io.File propFile = new java.io.File(configDir, "SageTVPlaceshifter.properties");
    if (propFile.isFile())
    {
      java.io.InputStream is = null;
      try
      {
        is = new java.io.FileInputStream(propFile);
        myProperties.load(is);
      }
      catch (java.io.IOException e)
      {
        javax.swing.JOptionPane.showMessageDialog(null, "Error loading configuration properties of:" + e.toString(),
            "I/O Error", javax.swing.JOptionPane.ERROR_MESSAGE);
      }
      finally
      {
        try
        {
          if (is != null)
            is.close();
        }
        catch (Exception e){}
        is = null;
      }
    } else {
      // act like we're running opengl on Mac OS X
      if(MAC_OS_X) myProperties.setProperty("opengl", "true");
    }

    java.io.PrintStream redir = null;
    redir = new java.io.PrintStream(new java.io.OutputStream()
    {
      public void write(int b) {}
    }, true)
    {

      public synchronized void println(String s)
      {
        System.err.println(df() + " " + s);
      }
    };

    System.setOut(redir);
    //System.setErr(redir);
    mainArgs = args;
    boolean noretries = false;
    for (int i = 0; args != null && i < args.length; i++)
    {
      if (args[i].equals("-mac") && i < args.length - 1)
      {
        forcedMAC = args[++i];
      }
      else if (args[i].equals("-fullscreen"))
        fsStartup = true;
      else if (args[i].equals("-noretry"))
        noretries = true;
      else if (args[i].equals("-irexitcode") && i < args.length - 1)
      {
        try
        {
          irKillCode = new Integer(args[++i]);
        }
        catch (NumberFormatException e)
        {
          System.out.println("ERROR: Invalid irexitcode parameter of: " + args[i]);
        }
      }
    }
    Runtime.getRuntime().addShutdownHook(new Thread()
    {
      public void run()
      {
        MiniClientPowerManagement.getInstance().goodbye();
        MiniClient.saveConfig();
      }
    });

    System.out.println("Detecting cryptography support...");
    try
    {
      javax.crypto.Cipher.getInstance("RSA");
      cryptoFormats = "RSA,Blowfish,DH,DES";
    }
    catch (Exception e)
    {
      // If we don't do RSA, then we use DH for the key exchange and DES for the secret stuff
      cryptoFormats = "DH,DES";
    }
    final MiniClientManagerFrame mcmf;
    String servername;
    if (args != null && args.length > 0 && (forcedMAC == null || args.length != 2))
    {
      forcedServer = true;
      servername = args[args.length - 1]; // 3.3.3.3 total 12+3+1
      myProperties.setProperty("last_server", servername);

      // Try to find the matching server info for this one
      String serverNameList = myProperties.getProperty("server_names", "");
      java.util.StringTokenizer toker = new java.util.StringTokenizer(serverNameList, ";");
      MiniClientManagerFrame.MgrServerInfo msi = null;
      while (toker.hasMoreTokens())
      {
        String currName = toker.nextToken();
        if (myProperties.getProperty("servers/" + currName + "/address", "").equals(servername) ||
            myProperties.getProperty("servers/" + currName + "/locator_id", "").equals(servername))
        {
          msi = new MiniClientManagerFrame.MgrServerInfo(currName);
          break;
        }
      }
      // See if it's a Locator ID, and if so look it up.
      if (servername.length() == 19)
      {
        try
        {
          String lookupIP = sage.locator.LocatorLookupClient.lookupIPForGuid(servername);
          if (lookupIP != null)
            servername = lookupIP;
        }
        catch (java.io.IOException e)
        {
          System.out.println("ERROR with lookup server of:" + e);
          safeExit(1);
        }
      }
      System.out.println("Starting SageTVPlaceshifter Client");

      int numServerRetries = 8;
      int waitBetweenRetries = 2500;
      try
      {
        numServerRetries = Integer.parseInt(myProperties.getProperty("server_connect_retries", "100"));
        waitBetweenRetries = Integer.parseInt(myProperties.getProperty("server_connect_retry_wait", "2500"));
      }
      catch (NumberFormatException nfe)
      {}

      MiniClientConnection myMini;
      while (true)
      {
        myMini = new MiniClientConnection(servername, forcedMAC, "127.0.0.1".equals(servername) || "localhost".equals(servername), msi);
        try
        {
          myMini.connect();
        }
        catch (java.io.IOException e)
        {
          if (numServerRetries-- <= 0 || noretries)
          {
            if (!fsStartup)
              javax.swing.JOptionPane.showMessageDialog(null, "Error connecting to server of:" + e.toString(),
                  "I/O Error", javax.swing.JOptionPane.ERROR_MESSAGE);
            safeExit(1);
          }
          try
          {
            Thread.sleep(waitBetweenRetries);
          }
          catch (InterruptedException ie){}
          continue;
        }
        break;
      }
      final MiniClientConnection finalMini = myMini;
      // Ensure we kill the connection which'll close the media player
      Runtime.getRuntime().addShutdownHook(new Thread()
      {
        public void run()
        {
          shuttingDown = true;
          finalMini.close();
        }
      });
      startupPM();
      while (true)
      {
        if (!myMini.isConnected() && !shuttingDown)
        {
          safeExit(0);
        }
        try
        {
          Thread.sleep(200);
        }
        catch (InterruptedException e){}
      }
    }
    else
    {
      mcmf = new MiniClientManagerFrame();
      // Ensure we kill the connection which'll close the media player
      Runtime.getRuntime().addShutdownHook(new Thread()
      {
        public void run()
        {
          shuttingDown = true;
          mcmf.killConnections();
        }
      });
      hideSplash();
      mcmf.setVisible(true);
      bridgeMCMF = mcmf; // allows native Mac OS X app to open the settings dialog

      servername = null;
      //			servername = javax.swing.JOptionPane.showInputDialog("Enter the address or Locator ID of the server to connect to:",
      //				myProperties.getProperty("last_server", ""));
      //			if (servername == null || servername.length() == 0)
      //				safeExit(0); // System.exit(0);
    }
    System.out.println("Starting SageTVPlaceshifter Client");
    startupPM();
    startupDeviceDetector();
    System.out.println("Starting main loop");
    while (true)
    {
      if (!mcmf.isConnected() && !mcmf.isVisible())
      {
        // TODO: tell other threads to exit so they can do it more cleanly
        System.out.println("Connection error " + ConnectionError + ", exiting");
        if (!shuttingDown)
        {
          if (myProperties.getProperty("exit_app_on_server_close", "false").equalsIgnoreCase("true"))
            safeExit(0);
          //System.exit(-1);
          else
            mcmf.setVisible(true);
        }
      }
      try
      {
        Thread.sleep(200);
      }
      catch (InterruptedException e){}
    }
  }
  static MiniClientManagerFrame bridgeMCMF = null; // so we can hook up the "Preferences" menu item

  private static void startupPM()
  {
    sage.PowerManagement pm = MiniClientPowerManagement.getInstance();
    pm.setLogging(false);
    pm.setWaitTime(30000);
    pm.setPrerollTime(120000);
    pm.setIdleTimeout(120000);
    Thread t = new Thread(pm, "PowerManagement");
    t.setDaemon(true);
    t.setPriority(Thread.MIN_PRIORITY);
    t.start();
  }

  private static void startupDeviceDetector()
  {
    MiniStorageDeviceDetector dd = new MiniStorageDeviceDetector();
    Thread t = new Thread(dd, "DeviceDetector");
    t.setDaemon(true);
    t.setPriority(Thread.MIN_PRIORITY);
    t.start();
  }

  public static ServerInfo[] discoverServers(int discoveryTimeout, ServerDiscoverCallback callback)
  {
    java.util.Vector servers = (callback == null) ? new java.util.Vector() : null;
    System.out.println("Sending out discovery packets to find SageTVPlaceshifter Servers...");
    java.net.DatagramSocket sock = null;
    try
    {
      // Try on the encoder discovery port which is less likely to be in use
      try
      {
        sock = new java.net.DatagramSocket(8271);
      }
      catch (java.net.BindException be2)
      {
        // Just create it wherever
        sock = new java.net.DatagramSocket();
      }
      java.net.DatagramPacket pack = new java.net.DatagramPacket(new byte[512], 512);
      byte[] data = pack.getData();
      data[0] = 'S';
      data[1] = 'T';
      data[2] = 'V';
      data[3] = 1;
      pack.setLength(32);
      sock.setBroadcast(true);
      // Find the broadcast address for this subnet.
      //			String myIP = SageTV.api("GetLocalIPAddress", new Object[0]).toString();
      //			int lastIdx = myIP.lastIndexOf('.');
      //			myIP = myIP.substring(0, lastIdx) + ".255";
      pack.setAddress(java.net.InetAddress.getByName("255.255.255.255"));
      pack.setPort(31100);
      sock.send(pack);
      long startTime = System.currentTimeMillis();
      do
      {
        int currTimeout = (int)Math.max(1, (startTime + discoveryTimeout) - System.currentTimeMillis());
        sock.setSoTimeout(currTimeout);
        sock.receive(pack);
        if (pack.getLength() >= 4)
        {
          System.out.println("Discovery packet received:" + pack);
          ServerInfo si = new ServerInfo();
          if (data[0] == 'S' && data[1] == 'T' && data[2] == 'V' && data[3] == 2)
          {
            si.name = pack.getAddress().getHostName();
            si.address = pack.getAddress().getHostAddress();
            if (pack.getLength() >= 13) // it also has locator ID in it
            {
              int locatorID1 = ((data[5] & 0xFF) << 24) | ((data[6] & 0xFF) << 16) | ((data[7] & 0xFF) << 8) | (data[8] & 0xFF);
              int locatorID2 = ((data[9] & 0xFF) << 24) | ((data[10] & 0xFF) << 16) | ((data[11] & 0xFF) << 8) | (data[12] & 0xFF);
              long locatorID = (((long) locatorID1) << 32) | locatorID2;
              String prettyGuid = "";
              for (int i = 0; i < 4; i++)
              {
                String subGuid = Long.toString((locatorID >> ((3 - i)*16)) & 0xFFFF, 16);
                while (subGuid.length() < 4)
                  subGuid = "0" + subGuid;
                if (i != 0)
                  prettyGuid += "-";
                prettyGuid += subGuid;
              }
              si.locatorID = prettyGuid.toUpperCase();
            }
            if (pack.getLength() >= 15) // it also has the port in it
            {
              si.port = ((data[13] & 0xFF) << 8) | (data[14] & 0xFF);
            }
            System.out.println("Added server info:" + si);
            if (callback != null)
              callback.serverDiscovered(si);
            else
              servers.add(si);
          }
        }
      } while (true);
    }
    catch (Exception e)
    {
      //			System.out.println("Error discovering servers:" + e);
    }
    finally
    {
      if (sock != null)
      {
        try
        {
          sock.close();
        }catch (Exception e){}
        sock = null;
      }
    }
    return (servers == null) ? null : (ServerInfo[]) servers.toArray(new ServerInfo[0]);
  }

  public static void safeExit(final int state) {
    // This can cause deadlocks on some platforms, so execute it in another thread
    new Thread(new Runnable() {
      public void run() {
        System.exit(state);
      }
    }).start();
  }

  public static class ServerInfo
  {
    public String address;
    public int port;
    public String name;
    public String locatorID;
    public String toString()
    {
      return name + " " + address;
    }
  }

  public interface ServerDiscoverCallback
  {
    public void serverDiscovered(ServerInfo si);
  }

  private static javax.swing.JWindow splashWindow;
  private static void splash()
  {
    splashWindow = new javax.swing.JWindow();
    splashWindow.getContentPane().setLayout(new java.awt.BorderLayout());
    java.awt.Image theImage = java.awt.Toolkit.getDefaultToolkit().createImage(MiniClient.class.getClassLoader().getResource("images/splash.gif"));
    GFXCMD2.ensureImageIsLoaded(theImage);
    javax.swing.JLabel splashImage = new javax.swing.JLabel(new javax.swing.ImageIcon(theImage));
    splashWindow.getContentPane().add(splashImage, "Center");
    java.awt.Dimension scrSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
    splashWindow.pack();
    java.awt.Point center = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
    splashWindow.setLocation(center.x - splashWindow.getWidth()/2, center.y - splashWindow.getHeight()/2);
    splashWindow.setVisible(true);
  }

  static void hideSplash()
  {
    if (splashWindow != null)
    {
      splashWindow.setVisible(false);
      splashWindow.removeAll();
      splashWindow.setBounds(0, 0, 0, 0);
      splashWindow.dispose();
      splashWindow = null;
    }
  }
}
