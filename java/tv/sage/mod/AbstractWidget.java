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
package tv.sage.mod;

/**
 * Abstraction for all types of {@link sage.Widget} implementations.
 * <p>
 * Translations ONLY for:
 * <li>TEXT,ITEM name,static
 * <li>ACTION name,dynamic
 * <li>ATTRIBUTE value,dynamic
 * @author 601
 */
public abstract class AbstractWidget implements sage.Widget
{
  static final char EVAL_PREFIX = '=';
  // NOTE: Narflex - This code breaks the Proxy functionality that we never used anyways. But it
  // is a performance optimization to avoid having abstraction on high frequency method call
  protected byte type;
  public final byte type()
  {
    return (type);
  }
  public final boolean isType(byte intype)
  {
    return (intype == type);
  }

  /**
   * Create an AbstractWidget type-based subclass.
   * @param rawWidget
   * @return
   */
  public static AbstractWidget create(RawWidget rawWidget)
  {
    switch (rawWidget.type())
    {
      case ITEM:
      case TEXT:
      case ACTION:
        return (new Nomed(rawWidget));

      case ATTRIBUTE:
        return (new Attribute(rawWidget));

      case MENU:
      case CONDITIONAL:
      case BRANCH:
        return (new Named(rawWidget));

      case THEME:
      case PANEL:
      case SHAPE:
      case OPTIONSMENU:
      case TEXTINPUT:
      case TABLE:
      case TABLECOMPONENT:
      case VIDEO:
      case IMAGE:
      case EFFECT:
        return (new Valued(rawWidget));

      case LISTENER:
      case HOOK:
        return (new Simple(rawWidget));

      default:
        // 601 FIX
        //System.out.println("RawWidget " + rawWidget);
        //throw (new RuntimeException("TEMP"));
        return (new Proxy(rawWidget));
    }
  }

  /** @return Widget type */
  /*EMBEDDED_SWITCH/
    public abstract byte type();
/**/

  /** @return Widget name, untranslated */
  public abstract String name();

  /** @return null if no prop */
  protected abstract String getValue(final byte prop);
  protected abstract void setValue(final byte prop, final String value);

  /**
   * A Widget property value, type resolved.
   * <ul><li>A constant <CODE>Boolean</CODE>, <CODE>Integer</CODE>, <CODE>Float</CODE>,
   * <CODE>?Number</CODE>, <CODE>Number[]</CODE>, or <CODE>java.awt.Color</CODE>.
   * <li><CODE>String</CODE> indicates dynamic, where <CODE>value.equals(EVAL_PREFIX + resloved)</CODE>
   * <li><CODE>Void.TYPE</CODE> for NOP resolution (non dymanic String)
   * <li><CODE>null</CODE> for unresolved.</ul>
   */
  protected abstract Object getResolved(final byte prop);
  /** @see #getResolved(byte prop) */
  protected abstract void setResolved(final byte prop, final Object resolved);

  /** @return null if no props */
  public abstract String[] getPropertyValues();

  protected abstract int index();
  protected abstract AbstractWidget[] contentz();
  protected abstract AbstractWidget[] containerz();

  // 601 storage?
  protected int index;
  protected Module module;
  public String symbol;

  protected transient boolean highlite = false;
  protected transient int breakpoint = 0;
  protected transient byte hierarchy;
  protected transient long hierarchyTimestamp;

  AbstractWidget[] contentz = null;
  AbstractWidget[] containerz = null;
  int[] contentzIDs = null;
  int[] containerzIDs = null;

  /**
   * Set widget contents/containers (phase 2 of 2).
   * @param rawWidget
   * @param module
   */
  public void setCC(RawWidget rawWidget, Module mod, AbstractWidget[] awz)
  {
    module = mod;

    int[] curr = rawWidget.contentz();
    contentz = curr.length == 0 ? sage.Pooler.EMPTY_ABSTRACTWIDGET_ARRAY : new AbstractWidget[curr.length];
    for (int i = 0; i < contentz.length; i++)
    {
      contentz[i] = awz[curr[i]];
    }

    curr = rawWidget.containerz();
    containerz = curr.length == 0 ? sage.Pooler.EMPTY_ABSTRACTWIDGET_ARRAY : new AbstractWidget[curr.length];
    for (int i = 0; i < containerz.length; i++)
    {
      containerz[i] = awz[curr[i]];
    }
  }

  /**
   *
   * @return
   */
  public String symbol()
  {
    return (symbol);
  }

  /**
   *
   * @return
   */
  public String toString()
  {
    return ("" + getModule().name + ":" + symbol + "|" + TYPES[type()] + ":" + name());
  }

  /**
   * override for translation
   */
  public String getName()
  {
    return (name());
  }


  // property evaluations

  protected Object eval(String expr, sage.Catbert.Context evalContext, sage.ZPseudoComp uiContext) throws Exception
  {
    if (evalContext == null)
    {
      evalContext = (uiContext == null ? null : uiContext.getRelatedContext());
    }

    return (sage.Catbert.evaluateExpression(expr, evalContext, uiContext, this));
  }

  protected void resolve(byte prop, Class klass)
  {
    if (sage.Sage.DBG && getResolved(prop) != null)
    {
      System.out.println("Modules: resolve thrash " + PROPS[prop] + " for " + this);
    }

    String value = getValue(prop);

    if (value == null) return;

    Object resolved = null;

    if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX)
    {
      resolved = value.substring(1); // dynamic (instanceof String)
    }
    else if (klass == String.class)
    {
      resolved = Void.TYPE;
    }
    else if (klass == Boolean.class)
    {
      resolved = (value.equalsIgnoreCase("t") || value.equalsIgnoreCase("true")) ? Boolean.TRUE : Boolean.FALSE;
    }
    else if (klass == Integer.class)
    {
      try
      {
        resolved = new Integer(value);
      }
      catch (NumberFormatException nfx)
      {
        resolved = Integer.class;
      }
    }
    else if (klass == Float.class)
    {
      try
      {
        resolved = new Float(value);
      }
      catch (NumberFormatException nfx)
      {
        resolved = Float.class;
      }
    }
    else if (klass == Number.class)
    {
      try
      {
        if (value.indexOf('.') == -1)
        {
          resolved = (new Integer(value));
        }
        else
        {
          resolved = (new Float(value));
        }
      }
      catch (NumberFormatException nfx)
      {
        resolved = Number.class;
      }
    }
    else if (klass == java.awt.Color.class)
    {
      try
      {
        resolved = java.awt.Color.decode(value);
      }
      catch (NumberFormatException nfx)
      {
        resolved = java.awt.Color.class;
      }
    }

    setResolved(prop, resolved);
  }

  public boolean isDynamicProperty(byte prop)
  {
    String value = getValue(prop);
    return (value != null && value.length() > 0 && value.charAt(0) == '=');
  }

  /**
   * @return null only for ignored Exception, "" or a value
   */
  public String getStringProperty(byte prop, sage.Catbert.Context evalContext, sage.ZPseudoComp uiContext)
  {
    String value = getValue(prop);

    if (value == null) return ("");

    Object resolved = getResolved(prop);

    if (resolved == Void.TYPE)
    {
      return (value);
    }

    if (!(resolved instanceof String)) // !dynamic, unresolved
    {
      if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX)
      {
        resolved = value.substring(1);

        setResolved(prop, resolved);

        // drop thru
      }
      else // resolve String (Void.TYPE)
      {
        setResolved(prop, Void.TYPE);

        return (value);
      }
    }

    try // resolved dymanic
    {
      Object result = eval((String)resolved, evalContext, uiContext);

      if (result != null)
      {
        return (result.toString());
      }
      else
        return (""); // Changed on 7/16/04 because a lot of places depend on this returning non-null
    }
    catch (Exception x)
    {
      // 601 throw away?
      // 601 null or ""?
      return (null);
    }
  }

  public Object getObjectProperty(byte prop, sage.Catbert.Context evalContext, sage.ZPseudoComp uiContext)
  {
    String value = getValue(prop);

    if (value == null) return null;

    Object resolved = getResolved(prop);

    if (resolved == Void.TYPE)
    {
      return (value);
    }

    if (resolved == null) // !dynamic, unresolved
    {
      if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX)
      {
        resolved = value.substring(1);

        setResolved(prop, resolved);

        // drop thru
      }
      else // resolve String (Void.TYPE)
      {
        setResolved(prop, Void.TYPE);

        return (value);
      }
    }

    try // resolved dymanic
    {
      return eval((String)resolved, evalContext, uiContext);
    }
    catch (Exception x)
    {
      // 601 throw away?
      // 601 null or ""?
      return (null);
    }
  }

  /**
   *
   * @param prop
   * @param evalContext
   * @param uiContext
   * @return
   */
  public boolean getBooleanProperty(final byte prop, sage.Catbert.Context evalContext, sage.ZPseudoComp uiContext)
  {
    //        if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX)
    //        {
    //            try
    //            {
    //                return (sage.Catbert.evalBool(eval(value.substring(1), evalContext, uiContext)));
    //            }
    //            catch (Exception x)
    //            {
    //                return (false);
    //            }
    //        }
    //        else
    //        {
    //            return (value.equalsIgnoreCase("t") || value.equalsIgnoreCase("true"));
    //        }

    String value = getValue(prop);

    if (value == null) return (false);

    Object resolved = getResolved(prop);

    if (resolved instanceof Boolean)
    {
      return ((Boolean)resolved).booleanValue();
    }

    if (!(resolved instanceof String)) // !dynamic, unresolved
    {
      if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX) // resolve dynamic
      {
        resolved = value.substring(1);

        setResolved(prop, resolved);

        // drop thru
      }
      else // resolve Boolean
      {
        boolean bv = (value.equalsIgnoreCase("t") || value.equalsIgnoreCase("true"));

        setResolved(prop, bv ? Boolean.TRUE : Boolean.FALSE);

        return (bv);
      }
    }

    try // resolved dymanic
    {
      return (sage.Catbert.evalBool(eval((String)resolved, evalContext, uiContext)));
    }
    catch (Exception x)
    {
      return (false);
    }
  }

  /**
   *
   * @param prop
   * @param i
   * @param evalContext
   * @param uiContext
   * @return
   */
  public int getIntProperty(byte prop, int i, sage.Catbert.Context evalContext, sage.ZPseudoComp uiContext)
  {
    //        if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX)
    //        {
    //            try
    //            {
    //                Object result = eval(value.substring(1), evalContext, uiContext);
    //
    //                if (result instanceof Number)
    //                {
    //                    return ((Number)result).intValue();
    //                }
    //                else if (result != null)
    //                {
    //                    return Integer.parseInt(result.toString());
    //                }
    //                else
    //                    return (i);
    //            }
    //            catch (Exception e)
    //            {
    //                return (i);
    //            }
    //        }
    //        else // constant
    //        {
    //            try
    //            {
    //                return (Integer.parseInt(value));
    //            }
    //            catch (NumberFormatException e)
    //            {
    //                return (i);
    //            }
    //        }

    String value = getValue(prop);

    if (value == null) return (i);

    Object resolved = getResolved(prop);

    if (resolved instanceof Integer)
    {
      return ((Integer)resolved).intValue();
    }

    if (!(resolved instanceof String)) // !dynamic, unresolved
    {
      if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX) // resolve dynamic
      {
        resolved = value.substring(1);

        setResolved(prop, resolved);

        // drop thru
      }
      else // resolve Integer
      {
        try
        {
          Integer newI = new Integer(value);

          setResolved(prop, newI);

          return (newI.intValue());
        }
        catch (NumberFormatException e)
        {
          // 601 setResolved(prop, Error);

          return (i);
        }
      }
    }

    try // resolved dymanic
    {
      Object result = eval((String)resolved, evalContext, uiContext);

      if (result instanceof Number)
      {
        return ((Number)result).intValue();
      }
      else if (result != null)
      {
        return Integer.parseInt(result.toString());
      }
      else
      {
        return (i);
      }
    }
    catch (Exception e)
    {
      return (i);
    }
  }

  /**
   *
   * @param prop
   * @param evalContext
   * @param uiContext
   * @return
   */
  public Number getNumericProperty(byte prop, sage.Catbert.Context evalContext, sage.ZPseudoComp uiContext)
  {
    //        if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX)
    //        {
    //            try
    //            {
    //                Object result = eval(value.substring(1), evalContext, uiContext);
    //
    //                if (result instanceof Double)
    //                {
    //                    return new Float(((Double) result).floatValue());
    //                }
    //                else if (result instanceof Long)
    //                {
    //                    return new Integer(((Long) result).intValue());
    //                }
    //                else if (result instanceof Number)
    //                {
    //                    return (Number) result;
    //                }
    //                else if (result != null)
    //                {
    //                    String s = result.toString();
    //                    if (s.indexOf('.') == -1)
    //                    {
    //                        return new Integer(s);
    //                    }
    //                    else
    //                        return new Float(s);
    //                }
    //                else
    //                    return (null);
    //            }
    //            catch (Exception x)
    //            {
    //                return (null);
    //            }
    //        }
    //        else // constant
    //        {
    //            try
    //            {
    //                if (value.indexOf('.') == -1)
    //                {
    //                    return (new Integer(value));
    //                }
    //                else
    //                {
    //                    return (new Float(value));
    //                }
    //            }
    //            catch (NumberFormatException nfx)
    //            {
    //                return (null);
    //            }
    //        }

    String value = getValue(prop);

    if (value == null) return (null);

    Object resolved = getResolved(prop);

    if (resolved instanceof Integer || resolved instanceof Float)
    {
      return ((Number)resolved);
    }

    if (!(resolved instanceof String)) // !dynamic, unresolved
    {
      if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX) // resolve dynamic
      {
        resolved = value.substring(1);

        setResolved(prop, resolved);

        // drop thru
      }
      else // resolve Number
      {
        try
        {
          if (value.indexOf('.') == -1)
          {
            resolved = (new Integer(value));
          }
          else
          {
            resolved = (new Float(value));
          }

          setResolved(prop, resolved);

          return ((Number)resolved);
        }
        catch (NumberFormatException nfx)
        {
          // 601 setResolved(prop, Error);

          return (null);
        }
      }
    }

    try // resolved dymanic
    {
      Object result = eval((String)resolved, evalContext, uiContext);

      if (result instanceof Double)
      {
        return new Float(((Double) result).floatValue());
      }
      else if (result instanceof Long)
      {
        return new Integer(((Long) result).intValue());
      }
      else if (result instanceof Number)
      {
        return (Number) result;
      }
      else if (result != null)
      {
        String s = result.toString();
        if (s.indexOf('.') == -1)
        {
          return new Integer(s);
        }
        else
          return new Float(s);
      }
      else
        return (null);
    }
    catch (Exception x)
    {
      return (null);
    }
  }

  /**
   *
   * @param prop
   * @param f
   * @param evalContext
   * @param uiContext
   * @return
   */
  public float getFloatProperty(byte prop, float f, sage.Catbert.Context evalContext, sage.ZPseudoComp uiContext)
  {
    //        if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX)
    //        {
    //            try
    //            {
    //                Object result = eval(value.substring(1), evalContext, uiContext);
    //
    //                if (result instanceof Number)
    //                {
    //                    return ((Number) result).floatValue();
    //                }
    //                else if (result != null)
    //                {
    //                    return Float.parseFloat(result.toString());
    //                }
    //                else
    //                    return (f);
    //            }
    //            catch (Exception x)
    //            {
    //                return (f);
    //            }
    //        }
    //        else // constant
    //        {
    //            try
    //            {
    //                return (Float.parseFloat(value));
    //            }
    //            catch (NumberFormatException nfx)
    //            {
    //                return (f);
    //            }
    //        }

    String value = getValue(prop);

    if (value == null) return (f);

    Object resolved = getResolved(prop);

    if (resolved instanceof Float)
    {
      return ((Float)resolved).floatValue();
    }

    if (!(resolved instanceof String)) // !dynamic, unresolved
    {
      if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX) // resolve dynamic
      {
        resolved = value.substring(1);

        setResolved(prop, resolved);

        // drop thru
      }
      else // resolve Float
      {
        try
        {
          Float newF = new Float(value);

          setResolved(prop, newF);

          return (newF.floatValue());
        }
        catch (NumberFormatException nfx)
        {
          // 601 setResolved(prop, Error);

          return (f);
        }
      }
    }

    try // resolved dymanic
    {
      Object result = eval((String)resolved, evalContext, uiContext);

      if (result instanceof Number)
      {
        return ((Number) result).floatValue();
      }
      else if (result != null)
      {
        return Float.parseFloat(result.toString());
      }
      else
        return (f);
    }
    catch (Exception x)
    {
      return (f);
    }
  }

  /**
   *
   * @param prop
   * @param evalContext
   * @param uiContext
   * @return
   */
  public java.awt.Color getColorProperty(byte prop, sage.Catbert.Context evalContext, sage.ZPseudoComp uiContext)
  {
    //        if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX)
    //        {
    //            try
    //            {
    //                Object result = eval(value.substring(1), evalContext, uiContext);
    //
    //                if (result instanceof java.awt.Color)
    //                {
    //                    return (java.awt.Color) result;
    //                }
    //                else if (result != null)
    //                {
    //                    try
    //                    {
    //                        return (java.awt.Color.decode(result.toString()));
    //                    }
    //                    catch (NumberFormatException nfx)
    //                    {
    //                        return (null);
    //                    }
    //                }
    //                else
    //                    return (null);
    //            }
    //            catch (Exception x)
    //            {
    //                return (null);
    //            }
    //        }
    //        else // constant
    //        {
    //            try
    //            {
    //                return (java.awt.Color.decode(value));
    //            }
    //            catch (NumberFormatException nfx)
    //            {
    //                return (null);
    //            }
    //        }

    String value = getValue(prop);

    if (value == null) return (null);

    Object resolved = getResolved(prop);

    if (resolved instanceof java.awt.Color)
    {
      return ((java.awt.Color)resolved);
    }

    if (!(resolved instanceof String)) // !dynamic, unresolved
    {
      if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX) // resolve dynamic
      {
        resolved = value.substring(1);

        setResolved(prop, resolved);

        // drop thru
      }
      else // resolve java.awt.Color
      {
        try
        {
          java.awt.Color newC = java.awt.Color.decode(value);

          setResolved(prop, newC);

          return (newC);
        }
        catch (NumberFormatException nfx)
        {
          // 601 setResolved(prop, Error);

          return (null);
        }
      }
    }

    try // resolved dymanic
    {
      Object result = eval((String)resolved, evalContext, uiContext);

      if (result instanceof java.awt.Color)
      {
        return (java.awt.Color) result;
      }
      else if (result != null)
      {
        try
        {
          return (java.awt.Color.decode(result.toString()));
        }
        catch (NumberFormatException nfx)
        {
          return (null);
        }
      }
      else
        return (null);
    }
    catch (Exception x)
    {
      return (null);
    }
  }

  /**
   *
   * @param prop
   * @param evalContext
   * @param uiContext
   * @return
   */
  public int getIntColorProperty(byte prop, int i, sage.Catbert.Context evalContext, sage.ZPseudoComp uiContext)
  {

    String value = getValue(prop);

    if (value == null) return i;

    Object resolved = getResolved(prop);

    if (resolved instanceof Integer)
    {
      return ((Integer)resolved).intValue();
    }

    if (!(resolved instanceof String)) // !dynamic, unresolved
    {
      if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX) // resolve dynamic
      {
        resolved = value.substring(1);

        setResolved(prop, resolved);

        // drop thru
      }
      else // resolve java.awt.Color
      {
        try
        {
          int newC = Integer.parseInt(value.startsWith("0x") ? value.substring(2) : value, 16);

          setResolved(prop, new Integer(newC));

          return (newC);
        }
        catch (NumberFormatException nfx)
        {
          // 601 setResolved(prop, Error);

          return i;
        }
      }
    }

    try // resolved dymanic
    {
      Object result = eval((String)resolved, evalContext, uiContext);

      if (result instanceof Number)
      {
        return ((Number) result).intValue();
      }
      else if (result != null)
      {
        try
        {
          String resultStr = result.toString();
          return (Integer.parseInt(resultStr.startsWith("0x") ? resultStr.substring(2) : resultStr, 16));
        }
        catch (NumberFormatException nfx)
        {
          return i;
        }
      }
      else
        return i;
    }
    catch (Exception x)
    {
      return i;
    }
  }


  // property misc

  /**
   *
   * @param prop
   * @return
   */
  public Number[] getNumericArrayProperty(byte prop, sage.Catbert.Context evalContext, sage.ZPseudoComp uiContext)
  {
    String value = getValue(prop);

    if (value == null) return (null);

    Object resolved = getResolved(prop);

    if (resolved instanceof Number[])
    {
      return ((Number[])resolved);
    }

    // unresolved


    if (!(resolved instanceof String)) // !dynamic, unresolved
    {
      if (value.length() > 0 && value.charAt(0) == EVAL_PREFIX) // resolve dynamic
      {
        resolved = value.substring(1);

        setResolved(prop, resolved);

        // drop thru
      }
      else // resolve numeric array
      {
        java.util.StringTokenizer st = new java.util.StringTokenizer(value, ", ");
        Number[] na = new Number[st.countTokens()];
        for (int i = 0; i < na.length; i++)
        {
          String t = st.nextToken();

          try
          {
            if (t.indexOf('.') == -1)
            {
              na[i] = new Integer(t);
            }
            else
            {
              na[i] = new Float(t);
            }
          }
          catch (NumberFormatException e)
          {
            return null;
          }
        }
        setResolved(prop, na);
        return na;
      }
    }

    try // resolved dymanic
    {
      Object result = eval((String)resolved, evalContext, uiContext);

      if (result instanceof Object[])
      {
        Object[] resArr = (Object[]) result;
        Number[] rv = new Number[resArr.length];
        for (int i = 0; i < resArr.length; i++)
        {
          if (resArr[i] instanceof Number)
            rv[i] = (Number)resArr[i];
          else
          {
            try
            {
              String s = resArr[i].toString();
              if (s.indexOf('.') != -1)
                rv[i] = new Float(s);
              else
                rv[i] = new Integer(s);
            }
            catch (NumberFormatException e)
            {
              System.out.println("BAD value in numeric array:" + e);
            }
          }
        }
        return rv;
      }
      else
        return (null);
    }
    catch (Exception x)
    {
      return (null);
    }
  }

  /**
   * @param prop
   * @return ORIGINAL raw String value or "", never null
   */
  public String getProperty(byte prop)
  {
    String value = getValue(prop);

    return (value == null ? "" : value);
  }

  /**
   *
   * @return
   */
  public boolean tempHighlight()
  {
    return (highlite);
  }

  /**
   *
   * @return
   */
  public int getBreakpointMask()
  {
    return (breakpoint);
  }


  // xml

  /**
   *
   * @return
   */
  public int[] getContainerz()
  {
    throw new UnsupportedOperationException();
  }

  /**
   *
   * @return
   */
  public int[] getContentz()
  {
    throw new UnsupportedOperationException();
  }

  /**
   *
   * @return
   */
  public java.util.Vector getPropv()
  {
    throw new UnsupportedOperationException();
  }


  // misc

  /**
   *
   * @return
   */
  public String getUntranslatedName()
  {
    return (name());
  }

  /**
   *
   * @param w
   * @return
   */
  public boolean isNameTypeMatch(sage.Widget w)
  {
    return (w.isType(type()) && w.getUntranslatedName().equals(name()));
  }

  /**
   *
   * @param prop
   * @return
   */
  public boolean hasProperty(byte prop)
  {
    return (getValue(prop) != null);
  }

  /**
   *
   * @param w
   * @return
   */
  public boolean isIdenticalProperties(sage.Widget w)
  {
    if (!w.isType(type())) return false;

    String[] thez = getPropertyValues();
    String[] thoz = ((AbstractWidget)w).getPropertyValues();

    if (thez == thoz) return (true); // both have no props
    if (thez == null || thoz == null)
    {
      if ((thez == null && thoz.length > 0) || (thoz == null && thez.length > 0))
        return false;
      else
        return true;
    }

    for (int i = 0; i < PROPS.length; i++)
    {
      if (thez[i] == thoz[i]) continue; // both hava no prop or very equal

      if (thez[i] == null || thoz[i] == null || !thez[i].equals(thoz[i])) return (false);
    }

    return (true);

    //        java.util.Properties props0 = properties();
    //        java.util.Properties props1 = aw.properties();
    //
    //        for (int i = 0; i < PROPS.length; i++)
    //        {
    //            String v0 = props0.getProperty(PROPS[i]);
    //            String v1 = props1.getProperty(PROPS[i]);
    //
    //            if (v0 == v1) continue; // both null or very equal
    //
    //            if (v0 == null || !v0.equals(v1)) return (false);
    //        }
    //
    //        return (true);

    //        WidgetProperty[] wprops = ((WidgetImp)w).props;
    //		if (wprops != props && (wprops == null || props == null)) return false;
    //		if (wprops == props) return true;
    //		for (int i = 0; i < props.length; i++)
    //		{
    //			if (props[i] == wprops[i]) continue;
    //			if (props[i] == null || wprops[i] == null) return false;
    //			if (!props[i].untransValue.equals(wprops[i].untransValue))
    //				return false;
    //		}
    //		return true;
  }


  // hierarchy (SHAPE, UI, ProcessChain)

  /*
   * VERY IMPORTANT
   * PROCESS CHAIN AND UI HIERARCHY ARE MUTUALLY EXCLUSIVE, WIDGETS CAN ALSO BE IN NEITHER
   * Menus are an exception and are in both
   */


  /**
   *
   * @param type
   * @return
   */
  /*EMBEDDED_SWITCH/
    public boolean isType(byte type)
    {
        return (type() == type);
    }
/**/

  public boolean isProcessChainType()
  {
    byte wt = type();
    return ACTION == wt || MENU == wt ||
        CONDITIONAL == wt || OPTIONSMENU == wt ||
        BRANCH == wt;
  }

  public boolean isUIComponent()
  {
    byte wt = type();
    return PANEL == wt || TEXT == wt || TEXTINPUT == wt ||
        IMAGE == wt || TABLECOMPONENT == wt ||
        TABLE == wt || ITEM == wt || VIDEO == wt;
  }

  private static final byte UI_HIERARCHY_MASK = 1;
  private static final byte PROCESS_HIERARCHY_MASK = 2;
  private static final byte SHAPE_HIERARCHY_MASK = 4;
  private static final byte EFFECT_HIERARCHY_MASK = 8;

  /*
   * VERY IMPORTANT
   * PROCESS CHAIN AND UI HIERARCHY ARE MUTUALLY EXCLUSIVE, WIDGETS CAN ALSO BE IN NEITHER
   * Menus are an exception and are in both
   */
  private void determineHierarchy()
  {
    if (hierarchyTimestamp < getModule().lastModified())
    {
      hierarchyTimestamp = getModule().lastModified();
      hierarchy = 0;
      byte wt = type();
      if (isUIComponent()/* || SHAPE == type()*/)
      {
        hierarchy = (byte) (hierarchy | UI_HIERARCHY_MASK);
      }
      else if (ACTION == wt || CONDITIONAL == wt || BRANCH == wt)
      {
        java.util.Set so = new java.util.HashSet();
        sage.Widget[] kids = contents();
        for (int i = 0; i < kids.length; i++)
        {
          if (((AbstractWidget)kids[i]).isInUIHierarchy(so))
          {
            hierarchy = (byte) (hierarchy | UI_HIERARCHY_MASK);
            break;
          }
        }
      }
      if (isInShapeHierarchy(new java.util.HashSet()))
        hierarchy = (byte) (hierarchy | SHAPE_HIERARCHY_MASK);
      if (isInEffectHierarchy(new java.util.HashSet()))
        hierarchy = (byte) (hierarchy | EFFECT_HIERARCHY_MASK);
      if (MENU == wt)
        hierarchy = (byte) (hierarchy | PROCESS_HIERARCHY_MASK);
      else if (((hierarchy & UI_HIERARCHY_MASK) == 0) && isProcessChainType())
      {
        // We're only in the process hierarchy if we've got a parent that can get fired somehow
        if (isInUpwardProcessHierarchy(new java.util.HashSet()))
        {
          hierarchy = (byte) (hierarchy | PROCESS_HIERARCHY_MASK);
        }
      }
    }
  }

  public boolean isInProcessChain()
  {
    determineHierarchy();
    return (hierarchy & PROCESS_HIERARCHY_MASK) != 0;
  }

  public boolean isInUIHierarchy()
  {
    determineHierarchy();
    return (hierarchy & UI_HIERARCHY_MASK) != 0;
  }

  private boolean isInUIHierarchy(java.util.Set ignoreUs)
  {
    byte wt = type();
    if (isUIComponent() || SHAPE == wt || EFFECT == wt) return true;
    if (ACTION == wt || CONDITIONAL == wt || BRANCH == wt)
    {
      if (!ignoreUs.add(this)) return false;
      sage.Widget[] kids = contents();
      for (int i = 0; i < kids.length; i++)
      {
        if (((AbstractWidget)kids[i]).isInUIHierarchy(ignoreUs))
          return true;
      }
    }
    return false;
  }

  private boolean isInUpwardProcessHierarchy(java.util.Set ignoreUs)
  {
    if (!ignoreUs.add(this)) return false;
    sage.Widget[] parents = containers();
    for (int i = 0; i < parents.length; i++)
    {
      byte parentType = parents[i].type();
      if (parentType == ITEM || parentType == IMAGE || parentType == HOOK ||
          parentType == LISTENER)
        return true;
      if (parentType == ACTION || parentType == CONDITIONAL || parentType == BRANCH)
      {
        if (((AbstractWidget)parents[i]).isInUpwardProcessHierarchy(ignoreUs))
          return true;
      }
    }
    return false;
  }

  public boolean isInShapeHierarchy()
  {
    determineHierarchy();
    return (hierarchy & SHAPE_HIERARCHY_MASK) != 0;
  }

  public boolean isInEffectHierarchy()
  {
    determineHierarchy();
    return (hierarchy & EFFECT_HIERARCHY_MASK) != 0;
  }

  private boolean isInShapeHierarchy(java.util.Set ignoreUs)
  {
    byte wt = type();
    if (SHAPE == wt) return true;
    if (ACTION == wt || CONDITIONAL == wt || BRANCH == wt)
    {
      if (!ignoreUs.add(this)) return false;
      sage.Widget[] kids = contents();
      for (int i = 0; i < kids.length; i++)
      {
        if (((AbstractWidget)kids[i]).isInShapeHierarchy(ignoreUs))
          return true;
      }
    }
    return false;
  }

  private boolean isInEffectHierarchy(java.util.Set ignoreUs)
  {
    byte wt = type();
    if (EFFECT == wt) return true;
    if (ACTION == wt || CONDITIONAL == wt || BRANCH == wt)
    {
      if (!ignoreUs.add(this)) return false;
      sage.Widget[] kids = contents();
      for (int i = 0; i < kids.length; i++)
      {
        if (((AbstractWidget)kids[i]).isInEffectHierarchy(ignoreUs))
          return true;
      }
    }
    return false;
  }

  /**
   * override in Proxies
   * @return
   */
  public boolean isProxy()
  {
    return (false);
  }


  // studio

  /** override in translated subclasses */
  public void retranslate()
  {
  }

  /**
   * @return the first property name that matches on the raw ORIGINAL value, null if none match
   */
  public String searchPropertyValues(String srch, boolean fullMatch)
  {
    //        java.util.regex.Pattern regexPattern =
    //            (fullMatch ? null : java.util.regex.Pattern.compile(srch, java.util.regex.Pattern.CASE_INSENSITIVE));

    //        java.util.Iterator esi = properties().entrySet().iterator();
    //        while (esi.hasNext())
    //        {
    //            java.util.Map.Entry me = (java.util.Map.Entry)esi.next();
    //
    //            if (fullMatch)
    //            {
    //                if (((String)me.getValue()).equals(srch))
    //                {
    //                    return ((String)me.getKey());
    //                }
    //            }
    //            else
    //            {
    //                if (((String)me.getValue()).indexOf(srch) != -1)
    //                {
    //                    return ((String)me.getKey());
    //                }
    //            }
    //        }

    String[] pvz = getPropertyValues();

    if (pvz != null && srch != null)
    {
      for (int i = 0; i < sage.Widget.PROPS.length; i++)
      {
        if (pvz[i] == null) continue;

        if (fullMatch)
        {
          if (pvz[i].equals(srch))
          {
            return (sage.Widget.PROPS[i]);
          }
        }
        else
        {
          if (pvz[i].indexOf(srch) != -1)
          {
            return (sage.Widget.PROPS[i]);
          }
        }
      }
    }

    return (null);
  }

  /**
   *
   * @param enlighten
   */
  public void tempHighlight(boolean enlighten)
  {
    highlite = enlighten;
  }

  /**
   *
   * @param x
   */
  public void setBreakpointMask(int x)
  {
    breakpoint = x;
  }

  /**
   *
   * @param name
   */
  public void setName(String name)
  {
    if (name() == name) return;

    if (name != null && name.equals(name())) return;

    // 601 heat Module
    if (this instanceof GenericWidget)
    {
      GenericWidget gw = (GenericWidget)this;

      // 601 debug
      //            if (sage.Sage.DBG) Log.ger.fine(gw.toString() + ".setname{" + name + "}");

      gw.nameProperty = name;

      getModule().setChanged();
    }
    else if (this instanceof Proxy)
    {
      if (sage.Sage.DBG) System.out.println("Modules: Proxy ignoring {" + name() + "}.setname{" + name + "}");

      //            // 601 more proxies?
      //            GenericWidget gw = (GenericWidget)((Proxy)this).actual;
      //
      //            gw.nameProperty = name;
    }
    else
    {
      if (sage.Sage.DBG) System.out.println("Modules: ignoring {" + name() + "}.setname{" + name + "}");

      // 601
      throw new UnsupportedOperationException();
    }
  }

  /**
   * @param prop
   * @param value null to remove, (length == 0 is treated as null)
   */
  public void setProperty(byte prop, String value)
  {
    // 601 heat Module
    if (this instanceof GenericWidget)
    {
      if (value != null && value.length() == 0) value = null;

      String original = getValue(prop);

      // 601 debug
      //            if (sage.Sage.DBG) Log.ger.fine(toString() + ".setProperty{" + sage.Widget.PROPS[prop] + ", {" + value + "}} was [" + original + "]");

      if (value == original) return;

      setValue(prop, value);

      // 601 remove?!?

      //            GenericWidget gw = (GenericWidget)this;
      //
      //            String oldValue = gw.properties.getProperty(sage.Widget.PROPS[prop]);
      //
      //            // 601 debug
      //            if (sage.Sage.DBG) Log.ger.fine(gw.toString() + ".setProperty{" + prop + ", {" + value + "}} was [" + oldValue + "]");
      //
      //            if (value == null)
      //            {
      //                if (oldValue == null) return;
      //
      //                gw.properties.remove(sage.Widget.PROPS[prop]);
      //            }
      //            else
      //            {
      //                gw.properties.setProperty(sage.Widget.PROPS[prop], value);
      //            }

      module.setChanged();
    }
    else if (this instanceof Proxy)
    {
      if (sage.Sage.DBG) System.out.println("Modules: Proxy ignoring {" + name() + "}.setProperty{" + prop + ", {" + value + "}}");

      //            // 601 more proxies?
      //            GenericWidget gw = (GenericWidget)((Proxy)this).actual;
      //
      //            gw.properties.setProperty(tv.sage.Widget.PROPS[prop], value);
    }
    else
    {
      if (sage.Sage.DBG) System.out.println("Modules: ignoring {" + name() + "}.setProperty{" + prop + ", {" + value + "}}");

      // 601
      throw new UnsupportedOperationException("setProperty");
    }
  }

  /**
   *
   * @param prop
   * @return
   */
  public String getTempProperty(String prop)
  {
    // never return null, "" instead

    java.util.Properties tps = (java.util.Properties)tempPropertyMap.get(this);

    if (tps == null) return ("");

    String value = tps.getProperty(prop);

    return (value == null ? "" : value);
  }

  /**
   *
   * @param prop
   * @param value
   */
  public void setTempProperty(String prop, String value)
  {
    java.util.Properties tps = (java.util.Properties)tempPropertyMap.get(this);

    if (tps == null) // first time
    {
      tps = new java.util.Properties();

      tempPropertyMap.put(this, tps);
    }

    tps.setProperty(prop, value);
  }

  /** of (AbstractWidget, java.util.Properties) */
  private static java.util.Map tempPropertyMap = new java.util.WeakHashMap();


  // contents

  /**
   *
   * @return
   */
  public sage.Widget[] contents()
  {
    return (contentz());
  }

  /**
   *
   * @param type
   * @return
   */
  public sage.Widget[] contents(byte type)
  {
    int numRV = 0;
    AbstractWidget[] contentz = contentz();
    for (int i = 0; i < contentz.length; i++)
      if (contentz[i].isType(type))
        numRV++;
    if (contentz.length == numRV) return contentz;
    sage.Widget[] rv = new sage.Widget[numRV];
    numRV = 0;
    for (int i = 0; i < contentz.length; i++)
      if (contentz[i].isType(type))
        rv[numRV++] = contentz[i];

    return rv;
  }

  public int getChildIndex(sage.Widget w)
  {
    sage.Widget[] kids = contents();
    for (int i = 0; i < kids.length; i++)
      if (kids[i] == w)
        return i;
    return -1;
  }

  /**
   *
   * @param w
   * @return
   */
  public boolean contains(sage.Widget w)
  {
    AbstractWidget[] contentz = contentz();
    for (int i = 0; i < contentz.length; i++)
    {
      if (contentz[i] == w) return (true);
    }

    return (false);
  }

  /**
   *
   * @param type
   * @param name
   * @return
   */
  public sage.Widget contentsSingularName(byte type, String name)
  {
    AbstractWidget[] conz = contentz();
    for (int i = 0; i < conz.length; i++)
    {
      if (conz[i].type() == type && conz[i].getUntranslatedName().equals(name))
      {
        return (conz[i]);
      }
    }

    return null;
  }

  /**
   *
   * @param w
   * @return
   */
  public boolean willContain(sage.Widget w)
  {
    return isRelationshipAllowed(type(), w.type());
  }

  private static final java.util.Map validRelationshipMap;
  static
  {
    validRelationshipMap = new java.util.HashMap();

    java.util.Set set = new java.util.HashSet();
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(TEXT));
    set.add(new Byte(IMAGE));
    set.add(new Byte(TEXTINPUT));
    set.add(new Byte(PANEL));
    set.add(new Byte(THEME));
    set.add(new Byte(ACTION));
    set.add(new Byte(LISTENER));
    set.add(new Byte(TABLE));
    set.add(new Byte(ITEM));
    set.add(new Byte(VIDEO));
    set.add(new Byte(ATTRIBUTE));
    set.add(new Byte(SHAPE));
    set.add(new Byte(HOOK));
    set.add(new Byte(EFFECT));
    validRelationshipMap.put(new Byte(MENU), set);

    set = new java.util.HashSet();
    set.add(new Byte(MENU));
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(TEXT));
    set.add(new Byte(IMAGE));
    set.add(new Byte(TEXTINPUT));
    set.add(new Byte(PANEL));
    set.add(new Byte(ACTION));
    set.add(new Byte(TABLE));
    set.add(new Byte(ITEM));
    set.add(new Byte(VIDEO));
    set.add(new Byte(SHAPE));
    set.add(new Byte(OPTIONSMENU));
    set.add(new Byte(EFFECT));
    validRelationshipMap.put(new Byte(BRANCH), set);

    set = new java.util.HashSet();
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(BRANCH));
    set.add(new Byte(MENU));
    set.add(new Byte(TEXT));
    set.add(new Byte(IMAGE));
    set.add(new Byte(TEXTINPUT));
    set.add(new Byte(PANEL));
    set.add(new Byte(ACTION));
    set.add(new Byte(TABLE));
    set.add(new Byte(ITEM));
    set.add(new Byte(VIDEO));
    set.add(new Byte(SHAPE));
    set.add(new Byte(OPTIONSMENU));
    set.add(new Byte(EFFECT));
    validRelationshipMap.put(new Byte(CONDITIONAL), set);

    set = new java.util.HashSet();
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(ACTION));
    set.add(new Byte(THEME));
    set.add(new Byte(ATTRIBUTE));
    set.add(new Byte(SHAPE));
    set.add(new Byte(LISTENER));
    set.add(new Byte(HOOK));
    set.add(new Byte(EFFECT));
    validRelationshipMap.put(new Byte(TEXT), set);

    set = new java.util.HashSet();
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(MENU));
    set.add(new Byte(ACTION));
    set.add(new Byte(THEME));
    set.add(new Byte(ATTRIBUTE));
    set.add(new Byte(OPTIONSMENU));
    set.add(new Byte(SHAPE));
    set.add(new Byte(LISTENER));
    set.add(new Byte(HOOK));
    set.add(new Byte(EFFECT));
    validRelationshipMap.put(new Byte(IMAGE), set);

    set = new java.util.HashSet();
    set.add(new Byte(THEME));
    set.add(new Byte(ATTRIBUTE));
    set.add(new Byte(SHAPE));
    set.add(new Byte(HOOK));
    set.add(new Byte(EFFECT));
    set.add(new Byte(LISTENER));
    validRelationshipMap.put(new Byte(TEXTINPUT), set);

    set = new java.util.HashSet();
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(TEXT));
    set.add(new Byte(IMAGE));
    set.add(new Byte(TEXTINPUT));
    set.add(new Byte(PANEL));
    set.add(new Byte(THEME));
    set.add(new Byte(ACTION));
    set.add(new Byte(LISTENER));
    set.add(new Byte(TABLE));
    set.add(new Byte(ITEM));
    set.add(new Byte(VIDEO));
    set.add(new Byte(ATTRIBUTE));
    set.add(new Byte(SHAPE));
    set.add(new Byte(HOOK));
    set.add(new Byte(EFFECT));
    validRelationshipMap.put(new Byte(PANEL), set);

    set = new java.util.HashSet();
    set.add(new Byte(TABLE));
    set.add(new Byte(TABLECOMPONENT));
    set.add(new Byte(ITEM));
    set.add(new Byte(OPTIONSMENU));
    set.add(new Byte(TEXT));
    set.add(new Byte(TEXTINPUT));
    set.add(new Byte(PANEL));
    set.add(new Byte(MENU));
    set.add(new Byte(LISTENER));
    set.add(new Byte(IMAGE));
    set.add(new Byte(ATTRIBUTE));
    validRelationshipMap.put(new Byte(THEME), set);

    set = new java.util.HashSet();
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(MENU));
    set.add(new Byte(TEXT));
    set.add(new Byte(IMAGE));
    set.add(new Byte(TEXTINPUT));
    set.add(new Byte(PANEL));
    set.add(new Byte(ACTION));
    set.add(new Byte(TABLE));
    set.add(new Byte(TABLECOMPONENT));
    set.add(new Byte(ITEM));
    set.add(new Byte(OPTIONSMENU));
    set.add(new Byte(SHAPE));
    set.add(new Byte(EFFECT));
    validRelationshipMap.put(new Byte(ACTION), set);

    set = new java.util.HashSet();
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(TEXT));
    set.add(new Byte(PANEL));
    set.add(new Byte(TEXTINPUT));
    set.add(new Byte(IMAGE));
    set.add(new Byte(ACTION));
    set.add(new Byte(ITEM));
    set.add(new Byte(ATTRIBUTE));
    set.add(new Byte(THEME));
    set.add(new Byte(LISTENER));
    set.add(new Byte(TABLE));
    set.add(new Byte(SHAPE));
    set.add(new Byte(HOOK));
    set.add(new Byte(VIDEO));
    set.add(new Byte(EFFECT));
    validRelationshipMap.put(new Byte(OPTIONSMENU), set);

    set = new java.util.HashSet();
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(MENU));
    set.add(new Byte(OPTIONSMENU));
    set.add(new Byte(ACTION));
    validRelationshipMap.put(new Byte(LISTENER), set);

    set = new java.util.HashSet();
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(TEXT));
    set.add(new Byte(IMAGE));
    set.add(new Byte(TEXTINPUT));
    set.add(new Byte(PANEL));
    set.add(new Byte(THEME));
    set.add(new Byte(ACTION));
    set.add(new Byte(LISTENER));
    set.add(new Byte(TABLECOMPONENT));
    set.add(new Byte(ITEM));
    set.add(new Byte(ATTRIBUTE));
    set.add(new Byte(VIDEO));
    set.add(new Byte(SHAPE));
    set.add(new Byte(HOOK));
    set.add(new Byte(EFFECT));
    validRelationshipMap.put(new Byte(TABLE), set);

    // NOTE: Conditionals/Actions underneath a table component won't work right w/ the way we generate cells
    set = new java.util.HashSet();
    //set.add(new Byte(CONDITIONAL));
    set.add(new Byte(TEXT));
    set.add(new Byte(IMAGE));
    set.add(new Byte(TEXTINPUT));
    set.add(new Byte(PANEL));
    set.add(new Byte(THEME));
    //set.add(new Byte(ACTION));
    set.add(new Byte(LISTENER));
    set.add(new Byte(ITEM));
    set.add(new Byte(ATTRIBUTE));
    set.add(new Byte(VIDEO));
    set.add(new Byte(SHAPE));
    set.add(new Byte(HOOK));
    set.add(new Byte(EFFECT));
    validRelationshipMap.put(new Byte(TABLECOMPONENT), set);

    set = new java.util.HashSet();
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(MENU));
    set.add(new Byte(TEXT));
    set.add(new Byte(IMAGE));
    set.add(new Byte(TEXTINPUT));
    set.add(new Byte(PANEL));
    set.add(new Byte(THEME));
    set.add(new Byte(ACTION));
    set.add(new Byte(OPTIONSMENU));
    set.add(new Byte(LISTENER));
    set.add(new Byte(TABLE));
    set.add(new Byte(ITEM));
    set.add(new Byte(VIDEO));
    set.add(new Byte(ATTRIBUTE));
    set.add(new Byte(SHAPE));
    set.add(new Byte(HOOK));
    set.add(new Byte(EFFECT));
    validRelationshipMap.put(new Byte(ITEM), set);

    set = new java.util.HashSet();
    set.add(new Byte(HOOK));
    set.add(new Byte(LISTENER));
    validRelationshipMap.put(new Byte(VIDEO), set);

    set = new java.util.HashSet();
    validRelationshipMap.put(new Byte(ATTRIBUTE), set);

    set = new java.util.HashSet();
    validRelationshipMap.put(new Byte(SHAPE), set);

    set = new java.util.HashSet();
    set.add(new Byte(CONDITIONAL));
    set.add(new Byte(MENU));
    set.add(new Byte(ACTION));
    set.add(new Byte(OPTIONSMENU));
    validRelationshipMap.put(new Byte(HOOK), set);

    set = new java.util.HashSet();
    validRelationshipMap.put(new Byte(EFFECT), set);
  }

  /**
   *
   * @param parentType
   * @param childType
   * @return
   */
  public static boolean isRelationshipAllowed(byte parentType, byte childType)
  {
    java.util.Set childSet = (java.util.Set) validRelationshipMap.get(new Byte(parentType));
    return (childSet != null) && childSet.contains(new Byte(childType));
  }

  //    public void contain(sage.Widget w)
  //    {
  //        throw new UnsupportedOperationException("Wimp");
  //    }
  //
  //    public void contain(sage.Widget w, int index)
  //    {
  //        throw new UnsupportedOperationException("Wimp");
  //    }
  //
  //    public int discontent(sage.Widget w)
  //    {
  //        throw new UnsupportedOperationException("Wimp");
  //    }


  // containers

  /**
   *
   * @return
   */
  public int numContainers()
  {
    return (containerz().length);
  }

  /**
   *
   * @param type
   * @return
   */
  public int numContainers(byte type)
  {
    int count = 0;

    AbstractWidget[] containerz = containerz();
    for (int i = 0; i < containerz.length; i++)
    {
      if (containerz[i].isType(type)) count++;
    }

    return (count);
  }

  /**
   *
   * @return
   */
  public sage.Widget[] containers()
  {
    return (containerz());
  }

  /**
   *
   * @param type
   * @return
   */
  public sage.Widget[] containers(byte type)
  {
    int numRV = 0;
    AbstractWidget[] containerz = containerz();
    for (int i = 0; i < containerz.length; i++)
      if (containerz[i].isType(type))
        numRV++;
    if (numRV == containerz.length) return containerz;
    sage.Widget[] rv = new sage.Widget[numRV];
    numRV = 0;
    for (int i = 0; i < containerz.length; i++)
      if (containerz[i].isType(type))
        rv[numRV++] = containerz[i];

    return rv;
  }

  /**
   *
   * @param w
   * @return
   */
  public boolean isContainer(sage.Widget w) // == w.contains(this)
  {
    AbstractWidget[] containerz = containerz();
    for (int i = 0; i < containerz.length; i++)
    {
      if (containerz[i] == w) return (true);
    }

    return (false);
  }


  // stuff

  /**
   *
   * @return
   */
  public Module getModule()
  {
    return (module);
  }

  /**
   *
   * @return
   */
  public int id()
  {
    return (index());
  }

  public void setSymbol(String s)
  {
    symbol = s;
  }
}
