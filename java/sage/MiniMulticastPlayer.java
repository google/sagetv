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

/**
 *
 * @author Narflex
 */
public class MiniMulticastPlayer implements MediaPlayer
{
  private java.net.Socket clientSocket;
  private java.io.DataInputStream clientInStream;
  private java.io.DataOutputStream clientOutStream;

  /** Creates a new instance of MiniMulticastPlayer */
  public MiniMulticastPlayer()
  {
    currState = NO_STATE;
  }

  public boolean canFastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file)
  {
    // They'll be the same type because that's already checked in VideoFrame
    return currState == PLAY_STATE;
  }

  private String getMulticastAddress()
  {
    CaptureDevice capDev = VideoFrame.getMediaFileForPlayer(this).guessCaptureDeviceFromEncoding();
    return capDev.getChannel();
  }

  public synchronized void fastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize, boolean waitUntilDone) throws PlaybackException
  {
    if (Sage.DBG) System.out.println("MiniMulticast Fast Load");

    currHintMajorType = majorTypeHint;
    currHintMinorType = minorTypeHint;
    currHintEncoding = encodingHint;

    if (Sage.getBoolean("miniplayer/use_tsdirect", true))
      openURL0(ptr, "tsdirect://" + getMulticastAddress());
    else
      openURL0(ptr, "multicast:" + getMulticastAddress());
    flushPush0(ptr);
  }

  public boolean frameStep(int amount)
  {
    return false;
  }

  public synchronized void free()
  {
    if (uiMgr != null)
      uiMgr.putFloat("miniplayer/last_volume", curVolume);
    currState = NO_STATE;
    System.out.println("Closing down MiniMulticastPlayer");
    closeDriver0(ptr);
    if (clientInStream != null)
    {
      try { clientInStream.close(); } catch(Exception e){}
      clientInStream = null;
    }
    if (clientOutStream != null)
    {
      try { clientOutStream.close(); } catch(Exception e){}
      clientOutStream = null;
    }
    if (clientSocket != null)
    {
      try { clientSocket.close(); } catch(Exception e){}
      clientSocket = null;
    }
    ptr = 0;
    currHintMajorType = currHintMinorType = (byte)0;
    currHintEncoding = null;
    videoDimensions = null;
    currMute = false;
    uiMgr = null;
    lastVideoSrcRect = lastVideoDestRect = null;
    curVolume = 1.0f;
  }

  public int getClosedCaptioningState()
  {
    return CC_DISABLED;
  }

  public java.awt.Color getColorKey()
  {
    return null;
  }

  public long getDurationMillis()
  {
    return 0;
  }

  public java.io.File getFile()
  {
    return null;
  }

  public synchronized long getMediaTimeMillis()
  {
    return Sage.time();
  }

  public boolean getMute()
  {
    return currMute;
  }

  public int getPlaybackCaps()
  {
    return LIVE_CAP;
  }

  public float getPlaybackRate()
  {
    return 1.0f;
  }

  public int getState()
  {
    return currState;
  }

  public int getTransparency()
  {
    return TRANSLUCENT;
  }

  public java.awt.Dimension getVideoDimensions()
  {
    return videoDimensions;
  }

  public float getVolume()
  {
    return curVolume;
  }

  public void inactiveFile()
  {
  }

  public synchronized void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    uiMgr = VideoFrame.getVideoFrameForPlayer(MiniMulticastPlayer.this).getUIMgr();

    boolean isPALOutput = (MMC.getInstance().isNTSCVideoFormat() ?
        Sage.getInt("osd_rendering_height_ntsc", 480) : Sage.getInt("osd_rendering_height_pal", 576)) == 576;
    ptr = initDriver0(VideoFrame.getVideoFrameForPlayer(this).getDisplayAspectRatio() > 1.40 ?
        1 :0 );
    if (ptr == 0)
      throw new PlaybackException();

    currState = LOADED_STATE;
    currMute = false;
    if (Sage.DBG) System.out.println("Launching multicast player for:" + getMulticastAddress());
    if (Sage.getBoolean("miniplayer/use_tsdirect", true))
    {
      if (!openURL0(ptr, "tsdirect://" + getMulticastAddress()))
        throw new PlaybackException();
    }
    else
    {
      if (!openURL0(ptr, "multicast:" + getMulticastAddress()))
        throw new PlaybackException();
      DVDStream(ptr, 0, 0xc000);
    }

    currState = PLAY_STATE;
    currHintMajorType = majorTypeHint;
    currHintMinorType = minorTypeHint;
    currHintEncoding = encodingHint;

    videoDimensions = getVideoDimensions0(ptr);
    if (Sage.DBG) System.out.println("Sigma video dim=" + videoDimensions);
    setMute0(ptr, false);

    // Preserve the volume setting across UI sessions
    setVolume(uiMgr.getFloat("miniplayer/last_volume", 1.0f));

    play();
    if (Sage.getBoolean("miniplayer/use_tsdirect", true))
      playPush0(ptr);
  }

  public boolean pause()
  {
    return false;
  }

  public boolean play()
  {
    return true;
  }
  public long seek(long seekTimeMillis) throws PlaybackException
  {
    return Sage.time();
  }

  public boolean setClosedCaptioningState(int ccState)
  {
    return false;
  }

  public void setMute(boolean x)
  {
    if (currMute != x)
    {
      synchronized (this)
      {
        synchronized (decoderLock)
        {
          setMute0(ptr, currMute = x);
        }
      }
    }
  }

  public float setPlaybackRate(float newRate)
  {
    return 1.0f;
  }

  public synchronized void setVideoRectangles(java.awt.Rectangle videoSrcRect,
      java.awt.Rectangle videoDestRect, boolean hideCursor)
  {
    if(clientOutStream != null && clientInStream != null && currHintMajorType != MediaFile.MEDIATYPE_AUDIO)
    {
      if (lastVideoSrcRect == null || lastVideoDestRect == null || !videoSrcRect.equals(lastVideoSrcRect) ||
          !videoDestRect.equals(lastVideoDestRect))
      {
        boolean tookIt;
        synchronized (decoderLock)
        {
          tookIt = setVideoRectangles0(ptr, videoSrcRect, videoDestRect);
        }
        if (tookIt)
        {
          lastVideoSrcRect = (java.awt.Rectangle) videoSrcRect.clone();
          lastVideoDestRect = (java.awt.Rectangle) videoDestRect.clone();
        }
        else
        {
          lastVideoSrcRect = null;
          lastVideoDestRect = null;
        }
      }
    }
  }

  public synchronized float setVolume(float f)
  {
    synchronized (decoderLock)
    {
      if(f>1.0f) f=1.0f;
      if(f<0.0f) f=0.0f;
      setVolume0(f);
      curVolume=f;
    }
    return f;
  }

  public void stop()
  {
  }

  protected long initDriver0(int videoFormat)
  {
    if (Sage.DBG) System.out.println("initDriver0()");
    clientSocket = ((MiniClientSageRenderer)uiMgr.getRootPanel().getRenderEngine()).getPlayerSocket();

    String clientName = uiMgr.getLocalUIClientName();
    if (clientSocket == null) return 0;
    try
    {
      clientInStream = new java.io.DataInputStream(clientSocket.getInputStream());
      clientOutStream = new java.io.DataOutputStream(new java.io.BufferedOutputStream(clientSocket.getOutputStream()));
      clientOutStream.writeInt(MiniPlayer.MEDIACMD_INIT<<24 | 4 );
      clientOutStream.writeInt(videoFormat);
      clientOutStream.flush();
      if (Sage.DBG) System.out.println("MiniMulticastPlayer established for " + clientName);
      return clientInStream.readInt()!=0 ? 1 : 0;
    }
    catch (java.io.IOException e)
    {
      if (Sage.DBG) System.out.println("Error w/ MiniPlayer socket of:" + e);
      return 0;
    }
  }

  protected boolean openURL0(long ptr, String url)
  {
    if (Sage.DBG) System.out.println("openURL0()");
    try
    {
      clientOutStream.writeInt(MiniPlayer.MEDIACMD_OPENURL<<24 | url.length()+1+4);
      byte [] b=url.getBytes(Sage.BYTE_CHARSET);
      clientOutStream.writeInt(url.length()+1);
      clientOutStream.write(b, 0, b.length);
      clientOutStream.write((byte)0);
      clientOutStream.flush();
      return clientInStream.readInt()!=0;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return false;
  }

  protected boolean closeDriver0(long ptr)
  {
    if (Sage.DBG) System.out.println("closeDriver0()");
    try
    {
      clientOutStream.writeInt(MiniPlayer.MEDIACMD_DEINIT<<24 | 0 );
      clientOutStream.flush();
      //clientInStream.readInt(); // Why do we care?? This just makes it take longer to close.
      return true;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    finally
    {
      try
      {
        clientOutStream.close();
      }catch (Exception e){}
      try
      {
        clientInStream.close();
      }catch (Exception e){}
      try
      {
        clientSocket.close();
      }catch (Exception e){}
      clientOutStream = null;
      clientInStream = null;
      clientSocket = null;
    }
    return false;
  }

  protected boolean setMute0(long ptr, boolean x)
  {
    if (clientOutStream == null) return false;
    if (Sage.DBG) System.out.println("setMute0()");
    try
    {
      clientOutStream.writeInt(MiniPlayer.MEDIACMD_SETMUTE<<24 | 4 );
      clientOutStream.writeInt(x ? 1 : 0);
      clientOutStream.flush();
      return clientInStream.readInt()!=0;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return false;
  }

  protected boolean playPush0(long ptr)
  {
    if (clientOutStream == null) return false;
    if (Sage.DBG) System.out.println("playPush0()");
    try
    {
      clientOutStream.writeInt(MiniPlayer.MEDIACMD_PLAY<<24 | 0 );
      clientOutStream.flush();
      return clientInStream.readInt()!=0;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return false;
  }

  protected java.awt.Dimension getVideoDimensions0(long ptr)
  {
    return new java.awt.Dimension(720,480);
  }

  private void killUI()
  {
    if (Sage.getBoolean("miniplayer/kill_ui_on_error", false))
    {
      // We have to do this on another thread to prevent deadlocking
      Pooler.execute(new Runnable()
      {
        public void run()
        {
          uiMgr.goodbye();
        }
      });
    }
  }

  protected boolean setVideoRectangles0(long ptr, java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect)
  {
    if(clientOutStream != null && clientInStream != null)
    {
      try
      {
        clientOutStream.writeInt(MiniPlayer.MEDIACMD_SETVIDEORECT<<24 | 8*4 );
        clientOutStream.writeInt(videoSrcRect.x);
        clientOutStream.writeInt(videoSrcRect.y);
        clientOutStream.writeInt(videoSrcRect.width);
        clientOutStream.writeInt(videoSrcRect.height);
        clientOutStream.writeInt(videoDestRect.x);
        clientOutStream.writeInt(videoDestRect.y);
        clientOutStream.writeInt(videoDestRect.width);
        clientOutStream.writeInt(videoDestRect.height);
        clientOutStream.flush();
        clientInStream.readInt();
        return true;
      }catch(Exception e)
      {
        if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
        e.printStackTrace();
        killUI();
      }
    }
    return false;
  }

  protected float getVolume0()
  {
    if (clientOutStream == null) return 0;
    try
    {
      clientOutStream.writeInt(MiniPlayer.MEDIACMD_GETVOLUME<<24 | 0 );
      clientOutStream.flush();
      return clientInStream.readInt()/65535.0f;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return 0.0f;
  }

  protected float setVolume0(float volume)
  {
    if (clientOutStream == null) return 0;
    try
    {
      clientOutStream.writeInt(MiniPlayer.MEDIACMD_SETVOLUME<<24 | 4 );
      clientOutStream.writeInt((int)(volume*65535));
      clientOutStream.flush();
      return clientInStream.readInt()/65535.0f;
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return 0.0f;
  }

  protected boolean DVDStream(long ptr, int type, int stream)
  {
    if (clientOutStream == null) return false;
    try
    {
      int streamStatus;
      clientOutStream.writeInt(MiniPlayer.MEDIACMD_DVD_STREAM<<24 |
          (8));
      clientOutStream.writeInt(type);
      clientOutStream.writeInt(stream);
      clientOutStream.flush();
      streamStatus = clientInStream.readInt();
      return true;
    }catch(Exception e)
    {
      if (Sage.DBG) System.out.println("Error with MiniPlayer, closing UI: " + e);
      e.printStackTrace();
      killUI();
    }
    return false;
  }

  protected boolean flushPush0(long ptr)
  {
    if (clientOutStream == null) return false;
    try
    {
      clientOutStream.writeInt(MiniPlayer.MEDIACMD_FLUSH<<24 | 0 );
      clientOutStream.flush();
      boolean rv = clientInStream.readInt()!=0;
      if (currMute)
      {
        // Flushing the decoder resets the mute state on the MVP
        setMute0(ptr, currMute);
      }
    }catch(Exception e)
    {
      System.out.println(e);
      e.printStackTrace();
    }
    return false;
  }

  protected volatile int currState;
  protected byte currHintMajorType;
  protected byte currHintMinorType;
  protected String currHintEncoding;

  protected Object decoderLock = new Object();

  protected java.awt.Dimension videoDimensions;

  protected long ptr;

  protected boolean currMute;

  protected UIManager uiMgr;
  protected java.awt.Rectangle lastVideoSrcRect;
  protected java.awt.Rectangle lastVideoDestRect;
  protected float curVolume = 1.0f;
}
