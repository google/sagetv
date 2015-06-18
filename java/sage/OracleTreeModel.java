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

public class OracleTreeModel implements javax.swing.tree.TreeModel
{
  protected static final Object ROOT = new Object();

  protected OracleTreeModel()
  {
    listenerList = new javax.swing.event.EventListenerList();
    duplicateHash = new java.util.HashMap();
  }

  public void setTreeUI(OracleTree inTreeUI)
  {
    treeUI = inTreeUI;
  }

  public void setUIMgr(UIManager inUIMgr)
  {
    uiMgr = inUIMgr;
  }

  // Part of TreeModel interface
  public Object getChild(Object parent, int index)
  {
    Widget[] orderedKids;
    if (parent == ROOT)
    {
      orderedKids = getRoots();
      if ((index == orderedKids.length) && !stragglers.isEmpty())
        return stragglers;
    }
    else if (parent == stragglers)
      orderedKids = (Widget[]) stragglers.toArray(new Widget[0]);
    else
      orderedKids = getKids((Widget) parent);

    if ((index < 0) || (index >= orderedKids.length))
      return null;

    Widget daKid = orderedKids[index];
    Object retVal;
    if ((parent == ROOT) || (parent == stragglers))
    {
      retVal = daKid;
    }
    else
    {
      Widget[] kidsParents = getParents(daKid);
      int parentIndex = -1;
      for (int i = 0; i < kidsParents.length; i++)
      {
        if (kidsParents[i].equals(parent))
        {
          parentIndex = i;
          break;
        }
      }
      if (parentIndex < 0)
      {
        /*
         * NOTE: This could theoretically happen if the model got changed between a call to getChildCount()
         * and the call here. But that's in Sun's code so I don't know what else to do but this.
         */
        retVal = null;
      }
      else if (parentIndex == 0 && !daKid.isType(Widget.MENU))
      {
        // We're the primary child, we get to be the actual object.
        retVal = daKid;
      }
      else
      {
        java.util.Vector cacheList = (java.util.Vector) duplicateHash.get(daKid);
        if (cacheList == null)
        {
          cacheList = new java.util.Vector();
          duplicateHash.put(daKid, cacheList);
        }
        if (cacheList.size() <= parentIndex)
        {
          cacheList.setSize(parentIndex + 1);
        }
        Object cachedKid = cacheList.elementAt(parentIndex);
        if (cachedKid == null)
        {
          cachedKid = new TreeNodeDuplicate(daKid, parentIndex);
          cacheList.setElementAt(cachedKid, parentIndex);
        }
        retVal = cachedKid;
      }
    }
    return retVal;
  }

  protected Widget[] getParents(Widget forMe)
  {
    if (forMe == null) return null;
    return forMe.containers();
  }

  protected Widget[] getKids(Widget forMe)
  {
    if (forMe == null) return null;
    return forMe.contents();
  }

  protected Widget[] getRoots()
  {
    java.util.Set currAllObjects = null;
    java.util.Set currAccountedFor = null;
    synchronized (rootCacheLock)
    {
      if (rootCache != null)
      {
        return rootCache;
      }
      if (allObjects != null)
      {
        currAllObjects = allObjects;
        allObjects = null;
      }
      else
        currAllObjects = new java.util.HashSet();
      if (accountedFor != null)
      {
        currAccountedFor = accountedFor;
        accountedFor = null;
      }
      else
        currAccountedFor = new java.util.HashSet();
    }
    // This is all of the unparented objects
    currAllObjects.clear();
    currAccountedFor.clear();

    java.util.ArrayList tempVec = new java.util.ArrayList();
    Widget[] all = uiMgr.getModuleGroup().getWidgets();
    for (int j = 0; j < all.length; j++)
    {
      boolean hasParents = false;
      currAllObjects.add(all[j]);
      if (!all[j].isType(Widget.MENU))
        hasParents = all[j].numContainers() > 0;
        if (!hasParents)
        {
          tempVec.add(all[j]);
          currAccountedFor.add(all[j]);
          addRecursiveChildren(all[j], currAccountedFor);
        }
    }
    Widget[] orderedKids = (Widget[]) tempVec.toArray(new Widget[0]);

    // Can't we just simply sort this here and we're OK then??
    java.util.Arrays.sort(orderedKids, widgetNameSorter);
    synchronized (rootCacheLock)
    {
      rootCache = orderedKids;
      stragglers.clear();
      java.util.Set tempStraggle = new java.util.HashSet(allObjects = currAllObjects);
      tempStraggle.removeAll(accountedFor = currAccountedFor);
      stragglers.addAll(tempStraggle);
    }
    return orderedKids;
  }

  protected void addRecursiveChildren(Widget parent, java.util.Set storage)
  {
    Widget[] currKids = getKids(parent);
    if (currKids == null) return;
    for (int i = 0; i < currKids.length; i++)
    {
      // Only descend into primary references...that means any hidden non-circular references
      // will show up in Circularities as well with the primary one being bolded there. So the user
      // can easily find the other references for that one and fix the problem.
      Widget[] parents = currKids[i].containers();
      if (parents[0] == parent)
      {
        if (!storage.contains(currKids[i]))
        {
          storage.add(currKids[i]);
          addRecursiveChildren(currKids[i], storage);
        }
      }
    }
  }

  // Part of TreeModel interface
  public int getChildCount(Object inParent)
  {
    int numKids;
    if (inParent == ROOT)
    {
      numKids = getRoots().length;
      if (!stragglers.isEmpty())
      {
        numKids++;
      }
    }
    else if (inParent == stragglers)
      numKids = stragglers.size();
    else if ((inParent instanceof TreeNodeDuplicate) || stragglers.contains(inParent))
      numKids = 0;
    else
    {
      Widget parent = (Widget) inParent;
      numKids = getKids(parent).length;
    }
    return numKids;
  }

  // Part of TreeModel interface
  public int getIndexOfChild(Object parent, Object child)
  {
    Widget[] orderedKids;
    int retVal = -1;
    if (parent == ROOT)
    {
      orderedKids = getRoots();
      if (child == stragglers)
      {
        return orderedKids.length;
      }
    }
    else if (parent == stragglers)
    {
      retVal = stragglers.indexOf(child);
      return retVal;
    }
    else if (!(parent instanceof Widget))
    {
      return -1;
    }
    else
    {
      orderedKids = getKids((Widget) parent);
    }
    if (child instanceof TreeNodeDuplicate)
    {
      child = ((TreeNodeDuplicate) child).getSource();
    }
    for (int i = 0; i < orderedKids.length; i++)
    {
      if (orderedKids[i].equals(child))
      {
        retVal = i;
        break;
      }
    }
    if (retVal < 0)
      retVal = -1;
    return retVal;
  }

  // Part of TreeModel interface
  public Object getRoot()
  {
    return ROOT;
  }

  // Part of TreeModel interface
  public boolean isLeaf(Object node)
  {
    boolean retVal = (node instanceof TreeNodeDuplicate) || (getChildCount(node) == 0);
    return retVal;
  }

  // Part of TreeModel interface
  public void valueForPathChanged(javax.swing.tree.TreePath path, Object newValue)
  {
    Object selMe = path.getLastPathComponent();
    Widget w = null;
    if (selMe instanceof TreeNodeDuplicate)
    {
      w = ((TreeNodeDuplicate) selMe).getSource();
    }
    else if (selMe instanceof Widget)
    {
      w = (Widget) selMe;
    }

    String oldName = w.getUntranslatedName();
    if (treeUI.getDisplayAttributeValues() && w.type() == Widget.ATTRIBUTE)
    {
      // This has the name and the value
      String valStr = newValue.toString();
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
        WidgetFidget.setName(w, newValue.toString());
        if (!oldName.equals(newValue.toString()))
          uiMgr.getStudio().pushWidgetOp(new StudioFrame.WidgetOp(w, oldName));
      }
    }
    else
    {
      WidgetFidget.setName(w, newValue.toString());
      if (!oldName.equals(newValue.toString()))
        uiMgr.getStudio().pushWidgetOp(new StudioFrame.WidgetOp(w, oldName));
    }
    try {
      // only precompile non-empty actions/attrs
      switch(w.type()){
        case Widget.ATTRIBUTE:
          if( w.getProperty(Widget.VALUE).length()>0)
            Catbert.precompileWidget(w);
          break;
        case Widget.ACTION:
        case Widget.CONDITIONAL:
        case Widget.BRANCH:
          if (w.getName().length()>0)
            Catbert.precompileWidget(w);
          break;
        default:
          Catbert.precompileWidget(w);
          break;
      }
    } catch (ParseException e) {
      if (sage.Sage.getBoolean("studio/alert_on_syntax_error", false)) {
        javax.swing.JOptionPane.showMessageDialog(JOptionPane.getFrameForComponent(treeUI), e.getMessage(), "Parse Error",
            JOptionPane.ERROR_MESSAGE);
      }
    }

    refreshPath(path);
  }

  public void addTreeModelListener(javax.swing.event.TreeModelListener l)
  {
    listenerList.add(javax.swing.event.TreeModelListener.class, l);
  }

  public void removeTreeModelListener(javax.swing.event.TreeModelListener l)
  {
    listenerList.remove(javax.swing.event.TreeModelListener.class, l);
  }

  protected void fireTreeNodesChanged(Object source, Object[] path,
      int[] childIndices, Object[] children)
  {
    // Guaranteed to return a non-null array
    Object[] listeners = listenerList.getListenerList();

    javax.swing.event.TreeModelEvent e = null;
    // Process the listeners last to first, notifying
    // those that are interested in this event
    for (int i = listeners.length - 2; i >= 0; i -= 2)
    {
      if (listeners[i] == javax.swing.event.TreeModelListener.class)
      {
        // Lazily create the event:
        if (e == null)
        {
          e = new javax.swing.event.TreeModelEvent(source, path,
              childIndices, children);
        }
        ((javax.swing.event.TreeModelListener)listeners[i + 1]).treeNodesChanged(e);
      }
    }
  }

  protected void fireTreeNodesInserted(Object source, Object[] path,
      int[] childIndices, Object[] children)
  {
    // Guaranteed to return a non-null array
    Object[] listeners = listenerList.getListenerList();

    javax.swing.event.TreeModelEvent e = null;
    // Process the listeners last to first, notifying
    // those that are interested in this event
    for (int i = listeners.length - 2; i >= 0; i -= 2)
    {
      if (listeners[i] == javax.swing.event.TreeModelListener.class)
      {
        // Lazily create the event:
        if (e == null)
        {
          e = new javax.swing.event.TreeModelEvent(source, path,
              childIndices, children);
        }
        ((javax.swing.event.TreeModelListener)listeners[i + 1]).treeNodesInserted(e);
      }
    }
  }

  protected void fireTreeNodesRemoved(Object source, Object[] path,
      int[] childIndices, Object[] children)
  {
    // Guaranteed to return a non-null array
    Object[] listeners = listenerList.getListenerList();

    javax.swing.event.TreeModelEvent e = null;
    // Process the listeners last to first, notifying
    // those that are interested in this event
    for (int i = listeners.length - 2; i >= 0; i -= 2)
    {
      if (listeners[i] == javax.swing.event.TreeModelListener.class)
      {
        // Lazily create the event:
        if (e == null)
        {
          e = new javax.swing.event.TreeModelEvent(source, path,
              childIndices, children);
        }
        ((javax.swing.event.TreeModelListener)listeners[i + 1]).treeNodesRemoved(e);
      }
    }
  }

  protected void fireTreeStructureChanged(Object source, Object[] path,
      int[] childIndices, Object[] children)
  {
    // Guaranteed to return a non-null array
    Object[] listeners = listenerList.getListenerList();

    javax.swing.event.TreeModelEvent e = null;
    // Process the listeners last to first, notifying
    // those that are interested in this event
    for (int i = listeners.length - 2; i >= 0; i -= 2)
    {
      if (listeners[i] == javax.swing.event.TreeModelListener.class)
      {
        // Lazily create the event:
        if (e == null)
        {
          e = new javax.swing.event.TreeModelEvent(source, path,
              childIndices, children);
        }
        ((javax.swing.event.TreeModelListener)listeners[i + 1]).treeStructureChanged(e);
      }
    }
  }

  public synchronized void refreshTree()
  {
    if (refreshSubmitted)
      return;

    refreshSubmitted = true;
    java.awt.EventQueue.invokeLater(new Runnable()
    {
      public void run()
      {
        safeRefreshTree();
      }
    });
  }

  protected java.util.Vector getCircularities()
  {
    return stragglers;
  }

  private synchronized void safeRefreshTree()
  {
    synchronized (rootCacheLock)
    {
      rootCache = null;
    }
    refreshSubmitted = false;
    java.util.Enumeration expanded =
        treeUI.getExpandedDescendants(new javax.swing.tree.TreePath(
            new Object[] { ROOT }));
    javax.swing.JScrollPane myScroll = (javax.swing.JScrollPane)
        javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class,
            treeUI);
    java.awt.Point scroll = null;
    if (myScroll != null)
      scroll = myScroll.getViewport().getViewPosition();
    javax.swing.tree.TreePath[] selectedPaths = treeUI.getSelectionPaths();
    fireTreeStructureChanged(this, new Object[] { ROOT }, null, null);
    while ((expanded != null) && expanded.hasMoreElements())
      treeUI.expandPath((javax.swing.tree.TreePath) expanded.nextElement());
    if (myScroll != null)
      myScroll.getViewport().setViewPosition(scroll);
    if (selectedPaths != null)
      treeUI.setSelectionPaths(selectedPaths);
  }

  protected void refreshPath(javax.swing.tree.TreePath nodePath)
  {
    final javax.swing.tree.TreePath parentPath = nodePath.getParentPath();
    Object kid = nodePath.getLastPathComponent();
    final int[] childInd = new int[] { getIndexOfChild(parentPath.getLastPathComponent(),
        kid) };
    final Object[] kids = new Object[] { kid };
    if (javax.swing.SwingUtilities.isEventDispatchThread())
    {
      java.awt.EventQueue.invokeLater(new Runnable()
      {
        public void run()
        {
          fireTreeNodesChanged(this, parentPath.getPath(), childInd, kids);
        }
      });
    }
    else
      fireTreeNodesChanged(this, parentPath.getPath(), childInd, kids);
  }

  public javax.swing.tree.TreePath getPathToNode(Object toMe)
  {
    // There's 3 special cases here.
    if (toMe == ROOT)
      return new javax.swing.tree.TreePath(new Object[] { ROOT });
    else if (toMe == stragglers)
      return new javax.swing.tree.TreePath(new Object[] { ROOT, toMe });
    else if (stragglers.contains(toMe))
      return new javax.swing.tree.TreePath(new Object[] { ROOT, stragglers, toMe });

    java.util.Set roots = new java.util.HashSet(java.util.Arrays.asList(getRoots()));
    java.util.ArrayList tempPath = new java.util.ArrayList();
    Widget currKid;
    int kidIndex;
    if (toMe instanceof TreeNodeDuplicate)
    {
      currKid = ((TreeNodeDuplicate) toMe).getSource();
      kidIndex = ((TreeNodeDuplicate) toMe).getNum();
    }
    else
    {
      currKid = (Widget) toMe;
      kidIndex = 0;
    }
    tempPath.add(0, toMe);
    while (!roots.contains(currKid))
    {
      Widget[] parents = getParents(currKid);
      if (parents == null || kidIndex >= parents.length || tempPath.contains(parents[kidIndex]))
      {
        // This could happen with concurrent modifications going on
        return null;
      }
      tempPath.add(0, parents[kidIndex]);
      currKid = parents[kidIndex];
      kidIndex = 0;
    }
    tempPath.add(0, ROOT);
    return new javax.swing.tree.TreePath(tempPath.toArray());
  }

  public int getType(){ return view; }

  private static final java.util.Comparator widgetNameSorter =
      new java.util.Comparator()
  {
    public int compare(Object o1, Object o2)
    {
      Widget w1 = (Widget) o1;
      Widget w2 = (Widget) o2;
      if (w1 == w2)
        return 0;
      if (w1 == null) return 1;
      if (w2 == null) return -1;
      // Sort by type first, and then by name
      if (w1.type() != w2.type())
        return ((int) w1.type()) - ((int) w2.type());
      return w1.getName().compareToIgnoreCase(w2.getName());
    }
  };

  protected int view;
  protected OracleTree treeUI;
  protected java.util.Set allObjects = new java.util.HashSet();
  protected java.util.Set accountedFor = new java.util.HashSet();
  protected java.util.Vector stragglers = new java.util.Vector();
  protected Widget[] rootCache;
  protected Object rootCacheLock = new Object();
  protected java.util.HashMap duplicateHash;
  private boolean refreshSubmitted = false;
  protected javax.swing.event.EventListenerList listenerList;
  private UIManager uiMgr;
}
