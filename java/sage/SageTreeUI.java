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

public class SageTreeUI extends javax.swing.plaf.metal.MetalTreeUI
{
  public static javax.swing.plaf.ComponentUI createUI(javax.swing.JComponent x)
  {
    return new SageTreeUI();
  }

  protected java.awt.event.MouseListener createMouseListener()
  {
    return new MouseHandler();
  }

  public class MouseHandler extends java.awt.event.MouseAdapter
  {
    private javax.swing.tree.TreePath alreadySelectedPath = null;

    /**
     * Handles mouse press.  If path is already selected,
     * saves path and applies selection/deselection when mouse
     * is released (if released above saved path).
     */
    public void mousePressed(java.awt.event.MouseEvent e)
    {
      if ((getTree() != null) && getTree().isEnabled())
      {
        getTree().requestFocus();
        javax.swing.tree.TreePath path =
            getClosestPathForLocation(getTree(), e.getX(), e.getY());

        if (path != null)
        {
          java.awt.Rectangle bounds = getPathBounds(getTree(), path);

          if (bounds == null || e.getY() > (bounds.y + bounds.height))
          {
            return;
          }

          if (javax.swing.SwingUtilities.isLeftMouseButton(e))
          {
            checkForClick(path, e.getX(), e.getY());
          }

          int x = e.getX();

          if (x > bounds.x)
          {
            if ((x <= (bounds.x + bounds.width)) && (getTree().isEditing() || !startEdit(path, e)))
            {
              if (getTree().isPathSelected(path))
              {
                alreadySelectedPath = path;
              }
              else
              {
                alreadySelectedPath = null;
                selectPath(path, e);
              }
            }
          }
        }
      }
    }

    /**
     * Handles mouse release.  If released above previously
     * selected path, applies selection/deselection.
     */
    public void mouseReleased(java.awt.event.MouseEvent e)
    {
      if ((getTree() != null) && getTree().isEnabled())
      {
        javax.swing.tree.TreePath path =
            getClosestPathForLocation(getTree(), e.getX(), e.getY());

        if (path != null)
        {
          java.awt.Rectangle bounds = getPathBounds(getTree(), path);

          if (bounds == null || e.getY() > (bounds.y + bounds.height))
          {
            return;
          }

          int x = e.getX();

          if ((x > bounds.x) && (x <= (bounds.x + bounds.width)) &&
              (alreadySelectedPath == path))
          {
            selectPath(path, e);
          }
        }
      }
      alreadySelectedPath = null;
    }
  }

  protected javax.swing.JTree getTree()
  {
    return tree;
  }

  protected void checkForClick(javax.swing.tree.TreePath path, int mouseX, int mouseY)
  {
    checkForClickInExpandControl(path, mouseX, mouseY);
  }

  protected boolean startEdit(javax.swing.tree.TreePath path, java.awt.event.MouseEvent e)
  {
    return startEditing(path,e);
  }

  protected void selectPath(javax.swing.tree.TreePath path, java.awt.event.MouseEvent e)
  {
    selectPathForEvent(path, e);
  }

  protected void paintVerticalLine(java.awt.Graphics g, javax.swing.JComponent c, int x, int top,
      int bottom)
  {
    if (top < bottom)
    {
      g.drawLine(x, top, x, bottom);
    }
  }

  protected void paintVerticalPartOfLeg(java.awt.Graphics g, java.awt.Rectangle clipBounds,
      java.awt.Insets insets, javax.swing.tree.TreePath path)
  {
    int lineX;
    lineX = ((path.getPathCount() + depthOffset) *
        totalChildIndent) - getRightChildIndent() + insets.left;

    int clipLeft = clipBounds.x;
    int clipRight = clipBounds.x + (clipBounds.width - 1);

    if ((lineX > clipLeft) && (lineX < clipRight))
    {
      int clipTop = clipBounds.y;
      int clipBottom = clipBounds.y + clipBounds.height;
      java.awt.Rectangle parentBounds = getPathBounds(tree, path);
      java.awt.Rectangle lastChildBounds = getPathBounds(tree, getLastChildPath(path));

      if (lastChildBounds == null)
      {
        // This shouldn't happen, but if the model is modified
        // in another thread it is possible for this to happen.
        // Swing isn't multithreaded, but I'll add this check in
        // anyway.
        return;
      }

      int top;

      if (parentBounds == null)
      {
        top = Math.max(insets.top + getVerticalLegBuffer(), clipTop);
      }
      else
      {
        top = Math.max(parentBounds.y + parentBounds.height +
            getVerticalLegBuffer(), clipTop);
      }
      if ((path.getPathCount() == 1) && !isRootVisible())
      {
        javax.swing.tree.TreeModel model = getModel();

        if (model != null)
        {
          Object root = model.getRoot();

          if (model.getChildCount(root) > 0)
          {
            parentBounds = getPathBounds(tree,
                path.pathByAddingChild(model.getChild(root, 0)));
            if (parentBounds != null)
            {
              top = Math.max(insets.top + getVerticalLegBuffer(),
                  parentBounds.y + parentBounds.height / 2);
            }
          }
        }
      }

      int bottom = Math.min(lastChildBounds.y +
          10/*(lastChildBounds.height / 2)*/, clipBottom);

      g.setColor(getHashColor());
      paintVerticalLine(g, tree, lineX, top, bottom);
    }
  }

  protected void paintHorizontalPartOfLeg(java.awt.Graphics g, java.awt.Rectangle clipBounds,
      java.awt.Insets insets, java.awt.Rectangle bounds,
      javax.swing.tree.TreePath path, int row, boolean isExpanded,
      boolean hasBeenExpanded, boolean isLeaf)
  {
    int clipLeft = clipBounds.x;
    int clipRight = clipBounds.x + (clipBounds.width - 1);
    int clipTop = clipBounds.y;
    int clipBottom = clipBounds.y + (clipBounds.height - 1);
    int lineY = bounds.y + 10; //bounds.height / 2;

    // Offset leftX from parents indent.
    int leftX = bounds.x - getRightChildIndent();
    int nodeX = bounds.x - getHorizontalLegBuffer();

    if ((lineY > clipTop) && (lineY < clipBottom) && (nodeX > clipLeft) &&
        (leftX < clipRight))
    {
      leftX = Math.max(leftX, clipLeft);
      nodeX = Math.min(nodeX, clipRight);

      g.setColor(getHashColor());
      paintHorizontalLine(g, tree, lineY, leftX, nodeX);
    }
  }

  /**
   * Paints the expand (toggle) part of a row. The reciever should
   * NOT modify <code>clipBounds</code>, or <code>insets</code>.
   */
  protected void paintExpandControl(java.awt.Graphics g, java.awt.Rectangle clipBounds,
      java.awt.Insets insets, java.awt.Rectangle bounds, javax.swing.tree.TreePath path,
      int row, boolean isExpanded, boolean hasBeenExpanded, boolean isLeaf)
  {
    Object value = path.getLastPathComponent();

    // Draw icons if not a leaf and either hasn't been loaded,
    // or the model child count is > 0.
    if (!isLeaf && (!hasBeenExpanded || treeModel.getChildCount(value) > 0))
    {
      int middleXOfKnob = bounds.x - (getRightChildIndent() - 1);
      int middleYOfKnob = bounds.y + 10; //(bounds.height / 2);

      if (isExpanded)
      {
        javax.swing.Icon expandedIcon = getExpandedIcon();
        if (expandedIcon != null)
        {
          drawCentered(tree, g, expandedIcon, middleXOfKnob, middleYOfKnob);
        }
      }
      else
      {
        javax.swing.Icon collapsedIcon = getCollapsedIcon();
        if (collapsedIcon != null)
        {
          drawCentered(tree, g, collapsedIcon, middleXOfKnob, middleYOfKnob);
        }
      }
    }
  }

  public void recalculateSizing()
  {
    if (treeState != null)
    {
      treeState.invalidateSizes();
    }
    updateSize();
  }

  public int getRowX(int depth)
  {
    return totalChildIndent * (depth + depthOffset);
  }
}
