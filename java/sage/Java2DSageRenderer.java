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

public class Java2DSageRenderer extends SageRenderer
{
  public static final boolean CACHE_SHAPES = false;
  public static final java.awt.Color TRANSPARENT_BLACK = new java.awt.Color(0, 0, 0, 0);
  public static final String OSD_RENDERING_PLUGIN_CLASS = "osd_rendering_plugin_class";
  public Java2DSageRenderer(ZRoot inMaster)
  {
    super(inMaster);
    vf = uiMgr.getVideoFrame();
    enable2DScaleCache = !uiMgr.getBoolean("ui/disable_2d_scaling_cache", false);
  }

  public boolean allocateBuffers(int width, int height)
  {
    return true;
  }

  public void preprocessNextDisplayList(java.util.ArrayList v)
  {
    cullDisplayList(v);
    for (int i = 0; i < v.size(); i++)
    {
      RenderingOp op = (RenderingOp) v.get(i);
      if (!op.isAnimationOp() && !op.isEffectOp() && op.srcRect != null && (op.srcRect.width <= 0 || op.srcRect.height <= 0))
      {
        v.remove(i);
        i--;
      }
      if (op.isImageOp())
      {
        // Cache the image if its going to be scaled, otherwise just ensure that cached image stays alive
        if (enable2DScaleCache &&
            (Math.round(op.copyImageRect.width) != op.texture.getWidth(op.textureIndex) ||
            Math.round(op.copyImageRect.height) != op.texture.getHeight(op.textureIndex)))
        {
          int targetW, targetH;
          if (true/*MathUtils.isTranslateScaleOnlyMatrix(op.renderXform) &&
						MathUtils.getScaleX(op.renderXform) >= 0 && MathUtils.getScaleY(op.renderXform) >= 0*/)
          {
            float scaleX = op.texture.getWidth(op.textureIndex) / op.copyImageRect.width;
            float scaleY = op.texture.getHeight(op.textureIndex) / op.copyImageRect.height;
            // Removing the scaling from the transformation and leave just the translation
            float transX = op.copyImageRect.x;//MathUtils.getTranslateX(op.renderXform);
            float transY = op.copyImageRect.y;//MathUtils.getTranslateY(op.renderXform);
            //op.renderXform = MathUtils.createTranslateMatrix(transX, transY);
            op.scaleSrc(1/scaleX, 1/scaleY);
            //						op.srcRect.x /= scaleX;
            //						op.srcRect.width /= scaleX;
            //						op.srcRect.height /= scaleY;
            //						op.srcRect.y /= scaleY;
            targetW = Math.round(op.copyImageRect.width);
            targetH = Math.round(op.copyImageRect.height);
          }
          else
          {
            targetW = op.texture.getWidth(op.textureIndex);
            targetH = op.texture.getHeight(op.textureIndex);
          }
          if (op.primitive != null && op.primitive.cornerArc > 0)
            op.textureIndex = op.texture.getImageIndex(targetW,
                targetH, new java.awt.geom.RoundRectangle2D.Float(0, 0,
                    targetW, targetH,
                    op.primitive.cornerArc, op.primitive.cornerArc));
          else if (op.privateData instanceof java.awt.Insets[])
          {
            op.textureIndex =
                op.texture.getImageIndex(
                    targetW,
                    targetH,
                    (java.awt.Insets[]) op.privateData);
          }
          else
            op.textureIndex = op.texture.getImageIndex(targetW, targetH);
        }
      }
      /*else if (op.isPrimitiveOp() && CACHE_SHAPES)
			{
				// Convert the primitive to an image
				op.texture = getAcceleratedShape(op.primitive);
				op.primitive = null;
			}*/
      else if (op.isTextOp() && !(op.text.font instanceof MetaFont.JavaFont))
      {
        convertGlyphsToCachedImages(op);
        if (op.text.fontImage != null && op.text.renderImageNumCache != null)
        {
          for (int j = 0; j < op.text.renderImageNumCache.length; j++)
          {
            if (op.text.renderImageNumCache[j] != -1)
            {
              op.text.fontImage.getJavaImage(op.text.renderImageNumCache[j]);
              op.text.fontImage.removeJavaRef(op.text.renderImageNumCache[j]);
            }
          }
        }
        continue;
      }
    }
  }

  protected boolean compositeSurfaces(Object targetSurface, Object srcSurface, float alphaFactor, java.awt.geom.Rectangle2D region)
  {
    java.awt.image.BufferedImage target = (java.awt.image.BufferedImage) targetSurface;
    java.awt.image.BufferedImage src = (java.awt.image.BufferedImage) srcSurface;
    if (src != null && target != null)
    {
      java.awt.Graphics2D g2 = target.createGraphics();
      g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alphaFactor));
      int x = (int)Math.round(region.getX());
      int y = (int)Math.round(region.getY());
      int x2 = (int)Math.round(region.getX() + region.getWidth());
      int y2 = (int)Math.round(region.getY() + region.getHeight());
      g2.drawImage(src, x, y, x2, y2, x, y, x2, y2, null);
      g2.dispose();
      return true;
    }
    return false;
  }

  public boolean executeDisplayList(java.awt.Rectangle clipRect)
  {
    //		if (Sage.DBG) System.out.println("Java2DSageRenderer is executing displayList=" + currDisplayList);
    if (ANIM_DEBUG) System.out.println("Executing display list now");
    if (currDisplayList == null)
      return true; // should be false???
    boolean setVideoRegion = false;
    boolean usedVideoOp = false;
    refreshVideo = false;
    // NOW EXECUTE THE DISPLAY LIST
    java.awt.Graphics2D g2;
    if (uiMgr.getBoolean("ui/disable_2d_double_buffering", false) && !hasOSDRenderer())
    {
      if (primary != null)
      {
        primary.flush();
        primary = null;
      }
      g2 = (java.awt.Graphics2D) master.getGraphics();
    }
    else
    {
      if (primary == null || primary.getWidth() < master.getRoot().getWidth() ||
          primary.getHeight() < master.getRoot().getHeight())
      {
        if (primary != null)
        {
          primary.flush();
          primary = null;
        }
        Sage.gc();
        primary = new java.awt.image.BufferedImage(master.getRoot().getWidth(),
            master.getRoot().getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        if (Sage.DBG) System.out.println("Allocated primary buffer: " + primary);
      }
      g2 = primary.createGraphics();
    }
    //g2.setBackground(TRANSPARENT_BLACK);
    boolean hqRender = uiMgr.getBoolean("java2d_render_higher_quality", true);
    if (hqRender)
    {
      //			g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
      //			g2.setRenderingHint(java.awt.RenderingHints.KEY_DITHERING, java.awt.RenderingHints.VALUE_DITHER_ENABLE);
      g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.setRenderingHint(java.awt.RenderingHints.KEY_ALPHA_INTERPOLATION, java.awt.RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
      g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
      //			g2.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
    }
    else
    {
      //			g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_SPEED);
      //			g2.setRenderingHint(java.awt.RenderingHints.KEY_DITHERING, java.awt.RenderingHints.VALUE_DITHER_DISABLE);
      g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
      g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g2.setRenderingHint(java.awt.RenderingHints.KEY_ALPHA_INTERPOLATION, java.awt.RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
    }
    //g2.clearRect(clipRect.x, clipRect.y, clipRect.width, clipRect.height);

    // NOTE: Update on 12/8/04
    // I changed the clipping to be done in the destination space. This was due to rounding errors when filling
    // shapes. This caused the edge artifact in OSD rendering where there'd be horizontal lines left sometimes in the UI.
    // So now the clipping happens before we set the new transformation matrix. We also must intersect the destRect
    // with the clipRect to fix the problem...not exactly sure why we have to do that, but without it, it's not fixed.

    currSurface = primary;
    // These should be empty; but just in case there's an exception
    surfStack.clear();
    g2Stack.clear();
    animsThatGoAfterSurfPop.clear();

    java.awt.geom.AffineTransform baseXform = g2.getTransform();
    java.awt.geom.AffineTransform tempXform = new java.awt.geom.AffineTransform();
    java.util.Stack matrixStack = new java.util.Stack();
    long renderStartTime = Sage.eventTime();
    boolean effectAnimationsActive = false;
    boolean effectAnimationsLocked = false;
    java.util.Stack alphaStack = new java.util.Stack();
    float currEffectAlpha = 1.0f;
    java.util.Stack effectClipStack = new java.util.Stack();
    java.awt.geom.Rectangle2D.Float currEffectClip = null;
    matrixStack.push(baseXform);
    if (dlThatWasComposited != currDisplayList || !uiMgr.areLayersEnabled())
    {
      boolean freshDL = dlThatWasComposited != currDisplayList;
      dlThatWasComposited = null;

      // This is all of the surface names that have been used in Out animation operations. Therefore
      // they should NOT also be used for any In operation. If they are we should duplicate
      // the surface and use that for the Out operation.
      if (uiMgr.areLayersEnabled())
        fixDuplicateAnimSurfs(currDisplayList, clipRect);

      compositeOps.clear();
      java.util.Set clearedSurfs = new java.util.HashSet();
      javax.vecmath.Matrix4f tempMat = new javax.vecmath.Matrix4f();
      //java.awt.geom.AffineTransform myXform = new java.awt.geom.AffineTransform();
      java.awt.geom.Rectangle2D.Float tempClipRect = new java.awt.geom.Rectangle2D.Float();
      for (int i = 0; i <= currDisplayList.size(); i++)
      {
        RenderingOp op;
        if (i == currDisplayList.size())
        {
          if (waitIndicatorState && waitIndicatorRops != null)
          {
            op = (RenderingOp) waitIndicatorRops.get((++waitIndicatorRopsIndex) % waitIndicatorRops.size());
          }
          else
            break;
        }
        else
          op = (RenderingOp) currDisplayList.get(i);
        // Skip transparent rendering ops
        if (!op.isEffectOp() && currEffectAlpha == 0)
          continue;
        if (op.isEffectOp())
        {
          if (op.effectTracker != null)
          {
            op.effectTracker.processEffectState(renderStartTime, freshDL);
            alphaStack.push(new Float(currEffectAlpha));
            effectClipStack.push(currEffectClip);
            if (op.effectTracker.hasFade())
            {
              float nextFade = op.effectTracker.getCurrentFade();
              currEffectAlpha *= nextFade;
            }
            if (op.effectTracker.isKiller())
              currEffectAlpha = 0;
            if (op.effectTracker.getCurrentTransform(tempMat, op.srcRect))
            {
              if (op.destRect != null)
              {
                // Now apply the effect to the clipping rectangle to account for the actual effect; and then
                // reclip against what the rect actually is
                currEffectClip = MathUtils.transformRectCoords(op.destRect, MathUtils.createInverse(tempMat));
                currEffectClip.intersect(currEffectClip, op.destRect, currEffectClip);
                //								currEffectClip.intersect(currEffectClip, clipRect, currEffectClip);
              }
              MathUtils.convertToAffineTransform(tempMat, tempXform);
              tempXform.preConcatenate((java.awt.geom.AffineTransform) matrixStack.peek());
              matrixStack.push(tempXform.clone());
            }
            else
            {
              // So we match the pop() below
              matrixStack.push(matrixStack.peek());
            }
            if (op.effectTracker.isActive())
            {
              effectAnimationsActive = true;
              if (op.effectTracker.requiresCompletion())
                effectAnimationsLocked = true;
            }
          }
          else
          {
            matrixStack.pop();
            currEffectAlpha = ((Float) alphaStack.pop()).floatValue();
            currEffectClip = (java.awt.geom.Rectangle2D.Float) effectClipStack.pop();
          }
        }
        else if (op.isImageOp())
        {
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.min(1.0f, op.alphaFactor * currEffectAlpha)));
          //					java.awt.geom.AffineTransform currXform = op.renderXform;
          //				myXform.setToTranslation(op.copyImageRect.x, op.copyImageRect.y);
          // Workaround for JDK Bug# 4916948 to get floating point translation
          // BUT it causes a big performance hit because it reallocates everything it draws so don't do it
          //at.shear(0.0000001, 0.0000001);
          g2.setTransform((java.awt.geom.AffineTransform) matrixStack.peek());
          /*					if (op.destRect.width < 0)
					{
						op.destRect.x += op.destRect.width;
						op.destRect.width *= -1;
					}
					if (op.destRect.height < 0)
					{
						op.destRect.y += op.destRect.height;
						op.destRect.height *= -1;
					}*/
          g2.setClip(op.destRect.createIntersection(currEffectClip != null ? (java.awt.geom.Rectangle2D)currEffectClip : (java.awt.geom.Rectangle2D)clipRect));
          /*					if ((op.opFlags & RenderingOp.RENDER_FLAG_FLIP_X) != 0 && (op.opFlags & RenderingOp.RENDER_FLAG_FLIP_Y) != 0)
					{
						g2.scale(-1, -1);
						g2.translate(-1*(2*op.destRect.x + op.destRect.width), -1*(2*op.destRect.y + op.destRect.height));
					}
					else if ((op.opFlags & RenderingOp.RENDER_FLAG_FLIP_X) != 0)
					{
						g2.scale(-1, 1);
						g2.translate(-1*(2*op.destRect.x + op.destRect.width), 0);
					}
					else if ((op.opFlags & RenderingOp.RENDER_FLAG_FLIP_Y) != 0)
					{
						g2.scale(1, -1);
						g2.translate(0, -1*(2*op.destRect.y + op.destRect.height));
					}
           */					java.awt.Image bi = op.texture.getJavaImage(op.textureIndex);
           ImageUtils.fixAlphaInconsistency(bi);
           boolean deallocBi = false;
           if (op.renderColor != null && bi instanceof java.awt.image.BufferedImage)
           {
             float currAlpha = op.renderColor.getAlpha() / 255.0f;
             java.awt.Color textColor = new java.awt.Color((int)(op.renderColor.getRed() * currAlpha), (int)(op.renderColor.getGreen() * currAlpha),
                 (int)(op.renderColor.getBlue() * currAlpha), (int)(currAlpha*255));
             java.awt.image.RescaleOp colorScaler = new java.awt.image.RescaleOp(
                 textColor.getRGBComponents(null), new float[] { 0f, 0f, 0f, 0f }, null);
             bi = colorScaler.filter((java.awt.image.BufferedImage)bi, null);
             deallocBi = true;
           }
           //					g2.setTransform(op.renderXform);
           //					MathUtils.convertToAffineTransform(op.renderXform, tempXform);
           //					g2.drawImage(bi, tempXform, null);
           g2.drawImage(bi, Math.round(op.destRect.x), Math.round(op.destRect.y),
               Math.round(op.destRect.x + op.destRect.width), Math.round(op.destRect.y + op.destRect.height),
               Math.round(op.srcRect.x), Math.round(op.srcRect.y), Math.round(op.srcRect.x + op.srcRect.width),
               Math.round(op.srcRect.y + op.srcRect.height), null);
           op.texture.removeJavaRef(op.textureIndex);
           if (deallocBi)
           {
             bi.flush();
             bi = null;
           }
        }
        else if (op.isPrimitiveOp())
        {
          g2.setTransform((java.awt.geom.AffineTransform)matrixStack.peek());
          g2.setClip(op.destRect.createIntersection(currEffectClip != null ? (java.awt.geom.Rectangle2D)currEffectClip : (java.awt.geom.Rectangle2D)clipRect));

          //MathUtils.convertToAffineTransform(op.renderXform, tempXform);
          //g2.transform(tempXform);
          g2.translate(op.copyImageRect.x, op.copyImageRect.y);
          java.awt.Paint oldPaint = g2.getPaint();
          java.awt.Stroke oldStroke = g2.getStroke();

          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.min(1.0f, op.alphaFactor * currEffectAlpha)));
          if (op.primitive.gradc1 != null)
            g2.setPaint(new java.awt.GradientPaint(op.primitive.fx1, op.primitive.fy1, op.primitive.gradc1,
                op.primitive.fx2, op.primitive.fy2, op.primitive.gradc2));
          else
            g2.setColor(op.primitive.color);

          // We need to disable AA or it won't use accelerated drawing (and we want to cache shapes that
          // we DO want rendered w/ AA, just like text & scaled images)
          float thicky = op.primitive.strokeSize;
          int cornerArc = op.primitive.cornerArc;
          if (!op.primitive.fill)
            g2.setStroke(new java.awt.BasicStroke(thicky));
          /*				if (op.primitive.fill)
						myXform.setToTranslation(op.destRect.x-op.srcRect.x, op.destRect.y-op.srcRect.y);
					else
						myXform.setToTranslation(op.destRect.x-op.srcRect.x+thicky/2, op.destRect.y-op.srcRect.y+thicky/2);
					if (op.renderXform != null)
					{
						myXform.concatenate(op.renderXform);
					}
					g2.setTransform(myXform);
           */				//if (op.renderXform != null)
          //	g2.setClip(null);
          //else
          {
            //					tempClipRect.setFrame(op.srcRect.x, op.srcRect.y, op.destRect.width, op.destRect.height);
            //					g2.setClip(tempClipRect);
          }
          //				g2.setClip(new java.awt.geom.Rectangle2D.Float(op.srcRect.x, op.srcRect.y, op.srcRect.width, op.srcRect.height));
          if (op.primitive.shapeType.equals("Circle") || op.primitive.shapeType.equals("Oval"))
          {
            if (op.primitive.fill)
              g2.fill(new java.awt.geom.Ellipse2D.Float(0, 0, op.primitive.shapeWidth, op.primitive.shapeHeight));
            else
              g2.draw(new java.awt.geom.Ellipse2D.Float(thicky/2, thicky/2,
                  op.primitive.shapeWidth - thicky, op.primitive.shapeHeight - thicky));
          }
          else if (op.primitive.shapeType.equals("Square") || op.primitive.shapeType.equals("Rectangle"))
          {
            if (op.primitive.cornerArc == 0)
            {
              if (op.primitive.fill)
              {
                if (i == 0)
                {
                  // SPECIAL CASE where we fill the whole image buffer with this color, otherwise
                  // we can't get zero transparency w/out using video
                  g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC, op.alphaFactor));
                }
                g2.fill(new java.awt.geom.Rectangle2D.Float(0, 0,
                    op.primitive.shapeWidth, op.primitive.shapeHeight));
              }
              else
                g2.draw(new java.awt.geom.Rectangle2D.Float(thicky/2, thicky/2,
                    op.primitive.shapeWidth - thicky, op.primitive.shapeHeight - thicky));
            }
            else
            {
              op.primitive.cornerArc = Math.min(op.primitive.cornerArc, (int)Math.floor(Math.min(
                  op.primitive.shapeWidth/2, op.primitive.shapeHeight/2)));
              //						if (hqRender)
              //							g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
              if (op.primitive.fill)
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0,
                    op.primitive.shapeWidth, op.primitive.shapeHeight, op.primitive.cornerArc, op.primitive.cornerArc));
              else
                g2.draw(new java.awt.geom.RoundRectangle2D.Float(thicky/2, thicky/2,
                    op.primitive.shapeWidth - thicky, op.primitive.shapeHeight - thicky, op.primitive.cornerArc, op.primitive.cornerArc));
              //						if (hqRender)
              //							g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
            }
          }
          else if (op.primitive.shapeType.equals("Line"))
          {
            g2.draw(new java.awt.geom.Line2D.Float(0, 0, op.primitive.shapeWidth, op.primitive.shapeHeight));
          }
          g2.setPaint(oldPaint);
          g2.setStroke(oldStroke);
        }
        else if (op.isTextOp())
        {
          g2.setTransform((java.awt.geom.AffineTransform)matrixStack.peek());
          /*					try
					{
						MathUtils.transformRectCoords(op.destRect, ((java.awt.geom.AffineTransform) matrixStack.peek()).createInverse(), tempClipRect);
					}
					catch (Exception e)
					{
						System.out.println("ERROR inverting matrix:" + e);;
					}
					tempClipRect.intersect(tempClipRect, clipRect, tempClipRect);*/
          g2.setClip(op.destRect.createIntersection(currEffectClip != null ? (java.awt.geom.Rectangle2D)currEffectClip : (java.awt.geom.Rectangle2D)clipRect));
          /*g2.setClip(Math.max(0, (int)Math.floor(op.destRect.x) - 1), Math.max(0, (int)Math.floor(op.destRect.y) - 1),
						(int)Math.floor(op.destRect.width) + 2, (int)Math.floor(op.destRect.height) + 2);*/
          //					MathUtils.convertToAffineTransform(op.renderXform, tempXform);
          //					g2.transform(tempXform);
          g2.translate(op.copyImageRect.x, op.copyImageRect.y);
          if (op.text.fontImage != null && op.text.renderRectCache != null && op.text.renderImageNumCache.length > 0)
          {
            g2.setComposite(java.awt.AlphaComposite.SrcOver);
            // We have to premultiply the text color since the BufferedImage is using premultiplied alpha as well
            float currAlpha = Math.min(1.0f, op.alphaFactor * currEffectAlpha * op.renderColor.getAlpha() / 255.0f);
            java.awt.Color textColor = new java.awt.Color((int)(op.renderColor.getRed() * currAlpha), (int)(op.renderColor.getGreen() * currAlpha),
                (int)(op.renderColor.getBlue() * currAlpha), (int)(currAlpha*255));
            java.awt.image.RescaleOp colorScaler = new java.awt.image.RescaleOp(
                textColor.getRGBComponents(null), new float[] { 0f, 0f, 0f, 0f }, null);
            for (int k = 0; k < op.text.renderImageNumCache.length; k++)
            {
              if (op.text.renderImageNumCache[k] == -1 || ((int)op.text.renderGlyphRectCache[k].height) <= 0 || ((int)op.text.renderGlyphRectCache[k].width) <= 0)
                continue;
              java.awt.image.BufferedImage img = (java.awt.image.BufferedImage)op.text.fontImage.getJavaImage(op.text.renderImageNumCache[k]);
              java.awt.image.BufferedImage subImage = img.getSubimage((int)op.text.renderGlyphRectCache[k].x,
                  (int)op.text.renderGlyphRectCache[k].y, (int)op.text.renderGlyphRectCache[k].width, (int)op.text.renderGlyphRectCache[k].height);
              java.awt.image.BufferedImage bi2 = colorScaler.filter(subImage, null);
              g2.drawImage(bi2, (int)op.text.renderRectCache[2*k], (int)op.text.renderRectCache[2*k+1],
                  (int)op.text.renderRectCache[2*k] + (int)op.text.renderGlyphRectCache[k].width, (int)op.text.renderRectCache[2*k+1] + (int)op.text.renderGlyphRectCache[k].height, 0, 0,
                  (int)op.text.renderGlyphRectCache[k].width, (int)op.text.renderGlyphRectCache[k].height, null);
              bi2.flush();
              bi2 = null;
              op.text.fontImage.removeJavaRef(op.text.renderImageNumCache[k]);
            }
          }
          else
          {
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, op.alphaFactor));
            g2.setColor(op.renderColor);
            if (uiMgr.shouldAntialiasFont(op.text.font.getSize()))
              g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            else
              g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            // Unneeded, the cilp is set above
            //				g2.setClip(op.srcRect);
            java.awt.geom.Rectangle2D glyphBounds = op.text.glyphVector.getLogicalBounds();
            g2.drawGlyphVector(((MetaFont.JavaGlyphVector)op.text.glyphVector).getJavaGV(), (float)glyphBounds.getX(), (float)-glyphBounds.getY());
          }
        }
        else if (op.isVideoOp()) // if its first then don't clear anything
        {
          g2.setTransform(baseXform);
          // WE MUST clip here or we'll overwrite other parts of the buffer!!
          g2.setClip(clipRect);

          /*
           * We're always using the color key to clear the video area. If we happen to get extra
           * rendering quality because its working and we don't know that, nothing's wrong with that.
           */

          //if (DBG) System.out.println("Clearing video rect for OSD: " + op.destRect);
          // For some reason if this has zero transparency then it doesn't get drawn
          java.awt.Color ckey = vf.getColorKey();
          if (ckey == null || !vf.isColorKeyingEnabled())
            ckey = new java.awt.Color(1, 1, 1, 1);
          g2.setBackground(new java.awt.Color(ckey.getRed(), ckey.getGreen(),
              ckey.getBlue(), 1)/*TRANSPARENT_BLACK*/);
          // Calculate the actual rectangle that the video will cover
          int vw, vh;
          java.awt.Dimension vfVidSize = vf.getVideoSize();
          vw = vfVidSize != null ? vfVidSize.width : 0;
          if (vw <= 0)
            vw = 720;
          vh = vfVidSize != null ? vfVidSize.height : 0;
          if (vh <= 0)
            vh = MMC.getInstance().isNTSCVideoFormat() ? 480 : 576;
          int assMode = vf.getAspectRatioMode();
          float targetX = op.destRect.x;
          float targetY = op.destRect.y;
          float targetW = op.destRect.width;
          float targetH = op.destRect.height;
          float forcedRatio = vf.getCurrentAspectRatio();
          if (forcedRatio != 0)
          {
            if (targetW/targetH < forcedRatio)
            {
              float shrink = targetH - targetW/forcedRatio;
              targetH -= shrink;
              targetY += shrink/2;
            }
            else
            {
              float shrink = targetW - targetH*forcedRatio;
              targetW -= shrink;
              targetX += shrink/2;
            }
          }
          float zoomX = vf.getVideoZoomX(assMode);
          float zoomY = vf.getVideoZoomY(assMode);
          float transX = vf.getVideoOffsetX(assMode) * targetW / master.getWidth();
          float transY = vf.getVideoOffsetY(assMode) * targetH / master.getHeight();

          float widthAdjust = (zoomX - 1.0f)*targetW;
          float heightAdjust = (zoomY - 1.0f)*targetH;
          targetX -= widthAdjust/2;
          targetY -= heightAdjust/2;
          targetW += widthAdjust;
          targetH += heightAdjust;

          targetX += transX;
          targetY += transY;

          long videoHShiftFreq =  vf.getVideoHShiftFreq();
          if (videoHShiftFreq != 0)
          {
            float maxHShift = (op.destRect.width - targetW)/2;
            long timeDiff = Sage.eventTime();
            timeDiff %= videoHShiftFreq;
            if (timeDiff < videoHShiftFreq/2)
            {
              if (timeDiff < videoHShiftFreq/4)
                targetX -= maxHShift*timeDiff*4/videoHShiftFreq;
              else
                targetX -= maxHShift - (maxHShift*(timeDiff - videoHShiftFreq/4)*4/videoHShiftFreq);
            }
            else
            {
              timeDiff -= videoHShiftFreq/2;
              if (timeDiff < videoHShiftFreq/4)
                targetX += maxHShift*timeDiff*4/videoHShiftFreq;
              else
                targetX += maxHShift - (maxHShift*(timeDiff - videoHShiftFreq/4)*4/videoHShiftFreq);
            }
          }

          java.awt.geom.Rectangle2D.Float videoSrc = new java.awt.geom.Rectangle2D.Float(0, 0, vw, vh);
          java.awt.geom.Rectangle2D.Float videoDest = new java.awt.geom.Rectangle2D.Float(targetX, targetY, targetW, targetH);
          Sage.clipSrcDestRects(op.destRect, videoSrc, videoDest);
          java.awt.Rectangle usedVideoRect = new java.awt.Rectangle();
          java.awt.Rectangle fullVideoRect = new java.awt.Rectangle();
          usedVideoRect.setFrame(videoDest);
          fullVideoRect.setFrame(op.destRect);

          // Clear the video rect area
          g2.clearRect(fullVideoRect.x, fullVideoRect.y, fullVideoRect.width, fullVideoRect.height);

          g2.setBackground(vf.getVideoBGColor());
          // Need to clear the left edge of the video region
          if (usedVideoRect.x > fullVideoRect.x)
          {
            g2.clearRect(fullVideoRect.x, fullVideoRect.y, usedVideoRect.x - fullVideoRect.x,
                fullVideoRect.height);
          }
          // Need to clear the top edge of the video region
          if (usedVideoRect.y > fullVideoRect.y)
          {
            g2.clearRect(fullVideoRect.x, fullVideoRect.y, fullVideoRect.width,
                usedVideoRect.y - fullVideoRect.y);
          }
          // Need to clear the right edge of the video region
          if (usedVideoRect.x + usedVideoRect.width < fullVideoRect.x + fullVideoRect.width)
          {
            int adjust = (fullVideoRect.x + fullVideoRect.width) - (usedVideoRect.x + usedVideoRect.width);
            g2.clearRect(fullVideoRect.x + fullVideoRect.width - adjust, fullVideoRect.y, adjust,
                fullVideoRect.height);
          }
          // Need to clear the bottom edge of the video region
          if (usedVideoRect.y + usedVideoRect.height < fullVideoRect.y + fullVideoRect.height)
          {
            int adjust = (fullVideoRect.y + fullVideoRect.height) - (usedVideoRect.y + usedVideoRect.height);
            g2.clearRect(fullVideoRect.x, fullVideoRect.y + fullVideoRect.height - adjust, fullVideoRect.width,
                adjust);
          }

          usedVideoOp = true;
          if (op.destRect.width == master.getRoot().getWidth() && op.destRect.height == master.getRoot().getHeight())
          {
            vf.setVideoBounds(null);
            refreshVideo = true;
            if (!vf.isColorKeyingEnabled() && vf.isNonBlendableVideoPlayerLoaded() && Sage.WINDOWS_OS)
            {
              if (lastVideoRect == null || !SageRenderer.floatRectEquals(op.destRect, lastVideoRect))
                uiMgr.repaintNextRegionChange = true;
              java.util.ArrayList convertedRegions = getCoveredRegions(currDisplayList, op);
              java.awt.Rectangle[] fixedRects = new java.awt.Rectangle[convertedRegions.size()];
              int[] rectRounds = new int[convertedRegions.size()];
              for (int j = 0; j < convertedRegions.size(); j++)
              {
                java.awt.geom.RoundRectangle2D.Float foofer = (java.awt.geom.RoundRectangle2D.Float)
                    convertedRegions.get(j);
                fixedRects[j] = foofer.getBounds();
                rectRounds[j] = Math.round(foofer.arcwidth);
              }
              uiMgr.setCompoundWindowRegion2(master.getHWND(),
                  fixedRects, rectRounds);
              setVideoRegion = true;
            }
          }
          else
          {
            vf.setVideoBounds(op.destRect);
            refreshVideo = true;
            if (!vf.isColorKeyingEnabled() && vf.isNonBlendableVideoPlayerLoaded() && Sage.WINDOWS_OS)
            {
              if (lastVideoRect == null || !SageRenderer.floatRectEquals(op.destRect, lastVideoRect))
                uiMgr.repaintNextRegionChange = true;
              java.util.ArrayList convertedRegions = getCoveredRegions(currDisplayList, op);
              java.awt.Rectangle[] fixedRects = new java.awt.Rectangle[convertedRegions.size() +
                                                                       op.legacyRegions.size()];
              int[] rectRounds = new int[convertedRegions.size() + op.legacyRegions.size()];
              for (int j = 0; j < op.legacyRegions.size(); j++)
              {
                java.awt.Rectangle r = (java.awt.Rectangle) op.legacyRegions.get(j);
                fixedRects[j] = r;
                rectRounds[j] = 0;
              }
              for (int j = 0; j < convertedRegions.size(); j++)
              {
                java.awt.geom.RoundRectangle2D.Float foofer = (java.awt.geom.RoundRectangle2D.Float)
                    convertedRegions.get(j);
                fixedRects[j + op.legacyRegions.size()] = foofer.getBounds();
                rectRounds[j + op.legacyRegions.size()] = Math.round(foofer.arcwidth);
              }
              uiMgr.setCompoundWindowRegion2(master.getHWND(), fixedRects, rectRounds);
              setVideoRegion = true;
            }
          }
          lastVideoRect = op.destRect;
        }
        else if (op.isSurfaceOp() && uiMgr.areLayersEnabled())
        {
          if (ANIM_DEBUG) System.out.println("Surface Op surf=" + op.surface + " on=" + op.isSurfaceOpOn() + " " + op);
          if (op.isSurfaceOpOn())
          {
            if (currSurface != primary)
            {
              surfStack.push(currSurface);
              g2Stack.push(g2);
            }
            else
              primaryG2 = g2;
            currSurface = (java.awt.image.BufferedImage) surfaceCache.get(op.surface);
            int primWidth = (primary == null) ? master.getWidth() : primary.getWidth();
            int primHeight = (primary == null) ? master.getHeight() : primary.getHeight();
            if (currSurface != null)
            {
              // Check to make sure it's big enough
              if (currSurface.getWidth() < primWidth || currSurface.getHeight() < primHeight)
              {
                currSurface.flush();
                currSurface  = null;
              }
            }
            if (currSurface == null)
            {
              currSurface = new java.awt.image.BufferedImage(primWidth, primHeight,
                  java.awt.image.BufferedImage.TYPE_INT_ARGB);
              surfaceCache.put(op.surface, currSurface);
              numSurfsCreated++;
            }
            if (ANIM_DEBUG) System.out.println("Switched rendering surface to " + op.surface + " " + currSurface);
            g2 = currSurface.createGraphics();
            g2.setTransform(baseXform);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ALPHA_INTERPOLATION, java.awt.RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            // Don't clear the area if this surface was already used
            if (clearedSurfs.add(currSurface))
            {
              //							g2.setClip(op.destRect.createIntersection(clipRect));
              g2.setBackground(new java.awt.Color(0, 0, 0, 0));
              //							g2.clearRect(Math.round(op.destRect.x), Math.round(op.destRect.y), Math.round(op.destRect.width), Math.round(op.destRect.height));
              g2.clearRect(clipRect.x, clipRect.y, clipRect.width, clipRect.height);
            }
          }
          else
          {
            g2.dispose();
            if (surfStack.isEmpty())
              g2 = primaryG2;
            else
              g2 = (java.awt.Graphics2D) g2Stack.pop();
            // Avoid double compositing operations from nested surface usage
            if (!surfStack.contains(currSurface))
            {
              compositeOps.add(op);
              java.util.ArrayList remnantAnims = (java.util.ArrayList) animsThatGoAfterSurfPop.remove(currSurface);
              if (remnantAnims != null)
              {
                if (ANIM_DEBUG) System.out.println("Adding animation ops into composite list now from prior nested surfs:" + remnantAnims);
                compositeOps.addAll(remnantAnims);
              }
            }
            if (surfStack.isEmpty())
              currSurface = primary;
            else
              currSurface = (java.awt.image.BufferedImage) surfStack.pop();
          }
        }
        else if (op.isAnimationOp() && uiMgr.areLayersEnabled())
        {
          processAnimOp(op, i, clipRect);
          if (currSurface != null && (currSurface.equals(surfaceCache.get(op.surface)) || surfStack.contains(surfaceCache.get(op.surface))))
          {
            if (ANIM_DEBUG) System.out.println("Putting animation op in surf pop map because we're nested in the current surface");
            java.util.ArrayList vecy = (java.util.ArrayList) animsThatGoAfterSurfPop.get(currSurface);
            if (vecy == null)
              animsThatGoAfterSurfPop.put(currSurface, vecy = new java.util.ArrayList());
            vecy.add(compositeOps.remove(compositeOps.size() - 1));
          }
        }
      }

      if (uiMgr.areLayersEnabled())
      {
        java.util.Collections.sort(compositeOps, COMPOSITE_LIST_SORTER);

        fixSurfacePostCompositeRegions();
      }
    }
    else
    {
      if (ANIM_DEBUG) System.out.println("OPTIMIZATION Skip DL render & composite only! dlSize=" + currDisplayList.size() +
          " optSize=" + compositeOps.size());
    }

    // Go back through and composite all of the surfaces that were there (if any)
    // Find all the names of the surface and sort those and then render the surfaces in order (which may not
    // match the order they appeared in the display list)
    dlThatWasComposited = currDisplayList;
    if (ANIM_DEBUG) System.out.println("Performing the surface compositing operations now");
    for (int i = 0; i <= compositeOps.size(); i++)
    {
      RenderingOp op;
      if (i == compositeOps.size())
      {
        if (waitIndicatorState && waitIndicatorRops != null)
        {
          op = (RenderingOp) waitIndicatorRops.get((++waitIndicatorRopsIndex) % waitIndicatorRops.size());
        }
        else
          break;
      }
      else
        op = (RenderingOp) compositeOps.get(i);
      if (op.isSurfaceOp())
      {
        if (ANIM_DEBUG) System.out.println("Surface Op surf=" + op.surface + " on=" + op.isSurfaceOpOn() + " " + op);
        if (op.isSurfaceOpOff())
        {
          currSurface = (java.awt.image.BufferedImage) surfaceCache.get(op.surface);
          g2.setTransform(baseXform);
          g2.setClip(op.destRect.createIntersection(clipRect));
          if (isBackgroundSurface(op.surface))
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC));
          else
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, op.alphaFactor));
          g2.drawImage(currSurface, Math.round(op.destRect.x), Math.round(op.destRect.y), Math.round(op.destRect.x + op.destRect.width),
              Math.round(op.destRect.y + op.destRect.height), Math.round(op.destRect.x), Math.round(op.destRect.y),
              Math.round(op.destRect.width + op.destRect.x), Math.round(op.destRect.height + op.destRect.y), null);
          if (ANIM_DEBUG) System.out.println("Finished cached surface rendering and re-composited it with the main surface");
        }
      }
      else if (op.isImageOp())
      {
        g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, op.alphaFactor));
        //				java.awt.geom.AffineTransform currXform = op.renderXform;
        //				myXform.setToTranslation(op.copyImageRect.x, op.copyImageRect.y);
        // Workaround for JDK Bug# 4916948 to get floating point translation
        // BUT it causes a big performance hit because it reallocates everything it draws so don't do it
        //at.shear(0.0000001, 0.0000001);
        g2.setTransform(baseXform);
        g2.setClip(op.destRect.createIntersection(clipRect));
        g2.drawImage(op.texture.getJavaImage(op.textureIndex), Math.round(op.destRect.x), Math.round(op.destRect.y),
            Math.round(op.destRect.x + op.destRect.width), Math.round(op.destRect.y + op.destRect.height),
            Math.round(op.srcRect.x), Math.round(op.srcRect.y), Math.round(op.srcRect.x + op.srcRect.width),
            Math.round(op.srcRect.y + op.srcRect.height), null);
        op.texture.removeJavaRef(op.textureIndex);
      }
      else if (op.isAnimationOp())
      {
        RenderingOp.Animation anime = op.anime;
        if (ANIM_DEBUG) System.out.println("Animation operation found! ANIMAIL ANIMAIL!!! " + op + " scrollSrcRect=" + anime.altSrcRect +
            " scrollDstRect=" + anime.altDestRect);
        // Find the cached surface first
        java.awt.image.BufferedImage cachedSurface = (java.awt.image.BufferedImage) surfaceCache.get(op.surface);
        if (cachedSurface != null)
        {
          if (ANIM_DEBUG) System.out.println("Cached animation surface found: " + op.surface);
          if (ANIM_DEBUG) System.out.println("Rendering Animation " + anime.animation);
          g2.setTransform(baseXform);
          g2.setClip(op.destRect.createIntersection(clipRect));
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, op.alphaFactor));
          g2.drawImage(cachedSurface, Math.round(op.destRect.x), Math.round(op.destRect.y), Math.round(op.destRect.x + op.destRect.width),
              Math.round(op.destRect.y + op.destRect.height), Math.round(op.srcRect.x), Math.round(op.srcRect.y),
              Math.round(op.srcRect.width + op.srcRect.x), Math.round(op.srcRect.height + op.srcRect.y), null);
          if (anime.isDualSurfaceOp())
          {
            // We need to render the other scrolling position
            cachedSurface = (java.awt.image.BufferedImage) surfaceCache.get(op.anime.altSurfName);
            if (cachedSurface != null)
            {
              if (ANIM_DEBUG) System.out.println("Rendering second scroll surface scrollSrcRect=" + anime.altSrcRect +
                  " scrollDstRect=" + anime.altDestRect);
              g2.setClip(op.anime.altDestRect.createIntersection(clipRect));
              g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, op.anime.altAlphaFactor));
              g2.drawImage(cachedSurface, Math.round(anime.altDestRect.x), Math.round(anime.altDestRect.y),
                  Math.round(anime.altDestRect.x + anime.altDestRect.width),
                  Math.round(anime.altDestRect.y + anime.altDestRect.height),
                  Math.round(anime.altSrcRect.x), Math.round(anime.altSrcRect.y),
                  Math.round(anime.altSrcRect.width + anime.altSrcRect.x),
                  Math.round(anime.altSrcRect.height + anime.altSrcRect.y), null);
            }
          }
        }
        else
        {
          if (ANIM_DEBUG) System.out.println("ERROR: Could not find cached animation surface:" + op.surface);
        }
        if (!anime.expired)
          master.setActiveAnimation(op);
      }
    }
    if (!usedVideoOp)
      lastVideoRect = null;
    if (!setVideoRegion && Sage.WINDOWS_OS)
      uiMgr.clearWindowRegion2(master.getHWND());
    g2.dispose();
    if (effectAnimationsActive)
      master.effectsNeedProcessing(effectAnimationsLocked);
    if (ANIM_DEBUG) System.out.println("Done executing display list cacheSize=" + surfaceCache.size() + " numSurfsCreated=" + numSurfsCreated +
        " dlSize=" + currDisplayList.size() + " compSize=" + compositeOps.size());
    return true;
  }

  public void present(java.awt.Rectangle clipRect)
  {
    if (primary != null)
      clipRect = clipRect.intersection(new java.awt.Rectangle(0, 0, primary.getWidth(), primary.getHeight()));
    //if (DBG) System.out.println("Java2DSageRenderer present called clip=" + clipRect);
    if ((!uiMgr.getBoolean("ui/disable_2d_double_buffering", false) || hasOSDRenderer()) &&
        master.isShowing() &&
        !uiMgr.getBoolean("disable_desktop_ui_rendering", false) &&
        primary != null) // minimizing the window will stop the desktop rendering but keep the osd alive
    {
      java.awt.Graphics2D myG = (java.awt.Graphics2D) master.getGraphics();
      myG.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC));
      if (clipRect != null)
        myG.setClip(clipRect);
      myG.drawImage(primary, 0, 0, null);
      myG.dispose();
      master.getToolkit().sync();
      /*try
{
	javax.imageio.ImageIO.write(primary, "png", new java.io.File("screen" + (frameNum++) + ".png"));
}
catch (java.io.IOException e)
{
	System.out.println("ERROR with Frame PNG Compression:" + e);
}*/
    }

    long desiredTime = ZRoot.FRAME_TIME;
    // Reload the OSD if we've come out of standby
    if (osdRenderer != null && uiMgr.getLastDeepSleepAwakeTime() > osdBuildTime)
    {
      osdRenderer.closeOSD();
      osdRenderer = null;
      ignoreOSD = false;
    }
    if (hasOSDRenderer() && !ignoreOSD)
    {
      if (osdRenderer == null)
      {
        osdRenderer = createOSDRenderer();
        osdBuildTime = Sage.eventTime();
        if (osdRenderer == null)
          ignoreOSD = true;
        else
				{
          if (osdRenderer instanceof OSDRenderingPlugin2)
						ignoreOSD = !((OSDRenderingPlugin2) osdRenderer).openOSD2(primary.getWidth(), primary.getHeight());
					else
            ignoreOSD = !osdRenderer.openOSD();
        }
			}
      if (!ignoreOSD)
      {
        if (MMC.getInstance().isNTSCVideoFormat())
          osdVideoHeight = uiMgr.getInt("osd_rendering_height_ntsc", 480);
        else
          osdVideoHeight = uiMgr.getInt("osd_rendering_height_pal", 576);
        //if (DBG) System.out.println("ZRoot OSD rendering from " + primary +
        //	" w=" + primary.getWidth() + " h=" + primary.getHeight());
        // Get the subrect if its less than half of our memory size
        java.awt.Rectangle currVideoRect = null;
        if (lastVideoRect != null)
        {
          int assMode = vf.getAspectRatioMode();
          float zoomX = vf.getVideoZoomX(assMode);
          float zoomY = vf.getVideoZoomY(assMode);
          int vw = uiMgr.getInt("osd_rendering_width", 720);
          int vh = osdVideoHeight;
          float targetX = lastVideoRect.x;
          float targetY = lastVideoRect.y;
          float targetW = lastVideoRect.width;
          float targetH = lastVideoRect.height;
          float forcedRatio = 0;
          switch (assMode)
          {
            case BasicVideoFrame.ASPECT_16X9:
              forcedRatio = 16.0f/9.0f;
              break;
            case BasicVideoFrame.ASPECT_4X3:
              forcedRatio = 4.0f/3.0f;
              break;
            case BasicVideoFrame.ASPECT_SOURCE:
              forcedRatio = ((float)vw)/vh;
              break;
          }
          if (forcedRatio != 0)
          {
            if (targetW/targetH < forcedRatio)
            {
              float shrink = targetH - targetW/forcedRatio;
              targetH -= shrink;
              targetY += shrink/2;
            }
            else
            {
              float shrink = targetW - targetH*forcedRatio;
              targetW -= shrink;
              targetX += shrink/2;
            }
          }
          float widthAdjust = (zoomX - 1.0f)*targetW;
          float heightAdjust = (zoomY - 1.0f)*targetH;
          float transX = vf.getVideoOffsetX(assMode) * targetW / vw;
          float transY = vf.getVideoOffsetY(assMode) * targetH / vh;
          targetX -= widthAdjust/2;
          targetY -= heightAdjust/2;
          targetW += widthAdjust;
          targetH += heightAdjust;

          targetX += transX;
          targetY += transY;
          currVideoRect = new java.awt.Rectangle(Math.max(0, Math.round(targetX)),
              Math.max(0, Math.round(targetY)), Math.min(vw, Math.round(targetW)),
              Math.min(vh, Math.round(targetH)));
        }

        boolean renderOSDRes;
				if (osdRenderer instanceof OSDRenderingPlugin2)
				{
					renderOSDRes = ((OSDRenderingPlugin2)osdRenderer).updateOSD2(primary, clipRect, currVideoRect);
				}
				else if (clipRect.width*clipRect.height <= primary.getWidth()*primary.getHeight()/2 &&
            osdRenderer.supportsRegionUpdates())
        {
          //if (DBG) System.out.println("Getting subimage to optimize 350 OSD blitting to " + clipRect);
          if (osdMemBuf == null)
            osdMemBuf = new int[primary.getWidth()*primary.getHeight()/2];
          primary.getRGB(clipRect.x, clipRect.y, clipRect.width,
              clipRect.height, osdMemBuf, 0, clipRect.width);
          renderOSDRes = osdRenderer.updateOSD(osdMemBuf, clipRect.width, clipRect.height, clipRect,
              currVideoRect);
        }
        else
        {
          renderOSDRes = osdRenderer.updateOSD(
              ((java.awt.image.DataBufferInt)primary.getRaster().getDataBuffer()).getData(),
              primary.getWidth(), primary.getHeight(), new java.awt.Rectangle(0, 0, primary.getWidth(),
                  primary.getHeight()), currVideoRect);
        }
        //if (DBG) System.out.println("Done with 350 OSD rendering");
        if (!renderOSDRes && Sage.eventTime() - last350ResetTime > 15000)
        {
          if (Sage.DBG) System.out.println("Performing 350 OSD reset...");
          // Failure with the rendering, if we haven't reset it in the past 2 minutes, then do it again.
          last350ResetTime = Sage.eventTime();
          osdRenderer.closeOSD();
          if (vf.hasFile())
          {
            vf.reloadFile();
            try{Thread.sleep(1000);}catch (Exception e){}
          }
          osdRenderer = createOSDRenderer();
          osdBuildTime = Sage.eventTime();
          if (osdRenderer != null)
          {
						if (osdRenderer instanceof OSDRenderingPlugin2)
							ignoreOSD = !((OSDRenderingPlugin2) osdRenderer).openOSD2(primary.getWidth(), primary.getHeight());
						else
              ignoreOSD = !osdRenderer.openOSD();
          }
        }
      }
    }
    else if (osdRenderer != null)
    {
      osdRenderer.closeOSD();
      osdRenderer = null;
      ignoreOSD = false;
    }

    if (refreshVideo)
      vf.refreshVideoSizing();

    markAnimationStartTimes();

    // Don't let the frame rate get out of control
    long currTime = Sage.eventTime();
    long frameTime = currTime - lastPresentTime;
    // This fixes a bug where the UI hangs and then repeats a bunch of events
    frameTime = Math.max(0, frameTime);
    if (frameTime < desiredTime)
    {
      try{Thread.sleep(desiredTime - frameTime); }catch(Exception e){}
    }
    else
    {
      // So we don't max out the CPU with our rendering because we're highest priority thread
      try{Thread.sleep(5); }catch(Exception e){}
    }
    lastPresentTime = currTime;
  }

  public void cleanupRenderer()
  {
    if (primary != null)
    {
      primary.flush();
      primary = null;
    }
    if (osdRenderer != null)
    {
      osdRenderer.closeOSD();
      osdRenderer = null;
    }
    super.cleanupRenderer();
  }

  private static final String ENABLE_PVR350_OSD = "enable_pvr350_osd";
  public boolean hasOSDRenderer()
  {
    return hasOSDRenderer(uiMgr);
  }
  public static boolean hasOSDRenderer(UIManager uiMgr)
  {
    // Check for the old property setting
    String oldPref = uiMgr.get("videoframe/" + ENABLE_PVR350_OSD, null);
    if (oldPref != null)
    {
      uiMgr.put("videoframe/" + ENABLE_PVR350_OSD, null);
      if (Boolean.valueOf(oldPref).booleanValue())
        uiMgr.put(OSD_RENDERING_PLUGIN_CLASS, "sage.PVR350OSDRenderingPlugin");
    }
    return uiMgr.get(OSD_RENDERING_PLUGIN_CLASS, "").length() > 0;
  }

  private OSDRenderingPlugin createOSDRenderer()
  {
    String osdPluginClass = uiMgr.get(OSD_RENDERING_PLUGIN_CLASS, "");
    if (osdPluginClass.length() > 0)
    {
      try
      {
        Class osdClass = Class.forName(osdPluginClass);
        OSDRenderingPlugin rv = (OSDRenderingPlugin) osdClass.newInstance();
        return rv;
      }
      catch (Throwable e)
      {
        System.out.println("ERROR creating the OSD rendering plugin:" + e);
        e.printStackTrace();
      }
    }
    return null;
  }

  public boolean supportsPartialUIUpdates()
  {
    return true;
  }

  public boolean hasHWImageScaling()
  {
    return false;
  }

  protected java.awt.image.BufferedImage primary;
  protected java.awt.image.BufferedImage secondary;
  private VideoFrame vf;
  private boolean ignoreOSD;
  private int[] osdMemBuf;

  private java.awt.geom.Rectangle2D.Float lastVideoRect;
  private long last350ResetTime;
  private int osdVideoHeight;

  private OSDRenderingPlugin osdRenderer;
  private long osdBuildTime;

  private boolean refreshVideo;
  private boolean enable2DScaleCache;

  private java.util.Stack surfStack = new java.util.Stack();
  private java.util.Stack g2Stack = new java.util.Stack();
  private java.awt.image.BufferedImage currSurface;
  private java.awt.Graphics2D primaryG2;

  private java.util.ArrayList dlThatWasComposited;
  private int numSurfsCreated = 0;
  private java.util.Map animsThatGoAfterSurfPop = new java.util.HashMap();
}
