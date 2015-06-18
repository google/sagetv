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

public class DebugUITreeModel implements javax.swing.tree.TreeModel
{
  protected static final Object ROOT = new Object();

  protected DebugUITreeModel(UIManager inUIMgr)
  {
    uiMgr = inUIMgr;
    listenerList = new javax.swing.event.EventListenerList();
  }

  public void setTreeUI(DebugUITree inTreeUI)
  {
    treeUI = inTreeUI;
  }

  private int getNumPseudoKids(Object foo)
  {
    if (!(foo instanceof ZPseudoComp))
      return 0;
    ZPseudoComp zk = (ZPseudoComp) foo;
    return zk.getDebugUIComps().size();
    /*		int rv = 0;
		int nk = zk.getNumKids();
		for (int i = 0; i < nk; i++)
			if (zk.getComponents()[i] instanceof ZPseudoComp)
				rv++;
		return rv;*/
  }

  private Object getPseudoKid(Object foo, int x)
  {
    if (!(foo instanceof ZPseudoComp) || x < 0)
      return null;
    ZPseudoComp zk = (ZPseudoComp) foo;
    return zk.getDebugUIComps().get(x);
    /*		int nk = zk.getNumKids();
		for (int i = 0; i < nk; i++)
			if (zk.getComponents()[i] instanceof ZPseudoComp)
			{
				if (x == 0)
				{
					return (ZPseudoComp) zk.getComponents()[i];
				}
				x--;
			}
		return null;*/
  }

  // Part of TreeModel interface
  public Object getChild(Object parent, int index)
  {
    Object rv;
    if (parent == ROOT)
    {
      if (index > 0)
        rv = null;
      else
        rv = uiMgr.getCurrUI().getUI();
    }
    else
      rv = getPseudoKid(parent, index);
    return rv;
  }

  // Part of TreeModel interface
  public int getChildCount(Object inParent)
  {
    int rv;
    if (inParent == ROOT)
    {
      rv = 1;
    }
    else
      rv = getNumPseudoKids(inParent);
    return rv;
  }

  // Part of TreeModel interface
  public int getIndexOfChild(Object parent, Object child)
  {
    int rv = -1;
    if (parent == ROOT)
    {
      if (child == uiMgr.getCurrUI().getUI())
        rv = 0;
      else
        rv = -1;
    }
    else if (parent instanceof ZPseudoComp)
    {
      ZPseudoComp zk = (ZPseudoComp) parent;
      java.util.Vector v = zk.getDebugUIComps();
      rv = v.indexOf(child);
      /*int numKids = zk.getNumKids();
			int rv = 0;
			for (int i = 0; i < numKids; i++)
			{
				if (zk.getComponents()[i] == child)
					return rv;
				if (zk.getComponents()[i] instanceof ZPseudoComp)
					rv++;
			}
			return -1;*/
    }
    return rv;
  }

  // Part of TreeModel interface
  public Object getRoot()
  {
    return ROOT;
  }

  // Part of TreeModel interface
  public boolean isLeaf(Object node)
  {
    return getChildCount(node) == 0;
  }

  // Part of TreeModel interface
  public void valueForPathChanged(javax.swing.tree.TreePath path, Object newValue)
  {
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

  private synchronized void safeRefreshTree()
  {
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
    javax.swing.tree.TreePath selectedPath = treeUI.getSelectionPath();
    fireTreeStructureChanged(this, new Object[] { ROOT }, null, null);
    while ((expanded != null) && expanded.hasMoreElements())
      treeUI.expandPath((javax.swing.tree.TreePath) expanded.nextElement());
    if (myScroll != null)
      myScroll.getViewport().setViewPosition(scroll);
    if (selectedPath != null)
      treeUI.setSelectionPath(selectedPath);
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

  protected DebugUITree treeUI;
  protected UIManager uiMgr;
  private boolean refreshSubmitted = false;
  protected javax.swing.event.EventListenerList listenerList;
}
