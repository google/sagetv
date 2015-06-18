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

import java.text.ParseException;

public abstract class AbstractEditor
{
  public AbstractEditor(Widget widg)
  {
    this(new Widget[] { widg });
  }
  public AbstractEditor(Widget[] widgs)
  {
    this.widgs = widgs;
  }

  public abstract void initEditingComponent();

  public abstract void setEditingFocus();

  public abstract void acceptEdit() throws ParseException;

  public abstract void revertEditingComponent();

  public final java.awt.Container getComponent()
  {
    return comp;
  }
  public final Widget getWidget()
  {
    return widgs[0];
  }
  public final Widget[] getWidgets()
  {
    return widgs;
  }
  protected Widget[] widgs;
  protected java.awt.Container comp;
}
