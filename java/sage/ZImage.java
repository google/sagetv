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

public class ZImage extends ZComp
{
  public ZImage(ZRoot inReality, MetaImage inImage)
  {
    this(inReality, inImage, null);
  }
  public ZImage(ZRoot inReality, MetaImage inImage, MetaImage inPressedImage)
  {
    this(inReality, inImage, inPressedImage, null);
  }
  public ZImage(ZRoot inReality, MetaImage inImage, MetaImage inPressedImage, String inTooltip)
  {
    this(inReality, inImage, inPressedImage, inTooltip, null);
  }
  public ZImage(ZRoot inReality, MetaImage inImage, MetaImage inPressedImage, String inTooltip,
      MetaImage inRolloverImage)
  {
    super(inReality);
    myImage = inImage;
    pressedImage = inPressedImage;
    rolloverImage = inRolloverImage;
    rollState = false;
    pressed = false;
    enabled = true;
    tooltip = inTooltip;

    if (myImage == null)
      prefSize = new java.awt.geom.Rectangle2D.Float(0, 0, 0, 0);
    else
      prefSize = new java.awt.geom.Rectangle2D.Float(0, 0, myImage.getWidth(), myImage.getHeight());

    if ((pressedImage != null) &&
        ((prefSize.width != pressedImage.getWidth()) ||
            (prefSize.height != pressedImage.getHeight())))
    {
      //throw new IllegalArgumentException("Both images must be the same size.");
    }

    actionListeners = new java.util.Vector();
  }

  public void addActionListener(SageTVActionListener l)
  {
    if (!actionListeners.contains(l))
    {
      actionListeners.addElement(l);
    }
  }

  public void removeActionListener(SageTVActionListener l)
  {
    actionListeners.remove(l);
  }

  // The purpose of this is because doing a .equals on a java.net.URL can try to do a hostname resolution which can be SLOW sometimes,
  // so for URLs we just convert to their string representation and then test that instead.
  private boolean safeObjTest(Object o1, Object o2)
  {
    if (o1 instanceof java.net.URL)
      o1 = o1.toString();
    if (o2 instanceof java.net.URL)
      o2 = o2.toString();
    return (o1 == o2) || (o1 != null && o1.equals(o2));
  }

  public boolean isWaitingOnObject(Object obj)
  {
    if ((myImage instanceof MetaImage.Waiter) && safeObjTest(obj, (((MetaImage.Waiter) myImage).getWaitObj())))
      return true;
    if ((pendingImage instanceof MetaImage.Waiter) && safeObjTest(obj, (((MetaImage.Waiter) pendingImage).getWaitObj())))
      return true;
    if (altWaitObject != null && safeObjTest(altWaitObject, obj))
      return true;
    return false;
  }

  public void setImage(MetaImage inImage)
  {
    if (myImage == inImage ||
        (myImage instanceof MetaImage.Waiter && inImage instanceof MetaImage.Waiter &&
            safeObjTest(((MetaImage.Waiter) myImage).getWaitObj(), (((MetaImage.Waiter) inImage).getWaitObj()))))
    {
      ignoreNextCrossFade = false;
      return;
    }
    if (crossFadeDuration > 0 && !ignoreNextCrossFade)
    {
      // For cross-fades; we don't temporarily show the Waiter image; we just wait for the real one to be ready and switch then...
      // but we still need to store this one so that the loadStillNeeded call will function properly
      if (myImage != null && !(myImage instanceof MetaImage.Waiter) && (inImage instanceof MetaImage.Waiter))
      {
        pendingImage = inImage;
        return;
      }
      crossFadeImage = myImage;
    }
    else
      ignoreNextCrossFade = false;
    pendingImage = null;
    boolean sizeMismatch = false;
    if (myImage == null || inImage == null || (inImage.getWidth() != myImage.getWidth()) ||
        (inImage.getHeight() != myImage.getHeight()))
    {
      sizeMismatch = true;
      //throw new IllegalArgumentException("Images must match in size.");
    }
    myImage = inImage;
    if (myImage == null || myImage.getWidth() <= 0 || myImage.getHeight() <= 0)
      prefSize = new java.awt.geom.Rectangle2D.Float(0, 0, 0, 0);
    else
      prefSize = new java.awt.geom.Rectangle2D.Float(0, 0, myImage.getWidth(), myImage.getHeight());

    //reality.renderOnce();
    appendToDirty(sizeMismatch);
  }

  public String getTip() { return tooltip; }
  public void setTip(String x) { tooltip = x; }

  protected void processMouseEvent(java.awt.event.MouseEvent evt)
  {
    switch (evt.getID())
    {
      case java.awt.event.MouseEvent.MOUSE_PRESSED:
        if ((evt.getModifiers() & java.awt.event.MouseEvent.BUTTON1_MASK) != 0)
        {
          pressed = true;
          ignoreRelease = false;
          if (pressedImage != null)
          {
            appendToDirty(false);//reality.renderOnce();
            //reality.renderOnceIfDirty();
          }
          if (autoRepeat && !actionListeners.isEmpty())
          {
            if (autoRepeatTimer != null)
            {
              autoRepeatTimer.cancel();
              autoRepeatTimer = null;
            }
            autoRepeatTimer = new java.util.TimerTask()
            {
              public void run()
              {
                if (pressed && enabled)
                {
                  fireAction(ZImage.this);
                }
              }
            };
            UIManager uiMgr = reality.getUIMgr();
            uiMgr.addTimerTask(autoRepeatTimer, uiMgr.getLong("ui/mouse_press_autorepeat_delay", 500),
                uiMgr.getLong("ui/mouse_press_autorepeat_period", 200));
            evt.consume();
          }
        }
        break;
      case java.awt.event.MouseEvent.MOUSE_RELEASED:
        if ((evt.getModifiers() & java.awt.event.MouseEvent.BUTTON1_MASK) != 0)
        {
          if (autoRepeatTimer != null)
          {
            autoRepeatTimer.cancel();
            autoRepeatTimer = null;
          }
          if (enabled && !ignoreRelease)
          {
            fireAction(this);
            if (!actionListeners.isEmpty())
              evt.consume();
          }
          pressed = false;
          ignoreRelease = true;
          if (pressedImage != null)
          {
            appendToDirty(false);//reality.renderOnce();
            //reality.renderOnceIfDirty();
          }
        }
        break;
      case java.awt.event.MouseEvent.MOUSE_ENTERED:
        if (!rollState)
        {
          rollState = true;
          if (rolloverImage != null)
            appendToDirty(false);
        }
        break;
      case java.awt.event.MouseEvent.MOUSE_EXITED:
        if (autoRepeatTimer != null)
        {
          autoRepeatTimer.cancel();
          autoRepeatTimer = null;
        }
        ignoreRelease = true;
        if (rollState)
        {
          rollState = false;
          if (rolloverImage != null)
            appendToDirty(false);
        }
        if (pressed)
        {
          pressed = false;
          if (pressedImage != null)
          {
            appendToDirty(false);//reality.renderOnce();
            //reality.renderOnceIfDirty();
          }
        }
        break;
    }

    // Fire any attached listeners for the parent
    if (!evt.isConsumed())
      super.processMouseEvent(evt);
  }

  protected void fireAction(Object evt)
  {
    for (int i = 0; i < actionListeners.size(); i++)
    {
      ((SageTVActionListener) actionListeners.elementAt(i)).
      actionPerformed(evt);
    }
  }

  public void buildRenderingOps(java.util.ArrayList opList, java.awt.geom.Rectangle2D.Float clipRect,
      int diffuseColor, float alphaFactor, float xoff, float yoff, int flags)
  {
    if (myImage == null)
    {
      lastRops.clear();
      crossFadeImage = null;
      crossEffectIn = null;
      return;
    }
    MetaImage currDrawImage;
    MetaImage currDiffuseImage = diffuseImage;
    if (pressed && (pressedImage != null))
      currDrawImage = pressedImage;
    else if (rollState && (rolloverImage != null))
      currDrawImage = rolloverImage;
    else
      currDrawImage = myImage;
    if (currDrawImage.getHeight() <= 0 || currDrawImage.getWidth() <= 0)
    {
      lastRops.clear();
      crossFadeImage = null;
      crossEffectIn = null;
      return;
    }
    float ass = ((float) currDrawImage.getWidth())/currDrawImage.getHeight();
    float par = reality.getUIMgr().getVideoFrame().getPixelAspectRatio();
    if (par > 0)
      ass /= par;
    java.awt.geom.Rectangle2D.Float destRect = null;
    if (currDiffuseImage != null && !scaleDiffuse)
    {
      // Build up our actual destination rectangle for the whole image so the rendering op can use
      // this as as base to determine the diffuse image's coordinates
      destRect = new java.awt.geom.Rectangle2D.Float(getTrueXf(), getTrueYf(), getWidthf(), getHeightf());
    }

    if (crossFadeImage != null)
    {
      // Switching to a new cross-fade target
      if (!lastRops.isEmpty())
      {
        fadeOutImageRops = lastRops;
        lastRops = new java.util.ArrayList();
        float lastFadeProgress = 1.0f;
        if (crossEffectIn != null && crossEffectIn.isActive())
          lastFadeProgress = crossEffectIn.getCurrentFade();
        crossEffectIn = new EffectTracker((ZPseudoComp) parent, 0, crossFadeDuration, (byte)0, EffectTracker.SCALE_LINEAR);
        crossEffectOut = new EffectTracker((ZPseudoComp) parent, 0, crossFadeDuration, (byte)0, EffectTracker.SCALE_LINEAR);
        crossEffectIn.setFadeEffect(0, 1);
        crossEffectOut.setFadeEffect(lastFadeProgress, 0);
        crossEffectIn.setInitialPositivity(false);
        crossEffectOut.setInitialPositivity(false);
        crossEffectIn.setPositivity(true);
        crossEffectOut.setPositivity(true);
      }
      crossFadeImage = null;
    }

    if (crossEffectIn != null)
    {
      if (!crossEffectIn.isActive())
      {
        crossEffectOut = null;
        crossEffectIn = null;
        fadeOutImageRops = null;
      }
      // Kill the out effect if we're scrolling since its coordinates will not be correct
      else if ((flags & ZPseudoComp.RENDER_FLAG_SCROLLING) != 0)
        crossEffectOut = null;
      else if (crossEffectOut != null)
      {
        boolean fsImage = getWidth() == reality.getWidth() && getHeight() == reality.getHeight();
        if (!fsImage)
          opList.add(new RenderingOp(crossEffectOut));
        opList.addAll(fadeOutImageRops);
        if (!fsImage)
          opList.add(new RenderingOp(null));
      }
    }
    MiniClientSageRenderer mcsr = (reality.getRenderEngine() instanceof MiniClientSageRenderer) ? ((MiniClientSageRenderer) reality.getRenderEngine()) : null;
    boolean canFlipX = mcsr == null || (mcsr.getGfxScalingCaps() & MiniClientSageRenderer.GFX_SCALING_FLIPH) != 0;
    boolean canFlipY = mcsr == null || (mcsr.getGfxScalingCaps() & MiniClientSageRenderer.GFX_SCALING_FLIPV) != 0;
    boolean didFlipX = false;
    boolean didFlipY = false;
    boolean canDiffuse = (mcsr != null && mcsr.hasDiffuseTextureSupport()) || (mcsr == null && !(reality.getRenderEngine() instanceof Java2DSageRenderer));
    if (!myImage.isNullOrFailed())
    {
      if (((currDiffuseImage != null || diffuseColor != 0xFFFFFF) && !canDiffuse) ||
          (!canFlipX && (flags & ZPseudoComp.RENDER_FLAG_FLIP_X) != 0) ||
          (!canFlipY && (flags & ZPseudoComp.RENDER_FLAG_FLIP_Y) != 0))
      {
        MetaImage effectSrcImage = currDrawImage;
        if (effectSrcImage instanceof MetaImage.Waiter)
          effectSrcImage = ((MetaImage.Waiter) effectSrcImage).getBase();
        // We need to create the image effect on the server since the client can't do it itself
        // NOTE: WE NEED TO PROVIDE THE SCALED DIFFUSE COORDINATES AS WELL HERE
        if (!bgLoader)
        {
          currDrawImage = MetaImage.getMetaImage(effectSrcImage, currDiffuseImage, null, didFlipX = (!canFlipX && (flags & ZPseudoComp.RENDER_FLAG_FLIP_X) != 0),
              didFlipY = (!canFlipY && (flags & ZPseudoComp.RENDER_FLAG_FLIP_Y) != 0), diffuseColor);
        }
        else
        {
          java.util.Vector effectDataVec = new java.util.Vector();
          effectDataVec.add(effectSrcImage);
          effectDataVec.add(currDiffuseImage);
          effectDataVec.add(null);
          effectDataVec.add((didFlipX = (!canFlipX && (flags & ZPseudoComp.RENDER_FLAG_FLIP_X) != 0)) ? Boolean.TRUE : Boolean.FALSE);
          effectDataVec.add((didFlipY = (!canFlipY && (flags & ZPseudoComp.RENDER_FLAG_FLIP_Y) != 0)) ? Boolean.TRUE : Boolean.FALSE);
          effectDataVec.add(new Integer(diffuseColor));
          if (effectDataVec.equals(altWaitObject))
            effectDataVec = (java.util.Vector) altWaitObject;
          else
            altWaitObject = effectDataVec;
          currDrawImage = reality.getUIMgr().getBGLoader().getMetaImageFast(effectDataVec, (ZPseudoComp) parent, null);
        }
        currDiffuseImage = null;
        diffuseColor = 0xFFFFFF;
      }
      else
        altWaitObject = null;
    }
    else
      altWaitObject = null;

    if (crossEffectIn != null)
      opList.add(new RenderingOp(crossEffectIn));
    float w = currDrawImage.getWidth();
    float h = currDrawImage.getHeight();

    lastRops.clear();

    xoff += boundsf.x;
    yoff += boundsf.y;
    float orgclipx=clipRect.x, orgclipy=clipRect.y, orgclipw=clipRect.width, orgcliph=clipRect.height;
    //		clipRect.x -= boundsf.x;
    //		clipRect.y -= boundsf.y;
    // Special case where we need to crop the image when clipping is disabled
    if (cropToFill && reality.getUIMgr().disableParentClip())
      clipRectToBounds(clipRect, xoff, yoff);

    // We'll have to do the translation here instead of passing it into the RenderingOp.
    // That means we also need to transform the clipRect into our coordinate system.
    // This is because we're performing a scale; so we need to fix the clip rect like we do in ZPseudoComp
    RenderingOp rop = null;
    if (!scaling)
    {
      float xOffset = hAlignment * (boundsf.width - w);
      float yOffset = vAlignment * (boundsf.height - h);
      if (reality.isIntegerPixels())
      {
        xOffset = Math.round(xOffset);
        yOffset = Math.round(yOffset);
      }
      rop = new RenderingOp(currDrawImage, 0, currDiffuseImage,
          diffuseColor, alphaFactor, clipRect, destRect,
          xOffset + xoff, yOffset + yoff, w, h);
      lastRops.add(rop);
    }
    else
    {
      float fullTargetWidth, fullTargetHeight, xOffset=xoff, yOffset=yoff;
      if (stretch)
      {
        fullTargetWidth = boundsf.width;
        fullTargetHeight = boundsf.height;
      }
      else if ((ass*boundsf.height <= boundsf.width) != cropToFill)
      {
        fullTargetWidth = ass*boundsf.height;
        fullTargetHeight = boundsf.height;
        xOffset = hAlignment * (boundsf.width - ass*boundsf.height);
      }
      else
      {
        yOffset = vAlignment * (boundsf.height - boundsf.width/ass);
        fullTargetWidth = boundsf.width;
        fullTargetHeight = (boundsf.width/ass);
      }
      // NOTE: We should really only do this when we're rendering in Integer pixels,
      // currently that's the miniclient renderer
      if (reality.isIntegerPixels())
      {
        fullTargetWidth = (float)Math.floor(fullTargetWidth);
        fullTargetHeight = (float)Math.floor(fullTargetHeight);
        xOffset = Math.round(xOffset);
        yOffset = Math.round(yOffset);
      }

      // Check to see if we should let the renderer do the scaling insets itself so it can cache it more efficiently
      boolean doScalingInsetsNow = reality.getRenderType() == ZRoot.NATIVE3DRENDER || (reality.getRenderType() == ZRoot.REMOTE2DRENDER &&
          (((MiniClientSageRenderer)reality.getRenderEngine()).getGfxScalingCaps() & MiniClientSageRenderer.GFX_SCALING_HW) != 0 &&
          !Sage.getBoolean("ui/enable_hardware_scaling_cache", false));

      if (scalingInsets != null && ((scalingInsets.top + scalingInsets.bottom + 2 < fullTargetHeight) ||
          (scalingInsets.left + scalingInsets.right + 2 < fullTargetWidth)) && cornerArc <= 0 &&
          (Sage.getBoolean("ui/enable_scaling_insets", true)))
      {
        // See what kind of scaling to apply to the destination of the scaling insets
        int realityHeight = reality.getHeight();
        int realityWidth = reality.getWidth();
        if ((reality.getRenderEngine() instanceof Java2DSageRenderer &&
            ((Java2DSageRenderer) reality.getRenderEngine()).hasOSDRenderer()) || reality.getRenderType() == ZRoot.REMOTE2DRENDER)
        {
          realityHeight = reality.getHeight();
          realityWidth = reality.getWidth();
        }
        float osh = reality.getUIMgr().getOverscanScaleHeight();
        float osw = reality.getUIMgr().getOverscanScaleWidth();
        int baseHeight = reality.getScalingInsetsBaseHeight();
        int baseWidth = reality.getScalingInsetsBaseWidth();
        float insetsScaleX = (realityWidth <= 0) ? 1.0f : ((realityWidth * osw)/((float)baseWidth));
        float insetsScaleY = (realityHeight <= 0) ? 1.0f : ((realityHeight * osh)/((float)baseHeight));
        if (scalingInsets != null)
        {
          // NOTE: XBMC allows you to specify scaling insets that cover the whole image; but then they still render the middle of it.
          // Reduce the insets by 1 in this case so we match better
          if (scalingInsets.left + scalingInsets.right == w)
          {
            scalingInsets.left--;
            scalingInsets.right--;
          }
          if (scalingInsets.top + scalingInsets.bottom == h)
          {
            scalingInsets.top--;
            scalingInsets.bottom--;
          }
        }

        if (srcScalingInsets == null)
          srcScalingInsets = new java.awt.Insets(scalingInsets.top, scalingInsets.left, scalingInsets.bottom, scalingInsets.right);
        else
        {
          srcScalingInsets.top = scalingInsets.top;
          srcScalingInsets.bottom = scalingInsets.bottom;
          srcScalingInsets.left = scalingInsets.left;
          srcScalingInsets.right = scalingInsets.right;
        }
        if (destScalingInsets == null)
          destScalingInsets = new java.awt.Insets(scalingInsets.top, scalingInsets.left, scalingInsets.bottom, scalingInsets.right);
        else
        {
          destScalingInsets.top = scalingInsets.top;
          destScalingInsets.bottom = scalingInsets.bottom;
          destScalingInsets.left = scalingInsets.left;
          destScalingInsets.right = scalingInsets.right;
        }
        destScalingInsets.top = Math.round(insetsScaleY * destScalingInsets.top);
        destScalingInsets.bottom = Math.round(insetsScaleY * destScalingInsets.bottom);
        destScalingInsets.left = Math.round(insetsScaleX * destScalingInsets.left);
        destScalingInsets.right = Math.round(insetsScaleX * destScalingInsets.right);
        if (destScalingInsets.top + destScalingInsets.bottom + 2 >= fullTargetHeight)
          destScalingInsets.top = destScalingInsets.bottom = srcScalingInsets.top = srcScalingInsets.bottom = 0;
        if (destScalingInsets.left + destScalingInsets.right + 2 >= fullTargetWidth)
          destScalingInsets.left = destScalingInsets.right = srcScalingInsets.left = srcScalingInsets.right = 0;

        if (didFlipX)
        {
          int tmp = srcScalingInsets.left;
          srcScalingInsets.left = srcScalingInsets.right;
          srcScalingInsets.right = tmp;
          tmp = destScalingInsets.left;
          destScalingInsets.left = destScalingInsets.right;
          destScalingInsets.right = tmp;
        }
        if (didFlipY)
        {
          int tmp = srcScalingInsets.top;
          srcScalingInsets.top = srcScalingInsets.bottom;
          srcScalingInsets.bottom = tmp;
          tmp = destScalingInsets.top;
          destScalingInsets.top = destScalingInsets.bottom;
          destScalingInsets.bottom = tmp;
        }
        if (doScalingInsetsNow)
        {
          float remv = h - srcScalingInsets.top - srcScalingInsets.bottom;
          float remh = w - srcScalingInsets.left - srcScalingInsets.right;
          float centervscale = (remv <= 0) ? 0 : ((fullTargetHeight - destScalingInsets.top - destScalingInsets.bottom) / remv);
          float centerhscale = (remh <= 0) ? 0 : ((fullTargetWidth - destScalingInsets.left - destScalingInsets.right) / remh);
          float topvscale = ((float) destScalingInsets.top) / srcScalingInsets.top;
          float bottomvscale = ((float) destScalingInsets.bottom) / srcScalingInsets.bottom;
          float lefthscale = ((float) destScalingInsets.left) / srcScalingInsets.left;
          float righthscale = ((float) destScalingInsets.right) / srcScalingInsets.right;

          // This is done as 9 separate image copies.
          // Top left corner (no scaling)
          java.awt.geom.Rectangle2D.Float segmentRect = new java.awt.geom.Rectangle2D.Float();
          segmentRect.setRect(xOffset, yOffset, destScalingInsets.left, destScalingInsets.top);
          segmentRect.intersect(clipRect, segmentRect, segmentRect);
          if (segmentRect.getWidth() > 0 && segmentRect.getHeight() > 0 && srcScalingInsets.left != 0 && srcScalingInsets.top != 0)
          {
            opList.add(rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, segmentRect, destRect,
                0, 0, srcScalingInsets.left, srcScalingInsets.top,
                xOffset, yOffset, destScalingInsets.left, destScalingInsets.top));
            lastRops.add(rop);
          }

          // Top right corner (no scaling)
          segmentRect.setRect(xOffset + fullTargetWidth - destScalingInsets.right, yOffset, destScalingInsets.right, destScalingInsets.top);
          segmentRect.intersect(clipRect, segmentRect, segmentRect);
          if (segmentRect.getWidth() > 0 && segmentRect.getHeight() > 0 && srcScalingInsets.right != 0 && srcScalingInsets.top != 0)
          {
            opList.add(rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, segmentRect, destRect,
                w - srcScalingInsets.right, 0, srcScalingInsets.right, srcScalingInsets.top,
                xOffset + fullTargetWidth - destScalingInsets.right, yOffset, destScalingInsets.right, destScalingInsets.top));
            lastRops.add(rop);
          }

          // Bottom left corner (no scaling)
          segmentRect.setRect(xOffset, yOffset + fullTargetHeight - destScalingInsets.bottom, destScalingInsets.left, destScalingInsets.bottom);
          segmentRect.intersect(clipRect, segmentRect, segmentRect);
          if (segmentRect.getWidth() > 0 && segmentRect.getHeight() > 0 && srcScalingInsets.left != 0 && srcScalingInsets.bottom != 0)
          {
            opList.add(rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, segmentRect, destRect,
                0, h - srcScalingInsets.bottom, srcScalingInsets.left, srcScalingInsets.bottom,
                xOffset, yOffset + fullTargetHeight - destScalingInsets.bottom, destScalingInsets.left, destScalingInsets.bottom));
            lastRops.add(rop);
          }

          // Bottom right corner (no scaling)
          segmentRect.setRect(xOffset + fullTargetWidth - destScalingInsets.right, yOffset + fullTargetHeight - destScalingInsets.bottom,
              destScalingInsets.right, destScalingInsets.bottom);
          segmentRect.intersect(clipRect, segmentRect, segmentRect);
          if (segmentRect.getWidth() > 0 && segmentRect.getHeight() > 0 && srcScalingInsets.right != 0 && srcScalingInsets.bottom != 0)
          {
            opList.add(rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, segmentRect, destRect,
                w - srcScalingInsets.right, h - srcScalingInsets.bottom, srcScalingInsets.right, srcScalingInsets.bottom,
                xOffset + fullTargetWidth - destScalingInsets.right, yOffset + fullTargetHeight - destScalingInsets.bottom,
                destScalingInsets.right, destScalingInsets.bottom));
            lastRops.add(rop);
          }

          // Left side (scaled vertically)
          segmentRect.setRect(xOffset, yOffset + destScalingInsets.top, destScalingInsets.left, fullTargetHeight - destScalingInsets.bottom - destScalingInsets.top);
          segmentRect.intersect(clipRect, segmentRect, segmentRect);
          if (segmentRect.getWidth() > 0 && segmentRect.getHeight() > 0 && srcScalingInsets.left != 0 && centervscale != 0)
          {
            opList.add(rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, segmentRect, destRect,
                0, srcScalingInsets.top, srcScalingInsets.left, h - srcScalingInsets.top - srcScalingInsets.bottom,
                xOffset, yOffset + destScalingInsets.top, destScalingInsets.left, fullTargetHeight - destScalingInsets.bottom - destScalingInsets.top));
            lastRops.add(rop);
          }

          // Right side (scaled vertically)
          segmentRect.setRect(xOffset + fullTargetWidth - destScalingInsets.right, yOffset + destScalingInsets.top,
              destScalingInsets.right, fullTargetHeight - destScalingInsets.bottom - destScalingInsets.top);
          segmentRect.intersect(clipRect, segmentRect, segmentRect);
          if (segmentRect.getWidth() > 0 && segmentRect.getHeight() > 0 && srcScalingInsets.right != 0 && centervscale != 0)
          {
            opList.add(rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, segmentRect, destRect,
                w - srcScalingInsets.right, srcScalingInsets.top, srcScalingInsets.right, h - srcScalingInsets.top - srcScalingInsets.bottom,
                xOffset + fullTargetWidth - destScalingInsets.right, yOffset + destScalingInsets.top,
                destScalingInsets.right, fullTargetHeight - destScalingInsets.bottom - destScalingInsets.top));
            lastRops.add(rop);
          }

          // Top side (scaled horizontally)
          segmentRect.setRect(xOffset + destScalingInsets.left, yOffset, fullTargetWidth - destScalingInsets.left - destScalingInsets.right, destScalingInsets.top);
          segmentRect.intersect(clipRect, segmentRect, segmentRect);
          if (segmentRect.getWidth() > 0 && segmentRect.getHeight() > 0 && srcScalingInsets.top != 0 && centerhscale != 0)
          {
            opList.add(rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, segmentRect, destRect,
                srcScalingInsets.left, 0, w - srcScalingInsets.left - srcScalingInsets.right, srcScalingInsets.top,
                xOffset + destScalingInsets.left, yOffset, fullTargetWidth - destScalingInsets.left - destScalingInsets.right,
                destScalingInsets.top));
            lastRops.add(rop);
          }

          // Bottom side (scaled horizontally)
          segmentRect.setRect(xOffset + destScalingInsets.left, yOffset + fullTargetHeight - destScalingInsets.bottom,
              fullTargetWidth - destScalingInsets.left - destScalingInsets.right, destScalingInsets.bottom);
          segmentRect.intersect(clipRect, segmentRect, segmentRect);
          if (segmentRect.getWidth() > 0 && segmentRect.getHeight() > 0 && srcScalingInsets.bottom != 0 && centerhscale != 0)
          {
            opList.add(rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, segmentRect, destRect,
                srcScalingInsets.left, h - srcScalingInsets.bottom,
                w - srcScalingInsets.left - srcScalingInsets.right, srcScalingInsets.bottom,
                xOffset + destScalingInsets.left, yOffset + fullTargetHeight - destScalingInsets.bottom,
                fullTargetWidth - destScalingInsets.left - destScalingInsets.right, destScalingInsets.bottom));
            lastRops.add(rop);
          }

          // Center (scaled both directions)
          segmentRect.setRect(xOffset + destScalingInsets.left, yOffset + destScalingInsets.top,
              fullTargetWidth - destScalingInsets.left - destScalingInsets.right, fullTargetHeight - destScalingInsets.bottom - destScalingInsets.top);
          segmentRect.intersect(clipRect, segmentRect, segmentRect);
          if (segmentRect.getWidth() > 0 && segmentRect.getHeight() > 0 && centerhscale != 0 && centervscale != 0)
          {
            opList.add(rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, segmentRect, destRect,
                srcScalingInsets.left, srcScalingInsets.top,
                w - srcScalingInsets.left - srcScalingInsets.right, h - srcScalingInsets.bottom - srcScalingInsets.top,
                xOffset + destScalingInsets.left, yOffset + destScalingInsets.top,
                fullTargetWidth - destScalingInsets.left - destScalingInsets.right,
                fullTargetHeight - destScalingInsets.top - destScalingInsets.bottom));
            lastRops.add(rop);
          }

          rop = null;
        }
        else
        {
          // We'll do the scaling insets in the renderer when it caches the image instead
          javax.vecmath.Matrix4f scaleTransform;
          if (stretch)
          {
            rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, clipRect, destRect,
                0, 0, w, h, xoff, yoff, boundsf.width, boundsf.height);
          }
          else if ((ass*boundsf.height <= boundsf.width) != cropToFill)
          {
            float targetX = xoff + (hAlignment * (boundsf.width - ass*boundsf.height));
            float targetW = ass*boundsf.height;
            if (reality.isIntegerPixels())
            {
              targetW = (float)(Math.floor(targetX + targetW) - Math.floor(targetX));
              targetX = (float)Math.floor(targetX);
            }
            rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, clipRect, destRect,
                0, 0, w, h, targetX,
                yoff, targetW, boundsf.height);
          }
          else
          {
            float targetY = yoff + (vAlignment * (boundsf.height - boundsf.width/ass));
            float targetH = boundsf.width/ass;
            if (reality.isIntegerPixels())
            {
              targetH = (float)(Math.floor(targetY + targetH) - Math.floor(targetY));
              targetY = (float)Math.floor(targetY);
            }
            rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, clipRect, destRect,
                0, 0, w, h, xoff, targetY, boundsf.width, targetH);
          }
          rop.privateData = new java.awt.Insets[] { srcScalingInsets, destScalingInsets };
          // NOTE: We cannot use a corner arc with cached scaling insets because they both use
          // the imageOption feature of MetaImage and it would conflict
          cornerArc = 0;
          lastRops.add(rop);
        }
      }
      else
      {
        javax.vecmath.Matrix4f scaleTransform;
        if (stretch)
        {
          rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, clipRect, destRect,
              0, 0, w, h, xoff, yoff, boundsf.width, boundsf.height);
        }
        else if ((ass*boundsf.height <= boundsf.width) != cropToFill)
        {
          float targetX = xoff + (hAlignment * (boundsf.width - ass*boundsf.height));
          float targetW = ass*boundsf.height;
          if (reality.isIntegerPixels())
          {
            targetW = (float)(Math.floor(targetX + targetW) - Math.floor(targetX));
            targetX = (float)Math.floor(targetX);
          }
          rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, clipRect, destRect,
              0, 0, w, h, targetX,
              yoff, targetW, boundsf.height);
        }
        else
        {
          float targetY = yoff + (vAlignment * (boundsf.height - boundsf.width/ass));
          float targetH = boundsf.width/ass;
          if (reality.isIntegerPixels())
          {
            targetH = (float)(Math.floor(targetY + targetH) - Math.floor(targetY));
            targetY = (float)Math.floor(targetY);
          }
          rop = new RenderingOp(currDrawImage, 0, currDiffuseImage, diffuseColor, alphaFactor, clipRect, destRect,
              0, 0, w, h, xoff, targetY, boundsf.width, targetH);
        }
        lastRops.add(rop);
      }
    }
    if (rop != null)
    {
      if (cornerArc > 0)
      {
        rop.primitive = new SageRenderer.ShapeDescription();
        rop.primitive.cornerArc = cornerArc;
        rop.textureIndex = currDrawImage.getImageIndex(new java.awt.geom.RoundRectangle2D.Float(0, 0,
            currDrawImage.getWidth(0), currDrawImage.getHeight(0), cornerArc, cornerArc));
      }
      opList.add(rop);
    }
    if (crossEffectIn != null)
      opList.add(new RenderingOp(null));
    clipRect.setFrame(orgclipx, orgclipy, orgclipw, orgcliph);
  }

  public void setEnabled(boolean x)
  {
    if (enabled != x)
    {
      enabled = x;
      appendToDirty(false);//reality.renderOnce();
    }
  }

  public void setBlankWhenDisabled(boolean x)
  {
    blankWhenDisabled = x;
  }

  public void setHAlignment(float inHAlignment)
  {
    hAlignment = inHAlignment;
  }

  public void setVAlignment(float inVAlignment)
  {
    vAlignment = inVAlignment;
  }

  public void setPressedImage(MetaImage inPressedImage)
  {
    pressedImage = inPressedImage;
  }

  public void setHoverImage(MetaImage inHoverImage)
  {
    rolloverImage = inHoverImage;
  }

  public void setDiffuseImage(MetaImage inDiffuseImage)
  {
    diffuseImage = inDiffuseImage;
  }

  public void setScaleDiffuse(boolean x)
  {
    scaleDiffuse = x;
  }

  public void setCornerArc(int x)
  {
    cornerArc = x;
  }

  public void setStretch(boolean x) { stretch = x; }
  public void setScaling(boolean x) { scaling = x; }
  public void setCropToFill(boolean x) { cropToFill = x; }

  public java.awt.geom.Rectangle2D.Float getPreferredSize(float availableWidth, float availableHeight,
      float parentWidth, float parentHeight, int depth)
  {
    // We only make changes to our image size if it's smaller than what will be displayed and we're preserving the aspect ratio
    if (myImage == null)
    {
      // this is for the border images to properly display themselves when the actual image is empty
      if (reality.getUIMgr().isXBMCCompatible() && availableWidth < 10000 && availableHeight < 10000)
        prefSize.setFrame(0, 0, availableWidth, availableHeight);
      else
        prefSize.setFrame(0, 0, 0, 0);
    }
    else if (!stretch && scaling && !cropToFill && (arMaximize || myImage.getWidth() > availableWidth ||
        myImage.getHeight() > availableHeight || reality.getUIMgr().isXBMCCompatible()))
    {
      float ass = ((float) myImage.getWidth())/myImage.getHeight();
      float par = reality.getUIMgr().getVideoFrame().getPixelAspectRatio();
      if (par > 0)
        ass /= par;
      float w = myImage.getWidth();
      float h = myImage.getHeight();
      if (ass*availableHeight <= availableWidth)
      {
        prefSize.setFrame(0, 0, ass*availableHeight, availableHeight);
      }
      else
      {
        prefSize.setFrame(0, 0, availableWidth, availableWidth/ass);
      }
    }
    else
      prefSize.setFrame(0, 0, myImage.getWidth(), myImage.getHeight());
    return prefSize;
  }

  public void setAutoRepeat(boolean x)
  {
    autoRepeat = x;
  }

  public void setScalingInsets(java.awt.Insets ins)
  {
    scalingInsets = ins;
  }

  public MetaImage getImage()
  {
    return myImage;
  }

  public void setCrossFadeDuration(int x)
  {
    crossFadeDuration = x;
  }

  protected void clearRecursiveChildContexts2(Catbert.Context parentContext)
  {
    lastRops.clear();
    crossFadeImage = null;
    pendingImage = null;
    fadeOutImageRops = null;
    crossEffectIn = crossEffectOut = null;
    ignoreNextCrossFade = true;
  }

  public boolean isBgLoader()
  {
    return bgLoader;
  }

  public void setBgLoader(boolean x)
  {
    bgLoader = x;
  }

  protected boolean processHideEffects(boolean validRegion)
  {
    // Prevent cross-fade in from occurring
    ignoreNextCrossFade = true;
    crossFadeImage = null;
    return false;
  }

  public void setARMaximize(boolean x)
  {
    arMaximize = x;
  }

  protected MetaImage myImage;
  private java.util.ArrayList fadeOutImageRops; // used for cross-fade effect
  protected MetaImage crossFadeImage;
  private java.util.ArrayList lastRops = new java.util.ArrayList();
  private EffectTracker crossEffectOut;
  private EffectTracker crossEffectIn;
  private MetaImage rolloverImage;
  private MetaImage pressedImage;
  private MetaImage diffuseImage;
  private boolean scaleDiffuse;
  java.util.Vector actionListeners;
  private boolean rollState;
  private boolean pressed;
  private boolean ignoreRelease = true;
  protected boolean enabled;
  private boolean blankWhenDisabled = false;
  private float hAlignment = 0.5f;
  private float vAlignment = 0.5f;
  private String tooltip;
  private boolean stretch = false;
  private boolean scaling = false;
  private boolean cropToFill = false;
  private int cornerArc;
  private boolean autoRepeat;
  private java.util.TimerTask autoRepeatTimer;
  private int crossFadeDuration;
  private boolean bgLoader;
  private MetaImage pendingImage;
  private Object altWaitObject;
  private boolean ignoreNextCrossFade;
  private boolean arMaximize;

  protected java.awt.Insets scalingInsets;
  protected java.awt.Insets srcScalingInsets;
  protected java.awt.Insets destScalingInsets;
}
