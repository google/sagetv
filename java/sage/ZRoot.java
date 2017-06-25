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

public class ZRoot extends java.awt.Canvas
    implements java.awt.event.ComponentListener, Runnable, java.awt.event.MouseListener,
    java.awt.event.MouseMotionListener, java.awt.event.HierarchyListener
{
  // Scaled against the min dimension and then squared off
  private static final float WAIT_INDICATOR_SIZE = 0.08f;

  public static final int JAVA2DRENDER = 1;
  public static final int NATIVE3DRENDER = 2;
  public static final int REMOTE2DRENDER = 3;
  // this is only used by the active renderer as a minimum time to check for animation updates,
  // but this will never end up going faster than the monitor refresh rate or the video frame rate (if video is playing)
  static final long FRAME_TIME = 30;
  public static final boolean THREADING_DBG = false;
  private static final int DEBUG_FPS_RATE = Sage.getInt("debug_fps_freq", 0);
  public ZRoot(UIManager inUIMgr)
  {
    super();
    uiMgr = inUIMgr;
    vf = uiMgr.getVideoFrame();
    addComponentListener(this);
    addMouseListener(this);
    addMouseMotionListener(this);
    addHierarchyListener(this);
    vf.addMouseListeners(this);
    vf.addMouseMotionListeners(this);

    alive = true;

    if (uiMgr.getBoolean("ui/remote_render", false))
    {
      renderType = REMOTE2DRENDER;
      uiMgr.putBoolean("ui/accelerated_rendering", false);
    }
    else if (uiMgr.getBoolean("ui/accelerated_rendering", true) && !Java2DSageRenderer.hasOSDRenderer(uiMgr))
    {
      renderType = NATIVE3DRENDER;
    }
    else
      renderType = JAVA2DRENDER;

    if (renderType == NATIVE3DRENDER)
    {
      if(Sage.MAC_OS_X) {
        try {
          Class[] params = {this.getClass()};
          Object[] args = {this};
          java.lang.reflect.Constructor ctor = Class.forName("sage.QuartzSageRenderer").getConstructor(params);
          renderEngine = (SageRenderer) ctor.newInstance(args);
          setIgnoreRepaint(true); // avoid issues with the native renderer causing us to endlessly redraw the UI...
        } catch(Throwable t) {
          System.out.println("Exception occurred while creating renderer: "+t);
          renderType = JAVA2DRENDER;
          renderEngine = new Java2DSageRenderer(this);
        }
      } else {
        renderEngine = /*uiMgr.getBoolean("opengl", false) ?
					(SageRenderer)new JOGLSageRenderer(this) :*/ new DirectX9SageRenderer(this);
      }
    }
    else if (renderType == REMOTE2DRENDER)
      renderEngine = new MiniClientSageRenderer(this);
    else
      renderEngine = new Java2DSageRenderer(this);
    allocatedRenderer = false;

    if (uiMgr.getBoolean("ui/display_wait_indicator", true))
    {
      watchdog = new AWTThreadWatcher(uiMgr);
      waitImages = new java.util.ArrayList();
      for (int i = 0; i < 8; i++)
        waitImages.add(MetaImage.getMetaImage(uiMgr.get("ui/wait_icon_prefix", "images/tvicon_anim") + i + ".png"));
    }

    //		setIgnoreRepaint(true);
    activeRenderThread = new Thread(this, "ActiveRender-" + uiMgr.getLocalUIClientName());
    activeRenderThread.setDaemon(true);
    activeRenderThread.setPriority(Math.min(Thread.MAX_PRIORITY,
        Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY + uiMgr.getInt("ui/active_rendering_thread_priority_offset", -3))));
    activeRenderThread.start();


    finalRenderThread = new Thread("FinalRender-" + uiMgr.getLocalUIClientName())
    {
      public void run()
      {
        if (watchdog != null)
          watchdog.start();
        long sleepStart = 0;
        boolean nextDLHasVideo = false;
        int lastCycleEvtCounter = 0;
        int lastCycleMouseCounter = 0;
        boolean forceContinuousRender = Sage.getBoolean("ui/force_continuous_render", false);
        while (alive)
        {
          if (lastRenderEngine != null && lastRenderEngine != renderEngine)
          {
            // Render engine changed, cleanup the old one.
            lastRenderEngine.cleanupRenderer();
            lastRenderEngine = null;
          }
          if (THREADING_DBG && Sage.DBG) System.out.println("FinalRender thread at the root of its cycle");
          lastRenderEngine = renderEngine;
          sleepStart = Sage.eventTime();
          if (forceContinuousRender)
            effectsNeedProcessing = true;
          try
          {
            java.awt.Rectangle currRenderRect = null;
            synchronized (finalRenderThreadLock)
            {
              boolean currentlyHidden = (getParent() == null || (!isShowing() && requiresWindow()));
              // We stop processing the current effect animatiion if we see that the AR is going to run. On PC systems we don't do this because
              // we have much better parallel processing there. Either GPU/CPU or server/extender. But on embedded systems everything is very reliant
              // on a single main CPU; and we get much better UI performance by just stopping this processing in that case.

              if (currentlyHidden ||
                  (renderNeeded == null && renderEngine.hasConsumedNextDisplayList() && finalAnims.isEmpty() && !finalAnimsNeedProcessing && !effectsNeedProcessing))
              {
                currentlyScrolling = false;
                // If we're not visible then clear the wait indicator state so we don't continually run through this loop
                if (currentlyHidden)
                  renderEngine.setWaitIndicatorState(false);
                if (THREADING_DBG && Sage.DBG) System.out.println("FinalRender thread waiting");
                // If AWT is hung we need to refresh quicker to get our animation effect in the wait
                if (watchdog != null && watchdog.isAWTHung() && allocatedRenderer)
                {
                  finalRenderThreadLock.wait(uiMgr.getInt("ui/wait_indicator_refresh_period", 100));
                }
                else
                {
                  if (!allocatedRenderer || !renderEngine.getWaitIndicatorState())
                    finalRenderThreadLock.wait(500);
                  else
                  {
                    // This is for clearing the wait indicator
                    if (renderEngine.supportsWaitCursor())
                    {
                      renderEngine.setWaitIndicatorState(false);
                    }
                    else
                      renderNeeded = getWaitRectArea();
                  }
                }
                // NOTE: We have to use lastRenderTime instead of lastFinishTime or the wait renders
                // would count themselves as activity
                if (allocatedRenderer && watchdog != null && watchdog.isAWTHung() &&
                    Sage.eventTime() - lastRenderTime > 500)
                {
                  if (THREADING_DBG && Sage.DBG) System.out.println("ZRoot updating with wait indicator since AWT is hung");
                  if (renderEngine.supportsWaitCursor())
                    renderEngine.setWaitIndicatorState(true);
                  else if (renderNeeded == null)
                    renderNeeded = getWaitRectArea();
                }
                continue;
              }
              long renderAnimTime = Sage.eventTime();
              int currEvtCount = uiMgr.getRouter().getCounter();
              int currMouseCount = mouseEvtCounter;
              // NOTE: We may need to move this into executeFinalRender cycle if there's issues with VMR9
              // and animation rates not looking right
              synchronized (finalAnims)
              {
                boolean hasScrollAnimsStill = uiMgr.areLayersEnabled() && renderEngine.doesNextDisplayListHaveScrollingAnimations();
                if (!finalAnims.isEmpty() && uiMgr.getBoolean("ui/animation/require_completion", true))
                {
                  if (lastRenderEngine.checkForFocusAnimConsolidate())
                    animationLock = false;
                  else
                  {
                    // If there hasn't been any user input then we put on the animation lock
                    if (uiMgr.getBoolean("ui/animation/only_interrupt_on_events", true) &&
                        (currEvtCount == lastCycleEvtCounter && currMouseCount == lastCycleMouseCounter))
                      animationLock = true;
                    else
                    {
                      animationLock = false;
                      // Check for any un-interruptable animations; if there are any then we flip on the animationLock
                      for (int i = 0; i < finalAnims.size(); i++)
                      {
                        RenderingOp roppy = (RenderingOp)finalAnims.get(i);
                        if (!roppy.anime.interruptable)
                        {
                          animationLock = true;
                          break;
                        }
                      }
                    }
                  }
                }
                else
                {
                  if (lockedEffectRunning)
                    animationLock = true;
                  else
                    animationLock = false;
                }
                if (renderNeeded != null)
                {
                  lastCycleEvtCounter = currEvtCount;
                  lastCycleMouseCounter = currMouseCount;
                }
                if (!animationLock && !finalAnims.isEmpty() && renderNeeded != null)
                {
                  if (THREADING_DBG && Sage.DBG) System.out.println("FinalRender thread processing animations w/out lock & new UI update");
                  currRenderRect = new java.awt.Rectangle();
                  currRenderRect.setBounds(renderNeeded);
                  for (int i = 0; i < finalAnims.size(); i++)
                  {
                    RenderingOp roppy = (RenderingOp)finalAnims.get(i);
                    if (roppy.anime.animType == RenderingOp.Animation.SCROLL) hasScrollAnimsStill = true;
                    // We do the union before and after so we cover the dirty area from last time
                    java.awt.Rectangle.union(currRenderRect, roppy.destRect, currRenderRect);
                    if (roppy.anime.altDestRect != null)
                      java.awt.Rectangle.union(currRenderRect, roppy.anime.altDestRect, currRenderRect);
                    if (roppy.anime.isExpired(renderAnimTime))
                      roppy.anime.expired = true;
                    roppy.anime.calculateAnimation(renderAnimTime, getWidth(), getHeight(), isIntegerPixels() && !roppy.isImageOp());
                    java.awt.Rectangle.union(currRenderRect, roppy.destRect, currRenderRect);
                    if (roppy.anime.altDestRect != null)
                      java.awt.Rectangle.union(currRenderRect, roppy.anime.altDestRect, currRenderRect);
                  }
                  finalAnimsNeedProcessing = true;
                }
                else if ((renderNeeded == null || animationLock) && !finalAnims.isEmpty())
                {
                  if (THREADING_DBG && Sage.DBG) System.out.println("FinalRender thread processing animations w/ lock or redone UI");
                  currRenderRect = new java.awt.Rectangle();
                  RenderingOp roppy = (RenderingOp)finalAnims.get(0);
                  if (roppy.anime.animType == RenderingOp.Animation.SCROLL) hasScrollAnimsStill = true;
                  currRenderRect.setRect(roppy.destRect);
                  if (roppy.anime.altDestRect != null)
                    java.awt.Rectangle.union(currRenderRect, roppy.anime.altDestRect, currRenderRect);
                  roppy.anime.calculateAnimation(renderAnimTime, getWidth(), getHeight(), isIntegerPixels() && !roppy.isImageOp());
                  if (roppy.anime.isExpired(renderAnimTime))
                    roppy.anime.expired = true;
                  java.awt.Rectangle.union(currRenderRect, roppy.destRect, currRenderRect);
                  if (roppy.anime.altDestRect != null)
                    java.awt.Rectangle.union(currRenderRect, roppy.anime.altDestRect, currRenderRect);
                  for (int i = 1; i < finalAnims.size(); i++)
                  {
                    roppy = (RenderingOp)finalAnims.get(i);
                    if (roppy.anime.animType == RenderingOp.Animation.SCROLL) hasScrollAnimsStill = true;
                    java.awt.Rectangle.union(currRenderRect, roppy.destRect, currRenderRect);
                    if (roppy.anime.altDestRect != null)
                      java.awt.Rectangle.union(currRenderRect, roppy.anime.altDestRect, currRenderRect);
                    roppy.anime.calculateAnimation(renderAnimTime, getWidth(), getHeight(), isIntegerPixels() && !roppy.isImageOp());
                    if (roppy.anime.isExpired(renderAnimTime))
                      roppy.anime.expired = true;
                    java.awt.Rectangle.union(currRenderRect, roppy.destRect, currRenderRect);
                    if (roppy.anime.altDestRect != null)
                      java.awt.Rectangle.union(currRenderRect, roppy.anime.altDestRect, currRenderRect);
                  }
                  finalAnimsNeedProcessing = true;
                }
                else if (renderNeeded != null)
                  currRenderRect = renderNeeded;
                lastFinalAnimArea = finalAnims.isEmpty() ? null : currRenderRect;
                finalAnims.clear();
                if ((!renderEngine.supportsPartialUIUpdates() || uiMgr.getBoolean("ui/force_full_ui_rendering", false) || uiMgr.areEffectsEnabled()) &&
                    (currRenderRect != null || effectsNeedProcessing))
                {
                  if (currRenderRect == null)
                    currRenderRect = new java.awt.Rectangle();
                  currRenderRect.setBounds(0, 0, rootComp.getWidth(), rootComp.getHeight());
                }
                // Make sure we're not too big!
                if (currRenderRect != null)
                  java.awt.Rectangle.intersect(currRenderRect, rootComp.getBounds(), currRenderRect);
                currentlyScrolling = hasScrollAnimsStill;
              }
              if (!animationLock)
                renderNeeded = null;
              nextDLHasVideo = renderEngine.doesNextDisplayListHaveVideo();
              /*if (vf.isPlayin() && vf.hasFile() &&
								uiMgr.isTV())
							{
								// We only render when told to by the VMR in this situation
								if (consumedSyncID == finalSyncID)
									continue;
							}
							consumedSyncID = finalSyncID;*/
            }
            MediaFile mf = vf.getCurrFile();
            if (mf != null && mf.hasVideoContent() && nextDLHasVideo &&
                uiMgr.isTV() && /*vf.getUseVmr() &&*/ /*(renderType == NATIVE3DRENDER)*/
                renderEngine instanceof DirectX9SageRenderer && !vf.isNonBlendableVideoPlayerLoaded())
            {
              if (vf.getPlayerState() == MediaPlayer.PLAY_STATE)
              {
                // The VMR is controlling rendering, just let it take over we don't do anything
                // at all because it handles frame rate control and display list consumption
                if (Sage.eventTime() - lastFinishTime < 100)
                {
                  if (THREADING_DBG && Sage.DBG) System.out.println("FinalRender continuing wait due to VMR control");
                  synchronized (finalRenderThreadLock)
                  {
                    // this can coincide weirdly with the pausing of the stream and there will
                    // be a DL left in the queue since the play state change won't trigger
                    // a render cycle.
                    finalRenderThreadLock.wait(FRAME_TIME);
                  }
                  continue;
                }
                else
                {
                  // This is essential for ensuring display list consumption which otherwise
                  // will hang the AR
                  if (THREADING_DBG && Sage.DBG) System.out.println("FinalRender executing even though VMR is active because VMR hasn't notified us in awhile.");
                }
              }
              else
              {
                // The VF is paused, but this refresh might be due to a VF action done
                // while paused which'll give us a VMR callback. In that case we want that
                // callback to force the render to avoid corruption in the video surface. This
                // is what was happening when the OSD was getting painted in extra places.
                if (Sage.eventTime() - vf.getLastVideoChangeTime() < 100)
                {
                  if (THREADING_DBG && Sage.DBG) System.out.println("FinalRender continuing wait due to recent VF op under VMR control");
                  synchronized (finalRenderThreadLock)
                  {
                    // this can coincide weirdly with the pausing of the stream and there will
                    // be a DL left in the queue since the play state change won't trigger
                    // a render cycle.
                    finalRenderThreadLock.wait(FRAME_TIME);
                  }
                  continue;
                }
                else
                {
                  // This is essential for ensuring display list consumption which otherwise
                  // will hang the AR
                  if (THREADING_DBG && Sage.DBG) System.out.println("FinalRender executing even though VMR is active because VMR is paused, and the expected callback didn't occur.");
                }
              }
            }
            if (THREADING_DBG && Sage.DBG) System.out.println("FinalRender executing from the FinalRender thread");
            executeFinalRenderCycle(currRenderRect);
            /*
             * This sleep makes  HUGE difference in CPU usage. It'll go from <10% to close to 100% if this is disabled.
             * 20 - gives 45 fps on MainMenu with CPU<10%
             * 15 - gives 54 fps on MainMenu with usage around 15-20%, due to a lot of individual spikes
             *
             * 20  - 50 fps, <10%CPU
             * 15 - still about 50 fps, but CPU is a little more volatile
             */
            //if (THREADING_DBG && Sage.DBG) System.out.println("FinalRender thread sleeping");
            int frameDur = uiMgr.getInt("ui/frame_duration", 14);
            if (frameDur > 1/* && (!vf.isPlayin() || !vf.hasFile() ||
	!uiMgr.isTV() || !vf.getUseVmr())*/)
            {
              while (Sage.eventTime() - sleepStart < frameDur)
              {
                Thread.sleep(1);
              }
            }
          }
          catch (Throwable t)
          {
            System.out.println("Final Render had an error:" + t);
            Sage.printStackTrace(t);
          }
        }
        renderEngine.cleanupRenderer();
      }
    };
    finalRenderThread.setDaemon(true);
    finalRenderThread.setPriority(Math.min(Thread.MAX_PRIORITY,
        Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY + uiMgr.getInt("ui/final_rendering_thread_priority_offset", -2))));
    finalRenderThread.start();
  }

  // Return the rectangular area that gets updated when the wait indicator is shown
  public java.awt.Rectangle getWaitRectArea()
  {
    java.awt.Rectangle rv = new java.awt.Rectangle();
    java.awt.Rectangle fullArea = rootComp.getBounds();
    int waitIndicatorSize = (int)(Math.min(fullArea.width, fullArea.height) * WAIT_INDICATOR_SIZE);
    rv.height = rv.width = waitIndicatorSize;
    rv.x = (fullArea.width - rv.width)/2;
    rv.y = (fullArea.height - rv.height)/2;
    //rv.setBounds(fullArea); // TEMP DEBUG
    return rv;
  }

  public boolean requiresWindow()
  {
    return uiMgr.getGlobalFrame() != null && !uiMgr.getBoolean("hide_java_ui_window", Sage.LINUX_OS) &&
        (!(renderEngine instanceof Java2DSageRenderer) || !((Java2DSageRenderer) renderEngine).hasOSDRenderer());
  }
  private static boolean checked;
  void initializeRenderer()
  {
    if (allocatedRenderer) return;
    if (renderEngine instanceof Java2DSageRenderer && ((Java2DSageRenderer) renderEngine).hasOSDRenderer())
    {
      java.awt.Dimension scrSize = new java.awt.Dimension();
      scrSize.width = uiMgr.getInt("osd_rendering_width", 720);//Sage.getInt("pvr350_osd_locked_width", 720);
      scrSize.height = MMC.getInstance().isNTSCVideoFormat() ?
          uiMgr.getInt("osd_rendering_height_ntsc", 480) : uiMgr.getInt("osd_rendering_height_pal", 576);
    }
    /* We need to do this on the same thread as the one that destroys it for DX9
		if (!renderEngine.allocateBuffers(scrSize.width, scrSize.height))
		{
			// Failed creating the render engine
			if (shiznit)
			{
				shiznit = false;
				System.out.println("Sage had a failure loading the accelerated renderer, defaulting to software rendering.");
				renderEngine = new Java2DSageRenderer(this);
				renderEngine.allocateBuffers(scrSize.width, scrSize.height);
			}
		}
		allocatedRenderer = true;
     */
  }

  long[] fpsTimes = new long[Math.max(30, DEBUG_FPS_RATE)];
  long[] pixelRenderCounts = new long[Math.max(30, DEBUG_FPS_RATE)];
  long[] pixelInputCounts = new long[Math.max(30, DEBUG_FPS_RATE)];
  int fpsTimeIndex = 0;
  long lastFinishTime = 0;
  long freq = Sage.getCpuResolution();
  long frameStart;
  void executeFinalRenderCycle(java.awt.Rectangle currRenderRect)
  {
    executeFinalRenderCycle(currRenderRect, null);
  }
  // Use this one if this is being called from a render engine so we can now if the engine was swapped before the call and then
  // just terminate it before it does something bad like callback into a different engine
  void executeFinalRenderCycle(java.awt.Rectangle currRenderRect, SageRenderer callbackEngine)
  {
    if (currRenderRect == null)
      currRenderRect = new java.awt.Rectangle(0, 0, rootComp.getWidth(), rootComp.getHeight());
    boolean didProcessAnims = finalAnimsNeedProcessing || effectsNeedProcessing;
    synchronized (finalRenderExecutionLock)
    {
      // This should maximize frame rate by triggering the preparation
      // of the next display list as we draw the current one
      boolean consumedDL = false;
      SageRenderer currRenderEngine;
      synchronized (renderEngineLock)
      {
        if (!allocatedRenderer)
        {
          java.awt.Dimension scrSize = uiMgr.getScreenSize();
          if (renderEngine instanceof Java2DSageRenderer && ((Java2DSageRenderer) renderEngine).hasOSDRenderer())
          {
            scrSize.width = uiMgr.getInt("osd_rendering_width", 720);
            scrSize.height = MMC.getInstance().isNTSCVideoFormat() ?
                uiMgr.getInt("osd_rendering_height_ntsc", 480) : uiMgr.getInt("osd_rendering_height_pal", 576);
          }
          if (!renderEngine.allocateBuffers(scrSize.width, scrSize.height))
          {
            // Failed creating the render engine
            if (renderType == NATIVE3DRENDER)
            {
              if (Sage.getBoolean("ui/fallback_on_2d_renderer", true))
              {
                renderType = JAVA2DRENDER;
                System.out.println("Sage had a failure loading the accelerated renderer, defaulting to software rendering.");
                renderEngine = new Java2DSageRenderer(this);
                renderEngine.allocateBuffers(scrSize.width, scrSize.height);
                appendToDirty(MAX_DIRTY);
                allocatedRenderer = true;
              }
              else
              {
                System.out.println("Failed loading the 3D renderer; try it again because we're not allowed to revert...");
                // Wait a bit for this as well since something is likely wrong and we need to wait for a recovery
                try{Thread.sleep(5000);}catch(Exception e){}
                if(Sage.MAC_OS_X) {
                  try {
                    Class[] params = {this.getClass()};
                    Object[] args = {this};
                    java.lang.reflect.Constructor ctor = Class.forName("sage.QuartzSageRenderer").getConstructor(params);
                    renderEngine = (SageRenderer) ctor.newInstance(args);
                    setIgnoreRepaint(true); // avoid issues with the native renderer causing us to endlessly redraw the UI...
                  } catch(Throwable t) {
                    System.out.println("Exception occurred while creating renderer: "+t);
                    renderType = JAVA2DRENDER;
                    renderEngine = new Java2DSageRenderer(this);
                  }
                } else {
                  renderEngine = /*uiMgr.getBoolean("opengl", false) ?
									(SageRenderer)new JOGLSageRenderer(this) :*/ new DirectX9SageRenderer(this);
                }

              }
              return;
            }
            else
            {
              throw new InternalError("Failed allocating renderer!");
            }
          }
          allocatedRenderer = true;
        }
        currRenderEngine = renderEngine;
      }
      if (callbackEngine != null && callbackEngine != currRenderEngine)
      {
        if (Sage.DBG) System.out.println("FinalRender is bailing early because the render engine was switched on it!");
        return;
      }
      long finalRenderStart = Sage.time();
      if (!animationLock)
      {
        if (THREADING_DBG && Sage.DBG) System.out.println("FinalRenderCycle is consuming a display list thread=" + Thread.currentThread());
        consumedDL = currRenderEngine.makeNextDisplayListCurrent();
      }
      if (consumedDL || waitingOnARCycleID != 0)
      {
        synchronized (activeRenderThreadLock)
        {
          if (THREADING_DBG && Sage.DBG) System.out.println("FinalRenderCycle is notifying the AR-1");
          activeRenderThreadLock.notifyAll();
        }
      }
      if (Sage.DBG)
      {
        long totalFps = 0;
        long totalRenderPix = 0;
        long totalInputPix = 0;
        for (int i = 0; i < fpsTimes.length; i++)
        {
          totalFps += fpsTimes[i];
          totalRenderPix += pixelRenderCounts[i];
          totalInputPix += pixelInputCounts[i];
        }
        float fps = ((float)fpsTimes.length)*1000/totalFps;
        long kpixFrameRender = totalRenderPix / (fpsTimes.length * 1024);
        long kpixFrameInput = totalInputPix / (fpsTimes.length * 1024);
        //uiMgr.putFloat("fps", fps);
        if (((DEBUG_FPS_RATE != 0 && (fpsTimeIndex % DEBUG_FPS_RATE) == 0) || THREADING_DBG) && Sage.DBG)
          System.out.println("FinalRender thread is executing now fps=" + fps + " KpixRender/frame=" + kpixFrameRender + " KpixInput/frame=" + kpixFrameInput);
      }
      if (watchdog != null && watchdog.isAWTHung() && !animationLock)
      {
        if (THREADING_DBG && Sage.DBG) System.out.println("ZRoot is setting up the wait indicator ROP");
        currRenderEngine.setWaitIndicatorState(true);
        if (!currRenderEngine.supportsWaitCursor())
        {
          java.awt.Rectangle waitArea = getWaitRectArea();
          java.awt.geom.Rectangle2D.Float clipArea = new java.awt.geom.Rectangle2D.Float(waitArea.x, waitArea.y, waitArea.width, waitArea.height);
          java.util.ArrayList waitRops = new java.util.ArrayList();
          for (int i = 0; i < waitImages.size(); i++)
          {
            MetaImage currMI = (MetaImage)waitImages.get(i);
            waitRops.add(new RenderingOp(currMI, currMI.getImageIndex(waitArea.width, waitArea.height), 0xFFFFFF, 1.0f,
                clipArea, waitArea.x, waitArea.y, waitArea.width, waitArea.height));
          }
          currRenderEngine.setWaitIndicatorRenderingOps(waitRops);
        }
      }
      else
      {
        // The rect test is invalid since we're likely just passed the wait rect area anyways!
        // Mac renders wait indicator separately, no need to redraw
        if (!currRenderEngine.supportsWaitCursor() && !Sage.MAC_OS_X && currRenderEngine.getWaitIndicatorState()/* && !currRenderRect.contains(getWaitRectArea())*/)
        {
          // This needs to be redrawn
          appendToDirty(getWaitRectArea());
        }
        currRenderEngine.setWaitIndicatorState(false);
      }
      fpsTimeIndex = fpsTimeIndex % fpsTimes.length;
      fpsTimes[fpsTimeIndex] = Sage.eventTime() - frameStart;
      if (currRenderEngine instanceof DirectX9SageRenderer)
      {
        pixelRenderCounts[fpsTimeIndex] = ((DirectX9SageRenderer) currRenderEngine).getLastPixelRenderCount();
        pixelInputCounts[fpsTimeIndex] = ((DirectX9SageRenderer) currRenderEngine).getLastPixelInputCount();
      }
      else if (currRenderEngine instanceof MiniClientSageRenderer)
      {
        pixelRenderCounts[fpsTimeIndex] = ((MiniClientSageRenderer) currRenderEngine).getLastPixelRenderCount();
        pixelInputCounts[fpsTimeIndex] = ((MiniClientSageRenderer) currRenderEngine).getLastPixelInputCount();
      }
      fpsTimeIndex++;
      frameStart = Sage.eventTime();
      finalAnimsNeedProcessing = false;
      effectsNeedProcessing = false;
      lockedEffectRunning = false;
      if (currRenderEngine.executeDisplayList(currRenderRect))
      {
        currRenderEngine.present(currRenderRect);
        if (THREADING_DBG && Sage.DBG) System.out.println("FinalRenderCycle execution done DURATION=" + (Sage.eventTime() - lastFinishTime));
        lastFinishTime = Sage.eventTime();
      }
      if (SageTV.PERF_TIMING)
        System.out.println("PERF: Final Render: " + (Sage.time() - finalRenderStart));
      if (consumedDL || waitingOnARCycleID != 0)
      {
        synchronized (activeRenderThreadLock)
        {
          if (THREADING_DBG && Sage.DBG) System.out.println("FinalRenderCycle is notifying the AR-2");
          activeRenderThreadLock.notifyAll();
        }
      }
      if (!currRenderEngine.getBGLoader().isAlive())
      {
        if (Sage.DBG) System.out.println("Initiating the BGResourceLoader for the rendering engine");
        currRenderEngine.getBGLoader().start();
      }
    }
    if (didProcessAnims && finalAnims.isEmpty() && !effectsNeedProcessing && renderEngine.hasConsumedNextDisplayList() && didLastRenderHaveFullScreenVideo() &&
        (renderType == REMOTE2DRENDER || (Sage.WINDOWS_OS && renderType == NATIVE3DRENDER && !vf.isColorKeyingEnabled() && vf.isNonBlendableVideoPlayerLoaded())) &&
        !uiMgr.getCurrUI().getUI().hasVisibleNonVideoChildren())
    {
      appendToDirty(MAX_DIRTY);
    }

    if (Thread.currentThread() != finalRenderThread)
    {
      synchronized (finalRenderThreadLock)
      {
        finalRenderThreadLock.notifyAll();
      }
    }
  }

  public void setRoot(ZComp inRoot)
  {
    rootComp = inRoot;
  }

  public ZComp getRoot() { return rootComp; }

  public void update(java.awt.Graphics g)
  {
    paint(g);
  }
  public void paint(java.awt.Graphics g)
  {
    //System.out.println("ZRoot.paint(g) called");
    // The active renderer seems to not repaint the obscured
    // area corectly, but the passive seems to work great even alongside the active one
    //passivelyRender(g);

    // Append the clipped area to our dirty area for the next active redraw
    java.awt.Rectangle clipRect = g.getClipBounds();
    appendToDirty(clipRect == null ? MAX_DIRTY : clipRect);
  }

  public void kill()
  {
    alive = false;
    if (watchdog != null)
      watchdog.kill();
    synchronized (activeRenderThreadLock)
    {
      activeRenderThreadLock.notifyAll();
      if (activeRenderThread.isAlive())
      {
        try
        {
          activeRenderThread.join(2000);
        }catch(Exception e){}
      }
    }
    synchronized (finalRenderThreadLock)
    {
      finalRenderThreadLock.notifyAll();
      if (finalRenderThread.isAlive())
      {
        try
        {
          finalRenderThread.join(2000);
        }catch(Exception e){}
      }
    }
  }

  private static final long MAX_WAIT_FOR_ACTIVE_RENDER_KICK = 10000;
  public void kickActiveRenderer()
  {
    synchronized (activeRenderThreadLock)
    {
      if (THREADING_DBG && Sage.DBG) System.out.println("kickActiveRenderer waiting for next cycle to execute");
      waitingOnARCycleID = arCycleID + 1;
      activeRenderThreadLock.notifyAll();
      long waitStart = Sage.eventTime();
      try
      {
        // For JOGL we can't block since it does stuff on the AWT thread
        while (/*!(renderEngine instanceof JOGLSageRenderer) &&*/ alive && arCycleID < waitingOnARCycleID && !uiMgr.isIgnoringAllEvents() &&
            (Sage.eventTime() - waitStart < MAX_WAIT_FOR_ACTIVE_RENDER_KICK) && !uiMgr.isAsleep()) // waiting for too long just gets silly
        {
          activeRenderThreadLock.wait(1000);
        }
        waitingOnARCycleID = 0;
      }
      catch (InterruptedException e){}
      if (THREADING_DBG && Sage.DBG) System.out.println("kickActiveRenderer FINISHED waiting for next cycle to execute");
    }
  }

  public boolean isActiveRenderGoingToRun(boolean orIsRunning)
  {
    return (!allocatedRenderer || dirtyArea != null || (rootComp != null && rootComp.needsLayout()) || (renderEngine != null && renderEngine.needsRefresh()) || uiMgr.lockPendingOnARRun()) ||
        (orIsRunning && arExecuting);
  }

  public void run()
  {
    while (alive)
    {
      try
      {
        if (THREADING_DBG && Sage.DBG) System.out.println("ActiveRender at the root of its cycle");
        long waitTime = FRAME_TIME;
        boolean[] ranCycle = new boolean[] { false };
        if ((getParent() != null && (isShowing() || !requiresWindow())))
        {
          // This needs to be synced just like anything else that relies on the UI structure
          long currTime = Sage.eventTime();
          if (nextAnimationTime <= currTime)
          {
            // Go through the animation list and recalculate the next animation time, AND
            // perform the animation callback on anything that's due
            boolean[] acquiredLock = new boolean[1];
            nextAnimationTime = Long.MAX_VALUE;
            if (uiMgr.getLock(false, acquiredLock))
            {
              try
              {
                synchronized (animations)
                {
                  for (int i = 0; i < animations.size(); i++)
                  {
                    Animation currAnim = (Animation) animations.get(i);
                    if (currAnim.time <= currTime)
                    {
                      if (THREADING_DBG) System.out.println("AnimationCallback into " + currAnim.comp);
                      currAnim.comp.animationCallback(currAnim.time);
                    }
                  }
                }
              }
              finally
              {
                if (acquiredLock[0])
                  uiMgr.clearLock();
              }
            }
            synchronized (animations)
            {
              for (int i = 0; i < animations.size(); i++)
              {
                Animation currAnim = (Animation) animations.get(i);
                nextAnimationTime = Math.min(nextAnimationTime, currAnim.time);
              }
            }
          }
          // Process any transition cleanup operations
          if (!activeTransitionSources.isEmpty())
          {
            boolean fireNext = false;
            boolean firePrev = false;
            synchronized (activeTransitionSources)
            {
              for (int i = 0; i < activeTransitionSources.size(); i++)
              {
                ZDataTable src = (ZDataTable) activeTransitionSources.get(i);
                if (uiMgr.isXBMCCompatible() || (!src.isScrollEffectStillActive() && (activeFocusTracker == null || !activeFocusTracker.effectTracker.isActive())))
                {
                  if (src.isDoingNextTransition())
                    fireNext = true;
                  if (src.isDoingPrevTransition())
                    firePrev = true;
                  src.resetTransitionFlags();
                  activeTransitionSources.remove(i--);
                }
              }
            }
            if (fireNext || firePrev)
            {
              boolean[] acquiredLock = new boolean[1];
              if (uiMgr.getLock(false, acquiredLock))
              {
                try
                {
                  if (fireNext)
                    uiMgr.getCurrUI().getUI().evaluateTransitionListeners(true);
                  if (firePrev)
                    uiMgr.getCurrUI().getUI().evaluateTransitionListeners(false);
                }
                finally
                {
                  if (acquiredLock[0])
                    uiMgr.clearLock();
                }
              }
            }
          }
          // We always have to render if the video stream is playing and we're using D3D rendering
          //if (shiznit && vf.isPlayin() && uiMgr.isTV())
          //	dirtyArea = MAX_DIRTY;
          if (!allocatedRenderer || dirtyArea != null || rootComp.needsLayout() || renderEngine.needsRefresh()/* || waitingOnARCycleID != 0*/ || uiMgr.lockPendingOnARRun())
          {
            long renderTime = activelyRender(ranCycle);
            renderTime = Math.max(renderTime, 1);
            waitTime -= renderTime;
            if (THREADING_DBG && Sage.DBG) System.out.println("ActiveRender cycle took " + renderTime + " msec");
          }
          else
          {
            if (waitingOnARCycleID != 0)
            {
              waitingOnARCycleID = 0;
              synchronized (activeRenderThreadLock)
              {
                activeRenderThreadLock.notifyAll();
              }
            }
          }
        }
        else if (waitingOnARCycleID != 0)
        {
          waitingOnARCycleID = 0;
          synchronized (activeRenderThreadLock)
          {
            activeRenderThreadLock.notifyAll();
          }
        }

        synchronized (activeRenderThreadLock)
        {
          if (!renderEngine.hasConsumedNextDisplayList())
          {
            if (THREADING_DBG && Sage.DBG) System.out.println("ActiveRender is waiting for its DL to be consumed");
            do
            {
              try { activeRenderThreadLock.wait(FRAME_TIME); } catch (Exception e){}
            }
            while (alive && !renderEngine.hasConsumedNextDisplayList() && !uiMgr.lockPendingOnARRun());
            if (THREADING_DBG && Sage.DBG) System.out.println("ActiveRender is DONE waiting for its DL to be consumed");
          }
          if (renderEngine.hasConsumedNextDisplayList() && waitingOnARCycleID == 0 &&
              !ranCycle[0] && waitTime > 0)
          {
            if (THREADING_DBG && Sage.DBG) System.out.println("ActiveRender is waiting for " + waitTime + " in idle");
            activeRenderThreadLock.wait(waitTime);
          }
        }
      }
      catch (Throwable t)
      {
        System.out.println("Active Render had an error:" + t);
        t.printStackTrace();
      }
    }
  }

  public void appendToDirty(java.awt.Rectangle rootRect)
  {
    if (rootRect == null || rootRect.width <= 0 || rootRect.height <= 0) return;
    synchronized (dirtyLock)
    {
      if (dirtyArea == null)
        dirtyArea = new java.awt.Rectangle(rootRect);
      else if (dirtyArea != MAX_DIRTY)
        dirtyArea = dirtyArea.union(rootRect);

      if (THREADING_DBG && Sage.DBG)
      {
        System.out.println("ZRoot.appendToDirty(" + rootRect + ") newDirtyArea=" + dirtyArea);
        //				Thread.dumpStack();
      }
    }
  }

  private void setupOSDRenderingVars()
  {
    boolean use350OSD = renderEngine instanceof Java2DSageRenderer && ((Java2DSageRenderer) renderEngine).hasOSDRenderer();

    forceRenderSize = false;
    fixMousePositions = false;
    if (renderType == REMOTE2DRENDER || (use350OSD && renderType == JAVA2DRENDER))
    {
      forceRenderSize = true;
      forcedRenderWidth = uiMgr.getInt("osd_rendering_width", 720);
      forcedRenderHeight = MMC.getInstance().isNTSCVideoFormat() ?
          uiMgr.getInt("osd_rendering_height_ntsc", 480) : uiMgr.getInt("osd_rendering_height_pal", 576);
          fixMousePositions = false;
    }
  }

  /*
   * NOTE: REMEMBER THAT WHEN HW ACCELERATION IS DOING THE RENDERING, WE'LL BE WAITING BUT
   * WE WON'T BE USING ANY CPU
   */
  private long activelyRender(boolean[] ranCycle)
  {
    long startCpu = Sage.getCpuTime();
    if (THREADING_DBG && Sage.DBG) System.out.println("ACTIVE starting layout... dirtyArea=" + dirtyArea + " rootDirty=" +
        rootComp.needsLayout() + " arcyclewait=" + waitingOnARCycleID + " this=" + this);
    if (getWidth() <= 0 || getHeight() <= 0 || (((!isShowing() && requiresWindow()) || getParent() == null)))
    {
      synchronized (activeRenderThreadLock)
      {
        arCycleID++;
        activeRenderThreadLock.notifyAll();
      }
      ranCycle[0] = false;
      return 0;
    }

    setupOSDRenderingVars();
    //		synchronized (renderEngineLock)
    {
      prepare();
    }

    if ("VWB".equals(SageTV.system))
    {
      if (lastVideoOutputPort == null || !lastVideoOutputPort.equals(uiMgr.get("linux/video_output_port", "Composite_Svideo")))
      {
        lastVideoOutputPort = uiMgr.get("linux/video_output_port", "Composite_Svideo");
        if ("Component".equals(lastVideoOutputPort))
        {
          IOUtils.exec2("./vwbypbpr", false);
        }
        else if ("Composite_Svideo".equals(lastVideoOutputPort))
        {
          IOUtils.exec2("./vwbsvideo", false);
        }
      }
    }

    java.awt.geom.Rectangle2D.Float targetDirty = null;
    boolean[] acquiredLock = new boolean[1];
    if ((!allocatedRenderer || dirtyArea != null || rootComp.needsLayout() || renderEngine.needsRefresh() || uiMgr.lockPendingOnARRun()) && uiMgr.getLock(false, acquiredLock, false, true))
    {
      java.awt.geom.Rectangle2D.Float repaintClipRect;
      java.util.ArrayList displayList;
      boolean isFileLoaded = false;
      try
      {
        synchronized (activeRenderThreadLock)
        {
          arCycleID++;
          activeRenderThreadLock.notifyAll();
        }
        boolean dirtyWasNull = dirtyArea == null;
        if (forceRenderSize)
        {
          rootComp.setBounds(0, 0, forcedRenderWidth, forcedRenderHeight);
        }
        else
        {
          rootComp.setBounds(0, 0, getWidth(), getHeight());
        }

        // Set some field vars that we an use for properties that won't change across a single render cycle
        scalingInsetsBaseHeight = uiMgr.getInt("ui/scaling_insets_base_height", 480);
        scalingInsetsBaseWidth = uiMgr.getInt("ui/scaling_insets_base_width", 720);
        minShrunkFontSize = uiMgr.getInt("ui/min_allowed_shrunk_font_size", 14);
        textShadowNever = uiMgr.getBoolean("ui/text_shadow_never", false);
        enableCornerArc = Sage.getBoolean("ui/enable_image_corner_arc", false);

        // NOTE: This really isn't the last height; but a factor we can re
        int currMainAreaHeight = (int)(rootComp.getHeight() * uiMgr.getOverscanScaleHeight());
        int currMainAreaWidth = (int)(rootComp.getWidth() * uiMgr.getOverscanScaleWidth());
        currMinFontSize = uiMgr.getInt("ui/min_allowed_font_size", 9);
        if (currMainAreaHeight != lastMainAreaHeight || currMinFontSize != lastMinFontSize)
          rootComp.recalculateDynamicFonts();

        mainSizeChanged = (currMainAreaHeight != lastMainAreaHeight) || (currMainAreaWidth != lastMainAreaWidth);
        arExecuting = true;
        lastMainAreaHeight = currMainAreaHeight;
        lastMainAreaWidth = currMainAreaWidth;
        lastMinFontSize = currMinFontSize;
        if (SageTV.PERF_TIMING)
          System.out.println("PERF: ActiveRender Starting");
        rootComp.doLayout();
        if (SageTV.PERF_TIMING)
          System.out.println("PERF: ActiveRender Did Layout: " + (Sage.getCpuTime() - startCpu)*1000/freq);
        synchronized (dirtyLock)
        {
          if (dirtyArea != null && dirtyArea != MAX_DIRTY)
          {
            targetDirty = new java.awt.geom.Rectangle2D.Float();
            targetDirty.setRect(dirtyArea);
          }
          dirtyArea = null;
        }

        // If the dirty area was null & still is null then we don't need to render anything. The reason
        // we're here is because invalidLayout was set. We just ran through the layout and nothing
        // became dirty; this is of course possible since the invalidLayout flag throws itself up
        // when just about anything changes.
        if (renderType != NATIVE3DRENDER && lastRenderVideoRect == null && lastAnimArea == null && dirtyWasNull && targetDirty == null &&
            allocatedRenderer && lostXtnAnimRops.isEmpty() && !renderEngine.needsRefresh())
        {
          if (Sage.DBG) System.out.println("ZRoot is aborting this draw because its unnecessary");
          ranCycle[0] = false;
          arExecuting = false;
          return (Sage.getCpuTime() - startCpu)*1000/freq;
        }

        arIsBuildingDL = true;
        // Complete any lost transition animations now before we build the rendering ops. This'll allow the beginning
        // of the transition to be setup in one menu state and then the next menu state can contain the target for it.
        if (!lostXtnAnimRops.isEmpty())
        {
          if (uiMgr.areLayersEnabled())
          {
            if (THREADING_DBG) System.out.println("Lost transition animation ops getting added to DL: " + lostXtnAnimRops);
            for (int i = 0; i < lostXtnAnimRops.size(); i++)
            {
              Object[] ropData = (Object[]) lostXtnAnimRops.get(i);
              RenderingOp lop = (RenderingOp) ropData[0];
              String destWidgName = (String) ropData[1];
              uiMgr.getCurrUI().getUI().setupTransitionAnimation(destWidgName, lop.surface, lop);
            }
          }
          lostXtnAnimRops.clear();
        }

        // Unfreshen all of the trackers so we know which to clear after this rendering pass
        java.util.Iterator walker = trackerEffectMap.values().iterator();
        while (walker.hasNext())
          ((EffectTracker) walker.next()).unfreshenTracker();

        // we also need to render everything if there's fullscreen video in the non-accelerated UI
        // or we won't get the window regions correct, but this is only used if color keying is disabled
        repaintClipRect = targetDirty;
        MediaFile mf = vf.getCurrFile();
        // Trackers need to have the whole UI processed every time; we could most likely optimize this later so full UI
        // rendering is not needed every time if the effects animations are enabled; but not just yet....
        if (!renderEngine.supportsPartialUIUpdates() || uiMgr.getBoolean("ui/force_full_ui_rendering", false) || uiMgr.areEffectsEnabled())
        {
          targetDirty = null; // force rendering of everything, this is essential if there's video playing
          repaintClipRect = null;
        }
        else
        {
          if (lastRenderVideoRect != null && renderType != REMOTE2DRENDER &&
              (/*!Sage.getBoolean("overlay_color_keying_enabled", true)*/!vf.isColorKeyingEnabled() ||
                  (mf != null && mf.isMusic() && Sage.WINDOWS_OS))) // for music visualizations
          {
            // We can't just check the color keying property because we may be using a renderer that doesn't support color keying
            // yet the user has the property enabled.
            repaintClipRect = targetDirty;
            targetDirty = (java.awt.geom.Rectangle2D.Float)
                ((targetDirty == null) ? null : lastRenderVideoRect.createUnion(targetDirty));
          }
          // If there was animation in the last UI update then we need to include that in our dirty region
          if (lastAnimArea != null)
          {
            if (repaintClipRect != null)
              java.awt.Rectangle.union(repaintClipRect, lastAnimArea, repaintClipRect);
            if (targetDirty != null)
              java.awt.Rectangle.union(targetDirty, lastAnimArea, targetDirty);
          }
          if (lastFinalAnimArea != null)
          {
            if (repaintClipRect != null)
              java.awt.Rectangle.union(repaintClipRect, lastFinalAnimArea, repaintClipRect);
            if (targetDirty != null)
              java.awt.Rectangle.union(targetDirty, lastFinalAnimArea, targetDirty);
          }
        }
        // Note below about why we have to do this here
        isFileLoaded = vf.isLoadingOrLoadedFile();
        if (Sage.DBG && THREADING_DBG) System.out.println("ACTIVE building DL targetDirty=" + targetDirty);
        displayList = new java.util.ArrayList();
        if (targetDirty == null)
        {
          if (uiMgr.disableParentClip())
            targetDirty = new java.awt.geom.Rectangle2D.Float(-rootComp.getWidth(), -rootComp.getHeight(), 3*rootComp.getWidth(), 3*rootComp.getHeight());
          else
            targetDirty = new java.awt.geom.Rectangle2D.Float(0, 0, rootComp.getWidth(), rootComp.getHeight());
        }
        /*				else
				{
					if (targetDirty.x < 0)
					{
						targetDirty.width += -targetDirty.x;
						targetDirty.x = 0;
					}
					if (targetDirty.y < 0)
					{
						targetDirty.height += -targetDirty.y;
						targetDirty.y = 0;
					}
					if (targetDirty.x + targetDirty.width > rootComp.getWidth())
						targetDirty.width -= (targetDirty.x + targetDirty.width) - rootComp.getWidth();
					if (targetDirty.y + targetDirty.height > rootComp.getHeight())
						targetDirty.height -= (targetDirty.y + targetDirty.height) - rootComp.getHeight();
				}*/
        if (repaintClipRect == null)
          repaintClipRect = new java.awt.geom.Rectangle2D.Float(0, 0, rootComp.getWidth(), rootComp.getHeight());
        rootComp.buildRenderingOps(displayList, targetDirty, 0xFFFFFF, 1.0f,
            0, 0, 0);
        lastRenderTime = Sage.eventTime();
        if (SageTV.PERF_TIMING)
          System.out.println("PERF: ActiveRender Built ROPs: " + (Sage.getCpuTime() - startCpu)*1000/freq);
        // We have the UI lock here so it's safe to clear this focus op so it doesn't linger over a single
        // menu transition
        lostFocusOp = null;
      }
      finally
      {
        if (acquiredLock[0])
          uiMgr.clearLock();
      }
      // Remove all of the unfresh trackers since they are stale and didn't appear in this rendering pass
      java.util.Iterator walker = trackerEffectMap.values().iterator();
      while (walker.hasNext())
        if (!((EffectTracker) walker.next()).isFreshTracker())
          walker.remove();

      boolean lastRenderHadVideo = lastRenderVideoRect != null && lastRenderVideoRect.width > 0 &&
          lastRenderVideoRect.height > 0;
          java.awt.geom.Rectangle2D.Float savedLastVidRect = lastRenderVideoRect;
          lastRenderVideoRect = null;
          // If there's final render animation regions that we just painted and now we get
          // a new dirty region from the active render system then we need to force a dirtyRegion
          // on the portions of the animation area that are not intersected by the new active render area
          // BUT the activeRender is what's creating the lists so it should monitor this area
          // itself by creating an intersection between the animated areas and then if it does an update
          // again next time it'll intersect with those....very similar to the video region update
          // when doing window regioning.
          // NOTE: We had to add the lastFinalAnimArea because of the focus animation interruption. Since now
          // the last dirty area may not just be what was the last active clip rect because we could be going back
          // more than one cycle in order to calculate the animation start point (because we could have interrupted one that's
          // got a starting position that's from an interruption so we could be more than one unit off our start)
          lastAnimArea = null;
          // If we've already got animations in the lostAnimRops list then we need to have a BG surface to animate with
          boolean surfOrAnimOps = !lostAnimRops.isEmpty() && uiMgr.areLayersEnabled();
          activeFocusTracker = null;
          for (int i = 0; i < displayList.size(); i++)
          {
            RenderingOp ro = (RenderingOp) displayList.get(i);
            if (ro.isVideoOp())
            {
              lastRenderVideoRect = ro.destRect;
              if (uiMgr.areLayersEnabled() && renderEngine instanceof DirectX9SageRenderer && !vf.isNonBlendableVideoPlayerLoaded())
              {
                // We need to do the BG surface in this case so that the post-compositing works in the renderer
                surfOrAnimOps = true;
              }
              continue;
            }
            else if (uiMgr.areLayersEnabled())
            {
              if (ro.isAnimationOp())
              {
                surfOrAnimOps = true;
                if (lastAnimArea == null)
                  lastAnimArea = ro.getAnimationArea(rootComp.getWidth(), rootComp.getHeight());
                else
                  java.awt.Rectangle.union(lastAnimArea, ro.getAnimationArea(rootComp.getWidth(), rootComp.getHeight()), lastAnimArea);
              }
              else if (ro.isSurfaceOp())
                surfOrAnimOps = true;
            }
            else if (ro.isEffectOp() && ro.effectTracker != null && ro.effectTracker.getTrigger() == EffectTracker.TRIGGER_FOCUSTRACKER)
              activeFocusTracker = ro;
          }
          if (surfOrAnimOps)
          {
            // If the whole display list is already contained by a BG surface then don't insert our own
            boolean foundBGSurf = false;
            String prefBGSurfName = uiMgr.get("ui/animation/background_surface_name", "BG");
            for (int i = 0; i < displayList.size(); i++)
            {
              RenderingOp ro = (RenderingOp) displayList.get(i);
              if (ro.isAnimationOp())
                continue;
              else if (ro.isSurfaceOp() && prefBGSurfName.equals(ro.surface))
              {
                foundBGSurf = true;
                break;
              }
              else
                break;
            }
            if (!foundBGSurf)
            {
              // Put in the BG surface operations
              java.awt.geom.Rectangle2D.Float bgArea = new java.awt.geom.Rectangle2D.Float(Math.max(0, repaintClipRect.x),
                  Math.max(0, repaintClipRect.y),
                  Math.min(rootComp.getWidth(), repaintClipRect.width),
                  Math.min(rootComp.getHeight(), repaintClipRect.height));
              displayList.add(0, new RenderingOp(prefBGSurfName, bgArea, true));
              displayList.add(new RenderingOp(prefBGSurfName, bgArea, false));
            }
          }

          // Check for default video playback initiation
          boolean thisRenderHasVideo = lastRenderVideoRect != null && lastRenderVideoRect.width > 0 &&
              lastRenderVideoRect.height > 0;
              // If the dirty area doesn't cover the last video rect, then we still have video.
              if (lastRenderHadVideo && !thisRenderHasVideo && !savedLastVidRect.intersects(targetDirty))
              {
                thisRenderHasVideo = true;
                lastRenderVideoRect = savedLastVidRect;
              }
              // NOTE On 4/13/04 I added " && !lastRenderHadVideo", this should prevent loading the media
              // player automatically after we try to unload it. So ZRoot will only launch the video now
              // on positive edge triggers.
              // NOTE 7/25/05 - I added another condition for menu transitions also being considered positive
              // edge triggers. Otherwise if you close the video on menu exit and go to another menu with video that
              // video won't be shown.
              // NOTE 3/25/06 - If on the first DL generation for a menu the player is up and a video widget is used, and then
              // before we get to this point, the player is closed, then there's an inconsistency. So we check the player
              // load state before we build the DL to avoid this problem.
              if (thisRenderHasVideo && ("XAlways".equals(uiMgr.get("display_video_on_menus", null)) || !lastRenderHadVideo ||
                  lastActiveMenu != uiMgr.getCurrUI()))
              {
                if (!isFileLoaded)
                {
                  Pooler.execute(new Runnable()
                  {
                    public void run(){ uiMgr.watchTV(uiMgr.getBoolean("ui/default_video_live_seek", false)); }
                  }, "DefaultAsyncWatch");
                }
              }

              lastActiveMenu = uiMgr.getCurrUI();

              // Check for forcing/unforcing a pause
              if (thisRenderHasVideo && forcedPause)
              {
                vf.play();
                forcedPause = false;
              }
              else if (thisRenderHasVideo && forcedMute)
              {
                vf.setMute(false);
                forcedMute = false;
              }


              MediaFile mf = vf.getCurrFile();
              if (lastRenderHadVideo && !thisRenderHasVideo && mf != null && mf.hasVideoContent() && vf.getPlayerState() == MediaPlayer.PLAY_STATE)
              {
                if (mf.isAnyLiveStream() || vf.isLivePreview())
                {
                  if (!vf.getMute())
                  {
                    vf.setMute(true);
                    forcedMute = true;
                  }
                }
                else
                {
                  vf.pause();
                  forcedPause = true;
                }
              }

              SageRenderer currRenderEngine;
              synchronized (renderEngineLock)
              {
                currRenderEngine = renderEngine;
              }
              if (!lostAnimRops.isEmpty())
              {
                if (uiMgr.areLayersEnabled())
                {
                  if (THREADING_DBG) System.out.println("Lost animation ops getting added to DL: " + lostAnimRops);
                  for (int i = 0; i < lostAnimRops.size(); i++)
                  {
                    RenderingOp lop = (RenderingOp) lostAnimRops.get(i);
                    if (lastAnimArea == null)
                      lastAnimArea = lop.getAnimationArea(rootComp.getWidth(), rootComp.getHeight());
                    else
                      java.awt.Rectangle.union(lastAnimArea, lop.getAnimationArea(rootComp.getWidth(), rootComp.getHeight()), lastAnimArea);
                    displayList.add(lop);
                  }
                }
                lostAnimRops.clear();
              }
              if (SageTV.PERF_TIMING)
                System.out.println("PERF: ActiveRender Almost Done: " + (Sage.getCpuTime() - startCpu)*1000/freq);
              // NOTE: 1/31/06 - We have to sync on the final render thread when we set the next display list & updates the dirty area or its
              // possible for the final render thread to get the wrong clip region
              // NARFLEX - 12/15/09 - We broke this up into 2 parts so that we can do parallel preloading of resources and not break
              // the sync logic we mention above which ties the DL to the renderNeeded area
              currRenderEngine.preprocessNextDisplayList(displayList);
              arExecuting = false;
              synchronized (finalRenderThreadLock)
              {
                currRenderEngine.setNextDisplayList(displayList);

                if (SageTV.PERF_TIMING)
                  System.out.println("PERF: ActiveRender Done: " + (Sage.getCpuTime() - startCpu)*1000/freq);
                //			if (vf.isPlayin() && vf.hasFile() &&
                //				uiMgr.isTV() && vf.getUseVmr() && shiznit)
                //			{
                // We only render when told to by the VMR in this situation
                //			}
                //			else
                //			{
                renderNeeded = new java.awt.Rectangle(Math.max(0, Math.round(repaintClipRect.x)),
                    Math.max(0, Math.round(repaintClipRect.y)),
                    Math.min(rootComp.getWidth(), Math.round(repaintClipRect.width)),
                    Math.min(rootComp.getHeight(), Math.round(repaintClipRect.height)));
                finalRenderThreadLock.notifyAll();
                //				}
              }
              arIsBuildingDL = false;
              ranCycle[0] = true;
              Sage.gc();
    }
    else
    {
      synchronized (activeRenderThreadLock)
      {
        arCycleID++;
        activeRenderThreadLock.notifyAll();
      }
      ranCycle[0] = false;
    }
    return (Sage.getCpuTime() - startCpu)*1000/freq;
  }

  public java.awt.Dimension getPreferredSize()
  {
    return new java.awt.Dimension(Integer.MAX_VALUE,
        Integer.MAX_VALUE);
  }
  public java.awt.Dimension getMinimumSize()
  {
    return new java.awt.Dimension(Integer.MAX_VALUE,
        Integer.MAX_VALUE);
  }
  public java.awt.Dimension getMaximumSize()
  {
    return new java.awt.Dimension(Integer.MAX_VALUE,
        Integer.MAX_VALUE);
  }

  public void hierarchyChanged(java.awt.event.HierarchyEvent e)
  {
    if (isShowing())
      rootComp.appendToDirty(true);
    else if (!isShowing())
      hideTipWindow();
  }

  public void componentMoved(java.awt.event.ComponentEvent evt){}
  public void componentHidden(java.awt.event.ComponentEvent evt){}
  public void componentShown(java.awt.event.ComponentEvent evt)
  {
  }
  public void componentResized(java.awt.event.ComponentEvent evt)
  {
    //System.out.println("Z RESIZE");
    boolean[] gotLock = new boolean[1];
    if (!uiMgr.getLock(true, gotLock, true))
      return;
    try
    {
      rootComp.appendToDirty(true);
    }
    finally
    {
      if (gotLock[0])
        uiMgr.clearLock();
    }
  }

  protected void finalize() { cleanse(); }

  public void cleanse()
  {
  }

  private boolean preparedYet = false;
  public void reprepRenderer() { preparedYet = false; }
  public void prepare()
  {
    if (preparedYet) return;
    preparedYet = true;
    // Make the buffers the size of the screen, the clip rect will take care of the rest
    if (uiMgr.getGlobalFrame() != null)
    {
      java.awt.Dimension scrSize = uiMgr.getScreenSize();
      if (renderType == NATIVE3DRENDER)
      {
        //scrSize.width = Sage.getInt("ui/fixed_resolution_width", 720);
        //scrSize.height = Sage.getInt("ui/fixed_resolution_height", 480);
        uiMgr.getGlobalFrame().setFixedClientSize(null);
      }
      //			else if (renderType == NATIVE3DRENDER) // Linux 3D-EAVIOS
      //			{
      //				uiMgr.getGlobalFrame().setFixedClientSize(scrSize);
      //			}
      else if ((renderEngine instanceof Java2DSageRenderer && ((Java2DSageRenderer) renderEngine).hasOSDRenderer()) || renderType == REMOTE2DRENDER)
      {
        if (Sage.WINDOWS_OS)
          Sage.writeDwordValue(Sage.HKEY_LOCAL_MACHINE,
              "SYSTEM\\CurrentControlSet\\Services\\Globespan\\Parameters\\ivac15\\Driver",
              "HcwTVOutColorBars", 0);
        scrSize.width = uiMgr.getInt("osd_rendering_width", 720);
        scrSize.height = MMC.getInstance().isNTSCVideoFormat() ?
            uiMgr.getInt("osd_rendering_height_ntsc", 480) : uiMgr.getInt("osd_rendering_height_pal", 576);
            uiMgr.getGlobalFrame().setFixedClientSize(scrSize);
      }
      else
        uiMgr.getGlobalFrame().setFixedClientSize(null);
    }
    /*if (!renderEngine.allocateBuffers(scrSize.width, scrSize.height))
		{
			// Failed creating the render engine
			if (shiznit)
			{
				renderEngine.cleanupRenderer();
				shiznit = false;
				System.out.println("Sage had a failure loading the accelerated renderer, defaulting to software rendering.");
				renderEngine = new Java2DSageRenderer(this);
				renderEngine.allocateBuffers(scrSize.width, scrSize.height);
			}
		}*/
  }

  public void mouseClicked(java.awt.event.MouseEvent e) { analyzeMouseEvent(e); }
  public void mouseEntered(java.awt.event.MouseEvent e) { analyzeMouseEvent(e); }
  public void mouseExited(java.awt.event.MouseEvent e)
  {
    /*
     * NOTE: JK 8/31/05 - This is here because without it we get a native exception when we
     * exit FSE on Windows using NVidia hardware. This should NOT be happening, but its
     * probably due to something wrong in Windows/NVidia's drivers...
     */
    /*if(true) return;*/ analyzeMouseEvent(e);
  }
  public void mousePressed(java.awt.event.MouseEvent e) { analyzeMouseEvent(e); }
  public void mouseReleased(java.awt.event.MouseEvent e) { analyzeMouseEvent(e); }
  public void mouseDragged(java.awt.event.MouseEvent e) { analyzeMouseEvent(e); }
  public void mouseMoved(java.awt.event.MouseEvent e) { analyzeMouseEvent(e); }
  private void analyzeMouseEvent(java.awt.event.MouseEvent evt)
  {
    mouseEvtCounter = (mouseEvtCounter + 1) % 1000000;
    if (uiMgr.isIgnoringAllEvents() || uiMgr.getBoolean("studio/discard_mouse_events", false)) return;
    if (fixMousePositions)
    {
      evt.translatePoint(Math.round(evt.getX()*(((float)forcedRenderWidth)/getWidth() - 1)),
          Math.round(evt.getY()*(((float)forcedRenderHeight)/getHeight() - 1)));
    }
    // convert Control-Click events to right mouse button unless disabled
    //		if(evt.getID() != java.awt.event.MouseEvent.MOUSE_MOVED) System.out.println("click="+evt);
    if(Sage.MAC_OS_X && Sage.getBoolean("ui/enable_control_click_for_right_mouse", true) &&
        evt.isControlDown() && (evt.getButton() == java.awt.event.MouseEvent.BUTTON1))
    {
      switch(evt.getID()) {
        case java.awt.event.MouseEvent.MOUSE_PRESSED:
          try {
            java.awt.Robot clicky = new java.awt.Robot();
            clicky.mousePress(java.awt.event.MouseEvent.BUTTON3_MASK);
          } catch (Throwable t) {};
          return;
        case java.awt.event.MouseEvent.MOUSE_RELEASED:
          try {
            java.awt.Robot clicky = new java.awt.Robot();
            clicky.mouseRelease(java.awt.event.MouseEvent.BUTTON3_MASK);
          } catch (Throwable t) {};
          return;
        case java.awt.event.MouseEvent.MOUSE_CLICKED:
          // just absorb these
          return;
        default:
          break;
      }
    }
    // Drags are a special case
    if (evt.getID() == java.awt.event.MouseEvent.MOUSE_DRAGGED && pressure != null)
    {
      java.awt.event.MouseEvent evt3 = new java.awt.event.MouseEvent((java.awt.Component)evt.getSource(),
          java.awt.event.MouseEvent.MOUSE_DRAGGED, evt.getWhen(), evt.getModifiers(), evt.getX() - pressure.getTrueX() - pressure.getHitAdjustX(),
          evt.getY() - pressure.getTrueY() - pressure.getHitAdjustY(),
          evt.getClickCount(), evt.isPopupTrigger());
      evt3.setSource(pressure);
      pressure.processMouseEvent(evt3);
      lastWasTrackedDrag = true;
      return;
    }
    if (lastWasTrackedDrag && (evt.getID() == java.awt.event.MouseEvent.MOUSE_ENTERED || evt.getID() == java.awt.event.MouseEvent.MOUSE_EXITED))
    {
      // When we're dragging we can do exit/enter on the component....but once that's done we stop the drag
      return;
    }
    lastWasTrackedDrag = false;
    // If we get a new mouse event that's not over the region for the press we've sent, clear
    // that press with an exit event.
    ZComp prevMouseSrc = lastMouseSrc;
    int ptx = pressure == null ? 0 : (pressure.getTrueX() + pressure.getHitAdjustX());
    int pty = pressure == null ? 0 : (pressure.getTrueY() + pressure.getHitAdjustY());
    if (pressure != null && (evt.getX() < ptx || evt.getX() >= pressure.getHitWidth() + ptx ||
        evt.getY() < pty || evt.getY() >= pressure.getHitHeight() + pty))
    {
      java.awt.event.MouseEvent evt3 = new java.awt.event.MouseEvent((java.awt.Component)evt.getSource(),
          java.awt.event.MouseEvent.MOUSE_EXITED, evt.getWhen(), evt.getModifiers(), evt.getX(), evt.getY(),
          evt.getClickCount(), evt.isPopupTrigger());
      evt3.setSource(pressure);
      pressure.processMouseEvent(evt3);
      pressure = null;
      enteredMouseComp = null;
    }
    if (!analyzeMouseEvent(rootComp, evt, evt.getX(), evt.getY()))
    {
      lastMouseSrc = null;
      hideTipWindow();
      if (enterTimer != null)
      {
        enterTimer.cancel();
        enterTimer = null;
      }
    }
    else
    {
      if (enterTimer != null)
      {
        enterTimer.cancel();
        enterTimer = null;
      }
      if (prevMouseSrc != lastMouseSrc)
      {
        if (tipShowing && lastMouseSrc.getTip() == null)
          hideTipWindow();
        else if (tipShowing)
          showTipWindow();
      }

      if (!tipShowing && lastMouseSrc.getTip() != null)
      {
        uiMgr.addTimerTask(enterTimer = new InsideTimerAction(), 750, 0);
      }
    }
  }
  private boolean analyzeMouseEvent(ZComp testComp, java.awt.event.MouseEvent evt, int x, int y)
  {
    if (testComp != null && (uiMgr.disableParentClip() || (x >= 0 && x < testComp.getHitWidth() && y >= 0 && y < testComp.getHitHeight())))
    {
      if (testComp.isMouseTransparent() && !uiMgr.isSingularMouseTransparency())
        return false;
      // It's insides this comp, but it might be for a child instead. Do it
      // in reverse order so it corresponds to what is painted on the screen in the
      // case of overlapping components.
      ZComp[] kids = testComp.getZOrderCache();
      for (int i = testComp.getNumKids() - 1; i >= 0; i--)
      {
        ZComp currKid = kids[i];
        if (currKid != null && currKid.isVisible() && analyzeMouseEvent(currKid, evt, x - currKid.getHitX(), y - currKid.getHitY()))
          return true;
      }
      if ((testComp.isMouseTransparent() && uiMgr.isSingularMouseTransparency()) ||
          (uiMgr.disableParentClip() && (x < 0 || x >= testComp.getHitWidth() || y < 0 || y >= testComp.getHitHeight())))
        return false;
      if (enteredMouseComp != testComp)
      {
        if (enteredMouseComp != null)
        {
          // Send an exit event to the component we've left
          java.awt.event.MouseEvent evt3 = new java.awt.event.MouseEvent((java.awt.Component)evt.getSource(),
              java.awt.event.MouseEvent.MOUSE_EXITED, evt.getWhen(), evt.getModifiers(), evt.getX(), evt.getY(),
              evt.getClickCount(), evt.isPopupTrigger());
          evt3.setSource(enteredMouseComp);
          enteredMouseComp.processMouseEvent(evt3);
          enteredMouseComp = null;
        }
        enteredMouseComp = testComp;
        // Send an enter event to the component we've entered
        java.awt.event.MouseEvent evt4 = new java.awt.event.MouseEvent((java.awt.Component)evt.getSource(),
            java.awt.event.MouseEvent.MOUSE_ENTERED, evt.getWhen(), evt.getModifiers(), evt.getX(), evt.getY(),
            evt.getClickCount(), evt.isPopupTrigger());
        evt4.setSource(enteredMouseComp);
        enteredMouseComp.processMouseEvent(evt4);
      }
      java.awt.event.MouseEvent evt2 = new java.awt.event.MouseEvent((java.awt.Component)evt.getSource(),
          evt.getID(), evt.getWhen(), evt.getModifiers(), x, y, evt.getClickCount(), evt.isPopupTrigger());
      evt2.setSource(testComp);
      testComp.processMouseEvent(evt2);
      lastMouseSrc = testComp;
      if (evt2.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED)
      {
        pressure = testComp;
        hideTipWindow();
        if (enterTimer != null)
        {
          enterTimer.cancel();
          enterTimer = null;
        }
      }
      else if (evt2.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED && pressure == testComp)
        pressure = null;
      return true;
    }
    return false;
  }

  private void showTipWindow()
  {
    if (lastMouseSrc == null || lastMouseSrc.getTip() == null)
    {
      return;
    }
    java.awt.Dimension size;
    java.awt.Point screenLocation = getLocationOnScreen();
    if (fixMousePositions)
    {
      float scaleX = ((float)forcedRenderWidth)/getWidth();
      float scaleY = ((float)forcedRenderHeight)/getHeight();
      screenLocation.x += Math.round(lastMouseSrc.getTrueX()/scaleX);
      screenLocation.y += Math.round(lastMouseSrc.getTrueY()/scaleY);
    }
    else
    {
      screenLocation.x += lastMouseSrc.getTrueX();
      screenLocation.y += lastMouseSrc.getTrueY();
    }
    java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
    java.awt.Point location = new java.awt.Point();

    if (tip == null)
    {
      tip = new java.awt.Label()
      {
        /*
         * This is to deal with JDC Bug #4383948 (which is closed, not a bug)
         */
        public java.awt.Dimension getMinimumSize()
        {
          java.awt.Dimension retVal = super.getMinimumSize();
          return new java.awt.Dimension(retVal.width - 10, retVal.height - 6);
        }
        public java.awt.Dimension getPreferredSize()
        {
          java.awt.Dimension retVal = super.getPreferredSize();
          return new java.awt.Dimension(retVal.width - 10, retVal.height - 6);
        }
        public java.awt.Insets getInsets()
        {
          return new java.awt.Insets(1, 1, 1, 1);
        }
        public void paint(java.awt.Graphics g)
        {
          super.paint(g);
          java.awt.Color oldColor = g.getColor();
          g.setColor(java.awt.Color.black);
          g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
          g.setColor(oldColor);
        }
      };
      tip.setForeground(java.awt.Color.black);
      tip.setBackground(new java.awt.Color(255, 255, 200));
    }
    tip.setText(lastMouseSrc.getTip());
    size = tip.getPreferredSize();

    location.x = screenLocation.x + 5;
    location.y = screenLocation.y + lastMouseSrc.getHeight() + 5;

    java.awt.Rectangle tipRect = new java.awt.Rectangle(location.x, location.y,
        size.width, ((size.height == 0) ? 25 : size.height));
    java.awt.Rectangle compRect = new java.awt.Rectangle(screenLocation.x, screenLocation.y,
        lastMouseSrc.getWidth(), lastMouseSrc.getHeight());
    if (tipRect.intersects(compRect))
    {
      // It'll partially obscure the component, move it so we don't get mouse events
      // flopping between the tip window and the component.
      location.y = screenLocation.y - ((size.height == 0) ? 25 : size.height) - 1;
    }

    java.awt.Frame mainFrame = (java.awt.Frame) javax.swing.SwingUtilities.
        getAncestorOfClass(java.awt.Frame.class, this);
    if (tipWindow == null)
    {
      tipWindow = new java.awt.Window(mainFrame);
      tipWindow.setLayout(new java.awt.BorderLayout());
      tipWindow.add(tip, "Center");
    }

    tipWindow.setLocation(location);
    tipWindow.pack();
    tipWindow.setVisible(true);
    tipShowing = true;

    // That border needs to be painted!!!!
    java.awt.EventQueue.invokeLater(new Runnable()
    {
      public void run()
      {
        java.awt.Graphics fixPaint = tip.getGraphics();
        tip.paint(fixPaint);
        fixPaint.dispose();
        // This sync does the trick!!!
        //Removed 3/8/04				tip.getToolkit().sync();
      }
    });
  }

  private void hideTipWindow()
  {
    if (tipWindow != null)
    {
      tipWindow.setVisible(false);
      tipShowing = false;
    }
  }

  protected class InsideTimerAction extends java.util.TimerTask
  {
    public void run()
    {
      if (lastMouseSrc != null && isShowing())
      {
        showTipWindow();
      }
    }
  }

  long getLastRenderTime() { return lastRenderTime; }
  public boolean isAlive() { return alive; }
  public SageRenderer getRenderEngine() { return renderEngine; }
  public boolean isAcceleratedDrawing() { return renderType == NATIVE3DRENDER; }
  public boolean setAcceleratedDrawing(boolean x, boolean permanent)
  {
    if (renderType == REMOTE2DRENDER || Sage.MAC_OS_X) return false;
    if (Sage.WINDOWS_OS && renderEngine instanceof Java2DSageRenderer && ((Java2DSageRenderer) renderEngine).hasOSDRenderer() && x)
      return false;
    if (permanent)
      uiMgr.putBoolean("ui/accelerated_rendering", x);
    if (x != (renderType == NATIVE3DRENDER) || !permanent) // temporary changes are done internally and usually require buffer reallocation
    {
      // There's a problem here if they've got video loaded with the VMR9 and they
      // disable accelerated rendering.
      if (renderType == NATIVE3DRENDER && vf.hasFile() && (DShowMediaPlayer.getUseVmr() || DShowMediaPlayer.getUseEvr()) && Sage.getBoolean("ui/close_media_player_on_standby", true))
      {
        vf.sleep();
      }
      synchronized (renderEngineLock)
      {
        //renderEngine.cleanupRenderer();
        if (x)
          renderType = NATIVE3DRENDER;
        else
          renderType = JAVA2DRENDER;
        preparedYet = false;
        if (renderType == NATIVE3DRENDER)
        {
          if(Sage.MAC_OS_X) {
            try {
              Class[] params = {this.getClass()};
              Object[] args = {this};
              java.lang.reflect.Constructor ctor = Class.forName("sage.QuartzSageRenderer").getConstructor(params);
              renderEngine = (SageRenderer) ctor.newInstance(args);
              setIgnoreRepaint(true); // avoid issues with the native renderer causing us to endlessly redraw the UI...
            } catch(Throwable t) {
              System.out.println("Exception occurred while creating renderer: "+t);
              renderType = JAVA2DRENDER;
              renderEngine = new Java2DSageRenderer(this);
            }
          } else {
            renderEngine = /*uiMgr.getBoolean("opengl", false) ?
						(SageRenderer)new JOGLSageRenderer(this) :*/ new DirectX9SageRenderer(this);
          }
        }
        else
          renderEngine = new Java2DSageRenderer(this);
        prepare();
        allocatedRenderer = false;
        return (renderType == NATIVE3DRENDER) == x;
      }
    }
    else
    {
      // This could require changing the fixed size because OSD changes call this too, but not necessarily
      // changing the value
      preparedYet = false;
    }
    return true;
  }

  public void waitForRenderEngineCleanup()
  {
    if (Sage.DBG) System.out.println("Waiting for the rendering engine to clean itself up....");
    while (lastRenderEngine != renderEngine && renderEngine != null)
    {
      try{Thread.sleep(50);}catch(Exception e){}
    }
    if (Sage.DBG) System.out.println("Done waiting for the rendering engine to clean itself up.");
  }

  public void resetHWND()
  {
    cachedHWND = 0;
    vf.resetVideoHandleCache();
  }
  private long cachedHWND;
  public long getHWND()
  {
    //if (!Sage.WINDOWS_OS)
    //	cachedHWND = 1;
    if (cachedHWND == 0)
      cachedHWND = UIUtils.getHWND(this);
    //		if (cachedHWND == 0)
    //			Thread.dumpStack();
    return cachedHWND;
  }

  public boolean didLastRenderHaveFullScreenVideo() { return lastRenderVideoRect != null &&
      Math.round(lastRenderVideoRect.width) == getWidth() && Math.round(lastRenderVideoRect.height) == rootComp.getHeight(); }

  public long getNextAnimationTime()
  {
    return nextAnimationTime;
  }

  public void registerAnimation(ZComp animComp, long nextAnimTime)
  {
    if (THREADING_DBG) System.out.println("ZRoot registerAnimation(" + animComp + animComp.hashCode() + ", " + Sage.df(nextAnimTime) + ")");
    nextAnimationTime = Math.min(nextAnimationTime, nextAnimTime);
    synchronized (animations)
    {
      for (int i = 0; i < animations.size(); i++)
      {
        if (((Animation) animations.get(i)).comp == animComp)
        {
          ((Animation) animations.get(i)).time = nextAnimTime;
          return;
        }
      }
      animations.add(new Animation(animComp, nextAnimTime));
    }
  }

  public void unregisterAnimation(ZComp animComp)
  {
    if (THREADING_DBG) System.out.println("ZRoot unregisterAnimation(" + animComp + animComp.hashCode() + ")");
    synchronized (animations)
    {
      for (int i = 0; i < animations.size(); i++)
        if (((Animation) animations.get(i)).comp == animComp)
        {
          animations.remove(i);
          return;
        }
    }
  }

  public UIManager getUIMgr() { return uiMgr; }

  public int getRenderType() { return renderType; }

  public boolean isIntegerPixels()
  {
    if(Sage.MAC_OS_X) return true; // Quartz has a funky rounding issue since it's coordinates are not tied directly to pixels
    return renderType != NATIVE3DRENDER;
  }

  // MUST be called from within the RenderEngine during FinalRender
  public void setActiveAnimation(RenderingOp currAnim)
  {
    if (SageRenderer.ANIM_DEBUG || THREADING_DBG) System.out.println("ZRoot got a final render animation callback:" + currAnim);
    finalAnims.add(currAnim);
  }

  public void registerLostAnimationOp(RenderingOp lostOp)
  {
    //if (Sage.DBG) System.out.println("ZRoot get a lost anim op: " + lostOp);
    if (!lostOp.isAnimationOp() || lostOp.anime.isIn())
      if (Sage.DBG) System.out.println("ERROR 'IN' rendering op was lost in the animation system!!!");
    lostAnimRops.add(lostOp);
  }

  public void registerLostTransitionAnimationOp(RenderingOp lostOp, String destWidgName)
  {
    lostXtnAnimRops.add(new Object[] { lostOp, destWidgName });
  }

  // Use null for this to clear it. That should be done after using the current op or doing a new focus
  // animation which should clear this op.
  public void registerLostFocusAnimationOp(RenderingOp lostOp)
  {
    lostFocusOp = lostOp;
  }

  public RenderingOp getLostFocusAnimationOp()
  {
    return lostFocusOp;
  }

  public boolean isDoingScrollAnimation()
  {
    return currentlyScrolling;
  }

  public void effectsNeedProcessing(boolean locked)
  {
    effectsNeedProcessing = true;
    if (locked)
      lockedEffectRunning = true;
  }

  public void resetNeedNextPrevCleanup(ZDataTable src)
  {
    activeTransitionSources.remove(src);
  }

  public void setNeedNextPrevCleanup(ZDataTable src)
  {
    if (!activeTransitionSources.contains(src))
      activeTransitionSources.add(src);
  }

  // This will put the tracker effect in the map and setup its current effect parameters if needed
  public EffectTracker handleTrackerEffect(EffectTracker effect, boolean visible)
  {
    Object trackerKey;
    if (effect.getTrigger() == EffectTracker.TRIGGER_FOCUSTRACKER)
    {
      // We have to separate out options menus; so just use the identity code for the pseudoparent if this isn't the menu level focus
      ZPseudoComp menuParent = effect.getSrcComp().getTopPseudoParent();
      if (menuParent.getWidget().type() == Widget.MENU)
        trackerKey = "Focus";
      else
        trackerKey = "Focus" + System.identityHashCode(menuParent);
    }
    else
      trackerKey = effect.getTrackerKey();
    EffectTracker oldTracker = (EffectTracker) trackerEffectMap.get(trackerKey);
    if (oldTracker == null)
    {
      if (!visible)
        return effect;
      effect.resetTrackerState();
      effect.freshenTracker();
      effect.setFadeEffect(0, 1);
      effect.setInitialPositivity(false);
      effect.setPositivity(true);
      ZPseudoComp trackerSrc = effect.getSrcComp();
      effect.setTrackerPosition(trackerSrc.getTrueXf(), trackerSrc.getTrueYf(), trackerSrc.getWidthf(), trackerSrc.getHeightf());
      trackerEffectMap.put(trackerKey, effect);
    }
    else if (oldTracker == effect)
    {
      if (!visible)
      {
        //				effect.resetTrackerState();
        //				effect.setFadeEffect(1, 0);
        //				effect.setInitialPositivity(false);
        //				effect.setPositivity(true);
        //				trackerEffectMap.remove(trackerKey);
      }
      else
      {
        effect.freshenTracker();
        // This fixes issues with the focus tracker competing with scrolling
        ZPseudoComp trackerSrc = effect.getSrcComp();
        if (effect.getTrigger() != EffectTracker.TRIGGER_FOCUSTRACKER)
        {
          // This'll return a new EffectTracker object if its moved & active so we don't mess with the state of the one that's
          // currently being animated
          effect = effect.setTrackerTarget(trackerSrc.getTrueBoundsf(), null);
          trackerEffectMap.put(trackerKey, effect); // in case it changed
        }
        else
          effect.setTrackerPosition(trackerSrc.getTrueXf(), trackerSrc.getTrueYf(), trackerSrc.getWidthf(), trackerSrc.getHeightf());
      }
    }
    else if (visible)
    {
      // Tracker change found....setup the animation
      trackerEffectMap.put(trackerKey, effect);
      effect.resetTrackerState();
      effect.freshenTracker();
      effect.setInitialPositivity(false);
      effect.setPositivity(true);
      ZPseudoComp trackerSrc = effect.getSrcComp();
      effect.setTrackerTarget(trackerSrc.getTrueBoundsf(), oldTracker);
    }
    return effect;
  }

  public boolean isARBuildingDL()
  {
    return arIsBuildingDL;
  }

  public boolean didRootSizeChange()
  {
    return mainSizeChanged;
  }

  // These return the max/min x/y values for the UI accounting for overscan as well
  public float getUIMaxY()
  {
    return getHeight() * uiMgr.getOverscanScaleHeight() + uiMgr.getOverscanOffsetY();
  }
  public float getUIMinY()
  {
    return uiMgr.getOverscanOffsetY();
  }
  public float getUIMaxX()
  {
    return getWidth() * uiMgr.getOverscanScaleWidth() + uiMgr.getOverscanOffsetX();
  }
  public float getUIMinX()
  {
    return uiMgr.getOverscanOffsetX();
  }

  public int getScalingInsetsBaseWidth() { return scalingInsetsBaseWidth; }
  public int getScalingInsetsBaseHeight() { return scalingInsetsBaseHeight; }
  public int getMinFontSize() { return currMinFontSize; }
  public int getMinShrunkFontSize() { return minShrunkFontSize; }
  public boolean getTextShadowNever() { return textShadowNever; }
  public boolean getEnableCornerArc() { return enableCornerArc; }

  private int scalingInsetsBaseWidth;
  private int scalingInsetsBaseHeight;
  private int currMinFontSize;
  private int minShrunkFontSize;
  private boolean textShadowNever;
  private boolean enableCornerArc;

  private int renderType;

  protected ZComp rootComp;
  private Thread activeRenderThread;
  private Thread finalRenderThread;
  private Object activeRenderThreadLock = new Object();
  private Object finalRenderThreadLock = new Object();
  private boolean doItOnce;
  private ZComp pressure;
  private ZComp lastMouseSrc;
  private ZComp enteredMouseComp;
  private boolean arIsBuildingDL;
  private boolean arExecuting;

  private UIManager uiMgr;
  private VideoFrame vf;

  private java.util.TimerTask enterTimer;
  private boolean tipShowing;
  private java.awt.Window tipWindow;
  private java.awt.Label tip;
  private boolean alive;

  private int[] imageByteCache;

  private boolean forceRenderSize;
  private int forcedRenderWidth;
  private int forcedRenderHeight;
  private boolean fixMousePositions;

  private PseudoMenu lastActiveMenu;

  private long lastRenderTime;

  private java.awt.Rectangle dirtyArea;
  private Object dirtyLock = new Object();
  private static java.awt.Rectangle MAX_DIRTY = new java.awt.Rectangle(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);

  private Object renderEngineLock = new Object();
  private SageRenderer renderEngine;
  private java.awt.Rectangle renderNeeded; // for the final render, its the dirty area
  private boolean allocatedRenderer;

  private Object externalRenderNowLock = new Object();
  private boolean externalRenderNow;

  private long arCycleID;
  private volatile long waitingOnARCycleID;

  Object finalRenderExecutionLock = new Object();

  private java.awt.geom.Rectangle2D.Float lastRenderVideoRect;

  private boolean forcedPause;
  private boolean forcedMute; // for live streams

  private int lastMinFontSize;
  private int lastMainAreaHeight; // for triggering dynamic font adjustments
  private int lastMainAreaWidth;
  private boolean mainSizeChanged;

  private java.util.ArrayList animations = new java.util.ArrayList();
  private long nextAnimationTime;

  // Special OEM variable
  private String lastVideoOutputPort;

  private AWTThreadWatcher watchdog;

  private java.util.ArrayList waitImages;

  private java.util.Vector finalAnims = new java.util.Vector();
  // this is after clearing them before we get into the actual final render call
  // we need this for VMR9 since it does the final render on an async call
  private boolean finalAnimsNeedProcessing;

  // If we remove a popup or change menus and there were pending animations then
  // we register them in this vector. Then on the next active render stage we
  // insert them at the end of the display list so they can get executed. This *should*
  // be safe because popup removal is always at the top and menu changes are a full screen effect.
  private java.util.Vector lostAnimRops = new java.util.Vector();
  // This is for AnimateTransition effects that occur across a refresh boundary
  private java.util.Vector lostXtnAnimRops = new java.util.Vector();

  private java.awt.Rectangle lastAnimArea;
  private java.awt.Rectangle lastFinalAnimArea;

  private RenderingOp lostFocusOp;

  private boolean animationLock; // true when we're locked into an animation rendering
  private boolean currentlyScrolling;

  private int mouseEvtCounter;
  private boolean lastWasTrackedDrag = false;

  private SageRenderer lastRenderEngine = null;

  // Flag to indicate the rendering engine is processing animated effects and should be kicked for another render
  private boolean effectsNeedProcessing;
  // If we're running an unloaded effect which needs to run until completion before we consume another DL
  private boolean lockedEffectRunning;

  private java.util.Vector activeTransitionSources = new java.util.Vector();
  private RenderingOp activeFocusTracker; // used for determining when a transition is over

  private java.util.Map trackerEffectMap = new java.util.HashMap();

  private static class Animation
  {
    Animation(ZComp animComp, long animTime)
    {
      comp = animComp;
      time = animTime;
    }
    ZComp comp;
    long time;
  }
}
