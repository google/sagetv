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
public abstract class WidgetFidget
{
  // 13 callers
  public static void setName(Widget w, String name)
  {
    //        if (tv.sage.ModuleManager.isModular)
    //        {
    ((tv.sage.mod.GenericWidget)w).setName(name);
    //        }
    //        else
    //            ((WidgetImp)w).setName(name);
  }

  // 18 callers
  public static void setProperty(Widget w, byte prop, String val)
  {
    //        if (tv.sage.ModuleManager.isModular)
    //        {
    ((tv.sage.mod.GenericWidget)w).setProperty(prop, val);
    //        }
    //        else
    //            ((WidgetImp)w).setProperty(prop, val);
  }

  // 18 callers (both versions)
  public static void contain(Widget w, Widget con)
  {
    if (con == null || w == null) return;
    //        if (tv.sage.ModuleManager.isModular)
    //        {
    ((tv.sage.mod.GenericWidget)w).contain(con);
    //        }
    //        else
    //            ((WidgetImp)w).contain(con);
  }

  public static void contain(Widget w, Widget con, int index)
  {
    //        if (tv.sage.ModuleManager.isModular)
    //        {
    ((tv.sage.mod.GenericWidget)w).contain(con, index);
    //        }
    //        else
    //            ((WidgetImp)w).contain(con, index);
  }

  // 13 callers
  public static int discontent(Widget w, Widget dis)
  {
    //        if (tv.sage.ModuleManager.isModular)
    //        {
    return (((tv.sage.mod.GenericWidget)w).discontent(dis));
    //        }
    //        else
    //            return (((WidgetImp)w).discontent(dis));
  }
}
