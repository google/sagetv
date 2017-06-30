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

public class MiniClientSageRenderer extends SageRenderer
    implements NativeImageAllocator
{
  private static final boolean DEBUG_NATIVE2D = false;
  private static final boolean DEBUG_REMOTEFS = false;
  private java.nio.channels.SocketChannel clientSocket;
  private java.nio.ByteBuffer sockBuf = java.nio.ByteBuffer.allocate(65536);

  private static boolean localHostConnectionInUse = false;
  private static int numFreebies = 0;

  private static boolean alive;
  private static java.nio.channels.ServerSocketChannel ss;
  private static Thread ssThread;
  private static java.util.Map clientSocketMap = new java.util.HashMap();
  private static java.util.Map clientSocketMapTimes = new java.util.HashMap();
  private static java.util.Map clientPlayerSocketMap = new java.util.HashMap();
  private static java.util.Map clientPlayerSocketMapTimes = new java.util.HashMap();
  private static Object mapLock = new Object();

  private static java.util.Map externalFSXferAuths = new java.util.HashMap();

  private static final long CLIENT_EXPIRE_TIME = 30000;

  private static final int NUM_SAMPLES_BANDWIDTH_ESTIMATE = 5;

  private static final int MAX_WINDOW_SIZE = 3000;

  private static final int HI_RES_SURFACE_SIZE_LIMIT = 2048;

  private static final int MAX_CMD_LEN = 16777215;

  public static final int DRAWING_CMD_TYPE = 16;
  public static final int DRAWING_CMD_TYPE_SHIFTED = (16 << 24);
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
  public static final int ASYNC_DIRECT_LOAD_COMPLETE = 228;  // int-handle, int-result(0 success)

  public static final int GFXCMD_INIT = 1;
  // mode

  public static final int GFXCMD_DEINIT = 2;

  public static final int GFXCMD_DRAWRECT = 16;
  // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL

  public static final int GFXCMD_FILLRECT = 17;
  // x, y, width, height, argbTL, argbTR, argbBR, argbBL

  public static final int GFXCMD_CLEARRECT = 18;
  // x, y, width, height, argbTL, argbTR, argbBR, argbBL

  public static final int GFXCMD_DRAWOVAL = 19;
  // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL,
  // clipX, clipY, clipW, clipH

  public static final int GFXCMD_FILLOVAL = 20;
  // x, y, width, height, argbTL, argbTR, argbBR, argbBL,
  // clipX, clipY, clipW, clipH

  public static final int GFXCMD_DRAWROUNDRECT = 21;
  // x, y, width, height, thickness, arcRadius,
  // argbTL, argbTR, argbBR, argbBL,
  // clipX, clipY, clipW, clipH

  public static final int GFXCMD_FILLROUNDRECT = 22;
  // x, y, width, height, arcRadius, argbTL, argbTR, argbBR, argbBL,
  // clipX, clipY, clipW, clipH

  public static final int GFXCMD_DRAWTEXT = 23;
  // x, y, len, text, handle, argb, clipX, clipY, clipW, clipH

  public static final int GFXCMD_DRAWTEXTURED = 24;
  // x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend

  public static final int GFXCMD_DRAWLINE = 25;
  // x1, y1, x2, y2, argb1, argb2

  public static final int GFXCMD_LOADIMAGE = 26;
  // width, height, [format]

  public static final int GFXCMD_UNLOADIMAGE = 27;
  // handle

  public static final int GFXCMD_LOADFONT = 28;
  // namelen, name, style, size

  public static final int GFXCMD_UNLOADFONT = 29;
  // handle

  public static final int GFXCMD_FLIPBUFFER = 30;
  public static final int GFXCMD_STARTFRAME = 31;

  public static final int GFXCMD_LOADIMAGELINE = 32;
  // handle, line, len, data

  public static final int GFXCMD_PREPIMAGE = 33;
  // width, height, [cache resource id]

  public static final int GFXCMD_LOADIMAGECOMPRESSED = 34;
  // handle, len, data

  public static final int GFXCMD_XFMIMAGE = 35;
  // srcHandle, destHandle, destWidth, destHeight, maskCornerArc

  public static final int GFXCMD_LOADFONTSTREAM = 36;
  // namelen, name, len, data

  public static final int GFXCMD_CREATESURFACE = 37;
  // width, height

  public static final int GFXCMD_SETTARGETSUFACE = 38;
  // handle

  public static final int GFXCMD_LOADIMAGEDIRECT = 39;
  // handle, len, data

  public static final int GFXCMD_DRAWTEXTUREDDIFFUSE = 40;
  // x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend, diffhandle, diffsrcx, diffsrcy, diffsrcwidth, diffsrcheight

  public static final int GFXCMD_PUSHTRANSFORM = 41;
  // v'= matrix * v
  // sent by row, then col, 12 values (skip the 4th row since its fixed)

  public static final int GFXCMD_POPTRANSFORM = 42;

  public static final int GFXCMD_TEXTUREBATCH = 43;
  // count, size

  public static final int GFXCMD_LOADCACHEDIMAGE = 44;
  // handle, width, height, cacheResourceID

  public static final int GFXCMD_LOADIMAGETARGETED = 45;
  // handle, width, height, [format]

  public static final int GFXCMD_PREPIMAGETARGETED = 46;
  // handle, width, height, [cache resource id] (but this will never actually load from the offline cache, this is only for knowing where to cache it)

  public static final int GFXCMD_SETCURSORPROP = 47;
  // mode, x, y, state, width, height, data

  public static final int GFXCMD_LOADIMAGEDIRECTASYNC = 48;
  // handle, len, data

  public static final int GFXCMD_SCREENSHOT = 49;
  // returns width, height, format (0=RGBA32), buffer data (w*h*Bpp)

  public static final int GFXCMD_SETVIDEOPROP = 130;
  // mode, sx, sy, swidth, sheight, ox, oy, owidth, oheight, alpha, activewin

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
  // secureID[4], offset[8], size[8], pathlen, path

  public static final int FSCMD_DELETE_FILE = 72;
  // pathlen, path

  public static final int CMD_LEN_BITS = 24;

  // We add this before we do the floor to compensate for floating point rounding errors in
  // the layout math. Otherwise when we get values like 79.99999 (which should be 80), we
  // end up having single pixel lines of corruption.
  static final float FLOAT_ERROR = 0.001f;

  public static final int GFX_SCALING_NONE = 0;
  public static final int GFX_SCALING_SW = 0x1;
  public static final int GFX_SCALING_HW = 0x2;
  public static final int GFX_SCALING_FLIPH = 0x4;
  public static final int GFX_SCALING_FLIPV = 0x8;

  public static final int IMAGE_FORMAT_ARGB32 = 0;
  public static final int IMAGE_FORMAT_8BPP = 1;
  public static final int IMAGE_FORMAT_HIRESYUV = 256;

  public static final int VIDEO_MODE_MASK_SOURCE = 0x1;
  public static final int VIDEO_MODE_MASK_OUTPUT = 0x2;
  public static final int VIDEO_MODE_MASK_ALPHA = 0x4;
  public static final int VIDEO_MODE_MASK_HANDLE = 0x8;
  public static final int VIDEO_MODE_MASK_TL_ORIGIN = 0x10;

  // Separate means we store RGB & YUV in separate memory spaces and that we also must not use the YUV
  // texture space while the media player is loaded
  public static final int YUV_CACHE_NONE = 0;
  public static final int YUV_CACHE_SEPARATE = 1;
  public static final int YUV_CACHE_UNIFIED = 2;

  public static final int GFX_CURSOR_STATE_AUTO = 0;
  public static final int GFX_CURSOR_STATE_ON = 1;
  public static final int GFX_CURSOR_STATE_OFF = 2;

  public static final int GFX_CURSOR_FLAG_POS = 1;
  public static final int GFX_CURSOR_FLAG_STATE = 2;
  public static final int GFX_CURSOR_FLAG_DATA = 4;

  private static int hasRSASupport = -1;
  private static java.security.KeyPairGenerator rsaKeyGen;
  private static java.security.KeyPairGenerator dhKeyGen;

  public static class SocketChannelInfo
  {
    private java.nio.channels.SocketChannel channel;
    private String clientName;
    private java.util.Map socketMap;
    private java.util.Map socketMapTimes;

    public SocketChannelInfo(String clientName, java.nio.channels.SocketChannel channel,
        java.util.Map socketMap, java.util.Map socketMapTimes)
    {
      this.channel = channel;
      this.clientName = clientName;
      this.socketMap = socketMap;
      this.socketMapTimes = socketMapTimes;
    }

    public java.nio.channels.SocketChannel getSocketChannel()
    {
      return this.channel;
    }
  }

  public static void startUIServer(final boolean allowNonlocalhostConnection)
  {
    if (ss != null) return;
    alive = true;

    // Fill in the map for authorized file transfers from external clients (used for sending media to friends)
    String extAuthXferStr = Sage.get("external_authorized_file_transfers", "");
    java.util.StringTokenizer toker = new java.util.StringTokenizer(extAuthXferStr, "|");
    while (toker.hasMoreTokens())
    {
      String nextToke = toker.nextToken();
      int idx = nextToke.indexOf(':');
      if (idx != -1)
      {
        try
        {
          Integer inty = new Integer(nextToke.substring(0, idx));
          java.io.File myFile = new java.io.File(nextToke.substring(idx + 1));
          externalFSXferAuths.put(inty, myFile);
        }
        catch (NumberFormatException nfe)
        {
          if (Sage.DBG) System.out.println("ERROR with formatting of external_authorized_file_transfers property:" + nfe + " prop=" + nextToke);
        }
      }
    }

    ssThread = new Thread("MiniUIServer")
    {
      public void run()
      {
        clientSocketMap.clear();
        clientSocketMapTimes.clear();
        java.nio.channels.Selector selector = null;
        while (alive)
        {
          try
          {
            selector = java.nio.channels.Selector.open();
            ss = java.nio.channels.ServerSocketChannel.open();
            ss.configureBlocking(false);
            ss.socket().bind(allowNonlocalhostConnection ? new java.net.InetSocketAddress(Sage.getInt("extender_and_placeshifter_server_port", 31099)) :
              new java.net.InetSocketAddress("127.0.0.1", Sage.getInt("extender_and_placeshifter_server_port", 31099)));
            //ss.socket().setSoTimeout(30000); // timeouts don't work with NIO server sockets
            ss.register(selector, java.nio.channels.SelectionKey.OP_ACCEPT);
            while (alive)
            {
              // Clean out any old entries from the socket map
              synchronized (mapLock)
              {
                java.util.Iterator walker = clientSocketMapTimes.entrySet().iterator();
                while (walker.hasNext())
                {
                  java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();

                  Long time = (Long) ent.getValue();
                  if (Sage.eventTime() - time.longValue() > CLIENT_EXPIRE_TIME)
                  {
                    if (Sage.DBG) System.out.println("Dropping old MiniUI connection because it's old from:" + ent.getKey());
                    try{((java.nio.channels.SocketChannel)clientSocketMap.remove(ent.getKey())).close();}catch(Exception e){};
                    walker.remove();
                    mapLock.notifyAll();
                  }
                }
                walker = clientPlayerSocketMapTimes.entrySet().iterator();
                while (walker.hasNext())
                {
                  java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();

                  Long time = (Long) ent.getValue();
                  if (Sage.eventTime() - time.longValue() > CLIENT_EXPIRE_TIME && UIManager.getLocalUIByName(ent.getKey().toString()) == null)
                  {
                    if (Sage.DBG) System.out.println("Dropping old MiniPlayer connection because it's old from:" + ent.getKey());
                    try{((java.nio.channels.SocketChannel)clientPlayerSocketMap.remove(ent.getKey())).close();}catch(Exception e){};
                    walker.remove();
                    mapLock.notifyAll();
                  }
                }
              }
              try
              {
                final java.nio.channels.SocketChannel sake = ss.accept();
                if (sake == null)
                {
                  // Use the NIO selector w/ a timeout to see if we have any connections to accept
                  // This'll return when something's ready, the timeout expires, or there's an error; all of which are a perfect
                  // time to try again
                  selector.select(10000);
                }
                else
                {
                  if (Sage.DBG) System.out.println("MiniUI got connection from " + sake);
                  if (!allowNonlocalhostConnection && !IOUtils.isLocalhostSocket(sake.socket()))
                  {
                    try
                    {
                      sake.close();
                    }catch (Exception e){}
                  }
                  else
                  {
                    Pooler.execute(new Runnable()
                    {
                      public void run()
                      {
                        if (!handleNewConnection(sake))
                        {
                          try
                          {
                            sake.close();
                          }catch (Exception e){}
                        }
                      }
                    }, "MiniUIServerConnection");
                  }
                }
              }
              catch (java.net.SocketTimeoutException et)
              {}
            }
          }
          catch (java.io.IOException e)
          {
            if (Sage.DBG) System.out.println("MiniUI ServerSocket died from:" + e);
          }
          finally
          {
            if (ss != null)
            {
              try
              {
                ss.close();
              }
              catch (Exception e){}
              ss = null;
            }
            if (selector != null)
            {
              try
              {
                selector.close();
              }
              catch (Exception e){}
              selector = null;
            }
          }
          if (alive)
          {
            if (Sage.DBG) System.out.println("Waiting to try to open MiniUIServer socket again...");
            try{Thread.sleep(15000);}catch (Exception e){}
          }
        }
        ssThread = null;
      }
    };
    ssThread.setDaemon(true);
    ssThread.start();
  }

  public static void stopUIServer()
  {
    alive = false;
    if (ss != null)
    {
      try{ss.close();}catch(Exception e){}
      ss = null;
    }
    Thread t = ssThread;
    if (t != null)
    {
      try { t.join(5000);} catch(Exception e){}
    }
  }

  public static boolean isClientLocalhost(String clientName)
  {
    java.nio.channels.SocketChannel sake = (java.nio.channels.SocketChannel) clientSocketMap.get(clientName);
    return sake != null && IOUtils.isLocalhostSocket(sake.socket());
  }

  private static boolean handleNewConnection(java.nio.channels.SocketChannel sake)
  {
    try
    {
      long timeout = Sage.getInt("ui/remote_ui_connection_timeout", 15000);
      sake.socket().setKeepAlive(true);
      sake.socket().setTcpNoDelay(true);
      java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(2048);
      bb.clear().limit(8);
      TimeoutHandler.registerTimeout(timeout, sake);
      do{
        int x = sake.read(bb);
        if (x < 0)
          throw new java.io.EOFException();
      } while (bb.remaining() > 0);
      bb.flip();
      // Version
      byte ver = bb.get();
      if (ver == 1)
      {
        TimeoutHandler.clearTimeout(sake);
        // MAC
        // Convert the MAC address to string
        String clientName = "";
        for(int i = 0; i <= 5; i++)
        {
          int val = bb.get() & 0xFF;
          if (val < 0x10) clientName += "0";
          clientName += Integer.toHexString(val);
        }
        // next byte is 0 for UI and 1 for player; anything else is for ping
        int nextByte = bb.get();
        boolean uiChannel = nextByte == 0;
        // If this is a reconnect then the reply is important and we can't send the wrong value
        int replyVal = 2;
        if (nextByte == 5)
        {
          // This is a reconnect.
          UIManager oldUI = UIManager.getLocalUIByName(clientName);
          if (oldUI == null)
            replyVal = 0;
        }
        // Reply OK
        bb.clear();
        bb.put((byte)replyVal);
        bb.flip();
        while (bb.hasRemaining())
          sake.write(bb);
        if (uiChannel)
        {
          if (Sage.DBG) System.out.println("MiniUI is adding to its map:" + clientName);
          // If this UI already exists, then kill it because it's probably stale
          UIManager oldUI = UIManager.getLocalUIByName(clientName);
          if (oldUI != null)
            oldUI.goodbye();
          synchronized (mapLock)
          {
            clientSocketMap.put(clientName, sake);
            clientSocketMapTimes.put(clientName, new Long(Sage.eventTime()));
            mapLock.notifyAll();
          }
          UIManager.createAltUI(clientName);
        }
        else if (nextByte == 1)
        {
          TimeoutHandler.clearTimeout(sake);
          if (Sage.DBG) System.out.println("MiniPlayer is adding to its map:" + clientName);
          synchronized (mapLock)
          {
            if (clientPlayerSocketMap.containsKey(clientName))
            {
              try{((java.nio.channels.SocketChannel)clientPlayerSocketMap.remove(clientName)).close();}catch(Exception e){};
            }
            clientPlayerSocketMap.put(clientName, sake);
            clientPlayerSocketMapTimes.put(clientName, new Long(Sage.eventTime()));
            mapLock.notifyAll();
          }
        }
        else if (nextByte == 2)
        {
          TimeoutHandler.clearTimeout(sake);
          if (Sage.DBG) System.out.println("MiniFS got connection from client:" + clientName);
          // Now we find the MiniClientSageRenderer that corresponds to this connection; then initiate the FS operation
          // in a new thread for that client
          UIManager myUI = UIManager.getLocalUIByName(clientName);
          if (myUI != null)
          {
            ZRoot rooty = myUI.getRootPanel();
            if (rooty != null && rooty.getRenderEngine() instanceof MiniClientSageRenderer)
            {
              MiniClientSageRenderer mcsr = (MiniClientSageRenderer) rooty.getRenderEngine();
              mcsr.processFSSocketConn(sake);
            }
          }
        }
        else if (nextByte == 3)
        {
          TimeoutHandler.clearTimeout(sake);
          if (Sage.DBG) System.out.println("MiniExternalFS got connection from client:" + clientName);
          // The file transfer ID is sent as hex for the MAC; so lookup that
          int magicTransferID = (int)(Long.parseLong(clientName, 16) & 0xFFFFFFFF);
          if (externalFSXferAuths.containsKey(new Integer(magicTransferID)))
          {
            if (Sage.DBG) System.out.println("The requested external file transfer is authorized for: " + externalFSXferAuths.get(new Integer(magicTransferID)));
            // First we send back 8 bytes for the file size; then the client will send back 8 bytes to indicate the file offset it wants to
            // start transferring from. The bytes from the file are then sent after that starting at the offset up until the file size. Then we read back
            // a 32-bit reply value to confirm all the data was received properly. At that point we de-authorize this file transfer from occuring anymore.

            // We've already been re-threaded off the main server thread so we can safely process all of this here
            java.io.File xferFile = (java.io.File) externalFSXferAuths.get(new Integer(magicTransferID));
            bb.clear().limit(8);
            long fileSize = xferFile.length();
            bb.putLong(fileSize);
            bb.flip();
            while (bb.hasRemaining())
              sake.write(bb);
            if (fileSize == 0) // means it doesn't exist or is empty; same thing
              return false;
            bb.clear().limit(8);
            TimeoutHandler.registerTimeout(timeout, sake);
            do{
              int x = sake.read(bb);
              if (x < 0)
                throw new java.io.EOFException();
            } while (bb.remaining() > 0);
            TimeoutHandler.clearTimeout(sake);
            bb.flip();
            long fileOffset = bb.getLong();
            if (Sage.DBG) System.out.println("External file transfer of " + xferFile + " size=" + fileSize + " offset=" + fileOffset);
            if (fileOffset >= fileSize)
            {
              if (Sage.DBG) System.out.println("File offset requested was greater than file size! De-authorize the transfer now.");
              externalFSXferAuths.remove(new Integer(magicTransferID));
              writeExternalFSXferProp();
              return false;
            }

            // Now we can start transferring the file data to the client
            java.nio.channels.FileChannel localRaf = null;
            localRaf = new java.io.FileInputStream(xferFile).getChannel();
            long xferSize = fileSize - fileOffset;
            while (xferSize > 0)
            {
              long currSize = Math.min(xferSize, 16384);
              TimeoutHandler.registerTimeout(timeout*10, sake);
              currSize = localRaf.transferTo(fileOffset, currSize, sake);
              TimeoutHandler.clearTimeout(sake);
              xferSize -= currSize;
              fileOffset += currSize;
            }
            // Now read back the 32-bit RV for the transfer so we know the client has written it to disk completely,
            // only the bottom byte will matter
            bb.clear().limit(4);
            TimeoutHandler.registerTimeout(timeout, sake);
            while (bb.hasRemaining())
            {
              int x = sake.read(bb);
              if (x < 0)
                throw new java.io.EOFException();
            }
            TimeoutHandler.clearTimeout(sake);
            bb.flip();
            int rv = bb.get() | bb.get() | bb.get() | bb.get();
            if (rv == 0)
            {
              // Successful transfer! De-authorize it.
              if (Sage.DBG) System.out.println("File transfer has completed successfully of " + xferFile + " remove it from the authorized list");
              externalFSXferAuths.remove(new Integer(magicTransferID));
              writeExternalFSXferProp();
            }
            else
            {
              if (Sage.DBG) System.out.println("File transfer has completed with a failure code of " + rv + " for " + xferFile + " keep it in the authorized list...");
            }
          }
        }
        else if (nextByte == 4)
        {
          // Server push notification for us to contact the locator server due to new messages/friends
          SageTV.getLocatorClient().kickLocator();
          return false;
        }
        else if (nextByte == 5)
        {
          // This is a reconnect.
          UIManager oldUI = UIManager.getLocalUIByName(clientName);
          if (oldUI != null)
          {
            if (Sage.DBG) System.out.println("MCSR got a reconnect request and we found the matching UI for " + clientName + " setup the reconnection!");
            ((MiniClientSageRenderer) oldUI.getRootPanel().getRenderEngine()).useReconnectSocket(sake);
          }
        }
        return true;
      }
      else if (ver == 'G' && bb.get() == 'E' && bb.get() == 'T' && bb.get() == ' ')
      {
        // Now pass this off to the HTTPLSServer and it will handle everything from there.
        if (Sage.DBG) System.out.println("Received GET request on MiniClient channel");
        TimeoutHandler.clearTimeout(sake);
        new HTTPLSServer(bb, sake);
        return true;
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("Error with new MiniUI connection:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    finally
    {
      // This may have already been cleared; but it's safe to clear it again
      TimeoutHandler.clearTimeout(sake);
    }
    return false;
  }

  public static int authorizeExternalFileTransfer(java.io.File filePath)
  {
    int secureKey = new java.util.Random().nextInt();
    // Ensure it is unique
    while (externalFSXferAuths.containsKey(new Integer(secureKey)))
      secureKey = new java.util.Random().nextInt();
    externalFSXferAuths.put(new Integer(secureKey), filePath);
    writeExternalFSXferProp();
    return secureKey;
  }

  public static void deauthorizeExternalFileTransfer(int transferID)
  {
    Integer inty = new Integer(transferID);
    if (externalFSXferAuths.containsKey(inty))
    {
      externalFSXferAuths.remove(inty);
      writeExternalFSXferProp();
    }
  }

  private static void writeExternalFSXferProp()
  {
    String propValue = "";
    java.util.Iterator walker = externalFSXferAuths.entrySet().iterator();
    while (walker.hasNext())
    {
      java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
      propValue = propValue + ent.getKey() + ":" + ent.getValue() + "|";
    }
    Sage.put("external_authorized_file_transfers", propValue);
  }

  public java.net.Socket getPlayerSocket()
  {
    java.nio.channels.SocketChannel sc = getPlayerSocketChannel();
    if (sc != null)
      return sc.socket();
    else
      return null;
  }
  public java.nio.channels.SocketChannel getPlayerSocketChannel()
  {
    return getPlayerSocketChannel(uiMgr.getLocalUIClientName(), this);
  }
  public static java.nio.channels.SocketChannel getPlayerSocketChannel(String clientName,
      MiniClientSageRenderer mcsr)
  {
    return getSocketChannel(clientName, mcsr, clientPlayerSocketMap, clientPlayerSocketMapTimes);
  }

  public static void releaseSocketChannel(SocketChannelInfo info)
  {
    synchronized(mapLock)
    {
      if (Sage.DBG)System.out.println("MCSR client socket returned from " + info.clientName);
      info.socketMap.put(info.clientName, info.getSocketChannel());
      info.socketMapTimes.put(info.clientName, new Long(Sage.eventTime()));
    }
  }

  public static void closeSocketChannel(SocketChannelInfo info)
      throws java.io.IOException {

    if (Sage.DBG)System.out.println("Closing MCSR client socket for " + info.clientName + ", miniclient must reconnect again");
    info.getSocketChannel().close();
  }

  /**
   * Temporary - this method returns a socket channel that cannot be released, only disconnected.
   */
  public static java.nio.channels.SocketChannel getSocketChannel(String clientName,
      MiniClientSageRenderer mcsr, java.util.Map socketMap, java.util.Map socketMapTimes)
  {
    return getSocketChannelInfo(clientName, mcsr, socketMap, socketMapTimes).getSocketChannel();
  }

  public static SocketChannelInfo getSocketChannelInfo(String clientName,
      MiniClientSageRenderer mcsr, java.util.Map socketMap, java.util.Map socketMapTimes)
  {
    synchronized (mapLock)
    {
      if (clientName == null)
      {
        // Just take whatever is in the map
        if (!socketMap.isEmpty())
          clientName = socketMap.keySet().iterator().next().toString();
      }
      java.nio.channels.SocketChannel sake = (java.nio.channels.SocketChannel) socketMap.get(clientName);
      long startWait = Sage.eventTime();
      boolean reconnectLeft = (mcsr != null && mcsr.supportsForcedMediaReconnect);
      while (true)
      {
        long maxWait = reconnectLeft ? 5000 : CLIENT_EXPIRE_TIME;
        while (sake == null && Sage.eventTime() - startWait < maxWait)
        {
          try
          {
            mapLock.wait(2000);
          }
          catch (InterruptedException e){}
          if (clientName == null)
          {
            // Just take whatever is in the map
            if (!socketMap.isEmpty())
              clientName = socketMap.keySet().iterator().next().toString();
          }
          sake = (java.nio.channels.SocketChannel) socketMap.get(clientName);
        }
        if (sake != null)
        {
          if (Sage.DBG)System.out.println("Issuing MCSR client socket to " + clientName);
          socketMap.remove(clientName);
          socketMapTimes.remove(clientName);
        }
        else if (reconnectLeft && mcsr != null)
        {
          reconnectLeft = false;
          if (Sage.DBG) System.out.println("Did not find a player socket connection; notifying the client to force a reconnect on the other channel");
          mcsr.forceMediaReconnect();
          continue;
        }
        return new SocketChannelInfo(clientName, sake, socketMap, socketMapTimes);
      }
    }
  }

  public MiniClientSageRenderer(ZRoot inMaster)
  {
    super(inMaster);
    vf = uiMgr.getVideoFrame();
    initMini();
  }

  public boolean allocateBuffers(int width, int height)
  {
    return true;
  }

  private boolean shouldUseTinyIOSImage(MetaImage mi)
  {
    if (iPhoneMode && mi != null)
    {
      Object src = mi.getSource();
      if (src instanceof MetaImage.MediaFileThumbnail)
        return true;
      if (src instanceof java.net.URL)
        return true;
      if (src instanceof java.io.File)
      {
        if (((java.io.File) src).toString().startsWith(MediaFile.THUMB_FOLDER.toString()))
          return true;
      }
    }
    return false;
  }

  public void preprocessNextDisplayList(java.util.ArrayList v)
  {
    cullDisplayList(v);
    boolean clipGlyphRects = isMediaExtender() || !isLocalConnection() || uiMgr.areLayersEnabled() || ((gfxScalingCaps & GFX_SCALING_HW) != 0 && !xformRenderSupport);
    for (int i = 0; i < v.size(); i++)
    {
      RenderingOp op = (RenderingOp) v.get(i);
      if (!op.isAnimationOp() && !op.isEffectOp() && ((op.srcRect != null && (op.srcRect.width <= 0 || op.srcRect.height <= 0)) ||
          ((op.copyImageRect != null && (op.copyImageRect.width <= 0 || op.copyImageRect.height <= 0)))))
      {
        v.remove(i);
        i--;
      }
      if (op.isImageOp())
      {
        // Cache the image if its going to be scaled,
        // otherwise just ensure that cached image stays alive
        int currTextureLimit = maxTextureDim;
        if (shouldUseTinyIOSImage(op.texture))
          currTextureLimit /= 4;
        if (((gfxScalingCaps & GFX_SCALING_HW) == 0 || Sage.getBoolean("ui/enable_hardware_scaling_cache", false)) &&
            ((int)(op.copyImageRect.width + FLOAT_ERROR) !=
            op.texture.getWidth(op.textureIndex) ||
            (int)(op.copyImageRect.height + FLOAT_ERROR) !=
            op.texture.getHeight(op.textureIndex)))
        {
          float scaleX = op.texture.getWidth(op.textureIndex)
              / (float)(int)(op.copyImageRect.width + FLOAT_ERROR);
          float scaleY = op.texture.getHeight(op.textureIndex)
              / (float)(int)(op.copyImageRect.height + FLOAT_ERROR);
          // NOTE: On 8/27/04 I added the SCALE test
          /*                    if (MathUtils.isTranslateScaleOnlyMatrix(op.renderXform))
                    {
                        // Removing the scaling from the transformation and
                        // leave just the translation
                        float transX = MathUtils.getTranslateX(op.renderXform);
                        float transY = MathUtils.getTranslateY(op.renderXform);
                        op.renderXform = MathUtils.createTranslateMatrix(transX, transY);
                    }
                    else
                    {
                        op.renderXform =
                            new javax.vecmath.Matrix4f(op.renderXform);
                        MathUtils.scaleMatrix(op.renderXform, scaleX, scaleY);
                    }*/
          //                    op.srcRect.x /= scaleX;
          //                  op.srcRect.width /= scaleX;
          //                op.srcRect.height /= scaleY;
          //              op.srcRect.y /= scaleY;
          op.scaleSrc(1/scaleX, 1/scaleY);
          if (op.primitive != null && op.primitive.cornerArc > 0)
            op.textureIndex =
            op.texture.getImageIndex(
                (int)(op.copyImageRect.width + FLOAT_ERROR),
                (int)(op.copyImageRect.height + FLOAT_ERROR),
                new java.awt.geom.RoundRectangle2D.Float(0, 0,
                    (int)(op.copyImageRect.width + FLOAT_ERROR),
                    (int)(op.copyImageRect.height + FLOAT_ERROR),
                    op.primitive.cornerArc,
                    op.primitive.cornerArc));
          else if (op.privateData instanceof java.awt.Insets[])
          {
            op.textureIndex =
                op.texture.getImageIndex(
                    (int)(op.copyImageRect.width + FLOAT_ERROR),
                    (int)(op.copyImageRect.height + FLOAT_ERROR),
                    (java.awt.Insets[]) op.privateData);
          }
          else
            op.textureIndex =
            op.texture.getImageIndex(
                (int)(op.copyImageRect.width + FLOAT_ERROR),
                (int)(op.copyImageRect.height + FLOAT_ERROR));
        }
        else if ((gfxScalingCaps & GFX_SCALING_HW) != 0 &&
            (op.texture.getWidth(op.textureIndex) > currTextureLimit ||
                op.texture.getHeight(op.textureIndex) > currTextureLimit))
        {
          if (highResSurfCaps != YUV_CACHE_NONE && op.texture.getSource() instanceof MediaFile &&
              (highResSurfCaps == YUV_CACHE_UNIFIED || !uiMgr.getVideoFrame().isLoadingOrLoadedFile()) &&
              sage.media.format.MediaFormat.JPEG.equals(((MediaFile)op.texture.getSource()).getContainerFormat()))
          {
            currTextureLimit = HI_RES_SURFACE_SIZE_LIMIT;
            if (op.texture.getWidth(op.textureIndex) <= currTextureLimit &&
                op.texture.getHeight(op.textureIndex) <= currTextureLimit)
            {
              continue;
            }
          }
          // We're in danger of going beyond a maximum texture size limit, so scale the image
          // down on the server first.
          float xScale = Math.min(currTextureLimit,
              op.texture.getWidth(op.textureIndex))/((float)op.texture.getWidth(op.textureIndex));
          float yScale = Math.min(currTextureLimit,
              op.texture.getHeight(op.textureIndex))/((float)op.texture.getHeight(op.textureIndex));
          xScale = yScale = Math.min(xScale, yScale);
          op.scaleSrc(xScale, yScale);
          op.textureIndex = op.texture.getImageIndex(Math.round(xScale *
              op.texture.getWidth(op.textureIndex)), Math.round(yScale *
                  op.texture.getHeight(op.textureIndex)));
        }
      }
      else if (op.isTextOp() && useImageMapsForText)
      {
        convertGlyphsToCachedImages(op, clipGlyphRects);
      }

    }
  }

  public java.awt.Rectangle getIntRect(java.awt.geom.Rectangle2D.Float inRect, java.awt.Rectangle rv)
  {
    // It's faster to add 0.5f and then cast it to an integer since we always deal with positive numbers. That's what Math.round does,
    // it just adds 0.5f and then floors the result; but we don't need to actually use floor and can just cast instead.
    rv.x = (int)(inRect.x + 0.5f);
    rv.y = (int)(inRect.y + 0.5f);
    rv.width = (int)(inRect.width + 0.5f);
    rv.height = (int)(inRect.height + 0.5f);
    //        java.awt.Rectangle rv = new java.awt.Rectangle(
    //            (int)Math.floor(inRect.x + FLOAT_ERROR), (int)Math.floor(inRect.y + FLOAT_ERROR),
    //            (int)Math.floor(inRect.x + inRect.width + FLOAT_ERROR) - (int)Math.floor(inRect.x + FLOAT_ERROR),
    //			(int)Math.floor(inRect.height + inRect.y + FLOAT_ERROR) - (int)Math.floor(inRect.y + FLOAT_ERROR));
    //        java.awt.Rectangle rv = new java.awt.Rectangle(
    //            (int)Math.floor(inRect.x), (int)Math.floor(inRect.y),
    //            (int)Math.floor(inRect.x + inRect.width) - (int)Math.floor(inRect.x),
    //            (int)Math.floor(inRect.y + inRect.height) -
    //            (int)Math.floor(inRect.y));
    //        rv.setFrame(inRect);
    return rv;
  }

  private void precacheImages()
  {
    // We want to preload images if we're doing video rendering with the 3D system so that it doesn't slow down
    // the frame rate of the video with image loading during the rendering process.
    if (xformRenderSupport && !isMediaExtender() && vf.hasFile())
    {
      for (int i = 0; i < currDisplayList.size(); i++)
      {
        RenderingOp op = (RenderingOp) currDisplayList.get(i);
        if (op.isTextOp())
        {
          MetaImage fontImage = op.text.fontImage;
          int[] imgNumCache = op.text.renderImageNumCache;
          if (fontImage != null && imgNumCache != null)
          {
            for (int j = 0; j < imgNumCache.length; j++)
            {
              int x = imgNumCache[j];
              if (x != -1)
              {
                fontImage.getNativeImage(this, x);
                fontImage.removeNativeRef(this, x);
              }
            }
          }
        }
        else if (op.isImageOp())
        {
          // Since we're not guaranteed to be allocated before this call
          op.texture.getNativeImage(this, op.textureIndex);
          op.texture.removeNativeRef(this, op.textureIndex);
        }
      }
    }
  }

  private boolean checkForAuthUpdates()
  {
    if (pendingClientAuthSet != null)
    {
      if (recvr.isEncryptionOn())
      {
        // We need to encrypt this with the symmetric key before sending it to the client
        try
        {
          javax.crypto.Cipher encryptCipher = javax.crypto.Cipher.getInstance(evtDecryptCipher.getAlgorithm());
          encryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, mySecretKey);
          byte[] cryptDat = encryptCipher.doFinal(pendingClientAuthSet.getBytes(Sage.BYTE_CHARSET));
          int propRV = sendSetProperty("SET_CACHED_AUTH".getBytes(Sage.BYTE_CHARSET), cryptDat);
          if (propRV != 0)
          {
            if (Sage.DBG) System.out.println("ERROR: MiniClient did not succeed with setting cached auth block in client");
          }
          else
          {
            if (Sage.DBG) System.out.println("Cached authentication info was received by client!");
          }
        }
        catch (java.security.GeneralSecurityException gse)
        {
          if (Sage.DBG) System.out.println("ERROR could not do crypto to set auth:" + gse);
        }
        catch (java.io.IOException e)
        {
          if (Sage.DBG) System.out.println("Error w/ MiniClient UI:" + e);
          killUIMgrAsync();
          return false;
        }
      }
      else
      {
        if (Sage.DBG) System.out.println("ERROR aborting sending the client auth block because encryption has been disabled!");
      }
      pendingClientAuthSet = null;
    }
    return true;
  }

  private boolean checkForCryptoUpdates()
  {
    if (nextEventCryptoState != null)
    {
      boolean killNow = false;
      synchronized (this)
      {
        recvr.nextReplyIsCryptoStatus(nextEventCryptoState.booleanValue());
        try
        {
          int propRV = sendSetProperty("CRYPTO_EVENTS_ENABLE", nextEventCryptoState.booleanValue() ? "TRUE" : "FALSE");
          nextEventCryptoState = null;
          if (propRV != 0)
          {
            if (Sage.DBG) System.out.println("MiniClient did not succeed with toggling event encryption, errcode=" + propRV);
            killNow = true;
          }
        }
        catch (java.io.IOException e)
        {
          if (Sage.DBG) System.out.println("Error w/ MiniClient UI:" + e);
          killNow = true;
        }
      }
      if (killNow)
      {
        killUIMgrAsync();
        return false;
      }
    }
    return true;
  }

  private boolean checkForOutputModeUpdates()
  {
    if (updateOutputModeInfo)
    {
      updateOutputModeInfo = false;
      String currDetails = null;
      String currRes = null;
      if (displayResolution != null)
      {
        currRes = displayResolution.getFormatName();
        currDetails = (String) resolutionNameMap.get(currRes);
      }
      // Re-send the DAR; that's needed after an HDMI change
      lastSetDAR = 0;
      try
      {
        setupDigitalResolutions(true);
        if (!"None".equalsIgnoreCase(hdmiAutodetectedConnector) && uiMgr.getBoolean("hdmi_always_select_best", false) &&
            preferredResolutions.length > 0)
        {
          currRes = preferredResolutions[preferredResolutions.length - 1];
          displayResolution = sage.media.format.VideoFormat.buildVideoFormatForResolution(currRes);
          if (Sage.DBG) System.out.println("Auto-selecting the best HDMI resolution of " + currRes + " " + displayResolution);
          // If there was another resolution change push done...we should clear that here so it doesn't override our autoselect
          // It may have been set in the STV as a default 480i mode on startup which is the case I've seen this happen in and
          // then our autoselect happens here but the other mode is processed afterwards
          synchronized (remoteResolutionChangeLock)
          {
            if (remoteResolutionChange != null)
            {
              if (Sage.DBG) System.out.println("Clearing remoteResolutionChange push so it doesn't override our autoselect of resolution pushed: " + remoteResolutionChange);
              remoteResolutionChange = null;
              // Set this to null as well so we can be sure this resolution push we want here goes through. Otherwise if the remoteResolutionChange
              // matches the target resolution we select here it won't get set because displayResolution will already have the target resolution in it
              // and that's where the currDetails value comes from.
              currDetails = null;
            }
          }
        }
        if (currRes != null)
        {
          String newDetails = (String) resolutionNameMap.get(currRes);
          if (newDetails != null && !newDetails.equals(currDetails))
          {
            if (Sage.DBG) System.out.println("Setting GFX_RESOLUTION to be: " + newDetails + " as part of an output mode change");
            int propRV = sendSetProperty("GFX_RESOLUTION", newDetails);
            if (propRV != 0)
            {
              if (Sage.DBG) System.out.println("MiniClient did not succeed with resolution update to:" + newDetails + ", errcode=" + propRV);
            }
          }
        }
      }
      catch (java.io.IOException e)
      {
        if (Sage.DBG) System.out.println("Error w/ MiniClient UI:" + e);
        connectionError();
        return false;
      }
    }
    return true;
  }

  private boolean checkForFullScreenChangeUpdates()
  {
    if (fullScreenChange != null)
    {
      try
      {
        int propRV = sendSetProperty("GFX_RESOLUTION", fullScreenChange.booleanValue() ? "FULLSCREEN" : "WINDOW");
        if (propRV != 0)
        {
          if (Sage.DBG) System.out.println("MiniClient did not succeed with full screen switch, errcode=" + propRV);
        }
        else
          remoteFullScreenMode = fullScreenChange.booleanValue();
        fullScreenChange = null;
      }
      catch (java.io.IOException e)
      {
        if (Sage.DBG) System.out.println("Error w/ MiniClient UI:" + e);
        connectionError();
        return false;
      }
    }
    return true;
  }

  private boolean checkForResolutionChangeUpdates()
  {
    if (remoteResolutionChange != null)
    {
      String thisResSwitch = null;
      synchronized (remoteResolutionChangeLock)
      {
        thisResSwitch = remoteResolutionChange;
        remoteResolutionChange = null;
      }
      try
      {
        String value = resolutionNameMap.containsKey(thisResSwitch) ?
            (String)resolutionNameMap.get(thisResSwitch) : thisResSwitch;
        if (Sage.DBG) System.out.println("Setting new GFX_RESOLUTION due to a pushed output change: " + value);
        int propRV = sendSetProperty("GFX_RESOLUTION", value);
        if (propRV != 0)
        {
          if (Sage.DBG) System.out.println("MiniClient did not succeed with resolution change to:" + thisResSwitch + ", errcode=" + propRV);
        }
        else
        {
          maxClientResolution = parseResolution(thisResSwitch);
        }
        // Re-send the DAR; that's needed after a resolution change
        lastSetDAR = 0;
      }
      catch (java.io.IOException e)
      {
        if (Sage.DBG) System.out.println("Error w/ MiniClient UI:" + e);
        connectionError();
        return false;
      }
    }
    return true;
  }

  private boolean checkForARChangeUpdates()
  {
    if (advancedARChange != null)
    {
      try
      {
        int propRV = sendSetProperty("VIDEO_ADVANCED_ASPECT", advancedARChange);
        if (propRV != 0)
        {
          if (Sage.DBG) System.out.println("MiniClient did not succeed with advanced AR change to:" + advancedARChange + ", errcode=" + propRV);
        }
        advancedARChange = null;
      }
      catch (java.io.IOException e)
      {
        if (Sage.DBG) System.out.println("Error w/ MiniClient UI:" + e);
        connectionError();
        return false;
      }
    }
    return true;
  }

  private boolean checkForAppModeUpdates()
  {
    if (appModeChange != null)
    {
      try
      {
        int propRV = sendSetProperty("SWITCH_TO_MODE", appModeChange);
        if (propRV != 0)
        {
          if (Sage.DBG) System.out.println("MiniClient did not succeed with app mode change to:" + appModeChange + ", errcode=" + propRV);
        }
        appModeChange = null;
      }
      catch (java.io.IOException e)
      {
        if (Sage.DBG) System.out.println("Error w/ MiniClient UI:" + e);
        connectionError();
        return false;
      }
    }
    return true;
  }

  private boolean checkForAudioOutputChangeUpdates()
  {
    if (audioOutputChange != null)
    {
      try
      {
        int propRV = sendSetProperty("AUDIO_OUTPUT", audioOutputChange);
        if (propRV != 0)
        {
          if (Sage.DBG) System.out.println("MiniClient did not succeed with audio output change to:" + audioOutputChange + ", errcode=" + propRV);
        }
        audioOutputChange = null;
      }
      catch (java.io.IOException e)
      {
        if (Sage.DBG) System.out.println("Error w/ MiniClient UI:" + e);
        connectionError();
        return false;
      }
    }
    return true;
  }

  private boolean checkForMenuHintUpdates()
  {
    String hintText;
    synchronized (menuHint) {
      // menuHint.format() will return null if no hint is available
      // otherwise get a formatted hint text to send to the client
      hintText=menuHint.format();
      menuHint.clear();
    }

    // if we have a hint, then push it to the client
    if (hintText!=null)
    {
      try {
        int propRV = sendSetProperty("MENU_HINT", hintText);
        if (propRV != 0)
        {
          if (Sage.DBG)
            System.out.println("MiniClient did not succeed with menu hint change to:" + hintText + ", errcode=" + propRV);
        }

      } catch (java.io.IOException e)
      {
        if (Sage.DBG) System.out.println("Error w/ MiniClient UI:" + e);
        connectionError();
        return false;
      }
    }
    return true;
  }

  private boolean checkForDARUpdates()
  {
    if (lastSetDAR != vf.getDisplayAspectRatio())
    {
      if (Sage.DBG) System.out.println("MiniClient sending GFX_ASPECT=" + vf.getDisplayAspectRatio());
      try
      {
        sendSetProperty("GFX_ASPECT", Float.toString(lastSetDAR = vf.getDisplayAspectRatio()));
      }
      catch (java.io.IOException e)
      {
        if (Sage.DBG) System.out.println("Error w/ MiniClient UI:" + e);
        connectionError();
        return false;
      }
    }
    return true;
  }

  private boolean checkForFlipUpdates()
  {
    if (uiMgr.areEffectsEnabled() != bufferFlipping)
    {
      String newFlipValue = uiMgr.areEffectsEnabled() ? "TRUE" : "FALSE";
      if (Sage.DBG) System.out.println("MiniClient sending GFX_FLIP=" + newFlipValue);
      try
      {
        int propRV = sendSetProperty("GFX_FLIP", newFlipValue);
        if (propRV != 0)
        {
          System.out.println("MiniClient did not accept the GFX_FLIP set property, errcode=" + propRV);
        }
      }
      catch (java.io.IOException e)
      {
        if (Sage.DBG) System.out.println("Error w/ MiniClient UI:" + e);
        connectionError();
        return false;
      }
      bufferFlipping = uiMgr.areEffectsEnabled();
      uiMgr.trueValidate();
    }
    return true;
  }

  public boolean executeDisplayList(java.awt.Rectangle clipRect)
  {
    if (startedReconnectDaemon && !reconnectCompleted)
    {
      return false;
    }

    if (!checkForAuthUpdates())
      return false;
    if (!checkForCryptoUpdates())
      return false;
    if (!checkForOutputModeUpdates())
      return false;
    if (!checkForFullScreenChangeUpdates())
      return false;
    if (!checkForResolutionChangeUpdates())
      return false;
    if (!checkForARChangeUpdates())
      return false;
    if (!checkForAudioOutputChangeUpdates())
      return false;
    if (!checkForMenuHintUpdates())
      return false;
    if (!checkForDARUpdates())
      return false;
    if (!checkForFlipUpdates())
      return false;
    if (!checkForAppModeUpdates())
      return false;
    //        if (Sage.DBG) System.out.println("Java2DSageRenderer is executing clipRect="
    //            + clipRect + " displayList=" + currDisplayList);
    if (ANIM_DEBUG) System.out.println("Executing display list now");
    if (currDisplayList == null)
      return false;
    // NOTE: Update on 12/8/04
    // I changed the clipping to be done in the destination space.
    // This was due to rounding errors when filling shapes. This
    // caused the edge artifact in OSD rendering where there'd be
    // horizontal lines left sometimes in the UI.
    // So now the clipping happens before we set the new transformation
    // matrix. We also must intersect the destRect with the clipRect to
    // fix the problem...not exactly sure why we have to do that, but
    // without it, it's not fixed.
    java.awt.geom.Rectangle2D.Float tempClipRect =
        new java.awt.geom.Rectangle2D.Float();

    establishRenderThread();
    precacheImages();
    lastPixelRenderCount = 0;
    lastPixelInputCount = 0;
    aliveAnims = false;
    currSurface = 0;
    textureBatchCount = textureBatchSize = 0;
    surfStack.clear();
    animsThatGoAfterSurfPop.clear();
    startFrameMini();
    boolean extraOps = false;
    java.util.ArrayList currWaitIndicatorRops = waitIndicatorRops;
    boolean currWaitIndicatorState = waitIndicatorState;
    refreshVideo = false;
    pendingHiResSurfHandleToSet = 0;
    pendingVidSurfCoords = null;
    renderStartTime = Sage.eventTime();
    effectAnimationsActive = false;
    effectAnimationsLocked = false;
    effectStack.clear();
    currEffectAlpha = 1.0f;
    currProjXform = null;
    if (xformRenderSupport)
    {
      currEffectClip = null;
      currProjXform = generateProjectionMatrix(0, 0);
      pushTransform(currProjXform);
      currXform = null;
    }
    else
    {
      currEffectClip = new java.awt.Rectangle(0, 0, master.getWidth(), master.getHeight());
      currXform = null;
    }
    baseClip = currEffectClip;
    virginsToFix.clear();
    if (dlThatWasComposited != currDisplayList || !uiMgr.areLayersEnabled())
    {
      boolean repeatedDL = dlThatWasComposited == currDisplayList;
      dlThatWasComposited = null;
      java.util.Set clearedSurfs = null;
      usedHiResSurf = false;
      currSurfHiResImageOp = null;
      lastHiResImageLayer = null;
      if (uiMgr.areLayersEnabled())
      {
        // This is all of the surface names that have been used in Out animation operations. Therefore
        // they should NOT also be used for any In operation. If they are we should duplicate
        // the surface and use that for the Out operation.
        fixDuplicateAnimSurfs(currDisplayList, clipRect);

        compositeOps.clear();
        clearedSurfs = new java.util.HashSet();
      }
      // Set these here so we ensure they're constant across the whole display list execution
      int targetWidth, targetHeight;
      if (remoteWindowedSystem)
      {
        targetWidth = maxClientResolution.width;
        targetHeight = maxClientResolution.height;
      }
      else
      {
        targetWidth = Math.max(uiMgr.getInt("osd_rendering_width", 720), clipRect.x + clipRect.width);
        targetHeight = Math.max(uiMgr.getInt(MMC.getInstance().isNTSCVideoFormat() ?
            "osd_rendering_height_ntsc" : "osd_rendering_height_pal", 480), clipRect.y + clipRect.height);
      }

      // See if we want to render some of this to a layer first so we can re-use during the animation cycle
      // NOTE: We should optimize this to NOT be done if there are not animated effects in the entire display list (we can determine
      // that in the preprocessing stage); and we should also abort it if another display list is being prepped which would cause us
      // to not actually execute any of the animations in this DL. This optimization is ONLY a benefit if we end up re-using the
      // layer that we're caching for more than one render cycle.
      // So we can't properly determine if there are any active effects because they wouldn't be activated until we process
      // them here; the preprocessing stage hasn't called the method to trigger them to become active yet. But considering nearly
      // everything the user does in the UI causes some kind of rendering effect to occur; that optimization wouldn't buy us much anyways.
      effectSurfCacheActive = false;
      effectSurfPixelCacheRenderCount = 0;
      boolean renderingFromEffectSurfCache = false;
      if (uiMgr.areEffectsEnabled() && surfaceSupport)
      {
        if (effectCacheSurf == 0 || (effectCacheDims != null && (effectCacheDims.width != targetWidth || effectCacheDims.height != targetHeight)))
        {
          if (effectCacheSurf != 0)
          {
            unloadImageMini(effectCacheSurf);
            effectCacheSurf = 0;
          }
          if (Sage.DBG) System.out.println("Allocating the cache surface to acclerate effects-based animations of size " + targetWidth + "x" + targetHeight);
          if (effectCacheDims == null)
            effectCacheDims = new java.awt.Dimension();
          effectCacheDims.width = targetWidth;
          effectCacheDims.height = targetHeight;
          synchronized (MetaImage.getNiaCacheLock(this))
          {
            while((effectCacheSurf = createSurfaceMini(targetWidth, targetHeight)) == 0)
            {
              Object[] oldestImage =
                  MetaImage.getLeastRecentlyUsedImage(this, null, 0);
              if (oldestImage == null)
              {
                if (Sage.DBG) System.out.println("ERROR Failed allocating the backing surface for the effects animation cache!");
                break;
              }
              ((MetaImage) oldestImage[0]).clearNativePointer(this,
                  ((Integer) oldestImage[1]).intValue());
            }
          }
        }
        else if (repeatedDL)
        {
          //System.out.println("Using the cached effect surface for rendering");
          renderingFromEffectSurfCache = true;
          dlThatWasComposited = currDisplayList;
          // Now we render the effectSurfaceCache to the main render target and then continue rendering in the main target from here
          setTargetSurfaceMini(0);
          // Draw it in SRC mode
          drawTexturedRectMini(0, 0, effectCacheDims.width, -effectCacheDims.height, effectCacheSurf, 0, 0, effectCacheDims.width, effectCacheDims.height,
              0xFFFFFFFF, false);
          pendingHiResSurfHandleToSet = effectSurfCacheHighResSurf;
        }
        if (!renderingFromEffectSurfCache && effectCacheSurf != 0 && nextDisplayList == null && !master.isARBuildingDL() && !master.isActiveRenderGoingToRun(false) &&
            currDisplayList.size() > 4 && Sage.getBoolean("ui/enable_effect_layer_caching", true)) // ignore video only lists or other simple renderings as well
        {
          //System.out.println("Building the cached effect surface for rendering");
          setTargetSurfaceMini(effectCacheSurf);
          effectSurfCacheActive = true;
          effectCacheOpCount = 0;
          dlThatWasComposited = currDisplayList;
          effectSurfCacheHighResSurf = 0;
        }
        else if (!renderingFromEffectSurfCache)
        {
          //System.out.println("Aborting building the effect cache since we're going to change the DL next cycle");
        }
      }
      for (int i = 0; ; i++)
      {
        if (sentUIMgrKill)
          break;
        RenderingOp op;
        if (i >= currDisplayList.size())
        {
          if (((currWaitIndicatorState  && i == currDisplayList.size()) || extraOps) && currWaitIndicatorRops != null)
          {
            if (currWaitIndicatorState)
            {
              waitIndicatorRopsIndex = (++waitIndicatorRopsIndex) % currWaitIndicatorRops.size();
              op = (RenderingOp) currWaitIndicatorRops.get(waitIndicatorRopsIndex);
            }
            else
            {
              if (i - currDisplayList.size() >= currWaitIndicatorRops.size())
                break;
              op = (RenderingOp) currWaitIndicatorRops.get(i - currDisplayList.size());
            }
          }
          else
            break;
        }
        else
          op = (RenderingOp) currDisplayList.get(i);
        //System.out.println("Executing Op:" + op);
        if (renderingFromEffectSurfCache && i < effectCacheOpCount)
        {
          // We still need to process any effect operations in order to maintain the proper effectStack or any effects who have an affect beyond the cached regions
          if (op.isEffectOp())
          {
            processEffectOp(op, repeatedDL);
          }
          continue;
        }
        if (effectSurfCacheActive)
          effectCacheOpCount++;
        // Skip transparent rendering ops
        if (!op.isEffectOp() && currEffectAlpha == 0)
          continue;
        if (op.isEffectOp())
        {
          processEffectOp(op, repeatedDL);
        }
        else if (op.isImageOp())
        {
          processImageOp(op);
        }
        else if (op.isPrimitiveOp())
        {
          processPrimitiveOp(op);
        }
        else if (op.isTextOp())
        {
          processTextOp(op);
        }
        else if (op.isVideoOp()) // if its first then don't clear anything
        {
          processVideoOp(op, clipRect);
        }
        else if (uiMgr.areLayersEnabled() && op.isSurfaceOp())
        {
          if (ANIM_DEBUG) System.out.println("Surface Op surf=" + op.surface + " on=" + op.isSurfaceOpOn() + " " + op);
          if (op.isSurfaceOpOn())
          {
            lastHiResImageLayer = op.surface;
            currSurfHiResImageOp = null;
            if (currSurface != 0)
              surfStack.push(new Long(currSurface));
            Long newSurfObj = (Long) surfaceCache.get(op.surface);
            if (newSurfObj != null)
            {
              // Make sure it's big enough
              java.awt.Dimension surfSize = (java.awt.Dimension) surfaceDimensions.get(newSurfObj);
              if (surfSize.width != targetWidth || surfSize.height != targetHeight)
              {
                surfaceDimensions.remove(newSurfObj);
                if (currSurface == newSurfObj.longValue())
                  setTargetSurfaceMini(0);
                unloadImageMini(newSurfObj.longValue());
                surfaceCache.remove(op.surface);
                newSurfObj = null;
              }
            }
            if (newSurfObj == null)
            {
              synchronized (MetaImage.getNiaCacheLock(this))
              {
                while((currSurface = createSurfaceMini(targetWidth, targetHeight)) == 0)
                {
                  Object[] oldestImage =
                      MetaImage.getLeastRecentlyUsedImage(this, null, 0);
                  if (oldestImage == null)
                  {
                    if (Sage.DBG) System.out.println("WARNING Failed to allocate a requested surface!!");
                    return false;
                  }
                  ((MetaImage) oldestImage[0]).clearNativePointer(this,
                      ((Integer) oldestImage[1]).intValue());
                }
              }
              surfaceCache.put(op.surface, new Long(currSurface));
              surfaceDimensions.put(new Long(currSurface), new java.awt.Dimension(targetWidth, targetHeight));
              numSurfsCreated++;
            }
            else
              currSurface = newSurfObj.longValue();
            if (ANIM_DEBUG) System.out.println("Switched rendering surface to " + op.surface + " " + currSurface);
            setTargetSurfaceMini(currSurface);
            if (clearedSurfs.add(new Long(currSurface)))
            {
              clearRectMini(clipRect.x, clipRect.y, clipRect.width, clipRect.height, 0, 0, 0, 0);
            }
          }
          else
          {
            // Avoid double compositing operations from nested surface usage
            if (!surfStack.contains(new Long(currSurface)))
            {
              compositeOps.add(op);
              java.util.ArrayList remnantAnims = (java.util.ArrayList) animsThatGoAfterSurfPop.remove(new Long(currSurface));
              if (remnantAnims != null)
              {
                if (ANIM_DEBUG) System.out.println("Adding animation ops into composite list now from prior nested surfs:" + remnantAnims);
                compositeOps.addAll(remnantAnims);
              }
            }
            if (surfStack.isEmpty())
              currSurface = 0;
            else
              currSurface = ((Long)surfStack.pop()).longValue();
            setTargetSurfaceMini(currSurface);
          }
        }
        else if (uiMgr.areLayersEnabled() && op.isAnimationOp())
        {
          processAnimOp(op, i, clipRect);
          if (new Long(currSurface).equals(surfaceCache.get(op.surface)) || surfStack.contains(surfaceCache.get(op.surface)))
          {
            if (ANIM_DEBUG) System.out.println("Putting animation op in surf pop map because we're nested in the current surface");
            java.util.ArrayList vecy = (java.util.ArrayList) animsThatGoAfterSurfPop.get(new Long(currSurface));
            if (vecy == null)
              animsThatGoAfterSurfPop.put(new Long(currSurface), vecy = new java.util.ArrayList());
            vecy.add(compositeOps.remove(compositeOps.size() - 1));
          }
          if (op.surface.equals(lastHiResImageLayer) && currSurfHiResImageOp != null)
          {
            if (ANIM_DEBUG) System.out.println("Adding HiRes image op for surface \"" + lastHiResImageLayer + "\" to animation list: " + currSurfHiResImageOp);
            currSurfHiResImageOp.cloneAnimation(op.anime);
            long theTime = Sage.eventTime();
            currSurfHiResImageOp.anime.startNow(theTime - uiMgr.getInt("ui/frame_duration", 14));
            currSurfHiResImageOp.anime.calculateAnimation(theTime, master.getWidth(), master.getHeight(), false);
            currSurfHiResImageOp = null;
          }
        }
      }
      if (uiMgr.areLayersEnabled())
      {
        java.util.Collections.sort(compositeOps, COMPOSITE_LIST_SORTER);

        fixSurfacePostCompositeRegions();
      }
      if (pendingHiResSurfHandleToSet == 0 && lastUsedHighResSurf != 0 && !clipRect.intersects(lastHiResSurfDest))
      {
        pendingHiResSurfHandleToSet = lastUsedHighResSurf;
      }
    }
    else
    {
      if (ANIM_DEBUG) System.out.println("OPTIMIZATION Skip DL render & composite only! dlSize=" + currDisplayList.size() +
          " optSize=" + compositeOps.size());
      pendingHiResSurfHandleToSet = lastUsedHighResSurf;
    }
    if (xformRenderSupport)
      popTransform();
    if (uiMgr.areLayersEnabled())
    {
      performSurfaceCompositing(currWaitIndicatorState, extraOps, currWaitIndicatorRops, clipRect);
    }
    if (effectAnimationsActive)
    {
      master.effectsNeedProcessing(effectAnimationsLocked);
    }
    if (effectSurfCacheActive)
    {
      effectSurfCacheActive = false;
      // Now we render the effectSurfaceCache to the main render target and then continue rendering in the main target from here
      setTargetSurfaceMini(0);
      // Draw it in SRC mode
      drawTexturedRectMini(0, 0, effectCacheDims.width, -effectCacheDims.height, effectCacheSurf, 0, 0, effectCacheDims.width, effectCacheDims.height,
          0xFFFFFFFF, false);
      effectSurfPixelCacheRenderCount = lastPixelRenderCount;
      effectSurfCacheHighResSurf = pendingHiResSurfHandleToSet;
    }
    if (effectSurfPixelCacheRenderCount != 0)
    {
      //System.out.println("Effect surface cache pixel count=" + effectSurfPixelCacheRenderCount/1024 + "K " + ((100.0f * effectSurfPixelCacheRenderCount) / lastPixelRenderCount) + "%");
    }
    //        try{clientOutStream.flush();}catch(Exception e){}
    return true;
  }

  private void performSurfaceCompositing(boolean currWaitIndicatorState, boolean extraOps, java.util.ArrayList currWaitIndicatorRops,
      java.awt.Rectangle clipRect)
  {
    // Go back through and composite all of the surfaces that were there (if any)
    // Find all the names of the surface and sort those and then render the surfaces in order (which may not
    // match the order they appeared in the display list)
    dlThatWasComposited = currDisplayList;
    if (ANIM_DEBUG) System.out.println("Performing the surface compositing operations now");
    boolean drawnBGYet = false;
    for (int i = 0; ; i++)
    {
      RenderingOp op;
      if (i >= compositeOps.size())
      {
        if (((currWaitIndicatorState  && i == compositeOps.size()) || extraOps) && currWaitIndicatorRops != null)
        {
          if (currWaitIndicatorState)
          {
            waitIndicatorRopsIndex = (++waitIndicatorRopsIndex) % currWaitIndicatorRops.size();
            op = (RenderingOp) currWaitIndicatorRops.get(waitIndicatorRopsIndex);
          }
          else
          {
            if (i - compositeOps.size() >= currWaitIndicatorRops.size())
              break;
            op = (RenderingOp) currWaitIndicatorRops.get(i - compositeOps.size());
          }
        }
        else
          break;
      }
      else
        op = (RenderingOp) compositeOps.get(i);
      int compositeMode;
      if (op.isSurfaceOp())
      {
        if (ANIM_DEBUG) System.out.println("Surface Op surf=" + op.surface + " on=" + op.isSurfaceOpOn() + " " + op);
        if (op.isSurfaceOpOff())
        {
          currSurface = ((Long) surfaceCache.get(op.surface)).longValue();
          if (isBackgroundSurface(op.surface))
          {
            compositeMode = java.awt.AlphaComposite.SRC;
            if ((op.opFlags & RenderingOp.RENDER_FLAG_NONOVERLAPPED_COPY) == 0)
              drawnBGYet = true;
          }
          else
            compositeMode = java.awt.AlphaComposite.SRC_OVER;
          java.awt.Rectangle recty = new java.awt.Rectangle();
          java.awt.geom.Rectangle2D.intersect(clipRect, op.destRect, recty);
          drawTexturedRectMini(recty.x, recty.y, recty.width, (compositeMode == java.awt.AlphaComposite.SRC) ? -recty.height : recty.height,
              currSurface, recty.x, recty.y, recty.width, recty.height, getCompositedColor(0xFFFFFFFF, op.alphaFactor));
          if (ANIM_DEBUG) System.out.println("Finished cached surface rendering and re-composited it with the main surface");
        }
      }
      else if (op.isImageOp())
      {
        if (op.isAnimationOp())
        {
          int clearColor = (((int)(1.0f - op.alphaFactor*255) & 0xFF) << 24) | 0x000000;
          clearRectMini((int)(op.destRect.x + 0.5f), (int)(op.destRect.y + 0.5f),
              (int)(op.destRect.width + 0.5f), (int)(op.destRect.height + 0.5f),
              clearColor, clearColor, clearColor, clearColor, clipRect);
          if (ANIM_DEBUG) System.out.println("HiRes animation operation being performed! srcRect=" + op.srcRect + " destRect=" + op.destRect + " orgSrcRect=" + op.anime.orgSrcRect);
          /*						java.awt.geom.Rectangle2D.Float clippedSrcRect = new java.awt.geom.Rectangle2D.Float();
					clippedSrcRect.setRect((java.awt.geom.Rectangle2D.Float)op.privateData);
					java.awt.geom.Rectangle2D.Float clippedDstRect = new java.awt.geom.Rectangle2D.Float();
					clippedDstRect.setRect(op.destRect);
					java.awt.geom.Rectangle2D.Float clonedClipRect = new java.awt.geom.Rectangle2D.Float();
					clonedClipRect.setRect(clipRect);
//						Sage.clipSrcDestRects(clonedClipRect, clippedSrcRect, clippedDstRect);
					setVideoPropertiesMini(VIDEO_MODE_MASK_SOURCE | VIDEO_MODE_MASK_OUTPUT | VIDEO_MODE_MASK_ALPHA,
						Math.round((clippedSrcRect.x + clippedSrcRect.width/2)*4096/op.texture.getWidth(op.textureIndex)),
						Math.round((clippedSrcRect.y + clippedSrcRect.height/2)*4096/op.texture.getHeight(op.textureIndex)),
						Math.round(clippedSrcRect.width*4096/op.texture.getWidth(op.textureIndex)),
						Math.round(clippedSrcRect.height*4096/op.texture.getHeight(op.textureIndex)),
						Math.round((clippedDstRect.x + clippedDstRect.width/2)*4096/master.getWidth()),
						Math.round((clippedDstRect.y + clippedDstRect.height/2)*4096/master.getHeight()),
						Math.round(clippedDstRect.width*4096/master.getWidth()),
						Math.round(clippedDstRect.height*4096/master.getHeight()),
						Math.round(op.alphaFactor * 255), 0);*/
          java.awt.geom.Rectangle2D.Float realSrc = (java.awt.geom.Rectangle2D.Float)op.privateData;
          if ((gfxVideoPropsSupportMask & VIDEO_MODE_MASK_TL_ORIGIN) != 0)
          {
            pendingVidSurfCoords = new int[] {
                //setVideoPropertiesMini(VIDEO_MODE_MASK_SOURCE | VIDEO_MODE_MASK_OUTPUT | VIDEO_MODE_MASK_ALPHA,
                (int)(((op.srcRect.x - op.anime.orgSrcRect.x)*4096/op.anime.orgSrcRect.width) + 0.5f),
                (int)(((op.srcRect.y - op.anime.orgSrcRect.y)*4096/op.anime.orgSrcRect.height) + 0.5f),
                (int)((op.srcRect.width*4096/op.anime.orgSrcRect.width) + 0.5f),
                (int)((op.srcRect.height*4096/op.anime.orgSrcRect.height) + 0.5f),
                (int)(((op.destRect.x)*4096/master.getWidth()) + 0.5f),
                (int)(((op.destRect.y)*4096/master.getHeight()) + 0.5f),
                (int)((op.destRect.width*4096/master.getWidth()) + 0.5f),
                (int)((op.destRect.height*4096/master.getHeight()) + 0.5f),
                (int)((op.alphaFactor * 255) + 0.5f)};//, 0);
          }
          else
          {
            pendingVidSurfCoords = new int[] {
                //setVideoPropertiesMini(VIDEO_MODE_MASK_SOURCE | VIDEO_MODE_MASK_OUTPUT | VIDEO_MODE_MASK_ALPHA,
                (int)(((op.srcRect.x + op.srcRect.width/2 - op.anime.orgSrcRect.x)*4096/op.anime.orgSrcRect.width) + 0.5f),
                (int)(((op.srcRect.y + op.srcRect.height/2 - op.anime.orgSrcRect.y)*4096/op.anime.orgSrcRect.height) + 0.5f),
                (int)((op.srcRect.width*4096/op.anime.orgSrcRect.width) + 0.5f),
                (int)((op.srcRect.height*4096/op.anime.orgSrcRect.height) + 0.5f),
                (int)(((op.destRect.x + op.destRect.width/2)*4096/master.getWidth()) + 0.5f),
                (int)(((op.destRect.y + op.destRect.height/2)*4096/master.getHeight()) + 0.5f),
                (int)((op.destRect.width*4096/master.getWidth()) + 0.5f),
                (int)((op.destRect.height*4096/master.getHeight()) + 0.5f),
                (int)((op.alphaFactor * 255) + 0.5f)};//, 0);
          }
          pendingHiResSurfHandleToSet = op.texture.getNativeImage(this, op.textureIndex);
          op.texture.removeNativeRef(this, op.textureIndex);
          if (!op.anime.expired)
          {
            master.setActiveAnimation(op);
            aliveAnims = true;
          }
        }
        else
        {
          // So that image will be loaded for now...
          java.awt.Rectangle intRectd = getIntRect(op.destRect, cacheRect1);
          if ((gfxScalingCaps & GFX_SCALING_HW) != 0)
          {
            drawTexturedRectMini(intRectd.x,
                intRectd.y,
                (int)(op.destRect.width + 0.5f),
                (int)(op.destRect.height + 0.5f),
                op.texture.getNativeImage(this, op.textureIndex),
                (int)(op.srcRect.x + 0.5f),
                (int)(op.srcRect.y + 0.5f),
                (int)(op.srcRect.width + 0.5f),
                (int)(op.srcRect.height + 0.5f),
                getCompositedColor(op.renderColor, op.alphaFactor));
          }
          else
          {
            drawTexturedRectMini(intRectd.x,
                intRectd.y,
                Math.min(intRectd.width, op.texture.getWidth(op.textureIndex)),
                Math.min(intRectd.height, op.texture.getHeight(op.textureIndex)),
                op.texture.getNativeImage(this, op.textureIndex),
                (int)(op.srcRect.x + 0.5f),
                (int)(op.srcRect.y + 0.5f),
                Math.min(intRectd.width, op.texture.getWidth(op.textureIndex)),
                Math.min(intRectd.height, op.texture.getHeight(op.textureIndex)),
                getCompositedColor(op.renderColor, op.alphaFactor));
          }

          op.texture.removeNativeRef(this, op.textureIndex);
        }
      }
      else if (op.isAnimationOp())
      {
        RenderingOp.Animation anime = op.anime;
        if (ANIM_DEBUG) System.out.println("Animation operation found! ANIMAIL ANIMAIL!!! " + op + " scrollSrcRect=" + anime.altSrcRect +
            " scrollDstRect=" + anime.altDestRect);
        // Find the cached surface first
        Long cachedSurface = (Long) surfaceCache.get(op.surface);
        if (cachedSurface != null)
        {
          if (ANIM_DEBUG) System.out.println("Cached animation surface found: " + op.surface);
          if (ANIM_DEBUG) System.out.println("Rendering Animation " + anime.animation);
          java.awt.Rectangle clippedSrcRect = new java.awt.Rectangle();
          clippedSrcRect.setRect(op.srcRect);
          java.awt.Rectangle clippedDstRect = new java.awt.Rectangle();
          clippedDstRect.setRect(op.destRect);
          java.awt.Rectangle clonedClipRect = new java.awt.Rectangle();
          clonedClipRect.setRect(clipRect);
          Sage.clipSrcDestRects(clonedClipRect, clippedSrcRect, clippedDstRect);
          if (isBackgroundSurface(op.surface) && !drawnBGYet)
          {
            compositeMode = java.awt.AlphaComposite.SRC;
            drawnBGYet = true;
          }
          else
            compositeMode = java.awt.AlphaComposite.SRC_OVER;
          drawTexturedRectMini(clippedDstRect.x, clippedDstRect.y, clippedDstRect.width, (compositeMode == java.awt.AlphaComposite.SRC) ? -clippedDstRect.height : clippedDstRect.height,
              cachedSurface.longValue(), clippedSrcRect.x, clippedSrcRect.y, clippedSrcRect.width, clippedSrcRect.height,
              getCompositedColor(0xFFFFFFFF, op.alphaFactor));
          if (anime.isDualSurfaceOp())
          {
            // We need to render the other scrolling position
            cachedSurface = (Long) surfaceCache.get(op.anime.altSurfName);
            if (cachedSurface != null)
            {
              if (ANIM_DEBUG) System.out.println("Rendering second scroll surface scrollSrcRect=" + anime.altSrcRect +
                  " scrollDstRect=" + anime.altDestRect);
              clippedSrcRect.setRect(op.anime.altSrcRect);
              clippedDstRect.setRect(op.anime.altDestRect);
              Sage.clipSrcDestRects(clonedClipRect, clippedSrcRect, clippedDstRect);
              drawTexturedRectMini(clippedDstRect.x, clippedDstRect.y, clippedDstRect.width, clippedDstRect.height,
                  cachedSurface.longValue(), clippedSrcRect.x, clippedSrcRect.y, clippedSrcRect.width, clippedSrcRect.height,
                  getCompositedColor(0xFFFFFFFF, op.anime.altAlphaFactor));
            }
          }
        }
        else
        {
          if (ANIM_DEBUG) System.out.println("ERROR: Could not find cached animation surface:" + op.surface);
        }
        if (!anime.expired)
        {
          master.setActiveAnimation(op);
          aliveAnims = true;
        }
      }
    }

  }

  private void processEffectOp(RenderingOp op, boolean repeatedDL)
  {
    if (op.effectTracker != null)
    {
      //System.out.println("Current Effect=" + op.effectTracker.getWidget() + " alpha=" + currEffectAlpha + " active=" + op.effectTracker.isActive() +
      //	" pos=" + op.effectTracker.isPositive() + " comp=" + System.identityHashCode(op.effectTracker.getSrcComp()) + " prog=" + op.effectTracker.getCurrProgress());
      if (op.effectTracker.processEffectState(renderStartTime, !repeatedDL))
        virginsToFix.add(op.effectTracker);
      EffectStackItem stackItem = new EffectStackItem(currXform, currProjXform, currEffectAlpha, currEffectClip);
      effectStack.push(stackItem);
      // We can't just check to see if they have a 1.0 alpha, or no transform...because they could temporarily have that state which could cause an invalid cache to be built
      if (effectSurfCacheActive && op.effectTracker.isActive())
      {
        effectSurfCacheActive = false;
        // Now we render the effectSurfaceCache to the main render target and then continue rendering in the main target from here
        setTargetSurfaceMini(0);
        // Draw it in SRC mode
        drawTexturedRectMini(0, 0, effectCacheDims.width, -effectCacheDims.height, effectCacheSurf, 0, 0, effectCacheDims.width, effectCacheDims.height,
            0xFFFFFFFF, false);
        effectSurfPixelCacheRenderCount = lastPixelRenderCount;
        // Remove this effect op from the op count
        effectCacheOpCount--;
        effectSurfCacheHighResSurf = pendingHiResSurfHandleToSet;
      }
      if (op.effectTracker.hasFade())
      {
        float nextFade = op.effectTracker.getCurrentFade();
        currEffectAlpha *= nextFade;
      }
      if (op.effectTracker.isKiller())
        currEffectAlpha = 0;
      boolean invertX = false;
      boolean invertY = false;
      if (op.effectTracker.hasZoom())
      {
        if ((gfxScalingCaps & GFX_SCALING_FLIPH) == 0 && op.effectTracker.getTargetScaleX() < 0)
        {
          // The cache has flipped this image already so undo it in the transform here
          invertX = true;
        }
        if ((gfxScalingCaps & GFX_SCALING_FLIPV) == 0 && op.effectTracker.getTargetScaleY() < 0)
        {
          // The cache has flipped this image already so undo it in the transform here
          invertY = true;
        }
      }
      // This is a MediaMVP and they can't do realtime scaling; so we just have to disable the scaling effects...not another option really
      if (op.effectTracker.getCurrentTransform(tempMat, op.srcRect, invertX, invertY, isMediaExtender() && ((gfxScalingCaps & GFX_SCALING_HW) == 0)))
      {
        if (op.destRect != null)
        {
          // Now apply the effect to the clipping rectangle to account for the actual effect; and then
          // reclip against what the rect actually is
          currEffectClip = new java.awt.Rectangle();
          if (xformRenderSupport)
          {
            // Now apply the effect to the clipping rectangle to account for the actual effect; and then
            // reclip against what the rect actually is
            MathUtils.transformRectCoords(op.destRect, MathUtils.createInverse(tempMat), currEffectClip);
            currEffectClip.intersect(currEffectClip, op.destRect, currEffectClip);
          }
          else
          {
            MathUtils.transformRectCoords(op.destRect, tempMat, currEffectClip);
            currEffectClip.intersect(currEffectClip, op.destRect, currEffectClip);
            if (baseClip != null)
              currEffectClip.intersect(currEffectClip, baseClip, currEffectClip);
          }
        }
        if (currXform != null)
          tempMat.mul(currXform, tempMat);
        currXform = new javax.vecmath.Matrix4f(tempMat);
        if (xformRenderSupport)
        {
          if (op.effectTracker.hasCameraOffset())
          {
            currProjXform = generateProjectionMatrix(op.effectTracker.getCameraOffsetX(), op.effectTracker.getCameraOffsetY());
          }
          tempMat.mul(currProjXform, currXform);
          pushTransform(tempMat);
        }
      }
      if (op.effectTracker.isActive())
      {
        effectAnimationsActive = true;
        if (op.effectTracker.requiresCompletion())
          effectAnimationsLocked = true;
      }
    }
    else
    {
      EffectStackItem stackItem = (EffectStackItem) effectStack.pop();
      if (xformRenderSupport && currXform != stackItem.xform)
        popTransform();
      currXform = stackItem.xform;
      currProjXform = stackItem.projxform;
      currEffectAlpha = stackItem.alpha;
      currEffectClip = (java.awt.Rectangle) stackItem.clip;
    }
  }

  private void processTextOp(RenderingOp op)
  {
    java.awt.Rectangle cr = getIntRect(op.destRect, cacheRect1);
    java.awt.Rectangle targetRect = getIntRect(op.copyImageRect, cacheRect2);
    // See if we have this text render cached as a surface
    TextSurfaceCache cachedTextStringSurface = getCachedTextSurfacePtr(op);
    if (cachedTextStringSurface != null)
    {
      java.awt.Rectangle srcRect = (java.awt.Rectangle) cachedTextStringSurface.srcRect.clone();
      Sage.clipSrcDestRects(cr, srcRect, targetRect);
      lastPixelRenderCount += targetRect.width * targetRect.height;
      lastPixelInputCount += srcRect.width * srcRect.height;
      drawTexturedRectMini(targetRect.x, targetRect.y, targetRect.width, targetRect.height, cachedTextStringSurface.handle,
          srcRect.x, srcRect.y, srcRect.width, srcRect.height, getCompositedColor(java.awt.Color.white, op.alphaFactor * currEffectAlpha), true);
    }
    else if (op.text.fontImage != null && op.text.renderRectCache != null)
    {
      int numFontImages = op.text.fontImage.getNumImages();
      int numGlyphs = op.text.renderImageNumCache.length;
      int offX, offY, offW, offH; // adjustments for clipping
      int color = getCompositedColor(op.renderColor, op.alphaFactor * currEffectAlpha);
      boolean obeyClipXform = true;
      // We can tell it to not try to redo the clip for each individual glyph if we know the entire text string fits
      // inside the clipping rectangle and there's no active transform. This will cover a large amount of our cases for text rendering.
      if (currXform == null && currEffectClip != null)
      {
        if (currEffectClip.contains(targetRect))
          obeyClipXform = false;
      }
      for (int j = 0; j < numFontImages; j++)
      {
        long texturePtr = -1;
        int rectCoordIndex = 0;
        for(int k = 0; k < numGlyphs; k++)
        {
          if(op.text.renderImageNumCache[k] == j)
          {
            if(texturePtr == -1)
              texturePtr = op.text.fontImage.getNativeImage(this, j);

            rectCoordIndex++;
            if (obeyClipXform)
            {
              offX = offY = offW = offH = 0;
              // Calculate the adjustments to the rects to account for clipping
              if (op.text.renderRectCache[2*k]+targetRect.x < cr.x)
                offX = cr.x - ((int)op.text.renderRectCache[2*k]+targetRect.x);
              if (op.text.renderRectCache[2*k+1]+targetRect.y < cr.y)
                offY = cr.y - ((int)op.text.renderRectCache[2*k+1]+targetRect.y);
              if (op.text.renderRectCache[2*k]+targetRect.x > cr.x + cr.width)
                offW = ((int)op.text.renderRectCache[2*k]+targetRect.x) - (cr.x + cr.width);
              if (op.text.renderRectCache[2*k+1]+targetRect.y > cr.y + cr.height)
                offH = ((int)op.text.renderRectCache[2*k+1]+targetRect.y) - (cr.y + cr.height);
              if (op.text.renderGlyphRectCache[k].width - offW - offX > 0 &&
                  op.text.renderGlyphRectCache[k].height - offH - offY > 0)
              {
                drawTexturedRectMini(
                    (int)op.text.renderRectCache[2*k]+targetRect.x + offX,
                    (int)op.text.renderRectCache[2*k+1]+targetRect.y + offY,
                    // If this is a negative value, it indicates that there is a blending color
                    // for the text.
                    -((int)(op.text.renderGlyphRectCache[k].width - offW - offX + FLOAT_ERROR)),
                    (int)(op.text.renderGlyphRectCache[k].height - offH - offY + FLOAT_ERROR),
                    texturePtr,
                    (int)op.text.renderGlyphRectCache[k].x + offX,
                    (int)op.text.renderGlyphRectCache[k].y + offY,
                    (int)(op.text.renderGlyphRectCache[k].width - offW - offX + FLOAT_ERROR),
                    (int)(op.text.renderGlyphRectCache[k].height - offH - offY + FLOAT_ERROR),
                    color, true);
              }
            }
            else
            {
              drawTexturedRectMini(
                  (int)op.text.renderRectCache[2*k]+targetRect.x,
                  (int)op.text.renderRectCache[2*k+1]+targetRect.y,
                  // If this is a negative value, it indicates that there is a blending color
                  // for the text.
                  -((int)(op.text.renderGlyphRectCache[k].width + FLOAT_ERROR)),
                  (int)(op.text.renderGlyphRectCache[k].height + FLOAT_ERROR),
                  texturePtr,
                  (int)op.text.renderGlyphRectCache[k].x,
                  (int)op.text.renderGlyphRectCache[k].y,
                  (int)(op.text.renderGlyphRectCache[k].width + FLOAT_ERROR),
                  (int)(op.text.renderGlyphRectCache[k].height + FLOAT_ERROR),
                  color, false);
            }
          }
        }
        if (rectCoordIndex > 0)
        {
          op.text.fontImage.removeNativeRef(this, j);
        }
      }
    }
    else
    {
      if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("Drawing with font:" + op.text.font);
      // NOTE: Sometimes we clip the last pixel of the text in the vertical dimension (and probably
      // horizontal as well). This is due to the rounding effect of adding the Ascent getting in the way.
      // Another reason for the floating point rendering option
      drawTextMini(
          (int)(op.destRect.x - op.srcRect.x + FLOAT_ERROR),
          (int)(op.destRect.y - op.srcRect.y +
              op.text.font.getAscent() + FLOAT_ERROR),
              op.text.string,
              getNativeFontHandle(op.text.font.getName(),
                  op.text.font.getStyle(), op.text.font.getSize()),
                  getCompositedColor(op.renderColor, op.alphaFactor * currEffectAlpha),
                  cr.x, cr.y,
                  cr.width, cr.height); // TODO: that's strange looking :)
    }
  }

  private void processImageOp(RenderingOp op)
  {
    // So that image will be loaded for now...
    java.awt.Rectangle intRectd = getIntRect(op.destRect, cacheRect1);
    if ((gfxScalingCaps & GFX_SCALING_HW) != 0)
    {
      long nativePtr = op.texture.getNativeImage(this, op.textureIndex);
      //System.out.println("destRect=" + op.destRect);
      if (!usedHiResSurf && highResSurfaceHandles.contains(new Long(nativePtr)) &&
          op.texture.getSource() instanceof MediaFile &&
          !uiMgr.getVideoFrame().isLoadingOrLoadedFile() &&
          op.diffuseTexture == null &&
          // If they're not being displayed bigger than 1/2 the screen don't use the hi-res surface
          (!uiMgr.isXBMCCompatible() || (intRectd.width * intRectd.height > master.getWidth() * master.getHeight()/2)) &&
          op.renderColor == null)
      {
        if (currXform != null)
          MathUtils.transformRectCoords(intRectd, currXform, intRectd);
        if (currEffectClip != null)
          intRectd.intersect(intRectd, currEffectClip, intRectd);
        if (ANIM_DEBUG) System.out.println("Clearing the graphics area for the YUV video image surface");
        usedHiResSurf = true;
        int clearColor = ((((int)((1.0f - Math.min(1.0f, op.alphaFactor*currEffectAlpha))*255)) & 0xFF) << 24) | 0x000000;
        clearRectMini(intRectd.x, intRectd.y,
            intRectd.width, intRectd.height,
            clearColor, clearColor, clearColor, clearColor, intRectd);
        synchronized (this)
        {
          pendingHiResSurfHandleToSet =  nativePtr;
          // x, y are offset relative to 4096
          // w, h are also relative to 4096
          java.awt.geom.Rectangle2D.Float dr = op.copyImageRect;
          if (currEffectClip != null || currXform != null)
          {
            dr = new java.awt.geom.Rectangle2D.Float(op.copyImageRect.x, op.copyImageRect.y, op.copyImageRect.width, op.copyImageRect.height);
            if (currXform != null)
              MathUtils.transformRectCoords(dr, currXform, dr);
            //									if (currEffectClip != null)
            //										dr.intersect(dr, currEffectClip, dr);
            if (dr.width <= 0 || dr.height <= 0)
            {
              op.texture.removeNativeRef(this, op.textureIndex);
              return;
            }
          }
          if ((gfxVideoPropsSupportMask & VIDEO_MODE_MASK_TL_ORIGIN) != 0)
          {
            pendingVidSurfCoords = new int[] {
                0, 0, 4096, 4096,
                (int)(((dr.x)*4096/master.getWidth()) + 0.5f),
                (int)(((dr.y)*4096/master.getHeight()) + 0.5f),
                (int)((dr.width*4096/master.getWidth()) + 0.5f),
                (int)((dr.height*4096/master.getHeight()) + 0.5f),
                255//Math.round(op.alphaFactor * 255 * currEffectAlpha) // We don't need this alpha since we do it in the clear already
            };
          }
          else
          {
            pendingVidSurfCoords = new int[] {
                2048, 2048, 4096, 4096,
                (int)(((dr.x + dr.width/2)*4096/master.getWidth()) + 0.5f),
                (int)(((dr.y + dr.height/2)*4096/master.getHeight()) + 0.5f),
                (int)((dr.width*4096/master.getWidth()) + 0.5f),
                (int)((dr.height*4096/master.getHeight()) + 0.5f),
                255//Math.round(op.alphaFactor * 255 * currEffectAlpha) // We don't need this alpha since we do it in the clear already
            };
          }
          /*									Math.round((op.srcRect.x + op.srcRect.width/2)*4096/op.texture.getWidth(op.textureIndex)),
						Math.round((op.srcRect.y + op.srcRect.height/2)*4096/op.texture.getHeight(op.textureIndex)),
						Math.round(op.srcRect.width*4096/op.texture.getWidth(op.textureIndex)),
						Math.round(op.srcRect.height*4096/op.texture.getHeight(op.textureIndex)),
						Math.round((op.destRect.x + op.destRect.width/2)*4096/master.getWidth()),
						Math.round((op.destRect.y + op.destRect.height/2)*4096/master.getHeight()),
						Math.round(op.destRect.width*4096/master.getWidth()),
						Math.round(op.destRect.height*4096/master.getHeight()),
						Math.round(op.alphaFactor * 255)};*/
        }
        currSurfHiResImageOp = op;
        op.surface = lastHiResImageLayer;
        if (uiMgr.areLayersEnabled())
          compositeOps.add(op);
        currSurfHiResImageOp.privateData = new java.awt.geom.Rectangle2D.Float();
        ((java.awt.geom.Rectangle2D.Float)currSurfHiResImageOp.privateData).setRect(currSurfHiResImageOp.srcRect);
      }
      else
      {
        if (op.diffuseTexture != null && diffuseTextureSupport)
        {
          long diffuseNativePtr = op.diffuseTexture.getNativeImage(this, 0);
          int srcx = (int)(op.srcRect.x + 0.5f);
          int srcy = (int)(op.srcRect.y + 0.5f);
          int dsrcx = (int)(op.diffuseSrcRect.x + 0.5f);
          int dsrcy = (int)(op.diffuseSrcRect.y + 0.5f);
          drawTexturedDiffusedRectMini(intRectd.x,
              intRectd.y,
              (int)(op.destRect.width + 0.5f),
              (int)(op.destRect.height + 0.5f),
              nativePtr,
              srcx,
              srcy,
              Math.min(op.texture.getWidth() - srcx, (int)(op.srcRect.width + 0.5f)),
              Math.min(op.texture.getHeight() - srcy, (int)(op.srcRect.height + 0.5f)),
              getCompositedColor(op.renderColor, op.alphaFactor * currEffectAlpha),
              diffuseNativePtr,
              dsrcx,
              dsrcy,
              Math.min(op.diffuseTexture.getWidth() - dsrcx, (int)(op.diffuseSrcRect.width + 0.5f)),
              Math.min(op.diffuseTexture.getHeight() - dsrcy, (int)(op.diffuseSrcRect.height + 0.5f)));
          op.diffuseTexture.removeNativeRef(this, 0);
        }
        else
        {
          // If there's a server-side effect being applied to this image and it represents a thumbnail then be sure the original one is loaded
          // as well so we avoid doing nasty reloading on things the UI thinks is not cached (but we can't know if its a thumbnail...so we do it for all of them)
          MetaImage freeMe = null;
          if (op.texture.getSource() instanceof java.util.Vector)
          {
            freeMe = (MetaImage) ((java.util.Vector) op.texture.getSource()).get(0);
            freeMe.getNativeImage(this, 0);
          }
          int srcx = (int)(op.srcRect.x + 0.5f);
          int srcy = (int)(op.srcRect.y + 0.5f);
          drawTexturedRectMini(intRectd.x,
              intRectd.y,
              (int)(op.destRect.width + 0.5f),
              (int)(op.destRect.height + 0.5f),
              nativePtr,
              srcx,
              srcy,
              Math.min(op.texture.getWidth() - srcx, (int)(op.srcRect.width + 0.5f)),
              Math.min(op.texture.getHeight() - srcy, (int)(op.srcRect.height + 0.5f)),
              getCompositedColor(op.renderColor, op.alphaFactor * currEffectAlpha), true); // Don't blend for now*/
          if (freeMe != null)
            freeMe.removeNativeRef(this, 0);
        }
      }
    }
    else
    {
      drawTexturedRectMini(intRectd.x,
          intRectd.y,
          Math.min(intRectd.width, op.texture.getWidth(op.textureIndex)),
          Math.min(intRectd.height, op.texture.getHeight(op.textureIndex)),
          op.texture.getNativeImage(this, op.textureIndex),
          (int)(op.srcRect.x + 0.5f),
          (int)(op.srcRect.y + 0.5f),
          Math.min(intRectd.width, op.texture.getWidth(op.textureIndex)),
          Math.min(intRectd.height, op.texture.getHeight(op.textureIndex)),
          getCompositedColor(op.renderColor, op.alphaFactor * currEffectAlpha), true);
    }

    op.texture.removeNativeRef(this, op.textureIndex);
  }

  private void processPrimitiveOp(RenderingOp op)
  {
    java.awt.Rectangle cr = getIntRect(op.destRect, cacheRect1);
    if (currXform != null && !xformRenderSupport)
      MathUtils.transformRectCoords(cr, currXform, cr);
    if (currEffectClip != null)
      cr.intersect(cr, currEffectClip, cr);
    if (cr.getWidth() <= 0 || cr.getHeight() <= 0)
      return;
    int thicky = (op.primitive.strokeSize);
    // fix flickering issues with odd-width lines
    thicky += (thicky % 2);
    java.awt.geom.Rectangle2D.Float frect;
    frect = new java.awt.geom.Rectangle2D.Float(
        op.destRect.x-op.srcRect.x,
        op.destRect.y-op.srcRect.y,
        op.primitive.shapeWidth,
        op.primitive.shapeHeight);
    lastPixelRenderCount += op.primitive.shapeWidth * op.primitive.shapeHeight;
    lastPixelInputCount += op.primitive.shapeWidth * op.primitive.shapeHeight;
    if (currXform != null && !xformRenderSupport)
      MathUtils.transformRectCoords(frect, currXform, frect);
    if (cr.width > 0 && cr.height > 0)
    {
      java.awt.Rectangle rect = getIntRect(frect, cacheRect2);
      // Make sure the transformed rect has a usable size as well
      if (rect.getWidth() <= 0 || rect.getHeight() <= 0)
        return;
      if (op.primitive.shapeType.equals("Circle") ||
          op.primitive.shapeType.equals("Oval"))
      {
        if (op.primitive.fill)
        {
          fillOvalMini(rect.x, rect.y, rect.width, rect.height,
              getCompositedColor(
                  getGradientColor(
                      op.primitive, 0, 0),
                      op.alphaFactor * currEffectAlpha),
                      getCompositedColor(
                          getGradientColor(
                              op.primitive, op.primitive.shapeWidth,
                              0),
                              op.alphaFactor * currEffectAlpha),
                              getCompositedColor(
                                  getGradientColor(
                                      op.primitive, op.primitive.shapeWidth,
                                      op.primitive.shapeHeight),
                                      op.alphaFactor * currEffectAlpha),
                                      getCompositedColor(
                                          getGradientColor(
                                              op.primitive, 0,
                                              op.primitive.shapeHeight),
                                              op.alphaFactor * currEffectAlpha),
                                              cr.x, cr.y, cr.width, cr.height);
        }
        else
        {
          drawOvalMini(rect.x, rect.y,
              rect.width, rect.height,
              thicky,
              getCompositedColor(
                  getGradientColor(
                      op.primitive,
                      0,
                      0),
                      op.alphaFactor * currEffectAlpha),
                      getCompositedColor(
                          getGradientColor(
                              op.primitive,
                              op.primitive.shapeWidth,
                              0),
                              op.alphaFactor * currEffectAlpha),
                              getCompositedColor(
                                  getGradientColor(
                                      op.primitive,
                                      op.primitive.shapeWidth,
                                      op.primitive.shapeHeight),
                                      op.alphaFactor * currEffectAlpha),
                                      getCompositedColor(
                                          getGradientColor(
                                              op.primitive,
                                              0,
                                              op.primitive.shapeHeight),
                                              op.alphaFactor * currEffectAlpha),
                                              cr.x, cr.y, cr.width, cr.height);
        }
      }
      else if (op.primitive.shapeType.equals("Square") ||
          op.primitive.shapeType.equals("Rectangle"))
      {
        if (op.primitive.cornerArc == 0)
        {
          if (op.primitive.fill)
          {
            fillRectMini(cr.x, cr.y, cr.width, cr.height,
                getCompositedColor(
                    getGradientColor(
                        op.primitive,
                        op.srcRect.x,
                        op.srcRect.y),
                        op.alphaFactor * currEffectAlpha),
                        getCompositedColor(
                            getGradientColor(
                                op.primitive,
                                op.srcRect.x + op.srcRect.width,
                                op.srcRect.y),
                                op.alphaFactor * currEffectAlpha),
                                getCompositedColor(
                                    getGradientColor(
                                        op.primitive,
                                        op.srcRect.x + op.srcRect.width,
                                        op.srcRect.y + op.srcRect.height),
                                        op.alphaFactor * currEffectAlpha),
                                        getCompositedColor(
                                            getGradientColor(
                                                op.primitive,
                                                op.srcRect.x,
                                                op.srcRect.y + op.srcRect.height),
                                                op.alphaFactor * currEffectAlpha));
          }
          else
          {
            // If the rectangle is clipped then we have to draw it as a bunch of lines instead
            if (rect.x < cr.x || rect.y < cr.y || rect.x + rect.width > cr.x + cr.width ||
                rect.y + rect.height > cr.y + cr.height)
            {
              //System.out.println("Using lines instead of rect because rect draw is clipped");
              int x1 = Math.max(cr.x, rect.x);
              int y1 = Math.max(cr.y, rect.y);
              int x2 = Math.min(cr.x + cr.width, rect.x + rect.width);
              int y2 = Math.min(cr.y + cr.height, rect.y + rect.height);
              // If there's something to draw, then draw it
              if (x1 + 2*thicky < x2 && y1 + 2*thicky < y2)
              {
                // Draw the top
                boolean drewTop = false;
                boolean drewBottom = false;
                if (y1 < rect.y + thicky)
                {
                  fillRectMini(x1, y1, x2 - x1, rect.y + thicky - y1,
                      getCompositedColor(getGradientColor(op.primitive,
                          op.srcRect.x, op.srcRect.y), op.alphaFactor * currEffectAlpha),
                          getCompositedColor(getGradientColor(op.primitive,
                              op.srcRect.x + op.srcRect.width, op.srcRect.y), op.alphaFactor * currEffectAlpha),
                              getCompositedColor(getGradientColor(op.primitive,
                                  op.srcRect.x + op.srcRect.width, op.srcRect.y + thicky), op.alphaFactor * currEffectAlpha),
                                  getCompositedColor(getGradientColor(op.primitive,
                                      op.srcRect.x, op.srcRect.y + thicky), op.alphaFactor * currEffectAlpha));
                  drewTop = true;
                }
                // Draw the bottom
                if (y2 > rect.y + rect.height - thicky)
                {
                  fillRectMini(x1, rect.y + rect.height - thicky, x2 - x1, y2 - (rect.y + rect.height - thicky),
                      getCompositedColor(getGradientColor(op.primitive,
                          op.srcRect.x, op.srcRect.y + op.srcRect.height - thicky), op.alphaFactor * currEffectAlpha),
                          getCompositedColor(getGradientColor(op.primitive,
                              op.srcRect.x + op.srcRect.width, op.srcRect.y + op.srcRect.height - thicky), op.alphaFactor * currEffectAlpha),
                              getCompositedColor(getGradientColor(op.primitive,
                                  op.srcRect.x + op.srcRect.width, op.srcRect.y + op.srcRect.height), op.alphaFactor * currEffectAlpha),
                                  getCompositedColor(getGradientColor(op.primitive,
                                      op.srcRect.x, op.srcRect.y + op.srcRect.height), op.alphaFactor * currEffectAlpha));
                  drewBottom = true;
                }
                // Draw the left
                if (x1 < rect.x + thicky && y2 > y1 + (drewTop ? thicky : 0) + (drewBottom ? thicky : 0))
                  fillRectMini(x1, y1 + (drewTop ? thicky : 0), rect.x + thicky - x1, y2 - y1 - (drewTop ? thicky : 0) - (drewBottom ? thicky : 0),
                      getCompositedColor(getGradientColor(op.primitive,
                          op.srcRect.x, op.srcRect.y), op.alphaFactor * currEffectAlpha),
                          getCompositedColor(getGradientColor(op.primitive,
                              op.srcRect.x + thicky, op.srcRect.y), op.alphaFactor * currEffectAlpha),
                              getCompositedColor(getGradientColor(op.primitive,
                                  op.srcRect.x + thicky, op.srcRect.y + op.srcRect.height), op.alphaFactor * currEffectAlpha),
                                  getCompositedColor(getGradientColor(op.primitive,
                                      op.srcRect.x, op.srcRect.y + op.srcRect.height), op.alphaFactor * currEffectAlpha));
                // Draw the right
                if (x2 > rect.x + rect.width - thicky && y2 > y1 + (drewTop ? thicky : 0) + (drewBottom ? thicky : 0))
                  fillRectMini(rect.x + rect.width - thicky, y1 + (drewTop ? thicky : 0),
                      x2 - (rect.x + rect.width - thicky), y2 - y1 - (drewTop ? thicky : 0) - (drewBottom ? thicky : 0),
                      getCompositedColor(getGradientColor(op.primitive,
                          op.srcRect.x + op.srcRect.width - thicky, op.srcRect.y), op.alphaFactor * currEffectAlpha),
                          getCompositedColor(getGradientColor(op.primitive,
                              op.srcRect.x + op.srcRect.width, op.srcRect.y), op.alphaFactor * currEffectAlpha),
                              getCompositedColor(getGradientColor(op.primitive,
                                  op.srcRect.x + op.srcRect.width, op.srcRect.y + op.srcRect.height), op.alphaFactor * currEffectAlpha),
                                  getCompositedColor(getGradientColor(op.primitive,
                                      op.srcRect.x + op.srcRect.width - thicky, op.srcRect.y + op.srcRect.height), op.alphaFactor * currEffectAlpha));
              }
            }
            else
            {
              drawRectMini(cr.x, cr.y,
                  cr.width, cr.height, thicky,
                  getCompositedColor(
                      getGradientColor(
                          op.primitive,
                          op.srcRect.x,
                          op.srcRect.y),
                          op.alphaFactor * currEffectAlpha),
                          getCompositedColor(
                              getGradientColor(
                                  op.primitive,
                                  op.srcRect.x + op.srcRect.width,
                                  op.srcRect.y),
                                  op.alphaFactor * currEffectAlpha),
                                  getCompositedColor(
                                      getGradientColor(
                                          op.primitive,
                                          op.srcRect.x + op.srcRect.width,
                                          op.srcRect.y + op.srcRect.height),
                                          op.alphaFactor * currEffectAlpha),
                                          getCompositedColor(
                                              getGradientColor(
                                                  op.primitive,
                                                  op.srcRect.x,
                                                  op.srcRect.y + op.srcRect.height),
                                                  op.alphaFactor * currEffectAlpha));
            }
          }
        }
        else
        {
          op.primitive.cornerArc =
              Math.min(op.primitive.cornerArc,
                  (int)(Math.min(
                      op.primitive.shapeWidth/2,
                      op.primitive.shapeHeight/2)));
          if (op.primitive.fill)
          {
            fillRoundRectMini(rect.x, rect.y,
                rect.width, rect.height,
                op.primitive.cornerArc,
                getCompositedColor(
                    getGradientColor(
                        op.primitive,
                        0,
                        0),
                        op.alphaFactor * currEffectAlpha),
                        getCompositedColor(
                            getGradientColor(
                                op.primitive,
                                op.primitive.shapeWidth,
                                0),
                                op.alphaFactor * currEffectAlpha),
                                getCompositedColor(
                                    getGradientColor(
                                        op.primitive,
                                        op.primitive.shapeWidth,
                                        op.primitive.shapeHeight),
                                        op.alphaFactor * currEffectAlpha),
                                        getCompositedColor(
                                            getGradientColor(
                                                op.primitive,
                                                0,
                                                op.primitive.shapeHeight),
                                                op.alphaFactor * currEffectAlpha),
                                                cr.x, cr.y, cr.width, cr.height);
          }
          else
          {
            drawRoundRectMini(
                rect.x, rect.y,
                rect.width, rect.height,
                thicky, op.primitive.cornerArc,
                getCompositedColor(
                    getGradientColor(
                        op.primitive,
                        0,
                        0),
                        op.alphaFactor * currEffectAlpha),
                        getCompositedColor(
                            getGradientColor(
                                op.primitive,
                                op.primitive.shapeWidth,
                                0),
                                op.alphaFactor * currEffectAlpha),
                                getCompositedColor(
                                    getGradientColor(
                                        op.primitive,
                                        op.primitive.shapeWidth,
                                        op.primitive.shapeHeight),
                                        op.alphaFactor * currEffectAlpha),
                                        getCompositedColor(
                                            getGradientColor(
                                                op.primitive,
                                                0,
                                                op.primitive.shapeHeight),
                                                op.alphaFactor * currEffectAlpha),
                                                cr.x, cr.y,
                                                cr.width, cr.height);
          }
        }
      }
      else if (op.primitive.shapeType.equals("Line"))
      {
        float dx, dy;
        if (rect.width == 0)
        {
          dx = 0;
          dy = 1;
        }
        else
        {
          float m = (rect.height)/((float)rect.width);
          float mp = -1.0f/m;
          if (Math.abs(mp) < 1.0)
          {
            dy = 1.0f;
            dx = dy / mp;
          }
          else
          {
            dx = 1.0f;
            dy = dx * mp;
          }
        }
        for (int q = -thicky/2; q < thicky/2; q++)
        {
          int x1 = (int)(rect.x + q*dx);
          int y1 = (int)(rect.y + q*dy);
          int x2 = (int)(rect.x + rect.width + q*dx);
          int y2 = (int)(rect.y + rect.height + q*dy);
          x1 = Math.max(0, Math.min(master.getWidth() - 1, x1));
          x2 = Math.max(0, Math.min(master.getWidth() - 1, x2));
          y1 = Math.max(0, Math.min(master.getHeight() - 1, y1));
          y2 = Math.max(0, Math.min(master.getHeight() - 1, y2));
          drawLineMini(x1, y1, x2, y2,
              getCompositedColor(
                  getGradientColor(
                      op.primitive,
                      (int)(op.srcRect.x + q*dx),
                      (int)(op.srcRect.y + q*dy)),
                      op.alphaFactor * currEffectAlpha),
                      getCompositedColor(
                          getGradientColor(
                              op.primitive,
                              (int)(op.srcRect.x + op.srcRect.width + q*dx),
                              (int)(op.srcRect.y + op.srcRect.height + q*dy)),
                              op.alphaFactor * currEffectAlpha));
        }
      }
    }
  }

  private void processVideoOp(RenderingOp op, java.awt.Rectangle clipRect)
  {
    /*
     * We're always using the color key to clear the video area.
     * If we happen to get extra rendering quality because its
     * working and we don't know that, nothing's wrong with that.
     */

    // For extenders they don't need the black bars to clear the video plane
    // like we do on Desktop systems
    boolean disableColorBars = isMediaExtender();
    //if (DBG) System.out.println(
    //    "Clearing video rect for OSD: " + op.destRect);
    // For some reason if this has zero transparency then it doesn't get drawn
    java.awt.Color ckey = remoteColorKey;
    if (ckey == null || !colorKeying)
      ckey = new java.awt.Color(1, 1, 1, 1);
    // Calculate the actual rectangle that the video will cover
    int vw, vh;
    java.awt.Dimension vfVidSize = vf.getVideoSize();
    vw = vfVidSize != null ? vfVidSize.width : 0;
    if (vw <= 0)
      vw = 720;
    vh = vfVidSize != null ? vfVidSize.height : 0;
    if (vh <= 0)
      vh = MMC.getInstance().isNTSCVideoFormat() ? 480 : 576;
    int assMode = vf.getAspectRatioMode();
    float targetX = op.destRect.x;
    float targetY = op.destRect.y;
    float targetW = op.destRect.width;
    float targetH = op.destRect.height;
    float forcedAspect = vf.getCurrentAspectRatio();
    if (forcedAspect != 0)
    {
      if (targetW < forcedAspect * targetH)
      {
        float shrink = targetH - targetW / forcedAspect;
        targetH -= shrink;
        targetY += shrink/2;
      }
      else
      {
        float shrink = targetW - targetH * forcedAspect;
        targetW -= shrink;
        targetX += shrink/2;
      }
    }
    float zoomX = vf.getVideoZoomX(assMode);
    float zoomY = vf.getVideoZoomY(assMode);
    float transX =
        vf.getVideoOffsetX(assMode) * targetW / master.getWidth();
    float transY =
        vf.getVideoOffsetY(assMode) * targetH / master.getHeight();

    float widthAdjust = ((zoomX - 1.0f)*targetW);
    float heightAdjust = ((zoomY - 1.0f)*targetH);
    targetX -= widthAdjust/2;
    targetY -= heightAdjust/2;
    targetW += widthAdjust;
    targetH += heightAdjust;

    targetX += transX;
    targetY += transY;

    long videoHShiftFreq =  vf.getVideoHShiftFreq();
    if (videoHShiftFreq != 0)
    {
      float maxHShift = (op.destRect.width - targetW)/2;
      long timeDiff = Sage.eventTime();
      timeDiff %= videoHShiftFreq;
      if (timeDiff < videoHShiftFreq/2)
      {
        if (timeDiff < videoHShiftFreq/4)
          targetX -= maxHShift*timeDiff*4/videoHShiftFreq;
        else
          targetX -= maxHShift - (maxHShift*(timeDiff -
              videoHShiftFreq/4)*4/videoHShiftFreq);
      }
      else
      {
        timeDiff -= videoHShiftFreq/2;
        if (timeDiff < videoHShiftFreq/4)
          targetX += maxHShift*timeDiff*4/videoHShiftFreq;
        else
          targetX += maxHShift - (maxHShift*(timeDiff -
              videoHShiftFreq/4)*4/videoHShiftFreq);
      }
    }

    targetX = (int)(targetX + 0.5f);
    targetY = (int)(targetY + 0.5f);
    targetW = (int)(targetW + 0.5f);
    targetH = (int)(targetH + 0.5f);
    java.awt.geom.Rectangle2D.Float videoSrc =
        new java.awt.geom.Rectangle2D.Float(0, 0, vw, vh);
    java.awt.geom.Rectangle2D.Float videoDest =
        new java.awt.geom.Rectangle2D.Float(
            targetX, targetY, targetW, targetH);
    Sage.clipSrcDestRects(op.destRect, videoSrc, videoDest);
    java.awt.Rectangle usedVideoRect = new java.awt.Rectangle();
    java.awt.Rectangle fullVideoRect = new java.awt.Rectangle();
    usedVideoRect.setFrame(videoDest);
    fullVideoRect.setFrame(op.destRect);

    // Clear the video rect area
    int rgbVideo = vf.getVideoBGColor().getRGB();
    rgbVideo &= 0xFFFFFF; // 0 alpha

    if (colorKeying)
      rgbVideo = ckey.getRGB() | 0xFF000000;

    // If this is an audio-only file, then use solid black for this instead of transparent black
    MediaFile mf = vf.getCurrFile();
    if (mf != null && mf.isAudioOnlyTVFile())
    {
      rgbVideo = 0xFF000000;
    }

    clearRectMini(fullVideoRect.x, fullVideoRect.y,
        fullVideoRect.width, fullVideoRect.height,
        rgbVideo, rgbVideo, rgbVideo, rgbVideo, clipRect);

    if (!disableColorBars)
    {
      //g2.setBackground(vf.getVideoBGColor());
      // Need to clear the left edge of the video region
      rgbVideo |= 0xFF000000;
      if (usedVideoRect.x > fullVideoRect.x)
      {
        clearRectMini(fullVideoRect.x, fullVideoRect.y,
            usedVideoRect.x - fullVideoRect.x,
            fullVideoRect.height,
            rgbVideo, rgbVideo, rgbVideo, rgbVideo, clipRect);
      }
      // Need to clear the top edge of the video region
      if (usedVideoRect.y > fullVideoRect.y)
      {
        clearRectMini(fullVideoRect.x, fullVideoRect.y,
            fullVideoRect.width, usedVideoRect.y - fullVideoRect.y,
            rgbVideo, rgbVideo, rgbVideo, rgbVideo, clipRect);
      }
      // Need to clear the right edge of the video region
      if (usedVideoRect.x + usedVideoRect.width <
          fullVideoRect.x + fullVideoRect.width)
      {
        int adjust = (fullVideoRect.x + fullVideoRect.width) -
            (usedVideoRect.x + usedVideoRect.width);
        clearRectMini(fullVideoRect.x + fullVideoRect.width - adjust,
            fullVideoRect.y, adjust,
            fullVideoRect.height,
            rgbVideo, rgbVideo, rgbVideo, rgbVideo, clipRect);
      }
      // Need to clear the bottom edge of the video region
      if (usedVideoRect.y + usedVideoRect.height <
          fullVideoRect.y + fullVideoRect.height)
      {
        int adjust = (fullVideoRect.y + fullVideoRect.height) -
            (usedVideoRect.y + usedVideoRect.height);
        clearRectMini(fullVideoRect.x,
            fullVideoRect.y + fullVideoRect.height - adjust,
            fullVideoRect.width,
            adjust,
            rgbVideo, rgbVideo, rgbVideo, rgbVideo, clipRect);
      }
    }

    /*                if (op.destRect.width == master.getRoot().getWidth() &&
			op.destRect.height == master.getRoot().getHeight())
		{
			refreshVideo = true;
			vf.refreshVideoSizing();
			//VideoFrame.getInstance().refreshVideoSizing();
		}
		else*/
    {
      if (gfxVideoPropsSupportMask != 0)
      {
        refreshVideo = true;
        if (supportsAdvancedAspectRatios())
        {
          setVideoPropertiesMini(VIDEO_MODE_MASK_OUTPUT,
              0, 0, 0, 0,
              (int)(((op.destRect.x + op.destRect.width/2)*4096/master.getWidth()) + 0.5f),
              (int)(((op.destRect.y + op.destRect.height/2)*4096/master.getHeight()) + 0.5f),
              (int)((op.destRect.width*4096/master.getWidth()) + 0.5f),
              (int)((op.destRect.height*4096/master.getHeight()) + 0.5f),
              255, lastUsedHighResSurf = pendingHiResSurfHandleToSet = 0);
        }
        else
        {
          setVideoPropertiesMini(VIDEO_MODE_MASK_OUTPUT | VIDEO_MODE_MASK_SOURCE,
              (int)(videoSrc.x + 0.5f), (int)(videoSrc.y + 0.5f), (int)(videoSrc.width + 0.5f), (int)(videoSrc.height + 0.5f),
              (int)(videoDest.x + 0.5f), (int)(videoDest.y + 0.5f), (int)(videoDest.width + 0.5f), (int)(videoDest.height + 0.5f),
              255, 0);
        }
      }
      else
      {
        vf.setVideoBounds(op.destRect);
        refreshVideo = true;
        vf.refreshVideoSizing();
      }
      //VideoFrame.getInstance().refreshVideoSizing();
    }
  }

  public void present(java.awt.Rectangle clipRect)
  {
    flipBufferMini((aliveAnims || (master.getNextAnimationTime() - Sage.eventTime() < 2000)) ? 0 : 1);

    lastPresentTime = Sage.eventTime();
    markAnimationStartTimes();
    // Fix all of the start times for the animations that account for the delay in rendering this frame
    if (!virginsToFix.isEmpty())
    {
      long startDiff = Sage.eventTime() - renderStartTime;
      for (int i = 0; i < virginsToFix.size(); i++)
      {
        EffectTracker et = (EffectTracker) virginsToFix.get(i);
        et.fixStartTime(startDiff);
      }
    }
  }

  public void cleanupRenderer()
  {
    super.cleanupRenderer();
    deinitMini();
    if (loopbackConnection)
      localHostConnectionInUse = false;
  }

  // Returns true if this scaling operation should be done on the server instead of the PS client.
  // We want to do that when it's a large JPEG image in the pic lib that'll be rendered at a smaller
  // size on the client.
  private boolean shouldScaleBeforeTransfer(MetaImage image, int imageIndex)
  {
    int targetWidth = image.getWidth(imageIndex);
    int targetHeight = image.getHeight(imageIndex);
    int srcWidth = image.getWidth();
    int srcHeight = image.getHeight();

    // If the image has scaling insets then we need to scale it server side before we transfer it
    if (image.getImageOption(imageIndex) instanceof java.awt.Insets[])
      return true;

    // If there's 150% as many pixels or more in the source image relative to the target image AND
    // the source image has more than 0.5Mpixels then we scale before the transfer
    int srcTotal = srcWidth * srcHeight;
    int targetTotal = targetWidth * targetHeight;
    if (srcTotal < 500000)
      return false;
    if ((targetTotal * 3 / 2) < srcTotal)
      return true;
    else
      return false;
  }

  public boolean createNativeImage(MetaImage image, int imageIndex)
  {
    if (startedReconnectDaemon && !reconnectCompleted)
      return false;

    long nativePtr = 0;
    sage.media.image.RawImage tightImage = null;
    boolean optimizeFontMem = (image.getSource() instanceof MetaFont) && allow8bppFontTransfer;
    if ((gfxScalingCaps & GFX_SCALING_SW) != 0 && imageIndex != 0 && !(image.getSource() instanceof MetaFont) &&
        !(image.getSource() instanceof java.lang.ref.WeakReference) && !shouldScaleBeforeTransfer(image, imageIndex))
    {
      // Get the native image that'll be used as the source for the transform. This is
      // image index 0.

      // If this requires loading on the other side then it'll be done in this call here.
      pauseIfNotRenderingThread();
      long nativeSrcImage = image.getNativeImage(this, 0);
      try
      {
        int destWidth = image.getWidth(imageIndex);
        int destHeight = image.getHeight(imageIndex);
        Object imageOpt = image.getImageOption(imageIndex);
        int cornerArc = 0;
        if (imageOpt instanceof java.awt.geom.RoundRectangle2D)
        {
          cornerArc = (int)(((java.awt.geom.RoundRectangle2D) imageOpt).getArcWidth()/2 +
              ((java.awt.geom.RoundRectangle2D) imageOpt).getArcHeight()/2 + 0.5f);
        }
        if (advImageCaching)
        {
          int newHandle;
          synchronized (clientHandleCounterLock)
          {
            newHandle = clientHandleCounter++;
          }
          prepImageTargetedMini(newHandle, destWidth, destHeight, "");
          transformImageMini(nativeSrcImage, newHandle, destWidth, destHeight, cornerArc);
          image.setNativePointer(this, imageIndex, newHandle, destWidth*destHeight*4);
        }
        else
        {
          pauseIfNotRenderingThread();
          synchronized (MetaImage.getNiaCacheLock(this))
          {
            while((nativePtr = prepImageMini(destWidth, destHeight, "")) == 0)
            {
              Object[] oldestImage =
                  MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
              if (oldestImage == null)
              {
                image.removeNativeRef(this, 0);
                return false;
              }
              ((MetaImage) oldestImage[0]).clearNativePointer(this,
                  ((Integer) oldestImage[1]).intValue());
            }
            if (Sage.DBG) System.out.println("Allocated image-1 for " + image.getSource() + " width=" + destWidth + " height=" + destHeight + " ptr=" + nativePtr);
          }
          if (nativePtr == -1 && isMediaExtender()) // failed allocation on a dead connection
            return false;
          pauseIfNotRenderingThread();
          nativePtr = transformImageMini(nativeSrcImage, nativePtr, destWidth, destHeight, cornerArc);
          if (nativePtr != 0)
          {
            image.setNativePointer(this, imageIndex, nativePtr,
                destWidth*destHeight*4);
          }
        }
      }
      finally
      {
        image.removeNativeRef(this, 0);
      }
    }
    else if ((imageIndex == 0 && !allowRawImageTransfer && !(image.getSource() instanceof MetaFont) &&
        !(image.getSource() instanceof java.lang.ref.WeakReference) && !(image.getSource() instanceof java.util.Vector) &&
        !image.isNullOrFailed() && image.getRotation() == 0))
    {
      // If it's image index 0 & we're not doing raw transfer then we can just transfer the original source image data.
      // Otherwise we have to transfer the actual Java image recompressed with PNG because we
      // won't be sure what it is.
      String imageRezID = image.getUniqueResourceID(imageIndex);
      int width = image.getWidth(imageIndex);
      int height = image.getHeight(imageIndex);
      if (advImageCaching)
      {
        int newHandle;
        synchronized (clientHandleCounterLock)
        {
          newHandle = clientHandleCounter++;
        }
        // This image is in the offline cache of the client and it supports advanced loading.
        if (clientOfflineCache.contains(imageRezID))
        {
          pauseIfNotRenderingThread();
          loadCachedImagMini(imageRezID, newHandle, width, height);
          image.setNativePointer(this, imageIndex, newHandle, width*height*4);
        }
        else
        {
          pauseIfNotRenderingThread(true);
          prepImageTargetedMini(newHandle, width, height, imageRezID);
          byte[] imageBytes = image.getSourceAsBytes();
          if (imageBytes != null)
            loadImageCompressedMini(newHandle, imageBytes);
          image.setNativePointer(this, imageIndex, newHandle, width*height*4);
        }
      }
      else
      {
        pauseIfNotRenderingThread();
        synchronized (MetaImage.getNiaCacheLock(this))
        {
          while((nativePtr = prepImageMini(width, height, imageRezID)) == 0)
          {
            Object[] oldestImage =
                MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
            if (oldestImage == null)
            {
              return false;
            }
            ((MetaImage) oldestImage[0]).clearNativePointer(this,
                ((Integer) oldestImage[1]).intValue());
          }
          if (Sage.DBG) System.out.println("Allocated image-4 for " + image.getSource() + " width=" + width + " height=" + height + " ptr=" + nativePtr);
        }
        if (nativePtr == -1 && isMediaExtender()) // failed allocation on a dead connection
          return false;
        if (nativePtr < 0 && hasOfflineCache)
        {
          // This means it was in the cache and it's returning the handle * -1
          nativePtr *= -1;
        }
        else
        {
          pauseIfNotRenderingThread();
          byte[] imageBytes = image.getSourceAsBytes();
          if (imageBytes != null)
            nativePtr = loadImageCompressedMini(nativePtr, imageBytes);
        }
        if (nativePtr != 0)
        {
          image.setNativePointer(this, imageIndex, nativePtr,
              width*height*4);
        }
      }
    }
    else if (allowRawImageTransfer && !Sage.getBoolean("ui/disable_native_image_loader", false))
    {
      if ((highResSurfCaps == YUV_CACHE_UNIFIED || (highResSurfCaps == YUV_CACHE_SEPARATE && !uiMgr.getVideoFrame().isLoadingOrLoadedFile())) &&
          image.isJPEG() && (image.getSource() instanceof MediaFile || (highResSurfCaps == YUV_CACHE_UNIFIED && image.getSource() instanceof java.io.File)))
      {
        if (Sage.DBG) System.out.println("Loading JPEG to VIDEO surface for " + image);
        int targetImgWidth = image.getWidth(imageIndex);
        int targetImgHeight = image.getHeight(imageIndex);
        if (Sage.DBG) System.out.println("Allocating a high-res surface of size " + targetImgWidth + "x" + targetImgHeight);
        long newVidSurf;
        pauseIfNotRenderingThread();
        synchronized (MetaImage.getNiaCacheLock(this))
        {
          newVidSurf = loadImageMini(targetImgWidth, targetImgHeight, IMAGE_FORMAT_HIRESYUV);
          while (newVidSurf == 0)
          {
            if (highResSurfCaps == YUV_CACHE_SEPARATE)
            {
              if (highResSurfaceData.isEmpty())
              {
                break;
              }
              if (Sage.DBG) System.out.println("Unloading high res texture to make room for a new one-1");
              Object[] killData = (Object[]) highResSurfaceData.get(0);
              MetaImage killMe = (MetaImage) killData[0];
              killMe.clearNativePointer(this, ((Integer)killData[1]).intValue());
            }
            else
            {
              Object[] oldestImage =
                  MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
              if (oldestImage == null)
              {
                // Check the text surface cache too for something to free until we have a better way
                // to deal with fragmentation issues in memory
                if (!releaseOldestTextCache())
                {
                  break;
                }
              }
              else
              {
                ((MetaImage) oldestImage[0]).clearNativePointer(this,
                    ((Integer) oldestImage[1]).intValue());
              }
            }
            newVidSurf = loadImageMini(targetImgWidth, targetImgHeight, IMAGE_FORMAT_HIRESYUV);
          }
        }
        if (newVidSurf == 0 || newVidSurf == -1)
        {
          if (Sage.DBG) System.out.println("Aborted usage of high res surface because we couldn't allocate one!");
        }
        else
        {
          pauseIfNotRenderingThread();
          Object imgSrc = image.getSource();
          try
          {
            tightImage = sage.media.image.ImageLoader.loadResizedRotatedImageFromFile((imgSrc instanceof MediaFile) ? ((MediaFile) imgSrc).getFile(0).toString() :
              ((java.io.File) imgSrc).getAbsolutePath(),
              targetImgWidth, targetImgHeight, 16, image.getRotation());
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR loading JPEG YUV image of:" + e);
            unloadImageMini(newVidSurf);
            return false;
          }
          if (tightImage == null)
          {
            if (Sage.DBG) System.out.println("Failed loading JPEG YUV image; null returned");
            unloadImageMini(newVidSurf);
            return false;
          }
          pauseIfNotRenderingThread();
          if (Sage.DBG) System.out.println("Successfully loaded YUV image of " + tightImage);
          highResSurfaceData.add(new Object[] { image, new Integer(imageIndex) });
          highResSurfaceHandles.add(new Long(newVidSurf));
          if (Sage.DBG) System.out.println("About to start loading the YUV image into the video plane...");
          int w2 = targetImgWidth;
          int posv=0;

          byte[] nativeImgBuff = (byte[])nativeImgBuffTL.get();
          if (nativeImgBuff == null || nativeImgBuff.length < w2)
          {
            nativeImgBuff = new byte[Math.max(720, targetImgWidth)*2];
            nativeImgBuffTL.set(nativeImgBuff);
          }
          java.nio.ByteBuffer tempData = tightImage.getROData();
          int dataLeftToLoadThisFrame = 102400;
          while(posv<targetImgHeight*2)
          {
            // When we're actively animating, only load 100k of data per-frame
            boolean activeAnims = (effectAnimationsActive || master.isARBuildingDL() || master.isActiveRenderGoingToRun(false) || nextDisplayList != null) &&
                (renderingThread != Thread.currentThread());
            tempData.get(nativeImgBuff, 0, w2);
            pauseIfNotRenderingThread();
            // We need to sync around this whole thing in case something with texture batching comes in during
            // the middle of it and interrupts what's going on.
            synchronized (this)
            {
              if (!loadImageLineMini(newVidSurf, posv, w2, 0, nativeImgBuff))
                break;
            }
            if (activeAnims)
            {
              dataLeftToLoadThisFrame -= w2;
              if (dataLeftToLoadThisFrame < 0)
              {
                synchronized (this)
                {
                  try
                  {
                    // Clean up any batched texture commands
                    if (textureBatchLimit > 0)
                      checkTextureBatchLimit(true);
                    if (sockBuf.position() > 0)
                      sendBufferNow();
                  }catch (java.io.IOException e){}

                }
                waitUntilNextFrameComplete(100);
                dataLeftToLoadThisFrame = 102400;
              }
            }
            else
              dataLeftToLoadThisFrame = 102400;
            posv+=1;
          }

          //					try{clientOutStream.flush();}catch(Exception e){}
          if (Sage.DBG) System.out.println("Done loading the YUV image into the video plane (" + newVidSurf + ")...");
          pauseIfNotRenderingThread();
          image.setNativePointer(this, imageIndex, newVidSurf, targetImgWidth*targetImgHeight*2);
          sage.media.image.ImageLoader.freeImage(tightImage);
          return true;
        }
      }

      // Load the native image data directly and send it
      try
      {
        pauseIfNotRenderingThread();
        tightImage = image.getRawImage(imageIndex);
        int width = image.getWidth(imageIndex);
        int height = image.getHeight(imageIndex);
        if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("Trying to load image " +
            image.getSource().toString() + " width: "+width+" height: "+height);

        pauseIfNotRenderingThread();
        synchronized (MetaImage.getNiaCacheLock(this))
        {
          while((nativePtr = loadImageMini(width, height, optimizeFontMem ? IMAGE_FORMAT_8BPP : IMAGE_FORMAT_ARGB32)) == 0)
          {
            Object[] oldestImage =
                MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
            if (oldestImage == null)
            {
              return false;
            }
            ((MetaImage) oldestImage[0]).clearNativePointer(this,
                ((Integer) oldestImage[1]).intValue());
          }
          if (Sage.DBG) System.out.println("Allocated image-5 for " + image.getSource() + " width=" + width + " height=" + height + " ptr=" + nativePtr);
        }
        if (nativePtr == -1 && isMediaExtender()) // failed allocation on a dead connection
          return false;
        int w4 = width*4;
        int posv=0;


        // We don't want to use the buffer from the image to transfer one line at a time because
        // if we do that it'll do a socket write on each line which is WAY slower than having the double
        // memory copy associated with not doing that
        byte[] nativeImgBuff = (byte[])nativeImgBuffTL.get();
        if (nativeImgBuff == null || nativeImgBuff.length < w4)
        {
          nativeImgBuff = new byte[Math.max(720, width)*4];
          nativeImgBuffTL.set(nativeImgBuff);
        }
        java.nio.ByteBuffer tempData = tightImage.getROData();
        int dataLeftToLoadThisFrame = 102400;
        while(posv<height)
        {
          tempData.get(nativeImgBuff, 0, w4);
          if (!PREMULTIPLY_ALPHA)
          {
            // Un-premultiply the alpha for this image before we transfer it...ick!!
            for (int i = 0; i < w4; i += 4)
            {
              int alpha = nativeImgBuff[i] & 0xFF;
              if (alpha < 0xFF && alpha > 0)
              {
                nativeImgBuff[i + 1] = (byte)((((nativeImgBuff[i + 1] & 0xFF)*255)/alpha));
                nativeImgBuff[i + 2] = (byte)((((nativeImgBuff[i + 2] & 0xFF)*255)/alpha));
                nativeImgBuff[i + 3] = (byte)((((nativeImgBuff[i + 3] & 0xFF)*255)/alpha));
              }
            }
          }
          // When we're actively animating, only load 100k of data per-frame
          boolean activeAnims = (effectAnimationsActive || master.isARBuildingDL() || master.isActiveRenderGoingToRun(false) || nextDisplayList != null) &&
              (renderingThread != Thread.currentThread());
          pauseIfNotRenderingThread();
          // We need to sync around this whole thing in case something with texture batching comes in during
          // the middle of it an interrupts what's going on.
          synchronized (this)
          {
            if (!loadImageLineMini(nativePtr, posv, w4, 0,
                nativeImgBuff))
              break;
          }
          if (activeAnims)
          {
            dataLeftToLoadThisFrame -= w4;
            if (dataLeftToLoadThisFrame < 0)
            {
              synchronized (this)
              {
                try
                {
                  // Clean up any batched texture commands
                  if (textureBatchLimit > 0)
                    checkTextureBatchLimit(true);
                  if (sockBuf.position() > 0)
                    sendBufferNow();
                }catch (java.io.IOException e){}

              }
              waitUntilNextFrameComplete(100);
              dataLeftToLoadThisFrame = 102400;
            }
          }
          else
            dataLeftToLoadThisFrame = 102400;
          posv+=1;
        }

        //				try{clientOutStream.flush();}catch(Exception e){}
        pauseIfNotRenderingThread();
        if (nativePtr != 0)
        {
          image.setNativePointer(this, imageIndex, nativePtr,
              width*height*4);
        }
      }
      finally
      {
        image.removeRawRef(imageIndex);
      }
    }
    else
    {
      if (!Sage.getBoolean("ui/disable_native_image_loader", false) && !allowRawImageTransfer)
      {
        try
        {
          int width = image.getWidth(imageIndex);
          int height = image.getHeight(imageIndex);
          String imageRezID = image.getUniqueResourceID(imageIndex);
          pauseIfNotRenderingThread();
          if (advImageCaching)
          {
            synchronized (clientHandleCounterLock)
            {
              nativePtr = clientHandleCounter++;
            }
            // This image is in the offline cache of the client and it supports advanced loading.
            if (clientOfflineCache.contains(imageRezID))
            {
              loadCachedImagMini(imageRezID, (int)nativePtr, width, height);
              image.setNativePointer(this, imageIndex, nativePtr, width*height*4);
              nativePtr *= -1;
            }
            else
            {
              prepImageTargetedMini((int)nativePtr, width, height, imageRezID);
            }
          }
          else
          {
            synchronized (MetaImage.getNiaCacheLock(this))
            {
              while((nativePtr = prepImageMini(width, height, image.getUniqueResourceID(imageIndex))) == 0)
              {
                Object[] oldestImage =
                    MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
                if (oldestImage == null)
                {
                  return false;
                }
                ((MetaImage) oldestImage[0]).clearNativePointer(this,
                    ((Integer) oldestImage[1]).intValue());
              }
            }
            if (Sage.DBG) System.out.println("Allocated image-6 for " + image.getSource() + " width=" + width + " height=" + height + " ptr=" + nativePtr);
          }
          if (nativePtr == -1 && isMediaExtender()) // failed allocation on a dead connection
            return false;
          if (nativePtr < 0 && hasOfflineCache)
          {
            // This means it was in the cache and it's returning the handle * -1
            nativePtr *= -1;
          }
          else
          {
            if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("Trying to load image " +
                image.getSource() +
                " width: "+width+" height: "+height);
            pauseIfNotRenderingThread();
            tightImage = image.getRawImage(imageIndex);

            // Save the image as a PNG and then transfer that (but do a quick check for a JPEG filename
            // and use JPEG in that case)
            String imgSrcName = image.getLcSourcePathname();
            boolean doJpegTransfer = false;
            if (imgSrcName.endsWith(".jpg") || imgSrcName.endsWith(".jpeg"))
              doJpegTransfer = true;
            byte[] compImgData;
            // The premultiply state should have no effect on this since we're encoding it as a PNG
            if (!doJpegTransfer)
            {
              // We need to make a version that does not have premultiplied alpha and then compress
              // that before we send it.
              java.nio.ByteBuffer fixedData = java.nio.ByteBuffer.allocateDirect(tightImage.getWidth() * tightImage.getHeight() * 4);
              int w4 = width*4;
              byte[] nativeImgBuff = (byte[])nativeImgBuffTL.get();
              if (nativeImgBuff == null || nativeImgBuff.length < w4)
              {
                nativeImgBuff = new byte[Math.max(720, width)*4];
                nativeImgBuffTL.set(nativeImgBuff);
              }
              int posv=0;
              java.nio.ByteBuffer tempData = tightImage.getROData();
              while(posv<height)
              {
                tempData.get(nativeImgBuff, 0, w4);
                // Un-premultiply the alpha for this image before we transfer it...ick!!
                for (int i = 0; i < w4; i += 4)
                {
                  int alpha = nativeImgBuff[i] & 0xFF;
                  if (alpha < 0xFF && alpha > 0)
                  {
                    nativeImgBuff[i + 1] = (byte)((((nativeImgBuff[i + 1] & 0xFF)*255)/alpha));
                    nativeImgBuff[i + 2] = (byte)((((nativeImgBuff[i + 2] & 0xFF)*255)/alpha));
                    nativeImgBuff[i + 3] = (byte)((((nativeImgBuff[i + 3] & 0xFF)*255)/alpha));
                  }
                }
                pauseIfNotRenderingThread();
                fixedData.put(nativeImgBuff, 0, w4);
                posv+=1;
              }
              sage.media.image.RawImage fixedImage = new sage.media.image.RawImage(tightImage.getWidth(), tightImage.getHeight(),
                  fixedData, true, w4, true);
              pauseIfNotRenderingThread();
              compImgData = sage.media.image.ImageLoader.compressImageToMemory(fixedImage, "png");
            }
            else
            {
              pauseIfNotRenderingThread();
              compImgData = sage.media.image.ImageLoader.compressImageToMemory(tightImage, doJpegTransfer ? "jpg" : "png");
            }
            if (advImageCaching)
            {
              pauseIfNotRenderingThread(true);
              loadImageCompressedMini(nativePtr, compImgData);
            }
            else
            {
              pauseIfNotRenderingThread();
              nativePtr = loadImageCompressedMini(nativePtr, compImgData);
            }
          }
          if (nativePtr != 0)
          {
            image.setNativePointer(this, imageIndex, nativePtr,
                width*height*4);
          }
        }
        finally
        {
          image.removeRawRef(imageIndex);
        }
      }
      else
      {
        // if we don't have this as a buffered image, we need to convert it
        java.awt.image.BufferedImage tempBuf;
        java.awt.Image javaImage = image.getJavaImage(imageIndex);
        try
        {
          pauseIfNotRenderingThread();
          if (!(javaImage instanceof java.awt.image.BufferedImage) ||
            ((((java.awt.image.BufferedImage) javaImage).getType() !=
              java.awt.image.BufferedImage.TYPE_INT_ARGB) &&
              (((java.awt.image.BufferedImage) javaImage).getType() !=
                java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE)))
          {
            if (!(javaImage instanceof java.awt.image.BufferedImage))
              ImageUtils.ensureImageIsLoaded(javaImage);
            tempBuf = ImageUtils.createBestImage(javaImage);
          } else
          {
            tempBuf = (java.awt.image.BufferedImage) javaImage;
          }
          if (tempBuf.isAlphaPremultiplied() != PREMULTIPLY_ALPHA && image.hasAlpha())
          {
            if (Sage.DBG)
              System.out.println((PREMULTIPLY_ALPHA ? "" : "Un-") + "Premultiplying alpha for BuffImage...");
            pauseIfNotRenderingThread();
            tempBuf.coerceData(PREMULTIPLY_ALPHA);
            if (Sage.DBG)
              System.out.println("Done " + (PREMULTIPLY_ALPHA ? "" : "Un-") + "Premultiplying alpha for BuffImage...");
          }
          int width = image.getWidth(imageIndex);
          int height = image.getHeight(imageIndex);
          if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("Trying to load image " +
            image.getSource() + " width: " + width + " height: " + height);

          if (!allowRawImageTransfer)
          {
            // Save the image as a PNG and then transfer that (but do a quick check for a JPEG filename
            // and use JPEG in that case)
            String imgSrcName = image.getLcSourcePathname();
            boolean doJpegTransfer = false;
            if (imgSrcName.endsWith(".jpg") || imgSrcName.endsWith(".jpeg"))
              doJpegTransfer = true;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            pauseIfNotRenderingThread();
            try
            {
              javax.imageio.ImageIO.write(tempBuf, doJpegTransfer ? "jpg" : "png", baos);
            } catch (java.io.IOException e)
            {
              System.out.println("ERROR with ImageIO Library! " + e);
            }
            synchronized (MetaImage.getNiaCacheLock(this))
            {
              while ((nativePtr = prepImageMini(width, height, "")) == 0)
              {
                Object[] oldestImage =
                  MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
                if (oldestImage == null)
                {
                  return false;
                }
                ((MetaImage) oldestImage[0]).clearNativePointer(this,
                  ((Integer) oldestImage[1]).intValue());
              }
            }
            if (nativePtr == -1 && isMediaExtender()) // failed allocation on a dead connection
              return false;
            if (advImageCaching)
            {
              pauseIfNotRenderingThread(true);
              loadImageCompressedMini(nativePtr, baos.toByteArray());
            } else
            {
              pauseIfNotRenderingThread();
              nativePtr = loadImageCompressedMini(nativePtr, baos.toByteArray());
            }
          } else
          {
            if (advImageCaching)
            {
              synchronized (clientHandleCounterLock)
              {
                nativePtr = clientHandleCounter++;
              }
              loadImageTargetedMini((int) nativePtr, width, height, optimizeFontMem ? IMAGE_FORMAT_8BPP : IMAGE_FORMAT_ARGB32);
            } else
            {
              synchronized (MetaImage.getNiaCacheLock(this))
              {
                while ((nativePtr = loadImageMini(width, height, optimizeFontMem ? IMAGE_FORMAT_8BPP : IMAGE_FORMAT_ARGB32)) == 0)
                {
                  Object[] oldestImage =
                    MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
                  if (oldestImage == null)
                  {
                    return false;
                  }
                  ((MetaImage) oldestImage[0]).clearNativePointer(this,
                    ((Integer) oldestImage[1]).intValue());
                }
              }
            }
            if (nativePtr == -1 && isMediaExtender()) // failed allocation on a dead connection
              return false;

            int posv = 0;
            int[] texturedata =
              ((java.awt.image.DataBufferInt)
                tempBuf.getRaster().getDataBuffer()).getData();
            while (posv < height)
            {
              pauseIfNotRenderingThread();
              if (!loadImageLineMini(nativePtr, posv, width, width * posv,
                texturedata))
                break;
              posv += 1;
            }
            //						try{clientOutStream.flush();}catch(Exception e){}
          }
          if (nativePtr != 0)
          {
            image.setNativePointer(this, imageIndex, nativePtr,
              width * height * 4);
          }
        } finally
        {
          image.removeJavaRef(imageIndex);
        }
      }
    }
    return (nativePtr != 0);
  }

  public void releaseNativeImage(long nativePointer)
  {
    unloadImageMini(nativePointer);
  }

  public void releaseHiResNativeImages()
  {
    // We only need to do this if we maintain a separate YUV texture cache which is then shared w/ the decoder
    boolean freedImages = false;
    if (lastUsedHighResSurf != 0)
    {
      lastUsedHighResSurf = 0;
      setVideoPropertiesMini(VIDEO_MODE_MASK_HANDLE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
      freedImages = true;
    }
    while (highResSurfCaps == YUV_CACHE_SEPARATE && !highResSurfaceData.isEmpty())
    {
      if (Sage.DBG) System.out.println("Unloading high res texture to make room for a new one");
      Object[] killData = (Object[]) highResSurfaceData.get(0);
      MetaImage killMe = (MetaImage) killData[0];
      killMe.clearNativePointer(this, ((Integer)killData[1]).intValue());
      freedImages = true;
    }
    if (freedImages) // make sure they've completed
    {
      synchronized (this)
      {
        try
        {
          sendGetProperty("NOOP");
        }catch (Exception e)
        {}
      }
    }
  }

  public void preloadImage(MetaImage mi)
  {
    int currMaxTextureDim = 0;
    if (mi.getSource() instanceof MediaFile && sage.media.format.MediaFormat.JPEG.equals(((MediaFile)mi.getSource()).getContainerFormat()))
      currMaxTextureDim = getHighResolutionSurfaceDimension();
    if (currMaxTextureDim == 0)
      currMaxTextureDim = maxTextureDim;
    // for iOS clients, we can greatly speed things up by reducing the size of thumbnails; they'll never be shown at the size of the screen, and likely never
    // more than 1/4 of any given dimension, so limit them to that...same for URL based images
    if (shouldUseTinyIOSImage(mi))
      currMaxTextureDim = maxTextureDim/4;
    if (mi.getWidth(0) > currMaxTextureDim ||
        mi.getHeight(0) > currMaxTextureDim)
    {
      int newIdex;
      float xScale = Math.min(currMaxTextureDim,
          mi.getWidth(0))/((float)mi.getWidth(0));
      float yScale = Math.min(currMaxTextureDim,
          mi.getHeight(0))/((float)mi.getHeight(0));
      xScale = yScale = Math.min(xScale, yScale);
      newIdex = mi.getImageIndex(Math.round(xScale *
          mi.getWidth(0)), Math.round(yScale * mi.getHeight(0)));

      mi.getNativeImage(this, newIdex);
      mi.removeNativeRef(this, newIdex);
    }
    else
    {
      mi.getNativeImage(this, 0);
      mi.removeNativeRef(this, 0);
    }

  }

  private boolean PREMULTIPLY_ALPHA = true;

  public int getCompositedColor(int color, int alpha)
  {
    alpha = Math.min(255, (alpha * ((color & 0xFF000000) >>> 24)) / 255);
    if (PREMULTIPLY_ALPHA)
    {
      return ((alpha & 0xFF) << 24) |
          (((alpha * ((color >> 16) & 0xFF))/255 & 0xFF) << 16) |
          (((alpha * ((color >> 8) & 0xFF))/255 & 0xFF) << 8) |
          ((alpha * (color & 0xFF))/255 & 0xFF);
    }
    else
    {
      return (alpha << 24) | (color & 0xFFFFFF);
    }
  }

  public int getCompositedColor(java.awt.Color color, float alpha)
  {
    if (color == null)
    {
      alpha = Math.min(1.0f, alpha);
      int val = ((int)(alpha*255 + 0.5f)) & 0xFF;
      if (PREMULTIPLY_ALPHA)
        return (val << 24) | (val << 16) | (val << 8) | val;
      else
        return (val << 24) | 0xFFFFFF;
    }
    alpha = Math.min(alpha * color.getAlpha() / 255.0f, 1.0f);
    if (PREMULTIPLY_ALPHA)
    {
      return ((((int)(alpha * 255 + 0.5f)) & 0xFF) << 24) |
          ((((int)(alpha * color.getRed() + 0.5f)) & 0xFF) << 16) |
          ((((int)(alpha * color.getGreen() + 0.5f)) & 0xFF) << 8) |
          (((int)(alpha * color.getBlue() + 0.5f)) & 0xFF);
    }
    else
    {
      return (((int)(alpha*255 + 0.5f)) << 24) | (color.getRGB() & 0xFFFFFF);
    }
  }

  public int getCompositedColor(int color, float alpha)
  {
    alpha = Math.min(1.0f, alpha * ((color & 0xFF000000) >>> 24) / 255.0f);
    if (PREMULTIPLY_ALPHA)
    {
      return ((((int)(alpha * 255 + 0.5f)) & 0xFF) << 24) |
          ((((int)(alpha * ((color >> 16) & 0xFF) + 0.5f)) & 0xFF) << 16) |
          ((((int)(alpha * ((color >> 8) & 0xFF) + 0.5f)) & 0xFF) << 8) |
          ((int)(alpha * (color & 0xFF) + 0.5f));
    }
    else
    {
      return (((int)(alpha*255 + 0.5f)) << 24) | (color & 0xFFFFFF);
    }
  }

  public static int getGradientColor(ShapeDescription sd, float x, float y)
  {
    if (sd.gradc1 == null)
      return sd.color.getRGB();

    // Calculate the projection of the point onto the vector,
    // and then we use that distance relative to the
    // length of the vector to determine what
    // proportionality of each color to use.

    float frac2 = Math.abs(
        (x-sd.fx1)*(sd.fx2-sd.fx1) + (y-sd.fy1)*(sd.fy2-sd.fy1)) /
        ((sd.fx2-sd.fx1)*(sd.fx2-sd.fx1) +
            (sd.fy2-sd.fy1)*(sd.fy2-sd.fy1));
    if (frac2 > 1.0f || frac2 < 0) // don't convert 1.0 to 0
      frac2 = frac2 % 1.0f;

    float frac1 = 1.0f - frac2;
    return 0xFF000000 |
        (((int)(sd.gradc1.getRed()*frac1 +
            sd.gradc2.getRed()*frac2 + 0.5f)) << 16) |
            (((int)(sd.gradc1.getGreen()*frac1 +
                sd.gradc2.getGreen()*frac2 + 0.5f)) << 8) |
                ((int)(sd.gradc1.getBlue()*frac1 +
                    sd.gradc2.getBlue()*frac2 + 0.5f));
  }

  protected synchronized boolean initMini()
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("initMini()");
    // I think this is an artifact from before the multi-UI system was done, but we implemented the
    // mini-renderer to test it out
    //startUIServer();
    String clientName = uiMgr.getLocalUIClientName();
    timeout = Sage.getInt("ui/remote_ui_connection_timeout", 15000);
    synchronized (mapLock)
    {
      clientSocket = (java.nio.channels.SocketChannel) clientSocketMap.get(clientName);
      long startWait = Sage.eventTime();
      while (clientSocket == null && Sage.eventTime() - startWait < CLIENT_EXPIRE_TIME)
      {
        try
        {
          mapLock.wait(5000);
        }
        catch (InterruptedException e){}
        clientSocket = (java.nio.channels.SocketChannel) clientSocketMap.get(clientName);
      }
      if (clientSocket != null)
      {
        clientSocketMap.remove(clientName);
        clientSocketMapTimes.remove(clientName);
      }
    }
    if (clientSocket == null) return false;
    try
    {
      // Check if it's a local connection. We do this by seeing if it's on the same subnet as us
      byte[] localIP = clientSocket.socket().getLocalAddress().getAddress();
      byte[] remoteIP = clientSocket.socket().getInetAddress().getAddress();
      byte[] subnetMask = IOUtils.getSubnetMask(clientSocket.socket().getLocalAddress()).getAddress();
      if ((localIP[0] & subnetMask[0]) != (remoteIP[0] & subnetMask[0]) ||
          (localIP[1] & subnetMask[1]) != (remoteIP[1] & subnetMask[1]) ||
          (localIP[2] & subnetMask[2]) != (remoteIP[2] & subnetMask[2]) ||
          (localIP[3] & subnetMask[3]) != (remoteIP[3] & subnetMask[3]))
        localConnection = false;
      else
        localConnection = true;

      loopbackConnection = IOUtils.isLocalhostSocket(clientSocket.socket());

      if (uiMgr.getBoolean("force_nonlocal_connection", false))
        localConnection = false;

      // For timeouts to work; we need to use standard socket input streams for this part
      recvr = new MiniUIClientReceiver(clientSocket);
      prepUICmdHeader(8);
      sockBuf.putInt(GFXCMD_INIT<<CMD_LEN_BITS | 4 );
      sockBuf.putInt(MMC.getInstance().isNTSCVideoFormat() ? 0 : 1 );
      sendBufferNow();
      boolean rv = recvr.getIntReply()!=0;
      if (Sage.DBG) System.out.println("MiniUI established for " + clientName + " status=" + rv);

      if (rv)
      {
        // This MASSIVELY speeds up loading when latency is high by sending these first
        // and then processing the replies afterwards
        sendGetPropertyAsync("GFX_TEXTMODE");
        sendGetPropertyAsync("FIRMWARE_VERSION");
        sendGetPropertyAsync("GFX_BLENDMODE");
        sendGetPropertyAsync("GFX_DRAWMODE");
        sendGetPropertyAsync("GFX_SURFACES");
        sendGetPropertyAsync("GFX_HIRES_SURFACES");
        sendGetPropertyAsync("GFX_VIDEO_UPDATE");
        sendGetPropertyAsync("GFX_VIDEO_MASKS");
        sendGetPropertyAsync("GFX_BITMAP_FORMAT");
        sendGetPropertyAsync("GFX_SCALING");
        sendGetPropertyAsync("GFX_NEGSCALING");
        sendGetPropertyAsync("GFX_OFFLINE_IMAGE_CACHE");
        sendGetPropertyAsync("GFX_YUV_IMAGE_CACHE");
        sendGetPropertyAsync("GFX_SUPPORTED_ASPECTS");
        sendGetPropertyAsync("GFX_ASPECT");
        sendGetPropertyAsync("GFX_FIXED_PAR");
        sendGetPropertyAsync("GFX_DIFFUSE_TEXTURES");
        sendGetPropertyAsync("GFX_XFORMS");
        sendGetPropertyAsync("GFX_TEXTURE_BATCH_LIMIT");
        sendGetPropertyAsync("VIDEO_ADVANCED_ASPECT_LIST");
        sendGetPropertyAsync("GFX_SUPPORTED_RESOLUTIONS");
        sendGetPropertyAsync("GFX_SUPPORTED_RESOLUTIONS_DIGITAL");
        sendGetPropertyAsync("GFX_RESOLUTION");
        sendGetPropertyAsync("GFX_HDMI_MODE");
        sendGetPropertyAsync("GFX_COMPOSITE");
        sendGetPropertyAsync("GFX_COLORKEY");
        sendGetPropertyAsync("AUDIO_OUTPUTS");
        sendGetPropertyAsync("AUDIO_OUTPUT");
        sendGetPropertyAsync("INPUT_DEVICES");
        sendGetPropertyAsync("DISPLAY_OVERSCAN");
        sendGetPropertyAsync("VIDEO_CODECS");
        sendGetPropertyAsync("AUDIO_CODECS");
        sendGetPropertyAsync("PULL_AV_CONTAINERS");
        sendGetPropertyAsync("PUSH_AV_CONTAINERS");
        sendGetPropertyAsync("STREAMING_PROTOCOLS");
        sendGetPropertyAsync("FIXED_PUSH_MEDIA_FORMAT");
        sendGetPropertyAsync("OPENURL_INIT");
        sendGetPropertyAsync("FRAME_STEP");
        sendGetPropertyAsync("GFX_CURSORPROP");
        sendGetPropertyAsync("DETAILED_BUFFER_STATS");
        sendGetPropertyAsync("PUSH_BUFFER_SEEKING");
        sendGetPropertyAsync("MEDIA_PLAYER_BUFFER_DELAY");
        sendGetPropertyAsync("REMOTE_FS");
        sendGetPropertyAsync("IR_PROTOCOL");
        sendGetPropertyAsync("GFX_SUBTITLES");
        sendGetPropertyAsync("FORCED_MEDIA_RECONNECT");
        sendGetPropertyAsync("AUTH_CACHE");
        sendGetPropertyAsync("DEINTERLACE_CONTROL");
        sendGetPropertyAsync("PUSH_BUFFER_LIMIT");
        sendGetPropertyAsync("SCREENSHOT_CAP");
        sendGetPropertyAsync("ZLIB_COMM");
        sendGetPropertyAsync("OFFLINE_CACHE_CONTENTS");
        sendGetPropertyAsync("ADVANCED_IMAGE_CACHING");
        sendGetPropertyAsync("VIDEO_ADVANCED_ASPECT");
        sendBufferNow();
        // Now get capabilities properties for this specific miniclient
        // The default is to use image maps for text rendering
        String textModeProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_TEXTMODE=" + textModeProp);
        useImageMapsForText = textModeProp == null || textModeProp.length() == 0 || "None".equalsIgnoreCase(textModeProp);

        remoteVersion = recvr.getStringReply();
        if (remoteVersion != null)
          remoteVersion = remoteVersion.trim();
        if (Sage.DBG) System.out.println("MiniClient FIRMWARE_VERSION=" + remoteVersion);

        String blendModeProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_BLENDMODE=" + blendModeProp);
        if ("POSTMULTIPLY".equalsIgnoreCase(blendModeProp))
          PREMULTIPLY_ALPHA = false;
        else
          PREMULTIPLY_ALPHA = true;

        // The default draw mode is update
        String drawModeProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_DRAWMODE=" + drawModeProp);
        if ("FULLSCREEN".equalsIgnoreCase(drawModeProp))
          partialUpdatesOK = false;
        else
          partialUpdatesOK = true;

        // The default is no surface support
        String surfModeProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_SURFACES=" + surfModeProp);
        if (surfModeProp != null && surfModeProp.equalsIgnoreCase("true"))
          surfaceSupport = true;
        else
          surfaceSupport = false;

        // The default is no surface support
        String hisurfModeProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_HIRES_SURFACES=" + hisurfModeProp);
        highResSurfCaps = YUV_CACHE_NONE;
        // separate is the default; but this can be overriden to be UNIFIED by the GFX_YUV_IMAGE_CACHE property
        if (hisurfModeProp != null && hisurfModeProp.equalsIgnoreCase("true"))
          highResSurfCaps = YUV_CACHE_SEPARATE;

        String vidGfxModeProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_VIDEO_UPDATE=" + vidGfxModeProp);
        if (vidGfxModeProp != null && vidGfxModeProp.equalsIgnoreCase("true"))
          gfxVideoPropsSupportMask = 	VIDEO_MODE_MASK_SOURCE | VIDEO_MODE_MASK_OUTPUT | VIDEO_MODE_MASK_ALPHA | VIDEO_MODE_MASK_HANDLE;
        else
          gfxVideoPropsSupportMask = 0;

        vidGfxModeProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_VIDEO_MASKS=" + vidGfxModeProp);
        if (vidGfxModeProp != null && vidGfxModeProp.length() > 0)
        {
          try
          {
            gfxVideoPropsSupportMask = Integer.parseInt(vidGfxModeProp);
          }
          catch (NumberFormatException nfe)
          {
            if (Sage.DBG) System.out.println("ERROR with property formatting, non-numeric: " + nfe);
          }
        }

        // The default is to only do raw image transfer
        String bitmapFormatProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_BITMAP_FORMAT=" + bitmapFormatProp);
        if (bitmapFormatProp != null && bitmapFormatProp.length() > 0)
        {
          allowRawImageTransfer = false;
          java.util.StringTokenizer toker = new java.util.StringTokenizer(bitmapFormatProp, ",");
          while (toker.hasMoreTokens())
          {
            String currToke = toker.nextToken();
            if (currToke.equalsIgnoreCase("RAW32"))
            {
              allowRawImageTransfer = true;
            }
            else if (currToke.equalsIgnoreCase("SCALE"))
            {
              canDoScalingImageDecode = true;
            }
            else if (currToke.equalsIgnoreCase("RAW8"))
            {
              allow8bppFontTransfer = true;
            }
            else if (currToke.equalsIgnoreCase("DIRECT"))
            {
              allowDirectImageLoading = true;
            }
            else
            {
              if ((compImageFormat == null || "PNG".equalsIgnoreCase(currToke) || !"PNG".equalsIgnoreCase(compImageFormat)))
                compImageFormat = currToke;
            }
          }
        }
        if (!localConnection && compImageFormat != null && allowRawImageTransfer)
        {
          if (Sage.DBG) System.out.println("Disabling raw image transfer for this client since we're not on a local network");
          allowRawImageTransfer = false;
        }

        String gfxScaleProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_SCALING=" + gfxScaleProp);
        if (gfxScaleProp != null)
        {
          if (gfxScaleProp.equalsIgnoreCase("HARDWARE"))
            gfxScalingCaps = GFX_SCALING_HW;
          else if (gfxScaleProp.equalsIgnoreCase("SOFTWARE"))
            gfxScalingCaps = GFX_SCALING_SW;
          else
            gfxScalingCaps = GFX_SCALING_NONE;
        }
        else
          gfxScalingCaps = GFX_SCALING_NONE;

        gfxScaleProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_NEGSCALING=" + gfxScaleProp);
        if (gfxScaleProp != null)
        {
          gfxScaleProp = gfxScaleProp.toLowerCase();
          if (gfxScaleProp.indexOf("horizontal") != -1)
            gfxScalingCaps |= GFX_SCALING_FLIPH;
          if (gfxScaleProp.indexOf("vertical") != -1)
            gfxScalingCaps |= GFX_SCALING_FLIPV;
        }

        String offCacheProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_OFFLINE_IMAGE_CACHE=" + offCacheProp);
        if (offCacheProp != null && offCacheProp.equalsIgnoreCase("true"))
          hasOfflineCache = true;
        else
          hasOfflineCache = false;

        String yuvCacheProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_YUV_IMAGE_CACHE=" + yuvCacheProp);
        if (yuvCacheProp != null)
        {
          if (yuvCacheProp.equalsIgnoreCase("UNIFIED"))
            highResSurfCaps = YUV_CACHE_UNIFIED;
          else if (yuvCacheProp.equalsIgnoreCase("SEPARATE"))
            highResSurfCaps = YUV_CACHE_SEPARATE;
        }

        String arSupportProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_SUPPORTED_ASPECTS=" + arSupportProp);

        String arProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_ASPECT=" + arProp);

        String parProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_FIXED_PAR=" + parProp);
        if (parProp != null && parProp.length() > 0)
        {
          uiMgr.put("ui/forced_pixel_aspect_ratio", parProp);
          try
          {
            uiMgr.getVideoFrame().setPixelAspectRatio(Float.parseFloat(parProp));
          }
          catch (NumberFormatException nfe){}
          iPhoneMode = true;
        }

        String diffTextProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_DIFFUSE_TEXTURES=" + diffTextProp);
        if (diffTextProp != null && diffTextProp.equalsIgnoreCase("true"))
          diffuseTextureSupport = true;
        else
          diffuseTextureSupport = false;

        String xformProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_XFORMS=" + xformProp);
        if (xformProp != null && xformProp.equalsIgnoreCase("true"))
        {
          xformRenderSupport = true;
          gfxScalingCaps |= GFX_SCALING_FLIPH;
          gfxScalingCaps |= GFX_SCALING_FLIPV;
        }
        else
          xformRenderSupport = false;

        String batchLimit = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_TEXTURE_BATCH_LIMIT=" + batchLimit);
        if (batchLimit != null && batchLimit.length() > 0)
        {
          try
          {
            textureBatchLimit = Integer.parseInt(batchLimit);
            textureBatchLimit = Math.min(textureBatchLimit, sockBuf.capacity());
          }
          catch (NumberFormatException nfe)
          {
            if (Sage.DBG) System.out.println("Badly formatted property GFX_TEXTURE_BATCH_LIMIT of:" + batchLimit);
          }
        }

        originalAdvancedARList = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient VIDEO_ADVANCED_ASPECT_LIST=" + originalAdvancedARList);
        loadAdvancedARs();

        String rezSupportProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_SUPPORTED_RESOLUTIONS=" + rezSupportProp);
        if (rezSupportProp != null && rezSupportProp.length() > 0)
        {
          java.util.ArrayList resolutionOptionsVec = new java.util.ArrayList();
          java.util.StringTokenizer toker = new java.util.StringTokenizer(rezSupportProp + ";" +
              uiMgr.get("extender_resolution_extra_modes", ""), ";");
          while (toker.hasMoreTokens())
          {
            String currToke = toker.nextToken();
            if ("windowed".equalsIgnoreCase(currToke))
              remoteWindowedSystem = true;
            else
            {
              resolutionOptionsVec.add(currToke);
              java.awt.Dimension res = parseResolution(currToke);
              if (res != null)
              {
                if (maxClientResolution == null || res.width > maxClientResolution.width || res.height > maxClientResolution.height)
                  maxClientResolution = res;
              }
            }
          }
          if (!remoteWindowedSystem)
          {
            resolutionOptions = (String[]) resolutionOptionsVec.toArray(Pooler.EMPTY_STRING_ARRAY);
            resolutionFormats = new sage.media.format.VideoFormat[resolutionOptions.length];
            for (int i = 0; i < resolutionOptions.length; i++)
            {
              String cleaned = getBarPrefix(resolutionOptions[i]);
              resolutionNameMap.put(cleaned, resolutionOptions[i]);
              resolutionOptions[i] = cleaned;
              resolutionFormats[i] = sage.media.format.VideoFormat.buildVideoFormatForResolution(resolutionOptions[i]);
            }
            // Lock us into full screen mode since we don't have a window system
            remoteFullScreenMode = true;
          }
        }

        setupDigitalResolutions(false);

        String rezProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_RESOLUTION=" + rezProp);
        if (!remoteWindowedSystem)
        {
          if (rezProp == null)
            rezProp = "720x" + (MMC.getInstance().isNTSCVideoFormat() ? "480" : "576");
          maxClientResolution = parseResolution(rezProp);
          displayResolution = sage.media.format.VideoFormat.buildVideoFormatForResolution(getBarPrefix(rezProp));
        }
        else
        {
          displayResolution = new sage.media.format.VideoFormat();
          displayResolution.setWidth(maxClientResolution.width);
          displayResolution.setHeight(maxClientResolution.height);
          displayResolution.setFormatName(maxClientResolution.width + "x" + maxClientResolution.height);
        }

        if (iPhoneMode)
        {
          maxTextureDim = Math.max(displayResolution.getWidth(), displayResolution.getHeight());
        }

        hdmiAutodetectedConnector = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_HDMI_MODE=" + hdmiAutodetectedConnector);

        // The default composite mode is blending
        String compositeProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_COMPOSITE=" + compositeProp);
        if ("COLORKEY".equalsIgnoreCase(compositeProp))
          colorKeying = true;
        else
          colorKeying = false;

        String colorKeyProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_COLORKEY=" + colorKeyProp);
        if (colorKeyProp != null && colorKeyProp.length() > 0)
        {
          try
          {
            remoteColorKey =  new java.awt.Color(Integer.parseInt(colorKeyProp, 16));
          }catch (NumberFormatException e)
          {
            if (Sage.DBG) System.out.println("Invalid colorkey prop:" + e);
          }
        }

        String audioOutputsProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient AUDIO_OUTPUTS=" + audioOutputsProp);
        if (audioOutputsProp != null && audioOutputsProp.length() > 0)
        {
          java.util.StringTokenizer toker = new java.util.StringTokenizer(audioOutputsProp, ";");
          if (toker.countTokens() > 0)
          {
            audioOutputOptions = new String[toker.countTokens()];
            for (int i = 0; i < audioOutputOptions.length; i++)
              audioOutputOptions[i] = toker.nextToken();
          }
        }

        currAudioOutput = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient AUDIO_OUTPUT=" + currAudioOutput);

        inputDevsProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient INPUT_DEVICES=" + inputDevsProp);
        java.util.Properties rawProps = uiMgr.getProperties() != null ? uiMgr.getProperties().getAllPrefs() : null;
        // If we don't have the property set for mouse icons, then set it based off this
        // if the MiniClient returns a value here
        if (inputDevsProp != null && inputDevsProp.length() > 0 && rawProps != null && rawProps.get("show_mouse_icons") == null)
        {
          uiMgr.putBoolean("show_mouse_icons", inputDevsProp.indexOf("MOUSE") != -1);
        }
        String overscanProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient DISPLAY_OVERSCAN=" + overscanProp);
        if (overscanProp != null && overscanProp.length() > 0 && rawProps != null)
        {
          java.util.StringTokenizer toker = new java.util.StringTokenizer(overscanProp, ";");
          if (toker.countTokens() == 4)
          {
            int overX=0, overY=0;
            float overW=1.0f, overH=1.0f;
            try
            {
              overX = Integer.parseInt(toker.nextToken());
              overY = Integer.parseInt(toker.nextToken());
              overW = Float.parseFloat(toker.nextToken());
              overH = Float.parseFloat(toker.nextToken());
              if (rawProps.get("ui/" + UIManager.UI_OVERSCAN_CORRECTION_OFFSET_X) == null)
                uiMgr.setOverscanOffsetX(overX);
              if (rawProps.get("ui/" + UIManager.UI_OVERSCAN_CORRECTION_OFFSET_Y) == null)
                uiMgr.setOverscanOffsetY(overY);
              if (rawProps.get("ui/" + UIManager.UI_OVERSCAN_CORRECTION_PERCT_WIDTH) == null)
                uiMgr.setOverscanScaleWidth(overW);
              if (rawProps.get("ui/" + UIManager.UI_OVERSCAN_CORRECTION_PERCT_HEIGHT) == null)
                uiMgr.setOverscanScaleHeight(overH);
            }
            catch (NumberFormatException e)
            {
              if (Sage.DBG) System.out.println("Error in formatting of overscan display property:" + e);
            }
          }
        }

        String vidCodecsProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient VIDEO_CODECS=" + vidCodecsProp);
        videoCodecs = createSetFromString(vidCodecsProp);
        if (vidCodecsProp == null)
        {
          // Add default video codecs
          videoCodecs.add(sage.media.format.MediaFormat.MPEG2_VIDEO.toUpperCase());
          videoCodecs.add(sage.media.format.MediaFormat.MPEG1_VIDEO.toUpperCase());
        }

        String audCodecsProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient AUDIO_CODECS=" + audCodecsProp);
        audioCodecs = createSetFromString(audCodecsProp);
        if (audCodecsProp == null)
        {
          // Add default audio codecs
          audioCodecs.add(sage.media.format.MediaFormat.MP2.toUpperCase());
          audioCodecs.add(sage.media.format.MediaFormat.MP3.toUpperCase());
          audioCodecs.add(sage.media.format.MediaFormat.AC3.toUpperCase());
        }
        // Fix bad codec names
        if (audioCodecs.remove("MPG1L2"))
          audioCodecs.add(sage.media.format.MediaFormat.MP2);
        if (audioCodecs.remove("MPG1L3"))
          audioCodecs.add(sage.media.format.MediaFormat.MP3);
        String audCodecsToDisable = uiMgr.get("miniclient/disabled_audio_codecs", "");
        if (audCodecsToDisable.length() > 0)
        {
          if (Sage.DBG) System.out.println("Disabling audio codecs: " + audCodecsToDisable);
          audioCodecs.removeAll(createSetFromString(audCodecsToDisable));
        }

        String pullContainersProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient PULL_AV_CONTAINERS=" + pullContainersProp);
        pullContainers = createSetFromString(pullContainersProp);

        String pushContainersProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient PUSH_AV_CONTAINERS=" + pushContainersProp);
        pushContainers = createSetFromString(pushContainersProp);
        if (pushContainersProp == null) // default push format
          pushContainers.add(sage.media.format.MediaFormat.MPEG2_PS.toUpperCase());

        String streamingProtocolsProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient STREAMING_PROTOCOLS=" + streamingProtocolsProp);
        streamingProtocols = createSetFromString(streamingProtocolsProp);

        fixedPushMediaFormatProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient FIXED_PUSH_MEDIA_FORMAT=" + fixedPushMediaFormatProp);

        String openUrlInit = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient OPENURL_INIT=" + openUrlInit);
        needsInitDriver = !(openUrlInit != null && "TRUE".equalsIgnoreCase(openUrlInit));

        String frameStep = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient FRAME_STEP=" + frameStep);
        frameStepSupport = frameStep != null && "TRUE".equalsIgnoreCase(frameStep);

        String cursorProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_CURSORPROP=" + cursorProp);
        waitCursorSupport = cursorProp != null && "TRUE".equalsIgnoreCase(cursorProp);

        if (waitCursorSupport)
        {
          // Load the wait cursor images now
          waitCursorData = new byte[8][];
          for (int i = 0; i < 8; i++)
          {
            MetaImage currCursorImage = MetaImage.getMetaImage(uiMgr.get("ui/wait_cursor_icon_prefix", "images/tvcursor_anim") + i + ".png");
            // Now extract the raw binary data
            // 16 indexed color entries and then 4bpp for color information
            byte[] currData = new byte[64 + currCursorImage.getWidth()*currCursorImage.getHeight()/2];
            sage.media.image.RawImage rawCursor = currCursorImage.getRawImage(0);
            waitCursorWidth = rawCursor.getWidth();
            waitCursorHeight = rawCursor.getHeight();
            java.util.Set indexedColorSet = new java.util.HashSet();
            byte[] rawBuffA = null;
            java.nio.ByteBuffer rawBuffB = null;
            rawBuffB = rawCursor.getROData();
            for (int j = 0; j < rawBuffB.capacity(); j+=4)
            {
              indexedColorSet.add(new Integer(rawBuffB.getInt(j)));
            }
            java.util.HashMap indexMap = new java.util.HashMap();
            java.util.Iterator walker = indexedColorSet.iterator();
            int indexCount = 0;
            int[] indexColors = new int[16];
            while (walker.hasNext())
            {
              Integer currColor = (Integer) walker.next();
              indexColors[indexCount++] = currColor.intValue();
            }
            java.util.Arrays.sort(indexColors);
            for (int j = 0; j < indexColors.length; j++)
            {
              int intColor = indexColors[j];
              // Convert the color from ARGB to AUYV
              int alpha = (intColor >> 24) & 0xFF;
              int red = (alpha == 0 ? 0 : (((intColor >> 16) & 0xFF) * 255/alpha));
              int green = (alpha == 0 ? 0 : (((intColor >> 8) & 0xFF) * 255/alpha));
              int blue = (alpha == 0 ? 0 : ((intColor & 0xFF) * 255/alpha));
              //							int y = (int)((0.299 * red) + (0.587 * green) + (0.114*blue));
              int y = (int)(0.257*red + 0.504*green + 0.098*blue + 16);
              y = Math.max(16, Math.min(235, y));
              //							int u = (int)((blue - y) * 0.565);
              int u = (int)(-0.148*red - 0.291*green + 0.439*blue + 128);
              u = Math.max(16, Math.min(235, u));
              //							int v = (int)((red - y)*0.713);
              int v = (int)(0.439*red - 0.368*green - 0.071*blue + 128);
              v = Math.max(16, Math.min(235, v));
              currData[j*4] = (byte)(v & 0xFF);
              currData[j*4 + 1] = (byte)(y & 0xFF);
              currData[j*4 + 2] = (byte)(u & 0xFF);
              currData[j*4 + 3] = (byte)(alpha & 0xFF);
              //System.out.println("Color #" + indexCount + " rgb=0x" + Long.toString(intColor&0xFFFFFFFFL, 16) + " yuv=0x" + Long.toString((((currData[indexCount*4]&0xFF) << 24) |
              //	((currData[indexCount*4+1] & 0xFF) << 16) | ((currData[indexCount*4+2] & 0xFF) << 8) | (currData[indexCount*4+3] & 0xFF))&0xFFFFFFFFL, 16));
              indexMap.put(new Integer(intColor), new Integer(j));
            }
            // Now build the indexed color cursor information
            for (int j = 0, k = 64; j < rawBuffB.capacity(); j+=4, k++)
            {
              Integer indexValue1 = (Integer)indexMap.get(new Integer(rawBuffB.getInt(j)));
              j+=4;
              Integer indexValue2 = (Integer)indexMap.get(new Integer(rawBuffB.getInt(j)));
              currData[k] = (byte)((((indexValue1.intValue() & 0x0F) << 4) | (indexValue2.intValue() & 0x0F)) & 0xFF);
            }

            waitCursorData[i] = currData;
            currCursorImage.removeRawRef(0);
          }
        }

        String dtpProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient DETAILED_BUFFER_STATS=" + dtpProp);
        detailedPushBufferStats = "true".equalsIgnoreCase(dtpProp);

        String pushSeekProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient PUSH_BUFFER_SEEKING=" + pushSeekProp);
        pushBufferSeeking = "true".equalsIgnoreCase(pushSeekProp);

        String delayProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient MEDIA_PLAYER_BUFFER_DELAY=" + delayProp);
        mediaPlayerDelay = 0;
        if (delayProp != null)
        {
          try
          {
            mediaPlayerDelay = Long.parseLong(delayProp);
          }catch (NumberFormatException e)
          {
            if (Sage.DBG) System.out.println("BAD numeric property value of:" + delayProp);
          }
        }

        String remoteFSProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient REMOTE_FS=" + remoteFSProp);
        if (remoteFSProp != null && remoteFSProp.equalsIgnoreCase("true"))
          remoteFSSupport = true;
        else
          remoteFSSupport = false;

        String irProtocol = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient IR_PROTOCOL=" + irProtocol);
        if ("RC5".equals(irProtocol))
          uiMgr.getRouter().setRC5(!uiMgr.getBoolean("rc5_disable", false));

        String subProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient GFX_SUBTITLES=" + subProp);
        subtitleSupport = (subProp != null && subProp.equalsIgnoreCase("true"));

        String reconProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient FORCED_MEDIA_RECONNECT=" + reconProp);
        supportsForcedMediaReconnect = (reconProp != null && reconProp.equalsIgnoreCase("true"));

        String authProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient AUTH_CACHE=" + authProp);
        clientCanDoAuth = (authProp != null && authProp.equalsIgnoreCase("true"));

        String advDeintProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient DEINTERLACE_CONTROL=" + advDeintProp);
        supportsAdvDeinterlacing = (advDeintProp != null && advDeintProp.equalsIgnoreCase("true"));

        String pushProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient PUSH_BUFFER_LIMIT=" + pushProp);
        if (pushProp != null && pushProp.length() > 0)
        {
          try
          {
            pushBufferLimit = Integer.parseInt(pushProp);
          }
          catch (NumberFormatException nfe)
          {
            if (Sage.DBG) System.out.println("ERROR invalid PUSH_BUFFER_LIMIT property of: " + pushProp);
          }
        }

        String ssProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient SCREENSHOT_CAP=" + ssProp);
        screenshotSupport = ssProp != null && ssProp.equalsIgnoreCase("true");

        String zipStat = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient ZLIB_COMM=" + zipStat);
        zipSocks = zipStat != null && "true".equalsIgnoreCase(zipStat);

        String offlineCacheData = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient OFFLINE_CACHE_CONTENTS=" + offlineCacheData);
        if (offlineCacheData != null && hasOfflineCache)
        {
          java.util.StringTokenizer toker = new java.util.StringTokenizer(offlineCacheData, "|");
          while (toker.hasMoreTokens())
          {
            clientOfflineCache.add(toker.nextToken());
          }
        }

        String advCacheProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient ADVANCED_IMAGE_CACHING=" + advCacheProp);
        advImageCaching = advCacheProp != null && "true".equalsIgnoreCase(advCacheProp);

        arProp = recvr.getStringReply();
        if (Sage.DBG) System.out.println("MiniClient VIDEO_ADVANCED_ASPECT=" + arProp);
        if (arProp != null && arProp.length() > 0)
        {
          defaultAdvancedAR = currAdvancedAR = getBarPrefix(arProp);
          currAdvancedARDetails = arProp;
          String replaceAR = uiMgr.get("default_advanced_aspect_ratio", "");
          if (replaceAR.length() > 0)
          {
            defaultAdvancedAR = replaceAR;
            for (int i = 0; i < advancedARNames.length; i++)
            {
              if (advancedARNames[i].equals(replaceAR))
              {
                sendSetProperty("DEFAULT_VIDEO_ADVANCED_ASPECT", advancedARsFull[i]);
                if (Sage.DBG) System.out.println("Using replacement default advanced AR of:" + defaultAdvancedAR);
                break;
              }
            }
          }
        }

        if (advImageCaching)
        {
          if (Sage.DBG) System.out.println("MiniClient sending ADVANCED_IMAGE_CACHING=" + !localConnection);
          sendSetProperty("ADVANCED_IMAGE_CACHING", localConnection ? "FALSE" : "TRUE");
          advImageCaching = !localConnection;
        }

        // Tell the client they can do reconnects if they want to
        if (Sage.DBG) System.out.println("MiniClient sending RECONNECT_SUPPORTED=TRUE");
        sendSetProperty("RECONNECT_SUPPORTED", "TRUE");

        if (zipSocks && textureBatchLimit == 0)
        {
          // All data we send AFTER this property set will need to be ZLIB compressed
          if (Sage.DBG) System.out.println("MiniClient sending ZLIB_COMM_XFER=TRUE");
          sendSetProperty("ZLIB_COMM_XFER", "TRUE");
          zout = new com.jcraft.jzlib.ZOutputStream(clientSocket.socket().getOutputStream(), 5/*halfway between best speed & best compression*/, true);
          zout.setFlushMode(com.jcraft.jzlib.JZlib.Z_SYNC_FLUSH);
        }

        // Tell it what our aspect ratio is (it can ignore sets on properties it doesn't understand)
        if (Sage.DBG) System.out.println("MiniClient sending GFX_ASPECT=" + vf.getDisplayAspectRatio());
        sendSetProperty("GFX_ASPECT", Float.toString(lastSetDAR = vf.getDisplayAspectRatio()));

        remotelyTransferFonts = !useImageMapsForText && "REMOTEFONTS".equalsIgnoreCase(textModeProp) &&
            System.getProperty("java.version").startsWith("1.5");

        if (remotelyTransferFonts)
        {
          int propRV = sendSetProperty("GFX_FONTSERVER", "TRUE");
          if (propRV != 0)
          {
            System.out.println("MiniClient did not accept the font server property, errcode=" + propRV);
            remotelyTransferFonts = false;
          }
        }
        if (subtitleSupport)
        {
          int propRV = sendSetProperty("SUBTITLES_CALLBACKS", "TRUE");
          if (propRV != 0)
          {
            System.out.println("MiniClient did not accept the subtitle callback server property, errcode=" + propRV);
            subtitleSupport = false;
          }
        }

        if (supportsAdvDeinterlacing)
        {
          advDeinterlacingEnabled = uiMgr.getBoolean("advanced_deinterlacing", true);
          sendSetProperty("ADVANCED_DEINTERLACING", advDeinterlacingEnabled ? "TRUE" : "FALSE");
        }
        /**/
        bufferFlipping = false;
        if (uiMgr.areEffectsEnabled())
        {
          // In this case we always render the full UI so we can optimize how we copy that on the other end to speed things up
          int propRV = sendSetProperty("GFX_FLIP", "TRUE");
          if (propRV != 0)
          {
            System.out.println("MiniClient did not accept the GFX_FLIP set property, errcode=" + propRV);
          }
          bufferFlipping = true;
        }

        // NOTE: If the MiniClient connection is not local then we need to authenticate this.
        // We will ONLY authenticate in a secure fashion so the MiniClient MUST support
        // cryptography. We start off with the events being encrypted for safety and then
        // the UI can disable encryption after the secure login has been completed.
        // Local miniclients will be authenticated if that feature is enabled and they support it
        String cryptoAlgosProp = sendGetProperty("CRYPTO_ALGORITHMS");
        if (Sage.DBG) System.out.println("MiniClient CRYPTO_ALGORITHMS=" + cryptoAlgosProp);
        if (localConnection && !Sage.getBoolean("miniclient/authenticate_local_connections", false))
        {
          // No authentication needed for this connection
          cryptoSupport = false;
        }
        else
        {
          if (cryptoAlgosProp == null || (cryptoAlgosProp.toLowerCase().indexOf("blowfish") == -1 && cryptoAlgosProp.toLowerCase().indexOf("des") == -1))
          {
            throw new java.io.IOException("MiniClient does not support Blowfish or DES yet authentication is required for it's connection!");
          }
          if (cryptoAlgosProp.toLowerCase().indexOf("rsa") == -1 && cryptoAlgosProp.toLowerCase().indexOf("dh") == -1)
          {
            throw new java.io.IOException("MiniClient does not support RSA or DiffieHellman yet authentication is required for it's connection!");
          }
          cryptoSupport = true;
          // Verify we have RSA support
          if (hasRSASupport == -1)
          {
            try
            {
              javax.crypto.Cipher.getInstance("RSA");
              hasRSASupport = 1;
            }
            catch (Exception e)
            {
              if (Sage.DBG) System.out.println("RSA support does not exist on the server, do not use it.");
              hasRSASupport = 0;
            }
          }
          if (Sage.DBG) System.out.println("MiniClient connection is setting up the cryptographic event link...");
          if (cryptoAlgosProp.toLowerCase().indexOf("rsa") != -1 && hasRSASupport == 1)
          {
            if (Sage.DBG) System.out.println("Generating RSA public/private keys for SageTV MiniClient connection...");
            try
            {
              if (rsaKeyGen == null)
              {
                rsaKeyGen = java.security.KeyPairGenerator.getInstance("RSA");
                java.security.SecureRandom random = java.security.SecureRandom.getInstance("SHA1PRNG");
                rsaKeyGen.initialize(1024, random);
              }
              java.security.KeyPair pair = rsaKeyGen.generateKeyPair();
              privateKey = pair.getPrivate();
              java.security.PublicKey pub = pair.getPublic();
              encodedPublicKeyBytes = pub.getEncoded();
            }
            catch (Exception e)
            {
              throw new java.io.IOException("ERROR with public/private key gen in cryptography system:" + e);
            }

            int propRV = sendSetProperty("CRYPTO_ALGORITHMS", "RSA,Blowfish");
            if (propRV != 0)
            {
              throw new java.io.IOException("MiniClient did not accept the crypto algorithms, errcode=" + propRV);
            }
            if (Sage.DBG) System.out.println("Sending the RSA public key to the MiniClient...");
            propRV = sendSetProperty("CRYPTO_PUBLIC_KEY".getBytes(Sage.BYTE_CHARSET), encodedPublicKeyBytes);
            if (propRV != 0)
            {
              throw new java.io.IOException("MiniClient did not accept the public key, errcode=" + propRV);
            }
            if (Sage.DBG) System.out.println("Retrieving the encrypted secret key from the MiniClient...");
            byte[] secretKeyCryptoBytes = sendGetRawProperty("CRYPTO_SYMMETRIC_KEY");
            if (secretKeyCryptoBytes == null || secretKeyCryptoBytes.length == 0)
            {
              throw new java.io.IOException("MiniClient did not provide the secret key, errcode=" + propRV);
            }
            if (Sage.DBG) System.out.println("Decrypting the secret key from the MiniClient to establish secure link...");
            try
            {
              javax.crypto.Cipher decryptCipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
              decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, privateKey);
              byte[] secretBytes = decryptCipher.doFinal(secretKeyCryptoBytes);
              if (Sage.DBG) System.out.println("Secret key for MiniClient link has been decrypted, finalizing crypto engine...");
              mySecretKey = new javax.crypto.spec.SecretKeySpec(secretBytes, "Blowfish");
              evtDecryptCipher = javax.crypto.Cipher.getInstance("Blowfish");
              evtDecryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, mySecretKey);
            }
            catch (Exception e)
            {
              throw new java.io.IOException("Error establishing secure MiniClient link:" + e);
            }
          }
          else
          {
            // Do a DiffieHellman key agreement and then generate a shared secret
            javax.crypto.spec.DHParameterSpec dhSkipParamSpec;
            try
            {
              java.security.AlgorithmParameterGenerator paramGen = java.security.AlgorithmParameterGenerator.getInstance("DH");
              paramGen.init(512);
              java.security.AlgorithmParameters params = paramGen.generateParameters();
              dhSkipParamSpec = params.getParameterSpec(javax.crypto.spec.DHParameterSpec.class);
            }
            catch (Exception e)
            {
              throw new java.io.IOException("Crypto error with DH key param generation:" + e);
            }
            /*
             * Alice creates her own DH key pair, using the DH parameters from
             * above
             */
            if (Sage.DBG) System.out.println("Generate DH keypair ...");
            if (dhKeyGen == null)
            {
              try
              {
                dhKeyGen = java.security.KeyPairGenerator.getInstance("DH");
                dhKeyGen.initialize(dhSkipParamSpec);
              }
              catch (Exception e)
              {
                throw new java.io.IOException("Error initializing DH key pair gen of:" + e);
              }
            }
            java.security.KeyPair aliceKpair = dhKeyGen.generateKeyPair();

            // Alice creates and initializes her DH KeyAgreement object
            javax.crypto.KeyAgreement aliceKeyAgree;
            try
            {
              aliceKeyAgree = javax.crypto.KeyAgreement.getInstance("DH");
              aliceKeyAgree.init(aliceKpair.getPrivate());
            }
            catch (Exception e)
            {
              throw new java.io.IOException("Error initializing DH key pair gen of:" + e);
            }

            int propRV = sendSetProperty("CRYPTO_ALGORITHMS", "DH,DES");
            if (propRV != 0)
            {
              throw new java.io.IOException("MiniClient did not accept the crypto algorithms, errcode=" + propRV);
            }

            // Alice encodes her public key, and sends it over to Bob.
            encodedPublicKeyBytes = aliceKpair.getPublic().getEncoded();
            if (Sage.DBG) System.out.println("Sending the DH public key to the MiniClient...");
            propRV = sendSetProperty("CRYPTO_PUBLIC_KEY".getBytes(Sage.BYTE_CHARSET), encodedPublicKeyBytes);
            if (propRV != 0)
            {
              throw new java.io.IOException("MiniClient did not accept the public key, errcode=" + propRV);
            }
            if (Sage.DBG) System.out.println("Completing the DH key agreement with the MiniClient...");
            byte[] bobPubKeyEnc = sendGetRawProperty("CRYPTO_SYMMETRIC_KEY");
            if (bobPubKeyEnc == null || bobPubKeyEnc.length == 0)
            {
              throw new java.io.IOException("MiniClient did not provide the secret key, errcode=" + propRV);
            }
            try
            {
              java.security.KeyFactory aliceKeyFac = java.security.KeyFactory.getInstance("DH");
              java.security.spec.X509EncodedKeySpec x509KeySpec = new java.security.spec.X509EncodedKeySpec(bobPubKeyEnc);
              java.security.PublicKey bobPubKey = aliceKeyFac.generatePublic(x509KeySpec);
              aliceKeyAgree.doPhase(bobPubKey, true);
            }
            catch (Exception e)
            {
              throw new java.io.IOException("Error finishing DH key agreement of:" + e);
            }

            // Now we can generate the shared secret
            try
            {
              mySecretKey = aliceKeyAgree.generateSecret("DES");
              evtDecryptCipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding");
              evtDecryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, mySecretKey);
            }
            catch (Exception e)
            {
              throw new java.io.IOException("Error establishing secure MiniClient link:" + e);
            }
          }
          if (Sage.DBG) System.out.println("MiniClient crypto engine has been established, turning on event encryption...");
          recvr.nextReplyIsCryptoStatus(true);
          int propRV = sendSetProperty("CRYPTO_EVENTS_ENABLE", "TRUE");
          if (propRV != 0)
          {
            throw new java.io.IOException("MiniClient did not succeed with enabling event encryption, errcode=" + propRV);
          }
          if (Sage.DBG) System.out.println("MiniClient event encryption has been successfully established!");

          // Now attempt to do the automatic authentication process if we can
          if (canCacheAuthentication()) // this checks server & client support
          {
            if (Sage.DBG) System.out.println("Server & client support auth caching....");
            String myAuth = Sage.get("miniclient/cached_authentication/" + clientName, null);
            if (myAuth != null && myAuth.length() > 0)
            {
              if (Sage.DBG) System.out.println("Server has an auth block stored for this client...");
              // Now get the auth block from the client and see if it matches what we saved
              String clientAuth = sendGetProperty("GET_CACHED_AUTH");
              if (clientAuth != null && clientAuth.length() > 0)
              {
                if (Sage.DBG) System.out.println("Client sent an auth block back to the server...check it...");
                if (SageTV.oneWayEncrypt(clientAuth).equals(myAuth))
                {
                  if (Sage.DBG) System.out.println("Cached authentication was verified!");
                  // Now we disable encryption on this channel and indicate we don't need authentication so that
                  // we load the regular STV instead of SecureLogin.xml
                  setEventEncryption(false);
                  cryptoSupport = false;

                  // We also need to determine what the security context should be for this connection since we have now
                  // bypassed the login mechanism for the placeshifter.

                  // First step is to find all of the active placeshifter user names
                  java.util.Set activeUsers = new java.util.HashSet();
                  String userList = Sage.get("miniclient/users", "");
                  java.util.StringTokenizer toker = new java.util.StringTokenizer(userList, ";");
                  while (toker.hasMoreTokens())
                  {
                    String toke = toker.nextToken();
                    int commaIdx = toke.indexOf(',');
                    if (commaIdx != -1)
                    {
                      // valid data
                      if ("true".equalsIgnoreCase(toke.substring(commaIdx + 1)))
                        activeUsers.add(toke.substring(0, commaIdx));
                    }
                  }

                  // Now walk through the list of active users and find the one that has a cached_auth property which contains the ID of
                  // the client we just let authenticate
                  java.util.Iterator walker = activeUsers.iterator();
                  while (walker.hasNext())
                  {
                    String currUser = walker.next().toString();
                    if (Sage.get("miniclient/" + currUser + "/cached_auth", "").indexOf(clientName) != -1)
                    {
                      if (Sage.DBG) System.out.println("Associated automatic placeshifter login to be from user: " + currUser);
                      String targetProfile = Sage.get("miniclient/" + currUser + "/security_profile", Permissions.getDefaultSecurityProfile());
                      if (Sage.DBG) System.out.println("Setting security profile based on automatic user login to be: " + targetProfile);
                      uiMgr.setActiveSecurityProfile(targetProfile);
                      break;
                    }
                  }

                  // If we didn't find a matching username then the last configured profile for that client would be used
                }
                else
                {
                  if (Sage.DBG) System.out.println("Cached authentication failed; use normal login");
                }
              }
            }
          }
        }
      }
      recvr.resetThreadChecker();
      if (rv)
      {
        sage.plugin.PluginEventManager.getInstance().postEvent(sage.plugin.PluginEventManager.CLIENT_CONNECTED,
            new Object[] { sage.plugin.PluginEventManager.VAR_IPADDRESS, clientSocket.socket().getInetAddress().getHostAddress(),
            sage.plugin.PluginEventManager.VAR_MACADDRESS, uiMgr.getLocalUIClientName()});
      }
      return rv;
    }
    catch (java.io.IOException e)
    {
      if (Sage.DBG) System.out.println("Error w/ MiniUI socket of:" + e);
      cleanupRenderer();
      //return false;
      throw new RuntimeException("Error creating MiniClient of:" + e);
    }
  }

  private synchronized void setupDigitalResolutions(boolean sendRequest) throws java.io.IOException
  {
    String rezSupportProp;
    String requestProp = "GFX_SUPPORTED_RESOLUTIONS_DIGITAL";
    if (sendRequest)
    {
      hdmiAutodetectedConnector = sendGetProperty("GFX_HDMI_MODE");
      if (Sage.DBG) System.out.println("MiniClient GFX_HDMI_MODE=" + hdmiAutodetectedConnector);
      requestProp = ("None".equalsIgnoreCase(hdmiAutodetectedConnector) ? "GFX_SUPPORTED_RESOLUTIONS" : "GFX_SUPPORTED_RESOLUTIONS_DIGITAL");
      rezSupportProp = sendGetProperty(requestProp);
      if (rezSupportProp == null || rezSupportProp.length() == 0)
      {
        hdmiAutodetectedConnector = "None";
        requestProp = ("None".equalsIgnoreCase(hdmiAutodetectedConnector) ? "GFX_SUPPORTED_RESOLUTIONS" : "GFX_SUPPORTED_RESOLUTIONS_DIGITAL");
        rezSupportProp = sendGetProperty(requestProp);
      }
    }
    else
    {
      rezSupportProp = recvr.getStringReply();
    }
    if (Sage.DBG) System.out.println("MiniClient " + requestProp + "=" + rezSupportProp);
    if (rezSupportProp != null && rezSupportProp.length() > 0 && !remoteWindowedSystem)
    {
      java.util.StringTokenizer toker = new java.util.StringTokenizer(rezSupportProp, ";");
      java.util.ArrayList prefResNames = new java.util.ArrayList();
      while (toker.hasMoreTokens())
      {
        String currToke = toker.nextToken();
        String currName = getBarPrefix(currToke);
        if (resolutionNameMap.containsKey(currName))
        {
          prefResNames.add(currName);
          resolutionNameMap.put(currName, currToke);
        }
      }
      preferredResolutions = (String[]) prefResNames.toArray(Pooler.EMPTY_STRING_ARRAY);
      java.util.Arrays.sort(preferredResolutions, new java.util.Comparator()
      {
        public int compare(Object o1, Object o2)
        {
          String s1 = (String) o1;
          String s2 = (String) o2;
          int h1 = parseResolution(s1).height;
          int h2 = parseResolution(s2).height;
          if (h1 != h2)
            return h1 - h2;
          boolean p1 = s1.indexOf('p') != -1;
          boolean p2 = s2.indexOf('p') != -1;
          if (p1 == p2)
            return 0;
          else if (p1)
            return 1;
          else
            return -1;
        }
      });
    }
  }
  protected synchronized boolean compositeSurfaces(Object targetSurface, Object srcSurface, float alphaFactor, java.awt.geom.Rectangle2D region)
  {
    Long target = (Long) targetSurface;
    Long src = (Long) srcSurface;
    if (src != null && target != null)
    {
      long lastSurface = currSurface;
      int x = (int)(region.getX() + 0.5f);
      int y = (int)(region.getY() + 0.5f);
      int w = (int)(region.getWidth() + 0.5f);
      int h = (int)(region.getHeight() + 0.5f);
      setTargetSurfaceMini(target.longValue());
      // Since it's the same dimensions just use nearest neighbor scaling
      drawTexturedRectMini(x, y, w, h, src.longValue(), x, y, w, h, getCompositedColor(0xFFFFFFFF, alphaFactor));
      setTargetSurfaceMini(lastSurface);
      return true;
    }
    return false;
  }

  protected synchronized void performHiResComposite(long hiResImage, Object targetSurface, RenderingOp op2)
  {
    long lastSurface = currSurface;
    setTargetSurfaceMini(((Long) targetSurface).longValue());
    java.awt.geom.Rectangle2D.Float realSrc = (java.awt.geom.Rectangle2D.Float) op2.privateData;
    drawTexturedRectMini((int)(op2.destRect.x + 0.5f), (int)(op2.destRect.y + 0.5f),
        (int)(op2.destRect.width + 0.5f), (int)(op2.destRect.height + 0.5f),
        hiResImage,
        (int)(realSrc.x + 0.5f), (int)(realSrc.y + 0.5f), (int)(realSrc.width + 0.5f),
        (int)(realSrc.height + 0.5f),
        getCompositedColor(0xFFFFFFFF, op2.alphaFactor));
    setTargetSurfaceMini(lastSurface);
  }

  protected synchronized void deinitMini()
  {
    if (Sage.DBG) System.out.println("deinitMini()");
    if (recvr != null)
      recvr.close();
    if (clientSocket != null)
    {
      sage.plugin.PluginEventManager.getInstance().postEvent(sage.plugin.PluginEventManager.CLIENT_DISCONNECTED,
          new Object[] { sage.plugin.PluginEventManager.VAR_IPADDRESS, clientSocket.socket().getInetAddress().getHostAddress(),
          sage.plugin.PluginEventManager.VAR_MACADDRESS, uiMgr.getLocalUIClientName()});
      try { clientSocket.close(); } catch(Exception e){}
      clientSocket = null;
    }
  }

  protected synchronized void drawRectMini(int x, int y,
      int width, int height, int thickness,
      int argbTL, int argbTR, int argbBR, int argbBL)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("drawRectMini("+x+","+y+","+width+","+height+")");
    try
    {
      prepUICmdHeader(40);
      sockBuf.putInt(GFXCMD_DRAWRECT<<CMD_LEN_BITS | 36 );
      sockBuf.putInt(x);
      sockBuf.putInt(y);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      sockBuf.putInt(thickness);
      sockBuf.putInt(argbTL);
      sockBuf.putInt(argbTR);
      sockBuf.putInt(argbBR);
      sockBuf.putInt(argbBL);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error1:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void fillRectMini(int x, int y,
      int width, int height,
      int argbTL, int argbTR, int argbBR, int argbBL)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("fillRectMini("+x+","+y+","+width+","+height+")");
    if (width <= 0 || height <= 0) return;
    try
    {
      prepUICmdHeader(36);
      sockBuf.putInt(GFXCMD_FILLRECT<<CMD_LEN_BITS | 32 );
      sockBuf.putInt(x);
      sockBuf.putInt(y);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      sockBuf.putInt(argbTL);
      sockBuf.putInt(argbTR);
      sockBuf.putInt(argbBR);
      sockBuf.putInt(argbBL);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error2:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void clearRectMini(int x, int y,
      int width, int height,
      int argbTL, int argbTR, int argbBR, int argbBL, java.awt.Rectangle clipRect)
  {
    width = Math.min(x + width, clipRect.x + clipRect.width) - Math.max(x, clipRect.x);
    height = Math.min(y + height, clipRect.y + clipRect.height) - Math.max(y, clipRect.y);
    x = Math.max(x, clipRect.x);
    y = Math.max(y, clipRect.y);
    if (width <= 0 || height <= 0)
      return;
    clearRectMini(x, y, width, height, argbTL, argbTR, argbBR, argbBL);
  }
  protected synchronized void clearRectMini(int x, int y,
      int width, int height,
      int argbTL, int argbTR, int argbBR, int argbBL)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("clearRectMini("+x+","+y+","+width+","+height+")");
    try
    {
      prepUICmdHeader(36);
      sockBuf.putInt(GFXCMD_CLEARRECT<<CMD_LEN_BITS | 32 );
      sockBuf.putInt(x);
      sockBuf.putInt(y);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      sockBuf.putInt(argbTL);
      sockBuf.putInt(argbTR);
      sockBuf.putInt(argbBR);
      sockBuf.putInt(argbBL);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error3:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void drawOvalMini(int x, int y,
      int width, int height, int thickness,
      int argbTL, int argbTR, int argbBR, int argbBL,
      int clipX, int clipY, int clipW, int clipH)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("drawOvalMini("+x+","+y+","+width+","+height+")");
    try
    {
      prepUICmdHeader(56);
      sockBuf.putInt(GFXCMD_DRAWOVAL<<CMD_LEN_BITS | 52 );
      sockBuf.putInt(x);
      sockBuf.putInt(y);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      sockBuf.putInt(thickness);
      sockBuf.putInt(argbTL);
      sockBuf.putInt(argbTR);
      sockBuf.putInt(argbBR);
      sockBuf.putInt(argbBL);
      sockBuf.putInt(clipX);
      sockBuf.putInt(clipY);
      sockBuf.putInt(clipW);
      sockBuf.putInt(clipH);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error4:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void fillOvalMini(int x, int y,
      int width, int height,
      int argbTL, int argbTR, int argbBR, int argbBL,
      int clipX, int clipY, int clipW, int clipH)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("fillOvalMini("+x+","+y+","+width+","+height+")");
    try
    {
      prepUICmdHeader(52);
      sockBuf.putInt(GFXCMD_FILLOVAL<<CMD_LEN_BITS | 48 );
      sockBuf.putInt(x);
      sockBuf.putInt(y);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      sockBuf.putInt(argbTL);
      sockBuf.putInt(argbTR);
      sockBuf.putInt(argbBR);
      sockBuf.putInt(argbBL);
      sockBuf.putInt(clipX);
      sockBuf.putInt(clipY);
      sockBuf.putInt(clipW);
      sockBuf.putInt(clipH);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error5:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void drawRoundRectMini(int x, int y,
      int width, int height,
      int thickness, int arcRadius,
      int argbTL, int argbTR, int argbBR, int argbBL,
      int clipX, int clipY, int clipW, int clipH)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("drawRoundRectMini("+x+","+y+","+width+","+height+") clip=("+clipX+","+clipY+","+clipW+","+clipH+")");
    try
    {
      prepUICmdHeader(60);
      sockBuf.putInt(GFXCMD_DRAWROUNDRECT<<CMD_LEN_BITS | 56 );
      sockBuf.putInt(x);
      sockBuf.putInt(y);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      sockBuf.putInt(thickness);
      sockBuf.putInt(arcRadius/2);
      sockBuf.putInt(argbTL);
      sockBuf.putInt(argbTR);
      sockBuf.putInt(argbBR);
      sockBuf.putInt(argbBL);
      sockBuf.putInt(clipX);
      sockBuf.putInt(clipY);
      sockBuf.putInt(clipW);
      sockBuf.putInt(clipH);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error6:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void fillRoundRectMini(int x, int y,
      int width, int height, int arcRadius,
      int argbTL, int argbTR, int argbBR, int argbBL,
      int clipX, int clipY, int clipW, int clipH)

  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("fillRoundRectMini("+x+","+y+","+width+","+height+") clip=("+clipX+","+clipY+","+clipW+","+clipH+")");
    try
    {
      prepUICmdHeader(56);
      sockBuf.putInt(GFXCMD_FILLROUNDRECT<<CMD_LEN_BITS | 52 );
      sockBuf.putInt(x);
      sockBuf.putInt(y);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      sockBuf.putInt(arcRadius/2);
      sockBuf.putInt(argbTL);
      sockBuf.putInt(argbTR);
      sockBuf.putInt(argbBR);
      sockBuf.putInt(argbBL);
      sockBuf.putInt(clipX);
      sockBuf.putInt(clipY);
      sockBuf.putInt(clipW);
      sockBuf.putInt(clipH);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error7:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void drawTextMini(int x, int y,
      String text, long fontHandle,
      int argb, int clipX, int clipY, int clipW, int clipH)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("drawTextMini()");
    try
    {
      prepUICmdHeader(40+text.length()*2);
      sockBuf.putInt(GFXCMD_DRAWTEXT<<CMD_LEN_BITS |
          36+text.length()*2);
      sockBuf.putInt(x);
      sockBuf.putInt(y);
      sockBuf.putInt(text.length());
      sockBuf.put(text.getBytes("UTF-16BE"));
      sockBuf.putInt((int)fontHandle);
      sockBuf.putInt(argb);
      sockBuf.putInt(clipX);
      sockBuf.putInt(clipY);
      sockBuf.putInt(clipW);
      sockBuf.putInt(clipH);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error8:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }

  }

  private java.awt.Rectangle tempdr = new java.awt.Rectangle();
  private java.awt.Rectangle tempsr = new java.awt.Rectangle();
  protected synchronized void drawTexturedRectMini(int x, int y,
      int width, int height, long handle,
      int srcx, int srcy, int srcwidth, int srcheight, int blend)
  {
    drawTexturedRectMini(x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend, true);
  }
  protected synchronized void drawTexturedRectMini(int x, int y,
      int width, int height, long handle,
      int srcx, int srcy, int srcwidth, int srcheight, int blend, boolean obeyClipAndXform)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("drawTexturedRectMini src=(" + srcx +","+srcy+","+srcwidth+","+srcheight+
        ")" + " dest=("+x+","+y+","+width+","+height+")");
    // height can be negative for SRC blending, and width can be negative for text blending
    if (width == 0 || height == 0 || srcwidth <= 0 || srcheight <= 0) return;
    if (obeyClipAndXform && (currEffectClip != null || currXform != null))
    {
      tempdr.setBounds(x, y, Math.abs(width), Math.abs(height));
      if (currXform != null && !xformRenderSupport)
        MathUtils.transformRectCoords(tempdr, currXform, tempdr);
      if (currEffectClip != null)
      {
        tempsr.setBounds(srcx, srcy, srcwidth, srcheight);
        Sage.clipSrcDestRects(currEffectClip, tempsr, tempdr);
        srcx = tempsr.x;
        srcy = tempsr.y;
        srcwidth = tempsr.width;
        srcheight = tempsr.height;
      }
      x = tempdr.x;
      y = tempdr.y;
      width = width < 0 ? -tempdr.width : tempdr.width;
      height = height < 0 ? -tempdr.height : tempdr.height;
      if (tempdr.width <= 0 || tempdr.height <= 0)
        return;
      if (currXform != null && !xformRenderSupport)
      {
        if ((gfxScalingCaps & GFX_SCALING_FLIPH) != 0 && MathUtils.getScaleX(currXform) < 0)
          srcwidth *= -1;
        if ((gfxScalingCaps & GFX_SCALING_FLIPV) != 0 && MathUtils.getScaleY(currXform) < 0)
          srcheight *= -1;
      }
      if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("T+C drawTexturedRectMini src=(" + srcx +","+srcy+","+srcwidth+","+srcheight+
          ")" + " dest=("+x+","+y+","+width+","+height+")");
    }
    lastPixelRenderCount += Math.abs(width * height);
    lastPixelInputCount += Math.abs(srcwidth * srcheight);
    try
    {
      int maxPixelsW = 100; // at 125 and higher we had corruption in HDMI
      int maxPixelsH = 100; // at 125 and higher we had corruption in HDMI
      if (false && highResSurfCaps == YUV_CACHE_UNIFIED && lastUsedHighResSurf != 0 && (srcheight >= maxPixelsH || srcwidth >= maxPixelsW) && width > 0)
      {
        int scalex = (srcwidth / maxPixelsW) + 1;
        int scaley = (srcheight / maxPixelsH) + 1;
        int heightMult = (height < 0) ? -1 : 1;
        height = height * heightMult;
        for (int i = 0; i < scalex; i++)
        {
          for (int j = 0; j < scaley; j++)
          {
            prepUICmdHeader(44, true);
            sockBuf.putInt(GFXCMD_DRAWTEXTURED<<CMD_LEN_BITS | 40);
            sockBuf.putInt(x + (i * width/scalex));
            sockBuf.putInt(y + (j * height/scaley));
            sockBuf.putInt((i > 0) ? ((i + 1) * width/scalex) - (i * width/scalex) :
              (width/scalex));
            sockBuf.putInt(((j > 0) ? ((j + 1) * height/scaley) - (j * height/scaley) :
              (height/scaley)) * heightMult);
            sockBuf.putInt((int)handle);
            sockBuf.putInt(srcx + (i * srcwidth/scalex));
            sockBuf.putInt(srcy + (j * srcheight/scaley));
            sockBuf.putInt((i > 0) ? ((i + 1) * srcwidth/scalex) - (i * srcwidth/scalex) :
              (srcwidth/scalex));
            sockBuf.putInt((j > 0) ? ((j + 1) * srcheight/scaley) - (j * srcheight/scaley) :
              (srcheight/scaley));
            sockBuf.putInt(blend);
          }
        }
      }
      else
      {
        prepUICmdHeader(44, width * height <= 100000);
        sockBuf.putInt(GFXCMD_DRAWTEXTURED<<CMD_LEN_BITS | 40);
        sockBuf.putInt(x);
        sockBuf.putInt(y);
        sockBuf.putInt(width);
        sockBuf.putInt(height);
        sockBuf.putInt((int)handle);
        sockBuf.putInt(srcx);
        sockBuf.putInt(srcy);
        sockBuf.putInt(srcwidth);
        sockBuf.putInt(srcheight);
        sockBuf.putInt(blend);
      }
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error9:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void drawTexturedDiffusedRectMini(int x, int y,
      int width, int height, long handle,
      int srcx, int srcy, int srcwidth, int srcheight, int blend, long diffhandle,
      int diffsrcx, int diffsrcy, int diffsrcwidth, int diffsrcheight)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("drawTexturedDiffusedRectMini src=(" + srcx +","+srcy+","+srcwidth+","+srcheight+
        ")" + " dest=("+x+","+y+","+width+","+height+")");
    if (width <= 0 || height <= 0 || srcwidth <= 0 || srcheight <= 0) return;
    if (currEffectClip != null || currXform != null)
    {
      tempdr.setBounds(x, y, width, height);
      if (currXform != null && !xformRenderSupport)
        MathUtils.transformRectCoords(tempdr, currXform, tempdr);
      if (currEffectClip != null)
      {
        tempsr.setBounds(srcx, srcy, srcwidth, srcheight);
        java.awt.Rectangle sr2 = new java.awt.Rectangle(diffsrcx, diffsrcy, diffsrcwidth, diffsrcheight);
        Sage.clipSrcDestRects(currEffectClip, sr2, (java.awt.Rectangle)tempdr.clone());
        Sage.clipSrcDestRects(currEffectClip, tempsr, tempdr);
        srcx = tempsr.x;
        srcy = tempsr.y;
        srcwidth = tempsr.width;
        srcheight = tempsr.height;
        diffsrcx = sr2.x;
        diffsrcy = sr2.y;
        diffsrcwidth = sr2.width;
        diffsrcheight = sr2.height;
      }
      x = tempdr.x;
      y = tempdr.y;
      width = tempdr.width;
      height = tempdr.height;
      if (tempdr.width <= 0 || tempdr.height <= 0)
        return;
      if (currXform != null && !xformRenderSupport)
      {
        if ((gfxScalingCaps & GFX_SCALING_FLIPH) != 0 && MathUtils.getScaleX(currXform) < 0)
          srcwidth *= -1;
        if ((gfxScalingCaps & GFX_SCALING_FLIPV) != 0 && MathUtils.getScaleY(currXform) < 0)
          srcheight *= -1;
      }
      if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("T+C drawTexturedDiffusedRectMini src=(" + srcx +","+srcy+","+srcwidth+","+srcheight+
          ")" + " dest=("+x+","+y+","+width+","+height+")");
    }
    lastPixelRenderCount += Math.abs(width * height);
    lastPixelInputCount += Math.abs(srcwidth * srcheight);
    try
    {
      prepUICmdHeader(64, width * height <= 100000);
      sockBuf.putInt(GFXCMD_DRAWTEXTUREDDIFFUSE<<CMD_LEN_BITS | 60);
      sockBuf.putInt(x);
      sockBuf.putInt(y);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      sockBuf.putInt((int)handle);
      sockBuf.putInt(srcx);
      sockBuf.putInt(srcy);
      sockBuf.putInt(srcwidth);
      sockBuf.putInt(srcheight);
      sockBuf.putInt(blend);
      sockBuf.putInt((int)diffhandle);
      sockBuf.putInt(diffsrcx);
      sockBuf.putInt(diffsrcy);
      sockBuf.putInt(diffsrcwidth);
      sockBuf.putInt(diffsrcheight);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error9:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void pushTransform(javax.vecmath.Matrix4f xform)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("pushTransform()");
    try
    {
      prepUICmdHeader(68);
      sockBuf.putInt(GFXCMD_PUSHTRANSFORM<<CMD_LEN_BITS | 64);
      if (xform == null)
      {
        sockBuf.putFloat(1);
        sockBuf.putFloat(0);
        sockBuf.putFloat(0);
        sockBuf.putFloat(0);
        sockBuf.putFloat(0);
        sockBuf.putFloat(1);
        sockBuf.putFloat(0);
        sockBuf.putFloat(0);
        sockBuf.putFloat(0);
        sockBuf.putFloat(0);
        sockBuf.putFloat(1);
        sockBuf.putFloat(0);
        sockBuf.putFloat(0);
        sockBuf.putFloat(0);
        sockBuf.putFloat(0);
        sockBuf.putFloat(1);
      }
      else
      {
        sockBuf.putFloat(xform.m00);
        sockBuf.putFloat(xform.m01);
        sockBuf.putFloat(xform.m02);
        sockBuf.putFloat(xform.m03);
        sockBuf.putFloat(xform.m10);
        sockBuf.putFloat(xform.m11);
        sockBuf.putFloat(xform.m12);
        sockBuf.putFloat(xform.m13);
        sockBuf.putFloat(xform.m20);
        sockBuf.putFloat(xform.m21);
        sockBuf.putFloat(xform.m22);
        sockBuf.putFloat(xform.m23);
        sockBuf.putFloat(xform.m30);
        sockBuf.putFloat(xform.m31);
        sockBuf.putFloat(xform.m32);
        sockBuf.putFloat(xform.m33);
      }
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error10b:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void popTransform()
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("popTransform()");
    try
    {
      prepUICmdHeader(4);
      sockBuf.putInt(GFXCMD_POPTRANSFORM<<CMD_LEN_BITS | 0);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error10b:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void drawLineMini(int x1, int y1,
      int x2, int y2, int argb1, int argb2)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("drawLineMini()");
    try
    {
      prepUICmdHeader(28);
      sockBuf.putInt(GFXCMD_DRAWLINE<<CMD_LEN_BITS | 24);
      sockBuf.putInt(x1);
      sockBuf.putInt(y1);
      sockBuf.putInt(x2);
      sockBuf.putInt(y2);
      sockBuf.putInt(argb1);
      sockBuf.putInt(argb2);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error10:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  // format - 1 for 8bpp font images, 256 for high resolution video display surfaces
  protected synchronized long loadImageMini(int width, int height, int imageFormat)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("loadImageMini(" + width + ", " + height + ")");
    try
    {
      prepUICmdHeader((imageFormat > 0) ? 16 : 12);
      sockBuf.putInt(GFXCMD_LOADIMAGE<<CMD_LEN_BITS | ((imageFormat > 0) ? 12 : 8));
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      if (imageFormat > 0)
        sockBuf.putInt(imageFormat);
      sendBufferNow();
      long rv = recvr.getIntReply();
      return rv;
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error11:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return -1;  // if we return 0 then it'll stick it in an infinite loop
  }

  // format - 1 for 8bpp font images, 256 for high resolution video display surfaces
  protected synchronized void loadImageTargetedMini(int handle, int width, int height, int imageFormat)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("loadImageTargetedMini(" + handle + ", " + width + ", " + height + ")");
    try
    {
      prepUICmdHeader((imageFormat > 0) ? 20 : 16);
      sockBuf.putInt(GFXCMD_LOADIMAGETARGETED<<CMD_LEN_BITS | ((imageFormat > 0) ? 16 : 12));
      sockBuf.putInt(handle);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      if (imageFormat > 0)
        sockBuf.putInt(imageFormat);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error11b:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized long createSurfaceMini(int width, int height)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("createSurfaceMini(" + width + "," + height + ")");
    try
    {
      prepUICmdHeader(12);
      sockBuf.putInt(GFXCMD_CREATESURFACE<<CMD_LEN_BITS | 8);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      sendBufferNow();
      return recvr.getIntReply();
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error12:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return -1;  // if we return 0 then it'll stick it in an infinite loop
  }

  protected synchronized long prepImageMini(int width, int height, String cacheID)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("prepImageMini(" + width + ", " + height + ", " + cacheID + ")");
    try
    {
      int mySize = 12;
      if (hasOfflineCache && cacheID != null && cacheID.length() > 0)
        mySize += 4 + cacheID.length() + 1;
      prepUICmdHeader(mySize);
      sockBuf.putInt(GFXCMD_PREPIMAGE<<CMD_LEN_BITS | (mySize-4));
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      if (hasOfflineCache && cacheID != null && cacheID.length() > 0)
      {
        sockBuf.putInt(cacheID.length()+1);
        sockBuf.put(cacheID.getBytes(Sage.BYTE_CHARSET));
        sockBuf.put((byte)0);
      }
      sendBufferNow();
      return recvr.getIntReply();
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error13:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return -1;  // if we return 0 then it'll stick it in an infinite loop
  }

  protected synchronized void prepImageTargetedMini(int handle, int width, int height, String cacheID)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("prepImageTargetedMini(" + handle + ", " + width + ", " + height + ", "  + cacheID + ")");
    try
    {
      int mySize = 16;
      if (hasOfflineCache && cacheID != null && cacheID.length() > 0)
        mySize += 4 + cacheID.length() + 1;
      prepUICmdHeader(mySize);
      sockBuf.putInt(GFXCMD_PREPIMAGETARGETED<<CMD_LEN_BITS | (mySize-4));
      sockBuf.putInt(handle);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      if (hasOfflineCache && cacheID != null && cacheID.length() > 0)
      {
        sockBuf.putInt(cacheID.length()+1);
        sockBuf.put(cacheID.getBytes(Sage.BYTE_CHARSET));
        sockBuf.put((byte)0);
      }
      sendBufferNow();
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error13c:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void loadCachedImagMini(String cacheID, int handle, int width, int height)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("loadCachedImagMini(" + cacheID + ", " + handle + ", " + width + ", " + height + ")");
    try
    {
      int mySize = 21 + cacheID.length();
      prepUICmdHeader(mySize);
      sockBuf.putInt(GFXCMD_LOADCACHEDIMAGE<<CMD_LEN_BITS | (mySize-4));
      sockBuf.putInt(handle);
      sockBuf.putInt(width);
      sockBuf.putInt(height);
      sockBuf.putInt(cacheID.length()+1);
      sockBuf.put(cacheID.getBytes(Sage.BYTE_CHARSET));
      sockBuf.put((byte)0);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error13b:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized boolean loadImageLineMini(long handle,
      int line, int len, int pos,  int []data)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("loadImageLineMini(" + handle + ", " + line + ", " + len + ", " + pos + ")");
    try
    {
      //			long t1 = Sage.time();
      prepUICmdHeader(16+len*4);
      sockBuf.putInt(GFXCMD_LOADIMAGELINE<<CMD_LEN_BITS | 12+len*4);
      sockBuf.putInt((int)handle);
      sockBuf.putInt(line);
      sockBuf.putInt(len*4);
      int count=0;
      while(count<len)
      {
        sockBuf.putInt(data[pos+count]);
        count+=1;
      }
      /*            clientOutStream.flush();
			long t2 = Sage.time();
			if (data.length > 512 && t2 > t1)
			{
				estimatedBandwidthBytes += data.length + 16;
				estimatedBandwidthTime += t2 - t1;
			}*/
      return true;
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error14:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
      return false;
    }
  }

  protected synchronized boolean loadImageLineMini(long handle,
      int line, int len, int pos,  byte []data)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("loadImageLineMini(" + handle + ", " + line + ", " + len + ", " + pos + ")");
    try
    {
      //			long t1 = Sage.time();
      prepUICmdHeader(16+len);
      sockBuf.putInt(GFXCMD_LOADIMAGELINE<<CMD_LEN_BITS | 12+len);
      sockBuf.putInt((int)handle);
      sockBuf.putInt(line);
      sockBuf.putInt(len);
      sockBuf.put(data, pos, len);
      /*            clientOutStream.flush();
			long t2 = Sage.time();
			if (data.length > 512 && t2 > t1)
			{
				estimatedBandwidthBytes += data.length + 16;
				estimatedBandwidthTime += t2 - t1;
			}*/
      return true;
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error15:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
      return false;
    }
  }

  protected synchronized int loadImageCompressedMini(long handle, byte[] data)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("loadImageCompressedMini(" + handle + ")");
    if (data.length + 12 > MAX_CMD_LEN)
    {
      if (Sage.DBG) System.out.println("Skipping loading image because its source is larger than 16MB");
      return 0;
    }
    try
    {
      //			long t1 = Sage.time();
      prepUICmdHeader(12+data.length);
      sockBuf.putInt(GFXCMD_LOADIMAGECOMPRESSED<<CMD_LEN_BITS | 8+data.length);
      sockBuf.putInt((int)handle);
      sockBuf.putInt(data.length);
      int dataLen = data.length;
      while (dataLen > 0)
      {
        int len = Math.min(dataLen, sockBuf.remaining());
        sockBuf.put(data, data.length - dataLen, len);
        dataLen -= len;
        if (dataLen > 0 || !advImageCaching)
          sendBufferNow();
      }

      if (advImageCaching)
        return 0; // return value is not sent in this mode
      int rv = recvr.getIntReply();
      // Don't use this for BW calc because the client will decompress before this returns so the result is bad for timing
      /*			long t2 = Sage.time();
			if (data.length > 4096 && t2 > t1)
			{
				estimatedBandwidthBytes += data.length + 16;
				estimatedBandwidthTime += t2 - t1;
			}*/
      return rv;
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error16:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return 0;
  }

  protected synchronized int loadImageCompressedMini(long handle, java.io.InputStream data, int dataLength)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("loadImageCompressedMini(stream)");
    if (dataLength + 12 > MAX_CMD_LEN)
    {
      if (Sage.DBG) System.out.println("Skipping loading image because its source is larger than 16MB");
      return 0;
    }
    try
    {
      //			long t1 = Sage.time();
      prepUICmdHeader(12+dataLength);
      sockBuf.putInt(GFXCMD_LOADIMAGECOMPRESSED<<CMD_LEN_BITS | 8+dataLength);
      sockBuf.putInt((int)handle);
      sockBuf.putInt(dataLength);
      if (fontXferBuf == null || fontXferBuf.length < 4096)
        fontXferBuf = new byte[4096];
      while (dataLength > 0)
      {
        int numRead = data.read(fontXferBuf, 0, Math.min(dataLength, 4096));
        if (numRead < 0)
        {
          // OK, so we failed reading from the source....that's fine, just send whatever which is better than killing this session
          numRead = Math.min(dataLength, 4096);
          if (Sage.DBG) System.out.println("FAILED reading from compressed image source, bytes left=" + dataLength);
        }
        sockBuf.put(fontXferBuf, 0, numRead);
        dataLength -= numRead;
        sockBuf.flip();
        while (sockBuf.hasRemaining())
          clientSocket.write(sockBuf);
        sockBuf.clear();
      }
      int rv = recvr.getIntReply();
      // Don't use this for BW calc because the client will decompress before this returns so the result is bad for timing
      /*			long t2 = Sage.time();
			if (data.length > 4096 && t2 > t1)
			{
				estimatedBandwidthBytes += data.length + 16;
				estimatedBandwidthTime += t2 - t1;
			}*/
      return rv;
    }catch(Exception e)
    {
      killUIMgrAsync();
      if (Sage.DBG) System.out.println("MiniUI client error16:" + e);
      if (Sage.DBG) e.printStackTrace();
    }
    return 0;
  }

  protected synchronized int loadImageDirectMini(long handle, java.io.File f, long offset, long size)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("loadImageDirectMini()");
    try
    {
      byte[] b = f.getAbsolutePath().getBytes(Sage.I18N_CHARSET);
      prepUICmdHeader(20+b.length);
      sockBuf.putInt(GFXCMD_LOADIMAGEDIRECT<<CMD_LEN_BITS | 16+b.length);
      sockBuf.putInt((int)handle);
      sockBuf.putInt((int)offset);
      sockBuf.putInt((int)size);
      sockBuf.putInt(b.length);
      sockBuf.put(b);
      sendBufferNow();
      int rv = recvr.getIntReply();
      return rv;
    }catch(Exception e)
    {
      killUIMgrAsync();
      if (Sage.DBG) System.out.println("MiniUI client error16b:" + e);
      if (Sage.DBG) e.printStackTrace();
    }
    return 0;
  }

  protected synchronized void loadImageDirectAsyncMini(long handle, java.io.File f, long offset, long size)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("loadImageDirectAsyncMini()");
    try
    {
      byte[] b = f.getAbsolutePath().getBytes(Sage.I18N_CHARSET);
      prepUICmdHeader(20+b.length);
      sockBuf.putInt(GFXCMD_LOADIMAGEDIRECTASYNC<<CMD_LEN_BITS | 16+b.length);
      sockBuf.putInt((int)handle);
      sockBuf.putInt((int)offset);
      sockBuf.putInt((int)size);
      sockBuf.putInt(b.length);
      sockBuf.put(b);
      sendBufferNow(); // since we may not have another request to push this through
    }catch(Exception e)
    {
      killUIMgrAsync();
      if (Sage.DBG) System.out.println("MiniUI client error16c:" + e);
      if (Sage.DBG) e.printStackTrace();
    }
  }

  protected synchronized int transformImageMini(long srcHandle, long destHandle, int destWidth,
      int destHeight, int maskCornerArc)
  {
    // srcHandle, destHandle, destWidth, destHeight, maskCornerArc
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("transformImageMini()");
    try
    {
      prepUICmdHeader(24);
      sockBuf.putInt(GFXCMD_XFMIMAGE<<CMD_LEN_BITS | 20);
      sockBuf.putInt((int)srcHandle);
      sockBuf.putInt((int)destHandle);
      sockBuf.putInt(destWidth);
      sockBuf.putInt(destHeight);
      sockBuf.putInt(maskCornerArc);
      if (advImageCaching)
        return 0; // return value not sent in this mode
      sendBufferNow();
      int rv = recvr.getIntReply();
      return rv;
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error17:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return 0;
  }

  protected synchronized void unloadImageMini(long handle)
  {
    if (Sage.DBG) System.out.println("unloadImageMini(" + handle + ")");
    try
    {
      if (lastUsedHighResSurf == handle)
      {
        if (Sage.DBG) System.out.println("Deallocating high res surface that is currently being displayed");
        lastUsedHighResSurf = 0;
        setVideoPropertiesMini(VIDEO_MODE_MASK_HANDLE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
      }
      // Check if it was a high-res surface and remove that entry if so
      int idx = highResSurfaceHandles.indexOf(new Long(handle));
      if (idx > -1)
      {
        highResSurfaceHandles.remove(idx);
        highResSurfaceData.remove(idx);
        if (DEBUG_NATIVE2D) System.out.println("High-res surface is being deallocated:" + handle +" numLeft=" + highResSurfaceHandles.size());
      }
      prepUICmdHeader(8);
      sockBuf.putInt(GFXCMD_UNLOADIMAGE<<CMD_LEN_BITS | 4);
      sockBuf.putInt((int) handle);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error18:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void setTargetSurfaceMini(long handle)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("setTargetSurfaceMini(" + handle + ")");
    try
    {
      prepUICmdHeader(8);
      sockBuf.putInt(GFXCMD_SETTARGETSUFACE<<CMD_LEN_BITS | 4);
      sockBuf.putInt((int) handle);
      //            clientOutStream.flush();
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error19:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void setWaitCursor(int mode, int x, int y, int state, int width, int height, byte[] data)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("setWaitCusor(mode=" + mode + ", x=" + x + ", y=" + y +
        ", state=" + state + ", width=" + width + ", height=" + height + ", dataLen=" + (data == null ? 0 : data.length) + ")");
    try
    {
      prepUICmdHeader(28 + (data == null ? 0 : data.length));
      sockBuf.putInt(GFXCMD_SETCURSORPROP<<CMD_LEN_BITS | (24 + (data == null ? 0 : data.length)));
      sockBuf.putInt(mode);
      sockBuf.putInt(x);
      sockBuf.putInt(y);
      sockBuf.putInt(state);
      sockBuf.putInt(data == null ? 0 : width);
      sockBuf.putInt(data == null ? 0 : height);
      if (data != null)
        sockBuf.put(data);
      sendBufferNow();
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error20-a:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized void setVideoPropertiesMini(int mode, int sx, int sy, int swidth, int sheight,
      int ox, int oy, int owidth, int oheight, int alpha, long handle)
  {
    if (Sage.DBG && DEBUG_NATIVE2D)	System.out.println("setVideoPropertiesMini(mode=" + mode + ", sx=" + sx +
        ", sy=" + sy + ", swidth=" + swidth + ", sheight=" + sheight + ", ox=" + ox + ", oy=" + oy +
        ", owidth=" + owidth + ", oheight=" + oheight + ", alpha=" + alpha + ", handle=" + handle + ")");
    lastHiResSurfDest.setBounds((ox - owidth/2) * master.getWidth() / 4096, (oy - oheight/2) * master.getHeight() / 4096,
        owidth * master.getWidth() / 4096, oheight * master.getHeight() / 4096);
    try
    {
      prepUICmdHeader(48);
      sockBuf.putInt(GFXCMD_SETVIDEOPROP<<CMD_LEN_BITS | 44);
      sockBuf.putInt(mode);
      sockBuf.putInt(sx);
      sockBuf.putInt(sy);
      sockBuf.putInt(swidth);
      sockBuf.putInt(sheight);
      sockBuf.putInt(ox);
      sockBuf.putInt(oy);
      sockBuf.putInt(owidth);
      sockBuf.putInt(oheight);
      sockBuf.putInt(alpha);
      sockBuf.putInt((int)handle);
      sendBufferNow();
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error20:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized long loadFontMini(String name,
      int style, int size)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("loadFontMini()");
    try
    {
      prepUICmdHeader(name.length() + 17);
      sockBuf.putInt(GFXCMD_LOADFONT<<CMD_LEN_BITS | name.length()+13);
      byte [] b=name.getBytes("ISO8859_1");
      sockBuf.putInt(name.length()+1);
      sockBuf.put(b, 0, b.length);
      sockBuf.put((byte)0);
      sockBuf.putInt(style);
      sockBuf.putInt(size);
      sendBufferNow();
      return recvr.getIntReply();
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error21:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return 0;
  }

  protected synchronized void loadFontStreamMini(String name, java.io.File fontFile)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("loadFontStreamMini()");
    java.io.FileInputStream fis = null;
    byte[] buf = new byte[4096];
    try
    {
      prepUICmdHeader(name.length() + 13 + (int)fontFile.length());
      sockBuf.putInt(GFXCMD_LOADFONTSTREAM<<CMD_LEN_BITS | (name.length()+9+(int)fontFile.length()));
      byte [] b=name.getBytes("ISO8859_1");
      sockBuf.putInt(name.length()+1);
      sockBuf.put(b, 0, b.length);
      sockBuf.put((byte)0);
      sockBuf.putInt((int)fontFile.length());
      sendBufferNow();
      long bytesToSend = fontFile.length();
      fis = new java.io.FileInputStream(fontFile);
      java.nio.channels.FileChannel fc = fis.getChannel();
      fc.transferTo(0, bytesToSend, clientSocket);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error22:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    finally
    {
      if (fis != null)
      {
        try
        {
          fis.close();
        }
        catch (Exception e){}
      }
    }
  }

  protected synchronized void unloadFontMini(long handle)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("unloadFontMini()");
  }

  // use 0 for the steadyState when animating; 1 if not animating
  protected synchronized void flipBufferMini(int steadyState)
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("flipBufferMini()");
    try
    {
      prepUICmdHeader(8);
      sockBuf.putInt(GFXCMD_FLIPBUFFER << CMD_LEN_BITS | 4);
      sockBuf.putInt(steadyState);

      // Set this at the end of rendering so the transparency of the GFX plane is setup properly before we put the new image
      // into the video plane
      if (pendingHiResSurfHandleToSet != 0)
      {
        if (pendingVidSurfCoords != null)
        {
          if ((gfxVideoPropsSupportMask & VIDEO_MODE_MASK_TL_ORIGIN) != 0)
          {
            // Clip the video rectangles to be within the screen bounds; this leads to more accurate scaling and positioning of the surfaces
            java.awt.Rectangle srcRect = new java.awt.Rectangle(pendingVidSurfCoords[0], pendingVidSurfCoords[1], pendingVidSurfCoords[2], pendingVidSurfCoords[3]);
            java.awt.Rectangle destRect = new java.awt.Rectangle(pendingVidSurfCoords[4], pendingVidSurfCoords[5], pendingVidSurfCoords[6], pendingVidSurfCoords[7]);
            Sage.clipSrcDestRects(new java.awt.Rectangle(0, 0, 4096, 4096), srcRect, destRect);
            setVideoPropertiesMini(((pendingHiResSurfHandleToSet != lastUsedHighResSurf) ? VIDEO_MODE_MASK_HANDLE : 0) |
                VIDEO_MODE_MASK_OUTPUT | VIDEO_MODE_MASK_SOURCE | VIDEO_MODE_MASK_TL_ORIGIN,
                srcRect.x, srcRect.y, srcRect.width, srcRect.height,
                destRect.x, destRect.y, destRect.width, destRect.height, pendingVidSurfCoords[8], pendingHiResSurfHandleToSet);
          }
          else
          {
            setVideoPropertiesMini(((pendingHiResSurfHandleToSet != lastUsedHighResSurf) ? VIDEO_MODE_MASK_HANDLE : 0) |
                VIDEO_MODE_MASK_OUTPUT | VIDEO_MODE_MASK_SOURCE,
                pendingVidSurfCoords[0], pendingVidSurfCoords[1],
                pendingVidSurfCoords[2], pendingVidSurfCoords[3], pendingVidSurfCoords[4], pendingVidSurfCoords[5], pendingVidSurfCoords[6],
                pendingVidSurfCoords[7], pendingVidSurfCoords[8], pendingHiResSurfHandleToSet);
          }
          lastUsedHighResSurf = pendingHiResSurfHandleToSet;
        }
        else if (lastUsedHighResSurf != pendingHiResSurfHandleToSet)
        {
          setVideoPropertiesMini(VIDEO_MODE_MASK_HANDLE, 0, 0, 0, 0, 0, 0, 0, 0,
              0, pendingHiResSurfHandleToSet);
          lastUsedHighResSurf = pendingHiResSurfHandleToSet;
        }
      }
      else if (lastUsedHighResSurf != 0)
      {
        setVideoPropertiesMini(VIDEO_MODE_MASK_HANDLE, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0);
        lastUsedHighResSurf = 0;
      }

      sendBufferNow();
      releaseRenderThread();
      if (advImageCaching)
        recvr.discardIntReply();
      else
        recvr.getIntReply();
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error23:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected synchronized int startFrameMini()
  {
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("startFrameMini()");
    try
    {
      prepUICmdHeader(4);
      sockBuf.putInt(GFXCMD_STARTFRAME << CMD_LEN_BITS | 0);
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error24:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return 0;
  }

  public synchronized void forceMediaReconnect()
  {
    if (!supportsForcedMediaReconnect) return;
    if (Sage.DBG && DEBUG_NATIVE2D) System.out.println("forceMediaReconnect()");
    try
    {
      prepUICmdHeader(4);
      sockBuf.putInt(GFXCMD_MEDIA_RECONNECT << CMD_LEN_BITS | 0);
      sendBufferNow();
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error24-1:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
  }

  protected void ensureBufferCanHandle(int dataSize) throws java.io.IOException
  {
    // We need to flush the buffer before we do another command
    if (sockBuf.remaining() < dataSize)
      sendBufferNow();
  }

  protected void sendBufferNow() throws java.io.IOException
  {
    if (sockBuf.position() == 0) return;
    sockBuf.flip();
    if (zout != null)
    {
      zout.write(sockBuf.array(), 0, sockBuf.limit());
      zout.flush();
    }
    else
    {
      while (clientSocket != null && sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
    }
    sockBuf.clear();
  }

  // Makes sure we don't have too much data we're batching up for texture drawing commands
  private java.nio.ByteBuffer textureBatchBuf;
  protected void checkTextureBatchLimit(boolean forceIt) throws java.io.IOException
  {
    if (forceIt && textureBatchCount == 1)
    {
      // If there's only one in there then just clear the batch
      textureBatchCount = textureBatchSize = 0;
    }
    if (textureBatchSize > 0 && (forceIt || textureBatchSize + 100 > textureBatchLimit))
    {
      // Send the texture batch now or we'll blow the size limit; or we're having a command type
      // change which requires flushing it
      if (textureBatchBuf == null)
        textureBatchBuf = java.nio.ByteBuffer.allocate(256);
      textureBatchBuf.clear();
      textureBatchBuf.put((byte)DRAWING_CMD_TYPE);
      int dataSize = 12 + textureBatchSize;
      textureBatchBuf.put((byte)((dataSize >> 16) & 0xFF));
      textureBatchBuf.put((byte)((dataSize >> 8) & 0xFF));
      textureBatchBuf.put((byte)(dataSize & 0xFF));
      textureBatchBuf.putInt(GFXCMD_TEXTUREBATCH << CMD_LEN_BITS | 8);
      textureBatchBuf.putInt(textureBatchCount);
      textureBatchBuf.putInt(textureBatchSize);
      textureBatchBuf.flip();
      while (clientSocket != null && textureBatchBuf.hasRemaining())
        clientSocket.write(textureBatchBuf);
      sockBuf.flip();
      while (clientSocket != null && sockBuf.hasRemaining())
        clientSocket.write(sockBuf);
      sockBuf.clear();
      textureBatchSize = 0;
      textureBatchCount = 0;
    }
  }

  public synchronized boolean saveScreenshotToFile(java.io.File f)
  {
    try
    {
      prepUICmdHeader(4);
      sockBuf.putInt(GFXCMD_SCREENSHOT << CMD_LEN_BITS | 0);
      sendBufferNow();
      ReplyPacket reply = recvr.getReply();
      java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(reply.getData());
      int width = bb.getInt();
      int height = bb.getInt();
      int format = bb.getInt();
      java.nio.ByteBuffer imgData = java.nio.ByteBuffer.allocateDirect(width * height * 4);
      // Reorder from RGBA to ARGB and flip vertically
      for (int i = 0; i < height; i++)
      {
        bb.position(width * 4 * (height - i - 1)); // vertical flip
        for (int j = 0; j < width; j++)
        {
          // reorder pixel data
          imgData.put(bb.get((i*j + 1)*4 - 1));
          imgData.put(bb.get());
          imgData.put(bb.get());
          imgData.put(bb.get());
          bb.get();
        }
      }
      imgData.flip();
      sage.media.image.RawImage rawImage = new sage.media.image.RawImage(width, height, imgData, true, width*4, true);
      sage.media.image.ImageLoader.compressImageToFilePath(rawImage, f.getAbsolutePath(), "png");
    }catch(Exception e)
    {
      connectionError();
      if (Sage.DBG) System.out.println("MiniUI client error24:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
      return false;
    }
    return true;
  }

  protected void prepUICmdHeader(int dataSize) throws java.io.IOException
  {
    prepUICmdHeader(dataSize, false);
  }
  protected void prepUICmdHeader(int dataSize, boolean smallTextureCmd) throws java.io.IOException
  {
    if (textureBatchLimit > 0)
    {
      checkTextureBatchLimit(!smallTextureCmd);
      if (smallTextureCmd)
      {
        if (textureBatchCount == 0 && sockBuf.position() > 0)
          sendBufferNow();
        textureBatchCount++;
        textureBatchSize += dataSize + 4;
      }
    }
    ensureBufferCanHandle(dataSize + 4);
    sockBuf.putInt(DRAWING_CMD_TYPE_SHIFTED | (dataSize & 0xFFFFFF));
  }

  protected void prepFSCmdHeader(int dataSize) throws java.io.IOException
  {
    // Clean up any batched texture commands
    if (textureBatchLimit > 0)
      checkTextureBatchLimit(true);
    ensureBufferCanHandle(dataSize + 4);
    sockBuf.put((byte)FS_CMD_TYPE);
    sockBuf.put((byte)((dataSize >> 16) & 0xFF));
    sockBuf.put((byte)((dataSize >> 8) & 0xFF));
    sockBuf.put((byte)(dataSize & 0xFF));
  }

  protected String sendGetProperty(String propName) throws java.io.IOException
  {
    // Clean up any batched texture commands
    if (textureBatchLimit > 0)
      checkTextureBatchLimit(true);
    int dataSize = propName.length();
    ensureBufferCanHandle(dataSize + 4);
    sockBuf.put((byte)GET_PROPERTY_CMD_TYPE);
    sockBuf.put((byte)((dataSize >> 16) & 0xFF));
    sockBuf.put((byte)((dataSize >> 8) & 0xFF));
    sockBuf.put((byte)(dataSize & 0xFF));
    sockBuf.put(propName.getBytes(Sage.BYTE_CHARSET));
    sendBufferNow();
    return recvr.getStringReply();
  }
  protected void sendGetPropertyAsync(String propName) throws java.io.IOException
  {
    // Clean up any batched texture commands
    if (textureBatchLimit > 0)
      checkTextureBatchLimit(true);
    int dataSize = propName.length();
    ensureBufferCanHandle(dataSize + 4);
    sockBuf.put((byte)GET_PROPERTY_CMD_TYPE);
    sockBuf.put((byte)((dataSize >> 16) & 0xFF));
    sockBuf.put((byte)((dataSize >> 8) & 0xFF));
    sockBuf.put((byte)(dataSize & 0xFF));
    sockBuf.put(propName.getBytes(Sage.BYTE_CHARSET));
  }
  protected byte[] sendGetRawProperty(String propName) throws java.io.IOException
  {
    // Clean up any batched texture commands
    if (textureBatchLimit > 0)
      checkTextureBatchLimit(true);
    int dataSize = propName.length();
    ensureBufferCanHandle(dataSize + 4);
    sockBuf.put((byte)GET_PROPERTY_CMD_TYPE);
    sockBuf.put((byte)((dataSize >> 16) & 0xFF));
    sockBuf.put((byte)((dataSize >> 8) & 0xFF));
    sockBuf.put((byte)(dataSize & 0xFF));
    sockBuf.put(propName.getBytes(Sage.BYTE_CHARSET));
    sendBufferNow();
    ReplyPacket rp = recvr.getReply();
    return rp == null ? null : rp.getData();
  }

  protected synchronized int sendSetProperty(String propName, String propVal) throws java.io.IOException
  {
    return sendSetProperty(propName.getBytes(Sage.BYTE_CHARSET), propVal.getBytes(Sage.BYTE_CHARSET));
  }
  protected synchronized int sendSetProperty(byte[] propName, byte[] propVal) throws java.io.IOException
  {
    // Clean up any batched texture commands
    if (textureBatchLimit > 0)
      checkTextureBatchLimit(true);
    int dataSize = 4 + propName.length + propVal.length;
    ensureBufferCanHandle(dataSize + 4);
    sockBuf.put((byte)SET_PROPERTY_CMD_TYPE);
    sockBuf.put((byte)((dataSize >> 16) & 0xFF));
    sockBuf.put((byte)((dataSize >> 8) & 0xFF));
    sockBuf.put((byte)(dataSize & 0xFF));
    sockBuf.putShort((short)propName.length);
    sockBuf.putShort((short)propVal.length);
    sockBuf.put(propName);
    sockBuf.put(propVal);
    sendBufferNow();
    return recvr.getIntReply();
  }

  private java.util.Map nativeFontMap = new java.util.HashMap();
  private long getNativeFontHandle(String name, int style, int size)
  {
    String fontName = name + "-" + style + "-" + size;
    Long hand = (Long) nativeFontMap.get(fontName);
    if (hand != null)
      return hand.longValue();
    else
    {
      hand = new Long(loadFontMini(name, style, size));
      if (hand.longValue() != 0)
        nativeFontMap.put(fontName, hand);
      else if (remotelyTransferFonts)
      {
        // NOTE NOTE NOTE Narflex
        // This is VERY BAD to do but I couldn't find an alternative. This is HIGHLY version
        // dependent code.
        // Resolve the font object into a file and then push that file to the client
        java.awt.Font myFont = UIManager.getCachedJavaFont(name, style, size);
        java.io.File fontFile = null;
        try
        {
          Class fontMgrClass = Class.forName("sun.font.FontManager");
          java.lang.reflect.Method findFont2DMeth = fontMgrClass.getMethod("findFont2D", new Class[] { String.class, Integer.TYPE, Integer.TYPE });
          Object font2d = findFont2DMeth.invoke(null, new Object[] { myFont.getName(), new Integer(myFont.getStyle()), new Integer(1) });
          java.lang.reflect.Field platField = Class.forName("sun.font.PhysicalFont").getDeclaredField("platName");
          platField.setAccessible(true);
          fontFile = new java.io.File(platField.get(font2d).toString());
          if (Sage.DBG) System.out.println("Sending font stream from source: " + fontFile);
          loadFontStreamMini(name + "-" + style, fontFile);
        }
        catch (Throwable t)
        {
          try
          {
            sendSetProperty("GFX_FONTSERVER", "FALSE");
          }
          catch (java.io.IOException e){}
          System.out.println("ERROR resolving a font into a file path of:" + t);
          System.out.println("Disabling remote font transfers");
        }
        hand = new Long(loadFontMini(name, style, size));
        if (hand.longValue() != 0)
          nativeFontMap.put(fontName, hand);
      }
      return hand.longValue();
    }
  }

  private boolean sentUIMgrKill = false;
  private void killUIMgrAsync()
  {
    if (sentUIMgrKill) return;
    sentUIMgrKill = true;
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        uiMgr.goodbye();
      }
    }, "KillUIMgr");
  }

  private boolean startedReconnectDaemon = false;
  private boolean reconnectCompleted = false;
  private Object reconnectLock = new Object();
  public boolean connectionError() // returns true if we should just die
  {
    // When an error occurs on the connection we don't immediately destroy everything since the client may try to reconnect.
    // A normal reconnect would kill their old session; so if they don't support the new logic then we're fine.
    // We also don't allow reconnects if the event channel is encrypted or we haven't started the first frame of rendering.
    if (renderStartTime == 0 || recvr.isEncryptionOn() || !SageTV.isAlive() || sentUIMgrKill)
    {
      killUIMgrAsync();
      return true;
    }
    synchronized (reconnectLock)
    {
      if (startedReconnectDaemon) return false;
      reconnectCompleted = false;
      startedReconnectDaemon = true;
    }
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        vf.prepareForReload();
        if (Sage.DBG) System.out.println("MCSR reconnect daemon has started...wait up to 30 seconds for a client reconnect...");
        long startTime = Sage.time();
        synchronized (reconnectLock)
        {
          while (!reconnectCompleted && Sage.time() - startTime < 30000 && !sentUIMgrKill)
          {
            // Give a max of 30 seconds for a reconnect to be performed
            try{reconnectLock.wait(1000);}catch(Exception e){}
          }
        }
        if (!reconnectCompleted)
        {
          if (Sage.DBG) System.out.println("MCSR reconnect daemon timed out w/no reconnection, kill the UI!");
          killUIMgrAsync();
        }
        startedReconnectDaemon = false;
      }
    }, "MCSRReconnect");
    return false;
  }

  private void useReconnectSocket(java.nio.channels.SocketChannel newChan)
  {
    // Make sure we know about this because it's possible the client is doing it before we're aware of the situation
    synchronized (reconnectLock)
    {
      startedReconnectDaemon = true;
    }
    if (Sage.DBG) System.out.println("MCSR is setting up the reconnection for the client w/ a new socket...");
    sockBuf.clear();
    clientSocket = newChan;
    // Do the zip socket if we're in that mode
    if (zipSocks && textureBatchLimit == 0)
    {
      // All data we send AFTER this property set will need to be ZLIB compressed
      try
      {
        zout = new com.jcraft.jzlib.ZOutputStream(clientSocket.socket().getOutputStream(), 5/*halfway between best speed & best compression*/, true);
        zout.setFlushMode(com.jcraft.jzlib.JZlib.Z_SYNC_FLUSH);
      }
      catch (Exception e)
      {
        if (Sage.DBG) System.out.println("ERROR in setting up zip socket on reconnect..destroy the connection err=" + e);
        killUIMgrAsync();
        startedReconnectDaemon = false;
        return;
      }
    }
    recvr.setNewStream(clientSocket);
    // Finish the media player reload now
    vf.reloadFile();
    synchronized (reconnectLock)
    {
      reconnectCompleted = true;
      startedReconnectDaemon = false;
      reconnectLock.notifyAll();
    }
    uiMgr.trueValidate();
  }

  // Required to redo the positioning for the trial text to prevent screen burn;
  // we also use it to force UI refreshes on certain client state changes that don't take effect until a redraw
  public boolean needsRefresh()
  {
    return updateOutputModeInfo || remoteResolutionChange != null || advancedARChange != null || nextEventCryptoState != null ||
        appModeChange != null;
  }

  public boolean supportsPartialUIUpdates()
  {
    return partialUpdatesOK && !bufferFlipping;
  }

  public boolean isMiniClientColorKeyed()
  {
    return colorKeying;
  }

  public java.awt.Color getMiniClientColorKey()
  {
    return remoteColorKey;
  }

  public boolean isSupportedVideoCodec(String testMe)
  {
    return testMe != null && videoCodecs != null && videoCodecs.contains(testMe.toUpperCase());
  }

  public boolean isSupportedAudioCodec(String testMe)
  {
    if (testMe != null && (testMe.toUpperCase().startsWith("PCM_") || testMe.toUpperCase().startsWith("ADPCM_")))
      testMe = "PCM_S16LE"; // this is the base PCM codec description from FFMPEG
    return testMe != null && audioCodecs != null && audioCodecs.contains(testMe.toUpperCase());
  }

  public boolean isSupportedPushContainerFormat(String testMe)
  {
    return testMe != null && pushContainers != null && pushContainers.contains(testMe.toUpperCase());
  }

  public boolean isSupportedPullContainerFormat(String testMe)
  {
    return testMe != null && pullContainers != null && pullContainers.contains(testMe.toUpperCase());
  }

  public boolean isStreamingProtocolSupported(String testMe)
  {
    return testMe != null && streamingProtocols != null && streamingProtocols.contains(testMe.toUpperCase());
  }

  public String getFixedPushMediaFormat()
  {
    return fixedPushMediaFormatProp;
  }

  public boolean isLocalConnection()
  {
    return localConnection;
  }

  public boolean isLoopbackConnection()
  {
    return loopbackConnection;
  }

  public boolean setEventEncryption(boolean x)
  {
    if (cryptoSupport)
      nextEventCryptoState = new Boolean(x);
    return cryptoSupport;
  }

  public boolean connectionRequiresAuthentication()
  {
    return cryptoSupport;
  }

  public long getEstimatedBandwidth()
  {
    if (Sage.DBG) System.out.println("getEstimatedBW=" + ((estimatedBandwidthTime == 0) ? 0 : estimatedBandwidthBytes*8000/estimatedBandwidthTime) +
        " estimatedBWBytes=" + estimatedBandwidthBytes + " estimatedBWTime=" + estimatedBandwidthTime);
    return (estimatedBandwidthTime == 0) ? 0 : estimatedBandwidthBytes*8000/estimatedBandwidthTime;
  }

  // If something else external does a calc cause we don't know, it should update us with what it found
  public void addDataToBandwidthCalc(long bytes, long time)
  {
    estimatedBandwidthTime += time;
    estimatedBandwidthBytes += bytes;
  }

  public void cacheAuthenticationNow()
  {
    if (!recvr.isEncryptionOn())
    {
      if (Sage.DBG) System.out.println("ERROR cannot establish authenticated login if the channel is not encrypted!!");
      return;
    }
    // Generate 128-bit random data; then convert it to a hexadecimal string. That's what we send to the client. In our
    // properties file we store a SHA-1 hash of that string to verify against later.
    byte[] randy = new byte[16];
    new java.util.Random().nextBytes(randy);
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < randy.length; i++)
    {
      if ((randy[i] & 0xFF) < 16)
        sb.append("0");
      sb.append(Integer.toString((randy[i] & 0xFF), 16));
    }
    String clientAuthStr = sb.toString();
    // Save the hash of it on the server
    Sage.put("miniclient/cached_authentication/" + uiMgr.getLocalUIClientName(), SageTV.oneWayEncrypt(clientAuthStr));
    // Send the actual value to the client
    pendingClientAuthSet = clientAuthStr;
  }

  public boolean isDetailedPushBufferStats()
  {
    return detailedPushBufferStats;
  }

  public boolean supportsFrameStep()
  {
    return frameStepSupport;
  }

  public java.awt.Dimension getMaxClientResolution()
  {
    return maxClientResolution;
  }

  public sage.media.format.VideoFormat getDisplayResolution()
  {
    return displayResolution;
  }

  // This may return false even though it is full screen if it started out that way, it only goes off what we sent to the MiniClient
  public boolean isFullScreen()
  {
    return remoteFullScreenMode;
  }

  public boolean isIOSClient()
  {
    return iPhoneMode;
  }

  public void pushFullScreenMode(boolean x)
  {
    fullScreenChange = new Boolean(x);
  }

  public void switchAppMode(String x)
  {
    appModeChange = x;
  }

  public boolean pushResolutionChange(String x)
  {
    if (displayResolution != null && displayResolution.getFormatName().equals(x))
      return false; // redundant
    if (Sage.DBG) System.out.println("Sending remote resolution change of: " + x + " val=" + resolutionNameMap.get(x));
    synchronized (remoteResolutionChangeLock)
    {
      remoteResolutionChange = x;
      displayResolution = sage.media.format.VideoFormat.buildVideoFormatForResolution(remoteResolutionChange);
    }
    master.getRoot().appendToDirty(false);
    // We have to disable this due to deadlocking...it wasn't necessarily required anyways for things to work properly
    /*		while (remoteResolutionChange != null && alive)
		{
			try{Thread.sleep(15);}catch(Exception e){}
		}*/
    return true;
  }

  public String getDetailedResolution(String x)
  {
    return null;
  }

  public boolean supportsPushBufferSeeking()
  {
    return pushBufferSeeking;
  }

  public String getInputDevsProp()
  {
    return inputDevsProp;
  }

  public long getMediaPlayerDelay()
  {
    return mediaPlayerDelay;
  }

  public int getPushBufferSizeLimit()
  {
    return pushBufferLimit;
  }

  public boolean isMediaExtender()
  {
    return inputDevsProp == null || inputDevsProp.indexOf("MOUSE") == -1;
  }

  public boolean supports3DTransforms()
  {
    return xformRenderSupport;
  }

  public boolean isStandaloneMediaPlayer()
  {
    return isMediaExtender() && highResSurfCaps == YUV_CACHE_UNIFIED;
  }

  public int getGfxScalingCaps()
  {
    return gfxScalingCaps;
  }

  public boolean canSupportAnimations()
  {
    return (surfaceSupport || (gfxScalingCaps & GFX_SCALING_HW) != 0) && (isLocalConnection() || Sage.getBoolean("ui/allow_non_local_animations", false));
  }

  public boolean supportsScreenshots()
  {
    return screenshotSupport;
  }

  public String[] getResolutionOptions()
  {
    return resolutionOptions;
  }

  private String cachedVidSupModeProp;
  private String[] cachedEnabledResOptions;
  private sage.media.format.VideoFormat[] cachedEnabledFormatOptions;
  private void fixEnabledResCache()
  {
    String restrictProp = Sage.get("VideoSupportedModes", "");
    if (restrictProp != cachedVidSupModeProp && (restrictProp == null || !restrictProp.equals(cachedVidSupModeProp)))
    {
      cachedVidSupModeProp = restrictProp;
      if (restrictProp.length() > 0)
      {
        java.util.Vector rv1 = new java.util.Vector();
        java.util.Vector rv2 = new java.util.Vector();
        java.util.Set modes = Sage.parseDelimSet(restrictProp, ";,");
        for (int i = 0; i < resolutionOptions.length; i++)
        {
          java.util.Iterator walker = modes.iterator();
          while (walker.hasNext())
          {
            String token = walker.next().toString();
            if (resolutionOptions[i].indexOf(token) != -1)
            {
              rv1.add(resolutionOptions[i]);
              rv2.add(resolutionFormats[i]);
              break;
            }
          }
        }
        if (!rv1.isEmpty())
        {
          cachedEnabledResOptions = (String[]) rv1.toArray(Pooler.EMPTY_STRING_ARRAY);
          cachedEnabledFormatOptions = (sage.media.format.VideoFormat[]) rv2.toArray(new sage.media.format.VideoFormat[0]);
        }
        else
        {
          cachedEnabledResOptions = resolutionOptions;
          cachedEnabledFormatOptions = resolutionFormats;
        }
      }
      else
      {
        cachedEnabledResOptions = resolutionOptions;
        cachedEnabledFormatOptions = resolutionFormats;
      }
    }
  }
  public String[] getEnabledResolutionOptions()
  {
    return null;
  }

  public String[] getPreferredResolutionOptions()
  {
    return preferredResolutions;
  }

  public sage.media.format.VideoFormat[] getResolutionFormats()
  {
    return resolutionFormats;
  }

  public sage.media.format.VideoFormat[] getEnabledResolutionFormats()
  {
    return null;
  }

  public static java.awt.Dimension parseResolution(String s)
  {
    if (s == null || s.length() == 0) return null;
    try
    {
      int xidx = s.indexOf('x');
      if (xidx == -1)
        return null;
      int width = Integer.parseInt(s.substring(0, xidx));
      int lastIdx = xidx+1;
      while (Character.isDigit(s.charAt(lastIdx)))
      {
        lastIdx++;
        if (lastIdx >= s.length())
          break;
      }
      int height = Integer.parseInt(s.substring(xidx + 1, lastIdx));
      return new java.awt.Dimension(width, height);
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("Error parsing resolution:" + s + " of:" + e);
      return null;
    }
  }

  public boolean hasSurfaceSupport()
  {
    return surfaceSupport;
  }

  public boolean hasSubtitleSupport()
  {
    return subtitleSupport;
  }

  public boolean hasDiffuseTextureSupport()
  {
    return diffuseTextureSupport;
  }

  public boolean canCacheAuthentication()
  {
    return clientCanDoAuth && Sage.getBoolean("miniclient/enable_cached_authentication", true);
  }

  private static java.util.Set createSetFromString(String s)
  {
    if (s == null || s.length() == 0)
      return new java.util.HashSet();
    java.util.Set rv = new java.util.HashSet();
    java.util.StringTokenizer toker = new java.util.StringTokenizer(s, ",;");
    while (toker.hasMoreTokens())
      rv.add(toker.nextToken().toUpperCase());
    return rv;
  }

  public String[] getAudioOutputOptions()
  {
    return audioOutputOptions;
  }

  public void setAudioOutput(String x)
  {
    audioOutputChange = x;
    currAudioOutput = audioOutputChange;
  }

  public String getAudioOutput()
  {
    return currAudioOutput;
  }

  public boolean doesNeedInitDriver()
  {
    return needsInitDriver;
  }

  public boolean supportsAdvancedAspectRatios()
  {
    return advancedARNames != null && advancedARNames.length > 0;
  }

  public String[] getAdvancedAspectRatioOptions()
  {
    // Reload the list of it has changed in the properties
    if (lastLoadedAdvancedARProp != null &&
        !lastLoadedAdvancedARProp.equals(uiMgr.get("advanced_aspect_ratio_extra_modes", "")))
      loadAdvancedARs();
    return advancedARNames;
  }

  public void setAdvancedARMode(String x)
  {
    if (x == null || advancedARNames == null) return;
    if (lastLoadedAdvancedARProp != null &&
        !lastLoadedAdvancedARProp.equals(uiMgr.get("advanced_aspect_ratio_extra_modes", "")))
    {
      // Allow redundant AR setting if they've change the properties
      loadAdvancedARs();
    }
    // Reload the list of it has changed in the properties
    String newARDetails = null;
    for (int i = 0; i < advancedARNames.length; i++)
      if (advancedARNames[i].equals(x))
      {
        newARDetails = advancedARsFull[i];
        break;
      }
    if (currAdvancedARDetails == null || !currAdvancedARDetails.equals(newARDetails))
      advancedARChange = newARDetails;
    if (advancedARChange != null)
    {
      currAdvancedAR = x;
      currAdvancedARDetails = advancedARChange;
    }
    master.getRoot().appendToDirty(false);
    // This will allow the UI to be refreshed by the user
    Catbert.processUISpecificHook("MediaPlayerPlayStateChanged", null, uiMgr, true);
  }

  public void resetAdvancedAR()
  {
    String replaceAR = uiMgr.get("default_advanced_aspect_ratio", "");
    if (replaceAR.length() > 0 && !replaceAR.equals(defaultAdvancedAR))
    {
      defaultAdvancedAR = replaceAR;
      for (int i = 0; i < advancedARNames.length; i++)
      {
        if (advancedARNames[i].equals(replaceAR))
        {
          // Spawn a thread for this to prevent potential deadlocking issues
          final int arnum = i;
          Pooler.execute(new Runnable()
          {
            public void run()
            {
              try
              {
                sendSetProperty("DEFAULT_VIDEO_ADVANCED_ASPECT", advancedARsFull[arnum]);
              }
              catch (java.io.IOException e)
              {
                if (Sage.DBG) System.out.println("ERROR setting new default advanced AR:" + e);
              }
            }
          });
          if (Sage.DBG) System.out.println("Using replacement default advanced AR of:" + defaultAdvancedAR);
          break;
        }
      }
    }
    currAdvancedAR = defaultAdvancedAR;
    for (int i = 0; advancedARNames != null && i < advancedARNames.length; i++)
      if (advancedARNames[i].equals(currAdvancedAR))
      {
        currAdvancedARDetails = advancedARsFull[i];
        break;
      }
  }

  public String getAdvancedAspectRatioMode()
  {
    return currAdvancedAR;
  }

  public void loadAdvancedARs()
  {
    if (originalAdvancedARList != null && originalAdvancedARList.length() > 0)
    {
      lastLoadedAdvancedARProp = uiMgr.get("advanced_aspect_ratio_extra_modes", "");
      java.util.StringTokenizer toker = new java.util.StringTokenizer(originalAdvancedARList + ";" +
          lastLoadedAdvancedARProp, ";");
      String[] newadvancedARNames = new String[toker.countTokens()];
      String[] newadvancedARsFull = new String[newadvancedARNames.length];
      for (int i = 0; i < newadvancedARNames.length; i++)
      {
        newadvancedARsFull[i] = toker.nextToken();
        newadvancedARNames[i] = getBarPrefix(newadvancedARsFull[i]);
      }
      advancedARNames = newadvancedARNames;
      advancedARsFull = newadvancedARsFull;
    }
  }

  public long getLastPixelRenderCount() { return lastPixelRenderCount; }
  public long getLastPixelInputCount() { return lastPixelInputCount; }

  public boolean supportsAdvancedDeinterlacing()
  {
    return supportsAdvDeinterlacing;
  }

  public boolean isAdvancedDeinterlacingEnabled()
  {
    return supportsAdvDeinterlacing && advDeinterlacingEnabled;
  }

  public void setAdvancedDeinterlacing(boolean x)
  {
    if (advDeinterlacingEnabled != x && supportsAdvDeinterlacing)
    {
      advDeinterlacingEnabled = x;
      try
      {
        sendSetProperty("ADVANCED_DEINTERLACING", advDeinterlacingEnabled ? "TRUE" : "FALSE");
      }
      catch (Exception e){}
      uiMgr.putBoolean("advanced_deinterlacing", x);
    }
  }

  public String getHDMIConnectorAutodetect()
  {
    return hdmiAutodetectedConnector;
  }

  public String getRemoteVersion()
  {
    return remoteVersion;
  }

  public static String getBarPrefix(String s)
  {
    if (s == null) return s;
    int i = s.indexOf('|');
    if (i == -1)
      return s;
    else
      return s.substring(0, i);
  }

  // Returns 0 if they're not supported on this renderer currently
  public int getHighResolutionSurfaceDimension()
  {
    return (highResSurfCaps == YUV_CACHE_UNIFIED ||
        (highResSurfCaps == YUV_CACHE_SEPARATE && !uiMgr.getVideoFrame().isLoadingOrLoadedFile())) ? HI_RES_SURFACE_SIZE_LIMIT : 0;
  }

  public boolean gfxPositionedVideo()
  {
    return gfxVideoPropsSupportMask != 0;
  }

  // Remote Filesystem Stuff

  public boolean hasRemoteFSSupport()
  {
    return remoteFSSupport;
  }

  public synchronized int fsCreateDirectory(String pathName)
  {
    if (!remoteFSSupport) return 0;
    if (Sage.DBG && DEBUG_REMOTEFS) System.out.println("fsCreateDirectory(" + pathName + ")");
    try
    {
      byte[] pathBytes = pathName.getBytes("UTF-8");
      prepFSCmdHeader(6 + pathBytes.length);
      sockBuf.putInt(FSCMD_CREATE_DIRECTORY << CMD_LEN_BITS | (2 + pathBytes.length));
      sockBuf.putShort((short)pathBytes.length);
      sockBuf.put(pathBytes);
      sendBufferNow();
      return recvr.getIntReply();
    }catch(Exception e)
    {
      killUIMgrAsync();
      if (Sage.DBG) System.out.println("MiniUI FS error:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return FS_RV_ERROR_UNKNOWN;
  }

  public synchronized int fsGetPathAttributes(String pathName)
  {
    if (!remoteFSSupport) return 0;
    if (Sage.DBG && DEBUG_REMOTEFS) System.out.println("fsGetPathAttributes(" + pathName + ")");
    try
    {
      byte[] pathBytes = pathName.getBytes("UTF-8");
      prepFSCmdHeader(6 + pathBytes.length);
      sockBuf.putInt(FSCMD_GET_PATH_ATTRIBUTES << CMD_LEN_BITS | (2 + pathBytes.length));
      sockBuf.putShort((short)pathBytes.length);
      sockBuf.put(pathBytes);
      sendBufferNow();
      return recvr.getIntReply();
    }catch(Exception e)
    {
      killUIMgrAsync();
      if (Sage.DBG) System.out.println("MiniUI FS error:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return 0;
  }

  public synchronized long fsGetFileSize(String pathName)
  {
    if (!remoteFSSupport) return 0;
    if (Sage.DBG && DEBUG_REMOTEFS) System.out.println("fsGetFileSize(" + pathName + ")");
    try
    {
      byte[] pathBytes = pathName.getBytes("UTF-8");
      prepFSCmdHeader(6 + pathBytes.length);
      sockBuf.putInt(FSCMD_GET_FILE_SIZE << CMD_LEN_BITS | (2 + pathBytes.length));
      sockBuf.putShort((short)pathBytes.length);
      sockBuf.put(pathBytes);
      sendBufferNow();
      return recvr.getLongReply();
    }catch(Exception e)
    {
      killUIMgrAsync();
      if (Sage.DBG) System.out.println("MiniUI FS error:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return 0;
  }

  public synchronized long fsGetPathModified(String pathName)
  {
    if (!remoteFSSupport) return 0;
    if (Sage.DBG && DEBUG_REMOTEFS) System.out.println("fsGetPathModified(" + pathName + ")");
    try
    {
      byte[] pathBytes = pathName.getBytes("UTF-8");
      prepFSCmdHeader(6 + pathBytes.length);
      sockBuf.putInt(FSCMD_GET_PATH_MODIFIED_TIME << CMD_LEN_BITS | (2 + pathBytes.length));
      sockBuf.putShort((short)pathBytes.length);
      sockBuf.put(pathBytes);
      sendBufferNow();
      return recvr.getLongReply();
    }catch(Exception e)
    {
      killUIMgrAsync();
      if (Sage.DBG) System.out.println("MiniUI FS error:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return 0;
  }

  public synchronized boolean fsDeletePath(String pathName)
  {
    if (!remoteFSSupport) return false;
    if (Sage.DBG && DEBUG_REMOTEFS) System.out.println("fsDeletePath(" + pathName + ")");
    try
    {
      byte[] pathBytes = pathName.getBytes("UTF-8");
      prepFSCmdHeader(6 + pathBytes.length);
      sockBuf.putInt(FSCMD_DELETE_FILE << CMD_LEN_BITS | (2 + pathBytes.length));
      sockBuf.putShort((short)pathBytes.length);
      sockBuf.put(pathBytes);
      sendBufferNow();
      return recvr.getIntReply() == 0;
    }catch(Exception e)
    {
      killUIMgrAsync();
      if (Sage.DBG) System.out.println("MiniUI FS error:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return false;
  }

  public synchronized String[] fsDirListing(String pathName)
  {
    if (!remoteFSSupport) return null;
    if (Sage.DBG && DEBUG_REMOTEFS) System.out.println("fsDirListing(" + pathName + ")");
    if (!pathName.endsWith("\\") && !pathName.endsWith("/"))
      pathName = pathName + "/";
    try
    {
      byte[] pathBytes = pathName.getBytes("UTF-8");
      prepFSCmdHeader(6 + pathBytes.length);
      sockBuf.putInt(FSCMD_DIR_LIST << CMD_LEN_BITS | (2 + pathBytes.length));
      sockBuf.putShort((short)pathBytes.length);
      sockBuf.put(pathBytes);
      sendBufferNow();
      ReplyPacket myReply = recvr.getReply();
      // Check if it was a variable length return which means the result is in the packet data
      if (myReply.getDataStringList() != null)
      {
        String[] rv = myReply.getDataStringList();
        for (int i = 0; rv != null && i < rv.length; i++)
          rv[i] = pathName + rv[i];
        return rv;
      }
      byte[] retData = myReply.getData();
      int numEntries = ((retData[0] & 0xFF) << 8) | (retData[1] & 0xFF);
      int offset = 2;
      String[] rv = new String[numEntries];
      for (int i = 0; i < numEntries; i++)
      {
        int currSize = ((retData[offset] & 0xFF) << 8) | (retData[offset + 1] & 0xFF);
        rv[i] = pathName + new String(retData, offset + 2, currSize, "UTF-8");
        offset += 2 + currSize;
      }
      return rv;
    }catch(Exception e)
    {
      killUIMgrAsync();
      if (Sage.DBG) System.out.println("MiniUI FS error:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return null;
  }

  public synchronized String[] fsGetRoots()
  {
    if (!remoteFSSupport) return null;
    if (Sage.DBG && DEBUG_REMOTEFS) System.out.println("fsGetRoots()");
    try
    {
      prepFSCmdHeader(4);
      sockBuf.putInt(FSCMD_LIST_ROOTS << CMD_LEN_BITS | 0);
      sendBufferNow();
      ReplyPacket myReply = recvr.getReply();
      // Check if it was a variable length return which means the result is in the packet data
      if (myReply.getDataStringList() != null)
        return myReply.getDataStringList();
      byte[] retData = myReply.getData();
      int numEntries = ((retData[0] & 0xFF) << 8) | (retData[1] & 0xFF);
      int offset = 2;
      String[] rv = new String[numEntries];
      for (int i = 0; i < numEntries; i++)
      {
        int currSize = ((retData[offset] & 0xFF) << 8) | (retData[offset + 1] & 0xFF);
        rv[i] = new String(retData, offset + 2, currSize, "UTF-8");
        offset += 2 + currSize;
      }
      return rv;
    }catch(Exception e)
    {
      killUIMgrAsync();
      if (Sage.DBG) System.out.println("MiniUI FS error:" + e);
      if (Sage.DBG) Sage.printStackTrace(e);
    }
    return null;
  }

  public RemoteFSXfer fsDownloadFile(java.io.File localFile, String remotePath)
  {
    return fsDownloadFile(localFile, 0, localFile.length(), remotePath);
  }
  public synchronized RemoteFSXfer fsDownloadFile(java.io.File localFile, long offset, long size, String remotePath)
  {
    if (Sage.DBG && DEBUG_REMOTEFS) System.out.println("fsDownloadFile(" + localFile + ", " + remotePath + ")");
    int secureKey = (int)Math.round(java.lang.Math.random() * Integer.MAX_VALUE);
    RemoteFSXfer rv = new RemoteFSXfer(secureKey, localFile, remotePath, offset, size, true);
    if (remoteFSSupport)
    {
      try
      {
        byte[] pathBytes = remotePath.getBytes("UTF-8");
        authFSXfers.put(new Integer(secureKey), rv);
        prepFSCmdHeader(26 + pathBytes.length);
        sockBuf.putInt(FSCMD_DOWNLOAD_FILE << CMD_LEN_BITS | (22 + pathBytes.length));
        sockBuf.putInt(secureKey);
        sockBuf.putLong(offset);
        sockBuf.putLong(size);
        sockBuf.putShort((short)pathBytes.length);
        sockBuf.put(pathBytes);
        sendBufferNow();
        rv.error = recvr.getIntReply();
        return rv;
      }catch(Exception e)
      {
        killUIMgrAsync();
        if (Sage.DBG) System.out.println("MiniUI FS error:" + e);
        if (Sage.DBG) Sage.printStackTrace(e);
      }
    }
    rv.error = FS_RV_ERROR_UNKNOWN;
    return rv;
  }

  public synchronized RemoteFSXfer fsUploadFile(java.io.File localFile, String remotePath)
  {
    return fsUploadFile(localFile, 0, fsGetFileSize(remotePath), remotePath);
  }
  public synchronized RemoteFSXfer fsUploadFile(java.io.File localFile, long offset, long size, String remotePath)
  {
    if (Sage.DBG && DEBUG_REMOTEFS) System.out.println("fsUploadFile(" + localFile + ", " + remotePath + ")");
    int secureKey = (int)Math.round(java.lang.Math.random() * Integer.MAX_VALUE);
    RemoteFSXfer rv = new RemoteFSXfer(secureKey, localFile, remotePath, offset, size, false);
    if (remoteFSSupport)
    {
      try
      {
        byte[] pathBytes = remotePath.getBytes("UTF-8");
        authFSXfers.put(new Integer(secureKey), rv);
        prepFSCmdHeader(26 + pathBytes.length);
        sockBuf.putInt(FSCMD_UPLOAD_FILE << CMD_LEN_BITS | (22 + pathBytes.length));
        sockBuf.putInt(secureKey);
        sockBuf.putLong(offset);
        sockBuf.putLong(size);
        sockBuf.putShort((short)pathBytes.length);
        sockBuf.put(pathBytes);
        sendBufferNow();
        rv.error = recvr.getIntReply();
        return rv;
      }catch(Exception e)
      {
        killUIMgrAsync();
        if (Sage.DBG) System.out.println("MiniUI FS error:" + e);
        if (Sage.DBG) Sage.printStackTrace(e);
      }
    }
    rv.error = FS_RV_ERROR_UNKNOWN;
    return rv;
  }

  private void processFSSocketConn(java.nio.channels.SocketChannel sake)
  {
    try
    {
      // Get the secureID to see what's supposed to happen now
      java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(4);
      bb.clear();
      TimeoutHandler.registerTimeout(timeout, sake);
      while (bb.hasRemaining())
      {
        int x = sake.read(bb);
        if (x < 0)
          throw new java.io.EOFException();
      }
      TimeoutHandler.clearTimeout(sake);
      int secureID = ((bb.get(0) & 0xFF) << 24) | ((bb.get(1) & 0xFF) << 16) | ((bb.get(2) & 0xFF) << 8) | (bb.get(3) & 0xFF);
      if (authFSXfers.containsKey(new Integer(secureID)))
        performAsyncFSOp(secureID, sake);
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("Error w/ remote FS connection:" + e);
      if (sake != null)
        try{sake.close();}catch(Exception e1){}
    }
    finally
    {
      TimeoutHandler.clearTimeout(sake);
    }
  }

  private void performAsyncFSOp(final int secureID, final java.nio.channels.SocketChannel sake)
  {
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        java.nio.channels.FileChannel localRaf = null;
        RemoteFSXfer xferData = (RemoteFSXfer) authFSXfers.remove(new Integer(secureID));
        if (Sage.DBG) System.out.println("Performing Remote FS op for:" + xferData);
        // If we're creating a file on the server then delete it in the fail/abort case
        boolean delIfFail = !xferData.download && !xferData.localFile.isFile();
        java.nio.ByteBuffer hackBuf = null;
        try
        {
          localRaf = xferData.download ? new java.io.FileInputStream(xferData.localFile).getChannel() :
            new java.io.FileOutputStream(xferData.localFile).getChannel();
          long xferSize = xferData.size;
          while (xferSize > 0 && !xferData.abort)
          {
            long currSize = Math.min(xferSize, 16384);
            // Increase the connection timeout for this operation to deal with low bandwidth connections
            TimeoutHandler.registerTimeout(timeout * 10, sake);
            if (Sage.LINUX_OS && xferData.offset + xferData.progress >= Integer.MAX_VALUE)
            {
              // NOTE: Due to Java using the sendfile kernel API call to do the transfer,
              // there's a 32-bit limitation here. This is very unfortunate. But since it's
              // compiled into the JVM and into the Linux kernel there's really no other way around this
              // We also don't use them by default on Windows because of poor performance w/ network shares
              if (hackBuf == null)
              {
                hackBuf = java.nio.ByteBuffer.allocateDirect(65536);
              }
              hackBuf.clear();
              hackBuf.limit((int)Math.min(currSize, hackBuf.capacity()));
              if (xferData.download)
              {
                currSize = localRaf.read(hackBuf, xferData.offset + xferData.progress);
                hackBuf.flip();
                while (hackBuf.hasRemaining())
                  sake.write(hackBuf);
              }
              else
              {
                currSize = sake.read(hackBuf);
                hackBuf.flip();
                while (hackBuf.hasRemaining())
                  localRaf.write(hackBuf);
              }
            }
            else
            {
              if (xferData.download)
              {
                currSize = localRaf.transferTo(xferData.offset + xferData.progress, currSize, sake);
              }
              else
              {
                currSize = localRaf.transferFrom(sake, xferData.offset + xferData.progress, currSize);
              }
            }
            TimeoutHandler.clearTimeout(sake);
            xferSize -= currSize;
            xferData.progress += currSize;
            //System.out.println("Progress bytes " + xferData.progress);
          }
          if (xferData.download)
          {
            // Now read back the 32-bit RV for the transfer so we know the client has written it to disk completely,
            // only the bottom byte will matter
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(4);
            bb.clear();
            TimeoutHandler.registerTimeout(timeout, sake);
            while (bb.hasRemaining())
            {
              int x = sake.read(bb);
              if (x < 0)
                throw new java.io.EOFException();
            }
            TimeoutHandler.clearTimeout(sake);
            bb.flip();
            int rv = bb.get() | bb.get() | bb.get() | bb.get();
            xferData.error = rv;
          }
          if (Sage.DBG) System.out.println("Finished Remote FS op:" + xferData);
          if (xferData.abort && delIfFail)
          {
            xferData.localFile.delete();
            if (Sage.DBG) System.out.println("Deleting local file because remote FS xfert was aborted!");
          }
        }
        catch (Exception e)
        {
          if (Sage.DBG) System.out.println("ERROR in remote FS interaction:" + xferData + " err=" + e);;
          if (delIfFail)
            xferData.localFile.delete();
          xferData.error = FS_RV_ERROR_UNKNOWN;
        }
        finally
        {
          TimeoutHandler.clearTimeout(sake);
          if (sake != null)
            try{sake.close();}catch(Exception e1){}
          if (localRaf != null)
            try{localRaf.close();}catch(Exception e1){}
        }
      }
    }, "RemoteFSAsync", Thread.MIN_PRIORITY);
  }

  public void setWaitIndicatorState(boolean x)
  {
    if (waitCursorSupport)
    {
      if (waitIndicatorState && !x)
      {
        // Turn off the wait indicator
        setWaitCursor(GFX_CURSOR_FLAG_STATE, 0, 0, GFX_CURSOR_STATE_AUTO, 0, 0, null);
      }
      else if (waitIndicatorState && x)
      {
        // Increment the index of the wait cursor
        waitIndicatorRopsIndex++;
        waitIndicatorRopsIndex = waitIndicatorRopsIndex % waitCursorData.length;
        setWaitCursor(GFX_CURSOR_FLAG_STATE | GFX_CURSOR_FLAG_POS | GFX_CURSOR_FLAG_DATA, master.getWidth()/2 - waitCursorWidth/2,
            master.getHeight()/2 - waitCursorHeight/2, GFX_CURSOR_STATE_ON, waitCursorWidth, waitCursorHeight, waitCursorData[waitIndicatorRopsIndex]);
      }
      else if (!waitIndicatorState && x)
      {
        // Turn on the initial wait indicator
        waitIndicatorRopsIndex = 0;
        setWaitCursor(GFX_CURSOR_FLAG_STATE | GFX_CURSOR_FLAG_POS | GFX_CURSOR_FLAG_DATA, master.getWidth()/2 - waitCursorWidth/2,
            master.getHeight()/2 - waitCursorHeight/2, GFX_CURSOR_STATE_ON, waitCursorWidth, waitCursorHeight, waitCursorData[waitIndicatorRopsIndex]);
      }
      waitIndicatorState = x;
    }
    else
      waitIndicatorState = x;
  }

  private VideoFrame vf;

  private boolean useImageMapsForText = true;
  private boolean remotelyTransferFonts = false;
  private boolean allowRawImageTransfer = true;
  private boolean allow8bppFontTransfer = false;
  private String compImageFormat = null;
  private boolean allowCompImageTransfer;
  private boolean allowDirectImageLoading = false;
  private boolean colorKeying;
  private boolean partialUpdatesOK;
  private java.awt.Color remoteColorKey;
  private boolean cryptoSupport;
  private Boolean nextEventCryptoState;
  private boolean surfaceSupport = false;
  private boolean canDoScalingImageDecode = false;
  private boolean diffuseTextureSupport = false;
  private boolean xformRenderSupport = false;

  private byte[] encodedPublicKeyBytes;
  private java.security.PrivateKey privateKey;

  private javax.crypto.Cipher evtDecryptCipher;
  private java.security.Key mySecretKey;

  private long estimatedBandwidthBytes;
  private long estimatedBandwidthTime;

  private java.util.Set videoCodecs;
  private java.util.Set audioCodecs;
  private java.util.Set pullContainers;
  private java.util.Set pushContainers;
  private java.util.Set streamingProtocols;
  private String fixedPushMediaFormatProp;
  private boolean detailedPushBufferStats;
  private boolean pushBufferSeeking;

  private java.awt.Dimension maxClientResolution;
  private sage.media.format.VideoFormat displayResolution;
  private Boolean fullScreenChange;
  private String remoteResolutionChange;
  private final Object remoteResolutionChangeLock = new Object();
  private boolean remoteFullScreenMode;
  private String[] resolutionOptions;
  private sage.media.format.VideoFormat[] resolutionFormats;
  private java.util.Map resolutionNameMap = new java.util.HashMap();
  private String[] preferredResolutions; // detected over digital connectors

  private String[] audioOutputOptions;
  private String currAudioOutput;
  private String audioOutputChange;

  private String appModeChange;

  private String[] advancedARsFull;
  private String[] advancedARNames;
  private String currAdvancedAR;
  private String currAdvancedARDetails;
  private String defaultAdvancedAR; // gets reset to this each time a new file is loaded
  private String advancedARChange;
  private String originalAdvancedARList;
  private String lastLoadedAdvancedARProp;

  private String hdmiAutodetectedConnector;
  private String remoteVersion = "";
  private boolean remoteWindowedSystem;
  private boolean supportsForcedMediaReconnect;

  private String inputDevsProp;

  private boolean refreshVideo;

  private boolean localConnection;
  private boolean loopbackConnection;

  private int gfxScalingCaps;
  private boolean hasOfflineCache;

  private long mediaPlayerDelay;

  private int maxTextureDim = 1024;

  private MiniUIClientReceiver recvr;

  private ThreadLocal nativeImgBuffTL = new ThreadLocal();

  // last display aspect ratio we sent to the client
  private float lastSetDAR;

  private boolean aliveAnims;

  protected long currSurface;
  private java.util.Stack surfStack = new java.util.Stack();
  private java.util.ArrayList dlThatWasComposited;
  private int numSurfsCreated;
  private java.util.Map animsThatGoAfterSurfPop = new java.util.HashMap();
  private java.util.Map surfaceDimensions = new java.util.HashMap();

  // Each element is [MetaImage, imageIndex]
  private java.util.Vector highResSurfaceData = new java.util.Vector();
  // Each element is handle and corresponds to the above Vector
  private java.util.Vector highResSurfaceHandles = new java.util.Vector();

  // Set to true when we receive an event from the client that the output modes may have changed
  private boolean updateOutputModeInfo;

  private int highResSurfCaps;
  private int gfxVideoPropsSupportMask = 0;
  private long lastUsedHighResSurf = 0;
  private long pendingHiResSurfHandleToSet = 0;
  private int[] pendingVidSurfCoords = null;
  private java.awt.Rectangle lastHiResSurfDest = new java.awt.Rectangle();

  private byte[] fontXferBuf;
  private java.awt.Rectangle currEffectClip;
  private javax.vecmath.Matrix4f currXform;
  private java.util.ArrayList virginsToFix = new java.util.ArrayList();
  private long renderStartTime;

  private boolean subtitleSupport = false;

  private boolean clientCanDoAuth;
  private String pendingClientAuthSet;

  private boolean remoteFSSupport;
  private java.util.Map authFSXfers = new java.util.HashMap();

  private int pushBufferLimit;
  private long timeout;
  private boolean zipSocks;
  private com.jcraft.jzlib.ZOutputStream zout;

  private int textureBatchLimit;
  private int textureBatchCount;
  private int textureBatchSize;

  private boolean supportsAdvDeinterlacing;
  private boolean advDeinterlacingEnabled;
  private boolean needsInitDriver;
  private boolean frameStepSupport;
  private byte[][] waitCursorData;
  private int waitCursorWidth;
  private int waitCursorHeight;

  private boolean bufferFlipping;
  private boolean screenshotSupport;
  private long lastPixelRenderCount;
  private long lastPixelInputCount;

  private boolean advImageCaching;
  private java.util.Set clientOfflineCache = new java.util.HashSet();
  private int clientHandleCounter = 1000000;
  private Object clientHandleCounterLock = new Object();

  private java.util.Stack effectStack = new java.util.Stack();
  private float currEffectAlpha;
  private javax.vecmath.Matrix4f currProjXform;
  private javax.vecmath.Matrix4f tempMat = new javax.vecmath.Matrix4f();
  private java.awt.Rectangle baseClip;
  private boolean effectAnimationsActive;
  private boolean effectAnimationsLocked;
  private boolean usedHiResSurf;
  private RenderingOp currSurfHiResImageOp;
  private String lastHiResImageLayer;
  private long effectCacheSurf;
  private java.awt.Dimension effectCacheDims;
  private int effectCacheOpCount;
  private boolean effectSurfCacheActive = false;
  private long effectSurfPixelCacheRenderCount;
  private long effectSurfCacheHighResSurf = 0;
  private java.util.HashMap asyncLoadConfirms = new java.util.HashMap();

  private java.awt.Rectangle cacheRect1 = new java.awt.Rectangle();
  private java.awt.Rectangle cacheRect2 = new java.awt.Rectangle();

  private boolean iPhoneMode;

  @Override
  public void setMenuHint(String menuName, String popupName, boolean hasTextInput)
  {
    synchronized (menuHint) {
      menuHint.menuName = menuName;
      menuHint.popupName = popupName;
      menuHint.hasTextInput = hasTextInput;
    }
  }

  private TextSurfaceCache getCachedTextSurfacePtr(RenderingOp op)
  {
    // Media extenders on a LAN don't need this optimization because its faster to do it by rendering everything;
    // ideally we'd optimize the placeshifter client to behave the same way as the extender does with handling this optimization.
    // And if its non-local we don't want to use this either since it adds a lot of round-trip activity to rendering which then
    // means latency affects rendering speed a lot more.
    // The OpenGL PS client has weird problems with string caching as well; so we disable that (HW Accel & no xform support indicates OpenGL client)
    if (advImageCaching || isMediaExtender() || !isLocalConnection() || uiMgr.areLayersEnabled() || !surfaceSupport || ((gfxScalingCaps & GFX_SCALING_HW) != 0 && !xformRenderSupport)) return null;
    String key = op.text.string + "-0x" + Integer.toString(op.renderColor.getRGB(), 16) + op.text.font;
    //System.out.println("Trying to look up cached text for key=" + key);
    TextSurfaceCache rv = (TextSurfaceCache) textCacheMap.get(key);
    if (rv != null)
    {
      //System.out.println("Returning cached text surface");
      rv.lastUsed = Sage.eventTime();
      return rv;
    }
    if (op.text.fontImage == null || op.text.renderRectCache == null)
    {
      System.out.println("Cannot create cached text surface; don't have cached rect values to use");
      return null;
    }
    java.awt.Rectangle srcRect = new java.awt.Rectangle();
    /*		float maxX = 0;
		float maxY = 0;
		for (int i = 0; i < op.text.renderImageNumCache.length; i++)
		{
			if (op.text.renderGlyphRectCache[i] != null)
			{
				maxY = Math.max(maxY, op.text.renderRectCache[2*i + 1] + op.text.renderGlyphRectCache[i].height);
				maxX = Math.max(maxX, op.text.renderRectCache[2*i] + op.text.renderGlyphRectCache[i].width);
			}
		}
		srcRect.width = (int) Math.ceil(maxY);
		srcRect.height = (int) Math.ceil(maxY);*/
    srcRect.setFrame(2, 2, op.copyImageRect.width, op.copyImageRect.height);
    int surfMemSize = srcRect.width * srcRect.height * 4;
    // NARFLEX: We need to hold the NiaCacheLock for this whole operation or we'll deadlock because you're not allowed to sync
    // on the MCSR and then on the NIA lock; which is exactly what we're doing below. Because the opposite of that will definitely
    // occur when you're managing and purging the cache to make room for a new load.
    synchronized (MetaImage.getNiaCacheLock(this))
    {
      while (textCacheSize + surfMemSize > 8*1024*1024)
      {
        if (!releaseOldestTextCache())
          return null;
      }
      rv = new TextSurfaceCache();
      rv.srcRect = srcRect;
      rv.key = key;
      rv.color = op.renderColor;
      rv.text = op.text.string;
      rv.lastUsed = Sage.eventTime();
      rv.size = surfMemSize;
      while((rv.handle = createSurfaceMini(srcRect.width + 4, srcRect.height + 4)) == 0)
      {
        Object[] oldestImage =
            MetaImage.getLeastRecentlyUsedImage(this, null, 0);
        if (oldestImage == null)
        {
          if (!releaseOldestTextCache())
            return null;
        }
        else
        {
          ((MetaImage) oldestImage[0]).clearNativePointer(this,
              ((Integer) oldestImage[1]).intValue());
        }
      }
      //System.out.println("Created surface for text caching & rendering it now");
      textCacheSize += rv.size;
      // Now we have the surface; draw the text on it to create our cache
      int numFontImages = op.text.fontImage.getNumImages();
      int numGlyphs = op.text.renderImageNumCache.length;
      int offX, offY, offW, offH; // adjustments for clipping
      synchronized (this)
      {
        if (xformRenderSupport)
          pushTransform(null);
        setTargetSurfaceMini(rv.handle);
        clearRectMini(0, 0, srcRect.width + 4, srcRect.height + 4, 0, 0, 0, 0);
        for(int j = 0; j < numFontImages; j++)
        {
          long texturePtr = -1;
          int rectCoordIndex = 0;
          for(int k = 0; k < numGlyphs; k++)
          {
            if(op.text.renderImageNumCache[k] == j)
            {
              if(texturePtr == -1)
                texturePtr = op.text.fontImage.getNativeImage(this, j);

              rectCoordIndex++;
              drawTexturedRectMini(
                  (int)op.text.renderRectCache[2*k] + 2,
                  (int)op.text.renderRectCache[2*k+1] + 2,
                  // If this is a negative value, it indicates that there is a blending color
                  // for the text.
                  -(int)(op.text.renderGlyphRectCache[k].width + FLOAT_ERROR),
                  (int)(op.text.renderGlyphRectCache[k].height + FLOAT_ERROR),
                  texturePtr,
                  (int)op.text.renderGlyphRectCache[k].x,
                  (int)op.text.renderGlyphRectCache[k].y,
                  (int)(op.text.renderGlyphRectCache[k].width + FLOAT_ERROR),
                  (int)(op.text.renderGlyphRectCache[k].height + FLOAT_ERROR),
                  getCompositedColor(op.renderColor, 1), false);
            }
          }
          if (rectCoordIndex > 0)
          {
            op.text.fontImage.removeNativeRef(this, j);
          }
        }
        setTargetSurfaceMini(effectSurfCacheActive ? effectCacheSurf : 0);
        if (xformRenderSupport)
          popTransform();
      }
    }
    textCacheMap.put(key, rv);
    return rv;
  }

  // Returns false if there's nothing to release
  private boolean releaseOldestTextCache()
  {
    if (textCacheMap.isEmpty())
    {
      System.out.println("UNABLE to release text cache surface since its empty");
      return false;
    }
    TextSurfaceCache oldest = null;
    java.util.Iterator walker = textCacheMap.values().iterator();
    while (walker.hasNext())
    {
      TextSurfaceCache texty = (TextSurfaceCache) walker.next();
      if (oldest == null || oldest.lastUsed > texty.lastUsed)
        oldest = texty;
    }
    releaseNativeImage(oldest.handle);
    textCacheSize -= oldest.size;
    textCacheMap.remove(oldest.key);
    return true;
  }

  // This runs as a thread which does all of the receiving from the UI socket channel.
  // If it gets events, then it sends them directly to the EventRouter. If it gets replies
  // then it puts them into its reply queue and gives them out whenever a reply is requested.
  private class MiniUIClientReceiver extends Thread
  {
    public MiniUIClientReceiver(java.nio.channels.ReadableByteChannel inDis)
    {
      super("MiniUIClientReceiver");
      dis = inDis;
      replyQueue = new java.util.Vector();
      alive = true;
      setDaemon(true);
      start();
    }
    private int read3ByteInt() throws java.io.IOException
    {
      int a1 = recvBuf.getShort() & 0xFFFF;
      int a2 = recvBuf.get() & 0xFF;
      if (a2 < 0) throw new java.io.EOFException();
      return ((a1 & 0xFFFF) << 8) | (a2 & 0xFF);
    }
    public void setNewStream(java.nio.channels.ReadableByteChannel inDis)
    {
      dis = inDis;
    }
    public void run()
    {
      byte[] strReadBuf = null;
      while (alive)
      {
        try
        {
          recvBuf.clear().limit(16); // the header
          do
          {
            int x = dis.read(recvBuf);
            if (x == -1)
              throw new java.io.EOFException();
          }while (recvBuf.remaining() > 0);
          recvBuf.flip();
          int replyType = recvBuf.get() & 0xFF;
          if (replyType == -1)
          {
            throw new java.io.EOFException();
          }
          int dataLen = read3ByteInt();
          long timestamp = recvBuf.getInt() & 0xFFFFFFFF;
          int id = recvBuf.getInt() & 0xFFFFFFFF;
          recvBuf.getInt(); // padding, 4 bytes
          if (recvBuf.capacity() < dataLen + 24)
            recvBuf = java.nio.ByteBuffer.allocate(dataLen * 2);
          byte[] replyData = null;
          if (encryptionOn && dataLen > 0)
          {
            // Read the encrypted data plus padding and then decrypt it
            if (decryptBuff == null)
              decryptBuff = new byte[512];
            // Blowfish & DES uses 64-bit block sizes so align on that. They also require a pad (which is why we add 8 instead of 7)
            recvBuf.clear().limit((dataLen + 8) & 0xFFFF8);
            do{
              int x = dis.read(recvBuf);
              if (x < 0)
                throw new java.io.EOFException();
            }while(recvBuf.remaining() > 0);
            recvBuf.flip();
            recvBuf.get(decryptBuff, 0, recvBuf.limit());
            byte[] theData = evtDecryptCipher.doFinal(decryptBuff, 0, (dataLen + 8) & 0xFFFF8);
            if (theData.length == dataLen)
              replyData = theData;
            else
            {
              replyData = new byte[dataLen];
              System.arraycopy(theData, 0, replyData, 0, dataLen);
            }
          }
          if (replyType < 128)
          {
            // Reply packet
            if (dataLen > 0 && replyData == null)
            {
              replyData = new byte[dataLen];
              recvBuf.clear().limit(dataLen);
              do{
                int x = dis.read(recvBuf);
                if (x < 0)
                  throw new java.io.EOFException();
              }while(recvBuf.remaining() > 0);
              recvBuf.flip();
              recvBuf.get(replyData);
            }
            ReplyPacket newReply = new ReplyPacket(replyType, timestamp, id, replyData);
            if (replyType == FS_CMD_TYPE && dataLen == 2 && (replyData[0] & 0xFF) == 0xFF && (replyData[1] & 0xFF) == 0xFF)
            {
              // This is a special reply for a directory listing where it's variable length. In this case we read 2 bytes
              // for the # of bytes, and then a UTF-8 byte string. Then repeat until the 2 byte length is zero. Put the
              // resulting list of strings in the reply packet as the 'stringList'
              recvBuf.clear().limit(2);
              do{
                int x = dis.read(recvBuf);
                if (x < 0)
                  throw new java.io.EOFException();
              }while(recvBuf.remaining() > 0);
              recvBuf.flip();
              int currSize = recvBuf.getShort() & 0xFFFF;
              java.util.ArrayList stringListVec = new java.util.ArrayList();
              if (strReadBuf == null)
                strReadBuf = new byte[65536];
              java.nio.ByteBuffer strBB = java.nio.ByteBuffer.wrap(strReadBuf);
              while (currSize != 0)
              {
                strBB.clear().limit(currSize + 2);
                do{
                  int x = dis.read(strBB);
                  if (x < 0)
                    throw new java.io.EOFException();
                }while(strBB.remaining() > 0);
                strBB.flip();
                try
                {
                  stringListVec.add(new String(strReadBuf, 0, currSize, "UTF-8"));
                }
                catch (java.io.UnsupportedEncodingException uee)
                {
                  stringListVec.add(new String(strReadBuf, 0, currSize));
                }
                strBB.position(currSize);
                currSize = strBB.getShort();
              }
              newReply.setStringList((String[]) stringListVec.toArray(Pooler.EMPTY_STRING_ARRAY));
            }
            synchronized (replyQueue)
            {
              if (DEBUG_NATIVE2D) System.out.println("Adding item to replyQueue");
              replyQueue.add(newReply);
              replyQueue.notifyAll();
            }
            if (nextReplyCryptoToggle != null)
            {
              if (replyData.length > 0 && replyData[0] == 0)
              {
                encryptionOn = nextReplyCryptoToggle.booleanValue();
                if (Sage.DBG) System.out.println("MiniClient event encryption state is now=" + encryptionOn);
              }
              else
              {
                if (Sage.DBG) System.out.println("ERROR enabling event encryption with MiniClient!");
              }
              nextReplyCryptoToggle = null;
            }
          }
          else
          {
            if (replyData == null)
            {
              recvBuf.clear().limit(dataLen);
              do{
                int x = dis.read(recvBuf);
                if (x < 0)
                  throw new java.io.EOFException();
              }while(recvBuf.remaining() > 0);
              recvBuf.flip();
            }
            // Event packet
            switch (replyType)
            {
              case IR_EVENT_REPLY_TYPE:
                byte[] irData;
                if (replyData == null)
                {
                  irData = new byte[4];
                  recvBuf.get(irData);
                }
                else
                  irData = replyData;
                uiMgr.getRouter().recvInfrared(irData);
                break;
              case SAGECOMMAND_EVENT_REPLY_TYPE:
                int cmd;
                if (replyData == null)
                {
                  cmd = recvBuf.getInt();
                }
                else
                  cmd = ((replyData[0] & 0xFF) << 24) | ((replyData[1] & 0xFF) << 16) | ((replyData[2] & 0xFF) << 8) | (replyData[3] & 0xFF);
                uiMgr.getRouter().recvCommand(cmd);
                break;
              case KB_EVENT_REPLY_TYPE:
                int keyCode, mods;
                char keyChar;
                if (replyData == null)
                {
                  keyCode = recvBuf.getInt();
                  keyChar = recvBuf.getChar();
                  mods = recvBuf.getInt();
                }
                else
                {
                  keyCode = ((replyData[0] & 0xFF) << 24) | ((replyData[1] & 0xFF) << 16) | ((replyData[2] & 0xFF) << 8) | (replyData[3] & 0xFF);
                  keyChar = (char) (((replyData[4] & 0xFF) << 8) | (replyData[5] & 0xFF));
                  mods = ((replyData[6] & 0xFF) << 24) | ((replyData[7] & 0xFF) << 16) | ((replyData[8] & 0xFF) << 8) | (replyData[9] & 0xFF);
                }
                uiMgr.getRouter().recvKeystroke(keyChar, keyCode, mods);
                break;
              case MPRESS_EVENT_REPLY_TYPE:
              case MRELEASE_EVENT_REPLY_TYPE:
              case MCLICK_EVENT_REPLY_TYPE:
              case MMOVE_EVENT_REPLY_TYPE:
              case MDRAG_EVENT_REPLY_TYPE:
              case MWHEEL_EVENT_REPLY_TYPE:
                int mouseEvtId;
                if (replyType == MPRESS_EVENT_REPLY_TYPE)
                  mouseEvtId = java.awt.event.MouseEvent.MOUSE_PRESSED;
                else if (replyType == MRELEASE_EVENT_REPLY_TYPE)
                  mouseEvtId = java.awt.event.MouseEvent.MOUSE_RELEASED;
                else if (replyType == MCLICK_EVENT_REPLY_TYPE)
                  mouseEvtId = java.awt.event.MouseEvent.MOUSE_CLICKED;
                else if (replyType == MDRAG_EVENT_REPLY_TYPE)
                  mouseEvtId = iPhoneMode ? java.awt.event.MouseEvent.MOUSE_MOVED : java.awt.event.MouseEvent.MOUSE_DRAGGED;
                else if (replyType == MWHEEL_EVENT_REPLY_TYPE)
                  mouseEvtId = java.awt.event.MouseEvent.MOUSE_WHEEL;
                else
                  mouseEvtId = java.awt.event.MouseEvent.MOUSE_MOVED;
                int x, y, theMods, clickCount, button;
                if (replyData == null)
                {
                  x = recvBuf.getInt();
                  y = recvBuf.getInt();
                  theMods = recvBuf.getInt();
                  clickCount = recvBuf.get(); // one SIGNED byte
                  button = recvBuf.get() & 0xFF; // one byte
                }
                else
                {
                  x = ((replyData[0] & 0xFF) << 24) | ((replyData[1] & 0xFF) << 16) | ((replyData[2] & 0xFF) << 8) | (replyData[3] & 0xFF);
                  y = ((replyData[4] & 0xFF) << 24) | ((replyData[5] & 0xFF) << 16) | ((replyData[6] & 0xFF) << 8) | (replyData[7] & 0xFF);
                  theMods = ((replyData[8] & 0xFF) << 24) | ((replyData[9] & 0xFF) << 16) | ((replyData[10] & 0xFF) << 8) | (replyData[11] & 0xFF);
                  clickCount = replyData[12];
                  button = replyData[13];
                }
                uiMgr.getRouter().recvMouse(mouseEvtId, theMods, x, y, clickCount, button);
                break;
              case UI_RESIZE_EVENT_REPLY_TYPE:
                int newWidth, newHeight;
                if (replyData == null)
                {
                  newWidth = recvBuf.getInt();
                  newHeight = recvBuf.getInt();
                }
                else
                {
                  newWidth = ((replyData[0] & 0xFF) << 24) | ((replyData[1] & 0xFF) << 16) | ((replyData[2] & 0xFF) << 8) | (replyData[3] & 0xFF);
                  newHeight = ((replyData[4] & 0xFF) << 24) | ((replyData[5] & 0xFF) << 16) | ((replyData[6] & 0xFF) << 8) | (replyData[7] & 0xFF);
                }
                newWidth = Math.min(newWidth, MAX_WINDOW_SIZE);
                newHeight = Math.min(newHeight, MAX_WINDOW_SIZE);
                if (Sage.DBG) System.out.println("Got UI size update to " + newWidth + "x" + newHeight);
                if (iPhoneMode)
                {
                  maxTextureDim = Math.max(newWidth, newHeight);
                }

                uiMgr.putInt("osd_rendering_width", newWidth);
                uiMgr.putInt(MMC.getInstance().isNTSCVideoFormat() ?
                    "osd_rendering_height_ntsc" : "osd_rendering_height_pal", newHeight);
                uiMgr.setUIResolution(newWidth, newHeight);
                uiMgr.trueValidate();
                break;
              case UI_REPAINT_EVENT_REPLY_TYPE:
                int dirtyX, dirtyY, dirtyW, dirtyH;
                if (replyData == null)
                {
                  dirtyX = recvBuf.getInt();
                  dirtyY = recvBuf.getInt();
                  dirtyW = recvBuf.getInt();
                  dirtyH = recvBuf.getInt();
                }
                else
                {
                  dirtyX = ((replyData[0] & 0xFF) << 24) | ((replyData[1] & 0xFF) << 16) | ((replyData[2] & 0xFF) << 8) | (replyData[3] & 0xFF);
                  dirtyY = ((replyData[4] & 0xFF) << 24) | ((replyData[5] & 0xFF) << 16) | ((replyData[6] & 0xFF) << 8) | (replyData[7] & 0xFF);
                  dirtyW = ((replyData[8] & 0xFF) << 24) | ((replyData[9] & 0xFF) << 16) | ((replyData[10] & 0xFF) << 8) | (replyData[11] & 0xFF);
                  dirtyH = ((replyData[12] & 0xFF) << 24) | ((replyData[13] & 0xFF) << 16) | ((replyData[14] & 0xFF) << 8) | (replyData[15] & 0xFF);
                }
                uiMgr.getRootPanel().appendToDirty(new java.awt.Rectangle(dirtyX, dirtyY, dirtyW, dirtyH));
                break;
              case MEDIA_PLAYER_UPDATE_EVENT_REPLY_TYPE:
                vf.kick();
                break;
              case OUTPUT_MODES_CHANGED_REPLY_TYPE:
                if (Sage.DBG) System.out.println("Server got notification to update the output modes...");
                updateOutputModeInfo = true;
                break;
              case SUBTITLE_UPDATE_REPLY_TYPE:
                int flags = recvBuf.getInt();
                long ptsmsec = (recvBuf.getInt() & 0xFFFFFFFF)/45;
                long durmsec = (recvBuf.getInt() & 0xFFFFFFFF)/45;
                int s2 = recvBuf.getShort();
                byte[] subData = new byte[s2];
                recvBuf.get(subData);
                //String subtext = new String(subData, 0, (s2 > 0 && subData[s2 - 1] == 0) ? (s2 - 1) : s2, Sage.I18N_CHARSET);
                vf.postSubtitleInfo(ptsmsec, durmsec, subData, flags);
                break;
              case ASYNC_DIRECT_LOAD_COMPLETE:
                int handle = recvBuf.getInt();
                int result = recvBuf.getInt();
                asyncLoadConfirms.put(new Long(handle), new Integer(result));
                synchronized (asyncLoadConfirms)
                {
                  asyncLoadConfirms.notifyAll();
                }
                break;
              case REMOTE_FS_HOTPLUG_INSERT_EVENT_REPLY_TYPE:
              case REMOTE_FS_HOTPLUG_REMOVE_EVENT_REPLY_TYPE:
                // Ignore these if we don't support the remote filesystem on that client
                if (remoteFSSupport)
                {
                  int s1 = recvBuf.getShort() & 0xFFFF;
                  byte[] b1 = new byte[s1];
                  recvBuf.get(b1);
                  String newPath = new String(b1);
                  s1 = recvBuf.getShort() & 0xFFFF;
                  b1 = new byte[s1];
                  recvBuf.get(b1);
                  String newDesc = new String(b1);
                  if (Sage.DBG) System.out.println("Received remote hotplug event of " +
                      ((replyType == REMOTE_FS_HOTPLUG_INSERT_EVENT_REPLY_TYPE) ? "insert" : "remove") +
                      " for path " + newPath + " desc=" + newDesc);
                  if (replyType == REMOTE_FS_HOTPLUG_INSERT_EVENT_REPLY_TYPE)
                    Catbert.processUISpecificHook("StorageDeviceAdded", new Object[] { newPath, newDesc }, uiMgr, true);
                }
                break;
              case IMAGE_UNLOAD_REPLY_TYPE:
                int lostHandle;
                if (replyData == null)
                {
                  lostHandle = recvBuf.getInt();
                }
                else
                {
                  lostHandle = ((replyData[0] & 0xFF) << 24) | ((replyData[1] & 0xFF) << 16) | ((replyData[2] & 0xFF) << 8) | (replyData[3] & 0xFF);
                }
                if (Sage.DBG) System.out.println("Server received an image unload reply from the client for handle " + lostHandle);
                synchronized (MetaImage.getNiaCacheLock(MiniClientSageRenderer.this))
                {
                  Object[] matcher = MetaImage.getImageDataForNativeHandle(MiniClientSageRenderer.this, lostHandle);
                  if (matcher != null)
                    ((MetaImage) matcher[0]).clearNativePointer(MiniClientSageRenderer.this, ((Integer) matcher[1]).intValue());
                }
                break;
              case OFFLINE_CACHE_CHANGE_REPLY_TYPE:
                boolean lostImage;
                if (replyData == null)
                {
                  lostImage = recvBuf.get() == 0;
                  int rezLength = recvBuf.getInt();
                  byte[] b1 = new byte[rezLength];
                  recvBuf.get(b1);
                  String rezID = new String(b1);
                  if (lostImage)
                    clientOfflineCache.remove(rezID);
                  else
                    clientOfflineCache.add(rezID);
                }
                else
                {
                  lostImage = replyData[0] == 0;
                  int rezLength = ((replyData[1] & 0xFF) << 24) | ((replyData[2] & 0xFF) << 16) | ((replyData[3] & 0xFF) << 8) | (replyData[4] & 0xFF);
                  String rezID = new String(replyData, 5, rezLength);
                  if (lostImage)
                    clientOfflineCache.remove(rezID);
                  else
                    clientOfflineCache.add(rezID);
                }
                break;
              default:
                if (Sage.DBG) System.out.println("RECEIVED AN UNKNOWN REPLY TYPE FROM THE MINICLIENT OF:" + replyType);
            }
          }
        }
        catch (java.net.SocketTimeoutException e){}
        catch (Exception e1)
        {
          if (Sage.DBG) System.out.println("Exception in the MiniUIClientReceiver of: " + e1);
          if (Sage.DBG) e1.printStackTrace();
          if (connectionError())
          {
            alive = false;
            return;
          }
          else
          {
            synchronized (reconnectLock)
            {
              while (startedReconnectDaemon && !reconnectCompleted)
              {
                try{reconnectLock.wait(1000);}catch(Exception e){}
              }
            }
            if (!reconnectCompleted)
            {
              killUIMgrAsync();
              alive = false;
              return;
            }
          }
        }
      }
    }
    void resetThreadChecker() { singleThread = null; }
    // NOTE: Enforce that only a single thread ever calls the getReply method
    public ReplyPacket getReply()
    {
      // NOTE: I don't believe this is necessary because the transactional nature of the reply is always
      // preserved since all operations are contained within sync blocks (i.e. all requests are made with the sync lock
      // and it is held while the reply is taken; so we're already thread-safe there)
      /*			if (singleThread == null)
				singleThread = Thread.currentThread();
			else if (singleThread != Thread.currentThread())
				throw new InternalError("getReply in MiniUIClientReceiver was called from the wrong thread!! caller thread=" + Thread.currentThread()
					+ " prior thread was=" + singleThread);*/
      synchronized (replyQueue)
      {
        while (intRepliesToDiscard > 0 && !replyQueue.isEmpty())
        {
          replyQueue.remove(0);
          intRepliesToDiscard--;
          if (DEBUG_NATIVE2D) System.out.println("Discarded reply in getReply-1 left=" + intRepliesToDiscard + " queueSize=" + replyQueue.size());
        }
        if (!replyQueue.isEmpty())
        {
          return (ReplyPacket)replyQueue.remove(0);
        }
        long startWait = Sage.eventTime();
        // Don't wait forever; and make it quicker if we're the main UI Server thread so we don't appear dead if the connection
        // is dropped
        long waitMax = (Thread.currentThread() == ssThread) ? 10000 : 60000;
        while (replyQueue.size() <= intRepliesToDiscard && alive && isAlive() && master.isAlive() && Sage.eventTime() - startWait < waitMax)
        {
          try
          {
            replyQueue.wait(5000);
          }
          catch (InterruptedException e)
          {
          }
          while (intRepliesToDiscard > 0 && !replyQueue.isEmpty())
          {
            replyQueue.remove(0);
            intRepliesToDiscard--;
            if (DEBUG_NATIVE2D) System.out.println("Discarded reply in getReply-2 left=" + intRepliesToDiscard + " queueSize=" + replyQueue.size());
          }
        }
        if (alive && replyQueue.isEmpty())
        {
          if (Sage.DBG) System.out.println("ERROR: MiniUIClient receiver timed out waiting for response from the MiniClient!");
        }
        if (DEBUG_NATIVE2D) System.out.println("Returned reply from queue, size before=" + replyQueue.size());
        return alive ? (ReplyPacket)replyQueue.remove(0) : null;
      }
    }
    public int getIntReply()
    {
      ReplyPacket rp = getReply();
      return (rp != null) ? rp.getDataAsInteger() : 0;
    }
    public void discardIntReply()
    {
      synchronized (replyQueue)
      {
        intRepliesToDiscard++;
        if (DEBUG_NATIVE2D) System.out.println("Set replies to discard to be: " + intRepliesToDiscard + " queueSize=" + replyQueue.size());
      }
    }
    public void waitForAllReplies()
    {
      if (intRepliesToDiscard > 0)
      {
        long startWait = Sage.eventTime();
        // Don't wait forever; and make it quicker if we're the main UI Server thread so we don't appear dead if the connection
        // is dropped
        long waitMax = (Thread.currentThread() == ssThread) ? 10000 : 60000;
        synchronized (replyQueue)
        {
          while (intRepliesToDiscard > 0 && alive && isAlive() && master.isAlive() && Sage.eventTime() - startWait < waitMax)
          {
            if (!replyQueue.isEmpty())
            {
              replyQueue.remove(0);
              intRepliesToDiscard--;
              if (DEBUG_NATIVE2D) System.out.println("Discarded reply in waitForAllReplies left=" + intRepliesToDiscard + " queueSize=" + replyQueue.size());
            }
            else
            {
              try
              {
                replyQueue.wait(500);
              }
              catch (InterruptedException e){}
            }
          }
        }
        if (alive && intRepliesToDiscard > 0)
        {
          if (Sage.DBG) System.out.println("ERROR: MiniUIClient receiver(2) timed out waiting for response from the MiniClient!");
          throw new RuntimeException("ERROR: MiniUIClient receiver(2) timed out waiting for response from the MiniClient!");
        }
      }
    }
    public long getLongReply()
    {
      ReplyPacket rp = getReply();
      return (rp != null) ? rp.getDataAsLong() : 0;
    }
    public String getStringReply()
    {
      ReplyPacket rp = getReply();
      return (rp != null) ? rp.getDataAsString() : null;
    }
    public void close()
    {
      alive = false;
    }
    public void nextReplyIsCryptoStatus(boolean turnedOn)
    {
      nextReplyCryptoToggle = new Boolean(turnedOn);
    }
    public boolean isEncryptionOn()
    {
      return encryptionOn;
    }

    private Thread singleThread;
    private java.util.Vector replyQueue;
    private java.nio.channels.ReadableByteChannel dis;
    private boolean alive;
    private java.nio.ByteBuffer recvBuf = java.nio.ByteBuffer.allocate(131072); // subpicture data sent back can be large sometimes

    private Boolean nextReplyCryptoToggle;

    private boolean encryptionOn;
    private byte[] decryptBuff;
    private int intRepliesToDiscard;
  }
  private static class ReplyPacket
  {
    public ReplyPacket(int inType, long inTimestamp, int inId, byte[] inData)
    {
      setPacketData(inType, inTimestamp, inId, inData);
    }
    public void setPacketData(int inType, long inTimestamp, int inId, byte[] inData)
    {
      data = inData;
      type = inType;
      timestamp = inTimestamp;
      id = inId;
      length = (data == null ? 0 : data.length);
    }
    public void setStringList(String[] x) { stringList = x; }
    public int getType() { return type; }
    public int getLength() { return length; }
    public long getTimestamp() { return timestamp; }
    public int getID() { return id;	}
    public byte[] getData() { return data; }
    public int getDataAsInteger()
    {
      int rv = 0;
      for (int i = 0; i < 4 && i < data.length; i++)
        rv = (rv << 8) | (data[i] & 0xFF);
      return rv;
    }
    public long getDataAsLong()
    {
      long rv = 0;
      for (int i = 0; i < 8 && i < data.length; i++)
        rv = (rv << 8) | (data[i] & 0xFF);
      return rv;
    }
    public String getDataAsString()
    {
      return data == null ? null : new String(data, 0, length);
    }
    public String[] getDataStringList()
    {
      return stringList;
    }
    private int type;
    private int length;
    private long timestamp;
    private int id;
    private byte[] data;
    private String[] stringList;
  }

  public static class RemoteFSXfer
  {
    public RemoteFSXfer(int inSecureID, java.io.File inLocalFile, String inRemotePath,
        long inOffset, long inSize, boolean inDownload)
    {
      secureID = inSecureID;
      localFile = inLocalFile;
      remotePath = inRemotePath;
      offset = inOffset;
      size = inSize;
      download = inDownload;
    }
    public String toString()
    {
      return "RemoteFSXfer[" + (download ? "Down" : "Up" ) + " id=" + secureID + " local=" + localFile + " remote=" + remotePath +
          " off=" + offset + " size=" + size + " err=" + error + "]";
    }
    public long getBytesXferd() { return progress; }
    public long getSize() { return size; }
    public int getError() { return error; }
    public void abortNow() { abort = true; }
    public boolean isDone() { return (progress == size && size > 0) || abort || error != 0; }
    public int secureID;
    public java.io.File localFile;
    public String remotePath;
    public long offset;
    public long size;
    public boolean download;
    public long progress;
    public int error;
    public boolean abort;
  }

  private long textCacheSize;
  private java.util.Map textCacheMap = new java.util.HashMap();
  private static class TextSurfaceCache
  {
    // These are keyed off their textString + hexCode of the color + fontDesc
    public long handle;
    public java.awt.Rectangle srcRect;
    public String text;
    public java.awt.Color color;
    public long lastUsed;
    public String key;
    public int size;
  }

  // reuse a single container to hold all the menu related hints
  private MenuHintItem menuHint = new MenuHintItem();
  protected static class MenuHintItem
  {
    public String menuName;
    public String popupName;
    public boolean hasTextInput;

    public MenuHintItem()
    {
    }

    public void clear()
    {
      menuName=null;
      popupName=null;
      hasTextInput=false;
    }

    /**
     * @return true if there is a menu or popup in this hint
     */
    public boolean hasHint()
    {
      return menuName!=null || popupName!=null;
    }

    /**
     * @return formatted hint text, or null, if no hint is available
     */
    public String format()
    {
      if (!hasHint()) return null;
      return "menuName:" + menuName +
              ", popupName:" + popupName +
              ", hasTextInput:" + hasTextInput;
    }
  }
}
