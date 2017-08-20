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

import sage.jep.FunctionTable;
import sage.jep.JEP;
import sage.jep.SymbolTable;
import sage.jep.function.PostfixMathCommand;
import sage.jep.function.PostfixMathCommandI;
import sage.media.rss.RSSChannel;
import sage.media.rss.RSSEnclosure;
import sage.media.rss.RSSHandler;
import sage.media.rss.RSSItem;
import sage.media.rss.RSSMediaContent;
import sage.media.rss.RSSMediaGroup;
import sage.media.rss.RSSParser;
import sage.media.rss.Translate;

import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

/*
 * TODO list for API:
 * GetAllCategories
 * GetAllSubCategories (and any other GetAll for DB fields)
 */

public class Catbert
{

  public static final boolean ENABLE_LOCATOR_API = false;

  // This gets set once the Studio is invoked so we can disable the extra profiling checks it has easier before that occurs
  public static boolean ENABLE_STUDIO_DEBUG_CHECKS = false;

  private static Map<String, Long> errorTimeMap = new HashMap<String, Long>();
  private static long overallLastErrorTime;
  private static final long REPEAT_ERROR_SPACING = 5000;
  private static final String[] pagingChangeVars;
  public static final Map<String, List<String>> categoryMethodMap =
      new HashMap<String, List<String>>();
  private static final boolean LOG_REM_ACTIONS = Sage.getBoolean("debug_rem_actions", false);

  public static final Map<String, Integer> keystrokeNameMap = new HashMap<String, Integer>();
  public static final int[] specialKeycodes = { KeyEvent.VK_ENTER,
      KeyEvent.VK_BACK_SPACE, KeyEvent.VK_TAB,
      KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT,
      KeyEvent.VK_CAPS_LOCK, KeyEvent.VK_ESCAPE,
      KeyEvent.VK_SPACE, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN,
      KeyEvent.VK_END, KeyEvent.VK_HOME, KeyEvent.VK_LEFT,
      KeyEvent.VK_UP, KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN,
      KeyEvent.VK_MULTIPLY, KeyEvent.VK_ADD, KeyEvent.VK_SEPARATOR,
      KeyEvent.VK_SUBTRACT, KeyEvent.VK_DECIMAL, KeyEvent.VK_DIVIDE,
      KeyEvent.VK_DELETE, KeyEvent.VK_NUM_LOCK, KeyEvent.VK_SCROLL_LOCK,
      KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3,
      KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6,
      KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9,
      KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12,
      KeyEvent.VK_F13, KeyEvent.VK_F14, KeyEvent.VK_F15,
      KeyEvent.VK_F16, KeyEvent.VK_F17, KeyEvent.VK_F18,
      KeyEvent.VK_F19, KeyEvent.VK_F20, KeyEvent.VK_F21,
      KeyEvent.VK_F22, KeyEvent.VK_F23, KeyEvent.VK_F24,
      KeyEvent.VK_PRINTSCREEN, KeyEvent.VK_INSERT, KeyEvent.VK_HELP,
      KeyEvent.VK_META, KeyEvent.VK_BACK_QUOTE, KeyEvent.VK_QUOTE,
      KeyEvent.VK_AMPERSAND, KeyEvent.VK_ASTERISK, KeyEvent.VK_QUOTEDBL,
      KeyEvent.VK_LESS, KeyEvent.VK_GREATER, KeyEvent.VK_BRACELEFT,
      KeyEvent.VK_BRACERIGHT, KeyEvent.VK_AT, KeyEvent.VK_COLON,
      KeyEvent.VK_CIRCUMFLEX, KeyEvent.VK_DOLLAR, KeyEvent.VK_EURO_SIGN,
      KeyEvent.VK_EXCLAMATION_MARK, KeyEvent.VK_INVERTED_EXCLAMATION_MARK,
      KeyEvent.VK_LEFT_PARENTHESIS, KeyEvent.VK_NUMBER_SIGN,
      KeyEvent.VK_MINUS, KeyEvent.VK_PLUS, KeyEvent.VK_RIGHT_PARENTHESIS,
      KeyEvent.VK_UNDERSCORE, KeyEvent.VK_AGAIN, KeyEvent.VK_UNDO,
      KeyEvent.VK_COPY, KeyEvent.VK_PASTE, KeyEvent.VK_CUT,
      KeyEvent.VK_FIND, KeyEvent.VK_PROPS, KeyEvent.VK_STOP,
      KeyEvent.VK_NUMPAD0, KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD2,
      KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD5,
      KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD7, KeyEvent.VK_NUMPAD8,
      KeyEvent.VK_NUMPAD9,
      KeyEvent.VK_BACK_SLASH, KeyEvent.VK_EQUALS,
      KeyEvent.VK_SEMICOLON, KeyEvent.VK_COMMA, KeyEvent.VK_PERIOD,
      KeyEvent.VK_OPEN_BRACKET, KeyEvent.VK_CLOSE_BRACKET,
      KeyEvent.VK_SLASH,};
  public static final String[] hookNamesWithVars = {
      "FilePlaybackFinished(MediaFile)", // UI specific
      "MediaPlayerFileLoadComplete(MediaFile, FullyLoaded)", // UI specific
      "MediaPlayerError(ErrorCategory, ErrorDetails)", // UI specific OR all UIs
      "RequestToExceedParentalRestrictions(AiringOrPlaylist, LimitsExceeded)", // UI specific
      "RecordRequestScheduleConflict(RequestedRecord, ConflictingRecords)",  // UI specific
      "RecordRequestLiveConflict(RequestedRecord, ConflictingRecord)", // UI specific
      "WatchRequestConflict(RequestedWatch, ConflictingRecord)",  // UI specific
      "DenyChannelChangeToRecord(AiringToRecord)", // UI specific
      "InactivityTimeout()",  // UI specific
      "NewUnresolvedSchedulingConflicts()", // all UIs
      "MediaPlayerPlayStateChanged()",  // UI specific
      "MediaPlayerSeekCompleted()",  // UI specific
      "BeforeMenuLoad(Reloaded)",  // UI specific
      "AfterMenuLoad(Reloaded)", // UI specific
      "BeforeMenuUnload()",  // UI specific
      "MenuNeedsDefaultFocus(Reloaded)", // UI specific
      "RecordingScheduleChanged()", // all UIs
      "RenderingStarted()",  // UI specific
      "FocusGained()",  // UI specific
      "FocusLost()", // UI specific
      "STVImported(ExistingWidgets, ImportedWidgets)",  // UI specific
      "MediaFilesImported(NewMediaFiles)", // all UIs
      "StorageDeviceAdded(DevicePath, DeviceDescription)", // all local UIs
      "ApplicationStarted()",  // UI specific
      "ApplicationExiting()",  // UI specific
      "LayoutStarted()", // UI specific
      "SystemStatusChanged()", // all UIs
      "VoiceInputReceived(FullString, Action, TargetString, TargetContent)",  // UI specific
      "TaskCompleted(TaskID, ReturnValue)", // See apiUIWithControler
      "RecordRequestLiveMultiConflict(RequestedRecord, ConflictingRecords)", // UI specific
      "WatchRequestLiveMultiConflict(RequestedWatch, ConflictingRecords)", // UI specific
  };
  public static final String[] hookNames = { "FilePlaybackFinished",
      "MediaPlayerFileLoadComplete", "MediaPlayerError", "RequestToExceedParentalRestrictions",
      "RecordRequestScheduleConflict", "RecordRequestLiveConflict",
      "WatchRequestConflict", "DenyChannelChangeToRecord", "InactivityTimeout",
      "NewUnresolvedSchedulingConflicts", "MediaPlayerPlayStateChanged",
      "MediaPlayerSeekCompleted", "BeforeMenuLoad", "AfterMenuLoad",
      "BeforeMenuUnload", "MenuNeedsDefaultFocus",
      "RecordingScheduleChanged", "RenderingStarted", "FocusGained", "FocusLost",
      "STVImported", "MediaFilesImported", "StorageDeviceAdded", "ApplicationStarted", "ApplicationExiting",
      "LayoutStarted", "SystemStatusChanged", "VoiceInputReceived", "TaskCompleted",
      "RecordRequestLiveMultiConflict", "WatchRequestLiveMultiConflict"
  };
  public static final String AUTO_CLEANUP_STV_IMPORTED_HOOK = "AutoCleanupSTVImportedHook";
  private static final Map<String, String[]> hookVarMap = new HashMap<String, String[]>();

  static
  {
    List<String> pagingChangeVarsList = new ArrayList<String>();
    pagingChangeVarsList.add("IsFirstPage");
    pagingChangeVarsList.add("IsFirstHPage");
    pagingChangeVarsList.add("IsFirstVPage");
    pagingChangeVarsList.add("IsLastPage");
    pagingChangeVarsList.add("IsLastVPage");
    pagingChangeVarsList.add("IsLastHPage");
    pagingChangeVarsList.add("VScrollIndex");
    pagingChangeVarsList.add("HScrollIndex");
    // NARFLEX - 10/27/09 - I disabled these 5 variables since they don't change on a simple page operation; they'll only change
    // if the table's internal data structure changes as well, and such an event will be part of a Refresh call at the table level
    // NARFLEX - 11/06/09 - Apparently ZPseudoComp's scrolling relies on this so then when it sets these values intially
    // the components that use them get refreshed properly
    pagingChangeVarsList.add("NumRows");
    pagingChangeVarsList.add("NumCols");
    pagingChangeVarsList.add("NumPages");
    pagingChangeVarsList.add("NumHPages");
    pagingChangeVarsList.add("NumVPages");
    pagingChangeVars = pagingChangeVarsList.toArray(Pooler.EMPTY_STRING_ARRAY);
    for (int i = 0; i < specialKeycodes.length; i++)
      keystrokeNameMap.put(KeyEvent.getKeyText(specialKeycodes[i]),
          new Integer(specialKeycodes[i]));
    for (int i = 0; i < hookNamesWithVars.length; i++)
    {
      int idx = hookNames[i].length();
      StringTokenizer toker = new StringTokenizer(hookNamesWithVars[i].substring(idx), "(), ");
      String[] vars = new String[toker.countTokens()];
      idx = 0;
      while (toker.hasMoreTokens())
      {
        vars[idx++] = toker.nextToken();
      }
      hookVarMap.put(hookNames[i], vars);
    }
    Sage.gc();
  }

  /*
   * This is cleared at every page change. The contents of it get dumped into
   * the context for that page. AddStaticContext(name,value) is used in process chains
   * to insert objects into that space.
   */
  private static ReflectionFunctionTable singleReflectionFunctionTable = new ReflectionFunctionTable();

  public static Map getAPI()
  {
    return singleReflectionFunctionTable;
  }

  public static void enableAPIProfiling()
  {
    PredefinedJEPFunction.API_PROFILING = true;
  }

  public static void disableAPIProfiling()
  {
    PredefinedJEPFunction.API_PROFILING = false;
  }

  public static void resetAPIProfiling()
  {
    for (Object foo : singleReflectionFunctionTable.values())
    {
      if (foo instanceof PredefinedJEPFunction)
        ((PredefinedJEPFunction)foo).numCalls = 0;
    }
  }

  public static void dumpAPIProfile(String filename) throws IOException
  {
    List<PostfixMathCommandI> results = new ArrayList<PostfixMathCommandI>(singleReflectionFunctionTable.values());
    PostfixMathCommandI[] allFuncs = results.toArray(new PostfixMathCommandI[0]);
    Arrays.sort(allFuncs, new Comparator<Object>()
    {
      public int compare(Object o1, Object o2)
      {
        if (o1 instanceof PredefinedJEPFunction)
        {
          if (o2 instanceof PredefinedJEPFunction)
          {
            return ((PredefinedJEPFunction)o2).numCalls - ((PredefinedJEPFunction)o1).numCalls;
          }
          else
            return -1;
        }
        else if (o2 instanceof PredefinedJEPFunction)
          return 1;
        else
          return 0;
      }
    });
    PrintWriter bw = null;
    try
    {
      bw = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
      for (int i = 0; i < allFuncs.length; i++)
      {
        if (allFuncs[i] instanceof PredefinedJEPFunction)
        {
          PredefinedJEPFunction foo = (PredefinedJEPFunction) allFuncs[i];
          if (foo.numCalls == 0) break;
          bw.println(foo.numCalls + " \t" + foo.getGroup() + "." + foo.getMethodName());
        }
      }
    }
    finally
    {
      if (bw != null)
        bw.close();
    }
  }

  // When an async call is executed, this variable is set in the context on the return. It'll
  // be set to a new ID number, which'll be unique to this task execution. The caller must then
  // register the ExecutionPosition and bail out from its location in processChain.
  // Then when the async call itself is ready to return, it'll get the appropriate
  // ExecutionPosition back from Catbert, then set the default variable to be its return value,
  // and then resume execution on that task.
  private static Map<Object, Object> taskIdToExecPosMap = new HashMap<Object, Object>();

  public static Object getNewTaskID()
  {
    return new AsyncTaskID();
  }

  static class AsyncTaskID{}


  // Returns true if the task has already completed, and we don't need to be async about it
  public static ExecutionPosition registerAsyncTaskInfo(ZPseudoComp ui, Widget child, Context con, Object taskID)
  {
    ExecutionPosition rv = null;
    synchronized (taskIdToExecPosMap)
    {
      if (taskIdToExecPosMap.containsKey(taskID))
      {
        // This means the async execution has already returned
        con.set(null, taskIdToExecPosMap.get(taskID));
        taskIdToExecPosMap.remove(taskID);
        return null;
      }
      taskIdToExecPosMap.put(taskID, rv = new ExecutionPosition(ui, child, con));
      return rv;
    }
  }

  public static void asyncTaskComplete(Object taskID, Object rv)
  {

    ExecutionPosition execPos = null;
    synchronized (taskIdToExecPosMap)
    {
      if (taskIdToExecPosMap.containsKey(taskID))
      {
        execPos = (ExecutionPosition) taskIdToExecPosMap.get(taskID);
        taskIdToExecPosMap.remove(taskID);
      }
      else
      {
        taskIdToExecPosMap.put(taskID, rv);
      }
    }
    if (execPos != null)
    {
      execPos.context.set(null, rv);
      execPos.resumeExecution();
    }
  }

  private Catbert()
  {
  }

  /*
   * Hooks are NEVER processed from the main execution thread. They wouldn't be a hook if they were!
   * That means when doing OptionsMenus while processing a Hook, we do NOT want to terminate execution
   * when we get to the OptionsMenu. We want to have the execution hang on the OptionsMenu's completion.
   * And this can be done through synchronization with the PseudoMenu on the state of an OptionMenu.
   */
  static final String HOOK_WIDGET_VAR = "_SAGE_HOOKSOURCEWIDGET";
  static final String PASSIVE_LISTEN_VAR = "_SAGE_PASSIVELISTEN";
  public static final String EXCEPTION_VAR = "SAGEEXCEPTION";

  // Distributed hooks are always rethreaded, otherwise one clients response could suspend the entire system
  public static void distributeHookToAll(final String hookName, final Object[] hookVars)
  {
    distributeHookToLocalUIs(hookName, hookVars);
    Pooler.execute(new Runnable(){
      public void run() {
        NetworkClient.distributeHook(hookName, hookVars);
      }
    }, "DistributeHookNetClients");
  }

  public static void distributeHookToLocalUIs(final String hookName, final Object[] hookVars)
  {
    Iterator<UIManager> walker = UIManager.getUIIterator();
    while (walker.hasNext())
    {
      processUISpecificHook(hookName, hookVars, walker.next(), true, 0);
    }
  }

  public static Object processUISpecificHook(final String hookName, final Object[] hookVars, final UIManager uiMgr, boolean rethread)
  {
    return processUISpecificHook(hookName, hookVars, uiMgr, rethread, 0);
  }

  public static Object processUISpecificHook(final String hookName, final Object[] hookVars, final UIManager uiMgr, boolean rethread, final long rethreadStartDelay)
  {
    if (Sage.isHeadless() && uiMgr == null)
    {
      if (hookVars != null && hookVars.length > 0)
      {
        System.out.println("SERVICE NOTIFICATION: " + hookName +
            (hookVars == null ? "" : (" data=" + Arrays.asList(hookVars))));
      }
      return null;
    }
    if (rethread) // these always return null
    {
      Pooler.execute(new Runnable()
      {
        public void run()
        {
          if (rethreadStartDelay > 0)
          {
            try{Thread.sleep(rethreadStartDelay);}catch(Exception e){}
          }
          Catbert.processUISpecificHook(hookName, hookVars, uiMgr, false, 0);
        }
      }, "ReProcessHook");
      return null;
    }
    //if (Sage.DBG) System.out.println("Looking for hooks for " + hookName);
    // Check for the Menu level Hook first
    Widget[] specificHooks = null;
    PseudoMenu ui = (uiMgr != null) ? uiMgr.getCurrUI() : null;
    ZPseudoComp hookMenu = null;
    if (ui != null)
    {
      ZPseudoComp topPopup = ui.getTopPopup();
      // NOTE: We don't want to check the menu for hooks if we're doing an OptionsMenu because then
      // an AfterMenuLoad hook that launches on OptionsMenu would then be called again when that OptionsMenu loads!
      Widget[] themedSpecificHooks = new Widget[0];
      if (topPopup != null)
      {
        hookMenu = topPopup;
        specificHooks = topPopup.widg.contents(Widget.HOOK);
        // Don't forget about themed hooks
        if (topPopup.widg != topPopup.propWidg)
          themedSpecificHooks = topPopup.propWidg.contents(Widget.HOOK);
      }
      else
      {
        hookMenu = ui.getUI();
        specificHooks = hookMenu.widg.contents(Widget.HOOK);
        if (hookMenu.widg != hookMenu.propWidg)
          themedSpecificHooks = hookMenu.propWidg.contents(Widget.HOOK);
      }
      if (themedSpecificHooks.length > 0)
      {
        if (specificHooks.length == 0)
          specificHooks = themedSpecificHooks;
        else
        {
          Widget[] tempArray = specificHooks;
          specificHooks = new Widget[specificHooks.length + themedSpecificHooks.length];
          System.arraycopy(tempArray, 0, specificHooks, 0, tempArray.length);
          System.arraycopy(themedSpecificHooks, 0, specificHooks, tempArray.length, themedSpecificHooks.length);
        }
      }
    }
    else
      specificHooks = new Widget[0];
    // Find all of the unparented hooks and add them as global candidates
    Widget[] globalHooks;
    if (uiMgr == null || uiMgr.getModuleGroup() == null)
    {
      globalHooks = new Widget[0];
    }
    else
    {
      globalHooks = uiMgr.getGlobalHooks();
    }
    for (int i = 0; i < specificHooks.length + globalHooks.length; i++)
    {
      Widget hooky = (i < specificHooks.length) ? specificHooks[i] : globalHooks[i - specificHooks.length];
      if (hooky.getName().equals(hookName))
      {
        return processHookDirectly(hooky, hookVars, uiMgr, hookMenu);
      }
    }
    return null;
  }

  public static Object processHookDirectly(Widget hooky, Object[] hookVars, UIManager uiMgr, ZPseudoComp hookUI)
  {
    Context con = (hookUI == null) ? new Context(uiMgr) : hookUI.getRelatedContext().createChild();

    if (uiMgr != null && uiMgr.getTracer() != null)
      uiMgr.getTracer().traceHook(hooky, hookVars, hookUI);

    String[] hookVarNames = hookVarMap.get(hooky.getName());
    for (int j = 0; hookVars != null && j < hookVars.length && j < hookVarNames.length; j++)
    {
      con.setLocal(hookVarNames[j], hookVars[j]);
    }
    Widget[] hookKids = hooky.contents();
    Object hookKey = new Object();
    con.setLocal(HOOK_WIDGET_VAR, hookKey);

    // Establish the depth level for this variable. Otherwise embedded UI components in
    // OptionsMenus will store their result at a level too low to be seen by us.
    con.setLocal("ReturnValue", null);
    ExecutionPosition ep = null;
    for (int j = 0; j < hookKids.length; j++)
    {
      if ((ep = ZPseudoComp.processChain(hookKids[j], con, null, hookUI, false)) != null)
      {
        ep.addToStack(hookKids[j]);
        ep.markSafe();
        // Narflex - 10/2/12 - There's a long time bug here. Previously, we just had the above two lines
        // and then we'd call PseudoMenu.waitUntilHookedPopupsAreClosed(hookKey). The whole point of that
        // was that when a hook launched an options menu that we would not continue on with our processing
        // of the hook until that options menu was closed since we needed to delay our return value
        // from the hook until all processing was complete. There was a flaw in this though in that it did
        // not account for when we did other asynchronous calls from hooks. So now we added another
        // facility into ExecutionPosition that allows us to ask it when it's done (and also
        // any other aynchronous child tasks it spawned in it's processing) and then we just resume from there.
        // This will put us into the valid state for resuming shortly after the old technique for options menus;
        // which is fine anyways because now we're waiting until the above ep's resumeExecution is done as well
        // rather than just waiting for the popup to come off the stack in PseudoMenu. This also then covers
        // both the popup and async call situations. Leave the below code commented out
        // so in case something is bad about this fix we know the old way we did it easily.
        ep.waitForCompletion();
        // An OptionsMenu was spawned, we need to wait for it to close before we continue
        // with what we're doing

      }
    }
    return con.safeLookup("ReturnValue");
  }

  private static Map<String, JEP> parserCache =
      Collections.synchronizedMap(new HashMap<String, JEP>());

  public static Object evaluateExpression(String expr, final Context context, ZPseudoComp inUIComp, Widget src) throws Exception
  {
    if (LOG_REM_ACTIONS && expr.startsWith("\"REM"))
      System.out.println(expr);
    //System.out.println("evaluating: " + expr);
    Object rv = null;
    if (inUIComp != null)
      context.focusedVar = inUIComp.doesAncestorOrMeHaveFocus() ? Boolean.TRUE : Boolean.FALSE;


    if (ENABLE_STUDIO_DEBUG_CHECKS && context.getUIMgr() != null && context.getUIMgr().getTracer() != null) context.getUIMgr().getTracer().traceEvaluate(Tracer.PRE_EVALUATION, expr, src, context);
    JEP myParser = parserCache.get(expr);
    if (myParser == null)
    {
      if (inUIComp != null && expr.equals("GetFocusContext()"))
      {
        ZComp focusOwner = inUIComp.getFocusOwner(true);
        if (focusOwner instanceof ZPseudoComp)
        {
          ((ZPseudoComp) focusOwner).getRelatedContext().copyTo(context);
          rv = Boolean.TRUE;
        }
        else
          rv = Boolean.FALSE;
        inUIComp.setFocusListenState(ZPseudoComp.ALL_FOCUS_CHANGES);
      }
      else if (expr.equals("PassiveListen()"))
      {
        context.setLocal(PASSIVE_LISTEN_VAR, rv = Boolean.TRUE);
      }
      else
      {
        myParser = new JEP();
        // Check for an assignment operator
        String orgExpr = expr;
        int equalIdx = expr.indexOf('=');
        String resultSymbolName = null;
        if (equalIdx > 0)
        {
          int quoteIdx = expr.indexOf('"');
          if (quoteIdx == -1 || quoteIdx > equalIdx)
          {
            int parenIdx = expr.indexOf('(');
            if (parenIdx == -1 || parenIdx > equalIdx)
            {
              String assVarName = expr.substring(0, equalIdx).trim();
              if (assVarName.length() > 0 && Character.isJavaIdentifierStart(assVarName.charAt(0)))
              {
                boolean badVarName = false;
                for (int i = 1; i < assVarName.length(); i++)
                {
                  if (!Character.isJavaIdentifierPart(assVarName.charAt(i)))
                  {
                    badVarName = true;
                    break;
                  }
                }
                // don't mess up == comparisons
                if (!badVarName && expr.charAt(equalIdx + 1) != '=')
                {
                  resultSymbolName = assVarName;
                  expr = expr.substring(equalIdx + 1).trim();
                }
              }
            }
          }
        }
        if (resultSymbolName != null)
          myParser.setAssignmentVar(resultSymbolName);
        myParser.setFunctionTable(singleReflectionFunctionTable);
        myParser.parseExpression(expr);
        myParser.checkForSpecialVars(expr, pagingChangeVars);
        myParser.checkForVolatileVar(expr, "Focused");
        myParser.checkForVolatileVar2(expr, "\"Focused\"");
        myParser.checkForVolatileVar2(expr, "\"FocusedChild\"");
        if (myParser.hasError())
        {
          final String errStr = "Parsing Error: " + myParser.getErrorInfo() + "\r\nExpression: " + orgExpr;
          final Widget errWidg = src;
          myParser.clearError();
          Long lastErrTime = errorTimeMap.get(errStr);
          System.out.println(errStr);
          if (Sage.getBoolean("popup_on_action_errors", false) && context.getUIMgr() != null && context.getUIMgr().getGlobalFrame() != null &&
              (lastErrTime == null || (Sage.time() - lastErrTime.longValue() > REPEAT_ERROR_SPACING)) &&
              (Sage.time() - overallLastErrorTime > 2000) &&
              !context.getUIMgr().isFSEXMode())
          {
            EventQueue.invokeLater(new Runnable()
            {
              public void run()
              {
                String[] choices = new String[] { Sage.rez("Continue"), Sage.rez("Go_to_Error"), Sage.rez("Continue_Hide_Errors") };
                int theChoice = javax.swing.JOptionPane.showOptionDialog(context.getUIMgr().getGlobalFrame(), errStr,
                    "Parsing Error", 0, 0, null, choices, choices[1]);
                if (theChoice == 1 && errWidg != null)
                {
                  if (UIManager.ENABLE_STUDIO && context.getUIMgr() != null && Permissions.hasPermission(Permissions.PERMISSION_STUDIO, context.getUIMgr()) &&
                      context.getUIMgr().getStudio() != null)
                  {
                    context.getUIMgr().getStudio().showAndHighlightNode(errWidg);
                  }
                }
                else if (theChoice == 2)
                {
                  Sage.putBoolean("popup_on_action_errors", false);
                }
              }
            });
            errorTimeMap.put(errStr, new Long(Sage.time()));
            overallLastErrorTime = Sage.time();
          }
          return null;
        }
        myParser.clearError();
        parserCache.put(orgExpr, myParser);
      }
    }
    if (myParser != null)
    {
      rv = myParser.getValueAsObject(context, inUIComp);
      if (myParser.hasError())
      {
        final String errStr = "Evaluation Error: " + myParser.getErrorInfo() + "\r\nExpression: " + expr;
        final Widget errWidg = src;
        myParser.clearError();
        Long lastErrTime = errorTimeMap.get(errStr);
        System.out.println(errStr);
        if (Sage.getBoolean("popup_on_action_errors", false) && context.getUIMgr() != null && context.getUIMgr().getGlobalFrame() != null &&
            (lastErrTime == null || (Sage.time() - lastErrTime.longValue() > REPEAT_ERROR_SPACING)) &&
            (Sage.time() - overallLastErrorTime > 2000) &&
            !context.getUIMgr().isFSEXMode())
        {
          EventQueue.invokeLater(new Runnable()
          {
            public void run()
            {
              String[] choices = new String[] { Sage.rez("Continue"), Sage.rez("Go_to_Error"), Sage.rez("Continue_Hide_Errors") };
              int theChoice = javax.swing.JOptionPane.showOptionDialog(context.getUIMgr().getGlobalFrame(), errStr,
                  "Evaluation Error", 0, 0, null, choices, choices[1]);
              if (theChoice == 1 && errWidg != null)
              {
                if (UIManager.ENABLE_STUDIO && context.getUIMgr() != null && Permissions.hasPermission(Permissions.PERMISSION_STUDIO, context.getUIMgr()) &&
                    context.getUIMgr().getStudio() != null)
                {
                  context.getUIMgr().getStudio().showAndHighlightNode(errWidg);
                }
              }
              else if (theChoice == 2)
              {
                Sage.putBoolean("popup_on_action_errors", false);
              }
            }
          });
          errorTimeMap.put(errStr, new Long(Sage.time()));
          overallLastErrorTime = Sage.time();
        }
      }
      if (Sage.q != null)
        ((byte[])Sage.q)[6] = (byte) (((byte[])Sage.q)[112] + ((byte[])Sage.q)[45]); // piracy protection
    }
    if (!(rv instanceof AsyncTaskID))
      context.setLocal(null, rv);

    if (ENABLE_STUDIO_DEBUG_CHECKS && context.getUIMgr() != null && context.getUIMgr().getTracer() != null) context.getUIMgr().getTracer().traceEvaluate(Tracer.POST_EVALUATION, expr, src, context);
    return rv;
  }

  public static Boolean isExpressionFixedBoolResult(String expr)
  {
    JEP compExpr =null;
    try{
      compExpr = precompileExpression(expr);
    } catch (ParseException e){}
    if (compExpr == null)
      return null;
    if (compExpr.getAssignmentVar() != null)
      return null;
    return compExpr.getConstantBoolResult();
  }

  public static boolean isConstantExpression(String expr)
  {
    JEP compExpr =null;
    try{
      compExpr = precompileExpression(expr);
    } catch (ParseException e){}
    if (compExpr == null)
      return false;
    if (compExpr.getAssignmentVar() != null)
      return false;
    return compExpr.isConstant();
  }

  public static boolean isThisReferencedInExpression(String expr)
  {
    JEP compExpr =null;
    try{
      compExpr = precompileExpression(expr);
    } catch (ParseException e){}
    if (compExpr == null)
      return false;
    return compExpr.referencesThisVar();
  }

  // This will parse an expression and put the parsed version into the cache
  public static JEP precompileExpression(String expr) throws ParseException
  {
    if (expr.equals("GetFocusContext()") || expr.equals("PassiveListen()"))
      return null;
    JEP myParser = parserCache.get(expr);
    if (myParser == null)
    {
      myParser = new JEP();
      // Check for an assignment operator
      String orgExpr = expr;
      int equalIdx = expr.indexOf('=');
      String resultSymbolName = null;
      if (equalIdx > 0)
      {
        int quoteIdx = expr.indexOf('"');
        if (quoteIdx == -1 || quoteIdx > equalIdx)
        {
          int parenIdx = expr.indexOf('(');
          if (parenIdx == -1 || parenIdx > equalIdx)
          {
            String assVarName = expr.substring(0, equalIdx).trim();
            if (assVarName.length() > 0 && Character.isJavaIdentifierStart(assVarName.charAt(0)))
            {
              boolean badVarName = false;
              for (int i = 1; i < assVarName.length(); i++)
              {
                if (!Character.isJavaIdentifierPart(assVarName.charAt(i)))
                {
                  badVarName = true;
                  break;
                }
              }
              // don't mess up == comparisons
              if (!badVarName && expr.charAt(equalIdx + 1) != '=')
              {
                resultSymbolName = assVarName;
                expr = expr.substring(equalIdx + 1).trim();
              }
            }
          }
        }
      }
      if (resultSymbolName != null)
        myParser.setAssignmentVar(resultSymbolName);
      myParser.setFunctionTable(singleReflectionFunctionTable);
      myParser.parseExpression(expr);
      myParser.checkForSpecialVars(expr, pagingChangeVars);
      myParser.checkForVolatileVar(expr, "Focused");
      myParser.checkForVolatileVar2(expr, "\"Focused\"");
      myParser.checkForVolatileVar2(expr, "\"FocusedChild\"");
      if (myParser.hasError())
      {
        String errStr = "Parsing Error: " + myParser.getErrorInfo() + "\r\nExpression: " + orgExpr;
        myParser.clearError();
        System.out.println(errStr);
        throw new ParseException(errStr, 0);
      }
      myParser.clearError();
      parserCache.put(orgExpr, myParser);
    }
    return myParser;
  }

  // This will cache any parsing that can be done for this Widget
  // on success
  // throws a ParseException on failure
  public static void precompileWidget(Widget widg) throws ParseException
  {
    byte widgType = widg.type();
    if (widgType == Widget.ACTION || widgType == Widget.CONDITIONAL)
      precompileExpression(widg.getName());
    else if (widgType == Widget.BRANCH)
    {
      String widgName = widg.getName();
      if (!widgName.equals("else"))
        precompileExpression(widgName);
    }
    else if (widgType == Widget.ATTRIBUTE)
      precompileExpression(widg.getProperty(Widget.VALUE));
    else if (widgType != Widget.HOOK && widgType != Widget.LISTENER)
    {
      // go through all the properties to see if any are dynamic
      for (byte i = 0; i <= Widget.MAX_PROP_NUM; i++)
        if (widg.hasProperty(i))
        {
          String currProp = widg.getProperty(i);
          if (currProp.length() > 1 && currProp.charAt(0) == '=')
            precompileExpression(currProp.substring(1));
        }
    }
  }

  public static final class ExecutionPosition
  {
    public ExecutionPosition(ZPseudoComp uiContext, Widget lastWidget, Context connie)
    {
      ui = uiContext;
      widgList = new ArrayList<Widget>();
      widgList.add(lastWidget);
      context = connie;
    }

    // We don't worry about the validChainLinks set when resuming execution because those are only used
    // in UI data chains; and async calls are NOT allowed in UI data chains. (i.e. you can't use a Watch call to feed a text widget)
    public void resumeExecution()
    {
      if (!safeToResume)
      {
        synchronized (safeLock)
        {
          if (!safeToResume)
          {
            try
            {
              safeLock.wait(30000);
            }
            catch (Exception e){}
          }
        }
        if (!safeToResume)
        {
          System.out.println("WARNING: RESUMING SUSPENDED EXECUTION WITHOUT MARK SAFE!!!! widgList=" + widgList);
        }
      }
      if (!widgList.isEmpty())
      {
        Widget child = widgList.remove(0);
        ExecutionPosition ep;
        if (child != null && !child.isType(Widget.OPTIONSMENU))
        {
          // For non-OptionsMenu resumption, we also execute the children of the
          // widget we stopped on
          if (child.isType(Widget.ACTION))
          {
            Widget[] potKids = child.contents();
            for (int i = 0; i < potKids.length; i++)
            {
              if (potKids[i].isProcessChainType())
                if ((ep = ZPseudoComp.processChain(potKids[i], context, null,
                    ui, false)) != null)
                {
                  ep.completionTrace = this;
                  ep.addToStack(potKids[i]);
                  ep.addToStack(child);
                  ep.addToStack(widgList);
                  ep.markSafe();
                  return;
                }
            }
          }
          else if (child.isType(Widget.CONDITIONAL))
          {
            try
            {
              Object goodBranchVal = context.safeLookup(null);
              Widget[] actKids = child.contents(Widget.BRANCH);
              Widget elseBranch = null;
              boolean disableElse = false;
              for (int i = 0; i < actKids.length; i++)
              {
                String brStr = actKids[i].getName();
                if (brStr.equals("else"))
                {
                  elseBranch = actKids[i];
                  continue;
                }
                Object brEval = evaluateExpression(brStr, context, ui, actKids[i]);
                if (ZPseudoComp.testBranchValue(goodBranchVal, brEval))
                {
                  disableElse = true;
                  Widget[] branchKids = actKids[i].contents();
                  for (int j = 0; j < branchKids.length; j++)
                  {
                    if (branchKids[j].isProcessChainType())
                    {
                      if ((ep = ZPseudoComp.processChain(branchKids[j], context, null, ui, false)) != null)
                      {
                        ep.completionTrace = this;
                        ep.addToStack(branchKids[j]);
                        ep.addToStack(actKids[i]);
                        ep.addToStack(child);
                        ep.addToStack(widgList);
                        ep.markSafe();
                        return;
                      }
                    }
                  }
                  // early termination of if statements, this also presents the evaluation
                  // result from the Branch of overriding the default return for the processChain
                  // we just called
                  break;
                }
              }
              if (actKids.length == 0 && evalBool(goodBranchVal))
                elseBranch = child; // no branch, but true continues execution past the conditional
              if (!disableElse && elseBranch != null)
              {
                Widget[] branchKids = elseBranch.contents();
                for (int j = 0; j < branchKids.length; j++)
                  if (branchKids[j].isProcessChainType())
                  {
                    if ((ep = ZPseudoComp.processChain(branchKids[j], context, null, ui, false)) != null)
                    {
                      ep.completionTrace = this;
                      ep.addToStack(branchKids[j]);
                      ep.addToStack(elseBranch);
                      ep.addToStack(child);
                      ep.addToStack(widgList);
                      ep.markSafe();
                      return;
                    }
                  }
              }
            }
            catch (Throwable e)
            {
              System.out.println("Error invoking the method for: " + child + " of " + e);
              Sage.printStackTrace(e);
            }
          }
        }
        while (!widgList.isEmpty())
        {
          Widget parent = widgList.remove(0);
          if (parent != null)
          {
            Widget[] potKids = parent.contents();
            boolean passedChild = false;
            for (int i = 0; i < potKids.length; i++)
            {
              if (potKids[i] == child)
              {
                passedChild = true;
                continue;
              }
              if (!passedChild)
                continue;
              if (potKids[i].isProcessChainType())
                if ((ep = ZPseudoComp.processChain(potKids[i], context, null,
                    ui, false)) != null)
                {
                  ep.completionTrace = this;
                  ep.addToStack(potKids[i]);
                  ep.addToStack(parent);
                  ep.addToStack(widgList);
                  ep.markSafe();
                  return;
                }
            }
            child = parent;
          }
        }
      }
      markComplete();
    }

    private void markComplete()
    {
      synchronized (safeLock)
      {
        complete = true;
        safeLock.notifyAll();
      }
      if (completionTrace != null)
        completionTrace.markComplete();
    }

    public void waitForCompletion()
    {
      if (complete)
        return;
      synchronized (safeLock)
      {
        while (!complete && (ui == null || ui.getUIMgr() == null || ui.getUIMgr().isAlive()))
        {
          try{safeLock.wait(5000);}catch(Exception e){}
        }
      }
    }

    public ExecutionPosition addToStackFinal(Widget w)
    {
      addToStack(w);
      markSafe();
      return this;
    }
    public ExecutionPosition addToStack(Widget w)
    {
      if (safeToResume)
      {
        System.out.println("ERROR: addToStack called after markSafe() widgList=" + widgList);
        Thread.dumpStack();
      }
      // Sometimes there are adds redundantly called because of how the stack gets cleared when it
      // doesn't need to recurse in processChain.
      if (widgList.isEmpty() || widgList.get(widgList.size() - 1) != w)
        widgList.add(w);
      return this;
    }

    private void addToStack(List<Widget> stackList)
    {
      if (safeToResume)
      {
        System.out.println("ERROR: addToStack called after markSafe() stackList=" + stackList + " widgList=" + widgList);
        Thread.dumpStack();
      }
      widgList.addAll(stackList);
    }

    public void markSafe()
    {
      synchronized (safeLock)
      {
        safeToResume = true;
        safeLock.notifyAll();
      }
    }

    public ZPseudoComp getUI() { return ui; }
    public Context getContext() { return context; }

    private ZPseudoComp ui;
    private List<Widget> widgList;
    private Context context;
    private final Object safeLock = new Object();
    private boolean safeToResume;
    private boolean complete;
    private ExecutionPosition completionTrace;
  }

  public static final class FastStack
  {
    public FastStack()
    {
      stackData = new Object[10];
      stackPtr = -1;
    }

    public Object pop()
    {
      if (stackPtr == -1)
      {
        System.out.println("ERROR: Stack pointer is invalid in parser!");
        Thread.dumpStack();
        return null;
      }
      Object rv = stackData[stackPtr];
      stackData[stackPtr--] = null;
      return rv;
    }

    public Object peek()
    {
      if (stackPtr == -1)
      {
        System.out.println("ERROR: Stack pointer is invalid in parser!");
        Thread.dumpStack();
        return null;
      }
      return stackData[stackPtr];
    }

    public Object peek(int depth)
    {
      if (stackPtr - depth < 0)
      {
        System.out.println("ERROR: Stack pointer is invalid in parser!");
        Thread.dumpStack();
        return null;
      }
      return stackData[stackPtr - depth];
    }

    public void push(Object foo)
    {
      if (stackPtr == stackData.length - 1)
      {
        Object[] newStackData = new Object[stackData.length + 10];
        System.arraycopy(stackData, 0, newStackData, 0, stackData.length);
        stackData = newStackData;
      }
      stackData[++stackPtr] = foo;
    }

    public int size() { return stackPtr + 1; }

    public void clear()
    {
      if (stackPtr > -1)
        Arrays.fill(stackData, 0, stackPtr + 1, null);
      stackPtr = -1;
      uiMgr = null;
      uiComp = null;
    }

    public void setUIMgr(UIManager inUIMgr)
    {
      uiMgr = inUIMgr;
    }

    // NOTE: For the client sometimes you need to use getUIMgrSafe in case the request is going to go to the server
    // or it won't have any originating host to identify itself with when it's called from the external API
    public UIManager getUIMgr() { return uiMgr; }

    public UIManager getUIMgrSafe()
    {
      return (uiMgr != null) ? uiMgr : UIManager.getLocalUI();
    }


    public void setUIComponent(ZPseudoComp inUIComp)
    {
      uiComp = inUIComp;
    }

    public ZPseudoComp getUIComponent() { return uiComp; }


    Object[] stackData;
    int stackPtr;
    UIManager uiMgr; // used for getting this into the context of an API call
    ZPseudoComp uiComp; // used for getting this into the context of an API call
  }

  public static boolean isStaticFieldVariable(String varName)
  {
    // Our internal vars use a _ prefix sometimes
    if (varName.length() > 0 && varName.charAt(0) == '_') return false;
    int lastDot = varName.lastIndexOf('_');
    while (lastDot != -1)
    {
      String className = varName.substring(0, lastDot);
      try
      {
        Class<?> varClass = Class.forName(className.replace('_', '.'), true, Sage.extClassLoader);
        varName = varName.substring(lastDot + 1);
        Field theField = varClass.getField(varName);
        return true;
      }
      catch (Throwable t)
      {
        // These'll happen with legitimate variable names, so don't print out errors here
      }
      // This'll catch underscores in field names
      lastDot = varName.lastIndexOf('_', lastDot - 1);
    }
    return false;
  }

  public static Object getStaticFieldVariable(String varName)
  {
    // Our internal vars use a _ prefix sometimes
    if (varName.length() > 0 && varName.charAt(0) == '_') return null;
    String orgVarName = varName;
    int lastDot = varName.lastIndexOf('_');
    Throwable orgExcept = null;
    while (lastDot != -1)
    {
      String className = varName.substring(0, lastDot);
      try
      {
        Class<?> varClass = Class.forName(className.replace('_', '.'), true, Sage.extClassLoader);
        varName = varName.substring(lastDot + 1);
        Field theField = varClass.getField(varName);
        return theField.get(null);
      }
      catch (Throwable t)
      {
        // These'll happen with legitimate variable names, so don't print out errors here
        if (orgExcept == null)
          orgExcept = t;
      }
      // This'll catch underscores in field names
      lastDot = varName.lastIndexOf('_', lastDot - 1);
    }
    if (orgExcept != null)
    {
      System.out.println("Catbert Static Field lookup Failure for:" + orgVarName);
      System.out.println(orgExcept); // it's important this is shown in case there's library loading errors or other difficult things to debug
    }
    return null;
  }

  // This deals with scoped variable contexts and has a static context at the top level.
  // It deals with getting/setting the value in the right context, as well as being able
  // to share maps
  public static final class Context implements SymbolTable
  {
    // Variables we use a lot so we don't have to compare names
    public static final int ST_PASSIVE_LISTEN_VAR = 1;
    public static final int ST_HOOK_WIDGET_VAR = 2;
    public static final int ST_THIS = 3;
    public static final int ST_USEAIRINGSCHEDULE = 4;
    public static final int ST_TABLEROW = 5;
    public static final int ST_TABLECOL = 6;
    public static final int ST_DEFAULTFOCUS = 7;
    public static final int ST_RETURNVALUE = 8;

    // we lazily build the maps for these
    public Context()
    {
      parent = null;
      map = null;
    }

    public Context(Context inParent)
    {
      parent = inParent;
      map = null;
      uiMgr = parent.uiMgr; // get this field now for faster access later
    }

    // sometimes there is a predefined variable map, i.e. passing a staticContext to the next Menu
    public Context(Context inParent, Map<String, Object> inMap)
    {
      parent = inParent;
      map = inMap;
      uiMgr = parent.uiMgr; // get this field now for faster access later
    }

    public Context(UIManager inUIMgr)
    {
      parent = null;
      map = null;
      uiMgr = inUIMgr;
    }

    public Context createChild()
    {
      return new Context(this, null);
    }

    public void clear()
    {
      if (map != null)
        map.clear();
    }

    public static Context createNewMenuContext(UIManager inUIMgr)
    {
      Context rv = new Context(inUIMgr);
      rv.map = new ThreadSafeHashMap<String, Object>();
      rv.map.putAll(inUIMgr.getStaticContext());
      inUIMgr.getStaticContext().clear();
      return rv;
    }

    public boolean isDefined(String varName)
    {
      if (varName == null)
      {
        if (thisVarSet)
          return true;
        Context testParent = parent;
        while (testParent != null)
        {
          if (testParent.thisVarSet)
            return true;
          testParent = testParent.parent;
        }
        return false;
      }
      else if ("Focused".equals(varName))
      {
        return true;
      }
      else
      {
        if (map != null && map.containsKey(varName))
          return true;
        Context testParent = parent;
        while (testParent != null)
        {
          if (testParent.map != null && testParent.map.containsKey(varName))
            return true;
          testParent = testParent.parent;
        }
        uiMgr = getUIMgr();
        if (uiMgr != null && (uiMgr.getStaticContext().containsKey(varName) || uiMgr.getGlobalContext().containsKey(varName)))
          return true;
        if (isStaticFieldVariable(varName))
          return true;
        return false;
      }
    }

    public Object safeLookup(String varName)
    {
      if (varName == null)
      {
        if (thisVarSet)
          return thisVar;
        Context testParent = parent;
        while (testParent != null)
        {
          if (testParent.thisVarSet)
            return testParent.thisVar;
          testParent = testParent.parent;
        }
        return null;
      }
      if ("Focused".equals(varName))
      {
        if (focusedVar != null)
          return focusedVar;
        Context testParent = parent;
        while (testParent != null)
        {
          if (testParent.focusedVar != null)
            return testParent.focusedVar;
          testParent = testParent.parent;
        }
        return Boolean.FALSE; // if it's not defined, then it's not Focused
      }

      if (map != null && map.containsKey(varName))
        return map.get(varName);
      Context testParent = parent;
      while (testParent != null)
      {
        if (testParent.map != null && testParent.map.containsKey(varName))
          return testParent.map.get(varName);
        testParent = testParent.parent;
      }
      uiMgr = getUIMgr();
      if (uiMgr != null && uiMgr.getStaticContext().containsKey(varName))
        return uiMgr.getStaticContext().get(varName);
      if (uiMgr != null && uiMgr.getGlobalContext().containsKey(varName))
        return uiMgr.getGlobalContext().get(varName);
      // Narflex: 6-6-08 Don't bother checking to see if its actually a static field
      // because if it's not then this'll return null just like we would anyways.
      // So its faster when it actually is a static field now. :)
      return getStaticFieldVariable(varName);
    }

    Map<String, Object> getMap() {
      return (map == null) ? (map = new ThreadSafeHashMap<String, Object>()) : map;
    }

    Context getParent() { return parent; }

    public void setLocal(String varName, Object value)
    {
      // "this" is never actually passed in to this call
      if (varName == null)
      {
        thisVarSet = true;
        thisVar = value;
      }
      else
      {
        if (map == null)
          map = new ThreadSafeHashMap<String, Object>();
        map.put(varName, value);
      }
    }

    public Object getLocal(String varName)
    {
      return (map == null) ? null : map.get(varName);
    }

    public void set(String varName, Object value)
    {
      // "this" is never passed in here
      if (varName == null)
      {
        thisVarSet = true;
        thisVar = value;
        return;
      }
      if (map != null && map.containsKey(varName))
      {
        map.put(varName, value);
        return;
      }
      Context testParent = parent;
      while (testParent != null)
      {
        if (testParent.map != null && testParent.map.containsKey(varName))
        {
          testParent.map.put(varName, value);
          return;
        }
        testParent = testParent.parent;
      }
      uiMgr = getUIMgr();
      if (uiMgr != null && uiMgr.getStaticContext().containsKey(varName))
      {
        uiMgr.getStaticContext().put(varName, value);
        return;
      }
      if (uiMgr != null && uiMgr.getGlobalContext().containsKey(varName))
      {
        uiMgr.getGlobalContext().put(varName, value);
        return;
      }
      // Variable isn't defined anywhere, create it in this context
      setLocal(varName, value);
    }

    public void copyTo(Context export)
    {
      // We copy everything that's not defined in a higher level context
      if (parent != null)
        parent.copyTo(export);
      if (map != null)
      {
        for (Map.Entry<String, Object> currEnt : map.entrySet())
        {
          if ((export.parent == null && (uiMgr == null || (!uiMgr.getStaticContext().containsKey(currEnt.getKey()) &&
              !uiMgr.getGlobalContext().containsKey(currEnt.getKey())))) ||
              !export.parent.isDefined(currEnt.getKey()))
          {
            export.getMap().put(currEnt.getKey(), currEnt.getValue());
          }
        }
      }
    }

    public String toString()
    {
      return "Context[parent=" + parent + " map=" + map + ']';
    }

    public boolean containsKey(Object o)
    {
      // Modified on 1/16/2004 so that undefined variables default to null
      return true;
      //return (o != null) && isDefined(o.toString());
    }

    public Object get(Object o)
    {
      if (o != null)
        return safeLookup(o.toString());
      else
        return safeLookup(null);
    }

    public UIManager getUIMgr()
    {
      if (uiMgr != null)
        return uiMgr;
      else
      {
        Context tempParent = parent;
        while (tempParent != null)
        {
          if (tempParent.uiMgr != null)
            return tempParent.uiMgr;
          tempParent = tempParent.parent;
        }
        return null;
      }
    }

    public void setFocusedVar(Boolean x)
    {
      focusedVar = x;
    }

    private Context parent;
    private Map<String, Object> map;
    private Object focusedVar;
    private Object thisVar;
    private boolean thisVarSet;
    private UIManager uiMgr;
  }

  // NARFLEX - 2/19/10 - The purpose of this is to allow safe retrieval of values
  // from a map even if that map is being structurally modified at the same time.
  // Since we never remove things from the map; simply checking the size is safe to do.
  static class ThreadSafeHashMap<K, V> extends HashMap<K, V>
  {
    public ThreadSafeHashMap()
    {
      super();
    }

    public V get(Object key) {
      // Avoid entering the conditional loop if possible
      int oldSize = size();
      V rv = super.get(key);
      if (oldSize == size())
        return rv;
      do
      {
        oldSize = size();
        rv = super.get(key);
      } while (oldSize != size());
      return rv;
    }
  }

  public static Object getWatchFailureObject(int watchCode)
  {
    Object rv;
    switch (watchCode)
    {
      case VideoFrame.WATCH_OK:
        rv = Boolean.TRUE;
        break;
      case VideoFrame.WATCH_FAILED_FILES_NOT_ON_DISK:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_FILES_NOT_ON_DISK");
        break;
      case VideoFrame.WATCH_FAILED_PARENTAL_CHECK_FAILED:
        rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_PARENTAL_CHECK_FAILED");
        break;
      case VideoFrame.WATCH_FAILED_AIRING_EXPIRED:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_AIRING_EXPIRED");
        break;
      case VideoFrame.WATCH_FAILED_AIRING_NOT_STARTED:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_AIRING_NOT_STARTED");
        break;
      case VideoFrame.WATCH_FAILED_LIVE_STREAM_UKNOWN_INPUT:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_LIVE_STREAM_UKNOWN_INPUT");
        break;
      case VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_USER_REJECTED_CONFLICT");
        break;
      case VideoFrame.WATCH_FAILED_ALL_ENCODERS_UNDER_LIVE_CONTROL:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_ALL_ENCODERS_UNDER_LIVE_CONTROL");
        break;
      case VideoFrame.WATCH_FAILED_NO_ENCODERS_HAVE_STATION:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_NO_ENCODERS_HAVE_STATION");
        break;
      case VideoFrame.WATCH_FAILED_GENERAL_CANT_FIND_ENCODER:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_GENERAL_CANT_FIND_ENCODER");
        break;
      case VideoFrame.WATCH_FAILED_NULL_AIRING:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_NULL_AIRING");
        break;
      case VideoFrame.WATCH_FAILED_NO_PLAYLIST_RANDOM_ACCESS:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_NO_PLAYLIST_RANDOM_ACCESS");
        break;
      case VideoFrame.WATCH_FAILED_PLAYLIST_OVER:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_PLAYLIST_OVER");
        break;
      case VideoFrame.WATCH_FAILED_SURF_CONTEXT:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_SURF_CONTEXT");
        break;
      case VideoFrame.WATCH_FAILED_NETWORK_ERROR:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_NETWORK_ERROR");
        break;
      case VideoFrame.WATCH_FAILED_FORCE_REQUEST_WITHOUT_CONTROL:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_FORCE_REQUEST_WITHOUT_CONTROL");
        break;
      case VideoFrame.WATCH_FAILED_NO_MEDIA_PLAYER_FOR_TIMESHIFTED_FILE:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_NO_MEDIA_PLAYER_FOR_TIMESHIFTED_FILE");
        break;
      case VideoFrame.WATCH_FAILED_NO_MEDIA_PLAYER_FOR_FILE:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_NO_MEDIA_PLAYER_FOR_FILE");
        break;
      case VideoFrame.WATCH_FAILED_INSUFFICIENT_PERMISSIONS:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_INSUFFICIENT_PERMISSIONS");
        break;
      case VideoFrame.WATCH_FAILED_INSUFFICIENT_RESOURCES_WHILE_RECORDING:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_INSUFFICIENT_RESOURCES_WHILE_RECORDING");
        break;
      case VideoFrame.WATCH_FAILED_GENERAL_SEEKER:
      default:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("PLAYBACK_FAILED_GENERAL");
        break;
    }
    return rv;
  }

  public static Object getRecordFailureObject(int watchCode)
  {
    Object rv;
    switch (watchCode)
    {
      case VideoFrame.WATCH_OK:
        rv = Boolean.TRUE;
        break;
      case VideoFrame.WATCH_FAILED_AIRING_EXPIRED:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("RECORD_FAILED_AIRING_EXPIRED");
        break;
      case VideoFrame.WATCH_FAILED_USER_REJECTED_CONFLICT:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("RECORD_FAILED_USER_REJECTED_CONFLICT");
        break;
      case VideoFrame.WATCH_FAILED_ALL_ENCODERS_UNDER_LIVE_CONTROL:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("RECORD_FAILED_ALL_ENCODERS_UNDER_LIVE_CONTROL");
        break;
      case VideoFrame.WATCH_FAILED_NO_ENCODERS_HAVE_STATION:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("RECORD_FAILED_NO_ENCODERS_HAVE_STATION");
        break;
      case VideoFrame.WATCH_FAILED_GENERAL_CANT_FIND_ENCODER:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("RECORD_FAILED_GENERAL_CANT_FIND_ENCODER");
        break;
      case VideoFrame.WATCH_FAILED_NULL_AIRING:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("RECORD_FAILED_GENERAL_CANT_FIND_ENCODER");
        break;
      case VideoFrame.WATCH_FAILED_INSUFFICIENT_PERMISSIONS:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("RECORD_FAILED_INSUFFICIENT_PERMISSIONS");
        break;
      case VideoFrame.WATCH_FAILED_GENERAL_SEEKER:
      default:
          rv = Sage.rez("ERROR") + " (" + watchCode + "): " + Sage.rez("RECORD_FAILED_GENERAL");
        break;
    }
    return rv;
  }

  public static boolean evalBool(Object o)
  {
    if (o instanceof Boolean)
      return ((Boolean) o).booleanValue();
    else if (o instanceof Number)
      return ((Number) o).doubleValue() != 0;
    else if (o != null)
    {
      String s = o.toString();
      if (s != null && (s.equalsIgnoreCase("t") || s.equalsIgnoreCase("true")))
        return true;
      else
        return false;
    }
    else
      return false;
  }

  static Object evaluateAction(String uiContext, String methodName, Object[] args) throws sage.jep.ParseException
  {
    PostfixMathCommandI filtMeth =
        (PostfixMathCommandI) singleReflectionFunctionTable.get(methodName);
    FastStack stack = new FastStack();
    stack.setUIMgr(UIManager.getLocalUIByName(uiContext));
    int argLength = (args != null) ? args.length : 0;
    stack.stackPtr = argLength - 1;
    stack.stackData = args;
    if (args == null)
      stack.stackData = Pooler.EMPTY_OBJECT_ARRAY;
    filtMeth.setCurNumberOfParameters(argLength);
    filtMeth.run(stack);
    return stack.pop();
  }

  public static String getPrettyStringList(String[] strs)
  {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < strs.length; i++)
    {
      if (i > 0)
      {
        if (i < strs.length - 1)
          sb.append(", ");
        else
          sb.append(" " + Sage.rez("and") + " ");
      }
      sb.append(strs[i]);
    }
    return sb.toString();
  }

  // [0] is the code, [1] is the modifiers
  public static int[] getKeystrokeFromString(String s)
  {
    if (s == null || s.length() == 0)
      return new int[] { 0, 0 };
    if ("+".equals(s))
      return new int[] { KeyEvent.VK_PLUS, 0 };
    int code = 0;
    int mods = 0;
    if (s.endsWith("+"))
    {
      code = KeyEvent.VK_PLUS;
      s = s.substring(0, s.length() - 1);
    }
    else
    {
      int lastPlus = s.lastIndexOf('+');
      String codestr;
      if (lastPlus != -1)
      {
        codestr = s.substring(lastPlus + 1);
        s = s.substring(0, lastPlus);
      }
      else
      {
        codestr = s;
        s = "";
      }
      Integer o = keystrokeNameMap.get(codestr);
      if (o != null)
      {
        code = o.intValue();
      }
      else
      {
        code = codestr.toUpperCase().charAt(0);
      }
    }
    String ctrlName = KeyEvent.getKeyModifiersText(KeyEvent.CTRL_MASK);
    String altName = KeyEvent.getKeyModifiersText(KeyEvent.ALT_MASK);
    String shiftName = KeyEvent.getKeyModifiersText(KeyEvent.SHIFT_MASK);
    String metaName = KeyEvent.getKeyModifiersText(KeyEvent.META_MASK);
    StringTokenizer toker = new StringTokenizer(s, "+");
    while (toker.hasMoreTokens())
    {
      String modName = toker.nextToken();
      if (modName.equalsIgnoreCase(ctrlName))
        mods = mods | KeyEvent.CTRL_MASK;
      else if (modName.equalsIgnoreCase(altName))
        mods = mods | KeyEvent.ALT_MASK;
      else if (modName.equalsIgnoreCase(shiftName))
        mods = mods | KeyEvent.SHIFT_MASK;
      else if (modName.equalsIgnoreCase(metaName))
        mods = mods | KeyEvent.META_MASK;
    }
    return new int[] { code, mods };
  }

  public static String getStringFromKeystroke(int code, int mods)
  {
    String str = "";
    if (mods != 0)
      str += KeyEvent.getKeyModifiersText(mods);
    if (code != KeyEvent.VK_SHIFT &&
        code != KeyEvent.VK_ALT &&
        code != KeyEvent.VK_CONTROL)
    {
      if (mods != 0)
        str += "+";
      str += KeyEvent.getKeyText(code);
    }
    return str;
  }

  public static ThreadLocal<String> uiContextThreadNames = new ThreadLocal<String>();

  private static final class ReflectedJEPFunction extends PostfixMathCommand
      implements PostfixMathCommandI
  {
    public ReflectedJEPFunction(String inName)
    {
      methodName = inName;
      numberOfParameters = -1; // variable arguments
      String currMethodName = methodName;
      isConstructor = currMethodName.startsWith("new_");
      if (isConstructor)
        currMethodName = currMethodName.substring(4) + "_<init>";
      int lastDot = currMethodName.lastIndexOf('_');
      if (lastDot != -1)
      {
        className = currMethodName.substring(0, lastDot);
        if (!isConstructor)
          subMethodName = currMethodName.substring(lastDot + 1);
      }
    }

    public void run(FastStack stack) throws sage.jep.ParseException
    {
      // Check if stack is null
      if (null == stack)
      {
        throw new sage.jep.ParseException("Stack argument null");
      }
      if (stack.getUIMgr() != null)
      {
        uiContextThreadNames.set(stack.getUIMgr().getLocalUIClientName());
      }
      //System.out.println("Invoking reflected method: " + methodName);
      Object[] args = new Object[curNumberOfParameters];
      Object result = null;
      // args are in reverse order when popped
      for (int i = args.length - 1; i >= 0; i--)
        args[i] = stack.pop();

      Object instObj = null;
      /*
       * instObj is set in the lookup if we're using a singleton, otherwise
       * we need to wait until the method check to see if we need the instance
       * variable or not. In all those cases, the first argument will be the this
       * variable.  So if instObj == null, and we're looking at an instance method,
       * then the first argument in that list would be the this object for that method
       * (but not necessarily referenced by 'this')
       */
      try
      {
        if (actClass == null)
          actClass = Class.forName(className.replace('_', '.'), true, Sage.extClassLoader);
        Class<?>[][] paramTypes = null;
        paramTypes = new Class[curNumberOfParameters][];
        for (int i = 0; i < curNumberOfParameters; i++)
        {
          if (args[i] instanceof Integer)
          {
            paramTypes[i] = new Class[] { args[i].getClass(), Integer.TYPE};
          }
          else if (args[i] instanceof Short)
          {
            paramTypes[i] = new Class[] { args[i].getClass(), Short.TYPE };
          }
          else if (args[i] instanceof Long)
          {
            paramTypes[i] = new Class[] { args[i].getClass(), Long.TYPE };
          }
          else if (args[i] instanceof Byte)
          {
            paramTypes[i] = new Class[] { args[i].getClass(), Byte.TYPE};
          }
          else if (args[i] instanceof Double)
          {
            paramTypes[i] = new Class[] { args[i].getClass(), Double.TYPE};
          }
          else if (args[i] instanceof Float)
          {
            paramTypes[i] = new Class[] { args[i].getClass(), Float.TYPE};
          }
          else if (args[i] instanceof Boolean)
          {
            paramTypes[i] = new Class[] { args[i].getClass(), Boolean.TYPE};
          }
          else if (args[i] instanceof Character)
          {
            paramTypes[i] = new Class[] { args[i].getClass(), Character.TYPE};
          }
          else if (args[i] instanceof Airing)
            paramTypes[i] = new Class[] { Airing.class }; // for FakeAiring
          else if (args[i] instanceof Person)
            paramTypes[i] = new Class[] { Person.class, String.class };
          else if (args[i] == null)
            paramTypes[i] = null;
          else
            paramTypes[i] = new Class[] { args[i].getClass() };
        }
        // Search the method list for ones that have the correct # of args, with matching types.
        // If its an instance method, then be sure we have an instance object and if
        // not, the first argument will be used as it
        if (isConstructor)
        {
          Constructor<?> theConst = null;
          if (allConstructors == null)
          {
            synchronized (this)
            {
              if (allConstructors == null)
              {
                Constructor<?>[] xallConstructors = actClass.getConstructors();
                matchedParamTypes = new Class[xallConstructors.length][];
                for (int i = 0; i < xallConstructors.length; i++)
                  matchedParamTypes[i] = xallConstructors[i].getParameterTypes();
                allConstructors = xallConstructors;
              }
            }
          }
          Class<?>[] methArgs = null;
          for (int i = 0; i < allConstructors.length; i++)
          {
            methArgs = matchedParamTypes[i];
            if (methArgs.length == paramTypes.length)
            {
              boolean badArgs = false;
              for (int j = 0; j < methArgs.length; j++)
              {
                if (paramTypes[j] != null)
                {
                  Class<?>[] currTypes = paramTypes[j];
                  int k = 0;
                  for (; k < currTypes.length; k++)
                  {
                    if (methArgs[j].isAssignableFrom(currTypes[k]))
                    {
                      break;
                    }
                  }
                  if (k == currTypes.length)
                  {
                    // We didn't find a matching type
                    badArgs = true;
                    break;
                  }
                }
                else if (methArgs[j].isPrimitive())
                {
                  // Don't allow null to be passed as a primitive type
                  badArgs = true;
                  break;
                }
              }
              if (!badArgs)
              {
                theConst = allConstructors[i];
                break;
              }
            }
          }
          if (theConst == null)
          {
            for (int i = 0; i < allConstructors.length; i++)
            {
              methArgs = matchedParamTypes[i];
              if (methArgs.length == paramTypes.length)
              {
                boolean badArgs = false;
                for (int j = 0; j < methArgs.length; j++)
                {
                  if (paramTypes[j] != null)
                  {
                    Class<?>[] currTypes = paramTypes[j];
                    int k = 0;
                    for (; k < currTypes.length; k++)
                    {
                      if (methArgs[j].isAssignableFrom(currTypes[k]))
                        break;
                      if (currTypes[k] == Byte.TYPE || currTypes[k] == Short.TYPE ||
                          currTypes[k] == Integer.TYPE || currTypes[k] == Long.TYPE ||
                          currTypes[k] == Float.TYPE || currTypes[k] == Double.TYPE)
                      {
                        if (methArgs[j].isPrimitive() && methArgs[j] != Boolean.TYPE &&
                            methArgs[j] != Character.TYPE)
                        {
                          // All numeric primitive types can be converted between each other
                          break;
                        }
                      }
                    }
                    if (k == currTypes.length)
                    {
                      // We didn't find a matching type
                      badArgs = true;
                      break;
                    }
                  }
                  else if (methArgs[j].isPrimitive())
                  {
                    // Don't allow null to be passed as a primitive type
                    badArgs = true;
                    break;
                  }
                }
                if (!badArgs)
                {
                  theConst = allConstructors[i];
                  break;
                }
              }
            }
            if (theConst == null)
              throw new sage.jep.ParseException("UNKNOWN CONSTRUCTOR ERROR name=" + methodName + " args=" + Arrays.asList(args));
          }
          for (int i = 0; i < args.length; i++)
          {
            if (paramTypes[i] != null && methArgs[i].isPrimitive() && !methArgs[i].isAssignableFrom(((Class[])paramTypes[i])[1]))
            {
              if (methArgs[i] == Byte.TYPE)
                args[i] = ((Number) args[i]).byteValue();
              else if (methArgs[i] == Short.TYPE)
                args[i] = ((Number) args[i]).shortValue();
              else if (methArgs[i] == Integer.TYPE)
                args[i] = ((Number) args[i]).intValue();
              else if (methArgs[i] == Long.TYPE)
                args[i] = ((Number) args[i]).longValue();
              else if (methArgs[i] == Float.TYPE)
                args[i] = ((Number) args[i]).floatValue();
              else if (methArgs[i] == Double.TYPE)
                args[i] = ((Number) args[i]).doubleValue();
            } else if (args[i] instanceof Person && methArgs[i].isAssignableFrom(String.class)) {
              args[i] = ((Person) args[i]).toString();
            }
          }
          result = theConst.newInstance(args);
        }
        else
        {
          if (nameMatchedMeths == null)
          {
            synchronized (this)
            {
              if (nameMatchedMeths == null)
              {
                List<Method> tempvec = new ArrayList<Method>();
                Method[] allMethods = actClass.getMethods();
                for (int i = 0; i < allMethods.length; i++)
                  if (allMethods[i].getName().equals(subMethodName))
                    tempvec.add(allMethods[i]);
                Method[] nameMatchedMethsx = tempvec.toArray(new Method[0]);
                matchedParamTypes = new Class[nameMatchedMethsx.length][];
                for (int i = 0; i < nameMatchedMethsx.length; i++)
                  matchedParamTypes[i] = nameMatchedMethsx[i].getParameterTypes();
                nameMatchedMeths = nameMatchedMethsx;
              }
            }
          }
          Method theMeth = null;
          Class<?>[] methArgs = null;
          for (int i = 0; i < nameMatchedMeths.length; i++)
          {
            methArgs = matchedParamTypes[i];
            boolean needThis = ((nameMatchedMeths[i].getModifiers() & Modifier.STATIC) == 0) &&
                (instObj == null);
            if ((methArgs.length == paramTypes.length && !needThis) ||
                ((methArgs.length == paramTypes.length - 1) && needThis))
            {
              boolean badArgs = false;
              for (int j = 0; j < methArgs.length; j++)
              {
                if (paramTypes[j + (needThis?1:0)] != null)
                {
                  Class<?>[] currTypes = paramTypes[j + (needThis?1:0)];
                  int k = 0;
                  for (; k < currTypes.length; k++)
                  {
                    if (methArgs[j].isAssignableFrom(currTypes[k]))
                    {
                      break;
                    }
                  }
                  if (k == currTypes.length)
                  {
                    // We didn't find a matching type
                    badArgs = true;
                    break;
                  }
                }
                else if (methArgs[j].isPrimitive())
                {
                  // Don't allow null to be passed as a primitive type
                  badArgs = true;
                  break;
                }
              }
              if (!badArgs)
              {
                theMeth = nameMatchedMeths[i];
                break;
              }
            }
          }
          if (theMeth == null)
          {
            for (int i = 0; i < nameMatchedMeths.length; i++)
            {
              methArgs = matchedParamTypes[i];
              boolean needThis = ((nameMatchedMeths[i].getModifiers() & Modifier.STATIC) == 0) &&
                  (instObj == null);
              if ((methArgs.length == paramTypes.length && !needThis) ||
                  ((methArgs.length == paramTypes.length - 1) && needThis))
              {
                boolean badArgs = false;
                for (int j = 0; j < methArgs.length; j++)
                {
                  int j2 = j + (needThis?1:0);
                  if (paramTypes[j2] != null)
                  {
                    Class<?>[] currTypes = paramTypes[j2];
                    int k = 0;
                    for (; k < currTypes.length; k++)
                    {
                      if (methArgs[j].isAssignableFrom(currTypes[k]))
                        break;
                      if (currTypes[k] == Byte.TYPE || currTypes[k] == Short.TYPE ||
                          currTypes[k] == Integer.TYPE || currTypes[k] == Long.TYPE ||
                          currTypes[k] == Float.TYPE || currTypes[k] == Double.TYPE)
                      {
                        if (methArgs[j].isPrimitive() && methArgs[j] != Boolean.TYPE &&
                            methArgs[j] != Character.TYPE)
                        {
                          break;
                        }
                      }
                    }
                    if (k == currTypes.length)
                    {
                      badArgs = true;
                      break;
                    }
                  }
                  else if (methArgs[j].isPrimitive())
                  {
                    // Don't allow null to be passed as a primitive type
                    badArgs = true;
                    break;
                  }
                }
                if (!badArgs)
                {
                  theMeth = nameMatchedMeths[i];
                  break;
                }
              }
            }
            if (theMeth == null)
              throw new sage.jep.ParseException("UNKNOWN METHOD ERROR name=" + methodName + " args=" + Arrays.asList(args));
          }
          int paramTypesOffset = 0;
          if (((theMeth.getModifiers() & Modifier.STATIC) == 0) && (instObj == null))
          {
            instObj = args[0];
            args = Arrays.asList(args).subList(1, args.length).toArray();
            paramTypesOffset = 1;
          }
          for (int i = 0; i < args.length; i++)
          {
            if (paramTypes[i] != null && methArgs[i].isPrimitive() && !methArgs[i].isAssignableFrom(((Class[])paramTypes[i + paramTypesOffset])[1]))
            {
              if (methArgs[i] == Byte.TYPE)
                args[i] = new Byte(((Number) args[i]).byteValue());
              else if (methArgs[i] == Short.TYPE)
                args[i] = new Short(((Number) args[i]).shortValue());
              else if (methArgs[i] == Integer.TYPE)
                args[i] = new Integer(((Number) args[i]).intValue());
              else if (methArgs[i] == Long.TYPE)
                args[i] = new Long(((Number) args[i]).longValue());
              else if (methArgs[i] == Float.TYPE)
                args[i] = new Float(((Number) args[i]).floatValue());
              else if (methArgs[i] == Double.TYPE)
                args[i] = new Double(((Number) args[i]).doubleValue());
            } else if (args[i] instanceof Person && methArgs[i].isAssignableFrom(String.class)) {
              args[i] = ((Person) args[i]).toString();
            }
          }
          result = theMeth.invoke(instObj, args);
        }
      }
      catch (Throwable e)
      {
        Sage.printStackTrace(e);
        if (e instanceof InvocationTargetException)
          Sage.printStackTrace(((InvocationTargetException) e).getTargetException());
        throw new sage.jep.ParseException("Error in method reflection of " + methodName + " of " + e, e);
      }

      // push the result on the inStack
      stack.push(result);
    }

    // NOTE We don't cache the reflected method object because of operator overloading
    private String methodName;
    private Class<?> actClass;
    private Method[] nameMatchedMeths;
    private Constructor<?>[] allConstructors;
    private Class<?>[][] matchedParamTypes;
    private String subMethodName;
    private boolean isConstructor;
    private String className;
  }

  /**
   * Install API functions outside of initialization.
   * @param foo
   */
  public static void installExtraFunction(PredefinedJEPFunction foo) {
    singleReflectionFunctionTable.put(foo);
  }

  public static final class ReflectionFunctionTable extends FunctionTable
  {
    public ReflectionFunctionTable()
    {
      super();
      put(null, null);
      put("", null);
      sage.api.Global.init(this);
      sage.api.AlbumAPI.init(this);
      sage.api.WidgetAPI.init(this);
      sage.api.Configuration.init(this);
      sage.api.CaptureDeviceAPI.init(this);
      sage.api.CaptureDeviceInputAPI.init(this);
      sage.api.AiringAPI.init(this);
      sage.api.PersonAPI.init(this);
      sage.api.FavoriteAPI.init(this);
      sage.api.MediaFileAPI.init(this);
      sage.api.MediaPlayerAPI.init(this);
      sage.api.Utility.init(this);
      sage.api.ChannelAPI.init(this);
      sage.api.Database.init(this);
      sage.api.PlaylistAPI.init(this);
      sage.api.ShowAPI.init(this);
      sage.api.TranscodeAPI.init(this);
      sage.api.TVEditorialAPI.init(this);
      sage.api.SeriesInfoAPI.init(this);
      sage.api.SystemMessageAPI.init(this);
      sage.api.MediaNodeAPI.init(this);
      sage.api.PluginAPI.init(this);
      sage.api.UserRecordAPI.init(this);
      sage.api.Security.init(this);
      // Load the commonly reflected API calls to speed them up
      loadCommonlyReflectedAPI(this);
    }

    public void put(PredefinedJEPFunction foo)
    {
      if (super.containsKey(foo.getMethodName()))
      {
        if (Sage.DBG) System.out.println("DUPLICATION ERROR IN API: " + foo.getMethodName());
      }
      put(foo.getMethodName(), foo);
      List<String> vec = categoryMethodMap.get(foo.getGroup());
      if (vec == null)
        categoryMethodMap.put(foo.getGroup(), vec = new ArrayList<String>());
      vec.add(foo.getMethodName());
    }

    // The only methods it calls are containsKey(Object) and get(Object).
    // All gets are cast to the PostfixMathCommandI interface
    public boolean containsKey(Object key)
    {
      // This is still a clean way to support all of our default functions
      // NOTE: THIS DOES NOT ALLOW FOR OPERATOR OVERLOADING...ACTUALLY IT DOES,
      // WE ONLY DETERMINE EXISTENCE HERE, just always say we support a variable
      // number of arguments. When the method is actually run, then we will have
      // the arguments and can do our parameter matching
      if (super.containsKey(key))
        return true;

      // See if reflection can resolve this one, first get a class name

      String methodName = key.toString();
      boolean isConstructor = methodName.startsWith("new_");
      if (isConstructor)
        methodName = methodName.substring(4) + "_<init>";
      int lastDot = methodName.lastIndexOf('_');
      while (lastDot != -1)
      {
        try
        {
          String className = methodName.substring(0, lastDot);
          Class<?> actClass = Class.forName(className.replace('_', '.'), true, Sage.extClassLoader);
          methodName = methodName.substring(lastDot + 1);
          Method theMeth = null;
          // Search the method list for ones that have the correct # of args, if there's
          // only one of those, then just try and call that one. Otherwise, we'll have to
          // pick the most suitable one by using isAssignableFrom
          Method[] allMethods = actClass.getMethods();
          if (isConstructor)
          {
            String reflectName = key.toString();
            put(reflectName, new ReflectedJEPFunction(reflectName));
            return true;
          }
          else
          {
            for (int i = 0; i < allMethods.length; i++)
              if (allMethods[i].getName().equals(methodName))
              {
                String reflectName = key.toString();
                put(reflectName, new ReflectedJEPFunction(reflectName));
                return true;
              }
          }
        }
        catch (ClassNotFoundException ce)
        {
          // These can be normal due to vars with _ in them; but more serious recursive errors we have to catch and display in debug
        }
        catch (Throwable e)
        {
          System.out.println("Catbert Method Lookup Failure for:" + key);
          System.out.println(e); // it's important this is shown in case there's library loading errors or other difficult things to debug
        }
        // In case there's underscores in the variable or method name, this will catch it
        lastDot = methodName.lastIndexOf('_', lastDot - 1);
      }
      return false;
    }

    public Object get(Object key)
    {
      Object rv = super.get(key);
      if (rv != null)
        return rv;
      String reflectName = key.toString();
      put(reflectName, rv = new ReflectedJEPFunction(reflectName));
      return rv;
    }

    // This is hidden from the API in the Studio that shows up in the Action properties because the RSS
    // category is not included in PredefinedJEPFunction.categoryDescriptions
    private void loadCommonlyReflectedAPI(ReflectionFunctionTable rft)
    {
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_Translate_decode", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return Translate.decode(getString(stack));
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSItem_getTitle", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSItem)stack.pop()).getTitle();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSItem_getDescription", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSItem)stack.pop()).getDescription();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSItem_getCleanDescription", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSItem)stack.pop()).getCleanDescription();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSItem_getDate", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSItem)stack.pop()).getDate();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSChannel_getItems", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSChannel)stack.pop()).getItems();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "java_lang_String_replaceAll", new String[] {"Foo", "Foo2", "Foo3"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          String s3 = getString(stack);
          String s2 = getString(stack);
          String s1 = getString(stack);
          if (s1 == null || s2 == null || s3 == null) return s1;
          return s1.replaceAll(s2, s3);
        }});
      rft.put(new PredefinedJEPFunction("RSS", "java_lang_String_trim", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          String s1 = getString(stack);
          return s1 == null ? s1 : s1.trim();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "new_sage_media_rss_RSSHandler")
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return new RSSHandler();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "new_java_net_URL", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return new URL(getString(stack));
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSParser_parseXmlFile", new String[] {"Foo", "Foo2", "Foo3" })
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          boolean b = evalBool(stack.pop());
          org.xml.sax.helpers.DefaultHandler dh = (org.xml.sax.helpers.DefaultHandler) stack.pop();
          Object obj = stack.pop();
          URL url;
          if (obj instanceof URL)
            url = (URL) obj;
          else
            url = new URL(obj.toString());
          RSSParser.parseXmlFile(url, dh, b);
          return null;
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSHandler_getRSSChannel", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSHandler)stack.pop()).getRSSChannel();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSItem_getMediaGroup", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSItem)stack.pop()).getMediaGroup();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSMediaGroup_getThumbURL", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSMediaGroup)stack.pop()).getThumbURL();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSMediaGroup_getContent", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSMediaGroup)stack.pop()).getContent();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSMediaContent_getType", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSMediaContent)stack.pop()).getType();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSMediaContent_getUrl", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSMediaContent)stack.pop()).getUrl();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSMediaContent_getDuration", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSMediaContent)stack.pop()).getDuration();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSItem_getEnclosure", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSItem)stack.pop()).getEnclosure();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSEnclosure_getUrl", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSEnclosure)stack.pop()).getUrl();
        }});
      rft.put(new PredefinedJEPFunction("RSS", "sage_media_rss_RSSEnclosure_getLength", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return ((RSSEnclosure)stack.pop()).getLength();
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "new_java_io_File", -1, new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          if (curNumberOfParameters == 2)
          {
            String s2 = getString(stack);
            Object o1 = stack.pop();
            if (o1 instanceof File)
              return new File((File) o1, s2);
            else
              return new File(o1 == null ? null : o1.toString(), s2);
          }
          else
          {
            Object o1 = stack.pop();
            if (o1 instanceof URI)
              return new File((URI) o1);
            else
              return new File(o1.toString());
          }
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_lang_Object_getClass", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          Object o = stack.pop();
          return (o == null) ? null : o.getClass();
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "new_java_util_Properties", -1, new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          if (curNumberOfParameters == 1)
            return new Properties((Properties) stack.pop());
          else
            return new Properties();
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_io_File_isFile", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          File f = getFile(stack);
          return (f == null || !f.isFile()) ? Boolean.FALSE : Boolean.TRUE;
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_io_File_canWrite", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          File f = getFile(stack);
          return (f == null || !f.canWrite()) ? Boolean.FALSE : Boolean.TRUE;
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_util_Properties_getProperty", -1, new String[] {"Foo", "Foo2"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          if (curNumberOfParameters == 3)
          {
            String s2 = getString(stack);
            String s1 = getString(stack);
            return ((Properties)stack.pop()).getProperty(s1, s2);
          }
          else
          {
            String s1 = getString(stack);
            return ((Properties)stack.pop()).getProperty(s1);
          }
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_lang_String_split", -1, new String[] {"Foo", "Foo2"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          if (curNumberOfParameters == 3)
          {
            int s2 = getInt(stack);
            String s1 = getString(stack);
            return getString(stack).split(s1, s2);
          }
          else
          {
            String s1 = getString(stack);
            return getString(stack).split(s1);
          }
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "new_java_util_HashMap", -1, new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          if (curNumberOfParameters == 2)
          {
            float f = getFloat(stack);
            int i = getInt(stack);
            return new HashMap<Object, Object>(i, f);
          }
          else if (curNumberOfParameters == 1)
          {
            Object o1 = stack.pop();
            if (o1 instanceof Map)
              return new HashMap<Object, Object>((Map<?, ?>) o1);
            else
              return new HashMap<Object, Object>(Integer.parseInt(o1.toString()));
          }
          else
            return new HashMap<Object, Object>();
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "new_java_util_Vector", -1, new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          if (curNumberOfParameters == 2)
          {
            int f = getInt(stack);
            int i = getInt(stack);
            return new Vector<Object>(i, f);
          }
          else if (curNumberOfParameters == 1)
          {
            Object o1 = stack.pop();
            if (o1 instanceof Collection)
              return new Vector<Object>((Collection<?>) o1);
            else if (o1 instanceof Object[])
              return new Vector<Object>(Arrays.asList((Object[]) o1));
            else if (o1 == null)
              return new Vector<Object>();
            else
              return new Vector<Object>(Integer.parseInt(o1.toString()));
          }
          else
            return new Vector<Object>();
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_util_Vector_add", -1, new String[] {"Foo", "Foo2"})
      {
        @SuppressWarnings("unchecked")
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          Object o1 = stack.pop();
          if (curNumberOfParameters == 3)
          {
            int idx = getInt(stack);
            ((Vector<Object>) stack.pop()).add(idx, o1);
            return null;
          }
          else
            return ((Vector<Object>) stack.pop()).add(o1) ? Boolean.TRUE : Boolean.FALSE;
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "new_java_util_HashSet", -1, new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          if (curNumberOfParameters == 2)
          {
            float f = getFloat(stack);
            int i = getInt(stack);
            return new HashSet<Object>(i, f);
          }
          else if (curNumberOfParameters == 1)
          {
            Object o1 = stack.pop();
            if (o1 instanceof Collection)
              return new HashSet<Object>((Collection<?>) o1);
            else if (o1 instanceof Object[])
              return new HashSet<Object>(Arrays.asList((Object[]) o1));
            else if (o1 == null)
              return new HashSet<Object>();
            else
              return new HashSet<Object>(Integer.parseInt(o1.toString()));
          }
          else
            return new HashSet<Object>();
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_io_File_listFiles", -1, new String[] {"Foo", "Foo2"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          if (curNumberOfParameters == 2)
          {
            Object o1 = stack.pop();
            File f = getFile(stack);
            if (o1 instanceof FileFilter)
              return f.listFiles((FileFilter) o1);
            else
              return f.listFiles((FilenameFilter) o1);
          }
          else
          {
            return getFile(stack).listFiles();
          }
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_util_Arrays_asList", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return Arrays.asList((Object[]) stack.pop());
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_util_List_toArray", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          Object o = stack.pop();
          if (o == null)
            return o;
          if (o.getClass().isArray())
            return o;
          return ((List<?>)o).toArray();
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_lang_String_toLowerCase", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return getString(stack).toLowerCase();
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_io_File_isDirectory", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          File f = getFile(stack);
          return (f == null || !f.isDirectory()) ? Boolean.FALSE : Boolean.TRUE;
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_io_File_isHidden", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          File f = getFile(stack);
          return (f == null || !f.isHidden()) ? Boolean.FALSE : Boolean.TRUE;
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_io_File_toString", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return stack.pop().toString();
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_util_Map_keySet", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          Map<?, ?> map = (Map<?, ?>) stack.pop();
          return map.keySet();
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_util_HashSet_contains", new String[] {"Foo", "Foo2"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          Object o = stack.pop();
          Set<?> s = (Set<?>) stack.pop();
          return (s != null && s.contains(o)) ? Boolean.TRUE : Boolean.FALSE;
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_lang_Math_abs", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          Object obj = stack.pop();
          if (obj instanceof Double)
            return new Double(Math.abs(((Double)obj).doubleValue()));
          else if (obj instanceof Long)
            return new Long(Math.abs(((Long)obj).longValue()));
          else if (obj instanceof Float)
            return new Float(Math.abs(((Float)obj).floatValue()));
          else if (obj instanceof Number)
            return new Integer(Math.abs(((Number)obj).intValue()));
          else
          {
            String str = obj.toString();
            if (str.indexOf('.') == -1)
              return new Integer(Math.abs(Integer.parseInt(str)));
            else
              return new Float(Math.abs(Float.parseFloat(str)));
          }
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_lang_Integer_toHexString", new String[] {"Foo"})
      {
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          return Integer.toHexString(getInt(stack));
        }});
      rft.put(new PredefinedJEPFunction("Reflection", "java_lang_Class_forName", -1, new String[] {"Foo", "Foo2", "Foo3"})
      {
        // We added this so it will use our dynamic class loader
        public Object runSafely(Catbert.FastStack stack) throws Exception{
          if (curNumberOfParameters == 3)
          {
            stack.pop(); // ignore the class loader they specified
            boolean init = evalBool(stack.pop());
            String s1 = getString(stack);
            return Class.forName(s1, init, Sage.extClassLoader);
          }
          else
          {
            String s1 = getString(stack);
            return Class.forName(s1, true, Sage.extClassLoader);
          }
        }});
    }
  }
}
