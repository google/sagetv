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

/**
 *
 * @author Narflex
 */
public class EffectTracker
{
  public static final int EFFECT_FADE = 0x1;
  public static final int EFFECT_SLIDE = 0x2;
  public static final int EFFECT_SCALE = 0x4;
  public static final int EFFECT_ROTATEX = 0x8;
  public static final int EFFECT_ROTATEY = 0x10;
  public static final int EFFECT_ROTATEZ = 0x20;
  public static final int EFFECT_SCALE_OR_ROTATE = 0x3C;

  public static final byte EASE_IN = 0x1;
  public static final byte EASE_OUT = 0x2;
  public static final byte EASE_INOUT = 0x3;

  public static final byte SCALE_LINEAR = 0;
  public static final byte SCALE_QUADRATIC = 1;
  public static final byte SCALE_CUBIC = 2;
  public static final byte SCALE_BOUNCE = 3;
  public static final byte SCALE_REBOUND = 4;
  public static final byte SCALE_SINE = 5;
  public static final byte SCALE_CIRCLE = 6;
  public static final byte SCALE_CURL = 7;

  public static final byte TRIGGER_MENULOADED = 1;
  public static final byte TRIGGER_MENUUNLOADED = 2;
  public static final byte TRIGGER_FOCUSED = 3;
  public static final byte TRIGGER_UNFOCUSED = 4;
  public static final byte TRIGGER_SHOWN = 5;
  public static final byte TRIGGER_HIDDEN = 6;
  public static final byte TRIGGER_CONDITIONAL = 7;
  public static final byte TRIGGER_VISIBLECHANGE = 8;
  public static final byte TRIGGER_STATIC = 9;
  public static final byte TRIGGER_FOCUSTRACKER = 10;
  public static final byte TRIGGER_SMOOTHTRACKER = 11;
  public static final byte TRIGGER_ITEMSELECTED = 12;
  /** Creates a new instance of EffectTracker */
  public EffectTracker(ZPseudoComp inComp, long inDelay, long inDuration, byte inEasing, byte inTimescale)
  {
    srcComp = inComp;
    trigger = TRIGGER_STATIC;
    easing = inEasing;
    timescale = inTimescale;
    preDelay = inDelay;
    duration = inDuration;
  }
  public EffectTracker(Widget inWidg, ZPseudoComp inComp, Catbert.Context effectContext)
  {
    srcComp = inComp;
    widg = inWidg;
    updateEffectPropsFromWidget(effectContext);
  }

  public void updateEffectPropsFromWidget(Catbert.Context effectContext)
  {
    String effectType = widg.getStringProperty(Widget.EFFECT_TRIGGER, effectContext, srcComp);
    if (Widget.FOCUSGAINED_EFFECT.equals(effectType))
    {
      trigger = TRIGGER_FOCUSED;
      easing = EASE_IN;
    }
    else if (Widget.FOCUSLOST_EFFECT.equals(effectType))
    {
      trigger = TRIGGER_UNFOCUSED;
      easing = EASE_OUT;
    }
    else if (Widget.MENULOADED_EFFECT.equals(effectType))
    {
      trigger = TRIGGER_MENULOADED;
      easing = EASE_IN;
    }
    else if (Widget.MENUUNLOADED_EFFECT.equals(effectType))
    {
      trigger = TRIGGER_MENUUNLOADED;
      easing = EASE_OUT;
    }
    else if (Widget.SHOWN_EFFECT.equals(effectType))
    {
      trigger = TRIGGER_SHOWN;
      easing = EASE_IN;
    }
    else if (Widget.VISIBLECHANGE_EFFECT.equals(effectType))
    {
      trigger = TRIGGER_VISIBLECHANGE;
      easing = EASE_INOUT;
    }
    else if (Widget.HIDDEN_EFFECT.equals(effectType))
    {
      trigger = TRIGGER_HIDDEN;
      easing = EASE_OUT;
    }
    else if (Widget.SMOOTHTRACKER_EFFECT.equals(effectType))
    {
      trigger = TRIGGER_SMOOTHTRACKER;
      easing = EASE_INOUT;
    }
    else if (Widget.FOCUSTRACKER_EFFECT.equals(effectType))
    {
      trigger = TRIGGER_FOCUSTRACKER;
      easing = EASE_INOUT;
    }
    else if (Widget.CONDITIONAL_EFFECT.equals(effectType))
    {
      trigger = TRIGGER_CONDITIONAL;
      easing = EASE_INOUT;
    }
    else if (Widget.ITEMSELECTED_EFFECT.equals(effectType))
    {
      trigger = TRIGGER_ITEMSELECTED;
      easing = EASE_INOUT;
    }
    else
    {
      trigger = TRIGGER_STATIC;
      easing = EASE_INOUT;
    }
    Number[] delays = widg.getNumericArrayProperty(Widget.DELAY, effectContext, srcComp);
    if (delays == null || delays.length == 0)
      preDelay = postDelay = 0;
    else if (delays.length == 1)
    {
      preDelay = delays[0].intValue();
      postDelay = 0;
    }
    else
    {
      preDelay = delays[0].intValue();
      postDelay = delays[1].intValue();
    }
    duration = widg.getIntProperty(Widget.DURATION, 0, effectContext, srcComp);
    reversible = widg.getBooleanProperty(Widget.REVERSIBLE, effectContext, srcComp);
    loop = widg.getBooleanProperty(Widget.LOOP, effectContext, srcComp);
    String inEasing = widg.getStringProperty(Widget.EASING, effectContext, srcComp);
    String inTimescale = widg.getStringProperty(Widget.TIMESCALE, effectContext, srcComp);

    if ("In".equals(inEasing))
      easing = EASE_IN;
    else if ("InOut".equals(inEasing))
      easing = EASE_INOUT;
    else if ("Out".equals(inEasing))
      easing = EASE_OUT;

    if ("Curl".equals(inTimescale))
      timescale = SCALE_CURL;
    else if ("Quadratic".equals(inTimescale))
      timescale = SCALE_QUADRATIC;
    else if ("Cubic".equals(inTimescale))
      timescale = SCALE_CUBIC;
    else if ("Bounce".equals(inTimescale))
      timescale = SCALE_BOUNCE;
    else if ("Rebound".equals(inTimescale))
      timescale = SCALE_REBOUND;
    else if ("Sine".equals(inTimescale))
      timescale = SCALE_SINE;
    else if ("Circle".equals(inTimescale))
      timescale = SCALE_CIRCLE;

    if (trigger == TRIGGER_SMOOTHTRACKER)
    {
      if (widg.hasProperty(Widget.KEY))
        trackerKey = widg.getObjectProperty(Widget.KEY, effectContext, srcComp);
      else
        trackerKey = widg.getName();
    }
  }

  public boolean hasFade()
  {
    return (effectType & EFFECT_FADE) != 0;
  }

  public boolean hasSlide()
  {
    return (effectType & EFFECT_SLIDE) != 0;
  }

  public boolean hasZoom()
  {
    return (effectType & EFFECT_SCALE) != 0;
  }

  public boolean hasRotateX()
  {
    return (effectType & EFFECT_ROTATEX) != 0;
  }

  public boolean hasRotateY()
  {
    return (effectType & EFFECT_ROTATEY) != 0;
  }

  public boolean hasRotateZ()
  {
    return (effectType & EFFECT_ROTATEZ) != 0;
  }

  public float getCurrentFade()
  {
    if (currProgress == 0 && !active && !needsStartup && trigger != TRIGGER_ITEMSELECTED) return 1.0f; // no effect currently
    return Math.max(0, startAlpha + (currProgress * deltaAlpha));
  }

  // returns true if the tranform is not the identity matrix
  public boolean getCurrentTransform(javax.vecmath.Matrix4f tempMat, java.awt.geom.Rectangle2D.Float trueOffset)
  {
    return getCurrentTransform(tempMat, trueOffset, false, false);
  }
  public boolean getCurrentTransform(javax.vecmath.Matrix4f tempMat, java.awt.geom.Rectangle2D.Float trueOffset, boolean invertXScale, boolean invertYScale)
  {
    return getCurrentTransform(tempMat, trueOffset, invertXScale, invertYScale, false);
  }
  public boolean getCurrentTransform(javax.vecmath.Matrix4f tempMat, java.awt.geom.Rectangle2D.Float trueOffset, boolean invertXScale, boolean invertYScale, boolean disableScaling)
  {
    // NOTE: Narflex - if we return false here; then we end up disabling the end state of reversible animations from being properly
    // displayed.
    if (currProgress == 0 && !active && !reversible) return false;
    tempMat.setIdentity();
    boolean rv = false;
    if ((effectType & EFFECT_SLIDE) != 0)
    {
      float currX = startTransX + (currProgress * deltaTransX);
      float currY = startTransY + (currProgress * deltaTransY);
      if (currX != 0 || currY != 0)
      {
        MathUtils.translateMatrix(tempMat, currX, currY);
        rv = true;
      }
    }
    if ((effectType & EFFECT_SCALE_OR_ROTATE) != 0)
    {
      float trueX = 0;
      float trueY = 0;
      if (trueOffset != null && !isTracking())
      {
        trueX = trueOffset.x;
        trueY = trueOffset.y;
      }
      float scalex = 1;
      float scaley = 1;
      float rotx = 0;
      float roty = 0;
      float rotz = 0;
      boolean applyNow = false;
      if ((effectType & EFFECT_SCALE) != 0 && !disableScaling)
      {
        scalex = startScaleX + (currProgress * deltaScaleX);
        if (invertXScale)
          scalex *= -1;
        if (invertYScale)
          scaley *= -1;
        scaley = startScaleY + (currProgress * deltaScaleY);
        if (scalex != 1 || scaley != 1)
          applyNow = true;
      }
      if ((effectType & EFFECT_ROTATEX) != 0)
      {
        rotx = startRotateX + (currProgress * deltaRotateX);
        if (rotx != 0)
          applyNow = true;
      }
      if ((effectType & EFFECT_ROTATEY) != 0)
      {
        roty = startRotateY + (currProgress * deltaRotateY);
        if (roty != 0)
          applyNow = true;
      }
      if ((effectType & EFFECT_ROTATEZ) != 0)
      {
        rotz = startRotateZ + (currProgress * deltaRotateZ);
        if (rotz != 0)
          applyNow = true;
      }
      if (applyNow)
      {
        rv = true;
        MathUtils.translateMatrix(tempMat, centerX + trueX, centerY + trueY);
        if (scalex != 1 || scaley != 1)
          MathUtils.scaleMatrix(tempMat, scalex, scaley);
        if (rotx != 0)
          MathUtils.rotateMatrixX(tempMat, rotx);
        if (roty != 0)
          MathUtils.rotateMatrixY(tempMat, roty);
        if (rotz != 0)
          MathUtils.rotateMatrixZ(tempMat, rotz);
        MathUtils.translateMatrix(tempMat, -(centerX + trueX), -(centerY + trueY));
      }
    }
    return rv;
  }

  public void transformTrackerCoords()
  {
    javax.vecmath.Matrix4f tempMat = new javax.vecmath.Matrix4f();
    if (getCurrentTransform(tempMat, null))
    {
      java.awt.geom.Rectangle2D.Float tempRect = new java.awt.geom.Rectangle2D.Float(trackerX, trackerY, trackerWidth, trackerHeight);
      MathUtils.transformRectCoords(tempRect, tempMat, tempRect);
      trackerX = tempRect.x;
      trackerY = tempRect.y;
      trackerWidth = tempRect.width;
      trackerHeight = tempRect.height;
    }
  }

  public void setFadeEffect(float startAlpha, float endAlpha)
  {
    this.startAlpha = startAlpha;
    deltaAlpha = endAlpha - startAlpha;
    effectType |= EFFECT_FADE;
  }

  public void resetTrackerState()
  {
    effectType = 0;
    deltaAlpha = deltaScaleX = deltaScaleY = startTransX = startTransY = deltaTransY = deltaTransX = 0;
    startAlpha = startScaleX = startScaleY = 1;
  }

  public void setTranslationEffect(float startX, float startY, float endX, float endY)
  {
    this.startTransX = startX;
    this.startTransY = startY;
    deltaTransX = endX - startX;
    deltaTransY = endY - startY;
    effectType |= EFFECT_SLIDE;
  }

  public float getTargetTranslationX()
  {
    return ((positive || needsStartup) && !needsReverse) ? (startTransX + deltaTransX) : startTransX;
  }

  public float getTargetTranslationY()
  {
    return ((positive || needsStartup) && !needsReverse) ? (startTransY + deltaTransY) : startTransY;
  }

  public float getTargetScaleX()
  {
    return ((positive || needsStartup) && !needsReverse) ? (startScaleX + deltaScaleX) : startScaleX;
  }

  public float getTargetScaleY()
  {
    return ((positive || needsStartup) && !needsReverse) ? (startScaleY + deltaScaleY) : startScaleY;
  }

  public void setZoomEffect(float startX, float startY, float endX, float endY, float centerX, float centerY)
  {
    this.startScaleX = startX;
    this.startScaleY = startY;
    deltaScaleX = endX - startX;
    deltaScaleY = endY - startY;
    this.centerX = centerX;
    this.centerY = centerY;
    effectType |= EFFECT_SCALE;
  }

  public void setRotateXEffect(float start, float end, float centerX, float centerY)
  {
    this.startRotateX = start;
    deltaRotateX = end - start;
    this.centerX = centerX;
    this.centerY = centerY;
    effectType |= EFFECT_ROTATEX;
  }

  public void setRotateYEffect(float start, float end, float centerX, float centerY)
  {
    this.startRotateY = start;
    deltaRotateY = end - start;
    this.centerX = centerX;
    this.centerY = centerY;
    effectType |= EFFECT_ROTATEY;
  }

  public void setRotateZEffect(float start, float end, float centerX, float centerY)
  {
    this.startRotateZ = start;
    deltaRotateZ = end - start;
    this.centerX = centerX;
    this.centerY = centerY;
    effectType |= EFFECT_ROTATEZ;
  }

  public void setCameraOffset(float x, float y)
  {
    cameraOffsetX = x;
    cameraOffsetY = y;
  }

  public boolean hasCameraOffset()
  {
    return cameraOffsetX != 0 || cameraOffsetY != 0;
  }

  public float getCameraOffsetX() { return cameraOffsetX; }
  public float getCameraOffsetY() { return cameraOffsetY; }

  public static float convertTimescale(float value, byte scale, byte ease)
  {
    float x = Math.max(0.f, Math.min(1.0f, value));
    float z;
    float rv;
    float orgX = 0;
    switch (scale)
    {
      case SCALE_QUADRATIC:
        if (ease == EASE_IN)
        {
          z = 1.0f - x;
          return 1.0f - z*z;
        }
        else if (ease == EASE_OUT)
        {
          return x*x;
        }
        else
        {
          if (x < 0.5f)
          {
            return 2 * x * x;
          }
          else
          {
            z = 1.0f - x;
            return 1.0f - 2*z*z;
          }
        }
      case SCALE_CUBIC:
        if (ease == EASE_IN)
        {
          z = 1.0f - x;
          return 1.0f - z*z*z;
        }
        else if (ease == EASE_OUT)
        {
          return x*x*x;
        }
        else
        {
          if (x < 0.5f)
          {
            return 4 * x * x * x;
          }
          else
          {
            z = 1.0f - x;
            return 1.0f - 4*z*z*z;
          }
        }
      case SCALE_CIRCLE:
        if (ease == EASE_IN)
        {
          return (float)Math.sqrt(1-((1-x)*(1-x)));
        }
        else if (ease == EASE_OUT)
        {
          return 1.0f - (float)Math.sqrt(1 - x*x);
        }
        else
        {
          if (x < 0.5f)
          {
            return 0.5f - 0.5f*(float)Math.sqrt(1 - 4*x*x);
          }
          else
          {
            z = (2 - 2*x);
            return 0.5f + 0.5f*(float)Math.sqrt(1 - z*z);
          }
        }
      case SCALE_SINE:
        if (ease == EASE_IN)
        {
          return (float)Math.sin(Math.PI*x/2);
        }
        else if (ease == EASE_OUT)
        {
          return 1.0f - (float)Math.sin(Math.PI*(1.0f - x)/2);
        }
        else
        {
          return 0.5f - 0.5f*(float)Math.sin(Math.PI*(1-x*2)/2);
        }
      case SCALE_CURL:
        float curlFactor = 1.7f;
        if (ease == EASE_IN)
        {
          z = 1.0f - x;
          return 1.0f - z*z*((curlFactor+1)*z - curlFactor);
        }
        else if (ease == EASE_OUT)
        {
          return x * x * ((curlFactor+1)*x - curlFactor);
        }
        else
        {
          curlFactor *= 1.5f;
          if (x < 0.5f)
          {
            return 2 * x * x * ((curlFactor+1)*x*2 - curlFactor);
          }
          else
          {
            z = (2 - 2*x);
            return 1.0f - 0.5f*z*z*((curlFactor+1)*z-curlFactor);
          }
        }
      case SCALE_REBOUND:
        if (ease == EASE_IN)
          x = 1.0f - x;
        else if (ease == EASE_INOUT)
        {
          orgX = x;
          if (x > 0.5f)
            x = 2 * (1.0f - x);
          else
            x = 2*x;
        }
        rv = (float)(-1*Math.pow(2.0f, 10 * (x - 1)) * Math.sin((x - 1 - 0.3f/4) * 2 * Math.PI/0.3f));
        if (ease == EASE_IN)
          rv = 1.0f - rv;
        else if (ease == EASE_INOUT)
        {
          if (orgX > 0.5f)
            rv = 1.0f - rv/2;
          else
            rv = rv/2;
        }
        return rv;
      case SCALE_BOUNCE:
        if (ease == EASE_IN)
          x = 1.0f - x;
        else if (ease == EASE_INOUT)
        {
          orgX = x;
          if (x > 0.5f)
            x = 2 * (x - 0.5f);
          else
            x = 2*(0.5f - x);
        }
        if (x < 1/2.75)
          rv = 7.5625f * x * x;
        else if (x < 2/2.75)
        {
          x -= 1.5f/2.75f;
          rv = 7.5625f * x * x + 0.75f;
        }
        else if (x < 2.5/2.75)
        {
          x -= 2.25f/2.75f;
          rv = 7.5625f * x * x + 0.9375f;
        }
        else
        {
          x -= 2.625f/2.75f;
          rv = 7.5625f * x * x + 0.984375f;
        }
        if (ease == EASE_IN)
          rv = 1.0f - rv;
        else if (ease == EASE_INOUT)
        {
          if (orgX > 0.5f)
            rv = 0.5f + rv/2;
          else
            rv = 0.5f - rv/2;
        }
        return rv;
      case SCALE_LINEAR:
      default:
        return x;
    }
  }

  // This is the percentage we are through the effect; the current rendering should occur this
  // amount between the start and end points
  private float getProgress(long currTime)
  {
    float rv;
    if (needsStartup)
      rv = 0; // haven't started yet
    else if (!active)
      rv = 1;
    else
    {
      long timeDiff = currTime - startTime;
      if (timeDiff < 0)
      {
        timeDiff = 0;
        startTime = currTime;
      }
      if (timeDiff < preDelay)
        rv = 0;
      else if (duration == 0)
        rv = 1;
      else if (loop)
      {
        boolean killLoop = false;
        if (reversible)
        {
          if (!positive && timeDiff > preDelay + duration)
            killLoop = true;
          timeDiff = timeDiff % (preDelay + duration*2 + postDelay);
          if (timeDiff > preDelay + duration + postDelay)
            timeDiff = preDelay + ((preDelay + duration*2 + postDelay) - timeDiff);
        }
        else
          timeDiff = timeDiff % (preDelay + duration + postDelay);
        if (timeDiff < preDelay)
          rv = 0;
        else if (killLoop)
          rv = positive ? 0 : 1;
        else
          rv = ((float)(timeDiff - preDelay))/duration;
      }
      else
        rv = ((float)(timeDiff - preDelay))/duration;
      rv = Math.max(0, Math.min(1, rv));
      // Force the effect into the end state if effect animations are disabled
      if (!srcComp.getUIMgr().areEffectsEnabled())
        rv = 1;
    }
    if (!positive)
      rv = 1.0f - rv;
    rv = convertTimescale(rv, timescale, easing);
    return rv;
  }

  public synchronized void setInitialPositivity(boolean x)
  {
    positive = x;
  }

  public synchronized void setPositivity(boolean x)
  {
    if (ZPseudoComp.DEBUG_EFFECTS)
      System.out.println("setPositivity=" + x + " for " + widg + " text=" + srcComp.getRelatedContext().get("ButtonText") + " comp=" + System.identityHashCode(srcComp) + " positive=" + positive + " active=" + active + " needsStartup=" + needsStartup + " needsRev=" + needsReverse + " prog=" + currProgress);
    if (positive != x)
    {
      if (positive)
      {
        if ((reversible && (active || trigger == TRIGGER_CONDITIONAL)) || trigger == TRIGGER_VISIBLECHANGE)
        {
          needsStartup = needsStop = false;
          needsReverse = true;
        }
        else
        {
          needsStartup = needsReverse = false;
          needsStop = true;
          // I think we should always just do the stop now; otherwise the effect might never actually get processed correctly
          // for the hide case
          //					if (!active)
          {
            needsStop = false;
            active = false;
            currProgress = 0;
            positive = false;
          }
        }
      }
      else
      {
        if (reversible && active && (trigger == TRIGGER_CONDITIONAL || trigger == TRIGGER_VISIBLECHANGE))
        {
          needsStartup = needsStop = false;
          needsReverse = true;
        }
        else
        {
          needsStop = needsReverse = false;
          needsStartup = true;
        }
      }
      if (duration == 0 && preDelay == 0)
      {
        // Just apply it now
        processEffectState(Sage.eventTime());
      }
    }
    else if (!x && needsStartup)
    {
      // NOTE: Narflex 5/27/10 - What can happen here is if the Effect is set into a needStartup state on a positive trigger and then the ActiveRender runs
      // again before the FinalRender has processed that effect, and then at that time the effect goes into a negative state we would have missed the
      // transition because positive would still be false....but needStartup would still be true. So then we end up with an incorrectly rendered frame
      // where we're showing an effect that should be disabled.
      needsStartup = needsReverse = needsStop = active = false;
      currProgress = 0;
    }
    else if (needsReverse)
    {
      // NOTE: Narflex 5/27/10 - What can happen here is if a reversible Effect is in the active positive state and is told to turn off, so it sets needsReverse.
      // Then if before the effect is processed by the FinalRender; we try to set it back into the positive state...but it won't do anything because it thinks
      // it's already in the right state. So the effect would reverse itself and finish that way, which is wrong. We instead just need to disable the needsReverse
      // and let it continue with what it was doing. Actually....this could go either way, be it the positive or negative...so just cancel the reverse in either case.
      needsReverse = false;
    }
  }

  // Calculates what our current progress should be so then we can get the current effects values,
  // Returns true if the start time was established now and could use correction after the current rendering pass finishes
  public synchronized boolean processEffectState(long currTime)
  {
    return processEffectState(currTime, true);
  }
  public synchronized boolean processEffectState(long currTime, boolean transitional)
  {
    if (ZPseudoComp.DEBUG_EFFECTS)
      System.out.println("processEffectState for " + widg + " text=" + srcComp.getRelatedContext().get("ButtonText") + " comp=" + System.identityHashCode(srcComp) + " positive=" + positive + " active=" + active + " needsStartup=" + needsStartup + " needsRev=" + needsReverse + " prog=" + currProgress);
    if (needsTrackerUpdate)
    {
      if (srcTrackerEffect != null)
      {
        resetTrackerState();
        srcTrackerEffect.transformTrackerCoords();
        trackerX = trackerTarget.x;
        trackerY = trackerTarget.y;
        trackerWidth = trackerTarget.width;
        trackerHeight = trackerTarget.height;
        if (srcTrackerEffect.trackerX != trackerX || srcTrackerEffect.trackerY != trackerY)
          setTranslationEffect(srcTrackerEffect.trackerX - trackerX,
              srcTrackerEffect.trackerY - trackerY, 0, 0);
        if (srcTrackerEffect.trackerWidth != trackerWidth || srcTrackerEffect.trackerHeight != trackerHeight)
          setZoomEffect(srcTrackerEffect.trackerWidth/trackerWidth, srcTrackerEffect.trackerHeight/trackerHeight,
              1, 1, trackerX, trackerY);
        srcTrackerEffect = null;
      }
      else
      {
        transformTrackerCoords();
        resetTrackerState();
        if (trackerTarget.x != trackerX || trackerTarget.y != trackerY)
          setTranslationEffect(trackerX - trackerTarget.x,
              trackerY - trackerTarget.y, 0, 0);
        if (trackerTarget.width != trackerWidth || trackerTarget.height != trackerHeight)
          setZoomEffect(trackerWidth/trackerTarget.width, trackerHeight/trackerTarget.height,
              1, 1, trackerTarget.x, trackerTarget.y);
        trackerX = trackerTarget.x;
        trackerY = trackerTarget.y;
        trackerWidth = trackerTarget.width;
        trackerHeight = trackerTarget.height;
      }
      needsTrackerUpdate = false;
      needsStartup = true;
    }
    if (transitional && needsStartup)
    {
      needsStartup = false;
      wasActive = active = true;
      startTime = (currTime > 0) ? (currTime - 17) : currTime;
      positive = true;
      currProgress = getProgress(currTime);
      if (currTime == 0)
      {
        active = false;
        currProgress = positive ? 1 : 0;
        return fixableStartTime = false;
      }
      if (srcComp.getUIMgr().areEffectsEnabled())
        return fixableStartTime = true;
      else
      {
        active = false;
        return fixableStartTime = false;
      }
    }
    else if (transitional && needsStop)
    {
      needsStop = false;
      active = false;
      currProgress = 0;
      positive = false;
      return fixableStartTime = false;
    }
    else if (transitional && needsReverse)
    {
      float prog = loop ? getProgress(currTime) : 0;
      positive = !positive;
      needsReverse = false;
      if (active && startTime != 0)
      {
        if (!positive && (currTime - startTime) < preDelay)
        {
          // Terminate it because nothing has happened yet since we're before the delay
          currTime = 0;
          startTime = 0;
        }
        else
        {
          if (loop && currTime - startTime - preDelay > duration && currTime != 0)
          {
            startTime = currTime - preDelay - (long)((1.0f - prog)*duration);
          }
          else
            startTime = currTime - preDelay - (duration - (Math.max(0, currTime - startTime - preDelay))) - 17;
        }
      }
      else
        startTime = (currTime > 0) ? (currTime - 17) : currTime;
        if (currTime == 0)
        {
          active = false;
          currProgress = positive ? 1 : 0;
          return fixableStartTime = false;
        }
        else
        {
          wasActive = active = true;
          currProgress = getProgress(currTime);
          if (srcComp.getUIMgr().areEffectsEnabled())
            return fixableStartTime = true;
          else
          {
            active = false;
            return fixableStartTime = false;
          }
        }
    }
    else if (active)
    {
      currProgress = getProgress(currTime);
      if ((!loop || !positive || !srcComp.getUIMgr().areEffectsEnabled() || duration == 0) && currProgress == (positive ? 1 : 0))
        active = false;
      if (currTime == 0)
      {
        active = false;
        currProgress = positive ? 1 : 0;
      }
      return fixableStartTime = false;
    }
    else if (positive)
      currProgress = 1;
    else
      currProgress = 0;
    return fixableStartTime = false;
  }

  public void fixStartTime(long startDiff)
  {
    if (fixableStartTime)
      startTime += startDiff;
  }

  public synchronized boolean isActive()
  {
    return active || needsStartup || needsReverse || needsTrackerUpdate;
  }

  public synchronized boolean isPositive()
  {
    if (needsStartup)
      return true;
    if (needsStop)
      return false;
    if (needsReverse)
      return !positive;
    return positive;
  }

  // Returns true if the end state has no rendering effect
  public boolean isFinalStateNormal()
  {
    if ((effectType & EFFECT_FADE) != 0 && (startAlpha + deltaAlpha != 1))
      return false;
    if ((effectType & EFFECT_SCALE) != 0 && (startScaleX + deltaScaleX != 1 || startScaleY + deltaScaleY != 1))
      return false;
    if ((effectType & EFFECT_SLIDE) != 0 && (startTransX + deltaTransX != 0 || startTransY + deltaTransY != 0))
      return false;
    if ((effectType & EFFECT_ROTATEX) != 0 && ((startRotateX + deltaRotateX) % 360) != 0)
      return false;
    if ((effectType & EFFECT_ROTATEY) != 0 && ((startRotateY + deltaRotateY) % 360) != 0)
      return false;
    if ((effectType & EFFECT_ROTATEZ) != 0 && ((startRotateZ + deltaRotateZ) % 360) != 0)
      return false;
    return true;
  }

  // Returns true if this effect can be currently ignored because the effect its applying doesn't do anything
  public synchronized boolean isNoop()
  {
    if (disabled) return true;
    if (isActive() || trigger == TRIGGER_ITEMSELECTED) return false;
    if (!positive || isFinalStateNormal()) return true;
    return (trigger == TRIGGER_HIDDEN || (trigger == TRIGGER_UNFOCUSED && !wasActive));
  }

  // Returns true if this effect can cause an otherwise hidden component to be rendered
  public boolean isForceful()
  {
    return isActive() && (trigger == TRIGGER_HIDDEN || trigger == TRIGGER_UNFOCUSED || trigger == TRIGGER_VISIBLECHANGE ||
        (reversible && (!positive || needsReverse) && (trigger == TRIGGER_SHOWN || trigger == TRIGGER_FOCUSED)));
  }

  // Set this to true if the end state of the animation should be hidden
  public void setTargetHidden(boolean x)
  {
    hiddenTarget = x;
  }

  // Returns true if this effect is in a steady state which causes everything underneath it to be not rendered; either
  // through zero alpha or a zero scale factor; or being told that they're from a hidden situation where they should no longer render
  // when time is up
  public boolean isKiller()
  {
    if (isActive() || trigger == TRIGGER_STATIC)
      return false;
    return hiddenTarget;
  }

  public Widget getWidget()
  {
    return widg;
  }

  public boolean hasTranslateX() { return deltaTransX != 0; }
  public boolean hasTranslateY() { return deltaTransY != 0; }

  public boolean requiresCompletion()
  {
    return trigger == TRIGGER_MENUUNLOADED;
  }

  public boolean isTracking()
  {
    return trigger == TRIGGER_FOCUSTRACKER || trigger == TRIGGER_SMOOTHTRACKER;
  }

  public ZPseudoComp getSrcComp() { return srcComp; }
  public boolean isReversible() { return reversible; }
  public byte getTrigger() { return trigger; }

  public void setTrackerPosition(float x, float y, float w, float h)
  {
    trackerX = x;
    trackerY = y;
    trackerWidth = w;
    trackerHeight = h;
  }

  public EffectTracker setTrackerTarget(java.awt.geom.Rectangle2D.Float trackerTarget, EffectTracker oldTracker)
  {
    if (oldTracker != null || trackerTarget.x != trackerX || trackerTarget.y != trackerY || trackerTarget.width != trackerWidth || trackerTarget.height != trackerHeight)
    {
      if (oldTracker == null && isActive())
      {
        // Need to build a new EffectTracker object so we don't overwrite the state of the currently executing one
        EffectTracker newTracker = new EffectTracker(widg, srcComp, srcComp.relatedContext);
        newTracker.freshenTracker();
        newTracker.setInitialPositivity(false);
        newTracker.setPositivity(true);
        newTracker.needsTrackerUpdate = true;
        newTracker.trackerTarget = trackerTarget;
        newTracker.srcTrackerEffect = this;
        return newTracker;
      }
      needsTrackerUpdate = true;
    }
    this.trackerTarget = trackerTarget;
    srcTrackerEffect = oldTracker;
    return this;
  }

  public long getCompleteDuration() { return duration + preDelay + postDelay; }
  public long getStartTime() { return startTime; }
  public float getTrackerX() { return trackerX; }
  public float getTrackerY() { return trackerY; }
  public float getTrackerWidth() { return trackerWidth; }
  public float getTrackerHeight() { return trackerHeight; }

  public float getCurrProgress() { return currProgress; }
  public void freshenTracker() { freshTracker = true; }
  public void unfreshenTracker() { freshTracker = false; }
  public boolean isFreshTracker() { return freshTracker; }
  public boolean isLoop() { return loop; }

  public boolean isDisabled() { return disabled; }
  public void setDisabled(boolean x) { disabled = x; }

  public boolean isClipped() { return clipped; }
  public void setClipped(boolean x) { clipped = x; }
  // For SmoothTracker effects
  public Object getTrackerKey() { return trackerKey; }

  public String toString()
  {
    return "EffectTracker[" + widg + " nstrt=" + needsStartup + " nrv=" + needsReverse + " nstp=" + needsStop + " ntu=" + needsTrackerUpdate +
        " pos=" + positive + " act=" + active + " wact=" + wasActive + " prog=" + currProgress + " dis=" + disabled + "]";
  }

  private Widget widg;
  private ZPseudoComp srcComp;
  private long startTime;
  private long preDelay;
  private long postDelay;
  private long duration;
  private boolean needsStartup; // positive transition should be started; end in positive state
  private boolean needsReverse; // current transition should reverse direction
  private boolean needsStop; // current transition should be stopped; end in negative state
  private boolean needsTrackerUpdate; // redo tracker positioning
  private boolean positive; // true if the target state for the effect is true
  private boolean active; // true if the effect is in the process of executing.
  private boolean wasActive; // true if the effect was just active; used for knowing if focuslost effects should be applied when over
  private byte trigger;
  private byte easing;
  private byte timescale;
  private boolean reversible;
  private boolean loop;
  private float currProgress;
  private boolean hiddenTarget;
  // Whether or not conditionality has disabled this non-conditional Effect
  private boolean disabled;

  private int effectType;

  private float startTransX;
  private float startTransY;
  private float deltaTransX;
  private float deltaTransY;

  private float startScaleX = 1.0f;
  private float startScaleY = 1.0f;
  private float deltaScaleX;
  private float deltaScaleY;

  private float startRotateX;
  private float startRotateY;
  private float startRotateZ;
  private float deltaRotateZ;
  private float deltaRotateX;
  private float deltaRotateY;

  private float centerX;
  private float centerY;
  private float cameraOffsetX;
  private float cameraOffsetY;

  private float startAlpha = 1.0f;
  private float deltaAlpha;

  private float trackerX;
  private float trackerY;
  private float trackerWidth;
  private float trackerHeight;
  private boolean freshTracker;
  private EffectTracker srcTrackerEffect;
  private java.awt.geom.Rectangle2D.Float trackerTarget;

  private boolean fixableStartTime;
  private boolean clipped;
  private Object trackerKey;

  // These are used outside of here for performance reasons to know if we need to redo certain calculations
  public float lastWidthCalc;
  public float lastHeightCalc;
  public boolean virgin = true;
}
