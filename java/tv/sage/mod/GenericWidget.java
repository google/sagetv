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
 *
 * @author 601
 */
public class GenericWidget extends AbstractWidget
{
  /*EMBEDDED_SWITCH/
    final byte type;
/**/
  String nameProperty;

  protected String[] valuez;
  protected Object[] resolvedz;
  protected int cacheMask;

  /**
   * Widget from raw data, except contents/containers (phase 1 of 2).
   * @param rawWidget
   */
  public GenericWidget(RawWidget rawWidget)
  {
    type = rawWidget.type();
    // Optimize memory usage on the strings to be minimal (already done by the parser)
    nameProperty = rawWidget.name();

    if (!rawWidget.properties().isEmpty())
    {
      valuez = new String[PROPS.length];

      java.util.Properties props = rawWidget.properties();
      for (int i = 0; i < PROPS.length; i++)
      {
        valuez[i] = props.getProperty(PROPS[i]);
      }
    }

    index = rawWidget.index();
    symbol = rawWidget.symbol();

    buildCacheMask();
  }

  public GenericWidget(byte inType, String inName, String[] propValz, int inIndex, String inSymbol)
  {
    type = inType;
    nameProperty = inName;
    valuez = propValz;
    index = inIndex;
    symbol = inSymbol;
    buildCacheMask();
  }

  /**
   * @return Widget type
   */
  /*EMBEDDED_SWITCH/
    public final byte type()
    {
        return (type);
    }

    public final boolean isType(byte type)
    {
        return (this.type == type);
    }
/**/
  /**
   * @return Widget name
   */
  public String name()
  {
    return (nameProperty);
  }

  protected String getValue(final byte prop)
  {
    if (valuez == null) return (null);

    return (valuez[prop]);
  }

  protected void setValue(final byte prop, final String value)
  {
    if (valuez == null) // allocate array?
    {
      if (value == null) return; // don't bother

      synchronized (this)
      {
        if (valuez == null)
        {
          valuez = new String[PROPS.length];
        }
      }
    }
    else if (value == null) // remove?
    {
      valuez[prop] = null;

      // 601 colapse array?

      setResolved(prop, null); // dump cached resolution
      buildCacheMask();
      return;
    }

    valuez[prop] = value;

    setResolved(prop, null); // dump cached resolution
    buildCacheMask();
  }

  protected Object getResolved(final byte prop)
  {
    if (resolvedz == null) return (null);

    return (resolvedz[prop]);
  }

  protected void setResolved(final byte prop, final Object resolved)
  {
    // 601 integrity check for corresponding value!?!

    if (resolvedz == null) // allocate array?
    {
      if (resolved == null) return; // don't bother

      synchronized (this)
      {
        if (resolvedz == null)
        {
          resolvedz = new Object[PROPS.length];
        }
      }
    }
    else if (resolved == null) // remove?
    {
      resolvedz[prop] = null;

      // 601 colapse array?

      return;
    }

    resolvedz[prop] = resolved;
  }

  public String[] getPropertyValues()
  {
    return (valuez);
  }


  /**
   * @return Widget Module relative index
   */
  public int index()
  {
    return (index);
  }

  protected AbstractWidget[] contentz()
  {
    return (contentz);
  }

  protected AbstractWidget[] containerz()
  {
    return (containerz);
  }

  /**
   * @param widget to contain
   */
  public void contain(sage.Widget widget)
  {
    synchronized (module)
    {
      GenericWidget gw = (GenericWidget)widget;

      // arrays change unless NOP

      contentz = insert(contentz, gw, contentz.length);
      gw.containerz = insert(gw.containerz, this, -1);

      // 601 fix
      if (true) module.setChanged();
    }
  }

  /**
   * @param widget to contain
   * @param atIndex
   */
  public void contain(sage.Widget widget, int atIndex)
  {
    synchronized (module)
    {
      GenericWidget gw = (GenericWidget)widget;

      if (atIndex < 0) atIndex = 0; // min limit to first

      // arrays change unless NOP

      contentz = insert(contentz, gw, atIndex);
      gw.containerz = insert(gw.containerz, this, -1);

      // 601 fix
      if (true) module.setChanged();
    }
  }

  /**
   * @param widget to remove
   * @return indexOf or -1
   */
  public int discontent(sage.Widget widget)
  {
    synchronized (module)
    {
      GenericWidget gw = (GenericWidget)widget;

      int[] wasIndexOf = new int[1]; // extra return value

      // arrays change unless NOP

      contentz = remove(contentz, gw, wasIndexOf);
      gw.containerz = remove(gw.containerz, this, null);

      // 601 fix
      if (true) module.setChanged();

      return (wasIndexOf[0]);
    }
  }


  /**
   * For discontent.
   * @param conz either contentz or containerz
   * @param widget to remove
   * @param wasIndexOf extra return value (indexOf or -1), null if N/A
   * @return new array (or original if not changed)
   */
  private static AbstractWidget[] remove(AbstractWidget[] conz, AbstractWidget widget, int[] wasIndexOf)
  {
    int length = conz.length;

    // find it
    int indexOf = -1;
    for (int i = 0; i < length; i++)
    {
      if (conz[i] == widget)
      {
        indexOf = i;

        break;
      }
    }

    if (wasIndexOf != null) wasIndexOf[0] = indexOf; // extra return value

    if (indexOf == -1) return (conz); // NOP, not found

    if (length > 1) // still some left
    {
      AbstractWidget[] newConz = new AbstractWidget[length - 1];

      for (int i = 0; i < indexOf; i++) newConz[i] = conz[i];
      for (int i = indexOf; i < length - 1; i++) newConz[i] = conz[i + 1];

      return (newConz);
    }
    else
      return (new AbstractWidget[0]); // now empty
  }

  /**
   * For contain, contain-atIndex.
   * @param conz either contentz or containerz
   * @param widget to insert/append
   * @param atIndex contain atIndex (-1 for append anywhere)
   * @return new array (or original if not changed)
   */
  private static AbstractWidget[] insert(AbstractWidget[] conz, AbstractWidget widget, int atIndex)
  {
    int length = conz.length;

    // find it
    int indexOf = -1;
    for (int i = 0; i < length; i++)
    {
      if (conz[i] == widget)
      {
        indexOf = i;

        break;
      }
    }

    if (length == 0) return (new AbstractWidget[] { widget }); // first in

    if (atIndex >= 0) // contain-atIndex
    {
      if (atIndex > length) atIndex = length; // limit

      if (indexOf == atIndex) return (conz); // NOP

      if (indexOf == -1) // insert atIndex
      {
        AbstractWidget[] newConz = new AbstractWidget[length + 1];

        for (int i = 0; i < atIndex; i++) newConz[i] = conz[i];
        newConz[atIndex] = widget;
        for (int i = atIndex; i < length; i++) newConz[i + 1] = conz[i];

        return (newConz);
      }
      else // move from indexOf to atIndex
      {
        if (atIndex == length) // new limit since array won't grow
        {
          atIndex--;

          if (indexOf == atIndex) return (conz); // NOP
        }

        AbstractWidget[] newConz = new AbstractWidget[length];

        System.arraycopy(conz, 0, newConz, 0, length);

        if (indexOf < atIndex) // shift down
        {
          for (int i = indexOf; i < atIndex; i++)
          {
            newConz[i] = newConz[i + 1];
          }
        }
        else // shift up
        {
          for (int i = indexOf; i > atIndex; i--)
          {
            newConz[i] = newConz[i - 1];
          }
        }

        newConz[atIndex] = widget;

        return (newConz);
      }
    }
    else // contain anywhere (default append)
    {
      if (indexOf != -1) return (conz); // NOP, already in

      AbstractWidget[] newConz = new AbstractWidget[length + 1];

      System.arraycopy(conz, 0, newConz, 0, length);
      newConz[length] = widget;

      return (newConz);
    }
  }

  //    public void dump(GenericWidget gw)
  //    {
  //        StringBuffer sb = new StringBuffer("ctz:");
  //
  //        sb.append(java.util.Arrays.asList(contentz)).append("\r\ncrz:");
  //        sb.append(java.util.Arrays.asList(gw.containerz));
  //
  //        System.out.println(sb.toString());
  //    }
  //
  //    public String toString()
  //    {
  //        return (Integer.toHexString(hashCode()) + "|" + TYPES[type()] + ":" + name());
  //    }

  public boolean hasAnyCacheMask(int masker)
  {
    return (cacheMask & masker) != 0;
  }
  public boolean hasAllCacheMask(int masker)
  {
    return (cacheMask & masker) == masker;
  }

  protected void buildCacheMask()
  {
    cacheMask = 0;
    if (type != EFFECT)
      return;
    if (hasProperty(ANCHOR_POINT_X) || hasProperty(ANCHOR_POINT_Y))
    {
      cacheMask |= HAS_ANCHOR_POINT_PROPS;
      if (isDynamicProperty(ANCHOR_POINT_X) || isDynamicProperty(ANCHOR_POINT_Y))
        cacheMask |= HAS_DYNAMIC_ANCHOR_POINT_PROPS;
    }
    if (hasProperty(ANCHOR_X) || hasProperty(ANCHOR_Y) || hasProperty(START_RENDER_OFFSET_X) || hasProperty(START_RENDER_OFFSET_Y))
    {
      cacheMask |= HAS_TRANSLATE_PROPS;
      if (isDynamicProperty(ANCHOR_X) || isDynamicProperty(ANCHOR_Y) || isDynamicProperty(START_RENDER_OFFSET_X) || isDynamicProperty(START_RENDER_OFFSET_Y))
        cacheMask |= HAS_DYNAMIC_TRANSLATE_PROPS;
    }
    if (hasProperty(FOREGROUND_ALPHA) || hasProperty(BACKGROUND_ALPHA))
    {
      cacheMask |= HAS_ALPHA_PROPS;
      if (isDynamicProperty(FOREGROUND_ALPHA) || isDynamicProperty(BACKGROUND_ALPHA))
        cacheMask |= HAS_DYNAMIC_ALPHA_PROPS;
    }
    if (hasProperty(RENDER_SCALE_X) || hasProperty(RENDER_SCALE_Y) || hasProperty(START_RENDER_SCALE_X) || hasProperty(START_RENDER_SCALE_Y))
    {
      cacheMask |= HAS_SCALE_PROPS;
      if (isDynamicProperty(RENDER_SCALE_X) || isDynamicProperty(RENDER_SCALE_Y) || isDynamicProperty(START_RENDER_SCALE_X) || isDynamicProperty(START_RENDER_SCALE_Y))
        cacheMask |= HAS_DYNAMIC_SCALE_PROPS;
    }
    if (hasProperty(START_RENDER_ROTATE_X) || hasProperty(RENDER_ROTATE_X))
    {
      cacheMask |= HAS_ROTX_PROPS;
      if (isDynamicProperty(START_RENDER_ROTATE_X) || isDynamicProperty(RENDER_ROTATE_X))
        cacheMask |= HAS_DYNAMIC_ROTX_PROPS;
    }
    if (hasProperty(START_RENDER_ROTATE_Y) || hasProperty(RENDER_ROTATE_Y))
    {
      cacheMask |= HAS_ROTY_PROPS;
      if (isDynamicProperty(START_RENDER_ROTATE_Y) || isDynamicProperty(RENDER_ROTATE_Y))
        cacheMask |= HAS_DYNAMIC_ROTY_PROPS;
    }
    if (hasProperty(START_RENDER_ROTATE_Z) || hasProperty(RENDER_ROTATE_Z))
    {
      cacheMask |= HAS_ROTZ_PROPS;
      if (isDynamicProperty(START_RENDER_ROTATE_Z) || isDynamicProperty(RENDER_ROTATE_Z))
        cacheMask |= HAS_DYNAMIC_ROTZ_PROPS;
    }
    if (hasProperty(HALIGNMENT) || hasProperty(VALIGNMENT))
    {
      cacheMask |= HAS_CAMERA_PROPS;
      if (isDynamicProperty(HALIGNMENT) || isDynamicProperty(VALIGNMENT))
        cacheMask |= HAS_DYNAMIC_CAMERA_PROPS;
    }
  }
}
