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

public class ZPseudoComp extends ZComp implements java.awt.event.MouseListener,
    SageTVActionListener, java.awt.event.MouseMotionListener, ResourceLoadListener
{
  public static final boolean DEBUG_PAINTING = false;
  public static final boolean DEBUG_EFFECTS = false;
  protected static final int EXTRA_UE_INDICES = 9;
  protected static final int UE_INDEX_MOUSE_CLICK = UserEvent.ANYTHING + 1;
  protected static final int UE_INDEX_MOUSE_DRAG = UserEvent.ANYTHING + 2;
  protected static final int UE_INDEX_RAW_IR = UserEvent.ANYTHING + 3;
  protected static final int UE_INDEX_RAW_KB = UserEvent.ANYTHING + 4;
  protected static final int UE_INDEX_NUMBERS = UserEvent.ANYTHING + 5;
  protected static final int UE_INDEX_MOUSE_MOVE = UserEvent.ANYTHING + 6;
  protected static final int UE_INDEX_MOUSE_ENTER = UserEvent.ANYTHING + 7;
  protected static final int UE_INDEX_MOUSE_EXIT = UserEvent.ANYTHING + 8;

  public static final int ALL_FOCUS_CHANGES = 1;
  public static final int PARENT_FOCUS_CHANGES = 2;

  // The hidden flag means that any children that pass their own conditional (not a hierarchy check) should
  // render themselves as well. This happens for all children of a forced hide/unfocus effect.
  // Children that fail their conditional could still be rendered if they have their own forced effect.
  protected static final int RENDER_FLAG_HIDDEN = 0x1;
  // The forced only flag means that we should continue down the hierarchy for rendering; but if
  // we hit an actual rendered component like Text or an Image; then we only allow it to render if it
  // has its own forced rendering effect.
  protected static final int RENDER_FLAG_FORCED_ONLY = 0x2;
  // This flag is used when the area that is being scrolled off is rendered in order to hide what was
  // the old focus item from rendering itself. We identify these component by them either being in the
  // LayerFocus or by having a FocusTracker effect.
  protected static final int RENDER_FLAG_SKIP_FOCUSED = 0x4;
  // This flag is set when we detect an active effect which has a negative scaling factor in the X dimension
  protected static final int RENDER_FLAG_FLIP_X = 0x8;
  // This flag is set when we detect an active effect which has a negative scaling factor in the Y dimension
  protected static final int RENDER_FLAG_FLIP_Y = 0x10;
  // This flag is set if we have scrolling effects that are active above us
  protected static final int RENDER_FLAG_SCROLLING = 0x20;

  public static final String PAYLOAD_VAR = "EventPayloads";

  public static final float SIZE_FLOAT_EPSILON = 0.01f;
  public ZPseudoComp(Widget inWidg)
  {
    this(inWidg, new java.util.ArrayList(), null);
  }
  public ZPseudoComp(Widget inWidg, java.util.ArrayList inDefaultThemes, Catbert.Context inContext)
  {
    this(inWidg, inDefaultThemes, inContext, null);
  }
  public ZPseudoComp(Widget inWidg, java.util.ArrayList inDefaultThemes, Catbert.Context inContext,
      java.util.ArrayList inParentActions)
  {
    super(inContext.getUIMgr().getRootPanel());
    uiMgr = inContext.getUIMgr();
    widg = inWidg;
    widgType = widg.type();
    //compToActionMap = new java.util.HashMap();
    //ueListenMap = new Widget[UserEvent.MAX_EVT_ID + EXTRA_UE_INDICES];
    relatedContext = new Catbert.Context(inContext);

    rootParentAction = inParentActions == null ? null : (Widget) inParentActions.get(0);
    parentActions = inParentActions == null ? null : new java.util.HashSet(inParentActions);

    if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.CREATE_UI, this, widg, null);

    // Check for conditional UI
    if (parentActions != null)
    {
      Widget[] bPar = widg.containers(Widget.BRANCH);
      for (int i = 0; i < bPar.length; i++)
        if (parentActions.contains(bPar[i]))
        {
          conditionalUIWidg = bPar[i];
          break;
        }
      if (conditionalUIWidg == null)
      {
        bPar = widg.containers(Widget.CONDITIONAL);
        for (int i = 0; i < bPar.length; i++)
          if (parentActions.contains(bPar[i]))
          {
            conditionalUIWidg = bPar[i];
            break;
          }
      }
      if (conditionalUIWidg != null)
        parentActionsMayBeConditional = true;
      else
      {
        java.util.Iterator walker = parentActions.iterator();
        while (walker.hasNext())
        {
          Widget w = (Widget) walker.next();
          if (w.isType(Widget.CONDITIONAL))
          {
            parentActionsMayBeConditional = true;
            break;
          }
        }
      }
    }

    //System.out.println("Create ZPseudo for " + widg + " context=" + relatedContext);

    defaultThemes = (inDefaultThemes == null) ? new java.util.ArrayList() : new java.util.ArrayList(inDefaultThemes);

    // Check for a theme
    Widget[] themeOpts = widg.contents();
    for (int i = 0; i < themeOpts.length; i++)
      // 601 if (themeOpts[i].widgetType == Widget.THEME)
      if (themeOpts[i].isType(Widget.THEME))
      {
        currTheme = themeOpts[i];
        break;
      }

    propWidg = widg;
    // Check for any themes from Widget type themes (really only needed to deal with background selected)
    containerTheme = getWidgetChildFromWidgetChain(widgType, currTheme, defaultThemes);
    if (containerTheme != null)
    {
      propWidg = containerTheme;
      Widget[] themedThemes = containerTheme.contents();
      for (int i = 0; i < themedThemes.length; i++)
      {
        // 601 if (themedThemes[i].widgetType == Widget.THEME)
        if (themedThemes[i].isType(Widget.THEME))
        {
          if (currTheme != null)
            defaultThemes.add(0, currTheme);
          currTheme = themedThemes[i];
          break;
        }
      }
    }

    renderStartHook = widg.contentsSingularName(Widget.HOOK, "RenderingStarted");
    if (renderStartHook == null && propWidg != widg)
      renderStartHook = propWidg.contentsSingularName(Widget.HOOK, "RenderingStarted");
    layoutStartHook = widg.contentsSingularName(Widget.HOOK, "LayoutStarted");
    if (layoutStartHook == null && propWidg != widg)
      layoutStartHook = propWidg.contentsSingularName(Widget.HOOK, "LayoutStarted");
    focusGainedHook = widg.contentsSingularName(Widget.HOOK, "FocusGained");
    if (focusGainedHook == null && propWidg != widg)
      focusGainedHook = propWidg.contentsSingularName(Widget.HOOK, "FocusGained");
    focusLostHook = widg.contentsSingularName(Widget.HOOK, "FocusLost");
    if (focusLostHook == null && propWidg != widg)
      focusLostHook = propWidg.contentsSingularName(Widget.HOOK, "FocusLost");

    if (widg.getBooleanProperty(Widget.IGNORE_THEME_PROPERTIES, null, this))
      propWidg = widg;

    backgroundComponent = propWidg.getBooleanProperty(Widget.BACKGROUND_COMPONENT, null, this);
    mouseTransparency = propWidg.getBooleanProperty(Widget.MOUSE_TRANSPARENCY, null, this);

    loadAttributeContext();

    loadLayoutProps();

    if (currTheme != null)
    {
      useBGImage = currTheme.hasProperty(Widget.BACKGROUND_IMAGE);
      useFocusBGImage = currTheme.hasProperty(Widget.BACKGROUND_SELECTED_IMAGE);
      if (useFocusBGImage)
        focusListener = focusListener | PARENT_FOCUS_CHANGES;
    }

    themeFont = null;
    java.awt.Color themeFG = null;
    java.awt.Color themeShadowFG = null;
    if (currTheme != null)
    {
      if (currTheme.hasProperty(Widget.BACKGROUND_COLOR))
      {
        bgColor = currTheme.getColorProperty(Widget.BACKGROUND_COLOR, null, this);
        dynamicOriginalBgColor = currTheme.isDynamicProperty(Widget.BACKGROUND_COLOR);
      }
      if (currTheme.hasProperty(Widget.BACKGROUND_SELECTED_COLOR))
      {
        focusBgColor = currTheme.getColorProperty(Widget.BACKGROUND_SELECTED_COLOR, null, this);
        focusListener = focusListener | PARENT_FOCUS_CHANGES;
        dynamicFocusBgColor = currTheme.isDynamicProperty(Widget.BACKGROUND_SELECTED_COLOR);
      }
    }

    // Now search the currTheme and the defaultThemes for any foreground information
    java.awt.Color fgColorProp = getColorPropertyFromWidgetChain(Widget.FOREGROUND_COLOR, currTheme, defaultThemes);
    if (fgColorProp != null)
    {
      fgColor = fgColorProp;
      int fgAlphaProp = getIntPropertyFromWidgetChain(Widget.FOREGROUND_ALPHA, currTheme, defaultThemes, -1);
      if (fgAlphaProp >= 0)
        fgColor = new java.awt.Color(((fgAlphaProp & 0xFF) << 24) | (fgColor.getRGB() & 0xFFFFFF), true);
      themeFG = fgColor;

      Widget widg1 = getWidgetPropertyOwnerFromWidgetChain(Widget.FOREGROUND_COLOR, currTheme, defaultThemes);
      if (widg1 != null && widg1.isDynamicProperty(Widget.FOREGROUND_COLOR))
        dynamicFgColor = true;
      else
      {
        widg1 = getWidgetPropertyOwnerFromWidgetChain(Widget.FOREGROUND_ALPHA, currTheme, defaultThemes);
        if (widg1 != null && widg1.isDynamicProperty(Widget.FOREGROUND_ALPHA))
          dynamicFgColor = true;
      }
    }
    java.awt.Color fgSelColorProp = getColorPropertyFromWidgetChain(Widget.FOREGROUND_SELECTED_COLOR, currTheme, defaultThemes);
    if (fgSelColorProp != null)
    {
      focusFgColor = fgSelColorProp;
      int fgSelAlphaProp = getIntPropertyFromWidgetChain(Widget.FOREGROUND_SELECTED_ALPHA, currTheme, defaultThemes, -1);
      if (fgSelAlphaProp >= 0)
        focusFgColor = new java.awt.Color(((fgSelAlphaProp & 0xFF) << 24) | (focusFgColor.getRGB() & 0xFFFFFF), true);

      Widget widg1 = getWidgetPropertyOwnerFromWidgetChain(Widget.FOREGROUND_SELECTED_COLOR, currTheme, defaultThemes);
      if (widg1 != null && widg1.isDynamicProperty(Widget.FOREGROUND_SELECTED_COLOR))
        dynamicFocusFgColor = true;
      else
      {
        widg1 = getWidgetPropertyOwnerFromWidgetChain(Widget.FOREGROUND_SELECTED_ALPHA, currTheme, defaultThemes);
        if (widg1 != null && widg1.isDynamicProperty(Widget.FOREGROUND_SELECTED_ALPHA))
          dynamicFocusFgColor = true;
      }
    }
    java.awt.Color fgShadowColorProp = getColorPropertyFromWidgetChain(Widget.FOREGROUND_SHADOW_COLOR, currTheme, defaultThemes);
    if (fgShadowColorProp != null)
    {
      fgShadowColor = fgShadowColorProp;
      int fgAlphaProp = getIntPropertyFromWidgetChain(Widget.FOREGROUND_SHADOW_ALPHA, currTheme, defaultThemes, -1);
      if (fgAlphaProp >= 0)
        fgShadowColor = new java.awt.Color(((fgAlphaProp & 0xFF) << 24) | (fgShadowColor.getRGB() & 0xFFFFFF), true);
      themeShadowFG = fgShadowColor;

      Widget widg1 = getWidgetPropertyOwnerFromWidgetChain(Widget.FOREGROUND_SHADOW_COLOR, currTheme, defaultThemes);
      if (widg1 != null && widg1.isDynamicProperty(Widget.FOREGROUND_SHADOW_COLOR))
        dynamicFgShadowColor = true;
      else
      {
        widg1 = getWidgetPropertyOwnerFromWidgetChain(Widget.FOREGROUND_SHADOW_ALPHA, currTheme, defaultThemes);
        if (widg1 != null && widg1.isDynamicProperty(Widget.FOREGROUND_SHADOW_ALPHA))
          dynamicFgShadowColor = true;
      }
    }
    java.awt.Color fgShadowSelColorProp = getColorPropertyFromWidgetChain(Widget.FOREGROUND_SHADOW_SELECTED_COLOR, currTheme, defaultThemes);
    if (fgShadowSelColorProp != null)
    {
      focusFgShadowColor = fgShadowSelColorProp;
      int fgSelAlphaProp = getIntPropertyFromWidgetChain(Widget.FOREGROUND_SHADOW_SELECTED_ALPHA, currTheme, defaultThemes, -1);
      if (fgSelAlphaProp >= 0)
        focusFgShadowColor = new java.awt.Color(((fgSelAlphaProp & 0xFF) << 24) | (focusFgShadowColor.getRGB() & 0xFFFFFF), true);

      Widget widg1 = getWidgetPropertyOwnerFromWidgetChain(Widget.FOREGROUND_SHADOW_SELECTED_COLOR, currTheme, defaultThemes);
      if (widg1 != null && widg1.isDynamicProperty(Widget.FOREGROUND_SHADOW_SELECTED_COLOR))
        dynamicFocusFgShadowColor = true;
      else
      {
        widg1 = getWidgetPropertyOwnerFromWidgetChain(Widget.FOREGROUND_SHADOW_SELECTED_ALPHA, currTheme, defaultThemes);
        if (widg1 != null && widg1.isDynamicProperty(Widget.FOREGROUND_SHADOW_SELECTED_ALPHA))
          dynamicFocusFgShadowColor = true;
      }
    }

    String fontFaceProp = getStringPropertyFromWidgetChain(Widget.FONT_FACE, currTheme, defaultThemes);
    if (fontFaceProp.length() > 0)
    {
      Widget fontWidg = getWidgetPropertyOwnerFromWidgetChain(Widget.FONT_FACE, currTheme, defaultThemes);
      if (fontWidg != null && fontWidg.isDynamicProperty(Widget.FONT_FACE))
        dynamicThemeFontFace = true;
      int fontSize = 18;
      int fontStyle = MetaFont.PLAIN;
      int fontSizeProp = getIntPropertyFromWidgetChain(Widget.FONT_SIZE, currTheme, defaultThemes, -1);
      if (fontSizeProp > 0)
        fontSize = fontSizeProp;
      fontWidg = getWidgetPropertyOwnerFromWidgetChain(Widget.FONT_SIZE, currTheme, defaultThemes);
      if (fontWidg != null && fontWidg.isDynamicProperty(Widget.FONT_SIZE))
        dynamicThemeFontSize = true;
      String fontStyleProp = getStringPropertyFromWidgetChain(Widget.FONT_STYLE, currTheme, defaultThemes);
      if (fontStyleProp.length() > 0)
        fontStyle = WidgetMeta.getFontStyleForName(fontStyleProp);
      fontWidg = getWidgetPropertyOwnerFromWidgetChain(Widget.FONT_STYLE, currTheme, defaultThemes);
      if (fontWidg != null && fontWidg.isDynamicProperty(Widget.FONT_STYLE))
        dynamicThemeFontStyle = true;
      themeFont = uiMgr.getCachedMetaFont(fontFaceProp, fontStyle, fontSize, uiMgr);
    }
    if (currTheme != null)
      defaultThemes.add(0, currTheme);
    originalFgColor = fgColor;
    originalFgShadowColor = fgShadowColor;
    originalBgColor = bgColor;

    // Setup any UE listeners
    if (containerTheme != null)
      addEventListenersFromList(containerTheme.contents());
    if (currTheme != null)
      addEventListenersFromList(currTheme.contents());
    addEventListenersFromList(widg.contents());

    if (widgType == Widget.PANEL || widgType == Widget.MENU ||
        widgType == Widget.ITEM || widgType == Widget.OPTIONSMENU)
    {
      // prevent from adding a widget more than once if it has conditional branch parenting to change its value,
      // but be sure to update the parent action set to include these additional options
      java.util.HashMap alreadyChecked = Pooler.getPooledHashMap();

      if (Sage.PERF_ANALYSIS)
        perfTime = Sage.time();
      // Create all of the child panels and add them
      // Add any container theme widgets
      if (containerTheme != null)
      {
        Widget[] contThemeKids = containerTheme.contents();
        for (int i = 0; i < contThemeKids.length; i++)
          addChildrenFromWidgetChain(contThemeKids[i], -(contThemeKids.length - i), alreadyChecked);
      }
      Widget[] widgKids = widg.contents();
      for (int i = 0; i < widgKids.length; i++)
        addChildrenFromWidgetChain(widgKids[i], i, alreadyChecked);

      if (Sage.PERF_ANALYSIS)
      {
        perfTime = Sage.time() - perfTime;
        if (perfTime > Sage.UI_BUILD_THRESHOLD_TIME)
        {
          // Check if we are the bottleneck by comparing us to the timing of all the children
          long childTotalTime = 0;
          for (int i = 0; i < numKids; i++)
          {
            childTotalTime += kids[i].perfTime;
          }
          if (perfTime - childTotalTime > Sage.UI_BUILD_THRESHOLD_TIME)
          {
            System.out.println("UI BUILD PERF time=" + (perfTime - childTotalTime) + " widg=" + widg);
          }
        }
      }

      // Return all of the ArrayList values in the Map to the Pooler since that's where they all came from
      java.util.Iterator walker = alreadyChecked.values().iterator();
      while (walker.hasNext())
      {
        Object nextie = walker.next();
        if (nextie instanceof java.util.ArrayList)
          Pooler.returnPooledArrayList((java.util.ArrayList) nextie);
      }
      Pooler.returnPooledHashMap(alreadyChecked);
      alreadyChecked = null;

      if (widgType == Widget.MENU && propWidg.getBooleanProperty(Widget.VIDEO_BACKGROUND, null, this))
      {
        addSubtitleTextChildren();
      }

      if (widgType == Widget.PANEL)
      {
        scrolling = propWidg.getIntProperty(Widget.SCROLLING, 0, null, this);
        if (inContext != null && scrolling != 0)
        {
          inContext.setLocal("IsFirstPage", Boolean.FALSE);
          inContext.setLocal("IsFirstHPage", Boolean.FALSE);
          inContext.setLocal("IsFirstVPage", Boolean.FALSE);
          inContext.setLocal("IsLastPage", Boolean.FALSE);
          inContext.setLocal("IsLastVPage", Boolean.FALSE);
          inContext.setLocal("IsLastHPage", Boolean.FALSE);
          inContext.setLocal("NumPages", new Integer(1));
          inContext.setLocal("NumHPages", new Integer(1));
          inContext.setLocal("NumVPages", new Integer(1));
          inContext.setLocal("NumHPagesF", new Float(1));
          inContext.setLocal("NumVPagesF", new Float(1));
        }
      }

      if (Widget.ITEM == widgType)
      {
        // Check if we have any non-background widget children
        boolean nonBGKids = false;
        for (int i = 0; i < numKids && !nonBGKids; i++)
        {
          if (kids[i] != null && !kids[i].backgroundComponent)
            nonBGKids = true;
        }
        if (!nonBGKids)
        {
          // Just add a text child with our name
          ZLabel newKid;
          add(newKid = new ZLabel(reality, widg.getName(), themeFont));
          newKid.mouseTransparency = mouseTransparency;
          newKid.childWidgetIndex = -1;
          Widget textPropWidg = null;
          // Check for any themes from Widget type themes for the text
          Widget textContainerTheme = getWidgetChildFromWidgetChain(Widget.TEXT, currTheme, defaultThemes);
          if (textContainerTheme != null)
          {
            textPropWidg = textContainerTheme;
            Widget[] themedThemes = textContainerTheme.contents();
            for (int i = 0; i < themedThemes.length; i++)
            {
              // 601 if (themedThemes[i].widgetType == Widget.THEME)
              if (themedThemes[i].isType(Widget.THEME))
              {
                if (currTheme != null)
                  defaultThemes.add(0, currTheme);
                currTheme = themedThemes[i];
                break;
              }
            }
          }
          if (widg.getBooleanProperty(Widget.IGNORE_THEME_PROPERTIES, null, this))
            textPropWidg = null;

          if (themeFG != null)
            newKid.setForeground(themeFG);
          if (themeShadowFG != null)
            newKid.setForegroundShadow(themeShadowFG);
          if (textPropWidg != null)
          {
            newKid.setSingleLine(!textPropWidg.getBooleanProperty(Widget.WRAP_TEXT, null, this));
            if (textPropWidg.getBooleanProperty(Widget.AUTOSIZE_TEXT, null, this))
              newKid.setFitToSize(true);
            newKid.setTextShadow(textPropWidg.getBooleanProperty(Widget.TEXT_SHADOW, null, this));
            if (textPropWidg.getBooleanProperty(Widget.DISABLE_FONT_SCALING, null, this))
              newKid.setDisableFontScaling(true);
          }
          else
          {
            newKid.setSingleLine(true);
            newKid.setTextShadow(true);
            newKid.setFitToSize(true);
          }
          newKid.setHAlign(propWidg.getFloatProperty(Widget.HALIGNMENT, 0.5f, null, this));
          newKid.setVAlign(propWidg.getFloatProperty(Widget.VALIGNMENT, 0.5f, null, this));
        }
        // Also check for Themed process chain triggers
        Widget[] allContents;
        if (propWidg != widg)
        {
          allContents = propWidg.contents();
          for (int j = 0; j < allContents.length; j++)
          {
            byte currType = allContents[j].type();
            if (currType == Widget.ACTION || currType == Widget.CONDITIONAL ||
                currType == Widget.OPTIONSMENU || currType == Widget.MENU)
            {
              if (allContents[j].isInProcessChain())
              {
                if (compToActionMap == null)
                  compToActionMap = new java.util.HashMap();
                compToActionMap.put(this, allContents[j]);
              }
            }
          }
        }
        allContents = widg.contents();
        for (int j = 0; j < allContents.length; j++)
        {
          byte currType = allContents[j].type();
          if (currType == Widget.ACTION || currType == Widget.CONDITIONAL ||
              currType == Widget.OPTIONSMENU || currType == Widget.MENU)
          {
            if (allContents[j].isInProcessChain())
            {
              if (compToActionMap == null)
                compToActionMap = new java.util.HashMap();
              compToActionMap.put(this, allContents[j]);
            }
          }
        }
      }
      // Items could have this disabled during construction; but re-enabled later so always add this in the case of an Item Widget
      if (isFocusable() || widgType == Widget.ITEM)
      {
        // Recursively add mouselisteners all the way down
        addMouseListenerRecursive(this, false);

        // Focus changes should always be reflected by a UI change
        focusListener = focusListener | PARENT_FOCUS_CHANGES;
      }
    }
    else if (widgType == Widget.TABLECOMPONENT)
    {
      // This is all done dynamically, so don't add anything here
    }
    else if (widgType == Widget.VIDEO)
    {
      addSubtitleTextChildren();
    }
    else
    {
      ZComp newKid = null;
      // 601 if (Widget.IMAGE == widg.widgetType)
      if (widgType == Widget.IMAGE)
      {
        ZImage newImage;
        // We check the theme and the original Widget for the source file since that's quite often changed for an Image Widget even though
        // we may want constant theming for other purposes.
        MetaImage mia = MetaImage.getMetaImage(propWidg.hasProperty(Widget.FILE) ? propWidg.getStringProperty(Widget.FILE, null, this) :
          widg.getStringProperty(Widget.FILE, null, this), this);
        MetaImage mib = (propWidg.hasProperty(Widget.PRESSED_FILE) ?
            MetaImage.getMetaImage(propWidg.getStringProperty(Widget.PRESSED_FILE, null, this), this) : null);
        MetaImage mih = (propWidg.hasProperty(Widget.HOVER_FILE) ?
            MetaImage.getMetaImage(propWidg.getStringProperty(Widget.HOVER_FILE, null, this), this) : null);
        add(newKid = newImage = new ZImage(reality, mia, mib, null, mih));
        newImage.mouseTransparency = mouseTransparency;
        newImage.setHAlignment(propWidg.getFloatProperty(Widget.HALIGNMENT, 0.5f, null, this));
        newImage.setVAlignment(propWidg.getFloatProperty(Widget.VALIGNMENT, 0.5f, null, this));
        // This is now done before calling the buildRenderingOp method on ZImage by modulating it's alphaFactor argument
        //newImage.setAlpha(propWidg.getFloatProperty(Widget.FOREGROUND_ALPHA, 1.0f, null, this));
        newImage.setStretch(!propWidg.getBooleanProperty(Widget.PRESERVE_ASPECT_RATIO, null, this));
        newImage.setScaling(propWidg.getBooleanProperty(Widget.RESIZE_IMAGE, null, this));
        newImage.setCropToFill(propWidg.getBooleanProperty(Widget.CROP_TO_FILL, null, this));
        newImage.setAutoRepeat(propWidg.getBooleanProperty(Widget.AUTO_REPEAT_ACTION, null, this));
        newImage.setCrossFadeDuration(propWidg.getIntProperty(Widget.DURATION, 0, null, this));
        newImage.setBgLoader(propWidg.getBooleanProperty(Widget.BACKGROUND_LOAD, null, this));
        Number[] scalingInsetsNums = propWidg.getNumericArrayProperty(Widget.SCALING_INSETS, null, this);
        if (scalingInsetsNums != null && scalingInsetsNums.length > 0)
        {
          if (scalingInsetsNums.length >= 4)
            newImage.setScalingInsets(new java.awt.Insets(scalingInsetsNums[0].intValue(),
                scalingInsetsNums[1].intValue(), scalingInsetsNums[2].intValue(),
                scalingInsetsNums[3].intValue()));
          else
            newImage.setScalingInsets(new java.awt.Insets(scalingInsetsNums[0].intValue(),
                scalingInsetsNums[0].intValue(), scalingInsetsNums[0].intValue(),
                scalingInsetsNums[0].intValue()));
        }

        if (propWidg.hasProperty(Widget.USER_EVENT))
        {
          newImage.addActionListener(this);
          if (compToActionMap == null)
            compToActionMap = new java.util.HashMap();
          compToActionMap.put(newKid, new Integer(propWidg.getIntProperty(Widget.USER_EVENT, 0, null, this)));
        }
        else
        {
          Widget[] allContents;
          boolean addedListyYet = false;
          // Also check for themed event responses
          if (propWidg != widg)
          {
            allContents = propWidg.contents();
            for (int j = 0; j < allContents.length; j++)
            {
              byte currType = allContents[j].type();
              if (currType == Widget.ACTION || currType == Widget.CONDITIONAL ||
                  currType == Widget.OPTIONSMENU || currType == Widget.MENU)
              {
                if (allContents[j].isInProcessChain())
                {
                  if (!addedListyYet)
                  {
                    addedListyYet = true;
                    newImage.addActionListener(this);
                  }
                  if (compToActionMap == null)
                    compToActionMap = new java.util.HashMap();
                  compToActionMap.put(newKid, allContents[j]);
                }
              }
            }
          }
          allContents = widg.contents();
          for (int j = 0; j < allContents.length; j++)
          {
            byte currType = allContents[j].type();
            if (currType == Widget.ACTION || currType == Widget.CONDITIONAL ||
                currType == Widget.OPTIONSMENU || currType == Widget.MENU)
            {
              if (allContents[j].isInProcessChain())
              {
                if (!addedListyYet)
                {
                  addedListyYet = true;
                  newImage.addActionListener(this);
                }
                if (compToActionMap == null)
                  compToActionMap = new java.util.HashMap();
                compToActionMap.put(newKid, allContents[j]);
              }
            }
          }
        }
      }
      // 601 else if (Widget.TEXT == widg.widgetType)
      else if (widgType == Widget.TEXT)
      {
        // Check for special versions
        if (widg.getName().startsWith("$Clock"))
        {
          ZClock clocky;
          if (widg.getName().trim().length() <= 6)
            clocky = new ZClock(reality, ZClock.TIME_ONLY, themeFont);
          else
          {
            String clockType = widg.getName().substring(6);
            if (clockType.equalsIgnoreCase("Date"))
              clocky = new ZClock(reality, ZClock.DATE_ONLY, themeFont);
            else if (clockType.equalsIgnoreCase("Time"))
              clocky = new ZClock(reality, ZClock.TIME_ONLY, themeFont);
            else if (clockType.equalsIgnoreCase("DateTime"))
              clocky = new ZClock(reality, ZClock.DATE_TIME, themeFont);
            else if (clockType.equalsIgnoreCase("TimeDate"))
              clocky = new ZClock(reality, ZClock.TIME_DATE, themeFont);
            else
              clocky = new ZClock(reality, new java.text.SimpleDateFormat(clockType, Sage.userLocale), null, themeFont);
          }
          clocky.setSingleLine(true);
          clocky.mouseTransparency = mouseTransparency;
          add(clocky);
          newKid = clocky;
        }
        else
        {
          add(newKid = new ZLabel(reality, widg.getName(), themeFont));
          newKid.mouseTransparency = mouseTransparency;
          ((ZLabel) newKid).setSingleLine(!propWidg.getBooleanProperty(Widget.WRAP_TEXT, null, this));
        }
        if (propWidg.getBooleanProperty(Widget.AUTOSIZE_TEXT, null, this))
          ((ZLabel) newKid).setFitToSize(true);
        ((ZLabel) newKid).setHAlign(propWidg.getFloatProperty(Widget.TEXT_ALIGNMENT, 0, null, this));
        if (propWidg.hasProperty(Widget.VALIGNMENT))
          ((ZLabel) newKid).setVAlign(propWidg.getFloatProperty(Widget.VALIGNMENT, 0.5f, null, this));
        ((ZLabel) newKid).setTextShadow(propWidg.getBooleanProperty(Widget.TEXT_SHADOW, null, this));
        if (propWidg.getBooleanProperty(Widget.DISABLE_FONT_SCALING, null, this))
          ((ZLabel) newKid).setDisableFontScaling(true);
        ((ZLabel) newKid).setCrossFadeDuration(propWidg.getIntProperty(Widget.DURATION, 0, null, this));
      }
      else if (widgType == Widget.TEXTINPUT)
      {
        Object initVal = relatedContext.safeLookup(widg.getName());
        ZLabel newLabel = new ZLabel(reality, initVal == null ? "" : initVal.toString(), themeFont)
        {
          //public boolean isFocusable() { return true; }
          public boolean setText(String s)
          {
            boolean rv;
            if (rv = super.setText(s))
              ZPseudoComp.this.cachedPrefParentHeight = ZPseudoComp.this.cachedPrefParentWidth = -1;
            relatedContext.set(widg.getName(), getText());
            return rv;
          }
        };
        newLabel.setHideText(propWidg.getBooleanProperty(Widget.HIDE_TEXT, null, this));
        newLabel.mouseTransparency = mouseTransparency;
        add(newKid = newLabel);
        newLabel.setSingleLine(true);
        newLabel.setShowTrailingWhitespace(true);
        newLabel.setHAlign(propWidg.getFloatProperty(Widget.TEXT_ALIGNMENT, 0, null, this));
        if (propWidg.hasProperty(Widget.VALIGNMENT))
          newLabel.setVAlign(propWidg.getFloatProperty(Widget.VALIGNMENT, 0.5f, null, this));
        if (propWidg.hasProperty(Widget.FOCUSABLE_CONDITION))
          newLabel.setEditable((propWidg.getBooleanProperty(Widget.FOCUSABLE_CONDITION, null, this)));
        if (newLabel.isEditable())
          kids[0].addMouseListener(this);
        relatedContext.set(widg.getName(), newLabel.getText());
      }
      if (newKid != null)
      {
        if (themeFont != null && newKid instanceof ZLabel && !(newKid instanceof ZCCLabel))
        {
          ((ZLabel) newKid).setFont(themeFont);
        }
        if (themeFG != null)
          newKid.setForeground(themeFG);
        if (themeShadowFG != null)
          newKid.setForegroundShadow(themeShadowFG);
      }
    }

    if ((widgType == Widget.VIDEO || (widgType == Widget.MENU &&
        propWidg.getBooleanProperty(Widget.VIDEO_BACKGROUND, null, this))))
    {
      addMouseListener(uiMgr.getVideoFrame().getDvdMouseListener());
      addMouseMotionListener(uiMgr.getVideoFrame().getDvdMouseMotionListener());
    }

    if (getUEListenWidget(UE_INDEX_MOUSE_CLICK) != null ||
        getUEListenWidget(UE_INDEX_MOUSE_ENTER) != null ||
        getUEListenWidget(UE_INDEX_MOUSE_EXIT) != null)
    {
      if (widgType == Widget.TEXT || widgType == Widget.TEXTINPUT || widgType == Widget.IMAGE)
        kids[0].addMouseListener(this);
      else
        addMouseListener(this);
    }
    if (getUEListenWidget(UE_INDEX_MOUSE_DRAG) != null || getUEListenWidget(UE_INDEX_MOUSE_MOVE) != null)
    {
      if (widgType == Widget.TEXT || widgType == Widget.TEXTINPUT || widgType == Widget.IMAGE)
        kids[0].addMouseMotionListener(this);
      else
        addMouseMotionListener(this);
    }
    if (Boolean.TRUE.equals(relatedContext.safeLookup("MouseMovementControlsWindow")) && uiMgr.getGlobalFrame() != null)
    {
      addMouseMotionListener(uiMgr.getGlobalFrame());
      addMouseListener(uiMgr.getGlobalFrame());
    }
  }

  protected void addSubtitleTextChildren()
  {
    // Add a text child which we can use for displaying subtitles for video
    // And another one which we can use for displaying closed captioning
    ZCCLabel newKid;
    add(newKid = new ZCCLabel(reality, true, themeFont));
    newKid.childWidgetIndex = -1;
    add(newKid = new ZCCLabel(reality, false, themeFont));
    newKid.childWidgetIndex = -1;
  }

  protected Widget getUEListenWidget(int ueType)
  {
    return ueListenMap == null ? null : ueListenMap[ueType];
  }

  protected void addEventListenersFromList(Widget[] listenWidgs)
  {
    for (int i = 0; i < listenWidgs.length; i++)
    {
      Widget ueKid = listenWidgs[i];
      // 601 if (ueKid.widgetType == Widget.LISTENER)
      if (ueKid.isType(Widget.LISTENER))
      {
        String evtName = "";
        if (ueKid.hasProperty(Widget.LISTENER_EVENT))
          evtName = ueKid.getStringProperty(Widget.LISTENER_EVENT, null, this);
        else
          evtName = ueKid.getName();
        if (evtName.length() > 0)
        {
          if (ueListenMap == null)
            ueListenMap = new Widget[UserEvent.ANYTHING + EXTRA_UE_INDICES + 1];
          if (Widget.MOUSE_CLICK.equals(evtName))
            ueListenMap[UE_INDEX_MOUSE_CLICK] = ueKid;
          else if (Widget.MOUSE_DRAG.equals(evtName))
            ueListenMap[UE_INDEX_MOUSE_DRAG] = ueKid;
          else if (Widget.MOUSE_MOVE.equals(evtName))
            ueListenMap[UE_INDEX_MOUSE_MOVE] = ueKid;
          else if (Widget.MOUSE_ENTER.equals(evtName))
            ueListenMap[UE_INDEX_MOUSE_ENTER] = ueKid;
          else if (Widget.MOUSE_EXIT.equals(evtName))
            ueListenMap[UE_INDEX_MOUSE_EXIT] = ueKid;
          else if (Widget.RAW_IR.equals(evtName))
            ueListenMap[UE_INDEX_RAW_IR] = ueKid;
          else if (Widget.RAW_KB.equals(evtName))
            ueListenMap[UE_INDEX_RAW_KB] = ueKid;
          else if (Widget.NUMBERS.equals(evtName))
            ueListenMap[UE_INDEX_NUMBERS] = ueKid;
          else
            ueListenMap[UserEvent.getEvtCodeForName(evtName)] = ueKid;
        }
      }
    }
  }

  protected void addChildrenFromWidgetChain(Widget currCheck, int widgetIndex, java.util.Map alreadyChecked)
  {
    addChildrenFromWidgetChain(currCheck, widgetIndex, alreadyChecked, null);
  }
  protected boolean addChildrenFromWidgetChain(Widget currCheck, int widgetIndex, java.util.Map alreadyChecked,
      java.util.ArrayList currParentActions)
  {
    boolean rv = false;
    if (currCheck.isInUIHierarchy())
    {
      // We should build up the currParentActions until we hit a UI component. The first one we hit
      // will be the one they're for because we know they're in the UI hierarchy. Then we map all of those
      // Widgets as having an effect on that component in the alreadyChecked map so if we hit any of them
      // again they'll be added to the parentActions set of the affected comps.
      if (currCheck.isUIComponent())
      {
        rv = true;
        ZPseudoComp newComp;
        if (currCheck.isType(Widget.TABLE))
        {
          newComp = new ZDataTable(currCheck, defaultThemes, relatedContext,
              currParentActions);
        }
        else if (currCheck.isType(Widget.TABLECOMPONENT) && (this instanceof ZDataTable))
        {
          String subtype = currCheck.getStringProperty(Widget.TABLE_SUBCOMP, null, this);
          if (subtype.equals(ZDataTable.ROW_HEADER))
          {
            ((ZDataTable) this).rowHeaderPanel = newComp =
                new ZPseudoComp(currCheck, defaultThemes, relatedContext, currParentActions);
          }
          else if (subtype.equals(ZDataTable.COL_HEADER))
          {
            ((ZDataTable) this).colHeaderPanel = newComp =
                new ZPseudoComp(currCheck, defaultThemes, relatedContext, currParentActions);
          }
          else if (subtype.equals(ZDataTable.NOOK))
          {
            ((ZDataTable) this).nook = newComp =
                new ZPseudoComp(currCheck, defaultThemes, relatedContext, currParentActions);
          }
          else if (subtype.equals(ZDataTable.EMPTY_TABLE))
          {
            ((ZDataTable) this).emptyComp = newComp =
                new ZPseudoComp(currCheck, defaultThemes, relatedContext, currParentActions);
          }
          else //if (subtype.equals(ZDataTable.CELL) || subtype.length() == 0)
          {
            ((ZDataTable) this).gridPanel = newComp =
                new ZPseudoComp(currCheck, defaultThemes, relatedContext, currParentActions);
          }
        }
        else
        {
          newComp = new ZPseudoComp(currCheck, defaultThemes, relatedContext,
              currParentActions);
        }
        newComp.childWidgetIndex = widgetIndex;
        add(newComp);
        alreadyChecked.put(currCheck, newComp);
        // Update all of the parent actions to indicate that the effect us in the map
        if (currParentActions != null)
        {
          for (int i=0; i < currParentActions.size(); i++)
          {
            Widget w = (Widget) currParentActions.get(i);
            java.util.ArrayList oldVec = (java.util.ArrayList) alreadyChecked.get(w);
            if (oldVec == null)
            {
              alreadyChecked.put(w, oldVec = Pooler.getPooledArrayList());
              oldVec.add(newComp);
            }
            else if (!oldVec.contains(newComp))
              oldVec.add(newComp);
          }
        }
      }
      else
      {
        Object oldMapObj = alreadyChecked.put(currCheck, null);
        if (oldMapObj instanceof java.util.ArrayList)
          Pooler.returnPooledArrayList((java.util.ArrayList) oldMapObj);
        boolean createdParentActions = false;
        if (currParentActions == null)
        {
          currParentActions = Pooler.getPooledArrayList();//new java.util.ArrayList();
          createdParentActions = true;
        }
        currParentActions.add(currCheck);
        Widget[] kids = currCheck.contents();
        for (int j = 0; j < kids.length; j++)
        {
          if (alreadyChecked.containsKey(kids[j]))
          {
            // NOTE: Narflex - 2/19/10 - These two rv=true statements in the conditionals below were something
            // new that I added. I had found a case where in the TV Editorials view; a panel had extra parent actions.
            // The extra one was for an image load which then caused a delay because a Panel was requesting it and not an Image.
            // The rv=true statement is meant to mean that as we return from this method; we should strip elements out of
            // the parent actions list up until (and including) the current Widget we're processing. BUT if we hit a reference
            // to a UI component that had already been created; we weren't doing that removal. There really should be no difference in
            // how we strip out parent actions from recursion based on whether or not we hit the primary or non-primary reference to
            // a UI component. I went through a lot of menu to see if anything got broken; and it all looks OK...but watch out for this one!
            Object oldObj = alreadyChecked.get(kids[j]);
            if (oldObj instanceof ZPseudoComp)
            {
              rv = true;
              // Update the action set for this UI kid
              ZPseudoComp theComp = (ZPseudoComp) oldObj;
              if (theComp.parentActions == null)
                theComp.parentActions = new java.util.HashSet(currParentActions);
              else
                theComp.parentActions.addAll(currParentActions);
              // Update us in the mapping
              for (int k = 0; k < currParentActions.size(); k++)
              {
                java.util.ArrayList oldVec = (java.util.ArrayList) alreadyChecked.get(currParentActions.get(k));
                if (oldVec == null)
                {
                  alreadyChecked.put(currParentActions.get(k), oldVec = Pooler.getPooledArrayList());
                  oldVec.add(theComp);
                }
                else if (!oldVec.contains(theComp))
                  oldVec.add(theComp);
              }
            }
            else if (oldObj instanceof java.util.ArrayList)
            {
              rv = true;
              java.util.ArrayList compVec = (java.util.ArrayList) oldObj;
              for (int k = 0; k < compVec.size(); k++)
              {
                ZPseudoComp theComp = (ZPseudoComp) compVec.get(k);
                if (theComp.parentActions == null)
                  theComp.parentActions = new java.util.HashSet(currParentActions);
                else
                  theComp.parentActions.addAll(currParentActions);
              }
              // Update us in the mapping
              for (int k = 0; k < currParentActions.size(); k++)
              {
                java.util.ArrayList oldVec = (java.util.ArrayList) alreadyChecked.get(currParentActions.get(k));
                if (oldVec == null)
                {
                  alreadyChecked.put(currParentActions.get(k), oldVec = Pooler.getPooledArrayList());
                  oldVec.addAll(compVec);
                }
                else if (oldVec != compVec) // don't do silly things
                {
                  for (int m = 0; m < compVec.size(); m++)
                  {
                    Object elem = compVec.get(m);
                    if (!oldVec.contains(elem))
                      oldVec.add(elem);
                  }
                }
              }
            }
            continue;
          }
          rv |= addChildrenFromWidgetChain(kids[j], widgetIndex, alreadyChecked,
              currParentActions);
        }
        if (rv)
        {
          // Done with this chain so we can pull this stuff out of currParentActions up until us
          while (currParentActions.remove(currParentActions.size() - 1) != currCheck);
        }
        if (createdParentActions)
          Pooler.returnPooledArrayList(currParentActions);
      }
    }
    return rv;
  }

  protected Widget getWidgetPropertyOwnerFromWidgetChain(byte propName, Widget defaultWidg, java.util.ArrayList backupWidgs)
  {
    if (defaultWidg != null && defaultWidg.hasProperty(propName))
      return defaultWidg;
    for (int i = 0; backupWidgs != null && i < backupWidgs.size(); i++)
    {
      Widget w = (Widget) backupWidgs.get(i);
      if (w.hasProperty(propName))
        return w;
    }
    return null;
  }

  protected String getStringPropertyFromWidgetChain(byte propName, Widget defaultWidg, java.util.ArrayList backupWidgs)
  {
    if (defaultWidg != null && defaultWidg.hasProperty(propName))
      return defaultWidg.getStringProperty(propName, null, this);
    for (int i = 0; backupWidgs != null && i < backupWidgs.size(); i++)
    {
      Widget w = (Widget) backupWidgs.get(i);
      if (w.hasProperty(propName))
        return w.getStringProperty(propName, null, this);
    }
    return "";
  }

  protected java.awt.Color getColorPropertyFromWidgetChain(byte propName, Widget defaultWidg, java.util.ArrayList backupWidgs)
  {
    if (defaultWidg != null && defaultWidg.hasProperty(propName))
      return defaultWidg.getColorProperty(propName, null, this);
    for (int i = 0; backupWidgs != null && i < backupWidgs.size(); i++)
    {
      Widget w = (Widget) backupWidgs.get(i);
      if (w.hasProperty(propName))
        return w.getColorProperty(propName, null, this);
    }
    return null;
  }

  protected int getIntPropertyFromWidgetChain(byte propName, Widget defaultWidg, java.util.ArrayList backupWidgs, int defaultVal)
  {
    if (defaultWidg != null && defaultWidg.hasProperty(propName))
      return defaultWidg.getIntProperty(propName, defaultVal, null, this);
    for (int i = 0; backupWidgs != null && i < backupWidgs.size(); i++)
    {
      Widget w = (Widget) backupWidgs.get(i);
      if (w.hasProperty(propName))
        return w.getIntProperty(propName, defaultVal, null, this);
    }
    return defaultVal;
  }

  protected Widget getWidgetChildFromWidgetChain(byte searchType, Widget defaultWidg, java.util.ArrayList backupWidgs)
  {
    Widget[] resKids;
    if (defaultWidg != null)
    {
      resKids = defaultWidg.contents();
      for (int i = 0; i < resKids.length; i++)
        if (resKids[i].isType(searchType))
          return resKids[i];
    }
    if (widgType != Widget.PANEL) // Panels don't use recursive Widget themes
    {
      for (int i = 0; backupWidgs != null && i < backupWidgs.size(); i++)
      {
        Widget w = (Widget) backupWidgs.get(i);
        resKids = w.contents();
        for (int j = 0; j < resKids.length; j++)
          if (resKids[j].isType(searchType))
            return resKids[j];
      }
    }
    return null;
  }

  protected void reloadAttributeContext()
  {
    loadAttributeContext();
    for (int i = 0; i < numKids; i++)
    {
      kids[i].reloadAttributeContext();
    }
  }

  protected void unfreshAttributeContext()
  {
    freshlyLoadedContext = false;
    for (int i = 0; i < numKids; i++)
    {
      kids[i].unfreshAttributeContext();
    }
  }
  protected void clearRecursiveChildContexts(Catbert.Context parentContext)
  {
    // NARFLEX 9/10/9 - I had disabled the focus clearing as an optimization; but then I came across a case where
    // refreshing the data in a table when travelling down a hierarchy wasn't properly refreshing related components
    // that were focus listeners; so this does need to be there.
    if (doesHierarchyHaveFocus())
    {
      ZPseudoComp topCop = getTopPseudoParent();
      if (topCop != null)
        topCop.setFocus(null);
    }
    clearRecursiveChildContexts2(parentContext);
  }
  protected void clearRecursiveChildContexts2(Catbert.Context parentContext)
  {
    focusTargetRect = null;  // this also needs to be reset when we re-use components....it'll work here for now
    if (effectTrackerMap != null)
    {
      effectTrackerMap.clear();
    }
    // NARFLEX - 10/29/09 - A nice optimization is to NOT reallocate the context below. BUT this causes a problem
    // for OptionsMenus and Fork'd threads who may be sharing that context and reliant upon the variables set for the specific
    // table cell when they were launched....weird things could happen like the MediaFile an OptionsMenu is for being changed
    // because the table data got refreshed behind it.
    relatedContext = new Catbert.Context(parentContext);
    for (int i = 0; i < numKids; i++)
    {
      kids[i].clearRecursiveChildContexts2(relatedContext);
    }
  }

  protected void loadAttributeContext()
  {
    // By doing expression evaluation, we can use attributes as a way of setting variables at
    // certain levels in the UI hierarchy that we can reuse
    if (attWidgList == null)
    {
      java.util.ArrayList attVec = new java.util.ArrayList();
      Widget[] widgList;
      if (containerTheme != null)
      {
        widgList = containerTheme.contents();
        for (int i = 0; i < widgList.length; i++)
          if (widgList[i].type() == Widget.ATTRIBUTE)
            attVec.add(widgList[i]);
      }
      if (currTheme != null)
      {
        widgList = currTheme.contents();
        for (int i = 0; i < widgList.length; i++)
          if (widgList[i].type() == Widget.ATTRIBUTE)
            attVec.add(widgList[i]);
      }
      widgList = widg.contents();
      for (int i = 0; i < widgList.length; i++)
        if (widgList[i].type() == Widget.ATTRIBUTE)
          attVec.add(widgList[i]);
      attWidgList = (Widget[]) attVec.toArray(new Widget[0]);
    }
    loadAttributeContext(attWidgList);
    freshlyLoadedContext = true;
  }
  private void loadAttributeContext(Widget[] attKids)
  {
    for (int i = 0; i < attKids.length; i++)
    {
      try
      {
        relatedContext.setLocal(attKids[i].getName(),
            Catbert.evaluateExpression(attKids[i].getStringProperty(Widget.VALUE, null, this),
                relatedContext, this, attKids[i]));
      }
      catch (Exception e)
      {
        System.out.println("Error evaluating expression for attribute: " + attKids[i] + " of " + e);
      }
    }
  }

  protected static boolean testBranchValue(Object condRes, Object branchRes)
  {
    if (condRes == branchRes) return true;
    if (condRes == null || branchRes == null) return false;
    if (condRes.equals(branchRes)) return true;
    // DBObjects must be the same actual object handle...String comparison shouldn't affect equality testing and this
    // also helps with performance
    if (condRes instanceof sage.DBObject && branchRes instanceof sage.DBObject) return false;
    return branchRes.toString().equals(condRes.toString());
    /*
     * I CAN'T DECIDE IF THIS IS WRONG OR NOT SINCE WE'RE VIOLATING TYPING RULES FOR EQUALS
     * BUT THAT'S SOMETHING I WANT TO DO AT LEAST FOR PRIMITIVES....CapDevs COMES TO MIND
     * WHY I WANT IT FOR OBJECTS
     */
    /*if ((condRes instanceof String || condRes instanceof Number || condRes instanceof Boolean ||
			condRes instanceof Character) && (branchRes instanceof String || branchRes instanceof Number ||
			branchRes instanceof Boolean || branchRes instanceof Character))
		{
		}
		else
			return false;*/
  }

  protected void evaluateTree(boolean doComps, boolean doData)
  {
    // Reset conditonal cache value upon tree evaluation, both have the potential to change
    // state on the same triggers
    freshlyLoadedContext = false;

    // Flag our layout properties as bad because this can potentially change them
    abortedLayoutPropLoad = true;

    // 10/29/03 added || focusListener
    // The if GetFocusContext() protects us from evaluating when there is no focus,
    // and this will allow us to do the extra evaluation after we do have the focus.
    // One of the old assumptions we made was that focus changes only affected how the UI
    // looked, not how it was structured. We had it fixed for navigation (simple focus changes),
    // but it was still wrong for when the Menu was loaded. Now it should be OK.
    if (!evaluate(doComps/* || focusListener*/, doData))
    {
      // We failed the UI conditional testing so do the focus check
      // clears anything underneath us that has the focus since it can't be shown anymore
      if (doesHierarchyHaveFocus())
      {
        ZPseudoComp topParent = getTopPseudoParent();
        topParent.setFocus(null);
      }
    }
    //		for (int i = 0; i < numKids; i++)
    //		{
    //			kids[i].evaluateTree(doComps, doData);
    //		}
  }

  public java.awt.geom.Rectangle2D.Float getPreferredSize(float availableWidth, float availableHeight,
      float parentWidth, float parentHeight, int depth)
  {
    //System.out.println("ZPseudoComp getPrefSize " + widg.getName() + " " + this + " availW=" + availableWidth + " availH=" + availableHeight +
    //	" pw=" + parentWidth + " ph=" + parentHeight);
    float adjustedAvailableHeight = availableHeight;
    float adjustedAvailableWidth = availableWidth;
    if (scrolling != 0)
    {
      if ((scrolling & ZDataTable.VERTICAL_DIMENSION) != 0)
        adjustedAvailableHeight = Float.MAX_VALUE/2;
      if ((scrolling & ZDataTable.HORIZONTAL_DIMENSION) != 0)
        adjustedAvailableWidth = Float.MAX_VALUE/2;
    }
    java.awt.geom.Rectangle2D.Float rv;
    if (Math.abs(adjustedAvailableWidth - cachedPrefParentWidth) < SIZE_FLOAT_EPSILON &&
        Math.abs(adjustedAvailableHeight - cachedPrefParentHeight) < SIZE_FLOAT_EPSILON)
    {
      rv = new java.awt.geom.Rectangle2D.Float();
      rv.setFrame(prefSize);
      return rv;
    }
    float childAreaWidth = adjustedAvailableWidth;
    float childAreaHeight = adjustedAvailableHeight;
    if ((validLayoutBits & FIXEDW_LAYOUT) != 0)
      childAreaWidth = fixedWidth;
    else if ((validLayoutBits & FILLX_LAYOUT) != 0)
      childAreaWidth = Math.min(availableWidth, parentWidth*fillX);
    if ((validLayoutBits & FIXEDH_LAYOUT) != 0)
      childAreaHeight = fixedHeight;
    else if ((validLayoutBits & FILLY_LAYOUT) != 0)
      childAreaHeight = Math.min(availableHeight, parentHeight*fillY);
    if (reality.isIntegerPixels())
    {
      childAreaWidth = (int)(childAreaWidth);
      childAreaHeight = (int)(childAreaHeight);
    }
    childAreaWidth -= insets.left + insets.right;
    childAreaHeight -= insets.top + insets.bottom;

    boolean dontCache = false;

    String layoutMode = propWidg.getStringProperty(Widget.LAYOUT, null, this);
    if ("SquareGrid".equals(layoutMode))
      rv = new java.awt.geom.Rectangle2D.Float(0, 0, availableWidth, availableHeight);
    else if (widgType == Widget.TABLECOMPONENT)
    {
      if (parent instanceof ZDataTable)
      {
        ZDataTable tabParent = (ZDataTable) parent;
        boolean freeform = tabParent.isFreeformTable();
        if (tabParent.dimensions == ZDataTable.HORIZONTAL_DIMENSION &&
            tabParent.isFirstHPage() && tabParent.isLastHPage() && tabParent.numRowsPerPage == 1)
        {
          rv = new java.awt.geom.Rectangle2D.Float();
          if (childAreaWidth > 0 && childAreaHeight > 0)
          {
            float maxKidWidth = 0;
            int numPassedKids = 0;
            for (int i = 0; i < numKids; i++)
            {
              ZComp currKid = kids[i];
              if (!currKid.backgroundComponent && currKid.passesConditional())
              {
                java.awt.geom.Rectangle2D.Float currPref = currKid.getPreferredSize(childAreaWidth,
                    childAreaHeight, childAreaWidth, childAreaHeight, depth + 1);
                rv.height = Math.max(rv.height, currPref.height);
                if (freeform)
                  rv.width += currPref.width;
                else
                  maxKidWidth = Math.max(maxKidWidth, currPref.width);
                numPassedKids++;
              }
            }
            if (freeform)
              rv.width += insets.left + insets.right + Math.max(0, (numPassedKids - 1))*padX;
            else
              rv.width = insets.left + insets.right + Math.max(0, (numPassedKids - 1))*padX +
              numPassedKids*maxKidWidth;
            rv.height += insets.top + insets.bottom;
          }
        }
        else if (tabParent.dimensions == ZDataTable.VERTICAL_DIMENSION &&
            tabParent.isFirstVPage() && tabParent.isLastVPage() && tabParent.numColsPerPage == 1)
        {
          rv = new java.awt.geom.Rectangle2D.Float();
          if (childAreaWidth > 0 && childAreaHeight > 0)
          {
            float maxKidHeight = 0;
            int numPassedKids = 0;
            for (int i = 0; i < numKids; i++)
            {
              ZComp currKid = kids[i];
              if (!currKid.backgroundComponent && currKid.passesConditional())
              {
                java.awt.geom.Rectangle2D.Float currPref = currKid.getPreferredSize(childAreaWidth,
                    childAreaHeight, childAreaWidth, childAreaHeight, depth + 1);
                rv.width = Math.max(rv.width, currPref.width);
                if (freeform)
                  rv.height += currPref.height;
                else
                  maxKidHeight = Math.max(maxKidHeight, currPref.height);
                numPassedKids++;
              }
            }
            if (freeform)
              rv.height += insets.top + insets.bottom + Math.max(0, (numPassedKids - 1))*padY;
            else
              rv.height = insets.top + insets.bottom + Math.max(0, (numPassedKids - 1))*padY +
              numPassedKids*maxKidHeight;
            rv.width += insets.left + insets.right;
          }
        }
        else
          rv = new java.awt.geom.Rectangle2D.Float(0, 0, availableWidth, availableHeight);
      }
      else
        rv = new java.awt.geom.Rectangle2D.Float();
    }
    else if ("Horizontal".equals(layoutMode) || "HorizontalReverse".equals(layoutMode) ||
        "HorizontalFill".equals(layoutMode))
    {
      rv = new java.awt.geom.Rectangle2D.Float();
      if (childAreaWidth > 0 && childAreaHeight > 0)
      {
        float orgChildAreaWidth = childAreaWidth;
        float orgChildAreaHeight = childAreaHeight;
        boolean placedPrior = false;
        for (int i = 0; i < numKids; i++)
        {
          ZComp currKid = kids[i];
          if (!currKid.backgroundComponent && currKid.passesConditional())
          {
            if (placedPrior)
            {
              rv.width += padX;
              childAreaWidth -= padX;
            }
            java.awt.geom.Rectangle2D.Float currPref = currKid.getPreferredSize(childAreaWidth,
                childAreaHeight, orgChildAreaWidth, orgChildAreaHeight, depth + 1);
            rv.height = Math.max(rv.height, currPref.height);
            rv.width += currPref.width;
            childAreaWidth -= currPref.width;
            placedPrior = currPref.width != 0;
          }
        }
        rv.width += insets.left + insets.right;
        rv.height += insets.top + insets.bottom;
      }
    }
    else if ("HorizontalGrid".equals(layoutMode))
    {
      rv = new java.awt.geom.Rectangle2D.Float();
      if (childAreaWidth > 0 && childAreaHeight > 0)
      {
        float orgChildAreaWidth = childAreaWidth;
        float orgChildAreaHeight = childAreaHeight;
        float maxKidWidth = 0;
        int numPassedKids = 0;
        for (int i = 0; i < numKids; i++)
        {
          if (!kids[i].backgroundComponent && kids[i].passesConditional())
            numPassedKids++;
        }
        if (numPassedKids > 0)
          childAreaWidth = (childAreaWidth - (numPassedKids - 1)*padX)/numPassedKids;
        for (int i = 0; i < numKids; i++)
        {
          ZComp currKid = kids[i];
          if (!currKid.backgroundComponent && currKid.passesConditional())
          {
            java.awt.geom.Rectangle2D.Float currPref = currKid.getPreferredSize(childAreaWidth,
                childAreaHeight, orgChildAreaWidth, orgChildAreaHeight, depth + 1);
            rv.height = Math.max(rv.height, currPref.height);
            maxKidWidth = Math.max(maxKidWidth, currPref.width);
          }
        }
        rv.width = insets.left + insets.right + Math.max(0, (numPassedKids - 1))*padX +
            numPassedKids*maxKidWidth;
        rv.height += insets.top + insets.bottom;
      }
    }
    else if ("Vertical".equals(layoutMode) || "VerticalReverse".equals(layoutMode) ||
        "VerticalFill".equals(layoutMode))
    {
      rv = new java.awt.geom.Rectangle2D.Float();
      if (childAreaWidth > 0 && childAreaHeight > 0)
      {
        float orgChildAreaWidth = childAreaWidth;
        float orgChildAreaHeight = childAreaHeight;
        boolean placedPrior = false;
        for (int i = 0; i < numKids; i++)
        {
          ZComp currKid = kids[i];
          if (!currKid.backgroundComponent && currKid.passesConditional())
          {
            if (placedPrior)
            {
              rv.height += padY;
              childAreaHeight -= padY;
            }
            java.awt.geom.Rectangle2D.Float currPref = currKid.getPreferredSize(childAreaWidth,
                childAreaHeight, orgChildAreaWidth, orgChildAreaHeight, depth + 1);
            rv.width = Math.max(rv.width, currPref.width);
            rv.height += currPref.height;
            childAreaHeight -= currPref.height;
            placedPrior = currPref.height != 0;
          }
        }
        rv.height += insets.top + insets.bottom;
        rv.width += insets.left + insets.right;
      }
    }
    else if ("VerticalGrid".equals(layoutMode))
    {
      rv = new java.awt.geom.Rectangle2D.Float();
      if (childAreaWidth > 0 && childAreaHeight > 0)
      {
        float orgChildAreaWidth = childAreaWidth;
        float orgChildAreaHeight = childAreaHeight;
        float maxKidHeight = 0;
        int numPassedKids = 0;
        for (int i = 0; i < numKids; i++)
        {
          if (!kids[i].backgroundComponent && kids[i].passesConditional())
            numPassedKids++;
        }
        if (numPassedKids > 0)
          childAreaHeight = (childAreaHeight - (numPassedKids - 1)*padY)/numPassedKids;
        for (int i = 0; i < numKids; i++)
        {
          ZComp currKid = kids[i];
          if (!currKid.backgroundComponent && currKid.passesConditional())
          {
            java.awt.geom.Rectangle2D.Float currPref = currKid.getPreferredSize(childAreaWidth,
                childAreaHeight, orgChildAreaWidth, orgChildAreaHeight, depth + 1);
            rv.width = Math.max(rv.width, currPref.width);
            maxKidHeight = Math.max(maxKidHeight, currPref.height);
          }
        }
        rv.height = insets.top + insets.bottom + Math.max(0, (numPassedKids - 1))*padY +
            numPassedKids*maxKidHeight;
        rv.width += insets.left + insets.right;
      }
    }
    else if ("Passive".equals(layoutMode))
    {
      rv = new java.awt.geom.Rectangle2D.Float();
      if (depth == 0)
      {
        for (int i = 0; i < numKids; i++)
        {
          ZComp currKid = kids[i];
          if (!currKid.backgroundComponent && currKid.passesConditional())
          {
            java.awt.geom.Rectangle2D.Float currPref = currKid.getPreferredSize(childAreaWidth,
                childAreaHeight, childAreaWidth, childAreaHeight, depth + 1);
            rv.width = Math.max(rv.width, currPref.width);
            rv.height = Math.max(rv.height, currPref.height);
          }
        }
      }
      else
        dontCache = true;
      if (childAreaWidth > 0 && childAreaHeight > 0)
      {
        rv.width += insets.left + insets.right;
        rv.height += insets.top + insets.bottom;
      }
    }
    else if (numKids > 0)
    {
      rv = new java.awt.geom.Rectangle2D.Float();
      if (childAreaWidth > 0 && childAreaHeight > 0)
      {
        for (int i = 0; i < numKids; i++)
        {
          ZComp currKid = kids[i];
          if (!currKid.backgroundComponent && currKid.passesConditional())
          {
            java.awt.geom.Rectangle2D.Float currPref = currKid.getPreferredSize(childAreaWidth,
                childAreaHeight, childAreaWidth, childAreaHeight, depth + 1);
            if (currKid instanceof ZPseudoComp && (((ZPseudoComp)currKid).validLayoutBits & ABSOLUTEX_LAYOUT) != 0)
              rv.width = Math.max(rv.width, currPref.width + ((ZPseudoComp)currKid).absoluteX);
            else
              rv.width = Math.max(rv.width, currPref.width);
            if (currKid instanceof ZPseudoComp && (((ZPseudoComp)currKid).validLayoutBits & ABSOLUTEY_LAYOUT) != 0)
              rv.height = Math.max(rv.height, currPref.height + ((ZPseudoComp)currKid).absoluteY);
            else
              rv.height = Math.max(rv.height, currPref.height);
          }
        }
        rv.width += insets.left + insets.right;
        rv.height += insets.top + insets.bottom;
      }
    }
    else
      rv = new java.awt.geom.Rectangle2D.Float(0, 0, 0, 0);
    if ((validLayoutBits & FIXEDW_LAYOUT) != 0)
      rv.width = fixedWidth;
    else if ((validLayoutBits & FILLX_LAYOUT) != 0)
      rv.width = Math.min(availableWidth, parentWidth*fillX);
    if ((validLayoutBits & FIXEDH_LAYOUT) != 0)
      rv.height = fixedHeight;
    else if ((validLayoutBits & FILLY_LAYOUT) != 0)
      rv.height = Math.min(availableHeight, parentHeight*fillY);
    if (reality.isIntegerPixels())
    {
      rv.width = (int)(rv.width);
      rv.height = (int)(rv.height);
    }
    prefSize.setFrame(rv);
    if (dontCache)
    {
      cachedPrefParentWidth = cachedPrefParentHeight = -1;
    }
    else
    {
      cachedPrefParentWidth = adjustedAvailableWidth;
      cachedPrefParentHeight = adjustedAvailableHeight;
    }
    //System.out.println("ZPseudoComp getPrefSize " + widg.getName() + " " + this + " returning: " + rv);
    return rv;
  }

  private long lastLayoutLastCached;
  private void loadLayoutProps()
  {
    float scaledFullHeight = reality.getRoot().getHeight() * uiMgr.getOverscanScaleHeight();
    float scaledFullWidth = reality.getRoot().getWidth() * uiMgr.getOverscanScaleWidth();
    int baseScalingHeight = 0;
    int baseScalingWidth = 0;
    if (uiMgr.isXBMCCompatible())
    {
      baseScalingHeight = reality.getUIMgr().getInt("ui/scaling_insets_base_height", 480);
      baseScalingWidth = reality.getUIMgr().getInt("ui/scaling_insets_base_width", 720);
    }
    boolean changeProps = false;
    float oldf;
    int oldi;
    if (lastLayoutLastCached == 0 || (lastLayoutLastCached < uiMgr.getModuleGroup().lastModified()))
    {
      lastLayoutLastCached = uiMgr.getModuleGroup().lastModified();
      changeProps = true;
    }
    validLayoutBits = 0;
    Number n = propWidg.getNumericProperty(Widget.ANCHOR_X, null, this);
    oldf = anchorX;
    oldi = absoluteX;
    anchorX = absoluteX = -1;
    int orgAbsX = 0;
    int orgAbsY = 0;
    if (n != null)
    {
      if (n instanceof Float)
      {
        anchorX = n.floatValue();
        validLayoutBits = validLayoutBits | ANCHORX_LAYOUT;
      }
      else
      {
        orgAbsX = absoluteX = n.intValue();
        validLayoutBits = validLayoutBits | ABSOLUTEX_LAYOUT;
        if (uiMgr.isXBMCCompatible())
          absoluteX = Math.round(absoluteX * scaledFullWidth / baseScalingWidth);
      }
    }
    changeProps |= oldf != anchorX || oldi != absoluteX;
    oldf = anchorY;
    oldi = absoluteY;
    n = propWidg.getNumericProperty(Widget.ANCHOR_Y, null, this);
    anchorY = absoluteY = -1;
    if (n != null)
    {
      if (n instanceof Float)
      {
        anchorY = n.floatValue();
        validLayoutBits = validLayoutBits | ANCHORY_LAYOUT;
      }
      else
      {
        orgAbsY = absoluteY = n.intValue();
        validLayoutBits = validLayoutBits | ABSOLUTEY_LAYOUT;
        if (uiMgr.isXBMCCompatible())
          absoluteY = Math.round(absoluteY * scaledFullHeight / baseScalingHeight);
      }
    }
    changeProps |= oldf != anchorY || oldi != absoluteY;
    oldf = fillX;
    oldi = fixedWidth;
    n = propWidg.getNumericProperty(Widget.FIXED_WIDTH, null, this);
    fillX = fixedWidth = -1;
    if (n != null)
    {
      if (n instanceof Float)
      {
        fillX = n.floatValue();
        validLayoutBits = validLayoutBits | FILLX_LAYOUT;
      }
      else
      {
        fixedWidth = n.intValue();
        validLayoutBits = validLayoutBits | FIXEDW_LAYOUT;
        if (uiMgr.isXBMCCompatible())
          fixedWidth = Math.round((fixedWidth + orgAbsX) * scaledFullWidth / baseScalingWidth) -
          (((validLayoutBits & ABSOLUTEX_LAYOUT) != 0) ? absoluteX : 0);
      }
    }
    changeProps |= oldf != fillX || oldi != fixedWidth;
    oldf = fillY;
    oldi = fixedHeight;
    n = propWidg.getNumericProperty(Widget.FIXED_HEIGHT, null, this);
    fillY = fixedHeight = -1;
    if (n != null)
    {
      if (n instanceof Float)
      {
        fillY = n.floatValue();
        validLayoutBits = validLayoutBits | FILLY_LAYOUT;
      }
      else
      {
        fixedHeight = n.intValue();
        validLayoutBits = validLayoutBits | FIXEDH_LAYOUT;
        if (uiMgr.isXBMCCompatible())
          fixedHeight = Math.round((fixedHeight + orgAbsY) * scaledFullHeight / baseScalingHeight) -
          (((validLayoutBits & ABSOLUTEY_LAYOUT) != 0) ? absoluteY : 0);
      }
    }
    changeProps |= oldf != fillY || oldi != fixedHeight;
    oldf = anchorPointX;
    anchorPointX = propWidg.getFloatProperty(Widget.ANCHOR_POINT_X, -1, null, this);
    changeProps |= oldf != anchorPointX;
    oldf = anchorPointY;
    anchorPointY = propWidg.getFloatProperty(Widget.ANCHOR_POINT_Y, -1, null, this);
    changeProps |= oldf != anchorPointY;
    // Relative padding are relative to the UI size, not the component size
    oldf = padX;
    n = propWidg.getNumericProperty(Widget.PAD_X, null, this);
    if (n != null)
    {
      if (n instanceof Float)
      {
        padX = n.floatValue() * scaledFullWidth;
        if (reality.isIntegerPixels())
          padX = (int)padX;
      }
      else
      {
        padX = n.intValue();
        if (uiMgr.isXBMCCompatible())
          padX = Math.round(padX * scaledFullWidth / baseScalingWidth);
      }
    }
    else
      padX = 0;
    changeProps |= oldf != padX;
    oldf = padY;
    n = propWidg.getNumericProperty(Widget.PAD_Y, null, this);
    if (n != null)
    {
      if (n instanceof Float)
      {
        padY = n.floatValue() * scaledFullHeight;
        if (reality.isIntegerPixels())
          padY = (int)padY;
      }
      else
      {
        padY = n.intValue();
        if (uiMgr.isXBMCCompatible())
          padY = Math.round(padY * scaledFullHeight / baseScalingHeight);
      }
    }
    else
      padY = 0;
    changeProps |= oldf != padY;

    if (widgType == Widget.IMAGE && numKids > 0)
    {
      ZImage newImage = (ZImage)kids[0];
      newImage.setHAlignment(propWidg.getFloatProperty(Widget.HALIGNMENT, 0.5f, null, this));
      newImage.setVAlignment(propWidg.getFloatProperty(Widget.VALIGNMENT, 0.5f, null, this));
      if (propWidg.hasProperty(Widget.PRESSED_FILE))
        newImage.setPressedImage(MetaImage.getMetaImage(propWidg.getStringProperty(Widget.PRESSED_FILE, null, this), this));
      if (propWidg.hasProperty(Widget.HOVER_FILE))
        newImage.setHoverImage(MetaImage.getMetaImage(propWidg.getStringProperty(Widget.HOVER_FILE, null, this), this));
      if (propWidg.hasProperty(Widget.DIFFUSE_FILE))
      {
        String imgSrc = propWidg.getStringProperty(Widget.DIFFUSE_FILE, null, this);
        if (imgSrc != null && imgSrc.length() > 0)
          newImage.setDiffuseImage(MetaImage.getMetaImage(imgSrc, this));
        else
          newImage.setDiffuseImage(null);
      }
      if (propWidg.hasProperty(Widget.SCALE_DIFFUSE))
        newImage.setScaleDiffuse(propWidg.getBooleanProperty(Widget.SCALE_DIFFUSE, null, this));
      if (propWidg.hasProperty(Widget.DURATION))
        newImage.setCrossFadeDuration(propWidg.getIntProperty(Widget.DURATION, 0, null, this));
      if (propWidg.hasProperty(Widget.BACKGROUND_LOAD))
        newImage.setBgLoader(propWidg.getBooleanProperty(Widget.BACKGROUND_LOAD, null, this));
      if (propWidg.hasProperty(Widget.CROP_TO_FILL))
        newImage.setCropToFill(propWidg.getBooleanProperty(Widget.CROP_TO_FILL, null, this));
      newImage.setARMaximize(Catbert.evalBool(relatedContext.getLocal("ARMaximize")));
    }

    if (changeProps)
      cachedPrefParentWidth = cachedPrefParentHeight = -1;

    if (widgType == Widget.TEXTINPUT)
    {
      disableFocus = !(propWidg.hasProperty(Widget.FOCUSABLE_CONDITION) && propWidg.getBooleanProperty(Widget.FOCUSABLE_CONDITION, null, this));
    }
    else if (propWidg.hasProperty(Widget.FOCUSABLE_CONDITION))
    {
      disableFocus = !propWidg.getBooleanProperty(Widget.FOCUSABLE_CONDITION, null, this);
    }
    else
      disableFocus = false;

    hasAnimation = propWidg.hasProperty(Widget.ANIMATION);
    if (hasAnimation)
    {
      String animStr = propWidg.getProperty(Widget.ANIMATION);
      if (animStr.startsWith("Cache") || animStr.startsWith("Layer"))
      {
        surfaceCache = animStr.substring(5);
        hasAnimation = false;
      }
      else
      {
        java.util.StringTokenizer toker = new java.util.StringTokenizer(animStr, ", ");
        dynamicAnimation = false;
        // Check if this is just a dynamic string
        if (animStr.startsWith("="))
        {
          String evalStr = propWidg.getStringProperty(Widget.ANIMATION, null, this);
          toker = new java.util.StringTokenizer(evalStr, ", ");
          if (toker.countTokens() == 3)
            animStr = evalStr;
        }
        if (toker.countTokens() != 3 || animStr.startsWith("="))
          dynamicAnimation = true;
        else
        {
          long oldDelay = initAnimDelay;
          long oldFreq = animFreq;
          long oldDur = animDuration;
          try
          {
            initAnimDelay = Long.parseLong(toker.nextToken());
            animFreq = Long.parseLong(toker.nextToken());
            animDuration = Long.parseLong(toker.nextToken());
            // Reset the animation start/last times if we change any parameters so it
            // doesn't mess up our tracking and we basically restart the animation over.
            if (oldDelay != initAnimDelay || oldFreq != animFreq || oldDur != animDuration)
            {
              lastAnimTime = animStart = 0;
              reality.unregisterAnimation(this);
              registeredAnimation = false;
            }
          }
          catch (NumberFormatException nfe)
          {
            if (Sage.DBG) System.out.println("ERROR parsing dynamic animation properties: " + nfe + " for Widget:" + propWidg);
          }
        }
      }
    }
    else if (widgType == Widget.TEXTINPUT && !disableFocus)
    {
      PseudoMenu currUI = uiMgr.getCurrUI();
      if (currUI == null || !currUI.hasMultipleTextInputs() || isFocused())
      {
        // Blinking cursor animation
        hasAnimation = true;
        initAnimDelay = 0;
        animFreq = 500;
        animDuration = 0;
      }
      else
      {
        hasAnimation = false;
        animFreq = 0;
      }
    }
    if (surfaceCache == null && scrolling != 0)
    {
      surfaceCache = uiMgr.get("ui/animation/preferred_scrolling_surface", "Foreground");
    }

    diffuseRenderColor = propWidg.getIntColorProperty(Widget.FOREGROUND_COLOR, 0xFFFFFF, null, this);

    if (parent != null)
    {
      int oldZOffset = zOffset;
      zOffset = propWidg.getIntProperty(Widget.Z_OFFSET, 0, null, this);
      if (oldZOffset != zOffset)
      {
        // Changed z-ordering; resort z-ordered index in parent
        parent.rebuildZOrderCache();
      }
    }
  }

  private FloatInsets parseInsets()
  {
    FloatInsets baseInsets = (insets == null) ? new FloatInsets() : insets;
    if (widgType == Widget.MENU || (widgType == Widget.OPTIONSMENU && backgroundComponent))
    {
      float scanAdjustX = uiMgr.getOverscanOffsetX();
      float scanAdjustY = uiMgr.getOverscanOffsetY();
      float scanAdjustW = getWidthf() * (1.0f - uiMgr.getOverscanScaleWidth());
      float scanAdjustH = getHeightf() * (1.0f - uiMgr.getOverscanScaleHeight());
      if (reality.isIntegerPixels())
      {
        scanAdjustX = (int)(scanAdjustX);
        scanAdjustY = (int)(scanAdjustY);
        scanAdjustW = (float)Math.ceil(scanAdjustW);
        scanAdjustH = (float)Math.ceil(scanAdjustH);
      }
      if (widgType != Widget.MENU)
      {
        if (fillX != 1.0f)
          scanAdjustX = scanAdjustW = 0;
        if (fillY != 1.0f)
          scanAdjustY = scanAdjustH = 0;
      }
      baseInsets.set(scanAdjustY, scanAdjustX, scanAdjustH - scanAdjustY, scanAdjustW - scanAdjustX);
    }
    else
      baseInsets.set(0, 0, 0, 0);
    if (!propWidg.hasProperty(Widget.INSETS))
    {
      return insets = baseInsets;
    }
    // Relative insets are relative to the UI size, not the component size
    float rootHeight = reality.getRoot().getHeight() * uiMgr.getOverscanScaleHeight();
    float rootWidth = reality.getRoot().getWidth() * uiMgr.getOverscanScaleWidth();
    int baseScalingHeight = 0;
    int baseScalingWidth = 0;
    if (uiMgr.isXBMCCompatible())
    {
      baseScalingHeight = reality.getUIMgr().getInt("ui/scaling_insets_base_height", 480);
      baseScalingWidth = reality.getUIMgr().getInt("ui/scaling_insets_base_width", 720);
    }
    Number[] insetsNums = propWidg.getNumericArrayProperty(Widget.INSETS, null, this);
    if (insetsNums == null || insetsNums.length == 0)
    {
      return insets = baseInsets;
    }
    else if (insetsNums.length >= 4)
    {
      insets = baseInsets;
      Number n = insetsNums[0];
      if (n instanceof Float || n instanceof Double)
        insets.top += rootHeight * n.floatValue();
      else
      {
        if (uiMgr.isXBMCCompatible())
          insets.top += (n.intValue() * rootHeight / baseScalingHeight);
        else
          insets.top += n.intValue();
      }
      n = insetsNums[1];
      if (n instanceof Float || n instanceof Double)
        insets.left += rootWidth * n.floatValue();
      else
      {
        if (uiMgr.isXBMCCompatible())
          insets.left += (n.intValue() * rootWidth / baseScalingWidth);
        else
          insets.left += n.intValue();
      }
      n = insetsNums[2];
      if (n instanceof Float || n instanceof Double)
        insets.bottom += rootHeight * n.floatValue();
      else
      {
        if (uiMgr.isXBMCCompatible())
          insets.bottom += (n.intValue() * rootHeight / baseScalingHeight);
        else
          insets.bottom += n.intValue();
      }
      n = insetsNums[3];
      if (n instanceof Float || n instanceof Double)
        insets.right += rootWidth * n.floatValue();
      else
      {
        if (uiMgr.isXBMCCompatible())
          insets.right += (n.intValue() * rootWidth / baseScalingWidth);
        else
          insets.right += n.intValue();
      }
      if (reality.isIntegerPixels())
        insets.integerize();
      return insets;
    }
    else
    {
      Number n = insetsNums[0];
      if (n instanceof Float || n instanceof Double)
      {
        float perct = n.floatValue();
        insets.set(baseInsets.top + perct* rootHeight,
            baseInsets.left + perct*rootWidth,
            baseInsets.bottom + perct*rootHeight,
            baseInsets.right + perct*rootWidth);
      }
      else
      {
        int ins = n.intValue();
        if (uiMgr.isXBMCCompatible())
        {
          float insx = ins * rootWidth / baseScalingWidth;
          float insy = ins * rootHeight / baseScalingHeight;
          insets.set(baseInsets.top + insy, baseInsets.left + insx,
              baseInsets.bottom + insy, baseInsets.right + insx);
        }
        else
          insets.set(baseInsets.top + ins, baseInsets.left + ins,
              baseInsets.bottom + ins, baseInsets.right + ins);
      }
      if (reality.isIntegerPixels())
        insets.integerize();
      return insets;
    }
  }

  private boolean updateThemeFont()
  {
    if (!dynamicThemeFontFace && !dynamicThemeFontSize && !dynamicThemeFontStyle)
      return false;
    String newFace = themeFont.getName();
    int newSize = themeFont.getSize();
    int newStyle = themeFont.getStyle();

    if (dynamicThemeFontFace)
    {
      String fontFaceProp = getStringPropertyFromWidgetChain(Widget.FONT_FACE, currTheme, defaultThemes);
      if (fontFaceProp.length() > 0)
        newFace = fontFaceProp;
    }
    if (dynamicThemeFontSize)
    {
      int fontSizeProp = getIntPropertyFromWidgetChain(Widget.FONT_SIZE, currTheme, defaultThemes, -1);
      if (fontSizeProp > 0)
        newSize = fontSizeProp;
    }
    if (dynamicThemeFontStyle)
    {
      String fontStyleProp = getStringPropertyFromWidgetChain(Widget.FONT_STYLE, currTheme, defaultThemes);
      if (fontStyleProp.length() > 0)
        newStyle = WidgetMeta.getFontStyleForName(fontStyleProp);
    }
    if (newSize != themeFont.getSize() || newStyle != themeFont.getStyle() || !newFace.equals(themeFont.getName()))
    {
      themeFont = uiMgr.getCachedMetaFont(newFace, newStyle, newSize, uiMgr);
      return true;
    }
    return false;
  }

  protected void loadDynamicLayoutProps()
  {
    if (!passesConditional())
    {
      abortedLayoutPropLoad = true;
      return;
    }
    abortedLayoutPropLoad = false;
    if (layoutStartHook != null)
    {
      Catbert.processHookDirectly(layoutStartHook, null, uiMgr, this);
    }
    cachedPrefParentWidth = -1;
    cachedPrefParentHeight = -1;
    checkForFocus = true;
    loadLayoutProps();
    parseInsets();
    checkForFocus = false;
    boolean checkedThemeFont = false;
    boolean newThemeFont = false;
    for (int i = 0; i < numKids; i++)
    {
      ZComp currKid = kids[i];
      if (currKid instanceof ZPseudoComp && currKid.needsLayout())
      {
        ((ZPseudoComp) currKid).loadDynamicLayoutProps();
      }
      else if (currKid instanceof ZLabel && !(currKid instanceof ZCCLabel))
      {
        if (checkedThemeFont && !newThemeFont)
          continue;
        if (checkedThemeFont)
          ((ZLabel) currKid).setFont(themeFont);
        else
        {
          newThemeFont = updateThemeFont();
          checkedThemeFont = true;
          if (newThemeFont)
            ((ZLabel) currKid).setFont(themeFont);
        }
      }
    }
  }

  private boolean isComponentInVisibleMenu()
  {
    PseudoMenu currUI = uiMgr.getCurrUI();
    ZPseudoComp topPseudo = getTopPseudoParent();
    return !(currUI != null && currUI.getUI() != topPseudo &&
        (!(topPseudo.parent instanceof ZPseudoComp) || topPseudo.parent != currUI.getUI()));
  }

  public void animationCallback(long animationTime)
  {
    // If this menu is no longer being shown then disable this animation
    if (!isComponentInVisibleMenu())
    {
      reality.unregisterAnimation(this);
      registeredAnimation = false;
    }
    else if (hasAnimation && passesUpwardConditional())
    {
      boolean animateNow = false;
      if (dynamicAnimation)
      {
        animateNow = propWidg.getBooleanProperty(Widget.ANIMATION, null, this);
        if (animationTime != 0)
          reality.registerAnimation(this, 0);
      }
      else
      {
        if (animationTime - animStart >= initAnimDelay && (animDuration == 0 ||
            lastAnimTime <= animStart + initAnimDelay + animDuration))
        {
          if (animFreq == 0 || animationTime - lastAnimTime >= animFreq)
          {
            animateNow = true;
            // If we fell behind, catch us up. It's OK to drop frames in these animations.
            if (animationTime + animFreq <= Sage.eventTime())
              animationTime = Sage.eventTime();
            reality.registerAnimation(this, animationTime + animFreq);
          }
        }
      }
      if (animateNow)
      {
        lastAnimTime = animationTime;
        appendToDirty(true);

        // Added on 11/7/03 This is supposed to enable things like the current media time display.
        // So when things are animated, it doesn't just do the redraw, it recalculates the data also
        // which is what we'd expect it to do.
        if (widgType != Widget.TEXTINPUT) // don't do the re-eval for cursor animations
          evaluateTree(false, true);
      }
    }
    else
    {
      reality.unregisterAnimation(this);
      registeredAnimation = false;
    }
  }

  public void cleanup()
  {
    animStart = lastAnimTime = 0;
    cachedPrefParentHeight = cachedPrefParentWidth = -1;
    if (pendingAnimations != null && !pendingAnimations.isEmpty())
    {
      // Don't forget to start the animation; somebody has to do it!
      for (int i = 0; i < pendingAnimations.size(); i++)
      {
        ((RenderingOp)pendingAnimations.get(i)).anime.setup(getTrueBoundsf());
        reality.registerLostAnimationOp((RenderingOp)pendingAnimations.get(i));
      }
      pendingAnimations.clear();
    }
    if (isFocused() && uiMgr.areLayersEnabled())
    {
      // See if we have focus animation to deal with.
      ZPseudoComp focusAnimChild = findFocusAnimatorChild();
      if (focusAnimChild != null)
      {
        java.awt.geom.Rectangle2D.Float oldFocBounds = focusAnimChild.getTrueBoundsf();
        // If we didn't get our layout completed but got the focus anyways (which is possible) then
        // we may have no size which'll cause the focus anim to do an undersirable smooth animation.
        // NOTE: Is it undesirable?
        //				if (oldFocBounds.width > 0 && oldFocBounds.height > 0)
        {
          // We set this "Focus" animation operation in the top level parent who will then find the appopriately focused
          // child on the next rendering pass and move the animation operation into that one
          String focusAnim = "SmoothQuadratic";//uiMgr.get("ui/animation/focus_transition_animation", "MorphQuadratic");
          long focusAnimDur = uiMgr.getLong("ui/animation/focus_transition_duration", 200);
          if (focusAnimDur > 0)
            reality.registerLostFocusAnimationOp(new RenderingOp(focusAnimChild.surfaceCache,
                focusAnim,
                focusAnimDur, 0, focusAnimChild.getTrueBoundsf(), focusAnimChild.getBGAlpha(), false));
        }
      }
    }
    if ((widgType == Widget.VIDEO || hasVideoBackground()) && numKids > 1)
    {
      if (uiMgr.getVideoFrame().getRegisteredSubtitleComponent() == kids[1])
      {
        ((ZLabel) kids[1]).setText("");
        uiMgr.getVideoFrame().registerSubtitleComponent(null);
      }
      if (uiMgr.getVideoFrame().getRegisteredCCComponent() == kids[0])
      {
        ((ZCCLabel) kids[0]).setText("");
        uiMgr.getVideoFrame().registerCCComponent(null);
      }
    }
    super.cleanup();
  }

  private void checkForLayoutLoad(ZPseudoComp theKid)
  {
    if (theKid != null && theKid.abortedLayoutPropLoad)
    {
      theKid.loadDynamicLayoutProps();
    }
  }

  private boolean doNotRecurseLayoutNow;
  public void doLayoutNow()
  {
    if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.LAYOUT_UI, this, widg, null);

    if (hasAnimation && animStart == 0)
      lastAnimTime = animStart = Sage.eventTime();
    if (hasAnimation && !registeredAnimation && passesUpwardConditional())
    {
      if (dynamicAnimation)
      {
        reality.registerAnimation(this, 0);
      }
      else
      {
        // If we fell behind, catch us up. It's OK to drop frames in these animations.
        lastAnimTime = Sage.eventTime();
        reality.registerAnimation(this, lastAnimTime + animFreq + initAnimDelay);
      }
      registeredAnimation = true;
    }
    if (widgType == Widget.MENU)
    {
      // Load all of the dynamic layout info for everyone that needs it
      loadDynamicLayoutProps();
    }
    FloatInsets ins = insets;
    boolean videoBG = widgType == Widget.MENU && propWidg.getBooleanProperty(Widget.VIDEO_BACKGROUND, null, this);
    boolean skipFirst2Kids = false;
    if (videoBG || widgType == Widget.VIDEO)
    {
      if (numKids > 1 && kids[0] instanceof ZCCLabel && kids[1] instanceof ZCCLabel)
      {
        // Locate the subtitle component
        float subhpos = uiMgr.getFloat("subtitle/hpos", 0.5f);
        float subvpos = uiMgr.getFloat("subtitle/vpos", 0.95f);
        float subwidth = uiMgr.getFloat("subtitle/width", 0.7f);
        float subheight = uiMgr.getFloat("subtitle/height", 0.2f);
        subwidth = boundsf.width * subwidth;
        subheight = boundsf.height * subheight;
        kids[1].setBounds((boundsf.width - subwidth - ins.left - ins.right) * subhpos + ins.left,
            (boundsf.height - subheight - ins.top - ins.bottom) * subvpos + ins.top,
            subwidth, subheight);
        kids[1].doLayout();

        // Locate the CC component (80% of width/height and centered; ignore overscan)
        kids[0].setBounds(boundsf.width / 10, boundsf.height / 10, boundsf.width * 8 / 10, boundsf.height * 8 / 10);
        kids[0].doLayout();

        skipFirst2Kids = true;
      }
    }
    if (widgType == Widget.VIDEO)
    {
      return;
    }
    if (!videoBG && numKids == 0) return;

    if (widgType != Widget.PANEL && Widget.MENU != widgType &&
        Widget.TABLECOMPONENT != widgType && widgType != Widget.ITEM &&
        widgType != Widget.TABLE && Widget.OPTIONSMENU != widgType)
    {
      // We don't do layout if we're not a Panel. Just pass on down the call
      if (kids[0].backgroundComponent)
        kids[0].setBounds(0, 0, getWidthf(), getHeightf());
      else
        kids[0].setBounds(ins.left, ins.top, getWidthf() - ins.left - ins.right,
            getHeightf() - ins.top - ins.bottom);
      kids[0].doLayout();
      return;
    }
    String layoutMode = propWidg.getStringProperty(Widget.LAYOUT, null, this);
    boolean ifv=isFirstVPage(),ilv=isLastVPage(),ifh=isFirstHPage(),ilh=isLastHPage();
    float x, y;
    x = ins.left - scrollPosX;
    y = ins.top - scrollPosY;
    // 601 if (Widget.TABLECOMPONENT == widg.widgetType)
    if (widgType == Widget.TABLECOMPONENT)
    {
      ZDataTable tabParent = (ZDataTable) parent;
      int numCols, numRows;
      int numDataCols, numDataRows;
      float theW, theH;
      String tabSubType = propWidg.getStringProperty(Widget.TABLE_SUBCOMP, null, this);
      float totalTableWeightV = tabParent.getTotalTableWeightV();
      float totalTableWeightH = tabParent.getTotalTableWeightH();
      if (ZDataTable.COL_HEADER.equals(tabSubType))
      {
        numDataCols = numCols = tabParent.numColsPerPage;
        totalTableWeightV = numDataRows = numRows = 1;
      }
      else if (ZDataTable.ROW_HEADER.equals(tabSubType))
      {
        totalTableWeightH = numDataCols = numCols = 1;
        numDataRows = numRows = tabParent.numRowsPerPage;
      }
      else //if (ZDataTable.CELL.equals(tabSubType))
      {
        numCols = tabParent.numColsPerPage;
        numRows = tabParent.numRowsPerPage;
        numDataCols = (tabParent.columnData != null) ?
            Math.min(tabParent.numColsPerPage, tabParent.columnData.length) : tabParent.numColsPerPage;
        numDataRows = (tabParent.rowData != null) ?
            Math.min(tabParent.numRowsPerPage, tabParent.rowData.length) : tabParent.numRowsPerPage;
      }
      if (!ZDataTable.ENABLE_VAR_SIZE_TABLES)
      {
        totalTableWeightV = numRows;
        totalTableWeightH = numCols;
      }
      boolean adjustableWidth = ((validLayoutBits & FILLX_LAYOUT) == 0) && ((validLayoutBits & FIXEDW_LAYOUT) == 0) && tabParent.hSpan < tabParent.numColsPerPage &&
          tabParent.hSpan > 0;
      boolean adjustableHeight = ((validLayoutBits & FILLY_LAYOUT) == 0) && ((validLayoutBits & FIXEDH_LAYOUT) == 0) && tabParent.vSpan < tabParent.numRowsPerPage &&
          tabParent.vSpan > 0;
      if (adjustableWidth)
        theW = (boundsf.width - ins.left - ins.right -(tabParent.hSpan-1)*padX)/tabParent.hSpan;
      else
        theW = (boundsf.width - ins.left - ins.right -(numCols-1)*padX)/totalTableWeightH;
      if (adjustableHeight)
        theH = (boundsf.height - ins.top - ins.bottom - (tabParent.vSpan-1)*padY)/tabParent.vSpan;
      else
        theH = (boundsf.height - ins.top - ins.bottom - (numRows-1)*padY)/totalTableWeightV;
      boolean useScheduleTimes = Catbert.evalBool(relatedContext.safeLookup("UseAiringSchedule"));
      if (ZDataTable.CELL.equals(tabSubType) && tabParent.dimensions == ZDataTable.BOTH_DIMENSIONS &&
          (tabParent.hTime || tabParent.vTime))
      {
        float baseY = y;
        float baseX = x;
        float w, h;
        float fullW = boundsf.width - ins.left - ins.right;
        float fullH = boundsf.height - ins.top - ins.bottom;
        long timebase, fullDur;
        if (tabParent.hTime)
        {
          timebase = ((Long) tabParent.columnData[0]).longValue();
          fullDur = ((Long) tabParent.columnData[1]).longValue() - timebase;
        }
        else
        {
          timebase = ((Long) tabParent.rowData[0]).longValue();
          fullDur = ((Long) tabParent.rowData[1]).longValue() - timebase;
        }
        int lastIdx = -1;
        float[] weights = ZDataTable.ENABLE_VAR_SIZE_TABLES ? (tabParent.hTime ? tabParent.getAggRowWeights() : tabParent.getAggColWeights()) : null;
        for (int i = 0; i < numKids; i++)
        {
          ZPseudoComp kid = (ZPseudoComp) kids[i];
          checkForLayoutLoad(kid);
          /*if (kid instanceof ZPseudoComp && !((ZPseudoComp) kid).passesConditional())
  {
    kid.setBounds(0, 0, 0, 0);
  }
  else*/
          {
            Integer intObj = (Integer) kid.relatedContext.safeLookup("TableRow");
            int theRow = (intObj != null) ? (intObj.intValue() - 1) : 0;
            intObj = (Integer) kid.relatedContext.safeLookup("TableCol");
            int theCol = (intObj != null) ? (intObj.intValue() - 1) : 0;
            if (tabParent.hTime)
            {
              theRow -= tabParent.vunitIndex;
              if (theRow < 0 && ((tabParent.wrapping & ZDataTable.VERTICAL_DIMENSION) != 0))
                theRow += tabParent.vSpan;
              if (ZDataTable.ENABLE_VAR_SIZE_TABLES)
              {
                float myWeight = (theRow == weights.length - 1) ? (totalTableWeightV - weights[theRow]) : (weights[theRow + 1] - weights[theRow]);
                y = baseY + padY*theRow + theH * weights[theRow];
                h = theH * myWeight;
              }
              else
              {
                y = baseY + (theH + padY)*theRow;
                h = theH;
              }

              // Get the time bounding object (Airing only for now)
              Object kidDataObj = kid.relatedContext.safeLookup(widg.getName());
              if (kidDataObj instanceof Airing)
              {
                Airing cellAir = (Airing) kidDataObj;
                long cellStart, cellEnd;
                if (useScheduleTimes)
                {
                  cellStart = cellAir.getSchedulingStart();
                  cellEnd = cellAir.getSchedulingEnd();
                }
                else
                {
                  cellStart = cellAir.getStartTime();
                  cellEnd = cellAir.getEndTime();
                }
                if (cellEnd <= timebase || cellStart >= timebase + fullDur)
                {
                  kid.setBounds(0, 0, 0, 0);
                  kid.forceChildrenToBeValid();
                  continue;
                }
                if (cellStart <= timebase)
                  x = baseX;
                else
                  x = baseX + (((float)(cellStart - timebase))/fullDur)*fullW;
                float endX;
                if (cellEnd >= timebase + fullDur)
                  endX = baseX + fullW;
                else
                  endX = baseX + (((float)(cellEnd - timebase))/fullDur)*fullW;
               if (reality.isIntegerPixels())
                  w = endX - (int)x;
                else
                  w = endX - x;
              }
              else
              {
                x = baseX;
                w = theW;
              }
            }
            else
            {
              theCol -= tabParent.hunitIndex;
              if (theCol < 0 && ((tabParent.wrapping & ZDataTable.HORIZONTAL_DIMENSION) != 0))
                theCol += tabParent.hSpan;
              if (ZDataTable.ENABLE_VAR_SIZE_TABLES)
              {
                /*								x = baseX;
        Number weighty = (Number) kid.relatedContext.safeLookup("TableWeightH");
        float currWeight = (weighty == null) ? 1.0f : weighty.floatValue();
        w = theW * currWeight;
        if (lastIdx != theCol)
        {
          baseX += w + padX;
          lastIdx = theCol;
        }*/
                float myWeight = (theCol == weights.length - 1) ? (totalTableWeightH - weights[theCol]) : (weights[theCol + 1] - weights[theCol]);
                x = baseX + padX*theCol + theW * weights[theCol];
                w = theW * myWeight;
              }
              else
              {
                x = baseX + (theW + padX)*theCol;
                w = theW;
              }
              // Get the time bounding object (Airing only for now)
              Object kidDataObj = kid.relatedContext.safeLookup(widg.getName());
              if (kidDataObj instanceof Airing)
              {
                Airing cellAir = (Airing) kidDataObj;
                long cellStart, cellEnd;
                if (useScheduleTimes)
                {
                  cellStart = cellAir.getSchedulingStart();
                  cellEnd = cellAir.getSchedulingEnd();
                }
                else
                {
                  cellStart = cellAir.getStartTime();
                  cellEnd = cellAir.getEndTime();
                }
                if (cellStart <= timebase)
                  y = baseY;
                else
                  y = baseY + (((float)(cellStart - timebase))/fullDur)*fullH;
                float endY;
                if (cellEnd >= timebase + fullDur)
                  endY = baseY + fullH;
                else
                  endY = baseY + (((float)(cellEnd - timebase))/fullDur)*fullH;
               if (reality.isIntegerPixels())
                  h = endY - (int)y;
                else
                h = endY - y;
              }
              else
              {
                y = baseY;
                h = theH;
              }
            }
            kid.setBounds(x, y,	w, h);
            kid.doLayout();
          }
        }
      }
      else
      {
        for (int i = 0; i < numKids; i++)
        {
          ZPseudoComp kid = (ZPseudoComp) kids[i];
          if (!kid.passesConditional())
          {
            kid.vis = false;
            //kid.setBounds(0, 0, 0, 0);
            kid.forceChildrenToBeValid();
          }
          else
          {
            if (!kid.vis)
            {
              kid.appendToDirty(true);
              kid.vis = true;
            }
            checkForLayoutLoad(kid);
            if (tabParent.isFreeformTable())
            {
              // Base the current width/height of a cell on its preferred size; not even spacing for all components
              java.awt.geom.Rectangle2D.Float prefSize = kid.getPreferredSize(boundsf.width - ins.left - ins.right,
                  boundsf.height - ins.top - ins.bottom, boundsf.width, boundsf.height, 0);
              theW = prefSize.width;
              theH = prefSize.height;
            }
            float currWeightV = 1.0f;
            float currWeightH = 1.0f;
            if (ZDataTable.ENABLE_VAR_SIZE_TABLES)
            {
              Number weightyV = (Number) kid.relatedContext.safeLookup("TableWeightV");
              currWeightV = (weightyV == null) ? 1.0f : weightyV.floatValue();
              Number weightyH = (Number) kid.relatedContext.safeLookup("TableWeightH");
              currWeightH = (weightyH == null) ? 1.0f : weightyH.floatValue();
              kid.setBounds(x, y, theW * currWeightH, theH * currWeightV);
            }
            else
              kid.setBounds(x, y, theW, theH);
            if (tabParent.dimensions == ZDataTable.VERTICAL_DIMENSION)
            {
              if (((i + 1) % numDataCols) == 0)
              {
                y += theH*currWeightV + padY;
                x = ins.left;
              }
              else
              {
                x += theW*currWeightH + padX;
              }
            }
            else
            {
              if (((i + 1) % numDataRows) == 0)
              {
                x += theW*currWeightH + padX;
                y = ins.top;
              }
              else
              {
                y += theH*currWeightV + padY;
              }
            }
            kid.doLayout();
          }
        }
      }
    }
    else if ("SquareGrid".equals(layoutMode) || "VerticalGrid".equals(layoutMode) ||
        "HorizontalGrid".equals(layoutMode))
    {
      java.util.ArrayList goodKids = Pooler.getPooledArrayList();
      int bgKidsCount = 0;
      for (int i = 0; i < numKids; i++)
      {
        if (kids[i].passesConditional())
        {
          if (!kids[i].vis)
          {
            kids[i].vis = true;
            kids[i].appendToDirty(true);
          }
          if (kids[i].backgroundComponent)
            bgKidsCount++;
          goodKids.add(kids[i]);
          if (kids[i] instanceof ZPseudoComp)
            checkForLayoutLoad((ZPseudoComp) kids[i]);
        }
        else
        {
          //kids[i].setBounds(0, 0, 0, 0);
          kids[i].vis = false;
          kids[i].forceChildrenToBeValid();
        }
      }

      int numCols, numRows;
      boolean unboundWidth=false, unboundHeight=false;
      if ("SquareGrid".equals(layoutMode))
      {
        numCols = (int)(Math.sqrt(goodKids.size() - bgKidsCount));
        if (numCols == 0) numCols = 1;
        numRows = (goodKids.size() - bgKidsCount) / numCols;
        if (numRows * numCols < (goodKids.size() - bgKidsCount))
          numRows++;
      }
      else if ("HorizontalGrid".equals(layoutMode))
      {
        numCols = goodKids.size() - bgKidsCount;
        numRows = 1;
        if ((scrolling & ZDataTable.VERTICAL_DIMENSION) != 0)
          unboundHeight = true;
      }
      else //if ("VerticalGrid".equals(propWidg.getProperty(Widget.LAYOUT)))
      {
        numCols = 1;
        numRows = goodKids.size() - bgKidsCount;
        if ((scrolling & ZDataTable.HORIZONTAL_DIMENSION) != 0)
          unboundWidth = true;
      }
      if (numCols == 0) numCols = 1;
      if (numRows == 0) numRows = 1;
      float theW = (boundsf.width - ins.left - ins.right -(numCols-1)*padX)/numCols;
      float theH = (boundsf.height - ins.top - ins.bottom - (numRows-1)*padY)/numRows;
      if (unboundWidth)
        theW = Float.MAX_VALUE/2;
      if (unboundHeight)
        theH = Float.MAX_VALUE/2;
      scrollingWidth = scrollingHeight = 0;
      float orgTheW = theW;
      float orgTheH = theH;
      bgKidsCount = 0;
      for (int i = 0; i < goodKids.size(); i++)
      {
        ZComp kid = (ZComp) goodKids.get(i);
        java.awt.geom.Rectangle2D.Float kidRect = new java.awt.geom.Rectangle2D.Float(x, y, theW, theH);
        theW = orgTheW;
        theH = orgTheH;
        if (kid.backgroundComponent)
        {
          kidRect.setRect(0, 0, boundsf.width, boundsf.height);
          theW = boundsf.width;
          theH = boundsf.height;
          bgKidsCount++;
        }
        else if (unboundWidth || unboundHeight)
        {
          java.awt.geom.Rectangle2D.Float kidPref = kid.getPreferredSize(theW, theH, theW, theH, 0);
          if (unboundWidth)
            kidRect.width = Math.min(kidRect.width, kidPref.width);
          if (unboundHeight)
            kidRect.height = Math.min(kidRect.height, kidPref.height);
        }
        if (kid instanceof ZPseudoComp)
        {
          ZPseudoComp pseudoKid = (ZPseudoComp) kid;
          positionComponentFullByProps(pseudoKid, kidRect.x, kidRect.y, kidRect.width, kidRect.height, theW, theH);
        }
        else
          kid.setBounds(x, y,	theW, theH);
        if (!kid.backgroundComponent)
        {
          scrollingWidth = Math.max(scrollingWidth, kid.boundsf.x + kid.boundsf.width + ins.right +
              scrollPosX);
          scrollingHeight = Math.max(scrollingHeight, kid.boundsf.y + kid.boundsf.height + ins.bottom +
              scrollPosY);
          if (((i + 1 - bgKidsCount) % numRows) == 0)
          {
            x += theW + padX;
            y = ins.top;
          }
          else
          {
            y += theH + padY;
          }
        }
        kid.doLayout();
      }
      Pooler.returnPooledArrayList(goodKids);
    }
    else if ("Horizontal".equals(layoutMode) || "Vertical".equals(layoutMode) ||
        "HorizontalReverse".equals(layoutMode) || "VerticalReverse".equals(layoutMode) ||
        "HorizontalFill".equals(layoutMode) || "VeritcalFill".equals(layoutMode))
    {
      boolean horizLay = layoutMode.startsWith("Horizontal");
      boolean reverseLay = layoutMode.endsWith("Reverse");
      boolean fillLay = layoutMode.endsWith("Fill");
      boolean placedPrior = false;
      float individAlign = 0.5f;
      java.util.HashMap prealignKidBoundsMap = Pooler.getPooledHashMap();//new java.util.HashMap();
      if (horizLay && propWidg.hasProperty(Widget.VALIGNMENT))
        individAlign = propWidg.getFloatProperty(Widget.VALIGNMENT, 0.5f, null, this);
      else if (!horizLay && propWidg.hasProperty(Widget.HALIGNMENT))
        individAlign = propWidg.getFloatProperty(Widget.HALIGNMENT, 0.5f, null, this);
      java.awt.geom.Rectangle2D.Float kidSize = new java.awt.geom.Rectangle2D.Float();
      float layoutWidth = boundsf.width - ins.left - ins.right;
      float layoutHeight = boundsf.height - ins.top - ins.bottom;
      float orgLayoutWidth = layoutWidth;
      float orgLayoutHeight = layoutHeight;
      int numLayoutKids = 0;
      if ((scrolling & ZDataTable.HORIZONTAL_DIMENSION) != 0)
        layoutWidth = Float.MAX_VALUE/2;
      if ((scrolling & ZDataTable.VERTICAL_DIMENSION) != 0)
        layoutHeight = Float.MAX_VALUE/2;
      scrollingWidth = scrollingHeight = 0;
      if (reverseLay)
      {
        if (horizLay)
          x += layoutWidth;
        else
          y += layoutHeight;
      }
      for (int i = 0; i < numKids; i++)
      {
        ZComp kid = kids[i];
        ZPseudoComp pseudoKid = null;
        if (kid instanceof ZPseudoComp)
          pseudoKid = (ZPseudoComp) kid;
        if (pseudoKid != null && pseudoKid.backgroundComponent && pseudoKid.passesConditional())
        {
          checkForLayoutLoad(pseudoKid);
          positionComponentFullByProps(pseudoKid, 0, 0, boundsf.width, boundsf.height, boundsf.width, boundsf.height);
        }
        else if (pseudoKid == null || pseudoKid.passesConditional())
        {
          if (pseudoKid != null)
            checkForLayoutLoad(pseudoKid);
          numLayoutKids++;
          if (horizLay)
          {
            if (placedPrior)
            {
              if (reverseLay)
                x -= padX;
              else
                x += padX;
              layoutWidth -= padX;
            }
            kidSize.setFrame(kid.getPreferredSize(layoutWidth, layoutHeight, orgLayoutWidth, orgLayoutHeight, 0));
            kidSize.height = Math.min(layoutHeight, kidSize.height);
            kidSize.width = Math.min(layoutWidth, kidSize.width);
            if (reverseLay)
              x -= kidSize.width;
            prealignKidBoundsMap.put(kid, new java.awt.geom.Rectangle2D.Float(x,
                (getHeightf() - kidSize.height - ins.top - ins.bottom)*individAlign +
                y, kidSize.width, kidSize.height));
            if (!reverseLay)
              x += kidSize.width;
            layoutWidth -= kidSize.width;
          }
          else
          {
            if (placedPrior)
            {
              if (reverseLay)
                y -= padY;
              else
                y += padY;
              layoutHeight -= padY;
            }
            kidSize.setFrame(kid.getPreferredSize(layoutWidth, layoutHeight, orgLayoutWidth, orgLayoutHeight, 0));
            kidSize.height = Math.min(layoutHeight, kidSize.height);
            kidSize.width = Math.min(layoutWidth, kidSize.width);
            if (reverseLay)
              y -= kidSize.height;
            prealignKidBoundsMap.put(kid, new java.awt.geom.Rectangle2D.Float(
                (getWidthf() - kidSize.width - ins.left - ins.right)*individAlign +
                x, y, kidSize.width, kidSize.height));
            if (!reverseLay)
              y += kidSize.height;
            layoutHeight -= kidSize.height;
          }
          placedPrior = (horizLay ? kidSize.width : kidSize.height) != 0;
        }
        else
          prealignKidBoundsMap.put(kid, null);
      }

      float extra = 0;
      if (fillLay || (horizLay && propWidg.hasProperty(Widget.HALIGNMENT)) ||
          (!horizLay && propWidg.hasProperty(Widget.VALIGNMENT)))
      {
        float align = propWidg.getFloatProperty(horizLay ? Widget.HALIGNMENT : Widget.VALIGNMENT, 0, null, this);
        if (reverseLay)
          extra = horizLay ? (x - ins.left) : (y - ins.top);
        else
          extra = horizLay ? (getWidthf() - ins.left - ins.right - x) : (getHeightf() - ins.top - ins.bottom - y);
        if (!fillLay)
          extra = extra * align;
      }
      if ((horizLay && (scrolling & ZDataTable.HORIZONTAL_DIMENSION) != 0) ||
          (!horizLay && (scrolling & ZDataTable.VERTICAL_DIMENSION) != 0))
        extra = Math.max(0, extra);
      float fillAdjust = 0;
      for (int i = 0; i < numKids; i++)
      {
        ZComp kid = kids[i];
        if (kid.backgroundComponent)
        {
          if (kid.passesConditional())
          {
            if (!kid.vis)
            {
              kid.vis = true;
              kid.appendToDirty(true);
            }
            kid.doLayout();
          }
          else
          {
            kid.vis = false;
            //kid.setBounds(0, 0, 0, 0);
            kid.forceChildrenToBeValid();
          }
          continue;
        }
        java.awt.geom.Rectangle2D.Float r = (java.awt.geom.Rectangle2D.Float) prealignKidBoundsMap.get(kid);
        if (r != null && extra != 0)
        {
          if (reverseLay)
          {
            if (horizLay)
              r.x -= extra;
            else
              r.y -= extra;
          }
          else if (fillLay)
          {
            if (horizLay)
            {
              r.x += fillAdjust;
              r.width += extra / numLayoutKids;
            }
            else
            {
              r.y += fillAdjust;
              r.height += extra / numLayoutKids;
            }
            fillAdjust += extra / numLayoutKids;
          }
          else
          {
            if (horizLay)
              r.x += extra;
            else
              r.y += extra;
          }
        }
        if (r == null)
        {
          kid.vis = false;
          //kid.setBounds(0, 0, 0, 0);
          kid.forceChildrenToBeValid();
        }
        else
        {
          if (!kid.vis)
          {
            kid.vis = true;
            kid.appendToDirty(true);
          }
          kid.setBounds(r);
          scrollingWidth = Math.max(scrollingWidth, kid.boundsf.x + kid.boundsf.width + ins.right +
              scrollPosX);
          scrollingHeight = Math.max(scrollingHeight, kid.boundsf.y + kid.boundsf.height + ins.bottom +
              scrollPosY);
          kid.doLayout();
        }
      }
      Pooler.returnPooledHashMap(prealignKidBoundsMap);
    }
    else
    {
      // Absolute positioning
      float layoutWidth = boundsf.width - ins.left - ins.right;
      float layoutHeight = boundsf.height - ins.top - ins.bottom;
      float orgLayoutWidth = layoutWidth;
      float orgLayoutHeight = layoutHeight;
      if ((scrolling & ZDataTable.HORIZONTAL_DIMENSION) != 0)
        layoutWidth = Float.MAX_VALUE/2;
      if ((scrolling & ZDataTable.VERTICAL_DIMENSION) != 0)
        layoutHeight = Float.MAX_VALUE/2;
      scrollingWidth = scrollingHeight = 0;
      java.awt.geom.Rectangle2D.Float kidSize = new java.awt.geom.Rectangle2D.Float();
      for (int i = skipFirst2Kids ? 2 : 0; i < numKids; i++)
      {
        ZComp kid = kids[i];
        ZPseudoComp pseudoKid = null;
        if (kid instanceof ZPseudoComp)
          pseudoKid = (ZPseudoComp) kid;
        boolean forceInvalid = false;
        if (pseudoKid != null && pseudoKid.backgroundComponent && pseudoKid.passesConditional())
        {
          if (!pseudoKid.vis)
          {
            pseudoKid.vis = true;
            pseudoKid.appendToDirty(true);
          }
          checkForLayoutLoad(pseudoKid);
          positionComponentFullByProps(pseudoKid, 0, 0, boundsf.width, boundsf.height, boundsf.width, boundsf.height);
          kid.doLayout();
        }
        else if (pseudoKid == null || pseudoKid.passesConditional())
        {
          if (!kid.vis)
          {
            kid.vis = true;
            kid.appendToDirty(true);
          }
          if (pseudoKid != null)
            checkForLayoutLoad(pseudoKid);
          kidSize.setFrame(kid.getPreferredSize(layoutWidth, layoutHeight, orgLayoutWidth, orgLayoutHeight, 0));
          if (pseudoKid == null || !uiMgr.disableParentClip() || (pseudoKid.validLayoutBits & FIXEDW_LAYOUT) == 0)
            kidSize.width = Math.min(layoutWidth, kidSize.width);
          if (pseudoKid == null || !uiMgr.disableParentClip() || (pseudoKid.validLayoutBits & FIXEDH_LAYOUT) == 0)
            kidSize.height = Math.min(layoutHeight, kidSize.height);
          float kidX = x;
          float kidY = y;
          if (pseudoKid != null)
          {
            if (pseudoKid.anchorPointX >= 0)
            {
              if ((pseudoKid.validLayoutBits & ABSOLUTEX_LAYOUT) != 0)
                kidX += pseudoKid.absoluteX - kidSize.width * pseudoKid.anchorPointX;
              else if ((pseudoKid.validLayoutBits & ANCHORX_LAYOUT) != 0)
                kidX += (boundsf.width - ins.left - ins.right)*pseudoKid.anchorX -
                kidSize.width*pseudoKid.anchorPointX;
            }
            else
            {
              if ((pseudoKid.validLayoutBits & ABSOLUTEX_LAYOUT) != 0)
                kidX += pseudoKid.absoluteX;
              else if ((pseudoKid.validLayoutBits & ANCHORX_LAYOUT) != 0)
                kidX += (boundsf.width - ins.left - ins.right - kidSize.width)*pseudoKid.anchorX;
            }
            if (pseudoKid.anchorPointY >= 0)
            {
              if ((pseudoKid.validLayoutBits & ABSOLUTEY_LAYOUT) != 0)
                kidY += pseudoKid.absoluteY - kidSize.height * pseudoKid.anchorPointY;
              else if ((pseudoKid.validLayoutBits & ANCHORY_LAYOUT) != 0)
                kidY += (boundsf.height - ins.top - ins.bottom)*pseudoKid.anchorY -
                kidSize.height*pseudoKid.anchorPointY;
            }
            else
            {
              if ((pseudoKid.validLayoutBits & ABSOLUTEY_LAYOUT) != 0)
                kidY += pseudoKid.absoluteY;
              else if ((pseudoKid.validLayoutBits & ANCHORY_LAYOUT) != 0)
                kidY += (boundsf.height - ins.top - ins.bottom - kidSize.height)*pseudoKid.anchorY;
            }
          }
          else if (widgType == Widget.ITEM && kid instanceof ZLabel)
          {
            // NOTE: This is a special trick to make Items easier to build
            kidX += (boundsf.width - ins.left - ins.right - kidSize.width)*
                propWidg.getFloatProperty(Widget.HALIGNMENT, 0.5f, null, this);
            kidY += (boundsf.height - ins.top - ins.bottom - kidSize.height)*
                propWidg.getFloatProperty(Widget.VALIGNMENT, 0.5f, null, this);
          }
          // NARFLEX - removed these on 9/17/09; they're redundant with the ones above
          //					kidSize.width = Math.min(kidSize.width, layoutWidth);
          //					kidSize.height = Math.min(kidSize.height, layoutHeight);
          /*if (propWidg.getProperty(Widget.ANIMATION).toLowerCase().startsWith("slide"))
					{
						if (animStart == 0)
							animStart = Sage.time();
						long animDur = 750;
						long animTime = Sage.time() - animStart;
						if (animTime < animDur)
						{
							kidX = Math.max(kidX, boundsf.width - (boundsf.width - kidX) * (((float)animTime)/animDur));
							forceInvalid = true;
						}
					}*/
          kid.setBounds(kidX, kidY, kidSize.width, kidSize.height);
          scrollingWidth = Math.max(scrollingWidth, kid.boundsf.x + kid.boundsf.width + ins.right +
              scrollPosX);
          scrollingHeight = Math.max(scrollingHeight, kid.boundsf.y + kid.boundsf.height + ins.bottom +
              scrollPosY);
          kid.doLayout();
        }
        else
        {
          pseudoKid.vis = false;
          //pseudoKid.setBounds(0, 0, 0, 0);
          pseudoKid.forceChildrenToBeValid();
        }
        if (forceInvalid)
          kid.invalidateAll();
      }
    }

    if (scrolling != 0)
    {
      // Check to make sure our scroll position is not showing area outside of what's valid
      float oldScrollX = scrollPosX;
      float oldScrollY = scrollPosY;
      scrollPosX = Math.min(Math.max(0, scrollPosX), Math.max(0, scrollingWidth - boundsf.width));
      // Scroll a little bit for if we're on the end
      if (lastLeanUpLeft)
      {
        if (scrollPosX <= insets.left + 1)
          scrollPosX = 0;
        else if (scrollPosX + boundsf.width + insets.right + 1 >= scrollingWidth)
          scrollPosX = Math.max(0, scrollingWidth - boundsf.width);
      }
      else
      {
        if (scrollPosX + boundsf.width + insets.right + 1 >= scrollingWidth)
          scrollPosX = Math.max(0, scrollingWidth - boundsf.width);
        else if (scrollPosX <= insets.left + 1)
          scrollPosX = 0;
      }
      scrollPosY = Math.min(Math.max(0, scrollPosY), Math.max(0, scrollingHeight - boundsf.height));
      // Scroll a little bit for if we're on the end
      if (lastLeanUpLeft)
      {
        if (scrollPosY <= insets.top + 1)
          scrollPosY = 0;
        else if (scrollPosY + boundsf.height + insets.bottom + 1 >= scrollingHeight)
          scrollPosY = Math.max(0, scrollingHeight - boundsf.height);
      }
      else
      {
        if (scrollPosY + boundsf.height + insets.bottom + 1 >= scrollingHeight)
          scrollPosY = Math.max(0, scrollingHeight - boundsf.height);
        else if (scrollPosY <= insets.top + 1)
          scrollPosY = 0;

      }
      // We need to redo the layout here because we offset our children by our scroll position
      if (!doNotRecurseLayoutNow && (scrollPosX != oldScrollX || scrollPosY != oldScrollY))
      {
        doNotRecurseLayoutNow = true;
        doLayoutNow();
        doNotRecurseLayoutNow = false;
      }
    }
    updatePaginationContext(ifv, ilv, ifh, ilh);
  }

  /** Invoked when the mouse button has been clicked (pressed
   * and released) on a component.
   */
  public void mouseClicked(java.awt.event.MouseEvent e)
  {
  }

  /** Invoked when the mouse enters a component.
   */
  public void mouseEntered(java.awt.event.MouseEvent e)
  {
    if (shouldTakeEvents())
    {
      Widget mouseListWidg = getUEListenWidget(UE_INDEX_MOUSE_ENTER);
      if (mouseListWidg != null)
      {
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceListener(this, mouseListWidg);
        Catbert.ExecutionPosition ep = null;
        Catbert.Context childContext = relatedContext.createChild();
        childContext.setLocal("X", new Integer(e.getX()));
        childContext.setLocal("Y", new Integer(e.getY()));
        childContext.setLocal("RelativeX", new Float(e.getX() / boundsf.width));
        childContext.setLocal("RelativeY", new Float(e.getY() / boundsf.height));
        childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
        Widget[] listenKids = mouseListWidg.contents();
        for (int i = 0; i < listenKids.length; i++)
        {
          if (listenKids[i].isProcessChainType())
          {
            if ((ep = processChain(listenKids[i], childContext, null, this, false)) != null)
            {
              ep.addToStack(listenKids[i]);
              ep.addToStackFinal(mouseListWidg);
              return;
            }
          }
        }
      }
      if (e.getSource() instanceof ZComp && uiMgr.getBoolean("ui/focus_follows_mouse", true))
      {
        ZComp src = (ZComp) e.getSource();
        ZPseudoComp picky = null;
        ZDataTable tabParent = null;
        while (src != null && picky == null)
        {
          if (src instanceof ZPseudoComp && src.isFocusable()/*((ZPseudoComp) src).widg.type().equals(Widget.ITEM)*/)
          {
            // Check to make sure its not a partially visible table cell which should not be focusable by the mouse
            tabParent = ((ZPseudoComp) src).getTableParent();
            if (tabParent == null || tabParent.isTableRegionVisible(src.getTrueBoundsf()))
              picky = (ZPseudoComp) src;
          }
          src = src.parent;
        }
        if (picky != null && !picky.isFocused())
        {
          ZPseudoComp tempParent = picky.getTopPseudoParent();
          int evtEmuType = 0;
          ZComp oldFocus = tempParent.getLastFocusedChild();
          if (oldFocus != null)
          {
            // This is just for the table transition notifier
            if (oldFocus.getTrueXf() < picky.getTrueXf() || oldFocus.getTrueYf() < picky.getTrueYf())
              evtEmuType = UserEvent.DOWN;
            else
              evtEmuType = UserEvent.UP;
          }
          selectNode(picky);
          tempParent.updateFocusTargetRect(0);
          if (tabParent != null && evtEmuType != 0)
          {
            tabParent.updateFocusTargetRect(0);
            tabParent.notifyOfTransition(evtEmuType);
          }
        }
      }
    }
  }

  /** Invoked when the mouse exits a component.
   */
  public void mouseExited(java.awt.event.MouseEvent e)
  {
    if (shouldTakeEvents())
    {
      Widget mouseListWidg = getUEListenWidget(UE_INDEX_MOUSE_EXIT);
      if (mouseListWidg != null)
      {
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceListener(this, mouseListWidg);
        Catbert.ExecutionPosition ep = null;
        Catbert.Context childContext = relatedContext.createChild();
        childContext.setLocal("X", new Integer(e.getX()));
        childContext.setLocal("Y", new Integer(e.getY()));
        childContext.setLocal("RelativeX", new Float(e.getX() / boundsf.width));
        childContext.setLocal("RelativeY", new Float(e.getY() / boundsf.height));
        childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
        Widget[] listenKids = mouseListWidg.contents();
        for (int i = 0; i < listenKids.length; i++)
        {
          if (listenKids[i].isProcessChainType())
          {
            if ((ep = processChain(listenKids[i], childContext, null, this, false)) != null)
            {
              ep.addToStack(listenKids[i]);
              ep.addToStackFinal(mouseListWidg);
              return;
            }
          }
        }
      }
    }
  }

  /** Invoked when a mouse button has been pressed on a component.
   */
  public void mousePressed(java.awt.event.MouseEvent e)
  {
    itemReleaseOK = false;
    generalReleaseOK = true;
    if (shouldTakeEvents() && (e.getModifiers() & java.awt.event.MouseEvent.BUTTON1_MASK) != 0)
    {
      if (e.getSource() instanceof ZComp)
      {
        ZComp src = (ZComp) e.getSource();
        ZPseudoComp picky = null;
        while (src != null && picky == null)
        {
          if (src instanceof ZPseudoComp && ((ZPseudoComp) src).widgType == Widget.ITEM &&
              src.isFocusable())
          {
            picky = (ZPseudoComp) src;
            // If the Widget has a specified mouse click handler which would override the default behavior;
            // don't do the standard selection
            if (picky.getUEListenWidget(UE_INDEX_MOUSE_CLICK) != null)
            {
              picky = null;
              break;
            }
          }
          src = src.parent;
        }
        int evtEmuType = 0;
        boolean pickyAlreadyFocused = picky != null && picky.isFocused();
        if (picky != null && !picky.isFocused())
        {
          ZPseudoComp tempParent = picky.getTopPseudoParent();
          ZComp oldFocus = tempParent.getLastFocusedChild();
          if (oldFocus != null)
          {
            // This is just for the table transition notifier
            if (oldFocus.getTrueXf() < picky.getTrueXf() || oldFocus.getTrueYf() < picky.getTrueYf())
              evtEmuType = UserEvent.DOWN;
            else
              evtEmuType = UserEvent.UP;
          }
          selectNode(picky);
          tempParent.updateFocusTargetRect(0);
        }
        else if (widgType == Widget.TEXTINPUT && !disableFocus)
        {
          ((ZLabel)kids[0]).setCursorLocation(e.getX(), e.getY());
        }
        itemReleaseOK = picky != null;
        if (itemReleaseOK)
        {
          // Mark the event as consumed
          e.consume();
        }
        // If focus is not following the mouse; then require a click to set focus first, and then another
        // to do the actual selection
        if (itemReleaseOK && !pickyAlreadyFocused && !uiMgr.getBoolean("ui/focus_follows_mouse", true) &&
            uiMgr.getBoolean("ui/require_focus_for_mouse_selection", false))
        {
          itemReleaseOK = false;
          if (picky != null && evtEmuType != 0)
          {
            ZDataTable tabParent = picky.getTableParent();
            if (tabParent != null)
            {
              tabParent.updateFocusTargetRect(0);
              tabParent.notifyOfTransition(evtEmuType);
            }
          }
        }
      }
    }
  }

  // used to be clicked...changed to make clicking easier
  public void mouseReleased(java.awt.event.MouseEvent e)
  {
    if (shouldTakeEvents() && generalReleaseOK)
    {
      Widget mouseListWidg = getUEListenWidget(UE_INDEX_MOUSE_CLICK);
      if (mouseListWidg != null)
      {
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceListener(this, mouseListWidg);
        Catbert.Context childContext = relatedContext.createChild();
        childContext.setLocal(Widget.MOUSE_CLICK, new Integer(e.getButton()));
        childContext.setLocal(mouseListWidg.getName(), new Integer(e.getButton()));
        childContext.setLocal(Widget.MOUSE_CLICK_COUNT, new Integer(e.getClickCount()));
        childContext.setLocal("X", new Integer(e.getX()));
        childContext.setLocal("Y", new Integer(e.getY()));
        childContext.setLocal("RelativeX", new Float(e.getX() / boundsf.width));
        childContext.setLocal("RelativeY", new Float(e.getY() / boundsf.height));
        childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
        Widget[] listenKids = mouseListWidg.contents();
        Catbert.ExecutionPosition ep = null;
        for (int i = 0; i < listenKids.length; i++)
        {
          if (listenKids[i].isProcessChainType())
          {
            if ((ep = processChain(listenKids[i], childContext, null, this, false)) != null)
            {
              ep.addToStack(listenKids[i]);
              ep.addToStackFinal(mouseListWidg);
              break;
            }
          }
        }
        if (!Catbert.evalBool(childContext.safeLookup(Catbert.PASSIVE_LISTEN_VAR)))
        {
          generalReleaseOK = itemReleaseOK = false;
          // Mark the event as consumed
          e.consume();
          return;
        }
      }
      if ((e.getModifiers() & java.awt.event.MouseEvent.BUTTON1_MASK) != 0 && itemReleaseOK)
      {
        if (e.getSource() instanceof ZComp)
        {
          ZComp src = (ZComp) e.getSource();
          ZPseudoComp picky = null;
          while (src != null && picky == null)
          {
            if (src instanceof ZPseudoComp && ((ZPseudoComp) src).widgType == Widget.ITEM &&
                src.isFocusable())
              picky = (ZPseudoComp) src;
            src = src.parent;
          }
          if (picky != null)
          {
            picky.actionPerformed(picky);
            // NOTE: We do this so if there's multiple mouse listeners for this component they
            // all don't fire the event for clicking on that item
            e.setSource(null);
            // We need to insert the mouse clicked popup events manually
            //if (isPopup())
            {
              //	uiMgr.forcePopupResult(picky.getObjectValue());
            }
            // Mark the event as consumed
            e.consume();
          }
        }
      }
    }
    generalReleaseOK = itemReleaseOK = false;
  }

  public void mouseDragged(java.awt.event.MouseEvent mouseEvent)
  {
    if (shouldTakeEvents())
    {
      Widget mouseListWidg = getUEListenWidget(UE_INDEX_MOUSE_DRAG);
      if (mouseListWidg != null)
      {
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceListener(this, mouseListWidg);
        Catbert.ExecutionPosition ep = null;
        Catbert.Context childContext = relatedContext.createChild();
        childContext.setLocal(Widget.MOUSE_DRAG, new Integer(mouseEvent.getButton()));
        childContext.setLocal(mouseListWidg.getName(), new Integer(mouseEvent.getButton()));
        childContext.setLocal("X", new Integer(mouseEvent.getX()));
        childContext.setLocal("Y", new Integer(mouseEvent.getY()));
        childContext.setLocal("RelativeX", new Float(mouseEvent.getX() / boundsf.width));
        childContext.setLocal("RelativeY", new Float(mouseEvent.getY() / boundsf.height));
        childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
        Widget[] listenKids = mouseListWidg.contents();
        for (int i = 0; i < listenKids.length; i++)
        {
          if (listenKids[i].isProcessChainType())
          {
            if ((ep = processChain(listenKids[i], childContext, null, this, false)) != null)
            {
              ep.addToStack(listenKids[i]);
              ep.addToStackFinal(mouseListWidg);
              return;
            }
          }
        }
      }
    }
  }

  public void mouseMoved(java.awt.event.MouseEvent mouseEvent)
  {
    if (shouldTakeEvents())
    {
      if (mouseEvent.getSource() != null)
      {
        if (widgType == Widget.TABLECOMPONENT && ZDataTable.CELL.equals(propWidg.getProperty(Widget.TABLE_SUBCOMP)))
        {
          ZDataTable tabParent = getTableParent();
          if (tabParent != null)
          {
            float relmousex = mouseEvent.getX() + ((ZComp) mouseEvent.getSource()).getTrueX() - getTrueX();
            float relmousey = mouseEvent.getY() + ((ZComp) mouseEvent.getSource()).getTrueY() - getTrueY();
            tabParent.checkForAutoscroll(relmousex/getWidth(), relmousey/getHeight());
          }
        }
      }
      Widget mouseListWidg = getUEListenWidget(UE_INDEX_MOUSE_MOVE);
      if (mouseListWidg != null)
      {
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceListener(this, mouseListWidg);
        Catbert.ExecutionPosition ep = null;
        Catbert.Context childContext = relatedContext.createChild();
        childContext.setLocal("X", new Integer(mouseEvent.getX()));
        childContext.setLocal("Y", new Integer(mouseEvent.getY()));
        childContext.setLocal("RelativeX", new Float(mouseEvent.getX() / boundsf.width));
        childContext.setLocal("RelativeY", new Float(mouseEvent.getY() / boundsf.height));
        childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
        Widget[] listenKids = mouseListWidg.contents();
        for (int i = 0; i < listenKids.length; i++)
        {
          if (listenKids[i].isProcessChainType())
          {
            if ((ep = processChain(listenKids[i], childContext, null, this, false)) != null)
            {
              ep.addToStack(listenKids[i]);
              ep.addToStackFinal(mouseListWidg);
              return;
            }
          }
        }
      }
    }
  }

  protected void selectNode(ZComp picky)
  {
    if (!shouldTakeEvents() && (!Catbert.evalBool(relatedContext.safeLookup("AllowHiddenFocus")))) return;

    boolean[] gotLock = new boolean[1];
    if (!uiMgr.getLock(true, gotLock, true))
    {
      if (Sage.DBG) System.out.println("Skipping node selection due to debug mode:" + widg);
      return;
    }
    try
    {
      PseudoMenu currMenu = uiMgr.getCurrUI();
      String nodeSelSound = currMenu.focusChangeSound;
      if (nodeSelSound != null && nodeSelSound.length() > 0)
        uiMgr.playSound(nodeSelSound);
      ZPseudoComp topParent = picky.getTopPseudoParent();
      topParent.setFocus(picky);
      // Because we may only be partially visible; we need to scroll any parent to ensure
      // maximum visibility of this component
      ZPseudoComp scrollParent = getScrollingContainer(picky, ZDataTable.BOTH_DIMENSIONS, false);
      if (scrollParent != null)
      {
        // NARFLEX - 12/4/09 - A new rule we're adding is if you move focus to the first or last focusable
        // item in scrollable panel that has more than a single focusable child; then also fully scroll that component
        // to the beginning or end; this'll prevent those cases where moving focus doesn't scroll the container as far
        // as you'd want and then you need to move it again to put it all properly into view.
        if (!(scrollParent instanceof ZDataTable))
        {
          java.util.ArrayList scrollKids = Pooler.getPooledArrayList();
          scrollParent.addFocusableChildrenToList(scrollKids);
          int idx = scrollKids.indexOf(picky);
          if (scrollKids.size() > 1)
          {
            if (idx == 0)
            {
              scrollParent.setOverallScrollLocation(0, 0, false);
            }
            else if (idx == scrollKids.size() - 1)
            {
              scrollParent.setOverallScrollLocation(1, 1, false);
            }
            else
              scrollParent.ensureVisibilityForRect(picky.getTrueBoundsf());
          }
          else
            scrollParent.ensureVisibilityForRect(picky.getTrueBoundsf());
        }
        else
          scrollParent.ensureVisibilityForRect(picky.getTrueBoundsf());
      }
      uiMgr.getRouter().updateLastEventTime();
      //topParent.evaluateFocusListeners();
    }
    finally
    {
      if (gotLock[0])
        uiMgr.clearLock();
    }
  }

  protected void ensureVisibilityForRect(java.awt.geom.Rectangle2D.Float childRect)
  {
    java.awt.geom.Rectangle2D.Float viewRect = getTrueBoundsf();
    if ((scrolling & ZDataTable.HORIZONTAL_DIMENSION) != 0)
    {
      if (!viewRect.contains(childRect))
      {
        if (viewRect.x > childRect.x)
        {
          // Scroll to the left
          setScrollPosition(scrollPosX - (viewRect.x - childRect.x), scrollPosY, false, true);
        }
        else if (viewRect.x + viewRect.width < childRect.x + childRect.width)
        {
          // Scroll to the right
          setScrollPosition(scrollPosX + (childRect.x + childRect.width) - (viewRect.x + viewRect.width),
              scrollPosY, false, false);
        }
      }
    }
    if ((scrolling & ZDataTable.VERTICAL_DIMENSION) != 0)
    {
      if (!viewRect.contains(childRect))
      {
        if (viewRect.y > childRect.y)
        {
          // Scroll up
          setScrollPosition(scrollPosX, scrollPosY - (viewRect.y - childRect.y), false, true);
        }
        else if (viewRect.y + viewRect.height < childRect.y + childRect.height)
        {
          // Scroll down
          setScrollPosition(scrollPosX, scrollPosY + (childRect.y + childRect.height) -
              (viewRect.y + viewRect.height), false, false);
        }
      }
    }
  }

  private void evaluateFocusListeners(int focusCheckState)
  {
    if ((focusListener & focusCheckState) != 0)
    {
      // On 4/8/04 I dropped the recursion because the evaluateTree recurses itself and the dirty
      // area affects both.
      // On 3/9/04 I changed this to be recursive because it can definitely affect the children
      evaluateTree(true, true); // was false, true
      // changed from invalidateAll() on 3/4/2004
      appendToDirty(true);
      return;
    }
    for (int i = 0; i < numKids; i++)
      if (kids[i] instanceof ZPseudoComp)
        ((ZPseudoComp) kids[i]).evaluateFocusListeners(focusCheckState);
  }

  private ZPseudoComp findFocusAnimatorChild()
  {
    if (!passesConditional()) return null;
    if (surfaceCache != null && surfaceCache.endsWith("Focus"))
      return this;
    for (int i = 0; i < numKids; i++)
      if (kids[i] instanceof ZPseudoComp)
      {
        ZPseudoComp rv = ((ZPseudoComp) kids[i]).findFocusAnimatorChild();
        if (rv != null) return rv;
      }
    return null;
  }

  private boolean doingPageListEval = false;
  private boolean doingTransListEval = false;
  protected void evaluatePagingListeners()
  {
    // protect against circularities
    if (doingPageListEval) return;
    if (pagingListener)
    {
      doingPageListEval = true;
      // NARFLEX - 3/11/10 - Used to be false,true but now it's true,true because paging vars can definitely
      // affect component display as well
      evaluateTree(true, true);
      appendToDirty(true);
      doingPageListEval = false;
      return;
    }
    if (!passesConditionalCacheValue) return;
    for (int i = 0; i < numKids; i++)
      if (kids[i] instanceof ZPseudoComp)
        ((ZPseudoComp) kids[i]).evaluatePagingListeners();
  }

  protected void evaluateTransitionListeners(boolean nextTransition)
  {
    // protect against circularities
    if (doingTransListEval) return;
    if ((nextTransition && nextTransitionListener) ||
        (!nextTransition && prevTransitionListener))
    {
      doingTransListEval = true;
      // NARFLEX - 3/11/10 - Used to be false,true but now it's true,true because transitional stuff can definitely
      // affect component display as well
      evaluateTree(true, true);
      appendToDirty(true);
      doingTransListEval = false;
      return;
    }
    if (!passesConditionalCacheValue) return;
    for (int i = 0; i < numKids; i++)
      if (kids[i] instanceof ZPseudoComp)
        ((ZPseudoComp) kids[i]).evaluateTransitionListeners(nextTransition);
  }

  protected void refreshByWidgetName(String refreshName)
  {
    if (refreshName.equals(widg.getName()))
    {
      evaluateTree(true, true);
      appendToDirty(true);
      return;
    }
    for (int i = 0; i < numKids; i++)
      if (kids[i] instanceof ZPseudoComp)
        ((ZPseudoComp) kids[i]).refreshByWidgetName(refreshName);
  }

  protected void repaintByWidgetName(String refreshName)
  {
    if (refreshName.equals(widg.getName()))
    {
      appendToDirty(true);
      return;
    }
    for (int i = 0; i < numKids; i++)
      if (kids[i] instanceof ZPseudoComp)
        ((ZPseudoComp) kids[i]).repaintByWidgetName(refreshName);
  }

  protected void refreshByValue(String varName, Object focusValue)
  {
    if (!passesConditional()) return;

    Object myValue = relatedContext.safeLookup(varName);
    if (myValue == focusValue || (myValue != null && myValue.equals(focusValue)))
    {
      evaluateTree(true, true);
      appendToDirty(true);
    }
    for (int i = 0; i < numKids; i++)
    {
      Object currKid = kids[i];
      if (currKid instanceof ZPseudoComp)
        ((ZPseudoComp) currKid).refreshByValue(varName, focusValue);
    }
  }

  // This is the entry point for setting focus!!! You can do higher level analysis in here!!
  public boolean setFocus(ZComp focusMe)
  {
    if (postFocusProcessing == null)
      postFocusProcessing = new java.util.ArrayList();
    ZComp orgFocus = getFocusOwner(false);
    boolean rv = setFocus(focusMe, false, postFocusProcessing);
    ZComp newFocus = getFocusOwner(false);
    if (orgFocus != newFocus && !postFocusProcessing.isEmpty())
    {
      // Go through the focus processing list and do these things:
      // 1. Call all of the FocusLost hooks that are in there
      // 2. Call all of the FocusGained hooks
      // 3. Run all of the evaluators
      for (int step = 1; step <= 3; step++)
      {
        for (int i = 0; i < postFocusProcessing.size(); i++)
        {
          Object o = postFocusProcessing.get(i);
          if (o instanceof Widget)
          {
            if (step == 1 && "FocusLost".equals(((Widget)o).getName()))
              Catbert.processHookDirectly((Widget) o, null, uiMgr, (ZPseudoComp) postFocusProcessing.get(i + 1));
            else if (step == 2 && "FocusGained".equals(((Widget)o).getName()))
              Catbert.processHookDirectly((Widget) o, null, uiMgr, (ZPseudoComp) postFocusProcessing.get(i + 1));
            i++;
          }
          else if (step == 3)
          {
            ZPseudoComp zp = (ZPseudoComp) o;
            zp.evaluateFocusListeners((zp.widgType == Widget.MENU || zp.widgType == Widget.OPTIONSMENU) ?
                ALL_FOCUS_CHANGES : PARENT_FOCUS_CHANGES);
          }
        }
      }
    }
    postFocusProcessing.clear();
    return rv;
  }

  protected boolean setFocus(ZComp focusMe, boolean parentTookFocus, java.util.ArrayList currPostFocusProcessing)
  {
    boolean wasFocused = isFocused();
    boolean rv;
    if (widgType == Widget.MENU || widgType == Widget.OPTIONSMENU)
    {
      ZComp orgFocus = getFocusOwner(false);
      rv = super.setFocus(focusMe, parentTookFocus, currPostFocusProcessing);
      if (orgFocus != getFocusOwner(false) && focusMe != null)
        currPostFocusProcessing.add(this);
    }
    else
    {
      rv = super.setFocus(focusMe, parentTookFocus, currPostFocusProcessing);
      if (wasFocused != isFocused() && focusMe != null)
      {
        currPostFocusProcessing.add(this);
        // See if we have focus animation to deal with. Always do this from the focus loser so
        // we get the right source rect.
        // If we've got a menu transition focus animator that's still there then don't do this new animation
        // because it's most likely an override on the default focus location so we'd end up with an animation
        // from each of the 2 old focus locations
        if (!isFocused() && uiMgr.areLayersEnabled() && reality.getLostFocusAnimationOp() == null)
        {
          ZPseudoComp focusAnimChild = findFocusAnimatorChild();
          if (focusAnimChild != null)
          {
            java.awt.geom.Rectangle2D.Float oldFocBounds = focusAnimChild.getTrueBoundsf();
            // If we didn't get our layout completed but got the focus anyways (which is possible) then
            // we may have no size which'll cause the focus anim to do an undersirable smooth animation.
            if (oldFocBounds.width > 0 && oldFocBounds.height > 0)
            {
              // We set this "Focus" animation operation in the top level parent who will then find the appopriately focused
              // child on the next rendering pass and move the animation operation into that one
              ZPseudoComp topComp = getTopPseudoParent();
              if (topComp != null)
              {
                if (topComp.pendingAnimations == null)
                  topComp.pendingAnimations = new java.util.ArrayList();
                String focusAnim = "SmoothQuadratic";//uiMgr.get("ui/animation/focus_transition_animation", "MorphQuadratic");
                long focusAnimDur = uiMgr.getLong("ui/animation/focus_transition_duration", 200);
                if (focusAnimDur > 0)
                  topComp.pendingAnimations.add(new RenderingOp(focusAnimChild.surfaceCache,
                      focusAnim,
                      focusAnimDur, 0, focusAnimChild.getTrueBoundsf(), focusAnimChild.getBGAlpha(), false));
              }
            }
          }
        }
      }
    }
    if ((focusGainedHook != null || focusLostHook != null) && wasFocused != isFocused())
    {
      if (wasFocused && focusLostHook != null)
      {
        currPostFocusProcessing.add(focusLostHook);
        currPostFocusProcessing.add(this);
      }
      else if (!wasFocused && focusGainedHook != null)
      {
        currPostFocusProcessing.add(focusGainedHook);
        currPostFocusProcessing.add(this);
      }
    }
    return rv;
  }

  public boolean isFocusable()
  {
    return (!disableFocus && (widgType == Widget.ITEM || widgType == Widget.TEXTINPUT)) ||
        (((scrolling != 0 && (boundsf.width == 0 || boundsf.height == 0)) ||
            ((scrolling != 0 || this instanceof ZDataTable) &&
                (!isFirstVPage() || !isLastVPage() || !isFirstHPage() || !isLastHPage()))) &&
                !hasFocusableChildren());
  }

  public boolean hasFocusableChildren()
  {
    for (int i = 0; i < numKids; i++)
    {
      if (kids[i].passesConditional() && (kids[i].isFocusable() || kids[i].hasFocusableChildren()))
        return true;
    }
    return false;
  }

  protected boolean isChildInSameFocusHierarchy(ZComp childComp)
  {
    return (widgType != Widget.MENU) || !(childComp instanceof ZPseudoComp) ||
        (((ZPseudoComp) childComp).widgType != Widget.OPTIONSMENU);
  }

  private boolean isInFocusableHierarchy()
  {
    if (isFocusable())
      return true;
    ZComp testParent = parent;
    while (testParent instanceof ZPseudoComp)
    {
      if (((ZPseudoComp)testParent).isFocusable())
        return true;
      testParent = testParent.parent;
    }
    return false;
  }

  public void addFocusableChildrenToList(java.util.ArrayList v)
  {
    if (!passesConditional()) return;
    super.addFocusableChildrenToList(v);
  }

  // Send them up the tree until something consumes it
  protected boolean propogateAction(UserEvent evt)
  {
    if (parent != null)
    {
      if (parent.action(evt))
        return true;
    }
    return false;
  }
  private boolean processDirectEvents(UserEvent evt)
  {
    int evtType = evt.getType();
    Catbert.ExecutionPosition ep = null;
    if (evt.isIR())
    {
      Widget rawIRListener = getUEListenWidget(UE_INDEX_RAW_IR);
      if (rawIRListener != null)
      {
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceListener(this, rawIRListener);
        Widget[] listenKids = rawIRListener.contents();
        Catbert.Context childContext = relatedContext.createChild();
        childContext.setLocal(null, new Long(evt.getIRCode()));
        childContext.setLocal(Widget.RAW_IR, new Long(evt.getIRCode()));
        childContext.setLocal(rawIRListener.getName(), new Long(evt.getIRCode()));
        childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
        if (evt.getPayloads() != null)
          childContext.setLocal(PAYLOAD_VAR, evt.getPayloads());
        for (int i = 0; i < listenKids.length; i++)
        {
          if (listenKids[i].isProcessChainType())
          {
            if ((ep = processChain(listenKids[i], childContext, null, this, false)) != null)
            {
              ep.addToStack(listenKids[i]);
              ep.addToStackFinal(rawIRListener);
              break;
            }
          }
        }
        if (!Catbert.evalBool(childContext.safeLookup(Catbert.PASSIVE_LISTEN_VAR)))
          return true;
      }
    }

    // Check for raw KB listeners
    if (getUEListenWidget(UE_INDEX_RAW_KB) != null && evt.isKB())
    {
      /*
       * We want to simplify keyboard listening and not deal with press,release,typed event
       * distinguishment. To do this, only complete keystrokes should be sent.
       * These occur under the following rules:
       * 1. Complete keystrokes occur on a keyReleased event
       * 2. Complete keystrokes will have the prior event as a keyPressed event
       */
      Widget kbWidg = getUEListenWidget(UE_INDEX_RAW_KB);
      if (uiMgr.getTracer() != null) uiMgr.getTracer().traceListener(this, kbWidg);
      String keystroke = Catbert.getStringFromKeystroke(evt.getKeyCode(), evt.getKeyModifiers());
      Catbert.Context childContext = relatedContext.createChild();
      childContext.setLocal(null, keystroke);
      childContext.setLocal("KeyCode", new Integer(evt.getKeyCode()));
      childContext.setLocal("KeyModifiers", new Integer(evt.getKeyModifiers()));
      childContext.setLocal("KeyChar", (evt.getKeyChar() != 0) ? ("" + evt.getKeyChar()) : "");
      childContext.setLocal(kbWidg.getName(), keystroke);
      childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
      if (evt.getPayloads() != null)
        childContext.setLocal(PAYLOAD_VAR, evt.getPayloads());
      Widget[] listenKids = kbWidg.contents();
      for (int i = 0; i < listenKids.length; i++)
      {
        if (listenKids[i].isProcessChainType())
        {
          if ((ep = processChain(listenKids[i], childContext, null, this, false)) != null)
          {
            ep.addToStack(listenKids[i]);
            ep.addToStackFinal(kbWidg);
            break;
          }
        }
      }
      if (!Catbert.evalBool(childContext.safeLookup(Catbert.PASSIVE_LISTEN_VAR)))
        return true;
    }

    for (int i = 0; i < 3; i++)
    {
      Widget ueListenWidg = null;
      if (i == 0)
        ueListenWidg = getUEListenWidget(evtType);
      else if (i == 1 && evt.getSecondaryType() != 0)
        ueListenWidg = getUEListenWidget(evt.getSecondaryType());
      else if (i == 2 && evt.getTernaryType() != 0)
        ueListenWidg = getUEListenWidget(evt.getTernaryType());

      if (ueListenWidg != null)
      {
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceListener(this, ueListenWidg);
        // 11/11/03 - I used to not create a child context here, but I added it when I did the
        // passive listen stuff
        Widget[] listenKids = ueListenWidg.contents();
        Catbert.Context childContext = relatedContext.createChild();
        childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
        if (evt.getPayloads() != null)
          childContext.setLocal(PAYLOAD_VAR, evt.getPayloads());
        for (int j = 0; j < listenKids.length; j++)
        {
          if (listenKids[j].isProcessChainType())
          {
            if ((ep = processChain(listenKids[j], childContext, null, this, false)) != null)
            {
              ep.addToStack(listenKids[j]);
              ep.addToStackFinal(ueListenWidg);
              break;
            }
          }
        }
        if (!Catbert.evalBool(childContext.safeLookup(Catbert.PASSIVE_LISTEN_VAR)))
          return true;
      }
    }
    int numericText = evt.getNumCode(evtType);
    if (numericText != -1 || evtType == UserEvent.DASH)
    {
      Widget numberListener = getUEListenWidget(UE_INDEX_NUMBERS);
      if (numberListener != null)
      {
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceListener(this, numberListener);
        Widget[] listenKids = numberListener.contents();
        Catbert.Context childContext = relatedContext.createChild();
        childContext.setLocal(null, (evtType == UserEvent.DASH) ? "-" : Integer.toString(numericText));
        childContext.setLocal(numberListener.getName(), (evtType == UserEvent.DASH) ? "-" : Integer.toString(numericText));
        childContext.setLocal(Catbert.PASSIVE_LISTEN_VAR, null);
        if (evt.getPayloads() != null)
          childContext.setLocal(PAYLOAD_VAR, evt.getPayloads());

        for (int i = 0; i < listenKids.length; i++)
          if (listenKids[i].isProcessChainType())
            if ((ep = processChain(listenKids[i], childContext, null, this, false)) != null)
            {
              ep.addToStack(listenKids[i]);
              ep.addToStackFinal(numberListener);
              break;
            }
        if (!Catbert.evalBool(childContext.safeLookup(Catbert.PASSIVE_LISTEN_VAR)))
          return true;
      }
    }
    return false;
  }
  public boolean action(UserEvent evt)
  {
    int evtType = evt.getType();
    boolean eventsAreOK = shouldTakeEvents();
    if (uiMgr.getTracer() != null) uiMgr.getTracer().traceEvent(this, UserEvent.getPrettyEvtName(evtType),
        evt.getIRCode(), evt.getKeyCode(), evt.getKeyModifiers(), evt.getKeyChar());
    /*
     * Check our map for an action
     * All components can directly override any UserEvent. This is where that is done.
     */
    // Check for the basic IR override first
    if (eventsAreOK && !uiMgr.isXBMCCompatible())
    {
      if (processDirectEvents(evt))
        return true;
    }

    ZComp currSelNode = null;
    if (evtType == UserEvent.SELECT && widgType == Widget.ITEM)
    {
      // Item widgets respond directly to SELECT commands and execute their process chain
      actionPerformed(this);
      return true;
    }

    if (eventsAreOK && widgType == Widget.TEXTINPUT && numKids > 0)
    {
      if (!disableFocus)
      {
        // core editable text input
        if (((ZLabel) kids[0]).processInput(evt))
          return true;
      }
      else
      {
        int numericText = evt.getNumCode(evtType);
        if (numericText != -1 || evtType == UserEvent.DASH)
        {
          ZLabel kidLabel = (ZLabel) kids[0];
          kidLabel.setText(kidLabel.getText() + ((evtType == UserEvent.DASH) ? "-" : Integer.toString(numericText)));
          return true;
        }
        else if (evt.isKB())
        {
          ZLabel kidLabel = (ZLabel) kids[0];
          if (evt.getKeyChar() != 0)
          {
            char keyChar = evt.getKeyChar();
            String currText = kidLabel.getText();
            if (keyChar != '\r' && keyChar != '\n')
            {
              if (keyChar == '\b')
              {
                if (currText.length() > 0)
                {
                  kidLabel.setText(currText.substring(0, currText.length() - 1));
                }
              }
              else
              {
                kidLabel.setText(currText + keyChar);
              }
              return true;
            }
          }
          else if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_INSERT && evt.getKeyModifiers() == java.awt.event.KeyEvent.SHIFT_MASK)
          {
            // Paste test into the text widget
            try
            {
              java.awt.datatransfer.Transferable xfer = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
              if (xfer != null)
              {
                Object strData = xfer.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
                if (strData != null)
                {
                  kidLabel.setText(strData.toString());
                }
              }
            }
            catch (Exception e)
            {
              System.out.println("Error pasting from clipboard of:" + e);
              e.printStackTrace();
            }
          }
        }
      }
    }

    /*
     * Paging controls are handled in the override for this method
     */
    if (evtType == UserEvent.PAGE_UP || evtType == UserEvent.PAGE_DOWN ||
        evtType == UserEvent.PAGE_LEFT || evtType == UserEvent.PAGE_RIGHT ||
        evtType == UserEvent.CHANNEL_UP || evtType == UserEvent.CHANNEL_DOWN ||
        evtType == UserEvent.REW || evtType == UserEvent.FF)
    {
      if (scrolling != 0 && eventsAreOK)
      {
        // Check for scrolling commands
        if ((scrolling & ZDataTable.VERTICAL_DIMENSION) != 0)
        {
          if ((evtType == UserEvent.PAGE_UP || evtType == UserEvent.CHANNEL_UP) &&
              !isFirstVPage())
          {
            setScrollPosition(scrollPosX, scrollPosY -
                Math.max(boundsf.height - getScrollUnitAmount(), getScrollUnitAmount()), true, true);
            return true;
          }
          else if ((evtType == UserEvent.PAGE_DOWN || evtType == UserEvent.CHANNEL_DOWN) &&
              !isLastVPage())
          {
            setScrollPosition(scrollPosX, scrollPosY +
                Math.max(boundsf.height - getScrollUnitAmount(), getScrollUnitAmount()), true, false);
            return true;
          }
        }
        if ((scrolling & ZDataTable.HORIZONTAL_DIMENSION) != 0)
        {
          if ((evtType == UserEvent.PAGE_RIGHT || evtType == UserEvent.FF) &&
              !isLastHPage())
          {
            setScrollPosition(scrollPosX + Math.max(boundsf.width - getScrollUnitAmount(),
                getScrollUnitAmount()), scrollPosY, true, false);
            return true;
          }
          else if ((evtType == UserEvent.PAGE_LEFT || evtType == UserEvent.REW) &&
              !isFirstHPage())
          {
            setScrollPosition(scrollPosX - Math.max(boundsf.width - getScrollUnitAmount(),
                getScrollUnitAmount()), scrollPosY, true, true);
            return true;
          }
        }
      }
      /*
       * NOTE:Also check scrolling siblings. This is necessary because if we create a scrolling
       * Panel we want to be able to put scrolling controls specific to that panel somewhere without
       * affecting the layout of that panel. So creating a parent panel to hold them both makes that
       * parent act like a scroll container.
       */
      for (int i = 0; i < parent.numKids; i++)
      {
        ZPseudoComp currSib = (ZPseudoComp) parent.kids[i];
        if (currSib == this)
          continue;
        if (currSib.scrolling != 0 && currSib.shouldTakeEvents())
          return currSib.action(evt);
      }
      /*
       * Select the last item in the paging direction. Check for a scrolling container
       * if "paging_always_scrolls" is true.
       * The way we do this is like wrap around navigation in the other direction.
       */
      ZPseudoComp scrollParent = null;
      //if (Sage.getBoolean("ui/paging_always_scrolls", true))
      {
        switch (evtType)
        {
          case UserEvent.PAGE_LEFT:
          case UserEvent.REW:
          case UserEvent.PAGE_RIGHT:
          case UserEvent.FF:
            scrollParent = getScrollingContainer(this, ZDataTable.HORIZONTAL_DIMENSION, true);
            break;
          default:
            scrollParent = getScrollingContainer(this, ZDataTable.VERTICAL_DIMENSION, true);
            break;
        }
      }
      if (scrollParent == null)
      {
        currSelNode = getFocusOwner(false);
        if (currSelNode != null)
        {
          java.util.ArrayList focusKids = Pooler.getPooledArrayList();//new java.util.ArrayList();
          addFocusableChildrenToList(focusKids);

          java.awt.geom.Rectangle2D.Float selBounds = currSelNode.getTrueBoundsf();
          ZComp minDistNode = null;
          float minDist = Float.MAX_VALUE;
          float minDist2 = Float.MAX_VALUE;

          // Adjust the bounds for wrap around source position
          switch (evtType)
          {
            case UserEvent.PAGE_LEFT:
            case UserEvent.REW:
              selBounds.x = -1;
              selBounds.width = 1;
              break;
            case UserEvent.PAGE_RIGHT:
            case UserEvent.FF:
              selBounds.x = getWidthf() + getTrueXf();
              break;
            case UserEvent.PAGE_UP:
            case UserEvent.CHANNEL_UP:
              selBounds.y = -1;
              selBounds.height = 1;
              break;
            case UserEvent.PAGE_DOWN:
            case UserEvent.CHANNEL_DOWN:
              selBounds.y = getHeightf() + getTrueYf();
              break;
          }
          for (int i = 0; i < focusKids.size(); i++)
          {
            ZPseudoComp currKid = (ZPseudoComp) focusKids.get(i);
            if (currKid.shouldTakeEvents() && currKid.isWithinScreenBounds())
            {
              java.awt.geom.Rectangle2D.Float kidBounds = currKid.getTrueBoundsf();
              // Make adjustments so edges don't perfectly align
              if (kidBounds.width > 0.02f && kidBounds.height > 0.02f)
              {
                kidBounds.x += 0.01f;
                kidBounds.y += 0.01f;
                kidBounds.width -= 0.02f;
                kidBounds.height -= 0.02f;
              }

              int currOut = selBounds.outcode(kidBounds.x + kidBounds.width/2,
                  kidBounds.y + kidBounds.height/2);
              if (evtType == UserEvent.PAGE_DOWN || evtType == UserEvent.CHANNEL_DOWN)
              {
                if ((currOut & java.awt.geom.Rectangle2D.OUT_TOP) == java.awt.geom.Rectangle2D.OUT_TOP)
                {
                  float testDist = Math.abs(selBounds.y - (kidBounds.y + kidBounds.height));
                  float testDist2 = 0;
                  if (selBounds.getMinX() < kidBounds.getMaxX() &&
                      selBounds.getMaxX() > kidBounds.getMinX())
                    testDist2 = 0;
                  else
                  {
                    testDist2 = Math.min(Math.abs(selBounds.x - (kidBounds.x + kidBounds.width)),
                        Math.abs(selBounds.x + selBounds.width - kidBounds.x));
                    if (testDist2 == 0) // edge-aligned
                      testDist2 = 1;
                  }
                  if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                  {
                    minDist = testDist;
                    minDist2 = testDist2;
                    minDistNode = currKid;
                  }
                }
              }
              else if (evtType == UserEvent.PAGE_LEFT || evtType == UserEvent.REW)
              {
                if ((currOut & java.awt.geom.Rectangle2D.OUT_RIGHT) == java.awt.geom.Rectangle2D.OUT_RIGHT)
                {
                  float testDist = Math.abs(selBounds.x + selBounds.width - kidBounds.x);
                  float testDist2 = 0;
                  if (selBounds.getMinY() < kidBounds.getMaxY() &&
                      selBounds.getMaxY() > kidBounds.getMinY())
                    testDist2 = 0;
                  else
                  {
                    testDist2 = Math.min(Math.abs(selBounds.y - (kidBounds.y + kidBounds.height)),
                        Math.abs(selBounds.y + selBounds.height - kidBounds.y));
                    if (testDist2 == 0) // edge-aligned
                      testDist2 = 1;
                  }
                  if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                  {
                    minDist = testDist;
                    minDist2 = testDist2;
                    minDistNode = currKid;
                  }
                }
              }
              else if (evtType == UserEvent.PAGE_UP || evtType == UserEvent.CHANNEL_UP)
              {
                if ((currOut & java.awt.geom.Rectangle2D.OUT_BOTTOM) == java.awt.geom.Rectangle2D.OUT_BOTTOM)
                {
                  float testDist = Math.abs(selBounds.y + selBounds.height - kidBounds.y);
                  float testDist2 = 0;
                  if (selBounds.getMinX() < kidBounds.getMaxX() &&
                      selBounds.getMaxX() > kidBounds.getMinX())
                    testDist2 = 0;
                  else
                  {
                    testDist2 = Math.min(Math.abs(selBounds.x - (kidBounds.x + kidBounds.width)),
                        Math.abs(selBounds.x + selBounds.width - kidBounds.x));
                    if (testDist2 == 0) // edge-aligned
                      testDist2 = 1;
                  }
                  if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                  {
                    minDist = testDist;
                    minDist2 = testDist2;
                    minDistNode = currKid;
                  }
                }
              }
              else if (evtType == UserEvent.PAGE_RIGHT || evtType == UserEvent.FF)
              {
                if ((currOut & java.awt.geom.Rectangle2D.OUT_LEFT) == java.awt.geom.Rectangle2D.OUT_LEFT)
                {
                  float testDist = Math.abs(selBounds.x - (kidBounds.x + kidBounds.width));
                  float testDist2 = 0;
                  if (selBounds.getMinY() < kidBounds.getMaxY() &&
                      selBounds.getMaxY() > kidBounds.getMinY())
                    testDist2 = 0;
                  else
                  {
                    testDist2 = Math.min(Math.abs(selBounds.y - (kidBounds.y + kidBounds.height)),
                        Math.abs(selBounds.y + selBounds.height - kidBounds.y));
                    if (testDist2 == 0) // edge-aligned
                      testDist2 = 1;
                  }
                  if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                  {
                    minDist = testDist;
                    minDist2 = testDist2;
                    minDistNode = currKid;
                  }
                }
              }
            }
          }
          Pooler.returnPooledArrayList(focusKids);
          if (minDistNode != null && minDistNode != currSelNode)
          {
            selectNode(minDistNode);
            ZPseudoComp tempParent = minDistNode.getTopPseudoParent();
            tempParent.updateFocusTargetRect(evt.getType());
            // Check for wrap around table navigation and notify the table of it
            ZDataTable orgTabParent = (currSelNode instanceof ZPseudoComp) ? ((ZPseudoComp)currSelNode).getTableParent() : null;
            ZDataTable newTabParent = ((ZPseudoComp)minDistNode).getTableParent();
            if (orgTabParent != null && orgTabParent == newTabParent)
            {
              newTabParent.notifyOfTransition(evt.getType());
            }
            return true;
          }
        }
      }
      return propogateAction(evt);
    }

    // Not sure why I had this in there, but it was lumped in with propogating paging controls before,
    // maybe an optimization
    if (widgType == Widget.TABLECOMPONENT)
      return propogateAction(evt);

    currSelNode = getFocusOwner(false);
    if (evt.isDirectionalType())
    {
      if (currSelNode != null)
      {
        java.util.ArrayList focusKids = Pooler.getPooledArrayList();
        addFocusableChildrenToList(focusKids);

        java.awt.geom.Rectangle2D.Float selBounds = currSelNode.getTrueBoundsf();
        // Use the outcode for the rectangle and find the nearest one in that direction
        ZComp minDistNode = null;
        float minDist = Float.MAX_VALUE;
        float minDist2 = Float.MAX_VALUE;
        for (int i = 0; i < focusKids.size(); i++)
        {
          ZPseudoComp currKid = (ZPseudoComp) focusKids.get(i);
          if (currKid.shouldTakeEvents() && currKid != currSelNode)
          {
            java.awt.geom.Rectangle2D.Float kidBounds = currKid.getTrueBoundsf();
            // Make adjustments so edges don't perfectly align
            if (kidBounds.width > 0.02f && kidBounds.height > 0.02f)
            {
              kidBounds.x += 0.01f;
              kidBounds.y += 0.01f;
              kidBounds.width -= 0.02f;
              kidBounds.height -= 0.02f;
            }

            int currOut = selBounds.outcode(kidBounds.x + kidBounds.width/2,
                kidBounds.y + kidBounds.height/2);
            if (UserEvent.isUpEvent(evtType))
            {
              if ((currOut & java.awt.geom.Rectangle2D.OUT_TOP) == java.awt.geom.Rectangle2D.OUT_TOP)
              {
                float testDist = Math.abs(selBounds.y - (kidBounds.y + kidBounds.height));
                float testDist2 = 0;
                if (selBounds.getMinX() < kidBounds.getMaxX() &&
                    selBounds.getMaxX() > kidBounds.getMinX())
                  testDist2 = 0;
                else
                {
                  testDist2 = Math.min(Math.abs(selBounds.x - (kidBounds.x + kidBounds.width)),
                      Math.abs(selBounds.x + selBounds.width - kidBounds.x));
                  if (testDist2 == 0) // edge-aligned
                    testDist2 = 1;
                }
                if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                {
                  minDist = testDist;
                  minDist2 = testDist2;
                  minDistNode = currKid;
                }
              }
            }
            else if (UserEvent.isRightEvent(evtType))
            {
              if ((currOut & java.awt.geom.Rectangle2D.OUT_RIGHT) == java.awt.geom.Rectangle2D.OUT_RIGHT)
              {
                float testDist = Math.abs(selBounds.x + selBounds.width - kidBounds.x);
                float testDist2 = 0;
                if (selBounds.getMinY() < kidBounds.getMaxY() &&
                    selBounds.getMaxY() > kidBounds.getMinY())
                  testDist2 = 0;
                else
                {
                  testDist2 = Math.min(Math.abs(selBounds.y - (kidBounds.y + kidBounds.height)),
                      Math.abs(selBounds.y + selBounds.height - kidBounds.y));
                  if (testDist2 == 0) // edge-aligned
                    testDist2 = 1;
                }
                if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                {
                  minDist = testDist;
                  minDist2 = testDist2;
                  minDistNode = currKid;
                }
              }
            }
            else if (UserEvent.isDownEvent(evtType))
            {
              if ((currOut & java.awt.geom.Rectangle2D.OUT_BOTTOM) == java.awt.geom.Rectangle2D.OUT_BOTTOM)
              {
                float testDist = Math.abs(selBounds.y + selBounds.height - kidBounds.y);
                float testDist2 = 0;
                if (selBounds.getMinX() < kidBounds.getMaxX() &&
                    selBounds.getMaxX() > kidBounds.getMinX())
                  testDist2 = 0;
                else
                {
                  testDist2 = Math.min(Math.abs(selBounds.x - (kidBounds.x + kidBounds.width)),
                      Math.abs(selBounds.x + selBounds.width - kidBounds.x));
                  if (testDist2 == 0) // edge-aligned
                    testDist2 = 1;
                }
                if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                {
                  minDist = testDist;
                  minDist2 = testDist2;
                  minDistNode = currKid;
                }
              }
            }
            else if (UserEvent.isLeftEvent(evtType))
            {
              if ((currOut & java.awt.geom.Rectangle2D.OUT_LEFT) == java.awt.geom.Rectangle2D.OUT_LEFT)
              {
                float testDist = Math.abs(selBounds.x - (kidBounds.x + kidBounds.width));
                float testDist2 = 0;
                if (selBounds.getMinY() < kidBounds.getMaxY() &&
                    selBounds.getMaxY() > kidBounds.getMinY())
                  testDist2 = 0;
                else
                {
                  testDist2 = Math.min(Math.abs(selBounds.y - (kidBounds.y + kidBounds.height)),
                      Math.abs(selBounds.y + selBounds.height - kidBounds.y));
                  if (testDist2 == 0) // edge-aligned
                    testDist2 = 1;
                }
                if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                {
                  minDist = testDist;
                  minDist2 = testDist2;
                  minDistNode = currKid;
                }
              }
            }
          }
        }

        int ueType = evtType;
        boolean horiz = UserEvent.isLeftEvent(ueType) || UserEvent.isRightEvent(ueType);
        if (minDistNode == null && (((widgType == Widget.MENU || widgType == Widget.OPTIONSMENU) &&
            true/*Sage.getBoolean("ui/menu_wrap_around_focus_navigation", true)*/) ||
            (propWidg.getBooleanProperty(Widget.WRAP_HORIZONTAL_NAVIGATION, null, this) && horiz) ||
            (propWidg.getBooleanProperty(Widget.WRAP_VERTICAL_NAVIGATION, null, this) && !horiz)))
        {
          // Wrap around navigation, but don't wrap if focus is inside a table because tables scroll
          // when there's navigation to components outside its bounds.
          // NOTE: Allow nav wrapping around the dimensions of the table/panel that don't scroll
          ZPseudoComp scrollParent;
          if (horiz)
            scrollParent = getScrollingContainer(currSelNode, ZDataTable.HORIZONTAL_DIMENSION, true);
          else
            scrollParent = getScrollingContainer(currSelNode, ZDataTable.VERTICAL_DIMENSION, true);
          if (!(scrollParent instanceof ZDataTable))//(scrollParent == null)
          {
            // Adjust the bounds for wrap around source position
            if (UserEvent.isLeftEvent(evtType))
              selBounds.x = 100000f;//getWidthf() + getTrueXf();
            else if (UserEvent.isRightEvent(evtType))
            {
              selBounds.x = -100000; // -1
              selBounds.width = 1;
            }
            else if (UserEvent.isUpEvent(evtType))
              selBounds.y = 100000f;//getHeightf() + getTrueYf();
            else if (UserEvent.isDownEvent(evtType))
            {
              selBounds.y = -100000; // -1
              selBounds.height = 1;
            }
            minDist = Float.MAX_VALUE;
            minDist2 = Float.MAX_VALUE;
            for (int i = 0; i < focusKids.size(); i++)
            {
              ZPseudoComp currKid = (ZPseudoComp) focusKids.get(i);
              if (currKid.shouldTakeEvents())
              {
                java.awt.geom.Rectangle2D.Float kidBounds = currKid.getTrueBoundsf();
                // Make adjustments so edges don't perfectly align
                if (kidBounds.width > 0.02f && kidBounds.height > 0.02f)
                {
                  kidBounds.x += 0.01f;
                  kidBounds.y += 0.01f;
                  kidBounds.width -= 0.02f;
                  kidBounds.height -= 0.02f;
                }

                int currOut = selBounds.outcode(kidBounds.x + kidBounds.width/2,
                    kidBounds.y + kidBounds.height/2);
                if (UserEvent.isUpEvent(evtType))
                {
                  if ((currOut & java.awt.geom.Rectangle2D.OUT_TOP) == java.awt.geom.Rectangle2D.OUT_TOP)
                  {
                    float testDist = Math.abs(selBounds.y - (kidBounds.y + kidBounds.height));
                    float testDist2 = 0;
                    if (selBounds.getMinX() < kidBounds.getMaxX() &&
                        selBounds.getMaxX() > kidBounds.getMinX())
                      testDist2 = 0;
                    else
                    {
                      testDist2 = Math.min(Math.abs(selBounds.x - (kidBounds.x + kidBounds.width)),
                          Math.abs(selBounds.x + selBounds.width - kidBounds.x));
                      if (testDist2 == 0) // edge-aligned
                        testDist2 = 1;
                    }
                    if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                    {
                      minDist = testDist;
                      minDist2 = testDist2;
                      minDistNode = currKid;
                    }
                  }
                }
                else if (UserEvent.isRightEvent(evtType))
                {
                  if ((currOut & java.awt.geom.Rectangle2D.OUT_RIGHT) == java.awt.geom.Rectangle2D.OUT_RIGHT)
                  {
                    float testDist = Math.abs(selBounds.x + selBounds.width - kidBounds.x);
                    float testDist2 = 0;
                    if (selBounds.getMinY() < kidBounds.getMaxY() &&
                        selBounds.getMaxY() > kidBounds.getMinY())
                      testDist2 = 0;
                    else
                    {
                      testDist2 = Math.min(Math.abs(selBounds.y - (kidBounds.y + kidBounds.height)),
                          Math.abs(selBounds.y + selBounds.height - kidBounds.y));
                      if (testDist2 == 0) // edge-aligned
                        testDist2 = 1;
                    }
                    if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                    {
                      minDist = testDist;
                      minDist2 = testDist2;
                      minDistNode = currKid;
                    }
                  }
                }
                else if (UserEvent.isDownEvent(evtType))
                {
                  if ((currOut & java.awt.geom.Rectangle2D.OUT_BOTTOM) == java.awt.geom.Rectangle2D.OUT_BOTTOM)
                  {
                    float testDist = Math.abs(selBounds.y + selBounds.height - kidBounds.y);
                    float testDist2 = 0;
                    if (selBounds.getMinX() < kidBounds.getMaxX() &&
                        selBounds.getMaxX() > kidBounds.getMinX())
                      testDist2 = 0;
                    else
                    {
                      testDist2 = Math.min(Math.abs(selBounds.x - (kidBounds.x + kidBounds.width)),
                          Math.abs(selBounds.x + selBounds.width - kidBounds.x));
                      if (testDist2 == 0) // edge-aligned
                        testDist2 = 1;
                    }
                    if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                    {
                      minDist = testDist;
                      minDist2 = testDist2;
                      minDistNode = currKid;
                    }
                  }
                }
                else if (UserEvent.isLeftEvent(evtType))
                {
                  if ((currOut & java.awt.geom.Rectangle2D.OUT_LEFT) == java.awt.geom.Rectangle2D.OUT_LEFT)
                  {
                    float testDist = Math.abs(selBounds.x - (kidBounds.x + kidBounds.width));
                    float testDist2 = 0;
                    if (selBounds.getMinY() < kidBounds.getMaxY() &&
                        selBounds.getMaxY() > kidBounds.getMinY())
                      testDist2 = 0;
                    else
                    {
                      testDist2 = Math.min(Math.abs(selBounds.y - (kidBounds.y + kidBounds.height)),
                          Math.abs(selBounds.y + selBounds.height - kidBounds.y));
                      if (testDist2 == 0) // edge-aligned
                        testDist2 = 1;
                    }
                    if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
                    {
                      minDist = testDist;
                      minDist2 = testDist2;
                      minDistNode = currKid;
                    }
                  }
                }
              }
            }
          }
        }

        Pooler.returnPooledArrayList(focusKids);
        if (minDistNode != null && minDistNode != currSelNode)
        {
          selectNode(minDistNode);
          ZPseudoComp tempParent = minDistNode.getTopPseudoParent();
          tempParent.updateFocusTargetRect(evt.getType());
          // Check for wrap around table navigation and notify the table of it
          ZDataTable orgTabParent = (currSelNode instanceof ZPseudoComp) ? ((ZPseudoComp)currSelNode).getTableParent() : null;
          ZDataTable newTabParent = ((ZPseudoComp)minDistNode).getTableParent();
          if (orgTabParent != null && orgTabParent == newTabParent)
          {
            newTabParent.notifyOfTransition(evt.getType());
          }
          return true;
        }
      }
    }
    if (evt.isDirectionalType() || evtType == UserEvent.SCROLL_DOWN || evtType == UserEvent.SCROLL_LEFT || evtType == UserEvent.SCROLL_RIGHT ||
        evtType == UserEvent.SCROLL_UP)
    {
      if (scrolling != 0 && eventsAreOK)
      {
        // Check for scrolling commands
        if ((scrolling & ZDataTable.VERTICAL_DIMENSION) != 0)
        {
          if ((UserEvent.isUpEvent(evtType) || evtType == UserEvent.SCROLL_UP) && !isFirstVPage())
          {
            setScrollPosition(scrollPosX, scrollPosY - getScrollUnitAmount(), true, true);
            return true;
          }
          else if ((UserEvent.isDownEvent(evtType) || evtType == UserEvent.SCROLL_DOWN) && !isLastVPage())
          {
            setScrollPosition(scrollPosX, scrollPosY + getScrollUnitAmount(), true, false);
            return true;
          }
        }
        if ((scrolling & ZDataTable.HORIZONTAL_DIMENSION) != 0)
        {
          if ((UserEvent.isRightEvent(evtType) || evtType == UserEvent.SCROLL_RIGHT) && !isLastHPage())
          {
            setScrollPosition(scrollPosX + getScrollUnitAmount(), scrollPosY, true, false);
            return true;
          }
          else if ((UserEvent.isLeftEvent(evtType) || evtType == UserEvent.SCROLL_LEFT) && !isFirstHPage())
          {
            setScrollPosition(scrollPosX - getScrollUnitAmount(), scrollPosY, true, true);
            return true;
          }
        }
      }
      /*
       * NOTE:Also check scrolling siblings. This is necessary because if we create a scrolling
       * Panel we want to be able to put scrolling controls specific to that panel somewhere without
       * affecting the layout of that panel. So creating a parent panel to hold them both makes that
       * parent act like a scroll container.
       */
      for (int i = 0; parent != null && i < parent.numKids; i++)
      {
        ZPseudoComp currSib = (ZPseudoComp) parent.kids[i];
        if (currSib == this)
          continue;
        if (currSib.scrolling != 0 && currSib.shouldTakeEvents())
          return currSib.action(evt);
      }
    }
    if (uiMgr.isXBMCCompatible() && (eventsAreOK || !uiMgr.getCurrUI().hasPopup() || isTopPopup()))
    {
      if (processDirectEvents(evt))
        return true;
    }
    return propogateAction(evt);
  }

  protected ZPseudoComp getScrollingContainer(ZComp currSelNode, int dimensions, boolean includeTables)
  {
    do
    {
      if (includeTables && currSelNode instanceof ZDataTable)
      {
        ZDataTable tTest = (ZDataTable) currSelNode;
        if ((tTest.dimensions & dimensions) != 0 && (!tTest.isFirstVPage() || !tTest.isLastVPage() ||
            !tTest.isFirstHPage() || !tTest.isLastHPage()))
          return tTest;
      }
      if (currSelNode instanceof ZPseudoComp)
      {
        ZPseudoComp pTest = (ZPseudoComp) currSelNode;
        if ((pTest.scrolling & dimensions) != 0 && (!pTest.isFirstVPage() || !pTest.isLastVPage() ||
            !pTest.isFirstHPage() || !pTest.isLastHPage()))
          return pTest;
      }
      currSelNode = currSelNode.parent;
    } while (currSelNode != null);
    return null;
  }

  boolean passesConditional()
  {
    //if (conditionalUIWidg == null || parentActions == null)
    //	return true;
    //if (passesConditionalIsCached)
    return passesConditionalCacheValue;
    /*		checkForFocus = true;
		boolean res = false;
		try
		{
			java.util.HashSet widgTracker = new java.util.HashSet();
			Catbert.Context resMap = processParentActions(widgTracker);
			res = widgTracker.contains(conditionalUIWidg);
			//if (Sage.DBG) System.out.println("CondRes=" + res + " cond=" + testConditionalWidg + " br=" + testBranchWidg);
		}
		catch (Exception e)
		{
			System.out.println("Error invoking the method for: " + conditionalUIWidg + " of " + e);
			e.printStackTrace();
		}
		checkForFocus = false;
		passesConditionalIsCached = true;
		passesConditionalCacheValue = res;
		return res;
     */}

  public void actionPerformed(Object evt)
  {
    //check for a userevent trigger
    Object trigger = (compToActionMap == null) ? null : compToActionMap.get(evt);
    if (trigger == null) return;

    // NOTE: We really should see if it's OK to get events before we do this since
    // there could be something modal that blocks us (but we haven't had it that way so it may break some things in the UI if we change it)
    PseudoMenu currMenu = uiMgr.getCurrUI();
    boolean[] acquiredLock = new boolean[1];
    // NOTE: JK on 6/10/05 I thought of changing this to true, I don't know why it would be false
    // but that doesn't mean it should be changed. :)
    // NARFLEX - 2/11/10 - We definitely need to have the lock here; so there's no reason I can
    // see that this shouldn't be set to true, especially if the return value is ignored
    uiMgr.getLock(true, acquiredLock);
    try
    {
      String nodeSelSound = currMenu.itemSelectSound;
      if (nodeSelSound != null && nodeSelSound.length() > 0)
        uiMgr.playSound(nodeSelSound);
      lastSelectTime = Sage.eventTime();
      if (trigger instanceof Integer) // UserEvent
      {
        UserEvent ue = new UserEvent(uiMgr.getRouter().getEventCreationTime(), ((Integer) trigger).intValue(), -1);
        uiMgr.getRouter().updateLastEventTime();
        if (!action(ue))
        {
          PseudoMenu currUI = uiMgr.getCurrUI();
          //currUI.defaultEventAction(ue);
          currUI.action(ue);
        }
      }
      else if (trigger instanceof Widget)
      {
        Catbert.ExecutionPosition ep = processChain((Widget) trigger, relatedContext.createChild(), null, this, false);
        if (ep != null)
          ep.markSafe();
      }
    }
    finally
    {
      if (acquiredLock[0])
        uiMgr.clearLock();
    }
  }

  // Return null if the chain should continue execution, the ExecutionPosition object if it should terminate and append itself
  // to the stack trace
  public static Catbert.ExecutionPosition processChain(Widget trigger, Catbert.Context context, java.util.Set validChainLinks,
      final ZPseudoComp thisz, boolean renderShapes)
  {
    return processChain(trigger, context, validChainLinks, thisz, renderShapes, false, null);
  }
  public static Catbert.ExecutionPosition processChain(Widget trigger, Catbert.Context context, java.util.Set validChainLinks,
      final ZPseudoComp thisz, boolean renderShapes, boolean validateEffects)
  {
    return processChain(trigger, context, validChainLinks, thisz, renderShapes, validateEffects, null);
  }
  public static Catbert.ExecutionPosition processChain(Widget trigger, Catbert.Context context, java.util.Set validChainLinks,
      final ZPseudoComp thisz, boolean renderShapes, boolean validateEffects, java.util.Set firedWidgets)
  {
    if (trigger == null) return null;
    boolean processAgain = false;
    Catbert.ExecutionPosition ep = null;
    do
    {
      processAgain = false;
      //if (Sage.DBG) System.out.println("processChain trigger=" + trigger/* + " contextMap=" + contextMap*/);
      byte tt = trigger.type();
      if (tt == Widget.ACTION)
      {
        if (firedWidgets != null) firedWidgets.add(trigger);
        // At Actions, we invoke them and continue on to any contents they have, only the first is valid
        if (trigger.getName().equals("Fork()"))
        {
          if (!renderShapes && !validateEffects)
          {
            final Widget forky = trigger;
            // NOTE: NARFLEX - 2/19/10 - MAJOR BUG - Since Contexts use a HashMap internally
            // to store data; that information is not synchronized. So if multiple threads end up
            // modifying the structure of the map; null or improper values could be returned when not expected.
            // We've seen a couple cases of this related to the online video update time and never understood
            // how possibly the value could have been null. By creating a child context; any modifications
            // done to the map structure (i.e. new variables initialized) would then be contained in
            // that new child context which then is only used by this thread. We are still susceptible
            // to changes in the map structure by parents that could be running though. Resolving that issue
            // would require synchronizing the Context class which would have significant performance impacts
            // because of how heavily it is utilized. Instead what we did was override the HashMap
            // implementation used in Context so that it should now safely retrieve a proper value even
            // if the map is currently being re-hashed.
            final Catbert.Context fooContext = context.createChild();
            Pooler.execute(new Runnable()
            {
              public void run()
              {
                Catbert.ExecutionPosition ep = null;
                Widget[] actKids = forky.contents();
                for (int i = 0; i < actKids.length; i++)
                  if (actKids[i].isProcessChainType())
                    if ((ep = processChain(actKids[i], fooContext, null, thisz, false)) != null)
                    {
                      ep.addToStack(actKids[i]);
                      ep.addToStackFinal(forky);
                      break;
                    }
              }
            }, "Fork-" + forky.symbol());
          }
        }
        else
        {
          try
          {
            Object asyncTask = Catbert.evaluateExpression(trigger.getName(), context, thisz, trigger);
            if (asyncTask instanceof Catbert.AsyncTaskID && !renderShapes && !validateEffects)
            {
              // Returning true means that the async task is already done and we can just continue
              // on as normal
              if ((ep = Catbert.registerAsyncTaskInfo(thisz, trigger, context, asyncTask)) != null)
              {
                return ep;
              }
            }
            Widget[] actKids = trigger.contents();
            int actKidsToRun = 0;
            for (int i = 0; i < actKids.length; i++)
            {
              if (((!renderShapes && !validateEffects && actKids[i].isProcessChainType()) ||
                  (renderShapes && actKids[i].isInShapeHierarchy()) ||
                  (validateEffects && actKids[i].isInEffectHierarchy()))
                  && (validChainLinks == null || validChainLinks.contains(actKids[i])))
              {
                actKidsToRun++;
              }
            }
            for (int i = 0; i < actKids.length; i++)
            {
              Widget currActKid = actKids[i];
              if (((!renderShapes && !validateEffects && currActKid.isProcessChainType()) ||
                  (renderShapes && currActKid.isInShapeHierarchy()) ||
                  (validateEffects && currActKid.isInEffectHierarchy()))
                  && (validChainLinks == null || validChainLinks.contains(currActKid)))
              {
                actKidsToRun--;
                if (actKidsToRun == 0)
                {
                  // Don't recurse because we don't need to
                  processAgain = true;
                  trigger = currActKid;
                }
                else
                {
                  if ((ep = processChain(currActKid, context, validChainLinks, thisz, renderShapes, validateEffects,
                      firedWidgets)) != null)
                  {
                    ep.addToStack(currActKid);
                    ep.addToStack(trigger);
                    return ep;
                  }
                }
              }
            }
          }
          catch (Exception e)
          {
            System.out.println("Error invoking the method for: " + trigger + " of " + e);
            e.printStackTrace();
          }
        }
      }
      else if (tt == Widget.CONDITIONAL)
      {
        try
        {
          String triggerName = trigger.getName();
          Object goodBranchVal = Catbert.evaluateExpression(triggerName, context, thisz, trigger);
          if (goodBranchVal instanceof Catbert.AsyncTaskID && !renderShapes && !validateEffects)
          {
            if ((ep = Catbert.registerAsyncTaskInfo(thisz, trigger, context, goodBranchVal)) != null)
            {
              return ep;
            }
          }
          Widget[] actKids = trigger.contents(Widget.BRANCH);
          Widget elseBranch = null;
          boolean disableElse = false;
          for (int i = 0; i < actKids.length; i++)
          {
            if (validChainLinks != null && !validChainLinks.contains(actKids[i]))
              continue;
            String brStr = actKids[i].getName();
            if (brStr.equals("else"))
            {
              elseBranch = actKids[i];
              break;
            }
          }
          // If there's an else branch then we need to test all of the branches regardless of
          // them being in the valid set; otherwise the else condition isn't being correctly tested

          // If there's branches then we consider this conditional to be fired
          if (firedWidgets != null && actKids.length > 0) firedWidgets.add(trigger);
          for (int i = 0; i < actKids.length; i++)
          {
            if (elseBranch == null && validChainLinks != null && !validChainLinks.contains(actKids[i]))
              continue;
            if (actKids[i] == elseBranch)
              continue;
            String brStr = actKids[i].getName();
            Object brEval = Catbert.evaluateExpression(brStr, context, thisz, actKids[i]);
            if (testBranchValue(goodBranchVal, brEval))
            {
              // Branch passed
              if (firedWidgets != null) firedWidgets.add(actKids[i]);
              disableElse = true;
              // We can continue on this case now; we just needed to set the else as being false
              if (validChainLinks != null && !validChainLinks.contains(actKids[i]))
                continue;
              Widget[] branchKids = actKids[i].contents();
              int passedBranchKids = 0;
              for (int j = 0; j < branchKids.length; j++)
              {
                if (((!renderShapes && !validateEffects && branchKids[j].isProcessChainType()) ||
                    (renderShapes && branchKids[j].isInShapeHierarchy()) ||
                    (validateEffects && branchKids[j].isInEffectHierarchy())) && (validChainLinks == null ||
                    validChainLinks.contains(branchKids[j])))
                {
                  passedBranchKids++;
                }
              }
              for (int j = 0; j < branchKids.length; j++)
              {
                Widget currBranchKid = branchKids[j];
                if (((!renderShapes && !validateEffects && currBranchKid.isProcessChainType()) ||
                    (renderShapes && currBranchKid.isInShapeHierarchy()) ||
                    (validateEffects && currBranchKid.isInEffectHierarchy())) && (validChainLinks == null ||
                    validChainLinks.contains(currBranchKid)))
                {
                  passedBranchKids--;
                  if (passedBranchKids == 0)
                  {
                    // last one, don't recurse
                    processAgain = true;
                    trigger = currBranchKid;
                  }
                  else
                  {
                    if ((ep = processChain(currBranchKid, context, validChainLinks, thisz, renderShapes, validateEffects,
                        firedWidgets)) != null)
                    {
                      ep.addToStack(currBranchKid);
                      ep.addToStack(actKids[i]);
                      ep.addToStack(trigger);
                      return ep;
                    }
                  }
                }
              }
              // early termination of if statements, this also presents the evaluation
              // result from the Branch of overriding the default return for the processChain
              // we just called
              break;
            }
          }
          if (actKids.length == 0 && Catbert.evalBool(goodBranchVal))
            elseBranch = trigger; // no branch, but true continues execution past the conditional
          if (!disableElse && elseBranch != null)
          {
            // Covers the else branch and the single conditional
            if (firedWidgets != null) firedWidgets.add(elseBranch);
            Widget[] branchKids = elseBranch.contents();
            int passedBranchKids = 0;
            for (int j = 0; j < branchKids.length; j++)
            {
              if (((!renderShapes && !validateEffects && branchKids[j].isProcessChainType()) ||
                  (renderShapes && branchKids[j].isInShapeHierarchy()) ||
                  (validateEffects && branchKids[j].isInEffectHierarchy())) && (validChainLinks == null ||
                  validChainLinks.contains(branchKids[j])))
              {
                passedBranchKids++;
              }
            }
            for (int j = 0; j < branchKids.length; j++)
            {
              Widget currBranchKid = branchKids[j];
              if (((!renderShapes && !validateEffects && currBranchKid.isProcessChainType()) ||
                  (renderShapes && currBranchKid.isInShapeHierarchy()) ||
                  (validateEffects && currBranchKid.isInEffectHierarchy())) && (validChainLinks == null ||
                  validChainLinks.contains(currBranchKid)))
              {
                passedBranchKids--;
                if (passedBranchKids == 0)
                {
                  // last one, don't recurse
                  processAgain = true;
                  trigger = currBranchKid;
                }
                else
                {
                  if ((ep = processChain(currBranchKid, context, validChainLinks, thisz, renderShapes, validateEffects,
                      firedWidgets)) != null)
                  {
                    ep.addToStack(currBranchKid);
                    ep.addToStack(elseBranch);
                    ep.addToStack(trigger);
                    return ep;
                  }
                }
              }
            }
          }
        }
        catch (Exception e)
        {
          System.out.println("Error invoking the method for: " + trigger + " of " + e);
          e.printStackTrace();
        }
      }
      else if (tt == Widget.OPTIONSMENU && !renderShapes && !validateEffects)
      {
        // Always stop the process chain after launching an options menu
        return processOptionsMenu(trigger, context, thisz);
      }
      else if (tt == Widget.MENU && !renderShapes && !validateEffects)
      {
        if (context.getUIMgr() != null)
          context.getUIMgr().advanceUI(trigger);
        else
          System.out.println("NO UI context established for menu jump to:" + trigger);
      }
      else if (tt == Widget.SHAPE && renderShapes && thisz != null)
      {
        thisz.drawShape(trigger, context);
      }
      else if (tt == Widget.EFFECT && validateEffects && thisz != null)
      {
        //				if (thisz.isEffectValid(trigger))
        thisz.processEffectsWidget(trigger, context);
      }
    } while (processAgain);
    return null;
  }

  private static Catbert.ExecutionPosition processOptionsMenu(Widget optionsMenu, Catbert.Context context, ZPseudoComp thisz)
  {
    if (Sage.DBG) System.out.println("processOptionsMenu optionsMenu=" + optionsMenu/* + " context=" + context*/);

    if (context.getUIMgr() != null && context.getUIMgr().getTracer() != null) context.getUIMgr().getTracer().traceOptionsMenu(optionsMenu);

    ZPseudoComp optComp = new ZPseudoComp(optionsMenu, thisz != null ? thisz.defaultThemes : null, context);
    optComp.childWidgetIndex = Integer.MAX_VALUE;
    // This is just like showing a new menu with the PseudoMenu
    if (context.getUIMgr() != null) {
      // Reset the timers as well when we launch a popup because it may happen right before we hit
      // the screen saver timeout and use the 'TV' command to jump back to full screen video if
      // the timings align properly. Such as it's been 19:55 since last activity and they are in the UI, then the channel change
      // prompt comes up...and then the system issues the Exit/TV command to jump back to full screen video but instead
      // ends up closing the popup menu. (I actually saw this happen)
      context.getUIMgr().getRouter().resetInactivityTimers();
      return context.getUIMgr().addPopup(optComp, context);
    }
    else
    {
      System.out.println("NO UI Context established for popup showing!");
      return null;
    }
  }

  protected static boolean wasWidgetParentFired(Widget w, java.util.Set firedWidgs)
  {
    Widget[] ps = w.containers();
    for (int i = 0; i < ps.length; i++)
      if (firedWidgs.contains(ps[i]))
        return true;
    return false;
  }

  /*
   * This is used to run the Actions that may contain a UI element. It does
   * the dynamic data population
   */
  protected boolean evaluate(boolean doComps, boolean doData)
  {
    // Because the dynamic input might be conditional we have to run this for both comps & data
    boolean allowHiddenFocus = Catbert.evalBool(relatedContext.getLocal("AllowHiddenFocus"));
    try
    {
      if (!doData)
      {
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.PRE_EVALUATE_COMPONENT_UI, this, widg, null);
        // Just do conditional UI
        // We only need to do this if we have dynamic data. That's determined by whether or
        // not we're contained by an Action
        passesConditionalCacheValue = true;
        if (parentActionsMayBeConditional && parentActions != null &&
            widgType != Widget.TABLE && widgType != Widget.TABLECOMPONENT)
        {
          if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.PRE_CONDITIONAL_UI, this, widg, null);
          checkForFocus = true;
          java.util.HashSet firedWidgets = Pooler.getPooledHashSet();
          Catbert.Context resMap = processParentActions(firedWidgets);
          if (!wasWidgetParentFired(widg, firedWidgets))
          {
            if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_CONDITIONAL_UI, this, widg, Boolean.FALSE);
            if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_EVALUATE_COMPONENT_UI, this, widg, null);
            Pooler.returnPooledHashSet(firedWidgets);
            checkForFocus = false;
            return (passesConditionalCacheValue = false) || allowHiddenFocus;
          }
          Pooler.returnPooledHashSet(firedWidgets);
          if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_CONDITIONAL_UI, this, widg, Boolean.TRUE);
          checkForFocus = false;
        }
        else if (parentActions != null &&
            widgType != Widget.TABLE && widgType != Widget.TABLECOMPONENT &&
            numKids > 0)
        {
          // If we've got parent actions AND we have children, but it's not conditional actions;
          // then we should execute them anyways because they may end up setting attributes or having other
          // side effects intended by the developer.
          checkForFocus = true;
          java.util.HashSet firedWidgets = Pooler.getPooledHashSet();
          processParentActions(firedWidgets);
          Pooler.returnPooledHashSet(firedWidgets);
          if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_CONDITIONAL_UI, this, widg, Boolean.TRUE);
          checkForFocus = false;
        }
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_EVALUATE_COMPONENT_UI, this, widg, null);
      }
      else
      {
        Object resVal = null;
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.PRE_EVALUATE_DATA_UI, this, widg, null);
        // We only need to do this if we have dynamic data. That's determined by whether or
        // not we're contained by an Action
        passesConditionalCacheValue = true;
        if (parentActions != null && widgType != Widget.TABLE && widgType != Widget.TABLECOMPONENT)
        {
          if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.PRE_CONDITIONAL_UI, this, widg, null);
          try
          {
            checkForFocus = true;
            java.util.HashSet firedWidgets = Pooler.getPooledHashSet();
            if (conditionalUIWidg == null && (widgType == Widget.TEXT || widgType == Widget.TEXTINPUT))
            {
              Catbert.Context chainRes = processParentActions(firedWidgets);
              if (!wasWidgetParentFired(widg, firedWidgets))
              {
                if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_CONDITIONAL_UI, this, widg, Boolean.FALSE);
                if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_EVALUATE_DATA_UI, this, widg, null);
                Pooler.returnPooledHashSet(firedWidgets);
                return (passesConditionalCacheValue = false) || allowHiddenFocus;
              }
              resVal = chainRes.safeLookup(null);
              if (resVal != null)
              {
                if (((ZLabel) kids[0]).setText(resVal.toString()))
                  cachedPrefParentHeight = cachedPrefParentWidth = -1;
              }
            }
            else if (conditionalUIWidg == null && widgType == Widget.IMAGE)
            {
              // NARFLEX - 6/1/09 - We need to get this lock BEFORE we do the parent actions; which would include the
              // call to GetThumbnail(); otherwise when it calls back into us to check to see if the load is needed we would not
              // have the MetaImage set inside the ZImage object yet and the load needed would say false.
              synchronized (metaImageCallbackLock)
              {
                Catbert.Context chainRes = processParentActions(firedWidgets);
                if (!wasWidgetParentFired(widg, firedWidgets))
                {
                  if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_CONDITIONAL_UI, this, widg, Boolean.FALSE);
                  if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_EVALUATE_DATA_UI, this, widg, null);
                  Pooler.returnPooledHashSet(firedWidgets);
                  return (passesConditionalCacheValue = false) || allowHiddenFocus;
                }
                resVal = chainRes.safeLookup(null);
                ZImage kidImage = (ZImage) kids[0];
                if (resVal instanceof MetaImage)
                {
                  if (kidImage.isBgLoader() && !(resVal instanceof MetaImage.Waiter))
                    kidImage.setImage(uiMgr.getBGLoader().getMetaImageFast((MetaImage) resVal, this, null));
                  else
                    kidImage.setImage((MetaImage) resVal);
                }
                else if (resVal instanceof java.awt.Image)
                {
                  java.awt.Image ima = (java.awt.Image) resVal;
                  //if (Sage.DBG) System.out.println("DynImage=" + ima + " w=" + ima.getWidth(null) + " h=" + ima.getHeight(null));
                  if (kidImage.isBgLoader())
                    kidImage.setImage(uiMgr.getBGLoader().getMetaImageFast(resVal, this, null));
                  else
                    kidImage.setImage(MetaImage.getMetaImage(ima));
                }
                else if (resVal instanceof MediaFile && ((MediaFile) resVal).isPicture())
                {
                  if (kidImage.isBgLoader())
                    kidImage.setImage(uiMgr.getBGLoader().getMetaImageFast(resVal, this, null));
                  else
                    kidImage.setImage(MetaImage.getMetaImage((MediaFile) resVal));
                }
                else if (resVal instanceof java.io.File)
                {
                  if (kidImage.isBgLoader())
                    kidImage.setImage(uiMgr.getBGLoader().getMetaImageFast(resVal, this, null));
                  else
                    kidImage.setImage(MetaImage.getMetaImage((java.io.File) resVal));
                }
                else
                {
                  if (resVal != null)
                  {
                    if (kidImage.isBgLoader())
                      kidImage.setImage(uiMgr.getBGLoader().getMetaImageFast(resVal.toString(), this, null));
                    else
                      kidImage.setImage(MetaImage.getMetaImage(resVal.toString(), this));
                  }
                  else
                    kidImage.setImage(null); // be sure to clear it in case of component re-use
                  /*							resVal = null;
									// Check all of the values for an Image instance
									while (chainRes != null)
									{
										java.util.Map currMap = chainRes.getMap();
										if (currMap != null)
										{
											java.util.Iterator walker = currMap.values().iterator();
											while (walker.hasNext())
											{
												Object obj = walker.next();
												if (obj instanceof MetaImage)
												{
													kidImage.setImage((MetaImage) obj);
													resVal = obj;
													chainRes = null;
													break;
												}
												else if (obj instanceof java.awt.Image)
												{
													java.awt.Image ima = (java.awt.Image) obj;
													//if (Sage.DBG) System.out.println("DynImage=" + ima + " w=" + ima.getWidth(null) + " h=" + ima.getHeight(null));
													kidImage.setImage(MetaImage.getMetaImage(ima));
													resVal = obj;
													chainRes = null;
													break;
												}
												else if (obj instanceof MediaFile && ((MediaFile) obj).isPicture())
												{
													kidImage.setImage(MetaImage.getMetaImage((MediaFile) obj));
													chainRes = null;
													resVal = obj;
													break;
												}
											}
										}
										if (chainRes != null)
											chainRes = chainRes.getParent();
									}*/
                }
              }
            }
            else // general conditional UI
            {
              Catbert.Context resMap = processParentActions(firedWidgets);
              if (!wasWidgetParentFired(widg, firedWidgets))
              {
                if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_CONDITIONAL_UI, this, widg, Boolean.FALSE);
                if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_EVALUATE_DATA_UI, this, widg, null);
                Pooler.returnPooledHashSet(firedWidgets);
                return (passesConditionalCacheValue = false) || allowHiddenFocus;
              }
            }
            Pooler.returnPooledHashSet(firedWidgets);
          }
          finally
          {
            checkForFocus = false;
          }
          if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_CONDITIONAL_UI, this, widg, Boolean.TRUE);
        }
        if (widgType == Widget.TEXTINPUT && (conditionalUIWidg != null || parentActions == null))
        {
          // Text input can get its data from an attribute if its doesn't have a dynamic parent
          resVal = relatedContext.safeLookup(widg.getName());
          if (resVal != null)
          {
            if (((ZLabel) kids[0]).setText(resVal.toString()))
              cachedPrefParentHeight = cachedPrefParentWidth = -1;
          }
        }
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_EVALUATE_DATA_UI, this, widg, resVal);
      }
    }
    finally
    {
      if (passesConditionalCacheValue || allowHiddenFocus)
      {
        //if (!passesConditionalCacheValue)System.out.println("EXTRA EVAL OF widg=" + widg + " sym=" + widg.symbol());
        for (int i = 0; i < numKids; i++)
        {
          kids[i].evaluateTree(doComps, doData);
        }
      }
    }
    return true;
  }

  public boolean checkForcedFocus() // returns true if it took the focus
  {
    if (!passesConditional())
      return false;
    if (isFocusable() && Catbert.evalBool(relatedContext.safeLookup("DefaultFocus")))
    {
      ZPseudoComp topParent = getTopPseudoParent();
      topParent.setFocus(this);
      topParent.updateFocusTargetRect(0);
      return true;
    }
    for (int i = 0; i < numKids; i++)
    {
      if (kids[i] instanceof ZPseudoComp && ((ZPseudoComp)kids[i]).checkForcedFocus())
        return true;
    }
    return false;
  }

  public boolean setDefaultFocus() // returns true if it took the focus
  {
    if (!passesConditional() && (!Catbert.evalBool(relatedContext.getLocal("AllowHiddenFocus"))))
      return false;
    if (focusTargetRect != null)
    {
      renderWithTrueFocusRect(focusTargetRect);
      return true;
    }
    if (isFocusable())
    {
      ZPseudoComp topParent = getTopPseudoParent();
      topParent.setFocus(this);
      topParent.updateFocusTargetRect(0);
      return true;
    }
    for (int i = 0; i < numKids; i++)
    {
      if (kids[i].setDefaultFocus())
        return true;
    }
    return false;
  }

  public void setFocusListenState(int x) { if (checkForFocus) focusListener = focusListener | x; }
  public void setPagingListenState(boolean x) { if (checkForFocus) pagingListener = x; }
  public void setNextTransitionListen()
  {
    if (checkForFocus)
      nextTransitionListener = true;
  }
  public void setPrevTransitionListen()
  {
    if (checkForFocus)
      prevTransitionListener = true;
  }

  protected Catbert.Context processParentActions(java.util.Set firedWidgets)
  {
    //System.out.println("processParentActions(" + fromMe + ")");
    if (parentActions == null) return relatedContext.createChild();
    //		java.util.Set parentActions = new java.util.HashSet();
    //		Widget rootAction = getParentActionSet(fromMe, parentActions, uiParent);

    //System.out.println("parentActions=" + parentActions + " rootAction=" + rootAction);
    // NOTE NOTE NOTE 11/11/03 - why do I want to create a new child context
    // when executing parent actions??
    // NOTE 4/28/04 I DON'T, doing this was causing gaps in the hierarchy for tables children
    // so I'm disabling it.
    // NOTE: 4/29/04 But then there's an issue with GetFocusContext in that it retains the
    // values from the last call which is undesired. That's why we have to create a child context.
    // The rule is that any variables created in parent action chains won't be accessible by other components
    // unless its declared as an attribute in the hierarchy.
    Catbert.Context rv = relatedContext.createChild();
    rv.setLocal(null, null);
    //		if (rootAction != null)
    {
      if (Sage.PERF_ANALYSIS)
        perfTime = Sage.time();
      Catbert.ExecutionPosition ep = processChain(rootParentAction, rv, parentActions, this, false, false, firedWidgets);
      if (Sage.PERF_ANALYSIS)
      {
        perfTime = Sage.time() - perfTime;
        if (perfTime > Sage.EVALUATE_THRESHOLD_TIME)
        {
          System.out.println("EXEC PARENTS PERF time=" + perfTime + " widg=" + widg);
        }
      }
      if (ep != null)
        System.out.println("ERROR: Async method call made in parent data UI execution!!!! widg=" + widg);
    }
    //		else
    {
      //			if (Sage.DBG) System.out.println("NULL ROOT ACTION from widget:" + fromMe + " parentSet=" + parentActions);
    }
    //System.out.println("processParentActions returns " + rv);
    return rv;
  }
  /*	protected Widget getParentActionSet(Widget fromMe, java.util.Set validChainLinks, ZPseudoComp uiParent)
	{
		// Avoid redundantly doing unnecessary parent calculations
		if (!validChainLinks.add(fromMe)) return null;
		Widget[] allParents = fromMe.containers();
		Widget theRoot = null;
		boolean foundParents = false;
		for (int i = 0; i < allParents.length; i++)
		{
			if (allParents[i].widgetType == Widget.ACTION)
			{
				foundParents = true;
				Widget testRoot = getParentActionSet(allParents[i], validChainLinks, uiParent);
				if (testRoot != null)
					theRoot = testRoot;
			}
		}
		for (int i = 0; i < allParents.length; i++)
		{
			if (allParents[i].widgetType == Widget.BRANCH && allParents[i] != branchWidg)
			{
				foundParents = true;
				if (validChainLinks.add(allParents[i]))
				{
					Widget[] branchParentConds = allParents[i].containers();
					for (int j = 0; j < branchParentConds.length; j++)
					{
						if (branchParentConds[j].widgetType == Widget.CONDITIONAL)
						{
							Widget testRoot = getParentActionSet(branchParentConds[j], validChainLinks, uiParent);
							if (testRoot != null)
								theRoot = testRoot;
						}
					}
				}
			}
		}
		for (int i = 0; i < allParents.length; i++)
		{
			if (allParents[i].widgetType == Widget.CONDITIONAL && (branchWidg == null ||
				conditionalWidg != allParents[i]))
			{
				foundParents = true;
				Widget testRoot = getParentActionSet(allParents[i], validChainLinks, uiParent);
				if (testRoot != null)
					theRoot = testRoot;
			}
		}

		Widget rv = !foundParents ? fromMe : theRoot;
		if (rv != null && uiParent != null && !uiParent.widg.contains(rv) &&
			!uiParent.propWidg.contains(rv))
			rv = null;
		return rv;
	}
   */

  private boolean isEffectValid(EffectTracker currTracker)
  {
    int trigger = currTracker.getTrigger();
    // Hide delayed effects for now
    //		if (nextEffect.getIntProperty(Widget.DELAY, 0, null, this) >= 1600)
    //			return false;
    // conditional w/ no parent is the same as static
    if (trigger == EffectTracker.TRIGGER_FOCUSED || trigger == EffectTracker.TRIGGER_FOCUSTRACKER)
    {
      if (currRenderVisibility && (doesAncestorOrMeHaveFocus() || (uiMgr.isXBMCCompatible() && doesHierarchyHaveFocus())))
        return true;
    }
    else if (trigger == EffectTracker.TRIGGER_UNFOCUSED)
    {
      if (isInFocusableHierarchy() && !doesAncestorOrMeHaveFocus() && (!uiMgr.isXBMCCompatible() || !doesHierarchyHaveFocus()))
        return true;
    }
    else if (trigger == EffectTracker.TRIGGER_MENULOADED)
    {
      // This is false if this effect wasn't visible when the menu was first shown
      if (currRenderVisibility && (menuLoadedState || (currTracker != null && currTracker.isPositive())))
        return true;
    }
    else if (trigger == EffectTracker.TRIGGER_MENUUNLOADED)
    {
      setHasMenuUnloadEffects();
      if (currRenderVisibility && (menuUnloadedState || (currTracker != null && currTracker.isActive())))
        return true;
    }
    else if (trigger == EffectTracker.TRIGGER_SHOWN || trigger == EffectTracker.TRIGGER_VISIBLECHANGE || trigger == EffectTracker.TRIGGER_SMOOTHTRACKER)
    {
      return currRenderVisibility && boundsf.width > 0 && boundsf.height > 0;
    }
    else if (trigger == EffectTracker.TRIGGER_HIDDEN)
    {
      return boundsf.width == 0 || boundsf.height == 0 || !currRenderVisibility;
    }
    else if (trigger == EffectTracker.TRIGGER_ITEMSELECTED)
    {
      // Find the proper lastSelectTime to use
      ZPseudoComp testMe = this;
      while (testMe != null)
      {
        if (testMe.widgType == Widget.ITEM)
        {
          lastSelectTime = testMe.lastSelectTime;
          break;
        }
        if (testMe.parent instanceof ZPseudoComp)
          testMe = (ZPseudoComp) testMe.parent;
        else
          testMe = null;
      }
      return Sage.eventTime() - lastSelectTime < currTracker.getCompleteDuration();
    }
    else //if (Widget.STATIC_EFFECT.equals(effectType) || Widget.CONDITIONAL_EFFECT.equals(effectType))
      return true; //currRenderVisibility;
    return false;
  }

  // Returns true if there's latent effects which should be processed underneath here
  protected boolean processHideEffects(boolean validRegion)
  {
    if (effectTrackerMap != null && validRegion)
    {
      currRenderVisibility = isVisible() && passesUpwardConditional();
      java.util.Iterator walker = effectTrackerMap.values().iterator();
      while (walker.hasNext())
      {
        EffectTracker currTracker = (EffectTracker) walker.next();
        if (currTracker.isForceful())
          return true;
        int currType = currTracker.getTrigger();
        if (currType == EffectTracker.TRIGGER_HIDDEN || currType == EffectTracker.TRIGGER_UNFOCUSED)
        {
          if (/*Widget.FOCUSLOST_EFFECT.equals(currType) &&*/ ((!currTracker.isPositive() && isEffectValid(currTracker)) || currTracker.isActive()))
            return true;
          currTracker.setPositivity(true);
          currTracker.processEffectState(0);
        }
        else if (currType == EffectTracker.TRIGGER_SHOWN || currType == EffectTracker.TRIGGER_VISIBLECHANGE || currType == EffectTracker.TRIGGER_FOCUSED)
        {
          currTracker.setPositivity(false);
          currTracker.processEffectState(0);
        }
      }
    }
    for (int i = 0; i < numKids; i++)
    {
      if (kids[i].processHideEffects(validRegion))
        return true;
    }
    return false;
  }

  private void processEffectsWidget(Widget currEffect, Catbert.Context effectContext)
  {
    float scaledFullHeight = 0;
    float scaledFullWidth = 0;
    int baseScalingHeight = 0;
    int baseScalingWidth = 0;
    if (DEBUG_EFFECTS)
      System.out.println("Processing " + currEffect + " for " + widg);
    if (uiMgr.isXBMCCompatible())
    {
      scaledFullHeight = reality.getRoot().getHeight() * uiMgr.getOverscanScaleHeight();
      scaledFullWidth = reality.getRoot().getWidth() * uiMgr.getOverscanScaleWidth();
      baseScalingHeight = reality.getUIMgr().getInt("ui/scaling_insets_base_height", 480);
      baseScalingWidth = reality.getUIMgr().getInt("ui/scaling_insets_base_width", 720);
    }
    EffectTracker currTracker = null;
    if (effectTrackerMap != null)
      currTracker = (EffectTracker) effectTrackerMap.get(currEffect);
    boolean newTracker = recalcAllEffectStates;
    if (currTracker == null)
    {
      // Create the new tracker object and put it in the map
      if (effectTrackerMap == null)
        effectTrackerMap = new java.util.HashMap();
      currTracker = new EffectTracker(currEffect, this, effectContext);
      effectTrackerMap.put(currEffect, currTracker);
      newTracker = true;
    }
    else
      currTracker.updateEffectPropsFromWidget(effectContext);
    if (DEBUG_EFFECTS)
      System.out.println("Tracker=" + currTracker);
    remainingEffects.remove(currTracker);
    currTracker.setDisabled(false);
    currTracker.setClipped(currEffect.getBooleanProperty(Widget.CLIPPED, null, this));
    boolean valid = isEffectValid(currTracker);
    if (currTracker.isTracking())
    {
      if (currTracker.getTrigger() == EffectTracker.TRIGGER_FOCUSTRACKER)
        hasFocusTracker = true;
      EffectTracker newTrackerObj = reality.handleTrackerEffect(currTracker, valid);
      if (newTrackerObj != currTracker)
      {
        currTracker = newTrackerObj;
        effectTrackerMap.put(currEffect, newTrackerObj);
      }
      if (!currTracker.isNoop())
        trackersToAdd.add(currTracker);
      return;
    }
    // Set the intial state for the tracker so that it handles the transitions properly
    if (menuLoadedState)
    {
      if (valid && currTracker.getTrigger() == EffectTracker.TRIGGER_MENULOADED)
      {
        currTracker.setInitialPositivity(false);
      }
      else
        currTracker.setInitialPositivity(valid);
    }
    else if (newTracker && currTracker.getTrigger() == EffectTracker.TRIGGER_UNFOCUSED && valid)
      currTracker.setInitialPositivity(true);
    // We need to reset the positivity on an item select tracker if it's been re-selected
    if (currTracker.getTrigger() == EffectTracker.TRIGGER_ITEMSELECTED && valid &&
        currTracker.getStartTime() < lastSelectTime)
      currTracker.setPositivity(false);
    currTracker.setPositivity(valid);
    if (!valid && currTracker.isActive() && currTracker.isReversible())
    {
      if (currTracker.getTrigger() == EffectTracker.TRIGGER_SHOWN || currTracker.getTrigger() == EffectTracker.TRIGGER_VISIBLECHANGE)
        killHideEffects = true;
      else if (currTracker.getTrigger() == EffectTracker.TRIGGER_FOCUSED)
        killUnfocusEffects = true;
    }
    // These values may change as a result of the component's size changing; or the screen size changing;
    // so recalculate them here (DEFINITELY CAN OPTIMIZE LATER)
    boolean sizeChange;
    if (newTracker || currTracker.virgin)
    {
      sizeChange = true;
      if (currTracker.virgin)
        newTracker = true;
      currTracker.virgin = false;
    }
    else
    {
      sizeChange = reality.didRootSizeChange() || currTracker.lastWidthCalc != getWidthf() || currTracker.lastHeightCalc != getHeightf();
    }
    currTracker.lastWidthCalc = getWidthf();
    currTracker.lastHeightCalc = getHeightf();
    if (currEffect.hasAnyCacheMask(Widget.HAS_TRANSLATE_PROPS) && (sizeChange || currEffect.hasAnyCacheMask(Widget.HAS_DYNAMIC_TRANSLATE_PROPS)))
    {
      boolean menuRelOffsets = currEffect.getBooleanProperty(Widget.MENU_RELATIVE_OFFSETS, effectContext, this);
      float transX = 0;
      float transY = 0;
      float startTransX = 0;
      float startTransY = 0;
      Number rTransX = currEffect.getNumericProperty(Widget.ANCHOR_X, effectContext, this);
      Number rTransY = currEffect.getNumericProperty(Widget.ANCHOR_Y, effectContext, this);
      Number rStartTransX = currEffect.getNumericProperty(Widget.START_RENDER_OFFSET_X, effectContext, this);
      Number rStartTransY = currEffect.getNumericProperty(Widget.START_RENDER_OFFSET_Y, effectContext, this);
      if (rTransX != null)
      {
        if (rTransX instanceof Integer)
        {
          transX = rTransX.intValue();
          if (uiMgr.isXBMCCompatible())
            transX = Math.round(transX * scaledFullWidth / baseScalingWidth);
        }
        else if (menuRelOffsets)
          transX = rTransX.floatValue() * uiMgr.getRootPanel().getWidth();
        else
          transX = rTransX.floatValue() * getWidthf();
      }
      if (rTransY != null)
      {
        if (rTransY instanceof Integer)
        {
          transY = rTransY.intValue();
          if (uiMgr.isXBMCCompatible())
            transY = Math.round(transY * scaledFullHeight / baseScalingHeight);
        }
        else if (menuRelOffsets)
          transY = rTransY.floatValue() * uiMgr.getRootPanel().getHeight();
        else
          transY = rTransY.floatValue() * getHeightf();
      }
      if (rStartTransX != null)
      {
        if (rStartTransX instanceof Integer)
        {
          startTransX = rStartTransX.intValue();
          if (uiMgr.isXBMCCompatible())
            startTransX = Math.round(startTransX * scaledFullWidth / baseScalingWidth);
        }
        else if (menuRelOffsets)
          startTransX = rStartTransX.floatValue() * uiMgr.getRootPanel().getWidth();
        else
          startTransX = rStartTransX.floatValue() * getWidthf();
      }
      if (rStartTransY != null)
      {
        if (rStartTransY instanceof Integer)
        {
          startTransY = rStartTransY.intValue();
          if (uiMgr.isXBMCCompatible())
            startTransY = Math.round(startTransY * scaledFullHeight / baseScalingHeight);
        }
        else if (menuRelOffsets)
          startTransY = rStartTransY.floatValue() * uiMgr.getRootPanel().getHeight();
        else
          startTransY = rStartTransY.floatValue() * getHeightf();
      }
      currTracker.setTranslationEffect(startTransX, startTransY, transX, transY);
    }

    if (currEffect.hasAnyCacheMask(Widget.HAS_ALPHA_PROPS) && (newTracker || currEffect.hasAnyCacheMask(Widget.HAS_DYNAMIC_ALPHA_PROPS)))
    {
      currTracker.setFadeEffect(currEffect.getFloatProperty(Widget.FOREGROUND_ALPHA, 1.0f, effectContext, this),
          currEffect.getFloatProperty(Widget.BACKGROUND_ALPHA, 1.0f, effectContext, this));
    }

    if (currEffect.hasAnyCacheMask(Widget.HAS_CENTER_DEPENDENT_PROPS) && (sizeChange || currEffect.hasAnyCacheMask(Widget.HAS_DYNAMIC_CENTER_DEPENDENT_PROPS)))
    {
      float centerX, centerY;
      Number centerXObj = currEffect.getNumericProperty(Widget.ANCHOR_POINT_X, effectContext, this);
      Number centerYObj = currEffect.getNumericProperty(Widget.ANCHOR_POINT_Y, effectContext, this);
      if (centerXObj instanceof Integer)
      {
        centerX = centerXObj.intValue();
        if (uiMgr.isXBMCCompatible())
          centerX = Math.round(centerX * scaledFullWidth / baseScalingWidth);
      }
      else if (centerXObj != null)
        centerX = centerXObj.floatValue() * getWidthf();
      else
        centerX = getWidthf()/2;
      if (centerYObj instanceof Integer)
      {
        centerY = centerYObj.intValue();
        if (uiMgr.isXBMCCompatible())
          centerY = Math.round(centerY * scaledFullHeight / baseScalingHeight);
      }
      else if (centerYObj != null)
        centerY = centerYObj.floatValue() * getHeightf();
      else
        centerY = getHeightf()/2;

      if (currEffect.hasAnyCacheMask(Widget.HAS_SCALE_PROPS) && (sizeChange || currEffect.hasAnyCacheMask(Widget.HAS_DYNAMIC_SCALE_PROPS)))
      {
        float startScaleX = currEffect.getFloatProperty(Widget.START_RENDER_SCALE_X, 1.0f, effectContext, this);
        float startScaleY = currEffect.getFloatProperty(Widget.START_RENDER_SCALE_Y, 1.0f, effectContext, this);
        float endScaleX = currEffect.getFloatProperty(Widget.RENDER_SCALE_X, 1.0f, effectContext, this);
        float endScaleY = currEffect.getFloatProperty(Widget.RENDER_SCALE_Y, 1.0f, effectContext, this);
        if (uiMgr.isXBMCCompatible())
        {
          // XBMC can use scaling to resize something to be a specific pixel size and we round absolute coordinates so
          // fix errors related to that here
          if (size.width == 1)
          {
            startScaleX = Math.round(startScaleX * scaledFullWidth / baseScalingWidth);
            endScaleX = Math.round(endScaleX * scaledFullWidth / baseScalingWidth);
          }
          if (size.height == 1)
          {
            startScaleY = Math.round(startScaleY * scaledFullHeight / baseScalingHeight);
            endScaleX = Math.round(endScaleY * scaledFullHeight / baseScalingHeight);
          }
        }
        currTracker.setZoomEffect(startScaleX, startScaleY, endScaleX, endScaleY,
            centerX, centerY);
      }
      if (currEffect.hasAnyCacheMask(Widget.HAS_ROTX_PROPS) && (sizeChange || currEffect.hasAnyCacheMask(Widget.HAS_DYNAMIC_ROTX_PROPS)))
        currTracker.setRotateXEffect(currEffect.getFloatProperty(Widget.START_RENDER_ROTATE_X, 0.0f, effectContext, this),
            currEffect.getFloatProperty(Widget.RENDER_ROTATE_X, 0.0f, effectContext, this), centerX, centerY);
      if (currEffect.hasAnyCacheMask(Widget.HAS_ROTY_PROPS) && (sizeChange || currEffect.hasAnyCacheMask(Widget.HAS_DYNAMIC_ROTY_PROPS)))
        currTracker.setRotateYEffect(currEffect.getFloatProperty(Widget.START_RENDER_ROTATE_Y, 0.0f, effectContext, this),
            currEffect.getFloatProperty(Widget.RENDER_ROTATE_Y, 0.0f, effectContext, this), centerX, centerY);
      if (currEffect.hasAnyCacheMask(Widget.HAS_ROTZ_PROPS) && (sizeChange || currEffect.hasAnyCacheMask(Widget.HAS_DYNAMIC_ROTZ_PROPS)))
        currTracker.setRotateZEffect(currEffect.getFloatProperty(Widget.START_RENDER_ROTATE_Z, 0.0f, effectContext, this),
            currEffect.getFloatProperty(Widget.RENDER_ROTATE_Z, 0.0f, effectContext, this), centerX, centerY);
    }
    if (currEffect.hasAnyCacheMask(Widget.HAS_CAMERA_PROPS)) // these are rare and we don't want to also have to cache reality's width/height just for this
      currTracker.setCameraOffset((currEffect.getFloatProperty(Widget.HALIGNMENT, 0.5f, effectContext, this) - 0.5f)* reality.getWidth(),
          (currEffect.getFloatProperty(Widget.VALIGNMENT, 0.5f, effectContext, this) - 0.5f) * reality.getHeight());

    if (!currTracker.isNoop())
    {
      if (DEBUG_EFFECTS)
        System.out.println("Adding to list: " + currTracker);
      trackersToAdd.add(currTracker);
    }
  }

  private boolean currRenderVisibility;
  private Object[] shapeKidsCache;
  private long shapeKidsLastCached;
  private Widget[] effectKidsCache;
  private long effectKidsLastCached;
  private java.util.Map effectTrackerMap;
  private float[] myTransCoords = null;
  // These are accessed from the processChain evaluation of an effect
  private java.util.HashSet remainingEffects;
  private boolean hasFocusTracker;
  private boolean killHideEffects = false;
  private boolean killUnfocusEffects = false;
  private java.util.ArrayList trackersToAdd;
  private boolean recalcAllEffectStates = false;
  public void buildRenderingOps(java.util.ArrayList opList, java.awt.geom.Rectangle2D.Float clipRect,
      int diffuseColor, float alphaFactor, float xoff, float yoff, int flags)
  {
    currRenderVisibility = isVisible() && (parent == null || !(parent instanceof ZPseudoComp) || (passesConditionalCacheValue && ((ZPseudoComp) parent).currRenderVisibility));
    maxEffectZoom = 1;
    if (widgType != Widget.MENU && widgType != Widget.OPTIONSMENU && parent instanceof ZPseudoComp)
    {
      menuLoadedState = ((ZPseudoComp)parent).menuLoadedState;
      menuUnloadedState = ((ZPseudoComp)parent).menuUnloadedState;
    }
    // In case there was a latent unload flag set
    if (menuLoadedState)
      menuUnloadedState = false;
    // We have to put these in whether we are displayed or not! So do it before any other checks if it's an out!
    // NOTE: We'll have to deal with this getting set below the level of conditionality most likely
    if (pendingAnimations != null && !pendingAnimations.isEmpty())
    {
      if (!uiMgr.areLayersEnabled())
        pendingAnimations.clear();
      for (int i = 0; i < pendingAnimations.size(); i++)
      {
        RenderingOp ropy = (RenderingOp) pendingAnimations.get(i);
        if ((ropy.surface != null && ropy.surface.endsWith("Focus")) && (surfaceCache == null || !surfaceCache.endsWith("Focus")))
        {
          // This is a focus animation but we are NOT the focus animator. We need to find the currently focused component,
          // and then find it's child animator and set this in it. If it ain't there, then clear it.
          ZComp focusOwner = getFocusOwner(false);
          if (focusOwner != null && focusOwner instanceof ZPseudoComp)
          {
            ZPseudoComp focusAnimChild = ((ZPseudoComp) focusOwner).findFocusAnimatorChild();
            if (focusAnimChild != null)
            {
              if (focusAnimChild.pendingAnimations == null)
                focusAnimChild.pendingAnimations = new java.util.ArrayList();
              focusAnimChild.pendingAnimations.add(ropy);
            }
          }
          pendingAnimations.remove(i--);
        }
        else if (ropy.anime.isOut())
        {
          opList.add(ropy);
          // Start it now so by the time it hits the final renderer it'll have progressed slightly past it's starting point
          ropy.anime.setup(getTrueBoundsf());
          pendingAnimations.remove(i--);
        }
      }
    }
    // Transform our coordinates by our rendering transform so we can test clipping against what we render
    boolean inRegion;
    boolean ensureAllEffectsExist = false;
    if (effectKidsLastCached == 0 || (uiMgr.getModuleGroup().lastModified() > effectKidsLastCached))
    {
      effectKidsLastCached = uiMgr.getModuleGroup().lastModified();
      // This flag is to override the caching we do for effects so that studio changes can be visualized in real-time
      recalcAllEffectStates = true;
      java.util.ArrayList effectsToProcess = Pooler.getPooledArrayList();
      // Add any Effects that come from themes for this Widget type
      Widget effectsTheme = getWidgetChildFromWidgetChain(widgType, currTheme, defaultThemes);
      if (effectsTheme != null)
      {
        Widget[] effectsThemeKids = effectsTheme.contents();
        for (int i = 0; i < effectsThemeKids.length; i++)
        {
          if (effectsThemeKids[i].isInEffectHierarchy())
          {
            effectsToProcess.add(effectsThemeKids[i]);
          }
        }
      }
      Widget[] allKids = widg.contents();
      for (int i = 0; i < allKids.length; i++)
      {
        if (allKids[i].isInEffectHierarchy())
        {
          effectsToProcess.add(allKids[i]);
        }
      }
      effectKidsCache = (Widget[]) effectsToProcess.toArray(new Widget[0]);
      Pooler.returnPooledArrayList(effectsToProcess);
      ensureAllEffectsExist = true;
    }

    int effectsToClose = 0;
    java.util.ArrayList effectsToStart = null;
    boolean forcefulEffects = false;
    hasFocusTracker = false;
    // Check for scrolling effects
    if (scrollTracker != null)
    {
      if (!scrollTracker.effectTracker.isActive())
      {
        scrollTracker = null;
        cachedScrollOps = null;
      }
      else
      {
        effectsToStart = Pooler.getPooledArrayList();
        effectsToStart.add(scrollTracker);
        flags = flags | RENDER_FLAG_SCROLLING;
      }
    }
    if (effectKidsCache.length > 0)
    {
      // This tracks the ones we fired this time; so the ones we didn't hit can get set to negative
      remainingEffects = Pooler.getPooledHashSet();
      // Either of these can be true if there was an active Shown effect that's reversible; or if there was an
      // active focus effect which was reversible
      killHideEffects = false;
      killUnfocusEffects = false;
      trackersToAdd = Pooler.getPooledArrayList();
      if (effectTrackerMap != null)
        remainingEffects.addAll(effectTrackerMap.values());
      else
        effectTrackerMap = new java.util.HashMap();
      // First determine which effects actually need to be processed
      for (int i = 0; i < effectKidsCache.length; i++)
      {
        Widget nextEffect = null;
        if (!effectKidsCache[i].isType(Widget.EFFECT))
        {
          if (Sage.PERF_ANALYSIS)
            perfTime = Sage.time();
          processChain(effectKidsCache[i], relatedContext.createChild(), null, this, false, true, null);
          if (Sage.PERF_ANALYSIS)
          {
            perfTime = Sage.time() - perfTime;
            if (perfTime > Sage.EVALUATE_THRESHOLD_TIME)
            {
              System.out.println("EXEC EFFECT PERF time=" + perfTime + " widg=" + widg + " effectRoot=" + effectKidsCache[i]);
            }
          }
        }
        else
        {
          //					if (isEffectValid(effectKidsCache[i]))
          processEffectsWidget(effectKidsCache[i], null);
        }
      }
      if (effectsToStart == null)
        effectsToStart = Pooler.getPooledArrayList();
      java.awt.Rectangle newHitRectAdjust = null;

      for (int i = 0; i < trackersToAdd.size(); i++)
      {
        EffectTracker currTracker = (EffectTracker) trackersToAdd.get(i);
        if ((killHideEffects && currTracker.getTrigger() == EffectTracker.TRIGGER_HIDDEN) ||
            (killUnfocusEffects && currTracker.getTrigger() == EffectTracker.TRIGGER_UNFOCUSED))
        {
          currTracker.setPositivity(true);
          currTracker.processEffectState(0);
        }
        else
        {
          effectsToStart.add(new RenderingOp(currTracker, currTracker.isClipped() ? parent.getTrueBoundsf() : null, getTrueXf(), getTrueYf()));
          if (!currTracker.isTracking())
          {
            currTracker.setTargetHidden(!currRenderVisibility);
            forcefulEffects |= currTracker.isForceful();
          }
          if (currTracker.hasSlide())
          {
            if (newHitRectAdjust == null)
              newHitRectAdjust = new java.awt.Rectangle();
            newHitRectAdjust.x += Math.round(currTracker.getTargetTranslationX());
            newHitRectAdjust.y += Math.round(currTracker.getTargetTranslationY());
          }
          if (currTracker.hasZoom())
          {
            if (currTracker.getTargetScaleX() < 0)
            {
              if ((flags & RENDER_FLAG_FLIP_X) != 0)
                flags = flags & ~RENDER_FLAG_FLIP_X;
              else
                flags = flags | RENDER_FLAG_FLIP_X;
            }
            if (currTracker.getTargetScaleY() < 0)
            {
              if ((flags & RENDER_FLAG_FLIP_Y) != 0)
                flags = flags & ~RENDER_FLAG_FLIP_Y;
              else
                flags = flags | RENDER_FLAG_FLIP_Y;
            }
            maxEffectZoom = Math.max(currTracker.getTargetScaleX(), currTracker.getTargetScaleY());
            if (maxEffectZoom < 0)
              maxEffectZoom *= 1;
          }
        }
      }
      Pooler.returnPooledArrayList(trackersToAdd);
      if (ensureAllEffectsExist)
      {
        // We need to make sure all of the effects that are failing conditionals currently are still
        // accounted for in our map so that they don't undergo an unwanted state transition when their
        // conditional becomes true.
        java.util.HashSet checkedEffects = Pooler.getPooledHashSet();
        java.util.ArrayList widgsToCheck = Pooler.getPooledArrayList();
        for (int i = 0; i < effectKidsCache.length; i++)
        {
          if (effectKidsCache[i].type() != Widget.EFFECT)
            widgsToCheck.add(effectKidsCache[i]);
        }
        checkedEffects.addAll(widgsToCheck);
        while (!widgsToCheck.isEmpty())
        {
          Widget currWidg = (Widget) widgsToCheck.remove(widgsToCheck.size() - 1);
          Widget[] currKids = currWidg.contents();
          for (int i = 0; i < currKids.length; i++)
          {
            Widget currKid = currKids[i];
            if (currKid.isInEffectHierarchy())
            {
              if (currKid.type() == Widget.EFFECT)
              {
                if (!effectTrackerMap.containsKey(currKid))
                {
                  // We need to create the tracker object for this widget now
                  EffectTracker currTracker = new EffectTracker(currKid, this, relatedContext);
                  effectTrackerMap.put(currKid, currTracker);
                  remainingEffects.add(currTracker);
                }
                else if (recalcAllEffectStates)
                {
                  EffectTracker currTracker = (EffectTracker) effectTrackerMap.get(currKid);
                  if (currTracker != null)
                    currTracker.virgin = true;
                }
              }
              else if (checkedEffects.add(currKid))
              {
                widgsToCheck.add(currKid);
              }
            }
          }
        }
        Pooler.returnPooledHashSet(checkedEffects);
        Pooler.returnPooledArrayList(widgsToCheck);
      }
      java.util.Iterator walker = remainingEffects.iterator();
      while (walker.hasNext())
      {
        EffectTracker trekkie = (EffectTracker) walker.next();
        if (trekkie.isTracking())
        {
          if (trekkie.getTrigger() == EffectTracker.TRIGGER_FOCUSTRACKER)
            hasFocusTracker = true;
          reality.handleTrackerEffect(trekkie, false);
          continue;
        }
        if (menuLoadedState)
        {
          // NOTE: This was set at true; but then there was an issue with the window.previous flag in a conditional animation on the MediaStream
          // Main Menu when coming from the skin settings menu; it seems like it really should be false instead
          trekkie.setInitialPositivity(false);
        }
        // If its just been disabled due to conditionality and its still active; let it run to completion
        if (trekkie.getTrigger() != EffectTracker.TRIGGER_CONDITIONAL && isEffectValid(trekkie) && trekkie.isActive() &&
            !trekkie.isLoop())
          trekkie.setPositivity(true);
        else
        {
          if (trekkie.getTrigger() == EffectTracker.TRIGGER_CONDITIONAL || (trekkie.isReversible() && trekkie.isActive()))
            trekkie.setPositivity(false);
          else
          {
            trekkie.setInitialPositivity(isEffectValid(trekkie));
            trekkie.setDisabled(true);
          }
        }
        if (!trekkie.isNoop())
        {
          // We may need to fix the center positions of these effects if there's a scroll going on that moved them.
          // Normally processEffectWidget would do this; but when we're in this case, we didn't hit the Effect due to that path.
          effectsToStart.add(new RenderingOp(trekkie, trekkie.isClipped() ? parent.getTrueBoundsf() : null, getTrueXf(), getTrueYf()));
          trekkie.setTargetHidden(!currRenderVisibility);
          forcefulEffects |= trekkie.isForceful();
          if (trekkie.hasSlide())
          {
            if (newHitRectAdjust == null)
              newHitRectAdjust = new java.awt.Rectangle();
            newHitRectAdjust.x += Math.round(trekkie.getTargetTranslationX());
            newHitRectAdjust.y += Math.round(trekkie.getTargetTranslationY());
          }
        }
      }
      // NOTE: Always set this or otherwise there's no place that we're clearing the hit rect!
      //if (newHitRectAdjust != null)
      hitRectAdjust = newHitRectAdjust;
      Pooler.returnPooledHashSet(remainingEffects);


      inRegion = true;
    }
    else
    {
      inRegion = testRectIntersect(clipRect, xoff, yoff);//.intersects(boundsf);
      hitRectAdjust = null;
    }
    recalcAllEffectStates = false;
    if (uiMgr.disableParentClip() && !Catbert.evalBool(relatedContext.getLocal("EnforceBounds")))
      inRegion = true;
    boolean renderMe = inRegion && boundsf.width != 0 && boundsf.height != 0;
    boolean disableShapeRender = false;
    if (renderMe)
    {
      if (!currRenderVisibility)
      {
        renderMe = false;
        // Check for other cases where we should render
        if (forcefulEffects)
          renderMe = true;
        // Parent in the hierarchy had a forced effect we're a part of
        else if ((flags & RENDER_FLAG_HIDDEN) != 0 && passesConditionalCacheValue)
          renderMe = true;
        // NOTE: Narflex - 3/9/10 - I added the passesConditionalCacheValue here because otherwise
        // we could end up with components that were not being shown when the hide effect occurred being displayed as
        // part of the hide effect. The example was in the top part of the OSD if you first viewed a program w/ a channel logo,
        // and then viewed one without a channel logo. You would then see the old logo on the screen during the hide animation.
        else if ((flags & RENDER_FLAG_FORCED_ONLY) != 0 && passesConditionalCacheValue)
        {
          // If we're not an image/text widget then we continue on rendering
          if (widgType != Widget.TEXT && widgType != Widget.IMAGE)
          {
            renderMe = true;
            disableShapeRender = true;
          }
        }
      }
    }
    if (renderMe && ((flags & RENDER_FLAG_SKIP_FOCUSED) != 0) && (hasFocusTracker || "Focus".equals(surfaceCache)))
      renderMe = false;
    if (!renderMe)
    {
      boolean childrenHaveHideEffects = false;
      // We do NOT want to trigger hide effects for our children if the only reason we're not rendering is because
      // we are outside of the clipping region which happens with scrolling effects
      for (int i = 0; i < numKids && !childrenHaveHideEffects ; i++)
      {
        childrenHaveHideEffects = kids[i].processHideEffects(inRegion);
      }
      if (!childrenHaveHideEffects)
      {
        if (effectsToStart != null)
        {
          // Be sure the effects we're ditching are in the right state
          for (int i = 0; i < effectsToStart.size(); i++)
          {
            RenderingOp rop = (RenderingOp) effectsToStart.get(i);
            if (!rop.effectTracker.isTracking())
              rop.effectTracker.processEffectState(0);
          }
          Pooler.returnPooledArrayList(effectsToStart);
        }
        menuLoadedState = false;
        menuUnloadedState = false;
        return;
      }
      flags |= RENDER_FLAG_FORCED_ONLY;
    }
    float orgclipx=clipRect.x, orgclipy=clipRect.y, orgclipw=clipRect.width, orgcliph=clipRect.height;

    if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.RENDER_UI, this, widg, null);
    if (renderStartHook != null)
    {
      Catbert.processHookDirectly(renderStartHook, null, uiMgr, this);
    }

    if (forcefulEffects)
      flags |= RENDER_FLAG_HIDDEN;
    if (cachedScrollOps != null)
    {
      opList.addAll(cachedScrollOps);
    }
    if (effectsToStart != null)
    {
      opList.addAll(effectsToStart);
      effectsToClose = effectsToStart.size();
      Pooler.returnPooledArrayList(effectsToStart);
      effectsToStart = null;
    }

    if (diffuseRenderColor != 0xFFFFFF)
      diffuseColor = MathUtils.compositeColors(diffuseColor, diffuseRenderColor);

    // Check for component level alpha
    float bgAlphaFactor = getBGAlpha();

    // Insert the surface cache operation if we have one and are actually rendered (otherwise the off op will get culled w/ zero alpha)
    String addedSurfaceCache = surfaceCache;
    if (addedSurfaceCache != null && bgAlphaFactor > 0 && uiMgr.areLayersEnabled())
    {
      // If we are not the top level menu/popup then we disable surface usage. This is so if we layer
      // multiple popups that each have an animation effect in them they won't overlap in
      // their surface usage.
      if (uiMgr.getCurrUI().hasPopup() && !isTopPopup())
      {
        addedSurfaceCache = null;
        alphaFactor *= bgAlphaFactor;
      }
      else
      {
        opList.add(new RenderingOp(addedSurfaceCache, getTrueBoundsf(), true));
        alphaFactor *= bgAlphaFactor;
      }
    }
    else
    {
      addedSurfaceCache = null;
      alphaFactor *= bgAlphaFactor;
    }

    java.awt.geom.Rectangle2D.Float bgCompClipRect = clipRect;
    if (!uiMgr.disableParentClip() || Catbert.evalBool(relatedContext.getLocal("EnforceBounds")))
    {
      if (uiMgr.disableParentClip())
      {
        bgCompClipRect = new java.awt.geom.Rectangle2D.Float(clipRect.x, clipRect.y, clipRect.width, clipRect.height);
      }
      clipRectToBounds(clipRect, xoff, yoff);
    }
    if (DEBUG_PAINTING)
    {
      System.out.println("buildRenderingOps for Widget " + widg + " this=" + this);
    }
    // Setup our rendering xform if there is one
    xoff += boundsf.x;
    yoff += boundsf.y;

    if (hasVideoBackground())
    {
      java.util.ArrayList rectRgns = new java.util.ArrayList();
      for (int i = 0; i < numKids; i++)
      {
        ZComp currKid = kids[i];
        if (currKid.size.width > 0 && currKid.size.height > 0 &&
            (i > 1 || !(currKid instanceof ZLabel) || ((ZLabel) currKid).getText().length() > 0)) // don't block out the area for empty subtitles
        {
          rectRgns.add(currKid.getTrueBounds());
        }
      }
      RenderingOp vop = new RenderingOp(new java.awt.geom.Rectangle2D.Float(0, 0, getTopPseudoParent().getWidthf(),
          getTopPseudoParent().getHeightf()), rectRgns);
      vop.privateData = widg;
      opList.add(vop);
      if (numKids > 1 && kids[0] instanceof ZCCLabel && kids[1] instanceof ZCCLabel)
      {
        uiMgr.getVideoFrame().registerSubtitleComponent((ZCCLabel) kids[1]);
        uiMgr.getVideoFrame().registerCCComponent((ZCCLabel) kids[0]);
      }
    }
    if (currTheme != null)
    {
      // Menus need to do a fill-type painting on their background.
      float currBGAlpha = alphaFactor;
      int bgAlpha = currTheme.getIntProperty(Widget.BACKGROUND_ALPHA, 255, null, this);
      int bgFocusAlpha = currTheme.getIntProperty(Widget.BACKGROUND_SELECTED_ALPHA, 255, null, this);
      if ((doesAncestorOrMeHaveFocus() && bgFocusAlpha != 255) || (!doesAncestorOrMeHaveFocus() && bgAlpha != 255))
      {
        currBGAlpha *= (doesAncestorOrMeHaveFocus() ? bgFocusAlpha : bgAlpha) / 255f;
      }
      if (useBGImage || (useFocusBGImage && doesAncestorOrMeHaveFocus()))
      {
        String imageName = (useFocusBGImage && doesAncestorOrMeHaveFocus()) ?
            currTheme.getStringProperty(Widget.BACKGROUND_SELECTED_IMAGE, null, this) :
              currTheme.getStringProperty(Widget.BACKGROUND_IMAGE, null, this);
            if (currTheme.getBooleanProperty(Widget.STRETCH_BACKGROUND_IMAGE, null, this))
            {
              MetaImage img = MetaImage.getMetaImage(imageName, this);
              opList.add(new RenderingOp(img, 0, diffuseColor, currBGAlpha, clipRect, xoff, yoff, boundsf.width, boundsf.height));
            }
            else if (currTheme.getBooleanProperty(Widget.TILE_BACKGROUND_IMAGE, null, this))
            {
              MetaImage img = MetaImage.getMetaImage(imageName, this);
              int w = img.getWidth();
              int h = img.getHeight();
              for (int x = 0; x < size.width; x+= w)
                for (int y = 0; y < size.height; y += h)
                {
                  opList.add(new RenderingOp(img, 0, diffuseColor, currBGAlpha,
                      clipRect, x + xoff, y + yoff, w, h));
                }
            }
            else
            {
              MetaImage img = MetaImage.getMetaImage(imageName, this);
              opList.add(new RenderingOp(img, 0, diffuseColor, currBGAlpha,
                  clipRect, xoff + (boundsf.width - img.getWidth())/2, yoff + (boundsf.height - img.getHeight())/2,
                  img.getWidth(), img.getHeight()));
            }
      }
      else if (focusBgColor != null && doesAncestorOrMeHaveFocus())
      {
        if (dynamicFocusBgColor)
          focusBgColor = currTheme.getColorProperty(Widget.BACKGROUND_SELECTED_COLOR, null, this);
        if (focusBgColor.getAlpha() < 255)
        {
          opList.add(new RenderingOp(new SageRenderer.ShapeDescription(
              boundsf.width, boundsf.height, new java.awt.Color(MathUtils.compositeColors(focusBgColor.getRGB(), diffuseColor))),
              currBGAlpha * focusBgColor.getAlpha()/255.0f, clipRect, xoff, yoff));
        }
        else
          opList.add(new RenderingOp(new SageRenderer.ShapeDescription(
              boundsf.width, boundsf.height, diffuseColor == 0xFFFFFF ? focusBgColor :
                new java.awt.Color(MathUtils.compositeColors(focusBgColor.getRGB(), diffuseColor))), currBGAlpha,
                clipRect, xoff, yoff));
      }
      else if (originalBgColor != null)
      {
        if (dynamicOriginalBgColor)
          originalBgColor = currTheme.getColorProperty(Widget.BACKGROUND_COLOR, null, this);
        if (originalBgColor.getAlpha() < 255)
        {
          opList.add(new RenderingOp(new SageRenderer.ShapeDescription(
              boundsf.width, boundsf.height, new java.awt.Color(MathUtils.compositeColors(originalBgColor.getRGB(), diffuseColor))),
              currBGAlpha * originalBgColor.getAlpha()/255.0f,
              clipRect, xoff, yoff));
        }
        else
          opList.add(new RenderingOp(new SageRenderer.ShapeDescription(
              boundsf.width, boundsf.height, diffuseColor == 0xFFFFFF ? originalBgColor :
                new java.awt.Color(MathUtils.compositeColors(originalBgColor.getRGB(), diffuseColor))), currBGAlpha,
                clipRect, xoff, yoff));
      }
    }

    if (widgType == Widget.VIDEO && !hasVideoBackground())
    {
      // Find the video component, if any, and setup the bounds for it
      if (size.width > 0 && size.height > 0)
      {
        // Since we're in a window, the only thing that can overlap on top of the video
        // is a popup menu or subtitles
        int arcMod = 0;//(uiMgr.getWindowSizeCat() + 1)*6;
        int vtx = (int)(xoff + boundsf.x);
        int vty = (int)(yoff + boundsf.y);
        int vw = size.width;
        int vh = size.height;
        java.util.ArrayList rectRgns = new java.util.ArrayList();
        if (vty > 0)
          rectRgns.add(new java.awt.Rectangle(-arcMod, -arcMod, reality.getRoot().getWidth()+2*arcMod, vty+arcMod));
        if (vtx > 0)
          rectRgns.add(new java.awt.Rectangle(-arcMod, vty - arcMod, vtx+arcMod, vh+2*arcMod));
        if (vtx + vw < reality.getRoot().getWidth())
          rectRgns.add(new java.awt.Rectangle(vtx + vw, vty-arcMod, reality.getRoot().getWidth() -
              (vtx + vw) + arcMod, vh+2*arcMod));
        if (vty + vh < reality.getRoot().getHeight())
          rectRgns.add(new java.awt.Rectangle(-arcMod, vty + vh, reality.getRoot().getWidth()+2*arcMod,
              reality.getRoot().getHeight() - (vty + vh)+arcMod));
        for (int i = 0; i < numKids; i++)
        {
          if (kids[i].isPopup() || (i <= 1 && kids[i] instanceof ZLabel && ((ZLabel) kids[i]).getText().length() > 0))
          {
            rectRgns.add(kids[i].getTrueBounds());
          }
        }
        RenderingOp vop = new RenderingOp(new java.awt.geom.Rectangle2D.Float(vtx, vty,
            boundsf.width, boundsf.height), rectRgns);
        vop.privateData = widg;
        opList.add(vop);
        if (numKids > 1 && kids[0] instanceof ZCCLabel && kids[1] instanceof ZCCLabel)
        {
          uiMgr.getVideoFrame().registerSubtitleComponent((ZLabel) kids[1]);
          uiMgr.getVideoFrame().registerCCComponent((ZCCLabel) kids[0]);
        }
      }
    }

    // Update any dynamic properties for non-pseudo children
    if (propWidg.isType(Widget.IMAGE))
    {
      if (reality.getEnableCornerArc())
      {
        ((ZImage) kids[0]).setCornerArc(propWidg.getIntProperty(Widget.CORNER_ARC, 0, null, this));
      }
      else
        ((ZImage) kids[0]).setCornerArc(0);
    }

    if (shapeKidsLastCached == 0 || (uiMgr.getModuleGroup().lastModified() > shapeKidsLastCached))
    {
      shapeKidsLastCached = uiMgr.getModuleGroup().lastModified();
      java.util.ArrayList shapeKids = Pooler.getPooledArrayList();//new java.util.ArrayList();
      // Add any Shapes that come from themes for this Widget type
      Widget shapesTheme = getWidgetChildFromWidgetChain(widgType, currTheme, defaultThemes);
      if (shapesTheme != null)
      {
        Widget[] shapeThemeKids = shapesTheme.contents();
        for (int i = 0; i < shapeThemeKids.length; i++)
        {
          if (shapeThemeKids[i].isInShapeHierarchy())
          {
            shapeKids.add(new Object[] { shapeThemeKids[i], new Integer(-(shapeThemeKids.length - i)) });
          }
        }
      }
      Widget[] allKids = widg.contents();
      for (int i = 0; i < allKids.length; i++)
      {
        if (allKids[i].isInShapeHierarchy())
        {
          shapeKids.add(new Object[] { allKids[i], new Integer(i) });
        }
      }
      shapeKidsCache = shapeKids.toArray();
      Pooler.returnPooledArrayList(shapeKids);
    }
    if (numKids > 0 || shapeKidsCache.length > 0)
    {
      tempOpList = opList;
      tempClipRect = clipRect;
      tempAlphaFactor = alphaFactor;
      tempDiffuseColor = diffuseColor;
      tempRenderXoff = xoff;
      tempRenderYoff = yoff;
      Widget nextShape = null;
      int nextShapeIndex = 0;
      int shapeCacheIndex = 0;
      ZComp nextKid = null;
      int i = 0;
      ZComp[] zKids = getZOrderCache();
      float maxChildEffectZoom = 1;
      while ((shapeCacheIndex < shapeKidsCache.length) || nextShape != null || i < numKids)
      {
        if (nextShape == null && shapeCacheIndex < shapeKidsCache.length)
        {
          Object[] shapeData = (Object[]) shapeKidsCache[shapeCacheIndex];
          shapeCacheIndex++;
          nextShape = (Widget) shapeData[0];
          nextShapeIndex = ((Integer) shapeData[1]).intValue();
        }
        if (nextKid == null && i < numKids)
        {
          nextKid = zKids[i];
        }
        // Always do the root ZComp first if we're a wrapper for one of those (autotext for an Item doesn't count)
        boolean nextKidIsZCompOnly = (nextKid != null) && !(nextKid instanceof ZPseudoComp) && widgType != Widget.ITEM;
        if (!nextKidIsZCompOnly && nextShape != null && (nextKid == null || nextKid.zOffset > 0 || (nextShapeIndex <= nextKid.childWidgetIndex && nextKid.zOffset == 0)))
        {
          if (!disableShapeRender)
          {
            checkForFocus = true;
            if (Sage.PERF_ANALYSIS)
              perfTime = Sage.time();
            Catbert.ExecutionPosition ep = processChain(nextShape, relatedContext.createChild(), null, this, true);
            if (Sage.PERF_ANALYSIS)
            {
              perfTime = Sage.time() - perfTime;
              if (perfTime > Sage.EVALUATE_THRESHOLD_TIME)
              {
                System.out.println("EXEC SHAPE PERF time=" + perfTime + " widg=" + nextShape);
              }
            }
            if (ep != null)
              System.out.println("ERROR: Async method call used in shape rendering chain:" + nextShape);
            checkForFocus = false;
          }
          nextShape = null;
        }
        else
        {
          nextKid.buildRenderingOps(opList, nextKid.backgroundComponent ? bgCompClipRect : clipRect, diffuseColor, alphaFactor,
              xoff, yoff, flags);
          maxChildEffectZoom = Math.max(maxChildEffectZoom, nextKid.maxEffectZoom);
          i++;
          nextKid = null;
        }
      }
      maxEffectZoom *= maxChildEffectZoom;
    }

    // 601 if ((widg != null && widg.tempHighlight == 1) || (propWidg != null && propWidg.tempHighlight == 1))
    if ((widg != null && widg.tempHighlight()) || (propWidg != null && propWidg.tempHighlight()))
    {
      SageRenderer.ShapeDescription sd = new SageRenderer.ShapeDescription();
      sd.shapeType = "Rectangle";
      sd.color = java.awt.Color.yellow;
      sd.shapeWidth = boundsf.width;
      sd.shapeHeight = boundsf.height;
      sd.strokeSize = 1;
      opList.add(new RenderingOp(sd, 1.0f,
          clipRect, xoff, yoff));
    }
    clipRect.setFrame(orgclipx, orgclipy, orgclipw, orgcliph);
    if (addedSurfaceCache != null)
      opList.add(new RenderingOp(addedSurfaceCache, getTrueBoundsf(), false));

    // See if there's a menu transition focus animation we need to run
    if (surfaceCache != null && surfaceCache.endsWith("Focus"))
    {
      RenderingOp lostFocusAnim = reality.getLostFocusAnimationOp();
      if (lostFocusAnim != null)
      {
        reality.registerLostFocusAnimationOp(null);
        if (pendingAnimations == null)
          pendingAnimations = new java.util.ArrayList();
        pendingAnimations.add(lostFocusAnim);
      }
    }
    while (effectsToClose > 0)
    {
      opList.add(new RenderingOp(null));
      effectsToClose--;
    }
    menuLoadedState = false;
    menuUnloadedState = false;
    // This is an 'IN' or Smooth animation so we need to have the surface already rendered before we get to the animation
    if (pendingAnimations != null && !pendingAnimations.isEmpty())
    {
      boolean clearFocusAnims = false;
      for (int i = 0; i < pendingAnimations.size(); i++)
      {
        RenderingOp ropy = (RenderingOp) pendingAnimations.get(i);
        opList.add(ropy);
        ropy.anime.setup(getTrueBoundsf());
        if (ropy.anime.animType == RenderingOp.Animation.SCROLL)
        {
          clearFocusAnims = true;
        }
      }
      pendingAnimations.clear();
      if (clearFocusAnims)
      {
        // Clear out any focus animation operations that are contained within this scroll animation
        // We need to do this unless we want to allow for more layers for animations
        // NOTE: WE MAY ALSO WANT TO CLEAR OUT ALL FOCUS SURFACE OPS
        for (int i = 0; i < opList.size(); i++)
        {
          RenderingOp op = (RenderingOp) opList.get(i);
          if ((op.isAnimationOp() /*|| op.isSurfaceOp()*/) && op.surface != null && op.surface.endsWith("Focus"))
            opList.remove(i--);
        }
      }
    }
  }

  protected java.util.Vector debugUIComps;
  protected long debugUICompsLastCached;
  public java.util.Vector getDebugUIComps()
  {
    /*		if (uiMgr.getModuleGroup().lastModified() > debugUICompsLastCached)
		{
			debugUICompsLastCached = uiMgr.getModuleGroup().lastModified();
			debugUIComps = new java.util.Vector();
		}
		else if (debugUIComps == null)
		{
			debugUICompsLastCached = uiMgr.getModuleGroup().lastModified();
			debugUIComps = new java.util.Vector();
		}
		else
			return debugUIComps;
     */
    debugUIComps = new java.util.Vector();
    if (hasVideoBackground() && widgType != Widget.VIDEO)
    {
      debugUIComps.add("BGVIDEO");
    }
    if (currTheme != null)
    {
      if (useBGImage || useFocusBGImage)
      {
        debugUIComps.add(currTheme);
        //				if (useBGImage)
        //					debugUIComps.add(MetaImage.getMetaImage(currTheme.getStringProperty(Widget.BACKGROUND_IMAGE, null, this)));
        //				if (useFocusBGImage)
        //					debugUIComps.add(MetaImage.getMetaImage(currTheme.getStringProperty(Widget.BACKGROUND_SELECTED_IMAGE, null, this)));
      }
      else if (focusBgColor != null || originalBgColor != null)
      {
        debugUIComps.add(currTheme);
      }
    }

    // This needs to be done here in case it wasn't done when building the rendering ops
    if (shapeKidsLastCached == 0 || (uiMgr.getModuleGroup().lastModified() > shapeKidsLastCached))
    {
      shapeKidsLastCached = uiMgr.getModuleGroup().lastModified();
      java.util.ArrayList shapeKids = new java.util.ArrayList();
      // Add any Shapes that come from themes for this Widget type
      Widget shapesTheme = getWidgetChildFromWidgetChain(widgType, currTheme, defaultThemes);
      if (shapesTheme != null)
      {
        Widget[] shapeThemeKids = shapesTheme.contents();
        for (int i = 0; i < shapeThemeKids.length; i++)
        {
          if (shapeThemeKids[i].isInShapeHierarchy())
          {
            shapeKids.add(new Object[] { shapeThemeKids[i], new Integer(-(shapeThemeKids.length - i)) });
          }
        }
      }
      Widget[] allKids = widg.contents();
      for (int i = 0; i < allKids.length; i++)
      {
        if (allKids[i].isInShapeHierarchy())
        {
          shapeKids.add(new Object[] { allKids[i], new Integer(i) });
        }
      }
      shapeKidsCache = shapeKids.toArray();
    }

    if (numKids > 0 || shapeKidsCache.length > 0)
    {
      Widget nextShape = null;
      int nextShapeIndex = 0;
      int shapeCacheIndex = 0;
      ZComp nextKid = null;
      int i = 0;
      while ((shapeCacheIndex < shapeKidsCache.length) || nextShape != null || i < numKids)
      {
        if (nextShape == null && shapeCacheIndex < shapeKidsCache.length)
        {
          Object[] shapeData = (Object[]) shapeKidsCache[shapeCacheIndex];
          shapeCacheIndex++;
          nextShape = (Widget) shapeData[0];
          nextShapeIndex = ((Integer) shapeData[1]).intValue();
        }

        if (nextKid == null && i < numKids)
        {
          nextKid = kids[i];
        }
        if (nextShape != null && (nextKid == null || (nextShapeIndex <= nextKid.childWidgetIndex)))
        {
          // Find the actual Shape widget(s) recursively
          if (nextShape.isType(Widget.SHAPE))
            debugUIComps.add(nextShape);
          else
            debugUIComps.add(getRecursiveWidgetChildOfType(Widget.SHAPE, nextShape, null));
          nextShape = null;
        }
        else
        {
          if (nextKid instanceof ZPseudoComp)
            debugUIComps.add(nextKid);
          i++;
          nextKid = null;
        }
      }
    }
    return debugUIComps;
  }

  protected Widget getRecursiveWidgetChildOfType(byte wType, Widget w, java.util.Set ignoreUs)
  {
    if (ignoreUs == null)
      ignoreUs = new java.util.HashSet();
    Widget[] kids = w.contents();
    for (int i = 0; i < kids.length; i++)
    {
      if (kids[i].isType(wType))
        return kids[i];
      if (ignoreUs.add(kids[i]))
      {
        Widget rv = getRecursiveWidgetChildOfType(wType, kids[i], ignoreUs);
        if (rv != null)
          return rv;
      }
    }
    return null;
  }

  protected boolean hasVideoBackground()
  {
    return (widgType == Widget.MENU && propWidg.getBooleanProperty(Widget.VIDEO_BACKGROUND, null, this)) ||
        (widgType == Widget.VIDEO && ((ZPseudoComp)parent).widgType == Widget.MENU &&
        fillX == 1.0f && fillY == 1.0f);
  }

  private void drawShape(Widget shape, Catbert.Context shapeContext)
  {
    if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.RENDER_UI, this, shape, null);

    float width = ((scrolling & ZDataTable.HORIZONTAL_DIMENSION) != 0) ? Math.max(scrollingWidth, boundsf.width) : boundsf.width;
    float height = ((scrolling & ZDataTable.VERTICAL_DIMENSION) != 0) ? Math.max(scrollingHeight, boundsf.height) : boundsf.height;

    // Derive a description for what we're going to draw and check our cache for it so
    // we can use accelerated drawing for repeated rendering of any shape on the same Menu
    SageRenderer.ShapeDescription sd = new SageRenderer.ShapeDescription();
    String shapeType = shape.getStringProperty(Widget.SHAPE_TYPE, null, this);
    sd.shapeType = shapeType;
    float shapeBoxx = ((scrolling & ZDataTable.HORIZONTAL_DIMENSION) != 0) ? -scrollPosX : 0;
    float shapeBoxy = ((scrolling & ZDataTable.VERTICAL_DIMENSION) != 0) ? -scrollPosY : 0;
    float shapeBoxwidth = width;
    float shapeBoxheight = height;

    // Apply compensation for overscan if on a menu for overscan
    if (widgType == Widget.MENU || (widgType == Widget.OPTIONSMENU && backgroundComponent))
    {
      if (widgType == Widget.MENU || fillX == 1.0f)
      {
        shapeBoxx += insets.left;
        shapeBoxwidth -= insets.left + insets.right;
        width -= insets.left + insets.right;
      }
      if (widgType == Widget.MENU || fillY == 1.0f)
      {
        shapeBoxy += insets.top;
        shapeBoxheight -= insets.top + insets.bottom;
        height -= insets.top + insets.bottom;
      }
    }
    Number n = shape.getNumericProperty(Widget.FIXED_WIDTH, shapeContext, this);
    if (n != null)
    {
      if (n instanceof Float)
        shapeBoxwidth = n.floatValue() * width;
      else
        shapeBoxwidth = n.intValue();
    }
    n = shape.getNumericProperty(Widget.FIXED_HEIGHT, shapeContext, this);
    if (n != null)
    {
      if (n instanceof Float)
        shapeBoxheight = n.floatValue() * height;
      else
        shapeBoxheight = n.intValue();
    }
    sd.shapeWidth = shapeBoxwidth;
    sd.shapeHeight = shapeBoxheight;

    // We need to square/circle the shapes before we position them or we won't be using the right size.
    if (shapeType.equals("Circle"))
    {
      float sqrDim = Math.min(shapeBoxwidth, shapeBoxheight);
      sd.shapeWidth = sd.shapeHeight = sqrDim;
      sd.shapeType = shapeType = "Oval";
    }
    if (shapeType.equals("Square"))
    {
      float sqrDim = Math.min(shapeBoxwidth, shapeBoxheight);
      sd.shapeWidth = sd.shapeHeight = sqrDim;
      sd.shapeType = shapeType = "Rectangle";
    }

    float shapeAnchorPointX = shape.getFloatProperty(Widget.ANCHOR_POINT_X, -1, shapeContext, this);
    float shapeAnchorPointY = shape.getFloatProperty(Widget.ANCHOR_POINT_Y, -1, shapeContext, this);
    n = shape.getNumericProperty(Widget.ANCHOR_X, shapeContext, this);
    if (n != null)
    {
      if (shapeAnchorPointX < 0)
      {
        if (n instanceof Float)
          shapeBoxx += n.floatValue() * (width - sd.shapeWidth);
        else
          shapeBoxx += n.intValue();
      }
      else
      {
        if (n instanceof Float)
          shapeBoxx += n.floatValue() * width - shapeAnchorPointX * sd.shapeWidth;
        else
          shapeBoxx += n.intValue() - shapeAnchorPointX * sd.shapeWidth;
      }
    }
    n = shape.getNumericProperty(Widget.ANCHOR_Y, shapeContext, this);
    if (n != null)
    {
      if (shapeAnchorPointY < 0)
      {
        if (n instanceof Float)
          shapeBoxy += n.floatValue() * (height - sd.shapeHeight);
        else
          shapeBoxy += n.intValue();
      }
      else
      {
        if (n instanceof Float)
          shapeBoxy += n.floatValue() * height - shapeAnchorPointY * sd.shapeHeight;
        else
          shapeBoxy += n.intValue() - shapeAnchorPointY * sd.shapeHeight;
      }
    }

    boolean usedGradient = false;
    if (shape.hasProperty(Widget.FOREGROUND_COLOR))
    {
      java.awt.Color shapeColor = shape.getColorProperty(Widget.FOREGROUND_COLOR, shapeContext, this);
      sd.color = shapeColor;
      if (shapeColor != null && shape.hasProperty(Widget.GRADIENT_ANGLE) && shape.hasProperty(Widget.GRADIENT_AMOUNT))
      {
        usedGradient = true;
        float gradAngle = shape.getIntProperty(Widget.GRADIENT_ANGLE, 270, shapeContext, this);
        java.awt.Color c1, c2;
        n = shape.getNumericProperty(Widget.GRADIENT_AMOUNT, shapeContext, this);
        if (n instanceof Float)
        {
          float gradPerct = n.floatValue();
          c1 = new java.awt.Color(tempDiffuseColor == 0xFFFFFF ? shapeColor.getRGB() : MathUtils.compositeColors(shapeColor.getRGB(), tempDiffuseColor));
          c2 = new java.awt.Color(
              Math.max(0,Math.min(255, Math.round((1.0f - gradPerct) * shapeColor.getRed()))),
              Math.max(0,Math.min(255, Math.round((1.0f - gradPerct) * shapeColor.getGreen()))),
              Math.max(0,Math.min(255, Math.round((1.0f - gradPerct) * shapeColor.getBlue())))/*,
						shapeColor.getAlpha()*/);
          if (tempDiffuseColor != 0xFFFFFF)
            c2 = new java.awt.Color(MathUtils.compositeColors(c2.getRGB(), tempDiffuseColor));
        }
        else
        {
          int gradDelta = n.intValue();
          c1 = new java.awt.Color(tempDiffuseColor == 0xFFFFFF ? shapeColor.getRGB() : MathUtils.compositeColors(shapeColor.getRGB(), tempDiffuseColor));
          c2 = new java.awt.Color(
              Math.max(0,Math.min(255, shapeColor.getRed() - gradDelta)),
              Math.max(0,Math.min(255, shapeColor.getGreen() - gradDelta)),
              Math.max(0,Math.min(255, shapeColor.getBlue() - gradDelta))/*,
						shapeColor.getAlpha()*/);
          if (tempDiffuseColor != 0xFFFFFF)
            c2 = new java.awt.Color(MathUtils.compositeColors(c2.getRGB(), tempDiffuseColor));
        }
        while (gradAngle < 0)
          gradAngle += 360;
        gradAngle %= 360;
        float gradx1, grady1, gradx2, grady2;

        gradx1 = (sd.shapeWidth/2) + (float)(sd.shapeWidth*Math.cos(Math.toRadians(gradAngle))/2);
        gradx2 = (sd.shapeWidth/2) - (gradx1 - (sd.shapeWidth/2));
        grady2 = (sd.shapeHeight/2) - (float)(sd.shapeHeight*Math.sin(Math.toRadians(gradAngle))/2);
        grady1 = (sd.shapeHeight/2) + ((sd.shapeHeight/2) - grady2);
        sd.gradc1 = c1;
        sd.gradc2 = c2;
        sd.fx1 = gradx1;// + shapeBoxx + trueX;
        sd.fy1 = grady1;// + shapeBoxy + trueY;
        sd.fx2 = gradx2;// + shapeBoxx + trueX;
        sd.fy2 = grady2;// + shapeBoxy + trueY;
      }
    }

    int thicky = shape.getIntProperty(Widget.THICKNESS, 1, shapeContext, this);
    boolean fillShape = sd.fill = shape.getBooleanProperty(Widget.SHAPE_FILL, shapeContext, this);
    int cornerArc = shape.getIntProperty(Widget.CORNER_ARC, 0, shapeContext, this);
    if (fillShape)
      thicky = 1;
    sd.cornerArc = cornerArc;
    sd.strokeSize = thicky;
    float alphaFactor = tempAlphaFactor * (shape.getIntProperty(Widget.FOREGROUND_ALPHA, 255, shapeContext, this)/255f);
    if (sd.color != null && sd.color.getAlpha() < 255)
    {
      alphaFactor *= sd.color.getAlpha()/255.0f;
      sd.color = new java.awt.Color(tempDiffuseColor != 0xFFFFFF ? MathUtils.compositeColors(sd.color.getRGB(), tempDiffuseColor) : sd.color.getRGB());
    }
    else if (sd.color != null && tempDiffuseColor != 0xFFFFFF)
      sd.color = new java.awt.Color(MathUtils.compositeColors(sd.color.getRGB(), tempDiffuseColor));
    // If there's no color then don't render the shape!
    if (sd.color != null)
    {
      if (reality.isIntegerPixels())
      {
        float intx = (int)(shapeBoxx);
        float inty = (int)(shapeBoxy);
        sd.shapeWidth = (int)(/*shapeBoxx +*/ sd.shapeWidth);// - intx;
        sd.shapeHeight = (int)(/*shapeBoxy +*/ sd.shapeHeight);// - inty;
        shapeBoxx = intx;
        shapeBoxy = inty;
      }
      tempOpList.add(new RenderingOp(sd, alphaFactor, tempClipRect, shapeBoxx + tempRenderXoff, shapeBoxy + tempRenderYoff));
    }
  }

  ZPseudoComp getVideoComp()
  {
    if ((widgType == Widget.MENU && propWidg.getBooleanProperty(Widget.VIDEO_BACKGROUND, null, this)) ||
        (widgType == Widget.VIDEO && passesUpwardConditional()))
      return this;
    for (int i = 0; i < numKids; i++)
    {
      if (kids[i] instanceof ZPseudoComp)
      {
        ZPseudoComp rv = ((ZPseudoComp) kids[i]).getVideoComp();
        if (rv != null)
          return rv;
      }
    }
    return null;
  }

  boolean tryToConsumeKeystroke(UserEvent evt)
  {
    int numInputs = getNumTextInputs(0);
    if (numInputs != 1)
      return false;
    return tryToConsumeKeystrokeInternal(evt);
  }

  private boolean tryToConsumeKeystrokeInternal(UserEvent evt)
  {
    if (widgType == Widget.TEXTINPUT)
    {
      if (action(evt))
        return true;
    }
    for (int i = 0; i < numKids; i++)
      if (kids[i] instanceof ZPseudoComp)
      {
        if (((ZPseudoComp) kids[i]).tryToConsumeKeystrokeInternal(evt))
          return true;
      }
    return false;
  }

  // Returns 0, 1 or 2 for the number of text inputs found, 2 means that there were 2 or more
  public int getNumTextInputs(int numFound)
  {
    if (!passesConditional()) return numFound;
    if (widgType == Widget.TEXTINPUT)
    {
      numFound++;
    }
    for (int i = 0; i < numKids; i++)
      if (kids[i] instanceof ZPseudoComp)
      {
        numFound = ((ZPseudoComp) kids[i]).getNumTextInputs(numFound);
        if (numFound >= 2)
          return numFound;
      }
    return numFound;
  }

  ZPseudoComp getCompForWidget(Widget forWidg)
  {
    if (widg == forWidg)
      return this;
    else
    {
      for (int i = 0; i < numKids; i++)
      {
        if (kids[i] instanceof ZPseudoComp)
        {
          ZPseudoComp rv = ((ZPseudoComp) kids[i]).getCompForWidget(forWidg);
          if (rv != null)
            return rv;
        }
      }
      return null;
    }
  }

  void getCompsForWidget(Widget forWidg, java.util.Vector rv)
  {
    if (widg == forWidg)
      rv.add(this);
    for (int i = 0; i < numKids; i++)
      if (kids[i] instanceof ZPseudoComp)
        ((ZPseudoComp) kids[i]).getCompsForWidget(forWidg, rv);
  }

  public Catbert.Context getRelatedContext() { return relatedContext; }

  //	public boolean isPopup() { return widgType == Widget.OPTIONSMENU || super.isPopup(); }

  public boolean isTopPopup()
  {
    if (widgType == Widget.OPTIONSMENU)
    {
      PseudoMenu currUI = uiMgr.getCurrUI();
      return currUI != null && currUI.getTopPopup() == this;
    }
    return (parent instanceof ZPseudoComp) && ((ZPseudoComp) parent).isTopPopup();
  }

  public boolean shouldTakeEvents()
  {
    return uiMgr.getCurrUI() != null && (!uiMgr.getCurrUI().hasPopup() || isTopPopup()) && passesUpwardConditional();
  }

  // Sets this component to be the focused component; must be focusable
  public boolean setFocus()
  {
    if (!isFocusable()) return false;
    if (!isFocused())
    {
      selectNode(this);
      ZPseudoComp tempParent = getTopPseudoParent();
      tempParent.updateFocusTargetRect(0);
    }
    return isFocused();
  }

  public boolean setFocusByValue(String varName, Object focusValue, boolean visCheck)  // returns true if it took the focus
  {
    if (!passesConditional() && (!uiMgr.allowHiddenFocus() || !Catbert.evalBool(relatedContext.getLocal("AllowHiddenFocus")))) return false;

    boolean focusTest = isFocusable();
    if (focusTest && (!visCheck || isWithinScreenBounds()))
    {
      Object myValue = relatedContext.safeLookup(varName);
      if (myValue == focusValue || (myValue != null && myValue.equals(focusValue)))
      {
        if (!isFocused())
        {
          selectNode(this);
          ZPseudoComp tempParent = getTopPseudoParent();
          tempParent.updateFocusTargetRect(0);
        }
        return true;
      }
      else if (myValue instanceof Long[])
      {
        long testTime = ZDataTable.getTimeFromObject(focusValue);
        Long[] testBounds = (Long[]) myValue;
        if (testBounds.length == 2 && testBounds[0].longValue() <= testTime && testBounds[1].longValue() > testTime)
        {
          if (!isFocused())
          {
            ZPseudoComp tempParent = getTopPseudoParent();
            tempParent.updateFocusTargetRect(0);
            selectNode(this);
          }
          return true;
        }
      }
    }
    if (!focusTest && (!visCheck || isWithinScreenBounds()))
    {
      // This is for setting the default focus based on a parent group XBMCID,
      // also useful in general for setting default focus into an area of the UI
      Object myValue = relatedContext.getLocal(varName);
      if (myValue == focusValue || (myValue != null && myValue.equals(focusValue)))
      {
        if (!doesHierarchyHaveFocus())
        {
          if (super.getLastFocusedChild() instanceof ZPseudoComp)
          {
            ZPseudoComp lastFocus = (ZPseudoComp) super.getLastFocusedChild();
            if (lastFocus.passesUpwardConditional())
            {
              ZPseudoComp topParent = getTopPseudoParent();
              if (topParent.setFocus(super.getLastFocusedChild()))
              {
                topParent.updateFocusTargetRect(0);
                return true;
              }
            }
          }
          return setDefaultFocus();
        }
        else
          return true; // already has focus!
      }
    }
    for (int i = 0; i < numKids; i++)
    {
      Object currKid = kids[i];
      if (currKid instanceof ZPseudoComp && ((ZPseudoComp) currKid).setFocusByValue(varName, focusValue, visCheck))
        return true;
    }
    return false;
  }
  public boolean ensureVisibilityForValue(String varName, Object focusValue, int displayIndex)
  {
    if (!passesConditional()) return false;
    for (int i = 0; i < numKids; i++)
    {
      Object currKid = kids[i];
      if (currKid instanceof ZPseudoComp && ((ZPseudoComp) currKid).ensureVisibilityForValue(varName, focusValue,
          displayIndex))
        return true;
    }
    return false;
  }

  public Boolean getPassesConditionalForVariable(String matchName, Object matchValue)
  {
    if ("Focused".equals(matchName))
    {
      if (matchValue instanceof Boolean)
      {
        if (((Boolean) matchValue).booleanValue() == isFocused())
        {
          return Boolean.valueOf(passesUpwardConditional());
        }
      }
      else
        return null; // invalid matching
    }
    else if ("FocusedChild".equals(matchName))
    {
      if (matchValue instanceof Boolean)
      {
        if (((Boolean) matchValue).booleanValue() == doesHierarchyHaveFocus())
        {
          return Boolean.valueOf(passesUpwardConditional());
        }
      }
      else
        return null; // invalid matching
    }
    else
    {
      Object myValue = relatedContext.safeLookup(matchName);
      if (myValue == matchValue || (myValue != null && myValue.equals(matchValue)))
      {
        return Boolean.valueOf(passesUpwardConditional());
      }
      else if (matchValue instanceof Long[])
      {
        long testTime = ZDataTable.getTimeFromObject(myValue);
        Long[] testBounds = (Long[]) myValue;
        if (testBounds.length == 2 && testBounds[0].longValue() <= testTime && testBounds[1].longValue() > testTime)
        {
          return Boolean.valueOf(passesUpwardConditional());
        }
      }
    }
    for (int i = 0; i < numKids; i++)
    {
      Object currKid = kids[i];
      if (currKid instanceof ZPseudoComp)
      {
        Boolean kidRez = ((ZPseudoComp) currKid).getPassesConditionalForVariable(matchName, matchValue);
        if (kidRez != null)
          return kidRez;
      }
    }
    return null;
  }

  // First position holds any visible children which match; those return with priority,
  // Second position holds the first hidden child which matches
  public void getCompForVariable(String matchName, Object matchValue, Object[] rvHolder, boolean currVis)
  {
    currVis &= passesConditional();
    if (rvHolder[1] != null && !currVis) return;

    if ("Focused".equals(matchName))
    {
      if (matchValue instanceof Boolean)
      {
        if (((Boolean) matchValue).booleanValue() == isFocused())
        {
          rvHolder[currVis ? 0 : 1] = this;
          return;
        }
        else if (((Boolean) matchValue).booleanValue())
        {
          ZComp lastFocuser = getLastFocusedChild();
          if (lastFocuser instanceof ZPseudoComp)
          {
            rvHolder[((ZPseudoComp)lastFocuser).passesUpwardConditional() ? 0 : 1] = lastFocuser;
            return;
          }
          else
            return;
        }
      }
      else
        return; // invalid matching
    }
    else if ("FocusedChild".equals(matchName))
    {
      if (matchValue instanceof Boolean)
      {
        if (((Boolean) matchValue).booleanValue() == doesHierarchyHaveFocus())
        {
          rvHolder[currVis ? 0 : 1] = this;
          return;
        }
        else if (((Boolean) matchValue).booleanValue())
          return;
      }
      else
        return; // invalid matching
    }
    else
    {
      Object myValue = relatedContext.safeLookup(matchName);
      if (myValue == matchValue || (myValue != null && myValue.equals(matchValue)))
      {
        rvHolder[currVis ? 0 : 1] = this;
        return;
      }
      else if (matchValue instanceof Long[])
      {
        long testTime = ZDataTable.getTimeFromObject(myValue);
        Long[] testBounds = (Long[]) myValue;
        if (testBounds.length == 2 && testBounds[0].longValue() <= testTime && testBounds[1].longValue() > testTime)
        {
          rvHolder[currVis ? 0 : 1] = this;
          return;
        }
      }
    }
    if (!uiMgr.isXBMCCompatible())
    {
      for (int i = 0; i < numKids; i++)
      {
        Object currKid = kids[i];
        if (currKid instanceof ZPseudoComp)
        {
          ((ZPseudoComp) currKid).getCompForVariable(matchName, matchValue, rvHolder, currVis);
          if (rvHolder[0] != null || (!currVis && rvHolder[1] != null))
            return;
        }
      }
    }
    else
    {
      // Optimization to not search beyond the needed depth for these variables
      if ((widgType == Widget.OPTIONSMENU || (parent instanceof ZPseudoComp && ((ZPseudoComp)parent).widgType == Widget.MENU)) && "MenuXBMCID".equals(matchName))
        return;
      for (int i = numKids - 1; i >= 0; i--)
      {
        Object currKid = kids[i];
        if (currKid instanceof ZPseudoComp)
        {
          ((ZPseudoComp) currKid).getCompForVariable(matchName, matchValue, rvHolder, currVis);
          if (rvHolder[0] != null || (!currVis && rvHolder[1] != null))
            return;
        }
      }
    }
    return;
  }

  /*	public Object getObjectValue()
	{
		if (!isPopup()) return null;
		if (widg.isType(Widget.ITEM))
		{
			// The text value of the last ZLabel child is what we want (themes go first, that's why
			// we start at the end of the list)
			for (int i = numKids - 1; i >= 0; i--)
			{
				if (kids[i] instanceof ZLabel)
				{
					return ((ZLabel) kids[i]).getText();
				}
			}
		}
		if (widg.isType(Widget.TEXTINPUT))
		{
			// The text value of the last ZLabel child is what we want (themes go first, that's why
			// we start at the end of the list)
			for (int i = numKids - 1; i >= 0; i--)
			{
				if (kids[i] instanceof ZLabel)
				{
					return ((ZLabel) kids[i]).getText();
				}
			}
		}
		if (widg.isType(Widget.OPTIONSMENU))
		{
			// Get the focused child and return that value
			ZComp focer = getFocusOwner(false);
			if (focer != null)
			{
				return focer.getObjectValue();
			}
		}
		return null;
	}
   */

  // This should return a ArrayList of ArrayLists. Each element has 2 items in it. The first would
  // be the UI widget. The second would be the parent action ArrayList (or null).
  protected static java.util.ArrayList deriveFinalUIChildrenWithActionParents(Widget rootWidg)
  {
    java.util.HashMap alreadyChecked = Pooler.getPooledHashMap();
    java.util.ArrayList rv = new java.util.ArrayList();
    Widget[] kids = rootWidg.contents();
    for (int i = 0; i < kids.length; i++)
      deriveFinalUIChildrenWithActionParents(kids[i], alreadyChecked, null, rv);
    Pooler.returnPooledHashMap(alreadyChecked);
    return rv;
  }
  private static void deriveFinalUIChildrenWithActionParents(Widget rootWidg, java.util.Map alreadyChecked,
      java.util.ArrayList currParentActions, java.util.ArrayList rv)
  {
    if (rootWidg.isInUIHierarchy())
    {
      if (rootWidg.isUIComponent())
      {
        java.util.ArrayList v = new java.util.ArrayList();
        v.add(rootWidg);
        v.add((currParentActions == null) ? null : currParentActions.clone());
        rv.add(v);
        alreadyChecked.put(rootWidg, v);
        // Update all of the parent actions to indicate that the effect us in the map
        if (currParentActions != null)
        {
          for (int i=0; i < currParentActions.size(); i++)
          {
            Widget w = (Widget) currParentActions.get(i);
            java.util.ArrayList oldVec = (java.util.ArrayList) alreadyChecked.get(w);
            if (oldVec == null)
              alreadyChecked.put(w, oldVec = new java.util.ArrayList());
            oldVec.add(v);
          }
        }
      }
      else
      {
        alreadyChecked.put(rootWidg, null);
        boolean builtCurrParentActions = false;
        if (currParentActions == null)
        {
          currParentActions = Pooler.getPooledArrayList();//new java.util.ArrayList();
          builtCurrParentActions = true;
        }
        currParentActions.add(rootWidg);
        boolean hadNewKids = false;
        Widget[] kids = rootWidg.contents();
        for (int j = 0; j < kids.length; j++)
        {
          if (alreadyChecked.containsKey(kids[j]))
          {
            java.util.ArrayList oldVec = (java.util.ArrayList) alreadyChecked.get(kids[j]);
            if (oldVec != null)
            {
              for (int k = 0; k < oldVec.size(); k++)
              {
                java.util.ArrayList vecinvec = (java.util.ArrayList) oldVec.get(k);
                if (vecinvec.get(1) == null)
                  vecinvec.set(1, currParentActions.clone());
                else
                  ((java.util.ArrayList) vecinvec.get(1)).addAll(currParentActions);
              }
            }
            continue;
          }
          hadNewKids = true;
          deriveFinalUIChildrenWithActionParents(kids[j], alreadyChecked, currParentActions, rv);
        }
        if (hadNewKids)
        {
          // Done with this chain so we can pull this stuff out of currParentActions up until us
          while (currParentActions.remove(currParentActions.size() - 1) != rootWidg);
        }
        if (builtCurrParentActions)
          Pooler.returnPooledArrayList(currParentActions);
      }
    }
  }

  protected void updateFocusTargetRect(int evtType)
  {
    ZComp newFocus = getFocusOwner(false);
    if (newFocus != null)
    {
      if (focusTargetRect != null && evtType != 0)
      {
        java.awt.geom.Rectangle2D.Float newFocusBounds = newFocus.getTrueBoundsf();
        if (UserEvent.isUpEvent(evtType) || UserEvent.isDownEvent(evtType) ||
            evtType == UserEvent.PAGE_UP || evtType == UserEvent.PAGE_DOWN ||
            evtType == UserEvent.CHANNEL_UP || evtType == UserEvent.CHANNEL_DOWN)
        {
          // If there's X overlap, then we use the X intersection and the new Y area
          // otherwise if there's no X overlap, we just use the entire new area
          if (newFocusBounds.getMaxX() <= focusTargetRect.getMinX() ||
              newFocusBounds.getMinX() >= focusTargetRect.getMaxX())
          {
            focusTargetRect = newFocusBounds;
          }
          else
          {
            double newX = Math.max(newFocusBounds.getMinX(),
                focusTargetRect.getMinX());
            focusTargetRect.setFrame(newX, newFocusBounds.y, Math.min(newFocusBounds.getMaxX(),
                focusTargetRect.getMaxX()) - newX, newFocusBounds.height);
          }
        }
        else if (UserEvent.isLeftEvent(evtType) || UserEvent.isRightEvent(evtType) ||
            evtType == UserEvent.PAGE_RIGHT || evtType == UserEvent.PAGE_LEFT ||
            evtType == UserEvent.FF || evtType == UserEvent.REW)
        {
          // If there's Y overlap, then we use the Y intersection and the new X area
          // otherwise if there's no Y overlap, we just use the entire new area
          if (newFocusBounds.getMaxY() <= focusTargetRect.getMinY() ||
              newFocusBounds.getMinY() >= focusTargetRect.getMaxY())
          {
            focusTargetRect = newFocusBounds;
          }
          else
          {
            double newY = Math.max(newFocusBounds.getMinY(),
                focusTargetRect.getMinY());
            focusTargetRect.setFrame(newFocusBounds.x, newY, newFocusBounds.width,
                Math.min(newFocusBounds.getMaxY(),
                    focusTargetRect.getMaxY()) - newY);
          }
        }
      }
      else
        focusTargetRect = newFocus.getTrueBoundsf();
    }
    else
      focusTargetRect = null;
    //System.out.println("this=" + this + " widg=" + widg + " rect=" + focusTargetRect);
    //updateFocusTargetRect(cause);
    // Propogate up to any parent tables
    /*ZComp tempParent = parent;
		while (tempParent != null)
		{
			if (tempParent instanceof ZDataTable)
			{
				((ZDataTable) tempParent).updateFocusTargetRect(cause);
				break;
			}
			tempParent = tempParent.parent;
		}*/
  }

  public boolean hasValidFocusTargetRect()
  {
    return focusTargetRect != null && focusTargetRect.width > 0 && focusTargetRect.height > 0;
  }

  protected java.awt.geom.Rectangle2D.Float getTrueFocusRect()
  {
    if (isFocused())
      return getTrueBoundsf();
    ZComp focusHog = getFocusOwner(false);
    if (focusHog != null)
    {
      java.awt.geom.Rectangle2D.Float fullRect = focusHog.getTrueBoundsf();
      if (focusTargetRect != null && focusTargetRect.intersects(fullRect))
      {
        java.awt.geom.Rectangle2D.Float newSelBounds = (java.awt.geom.Rectangle2D.Float)fullRect.createIntersection(focusTargetRect);
        // JAK 8/12/05 - Sometimes this overlap exists due to floating point errors so make sure its not almost 0
        if (newSelBounds.width > 0.01 && newSelBounds.height > 0.01)
        {
          return newSelBounds;
        }
        else
        {
          return (focusTargetRect = fullRect);
        }
      }
      else
      {
        return (focusTargetRect = fullRect);
      }
    }
    /*for (int i = 0; i < gridPanel.numKids; i++)
		{
			if (((ZComp) gridPanel.kids[i]).doesHierarchyHaveFocus())
			{
				java.awt.Rectangle fullRect = ((ZComp) gridPanel.kids[i]).getTrueBounds();
				if (focusTargetRect != null && focusTargetRect.intersects(fullRect))
					return fullRect.intersection(focusTargetRect);
				else
					return (focusTargetRect = fullRect);
			}
		}*/
    // Changed on 11/11/2003, don't see why we wouldn't want to use the default rect
    //return null;
    //return focusTargetRect;
    // Changed back on 12/9/2003, we don't want the Table to steal focus if its paging
    // controls are used....but this'll probably cause the issue that I intended to
    // resolve on 11/11.
    return null;
  }

  public boolean passesUpwardConditional()
  {
    if (parent == null || !(parent instanceof ZPseudoComp))
      return true;
    if (!passesConditional())
      return false;
    return ((ZPseudoComp) parent).passesUpwardConditional();
  }

  public ZDataTable getTableParent()
  {
    ZPseudoComp rv = this;
    while (rv != null && !(rv instanceof ZDataTable))
      rv = (rv.parent instanceof ZPseudoComp) ? ((ZPseudoComp) rv.parent) : null;
      return (ZDataTable) rv;
  }

  public final ZComp getLastFocusedChild()
  {
    ZComp rv = super.getLastFocusedChild();
    if (rv == null && uiMgr.isXBMCCompatible())
    {
      // Check for a fixed focus position and return that as the focused component
      Object fixedFocus = getRelatedContext().get("ListFocusIndex");
      if (fixedFocus != null)
      {
        try
        {
          int pos = Integer.parseInt(fixedFocus.toString());
          if (pos >= 0 && pos < getNumKids())
            return kids[pos];
        }
        catch (NumberFormatException nfe){}
      }
      // Just return any focusable child; it always prefers something than nothing here
      java.util.ArrayList focusKids = Pooler.getPooledArrayList();
      addFocusableChildrenToList(focusKids);
      if (!focusKids.isEmpty())
        return (ZComp) focusKids.get(0);
      Pooler.returnPooledArrayList(focusKids);
    }
    return rv;
  }

  public boolean setOverallScrollLocation(float relativeX, float relativeY, boolean checkParents)
  {
    if (scrolling != 0)
    {
      boolean[] gotLock = { false };
      try
      {
        if (uiMgr.getLock(true, gotLock, true))
        {
          // Figure out the actual scroll positions
          setScrollPosition((relativeX < 0) ? scrollPosX : relativeX * Math.max(0, scrollingWidth - boundsf.width),
              (relativeY < 0) ? scrollPosY : relativeY * Math.max(0, scrollingHeight - boundsf.height), true, true);
        }
      }
      finally
      {
        if (gotLock[0])
          uiMgr.clearLock();
      }
      return true;
    }
    else if (parent instanceof ZPseudoComp && checkParents)
    {
      /*
       * NOTE:Also check scrolling siblings. This is necessary because if we create a scrolling
       * Panel we want to be able to put scrolling controls specific to that panel somewhere without
       * affecting the layout of that panel. So creating a parent panel to hold them both makes that
       * parent act like a scroll container.
       */
      for (int i = 0; i < parent.numKids; i++)
      {
        ZPseudoComp currSib = (ZPseudoComp) parent.kids[i];
        if (currSib == this)
          continue;
        if (currSib.setOverallScrollLocation(relativeX, relativeY, false))
          return true;
      }
      ((ZPseudoComp) parent).setOverallScrollLocation(relativeX, relativeY, true);
    }
    return false;
  }

  protected void setScrollPosition(float x, float y, boolean fixFocus, boolean leanUpLeft)
  {
    boolean posMatches = (scrollPosX == x && scrollPosY == y);
    if (posMatches || scrolling == 0) return;
    boolean ifv=isFirstVPage(),ilv=isLastVPage(),ifh=isFirstHPage(),ilh=isLastHPage();
    java.awt.geom.Rectangle2D.Float foci = (fixFocus && !isFocused()) ? getTrueFocusRect() : null;
    float oldScrollX = scrollPosX;
    float oldScrollY = scrollPosY;
    float newScrollPosX = x;
    float newScrollPosY = y;
    this.lastLeanUpLeft = leanUpLeft;

    newScrollPosX = Math.min(Math.max(0, newScrollPosX), Math.max(0, scrollingWidth - boundsf.width));
    // Scroll a little bit for if we're on the end
    if (leanUpLeft)
    {
      if (newScrollPosX <= insets.left + 1)
        newScrollPosX = 0;
      else if (newScrollPosX + boundsf.width + insets.right + 1 >= scrollingWidth)
        newScrollPosX = Math.max(0, scrollingWidth - boundsf.width);
    }
    else
    {
      if (newScrollPosX + boundsf.width + insets.right + 1 >= scrollingWidth)
        newScrollPosX = Math.max(0, scrollingWidth - boundsf.width);
      else if (newScrollPosX <= insets.left + 1)
        newScrollPosX = 0;
    }
    newScrollPosY = Math.min(Math.max(0, newScrollPosY), Math.max(0, scrollingHeight - boundsf.height));
    // Scroll a little bit for if we're on the end
    if (leanUpLeft)
    {
      if (newScrollPosY <= insets.top + 1)
        newScrollPosY = 0;
      else if (newScrollPosY + boundsf.height + insets.bottom + 1 >= scrollingHeight)
        newScrollPosY = Math.max(0, scrollingHeight - boundsf.height);
    }
    else
    {
      if (newScrollPosY + boundsf.height + insets.bottom + 1 >= scrollingHeight)
        newScrollPosY = Math.max(0, scrollingHeight - boundsf.height);
      else if (newScrollPosY <= insets.top + 1)
        newScrollPosY = 0;
    }
    RenderingOp rop = null;
    if (!processingScroll && uiMgr.areLayersEnabled() && Math.abs(newScrollPosX - oldScrollX) < boundsf.width &&
        Math.abs(newScrollPosY - oldScrollY) < boundsf.height)
    {
      // Make sure we don't already have a scrolling op in the pendingAnimations list. And if we do; then remove it
      // so we update with the last thing the user did.
      for (int i = 0; pendingAnimations != null && i < pendingAnimations.size(); i++)
      {
        RenderingOp aop = (RenderingOp) pendingAnimations.get(i);
        if (aop.isAnimationOp() && aop.anime.animType == RenderingOp.Animation.SCROLL)
        {
          pendingAnimations.remove(i--);
        }
      }
      if (pendingAnimations == null)
        pendingAnimations = new java.util.ArrayList();
      boolean fastScroll = reality.isDoingScrollAnimation();
      rop = new RenderingOp(surfaceCache, fastScroll ? "ScrollLinear" : "Scroll",
          uiMgr.getLong("ui/animation/panel_scroll_duration", 300) / (fastScroll ?
              uiMgr.getInt("ui/animation/continuous_scroll_speedup_factor", 4) : 1), 0, getTrueBoundsf(), getBGAlpha(), false);
      pendingAnimations.add(rop);
      processingScroll = true;
    }
    // Create the scrolling effect
    EffectTracker scrollOffTracker = null;
    EffectTracker newScrollTracker = null;
    if (!processingScroll && uiMgr.areEffectsEnabled() && Math.abs(newScrollPosX - oldScrollX) < boundsf.width &&
        Math.abs(newScrollPosY - oldScrollY) < boundsf.height)
    {
      int scrollDuration = propWidg.getIntProperty(Widget.DURATION, uiMgr.getInt("ui/animation/panel_scroll_duration", 300), null, this);
      if (scrollDuration > 0)
      {
        processingScroll = true;
        scrollTracker = null;
        cachedScrollOps = null;
        java.util.ArrayList newScrollCache = new java.util.ArrayList();
        float gptx = getTrueXf();
        float gpty = getTrueYf();
        boolean linearScroll = Catbert.evalBool(relatedContext.safeLookup("LinearScrolling"));
        scrollOffTracker = new EffectTracker(this, 0, scrollDuration, EffectTracker.EASE_INOUT, linearScroll ? EffectTracker.SCALE_LINEAR : EffectTracker.SCALE_QUADRATIC);
        scrollOffTracker.setInitialPositivity(false);
        scrollOffTracker.setPositivity(true);
        float moveX = newScrollPosX - oldScrollX;
        float moveY = newScrollPosY - oldScrollY;
        // This rectangle will clip the scrolling operations so things don't appear outside of the actual scrolling containers themselves
        java.awt.geom.Rectangle2D.Float effectClipRect = getTrueBoundsf();
        if (moveX != 0)
        {
          // Unbounded Y when doing horizontal scrolling
          effectClipRect.y = -2000;
          effectClipRect.height = 6000;
        }
        else
        {
          // Unbounded X when doing vertical scrolling
          effectClipRect.x = -2000;
          effectClipRect.width = 6000;
        }
        newScrollCache.add(new RenderingOp(scrollOffTracker, effectClipRect, 0, 0));
        // This rectangle will clip the rendering operations below us so we don't get overlap when we apply the scroll effect.
        java.awt.geom.Rectangle2D.Float scrollClipRect = new java.awt.geom.Rectangle2D.Float(gptx, gpty, moveX != 0 ? Math.abs(moveX) : reality.getWidth(),
            moveY != 0 ? Math.abs(moveY) : reality.getHeight());
        if (moveX != 0)
        {
          // Allow full height in the Y direction; but clip in the X direction at the overlap point
          if (moveX > 0)
            scrollClipRect.x = getTrueXf();
          else
            scrollClipRect.x = getWidthf() + moveX + getTrueXf();
        }
        else
        {
          // Allow full width in the X direction; but clip in the Y direction at the overlap point
          if (moveY > 0)
            scrollClipRect.y = getTrueYf();
          else
            scrollClipRect.y = getHeightf() + moveY + getTrueYf();
        }
        // Don't set the flag for skipping focus if this scrolling panel itself has focus...because it's
        // not dropping focus on the scroll then
        buildRenderingOps(newScrollCache, scrollClipRect, 0xFFFFFF, 1.0f, gptx - getXf(), gpty - getYf(),
            isFocused() ? 0 : RENDER_FLAG_SKIP_FOCUSED);
        newScrollCache.add(new RenderingOp(null));
        newScrollTracker = new EffectTracker(this, 0, scrollDuration, EffectTracker.EASE_INOUT, linearScroll ? EffectTracker.SCALE_LINEAR : EffectTracker.SCALE_QUADRATIC);
        newScrollTracker.setInitialPositivity(false);
        newScrollTracker.setPositivity(true);
        scrollTracker = new RenderingOp(newScrollTracker, effectClipRect, 0, 0);
        cachedScrollOps = newScrollCache;
      }
    }
    scrollPosX = newScrollPosX;
    scrollPosY = newScrollPosY;
    updatePaginationContext(ifv, ilv, ifh, ilh);
    if (fixFocus)
    {
      appendToDirty(true); // Re-enabled on 4/20/04
      if (!isFocused())
      {
        renderWithTrueFocusRect(foci);
      }
      else
      {
        // NOTE: If we're going to explicitly call the layout system we have to ensure the
        // layout properties are loaded for the subtree since we're going around the root layout call
        loadDynamicLayoutProps();
        doLayout();
        //appendToDirty(true); // Disabled on 4/20/04
      }
    }
    if (rop != null)
    {
      // In case the focus fixing caused it to move differently then we originally intended
      rop.anime.scrollVector = new float[] { (scrollPosX - oldScrollX), (scrollPosY - oldScrollY)};
      processingScroll = false;
    }
    if (scrollOffTracker != null)
    {
      float moveX = scrollPosX - oldScrollX;
      float moveY = scrollPosY - oldScrollY;
      scrollOffTracker.setTranslationEffect(0, 0, -moveX, -moveY);
      newScrollTracker.setTranslationEffect(moveX, moveY, 0, 0);
      processingScroll = false;
    }
  }

  protected void renderWithTrueFocusRect(java.awt.geom.Rectangle2D.Float x)
  {
    ZPseudoComp topParent = null;
    if (numKids > 0)
    {
      // Locations need to be established now since we use them to determine which is focused
      // NOTE: If we're going to explicitly call the layout system we have to ensure the
      // layout properties are loaded for the subtree since we're going around the root layout call
      // NOTE: Narflex - 12/7/11 - Call this even if we don't have a target focus rect because other state
      // variables ae updated in the layout methods which may be used in other calls such as SetFocusForVariable
      loadDynamicLayoutProps();
      doLayout();

    }
    if (numKids > 0 && x != null)
    {
      double minRectDist = Double.MAX_VALUE;
      java.awt.geom.Rectangle2D.Float maxRectOverlap = null;
      ZComp currWinner = null;
      for (int i = 0; i < numKids; i++)
      {
        ZComp focusTest = kids[i];
        java.util.ArrayList focusKids = Pooler.getPooledArrayList();//new java.util.ArrayList();
        focusTest.addFocusableChildrenToList(focusKids);
        for (int j = 0; j < focusKids.size(); j++)
        {
          focusTest = (ZComp) focusKids.get(j);
          java.awt.geom.Rectangle2D.Float currBounds = focusTest.getTrueBoundsf();
          boolean doesOverlap = currBounds.intersects(x);
          if (maxRectOverlap != null || doesOverlap)
          {
            if (!doesOverlap)
              continue;
            java.awt.geom.Rectangle2D.Float newInter = (java.awt.geom.Rectangle2D.Float)
                currBounds.createIntersection(x);
            // Don't go on the maximum area; go on the maximum percentage of area covered. That way if you
            // Have a 3 column item selected in the EPG; then do a unit left which causes a scroll; it'll then
            // pick the new one column item which has 100% overlap instead of picking the other one with more area
            // overlapped; but a smaller percentage of it being overlapped.
            if (maxRectOverlap == null ||
                newInter.width*newInter.height/(focusTest.boundsf.width*focusTest.boundsf.height) >
            maxRectOverlap.width*maxRectOverlap.height/(currWinner.boundsf.width*currWinner.boundsf.height))
            {
              if (focusTest instanceof ZPseudoComp && ((ZPseudoComp) focusTest).passesUpwardConditional())
              {
                maxRectOverlap = newInter;
                currWinner = focusTest;
              }
            }
          }
          else
          {
            double currDist = java.awt.geom.Point2D.distance(x.getCenterX(), x.getCenterY(),
                currBounds.getCenterX(), currBounds.getCenterY());
            if (currDist < minRectDist)
            {
              if (focusTest instanceof ZPseudoComp && ((ZPseudoComp) focusTest).passesUpwardConditional())
              {
                minRectDist = currDist;
                currWinner = focusTest;
              }
            }
          }
        }
        Pooler.returnPooledArrayList(focusKids);
      }
      topParent = getTopPseudoParent();
      if (currWinner != null)
      {
        // Be sure we actually have overlap and aren't catching a rounding error
        if (maxRectOverlap != null && maxRectOverlap.width > 0.1 && maxRectOverlap.height > 0.1)
          focusTargetRect = maxRectOverlap;
        else
          focusTargetRect = currWinner.getTrueBoundsf();
        ZComp oldFocuser = topParent.getFocusOwner(false);
        topParent.setFocus(currWinner);
        if (oldFocuser != currWinner)
        {
          ZPseudoComp scrollParent = getScrollingContainer(currWinner, ZDataTable.BOTH_DIMENSIONS, false);
          if (scrollParent != null)
          {
            scrollParent.ensureVisibilityForRect(currWinner.getTrueBoundsf());
          }
        }
      }
      else
      {
        // Check to make sure we ourselves are not currently focus owner in the proper region...this fixes
        // a bug where scrollable tables w/ no children would lose focus when scrolled
        if (!isFocusable() || !isFocused() || !getTrueBoundsf().equals(x))
        {
          focusTargetRect = null;
          if (!topParent.checkForcedFocus())
            topParent.setDefaultFocus();
        }
      }
    }
    else
    {
      focusTargetRect = null;
      topParent = getTopPseudoParent();
      if (!topParent.doesHierarchyHaveFocus())
      {
        if (!setDefaultFocus())
          topParent.setDefaultFocus();
      }
    }

    //		evaluateTree(false, true);
    //		if (topParent != null)
    //			topParent.evaluateFocusListeners(true); // needed for context listeners
    //		appendToDirty(true);
  }

  public boolean hasFreshlyLoadedContext() { return freshlyLoadedContext; }

  public boolean isFirstVPage() { return ((scrolling & ZDataTable.VERTICAL_DIMENSION) == 0) || (scrollPosY < 1); }
  public boolean isFirstHPage() { return ((scrolling & ZDataTable.HORIZONTAL_DIMENSION) == 0) || (scrollPosX < 1); }
  public boolean isLastVPage() { return ((scrolling & ZDataTable.VERTICAL_DIMENSION) == 0) ||
      (scrollPosY + boundsf.height >= scrollingHeight - 1); }
  public boolean isLastHPage() { return ((scrolling & ZDataTable.HORIZONTAL_DIMENSION) == 0) ||
      (scrollPosX + boundsf.width >= scrollingWidth - 1); }

  protected void updatePaginationContext(boolean wasFirstV, boolean wasLastV, boolean wasFirstH, boolean wasLastH)
  {
    if (scrolling == 0) return;
    // We set these in the parent context because we can't put paging controls in our
    // own Panel or it'll screw up the layout. This is similar to how ZDataTable works.
    boolean firstV = isFirstVPage();
    boolean firstH = isFirstHPage();
    boolean lastV = isLastVPage();
    boolean lastH = isLastHPage();
    ((ZPseudoComp)parent).relatedContext.setLocal("IsFirstPage", Boolean.valueOf(firstV && firstH));
    ((ZPseudoComp)parent).relatedContext.setLocal("IsFirstHPage", Boolean.valueOf(firstH));
    ((ZPseudoComp)parent).relatedContext.setLocal("IsFirstVPage", Boolean.valueOf(firstV));
    ((ZPseudoComp)parent).relatedContext.setLocal("IsLastPage", Boolean.valueOf(lastV && lastH));
    ((ZPseudoComp)parent).relatedContext.setLocal("IsLastVPage", Boolean.valueOf(lastV));
    ((ZPseudoComp)parent).relatedContext.setLocal("IsLastHPage", Boolean.valueOf(lastH));
    // Account for any floating point errors in the sizing calcs
    int numPages = (int)Math.ceil(Math.max((scrollingWidth - 1)/boundsf.width,
        (scrollingHeight - 1)/boundsf.height));
    int numHPages = Math.max(1, (int)Math.ceil((scrollingWidth - 1)/boundsf.width));
    int numVPages = Math.max(1, (int)Math.ceil((scrollingHeight - 1)/boundsf.height));
    ((ZPseudoComp)parent).relatedContext.setLocal("NumPages", new Integer(numPages));
    ((ZPseudoComp)parent).relatedContext.setLocal("NumHPages", new Integer(numHPages));
    ((ZPseudoComp)parent).relatedContext.setLocal("NumVPages", new Integer(numVPages));
    ((ZPseudoComp)parent).relatedContext.setLocal("NumPagesF", new Float(Math.max((scrollingWidth - 1)/boundsf.width,
        (scrollingHeight - 1)/boundsf.height)));
    ((ZPseudoComp)parent).relatedContext.setLocal("NumHPagesF", new Float(Math.max(1, (scrollingWidth - 1)/boundsf.width)));
    ((ZPseudoComp)parent).relatedContext.setLocal("NumVPagesF", new Float(Math.max(1, (scrollingHeight - 1)/boundsf.height)));
    if (scrollingWidth > boundsf.width)
      ((ZPseudoComp)parent).relatedContext.setLocal("HScrollIndex", new Float(scrollPosX * numHPages / Math.max(0, scrollingWidth - boundsf.width)));
    if (scrollingHeight > boundsf.height)
      ((ZPseudoComp)parent).relatedContext.setLocal("VScrollIndex", new Float(scrollPosY * numVPages / Math.max(0, scrollingHeight - boundsf.height)));
    if (lastPageState == null || lastPageState[0] != firstV ||
        lastPageState[1] != lastV || lastPageState[2] != firstH ||
        lastPageState[3] != lastH || lastPageStateNum == null ||
        lastPageStateNum[0] != (scrollPosX * numHPages / Math.max(1, Math.max(0, scrollingWidth - boundsf.width))) ||
        lastPageStateNum[1] != (scrollPosY * numVPages / Math.max(1, Math.max(0, scrollingHeight - boundsf.height))))
    {
      if (uiMgr.isXBMCCompatible())
        getTopPseudoParent().evaluatePagingListeners();
      else
        ((ZPseudoComp)parent).evaluatePagingListeners();
      if (lastPageState == null)
        lastPageState = new boolean[4];
      lastPageState[0] = firstV;
      lastPageState[1] = lastV;
      lastPageState[2] = firstH;
      lastPageState[3] = lastH;
      if (lastPageStateNum == null)
        lastPageStateNum = new float[2];
      lastPageStateNum[0] = (scrollPosX * numHPages / Math.max(1, Math.max(0, scrollingWidth - boundsf.width)));
      lastPageStateNum[1] = (scrollPosY * numVPages / Math.max(1, Math.max(0, scrollingHeight - boundsf.height)));
    }
  }

  protected float getScrollUnitAmount()
  {
    if (themeFont == null) return reality.getHeight()/10;
    return themeFont.getHeight()*3;
  }

  public ZPseudoComp getScrollingParent()
  {
    if (scrolling != 0)
      return this;
    if (parent instanceof ZPseudoComp)
      return ((ZPseudoComp)parent).getScrollingParent();
    return null;
  }

  public Widget getWidget() { return widg; }

  /*	public boolean setFocusForHierarchy(ZPseudoComp focusMe, boolean parentTookFocus)
	{
		// Find the top parent in our hierarchy first. Then set the focus for the new child on it.
		// Call focusLost on the currently focused child at the beginning
		// Call focusGained on the newly focused child at the end
		// Check the focusMe comp for a FocusChain attribute. If it doesn't have one then
		// clear the focusChain. If it does have one, and a component with the same
		// widget is already in the focusChain, then backup the focusChain to that comp
		// and replace it with this one.  If this comp isn't in the focusChain yet and its FocusChain
		// is set then just add it to the focusChain.
		// Only Menu & OptionsMenu comps will have focusChains in them.
		ZPseudoComp topParent = getTopPseudoParent();
		topParent.setFocus(focusMe, parentTookFocus);
		ZPseudoComp orgFocus = (ZPseudoComp) topParent.getFocusOwner(false);
		if (focusMe != null)
		{
			String chainName = (String) relatedContext.safeLookup("FocusChain");
			if (chainName != null)
			{
				if (focusChains == null)
					focusChains = new java.util.HashMap();
				java.util.Vector myFocusChain = (java.util.Vector) focusChains.get(chainName);
				int oldIndex = -1;
				if (myFocusChain == null)
				{
					myFocusChain = new java.util.Vector();
					focusChains.put(chainName, myFocusChain);
				}
				else
				{
					for (int i = 0; i < myFocusChain.size(); i++)
					{
						ZPseudoComp tc = (ZPseudoComp) myFocusChain.get(i);
						if (tc.widg == widg)
				}
				myFocusChain.add(this);
			}
		}

		// NOTE BUT NOW WE MISS SOME OF THE FOCUS EVENTS
		if (focusMe != orgFocus)
		{
			if (orgFocus != null && orgFocus.focusLostHook != null)
				Catbert.processHookDirectly(orgFocus.focusLostHook, null, orgFocus);
			if (focusMe != null && focusMe.focusGainedHook != null)
				Catbert.processHookDirectly(focusMe.focusGainedHook, null, focusMe);
		}
	}
   */
  public Widget getPropertyWidget()
  {
    return propWidg;
  }

  public boolean needsLoadCallback(Object obj)
  {
    // 04/20/12 - Narflex - This used to be IMAGE only...which makes sense because only images need image load callbacks. But
    // quite often in the UI we conditionalize display of text vs. image on the existence of an image; so in that case the text
    // widgets would not indicate they need a callback and block on the image load. Since I can't think of any cases where
    // we would actually want to block when this occurs; I'm changing this to allow Text widgets as well.
    return widgType == Widget.IMAGE || widgType == Widget.TEXT;
  }

  public boolean loadStillNeeded(Object obj)
  {
    // Just make sure we can get the lock so we know the initial call into the image loading is done
    synchronized (metaImageCallbackLock)
    {
    }
    if (widgType == Widget.IMAGE && isComponentInVisibleMenu() && passesUpwardConditional() && obj != null)
    {
      ZImage kidImage = (ZImage) kids[0];
      if (kidImage.isWaitingOnObject(obj))
      {
        return true;
      }
    }
    return false;
  }

  public void loadFinished(Object obj, boolean success)
  {
    // Just make sure we can get the lock so we know the initial call into the image loading is done
    synchronized (metaImageCallbackLock)
    {
    }
    if (/*success &&*/ widgType == Widget.IMAGE && isComponentInVisibleMenu() && passesUpwardConditional() && obj != null)
    {
      ZImage kidImage = (ZImage) kids[0];
      // Don't check against null here because if it happens that soon the regular evaluation system still will need to run
      if (kidImage.isWaitingOnObject(obj))
      {
        boolean[] gotLock = { false };
        if (uiMgr.getLock(true, gotLock, true))
        {
          //					if (Sage.DBG) System.out.println("UI Comp refreshing due to resource load finished of: " + obj);
          evaluateTree(false, true);
          appendToDirty(true);
          if (gotLock[0])
            uiMgr.clearLock();
        }
      }
    }
  }

  // Used when positioning a component inside a fixed size parent area
  private void positionComponentFullByProps(ZPseudoComp pseudoKid, float x, float y,
      float kidPrefWidth, float kidPrefHeight, float availWidth, float availHeight)
  {
    float targetX=x, targetY=y, targetWidth=availWidth, targetHeight=availHeight;
    if ((pseudoKid.validLayoutBits & FIXEDW_LAYOUT) != 0)
      targetWidth = pseudoKid.fixedWidth;
    else if ((pseudoKid.validLayoutBits & FILLX_LAYOUT) != 0)
      targetWidth = availWidth * pseudoKid.fillX;
    if ((pseudoKid.validLayoutBits & FIXEDH_LAYOUT) != 0)
      targetHeight = pseudoKid.fixedHeight;
    else if ((pseudoKid.validLayoutBits & FILLY_LAYOUT) != 0)
      targetHeight = availHeight * pseudoKid.fillY;
    if (reality.isIntegerPixels())
    {
      targetWidth = (int)(targetWidth);
      targetHeight = (int)(targetHeight);
    }
    if (pseudoKid.anchorPointX >= 0)
    {
      if ((pseudoKid.validLayoutBits & ABSOLUTEX_LAYOUT) != 0)
        targetX += pseudoKid.absoluteX - targetWidth*pseudoKid.anchorPointX;
      else if ((pseudoKid.validLayoutBits & ANCHORX_LAYOUT) != 0)
        targetX += pseudoKid.anchorX * availWidth - targetWidth*pseudoKid.anchorPointX;
    }
    else
    {
      if ((pseudoKid.validLayoutBits & ABSOLUTEX_LAYOUT) != 0)
        targetX += pseudoKid.absoluteX;
      else if ((pseudoKid.validLayoutBits & ANCHORX_LAYOUT) != 0)
        targetX += pseudoKid.anchorX * (availWidth - targetWidth);
    }
    if (pseudoKid.anchorPointY >= 0)
    {
      if ((pseudoKid.validLayoutBits & ABSOLUTEY_LAYOUT) != 0)
        targetY += pseudoKid.absoluteY - targetHeight*pseudoKid.anchorPointY;
      else if ((pseudoKid.validLayoutBits & ANCHORY_LAYOUT) != 0)
        targetY += pseudoKid.anchorY * availHeight - targetHeight*pseudoKid.anchorPointY;
    }
    else
    {
      if ((pseudoKid.validLayoutBits & ABSOLUTEY_LAYOUT) != 0)
        targetY += pseudoKid.absoluteY;
      else if ((pseudoKid.validLayoutBits & ANCHORY_LAYOUT) != 0)
        targetY += pseudoKid.anchorY * (availHeight - targetHeight);
    }
    pseudoKid.setBounds(targetX, targetY, targetWidth, targetHeight);
  }

  public UIManager getUIMgr() { return uiMgr; }

  protected float getBGAlpha()
  {
    if (widgType == Widget.IMAGE)
      return propWidg.getFloatProperty(Widget.FOREGROUND_ALPHA, 1.0f, null, this);
    else if (propWidg.hasProperty(Widget.BACKGROUND_ALPHA))
    {
      int bgAlpha = propWidg.getIntProperty(Widget.BACKGROUND_ALPHA, 255, null, this);
      return bgAlpha/255.0f;
    }
    else
      return 1.0f;
  }

  public boolean setupAnimation(String widgName, String surfName, String animName, long dur, long delay, boolean interruptable, boolean checkAll)
  {
    boolean rv = false;
    if ((widgName == null || widgName.equals(widg.getName()) || widgName.equals(propWidg.getName())) && surfName.equals(surfaceCache))
    {
      // We found ourself!
      // Check for component level alpha
      if (pendingAnimations == null)
        pendingAnimations = new java.util.ArrayList();
      pendingAnimations.add(new RenderingOp(surfName, animName, dur, delay, getTrueBoundsf(), getBGAlpha(), interruptable));
      rv = true;
      if (!checkAll)
        return rv;
    }
    ZComp[] kidlets = getZOrderCache();
    for (int i = Math.min(numKids - 1, kidlets.length); i >= 0; i--)
    {
      if (kidlets[i] instanceof ZPseudoComp)
      {
        if (((ZPseudoComp)kidlets[i]).setupAnimation(widgName, surfName, animName, dur, delay, interruptable, checkAll))
        {
          rv = true;
          if (!checkAll)
            return rv;
        }
      }
    }
    return rv;
  }

  public RenderingOp getTransitionSourceOp(String widgName, String surfName, String animName, long dur, long delay, boolean interruptable)
  {
    if ((widgName == null || widgName.equals(widg.getName()) || widgName.equals(propWidg.getName())) && surfName.equals(surfaceCache))
    {
      // We found ourself!
      return new RenderingOp(surfName, animName, dur, delay, getTrueBoundsf(), getBGAlpha(), interruptable);
    }
    ZComp[] kidlets = getZOrderCache();
    for (int i = Math.min(numKids - 1, kidlets.length); i >= 0; i--)
    {
      if (kidlets[i] instanceof ZPseudoComp)
      {
        RenderingOp rop = ((ZPseudoComp)kidlets[i]).getTransitionSourceOp(widgName, surfName, animName, dur, delay, interruptable);
        if (rop != null)
        {
          return rop;
        }
      }
    }
    return null;
  }

  public boolean setupTransitionAnimation(String widgName, String surfName, RenderingOp rop)
  {
    if ((widgName == null || widgName.equals(widg.getName()) || widgName.equals(propWidg.getName())) && surfName.equals(surfaceCache))
    {
      // We found ourself!
      // Check for component level alpha
      if (pendingAnimations == null)
        pendingAnimations = new java.util.ArrayList();
      pendingAnimations.add(rop);
      return true;
    }
    ZComp[] kidlets = getZOrderCache();
    for (int i = Math.min(numKids - 1, kidlets.length); i >= 0; i--)
    {
      if (kidlets[i] instanceof ZPseudoComp)
      {
        if (((ZPseudoComp)kidlets[i]).setupTransitionAnimation(widgName, surfName, rop))
        {
          return true;
        }
      }
    }
    return false;
  }

  public boolean setupAnimationVar(String widgName, String surfName, String varName, Object varValue,
      String animName, long dur, long delay, boolean interruptable, boolean checkAll)
  {
    boolean rv = false;
    if ((widgName == null || widgName.equals(widg.getName()) || widgName.equals(propWidg.getName())) && surfName.equals(surfaceCache))
    {
      Object myValue = relatedContext.safeLookup(varName);
      if (myValue == varValue || (myValue != null && myValue.equals(varValue)))
      {
        if (pendingAnimations == null)
          pendingAnimations = new java.util.ArrayList();
        pendingAnimations.add(new RenderingOp(surfaceCache, animName, dur, delay, getTrueBoundsf(), getBGAlpha(), interruptable));
        rv = true;
        if (!checkAll)
          return rv;
      }
      else if (myValue instanceof Long[])
      {
        long testTime = ZDataTable.getTimeFromObject(varValue);
        Long[] testBounds = (Long[]) myValue;
        if (testBounds.length == 2 && testBounds[0].longValue() <= testTime && testBounds[1].longValue() > testTime)
        {
          if (pendingAnimations == null)
            pendingAnimations = new java.util.ArrayList();
          pendingAnimations.add(new RenderingOp(surfaceCache, animName, dur, delay, getTrueBoundsf(), getBGAlpha(), interruptable));
          rv = true;
          if (!checkAll)
            return rv;
        }
      }
    }
    ZComp[] kidlets = getZOrderCache();
    for (int i = Math.min(numKids - 1, kidlets.length); i >= 0; i--)
    {
      if (kidlets[i] instanceof ZPseudoComp)
      {
        if (((ZPseudoComp)kidlets[i]).setupAnimationVar(widgName, surfName, varName, varValue, animName, dur, delay, interruptable, checkAll))
        {
          rv = true;
          if (!checkAll)
            return rv;
        }
      }
    }
    return false;
  }

  public boolean hasVisibleNonVideoChildren()
  {
    for (int i = 0; i < numKids; i++)
    {
      if (kids[i] instanceof ZPseudoComp)
      {
        ZPseudoComp zp = (ZPseudoComp) kids[i];
        if (zp.widgType != Widget.VIDEO && zp.passesConditional())
          return true;
      }
      else
        return true;
    }
    return false;
  }

  public ZLabel findFirstLabelChild()
  {
    for (int i = 0; i < numKids; i++)
    {
      if (kids[i] instanceof ZPseudoComp)
      {
        ZLabel rv = ((ZPseudoComp) kids[i]).findFirstLabelChild();
        if (rv != null)
          return rv;
      }
      else if ((kids[i] instanceof ZLabel) && !(kids[i] instanceof ZCCLabel))
        return (ZLabel) kids[i];
    }
    return null;
  }

  public boolean isMenuLoadingState()
  {
    return menuLoadedState;
  }

  public void setMenuLoadedState(boolean x)
  {
    menuLoadedState = x;
  }

  public void setMenuUnloadedState(boolean x)
  {
    menuUnloadedState = x;
  }

  public boolean hasMenuUnloadEffects()
  {
    return hasMenuUnloadEffects;
  }

  private void setHasMenuUnloadEffects()
  {
    if (!hasMenuUnloadEffects)
    {
      hasMenuUnloadEffects = true;
      if (parent instanceof ZPseudoComp)
      {
        ZPseudoComp tempParent = (ZPseudoComp) parent;
        while (tempParent != null)
        {
          if (!tempParent.hasMenuUnloadEffects)
          {
            tempParent.hasMenuUnloadEffects = true;
            if (tempParent.parent instanceof ZPseudoComp)
              tempParent = (ZPseudoComp) tempParent.parent;
            else
              break;
          }
          else
            break;
        }
      }
    }
  }

  public boolean isWithinScreenBounds()
  {
    // Return true here for zero sized components since they haven't had their layout done yet so we won't know if
    // they are onscreen; and the main purpose of this is to handle partially visible components that are not
    // supposed to take focus
    if (getWidth() == 0 && getHeight() == 0)
      return true;
    float txf = getTrueXf();
    float tyf = getTrueYf();
    return txf > reality.getUIMinX() - 5 && tyf > reality.getUIMinY() - 5 &&
        txf + getWidthf() < reality.getUIMaxX() + 5 && tyf + getHeightf() < reality.getUIMaxY() + 5;
  }

  protected UIManager uiMgr;
  protected Widget widg;
  protected byte widgType; // for faster access
  protected Widget propWidg;
  // currTheme is where the BG information is taken from, so it handles the
  // non-propogating theme properties.
  protected Widget currTheme;
  // This is the built up Theme chain from our parents
  protected java.util.ArrayList defaultThemes;
  private boolean generalReleaseOK;
  private boolean itemReleaseOK;

  protected java.util.Set parentActions;
  protected boolean parentActionsMayBeConditional;
  protected Widget rootParentAction;
  protected Widget conditionalUIWidg;
  //protected boolean passesConditionalIsCached = false;
  protected boolean passesConditionalCacheValue = false;

  private int validLayoutBits;
  private static final int FILLX_LAYOUT = 0x1;
  private static final int FILLY_LAYOUT = 0x2;
  private static final int ANCHORX_LAYOUT = 0x4;
  private static final int ANCHORY_LAYOUT = 0x8;
  private static final int ABSOLUTEX_LAYOUT = 0x10;
  private static final int ABSOLUTEY_LAYOUT = 0x20;
  private static final int FIXEDW_LAYOUT = 0x40;
  private static final int FIXEDH_LAYOUT = 0x80;
  private float fillX = -1;
  private float fillY = -1;
  private float anchorX = -1;
  private float anchorY = -1;
  private float anchorPointX = -1;
  private float anchorPointY = -1;
  private int absoluteX = -1;
  private int absoluteY = -1;
  private int fixedWidth = -1;
  private int fixedHeight = -1;
  protected float padX = 0;
  protected float padY = 0;
  private boolean focusable;
  boolean dynamicFgColor;
  java.awt.Color originalFgColor;
  boolean dynamicFocusFgColor;
  java.awt.Color focusFgColor;
  boolean dynamicFgShadowColor;
  java.awt.Color originalFgShadowColor;
  boolean dynamicFocusFgShadowColor;
  java.awt.Color focusFgShadowColor;
  boolean dynamicOriginalBgColor;
  private java.awt.Color originalBgColor;
  boolean dynamicFocusBgColor;
  private java.awt.Color focusBgColor;
  private boolean useFocusBGImage;

  private int diffuseRenderColor;

  boolean dynamicThemeFontFace;
  boolean dynamicThemeFontSize;
  boolean dynamicThemeFontStyle;

  private java.util.Map compToActionMap;
  protected Widget[] ueListenMap;

  protected Catbert.Context relatedContext;

  protected boolean checkForFocus;
  protected int focusListener;
  protected boolean pagingListener;
  protected boolean nextTransitionListener;
  protected boolean prevTransitionListener;

  protected long animStart;
  protected boolean hasAnimation;
  protected boolean dynamicAnimation;
  protected long initAnimDelay;
  protected long animFreq;
  protected long animDuration;
  protected long lastAnimTime;

  protected java.awt.geom.Rectangle2D.Float focusTargetRect;

  private java.util.ArrayList tempOpList;
  private java.awt.geom.Rectangle2D.Float tempClipRect;
  private int tempDiffuseColor;
  private float tempAlphaFactor;
  //	private javax.vecmath.Matrix4f tempRenderXform;
  private float tempRenderXoff;
  private float tempRenderYoff;

  protected float cachedPrefParentWidth;
  protected float cachedPrefParentHeight;

  private boolean freshlyLoadedContext;

  private int scrolling;
  private float scrollPosX;
  private float scrollPosY;
  private float scrollingWidth;
  private float scrollingHeight;
  private boolean lastLeanUpLeft = true;

  protected MetaFont themeFont;
  //protected java.awt.FontMetrics themeFontMetrics;

  protected boolean[] lastPageState;
  protected float[] lastPageStateNum;

  protected Widget renderStartHook;
  protected Widget layoutStartHook;
  protected Widget focusGainedHook;
  protected Widget focusLostHook;

  private Widget containerTheme;
  private Widget[] attWidgList;

  //	protected java.util.Map focusChains;

  private Object metaImageCallbackLock = new Object();

  private boolean abortedLayoutPropLoad = false;

  protected String surfaceCache;
  protected java.util.ArrayList pendingAnimations;
  private java.util.ArrayList postFocusProcessing; // cached for performance
  private boolean processingScroll; // so we don't do multiple animation ops

  protected RenderingOp scrollTracker; // for scrolling effects
  protected java.util.ArrayList cachedScrollOps; // For rendering what's scrolling off the screen

  private boolean disableFocus;

  // True during the first pass when MenuLoaded effects should fire
  private boolean menuLoadedState;
  // True during the last pass when MenuUnloaded effects should fire
  private boolean menuUnloadedState;

  private boolean hasMenuUnloadEffects;

  // Last time this item widget was selected for action
  private long lastSelectTime;
}
