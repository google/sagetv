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
 * @author  Narflex
 */
public class SpecialWindow extends SageTVWindow
{

  /** Creates a new instance of SageTVWindow */
  public SpecialWindow(String title, int prefTitleStyle)
  {
    super(title, prefTitleStyle);
  }
  public SpecialWindow(String title)
  {
    super(title);
  }

  public void setFullScreen(boolean x)
  {
    if (Sage.MAC_OS_X)
      super.setFullScreen(x);
    else
      setFullScreen(x, false);
  }
  public void setFullScreen(boolean x, boolean noToFront)
  {
    if (Sage.DBG) System.out.println("SetFullScreen(" + x + ")");
    if (Sage.MAC_OS_X) {
      super.setFullScreen(x);
      return;
    }

    java.awt.Rectangle scrSize = getCurrScreenBounds();
    if (Sage.WINDOWS_OS && x && fixedClientSize != null &&
        (scrSize.width > fixedClientSize.width || scrSize.height > fixedClientSize.height))
      x = false;
    if (fullScreen != x)
    {
      if (x)
      {
        lastLoc = getLocation();
        lastSize = getSize();
        fullScreen = true;
        if (titleStyle == PLATFORM_TITLE_STYLE)
        {
          boolean wasVisible = isVisible();
          dispose(); // must be done before calling setUndecorated()
          setUndecorated(true);
          setResizable(false);
          uiMgr.getRootPanel().resetHWND();
          setVisible(wasVisible);
        }
        if (Sage.DBG) System.out.println("FullScreen set to true");
        setBounds(getCurrScreenBounds());
        if(titleBar != null)
          titleBar.invalidate();
        if (!noToFront)
        {
          java.awt.EventQueue.invokeLater(new Runnable()
          {
            public void run()
            {
              validate();
              Pooler.execute(new Runnable()
              {
                public void run()
                {
                  //								try{Thread.sleep(1000);}catch(Exception e){}
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
              });
            }
          });
        }
      }
      else
      {
        fullScreen = false;
        if (Sage.DBG) System.out.println("FullScreen set to false");
        if (fixedClientSize != null)
        {
          lastSize.width = fixedClientSize.width;
          lastSize.height = fixedClientSize.height;
        }
        if (titleStyle == PLATFORM_TITLE_STYLE)
        {
          boolean wasVisible = isVisible();
          dispose(); // must be done before calling setUndecorated()
          setUndecorated(false);
          setResizable(true);
          uiMgr.getRootPanel().resetHWND();
          setVisible(wasVisible);
        }
        /*				if (Sage.WINDOWS_OS)
				{
					// This is for letting DX9 get out of full screen exclusive mode
					uiMgr.trueValidate();
					try{Thread.sleep(500);}catch(Exception e){}
				}*/
        java.awt.EventQueue.invokeLater(new Runnable()
        {
          public void run()
          {
            setBounds(lastLoc.x, lastLoc.y, lastSize.width, lastSize.height);
            if(titleBar != null) {
              titleBar.invalidate();
              Pooler.execute(new Runnable()
              {
                public void run()
                {
                  // If we don't delay here it'll lockup
                  //									try{Thread.sleep(1000);}catch(Exception e){}
                  validate();
                  titleBar.repaint();
                }
              });
            }
          }
        });
      }
    }
  }

  public void mouseClicked(java.awt.event.MouseEvent evt)
  {
    if (evt.getSource() instanceof MultiLineLabel &&
        evt.getClickCount() == 1 &&
        (evt.getModifiers() & java.awt.event.MouseEvent.BUTTON3_MASK) == java.awt.event.MouseEvent.BUTTON3_MASK)
    {
      if (ontopPopup == null)
      {
        ontopPopup = new java.awt.PopupMenu("Window Options");
        ontopMenuItem = new java.awt.CheckboxMenuItem(Sage.rez("Video_Always_On_Top"), alwaysOnTop);
        ontopMenuItem.addItemListener(new java.awt.event.ItemListener()
        {
          public void itemStateChanged(java.awt.event.ItemEvent evt)
          {
            UIManager.setAlwaysOnTop(uiMgr.getVideoFrame().getVideoHandle(),
                (evt.getStateChange() == evt.SELECTED));
          }
        });
        ontopPopup.add(ontopMenuItem);
        ontopPopup.addSeparator();
        String menuSizes = Sage.get("quick_menu_sizes", "640x480,320x240");
        java.util.StringTokenizer toker = new java.util.StringTokenizer(menuSizes, ",;");
        while (toker.hasMoreTokens())
        {
          try
          {
            String currSize = toker.nextToken();
            int xidx = currSize.indexOf('x');
            final int quickWidth = Integer.parseInt(currSize.substring(0, xidx));
            int tempHeight = Integer.parseInt(currSize.substring(xidx + 1));
            final int quickHeight = (tempHeight == 480 && !MMC.getInstance().isNTSCVideoFormat()) ? 576 :
              ((tempHeight == 240 && !MMC.getInstance().isNTSCVideoFormat()) ? 288 : tempHeight);
            java.awt.MenuItem quickItem = new java.awt.MenuItem(currSize);
            quickItem.addActionListener(new java.awt.event.ActionListener()
            {
              public void actionPerformed(java.awt.event.ActionEvent evt)
              {
                int w = quickWidth; int h = quickHeight;
                if (decorationState != NO_DECORATIONS)
                {
                  java.awt.Insets insets = getInsets();
                  w += insets.left + insets.right;
                  h += insets.top + insets.bottom;
                  if (decorationState != NO_TITLE_DECORATION)
                    h += titleBar.getPreferredSize().height;
                }
                setBounds(SpecialWindow.this.getX(), SpecialWindow.this.getY(), w, h);
                java.awt.EventQueue.invokeLater(new Runnable()
                {
                  public void run() { validate(); }
                });
              }
            });
            ontopPopup.add(quickItem);
          }
          catch (Exception e)
          {
            System.out.println("ERROR with quick_menu_sizes property");
          }
        }
        add(ontopPopup);
      }
      ontopMenuItem.setLabel(Sage.rez("Video_Always_On_Top"));
      ontopPopup.show(evt.getComponent(), evt.getX(), evt.getY());
    }
    else
      super.mouseClicked(evt);
  }

  public void layoutContainer(java.awt.Container parent)
  {
    super.layoutContainer(parent);
    if (Sage.DBG && (titleLabel == null || titleLabel.getText().startsWith("SageTV")))
      setTitle("SageTV " + (Sage.isTrueClient() ? "Client " : "") + "- [" + mainPanel.getWidth() + "x" + mainPanel.getHeight() + "]");
  }

  public void setFixedClientSize(java.awt.Dimension x)
  {
    if (x == fixedClientSize || (x != null && x.equals(fixedClientSize))) return;
    fixedClientSize = x;
    if (x != null)
    {
      if (Sage.DBG) System.out.println("Fixing window size at:" + x);
      java.awt.Rectangle scrSize = getCurrScreenBounds();
      if (Sage.WINDOWS_OS && (scrSize.width > x.width || scrSize.height > x.height))
        setFullScreen(false);
      pack();
    }
  }

  public void setUIMgr(UIManager inUIMgr)
  {
    uiMgr = inUIMgr;
  }

  private UIManager uiMgr;
  private java.awt.PopupMenu ontopPopup;
  private java.awt.CheckboxMenuItem ontopMenuItem;
}
