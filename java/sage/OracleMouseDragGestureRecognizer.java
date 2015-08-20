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

public class OracleMouseDragGestureRecognizer extends java.awt.dnd.MouseDragGestureRecognizer
{
  protected static final int motionThreshold = 5;
  protected static final int ButtonMask = java.awt.event.InputEvent.BUTTON1_MASK;
  protected static final int ModMask =
      java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK;

  public OracleMouseDragGestureRecognizer(java.awt.dnd.DragSource ds, java.awt.Component c,
      int act, java.awt.dnd.DragGestureListener dgl)
  {
    this(ds, c, act, dgl, ButtonMask | ModMask);
  }
  public OracleMouseDragGestureRecognizer(java.awt.dnd.DragSource ds, java.awt.Component c,
      int act, java.awt.dnd.DragGestureListener dgl, int inInitiationMods)
  {
    super(ds, c, act, dgl);
    initiationMods = inInitiationMods;
    leftOK = ((initiationMods & java.awt.event.InputEvent.BUTTON1_MASK) ==
        java.awt.event.InputEvent.BUTTON1_MASK);
    rightOK = ((initiationMods & java.awt.event.InputEvent.BUTTON3_MASK) ==
        java.awt.event.InputEvent.BUTTON3_MASK);
  }


  protected int mapDragOperationFromModifiers(java.awt.event.MouseEvent e)
  {
    int mods = e.getModifiers();

    if ((mods & ~initiationMods) != 0)
    {
      return java.awt.dnd.DnDConstants.ACTION_NONE;
    }

    if ((((mods & java.awt.event.InputEvent.BUTTON1_MASK) !=
        java.awt.event.InputEvent.BUTTON1_MASK) || !leftOK) &&
        (((mods & java.awt.event.InputEvent.BUTTON3_MASK) !=
        java.awt.event.InputEvent.BUTTON3_MASK) || !rightOK))
    {
      return java.awt.dnd.DnDConstants.ACTION_NONE;
    }

    if ((mods &= ModMask) == 0)
    {
      return java.awt.dnd.DnDConstants.ACTION_MOVE;
    }
    else if (mods == ModMask)
    {
      return java.awt.dnd.DnDConstants.ACTION_LINK;
    }
    else if (mods == java.awt.event.InputEvent.CTRL_MASK)
    {
      return java.awt.dnd.DnDConstants.ACTION_COPY;
    }
    else
    {
      return java.awt.dnd.DnDConstants.ACTION_NONE;
    }
  }

  public void mouseClicked(java.awt.event.MouseEvent e)
  {
  }

  public void mousePressed(java.awt.event.MouseEvent e)
  {
    events.clear();

    if (mapDragOperationFromModifiers(e) != java.awt.dnd.DnDConstants.ACTION_NONE)
    {
      appendEvent(e);
    }
  }

  public void mouseReleased(java.awt.event.MouseEvent e)
  {
    inDrag = false;
    events.clear();
  }

  public void mouseEntered(java.awt.event.MouseEvent e)
  {
    events.clear();
  }

  public void mouseExited(java.awt.event.MouseEvent e)
  {
    if (!events.isEmpty())
    {
      // gesture pending
      int dragAction = mapDragOperationFromModifiers(e);

      if (dragAction != java.awt.dnd.DnDConstants.ACTION_NONE)
      {
        appendEvent(e);
        fireDragGestureRecognized(dragAction,
            ((java.awt.event.MouseEvent)getTriggerEvent()).getPoint());
        inDrag = false;
      }
      else if (!inDrag)
      {
        events.clear();
      }
    }
  }

  public void mouseDragged(java.awt.event.MouseEvent e)
  {
    inDrag = true;
    if (!events.isEmpty())
    {
      // gesture pending
      int dop = mapDragOperationFromModifiers(e);

      if (dop == java.awt.dnd.DnDConstants.ACTION_NONE)
      {
        return;
      }

      java.awt.event.MouseEvent trigger = (java.awt.event.MouseEvent)events.get(0);

      java.awt.Point origin = trigger.getPoint();
      java.awt.Point current = e.getPoint();

      int dx = Math.abs(origin.x - current.x);
      int dy = Math.abs(origin.y - current.y);

      if (dx > motionThreshold || dy > motionThreshold)
      {
        fireDragGestureRecognized(dop, ((java.awt.event.MouseEvent)getTriggerEvent()).getPoint());
        inDrag = false;
      }
      else
      {
        appendEvent(e);
      }
    }
  }

  public void mouseMoved(java.awt.event.MouseEvent e)
  {
  }

  protected void fireDragGestureRecognized(int dragOp, java.awt.Point loc)
  {
    java.awt.event.MouseEvent firstEvent = (java.awt.event.MouseEvent) events.remove(0);
    events.add(0, new java.awt.event.MouseEvent((java.awt.Component) firstEvent.getSource(),
        firstEvent.getID(),
        firstEvent.getWhen(),
        ((firstEvent.getModifiers() & java.awt.event.MouseEvent.BUTTON1_MASK) ==
        java.awt.event.MouseEvent.BUTTON1_MASK) ? java.awt.event.MouseEvent.BUTTON1_MASK :
          java.awt.event.MouseEvent.BUTTON3_MASK, firstEvent.getX(),
          firstEvent.getY(), firstEvent.getClickCount(), false));
    super.fireDragGestureRecognized(dragOp, loc);
  }

  private boolean inDrag = false;
  private int initiationMods;
  private boolean leftOK;
  private boolean rightOK;
}
