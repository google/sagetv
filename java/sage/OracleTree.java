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

public class OracleTree extends javax.swing.JTree implements
java.awt.event.MouseListener, java.awt.dnd.DragGestureListener,
java.awt.dnd.DragSourceListener, javax.swing.event.TreeSelectionListener,
java.awt.dnd.DropTargetListener, java.awt.event.KeyListener,
java.awt.dnd.Autoscroll, java.awt.datatransfer.ClipboardOwner
{
  public static final int DRAG_EDGE_TO_AUTOSCROLL = 25;
  public static final int MOVE_FOR_AUTOSCROLL = 30;
  public static final long DELAY_TO_START_AUTOSCROLL = 400;
  public static final long DELAY_TO_RESET_COUNTER = 500;
  public OracleTree(UIManager inUIMgr, OracleTreeModel model)
  {
    super(model);
    allEditors = new java.util.HashMap();
    uiMgr = inUIMgr;
    setEditable(true);
    setRootVisible(false);
    //setDragEnabled(true);
    setInvokesStopCellEditing(true);
    getSelectionModel().setSelectionMode(
        javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    setShowsRootHandles(true);
    setExpandsSelectedPaths(true);
    putClientProperty("JTree.lineStyle", "Angled");
    setForeground(java.awt.Color.black);
    setBackground(java.awt.Color.white);

    new OracleMouseDragGestureRecognizer(java.awt.dnd.DragSource.getDefaultDragSource(),
        this, java.awt.dnd.DnDConstants.ACTION_COPY_OR_MOVE | java.awt.dnd.DnDConstants.ACTION_LINK,
        this);

    new java.awt.dnd.DropTarget(this, java.awt.dnd.DnDConstants.ACTION_COPY_OR_MOVE |
        java.awt.dnd.DnDConstants.ACTION_LINK, this, true);

    addMouseListener(this);
    addKeyListener(this);

    myClipboard = new java.awt.datatransfer.Clipboard("StudioClipboard");

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
        if (dragPaths != null)
        {
          for (int i = 0; i < dragPaths.length; i++)
          {
            if (dragPaths[i].getLastPathComponent() == value)
            {
              super.beingDragged = true;
              break;
            }
          }
        }
        Widget myW = null;
        if (value instanceof Widget)
        {
          myW = (Widget) value;
          setIcon(uiMgr.getIcon(Widget.TYPES[myW.type()]));
          boolean isCode = myW.isType(Widget.ACTION) || myW.isType(Widget.CONDITIONAL) || myW.isType(Widget.BRANCH) ||
              myW.isType(Widget.TABLECOMPONENT) || myW.isType(Widget.TEXTINPUT) || myW.isType(Widget.ATTRIBUTE);
          if (myW.isType(Widget.MENU))
            setFont(myW.numContainers() == 0 ? (isCode?standardCodeFont:standardFont) :
              (isCode?boldCodeFont:boldFont));
          else
            setFont(myW.numContainers() <= 1 ? (isCode?standardCodeFont:standardFont) :
              (isCode?boldCodeFont:boldFont));
          if (myW.isType(Widget.BRANCH) || myW.isType(Widget.CONDITIONAL) || myW.isType(Widget.ACTION))
            setIconBackgroundColor(myW.isInEffectHierarchy() ? java.awt.Color.magenta : (myW.isInUIHierarchy() ? java.awt.Color.blue.darker() :
              (myW.isInProcessChain() ? java.awt.Color.green.darker() : java.awt.Color.yellow.darker())));
          else
            setIconBackgroundColor(null);
        }
        else if (value instanceof TreeNodeDuplicate)
        {
          myW = ((TreeNodeDuplicate) value).getSource();
          setIcon(uiMgr.getIcon(Widget.TYPES[myW.type()]));
          boolean isCode = myW.isType(Widget.ACTION) || myW.isType(Widget.CONDITIONAL) || myW.isType(Widget.BRANCH) ||
              myW.isType(Widget.TABLECOMPONENT) || myW.isType(Widget.TEXTINPUT) || myW.isType(Widget.ATTRIBUTE);
          setFont(isCode?italicCodeFont:italicFont);
          if (myW.isType(Widget.BRANCH) || myW.isType(Widget.CONDITIONAL) || myW.isType(Widget.ACTION))
            setIconBackgroundColor(myW.isInEffectHierarchy() ? java.awt.Color.magenta : (myW.isInUIHierarchy() ? java.awt.Color.blue.darker() :
              (myW.isInProcessChain() ? java.awt.Color.green.darker() : java.awt.Color.yellow.darker())));
          else
            setIconBackgroundColor(null);
        }
        else if (value instanceof java.util.Vector)
        {
          setIcon(expanded ? openIcon : closedIcon);
        }
        if (myW != null)
        {
          Breakpoint bi = uiMgr.getStudio().getBreakpointInfo(myW);
          if (bi != null)
          {
            setIconBackgroundColor2(bi.isEnabled() ? java.awt.Color.red : java.awt.Color.orange);
          }
          else
            setIconBackgroundColor2(null);
        }
        else
          setIconBackgroundColor2(null);
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        return this;
      }
    };
    setCellRenderer(myCellRenderer);
    setCellEditor(new javax.swing.tree.DefaultTreeCellEditor(this, null)
    {
      public boolean stopCellEditing()
      {
        javax.swing.tree.TreePath editPath = getEditingPath();
        if (super.stopCellEditing())
        {
          Object newVal = getCellEditorValue();
          if (editPath != null)
          {
            Object selMe = editPath.getLastPathComponent();
            Widget w = null;
            if (selMe instanceof TreeNodeDuplicate)
            {
              w = ((TreeNodeDuplicate) selMe).getSource();
            }
            else if (selMe instanceof Widget)
            {
              w = (Widget) selMe;
            }
            if (w != null)
            {
              String oldName = w.getUntranslatedName();
              if (getDisplayAttributeValues() && w.type() == Widget.ATTRIBUTE)
              {
                // This has the name and the value
                String valStr = newVal.toString();
                int idx1 = valStr.indexOf("[");
                int idx2 = valStr.lastIndexOf("]");
                if (idx1 != -1)
                {
                  String newName = valStr.substring(0, (valStr.charAt(idx1 - 1) == ' ') ? (idx1 - 1) : idx1);
                  WidgetFidget.setName(w, newName);
                  if (!oldName.equals(newName))
                    uiMgr.getStudio().pushWidgetOp(new StudioFrame.WidgetOp(w, oldName));
                  if (idx2 == -1)
                    valStr = valStr.substring(idx1 + 1);
                  else
                    valStr = valStr.substring(idx1 + 1, idx2);
                  if (valStr.length() > 0 && valStr.charAt(0) == '=')
                    valStr = valStr.substring(1);
                  String oldValue = w.getProperty(Widget.VALUE);
                  WidgetFidget.setProperty(w, Widget.VALUE, valStr);
                  if (!valStr.equals(oldValue))
                    uiMgr.getStudio().pushWidgetOp(new StudioFrame.WidgetOp(w, Widget.VALUE, oldValue));
                }
                else
                {
                  WidgetFidget.setName(w, newVal.toString());
                  if (!oldName.equals(newVal.toString()))
                    uiMgr.getStudio().pushWidgetOp(new StudioFrame.WidgetOp(w, oldName));
                }
              }
              else
              {
                WidgetFidget.setName(w, newVal.toString());
                if (!oldName.equals(newVal.toString()))
                  uiMgr.getStudio().pushWidgetOp(new StudioFrame.WidgetOp(w, oldName));
              }
            }
          }
          return true;
        }
        else
          return false;
      }

      protected void prepareForEditing()
      {
        super.prepareForEditing();
        if (editingComponent instanceof javax.swing.JTextField)
          ((javax.swing.JTextField) editingComponent).selectAll();
      }
    });
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
    deleteMenuItem= new javax.swing.JMenuItem("Delete");
    deleteMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
        java.awt.event.KeyEvent.VK_DELETE, 0));
    deleteMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        deleteSelectedWidgets();
      }});
    breakFromParentMenuItem= new javax.swing.JMenuItem("Break From Parent");
    breakFromParentMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
        java.awt.event.KeyEvent.VK_B, java.awt.event.ActionEvent.CTRL_MASK));
    breakFromParentMenuItem.setMnemonic(java.awt.event.KeyEvent.VK_B);
    breakFromParentMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
        if (selPaths != null && selPaths.length > 0)
        {
          final int rowSelNum = getRowForPath(selPaths[0]);
          uiMgr.getStudio().pushWidgetOps(breakPathsFromParents(selPaths));
          ((OracleTreeModel) getModel()).refreshTree();
          java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
            setSelectionRow(rowSelNum); } });
        }
      }});
    moveDownMenuItem = new javax.swing.JMenuItem("Move Down");
    moveDownMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
        java.awt.event.KeyEvent.VK_D, java.awt.event.ActionEvent.CTRL_MASK));
    moveDownMenuItem.setMnemonic(java.awt.event.KeyEvent.VK_D);
    moveDownMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        moveSelectedPaths(1);
      }});
    moveUpMenuItem= new javax.swing.JMenuItem("Move Up");
    moveUpMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
        java.awt.event.KeyEvent.VK_U, java.awt.event.ActionEvent.CTRL_MASK));
    moveUpMenuItem.setMnemonic(java.awt.event.KeyEvent.VK_U);
    moveUpMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        moveSelectedPaths(-1);
      }});
    renameMenuItem = new javax.swing.JMenuItem("Rename");
    renameMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0));
    renameMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        javax.swing.tree.TreePath[] selPath = getSelectionPaths();
        if (selPath != null && selPath.length > 0)
          startEditingAtPath(selPath[0]);
      }});
    propertiesMenuItem = new javax.swing.JMenuItem("Properties");
    propertiesMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L,
        java.awt.event.ActionEvent.CTRL_MASK));
    propertiesMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        showEditorForSelectedPath();
      }});
    refreshMenuMenuItem = new javax.swing.JMenuItem("Refresh Menu");
    refreshMenuMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        refreshMenu();
      }});
    refreshMenuMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F6, 0));
    launchMenuMenuItem = new javax.swing.JMenuItem("Launch Menu");
    launchMenuMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        launchSelectedMenu();
      }});
    launchMenuMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0));
    showReferencesMenuItem = new javax.swing.JMenuItem("Highlight References");
    showReferencesMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        highlightReferences();
      }});
    setAsPrimaryReferenceMenuItem = new javax.swing.JMenuItem("Set as Primary Reference");
    setAsPrimaryReferenceMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R,
        java.awt.event.ActionEvent.CTRL_MASK));
    setAsPrimaryReferenceMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        setAsPrimaryReference();
      }});
    pasteFormattingMenuItem = new javax.swing.JMenuItem("Paste Properties");
    pasteFormattingMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        pasteFormatting();
      }});

    evaluateMenuItem = new javax.swing.JMenuItem("Evaluate Widget");
    evaluateMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        evaluateSelectedWidget();
      }});
    evaluateChainMenuItem = new javax.swing.JMenuItem("Execute Widget Chain");
    evaluateChainMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        evaluateSelectedWidgetChain();
      }});

    newChildrenSubmenu = new javax.swing.JMenu("New Child");
    newChildrenSubmenu.setMnemonic(java.awt.event.KeyEvent.VK_N);
    newWidgetMenuItems = new NewWidgetMenuItem[Widget.TYPES.length];
    for (byte i = 0; i <= Widget.MAX_TYPE_NUM; i++)
    {
      newWidgetMenuItems[i] = new NewWidgetMenuItem(i);
      newWidgetMenuItems[i].setMnemonic(Widget.MNEMONICS[i]);
      newChildrenSubmenu.add(newWidgetMenuItems[i]);
    }
    addBreakpointMenuItem = new javax.swing.JMenuItem("Add Breakpoint");
    addBreakpointMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        addBreakpointsAtSelectedWidgets();
      }
    });
    removeBreakpointMenuItem = new javax.swing.JMenuItem("Remove Breakpoint");
    removeBreakpointMenuItem.addActionListener(new java.awt.event.ActionListener(){
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        removeBreakpointsAtSelectedWidgets();
      }
    });

    rightClicky = new javax.swing.JPopupMenu("Oracle Righty");
    rightClicky.add(newChildrenSubmenu);
    rightClicky.addSeparator();
    if (Sage.getBoolean("studio/show_expand_all_nodes", false))
      rightClicky.add(expandAllNodesMenuItem);
    rightClicky.add(collapseAllNodesMenuItem);
    rightClicky.add(expandChildrenMenuItem);
    rightClicky.add(refreshMenuMenuItem);
    rightClicky.addSeparator();
    rightClicky.add(breakFromParentMenuItem);
    rightClicky.add(moveUpMenuItem);
    rightClicky.add(moveDownMenuItem);
    rightClicky.add(showReferencesMenuItem);
    rightClicky.add(setAsPrimaryReferenceMenuItem);
    rightClicky.addSeparator();
    rightClicky.add(addBreakpointMenuItem);
    rightClicky.add(removeBreakpointMenuItem);
    rightClicky.addSeparator();
    rightClicky.add(pasteFormattingMenuItem);
    rightClicky.addSeparator();
    rightClicky.add(launchMenuMenuItem);
    rightClicky.add(evaluateMenuItem);
    rightClicky.add(evaluateChainMenuItem);
    rightClicky.add(renameMenuItem);
    rightClicky.add(deleteMenuItem);
    rightClicky.addSeparator();
    rightClicky.add(propertiesMenuItem);
  }

  private class NewWidgetMenuItem extends javax.swing.JMenuItem implements java.awt.event.ActionListener
  {
    public NewWidgetMenuItem(byte inWidgType)
    {
      super(Widget.TYPES[inWidgType]);
      widgType = inWidgType;
      addActionListener(this);
    }
    public void actionPerformed(java.awt.event.ActionEvent evt)
    {
      javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
      if (selPaths != null && selPaths.length == 1)
      {
        Object childObj = selPaths[0].getLastPathComponent();
        Widget widgy = null;
        if (childObj instanceof Widget)
          widgy = (Widget) childObj;
        else if (childObj instanceof TreeNodeDuplicate)
          widgy = ((TreeNodeDuplicate) childObj).getSource();
        if (widgy != null && WidgetMeta.isRelationshipAllowed(widgy.type(), widgType))
        {
          java.util.Vector undoList = new java.util.Vector();
          Widget newWidg = uiMgr.getModuleGroup().addWidget(widgType);
          undoList.add(new StudioFrame.WidgetOp(true, newWidg));
          WidgetFidget.contain(widgy, newWidg);
          undoList.add(new StudioFrame.WidgetOp(widgy, newWidg));
          uiMgr.getStudio().pushWidgetOps(undoList);
          expandPath(selPaths[0]);
          final javax.swing.tree.TreePath newPath = selPaths[0].pathByAddingChild(newWidg);
          setSelectionPath(newPath);
          ((OracleTreeModel) OracleTree.this.getModel()).refreshTree();
          java.awt.EventQueue.invokeLater(new Runnable()
          {
            public void run()
            {
              startEditingAtPath(newPath);
            }
          });
        }
      }
    }
    byte widgType;
  }

  public String convertValueToText(Object value, boolean selected,
      boolean expanded, boolean leaf, int row, boolean hasFocus)
  {
    return convertValueToText(value, selected, expanded, leaf, row, hasFocus, false);
  }
  public String convertValueToText(Object value, boolean selected,
      boolean expanded, boolean leaf, int row, boolean hasFocus, boolean forRendering)
  {
    String retVal = null;
    Widget widgy = null;
    if (value instanceof TreeNodeDuplicate)
    {
      widgy = ((TreeNodeDuplicate) value).getSource();
    }
    else if (value instanceof Widget)
    {
      widgy = (Widget) value;
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
    if (forRendering && displayUIDs && widgy != null)
      retVal += " [" + widgy.symbol() + "]";
    if (displayAttValues && widgy != null && widgy.type() == Widget.ATTRIBUTE)
      retVal += " [=" + widgy.getProperty(Widget.VALUE) + "]";
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
    boolean showEvaluate = false;
    boolean showEvaluateChain = false;
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
    if (showSingleOps)
    {
      Object lastElem = selPaths[0].getLastPathComponent();
      if (lastElem instanceof Widget)
        singleWidg = (Widget) lastElem;
      else if (lastElem instanceof TreeNodeDuplicate)
        singleWidg = ((TreeNodeDuplicate) lastElem).getSource();

      if (singleWidg != null && singleWidg.isType(Widget.BRANCH))
        showEvaluate = true;
      else if (singleWidg != null && (singleWidg.isType(Widget.ACTION) || singleWidg.isType(Widget.CONDITIONAL) ||
          singleWidg.isType(Widget.MENU) || singleWidg.isType(Widget.OPTIONSMENU)))
        showEvaluate = showEvaluateChain = true;
    }
    expandChildrenMenuItem.setEnabled(showMultiOps);
    deleteMenuItem.setEnabled(showMultiOps);
    boolean showBreaksParent = false;
    boolean showMoves = false;
    boolean showProps = showSingleOps || showMultiOps;
    if (showMultiOps)
    {
      byte commonWidgType = -1;
      for (int i = 0; i < selPaths.length; i++)
      {
        if (selPaths[i].getPathCount() > 2)
        {
          showBreaksParent = true;
          if (selPaths.length == 1)
            showMoves = true;
        }
        Object lastElem = selPaths[i].getLastPathComponent();
        Widget currLastWidg = null;
        if (lastElem instanceof Widget)
          currLastWidg = (Widget) lastElem;
        else if (lastElem instanceof TreeNodeDuplicate)
          currLastWidg = ((TreeNodeDuplicate) lastElem).getSource();
        if (currLastWidg == null)
          showProps = false;
        else if (commonWidgType == -1)
          commonWidgType = currLastWidg.type();
        else if (!currLastWidg.isType(commonWidgType))
          showProps = false;
      }
    }
    breakFromParentMenuItem.setEnabled(showBreaksParent);
    showReferencesMenuItem.setEnabled(singleWidg != null &&
        (singleWidg.numContainers() > 1 || singleWidg.isType(Widget.MENU)));
    setAsPrimaryReferenceMenuItem.setEnabled(singleWidg != null &&
        singleWidg.numContainers() > 1 && !singleWidg.isType(Widget.MENU));
    moveUpMenuItem.setEnabled(showMoves);
    moveDownMenuItem.setEnabled(showMoves);
    renameMenuItem.setEnabled(showSingleOps);
    launchMenuMenuItem.setEnabled(singleWidg != null && singleWidg.isType(Widget.MENU));
    propertiesMenuItem.setEnabled(showProps);
    pasteFormattingMenuItem.setEnabled((showSingleOps || showMultiOps) && myClipboard.getContents(null) != null);
    newChildrenSubmenu.setEnabled(singleWidg != null);
    evaluateMenuItem.setEnabled(showEvaluate);
    evaluateChainMenuItem.setEnabled(showEvaluateChain);
    int bpState = getSelectedWidgetsBreakpointState();
    addBreakpointMenuItem.setEnabled(bpState == -1);
    removeBreakpointMenuItem.setEnabled(bpState == 1);
    if (singleWidg != null)
    {
      boolean enabledAny = false;
      for (int i = 0; i < newWidgetMenuItems.length; i++)
      {
        boolean currOK = WidgetMeta.isRelationshipAllowed(singleWidg.type(), (byte)i);
        newWidgetMenuItems[i].setEnabled(currOK);
        enabledAny |= currOK;
      }
      if (!enabledAny)
        newChildrenSubmenu.setEnabled(false);
    }
    MySwingUtils.safeShowPopupMenu(rightClicky, this, evtx, evty);
  }

  public void mouseClicked(java.awt.event.MouseEvent evt)
  {
    if (javax.swing.SwingUtilities.isLeftMouseButton(evt) &&
        (evt.getClickCount() == 2))
    {
      javax.swing.tree.TreePath clickPath = getPathForLocation(evt.getX(), evt.getY());
      if ((clickPath != null) && (clickPath.getLastPathComponent() instanceof TreeNodeDuplicate))
      {
        Widget widgy =
            ((TreeNodeDuplicate) clickPath.getLastPathComponent()).getSource();
        javax.swing.tree.TreePath pathToMaster =
            ((OracleTreeModel) getModel()).getPathToNode(widgy);
        if (pathToMaster != null)
        {
          setSelectionPath(pathToMaster);
          scrollPathToVisible(pathToMaster);
        }
      }
    }
  }

  Widget[] getSelectedWidgets()
  {
    javax.swing.tree.TreePath[] selectedPaths = getSelectionPaths();
    if (selectedPaths == null)
    {
      return new Widget[0];
    }
    java.util.Set selwidgs = new java.util.HashSet(selectedPaths.length);
    for (int i = 0; i < selectedPaths.length; i++)
    {
      Object selMe = selectedPaths[i].getLastPathComponent();
      if (selMe instanceof TreeNodeDuplicate)
      {
        selwidgs.add(((TreeNodeDuplicate) selMe).getSource());
      }
      else if (selMe instanceof Widget)
      {
        selwidgs.add(selMe);
      }
    }
    return (Widget[]) selwidgs.toArray(new Widget[0]);
  }

  // DRAG stuff
  public void dragGestureRecognized(java.awt.dnd.DragGestureEvent evt)
  {
    // We support all actions
    java.awt.Point dragPoint = evt.getDragOrigin();
    javax.swing.tree.TreePath dragTreePath = getPathForLocation(dragPoint.x, dragPoint.y);
    if (dragTreePath == null)
    {
      return;
    }
    if (isEditing()) return;

    javax.swing.tree.TreePath[] selectedPaths = getSelectionPaths();
    if ((selectedPaths == null) || (selectedPaths.length == 0))
    {
      return;
    }
    int numValidSels = 0;
    java.util.Set selwidgs = new java.util.HashSet(selectedPaths.length);
    java.util.ArrayList dragObjs = new java.util.ArrayList();
    java.util.Set dragObjsSet = new java.util.HashSet();
    for (int i = 0; i < selectedPaths.length; i++)
    {
      javax.swing.tree.TreePath selPath = selectedPaths[i];
      if (selPath != null)
      {
        Object selMe = selPath.getLastPathComponent();
        if (selMe instanceof TreeNodeDuplicate)
        {
          selwidgs.add(((TreeNodeDuplicate) selMe).getSource());
          selectedPaths[numValidSels] = selectedPaths[i];
          numValidSels++;

          if (dragObjsSet.add(selMe))
            dragObjs.add(selMe);
        }
        else if (selMe instanceof Widget)
        {
          selwidgs.add(selMe);
          selectedPaths[numValidSels] = selectedPaths[i];
          numValidSels++;

          if (dragObjsSet.add(selMe))
            dragObjs.add(selMe);
        }
      }
    }
    if (numValidSels == selectedPaths.length)
    {
      dragPaths = selectedPaths;
    }
    else
    {
      dragPaths = new javax.swing.tree.TreePath[numValidSels];
      System.arraycopy(selectedPaths, 0, dragPaths, 0, numValidSels);
    }
    dragwidgs = (Widget[]) selwidgs.toArray(new Widget[0]);

    if (dragwidgs.length == 1)
    {
      // Single widget drag
      java.awt.datatransfer.Transferable dragObj = new WidgetTransfer(dragwidgs[0], uiMgr);
      evt.startDrag(uiMgr.getCursor(Widget.TYPES[dragwidgs[0].type()] + "NoDropCursor"), dragObj, this);
    }
    else
    {
      // Multiple widget drag
      java.awt.datatransfer.Transferable dragObj = new WidgetTransfer(dragObjs.toArray(), uiMgr);
      evt.startDrag(uiMgr.getCursor(Widget.TYPES[dragwidgs[0].type()] + "NoDropCursor"), dragObj, this);
    }
    clearSelection();
  }

  public void dragDropEnd(java.awt.dnd.DragSourceDropEvent evt)
  {
    // Clear all of the stuff we've highlighted
    if (dragPaths != null)
    {
      javax.swing.plaf.basic.BasicTreeUI myUI = (javax.swing.plaf.basic.BasicTreeUI) getUI();
      for (int i = 0; i < dragPaths.length; i++)
      {
        javax.swing.tree.TreePath thePath = dragPaths[i];
        if (thePath != null)
        {
          java.awt.Rectangle pb = myUI.getPathBounds(this, thePath);
          if (pb != null)
            repaint(pb);
        }
      }
    }
    dragwidgs = null;
    dragPaths = null;
  }

  public void dragEnter(java.awt.dnd.DragSourceDragEvent evt)
  {
    evt.getDragSourceContext().setCursor(null); // This gets rid of flickering
    evt.getDragSourceContext().setCursor(uiMgr.getCursor(Widget.TYPES[dragwidgs[0].type()] +
        (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_COPY ? "Copy" :
          (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_LINK ? "Link" : "")) + "Cursor"));
  }

  public void dragExit(java.awt.dnd.DragSourceEvent evt)
  {
    evt.getDragSourceContext().setCursor(null); // This gets rid of flickering
    evt.getDragSourceContext().setCursor(uiMgr.getCursor(Widget.TYPES[dragwidgs[0].type()] + "NoDropCursor"));
  }

  public void dragOver(java.awt.dnd.DragSourceDragEvent evt)
  {
    evt.getDragSourceContext().setCursor(null); // This gets rid of flickering
    evt.getDragSourceContext().setCursor(uiMgr.getCursor(Widget.TYPES[dragwidgs[0].type()] +
        (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_COPY ? "Copy" :
          (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_LINK ? "Link" : "")) + "Cursor"));
  }

  public void dropActionChanged(java.awt.dnd.DragSourceDragEvent evt)
  {
    // We'll get a dragOver if it's actually on something valid, we can't know that here so just
    // don't do anything.
  }
  // DRAG stuff end

  // DROP stuff
  public void dragEnter(java.awt.dnd.DropTargetDragEvent evt)
  {
    // We can treat this just like a drag over situation.
    dragOver(evt);
  }

  public void dragExit(java.awt.dnd.DropTargetEvent evt)
  {
    clearSelection();
  }

  public void dragOver(java.awt.dnd.DropTargetDragEvent evt)
  {
    if ((isWidgetKeyDrag(evt) || isMultipleWidgetKeyDrag(evt) ||
        isWidgetTypeDrag(evt)))
    {
      java.awt.Point dragPoint = evt.getLocation();
      javax.swing.tree.TreePath dragPath = getPathForLocation(dragPoint.x, dragPoint.y);
      if (dragPath == null)
      {
        if (isWidgetTypeDrag(evt))
        {
          evt.acceptDrag(evt.getDropAction());
        }
        else
        {
          evt.rejectDrag();
        }
        clearSelection();
      }
      else
      {
        Object boy = dragPath.getLastPathComponent();
        Widget overwidg = null;
        if (boy instanceof TreeNodeDuplicate)
        {
          overwidg = ((TreeNodeDuplicate) boy).getSource();
        }
        else if (boy instanceof Widget)
        {
          overwidg = (Widget) boy;
        }

        boolean goodDrag = true;
        // We only allow copy operations if we're doing a drag & drop between different UIManagers
        if (!isWidgetDragSameUI(evt.getCurrentDataFlavors()) && evt.getDropAction() != java.awt.dnd.DnDConstants.ACTION_COPY)
          goodDrag = false;
        if (overwidg != null)
        {
          if (isWidgetTypeDrag(evt))
          {
            if (!WidgetMeta.isRelationshipAllowed(overwidg.type(), getWidgetTypeForDrag(evt)))
              goodDrag = false;
          }
          else
          {
            Widget[] testwidgs;
            if (isWidgetKeyDrag(evt))
            {
              testwidgs = new Widget[] { getWidgetForDrag(evt) };
            }
            else
            {
              testwidgs = getWidgetsForMultiDrag(evt);
            }
            if (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_COPY)
            {
              // For copy, we don't want to try to determine the whole tree structure here....that could get really nasty.
              // So there are illegal cases that could occur here.
              goodDrag = false;
              for (int i = 0; i < testwidgs.length; i++)
              {
                if (overwidg.willContain(testwidgs[i]))
                {
                  goodDrag = true;
                  break;
                }
              }
            }
            else
            {
              for (int i = 0; (i < testwidgs.length) && goodDrag; i++)
              {
                if (testwidgs[i] == overwidg)
                {
                  goodDrag = false;
                }
                // only singles can be verified correctly since we transfer a tree structure in the multi-case
                // But we only transfer the structure if it's a copy operation
                else if (!overwidg.willContain(testwidgs[i]) && (testwidgs.length == 1 || evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_MOVE ||
                    evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_LINK))
                  goodDrag = false;
              }
            }
          }
        }
        else
        {
          goodDrag = false;
        }

        if (goodDrag)
        {
          evt.acceptDrag(evt.getDropAction());
          setSelectionPath(dragPath);
        }
        else
        {
          evt.rejectDrag();
          clearSelection();
        }
      }
    }
    else
    {
      evt.rejectDrag();
      clearSelection();
    }
  }

  public void drop(java.awt.dnd.DropTargetDropEvent evt)
  {
    // Check for the correct flavor.
    if (!evt.isLocalTransfer() ||
        (!isWidgetKeyDrag(evt) && !isMultipleWidgetKeyDrag(evt) &&
            !isWidgetTypeDrag(evt)))
    {
      evt.rejectDrop();
      return;
    }

    java.awt.Point dropPoint = evt.getLocation();
    javax.swing.tree.TreePath dropPath = getPathForLocation(dropPoint.x, dropPoint.y);
    if (dropPath == null)
    {
      // This will be a type drop, so just grab the data
      if (isWidgetTypeDrag(evt))
      {
        evt.acceptDrop(evt.getDropAction());
        Widget newWidg = (Widget) WidgetTransfer.getTransferData(uiMgr, evt.getTransferable());
        uiMgr.getStudio().pushWidgetOp(new StudioFrame.WidgetOp(true, newWidg));
        evt.dropComplete(true);
        ((OracleTreeModel) getModel()).refreshTree();
      }
      else
      {
        evt.rejectDrop();
      }
      return;
    }

    Object boy = dropPath.getLastPathComponent();
    final Widget overwidg;
    if (boy instanceof TreeNodeDuplicate)
    {
      overwidg = ((TreeNodeDuplicate) boy).getSource();
    }
    else if (boy instanceof Widget)
    {
      overwidg = (Widget) boy;
    }
    else
    {
      evt.rejectDrop();
      return;
    }

    evt.acceptDrop(evt.getDropAction());

    Widget[] dropwidgs;
    Object[] dropObjs = null;
    java.util.Vector undoList = new java.util.Vector();
    if (isWidgetKeyDrag(evt))
    {
      dropObjs = dropwidgs = new Widget[] { (Widget)
          WidgetTransfer.getTransferData(uiMgr, evt.getTransferable()) };
    }
    else if (isWidgetTypeDrag(evt))
    {
      dropObjs = dropwidgs = new Widget[] { (Widget)
          WidgetTransfer.getTransferData(uiMgr, evt.getTransferable()) };
      undoList.add(new StudioFrame.WidgetOp(true, dropwidgs[0]));
    }
    else
    {
      dropObjs = (Object[]) WidgetTransfer.getTransferData(uiMgr, evt.getTransferable());
      dropwidgs = new Widget[dropObjs.length];
      for (int i = 0; i < dropObjs.length; i++)
      {
        if (dropObjs[i] instanceof TreeNodeDuplicate)
          dropwidgs[i] = ((TreeNodeDuplicate) dropObjs[i]).getSource();
        else
          dropwidgs[i] = (Widget) dropObjs[i];
      }
    }
    if (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_MOVE)
    {
      for (int i = 0; i < dropwidgs.length; i++)
      {
        // test for drop allowed
        if (overwidg != dropwidgs[i] && overwidg.willContain(dropwidgs[i]))
        {
          WidgetFidget.contain(overwidg, dropwidgs[i]);
          undoList.add(new StudioFrame.WidgetOp(overwidg, dropwidgs[i]));
        }
      }
    }
    else if (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_LINK)
    {
      // This is effectively a 2-part operation. The first is when you break the link from your
      // parent. The second is when you create the new link. The way we determine
      // NOTE: When we restrict where you can drop things we should check that they're all clear
      // before starting the breaks
      undoList.addAll(breakPathsFromParents(dragPaths));
      for (int i = 0; i < dropwidgs.length; i++)
      {
        // test for drop allowed
        if (overwidg != dropwidgs[i] && overwidg.willContain(dropwidgs[i]))
        {
          WidgetFidget.contain(overwidg, dropwidgs[i]);
          undoList.add(new StudioFrame.WidgetOp(overwidg, dropwidgs[i]));
        }
      }
    }
    else // COPY
    {
      // We also rebuild any inter-widget relationships as part of this. Any Widgets
      // in the transfer that do not have parents in the transfer are contained
      // in the drop widget.
      java.util.Map widgCloneMap = new java.util.HashMap();
      java.util.Set alreadyKloned = new java.util.HashSet();
      for (int i = 0; i < dropObjs.length; i++)
      {
        // test for drop allowed

        if (dropObjs[i] instanceof TreeNodeDuplicate)
        {
          if (!alreadyKloned.contains(dropwidgs[i]))
            widgCloneMap.put(dropwidgs[i], dropwidgs[i]);
        }
        else
        {
          Widget kloneWidg = uiMgr.getModuleGroup().klone(dropwidgs[i]);
          widgCloneMap.put(dropwidgs[i], kloneWidg);
          alreadyKloned.add(dropwidgs[i]);
          undoList.add(new StudioFrame.WidgetOp(true, kloneWidg));
        }
      }
      for (int i = 0; i < dropwidgs.length; i++)
      {
        // Check if there's any parents in the new set of widgets for this one
        Widget[] currParents = dropwidgs[i].containers();
        boolean isRoot = true;
        if (new java.util.HashSet(widgCloneMap.keySet()).removeAll(java.util.Arrays.asList(currParents)))
        {
          // there's parents in there, we're not a root
          isRoot = false;
        }
        Widget[] currConts = dropwidgs[i].contents();
        for (int j = 0; j < currConts.length; j++)
        {
          if (widgCloneMap.containsKey(currConts[j]))
          {
            Widget parentWidg = (Widget) widgCloneMap.get(dropwidgs[i]);
            Widget childWidg = (Widget) widgCloneMap.get(currConts[j]);
            WidgetFidget.contain(parentWidg, childWidg);
            undoList.add(new StudioFrame.WidgetOp(parentWidg, childWidg));
          }
        }
        if (isRoot && overwidg.willContain((Widget) widgCloneMap.get(dropwidgs[i])))
        {
          WidgetFidget.contain(overwidg, (Widget) widgCloneMap.get(dropwidgs[i]));
          undoList.add(new StudioFrame.WidgetOp(overwidg, (Widget) widgCloneMap.get(dropwidgs[i])));
        }
      }
    }
    uiMgr.getStudio().pushWidgetOps(undoList);
    evt.dropComplete(true);
    expandPath(dropPath);

    ((OracleTreeModel) getModel()).refreshTree();
  }

  public void dropActionChanged(java.awt.dnd.DropTargetDragEvent evt)
  {
    evt.acceptDrag(evt.getDropAction());
  }
  // DROP stuff end

  public void setSelectionPathWithoutHighlight(javax.swing.tree.TreePath path)
  {
    forcingSelectionPath = true;
    super.setSelectionPath(path);
    forcingSelectionPath = false;
  }

  public void valueChanged(javax.swing.event.TreeSelectionEvent evt)
  {
    if (forcingSelectionPath) return;
    javax.swing.tree.TreePath selPath = evt.getPath();
    if (selPath != null)
    {
      Object lastGuy = selPath.getLastPathComponent();
      Widget dawidg = null;
      if (lastGuy instanceof TreeNodeDuplicate)
      {
        dawidg = ((TreeNodeDuplicate) lastGuy).getSource();
      }
      else if (lastGuy instanceof Widget)
      {
        dawidg = (Widget) lastGuy;
      }
      if (dawidg != null)
      {
        Widget[] ws = uiMgr.getModuleGroup().getWidgets();
        // 601 Widget[] ws = uiMgr.moduleGroup.getWidgets();
        java.util.ArrayList paintyWidgs = new java.util.ArrayList();
        for (int i = 0; i < ws.length; i++)
        {
          // 601 if (ws[i].tempHighlight != 0)
          if (ws[i].tempHighlight())
          {
            paintyWidgs.add(ws[i]);
            // 601 ws[i].tempHighlight = 0;
            ws[i].tempHighlight(false);
          }
        }
        paintyWidgs.add(dawidg);
        // 601 dawidg.tempHighlight = 1;
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

  public void keyPressed(java.awt.event.KeyEvent evt)
  {
  }

  public void keyReleased(java.awt.event.KeyEvent evt)
  {
    if ((evt.getKeyCode() == evt.VK_E) && evt.isControlDown() && !isEditing())
    {
      if (evt.isShiftDown())
      {
        collapseAllNodes();
      }
      else
      {
        javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
        for (int i = 0; i < selPaths.length; i++)
          expandAllNodes(selPaths[i]);
      }
    }
    else if (evt.getKeyCode() == evt.VK_L && evt.isControlDown() && !isEditing())
      showEditorForSelectedPath();
    else if (evt.getKeyCode() == evt.VK_F5 && !evt.isControlDown() && !evt.isShiftDown() && !evt.isAltDown())
      launchSelectedMenu();
    else if (evt.getKeyCode() == evt.VK_F2 && !isEditing())
    {
      javax.swing.tree.TreePath[] selPath = getSelectionPaths();
      if (selPath != null && selPath.length > 0)
        startEditingAtPath(selPath[0]);
    }
    else if (evt.getKeyCode() == evt.VK_F6 && !evt.isControlDown() && !evt.isShiftDown() && !evt.isAltDown())
      refreshMenu();
    //		else if (evt.getKeyCode() == evt.VK_DELETE)
    //			deleteSelectedWidgets();
    else if ((evt.getKeyCode() == evt.VK_B) && evt.isControlDown() && !isEditing())
    {
      javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
      if (selPaths != null && selPaths.length > 0)
      {
        final int rowSelNum = getRowForPath(selPaths[0]);
        uiMgr.getStudio().pushWidgetOps(breakPathsFromParents(selPaths));
        ((OracleTreeModel) getModel()).refreshTree();
        java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
          setSelectionRow(rowSelNum); } });
      }
    }
    else if ((evt.getKeyCode() == evt.VK_D) && evt.isControlDown())
      moveSelectedPaths(1);
    else if ((evt.getKeyCode() == evt.VK_U) && evt.isControlDown())
      moveSelectedPaths(-1);
    else if (evt.getKeyCode() == evt.VK_F3 && !evt.isShiftDown())
      findNext();
    else if (evt.getKeyCode() == evt.VK_F3 && evt.isShiftDown())
      findPrevious();
    else if (evt.getKeyCode() == evt.VK_R && evt.isControlDown())
      setAsPrimaryReference();
    else if (evt.getKeyCode() == evt.VK_ESCAPE)
    {
      myClipboard.setContents(null, this);
      dragDropEnd(null);
    }
    else if (evt.getKeyCode() == evt.VK_P && evt.isControlDown())
    {
      javax.swing.tree.TreePath selPath = getSelectionPath();
      if (selPath != null)
      {
        java.awt.Rectangle pb = getPathBounds(selPath);
        showRightClickMenu(pb.x + 2, pb.y + 2);
      }
    }
    else if ((evt.getKeyCode() == evt.VK_C || evt.getKeyCode() == evt.VK_X) && evt.getModifiers() == evt.CTRL_MASK)
    {
      if (isEditing()) return;

      if (evt.VK_C == evt.getKeyCode())
        copySelection();
      else
        cutSelection();
    }
    else if (evt.getKeyCode() == evt.VK_V && evt.isControlDown())
    {
      pasteToSelection(evt.isShiftDown());
    }
  }

  protected void cutSelection()
  {
    copySelection();
    lastClipboardCopy = false;
  }

  protected void pasteToSelection(boolean asRef)
  {
    // Check for the correct flavor.
    javax.swing.tree.TreePath pastePath = getSelectionPath();
    if (pastePath == null) return;
    Object boy = pastePath.getLastPathComponent();
    Widget overwidg;
    if (boy instanceof TreeNodeDuplicate)
    {
      overwidg = ((TreeNodeDuplicate) boy).getSource();
    }
    else if (boy instanceof Widget)
    {
      overwidg = (Widget) boy;
    }
    else
    {
      return;
    }

    java.awt.datatransfer.Transferable transData = myClipboard.getContents(null);
    if (transData == null) return;
    if (!isWidgetKeyDrag(transData.getTransferDataFlavors()) &&
        !isMultipleWidgetKeyDrag(transData.getTransferDataFlavors()) &&
        !isWidgetTypeDrag(transData.getTransferDataFlavors()))
    {
      return;
    }

    Widget[] dropwidgs;
    Object[] dropObjs = null;
    java.util.Vector undoList = new java.util.Vector();
    if (isWidgetKeyDrag(transData.getTransferDataFlavors()))
    {
      dropObjs = dropwidgs = new Widget[] { (Widget)
          WidgetTransfer.getTransferData(uiMgr, transData) };
    }
    else if (isWidgetTypeDrag(transData.getTransferDataFlavors()))
    {
      dropObjs = dropwidgs = new Widget[] { (Widget)
          WidgetTransfer.getTransferData(uiMgr, transData) };
      undoList.add(new StudioFrame.WidgetOp(true, dropwidgs[0]));
    }
    else
    {
      dropObjs = (Object[]) WidgetTransfer.getTransferData(uiMgr, transData);
      dropwidgs = new Widget[dropObjs.length];
      for (int i = 0; i < dropObjs.length; i++)
      {
        if (dropObjs[i] instanceof TreeNodeDuplicate)
          dropwidgs[i] = ((TreeNodeDuplicate) dropObjs[i]).getSource();
        else
          dropwidgs[i] = (Widget) dropObjs[i];
      }
    }
    if (asRef && lastClipboardCopy)
    {
      for (int i = 0; i < dropwidgs.length; i++)
      {
        // test for drop allowed
        if (overwidg != dropwidgs[i] && overwidg.willContain(dropwidgs[i]))
        {
          WidgetFidget.contain(overwidg, dropwidgs[i]);
          undoList.add(new StudioFrame.WidgetOp(overwidg, dropwidgs[i]));
        }
      }
    }
    else if (!lastClipboardCopy)
    {
      // This is effectively a 2-part operation. The first is when you break the link from your
      // parent. The second is when you create the new link. The way we determine
      // NOTE: When we restrict where you can drop things we should check that they're all clear
      // before starting the breaks
      undoList.addAll(breakPathsFromParents(dragPaths));
      for (int i = 0; i < dropwidgs.length; i++)
      {
        // test for drop allowed
        if (overwidg != dropwidgs[i] && overwidg.willContain(dropwidgs[i]))
        {
          WidgetFidget.contain(overwidg, dropwidgs[i]);
          undoList.add(new StudioFrame.WidgetOp(overwidg, dropwidgs[i]));
        }
      }
    }
    else // COPY
    {
      // We also rebuild any inter-widget relationships as part of this. Any Widgets
      // in the transfer that do not have parents in the transfer are contained
      // in the drop widget.
      java.util.Map widgCloneMap = new java.util.HashMap();
      java.util.Set alreadyKloned = new java.util.HashSet();
      for (int i = 0; i < dropObjs.length; i++)
      {
        // test for drop allowed

        if (dropObjs[i] instanceof TreeNodeDuplicate)
        {
          if (!alreadyKloned.contains(dropwidgs[i]))
            widgCloneMap.put(dropwidgs[i], dropwidgs[i]);
        }
        else
        {
          Widget kloneWidg = uiMgr.getModuleGroup().klone(dropwidgs[i]);
          widgCloneMap.put(dropwidgs[i], kloneWidg);
          alreadyKloned.add(dropwidgs[i]);
          undoList.add(new StudioFrame.WidgetOp(true, kloneWidg));
        }
      }
      for (int i = 0; i < dropwidgs.length; i++)
      {
        // Check if there's any parents in the new set of widgets for this one
        Widget[] currParents = dropwidgs[i].containers();
        boolean isRoot = true;
        if (new java.util.HashSet(widgCloneMap.keySet()).removeAll(java.util.Arrays.asList(currParents)))
        {
          // there's parents in there, we're not a root
          isRoot = false;
        }
        Widget[] currConts = dropwidgs[i].contents();
        for (int j = 0; j < currConts.length; j++)
        {
          if (widgCloneMap.containsKey(currConts[j]))
          {
            Widget parentWidg = (Widget) widgCloneMap.get(dropwidgs[i]);
            Widget childWidg = (Widget) widgCloneMap.get(currConts[j]);
            if (parentWidg.willContain(childWidg))
            {
              WidgetFidget.contain(parentWidg, childWidg);
              undoList.add(new StudioFrame.WidgetOp(parentWidg, childWidg));
            }
          }
        }
        if (isRoot && overwidg.willContain((Widget) widgCloneMap.get(dropwidgs[i])))
        {
          WidgetFidget.contain(overwidg, (Widget) widgCloneMap.get(dropwidgs[i]));
          undoList.add(new StudioFrame.WidgetOp(overwidg, (Widget) widgCloneMap.get(dropwidgs[i])));
        }
      }
    }
    uiMgr.getStudio().pushWidgetOps(undoList);
    expandPath(pastePath);

    dragDropEnd(null);
    ((OracleTreeModel) getModel()).refreshTree();
  }

  protected void copySelection()
  {
    lastClipboardCopy = true;

    javax.swing.tree.TreePath[] selectedPaths = getSelectionPaths();
    if ((selectedPaths == null) || (selectedPaths.length == 0))
    {
      return;
    }
    int numValidPaths = 0;
    java.util.Set selwidgs = new java.util.HashSet(selectedPaths.length);
    java.util.Set dragObjsSet = new java.util.HashSet();
    java.util.ArrayList dragObjs = new java.util.ArrayList();
    for (int i = 0; i < selectedPaths.length; i++)
    {
      javax.swing.tree.TreePath selPath = selectedPaths[i];
      if (selPath != null)
      {
        Object selMe = selPath.getLastPathComponent();
        if (selMe instanceof TreeNodeDuplicate)
        {
          selwidgs.add(((TreeNodeDuplicate) selMe).getSource());
          selectedPaths[numValidPaths] = selectedPaths[i];
          numValidPaths++;

          if (dragObjsSet.add(selMe))
            dragObjs.add(selMe);
        }
        else if (selMe instanceof Widget)
        {
          selwidgs.add(selMe);
          selectedPaths[numValidPaths] = selectedPaths[i];
          numValidPaths++;

          if (dragObjsSet.add(selMe))
            dragObjs.add(selMe);
        }
      }
    }
    if (numValidPaths == selectedPaths.length)
    {
      dragPaths = selectedPaths;
    }
    else
    {
      dragPaths = new javax.swing.tree.TreePath[numValidPaths];
      System.arraycopy(selectedPaths, 0, dragPaths, 0, numValidPaths);
    }
    dragwidgs = (Widget[]) selwidgs.toArray(new Widget[0]);

    if (dragwidgs.length == 1)
    {
      // Single widget copy
      java.awt.datatransfer.Transferable dragObj = new WidgetTransfer(dragwidgs[0], uiMgr);
      myClipboard.setContents(dragObj, this);
    }
    else
    {
      // Multiple widget copy
      java.awt.datatransfer.Transferable dragObj = new WidgetTransfer(dragObjs.toArray(), uiMgr);
      myClipboard.setContents(dragObj, this);
    }
  }

  private void refreshMenu()
  {
    // Ensure the debugger isn't paused/stepping
    uiMgr.getStudio().resumeExecution();
    // Refresh the current menu and retain its page level context
    PseudoMenu currUI = uiMgr.getCurrUI();
    uiMgr.getStaticContext().putAll(currUI.getUI().getRelatedContext().getParent().getMap());
    uiMgr.advanceUI(new PseudoMenu(uiMgr, currUI.getBlueprint()));
  }

  private void launchSelectedMenu()
  {
    javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
    if (selPaths != null && selPaths.length > 0)
    {
      // Ensure the debugger isn't paused/stepping
      uiMgr.getStudio().resumeExecution();
      Object childObj = selPaths[0].getLastPathComponent();
      Widget widgy = null;
      if (childObj instanceof Widget)
      {
        widgy = (Widget) childObj;
      }
      else if (childObj instanceof TreeNodeDuplicate)
      {
        widgy = ((TreeNodeDuplicate) childObj).getSource();
      }
      // Rethread this in case we're debugging and we're the AWT thread
      final Widget reWidg = widgy;
      Pooler.execute(new Runnable()
      {
        public void run()
        {
          if (reWidg != null && reWidg.isType(Widget.MENU))
          {
            uiMgr.advanceUI(new PseudoMenu(uiMgr, reWidg));
          }
          else
          {
            // Refresh the current menu
            PseudoMenu currUI = uiMgr.getCurrUI();
            uiMgr.advanceUI(new PseudoMenu(uiMgr, currUI.getBlueprint()));
          }
        }
      });
    }
  }

  private void pasteFormatting()
  {
    // Check for the correct flavor.
    javax.swing.tree.TreePath[] pastePaths = getSelectionPaths();
    if (pastePaths == null || pastePaths.length == 0) return;
    java.awt.datatransfer.Transferable transData = myClipboard.getContents(null);
    if (transData == null) return;
    if (!isWidgetKeyDrag(transData.getTransferDataFlavors()))
      return;

    Widget sourceWidg = (Widget) WidgetTransfer.getTransferData(uiMgr, transData);
    java.util.Vector undoList = new java.util.Vector();

    for (int i = 0; i < pastePaths.length; i++)
    {
      Object boy = pastePaths[i].getLastPathComponent();
      Widget overwidg;
      if (boy instanceof TreeNodeDuplicate)
        overwidg = ((TreeNodeDuplicate) boy).getSource();
      else if (boy instanceof Widget)
        overwidg = (Widget) boy;
      else continue;

      for (byte j = 0; j <= Widget.MAX_PROP_NUM; j++)
      {
        String oldProp = overwidg.getProperty(j);
        String newProp = sourceWidg.getProperty(j);
        if (!oldProp.equals(newProp))
        {
          undoList.add(new StudioFrame.WidgetOp(overwidg, j, oldProp));
          WidgetFidget.setProperty(overwidg, j, newProp);
        }
      }
    }
    uiMgr.getStudio().pushWidgetOps(undoList);

    dragDropEnd(null);
    ((OracleTreeModel) getModel()).refreshTree();
  }

  private void highlightReferences()
  {
    javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
    if (selPaths != null && selPaths.length > 0)
    {
      Object childObj = selPaths[0].getLastPathComponent();
      Widget widgy = null;
      if (childObj instanceof Widget)
      {
        widgy = (Widget) childObj;
      }
      else if (childObj instanceof TreeNodeDuplicate)
      {
        widgy = ((TreeNodeDuplicate) childObj).getSource();
      }
      if (widgy != null)
      {
        /*
         * The way we find all of the paths in the tree for the references is by
         * first getting all of the containers of this widget. Then you find the paths
         * in the tree for all of those container widgets. Then you need to append the
         * final child to each of those paths. This is done by getting the list of
         * the children for each of those from the TreeModel, and then finding the
         * one that's the same Widget as what we're looking for. That'll ensure we've
         * appended the correct instanceof TreeNodeDuplicate to the tree path. Then
         * we can just set them all as the selected & expanded paths. Find the one that has
         * the highest row in the tree, and then scroll to ensure visibility of that.
         */
        Widget[] conts = widgy.containers();
        OracleTreeModel otm = (OracleTreeModel) getModel();
        javax.swing.tree.TreePath[] contPaths = new javax.swing.tree.TreePath[conts.length];
        for (int i = 0; i < conts.length; i++)
        {
          contPaths[i] = otm.getPathToNode(conts[i]);
          if (contPaths[i] == null)
            continue;
          int currContKidCount = otm.getChildCount(conts[i]);
          for (int j = 0; j < currContKidCount; j++)
          {
            Object currKid = otm.getChild(conts[i], j);
            if (currKid == widgy || (currKid instanceof TreeNodeDuplicate &&
                (((TreeNodeDuplicate) currKid).getSource() == widgy)))
            {
              contPaths[i] = contPaths[i].pathByAddingChild(currKid);
              break;
            }
          }
        }
        setSelectionPaths(contPaths);
      }
    }
  }

  private void setAsPrimaryReference()
  {
    javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
    if (selPaths != null && selPaths.length > 0)
    {
      Object childObj = selPaths[0].getLastPathComponent();
      java.util.Vector undoList = new java.util.Vector();

      Object parentObj = selPaths[0].getPathComponent(selPaths[0].getPathCount() - 2);
      if (!(parentObj instanceof Widget))
        return;

      Widget primaryParent = (Widget) parentObj;

      // We remove all of the parent Widgets, and then add them back but do this one first

      Widget widgy = null;
      if (childObj instanceof Widget)
      {
        widgy = (Widget) childObj;
      }
      else if (childObj instanceof TreeNodeDuplicate)
      {
        widgy = ((TreeNodeDuplicate) childObj).getSource();
      }
      if (widgy != null)
      {
        Widget[] containers = widgy.containers();
        int[] oldIdx = new int[containers.length];
        int primaryIdx = 0;
        for (int i = 0; i < containers.length; i++)
        {
          oldIdx[i] = WidgetFidget.discontent(containers[i], widgy);
          undoList.add(new StudioFrame.WidgetOp(containers[i], widgy, oldIdx[i]));
          if (containers[i] == primaryParent)
            primaryIdx = oldIdx[i];
        }

        // Now re-contain them all, but do the desired primary first
        WidgetFidget.contain(primaryParent, widgy, primaryIdx);
        undoList.add(new StudioFrame.WidgetOp(primaryParent, widgy));
        for (int i = 0; i < containers.length; i++)
        {
          if (containers[i] != primaryParent)
          {
            WidgetFidget.contain(containers[i], widgy, oldIdx[i]);
            undoList.add(new StudioFrame.WidgetOp(containers[i], widgy));
          }
        }
        uiMgr.getStudio().pushWidgetOps(undoList);
        ((OracleTreeModel) getModel()).refreshTree();
      }
    }
  }

  protected void evaluateSelectedWidget()
  {
    Widget[] widgs = getSelectedWidgets();
    if (widgs == null || widgs.length == 0) return;
    try
    {
      Catbert.Context theCon = uiMgr.getStudio().getSuspendedContext();
      Object val = Catbert.evaluateExpression(widgs[0].getName(), theCon == null ? new Catbert.Context(uiMgr) : theCon, null, null);
      javax.swing.JOptionPane.showMessageDialog(javax.swing.SwingUtilities.getAncestorOfClass(
          javax.swing.JFrame.class, this), "Result: " + val);
    }
    catch (Exception e)
    {
      javax.swing.JOptionPane.showMessageDialog(javax.swing.SwingUtilities.getAncestorOfClass(
          javax.swing.JFrame.class, this), "Error evaluating expression: " + e);
    }
  }

  protected void evaluateSelectedWidgetChain()
  {
    Widget[] widgs = getSelectedWidgets();
    if (widgs == null || widgs.length == 0) return;
    Catbert.Context theCon = uiMgr.getStudio().getSuspendedContext();
    Catbert.Context con = (theCon == null) ? new Catbert.Context(uiMgr) : theCon.createChild();
    Catbert.ExecutionPosition ep = ZPseudoComp.processChain(widgs[0], con, null, null, false);
    if (ep != null)
    {
      ep.addToStack(widgs[0]);
      ep.markSafe();
    }
    javax.swing.JOptionPane.showMessageDialog(javax.swing.SwingUtilities.getAncestorOfClass(
        javax.swing.JFrame.class, this), "Result: " + con.get(null));

  }

  private void showEditorForSelectedPath()
  {
    javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
    if (selPaths != null && selPaths.length > 0)
    {
      java.util.ArrayList editWidgs = new java.util.ArrayList();
      for (int i = 0; i < selPaths.length; i++)
      {
        Object childObj = selPaths[i].getLastPathComponent();
        Widget widgy = null;
        if (childObj instanceof Widget)
        {
          widgy = (Widget) childObj;
        }
        else if (childObj instanceof TreeNodeDuplicate)
        {
          widgy = ((TreeNodeDuplicate) childObj).getSource();
        }
        if (widgy != null)
          editWidgs.add(widgy);
      }
      if (!editWidgs.isEmpty())
      {
        if (editWidgs.size() == 1 && allEditors.get(editWidgs.get(0)) != null)
          ((FloatingEditor) allEditors.get(editWidgs.get(0))).toFront();
        else
        {
          Widget[] widgys = (Widget[]) editWidgs.toArray(new Widget[0]);
          FloatingEditor linkedEditor = new FloatingEditor(widgys, this);
          java.awt.Rectangle pb = getPathBounds(selPaths[0]);
          linkedEditor.spawn(this, pb.x, pb.y);
          if (widgys.length == 1)
            allEditors.put(widgys[0], linkedEditor);
        }
      }
    }
  }

  // When duplicates are selected, delete the relationship, NOT the widget,
  // that's what I've always meant when I did that on accident. We should actually do
  // that for ANY widget that has more than one parent.
  protected void deleteSelectedWidgets()
  {
    javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
    if (selPaths != null && selPaths.length > 0)
    {
      final int rowSelNum = getRowForPath(selPaths[0]);
      java.util.ArrayList killUs = new java.util.ArrayList();
      java.util.ArrayList killUsIfNoParents = new java.util.ArrayList();
      java.util.ArrayList parentChildToBreak = new java.util.ArrayList();
      for (int i = 0; i < selPaths.length; i++)
      {
        Object childObj = selPaths[i].getLastPathComponent();
        if (childObj instanceof Widget)
        {
          Widget woo = (Widget) childObj;
          if (woo.isType(Widget.MENU) || woo.numContainers() <= 1 || selPaths[i].getPathCount() < 3)
            killUs.add(childObj);
          else
          {
            Object parentObj = selPaths[i].getPathComponent(selPaths[i].getPathCount() - 2);
            if (parentObj instanceof Widget)
            {
              parentChildToBreak.add(new Widget[] { (Widget) parentObj, woo });
              killUsIfNoParents.add(woo);
            }
          }
        }
        else if (childObj instanceof TreeNodeDuplicate)
        {
          if (selPaths[i].getPathCount() > 2)
          {
            Object parentObj = selPaths[i].getPathComponent(selPaths[i].getPathCount() - 2);
            if (parentObj instanceof Widget)
            {
              parentChildToBreak.add(new Widget[] {
                  (Widget) parentObj, ((TreeNodeDuplicate) childObj).getSource() });
            }
          }
        }
      }

      java.util.Vector undoList = new java.util.Vector();
      for (int i = 0; i < parentChildToBreak.size(); i++)
      {
        Widget[] currElem = (Widget[]) parentChildToBreak.get(i);
        int oldIdx = WidgetFidget.discontent(currElem[0], currElem[1]);
        undoList.add(new StudioFrame.WidgetOp(currElem[0], currElem[1], oldIdx));
      }
      for (int i = 0; i < killUsIfNoParents.size(); i++)
      {
        Widget woo = (Widget) killUsIfNoParents.get(i);
        if (woo.numContainers() == 0)
          killUs.add(woo);
      }
      uiMgr.getStudio().removeWidgetsWithUndo((Widget[]) killUs.toArray(new Widget[0]), undoList);
      uiMgr.getStudio().pushWidgetOps(undoList);
      ((OracleTreeModel) getModel()).refreshTree();
      java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
        setSelectionRow(rowSelNum); } });
    }
  }

  private java.util.Vector breakPathsFromParents(javax.swing.tree.TreePath[] selPaths)
  {
    java.util.Vector undoList = new java.util.Vector();
    if (selPaths != null)
    {
      java.util.ArrayList parentChildToBreak = new java.util.ArrayList();
      for (int i = 0; i < selPaths.length; i++)
      {
        if (selPaths[i].getPathCount() > 2)
        {
          Object parentObj = selPaths[i].getPathComponent(selPaths[i].getPathCount() - 2);
          Object childObj = selPaths[i].getLastPathComponent();
          if (parentObj instanceof Widget)
          {
            if (childObj instanceof Widget)
            {
              parentChildToBreak.add(new Widget[] {
                  (Widget) parentObj, (Widget) childObj });
            }
            else if (childObj instanceof TreeNodeDuplicate)
            {
              parentChildToBreak.add(new Widget[] {
                  (Widget) parentObj, ((TreeNodeDuplicate) childObj).getSource() });
            }
          }
          else if ((parentObj instanceof java.util.Vector) && (childObj instanceof Widget))
          {
            // This special case allows breaking a connection when something points to itself.
            Widget circwidg = (Widget) childObj;
            if (circwidg.isContainer(circwidg))
            {
              parentChildToBreak.add(new Widget[] { circwidg, circwidg });
            }
          }
        }
      }

      for (int i = 0; i < parentChildToBreak.size(); i++)
      {
        Widget[] currElem = (Widget[]) parentChildToBreak.get(i);
        int oldIdx = WidgetFidget.discontent(currElem[0], currElem[1]);
        undoList.add(new StudioFrame.WidgetOp(currElem[0], currElem[1], oldIdx));
      }
    }
    return undoList;
  }

  private void moveSelectedPaths(int direction)
  {
    javax.swing.tree.TreePath[] selPaths = getSelectionPaths();
    if (selPaths != null && selPaths.length > 0)
    {
      if (selPaths[0].getPathCount() > 2)
      {
        Object parentObj = selPaths[0].getPathComponent(selPaths[0].getPathCount() - 2);
        Object childObj = selPaths[0].getLastPathComponent();
        if (parentObj instanceof Widget)
        {
          java.util.Vector undoList = new java.util.Vector();
          Widget parentWidg = (Widget) parentObj;
          Widget childWidg;
          if (childObj instanceof Widget)
            childWidg = (Widget) childObj;
          else
            childWidg = ((TreeNodeDuplicate) childObj).getSource();

          int oldIdx = WidgetFidget.discontent(parentWidg, childWidg);
          undoList.add(new StudioFrame.WidgetOp(parentWidg, childWidg, oldIdx));
          WidgetFidget.contain(parentWidg, childWidg, oldIdx + direction);
          undoList.add(new StudioFrame.WidgetOp(parentWidg, childWidg));
          /*
           * To move something up, we recontain the one just before this one, and then
           * recontain all of the ones that were originally after this one.
           * To move something down, we recontain ourself, and then recontain all of the
           * ones that were originally after the one after this one.
           */
          /*Widget[] parKids = parentWidg.contents();
					for (int i = 0; i < parKids.length; i++)
					{
						if (parKids[i] == childWidg)
						{
							if (direction == -1 && i > 0)
							{
								int oldIdx = parentWidg.discontent(parKids[i - 1]);
								undoList.add(new StudioFrame.WidgetOp(parentWidg, parKids[i - 1], oldIdx));
								parentWidg.contain(parKids[i - 1]);
								undoList.add(new StudioFrame.WidgetOp(parentWidg, parKids[i - 1]));
							}
							else if (direction == 1 && i < parKids.length - 1)
							{
								int oldIdx = parentWidg.discontent(childWidg);
								undoList.add(new StudioFrame.WidgetOp(parentWidg, childWidg, oldIdx));
								parentWidg.contain(childWidg);
								undoList.add(new StudioFrame.WidgetOp(parentWidg, childWidg));
								i++;
							}
							else
								break;
							i++;
							while (i < parKids.length)
							{
								int oldIdx = parentWidg.discontent(parKids[i]);
								undoList.add(new StudioFrame.WidgetOp(parentWidg, parKids[i], oldIdx));
								parentWidg.contain(parKids[i]);
								undoList.add(new StudioFrame.WidgetOp(parentWidg, parKids[i]));
								i++;
							}
						}
					}*/
          uiMgr.getStudio().pushWidgetOps(undoList);
          ((OracleTreeModel) getModel()).refreshTree();
        }
      }
    }
  }

  private String lastFindString = "";
  public void find()
  {
    String newFindString = (String) javax.swing.JOptionPane.showInputDialog(javax.swing.SwingUtilities.getAncestorOfClass(
        javax.swing.JFrame.class, this),
        "Enter the string to search for:", "Find",
        javax.swing.JOptionPane.QUESTION_MESSAGE, null, null, lastFindString);
    if (newFindString != null && newFindString.trim().length() > 0)
    {
      lastFindString = newFindString;
      Widget[] allWidgets = uiMgr.getModuleGroup().getWidgets();
      // 601 Widget[] allWidgets = uiMgr.moduleGroup.getWidgets();
      java.util.ArrayList newSelPaths = new java.util.ArrayList();
      boolean checkSymbols = Sage.getBoolean("studio/display_widget_uids", false);
      int maxResults = Sage.getInt("studio/max_find_results", 1000);
      for (int i = 0; i < allWidgets.length && newSelPaths.size() < maxResults; i++)
      {
        if (allWidgets[i].getUntranslatedName().indexOf(newFindString) != -1 ||
            (checkSymbols && allWidgets[i].symbol() != null && allWidgets[i].symbol().indexOf(newFindString) != -1) ||
            (!allWidgets[i].isType(Widget.CONDITIONAL) &&
                !allWidgets[i].isType(Widget.ACTION) &&
                allWidgets[i].searchPropertyValues(newFindString, false) != null))
        {
          newSelPaths.add(((OracleTreeModel) getModel()).getPathToNode(allWidgets[i]));
        }
      }
      setSelectionPaths((javax.swing.tree.TreePath[]) newSelPaths.toArray(new javax.swing.tree.TreePath[0]));
      if (newSelPaths.size() >= maxResults)
      {
        javax.swing.JOptionPane.showMessageDialog(javax.swing.SwingUtilities.getAncestorOfClass(
            javax.swing.JFrame.class, this), "Maximum search results reached of " + maxResults, "Notification",
            javax.swing.JOptionPane.WARNING_MESSAGE);
      }
    }
  }

  public void setDisplayUIDs(boolean x)
  {
    displayUIDs = x;
  }

  public void setDisplayAttributeValues(boolean x)
  {
    displayAttValues = x;
  }

  public boolean getDisplayAttributeValues()
  {
    return displayAttValues;
  }

  private void findNext()
  {
  }

  private void findPrevious()
  {
  }

  public void keyTyped(java.awt.event.KeyEvent evt)
  {
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

  public void addBreakpointsAtSelectedWidgets()
  {
    Widget[] ws = getSelectedWidgets();
    if (ws == null || ws.length == 0) return;
    for (int i = 0; i < ws.length; i++)
      uiMgr.getStudio().addBreakpoint(ws[i]);
  }

  public void removeBreakpointsAtSelectedWidgets()
  {
    Widget[] ws = getSelectedWidgets();
    if (ws == null || ws.length == 0) return;
    for (int i = 0; i < ws.length; i++)
      uiMgr.getStudio().removeBreakpoint(ws[i]);
  }

  // Returns 1 if they're all breakpoints, 0 if it's a combo, -1 if they're all not breakpoints
  public int getSelectedWidgetsBreakpointState()
  {
    boolean foundBreak = false;
    Widget[] ws = getSelectedWidgets();
    if (ws == null || ws.length == 0) return 0;
    for (int i = 0; i < ws.length; i++)
    {
      if (uiMgr.getStudio().getBreakpointInfo(ws[i]) != null)
      {
        if (!foundBreak && i > 0)
          return 0;
        foundBreak = true;
      }
      else if (foundBreak)
        return 0;
    }
    return foundBreak ? 1 : -1;
  }

  public void editorDestroyed(FloatingEditor whichOne)
  {
    javax.swing.tree.TreePath refreshMe = ((OracleTreeModel) getModel()).getPathToNode(whichOne.getWidget());
    if (refreshMe != null)
    {
      ((OracleTreeModel) getModel()).refreshPath(refreshMe);
    }
    allEditors.remove(whichOne.getWidget());
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

  public java.awt.Insets getAutoscrollInsets()
  {
    // Find our current viewport location
    if (getParent() instanceof javax.swing.JViewport)
    {
      javax.swing.JViewport view = (javax.swing.JViewport) getParent();
      java.awt.Rectangle viewRect = view.getViewRect();
      return new java.awt.Insets(viewRect.y + DRAG_EDGE_TO_AUTOSCROLL,
          viewRect.x + DRAG_EDGE_TO_AUTOSCROLL,
          getHeight() - viewRect.height - viewRect.y + DRAG_EDGE_TO_AUTOSCROLL,
          getWidth() - viewRect.width - viewRect.x + DRAG_EDGE_TO_AUTOSCROLL);
    }
    else
    {
      return new java.awt.Insets(0, 0, 0, 0);
    }
  }

  public void autoscroll(java.awt.Point cursorLoc)
  {
    // Figure out which direction to scroll
    javax.swing.JViewport view = null;
    if (getParent() instanceof javax.swing.JViewport)
    {
      view = (javax.swing.JViewport) getParent();
    }
    else
    {
      return;
    }

    java.awt.Point viewPos = view.getViewPosition();
    if (cursorLoc.y > viewPos.y + (view.getExtentSize().height/2))
    {
      viewPos.y += MOVE_FOR_AUTOSCROLL;
    }
    else
    {
      viewPos.y -= MOVE_FOR_AUTOSCROLL;
    }
    viewPos.y = Math.max(0, viewPos.y);
    viewPos.y = Math.min(viewPos.y, getHeight() - view.getExtentSize().height);
    if (cursorLoc.x > viewPos.x + (view.getExtentSize().width/2))
    {
      viewPos.x += MOVE_FOR_AUTOSCROLL;
    }
    else
    {
      viewPos.x -= MOVE_FOR_AUTOSCROLL;
    }
    viewPos.x = Math.max(0, viewPos.x);
    viewPos.x = Math.min(viewPos.x, getWidth() - view.getExtentSize().width);
    view.setViewPosition(viewPos);
  }

  public UIManager getUIMgr() { return uiMgr; }

  private UIManager uiMgr;
  private Widget[] dragwidgs;
  private javax.swing.tree.TreePath[] dragPaths;
  private MultiLineTreeRenderer myCellRenderer;
  private String selTextColorString;
  private String nonSelTextColorString;
  private boolean displayUIDs;
  private boolean displayAttValues;

  private javax.swing.JPopupMenu rightClicky;
  private javax.swing.JMenuItem expandChildrenMenuItem;
  private javax.swing.JMenuItem expandAllNodesMenuItem;
  private javax.swing.JMenuItem collapseAllNodesMenuItem;
  private javax.swing.JMenuItem deleteMenuItem;
  private javax.swing.JMenuItem breakFromParentMenuItem;
  private javax.swing.JMenuItem moveDownMenuItem;
  private javax.swing.JMenuItem moveUpMenuItem;
  private javax.swing.JMenuItem renameMenuItem;
  private javax.swing.JMenuItem propertiesMenuItem;
  private javax.swing.JMenuItem refreshMenuMenuItem;
  private javax.swing.JMenuItem launchMenuMenuItem;
  private javax.swing.JMenuItem showReferencesMenuItem;
  private javax.swing.JMenuItem setAsPrimaryReferenceMenuItem;
  private javax.swing.JMenuItem pasteFormattingMenuItem;
  private javax.swing.JMenuItem evaluateMenuItem;
  private javax.swing.JMenuItem evaluateChainMenuItem;
  private javax.swing.JMenu newChildrenSubmenu;
  private NewWidgetMenuItem[] newWidgetMenuItems;
  private javax.swing.JMenuItem addBreakpointMenuItem;
  private javax.swing.JMenuItem removeBreakpointMenuItem;

  private boolean lastClipboardCopy;

  private java.util.Map allEditors;
  private int oldTreeWidth;

  private boolean forcingSelectionPath;

  private java.awt.datatransfer.Clipboard myClipboard;

  public static final String WIDGET_KEY = "WidgetKey";
  public static final String MULTIPLE_WIDGET_KEYS = "MultipleWidgetKeys";
  public static final char MULTIPLE_KEY_SEPARATOR = ',';
  public static final String WIDGET_TYPE = "WidgetType";

  public boolean isWidgetDragSameUI(java.awt.datatransfer.DataFlavor[] flavs)
  {
    for (int i = 0; i < flavs.length; i++)
    {
      String currFlav = flavs[i].getHumanPresentableName();
      if (currFlav != null)
      {
        if (currFlav.startsWith(WIDGET_TYPE))
        {
          return true;
        }
        else if (currFlav.startsWith(WIDGET_KEY))
        {
          return UIManager.getLocalUIByName(currFlav.substring(WIDGET_KEY.length(), currFlav.indexOf('*'))) == uiMgr;
        }
        else if (currFlav.startsWith(MULTIPLE_WIDGET_KEYS))
        {
          return UIManager.getLocalUIByName(currFlav.substring(MULTIPLE_WIDGET_KEYS.length(), currFlav.indexOf('*'))) == uiMgr;
        }
      }
    }
    return false;

  }

  public Widget getWidgetForDrag(java.awt.dnd.DropTargetDropEvent evt)
  {
    return getWidgetForDrag(evt.getCurrentDataFlavors());
  }
  public Widget getWidgetForDrag(java.awt.dnd.DropTargetDragEvent evt)
  {
    return getWidgetForDrag(evt.getCurrentDataFlavors());
  }
  public Widget getWidgetForDrag(java.awt.datatransfer.DataFlavor[] flavs)
  {
    for (int i = 0; i < flavs.length; i++)
    {
      String currFlav = flavs[i].getHumanPresentableName();
      if ((currFlav != null) && currFlav.startsWith(WIDGET_KEY))
      {
        Widget retVal =
            // 601
            //                    Wizard.getInstance().getWidgetForID(Integer.parseInt(currFlav.substring(WIDGET_KEY.length())));
            UIManager.getLocalUIByName(currFlav.substring(WIDGET_KEY.length(), currFlav.indexOf('*'))).getModuleGroup().
            getWidgetForID(Integer.parseInt(currFlav.substring(currFlav.indexOf('*') + 1)));
        return retVal;
      }
    }
    return null;
  }

  public Widget[] getWidgetsForMultiDrag(java.awt.dnd.DropTargetDropEvent evt)
  {
    return getWidgetsForMultiDrag(evt.getCurrentDataFlavors());
  }
  public Widget[] getWidgetsForMultiDrag(java.awt.dnd.DropTargetDragEvent evt)
  {
    return getWidgetsForMultiDrag(evt.getCurrentDataFlavors());
  }
  public Widget[] getWidgetsForMultiDrag(java.awt.datatransfer.DataFlavor[] flavs)
  {
    for (int i = 0; i < flavs.length; i++)
    {
      String currFlav = flavs[i].getHumanPresentableName();
      if ((currFlav != null) && currFlav.startsWith(MULTIPLE_WIDGET_KEYS))
      {
        UIManager currUIMgr = UIManager.getLocalUIByName(currFlav.substring(MULTIPLE_WIDGET_KEYS.length(), currFlav.indexOf('*')));
        String keys = currFlav.substring(currFlav.indexOf('*') + 1);
        java.util.StringTokenizer toker = new java.util.StringTokenizer(keys,
            "" + MULTIPLE_KEY_SEPARATOR);
        Widget[] retVal = new Widget[toker.countTokens()];
        for(int j = 0; toker.hasMoreTokens(); j++)
        {
          // 601
          //					retVal[j] = Wizard.getInstance().getWidgetForID(Integer.parseInt(toker.nextToken()));
          retVal[j] = currUIMgr.getModuleGroup().getWidgetForID(Integer.parseInt(toker.nextToken()));
        }
        return retVal;
      }
    }
    return new Widget[0];
  }

  public byte getWidgetTypeForDrag(java.awt.dnd.DropTargetDropEvent evt)
  {
    return getWidgetTypeForDrag(evt.getCurrentDataFlavors());
  }
  public byte getWidgetTypeForDrag(java.awt.dnd.DropTargetDragEvent evt)
  {
    return getWidgetTypeForDrag(evt.getCurrentDataFlavors());
  }
  public byte getWidgetTypeForDrag(java.awt.datatransfer.DataFlavor[] flavs)
  {
    for (int i = 0; i < flavs.length; i++)
    {
      String currFlav = flavs[i].getHumanPresentableName();
      if (currFlav != null)
      {
        if (currFlav.startsWith(WIDGET_TYPE))
        {
          return WidgetMeta.getTypeForName(currFlav.substring(WIDGET_TYPE.length()));
        }
        else if (currFlav.startsWith(WIDGET_KEY))
        {
          Widget widgy =
              // 601					Wizard.getInstance().getWidgetForID(Integer.parseInt(currFlav.substring(WIDGET_KEY.length())));
              UIManager.getLocalUIByName(currFlav.substring(WIDGET_KEY.length(), currFlav.indexOf('*'))).getModuleGroup().
              getWidgetForID(Integer.parseInt(currFlav.substring(currFlav.indexOf('*') + 1)));
          if (widgy != null)
          {
            return widgy.type();
          }
          else
          {
            return -1;
          }
        }
      }
    }
    return -1;
  }

  public static boolean isWidgetTypeDrag(java.awt.dnd.DropTargetDropEvent evt)
  {
    return isWidgetTypeDrag(evt.getCurrentDataFlavors());
  }
  public static boolean isWidgetTypeDrag(java.awt.dnd.DropTargetDragEvent evt)
  {
    return isWidgetTypeDrag(evt.getCurrentDataFlavors());
  }
  public static boolean isWidgetTypeDrag(java.awt.datatransfer.DataFlavor[] flavs)
  {
    for (int i = 0; i < flavs.length; i++)
    {
      String currFlav = flavs[i].getHumanPresentableName();
      if (currFlav != null)
      {
        if (currFlav.startsWith(WIDGET_TYPE))
        {
          return true;
        }
        else if (currFlav.startsWith(WIDGET_KEY))
        {
          return false;
        }
        else if (currFlav.startsWith(MULTIPLE_WIDGET_KEYS))
        {
          return false;
        }
      }
    }
    return false;
  }

  public static boolean isWidgetKeyDrag(java.awt.dnd.DropTargetDropEvent evt)
  {
    return isWidgetKeyDrag(evt.getCurrentDataFlavors());
  }
  public static boolean isWidgetKeyDrag(java.awt.dnd.DropTargetDragEvent evt)
  {
    return isWidgetKeyDrag(evt.getCurrentDataFlavors());
  }
  public static boolean isWidgetKeyDrag(java.awt.datatransfer.DataFlavor[] flavs)
  {
    for (int i = 0; i < flavs.length; i++)
    {
      String currFlav = flavs[i].getHumanPresentableName();
      if (currFlav != null)
      {
        if (currFlav.startsWith(WIDGET_TYPE))
        {
          return false;
        }
        else if (currFlav.startsWith(WIDGET_KEY))
        {
          return true;
        }
        else if (currFlav.startsWith(MULTIPLE_WIDGET_KEYS))
        {
          return false;
        }
      }
    }
    return false;
  }

  public static boolean isMultipleWidgetKeyDrag(java.awt.dnd.DropTargetDropEvent evt)
  {
    return isMultipleWidgetKeyDrag(evt.getCurrentDataFlavors());
  }
  public static boolean isMultipleWidgetKeyDrag(java.awt.dnd.DropTargetDragEvent evt)
  {
    return isMultipleWidgetKeyDrag(evt.getCurrentDataFlavors());
  }
  public static boolean isMultipleWidgetKeyDrag(java.awt.datatransfer.DataFlavor[] flavs)
  {
    for (int i = 0; i < flavs.length; i++)
    {
      String currFlav = flavs[i].getHumanPresentableName();
      if (currFlav != null)
      {
        if (currFlav.startsWith(WIDGET_TYPE))
          return false;
        else if (currFlav.startsWith(WIDGET_KEY))
          return false;
        else if (currFlav.startsWith(MULTIPLE_WIDGET_KEYS))
          return true;
      }
    }
    return false;
  }

  public void lostOwnership(java.awt.datatransfer.Clipboard clipboard, java.awt.datatransfer.Transferable contents)
  {
  }
}
