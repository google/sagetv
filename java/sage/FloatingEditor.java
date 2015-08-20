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

import java.text.ParseException;

import javax.swing.JOptionPane;

public class FloatingEditor implements java.awt.event.ActionListener
{
  public static final String[] LAYOUT_MODES = { "SquareGrid",
    "Horizontal", "HorizontalReverse", "HorizontalFill",
    "Vertical", "VerticalReverse", "VerticalFill",
    "HorizontalGrid", "VerticalGrid", "Passive"
  };

  public FloatingEditor(Widget inWidg, OracleTree inTree)
  {
    this(new Widget[] { inWidg }, inTree);
  }
  public FloatingEditor(Widget[] inWidgs, OracleTree inTree)
  {
    widgs = inWidgs;
    tree = inTree;

    kl = createKeyListener();

    win = new java.awt.Frame("Editor");
    win.setLayout(new java.awt.BorderLayout());
    win.addWindowListener(new java.awt.event.WindowAdapter()
    {
      public void windowClosing(java.awt.event.WindowEvent evt)
      {
        if (FloatingEditor.this.apply())
          FloatingEditor.this.destroy();
      }
    });

    myEditor = createEditor(widgs, kl);

    bottomPanel = new java.awt.Panel();
    bottomPanel.setBackground(java.awt.Color.gray);
    bottomPanel.setLayout(new java.awt.FlowLayout());
    okButton = new java.awt.Button("OK");
    okButton.addActionListener(this);
    cancelButton = new java.awt.Button("Cancel");
    cancelButton.addActionListener(this);
    applyButton = new java.awt.Button("Apply");
    applyButton.addActionListener(this);
    revertButton = new java.awt.Button("Revert");
    revertButton.addActionListener(this);

    bottomPanel.add(okButton);
    bottomPanel.add(cancelButton);
    bottomPanel.add(applyButton);
    bottomPanel.add(revertButton);

    win.add(new javax.swing.JScrollPane(myEditor.getComponent(), javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), "Center");
    win.add(bottomPanel, "South");
  }
  private int lastWinX = -1;
  private int lastWinY = -1;
  public void destroy()
  {
    lastWinX = win.getX();
    lastWinY = win.getY();
    win.dispose();
    tree.editorDestroyed(this);
  }

  public void spawn(java.awt.Component coordSys, int x, int y)
  {
    myEditor.initEditingComponent();
    win.pack();

    int xLoc, yLoc;
    if (lastWinX >= 0 && lastWinY >= 0)
    {
      xLoc = lastWinX;
      yLoc = lastWinY;
    }
    else
    {
      //xLoc = x - win.getWidth()/2;
      //yLoc = y - 8; // 8's about good to get into the center of the title bar
      xLoc = x;
      yLoc = y;
    }
    //		xLoc = Math.max(0, xLoc);
    //		yLoc = Math.max(0, yLoc);
    /*		java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().
			getScreenSize();
		xLoc = Math.min(screenSize.width - win.getWidth(), xLoc);
		yLoc = Math.min(screenSize.height - win.getHeight(), yLoc);
		win.setLocation(xLoc, yLoc);*/
    MySwingUtils.safePositionWindow(win, coordSys, xLoc, yLoc);
    win.setVisible(true);
    myEditor.setEditingFocus();
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        try{Thread.sleep(30); }catch(Exception e){}
        java.awt.EventQueue.invokeLater(new Runnable()
        {
          public void run()
          {
            bottomPanel.invalidate();
            win.invalidate();
            win.validate();
            win.repaint();
            bottomPanel.repaint();
            bottomPanel.validate();
          }
        });
      }
    });
  }

  private static String[] actionChoices = null;
  private AbstractEditor createEditor(Widget[] widgs, java.awt.event.KeyListener kl)
  {
    int checkMode = Sage.getBoolean("studio/checkbox_dynamic_properties", false) ? EDIT_TEXT : EDIT_CHECK;
    // 601 if (Widget.IMAGE == widgs[0].widgetType)
    if (widgs[0].isType(Widget.IMAGE))
      return new ExtendableEditor(widgs, kl,
          new EditConfig[] {
          new EditConfig(Widget.FILE, EDIT_IMAGE_FILE, "Image Source File"),
          new EditConfig(Widget.PRESSED_FILE, EDIT_IMAGE_FILE, "Pressed Image Source File"),
          new EditConfig(Widget.HOVER_FILE, EDIT_IMAGE_FILE, "Hover Image Source File"),
          new EditConfig(Widget.DIFFUSE_FILE, EDIT_IMAGE_FILE, "Diffuse Image Source File"),
          new EditConfig(Widget.SCALE_DIFFUSE, checkMode, "Scale Diffused Image:"),
          new EditConfig(Widget.USER_EVENT, EDIT_ENUM, "Fire User Event:", UserEvent.PRETTY_UENAMES),
          new EditConfig(Widget.ANCHOR_X, EDIT_FLOAT_RANGE, "Anchor X:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.ANCHOR_Y, EDIT_FLOAT_RANGE, "Anchor Y:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.FIXED_WIDTH, EDIT_FLOAT_RANGE, "Fixed Width:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.FIXED_HEIGHT, EDIT_FLOAT_RANGE, "Fixed Height:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.ANCHOR_POINT_X, EDIT_FLOAT_RANGE, "Anchor Point X:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.ANCHOR_POINT_Y, EDIT_FLOAT_RANGE, "Anchor Point Y:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.VALIGNMENT, EDIT_FLOAT_RANGE, "VerticalAlignment:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.HALIGNMENT, EDIT_FLOAT_RANGE, "HorizontalAlignment:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.FOREGROUND_ALPHA, EDIT_FLOAT_RANGE, "Transparency:", new double[] {0, 1, 0.025, 1.0}),
          new EditConfig(Widget.FOREGROUND_COLOR, EDIT_COLOR, "Diffuse Color:"),
          new EditConfig(Widget.INSETS, 0, "Insets:"),
          new EditConfig(Widget.RESIZE_IMAGE, checkMode, "Resize to Fit:"),
          new EditConfig(Widget.PRESERVE_ASPECT_RATIO, checkMode, "Preserve Aspect Ratio:"),
          new EditConfig(Widget.CROP_TO_FILL, checkMode, "Crop to Fill Entire Area:"),
          new EditConfig(Widget.CORNER_ARC, 0, "Corner Arc:"),
          new EditConfig(Widget.AUTO_REPEAT_ACTION, checkMode, "Repeat During Mouse Press:"),
          new EditConfig(Widget.BACKGROUND_COMPONENT, checkMode, "Background Component:"),
          new EditConfig(Widget.SCALING_INSETS, 0, "Scaling Insets:"),
          new EditConfig(Widget.ANIMATION, 0, "Animation:"),
          new EditConfig(Widget.Z_OFFSET, 0, "Z Offset:"),
          new EditConfig(Widget.MOUSE_TRANSPARENCY, checkMode, "Mouse Transparency:"),
          new EditConfig(Widget.DURATION, 0, "Cross Fade Duration:"),
          new EditConfig(Widget.BACKGROUND_LOAD, checkMode, "Background Loading:"),
      });
    // 601 else if (Widget.MENU == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.MENU))
    {
      return new ExtendableEditor(widgs, kl,
          new EditConfig[] {
          new EditConfig(Widget.VIDEO_BACKGROUND, checkMode, "Video Background:"),
          new EditConfig(Widget.BACKGROUND_ALPHA, EDIT_INT_RANGE, "Transparency:", new int[] {0, 255, 1, 255}),
          new EditConfig(Widget.FOREGROUND_COLOR, EDIT_COLOR, "Diffuse Color:"),
      });
    }
    // 601 else if (Widget.VIDEO == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.VIDEO))
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.ANCHOR_X, EDIT_FLOAT_RANGE, "Anchor X:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.ANCHOR_Y, EDIT_FLOAT_RANGE, "Anchor Y:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.FIXED_WIDTH, EDIT_FLOAT_RANGE, "Fixed Width:", new double[] {0,1,.01,1}),
          new EditConfig(Widget.FIXED_HEIGHT, EDIT_FLOAT_RANGE, "Fixed Height:", new double[] {0,1,.01,1}),
          new EditConfig(Widget.ANCHOR_POINT_X, EDIT_FLOAT_RANGE, "Anchor Point X:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.ANCHOR_POINT_Y, EDIT_FLOAT_RANGE, "Anchor Point Y:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.BACKGROUND_COMPONENT, checkMode, "Background Component:"),
      });
    // 601 else if (Widget.PANEL == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.PANEL))
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.IGNORE_THEME_PROPERTIES, checkMode, "Ignore Theme Properties:"),
          new EditConfig(Widget.LAYOUT, EDIT_CHOICE, "AutoArrange:", LAYOUT_MODES),
              new EditConfig(Widget.ANCHOR_X, EDIT_FLOAT_RANGE, "Anchor X:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANCHOR_Y, EDIT_FLOAT_RANGE, "Anchor Y:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.FIXED_WIDTH, EDIT_FLOAT_RANGE, "Fixed Width:", new double[] {0,1,.01,1}),
              new EditConfig(Widget.FIXED_HEIGHT, EDIT_FLOAT_RANGE, "Fixed Height:", new double[] {0,1,.01,1}),
              new EditConfig(Widget.ANCHOR_POINT_X, EDIT_FLOAT_RANGE, "Anchor Point X:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANCHOR_POINT_Y, EDIT_FLOAT_RANGE, "Anchor Point Y:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.PAD_X, 0, "Pad X:"),
              new EditConfig(Widget.PAD_Y, 0, "Pad Y:"),
              new EditConfig(Widget.INSETS, 0, "Insets:"),
              new EditConfig(Widget.VALIGNMENT, EDIT_FLOAT_RANGE, "VerticalAlignment:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.HALIGNMENT, EDIT_FLOAT_RANGE, "HorizontalAlignment:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANIMATION, 0, "Animation:"),
              new EditConfig(Widget.SCROLLING, EDIT_ENUM, "Scrolling:",
                  new String[] { "", "Vertical", "Horizontal", "Both" }),
                  new EditConfig(Widget.DURATION, 0, "Scroll Duration:"),
                  new EditConfig(Widget.WRAP_HORIZONTAL_NAVIGATION, checkMode, "Wrap Horizontal Navigation:"),
                  new EditConfig(Widget.WRAP_VERTICAL_NAVIGATION, checkMode, "Wrap Vertical Navigation:"),
                  new EditConfig(Widget.BACKGROUND_ALPHA, EDIT_INT_RANGE, "Transparency:", new int[] {0, 255, 1, 255}),
                  new EditConfig(Widget.FOREGROUND_COLOR, EDIT_COLOR, "Diffuse Color:"),
                  new EditConfig(Widget.BACKGROUND_COMPONENT, checkMode, "Background Component:"),
                  new EditConfig(Widget.Z_OFFSET, 0, "Z Offset:"),
                  new EditConfig(Widget.MOUSE_TRANSPARENCY, checkMode, "Mouse Transparency:"),
      });
    // 601 else if (Widget.ITEM == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.ITEM))
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.IGNORE_THEME_PROPERTIES, checkMode, "Ignore Theme Properties:"),
          new EditConfig(Widget.LAYOUT, EDIT_CHOICE, "AutoArrange:", LAYOUT_MODES),
              new EditConfig(Widget.ANCHOR_X, EDIT_FLOAT_RANGE, "Anchor X:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANCHOR_Y, EDIT_FLOAT_RANGE, "Anchor Y:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.FIXED_WIDTH, EDIT_FLOAT_RANGE, "Fixed Width:", new double[] {0,1,.01,1}),
              new EditConfig(Widget.FIXED_HEIGHT, EDIT_FLOAT_RANGE, "Fixed Height:", new double[] {0,1,.01,1}),
              new EditConfig(Widget.ANCHOR_POINT_X, EDIT_FLOAT_RANGE, "Anchor Point X:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANCHOR_POINT_Y, EDIT_FLOAT_RANGE, "Anchor Point Y:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.PAD_X, 0, "Pad X:"),
              new EditConfig(Widget.PAD_Y, 0, "Pad Y:"),
              new EditConfig(Widget.INSETS, 0, "Insets:"),
              new EditConfig(Widget.VALIGNMENT, EDIT_FLOAT_RANGE, "VerticalAlignment:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.HALIGNMENT, EDIT_FLOAT_RANGE, "HorizontalAlignment:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANIMATION, 0, "Animation:"),
              new EditConfig(Widget.BACKGROUND_ALPHA, EDIT_INT_RANGE, "Transparency:", new int[] {0, 255, 1, 255}),
              new EditConfig(Widget.FOREGROUND_COLOR, EDIT_COLOR, "Diffuse Color:"),
              new EditConfig(Widget.BACKGROUND_COMPONENT, checkMode, "Background Component:"),
              new EditConfig(Widget.Z_OFFSET, 0, "Z Offset:"),
              new EditConfig(Widget.MOUSE_TRANSPARENCY, checkMode, "Mouse Transparency:"),
              new EditConfig(Widget.FOCUSABLE_CONDITION, 0, "Focusable Condition:"),
      });
    // 601 else if (Widget.THEME == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.THEME))
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.BACKGROUND_IMAGE, EDIT_IMAGE_FILE, "Background Image:"),
          new EditConfig(Widget.TILE_BACKGROUND_IMAGE, checkMode, "Tile Background:"),
          new EditConfig(Widget.BACKGROUND_COLOR, EDIT_COLOR, "Background Color:"),
          new EditConfig(Widget.BACKGROUND_ALPHA, EDIT_INT_RANGE, "Background Alpha:", new int[] {0,255,1,255}),
          new EditConfig(Widget.FOCUS_CHANGE_SOUND, EDIT_FILE, "Focus Change Sound:"),
          new EditConfig(Widget.MENU_CHANGE_SOUND, EDIT_FILE, "Menu Change Sound:"),
          new EditConfig(Widget.FOREGROUND_COLOR, EDIT_COLOR, "Foreground Color:"),
          new EditConfig(Widget.FOREGROUND_ALPHA, EDIT_INT_RANGE, "Foreground Alpha:", new int[] {0,255,1,255}),
          new EditConfig(Widget.FOREGROUND_SHADOW_COLOR, EDIT_COLOR, "Foreground Shadow Color:"),
          new EditConfig(Widget.FOREGROUND_SHADOW_ALPHA, EDIT_INT_RANGE, "Foreground Shadow Alpha:", new int[] {0,255,1,255}),
          new EditConfig(Widget.FONT_FACE, EDIT_FONT, "Edit Font:"),

          new EditConfig(Widget.BACKGROUND_SELECTED_IMAGE, EDIT_IMAGE_FILE, "Background Selected Image:"),
          new EditConfig(Widget.STRETCH_BACKGROUND_IMAGE, checkMode, "Stretch Background:"),
          new EditConfig(Widget.BACKGROUND_SELECTED_COLOR, EDIT_COLOR, "Background Selected Color:"),
          new EditConfig(Widget.BACKGROUND_SELECTED_ALPHA, EDIT_INT_RANGE, "Background Selected Alpha:", new int[] {0,255,1,255}),
          new EditConfig(Widget.ITEM_SELECT_SOUND, EDIT_FILE, "Item Select Sound:"),
          new EditConfig(Widget.USER_ACTION_SOUND, EDIT_FILE, "User Action Sound:"),
          new EditConfig(Widget.FOREGROUND_SELECTED_COLOR, EDIT_COLOR, "Foreground Selected Color:"),
          new EditConfig(Widget.FOREGROUND_SELECTED_ALPHA, EDIT_INT_RANGE, "Foreground Selected Alpha:", new int[] {0,255,1,255}),
          new EditConfig(Widget.FOREGROUND_SHADOW_SELECTED_COLOR, EDIT_COLOR, "Foreground Shadow Selected Color:"),
          new EditConfig(Widget.FOREGROUND_SHADOW_SELECTED_ALPHA, EDIT_INT_RANGE, "Foreground Shadow Selected Alpha:", new int[] {0,255,1,255}),
      });
    // 601 else if (Widget.TEXT == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.TEXT))
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.IGNORE_THEME_PROPERTIES, checkMode, "Ignore Theme Properties:"),
          new EditConfig(Widget.ANCHOR_X, EDIT_FLOAT_RANGE, "Anchor X:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.ANCHOR_Y, EDIT_FLOAT_RANGE, "Anchor Y:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.FIXED_WIDTH, EDIT_FLOAT_RANGE, "Fixed Width:", new double[] {0,1,.01,1}),
          new EditConfig(Widget.FIXED_HEIGHT, EDIT_FLOAT_RANGE, "Fixed Height:", new double[] {0,1,.01,1}),
          new EditConfig(Widget.ANCHOR_POINT_X, EDIT_FLOAT_RANGE, "Anchor Point X:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.ANCHOR_POINT_Y, EDIT_FLOAT_RANGE, "Anchor Point Y:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.INSETS, 0, "Insets:"),
          new EditConfig(Widget.AUTOSIZE_TEXT, checkMode, "ShrinkToFit:"),
          new EditConfig(Widget.WRAP_TEXT, checkMode, "Wrap Text:"),
          new EditConfig(Widget.TEXT_SHADOW, checkMode, "Text Shadow:"),
          new EditConfig(Widget.DISABLE_FONT_SCALING, checkMode, "Disable Font Scaling:"),
          new EditConfig(Widget.TEXT_ALIGNMENT, EDIT_FLOAT_RANGE, "Horizontal Text Align:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.VALIGNMENT, EDIT_FLOAT_RANGE, "Vertical Text Align:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.BACKGROUND_COMPONENT, checkMode, "Background Component:"),
          new EditConfig(Widget.ANIMATION, 0, "Animation:"),
          new EditConfig(Widget.Z_OFFSET, 0, "Z Offset:"),
          new EditConfig(Widget.BACKGROUND_ALPHA, EDIT_INT_RANGE, "Transparency:", new int[] {0, 255, 1, 255}),
          new EditConfig(Widget.FOREGROUND_COLOR, EDIT_COLOR, "Diffuse Color:"),
          new EditConfig(Widget.MOUSE_TRANSPARENCY, checkMode, "Mouse Transparency:"),
          new EditConfig(Widget.DURATION, 0, "Cross Fade Duration:"),
      });
    // 601 else if (Widget.TEXTINPUT == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.TEXTINPUT))
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.IGNORE_THEME_PROPERTIES, checkMode, "Ignore Theme Properties:"),
          new EditConfig(Widget.ANCHOR_X, EDIT_FLOAT_RANGE, "Anchor X:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.ANCHOR_Y, EDIT_FLOAT_RANGE, "Anchor Y:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.FIXED_WIDTH, EDIT_FLOAT_RANGE, "Fixed Width:", new double[] {0,1,.01,1}),
          new EditConfig(Widget.FIXED_HEIGHT, EDIT_FLOAT_RANGE, "Fixed Height:", new double[] {0,1,.01,1}),
          new EditConfig(Widget.ANCHOR_POINT_X, EDIT_FLOAT_RANGE, "Anchor Point X:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.ANCHOR_POINT_Y, EDIT_FLOAT_RANGE, "Anchor Point Y:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.INSETS, 0, "Insets:"),
          new EditConfig(Widget.HIDE_TEXT, checkMode, "Hide Text:"),
          new EditConfig(Widget.TEXT_ALIGNMENT, EDIT_FLOAT_RANGE, "Text Align:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.VALIGNMENT, EDIT_FLOAT_RANGE, "Vertical Text Align:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.BACKGROUND_COMPONENT, checkMode, "Background Component:"),
          new EditConfig(Widget.ANIMATION, 0, "Animation:"),
          new EditConfig(Widget.Z_OFFSET, 0, "Z Offset:"),
          new EditConfig(Widget.BACKGROUND_ALPHA, EDIT_INT_RANGE, "Transparency:", new int[] {0, 255, 1, 255}),
          new EditConfig(Widget.FOREGROUND_COLOR, EDIT_COLOR, "Diffuse Color:"),
          new EditConfig(Widget.FOCUSABLE_CONDITION, checkMode, "Focusable and Cursor:"),
      });
    // 601 else if (Widget.SHAPE == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.SHAPE))
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.SHAPE_TYPE, EDIT_CHOICE, "Shape Type:", new String[] {
              "Square", "Rectangle", "Circle", "Oval", "Line", }),
              new EditConfig(Widget.FOREGROUND_COLOR, EDIT_COLOR, "Color:"),
              new EditConfig(Widget.FOREGROUND_ALPHA, EDIT_INT_RANGE, "Alpha:", new int[] {0,255,1,255}),
              new EditConfig(Widget.SHAPE_FILL, checkMode, "Color Fill:"),
              new EditConfig(Widget.ANCHOR_X, EDIT_MULTI_RANGE, "Anchor X:", new double[] {0,1,0.01,0}),
              new EditConfig(Widget.ANCHOR_Y, EDIT_MULTI_RANGE, "Anchor Y:", new double[] {0,1,0.01,0}),
              new EditConfig(Widget.FIXED_WIDTH, EDIT_MULTI_RANGE, "Fixed Width:", new double[] {0,1,0.01,1}),
              new EditConfig(Widget.FIXED_HEIGHT, EDIT_MULTI_RANGE, "Fixed Height:", new double[] {0,1,0.01,1}),
              new EditConfig(Widget.ANCHOR_POINT_X, EDIT_FLOAT_RANGE, "Anchor Point X:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANCHOR_POINT_Y, EDIT_FLOAT_RANGE, "Anchor Point Y:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.THICKNESS, 0, "Thickness:"),
              new EditConfig(Widget.GRADIENT_ANGLE, EDIT_INT_RANGE, "GradientAngle(deg):", new int[] {0,360,1,90}),
              new EditConfig(Widget.GRADIENT_AMOUNT, EDIT_FLOAT_RANGE, "GradientAmount:", new double[] {0,1,0.01,0}),
              new EditConfig(Widget.CORNER_ARC, 0, "Corner Arc:"),
      });
    // 601 else if (Widget.ATTRIBUTE == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.ATTRIBUTE))
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.VALUE, 0, "Value:"),
      });
    // 601 else if (Widget.OPTIONSMENU == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.OPTIONSMENU))
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.IGNORE_THEME_PROPERTIES, checkMode, "Ignore Theme Properties:"),
          new EditConfig(Widget.LAYOUT, EDIT_CHOICE, "AutoArrange:", LAYOUT_MODES),
              new EditConfig(Widget.ANCHOR_X, EDIT_FLOAT_RANGE, "Anchor X:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANCHOR_Y, EDIT_FLOAT_RANGE, "Anchor Y:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.FIXED_WIDTH, EDIT_FLOAT_RANGE, "Fixed Width:", new double[] {0,1,.01,1}),
              new EditConfig(Widget.FIXED_HEIGHT, EDIT_FLOAT_RANGE, "Fixed Height:", new double[] {0,1,.01,1}),
              new EditConfig(Widget.ANCHOR_POINT_X, EDIT_FLOAT_RANGE, "Anchor Point X:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANCHOR_POINT_Y, EDIT_FLOAT_RANGE, "Anchor Point Y:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.PAD_X, 0, "Pad X:"),
              new EditConfig(Widget.PAD_Y, 0, "Pad Y:"),
              new EditConfig(Widget.INSETS, 0, "Insets:"),
              new EditConfig(Widget.VALIGNMENT, EDIT_FLOAT_RANGE, "VerticalAlignment:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.HALIGNMENT, EDIT_FLOAT_RANGE, "HorizontalAlignment:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANIMATION, 0, "Animation:"),
              new EditConfig(Widget.BACKGROUND_ALPHA, EDIT_INT_RANGE, "Transparency:", new int[] {0, 255, 1, 255}),
              new EditConfig(Widget.FOREGROUND_COLOR, EDIT_COLOR, "Diffuse Color:"),
              new EditConfig(Widget.BACKGROUND_COMPONENT, checkMode, "Background Component:"),
      });
    // 601 else if (Widget.TABLE == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.TABLE))
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.IGNORE_THEME_PROPERTIES, checkMode, "Ignore Theme Properties:"),
          new EditConfig(Widget.NUM_ROWS, 0, "NumRows:"),
          new EditConfig(Widget.NUM_COLS, 0, "NumCols:"),
          new EditConfig(Widget.DIMENSIONS, EDIT_ENUM, "Dimensions:",
              new String[] { "", "Vertical", "Horizontal", "Both" }),
              new EditConfig(Widget.TABLE_WRAPPING, EDIT_ENUM, "Wrapping:",
                  new String[] { "", "Vertical", "Horizontal", "Both" }),
                  new EditConfig(Widget.ANCHOR_X, EDIT_FLOAT_RANGE, "Anchor X:", new double[] {0,1,.01,0}),
                  new EditConfig(Widget.ANCHOR_Y, EDIT_FLOAT_RANGE, "Anchor Y:", new double[] {0,1,.01,0}),
                  new EditConfig(Widget.FIXED_WIDTH, EDIT_FLOAT_RANGE, "Fixed Width:", new double[] {0,1,.01,1}),
                  new EditConfig(Widget.FIXED_HEIGHT, EDIT_FLOAT_RANGE, "Fixed Height:", new double[] {0,1,.01,1}),
                  new EditConfig(Widget.ANCHOR_POINT_X, EDIT_FLOAT_RANGE, "Anchor Point X:", new double[] {0,1,.01,0}),
                  new EditConfig(Widget.ANCHOR_POINT_Y, EDIT_FLOAT_RANGE, "Anchor Point Y:", new double[] {0,1,.01,0}),
                  new EditConfig(Widget.INSETS, 0, "Insets:"),
                  new EditConfig(Widget.WRAP_HORIZONTAL_NAVIGATION, checkMode, "Wrap Horizontal Navigation:"),
                  new EditConfig(Widget.WRAP_VERTICAL_NAVIGATION, checkMode, "Wrap Vertical Navigation:"),
                  new EditConfig(Widget.BACKGROUND_ALPHA, EDIT_INT_RANGE, "Transparency:", new int[] {0, 255, 1, 255}),
                  new EditConfig(Widget.FOREGROUND_COLOR, EDIT_COLOR, "Diffuse Color:"),
                  new EditConfig(Widget.BACKGROUND_COMPONENT, checkMode, "Background Component:"),
                  new EditConfig(Widget.ANIMATION, 0, "Animation:"),
                  new EditConfig(Widget.Z_OFFSET, 0, "Z Offset:"),
                  new EditConfig(Widget.DURATION, 0, "Scroll Duration:"),
                  new EditConfig(Widget.AUTO_REPEAT_ACTION, EDIT_FLOAT_RANGE, "Region for Autoscroll:", new double[] {0, 0.5, 0.01, 0}),
      });
    // else if (Widget.TABLECOMPONENT == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.TABLECOMPONENT))
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.IGNORE_THEME_PROPERTIES, checkMode, "Ignore Theme Properties:"),
          new EditConfig(Widget.TABLE_SUBCOMP, EDIT_CHOICE, "TableSubcompType:", new String[] {
              ZDataTable.ROW_HEADER, ZDataTable.COL_HEADER, ZDataTable.CELL, ZDataTable.NOOK,
              ZDataTable.EMPTY_TABLE } ),
              new EditConfig(Widget.ANCHOR_X, EDIT_FLOAT_RANGE, "Anchor X:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANCHOR_Y, EDIT_FLOAT_RANGE, "Anchor Y:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.FIXED_WIDTH, EDIT_FLOAT_RANGE, "Fixed Width:", new double[] {0,1,.01,1}),
              new EditConfig(Widget.FIXED_HEIGHT, EDIT_FLOAT_RANGE, "Fixed Height:", new double[] {0,1,.01,1}),
              new EditConfig(Widget.ANCHOR_POINT_X, EDIT_FLOAT_RANGE, "Anchor Point X:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.ANCHOR_POINT_Y, EDIT_FLOAT_RANGE, "Anchor Point Y:", new double[] {0,1,.01,0}),
              new EditConfig(Widget.PAD_X, 0, "Pad X:"),
              new EditConfig(Widget.PAD_Y, 0, "Pad Y:"),
              new EditConfig(Widget.INSETS, 0, "Insets:"),
              new EditConfig(Widget.VALIGNMENT, 0, "VerticalAlignment:"),
              new EditConfig(Widget.HALIGNMENT, 0, "HorizontalAlignment:"),
              new EditConfig(Widget.WRAP_HORIZONTAL_NAVIGATION, checkMode, "Wrap Horizontal Navigation:"),
              new EditConfig(Widget.WRAP_VERTICAL_NAVIGATION, checkMode, "Wrap Vertical Navigation:"),
              new EditConfig(Widget.BACKGROUND_ALPHA, EDIT_INT_RANGE, "Transparency:", new int[] {0, 255, 1, 255}),
              new EditConfig(Widget.FOREGROUND_COLOR, EDIT_COLOR, "Diffuse Color:"),
              new EditConfig(Widget.BACKGROUND_COMPONENT, checkMode, "Background Component:"),
              new EditConfig(Widget.ANIMATION, 0, "Animation:"),
      });
    // 601 else if (Widget.LISTENER == widgs[0].widgetType)
    else if (widgs[0].isType(Widget.LISTENER))
    {
      java.util.ArrayList listChoices = new java.util.ArrayList();
      listChoices.add(Widget.MOUSE_CLICK);
      listChoices.add(Widget.MOUSE_DRAG);
      listChoices.add(Widget.MOUSE_MOVE);
      listChoices.add(Widget.MOUSE_ENTER);
      listChoices.add(Widget.MOUSE_EXIT);
      listChoices.add(Widget.RAW_KB);
      listChoices.add(Widget.RAW_IR);
      listChoices.add(Widget.NUMBERS);
      for (int i = UserEvent.MIN_EVT_ID; i <= UserEvent.MAX_EVT_ID; i++)
        listChoices.add(UserEvent.getPrettyEvtName(i));
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.LISTENER_EVENT, EDIT_CHOICE, "Listener Type:", (String[])listChoices.toArray(new String[0]))
      });
    }
    else if (widgs[0].isType(Widget.EFFECT))
    {
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[] {
          new EditConfig(Widget.EFFECT_TRIGGER, EDIT_CHOICE, "Trigger:", Widget.EFFECT_TRIGGER_NAMES),
          new EditConfig(Widget.DELAY, 0, "Delay:"),
          new EditConfig(Widget.DURATION, 0, "Duration:"),
          new EditConfig(Widget.REVERSIBLE, checkMode, "Reversible:"),
          new EditConfig(Widget.CLIPPED, checkMode, "Clipped (2D Only):"),
          new EditConfig(Widget.ANCHOR_POINT_X, EDIT_FLOAT_RANGE, "Center X:", new double[] {0,1,.01,0}),
          new EditConfig(Widget.ANCHOR_POINT_Y, EDIT_FLOAT_RANGE, "Center Y:", new double[] {0,1,.01,0}),

          new EditConfig(Widget.START_RENDER_OFFSET_X, EDIT_FLOAT_RANGE, "Start Offset X:", new double[] {-1.0,1,.01,0}),
          new EditConfig(Widget.START_RENDER_OFFSET_Y, EDIT_FLOAT_RANGE, "Start Offset Y:", new double[] {-1.0,1,.01,0}),
          new EditConfig(Widget.START_RENDER_SCALE_X, EDIT_FLOAT_RANGE, "Start Scale X:", new double[] {-2.0,2,.01,1.0}),
          new EditConfig(Widget.START_RENDER_SCALE_Y, EDIT_FLOAT_RANGE, "Start Scale Y:", new double[] {-2.0,2,.01,1.0}),
          new EditConfig(Widget.START_RENDER_ROTATE_X, EDIT_FLOAT_RANGE, "Start Rotate X:", new double[] {-360,360,1,0}),
          new EditConfig(Widget.START_RENDER_ROTATE_Y, EDIT_FLOAT_RANGE, "Start Rotate Y:", new double[] {-360,360,1,0}),
          new EditConfig(Widget.START_RENDER_ROTATE_Z, EDIT_FLOAT_RANGE, "Start Rotate Z:", new double[] {-360,360,1,0}),
          new EditConfig(Widget.FOREGROUND_ALPHA, EDIT_FLOAT_RANGE, "Start Transparency:", new double[] {0,1,.01,1.0}),

          new EditConfig(Widget.TIMESCALE, EDIT_CHOICE, "Timescale:", Widget.EFFECT_TIMESCALES),
          new EditConfig(Widget.EASING, EDIT_CHOICE, "Easing:", Widget.EFFECT_EASING),
          new EditConfig(Widget.KEY, 0, "SmoothTracker Key:"),
          new EditConfig(Widget.LOOP, checkMode, "Loop:"),
          new EditConfig(Widget.MENU_RELATIVE_OFFSETS, checkMode, "Menu Relative Offsets:"),
          new EditConfig(Widget.HALIGNMENT, EDIT_FLOAT_RANGE, "Camera X:", new double[] {0,1,.01,0.5}),
          new EditConfig(Widget.VALIGNMENT, EDIT_FLOAT_RANGE, "Camera Y:", new double[] {0,1,.01,0.5}),

          new EditConfig(Widget.ANCHOR_X, EDIT_FLOAT_RANGE, "End Offset X:", new double[] {-1.0,1,.01,0}),
          new EditConfig(Widget.ANCHOR_Y, EDIT_FLOAT_RANGE, "End Offset Y:", new double[] {-1.0,1,.01,0}),
          new EditConfig(Widget.RENDER_SCALE_X, EDIT_FLOAT_RANGE, "End Scale X:", new double[] {-2.0,2,.01,1.0}),
          new EditConfig(Widget.RENDER_SCALE_Y, EDIT_FLOAT_RANGE, "End Scale Y:", new double[] {-2.0,2,.01,1.0}),
          new EditConfig(Widget.RENDER_ROTATE_X, EDIT_FLOAT_RANGE, "End Rotate X:", new double[] {-360,360,1,0}),
          new EditConfig(Widget.RENDER_ROTATE_Y, EDIT_FLOAT_RANGE, "End Rotate Y:", new double[] {-360,360,1,0}),
          new EditConfig(Widget.RENDER_ROTATE_Z, EDIT_FLOAT_RANGE, "End Rotate Z:", new double[] {-360,360,1,0}),
          new EditConfig(Widget.BACKGROUND_ALPHA, EDIT_FLOAT_RANGE, "End Transparency:", new double[] {0,1,.01,1.0}),
      });
    }
    else if (widgs[0].isType(Widget.HOOK))
      return new HookEditor(widgs, kl);
    else if (widgs[0].isType(Widget.ACTION) || widgs[0].isType(Widget.CONDITIONAL))
    {
      return new ActionEditor(widgs, kl);
    }
    else
      return new ExtendableEditor(widgs,  kl,
          new EditConfig[0]);
  }

  // returns false and optionally reports if a syntax error occured
  public boolean  apply()
  {
    try {
      myEditor.acceptEdit();
    } catch (ParseException e) {
      // parse error for expressions
      if (sage.Sage.getBoolean("studio/alert_on_syntax_error", false)) {
        javax.swing.JOptionPane.showMessageDialog(win,e.getMessage(), "Parse Error",
            JOptionPane.ERROR_MESSAGE);
        return false;
      }
    } finally {
      tree.getUIMgr().trueValidate();
    }
    return true;
  }

  public void revert()
  {
    myEditor.revertEditingComponent();
    tree.getUIMgr().trueValidate();
  }

  public void actionPerformed(java.awt.event.ActionEvent evt)
  {
    if (evt.getSource() == applyButton)
    {
      apply();
    }
    else if (evt.getSource() == revertButton)
    {
      revert();
    }
    else if (evt.getSource() == okButton)
    {
      if ( apply() )
        destroy();
    }
    else if (evt.getSource() == cancelButton)
    {
      revert();
      destroy();
    }
  }

  private java.awt.event.KeyListener createKeyListener()
  {
    return new java.awt.event.KeyAdapter()
    {
      public void keyReleased(java.awt.event.KeyEvent evt)
      {
        if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER)
        {
          evt.consume();
          if (apply())
            if (evt.isControlDown())
              destroy();
        }
        else if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE)
        {
          revert();
          destroy();
        }
      }
    };
  }

  public final Widget getWidget()
  {
    return widgs[0];
  }
  public final Widget[] getWidgets()
  {
    return widgs;
  }

  public void toFront()
  {
    win.toFront();
  }

  private java.awt.Frame win;
  private Widget[] widgs;
  private OracleTree tree;
  private AbstractEditor myEditor;

  private java.awt.event.KeyListener kl;

  private java.awt.Panel bottomPanel;
  private java.awt.Button okButton;
  private java.awt.Button cancelButton;
  private java.awt.Button applyButton;
  private java.awt.Button revertButton;

  public class IntRangeComponent extends javax.swing.JComponent implements EditableResult, java.awt.event.ActionListener
  {
    public IntRangeComponent(String name, int low, int hi, int inc, int defaultVal, Widget[] theWidgs, byte inProp,
        java.awt.event.KeyListener kl)
    {
      super();
      myWidgs =  theWidgs;
      propName = inProp;
      myDefaultVal = defaultVal;
      setLayout(new java.awt.BorderLayout());
      add(new javax.swing.JLabel(name), "West");

      valField = new javax.swing.JTextField(5);
      add(valField, "East");

      slider = new javax.swing.JSlider(javax.swing.JSlider.HORIZONTAL);
      add(slider, "Center");
      slider.setMinimum(low);
      slider.setMaximum(hi);
      slider.setValue(defaultVal);
      expanded = false;
      //valField.setText(Integer.toString(defaultVal));
      slider.addChangeListener(new javax.swing.event.ChangeListener()
      {
        public void stateChanged(javax.swing.event.ChangeEvent evt)
        {
          setSolid(true);
          String newVal = Integer.toString(slider.getValue());
          for (int i = 0; i < myWidgs.length; i++)
            // myWidgs[i].setProperty(propName, newVal);
            WidgetFidget.setProperty(myWidgs[i], propName, newVal);
          valField.setText(newVal);
          tree.getUIMgr().trueValidate();
        }
      });

      if (kl != null)
        valField.addKeyListener(kl);
      valField.addActionListener(this);
      valField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
      {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        private void checkSolid()
        {
          if (valField.getBackground() != java.awt.Color.white)
          {
            valField.setBackground(java.awt.Color.white);
            valField.repaint();
          }
        }
      });
    }

    public String getValue() { return valField.getText(); }
    public void setValue(String s)
    {
      synchronized (getTreeLock())
      {
        if (s.startsWith("="))
        {
          if (!expanded)
          {
            remove(valField);
            remove(slider);
            add(valField, "Center");
            expanded = true;
            getParent().validate();
          }
        }
        else if (expanded)
        {
          remove(valField);
          add(slider, "Center");
          add(valField, "East");
          expanded = false;
          getParent().validate();
        }
      }
      try{
        if (s.length() > 0)
          slider.setValue(Integer.parseInt(s));
        else
          slider.setValue(myDefaultVal);
      }catch (Exception e){}
      if (valField.getBackground() == java.awt.Color.white)
        for (int i = 0; i < myWidgs.length; i++)
          // 601 myWidgs[i].setProperty(propName, s);
          WidgetFidget.setProperty(myWidgs[i], propName, s);
      valField.setText(s);
    }

    /** Invoked when an action occurs.
     */
    public void actionPerformed(java.awt.event.ActionEvent e)
    {
      setValue(valField.getText());
    }

    public void setSolid(boolean x)
    {
      if (x)
      {
        valField.setBackground(java.awt.Color.white);
      }
      else
      {
        valField.setBackground(java.awt.Color.lightGray);
        valField.setText("");
        valField.setBackground(java.awt.Color.lightGray);
      }
    }
    public boolean isSolid() { return valField.getBackground() == java.awt.Color.white; }

    private javax.swing.JSlider slider;
    private Widget[] myWidgs;
    private byte propName;
    private javax.swing.JTextField valField;
    private boolean expanded;
    private int myDefaultVal;
  }

  public static final java.text.DecimalFormat floatFormat = new java.text.DecimalFormat("0.0##");
  public class FloatRangeComponent extends javax.swing.JComponent implements EditableResult, java.awt.event.ActionListener
  {
    public FloatRangeComponent(String name, double low, double hi, double inc, double defaultVal,
        Widget[] theWidgs, byte inProp, java.awt.event.KeyListener kl)
    {
      super();
      myWidgs =  theWidgs;
      propName = inProp;
      setLayout(new java.awt.BorderLayout());
      add(new javax.swing.JLabel(name), "West");
      myLow = low;
      myHi = hi;
      myInc = inc;
      myDefaultVal = defaultVal;

      valField = new javax.swing.JTextField(5);
      add(valField, "East");

      slider = new javax.swing.JSlider(javax.swing.JSlider.HORIZONTAL);
      add(slider, "Center");
      slider.setMinimum(0);
      slider.setMaximum((int)Math.round((myHi-myLow)/myInc));
      slider.setValue((int)Math.round((defaultVal-myLow)/myInc));
      expanded = false;
      //valField.setText(Double.toString(defaultVal));
      slider.addChangeListener(new javax.swing.event.ChangeListener()
      {
        public void stateChanged(javax.swing.event.ChangeEvent evt)
        {
          /*
           * IMPORTANT: LIMIT THE NUMBER OF DECIMAL PLACES HERE. We came across some weird
           * bug where having a double of value 0.47000000000000003 would eventually
           * hang the new Double(String) call. There was no logical reason I could see why
           * this was occuring. It also seemed to be dependent upon enabling the VMR9OSD...weird.
           */
          setSolid(true);
          String newVal = floatFormat.format((slider.getValue()*myInc)+myLow);
          for (int i = 0; i < myWidgs.length; i++)
            // 601 myWidgs[i].setProperty(propName, newVal);
            WidgetFidget.setProperty(myWidgs[i], propName, newVal);
          valField.setText(newVal);
          tree.getUIMgr().trueValidate();
        }
      });

      if (kl != null)
        valField.addKeyListener(kl);
      valField.addActionListener(this);
      valField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
      {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        private void checkSolid()
        {
          if (valField.getBackground() != java.awt.Color.white)
          {
            valField.setBackground(java.awt.Color.white);
            valField.repaint();
          }
        }
      });
    }

    public String getValue()
    {
      return valField.getText();
    }
    public void setValue(String s)
    {
      synchronized (getTreeLock())
      {
        if (s.startsWith("="))
        {
          if (!expanded)
          {
            remove(valField);
            remove(slider);
            add(valField, "Center");
            expanded = true;
            getParent().validate();
          }
        }
        else if (expanded)
        {
          remove(valField);
          add(slider, "Center");
          add(valField, "East");
          expanded = false;
          getParent().validate();
        }
      }
      useForcedVal = true;
      try
      {
        if (s.length() > 0)
          slider.setValue((int)Math.round((Double.parseDouble(s) - myLow)/myInc));
        else
          slider.setValue((int)Math.round((myDefaultVal-myLow)/myInc));
      }
      catch (Exception e)
      {}
      finally
      {
        useForcedVal = false;
      }
      if (valField.getBackground() == java.awt.Color.white)
        for (int i = 0; i < myWidgs.length; i++)
          // 601 myWidgs[i].setProperty(propName, s);
          WidgetFidget.setProperty(myWidgs[i], propName, s);
      valField.setText(s);
    }

    /** Invoked when an action occurs.
     */
    public void actionPerformed(java.awt.event.ActionEvent e)
    {
      setValue(valField.getText());
    }

    public void setSolid(boolean x)
    {
      if (x)
      {
        valField.setBackground(java.awt.Color.white);
      }
      else
      {
        valField.setBackground(java.awt.Color.lightGray);
        valField.setText("");
        valField.setBackground(java.awt.Color.lightGray);
      }
    }
    public boolean isSolid() { return valField.getBackground() == java.awt.Color.white; }

    private javax.swing.JSlider slider;
    private double myLow;
    private double myHi;
    private double myInc;
    private double myDefaultVal;
    private Widget[] myWidgs;
    private byte propName;
    private javax.swing.JTextField valField;

    private boolean useForcedVal;
    private double forcedVal;
    private boolean expanded;
  }

  public class ColorComponent extends javax.swing.JComponent implements java.awt.event.MouseListener,
  java.awt.event.ActionListener, EditableResult
  {
    public ColorComponent(String name, Widget[] theWidgs, byte inProp, java.awt.event.KeyListener kl)
    {
      super();
      myWidgs =  theWidgs;
      propName = inProp;
      this.name = name;
      setLayout(new java.awt.BorderLayout());
      add(new javax.swing.JLabel(name), "West");
      add(colorSample = new javax.swing.JComponent()
      {
        public void paint(java.awt.Graphics g)
        {
          g.setColor(getBackground());
          g.fillRect(1, 1, getWidth()-1, getHeight()-1);
          g.setColor(java.awt.Color.black);
          g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        }
      }, "Center");
      add(colorHexField = new javax.swing.JTextField(8), "East");
      colorHexField.addActionListener(this);
      colorSample.addMouseListener(this);
      if (kl != null)
        colorHexField.addKeyListener(kl);
      colorHexField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
      {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        private void checkSolid()
        {
          if (colorHexField.getBackground() != java.awt.Color.white)
          {
            colorHexField.setBackground(java.awt.Color.white);
            colorHexField.repaint();
          }
        }
      });
    }

    public void actionPerformed(java.awt.event.ActionEvent evt)
    {
      if (colorHexField.getText().length() > 0)
      {
        try
        {
          colorSample.setBackground(java.awt.Color.decode(colorHexField.getText()));
        }
        catch (Exception e){}
        colorSample.repaint();
      }
    }

    public String getValue() { return colorHexField.getText(); }
    public void setValue(String s)
    {
      colorHexField.setText(s);
      actionPerformed(null);
    }

    /** Invoked when the mouse button has been clicked (pressed
     * and released) on a component.
     */
    public void mouseClicked(java.awt.event.MouseEvent e)
    {
      final javax.swing.JColorChooser jcc = new javax.swing.JColorChooser(colorSample.getBackground());
      jcc.getSelectionModel().addChangeListener(new javax.swing.event.ChangeListener()
      {
        public void stateChanged(javax.swing.event.ChangeEvent e)
        {
          setSolid(true);
          java.awt.Color newColor = jcc.getColor();
          for (int i = 0; i < myWidgs.length; i++)
            // 601 myWidgs[i].setProperty(propName, ...
            WidgetFidget.setProperty(myWidgs[i], propName, "0x" + ((newColor.getRed() < 16 ? "0" : "") + Integer.toString(newColor.getRed(), 16) +
                (newColor.getGreen() < 16 ? "0" : "") + Integer.toString(newColor.getGreen(), 16) +
                (newColor.getBlue() < 16 ? "0" : "") + Integer.toString(newColor.getBlue(), 16)));
          tree.getUIMgr().trueValidate();
        }
      });

      javax.swing.JDialog jdc = javax.swing.JColorChooser.createDialog(win,
          name + " Color", true, jcc, new java.awt.event.ActionListener(){
        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
          setSolid(true);
          java.awt.Color newColor = jcc.getColor();
          colorHexField.setText("0x" + ((newColor.getRed() < 16 ? "0" : "") + Integer.toString(newColor.getRed(), 16) +
              (newColor.getGreen() < 16 ? "0" : "") + Integer.toString(newColor.getGreen(), 16) +
              (newColor.getBlue() < 16 ? "0" : "") + Integer.toString(newColor.getBlue(), 16)).toUpperCase());
          for (int i = 0; i < myWidgs.length; i++)
            // 601 myWidgs[i].setProperty(propName, getValue());
            WidgetFidget.setProperty(myWidgs[i], propName, getValue());
          tree.getUIMgr().trueValidate();
        }}, new java.awt.event.ActionListener(){
          public void actionPerformed(java.awt.event.ActionEvent evt)
          {
            setSolid(true);
            for (int i = 0; i < myWidgs.length; i++)
              // 601 myWidgs[i].setProperty(propName, colorHexField.getText());
              WidgetFidget.setProperty(myWidgs[i], propName, colorHexField.getText());
          }});
      jdc.setVisible(true);
      jdc.dispose();
      actionPerformed(null);
      tree.getUIMgr().trueValidate();
    }

    /** Invoked when the mouse enters a component.
     */
    public void mouseEntered(java.awt.event.MouseEvent e)
    {
    }

    /** Invoked when the mouse exits a component.
     */
    public void mouseExited(java.awt.event.MouseEvent e)
    {
    }

    /** Invoked when a mouse button has been pressed on a component.
     */
    public void mousePressed(java.awt.event.MouseEvent e)
    {
    }

    /** Invoked when a mouse button has been released on a component.
     */
    public void mouseReleased(java.awt.event.MouseEvent e)
    {
    }

    public void setSolid(boolean x)
    {
      if (x)
      {
        colorHexField.setBackground(java.awt.Color.white);
      }
      else
      {
        colorHexField.setBackground(java.awt.Color.lightGray);
        colorHexField.setText("");
        colorHexField.setBackground(java.awt.Color.lightGray);
      }
    }
    public boolean isSolid() { return colorHexField.getBackground() == java.awt.Color.white; }

    private javax.swing.JTextField colorHexField;
    private javax.swing.JComponent colorSample;
    private String name;
    private Widget[] myWidgs;
    private byte propName;
  }

  public class ImageFileComponent extends javax.swing.JComponent implements java.awt.event.ActionListener,
  EditableResult
  {
    public ImageFileComponent(String name, java.awt.event.KeyListener kl)
    {
      this(name, true, kl);
    }
    public ImageFileComponent(String name, boolean showImage, java.awt.event.KeyListener kl)
    {
      super();
      setBorder(javax.swing.BorderFactory.createTitledBorder(name));
      setLayout(new java.awt.BorderLayout());
      javax.swing.JComponent buttonPanel = new javax.swing.JPanel();
      buttonPanel.setLayout(new java.awt.BorderLayout());
      setImageFileButton = new javax.swing.JButton("...");
      setImageFileButton.addActionListener(this);
      fileField = new javax.swing.JTextField(12);
      buttonPanel.add(fileField, "Center");
      buttonPanel.add(setImageFileButton, "East");
      add(buttonPanel, "North");
      if (showImage)
      {
        imageComp = new javax.swing.JLabel(tree.getUIMgr().getIcon("Nothing"));
        imageComp.setPreferredSize(new java.awt.Dimension(16, 16));
        imageComp.setMaximumSize(new java.awt.Dimension(128, 128));
        add(imageComp, "Center");
      }
      if (kl != null)
        fileField.addKeyListener(kl);
      fileField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
      {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        private void checkSolid()
        {
          if (fileField.getBackground() != java.awt.Color.white)
          {
            fileField.setBackground(java.awt.Color.white);
            fileField.repaint();
          }
        }
      });
    }

    public void actionPerformed(java.awt.event.ActionEvent evt)
    {
      if (evt.getSource() == setImageFileButton)
      {
        java.awt.FileDialog fd = new java.awt.FileDialog(win,
            "Select File", java.awt.FileDialog.LOAD);
        fd.setDirectory(Sage.get("studio/last_image_browse_dir", System.getProperty("user.dir")));
        fd.pack();
        fd.setLocation(setImageFileButton.getLocationOnScreen());
        fd.setVisible(true);
        String selFilename = fd.getFile();
        String fileDir = fd.getDirectory();
        if ((fileDir != null) && (selFilename != null))
        {
          Sage.put("studio/last_image_browse_dir", fileDir);
          java.io.File selFile = new java.io.File(fileDir, selFilename);
          java.io.File widgFile = new java.io.File(tree.getUIMgr().getModuleGroup().defaultModule.description());
          if (widgFile != null)
          {
            widgFile = widgFile.getParentFile();
            java.io.File selFileParent = selFile.getParentFile();
            java.io.File relativeSelFile = new java.io.File(selFile.getName());
            while (selFileParent != null)
            {
              if (selFileParent.equals(widgFile))
              {
                // Found the relative path from the widget file
                selFile = relativeSelFile;
                break;
              }
              relativeSelFile = new java.io.File(selFileParent.getName(), relativeSelFile.toString());
              selFileParent = selFileParent.getParentFile();
            }
          }
          setSolid(true);
          fileField.setText(selFile.toString());
          if (imageComp != null)
          {
            imageComp.setIcon(tree.getUIMgr().getIcon(selFile.toString()));
            imageComp.setPreferredSize(new java.awt.Dimension(128, 128));
            java.awt.Image img = tree.getUIMgr().getImage(selFile.toString());
            imageComp.setToolTipText("Size: " + img.getWidth(null) + "x" + img.getHeight(null));
          }
        }
      }
    }

    public String getValue() { return fileField.getText(); }
    public void setValue(String s)
    {
      fileField.setText(s);
      if (imageComp != null)
      {
        if (s != null && s.length() > 0)
        {
          imageComp.setIcon(tree.getUIMgr().getIcon(s));
          imageComp.setPreferredSize(new java.awt.Dimension(128, 128));
          java.awt.Image img = tree.getUIMgr().getImage(s);
          imageComp.setToolTipText("Size: " + img.getWidth(null) + "x" + img.getHeight(null));
        }
        else
        {
          imageComp.setIcon(tree.getUIMgr().getIcon("Nothing"));
          imageComp.setToolTipText(null);
        }
      }
    }

    public void setSolid(boolean x)
    {
      if (x)
      {
        fileField.setBackground(java.awt.Color.white);
      }
      else
      {
        fileField.setBackground(java.awt.Color.lightGray);
        fileField.setText("");
        fileField.setBackground(java.awt.Color.lightGray);
      }
    }
    public boolean isSolid() { return fileField.getBackground() == java.awt.Color.white; }

    private javax.swing.JButton setImageFileButton;
    private javax.swing.JTextField fileField;
    private javax.swing.JLabel imageComp;
  }

  public class FontComponent extends javax.swing.JComponent implements java.awt.event.ItemListener
  {
    public FontComponent(String name, java.awt.event.KeyListener kl)
    {
      super();
      //setBorder(javax.swing.BorderFactory.createTitledBorder(name));
      setLayout(new java.awt.GridBagLayout());
      fontNameC = new javax.swing.JComboBox();
      fontNameC.setEditable(true);
      String[] fontNames = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
      fontNameC.addItem("");
      for (int i = 0; i < fontNames.length; i++)
        fontNameC.addItem(fontNames[i]);
      java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
      gbc.weightx = gbc.weighty = 0;
      gbc.gridx = gbc.gridy = 0;
      gbc.gridwidth = gbc.gridheight = 1;
      gbc.gridwidth = 2;
      gbc.weightx = 1;
      gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
      add(fontPreview = new javax.swing.JLabel("AaBbCcDdEe...WwXxYyZz012345679"), gbc);
      gbc.gridx = 0;
      gbc.gridy++;
      gbc.gridwidth = gbc.gridheight = 1;
      gbc.weightx = 0;
      gbc.fill = 0;
      add(new javax.swing.JLabel("Font Size:"), gbc);
      gbc.gridx++;
      gbc.weightx = 1;
      gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
      add(fontSizeField = new javax.swing.JTextField(12), gbc);
      gbc.gridx = 0;
      gbc.gridy++;
      gbc.weightx = 0;
      gbc.fill = 0;
      add(new javax.swing.JLabel("Font Style:"), gbc);
      gbc.gridx++;
      gbc.weightx = 1;
      gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
      add(fontStyleC = new javax.swing.JComboBox(Widget.FONT_STYLE_CHOICES), gbc);
      fontStyleC.setEditable(true);
      gbc.gridx = 0;
      gbc.gridy++;
      gbc.weightx = 0;
      gbc.fill = 0;
      add(new javax.swing.JLabel("Font Name:"), gbc);
      gbc.gridx++;
      add(fontNameC, gbc);
      fontNameC.addItemListener(this);
      fontStyleC.addItemListener(this);
      fontSizeField.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
          itemStateChanged(null);
        }});
      fontSizeField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
      {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        private void checkSolid()
        {
          if (fontSizeField.getBackground() != java.awt.Color.white)
          {
            fontSizeField.setBackground(java.awt.Color.white);
            fontSizeField.repaint();
          }
        }
      });
      if (kl != null)
        fontSizeField.addKeyListener(kl);
    }

    public String getFontName() { return (String) fontNameC.getSelectedItem(); }
    public String getFontSize() { return fontSizeField.getText(); }
    public String getFontStyle() { return (String) fontStyleC.getSelectedItem(); }
    public void setFontName(String s) { fontNameC.setSelectedItem(s); }
    public void setFontSize(String s) { fontSizeField.setText(s); }
    public void setFontStyle(String s) { fontStyleC.setSelectedItem(s); }

    /** Invoked when an item has been selected or deselected by the user.
     * The code written for this method performs the operations
     * that need to occur when an item is selected (or deselected).
     */
    public void itemStateChanged(java.awt.event.ItemEvent e)
    {
      if (e == null)
        setSizeSolid(true);
      else if (e.getSource() == fontNameC)
        setNameSolid(true);
      else
        setStyleSolid(true);
      if (fontNameC.getSelectedItem() == null || fontNameC.getSelectedItem().toString().length() == 0)
      {
        fontPreview.setText("No Font Selected to Preview");
        fontPreview.setFont(new java.awt.Font("Dialog", 0, 12));
      }
      else
      {
        fontPreview.setText("AaBbCcDd012345679");
        try{
          fontPreview.setFont(new java.awt.Font(getFontName(),
              WidgetMeta.getFontStyleForName(getFontStyle()), Integer.parseInt(getFontSize())));
        }catch (Exception e1){}
      }
    }

    public void setSizeSolid(boolean x)
    {
      if (x)
      {
        fontSizeField.setBackground(java.awt.Color.white);
      }
      else
      {
        fontSizeField.setBackground(java.awt.Color.lightGray);
        fontSizeField.setText("");
        fontSizeField.setBackground(java.awt.Color.lightGray);
      }
    }
    public void setNameSolid(boolean x)
    {
      if (x)
      {
        fontNameC.setForeground(java.awt.Color.black);
      }
      else
      {
        fontNameC.setForeground(java.awt.Color.lightGray);
      }
    }
    public void setStyleSolid(boolean x)
    {
      if (x)
      {
        fontStyleC.setForeground(java.awt.Color.black);
      }
      else
      {
        fontStyleC.setForeground(java.awt.Color.lightGray);
      }
    }
    public boolean isSizeSolid() { return fontSizeField.getBackground() == java.awt.Color.white; }
    public boolean isNameSolid() { return fontNameC.getForeground() == java.awt.Color.black; }
    public boolean isStyleSolid() { return fontStyleC.getForeground() == java.awt.Color.black; }

    private javax.swing.JComboBox fontNameC;
    private javax.swing.JComboBox fontStyleC;
    private javax.swing.JTextField fontSizeField;
    private javax.swing.JLabel fontPreview;
  }

  public static class EditConfig
  {
    public EditConfig(byte prop, int type, String label)
    {
      this.prop = prop;
      this.type = type;
      this.label = label;
    }
    public EditConfig(byte prop, int type, String label, int[] choices)
    {
      this.prop = prop;
      this.type = type;
      this.label = label;
      this.intdefs = choices;
    }
    public EditConfig(byte prop, int type, String label, double[] choices)
    {
      this.prop = prop;
      this.type = type;
      this.label = label;
      this.doubledefs = choices;
    }
    public EditConfig(byte prop, int type, String label, String[] choices)
    {
      this.prop = prop;
      this.type = type;
      this.label = label;
      this.choices = choices;
    }
    byte prop;
    int type;
    String label;
    String[] choices;
    int[] intdefs;
    double[] doubledefs;
  }

  public static final int EDIT_TEXT = 0;
  public static final int EDIT_CHOICE = 1;
  public static final int EDIT_CHECK = 2;
  public static final int EDIT_IMAGE_FILE = 3;
  public static final int EDIT_ENUM = 4; // same as choice, but it's the index we set
  public static final int EDIT_FILE = 5;
  public static final int EDIT_FONT = 6;
  public static final int EDIT_COLOR = 7;
  public static final int EDIT_INT_RANGE = 8;
  public static final int EDIT_FLOAT_RANGE = 9;
  public static final int EDIT_MULTI_RANGE = EDIT_FLOAT_RANGE; // for floats or ints, currently does floats

  private interface EditableResult
  {
    public String getValue();
    public void setValue(String s);
    public void setSolid(boolean x);
    public boolean isSolid();
  }

  public class ExtendableEditor extends AbstractEditor
  {
    public ExtendableEditor(Widget[] inWidgs, java.awt.event.KeyListener kl,
        EditConfig[] inEditables)
    {
      super(inWidgs);
      this.editables = inEditables;
      comp = new javax.swing.JPanel();

      comp.setLayout(new java.awt.BorderLayout());
      javax.swing.JLabel nameLabel = new javax.swing.JLabel(Widget.TYPES[widgs[0].type()] + ':');
      nameField = new javax.swing.JTextField(0);
      comp.add(nameLabel, "West");
      comp.add(nameField, "Center");
      if (kl != null)
        nameField.addKeyListener(kl);
      javax.swing.JPanel panny = new javax.swing.JPanel();
      panny.setLayout(new java.awt.GridBagLayout());
      java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.gridwidth = gbc.gridheight = 1;
      gbc.anchor = java.awt.GridBagConstraints.WEST;
      gbc.fill = 0;
      gbc.weightx = gbc.weighty = 0;

      editableComps = new java.awt.Component[editables.length];
      for (int i = 0; i < editables.length; i++)
      {
        gbc.gridx = 0;
        if ((widgs[0].isType(Widget.THEME) || widgs[0].isType(Widget.EFFECT)) && i >= (editables.length + 1)/2)
        {
          gbc.gridx = 2;
          if (i == (editables.length + 1)/2)
            gbc.gridy = 0;
        }
        gbc.weightx = 1;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        if (editables[i].type == EDIT_CHECK) // checkbox
        {
          gbc.gridwidth = 2;
          editableComps[i] = new javax.swing.JCheckBox(editables[i].label);
          final javax.swing.JCheckBox myBox = (javax.swing.JCheckBox) editableComps[i];
          myBox.setIcon(new VariableCheckBoxIcon());
          myBox.addItemListener(new java.awt.event.ItemListener()
          {
            public void itemStateChanged(java.awt.event.ItemEvent evt)
            {
              myBox.putClientProperty("solid", "true");
            }
          });
        }
        else if (editables[i].type == EDIT_IMAGE_FILE) // file
        {
          gbc.gridwidth = 2;
          editableComps[i] = new ImageFileComponent(editables[i].label, kl);
        }
        else if (editables[i].type == EDIT_FILE) // file
        {
          gbc.gridwidth = 2;
          editableComps[i] = new ImageFileComponent(editables[i].label, false, kl);
        }
        else if (editables[i].type == EDIT_FONT)
        {
          gbc.gridwidth = 2;
          editableComps[i] = new FontComponent(editables[i].label, kl);
        }
        else if (editables[i].type == EDIT_INT_RANGE)
        {
          gbc.gridwidth = 2;
          editableComps[i] = new IntRangeComponent(editables[i].label, editables[i].intdefs[0],
              editables[i].intdefs[1], editables[i].intdefs[2], editables[i].intdefs[3], widgs,
              editables[i].prop, kl);
        }
        else if (editables[i].type == EDIT_FLOAT_RANGE)
        {
          gbc.gridwidth = 2;
          editableComps[i] = new FloatRangeComponent(editables[i].label, editables[i].doubledefs[0],
              editables[i].doubledefs[1], editables[i].doubledefs[2], editables[i].doubledefs[3],
              widgs, editables[i].prop, kl);
        }
        else if (editables[i].type == EDIT_COLOR)
        {
          gbc.gridwidth = 2;
          editableComps[i] = new ColorComponent(editables[i].label, widgs, editables[i].prop, kl);
        }
        else
        {
          gbc.gridwidth = 1;
          gbc.weightx = 0;
          panny.add(new javax.swing.JLabel(editables[i].label), gbc);
          gbc.weightx = 1;
          gbc.gridx++;
          switch (editables[i].type)
          {
            case EDIT_CHOICE:
            case EDIT_ENUM:
              editableComps[i] = new javax.swing.JComboBox();
              final javax.swing.JComboBox myBox = (javax.swing.JComboBox) editableComps[i];
              myBox.addItemListener(new java.awt.event.ItemListener()
              {
                public void itemStateChanged(java.awt.event.ItemEvent evt)
                {
                  myBox.setBackground(java.awt.Color.white);
                }
              });
              ((javax.swing.JComboBox) editableComps[i]).setEditable(true);
              ((javax.swing.JComboBox) editableComps[i]).addItem("");
              for (int j = 0; j < editables[i].choices.length; j++)
                ((javax.swing.JComboBox) editableComps[i]).addItem(editables[i].choices[j]);
              break;
            default:
              editableComps[i] = new javax.swing.JTextField(0);
              final javax.swing.JTextField myTexty = (javax.swing.JTextField) editableComps[i];
              myTexty.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
              {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) {}
                private void checkSolid() { myTexty.setBackground(java.awt.Color.white); }
              });
              break;
          }
        }
        if (kl != null)
          editableComps[i].addKeyListener(kl);
        panny.add(editableComps[i], gbc);
        gbc.gridy++;
      }

      comp.add(panny, "South");
    }

    public void initEditingComponent()
    {
      revertProps = new java.util.Vector[widgs.length];
      for (int i = 0; i < widgs.length; i++)
        revertProps[i] = new java.util.Vector();
      String nameValue = widgs[0].getUntranslatedName();
      revertProps[0].add(nameValue);
      boolean revertSolidName = true;
      for (int i = 1; i < widgs.length; i++)
      {
        if (!nameValue.equals(widgs[i].getUntranslatedName()))
          revertSolidName = false;
        revertProps[i].add(widgs[i].getUntranslatedName());
      }
      if (revertSolidName)
      {
        nameField.setText(nameValue);
        nameField.setBackground(java.awt.Color.white);
      }
      else
      {
        nameField.setText("");
        nameField.setBackground(java.awt.Color.lightGray);
      }
      nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
      {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        private void checkSolid()
        {
          if (nameField.getBackground() != java.awt.Color.white)
          {
            nameField.setBackground(java.awt.Color.white);
            nameField.repaint();
          }
        }
      });

      for (int i = 0; i < editables.length; i++)
      {
        try
        {
          if (editables[i].type == EDIT_FONT)
          {
            String commonStyle = widgs[0].getProperty(Widget.FONT_STYLE);
            String commonSize = widgs[0].getProperty(Widget.FONT_SIZE);
            String commonFace = widgs[0].getProperty(Widget.FONT_FACE);
            revertProps[0].add(commonStyle);
            revertProps[0].add(commonSize);
            revertProps[0].add(commonFace);
            boolean solidStyle = true;
            boolean solidSize = true;
            boolean solidFace = true;
            for (int j = 1; j < widgs.length; j++)
            {
              if (!commonStyle.equals(widgs[j].getProperty(Widget.FONT_STYLE)))
                solidStyle = false;
              if (!commonSize.equals(widgs[j].getProperty(Widget.FONT_SIZE)))
                solidSize = false;
              if (!commonFace.equals(widgs[j].getProperty(Widget.FONT_FACE)))
                solidFace = false;
              revertProps[j].add(widgs[j].getProperty(Widget.FONT_STYLE));
              revertProps[j].add(widgs[j].getProperty(Widget.FONT_SIZE));
              revertProps[j].add(widgs[j].getProperty(Widget.FONT_FACE));
            }
            if (solidStyle)
              ((FontComponent) editableComps[i]).setFontStyle(commonStyle);
            ((FontComponent) editableComps[i]).setStyleSolid(solidStyle);
            if (solidSize)
              ((FontComponent) editableComps[i]).setFontSize(commonSize);
            ((FontComponent) editableComps[i]).setNameSolid(solidFace);
            if (solidFace)
              ((FontComponent) editableComps[i]).setFontName(commonFace);
            ((FontComponent) editableComps[i]).setSizeSolid(solidSize);
          }
          else
          {
            String commonValue = widgs[0].getProperty(editables[i].prop);
            revertProps[0].add(commonValue);
            boolean solid = true;
            for (int j = 1; j < widgs.length; j++)
            {
              String otherValue = widgs[j].getProperty(editables[i].prop);
              if ((editables[i].type != EDIT_CHECK && !commonValue.equals(otherValue)) ||
                  (editables[i].type == EDIT_CHECK && Boolean.valueOf(commonValue) != Boolean.valueOf(otherValue)))
                solid = false;
              revertProps[j].add(widgs[j].getProperty(editables[i].prop));
            }
            switch (editables[i].type)
            {
              case EDIT_FILE:
              case EDIT_IMAGE_FILE:
              case EDIT_COLOR:
              case EDIT_FLOAT_RANGE:
              case EDIT_INT_RANGE:
                ((EditableResult) editableComps[i]).setSolid(solid);
                if (solid)
                  ((EditableResult) editableComps[i]).setValue(commonValue);
                break;
              case EDIT_CHECK:
                if (solid && (commonValue == null || commonValue.length() == 0 || commonValue.charAt(0) != '='))
                {
                  ((javax.swing.JCheckBox) editableComps[i]).setSelected(
                      Boolean.valueOf(commonValue).booleanValue());
                  ((javax.swing.JCheckBox) editableComps[i]).putClientProperty("solid", "true");
                }
                else
                {
                  ((javax.swing.JCheckBox) editableComps[i]).setSelected(true);
                  ((javax.swing.JCheckBox) editableComps[i]).putClientProperty("solid", "false");
                }
                break;
              case EDIT_CHOICE:
                if (solid)
                {
                  ((javax.swing.JComboBox) editableComps[i]).setSelectedItem(commonValue);
                  ((javax.swing.JComboBox) editableComps[i]).setBackground(java.awt.Color.white);
                }
                else
                {
                  ((javax.swing.JComboBox) editableComps[i]).setSelectedItem("");
                  ((javax.swing.JComboBox) editableComps[i]).setBackground(java.awt.Color.lightGray);
                }
                break;
              case EDIT_ENUM:
                if (solid)
                {
                  if (commonValue != null && commonValue.startsWith("="))
                    ((javax.swing.JComboBox) editableComps[i]).setSelectedItem(commonValue);
                  else
                    ((javax.swing.JComboBox) editableComps[i]).setSelectedIndex(widgs[0].getIntProperty(editables[i].prop, -1, null, null) + 1);
                  ((javax.swing.JComboBox) editableComps[i]).setBackground(java.awt.Color.white);
                }
                else
                {
                  ((javax.swing.JComboBox) editableComps[i]).setSelectedItem("");
                  ((javax.swing.JComboBox) editableComps[i]).setBackground(java.awt.Color.lightGray);
                }
                break;
              default:
                if (solid)
                {
                  ((javax.swing.JTextField) editableComps[i]).setText(commonValue);
                  ((javax.swing.JTextField) editableComps[i]).setBackground(java.awt.Color.white);
                }
                else
                {
                  ((javax.swing.JTextField) editableComps[i]).setText("");
                  ((javax.swing.JTextField) editableComps[i]).setBackground(java.awt.Color.lightGray);
                }
                break;
            }
          }
        }
        catch (Exception e)
        {
          System.out.println("Parsing initediting error:" + e + " prop=" + Widget.PROPS[editables[i].prop] + " widgs=" + java.util.Arrays.asList(widgs));
          e.printStackTrace();
        }
      }
    }

    public void revertEditingComponent()
    {
      for (int j = 0; j < widgs.length; j++)
      {
        // 601 widgs[j].setName((String) revertProps[j].remove(0));
        WidgetFidget.setName(widgs[j], (String) revertProps[j].remove(0));
        for (int i = 0; i < editables.length; i++)
        {
          if (editables[i].type != EDIT_FONT)
            // 601 widgs[j].setProperty(editables[i].prop, (String) revertProps[j].remove(0));
            WidgetFidget.setProperty(widgs[j], editables[i].prop, (String) revertProps[j].remove(0));
          else
          {
            // 601
            //						widgs[j].setProperty(Widget.FONT_STYLE, (String) revertProps[j].remove(0));
            //						widgs[j].setProperty(Widget.FONT_SIZE, (String) revertProps[j].remove(0));
            //						widgs[j].setProperty(Widget.FONT_FACE, (String) revertProps[j].remove(0));
            WidgetFidget.setProperty(widgs[j], Widget.FONT_STYLE,(String) revertProps[j].remove(0));
            WidgetFidget.setProperty(widgs[j], Widget.FONT_SIZE, (String) revertProps[j].remove(0));
            WidgetFidget.setProperty(widgs[j], Widget.FONT_FACE, (String) revertProps[j].remove(0));
          }
        }
      }
      initEditingComponent();
    }

    public void setEditingFocus()
    {
      java.awt.EventQueue.invokeLater(new Runnable()
      {
        public void run()
        {
          nameField.selectAll();
          nameField.requestFocus();
        }
      });
    }

    public void acceptEdit()  throws ParseException
    {
      for (int j = 0; j < widgs.length; j++)
      {
        if (nameField.getBackground() == java.awt.Color.white)
        {
          // 601 widgs[j].setName(nameField.getText());
          WidgetFidget.setName(widgs[j], nameField.getText());
        }
        for (int i = 0; i < editables.length; i++)
        {
          if (editables[i].type == EDIT_CHECK)
          {
            if (isEditableSolid(i))
            {
              // Only set it if it changed (so don't set "" to be "false")
              if (((javax.swing.JCheckBox) editableComps[i]).isSelected() || widgs[j].getProperty(editables[i].prop).length() != 0)
                WidgetFidget.setProperty(widgs[j], editables[i].prop, getEditableResult(i));
            }
          }
          else if (editables[i].type != EDIT_FONT)
          {
            if (isEditableSolid(i))
              // 601 widgs[j].setProperty(editables[i].prop, getEditableResult(i));
              WidgetFidget.setProperty(widgs[j], editables[i].prop, getEditableResult(i));
          }
          else
          {
            if (((FontComponent) editableComps[i]).isNameSolid())
              // 601 widgs[j].setProperty(Widget.FONT_FACE, ((FontComponent) editableComps[i]).getFontName());
              WidgetFidget.setProperty(widgs[j], Widget.FONT_FACE, ((FontComponent) editableComps[i]).getFontName());
            if (((FontComponent) editableComps[i]).isStyleSolid())
              // 601 widgs[j].setProperty(Widget.FONT_STYLE, ((FontComponent) editableComps[i]).getFontStyle());
              WidgetFidget.setProperty(widgs[j], Widget.FONT_STYLE, ((FontComponent) editableComps[i]).getFontStyle());
            if (((FontComponent) editableComps[i]).isSizeSolid())
              // 601 widgs[j].setProperty(Widget.FONT_SIZE, ((FontComponent) editableComps[i]).getFontSize());
              WidgetFidget.setProperty(widgs[j], Widget.FONT_SIZE, ((FontComponent) editableComps[i]).getFontSize());
          }
        }
      }
      // nielm: syntax check 0th widget so that all edits get applied,
      // but only one error triggered
      Catbert.precompileWidget(widgs[0]);
    }

    private boolean isEditableSolid(int i)
    {
      switch (editables[i].type)
      {
        case EDIT_FILE:
        case EDIT_IMAGE_FILE:
        case EDIT_COLOR:
        case EDIT_FLOAT_RANGE:
        case EDIT_INT_RANGE:
          return ((EditableResult) editableComps[i]).isSolid();
        case EDIT_CHECK:
          return "true".equals(((javax.swing.JCheckBox) editableComps[i]).getClientProperty("solid"));
        case EDIT_CHOICE:
          return ((javax.swing.JComboBox) editableComps[i]).getBackground() == java.awt.Color.white;
        case EDIT_ENUM:
          return ((javax.swing.JComboBox) editableComps[i]).getBackground() == java.awt.Color.white;
        default:
          return ((javax.swing.JTextField) editableComps[i]).getBackground() == java.awt.Color.white;
      }
    }
    private String getEditableResult(int i)
    {
      switch (editables[i].type)
      {
        case EDIT_FILE:
        case EDIT_IMAGE_FILE:
        case EDIT_COLOR:
        case EDIT_FLOAT_RANGE:
        case EDIT_INT_RANGE:
          return ((EditableResult) editableComps[i]).getValue();
        case EDIT_CHECK:
          return Boolean.toString(((javax.swing.JCheckBox) editableComps[i]).isSelected());
        case EDIT_CHOICE:
          return (String) ((javax.swing.JComboBox) editableComps[i]).getSelectedItem();
        case EDIT_ENUM:
          String selObj = (String) ((javax.swing.JComboBox) editableComps[i]).getSelectedItem();
          if (selObj != null && selObj.startsWith("="))
            return selObj;
          int selIdx = ((javax.swing.JComboBox) editableComps[i]).getSelectedIndex();
          return (selIdx == 0) ? "" : Integer.toString(selIdx - 1);
        default:
          return ((javax.swing.JTextField) editableComps[i]).getText();
      }
    }

    private javax.swing.JTextField nameField;
    private EditConfig[] editables;
    private java.awt.Component[] editableComps;
    private java.util.Vector[] revertProps;
  }

  public class ActionEditor extends AbstractEditor
  {
    public ActionEditor(Widget[] inWidgs, java.awt.event.KeyListener kl)
    {
      super(inWidgs);
      comp = new javax.swing.JPanel();

      comp.setLayout(new java.awt.BorderLayout());
      ((javax.swing.JPanel)comp).setDoubleBuffered(true);
      javax.swing.JLabel nameLabel = new javax.swing.JLabel(Widget.TYPES[widgs[0].type()] + ':');
      nameField = new javax.swing.JTextField(40);
      comp.add(nameLabel, "West");
      comp.add(nameField, "Center");
      if (kl != null)
        nameField.addKeyListener(kl);
      nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
      {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { checkSolid(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        private void checkSolid()
        {
          if (nameField.getBackground() != java.awt.Color.white)
          {
            nameField.setBackground(java.awt.Color.white);
            nameField.repaint();
          }
        }
      });


      javax.swing.JPanel panny = new javax.swing.JPanel();
      panny.setLayout(new java.awt.GridBagLayout());
      java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.gridwidth = gbc.gridheight = 1;
      gbc.anchor = java.awt.GridBagConstraints.WEST;
      gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
      gbc.weightx = gbc.weighty = 0;

      javax.swing.JLabel lab = new javax.swing.JLabel("Prefix:");
      gbc.weightx = 1;
      panny.add(lab, gbc);
      gbc.gridx++;
      prefixField = new javax.swing.JTextField(35);
      gbc.weightx = 0;
      panny.add(prefixField, gbc);
      gbc.gridx = 0;
      gbc.gridy++;

      lab = new javax.swing.JLabel("Category:");
      gbc.weightx = 1;
      panny.add(lab, gbc);

      Object[] theCats = PredefinedJEPFunction.categoryDescriptions.keySet().toArray();
      java.util.Arrays.sort(theCats);
      catChoice = new javax.swing.JComboBox(theCats);
      catChoice.insertItemAt("", 0);
      catChoice.setMaximumRowCount(theCats.length + 1);
      catChoice.setPrototypeDisplayValue("1234567890123456789012345678901234567890");
      catChoice.setSelectedIndex(0);
      gbc.gridx++;
      gbc.weightx = 0;
      panny.add(catChoice, gbc);
      catChoice.addItemListener(new java.awt.event.ItemListener()
      {
        public void itemStateChanged(java.awt.event.ItemEvent evt)
        {
          if (evt.getStateChange() == java.awt.event.ItemEvent.SELECTED)
          {
            if (catChoice.getForeground() != java.awt.Color.black)
            {
              catChoice.setForeground(java.awt.Color.black);
              methChoice.setForeground(java.awt.Color.black);
              catChoice.repaint();
              methChoice.repaint();
            }
            java.util.ArrayList methList = (java.util.ArrayList) Catbert.categoryMethodMap.get(evt.getItem());
            if (methList != null)
            {
              String[] methArr = (String[]) methList.toArray(new String[0]);
              java.util.Arrays.sort(methArr);
              synchronized (methChoice.getTreeLock())
              {
                methChoice.removeAllItems();
                methChoice.addItem("");
                for (int i = 0; i < methArr.length; i++)
                  methChoice.addItem(methArr[i]);
              }
              //win.pack();
            }
          }
        }
      });

      lab = new javax.swing.JLabel("Method:");
      gbc.gridx = 0;
      gbc.gridy++;
      gbc.weightx = 1;
      panny.add(lab, gbc);

      methChoice = new javax.swing.JComboBox();
      methChoice.setPrototypeDisplayValue("1234567890123456789012345678901234567890");
      methChoice.setMaximumRowCount(theCats.length + 1);
      gbc.gridx++;
      gbc.weightx = 0;
      panny.add(methChoice, gbc);
      methChoice.addItemListener(new java.awt.event.ItemListener()
      {
        public void itemStateChanged(java.awt.event.ItemEvent evt)
        {
          if (catChoice.getForeground() != java.awt.Color.black)
          {
            catChoice.setForeground(java.awt.Color.black);
            methChoice.setForeground(java.awt.Color.black);
            catChoice.repaint();
            methChoice.repaint();
          }
          Object obj = Catbert.getAPI().get(evt.getItem());
          if (obj instanceof PredefinedJEPFunction)
          {
            PredefinedJEPFunction foo = (PredefinedJEPFunction) obj;
            synchronized (comp.getTreeLock())
            {
              String[] paramDesc = foo.getParamDesc();
              String newName = prefixField.getText() + evt.getItem().toString() + "(";
              for (int i = 0; i < paramDesc.length; i++)
              {
                varNameLabels[i].setText(paramDesc[i] + ":");
                varNameFields[i].setEnabled(true);
                if (i > 0)
                  newName += ", ";
                if (varNameFields[i].getText().length() == 0)
                  newName += paramDesc[i];
                else
                  newName += varNameFields[i].getText();
              }
              for (int i = paramDesc.length; i < varNameFields.length; i++)
              {
                varNameLabels[i].setText("N/A");
                varNameFields[i].setEnabled(false);
              }

              nameField.setText(newName + ")" + suffixField.getText());
              nameField.setBackground(java.awt.Color.white);
            }
            //win.pack();
          }
        }
      });

      varNameLabels = new javax.swing.JLabel[18];
      varNameFields = new javax.swing.JTextField[18];
      gbc.gridx = 0;
      gbc.weightx = 1;
      for (int i = 0; i < varNameLabels.length; i++)
      {
        gbc.gridy++;
        varNameLabels[i] = new javax.swing.JLabel("N/A");
        panny.add(varNameLabels[i], gbc);
      }
      gbc.gridx = 1;
      gbc.gridy = 2;
      javax.swing.event.DocumentListener drfeelgood = new javax.swing.event.DocumentListener()
      {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { updateExpr(e); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { updateExpr(e); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        private void updateExpr(javax.swing.event.DocumentEvent e)
        {
          String prefT = prefixField.getText();
          if (prefixField.getDocument().equals(e.getDocument()))
            prefixField.setBackground(java.awt.Color.white);
          if (suffixField.getDocument().equals(e.getDocument()))
            suffixField.setBackground(java.awt.Color.white);
          Object selIt = methChoice.getSelectedItem();
          String newName = prefT;
          if (selIt != null && selIt.toString().length() > 0)
          {
            newName += selIt.toString() + "(";
            for (int i = 0; i < varNameFields.length; i++)
            {
              if (varNameFields[i].getDocument().equals(e.getDocument()))
                varNameFields[i].setBackground(java.awt.Color.white);
              if (varNameFields[i].isEnabled())
              {
                if (i > 0)
                  newName += ", ";
                if (varNameFields[i].getText().length() == 0)
                  newName += varNameLabels[i].getText().substring(0,
                      varNameLabels[i].getText().length() - 1);
                else
                  newName += varNameFields[i].getText();
              }
            }
            newName += ")";
          }
          nameField.setBackground(java.awt.Color.white);
          nameField.setText(newName + suffixField.getText());
        }
      };
      gbc.weightx = 0;
      for (int i = 0; i < varNameFields.length; i++)
      {
        gbc.gridy++;
        varNameFields[i] = new javax.swing.JTextField(7);
        varNameFields[i].setEnabled(false);
        varNameFields[i].getDocument().addDocumentListener(drfeelgood);

        panny.add(varNameFields[i], gbc);
        if (kl != null)
          varNameFields[i].addKeyListener(kl);
      }

      gbc.gridx = 0;
      gbc.gridy++;
      gbc.weightx = 1;
      lab = new javax.swing.JLabel("Suffix:");
      panny.add(lab, gbc);
      gbc.gridx++;
      gbc.weightx = 0;
      suffixField = new javax.swing.JTextField(35);
      panny.add(suffixField, gbc);
      prefixField.getDocument().addDocumentListener(drfeelgood);
      suffixField.getDocument().addDocumentListener(drfeelgood);
      prefixField.addKeyListener(kl);
      suffixField.addKeyListener(kl);

      comp.add(panny, "South");
    }

    private sage.jep.ASTFunNode findFunctionNode(sage.jep.Node topNode, String preferredName)
    {
      sage.jep.ASTFunNode mainFunNode;
      for (int i = 0; i < topNode.jjtGetNumChildren(); i++)
      {
        sage.jep.Node currNode = topNode.jjtGetChild(i);
        if (currNode instanceof sage.jep.ASTFunNode)
        {
          mainFunNode = (sage.jep.ASTFunNode) currNode;
          if (Catbert.getAPI().get(mainFunNode.getName()) instanceof PredefinedJEPFunction &&
              (preferredName.length() == 0 || mainFunNode.getName().equals(preferredName)))
            return mainFunNode;
        }
        mainFunNode = findFunctionNode(currNode, preferredName);
        if (mainFunNode != null)
          return mainFunNode;
      }
      return null;
    }

    public void initEditingComponent()
    {
      String commonMethod = null;
      String commonPrefix = null;
      String commonSuffix = null;
      String commonName = null;
      String[] commonVars = new String[varNameFields.length];
      boolean solidMethod = true;
      boolean solidPrefix = true;
      boolean solidSuffix = true;
      boolean solidName = true;
      boolean[] solidVars = new boolean[varNameFields.length];
      java.util.Arrays.fill(solidVars, true);
      for (int widgNum = 0; widgNum < widgs.length; widgNum++)
      {
        // Check to see if the name matches that parameters; if not then fix them
        final Widget widg = widgs[widgNum];
        String prefT = widg.getTempProperty("Prefix");
        String selIt = widg.getTempProperty("Method");
        String newName = prefT;
        Object methObj = Catbert.getAPI().get(selIt);
        boolean methDefined = false;
        if (methObj != null && methObj instanceof PredefinedJEPFunction)
        {
          methDefined = true;
          newName += selIt + "(";
          PredefinedJEPFunction foo = (PredefinedJEPFunction) methObj;
          String[] paramDesc = foo.getParamDesc();
          for (int i = 0; i < paramDesc.length; i++)
          {
            if (i > 0)
              newName += ", ";
            if (widg.getTempProperty("Variable" + i).length() == 0)
              newName += "?"; // the name will have it correct
            else
              newName += widg.getTempProperty("Variable" + i);
          }
          newName += ")";
        }
        newName += widg.getTempProperty("Suffix");

        if (!widg.getUntranslatedName().equals(newName))
        {
          // Parse the expression and set the values accordingly
          sage.jep.JEP myParser = new sage.jep.JEP();
          myParser.setFunctionTable((sage.jep.FunctionTable)Catbert.getAPI());
          String resultSymbolName = null;
          String expr = widg.getUntranslatedName();
          int equalIdx = expr.indexOf('=');
          int quoteIdx = expr.indexOf('"');
          int parenIdx = expr.indexOf('(');
          if (equalIdx > 0 && (quoteIdx == -1 || quoteIdx > equalIdx) && (parenIdx == -1 || parenIdx > equalIdx))
          {
            String fooVarName = expr.substring(0, equalIdx).trim();
            if (fooVarName.length() > 0 && Character.isJavaIdentifierStart(fooVarName.charAt(0)))
            {
              boolean badVarName = false;
              for (int i = 1; i < fooVarName.length(); i++)
              {
                if (!Character.isJavaIdentifierPart(fooVarName.charAt(i)))
                {
                  badVarName = true;
                  break;
                }
              }
              // don't mess up == comparisons
              if (!badVarName && expr.charAt(equalIdx + 1) != '=')
              {
                resultSymbolName = fooVarName;
                expr = expr.substring(equalIdx + 1).trim();
              }
            }
          }

          myParser.parseExpression(expr);
          if (!myParser.hasError())
          {
            final sage.jep.Node topNode = myParser.getTopNode();
            // If there's a function node that matches the set method name, then use that.
            // Otherwise use any function node we find
            sage.jep.ASTFunNode mainFunNode = null;
            if (topNode instanceof sage.jep.ASTFunNode)
            {
              if (Catbert.getAPI().get(((sage.jep.ASTFunNode) topNode).getName()) instanceof PredefinedJEPFunction &&
                  (widg.getTempProperty("Method").length() == 0 ||
                  ((sage.jep.ASTFunNode) topNode).getName().equals(widg.getTempProperty("Method"))))
              {
                mainFunNode = ((sage.jep.ASTFunNode) topNode);
              }
            }
            if (mainFunNode == null)
              findFunctionNode(topNode, widg.getTempProperty("Method"));
            if (mainFunNode == null)
              mainFunNode = findFunctionNode(topNode, "");
            if (mainFunNode == null)
            {
              // There's no functions in this expression, make the whole thing a prefix
              widg.setTempProperty("Method", "");
              widg.setTempProperty("Prefix", widg.getUntranslatedName());
              widg.setTempProperty("Suffix", "");
            }
            else
            {
              // Run a visitor through the tree and extract the data that we need
              if (Sage.DBG) System.out.println("Widget values before autoparsing: " + widg);
              final sage.jep.ASTFunNode ourFunNode = mainFunNode;
              widg.setTempProperty("Prefix", ((resultSymbolName == null) ? "" : (resultSymbolName + " = ")));
              widg.setTempProperty("Suffix", "");
              widg.setTempProperty("Method", mainFunNode.getName());
              topNode.jjtAccept(new sage.jep.ParserVisitor()
              {
                public Object visit(sage.jep.SimpleNode node, Object data)
                {
                  System.out.println("ERROR: acceptor not unimplemented in subclass?");
                  data = node.childrenAccept(this, data);
                  return data;
                }

                public Object visit(sage.jep.ASTStart node, Object data)
                {
                  data = node.childrenAccept(this, data);
                  return data;
                }

                public Object visit(sage.jep.ASTFunNode node, Object data)
                {
                  if (node.getName().startsWith("\""))
                  {
                    // Arithmetic operation
                    String arithOp = node.getName().substring(1, node.getName().length() - 1);
                    boolean closeParen = false;
                    if (node.jjtGetParent() == ourFunNode)
                    {
                      varIndex++;
                      appendProperty = "Variable" + varIndex;
                      widg.setTempProperty(appendProperty, "");
                    }
                    else if (node != topNode &&
                        !((sage.jep.ASTFunNode)node.jjtGetParent()).getName().equals(node.getName()))
                    {
                      closeParen = true;
                      widg.setTempProperty(appendProperty, widg.getTempProperty(appendProperty) + "(");
                    }
                    for (int i = 0; i < node.jjtGetNumChildren(); ++i)
                    {
                      if (i > 0)
                        widg.setTempProperty(appendProperty, widg.getTempProperty(appendProperty) +
                            " " + arithOp + " ");
                      else if (i == 0 && node.jjtGetNumChildren() == 1) // unary op
                        widg.setTempProperty(appendProperty, widg.getTempProperty(appendProperty) +
                            arithOp);
                      node.jjtGetChild(i).jjtAccept(this, data);
                    }
                    if (closeParen)
                      widg.setTempProperty(appendProperty, widg.getTempProperty(appendProperty) + ")");
                  }
                  else
                  {
                    if (node == ourFunNode)
                    {
                      varIndex = -1;
                    }
                    else if (node.jjtGetParent() == ourFunNode)
                    {
                      varIndex++;
                      appendProperty = "Variable" + varIndex;
                      widg.setTempProperty(appendProperty, node.getName() + "(");
                    }
                    else
                    {
                      widg.setTempProperty(appendProperty, widg.getTempProperty(appendProperty) +
                          node.getName() + "(");
                    }
                    if (node == ourFunNode)
                    {
                      data = node.childrenAccept(this, data);
                    }
                    else
                    {
                      for (int i = 0; i < node.jjtGetNumChildren(); ++i)
                      {
                        if (i > 0)
                          widg.setTempProperty(appendProperty, widg.getTempProperty(appendProperty) +
                              ", ");
                        node.jjtGetChild(i).jjtAccept(this, data);
                      }
                    }
                    if (node == ourFunNode)
                    {
                      varIndex = -1;
                      appendProperty = "Suffix";
                    }
                    else
                    {
                      widg.setTempProperty(appendProperty, widg.getTempProperty(appendProperty) + ")");
                    }
                  }
                  return data;
                }

                public Object visit(sage.jep.ASTVarNode node, Object data)
                {
                  if (node.jjtGetParent() == ourFunNode)
                  {
                    varIndex++;
                    appendProperty = "Variable" + varIndex;
                    widg.setTempProperty(appendProperty, node.getName());
                  }
                  else
                  {
                    widg.setTempProperty(appendProperty, widg.getTempProperty(appendProperty) +
                        node.getName());
                  }
                  data = node.childrenAccept(this, data);
                  return data;
                }

                public Object visit(sage.jep.ASTConstant node, Object data)
                {
                  Object nodeVal = node.getValue();
                  String nodeStr;
                  if (nodeVal instanceof String)
                  {
                    nodeStr = nodeVal.toString();
                    nodeStr = nodeStr.replaceAll("\\\\", "\\\\\\\\");
                    nodeStr = nodeStr.replaceAll("\"", "\\\\\"");
                    nodeStr = nodeStr.replaceAll("\t", "\\\\t");
                    nodeStr = nodeStr.replaceAll("\r", "\\\\r");
                    nodeStr = nodeStr.replaceAll("\n", "\\\\n");
                    nodeStr = nodeStr.replaceAll("\r\n", "\\\\r\\\\n");
                    nodeStr = "\"" + nodeStr + "\"";
                  }
                  else
                    nodeStr = nodeVal.toString();
                  if (node.jjtGetParent() == ourFunNode)
                  {
                    varIndex++;
                    appendProperty = "Variable" + varIndex;
                    widg.setTempProperty(appendProperty, nodeStr);
                  }
                  else
                  {
                    widg.setTempProperty(appendProperty, widg.getTempProperty(appendProperty) + nodeStr);
                  }
                  data = node.childrenAccept(this, data);
                  return data;
                }
                private String appendProperty = "Prefix";
                private int varIndex = -1;
              }, null);
              if (Sage.DBG) System.out.println("Widget values after autoparsing: " + widg);
            }
          }
          else
          {
            // Unparseable expression, make no changes
            if (Sage.DBG) System.out.println("Unable to parse expression:" + widg.getUntranslatedName());
          }
        }

        if (widgNum == 0)
        {
          commonName = widg.getUntranslatedName();
          commonPrefix = widg.getTempProperty("Prefix");
          commonSuffix = widg.getTempProperty("Suffix");
          commonMethod = widg.getTempProperty("Method");
          for (int i = 0; i < varNameFields.length; i++)
            commonVars[i] = widg.getTempProperty("Variable" + i);
        }
        else
        {
          if (!commonName.equals(widg.getUntranslatedName()))
            solidName = false;
          if (!commonPrefix.equals(widg.getTempProperty("Prefix")))
            solidPrefix = false;
          if (!commonSuffix.equals(widg.getTempProperty("Suffix")))
            solidSuffix = false;
          if (!commonMethod.equals(widg.getTempProperty("Method")))
            solidMethod = false;
          for (int i = 0; i < varNameFields.length; i++)
            if (!commonVars[i].equals(widg.getTempProperty("Variable" + i)))
              solidVars[i] = false;
        }
      }
      if (solidMethod)
      {
        if (widgs[0].getTempProperty("Method").length() > 0)
        {
          catChoice.setSelectedItem(((PredefinedJEPFunction)Catbert.getAPI().
              get(widgs[0].getTempProperty("Method"))).getGroup());
          methChoice.setSelectedItem(widgs[0].getTempProperty("Method"));
        }
        else
        {
          catChoice.setSelectedItem("");
          methChoice.setSelectedItem("");
        }
        catChoice.setForeground(java.awt.Color.black);
        methChoice.setForeground(java.awt.Color.black);
      }
      else
      {
        catChoice.setSelectedItem("");
        methChoice.setSelectedItem("");
        catChoice.setForeground(java.awt.Color.lightGray);
        methChoice.setForeground(java.awt.Color.lightGray);
      }
      for (int i = 0; i < varNameFields.length; i++)
      {
        if (solidVars[i])
        {
          varNameFields[i].setText(widgs[0].getTempProperty("Variable"+ i));
          varNameFields[i].setBackground(java.awt.Color.white);
        }
        else
        {
          varNameFields[i].setText("");
          varNameFields[i].setBackground(java.awt.Color.lightGray);
        }
      }
      if (solidPrefix)
      {
        prefixField.setText(widgs[0].getTempProperty("Prefix"));
        prefixField.setBackground(java.awt.Color.white);
      }
      else
      {
        prefixField.setText("");
        prefixField.setBackground(java.awt.Color.lightGray);
      }
      if (solidSuffix)
      {
        suffixField.setText(widgs[0].getTempProperty("Suffix"));
        suffixField.setBackground(java.awt.Color.white);
      }
      else
      {
        suffixField.setText("");
        suffixField.setBackground(java.awt.Color.lightGray);
      }
      if (solidName)
      {
        nameField.setText(widgs[0].getUntranslatedName());
        nameField.setBackground(java.awt.Color.white);
      }
      else
      {
        nameField.setText("");
        nameField.setBackground(java.awt.Color.lightGray);
      }
    }

    public void revertEditingComponent()
    {
      initEditingComponent();
    }

    public void setEditingFocus()
    {
      java.awt.EventQueue.invokeLater(new Runnable()
      {
        public void run()
        {
          nameField.selectAll();
          nameField.requestFocus();
        }
      });
    }

    public void acceptEdit() throws ParseException
    {
      for (int j = 0; j < widgs.length; j++)
      {
        Widget widg = widgs[j];
        if (nameField.getBackground() == java.awt.Color.white)
          // 601 widg.setName(nameField.getText());
          WidgetFidget.setName(widg, nameField.getText());
        Object o = methChoice.getSelectedItem();
        if (methChoice.getForeground() == java.awt.Color.black)
          widg.setTempProperty("Method", (o == null) ? "" : o.toString());
        for (int i = 0; i < varNameFields.length; i++)
          if (varNameFields[i].getBackground() == java.awt.Color.white)
            widg.setTempProperty("Variable"+i, varNameFields[i].getText());
        if (prefixField.getBackground() == java.awt.Color.white)
          widg.setTempProperty("Prefix", prefixField.getText());
        if (suffixField.getBackground() == java.awt.Color.white)
          widg.setTempProperty("Suffix", suffixField.getText());
      }
      // nielm: syntax check 0th widget so that all edits get applied,
      // but only one error triggered
      Catbert.precompileWidget(widgs[0]);
    }

    private javax.swing.JTextField nameField;
    private javax.swing.JTextField prefixField;
    private javax.swing.JTextField suffixField;
    private javax.swing.JComboBox catChoice;
    private javax.swing.JComboBox methChoice;
    private javax.swing.JLabel[] varNameLabels;
    private javax.swing.JTextField[] varNameFields;
  }

  public class HookEditor extends AbstractEditor implements java.awt.event.ItemListener
  {
    public HookEditor(Widget[] inWidgs, java.awt.event.KeyListener kl)
    {
      super(inWidgs);
      comp = new javax.swing.JPanel();

      comp.setLayout(new java.awt.BorderLayout());
      javax.swing.JLabel nameLabel = new javax.swing.JLabel(Widget.TYPES[widgs[0].type()] + ':');
      java.util.Vector hookies = new java.util.Vector(java.util.Arrays.asList(Catbert.hookNamesWithVars));
      hookies.insertElementAt("", 0);
      hookChoice = new javax.swing.JComboBox(hookies);
      comp.add(nameLabel, "West");
      comp.add(hookChoice, "Center");
      hookChoice.addItemListener(this);
    }

    public void initEditingComponent()
    {
      String commonValue = widgs[0].getUntranslatedName();
      boolean solid = true;
      for (int i = 1; i < widgs.length; i++)
        if (!widgs[i].getUntranslatedName().equals(commonValue))
          solid = false;
      int theIdx = java.util.Arrays.asList(Catbert.hookNames).indexOf(commonValue);
      if (theIdx != -1)
        hookChoice.setSelectedIndex(theIdx + 1); // skip the first empty choice
      if (solid)
      {
        hookChoice.setForeground(java.awt.Color.black);
      }
      else
      {
        hookChoice.setForeground(java.awt.Color.lightGray);
      }
    }

    public void revertEditingComponent()
    {
      initEditingComponent();
    }

    public void setEditingFocus()
    {
      java.awt.EventQueue.invokeLater(new Runnable()
      {
        public void run()
        {
          hookChoice.requestFocus();
        }
      });
    }

    public void acceptEdit()
    {
      if (hookChoice.getForeground() == java.awt.Color.black)
      {
        for (int i = 0; i < widgs.length; i++)
        {
          Widget widg = widgs[i];
          Object o = hookChoice.getSelectedItem();
          if (o == null)
            WidgetFidget.setName(widg, "");
          else
          {
            String s = o.toString();
            WidgetFidget.setName(widg, s);
            if (s.length() > 0)
              WidgetFidget.setName(widg, s.substring(0, s.indexOf('(')));
          }
        }
      }
    }

    public void itemStateChanged(java.awt.event.ItemEvent e)
    {
      hookChoice.setForeground(java.awt.Color.black);
    }

    private javax.swing.JComboBox hookChoice;
  }

  private static class VariableCheckBoxIcon implements javax.swing.Icon, javax.swing.plaf.UIResource, java.io.Serializable {

    protected int getControlSize() { return 13; }

    public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {

      javax.swing.JCheckBox cb = (javax.swing.JCheckBox)c;
      javax.swing.ButtonModel model = cb.getModel();
      int controlSize = getControlSize();

      boolean drawCheck = model.isSelected();

      if ( model.isEnabled() )
      {
        if (model.isPressed() && model.isArmed())
        {
          if (cb.getClientProperty("solid").equals("false"))
            g.setColor(java.awt.Color.lightGray);
          else
            g.setColor(java.awt.Color.white);
          g.fillRect( x, y, controlSize-1, controlSize-1);
          drawPressed3DBorder(g, x, y, controlSize, controlSize);
        }
        else
        {
          if (cb.getClientProperty("solid").equals("false"))
            g.setColor(java.awt.Color.lightGray);
          else
            g.setColor(java.awt.Color.white);
          g.fillRect( x, y, controlSize-1, controlSize-1);
          drawFlush3DBorder(g, x, y, controlSize, controlSize);
        }
        g.setColor( javax.swing.plaf.metal.MetalLookAndFeel.getControlInfo() );
      }
      else
      {
        g.setColor( javax.swing.plaf.metal.MetalLookAndFeel.getControlShadow() );
        g.drawRect( x, y, controlSize-2, controlSize-2);
      }

      if (model.isSelected()) {
        drawCheck(c,g,x,y);
      }

    }
    static void drawPressed3DBorder(java.awt.Graphics g, int x, int y, int w, int h) {
      g.translate( x, y);

      drawFlush3DBorder(g, 0, 0, w, h);

      g.setColor( javax.swing.plaf.metal.MetalLookAndFeel.getControlShadow() );
      g.drawLine( 1, 1, 1, h-2 );
      g.drawLine( 1, 1, w-2, 1 );
      g.translate( -x, -y);
    }
    static void drawFlush3DBorder(java.awt.Graphics g, int x, int y, int w, int h) {
      g.translate( x, y);
      g.setColor( javax.swing.plaf.metal.MetalLookAndFeel.getControlDarkShadow() );
      g.drawRect( 0, 0, w-2, h-2 );
      g.setColor( javax.swing.plaf.metal.MetalLookAndFeel.getControlHighlight() );
      g.drawRect( 1, 1, w-2, h-2 );
      g.setColor( javax.swing.plaf.metal.MetalLookAndFeel.getControl() );
      g.drawLine( 0, h-1, 1, h-2 );
      g.drawLine( w-1, 0, w-2, 1 );
      g.translate( -x, -y);
    }

    protected void drawCheck(java.awt.Component c, java.awt.Graphics g, int x, int y) {
      int controlSize = getControlSize();
      g.fillRect( x+3, y+5, 2, controlSize-8 );
      g.drawLine( x+(controlSize-4), y+3, x+5, y+(controlSize-6) );
      g.drawLine( x+(controlSize-4), y+4, x+5, y+(controlSize-5) );
    }

    public int getIconWidth() {
      return getControlSize();
    }

    public int getIconHeight() {
      return getControlSize();
    }
  }
}
