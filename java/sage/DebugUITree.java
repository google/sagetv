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

public class DebugUITree extends javax.swing.JTree implements java.awt.event.MouseListener, javax.swing.event.TreeSelectionListener
{
  public DebugUITree(UIManager inUIMgr, DebugUITreeModel model)
  {
    super(model);
    uiMgr = inUIMgr;
    setEditable(false);
    setRootVisible(false);
    setInvokesStopCellEditing(true);
    setShowsRootHandles(true);
    setExpandsSelectedPaths(true);
    putClientProperty("JTree.lineStyle", "Angled");
    setForeground(java.awt.Color.black);
    setBackground(java.awt.Color.white);

    addMouseListener(this);

    final java.awt.Font standardFont = new java.awt.Font(uiMgr.get("studio/text_font", "Times New Roman"),
        java.awt.Font.PLAIN, 12);
    final java.awt.Font italicFont = standardFont.deriveFont(java.awt.Font.ITALIC);
    final java.awt.Font boldFont = standardFont.deriveFont(java.awt.Font.BOLD);
    final java.awt.Font standardCodeFont = new java.awt.Font(uiMgr.get("studio/code_font", "Monospaced"),
        java.awt.Font.PLAIN, 12);
    final java.awt.Font italicCodeFont = standardCodeFont.deriveFont(java.awt.Font.ITALIC);
    final java.awt.Font boldCodeFont = standardCodeFont.deriveFont(java.awt.Font.BOLD);
    myCellRenderer = new MultiLineTreeRenderer(this, standardFont)
    {
      public java.awt.Component getTreeCellRendererComponent(javax.swing.JTree tree,
          Object value, boolean sel, boolean expanded, boolean leaf, int row,
          boolean hasFocus)
      {
        super.beingDragged = false;
        Widget myW = null;
        setFont(standardFont);
        setIconBackgroundColor(null);
        setIconBackgroundColor2(null);
        if (value instanceof ZPseudoComp)
        {
          myW = ((ZPseudoComp) value).getWidget();
          setIcon(uiMgr.getIcon(Widget.TYPES[myW.type()]));
          if (!((ZPseudoComp) value).passesUpwardConditional())
            setIconBackgroundColor(java.awt.Color.yellow);
          if (myW != ((ZPseudoComp) value).getPropertyWidget())
            setIconBackgroundColor2(java.awt.Color.orange);
        }
        else if (value instanceof Widget)
        {
          myW = (Widget) value;
          setIcon(uiMgr.getIcon(Widget.TYPES[myW.type()]));
        }
        else if (value instanceof MetaImage)
        {
          setIcon(uiMgr.getIcon(Widget.TYPES[Widget.IMAGE]));
        }
        else if (value instanceof String)
        {
          if ("BGFILL".equals(value))
          {
            setIcon(uiMgr.getIcon(Widget.TYPES[Widget.SHAPE]));
          }
          else if ("BGVIDEO".equals(value))
          {
            setIcon(uiMgr.getIcon(Widget.TYPES[Widget.VIDEO]));
          }
          else
            setIcon(null);
        }
        else if (value instanceof java.util.Vector)
        {
          setIcon(expanded ? openIcon : closedIcon);
        }
        else
        {
          setIcon(null);
        }
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        return this;
      }
    };
    setCellRenderer(myCellRenderer);
    myCellRenderer.setBackground(java.awt.Color.white);
    myCellRenderer.setBackgroundNonSelectionColor(java.awt.Color.white);
    myCellRenderer.setTextNonSelectionColor(java.awt.Color.black);

    addTreeSelectionListener(this);

    oldTreeWidth = 0;
    addComponentListener(new java.awt.event.ComponentAdapter()
    {
      public synchronized void componentResized(java.awt.event.ComponentEvent evt)
      {
        if (oldTreeWidth != getWidth())
        {
          // Avoid spurious resize events, cause they happen
          oldTreeWidth = getWidth();
          ((SageTreeUI)getUI()).recalculateSizing();
        }
      }
    });

    expandChildrenMenuItem = new javax.swing.JMenuItem("Expand Children");
    expandChildrenMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
        java.awt.event.KeyEvent.VK_E, java.awt.event.ActionEvent.CTRL_MASK));
    expandChildrenMenuItem.setMnemonic(java.awt.event.KeyEvent.VK_E);
    expandChildrenMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
        for (int i = 0; i < selPaths.length; i++)
          expandAllNodes(selPaths[i]);
      }});
    expandAllNodesMenuItem= new javax.swing.JMenuItem("Expand All Nodes");
    expandAllNodesMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        expandAllNodes();
      }});
    collapseAllNodesMenuItem= new javax.swing.JMenuItem("Collapse All Nodes");
    collapseAllNodesMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
        java.awt.event.KeyEvent.VK_E, java.awt.event.ActionEvent.CTRL_MASK | java.awt.event.ActionEvent.SHIFT_MASK));
    collapseAllNodesMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        collapseAllNodes();
      }});
    highlightWidgetMenuItem = new javax.swing.JMenuItem("Highlight Widget");
    highlightWidgetMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
        if (selPaths != null && selPaths.length > 0)
        {
          // Use the first path only
          Object lastElem = selPaths[0].getLastPathComponent();
          if (lastElem instanceof ZPseudoComp)
          {
            uiMgr.getStudio().showAndHighlightNode(((ZPseudoComp)lastElem).getWidget());
          }
          else if (lastElem instanceof Widget)
          {
            uiMgr.getStudio().showAndHighlightNode((Widget)lastElem);
          }
          else
          {
            // Other things may be from themes....it'd be nice to show those links
          }
        }
      }});
    highlightWidgetThemeMenuItem = new javax.swing.JMenuItem("Highlight Themed Widget Source");
    highlightWidgetThemeMenuItem .addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
        if (selPaths != null && selPaths.length > 0)
        {
          // Use the first path only
          Object lastElem = selPaths[0].getLastPathComponent();
          if (lastElem instanceof ZPseudoComp)
          {
            uiMgr.getStudio().showAndHighlightNode(((ZPseudoComp)lastElem).getPropertyWidget());
          }
        }
      }});

    rightClicky = new javax.swing.JPopupMenu("DebugUITree Righty");
    rightClicky.add(expandAllNodesMenuItem);
    rightClicky.add(collapseAllNodesMenuItem);
    rightClicky.add(expandChildrenMenuItem);
    rightClicky.addSeparator();
    rightClicky.add(highlightWidgetMenuItem);
    rightClicky.add(highlightWidgetThemeMenuItem);
  }

  public String convertValueToText(Object value, boolean selected,
      boolean expanded, boolean leaf, int row, boolean hasFocus)
  {
    String retVal = null;
    Widget widgy = null;
    if (value instanceof ZPseudoComp)
    {
      widgy = ((ZPseudoComp) value).getWidget();
    }
    else if (value instanceof Widget)
    {
      widgy = (Widget) value;
    }
    else if (value instanceof MetaImage)
    {
      retVal = ((MetaImage)value).getSource().toString();
    }
    else if (value instanceof java.util.Vector)
    {
      retVal = "Circularities";
    }
    else
    {
      retVal = value.toString();
    }
    if (retVal == null)
    {
      retVal = widgy.getUntranslatedName();
      if (widgy.isType(Widget.LISTENER) && (retVal == null || retVal.length() == 0))
        retVal = widgy.getProperty(Widget.LISTENER_EVENT);
      if (retVal == null || retVal.length() == 0)
      {
        retVal = "Untitled";
      }
    }
    return retVal;
  }

  public void mouseEntered(java.awt.event.MouseEvent evt)
  {
  }

  public void mouseExited(java.awt.event.MouseEvent evt)
  {
  }

  public void mousePressed(java.awt.event.MouseEvent evt)
  {
  }

  public void mouseReleased(java.awt.event.MouseEvent evt)
  {
    if (javax.swing.SwingUtilities.isRightMouseButton(evt))
    {
      showRightClickMenu(evt.getX(), evt.getY());
    }
  }

  private void showRightClickMenu(int evtx, int evty)
  {
    /*
     * Case 1: Right clicking with nothing already selected on nothing will show the global options
     * Case 2: Right clicking with nothing already selected on a node will select that node, & show single + global options
     * Case 3: Right clicking with one node selected on any node will select that new node, & show single + global options
     * Case 4: Right clicking with multi-nodes selected anywhere will show multi + global options
     */
    javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
    javax.swing.tree.TreePath clickTree = getPathForLocation(evtx, evty);
    boolean showSingleOps = false;
    boolean showMultiOps = false;
    if ((selPaths == null || selPaths.length == 0) && clickTree == null)
    {
      // Case 1
    }
    else if (selPaths == null || selPaths.length <= 1)
    {
      // Case 2 & 3
      if (clickTree != null)
      {
        setSelectionPath(clickTree);
        selPaths = getSelectionPaths();
      }
      showSingleOps = true;
      showMultiOps = true;
    }
    else
    {
      // Case 4
      showMultiOps = true;
    }
    Widget singleWidg = null;
    ZPseudoComp theComp = null;
    if (showSingleOps)
    {
      Object lastElem = selPaths[0].getLastPathComponent();
      if (lastElem instanceof Widget)
        singleWidg = (Widget) lastElem;
      else if (lastElem instanceof ZPseudoComp)
      {
        theComp = (ZPseudoComp) lastElem;
        singleWidg = theComp.getWidget();
      }
    }
    expandChildrenMenuItem.setEnabled(showMultiOps);
    highlightWidgetMenuItem.setEnabled(showSingleOps && singleWidg != null);
    highlightWidgetThemeMenuItem.setEnabled(theComp != null && theComp.getWidget() != theComp.getPropertyWidget());
    MySwingUtils.safeShowPopupMenu(rightClicky, this, evtx, evty);
  }

  public void mouseClicked(java.awt.event.MouseEvent evt)
  {
  }

  public void valueChanged(javax.swing.event.TreeSelectionEvent evt)
  {
    javax.swing.tree.TreePath selPath = evt.getPath();
    if (selPath != null)
    {
      Object lastGuy = selPath.getLastPathComponent();
      Widget dawidg = null;
      if (lastGuy instanceof ZPseudoComp)
      {
        dawidg = ((ZPseudoComp) lastGuy).getWidget();
      }
      else if (lastGuy instanceof Widget)
      {
        dawidg = (Widget) lastGuy;
      }
      if (dawidg != null)
      {
        Widget[] ws = uiMgr.getModuleGroup().getWidgets();
        java.util.ArrayList paintyWidgs = new java.util.ArrayList();
        for (int i = 0; i < ws.length; i++)
        {
          if (ws[i].tempHighlight())
          {
            paintyWidgs.add(ws[i]);
            ws[i].tempHighlight(false);
          }
        }
        paintyWidgs.add(dawidg);
        dawidg.tempHighlight(true);
        PseudoMenu currUI = uiMgr.getCurrUI();
        for (int i = 0; i < paintyWidgs.size(); i++)
        {
          java.util.Vector pseud = currUI.getCompsForWidget((Widget) paintyWidgs.get(i));
          if (!pseud.isEmpty())
          {
            for (int j = 0; j < pseud.size(); j++)
              ((ZPseudoComp) pseud.get(j)).appendToDirty(false);
          }
        }
      }
    }
  }

  public void cleanup()
  {
    removeMouseListener(this);
    removeTreeSelectionListener(this);
  }

  public void expandAllNodes()
  {
    expandAllNodes(new javax.swing.tree.TreePath(treeModel.getRoot()));
  }
  public void expandAllNodes(javax.swing.tree.TreePath rootPath)
  {
    expandPath(rootPath);

    Object currParent = rootPath.getLastPathComponent();
    int numKids = treeModel.getChildCount(currParent);
    for (int i = 0; i < numKids; i++)
    {
      expandAllNodes(rootPath.pathByAddingChild(treeModel.getChild(currParent, i)));
    }
  }

  public void collapseAllNodes()
  {
    int numRows = getRowCount();//treeModel.getChildCount(treeModel.getRoot());
    for (int i = numRows; i >= 0; i--)
    {
      collapseRow(i);
    }
  }

  public java.awt.Dimension getPreferredSize()
  {
    java.awt.Dimension prefSize = super.getPreferredSize();
    return prefSize;
  }

  public java.awt.Dimension getMinimumSize()
  {
    java.awt.Dimension minSize = super.getMinimumSize();
    return minSize;
  }

  public java.awt.Dimension getMaximumSize()
  {
    java.awt.Dimension maxSize = super.getMaximumSize();
    return maxSize;
  }

  public boolean getScrollableTracksViewportWidth()
  {
    return uiMgr.getBoolean("studio/horizontal_scrolling", true) ? super.getScrollableTracksViewportWidth() : true;
  }

  public UIManager getUIMgr() { return uiMgr; }

  private UIManager uiMgr;
  private MultiLineTreeRenderer myCellRenderer;

  private javax.swing.JPopupMenu rightClicky;
  private javax.swing.JMenuItem expandChildrenMenuItem;
  private javax.swing.JMenuItem expandAllNodesMenuItem;
  private javax.swing.JMenuItem collapseAllNodesMenuItem;
  private javax.swing.JMenuItem highlightWidgetMenuItem;
  private javax.swing.JMenuItem highlightWidgetThemeMenuItem;

  private int oldTreeWidth;
}
