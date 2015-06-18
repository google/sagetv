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

public class MiniClientConnection implements sage.SageTVInputCallback
{
  public static final String QUICKTIME = "Quicktime";
  public static final String AAC = "AAC";
  public static final String AC3 = "AC3";
  public static final String MPEG2_TS = "MPEG2-TS";
  public static final String MPEG2_VIDEO = "MPEG2-Video";
  public static final String WMA7 = "WMA7";
  public static final String WMA8 = "WMA8";
  public static final String WMA9LOSSLESS = "WMA9Lossless";
  public static final String WMA_PRO = "WMAPRO";
  public static final String WMV9 = "WMV9";
  public static final String WMV8 = "WMV8";
  public static final String WMV7 = "WMV7";
  public static final String FLASH_VIDEO = "FlashVideo";
  public static final String H264 = "H.264";
  public static final String OGG = "Ogg";
  public static final String VORBIS = "Vorbis";
  public static final String MPEG2_PS = "MPEG2-PS";
  public static final String MPEG2_PES_VIDEO = "MPEG2-PESVideo";
  public static final String MPEG2_PES_AUDIO = "MPEG2-PESAudio";
  public static final String MPEG1 = "MPEG1";
  public static final String JPEG = "JPEG";
  public static final String GIF = "GIF";
  public static final String PNG = "PNG";
  public static final String BMP = "BMP";
  public static final String MP2 = "MP2";
  public static final String MP3 = "MP3";
  public static final String MPEG1_VIDEO = "MPEG1-Video";
  public static final String MPEG4_VIDEO = "MPEG4-Video";
  public static final String MPEG4X = "MPEG4X"; // this is our private stream format we put inside MPEG2 PS for MPEG4/DivX video on Windows
  public static final String AVI = "AVI";
  public static final String WAV = "WAV";
  public static final String ASF = "ASF";
  public static final String FLAC = "FLAC";
  public static final String MATROSKA = "MATROSKA";
  public static final String VC1 = "VC1";
  public static final String ALAC = "ALAC"; // Apple lossless
  public static final String SMIL = "SMIL"; // for SMIL-XML files which represent sequences of content
  public static final String VP6F = "VP6F";

  public static boolean detailedBufferStats = false;

  public static String CONNECT_FAILURE_GENERAL_INTERNET = "The SageTV Placeshifter is having trouble connecting to the Internet. " +
      "Please make sure your Internet connection is established and properly configured. " +
      "If you have firewall software enabled (like ZoneAlarm or the Windows Firewall) be sure that the SageTV Placeshifter is allowed to have outgoing network access.";
  public static String CONNECT_FAILURE_LOCATOR_SERVER = "The SageTV Placeshifter is unable to connect to the SageTV Locator server. " +
      "The SageTV Locator server may be temporarily down, or connection to it may be blocked by a firewall. " +
      "If you have firewall software enabled (like ZoneAlarm or the Windows Firewall) be sure that the SageTV Placeshifter is allowed to have outgoing network access on port 8018. " +
      "If you're on a network that has a firewall, please contact your network administrator and ask them if they can open the outbound port 8018 for you.";
  public static String CONNECT_FAILURE_LOCATOR_REGISTRATION = "The SageTV Placeshifter is unable to connect to the specified SageTV Media Center Server because it is not registered with the SageTV Locator service. " +
      "You may have entered your Locator ID incorrectly. If your Locator ID is correct, please make sure that the Placeshifter is configured on your SageTV Media Center, and that there's no outbound firewall restrictions on port 8018. " +
      "This can be done by going through the Configuration Wizard and testing the Placeshifter connection.";
  public static String CONNECT_FAILURE_SEVER_SIDE = "The SageTV Placeshifter is unable to connect to the specified SageTV Media Center Server. " +
      "Please make sure that the Placeshifter is configured on your SageTV Media Center and that port forwarding is properly configured for your network. " +
      "This can be done by going through the Configuration Wizard on the SageTV Media Center and testing the Placeshifter connection.";
  public static String CONNECT_FAILURE_CLIENT_SIDE = "The SageTV Placeshifter is unable to connect to the specified SageTV Media Center Server. " +
      "Check to make sure you don't have any local firewall software running that may be blocking the connection. " +
      "The connection may also be blocked by a firewall on your network. If you're being blocked by a network firewall, " +
      "you should try reconfiguring the Placeshifter on the SageTV Server to use a common external port such as 80 or 443. " +
      "This can be done in the Configuration Wizard on the SageTV Media Center.";

  public static final String MPLAYER_PUSH_FORMATS = MPEG2_PS;
  public static final String MPLAYER_PULL_FORMATS = MPEG2_PS + "," + AAC + "," +
      MPEG2_TS + "," + ASF + "," + AVI + "," +
      FLAC + "," + FLASH_VIDEO + "," + MP2 + "," +
      MP3 + "," + MPEG1 + "," + OGG + "," +
      QUICKTIME + "," + VORBIS + "," + WAV + "," + MATROSKA;
  public static final String MPLAYER_AUDIO_CODECS = AAC + "," + FLAC + "," +
      MP2 + "," + MP3 + "," + WAV + "," +
      VORBIS + "," + WMA7 + "," + WMA8 + "," +
      AC3;
  public static final String MPLAYER_VIDEO_CODECS = FLASH_VIDEO + "," + H264 + "," +
      MPEG1_VIDEO + "," + MPEG2_VIDEO + "," + MPEG4_VIDEO + "," +
      WMV7 + "," + WMV8 + "," + WMV9 + "," + VP6F;

  public static final int DRAWING_CMD_TYPE = 16;
  public static final int GET_PROPERTY_CMD_TYPE = 0;
  public static final int SET_PROPERTY_CMD_TYPE = 1;
  public static final int FS_CMD_TYPE = 2;

  public static final int IR_EVENT_REPLY_TYPE = 128;
  public static final int KB_EVENT_REPLY_TYPE = 129;
  public static final int MPRESS_EVENT_REPLY_TYPE = 130;
  public static final int MRELEASE_EVENT_REPLY_TYPE = 131;
  public static final int MCLICK_EVENT_REPLY_TYPE = 132;
  public static final int MMOVE_EVENT_REPLY_TYPE = 133;
  public static final int MDRAG_EVENT_REPLY_TYPE = 134;
  public static final int MWHEEL_EVENT_REPLY_TYPE = 135;
  public static final int SAGECOMMAND_EVENT_REPLY_TYPE = 136;
  public static final int UI_RESIZE_EVENT_REPLY_TYPE = 192;
  public static final int UI_REPAINT_EVENT_REPLY_TYPE = 193;
  public static final int MEDIA_PLAYER_UPDATE_EVENT_REPLY_TYPE = 201;
  public static final int REMOTE_FS_HOTPLUG_INSERT_EVENT_REPLY_TYPE = 202;
  public static final int REMOTE_FS_HOTPLUG_REMOVE_EVENT_REPLY_TYPE = 203;
  public static final int OUTPUT_MODES_CHANGED_REPLY_TYPE = 224;
  public static final int SUBTITLE_UPDATE_REPLY_TYPE = 225;
  public static final int IMAGE_UNLOAD_REPLY_TYPE = 226;
  public static final int OFFLINE_CACHE_CHANGE_REPLY_TYPE = 227;

  // Tells the GFX channel to force the media channel to reconnect
  public static final int GFXCMD_MEDIA_RECONNECT = 131;

  public static final int FS_RV_SUCCESS = 0;
  public static final int FS_RV_PATH_EXISTS = 1;
  public static final int FS_RV_NO_PERMISSIONS = 2;
  public static final int FS_RV_PATH_DOES_NOT_EXIST = 3;
  public static final int FS_RV_NO_SPACE_ON_DISK = 4;
  public static final int FS_RV_ERROR_UNKNOWN = 5;

  public static final int FSCMD_CREATE_DIRECTORY = 64;
  // pathlen, path

  public static final int FS_PATH_HIDDEN = 0x01;
  public static final int FS_PATH_DIRECTORY = 0x02;
  public static final int FS_PATH_FILE = 0x04;

  public static final int FSCMD_GET_PATH_ATTRIBUTES = 65;
  // pathlen, path

  public static final int FSCMD_GET_FILE_SIZE = 66;
  // pathlen, path
  // 64-bit return value

  public static final int FSCMD_GET_PATH_MODIFIED_TIME = 67;
  // pathlen, path
  // 64-bit return value

  public static final int FSCMD_DIR_LIST = 68;
  // pathlen, path
  // 16-bit numEntries, *(16-bit pathlen, path)

  public static final int FSCMD_LIST_ROOTS = 69;
  // pathlen, path
  // 16-bit numEntries, *(16-bit pathlen, path)

  public static final int FSCMD_DOWNLOAD_FILE = 70;
  // secureID[4], offset[8], size[8], pathlen, path

  public static final int FSCMD_UPLOAD_FILE = 71;

  public static final int FSCMD_DELETE_FILE = 72;
  // pathlen, path

  public static int HIGH_SECURITY_FS = 3;
  public static int MED_SECURITY_FS = 2;
  public static int LOW_SECURITY_FS = 1;

  public static MiniClientConnection currConnection = null;

  public MiniClientConnection(String serverName, String myID, boolean useLocalNetworkOptimizations,
      MiniClientManagerFrame.MgrServerInfo msi)
  {
    if (serverName.indexOf(":") == -1)
    {
      this.serverName = serverName;
      this.port = 31099;
    }
    else
    {
      this.serverName = serverName.substring(0, serverName.indexOf(":"));
      this.port = 31099;
      try
      {
        this.port = Integer.parseInt(serverName.substring(serverName.indexOf(":") + 1));
      }
      catch (NumberFormatException e){}
    }
    this.myID = myID;
    this.useLocalNetworkOptimizations = useLocalNetworkOptimizations;
    this.msi = msi;
    offlineImageCacheLimit = Integer.parseInt(MiniClient.myProperties.getProperty("disk_image_cache_size", "100000000"));
    if ("true".equals(MiniClient.myProperties.getProperty("cache_images_on_disk", "true")))
    {
      java.io.File configDir = new java.io.File(System.getProperty("user.home"), ".sagetv");
      cacheDir = new java.io.File(configDir, "imgcache");
      cacheDir.mkdir();
    }
    else
      cacheDir = null;
    if ("true".equals(MiniClient.myProperties.getProperty("force_nonlocal_connection", "false")))
      this.useLocalNetworkOptimizations = false;
    usesAdvancedImageCaching = false;
  }

  private java.net.Socket EstablishServerConnection(int connType) throws java.io.IOException
  {
    int flag=1;
    int blockingmode;
    java.net.Socket sake = null;
    java.io.InputStream inStream = null;
    java.io.OutputStream outStream = null;
    try
    {
      sake = new java.net.Socket();
      sake.connect(new java.net.InetSocketAddress(serverName, port), 30000);
      sake.setSoTimeout(30000);
      sake.setTcpNoDelay(true);
      sake.setKeepAlive(true);
      outStream = sake.getOutputStream();
      inStream = sake.getInputStream();
      byte[] msg = new byte[7];
      msg[0] = (byte)1;
      if (myID == null)
      {
        try
        {
          String prefix;
          final Process procy = Runtime.getRuntime().exec(MiniClient.WINDOWS_OS ? "ipconfig /all" : "ifconfig", null, null);
          final java.util.regex.Pattern pat;// = java.util.regex.Pattern.compile(MiniClient.WINDOWS_OS ?
          //"Physical Address(\\. )*\\: (\\p{XDigit}\\p{XDigit}\\-\\p{XDigit}\\p{XDigit}\\-\\p{XDigit}\\p{XDigit}\\-\\p{XDigit}\\p{XDigit}\\-\\p{XDigit}\\p{XDigit}\\-\\p{XDigit}\\p{XDigit})" :
          //"(\\p{XDigit}\\p{XDigit}\\:\\p{XDigit}\\p{XDigit}\\:\\p{XDigit}\\p{XDigit}\\:\\p{XDigit}\\p{XDigit}\\:\\p{XDigit}\\p{XDigit}\\:\\p{XDigit}\\p{XDigit})");
          if (MiniClient.WINDOWS_OS)
            prefix = "";
          else if (MiniClient.MAC_OS_X)
            prefix = "ether";
          else
            prefix = ""; // no prefix for linux since language changes the label
          pat = java.util.regex.Pattern.compile(prefix + " ((\\p{XDigit}{2}[:-]){5}\\p{XDigit}{2})");
          Thread the = new Thread("InputStreamConsumer")
          {
            public void run()
            {
              try
              {
                java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(
                    procy.getInputStream()));
                String s;
                while ((s = buf.readLine()) != null)
                {
                  java.util.regex.Matcher m = pat.matcher(s);
                  // in case there's multiple adapters we only want the first one
                  if (myID == null && m.find())
                  {
                    myID = m.group(1);
                  }
                }
                buf.close();
              }
              catch (Exception e){}
            }
          };
          the.setDaemon(true);
          the.start();
          Thread the2 = new Thread("ErrorStreamConsumer")
          {
            public void run()
            {
              try
              {
                java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(
                    procy.getErrorStream()));
                while (buf.readLine() != null);
                buf.close();
              }
              catch (Exception e){}
            }
          };
          the2.setDaemon(true);
          the2.start();
          the.join();
          the2.join();
          procy.waitFor();
        }
        catch (Exception e)
        {
          System.out.println("Error getting MAC address of:" + e);
        }
      }

      if (myID != null)
      {
        for (int i = 0; i < myID.length(); i+=3)
        {
          msg[1 + i/3] = (byte)(Integer.parseInt(myID.substring(i, i+2), 16) & 0xFF);
        }
      }
      outStream.write(msg);
      outStream.write(connType);
      int rez = inStream.read();
      if(rez != 2)
      {
        System.out.println("Error with reply from server:" + rez);
        inStream.close();
        outStream.close();
        sake.close();
        return null;
      }
      System.out.println("Connection accepted by server");
      sake.setSoTimeout(0);
      return sake;
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR with socket connection: " + e);
      try
      {
        sake.close();
      }
      catch (Exception e1)
      {}
      try
      {
        inStream.close();
      }
      catch (Exception e1)
      {}
      try
      {
        outStream.close();
      }
      catch (Exception e1)
      {}
      throw e;
    }
  }

  public void connect() throws java.io.IOException
  {
    System.out.println("Attempting to connect to server at " + serverName + ":" + port);
    while(mediaSocket == null)
    {
      mediaSocket = EstablishServerConnection(1);
      if(mediaSocket == null)
      {
        //System.out.println("couldn't connect to media server, retrying in 1 secs.");
        //try{Thread.sleep(1000);}catch(InterruptedException e){}
        throw new java.net.ConnectException();
      }
    }
    System.out.println("Connected to media server");

    while (gfxSocket == null)
    {
      gfxSocket = EstablishServerConnection(0);
      if (gfxSocket == null)
      {
        //System.out.println("couldn't connect to gfx server, retrying in 5 secs.");
        //try { Thread.sleep(5000);} catch (InterruptedException e){}
        throw new java.net.ConnectException();
      }
    }
    System.out.println("Connected to gfx server");

    currConnection = this;

    alive = true;
    Thread t = new Thread("Media-" + serverName)
    {
      public void run()
      {
        MediaThread();
      }
    };
    t.start();
    try{Thread.sleep(100);}catch(Exception e){}
    t = new Thread("GFX-" + serverName)
    {
      public void run()
      {
        GFXThread();
      }
    };
    t.start();
    if(MiniClient.MAC_OS_X) {
      macIRC = new sage.MacIRC();
      macIRC.openInputPlugin(this);
    } else if(!MiniClient.WINDOWS_OS)
    {
      lircReader = new MiniLIRC(this);
    }
    else // Windows
    {
      winIR = new WinInfraredReceive();
      winIR.openInputPlugin(this);
    }

    String str = MiniClient.myProperties.getProperty("local_fs_security", "high");
    if ("low".equals(str))
      fsSecurity = LOW_SECURITY_FS;
    else if ("med".equals(str))
      fsSecurity = MED_SECURITY_FS;
    else
      fsSecurity = HIGH_SECURITY_FS;
  }

  public boolean isConnected()
  {
    return alive;
  }

  public void close()
  {
    alive = false;
    currConnection = null;
    try
    {
      gfxSocket.close();
    }
    catch (Exception e)
    {}
    GFXCMD2 oldGfx = myGfx;
    myGfx = null;
    if (oldGfx != null)
      oldGfx.close();
    if (myMedia != null)
      myMedia.close();
    try
    {
      mediaSocket.close();
    }
    catch (Exception e)
    {}
    if(lircReader!=null)
    {
      lircReader.close();
    }
    if(macIRC != null)
    {
      macIRC.closeInputPlugin();
      macIRC = null;
    }
    if (winIR != null)
    {
      winIR.closeInputPlugin();
      winIR = null;
    }
    cleanupOfflineCache();
  }

  protected void closeVideoClient()
  {
    System.out.println("closeVideoClient\n");
    if(socketfd>=0) try { jtux.UFile.close(socketfd); } catch(Exception e){}
    socketfd=-1;
  }

  protected void closeVideoServer()
  {
    System.out.println("closeVideoServer\n");
    if(tempfile!=null) try { tempfile.delete(); } catch(Exception e){}
    tempfile=null;
    if(tempfile2!=null) try { tempfile2.delete(); } catch(Exception e){}
    tempfile2=null;
    if(serverfd>=0) try { jtux.UFile.close(serverfd); } catch(Exception e){}
    serverfd=-1;
  }

  protected void startVideoServer()
  {
    System.out.println("Staring video server");
    int fd=-1;
    try
    {
      tempfile = java.io.File.createTempFile("sagevideo","");
      tempfile.deleteOnExit();
      java.io.RandomAccessFile rafile = new java.io.RandomAccessFile(tempfile,"rw");
      java.nio.channels.FileChannel videoCh =
          rafile.getChannel();

      java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(1920);
      for(int i=0;i<540*3;i++)
      {
        buf.clear();
        videoCh.write(buf);
      }

      mappedVideo =
          videoCh.map(java.nio.channels.FileChannel.MapMode.READ_WRITE,
              0, videoCh.size());
      videoCh.close();
      rafile.close();
      // Now we have the mapped video
      System.out.println("Temp file:"+tempfile);

      try
      {
        serverfd = jtux.UNetwork.socket(jtux.UConstant.AF_UNIX, jtux.UConstant.SOCK_STREAM, 0);
        if(serverfd<0)
        {
          System.out.println("Could not open unix server socket\n");
          return;
        }
        jtux.UNetwork.s_sockaddr_un sa = new jtux.UNetwork.s_sockaddr_un();
        sa.sun_family = jtux.UConstant.AF_UNIX;
        sa.sun_path = tempfile + ".socket";

        jtux.UNetwork.bind(serverfd, sa, 0);
        // Remove socket on exit
        tempfile2 = new java.io.File(tempfile + ".socket");
        tempfile2.deleteOnExit();
        mappedfname=tempfile.toString();
        jtux.UNetwork.listen(serverfd, 0);
      }
      catch(jtux.UErrorException e2)
      {
        closeVideoServer();
        System.out.println("Could not server socket " + e2);
        e2.printStackTrace();
        return;
      }
    }
    catch(java.io.IOException e)
    {
      System.out.println("Couldn't create temp file");
      return;
    }
  }


  protected void getVideoClient()
  {
    try
    {
      jtux.UNetwork.s_sockaddr_un clientsa = new jtux.UNetwork.s_sockaddr_un();
      jtux.UUtil.IntHolder clientsalen = new jtux.UUtil.IntHolder();
      clientsalen.value=0;
      System.out.println("Trying to accept fd: "+serverfd);
      socketfd = jtux.UNetwork.accept(serverfd, null,  clientsalen);
      System.out.println("Got connection on fd: "+socketfd);
    }
    catch(jtux.UErrorException e)
    {
      if(socketfd>=0) try { jtux.UFile.close(socketfd); } catch(Exception e2){}
      System.out.println("Could not accept client connection on socket " + e);
      e.printStackTrace();
      return;
    }
  }

  public String getVideoSocket()
  {
    return tempfile2.toString();
  }

  private static boolean fullRead(int fd, byte []buf, int count) throws jtux.UErrorException
  {
    int pos=0;
    byte []tempbuf = new byte[count];
    while(pos<count)
    {
      int retval=jtux.UFile.read(fd, tempbuf, count-pos);
      if(retval<0) return false;
      System.arraycopy(tempbuf, 0, buf, pos, retval);
      pos+=retval;
    }
    return true;
  }

  protected void readVideoCommand(byte[] cmdBuf) throws jtux.UErrorException
  {
    fullRead(socketfd, cmdBuf, 4);
  }

  // Needed for local video images...
  private static int getInt(byte [] buf, int offset)
  {
    int value=(buf[offset]&0xFF)<<24;
    value|=(buf[offset+1]&0xFF)<<16;
    value|=(buf[offset+2]&0xFF)<<8;
    value|=(buf[offset+3]&0xFF)<<0;
    return value;
  }

  private static void putInt(byte [] buf, int offset, int value)
  {
    buf[offset]=(byte) ((value>>24)&0xFF);
    buf[offset+1]=(byte) ((value>>16)&0xFF);
    buf[offset+2]=(byte) ((value>>8)&0xFF);
    buf[offset+3]=(byte) ((value>>0)&0xFF);
  }

  private static void putString(byte []buf, int offset, String str)
  {
    try
    {
      byte [] b=str.getBytes("ISO8859_1");
      System.arraycopy(b, 0, buf, offset, str.length());
    }catch(Exception e)
    {
      System.out.println("error:" + e);
      e.printStackTrace();
    }
  }

  private void GFXThread()
  {
    if (MiniClient.MAC_OS_X) {
      try {
        Class[] params = {this.getClass()};
        Object[] args = {this};
        java.lang.reflect.Constructor ctor = Class.forName("sage.miniclient.QuartzGFXCMD").getConstructor(params);
        myGfx = (GFXCMD2) ctor.newInstance(args);
      }
      catch (Throwable t) {
        System.out.println("Error loading QuartzGFXCMD class, reverting to default rendering:" + t);
        t.printStackTrace();
        myGfx = new GFXCMD2(this);
      }
    }
    else if ("true".equals(MiniClient.myProperties.getProperty("opengl", "true")))
    {
      if (MiniClient.WINDOWS_OS)
      {
        myGfx = new DirectX9GFXCMD(this);
      }
      else
      {
        try {
          Class[] params = {this.getClass()};
          Object[] args = {this};
          java.lang.reflect.Constructor ctor = Class.forName("sage.miniclient.OpenGLGFXCMD").getConstructor(params);
          myGfx = (GFXCMD2) ctor.newInstance(args);
        }
        catch (Throwable t) {
          System.out.println("Error loading OpenGLGFXCMD class, reverting to default rendering:" + t);
          t.printStackTrace();
          myGfx = new GFXCMD2(this);
        }
      }
    }
    else
    {
      myGfx =  new GFXCMD2(this);
    }
    detailedBufferStats = false;
    byte[] cmd = new byte[4];
    int command,len;
    int[] hasret = new int[1];
    int retval;
    byte[] retbuf = new byte[4];
    // Try to connect to the media server port to see if we can actually do pull-mode streaming.
    boolean canDoPullStreaming = false;
    try
    {
      System.out.println("Testing to see if server can do a pull mode streaming connection...");
      java.net.Socket mediaTest = new java.net.Socket();
      mediaTest.connect(new java.net.InetSocketAddress(serverName, 7818), 2000);
      mediaTest.close();
      canDoPullStreaming = true;
      System.out.println("Server can do a pull-mode streaming connection.");
    }
    catch (Exception e)
    {
      System.out.println("Failed pull mode media test....only use push mode.");
    }

    // Find the max resolution of all the displays and use that
    // Do this before we init so the GFXCMD can access it
    reportedScrSize = new java.awt.Dimension(0, 0);
    java.awt.GraphicsDevice[] screens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    for (int i = 0; i < screens.length; i++)
    {
      java.awt.Rectangle sb = screens[i].getDefaultConfiguration().getBounds();
      reportedScrSize.width = Math.max(reportedScrSize.width, sb.width);
      reportedScrSize.height = Math.max(reportedScrSize.height, sb.height);
    }
    if (reportedScrSize.width == 0 || reportedScrSize.height == 0)
      reportedScrSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
    System.out.println("Max screen size=" + reportedScrSize);

    final java.util.Vector gfxSyncVector = new java.util.Vector();

    try
    {
      eventChannel = new java.io.DataOutputStream(new java.io.BufferedOutputStream(gfxSocket.getOutputStream()));
      gfxIs = new java.io.DataInputStream(gfxSocket.getInputStream());
      zipMode = false;
      // Create the parallel threads so we can sync video and UI rendering appropriately
      Thread gfxReadThread = new Thread("GFXRead")
      {
        public void run()
        {
          byte[] gfxCmds = new byte[4];
          byte[] cmdbuffer = new byte[4096];
          int len;
          java.io.DataInputStream myStream = gfxIs;
          boolean enabledzip = false;
          while (alive)
          {
            synchronized (gfxSyncVector)
            {
              if (gfxSyncVector.contains(gfxCmds))
              {
                try
                {
                  gfxSyncVector.wait(5000);
                }catch (InterruptedException e)
                {}
                continue;
              }
            }
            try
            {
              if (zipMode && !enabledzip)
              {
                // Recreate stream wrappers with ZLIB
                com.jcraft.jzlib.ZInputStream zs = new com.jcraft.jzlib.ZInputStream(gfxSocket.getInputStream(), true);
                zs.setFlushMode(com.jcraft.jzlib.JZlib.Z_SYNC_FLUSH);
                myStream = new java.io.DataInputStream(zs);
                enabledzip = true;
              }
              //System.out.println("before gfxread readfully");
              myStream.readFully(gfxCmds);
              len = ((gfxCmds[1] & 0xFF) << 16) | ((gfxCmds[2] & 0xFF)<<8) | (gfxCmds[3] & 0xFF);
              if (cmdbuffer.length < len)
              {
                cmdbuffer = new byte[len];
              }
              // Read from the tcp socket
              myStream.readFully(cmdbuffer, 0, len);
            }
            catch (Exception e)
            {
              if (reconnectAllowed && alive && firstFrameStarted && !encryptEvents)
              {
                performingReconnect = true;
                enabledzip = false;
                System.out.println("GFX channel detected a connection error and we're in a mode that allows reconnect...try to reconnect to the server now");
                try
                {
                  myStream.close();
                }
                catch (Exception e1){}
                try
                {
                  eventChannel.close();
                }
                catch (Exception e1){}
                try
                {
                  gfxSocket.close();
                }
                catch (Exception e1){}
                try
                {
                  gfxSocket = EstablishServerConnection(5);
                  eventChannel = new java.io.DataOutputStream(new java.io.BufferedOutputStream(gfxSocket.getOutputStream()));
                  myStream = gfxIs = new java.io.DataInputStream(gfxSocket.getInputStream());
                  if (zipMode && !enabledzip)
                  {
                    // Recreate stream wrappers with ZLIB
                    com.jcraft.jzlib.ZInputStream zs = new com.jcraft.jzlib.ZInputStream(gfxSocket.getInputStream(), true);
                    zs.setFlushMode(com.jcraft.jzlib.JZlib.Z_SYNC_FLUSH);
                    myStream = new java.io.DataInputStream(zs);
                    enabledzip = true;
                  }
                  System.out.println("Done doing server reconnect...continue on our merry way!");
                }
                catch (Exception e1)
                {
                  System.out.println("Failure in reconnecting to server...abort the client");
                  performingReconnect = false;
                  synchronized (gfxSyncVector)
                  {
                    gfxSyncVector.add(e);
                    return;
                  }
                }
                performingReconnect = false;
              }
              else
              {
                synchronized (gfxSyncVector)
                {
                  gfxSyncVector.add(e);
                  return;
                }
              }
            }
            synchronized (gfxSyncVector)
            {
              gfxSyncVector.add(gfxCmds);
              gfxSyncVector.add(cmdbuffer);
              gfxSyncVector.notifyAll();
            }
          }
        }
      };
      gfxReadThread.setDaemon(true);
      gfxReadThread.start();
      Thread videoReadThread = new Thread("VideoRead")
      {
        public void run()
        {
          byte[] gfxCmds = new byte[4];
          byte[] cmdbuffer2 = new byte[4096];
          int len;
          startVideoServer();
          while (alive)
          {
            synchronized (gfxSyncVector)
            {
              if (gfxSyncVector.contains(gfxCmds))
              {
                try
                {
                  gfxSyncVector.wait(5000);
                }catch (InterruptedException e)
                {}
                continue;
              }
            }
            try
            {
              if(socketfd>=0)
              {
                readVideoCommand(gfxCmds);
                len = ((gfxCmds[1] & 0xFF) << 16) | ((gfxCmds[2] & 0xFF)<<8) | (gfxCmds[3] & 0xFF);
                if(fullRead(socketfd, cmdbuffer2, len)!=true)
                {
                  System.out.println("Could not get command from socket ");
                  closeVideoClient();
                  continue;
                }
                //System.out.println("Video cmd "+ (cmd[0]&0xFF));
              }
              else
              {
                getVideoClient();
                continue;
              }
            }
            catch (Exception e)
            {
              // Close the connection and wait until we have another one...
              closeVideoClient();
              continue;
              //synchronized (gfxSyncVector)
              //{
              //	gfxSyncVector.add(e);
              //	return;
              //}
            }
            if((gfxCmds[0]&0xFF)==130)
            {
              System.out.println("Received video close command\n");
              closeVideoClient();
              continue;
            }
            synchronized (gfxSyncVector)
            {
              gfxSyncVector.add(gfxCmds);
              gfxSyncVector.add(cmdbuffer2);
              gfxSyncVector.notifyAll();
            }
          }
          closeVideoServer();
        }
      };
      videoReadThread.setDaemon(true);
      if(!MiniClient.WINDOWS_OS && !MiniClient.MAC_OS_X)
        videoReadThread.start();


      while (alive)
      {
        byte []cmdbuffer;
        synchronized (gfxSyncVector)
        {
          if (!gfxSyncVector.isEmpty())
          {
            Object newData = gfxSyncVector.get(0);
            if (newData instanceof Throwable)
              throw (Throwable) newData;
            else
            {
              cmd = (byte[]) newData;
              cmdbuffer = (byte[]) gfxSyncVector.get(1);
            }
          }
          else
          {
            try
            {
              gfxSyncVector.wait(5000);
            }
            catch (InterruptedException e){}
            continue;
          }
        }
        //is.readFully(cmd);

        command = (cmd[0] & 0xFF);
        len = ((cmd[1] & 0xFF) << 16) | ((cmd[2] & 0xFF)<<8) | (cmd[3] & 0xFF);
        //System.out.println("inside loop command "+command + " len "+len);
        if((command&0x80)!=0) // Local video update command
        {
          byte []data = cmdbuffer;
          switch(cmd[0]&0xFF)
          {
            case 0x80: // New video
              System.out.println("Video cmd "+ (cmd[0]&0xFF));
              videowidth=getInt(data, 0);
              videoheight=getInt(data, 4);
              videoformat=getInt(data, 8);
              myGfx.createVideo(videowidth, videoheight, videoformat);
              putInt(data, 0, mappedfname.length());
              putString(data, 4, mappedfname);
              putInt(data, 4+mappedfname.length()+0, 0); //offsetY
              putInt(data, 4+mappedfname.length()+4, videowidth); //pitchY
              putInt(data, 4+mappedfname.length()+8, videowidth*videoheight); //offsetU
              putInt(data, 4+mappedfname.length()+12, videowidth/2); //pitchU
              putInt(data, 4+mappedfname.length()+16, videowidth*videoheight+videowidth*videoheight/4); //offsetV
              putInt(data, 4+mappedfname.length()+20, videowidth/2); //pitchV
              try
              {
                jtux.UFile.write(socketfd, data, 4+mappedfname.length()+24);
              }
              catch(jtux.UErrorException e)
              {
                System.out.println("Could not send reply to socket " + e);
                e.printStackTrace();
                closeVideoClient();
              }
              break;
            case 0x81: // New frame
              videoframetype=getInt(data, 0);
              myGfx.updateVideo(videoframetype, mappedVideo);
              putInt(data, 0, 0); //offsetY
              putInt(data, 4, videowidth); //pitchY
              putInt(data, 8, videowidth*videoheight); //offsetU
              putInt(data, 12, videowidth/2); //pitchU
              putInt(data, 16, videowidth*videoheight+videowidth*videoheight/4); //offsetV
              putInt(data, 20, videowidth/2); //pitchV
              try
              {
                jtux.UFile.write(socketfd, data, 24);
              }
              catch(jtux.UErrorException e)
              {
                System.out.println("Could not send reply to socket " + e);
                e.printStackTrace();
                closeVideoClient();
              }
              break;
          }

        }
        if (command == DRAWING_CMD_TYPE) // GFX cmd
        {
          // We need to let the opengl rendering thread do that...
          command = (cmdbuffer[0] & 0xFF);
          if (command == GFXCMD_MEDIA_RECONNECT)
          {
            // Just tell the MediaThread to kill its current connection and reconnect
            try
            {
              mediaSocket.close();
            }
            catch(Exception e){}
          }
          else
          {
            if (command == GFXCMD2.GFXCMD_STARTFRAME)
              firstFrameStarted = true;
            retval = myGfx.ExecuteGFXCommand(command, len, cmdbuffer, hasret);

            if(hasret[0] != 0)
            {
              retbuf[0] = (byte)((retval>>24) & 0xFF);
              retbuf[1] = (byte) ((retval>>16) & 0xFF);
              retbuf[2] = (byte) ((retval>>8) & 0xFF);
              retbuf[3] = (byte) ((retval>>0) & 0xFF);
              try
              {
                synchronized (eventChannel)
                {
                  eventChannel.write(DRAWING_CMD_TYPE); // GFX reply
                  eventChannel.writeShort(0);eventChannel.write(4);// 3 byte length of 4
                  eventChannel.writeInt(0); // timestamp
                  eventChannel.writeInt(replyCount++);
                  eventChannel.writeInt(0); // pad
                  if (encryptEvents && evtEncryptCipher != null)
                    eventChannel.write(evtEncryptCipher.doFinal(retbuf, 0, 4));
                  else
                    eventChannel.write(retbuf, 0, 4);
                  eventChannel.flush();
                }
              }
              catch (Exception e)
              {
                eventChannelError();
              }
            }
          }
        }
        else if (command == GET_PROPERTY_CMD_TYPE) // get property
        {
          String propName = new String(cmdbuffer, 0, len);
          String propVal = "";
          byte[] propValBytes = null;
          if ("GFX_TEXTMODE".equals(propName))
          {
            // Don't use JOGL text rendering if we're doing a connection to a Mac from another machine
            // since it may not have the same JVM version and text will get truncated then
            // NARFLEX - 5/26/09 - Don't use local text rendering unless we're on the same machine as
            // the server so we know all font calculations will be the same. We can cache the font images in the
            // filesystem anyways, so it won't be that bad of a performance impact
            // NARFLEX - 1/17/10 - Just never allow text rendering directly because the new effects system has
            // clipping issues associated with it and the Placeshifter never will connect to the localhost
            // address automatically anyways. This way we'll have consistency across implementations.
            //						if (!isLocahostConnection())
            propVal = "";
            //						else
            //							propVal = "REMOTEFONTS";
          }
          else if ("GFX_BLENDMODE".equals(propName))
          {
            if ("true".equals(MiniClient.myProperties.getProperty("opengl", "true")))
              propVal = "PREMULTIPLY";
            else
              propVal = "POSTMULTIPLY";
          }
          else if ("GFX_SCALING".equals(propName))
          {
            if ("true".equals(MiniClient.myProperties.getProperty("opengl", "true")))
              propVal = "HARDWARE";
            else
              propVal = "SOFTWARE";
          }
          else if ("GFX_OFFLINE_IMAGE_CACHE".equals(propName))
          {
            if ("true".equals(MiniClient.myProperties.getProperty("cache_images_on_disk", "true")))
              propVal = "TRUE";
            else
              propVal = "FALSE";
          }
          else if ("OFFLINE_CACHE_CONTENTS".equals(propName))
          {
            propVal = getOfflineCacheList();
          }
          else if ("ADVANCED_IMAGE_CACHING".equals(propName))
          {
            propVal = "TRUE";
            usesAdvancedImageCaching = true;
          }
          else if ("GFX_BITMAP_FORMAT".equals(propName))
          {
            if (useLocalNetworkOptimizations/* || (!MiniClient.WINDOWS_OS && "true".equals(MiniClient.myProperties.getProperty("opengl", "false")))*/)
              propVal = "";
            else
              propVal = "PNG,JPG,GIF,BMP";
          }
          else if ("GFX_COMPOSITE".equals(propName))
          {
            if ("true".equals(MiniClient.myProperties.getProperty("opengl", "true")))
              propVal = "BLEND";
            else
              propVal = "COLORKEY";
          }
          else if ("GFX_SURFACES".equals(propName))
          {
            propVal = "TRUE";
          }
          else if ("GFX_DIFFUSE_TEXTURES".equals(propName))
          {
            if (myGfx instanceof DirectX9GFXCMD)
              propVal = "TRUE";
            else
              propVal = "";
          }
          else if ("GFX_XFORMS".equals(propName))
          {
            if (myGfx instanceof DirectX9GFXCMD)
              propVal = "TRUE";
            else
              propVal = "";
          }
          else if ("GFX_TEXTURE_BATCH_LIMIT".equals(propName))
          {
            // We don't support this command yet
            propVal = "";
          }
          else if ("GFX_COLORKEY".equals(propName))
          {
            propVal = Integer.toString(MiniMPlayerPlugin.COLORKEY_VALUE, 16);
            while (propVal.length() < 6)
              propVal = "0" + propVal;
          }
          else if ("STREAMING_PROTOCOLS".equals(propName))
          {
            propVal = "file,stv";
          }
          else if ("INPUT_DEVICES".equals(propName))
          {
            propVal = "IR,KEYBOARD,MOUSE";
          }
          else if ("DISPLAY_OVERSCAN".equals(propName))
          {
            propVal = "0;0;1.0;1.0";
          }
          else if ("FIRMWARE_VERSION".equals(propName))
          {
            propVal = sage.Version.MAJOR_VERSION + "." + sage.Version.MINOR_VERSION + "." + sage.Version.MICRO_VERSION;
          }
          else if ("DETAILED_BUFFER_STATS".equals(propName))
          {
            propVal = "TRUE";
            detailedBufferStats = true;
          }
          else if ("PUSH_BUFFER_SEEKING".equals(propName))
            propVal = "TRUE";
          else if ("GFX_SUBTITLES".equals(propName))
            propVal = "TRUE";
          else if ("FORCED_MEDIA_RECONNECT".equals(propName))
            propVal = "TRUE";
          else if ("AUTH_CACHE".equals(propName))
          {
            propVal = (msi != null) ? "TRUE" : "";
          }
          else if ("GET_CACHED_AUTH".equals(propName))
          {
            // Make sure crypto is on before we send this back!!
            if (encryptEvents && evtEncryptCipher != null && msi != null && msi.authBlock != null)
            {
              propVal = msi.authBlock;
            }
            else
              propVal = "";
          }
          else if ("REMOTE_FS".equals(propName))
          {
            if (fsSecurity <= MED_SECURITY_FS)
              propVal = "TRUE";
            else
              propVal = "";
          }
          else if ("GFX_VIDEO_UPDATE".equals(propName))
            propVal = "TRUE";
          else if ("ZLIB_COMM".equals(propName))
            propVal = "TRUE";
          else if ("VIDEO_CODECS".equals(propName))
          {
            String extra_codecs = MiniClient.myProperties.getProperty("mplayer/extra_video_codecs", null);
            propVal = MPLAYER_VIDEO_CODECS;
            if(extra_codecs != null)
              propVal += "," + extra_codecs;
          }
          else if ("AUDIO_CODECS".equals(propName))
          {
            String extra_codecs = MiniClient.myProperties.getProperty("mplayer/extra_audio_codecs", null);
            propVal = MPLAYER_AUDIO_CODECS;
            if(extra_codecs != null)
              propVal += "," + extra_codecs;
          }
          else if ("PUSH_AV_CONTAINERS".equals(propName))
          {
            // If we are forced into pull mode then we don't support pushing
            if (canDoPullStreaming && "pull".equalsIgnoreCase(MiniClient.myProperties.getProperty("streaming_mode", "dynamic")))
              propVal = "";
            else
              propVal = MPLAYER_PUSH_FORMATS;
          }
          else if ("PULL_AV_CONTAINERS".equals(propName))
          {
            // If we're forced into fixed mode then we don't support pulling
            if (!canDoPullStreaming || "fixed".equalsIgnoreCase(MiniClient.myProperties.getProperty("streaming_mode", "dynamic")))
              propVal = "";
            else
              propVal = MPLAYER_PULL_FORMATS;
          }
          else if ("MEDIA_PLAYER_BUFFER_DELAY".equals(propName))
          {
            // MPlayer needs an extra 2 seconds of buffer before it can do playback because of it's single-threaded nature
            // NOTE: If MPlayer is not being used, this should be changed...hopefully to a lower value like 0 :)
            propVal = MiniClient.MAC_OS_X ? "3000" : "2000";
          }
          else if ("FIXED_PUSH_MEDIA_FORMAT".equals(propName))
          {
            if ("fixed".equalsIgnoreCase(MiniClient.myProperties.getProperty("streaming_mode", "dynamic")))
            {
              // Build the fixed media format string
              propVal = "videobitrate=" + MiniClient.myProperties.getProperty("fixed_encoding/video_bitrate_kbps", "300") + "000;";
              propVal += "audiobitrate=" + MiniClient.myProperties.getProperty("fixed_encoding/audio_bitrate_kbps", "64") + "000;";
              int fps = 30;
              int keyFrameInt = 10;
              try
              {
                fps = Integer.parseInt(MiniClient.myProperties.getProperty("fixed_encoding/fps", "30"));
                keyFrameInt = Integer.parseInt(MiniClient.myProperties.getProperty("fixed_encoding/key_frame_interval", "10"));
              }
              catch (NumberFormatException e){}
              propVal += "gop=" + (fps * keyFrameInt) + ";";
              propVal += "bframes=" + ("true".equalsIgnoreCase(MiniClient.myProperties.getProperty("fixed_encoding/use_b_frames",
                  "true")) ? "2" : "0") + ";";
              propVal += "fps=" + fps + ";";
              propVal += "resolution=" + MiniClient.myProperties.getProperty("fixed_encoding/video_resolution", "CIF") + ";";
            }
            else
              propVal = "";
          }
          else if ("CRYPTO_ALGORITHMS".equals(propName))
            propVal = MiniClient.cryptoFormats;
          else if ("CRYPTO_SYMMETRIC_KEY".equals(propName))
          {
            if (serverPublicKey != null && encryptedSecretKeyBytes == null)
            {
              if (currentCrypto.indexOf("RSA") != -1)
              {
                // We have to generate our secret key and then encrypt it with the server's public key
                javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance("Blowfish");
                mySecretKey = keyGen.generateKey();
                evtEncryptCipher = javax.crypto.Cipher.getInstance("Blowfish");
                evtEncryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, mySecretKey);

                byte[] rawSecretBytes = mySecretKey.getEncoded();
                try
                {
                  javax.crypto.Cipher encryptCipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
                  encryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, serverPublicKey);
                  encryptedSecretKeyBytes = encryptCipher.doFinal(rawSecretBytes);
                }
                catch (Exception e)
                {
                  System.out.println("Error encrypting data to submit to server: " + e);
                  e.printStackTrace();
                }
              }
              else
              {
                // We need to finish the DH key agreement and generate the shared secret key
                /*
                 * Bob gets the DH parameters associated with Alice's public key.
                 * He must use the same parameters when he generates his own key
                 * pair.
                 */
                javax.crypto.spec.DHParameterSpec dhParamSpec = ((javax.crypto.interfaces.DHPublicKey)serverPublicKey).getParams();

                // Bob creates his own DH key pair
                System.out.println("Generate DH keypair ...");
                java.security.KeyPairGenerator bobKpairGen = java.security.KeyPairGenerator.getInstance("DH");
                bobKpairGen.initialize(dhParamSpec);
                java.security.KeyPair bobKpair = bobKpairGen.generateKeyPair();

                // Bob creates and initializes his DH KeyAgreement object
                javax.crypto.KeyAgreement bobKeyAgree = javax.crypto.KeyAgreement.getInstance("DH");
                bobKeyAgree.init(bobKpair.getPrivate());

                // Bob encodes his public key, and sends it over to Alice.
                encryptedSecretKeyBytes = bobKpair.getPublic().getEncoded();

                // We also have to generate the shared secret now
                bobKeyAgree.doPhase(serverPublicKey, true);
                mySecretKey = bobKeyAgree.generateSecret("DES");
                evtEncryptCipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding");
                evtEncryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, mySecretKey);
              }
            }
            propValBytes = encryptedSecretKeyBytes;
          }
          else if ("GFX_SUPPORTED_RESOLUTIONS".equals(propName))
          {
            propVal = Integer.toString(reportedScrSize.width) + "x" + Integer.toString(reportedScrSize.height) + ";windowed";
          }
          else if ("GFX_RESOLUTION".equals(propName))
          {
            sage.SageTVWindow winny = myGfx.getWindow();
            if (winny != null)
              propVal = Integer.toString(winny.getWidth()) + "x" + Integer.toString(winny.getHeight());
          }
          else if ("GFX_DRAWMODE".equals(propName) && "true".equalsIgnoreCase(MiniClient.myProperties.getProperty("force_full_screen_draw", "false")))
          {
            propVal = "FULLSCREEN";
          }
          System.out.println("GetProperty: " + propName + " = " + propVal);
          try
          {
            synchronized (eventChannel)
            {
              if (propValBytes == null)
                propValBytes = propVal.getBytes(MiniClient.BYTE_CHARSET);
              eventChannel.write(GET_PROPERTY_CMD_TYPE); // get property reply
              eventChannel.write((propValBytes.length >> 16) & 0xFF);
              eventChannel.write((propValBytes.length >> 8) & 0xFF);
              eventChannel.write(propValBytes.length & 0xFF);// 3 byte length
              eventChannel.writeInt(0); // timestamp
              eventChannel.writeInt(replyCount++);
              eventChannel.writeInt(0); // pad
              if (propValBytes.length > 0)
              {
                if (encryptEvents && evtEncryptCipher != null)
                  eventChannel.write(evtEncryptCipher.doFinal(propValBytes));
                else
                  eventChannel.write(propValBytes);
              }
              eventChannel.flush();
            }
          }
          catch (Exception e)
          {
            eventChannelError();
          }
        }
        else if (command == SET_PROPERTY_CMD_TYPE) // set property
        {
          short nameLen = (short) (((cmdbuffer[0] & 0xFF) << 8) | (cmdbuffer[1] & 0xFF));
          short valLen = (short) (((cmdbuffer[2] & 0xFF) << 8) | (cmdbuffer[3] & 0xFF));
          String propName = new String(cmdbuffer, 4, nameLen);
          //String propVal = new String(cmdbuffer, 4 + nameLen, valLen);
          System.out.println("SetProperty " + propName);
          synchronized (eventChannel)
          {
            boolean encryptThisReply = encryptEvents;
            if ("CRYPTO_PUBLIC_KEY".equals(propName))
            {
              byte[] keyBytes = new byte[valLen];
              System.arraycopy(cmdbuffer, 4 + nameLen, keyBytes, 0, valLen);
              java.security.spec.X509EncodedKeySpec pubKeySpec = new java.security.spec.X509EncodedKeySpec(keyBytes);
              java.security.KeyFactory keyFactory;
              if (currentCrypto.indexOf("RSA") != -1)
                keyFactory = java.security.KeyFactory.getInstance("RSA");
              else
                keyFactory = java.security.KeyFactory.getInstance("DH");
              serverPublicKey = keyFactory.generatePublic(pubKeySpec);
              retval = 0;
            }
            else if ("CRYPTO_ALGORITHMS".equals(propName))
            {
              currentCrypto = new String(cmdbuffer, 4 + nameLen, valLen);
              retval = 0;
            }
            else if ("CRYPTO_EVENTS_ENABLE".equals(propName))
            {
              if ("TRUE".equalsIgnoreCase(new String(cmdbuffer, 4 + nameLen, valLen)))
              {
                if (evtEncryptCipher != null)
                {
                  encryptEvents = true;
                  retval = 0;
                }
                else
                {
                  encryptEvents = false;
                  retval = 1;
                }
              }
              else
              {
                encryptEvents = false;
                retval = 0;
              }
              System.out.println("SageTVPlaceshifter event encryption is now=" + encryptEvents);
            }
            else if ("GFX_RESOLUTION".equals(propName))
            {
              String propVal = new String(cmdbuffer, 4 + nameLen, valLen);
              if (myGfx.getWindow() != null)
              {
                // NOTE: These resolution changes need to be done on the AWT thread because if we're disposing
                // a window then that may invoke on AWT which could block against an event coming back in
                if ("FULLSCREEN".equals(propVal))
                {
                  java.awt.EventQueue.invokeLater(new Runnable()
                  {
                    public void run()
                    {
                      myGfx.getWindow().setFullScreen(true);
                    }
                  });
                }
                else if ("WINDOW".equals(propVal))
                {
                  java.awt.EventQueue.invokeLater(new Runnable()
                  {
                    public void run()
                    {
                      myGfx.getWindow().setFullScreen(false);
                    }
                  });
                }
                else
                {
                  int xidx = propVal.indexOf('x');
                  if (xidx != -1)
                  {
                    try
                    {
                      int w = Integer.parseInt(propVal.substring(0, xidx));
                      int h = Integer.parseInt(propVal.substring(xidx + 1));
                      myGfx.getWindow().setSize(w, h);
                    }
                    catch (Exception e)
                    {
                    }
                  }
                }
              }
              retval = 0;
            }
            else if ("GFX_FONTSERVER".equals(propName))
            {
              if ("TRUE".equalsIgnoreCase(new String(cmdbuffer, 4 + nameLen, valLen)))
              {
                fontServer = true;
                retval = 0;
              }
              else
              {
                fontServer = false;
                retval = 0;
              }
            }
            else if ("ZLIB_COMM_XFER".equals(propName))
            {
              zipMode = "TRUE".equalsIgnoreCase(new String(cmdbuffer, 4 + nameLen, valLen));
              retval = 0;
            }
            else if ("ADVANCED_IMAGE_CACHING".equals(propName))
            {
              usesAdvancedImageCaching = "TRUE".equalsIgnoreCase(new String(cmdbuffer, 4 + nameLen, valLen));
              retval = 0;
            }
            else if ("RECONNECT_SUPPORTED".equals(propName))
            {
              reconnectAllowed = "TRUE".equalsIgnoreCase(new String(cmdbuffer, 4 + nameLen, valLen));
              retval = 0;
            }
            else if ("SUBTITLES_CALLBACKS".equals(propName))
            {
              subSupport = "TRUE".equalsIgnoreCase(new String(cmdbuffer, 4 + nameLen, valLen));
              retval = 0;
            }
            else if ("SET_CACHED_AUTH".equals(propName))
            {
              // Save this authentication block in the properties file
              // First we need to decrypt it with the symmetric key
              if (evtEncryptCipher != null && msi != null)
              {
                javax.crypto.Cipher decryptCipher = javax.crypto.Cipher.getInstance(evtEncryptCipher.getAlgorithm());
                decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, mySecretKey);
                String newAuth = new String(decryptCipher.doFinal(cmdbuffer, 4 + nameLen, valLen));
                if (msi != null)
                  msi.setAuthBlock(newAuth);
              }
              retval = 0;
            }
            else
              retval = 0; // or the error code if it failed the set
            retbuf[0] = (byte)((retval>>24) & 0xFF);
            retbuf[1] = (byte) ((retval>>16) & 0xFF);
            retbuf[2] = (byte) ((retval>>8) & 0xFF);
            retbuf[3] = (byte) ((retval>>0) & 0xFF);
            try
            {
              eventChannel.write(SET_PROPERTY_CMD_TYPE); // set property reply
              eventChannel.write(0);eventChannel.writeShort(4);// 3 byte length of 4
              eventChannel.writeInt(0); // timestamp
              eventChannel.writeInt(replyCount++);
              eventChannel.writeInt(0); // pad
              if (encryptThisReply)
                eventChannel.write(evtEncryptCipher.doFinal(retbuf, 0, 4));
              else
                eventChannel.write(retbuf, 0, 4);
              eventChannel.flush();
            }
            catch (Exception e)
            {
              eventChannelError();
            }
          }
        }
        else if (command == FS_CMD_TYPE)
        {
          command = (cmdbuffer[0] & 0xFF);
          processFSCmd(command, len, cmdbuffer);
        }

        // Remove whatever we just processed
        synchronized (gfxSyncVector)
        {
          gfxSyncVector.remove(0);
          gfxSyncVector.remove(0);
          gfxSyncVector.notifyAll();
        }
      }
    }
    catch (Throwable e)
    {
      System.out.println("Error w/ GFX Thread: " + e);
      e.printStackTrace();
    }
    finally
    {
      try
      {
        gfxIs.close();
      }
      catch (Exception e){}
      try
      {
        eventChannel.close();
      }
      catch (Exception e){}
      try
      {
        gfxSocket.close();
      }
      catch (Exception e){}

      if (alive)
        connectionError();
    }
  }

  public void recvCommand(int sageCommandID)
  {
    postSageCommandEvent(sageCommandID);
  }

  public void recvCommand(int sageCommandID, String payload)
  {
    postSageCommandEvent(sageCommandID);
  }

  public void recvCommand(int sageCommandID, String[] payloads)
  {
    postSageCommandEvent(sageCommandID);
  }

  public void recvInfrared(byte[] irCode)
  {
    int coded = 0;
    for (int i = 0; i < irCode.length; i += 4)
    {
      int currCoded = (irCode[i] & 0xFF) << 24;
      if (i + 1 < irCode.length)
        currCoded |= (irCode[i + 1] & 0xFF) << 16;
      if (i + 2 < irCode.length)
        currCoded |= (irCode[i + 2] & 0xFF) << 8;
      if (i + 3 < irCode.length)
        currCoded |= (irCode[i + 3] & 0xFF);
      coded = coded ^ currCoded;
    }
    postIREvent(coded);
  }

  public void recvKeystroke(char keyChar, int keyCode, int keyModifiers)
  {
    postKeyEvent(keyCode, keyModifiers, keyChar);
  }

  public void postIREvent(int IRCode)
  {
    if (MiniClient.irKillCode != null && MiniClient.irKillCode.intValue() == IRCode)
    {
      System.out.println("IR Exit Code received...terminating");
      close();
      return;
    }
    if (myGfx != null)
      myGfx.setHidden(false, false);
    MiniClientPowerManagement.getInstance().kick();
    if (performingReconnect)
      return;
    synchronized (eventChannel)
    {
      try
      {
        eventChannel.write(IR_EVENT_REPLY_TYPE); // ir event code
        eventChannel.write(0);eventChannel.writeShort(4);// 3 byte length of 4
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        if (encryptEvents && evtEncryptCipher != null)
        {
          byte[] data = new byte[4];
          data[0] = (byte) ((IRCode >> 24) & 0xFF);
          data[1] = (byte) ((IRCode >> 16) & 0xFF);
          data[2] = (byte) ((IRCode >> 8) & 0xFF);
          data[3] = (byte) (IRCode & 0xFF);
          eventChannel.write(evtEncryptCipher.doFinal(data));
        }
        else
        {
          eventChannel.writeInt(IRCode);
        }
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        eventChannelError();
      }
    }
  }

  public void postSageCommandEvent(int sageCommand)
  {
    if (myGfx != null)
      myGfx.setHidden(false, false);
    MiniClientPowerManagement.getInstance().kick();
    if (performingReconnect)
      return;
    synchronized (eventChannel)
    {
      try
      {
        eventChannel.write(136); // SageTV Command event code
        eventChannel.write(0);eventChannel.writeShort(4);// 3 byte length of 4
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        if (encryptEvents && evtEncryptCipher != null)
        {
          byte[] data = new byte[4];
          data[0] = (byte) ((sageCommand >> 24) & 0xFF);
          data[1] = (byte) ((sageCommand >> 16) & 0xFF);
          data[2] = (byte) ((sageCommand >> 8) & 0xFF);
          data[3] = (byte) (sageCommand & 0xFF);
          eventChannel.write(evtEncryptCipher.doFinal(data));
        }
        else
        {
          eventChannel.writeInt(sageCommand);
        }
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        eventChannelError();
      }
    }
  }

  public void postKeyEvent(int keyCode, int keyModifiers, char keyChar)
  {
    MiniClientPowerManagement.getInstance().kick();
    if (performingReconnect)
      return;
    synchronized (eventChannel)
    {
      try
      {
        eventChannel.write(KB_EVENT_REPLY_TYPE); // kb event code
        eventChannel.write(0);eventChannel.writeShort(10);// 3 byte length of 10
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        if (encryptEvents && evtEncryptCipher != null)
        {
          byte[] data = new byte[10];
          data[0] = (byte) ((keyCode >> 24) & 0xFF);
          data[1] = (byte) ((keyCode >> 16) & 0xFF);
          data[2] = (byte) ((keyCode >> 8) & 0xFF);
          data[3] = (byte) (keyCode & 0xFF);
          data[4] = (byte) ((keyChar >> 8 ) & 0xFF);
          data[5] = (byte) (keyChar & 0xFF);
          data[6] = (byte) ((keyModifiers >> 24) & 0xFF);
          data[7] = (byte) ((keyModifiers >> 16) & 0xFF);
          data[8] = (byte) ((keyModifiers >> 8) & 0xFF);
          data[9] = (byte) (keyModifiers & 0xFF);
          eventChannel.write(evtEncryptCipher.doFinal(data));
        }
        else
        {
          eventChannel.writeInt(keyCode);
          eventChannel.writeChar(keyChar);
          eventChannel.writeInt(keyModifiers);
        }
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        eventChannelError();
      }
    }
  }

  public void postResizeEvent(java.awt.Dimension size)
  {
    if (performingReconnect)
      return;
    synchronized (eventChannel)
    {
      try
      {
        eventChannel.write(UI_RESIZE_EVENT_REPLY_TYPE); // resize event code
        eventChannel.write(0);eventChannel.writeShort(8);// 3 byte length of 8
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        if (encryptEvents && evtEncryptCipher != null)
        {
          byte[] data = new byte[8];
          data[0] = (byte) ((size.width >> 24) & 0xFF);
          data[1] = (byte) ((size.width >> 16) & 0xFF);
          data[2] = (byte) ((size.width >> 8) & 0xFF);
          data[3] = (byte) (size.width & 0xFF);
          data[4] = (byte) ((size.height >> 24) & 0xFF);
          data[5] = (byte) ((size.height >> 16) & 0xFF);
          data[6] = (byte) ((size.height >> 8) & 0xFF);
          data[7] = (byte) (size.height & 0xFF);
          eventChannel.write(evtEncryptCipher.doFinal(data));
        }
        else
        {
          eventChannel.writeInt(size.width);
          eventChannel.writeInt(size.height);
        }
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        eventChannelError();
      }
    }
  }

  public void postRepaintEvent(int x, int y, int w, int h)
  {
    if (performingReconnect)
      return;
    synchronized (eventChannel)
    {
      try
      {
        eventChannel.write(UI_REPAINT_EVENT_REPLY_TYPE); // repaint event code
        eventChannel.write(0);eventChannel.writeShort(16);// 3 byte length of 16
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        if (encryptEvents && evtEncryptCipher != null)
        {
          byte[] data = new byte[16];
          data[0] = (byte) ((x >> 24) & 0xFF);
          data[1] = (byte) ((x >> 16) & 0xFF);
          data[2] = (byte) ((x >> 8) & 0xFF);
          data[3] = (byte) (x & 0xFF);
          data[4] = (byte) ((y >> 24) & 0xFF);
          data[5] = (byte) ((y >> 16) & 0xFF);
          data[6] = (byte) ((y >> 8) & 0xFF);
          data[7] = (byte) (y & 0xFF);
          data[8] = (byte) ((w >> 24) & 0xFF);
          data[9] = (byte) ((w >> 16) & 0xFF);
          data[10] = (byte) ((w >> 8) & 0xFF);
          data[11] = (byte) (w & 0xFF);
          data[12] = (byte) ((h >> 24) & 0xFF);
          data[13] = (byte) ((h >> 16) & 0xFF);
          data[14] = (byte) ((h >> 8) & 0xFF);
          data[15] = (byte) (h & 0xFF);
          eventChannel.write(evtEncryptCipher.doFinal(data));
        }
        else
        {
          eventChannel.writeInt(x);
          eventChannel.writeInt(y);
          eventChannel.writeInt(w);
          eventChannel.writeInt(h);
        }
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        eventChannelError();
      }
    }
  }

  public void postImageUnload(int handle)
  {
    if (performingReconnect)
      return;
    synchronized (eventChannel)
    {
      try
      {
        eventChannel.write(IMAGE_UNLOAD_REPLY_TYPE); // repaint event code
        eventChannel.write(0);eventChannel.writeShort(4);// 3 byte length of 16
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        if (encryptEvents && evtEncryptCipher != null)
        {
          byte[] data = new byte[4];
          data[0] = (byte) ((handle >> 24) & 0xFF);
          data[1] = (byte) ((handle >> 16) & 0xFF);
          data[2] = (byte) ((handle >> 8) & 0xFF);
          data[3] = (byte) (handle & 0xFF);
          eventChannel.write(evtEncryptCipher.doFinal(data));
        }
        else
        {
          eventChannel.writeInt(handle);
        }
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        eventChannelError();
      }
    }
  }

  public void postOfflineCacheChange(boolean addedToCache, String rezID)
  {
    if (performingReconnect)
      return;
    synchronized (eventChannel)
    {
      try
      {
        int strlen = rezID.length();
        eventChannel.write(OFFLINE_CACHE_CHANGE_REPLY_TYPE); // repaint event code
        eventChannel.write(0);eventChannel.writeShort(5 + strlen);// 3 byte length
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        byte[] strBytes = rezID.getBytes(MiniClient.BYTE_CHARSET);
        if (encryptEvents && evtEncryptCipher != null)
        {
          byte[] data = new byte[5 + strlen];
          data[0] = (byte)(addedToCache ? 1 : 0);
          data[1] = (byte) ((strlen >> 24) & 0xFF);
          data[2] = (byte) ((strlen >> 16) & 0xFF);
          data[3] = (byte) ((strlen >> 8) & 0xFF);
          data[4] = (byte) (strlen & 0xFF);
          System.arraycopy(strBytes, 0, data, 5, strBytes.length);
          eventChannel.write(evtEncryptCipher.doFinal(data));
        }
        else
        {
          eventChannel.writeByte(addedToCache ? 1 : 0);
          eventChannel.writeInt(strlen);
          eventChannel.write(strBytes);
        }
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        eventChannelError();
      }
    }
  }

  public void postMouseEvent(java.awt.event.MouseEvent evt)
  {
    // if on Mac OS X we need to intercept Control-Clicks and make them look like right clicks
    if (MiniClient.MAC_OS_X && MiniClient.myProperties.getProperty("ui/enable_control_click_for_right_mouse", "true").equalsIgnoreCase("true")
        && evt.isControlDown() && (evt.getButton() == java.awt.event.MouseEvent.BUTTON1)
        && (evt.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED || evt.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED))
    {
      // clear control key modifier masks, convert button1 modifier masks to button3 masks
      // then post a button3 event
      int newModifiers = evt.getModifiers();
      newModifiers &= ~(java.awt.event.InputEvent.CTRL_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK);
      if ((newModifiers & java.awt.event.InputEvent.BUTTON1_MASK) == java.awt.event.InputEvent.BUTTON1_MASK)
        newModifiers = (newModifiers & ~java.awt.event.InputEvent.BUTTON1_MASK) | java.awt.event.InputEvent.BUTTON3_MASK;
      if ((newModifiers & java.awt.event.InputEvent.BUTTON1_DOWN_MASK) == java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
        newModifiers = (newModifiers & java.awt.event.InputEvent.BUTTON1_DOWN_MASK) | java.awt.event.InputEvent.BUTTON3_DOWN_MASK;

      postMouseEvent(new java.awt.event.MouseEvent((java.awt.Component)evt.getSource(), evt.getID(), evt.getWhen(), newModifiers,
          evt.getX(), evt.getY(), evt.getClickCount(), evt.isPopupTrigger(),
          java.awt.event.MouseEvent.BUTTON3));
      return;
    }
    MiniClientPowerManagement.getInstance().kick();
    if (performingReconnect)
      return;
    synchronized (eventChannel)
    {
      try
      {
        if (evt.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED)
          eventChannel.write(MCLICK_EVENT_REPLY_TYPE); // mouse click event code
        else if (evt.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED)
          eventChannel.write(MPRESS_EVENT_REPLY_TYPE); // mouse press event code
        else if (evt.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED)
          eventChannel.write(MRELEASE_EVENT_REPLY_TYPE); // mouse release event code
        else if (evt.getID() == java.awt.event.MouseEvent.MOUSE_DRAGGED)
          eventChannel.write(MDRAG_EVENT_REPLY_TYPE); // mouse drag event code
        else if (evt.getID() == java.awt.event.MouseEvent.MOUSE_MOVED)
          eventChannel.write(MMOVE_EVENT_REPLY_TYPE); // mouse move event code
        else if (evt.getID() == java.awt.event.MouseEvent.MOUSE_WHEEL)
          eventChannel.write(MWHEEL_EVENT_REPLY_TYPE); // mouse wheel event code
        else
          return;
        eventChannel.write(0);eventChannel.writeShort(14);// 3 byte length of 14
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        if (encryptEvents && evtEncryptCipher != null)
        {
          byte[] data = new byte[14];
          data[0] = (byte) ((evt.getX() >> 24) & 0xFF);
          data[1] = (byte) ((evt.getX() >> 16) & 0xFF);
          data[2] = (byte) ((evt.getX() >> 8) & 0xFF);
          data[3] = (byte) (evt.getX() & 0xFF);
          data[4] = (byte) ((evt.getY() >> 24) & 0xFF);
          data[5] = (byte) ((evt.getY() >> 16) & 0xFF);
          data[6] = (byte) ((evt.getY() >> 8) & 0xFF);
          data[7] = (byte) (evt.getY() & 0xFF);
          data[8] = (byte) ((evt.getModifiers() >> 24) & 0xFF);
          data[9] = (byte) ((evt.getModifiers() >> 16) & 0xFF);
          data[10] = (byte) ((evt.getModifiers() >> 8) & 0xFF);
          data[11] = (byte) (evt.getModifiers() & 0xFF);
          if (evt.getID() == java.awt.event.MouseEvent.MOUSE_WHEEL)
            data[12] = (byte) ((java.awt.event.MouseWheelEvent)evt).getWheelRotation();
          else
            data[12] = (byte) evt.getClickCount();
          data[13] = (byte) evt.getButton();
          eventChannel.write(evtEncryptCipher.doFinal(data));
        }
        else
        {
          eventChannel.writeInt(evt.getX());
          eventChannel.writeInt(evt.getY());
          eventChannel.writeInt(evt.getModifiers());
          if (evt.getID() == java.awt.event.MouseEvent.MOUSE_WHEEL)
            eventChannel.write(((java.awt.event.MouseWheelEvent)evt).getWheelRotation());
          else
            eventChannel.write(evt.getClickCount());
          eventChannel.write(evt.getButton());
        }
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        eventChannelError();
      }
    }
  }

  public void postMediaPlayerUpdateEvent()
  {
    if (performingReconnect)
      return;
    synchronized (eventChannel)
    {
      try
      {
        eventChannel.write(MEDIA_PLAYER_UPDATE_EVENT_REPLY_TYPE); // media player update event code
        eventChannel.write(0);eventChannel.writeShort(0);// 3 byte length of 0
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        eventChannelError();
      }
    }
  }

  public void postSubtitleInfo(long pts, long duration, byte[] data, int flags)
  {
    if (!subSupport) return; // don't send events if the other end doesn't support it
    if (performingReconnect)
      return;
    synchronized (eventChannel)
    {
      try
      {
        eventChannel.write(SUBTITLE_UPDATE_REPLY_TYPE); // subtitle update event code
        eventChannel.write(0);eventChannel.writeShort((short)(14 + ((data == null) ? 0 : data.length)));// 3 byte length of 0
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        eventChannel.writeInt(flags);
        eventChannel.writeInt((int)pts);
        eventChannel.writeInt((int)duration);
        if (data != null)
        {
          eventChannel.writeShort((short)data.length);
          eventChannel.write(data);
        }
        else
          eventChannel.writeShort(0);
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        eventChannelError();
      }
    }
  }

  public void postHotplugEvent(boolean insertion, String devPath, String devDesc)
  {
    if (eventChannel == null || fsSecurity == HIGH_SECURITY_FS) return;
    if (performingReconnect)
      return;
    synchronized (eventChannel)
    {
      if (encryptEvents && evtEncryptCipher != null)
      {
        // can't do this while encrypted'
        return;
      }
      try
      {
        eventChannel.write(insertion ? REMOTE_FS_HOTPLUG_INSERT_EVENT_REPLY_TYPE : REMOTE_FS_HOTPLUG_REMOVE_EVENT_REPLY_TYPE);
        eventChannel.write(0);eventChannel.writeShort(4 + devPath.length() + devDesc.length());// 3 byte length of the 2 strings + count
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        eventChannel.writeShort(devPath.length());
        eventChannel.write(devPath.getBytes());
        eventChannel.writeShort(devDesc.length());
        eventChannel.write(devDesc.getBytes());
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        eventChannelError();
      }
    }
  }

  private static String getCmdString(byte[] data, int offset)
  {
    int length = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    try
    {
      return new String(data, offset + 2, length, "UTF-8");
    }
    catch (java.io.UnsupportedEncodingException uee)
    {
      return new String(data, offset + 2, length);
    }
  }

  private static long getCmdLong(byte[] data, int offset)
  {
    long rv = 0;
    for (int i = 0; i < 8; i++)
      rv = (rv << 8) | ((long)(data[offset + i] & 0xFF));
    return rv;
  }

  private void processFSCmd(int cmdType, int len, byte[] cmdData) throws java.io.IOException
  {
    if (encryptEvents && evtEncryptCipher != null)
    {
      // can't do this while encrypted'
      return;
    }
    // Filesystem commands should not even be seen in this security mode
    if (fsSecurity == HIGH_SECURITY_FS)
      return;
    //		System.out.println("MiniClient processing FS Command: " + cmdType);
    len -= 4; // for the header
    byte[][] strRv = null;
    long longRv = 0;
    int intRv = 0;
    boolean isLongRv = false;
    String pathName;
    java.io.File theFile;
    switch (cmdType)
    {
      case FSCMD_CREATE_DIRECTORY:
        theFile = new java.io.File(getCmdString(cmdData, 4));
        // Check security
        if (fsSecurity == MED_SECURITY_FS && !theFile.isDirectory())
        {
          if (javax.swing.JOptionPane.showConfirmDialog(null, "<html>Would you like to allow the server to create the local directory:<br>" +
              theFile + "</html>", "File System Security", javax.swing.JOptionPane.YES_NO_OPTION,
              javax.swing.JOptionPane.WARNING_MESSAGE) != javax.swing.JOptionPane.YES_OPTION)
          {
            intRv = FS_RV_NO_PERMISSIONS;
          }
        }
        if (intRv == 0)
          intRv = (theFile.isDirectory() || theFile.mkdirs()) ? FS_RV_SUCCESS : FS_RV_ERROR_UNKNOWN;
        break;
      case FSCMD_GET_FILE_SIZE:
        isLongRv = true;
        longRv = new java.io.File(getCmdString(cmdData, 4)).length();
        break;
      case FSCMD_DELETE_FILE:
        theFile = new java.io.File(getCmdString(cmdData, 4));
        if (!theFile.exists())
          intRv = FS_RV_PATH_DOES_NOT_EXIST;
        else
        {
          // Check security
          if (fsSecurity == MED_SECURITY_FS)
          {
            if (javax.swing.JOptionPane.showConfirmDialog(null, "<html>Would you like to allow the server to delete the local file:<br>" +
                theFile + "</html>", "File System Security", javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE) != javax.swing.JOptionPane.YES_OPTION)
            {
              intRv = FS_RV_NO_PERMISSIONS;
            }
          }
          if (intRv == 0 && !theFile.delete())
            intRv = FS_RV_ERROR_UNKNOWN;
        }
        break;
      case FSCMD_GET_PATH_ATTRIBUTES:
        theFile = new java.io.File(getCmdString(cmdData, 4));
        if (theFile.isHidden())
          intRv = intRv | FS_PATH_HIDDEN;
        if (theFile.isFile())
          intRv = intRv | FS_PATH_FILE;
        if (theFile.isDirectory())
          intRv = intRv | FS_PATH_DIRECTORY;
        break;
      case FSCMD_GET_PATH_MODIFIED_TIME:
        isLongRv = true;
        longRv = new java.io.File(getCmdString(cmdData, 4)).lastModified();
        break;
      case FSCMD_DIR_LIST:
        theFile = new java.io.File(getCmdString(cmdData, 4));
        String[] list = theFile.list();
        strRv = new byte[(list == null) ? 0 : list.length][];
        for (int i = 0; i < strRv.length; i++)
          strRv[i] = list[i].getBytes("UTF-8");
        break;
      case FSCMD_LIST_ROOTS:
        java.io.File[] rootFiles = java.io.File.listRoots();
        strRv = new byte[(rootFiles == null) ? 0 : rootFiles.length][];
        for (int i = 0; i < strRv.length; i++)
          strRv[i] = rootFiles[i].toString().getBytes("UTF-8");
        break;
      case FSCMD_DOWNLOAD_FILE:
      case FSCMD_UPLOAD_FILE:
        int secureID = ((cmdData[4] & 0xFF) << 24) | ((cmdData[5] & 0xFF) << 16) | ((cmdData[6] & 0xFF) << 8) | (cmdData[7] & 0xFF);
        long fileOffset = getCmdLong(cmdData, 8);
        long fileSize = getCmdLong(cmdData, 16);
        pathName = getCmdString(cmdData, 24);
        theFile = new java.io.File(pathName);
        if (cmdType == FSCMD_DOWNLOAD_FILE)
        {
          // Make sure we're downloading to a valid file that we can write to
          if (theFile.exists() && !theFile.canWrite())
            intRv = FS_RV_NO_PERMISSIONS;
          else if (theFile.getParentFile() != null && !theFile.getParentFile().isDirectory())
            intRv = FS_RV_PATH_DOES_NOT_EXIST;
          else
          {
            // Check security
            if (fsSecurity == MED_SECURITY_FS)
            {
              if (javax.swing.JOptionPane.showConfirmDialog(null, "<html>Would you like to allow the server to download to the local file:<br>" +
                  theFile + "</html>", "File System Security", javax.swing.JOptionPane.YES_NO_OPTION,
                  javax.swing.JOptionPane.WARNING_MESSAGE) != javax.swing.JOptionPane.YES_OPTION)
              {
                intRv = FS_RV_NO_PERMISSIONS;
              }
            }
            if (intRv == 0)
            {
              // Try to create the pathname
              try
              {
                if (!theFile.createNewFile())
                  intRv = FS_RV_NO_PERMISSIONS;
              }
              catch (java.io.IOException e)
              {
                intRv = FS_RV_NO_PERMISSIONS;
              }
            }
          }
        }
        else
        {
          // It's an upload; make sure the file is there and can be read
          if (!theFile.exists())
            intRv = FS_RV_PATH_DOES_NOT_EXIST;
          else if (!theFile.canRead())
            intRv = FS_RV_NO_PERMISSIONS;
          else if (fsSecurity == MED_SECURITY_FS)
          {
            if (javax.swing.JOptionPane.showConfirmDialog(null, "<html>Would you like to allow the server to upload from the local file:<br>" +
                theFile + "</html>", "File System Security", javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE) != javax.swing.JOptionPane.YES_OPTION)
            {
              intRv = FS_RV_NO_PERMISSIONS;
            }
          }
        }
        if (intRv == 0)
        {
          intRv = startAsyncFSOperation(cmdType == FSCMD_DOWNLOAD_FILE, secureID, fileOffset, fileSize, theFile);
        }
        break;
    }
    synchronized (eventChannel)
    {
      eventChannel.write(FS_CMD_TYPE);
      eventChannel.write(0);
      if (strRv != null)
      {
        //				System.out.println("MiniClient returning file list of size " + strRv.length);
        // find the total length
        int totalLen = 0;
        for (int i = 0; i < strRv.length; i++)
          totalLen += strRv[i].length + 2;
        eventChannel.writeShort(totalLen + 2);// 3 byte length of 0
      }
      else if (isLongRv)
      {
        //				System.out.println("MiniClient returning 64-bit FS RV of " + longRv);
        eventChannel.writeShort(8);
      }
      else
      {
        //				System.out.println("MiniClient returning 32-bit FS RV of " + intRv);
        eventChannel.writeShort(4);
      }
      eventChannel.writeInt(0); // timestamp
      eventChannel.writeInt(replyCount++);
      eventChannel.writeInt(0); // pad
      if (strRv != null)
      {
        eventChannel.writeShort(strRv.length);
        for (int i = 0; i < strRv.length; i++)
        {
          eventChannel.writeShort(strRv[i].length);
          eventChannel.write(strRv[i]);
        }
      }
      else if (isLongRv)
        eventChannel.writeLong(longRv);
      else
        eventChannel.writeInt(intRv);
      eventChannel.flush();
    }
  }

  // Connects back to the server to initiate a remote FS operation; returns 0 if this starts up OK
  private int startAsyncFSOperation(boolean download, int secureID, long fileOffset, long fileSize, java.io.File theFile)
  {
    System.out.println("Attempting to connect bak to server on FS channel");
    java.net.Socket sake = null;
    java.io.OutputStream fsOut = null;
    java.io.InputStream fsIn = null;
    java.io.RandomAccessFile raf = null;
    try
    {
      sake = EstablishServerConnection(2);
      sake.setSoTimeout(30000);
      fsOut = sake.getOutputStream();
      fsIn = sake.getInputStream();
      byte[] secureBytes = new byte[4];
      secureBytes[0] = (byte)((secureID >> 24) & 0xFF);
      secureBytes[1] = (byte)((secureID >> 16) & 0xFF);
      secureBytes[2] = (byte)((secureID >> 8) & 0xFF);
      secureBytes[3] = (byte)(secureID & 0xFF);
      fsOut.write(secureBytes);
      raf = new java.io.RandomAccessFile(theFile, download ? "rw" : "r");
      if (fileOffset > 0)
        raf.seek(fileOffset);
      // Now start the real async operation
      asyncFSXfer(download, sake, fsOut, fsIn, fileOffset, fileSize, raf);
    }
    catch (java.io.IOException e)
    {
      if (raf != null)
        try{raf.close();}catch(Exception e1){}
      if (sake != null)
        try{sake.close();}catch(Exception e1){}
      if (fsOut != null)
        try{fsOut.close();}catch(Exception e1){}
      if (fsIn != null)
        try{fsIn.close();}catch(Exception e1){}
      return FS_RV_ERROR_UNKNOWN;
    }
    return FS_RV_SUCCESS;
  }

  private void asyncFSXfer(final boolean download, final java.net.Socket sake, final java.io.OutputStream fsOut,
      final java.io.InputStream fsIn, final long fileOffset, final long fileSize, final java.io.RandomAccessFile localFile)
  {
    Thread t = new Thread()
    {
      public void run()
      {
        byte[] fsBuffer = new byte[16384];
        try
        {
          long xferSize = fileSize;
          while (xferSize > 0)
          {
            int currSize = (int)Math.min(xferSize, fsBuffer.length);
            if (!download)
            {
              localFile.readFully(fsBuffer, 0, currSize);
              fsOut.write(fsBuffer, 0, currSize);
            }
            else
            {
              currSize = fsIn.read(fsBuffer, 0, currSize);
              if (currSize < 0)
                throw new java.io.EOFException();
              localFile.write(fsBuffer, 0, currSize);
            }
            xferSize -= currSize;
            //System.out.println("xferSize rem " + xferSize);
          }
          if (!download)
            fsOut.flush();
          else
          {
            localFile.close();
            fsOut.write(0);fsOut.write(0);fsOut.write(0);fsOut.write(0);
          }
          System.out.println("Finished Remote FS operation!");
        }
        catch (Exception e)
        {
          System.out.println("ERROR w/ remote FS operation: " + e);
          e.printStackTrace();
        }
        finally
        {
          if (sake != null)
            try{sake.close();}catch(Exception e){}
          if (fsOut != null)
            try{fsOut.close();}catch(Exception e){}
          if (fsIn != null)
            try{fsIn.close();}catch(Exception e){}
          if (localFile != null)
            try{localFile.close();}catch(Exception e){}
        }
      }
    };
    t.setDaemon(true);
    t.setPriority(Thread.MIN_PRIORITY);
    t.start();
  }

  private void MediaThread()
  {
    byte[] cmdbuffer = new byte[65536];

    java.io.OutputStream os = null;
    java.io.DataInputStream is = null;
    while(alive)
    {
      myMedia = new MediaCmd(this);
      try
      {
        os = mediaSocket.getOutputStream();
        is = new java.io.DataInputStream(mediaSocket.getInputStream());
        while(alive)
        {
          byte[] cmd = new byte[4];
          int command,len;
          int retval;
          byte[] retbuf = new byte[16];
          is.readFully(cmd);

          command = (cmd[0] & 0xFF);
          len = ((cmd[1] & 0xFF) << 16) | ((cmd[2] & 0xFF)<<8) | (cmd[3] & 0xFF);
          if (cmdbuffer.length < len)
          {
            cmdbuffer = new byte[len];
          }
          is.readFully(cmdbuffer, 0, len);

          retval = myMedia.ExecuteMediaCommand(command, len, cmdbuffer, retbuf);

          if(retval > 0)
          {
            os.write(retbuf, 0, retval);
            os.flush();
          }
        }
      }
      catch (Exception e)
      {
        System.out.println("Error w/ Media Thread: " + e);
        e.printStackTrace();
      }
      finally
      {
        try
        {
          os.close();
        }
        catch (Exception e){}
        os = null;
        try
        {
          is.close();
        }
        catch (Exception e){}
        is = null;
        try
        {
          mediaSocket.close();
        }
        catch (Exception e){}
        mediaSocket = null;
      }
      if (!alive)
        break;
      try
      {
        mediaSocket = EstablishServerConnection(1);
      }
      catch (Exception e)
      {
        connectionError();
      }
      if(mediaSocket == null)
      {
        //System.out.println("couldn't connect to media server, retrying in 1 secs.");
        //try{Thread.sleep(1000);}catch(InterruptedException e){}
        connectionError();
      }
    }
  }

  private void connectionError()
  {
    close();
  }

  private void eventChannelError()
  {
    if (reconnectAllowed && alive && !encryptEvents && firstFrameStarted)
    {
      // close the gfx sockets; this'll cause an error in the GFX loop which'll then cause it to do a reconnect
      System.out.println("Event channel error occurred...closing other sockets to force reconnect...");
      try
      {
        gfxSocket.close();
      }
      catch (Exception e){}
    }
    else
      close();
  }

  public String getServerName()
  {
    return serverName;
  }

  public void addTimerTask(java.util.TimerTask addMe, long delay, long period)
  {
    if (uiTimer == null)
      uiTimer = new java.util.Timer(true);
    if (period == 0)
      uiTimer.schedule(addMe, delay);
    else
      uiTimer.schedule(addMe, delay, period);
  }

  public MediaCmd getMediaCmd() { return myMedia; }
  public GFXCMD2 getGfxCmd() { return myGfx; }
  public boolean hasFontServer() { return fontServer; }

  public boolean isLocahostConnection()
  {
    return ("127.0.0.1".equals(serverName) || "localhost".equals(serverName));
  }
  public String getWindowTitle()
  {
    if (isLocahostConnection())
      return "SageTV";
    else
      return "SageTV Placeshifter";
  }

  public java.awt.Dimension getReportedScrSize()
  {
    return reportedScrSize;
  }

  public java.io.File getCachedImageFile(String resourceID)
  {
    return getCachedImageFile(resourceID, true);
  }
  public java.io.File getCachedImageFile(String resourceID, boolean verify)
  {
    if (cacheDir == null)
      return null;
    java.io.File cachedFile = new java.io.File(cacheDir, resourceID);
    return (!verify || (cachedFile.isFile() && cachedFile.length() > 0)) ? cachedFile : null;
  }

  public void saveCacheData(String resourceID, byte[] data, int offset, int length)
  {
    if (cacheDir == null)
      return;
    java.io.FileOutputStream fos = null;
    try
    {
      fos = new java.io.FileOutputStream(new java.io.File(cacheDir, resourceID));
      fos.write(data, offset, length);
    }
    catch (java.io.IOException ioe)
    {
      System.out.println("ERROR writing cache data to file of :" + ioe);
    }
    finally
    {
      if (fos != null)
      {
        try
        {
          fos.close();
        }
        catch (Exception e){}
      }
    }
  }

  private String getOfflineCacheList()
  {
    if (cacheDir == null)
      return "";
    StringBuffer sb = new StringBuffer();
    java.io.File[] cacheFiles = cacheDir.listFiles();
    for (int i = 0; cacheFiles != null && i < cacheFiles.length; i++)
    {
      sb.append(cacheFiles[i].getName());
      sb.append("|");
    }
    return sb.toString();
  }

  private void cleanupOfflineCache()
  {
    // Cleanup the offline cache...just dump the oldest half of it
    java.io.File[] cacheFiles = cacheDir.listFiles();
    long size = 0;
    for (int i = 0; i < cacheFiles.length; i++)
    {
      size += cacheFiles[i].length();
      if (size > offlineImageCacheLimit)
      {
        System.out.println("Dumping offline image cache because it's exceeded the maximum size");
        java.util.Arrays.sort(cacheFiles, new java.util.Comparator()
        {
          public int compare(Object o1, Object o2)
          {
            java.io.File f1 = (java.io.File) o1;
            java.io.File f2 = (java.io.File) o2;
            long l1 = f1.lastModified();
            long l2 = f2.lastModified();
            if (l1 < l2)
              return -1;
            else if (l1 > l2)
              return 1;
            else
              return 0;
          }
        });
        for (int j = 0; j < cacheFiles.length / 2; j++)
          cacheFiles[j].delete();
        break;
      }
    }
  }

  public void registerImageAccess(int handle)
  {
    lruImageMap.put(new Integer(handle), new Long(System.currentTimeMillis()));
  }

  public void clearImageAccess(int handle)
  {
    lruImageMap.remove(new Integer(handle));
  }

  public int getOldestImage()
  {
    java.util.Iterator walker = lruImageMap.entrySet().iterator();
    Integer oldestHandle = null;
    long oldestTime = Long.MAX_VALUE;
    while (walker.hasNext())
    {
      java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
      long currTime = ((Long) ent.getValue()).longValue();
      if (currTime < oldestTime)
      {
        oldestTime = currTime;
        oldestHandle = (Integer) ent.getKey();
      }
    }
    return (oldestHandle == null) ? 0 : oldestHandle.intValue();
  }

  public boolean doesUseAdvancedImageCaching()
  {
    return usesAdvancedImageCaching;
  }

  private java.net.Socket gfxSocket;
  private java.net.Socket mediaSocket;
  private MiniLIRC lircReader;
  private sage.MacIRC macIRC;
  private WinInfraredReceive winIR;
  private String serverName;
  private int port;
  private String myID;
  private java.io.DataInputStream gfxIs;

  private MediaCmd myMedia;

  // This is the secret symmetric key encrypted with the public key
  private byte[] encryptedSecretKeyBytes;
  private java.security.PublicKey serverPublicKey;
  private javax.crypto.Cipher evtEncryptCipher;
  private java.security.Key mySecretKey;

  private boolean encryptEvents;

  private java.io.DataOutputStream eventChannel;
  private int replyCount;
  private GFXCMD2 myGfx;

  private boolean alive;

  private String currentCrypto = MiniClient.cryptoFormats;
  private boolean useLocalNetworkOptimizations;

  private boolean fontServer;
  private java.util.Timer uiTimer;

  private java.io.File tempfile;
  private java.io.File tempfile2; // TODO: add function to get name for mplayer
  private java.nio.ByteBuffer mappedVideo;
  private int serverfd=-1;
  private int socketfd=-1;
  private String mappedfname;
  private int videowidth=0;
  private int videoheight=0;
  private int videoformat=0;
  private int videoframetype=0;

  private int fsSecurity;

  private boolean subSupport = false;

  // We need this for being able to store the auth block in the properties file correctly
  private MiniClientManagerFrame.MgrServerInfo msi;

  private java.awt.Dimension reportedScrSize;
  private boolean zipMode;
  protected long offlineImageCacheLimit;
  protected java.io.File cacheDir;
  private java.util.Map lruImageMap = new java.util.HashMap();;
  private boolean usesAdvancedImageCaching;

  private boolean reconnectAllowed;
  private boolean firstFrameStarted;
  private boolean performingReconnect;
}
