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
package tv.sage.xml;

import sage.Widget;

/**
 * A RawWidget as found by parsing an Module XML file.
 * <br>
 * @author 601
 */
public class ModuleXMLWidget implements tv.sage.mod.RawWidget
{
  public static void fixCC(ModuleXMLWidget[] mxwz)
  {
    for (int i = 0; i < mxwz.length; i++)
    {
      mxwz[i].index = i;
    }
  }


  final byte type;
  String name;
  boolean persistentPrimaryRefs;

  final java.util.Properties properties = new java.util.Properties();

  int index = -1;
  final java.util.Vector contentv = new java.util.Vector(); // of ModuleXMLWidget
  final java.util.Vector containerv = new java.util.Vector(); // of ModuleXMLWidget

  String ref = null;
  String sym = null;


  ModuleXMLWidget(byte inType, String name, String ref, String sym)
  {
    type = inType;

    this.name = name;

    this.ref = ref;
    this.sym = sym;
  }


  void addProperty(ModuleXMLWidget.Property wp)
  {
    if (wp.name != null && wp.value != null)
    {
      properties.setProperty(wp.name, wp.value);
    }
  }

  void addProperty(String name, String value)
  {
    if (name != null && value != null)
    {
      properties.setProperty(name, value);
    }
  }

  public boolean isRef()
  {
    return (ref != null);
  }

  void containWidget(ModuleXMLWidget w)
  {
    contentv.add(w);

    w.containerv.add(this);
  }

  void containWidgetPrimary(ModuleXMLWidget w)
  {
    contentv.add(w);

    w.containerv.insertElementAt(this, 0);
    w.persistentPrimaryRefs = true;
    persistentPrimaryRefs = true;
  }

  public String toString()
  {
    StringBuffer sb = new StringBuffer();

    for (int i = 0; i < contentv.size(); i++)
    {
      sb.append(',').append(((tv.sage.mod.RawWidget)contentv.get(i)).index());
    }

    String typeName = "" + type;
    if (type >= 0 && type < sage.Widget.TYPES.length) typeName = sage.Widget.TYPES[type];

    return (typeName + "#" + index + " " + name + "[" + sb + "] sym=" + sym + properties);
  }


  // implements RawWidget

  public byte type()
  {
    return (type);
  }

  public String name()
  {
    return (name);
  }

  public java.util.Properties properties()
  {
    return (properties);
  }

  public int index()
  {
    return (index);
  }

  public int[] contentz()
  {
    int[] conz = new int[contentv.size()];

    for (int i = 0; i < conz.length; i++)
    {
      tv.sage.mod.RawWidget rw = (tv.sage.mod.RawWidget)contentv.get(i);

      conz[i] = rw.index();

      // 601 debug
      if (rw.index() < 0)
      {
        if (sage.Sage.DBG) System.out.println("Modules: con.id < 0 " + this);
      }
    }

    return (conz);
  }

  public int[] containerz()
  {
    int[] conz = new int[containerv.size()];

    for (int i = 0; i < conz.length; i++)
    {
      if (persistentPrimaryRefs)
      {
        conz[i] = ((tv.sage.mod.RawWidget)containerv.get(i)).index();
      }
      else
        // 601 reverse order
        conz[conz.length - 1 - i] = ((tv.sage.mod.RawWidget)containerv.get(i)).index();
    }

    return (conz);
  }

  public String symbol()
  {
    return (sym);
  }

  public static class Property
  {
    final String name;
    String value = null;

    Property(String name)
    {
      this.name = name; // 601 tv.sage.WidgetConstants.PROPS[widgetProperty(name)];
    }

    void setValue(StringBuffer value)
    {
      if (value != null)
      {
        this.value = value.toString();
      }
    }
  }
}
