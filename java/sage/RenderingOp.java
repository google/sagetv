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

public class RenderingOp implements Cloneable
{
  public static final int RENDER_FLAG_NONOVERLAPPED_COPY = 0x01;
  public RenderingOp(MetaImage im, int imIdx, int dc, float af,
      java.awt.geom.Rectangle2D.Float cr,
      float translateX, float translateY, float destW, float destH)
  {
    texture = im;
    textureIndex = imIdx;
    renderColor = (dc == 0xFFFFFF) ? null : new java.awt.Color(dc);
    alphaFactor = af;
    srcRect = new java.awt.geom.Rectangle2D.Float(0, 0, im.getWidth(imIdx), im.getHeight(imIdx));
    destRect = new java.awt.geom.Rectangle2D.Float(translateX, translateY, destW, destH);
    copyImageRect = new java.awt.geom.Rectangle2D.Float(translateX, translateY, destW, destH);
    Sage.clipSrcDestRects(cr, srcRect, destRect);
  }

  public RenderingOp(MetaImage im, int imIdx, MetaImage diffusor, int dc, float af,
      java.awt.geom.Rectangle2D.Float cr,	java.awt.geom.Rectangle2D.Float scaleDiffuseRect,
      float translateX, float translateY, float destW, float destH)
  {
    texture = im;
    diffuseTexture = diffusor;
    textureIndex = imIdx;
    renderColor = (dc == 0xFFFFFF) ? null : new java.awt.Color(dc);
    alphaFactor = af;
    srcRect = new java.awt.geom.Rectangle2D.Float(0, 0, im.getWidth(imIdx), im.getHeight(imIdx));
    copyImageRect = new java.awt.geom.Rectangle2D.Float(translateX, translateY, destW, destH);
    destRect = new java.awt.geom.Rectangle2D.Float(translateX, translateY, destW, destH);
    Sage.clipSrcDestRects(cr, srcRect, destRect);
    if (diffuseTexture != null)
    {
      if (scaleDiffuseRect == null)
      {
        // scaleDiffuse is true; which means we scale to match the image; so match its source area
        diffuseSrcRect = new java.awt.geom.Rectangle2D.Float((srcRect.x/im.getWidth(imIdx))*diffusor.getWidth(),
            (srcRect.y/im.getHeight(imIdx))*diffusor.getHeight(), (srcRect.width/im.getWidth(imIdx))*diffusor.getWidth(),
            (srcRect.height/im.getHeight(imIdx))*diffusor.getHeight());
      }
      else
      {
        // scaleDiffuse is false; which means we scale to match the area of the destination image which is rendered to
        diffuseSrcRect = new java.awt.geom.Rectangle2D.Float(diffusor.getWidth() * (destRect.x - scaleDiffuseRect.x)/scaleDiffuseRect.width,
            diffusor.getHeight() * (destRect.y - scaleDiffuseRect.y)/scaleDiffuseRect.height,
            diffusor.getWidth() * destRect.width/scaleDiffuseRect.width,
            diffusor.getHeight() * destRect.height/scaleDiffuseRect.height);
      }
    }
  }

  public RenderingOp(MetaImage im, int imIdx, MetaImage diffusor, int dc, float af,
      java.awt.geom.Rectangle2D.Float cr,	java.awt.geom.Rectangle2D.Float scaleDiffuseRect,
      float srcX, float srcY, float srcW, float srcH,
      float translateX, float translateY, float destW, float destH)
  {
    texture = im;
    diffuseTexture = diffusor;
    textureIndex = imIdx;
    renderColor = (dc == 0xFFFFFF) ? null : new java.awt.Color(dc);
    alphaFactor = af;
    srcRect = new java.awt.geom.Rectangle2D.Float(srcX, srcY, srcW, srcH);
    copyImageRect = new java.awt.geom.Rectangle2D.Float(translateX, translateY, destW, destH);
    destRect = new java.awt.geom.Rectangle2D.Float(translateX, translateY, destW, destH);
    Sage.clipSrcDestRects(cr, srcRect, destRect);
    if (diffuseTexture != null)
    {
      if (scaleDiffuseRect == null)
      {
        // scaleDiffuse is true; which means we scale to match the image; so match its source area
        diffuseSrcRect = new java.awt.geom.Rectangle2D.Float((srcRect.x/im.getWidth(imIdx))*diffusor.getWidth(),
            (srcRect.y/im.getHeight(imIdx))*diffusor.getHeight(), (srcRect.width/im.getWidth(imIdx))*diffusor.getWidth(),
            (srcRect.height/im.getHeight(imIdx))*diffusor.getHeight());
      }
      else
      {
        // scaleDiffuse is false; which means we scale to match the area of the destination image which is rendered to
        diffuseSrcRect = new java.awt.geom.Rectangle2D.Float(diffusor.getWidth() * (destRect.x - scaleDiffuseRect.x)/scaleDiffuseRect.width,
            diffusor.getHeight() * (destRect.y - scaleDiffuseRect.y)/scaleDiffuseRect.height,
            diffusor.getWidth() * destRect.width/scaleDiffuseRect.width,
            diffusor.getHeight() * destRect.height/scaleDiffuseRect.height);
      }
    }
  }

  public RenderingOp(SageRenderer.ShapeDescription p,
      float af, java.awt.geom.Rectangle2D.Float cr,
      float translateX, float translateY)
  {
    primitive = p;
    alphaFactor = af;
    srcRect = new java.awt.geom.Rectangle2D.Float(0, 0, p.shapeWidth, p.shapeHeight);
    destRect = new java.awt.geom.Rectangle2D.Float(translateX, translateY, p.shapeWidth, p.shapeHeight);
    Sage.clipSrcDestRects(cr, srcRect, destRect);
    copyImageRect = new java.awt.geom.Rectangle2D.Float(translateX, translateY, p.shapeWidth, p.shapeHeight);
  }

  public RenderingOp(SageRenderer.TextDescription t,
      java.awt.Color color, float af, java.awt.geom.Rectangle2D.Float cr,
      float translateX, float translateY)
  {
    text = t;
    renderColor = color;
    alphaFactor = af;
    srcRect = new java.awt.geom.Rectangle2D.Float(0, 0, t.glyphVector.getVisibleAdvance(), t.glyphVector.getHeight());
    copyImageRect = new java.awt.geom.Rectangle2D.Float(translateX, translateY, srcRect.width, srcRect.height);
    destRect = new java.awt.geom.Rectangle2D.Float(translateX, translateY, srcRect.width, srcRect.height);
    Sage.clipSrcDestRects(cr, srcRect, destRect);
  }

  // there's no clipping or compositing for video
  public RenderingOp(java.awt.geom.Rectangle2D.Float dr, java.util.ArrayList regionOps)
  {
    video = true;
    destRect = new java.awt.geom.Rectangle2D.Float();
    destRect.setFrame(dr);
    srcRect = new java.awt.geom.Rectangle2D.Float();
    srcRect.setFrame(destRect);
    legacyRegions = regionOps;
    alphaFactor = 1.0f;
  }

  // Surface op
  public RenderingOp(String surfaceSwitch, java.awt.geom.Rectangle2D.Float surfaceArea, boolean surfOn)
  {
    surface = surfaceSwitch;
    textureIndex = surfOn ? 1 : 0;
    srcRect = destRect = surfaceArea;
    alphaFactor = 1.0f; // prevents culling of the op & all alpha blending in surfaces is handled by the individual components that make up a surface
  }

  public RenderingOp(String surfaceName, String animationName, long duration, long delay, java.awt.geom.Rectangle2D.Float surfaceArea,
      float srcAlpha, boolean interruptable)
  {
    anime = new Animation(animationName, duration, delay);
    anime.interruptable = interruptable;
    surface = surfaceName;
    // all alpha blending outside of fade animations is handled by the individual components that make up a surface
    alphaFactor = 1.0f;//srcAlpha; // we need to obey this alpha!! //1.0f; // prevents culling of the op
    destRect = new java.awt.geom.Rectangle2D.Float();
    destRect.setFrame(surfaceArea);
    srcRect = new java.awt.geom.Rectangle2D.Float();
    srcRect.setFrame(surfaceArea);
  }

  public RenderingOp(EffectTracker effector)
  {
    effect = true;
    effectTracker = effector;
  }

  public RenderingOp(EffectTracker effector, java.awt.geom.Rectangle2D.Float effectClip, float trueOffsetX, float trueOffsetY)
  {
    effect = true;
    effectTracker = effector;
    if (effectClip != null)
    {
      destRect = new java.awt.geom.Rectangle2D.Float();
      destRect.setFrame(effectClip);
    }
    if (trueOffsetX != 0 || trueOffsetY != 0)
    {
      srcRect = new java.awt.geom.Rectangle2D.Float(trueOffsetX, trueOffsetY, 0, 0);
    }
  }

  public boolean isImageOp() { return texture != null; }
  public boolean isPrimitiveOp() { return primitive != null; }
  public boolean isVideoOp() { return video; }
  public boolean isTextOp() { return text != null; }
  public boolean isSurfaceOp() { return surface != null && anime == null; }
  public boolean isAnimationOp() { return anime != null; }
  public boolean isEffectOp() { return effect; }

  public boolean isSurfaceOpOn() { return isSurfaceOp() && textureIndex == 1; }
  public boolean isSurfaceOpOff() { return isSurfaceOp() && textureIndex == 0; }

  public String toString()
  {
    return "RenderingOp[image=" + texture + " effect=" + effect + " imIdx=" + textureIndex + " alpha=" + alphaFactor +
        " src=" + srcRect + " dest=" + destRect + " cir=" + copyImageRect + " prim=" + primitive +
        " video=" + video + " anime=" + anime + " surf=" + surface +
        (privateData != null ? (" privateData=" + privateData.toString()) : "") + "]";
  }

  protected void scaleSrc(float xScale, float yScale)
  {
    srcRect.x *= xScale;
    srcRect.width *= xScale;
    srcRect.y *= yScale;
    srcRect.height *= yScale;
  }

  public Object clone()
  {
    try
    {
      return super.clone();
    }
    catch (CloneNotSupportedException e)
    {
      throw new InternalError();
    }
  }

  public java.awt.geom.Rectangle2D.Float getDestination() {
    java.awt.geom.Rectangle2D.Float rect = new java.awt.geom.Rectangle2D.Float();
    rect.setRect(destRect);
    return rect;
  }

  public java.awt.Rectangle getAnimationArea(int uiWidth, int uiHeight)
  {
    return anime.getAnimationArea(uiWidth, uiHeight);
  }

  public void cloneAnimation(Animation otherAnim)
  {
    anime = new Animation(otherAnim.animation, otherAnim.duration, otherAnim.delay);
    anime.interruptable = otherAnim.interruptable;
    anime.setup(destRect);//otherAnim.orgDestRect);
  }

  public float alphaFactor;
  public java.awt.geom.Rectangle2D.Float srcRect;
  public java.awt.geom.Rectangle2D.Float destRect;
  public java.awt.geom.Rectangle2D.Float copyImageRect; // unclipped dest rect for image copies
  public MetaImage texture;
  public int textureIndex;
  public MetaImage diffuseTexture;
  public java.awt.geom.Rectangle2D.Float diffuseSrcRect;
  public SageRenderer.ShapeDescription primitive;
  public boolean video;
  public SageRenderer.TextDescription text;
  public java.util.ArrayList legacyRegions;
  public java.awt.Color renderColor;
  public Object privateData;
  //	public javax.vecmath.Matrix4f renderXform;
  public Animation anime;
  public String surface;
  public int opFlags; // used for optimizations in the rendering engines
  public boolean effect;
  public EffectTracker effectTracker;

  public static int getAnimTypeForName(String animation)
  {
    if (animation.startsWith("Slide"))
    {
      if (animation.startsWith(Animation.SLIDE_DOWN_OUT_STR))
        return Animation.SLIDE_DOWN_OUT;
      else if (animation.startsWith(Animation.SLIDE_UP_OUT_STR))
        return Animation.SLIDE_UP_OUT;
      else if (animation.startsWith(Animation.SLIDE_RIGHT_OUT_STR))
        return Animation.SLIDE_RIGHT_OUT;
      else if (animation.startsWith(Animation.SLIDE_LEFT_OUT_STR))
        return Animation.SLIDE_LEFT_OUT;
      else if (animation.startsWith(Animation.SLIDE_DOWN_IN_STR))
        return Animation.SLIDE_DOWN_IN;
      else if (animation.startsWith(Animation.SLIDE_UP_IN_STR))
        return Animation.SLIDE_UP_IN;
      else if (animation.startsWith(Animation.SLIDE_RIGHT_IN_STR))
        return Animation.SLIDE_RIGHT_IN;
      else if (animation.startsWith(Animation.SLIDE_LEFT_IN_STR))
        return Animation.SLIDE_LEFT_IN;
    }
    else if (animation.startsWith("FullSlide"))
    {
      if (animation.startsWith(Animation.FULL_SLIDE_DOWN_OUT_STR))
        return Animation.FULL_SLIDE_DOWN_OUT;
      else if (animation.startsWith(Animation.FULL_SLIDE_UP_OUT_STR))
        return Animation.FULL_SLIDE_UP_OUT;
      else if (animation.startsWith(Animation.FULL_SLIDE_RIGHT_OUT_STR))
        return Animation.FULL_SLIDE_RIGHT_OUT;
      else if (animation.startsWith(Animation.FULL_SLIDE_LEFT_OUT_STR))
        return Animation.FULL_SLIDE_LEFT_OUT;
      else if (animation.startsWith(Animation.FULL_SLIDE_DOWN_IN_STR))
        return Animation.FULL_SLIDE_DOWN_IN;
      else if (animation.startsWith(Animation.FULL_SLIDE_UP_IN_STR))
        return Animation.FULL_SLIDE_UP_IN;
      else if (animation.startsWith(Animation.FULL_SLIDE_RIGHT_IN_STR))
        return Animation.FULL_SLIDE_RIGHT_IN;
      else if (animation.startsWith(Animation.FULL_SLIDE_LEFT_IN_STR))
        return Animation.FULL_SLIDE_LEFT_IN;
    }
    else if (animation.startsWith(Animation.FADE_OUT_STR))
      return Animation.FADE_OUT;
    else if (animation.startsWith(Animation.FADE_IN_STR))
      return Animation.FADE_IN;
    else if (animation.startsWith(Animation.ZOOM_OUT_STR))
      return Animation.ZOOM_OUT;
    else if (animation.startsWith(Animation.ZOOM_IN_STR))
      return Animation.ZOOM_IN;
    else if (animation.startsWith(Animation.HZOOM_OUT_STR))
      return Animation.HZOOM_OUT;
    else if (animation.startsWith(Animation.HZOOM_IN_STR))
      return Animation.HZOOM_IN;
    else if (animation.startsWith(Animation.VZOOM_OUT_STR))
      return Animation.VZOOM_OUT;
    else if (animation.startsWith(Animation.VZOOM_IN_STR))
      return Animation.VZOOM_IN;
    else if (animation.startsWith(Animation.SCROLL_STR))
      return Animation.SCROLL;
    else if (animation.startsWith(Animation.MORPH_STR))
      return Animation.MORPH;
    else if (animation.startsWith(Animation.PAN_STR))
      return Animation.PAN;

    return Animation.SMOOTH;
  }
  public static final int OPTION_FADE = 0x200;
  public static final int OPTION_NORTH = 0x01;
  public static final int OPTION_WEST = 0x02;
  public static final int OPTION_SOUTH = 0x04;
  public static final int OPTION_EAST = 0x08;
  public static final int OPTION_BEHIND = 0x400;
  public static final int OPTION_UNCLIPPED = 0x800;
  public static final int OPTION_UNEASE = 0x1000;
  public static final int OPTION_UNDISTORTED = 0x2000;
  private static final String[] TIMESCALE_NAMES = { "Linear", "Quadratic", "Cubic", "Exponential", "Bounce", "Rebound" };
  private static final String[] OPTION_NAMES = { "Fade", "North", "West", "South", "East", "Behind", "Unclipped", "Unease", "Undistorted" };
  private static final int[] OPTION_VALUES = { OPTION_FADE, OPTION_NORTH, OPTION_WEST, OPTION_SOUTH, OPTION_EAST, OPTION_BEHIND, OPTION_UNCLIPPED,
    OPTION_UNEASE, OPTION_UNDISTORTED };
  public class Animation
  {
    public static final int SLIDE_DOWN_OUT = 0x121;
    public static final int SLIDE_UP_OUT = 0x122;
    public static final int SLIDE_LEFT_OUT = 0x124;
    public static final int SLIDE_RIGHT_OUT = 0x128;
    public static final int SLIDE_DOWN_IN = 0x111;
    public static final int SLIDE_UP_IN = 0x112;
    public static final int SLIDE_LEFT_IN = 0x114;
    public static final int SLIDE_RIGHT_IN = 0x118;
    public static final int FULL_SLIDE_DOWN_OUT = 0x1121;
    public static final int FULL_SLIDE_UP_OUT = 0x1122;
    public static final int FULL_SLIDE_LEFT_OUT = 0x1124;
    public static final int FULL_SLIDE_RIGHT_OUT = 0x1128;
    public static final int FULL_SLIDE_DOWN_IN = 0x1111;
    public static final int FULL_SLIDE_UP_IN = 0x1112;
    public static final int FULL_SLIDE_LEFT_IN = 0x1114;
    public static final int FULL_SLIDE_RIGHT_IN = 0x1118;
    public static final int FADE_OUT = 0x220;
    public static final int FADE_IN = 0x210;
    public static final int SMOOTH = 0x400;
    public static final int ZOOM_OUT = 0x820;
    public static final int ZOOM_IN = 0x810;
    public static final int HZOOM_OUT = 0x821;
    public static final int HZOOM_IN = 0x811;
    public static final int VZOOM_OUT = 0x822;
    public static final int VZOOM_IN = 0x812;
    public static final int SCROLL = 0x1000;
    public static final int MORPH = 0x2000;
    public static final int PAN = 0x4010;
    public static final String SLIDE_DOWN_OUT_STR = "SlideDownOut";
    public static final String SLIDE_UP_OUT_STR = "SlideUpOut";
    public static final String SLIDE_LEFT_OUT_STR = "SlideLeftOut";
    public static final String SLIDE_RIGHT_OUT_STR = "SlideRightOut";
    public static final String SLIDE_DOWN_IN_STR = "SlideDownIn";
    public static final String SLIDE_UP_IN_STR = "SlideUpIn";
    public static final String SLIDE_LEFT_IN_STR = "SlideLeftIn";
    public static final String SLIDE_RIGHT_IN_STR = "SlideRightIn";
    public static final String FULL_SLIDE_DOWN_OUT_STR = "FullSlideDownOut";
    public static final String FULL_SLIDE_UP_OUT_STR = "FullSlideUpOut";
    public static final String FULL_SLIDE_LEFT_OUT_STR = "FullSlideLeftOut";
    public static final String FULL_SLIDE_RIGHT_OUT_STR = "FullSlideRightOut";
    public static final String FULL_SLIDE_DOWN_IN_STR = "FullSlideDownIn";
    public static final String FULL_SLIDE_UP_IN_STR = "FullSlideUpIn";
    public static final String FULL_SLIDE_LEFT_IN_STR = "FullSlideLeftIn";
    public static final String FULL_SLIDE_RIGHT_IN_STR = "FullSlideRightIn";
    public static final String FADE_OUT_STR = "FadeOut";
    public static final String FADE_IN_STR = "FadeIn";
    public static final String SMOOTH_STR = "Smooth";
    public static final String ZOOM_OUT_STR = "ZoomOut";
    public static final String ZOOM_IN_STR = "ZoomIn";
    public static final String HZOOM_OUT_STR = "HZoomOut";
    public static final String HZOOM_IN_STR = "HZoomIn";
    public static final String VZOOM_OUT_STR = "VZoomOut";
    public static final String VZOOM_IN_STR = "VZoomIn";
    public static final String SCROLL_STR = "Scroll";
    public static final String MORPH_STR = "Morph";
    public static final String PAN_STR = "Pan";

    public static final byte SCALE_LINEAR = 0;
    public static final byte SCALE_QUADRATIC = 1;
    public static final byte SCALE_CUBIC = 2;
    public static final byte SCALE_EXPONENTIAL = 3;
    public static final byte SCALE_BOUNCE = 4;
    public static final byte SCALE_REBOUND = 5;
    public Animation(String animation, long inDur, long inDelay)
    {
      this(getAnimTypeForName(animation), animation, inDur, inDelay);
    }
    public Animation(int inAnimType, String animation, long inDur, long inDelay)
    {
      duration = inDur;
      delay = inDelay;
      animType = inAnimType;
      this.animation = animation;
      for (byte i = 0; i < TIMESCALE_NAMES.length; i++)
        if (animation.indexOf(TIMESCALE_NAMES[i]) != -1)
        {
          timescale = i;
          break;
        }
      for (byte i = 0; i < OPTION_NAMES.length; i++)
        if (animation.indexOf(OPTION_NAMES[i]) != -1 && !animation.startsWith(OPTION_NAMES[i]))
        {
          animOptions |= OPTION_VALUES[i];
        }
    }
    public boolean isIn() { return (animType & 0x10) == 0x10; }
    public boolean isOut() { return (animType & 0x20) == 0x20; }
    public boolean isDualSurfaceOp() { return animType == SCROLL || animType == MORPH; }
    public boolean isBehind() { return (animOptions & OPTION_BEHIND) == OPTION_BEHIND; }
    public boolean isExpired(long calcTime)
    {
      return startTime != 0 && startTime + duration + delay < calcTime;
    }
    public void setup(java.awt.geom.Rectangle2D.Float currBounds)
    {
      // When we slide/fade in we want the target region; not the starting region info
      if (animType == SMOOTH || animType == MORPH)
      {
        // We want to use the destination of the smooth operation as the backing
        destRect.setFrame(currBounds);
        orgDestRect = new java.awt.geom.Rectangle2D.Float(destRect.x, destRect.y, destRect.width, destRect.height);
        orgSrcRect = srcRect;
        srcRect = new java.awt.geom.Rectangle2D.Float(destRect.x, destRect.y, destRect.width, destRect.height);
        if (animType == MORPH)
        {
          if ((animOptions & OPTION_UNDISTORTED) == OPTION_UNDISTORTED)
          {
            altDestRect = new java.awt.geom.Rectangle2D.Float(destRect.x, destRect.y, destRect.width, destRect.height);
            altSrcRect = new java.awt.geom.Rectangle2D.Float(orgSrcRect.x, orgSrcRect.y, orgSrcRect.width, orgSrcRect.height);
          }
          else
          {
            altDestRect = destRect;
            altSrcRect = orgSrcRect;
          }
        }
      }
      else if (isIn())
      {
        destRect.setFrame(currBounds);
        srcRect.setFrame(currBounds);
        orgDestRect = new java.awt.geom.Rectangle2D.Float(destRect.x, destRect.y, destRect.width, destRect.height);
        orgSrcRect = new java.awt.geom.Rectangle2D.Float(srcRect.x, srcRect.y, srcRect.width, srcRect.height);
      }
      else if (animType == SCROLL)
      {
        orgDestRect = new java.awt.geom.Rectangle2D.Float(destRect.x, destRect.y, destRect.width, destRect.height);
        altDestRect = new java.awt.geom.Rectangle2D.Float(destRect.x, destRect.y, destRect.width, destRect.height);
        altSrcRect = new java.awt.geom.Rectangle2D.Float(destRect.x, destRect.y, destRect.width, destRect.height);
        // First determine how much overlap there is. Then reduce the size of the start
        // rectangle by that amount so we don't have overlap in the animated rendering.
        if (scrollVector[1] != 0)
        {
          // We're scrolling in the y direction
          float overlap = orgDestRect.height - Math.abs(scrollVector[1]);
          altDestRect.height = altSrcRect.height = Math.abs(scrollVector[1]);
          srcRect.height = destRect.height = overlap;
          if (scrollVector[1] < 0)
          {
            altSrcRect.y += overlap;
            altDestRect.y += overlap;
            srcRect.y = orgDestRect.y - scrollVector[1];
          }
          else
          {
            destRect.y = orgDestRect.y + scrollVector[1];
          }
        }
        else
        {
          // We're scrolling in the X direction
          float overlap = orgDestRect.width - Math.abs(scrollVector[0]);
          altDestRect.width = altSrcRect.width = Math.abs(scrollVector[0]);
          srcRect.width = destRect.width = overlap;
          if (scrollVector[0] < 0)
          {
            altSrcRect.x += overlap;
            altDestRect.x += overlap;
            srcRect.x = orgDestRect.x - scrollVector[0];
          }
          else
          {
            destRect.x = orgDestRect.x + scrollVector[0];
          }
        }
      }
      else
      {
        orgDestRect = new java.awt.geom.Rectangle2D.Float(destRect.x, destRect.y, destRect.width, destRect.height);
        orgSrcRect = new java.awt.geom.Rectangle2D.Float(srcRect.x, srcRect.y, srcRect.width, srcRect.height);
      }
    }
    public void startNow(long inTime)
    {
      startTime = inTime;
    }
    private float getPerct(long calcTime)
    {
      float x = Math.max(0.f, Math.min(1.0f, (calcTime - startTime - delay) / ((float)duration)));
      float z;
      if (((isIn() && (animOptions & OPTION_UNEASE) == OPTION_UNEASE) ||
          (isOut() && (animOptions & OPTION_UNEASE) != OPTION_UNEASE)) && !forcedEaseIn)
      {
        switch (timescale)
        {
          case SCALE_BOUNCE:
          case SCALE_QUADRATIC:
            return x * x;
          case SCALE_CUBIC:
            return x * x * x;
          default:
            return x;
        }
      }
      else if (timescale == SCALE_BOUNCE && !forcedEaseIn)
      {
        int bounceFactor = 4;
        int currStage = 1;
        float lastStart = 0;
        int currMultiplier = bounceFactor;
        while (currStage <= 10 - bounceFactor)
        {
          float nextStart = 1.0f - 1.0f/currMultiplier;
          if (x <= nextStart)
          {
            if (currStage == 1)
            {
              z = x * x / (nextStart * nextStart);
              return z;
            }
            float t = (x - lastStart) / (nextStart - lastStart);
            t = t*2 - 1;
            z = 1.0f - (1.0f - t * t) / currMultiplier;
            return z;
          }
          lastStart = nextStart;
          currMultiplier *= bounceFactor;
          currStage++;
        }
        return x;
      }
      else if ((isIn() && (animOptions & OPTION_UNEASE) != OPTION_UNEASE) || forcedEaseIn || (isOut() && (animOptions & OPTION_UNEASE) == OPTION_UNEASE))
      {
        switch (timescale)
        {
          case SCALE_QUADRATIC:
            z = 1.0f - x;
            return 1.0f - z * z;
          case SCALE_CUBIC:
            z = 1.0f - x;
            return 1.0f - z * z * z;
          default:
            return x;
        }
      }
      else // both
      {
        if (x < 0.5f)
        {
          switch (timescale)
          {
            case SCALE_QUADRATIC:
              return 2 * x * x;
            case SCALE_CUBIC:
              return 4 * x * x * x;
            default:
              return x;
          }
        }
        else
        {
          switch (timescale)
          {
            case SCALE_QUADRATIC:
              z = 1.0f - x;
              return 1.0f - 2 * z * z;
            case SCALE_CUBIC:
              z = 1.0f - x;
              return 1.0f - 4 * z * z * z;
            default:
              return x;
          }
        }
      }
    }
    public void calculateAnimation(long calcTime, int uiWidth, int uiHeight, boolean integerize)
    {
      // Updates the destination rectangle and alpha values for this animation based on the current time
      float perct;
      float altPerct;
      switch (animType)
      {
        case FULL_SLIDE_DOWN_OUT:
          // Animate the cached area off the bottom of the screen. The total distance it has to cover is the height of the
          // buffer minus the y value of the topmost area
          destRect.y = srcRect.y + (uiHeight - srcRect.y) * getPerct(calcTime);
          if (integerize)
            destRect.y = (int)Math.floor(destRect.y);
          break;
        case FULL_SLIDE_UP_OUT:
          // Animate the cached area off the bottom of the screen. The total distance it has to cover is the height of the
          // buffer minus the y value of the topmost area
          destRect.y = srcRect.y - (srcRect.y + srcRect.height) * getPerct(calcTime);
          if (integerize)
            destRect.y = (int)Math.floor(destRect.y);
          break;
        case FULL_SLIDE_LEFT_OUT:
          destRect.x = srcRect.x - (srcRect.x + srcRect.width) * getPerct(calcTime);
          if (integerize)
            destRect.x = (int)Math.floor(destRect.x);
          break;
        case FULL_SLIDE_RIGHT_OUT:
          destRect.x = srcRect.x + (uiWidth - srcRect.x) * getPerct(calcTime);
          if (integerize)
            destRect.x = (int)Math.floor(destRect.x);
          break;
        case FULL_SLIDE_DOWN_IN:
          destRect.y = srcRect.y - (1.0f - getPerct(calcTime)) * (srcRect.y + srcRect.height);
          if (integerize)
            destRect.y = (int)Math.floor(destRect.y);
          break;
        case FULL_SLIDE_UP_IN:
          destRect.y = srcRect.y + (1.0f - getPerct(calcTime)) * (uiHeight - srcRect.y);
          if (integerize)
            destRect.y = (int)Math.floor(destRect.y);
          break;
        case FULL_SLIDE_LEFT_IN:
          destRect.x = srcRect.x + (1.0f - getPerct(calcTime)) *  (uiWidth - srcRect.x);
          if (integerize)
            destRect.x = (int)Math.floor(destRect.x);
          break;
        case FULL_SLIDE_RIGHT_IN:
          destRect.x = srcRect.x - (1.0f - getPerct(calcTime)) * (srcRect.x + srcRect.width);
          if (integerize)
            destRect.x = (int)Math.floor(destRect.x);
          break;
        case SLIDE_DOWN_OUT:
          perct = getPerct(calcTime);
          altPerct = 1.0f - perct;
          destRect.y = orgDestRect.y + orgDestRect.height * perct;
          if ((animOptions & OPTION_UNCLIPPED) != OPTION_UNCLIPPED)
          {
            if (integerize)
            {
              destRect.y = (int)Math.floor(destRect.y);
              srcRect.height = destRect.height = (int)Math.floor(orgDestRect.y + orgDestRect.height) - destRect.y;
            }
            else
            {
              srcRect.height = orgSrcRect.height * altPerct;
              destRect.height = orgDestRect.height * altPerct;
            }
          }
          else if (integerize)
          {
            destRect.y = (int)Math.floor(destRect.y);
            srcRect.height = (int)Math.floor(srcRect.height);
            destRect.height = (int)Math.floor(destRect.height);
          }
          break;
        case SLIDE_UP_OUT:
          perct = getPerct(calcTime);
          altPerct = 1.0f - perct;
          if ((animOptions & OPTION_UNCLIPPED) != OPTION_UNCLIPPED)
          {
            srcRect.y = orgSrcRect.y + perct * orgSrcRect.height;
            if (integerize)
            {
              srcRect.y = (int)Math.floor(srcRect.y);
              srcRect.height = (int)Math.floor(orgSrcRect.height + orgSrcRect.y) - srcRect.y;
              destRect.height = srcRect.height;
            }
            else
            {
              destRect.height = orgDestRect.height * altPerct;
              srcRect.height = orgSrcRect.height * altPerct;
            }
          }
          else
          {
            destRect.y = orgDestRect.y - orgDestRect.height * perct;
            if (integerize)
              destRect.y = (int)Math.floor(destRect.y);
          }
          break;
        case SLIDE_LEFT_OUT:
          perct = getPerct(calcTime);
          altPerct = 1.0f - perct;
          if ((animOptions & OPTION_UNCLIPPED) != OPTION_UNCLIPPED)
          {
            srcRect.x = orgSrcRect.x + perct * orgSrcRect.width;
            if (integerize)
            {
              srcRect.x = (int)Math.floor(srcRect.x);
              destRect.width = srcRect.width = (int)Math.floor(orgSrcRect.x + orgSrcRect.width) - srcRect.x;
            }
            else
            {
              destRect.width = orgDestRect.width * altPerct;
              srcRect.width = orgSrcRect.width * altPerct;
            }
          }
          else
          {
            destRect.x = orgDestRect.x - orgDestRect.width * perct;
            if (integerize)
            {
              srcRect.x = (int)Math.floor(srcRect.x);
              srcRect.width = (int)Math.floor(srcRect.width);
              destRect.width = (int)Math.floor(destRect.width);
            }
          }
          break;
        case SLIDE_RIGHT_OUT:
          perct = getPerct(calcTime);
          altPerct = 1.0f - perct;
          destRect.x = orgDestRect.x + orgDestRect.width * perct;
          if ((animOptions & OPTION_UNCLIPPED) != OPTION_UNCLIPPED)
          {
            if (integerize)
            {
              destRect.x = (int)Math.floor(destRect.x);
              srcRect.width = destRect.width = (int)Math.floor(orgDestRect.x + orgDestRect.width) - destRect.x;
            }
            else
            {
              srcRect.width = orgSrcRect.width * altPerct;
              destRect.width = orgDestRect.width * altPerct;
            }
          }
          else if (integerize)
          {
            destRect.x = (int)Math.floor(destRect.x);
            srcRect.width = (int)Math.floor(srcRect.width);
            destRect.width = (int)Math.floor(destRect.width);
          }
          break;
        case SLIDE_DOWN_IN:
          perct = getPerct(calcTime);
          altPerct = 1.0f - perct;
          if ((animOptions & OPTION_UNCLIPPED) != OPTION_UNCLIPPED)
          {
            srcRect.y = orgSrcRect.y + altPerct * orgSrcRect.height;
            if (integerize)
            {
              srcRect.y = (int)Math.floor(srcRect.y);
              srcRect.height = destRect.height = (int)Math.floor(orgSrcRect.y + orgSrcRect.height) - srcRect.y;
            }
            else
            {
              destRect.height = orgDestRect.height * perct;
              srcRect.height = orgSrcRect.height * perct;
            }
          }
          else
          {
            destRect.y = orgDestRect.y - orgDestRect.height * altPerct;
            if (integerize)
            {
              srcRect.y = (int)Math.floor(srcRect.y);
              srcRect.height = (int)Math.floor(srcRect.height);
              destRect.height = (int)Math.floor(destRect.height);
            }
          }
          break;
        case SLIDE_UP_IN:
          perct = getPerct(calcTime);
          altPerct = 1.0f - perct;
          destRect.y = orgDestRect.y + orgDestRect.height * altPerct;
          if ((animOptions & OPTION_UNCLIPPED) != OPTION_UNCLIPPED)
          {
            if (integerize)
            {
              destRect.y = (int)Math.floor(destRect.y);
              destRect.height = srcRect.height = (int)Math.floor(orgDestRect.y + orgDestRect.height) - destRect.y;
            }
            else
            {
              srcRect.height = orgSrcRect.height * perct;
              destRect.height = orgDestRect.height * perct;
            }
          }
          else if (integerize)
          {
            destRect.y = (int)Math.floor(destRect.y);
            srcRect.height = (int)Math.floor(srcRect.height);
            destRect.height = (int)Math.floor(destRect.height);
          }
          break;
        case SLIDE_LEFT_IN:
          perct = getPerct(calcTime);
          altPerct = 1.0f - perct;
          destRect.x = orgDestRect.x + orgDestRect.width * altPerct;
          if ((animOptions & OPTION_UNCLIPPED) != OPTION_UNCLIPPED)
          {
            if (integerize)
            {
              destRect.x = (int)Math.floor(destRect.x);
              srcRect.width = (int)Math.floor(orgDestRect.x + orgSrcRect.width) - destRect.x;
              destRect.width = (int)Math.floor(orgDestRect.x + orgDestRect.width) - destRect.x;
            }
            else
            {
              srcRect.width = orgSrcRect.width * perct;
              destRect.width = orgDestRect.width * perct;
            }
          }
          else if (integerize)
          {
            destRect.x = (int)Math.floor(destRect.x);
            srcRect.width = (int)Math.floor(srcRect.width);
            destRect.width = (int)Math.floor(destRect.width);
          }
          break;
        case SLIDE_RIGHT_IN:
          perct = getPerct(calcTime);
          altPerct = 1.0f - perct;
          if ((animOptions & OPTION_UNCLIPPED) != OPTION_UNCLIPPED)
          {
            srcRect.x = orgSrcRect.x + altPerct * orgSrcRect.width;
            if (integerize)
            {
              srcRect.x = (int)Math.floor(srcRect.x);
              destRect.width = srcRect.width = (int)Math.floor(orgSrcRect.x + orgSrcRect.width) - srcRect.x;
            }
            else
            {
              destRect.width = orgDestRect.width * perct;
              srcRect.width = orgSrcRect.width * perct;
            }
          }
          else
          {
            destRect.x = orgDestRect.x - orgDestRect.width * altPerct;
            if (integerize)
            {
              srcRect.x = (int)Math.floor(srcRect.x);
              srcRect.width = (int)Math.floor(srcRect.width);
              destRect.width = (int)Math.floor(destRect.width);
            }
          }
          break;
        case FADE_IN:
          alphaFactor = getPerct(calcTime);
          break;
        case FADE_OUT:
          alphaFactor = (1.0f - getPerct(calcTime));
          break;
        case SMOOTH:
        case MORPH:
          perct = getPerct(calcTime);
          altPerct = 1.0f - perct;
          if (animType == MORPH)
          {
            alphaFactor = perct;
            altAlphaFactor = altPerct;
          }
          else
            alphaFactor = 1.0f;
          // NOTE: We can end up with floating point errors here because we floor instead of rounding after this. BUT
          // those are only really a problem if the value is supposed to remain constant. So check for that special case.
          if (orgDestRect.width == orgSrcRect.width)
            destRect.width = orgDestRect.width;
          else
            destRect.width = perct * orgDestRect.width + altPerct * orgSrcRect.width;
          if (orgDestRect.height == orgSrcRect.height)
            destRect.height = orgDestRect.height;
          else
            destRect.height = perct * orgDestRect.height + altPerct * orgSrcRect.height;
          if (orgDestRect.x == orgSrcRect.x)
            destRect.x = orgDestRect.x;
          else
            destRect.x = perct * orgDestRect.x + altPerct * orgSrcRect.x;
          if (orgDestRect.y == orgSrcRect.y)
            destRect.y = orgDestRect.y;
          else
            destRect.y = perct * orgDestRect.y + altPerct * orgSrcRect.y;
          if (integerize)
          {
            destRect.width = (int)Math.floor(destRect.width);
            destRect.height = (int)Math.floor(destRect.height);
            destRect.x = (int)Math.floor(destRect.x);
            destRect.y = (int)Math.floor(destRect.y);
          }
          if ((animOptions & OPTION_UNDISTORTED) == OPTION_UNDISTORTED)
          {
            if (animType == MORPH)
            {
              altDestRect.setRect(destRect);
              altSrcRect.width = altDestRect.width = Math.min(orgSrcRect.width, altDestRect.width);
              altSrcRect.height = altDestRect.height = Math.min(orgSrcRect.height, altDestRect.height);
            }
            srcRect.width = destRect.width = Math.min(orgDestRect.width, destRect.width);
            srcRect.height = destRect.height = Math.min(orgDestRect.height, destRect.height);
          }
          break;
        case ZOOM_IN:
        case ZOOM_OUT:
          perct = (animType == ZOOM_IN) ? getPerct(calcTime) : (1.0f - getPerct(calcTime));
          destRect.width = perct * srcRect.width;
          destRect.height = perct * srcRect.height;
          destRect.x = srcRect.x + (srcRect.width - destRect.width)*getAlignX();
          destRect.y = srcRect.y + (srcRect.height - destRect.height)*getAlignY();
          if (integerize)
          {
            destRect.width = (int)Math.floor(destRect.width);
            destRect.height = (int)Math.floor(destRect.height);
            destRect.x = (int)Math.floor(destRect.x);
            destRect.y = (int)Math.floor(destRect.y);
          }
          break;
        case HZOOM_IN:
        case HZOOM_OUT:
          perct = (animType == HZOOM_IN) ? getPerct(calcTime) : (1.0f - getPerct(calcTime));
          destRect.width = perct * srcRect.width;
          destRect.height = srcRect.height;
          destRect.x = srcRect.x + (srcRect.width - destRect.width)*getAlignX();
          destRect.y = srcRect.y;
          if (integerize)
          {
            destRect.width = (int)Math.floor(destRect.width);
            destRect.height = (int)Math.floor(destRect.height);
            destRect.x = (int)Math.floor(destRect.x);
            destRect.y = (int)Math.floor(destRect.y);
          }
          break;
        case VZOOM_IN:
        case VZOOM_OUT:
          perct = (animType == VZOOM_IN) ? getPerct(calcTime) : (1.0f - getPerct(calcTime));
          destRect.width = srcRect.width;
          destRect.height = perct * srcRect.height;
          destRect.x = srcRect.x;
          destRect.y = srcRect.y + (srcRect.height - destRect.height)*getAlignY();
          if (integerize)
          {
            destRect.width = (int)Math.floor(destRect.width);
            destRect.height = (int)Math.floor(destRect.height);
            destRect.x = (int)Math.floor(destRect.x);
            destRect.y = (int)Math.floor(destRect.y);
          }
          break;
        case SCROLL:
          perct = getPerct(calcTime);
          if (scrollVector[1] != 0)
          {
            // We're scrolling in the y direction
            float overlap = orgDestRect.height - Math.abs(scrollVector[1]);
            float scrollAmount = Math.abs(scrollVector[1]) * perct;
            altDestRect.height = altSrcRect.height = Math.abs(scrollVector[1]) - scrollAmount;
            srcRect.height = destRect.height = overlap + scrollAmount;
            if (scrollVector[1] < 0)
            {
              altDestRect.y = orgDestRect.y + overlap + scrollAmount;
              srcRect.y = orgDestRect.y + (-scrollVector[1] - scrollAmount);
            }
            else
            {
              altSrcRect.y = orgDestRect.y + scrollAmount;
              destRect.y = orgDestRect.y + orgDestRect.height - destRect.height;
            }
            if (integerize)
            {
              altDestRect.height = altSrcRect.height = (int)Math.floor(altSrcRect.height);
              srcRect.height = destRect.height = (int)Math.floor(destRect.height);
              srcRect.y = (int)Math.floor(srcRect.y);
              destRect.y = (int)Math.floor(destRect.y);
              altDestRect.y = (int)Math.floor(altDestRect.y);
              altSrcRect.y = (int)Math.floor(altSrcRect.y);
            }
          }
          else
          {
            // We're scrolling in the X direction
            float overlap = orgDestRect.width - Math.abs(scrollVector[0]);
            float scrollAmount = Math.abs(scrollVector[0]) * perct;
            altDestRect.width = altSrcRect.width = Math.abs(scrollVector[0]) - scrollAmount;
            srcRect.width = destRect.width = overlap + scrollAmount;
            if (scrollVector[0] < 0)
            {
              altDestRect.x = orgDestRect.x + overlap + scrollAmount;
              srcRect.x = orgDestRect.x + (-scrollVector[0] - scrollAmount);
            }
            else
            {
              altSrcRect.x = orgDestRect.x + scrollAmount;
              destRect.x = orgDestRect.x + orgDestRect.width - destRect.width;
            }
            if (integerize)
            {
              altDestRect.width = altSrcRect.width = (int)Math.floor(altSrcRect.width);
              srcRect.width = destRect.width = (int)Math.floor(destRect.width);
              srcRect.x = (int)Math.floor(srcRect.x);
              destRect.x = (int)Math.floor(destRect.x);
              altDestRect.x = (int)Math.floor(altDestRect.x);
              altSrcRect.x = (int)Math.floor(altSrcRect.x);
            }
          }
          break;
        case PAN:
          float fadeRange = 0.1f;
          float panAmount = 0.02f;
          perct = getPerct(calcTime);
          srcRect.width = orgSrcRect.width * (1.0f - panAmount);
          srcRect.height = orgSrcRect.height * (1.0f - panAmount);
          srcRect.x = orgSrcRect.width * panAmount * perct;
          srcRect.y = orgSrcRect.height * panAmount * perct;
          if (perct < fadeRange)
            alphaFactor = perct / fadeRange;
          else if (perct > (1.0f - fadeRange))
            alphaFactor = (1.0f - perct) / fadeRange;
          else
            alphaFactor = 1.0f;
          if (integerize)
          {
            srcRect.x = (int)Math.floor(srcRect.x);
            srcRect.y = (int)Math.floor(srcRect.y);
            srcRect.width = (int)Math.floor(srcRect.width);
            srcRect.height = (int)Math.floor(srcRect.height);
          }
          break;
      }

      if ((animOptions & OPTION_FADE) == OPTION_FADE)
      {
        if (isIn())
          alphaFactor = getPerct(calcTime);
        else
          alphaFactor = (1.0f - getPerct(calcTime));
      }
    }

    public java.awt.Rectangle getAnimationArea(int uiWidth, int uiHeight)
    {
      // This at least covers the source and dest rects
      switch (animType)
      {
        case SMOOTH:
        case MORPH:
          java.awt.Rectangle rv0 = new java.awt.Rectangle();
          java.awt.Rectangle.union(orgSrcRect, orgDestRect, rv0);
          return rv0;
        case FULL_SLIDE_UP_IN:
        case FULL_SLIDE_DOWN_OUT:
          return new java.awt.Rectangle(Math.round(srcRect.x), Math.round(srcRect.y),
              Math.round(srcRect.width), Math.round(uiHeight - srcRect.y));
        case FULL_SLIDE_LEFT_IN:
        case FULL_SLIDE_RIGHT_OUT:
          return new java.awt.Rectangle(Math.round(srcRect.x), Math.round(srcRect.y),
              Math.round(uiWidth - srcRect.x), Math.round(srcRect.height));
        case FULL_SLIDE_UP_OUT:
        case FULL_SLIDE_DOWN_IN:
          return new java.awt.Rectangle(Math.round(srcRect.x), 0, Math.round(srcRect.width),
              Math.round(srcRect.y + srcRect.height));
        case FULL_SLIDE_LEFT_OUT:
        case FULL_SLIDE_RIGHT_IN:
          return new java.awt.Rectangle(0, Math.round(srcRect.y),
              Math.round(srcRect.x + srcRect.width), Math.round(srcRect.height));
        case SLIDE_UP_IN:
        case SLIDE_DOWN_OUT:
        case SLIDE_LEFT_IN:
        case SLIDE_RIGHT_OUT:
        case SLIDE_UP_OUT:
        case SLIDE_DOWN_IN:
        case SLIDE_LEFT_OUT:
        case SLIDE_RIGHT_IN:
        case SCROLL:
        case FADE_IN:
        case FADE_OUT:
        case ZOOM_IN:
        case ZOOM_OUT:
        case VZOOM_IN:
        case VZOOM_OUT:
        case HZOOM_IN:
        case HZOOM_OUT:
        default:
          java.awt.Rectangle rv1 = new java.awt.Rectangle();
          rv1.setRect(orgDestRect);
          return rv1;
      }
    }

    private float getAlignX()
    {
      if ((animOptions & 0xF) == 0)
        return 0.5f;
      if ((animOptions & OPTION_WEST) == OPTION_WEST)
        return 0f;
      if ((animOptions & OPTION_EAST) == OPTION_EAST)
        return 1.0f;
      return 0.5f;
    }

    private float getAlignY()
    {
      if ((animOptions & 0xF) == 0)
        return 0.5f;
      if ((animOptions & OPTION_NORTH) == OPTION_NORTH)
        return 0f;
      if ((animOptions & OPTION_SOUTH) == OPTION_SOUTH)
        return 1.0f;
      return 0.5f;
    }

    public int animType;
    public long duration;
    public long startTime;
    public long delay;
    public String animation;
    public boolean cleanedDL;
    public boolean expired;
    // Used for the start scrolling position
    public java.awt.geom.Rectangle2D.Float orgDestRect;
    public java.awt.geom.Rectangle2D.Float orgSrcRect;
    // The 'alt' values are for the secondary (original) surface that we're working with
    public java.awt.geom.Rectangle2D.Float altDestRect;
    public java.awt.geom.Rectangle2D.Float altSrcRect;
    public float altAlphaFactor = 1.0f;
    public float[] scrollVector;
    public byte timescale = SCALE_QUADRATIC;
    public boolean forcedEaseIn;
    public int animOptions;
    public boolean interruptable;
    public String altSurfName; // to be filled in by the renderer during prep
  }
}
