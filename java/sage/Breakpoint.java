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

public class Breakpoint
{
  public Breakpoint(Widget w, int flags)
  {
    widg = w;
    myFlags = flags;
    enabled = true;
    w.setBreakpointMask(flags);
  }

  public Widget getWidget() { return widg; }
  public int getFlags() { return myFlags; }
  public void setFlags(int x)
  {
    myFlags = x;
    widg.setBreakpointMask(x);
  }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean x)
  {
    enabled = x;
    if (x)
      widg.setBreakpointMask(myFlags);
    else
      widg.setBreakpointMask(0);
  }

  public static int getValidFlagsForWidget(Widget w)
  {
    int rv = 0;
    if (w.isType(Widget.HOOK))
      rv |= Tracer.HOOK_TRACE;
    else
      rv |= Tracer.ALL_EVALUATION;
    if (w.isType(Widget.LISTENER))
      rv |= Tracer.LISTENER_TRACE;
    else if (w.isType(Widget.MENU))
      rv |= Tracer.MENU_TRACE;
    else if (w.isType(Widget.OPTIONSMENU))
      rv |= Tracer.OPTIONSMENU_TRACE;
    if (w.isType(Widget.MENU) || w.isType(Widget.OPTIONSMENU) || w.isType(Widget.PANEL) || w.isType(Widget.ITEM) ||
        w.isType(Widget.TABLE) || w.isType(Widget.TABLECOMPONENT) || w.isType(Widget.TEXT) || w.isType(Widget.TEXTINPUT) ||
        w.isType(Widget.IMAGE) || w.isType(Widget.VIDEO))
      rv |= Tracer.ALL_UI;
    else if (w.isType(Widget.SHAPE))
      rv |= Tracer.RENDER_UI;
    return rv;
  }

  public static void saveBreakpoints(Breakpoint[] breaks, java.io.File theFile, java.util.Map idMap) throws java.io.IOException
  {
    // 601 debug
    //        tv.sage.mod.Log.ger.info("saveBP:idMap.size = " + (idMap == null ? -1 : idMap.size()));

    java.io.PrintWriter writer = null;
    try
    {
      writer = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(theFile)));
      for (int i = 0; i < breaks.length; i++)
      {
        // 601
        int id = breaks[i].widg.id();

        if (idMap != null)
        {
          Integer newId = (Integer)idMap.get(breaks[i].widg);

          if (newId != null)
          {
            id = newId.intValue();
          }
        }

        // 601 writer.println((breaks[i].widg.id() - Wizard.getInstance().getBaseWidgetID()) + "=" +
        writer.println("" + id + "=" + breaks[i].myFlags + "," + breaks[i].enabled);
      }
    }
    finally
    {
      if (writer != null)
        writer.close();
      writer = null;
    }
  }

  public static java.util.Vector loadBreakpoints(java.io.File theFile, UIManager uiMgr) throws java.io.IOException
  {
    java.io.BufferedReader reader = null;
    java.util.Vector rv = new java.util.Vector();
    try
    {
      reader = new java.io.BufferedReader(new java.io.FileReader(theFile));
      String line;
      while (true)
      {
        line = reader.readLine();
        if (line == null) break;
        int eqIdx = line.indexOf('=');
        if (eqIdx == -1)
          continue;
        int comIdx = line.indexOf(',', eqIdx);
        int breakID = Integer.parseInt(line.substring(0, eqIdx));
        int theFlags = Integer.parseInt(comIdx == -1 ? line.substring(eqIdx + 1) :
          line.substring(eqIdx + 1, comIdx));

        // 601 Widget widgie = Wizard.getInstance().getWidgetForID601(breakID + Wizard.getInstance().getBaseWidgetID());
        Widget widgie = null;

        if (uiMgr.getModuleGroup() != null)
        {
          widgie = uiMgr.getModuleGroup().defaultModule.getWidgetForId(breakID);
        }

        if (widgie == null)
        {
          if (Sage.DBG) System.out.println("Breakpoint widget lost: " + breakID);
          continue;
        }
        Breakpoint breakie = new Breakpoint(widgie, theFlags);
        if (comIdx != -1 && !Boolean.valueOf(line.substring(comIdx + 1)).booleanValue())
          breakie.setEnabled(false);
        rv.add(breakie);
      }
    }
    finally
    {
      if (reader != null)
        reader.close();
      reader = null;
    }
    return rv;
  }

  public String toString()
  {
    return (enabled ? "On " : "Off ") + Widget.TYPES[widg.type()] + " " + widg.getName() + " #" +
        // 601 (widg.id() - Wizard.getInstance().getBaseWidgetID()) + " 0x" + Integer.toString(myFlags, 16);
        widg.id() + " 0x" + Integer.toString(myFlags, 16);
  }

  private Widget widg;
  private int myFlags;
  private boolean enabled;
}
