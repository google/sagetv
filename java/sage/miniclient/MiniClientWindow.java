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

/**
 *
 * @author  Narflex
 */
public class MiniClientWindow extends sage.SageTVWindow
{
  public MiniClientWindow(String title)
  {
    this(title, 0);
  }
  public MiniClientWindow(String title, int prefTitleStyle)
  {
    super(title, prefTitleStyle);
    if (titleStyle != PLATFORM_TITLE_STYLE)
    {
      java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();

      summStats = new StatSummCanvas();
      gbc.gridx = (titleStyle == MAC_TITLE_STYLE) ? 5 : 2;
      gbc.gridy = 0;
      gbc.gridwidth = 1;
      gbc.gridheight = 1;
      gbc.ipadx = 0;
      gbc.ipady = 0;
      gbc.weightx = 0;
      gbc.weighty = 1;
      gbc.fill = java.awt.GridBagConstraints.BOTH;
      gbc.insets = new java.awt.Insets((titleStyle == WIN2K_TITLE_STYLE) ? 0 : 6, 5, (titleStyle == WIN2K_TITLE_STYLE) ? 0 : 4, 25);
      titleBar.add(summStats, gbc);
      summStats.addMouseListener(this);
      summStats.addMouseMotionListener(this);
      summStats.setFocusable(false);
      summStats.setVisible(false);

      summRateText = new sage.MultiLineLabel("0 Kbps", getFont());
      summRateText.setForeground((titleStyle == MAC_TITLE_STYLE) ? java.awt.Color.black : java.awt.Color.white);
      gbc.gridx = (titleStyle == MAC_TITLE_STYLE) ? 4 : 1;
      gbc.gridy = 0;
      gbc.gridwidth = 1;
      gbc.gridheight = 1;
      gbc.ipadx = 0;
      gbc.ipady = 0;
      gbc.weightx = 0;
      gbc.weighty = 1;
      gbc.fill = java.awt.GridBagConstraints.NONE;
      gbc.insets = new java.awt.Insets(0, 10, 0, 5);
      titleBar.add(summRateText, gbc);
      summRateText.addMouseListener(this);
      summRateText.addMouseMotionListener(this);
      summRateText.setFocusable(false);
      summRateText.setVisible(false);

      showStatsSummary = MiniClient.myProperties.getProperty("show_stat_summary", MiniClient.MAC_OS_X ? "false" : "true").equalsIgnoreCase("true");
      if (showStatsSummary)
      {
        summRateText.setVisible(true);
        summStats.setVisible(true);
      }
      alwaysOnTop = MiniClient.myProperties.getProperty("always_on_top", "false").equalsIgnoreCase("true");
      if (alwaysOnTop && (System.getProperty("java.version").startsWith("1.5") || System.getProperty("java.version").startsWith("1.6")))
        setAlwaysOnTop(true);
    }
    if ((System.getProperty("os.name").toLowerCase().indexOf("windows") == -1) &&
        (System.getProperty("os.name").toLowerCase().indexOf("mac os x") == -1))
    {
      try
      {
        sage.Native.loadLibrary("Sage");
      }
      catch (Throwable t)
      {
        System.err.println("ERROR loading native lib for UI:" + t);
      }
    }
  }

  protected void invalidateExtraComponents()
  {
    if (titleStyle != PLATFORM_TITLE_STYLE)
    {
      summStats.invalidate();
      summStats.repaint();
      summRateText.repaint();
    }
  }

  public void mouseClicked(java.awt.event.MouseEvent evt)
  {
    if ((evt.getSource() instanceof sage.MultiLineLabel || evt.getSource() == summStats) &&
        evt.getClickCount() == 1 &&
        (evt.getModifiers() & java.awt.event.InputEvent.BUTTON3_MASK) == java.awt.event.InputEvent.BUTTON3_MASK)
    {
      if (ontopPopup == null)
      {
        ontopPopup = new java.awt.PopupMenu("Window Options");
        if (System.getProperty("java.version").startsWith("1.5") || System.getProperty("java.version").startsWith("1.6"))
        {
          ontopMenuItem = new java.awt.CheckboxMenuItem("Always On Top", alwaysOnTop);
          ontopMenuItem.addItemListener(new java.awt.event.ItemListener()
          {
            public void itemStateChanged(java.awt.event.ItemEvent evt)
            {
              MiniClientWindow.this.setAlwaysOnTop(evt.getStateChange() == evt.SELECTED);
              MiniClient.myProperties.setProperty("always_on_top", evt.getStateChange() == evt.SELECTED ? "true" : "false");
            }
          });;
          ontopPopup.add(ontopMenuItem);
          ontopPopup.addSeparator();
        }
        statSummMenuItem = new java.awt.CheckboxMenuItem("Show Statistics Summary", showStatsSummary);
        statSummMenuItem.addItemListener(new java.awt.event.ItemListener()
        {
          public void itemStateChanged(java.awt.event.ItemEvent evt)
          {
            showStatsSummary = !showStatsSummary;
            summRateText.setVisible(showStatsSummary);
            summStats.setVisible(showStatsSummary);
            titleBar.validate();
            summStats.repaint();
            MiniClient.myProperties.setProperty("show_stat_summary", Boolean.toString(showStatsSummary));
          }
        });;
        ontopPopup.add(statSummMenuItem);
        ontopPopup.addSeparator();
        showStatsWindowItem = new java.awt.MenuItem("Show Statistics Window");
        showStatsWindowItem.addActionListener(new java.awt.event.ActionListener()
        {
          public void actionPerformed(java.awt.event.ActionEvent evt)
          {
            if (MediaCmd.bufferStatsFrame != null)
            {
              MediaCmd.bufferStatsFrame.setVisible(true);
              MediaCmd.bufferStatsFrame.toFront();
            }
          }
        });
        ontopPopup.add(showStatsWindowItem);
        add(ontopPopup);
      }
      showStatsWindowItem.setEnabled(MediaCmd.bufferStatsFrame != null);
      ontopPopup.show(evt.getComponent(), evt.getX(), evt.getY());
    }
    else if ((evt.getSource() instanceof sage.MultiLineLabel || evt.getSource() == summStats) &&
        evt.getClickCount() == 2 &&
        (evt.getModifiers() & java.awt.event.InputEvent.BUTTON1_MASK) != 0)
    {
      if (!fullScreen)
        setFullScreen(true);
    }
  }

  private long lastUpdateTime;
  public void updateStats()
  {
    // Throttle this down
    if (System.currentTimeMillis() - lastUpdateTime < 250) return;
    lastUpdateTime = System.currentTimeMillis();

    if (showStatsSummary && MediaCmd.bufferStatsFrame != null && !isFullScreen() && titleStyle != PLATFORM_TITLE_STYLE)
    {
      String newStr = Integer.toString(MediaCmd.bufferStatsFrame.getLastStreamBW()) + " Kbps";
      summRateText.setText(newStr);
      // If we don't rethread this I've seen it deadlock the JVM on Linux
      java.awt.EventQueue.invokeLater(new Runnable() {
        public void run() {
          titleBar.validate();
          summStats.repaint();
        }
      });
    }
  }

  private java.awt.PopupMenu ontopPopup;
  private java.awt.CheckboxMenuItem statSummMenuItem;
  private java.awt.MenuItem showStatsWindowItem;
  private java.awt.CheckboxMenuItem ontopMenuItem;
  private boolean showStatsSummary;
  private StatSummCanvas summStats;
  private sage.MultiLineLabel summRateText;

  private class StatSummCanvas extends java.awt.Canvas
  {
    public void update(java.awt.Graphics g)
    {
      paint(g);
    }
    public void paint(java.awt.Graphics g)
    {
      if (showStatsSummary && !isFullScreen())
      {
        if (doubleBuff == null || doubleBuff.getWidth() < getWidth() || doubleBuff.getHeight() < getHeight())
        {
          doubleBuff = new java.awt.image.BufferedImage(getWidth(), getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        }
        java.awt.Graphics2D g2 = doubleBuff.createGraphics();
        g2.setColor(java.awt.Color.black);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setPaint(new java.awt.GradientPaint(0, getHeight()/2.0f, java.awt.Color.red.darker().darker().darker(), getWidth(), getHeight()/2.0f,
            java.awt.Color.orange.darker().darker().darker()));
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        int numDivs = 15;
        float spacing = 2.0f;
        float barSize = ((float)getWidth() - (numDivs + 1)*spacing)/numDivs;
        float x = spacing;
        while (x + barSize < getWidth())
        {
          g2.fill(new java.awt.geom.RoundRectangle2D.Float(x, spacing, barSize, getHeight()-2*spacing, spacing, spacing));
          x += barSize + spacing;
        }

        // Now we draw with the actual data
        long maxBufferTime = 15000;
        if (MediaCmd.bufferStatsFrame != null)
        {
          float xlimit = ((float)MediaCmd.bufferStatsFrame.getLastBufferTime())/maxBufferTime;
          xlimit = Math.min(1.0f, xlimit);
          xlimit = xlimit * (getWidth() - 2*spacing) + spacing;
          g2.setPaint(new java.awt.GradientPaint(0, getHeight()/2.0f, java.awt.Color.red.darker(), getWidth(), getHeight()/2.0f,
              java.awt.Color.orange.darker()));
          x = spacing;
          while (x + barSize < getWidth() && x < xlimit)
          {
            g2.fill(new java.awt.geom.RoundRectangle2D.Float(x, spacing, Math.min(barSize, xlimit - x), getHeight()-2*spacing, spacing, spacing));
            x += barSize + spacing;
          }
        }

        g2.dispose();
        g.drawImage(doubleBuff, 0, 0, null);
      }
    }
    public java.awt.Dimension getPreferredSize()
    {
      return new java.awt.Dimension(100, 5);
    }
    private java.awt.image.BufferedImage doubleBuff;
  }
}
