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
package sage.api;

import sage.Catbert;
import sage.PredefinedJEPFunction;
import sage.PseudoMenu;
import sage.Sage;
import sage.SageConstants;
import sage.UIManager;
import sage.Widget;
import sage.WidgetFidget;
import sage.WidgetMeta;
import sage.ZPseudoComp;

/**
 * Widget reflection API
 */
public class WidgetAPI {
  private WidgetAPI() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Widget", "LoadSTVFile", new String[] { "STVFile" })
    {
      /**
       * Loads a new SageTV Application Definition file that defines the entire user interface for SageTV
       * @param STVFile the new .stv file that should be loaded for the UI
       * @return true if it was succesful, otherwise an error string
       *
       * @declaration public Object LoadSTVFile(java.io.File STVFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        if (stack.getUIMgr() == null) return Boolean.FALSE;
        try
        {
          stack.getUIMgr().freshStartup(f);
        }
        catch (Throwable e)
        {
          return "There was an error loading the file of: " + e;
        }
        return Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "ImportSTVFile", new String[] { "STVFile" })
    {
      /**
       * Imports a SageTV Application Definition file into the current STV file that is loaded. This will essentially merge the two together.
       * @param STVFile the .stv file that should be imported into the currently loaded one
       * @return true if it was succesful, otherwise an error string
       *
       * @declaration public Object ImportSTVFile(java.io.File STVFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        if (stack.getUIMgr() == null) return Boolean.FALSE;
        try
        {
          // 601
          //Wizard.getInstance().importWidgetFile(stack.getUIMgr(), f);

          stack.getUIMgr().getModuleGroup().importXML(f, stack.getUIMgr());

          if (stack.getUIMgr().getBoolean("save_stv_after_import", true))
          {
            String fname = stack.getUIMgr().getModuleGroup().defaultModule.description();
            if (fname.toLowerCase().endsWith(".stv"))
            {
              // Convert to an XML filename
              fname = fname.substring(0, fname.lastIndexOf('.')) + ".xml";
            }
            int i = 1;
            String basefname = fname;
            int lastDot = basefname.lastIndexOf('.');
            if (lastDot != -1)
            {
              basefname = basefname.substring(0, lastDot);
              // Now check for a numeric extension
              int lastDash = basefname.lastIndexOf('-');
              if (lastDash != -1)
              {
                try
                {
                  int lastNum = Integer.parseInt(basefname.substring(lastDash + 1));
                  i = lastNum;
                  basefname = basefname.substring(0, lastDash);
                }
                catch (NumberFormatException e)
                {
                  // Non-numeric suffix, don't try to increment it.
                }
              }
            }
            while (new java.io.File(fname).isFile())
            {
              // Add a number to the name so we save to a different file automatically
              fname = basefname + "-" + i + ".xml";
              i++;
            }
            try
            {
              stack.getUIMgr().getModuleGroup().defaultModule.saveXML(new java.io.File(fname), null);
            }
            catch (tv.sage.SageException e)
            {
              e.printStackTrace();
              return "There was an error saving the file: " + e;
            }
            try
            {
              stack.getUIMgr().freshStartup(new java.io.File(fname));
            }
            catch (Throwable e)
            {
              e.printStackTrace();
              return "There was an error reloading the saved file: " + e;
            }
          }
        }
        catch (Throwable e)
        {
          e.printStackTrace();
          return "There was an error loading the file of: " + e;
        }
        return Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "IsSTVModified")
    {
      /**
       * Returns true if the currently loaded STV has been modified at all since its last save
       * @return true if the currently loaded STV has been modified at all since its last save
       * @since 6.1
       *
       * @declaration public boolean IsSTVModified();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() == null) return Boolean.FALSE;
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr.getModuleGroup().defaultModule != null)
        {
          String fname = uiMgr.getModuleGroup().defaultModule.description();
          java.io.File f = new java.io.File(fname);
          // NOTE: This can miss changes if the file's timestamp is in the future!
          if (!f.isFile() || uiMgr.getModuleGroup().lastModified() > f.lastModified())
          {
            return Boolean.TRUE;
          }
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetAllWidgets")
    {
      /**
       * Gets all of the Widgets that are in the currently loaded STV
       * @return all of the Widgets that are in the currently loaded STV
       *
       * @declaration public Widget[] GetAllWidgets();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return (stack.getUIMgr() == null) ? null : stack.getUIMgr().getModuleGroup().getWidgets();
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetWidgetsByType", new String[] { "WidgetType" })
    {
      /**
       * Gets all of the Widgets that are in the currently loaded STV that are of the specified type
       * @param WidgetType the name of the widget type
       * @return all of the Widgets that are in the currently loaded STV that are of the specified type
       *
       * @declaration public Widget[] GetWidgetsByType(String WidgetType);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        return (stack.getUIMgr() == null) ? null : stack.getUIMgr().getModuleGroup().getWidgets(WidgetMeta.getTypeForName(s));
      }});
    rft.put(new PredefinedJEPFunction("Widget", "AddWidget", new String[] { "WidgetType" })
    {
      /**
       * Creates a new Widget of the specified type and adds it to the STV
       * @param WidgetType the type of the new Widget
       * @return the newly created Widget
       *
       * @declaration public Widget AddWidget(String WidgetType);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        return stack.getUIMgr() == null ? null : stack.getUIMgr().getModuleGroup().addWidget(WidgetMeta.getTypeForName(s));
      }});
    rft.put(new PredefinedJEPFunction("Widget", "AddWidgetWithSymbol", new String[] { "WidgetType", "Symbol" })
    {
      /**
       * Creates a new Widget of the specified type and adds it to the STV. This also allows specifying the desired symbol to use for the Widget.
       * If the symbol is already in use; then a new symbol will automatically be assigned to this Widget instead.
       * @param WidgetType the type of the new Widget
       * @param Symbol the symbol name for the new widget (UID)
       * @return the newly created Widget
       * @since 7.0
       *
       * @declaration public Widget AddWidgetWithSymbol(String WidgetType, String Symbol);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String symb = getString(stack);
        String s = getString(stack);
        return stack.getUIMgr() == null ? null : stack.getUIMgr().getModuleGroup().addWidget(WidgetMeta.getTypeForName(s), symb);
      }});
    rft.put(new PredefinedJEPFunction("Widget", "RemoveWidget", new String[] { "Widget" })
    {
      /**
       * Removes a Widget from the STV
       * @param Widget the Widget (or a String which represents the symbol for that Widget) to remove
       *
       * @declaration public void RemoveWidget(Widget Widget);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgr().getModuleGroup().removeWidget(getWidget(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "AddWidgetChild", new String[] { "WidgetParent", "WidgetChild" })
    {
      /**
       * Creates a parent-child relationship between two Widgets. If the relationship already exists, this call has no effect.
       * This new child will be the last child of the parent.
       * @param WidgetParent the Widget (or a String which represents the symbol for that Widget) that should be the parent in the relationship
       * @param WidgetChild the Widget (or a String which represents the symbol for that Widget) that should be the child in the relationship
       *
       * @declaration public void AddWidgetChild(Widget WidgetParent, Widget WidgetChild);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Widget cw = getWidget(stack);
        Widget pw = getWidget(stack);
        if (cw != null && pw != null && pw.willContain(cw))
          WidgetFidget.contain(pw, cw);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "InsertWidgetChild", new String[] { "WidgetParent", "WidgetChild", "ChildIndex" })
    {
      /**
       * Creates a parent-child relationship between two Widgets. Since parent-child relationships are ordered, this allows
       * specifying where in that order this relationship should be.
       * @param WidgetParent the Widget (or a String which represents the symbol for that Widget) that should be the parent in the relationship
       * @param WidgetChild the Widget (or a String which represents the symbol for that Widget) that should be the child in the relationship
       * @param ChildIndex the 0-based index in the parent's child relationships list that the new relationship should occupy
       *
       * @declaration public void InsertWidgetChild(Widget WidgetParent, Widget WidgetChild, int ChildIndex);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int idx = getInt(stack);
        Widget cw = getWidget(stack);
        Widget pw = getWidget(stack);
        if (cw != null && pw != null && pw.willContain(cw))
          WidgetFidget.contain(pw, cw, idx);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "RemoveWidgetChild", new String[] { "WidgetParent", "WidgetChild" })
    {
      /**
       * Breaks a parent-child relationships between two Widgets. If the Widgets do not have the specified parent-child relationship
       * then there is no effect.
       * @param WidgetParent the parent of the Widget (or a String which represents the symbol for that Widget) relationship to break
       * @param WidgetChild the child of the Widget (or a String which represents the symbol for that Widget) relationship to break
       *
       * @declaration public void RemoveWidgetChild(Widget WidgetParent, Widget WidgetChild);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Widget cw = getWidget(stack);
        Widget pw = getWidget(stack);
        if (cw != null && pw != null)
          WidgetFidget.discontent(pw, cw);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "IsWidgetParentOf", new String[] { "WidgetParent", "WidgetChild" })
    {
      /**
       * Returns true if the specified Widgets have a parent-child relationship.
       * @param WidgetParent the parent Widget (or a String which represents the symbol for that Widget) to test
       * @param WidgetChild the child Widget (or a String which represents the symbol for that Widget) to test
       * @return true if the specified parent has a parent-child relationship with the specified child, false otherwise
       *
       * @declaration public boolean IsWidgetParentOf(Widget WidgetParent, Widget WidgetChild);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Widget cw = getWidget(stack);
        Widget pw = getWidget(stack);
        if (cw != null && pw != null)
          return pw.contains(cw) ? Boolean.TRUE : Boolean.FALSE;
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetWidgetType", new String[] { "Widget" })
    {
      /**
       * Returns the type of a Widget
       * @param Widget the Widget (or a String which represents the symbol for that Widget) object
       * @return the type name of the specified Widget
       *
       * @declaration public String GetWidgetType(Widget Widget);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Widget w = getWidget(stack);
        return (w != null) ? Widget.TYPES[w.type()] : null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "HasWidgetProperty", new String[] { "Widget", "PropertyName" })
    {
      /**
       * Returns true if the specified Widget has a property defined with the specified name
       * @param Widget the Widget object (or a String which represents the symbol for that Widget)
       * @param PropertyName the name of the property to check existence of
       * @return true if the specified Widget has a property defined with the specified name, false otherwise
       *
       * @declaration public boolean HasWidgetProperty(Widget Widget, String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String pn = getString(stack);
        Widget w = getWidget(stack);
        return ((w != null) && w.hasProperty(WidgetMeta.getPropForName(pn))) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "SetWidgetProperty", new String[] { "Widget", "PropertyName", "PropertyValue" })
    {
      /**
       * Sets a property in a Widget to a specified value. If that property is already defined, this will overwrite it.
       * @param Widget the Widget object (or a String which represents the symbol for that Widget)
       * @param PropertyName the name of the property to set in the Widget
       * @param PropertyValue the value to set the property to
       *
       * @declaration public void SetWidgetProperty(Widget Widget, String PropertyName, String PropertyValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String pv = getString(stack);
        String pn = getString(stack);
        Widget w = getWidget(stack);
        if (w != null)
          WidgetFidget.setProperty(w, WidgetMeta.getPropForName(pn), pv);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetWidgetProperty", new String[] { "Widget", "PropertyName" })
    {
      /**
       * Returns the value for a specified property in a Widget
       * @param Widget the Widget object (or a String which represents the symbol for that Widget)
       * @param PropertyName the name of the property to get
       * @return the value for a specified property in a Widget
       *
       * @declaration public String GetWidgetProperty(Widget Widget, String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String pn = getString(stack);
        Widget w = getWidget(stack);
        if (w != null)
          return w.getProperty(WidgetMeta.getPropForName(pn));
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetWidgetName", new String[] { "Widget" })
    {
      /**
       * Returns the name of the specified Widget
       * @param Widget the Widget object (or a String which represents the symbol for that Widget)
       * @return the name of the specified Widget
       *
       * @declaration public String GetWidgetName(Widget Widget);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Widget w = getWidget(stack);
        return (w != null) ? w.getUntranslatedName() : null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "SetWidgetName", new String[] { "Widget", "Name" })
    {
      /**
       * Sets the name for a Widget
       * @param Widget the Widget object (or a String which represents the symbol for that Widget)
       * @param Name the value to set the name to for this Widget
       *
       * @declaration public void SetWidgetName(Widget Widget, String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        Widget w = getWidget(stack);
        if (w != null)
          WidgetFidget.setName(w, s);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetWidgetParents", new String[] { "Widget" })
    {
      /**
       * Gets the list of Widgets that are parents of the specified Widget. The ordering of this list has no effect.
       * @param Widget the Widget object (or a String which represents the symbol for that Widget)
       * @return a list of Widgets which are all parents of the specified Widget
       *
       * @declaration public Widget[] GetWidgetParents(Widget Widget);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Widget w = getWidget(stack);
        return (w != null) ? w.containers() : null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetWidgetChildren", new String[] { "Widget" })
    {
      /**
       * Gets the list of Widgets that are children of the specified Widget. The ordering of this list does have an effect.
       * @param Widget the Widget object (or a String which represents the symbol for that Widget)
       * @return a list of Widgets which are all children of the specified Widget
       *
       * @declaration public Widget[] GetWidgetChildren(Widget Widget);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Widget w = getWidget(stack);
        return (w != null) ? w.contents() : null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "ExecuteWidgetChain", new String[] { "Widget" })
    {
      /**
       * Executes a Widget and the chain of child Widgets underneath it
       * @param Widget the root of the Widget (or a String which represents the symbol for that Widget) action chain to execute
       * @return the value returned by the last executed Widget in the chain
       *
       * @declaration public Object ExecuteWidgetChain(Widget Widget);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Widget w = getWidget(stack);
        if (w != null)
        {
          Catbert.Context con = new Catbert.Context(stack.getUIMgr());
          Catbert.ExecutionPosition ep = ZPseudoComp.processChain(w, con, null, null, false);
          if (ep != null)
          {
            ep.addToStackFinal(w);
          }
          return con.get(null);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "ExecuteWidgetChainInCurrentMenuContext", new String[] { "Widget" })
    {
      /**
       * Executes a Widget and the chain of child Widgets underneath it. This will use the context of the currently loaded menu to do this which
       * is useful if you want to launch an OptionsMenu programatically w/ the proper parent context. NOTE: If this does launch an OptionsMenu then the
       * value returned from this function will not be usable and this call will return once the OptionsMenu is launched. Once it is closed the core
       * will resume execution of the widget chain using one of its own internal threads at that point.
       * @param Widget the root of the Widget (or a String which represents the symbol for that Widget) action chain to execute
       * @return the value returned by the last executed Widget in the chain
       * @since 7.0
       *
       * @declaration public Object ExecuteWidgetChainInCurrentMenuContext(Widget Widget);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Widget w = getWidget(stack);
        if (w != null)
        {
          UIManager uiMgr = stack.getUIMgr();
          Catbert.Context con;
          if (uiMgr == null || uiMgr.getCurrUI() == null)
            con = new Catbert.Context(stack.getUIMgr());
          else
            con = uiMgr.getCurrUI().getUI().getRelatedContext().createChild();
          Catbert.ExecutionPosition ep = ZPseudoComp.processChain(w, con, null, null, false);
          if (ep != null)
          {
            ep.addToStackFinal(w);
          }
          return con.get(null);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "LaunchMenuWidget", new String[] { "Widget" })
    {
      /**
       * Launches a new menu in SageTV with the specified Widget as the menu's definition.
       * @param Widget the Widget object (or a String which represents the symbol for that Widget) to use for the launched menu, this must be a Menu type Widget
       *
       * @declaration public void LaunchMenuWidget(Widget Widget);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Widget w = getWidget(stack);
        if (w != null && stack.getUIMgr() != null)
          stack.getUIMgr().advanceUI(w);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetCurrentSTVFile")
    {
      /**
       * Gets the STV file that is currently loaded by the system
       * @return the STV file that is currently loaded by the system
       *
       * @declaration public String GetCurrentSTVFile();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgr() == null ? null : stack.getUIMgr().getModuleGroup().defaultModule.description();
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetWidgetChild", new String[] { "Widget", "Type", "Name" })
    {
      /**
       * Searches the children of the specified Widget for one with the specified type and name. If no match
       * is found then null is returned. If there are multiple matches then the first one is returned.
       * @param Widget the Widget (or a String which represents the symbol for that Widget) who's children should be searched
       * @param Type the type of the Widget to search for, if null than any type will match
       * @param Name the name that the Widget to search for must match, if null than any name will match
       * @return the Widget child of the specified Widget of the specified type and name
       *
       * @declaration public Widget GetWidgetChild(Widget Widget, String Type, String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String name = getString(stack);
        byte type = WidgetMeta.getTypeForName(getString(stack));
        Widget w = getWidget(stack);
        if (w == null)
          return null;
        Widget[] wkids = (type == -1) ? w.contents() : w.contents(type);
        if (name == null)
          return (wkids.length > 0) ? wkids[0] : null;
          else
          {
            for (int i = 0; i < wkids.length; i++)
              if (wkids[i].getUntranslatedName().equals(name))
                return wkids[i];
            return null;
          }
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetWidgetParent", new String[] { "Widget", "Type", "Name" })
    {
      /**
       * Searches the parents of the specified Widget for one with the specified type and name. If no match
       * is found then null is returned. If there are multiple matches then the first one is returned.
       * @param Widget the Widget (or a String which represents the symbol for that Widget) who's parents should be searched
       * @param Type the type of the Widget to search for, if null than any type will match
       * @param Name the name that the Widget to search for must match, if null than any name will match
       * @return the Widget parent of the specified Widget of the specified type and name
       *
       * @declaration public Widget GetWidgetParent(Widget Widget, String Type, String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String name = getString(stack);
        byte type = WidgetMeta.getTypeForName(getString(stack));
        Widget w = getWidget(stack);
        if (w == null)
          return null;
        Widget[] wkids = (type == -1) ? w.containers() : w.containers(type);
        if (name == null)
          return (wkids.length > 0) ? wkids[0] : null;
          else
          {
            for (int i = 0; i < wkids.length; i++)
              if (wkids[i].getUntranslatedName().equals(name))
                return wkids[i];
            return null;
          }
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetCurrentMenuWidget")
    {
      /**
       * Gets the Widget the defines the menu that is currently loaded by the system
       * @return the Widget the defines the menu that is currently loaded by the system
       *
       * @declaration public Widget GetCurrentMenuWidget();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        PseudoMenu pm = (stack.getUIMgr() == null) ? null : stack.getUIMgr().getCurrUI();
        return (pm == null) ? null : pm.getBlueprint();
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetWidgetMenuHistory")
    {
      /**
       * Gets a list of the Widgets that have defined the menus that were recently displayed in the UI
       * @return a list of the Widgets that have defined the menus that were recently displayed in the UI
       *
       * @declaration public Widget[] GetWidgetMenuHistory();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgr() == null ? null : stack.getUIMgr().getUIHistoryWidgets();
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetWidgetMenuBackHistory")
    {
      /**
       * Gets a list of the Widgets that have defined the menus that were recently displayed in the UI.
       * Unlike {@link #GetWidgetMenuHistory GetWidgetMenuHistory()} this only returns Menus that are
       * 'Back' (not Forward) in the navigations the user has performed. Similar to getting only the 'Back'
       * history in a web browser.
       * @return a list of the Widgets that have defined the menus that were recently displayed in the UI
       * @since 5.1
       *
       * @declaration public Widget[] GetWidgetMenuBackHistory();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgr() == null ? null : stack.getUIMgr().getUIBackHistoryWidgets();
      }});
    rft.put(new PredefinedJEPFunction("Widget", "EvaluateExpression", new String[] { "Expression" })
    {
      /**
       * Evaluates the passed in expression and returns the result. This is executed in a new variable context w/out any
       * user interface context.
       * @param Expression the expression string to evaluate
       * @return the result of evaluating the specified expression
       *
       * @declaration public Object EvaluateExpression(String Expression);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String expr = getString(stack);
        return Catbert.evaluateExpression(expr, new Catbert.Context(stack.getUIMgr()), null, null);
      }});
    rft.put(new PredefinedJEPFunction("Widget", "SaveWidgetsAsXML", new String[] { "File", "Overwrite" })
    {
      /**
       * Saves all of the current Widgets as an XML file. Same as the "Save a Copy as XML..." in the Studio.
       * @param File the file to write to
       * @param Overwrite if true then if the File exists it will be overwritten
       * @return true if successful, false if not
       *
       * @declaration public boolean SaveWidgetsAsXML(java.io.File File, boolean Overwrite);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean overwrite = evalBool(stack.pop());
        java.io.File selFile = getFile(stack);
        if (stack.getUIMgr() == null) return Boolean.FALSE;
        if (selFile.toString().indexOf('.') == -1)
          selFile = new java.io.File(selFile.toString() + ".xml");
        if (selFile.isFile() && !overwrite)
        {
          return Boolean.FALSE;
        }
        try
        {
          stack.getUIMgr().getModuleGroup().defaultModule.saveXML(selFile, null);
          return Boolean.TRUE;
        }
        catch (tv.sage.SageException e)
        {
          if (Sage.DBG) System.out.println("There was an error saving the file: " + e);
          return Boolean.FALSE;
        }
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetWidgetSymbol", new String[] { "Widget" })
    {
      /**
       * Returns the UID symbol for the specified Widget
       * @param Widget the Widget object
       * @return the UID symbol which is used to represent this widget uniquely
       * @since 6.4
       *
       * @declaration public String GetWidgetSymbol(Widget Widget);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Widget w = getWidget(stack);
        return (w != null) ? w.symbol() : null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "FindWidgetBySymbol", new String[] { "Symbol" })
    {
      /**
       * Returns the Widget represented by the specified UID symbol
       * @param Symbol the UID symbol to lookup the Widget for
       * @return the Widget who's symbol matches the argument, null if it cannot be found
       * @since 6.4
       *
       * @declaration public Widget FindWidgetBySymbol(String Symbol);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        return (stack.getUIMgr() != null) ? stack.getUIMgr().getModuleGroup().symbolMap.get(s) : null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetDefaultSTVFile")
    {
      /**
       * Returns the file path for the default STV file
       * @return the file path for the default STV file
       * @since 6.4
       *
       * @declaration public java.io.File GetDefaultSTVFile();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new java.io.File(System.getProperty("user.dir"), "STVs" + java.io.File.separatorChar +
            ("SageTV7" + java.io.File.separatorChar + "SageTV7.xml"));

      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetUIWidgetContext")
    {
      /**
       * Returns the Widget for the corresponding UI component that this execution originated from. For
       * 'green' process chains; this will correspond to the UI component that received the event. For 'blue'
       * UI chains; this will correspond to the UI component who's conditionality is being determined or who's data
       * is being evaluated. This will be null if there is no UI context; such as for non-UI hooks and calls made from
       * Java directly.
       * @return the Widget that corresponds to the UI context used for the current evaluation, null if there is no context
       * @since 6.6
       *
       * @declaration public Widget GetUIWidgetContext();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIComponent() != null)
          return stack.getUIComponent().getWidget();
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetSTVName")
    {
      /**
       * Returns the value of the 'STVName' Attribute under the Global Theme Widget. This is used for dependencies relating to plugins.
       * @return the value of the 'STVName' Attribute under the Global Theme Widget
       * @since 7.0
       *
       * @declaration public String GetSTVName();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getSTVName();

      }});
    rft.put(new PredefinedJEPFunction("Widget", "GetSTVVersion")
    {
      /**
       * Returns the value of the 'STVVersion' Attribute under the Global Theme Widget. This is used for dependencies relating to plugins.
       * @return the value of the 'STVVersion' Attribute under the Global Theme Widget
       * @since 7.0
       *
       * @declaration public String GetSTVVersion();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return stack.getUIMgrSafe().getSTVVersion();

      }});
    /*
		rft.put(new PredefinedJEPFunction("Widget", "", 0)
		{public Object runSafely(Catbert.FastStack stack) throws Exception{

			}});
     */
  }
}
