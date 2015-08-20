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

public class MySwingUtils
{
  private MySwingUtils()
  {
  }

  public static void showWrappedMessageDialog(String message, String title, int type)
  {
    javax.swing.JTextArea texty = new javax.swing.JTextArea(message);
    texty.setLineWrap(true);
    int col = 50;
    texty.setColumns(col);
    int docLen = texty.getDocument().getLength();
    texty.setRows((int)Math.ceil(docLen/col));
    texty.setWrapStyleWord(true);
    texty.setBackground(null);
    texty.setEditable(false);
    javax.swing.JOptionPane.showMessageDialog(null, texty, title, type);
  }

  // This adds the ENTER key action to this button
  public static void fixOKButton(final javax.swing.JButton okButton)
  {
    okButton.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).
    put(javax.swing.KeyStroke.getKeyStroke(
        java.awt.event.KeyEvent.VK_ENTER, 0), "OK");
    okButton.getActionMap().put("OK",
        new javax.swing.AbstractAction()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        okButton.doClick();
      }
    });
  }

  // This adds the ESCAPE key action to this button
  public static void fixCancelButton(final javax.swing.JButton cancelButton)
  {
    cancelButton.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).
    put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
        "Cancel");
    cancelButton.getActionMap().put("Cancel",
        new javax.swing.AbstractAction()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        cancelButton.doClick();
      }
    });
  }

  /*	// This will make the OK button work on Enter and the Cancel button work on
	// escape.
	public static Object showInputDialogFixed(java.awt.Component parentComponent,
		Object message, String title, int messageType, Object[] selectionValues,
		Object initialSelectionValue)
	{
		javax.swing.JOptionPane pane = new javax.swing.JOptionPane(message,
			messageType, javax.swing.JOptionPane.OK_CANCEL_OPTION, null,
			null, null);
		pane.setWantsInput(true);
		pane.setSelectionValues(selectionValues);
		pane.setInitialSelectionValue(initialSelectionValue);

		// Find the buttons & the main selection components.
		final javax.swing.JButton okButton = getOKButton(pane);
		fixOKButton(okButton);
		final javax.swing.JButton cancelButton = getCancelButton(pane);
		fixCancelButton(cancelButton);

		// If it's a combo box, fix the Enter & Escape keys in there to work properly.
		final javax.swing.JComboBox selector = (javax.swing.JComboBox) getComponentOfTypes(pane, new Class[] {
			javax.swing.JComboBox.class });
		if (selector != null)
		{
			Object originalKey = selector.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0));
			final javax.swing.Action defaultAction =
				selector.getActionMap().get(originalKey);
			javax.swing.Action replaceAction = new javax.swing.AbstractAction()
			{
				public void actionPerformed(java.awt.event.ActionEvent evt)
				{
					if (selector.isPopupVisible())
					{
						defaultAction.actionPerformed(evt);
					}
					else
					{
						okButton.doClick();
					}
				}
			};
			selector.getActionMap().put(originalKey, replaceAction);

			originalKey = selector.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0));
			final javax.swing.Action defaultAction2 =
				selector.getActionMap().get(originalKey);
			replaceAction = new javax.swing.AbstractAction()
			{
				public void actionPerformed(java.awt.event.ActionEvent evt)
				{
					if (selector.isPopupVisible())
					{
						defaultAction2.actionPerformed(evt);
					}
					else
					{
						cancelButton.doClick();
					}
				}
			};
			selector.getActionMap().put(originalKey, replaceAction);
		}

		javax.swing.JDialog dialog = pane.createDialog(parentComponent, title);

		pane.selectInitialValue();
		dialog.show();

		Object value = pane.getInputValue();

		if (value == javax.swing.JOptionPane.UNINITIALIZED_VALUE)
		{
			return null;
		}
		return value;
	}*/

  public static javax.swing.JButton getOKButton(java.awt.Container parent)
  {
    java.awt.Component[] kids = parent.getComponents();
    for (int i = 0; i < kids.length; i++)
    {
      if (kids[i] instanceof javax.swing.JButton)
      {
        javax.swing.JButton currButton = (javax.swing.JButton) kids[i];
        if (currButton.getText().equals(javax.swing.UIManager.
            get("OptionPane.okButtonText")))
        {
          return currButton;
        }
      }
      else if (kids[i] instanceof java.awt.Container)
      {
        javax.swing.JButton retVal = getOKButton((java.awt.Container) kids[i]);
        if (retVal != null)
        {
          return retVal;
        }
      }
    }
    return null;
  }
  /*
	public static javax.swing.JButton getCancelButton(java.awt.Container parent)
	{
		java.awt.Component[] kids = parent.getComponents();
		for (int i = 0; i < kids.length; i++)
		{
			if (kids[i] instanceof javax.swing.JButton)
			{
				javax.swing.JButton currButton = (javax.swing.JButton) kids[i];
				if (currButton.getText().equals(javax.swing.UIManager.
					get("OptionPane.cancelButtonText")))
				{
					return currButton;
				}
			}
			else if (kids[i] instanceof java.awt.Container)
			{
				javax.swing.JButton retVal = getCancelButton((java.awt.Container) kids[i]);
				if (retVal != null)
				{
					return retVal;
				}
			}
		}
		return null;
	}

	public static java.awt.Component getComponentOfTypes(java.awt.Container parent,
		Class[] types)
	{
		java.awt.Component[] kids = parent.getComponents();
		for (int i = 0; i < kids.length; i++)
		{
			for (int j = 0; j < types.length; j++)
			{
				if (types[j].isInstance(kids[i]))
				{
					return kids[i];
				}
			}
			if (kids[i] instanceof java.awt.Container)
			{
				java.awt.Component retVal =
					getComponentOfTypes((java.awt.Container) kids[i], types);
				if (retVal != null)
				{
					return retVal;
				}
			}
		}
		return null;
	}
   */
  // If the x, y is where you wanted to show the menu at, this will ensure that the menu doesn't
  // go off the screen.
  public static void safeShowPopupMenu(javax.swing.JPopupMenu theMenu,
      java.awt.Component coordSys, int x, int y)
  {
    theMenu.pack();

    // Check to see if we want to put the menu origin up some because it
    // might go off the screen.
    java.awt.Point screenLoc = coordSys.getLocationOnScreen();
    screenLoc.x += x;
    screenLoc.y += y;
    java.awt.GraphicsDevice[] screens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().
        getScreenDevices();
    // Find the monitor we're displaying this on and make sure it fits within its whole bounds
    java.awt.Rectangle scrBounds = null;
    java.awt.Insets screenInsets = null;
    for (int i = 0; i < screens.length; i++)
    {
      scrBounds = screens[i].getDefaultConfiguration().getBounds();
      screenInsets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(screens[i].getDefaultConfiguration());
      scrBounds.x += screenInsets.left;
      scrBounds.y += screenInsets.top;
      scrBounds.width -= screenInsets.left + screenInsets.right + 50;
      scrBounds.height -= screenInsets.top + screenInsets.bottom + 50;
      if (scrBounds.contains(screenLoc.x, screenLoc.y))
      {
        break;
      }
    }
    if (scrBounds == null)
    {
      //HUH?
      theMenu.show(coordSys, screenLoc.x, screenLoc.y);
    }
    else
    {
      if ((screenLoc.x + theMenu.getWidth()) > scrBounds.x + scrBounds.width - screenInsets.right)
      {
        x -= theMenu.getWidth();
        screenLoc.x -= theMenu.getWidth();
      }
      if ((screenLoc.y + theMenu.getHeight()) > scrBounds.y + scrBounds.height - screenInsets.bottom)
      {
        y -= theMenu.getHeight();
        screenLoc.y -= theMenu.getHeight();
      }
      if (screenLoc.x < screenInsets.left + scrBounds.x)
        x += scrBounds.x + screenInsets.left - screenLoc.x;
      if (screenLoc.y < screenInsets.top + scrBounds.y)
        y += scrBounds.y + screenInsets.top - screenLoc.y;
      theMenu.show(coordSys, x, y);
    }
  }

  public static void safePositionWindow(java.awt.Window theDialog,
      java.awt.Component coordSys, int x, int y)
  {
    theDialog.pack();

    // Check to see if we want to put the menu origin up some because it
    // might go off the screen.
    java.awt.Point screenLoc = coordSys.getLocationOnScreen();
    screenLoc.x += x;
    screenLoc.y += y;
    java.awt.GraphicsDevice[] screens = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().
        getScreenDevices();
    // Find the monitor we're displaying this on and make sure it fits within its whole bounds
    java.awt.Rectangle scrBounds = null;
    java.awt.Insets screenInsets = null;
    for (int i = 0; i < screens.length; i++)
    {
      scrBounds = screens[i].getDefaultConfiguration().getBounds();
      screenInsets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(screens[i].getDefaultConfiguration());
      scrBounds.x += screenInsets.left;
      scrBounds.y += screenInsets.top;
      scrBounds.width -= screenInsets.left + screenInsets.right + 50;
      scrBounds.height -= screenInsets.top + screenInsets.bottom + 50;
      if (scrBounds.contains(screenLoc.x, screenLoc.y))
      {
        break;
      }
    }
    if (scrBounds == null)
    {
      //HUH?
      theDialog.setLocation(screenLoc.x, screenLoc.y);
    }
    else
    {
      if ((screenLoc.x + theDialog.getWidth()) > scrBounds.x + scrBounds.width - screenInsets.right)
      {
        //				x -= theDialog.getWidth();
        screenLoc.x -= theDialog.getWidth();
      }
      if ((screenLoc.y + theDialog.getHeight()) > scrBounds.y + scrBounds.height - screenInsets.bottom)
      {
        //				y -= theDialog.getHeight();
        screenLoc.y -= theDialog.getHeight();
      }
      if (screenLoc.x < screenInsets.left + scrBounds.x)
        screenLoc.x += scrBounds.x + screenInsets.left - screenLoc.x;
      if (screenLoc.y < screenInsets.top + scrBounds.y)
        screenLoc.y += scrBounds.y + screenInsets.top - screenLoc.y;
      theDialog.setLocation(screenLoc.x, screenLoc.y);
    }
  }

  public static void safePositionDialog(javax.swing.JDialog theDialog)
  {
    // Check to see if we want to put the menu origin up some because it
    // might go off the screen.
    int x = theDialog.getX();
    int y = theDialog.getY();
    java.awt.Point screenLoc = new java.awt.Point(x, y);
    //screenLoc.x += x;
    //screenLoc.y += y;
    java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
    java.awt.Insets screenInsets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration());
    if ((screenLoc.x + theDialog.getWidth()) > screenSize.width - screenInsets.right)
    {
      x -= theDialog.getWidth();
      screenLoc.x -= theDialog.getWidth();
    }
    if ((screenLoc.y + theDialog.getHeight()) > screenSize.height - screenInsets.bottom)
    {
      y -= theDialog.getHeight();
      screenLoc.y -= theDialog.getHeight();
    }
    if (screenLoc.x < screenInsets.left)
      x += screenInsets.left - screenLoc.x;
    if (screenLoc.y < screenInsets.top)
      y += screenInsets.top - screenLoc.y;
    theDialog.setLocation(x, y);
  }

  /*
	public static void addRecursiveMouseListener(java.awt.Container cont,
		java.awt.event.MouseListener ml)
	{
		cont.addMouseListener(ml);
		java.awt.Component[] kids = cont.getComponents();
		for (int i = 0; i < kids.length; i++)
		{
			if (kids[i] instanceof java.awt.Container)
			{
				addRecursiveMouseListener((java.awt.Container) kids[i], ml);
			}
			else
			{
				kids[i].addMouseListener(ml);
			}
		}
	}

	public static java.awt.Color averageColors(java.awt.Color c1, java.awt.Color c2)
	{
		return new java.awt.Color((c1.getRed() + c2.getRed())/2, (c1.getGreen() + c2.getGreen())/2,
			(c1.getBlue() + c2.getBlue())/2);
	}

	public static void clipInternalFrameCorrectly(java.awt.Graphics2D g2, javax.swing.JComponent drawInMe)
	{
		 // Due to bug #4167933 on the JDC (Which they say is fixed, but is NOT), a call to getGraphics()
		 // gives back on that is inappropriately clipped. So now I have to clip all this myself
		 // through all the internal frames inside the dekstop pane.

		// Get the internal frame we're in
		javax.swing.JInternalFrame intFrame = (javax.swing.JInternalFrame) javax.swing.SwingUtilities.getAncestorOfClass(
			javax.swing.JInternalFrame.class, drawInMe);

		// Get the desktop pane that we're in
		javax.swing.JDesktopPane desktop = (javax.swing.JDesktopPane) javax.swing.SwingUtilities.getAncestorOfClass(
			javax.swing.JDesktopPane.class, drawInMe);

		// Get all of the frames in the desktop
		javax.swing.JInternalFrame[] allFrames = desktop.getAllFrames();

		// This is the total area we're going to draw in. To calculate the correct one we do this:
		// 1. Set the clipping to be the current components area.
		// 2. Walk through the array of frames and find out where ours is.
		// 3. Walk backwards to the beginning of the array, and for all the other frames in the array
		//    we'll pass, those are all higher up in layer than us.
		// 4. Subtract the geometry of those frames in our coordinate system from our available area.
		// 5. When we're done we should have a shape that outlines are exposed drawing area and
		//    accomodates the clipping bug in Swing regarding Internal Frames.
		java.awt.geom.Area drawArea;
		if (g2.getClip() == null)
		{
			drawArea = new java.awt.geom.Area(new java.awt.Rectangle(0, 0, drawInMe.getWidth(), drawInMe.getHeight()));
		}
		else
		{
			drawArea = new java.awt.geom.Area(g2.getClip());
		}
		int i = 0;
		for (; i < allFrames.length; i++)
		{
			if (allFrames[i] == intFrame)
			{
				break;
			}
		}

		java.awt.Rectangle currRect = new java.awt.Rectangle();
		for (i = i - 1; i >= 0; i--)
		{
			allFrames[i].getBounds(currRect);
			java.awt.Rectangle convRect = javax.swing.SwingUtilities.convertRectangle(allFrames[i].getParent(), currRect,
				drawInMe);
			drawArea.subtract(new java.awt.geom.Area(convRect));
		}

		g2.clip(drawArea);
	}
   */
}
