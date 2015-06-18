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

public class ActiveImage extends java.awt.Canvas implements java.awt.event.MouseListener
{
  public ActiveImage(java.awt.Image inImage)
  {
    this(inImage, null);
  }
  public ActiveImage(java.awt.Image inImage,
      java.awt.Image inRolloverImage)
  {
    myImage = inImage;
    rolloverImage = inRolloverImage;
    rollState = false;
    pressed = false;
    enabled = true;

    size = new java.awt.Dimension(myImage.getWidth(this), myImage.getHeight(this));
    setSize(size);

    if ((rolloverImage != null) &&
        ((size.width != rolloverImage.getWidth(this)) ||
            (size.height != rolloverImage.getHeight(this))))
    {
      throw new IllegalArgumentException("Both images must be the same size.");
    }

    addMouseListener(this);

    listeners = new java.util.ArrayList();
  }

  public void addActionListener(java.awt.event.ActionListener l)
  {
    if (!listeners.contains(l))
    {
      listeners.add(l);
    }
  }

  public void removeActionListener(java.awt.event.ActionListener l)
  {
    listeners.remove(l);
  }

  public void setImage(java.awt.Image inImage)
  {
    if ((inImage.getWidth(null) != myImage.getWidth(null)) ||
        (inImage.getHeight(null) != myImage.getHeight(null)))
    {
      //throw new IllegalArgumentException("Images must match in size.");
      size = new java.awt.Dimension(inImage.getWidth(null), inImage.getHeight(null));
      setSize(size);
      invalidate();
    }
    myImage = inImage;
    repaint();
  }

  public void mousePressed(java.awt.event.MouseEvent evt)
  {
    pressed = true;
    ignoreRelease = false;
    if (pressedImage != null)
    {
      repaint();
    }
  }

  public void mouseReleased(java.awt.event.MouseEvent evt)
  {
    if (enabled && !ignoreRelease)
    {
      java.awt.event.ActionEvent actEvt = new java.awt.event.ActionEvent(this,
          java.awt.event.ActionEvent.ACTION_PERFORMED, "", 0);
      fireAction(actEvt);
    }
    pressed = false;
    if (pressedImage != null)
    {
      repaint();
    }
  }

  public void mouseClicked(java.awt.event.MouseEvent evt)
  {
  }

  protected void fireAction(java.awt.event.ActionEvent evt)
  {
    for (int i = 0; i < listeners.size(); i++)
    {
      ((java.awt.event.ActionListener) listeners.get(i)).
      actionPerformed(evt);
    }
  }

  public void mouseEntered(java.awt.event.MouseEvent evt)
  {
    if (!rollState)
    {
      rollState = true;
      if (rolloverImage != null)
      {
        repaint();
      }
    }
  }

  public void mouseExited(java.awt.event.MouseEvent evt)
  {
    if (rollState)
    {
      rollState = false;
      if (rolloverImage != null)
      {
        repaint();
      }
    }
    ignoreRelease = true;
    if (pressed)
    {
      pressed = false;
      if (pressedImage != null)
      {
        repaint();
      }
    }
  }

  public void paint(java.awt.Graphics g)
  {
    int x = Math.round((getWidth() - size.width) * hAlignment);
    int y = Math.round((getHeight() - size.height) * vAlignment);
    /*if (!enabled)
		{
			if (!blankWhenDisabled)
			{
				g.drawImage(disabledImage, x, y, this);
			}
		}
		else*/
    {
      if (pressed && (pressedImage != null))
      {
        g.drawImage(pressedImage, x, y, this);
      }
      else if (rollState && (rolloverImage != null))
      {
        g.drawImage(rolloverImage, x, y, this);
      }
      else
      {
        g.drawImage(myImage, x, y, this);
      }
    }
  }

  public void setEnabled(boolean x)
  {
    if (enabled != x)
    {
      enabled = x;
      repaint();
      if (!enabled)
      {
        removeMouseListener(this);
      }
      else
      {
        addMouseListener(this);
      }
    }
  }

  public java.awt.Dimension getPreferredSize()
  {
    return size;
  }

  public java.awt.Dimension getMinimumSize()
  {
    return size;
  }

  public java.awt.Dimension getMaximumSize()
  {
    return MAX_SIZE;
  }

  public void setBlankWhenDisabled(boolean x)
  {
    blankWhenDisabled = x;
  }

  public void setHAlignment(float inHAlignment)
  {
    hAlignment = inHAlignment;
  }

  public void setVAlignment(float inVAlignment)
  {
    vAlignment = inVAlignment;
  }

  public void setPressedImage(java.awt.Image inPressedImage)
  {
    pressedImage = inPressedImage;
  }

  public void update(java.awt.Graphics g)
  {
    paint(g);
  }

  public boolean imageUpdate(java.awt.Image img, int flags, int x, int y,
      int w, int h)
  {
    repaint();
    return true;
  }
  protected java.awt.Image myImage;
  private java.awt.Image disabledImage;
  private java.awt.Image rolloverImage;
  private java.awt.Image pressedImage;
  private java.util.ArrayList listeners;
  protected boolean rollState;
  protected boolean pressed;
  private boolean ignoreRelease;
  protected boolean enabled;
  private boolean blankWhenDisabled = false;
  private java.awt.Dimension size;
  private float hAlignment = 0.5f;
  private float vAlignment = 0.5f;
  private static final java.awt.Dimension MAX_SIZE = new java.awt.Dimension(Integer.MAX_VALUE,
      Integer.MAX_VALUE);
}
