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

public class DShowLivePlayer extends DShowMediaPlayer
{
  public DShowLivePlayer()
  {
    super();
  }

  public synchronized void load(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException
  {
    eos = false;
    pHandle = createGraph0();
    currHintMajorType = majorTypeHint;
    currHintMinorType = minorTypeHint;
    currHintEncoding = encodingHint;
    setFilters();

    // There's 3 different scenarios here
    // Case 1: Same filter for audio & video preview, I might even be OK adding it to the graph twice and just
    // make this a special case that doesn't need special attention for the 2 filter scenario
    // Case 2: 2 different filters, one for audio & one for video capture
    // Case 3: a video capture filter and a live input from the sound card

    cdi = MMC.getInstance().getCaptureDeviceInputNamed(encodingHint);
    if (cdi == null)
      throw new PlaybackException();
    CaptureDevice capDev = cdi.getCaptureDevice();
    if (capDev.hasAuxCaptureDevice())
    {
      setLiveSource0(pHandle, capDev.getCaptureDeviceName(), capDev.getCaptureDeviceNum(), capDev.getAuxCaptureDevice(),
          capDev.getAuxCaptureDeviceNum() );
    }
    else
    {
      setLiveSource0(pHandle, capDev.getCaptureDeviceName(), capDev.getCaptureDeviceNum(), null, 0);
    }
    setupGraph0(pHandle, file != null ? file.getPath() : null, hostname, true, true);
    if (transparency != TRANSLUCENT && (majorTypeHint == MediaFile.MEDIATYPE_VIDEO ||
        majorTypeHint == MediaFile.MEDIATYPE_DVD)) // not vmr & video is present, so we need to render to the HWND
      setVideoHWND0(pHandle, VideoFrame.getVideoFrameForPlayer(this).getVideoHandle());
    audioPreviewPtr = DShowSharedLiveMediaPlayer.openAudioPreview0(capDev.getName());

    colorKey = null;
    currCCState = -1;
    videoDimensions = null;
    getColorKey();
    getVideoDimensions();
    currFile = file;
    currState = LOADED_STATE;
    setNotificationWindow0(pHandle, Sage.mainHwnd);
    UIManager uiMgr = VideoFrame.getVideoFrameForPlayer(this).getUIMgr();
    lastVolume = (uiMgr.getBoolean("media_player_uses_system_volume", Sage.WINDOWS_OS && !Sage.VISTA_OS)) ? 1.0f : uiMgr.getFloat("videoframe/last_dshow_volume", 1.0f);
  }

  public boolean canFastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file)
  {
    return encodingHint != null && encodingHint.equals(currHintEncoding);
  }

  public void fastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint,
      java.io.File file, String hostname, boolean timeshifted, long bufferSize, boolean waitUntilDone) throws PlaybackException
      {
    // We don't actually do anything here because there's no graph changes aside from tuning which we don't do
    eos = false;
      }

  public void free()
  {
    killAudioPreview();
    super.free();
  }

  public boolean play()
  {
    if (currState == LOADED_STATE)
    {
      // For the PX-TV100U we need to activate the crossbar after we start the graph
      if (super.play())
      {
        if ("PX-TV100U".equals(cdi.getCaptureDevice().getName()))
        {
          if (Sage.DBG) System.out.println("Postactivating the crossbar...");
          Pooler.execute(new Runnable()
          {
            public void run()
            {
              try
              {
                cdi.getCaptureDevice().activateInput(cdi);
              }
              catch (EncodingException e){}
            }
          });
        }
        return true;
      }
      else
        return false;
    }
    else
      return super.play();
  }

  public void stop()
  {
    // Be sure to mute us before we stop
    //String liveAudio = cdi == null ? "" : cdi.getCaptureDevice().getLiveAudioInput();
    // NOTE: This is to make raw capture devices work without audio configuration temporarily
    // AND it's also used for muting/unmuting the PX-TV100U
    /*if (liveAudio.length() == 0) liveAudio = "Line In";
		if (liveAudio.length() > 0)
		{
			String[] inputPaths = DShowCaptureDevice.getAudioInputPaths0();
			for (int i = 0; i < inputPaths.length; i++)
				if (inputPaths[i].equals(liveAudio))
				{
					setLiveMute0(i, true);
					return;
				}
		}
     */
    setLiveMute0(-1, true);
    killAudioPreview();
    super.stop();
  }


  public boolean frameStep(int amount)
  {
    return false;
  }

  public long getDurationMillis()
  {
    return 0;
  }

  public java.io.File getFile()
  {
    return currFile;
  }

  public long getMediaTimeMillis()
  {
    return Sage.time();
  }

  public int getPlaybackCaps()
  {
    return LIVE_CAP;
  }

  public float getPlaybackRate()
  {
    return 1.0f;
  }

  public long seek(long seekTimeMillis) throws PlaybackException
  {
    throw new PlaybackException(PlaybackException.SEEK, 0);
  }

  public float setPlaybackRate(float newRate)
  {
    return 1.0f;
  }

  public void setMute(boolean x)
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      //String liveAudio = cdi == null ? "" : cdi.getCaptureDevice().getLiveAudioInput();
      // NOTE: This is to make raw capture devices work without audio configuration temporarily
      // AND it's also used for muting/unmuting the PX-TV100U
      /*if (liveAudio.length() == 0) liveAudio = "Line In";
			if (liveAudio.length() > 0)
			{
				String[] inputPaths = DShowCaptureDevice.getAudioInputPaths0();
				for (int i = 0; i < inputPaths.length; i++)
					if (inputPaths[i].equals(liveAudio))
					{
						setLiveMute0(i, x);
						return;
					}
			}*/
      if (audioPreviewPtr != 0)
      {
        synchronized (this)
        {
          DShowSharedLiveMediaPlayer.setAudioPreview0(audioPreviewPtr, audioPreviewMuted = x);
        }
      }
      else
      {
        setLiveMute0(-1, x);
        synchronized (this)
        {
          setGraphVolume0(pHandle, x ? 0 : lastVolume);
        }
      }
    }
  }

  public boolean getMute()
  {
    if (currState == PLAY_STATE || currState == PAUSE_STATE || currState == LOADED_STATE)
    {
      if (audioPreviewPtr != 0)
        return audioPreviewMuted;
      //String liveAudio = cdi == null ? "" : cdi.getCaptureDevice().getLiveAudioInput();
      // NOTE: This is to make raw capture devices work without audio configuration temporarily,
      // AND it's also used for muting/unmuting the PX-TV100U
      /*if (liveAudio.length() == 0) liveAudio = "Line In";
			if (liveAudio.length() > 0)
			{
				String[] inputPaths = DShowCaptureDevice.getAudioInputPaths0();
				for (int i = 0; i < inputPaths.length; i++)
					if (inputPaths[i].equals(liveAudio))
					{
						return getLiveMute0(i);
					}
			}*/
      synchronized (this)
      {
        return getGraphVolume0(pHandle) == 0;
      }
    }
    else
      return false;
  }

  protected void killAudioPreview()
  {
    if (audioPreviewPtr != 0)
    {
      long myPtr = audioPreviewPtr;
      synchronized (this)
      {
        audioPreviewPtr = 0;
        DShowSharedLiveMediaPlayer.setAudioPreview0(myPtr, true);
        DShowSharedLiveMediaPlayer.closeAudioPreview0(myPtr);
      }
    }
  }

  // createGraph0 will create the peer native object and create the initial filter graph
  protected native long createGraph0() throws PlaybackException;
  protected native void setLiveSource0(long ptr, String captureFilterName, int captureFilterNum,
      String audioCaptureFilterName, int audioCaptureFilterNum ) throws PlaybackException;
  // setupGraph0 adds all of the filters to the graph and connects it up appropriately
  protected native void setupGraph0(long ptr, String filePath, String remoteHost, boolean renderVideo, boolean renderAudio) throws PlaybackException;

  protected native static boolean getLiveMute0(int inputIndex);
  protected native static void setLiveMute0(int inputIndex, boolean x);

  protected CaptureDeviceInput cdi;
  protected long audioPreviewPtr;
  protected boolean audioPreviewMuted = false;
}
