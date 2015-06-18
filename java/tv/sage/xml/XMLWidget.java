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

/**
 * An XMLWidget as found by parsing an Widget XML file.
 * <br>
 * @author 601
 */
public class XMLWidget //implements tv.sage.mod.RawWidget
{
  //    public static void fixCC(XMLWidget[] mxwz)
  //    {
  //        for (int i = 0; i < mxwz.length; i++)
  //        {
  //            mxwz[i].index = i;
  //        }
  //    }


  final byte type;
  String name;

  final java.util.Properties properties = new java.util.Properties();

  final int index; // Wizard.getNextWizID()
  final java.util.Vector contentv = new java.util.Vector(); // of XMLWidget
  final java.util.Vector containerv = new java.util.Vector(); // of XMLWidget

  String ref = null;
  String sym = null;


  XMLWidget(String typeName, String name, String ref, String sym, int wid)
  {
    index = (wid == -1 ? sage.Wizard.getInstance().getNextWizID() : wid);

    type = sage.WidgetMeta.getTypeForName(typeName);

    this.name = name;

    this.ref = ref;
    this.sym = sym;
  }


  void addProperty(XMLWidget.Property wp)
  {
    if (wp.name != null && wp.value != null)
    {
      properties.setProperty(wp.name, wp.value);
    }
  }

  public boolean isRef()
  {
    return (ref != null);
  }

  void containWidget(XMLWidget w)
  {
    contentv.add(w);

    w.containerv.add(this);
  }

  public String toString()
  {
    StringBuffer sb = new StringBuffer();

    for (int i = 0; i < contentv.size(); i++)
    {
      sb.append(',').append(((XMLWidget)contentv.get(i)).index());
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

  // 601 retro API, check
  public int getId()
  {
    return (index); // 601 ???
  }

  public String getType()
  {
    if (type >= 0 && type < sage.Widget.TYPES.length)
    {
      return (sage.Widget.TYPES[type]);
    }

    System.out.println("XMLWidget:  bad type = " + type);

    return (null);
  }

  public String getName()
  {
    return (name());
  }

  public java.util.Properties getProperties()
  {
    return (properties());
  }

  public int[] getContentz()
  {
    return (contentz());
  }

  public int[] getContainerz()
  {
    return (containerz());
  }


  public int[] contentz()
  {
    int[] conz = new int[contentv.size()];

    for (int i = 0; i < conz.length; i++)
    {
      XMLWidget rw = (XMLWidget)contentv.get(i);

      conz[i] = rw.index();

      // 601 debug
      if (rw.index() < 0)
      {
        System.out.println("XMLWidget:  con.id < 0 " + this);
      }
    }

    return (conz);
  }

  public int[] containerz()
  {
    int[] conz = new int[containerv.size()];

    for (int i = 0; i < conz.length; i++)
    {
      conz[i] = ((XMLWidget)containerv.get(i)).index();
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
