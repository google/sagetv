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

public class MultiLineLabel extends java.awt.Canvas
{
  public MultiLineLabel(String inText, java.awt.Font inFont)
  {
    this(inText, inFont, false, CENTER_ALIGNMENT);
  }
  public MultiLineLabel(String inText, java.awt.Font inFont, boolean inWrapText,
      float inTextAlignment)
  {
    this(inText, inFont, inWrapText, inTextAlignment, inTextAlignment);
  }
  public MultiLineLabel(String inText, java.awt.Font inFont, boolean inWrapText,
      float inTextAlignmentH, float inTextAlignmentV)
  {
    super();
    theString = inText;
    wrapText = inWrapText;
    if (wrapText)
    {
      lineBreaker = java.text.BreakIterator.getLineInstance();
    }

    textAlignmentH = inTextAlignmentH;
    textAlignmentV = inTextAlignmentV;
    size = new java.awt.Dimension();
    myFont = inFont;
    myMetrics = (myFont != null || ((myFont = getFont()) != null)) ? getFontMetrics(myFont) : null;
    if (theString != null)
    {
      breakString();
      calculateSize();
    }
  }

  public void setFont(java.awt.Font f)
  {
    super.setFont(f);
    if (!f.equals(myFont))
    {
      myFont = f;
      myMetrics = (myFont != null || ((myFont = getFont()) != null)) ? getFontMetrics(myFont) : null;
      layoutCache = null;
      calculateSize();
      invalidate();
    }
  }

  private void breakString()
  {
    java.util.StringTokenizer toker = new java.util.StringTokenizer(theString, "\r\n");
    naturalTextLines = new String[toker.countTokens()];
    for (int i = 0; toker.hasMoreTokens(); i++)
    {
      naturalTextLines[i] = toker.nextToken();
    }
  }

  public void setText(String inText)
  {
    if (!inText.equals(theString))
    {
      theString = inText;
      layoutCache = null;
      breakString();
      calculateSize();
      invalidate();
      repaint(); // added by me after reuse of code
    }
  }

  private void calculateSize()
  {
    if (myMetrics == null)
    {
      myMetrics = (myFont != null || ((myFont = getFont()) != null)) ? getFontMetrics(myFont) : null;
      if (myMetrics == null) return;
    }
    size.width = 0;
    size.height = myMetrics.getHeight() * naturalTextLines.length;
    for (int i = 0; i < naturalTextLines.length; i++)
    {
      size.width = Math.max(size.width, myMetrics.stringWidth(naturalTextLines[i]));
    }
    if (myIcon != null)
    {
      int imageHeight = myIcon.getHeight(null);
      int imageWidth = myIcon.getWidth(null);
      if (imageHAlignment != CENTER_ALIGNMENT)
      {
        size.width += imageWidth + imageGap;
      }
      else
      {
        size.height += imageHeight + imageGap;
      }
    }
  }

  public String getText()
  {
    return theString;
  }

  public void paint(java.awt.Graphics g)
  {
    if (myMetrics == null)
    {
      myMetrics = (myFont != null || ((myFont = getFont()) != null)) ? getFontMetrics(myFont) : null;
      if (myMetrics == null) return;
    }

    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
    int textWidth = getWidth();
    if ((myIcon != null) && (imageHAlignment != CENTER_ALIGNMENT))
    {
      textWidth -= imageGap + myIcon.getWidth(null);
    }
    if ((layoutCache == null) || ((cachedWidth != textWidth) && wrapText))
    {
      cachedWidth = textWidth;
      if (wrapText)
      {
        java.util.ArrayList newText = new java.util.ArrayList();
        for (int i = 0; i < naturalTextLines.length; i++)
        {
          int[] breakPos = analyzeText(naturalTextLines[i], lineBreaker, myMetrics, cachedWidth);
          if (breakPos.length == 0)
          {
            newText.add(naturalTextLines[i]);
          }
          else
          {
            for (int j = 0; j <= breakPos.length; j++)
            {
              if (j == 0)
              {
                newText.add(naturalTextLines[i].substring(0, breakPos[j]));
              }
              else if (j == breakPos.length)
              {
                newText.add(naturalTextLines[i].substring(breakPos[j - 1]));
              }
              else
              {
                newText.add(naturalTextLines[i].substring(breakPos[j - 1], breakPos[j]));
              }
            }
          }
        }
        textLines = (String[]) newText.toArray(new String[0]);
      }
      else
      {
        textLines = naturalTextLines;
      }
      layoutCache = new java.awt.font.TextLayout[textLines.length];
      java.awt.font.FontRenderContext frc = g2.getFontRenderContext();
      for (int i = 0; i < textLines.length; i++)
      {
        layoutCache[i] = new java.awt.font.TextLayout(textLines[i], myFont, frc);
      }
    }

    java.awt.Color oldColor = g.getColor();
    g.setColor(getForeground());

    // NOTE: this used to be both divided by 2, but I changed it to the alignment multiply
    int currX = (int) ((getWidth() - size.width) * textAlignmentH);
    int currY = (int) ((getHeight() - size.height) * textAlignmentV);
    if (myIcon != null)
    {
      if (imageHAlignment == CENTER_ALIGNMENT)
      {
        if (imageVAlignment == TOP_ALIGNMENT)
        {
          // Top-center of the label
          g.drawImage(myIcon, currX + Math.round((size.width -
              myIcon.getWidth(null)) * imageHAlignment), currY, null);
          currY += imageGap + myIcon.getHeight(null);
        }
        else
        {
          // bottom-center of the label
          g.drawImage(myIcon, currX + Math.round((size.width -
              myIcon.getWidth(null)) * imageHAlignment),
              currY + size.height - imageGap - myIcon.getHeight(null), null);
        }
      }
      else if (imageHAlignment == LEFT_ALIGNMENT)
      {
        // image on the left side
        g.drawImage(myIcon, currX, currY +
            Math.round((size.height - imageGap - myIcon.getHeight(null)) * imageVAlignment), null);
        currX += imageGap + myIcon.getWidth(null);
      }
      else
      {
        // image on the right side
        g.drawImage(myIcon, currX + size.width - imageGap - myIcon.getWidth(null), currY +
            Math.round((size.height - imageGap - myIcon.getHeight(null)) * imageVAlignment), null);
      }
    }
    g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(java.awt.RenderingHints.KEY_ALPHA_INTERPOLATION, java.awt.RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    for (int i = 0; i < layoutCache.length; i++)
    {
      layoutCache[i].draw(g2, currX + Math.max((size.width - layoutCache[i].getAdvance())*textAlignmentH, 0),
          currY + layoutCache[i].getAscent());
      currY += myMetrics.getHeight();
    }

    g.setColor(oldColor);
  }

  public java.awt.Dimension getMinimumSize()
  {
    return size;
  }

  public java.awt.Dimension getPreferredSize()
  {
    return size;
  }

  public void setIcon(java.awt.Image inIcon)
  {
    if (myIcon != inIcon)
    {
      myIcon = inIcon;
      invalidate();
    }
  }

  public java.awt.Image getIcon()
  {
    return myIcon;
  }

  public void setHImageAlignment(float x)
  {
    if (imageHAlignment != x)
    {
      imageHAlignment = x;
      invalidate();
    }
  }

  public float getHImageAlignment()
  {
    return imageHAlignment;
  }

  public void setVImageAlignment(float x)
  {
    if (imageVAlignment != x)
    {
      imageVAlignment = x;
      invalidate();
    }
  }

  public float getVImageAlignment()
  {
    return imageVAlignment;
  }

  public void setImageGap(int x)
  {
    if (imageGap != x)
    {
      imageGap = x;
      invalidate();
    }
  }

  public int getImageGap()
  {
    return imageGap;
  }

  public static int[] analyzeText(String s, java.text.BreakIterator breaker,
      java.awt.FontMetrics theMetrics, int maxWidth)
  {
    breaker.setText(s);
    java.util.ArrayList wrapData = new java.util.ArrayList();
    int currStart = breaker.first();
    int currEnd = breaker.next();
    while (currEnd != java.text.BreakIterator.DONE)
    {
      if (theMetrics.stringWidth(s.substring(currStart, currEnd)) >= maxWidth)
      {
        // Break the text at the last position, unless it's the
        // same as this one, then we'll just have to draw off the end.
        int currPrior = breaker.previous();
        if (currPrior != currStart)
        {
          currEnd = currPrior;
        }
        else
        {
          breaker.next();
        }
        wrapData.add(new Integer(currEnd));
        currStart = currEnd;
      }
      currEnd = breaker.next();
    }

    int[] wrapPos = new int[wrapData.size()];
    for (int i = 0; i < wrapPos.length; i++)
    {
      wrapPos[i] = ((Integer) wrapData.get(i)).intValue();
    }
    return wrapPos;
  }

  private String theString;
  private java.awt.Font myFont;
  private java.awt.FontMetrics myMetrics;
  private String[] textLines;
  private String[] naturalTextLines;
  private java.awt.font.TextLayout[] layoutCache;
  private java.awt.Dimension size;
  private int cachedWidth;
  private boolean wrapText;
  private java.text.BreakIterator lineBreaker;
  private float textAlignmentH;
  private float textAlignmentV;
  protected java.awt.Image myIcon;
  protected float imageVAlignment = TOP_ALIGNMENT;
  protected float imageHAlignment = LEFT_ALIGNMENT;
  protected int imageGap = 2;
}
