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

public interface Tracer
{
  /*
   * Execution Tracing
   */
  // Called before and after every evaluateExpression
  public static final int PRE_EVALUATION = 0x1;
  public static final int POST_EVALUATION = 0x2;
  public static final int ALL_EVALUATION = 0x3;
  public void traceEvaluate(int evalState, String expr, Widget w, Catbert.Context con);

  /*
   * UI Tracing
   */
  public static final int MENU_TRACE = 0x4;
  public static final int OPTIONSMENU_TRACE = 0x8;
  public static final int ALL_MENU = 0xC;
  public void traceMenu(Widget w);
  // called when we're about to launch an options menu
  public void traceOptionsMenu(Widget w);
  // Called from the ZPseudoCopm contstructor
  public static final int CREATE_UI = 0x10;
  // Called from doLayoutNow in ZPseudoComp
  public static final int LAYOUT_UI = 0x20;
  // Called at the beginning of evaluate in ZPseudoComp
  public static final int PRE_EVALUATE_COMPONENT_UI = 0x40;
  // Called at the end of evaluate in ZPseudoComp with its result
  public static final int POST_EVALUATE_COMPONENT_UI = 0x80;
  // Called at the beginning of evaluate in ZPseudoComp
  public static final int PRE_EVALUATE_DATA_UI = 0x100;
  // Called at the end of evaluate in ZPseudoComp with its result
  public static final int POST_EVALUATE_DATA_UI = 0x200;
  // Called at the beginning of buildRenderingOps for a ZPseudoComp
  public static final int RENDER_UI = 0x400;
  // Called when the conditional chain is about to be evaluated again
  public static final int PRE_CONDITIONAL_UI = 0x800;
  // Called after a conditional chain was evaluated with the result of it
  public static final int POST_CONDITIONAL_UI = 0x1000;
  public static final int ALL_UI = 0x1FF0;
  public void traceUI(int traceAction, ZPseudoComp z, Widget w, Object result);
  // called when we're about to launch a menu

  /*
   * Event Tracing
   */
  public static final int EVENT_TRACE = 0x2000;
  public static final int LISTENER_TRACE = 0x4000;
  public void traceEvent(ZPseudoComp currCheck, String sageCommand, long irCode, int keyCode, int keyModifiers, char keyChar);
  public void traceListener(ZPseudoComp uiComp, Widget listener);

  /*
   * Hook Tracing
   */
  public static final int HOOK_TRACE = 0x8000;
  public void traceHook(Widget hook, Object[] hookVars, ZPseudoComp hookUI);

  public static final int TRACE_RESULT_MASK = POST_EVALUATION | POST_EVALUATE_COMPONENT_UI | POST_EVALUATE_DATA_UI | POST_CONDITIONAL_UI;
}
