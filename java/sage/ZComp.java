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

// This class is a generic container

public class ZComp
{
  public static final int ZCOMP_KIDS_BUF_INC_SIZE = 4;
  public ZComp(ZRoot inReality)
  {
    reality = inReality;
    if (reality == null)
      throw new NullPointerException("Null Reality");
    kids = new ZComp[ZCOMP_KIDS_BUF_INC_SIZE];
    numKids = 0;
    insets = new FloatInsets();
    size = new java.awt.Dimension();
    prefSize = new java.awt.geom.Rectangle2D.Float();
    loc = new java.awt.Point();
    boundsf = new java.awt.geom.Rectangle2D.Float();
    vis = true;
    fgColor = java.awt.Color.lightGray;
    //mouseListeners = new java.util.Vector();
    //mouseMotionListeners = new java.util.Vector();
  }
  public ZRoot getReality() { return reality; }
  public final ZComp[] getComponents() { return kids; }
  public final int getNumKids() { return numKids; }
  public final ZComp getParent() { return parent; }
  public final ZComp getTopParent()
  {
    if (parent == null)
      return null;
    else if (parent.parent == null)
      return parent;
    else
      return parent.getTopParent();
  }
  public final ZPseudoComp getTopPseudoParent()
  {
    ZComp currParent = parent;
    ZComp lastChild = this;
    do
    {
      if (currParent == null || !(currParent instanceof ZPseudoComp) ||
          (isPopup() != currParent.isPopup()))
      {
        return (lastChild instanceof ZPseudoComp) ? ((ZPseudoComp) lastChild) : null;
      }
      lastChild = currParent;
      currParent = currParent.parent;
    }while (true);
  }
  private final void addKid(ZComp addMe)
  {
    if (kids.length == numKids)
    {
      ZComp[] newKids = new ZComp[numKids + ZCOMP_KIDS_BUF_INC_SIZE];
      System.arraycopy(kids, 0, newKids, 0, numKids);
      kids = newKids;
    }
    kids[numKids++] = addMe;
    if (zOrderedKids != null)
      rebuildZOrderCache();
  }
  private final void removeKid(ZComp removeMe)
  {
    for (int i = 0; i < numKids; i++)
    {
      if (kids[i].equals(removeMe))
      {
        System.arraycopy(kids, i + 1, kids, i, numKids - i - 1);
        numKids--;
        kids[numKids] = null;
        if (zOrderedKids != null)
          rebuildZOrderCache();
      }
    }
  }
  public void add(ZComp addMe)
  {
    for (int i = 0; i < numKids; i++)
    {
      if (kids[i].equals(addMe))
      {
        // Move it to the end of the list
        // NOTE: This still makes us dirty!!!!
        if (i < numKids - 1)
        {
          System.arraycopy(kids, i + 1, kids, i, numKids - i - 1);
          kids[numKids - 1] = addMe;
          appendToDirty(true);
          if (zOrderedKids != null)
            rebuildZOrderCache();
        }
        return;
      }
    }
    if (addMe.parent != null) addMe.parent.remove(addMe);
    addKid(addMe);
    addMe.parent = this;
    appendToDirty(true);
  }
  public final void remove(ZComp removeMe)
  {
    if (removeMe.parent == this)
    {
      // NOTE: Only clear the focus if we're being removed from the pseudo hierarchy. If we're being removed from the
      // base component as part of a new menu load we do NOT want to clear the focus because it needs to be retained
      // in case we go back to the menu.
      if (removeMe.childIsFocused && (this instanceof ZPseudoComp))
      {
        ZPseudoComp topCop = removeMe.getTopPseudoParent();
        if (topCop != null)
          topCop.setFocus(null);
      }
      removeMe.parent = null;
      removeKid(removeMe);
      if (zOrderedKids != null)
        rebuildZOrderCache();
      appendToDirty(true);
    }
  }

  public final void removeAll()
  {
    while (numKids-- > 0)
    {
      if (kids[numKids].childIsFocused)
      {
        ZPseudoComp topCop = kids[numKids].getTopPseudoParent();
        if (topCop != null)
          topCop.setFocus(null);
      }
      kids[numKids].parent = null;
      kids[numKids] = null;
    }
    numKids = 0;
    if (zOrderedKids != null)
      rebuildZOrderCache();
    appendToDirty(true);
  }

  public FloatInsets getInsets() { return insets; }
  public final int getWidth() { return size.width; }
  public final int getHeight() { return size.height; }
  public final int getX() { return loc.x; }
  public final int getY() { return loc.y; }
  public final int getHitX() { return loc.x + (hitRectAdjust == null ? 0 : hitRectAdjust.x); }
  public final int getHitY() { return loc.y + (hitRectAdjust == null ? 0 : hitRectAdjust.y); }
  public final int getHitWidth() { return size.width + (hitRectAdjust == null ? 0 : hitRectAdjust.width); }
  public final int getHitHeight() { return size.height + (hitRectAdjust == null ? 0 : hitRectAdjust.height); }
  public final float getWidthf() { return boundsf.width; }
  public final float getHeightf() { return boundsf.height; }
  public final float getXf() { return boundsf.x; }
  public final float getYf() { return boundsf.y; }
  public java.awt.geom.Rectangle2D.Float getPreferredSize(float availableWidth, float availableHeight,
      float parentWidth, float parentHeight, int depth)
  {
    return new java.awt.geom.Rectangle2D.Float(0, 0, prefSize.width, prefSize.height);
  }
  public final void setBounds(java.awt.Rectangle r)
  {
    setBounds(r.x, r.y, r.width, r.height);
  }
  public final void setBounds(java.awt.geom.Rectangle2D.Float r)
  {
    setBounds(r.x, r.y, r.width, r.height);
  }
  public final void setBounds(int x, int y, int width, int height)
  {
    boolean dirtify = (loc.x != x || loc.y != y || size.width != width || size.height != height);
    if (dirtify)
      appendToDirty(false);
    loc.x = x; loc.y = y; size.width = width; size.height = height;
    if ((prefSize.width == 0) || (prefSize.height == 0))
    {
      prefSize.width = width;
      prefSize.height = height;
    }
    boundsf.setRect(x, y, width, height);
    if (dirtify)
      appendToDirty(true);
  }
  public final void setBounds(float x, float y, float width, float height)
  {
    if (reality.isIntegerPixels())
    {
      loc.x = (int)(x);
      loc.y = (int)(y);
      size.width = (int)(/*x+*/width);// - loc.x;
      size.height = (int)(/*y+*/height);// - loc.y;
      x = loc.x;
      y = loc.y;
      width = size.width;
      height = size.height;
    }
    boolean dirtify = (Math.abs(boundsf.x - x) > 0.01 || Math.abs(boundsf.y - y) > 0.01 || Math.abs(boundsf.width - width) > 0.01 || Math.abs(boundsf.height - height) > 0.01);
    if (dirtify)
      appendToDirty(false);
    boundsf.setRect(x, y, width, height);
    if ((prefSize.width == 0) || (prefSize.height == 0))
    {
      prefSize.width = width;
      prefSize.height = height;
    }
    if (!reality.isIntegerPixels())
    {
      loc.x = (int)(x);
      loc.y = (int)(y);
      size.width = (int) Math.ceil(x+width) - loc.x;
      size.height = (int) Math.ceil(y+height) - loc.y;
    }
    if (dirtify)
      appendToDirty(true);
  }
  public java.awt.Rectangle getBounds()
  {
    return new java.awt.Rectangle(loc.x, loc.y, size.width, size.height);
  }
  public java.awt.Rectangle getTrueBounds()
  {
    return new java.awt.Rectangle(getTrueX(), getTrueY(), size.width, size.height);
  }
  public java.awt.geom.Rectangle2D.Float getBoundsf()
  {
    return new java.awt.geom.Rectangle2D.Float(boundsf.x, boundsf.y, boundsf.width, boundsf.height);
  }
  public java.awt.geom.Rectangle2D.Float getTrueBoundsf()
  {
    return new java.awt.geom.Rectangle2D.Float(getTrueXf(), getTrueYf(), boundsf.width, boundsf.height);
  }
  public java.awt.geom.Rectangle2D.Float getInsetTrueBoundsf()
  {
    return new java.awt.geom.Rectangle2D.Float(getTrueXf() + insets.left, getTrueYf() + insets.top,
        boundsf.width - insets.left - insets.right, boundsf.height - insets.top - insets.bottom);
  }
  public boolean isVisible()
  {
    return vis;
  }
  public final int getTrueX()
  {
    int rv = loc.x;
    ZComp currParent = parent;
    while (currParent != null)
    {
      rv += currParent.loc.x;
      currParent = currParent.parent;
    }
    return rv;
  }
  public final int getTrueY()
  {
    int rv = loc.y;
    ZComp currParent = parent;
    while (currParent != null)
    {
      rv += currParent.loc.y;
      currParent = currParent.parent;
    }
    return rv;
  }
  public final float getTrueXf()
  {
    float rv = boundsf.x;
    ZComp currParent = parent;
    while (currParent != null)
    {
      rv += currParent.boundsf.x;
      currParent = currParent.parent;
    }
    return rv;
  }
  public final float getTrueYf()
  {
    float rv = boundsf.y;
    ZComp currParent = parent;
    while (currParent != null)
    {
      rv += currParent.boundsf.y;
      currParent = currParent.parent;
    }
    return rv;
  }
  /*	protected void clipRectToBounds(java.awt.geom.Rectangle2D.Float clipRect)
	{
		if (clipRect.x < boundsf.x)
		{
			clipRect.width -= boundsf.x - clipRect.x;
			clipRect.x = boundsf.x;
		}
		if (clipRect.y < boundsf.y)
		{
			clipRect.height -= boundsf.y - clipRect.y;
			clipRect.y = boundsf.y;
		}
		if (clipRect.x + clipRect.width > boundsf.x + boundsf.width)
		{
			clipRect.width = boundsf.x + boundsf.width - clipRect.x;
		}
		if (clipRect.y + clipRect.height > boundsf.y + boundsf.height)
		{
			clipRect.height = boundsf.y + boundsf.height - clipRect.y;
		}
	}
   */	protected void clipRectToBounds(java.awt.geom.Rectangle2D.Float clipRect, float xoff, float yoff)
   {
     float tx = xoff + boundsf.x;
     float ty = yoff + boundsf.y;
     if (clipRect.x < tx)
     {
       clipRect.width -= tx - clipRect.x;
       clipRect.x = tx;
     }
     if (clipRect.y < ty)
     {
       clipRect.height -= ty - clipRect.y;
       clipRect.y = ty;
     }
     if (clipRect.x + clipRect.width > tx + boundsf.width)
     {
       clipRect.width = tx + boundsf.width - clipRect.x;
     }
     if (clipRect.y + clipRect.height > ty + boundsf.height)
     {
       clipRect.height = ty + boundsf.height - clipRect.y;
     }
   }
   public boolean testRectIntersect(java.awt.geom.Rectangle2D.Float r, float xoff, float yoff)
   {
     float th = boundsf.height;
     float tw = boundsf.width;
     float rw = r.width;
     float rh = r.height;
     if (rw <= 0 || rh <= 0 || tw <= 0 || th <= 0) {
       return false;
     }
     float tx = xoff + boundsf.x;
     float ty = yoff + boundsf.y;
     float rx = r.x;
     float ry = r.y;
     rw += rx;
     rh += ry;
     tw += tx;
     th += ty;
     //      overflow || intersect
     return ((rw < rx || rw > tx) &&
         (rh < ry || rh > ty) &&
         (tw < tx || tw > rx) &&
         (th < ty || th > ry));
   }
   public void buildRenderingOps(java.util.ArrayList opList, java.awt.geom.Rectangle2D.Float clipRect,
       int diffuseColor, float alphaFactor, float xoff, float yoff, int flags)
   {
     boolean inRegion = clipRect.intersects(boundsf);
     if (!inRegion && !reality.getUIMgr().disableParentClip()) return;
     float orgclipx=clipRect.x, orgclipy=clipRect.y, orgclipw=clipRect.width, orgcliph=clipRect.height;
     if (!reality.getUIMgr().disableParentClip())
       clipRectToBounds(clipRect, xoff, yoff);
     if (numKids > 0)
     {
       // Transform the clip rect into the next space, this is backwards because we're
       // dealing with a user space coordinate system change while the render xform goes from
       // user to device space
       //			clipRect.x -= boundsf.x;
       //			clipRect.y -= boundsf.y;
       xoff += boundsf.x;
       yoff += boundsf.y;
       for (int i = 0; i < numKids; i++)
       {
         try
         {
           kids[i].buildRenderingOps(opList, clipRect, diffuseColor, alphaFactor, xoff, yoff, flags);
         }
         catch (Exception e)
         {
           System.out.println("Error painting child:" + e);
           e.printStackTrace();
         }
       }
     }
     clipRect.setFrame(orgclipx, orgclipy, orgclipw, orgcliph);
   }
   public final void paintAll(java.awt.Graphics2D g2)
   {
     java.awt.Rectangle clipRect = g2.getClipBounds();
     boolean inRegion = ((clipRect == null) ||
         clipRect.intersects(loc.x, loc.y, size.width, size.height));
     if (!inRegion) return;
     g2.translate(loc.x, loc.y);
     g2.clipRect(0, 0, size.width, size.height); // clip before we do our own paint!
     paint(g2);
     if (numKids > 0)
     {
       for (int i = 0; i < numKids; i++)
       {
         try
         {
           kids[i].paintAll(g2);
         }
         catch (Exception e)
         {
           System.out.println("Error painting child:" + e);
           e.printStackTrace();
         }
       }
     }
     paintLast(g2);
     g2.translate(-loc.x, -loc.y);
     g2.setClip(clipRect);
   }
   // We ALWAYS paint with location at 0,0
   protected void paint(java.awt.Graphics2D g2)
   {
     if (bgColor != null)
     {
       g2.setColor(bgColor);
       g2.fillRect(0, 0, size.width, size.height);
     }
   }
   protected void paintLast(java.awt.Graphics2D g2){}
   protected void repaint()
   {
     appendToDirty(false);
   }

   public void appendToDirty(boolean invalidatesLayout)
   {
     if (invalidatesLayout)
     {
       invalidateAll();
     }
     if (boundsf.width > 0 && boundsf.height > 0)
     {
       java.awt.Rectangle rv = new java.awt.Rectangle();
       float truex = getTrueXf();
       float truey = getTrueYf();
       rv.x = Math.max(0, (int)(truex));
       rv.y = Math.max(0, (int)(truey));
       rv.width = ((int)Math.ceil(truex + boundsf.width)) - rv.x;
       rv.height = ((int)Math.ceil(truey + boundsf.height)) - rv.y;
       if (hitRectAdjust != null && (hitRectAdjust.x != 0 || hitRectAdjust.y != 0))
       {
         if (hitRectAdjust.x < 0)
         {
           rv.x += hitRectAdjust.x;
           rv.width -= hitRectAdjust.x;
         }
         else
         {
           rv.width += hitRectAdjust.x;
         }
         if (hitRectAdjust.y < 0)
         {
           rv.y += hitRectAdjust.y;
           rv.height -= hitRectAdjust.y;
         }
         else
         {
           rv.height += hitRectAdjust.y;
         }
       }
       if (maxEffectZoom > 1)
       {
         int adjX = (int)Math.ceil(maxEffectZoom * rv.width);
         rv.x -= adjX;
         rv.width += 2*adjX;
         int adjY = (int)Math.ceil(maxEffectZoom * rv.height);
         rv.y -= adjY;
         rv.height += 2*adjY;
       }
       reality.appendToDirty(rv);
     }
   }

   void invalidateAll()
   {
     if (validLayout)
     {
       validLayout = false;
       ZComp tempParent = parent;
       while (tempParent != null && tempParent.validLayout)
       {
         tempParent.validLayout = false;
         tempParent = tempParent.parent;
       }
     }
     if (!childrenWereInvalidated)
     {
       childrenWereInvalidated = true;
       for (int i = 0; i < numKids; i++)
         kids[i].invalidateAll();
     }
   }

   protected void forceChildrenToBeValid()
   {
     if (validLayout && !childrenWereInvalidated) return;
     validLayout = true;
     childrenWereInvalidated = false;
     for (int i = 0; i < numKids; i++)
       kids[i].forceChildrenToBeValid();
   }

   protected void recalculateDynamicFonts()
   {
     for (int i = 0; i < numKids; i++)
       kids[i].recalculateDynamicFonts();
   }
   public final void doLayout()
   {
     if (validLayout) return;
     validLayout = true;
     childrenWereInvalidated = false;
     doLayoutNow();
   }
   protected void doLayoutNow()
   {
     if (numKids == 0) return;
     int x, y;
     x = y = 0;
     int maxX = 0;
     int maxY = 0;
     for (int i = 0; i < numKids; i++)
     {
       ZComp kid = kids[i];
       if (kid.numKids == 0)
         kid.doLayout();
       if (horizLay)
       {
         kid.setBounds(x, (getHeight() - kid.prefSize.height)/2,
             kid.prefSize.width, kid.prefSize.height);
         x += kid.size.width;
         maxY = Math.max(maxY, y + kid.size.height);
         maxX = x;
       }
       else
       {
         kid.setBounds((getWidth() - kid.prefSize.width)/2, y,
             kid.prefSize.width, kid.prefSize.height);
         y += kid.size.height;
         maxX = Math.max(maxX, x + kid.size.width);
         maxY = y;
       }
       if (kid.numKids > 0)
         kid.doLayout();
     }
     if (prefSize.width < maxX) prefSize.width = maxX;
     if (prefSize.height < maxY) prefSize.height = maxY;

     if (evenLay)
     {
       int extra = horizLay ? (getWidth() - maxX) : (getHeight() - maxY);
       extra /= (numKids + 1);
       for (int i = 0; i < numKids; i++)
       {
         ZComp kid = kids[i];
         if (horizLay)
           kid.loc.x += extra * (i + 1);
         else
           kid.loc.y += extra * (i + 1);
         if (extra < 0)
         {
           if (horizLay)
             kid.size.width -= extra;
           else
             kid.size.height -= extra;
         }
       }
     }
   }

   public void setHorizontal(boolean x) { horizLay = x; }

   public void setBackground(java.awt.Color inColor) { bgColor = inColor; }
   public void setForeground(java.awt.Color inColor) { fgColor = inColor; }
   public java.awt.Color getForeground() { return fgColor; }
   public void setForegroundShadow(java.awt.Color inColor) { fgShadowColor = inColor; }
   public java.awt.Color getForegroundShadow() { return fgShadowColor; }
   public void setUseBGImage(boolean x) { useBGImage = x; }

   public String toString()
   {
     return getClass().getName() + "[loc=" + loc + " size=" + size + ']';
   }

   public String getTip() { return null; }

   public void addMouseListener(java.awt.event.MouseListener x)
   {
     if (mouseListeners == null)
       mouseListeners = new java.util.Vector();
     if (!mouseListeners.contains(x))
       mouseListeners.addElement(x);
   }
   public void removeMouseListener(java.awt.event.MouseListener x)
   {
     if (mouseListeners != null)
       mouseListeners.remove(x);
   }
   public void addMouseListenerRecursive(java.awt.event.MouseListener x, boolean alwaysAdd)
   {
     // Recursively add mouselisteners all the way down
     addMouseListener(x);
     for (int i = 0; i < numKids; i++)
     {
       // Don't overlap mouse listeners or we'll get parallel execution of actions
       // which we don't want (i.e. the Item will trigger and so will the child that's
       // listening for whatever reason)
       // NARFLEX - 2/17/10 - Always add them; the proper way to handle this is to mark the
       // events as consumed. Otherwise Enter/Exit trackers will block click events.
       /*			if (alwaysAdd || ((kids[i].mouseListeners == null || kids[i].mouseListeners.size() == 0) &&
				(!(kids[i] instanceof ZImage) ||
				((ZImage) kids[i]).actionListeners.size() == 0)))*/
       {
         kids[i].addMouseListenerRecursive(x, alwaysAdd);
       }
     }
   }
   public void addMouseMotionListener(java.awt.event.MouseMotionListener x)
   {
     if (mouseMotionListeners == null)
       mouseMotionListeners = new java.util.Vector();
     if (!mouseMotionListeners.contains(x))
       mouseMotionListeners.addElement(x);
   }
   public void removeMouseMotionListener(java.awt.event.MouseMotionListener x)
   {
     if (mouseMotionListeners != null)
       mouseMotionListeners.remove(x);
   }
   public void addMouseMotionListenerRecursive(java.awt.event.MouseMotionListener x, boolean alwaysAdd)
   {
     // Recursively add mouselisteners all the way down
     addMouseMotionListener(x);
     for (int i = 0; i < numKids; i++)
     {
       // NOTE: This conditionality is cloned from addMouseListenerRecursive; not sure if we need it for motion listeners too
       // Don't overlap mouse listeners or we'll get parallel execution of actions
       // which we don't want (i.e. the Item will trigger and so will the child that's
       // listening for whatever reason)
       /*			if (alwaysAdd || ((kids[i].mouseMotionListeners == null || kids[i].mouseMotionListeners.size() == 0) &&
				(!(kids[i] instanceof ZImage) ||
				((ZImage) kids[i]).actionListeners.size() == 0)))*/
       {
         kids[i].addMouseMotionListenerRecursive(x, alwaysAdd);
       }
     }
   }
   protected void processMouseEvent(java.awt.event.MouseEvent evt)
   {
     // NOTE: 12/31/09 - WOW - that was an old bug...duh...of course we need the UI lock when we're processing mouse
     // events since its modifying the UI hierarchy potentially.
     if (evt.getID() == java.awt.event.MouseEvent.MOUSE_MOVED || evt.getID() == java.awt.event.MouseEvent.MOUSE_DRAGGED)
     {
       if (mouseMotionListeners != null)
       {
         boolean[] acquiredLock = new boolean[1];
         if (!reality.getUIMgr().getLock(true, acquiredLock, true))
         {
           if (Sage.DBG) System.out.println("Skipping event due to debug mode:" + evt);
           return;
         }
         try
         {
           for (int i = 0; i < mouseMotionListeners.size(); i++)
           {
             switch (evt.getID())
             {
               case java.awt.event.MouseEvent.MOUSE_MOVED:
                 ((java.awt.event.MouseMotionListener) mouseMotionListeners.elementAt(i)).mouseMoved(evt);
                 break;
               case java.awt.event.MouseEvent.MOUSE_DRAGGED:
                 ((java.awt.event.MouseMotionListener) mouseMotionListeners.elementAt(i)).mouseDragged(evt);
                 break;
             }
             if (evt.isConsumed())
               break;
           }
         }
         finally
         {
           if (acquiredLock[0])
             reality.getUIMgr().clearLock();
         }
       }
     }
     else
     {
       if (mouseListeners != null)
       {
         boolean[] acquiredLock = new boolean[1];
         if (!reality.getUIMgr().getLock(true, acquiredLock, true))
         {
           if (Sage.DBG) System.out.println("Skipping event due to debug mode:" + evt);
           return;
         }
         try
         {
           for (int i = 0; i < mouseListeners.size(); i++)
           {
             switch (evt.getID())
             {
               case java.awt.event.MouseEvent.MOUSE_CLICKED:
                 ((java.awt.event.MouseListener) mouseListeners.elementAt(i)).mouseClicked(evt);
                 break;
               case java.awt.event.MouseEvent.MOUSE_PRESSED:
                 ((java.awt.event.MouseListener) mouseListeners.elementAt(i)).mousePressed(evt);
                 break;
               case java.awt.event.MouseEvent.MOUSE_RELEASED:
                 ((java.awt.event.MouseListener) mouseListeners.elementAt(i)).mouseReleased(evt);
                 break;
               case java.awt.event.MouseEvent.MOUSE_ENTERED:
                 ((java.awt.event.MouseListener) mouseListeners.elementAt(i)).mouseEntered(evt);
                 break;
               case java.awt.event.MouseEvent.MOUSE_EXITED:
                 ((java.awt.event.MouseListener) mouseListeners.elementAt(i)).mouseExited(evt);
                 break;
             }
             if (evt.isConsumed())
               break;
           }
         }
         finally
         {
           if (acquiredLock[0])
             reality.getUIMgr().clearLock();
         }
       }
     }
     //System.out.println("MouseEvent=" + evt);
   }

   public boolean isFocusable()
   {
     return false;
   }
   public boolean hasFocusableChildren()
   {
     for (int i = 0; i < numKids; i++)
     {
       if (kids[i].isFocusable() || kids[i].hasFocusableChildren())
         return true;
     }
     return false;
   }
   public boolean isFocused()
   {
     return focused/* && isFocusable()*/;
   }
   public final boolean doesHierarchyHaveFocus()
   {
     return childIsFocused;
   }
   public final boolean doesAncestorOrMeHaveFocus()
   {
     return parentIsFocused;
   }
   protected boolean isChildInSameFocusHierarchy(ZComp childComp)
   {
     return true;
   }

   protected boolean setFocus(ZComp focusMe, boolean parentTookFocus, java.util.ArrayList postFocusProcessing)
   {
     if (!isFocusable())
       focused = false;
     else
       focused = (focusMe == this);
     parentIsFocused = parentTookFocus || focused;
     childIsFocused = focused;
     for (int i = 0; i < numKids; i++)
     {
       if (isChildInSameFocusHierarchy(kids[i]))
         childIsFocused = kids[i].setFocus(focusMe, parentIsFocused, postFocusProcessing) || childIsFocused;
     }
     if (childIsFocused)
       focusedChild = focusMe;
     //		else
     //			focusedChild = null;
     return childIsFocused;
   }
   public boolean setDefaultFocus() // returns true if it took the focus
   {
     if (isFocusable())
     {
       ZPseudoComp topCop = getTopPseudoParent();
       if (topCop != null)
         topCop.setFocus(this);
       else // isFocusable is false here so this has to be a ZPseudoComp
         ((ZPseudoComp)this).setFocus(this);
       return true;
     }
     for (int i = 0; i < numKids; i++)
     {
       if (kids[i].setDefaultFocus())
         return true;
     }
     return false;
   }
   public void addFocusableChildrenToList(java.util.ArrayList v)
   {
     if (isFocusable() && size.width > 0 && size.height > 0)
       v.add(this);
     for (int i = 0; i < numKids; i++)
       kids[i].addFocusableChildrenToList(v);
   }
   public final ZComp getFocusOwner(boolean checkUpward)
   {
     if (focusedChild != null && childIsFocused) return focusedChild;
     if (checkUpward && parent != null)
       return parent.getFocusOwner(true);
     return null;
   }
   public ZComp getLastFocusedChild()
   {
     return focusedChild;
   }

   public boolean isPopup()
   {
     if (this instanceof ZPseudoComp && ((ZPseudoComp)this).widgType == Widget.OPTIONSMENU)
       return true;
     ZComp currParent = parent;
     while (currParent != null)
     {
       if (currParent instanceof ZPseudoComp && ((ZPseudoComp)currParent).widgType == Widget.OPTIONSMENU)
         return true;
       currParent = currParent.parent;
     }
     return false;
   }

   public boolean needsLayout() { return !validLayout; }

   public boolean isSingleCompChain()
   {
     if (numKids == 0) return true;
     else if (numKids == 1)
       return kids[0].isSingleCompChain();
     else
       return false;
   }

   public void cleanup()
   {
     if (registeredAnimation)
     {
       reality.unregisterAnimation(this);
       registeredAnimation = false;
     }
     for (int i = 0; i < numKids; i++)
     {
       kids[i].cleanup();
     }
   }

   // It is the responsibility of the ZComp to unregister or re-register itself in this callback if necessary.
   public void animationCallback(long animationTime)
   {
   }
   protected void evaluateTree(boolean doComps, boolean doData)
   {
   }
   public boolean action(UserEvent evt)
   {
     return false;
   }
   boolean passesConditional()
   {
     return true;
   }

   void rebuildZOrderCache()
   {
     if (zOrderedKids == null || zOrderedKids.length != kids.length)
       zOrderedKids = new ZComp[kids.length];
     if (zOrderedKids.length > numKids)
       java.util.Arrays.fill(zOrderedKids, numKids, zOrderedKids.length, null);
     if (numKids > 0)
     {
       System.arraycopy(kids, 0, zOrderedKids, 0, numKids);
       java.util.Arrays.sort(zOrderedKids, 0, numKids, zOrderSorter);
     }
   }

   protected ZComp[] getZOrderCache()
   {
     return (zOrderedKids != null) ? zOrderedKids : kids;
   }

   public boolean isMouseTransparent()
   {
     return mouseTransparency;
   }

   protected void clearRecursiveChildContexts2(Catbert.Context parentContext)
   {
   }

   protected void reloadAttributeContext()
   {
   }

   protected void unfreshAttributeContext()
   {
   }

   protected boolean processHideEffects(boolean validRegion)
   {
     return false;
   }

   public final int getHitAdjustX() { return ((parent == null) ? 0 : parent.getHitAdjustX()) + (hitRectAdjust == null ? 0 : hitRectAdjust.x); }
   public final int getHitAdjustY() { return ((parent == null) ? 0 : parent.getHitAdjustY()) + (hitRectAdjust == null ? 0 : hitRectAdjust.y); }

   protected FloatInsets insets;
   protected java.awt.Dimension size;
   protected java.awt.geom.Rectangle2D.Float prefSize;
   protected java.awt.Point loc;
   protected boolean vis;
   protected ZComp parent;
   protected ZComp[] kids;
   protected ZComp[] zOrderedKids;
   protected int numKids;
   protected ZRoot reality;
   protected java.awt.Color bgColor;
   protected java.awt.Color fgColor;
   protected java.awt.Color fgShadowColor;
   protected boolean horizLay = true;
   protected boolean evenLay = false;
   protected boolean useBGImage = false;
   protected java.util.Vector mouseListeners;
   protected java.util.Vector mouseMotionListeners;
   private boolean focused;
   private boolean validLayout = false;
   private boolean childrenWereInvalidated = false;
   protected java.awt.geom.Rectangle2D.Float boundsf;
   protected java.awt.Rectangle hitRectAdjust;
   protected float maxEffectZoom;

   protected int childWidgetIndex;

   private boolean childIsFocused;
   private boolean parentIsFocused;
   // focusedChild will be the last focused child; if childIsFocused is also true; then it actually does have focus
   private ZComp focusedChild;

   protected boolean registeredAnimation;
   boolean backgroundComponent;

   // Used for altering the Z-order of components
   protected int zOffset;

   protected boolean mouseTransparency = false; // if true then mouse events should not be processed by this component's hiearchy

   protected long perfTime;

   private static final java.util.Comparator zOrderSorter  = new java.util.Comparator()
   {
     public int compare(Object o1, Object o2)
     {
       ZComp z1 = (ZComp) o1;
       ZComp z2 = (ZComp) o2;
       return z1.zOffset - z2.zOffset;
     }
   };
}
