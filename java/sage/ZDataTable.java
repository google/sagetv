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

public class ZDataTable extends ZPseudoComp
{
  public static final String ROW_HEADER = "RowHeader";
  public static final String COL_HEADER = "ColHeader";
  public static final String CELL = "Cell";
  public static final String NOOK = "Nook";
  public static final String EMPTY_TABLE = "EmptyTable";
  public static final int VERTICAL_DIMENSION = 1;
  public static final int HORIZONTAL_DIMENSION = 2;
  public static final int BOTH_DIMENSIONS = 3;
  public static final boolean ENABLE_VAR_SIZE_TABLES = true;

  private static final int MAX_DATA_LENGTH = 128;

  public ZDataTable(Widget inWidg)
  {
    this(inWidg, null, null);
  }
  public ZDataTable(Widget inWidg, java.util.ArrayList defaultThemes, Catbert.Context inContext)
  {
    this(inWidg, defaultThemes, inContext, null);
  }
  public ZDataTable(Widget inWidg, java.util.ArrayList defaultThemes, Catbert.Context inContext,
      java.util.ArrayList inParentActions)
  {
    super(inWidg, defaultThemes, inContext, inParentActions);
    numColsPerPage = propWidg.getIntProperty(Widget.NUM_COLS, 0, null, this);
    numRowsPerPage = propWidg.getIntProperty(Widget.NUM_ROWS, 0, null, this);
    dimensions = propWidg.getIntProperty(Widget.DIMENSIONS, 0, null, this);
    wrapping = propWidg.getIntProperty(Widget.TABLE_WRAPPING, 0, null, this);

    //reuseMap = new java.util.HashMap();
    reuseRowMap = new java.util.HashMap();
    reuseColMap = new java.util.HashMap();
    reuseCellMap = new java.util.HashMap();

    if (propWidg.hasProperty(Widget.AUTO_REPEAT_ACTION))
    {
      float autoscrollRange = propWidg.getFloatProperty(Widget.AUTO_REPEAT_ACTION, 0, null, this);
      autoscroll = new AutoscrollData();
      autoscroll.autoscrollRange = autoscrollRange;
      autoscroll.autoscrollPeriod = uiMgr.getLong("ui/autoscroll_period", 250);
    }


    // Initialize our paging variables
    relatedContext.setLocal("IsFirstPage", Boolean.FALSE);
    relatedContext.setLocal("IsFirstHPage", Boolean.FALSE);
    relatedContext.setLocal("IsFirstVPage", Boolean.FALSE);
    relatedContext.setLocal("IsLastPage", Boolean.FALSE);
    relatedContext.setLocal("IsLastVPage", Boolean.FALSE);
    relatedContext.setLocal("IsLastHPage", Boolean.FALSE);
    relatedContext.setLocal("NumRows", new Integer(0));
    relatedContext.setLocal("NumCols", new Integer(0));
    relatedContext.setLocal("NumPages", new Integer(0));
    relatedContext.setLocal("NumColsPerPage", new Integer(numColsPerPage));
    relatedContext.setLocal("NumRowsPerPage", new Integer(numRowsPerPage));
    relatedContext.setLocal("HScrollIndex", new Integer(1));
    relatedContext.setLocal("VScrollIndex", new Integer(1));

    freeformTable = Catbert.evalBool(relatedContext.getLocal("FreeformCellSize"));
    alwaysPageOnScroll = Catbert.evalBool(relatedContext.getLocal("AlwaysPageOnScroll"));

    if (Sage.PERF_ANALYSIS)
      perfTime = Sage.time();
    // Add any table theme widgets
    // NOTE Do these last for now, because we're using it for pagination controls
    // and we need them to be at the top of the Z order so they get mouse events
    java.util.HashMap alreadyChecked = Pooler.getPooledHashMap();
    Widget[] widgKids = widg.contents();
    for (int i = 0; i < widgKids.length; i++)
      addChildrenFromWidgetChain(widgKids[i], i, alreadyChecked, null);
    Widget tableTheme = getWidgetChildFromWidgetChain(Widget.TABLE, currTheme, defaultThemes);
    if (tableTheme != null)
    {
      Widget[] contThemeKids = tableTheme.contents();
      for (int i = 0; i < contThemeKids.length; i++)
        addChildrenFromWidgetChain(contThemeKids[i], -(contThemeKids.length - i), alreadyChecked, null);
    }
    if (Sage.PERF_ANALYSIS)
    {
      perfTime = Sage.time() - perfTime;
      if (perfTime > Sage.UI_BUILD_THRESHOLD_TIME)
      {
        // Check if we are the bottleneck by comparing us to the timing of the children
        for (int i = 0; i < numKids; i++)
        {
          if (kids[i] instanceof ZPseudoComp && perfTime - kids[i].perfTime > Sage.UI_BUILD_THRESHOLD_TIME)
          {
            System.out.println("UI BUILD PERF self=" + perfTime + " child=" + kids[i].perfTime + " selfWidg=" + widg + " childWidg=" + ((ZPseudoComp)kids[i]).widg);
          }
        }
      }
    }

    // Return all of the Vector values in the Map to the Pooler since that's where they all came from
    java.util.Iterator walker = alreadyChecked.values().iterator();
    while (walker.hasNext())
    {
      Object nextie = walker.next();
      if (nextie instanceof java.util.ArrayList)
        Pooler.returnPooledArrayList((java.util.ArrayList) nextie);
    }
    Pooler.returnPooledHashMap(alreadyChecked);
    alreadyChecked = null;
  }

  public int getTableDimensions()
  {
    return dimensions;
  }

  public boolean action(UserEvent evt)
  {
    int evtType = evt.getType();
    if (uiMgr.getTracer() != null) uiMgr.getTracer().traceEvent(this, UserEvent.getPrettyEvtName(evtType),
        evt.getIRCode(), evt.getKeyCode(), evt.getKeyModifiers(), evt.getKeyChar());


    /*
     * Check our map for an action
     * All components can directly override any UserEvent. This is where that is done.
     */
    Widget ueListenWidg = getUEListenWidget(evtType);
    Catbert.ExecutionPosition ep = null;
    if (ueListenWidg != null)
    {
      if (uiMgr.getTracer() != null) uiMgr.getTracer().traceListener(this, ueListenWidg);
      Widget[] listenKids = ueListenWidg.contents();
      for (int i = 0; i < listenKids.length; i++)
      {
        if (listenKids[i].isProcessChainType())
        {
          if ((ep = processChain(listenKids[i], relatedContext, null, this, false)) != null)
          {
            ep.addToStack(listenKids[i]);
            ep.addToStackFinal(ueListenWidg);
            return true;
          }
        }
      }
      return true;
    }

    switch (evtType)
    {
      case UserEvent.PAGE_UP:
      case UserEvent.CHANNEL_UP:
        if (pageUp(false))
        {
          notifyOfTransition(evt.getType());
          return true;
        }
        else
          return propogateAction(evt);
      case UserEvent.PAGE_DOWN:
      case UserEvent.CHANNEL_DOWN:
        if (pageDown(false))
        {
          notifyOfTransition(evt.getType());
          return true;
        }
        else
          return propogateAction(evt);
      case UserEvent.PAGE_RIGHT:
      case UserEvent.FF:
        if (pageRight(false))
        {
          notifyOfTransition(evt.getType());
          return true;
        }
        else
          return propogateAction(evt);
      case UserEvent.PAGE_LEFT:
      case UserEvent.REW:
        if (pageLeft(false))
        {
          notifyOfTransition(evt.getType());
          return true;
        }
        else
          return propogateAction(evt);
    }

    ZComp currSelNode = getFocusOwner(false);
    if (currSelNode != null && evt.isDirectionalType())
    {
      java.awt.geom.Rectangle2D.Float selBounds = currSelNode.getTrueBoundsf();
      if (focusTargetRect != null && focusTargetRect.intersects(selBounds))
      {
        java.awt.geom.Rectangle2D.Float newSelBounds = (java.awt.geom.Rectangle2D.Float)focusTargetRect.createIntersection(selBounds);
        // JAK 8/12/05 - Sometimes this overlap exists due to floating point errors so make sure its not almost 0
        if (newSelBounds.width > 0.01 && newSelBounds.height > 0.01)
        {
          selBounds = newSelBounds;
        }
      }
      if (UserEvent.isUpEvent(evtType) || UserEvent.isDownEvent(evtType))
      {
        selBounds.y = currSelNode.getTrueYf();
        selBounds.height = currSelNode.getHeightf();
      }
      else if (UserEvent.isLeftEvent(evtType) || UserEvent.isRightEvent(evtType))
      {
        selBounds.x = currSelNode.getTrueXf();
        selBounds.width = currSelNode.getWidthf();
      }

      // Make adjustments so edges don't perfectly align
      if (selBounds.width > 0.02f && selBounds.height > 0.02f)
      {
        selBounds.x += 0.01f;
        selBounds.y += 0.01f;
        selBounds.width -= 0.02f;
        selBounds.height -= 0.02f;
      }

      java.util.ArrayList focusKids = new java.util.ArrayList();
      // When our scroller is inside the table we don't want to send focus to it aside from when explicitly told to
      if (gridPanel != null && uiMgr.isXBMCCompatible())
        gridPanel.addFocusableChildrenToList(focusKids);
      else
        addFocusableChildrenToList(focusKids);

      // Use the outcode for the rectangle and find the nearest one in that direction
      ZComp minDistNode = null;
      float minDist = Float.MAX_VALUE;
      float minDist2 = Float.MAX_VALUE;
      for (int i = 0; i < focusKids.size(); i++)
      {
        ZPseudoComp currKid = (ZPseudoComp) focusKids.get(i);
        if (currKid != currSelNode && currKid.shouldTakeEvents())
        {
          java.awt.geom.Rectangle2D.Float kidBounds = currKid.getTrueBoundsf();
          // Make adjustments so edges don't perfectly align
          if (kidBounds.width > 0.02f && kidBounds.height > 0.02f)
          {
            kidBounds.x += 0.01f;
            kidBounds.y += 0.01f;
            kidBounds.width -= 0.02f;
            kidBounds.height -= 0.02f;
          }

          int currOut = selBounds.outcode(kidBounds.x + kidBounds.width/2,
              kidBounds.y + kidBounds.height/2);
          if (UserEvent.isUpEvent(evtType))
          {
            if ((currOut & java.awt.geom.Rectangle2D.OUT_TOP) == java.awt.geom.Rectangle2D.OUT_TOP)
            {
              float testDist = Math.abs(selBounds.y - (kidBounds.y + kidBounds.height));
              float testDist2;
              if (selBounds.getMinX() < kidBounds.getMaxX() &&
                  selBounds.getMaxX() > kidBounds.getMinX())
                testDist2 = 0;
              else
              {
                testDist2 = Math.min(Math.abs(selBounds.x - (kidBounds.x + kidBounds.width)),
                    Math.abs(selBounds.x + selBounds.width - kidBounds.x));
                if (testDist2 == 0) // edge-aligned
                  testDist2 = 1;
              }
              if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
              {
                minDist = testDist;
                minDist2 = testDist2;
                minDistNode = currKid;
              }
            }
          }
          else if (UserEvent.isRightEvent(evtType))
          {
            if ((currOut & java.awt.geom.Rectangle2D.OUT_RIGHT) == java.awt.geom.Rectangle2D.OUT_RIGHT)
            {
              float testDist = Math.abs(selBounds.x + selBounds.width - kidBounds.x);
              float testDist2;
              if (selBounds.getMinY() < kidBounds.getMaxY() &&
                  selBounds.getMaxY() > kidBounds.getMinY())
                testDist2 = 0;
              else
              {
                testDist2 = Math.min(Math.abs(selBounds.y - (kidBounds.y + kidBounds.height)),
                    Math.abs(selBounds.y + selBounds.height - kidBounds.y));
                if (testDist2 == 0) // edge-aligned
                  testDist2 = 1;
              }
              if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
              {
                minDist = testDist;
                minDist2 = testDist2;
                minDistNode = currKid;
              }
            }
          }
          else if (UserEvent.isDownEvent(evtType))
          {
            if ((currOut & java.awt.geom.Rectangle2D.OUT_BOTTOM) == java.awt.geom.Rectangle2D.OUT_BOTTOM)
            {
              float testDist = Math.abs(selBounds.y + selBounds.height - kidBounds.y);
              float testDist2;
              if (selBounds.getMinX() < kidBounds.getMaxX() &&
                  selBounds.getMaxX() > kidBounds.getMinX())
                testDist2 = 0;
              else
              {
                testDist2 = Math.min(Math.abs(selBounds.x - (kidBounds.x + kidBounds.width)),
                    Math.abs(selBounds.x + selBounds.width - kidBounds.x));
                if (testDist2 == 0) // edge-aligned
                  testDist2 = 1;
              }
              if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
              {
                minDist = testDist;
                minDist2 = testDist2;
                minDistNode = currKid;
              }
            }
          }
          else if (UserEvent.isLeftEvent(evtType))
          {
            if ((currOut & java.awt.geom.Rectangle2D.OUT_LEFT) == java.awt.geom.Rectangle2D.OUT_LEFT)
            {
              float testDist = Math.abs(selBounds.x - (kidBounds.x + kidBounds.width));
              float testDist2;
              if (selBounds.getMinY() < kidBounds.getMaxY() &&
                  selBounds.getMaxY() > kidBounds.getMinY())
                testDist2 = 0;
              else
              {
                testDist2 = Math.min(Math.abs(selBounds.y - (kidBounds.y + kidBounds.height)),
                    Math.abs(selBounds.y + selBounds.height - kidBounds.y));
                if (testDist2 == 0) // edge-aligned
                  testDist2 = 1;
              }
              if (testDist2 < minDist2 || (testDist2 == minDist2 && testDist < minDist))
              {
                minDist = testDist;
                minDist2 = testDist2;
                minDistNode = currKid;
              }
            }
          }
        }
      }

      if (minDistNode != null)
      {
        boolean letItScroll = false;
        if (freeformTable && gridPanel != null)
        {
          // Autoscrolling for components that aren't fully visible
          java.awt.geom.Rectangle2D.Float rectBounds = minDistNode.getTrueBoundsf();
          java.awt.geom.Rectangle2D.Float gridBounds = gridPanel.getTrueBoundsf();
          // Give a fudge factor of 5 pixels
          if (dimensions == VERTICAL_DIMENSION)
          {
            if (rectBounds.y - gridBounds.y < -5 || (rectBounds.y + rectBounds.height - 5 > gridBounds.height + gridBounds.y))
              letItScroll = true;
          }
          else if (dimensions == HORIZONTAL_DIMENSION)
          {
            if (rectBounds.x - gridBounds.x < -5 || (rectBounds.x + rectBounds.width - 5 > gridBounds.width + gridBounds.x))
              letItScroll = true;
          }
        }
        else if (gridPanel != null)
        {
          // Autoscrolling for components that aren't fully visible
          java.awt.geom.Rectangle2D.Float rectBounds = minDistNode.getTrueBoundsf();
          // Give a fudge factor of 5 pixels
          if ((dimensions & VERTICAL_DIMENSION) != 0 && (UserEvent.isUpEvent(evtType) || UserEvent.isDownEvent(evtType)))
          {
            if (rectBounds.y + rectBounds.height - 5 > reality.getUIMaxY() || rectBounds.y < reality.getUIMinY() - 5)
              letItScroll = true;
          }
          if ((dimensions & HORIZONTAL_DIMENSION) != 0 && (UserEvent.isLeftEvent(evtType) || UserEvent.isRightEvent(evtType)))
          {
            if (hTime)
            {
              // Special case here to deal with EPG scrolling, we only want to do it if less than 68% of it is visible
              if (rectBounds.x + rectBounds.width*0.68f > reality.getUIMaxX() || rectBounds.x + 0.32f*rectBounds.width < reality.getUIMinX())
                letItScroll = true;
            }
            else if (rectBounds.x + rectBounds.width - 5 > reality.getUIMaxX() || rectBounds.x < reality.getUIMinX() - 5)
              letItScroll = true;
          }
        }
        if (!letItScroll)
        {
          selectNode(minDistNode);
          ZPseudoComp tempParent = minDistNode.getTopPseudoParent();
          tempParent.updateFocusTargetRect(evt.getType());
          // We also need to keep the Table's focus rect updated
          updateFocusTargetRect(evt.getType());
          notifyOfTransition(evt.getType());
          return true;
        }
      }
    }

    // NARFLEX - 12/4/09 - We changed this so that it doesn't need to have focus to process these. That way we can scroll tables by a unit
    // without having to shift focus to it.
    if (UserEvent.isUpEvent(evtType) || UserEvent.SCROLL_UP == evt.getType())
    {
      if (alwaysPageOnScroll ? pageUp(true) : unitUp(true))
      {
        notifyOfTransition(evt.getType());
        return true;
      }
    }
    else if (UserEvent.isDownEvent(evtType) || UserEvent.SCROLL_DOWN == evt.getType())
    {
      if (alwaysPageOnScroll ? pageDown(true) : unitDown(true))
      {
        notifyOfTransition(evt.getType());
        return true;
      }
    }
    else if (UserEvent.isRightEvent(evtType) || UserEvent.SCROLL_RIGHT == evt.getType())
    {
      if (alwaysPageOnScroll ? pageRight(true) : unitRight(true))
      {
        notifyOfTransition(evt.getType());
        return true;
      }
    }
    else if (UserEvent.isLeftEvent(evtType) || UserEvent.SCROLL_LEFT == evt.getType())
    {
      if (alwaysPageOnScroll ? pageLeft(true) : unitLeft(true))
      {
        notifyOfTransition(evt.getType());
        return true;
      }
    }
    return propogateAction(evt);
  }

  protected boolean isTableRegionVisible(java.awt.geom.Rectangle2D.Float childRect)
  {
    if (freeformTable && gridPanel != null)
    {
      java.awt.geom.Rectangle2D.Float gridBounds = gridPanel.getTrueBoundsf();
      // Give a fudge factor of 5 pixels
      if (dimensions == VERTICAL_DIMENSION)
      {
        if (childRect.y - gridBounds.y < -5)
          return false;
        else if (childRect.y + childRect.height - 5 > gridBounds.height + gridBounds.y)
          return false;
      }
      else if (dimensions == HORIZONTAL_DIMENSION)
      {
        if (childRect.x - gridBounds.x < -5)
          return false;
        else if (childRect.x + childRect.width - 5 > gridBounds.width + gridBounds.x)
          return false;
      }
    }
    return true;
  }

  static long getTimeFromObject(Object o)
  {
    if (o == null)
      return 0;
    else if (o instanceof Number)
      return ((Number) o).longValue();
    else if (o instanceof java.util.Date)
      return ((java.util.Date) o).getTime();
    else
      return Long.parseLong(o.toString());
  }

  public boolean setFocusByValue(String varName, Object focusValue, boolean visCheck)  // returns true if it took the focus
  {
    if (!passesConditional() && (!Catbert.evalBool(relatedContext.getLocal("AllowHiddenFocus")))) return false;

    for (int i = 0; i < numKids; i++)
    {
      if (kids[i] instanceof ZPseudoComp && ((ZPseudoComp) kids[i]).setFocusByValue(varName, focusValue, true))
      {
        return true;
      }
    }

    // If there wasn't a child that took the focus, we might need to page to make them
    // visible. This is only true if the variable name matches one of the table component names.
    java.awt.geom.Rectangle2D.Float foci = null;
    boolean madeChanges = false;
    if (colHeaderPanel != null || rowHeaderPanel != null)
    {
      if (colHeaderPanel != null && varName.equals(colHeaderPanel.widg.getName()))
      {
        if (hTime)
        {
          // For time we have to do a range comparison
          long targetTime = getTimeFromObject(focusValue);
          long timebase = ((Long) baseColumnData[0]).longValue();
          long colDur = (((Long) baseColumnData[1]).longValue() - timebase)/numColsPerPage;

          foci = getTrueFocusRect();
          madeChanges = true;
          if (hunitIndex * colDur + timebase > targetTime)
          {
            hunitIndex = (int) (((targetTime - (targetTime % colDur)) - timebase) / colDur);
          }
          else
          {
            hunitIndex = (int) (((targetTime - (targetTime % colDur)) - timebase) / colDur) - numColsPerPage + 1;
          }
          buildUIForData(false, true);
        }
        else
        {
          for (int i = 0; i < columnData.length; i++)
          {
            if (columnData[i] == focusValue || (columnData[i] != null && columnData[i].equals(focusValue)))
            {
              // Found the target index
              foci = getTrueFocusRect();
              madeChanges = true;
              if (hunitIndex > i)
                hunitIndex = i;
              else
              {
                hunitIndex = i - numColsPerPage + 1;
                if (hunitIndex < 0)
                  hunitIndex += hSpan;
              }
              buildUIForData(false, true);
              break;
            }
          }
        }
      }
      else if (rowHeaderPanel != null && varName.equals(rowHeaderPanel.widg.getName()))
      {
        if (vTime)
        {
          // For time we have to do a range comparison
          long targetTime = getTimeFromObject(focusValue);
          long timebase = ((Long) baseRowData[0]).longValue();
          long rowDur = (((Long) baseRowData[1]).longValue() - timebase)/numRowsPerPage;

          foci = getTrueFocusRect();
          madeChanges = true;
          if (vunitIndex * rowDur + timebase > targetTime)
          {
            // Scroll up to the time
            vunitIndex = (int) (((targetTime - (targetTime % rowDur)) - timebase) / rowDur);
          }
          else
          {
            // Scroll down to the time
            vunitIndex = (int) (((targetTime - (targetTime % rowDur)) - timebase) / rowDur) - numRowsPerPage + 1;
          }
          buildUIForData(false, true);
        }
        else
        {
          // See if the value is in the row data anywhere
          for (int i = 0; i < rowData.length; i++)
          {
            if (rowData[i] == focusValue || (rowData[i] != null && rowData[i].equals(focusValue)))
            {
              // Found the target index
              foci = getTrueFocusRect();
              madeChanges = true;
              int oldIndex = vunitIndex;
              if (vunitIndex > i)
                vunitIndex = i;
              else
              {
                vunitIndex = i - numRowsPerPage + 1;
                if (vunitIndex < 0)
                  vunitIndex += vSpan;
              }
              // DOESN'T WORK RIGHT YET!!!! NEEDS TO BE FIXED AND ADDED TO THE REST OF THE SETFOCUS/ENSUREVIS METHODS
              /*							if ((oldIndex > vunitIndex && oldIndex - vunitIndex > vunitIndex + vSpan - oldIndex) ||
								(oldIndex < vunitIndex && oldIndex + vSpan - vunitIndex < vunitIndex - oldIndex))
							{
								// it moved up
								int motion = Math.abs(Math.min(oldIndex - vunitIndex, oldIndex + vSpan - vunitIndex));
								if (motion <= numRowsPerPage)
									setupScrollAnimation(0, -gridPanel.boundsf.height * motion / numRowsPerPage);
							}
							else
							{
								// it moved down
								int motion = Math.abs(Math.min(vunitIndex - oldIndex, vunitIndex + vSpan - oldIndex));
								if (motion <= numRowsPerPage)
									setupScrollAnimation(0, gridPanel.boundsf.height * motion / numRowsPerPage);
							}*/
              buildUIForData(false, true);
              break;
            }
          }
        }
      }
    }
    else if (gridPanel != null)
    {
      if (varName.equals(gridPanel.widg.getName()))
      {
        for (int i = 0; i < tableData.length; i++)
        {
          if (tableData[i] == focusValue || (tableData[i] != null && tableData[i].equals(focusValue)))
          {
            // Found the target index
            foci = getTrueFocusRect();
            madeChanges = true;
            int currDataIndex = vunitIndex*numColsPerPage + hunitIndex*numRowsPerPage;
            if (currDataIndex > i)
            {
              if (dimensions == VERTICAL_DIMENSION)
                vunitIndex = i/numColsPerPage;
              else
                hunitIndex = i/numRowsPerPage;
            }
            else
            {
              if (dimensions == VERTICAL_DIMENSION)
              {
                vunitIndex = i/numColsPerPage - numRowsPerPage + 1;
                if (vunitIndex < 0)
                  vunitIndex += vSpan;
              }
              else
              {
                hunitIndex = i/numRowsPerPage - numColsPerPage + 1;
                if (hunitIndex < 0)
                  hunitIndex += hSpan;
              }
            }
            buildUIForData(false, true);
            break;
          }
        }
      }
    }
    if (!madeChanges && !passesConditional() && uiMgr.allowHiddenFocus() && Catbert.evalBool(relatedContext.getLocal("AllowHiddenFocus")))
    {
      Object myValue = relatedContext.safeLookup(varName);
      if (myValue == focusValue || (myValue != null && myValue.equals(focusValue)))
      {
        // XBMC may be trying to set the focus in a component that isn't visible yet; so we construct the UI so it can happen
        evaluate(true, true);
        if (setDefaultFocus())
          return true;

      }
    }
    if (madeChanges)
    {
      // We paged, so we should now be able to find the default value we hope...
      for (int i = 0; i < numKids; i++)
      {
        if (kids[i] instanceof ZPseudoComp && ((ZPseudoComp) kids[i]).setFocusByValue(varName, focusValue, false))
          return true;
      }
      renderWithTrueFocusRect(foci);
    }
    return false;
  }
  public boolean ensureVisibilityForValue(String varName, Object focusValue, int displayIndex)
  {
    if (!passesConditional()) return false;

    for (int i = 0; i < numKids; i++)
    {
      if (kids[i] instanceof ZPseudoComp && ((ZPseudoComp) kids[i]).ensureVisibilityForValue(varName, focusValue,
          displayIndex))
        return true;
    }

    // NOTE: We had optimized this below so that we only did the update on the data but this caused an issue in the EPG
    // where it was expecting this call to clear up an initial focus issue so we had to put that back to always redoing the focus rect
    if (colHeaderPanel != null || rowHeaderPanel != null)
    {
      if (colHeaderPanel != null && varName.equals(colHeaderPanel.widg.getName()))
      {
        if (hTime)
        {
          // For time we have to do a range comparison
          long targetTime = getTimeFromObject(focusValue);
          long timebase = ((Long) baseColumnData[0]).longValue();
          long colDur = (((Long) baseColumnData[1]).longValue() - timebase)/numColsPerPage;

          java.awt.geom.Rectangle2D.Float foci = getTrueFocusRect();
          int newhunitIndex = (int) (((targetTime - (targetTime % colDur)) - timebase) / colDur) - displayIndex;
          if (newhunitIndex != hunitIndex)
          {
            hunitIndex = newhunitIndex;
            buildUIForData(false, true);
          }
          renderWithTrueFocusRect(foci);
          return true;
        }
        else
        {
          for (int i = 0; i < columnData.length; i++)
          {
            if (columnData[i] == focusValue || (columnData[i] != null && columnData[i].equals(focusValue)))
            {
              // Found the target index
              java.awt.geom.Rectangle2D.Float foci = getTrueFocusRect();
              int newhunitIndex = i - displayIndex;
              if (newhunitIndex != hunitIndex)
              {
                hunitIndex = newhunitIndex;
                buildUIForData(false, true);
              }
              renderWithTrueFocusRect(foci);
              return true;
            }
          }
        }
      }
      else if (rowHeaderPanel != null && varName.equals(rowHeaderPanel.widg.getName()))
      {
        if (vTime)
        {
          // For time we have to do a range comparison
          long targetTime = getTimeFromObject(focusValue);
          long timebase = ((Long) baseRowData[0]).longValue();
          long rowDur = (((Long) baseRowData[1]).longValue() - timebase)/numRowsPerPage;

          java.awt.geom.Rectangle2D.Float foci = getTrueFocusRect();
          int newvunitIndex = (int) (((targetTime - (targetTime % rowDur)) - timebase) / rowDur) - displayIndex;
          if (newvunitIndex != vunitIndex)
          {
            vunitIndex = newvunitIndex;
            buildUIForData(false, true);
          }
          renderWithTrueFocusRect(foci);
          return true;
        }
        else
        {
          // See if the value is in the row data anywhere
          for (int i = 0; i < rowData.length; i++)
          {
            if (rowData[i] == focusValue || (rowData[i] != null && rowData[i].equals(focusValue)))
            {
              // Found the target index
              java.awt.geom.Rectangle2D.Float foci = getTrueFocusRect();
              int newvunitIndex = i - displayIndex;
              if (newvunitIndex != vunitIndex)
              {
                vunitIndex = newvunitIndex;
                buildUIForData(false, true);
              }
              renderWithTrueFocusRect(foci);
              return true;
            }
          }
        }
      }
    }
    else if (gridPanel != null)
    {
      if (varName.equals(gridPanel.widg.getName()))
      {
        for (int i = 0; i < tableData.length; i++)
        {
          if (tableData[i] == focusValue || (tableData[i] != null && tableData[i].equals(focusValue)))
          {
            // Found the target index
            java.awt.geom.Rectangle2D.Float foci = getTrueFocusRect();
            int currDataIndex = vunitIndex*numColsPerPage + hunitIndex*numRowsPerPage;
            int oldvunitIndex = vunitIndex;
            int oldhunitIndex = hunitIndex;
            if (dimensions == VERTICAL_DIMENSION)
            {
              vunitIndex = i/numColsPerPage - displayIndex;
              if (vunitIndex < 0)
              {
                if ((wrapping & VERTICAL_DIMENSION) != 0)
                  vunitIndex += vSpan;
                else
                  vunitIndex = 0;
              }
            }
            else
            {
              hunitIndex = i/numRowsPerPage - displayIndex;
              if (hunitIndex < 0)
              {
                if ((wrapping & HORIZONTAL_DIMENSION) != 0)
                  hunitIndex += hSpan;
                else
                  hunitIndex = 0;
              }
            }
            if (oldvunitIndex != vunitIndex ||
                oldhunitIndex != hunitIndex)
            {
              buildUIForData(false, true);
            }
            renderWithTrueFocusRect(foci);
            return true;
          }
        }
      }
    }
    return false;
  }

  public int getNumColsPerPage() { return numColsPerPage; }
  public int getNumRowsPerPage() { return numRowsPerPage; }
  public int getVScrollIndex() { return vunitIndex; }
  public int getHScrollIndex() { return hunitIndex; }

  public boolean isFirstVPage() { return !vTime && ((wrapping & VERTICAL_DIMENSION) == 0 || vSpan <= numRowsPerPage) &&
      (vunitIndex == 0 || vSpan == 1 || dimensions == 0); }
  public boolean isFirstHPage() { return !hTime && ((wrapping & HORIZONTAL_DIMENSION) == 0 || hSpan <= numColsPerPage) &&
      (hunitIndex == 0 || hSpan == 1 || dimensions == 0); }
  public boolean isLastVPage()
  {
    if (vTime || ((wrapping & VERTICAL_DIMENSION) != 0 && vSpan > numRowsPerPage)) return false;
    if (vSpan == 1 || dimensions == 0) return true;
    if (dimensions == VERTICAL_DIMENSION)
      return ((vunitIndex + numRowsPerPage) * numColsPerPage >= vSpan);
    else
      return (vunitIndex + numRowsPerPage >= vSpan);
  }
  public boolean isLastHPage()
  {
    if (hTime || ((wrapping & HORIZONTAL_DIMENSION) != 0 && hSpan > numColsPerPage)) return false;
    if (hSpan == 1 || dimensions == 0) return true;
    if (dimensions == HORIZONTAL_DIMENSION)
      return ((hunitIndex + numColsPerPage) * numRowsPerPage >= hSpan);
    else
      return (hunitIndex + numColsPerPage >= hSpan);
  }
  private int getLastVIndex()
  {
    int rv;
    if (dimensions == VERTICAL_DIMENSION)
      rv = (int)Math.ceil(((float)vSpan)/numColsPerPage) - numRowsPerPage;
    else
      rv = (vSpan - numRowsPerPage);
    return Math.max(rv, 0);
  }
  private int getLastHIndex()
  {
    int rv;
    if (dimensions == HORIZONTAL_DIMENSION)
      rv = (int)Math.ceil(((float)hSpan)/numRowsPerPage) - numColsPerPage;
    else
      rv = (hSpan - numColsPerPage);
    return Math.max(rv, 0);
  }

  private boolean pageUp(boolean flipFocusRect)
  {
    if (!isFirstVPage())
    {
      // Adjust the amount we scroll if we are larger than the display area
      int rowPerScroll = numRowsPerPage;
      if (gridPanel != null)
      {
        float tyf = gridPanel.getTrueYf();
        if (tyf < reality.getUIMinY() - 5)
          rowPerScroll--;
        if (tyf + gridPanel.getHeightf() - 5 > reality.getUIMaxY())
          rowPerScroll--;
      }
      java.awt.geom.Rectangle2D.Float foci = getTrueFocusRect();
      float oldFocusHeight = foci.height/2;
      if (flipFocusRect)
      {
        ZComp oldFocuser = getFocusOwner(false);
        if (oldFocuser != null)
          oldFocusHeight = oldFocuser.getHeightf();
      }
      int oldIndex = vunitIndex;
      vunitIndex -= rowPerScroll;
      float scrollAmount = 0;
      if (!vTime)
      {
        if ((wrapping & VERTICAL_DIMENSION) == 0)
        {
          vunitIndex = Math.max(0, vunitIndex);
          if (gridPanel != null)
            setupScrollAnimation(0, scrollAmount = -gridPanel.boundsf.height * (oldIndex - vunitIndex) / numRowsPerPage);
        }
        else
        {
          vunitIndex = (vunitIndex < 0) ? (vunitIndex + vSpan/(dimensions == VERTICAL_DIMENSION ? numColsPerPage : 1)) : vunitIndex;
          if (gridPanel != null)
            setupScrollAnimation(0, scrollAmount = -gridPanel.boundsf.height * rowPerScroll / numRowsPerPage);
        }
      }
      else if (gridPanel != null)
        setupScrollAnimation(0, scrollAmount = -gridPanel.boundsf.height);
      if (flipFocusRect)
      {
        foci.y = foci.y + (-scrollAmount) - oldFocusHeight;
      }
      buildUIForData(false, true);
      renderWithTrueFocusRect(foci);
      appendToDirty(true);
      return true;
    }
    else
      return false;
  }

  private boolean unitUp(boolean fixFocus)
  {
    if (!isFirstVPage())
    {
      java.awt.geom.Rectangle2D.Float foci = fixFocus ? getTrueFocusRect() : null;
      vunitIndex--;
      if (!vTime)
      {
        if ((wrapping & VERTICAL_DIMENSION) == 0)
          vunitIndex = Math.max(0, vunitIndex);
        else
          vunitIndex = (vunitIndex < 0) ? (vunitIndex + vSpan/(dimensions == VERTICAL_DIMENSION ? numColsPerPage : 1)) : vunitIndex;
      }
      if (gridPanel != null)
      {
        if (freeformTable)
          setupScrollAnimation(0, -gridPanel.kids[0].boundsf.height);
        else
          setupScrollAnimation(0, -gridPanel.boundsf.height/getTotalTableWeightV());
      }
      buildUIForData(false, true);
      if (fixFocus)
        renderWithTrueFocusRect(foci);
      appendToDirty(true);
      return true;
    }
    /*else if (!isLastVPage() && propWidg.getBooleanProperty(Widget.WRAP_VERTICAL_NAVIGATION, null, this))
		{
			// Scrolling w/ wrap navigation, scroll to the bottom and select the last item
			vunitIndex = getLastVIndex();
			buildUIForData(false, true);
			for (int i = gridPanel.numKids - 1; i >= 0; i--)
			{
				if (gridPanel.kids[i].setDefaultFocus())
					break;
			}
			appendToDirty(true);
			return true;
		}*/
    else
      return false;
  }

  private boolean pageDown(boolean flipFocusRect)
  {
    if (!isLastVPage())
    {
      // Adjust the amount we scroll if we are larger than the display area
      int rowPerScroll = numRowsPerPage;
      if (gridPanel != null)
      {
        float tyf = gridPanel.getTrueYf();
        if (tyf < reality.getUIMinY() - 5)
          rowPerScroll--;
        if (tyf + gridPanel.getHeightf() - 5 > reality.getUIMaxY())
          rowPerScroll--;
      }
      java.awt.geom.Rectangle2D.Float foci = getTrueFocusRect();
      float oldFocusHeight = foci.height;
      if (flipFocusRect)
      {
        ZComp oldFocuser = getFocusOwner(false);
        if (oldFocuser != null)
          oldFocusHeight = oldFocuser.getHeightf();
      }
      float scrollAmount = 0;
      int oldIndex = vunitIndex;
      vunitIndex += rowPerScroll;
      if ((wrapping & VERTICAL_DIMENSION) != 0 && vSpan > 0)
      {
        vunitIndex %= vSpan;
        if (gridPanel != null)
          setupScrollAnimation(0, scrollAmount = gridPanel.boundsf.height * rowPerScroll / numRowsPerPage);
      }
      else if (isLastVPage())
      {
        vunitIndex = getLastVIndex();
        if (gridPanel != null)
          setupScrollAnimation(0, scrollAmount = gridPanel.boundsf.height * (vunitIndex - oldIndex) / numRowsPerPage);
      }
      else if (gridPanel != null)
        setupScrollAnimation(0, scrollAmount = gridPanel.boundsf.height * rowPerScroll / numRowsPerPage);

      if (flipFocusRect)
      {
        foci.y = foci.y - scrollAmount + oldFocusHeight;;
      }
      buildUIForData(false, true);
      renderWithTrueFocusRect(foci);
      appendToDirty(true);
      return true;
    }
    else
      return false;
  }

  private boolean unitDown(boolean fixFocus)
  {
    if (!isLastVPage())
    {
      java.awt.geom.Rectangle2D.Float foci = fixFocus ? getTrueFocusRect() : null;
      vunitIndex++;
      if ((wrapping & VERTICAL_DIMENSION) != 0 && vSpan > 0)
        vunitIndex %= vSpan;
      if (gridPanel != null)
      {
        if (freeformTable)
          setupScrollAnimation(0, gridPanel.kids[0].boundsf.height + gridPanel.padY);
        else
          setupScrollAnimation(0, gridPanel.boundsf.height/getTotalTableWeightV() + gridPanel.padY);
      }
      buildUIForData(false, true);
      if (fixFocus)
        renderWithTrueFocusRect(foci);
      appendToDirty(true);
      return true;
    }
    /*else if (!isFirstVPage() && propWidg.getBooleanProperty(Widget.WRAP_VERTICAL_NAVIGATION, null, this))
		{
			// Scrolling w/ wrap navigation, scroll to the top and select the first item
			vunitIndex = 0;
			buildUIForData(false, true);
			for (int i = 0; i < gridPanel.numKids; i++)
			{
				if (gridPanel.kids[i].setDefaultFocus())
					break;
			}
			appendToDirty(true);
			return true;
		}*/
    else
      return false;
  }

  private boolean pageRight(boolean flipFocusRect)
  {
    if (!isLastHPage())
    {
      // Adjust the amount we scroll if we are larger than the display area
      int colPerScroll = numColsPerPage;
      if (gridPanel != null)
      {
        float txf = gridPanel.getTrueXf();
        if (txf < reality.getUIMinX() - 5)
          colPerScroll--;
        if (txf + gridPanel.getWidthf() - 5 > reality.getUIMaxX())
          colPerScroll--;
      }
      java.awt.geom.Rectangle2D.Float foci = getTrueFocusRect();
      float oldFocusWidth = foci.width;
      if (flipFocusRect)
      {
        ZComp oldFocuser = getFocusOwner(false);
        if (oldFocuser != null)
          oldFocusWidth = oldFocuser.getWidthf();
      }
      float scrollAmount = 0;
      int oldIndex = hunitIndex;
      hunitIndex += colPerScroll;
      if ((wrapping & HORIZONTAL_DIMENSION) != 0 && hSpan > 0)
      {
        hunitIndex %= hSpan;
        if (gridPanel != null)
          setupScrollAnimation(scrollAmount = gridPanel.boundsf.width * colPerScroll / numColsPerPage, 0);
      }
      else if (isLastHPage())
      {
        hunitIndex = getLastHIndex();
        if (gridPanel != null)
          setupScrollAnimation(scrollAmount = gridPanel.boundsf.width * (hunitIndex - oldIndex) / numColsPerPage, 0);
      }
      else if (gridPanel != null)
        setupScrollAnimation(scrollAmount = gridPanel.boundsf.width * (hunitIndex - oldIndex) / numColsPerPage, 0);
      if (flipFocusRect)
      {
        foci.x = foci.x - scrollAmount + oldFocusWidth;
      }
      buildUIForData(false, true);
      renderWithTrueFocusRect(foci);
      appendToDirty(true);
      return true;
    }
    else
      return false;
  }

  private boolean pageLeft(boolean flipFocusRect)
  {
    if (!isFirstHPage())
    {
      // Adjust the amount we scroll if we are larger than the display area
      int colPerScroll = numColsPerPage;
      if (gridPanel != null)
      {
        float txf = gridPanel.getTrueXf();
        if (txf < reality.getUIMinX() - 5)
          colPerScroll--;
        if (txf + gridPanel.getWidthf() - 5 > reality.getUIMaxX())
          colPerScroll--;
      }
      java.awt.geom.Rectangle2D.Float foci = getTrueFocusRect();
      float oldFocusWidth = foci.width;
      if (flipFocusRect)
      {
        ZComp oldFocuser = getFocusOwner(false);
        if (oldFocuser != null)
          oldFocusWidth = oldFocuser.getWidthf();
      }
      float scrollAmount = 0;
      int oldIndex = hunitIndex;
      hunitIndex -= colPerScroll;
      if (!hTime)
      {
        if ((wrapping & HORIZONTAL_DIMENSION) == 0)
        {
          hunitIndex = Math.max(0, hunitIndex);
          if (gridPanel != null)
            setupScrollAnimation(scrollAmount = -gridPanel.boundsf.width * (oldIndex - hunitIndex) / numColsPerPage, 0);
        }
        else
        {
          hunitIndex = (hunitIndex < 0) ? (hunitIndex + hSpan/(dimensions == HORIZONTAL_DIMENSION ? numRowsPerPage : 1)) : hunitIndex;
          if (gridPanel != null)
            setupScrollAnimation(scrollAmount = -gridPanel.boundsf.width, 0);
        }
      }
      else if (gridPanel != null)
        setupScrollAnimation(scrollAmount = -gridPanel.boundsf.width * colPerScroll / numColsPerPage, 0);
      if (flipFocusRect)
      {
        foci.x = foci.x + (-scrollAmount) - oldFocusWidth;
      }
      buildUIForData(false, true);
      renderWithTrueFocusRect(foci);
      appendToDirty(true);
      return true;
    }
    else
      return false;
  }

  private boolean unitRight(boolean fixFocus)
  {
    if (!isLastHPage())
    {
      java.awt.geom.Rectangle2D.Float foci = fixFocus ? getTrueFocusRect() : null;
      hunitIndex++;
      if ((wrapping & HORIZONTAL_DIMENSION) != 0 && hSpan > 0)
        hunitIndex %= hSpan;
      if (gridPanel != null)
      {
        if (freeformTable)
          setupScrollAnimation(gridPanel.kids[0].boundsf.width, 0);
        else
          setupScrollAnimation(gridPanel.boundsf.width/getTotalTableWeightH(), 0);
      }
      buildUIForData(false, true);
      if (fixFocus)
        renderWithTrueFocusRect(foci);
      appendToDirty(true);
      return true;
    }
    /*else if (!isFirstHPage() && propWidg.getBooleanProperty(Widget.WRAP_HORIZONTAL_NAVIGATION, null, this))
		{
			hunitIndex = 0;
			buildUIForData(false, true);
			for (int i = 0; i < gridPanel.numKids; i++)
			{
				if (gridPanel.kids[i].setDefaultFocus())
					break;
			}
			appendToDirty(true);
			return true;
		}*/
    else
      return false;
  }

  private boolean unitLeft(boolean fixFocus)
  {
    if (!isFirstHPage())
    {
      java.awt.geom.Rectangle2D.Float foci = fixFocus ? getTrueFocusRect() : null;
      hunitIndex --;
      if (!hTime)
      {
        if ((wrapping & HORIZONTAL_DIMENSION) == 0)
          hunitIndex = Math.max(0, hunitIndex);
        else
          hunitIndex = (hunitIndex < 0) ? (hunitIndex + hSpan/(dimensions == HORIZONTAL_DIMENSION ? numRowsPerPage : 1)) : hunitIndex;
      }
      if (gridPanel != null)
      {
        if (freeformTable)
          setupScrollAnimation(-gridPanel.kids[0].boundsf.width, 0);
        else
          setupScrollAnimation(-gridPanel.boundsf.width/getTotalTableWeightH(), 0);
      }
      buildUIForData(false, true);
      if (fixFocus)
        renderWithTrueFocusRect(foci);
      appendToDirty(true);
      return true;
    }
    /*else if (!isLastVPage() && propWidg.getBooleanProperty(Widget.WRAP_HORIZONTAL_NAVIGATION, null, this))
		{
			hunitIndex = getLastHIndex();
			buildUIForData(false, true);
			for (int i = gridPanel.numKids - 1; i >= 0; i--)
			{
				if (gridPanel.kids[i].setDefaultFocus())
					break;
			}
			appendToDirty(true);
			return true;
		}*/
    else
      return false;
  }

  private void setupScrollAnimation(float moveX, float moveY)
  {
    if (reality.isIntegerPixels())
    {
      moveX = Math.round(moveX);
      moveY = Math.round(moveY);
    }
    if (uiMgr.areEffectsEnabled())
    {
      if (Sage.PERF_ANALYSIS)
        System.out.println("Starting setup of scroll animation for " + widg);
      if (gridPanel != null)
      {
        int scrollDuration = propWidg.getIntProperty(Widget.DURATION, uiMgr.getInt("ui/animation/table_scroll_duration", 300), null, this);
        if (scrollDuration <= 0)
          return;
        gridPanel.scrollTracker = null;
        gridPanel.cachedScrollOps = null;
        java.util.ArrayList gridScrollCache = new java.util.ArrayList();
        float gptx = getTrueXf();
        float gpty = getTrueYf();
        boolean linearScroll = Catbert.evalBool(relatedContext.safeLookup("LinearScrolling"));
        EffectTracker scrollOffTracker = new EffectTracker(this, 0, scrollDuration, EffectTracker.EASE_INOUT, linearScroll ? EffectTracker.SCALE_LINEAR : EffectTracker.SCALE_QUADRATIC);
        scrollOffTracker.setTranslationEffect(0, 0, -moveX, -moveY);
        scrollOffTracker.setInitialPositivity(false);
        scrollOffTracker.setPositivity(true);
        // This rectangle will clip the scrolling operations so things don't appear outside of the actual scrolling containers themselves
        java.awt.geom.Rectangle2D.Float effectClipRect = gridPanel.getTrueBoundsf();
        if (moveX != 0)
        {
          // Unbounded Y when doing horizontal scrolling
          effectClipRect.y = -2000;
          effectClipRect.height = 6000;
        }
        else
        {
          // Unbounded X when doing vertical scrolling
          effectClipRect.x = -2000;
          effectClipRect.width = 6000;
        }
        gridScrollCache.add(new RenderingOp(scrollOffTracker, effectClipRect, 0, 0));
        // This rectangle will clip the rendering operations below us so we don't get overlap when we apply the scroll effect.
        java.awt.geom.Rectangle2D.Float scrollClipRect = new java.awt.geom.Rectangle2D.Float(gptx, gpty, moveX != 0 ? Math.abs(moveX) : reality.getWidth(),
            moveY != 0 ? Math.abs(moveY) : reality.getHeight());
        // Narflex 10/7/11 - I added the max/mins below for when we have the case where the gridpanel is larger than the table itself and we need to
        // offset the old scrolling animation to account for that
        if (moveX != 0)
        {
          // Allow full height in the Y direction; but clip in the X direction at the overlap point
          if (moveX > 0)
            scrollClipRect.x = Math.max(gptx, gridPanel.getTrueXf());
          else
            scrollClipRect.x = Math.min(gptx + moveX + getWidthf(), gridPanel.getWidthf() + moveX + gridPanel.getTrueXf());
        }
        else
        {
          // Allow full width in the X direction; but clip in the Y direction at the overlap point
          if (moveY > 0)
            scrollClipRect.y = Math.max(gpty, gridPanel.getTrueYf());
          else
            scrollClipRect.y = Math.min(gpty + moveY + getHeightf(), gridPanel.getHeightf() + moveY + gridPanel.getTrueYf());
        }
        //				javax.vecmath.Matrix4f tempMat = MathUtils.createTranslateMatrix(gptx, gpty);
        if (Sage.PERF_ANALYSIS)
          System.out.println("Starting buildRenderingOps for grid scroll for " + widg);
        gridPanel.buildRenderingOps(gridScrollCache, scrollClipRect, 0xFFFFFF, 1.0f, gptx, gpty, RENDER_FLAG_SKIP_FOCUSED);
        if (Sage.PERF_ANALYSIS)
          System.out.println("DONE with buildRenderingOps for grid scroll for " + widg);
        gridScrollCache.add(new RenderingOp(null));
        EffectTracker newScrollTracker = new EffectTracker(this, 0, scrollDuration, EffectTracker.EASE_INOUT, linearScroll ? EffectTracker.SCALE_LINEAR : EffectTracker.SCALE_QUADRATIC);
        newScrollTracker.setTranslationEffect(moveX, moveY, 0, 0);
        newScrollTracker.setInitialPositivity(false);
        newScrollTracker.setPositivity(true);
        gridPanel.scrollTracker = new RenderingOp(newScrollTracker, effectClipRect, 0, 0);
        gridPanel.cachedScrollOps = gridScrollCache;
        if (moveX != 0 && colHeaderPanel != null)
        {
          colHeaderPanel.scrollTracker = null;
          colHeaderPanel.cachedScrollOps = null;
          java.util.ArrayList colScrollCache = new java.util.ArrayList();
          colScrollCache.add(new RenderingOp(scrollOffTracker, effectClipRect, 0, 0));
          // This rectangle will clip the rendering operations below us so we don't get overlap when we apply the scroll effect.
          if (moveX != 0)
          {
            // Allow full height in the Y direction; but clip in the X direction at the overlap point
            if (moveX > 0)
              scrollClipRect.x = Math.max(gptx, colHeaderPanel.getTrueXf());
            else
              scrollClipRect.x = Math.min(gptx + moveX + getWidthf(), colHeaderPanel.getWidthf() + moveX + colHeaderPanel.getTrueXf());
          }
          else
          {
            // Allow full width in the X direction; but clip in the Y direction at the overlap point
            if (moveY > 0)
              scrollClipRect.y = Math.max(gpty, colHeaderPanel.getTrueYf());
            else
              scrollClipRect.y = Math.min(gpty + moveY + getHeightf(), colHeaderPanel.getHeightf() + moveY + colHeaderPanel.getTrueYf());
          }
          if (Sage.PERF_ANALYSIS)
            System.out.println("Starting buildRenderingOps for col scroll for " + widg);
          colHeaderPanel.buildRenderingOps(colScrollCache, scrollClipRect, 0xFFFFFF, 1.0f, gptx, gpty, RENDER_FLAG_SKIP_FOCUSED);
          if (Sage.PERF_ANALYSIS)
            System.out.println("DONE with buildRenderingOps for col scroll for " + widg);
          colScrollCache.add(new RenderingOp(null));
          colHeaderPanel.scrollTracker = new RenderingOp(newScrollTracker, effectClipRect, 0, 0);
          colHeaderPanel.cachedScrollOps = colScrollCache;
        }
        else if (moveY != 0 && rowHeaderPanel != null)
        {
          rowHeaderPanel.scrollTracker = null;
          rowHeaderPanel.cachedScrollOps = null;
          java.util.ArrayList rowScrollCache = new java.util.ArrayList();
          rowScrollCache.add(new RenderingOp(scrollOffTracker, effectClipRect, 0, 0));
          // This rectangle will clip the rendering operations below us so we don't get overlap when we apply the scroll effect.
          if (moveX != 0)
          {
            // Allow full height in the Y direction; but clip in the X direction at the overlap point
            if (moveX > 0)
              scrollClipRect.x = Math.max(gptx, rowHeaderPanel.getTrueXf());
            else
              scrollClipRect.x = Math.min(gptx + moveX + getWidthf(), rowHeaderPanel.getWidthf() + moveX + rowHeaderPanel.getTrueXf());
          }
          else
          {
            // Allow full width in the X direction; but clip in the Y direction at the overlap point
            if (moveY > 0)
              scrollClipRect.y = Math.max(gpty, rowHeaderPanel.getTrueYf());
            else
              scrollClipRect.y = Math.min(gpty + moveY + getHeightf(), rowHeaderPanel.getHeightf() + moveY + rowHeaderPanel.getTrueYf());
          }
          if (Sage.PERF_ANALYSIS)
            System.out.println("Starting buildRenderingOps for row scroll for " + widg);
          rowHeaderPanel.buildRenderingOps(rowScrollCache, scrollClipRect, 0xFFFFFF, 1.0f, gptx, gpty, RENDER_FLAG_SKIP_FOCUSED);
          if (Sage.PERF_ANALYSIS)
            System.out.println("DONE with buildRenderingOps for row scroll for " + widg);
          rowScrollCache.add(new RenderingOp(null));
          rowHeaderPanel.scrollTracker = new RenderingOp(newScrollTracker, effectClipRect, 0, 0);
          rowHeaderPanel.cachedScrollOps = rowScrollCache;
        }
      }
      return;
    }
    if ((!uiMgr.getBoolean("ui/animation/smooth_scrolling_tables", true) || !uiMgr.areLayersEnabled()) && surfaceCache != null)
      return;
    // Make sure we don't already have a scrolling op in the pendingAnimations list. And if we do; then remove it
    // so we update with the last thing the user did.
    for (int i = 0; pendingAnimations != null && i < pendingAnimations.size(); i++)
    {
      RenderingOp aop = (RenderingOp) pendingAnimations.get(i);
      if (aop.isAnimationOp() && aop.anime.animType == RenderingOp.Animation.SCROLL)
      {
        pendingAnimations.remove(i--);
      }
    }
    boolean fastScroll = reality.isDoingScrollAnimation();
    RenderingOp rop = null;
    java.awt.geom.Rectangle2D.Float bounder = null;
    if (gridPanel != null)
      bounder = gridPanel.getInsetTrueBoundsf();
    if (moveX != 0)
    {
      // Horizontal scrolling
      // We scroll only the gridPanel and the colHeader if it exists
      if (bounder != null && colHeaderPanel != null)
        java.awt.Rectangle.union(bounder, colHeaderPanel.getInsetTrueBoundsf(), bounder);
    }
    else
    {
      // Vertical scrolling
      // We scroll only the gridPanel and the rowHeader if it exists
      if (bounder != null && rowHeaderPanel != null)
        java.awt.Rectangle.union(bounder, rowHeaderPanel.getInsetTrueBoundsf(), bounder);
    }
    if (bounder != null)
    {
      rop = new RenderingOp(surfaceCache, fastScroll ? "ScrollLinear" : "Scroll",
          uiMgr.getLong("ui/animation/table_scroll_duration", 300) / (fastScroll ?
              uiMgr.getInt("ui/animation/continuous_scroll_speedup_factor", 4) : 1), 0, bounder, getBGAlpha(), false);
      rop.anime.scrollVector = new float[] { moveX, moveY};
      if (pendingAnimations == null)
        pendingAnimations = new java.util.ArrayList();
      pendingAnimations.add(rop);
    }
  }

  // Returns true/false for conditional UI
  protected boolean evaluate(boolean doComps, boolean doData)
  {
    boolean rv = true;
    if (!doComps)
    {
      for (int i = 0; i < numKids; i++)
      {
        kids[i].evaluateTree(doComps, doData);
      }
      //return super.evaluate(doComps, doData);
    }
    else if (colHeaderPanel != null || rowHeaderPanel != null)
    {
      passesConditionalCacheValue = true;
      if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.PRE_EVALUATE_COMPONENT_UI, this, widg, null);
      if (doData)
      {
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.PRE_EVALUATE_DATA_UI, this, widg, null);
      }
      // Table with row/column data indexes
      if (colHeaderPanel != null)
      {
        colHeaderPanel.checkForFocus = true;
        colHeaderPanel.passesConditionalCacheValue = true;
        java.util.Set firedWidgets = new java.util.HashSet();
        columnDataContext = colHeaderPanel.processParentActions(firedWidgets);
        Object defaultRes = columnDataContext.safeLookup(null);
        colHeaderPanel.relatedContext = columnDataContext = columnDataContext.createChild();
        colHeaderPanel.loadAttributeContext();
        colHeaderPanel.checkForFocus = false;
        if (!wasWidgetParentFired(colHeaderPanel.widg, firedWidgets))
        {
          colHeaderPanel.passesConditionalCacheValue = passesConditionalCacheValue = false;
          rv = false;
        }
        else
        {
          if (defaultRes instanceof Object[])
            columnData = (Object[]) defaultRes;
          else if (defaultRes instanceof java.util.Collection)
            columnData = ((java.util.Collection) defaultRes).toArray();
          else if (defaultRes instanceof java.util.Map)
            columnData = ((java.util.Map) defaultRes).keySet().toArray();
          if (columnData != null)
          {
            numColsPerPage = propWidg.getIntProperty(Widget.NUM_COLS, 0, null, this);
            if (numColsPerPage == 0)
              numColsPerPage = columnData.length;
          }
        }
      }
      else
        numColsPerPage = propWidg.getIntProperty(Widget.NUM_COLS, 0, null, this);
      if (rowHeaderPanel != null)
      {
        rowHeaderPanel.checkForFocus = true;
        rowHeaderPanel.passesConditionalCacheValue = true;
        java.util.Set firedWidgets = new java.util.HashSet();
        rowDataContext = rowHeaderPanel.processParentActions(firedWidgets);
        Object defaultRes = rowDataContext.safeLookup(null);
        rowHeaderPanel.relatedContext = rowDataContext = rowDataContext.createChild();
        rowHeaderPanel.loadAttributeContext();
        rowHeaderPanel.checkForFocus = false;
        if (!wasWidgetParentFired(rowHeaderPanel.widg, firedWidgets))
        {
          rowHeaderPanel.passesConditionalCacheValue = passesConditionalCacheValue = false;
          rv = false;
        }
        else
        {
          if (defaultRes instanceof Object[])
            rowData = (Object[]) defaultRes;
          else if (defaultRes instanceof java.util.Collection)
            rowData = ((java.util.Collection) defaultRes).toArray();
          else if (defaultRes instanceof java.util.Map)
            rowData = ((java.util.Map) defaultRes).keySet().toArray();
          if (rowData != null)
          {
            numRowsPerPage = propWidg.getIntProperty(Widget.NUM_ROWS, 0, null, this);
            if (numRowsPerPage == 0)
              numRowsPerPage = rowData.length;
          }
        }
      }
      else
        numRowsPerPage = propWidg.getIntProperty(Widget.NUM_ROWS, 0, null, this);
      if (columnData == null)
        columnData = Pooler.EMPTY_OBJECT_ARRAY;
      hSpan = columnData.length;
      hTime = (columnData instanceof Long[]) && (columnData.length == 2);
      if (rowData == null)
        rowData = Pooler.EMPTY_OBJECT_ARRAY;
      vSpan = rowData.length;
      vTime = (rowData instanceof Long[]) && (rowData.length == 2);
      baseRowData = rowData;
      baseColumnData = columnData;
      wrapping = propWidg.getIntProperty(Widget.TABLE_WRAPPING, 0, null, this);
      dimensions = propWidg.getIntProperty(Widget.DIMENSIONS, 0, null, this);
      freeformTable = Catbert.evalBool(relatedContext.getLocal("FreeformCellSize"));
      alwaysPageOnScroll = Catbert.evalBool(relatedContext.getLocal("AlwaysPageOnScroll"));

      if (rv)
      {
        buildUIForData(doData, false);
        if (doData)
        {
          if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_EVALUATE_DATA_UI, this, widg, null);
        }
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_EVALUATE_COMPONENT_UI, this, widg, null);
        for (int i = 0; i < numKids; i++)
        {
          if (kids[i] != gridPanel)
            kids[i].evaluateTree(true, doData);
        }
      }
    }
    else if (gridPanel != null)
    {
      if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.PRE_EVALUATE_COMPONENT_UI, this, widg, null);
      if (doData)
      {
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.PRE_EVALUATE_DATA_UI, this, widg, null);
      }
      passesConditionalCacheValue = true;
      checkForFocus = true;
      gridPanel.passesConditionalCacheValue = true;
      java.util.Set firedWidgets = new java.util.HashSet();
      tableDataContext = processParentActions(firedWidgets);
      Object defaultRes = tableDataContext.safeLookup(null);
      gridPanel.relatedContext = tableDataContext = tableDataContext.createChild();
      gridPanel.loadAttributeContext();
      checkForFocus = false;
      if (!wasWidgetParentFired(widg, firedWidgets))
      {
        gridPanel.passesConditionalCacheValue = false;
        passesConditionalCacheValue = false;
        rv = false;
      }
      else
      {
        // Get the array version for the context
        if (defaultRes instanceof Object[])
          tableData = (Object[]) defaultRes;
        else if (defaultRes instanceof java.util.Collection)
          tableData = ((java.util.Collection) defaultRes).toArray();
        else if (defaultRes instanceof java.util.Map)
          tableData = ((java.util.Map) defaultRes).keySet().toArray();
        /*else
				{
					String multiName = "[L" + cellClass.getName() + ";";
					if (DBG) System.out.println("multiName=" + multiName + " chainRes=" + chainRes);
					tableData = (Object[]) chainRes.safeLookup(multiName);
				}*/

        if (tableData == null)
          //tableData = (Object[]) java.lang.reflect.Array.newInstance(cellClass, 0);
          tableData = Pooler.EMPTY_OBJECT_ARRAY;

        // Check to make sure the data is all unique; otherwise we need to add the index to the key so we don't
        // reuse the wrong component and mess up focus when we refresh
        java.util.HashSet testSet = Pooler.getPooledHashSet();
        testSet.addAll(java.util.Arrays.asList(tableData));
        nonUniqueData = (testSet.size() < tableData.length);
        Pooler.returnPooledHashSet(testSet);
        wrapping = propWidg.getIntProperty(Widget.TABLE_WRAPPING, 0, null, this);
        dimensions = propWidg.getIntProperty(Widget.DIMENSIONS, 0, null, this);
        freeformTable = Catbert.evalBool(relatedContext.getLocal("FreeformCellSize"));
        alwaysPageOnScroll = Catbert.evalBool(relatedContext.getLocal("AlwaysPageOnScroll"));
        numColsPerPage = propWidg.getIntProperty(Widget.NUM_COLS, 0, null, this);
        numRowsPerPage = propWidg.getIntProperty(Widget.NUM_ROWS, 0, null, this);
        if (dimensions == HORIZONTAL_DIMENSION)
        {
          hSpan = tableData.length;
          vSpan = 1;
          if (numColsPerPage == 0)
            numColsPerPage = hSpan;
        }
        else
        {
          vSpan = tableData.length;
          hSpan = 1;
          if (numRowsPerPage == 0)
            numRowsPerPage = vSpan;
        }
        buildUIForData(doData, false);
        if (doData)
        {
          if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_EVALUATE_DATA_UI, this, widg, null);
        }
        if (uiMgr.getTracer() != null) uiMgr.getTracer().traceUI(Tracer.POST_EVALUATE_COMPONENT_UI, this, widg, null);
        for (int i = 0; i < numKids; i++)
        {
          if (kids[i] != gridPanel)
            kids[i].evaluateTree(true, doData);
        }
      }
    }
    return rv;
  }

  private java.util.ArrayList finalCellWidgs;
  private long finalCellModTime;
  private java.util.ArrayList finalRowWidgs;
  private long finalRowModTime;
  private java.util.ArrayList finalColWidgs;
  private long finalColModTime;
  private void buildUIForData(boolean doDataAlways, boolean doDataNew)
  {
    // NOTE: This is because when you re-use components from a table during table scrolling there may still
    // need to be data updates done to the components. A good example is the time extender arrows in the EPG which
    // are dependent upon the current time index but can change even when a component is fully-reused. What will happen
    // is that the Image won't show up unless it's already had it's data evaluated. It will end up getting it's
    // conditional evaluated since that's always done here. BUT the downside to this is that table navigation
    // becomes much more inefficient...but that may not be that bad because we were already checking all of
    // the conditionals each time...we just weren't doing the dynamic data updates (And many things are conditional)
    // Instead of doing this for all cases; we're just going to do it for the vTime & hTime cases since the evaluation
    // should only really change if the row or col data changes; and normally we just use the cell value in the map
    // to track this so those will slip by during reuse.
    //		if (doDataNew)
    //			doDataAlways = true;
    java.awt.event.MouseListener recurseML;
    java.awt.event.MouseMotionListener recurseMM;
    if (gridPanel != null && gridPanel.mouseListeners != null && gridPanel.mouseListeners.size() > 0)
      recurseML = (java.awt.event.MouseListener) gridPanel.mouseListeners.firstElement();
    else
      recurseML = null;
    if (gridPanel != null && autoscroll != null)
      gridPanel.addMouseMotionListener(gridPanel);
    if (gridPanel != null && gridPanel.mouseMotionListeners != null && gridPanel.mouseMotionListeners.size() > 0)
      recurseMM = (java.awt.event.MouseMotionListener) gridPanel.mouseMotionListeners.firstElement();
    else
      recurseMM = null;

    java.util.Map newRowReuseMap = new java.util.HashMap();
    java.util.Map newColReuseMap = new java.util.HashMap();
    java.util.Map newCellReuseMap = new java.util.HashMap();

    // Retain all of the old ZPseudoComps through a data mapping since every cell in the table
    // can be linked to an Object. Reuse this if they are encountered.
    java.util.Set originalKids = new java.util.HashSet(java.util.Arrays.asList(gridPanel.kids));
    //		gridPanel.removeAll();
    if (rowHeaderPanel != null)
    {
      originalKids.addAll(java.util.Arrays.asList(rowHeaderPanel.kids));
      //			rowHeaderPanel.removeAll();
    }
    if (colHeaderPanel != null)
    {
      originalKids.addAll(java.util.Arrays.asList(colHeaderPanel.kids));
      //			colHeaderPanel.removeAll();
    }
    originalKids.remove(null);
    Widget cellWidg = gridPanel.widg;
    if (finalCellWidgs == null || (finalCellModTime < uiMgr.getModuleGroup().lastModified()))
    {
      finalCellWidgs = deriveFinalUIChildrenWithActionParents(cellWidg);
      finalCellModTime = uiMgr.getModuleGroup().lastModified();
    }

    // If we're not scrollable then don't shift our index to the bottom
    if (isLastVPage() && dimensions != 0)
    {
      vunitIndex = getLastVIndex();
    }
    if (isLastHPage() && dimensions != 0)
    {
      hunitIndex = getLastHIndex();
    }

    if (!vTime && ((dimensions == VERTICAL_DIMENSION && (vunitIndex * numColsPerPage >= vSpan)) ||
        (vunitIndex >= vSpan)))
    {
      vunitIndex = 0;
    }
    if (!hTime && ((dimensions == HORIZONTAL_DIMENSION && (hunitIndex * numRowsPerPage >= hSpan)) ||
        (hunitIndex >= hSpan)))
    {
      hunitIndex = 0;
    }

    // NOTE: This updatePaginationContext() used to be at the end of this method but I moved it so that fixed focus tables worked properly
    // because the table components depend upon this value for who's focused when they're evaluated below
    updatePaginationContext();
    // NOTE: New table components caching technique
    // Figure out which components are going to be re-used based on context and get those done first. Then re-use
    // any components that are left (with the extra call to completely reset their contexts). If we still need more then create those from scratch.

    int numVisRowsPerPage = numRowsPerPage;
    int numVisColsPerPage = numColsPerPage;
    // In case there's an extra partially visible row/column we want to be sure we visibly render it; but not include it
    // in our other calculations for paging and such.
    if (freeformTable)
    {
      if ((dimensions & VERTICAL_DIMENSION) != 0 && (wrapping & VERTICAL_DIMENSION) == 0)
        numVisRowsPerPage++;
      if ((dimensions & HORIZONTAL_DIMENSION) != 0 && (wrapping & HORIZONTAL_DIMENSION) == 0)
        numVisColsPerPage++;
    }
    if (tableDataContext != null)
    {
      Catbert.Context chainRes;

      int dataIndex = vunitIndex*numColsPerPage + hunitIndex*numRowsPerPage;
      int cellIndex = 0;
      int endDataIndex = (tableData.length == 0) ? 0 : (((dataIndex+tableData.length)-1) % tableData.length);
      fullyReusedComps.clear();
      for (; dataIndex < tableData.length && cellIndex < numVisRowsPerPage*numVisColsPerPage; cellIndex++, dataIndex++)
      {
        for (int j = 0; j < finalCellWidgs.size(); j++)
        {
          // Check the reuseMap first
          java.util.Map map1 = (java.util.Map) reuseCellMap.get(nonUniqueData ? (tableData[dataIndex] + "" + dataIndex) : tableData[dataIndex]);
          if (map1 != null)
          {
            ZPseudoComp oldComp = (ZPseudoComp) map1.get(finalCellWidgs.get(j));
            if (oldComp != null)
            {
              fullyReusedComps.add(oldComp);
            }
          }
        }
        if (wrapping != 0)
        {
          if (dataIndex == endDataIndex)
            break;
          if (dataIndex == tableData.length - 1)
          {
            // This must be done on a new row/column or it doesn't
            // make sense.
            dataIndex = -1;
          }
        }
      }

      // Narflex - 11/30/10 - I'm trying a new trick here where we hold onto the list of old unused components between refreshes. In testing it all looks
      // like it works fine; and then prevents constantly reallocating children as the table changes size.
      //freeToReuseComps.clear();
      java.util.Iterator walker1 = reuseCellMap.values().iterator();
      while (walker1.hasNext())
      {
        java.util.Map currMap = (java.util.Map) walker1.next();
        java.util.Iterator walker2 = currMap.values().iterator();
        while (walker2.hasNext())
        {
          Object foo = walker2.next();
          if (!fullyReusedComps.contains(foo))
            freeToReuseCompsCell.add(new Object[] { foo, currMap });
        }
      }

      dataIndex = vunitIndex*numColsPerPage + hunitIndex*numRowsPerPage;
      cellIndex = 0;
      for (; dataIndex < tableData.length && cellIndex < numVisRowsPerPage*numVisColsPerPage; cellIndex++, dataIndex++)
      {
        for (int j = 0; j < finalCellWidgs.size(); j++)
        {
          java.util.ArrayList currWidgFoo = (java.util.ArrayList) finalCellWidgs.get(j);
          Widget currWidg = (Widget) currWidgFoo.get(0);

          // Check the reuseMap first
          ZPseudoComp oldComp;
          java.util.Map map1 = (java.util.Map) reuseCellMap.get(nonUniqueData ? (tableData[dataIndex] + "" + dataIndex) : tableData[dataIndex]);
          if (map1 != null)
          {
            oldComp = (ZPseudoComp) map1.get(currWidgFoo);
            if (oldComp != null)
            {
              // Clear it from the map so its not used multiple times here, because that's wrong
              map1.remove(currWidgFoo);
              chainRes = oldComp.relatedContext;
              chainRes.setLocal(gridPanel.widg.getName(), tableData[dataIndex]);
              chainRes.setLocal("TableRow", new Integer(dataIndex + 1));
              gridPanel.add(oldComp);
              // NOTE: There's a bug here where in PseudoMenu.activate it reloads the attribute context before it calls
              // evaluateTree. That means if we're using a cached menu with tables, the attributes will get loaded with the
              // table values from the prior state. As part of that they'll be marked as 'fresh' which means they won't get evaluated
              // again here...this code is from before CVS started, so I can't find out exactly why we did this, but as this behavior is wrong
              // and this looks to be just some kind of performance optimization (from a time when performance wasn't an issue), it seems safe
              // to just comment the conditionality of this out.
              //if (!oldComp.hasFreshlyLoadedContext())
              oldComp.reloadAttributeContext();
              oldComp.evaluateTree(true, doDataAlways);
              map1 = (java.util.Map) newCellReuseMap.get(nonUniqueData ? (tableData[dataIndex] + "" + dataIndex) : tableData[dataIndex]);
              if (map1 == null)
                newCellReuseMap.put(nonUniqueData ? (tableData[dataIndex] + "" + dataIndex) : tableData[dataIndex], map1 = new java.util.HashMap());
              map1.put(currWidgFoo, oldComp);
              originalKids.remove(oldComp);
              continue;
            }
          }
          if (!freeToReuseCompsCell.isEmpty())
          {
            Object[] tempData = (Object[]) freeToReuseCompsCell.remove(0);
            oldComp = (ZPseudoComp) tempData[0];
            map1 = (java.util.Map) tempData[1];
            // Clear it from the map so its not used multiple times here, because that's wrong
            map1.values().remove(oldComp);
            // This should make the component safe to re-use even though it has a different value now
            gridPanel.add(oldComp);
            oldComp.clearRecursiveChildContexts(tableDataContext);
            chainRes = oldComp.relatedContext;
            chainRes.setLocal(gridPanel.widg.getName(), tableData[dataIndex]);
            chainRes.setLocal("TableRow", new Integer(dataIndex + 1));
            oldComp.reloadAttributeContext();
            oldComp.evaluateTree(true, doDataAlways || doDataNew);
            map1 = (java.util.Map) newCellReuseMap.get(nonUniqueData ? (tableData[dataIndex] + "" + dataIndex) : tableData[dataIndex]);
            if (map1 == null)
              newCellReuseMap.put(nonUniqueData ? (tableData[dataIndex] + "" + dataIndex) : tableData[dataIndex], map1 = new java.util.HashMap());
            map1.put(currWidgFoo, oldComp);
            originalKids.remove(oldComp);
            continue;
          }
          chainRes = tableDataContext.createChild();
          chainRes.setLocal(gridPanel.widg.getName(), tableData[dataIndex]);
          chainRes.setLocal("TableRow", new Integer(dataIndex + 1));
          ZPseudoComp fixMeKid = new ZPseudoComp(currWidg, defaultThemes, chainRes, (java.util.ArrayList)currWidgFoo.get(1));
          gridPanel.add(fixMeKid);
          fixMeKid.evaluateTree(true, doDataAlways || doDataNew);
          if (recurseML != null)
            fixMeKid.addMouseListenerRecursive(recurseML, true);
          if (recurseMM != null)
            fixMeKid.addMouseMotionListenerRecursive(recurseMM, true);
          map1 = (java.util.Map) newCellReuseMap.get(nonUniqueData ? (tableData[dataIndex] + "" + dataIndex) : tableData[dataIndex]);
          if (map1 == null)
            newCellReuseMap.put(nonUniqueData ? (tableData[dataIndex] + "" + dataIndex) : tableData[dataIndex], map1 = new java.util.HashMap());
          map1.put(currWidgFoo, fixMeKid);
        }
        if (wrapping != 0)
        {
          if (dataIndex == endDataIndex)
            break;
          if (dataIndex == tableData.length - 1)
          {
            // This must be done on a new row/column or it doesn't
            // make sense.
            dataIndex = -1;
          }
        }
      }
    }
    else
    {
      if (rowDataContext != null)
      {
        if (finalRowWidgs == null || (finalRowModTime < uiMgr.getModuleGroup().lastModified()))
        {
          finalRowWidgs = deriveFinalUIChildrenWithActionParents(rowHeaderPanel.widg);
          finalRowModTime = uiMgr.getModuleGroup().lastModified();
        }
        Catbert.Context chainRes;

        int dataIndex = vunitIndex;
        int cellIndex = 0;
        long timebase=0, rowDur=0;
        if (vTime)
        {
          timebase = ((Long) baseRowData[0]).longValue();
          rowDur = (((Long) baseRowData[1]).longValue() - timebase)/numRowsPerPage;
          timebase += vunitIndex * rowDur;
          rowData = new Long[numVisRowsPerPage];
          long tempTimebase = timebase;
          for (int q = 0; q < numVisRowsPerPage; q++)
          {
            rowData[q] = new Long(tempTimebase);
            tempTimebase += rowDur;
          }
          dataIndex = 0;
        }
        int endDataIndex = (rowData.length == 0) ? 0 : (((dataIndex+rowData.length)-1) % rowData.length);

        fullyReusedComps.clear();
        for (; dataIndex < rowData.length && cellIndex < numVisRowsPerPage; cellIndex++, dataIndex++)
        {
          for (int j = 0; j < finalRowWidgs.size(); j++)
          {
            // Check the reuseMap first
            java.util.Map map1 = (java.util.Map) reuseRowMap.get(rowData[dataIndex]);
            if (map1 != null)
            {
              ZPseudoComp oldComp = (ZPseudoComp) map1.get(finalRowWidgs.get(j));
              if (oldComp != null)
              {
                fullyReusedComps.add(oldComp);
              }
            }
          }
          if ((wrapping & VERTICAL_DIMENSION) != 0)
          {
            if (dataIndex == endDataIndex)
              break;
            if (dataIndex == rowData.length - 1)
            {
              dataIndex = -1;
            }
          }
        }

        // Narflex - 11/30/10 - I'm trying a new trick here where we hold onto the list of old unused components between refreshes. In testing it all looks
        // like it works fine; and then prevents constantly reallocating children as the table changes size.
        //freeToReuseCompsRow.clear();
        java.util.Iterator walker1 = reuseRowMap.values().iterator();
        while (walker1.hasNext())
        {
          java.util.Map currMap = (java.util.Map) walker1.next();
          java.util.Iterator walker2 = currMap.values().iterator();
          while (walker2.hasNext())
          {
            Object foo = walker2.next();
            if (!fullyReusedComps.contains(foo))
              freeToReuseCompsRow.add(new Object[] { foo, currMap });
          }
        }

        dataIndex = vunitIndex;
        cellIndex = 0;
        if (vTime)
        {
          dataIndex = 0;
        }


        for (; dataIndex < rowData.length && cellIndex < numVisRowsPerPage; cellIndex++, dataIndex++)
        {
          for (int j = 0; j < finalRowWidgs.size(); j++)
          {
            java.util.ArrayList currWidgFoo = (java.util.ArrayList) finalRowWidgs.get(j);
            Widget currWidg = (Widget) currWidgFoo.get(0);

            ZPseudoComp oldComp;
            // Check the reuseMap first
            java.util.Map map1 = (java.util.Map) reuseRowMap.get(rowData[dataIndex]);
            if (map1 != null)
            {
              oldComp = (ZPseudoComp) map1.get(currWidgFoo);
              if (oldComp != null)
              {
                // Clear it from the map so its not used multiple times here, because that's wrong
                map1.remove(currWidgFoo);
                chainRes = oldComp.relatedContext;
                chainRes.setLocal(rowHeaderPanel.widg.getName(), rowData[dataIndex]);
                chainRes.setLocal("TableRow", new Integer(dataIndex + 1));
                rowHeaderPanel.add(oldComp);
                // NOTE: There's a bug here where in PseudoMenu.activate it reloads the attribute context before it calls
                // evaluateTree. That means if we're using a cached menu with tables, the attributes will get loaded with the
                // table values from the prior state. As part of that they'll be marked as 'fresh' which means they won't get evaluated
                // again here...this code is from before CVS started, so I can't find out exactly why we did this, but as this behavior is wrong
                // and this looks to be just some kind of performance optimization (from a time when performance wasn't an issue), it seems safe
                // to just comment the conditionality of this out.
                //if (!oldComp.hasFreshlyLoadedContext())
                oldComp.reloadAttributeContext();
                oldComp.evaluateTree(true, doDataAlways || (vTime && doDataNew));
                map1 = (java.util.Map) newRowReuseMap.get(rowData[dataIndex]);
                if (map1 == null)
                  newRowReuseMap.put(rowData[dataIndex], map1 = new java.util.HashMap());
                map1.put(currWidgFoo, oldComp);
                originalKids.remove(oldComp);
                continue;
              }
            }
            if (!freeToReuseCompsRow.isEmpty())
            {
              Object[] tempData = (Object[]) freeToReuseCompsRow.remove(0);
              oldComp = (ZPseudoComp) tempData[0];
              map1 = (java.util.Map) tempData[1];
              // Clear it from the map so its not used multiple times here, because that's wrong
              map1.values().remove(oldComp);
              // This should make the component safe to re-use even though it has a different value now
              oldComp.clearRecursiveChildContexts(rowDataContext);
              chainRes = oldComp.relatedContext;
              chainRes.setLocal(rowHeaderPanel.widg.getName(), rowData[dataIndex]);
              chainRes.setLocal("TableRow", new Integer(dataIndex + 1));
              rowHeaderPanel.add(oldComp);
              oldComp.reloadAttributeContext();
              oldComp.evaluateTree(true, doDataAlways || doDataNew);
              map1 = (java.util.Map) newRowReuseMap.get(rowData[dataIndex]);
              if (map1 == null)
                newRowReuseMap.put(rowData[dataIndex], map1 = new java.util.HashMap());
              map1.put(currWidgFoo, oldComp);
              originalKids.remove(oldComp);
              continue;
            }
            chainRes = rowDataContext.createChild();
            chainRes.setLocal(rowHeaderPanel.widg.getName(), rowData[dataIndex]);
            chainRes.setLocal("TableRow", new Integer(dataIndex + 1));
            ZPseudoComp fixMeKid = new ZPseudoComp(currWidg, defaultThemes, chainRes, (java.util.ArrayList)currWidgFoo.get(1));
            rowHeaderPanel.add(fixMeKid);
            fixMeKid.evaluateTree(true, doDataAlways || doDataNew);
            map1 = (java.util.Map) newRowReuseMap.get(rowData[dataIndex]);
            if (map1 == null)
              newRowReuseMap.put(rowData[dataIndex], map1 = new java.util.HashMap());
            map1.put(currWidgFoo, fixMeKid);
          }
          if ((wrapping & VERTICAL_DIMENSION) != 0)
          {
            if (dataIndex == endDataIndex)
              break;
            if (dataIndex == rowData.length - 1)
            {
              dataIndex = -1;
            }
          }
        }
        if (vTime)
          rowData = new Long[] { new Long(timebase), new Long(timebase + rowDur*numRowsPerPage) };
      }
      if (columnDataContext != null)
      {
        if (finalColWidgs == null || (finalColModTime < uiMgr.getModuleGroup().lastModified()))
        {
          finalColWidgs = deriveFinalUIChildrenWithActionParents(colHeaderPanel.widg);
          finalColModTime = uiMgr.getModuleGroup().lastModified();
        }
        Catbert.Context chainRes;

        int dataIndex = hunitIndex;
        int cellIndex = 0;
        long timebase=0, colDur=0;
        if (hTime)
        {
          timebase = ((Long) baseColumnData[0]).longValue();
          colDur = (((Long) baseColumnData[1]).longValue() - timebase)/numColsPerPage;
          timebase += hunitIndex * colDur;
          columnData = new Long[numVisColsPerPage];
          long tempTimebase = timebase;
          for (int q = 0; q < numVisColsPerPage; q++)
          {
            columnData[q] = new Long(tempTimebase);
            tempTimebase += colDur;
          }
          dataIndex = 0;
        }
        int endDataIndex = (columnData.length == 0) ? 0 : (((dataIndex+columnData.length)-1) % columnData.length);
        fullyReusedComps.clear();
        for (; dataIndex < columnData.length && cellIndex < numVisColsPerPage; cellIndex++, dataIndex++)
        {
          for (int j = 0; j < finalColWidgs.size(); j++)
          {
            // Check the reuseMap first
            java.util.Map map1 = (java.util.Map) reuseColMap.get(columnData[dataIndex]);
            if (map1 != null)
            {
              ZPseudoComp oldComp = (ZPseudoComp) map1.get(finalColWidgs.get(j));
              if (oldComp != null)
              {
                fullyReusedComps.add(oldComp);
              }
            }
          }
          if ((wrapping & HORIZONTAL_DIMENSION) != 0)
          {
            if (dataIndex == endDataIndex)
              break;
            if (dataIndex == columnData.length - 1)
            {
              dataIndex = -1;
            }
          }
        }

        // Narflex - 11/30/10 - I'm trying a new trick here where we hold onto the list of old unused components between refreshes. In testing it all looks
        // like it works fine; and then prevents constantly reallocating children as the table changes size.
        //freeToReuseCompsCol.clear();
        java.util.Iterator walker1 = reuseColMap.values().iterator();
        while (walker1.hasNext())
        {
          java.util.Map currMap = (java.util.Map) walker1.next();
          java.util.Iterator walker2 = currMap.values().iterator();
          while (walker2.hasNext())
          {
            Object foo = walker2.next();
            if (!fullyReusedComps.contains(foo))
              freeToReuseCompsCol.add(new Object[] { foo, currMap });
          }
        }
        dataIndex = hunitIndex;
        cellIndex = 0;
        if (hTime)
        {
          dataIndex = 0;
        }


        for (; dataIndex < columnData.length && cellIndex < numVisColsPerPage; cellIndex++, dataIndex++)
        {
          for (int j = 0; j < finalColWidgs.size(); j++)
          {
            java.util.ArrayList currWidgFoo = (java.util.ArrayList) finalColWidgs.get(j);
            Widget currWidg = (Widget) currWidgFoo.get(0);

            ZPseudoComp oldComp;
            // Check the reuseMap first
            java.util.Map map1 = (java.util.Map) reuseColMap.get(columnData[dataIndex]);
            if (map1 != null)
            {
              oldComp = (ZPseudoComp) map1.get(currWidgFoo);
              if (oldComp != null)
              {
                // Clear it from the map so its not used multiple times here, because that's wrong
                map1.remove(currWidgFoo);
                chainRes = oldComp.relatedContext;
                chainRes.setLocal(colHeaderPanel.widg.getName(), columnData[dataIndex]);
                chainRes.setLocal("TableCol", new Integer(dataIndex + 1));
                colHeaderPanel.add(oldComp);
                // NOTE: There's a bug here where in PseudoMenu.activate it reloads the attribute context before it calls
                // evaluateTree. That means if we're using a cached menu with tables, the attributes will get loaded with the
                // table values from the prior state. As part of that they'll be marked as 'fresh' which means they won't get evaluated
                // again here...this code is from before CVS started, so I can't find out exactly why we did this, but as this behavior is wrong
                // and this looks to be just some kind of performance optimization (from a time when performance wasn't an issue), it seems safe
                // to just comment the conditionality of this out.
                //if (!oldComp.hasFreshlyLoadedContext())
                oldComp.reloadAttributeContext();
                oldComp.evaluateTree(true, doDataAlways || (hTime && doDataNew));
                map1 = (java.util.Map) newColReuseMap.get(columnData[dataIndex]);
                if (map1 == null)
                  newColReuseMap.put(columnData[dataIndex], map1 = new java.util.HashMap());
                map1.put(currWidgFoo, oldComp);
                originalKids.remove(oldComp);
                continue;
              }
            }
            if (!freeToReuseCompsCol.isEmpty())
            {
              Object[] tempData = (Object[]) freeToReuseCompsCol.remove(0);
              oldComp = (ZPseudoComp) tempData[0];
              map1 = (java.util.Map) tempData[1];
              // Clear it from the map so its not used multiple times here, because that's wrong
              map1.values().remove(oldComp);
              // This should make the component safe to re-use even though it has a different value now
              oldComp.clearRecursiveChildContexts(columnDataContext);
              chainRes = oldComp.relatedContext;
              chainRes.setLocal(colHeaderPanel.widg.getName(), columnData[dataIndex]);
              chainRes.setLocal("TableCol", new Integer(dataIndex + 1));
              colHeaderPanel.add(oldComp);
              oldComp.reloadAttributeContext();
              oldComp.evaluateTree(true, doDataAlways || doDataNew);
              map1 = (java.util.Map) newColReuseMap.get(columnData[dataIndex]);
              if (map1 == null)
                newColReuseMap.put(columnData[dataIndex], map1 = new java.util.HashMap());
              map1.put(currWidgFoo, oldComp);
              originalKids.remove(oldComp);
              continue;
            }

            chainRes = columnDataContext.createChild();
            chainRes.setLocal(colHeaderPanel.widg.getName(), columnData[dataIndex]);
            chainRes.setLocal("TableCol", new Integer(dataIndex + 1));
            ZPseudoComp fixMeKid = new ZPseudoComp(currWidg, defaultThemes, chainRes, (java.util.ArrayList) currWidgFoo.get(1));
            colHeaderPanel.add(fixMeKid);
            fixMeKid.evaluateTree(true, doDataAlways || doDataNew);
            map1 = (java.util.Map) newColReuseMap.get(columnData[dataIndex]);
            if (map1 == null)
              newColReuseMap.put(columnData[dataIndex], map1 = new java.util.HashMap());
            map1.put(currWidgFoo, fixMeKid);
          }
          if ((wrapping & HORIZONTAL_DIMENSION) != 0)
          {
            if (dataIndex == endDataIndex)
              break;
            if (dataIndex == columnData.length - 1)
            {
              dataIndex = -1;
            }
          }
        }
        if (hTime)
          columnData = new Long[] { new Long(timebase), new Long(timebase + colDur*numColsPerPage) };
      }
      if (gridPanel != null)
      {
        Catbert.Context chainRes;

        int colDataIndex = hunitIndex;
        int rowDataIndex = vunitIndex;
        if (hTime)
          colDataIndex = 0;
        else if (vTime)
          rowDataIndex = 0;

        int baseRowDataIndex = rowDataIndex;
        int endColIndex = (columnData.length == 0) ? 0 : (((colDataIndex+columnData.length)-1) % columnData.length);


        fullyReusedComps.clear();
        allCellData.clear();
        for (int colIndex = 0; colIndex < numVisColsPerPage && colDataIndex < columnData.length; colIndex++, colDataIndex++)
        {
          rowDataIndex = baseRowDataIndex;
          int endRowIndex = (rowData.length == 0) ? 0 : (((rowDataIndex+rowData.length)-1) % rowData.length);
          for (int rowIndex = 0;
              rowIndex < numVisRowsPerPage && rowDataIndex < rowData.length; rowIndex++, rowDataIndex++)
          {
            chainRes = gridPanel.relatedContext.createChild();
            if (hTime)
              chainRes.setLocal(colHeaderPanel.widg.getName(), columnData);
            else
              chainRes.setLocal(colHeaderPanel.widg.getName(), columnData[colDataIndex]);
            chainRes.setLocal("TableCol", new Integer(colDataIndex + 1));
            if (vTime)
              chainRes.setLocal(rowHeaderPanel.widg.getName(), rowData);
            else
              chainRes.setLocal(rowHeaderPanel.widg.getName(), rowData[rowDataIndex]);
            chainRes.setLocal("TableRow", new Integer(rowDataIndex + 1));

            // Now we need to evaluate the parent actions for the cell
            //java.util.Set parentActions = new java.util.HashSet();
            //Widget rootAction = getParentActionSet(cellWidg, parentActions, this);
            if (gridPanel.rootParentAction != null)
            {
              if (Sage.PERF_ANALYSIS)
                perfTime = Sage.time();
              processChain(gridPanel.rootParentAction, chainRes, gridPanel.parentActions, gridPanel, false);
              if (Sage.PERF_ANALYSIS)
              {
                perfTime = Sage.time() - perfTime;
                if (perfTime > Sage.EVALUATE_THRESHOLD_TIME)
                {
                  System.out.println("EXEC TABLE-1 PARENTS PERF time=" + perfTime + " widg=" + gridPanel.widg);
                }
              }
            }
            Object indivCellData = chainRes.safeLookup(null);
            Object[] allIndivCellData;
            Object[] reuseMapKey;
            if (hTime || vTime)
            {
              if (indivCellData instanceof Object[])
                allIndivCellData = (Object[]) indivCellData;
              else if (indivCellData instanceof java.util.Collection)
                allIndivCellData = ((java.util.Collection) indivCellData).toArray();
              else if (indivCellData instanceof java.util.Map)
                allIndivCellData = ((java.util.Map) indivCellData).keySet().toArray();
              else
                allIndivCellData = new Object[] { indivCellData };
              reuseMapKey = allIndivCellData;
            }
            else
            {
              allIndivCellData = new Object[] { indivCellData };
              java.util.ArrayList reuseKeyList = new java.util.ArrayList();
              reuseKeyList.add(columnData[colDataIndex]);
              reuseKeyList.add(rowData[rowDataIndex]);
              reuseMapKey = new Object[] { reuseKeyList };
            }
            allCellData.add(chainRes);
            allCellData.add(allIndivCellData);
            allCellData.add(reuseMapKey);
            for (int cellDataCount = 0; cellDataCount < allIndivCellData.length; cellDataCount++)
            {
              for (int j = 0; j < finalCellWidgs.size(); j++)
              {
                // Check the reuseMap first
                java.util.Map map1 = (java.util.Map) reuseCellMap.get(reuseMapKey[cellDataCount]);
                if (map1 != null)
                {
                  ZPseudoComp oldComp = (ZPseudoComp) map1.get(finalCellWidgs.get(j));
                  if (oldComp != null)
                  {
                    fullyReusedComps.add(oldComp);
                  }
                }
              }
            }
            if (vTime)
              break;
            if ((wrapping & VERTICAL_DIMENSION) != 0)
            {
              if (rowDataIndex == endRowIndex)
                break;
              if (rowDataIndex == rowData.length - 1)
              {
                rowDataIndex = -1;
              }
            }
          }
          if (hTime)
            break;
          if ((wrapping & HORIZONTAL_DIMENSION) != 0)
          {
            if (colDataIndex == endColIndex)
              break;
            if (colDataIndex == columnData.length - 1)
            {
              colDataIndex = -1;
            }
          }
        }

        // Narflex - 11/30/10 - I'm trying a new trick here where we hold onto the list of old unused components between refreshes. In testing it all looks
        // like it works fine; and then prevents constantly reallocating children as the table changes size.
        //freeToReuseCompsCell.clear();
        java.util.Iterator walker1 = reuseCellMap.values().iterator();
        while (walker1.hasNext())
        {
          java.util.Map currMap = (java.util.Map) walker1.next();
          java.util.Iterator walker2 = currMap.values().iterator();
          while (walker2.hasNext())
          {
            Object foo = walker2.next();
            if (!fullyReusedComps.contains(foo))
              freeToReuseCompsCell.add(new Object[] { foo, currMap });
          }
        }
        colDataIndex = hunitIndex;
        rowDataIndex = vunitIndex;
        if (hTime)
          colDataIndex = 0;
        else if (vTime)
          rowDataIndex = 0;

        baseRowDataIndex = rowDataIndex;
        endColIndex = (columnData.length == 0) ? 0 : (((colDataIndex+columnData.length)-1) % columnData.length);

        for (int colIndex = 0; colIndex < numVisColsPerPage && colDataIndex < columnData.length; colIndex++, colDataIndex++)
        {
          rowDataIndex = baseRowDataIndex;
          int endRowIndex = (rowData.length == 0) ? 0 : (((rowDataIndex+rowData.length)-1) % rowData.length);
          for (int rowIndex = 0;
              rowIndex < numVisRowsPerPage && rowDataIndex < rowData.length; rowIndex++, rowDataIndex++)
          {
            chainRes = (Catbert.Context) allCellData.remove(0);
            Object[] allIndivCellData = (Object[]) allCellData.remove(0);
            Object[] reuseMapKey = (Object[]) allCellData.remove(0);
            for (int cellDataCount = 0; cellDataCount < allIndivCellData.length; cellDataCount++)
            {
              Catbert.Context cellChainRes = chainRes.createChild();
              cellChainRes.setLocal(cellWidg.getName(), allIndivCellData[cellDataCount]);
              for (int j = 0; j < finalCellWidgs.size(); j++)
              {
                java.util.ArrayList currWidgFoo = (java.util.ArrayList) finalCellWidgs.get(j);
                Widget currWidg = (Widget) currWidgFoo.get(0);

                ZPseudoComp oldComp;
                // Check the reuseMap first
                java.util.Map map1 = (java.util.Map) reuseCellMap.get(reuseMapKey[cellDataCount]);
                if (map1 != null)
                {
                  oldComp = (ZPseudoComp) map1.get(currWidgFoo);
                  if (oldComp != null)
                  {
                    // Clear it from the map so its not used multiple times here, because that's wrong
                    map1.remove(currWidgFoo);
                    Catbert.Context tempChainRes = oldComp.relatedContext;
                    tempChainRes.setLocal(cellWidg.getName(), allIndivCellData[cellDataCount]);
                    if (hTime)
                      tempChainRes.setLocal(colHeaderPanel.widg.getName(), columnData);
                    else
                      tempChainRes.setLocal(colHeaderPanel.widg.getName(), columnData[colDataIndex]);
                    tempChainRes.setLocal("TableCol", new Integer(colDataIndex + 1));
                    if (vTime)
                      tempChainRes.setLocal(rowHeaderPanel.widg.getName(), rowData);
                    else
                      tempChainRes.setLocal(rowHeaderPanel.widg.getName(), rowData[rowDataIndex]);
                    tempChainRes.setLocal("TableRow", new Integer(rowDataIndex + 1));
                    gridPanel.add(oldComp);
                    // NOTE: There's a bug here where in PseudoMenu.activate it reloads the attribute context before it calls
                    // evaluateTree. That means if we're using a cached menu with tables, the attributes will get loaded with the
                    // table values from the prior state. As part of that they'll be marked as 'fresh' which means they won't get evaluated
                    // again here...this code is from before CVS started, so I can't find out exactly why we did this, but as this behavior is wrong
                    // and this looks to be just some kind of performance optimization (from a time when performance wasn't an issue), it seems safe
                    // to just comment the conditionality of this out.
                    //if (!oldComp.hasFreshlyLoadedContext())
                    oldComp.reloadAttributeContext();
                    oldComp.evaluateTree(true, doDataAlways || ((hTime || vTime) && doDataNew));
                    map1 = (java.util.Map) newCellReuseMap.get(reuseMapKey[cellDataCount]);
                    if (map1 == null)
                      newCellReuseMap.put(reuseMapKey[cellDataCount],
                          map1 = new java.util.HashMap());
                    map1.put(currWidgFoo, oldComp);
                    originalKids.remove(oldComp);
                    continue;
                  }
                }
                if (!freeToReuseCompsCell.isEmpty())
                {
                  Object[] tempData = (Object[]) freeToReuseCompsCell.remove(0);
                  oldComp = (ZPseudoComp) tempData[0];
                  map1 = (java.util.Map) tempData[1];
                  // Clear it from the map so its not used multiple times here, because that's wrong
                  map1.values().remove(oldComp);
                  // This should make the component safe to re-use even though it has a different value now
                  oldComp.clearRecursiveChildContexts(cellChainRes);
                  Catbert.Context tempChainRes = oldComp.relatedContext;
                  tempChainRes.setLocal(cellWidg.getName(), allIndivCellData[cellDataCount]);
                  if (hTime)
                    tempChainRes.setLocal(colHeaderPanel.widg.getName(), columnData);
                  else
                    tempChainRes.setLocal(colHeaderPanel.widg.getName(), columnData[colDataIndex]);
                  tempChainRes.setLocal("TableCol", new Integer(colDataIndex + 1));
                  if (vTime)
                    tempChainRes.setLocal(rowHeaderPanel.widg.getName(), rowData);
                  else
                    tempChainRes.setLocal(rowHeaderPanel.widg.getName(), rowData[rowDataIndex]);
                  tempChainRes.setLocal("TableRow", new Integer(rowDataIndex + 1));
                  gridPanel.add(oldComp);
                  oldComp.reloadAttributeContext();
                  oldComp.evaluateTree(true, doDataAlways || doDataNew);
                  map1 = (java.util.Map) newCellReuseMap.get(reuseMapKey[cellDataCount]);
                  if (map1 == null)
                    newCellReuseMap.put(reuseMapKey[cellDataCount],
                        map1 = new java.util.HashMap());
                  map1.put(currWidgFoo, oldComp);
                  originalKids.remove(oldComp);
                  continue;
                }
                ZPseudoComp fixMeKid = new ZPseudoComp(currWidg, defaultThemes, cellChainRes,
                    (java.util.ArrayList) currWidgFoo.get(1));
                gridPanel.add(fixMeKid);
                fixMeKid.evaluateTree(true, doDataAlways || doDataNew);
                if (recurseML != null)
                  fixMeKid.addMouseListenerRecursive(recurseML, true);
                if (recurseMM != null)
                  fixMeKid.addMouseMotionListenerRecursive(recurseMM, true);
                map1 = (java.util.Map) newCellReuseMap.get(reuseMapKey[cellDataCount]);
                if (map1 == null)
                  newCellReuseMap.put(reuseMapKey[cellDataCount], map1 = new java.util.HashMap());
                map1.put(currWidgFoo, fixMeKid);
              }
            }
            if (vTime)
              break;
            if ((wrapping & VERTICAL_DIMENSION) != 0)
            {
              if (rowDataIndex == endRowIndex)
                break;
              if (rowDataIndex == rowData.length - 1)
              {
                rowDataIndex = -1;
              }
            }
          }
          if (hTime)
            break;
          if ((wrapping & HORIZONTAL_DIMENSION) != 0)
          {
            if (colDataIndex == endColIndex)
              break;
            if (colDataIndex == columnData.length - 1)
            {
              colDataIndex = -1;
            }
          }
        }
      }
    }

    // What's left in original kids should be removed.
    java.util.Iterator walker = originalKids.iterator();
    while (walker.hasNext())
    {
      ZComp deadKid = (ZComp) walker.next();
      if (deadKid.parent == gridPanel && gridPanel != null)
        gridPanel.remove(deadKid);
      else if (deadKid.parent == rowHeaderPanel && rowHeaderPanel != null)
        rowHeaderPanel.remove(deadKid);
      else if (colHeaderPanel != null)
        colHeaderPanel.remove(deadKid);
      deadKid.cleanup();
    }
    reuseRowMap = newRowReuseMap;
    reuseColMap = newColReuseMap;
    reuseCellMap = newCellReuseMap;
    String preferredTableCache = uiMgr.get("ui/animation/preferred_scrolling_surface", "Foreground");
    // NOTE: This is a little awkward to disable the surface when it doesn't scroll because that could then affect the
    // rendering order...BUT if we don't do this then there's no way at all to disable surface usage for tables
    if ((surfaceCache == null || preferredTableCache.equals(surfaceCache)) && (!isFirstHPage() || !isFirstVPage() || !isLastHPage() || !isLastVPage()))
      surfaceCache = preferredTableCache;
  }

  protected void updatePaginationContext()
  {
    boolean firstV = isFirstVPage();
    boolean firstH = isFirstHPage();
    boolean lastV = isLastVPage();
    boolean lastH = isLastHPage();
    relatedContext.setLocal("IsFirstPage", Boolean.valueOf(firstV && firstH));
    relatedContext.setLocal("IsFirstHPage", Boolean.valueOf(firstH));
    relatedContext.setLocal("IsFirstVPage", Boolean.valueOf(firstV));
    relatedContext.setLocal("IsLastPage", Boolean.valueOf(lastV && lastH));
    relatedContext.setLocal("IsLastVPage", Boolean.valueOf(lastV));
    relatedContext.setLocal("IsLastHPage", Boolean.valueOf(lastH));
    relatedContext.setLocal("NumRows", new Integer(vSpan));
    relatedContext.setLocal("NumCols", new Integer(hSpan));
    relatedContext.setLocal("NumPages", new Integer((tableData == null || (numRowsPerPage*numColsPerPage == 0)) ?
        0 : (1 + (tableData.length/(numRowsPerPage*numColsPerPage)))));
    int numHPages = Math.max(1, (int)Math.ceil(hSpan*1.0f/numRowsPerPage - numColsPerPage + 1));
    int numVPages = Math.max(1, (int)Math.ceil(vSpan*1.0f/numColsPerPage - numRowsPerPage + 1));
    relatedContext.setLocal("NumHPages", new Integer(numHPages));
    relatedContext.setLocal("NumVPages", new Integer(numVPages));
    relatedContext.setLocal("NumHPagesF", new Float((hSpan*1.0f)/(numRowsPerPage * numColsPerPage)));
    relatedContext.setLocal("NumVPagesF", new Float((vSpan*1.0f)/(numRowsPerPage * numColsPerPage)));
    relatedContext.setLocal("NumColsPerPage", new Integer(numColsPerPage));
    relatedContext.setLocal("NumRowsPerPage", new Integer(numRowsPerPage));
    relatedContext.setLocal("HScrollIndex", new Integer(hunitIndex + 1));
    relatedContext.setLocal("VScrollIndex", new Integer(vunitIndex + 1));
    if (lastPageState == null || lastPageState[0] != firstV ||
        lastPageState[1] != lastV || lastPageState[2] != firstH ||
        lastPageState[3] != lastH || lastPageStateNum == null ||
        lastPageStateNum[0] != hunitIndex + 1 || lastPageStateNum[1] != vunitIndex + 1)
    {
      if (uiMgr.isXBMCCompatible())
        getTopPseudoParent().evaluatePagingListeners();
      else
        evaluatePagingListeners();
      if (lastPageState == null)
        lastPageState = new boolean[4];
      lastPageState[0] = firstV;
      lastPageState[1] = lastV;
      lastPageState[2] = firstH;
      lastPageState[3] = lastH;
      if (lastPageStateNum == null)
        lastPageStateNum = new float[2];
      lastPageStateNum[0] = hunitIndex + 1;
      lastPageStateNum[1] = vunitIndex + 1;
    }
  }

  public boolean isFocusable()
  {
    // A table only needs focus if you can scroll it and there's no focusable children to take the focus
    return ((!isFirstVPage() || !isLastVPage() || !isFirstHPage() || !isLastHPage()) && !hasFocusableChildren()) &&
        !backgroundComponent;
  }

  public boolean setOverallScrollLocation(float relativeX, float relativeY, boolean checkParents)
  {
    if (!isLastVPage() || !isFirstVPage() || !isLastHPage() || !isFirstHPage())
    {
      boolean[] gotLock = { false };
      try
      {
        if (uiMgr.getLock(true, gotLock, true))
        {
          java.awt.geom.Rectangle2D.Float foci = getTrueFocusRect();
          if ((dimensions & VERTICAL_DIMENSION) != 0 && relativeY >= 0 && !vTime)
          {
            vunitIndex = Math.round(relativeY * (vSpan - numRowsPerPage));
            if ((wrapping & VERTICAL_DIMENSION) == 0)
              vunitIndex = Math.max(0, vunitIndex);
            else
              vunitIndex = (vunitIndex < 0) ? (vunitIndex + vSpan) : vunitIndex;
              if ((wrapping & VERTICAL_DIMENSION) != 0 && vSpan > 0)
                vunitIndex %= vSpan;
              else if (isLastVPage())
                vunitIndex = getLastVIndex();
          }
          if ((dimensions & HORIZONTAL_DIMENSION) != 0 && relativeX >= 0 && !hTime)
          {
            hunitIndex = Math.round(relativeX * (hSpan - numColsPerPage));
            if ((wrapping & HORIZONTAL_DIMENSION) == 0)
              hunitIndex = Math.max(0, hunitIndex);
            else
              hunitIndex = (hunitIndex < 0) ? (hunitIndex + hSpan) : hunitIndex;
              if ((wrapping & HORIZONTAL_DIMENSION) != 0 && hSpan > 0)
                hunitIndex %= hSpan;
              else if (isLastHPage())
                hunitIndex = getLastHIndex();
          }
          buildUIForData(false, true);
          renderWithTrueFocusRect(foci);
          appendToDirty(true);
        }
      }
      finally
      {
        if (gotLock[0])
          uiMgr.clearLock();
      }
      return true;
    }
    else if (parent instanceof ZPseudoComp && checkParents)
    {
      /*
       * NOTE:Also check scrolling siblings. This is necessary because if we create a scrolling
       * Panel we want to be able to put scrolling controls specific to that panel somewhere without
       * affecting the layout of that panel. So creating a parent panel to hold them both makes that
       * parent act like a scroll container.
       */
      for (int i = 0; i < parent.numKids; i++)
      {
        ZPseudoComp currSib = (ZPseudoComp) parent.kids[i];
        if (currSib == this)
          continue;
        if (currSib.setOverallScrollLocation(relativeX, relativeY, false))
          return true;
      }
      ((ZPseudoComp) parent).setOverallScrollLocation(relativeX, relativeY, true);
    }
    return false;
  }

  protected float[] getAggRowWeights()
  {
    if (rowHeaderPanel != null)
    {
      float[] rv = new float[vTime ? rowHeaderPanel.numKids : numRowsPerPage];
      float totalWeight = 0;
      for (int i = 0; i < rowHeaderPanel.numKids; i++)
      {
        Number currWeight = (Number) ((ZPseudoComp)rowHeaderPanel.kids[i]).relatedContext.safeLookup("TableWeightV");
        if (currWeight == null)
        {
          rv[i] = totalWeight;
          totalWeight += 1.0f;
        }
        else
        {
          rv[i] = totalWeight;
          totalWeight += currWeight.floatValue();
        }
      }
      for (int j = rowHeaderPanel.numKids; j < rv.length; j++)
      {
        rv[j] = totalWeight;
        totalWeight += 1.0f;
      }
      return rv;
    }
    else
      return null;
  }

  protected float[] getAggColWeights()
  {
    if (colHeaderPanel != null)
    {
      float[] rv = new float[hTime ? colHeaderPanel.numKids : numColsPerPage];
      float totalWeight = 0;
      for (int i = 0; i < colHeaderPanel.numKids; i++)
      {
        Number currWeight = (Number) ((ZPseudoComp)colHeaderPanel.kids[i]).relatedContext.safeLookup("TableWeightH");
        if (currWeight == null)
        {
          rv[i] = totalWeight;
          totalWeight += 1.0f;
        }
        else
        {
          rv[i] = totalWeight;
          totalWeight += currWeight.floatValue();
        }
      }
      for (int j = colHeaderPanel.numKids; j < rv.length; j++)
      {
        rv[j] = totalWeight;
        totalWeight += 1.0f;
      }
      return rv;
    }
    else
      return null;
  }

  protected float getTotalTableWeightH()
  {
    if (ENABLE_VAR_SIZE_TABLES)
    {
      if (colHeaderPanel != null)
      {
        float totalWeight = hTime ? 0 : Math.max(0, numColsPerPage - colHeaderPanel.numKids);
        for (int i = 0; i < colHeaderPanel.numKids; i++)
        {
          Number currWeight = (Number) ((ZPseudoComp)colHeaderPanel.kids[i]).relatedContext.safeLookup("TableWeightH");
          if (currWeight == null)
            totalWeight += 1.0f;
          else
            totalWeight += currWeight.floatValue();
        }
        return totalWeight;
      }
      else if (gridPanel != null)
      {
        float totalWeight = 0;
        int numAdded = 0;
        for (int i = 0; i < gridPanel.numKids; i+= numRowsPerPage)
        {
          numAdded++;
          Number currWeight = (Number) ((ZPseudoComp)gridPanel.kids[i]).relatedContext.safeLookup("TableWeightH");
          if (currWeight == null)
            totalWeight += 1.0f;
          else
            totalWeight += currWeight.floatValue();
        }
        if (numAdded < numColsPerPage)
          totalWeight += numColsPerPage - numAdded;
        return totalWeight;
      }
      return 1.0f;
    }
    else
      return numColsPerPage;
  }

  protected float getTotalTableWeightV()
  {
    if (ENABLE_VAR_SIZE_TABLES)
    {
      if (rowHeaderPanel != null)
      {
        float totalWeight = vTime ? 0 : Math.max(0, numRowsPerPage - rowHeaderPanel.numKids);
        for (int i = 0; i < rowHeaderPanel.numKids; i++)
        {
          Number currWeight = (Number) ((ZPseudoComp)rowHeaderPanel.kids[i]).relatedContext.safeLookup("TableWeightV");
          if (currWeight == null)
            totalWeight += 1.0f;
          else
            totalWeight += currWeight.floatValue();
        }
        return totalWeight;
      }
      else if (gridPanel != null)
      {
        float totalWeight = 0;
        int numAdded = 0;
        for (int i = 0; i < gridPanel.numKids; i += numColsPerPage)
        {
          numAdded++;
          Number currWeight = (Number) ((ZPseudoComp)gridPanel.kids[i]).relatedContext.safeLookup("TableWeightV");
          if (currWeight == null)
            totalWeight += 1.0f;
          else
            totalWeight += currWeight.floatValue();
        }
        if (numAdded < numRowsPerPage)
          totalWeight += numRowsPerPage - numAdded;
        return totalWeight;
      }
      return 1.0f;
    }
    else
      return numRowsPerPage;
  }

  public Object getTableDataFromOffset(int x, boolean wrapIndex)
  {
    if (tableData == null || tableData.length == 0)
      return null;
    if (wrapIndex)
      x = (x + tableData.length) % tableData.length;
    if (x < 0 || x >= tableData.length)
      return null;
    return tableData[x];
  }

  public void buildRenderingOps(java.util.ArrayList opList, java.awt.geom.Rectangle2D.Float clipRect,
      int diffuseColor, float alphaFactor, float xoff, float yoff, int flags)
  {
    super.buildRenderingOps(opList, clipRect, diffuseColor, alphaFactor, xoff, yoff, flags);

    if (doingNext || doingPrev)
      reality.setNeedNextPrevCleanup(this);
    // NOTE: WE SHOULD REALLY CLEAR THIS ONLY IF WE KNOW THERE'S NOT ANOTHER EVENT HAPPENING RIGHT NOW
    // WHICH IS JUST GOING TO SET THEM BACK TO TRUE; THAT'LL SAVE US AN EXTRA REFRESH HERE AND PREVENT UNWANTED
    // STATE TRANSITIONS
    /*		boolean didNext = doingNext;
		boolean didPrev = doingPrev;
		doingNext = doingPrev = false;
		if (didNext)
			getTopPseudoParent().evaluateTransitionListeners(this, true);
		if (didPrev)
			getTopPseudoParent().evaluateTransitionListeners(this, false);*/
  }

  public void resetTransitionFlags()
  {
    doingNext = doingPrev = false;
  }

  public boolean isDoingNextTransition() { return doingNext; }
  public boolean isDoingPrevTransition() { return doingPrev; }

  public void notifyOfTransition(int evtType)
  {
    if (UserEvent.isUpEvent(evtType) || UserEvent.isLeftEvent(evtType) || evtType == UserEvent.PAGE_UP || evtType == UserEvent.PAGE_LEFT ||
        evtType == UserEvent.CHANNEL_UP || evtType == UserEvent.REW || evtType == UserEvent.SCROLL_UP || evtType == UserEvent.SCROLL_LEFT)
    {
      if (doingPrev)
        reality.resetNeedNextPrevCleanup(this);
      else
        doingPrev = true;
      doingNext = false;
    }
    else
    {
      if (doingNext)
        reality.resetNeedNextPrevCleanup(this);
      else
        doingNext = true;
      doingPrev = false;
    }
    getTopPseudoParent().evaluateTransitionListeners(doingNext);
  }

  protected boolean isFreeformTable()
  {
    return freeformTable;
  }

  public boolean isScrollEffectStillActive()
  {
    if (gridPanel != null)
    {
      RenderingOp scroller = gridPanel.scrollTracker;
      if (scroller != null)
      {
        EffectTracker trek = scroller.effectTracker;
        if (trek != null && trek.isActive())
          return true;
      }
    }
    if (colHeaderPanel != null)
    {
      RenderingOp scroller = colHeaderPanel.scrollTracker;
      if (scroller != null)
      {
        EffectTracker trek = scroller.effectTracker;
        if (trek != null && trek.isActive())
          return true;
      }
    }
    if (rowHeaderPanel != null)
    {
      RenderingOp scroller = rowHeaderPanel.scrollTracker;
      if (scroller != null)
      {
        EffectTracker trek = scroller.effectTracker;
        if (trek != null && trek.isActive())
          return true;
      }
    }
    return false;
  }

  protected void checkForAutoscroll(float relX, float relY)
  {
    if (autoscroll != null)
    {
      autoscroll.autoscrollRange = propWidg.getFloatProperty(Widget.AUTO_REPEAT_ACTION, 0, null, this);
      if (Sage.eventTime() - autoscroll.lastAutoscrollTime > autoscroll.autoscrollPeriod)
      {
        if ((dimensions | VERTICAL_DIMENSION) != 0)
        {
          if (relY < autoscroll.autoscrollRange && relY <= autoscroll.lastAutoScrollY)
          {
            autoscroll.lastAutoscrollTime = Sage.eventTime();
            if (unitUp(true))
              notifyOfTransition(UserEvent.UP);
            autoscroll.lastAutoScrollY = relY;
            return;
          }
          else if (relY > 1.0f - autoscroll.autoscrollRange && relY >= autoscroll.lastAutoScrollY)
          {
            autoscroll.lastAutoscrollTime = Sage.eventTime();
            if (unitDown(true))
              notifyOfTransition(UserEvent.DOWN);
            autoscroll.lastAutoScrollY = relY;
            return;
          }
        }
        if ((dimensions | HORIZONTAL_DIMENSION) != 0)
        {
          if (relX < autoscroll.autoscrollRange && relX <= autoscroll.lastAutoScrollX)
          {
            autoscroll.lastAutoscrollTime = Sage.eventTime();
            if (unitLeft(true))
              notifyOfTransition(UserEvent.LEFT);
            autoscroll.lastAutoScrollX = relX;
            return;
          }
          else if (relX > 1.0f - autoscroll.autoscrollRange && relX >= autoscroll.lastAutoScrollX)
          {
            autoscroll.lastAutoscrollTime = Sage.eventTime();
            if (unitRight(true))
              notifyOfTransition(UserEvent.RIGHT);
            autoscroll.lastAutoScrollX = relX;
            return;
          }
        }
      }
      autoscroll.lastAutoScrollX = relX;
      autoscroll.lastAutoScrollY = relY;
    }
  }

  int numRowsPerPage;
  int numColsPerPage;
  ZPseudoComp rowHeaderPanel;
  ZPseudoComp colHeaderPanel;
  ZPseudoComp gridPanel;
  ZPseudoComp nook;
  ZPseudoComp emptyComp;

  private Object[] tableData;
  private Catbert.Context tableDataContext;

  int vunitIndex;
  int hunitIndex;

  int dimensions; // 1 is vert, 2 is horiz, 3 is both
  int wrapping; // 1 is vert, 2 is horiz, 3 is both

  boolean hTime;
  boolean vTime;

  Object[] rowData;
  Object[] baseRowData;
  private Catbert.Context rowDataContext;
  Object[] columnData;
  Object[] baseColumnData;
  private Catbert.Context columnDataContext;

  //	private java.util.Map reuseMap;

  private java.util.Map reuseRowMap;
  private java.util.Map reuseColMap;
  private java.util.Map reuseCellMap;

  // Cache these so we don't rebuild them every time
  private java.util.ArrayList freeToReuseCompsCell = new java.util.ArrayList();
  private java.util.ArrayList freeToReuseCompsRow = new java.util.ArrayList();
  private java.util.ArrayList freeToReuseCompsCol = new java.util.ArrayList();
  private java.util.HashSet fullyReusedComps = new java.util.HashSet();
  private java.util.ArrayList allCellData = new java.util.ArrayList();

  int hSpan;
  int vSpan;

  // These flags are set when the user navigates in a way which would cause us to change the
  private boolean doingNext;
  private boolean doingPrev;

  private boolean freeformTable;
  private boolean alwaysPageOnScroll;

  private boolean nonUniqueData = false;

  private AutoscrollData autoscroll;
  private static class AutoscrollData
  {
    float autoscrollRange;
    long lastAutoscrollTime;
    long autoscrollPeriod;
    float lastAutoScrollX;
    float lastAutoScrollY;
  }
}
