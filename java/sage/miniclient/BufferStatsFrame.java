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
public class BufferStatsFrame extends javax.swing.JFrame
{
  private static final int BUFFER_SIZE = 1024;
  /** Creates a new instance of BufferStatsFrame */
  public BufferStatsFrame()
  {
    super("SageTV Placeshifter Stats");
    chanBWs = new int[BUFFER_SIZE];
    streamBWs = new int[BUFFER_SIZE];
    targetBWs = new int[BUFFER_SIZE];
    buffTimes = new long[BUFFER_SIZE];
    statTimes = new long[BUFFER_SIZE];
    idx = 0;
    numEntries = 0;
    getContentPane().setLayout(new java.awt.GridBagLayout());
    java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
    gbc.gridwidth = 1;
    gbc.gridheight = 1;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.8;
    gbc.weighty = 0;
    gbc.fill = gbc.NONE;
    gbc.anchor = gbc.WEST;
    gbc.insets = new java.awt.Insets(4, 4, 4, 4);
    javax.swing.JLabel lab = new javax.swing.JLabel("Channel Bandwidth:");
    lab.setForeground(java.awt.Color.red.darker());
    getContentPane().add(lab, gbc);
    chanBWText = new javax.swing.JLabel("0 Kbps");
    gbc.gridx++;
    gbc.weightx = 0.2;
    getContentPane().add(chanBWText, gbc);
    gbc.gridx = 0;
    gbc.weightx = 0.8;
    gbc.gridy++;
    lab = new javax.swing.JLabel("Stream Bandwidth:");
    lab.setForeground(java.awt.Color.green.darker());
    getContentPane().add(lab, gbc);
    streamBWText = new javax.swing.JLabel("0 Kbps");
    gbc.gridx++;
    gbc.weightx = 0.2;
    getContentPane().add(streamBWText, gbc);
    gbc.gridx = 0;
    gbc.weightx = 0.8;
    gbc.gridy++;
    lab = new javax.swing.JLabel("Target Bandwidth:");
    lab.setForeground(java.awt.Color.blue.darker());
    getContentPane().add(lab, gbc);
    targetBWText = new javax.swing.JLabel("0 Kbps");
    gbc.gridx++;
    gbc.weightx = 0.2;
    getContentPane().add(targetBWText, gbc);
    gbc.gridx = 0;
    gbc.weightx = 0.8;
    gbc.gridy++;
    lab = new javax.swing.JLabel("Buffer Time:");
    lab.setForeground(java.awt.Color.orange.darker());
    getContentPane().add(lab, gbc);
    bufferSizeText = new javax.swing.JLabel("0 msec");
    gbc.gridx++;
    gbc.weightx = 0.2;
    getContentPane().add(bufferSizeText, gbc);

    gbc.gridx = 0;
    gbc.gridy++;
    javax.swing.JButton resetButt = new javax.swing.JButton("Reset");
    resetButt.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        idx = 0;
        numEntries = 0;
        repaint();
      }
    });
    getContentPane().add(resetButt, gbc);

    canny = new BufferStatsCanvas();
    gbc.gridy++;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = gbc.BOTH;
    getContentPane().add(canny, gbc);
    canny.setBackground(java.awt.Color.black);
    canny.setFont(myFont = new java.awt.Font("Arial", java.awt.Font.PLAIN, 16));

    setDefaultCloseOperation(HIDE_ON_CLOSE);
  }

  public long getLastBufferTime()
  {
    return (numEntries == 0) ? 0 : buffTimes[(idx == 0) ? (BUFFER_SIZE-1) : (idx-1)];
  }
  public int getLastStreamBW()
  {
    return (numEntries == 0) ? 0 : streamBWs[(idx == 0) ? (BUFFER_SIZE-1) : (idx-1)];
  }

  public void addNewStats(int chanBW, int streamBW, int targetBW, long buffTime)
  {
    chanBWs[idx] = chanBW;
    streamBWs[idx] = streamBW;
    targetBWs[idx] = targetBW;
    buffTimes[idx] = buffTime;
    statTimes[idx] = System.currentTimeMillis();
    idx++;
    idx = (idx % BUFFER_SIZE);
    numEntries++;
    numEntries = Math.min(numEntries, BUFFER_SIZE);
    chanBWText.setText(Integer.toString(chanBW) + " Kbps");
    streamBWText.setText(Integer.toString(streamBW) + " Kbps");
    targetBWText.setText(Integer.toString(targetBW) + " Kbps");
    bufferSizeText.setText(buffTime + " msec");
    canny.repaint();
  }

  public class BufferStatsCanvas extends javax.swing.JPanel
  {
    public void update(java.awt.Graphics g)
    {
      //			g.clearRect(0, 0, getWidth(), getHeight());
      paint(g);
    }

    public void paint(java.awt.Graphics g)
    {
      // The left 50 pxiels is for doing the vertical axis labels so the max range can be seen.
      int w = getWidth();
      int h = getHeight();
      g.setColor(java.awt.Color.black);
      g.fillRect(0, 0, w, h);
      g.setColor(java.awt.Color.gray);
      int numDivs = 10;
      for (int i = 0; i < numDivs; i++)
        g.drawLine(50, h*i/numDivs, w, h*i/numDivs);
      g.setColor(java.awt.Color.white);
      g.drawLine(0, h/2, w, h/2);
      g.drawLine(50, 0, 50, h);
      // Find the max value for the bandwidth stats. We do this in units of 100Kbps.
      int maxBW = 0;
      long maxBuffTime = 0;
      for (int i = 0; i < numEntries; i++)
      {
        if (chanBWs[i] > maxBW) maxBW = chanBWs[i];
        if (streamBWs[i] > maxBW) maxBW = streamBWs[i];
        if (targetBWs[i] > maxBW) maxBW = targetBWs[i];
        if (buffTimes[i] > maxBuffTime) maxBuffTime = buffTimes[i];
      }
      maxBW = (1 + (maxBW / 100)) * 100;
      maxBuffTime = (1 + (maxBuffTime / 5000)) * 5000;

      int startIdx = (numEntries < BUFFER_SIZE) ? 0 : idx;

      int baseX = 50;
      if (numEntries > w - baseX)
      {
        if (numEntries < BUFFER_SIZE)
          startIdx = numEntries - (w - baseX);
        else
        {
          startIdx = idx - 1 - (w - baseX);
          if (startIdx < 0)
            startIdx += BUFFER_SIZE;
        }
      }

      int lastChanBW = chanBWs[startIdx];
      int lastStreamBW = streamBWs[startIdx];
      int lastTargetBW = targetBWs[startIdx];
      long lastBuffTime = buffTimes[startIdx];
      float bwScale = ((float)h/2)/maxBW;
      float timeScale = ((float)h/2)/maxBuffTime;
      java.awt.Stroke oldStroke = ((java.awt.Graphics2D) g).getStroke();
      ((java.awt.Graphics2D)g).setStroke(new java.awt.BasicStroke(2));
      float scaleX = ((float)w-baseX)/numEntries;
      scaleX = Math.max(1, Math.min(scaleX, 5));
      java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
      for (int i = 1; i < numEntries && baseX + scaleX*(i - 1) < w; i++, startIdx++)
      {
        startIdx = startIdx % BUFFER_SIZE;
        g.setColor(java.awt.Color.red.darker());
        g2.draw(new java.awt.geom.Line2D.Float(baseX + scaleX*(i - 1), h/2 -lastChanBW*bwScale, baseX + scaleX*i, h/2 - chanBWs[startIdx]*bwScale));
        lastChanBW = chanBWs[startIdx];
        g.setColor(java.awt.Color.green.darker());
        g2.draw(new java.awt.geom.Line2D.Float(baseX + scaleX*(i - 1), h/2 - lastStreamBW*bwScale, baseX + scaleX*i, h/2 - streamBWs[startIdx]*bwScale));
        lastStreamBW = streamBWs[startIdx];
        g.setColor(java.awt.Color.blue.darker());
        g2.draw(new java.awt.geom.Line2D.Float(baseX + scaleX*(i - 1), h/2 - lastTargetBW*bwScale, baseX + scaleX*i, h/2 - targetBWs[startIdx]*bwScale));
        lastTargetBW = targetBWs[startIdx];
        g.setColor(java.awt.Color.orange.darker());
        g2.draw(new java.awt.geom.Line2D.Float(baseX + scaleX*(i - 1), h - timeScale*lastBuffTime, baseX + scaleX*i, h - timeScale*buffTimes[startIdx]));
        lastBuffTime = buffTimes[startIdx];
      }
      g2.setStroke(oldStroke);

      // Draw the text for the axis labels
      g.setColor(java.awt.Color.white);
      g.setFont(myFont);
      java.awt.FontMetrics fm = getFontMetrics(myFont);
      g.drawString(maxBW + "", 1, fm.getAscent() + 2);
      g.drawString("Kbps", 1, fm.getHeight() + fm.getAscent() + 2);
      g.drawString(maxBuffTime/1000 + "", 1, h/2 + fm.getAscent() + 2);
      g.drawString("sec", 1, h/2 + fm.getHeight() + fm.getAscent() + 2);
    }

    public java.awt.Dimension getPreferredSize()
    {
      return new java.awt.Dimension(300, 200);
    }
  }

  private int[] chanBWs;
  private int[] streamBWs;
  private int[] targetBWs;
  private long[] buffTimes;
  private long[] statTimes;
  private int idx;
  private int numEntries;

  private javax.swing.JLabel chanBWText;
  private javax.swing.JLabel streamBWText;
  private javax.swing.JLabel targetBWText;
  private javax.swing.JLabel bufferSizeText;

  private BufferStatsCanvas canny;

  private java.awt.Font myFont;
}
