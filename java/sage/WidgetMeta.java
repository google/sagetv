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
 * @author brian
 */
public abstract class WidgetMeta
{
  //    private static Wizard wizard = Wizard.getInstance();

  public static Widget[] getWidgets()
  {
    //        if (tv.sage.ModuleManager.isModular)
    //        {
    if (tv.sage.ModuleManager.defaultModuleGroup != null)
    {
      return (tv.sage.ModuleManager.defaultModuleGroup.defaultModule.getWidgetz());
    }
    else
      return (new Widget[0]);
    //        }
    //        else
    //            return (wizard.getWidgets601());
  }

  //    public static Widget[] getWidgets(byte type)
  //    {
  //        if (tv.sage.ModuleManager.isModular)
  //        {
  //            if (tv.sage.ModuleManager.defaultModuleGroup != null)
  //            {
  //                return (tv.sage.ModuleManager.defaultModuleGroup.defaultModule.findWidgetz(type));
  //            }
  //            else
  //                return (new Widget[0]);
  //        }
  //        else
  //            return (wizard.getWidgets601(type));
  //    }

  //    public static Widget getWidgetForID(int id)
  //    {
  //        if (tv.sage.ModuleManager.isModular)
  //        {
  //            if (tv.sage.ModuleManager.defaultModuleGroup != null)
  //            {
  //                return (tv.sage.ModuleManager.defaultModuleGroup.defaultModule.getWidgetForId(id));
  //            }
  //
  //            throw (new IllegalStateException("Missing defaultModuleGroup for getWidgetForID"));
  //        }
  //        else
  //            return (wizard.getWidgetForID601(id));
  //    }

  public static Widget addWidget(byte type)
  {
    //        if (tv.sage.ModuleManager.isModular)
    //        {
    if (tv.sage.ModuleManager.defaultModuleGroup != null)
    {
      return (tv.sage.ModuleManager.defaultModuleGroup.defaultModule.addWidget(type, null));
    }

    throw (new IllegalStateException("Missing defaultModuleGroup for addWidget"));
    //        }
    //        else
    //            return (wizard.addWidget601(type));
  }

  //    public static void removeWidget(Widget removeMe)
  //    {
  //        if (tv.sage.ModuleManager.isModular)
  //        {
  //            if (tv.sage.ModuleManager.defaultModuleGroup != null)
  //            {
  //                tv.sage.ModuleManager.defaultModuleGroup.defaultModule.removeWidget(removeMe);
  //            }
  //            else
  //            {
  //                throw (new IllegalStateException("Missing defaultModuleGroup for removeWidget"));
  //            }
  //        }
  //        else
  //            wizard.removeWidget601(removeMe);
  //    }

  // 4 callers DynamicToolbar, OracleTree
  //    public static Widget klone(Widget w)
  //    {
  //        if (tv.sage.ModuleManager.isModular)
  //        {
  //            if (tv.sage.ModuleManager.defaultModuleGroup != null)
  //            {
  //                return (tv.sage.ModuleManager.defaultModuleGroup.defaultModule.kloneWidget(w));
  //            }
  //
  //            throw (new IllegalStateException("Missing defaultModuleGroup for addWidget"));
  //        }
  //        else
  //            return (((WidgetImp)w).klone());
  //    }

  //    public static void resurrectWidget(Widget widg)
  //    {
  //        if (tv.sage.ModuleManager.isModular)
  //        {
  //            if (tv.sage.ModuleManager.defaultModuleGroup != null)
  //            {
  //                tv.sage.ModuleManager.defaultModuleGroup.defaultModule.resurrectWidget(widg);
  //            }
  //            else
  //            {
  //                throw (new IllegalStateException("Missing defaultModuleGroup for resurrectWidget"));
  //            }
  //        }
  //        else
  //            wizard.resurrectWidget601(widg);
  //    }

  //    public static void retranslate()
  //    {
  //        if (tv.sage.ModuleManager.isModular)
  //        {
  //            // 601 LOCK?
  //
  //            tv.sage.mod.Translator.reset();
  //
  //            Widget[] widgetz = getWidgets();
  //            for (int i = 0; i < widgetz.length; i++)
  //            {
  //                ((tv.sage.mod.AbstractWidget)widgetz[i]).retranslate();
  //            }
  //        }
  //        else
  //        {
  //            WidgetImp.sourceTranslationMap = null;
  //            Widget[] allWidgs = getWidgets();
  //            for (int i = 0; i < allWidgs.length; i++)
  //                ((WidgetImp)allWidgs[i]).retranslateWidget();
  //        }
  //    }

  public static boolean isRelationshipAllowed(byte parentType, byte childType)
  {
    //        if (tv.sage.ModuleManager.isModular)
    //        {
    return (tv.sage.mod.AbstractWidget.isRelationshipAllowed(parentType, childType));
    //        }
    //        else
    //        {
    //            return (WidgetImp.isRelationshipAllowed(parentType, childType));
    //        }
  }

  public static final byte getTypeForName(String typeName)
  {
    for (byte i = 0; i < Widget.TYPES.length; i++)
    {
      if (Widget.TYPES[i].equals(typeName)) return (i);
    }

    return (-1);

    //return (WidgetImp.getTypeForName(typeName));
  }

  public static final byte getPropForName(String propName)
  {
    for (byte i = 0; i < Widget.PROPS.length; i++)
    {
      if (Widget.PROPS[i].equals(propName)) return (i);
    }

    return (-1);

    //return (WidgetImp.getPropForName(propName));
  }

  public static int getFontStyleForName(String s)
  {
    if ("BoldItalic".equals(s))
      return MetaFont.BOLD | MetaFont.ITALIC;
    else if ("Bold".equals(s))
      return MetaFont.BOLD;
    else if ("Italic".equals(s))
      return MetaFont.ITALIC;
    else
      return MetaFont.PLAIN;

    //return (WidgetImp.getFontStyleForName(s));
  }

  public static String convertToCleanPropertyName(String s)
  {
    StringBuffer sb = new StringBuffer();
    boolean lastWasUnder = false;
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (Character.isLetterOrDigit(c))
      {
        lastWasUnder = false;
        sb.append(c);
      }
      else if (!lastWasUnder)
      {
        lastWasUnder = true;
        sb.append('_');
      }
    }
    return sb.toString();

    //return (WidgetImp.convertToCleanPropertyName(s));
  }

  /**
   * Widget Cache support.
   * @return last modified time (System.currentTimeMillis() style)
   */
  public static long lastModified()
  {
    //        if (tv.sage.ModuleManager.isModular)
    //        {
    if (tv.sage.ModuleManager.defaultModuleGroup != null)
    {
      return (tv.sage.ModuleManager.defaultModuleGroup.defaultModule.lastModified());
    }
    else
      throw (new IllegalStateException("Missing defaultModuleGroup for lastModified"));
    //        }
    //        else
    //            return (wizard.getLastWidgetModified601());
  }
}
