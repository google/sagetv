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
 * Widget state in an STV file.
 * <br>
 * @author  601
 */
public class STVWidget implements RawWidget
{
  static final boolean CHECK_INTEGRITY = false;

  static final String NAME = "Name";

  final byte type;
  final String name; // the special NAME property

  final java.util.Properties properties;

  final int[] rawContents;
  final int[] rawContainers;

  // patched later by mapCC()
  int index = -1;
  STVWidget[] contentz;
  STVWidget[] containerz;


  STVWidget(String typeName, java.util.Properties properties, int[] contents, int[] containers)
  {
    type = MetaWidget.getTypeForName(typeName);

    name = properties.getProperty(NAME, null);
    properties.remove(NAME);
    this.properties = properties;

    //rawId = id;
    rawContents = contents;
    rawContainers = containers;

    if (CHECK_INTEGRITY)
    {
      for (int i = 0; i < rawContents.length; i++)
      {
        if (rawContents[i] < 0)
          if (sage.Sage.DBG) System.out.println("Modules: " + new tv.sage.SageRuntimeException("bad rawContent " + this.toString(), tv.sage.SageExceptable.INTEGRITY).toString());
      }

      for (int i = 0; i < rawContainers.length; i++)
      {
        if (rawContainers[i] < 0)
          if (sage.Sage.DBG) System.out.println("Modules: " + new tv.sage.SageRuntimeException("bad rawContainer " + this.toString(), tv.sage.SageExceptable.INTEGRITY).toString());
      }
    }
  }


  public String toString()
  {
    StringBuffer sb = new StringBuffer();

    String typeName = (type < 0 || type >= sage.Widget.TYPES.length) ? ""+type : sage.Widget.TYPES[type];

    sb.append(index).append('|') /* .append(rawId)*/ .append(':');
    sb.append(typeName).append('/').append(name);
    sb.append("\r\n has ").append(rawContents.length).append(' ');
    java.util.Vector rawHasv = new java.util.Vector();
    for (int x = 0; x < rawContents.length; rawHasv.add(new Integer(rawContents[x])), x++);
    sb.append(rawHasv);

    sb.append("\r\n had ").append(rawContainers.length).append(' ');
    java.util.Vector rawHadv = new java.util.Vector();
    for (int x = 0; x < rawContainers.length; rawHadv.add(new Integer(rawContainers[x])), x++);
    sb.append(rawHadv);

    java.util.Iterator itr = properties.entrySet().iterator();
    while (itr.hasNext())
    {
      java.util.Map.Entry me = (java.util.Map.Entry)itr.next();

      sb.append("\r\n  ").append(me.getKey()).append(" = ").append(me.getValue());
    }

    return (sb.toString());
  }

  /**
   * fixup index/contentz/containerz
   *
   * rawContents and rawContainers are of the id's from the STV file (unmapped)
   * there may be elements < 0 (which are deletion holes) which are to be ignored.
   */
  public static void fixCC(STVWidget[] swz, java.util.Map idMap)
  {
    java.util.Set conerrs = new java.util.HashSet();

    /** patch index (for RawWidget#index()) */
    for (int i = 0; i < swz.length; i++)
    {
      swz[i].index = i;
    }

    // each Widget
    for (int j = 0; j < swz.length; j++)
    {
      STVWidget stvWidget = swz[j];

      // 601 debug
      //if (j < 10) Log.ger.warning("mapCC[" + j + "] " + stvWidget);

      boolean conerr = false;

      // contents
      java.util.ArrayList conv = new java.util.ArrayList();

      for (int i = 0; i < stvWidget.rawContents.length; i++)
      {
        if (stvWidget.rawContents[i] >= 0) // prune "empty" elements
        {
          STVWidget stw = (STVWidget)idMap.get(new Integer(stvWidget.rawContents[i]));

          if (stw != null)
          {
            conv.add(stw);
          }
          else
          {
            conerr = true; conerrs.add(new Integer(stvWidget.rawContents[i]));
          }
        }
      }

      stvWidget.contentz = new STVWidget[conv.size()];
      for (int k = 0; k < stvWidget.contentz.length; k++)
      {
        stvWidget.contentz[k] = (STVWidget)conv.get(k);
      }

      // containers
      conv.clear();

      for (int i = 0; i < stvWidget.rawContainers.length; i++)
      {
        if (stvWidget.rawContainers[i] >= 0) // prune "empty" elements
        {
          STVWidget stw = (STVWidget)idMap.get(new Integer(stvWidget.rawContainers[i]));

          if (stw != null)
          {
            conv.add(stw);
          }
          else
          {
            conerr = true; conerrs.add(new Integer(stvWidget.rawContainers[i]));
          }
        }
      }

      stvWidget.containerz = new STVWidget[conv.size()];
      for (int k = 0; k < stvWidget.containerz.length; k++)
      {
        stvWidget.containerz[k] = (STVWidget)conv.get(k);
      }

      if (conerr)
      {
        String message = "unmapped rawCon element in " + stvWidget;

        if (sage.Sage.DBG) System.out.println("Modules: tv.sage.ws.STVWidget.mapCC: " + message);

        //throw (new tv.sage.SageRuntimeException(message, tv.sage.SageExceptable.INTEGRITY));
      }
    }

    if (!conerrs.isEmpty())
    {
      if (sage.Sage.DBG) System.out.println("Modules: tv.sage.ws.STVWidget.mapCC: conerrs = " + conerrs);
    }
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
    int[] conz = new int[contentz.length];

    for (int i = 0; i < conz.length; i++)
    {
      conz[i] = contentz[i].index;
    }

    return (conz);
  }

  public int[] containerz()
  {
    int[] conz = new int[containerz.length];

    for (int i = 0; i < conz.length; i++)
    {
      conz[i] = containerz[i].index;
    }

    return (conz);
  }

  public String symbol()
  {
    if (name != null && (type == sage.Widget.MENU || type == sage.Widget.THEME))
    {
      String name = this.name.trim();

      if (name.length() > 0)
      {
        StringBuffer sb = new StringBuffer().append(sage.Widget.TYPES[type]).append(':');

        for (int i = 0; i < name.length(); i++)
        {
          char ch = name.charAt(i);

          if (Character.isJavaIdentifierPart(ch))
          {
            sb.append(ch);
          }
        }

        return (sb.toString());
      }
    }

    return (null);
  }
}
