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

/*
 * This used to extend MultiLineLabel, however the heavyweight component was problematic because
 * it can't be inside a JScrollPane.
 * There's one problem I've noticed with this so far. I think it comes from something to do with
 * how they invalidate their size cache after doing an expand and it kicks in the scroll
 * bar which then invalidates our sizing. I guess it sort of needs to realize that the
 * height is bigger than what is available first, then it messages the scroll pane
 * that it needs the scroll bar, which in turn decreases our width, which then also
 * invalidates the size cache causing a double repaint.
 * I do believe this is a completely unavoidable situation, as it's a classic chicken/egg problem.
 * We'll just have to live with it unless we want to redo how the entire layout manager system works.
 */
public class MultiLineTreeRenderer extends javax.swing.JComponent implements
javax.swing.tree.TreeCellRenderer
{
  public MultiLineTreeRenderer(javax.swing.JTree inTree, java.awt.Font inFont)
  {
    myTree = inTree;
    setLeafIcon(javax.swing.UIManager.getIcon("Tree.leafIcon"));
    setClosedIcon(javax.swing.UIManager.getIcon("Tree.closedIcon"));
    setOpenIcon(javax.swing.UIManager.getIcon("Tree.openIcon"));

    setTextSelectionColor(javax.swing.UIManager.getColor("Tree.selectionForeground"));
    setTextNonSelectionColor(javax.swing.UIManager.getColor("Tree.textForeground"));
    setBackgroundSelectionColor(javax.swing.UIManager.getColor("Tree.selectionBackground"));
    setBackgroundNonSelectionColor(javax.swing.UIManager.getColor("Tree.textBackground"));
    setBorderSelectionColor(javax.swing.UIManager.getColor("Tree.selectionBorderColor"));

    Object value = javax.swing.UIManager.get("Tree.drawsFocusBorderAroundIcon");
    drawsFocusBorderAroundIcon = (value != null && ((Boolean)value).
        booleanValue());

    theString = null;
    wrapText = true;
    lineBreaker = java.text.BreakIterator.getLineInstance();

    textAlignment = LEFT_ALIGNMENT;
    myFont = inFont;
    myMetrics = getFontMetrics(myFont);
    beingDragged = false;
  }

  public java.awt.Component getTreeCellRendererComponent(javax.swing.JTree tree,
      Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean inHasFocus)
  {
    currValue = value;
    String stringValue = (tree instanceof OracleTree) ?
        ((OracleTree) tree).convertValueToText(value, sel, expanded, leaf, row, hasFocus, true) :
          tree.convertValueToText(value, sel, expanded, leaf, row, hasFocus);
        hasFocus = inHasFocus;
        setText(stringValue);
        calculateSize();
        if(sel)
        {
          setForeground(getTextSelectionColor());
        }
        else
        {
          setForeground(getTextNonSelectionColor());
        }

        setComponentOrientation(tree.getComponentOrientation());

        selected = sel;

        return this;
  }

  public void paint(java.awt.Graphics g)
  {
    java.awt.Color bColor;
    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;

    if (selected)
    {
      bColor = getBackgroundSelectionColor();
    }
    else
    {
      bColor = getBackgroundNonSelectionColor();
      if (bColor == null)
      {
        bColor = getBackground();
      }
    }

    int imageOffset = -1;
    if (bColor != null)
    {
      imageOffset = getLabelStart();
      g.setColor(bColor);
      if (getComponentOrientation().isLeftToRight())
      {
        g.fillRect(imageOffset, 0, getWidth() - 1 - imageOffset,
            getHeight());
      }
      else
      {
        g.fillRect(0, 0, getWidth() - 1 - imageOffset,
            getHeight());
      }
    }

    if (hasFocus || beingDragged)
    {
      java.awt.Stroke oldStroke = null;;
      if (beingDragged)
      {
        oldStroke = g2.getStroke();
        g2.setStroke(dashStroke);
      }
      if (drawsFocusBorderAroundIcon)
      {
        imageOffset = 0;
      }
      else if (imageOffset == -1)
      {
        imageOffset = getLabelStart();
      }
      java.awt.Color bsColor = getBorderSelectionColor();

      if (bsColor != null)
      {
        g.setColor(bsColor);
        if (getComponentOrientation().isLeftToRight())
        {
          g.drawRect(imageOffset, 0, getWidth() - 1 - imageOffset,
              getHeight() - 1);
        }
        else
        {
          g.drawRect(0, 0, getWidth() - 1 - imageOffset,
              getHeight() - 1);
        }
      }
      if (beingDragged)
      {
        g2.setStroke(oldStroke);
      }
    }
    int textWidth = getWidth();
    if ((myIcon != null) && (imageHAlignment != CENTER_ALIGNMENT))
    {
      textWidth -= imageGap + myIcon.getIconWidth();
    }
    java.awt.font.TextLayout[] currLayoutCache = layoutCache;
    if ((layoutCache == null) || ((cachedWidth != textWidth) && wrapText))
    {
      cachedWidth = textWidth;
      if (wrapText)
      {
        java.util.ArrayList newText = new java.util.ArrayList();
        for (int i = 0; i < naturalTextLines.length; i++)
        {
          int[] breakPos = analyzeText(
              naturalTextLines[i], lineBreaker, myMetrics, cachedWidth);
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
              if (((String) newText.get(newText.size() - 1)).length() == 0)
              {
                // Don't let zero-length strings slip through.
                newText.remove(newText.size() - 1);
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
      currLayoutCache = layoutCache = new java.awt.font.TextLayout[textLines.length];
      java.awt.font.FontRenderContext frc = g2.getFontRenderContext();
      for (int i = 0; i < textLines.length; i++)
      {
        currLayoutCache[i] = new java.awt.font.TextLayout(textLines[i], myFont, frc);
      }
    }

    java.awt.Color oldColor = g.getColor();

    int currX = (getWidth() - size.width) / 2;
    int currY = (getHeight() - size.height) / 2;
    if (myIcon != null)
    {
      int iconHighlightSize = Sage.getInt("studio/chain_type_highlight_size", 8);
      if (imageHAlignment == CENTER_ALIGNMENT)
      {
        if (imageVAlignment == TOP_ALIGNMENT)
        {
          // Top-center of the label
          if (iconBackgroundColor != null)
          {
            g.setColor(iconBackgroundColor);
            g.fillRect(currX, currY, iconHighlightSize, iconHighlightSize);//size.width, myIcon.getIconHeight());
          }
          if (iconBackgroundColor2 != null)
          {
            g.setColor(iconBackgroundColor2);
            g.fillRect(currX + myIcon.getIconWidth() - iconHighlightSize, currY + myIcon.getIconHeight() - iconHighlightSize, iconHighlightSize, iconHighlightSize);
          }
          myIcon.paintIcon(this, g, currX + Math.round((size.width -
              myIcon.getIconWidth()) * imageHAlignment), currY);
          currY += imageGap + myIcon.getIconHeight();
        }
        else
        {
          // bottom-center of the label
          if (iconBackgroundColor != null)
          {
            g.setColor(iconBackgroundColor);
            g.fillRect(currX, currY + size.height - imageGap - myIcon.getIconHeight(), iconHighlightSize,
                iconHighlightSize);//size.width, myIcon.getIconHeight());
          }
          if (iconBackgroundColor2 != null)
          {
            g.setColor(iconBackgroundColor2);
            g.fillRect(currX + myIcon.getIconWidth() - iconHighlightSize, currY + myIcon.getIconHeight() - iconHighlightSize, iconHighlightSize, iconHighlightSize);
          }
          myIcon.paintIcon(this, g, currX + Math.round((size.width -
              myIcon.getIconWidth()) * imageHAlignment),
              currY + size.height - imageGap - myIcon.getIconHeight());
        }
      }
      else if (imageHAlignment == LEFT_ALIGNMENT)
      {
        // image on the left side
        if (iconBackgroundColor != null)
        {
          g.setColor(iconBackgroundColor);
          g.fillRect(currX, currY, iconHighlightSize, iconHighlightSize);//myIcon.getIconWidth(), size.height);
        }
        if (iconBackgroundColor2 != null)
        {
          g.setColor(iconBackgroundColor2);
          g.fillRect(currX + myIcon.getIconWidth() - iconHighlightSize, currY + myIcon.getIconHeight() - iconHighlightSize, iconHighlightSize, iconHighlightSize);
        }
        myIcon.paintIcon(this, g, currX, currY +
            Math.round((size.height - imageGap - myIcon.getIconHeight()) * imageVAlignment));
        currX += imageGap + myIcon.getIconWidth();
      }
      else
      {
        // image on the right side
        if (iconBackgroundColor != null)
        {
          g.setColor(iconBackgroundColor);
          g.fillRect(currX + size.width - imageGap - myIcon.getIconWidth(), currY,
              iconHighlightSize, iconHighlightSize);//myIcon.getIconWidth(), size.height);
        }
        if (iconBackgroundColor2 != null)
        {
          g.setColor(iconBackgroundColor2);
          g.fillRect(currX + myIcon.getIconWidth() - iconHighlightSize, currY + myIcon.getIconHeight() - iconHighlightSize, iconHighlightSize, iconHighlightSize);
        }
        myIcon.paintIcon(this, g, currX + size.width - imageGap - myIcon.getIconWidth(), currY +
            Math.round((size.height - imageGap - myIcon.getIconHeight()) * imageVAlignment));
      }
    }

    // For single line text, it won't be centered vertically because our size also accounts
    // for the image. Account for that here.
    if (currLayoutCache.length == 1)
    {
      currY = (getHeight() - myMetrics.getHeight()) / 2;
    }

    g.setColor(getForeground());

    for (int i = 0; i < currLayoutCache.length; i++)
    {
      currLayoutCache[i].draw(g2, currX + Math.max((size.width - currLayoutCache[i].getAdvance())*textAlignment, 0),
          currY + currLayoutCache[i].getAscent());
      currY += myMetrics.getHeight();
    }

    g.setColor(oldColor);
  }

  private int getLabelStart()
  {
    if ((myIcon != null) && (getText() != null))
    {
      return myIcon.getIconWidth() + Math.max(0, imageGap - 1);
    }
    return 0;
  }

  /**
   * Overrides <code>JComponent.getPreferredSize</code> to
   * return slightly wider preferred size value.
   */
  public java.awt.Dimension getPreferredSize()
  {
    if ((parentWidth != getParentWidth()) || (size == null))
    {
      calculateSize();
    }
    java.awt.Dimension retDimension = size;

    if (retDimension != null)
    {
      retDimension = new java.awt.Dimension(retDimension.width, retDimension.height);
    }
    return retDimension;
  }

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void validate() {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void invalidate() {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void revalidate() {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void repaint(long tm, int x, int y, int width, int height) {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void repaint(java.awt.Rectangle r) {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue)
  {
    // Strings get interned...
    if (propertyName == "text")
    {
      super.firePropertyChange(propertyName, oldValue, newValue);
    }
  }

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void firePropertyChange(String propertyName, byte oldValue, byte newValue) {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void firePropertyChange(String propertyName, char oldValue, char newValue) {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void firePropertyChange(String propertyName, short oldValue, short newValue) {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void firePropertyChange(String propertyName, int oldValue, int newValue) {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void firePropertyChange(String propertyName, long oldValue, long newValue) {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void firePropertyChange(String propertyName, float oldValue, float newValue) {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void firePropertyChange(String propertyName, double oldValue, double newValue) {}

  /**
   * Overridden for performance reasons.
   * See the <a href="#override">Implementation Note</a>
   * for more information.
   */
  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}

  /**
   * Returns the default icon, for the current laf, that is used to
   * represent non-leaf nodes that are expanded.
   */
  public javax.swing.Icon getDefaultOpenIcon() {
    return javax.swing.UIManager.getIcon("Tree.openIcon");
  }

  /**
   * Returns the default icon, for the current laf, that is used to
   * represent non-leaf nodes that are not expanded.
   */
  public javax.swing.Icon getDefaultClosedIcon() {
    return javax.swing.UIManager.getIcon("Tree.closedIcon");
  }

  /**
   * Returns the default icon, for the current laf, that is used to
   * represent leaf nodes.
   */
  public javax.swing.Icon getDefaultLeafIcon() {
    return javax.swing.UIManager.getIcon("Tree.leafIcon");
  }

  /**
   * Sets the icon used to represent non-leaf nodes that are expanded.
   */
  public void setOpenIcon(javax.swing.Icon newIcon) {
    openIcon = newIcon;
  }

  /**
   * Returns the icon used to represent non-leaf nodes that are expanded.
   */
  public javax.swing.Icon getOpenIcon() {
    return openIcon;
  }

  /**
   * Sets the icon used to represent non-leaf nodes that are not expanded.
   */
  public void setClosedIcon(javax.swing.Icon newIcon) {
    closedIcon = newIcon;
  }

  /**
   * Returns the icon used to represent non-leaf nodes that are not
   * expanded.
   */
  public javax.swing.Icon getClosedIcon() {
    return closedIcon;
  }

  /**
   * Sets the icon used to represent leaf nodes.
   */
  public void setLeafIcon(javax.swing.Icon newIcon) {
    leafIcon = newIcon;
  }

  /**
   * Returns the icon used to represent leaf nodes.
   */
  public javax.swing.Icon getLeafIcon() {
    return leafIcon;
  }

  /**
   * Sets the color the text is drawn with when the node is selected.
   */
  public void setTextSelectionColor(java.awt.Color newColor) {
    textSelectionColor = newColor;
  }

  /**
   * Returns the color the text is drawn with when the node is selected.
   */
  public java.awt.Color getTextSelectionColor() {
    return textSelectionColor;
  }

  /**
   * Sets the color the text is drawn with when the node isn't selected.
   */
  public void setTextNonSelectionColor(java.awt.Color newColor) {
    textNonSelectionColor = newColor;
  }

  /**
   * Returns the color the text is drawn with when the node isn't selected.
   */
  public java.awt.Color getTextNonSelectionColor() {
    return textNonSelectionColor;
  }

  /**
   * Sets the color to use for the background if node is selected.
   */
  public void setBackgroundSelectionColor(java.awt.Color newColor) {
    backgroundSelectionColor = newColor;
  }


  /**
   * Returns the color to use for the background if node is selected.
   */
  public java.awt.Color getBackgroundSelectionColor() {
    return backgroundSelectionColor;
  }

  public void setIconBackgroundColor(java.awt.Color newColor) {
    iconBackgroundColor = newColor;
  }

  public java.awt.Color getIconBackgroundColor() {
    return iconBackgroundColor;
  }

  public void setIconBackgroundColor2(java.awt.Color newColor) {
    iconBackgroundColor2 = newColor;
  }

  public java.awt.Color getIconBackgroundColor2() {
    return iconBackgroundColor2;
  }

  /**
   * Sets the background color to be used for non selected nodes.
   */
  public void setBackgroundNonSelectionColor(java.awt.Color newColor) {
    backgroundNonSelectionColor = newColor;
  }

  /**
   * Returns the background color to be used for non selected nodes.
   */
  public java.awt.Color getBackgroundNonSelectionColor() {
    return backgroundNonSelectionColor;
  }

  /**
   * Sets the color to use for the border.
   */
  public void setBorderSelectionColor(java.awt.Color newColor) {
    borderSelectionColor = newColor;
  }

  /**
   * Returns the color the border is drawn.
   */
  public java.awt.Color getBorderSelectionColor() {
    return borderSelectionColor;
  }

  /**
   * Subclassed to map <code>ColorUIResource</code>s to null. If
   * <code>color</code> is null, or a <code>ColorUIResource</code>, this
   * has the effect of letting the background color of the JTree show
   * through. On the other hand, if <code>color</code> is non-null, and not
   * a <code>ColorUIResource</code>, the background becomes
   * <code>color</code>.
   */
  public void setBackground(java.awt.Color color) {
    if(color instanceof javax.swing.plaf.ColorUIResource)
      color = null;
    super.setBackground(color);
  }

  public void setFont(java.awt.Font f)
  {
    if (!f.equals(myFont))
    {
      myFont = f;
      myMetrics = getFontMetrics(f);
      layoutCache = null;
      size = null;
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
      size = null;
      breakString();
    }
  }

  private void calculateSize()
  {
    size = new java.awt.Dimension();

    // Find the depth of the node
    javax.swing.tree.TreeModel testModel = myTree.getModel();
    int depth = 1;
    if (testModel instanceof OracleTreeModel)
    {
      OracleTreeModel myModel = (OracleTreeModel) myTree.getModel();
      javax.swing.tree.TreePath nodePath = myModel.getPathToNode(currValue);

      depth = (nodePath != null) ? nodePath.getPathCount() - 1 : 1;
    }
    else if (testModel instanceof DebugUITreeModel)
    {
    }
    // This 3 comes out of Sun's tree rendering code, it's to accomdate for some bug
    // they don't know what it is.
    int xOffset = ((SageTreeUI) myTree.getUI()).getRowX(depth) + 3;

    parentWidth = getParentWidth();
    int imageHeight = ((myIcon != null) && (imageHAlignment == CENTER_ALIGNMENT)) ?
        myIcon.getIconHeight() + imageGap : 0;
    int imageWidth = ((myIcon != null) && (imageHAlignment != CENTER_ALIGNMENT)) ?
        myIcon.getIconWidth() + imageGap : 0;
    size.height = imageHeight;
    size.width = imageWidth;
    for (int i = 0; i < naturalTextLines.length; i++)
    {
      int currWidth = myMetrics.stringWidth(naturalTextLines[i]);
      if (currWidth > parentWidth - imageWidth - xOffset)
      {
        size.width = parentWidth - xOffset;

        // This one needs to be wrapped
        size.height += (analyzeText(naturalTextLines[i],
            lineBreaker, myMetrics, parentWidth - imageWidth - xOffset).length + 1) *
            myMetrics.getHeight();
      }
      else
      {
        size.width = Math.max(size.width, currWidth + imageWidth);
        size.height += myMetrics.getHeight();
      }
    }
    if (myIcon != null)
    {
      if (imageHAlignment == CENTER_ALIGNMENT)
      {
        size.width = Math.max(myIcon.getIconWidth(), size.width);
      }
      else
      {
        size.height = Math.max(myIcon.getIconHeight(), size.height);
      }
    }

    size.width += 3;
  }

  public String getText()
  {
    return theString;
  }

  public java.awt.Dimension getMinimumSize()
  {
    return getPreferredSize();
  }

  public void setIcon(javax.swing.Icon inIcon)
  {
    if (myIcon != inIcon)
    {
      myIcon = inIcon;
    }
  }

  public javax.swing.Icon getIcon()
  {
    return myIcon;
  }

  public void setHImageAlignment(float x)
  {
    if (imageHAlignment != x)
    {
      imageHAlignment = x;
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
    }
  }

  public int getImageGap()
  {
    return imageGap;
  }

  public int getParentWidth()
  {
    if (Sage.getBoolean("studio/horizontal_scrolling", true) || (myTree.getParent() == null) || (myTree.getParent().getWidth() <= 0))
    {
      return Integer.MAX_VALUE;
    }
    else
    {
      return myTree.getParent().getWidth();
    }
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
  private float textAlignment;
  protected javax.swing.Icon myIcon;
  protected float imageVAlignment = TOP_ALIGNMENT;
  protected float imageHAlignment = LEFT_ALIGNMENT;
  protected int imageGap = 2;
  private javax.swing.JTree myTree;
  private Object currValue;

  private int parentWidth = Integer.MAX_VALUE;

  /** Is the value currently selected. */
  protected boolean selected;
  /** True if has focus. */
  protected boolean hasFocus;
  /** True if it's being dragged */
  protected boolean beingDragged;
  /** True if draws focus border around icon as well. */
  private boolean drawsFocusBorderAroundIcon;

  // Icons
  /** Icon used to show non-leaf nodes that aren't expanded. */
  transient protected javax.swing.Icon closedIcon;

  /** Icon used to show leaf nodes. */
  transient protected javax.swing.Icon leafIcon;

  /** Icon used to show non-leaf nodes that are expanded. */
  transient protected javax.swing.Icon openIcon;

  // Colors
  /** Color to use for the foreground for selected nodes. */
  protected java.awt.Color textSelectionColor;

  /** Color to use for the foreground for non-selected nodes. */
  protected java.awt.Color textNonSelectionColor;

  /** Color to use for the background when a node is selected. */
  protected java.awt.Color backgroundSelectionColor;

  /** Color to use for the background when the node isn't selected. */
  protected java.awt.Color backgroundNonSelectionColor;

  protected java.awt.Color iconBackgroundColor;
  protected java.awt.Color iconBackgroundColor2;

  /** Color to use for the background when the node isn't selected. */
  protected java.awt.Color borderSelectionColor;

  protected static final java.awt.Stroke dashStroke = new java.awt.BasicStroke(1,
      java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_BEVEL, 1,
      new float[] { 4, 4 }, 0);

  public static int[] analyzeText(String s, java.text.BreakIterator breaker,
      java.awt.FontMetrics theMetrics, int maxWidth)
  {
    java.util.ArrayList wrapData = new java.util.ArrayList();
    synchronized (breaker)
    {
      breaker.setText(s);
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
    }

    int[] wrapPos = new int[wrapData.size()];
    for (int i = 0; i < wrapPos.length; i++)
    {
      wrapPos[i] = ((Integer) wrapData.get(i)).intValue();
    }
    return wrapPos;
  }

}
