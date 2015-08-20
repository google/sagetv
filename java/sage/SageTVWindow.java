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

public class SageTVWindow extends java.awt.Frame implements
java.awt.event.ActionListener, java.awt.event.MouseListener,
java.awt.event.MouseMotionListener, java.awt.LayoutManager
{
  public static final java.awt.Color OTHER_GRAY = new java.awt.Color(212, 208, 200);
  private static java.awt.Color TITLE_BG_COLOR;
  private static java.awt.Color TITLE_DISABLE_COLOR;

  private static java.awt.Color[] LEFT_WIN_BORDER;
  private static java.awt.Color[] BOTTOM_WIN_BORDER;
  private static java.awt.Color[] RIGHT_WIN_BORDER;
  private static java.awt.Color[] LEFT_WIN_BORDER_INACTIVE;
  private static java.awt.Color[] BOTTOM_WIN_BORDER_INACTIVE;
  private static java.awt.Color[] RIGHT_WIN_BORDER_INACTIVE;

  public static final int NO_DECORATIONS = 2;
  public static final int NO_TITLE_DECORATION = 1;

  public static final int WINXP_TITLE_STYLE = 1;
  public static final int WIN2K_TITLE_STYLE = 2;
  public static final int MAC_TITLE_STYLE = 3;

  public static final int PLATFORM_TITLE_STYLE = 10;

  public SageTVWindow(String title)
  {
    this(title, 0);
  }
  public SageTVWindow(String title, int prefTitleStyle)
  {
    super(title);
    titleStyle = prefTitleStyle;
    if (titleStyle != WINXP_TITLE_STYLE && titleStyle != WIN2K_TITLE_STYLE && titleStyle != MAC_TITLE_STYLE && titleStyle != PLATFORM_TITLE_STYLE)
    {
      if (System.getProperty("os.name").toLowerCase().indexOf("mac os x") != -1)
        titleStyle = PLATFORM_TITLE_STYLE;
      else if (System.getProperty("os.name").toLowerCase().indexOf("windows") == -1 ||
          System.getProperty("os.name").toLowerCase().indexOf("2000") != -1)
        titleStyle = WIN2K_TITLE_STYLE;
      else
        titleStyle = WINXP_TITLE_STYLE;
    }
    if (minWindowImageBG == null)
    {
      if (titleStyle == MAC_TITLE_STYLE)
      {
        bgImage = loadMyImage("images/MacTitleBarBackground.gif");
        bgImageInactive = loadMyImage("images/MacTitleBarBackgroundInactive.gif");
        closeButtonRedBGActive = loadMyImage("images/MacTitleButtonCloseActive.gif");
        minWindowImageBGDisabled = maxWindowImageBGDisabled = closeButtonRedBGDisabled = loadMyImage("images/MacTitleButtonDisabled.gif");
        minWindowImageBG = loadMyImage("images/MacTitleButtonMinActive.gif");
        maxWindowImageBG = loadMyImage("images/MacTitleButtonMaxActive.gif");
        TITLE_BG_COLOR = new java.awt.Color(221, 221, 221);
        TITLE_DISABLE_COLOR = new java.awt.Color(238, 238, 238);

        RIGHT_WIN_BORDER = BOTTOM_WIN_BORDER = LEFT_WIN_BORDER = new java.awt.Color[] { new java.awt.Color(221, 221, 221),
            new java.awt.Color(221, 221, 221), new java.awt.Color(221, 221, 221) };
        RIGHT_WIN_BORDER_INACTIVE = BOTTOM_WIN_BORDER_INACTIVE = LEFT_WIN_BORDER_INACTIVE = new java.awt.Color[] { new java.awt.Color(238, 238, 238),
            new java.awt.Color(238, 238, 238), new java.awt.Color(238, 238, 238) };
      }
      else if (titleStyle == WIN2K_TITLE_STYLE)
      {
        TITLE_BG_COLOR = new java.awt.Color(112, 161, 202);
        TITLE_DISABLE_COLOR = java.awt.Color.gray;;
        closeButtonRedBGActive = loadMyImage("images/CloseWindow.gif");
        minWindowImageBG = loadMyImage("images/MinWindow.gif");

        RIGHT_WIN_BORDER = BOTTOM_WIN_BORDER = LEFT_WIN_BORDER = new java.awt.Color[] { null, null, null };
        RIGHT_WIN_BORDER_INACTIVE = BOTTOM_WIN_BORDER_INACTIVE = LEFT_WIN_BORDER_INACTIVE = new java.awt.Color[] { null, null, null };
      }
      else if (titleStyle == WINXP_TITLE_STYLE)
      {
        bgImage = loadMyImage("images/WinTitleBarBackground.gif");
        bgImageInactive = loadMyImage("images/WinTitleBarBackgroundInactive.gif");
        closeButtonRedBGActive = loadMyImage("images/WinTitleButtonRedBGActive.png");
        closeButtonRedBGDisabled = loadMyImage("images/WinTitleButtonRedBGDisabled.png");
        maxWindowImageBG = minWindowImageBG = loadMyImage("images/WinTitleButtonBGActive.png");
        maxWindowImageBGDisabled = minWindowImageBGDisabled = loadMyImage("images/WinTitleButtonBGDisabled.png");
        TITLE_BG_COLOR = new java.awt.Color(8, 85, 221);
        TITLE_DISABLE_COLOR = new java.awt.Color(117, 134, 220);

        LEFT_WIN_BORDER = new java.awt.Color[] { new java.awt.Color(0, 25, 207),
            new java.awt.Color(8, 49, 217), new java.awt.Color(22, 106, 238), new java.awt.Color(8, 85, 221) };
        BOTTOM_WIN_BORDER = new java.awt.Color[] { new java.awt.Color(0, 19, 140),
            new java.awt.Color(0, 30, 160), new java.awt.Color(4, 65, 216), new java.awt.Color(7, 79, 234) };
        RIGHT_WIN_BORDER = new java.awt.Color[] { new java.awt.Color(0, 19, 140),
            new java.awt.Color(0, 29, 160), new java.awt.Color(0, 61, 220), new java.awt.Color(0, 72, 241) };
        LEFT_WIN_BORDER_INACTIVE = new java.awt.Color[] { new java.awt.Color(91, 104, 205),
            new java.awt.Color(116, 128, 220), new java.awt.Color(117, 140, 221), new java.awt.Color(117, 140, 220) };
        BOTTOM_WIN_BORDER_INACTIVE = new java.awt.Color[] { new java.awt.Color(79, 83, 188),
            new java.awt.Color(109, 116, 205), new java.awt.Color(117, 135, 221), new java.awt.Color(117, 134, 220) };
        RIGHT_WIN_BORDER_INACTIVE = new java.awt.Color[] { new java.awt.Color(79, 83, 188),
            new java.awt.Color(109, 116, 205), new java.awt.Color(117, 135, 221), new java.awt.Color(117, 134, 220) };
      }
      else
        RIGHT_WIN_BORDER = BOTTOM_WIN_BORDER = LEFT_WIN_BORDER =
        RIGHT_WIN_BORDER_INACTIVE = BOTTOM_WIN_BORDER_INACTIVE = LEFT_WIN_BORDER_INACTIVE =
        new java.awt.Color[0];

    }
    if (titleStyle == WINXP_TITLE_STYLE)
    {
      closeButton = new ActiveImage(closeButtonRedBGActive, loadMyImage("images/WinTitleButtonRedBGHover.png"))
      {
        public void paint(java.awt.Graphics g)
        {
          super.paint(g);
          java.awt.Color oldColor = g.getColor();
          ((java.awt.Graphics2D) g).setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
              java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
          ((java.awt.Graphics2D) g).setStroke(new java.awt.BasicStroke(2));
          g.setColor(pressed ? new java.awt.Color(223, 154, 136) : java.awt.Color.white);
          g.drawLine(6, 6, getWidth() - 7, getHeight() - 7);
          g.drawLine(6, getHeight() - 7, getWidth() - 7, 6);
          g.setColor(oldColor);
        }
      };
      closeButton.setPressedImage(loadMyImage("images/WinTitleButtonRedBGPressed.png"));
      java.awt.Image minWindowImageBGPressed = loadMyImage("images/WinTitleButtonBGPressed.png");
      java.awt.Image minWindowImageBGRollover = loadMyImage("images/WinTitleButtonBGHover.png");
      minButton = new ActiveImage(minWindowImageBG, minWindowImageBGRollover)
      {
        public void paint(java.awt.Graphics g)
        {
          super.paint(g);
          java.awt.Color oldColor = g.getColor();
          g.setColor(pressed ? new java.awt.Color(120, 162, 216) : java.awt.Color.white);
          g.fillRect(5, getHeight() - 8, 7, 3);
          g.setColor(oldColor);
        }
      };
      minButton.setPressedImage(minWindowImageBGPressed);
      maxButton = new ActiveImage(minWindowImageBG, minWindowImageBGRollover)
      {
        public void paint(java.awt.Graphics g)
        {
          super.paint(g);
          java.awt.Color oldColor = g.getColor();
          g.setColor(pressed ? new java.awt.Color(120, 162, 216) : java.awt.Color.white);
          g.drawLine(5, 5, 5, getHeight() - 6);
          g.drawLine(5, getHeight() - 6, getWidth() - 6, getHeight() - 6);
          g.drawLine(getWidth() - 6, 5, getWidth() - 6, getHeight() - 6);
          g.fillRect(6, 5, 9, 3);
          g.setColor(oldColor);
        }
      };
      maxButton.setPressedImage(minWindowImageBGPressed);
    }
    else if (titleStyle == WIN2K_TITLE_STYLE)
    {
      closeButton = new ActiveImage(closeButtonRedBGActive);
      closeButton.setPressedImage(loadMyImage("images/CloseWindowPressed.gif"));
      minButton = new ActiveImage(minWindowImageBG);
      minButton.setPressedImage(loadMyImage("images/MinWindowPressed.gif"));
    }
    else if (titleStyle == MAC_TITLE_STYLE)
    {
      closeButton = new ActiveImage(closeButtonRedBGActive, loadMyImage("images/MacTitleButtonCloseHover.gif"));
      closeButton.setPressedImage(loadMyImage("images/MacTitleButtonClosePressed.gif"));
      java.awt.Image minWindowImageBGPressed = loadMyImage("images/MacTitleButtonMinPressed.gif");
      java.awt.Image maxWindowImageBGPressed = loadMyImage("images/MacTitleButtonMaxPressed.gif");
      java.awt.Image minWindowImageBGRollover = loadMyImage("images/MacTitleButtonMinHover.gif");
      java.awt.Image maxWindowImageBGRollover = loadMyImage("images/MacTitleButtonMaxHover.gif");
      minButton = new ActiveImage(minWindowImageBG, minWindowImageBGRollover);
      minButton.setPressedImage(minWindowImageBGPressed);
      maxButton = new ActiveImage(maxWindowImageBG, maxWindowImageBGRollover);
      maxButton.setPressedImage(maxWindowImageBGPressed);
    }

    setLayout(this);
    if (titleStyle != PLATFORM_TITLE_STYLE)
      setUndecorated(true);
    addNotify();
    fullScreen = false;
    fsScreen = null;

    if (titleStyle == WIN2K_TITLE_STYLE)
      titleBar = new java.awt.Panel();
    else if (titleStyle != PLATFORM_TITLE_STYLE)
      titleBar = new java.awt.Panel()
    {
      public void update(java.awt.Graphics g)
      {
        paint(g);
      }
      public void paint(java.awt.Graphics g)
      {
        g.drawImage(SageTVWindow.this.isFocused() ? bgImage : bgImageInactive, 0, 0, getWidth(), getHeight(), null);
      }
    };

    mainPanel = new java.awt.Panel();
    if (titleStyle != PLATFORM_TITLE_STYLE)
    {
      titleBar.setFocusable(false);
      titleBar.setBackground(TITLE_BG_COLOR);
      titleBar.setLayout(new java.awt.GridBagLayout());
      java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();

      minButton.addActionListener(this);
      minButton.setFocusable(false);
      if (maxButton != null)
      {
        maxButton.addActionListener(this);
        maxButton.setFocusable(false);
      }
      closeButton.addActionListener(this);
      closeButton.setFocusable(false);
      if (titleStyle == WINXP_TITLE_STYLE)
      {
        // Make it 3 so we can fit 2 extra child components on the title bar
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;
        gbc.ipadx = 0;
        gbc.ipady = 0;
        gbc.weightx = 0;
        gbc.weighty = 1;
        gbc.fill = java.awt.GridBagConstraints.NONE;
        gbc.insets = new java.awt.Insets(6, 0, 3, 2);
        gbc.anchor = java.awt.GridBagConstraints.EAST;
        titleBar.add(minButton, gbc);

        gbc.gridx++;
        titleBar.add(maxButton, gbc);

        gbc.gridx++;
        titleBar.add(closeButton, gbc);
      }
      else if (titleStyle == WIN2K_TITLE_STYLE)
      {
        // Make it 3 so we can fit 2 extra child components on the title bar
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;
        gbc.ipadx = 0;
        gbc.ipady = 0;
        gbc.weightx = 0;
        gbc.weighty = 1;
        gbc.fill = java.awt.GridBagConstraints.NONE;
        gbc.anchor = java.awt.GridBagConstraints.EAST;
        titleBar.add(minButton, gbc);

        gbc.gridx++;
        titleBar.add(closeButton, gbc);
      }
      else
      {
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;
        gbc.ipadx = 0;
        gbc.ipady = 0;
        gbc.weightx = 0;
        gbc.weighty = 1;
        gbc.fill = java.awt.GridBagConstraints.NONE;
        gbc.insets = new java.awt.Insets(4, 5, 3, 2);
        gbc.anchor = java.awt.GridBagConstraints.EAST;
        titleBar.add(closeButton, gbc);

        gbc.gridx++;
        titleBar.add(minButton, gbc);

        gbc.gridx++;
        titleBar.add(maxButton, gbc);
      }

      /*
       * IMPORTANT: This uses the multi line label instead of java.awt.Label because
       * of JDC Bug# 4083025
       */
      if (titleStyle == WIN2K_TITLE_STYLE)
        titleLabel = new MultiLineLabel(title, new java.awt.Font("Arial", java.awt.Font.BOLD, 16), false,
            0, 0.5f);
      else
        titleLabel = new MultiLineLabel(title, new java.awt.Font("Arial", java.awt.Font.BOLD, 16), false,
            (titleStyle == MAC_TITLE_STYLE) ? 0.5f : 0, 0.5f)
      {
        public void paint(java.awt.Graphics g)
        {
          g.drawImage(SageTVWindow.this.isFocused() ? bgImage : bgImageInactive, 0, 0, getWidth(), getHeight(), null);
          super.paint(g);
        }
      };
      titleLabel.addMouseListener(this);
      titleLabel.setForeground((titleStyle == MAC_TITLE_STYLE) ? java.awt.Color.black : java.awt.Color.white);
      titleLabel.addMouseMotionListener(this);
      titleLabel.setFocusable(false);
      gbc.weightx = 1;
      gbc.fill = java.awt.GridBagConstraints.BOTH;
      if (titleStyle == MAC_TITLE_STYLE)
      {
        gbc.gridx++;
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
      }
      else
      {
        gbc.gridx = 0;
        gbc.insets = new java.awt.Insets(0, 5, 0, 0);
      }
      titleBar.add(titleLabel, gbc);


      add(titleBar, "North");

      addMouseListener(this);
      addMouseMotionListener(this);
      addWindowFocusListener(new java.awt.event.WindowFocusListener()
      {
        public void windowGainedFocus(java.awt.event.WindowEvent evt)
        {
          if (titleStyle != WIN2K_TITLE_STYLE)
          {
            closeButton.setImage(closeButtonRedBGActive);
            minButton.setImage(minWindowImageBG);
            if (maxButton != null)
              maxButton.setImage(maxWindowImageBG);
          }
          titleBar.setBackground(TITLE_BG_COLOR);
          titleLabel.setForeground((titleStyle == MAC_TITLE_STYLE) ? java.awt.Color.black : java.awt.Color.white);
          titleLabel.invalidate();
          titleLabel.repaint();
          SageTVWindow.this.invalidate();
          SageTVWindow.this.repaint();
          invalidateExtraComponents();
          titleBar.validate();
          titleBar.repaint();
        }

        public void windowLostFocus(java.awt.event.WindowEvent evt)
        {
          if (titleStyle != WIN2K_TITLE_STYLE)
          {
            closeButton.setImage(closeButtonRedBGDisabled);
            minButton.setImage(minWindowImageBGDisabled);
            if (maxButton != null)
              maxButton.setImage(maxWindowImageBGDisabled);
          }
          titleBar.setBackground(TITLE_DISABLE_COLOR);
          titleLabel.setForeground((titleStyle == MAC_TITLE_STYLE) ? java.awt.Color.gray : java.awt.Color.white);
          titleLabel.invalidate();
          titleLabel.repaint();
          SageTVWindow.this.invalidate();
          SageTVWindow.this.repaint();
          invalidateExtraComponents();
          titleBar.validate();
          titleBar.repaint();
        }
      });
    }

    addComponentListener(new java.awt.event.ComponentListener()
    {
      public void componentResized(java.awt.event.ComponentEvent e)
      {
        lastScreenBounds = null;
      }

      public void componentMoved(java.awt.event.ComponentEvent e)
      {
        lastScreenBounds = null;
      }

      public void componentShown(java.awt.event.ComponentEvent e)
      {
        lastScreenBounds = null;
      }

      public void componentHidden(java.awt.event.ComponentEvent e)
      {
        lastScreenBounds = null;
      }
    });

    add(mainPanel, "Center");
  }

  protected void invalidateExtraComponents()
  {
  }

  public void dispose()
  {
    if(System.getProperty("os.name").toLowerCase().indexOf("mac os x") != -1) {
      removeNotify();		// order seems to matter, we crash if we call super.dispose() first
      super.dispose();
    } else {
      super.dispose();
      removeNotify();
    }
  }

  public java.awt.Insets getInsets()
  {
    return fullScreen ? new java.awt.Insets(0, 0, 0, 0) :
      ((titleStyle == PLATFORM_TITLE_STYLE) ? super.getInsets() : new java.awt.Insets(titleStyle == WIN2K_TITLE_STYLE ? 3 : 0, LEFT_WIN_BORDER.length,
          BOTTOM_WIN_BORDER.length, RIGHT_WIN_BORDER.length));
  }

  public void paint(java.awt.Graphics g)
  {
    if (fullScreen || titleStyle == PLATFORM_TITLE_STYLE)
    {
      super.paint(g);
      return;
    }

    java.awt.Color oldColor = g.getColor();
    int x = 0;
    int y = 0;
    int width = getWidth();
    int height = getHeight();
    if (titleStyle == WIN2K_TITLE_STYLE)
    {
      g.setColor(java.awt.Color.darkGray);
      g.drawRect(x, y, width, height);
      width--;
      height--;
      g.setColor(OTHER_GRAY);
      g.drawRect(x, y, width, height);
      x++;
      y++;
      width--;
      height--;
      g.setColor(java.awt.Color.gray);
      g.drawRect(x, y, width, height);
      width--;
      height--;
      g.setColor(java.awt.Color.white);
      g.drawRect(x, y, width, height);
      x++;
      y++;
      width--;
      height--;
      g.setColor(OTHER_GRAY);
      g.drawRect(x, y, width, height);
    }
    else
    {
      for (int i = 0; i < LEFT_WIN_BORDER.length; i++)
      {
        g.setColor(isFocused() ? LEFT_WIN_BORDER[i] : LEFT_WIN_BORDER_INACTIVE[i]);
        g.drawLine(i, titleBar.getHeight(), i, height - i - 1);
      }
      for (int i = 0; i < BOTTOM_WIN_BORDER.length; i++)
      {
        g.setColor(isFocused() ? BOTTOM_WIN_BORDER[i] : BOTTOM_WIN_BORDER_INACTIVE[i]);
        g.drawLine(i, height - i - 1, width - i - 1, height - i - 1);
      }
      for (int i = 0; i < RIGHT_WIN_BORDER.length; i++)
      {
        g.setColor(isFocused() ? RIGHT_WIN_BORDER[i] : RIGHT_WIN_BORDER_INACTIVE[i]);
        g.drawLine(width - i - 1, titleBar.getHeight(), width - i - 1, height - i - 1);
      }
      g.drawImage(isFocused() ? bgImage : bgImageInactive, 0, 0, LEFT_WIN_BORDER.length, titleBar.getHeight(), null);
      g.drawImage(isFocused() ? bgImage : bgImageInactive, width - LEFT_WIN_BORDER.length, 0, LEFT_WIN_BORDER.length, titleBar.getHeight(), null);
    }
    g.setColor(oldColor);
    super.paint(g);
  }

  public boolean isFullScreen() { return fullScreen; }

  protected java.awt.GraphicsDevice getCurrentGraphicsDevice()
  {
    /*
     * Because of multiple monitors, we need to go through all of the
     * virtual display devices and find the one that we occupy the most
     * area in. Then we set our bounds to be the bounds of that
     * display device.
     */
    java.awt.GraphicsDevice[] screens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    int biggestArea = 0;
    java.awt.GraphicsDevice bestScreen = null;
    java.awt.Rectangle mb = getBounds();

    for (int i = 0; i < screens.length; i++)
    {
      java.awt.Rectangle sb = screens[i].getDefaultConfiguration().getBounds();
      if(sb.intersects(mb)) {
        java.awt.Rectangle currOverlap = sb.intersection(mb);
        if (currOverlap.width * currOverlap.height > biggestArea)
        {
          biggestArea = currOverlap.width * currOverlap.height;
          bestScreen = screens[i];
        }
      }
    }
    return bestScreen;
  }

  public java.awt.Rectangle getCurrScreenBounds()
  {
    java.awt.Rectangle rv = lastScreenBounds;
    if (rv != null)
      return rv;
    if(fsScreen != null) {
      // always use the selected screen bounds in FS mode (Mac only for now...)
      return lastScreenBounds = fsScreen.getDefaultConfiguration().getBounds();
    }
    java.awt.GraphicsDevice bestScreen = getCurrentGraphicsDevice();
    if (bestScreen == null)
      return lastScreenBounds = new java.awt.Rectangle(getToolkit().getScreenSize());
    else
      return lastScreenBounds = bestScreen.getDefaultConfiguration().getBounds();
  }

  public void setFullScreenAWT(boolean state)
  {
    java.awt.GraphicsDevice bestScreen = getCurrentGraphicsDevice();
    if (bestScreen == null)
    {
      java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
      bestScreen = ge.getDefaultScreenDevice();
    }

    // even if isFullScreenSupported returns false, this will still work...
    if(state) {
      // make sure we turn off native window decorations
      if(titleStyle == PLATFORM_TITLE_STYLE) {
        dispose(); // must be done before calling setUndecorated()
        setUndecorated(true);
        setResizable(false);
      }
      bestScreen.setFullScreenWindow(this);
      fsScreen = bestScreen;
      setVisible(true);
    } else {
      // then back on...
      // Under Mac OS X Leopard, we MUST call setFullScreenWindow FIRST or it won't exit fullscreen mode
      bestScreen.setFullScreenWindow(null);
      if(titleStyle == PLATFORM_TITLE_STYLE) {
        dispose();
        setUndecorated(false);
        setResizable(true);
      }
      fsScreen = null;
      setVisible(true);
    }
  }

  public void setFullScreen(boolean x)
  {
    java.awt.Rectangle scrSize = getCurrScreenBounds();
    if (x && fixedClientSize != null &&
        (scrSize.width > fixedClientSize.width || scrSize.height > fixedClientSize.height))
      x = false;
    if (fullScreen != x)
    {
      if (x)
      {
        lastLoc = getLocation();
        lastSize = getSize();
        fullScreen = true;

        if (System.getProperty("os.name").toLowerCase().indexOf("mac os x") != -1)
        {
          try
          {
            // let the native side have a chance to promote full screen mode
            if(UIUtils.setFullScreenMode(null, x) == 0) {
              setFullScreenAWT(x); // fall back on AWT
            } else {
              // still need to set it undecorated or mouse events don't get interpreted properly
              // FIXME: find a better way...
              dispose(); // must be done before calling setUndecorated()
              setUndecorated(true);
              setResizable(false);
              setVisible(true);
            }
          }
          catch(Throwable t)
          {
            System.out.println("Exception while setting fullscreen mode: " + t);
          }
        }
        else if (System.getProperty("os.name").toLowerCase().indexOf("windows") == -1)
        {
          java.awt.Component [] comps = getContentPane().getComponents();
          for(int i=0; i<comps.length; i++)
          {
            if(comps[i] instanceof java.awt.Canvas)
            {
              try
              {
                UIUtils.setFullScreenMode((java.awt.Canvas)comps[i], true);
              }
              catch (Throwable e)
              {
                System.out.println("WARNING: setFullScreenMode not implemented");
              }
              break;
            }
          }
        }
        setBounds(getCurrScreenBounds());
        if (titleBar != null)
          titleBar.invalidate();
        java.awt.EventQueue.invokeLater(new Runnable()
        {
          public void run()
          {
            validate();
            Thread asyncFocus = new Thread()
            {
              public void run()
              {
                try{Thread.sleep(1000);}catch(Exception e){}
                java.awt.EventQueue.invokeLater(new Runnable()
                {
                  public void run()
                  {
                    // This is needed on JRE 1.4 on Windows, but not on JRE 1.5
                    // (but the !isFocused() is required so it doesn't lose focus on JRE 1.5)
                    if (!isFocused())
                    {
                      //System.out.println("Bringing window to front...focused=" + isFocused() + " active=" + isActive());
                      toFront();
                    }
                  }
                });
              }
            };
            asyncFocus.start();
          }
        });
      }
      else
      {
        fullScreen = false;
        if (fixedClientSize != null)
        {
          lastSize.width = fixedClientSize.width;
          lastSize.height = fixedClientSize.height;
        }

        if (System.getProperty("os.name").toLowerCase().indexOf("mac os x") != -1)
        {
          try
          {
            if(UIUtils.setFullScreenMode(null, x) == 0) {
              setFullScreenAWT(x); // fall back on AWT
            } else {
              // still need to set it undecorated or mouse events don't get interpreted properly
              // FIXME: find a better way...
              dispose(); // must be done before calling setUndecorated()
              setUndecorated(false);
              setResizable(true);
              setVisible(true);
            }
          }
          catch(Throwable t)
          {
            System.out.println("Exception while setting fullscreen mode: " + t);
          }
        }
        else if (System.getProperty("os.name").toLowerCase().indexOf("windows") == -1)
        {
          java.awt.Component [] comps = getContentPane().getComponents();
          for(int i=0; i<comps.length; i++)
          {
            if(comps[i] instanceof java.awt.Canvas)
            {
              try
              {
                UIUtils.setFullScreenMode((java.awt.Canvas)comps[i], false);
              }
              catch (Throwable e)
              {
                System.out.println("WARNING: setFullScreenMode not implemented");
              }
              break;
            }
          }
        }
        java.awt.EventQueue.invokeLater(new Runnable()
        {
          public void run()
          {
            setBounds(lastLoc.x, lastLoc.y, lastSize.width, lastSize.height);
            if (titleBar != null)
            {
              titleBar.invalidate();
              Thread asyncDelay = new Thread()
              {
                public void run()
                {
                  // If we don't delay here it'll lockup
                  try{Thread.sleep(1000);}catch(Exception e){}
                  validate();
                  titleBar.repaint();
                }
              };
              asyncDelay.start();
            }
          }
        });
      }
    }
  }

  public void mouseEntered(java.awt.event.MouseEvent evt)
  {
    if (fixedClientSize != null) return;
    prevCursor = getCursor();
    mouseMoved(evt);
  }

  public void mouseExited(java.awt.event.MouseEvent evt)
  {
    if (fixedClientSize != null) return;
    setCursor(prevCursor);
  }

  public void mousePressed(java.awt.event.MouseEvent evt)
  {
    shiftedLast = false;
    pressPoint = evt.getPoint();
    if (fixedClientSize != null) return;
    dragCorner = getCorner(evt);
  }

  public void mouseClicked(java.awt.event.MouseEvent evt)
  {
    if (evt.getSource() instanceof MultiLineLabel &&
        evt.getClickCount() == 2 &&
        (evt.getModifiers() & java.awt.event.MouseEvent.BUTTON1_MASK) != 0)
    {
      if (!fullScreen)
        setFullScreen(true);
    }
  }

  public void mouseReleased(java.awt.event.MouseEvent evt)
  {
    pressPoint = null;
    if (dragCorner != -1)
    {
      mainPanel.invalidate();
      dragCorner = -1;
      java.awt.EventQueue.invokeLater(new Runnable()
      {
        public void run()
        {
          validate();
        }
      });
    }
  }

  public void mouseMoved(java.awt.event.MouseEvent evt)
  {
    if (fixedClientSize != null) return;
    if (evt.getSource() == this)
    {
      int corner = getCorner(evt);
      if (corner == -1)
        setCursor(prevCursor);
      else
        setCursor(getCornerCursor(corner));
    }
  }

  private java.awt.Cursor getCornerCursor(int corner)
  {
    switch (corner)
    {
      case 0:
        return java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.NW_RESIZE_CURSOR);
      case 1:
        return java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.N_RESIZE_CURSOR);
      case 2:
        return java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.NE_RESIZE_CURSOR);
      case 3:
        return java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR);
      case 4:
        return java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.SE_RESIZE_CURSOR);
      case 5:
        return java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.S_RESIZE_CURSOR);
      case 6:
        return java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.SW_RESIZE_CURSOR);
      case 7:
        return java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.W_RESIZE_CURSOR);
      default:
        return prevCursor;
    }
  }

  // 0 is the top left, incrementing going clockwise
  private int getCorner(java.awt.event.MouseEvent evt)
  {
    java.awt.Insets insets = getInsets();
    if (evt.getX() < 16)
    {
      if (evt.getY() < insets.top)
        return 0;
      else if (evt.getY() >= getHeight() - insets.bottom)
        return 6;
      if (evt.getX() < insets.left)
      {
        if (evt.getY() < 16)
          return 0;
        else if (evt.getY() >= getHeight() - 16)
          return 6;
        else
          return 7;
      }
      return -1;
    }
    else if (evt.getX() >= getWidth() - 16)
    {
      if (evt.getY() < insets.top)
        return 2;
      else if (evt.getY() >= getHeight() - insets.bottom)
        return 4;
      if (evt.getX() >= getWidth() - insets.right)
      {
        if (evt.getY() < 16)
          return 2;
        else if (evt.getY() >= getHeight() - 16)
          return 4;
        else
          return 3;
      }
      return -1;
    }
    else if (evt.getY() < insets.top)
      return 1;
    else if (evt.getY() >= getHeight() - insets.bottom)
      return 5;

    return -1;
  }

  public void mouseDragged(java.awt.event.MouseEvent evt)
  {
    if (fullScreen) return;
    if (evt.getSource() == this)
    {
      if (fixedClientSize != null) return;
      if (pressPoint == null)
      {
        pressPoint = evt.getPoint();
        dragCorner = getCorner(evt);
        return;
      }
      if (dragCorner != -1)
      {
        int xShift = evt.getX() - pressPoint.x;
        int yShift = evt.getY() - pressPoint.y;
        int newX = getX();
        int newY = getY();
        int newW = getWidth();
        int newH = getHeight();
        switch (dragCorner)
        {
          case 0:
            newX += xShift;
            newY += yShift;
            newW -= xShift;
            newH -= yShift;
            break;
          case 1:
            newY += yShift;
            newH -= yShift;
            break;
          case 2:
            newY += yShift;
            newW += xShift;
            newH -= yShift;
            break;
          case 3:
            newW += xShift;
            break;
          case 4:
            newW += xShift;
            newH += yShift;
            break;
          case 5:
            newH += yShift;
            break;
          case 6:
            newX += xShift;
            newW -= xShift;
            newH += yShift;
            break;
          case 7:
            newX += xShift;
            newW -= xShift;
            break;
        }
        if (newW < 64)
        {
          newW = 64;
          if (newX != getX())
            newX = getX() + (getWidth() - 64);
        }
        if (newH < 64)
        {
          newH = 64;
          if (newY != getY())
            newY = getY() + (getHeight() - 64);
        }
        if (dragCorner >= 2 && dragCorner <= 4)
          pressPoint.x = Math.max(64 - getInsets().right, evt.getX());
        if (dragCorner >= 4 && dragCorner <= 6)
          pressPoint.y = Math.max(64 - getInsets().bottom, evt.getY());
        setBounds(newX, newY, newW, newH);
        java.awt.EventQueue.invokeLater(new Runnable()
        {
          public void run() { validate(); }
        });
      }
    }
    else
    {
      if (pressPoint == null)
      {
        pressPoint = evt.getPoint();
        shiftedLast = false;
        return;
      }
      if (!shiftedLast)
      {
        int xShift = evt.getX() - pressPoint.x;
        int yShift = evt.getY() - pressPoint.y;
        setLocation(getX() + xShift, getY() + yShift);
      }
      shiftedLast = !shiftedLast;
    }
  }

  public void actionPerformed(java.awt.event.ActionEvent evt)
  {
    if (evt.getSource() == closeButton)
    {
      processWindowEvent(new java.awt.event.WindowEvent(this,
          java.awt.event.WindowEvent.WINDOW_CLOSING));
    }
    else if (evt.getSource() == minButton)
    {
      setExtendedState(ICONIFIED);
    }
    else if (evt.getSource() == maxButton && maxButton != null)
    {
      setFullScreen(true);
    }
  }

  public java.awt.Container getContentPane() { return mainPanel; }

  public void addLayoutComponent(String name, java.awt.Component comp) {
  }

  public void layoutContainer(java.awt.Container parent)
  {
    if (fullScreen)
    {
      mainPanel.setLocation(0, 0);
      java.awt.Rectangle screenBounds = getCurrScreenBounds();
      mainPanel.setSize(screenBounds.width, screenBounds.height);
      if (titleBar != null)
        titleBar.setBounds(0, 0, 0, 0);
      return;
    }

    java.awt.Dimension fullSize = parent.getSize();
    java.awt.Insets insets = parent.getInsets();
    if (titleStyle == PLATFORM_TITLE_STYLE)
    {
      mainPanel.setBounds(insets.left, insets.top, fullSize.width - insets.left - insets.right,
          fullSize.height - insets.top - insets.bottom);
      return;
    }
    switch (decorationState)
    {
      case NO_TITLE_DECORATION:
        titleBar.setBounds(0, 0, 0, 0);
        mainPanel.setBounds(insets.left, insets.top, fullSize.width - insets.left - insets.right,
            fullSize.height - insets.top - insets.bottom);
        break;
      case NO_DECORATIONS:
        titleBar.setBounds(0, 0, 0, 0);
        mainPanel.setBounds(0, 0, fullSize.width, fullSize.height);
        break;
      default:
        titleBar.setBounds(insets.left, insets.top, fullSize.width - insets.left - insets.right,
            titleBar.getPreferredSize().height);
        mainPanel.setBounds(insets.left, titleBar.getY() + titleBar.getHeight(), titleBar.getWidth(),
            fullSize.height - titleBar.getHeight() - insets.top - insets.bottom);
        break;
    }
  }

  public java.awt.Dimension minimumLayoutSize(java.awt.Container parent) {
    return preferredLayoutSize(parent);
  }

  public java.awt.Dimension preferredLayoutSize(java.awt.Container parent)
  {
    if (fullScreen)
    {
      System.out.println("preferredLayoutSize");
      java.awt.Rectangle screenBounds = getCurrScreenBounds();
      return new java.awt.Dimension(screenBounds.width, screenBounds.height);
    }

    java.awt.Dimension prefSize = (fixedClientSize == null) ? mainPanel.getPreferredSize() :
      (java.awt.Dimension)fixedClientSize.clone();
    if (decorationState != NO_DECORATIONS && titleStyle != PLATFORM_TITLE_STYLE)
    {
      java.awt.Insets insets = getInsets();
      prefSize.width += insets.left + insets.right;
      prefSize.height += insets.top + insets.bottom;
      if (decorationState != NO_TITLE_DECORATION)
        prefSize.height += titleBar.getPreferredSize().height;
    }
    return prefSize;
  }

  public void removeLayoutComponent(java.awt.Component comp) {
  }

  public void setTitle(String x)
  {
    if (titleStyle == PLATFORM_TITLE_STYLE)
      super.setTitle(x);
    else
      titleLabel.setText(x);
  }

  public void setClosable(boolean x)
  {
    if (closeButton != null)
      closeButton.setEnabled(x);
  }

  public void setFixedClientSize(java.awt.Dimension x)
  {
    if (x == fixedClientSize || (x != null && x.equals(fixedClientSize))) return;
    fixedClientSize = x;
    if (x != null)
    {
      //System.out.println("setFixedClientSize");
      java.awt.Rectangle scrSize = getCurrScreenBounds();
      if (scrSize.width > x.width || scrSize.height > x.height)
        setFullScreen(false);
      pack();
    }
  }

  public void setDecorationState(int x) { decorationState = x; }

  protected java.awt.image.BufferedImage loadMyImage(String imageName)
  {
    java.net.URL imageURL = getClass().getClassLoader().getResource(imageName);
    if (imageURL == null)
    {
      return null;
    }
    try
    {
      return javax.imageio.ImageIO.read(imageURL);
    }
    catch (Exception e)
    {
      System.out.println("ERROR loading image: " + imageName + " of " + e);
      return null;
    }
  }

  protected boolean shiftedLast;
  protected boolean fullScreen;
  protected java.awt.GraphicsDevice fsScreen; // selected screen for FS mode
  protected java.awt.Dimension lastSize;
  protected java.awt.Point lastLoc;

  protected java.awt.Point pressPoint;
  protected int dragCorner;

  protected java.awt.Panel mainPanel;

  protected ActiveImage closeButton;
  protected ActiveImage maxButton;
  protected ActiveImage minButton;
  protected java.awt.Panel titleBar;
  protected MultiLineLabel titleLabel;

  protected java.awt.Cursor prevCursor;

  protected boolean alwaysOnTop;

  protected java.awt.Dimension fixedClientSize;

  protected int decorationState;

  protected String myTitle;

  protected int titleStyle;

  private static java.awt.Image bgImage;
  private static java.awt.Image bgImageInactive;
  private static java.awt.Image closeButtonRedBGActive;
  private static java.awt.Image closeButtonRedBGDisabled;
  private static java.awt.Image minWindowImageBG;
  private static java.awt.Image minWindowImageBGDisabled;
  private static java.awt.Image maxWindowImageBG;
  private static java.awt.Image maxWindowImageBGDisabled;

  private java.awt.Rectangle lastScreenBounds;
}
