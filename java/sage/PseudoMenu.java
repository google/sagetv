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

public class PseudoMenu implements EventHandler
{
  private Widget getGlobalThemeWidget()
  {
    Widget globalThemeWidget = null;
    Widget[] widgs = uiMgr.getModuleGroup().getWidgets(Widget.THEME);
    for (int i = 0; i < widgs.length; i++)
      if ("Global".equals(widgs[i].getName()))
      {
        globalThemeWidget = widgs[i];
        break;
      }
    return globalThemeWidget;
  }
  public PseudoMenu(UIManager inUIMgr, Widget inWidg)
  {
    uiMgr = inUIMgr;
    widg = inWidg;
    popupStack = new java.util.Stack();
    java.util.ArrayList themeVec = new java.util.ArrayList();
    Widget gtw = getGlobalThemeWidget();
    if (gtw != null)
    {
      addEventListenersFromList(gtw.contents());
      themeVec.add(gtw);
    }
    comp = new ZPseudoComp(widg, themeVec, Catbert.Context.createNewMenuContext(uiMgr));

    Widget[] topTheme = widg.contents(Widget.THEME);
    if (topTheme.length > 0 || gtw != null)
    {
      boolean hasTop = topTheme.length > 0;
      if (hasTop && topTheme[0].hasProperty(Widget.FOCUS_CHANGE_SOUND))
        focusChangeSound = topTheme[0].getStringProperty(Widget.FOCUS_CHANGE_SOUND, null, comp);
      else if (gtw != null && gtw.hasProperty(Widget.FOCUS_CHANGE_SOUND))
        focusChangeSound = gtw.getStringProperty(Widget.FOCUS_CHANGE_SOUND, null, comp);
      if (hasTop && topTheme[0].hasProperty(Widget.ITEM_SELECT_SOUND))
        itemSelectSound = topTheme[0].getStringProperty(Widget.ITEM_SELECT_SOUND, null, comp);
      else if (gtw != null && gtw.hasProperty(Widget.ITEM_SELECT_SOUND))
        itemSelectSound = gtw.getStringProperty(Widget.ITEM_SELECT_SOUND, null, comp);
      if (hasTop && topTheme[0].hasProperty(Widget.MENU_CHANGE_SOUND))
        menuChangeSound = topTheme[0].getStringProperty(Widget.MENU_CHANGE_SOUND, null, comp);
      else if (gtw != null && gtw.hasProperty(Widget.MENU_CHANGE_SOUND))
        menuChangeSound = gtw.getStringProperty(Widget.MENU_CHANGE_SOUND, null, comp);
      if (hasTop && topTheme[0].hasProperty(Widget.USER_ACTION_SOUND))
        userActionSound = topTheme[0].getStringProperty(Widget.USER_ACTION_SOUND, null, comp);
      else if (gtw != null && gtw.hasProperty(Widget.USER_ACTION_SOUND))
        userActionSound = gtw.getStringProperty(Widget.USER_ACTION_SOUND, null, comp);
      if (hasTop)
      {
        // Check for disabling parent clipping
        Widget[] attWidgets = topTheme[0].contents(Widget.ATTRIBUTE);
        for (int i = 0; i < attWidgets.length; i++)
        {
          if ("DisableParentClip".equals(attWidgets[i].getName()))
            disableParentClip = Catbert.evalBool(attWidgets[i].getProperty(Widget.VALUE));
          else if ("ForceLayerAnimations".equals(attWidgets[i].getName()))
            forcedLayers = Catbert.evalBool(attWidgets[i].getProperty(Widget.VALUE));
        }
      }
    }
  }

  public void activate(boolean redo) {
    active = true;
    if (!comp.hasFreshlyLoadedContext() && !redo)
      comp.reloadAttributeContext();
    Catbert.processUISpecificHook("BeforeMenuLoad", new Object[]{Boolean.valueOf(redo)}, uiMgr, false);

    // This can happen with menu shortcuts
    if (!active) {
      comp.unfreshAttributeContext();
      return;
    }

    comp.setMenuLoadedState(true);
    comp.evaluateTree(true, false);
    comp.evaluateTree(false, true);

    // Locations need to be established now since we use them to determine which is focused
    comp.recalculateDynamicFonts();
    comp.invalidateAll();
    // Only bother doing this here if it actually has a size already. Otherwise this can incorrectly
    // mark the layout as valid
    if (comp.getWidth() > 0 && comp.getHeight() > 0)
      comp.doLayout();

    if ((comp.hasFocusableChildren() || uiMgr.allowHiddenFocus()) && (!redo || !comp.doesHierarchyHaveFocus())) {
      Catbert.processUISpecificHook("MenuNeedsDefaultFocus", new Object[]{Boolean.valueOf(redo)}, uiMgr, false);
      if (!comp.doesHierarchyHaveFocus()) {
        if (!comp.checkForcedFocus())
          comp.setDefaultFocus();
      }
      // NARFLEX 6/29/07 - Won't this automatically happen in focus listener eval?
      //comp.invalidateAll();
    }

    int numTextInputs=comp.getNumTextInputs(0);
    multipleTextInputs = numTextInputs > 1;

    // notify the UI Renderer as to which Menu we are activating, so that it can pass it to the client as a HINT
    uiMgr.getRootPanel().getRenderEngine().setMenuHint(widg.getName(), null, numTextInputs>0);
  }

  public boolean hasMultipleTextInputs()
  {
    return multipleTextInputs;
  }

  public EventHandler getEV()
  {
    return this;
  }

  public ZPseudoComp getUI()
  {
    return comp;
  }

  public boolean isTV()
  {
    return comp.getVideoComp() != null;
  }

  public ZPseudoComp getCompForWidget(Widget forWidg)
  {
    return comp.getCompForWidget(forWidg);
  }

  public java.util.Vector getCompsForWidget(Widget forWidg)
  {
    java.util.Vector rv = new java.util.Vector();
    comp.getCompsForWidget(forWidg, rv);
    return rv;
  }

  public void postactivate(boolean redo)
  {
    while (comp.isMenuLoadingState() && active && !uiMgr.isAsleep())
    {
      try{Thread.sleep(10);}catch(Exception e){}
    }
    Catbert.processUISpecificHook("AfterMenuLoad", new Object[] { Boolean.valueOf(redo) }, uiMgr, true);
    if (UIManager.ENABLE_STUDIO && uiMgr.getStudio() != null)
    {
      // For the UI Component debug view
      uiMgr.getStudio().refreshUITree();
    }
  }

  public void terminate(boolean runCloseAnims)
  {
    active = false;
    // This is VERY important so that any Hooks that are waiting on the completion of
    // an OptionsMenu get cleaned up.
    while (!popupStack.isEmpty())
      removePopup();
    if (runCloseAnims && comp.hasMenuUnloadEffects() && uiMgr.areEffectsEnabled())
    {
      comp.setMenuUnloadedState(true);
      // NOTE: Should optimize this to only execute if needed
      // For now, let's just disable it on embedded; we only would need to call this if we used the IsTransitioningToMenu API call in a way that would require the layout to
      // be redone. We do use that API call; but only for conditionality in Effect chains which always get executed anyways
      comp.evaluateTree(false, true);
      comp.appendToDirty(true);
      // Now we want to have the active renderer run a cycle so it builds up the rendering operations needed
      // to run any closing effects for this popup.
      // When we cler the lock with the true flag; it means the activeRenderer MUST run before the lock
      // can be obtained again.
      uiMgr.clearLock(true, true);
      uiMgr.getLock(true, null);
    }
    // UPDATE: 1/17/06 - we need to do the menu hook after the popups are removed or it won't
    // end up calling the menu hook since the popup will have priority!!!
    Catbert.processUISpecificHook("BeforeMenuUnload", null, uiMgr, false);
    if (menuChangeSound != null && menuChangeSound.length() > 0)
      uiMgr.playSound(menuChangeSound);
    comp.cleanup();
  }

  public void action(UserEvent evt)
  {
    if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu processing: " + evt);
    int soundCount = uiMgr.getClipPlayCount();
    ZComp focuser = hasPopup() ? getTopPopup().getFocusOwner(false) : comp.getFocusOwner(false);

    if (focuser == null)
    {
      // Nothing is there to take the event, we should try and establish a default focus instead
    }

    boolean hasMF = uiMgr.getVideoFrame().hasFile();
    boolean consumed = false;
    boolean[] acquiredLock = new boolean[1];
    if (!uiMgr.getLock(true, acquiredLock, true))
    {
      if (Sage.DBG) System.out.println("Skipping event due to debug mode:" + evt);
      return;
    }
    try
    {
      if (focuser != null && focuser instanceof ZPseudoComp)
        consumed = ((ZPseudoComp) focuser).action(evt);
      else
        consumed = hasPopup() ? getTopPopup().action(evt) : comp.action(evt);
        if (!consumed)
        {
          if (UserEvent.getNumCode(evt.getType()) != -1 || evt.getType() == UserEvent.DASH ||
              (evt.getType() == UserEvent.ANYTHING && evt.isKB()))
          {
            // Find any text inputs and send them the event
            consumed = hasPopup() ? getTopPopup().tryToConsumeKeystroke(evt) : comp.tryToConsumeKeystroke(evt);
          }
        }
        if (!consumed)
        {
          defaultEventAction(evt);
        }
        else if (evt.getType() == UserEvent.STOP_EJECT && !hasMF && Sage.LINUX_OS && uiMgr.getBoolean("enable_default_stop_eject", true))
        {
          // This is for doing eject when they hit stop eject. Otherwise we have to update a bunch of stuff in the STV.
          uiMgr.getVideoFrame().ejectCdrom(null);
        }
    }
    finally
    {
      if (acquiredLock[0])
        uiMgr.clearLock();
    }
    if (evt.getType() != UserEvent.ANYTHING && soundCount == uiMgr.getClipPlayCount() &&
        userActionSound != null && userActionSound.length() > 0)
      uiMgr.playSound(userActionSound);
    if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu finished processing: " + evt);
  }

  protected Widget getUEListenWidget(int ueType)
  {
    return ueListenMap == null ? null : ueListenMap[ueType];
  }

  void defaultEventAction(UserEvent evt)
  {
    Catbert.ExecutionPosition ep = null;
    // Check the global listener map first
    if (evt.isIR())
    {
      Widget rawIRListener = getUEListenWidget(ZPseudoComp.UE_INDEX_RAW_IR);
      if (rawIRListener != null)
      {
        Widget[] listenKids = rawIRListener.contents();
        Catbert.Context childContext = comp.relatedContext.createChild();
        childContext.setLocal(null, new Long(evt.getIRCode()));
        childContext.setLocal(Widget.RAW_IR, new Long(evt.getIRCode()));
        childContext.setLocal(rawIRListener.getName(), new Long(evt.getIRCode()));
        childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
        if (evt.getPayloads() != null)
          childContext.setLocal(ZPseudoComp.PAYLOAD_VAR, evt.getPayloads());
        for (int i = 0; i < listenKids.length; i++)
        {
          if (listenKids[i].isProcessChainType())
          {
            if ((ep = ZPseudoComp.processChain(listenKids[i], childContext, null, comp, false)) != null)
            {
              ep.addToStack(listenKids[i]);
              ep.addToStackFinal(rawIRListener);
              return;
            }
          }
        }
        if (!Catbert.evalBool(childContext.safeLookup(Catbert.PASSIVE_LISTEN_VAR)))
          return;
      }
    }

    // Check for raw KB listeners
    if (getUEListenWidget(ZPseudoComp.UE_INDEX_RAW_KB) != null && evt.isKB())
    {
      /*
       * We want to simplify keyboard listening and not deal with press,release,typed event
       * distinguishment. To do this, only complete keystrokes should be sent.
       * These occur under the following rules:
       * 1. Complete keystrokes occur on a keyReleased event
       * 2. Complete keystrokes will have the prior event as a keyPressed event
       */
      Widget kbWidg = getUEListenWidget(ZPseudoComp.UE_INDEX_RAW_KB);
      String keystroke = Catbert.getStringFromKeystroke(evt.getKeyCode(), evt.getKeyModifiers());
      Catbert.Context childContext = comp.relatedContext.createChild();
      childContext.setLocal(null, keystroke);
      childContext.setLocal("KeyCode", new Integer(evt.getKeyCode()));
      childContext.setLocal("KeyModifiers", new Integer(evt.getKeyModifiers()));
      childContext.setLocal("KeyChar", (evt.getKeyChar() != 0) ? ("" + evt.getKeyChar()) : "");
      childContext.setLocal(kbWidg.getName(), keystroke);
      childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
      if (evt.getPayloads() != null)
        childContext.setLocal(ZPseudoComp.PAYLOAD_VAR, evt.getPayloads());
      Widget[] listenKids = kbWidg.contents();
      for (int i = 0; i < listenKids.length; i++)
      {
        if (listenKids[i].isProcessChainType())
        {
          if ((ep = ZPseudoComp.processChain(listenKids[i], childContext, null, comp, false)) != null)
          {
            ep.addToStack(listenKids[i]);
            ep.addToStackFinal(kbWidg);
            return;
          }
        }
      }
      if (!Catbert.evalBool(childContext.safeLookup(Catbert.PASSIVE_LISTEN_VAR)))
        return;
    }

    for (int i = 0; i < 3; i++)
    {
      Widget ueListenWidg = null;
      if (i == 0)
        ueListenWidg = getUEListenWidget(evt.getType());
      else if (i == 1 && evt.getSecondaryType() != 0)
        ueListenWidg = getUEListenWidget(evt.getSecondaryType());
      else if (i == 2 && evt.getTernaryType() != 0)
        ueListenWidg = getUEListenWidget(evt.getTernaryType());

      if (ueListenWidg != null)
      {
        // 11/11/03 - I used to not create a child context here, but I added it when I did the
        // passive listen stuff
        Widget[] listenKids = ueListenWidg.contents();
        Catbert.Context childContext = comp.relatedContext.createChild();
        childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
        if (evt.getPayloads() != null)
          childContext.setLocal(ZPseudoComp.PAYLOAD_VAR, evt.getPayloads());
        for (int j = 0; j < listenKids.length; j++)
        {
          if (listenKids[j].isProcessChainType())
          {
            if ((ep = ZPseudoComp.processChain(listenKids[j], childContext, null, comp, false)) != null)
            {
              ep.addToStack(listenKids[i]);
              ep.addToStackFinal(ueListenWidg);
              return;
            }
          }
        }
        if (!Catbert.evalBool(childContext.safeLookup(Catbert.PASSIVE_LISTEN_VAR)))
          return;
      }
    }
    int numericText = evt.getNumCode(evt.getType());
    if (numericText != -1 || evt.getType() == UserEvent.DASH)
    {
      Widget numberListener = getUEListenWidget(ZPseudoComp.UE_INDEX_NUMBERS);
      if (numberListener != null)
      {
        Widget[] listenKids = numberListener.contents();
        Catbert.Context childContext = comp.relatedContext.createChild();
        childContext.setLocal(null, (evt.getType() == UserEvent.DASH) ? "-" : Integer.toString(numericText));
        childContext.setLocal(numberListener.getName(), (evt.getType() == UserEvent.DASH) ? "-" : Integer.toString(numericText));
        childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
        if (evt.getPayloads() != null)
          childContext.setLocal(ZPseudoComp.PAYLOAD_VAR, evt.getPayloads());
        for (int i = 0; i < listenKids.length; i++)
          if (listenKids[i].isProcessChainType())
            if ((ep = ZPseudoComp.processChain(listenKids[i], childContext, null, comp, false)) != null)
            {
              ep.addToStack(listenKids[i]);
              ep.addToStackFinal(numberListener);
              break;
            }
        if (!Catbert.evalBool(childContext.safeLookup(Catbert.PASSIVE_LISTEN_VAR)))
          return;
      }
    }

    VideoFrame vf = uiMgr.getVideoFrame();
    MediaFile mf = vf.getCurrFile();
    // Allow VF actions for music or audio-only TV files on any menu; and only on video display menus for video content
    boolean vfActionsOK = (mf != null && ((mf.isMusic() || (mf.isTV() && !mf.hasVideoContent())) || ((mf.isVideo() || mf.isBluRay() || mf.isDVD()) && isTV())));
    boolean videoActionsOK = vfActionsOK && mf.hasVideoContent();
    boolean dvdActionsOK = videoActionsOK && mf.isDVD();
    boolean bluRayActionsOK = videoActionsOK && mf.isBluRay();
    switch (evt.getType())
    {
      case UserEvent.CUSTOMIZE:
        if (UIManager.ENABLE_STUDIO && Permissions.hasPermission(Permissions.PERMISSION_STUDIO, uiMgr) && uiMgr.getStudio(true) != null)
        {
          uiMgr.getStudio().setUIMgr(uiMgr);
          final ZPseudoComp topPop = getTopPopup();
          if (!uiMgr.getLocalUIClientName().equals(Seeker.LOCAL_PROCESS_CLIENT))
          {
            java.awt.EventQueue.invokeLater(new Runnable()
            {
              public void run()
              {
                if (topPop != null)
                  uiMgr.getStudio().showAndHighlightNode(topPop.widg);
                else
                  uiMgr.getStudio().showAndHighlightNode(getBlueprint());
              }
            });
          }
          else
          {
            if (topPop != null)
              uiMgr.getStudio().showAndHighlightNode(topPop.widg);
            else
              uiMgr.getStudio().showAndHighlightNode(getBlueprint());
          }
        }
        break;
      case UserEvent.BACK:
        if (!hasPopup())
          uiMgr.backupUI();
        else if (uiMgr.getBoolean("ui/back_closes_options_menus", true))
        {
          action(new UserEvent(evt.getWhen(), UserEvent.OPTIONS, evt.getIRCode(), evt.getKeyCode(), evt.getKeyModifiers(), evt.getKeyChar()));
        }
        break;
      case UserEvent.FORWARD:
        if (!hasPopup())
          uiMgr.forwardUI();
        break;
      case UserEvent.HOME:
        if (!hasPopup())
          uiMgr.advanceUI(UIManager.MAIN_MENU);
        break;
      case UserEvent.FULL_SCREEN:
        uiMgr.setFullScreen(!uiMgr.isFullScreen());
        break;
      case UserEvent.FULL_SCREEN_ON:
        if (!uiMgr.isFullScreen())
          uiMgr.setFullScreen(true);
        break;
      case UserEvent.FULL_SCREEN_OFF:
        if (uiMgr.isFullScreen())
          uiMgr.setFullScreen(false);
        break;
      case UserEvent.POWER:
        uiMgr.gotoSleep(!uiMgr.isAsleep());
        break;
      case UserEvent.POWER_ON:
        uiMgr.gotoSleep(false);
        break;
      case UserEvent.POWER_OFF:
        uiMgr.gotoSleep(true);
        break;
      case UserEvent.MUTE:
        vf.setMute(!vf.getMute());
        break;
      case UserEvent.MUTE_ON:
        vf.setMute(true);
        break;
      case UserEvent.MUTE_OFF:
        vf.setMute(false);
        break;
      case UserEvent.PAUSE:
        if (vfActionsOK)
          vf.pause();
        break;
      case UserEvent.PLAY:
        if (vfActionsOK)
          vf.play();
        break;
      case UserEvent.FF:
        if (vfActionsOK)
          vf.ff();
        break;
      case UserEvent.REW:
        if (vfActionsOK)
          vf.rew();
        break;
      case UserEvent.FF_2:
        if (vfActionsOK)
          vf.ff2();
        break;
      case UserEvent.REW_2:
        if (vfActionsOK)
          vf.rew2();
        break;
      case UserEvent.VOLUME_UP:
        vf.volumeUp();
        break;
      case UserEvent.VOLUME_DOWN:
        vf.volumeDown();
        break;
      case UserEvent.FASTER:
        if (vfActionsOK)
          vf.faster();
        break;
      case UserEvent.SLOWER:
        if (vfActionsOK)
          vf.slower();
        break;
      case UserEvent.SMOOTH_FF:
        if (vfActionsOK)
          vf.smoothFF();
        break;
      case UserEvent.SMOOTH_REW:
        if (vfActionsOK)
          vf.smoothRew();
        break;
      case UserEvent.OPTIONS:
        removePopup();
        break;
      case UserEvent.AR_FILL:
        if (videoActionsOK)
          vf.setAspectRatioMode(BasicVideoFrame.ASPECT_FILL);
        break;
      case UserEvent.AR_4X3:
        if (videoActionsOK)
          vf.setAspectRatioMode(BasicVideoFrame.ASPECT_4X3);
        break;
      case UserEvent.AR_16X9:
        if (videoActionsOK)
          vf.setAspectRatioMode(BasicVideoFrame.ASPECT_16X9);
        break;
      case UserEvent.AR_SOURCE:
        if (videoActionsOK)
          vf.setAspectRatioMode(BasicVideoFrame.ASPECT_SOURCE);
        break;
      case UserEvent.AR_TOGGLE:
        if (videoActionsOK)
        {
          if (uiMgr.getUIClientType() == UIClient.REMOTE_UI &&
              uiMgr.getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer)
          {
            // See if we do advanced aspect ratios
            MiniClientSageRenderer mcsr = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
            if (mcsr.supportsAdvancedAspectRatios())
            {
              // Don't allow changing aspects while in DVD menus
              if (dvdActionsOK && vf.isShowingDVDMenu() && Sage.getBoolean("miniplayer/disable_aspect_change_in_dvd_menus", true))
                break;
              String[] arModes = mcsr.getAdvancedAspectRatioOptions();
              String currARMode = mcsr.getAdvancedAspectRatioMode();
              int currModeIndex = -1;
              for (int i = 0; i < arModes.length; i++)
              {
                if (arModes[i].equals(currARMode))
                {
                  currModeIndex = i;
                  break;
                }
              }
              mcsr.setAdvancedARMode(arModes[(currModeIndex + 1) % arModes.length]);
            }
            else
              vf.setAspectRatioMode((vf.getAspectRatioMode() + 1) % BasicVideoFrame.NUM_ASPECT_RATIO_CODES);
          }
          else
            vf.setAspectRatioMode((vf.getAspectRatioMode() + 1) % BasicVideoFrame.NUM_ASPECT_RATIO_CODES);
        }
        break;
      case UserEvent.PLAY_PAUSE:
        if (vfActionsOK)
          vf.playPause();
        break;
      case UserEvent.DVD_REVERSE_PLAY:
        if (videoActionsOK)
          vf.setRate(-1 * vf.getRate());
        break;
      case UserEvent.DVD_CHAPTER_NEXT:
        if (dvdActionsOK || bluRayActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_CHAPTER_NEXT);
        break;
      case UserEvent.DVD_CHAPTER_PREV:
        if (dvdActionsOK || bluRayActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_CHAPTER_PREV);
        break;
      case UserEvent.DVD_MENU:
        if (dvdActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_MENU, 2, 0);
        break;
      case UserEvent.DVD_TITLE_MENU:
        if (dvdActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_MENU, 1, 0);
        break;
      case UserEvent.DVD_RETURN:
        if (dvdActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_RETURN);
        break;
      case UserEvent.UP:
      case UserEvent.LEFT:
      case UserEvent.DOWN:
      case UserEvent.RIGHT:
        if (dvdActionsOK && vf.isShowingDVDMenu())
          vf.playbackControl(VideoFrame.DVD_CONTROL_BUTTON_NAV, evt.getType() == UserEvent.UP ? 1 :
            (evt.getType() == UserEvent.RIGHT ? 2 : (evt.getType() == UserEvent.DOWN ? 3 : 4)), 0);
        break;
      case UserEvent.SELECT:
        if (dvdActionsOK && vf.isShowingDVDMenu())
          vf.playbackControl(VideoFrame.DVD_CONTROL_ACTIVATE_CURRENT);
        break;
      case UserEvent.VOLUME_UP2:
        if (dvdActionsOK && vf.isShowingDVDMenu())
          vf.playbackControl(VideoFrame.DVD_CONTROL_BUTTON_NAV, 2, 0);
        else if (vfActionsOK)
          vf.volumeUp();
        break;
      case UserEvent.VOLUME_DOWN2:
        if (dvdActionsOK && vf.isShowingDVDMenu())
          vf.playbackControl(VideoFrame.DVD_CONTROL_BUTTON_NAV, 4, 0);
        else if (vfActionsOK)
          vf.volumeDown();
        break;
      case UserEvent.CHANNEL_UP:
        if (dvdActionsOK || bluRayActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_CHAPTER_NEXT);
        break;
      case UserEvent.CHANNEL_UP2:
        if (dvdActionsOK && vf.isShowingDVDMenu())
          vf.playbackControl(VideoFrame.DVD_CONTROL_BUTTON_NAV, 1, 0);
        else if (dvdActionsOK || bluRayActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_CHAPTER_NEXT);
        /*else if (vfActionsOK)
				{
					new Thread(){ public void run() { uiMgr.getVideoFrame().surfUp(); } }.start();
				}*/
        break;
      case UserEvent.LEFT_REW:
        if (dvdActionsOK && vf.isShowingDVDMenu())
          vf.playbackControl(VideoFrame.DVD_CONTROL_BUTTON_NAV, 4, 0);
        else if (vfActionsOK)
          vf.rew();
        break;
      case UserEvent.RIGHT_FF:
        if (dvdActionsOK && vf.isShowingDVDMenu())
          vf.playbackControl(VideoFrame.DVD_CONTROL_BUTTON_NAV, 2, 0);
        else if (vfActionsOK)
          vf.ff();
        break;
      case UserEvent.UP_VOL_UP:
        if (dvdActionsOK && vf.isShowingDVDMenu())
          vf.playbackControl(VideoFrame.DVD_CONTROL_BUTTON_NAV, 1, 0);
        else if (vfActionsOK)
          vf.volumeUp();
        break;
      case UserEvent.DOWN_VOL_DOWN:
        if (dvdActionsOK && vf.isShowingDVDMenu())
          vf.playbackControl(VideoFrame.DVD_CONTROL_BUTTON_NAV, 3, 0);
        else if (vfActionsOK)
          vf.volumeDown();
        break;
      case UserEvent.CHANNEL_DOWN:
        if (dvdActionsOK || bluRayActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_CHAPTER_PREV);
        break;
      case UserEvent.CHANNEL_DOWN2:
        if (dvdActionsOK && vf.isShowingDVDMenu())
          vf.playbackControl(VideoFrame.DVD_CONTROL_BUTTON_NAV, 3, 0);
        else if (dvdActionsOK || bluRayActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_CHAPTER_PREV);
        /*else if (vfActionsOK)
				{
					new Thread(){ public void run() { uiMgr.getVideoFrame().surfDown(); } }.start();
				}*/
        break;
      case UserEvent.PREV_CHANNEL:
        if (vfActionsOK)
        {
          Pooler.execute(new Runnable(){ public void run() { uiMgr.getVideoFrame().surfPreviousChannel(); } });
        }
        break;
      case UserEvent.DVD_SUBTITLE_CHANGE: // this isn't just for DVD media players
        if (vfActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_SUBTITLE_CHANGE, -1, -1);
        break;
      case UserEvent.DVD_SUBTITLE_TOGGLE: // this isn't just for DVD media players
        if (vfActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_SUBTITLE_TOGGLE);
        break;
      case UserEvent.DVD_ANGLE_CHANGE:
        if (dvdActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_ANGLE_CHANGE, -1, -1);
        break;
      case UserEvent.DVD_AUDIO_CHANGE: // this isn't just for DVD media players
        if (vfActionsOK)
          vf.playbackControl(VideoFrame.DVD_CONTROL_AUDIO_CHANGE, -1, -1);
        break;
      case UserEvent.STOP_EJECT:
      case UserEvent.EJECT:
        // We don't do the actual stop here because that should be handled in the STV since it usually involves a menu switch.
        // But if they're not doing anything with that command at the time do the eject
        vf.ejectCdrom(null);
        break;
      case UserEvent.VIDEO_OUTPUT:
        if (uiMgr.getUIClientType() == UIClient.REMOTE_UI && uiMgr.getRootPanel() != null)
        {
          MiniClientSageRenderer mcsr = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
          sage.media.format.VideoFormat currRez = mcsr.getDisplayResolution();
          String[] rezOptions = mcsr.getResolutionOptions();
          if (rezOptions != null && rezOptions.length > 0)
          {
            if (currRez == null)
              mcsr.pushResolutionChange(rezOptions[0]);
            else
            {
              int matchNum = -1;
              for (int i = 0; i < rezOptions.length; i++)
              {
                if (rezOptions[i].equals(currRez.getFormatName()))
                {
                  matchNum = i;
                  break;
                }
              }
              if (matchNum < 0)
                mcsr.pushResolutionChange(rezOptions[0]);
              else
                mcsr.pushResolutionChange(rezOptions[(matchNum + 1) % rezOptions.length]);
            }
          }
        }
        break;
    }
  }

  public Widget getBlueprint() { return widg; }

  public Catbert.ExecutionPosition addPopup(ZPseudoComp newPopup, Catbert.Context resumeContext)
  {
    boolean[] acquiredLock = new boolean[1];
    Catbert.ExecutionPosition rv = null;
    uiMgr.getLock(true, acquiredLock);
    try
    {
      processingPopup = true;
      // We need this on the popup stack so the Hooks see it correctly
      popupStack.push(rv = new Catbert.ExecutionPosition(newPopup, newPopup.widg, resumeContext));
      comp.add(newPopup);
      newPopup.setMenuLoadedState(true);
      Catbert.processUISpecificHook("BeforeMenuLoad", new Object[] { Boolean.valueOf(false) }, uiMgr, false);
      newPopup.evaluateTree(true, false);
      // In XBMC conditional display of components can depend on if popup menus are visible or not; so do a full refresh to cover that case
      if (uiMgr.isXBMCCompatible())
        comp.evaluateTree(false, true);
      else
        newPopup.evaluateTree(false, true);
      // Locations need to be established now since we use them to determine which is focused
      newPopup.invalidateAll();
      comp.doLayout();
      Catbert.processUISpecificHook("MenuNeedsDefaultFocus", null, uiMgr, false);
      ensureMenuFocus(false);
      newPopup.invalidateAll();

      // notify the UI Renderer as to which Menu/Popup we are activating, so that it can pass it to the client as a HINT
      uiMgr.getRootPanel().getRenderEngine().setMenuHint(widg.getName(),  newPopup.widg.getName(), newPopup.getNumTextInputs(0)>0);
    }
    finally
    {
      processingPopup = false;
      if (acquiredLock[0])
        uiMgr.clearLock();
    }
    Catbert.processUISpecificHook("AfterMenuLoad", new Object[] { Boolean.valueOf(false) }, uiMgr, true);
    if (UIManager.ENABLE_STUDIO && uiMgr.getStudio() != null)
    {
      // For the UI Component debug view
      uiMgr.getStudio().refreshUITree();
    }
    return rv;
  }
  public boolean removePopup(String widgName, boolean waitForClose)
  {
    boolean[] acquiredLock = new boolean[1];
    // If we got here then the popup is in the stack and we're just waiting for it to be the one on the top
    boolean didDebug = false;
    while (!popupStack.isEmpty())
    {
      Catbert.ExecutionPosition lastPop;
      boolean foundIt = false;
      synchronized (popupStack)
      {
        lastPop = (Catbert.ExecutionPosition) popupStack.peek();
        if (lastPop.getUI().getWidget().getName().equals(widgName) ||
            lastPop.getUI().getPropertyWidget().getName().equals(widgName))
        {
          foundIt = true;
        }
        else
        {
          // Check to make sure it's still in the stack before we start waiting again
          foundIt = false;
          for (int i = 0; i < popupStack.size(); i++)
          {
            lastPop = (Catbert.ExecutionPosition) popupStack.get(i);
            if (lastPop.getUI().getWidget().getName().equals(widgName) ||
                lastPop.getUI().getPropertyWidget().getName().equals(widgName))
            {
              foundIt = true;
              break;
            }
          }
          if (!foundIt)
            return true;
          else if (!waitForClose)
            return false;
          if (!didDebug)
          {
            if (Sage.DBG) System.out.println("Waiting for OptionsMenu " + widgName + " to become the top one before we close it...");
            didDebug = true;
          }
          try{popupStack.wait();}catch(InterruptedException e){}
        }
      }
      if (foundIt)
      {
        // Now get the UI lock and make sure it's still on top, then it's OK to close it
        uiMgr.getLock(true, acquiredLock);
        try
        {
          if (!popupStack.isEmpty())
          {
            lastPop = (Catbert.ExecutionPosition) popupStack.peek();
            if (lastPop.getUI().getWidget().getName().equals(widgName) ||
                lastPop.getUI().getPropertyWidget().getName().equals(widgName))
            {
              if (Sage.DBG) System.out.println("Closing targeted options menu of " + widgName);
              removePopup();
              return true;
            }
          }
        }
        finally
        {
          if (acquiredLock[0])
            uiMgr.clearLock();
        }
      }
    }
    return true;
  }
  public void removePopup()
  {
    if (!popupStack.isEmpty())
    {
      // NOTE: Narflex 10/9/07 - I'm quite sure that this needs a lock around it. We are modifying
      // the UI structure and potentially redoing focus here. I'm suspicious that the asynchronicity of
      // this may be the cause of some weird animation effects when closing OptionsMenus that I've seen.
      boolean[] acquiredLock = new boolean[1];
      Catbert.ExecutionPosition rv = null;
      uiMgr.getLock(true, acquiredLock);
      try
      {
        processingPopup = true;
        Catbert.ExecutionPosition lastPop = (Catbert.ExecutionPosition) popupStack.peek();
        ZPseudoComp currPopup = lastPop.getUI();
        // Do we want to disable popup hide animations if the window is closing too??
        // XBMC retains popups across window transitions; which we don't do so compatability there is irrelevant
        if (uiMgr.areEffectsEnabled() && currPopup.hasMenuUnloadEffects())
        {
          currPopup.setMenuUnloadedState(true);
          currPopup.appendToDirty(false);
          // Now we want to have the active renderer run a cycle so it builds up the rendering operations needed
          // to run any closing effects for this popup.
          // When we cler the lock with the true flag; it means the activeRenderer MUST run before the lock
          // can be obtained again.
          uiMgr.clearLock(true, true);
          uiMgr.getLock(true, null);
        }
        Catbert.processUISpecificHook("BeforeMenuUnload", null, uiMgr, false);
        popupStack.pop();
        comp.remove(currPopup);
        // Resume execution of the process chain for this OptionsMenu after its been closed
        lastPop.resumeExecution();
        synchronized (popupStack)
        {
          popupStack.notifyAll();
        }
        currPopup.cleanup();
        // In XBMC conditional display of components can depend on if popup menus are visible or not; so do a full refresh to cover that case
        if (uiMgr.isXBMCCompatible())
          comp.evaluateTree(false, true);
        ensureMenuFocus(true);

        // notify that the popup is removed
        // notify the UI Renderer as to which Menu/Popup we are activating, so that it can pass it to the client as a HINT
        if (popupStack.size()>0)
        {
          uiMgr.getRootPanel().getRenderEngine().setMenuHint(widg.getName() ,
                  ((Catbert.ExecutionPosition) popupStack.peek()).getUI().widg.getName(),
                  ((Catbert.ExecutionPosition) popupStack.peek()).getUI().getNumTextInputs(0)>0);
        }
        else
        {
          uiMgr.getRootPanel().getRenderEngine().setMenuHint(widg.getName() , null, comp.getNumTextInputs(0)>0);
        }
      }
      finally
      {
        processingPopup = false;
        if (acquiredLock[0])
          uiMgr.clearLock();
      }
      if (UIManager.ENABLE_STUDIO && uiMgr.getStudio() != null)
      {
        // For the UI Component debug view
        uiMgr.getStudio().refreshUITree();
      }
    }
  }
  private ZPseudoComp getTopRootComp()
  {
    if (popupStack.isEmpty())
      return comp;
    else
      return ((Catbert.ExecutionPosition) popupStack.peek()).getUI();
  }
  ZPseudoComp getTopPopup()
  {
    if (popupStack.isEmpty())
      return null;
    else
      return ((Catbert.ExecutionPosition) popupStack.peek()).getUI();
  }
  public boolean hasPopup() { return !popupStack.isEmpty(); }
  void waitUntilHookedPopupsAreClosed(Object hookKey)
  {
    synchronized (popupStack)
    {
      while (true)
      {
        boolean stillActive = false;
        for (int i = 0; i < popupStack.size(); i++)
        {
          if (((Catbert.ExecutionPosition) popupStack.get(i)).getContext().
              safeLookup(Catbert.HOOK_WIDGET_VAR) == hookKey)
          {
            stillActive = true;
            break;
          }
        }
        if (!stillActive)
          return;
        try{popupStack.wait();}catch(Exception e){}
      }
    }
  }

  private void ensureMenuFocus(boolean refresh)
  {
    ZPseudoComp testPopup = getTopPopup();
    if (testPopup != null)
    {
      if (testPopup.hasFocusableChildren() && !testPopup.doesHierarchyHaveFocus())
      {
        if (refresh)
        {
          if (!testPopup.setDefaultFocus())
            testPopup.checkForcedFocus();
        }
        else
        {
          if (!testPopup.checkForcedFocus())
            testPopup.setDefaultFocus();
        }
      }
    }
    else
    {
      if (comp.hasFocusableChildren() && !comp.doesHierarchyHaveFocus())
      {
        if (refresh)
        {
          if (!comp.setDefaultFocus())
            comp.checkForcedFocus();
        }
        else
        {
          if (!comp.checkForcedFocus())
            comp.setDefaultFocus();
        }
      }
    }
  }

  public void repaintByWidgetName(String refreshName)
  {
    if (refreshName != null && refreshName.length() > 0)
    {
      if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu repaint(" + refreshName + ") called");
      boolean[] acquiredLock = new boolean[1];
      if (!uiMgr.getLock(true, acquiredLock, true))
      {
        if (Sage.DBG) System.out.println("skipping repaint due to debug mode:"  + refreshName);
        return;
      }
      try
      {
        comp.repaintByWidgetName(refreshName);
      }
      finally
      {
        if (acquiredLock[0])
          uiMgr.clearLock();
      }
      uiMgr.repaintNextRegionChange = true;
      if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu repaint() finished");
    }
    else
      repaint();
  }

  public void repaint()
  {
    if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu repaint() called");
    boolean[] acquiredLock = new boolean[1];
    if (!uiMgr.getLock(true, acquiredLock, true))
    {
      if (Sage.DBG) System.out.println("skipping repaint due to debug mode");
      return;
    }
    try
    {
      comp.appendToDirty(true);
    }
    finally
    {
      if (acquiredLock[0])
        uiMgr.clearLock();
    }
    uiMgr.repaintNextRegionChange = true;
    if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu repaint() finished");
  }

  public void refreshByWidgetName(String refreshName)
  {
    if (!comp.hasValidFocusTargetRect())
      comp.updateFocusTargetRect(0);
    if (refreshName != null && refreshName.length() > 0)
    {
      if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu refresh(" + refreshName + ") called");
      boolean[] acquiredLock = new boolean[1];
      if (!uiMgr.getLock(true, acquiredLock, true))
      {
        if (Sage.DBG) System.out.println("skipping refresh due to debug mode:"  + refreshName);
        return;
      }
      try
      {
        comp.refreshByWidgetName(refreshName);
        if (!processingPopup)
          ensureMenuFocus(false);
      }
      finally
      {
        if (acquiredLock[0])
          uiMgr.clearLock();
      }
      uiMgr.repaintNextRegionChange = true;
      if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu refresh() finished");
    }
    else
      refresh();
  }

  public void refreshByValue(String name, Object value)
  {
    if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu refreshByValue(" + name + ", " + value + ") called");
    boolean[] acquiredLock = new boolean[1];
    if (!uiMgr.getLock(true, acquiredLock, true))
    {
      if (Sage.DBG) System.out.println("skipping refresh due to debug mode:"  + name);
      return;
    }
    if (!comp.hasValidFocusTargetRect())
      comp.updateFocusTargetRect(0);
    try
    {
      comp.refreshByValue(name, value);
      if (!processingPopup)
        ensureMenuFocus(false);
    }
    finally
    {
      if (acquiredLock[0])
        uiMgr.clearLock();
    }
    uiMgr.repaintNextRegionChange = true;
    if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu refresh() finished");
  }

  public void refresh()
  {
    if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu refresh() called");
    boolean[] acquiredLock = new boolean[1];
    if (!uiMgr.getLock(true, acquiredLock, true))
    {
      if (Sage.DBG) System.out.println("skipping refresh due to debug mode");
      return;
    }
    if (!comp.hasValidFocusTargetRect())
      comp.updateFocusTargetRect(0);
    try
    {
      //comp.invalidateAll();
      comp.appendToDirty(true); // Changed from above on 11/7/03, because Shapes weren't being refreshed
      comp.evaluateTree(true, false);
      if (!processingPopup)
        ensureMenuFocus(true);
      comp.evaluateTree(false, true);
    }
    finally
    {
      if (acquiredLock[0])
        uiMgr.clearLock();
    }
    uiMgr.repaintNextRegionChange = true;
    if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("PseudoMenu refresh() finished");
  }

  // 2/22/08 - Narflex - The setFocusByValue and ensureVisibilityForValue methods DEFINITELY need to have
  // the UI lock when they're running since they can force UI rebuilds
  public boolean setFocusByValue(String name, Object value)
  {
    boolean[] acquiredLock = new boolean[1];
    uiMgr.getLock(true, acquiredLock);
    try
    {
      return getTopRootComp().setFocusByValue(name, value, false);
    }
    finally
    {
      if (acquiredLock[0])
        uiMgr.clearLock();
    }
  }

  public ZPseudoComp getCompForVariable(String matchName, Object matchValue, ZPseudoComp searchContext)
  {
    //System.out.println("getCompForVariable name=" + matchName + " value=" + matchValue);
    ZPseudoComp searchRoot = comp;
    if (searchContext != null)
      searchRoot = searchContext.getTopPseudoParent();
    Object[] rvHolder = new Object[2];
    // First position holds any visible children which match; those return with priority,
    // Second position holds the first hidden child which matches
    searchRoot.getCompForVariable(matchName, matchValue, rvHolder, true);
    ZPseudoComp rv;
    if (rvHolder[0] != null)
      rv = (ZPseudoComp) rvHolder[0];
    else
      rv = (ZPseudoComp) rvHolder[1];
    if (rv == null && searchRoot != comp && uiMgr.isXBMCCompatible() && "MenuXBMCID".equals(matchName))
    {
      comp.getCompForVariable(matchName, matchValue, rvHolder, true);
      if (rvHolder[0] != null)
        rv = (ZPseudoComp) rvHolder[0];
      else
        rv = (ZPseudoComp) rvHolder[1];
    }
    return rv;
  }

  public boolean getPassesConditionalForVariable(String matchName, Object matchValue)
  {
    Boolean rv = comp.getPassesConditionalForVariable(matchName, matchValue);
    if (rv != null)
      return rv.booleanValue();
    else
      return false;
  }

  public boolean ensureVisbilityForValue(String name, Object value, int displayIndex)
  {
    boolean[] acquiredLock = new boolean[1];
    uiMgr.getLock(true, acquiredLock);
    try
    {
      return comp.ensureVisibilityForValue(name, value, displayIndex);
    }
    finally
    {
      if (acquiredLock[0])
        uiMgr.clearLock();
    }
  }

  public String toString()
  {
    return super.toString() + "[" + widg.getName() + "]";
  }

  protected void addEventListenersFromList(Widget[] listenWidgs)
  {
    for (int i = 0; i < listenWidgs.length; i++)
    {
      Widget ueKid = listenWidgs[i];
      //if (ueKid.widgetType == Widget.LISTENER)
      if (ueKid.isType(Widget.LISTENER))
      {
        String evtName = "";
        if (ueKid.hasProperty(Widget.LISTENER_EVENT))
          evtName = ueKid.getStringProperty(Widget.LISTENER_EVENT, null, comp);
        else
          evtName = ueKid.getName();
        if (evtName.length() > 0)
        {
          if (ueListenMap == null)
            ueListenMap = new Widget[UserEvent.ANYTHING + ZPseudoComp.EXTRA_UE_INDICES + 1];
          if (Widget.MOUSE_CLICK.equals(evtName))
            ueListenMap[ZPseudoComp.UE_INDEX_MOUSE_CLICK] = ueKid;
          else if (Widget.MOUSE_DRAG.equals(evtName))
            ueListenMap[ZPseudoComp.UE_INDEX_MOUSE_DRAG] = ueKid;
          else if (Widget.MOUSE_MOVE.equals(evtName))
            ueListenMap[ZPseudoComp.UE_INDEX_MOUSE_MOVE] = ueKid;
          else if (Widget.MOUSE_ENTER.equals(evtName))
            ueListenMap[ZPseudoComp.UE_INDEX_MOUSE_ENTER] = ueKid;
          else if (Widget.MOUSE_EXIT.equals(evtName))
            ueListenMap[ZPseudoComp.UE_INDEX_MOUSE_EXIT] = ueKid;
          else if (Widget.RAW_IR.equals(evtName))
            ueListenMap[ZPseudoComp.UE_INDEX_RAW_IR] = ueKid;
          else if (Widget.RAW_KB.equals(evtName))
            ueListenMap[ZPseudoComp.UE_INDEX_RAW_KB] = ueKid;
          else if (Widget.NUMBERS.equals(evtName))
            ueListenMap[ZPseudoComp.UE_INDEX_NUMBERS] = ueKid;
          else
            ueListenMap[UserEvent.getEvtCodeForName(evtName)] = ueKid;
        }
      }
    }
  }

  public void xferStaticContext()
  {
    /*
     * NOTE: JK 8/26/05 - I found a major flaw. Apparently the staticContext is NOT getting reset
     * on every menu transition. It's only getting reset when a Menu is allocated. So this needs
     * to be called when that would have been called.
     */
    comp.getRelatedContext().getParent().getMap().putAll(uiMgr.getStaticContext());
    uiMgr.getStaticContext().clear();
  }

  public boolean setupAnimation(String widgName, String surfName, String animName, long dur, long delay, boolean interruptable)
  {
    // If the widgName ends with a '*' then we strip off that star and that means all widgets with that name
    // should match; not just the first
    boolean checkAll = false;
    if (widgName != null && widgName.charAt(widgName.length() - 1) == '*')
    {
      checkAll = true;
      widgName = widgName.substring(0, widgName.length() - 1);
      if (widgName.length() == 0)
        widgName = null;
    }
    return getTopRootComp().setupAnimation(widgName, surfName, animName, dur, delay, interruptable, checkAll);
  }

  public boolean setupAnimationVar(String widgName, String surfName, String varName, Object varValue, String animName, long dur, long delay, boolean interruptable)
  {
    // If the widgName ends with a '*' then we strip off that star and that means all widgets with that name
    // should match; not just the first
    boolean checkAll = false;
    if (widgName != null && widgName.charAt(widgName.length() - 1) == '*')
    {
      checkAll = true;
      widgName = widgName.substring(0, widgName.length() - 1);
      if (widgName.length() == 0)
        widgName = null;
    }
    return getTopRootComp().setupAnimationVar(widgName, surfName, varName, varValue, animName, dur, delay, interruptable, checkAll);
  }

  public boolean setupTransitionAnimation(String srcWidgName, String destWidgName, String surfName, String animName, long dur, long delay, boolean interruptable)
  {
    // First get the ROP from the source of the transition and then set it in the target
    RenderingOp rop = getTopRootComp().getTransitionSourceOp(srcWidgName, surfName, animName, dur, delay, interruptable);
    if (rop != null)
    {
      // If the two widget names match then don't try to find the destination now since we know it'll just be the same thing.
      if ((srcWidgName == destWidgName || (srcWidgName != null && srcWidgName.equals(destWidgName))) ||
          !getTopRootComp().setupTransitionAnimation(destWidgName, surfName, rop))
        uiMgr.getRootPanel().registerLostTransitionAnimationOp(rop, destWidgName);
      return true;
    }
    else
      return false;
  }

  public boolean disableParentClip() { return disableParentClip; }
  public boolean areLayersForced() { return forcedLayers; }

  private UIManager uiMgr;
  private Widget widg;

  private ZPseudoComp comp;

  String focusChangeSound;
  String itemSelectSound;
  String menuChangeSound;
  String userActionSound;

  private java.util.Stack popupStack;

  private Widget[] ueListenMap;

  private boolean active;
  private boolean disableParentClip;
  private boolean forcedLayers;
  // We don't want to be ensuring focus is set properly if we're in the middle of adding or removing an options menu.
  // Quite often the STV will do a Refresh() call during this transitional state and then focus will be set someplace that we didn't want it to be.
  private boolean processingPopup;

  private boolean multipleTextInputs;
}
