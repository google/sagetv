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

public class DynamicToolbar extends javax.swing.JComponent
{
  public static final java.awt.Color GLOBAL_BACKGROUND_COLOR = java.awt.Color.lightGray;
  public static final java.awt.Color GLOBAL_FOREGROUND_COLOR = java.awt.Color.black;
  public DynamicToolbar(UIManager inUIMgr)
  {
    super();
    uiMgr = inUIMgr;
    setBorder(javax.swing.BorderFactory.createLineBorder(GLOBAL_FOREGROUND_COLOR));

    setLayout(new java.awt.GridBagLayout());

    gbc = new java.awt.GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gbc.ipadx = 0;
    gbc.ipady = 0;
    gbc.insets = new java.awt.Insets(0, 0, 0, 0);
    gbc.anchor = java.awt.GridBagConstraints.CENTER;
    gbc.fill = java.awt.GridBagConstraints.VERTICAL;

    for (byte i = 0; i <= Widget.MAX_TYPE_NUM; i++)
    {
      WidgetCreateButton currButton = new WidgetCreateButton(i);
      currButton.setToolTipText(Widget.TYPES[i]);
      add(currButton, gbc);
      gbc.gridy++;
    }
    gbc.weighty = 1;
    add(javax.swing.Box.createVerticalStrut(64), gbc);
    gbc.gridy++;

    javax.swing.JLabel copy = new javax.swing.JLabel(new javax.swing.ImageIcon(
        ImageUtils.fullyLoadImage("images/studio/Copy16.gif")));
    new java.awt.dnd.DropTarget(copy, java.awt.dnd.DnDConstants.ACTION_MOVE,
        new java.awt.dnd.DropTargetListener()
    {
      public void dragEnter(java.awt.dnd.DropTargetDragEvent evt)
      {
        dragOver(evt);
      }

      public void dragExit(java.awt.dnd.DropTargetEvent evt)
      {
      }

      public void dragOver(java.awt.dnd.DropTargetDragEvent evt)
      {
        if (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_MOVE)
        {
          if (OracleTree.isWidgetKeyDrag(evt))
          {
            evt.acceptDrag(java.awt.dnd.DnDConstants.ACTION_MOVE);
            return;
          }
          else if (OracleTree.isMultipleWidgetKeyDrag(evt))
          {
            evt.acceptDrag(java.awt.dnd.DnDConstants.ACTION_MOVE);
            return;
          }
        }
        else
          evt.rejectDrag();
      }

      public void drop(java.awt.dnd.DropTargetDropEvent evt)
      {
        if (!evt.isLocalTransfer())
        {
          evt.rejectDrop();
          return;
        }

        evt.acceptDrop(java.awt.dnd.DnDConstants.ACTION_MOVE);
        // Get the data that's being transferred.
        if (OracleTree.isWidgetKeyDrag(evt))
        {
          Widget transferValue = (Widget) WidgetTransfer.getTransferData(uiMgr, evt.getTransferable());
          Widget newWidg = uiMgr.getModuleGroup().klone(transferValue);
          uiMgr.getStudio().pushWidgetOp(new StudioFrame.WidgetOp(true, newWidg));
        }
        else if (OracleTree.isMultipleWidgetKeyDrag(evt))
        {
          // References to Widgets don't get deleted if dropped in the trash
          java.util.Vector undoList = new java.util.Vector();
          Widget[] dropwidgs;
          Object[] dropObjs = null;
          dropObjs = (Object[]) WidgetTransfer.getTransferData(uiMgr, evt.getTransferable());
          dropwidgs = new Widget[dropObjs.length];
          for (int i = 0; i < dropObjs.length; i++)
          {
            if (dropObjs[i] instanceof TreeNodeDuplicate)
              dropwidgs[i] = ((TreeNodeDuplicate) dropObjs[i]).getSource();
            else
              dropwidgs[i] = (Widget) dropObjs[i];
          }

          java.util.Map widgCloneMap = new java.util.HashMap();
          java.util.Set alreadyKloned = new java.util.HashSet();
          for (int i = 0; i < dropObjs.length; i++)
          {
            if (dropObjs[i] instanceof TreeNodeDuplicate)
            {
              if (!alreadyKloned.contains(dropwidgs[i]))
                widgCloneMap.put(dropwidgs[i], dropwidgs[i]);
            }
            else
            {
              Widget newWidg = uiMgr.getModuleGroup().klone(dropwidgs[i]);
              widgCloneMap.put(dropwidgs[i], newWidg);
              alreadyKloned.add(dropwidgs[i]);
              undoList.add(new StudioFrame.WidgetOp(true, newWidg));
            }
          }
          for (int i = 0; i < dropwidgs.length; i++)
          {
            // Check if there's any parents in the new set of widgets for this one
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
          }
          uiMgr.getStudio().pushWidgetOps(undoList);
        }
        evt.dropComplete(true);
        uiMgr.getStudio().refreshTree();
      }

      public void dropActionChanged(java.awt.dnd.DropTargetDragEvent evt)
      {
        if (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_MOVE)
          evt.acceptDrag(java.awt.dnd.DnDConstants.ACTION_MOVE);
        else
          evt.rejectDrag();
      }
    }, true);
    copy.setToolTipText("Duplicate");
    add(copy, gbc);

    gbc.gridy++;
    add(javax.swing.Box.createVerticalGlue(), gbc);
    gbc.gridy++;

    javax.swing.JLabel destroy = new javax.swing.JLabel(new javax.swing.ImageIcon(
        ImageUtils.fullyLoadImage("images/studio/Destroy.gif")));
    new java.awt.dnd.DropTarget(destroy, java.awt.dnd.DnDConstants.ACTION_MOVE,
        new java.awt.dnd.DropTargetListener()
    {
      public void dragEnter(java.awt.dnd.DropTargetDragEvent evt)
      {
        dragOver(evt);
      }

      public void dragExit(java.awt.dnd.DropTargetEvent evt)
      {
      }

      public void dragOver(java.awt.dnd.DropTargetDragEvent evt)
      {
        if (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_MOVE)
        {
          if (OracleTree.isWidgetKeyDrag(evt))
          {
            evt.acceptDrag(java.awt.dnd.DnDConstants.ACTION_MOVE);
            return;
          }
          else if (OracleTree.isMultipleWidgetKeyDrag(evt))
          {
            evt.acceptDrag(java.awt.dnd.DnDConstants.ACTION_MOVE);
            return;
          }
        }
        else
          evt.rejectDrag();
      }

      public void drop(java.awt.dnd.DropTargetDropEvent evt)
      {
        if (!evt.isLocalTransfer())
        {
          evt.rejectDrop();
          return;
        }

        evt.acceptDrop(java.awt.dnd.DnDConstants.ACTION_MOVE);
        // Get the data that's being transferred.
        if (OracleTree.isWidgetKeyDrag(evt))
        {
          Widget transferValue = (Widget) WidgetTransfer.getTransferData(uiMgr, evt.getTransferable());
          uiMgr.getStudio().pushWidgetOps(uiMgr.getStudio().
              removeWidgetsWithUndo(new Widget[] { transferValue }, null));
        }
        else if (OracleTree.isMultipleWidgetKeyDrag(evt))
        {
          // References to Widgets don't get deleted if dropped in the trash
          java.util.Vector killList = new java.util.Vector();
          Object[] transferValues = (Object[]) WidgetTransfer.getTransferData(uiMgr, evt.getTransferable());
          for (int i = 0; i < transferValues.length; i++)
          {
            if (transferValues[i] instanceof Widget)
            {
              killList.add(transferValues[i]);
            }
          }
          uiMgr.getStudio().pushWidgetOps(uiMgr.getStudio().
              removeWidgetsWithUndo((Widget[]) killList.toArray(new Widget[0]), null));
        }
        evt.dropComplete(true);
        uiMgr.getStudio().refreshTree();
      }

      public void dropActionChanged(java.awt.dnd.DropTargetDragEvent evt)
      {
        if (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_MOVE)
          evt.acceptDrag(java.awt.dnd.DnDConstants.ACTION_MOVE);
        else
          evt.rejectDrag();
      }
    }, true);
    destroy.setToolTipText("Delete");
    add(destroy, gbc);
  }

  private class WidgetCreateButton extends javax.swing.JLabel implements java.awt.dnd.DragGestureListener,
  java.awt.dnd.DragSourceListener//, java.awt.dnd.DropTargetListener
  {
    public WidgetCreateButton(byte inType)
    {
      super(uiMgr.getIcon(Widget.TYPES[inType]));
      type = inType;

      // Setup the drag source
      new OracleMouseDragGestureRecognizer(java.awt.dnd.DragSource.getDefaultDragSource(),
          this, true ? java.awt.dnd.DnDConstants.ACTION_MOVE :
            java.awt.dnd.DnDConstants.ACTION_MOVE, this);
    }

    public void dragGestureRecognized(java.awt.dnd.DragGestureEvent evt)
    {
      // We only support the move operation
      if (evt.getDragAction() != java.awt.dnd.DnDConstants.ACTION_MOVE)
      {
        return;
      }
      evt.startDrag(uiMgr.getCursor(Widget.TYPES[type] + "NoDropCursor"),
          new WidgetTransfer(type), this);
    }

    public void dragDropEnd(java.awt.dnd.DragSourceDropEvent evt)
    {
    }

    public void dragEnter(java.awt.dnd.DragSourceDragEvent evt)
    {
      evt.getDragSourceContext().setCursor(null); // This gets rid of flickering
      evt.getDragSourceContext().setCursor(uiMgr.getCursor(Widget.TYPES[type] +
          (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_COPY ? "Copy" :
            (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_LINK ? "Link" : "")) + "Cursor"));
      //evt.getDragSourceContext().setCursor(uiMgr.getCursor(type + "Cursor"));
    }

    public void dragExit(java.awt.dnd.DragSourceEvent evt)
    {
      evt.getDragSourceContext().setCursor(null); // This gets rid of flickering
      evt.getDragSourceContext().setCursor(uiMgr.getCursor(Widget.TYPES[type] + "NoDropCursor"));
    }

    public void dragOver(java.awt.dnd.DragSourceDragEvent evt)
    {
      evt.getDragSourceContext().setCursor(null); // This gets rid of flickering
      evt.getDragSourceContext().setCursor(uiMgr.getCursor(Widget.TYPES[type] +
          (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_COPY ? "Copy" :
            (evt.getDropAction() == java.awt.dnd.DnDConstants.ACTION_LINK ? "Link" : "")) + "Cursor"));
      //evt.getDragSourceContext().setCursor(uiMgr.getCursor(type + "Cursor"));
    }

    public void dropActionChanged(java.awt.dnd.DragSourceDragEvent evt)
    {
      // We'll get a dragOver if it's actually on something valid, we can't know that here so just
      // don't do anything.
    }

    private byte type;
  }

  private java.awt.GridBagConstraints gbc;
  private UIManager uiMgr;
}
