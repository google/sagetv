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

public abstract class SageRenderer
{
  public static final String TRANSLUCENT_RENDERING = "translucent_rendering";
  public static final String ALPHA_CUTOFF_VALUE_FLOAT = "alpha_cutoff_value_float";
  public static final String ALPHA_CUTOFF_VALUE_FLOAT_NO_CKEY = "alpha_cutoff_value_float_no_ckey";
  public static final int FONT_TEXTURE_MAP_SIZE = 128;
  protected static final String SURF_DUP_SUFFIX = "_STV_SURF_DUP_";
  public static final boolean ANIM_DEBUG = false;
  protected SageRenderer(ZRoot inMaster)
  {
    master = inMaster;
    uiMgr = master.getUIMgr();
    bgLoader = new BGResourceLoader(uiMgr, this);
    imageLoadPauseTime = uiMgr.getLong("ui/parallel_image_load_pause_time", 200);
    disableAllTransparency = uiMgr.getBoolean("ui/disable_all_transparency", false);
  }

  public java.util.ArrayList getLastRenderedDisplayList()
  {
    synchronized (dlQueueLock)
    {
      if (currDisplayList != null)
        return (java.util.ArrayList)currDisplayList.clone();
      if (lastDisplayList != null)
        return (java.util.ArrayList)lastDisplayList.clone();
    }
    return null;
  }

  /**
   * Subclasses can override this to provide context for the MENU_HINTS property to pass to the client.
   *
   * @return
   */
  public void setMenuHint(String menuName, String popupName, boolean hasTextInput)
  {
    // doing nothing for most renderers.
  }

  public abstract boolean allocateBuffers(int width, int height);

  public void preprocessNextDisplayList(java.util.ArrayList v)
  {

  }

  public final void setNextDisplayList(java.util.ArrayList v)
  {
    if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("SageRenderer set a new display list size:" + v.size());
    synchronized (dlQueueLock)
    {
      nextDisplayList = v;
    }
  }

  // Returns true if this consumed a new display list, false if it had to repeat one.
  public final boolean makeNextDisplayListCurrent()
  {
    synchronized (dlQueueLock)
    {
      if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("SageRenderer makeNextDisplayListCurrent nextNull=" + (nextDisplayList==null) +
          " currNull=" + (currDisplayList==null) + " lastNull=" + (lastDisplayList==null));
      if (nextDisplayList == null)
        return false;
      if (lastDisplayList != null)
      {
        lastDisplayList.clear();
        lastDisplayList = null;
      }
      lastDisplayList = currDisplayList;
      currDisplayList = nextDisplayList;
      nextDisplayList = null;
    }
    return true;
  }

  public void cullDisplayList(java.util.ArrayList dl)
  {
    java.awt.Color avoidColor = uiMgr.getVideoFrame().getColorKey();
    boolean anyTransparency = ((Java2DSageRenderer.hasOSDRenderer(uiMgr) ||
        (uiMgr.getBoolean("ui/enable_stippling_for_2d", true) && uiMgr.getBoolean("overlay_color_keying_enabled", true)) ||
        !uiMgr.getVideoFrame().isNonBlendableVideoPlayerLoaded() ||
        !uiMgr.isMainTV()) &&
        uiMgr.getBoolean(TRANSLUCENT_RENDERING, true)) || avoidColor == null;
    boolean removeRemaining = false;
    int avoidColorValue = (avoidColor == null) ? 0 : (avoidColor.getRGB() & 0xFFFFFF);
    java.awt.Color replaceColor = null;
    if (avoidColor != null)
    {
      replaceColor = new java.awt.Color(avoidColor.getRed(), avoidColor.getGreen(), avoidColor.getBlue() == 255 ?
          254 : (avoidColor.getBlue() + 1));
    }
    for (int i = dl.size() - 1; i >= 0; i--)
    {
      RenderingOp ro = (RenderingOp) dl.get(i);
      if (ro.isAnimationOp() || ro.isSurfaceOp() || ro.isEffectOp())
        continue; // these kept on getting removed here when we wanted them to stay and I don't see a reason to cull them yet
      if (removeRemaining)
      {
        dl.remove(i);
        continue;
      }
      if (ro.alphaFactor == 0 ||
          (!anyTransparency && ro.alphaFactor <=
          (uiMgr.getBoolean("overlay_color_keying_enabled", true) ? uiMgr.getFloat(ALPHA_CUTOFF_VALUE_FLOAT, 0.7f) :
            uiMgr.getFloat(ALPHA_CUTOFF_VALUE_FLOAT_NO_CKEY, 0.1f))))
      {
        dl.remove(i);
        continue;
      }

      // null images have no point in being rendered since they're completely transparent anyways!!
      if (ro.isImageOp() && ro.texture.isNullOrFailed())
      {
        dl.remove(i);
        continue;
      }
      if (disableAllTransparency && ro.alphaFactor < 1.0f)
      {
        ro.alphaFactor = 1.0f;
      }

      // Avoid using the transparent color key if its enabled, mask off the alpha bits
      if (avoidColor != null)
      {
        if (ro.renderColor != null && avoidColorValue == (ro.renderColor.getRGB() & 0xFFFFFF))
          ro.renderColor = replaceColor;
        if (ro.primitive != null && ro.primitive.color != null &&
            avoidColorValue == (ro.primitive.color.getRGB() & 0xFFFFFF))
          ro.primitive.color = replaceColor;
        if (ro.primitive != null && ro.primitive.gradc1 != null &&
            avoidColorValue == (ro.primitive.gradc1.getRGB() & 0xFFFFFF))
          ro.primitive.gradc1 = replaceColor;
        if (ro.primitive != null && ro.primitive.gradc2 != null &&
            avoidColorValue == (ro.primitive.gradc2.getRGB() & 0xFFFFFF))
          ro.primitive.gradc2 = replaceColor;
      }

      // Full screen images with transparent pixels shouldn't cull what's behind them.
      // For now we can only be sure something is covered if its video or a primitive rect fill
      // without transparency.  But if we don't have blending in the UI then regioning/color keying
      // could cause things to show that wouldn't otherwise in which case we can't cull anything.
      if (ro.destRect != null &&
          Math.round(ro.destRect.width) == master.getRoot().getWidth() &&
          Math.round(ro.destRect.height) == master.getRoot().getHeight() &&
          ro.alphaFactor == 1.0f && (ro.isVideoOp() || ro.isPrimitiveOp()))
      {
        if (ro.isPrimitiveOp() && (!ro.primitive.fill || !"Rectangle".equals(ro.primitive.shapeType) ||
            ro.primitive.cornerArc > 0))
          continue; // unfilled shapes don't overlap, same with non-rectangles and rectangles with rounded corners
        // This fills the whole screen and it has no transparency so kill anything that
        // that's drawn before it.
        removeRemaining = true;
      }
    }
  }

  public java.util.ArrayList getCoveredRegions(java.util.ArrayList testdl, RenderingOp theOp)
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    boolean foundTheOp = false;
    java.awt.geom.Rectangle2D.Float videoRect = null;
    int dlsize = testdl.size();
    for (int i = 0; i < dlsize; i++)
    {
      RenderingOp op = (RenderingOp) testdl.get(i);
      if (op == theOp)
      {
        foundTheOp = true;
        videoRect = op.destRect;
        continue;
      }
      if (op.alphaFactor == 0 || !foundTheOp)
        continue;
      if (op.destRect.width > 0 && op.destRect.height > 0 && op.destRect.intersects(videoRect))
      {
        java.awt.geom.RoundRectangle2D.Float newRect = null;
        if (op.isImageOp() || op.isTextOp())
          newRect = new java.awt.geom.RoundRectangle2D.Float(op.destRect.x, op.destRect.y, op.destRect.width + 1,
              op.destRect.height + 1, 0, 0);
        else if (op.isPrimitiveOp() && op.primitive.fill)
          newRect = new java.awt.geom.RoundRectangle2D.Float(op.destRect.x, op.destRect.y, op.destRect.width + 1,
              op.destRect.height + 1, op.primitive.cornerArc, op.primitive.cornerArc);

        if (newRect != null)
          rv.add(newRect);
      }
    }
    return rv;
  }

  protected static String getDupSurfName(RenderingOp op)
  {
    return op.anime.isBehind() ? getDupSurfName(op.surface, true, false) :
      getDupSurfName(op.surface, op.anime.isIn(), op.anime.isOut());
  }
  protected static String getDupSurfName(String s, boolean inny, boolean outy)
  {
    StringBuffer sb = new StringBuffer(s.length() + SURF_DUP_SUFFIX.length() + 2);
    if (outy)
      sb.append('z');
    sb.append(s.substring(0, s.length() - 1));
    // Animations should go on top of us. For IN animations, those don't use the dup surf in the anim so they should have the dup
    // surface lowered one. For OUT animations, the dup surf should be on top
    sb.append((char)((s.charAt(s.length() - 1)) - (inny ? 1 : (outy ? -1 : 0))));
    sb.append(SURF_DUP_SUFFIX);
    return sb.toString();
  }

  protected boolean isBackgroundSurface(String surface)
  {
    String bgSurfName = uiMgr.get("ui/animation/background_surface_name", "BG");
    if (bgSurfName.equals(surface))
      return true;
    if (getDupSurfName(bgSurfName, true, false).equals(surface))
      return true;
    if (getDupSurfName(bgSurfName, false, true).equals(surface))
      return true;
    if (getDupSurfName(bgSurfName, false, false).equals(surface))
      return true;
    return false;
  }

  protected void fixDuplicateAnimSurfs(java.util.ArrayList testdl, java.awt.Rectangle clipRect)
  {
    // This goes through the display list and finds any 'Out' animation operations. Then it looks to
    // see if there's any surface operations that also use that same surface as the animation. If it finds
    // any matches; then it renames the surface used in the animation (in the rop and in the cache) so it won't
    // get overwritten by the surface op.
    // It also has to do any compositing of surfaces that are on top of it so that the duplicated surface has the right layers
    // rendered into it.
    java.util.Set usedSurfs = new java.util.HashSet();
    java.util.Set nestedSurfs = new java.util.HashSet();
    java.util.Stack tempSurfStack = new java.util.Stack();
    java.util.Set alreadyDupedDualSurfs = new java.util.HashSet();
    int dlsize = testdl.size();
    for (int i = 0; i < dlsize; i++)
    {
      RenderingOp op = (RenderingOp) testdl.get(i);
      if (op.isSurfaceOp() && surfaceCache.containsKey(op.surface) && !op.isImageOp())
      {
        if (op.isSurfaceOpOn())
        {
          usedSurfs.add(op.surface);
          if (tempSurfStack.contains(op.surface))
            nestedSurfs.add(op.surface);
          tempSurfStack.push(op.surface);
        }
        else
        {
          tempSurfStack.pop();
        }
      }
    }
    for (int i = 0; i < dlsize; i++)
    {
      RenderingOp op = (RenderingOp) testdl.get(i);
      if (op.isAnimationOp() && !op.anime.cleanedDL && usedSurfs.contains(op.surface))
      {
        if (op.anime.isOut() || op.anime.isDualSurfaceOp())
        {
          // This is duplicate surface usage where we need to use a last rendered state as part of an animation.
          // We need to do 3 things.
          // 1. Remove this surface from the cache at it's old entry and put it in at a new 'dup' entry
          // 2. Update any Animation Out ROPs that were accessing that surface to use the dup surface instead
          // 3. Composite any child layers with the dup'd surface
          // This may have already been removed from another op
          Object oldSurface = surfaceCache.remove(op.surface);
          String dupSurfName = getDupSurfName(op);
          if (oldSurface != null)
          {
            if (ANIM_DEBUG) System.out.println("Detected duplicate surface usage. Making dup backing copy for " + op.surface);
            // Find any composite operations that are on top of this surface and execute them now...BUT we can't just use
            // the composite ops because it might not have enough info. We need to copy everything from its backing surface; which
            // is why we use the rect of the animation op.
            //						if (!op.anime.isBehind())
            {
              boolean compositeRemaining = false;
              java.util.Set surfsCompositedNow = new java.util.HashSet();
              int orgsize = orgCompositeOps.size();
              for (int j = 0; j < orgsize; j++)
              {
                RenderingOp op2 = (RenderingOp) orgCompositeOps.get(j);
                if (compositeRemaining && op2.isSurfaceOp() && !surfsCompositedNow.contains(op2.surface))
                {
                  // NOTE: This is questionable about what state we want here. Currently we render any Focus
                  // surfaces that are on top of the out animated surface. This will only affect BG animations and not
                  // FG animations since the Focus layer lies between them.
                  //								if (op2.surface == null || !op2.surface.endsWith("Focus"))
                  {
                    Object newSurface = surfaceCache.get(op2.surface);
                    if (newSurface != null)
                    {
                      if (ANIM_DEBUG) System.out.println("Composting last surface op for " + op2.surface + " with dup backing copy for transitional animation");
                      if (compositeSurfaces(oldSurface, newSurface, op2.alphaFactor,
                          (op.anime.animType == RenderingOp.Animation.MORPH || op.anime.animType == RenderingOp.Animation.SMOOTH) ?
                              op.anime.orgSrcRect.createIntersection(clipRect) : op.destRect.createIntersection(clipRect)))
                        surfsCompositedNow.add(op2.surface);
                    }
                  }
                }
                else if (op2.isImageOp() && (op2.isAnimationOp() || op2.isSurfaceOp()) && this instanceof MiniClientSageRenderer)
                {
                  // Check to see if this image is also in the new display list; and if it is then we don't composite it here
                  // since that operation will take care of the display of it
                  boolean imageReused = false;
                  for (int k = 0; k < dlsize; k++)
                  {
                    if (((RenderingOp) testdl.get(k)).texture == op2.texture)
                    {
                      imageReused = true;
                      break;
                    }
                  }
                  if (!imageReused)
                  {
                    if (ANIM_DEBUG) System.out.println("Found a hi res surface for pre compositing:" + op2);
                    try
                    {
                      ((MiniClientSageRenderer) this).performHiResComposite(op2.texture.getNativeImage(
                          ((MiniClientSageRenderer) this), op2.textureIndex),
                          oldSurface, op2);
                    }
                    finally
                    {
                      op2.texture.removeNativeRef(((MiniClientSageRenderer) this), op2.textureIndex);
                    }
                  }
                }
                else if (op.surface.equals(op2.surface))
                  compositeRemaining = !op.anime.isBehind();
              }
            }
            if (alreadyDupedDualSurfs.add(op.surface))
            {
              Object lastDupSurf = surfaceCache.remove(lastDupSurfName);
              surfaceCache.put(dupSurfName, oldSurface);
              lastDupSurfName = dupSurfName;
              op.anime.altSurfName = lastDupSurfName;
              if (lastDupSurf != null)
              {
                // Put this one back for the other so it doesn't need to be re-allocated
                // NOTE: We may need to clear this surface before we do this!!
                if (ANIM_DEBUG) System.out.println("Backing copy already existed; putting it back into the main cache");
                surfaceCache.put(op.surface, lastDupSurf);
              }
            }
            else
            {
              surfaceCache.put(op.surface, oldSurface);
              dupSurfName = lastDupSurfName;
              op.anime.altSurfName = lastDupSurfName;
            }
          }
          if (op.anime.isOut())
          {
            if (ANIM_DEBUG) System.out.println("Found animation with duplicate surface; switching it to use dup of: " + dupSurfName);
            op.surface = dupSurfName;
          }
        }
        else if (op.anime.isIn() && nestedSurfs.contains(op.surface) && uiMgr.get("ui/animation/background_surface_name", "BG").equals(op.surface))
        {
          if (ANIM_DEBUG) System.out.println("Detected dup surface usage on an IN animation; change target of non-animated ops to use backup surface name=" + op.surface);
          // This is duplicate surface usage where two layers are rendering to the same surface, in the same region, and one of them is animated
          // so we need to render the non-animated layer to the backup surface. We do this by changing the surface target for thos ops.
          // Fix the ops after this one and then the ones before it all
          // NOTE: The main purpose of this is to deal with the problem when you make the Options menus part
          // of the BG layer and do a ZoomIn animation with them.
          String dupSurfName = getDupSurfName(op);
          surfaceCache.put(dupSurfName, surfaceCache.remove(lastDupSurfName));
          lastDupSurfName = dupSurfName;
          for (int j = i + 1; j < dlsize; j++)
          {
            RenderingOp op2 = (RenderingOp) testdl.get(j);
            if (op.surface.equals(op2.surface))
            {
              if (ANIM_DEBUG) System.out.println("Changing surface target to " + dupSurfName + " for " + op2);
              op2.surface = dupSurfName;
            }
          }
          boolean foundOnOp = false;
          int extraSurfsDepth = 0;
          for (int j = i - 2; j >= 0; j--)
          {
            RenderingOp op2 = (RenderingOp) testdl.get(j);
            if (foundOnOp)
            {
              if (op.surface.equals(op2.surface))
              {
                if (ANIM_DEBUG) System.out.println("Changing surface target to " + dupSurfName + " for " + op2);
                op2.surface = dupSurfName;
              }
            }
            else if (op2.isSurfaceOpOff() && op.surface.equals(op2.surface))
              extraSurfsDepth++;
            else if (op2.isSurfaceOpOn() && op.surface.equals(op2.surface))
            {
              if (extraSurfsDepth == 0)
              {
                if (ANIM_DEBUG) System.out.println("Found the surface on op which starts the dup surf anim we're fixing: " + op2);
                foundOnOp = true;
              }
              else
                extraSurfsDepth--;
            }
          }
        }
      }
    }
  }

  // Implement this here so it's easier for the different implementations to handle animations
  protected void processAnimOp(RenderingOp op, int dlIndex, java.awt.Rectangle clipRect)
  {
    RenderingOp.Animation anime = op.anime;
    if (ANIM_DEBUG) System.out.println("Animation operation found! ANIMAIL ANIMAIL!!! " + op + " scrollSrcRect=" + anime.altSrcRect +
        " scrollDstRect=" + anime.altDestRect);
    // Find the cached surface first
    Object cachedSurface = surfaceCache.get(op.surface);
    if (cachedSurface != null)
    {
      if (ANIM_DEBUG) System.out.println("Cached animation surface found: " + op.surface);
      if (ANIM_DEBUG) System.out.println("Rendering Animation " + anime.animation);
      if ((anime.isIn() || anime.animType == RenderingOp.Animation.SMOOTH ||
          anime.isDualSurfaceOp()) && !anime.cleanedDL)
      {
        if (ANIM_DEBUG) System.out.println("Searching the post composite list for any operations which should be composited onto this animated surface now");
        // Clearing out the DL is pointless since if we re-render we'll use the composite list.
        // What we need to be concerned about here is any compositing operations that need to be performed
        // onto the surface that is being animated BEFORE we start the animation. So anything in the composite
        // list that went between our surface start and now should be composited right now onto the animation
        // surface and that ROP should also be removed from the composite list.
        int surfStartOpIndex = -1;
        int extraSurfsDepth = 0;
        for (int j = dlIndex - 2; j >= 0; j--)
        {
          RenderingOp op2 = (RenderingOp) currDisplayList.get(j);
          if (op2.isSurfaceOpOff() && op.surface.equals(op2.surface))
            extraSurfsDepth++;
          else if (op2.isSurfaceOpOn() && op.surface.equals(op2.surface))
          {
            if (extraSurfsDepth == 0)
            {
              if (ANIM_DEBUG) System.out.println("Found the surface on op which starts this anim we're compositing: " + op2);
              surfStartOpIndex = j;
              break;
            }
            else
              extraSurfsDepth--;
          }
        }
        if (surfStartOpIndex >= 0)
        {
          for (int j = surfStartOpIndex; j < dlIndex - 1; j++)
          {
            RenderingOp op2 = (RenderingOp) currDisplayList.get(j);
            if (op2.isSurfaceOpOff() && compositeOps.contains(op2))
            {
              // NOTE: Again, this is questionable. Currently we composite the surfaces that are after
              // us in the layer order. This seems logical and fits with the weird special case we did before.
              if (op2.surface.compareTo(op.surface) >= 0/*!"Focus".equals(op2.surface) || anime.animType == RenderingOp.Animation.ZOOM_IN*/)
              {
                compositeOps.remove(op2);
                Object altSurfImage = surfaceCache.get(op2.surface);
                if (altSurfImage != null)
                {
                  if (ANIM_DEBUG) System.out.println("Found an op in the composite list which we need to composite now onto the animation surface:" + op2);
                  compositeSurfaces(cachedSurface, altSurfImage, op2.alphaFactor, op2.destRect.createIntersection(clipRect));
                }
              }
            }
          }
        }
        long theTime = Sage.eventTime();
        anime.startNow(theTime - uiMgr.getInt("ui/frame_duration", 14));
        anime.calculateAnimation(theTime, master.getWidth(), master.getHeight(), master.isIntegerPixels());
      }
      else if (!anime.cleanedDL)
      {
        long theTime = Sage.eventTime();
        anime.startNow(theTime - uiMgr.getInt("ui/frame_duration", 14));
        anime.calculateAnimation(theTime, master.getWidth(), master.getHeight(), master.isIntegerPixels());
      }
      compositeOps.add(op);
    }
    else
    {
      if (ANIM_DEBUG) System.out.println("ERROR: Could not find cached animation surface:" + op.surface);
    }

  }

  protected void markAnimationStartTimes()
  {
    // Now go through all the animations that are new and mark this as their start time. This helps avoid
    // missing the start of the animation because the renderer is finishing execution of the larger batch of unoptimized
    // rendering commands.
    // This should be called at the end of the present method
    long time = Sage.eventTime() - uiMgr.getInt("ui/frame_duration", 14);
    int vecsize = compositeOps.size();
    for (int i = 0; i < vecsize; i++)
    {
      RenderingOp op = (RenderingOp) compositeOps.get(i);
      if (op.isAnimationOp() && !op.anime.cleanedDL)
      {
        op.anime.cleanedDL = true;
        op.anime.startNow(time);
      }
    }
  }

  protected void fixSurfacePostCompositeRegions()
  {
    // NOTE: We keep the original ArrayList of composite operations around for use in compositing any of these surfaces
    // for another animation right after this one. Since some of the animations will already have the compositing done;
    // we're really just culling that stuff out here (which is why that data may actually be needed again later).
    orgCompositeOps.clear();
    orgCompositeOps.addAll(compositeOps);
    if (ANIM_DEBUG) System.out.println("Fixing the surface compositing operations to handle animation region overlaps compositeOps=" + compositeOps);
    for (int i = 0; i < compositeOps.size(); i++)
    {
      RenderingOp op = (RenderingOp) compositeOps.get(i);
      if (op.isSurfaceOp() && !op.isImageOp())
      {
        if (ANIM_DEBUG) System.out.println("Surface Op surf=" + op.surface + " on=" + op.isSurfaceOpOn() + " " + op);
        if (op.isSurfaceOpOff())
        {
          //					currSurface = (java.awt.image.BufferedImage) surfaceCache.get(op.surface);
          // If we're about to do an IN or SMOOTH animation then we do NOT do this surface copy; we leave that to the
          // animator ROP. We also don't do it for SCROLL ops; EXCEPT for any area of the surface that doesn't intersect
          // with the region that's scrolled. Those areas we need to copy back.
          java.util.ArrayList avoidAreas = null;
          int searchOffset = 1;
          while (i + searchOffset < compositeOps.size())
          {
            RenderingOp flop = (RenderingOp) compositeOps.get(i + searchOffset);
            if (flop.isAnimationOp() && !flop.isImageOp())
            {
              if (flop.surface.equals(op.surface) && (flop.anime.isIn() || flop.anime.animType == RenderingOp.Animation.SMOOTH ||
                  flop.anime.isDualSurfaceOp()))
              {
                if (avoidAreas == null)
                  avoidAreas = new java.util.ArrayList();
                // NOTE: I changed this to be active for all animations since we had a case when
                // we did a morph animation between 2 different size dialogs that commenting this out
                // corrected. It makes sense because this preserves surface usage for non-animated areas;
                // so it should be applied in the general case.
                // NOTE: But then we had a case where for Smooth animatiosn it screwed things up so we don't do it there
                if (flop.anime.isIn() || flop.anime.animType == RenderingOp.Animation.SCROLL || flop.anime.isDualSurfaceOp())
                  avoidAreas.add(flop.anime.orgDestRect);
              }
            }
            else
              break;
            searchOffset++;
          }
          if (avoidAreas == null)
          {
            if (ANIM_DEBUG) System.out.println("Normal surface for compositing; leave it alone");
          }
          else if (!avoidAreas.isEmpty())
          {
            compositeOps.remove(i--);
            if (ANIM_DEBUG) System.out.println("Checking cached surface rendering; surface overlapped animation is coming, check for non-overlapped areas");
            // Now we need to determine which areas of the old surface should be copied back.
            // 1. We do this by first dividing the data up until horizontal bands that we'll process separately.
            //     Each band can be found by finding the union of all the different y values used in the rectangular regions.
            //     Then each band will have no variance in y.
            // 2. To process a horizontal band; you find all the rectangular regions that intersect that band; and then find
            //    the union of all the x values used in those rectangles. This will give you vertical bands within a horizontal band.
            // 3. Then you can process each of these remaining bands. Some will need to be copied completely and some will not.
            java.awt.geom.Rectangle2D.Float[] avoidRects =
                (java.awt.geom.Rectangle2D.Float[]) avoidAreas.toArray(new java.awt.geom.Rectangle2D.Float[0]);
            float[] ybands = new float[avoidAreas.size()*2 + 2];
            float[] xbands = new float[avoidAreas.size()*2 + 2];
            // Step 1
            ybands[0] = op.destRect.y;
            ybands[1] = op.destRect.y + op.destRect.height;
            for (int r = 0; r < avoidRects.length; r++)
            {
              ybands[r*2 + 2] = avoidRects[r].y;
              ybands[r*2 + 3] = avoidRects[r].y + avoidRects[r].height;
            }
            java.util.Arrays.sort(ybands);
            for (int yb = 0; yb < ybands.length - 1; yb++)
            {
              // Part of step 1 still, we're doing the union of the y values here essentially
              float startY = ybands[yb];
              while (yb < ybands.length - 1 && Math.round(ybands[yb + 1]) == Math.round(startY))
                yb++;
              if (yb == ybands.length - 1) break; // no more bands
              float endY = ybands[yb + 1];
              // Step 2
              int numXBands = 2;
              xbands[0] = op.destRect.x;
              xbands[1] = op.destRect.x + op.destRect.width;
              for (int t = 0; t < avoidRects.length; t++)
              {
                if (avoidRects[t].y < endY && avoidRects[t].y + avoidRects[t].height >= startY)
                {
                  // This rectangle intersects this y band so we add its x values to the xbands
                  xbands[numXBands++] = avoidRects[t].x;
                  xbands[numXBands++] = avoidRects[t].x + avoidRects[t].width;
                }
              }
              java.util.Arrays.sort(xbands, 0, numXBands);
              for (int xb = 0; xb < numXBands; xb++)
              {
                float startX = xbands[xb];
                while (xb < numXBands - 1 && Math.round(xbands[xb + 1]) == Math.round(startX))
                  xb++;
                if (xb == numXBands - 1) break; // no more bands
                float endX = xbands[xb + 1];
                // See if any rectangles intersect this band. If they do; do NOT copy it...otherwise copy it.
                boolean copyMe = true;
                for (int t = 0; t < avoidRects.length; t++)
                {
                  if (floatRectsIntersect(avoidRects[t], startX, startY, endX - startX, endY - startY))
                  {
                    copyMe = false;
                    break;
                  }
                }
                if (copyMe)
                {
                  // No overlap so copy this region back
                  if (ANIM_DEBUG) System.out.println("Creating surface op to copy back subregion that's not overlapped [" + startX +
                      ", " + startY + ", " + endX + ", " + endY + "]");
                  RenderingOp surfCopyOp = new RenderingOp(op.surface, new java.awt.geom.Rectangle2D.Float(startX, startY,
                      endX - startX, endY - startY), false);
                  surfCopyOp.opFlags = RenderingOp.RENDER_FLAG_NONOVERLAPPED_COPY;
                  compositeOps.add(++i, surfCopyOp);
                }
              }
            }
          }
          else
          {
            if (ANIM_DEBUG) System.out.println("Removing cached surface rendering; animation is coming.");
            compositeOps.remove(i--);
          }
        }
      }
    }
  }

  // NOTE: Make this abstract after we have all the implementations done
  protected abstract boolean compositeSurfaces(Object targetSurface, Object srcSurface, float alphaFactor, java.awt.geom.Rectangle2D region);

  public final boolean hasConsumedNextDisplayList()
  {
    synchronized (dlQueueLock)
    {
      return (nextDisplayList == null);
    }
  }

  public void setWaitIndicatorState(boolean x)
  {
    waitIndicatorState = x;
  }
  public final boolean getWaitIndicatorState()
  {
    return waitIndicatorState;
  }

  public final void setWaitIndicatorRenderingOps(java.util.ArrayList waitOps)
  {
    waitIndicatorRops = waitOps;
  }

  public final boolean supportsWaitCursor()
  {
    return waitCursorSupport;
  }

  public final boolean doesNextDisplayListHaveVideo()
  {
    // If there is no next then we check the current
    java.util.ArrayList checkDL = null;
    synchronized (dlQueueLock)
    {
      if (nextDisplayList == null)
        checkDL = currDisplayList;
      else
        checkDL = nextDisplayList;
    }
    if (checkDL == null) return false;
    int dlsize = checkDL.size();
    for (int i = 0; i < dlsize; i++)
      if (((RenderingOp) checkDL.get(i)).isVideoOp()) return true;
    return false;
  }

  public boolean doesNextDisplayListHaveScrollingAnimations()
  {
    // If there is no next then we check the current
    java.util.ArrayList checkDL = null;
    synchronized (dlQueueLock)
    {
      if (nextDisplayList == null)
        checkDL = currDisplayList;
      else
        checkDL = nextDisplayList;
    }
    if (checkDL == null) return false;
    int dlsize = checkDL.size();
    for (int i = 0; i < dlsize; i++)
    {
      RenderingOp rop = (RenderingOp) checkDL.get(i);
      if (rop.isAnimationOp() && rop.anime.animType == RenderingOp.Animation.SCROLL)
        return true;
    }
    return false;
  }

  public abstract boolean executeDisplayList(java.awt.Rectangle clipRect);

  public abstract void present(java.awt.Rectangle clipRect);

  public abstract boolean supportsPartialUIUpdates();

  public void cleanupRenderer()
  {
    nextDisplayList = currDisplayList = lastDisplayList = null;
    Sage.gc(true);
    if (Sage.DBG) System.out.println("Terminating the BGResourceLoader for the rendering engine");
    bgLoader.kill();
  }

  public boolean needsRefresh()
  {
    return false;
  }

  // Override to return false if the renderer can't support animation surfaces
  public boolean hasSurfaceSupport()
  {
    return true;
  }

  // Return true if we need to switch to the next display list
  public boolean checkForFocusAnimConsolidate()
  {
    // Check if we're doing a focus animation and if the next display list contains a focus animation we switch to the new display
    // list after we modify the focus animation so that its starting position is the running focus animation's current position.
    // UPDATE: But we don't consolidate the focus animation if there's another active animation at the same time!
    synchronized (dlQueueLock)
    {
      if (compositeOps.isEmpty() || nextDisplayList == null)
        return false;
      int compsize = compositeOps.size();
      for (int i = 0; i < compsize; i++)
      {
        RenderingOp rop = (RenderingOp) compositeOps.get(i);
        if (rop.isAnimationOp() && !rop.surface.endsWith("Focus") && !rop.anime.interruptable)
          return false;
      }
      for (int i = 0; i < compsize; i++)
      {
        RenderingOp rop = (RenderingOp) compositeOps.get(i);
        if (rop.isAnimationOp() && rop.surface.endsWith("Focus"))
        {
          // We found a current focus animation op. So let's see if there's one in our next DL
          int nextdlsize = nextDisplayList.size();
          for (int j = 0; j < nextdlsize; j++)
          {
            RenderingOp rop2 = (RenderingOp) nextDisplayList.get(j);
            if (rop2.isAnimationOp() && rop2.surface.endsWith("Focus"))
            {
              // OK we found it!
              rop2.anime.orgSrcRect.setRect(rop.destRect);
              rop2.anime.forcedEaseIn = true;
              return true;
            }
          }
          return false;
        }
      }
    }
    return false;
  }

  protected RenderingOp convertGlyphsToCachedImages(RenderingOp textRenderOp)
  {
    return convertGlyphsToCachedImages(textRenderOp, true);
  }
  protected RenderingOp convertGlyphsToCachedImages(RenderingOp textRenderOp, boolean obeyClipping)
  {
    // Go through each individual glyph in the glyph vector and get the cached image
    // for it. Also specify the position information in the destRect for the new
    // image op created for it
    if (textRenderOp.text.fontImage == null)
      textRenderOp.text.fontImage = MetaImage.getMetaImage(textRenderOp.text.font);
    else
      return textRenderOp; // its already been cached
    int numGlyphs = textRenderOp.text.glyphVector.getNumGlyphs();
    CachedFontGlyphs fontCache = getAcceleratedFont(textRenderOp.text.font);
    if (textRenderOp.text.renderRectCache == null)
    {
      textRenderOp.text.renderRectCache = new float[numGlyphs*2];
      textRenderOp.text.renderImageNumCache = new int[numGlyphs];
      textRenderOp.text.renderGlyphRectCache = new java.awt.geom.Rectangle2D.Float[numGlyphs];
    }
    for (int i = 0; i < numGlyphs; i++)
    {
      int currGlyph = textRenderOp.text.glyphVector.getGlyphCode(i);
      float glyphPos = textRenderOp.text.glyphVector.getGlyphPosition(i);
      java.awt.geom.Rectangle2D.Float glyphRect = fontCache.getPixelRect(currGlyph);
      if (fontCache.getImageIndexForGlyph(currGlyph) < 0 || glyphRect == null)
      {
        textRenderOp.text.renderImageNumCache[i] = -1;
        continue;
      }
      if (!obeyClipping || textRenderOp.srcRect.intersects(glyphPos + fontCache.getRenderXOffset(currGlyph),
          fontCache.getRenderYOffset(currGlyph), glyphRect.width,
          glyphRect.height))
      {
        textRenderOp.text.renderImageNumCache[i] = fontCache.getImageIndexForGlyph(currGlyph);
        textRenderOp.text.renderRectCache[2*i] = glyphPos + fontCache.getRenderXOffset(currGlyph);
        textRenderOp.text.renderRectCache[2*i+1] = fontCache.getRenderYOffset(currGlyph);
        if (master.isIntegerPixels())
        {
          textRenderOp.text.renderRectCache[2*i] = (int)textRenderOp.text.renderRectCache[2*i];
          textRenderOp.text.renderRectCache[2*i + 1] = (int)textRenderOp.text.renderRectCache[2*i + 1];
        }
        textRenderOp.text.renderGlyphRectCache[i] = glyphRect;
        if (!obeyClipping)
          continue;
        // Clip the glyph rect relative the dest rect
        boolean clonedRect = false;
        float offset = textRenderOp.srcRect.x - textRenderOp.text.renderRectCache[2*i];
        if (offset > 0)
        {
          // Be sure we maintain integral offsets for text rendering
          offset = (float)Math.ceil(offset);
          if (!clonedRect)
          {
            textRenderOp.text.renderGlyphRectCache[i] = glyphRect = (java.awt.geom.Rectangle2D.Float) glyphRect.clone();
            clonedRect = true;
          }
          glyphRect.width -= offset;
          glyphRect.x += offset;
          textRenderOp.text.renderRectCache[2*i] += offset;
        }
        offset = textRenderOp.srcRect.y - textRenderOp.text.renderRectCache[2*i + 1];
        if (offset > 0)
        {
          // Be sure we maintain integral offsets for text rendering
          offset = (float)Math.ceil(offset);
          if (!clonedRect)
          {
            textRenderOp.text.renderGlyphRectCache[i] = glyphRect = (java.awt.geom.Rectangle2D.Float) glyphRect.clone();
            clonedRect = true;
          }
          glyphRect.height -= offset;
          glyphRect.y += offset;
          textRenderOp.text.renderRectCache[2*i + 1] += offset;
        }
        if (textRenderOp.text.renderRectCache[2*i] + glyphRect.width > textRenderOp.srcRect.x +
            textRenderOp.srcRect.width)
        {
          if (!clonedRect)
          {
            textRenderOp.text.renderGlyphRectCache[i] = glyphRect = (java.awt.geom.Rectangle2D.Float) glyphRect.clone();
            clonedRect = true;
          }
          glyphRect.width -= (textRenderOp.text.renderRectCache[2*i] + glyphRect.width) -
              (textRenderOp.srcRect.x + textRenderOp.srcRect.width);
        }
        if (textRenderOp.text.renderRectCache[2*i+1] + glyphRect.height > textRenderOp.srcRect.y +
            textRenderOp.srcRect.height)
        {
          if (!clonedRect)
          {
            textRenderOp.text.renderGlyphRectCache[i] = glyphRect = (java.awt.geom.Rectangle2D.Float) glyphRect.clone();
            clonedRect = true;
          }
          glyphRect.height -= (textRenderOp.text.renderRectCache[2*i+1] + glyphRect.height) -
              (textRenderOp.srcRect.y + textRenderOp.srcRect.height);
        }
      }
      else
        textRenderOp.text.renderImageNumCache[i] = -1;
    }
    return textRenderOp;
  }

  public long getLastPresentFrameTime()
  {
    return lastPresentTime;
  }

  public BGResourceLoader getBGLoader()
  {
    return bgLoader;
  }

  protected void pauseIfNotRenderingThread()
  {
    pauseIfNotRenderingThread(false);
  }
  protected void pauseIfNotRenderingThread(boolean doNotInterfere)
  {
    do
    {
      if (renderingThread != null && Thread.currentThread() != renderingThread &&
          !MetaImage.isThreadLockedOnNCL(renderingThread))
      {
        synchronized (pauseLock)
        {
          try
          {
            if (renderingThread != null)
              pauseLock.wait(imageLoadPauseTime);
          }
          catch (Exception e){}
        }
      }
      else
        break;
    }while (doNotInterfere);
  }

  protected void waitUntilNextFrameComplete(long waitTime)
  {
    synchronized (pauseLock)
    {
      try
      {
        pauseLock.wait(waitTime);
      }
      catch (Exception e){}
    }
  }

  protected void establishRenderThread()
  {
    renderingThread = Thread.currentThread();
  }

  protected void releaseRenderThread()
  {
    synchronized (pauseLock)
    {
      renderingThread = null;
      pauseLock.notifyAll();
    }
  }

  protected javax.vecmath.Matrix4f generateProjectionMatrix(float cameraOffsetX, float cameraOffsetY)
  {
    int viewportWidth = master.getWidth();
    int viewportHeight = master.getHeight();
    int cameraX = Math.round(cameraOffsetX);//viewportWidth/2;
    int cameraY = Math.round(cameraOffsetY);//viewportHeight/2;
    javax.vecmath.Matrix4f viewMat = MathUtils.createScaleMatrix(1.0f, -1.0f);
    viewMat.m03 = -(viewportWidth*0.5f + cameraX);
    viewMat.m13 = viewportHeight*0.5f + cameraY;
    viewMat.m23 = viewportWidth;
    float xmin = (viewportWidth*0.5f + cameraX)/-2;
    float xmax = (viewportWidth*0.5f - cameraX)/2;
    float ymin = (viewportHeight*0.5f - cameraY)/-2;
    float ymax = (viewportHeight*0.5f + cameraY)/2;
    float zmin = viewportWidth*0.5f;
    float zmax = viewportWidth*50;
    javax.vecmath.Matrix4f projMat =new javax.vecmath.Matrix4f(
        2*zmin/(xmax - xmin), 0, 0, 0,
        0, 2*zmin/(ymax-ymin), 0, 0,
        (xmin+xmax)/(xmin-xmax), (ymin+ymax)/(ymin-ymax), zmax/(zmax-zmin), 1,
        0, 0, zmax*zmin/(zmin-zmax), 0);
    projMat.transpose();
    // This is the view-projection matrix currently
    projMat.mul(projMat, viewMat);
    return projMat;
  }

  public boolean canSupportAnimations()
  {
    return true;
  }

  protected ZRoot master;
  protected Object dlQueueLock = new Object();
  protected java.util.ArrayList nextDisplayList;
  protected java.util.ArrayList currDisplayList;
  protected java.util.ArrayList lastDisplayList;
  protected UIManager uiMgr;
  protected boolean waitIndicatorState;
  protected java.util.ArrayList waitIndicatorRops;
  protected int waitIndicatorRopsIndex;
  protected boolean waitCursorSupport;
  protected long lastPresentTime;
  protected String lastDupSurfName;
  protected java.util.Map surfaceCache = new java.util.HashMap();
  protected java.util.ArrayList compositeOps = new java.util.ArrayList();
  protected java.util.ArrayList orgCompositeOps = new java.util.ArrayList();
  protected BGResourceLoader bgLoader;
  protected Thread renderingThread;
  protected Object pauseLock = new Object();
  protected long imageLoadPauseTime = 200;
  protected boolean disableAllTransparency;

  public static class ShapeDescription
  {
    public ShapeDescription()
    {
    }

    public ShapeDescription(float width, float height, java.awt.Color c)
    {
      shapeType = "Rectangle";
      shapeWidth = width;
      shapeHeight = height;
      color = c;
      fill = true;
    }

    public int hashCode()
    {
      return (shapeType != null ? shapeType.hashCode() : 0) + ((int)shapeWidth)*371 + ((int)shapeHeight)*18373 + cornerArc*7 + strokeSize*13 +
          (color != null ? color.hashCode() : 44) + (gradc1 != null ? gradc1.hashCode() : 271) +
          (gradc2 != null ? gradc2.hashCode() : 199) + Math.round(((fx1 + 6)*(fx2 + 9)*(fy1+7)*(fy2+13))*10000);
    }
    public boolean equals(Object o)
    {
      if (o instanceof ShapeDescription)
      {
        ShapeDescription sd = (ShapeDescription) o;
        return shapeType.equals(sd.shapeType) && shapeWidth == sd.shapeWidth && shapeHeight == sd.shapeHeight &&
            cornerArc == sd.cornerArc && fill == sd.fill && strokeSize == sd.strokeSize &&
            ((gradc1 == sd.gradc1) || (gradc1 != null && gradc1.equals(sd.gradc1))) &&
            ((gradc2 == sd.gradc2) || (gradc2 != null && gradc2.equals(sd.gradc2))) &&
            fx1 == sd.fx1 && fx2 == sd.fx2 && fy1 == sd.fy1 && fy2 == sd.fy2 &&
            ((color == sd.color) || (color != null && color.equals(sd.color)));
      }
      return false;
    }
    public boolean hasArea() { return shapeWidth != 0 && shapeHeight != 0; }
    public String shapeType;
    public float/*X*/ shapeWidth;
    public float/*X*/ shapeHeight;
    public int cornerArc;
    public boolean fill;
    public int strokeSize;
    public java.awt.Color gradc1;
    public java.awt.Color gradc2;
    public float/*X*/ fx1;
    public float/*X*/ fy1;
    public float/*X*/ fx2;
    public float/*X*/ fy2;
    public java.awt.Color color;
  }

  // This is Font->MetaImage
  protected static java.util.Map globalFontRenderCache = new java.util.HashMap();
  void clearFontRenderCache()
  {
    if (Sage.DBG) System.out.println("CLEARING the Font Cache");
    globalFontRenderCache.clear();
  }

  static class CachedFontGlyphs
  {
    MetaFont font;
    int[] imageIndexByGlyphCode;
    java.awt.geom.Rectangle2D.Float[] pixelRectByGlyphCode;
    java.awt.geom.Rectangle2D.Float[] logicalRectByGlyphCode;
    int width;
    int height;
    int[] glyphCounts;
    int numGlyphs;
    public int getImageIndexForGlyph(int gc)
    {
      if (gc >= 0 && gc < numGlyphs)
        return imageIndexByGlyphCode[gc];
      else
        return -1;
    }
    public java.awt.geom.Rectangle2D.Float getPixelRect(int gc)
    {
      if (gc >= 0 && gc < numGlyphs)
        return pixelRectByGlyphCode[gc];
      else
        return null;
    }
    public java.awt.geom.Rectangle2D.Float getLogicalRect(int gc)
    {
      if (gc >= 0 && gc < numGlyphs)
        return logicalRectByGlyphCode[gc];
      else
        return null;
    }

    public float getRenderYOffset(int gc)
    {
      if (gc >= 0 && gc < numGlyphs)
        return (pixelRectByGlyphCode[gc].y - logicalRectByGlyphCode[gc].y) + font.getAscent();
      else
        return 0;
    }

    public float getRenderXOffset(int gc)
    {
      if (gc >= 0 && gc < numGlyphs)
        return (pixelRectByGlyphCode[gc].x - logicalRectByGlyphCode[gc].x);
      else
        return 0;
    }
  }

  // We do a-z, A-Z, 0-9 and these characters AND the missing glyph code
  public static char[] EXTRA_RENDERABLE_CHARS = { '!','@','#','$','%','^','&','*','(',')','_','-','=','+',
    '{','}','|','\\',':',';','"','\'','<','>',',','.','/','?','~','`','\'',
    0xc1, 0xe1, 0xc9, 0xe9, 0xcd, 0xed, 0xd3, 0xf3, 0xda, 0xfa, 0xfd, // acute accent
    0xc7, 0xe7, // accent cedille
    0xc2, 0xe2, 0xca, 0xea, 0xce, 0xee, 0xd4, 0xf4, 0xdb, 0xfb, // accent circumflex
    0xc4, 0xe4, 0xcb, 0xeb, 0xcf, 0xef, 0xd6, 0xf6, 0xdc, 0xfc, 0xff, // accent diaresis
    0xc0, 0xe0, 0xc8, 0xe8, 0xcc, 0xec, 0xd2, 0xf2, 0xd9, 0xf9,  // grave accent
    0xbf, 0xa1, // punctuation
    0xc3, 0xe3, 0xc1, 0xf1, 0xd5, 0xf5, // accent tilde
  };

  static CachedFontGlyphs getAcceleratedFont(MetaFont font)
  {
    CachedFontGlyphs rv = null;
    rv = (CachedFontGlyphs) globalFontRenderCache.get(font);
    if (rv != null)
      return rv;
    synchronized (globalFontRenderCache)
    {
      rv = (CachedFontGlyphs) globalFontRenderCache.get(font);
      if (rv != null)
        return rv;
      if (Sage.DBG) System.out.println("Loading new font to cache font=" + font);
      //		int numGlyphs = font.getNumGlyphs();
      //		java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null,
      //			UIManager.shouldAntialiasFont(font.getSize()), false);
      int maxRequiredGlyphCode = 0;
      if (Sage.getBoolean("ui/load_complete_glyph_maps", false))
      {
        maxRequiredGlyphCode = Integer.MAX_VALUE;
      }
      else
      {
        /*			for (char x = 'a'; x <= 'z'; x++)
					maxRequiredGlyphCode = Math.max(maxRequiredGlyphCode, font.createGlyphVector("" + x).getGlyphCode(0));
				for (char x = 'A'; x <= 'Z'; x++)
					maxRequiredGlyphCode = Math.max(maxRequiredGlyphCode, font.createGlyphVector("" + x).getGlyphCode(0));
				for (char x = '0'; x <= '9'; x++)
					maxRequiredGlyphCode = Math.max(maxRequiredGlyphCode, font.createGlyphVector("" + x).getGlyphCode(0));*/
        // This should be plenty to get the max glyph code
        for (int x = 0; x < EXTRA_RENDERABLE_CHARS.length; x++)
        {
          maxRequiredGlyphCode = Math.max(maxRequiredGlyphCode, font.createGlyphVector("" +
              EXTRA_RENDERABLE_CHARS[x]).getGlyphCode(0));
        }
        String extraLocalChars = Sage.rez("Extra_Locale_Characters");
        MetaFont.GlyphVector extraGV;
        if (extraLocalChars.length() > 0)
        {
          extraGV = font.createGlyphVector(extraLocalChars);
          for (int x = 0; x < extraGV.getNumGlyphs(); x++)
            maxRequiredGlyphCode = Math.max(maxRequiredGlyphCode, extraGV.getGlyphCode(x));
        }
        extraLocalChars = Sage.get("ui/extra_characters_for_glyph_maps", "");
        if (extraLocalChars.length() > 0)
        {
          extraGV = font.createGlyphVector(extraLocalChars);
          for (int x = 0; x < extraGV.getNumGlyphs(); x++)
            maxRequiredGlyphCode = Math.max(maxRequiredGlyphCode, extraGV.getGlyphCode(x));
        }
      }

      // Each image should be stored in a FONT_TEXTURE_MAP_SIZExFONT_TEXTURE_MAP_SIZE block
      globalFontRenderCache.put(font, rv = font.loadAcceleratedFont(maxRequiredGlyphCode, FONT_TEXTURE_MAP_SIZE,	FONT_TEXTURE_MAP_SIZE));
    }
    return rv;
  }
  public static class TextDescription
  {
    public TextDescription(MetaFont f, MetaFont.GlyphVector gv, String s)
    {
      font = f;
      glyphVector = gv;
      string = s;
    }

    public MetaFont font;
    public MetaFont.GlyphVector glyphVector;
    public String string;
    // For render data caching
    public float[] renderRectCache;
    public java.awt.geom.Rectangle2D.Float[] renderGlyphRectCache;
    public int[] renderImageNumCache;
    public MetaImage fontImage;
  }

  private static final double EPSILON = 0.0001;
  public static boolean floatRectEquals(java.awt.geom.Rectangle2D r1, java.awt.geom.Rectangle2D r2)
  {
    return (Math.abs(r1.getX() - r2.getX()) < EPSILON) &&
        (Math.abs(r1.getY() - r2.getY()) < EPSILON) &&
        (Math.abs(r1.getWidth() - r2.getWidth()) < EPSILON) &&
        (Math.abs(r1.getHeight() - r2.getHeight()) < EPSILON);
  }
  public static boolean floatRectEquals(java.awt.geom.Rectangle2D r1, java.awt.geom.Rectangle2D r2, float r1offsetX, float r1offsetY)
  {
    return (Math.abs(r1.getX() + r1offsetX - r2.getX()) < EPSILON) &&
        (Math.abs(r1.getY() + r1offsetY - r2.getY()) < EPSILON) &&
        (Math.abs(r1.getWidth() - r2.getWidth()) < EPSILON) &&
        (Math.abs(r1.getHeight() - r2.getHeight()) < EPSILON);
  }
  public static boolean floatRectsIntersect(java.awt.geom.Rectangle2D r1, float x, float y, float w, float h)
  {
    if (r1.isEmpty() || w <= 0 || h <= 0) {
      return false;
    }
    double x0 = r1.getX();
    double y0 = r1.getY();
    return ((x + w) - x0 > EPSILON) &&
        ((y + h) - y0 > EPSILON) &&
        ((x0 + r1.getWidth()) - x > EPSILON) &&
        ((y0 + r1.getHeight()) - y > EPSILON);
  }

  protected static final java.util.Comparator COMPOSITE_LIST_SORTER = new java.util.Comparator()
  {
    public int compare(Object o1, Object o2)
    {
      RenderingOp r1 = (RenderingOp) o1;
      RenderingOp r2 = (RenderingOp) o2;
      String s1 = r1.surface;
      String s2 = r2.surface;
      if (s1 == s2)
        return 0;
      else if (s1 == null)
        return 1;
      else if (s2 == null)
        return -1;
      else
        return s1.compareTo(s2);
    }
  };

  protected static class EffectStackItem
  {
    public EffectStackItem(javax.vecmath.Matrix4f xform, javax.vecmath.Matrix4f projxform, float alpha, java.awt.geom.Rectangle2D clip)
    {
      this.xform = xform;
      this.projxform = projxform;
      this.alpha = alpha;
      this.clip = clip;
    }
    public javax.vecmath.Matrix4f xform;
    public javax.vecmath.Matrix4f projxform;
    public float alpha;
    public java.awt.geom.Rectangle2D clip;
  }
}
