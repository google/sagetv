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

public interface STVEditor
{
  public boolean showAndHighlightNode(Widget ui);
  public void notifyOfExternalSTVChange();
  public void kill();
  public java.util.Vector generateDiffOps(Widget[] currWidgs, int currWidgCount, Widget[] diffWidgs,
      int diffWidgCount);
  public java.util.Vector generateDiffOpsUID(Widget[] newWidgs, int newWidgCount, Widget[] oldWidgs, int oldWidgCount);
  public void refreshTree();
  public void refreshUITree();
  public java.util.Vector removeWidgetsWithUndo(Widget[] killUs, java.util.Vector undoList);
  public void pushWidgetOp(Object wop);
  public void pushWidgetOps(java.util.List wops);
  public boolean highlightNode(Widget ui);
  public void addBreakpoint(Widget w);
  public void removeBreakpoint(Widget w);
  public Breakpoint getBreakpointInfo(Widget w);
  public Catbert.Context getSuspendedContext();
  public void setUIMgr(UIManager inUIMgr);
  public UIManager getUIMgr();
  public void resumeExecution();
}
