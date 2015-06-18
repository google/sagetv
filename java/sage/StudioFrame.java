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

import java.awt.Dimension;


public class StudioFrame implements java.awt.event.ActionListener, STVEditor, Tracer
{
  private static final String LAST_STV_BROWSE_DIR = "last_stv_browse_dir";
  public StudioFrame()
  {
    wiz = Wizard.getInstance();
    widgetOperations = new java.util.Stack();
    traceData = new TracerOp[Sage.getInt("studio/trace_depth", 1000)];
    breakpoints = new java.util.Vector();

    repoMaster = new sage.version.CVSRepository();
  }

  public void setUIMgr(UIManager inUIMgr)
  {
    uiMgr = inUIMgr;
    java.io.File breakpointFile = new java.io.File(Sage.get("studio/breakpoints_file", "Breakpoints.properties"));
    if (breakpointFile.isFile())
    {
      try
      {
        breakpoints = Breakpoint.loadBreakpoints(breakpointFile, uiMgr);
      }
      catch (Exception e)
      {
        javax.swing.JOptionPane.showMessageDialog(null, "There was an error loading the breakpoints file:" + e);
      }
    }
  }

  public synchronized void show()
  {
    if (myFrame == null)
    {
      javax.swing.UIDefaults uidef = javax.swing.UIManager.getLookAndFeelDefaults();
      uidef.put("TreeUI", "sage.SageTreeUI");

      for (int i = 0; i < Widget.TYPES.length; i++)
      {
        uiMgr.setCursor(Widget.TYPES[i] + "Cursor",
            ImageUtils.createCursor("images/studio/" + Widget.TYPES[i] + ".gif",
                Widget.TYPES[i] + "Drag", 8, 8, false, false));

        uiMgr.setCursor(Widget.TYPES[i] + "CopyCursor",
            ImageUtils.createCursor("images/studio/" +
                Widget.TYPES[i] + ".gif",
                Widget.TYPES[i] + "CopyDrag", 8, 8, true, false));

        uiMgr.setCursor(Widget.TYPES[i] + "LinkCursor",
            ImageUtils.createCursor("images/studio/" +
                Widget.TYPES[i] + ".gif",
                Widget.TYPES[i] + "LinkDrag", 8, 8, false, true));

        uiMgr.setCursor(Widget.TYPES[i] + "NoDropCursor",
            ImageUtils.createCursor(
                ImageUtils.addImageEffect(
                    ImageUtils.fullyLoadImage("images/studio/"+
                        Widget.TYPES[i] + ".gif"),
                        ImageUtils.CIRCLE_SLASH |
                        ImageUtils.TRANSPARENT),
                        Widget.TYPES[i] + "BadDrag", 8, 8, false, false));

        uiMgr.setImage(Widget.TYPES[i], uiMgr.getImage("images/studio/" + Widget.TYPES[i] + ".gif"));

        uiMgr.setIcon(Widget.TYPES[i], new javax.swing.ImageIcon(uiMgr.getImage(Widget.TYPES[i])));
      }
      myFrame = new javax.swing.JFrame("SageTV Studio - [" + uiMgr.getModuleGroup().defaultModule.description() + ']' +
          (!new java.io.File(uiMgr.getModuleGroup().defaultModule.description()).canWrite() ? " READ ONLY" : "") +
          (uiMgr.arePluginImportsActive() ? " ***IMPORTS ACTIVE***" : ""));
      model = new OracleTreeModel();
      model.setUIMgr(uiMgr);
      tree = new OracleTree(uiMgr, model);
      model.setTreeUI(tree);
      tree.setDisplayUIDs(Sage.getBoolean("studio/display_widget_uids", false));
      tree.setDisplayAttributeValues(Sage.getBoolean("studio/display_attribute_values", true));
      myFrame.getContentPane().setLayout(new java.awt.BorderLayout());
      conl = new javax.swing.JLabel(" ")
      {
        public void paint(java.awt.Graphics g)
        {
          super.paint(g);
          java.awt.Color og = g.getColor();
          g.setColor(inSuspension ? java.awt.Color.red :
            (suspendExecution ? java.awt.Color.yellow : java.awt.Color.green));
          g.fillOval(conl.getWidth() - conl.getHeight() - 3, 3, conl.getHeight() - 6, conl.getHeight() - 6);
          g.setColor(og);
        }
      };
      conl.setFont(new java.awt.Font("Helvetica", 0, 16));
      myFrame.getContentPane().add(conl, "North");
      myFrame.getContentPane().add(new javax.swing.JScrollPane(tree), "Center");
      myFrame.getContentPane().add(new DynamicToolbar(uiMgr), "West");
      myFrame.pack();
      myFrame.setSize(uiMgr.getInt("studio_win_pos_w", myFrame.getWidth()),
          uiMgr.getInt("studio_win_pos_h", myFrame.getHeight()));
      myFrame.setLocation(uiMgr.getInt("studio_win_pos_x", 100),
          uiMgr.getInt("studio_win_pos_y", 100));
      myFrame.addWindowListener(new java.awt.event.WindowAdapter()
      {
        public void windowClosing(java.awt.event.WindowEvent evt)
        {
          // Ensure the debugger isn't paused/stepping
          resumeExecution();
          uiMgr.putInt("studio_win_pos_x", myFrame.getX());
          uiMgr.putInt("studio_win_pos_y", myFrame.getY());
          uiMgr.putInt("studio_win_pos_w", myFrame.getWidth());
          uiMgr.putInt("studio_win_pos_h", myFrame.getHeight());
          myFrame.dispose();
        }
      });

      menuBar = new javax.swing.JMenuBar();
      myFrame.setJMenuBar(menuBar);

      javax.swing.JMenu menu = new javax.swing.JMenu("File");
      menu.setMnemonic('F');
      menuBar.add(menu);

      newFileMenuItem = new javax.swing.JMenuItem("New...");
      newFileMenuItem.setMnemonic('N');
      newFileMenuItem.addActionListener(this);
      menu.add(newFileMenuItem);
      loadFileMenuItem = new javax.swing.JMenuItem("Open...");
      loadFileMenuItem.setMnemonic('L');
      loadFileMenuItem.addActionListener(this);
      menu.add(loadFileMenuItem);
      saveFileMenuItem = new javax.swing.JMenuItem("Save");
      saveFileMenuItem.setMnemonic('S');
      saveFileMenuItem.addActionListener(this);
      menu.add(saveFileMenuItem);
      saveAsMenuItem = new javax.swing.JMenuItem("Save As...");
      saveAsMenuItem.addActionListener(this);
      menu.add(saveAsMenuItem);
      saveAsMenuItem.setEnabled(!uiMgr.arePluginImportsActive());
      saveACopyAsMenuItem = new javax.swing.JMenuItem("Save A Copy As...");
      saveACopyAsMenuItem.addActionListener(this);
      menu.add(saveACopyAsMenuItem);
      importFileMenuItem= new javax.swing.JMenuItem("Import...");
      importFileMenuItem.setMnemonic('I');
      importFileMenuItem.addActionListener(this);
      menu.add(importFileMenuItem);
      exportMenuItem= new javax.swing.JMenuItem("Export Selected Menus...");
      exportMenuItem.setMnemonic('E');
      exportMenuItem.addActionListener(this);
      menu.add(exportMenuItem);
      menu.addSeparator();
      recentFilesMenu = new javax.swing.JMenu("Recent Files");
      rebuildRecentFilesSubmenu();
      menu.add(recentFilesMenu);
      menu.addSeparator();
      closeMenuItem = new javax.swing.JMenuItem("Close");
      closeMenuItem.setMnemonic('C');
      closeMenuItem.addActionListener(this);
      closeMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
          java.awt.event.KeyEvent.VK_F4, java.awt.event.ActionEvent.ALT_MASK));
      menu.add(closeMenuItem);

      menu = new javax.swing.JMenu("Edit");
      menu.setMnemonic('E');
      menuBar.add(menu);
      undoMenuItem = new javax.swing.JMenuItem("Undo");
      undoMenuItem.addActionListener(this);
      undoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
          java.awt.event.KeyEvent.VK_Z, java.awt.event.ActionEvent.CTRL_MASK));
      undoMenuItem.setEnabled(false);
      menu.add(undoMenuItem);
      menu.addSeparator();
      cutMenuItem = new javax.swing.JMenuItem("Cut");
      cutMenuItem.setMnemonic('t');
      cutMenuItem.addActionListener(this);
      cutMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
          java.awt.event.KeyEvent.VK_X, java.awt.event.ActionEvent.CTRL_MASK));
      menu.add(cutMenuItem);
      copyMenuItem = new javax.swing.JMenuItem("Copy");
      copyMenuItem.setMnemonic('C');
      copyMenuItem.addActionListener(this);
      copyMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
          java.awt.event.KeyEvent.VK_C, java.awt.event.ActionEvent.CTRL_MASK));
      menu.add(copyMenuItem);
      pasteMenuItem = new javax.swing.JMenuItem("Paste");
      pasteMenuItem.setMnemonic('P');
      pasteMenuItem.addActionListener(this);
      pasteMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
          java.awt.event.KeyEvent.VK_V, java.awt.event.ActionEvent.CTRL_MASK));
      menu.add(pasteMenuItem);
      pasteReferenceMenuItem = new javax.swing.JMenuItem("Paste Reference");
      pasteReferenceMenuItem.setMnemonic('R');
      pasteReferenceMenuItem.addActionListener(this);
      pasteReferenceMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
          java.awt.event.KeyEvent.VK_V, java.awt.event.ActionEvent.CTRL_MASK | java.awt.event.ActionEvent.SHIFT_MASK));
      menu.add(pasteReferenceMenuItem);
      deleteMenuItem = new javax.swing.JMenuItem("Delete");
      deleteMenuItem.setMnemonic('D');
      deleteMenuItem.addActionListener(this);
      deleteMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
          java.awt.event.KeyEvent.VK_DELETE, 0));
      menu.add(deleteMenuItem);
      menu.addSeparator();
      selectAllMenuItem = new javax.swing.JMenuItem("Select All");
      selectAllMenuItem.setMnemonic('A');
      selectAllMenuItem.addActionListener(this);
      selectAllMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
          java.awt.event.KeyEvent.VK_A, java.awt.event.ActionEvent.CTRL_MASK));
      menu.add(selectAllMenuItem);
      menu.addSeparator();
      findAllMenuItem = new javax.swing.JMenuItem("Find All...");
      findAllMenuItem.setMnemonic('F');
      findAllMenuItem.addActionListener(this);
      findAllMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
          java.awt.event.KeyEvent.VK_F, java.awt.event.ActionEvent.CTRL_MASK));
      menu.add(findAllMenuItem);

      menu = new javax.swing.JMenu("Debug");
      menu.setMnemonic('D');
      menuBar.add(menu);
      breakpointsItem = new javax.swing.JMenuItem("Breakpoints...");
      breakpointsItem.addActionListener(this);
      menu.add(breakpointsItem);
      tracerItem = new javax.swing.JMenuItem("Tracer...");
      tracerItem.addActionListener(this);
      menu.add(tracerItem);
      uiCompsItem = new javax.swing.JMenuItem("UI Components...");
      uiCompsItem.addActionListener(this);
      menu.add(uiCompsItem);
      menu.addSeparator();
      pauseItem = new javax.swing.JMenuItem("Pause");
      pauseItem.addActionListener(this);
      menu.add(pauseItem);
      pauseItem.setEnabled(true);
      resumeItem = new javax.swing.JMenuItem("Resume");
      resumeItem.addActionListener(this);
      resumeItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, java.awt.event.KeyEvent.CTRL_MASK));
      menu.add(resumeItem);
      resumeItem.setEnabled(false);
      stepItem = new javax.swing.JMenuItem("Step");
      stepItem.addActionListener(this);
      stepItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F7, 0));
      menu.add(stepItem);
      stepItem.setEnabled(false);
      menu.addSeparator();
      traceScrollItem = new javax.swing.JCheckBoxMenuItem("Scroll on Trace", uiMgr.getBoolean("studio/scroll_tracks_tracer", true));
      traceScrollItem.addActionListener(this);
      menu.add(traceScrollItem);
      menu.addSeparator();
      javax.swing.JMenuItem enableAllTraceItem = new javax.swing.JMenuItem("Enable All Tracing");
      menu.add(enableAllTraceItem);
      enableAllTraceItem.addActionListener(new java.awt.event.ActionListener()
      {
        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
          setAllTracing(true);
        }
      });
      javax.swing.JMenuItem disableAllTraceItem = new javax.swing.JMenuItem("Disable All Tracing");
      menu.add(disableAllTraceItem);
      disableAllTraceItem.addActionListener(new java.awt.event.ActionListener()
      {
        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
          setAllTracing(false);
        }
      });
      menu.addSeparator();
      traceExecPreItem = new javax.swing.JCheckBoxMenuItem("Trace Evaluate - Pre", false);
      traceExecPreItem.addActionListener(this);
      menu.add(traceExecPreItem);
      traceExecPostItem = new javax.swing.JCheckBoxMenuItem("Trace Evaluate - Post", false);
      traceExecPostItem.addActionListener(this);
      menu.add(traceExecPostItem);
      traceUICreateItem = new javax.swing.JCheckBoxMenuItem("Trace UI - Create", false);
      traceUICreateItem.addActionListener(this);
      menu.add(traceUICreateItem);
      traceUILayoutItem = new javax.swing.JCheckBoxMenuItem("Trace UI - Layout", false);
      traceUILayoutItem.addActionListener(this);
      menu.add(traceUILayoutItem);
      traceUIRenderItem = new javax.swing.JCheckBoxMenuItem("Trace UI - Render", false);
      traceUIRenderItem.addActionListener(this);
      menu.add(traceUIRenderItem);
      traceUIPreDataItem = new javax.swing.JCheckBoxMenuItem("Trace UI - Pre Data Eval", false);
      traceUIPreDataItem.addActionListener(this);
      menu.add(traceUIPreDataItem);
      traceUIPostDataItem = new javax.swing.JCheckBoxMenuItem("Trace UI - Post Data Eval", false);
      traceUIPostDataItem.addActionListener(this);
      menu.add(traceUIPostDataItem);
      traceUIPreCompItem = new javax.swing.JCheckBoxMenuItem("Trace UI - Pre Component Eval", false);
      traceUIPreCompItem.addActionListener(this);
      menu.add(traceUIPreCompItem);
      traceUIPostCompItem = new javax.swing.JCheckBoxMenuItem("Trace UI - Post Component Eval", false);
      traceUIPostCompItem.addActionListener(this);
      menu.add(traceUIPostCompItem);
      traceUIPreCondItem = new javax.swing.JCheckBoxMenuItem("Trace UI - Pre Conditional Eval", false);
      traceUIPreCondItem.addActionListener(this);
      menu.add(traceUIPreCondItem);
      traceUIPostCondItem = new javax.swing.JCheckBoxMenuItem("Trace UI - Post Conditional Eval", false);
      traceUIPostCondItem.addActionListener(this);
      menu.add(traceUIPostCondItem);
      traceMenuItem = new javax.swing.JCheckBoxMenuItem("Trace Menus", false);
      traceMenuItem.addActionListener(this);
      menu.add(traceMenuItem);
      traceOptionsMenuItem = new javax.swing.JCheckBoxMenuItem("Trace OptionsMenus", false);
      traceOptionsMenuItem.addActionListener(this);
      menu.add(traceOptionsMenuItem);
      traceHookItem = new javax.swing.JCheckBoxMenuItem("Trace Hooks", false);
      traceHookItem.addActionListener(this);
      menu.add(traceHookItem);
      traceListenerItem = new javax.swing.JCheckBoxMenuItem("Trace Listeners", false);
      traceListenerItem.addActionListener(this);
      menu.add(traceListenerItem);
      traceEventItem = new javax.swing.JCheckBoxMenuItem("Trace Events", false);
      traceEventItem.addActionListener(this);
      menu.add(traceEventItem);

      menu = new javax.swing.JMenu("Tools");
      menu.setMnemonic('T');
      menuBar.add(menu);
      diffMenuItem = new javax.swing.JMenuItem("STV Lexical File Difference...");
      diffMenuItem.setMnemonic('D');
      diffMenuItem.addActionListener(this);
      menu.add(diffMenuItem);
      diffUIDMenuItem = new javax.swing.JMenuItem("STV UID File Difference...");
      diffUIDMenuItem.setMnemonic('U');
      diffUIDMenuItem.addActionListener(this);
      menu.add(diffUIDMenuItem);
      exprEvalItem = new javax.swing.JMenuItem("Expression Evaluator...");
      exprEvalItem.setMnemonic('E');
      exprEvalItem.addActionListener(this);
      menu.add(exprEvalItem);
      genTransItem = new javax.swing.JMenuItem("Generate Translation Source...");
      genTransItem.addActionListener(this);
      menu.add(genTransItem);
      consolidateMenusItem = new javax.swing.JMenuItem("Consolidate Menus & \"SYM:\" Actions");
      consolidateMenusItem.addActionListener(this);
      menu.add(consolidateMenusItem);
      notifyOnErrorsItem = new javax.swing.JCheckBoxMenuItem("Notify On Errors",
          uiMgr.getBoolean("popup_on_action_errors", false));
      notifyOnErrorsItem.addActionListener(this);
      menu.add(notifyOnErrorsItem);
      launchAnotherFrameMenusItem = new javax.swing.JMenuItem("Launch Another Frame");
      launchAnotherFrameMenusItem.addActionListener(this);
      menu.add(launchAnotherFrameMenusItem);
      refreshMenuItem = new javax.swing.JMenuItem("Refresh");
      refreshMenuItem.addActionListener(this);
      menu.add(refreshMenuItem);
      uiPrefixMenuItem = new javax.swing.JMenuItem("Edit Widget UID Prefix...");
      uiPrefixMenuItem.addActionListener(this);
      menu.add(uiPrefixMenuItem);
      displaySymsItem = new javax.swing.JCheckBoxMenuItem("Display Widget UIDs",
          Sage.getBoolean("studio/display_widget_uids", false));
      displaySymsItem.addActionListener(this);
      menu.add(displaySymsItem);
      displayAttValuesItem = new javax.swing.JCheckBoxMenuItem("Display Attribute Values",
          Sage.getBoolean("studio/display_attribute_values", true));
      displayAttValuesItem.addActionListener(this);
      menu.add(displayAttValuesItem);
      dynBoolPropItem = new javax.swing.JCheckBoxMenuItem("Dynamic Boolean Property Editing",
          Sage.getBoolean("studio/checkbox_dynamic_properties", false));
      dynBoolPropItem.addActionListener(this);
      menu.add(dynBoolPropItem);
      optimizeItem = new javax.swing.JMenuItem("Optimize STV for Embedded Platform...");
      optimizeItem.addActionListener(this);
      menu.add(optimizeItem);
      syntaxAlertBoolPropItem = new javax.swing.JCheckBoxMenuItem("Alert on Syntax error when editing",
          Sage.getBoolean("studio/alert_on_syntax_error", false));
      syntaxAlertBoolPropItem.addActionListener(this);
      menu.add(syntaxAlertBoolPropItem);
      versionControlMenu = new javax.swing.JMenu("Version Control");
      menu.add(versionControlMenu);
      versionControlMenu.addMenuListener(new javax.swing.event.MenuListener() {

        public void menuSelected(javax.swing.event.MenuEvent e) {
          if (Sage.time() - lastCheckTime > 10000)
          {
            updateVCMenuStates(false);
            lastCheckTime = Sage.time();
          }
        }

        public void menuDeselected(javax.swing.event.MenuEvent e) {

        }

        public void menuCanceled(javax.swing.event.MenuEvent e) {

        }
        private long lastCheckTime = 0;
      });
      vcDiffWorkingMenuItem = new javax.swing.JMenuItem("Diff Working vs. Working Base");
      vcDiffWorkingMenuItem.addActionListener(this);
      versionControlMenu.add(vcDiffWorkingMenuItem);
      vcDiffCurrentMenuItem = new javax.swing.JMenuItem("Diff Head vs. Working Base");
      vcDiffCurrentMenuItem.addActionListener(this);
      versionControlMenu.add(vcDiffCurrentMenuItem);
      vcCheckinMenuItem = new javax.swing.JMenuItem("Checkin");
      vcCheckinMenuItem.addActionListener(this);
      versionControlMenu.add(vcCheckinMenuItem);
      vcUpdateTestMenuItem = new javax.swing.JMenuItem("Test Update for Conflicts");
      vcUpdateTestMenuItem.addActionListener(this);
      versionControlMenu.add(vcUpdateTestMenuItem);
      vcUpdateMenuItem = new javax.swing.JMenuItem("Update");
      vcUpdateMenuItem.addActionListener(this);
      versionControlMenu.add(vcUpdateMenuItem);
      vcRefreshMenuItem = new javax.swing.JMenuItem("Refresh");
      vcRefreshMenuItem.addActionListener(this);
      versionControlMenu.add(vcRefreshMenuItem);

      // Set these to disabled by default while we do the background repo refresh
      vcDiffWorkingMenuItem.setEnabled(false);
      vcDiffCurrentMenuItem.setEnabled(false);
      vcCheckinMenuItem.setEnabled(false);
      vcUpdateTestMenuItem.setEnabled(false);
      vcUpdateMenuItem.setEnabled(false);


      if (uiMgr.getBoolean("studio/enable_tracer", true))
      {
        uiMgr.setTracer(this);
      }

      myFrame.setName(uiMgr.getLocalUIClientName());

      updateVCMenuStates(false);
    }
    Catbert.ENABLE_STUDIO_DEBUG_CHECKS = true;
    myFrame.setVisible(true);
    if (autoSaver == null)
    {
      autoSaver = new AutoSaver();
      Thread t = new Thread(autoSaver, "AutoSaver");
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
    }
  }

  private void rebuildRecentFilesSubmenu()
  {
    recentFilesMenu.removeAll();
    String[] recentFiles = Sage.getRawProperties().getMRUList("studio_recent_files", 10);
    for (int i = 0; i < recentFiles.length; i++)
    {
      final String currFile = recentFiles[i];
      javax.swing.JMenuItem tempItem = new javax.swing.JMenuItem(currFile);
      tempItem.addActionListener(new java.awt.event.ActionListener()
      {
        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
          if (!checkForDirtyClose(true))
            return;
          try
          {
            uiMgr.freshStartup(new java.io.File(currFile));
          }
          catch (Throwable e)
          {
            javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error with the file:" + e);
          }
          Sage.getRawProperties().updateMRUList("studio_recent_files", currFile, 10);
          rebuildRecentFilesSubmenu();
          refreshTree();
          myFrame.setTitle("SageTV Studio - [" + currFile + ']' +
              (!new java.io.File(currFile).canWrite() ? " READ ONLY" : "") +
              (uiMgr.arePluginImportsActive() ? " ***IMPORTS ACTIVE***" : ""));
          saveAsMenuItem.setEnabled(!uiMgr.arePluginImportsActive());
          uiMgr.put(LAST_STV_BROWSE_DIR, new java.io.File(currFile).getParent());
          widgetOperations.clear();
          undoMenuItem.setEnabled(false);
          updateVCMenuStates(false);
        }
      });
      recentFilesMenu.add(tempItem);
    }
  }

  private void updateVCMenuStates(boolean synchronous)
  {
    if (doingRepoCheck)
    {
      if (synchronous)
      {
        while (doingRepoCheck)
        {
          try{
            Thread.sleep(500);
          } catch (Exception e){}
        }
      }
      return;
    }
    doingRepoCheck = true;
    vcRefreshMenuItem.setText("Refreshing (Wait)...");
    if (synchronous)
    {
      updateVCMenuStatesForReal();
      doingRepoCheck = false;
      vcRefreshMenuItem.setText("Refresh");
    }
    else
    {
      Pooler.execute(new Runnable()
      {
        public void run()
        {
          updateVCMenuStatesForReal();
          doingRepoCheck = false;
          vcRefreshMenuItem.setText("Refresh");
        }
      });
    }
  }

  private void updateVCMenuStatesForReal()
  {
    String fname = uiMgr.getModuleGroup().defaultModule.description();
    java.io.File f = new java.io.File(fname);
    sage.version.VersionControlState vcs = Sage.getBoolean("studio/enable_cvs", true) ? repoMaster.getState(f) : null;
    if (vcs == null)
    {
      // not in version control
      //versionControlMenu.setEnabled(false);
      vcDiffWorkingMenuItem.setEnabled(false);
      vcDiffCurrentMenuItem.setEnabled(false);
      vcCheckinMenuItem.setEnabled(false);
      vcUpdateTestMenuItem.setEnabled(false);
      vcUpdateMenuItem.setEnabled(false);
    }
    else
    {
      //versionControlMenu.setEnabled(true);
      vcDiffWorkingMenuItem.setEnabled(vcs.isModified || uiMgr.getModuleGroup().lastModified() > f.lastModified());
      vcDiffCurrentMenuItem.setEnabled(!vcs.isCurrent);
      vcCheckinMenuItem.setEnabled((vcs.isModified || uiMgr.getModuleGroup().lastModified() > f.lastModified()) && vcs.isCurrent);
      vcUpdateTestMenuItem.setEnabled((vcs.isModified || uiMgr.getModuleGroup().lastModified() > f.lastModified()) && !vcs.isCurrent);
      vcUpdateMenuItem.setEnabled(!vcs.isCurrent);
    }
  }

  public synchronized void hide()
  {
    if (childStudio != null)
      childStudio.hide();
    if (diffFrame != null)
      uiMgr.hideWindow(diffFrame);
    if (breakpointsFrame != null)
      uiMgr.hideWindow(breakpointsFrame);
    if (tracerFrame != null)
      uiMgr.hideWindow(tracerFrame);
    if (uiCompsFrame != null)
      uiMgr.hideWindow(uiCompsFrame);
    if (conflictsFrame != null)
      uiMgr.hideWindow(conflictsFrame);
    uiMgr.hideWindow(myFrame);
  }

  // Returns false if the user picked cancel
  private boolean checkForDirtyClose(boolean canShowCancel)
  {
    if (uiMgr.getModuleGroup().defaultModule != null)
    {
      String fname = uiMgr.getModuleGroup().defaultModule.description();
      java.io.File f = new java.io.File(fname);
      // NOTE: This can miss changes if the file's timestamp is in the future!
      if (!f.isFile() || uiMgr.getModuleGroup().lastModified() > f.lastModified())
      {
        while (true)
        {
          int opt = javax.swing.JOptionPane.showConfirmDialog(myFrame,
              "There are unsaved changes to the current file. Would you like to save them before exiting?",
              "Unsaved File Changes", canShowCancel ? javax.swing.JOptionPane.YES_NO_CANCEL_OPTION : javax.swing.JOptionPane.YES_NO_OPTION);
          if (opt == javax.swing.JOptionPane.YES_OPTION)
          {
            if (uiMgr.arePluginImportsActive() &&
                javax.swing.JOptionPane.showConfirmDialog(myFrame, "There are currently STV Import Plugins loaded into this STV, are you sure you want to save it this way?") !=
                javax.swing.JOptionPane.YES_OPTION)
              return false;
            if (new java.io.File(fname).isFile() && !fname.toLowerCase().endsWith(".stv") && !fname.toLowerCase().endsWith(".stvi"))
            {
              try
              {
                java.util.Map breakIdMap = uiMgr.getModuleGroup().defaultModule.saveXML(new java.io.File(fname), null);

                // 601 maybe not?
                uiMgr.getModuleGroup().setBreakIdMap(breakIdMap);
              }
              catch (tv.sage.SageException e)
              {
                javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error saving the file: " + e);
                continue;
              }
            }
            else
            {
              doSaveAs();
            }
            return true;
          }
          else if (opt == javax.swing.JOptionPane.NO_OPTION)
            return true;
          else if (opt == javax.swing.JOptionPane.CANCEL_OPTION && canShowCancel)
            return false;
        }
      }
    }
    return true;
  }

  public synchronized void kill()
  {
    if (childStudio != null)
      childStudio.kill();
    if (uiMgr.getModuleGroup() != null && parentStudio == null)
    {
      checkForDirtyClose(false);

      java.io.File breakpointFile = new java.io.File(uiMgr.get("studio/breakpoints_file", "Breakpoints.properties"));
      // 601
      java.util.Map idMap = getUIMgr().getModuleGroup().getBreakIdMap();
      try
      {
        Breakpoint.saveBreakpoints((Breakpoint[]) breakpoints.toArray(new Breakpoint[0]), breakpointFile, idMap);
      }
      catch (Exception e)
      {
        javax.swing.JOptionPane.showMessageDialog(null, "There was an error saving the breakpoints file:" + e);
      }
    }
    if (autoSaver != null)
      autoSaver.kill();
    if (diffFrame != null)
      diffFrame.setVisible(false);
    if (breakpointsFrame != null)
      breakpointsFrame.setVisible(false);
    if (tracerFrame != null)
      tracerFrame.setVisible(false);
    if (conflictsFrame != null)
      conflictsFrame.setVisible(false);
    if (uiCompsFrame != null)
      uiCompsFrame.setVisible(false);
    if (myFrame != null)
    {
      // 11/4/03 this was causing the Win32 error
      //myFrame.dispose();
      myFrame.setVisible(false);
    }
  }

  public void notifyOfExternalSTVChange()
  {
    showAndHighlightNode(null, true);
  }

  public synchronized boolean showAndHighlightNode(Widget highlightMe)
  {
    return showAndHighlightNode(highlightMe, false);
  }
  public synchronized boolean showAndHighlightNode(Widget highlightMe, boolean fixupsOnly)
  {
    if (myFrame != null && !myFrame.getTitle().equals("SageTV Studio - [" + uiMgr.getModuleGroup().defaultModule.description() + ']' +
        (!new java.io.File(uiMgr.getModuleGroup().defaultModule.description()).canWrite() ? " READ ONLY" : "") +
        (uiMgr.arePluginImportsActive() ? " ***IMPORTS ACTIVE***" : "")))
    {
      widgetOperations.clear();
      undoMenuItem.setEnabled(false);
      myFrame.setTitle("SageTV Studio - [" + uiMgr.getModuleGroup().defaultModule.description() + ']' +
          (!new java.io.File(uiMgr.getModuleGroup().defaultModule.description()).canWrite() ? " READ ONLY" : "") +
          (uiMgr.arePluginImportsActive() ? " ***IMPORTS ACTIVE***" : ""));
      saveAsMenuItem.setEnabled(!uiMgr.arePluginImportsActive());
      updateVCMenuStates(false);
    }

    refreshTree();

    if (!fixupsOnly)
      show();
    if (highlightMe != null && myFrame != null)
    {
      javax.swing.tree.TreePath path = model.getPathToNode(highlightMe);
      if (path != null)
      {
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
        return true;
      }
    }
    return false;
  }

  public synchronized boolean highlightNode(Widget highlightMe)
  {
    if (myFrame == null || !myFrame.isShowing()) return false;

    if (highlightMe != null)
    {
      javax.swing.tree.TreePath path = model.getPathToNode(highlightMe);
      if (path != null)
      {
        tree.setSelectionPathWithoutHighlight(path);
        if (uiMgr.getBoolean("studio/scroll_tracks_tracer", true))
          tree.scrollPathToVisible(path);
        return true;
      }
    }
    return false;
  }

  public void refreshTree()
  {
    if (model != null)
      model.refreshTree();
    if (childStudio != null)
      childStudio.refreshTree();
  }

  private void doSaveAs()
  {
    if (uiMgr.arePluginImportsActive() &&
        javax.swing.JOptionPane.showConfirmDialog(myFrame, "There are currently STV Import Plugins loaded into this STV, are you sure you want to save it this way?") !=
        javax.swing.JOptionPane.YES_OPTION)
      return;
    if (getxmlfc().showSaveDialog(myFrame) == javax.swing.JFileChooser.APPROVE_OPTION)
    {
      java.io.File selFile = getxmlfc().getSelectedFile();
      if (selFile.toString().indexOf('.') == -1)
        selFile = new java.io.File(selFile.toString() + ".xml");
      if (selFile.isFile())
      {
        if (javax.swing.JOptionPane.showConfirmDialog(myFrame, "The file " +
            selFile + " already exists. Are you sure you want to overwrite it?") !=
            javax.swing.JOptionPane.YES_OPTION)
          return;
      }
      try
      {
        // 601 uiMgr.getModuleGroup().defaultModule.saveXML(selFile, selFile.toString());

        java.util.Map breakIdMap = uiMgr.getModuleGroup().defaultModule.saveXML(selFile, selFile.toString());

        uiMgr.getModuleGroup().setBreakIdMap(breakIdMap);
      }
      catch (tv.sage.SageException e)
      {
        javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error saving the file: " + e);
      }

      // 601
      // extra breakpoint save before reload
      java.io.File breakpointFile = new java.io.File(uiMgr.get("studio/breakpoints_file", "Breakpoints.properties"));
      java.util.Map idMap = getUIMgr().getModuleGroup().getBreakIdMap();
      try
      {
        Breakpoint.saveBreakpoints((Breakpoint[]) breakpoints.toArray(new Breakpoint[0]), breakpointFile, idMap);
      }
      catch (Exception e)
      {
        javax.swing.JOptionPane.showMessageDialog(null, "There was an error saving the breakpoints file:" + e);
      }

      uiMgr.put(LAST_STV_BROWSE_DIR, selFile.getParent());
      Sage.getRawProperties().updateMRUList("studio_recent_files", selFile.toString(), 10);
      rebuildRecentFilesSubmenu();
      // Now load the new one we saved as the STV we're editing
      try
      {
        uiMgr.freshStartup(selFile);
      }
      catch (Throwable e)
      {
        javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error reloading the saved file: " + e);
      }

      // 601
      // reload breakpoints
      breakpointFile = new java.io.File(Sage.get("studio/breakpoints_file", "Breakpoints.properties"));
      if (breakpointFile.isFile())
      {
        try
        {
          breakpoints = Breakpoint.loadBreakpoints(breakpointFile, uiMgr);
        }
        catch (Exception e)
        {
          javax.swing.JOptionPane.showMessageDialog(null, "There was an error loading the breakpoints file:" + e);
        }
      }
      if (breakpoints == null)
        breakpoints = new java.util.Vector();

      if (breaksTableModel != null)
        breaksTableModel.fireTableDataChanged();


      refreshTree();
      myFrame.setTitle("SageTV Studio - [" + selFile + ']');
      widgetOperations.clear();
      undoMenuItem.setEnabled(false);
      updateVCMenuStates(false);
    }
  }

  public void actionPerformed(java.awt.event.ActionEvent evt)
  {
    Object esrc = evt.getSource();
    if (esrc == newFileMenuItem)
    {
      if (!checkForDirtyClose(true))
        return;
      //			if (getstvfc().showOpenDialog(myFrame) == javax.swing.JFileChooser.APPROVE_OPTION)
      {
        /*java.io.File newFile = getstvfc().getSelectedFile();
				if (newFile.toString().indexOf('.') == -1)
					newFile = new java.io.File(newFile.toString() + ".stv");
				if (newFile.isFile())
				{
					if (javax.swing.JOptionPane.showConfirmDialog(myFrame, "The file " +
						newFile + " already exists. Are you sure you want to overwrite it?") !=
						javax.swing.JOptionPane.YES_OPTION)
						return;
				}
				try
				{
					newFile.createNewFile();
					wiz.setWidgetFile(newFile);*/
        uiMgr.freshStartup(null);
        /*				}
				catch (Throwable e)
				{
					javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error with the file:" + e);
				}*/
        refreshTree();
        myFrame.setTitle("SageTV Studio - [" + uiMgr.getModuleGroup().defaultModule.description() + "]");
        widgetOperations.clear();
        undoMenuItem.setEnabled(false);
        updateVCMenuStates(false);
      }
    }
    else if (esrc == loadFileMenuItem)
    {
      if (!checkForDirtyClose(true))
        return;
      if (getstvfc().showOpenDialog(myFrame) == javax.swing.JFileChooser.APPROVE_OPTION)
      {
        try
        {
          //wiz.setWidgetFile(getstvfc().getSelectedFile());
          uiMgr.freshStartup(getstvfc().getSelectedFile());
        }
        catch (Throwable e)
        {
          javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error with the file:" + e);
          e.printStackTrace();
        }
        Sage.getRawProperties().updateMRUList("studio_recent_files", getstvfc().getSelectedFile().toString(), 10);
        rebuildRecentFilesSubmenu();
        refreshTree();
        myFrame.setTitle("SageTV Studio - [" + getstvfc().getSelectedFile() + ']' +
            (!getstvfc().getSelectedFile().canWrite() ? " READ ONLY" : "") +
            (uiMgr.arePluginImportsActive() ? " ***IMPORTS ACTIVE***" : ""));
        uiMgr.put(LAST_STV_BROWSE_DIR, getstvfc().getSelectedFile().getParent());
        widgetOperations.clear();
        undoMenuItem.setEnabled(false);
        saveAsMenuItem.setEnabled(!uiMgr.arePluginImportsActive());
        updateVCMenuStates(false);
      }
    }
    else if (esrc == saveFileMenuItem)
    {
      String fname = uiMgr.getModuleGroup().defaultModule.description();
      if (new java.io.File(fname).isFile() && !fname.toLowerCase().endsWith(".stv"))
      {
        if (uiMgr.arePluginImportsActive() &&
            javax.swing.JOptionPane.showConfirmDialog(myFrame, "There are currently STV Import Plugins loaded into this STV, are you sure you want to save it this way?") !=
            javax.swing.JOptionPane.YES_OPTION)
          return;
        try
        {
          // 601 uiMgr.getModuleGroup().defaultModule.saveXML(new java.io.File(fname), null);

          java.util.Map breakIdMap = uiMgr.getModuleGroup().defaultModule.saveXML(new java.io.File(fname), null);

          uiMgr.getModuleGroup().setBreakIdMap(breakIdMap);
        }
        catch (tv.sage.SageException e)
        {
          javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error saving the file: " + e);
        }
        Sage.getRawProperties().updateMRUList("studio_recent_files", fname, 10);
        rebuildRecentFilesSubmenu();
      }
      else
      {
        doSaveAs();
      }
    }
    else if (esrc == saveAsMenuItem)
    {
      doSaveAs();
    }
    else if (esrc == saveACopyAsMenuItem)
    {
      if (uiMgr.arePluginImportsActive() &&
          javax.swing.JOptionPane.showConfirmDialog(myFrame, "There are currently STV Import Plugins loaded into this STV, are you sure you want to save it this way?") !=
          javax.swing.JOptionPane.YES_OPTION)
        return;
      if (getxmlfc().showSaveDialog(myFrame) == javax.swing.JFileChooser.APPROVE_OPTION)
      {
        java.io.File selFile = getxmlfc().getSelectedFile();
        if (selFile.toString().indexOf('.') == -1)
          selFile = new java.io.File(selFile.toString() + ".xml");
        if (selFile.isFile())
        {
          if (javax.swing.JOptionPane.showConfirmDialog(myFrame, "The file " +
              selFile + " already exists. Are you sure you want to overwrite it?") !=
              javax.swing.JOptionPane.YES_OPTION)
            return;
        }
        try
        {
          if (selFile.toString().toLowerCase().endsWith(".stv"))
            uiMgr.getModuleGroup().exportSTV(selFile);
          else
            uiMgr.getModuleGroup().defaultModule.saveXML(selFile, null);
        }
        catch (tv.sage.SageException e)
        {
          javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error saving the file: " + e);
        }
        uiMgr.put(LAST_STV_BROWSE_DIR, selFile.getParent());
        Sage.getRawProperties().updateMRUList("studio_recent_files", selFile.toString(), 10);
        rebuildRecentFilesSubmenu();
      }
    }
    else if (esrc == importFileMenuItem)
    {
      if (getstvfc().showOpenDialog(myFrame) == javax.swing.JFileChooser.APPROVE_OPTION)
      {
        try
        {
          // 601
          // wiz.importWidgetFile(uiMgr, getstvfc().getSelectedFile());

          getUIMgr().getModuleGroup().importXML(getstvfc().getSelectedFile(), getUIMgr());
        }
        catch (Throwable e)
        {
          javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error importing the file:" + e);
        }
        refreshTree();
        uiMgr.put(LAST_STV_BROWSE_DIR, getstvfc().getSelectedFile().getParent());
        Sage.getRawProperties().updateMRUList("studio_recent_files", getstvfc().getSelectedFile().toString(), 10);
        rebuildRecentFilesSubmenu();
      }
    }
    else if (esrc == exportMenuItem)
    {
      if (getxmlfc().showSaveDialog(myFrame) == javax.swing.JFileChooser.APPROVE_OPTION)
      {
        if (getxmlfc().getSelectedFile().isFile())
        {
          if (javax.swing.JOptionPane.showConfirmDialog(myFrame, "The file " +
              getxmlfc().getSelectedFile() + " already exists. Are you sure you want to overwrite it?") !=
              javax.swing.JOptionPane.YES_OPTION)
            return;
        }
        // Get the list of all of the selections, filter all of the ones that are menus.
        // Then find all of the recursive children of these menus, stopping at menus.
        // Save this collection of Widgets to the file using the Wizard
        Widget[] selWidgs = tree.getSelectedWidgets();
        java.util.ArrayList menuVec = new java.util.ArrayList();
        java.util.Set exportWidgs = new java.util.HashSet();
        for (int i = 0; i < selWidgs.length; i++)
          if (selWidgs[i].isType(Widget.MENU) ||
              selWidgs[i].isType(Widget.OPTIONSMENU) ||
              selWidgs[i].isType(Widget.HOOK))
            menuVec.add(selWidgs[i]);
        while (!menuVec.isEmpty())
        {
          Widget w = (Widget) menuVec.remove(0);
          if (exportWidgs.add(w))
          {
            Widget[] kids = w.contents();
            for (int i = 0; i < kids.length; i++)
              if (!kids[i].isType(Widget.MENU))
                menuVec.add(kids[i]);
          }
        }
        exportWidgs.addAll(java.util.Arrays.asList(selWidgs));
        try
        {
          // 601 FIX
          //throw (new tv.sage.SageException("NOT Supported", 0));

          getUIMgr().getModuleGroup().exportXML(exportWidgs, getxmlfc().getSelectedFile());

          //wiz.exportWidgetsToFile((Widget[]) exportWidgs.toArray(new Widget[0]), getxmlfc().getSelectedFile());
        }
        catch (Throwable e)
        {
          // 601 debug
          e.printStackTrace();

          javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error exporting to the file:" + e);
        }
        uiMgr.put(LAST_STV_BROWSE_DIR, getxmlfc().getSelectedFile().getParent());
        Sage.getRawProperties().updateMRUList("studio_recent_files", getxmlfc().getSelectedFile().toString(), 10);
        rebuildRecentFilesSubmenu();
      }
    }
    else if (esrc == closeMenuItem)
      hide();
    else if (esrc == uiPrefixMenuItem)
    {
      String currPrefix = getUIMgr().getModuleGroup().defaultModule.getUIPrefix();
      String newPrefix = (String) javax.swing.JOptionPane.showInputDialog(myFrame,
          "<html>The String below will prefix all Widget UIDs created.<br/>You may edit this value here:</html>",
          "UID Prefix",
          javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, currPrefix);
      if (newPrefix != null && newPrefix.trim().length() > 0)
      {
        if (newPrefix.length() < 4)
        {
          javax.swing.JOptionPane.showMessageDialog(myFrame, "The UID Prefix must be at least four characters. Please try again.");
        }
        else
          Sage.put("studio/custom_ui_prefix", newPrefix);
      }

    }
    else if (esrc == optimizeItem)
    {
      // Do the optimization of the STV for execution on an embedded platform. We should popup a dialog that gives options for this
      // for which optimizations to perform. It should also allow specifying a file path for variable replacement (for theme removal).
      // During the process, a progress dialog should be shown...and when done it should give some statistics on what it did....but really, this
      // is just meant as a tool for us so we could just hide all that for now and use debug log output. :)
      performEmbeddedSTVOptimization();
    }
    else if (esrc == findAllMenuItem)
      tree.find();
    else if (esrc == cutMenuItem)
      tree.cutSelection();
    else if (esrc == copyMenuItem)
      tree.copySelection();
    else if (esrc == pasteMenuItem)
      tree.pasteToSelection(false);
    else if (esrc == pasteReferenceMenuItem)
      tree.pasteToSelection(true);
    else if (esrc == deleteMenuItem)
      tree.deleteSelectedWidgets();
    else if (esrc == selectAllMenuItem)
    {
      int[] selRows = new int[tree.getRowCount()];
      for (int i = 0; i < selRows.length; i++)
        selRows[i] = i;
      tree.setSelectionRows(selRows);
    }
    else if (esrc == undoMenuItem)
      undo();
    else if (esrc == diffMenuItem || esrc == diffUIDMenuItem)
    {
      if (getstvfc().showOpenDialog(myFrame) == javax.swing.JFileChooser.APPROVE_OPTION)
      {
        try
        {
          java.io.File diffSrcFile = getstvfc().getSelectedFile();
          threeWayUIDiff = false;
          if ((evt.getModifiers() & java.awt.Event.CTRL_MASK) == java.awt.Event.CTRL_MASK && (esrc == diffUIDMenuItem))
          {
            if (getstvfc().showOpenDialog(myFrame) == javax.swing.JFileChooser.APPROVE_OPTION)
            {
              diffRes = wiz.diffWidgetFilesUID(this, diffSrcFile, getstvfc().getSelectedFile());
              setup3WayUIDiff();
            }
            else
              return;
          }
          else
          {
            diffRes = (esrc == diffUIDMenuItem) ? wiz.diffWidgetFileUID(this, diffSrcFile) :
              wiz.diffWidgetFile(this, diffSrcFile);
          }
          doingUIDDiff = esrc == diffUIDMenuItem;
          showDiffFrame(getstvfc().getSelectedFile().toString());
        }
        catch (Throwable e)
        {
          javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error diffing the file:" + e);
          e.printStackTrace();
        }
        uiMgr.put(LAST_STV_BROWSE_DIR, getstvfc().getSelectedFile().getParent());
      }
    }
    else if (esrc == genSTVIB)
    {
      if (getxmlfc().showSaveDialog(myFrame) == javax.swing.JFileChooser.APPROVE_OPTION)
      {
        if (getxmlfc().getSelectedFile().isFile())
        {
          if (javax.swing.JOptionPane.showConfirmDialog(myFrame, "The file " +
              getxmlfc().getSelectedFile() + " already exists. Are you sure you want to overwrite it?") !=
              javax.swing.JOptionPane.YES_OPTION)
            return;
        }

        // Now do the magic of creating the STVI file automatically. :)
        tv.sage.ModuleGroup patchGroup;
        try
        {
          patchGroup = tv.sage.ModuleManager.newModuleGroup();
        }
        catch (tv.sage.SageException se)
        {
          throw new InternalError("Unexpected SageException in newModuleGroup()");
        }
        javax.swing.ListModel lm = diffList.getModel();
        java.util.Vector diffOps = new java.util.Vector(lm.getSize());
        for (int i = 0; i < lm.getSize(); i++)
          diffOps.add(lm.getElementAt(i));
        // Create the STVImported hook for linking up all the other patch info
        Widget stvImportHook = patchGroup.addWidget(Widget.HOOK, "");
        WidgetFidget.setName(stvImportHook, "STVImported");
        Widget removeWidgRem = patchGroup.addWidget(Widget.ACTION, "");
        WidgetFidget.setName(removeWidgRem, "\"REM Deletion of Widgets\"");
        WidgetFidget.contain(stvImportHook, removeWidgRem);
        Widget renameWidgRem = patchGroup.addWidget(Widget.ACTION, "");
        WidgetFidget.setName(renameWidgRem, "\"REM Renaming of Widgets\"");
        WidgetFidget.contain(stvImportHook, renameWidgRem);
        Widget repropWidgRem = patchGroup.addWidget(Widget.ACTION, "");
        WidgetFidget.setName(repropWidgRem, "\"REM Resetting of Widget properties\"");
        WidgetFidget.contain(stvImportHook, repropWidgRem);
        Widget discontentWidgRem = patchGroup.addWidget(Widget.ACTION, "");
        WidgetFidget.setName(discontentWidgRem, "\"REM Removal of Widget children relationships\"");
        WidgetFidget.contain(stvImportHook, discontentWidgRem);
        Widget containWidgRem = patchGroup.addWidget(Widget.ACTION, "");
        WidgetFidget.setName(containWidgRem, "\"REM Adding of Widget children relationships\"");
        WidgetFidget.contain(stvImportHook, containWidgRem);
        Widget moveWidgRem = patchGroup.addWidget(Widget.ACTION, "");
        WidgetFidget.setName(moveWidgRem, "\"REM Repositioning of child Widgets\"");
        WidgetFidget.contain(stvImportHook, moveWidgRem);
        for (int i = 0; i < diffOps.size(); i++)
        {
          WidgetOp op = (WidgetOp) diffOps.get(i);
          // put those in the module as new widgets using the same name/type/properties
          if (op.opType == CREATE_OP)
          {
            Widget newWidg = patchGroup.addWidget(op.w1.type(), op.w1.symbol());
            WidgetFidget.setName(newWidg, op.w1.getName());
            for (byte p = 0; p <= Widget.MAX_PROP_NUM; p++)
            {
              if (op.w1.hasProperty(p))
              {
                WidgetFidget.setProperty(newWidg, p, op.w1.getProperty(p));
              }
            }
          }
          else if (op.opType == DESTROY_OP)
          {
            // if one of these widgets was removed anyways, then we're OK so don't warn about it
            Widget remover = addWidgetAsChild(patchGroup, "RemoveWidget(FindWidgetBySymbol(\"" + op.w1.symbol() + "\"))",
                Widget.ACTION, removeWidgRem);
          }
          else if (op.opType == RENAME_OP)
          {
            Widget check = addWidgetAsChild(patchGroup, "Widg = FindWidgetBySymbol(\"" + op.w1.symbol() + "\")",
                Widget.CONDITIONAL, renameWidgRem);
            Widget elseB = addWidgetAsChild(patchGroup, "else", Widget.BRANCH, check);
            Widget nullB = addWidgetAsChild(patchGroup, "null", Widget.BRANCH, check);
            Widget renamer = addWidgetAsChild(patchGroup, "SetWidgetName(Widg, \"" +
                addEscapes(op.w1.getName()) + "\")", Widget.ACTION, elseB);
            Widget warn = addWidgetAsChild(patchGroup, "DebugLog(\"PATCH ERROR Missing Widget - Symbol=" +
                op.w1.symbol() + " Could not rename from: \\\"" + addEscapes(op.pv) + "\\\" to: \\\"" +
                addEscapes(op.w1.getName()) + "\\\"\")", Widget.ACTION, nullB);
          }
          else if (op.opType == PROPERTY_OP)
          {
            Widget check = addWidgetAsChild(patchGroup, "Widg = FindWidgetBySymbol(\"" + op.w1.symbol() + "\")",
                Widget.CONDITIONAL, repropWidgRem);
            Widget elseB = addWidgetAsChild(patchGroup, "else", Widget.BRANCH, check);
            Widget nullB = addWidgetAsChild(patchGroup, "null", Widget.BRANCH, check);
            Widget reprop = addWidgetAsChild(patchGroup, "SetWidgetProperty(Widg, \"" +
                Widget.PROPS[op.pn] + "\", " + (!op.w1.hasProperty(op.pn) ? "null" : ("\"" +
                    addEscapes(op.w1.getProperty(op.pn)) + "\"")) + ")", Widget.ACTION, elseB);
            Widget warn = addWidgetAsChild(patchGroup, "DebugLog(\"PATCH ERROR Missing Widget - Symbol=" +
                op.w1.symbol() + " could not change property " + Widget.PROPS[op.pn] + " from: \\\"" +
                addEscapes(op.pv) + "\\\" to: \\\"" +
                addEscapes(op.w1.getProperty(op.pn)) + "\\\"\")", Widget.ACTION, nullB);
          }
          else if (op.opType == UNCONTAIN_OP)
          {
            // If we can't break a relationship; then that means the relationship is already gone which is fine
            Widget discont = patchGroup.addWidget(Widget.ACTION, "");
            WidgetFidget.setName(discont, "RemoveWidgetChild(FindWidgetBySymbol(\"" + op.w1.symbol() + "\"), " +
                "FindWidgetBySymbol(\"" + op.w2.symbol() + "\"))");
            WidgetFidget.contain(discontentWidgRem, discont);
          }
          else if (op.opType == CONTAIN_OP)
          {
            Widget check1 = addWidgetAsChild(patchGroup, "Widg1 = FindWidgetBySymbol(\"" + op.w1.symbol() + "\")",
                Widget.CONDITIONAL, containWidgRem);
            Widget elseB1 = addWidgetAsChild(patchGroup, "else", Widget.BRANCH, check1);
            Widget nullB1 = addWidgetAsChild(patchGroup, "null", Widget.BRANCH, check1);
            Widget warn1 = addWidgetAsChild(patchGroup, "DebugLog(\"PATCH ERROR Missing Widget - Symbol=" +
                op.w1.symbol() + " \\\"" + addEscapes(op.w1.getName()) + "\\\" missing parent; could not add child with symbol=" + op.w2.symbol() +
                " \\\"" + addEscapes(op.w2.getName()) + "\\\"\")", Widget.ACTION, nullB1);

            Widget check2 = addWidgetAsChild(patchGroup, "Widg2 = FindWidgetBySymbol(\"" + op.w2.symbol() + "\")",
                Widget.CONDITIONAL, elseB1);
            Widget elseB2 = addWidgetAsChild(patchGroup, "else", Widget.BRANCH, check2);
            Widget nullB2 = addWidgetAsChild(patchGroup, "null", Widget.BRANCH, check2);
            Widget warn2 = addWidgetAsChild(patchGroup, "DebugLog(\"PATCH ERROR Missing Widget - Symbol=" +
                op.w2.symbol() + " \\\"" + addEscapes(op.w1.getName()) + "\\\" could not add as child; parent symbol=" + op.w1.symbol() +
                " \\\"" + addEscapes(op.w2.getName()) + "\\\"\")", Widget.ACTION, nullB2);

            Widget content = addWidgetAsChild(patchGroup, "AddWidgetChild(Widg1, Widg2)", Widget.ACTION, elseB2);
          }
          else if (op.opType == MOVE_OP)
          {
            Widget check1 = addWidgetAsChild(patchGroup, "Widg1 = FindWidgetBySymbol(\"" + op.w1.symbol() + "\")",
                Widget.CONDITIONAL, containWidgRem);
            Widget elseB1 = addWidgetAsChild(patchGroup, "else", Widget.BRANCH, check1);
            Widget nullB1 = addWidgetAsChild(patchGroup, "null", Widget.BRANCH, check1);
            Widget warn1 = addWidgetAsChild(patchGroup, "DebugLog(\"PATCH ERROR Missing Widget - Symbol=" +
                op.w1.symbol() + " \\\"" + addEscapes(op.w1.getName()) + "\\\" missing parent; could not move child with symbol=" + op.w2.symbol() +
                " \\\"" + addEscapes(op.w2.getName()) + "\\\"\")", Widget.ACTION, nullB1);

            Widget check2 = addWidgetAsChild(patchGroup, "Widg2 = FindWidgetBySymbol(\"" + op.w2.symbol() + "\")",
                Widget.CONDITIONAL, elseB1);
            Widget elseB2 = addWidgetAsChild(patchGroup, "else", Widget.BRANCH, check2);
            Widget nullB2 = addWidgetAsChild(patchGroup, "null", Widget.BRANCH, check2);
            Widget warn2 = addWidgetAsChild(patchGroup, "DebugLog(\"PATCH ERROR Missing Widget - Symbol=" +
                op.w2.symbol() + " \\\"" + addEscapes(op.w1.getName()) + "\\\" could not move child; parent symbol=" + op.w1.symbol() +
                " \\\"" + addEscapes(op.w2.getName()) + "\\\"\")", Widget.ACTION, nullB2);

            Widget mover = addWidgetAsChild(patchGroup, "InsertWidgetChild(Widg1, Widg2, " +
                java.util.Arrays.asList(op.w1.contents()).indexOf(op.w2) + ")", Widget.ACTION, elseB2);
          }
        }
        Widget autocleanWidgRem = patchGroup.addWidget(Widget.ACTION, "");
        WidgetFidget.setName(autocleanWidgRem, "ReturnValue = \"" + Catbert.AUTO_CLEANUP_STV_IMPORTED_HOOK + "\"");
        WidgetFidget.contain(stvImportHook, autocleanWidgRem);
        try
        {
          patchGroup.defaultModule.saveXML(getxmlfc().getSelectedFile(), null);
        }
        catch (Throwable e)
        {
          // 601 debug
          e.printStackTrace();

          javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error exporting to the file:" + e);
        }
        patchGroup.dispose();
      }
    }
    else if (esrc == breakpointsItem)
    {
      if (breakpointsFrame == null)
      {
        breakpointsFrame = new javax.swing.JFrame("Breakpoints");
        breaksTableModel = new BreaksTableModel();
        breaksTable = new javax.swing.JTable(breaksTableModel);
        breaksTable.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        breaksTable.setCellSelectionEnabled(false);
        breaksTable.setRowSelectionAllowed(true);
        breaksTable.setColumnSelectionAllowed(false);
        breaksTable.getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener()
        {
          public void valueChanged(javax.swing.event.ListSelectionEvent evt)
          {
            Object selVal = breaksTableModel.getRowValue(breaksTable.getSelectedRow());
            if (selVal != null)
            {
              Breakpoint theOp = (Breakpoint) selVal;
              javax.swing.tree.TreePath path = model.getPathToNode(theOp.getWidget());
              if (path != null)
              {
                tree.setSelectionPathWithoutHighlight(path);
                tree.scrollPathToVisible(path);
              }
            }
          }
        });
        breaksTable.addMouseListener(new java.awt.event.MouseAdapter()
        {
          javax.swing.JPopupMenu rightClicky;
          javax.swing.JMenuItem removeAllBreaks;
          javax.swing.JMenuItem disableAllBreaks;
          javax.swing.JMenuItem enableAllBreaks;
          javax.swing.JMenuItem removeBreak;
          javax.swing.JMenuItem disableBreak;
          javax.swing.JMenuItem enableBreak;

          {
            removeAllBreaks = new javax.swing.JMenuItem("Remove All Breakpoints");
            removeAllBreaks.addActionListener(new java.awt.event.ActionListener()
            {
              public void actionPerformed(java.awt.event.ActionEvent evt)
              {
                removeAllBreakpoints();
                tree.repaint();
              }
            });
            disableAllBreaks = new javax.swing.JMenuItem("Disable All Breakpoints");
            disableAllBreaks.addActionListener(new java.awt.event.ActionListener()
            {
              public void actionPerformed(java.awt.event.ActionEvent evt)
              {
                enableAllBreakpoints(false);
                tree.repaint();
              }
            });
            enableAllBreaks = new javax.swing.JMenuItem("Enable All Breakpoints");
            enableAllBreaks.addActionListener(new java.awt.event.ActionListener()
            {
              public void actionPerformed(java.awt.event.ActionEvent evt)
              {
                enableAllBreakpoints(true);
                tree.repaint();
              }
            });
            removeBreak = new javax.swing.JMenuItem("Remove Breakpoint(s)");
            removeBreak.addActionListener(new java.awt.event.ActionListener()
            {
              public void actionPerformed(java.awt.event.ActionEvent evt)
              {
                int[] sels = breaksTable.getSelectedRows();
                if (sels != null)
                {
                  for (int i = 0; i < sels.length; i++)
                  {
                    removeBreakpoint(breaksTableModel.getRowValue(sels[i]).getWidget());
                  }
                  tree.repaint();
                }
              }
            });
            enableBreak = new javax.swing.JMenuItem("Enable Breakpoint(s)");
            enableBreak.addActionListener(new java.awt.event.ActionListener()
            {
              public void actionPerformed(java.awt.event.ActionEvent evt)
              {
                int[] sels = breaksTable.getSelectedRows();
                if (sels != null)
                {
                  for (int i = 0; i < sels.length; i++)
                  {
                    breaksTableModel.getRowValue(sels[i]).setEnabled(true);
                  }
                  breaksTable.repaint();
                  tree.repaint();
                }
              }
            });
            disableBreak = new javax.swing.JMenuItem("Disable Breakpoint(s)");
            disableBreak.addActionListener(new java.awt.event.ActionListener()
            {
              public void actionPerformed(java.awt.event.ActionEvent evt)
              {
                int[] sels = breaksTable.getSelectedRows();
                if (sels != null)
                {
                  for (int i = 0; i < sels.length; i++)
                  {
                    breaksTableModel.getRowValue(sels[i]).setEnabled(false);
                  }
                  breaksTable.repaint();
                  tree.repaint();
                }
              }
            });
          }

          public void mouseReleased(java.awt.event.MouseEvent evt)
          {
            if (javax.swing.SwingUtilities.isRightMouseButton(evt))
            {
              if (rightClicky == null)
              {
                rightClicky = new javax.swing.JPopupMenu("Breakpoint Options");
                rightClicky.add(enableBreak);
                rightClicky.add(disableBreak);
                rightClicky.add(removeBreak);
                rightClicky.addSeparator();
                rightClicky.add(enableAllBreaks);
                rightClicky.add(disableAllBreaks);
                rightClicky.add(removeAllBreaks);
              }
              enableBreak.setEnabled(false);
              disableBreak.setEnabled(false);
              removeBreak.setEnabled(false);
              enableAllBreaks.setEnabled(false);
              disableAllBreaks.setEnabled(false);
              removeAllBreaks.setEnabled(false);
              int[] sels = breaksTable.getSelectedRows();
              if (sels != null && sels.length > 0)
              {
                removeBreak.setEnabled(true);
                for (int i = 0; i < sels.length; i++)
                {
                  Breakpoint bp = breaksTableModel.getRowValue(sels[i]);
                  if (bp.isEnabled())
                  {
                    disableBreak.setEnabled(true);
                  }
                  else
                  {
                    enableBreak.setEnabled(true);
                  }
                }
              }
              if (breakpoints.size() > 0)
                removeAllBreaks.setEnabled(true);
              for (int i = 0; i < breakpoints.size(); i++)
              {
                Breakpoint bp = (Breakpoint) breakpoints.get(i);
                if (bp.isEnabled())
                  disableAllBreaks.setEnabled(true);
                else
                  enableAllBreaks.setEnabled(true);
              }
              MySwingUtils.safeShowPopupMenu(rightClicky, breaksTable, evt.getX(), evt.getY());
            }
          }
        });
        breakpointsFrame.getContentPane().add(new javax.swing.JScrollPane(breaksTable));
        breakpointsFrame.pack();
        breakpointsFrame.setSize(uiMgr.getInt("studio/breaks_win_pos_w", breakpointsFrame.getWidth()),
            uiMgr.getInt("studio/breaks_win_pos_h", breakpointsFrame.getHeight()));
        breakpointsFrame.setLocation(uiMgr.getInt("studio/breaks_win_pos_x", 100),
            uiMgr.getInt("studio/breaks_win_pos_y", 100));
        int numCols = breaksTable.getColumnCount();
        for (int i = 0; i < numCols; i++)
        {
          int prefWidth = uiMgr.getInt("studio/breaks_col_width/" + i, 0);
          if (prefWidth > 0)
            breaksTable.getColumnModel().getColumn(i).setPreferredWidth(prefWidth);
        }
        breakpointsFrame.addWindowListener(new java.awt.event.WindowAdapter()
        {
          public void windowClosing(java.awt.event.WindowEvent evt)
          {
            int numCols = breaksTable.getColumnCount();
            for (int i = 0; i < numCols; i++)
              uiMgr.putInt("studio/breaks_col_width/" + i, breaksTable.getColumnModel().getColumn(i).getWidth());
            uiMgr.putInt("studio/breaks_win_pos_x", breakpointsFrame.getX());
            uiMgr.putInt("studio/breaks_win_pos_y", breakpointsFrame.getY());
            uiMgr.putInt("studio/breaks_win_pos_w", breakpointsFrame.getWidth());
            uiMgr.putInt("studio/breaks_win_pos_h", breakpointsFrame.getHeight());
            breakpointsFrame.dispose();
          }
        });
      }
      /*else
			{
				breaksList.setListData(breakpoints);
			}*/
      breakpointsFrame.setVisible(true);
    }
    else if (esrc == tracerItem)
    {
      if (tracerFrame == null)
      {
        tracerFrame = new javax.swing.JFrame("Tracer");
        tracerTableModel = new TracerTableModel();
        tracerTable = new javax.swing.JTable(tracerTableModel)
        {
          //Implement table cell tool tips.
          public String getToolTipText(java.awt.event.MouseEvent e)
          {
            String tip = null;
            java.awt.Point p = e.getPoint();
            int rowIndex = rowAtPoint(p);
            int colIndex = columnAtPoint(p);
            int realColumnIndex = convertColumnIndexToModel(colIndex);

            if (realColumnIndex == 1)
            {
              tip = Widget.TYPES[tracerTableModel.getRowValue(rowIndex).w.type()];
            } else { //another column
              //You can omit this part if you know you don't
              //have any renderers that supply their own tool
              //tips.
              tip = super.getToolTipText(e);
            }
            return tip;
          }
        };
        tracerTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tracerTable.setCellSelectionEnabled(false);
        tracerTable.setRowSelectionAllowed(true);
        tracerTable.setColumnSelectionAllowed(false);
        tracerTable.getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener()
        {
          public void valueChanged(javax.swing.event.ListSelectionEvent evt)
          {
            Object selVal = tracerTableModel.getRowValue(tracerTable.getSelectedRow());
            if (selVal != null)
            {
              TracerOp theOp = (TracerOp) selVal;
              javax.swing.tree.TreePath path = model.getPathToNode(theOp.w);
              if (path != null)
              {
                tree.setSelectionPathWithoutHighlight(path);
                tree.scrollPathToVisible(path);
              }
            }
          }
        });
        tracerTable.addMouseListener(new java.awt.event.MouseAdapter()
        {
          javax.swing.JPopupMenu rightClicky;
          javax.swing.JMenuItem clearTraces;

          {
            clearTraces = new javax.swing.JMenuItem("Clear");
            clearTraces.addActionListener(new java.awt.event.ActionListener()
            {
              public void actionPerformed(java.awt.event.ActionEvent evt)
              {
                tracerTableModel.clear();
              }
            });
          }
          public void mouseReleased(java.awt.event.MouseEvent evt)
          {
            if (javax.swing.SwingUtilities.isRightMouseButton(evt))
            {
              if (rightClicky == null)
              {
                rightClicky = new javax.swing.JPopupMenu("Tracer Options");
                rightClicky.add(clearTraces);
              }
              clearTraces.setEnabled(true);
              MySwingUtils.safeShowPopupMenu(rightClicky, tracerTable, evt.getX(), evt.getY());
            }
          }
        });
        tracerFrame.getContentPane().add(new javax.swing.JScrollPane(tracerTable));
        tracerFrame.pack();
        tracerFrame.setSize(uiMgr.getInt("studio/tracer_win_pos_w", tracerFrame.getWidth()),
            uiMgr.getInt("studio/tracer_win_pos_h", tracerFrame.getHeight()));
        tracerFrame.setLocation(uiMgr.getInt("studio/tracer_win_pos_x", 100),
            uiMgr.getInt("studio/tracer_win_pos_y", 100));
        int numCols = tracerTable.getColumnCount();
        for (int i = 0; i < numCols; i++)
        {
          int prefWidth = uiMgr.getInt("studio/tracer_col_width/" + i, 0);
          if (prefWidth > 0)
            tracerTable.getColumnModel().getColumn(i).setPreferredWidth(prefWidth);
        }
        tracerFrame.addWindowListener(new java.awt.event.WindowAdapter()
        {
          public void windowClosing(java.awt.event.WindowEvent evt)
          {
            int numCols = tracerTable.getColumnCount();
            for (int i = 0; i < numCols; i++)
              uiMgr.putInt("studio/tracer_col_width/" + i, tracerTable.getColumnModel().getColumn(i).getWidth());
            uiMgr.putInt("studio/tracer_win_pos_x", tracerFrame.getX());
            uiMgr.putInt("studio/tracer_win_pos_y", tracerFrame.getY());
            uiMgr.putInt("studio/tracer_win_pos_w", tracerFrame.getWidth());
            uiMgr.putInt("studio/tracer_win_pos_h", tracerFrame.getHeight());
            tracerFrame.dispose();
          }
        });
      }
      tracerFrame.setVisible(true);
    }
    else if (esrc == uiCompsItem)
    {
      if (uiCompsFrame == null)
      {
        uiCompsFrame = new javax.swing.JFrame("UI Components");
        uiCompsTreeModel = new DebugUITreeModel(uiMgr);
        uiCompsTree = new DebugUITree(uiMgr, uiCompsTreeModel);
        uiCompsTreeModel.setTreeUI(uiCompsTree);
        uiCompsFrame.getContentPane().add(new javax.swing.JScrollPane(uiCompsTree));
        uiCompsFrame.pack();
        uiCompsFrame.setSize(uiMgr.getInt("studio/uicomps_win_pos_w", uiCompsFrame.getWidth()),
            uiMgr.getInt("studio/uicomps_win_pos_h", uiCompsFrame.getHeight()));
        uiCompsFrame.setLocation(uiMgr.getInt("studio/uicomps_win_pos_x", 100),
            uiMgr.getInt("studio/uicomps_win_pos_y", 100));
        uiCompsFrame.addWindowListener(new java.awt.event.WindowAdapter()
        {
          public void windowClosing(java.awt.event.WindowEvent evt)
          {
            uiMgr.putInt("studio/uicomps_win_pos_x", uiCompsFrame.getX());
            uiMgr.putInt("studio/uicomps_win_pos_y", uiCompsFrame.getY());
            uiMgr.putInt("studio/uicomps_win_pos_w", uiCompsFrame.getWidth());
            uiMgr.putInt("studio/uicomps_win_pos_h", uiCompsFrame.getHeight());
            uiCompsFrame.dispose();
          }
        });
      }
      uiCompsFrame.setVisible(true);
    }
    else if (esrc == exprEvalItem)
    {
      String[] selectionValues = Sage.getRawProperties().getMRUList("studio_eval_expressions", 25);
      final javax.swing.JComboBox jcb = new javax.swing.JComboBox(selectionValues);
      jcb.setEditable(true);
      javax.swing.JPanel jpan = new javax.swing.JPanel();
      jpan.setLayout(new java.awt.GridLayout(2, 1));
      jpan.add(new javax.swing.JLabel("Enter the expression to evaluate:"));
      jpan.add(jcb);
      javax.swing.JOptionPane pane = new javax.swing.JOptionPane(jpan, javax.swing.JOptionPane.QUESTION_MESSAGE,
          javax.swing.JOptionPane.OK_CANCEL_OPTION, null, null, null);

      pane.setComponentOrientation(myFrame.getComponentOrientation());

      javax.swing.JDialog dialog = pane.createDialog(myFrame, "Input");
      jcb.getEditor().selectAll();

      dialog.pack();
      jcb.requestFocusInWindow();
      dialog.setVisible(true);
      dialog.dispose();

      Object selectedValue = pane.getValue();
      int popRv;
      if(selectedValue == null)
        popRv = javax.swing.JOptionPane.CLOSED_OPTION;
      else
      {
        if(selectedValue instanceof Integer)
          popRv = ((Integer)selectedValue).intValue();
        else
          popRv = javax.swing.JOptionPane.CLOSED_OPTION;
      }
      if (popRv == javax.swing.JOptionPane.OK_OPTION)
      {
        Object selExpr = jcb.getSelectedItem();
        if (selExpr != null)
        {
          String expr = selExpr.toString();
          Sage.getRawProperties().updateMRUList("studio_eval_expressions", expr, 25);
          try
          {
            Catbert.Context theCon = getSuspendedContext();
            Catbert.precompileExpression(expr);
            Object val = Catbert.evaluateExpression(expr, theCon == null ? new Catbert.Context(uiMgr) : theCon, null, null);
            javax.swing.JTextArea texty = new javax.swing.JTextArea(val == null ? "null" : val.toString());
            texty.setEditable(false);
            // nielm add scrollbars to prevent joptionpane from blowing up the window manager
            // if someone (like me) outputs too much text in their expression
            javax.swing.JScrollPane textscroll=new javax.swing.JScrollPane(texty);
            textscroll.setPreferredSize(new Dimension(320, 100));
            javax.swing.JOptionPane.showMessageDialog(myFrame, textscroll, "Result", javax.swing.JOptionPane.PLAIN_MESSAGE);
          }
          catch (Exception e)
          {
            javax.swing.JOptionPane.showMessageDialog(myFrame, "Error evaluating expression: " + e);
          }
        }
      }
    }
    else if (esrc == notifyOnErrorsItem)
    {
      uiMgr.putBoolean("popup_on_action_errors", notifyOnErrorsItem.isSelected());
    }
    else if (esrc == displaySymsItem)
    {
      tree.setDisplayUIDs(displaySymsItem.isSelected());
      Sage.putBoolean("studio/display_widget_uids", displaySymsItem.isSelected());
      refreshTree();
    }
    else if (esrc == displayAttValuesItem)
    {
      tree.setDisplayAttributeValues(displayAttValuesItem.isSelected());
      Sage.putBoolean("studio/display_attribute_values", displayAttValuesItem.isSelected());
      refreshTree();
    }
    else if (esrc == dynBoolPropItem)
    {
      Sage.putBoolean("studio/checkbox_dynamic_properties", dynBoolPropItem.isSelected());
    }
    else if (esrc == syntaxAlertBoolPropItem)
    {
      Sage.putBoolean("studio/alert_on_syntax_error", syntaxAlertBoolPropItem.isSelected());
    }
    else if (esrc == genTransItem)
    {
      javax.swing.JFileChooser fcp = new javax.swing.JFileChooser(uiMgr.get(LAST_STV_BROWSE_DIR, System.getProperty("user.dir")));
      javax.swing.filechooser.FileFilter ff = new javax.swing.filechooser.FileFilter()
      {
        public String getDescription() { return "Java Properties Files (*.properties)"; }
        public boolean accept(java.io.File f)
        {
          return f.isDirectory() || (f.isFile() && f.getName().toLowerCase().endsWith(".properties"));
        }
      };
      fcp.addChoosableFileFilter(ff);
      fcp.setFileFilter(ff);
      if (fcp.showSaveDialog(myFrame) == javax.swing.JFileChooser.APPROVE_OPTION)
      {
        if (fcp.getSelectedFile().isFile())
        {
          if (javax.swing.JOptionPane.showConfirmDialog(myFrame,
              "The selected file already exists. Are you sure you want to overwrite it?") !=
              javax.swing.JOptionPane.YES_OPTION)
            return;
        }
        java.io.File transFile = fcp.getSelectedFile();
        java.util.Set transText = new java.util.HashSet();
        java.util.Set transTextDyn = new java.util.HashSet();
        Widget[] widgs = uiMgr.getModuleGroup().getWidgets(Widget.TEXT);
        // All of the static text Widgets (theme widgets don't matter)
        for (int i = 0; i < widgs.length; i++)
        {
          if (widgs[i].numContainers(Widget.ACTION) == 0 && widgs[i].numContainers(Widget.THEME) == 0)
            transText.add(fixEscapes(widgs[i].getUntranslatedName()));
        }
        widgs = uiMgr.getModuleGroup().getWidgets(Widget.ITEM);
        // All of the static text Items
        for (int i = 0; i < widgs.length; i++)
        {
          if (widgs[i].numContainers(Widget.THEME) != 0)
            continue;
          boolean hasUI = false;
          // Check if it has any children in the UIHierarchy that aren't shapes
          Widget[] kids = widgs[i].contents();
          for (int j = 0; j < kids.length; j++)
          {
            if (kids[j].isInUIHierarchy() && !kids[j].isInShapeHierarchy() && !kids[j].isInEffectHierarchy())
            {
              hasUI = true;
              break;
            }
          }
          if (!hasUI)
            transText.add(fixEscapes(widgs[i].getUntranslatedName()));
        }
        // Any string based Attribute values
        widgs = uiMgr.getModuleGroup().getWidgets(Widget.ATTRIBUTE);
        for (int i = 0; i < widgs.length; i++)
        {
          if (widgs[i].getProperty(Widget.VALUE).indexOf('"') != -1)
            transTextDyn.add(fixEscapes(widgs[i].getProperty(Widget.VALUE)));
        }

        // Any Text or TextInput with a parent Action should have that action
        // chains names added for translations. But only add the Action Widgets in those
        // parent chains because the Conditional & Branch won't be affecting this.
        // UPDATE: 6/1/05 - Add TableComponent & Table to this since that can populate data too
        widgs = uiMgr.getModuleGroup().getWidgets();
        for (int i = 0; i < widgs.length; i++)
        {
          if (!widgs[i].isType(Widget.TEXT) && !widgs[i].isType(Widget.TEXTINPUT) &&
              !widgs[i].isType(Widget.TABLE) && !widgs[i].isType(Widget.TABLECOMPONENT))
            continue;
          if (widgs[i].numContainers(Widget.ACTION) == 0)
            continue;
          Widget[] parents = widgs[i].containers(Widget.ACTION);
          for (int j = 0; j < parents.length; j++)
            addParentActionChainToTrans(parents[j], transTextDyn, new java.util.HashSet());
        }

        String[] allText = (String[]) transText.toArray(new String[0]);
        java.util.Arrays.sort(allText);
        String[] allDynText = (String[]) transTextDyn.toArray(new String[0]);
        java.util.Arrays.sort(allDynText);
        java.io.PrintWriter pw = null;
        try
        {
          pw = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(transFile)));
          pw.println("# SageTV Translations" + new java.util.Date().toString());
          for (int i = 0; i < allText.length; i++)
          {
            pw.println("S_" + WidgetMeta.convertToCleanPropertyName(allText[i]) + "=" + allText[i]);
          }
          for (int i = 0; i < allDynText.length; i++)
          {
            pw.println("D_" + WidgetMeta.convertToCleanPropertyName(allDynText[i]) + "=" + allDynText[i]);
          }
          pw.close();
          pw = null;
        }
        catch (Exception e)
        {
          javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error writing the translation of:" + e);
          if (pw != null)
            pw.close();
        }
      }
    }
    else if (esrc == consolidateMenusItem)
    {
      if (javax.swing.JOptionPane.showConfirmDialog(myFrame,
          "Consolidating Menu & Action[\"SYM:...\"] Widgets cannot be undone. Are you sure you want to proceed?") !=
          javax.swing.JOptionPane.YES_OPTION)
        return;
      for (int z = 0; z < 2; z++)
      {
        Widget[] menuWidgs = uiMgr.getModuleGroup().getWidgets(z == 0 ? Widget.MENU : Widget.ACTION);
        java.util.HashMap widgNameMap = new java.util.HashMap();
        for (int i = 0; i < menuWidgs.length; i++)
        {
          Widget currMenu = menuWidgs[i];
          String currName = currMenu.getName();
          if (currName == null || currName.trim().length() == 0 || currName.equals("Untitled") ||
              (z == 1 && !currName.startsWith("\"SYM:")))
            continue; // skip unnamed menus, or Actions without "SYM:
          Widget existingMenu = (Widget) widgNameMap.get(currName);
          if (existingMenu == null)
          {
            widgNameMap.put(currName, currMenu);
            continue;
          }

          // See which one should be the primary menu
          int currMenContLength = currMenu.contents().length;
          int existMenContLength = existingMenu.contents().length;
          if ((currMenContLength == existMenContLength && existingMenu.id() > currMenu.id()) ||
              (currMenContLength > existMenContLength))
          {
            Widget swap = existingMenu;
            existingMenu = currMenu;
            currMenu = swap;
            widgNameMap.put(currName, existingMenu);
          }

          // Now replace every reference to the currMenu with the existingMenu
          Widget[] parents = currMenu.containers();
          for (int j = 0; j < parents.length; j++)
          {
            WidgetFidget.discontent(parents[j], currMenu);
            WidgetFidget.contain(parents[j], existingMenu);
          }
          WidgetFidget.setName(currMenu, currName + " - PATCHED ORPHAN");
        }
      }
      refreshTree();
      widgetOperations.clear();
      undoMenuItem.setEnabled(false);
    }
    else if (esrc == launchAnotherFrameMenusItem)
    {
      if (childStudio == null)
      {
        childStudio = new StudioFrame();
        childStudio.setUIMgr(uiMgr);
        childStudio.parentStudio = this;
      }
      childStudio.showAndHighlightNode(null);
    }
    else if (esrc == refreshMenuItem)
    {
      refreshTree();
      updateVCMenuStates(false);
    }
    else if (esrc == traceExecPreItem)
    {
      if (((javax.swing.JCheckBoxMenuItem) esrc).isSelected())
        traceEvaluateMask |= Tracer.PRE_EVALUATION;
      else
        traceEvaluateMask &= ~Tracer.PRE_EVALUATION;
    }
    else if (esrc == traceExecPostItem)
    {
      if (((javax.swing.JCheckBoxMenuItem) esrc).isSelected())
        traceEvaluateMask |= Tracer.POST_EVALUATION;
      else
        traceEvaluateMask &= ~Tracer.POST_EVALUATION;
    }
    else if (esrc == traceUICreateItem)
    {
      if (((javax.swing.JCheckBoxMenuItem) esrc).isSelected())
        traceUIMask |= Tracer.CREATE_UI;
      else
        traceUIMask &= ~Tracer.CREATE_UI;
    }
    else if (esrc == traceUILayoutItem)
    {
      if (((javax.swing.JCheckBoxMenuItem) esrc).isSelected())
        traceUIMask |= Tracer.LAYOUT_UI;
      else
        traceUIMask &= ~Tracer.LAYOUT_UI;
    }
    else if (esrc == traceUIRenderItem)
    {
      if (((javax.swing.JCheckBoxMenuItem) esrc).isSelected())
        traceUIMask |= Tracer.RENDER_UI;
      else
        traceUIMask &= ~Tracer.RENDER_UI;
    }
    else if (esrc == traceUIPreDataItem)
    {
      if (((javax.swing.JCheckBoxMenuItem) esrc).isSelected())
        traceUIMask |= Tracer.PRE_EVALUATE_DATA_UI;
      else
        traceUIMask &= ~Tracer.PRE_EVALUATE_DATA_UI;
    }
    else if (esrc == traceUIPostDataItem)
    {
      if (((javax.swing.JCheckBoxMenuItem) esrc).isSelected())
        traceUIMask |= Tracer.POST_EVALUATE_DATA_UI;
      else
        traceUIMask &= ~Tracer.POST_EVALUATE_DATA_UI;
    }
    else if (esrc == traceUIPreCompItem)
    {
      if (((javax.swing.JCheckBoxMenuItem) esrc).isSelected())
        traceUIMask |= Tracer.PRE_EVALUATE_COMPONENT_UI;
      else
        traceUIMask &= ~Tracer.PRE_EVALUATE_COMPONENT_UI;
    }
    else if (esrc == traceUIPostCompItem)
    {
      if (((javax.swing.JCheckBoxMenuItem) esrc).isSelected())
        traceUIMask |= Tracer.POST_EVALUATE_COMPONENT_UI;
      else
        traceUIMask &= ~Tracer.POST_EVALUATE_COMPONENT_UI;
    }
    else if (esrc == traceUIPreCondItem)
    {
      if (((javax.swing.JCheckBoxMenuItem) esrc).isSelected())
        traceUIMask |= Tracer.PRE_CONDITIONAL_UI;
      else
        traceUIMask &= ~Tracer.PRE_CONDITIONAL_UI;
    }
    else if (esrc == traceUIPostCondItem)
    {
      if (((javax.swing.JCheckBoxMenuItem) esrc).isSelected())
        traceUIMask |= Tracer.POST_CONDITIONAL_UI;
      else
        traceUIMask &= ~Tracer.POST_CONDITIONAL_UI;
    }
    else if (esrc == traceMenuItem)
      traceMenuEnabled = traceMenuItem.isSelected();
    else if (esrc == traceOptionsMenuItem)
      traceOptionsMenuEnabled = traceOptionsMenuItem.isSelected();
    else if (esrc == traceHookItem)
      traceHookEnabled = traceHookItem.isSelected();
    else if (esrc == traceListenerItem)
      traceListenerEnabled = traceListenerItem.isSelected();
    else if (esrc == traceEventItem)
      traceEventEnabled = traceEventItem.isSelected();
    else if (esrc == traceScrollItem)
      uiMgr.putBoolean("studio/scroll_tracks_tracer", traceScrollItem.isSelected());
    else if (esrc == pauseItem)
      pauseExecution();
    else if (esrc == resumeItem)
      resumeExecution();
    else if (esrc == stepItem)
      stepExecution(1);
    else if (esrc == vcDiffWorkingMenuItem)
    {
      // Just debug output for testing now...
      String fname = uiMgr.getModuleGroup().defaultModule.description();
      java.io.File f = new java.io.File(fname);
      sage.version.VersionControlState vcs = repoMaster.getState(f);
      System.out.println("Version Control Testing for source file: " + fname);
      System.out.println("Working Version: \"" + vcs.workingVersion + "\"");
      System.out.println("Repository Version: \"" + vcs.repositoryVersion + "\"");
      System.out.println("IsModified: " + vcs.isModified);
      System.out.println("IsCurrent: " + vcs.isCurrent);

      // Leave this code below here...it's part of the diff logic
      java.io.File tempFile = null;
      try
      {
        tempFile = java.io.File.createTempFile("repostv", ".xml");
        repoMaster.getFileVersionContents(f, vcs.workingVersion, tempFile);
        tempFile.deleteOnExit();
      }
      catch (java.io.IOException e)
      {
        System.out.println("ERROR with test of:" + e);
        e.printStackTrace();
      }

      // Do the diff!
      threeWayUIDiff = false;
      try
      {
        diffRes = wiz.diffWidgetFileUID(this, tempFile);
        doingUIDDiff = true;
        showDiffFrame("Working Ver: " + vcs.workingVersion + " vs. Base: " + vcs.workingVersion);
      }
      catch (Throwable e)
      {
        javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error diffing the file:" + e);
        e.printStackTrace();
      }
    }
    else if (esrc == vcDiffCurrentMenuItem)
    {
      String fname = uiMgr.getModuleGroup().defaultModule.description();
      java.io.File f = new java.io.File(fname);
      sage.version.VersionControlState vcs = repoMaster.getState(f);

      java.io.File tempFileHead = null;
      java.io.File tempFileBase = null;
      try
      {
        tempFileHead = java.io.File.createTempFile("repostv", ".xml");
        repoMaster.getFileVersionContents(f, vcs.repositoryVersion, tempFileHead);
        tempFileHead.deleteOnExit();
        tempFileBase = java.io.File.createTempFile("repostv", ".xml");
        repoMaster.getFileVersionContents(f, vcs.workingVersion, tempFileBase);
        tempFileBase.deleteOnExit();
      }
      catch (java.io.IOException e)
      {
        javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error diffing the file:" + e);
        e.printStackTrace();
        return;
      }

      // Do the diff!
      try
      {
        diffRes = wiz.diffWidgetFilesUID(this, tempFileBase, tempFileHead);
        doingUIDDiff = true;
        setup3WayUIDiff();
        showDiffFrame("Head: " + vcs.repositoryVersion + " vs. Working Base: " + vcs.workingVersion);
      }
      catch (Throwable e)
      {
        javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error diffing the file:" + e);
        e.printStackTrace();
      }
    }
    else if (esrc == vcRefreshMenuItem)
    {
      updateVCMenuStates(true);
    }
    else if (esrc == vcUpdateMenuItem || esrc == vcUpdateTestMenuItem)
    {
      if ((diffFrame != null && diffFrame.isShowing()) || (conflictsFrame != null && conflictsFrame.isShowing()))
        return; // don't let someone do something bad

      String fname = uiMgr.getModuleGroup().defaultModule.description();
      java.io.File f = new java.io.File(fname);
      if (uiMgr.getModuleGroup().lastModified() > f.lastModified() && esrc == vcUpdateMenuItem)
      {
        javax.swing.JOptionPane.showMessageDialog(myFrame, "The STV must be saved prior to doing an update/merge operation.");
        return;
      }
      premergeVCState = repoMaster.getState(f);
      if (premergeVCState.isCurrent)
        return;
      if (premergeVCState.isModified || uiMgr.getModuleGroup().lastModified() > f.lastModified())
      {
        // First we need to generate the list of diff ops for the repository change and also for our working changes
        java.io.File tempFileHead = null;
        java.io.File tempFileBase = null;
        WidgetOp[] repoDiffs = null;
        workingDiffs = null;
        try
        {
          tempFileHead = java.io.File.createTempFile("repostv", ".xml");
          repoMaster.getFileVersionContents(f, premergeVCState.repositoryVersion, tempFileHead);
          tempFileHead.deleteOnExit();
          tempFileBase = java.io.File.createTempFile("repostv", ".xml");
          repoMaster.getFileVersionContents(f, premergeVCState.workingVersion, tempFileBase);
          tempFileBase.deleteOnExit();

          repoDiffs = (WidgetOp[]) wiz.diffWidgetFilesUID(this, tempFileBase, tempFileHead).toArray(new WidgetOp[0]);
          workingDiffs = (WidgetOp[]) wiz.diffWidgetFileUID(this, tempFileBase).toArray(new WidgetOp[0]);
        }
        catch (Throwable e)
        {
          javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error diffing the files:" + e);
          e.printStackTrace();
          return;
        }

        // Now we put all of the working diff ops into a map based on symbol. Since a Widget can have multiple changes to it, we have to use
        // lists for the actual values in the map instead of just the WidgetOp itself (which are instead in the list).
        java.util.Map symToWorkingDiffMap = new java.util.HashMap();
        for (int i = 0; i < workingDiffs.length; i++)
        {
          java.util.ArrayList existingList = (java.util.ArrayList) symToWorkingDiffMap.get(workingDiffs[i].w1.symbol());
          if (existingList == null)
            symToWorkingDiffMap.put(workingDiffs[i].w1.symbol(), existingList = new java.util.ArrayList());
          existingList.add(workingDiffs[i]);
          if (workingDiffs[i].w2 != null)
          {
            existingList = (java.util.ArrayList) symToWorkingDiffMap.get(workingDiffs[i].w2.symbol());
            if (existingList == null)
              symToWorkingDiffMap.put(workingDiffs[i].w2.symbol(), existingList = new java.util.ArrayList());
            existingList.add(workingDiffs[i]);
          }
        }

        // Now we go through the list of all the repo diffs and see if any of those conflict with the list of diffs in our working changes
        // When we find conflicts, we put them into a list where each item is a WidgetConflict object.

        // There's 2 types of conflicts, hard and soft. Hard conflicts are ones where we really should ask the user for confirmation on what they want
        // to do because something really doesn't match, or we can't automatically determine the right answer. Soft conflicts are more informational in that
        // they should probably be OK, but we want to inform them about it because both touched areas of the same code.
        /*
         * RULES FOR THE CONFLICTS
         *
         * Create - This is never in conflict because creation of a Widget is unique
         * Destroy - If A destroys a Widget, then B must not have any of the following changes: Property change on the Widget,
         *    Rename of the Widget, Containment of the Widget (child or parent), Move of the Widget (child or parent).
         *    Uncontain is allowed. Differences here are hard in nature.
         * Property - A makes a property change to a Widget. If B destroyed that Widget, that’s a soft conflict.
         *    If B made the same property change, that is OK. If B changed the same property to a different value,
         *    that is a medium conflict (the user can choose the right value). If B changed some other property in that Widget, that’s a soft conflict.
         *    If B performed some other containment/uncontain/move operation relating to the Widget, that’s a soft conflict.
         * Rename - A renames a Widget. If B destroyed that Widget, that’s a soft conflict. If B made the same name change, that is OK.
         *    If B made a different name change, that’s a medium conflict (the user can choose the right value).
         * Containment - A contains Widget Y under Widget X. If B destroys either of these Widgets, that’s a soft conflict.
         *    If B contains anything new under X or moves anything under X, that’s a medium conflict for ordering of the contents of X.
         *    If B has renames or property changes to X or Y, those are soft conflicts. If B uncontains anything under X, that’s a soft conflict.
         * Uncontainment - A uncontains Widget Y under Widget X. If B destroys either of these Widgets, that’s OK.
         *    If B contains anything new under Widget X or moves anything under Widget X, that’s a soft conflict.
         *    If B has renames or property changes to X or Y, those are soft conflicts.
         * Move - A changes the index of Widget Y under Widget X. If B destroys either of these Widgets, that’s a soft conflict.
         *    If B contains anything new under X or moves anything under X, that’s a medium conflict for ordering of the contents of X.
         *    If B has renames or property changes to X or Y, those are soft conflicts. If B uncontains anything under X, that’s a soft conflict.
         *
         */

        // When we're done with the conflict resolution, consolidation is done by processing all of the repo difference ops, then processing all of the working
        // diff ops, and then finally processing all of the resolution operations. There will be redundancy in this if we don't have
        // common widgets consolidated in the operations. The only one we really need to be careful about is that creating a widget w/ the
        // same symbol more than once doesn't cause a problem (since we do that to put back a destroyed widget we modified, but if there
        // were multiple modifications done to it, then it would be created multiple times).
        if (widgetConflictDiffs == null)
          widgetConflictDiffs = new java.util.ArrayList();
        else
          widgetConflictDiffs.clear();
        java.util.ArrayList relatedOps;
        for (int i = 0; i < repoDiffs.length; i++)
        {
          WidgetOp currRepoOp = repoDiffs[i];
          switch (currRepoOp.opType)
          {
            case CREATE_OP:
              // These are always safe and can be skipped
              break;
            case DESTROY_OP:
              relatedOps = (java.util.ArrayList) symToWorkingDiffMap.get(currRepoOp.w1.symbol());
              if (relatedOps != null)
              {
                for (int j = 0; j < relatedOps.size(); j++)
                {
                  // Only non-conflicting op is uncontain and destroy
                  WidgetOp testOp = (WidgetOp) relatedOps.get(j);
                  if (testOp.opType == UNCONTAIN_OP || testOp.opType == DESTROY_OP)
                    continue;
                  // All diffs here are hard diffs
                  WidgetConflict wc = new WidgetConflict(currRepoOp, testOp, true);
                  wc.resolutionOptions = new java.util.LinkedHashMap();
                  // This option requires no operations to fix it since all the ones done in our working copy will just abort
                  // due to the widget not existing
                  wc.resolutionOptions.put("Leave Widget destroyed", null);
                  // We also need to rebuild all the existing Widget relationships that the destroyed Widget has in our STV
                  java.util.ArrayList rebuildOps = new java.util.ArrayList();
                  rebuildOps.add(new WidgetOp(true, testOp.w1));
                  Widget[] parents = testOp.w1.containers();
                  for (int k = 0; k < parents.length; k++)
                  {
                    // Maintain proper child position
                    Widget[] parentKids = parents[k].contents();
                    int kidIdx = parents[k].getChildIndex(testOp.w1);
                    rebuildOps.add(new WidgetOp(parents[k], testOp.w1, kidIdx <= 0 ? null : parentKids[kidIdx - 1]));
                  }
                  Widget[] kids = testOp.w1.contents();
                  for (int k = 0; k < kids.length; k++)
                  {
                    rebuildOps.add(new WidgetOp(testOp.w1, kids[k]));
                  }
                  rebuildOps.add(testOp);
                  wc.resolutionOptions.put("Resurrect Widget", (WidgetOp[]) rebuildOps.toArray(new WidgetOp[0]));
                  widgetConflictDiffs.add(wc);
                }
              }
              break;
            case PROPERTY_OP:
              relatedOps = (java.util.ArrayList) symToWorkingDiffMap.get(currRepoOp.w1.symbol());
              if (relatedOps != null)
              {
                for (int j = 0; j < relatedOps.size(); j++)
                {
                  WidgetOp testOp = (WidgetOp) relatedOps.get(j);
                  // Destroy, rename or any type of containment change is soft here
                  if (testOp.opType == DESTROY_OP || testOp.opType == CONTAIN_OP ||
                      testOp.opType == UNCONTAIN_OP || testOp.opType == MOVE_OP ||
                      testOp.opType == RENAME_OP)
                  {
                    widgetConflictDiffs.add(new WidgetConflict(currRepoOp, testOp, false));
                  }
                  else if (testOp.opType == PROPERTY_OP)
                  {
                    if (testOp.pn == currRepoOp.pn)
                    {
                      String p1 = testOp.w1.getProperty(testOp.pn);
                      String p2 = currRepoOp.w1.getProperty(currRepoOp.pn);
                      if (p1 != p2 && (p1 == null || !p1.equals(p2)))
                      {
                        // Property difference between the two on the same property, medium conflict
                        WidgetConflict wc = new WidgetConflict(currRepoOp, testOp, true);
                        wc.resolutionOptions = new java.util.LinkedHashMap();
                        wc.resolutionOptions.put("Set property " + Widget.PROPS[currRepoOp.pn] + " = \"" + p1 + "\"",
                            new WidgetOp[] { testOp });
                        wc.resolutionOptions.put("Set property " + Widget.PROPS[currRepoOp.pn] + " = \"" + p2 + "\"",
                            new WidgetOp[] { currRepoOp });
                        widgetConflictDiffs.add(wc);
                      }
                    }
                    else
                    {
                      // Each changed different properties on the same Widget, soft conflict
                      widgetConflictDiffs.add(new WidgetConflict(currRepoOp, testOp, false));
                    }
                  }
                }
              }
              break;
            case RENAME_OP:
              relatedOps = (java.util.ArrayList) symToWorkingDiffMap.get(currRepoOp.w1.symbol());
              if (relatedOps != null)
              {
                for (int j = 0; j < relatedOps.size(); j++)
                {
                  WidgetOp testOp = (WidgetOp) relatedOps.get(j);
                  // Destroy, rename or any type of containment change is soft here
                  if (testOp.opType == DESTROY_OP || testOp.opType == CONTAIN_OP ||
                      testOp.opType == UNCONTAIN_OP || testOp.opType == MOVE_OP ||
                      testOp.opType == PROPERTY_OP)
                  {
                    widgetConflictDiffs.add(new WidgetConflict(currRepoOp, testOp, false));
                  }
                  else if (testOp.opType == RENAME_OP)
                  {
                    if (testOp.w1.getName() != currRepoOp.w1.getName() && (testOp.w1.getName() == null ||
                        !testOp.w1.getName().equals(currRepoOp.w1.getName())))
                    {
                      // Rename difference, medium conflict
                      WidgetConflict wc = new WidgetConflict(currRepoOp, testOp, true);
                      wc.resolutionOptions = new java.util.LinkedHashMap();
                      wc.resolutionOptions.put("Rename to \"" + currRepoOp.w1.getName() + "\"",
                          new WidgetOp[] { currRepoOp });
                      wc.resolutionOptions.put("Rename to \"" + testOp.w1.getName() + "\"",
                          new WidgetOp[] { testOp });
                      widgetConflictDiffs.add(wc);
                    }
                    // else they both did the same rename, which is fine
                  }
                }
              }
              break;
            case CONTAIN_OP:
            case MOVE_OP: // these both end up being the same rules
              relatedOps = (java.util.ArrayList) symToWorkingDiffMap.get(currRepoOp.w1.symbol());
              if (relatedOps != null)
              {
                // Check the parent
                for (int j = 0; j < relatedOps.size(); j++)
                {
                  WidgetOp testOp = (WidgetOp) relatedOps.get(j);
                  // Destroy, rename or property change is soft here
                  if (testOp.opType == DESTROY_OP || testOp.opType == RENAME_OP || testOp.opType == PROPERTY_OP ||
                      (testOp.w1.symbol().equals(currRepoOp.w1.symbol()) && testOp.opType == UNCONTAIN_OP))
                  {
                    widgetConflictDiffs.add(new WidgetConflict(currRepoOp, testOp, false));
                  }
                  else if (testOp.w1.symbol().equals(currRepoOp.w1.symbol()) &&
                      (testOp.opType == CONTAIN_OP || testOp.opType == MOVE_OP))
                  {
                    // Containment difference, possibly ordering issue, medium conflict
                    WidgetConflict wc = new WidgetConflict(currRepoOp, testOp, true);
                    wc.resolutionOptions = new java.util.LinkedHashMap();
                    wc.resolutionOptions.put("Widget2 should be the first child of Widget",
                        new WidgetOp[] { new WidgetOp(currRepoOp.w1, currRepoOp.w2, null) });
                    Widget[] kids = testOp.w1.contents();
                    for (int k = 0; k < kids.length; k++)
                    {
                      wc.resolutionOptions.put("Widget2 should be a child of Widget, inserted after \"" + kids[k].getUntranslatedName() + "\"",
                          new WidgetOp[] { new WidgetOp(currRepoOp.w1, currRepoOp.w2, kids[k]) });
                    }
                    widgetConflictDiffs.add(wc);
                  }
                }
              }
              relatedOps = (java.util.ArrayList) symToWorkingDiffMap.get(currRepoOp.w2.symbol());
              if (relatedOps != null)
              {
                // Check the child (we're more loose here because re-use of components is quite common)
                for (int j = 0; j < relatedOps.size(); j++)
                {
                  WidgetOp testOp = (WidgetOp) relatedOps.get(j);
                  // Destroy, rename or property change is soft here
                  if (testOp.opType == DESTROY_OP || testOp.opType == RENAME_OP || testOp.opType == PROPERTY_OP)
                  {
                    widgetConflictDiffs.add(new WidgetConflict(currRepoOp, testOp, false));
                  }
                }
              }
              break;
            case UNCONTAIN_OP:
              relatedOps = (java.util.ArrayList) symToWorkingDiffMap.get(currRepoOp.w1.symbol());
              if (relatedOps != null)
              {
                // Check the parent
                for (int j = 0; j < relatedOps.size(); j++)
                {
                  WidgetOp testOp = (WidgetOp) relatedOps.get(j);
                  // Rename or property change is soft here
                  if (testOp.opType == RENAME_OP || testOp.opType == PROPERTY_OP)
                  {
                    widgetConflictDiffs.add(new WidgetConflict(currRepoOp, testOp, false));
                  }
                  else if (testOp.w1.symbol().equals(currRepoOp.w1.symbol()) &&
                      (testOp.opType == CONTAIN_OP || testOp.opType == MOVE_OP))
                  {
                    // Repo removed a link from this Widget, we added one, likely OK, soft conflict
                    widgetConflictDiffs.add(new WidgetConflict(currRepoOp, testOp, false));
                  }
                }
              }
              relatedOps = (java.util.ArrayList) symToWorkingDiffMap.get(currRepoOp.w2.symbol());
              if (relatedOps != null)
              {
                // Check the child (we're more loose here because re-use of components is quite common)
                for (int j = 0; j < relatedOps.size(); j++)
                {
                  WidgetOp testOp = (WidgetOp) relatedOps.get(j);
                  // Rename or property change is soft here
                  if (testOp.opType == RENAME_OP || testOp.opType == PROPERTY_OP)
                  {
                    widgetConflictDiffs.add(new WidgetConflict(currRepoOp, testOp, false));
                  }
                }
              }
              break;
            default: // should never occur
              throw new InternalError("ERROR: Unknown diff op type of:" + currRepoOp.opType);
          }
        }

        // Now we're going to want to consolidate all of these since we'll have duplicates for various actions. We should group
        // the list by affected Widgets, and then for each Widget determine how we can consolidate the ops in there. We'll do this for
        // each level of severity.
        // NOTE: WE'RE PUTTING THIS OFF UNTIL A LITTLE LATER BECAUSE IT JUST ADDS MORE ITEMS INTO THE UI IF WE DON'T AND THOSE MAY BE
        // USEFUL FOR DEBUGGING!!!
        if (widgetConflictDiffs.size() > 0)
        {
          launchWidgetConflictResolution(esrc == vcUpdateTestMenuItem);
          return;
        }
        else if (esrc == vcUpdateMenuItem)
        {
          // We still want to do our merge and not the one CVS would do itself
          performSTVRepositoryMerge();
          return;
        }
      }

      // If it's only a test, exit now and don't do the real update/merge
      if (esrc == vcUpdateTestMenuItem)
      {
        // We only got here if there was no conflict UI shown
        // Show a message saying no conflicts found
        javax.swing.JOptionPane.showMessageDialog(myFrame, "There were no conflicts found for the current changes.");
        return;
      }

      String updateRes = repoMaster.updateFile(f);
      javax.swing.JOptionPane.showMessageDialog(myFrame, "Update Result: " + updateRes);
      updateVCMenuStates(false);
      // Reload the STV as well since it changed
      try
      {
        //wiz.setWidgetFile(getstvfc().getSelectedFile());
        uiMgr.freshStartup(f);
      }
      catch (Throwable e)
      {
        javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error loading the updated file:" + e);
        e.printStackTrace();
      }
      refreshTree();
      widgetOperations.clear();
      undoMenuItem.setEnabled(false);
      saveAsMenuItem.setEnabled(!uiMgr.arePluginImportsActive());
    }
    else if (esrc == vcCheckinMenuItem)
    {
      String fname = uiMgr.getModuleGroup().defaultModule.description();
      java.io.File f = new java.io.File(fname);
      if (uiMgr.getModuleGroup().lastModified() > f.lastModified())
      {
        javax.swing.JOptionPane.showMessageDialog(myFrame, "The STV must be saved prior to doing a checkin operation.");
        return;
      }
      sage.version.VersionControlState vcs = repoMaster.getState(f);
      if (!vcs.isModified || !vcs.isCurrent)
      {
        updateVCMenuStates(false);
        return;
      }

      // Popup a dialog asking for the checkin message
      final javax.swing.JTextArea msgText = new javax.swing.JTextArea(8, 80);
      // Put focus on the text field by default
      msgText.addAncestorListener(new javax.swing.event.AncestorListener() {

        public void ancestorAdded(javax.swing.event.AncestorEvent event) {
          try{Thread.sleep(100);}catch(Exception e){}
          javax.swing.SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              msgText.requestFocusInWindow();
            }
          });
        }

        public void ancestorRemoved(javax.swing.event.AncestorEvent event) {
        }

        public void ancestorMoved(javax.swing.event.AncestorEvent event) {
        }
      });
      // NOTE: Don't preserve the last checkin message since this isn't a multi-file IDE
      //if (lastCheckinMessage != null)
      //	msgText.setText(lastCheckinMessage);
      javax.swing.JScrollPane scroller = new javax.swing.JScrollPane(msgText);
      int optionResult = javax.swing.JOptionPane.showOptionDialog(myFrame, scroller, "Checkin Comments for " + fname,
          javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE, null, null, null);
      if (optionResult == javax.swing.JOptionPane.OK_OPTION)
      {
        // Checkin is desired, proceed!
        lastCheckinMessage = msgText.getText().trim();
        String checkinRes = repoMaster.checkinFile(f, lastCheckinMessage);
        javax.swing.JOptionPane.showMessageDialog(myFrame, "Checkin Result: " + checkinRes);
        updateVCMenuStates(false);
      }
    }
    else if (esrc == finalizeConflictsB)
    {
      performSTVRepositoryMerge();
    }
  }

  private void performSTVRepositoryMerge()
  {
    // At this point the user has resolved all the merge conflicts, so we need to do the following.
    // 1. Move our current STV to a temporary file (should save in the same directory, but use an extension that reflects the merged version #s)
    // 2. Do an update on version control so that we have the current head copy
    // 3. Load the STV we just updated from version control (just into a ModuleGroup...we don't need to load it into the UI at this point)
    // 4. Apply all of the CREATE conflict resolution changes to the loaded ModuleGroup
    // 5. Apply all of our working diff updates to the loaded ModuleGroup
    // 6. Apply all of the remaining conflict resolution changes to the loaded ModuleGroup (excludes creates)
    // 7. Save the merged ModuleGroup to our STV file location and load that back up
    //
    // It's important to not close the conflict resolution dialog at this point so that they can introspect resolutions
    // that they may not be sure are correct (and there's issues in the resolution system where you can't always specify the desired output)

    // WARNING: OK, so CVS has issues. Apparently it's not possible to do an update/checkout to a specific revision of a file without setting
    // the sticky tag. So if we tried to update to the revision we know we merged against, we'd put ourselves into a bad state by having a sticky
    // tag set. What we need to do instead is check to see if the head version still matches what we diffed against...and hope that between now and when
    // we do the update, no one checks anything in (although we can catch that...and notify the user...should be a very rare occurence if ever).

    // 1. Rename our current STV
    String currFilename = uiMgr.getModuleGroup().defaultModule.description();
    java.io.File currFile = new java.io.File(currFilename);
    sage.version.VersionControlState vcs = repoMaster.getState(currFile);
    if (!vcs.repositoryVersion.equals(premergeVCState.repositoryVersion))
    {
      javax.swing.JOptionPane.showMessageDialog(myFrame, "ABORTED MERGE: Version control has updated this STV since the merge operation was perform.\nPlease redo the operation.");
      return;
    }
    int dupeCounter = 1;
    String baseRenameFile = ".#merged." + premergeVCState.workingVersion + "-" +
        premergeVCState.repositoryVersion +"-" + currFile.getName();
    java.io.File mergeBackupFile = new java.io.File(currFile.getParentFile(), baseRenameFile);
    while (mergeBackupFile.isFile())
    {
      mergeBackupFile = new java.io.File(currFile.getParentFile(), baseRenameFile + "." + dupeCounter++);
    }
    if (!currFile.renameTo(mergeBackupFile))
    {
      javax.swing.JOptionPane.showMessageDialog(myFrame, "ERROR ABORTING OPERATION: Could not rename STV at " + currFile +
          " to merge backup file at " + mergeBackupFile);
      return;
    }

    // 2. Update to the head version
    repoMaster.updateFile(currFile);
    // Special case check for above WARNING
    vcs = repoMaster.getState(currFile);
    if (!vcs.workingVersion.equals(premergeVCState.repositoryVersion))
    {
      // SUPER UGLY HACK - This is to get around the CVS problem where you can't update to a specific revision
      // without setting the sticky tag....we work around that by doing a manual edit of the Entries file in order to fix this (we tell the user to do it)
      currFile.delete();
      repoMaster.updateFile(currFile, premergeVCState.repositoryVersion);
      javax.swing.JOptionPane.showMessageDialog(myFrame, "WARNING: The repository head version has changed since the conflict resolution was done.\n" +
          "The file was updated to the proper version merged against, but if using CVS, the sticky tag has now been set.\n" +
          "You should manually edit the CVS/Entries file and remove the \"T" + vcs.repositoryVersion +
          "\" from the end of the line for the STV file to resolve this.");
    }

    // 3. Load the STV we just updated from version control (just into a ModuleGroup...we don't need to load it into the UI at this point)
    java.util.Properties fakePrefs = new java.util.Properties();
    fakePrefs.put("STV", currFile.toString());
    tv.sage.ModuleGroup mergeGroup;
    try
    {
      mergeGroup = tv.sage.ModuleManager.loadModuleGroup(fakePrefs);
    }
    catch (tv.sage.SageException se)
    {
      javax.swing.JOptionPane.showMessageDialog(myFrame, "SERIOUS ERROR: Unable to load the STV from the repository...don't know how this could have happened...talk to Jeff about recovery.");
      return;
    }

    // Before we move on, we should come up with the list of all the WidgetOps that are a result of conflict resolution
    java.util.ArrayList firstResolutionOps = new java.util.ArrayList();
    java.util.ArrayList secondResolutionOps = new java.util.ArrayList();
    for (int i = 0; i < widgetConflictDiffs.size(); i++)
    {
      WidgetConflict wc = (WidgetConflict) widgetConflictDiffs.get(i);
      if (wc.resolutionResult != null)
      {
        WidgetOp[] resResult = (WidgetOp[]) wc.resolutionOptions.get(wc.resolutionResult);
        if (resResult != null && resResult.length > 0)
        {
          if (resResult[0].opType == CREATE_OP)
            firstResolutionOps.addAll(java.util.Arrays.asList(resResult));
          else
            secondResolutionOps.addAll(java.util.Arrays.asList(resResult));
        }
      }
    }

    // 4. Apply all of the CREATE conflict resolution changes to the loaded ModuleGroup
    for (int i = 0; i < firstResolutionOps.size(); i++)
    {
      WidgetOp wop = (WidgetOp) firstResolutionOps.get(i);
      wop.applyOpToModuleGroup(mergeGroup);
    }

    // 5. Apply all of our working diff updates to the loaded ModuleGroup
    for (int i = 0; i < workingDiffs.length; i++)
    {
      workingDiffs[i].applyOpToModuleGroup(mergeGroup);
    }

    // 6. Apply all of the remaining conflict resolution changes to the loaded ModuleGroup (excludes creates)
    for (int i = 0; i < secondResolutionOps.size(); i++)
    {
      WidgetOp wop = (WidgetOp) secondResolutionOps.get(i);
      wop.applyOpToModuleGroup(mergeGroup);
    }

    // 7. Save the merged ModuleGroup to our STV file location and load that back up
    try
    {
      mergeGroup.defaultModule.saveXML(currFile, currFilename);
    }
    catch (tv.sage.SageException se)
    {
      javax.swing.JOptionPane.showMessageDialog(myFrame, "SERIOUS ERROR: Unable to save out the merged STV! This should never happen. Contact Jeff for help now.");
    }

    try
    {
      //wiz.setWidgetFile(getstvfc().getSelectedFile());
      uiMgr.freshStartup(currFile);
    }
    catch (Throwable e)
    {
      javax.swing.JOptionPane.showMessageDialog(myFrame, "There was an error loading the merged file:" + e);
      e.printStackTrace();
    }
    refreshTree();
    widgetOperations.clear();
    undoMenuItem.setEnabled(false);
    saveAsMenuItem.setEnabled(!uiMgr.arePluginImportsActive());
    updateVCMenuStates(false);

    if (finalizeConflictsB != null)
      finalizeConflictsB.setVisible(false);
    //if (conflictsFrame != null)
    //	conflictsFrame.pack();
    javax.swing.JOptionPane.showMessageDialog(myFrame, "Yeah! Merging has been done! Please check the results by going through the conflict resolution list again to\n" +
        "ensure the results are as expected....then go grab a beer. :)");
  }

  private void setup3WayUIDiff()
  {
    threeWayUIDiff = true;
    // Sort the list so it's easier to find what we want.
    java.util.Collections.sort(diffRes, new java.util.Comparator()
    {
      public int compare(Object o1, Object o2)
      {
        WidgetOp wop1 = (WidgetOp) o1;
        WidgetOp wop2 = (WidgetOp) o2;
        Widget w1 = (wop1.w2 != null) ? wop1.w2 : wop1.w1;
        Widget w2 = (wop2.w2 != null) ? wop2.w2 : wop2.w1;
        return w1.symbol().compareTo(w2.symbol());
      }
    });
    for (int i = 0; i < diffRes.size(); i++)
    {
      WidgetOp wop = (WidgetOp) diffRes.get(i);
      wop.checkGroup = uiMgr.getModuleGroup();
    }
  }

  private void showDiffFrame(String diffTitle)
  {
    if (diffFrame == null)
    {
      diffFrame = new javax.swing.JFrame("Diff Results [" + diffTitle + "]");
      diffFrame.getContentPane().setLayout(new java.awt.BorderLayout());
      diffList = new javax.swing.JList(diffRes);
      diffList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
      diffList.addListSelectionListener(new javax.swing.event.ListSelectionListener()
      {
        public void valueChanged(javax.swing.event.ListSelectionEvent evt)
        {
          Object selVal = diffList.getSelectedValue();
          if (selVal != null)
          {
            WidgetOp theOp = (WidgetOp) selVal;
            javax.swing.tree.TreePath path = model.getPathToNode(theOp.w1);
            if (path == null)
            {
              // This may be from another STV so we should lookup the symbol
              path = model.getPathToNode(uiMgr.getModuleGroup().symbolMap.get(theOp.w1.symbol()));
            }
            if ((theOp.opType == CONTAIN_OP || theOp.opType == MOVE_OP) && theOp.w2 != null)
            {
              // This may be from another STV so we should lookup the symbol
              Widget testWidg = (Widget) uiMgr.getModuleGroup().symbolMap.get(theOp.w2.symbol());
              javax.swing.tree.TreePath childPath = (path != null && testWidg != null) ? path.pathByAddingChild(testWidg) :
                (testWidg != null ? model.getPathToNode(testWidg) : null);
              if (childPath != null)
              {
                if (path != null)
                  tree.setSelectionPaths(new javax.swing.tree.TreePath[] {
                      path, childPath });
                else
                  tree.setSelectionPath(childPath);
                tree.scrollPathToVisible(childPath);
              }
              else if (path != null)
              {
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
              }
            }
            else if (path != null)
            {
              tree.setSelectionPath(path);
              tree.scrollPathToVisible(path);
            }
          }
        }
      });
      diffList.addMouseListener(new java.awt.event.MouseAdapter()
      {
        javax.swing.JPopupMenu rightClicky;
        javax.swing.JMenuItem applyDiff;
        javax.swing.JMenuItem reverseDiff;
        javax.swing.JMenuItem ignoreDiff;
        {
          applyDiff = new javax.swing.JMenuItem("Apply Difference(s)");
          applyDiff.addActionListener(new java.awt.event.ActionListener()
          {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
              Object[] selVals = diffList.getSelectedValues();
              if (selVals != null)
              {
                java.util.ArrayList opsToRemove = new java.util.ArrayList();
                for (int i = 0; i < selVals.length; i++)
                {
                  WidgetOp op = (WidgetOp) selVals[i];
                  if (op.opType == DESTROY_OP)
                  {
                    uiMgr.getModuleGroup().removeWidget((Widget)uiMgr.getModuleGroup().symbolMap.get(op.w1.symbol()));
                    opsToRemove.add(op);
                  }
                  else if (op.opType == CREATE_OP)
                  {
                    Widget newWidg = uiMgr.getModuleGroup().addWidget(op.w1.type(), op.w1.symbol());
                    WidgetFidget.setName(newWidg, op.w1.getName());
                    for (byte p = 0; p <= Widget.MAX_PROP_NUM; p++)
                    {
                      if (op.w1.hasProperty(p))
                      {
                        WidgetFidget.setProperty(newWidg, p, op.w1.getProperty(p));
                      }
                    }
                    opsToRemove.add(op);
                  }
                  else if (op.opType == RENAME_OP)
                  {
                    WidgetFidget.setName((Widget)uiMgr.getModuleGroup().symbolMap.get(op.w1.symbol()), op.w1.getName());
                    opsToRemove.add(op);
                  }
                  else if (op.opType == PROPERTY_OP)
                  {
                    WidgetFidget.setProperty((Widget)uiMgr.getModuleGroup().symbolMap.get(op.w1.symbol()), op.pn,
                        op.w1.getProperty(op.pn));
                    opsToRemove.add(op);
                  }
                  else if (op.opType == CONTAIN_OP || op.opType == MOVE_OP)
                  {
                    Widget w1 = (Widget)uiMgr.getModuleGroup().symbolMap.get(op.w1.symbol());
                    Widget w2 = (Widget)uiMgr.getModuleGroup().symbolMap.get(op.w2.symbol());
                    if (w1 != null && w2 != null)
                    {
                      WidgetFidget.contain(w1, w2,
                          java.util.Arrays.asList(op.w1.contents()).indexOf(op.w2));
                      opsToRemove.add(op);
                    }
                  }
                  else if (op.opType == UNCONTAIN_OP)
                  {
                    Widget w1 = (Widget)uiMgr.getModuleGroup().symbolMap.get(op.w1.symbol());
                    Widget w2 = (Widget)uiMgr.getModuleGroup().symbolMap.get(op.w2.symbol());
                    if (w1 != null && w2 != null)
                    {
                      WidgetFidget.discontent(w1, w2);
                      opsToRemove.add(op);
                    }
                  }
                }
                diffRes.removeAll(opsToRemove);
                diffList.setListData(diffRes);
                refreshTree();
              }
            }
          });
          reverseDiff = new javax.swing.JMenuItem("Reverse Difference(s)");
          reverseDiff.addActionListener(new java.awt.event.ActionListener()
          {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
              Object[] selVals = diffList.getSelectedValues();
              if (selVals != null)
              {
                java.util.Vector opsToRemove = new java.util.Vector();
                for (int i = 0; i < selVals.length; i++)
                {
                  WidgetOp op = (WidgetOp) selVals[i];
                  if (op.opType == CREATE_OP)
                  {
                    uiMgr.getModuleGroup().removeWidget(op.w1);
                    opsToRemove.add(op);
                  }
                  else if (op.opType == DESTROY_OP)
                  {
                    Widget newWidg = uiMgr.getModuleGroup().addWidget(op.w1.type(), op.w1.symbol());
                    WidgetFidget.setName(newWidg, op.w1.getName());
                    for (byte p = 0; p <= Widget.MAX_PROP_NUM; p++)
                    {
                      if (op.w1.hasProperty(p))
                      {
                        WidgetFidget.setProperty(newWidg, p, op.w1.getProperty(p));
                      }
                    }
                    opsToRemove.add(op);
                  }
                  else if (op.opType == RENAME_OP)
                  {
                    WidgetFidget.setName(op.w1, op.pv);
                    opsToRemove.add(op);
                  }
                  else if (op.opType == PROPERTY_OP)
                  {
                    WidgetFidget.setProperty(op.w1, op.pn, op.pv);
                    opsToRemove.add(op);
                  }
                  else if (op.opType == UNCONTAIN_OP || op.opType == MOVE_OP)
                  {
                    WidgetFidget.contain(op.w1, op.w2, op.idx);
                    opsToRemove.add(op);
                  }
                  else if (op.opType == CONTAIN_OP)
                  {
                    WidgetFidget.discontent(op.w1, op.w2);
                    opsToRemove.add(op);
                  }
                }
                diffRes.removeAll(opsToRemove);
                diffList.setListData(diffRes);
                refreshTree();
              }
            }
          });
          ignoreDiff = new javax.swing.JMenuItem("Ignore Difference(s)");
          ignoreDiff.addActionListener(new java.awt.event.ActionListener()
          {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
              Object[] selVals = diffList.getSelectedValues();
              if (selVals != null)
              {
                diffRes.removeAll(java.util.Arrays.asList(selVals));
                diffList.setListData(diffRes);
                refreshTree();
              }
            }
          });
        }

        public void mouseReleased(java.awt.event.MouseEvent evt)
        {
          if (doingUIDDiff && javax.swing.SwingUtilities.isRightMouseButton(evt))
          {
            if (rightClicky == null)
            {
              rightClicky = new javax.swing.JPopupMenu("Diff Options");
              rightClicky.add(applyDiff);
              rightClicky.add(ignoreDiff);
              rightClicky.add(reverseDiff);
            }
            applyDiff.setEnabled(threeWayUIDiff);
            ignoreDiff.setEnabled(true);
            reverseDiff.setEnabled(!threeWayUIDiff);
            Object selVal = diffList.getSelectedValue();
            if (selVal != null)
            {
              MySwingUtils.safeShowPopupMenu(rightClicky, diffList, evt.getX(), evt.getY());
            }
          }
        }
      });
      diffFrame.getContentPane().add(new javax.swing.JScrollPane(diffList), "Center");
      genSTVIB = new javax.swing.JButton("Generate STVI");
      genSTVIB.addActionListener(this);
      javax.swing.JPanel panny = new javax.swing.JPanel();
      panny.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
      panny.add(genSTVIB);
      diffFrame.getContentPane().add(panny, "North");
      diffFrame.pack();
      diffFrame.setSize(uiMgr.getInt("studio/diff_win_pos_w", diffFrame.getWidth()),
          uiMgr.getInt("studio/diff_win_pos_h", diffFrame.getHeight()));
      diffFrame.setLocation(uiMgr.getInt("studio/diff_win_pos_x", 100),
          uiMgr.getInt("studio/diff_win_pos_y", 100));
      diffFrame.addWindowListener(new java.awt.event.WindowAdapter()
      {
        public void windowClosing(java.awt.event.WindowEvent evt)
        {
          uiMgr.putInt("studio/diff_win_pos_x", diffFrame.getX());
          uiMgr.putInt("studio/diff_win_pos_y", diffFrame.getY());
          uiMgr.putInt("studio/diff_win_pos_w", diffFrame.getWidth());
          uiMgr.putInt("studio/diff_win_pos_h", diffFrame.getHeight());
          diffFrame.dispose();
        }
      });
    }
    else
    {
      diffFrame.setTitle("Diff Results [" + diffTitle + "]");
      diffList.setListData(diffRes);
    }
    //diffFrame.pack();
    genSTVIB.setVisible(doingUIDDiff);
    diffList.setSelectionMode(doingUIDDiff ? javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION :
      javax.swing.ListSelectionModel.SINGLE_SELECTION);
    diffFrame.setVisible(true);

  }

  private void launchWidgetConflictResolution(boolean testOnly)
  {
    if (conflictsFrame == null)
    {
      conflictsFrame = new javax.swing.JFrame("Widget Conflict Resolution");
      conflictTableModel = new WidgetConflictTableModel();
      conflictTable = new javax.swing.JTable(conflictTableModel);
      conflictTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
      conflictTable.setCellSelectionEnabled(false);
      conflictTable.setRowSelectionAllowed(true);
      conflictTable.setColumnSelectionAllowed(false);
      conflictTable.getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener()
      {
        public void valueChanged(javax.swing.event.ListSelectionEvent evt)
        {
          Object selVal = conflictTableModel.getRowValue(conflictTable.getSelectedRow());
          if (selVal != null)
          {
            WidgetConflict theOp = (WidgetConflict) selVal;
            Widget targetWidg = theOp.repoOp.w1;
            if (!theOp.workingOp.w1.symbol().equals(targetWidg.symbol()) && theOp.repoOp.w2 != null)
            {
              if (theOp.repoOp.w2.symbol().equals(theOp.workingOp.w1.symbol()))
                targetWidg = theOp.repoOp.w2;
              else if (theOp.workingOp.w2 != null)
                targetWidg = theOp.workingOp.w2;
            }
            // Do the lookup by symbol here in case this is after the resolution is done where we've then loaded a
            // new merged STV and the object may not reference the same thing anymore.
            javax.swing.tree.TreePath path = model.getPathToNode(uiMgr.getModuleGroup().symbolMap.get(targetWidg.symbol()));
            if (path != null)
            {
              tree.setSelectionPathWithoutHighlight(path);
              tree.scrollPathToVisible(path);
            }
          }
        }
      });
      conflictTable.addMouseListener(new java.awt.event.MouseAdapter()
      {
        javax.swing.JPopupMenu rightClicky;

        public void mouseReleased(java.awt.event.MouseEvent evt)
        {
          if (javax.swing.SwingUtilities.isRightMouseButton(evt))
          {
            final WidgetConflict theOp;
            Object selVal = conflictTableModel.getRowValue(conflictTable.getSelectedRow());
            if (selVal != null)
              theOp = (WidgetConflict) selVal;
            else
              theOp = null;
            if (theOp == null || !theOp.hard)
              return;

            if (rightClicky == null)
              rightClicky = new javax.swing.JPopupMenu("Resolution Options");
            else
              rightClicky.removeAll();

            java.util.Iterator walker = theOp.resolutionOptions.keySet().iterator();
            javax.swing.ButtonGroup buttGroup = new javax.swing.ButtonGroup();
            while (walker.hasNext())
            {
              final String str = walker.next().toString();
              javax.swing.JRadioButtonMenuItem resolutionItem = new javax.swing.JRadioButtonMenuItem(str, str.equals(theOp.resolutionResult));
              resolutionItem.addActionListener(new java.awt.event.ActionListener()
              {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                  theOp.resolutionResult = str;
                  theOp.resolved = true;
                  conflictTableModel.fireTableDataChanged();
                  if (finalizeConflictsB.isVisible() && !finalizeConflictsB.isEnabled())
                  {
                    // Check if all are resolved now
                    for (int i = 0; i < widgetConflictDiffs.size(); i++)
                    {
                      if (!((WidgetConflict) widgetConflictDiffs.get(i)).resolved)
                        return;
                    }
                    finalizeConflictsB.setEnabled(true);
                  }
                }
              });
              buttGroup.add(resolutionItem);
              rightClicky.add(resolutionItem);
            }
            MySwingUtils.safeShowPopupMenu(rightClicky, conflictTable, evt.getX(), evt.getY());
          }
        }
      });
      conflictsFrame.getContentPane().add(new javax.swing.JScrollPane(conflictTable), "Center");

      // Need to add button here for confirming the updates if it's not just a test
      finalizeConflictsB = new javax.swing.JButton("Apply Resolutions and Merge Update");
      finalizeConflictsB.addActionListener(this);
      javax.swing.JPanel panny = new javax.swing.JPanel();
      panny.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
      panny.add(finalizeConflictsB);
      conflictsFrame.getContentPane().add(panny, "North");

      conflictsFrame.pack();
      conflictsFrame.setSize(uiMgr.getInt("studio/conflicts_win_pos_w", conflictsFrame.getWidth()),
          uiMgr.getInt("studio/conflicts_win_pos_h", conflictsFrame.getHeight()));
      conflictsFrame.setLocation(uiMgr.getInt("studio/conflicts_win_pos_x", 100),
          uiMgr.getInt("studio/conflicts_win_pos_y", 100));
      int numCols = conflictTable.getColumnCount();
      for (int i = 0; i < numCols; i++)
      {
        int prefWidth = uiMgr.getInt("studio/conflicts_col_width/" + i, 0);
        if (prefWidth > 0)
          conflictTable.getColumnModel().getColumn(i).setPreferredWidth(prefWidth);
      }
      conflictsFrame.addWindowListener(new java.awt.event.WindowAdapter()
      {
        public void windowClosing(java.awt.event.WindowEvent evt)
        {
          int numCols = conflictTable.getColumnCount();
          for (int i = 0; i < numCols; i++)
            uiMgr.putInt("studio/conflicts_col_width/" + i, conflictTable.getColumnModel().getColumn(i).getWidth());
          uiMgr.putInt("studio/conflicts_win_pos_x", conflictsFrame.getX());
          uiMgr.putInt("studio/conflicts_win_pos_y", conflictsFrame.getY());
          uiMgr.putInt("studio/conflicts_win_pos_w", conflictsFrame.getWidth());
          uiMgr.putInt("studio/conflicts_win_pos_h", conflictsFrame.getHeight());
          conflictsFrame.dispose();
        }
      });

    }
    finalizeConflictsB.setEnabled(false);
    finalizeConflictsB.setVisible(!testOnly);
    conflictsFrame.setVisible(true);
  }

  private String fixEscapes(String s)
  {
    if (s == null) return s;
    int idx = s.indexOf('\\');
    if (idx == -1) return s;
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (c == '\\')
        sb.append(c);
      sb.append(c);
    }
    return sb.toString();
  }

  private String addEscapes(String s)
  {
    if (s == null) return s;
    StringBuffer sb = null;
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (c == '"' || c == '\\')
      {
        if (sb == null)
          sb = new StringBuffer(s.substring(0, i));
        sb.append('\\');
        sb.append(c);
      }
      else if (sb != null)
        sb.append(c);
    }
    return sb == null ? s : sb.toString();
  }

  private Widget addWidgetAsChild(tv.sage.ModuleGroup mod, String name, byte type, Widget parent)
  {
    Widget noob = mod.addWidget(type, null);
    WidgetFidget.setName(noob, name);
    WidgetFidget.contain(parent, noob);
    return noob;
  }

  private void addParentActionChainToTrans(Widget widg, java.util.Set transText, java.util.Set doneSet)
  {
    // Avoid redundantly doing unnecessary parent calculations
    if (!doneSet.add(widg)) return;
    if (widg.isType(Widget.ACTION))
      transText.add(fixEscapes(widg.getUntranslatedName()));
    Widget[] allParents = widg.containers();
    for (int i = 0; i < allParents.length; i++)
    {
      if (allParents[i].isType(Widget.ACTION))
      {
        addParentActionChainToTrans(allParents[i], transText, doneSet);
      }
    }
    for (int i = 0; i < allParents.length; i++)
    {
      if (allParents[i].isType(Widget.BRANCH))
      {
        if (doneSet.add(allParents[i]))
        {
          //					transText.add(allParents[i].getName());
          Widget[] branchParentConds = allParents[i].containers();
          for (int j = 0; j < branchParentConds.length; j++)
          {
            if (branchParentConds[j].isType(Widget.CONDITIONAL))
            {
              addParentActionChainToTrans(branchParentConds[j], transText, doneSet);
            }
          }
        }
      }
    }
    for (int i = 0; i < allParents.length; i++)
    {
      if (allParents[i].isType(Widget.CONDITIONAL))
      {
        addParentActionChainToTrans(allParents[i], transText, doneSet);
      }
    }
  }

  public void addBreakpoint(Widget breaker)
  {
    addBreakpoint(breaker, Breakpoint.getValidFlagsForWidget(breaker));
  }
  public void addBreakpoint(Widget breaker, int breakFlags)
  {
    // First check if this one is already set
    for (int i = 0; i < breakpoints.size(); i++)
    {
      Breakpoint bp = (Breakpoint) breakpoints.get(i);
      if (bp.getWidget() == breaker)
      {
        bp.setFlags(breakFlags);
        bp.setEnabled(true);
        if (breaksTableModel != null)
          breaksTableModel.fireTableDataChanged();
        return;
      }
    }
    breakpoints.add(new Breakpoint(breaker, breakFlags));
    if (breaksTableModel != null)
      breaksTableModel.fireTableDataChanged();
  }

  public void removeAllBreakpoints()
  {
    while (!breakpoints.isEmpty())
    {
      Breakpoint bp = (Breakpoint) breakpoints.get(0);
      bp.setEnabled(false); // clear the bp mask in the Widget
      breakpoints.removeElementAt(0);
    }
    if (breaksTableModel != null)
      breaksTableModel.fireTableDataChanged();
  }

  public void enableAllBreakpoints(boolean x)
  {
    for (int i = 0; i < breakpoints.size(); i++)
    {
      Breakpoint bp = (Breakpoint) breakpoints.get(i);
      bp.setEnabled(x);
    }
    if (breaksTable != null)
      breaksTable.repaint();
  }

  public void removeBreakpoint(Widget w)
  {
    for (int i = 0; i < breakpoints.size(); i++)
    {
      Breakpoint bp = (Breakpoint) breakpoints.get(i);
      if (bp.getWidget() == w)
      {
        bp.setEnabled(false); // clear the bp mask in the Widget
        breakpoints.removeElementAt(i);
        if (breaksTableModel != null)
          breaksTableModel.fireTableDataChanged();
        return;
      }
    }
  }

  public java.util.Vector generateDiffOpsUID(Widget[] newWidgs, int newWidgCount, Widget[] oldWidgs, int oldWidgCount)
  {
    // The technique for doing the UID-based diff is somewhat different.
    // This is what we will detect:
    // 1. Removal of widget (no widget w/ matching UID found in new set)
    // 2. Addition of new widget and setting its properties and establishing its containments (widget in
    //    new set has a UID which is not in the old set)
    // 3. Modification of properties (compare props of widgets w/ the same UID in each set)
    // 4. Removal/Addition/Reorder of containment (compare child widgets of each widg w/ matching UID between old and new)
    // Between those 4 sets of modifications we can describe a complete change from one STV to another
    java.util.Vector rv = new java.util.Vector();

    java.util.Map oldSymMap = new java.util.HashMap();
    java.util.Map newSymMap = new java.util.HashMap();
    for (int i = 0; i < newWidgCount; i++)
      newSymMap.put(newWidgs[i].symbol(), newWidgs[i]);
    for (int i = 0; i < oldWidgCount; i++)
      oldSymMap.put(oldWidgs[i].symbol(), oldWidgs[i]);

    // Step 1
    java.util.Set removedSyms = new java.util.HashSet();
    for (int i = 0; i < oldWidgCount; i++)
    {
      if (!newSymMap.containsKey(oldWidgs[i].symbol()))
      {
        removedSyms.add(oldWidgs[i].symbol());
        rv.add(new WidgetOp(false, oldWidgs[i]));
      }
    }

    // Step 2
    java.util.Set addedSyms = new java.util.HashSet();
    for (int i = 0; i < newWidgCount; i++)
    {
      if (!oldSymMap.containsKey(newWidgs[i].symbol()))
      {
        addedSyms.add(newWidgs[i].symbol());
        rv.add(new WidgetOp(true, newWidgs[i]));
      }
    }

    // Steps 3 & 4
    for (int i = 0; i < newWidgCount; i++)
    {
      Widget oldWidget = (Widget) oldSymMap.get(newWidgs[i].symbol());
      Widget newWidget = newWidgs[i];
      if (oldWidget != null)
      {
        if (!newWidget.getUntranslatedName().equals(oldWidget.getUntranslatedName()))
          rv.add(new WidgetOp(newWidget, oldWidget.getUntranslatedName()));
        if (!oldWidget.isIdenticalProperties(newWidget))
        {
          // Property differences
          for (byte j = 0; j <= Widget.MAX_PROP_NUM; j++)
          {
            String oldProp = oldWidget.getProperty(j);
            String newProp = newWidget.getProperty(j);
            if (oldProp.equals(newProp))
              continue;
            rv.add(new WidgetOp(newWidget, j, oldProp));
          }
        }
        // Go through our children and try to match them up to children in the diff widget. This will
        // give us 4 categories. Added children, removed children, moved children and unchanged children.
        java.util.Vector newKids = new java.util.Vector(java.util.Arrays.asList(newWidget.contents()));
        java.util.List orgOldKids = java.util.Arrays.asList(oldWidget.contents());
        java.util.Vector oldKids = new java.util.Vector(orgOldKids);
        // First remove any 'removed' kids from the old kids since they're already destroyed from the remove op
        for (int j = 0; j < oldKids.size(); j++)
        {
          Widget oldTest = (Widget) oldKids.get(j);
          if (removedSyms.contains(oldTest))
          {
            oldKids.removeElementAt(j);
            j--;
            continue;
          }
          boolean matchFound = false;
          for (int k = 0; k < newKids.size(); k++)
          {
            Widget newTest = (Widget) newKids.get(k);
            if (newTest.symbol().equals(oldTest.symbol()))
            {
              matchFound = true;
              break;
            }
          }
          if (!matchFound)
          {
            rv.add(new WidgetOp(newWidget, oldTest, j));
            oldKids.removeElementAt(j);
            j--;
          }
        }
        // If there's any 'new' widgets that we've contained, then put those in the op list as new containments
        // and add them to the end of the old list to prep it for the 'move'
        for (int j = 0; j < newKids.size(); j++)
        {
          Widget newTest = (Widget) newKids.get(j);
          boolean matchFound = false;
          for (int k = 0; k < oldKids.size(); k++)
          {
            Widget oldTest = (Widget) oldKids.get(k);
            if (newTest.symbol().equals(oldTest.symbol()))
            {
              matchFound = true;
              break;
            }
          }
          if (!matchFound)
          {
            rv.add(new WidgetOp(newWidget, newTest));
            oldKids.add(newTest);
          }
        }
        if (newKids.size() != oldKids.size())
          System.out.println("WARNING: MISMATCHED SIZES FOR DIFF KIDS!!!!!");

        // We definitely want to minimize the # of diff ops as well. That'll give it a better chance of
        // them all succeeding w/out error. Don't use 'relative' widgets because that won't detect the moves
        // of widgets between the relative changes.
        for (int k = 0; k < newKids.size(); k++)
        {
          Widget newTest = (Widget) newKids.get(k);
          if (newTest.symbol().equals(((Widget)oldKids.get(k)).symbol()))
            continue;
          // We have a difference.
          for (int j = 0; j < oldKids.size(); j++)
          {
            if (j == k) continue;
            Widget oldTest = (Widget) oldKids.get(j);
            if (oldTest.symbol().equals(newTest.symbol()))
            {
              if (j == k + 1)
              {
                // We're just moving it up one; so instead of that move this one out to its proper location
                String testSym = ((Widget) oldKids.get(k)).symbol();
                int targetIdx = -1;
                for (int m = k + 1; m < newKids.size(); m++)
                {
                  if (((Widget) newKids.get(m)).symbol().equals(testSym))
                  {
                    targetIdx = m;
                    break;
                  }
                }
                if (targetIdx < 0)
                {
                  System.out.println("ERROR in diff detection; didn't find targetIdx!");
                }
                else
                {
                  Object moveMe = oldKids.remove(k);
                  WidgetOp wop = new WidgetOp(newWidget, (Widget) newKids.get(targetIdx), orgOldKids.indexOf(moveMe));
                  wop.opType = MOVE_OP;
                  rv.add(wop);
                  oldKids.insertElementAt(moveMe, targetIdx);
                  k = 0;
                  break;
                }
              }
              WidgetOp wop = new WidgetOp(newWidget, newTest, orgOldKids.indexOf(oldTest));
              wop.opType = MOVE_OP;
              rv.add(wop);
              oldKids.remove(j);
              oldKids.insertElementAt(oldTest, k);
              k = 0;
              break;
            }
          }
        }
      }
      else
      {
        // Newly added Widget; check for its new containments
        Widget[] newKids = newWidget.contents();
        for (int j = 0; j < newKids.length; j++)
        {
          rv.add(new WidgetOp(newWidget, newKids[j]));
        }
      }
    }


    return rv;
  }

  public java.util.Vector generateDiffOps(Widget[] currWidgs, int currWidgCount, Widget[] diffWidgs, int diffWidgCount)
  {
    java.util.Vector rv = new java.util.Vector();
    java.util.Set checkedWidgs = new java.util.HashSet();

    java.util.Vector rootWidgs = new java.util.Vector();
    for (int i = 0; i < currWidgCount; i++)
    {
      Widget widg = currWidgs[i];
      // We only want Menus and unparented Hooks
      // UPDATE: 4/28/06 Narflex - Why wouldn't we also want all the unparented Widgets instead of just the unparented hooks?
      // Doing it that way will leave out widget chains that aren't utilized in the code which may be why it was done,
      // but it FAILS to correctly identify differences rooted in those other widgets if don't check all of them.
      if (widg.isType(Widget.MENU) || (/*widg.isType(Widget.HOOK) && */widg.numContainers() == 0))
        rootWidgs.add(widg);
    }

    java.util.Vector accountedRoots = new java.util.Vector();
    // Now go through the diff widgs and find any mismatches in the roots
    for (int i = 0; i < diffWidgCount; i++)
    {
      Widget widg = diffWidgs[i];
      if (widg.isType(Widget.MENU) || (/*widg.isType(Widget.HOOK) && */widg.numContainers() == 0))
      {
        // We need to check this widg against the roots
        boolean foundMatch = false;
        for (int j = 0; j < rootWidgs.size(); j++)
        {
          if (((Widget) rootWidgs.get(j)).isNameTypeMatch(widg))
          {
            accountedRoots.add(new Widget[] { (Widget)rootWidgs.remove(j), widg });
            foundMatch = true;
            break;
          }
        }
        if (!foundMatch)
        {
          // This root widget was in the diff file, but not in the current file. Therefore
          // it was destroyed.
          rv.add(new WidgetOp(false, widg));
        }
      }
    }

    // Any roots left that weren't accounted for were adds
    for (int i = 0; i < rootWidgs.size(); i++)
      rv.add(new WidgetOp(true, (Widget) rootWidgs.get(i)));

    checkedWidgs.addAll(accountedRoots);

    for (int i = 0; i < accountedRoots.size(); i++)
    {
      Widget[] rootPair = (Widget[]) accountedRoots.get(i);
      checkForRecursiveDiffs(rootPair[0], rootPair[1], rv, checkedWidgs);
    }

    return rv;
  }

  static void checkForRecursiveDiffs(Widget currWidg, Widget diffWidg, java.util.Vector rv, java.util.Set checkedWidgs)
  {
    if (!checkedWidgs.add(currWidg))
      return;
    if (!currWidg.getUntranslatedName().equals(diffWidg.getUntranslatedName()))
      rv.add(new WidgetOp(currWidg, diffWidg.getUntranslatedName()));
    if (!currWidg.isIdenticalProperties(diffWidg))
    {
      // Property differences
      for (byte i = 0; i <= Widget.MAX_PROP_NUM; i++)
      {
        String diffProp = diffWidg.getProperty(i);
        String currProp = currWidg.getProperty(i);
        if (diffProp.equals(currProp))
          continue;
        rv.add(new WidgetOp(currWidg, i, diffProp));
      }
    }

    // Go through our children and try to match them up to children in the diff widget. This will
    // give us 4 categories. Added children, removed children, moved children and unchanged children.
    // For moved & unchanged children, we also need to check them for property changes...but this occurs
    // when we recursively process them.
    java.util.Vector currKids = new java.util.Vector(java.util.Arrays.asList(currWidg.contents()));
    java.util.Vector diffKids = new java.util.Vector(java.util.Arrays.asList(diffWidg.contents()));
    for (int i = 0; i < currKids.size(); i++)
    {
      Widget currTest = (Widget) currKids.get(i);
      for (int j = 0; j < diffKids.size(); j++)
      {
        Widget diffTest = (Widget) diffKids.get(j);
        if (diffTest != null && (diffTest.isNameTypeMatch(currTest) ||
            (currKids.size() == diffKids.size() && currTest.type() == diffTest.type())))
        {
          currKids.setElementAt(null, i);
          diffKids.setElementAt(null, j);
          if (i != j) // index move
          {
            WidgetOp wop = new WidgetOp(currWidg, currTest, j);
            wop.opType = MOVE_OP;
            rv.add(wop);
          }

          // We don't recurse menus
          if (!currTest.isType(Widget.MENU))
          {
            checkForRecursiveDiffs(currTest, diffTest, rv, checkedWidgs);
          }
          break;
        }
      }
    }

    // What's left in the currKids list is what was added and what's left in the diffKids list is
    // what was removed.
    for (int i = 0; i < currKids.size(); i++)
    {
      if (currKids.get(i) != null)
        rv.add(new WidgetOp(currWidg, (Widget) currKids.get(i)));
    }
    for (int i = 0; i < diffKids.size(); i++)
    {
      if (diffKids.get(i) != null)
        rv.add(new WidgetOp(currWidg, (Widget) diffKids.get(i), i));
    }
  }

  public java.util.Vector removeWidgetsWithUndo(Widget[] killUs, java.util.Vector undoList)
  {
    if (undoList == null) undoList = new java.util.Vector();
    for (int i = 0; i < killUs.length; i++)
    {
      Widget[] kids = killUs[i].contents();
      for (int j = 0; j < kids.length; j++)
      {
        int oldIdx = WidgetFidget.discontent(killUs[i], kids[j]);
        undoList.add(new WidgetOp(killUs[i], kids[j], oldIdx));
      }
      Widget[] parents = killUs[i].containers();
      for (int j = 0; j < parents.length; j++)
      {
        int oldIdx = WidgetFidget.discontent(parents[j], killUs[i]);
        undoList.add(new WidgetOp(parents[j], killUs[i], oldIdx));
      }
      uiMgr.getModuleGroup().removeWidget(killUs[i]);
      undoList.add(new WidgetOp(false, killUs[i]));
    }
    return undoList;
  }

  public void pushWidgetOp(Object wop)
  {
    pushOp(wop);
  }
  public void pushWidgetOps(java.util.List wops)
  {
    if (wops != null && !wops.isEmpty())
      pushOp(wops);
  }
  private void pushOp(Object o)
  {
    widgetOperations.push(o);
    while (widgetOperations.size() > uiMgr.getInt("studio_undo_depth", 100))
      widgetOperations.removeElementAt(0);
    undoMenuItem.setEnabled(true);
  }
  public boolean hasUndo() { return !widgetOperations.isEmpty(); }
  public void undo()
  {
    if (widgetOperations.isEmpty()) return;
    Object lastOp = widgetOperations.pop();
    java.util.List opList;
    if (lastOp instanceof java.util.List)
      opList = (java.util.List) lastOp;
    else
    {
      opList = new java.util.Vector();
      opList.add(lastOp);
    }
    for (int i = opList.size() - 1; i >= 0 ; i--)
    {
      WidgetOp undoThisOp = (WidgetOp) opList.get(i);
      switch (undoThisOp.opType)
      {
        case CONTAIN_OP:
          WidgetFidget.discontent(undoThisOp.w1, undoThisOp.w2);
          break;
        case UNCONTAIN_OP: // the index of the containment matters, we can't simply redo this
          WidgetFidget.contain(undoThisOp.w1, undoThisOp.w2, undoThisOp.idx);
          break;
        case PROPERTY_OP:
          WidgetFidget.setProperty(undoThisOp.w1, undoThisOp.pn, undoThisOp.pv);
          break;
        case RENAME_OP:
          WidgetFidget.setName(undoThisOp.w1, undoThisOp.pv);
          break;
        case CREATE_OP:
          uiMgr.getModuleGroup().removeWidget(undoThisOp.w1);
          break;
        case DESTROY_OP:
          // We need to maintain the old object so redoing of the containment ops works correctly,
          // since we do those based off Object and not off DBID
          uiMgr.getModuleGroup().resurrectWidget(undoThisOp.w1);
          break;
      }
    }
    undoMenuItem.setEnabled(!widgetOperations.isEmpty());
    model.refreshTree();
  }

  public Breakpoint getBreakpointInfo(Widget w)
  {
    for (int i = 0; i < breakpoints.size(); i++)
    {
      Breakpoint bp = (Breakpoint) breakpoints.get(i);
      if (bp.getWidget() == w)
        return bp;
    }
    return null;
  }

  private boolean breakCheck(Widget w, int mask)
  {
    // Steps should stop on everything, not just what's enabled for tracing
    if (suspendExecution) return true;
    if (w != null && (w.getBreakpointMask() & mask) != 0)
    {
      if (Sage.DBG) System.out.println("Hit Breakpoint: " + w);
      if (((mask != Tracer.POST_CONDITIONAL_UI && mask != Tracer.POST_EVALUATE_COMPONENT_UI && mask != Tracer.POST_EVALUATE_DATA_UI)) ||
          mask == Tracer.PRE_EVALUATION)
        pauseExecution();
      if (breaksTable != null && breakpointsFrame.isShowing())
      {
        breaksTable.clearSelection();
        for (int i = 0; i < breakpoints.size(); i++)
        {
          Breakpoint bp = (Breakpoint) breakpoints.get(i);
          if (bp.getWidget() == w)
          {
            breaksTable.addRowSelectionInterval(i, i);
            breaksTable.scrollRectToVisible(breaksTable.getCellRect(i,1,true));
            break;
          }
        }
        //breaksList.setSelectedValue(getBreakpointInfo(w), true);
      }
      return true;
    }
    return false;
  }
  public void traceEvaluate(int evalState, String expr, Widget w, Catbert.Context con)
  {
    if (!myFrame.isShowing() || (!breakCheck(w, evalState) && (traceEvaluateMask & evalState) == 0)) return;
    addToTrace(new TracerOp(w, expr, evalState, (con != null && TracerOp.hasRez(evalState)) ? con.safeLookup(null) : null));
    highlightNode(w);
    // Don't suspend execution on post evaluate
    if (evalState == Tracer.PRE_EVALUATION)
      suspensionCheck(con);
  }

  public void traceEvent(ZPseudoComp currCheck, String sageCommand, long irCode, int keyCode, int keyModifiers, char keyChar)
  {
    if (!myFrame.isShowing() || (!breakCheck(currCheck.getWidget(), Tracer.EVENT_TRACE) && !traceEventEnabled)) return;
    addToTrace(new TracerOp(currCheck.getWidget(), null, Tracer.EVENT_TRACE, null));
    highlightNode(currCheck.getWidget());
    suspensionCheck(currCheck.getRelatedContext());
  }

  public void traceListener(ZPseudoComp uiComp, Widget listener)
  {
    if (!myFrame.isShowing() || (!breakCheck(listener, Tracer.LISTENER_TRACE) && !traceListenerEnabled)) return;
    addToTrace(new TracerOp(listener, null, Tracer.LISTENER_TRACE, null));
    highlightNode(listener);
    suspensionCheck(uiComp.getRelatedContext());
  }

  public void traceUI(int traceAction, ZPseudoComp z, Widget w, Object result)
  {
    if (!myFrame.isShowing() || (!breakCheck(w, traceAction) && (traceUIMask & traceAction) == 0)) return;
    addToTrace(new TracerOp(w, null, traceAction, result));
    highlightNode(w);
    // Don't suspend on the post UI stuff
    if (traceAction != Tracer.POST_CONDITIONAL_UI && traceAction != Tracer.POST_EVALUATE_COMPONENT_UI && traceAction != Tracer.POST_EVALUATE_DATA_UI)
      suspensionCheck(z.getRelatedContext());
  }

  public void traceMenu(Widget w)
  {
    if (!myFrame.isShowing() || (!breakCheck(w, Tracer.MENU_TRACE) && !traceMenuEnabled)) return;
    addToTrace(new TracerOp(w, null, Tracer.MENU_TRACE, null));
    highlightNode(w);
    suspensionCheck(null);
  }

  public void traceOptionsMenu(Widget w)
  {
    if (!myFrame.isShowing() || (!breakCheck(w, Tracer.OPTIONSMENU_TRACE) && !traceOptionsMenuEnabled)) return;
    addToTrace(new TracerOp(w, null, Tracer.OPTIONSMENU_TRACE, null));
    highlightNode(w);
    suspensionCheck(null);
  }

  public void traceHook(Widget hook, Object[] hookVars, ZPseudoComp hookUI)
  {
    if (!myFrame.isShowing() || (!breakCheck(hook, Tracer.HOOK_TRACE) && !traceHookEnabled)) return;
    addToTrace(new TracerOp(hook, null, Tracer.HOOK_TRACE, null));
    highlightNode(hook);
    suspensionCheck(hookUI == null ? null : hookUI.getRelatedContext());
  }

  public void setAllTracing(boolean x)
  {
    traceExecPreItem.setSelected(x);
    traceExecPostItem.setSelected(x);
    traceUICreateItem.setSelected(x);
    traceUILayoutItem.setSelected(x);
    traceUIRenderItem.setSelected(x);
    traceUIPreDataItem.setSelected(x);
    traceUIPostDataItem.setSelected(x);
    traceUIPreCompItem.setSelected(x);
    traceUIPostCompItem.setSelected(x);
    traceUIPreCondItem.setSelected(x);
    traceUIPostCondItem.setSelected(x);
    traceMenuItem.setSelected(x);
    traceOptionsMenuItem.setSelected(x);
    traceHookItem.setSelected(x);
    traceListenerItem.setSelected(x);
    traceEventItem.setSelected(x);
    traceHookEnabled = x;
    traceListenerEnabled = x;
    traceEventEnabled = x;
    traceMenuEnabled = x;
    traceOptionsMenuEnabled = x;
    traceEvaluateMask = x ? Tracer.ALL_EVALUATION : 0;
    traceUIMask = x ? Tracer.ALL_UI : 0;
  }

  public void pauseExecution()
  {
    if (Sage.DBG) System.out.println("Pausing...");
    pauseItem.setEnabled(false);
    resumeItem.setEnabled(true);
    stepItem.setEnabled(true);
    synchronized (suspendLock)
    {
      suspendExecution = true;
      suspendThread = null;
      suspendLock.notifyAll();
    }
    conl.repaint();
  }

  public void resumeExecution()
  {
    if (Sage.DBG) System.out.println("Resuming...");
    pauseItem.setEnabled(true);
    resumeItem.setEnabled(false);
    stepItem.setEnabled(false);
    synchronized (suspendLock)
    {
      suspendExecution = false;
      if (awtSuspended)
      {
        kickEventQueue();
      }
      suspendLock.notifyAll();
    }
    conl.repaint();
  }

  public void stepExecution(int stepStyle)
  {
    if (Sage.DBG) System.out.println("Stepping...");
    synchronized (suspendLock)
    {
      stepExecution = true;
      if (awtSuspended)
      {
        kickEventQueue();
      }
      suspendLock.notifyAll();
    }
  }

  public void kickEventQueue()
  {
    java.awt.EventQueue.invokeLater(new Runnable()
    {
      public void run()
      {}
    });
  }

  private java.lang.reflect.Method pumper = null;
  private java.lang.reflect.Method addFilter = null;
  private java.lang.reflect.Method removeFilter = null;
  private java.lang.reflect.Constructor buildFilter = null;
  private java.lang.reflect.Field stopper = null;
  private int suspendCount = 0;
  private Object suspendCountLock = new Object();
  protected void suspensionCheck(Catbert.Context susContext)
  {
    if (!suspendExecution) return;
    if (suspendExecution && suspendThread != null && suspendThread != Thread.currentThread()) return;
    boolean firstSuspend = false;
    synchronized (suspendCountLock)
    {
      suspendedContexts.push(susContext);
      suspendCount++;
      if (suspendCount == 1)
        firstSuspend = true;
    }
    if (Sage.DBG) System.out.println("Suspending Execution...count=" + suspendCount);
    inSuspension = true;
    if (firstSuspend)
      conl.repaint();
    if (java.awt.EventQueue.isDispatchThread())
    {
      if (firstSuspend)
        uiMgr.setIgnoreAllEvents(true);
      try
      {
        Class evtThreadClass = Thread.currentThread().getClass();
        Thread evtThread = Thread.currentThread();
        boolean jre16 = System.getProperty("java.version").startsWith("1.6");
        if (pumper == null)
        {
          if (Sage.DBG) System.out.println("Finding the JVM AWT pumper...");
          if (jre16)
          {
            pumper = evtThreadClass.getDeclaredMethod(
                "pumpOneEventForFilters", new Class[] { Integer.TYPE });
            Class evtFilterClass = Class.forName("java.awt.EventFilter");
            addFilter = evtThreadClass.getDeclaredMethod(
                "addEventFilter", new Class[] { evtFilterClass });
            removeFilter = evtThreadClass.getDeclaredMethod(
                "removeEventFilter", new Class[] { evtFilterClass });
            Class hierEventFilter = Class.forName("java.awt.EventDispatchThread$HierarchyEventFilter");
            buildFilter = hierEventFilter.getConstructor(new Class[] { java.awt.Component.class });
            addFilter.setAccessible(true);
            removeFilter.setAccessible(true);
            buildFilter.setAccessible(true);
          }
          else
            pumper = evtThreadClass.getDeclaredMethod(
                "pumpOneEventForHierarchy", new Class[] { Integer.TYPE,
                    java.awt.Component.class });
          stopper = evtThreadClass.getDeclaredField("doDispatch");
          pumper.setAccessible(true);
          stopper.setAccessible(true);
        }
        //stopper.setBoolean(evtThread, true);
        Object evtFilter = null;
        if (jre16)
        {
          evtFilter = buildFilter.newInstance(new Object[] { myFrame });
          addFilter.invoke(evtThread, new Object[] { evtFilter });
        }
        while (stopper.getBoolean(evtThread))
        {
          synchronized (suspendLock)
          {
            awtSuspended = true;
            if (!suspendExecution) break;
            if (stepExecution)
            {
              stepExecution = false;
              break;
            }
          }
          if (evtThread.isInterrupted() ||
              (jre16 ?
                  !((Boolean) pumper.invoke(evtThread, new Object[] { new Integer(-1) })).booleanValue() :
                    !((Boolean) pumper.invoke(evtThread, new Object[] { new Integer(-1), myFrame} )).booleanValue()))
          {
            //stopper.setBoolean(evtThread, false);
          }
        }
        if (jre16)
          removeFilter.invoke(evtThread, new Object[] { evtFilter });
        awtSuspended = false;
      }
      catch (Exception e)
      {
        throw new InternalError("ERROR:"  + e);
      }
    }
    else
    {
      // We have to kill the AWT events coming into SageTV before we suspend here
      if (firstSuspend)
      {
        // NOTE: If we do this on the AWT thread then we could deadlock in the getLock call from the other thread
        // since it won't know to bail out
        //				try
        {
          //					java.awt.EventQueue.invokeAndWait(new Runnable()
          //					{
          //						public void run()
          {
            uiMgr.setIgnoreAllEvents(true);
          }
          //					});
        }
        //				catch (Exception e){}
      }
      /*try{
			java.awt.EventQueue.invokeAndWait(new Runnable()
			{
				public void run()
				{
					try
					{
						Class evtThreadClass = Thread.currentThread().getClass();
						Thread evtThread = Thread.currentThread();
						if (pumper == null)
						{
							pumper = evtThreadClass.getDeclaredMethod(
								"pumpOneEventForHierarchy", new Class[] { Integer.TYPE,
								java.awt.Component.class });
							stopper = evtThreadClass.getDeclaredField("doDispatch");
							pumper.setAccessible(true);
							stopper.setAccessible(true);
						}
						//stopper.setBoolean(evtThread, true);
						while (stopper.getBoolean(evtThread))
						{
							synchronized (suspendLock)
							{
								awtSuspended = true;
								if (!suspendExecution) break;
								if (stepExecution)
								{
									stepExecution = false;
									break;
								}
							}
							if (evtThread.isInterrupted() || !((Boolean) pumper.invoke(evtThread,
								new Object[] { new Integer(-1), myFrame} )).booleanValue())
							{
								//stopper.setBoolean(evtThread, false);
							}
						}
						awtSuspended = false;
					}
					catch (Exception e)
					{
						throw new InternalError("ERROR:"  + e);
					}
				}
			});}catch(Exception e){}*/
      synchronized (suspendLock)
      {
        while (suspendExecution)
        {
          if (stepExecution)
          {
            stepExecution = false;
            break;
          }
          try
          {
            suspendLock.wait();
          }catch (Exception e){}
        }
      }
    }
    boolean lastSuspend = false;
    synchronized (suspendCountLock)
    {
      suspendedContexts.pop();
      suspendCount--;
      lastSuspend = suspendCount == 0;
    }
    if (lastSuspend)
    {
      uiMgr.setIgnoreAllEvents(false);
      inSuspension = false;
      conl.repaint();
    }
    if (Sage.DBG) System.out.println("Execution has resumed. count=" + suspendCount);
  }

  private void addToTrace(TracerOp top)
  {
    if (numTraceData == traceData.length)
    {
      traceDataOffset++;
    }
    else
    {
      numTraceData++;
    }
    traceData[(traceDataOffset + numTraceData - 1) % traceData.length] = top;
    if (tracerTable != null)
    {
      //tracerTable.setListData(traceData);
      //tracerTable.setSelectedValue(top, true);
      tracerTableModel.fireAdded();
      //tracerTable.setSelectedIndex(numTraceData - 1);
      //			tracerTable.ensureIndexIsVisible(numTraceData - 1);
      tracerTable.scrollRectToVisible(tracerTable.getCellRect(numTraceData - 1,1,true));
    }
  }

  public sage.Catbert.Context getSuspendedContext()
  {
    synchronized (suspendCountLock)
    {
      return suspendedContexts.isEmpty() ? null : (Catbert.Context)suspendedContexts.peek();
    }
  }

  public OracleTree getTree()
  {
    return tree;
  }

  public void refreshUITree()
  {
    if (uiCompsTreeModel != null)
      uiCompsTreeModel.refreshTree();
    if (childStudio != null && childStudio.uiCompsTreeModel != null)
      childStudio.uiCompsTreeModel.refreshTree();
  }

  public UIManager getUIMgr()
  {
    return uiMgr;
  }

  // NARFLEX - 5/12/09 - Loading the file choosers can cause significant delays sometimes on Windows so lazily
  // load them
  private javax.swing.JFileChooser getstvfc()
  {
    if (stvfc == null)
    {
      stvfc = new javax.swing.JFileChooser(uiMgr.get(LAST_STV_BROWSE_DIR, System.getProperty("user.dir")));
      javax.swing.filechooser.FileFilter ff = new javax.swing.filechooser.FileFilter()
      {
        public String getDescription() { return "SageTV Application Definitions (*.stv,*.stvi,*.xml)"; }
        public boolean accept(java.io.File f)
        {
          return f.isDirectory() || (f.isFile() &&
              (f.getName().toLowerCase().endsWith(".stv") || f.getName().toLowerCase().endsWith(".stvi") ||
                  f.getName().toLowerCase().endsWith(".xml")));
        }
      };
      stvfc.addChoosableFileFilter(ff);
      stvfc.setFileFilter(ff);
    }
    return stvfc;
  }

  private javax.swing.JFileChooser getxmlfc()
  {
    if (xmlfc == null)
    {
      xmlfc = new javax.swing.JFileChooser(uiMgr.get(LAST_STV_BROWSE_DIR, System.getProperty("user.dir")));
      javax.swing.filechooser.FileFilter ffxml = new javax.swing.filechooser.FileFilter()
      {
        public String getDescription() { return "XML SageTV Application Definitions (*.xml,*.stvi)"; }
        public boolean accept(java.io.File f)
        {
          return f.isDirectory() || (f.isFile() && (f.getName().toLowerCase().endsWith(".xml") || f.getName().toLowerCase().endsWith(".stvi")));
        }
      };
      xmlfc.addChoosableFileFilter(ffxml);
      xmlfc.setFileFilter(ffxml);
    }
    return xmlfc;
  }

  private void performEmbeddedSTVOptimization()
  {
    if (Sage.DBG) System.out.println("Starting the STV optimization routines for embedded platforms...");
    constantSubstitutions();
    replaceFixedBoolResults();
    removeUnreachableBranches();
    removeUnusedConstantActionWidgets();
    consolidateConstantParentActions();
    removeUnreachableWidgetCode();
    convertDynamicToStaticProps();
    if (Sage.DBG) System.out.println("Done with the STV optimization routines for embedded platforms!");
    // Now print out some statistics as well
    refreshTree();
  }

  private void consolidateConstantParentActions()
  {
    if (Sage.DBG) System.out.println("Consolidating all constant parent actions that feed text/image Widgets");
    int numChanged = 0;
    Widget[] widgs = uiMgr.getModuleGroup().getWidgets();
    for (int i = 0; i < widgs.length; i++)
    {
      if (widgs[i].type() == Widget.IMAGE || widgs[i].type() == Widget.TEXT)
      {
        // We can only do this if it has an Action parent, and that parent has no Action parents itself (otherwise it would get replaced with that Action's result)
        // Don't allow escaped characters to be replaced either
        Widget[] parents = widgs[i].containers();
        if (parents.length == 1 && parents[0].type() == Widget.ACTION && parents[0].numContainers(Widget.ACTION) == 0 && Catbert.isConstantExpression(parents[0].getName()) &&
            parents[0].getName().startsWith("\"") && parents[0].getName().endsWith("\"") && parents[0].getName().indexOf("\\") == -1 &&
            parents[0].getName().length() > 2)
        {
          String name = parents[0].getName();
          name = name.substring(1, name.length() - 1);
          numChanged++;
          if (widgs[i].type() == Widget.IMAGE)
          {
            if (Sage.DBG) System.out.println("Setting constant action parent as the image file value for " + widgs[i] + " to be " + name);
            WidgetFidget.setProperty(widgs[i], Widget.FILE, name);
          }
          else
          {
            if (Sage.DBG) System.out.println("Setting constant action parent as the text value for " + widgs[i] + " to be " + name);
            WidgetFidget.setName(widgs[i], name);
          }
          Widget[] grandPaps = parents[0].containers();
          WidgetFidget.discontent(parents[0], widgs[i]);
          for (int j = 0; j < grandPaps.length; j++)
          {
            int childIndex = 0;
            Widget[] kids = grandPaps[j].contents();
            for (int k = 0; k < kids.length; k++)
              if (kids[k] == parents[0])
              {
                childIndex = k;
                break;
              }
            WidgetFidget.contain(grandPaps[j], widgs[i], childIndex);
          }
          if (parents[0].contents().length == 0)
            uiMgr.getModuleGroup().removeWidget(parents[0]);
        }
      }
    }
    if (Sage.DBG) System.out.println("DONE Consolidating all constant parent actions that feed text/image Widgets changes=" + numChanged);
  }

  private void convertDynamicToStaticProps()
  {
    if (Sage.DBG) System.out.println("Finding all dynamic properties that are constants and converting them to their static values");
    int numChanged = 0;
    Widget[] widgs = uiMgr.getModuleGroup().getWidgets();
    for (int i = 0; i < widgs.length; i++)
    {
      if (widgs[i].type() == Widget.ACTION || widgs[i].type() == Widget.CONDITIONAL || widgs[i].type() == Widget.BRANCH)
      {
        continue;
      }
      else if (widgs[i].type() != Widget.HOOK && widgs[i].type() != Widget.LISTENER && widgs[i].type() != Widget.ATTRIBUTE)
      {
        for(byte j = 0; j < Widget.MAX_PROP_NUM; j++)
        {
          if (widgs[i].hasProperty(j))
          {
            String prop = widgs[i].getProperty(j);
            if (prop.startsWith("="))
            {
              if (Catbert.isConstantExpression(prop.substring(1)))
              {
                String newValue = prop.substring(1);
                if (newValue.startsWith("\"") && newValue.endsWith("\""))
                  newValue = newValue.substring(1, newValue.length() - 1);
                if (Sage.DBG) System.out.println("Replacing constant dynamic property to " + newValue + " widg=" + widgs[i] + " property " + Widget.PROPS[j] + " from: " + prop);
                WidgetFidget.setProperty(widgs[i], j, newValue);
                numChanged++;
              }
            }
          }
        }
      }
    }
    if (Sage.DBG) System.out.println("DONE Finding all dynamic properties that are constants and converting them to their static values changes=" + numChanged);
  }

  private void replaceFixedBoolResults()
  {
    if (Sage.DBG) System.out.println("Checking for boolean expressions that have a fixed result due to ORing with true or ANDing with false");
    Widget[] widgs = uiMgr.getModuleGroup().getWidgets();
    int numChanged = 0;
    for (int i = 0; i < widgs.length; i++)
    {
      if (widgs[i].type() == Widget.ACTION || widgs[i].type() == Widget.CONDITIONAL || widgs[i].type() == Widget.BRANCH)
      {
        Boolean res = Catbert.isExpressionFixedBoolResult(widgs[i].getName());
        if (res != null)
        {
          if (Sage.DBG) System.out.println("Replacing boolean expression since it's result is FIXED to " + res + " widg=" + widgs[i]);
          WidgetFidget.setName(widgs[i], res.toString());
          numChanged++;
        }
      }
      else if (widgs[i].type() != Widget.HOOK && widgs[i].type() != Widget.LISTENER && widgs[i].type() != Widget.ATTRIBUTE)
      {
        for(byte j = 0; j < Widget.MAX_PROP_NUM; j++)
        {
          if (widgs[i].hasProperty(j))
          {
            String prop = widgs[i].getProperty(j);
            if (prop.startsWith("="))
            {
              Boolean res = Catbert.isExpressionFixedBoolResult(prop.substring(1));
              if (res != null)
              {
                if (Sage.DBG) System.out.println("Replacing boolean expression since it's result is FIXED to " + res + " widg=" + widgs[i] + " property " + Widget.PROPS[j] + " from: " + prop);
                WidgetFidget.setProperty(widgs[i], j, res.toString());
                numChanged++;
              }
            }
          }
        }
      }
      else if (widgs[i].type() == Widget.ATTRIBUTE)
      {
        if (widgs[i].hasProperty(Widget.VALUE))
        {
          String prop = widgs[i].getProperty(Widget.VALUE);
          Boolean res = Catbert.isExpressionFixedBoolResult(prop);
          if (res != null)
          {
            if (Sage.DBG) System.out.println("Replacing attribute value boolean expression since it's result is FIXED to " + res + " widg=" + widgs[i] + " from: " + prop);
            WidgetFidget.setProperty(widgs[i], Widget.VALUE, res.toString());
            numChanged++;
          }
        }
      }
    }
    if (Sage.DBG) System.out.println("Done replacing fixed boolean expressions num=" + numChanged);
  }

  private void removeUnreachableBranches()
  {
    // This goes through all of the Conditional/Branch expressions in the UI and unlinks any branches that cannot actually be evaluated
    // We'll also want to do one that removes the branches if there's only one left and it has a value of 'true'; and we can check that here as well
    // We can also remove all branches that have 'trueX' as their name as well as any Conditionals with that name as well, unless that conditional has an else branch
    // And if a conditional ends up with only an else branch, then we can just remove that branch and the conditional altogether (unless the conditional does something that has a side effect...tricky)
    if (Sage.DBG) System.out.println("Checking for fixed conditional branches and removing them...");
    Widget[] conds = uiMgr.getModuleGroup().getWidgets(Widget.CONDITIONAL);
    int numChanged = 0;
    for (int i = 0; i < conds.length; i++)
    {
      if ("trueX".equalsIgnoreCase(conds[i].getName()))
      {
        if (Sage.DBG) System.out.println("Removing Conditional because it's a 'trueX' one: " + conds[i]);
        uiMgr.getModuleGroup().removeWidget(conds[i]);
        numChanged++;
        continue;
      }
      boolean constantCond = Catbert.isConstantExpression(conds[i].getName());
      Widget[] branches = conds[i].contents(Widget.BRANCH);
      if (constantCond && branches.length == 0 && !"true".equals(conds[i].getName()))
      {
        if (Sage.DBG) System.out.println("Removing Condtional of constant 'false' that has no branches: " + conds[i]);
        uiMgr.getModuleGroup().removeWidget(conds[i]);
        numChanged++;
        continue;
      }
      boolean removeNonMatchingBranches = false;
      boolean allConstantBranches = branches.length > 0;
      for (int j = 0; j < branches.length; j++)
      {
        if ("trueX".equalsIgnoreCase(branches[j].getName()))
        {
          if (Sage.DBG) System.out.println("Removing Branch because it's a 'trueX' one: " + branches[j]);
          WidgetFidget.discontent(conds[i], branches[j]);
          // If there's also an 'else' branch then we can't just remove this one because then the else won't be true anymore; we have to remove the conditional, else and this branch altogether
          /*					if (branches.length == 2 && "else".equals(branches[j == 0 ? 1 : 0].getName()))
					{
						if (Sage.DBG) System.out.println("Removing else branch and conditional parent & reconnecting chain because the only other branch is trueX and it's going to be removed cond=" + conds[i] +
							" branch=" + branches[j == 0 ? 1 : 0]);
						removeWidgetAndReconnectChain(branches[j == 0 ? 1 : 0]);
						removeWidgetAndReconnectChain(conds[i]);
						allConstantBranches = false;
						numChanged+=2;
						branches = new Widget[0];
					}
					else */if (branches.length == 1)
					{
					  // Remove the conditional as well since it will never match the old single branch, leaving it would allow the true case to continue
					  if (Sage.DBG) System.out.println("Removing conditional parent because we removed the only child branch of it which was a trueX branch cond=" + conds[i]);
					  uiMgr.getModuleGroup().removeWidget(conds[i]);
					  allConstantBranches = false;
					  numChanged++;
					}
					else
					{
					  j = -1; // in case this ends up with a singular branch
					  branches = conds[i].contents(Widget.BRANCH);
					  allConstantBranches = branches.length > 0;
					}
					numChanged++;
					continue;
        }
        else if ("else".equals(branches[j].getName()) && branches.length == 1)
        {
          if (Sage.DBG) System.out.println("Removing else branch & conditional parent because it's the only child of it's conditional parent: " + conds[i] + " branch: " + branches[j]);
          removeWidgetAndReconnectChain(branches[j], new Widget[] { conds[i] });
          removeWidgetAndReconnectChain(conds[i]);
          j--;
          branches = conds[i].contents(Widget.BRANCH);
          numChanged+=2;
          allConstantBranches = false;
          continue;
        }
        else if ("true".equals(branches[j].getName()) && branches.length == 1)
        {
          if (Sage.DBG) System.out.println("Removing true branch because it's the only child of it's conditional parent: " + conds[i] + " branch: " + branches[j]);
          removeWidgetAndReconnectChain(branches[j], new Widget[] { conds[i] });
          j--;
          branches = conds[i].contents(Widget.BRANCH);
          numChanged++;
          allConstantBranches = false;
          continue;
        }
        else if (!removeNonMatchingBranches && constantCond && conds[i].getName().equals(branches[j].getName()))
        {
          if (Sage.DBG) System.out.println("Found Conditional/Branch with constant pair that's equal, remove all other branches that do not match cond=" + conds[i]);
          removeNonMatchingBranches = true;
        }
        else if (!Catbert.isConstantExpression(branches[j].getName()))
        {
          allConstantBranches = false;
        }
        else if (constantCond && !conds[i].getName().equals(branches[j].getName()))
        {
          if (Sage.DBG) System.out.println("Found constant Conditional/Branch pair that are NOT equal, disconnect the branch cond=" + conds[i] + " branch=" + branches[j]);
          WidgetFidget.discontent(conds[i], branches[j]);
          j = -1; // in case this ends up with a singular branch
          branches = conds[i].contents(Widget.BRANCH);
          allConstantBranches = branches.length > 0;
          numChanged++;
        }
      }
      // Since we may have removed branches, check this again
      if (constantCond && branches.length == 0 && !"true".equals(conds[i].getName()))
      {
        if (Sage.DBG) System.out.println("Removing Condtional of constant 'false' that has no branches: " + conds[i]);
        uiMgr.getModuleGroup().removeWidget(conds[i]);
        numChanged++;
        continue;
      }
      if (allConstantBranches && !removeNonMatchingBranches && constantCond)
      {
        if (Sage.DBG) System.out.println("Found Conditional/Branch set that will never match; just remove the Conditional altogether: " + conds[i]);
        uiMgr.getModuleGroup().removeWidget(conds[i]);
        numChanged++;
      }
      else if (removeNonMatchingBranches)
      {
        for (int j = 0; j < branches.length; j++)
        {
          if (!conds[i].getName().equals(branches[j].getName()))
          {
            if (Sage.DBG) System.out.println("Removing unreachable Branch from constant Conditional/Branch set cond=" + conds[i] + " branch=" + branches[j]);
            WidgetFidget.discontent(conds[i], branches[j]);
            numChanged++;
          }
        }
        // Now we also remove that Conditional & Branches that are left...BUT we can't do this if there's any UI children of the branch of conditional that would make
        // use of an Action result and we have an Action Widget as a parent of the Conditional
        branches = conds[i].contents(Widget.BRANCH);
        boolean skipThisRemove = false;
        if (conds[i].numContainers(Widget.ACTION) != 0)
        {
          for (int j = 0; j < branches.length; j++)
          {
            if (branches[j].contents(Widget.IMAGE).length > 0 || branches[j].contents(Widget.TEXT).length > 0 || branches[j].contents(Widget.TEXTINPUT).length > 0)
            {
              skipThisRemove = true;
              if (Sage.DBG) System.out.println("Skipping remove of conditional w/ constant branches due to inteference from parent action cond=" + conds[i]);
            }
          }
        }
        if (!skipThisRemove)
        {
          for (int j = 0; j < branches.length; j++)
          {
            if (Sage.DBG) System.out.println("Removing constant matching Branch from Conditional/Branch set and reconnecting children to parent cond=" + conds[i] + " branch=" + branches[j]);
            removeWidgetAndReconnectChain(branches[j], new Widget[] { conds[i] });
          }
          if (Sage.DBG) System.out.println("Removing constant Conditional from old matching set and reconnecting children to parent cond=" + conds[i]);
          removeWidgetAndReconnectChain(conds[i]);
        }
      }
    }
    if (Sage.DBG) System.out.println("Done doing conditional/branch analysis and made " + numChanged + " modifications.");
  }

  private String getGlobalAttributeValue(String name)
  {
    Widget globalThemeWidget = null;
    Widget[] widgs = uiMgr.getModuleGroup().getWidgets(Widget.THEME);
    for (int i = 0; i < widgs.length; i++)
      if ("Global".equals(widgs[i].getName()))
      {
        globalThemeWidget = widgs[i];
        break;
      }
    if (globalThemeWidget != null)
    {
      Widget[] attWidgets = globalThemeWidget.contents(Widget.ATTRIBUTE);
      for (int i = 0; i < attWidgets.length; i++)
      {
        if (name.equals(attWidgets[i].getName()))
        {
          try
          {
            Object obj = Catbert.evaluateExpression(attWidgets[i].getProperty(Widget.VALUE), new Catbert.Context(uiMgr), null, attWidgets[i]);
            if (obj != null)
              return obj.toString();
          }
          catch (Exception e)
          {
            if (Sage.DBG) System.out.println("ERROR Evaluating " + name + " expression of: " + e);
          }
        }
      }
    }
    return null;

  }

  private void constantSubstitutions()
  {
    // First check to see if the STV specifies a custom filename for the substitutions file
    String customSubsFile = getGlobalAttributeValue("STVOptSubsFilename");
    if (customSubsFile == null || customSubsFile.trim().length() == 0)
      customSubsFile = "STVOptSubs.properties";
    // This loads a file of external mappings that indicate subexpressions that can be replaced with a specified constant. We need to be careful about substring matching, so when
    // we search for these, we need to be sure that the character before or after what we match is non-alphanumeric to ensure it wont match substring vars.
    if (Sage.DBG) System.out.println("Performing substitutions of expressions to constant values in the STV from " + customSubsFile + "...");
    java.util.Properties subMap = new java.util.Properties();
    java.io.InputStream fis = null;
    try
    {
      // first check if the subs file is in the STVs directory, if not check the current working directory
      java.io.File stvSubs = new java.io.File(new java.io.File(uiMgr.getModuleGroup().defaultModule.description()).getParentFile(), customSubsFile);
      if (!stvSubs.isFile())
        stvSubs = new java.io.File(customSubsFile);
      fis = new java.io.BufferedInputStream(new java.io.FileInputStream(stvSubs));
      subMap.load(fis);
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR loading " + customSubsFile + " of:" + e);
      return;
    }
    finally
    {
      if (fis != null)
      {
        try{fis.close();}catch(Exception e){}
      }
    }

    String themeSubDir = getGlobalAttributeValue("STVOptSubsThemeSubdir");
    if (themeSubDir == null || themeSubDir.length() == 0)
      themeSubDir = "Themes/FiberTV/";

    // Create arrays of this information for faster access
    String[] srcSub = new String[subMap.size()];
    String[] dstSub = new String[subMap.size()];

    if (Sage.DBG) System.out.println("Loaded substitutions of:");
    java.util.Iterator walker = subMap.entrySet().iterator();
    int i = 0;
    int subCount = 0;
    while (walker.hasNext())
    {
      java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
      srcSub[i] = ent.getKey().toString();
      dstSub[i] = ent.getValue().toString();

      // See if this is theme property stuff we want to convert to the proper format
      if (srcSub[i].startsWith("ColorValue/"))
      {
        srcSub[i] = srcSub[i].substring(11);
        dstSub[i] = "\"" + dstSub[i] + "\"";
      }
      else if (srcSub[i].startsWith("ImageFile/"))
      {
        srcSub[i] = srcSub[i].substring(10);
        dstSub[i] = "\"" + themeSubDir + dstSub[i] + "\"";
      }
      else if (srcSub[i].startsWith("ScalingInset/"))
        srcSub[i] = "=" + srcSub[i].substring(13);
      else if (srcSub[i].startsWith("ImageSetPath/"))
      {
        srcSub[i] = srcSub[i].substring(13);
        dstSub[i] = "\"" + themeSubDir + "\"";
      }
      else if (srcSub[i].startsWith("NumberFloatTrans/"))
        srcSub[i] = srcSub[i].substring(17);
      else if (srcSub[i].startsWith("UIWidgetInset/"))
        srcSub[i] = "=" + srcSub[i].substring(14);
      else if (srcSub[i].startsWith("NumberIntAlpha/"))
        srcSub[i] = srcSub[i].substring(15);
      else if (srcSub[i].startsWith("String/gFont"))
      {
        srcSub[i] = srcSub[i].substring(7);
        dstSub[i] = "\"" + dstSub[i] + "\"";
      }
      else if (srcSub[i].startsWith("NumberInteger/"))
        srcSub[i] = srcSub[i].substring(14);

      if (Sage.DBG) System.out.println(srcSub[i] + " -> " + dstSub[i]);
      i++;
    }

    // Now go through all of the Action, Conditional and Branch widgets and do the replacements.
    // Also go through all the properties of the Widgets except for Hooks, Actions, Conditionals, Listeners and Branches
    Widget[] widgs = uiMgr.getModuleGroup().getWidgets();
    for (i = 0; i < widgs.length; i++)
    {
      if (widgs[i].type() == Widget.ACTION || widgs[i].type() == Widget.CONDITIONAL || widgs[i].type() == Widget.BRANCH)
      {
        String subResult = checkSub(widgs[i].getName(), srcSub, dstSub, false);
        if (subResult != null)
        {
          if (Sage.DBG) System.out.println("Doing substitution on Widget " + widgs[i] + " to new name of: " + subResult);
          WidgetFidget.setName(widgs[i], subResult);
          subCount++;
        }
      }
      else if (widgs[i].type() != Widget.HOOK && widgs[i].type() != Widget.LISTENER && widgs[i].type() != Widget.ATTRIBUTE)
      {
        for(byte j = 0; j < Widget.MAX_PROP_NUM; j++)
        {
          if (widgs[i].hasProperty(j))
          {
            String prop = widgs[i].getProperty(j);
            if (prop.startsWith("="))
            {
              String subResult = checkSub(prop, srcSub, dstSub, true);
              if (subResult != null)
              {
                if (Sage.DBG) System.out.println("Doing substitution on Widget " + widgs[i] + " of property value " + Widget.PROPS[j] + " from: " + prop + " to: " + subResult);
                WidgetFidget.setProperty(widgs[i], j, subResult);
                subCount++;
              }
            }
          }
        }
      }
      else if (widgs[i].type() == Widget.ATTRIBUTE)
      {
        if (widgs[i].hasProperty(Widget.VALUE))
        {
          String prop = widgs[i].getProperty(Widget.VALUE);
          String subResult = checkSub(prop, srcSub, dstSub, true);
          if (subResult != null)
          {
            if (Sage.DBG) System.out.println("Doing substitution on Widget " + widgs[i] + " of attribute value from: " + prop + " to: " + subResult);
            WidgetFidget.setProperty(widgs[i], Widget.VALUE, subResult);
            subCount++;
          }
        }
      }
    }
    if (Sage.DBG) System.out.println("Done doing widget substitutions count=" + subCount);
  }

  private String checkSub(String s, String[] srcSub, String[] dstSub, boolean propReplace)
  {
    if (s == null) return null;
    int slen = s.length();
    if (slen == 0)
      return null;
    boolean didReplacement = false;
    for (int i = 0; i < srcSub.length; i++)
    {
      int idx = s.indexOf(srcSub[i]);
      if (idx != -1)
      {
        // Check the char before to see if it's OK (unless the sub string starts with whitespace)
        // Also don't break inversion of booleans either
        if (idx > 0 && srcSub[i].charAt(0) != ' ')
        {
          char c = s.charAt(idx - 1);
          if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || ((srcSub[i].startsWith("false") || srcSub[i].startsWith("true")) && c == '!'))
          {
            if (Sage.DBG) System.out.println("SKIPPING replacement of " + s + " with " + srcSub[i] + " due to partial match only!");
            continue;
          }
        }
        int endCheck = idx + srcSub[i].length();
        if (endCheck < slen && srcSub[i].charAt(srcSub[i].length() - 1) != ' ')
        {
          char  c = s.charAt(endCheck);
          if (Character.isLetterOrDigit(c) || c == '_' || c == '.')
          {
            if (Sage.DBG) System.out.println("SKIPPING replacement of " + s + " with " + srcSub[i] + " due to partial match only!");
            continue;
          }
        }
        // Check to make sure what we're replacing isn't inside of double quotes; that's the case if there's an odd number of double quotes (unescaped) before the index
        int quoteCount = 0;
        int quoteIdx = s.indexOf('"');
        while (quoteIdx != -1 && quoteIdx < idx)
        {
          // Check for escaped
          if (quoteIdx == 0 || s.charAt(quoteIdx - 1) != '\\')
            quoteCount++;
          quoteIdx = s.indexOf('"', quoteIdx + 1);
        }

        if (quoteCount % 2 == 1)
        {
          if (Sage.DBG) System.out.println("SKIPPING replacement of " + s + " with " + srcSub[i] + " because what we're replacing is inside of quotes");
          continue;
        }

        // We passed the tests, do the replacement
        didReplacement = true;
        if (propReplace && srcSub[i].startsWith("=") && s.length() != srcSub[i].length())
        {
          // This is a property replace but we don't match the whole thing so we need to keep it as a dynamic property
          s = "=" + s.substring(0, idx) + dstSub[i] + s.substring(endCheck);
        }
        else
          s = s.substring(0, idx) + dstSub[i] + s.substring(endCheck);
        slen = s.length();
        i = -1; // there may be multiple occurrences of the same one; and earlier ones may become valid now
      }
    }
    return didReplacement ? s : null;
  }

  private void removeUnusedConstantActionWidgets()
  {
    // This should go through all the Action widgets in the STV and remove any of them that are constants...BUT it should not remove the following cases:
    // 1 - there is a child process widget that references the 'this' variable
    // 2 - "Focused" is used in the constant (but these probably won't show up since they'll be part of "REM Foo" + "Focused")
    // 3 - Action widgets that parent a Text, TextInput or Image Widget as that effects what they display
    // 4 - Action widgets that are children of an Image or Item Widget if they have more than one child themselves; otherwise all of those children will not get evaluated on actioning that element
    // 5 - If the removal of an Action would cause the parent to have multiple refs to the same child...this is allowed indirectly, but not directly
    if (Sage.DBG) System.out.println("Finding all constant Action widgets in the STV and removing them if there is nothing dependent upon their result...");
    Widget[] allActions = uiMgr.getModuleGroup().getWidgets(Widget.ACTION);
    int numRemoved = 0;
    for (int i = 0; i < allActions.length; i++)
    {
      if (Catbert.isConstantExpression(allActions[i].getName()))
      {
        // Check to make sure Focused is not in it; if so then it can act as a focus listener if this is part of the UI chain
        //if (allActions[i].getName().indexOf("Focused") != -1 && allActions[i].isInUIHierarchy())
        //	continue;
        // The "Focused" check shouldn't matter, because it would always be done with an operation to append the strings together if used, and even though other
        // cases may have an effect on focus listeners, they were likely never intended to so removing them should not matter...we can check this further later if it causes issues

        // It is important to check to make sure 'this' is not used in a child since that would definitely have an impact if we removed the current action widget
        Widget[] kids = allActions[i].contents();
        boolean childUsesResult = false;
        for (int j = 0; j < kids.length; j++)
        {
          if (kids[j].type() == Widget.ACTION || kids[j].type() == Widget.CONDITIONAL)
          {
            if (Catbert.isThisReferencedInExpression(kids[j].getName()))
            {
              childUsesResult = true;
              break;
            }
          }
          else if (kids[j].type() == Widget.TEXT || kids[j].type() == Widget.TEXTINPUT || kids[j].type() == Widget.IMAGE)
          {
            childUsesResult = true;
            break;
          }
        }
        if (childUsesResult)
          continue;
        if (kids.length > 1 && allActions[i].isInProcessChain() && (allActions[i].numContainers(Widget.IMAGE) > 0 || allActions[i].numContainers(Widget.ITEM) > 0))
        {
          int actionChildCount = 0;
          for (int j = 0; j < kids.length; j++)
          {
            if (kids[j].type() == Widget.ACTION || kids[j].type() == Widget.CONDITIONAL || kids[j].type() == Widget.OPTIONSMENU || kids[j].type() == Widget.MENU)
              actionChildCount++;
          }
          if (actionChildCount > 1)
            continue;
        }

        if (removeWidgetAndReconnectChain(allActions[i]))
          numRemoved++;
      }
    }
    if (Sage.DBG) System.out.println("Done removing all constant Action widgets, numRemoved=" + numRemoved);
  }

  private boolean removeWidgetAndReconnectChain(Widget w)
  {
    return removeWidgetAndReconnectChain(w, null);
  }
  private boolean removeWidgetAndReconnectChain(Widget w, Widget[] parents)
  {
    if (parents == null)
      parents = w.containers();
    Widget[] kids = w.contents();
    // Now check to make sure none of our children are already a child of any of our parents; otherwise one of the reference chains would be broken
    // since a Widget cannot parent the same child at multiple indices
    java.util.Set kidSet = new java.util.HashSet(java.util.Arrays.asList(kids));
    for (int j = 0; j < parents.length; j++)
    {
      Widget[] tempKids = parents[j].contents();
      for (int k = 0; k < tempKids.length; k++)
      {
        if (kidSet.contains(tempKids[k]))
        {
          if (Sage.DBG) System.out.println("CANNOT REMOVE WIDGET - Detected common reference from parent=" + parents[j] + " to " + tempKids[k] + " through " + w);
          return false;
        }
      }
    }

    if (Sage.DBG) System.out.println("Performing re-parenting and removing widget of: " + w);
    // To safely remove this, we need to take all of the children from this widget and put them underneath all the Widgets that are parenting us
    for (int j = 0; j < parents.length; j++)
    {
      // We also need to do this at the same index we currently occupy
      Widget[] tempKids = parents[j].contents();
      int index = -1;
      for (int k = 0; k < tempKids.length; k++)
        if (tempKids[k] == w)
        {
          index = k;
          break;
        }
      if (index < 0)
      {
        if (Sage.DBG) System.out.println("ERROR We did not find the child widget in the parents list of children!!!! parent=" + parents[j] + " child=" + w);
        index = 0;
      }
      WidgetFidget.discontent(parents[j], w);
      for (int k = 0; k < kids.length; k++)
      {
        WidgetFidget.contain(parents[j], kids[k], index + k);
      }
    }
    // Let the unreachable check clean this up instead for saftey
    //		uiMgr.getModuleGroup().removeWidget(w);
    return true;
  }

  private void removeUnreachableWidgetCode()
  {
    // Let's try this in the opposite direction; rather than trying to find all of the unreachable widgets, let's find all the reachable ones instead
    // and then what's left are the unreachable Widgets. We can start our search with these locations:
    // 1 - Global Theme
    // 2 - Unparented Hooks
    // 3 - Menu named either Main Menu, Server Connection Lost or Screen Saver

    if (Sage.DBG) System.out.println("Starting to analyze Widgets to find all of the unreachable ones...");
    // Then we follow all of the children from these sources all the way through.
    java.util.ArrayList startingRoots = new java.util.ArrayList();
    tv.sage.ModuleGroup mg = uiMgr.getModuleGroup();
    Widget[] allThemes = mg.getWidgets(Widget.THEME);
    for (int i = 0; i < allThemes.length; i++)
      if ("Global".equals(allThemes[i].getName()))
      {
        startingRoots.add(allThemes[i]);
        break;
      }
    Widget[] allHooks = mg.getWidgets(Widget.HOOK);
    for (int i = 0; i < allHooks.length; i++)
      if (allHooks[i].numContainers() == 0)
        startingRoots.add(allHooks[i]);
    Widget[] allMenus = mg.getWidgets(Widget.MENU);
    for (int i = 0; i < allMenus.length; i++)
    {
      String name = allMenus[i].getName();
      if ("Main Menu".equals(name) || "Server Connection Lost".equals(name) || "Screen Saver".equals(name))
        startingRoots.add(allMenus[i]);
    }

    java.util.Set reach = new java.util.HashSet();
    reach.addAll(startingRoots);
    while (!startingRoots.isEmpty())
    {
      Widget test = (Widget) startingRoots.remove(startingRoots.size() - 1);
      Widget[] kids = test.contents();
      for (int i = 0; i < kids.length; i++)
      {
        if (reach.add(kids[i]))
          startingRoots.add(kids[i]);
      }
    }

    if (Sage.DBG) System.out.println("About to remove all unreachable widgets from the STV. Current widget count=" + uiMgr.getModuleGroup().getWidgets().length + " reachableWidgetCount=" +
        reach.size());

    Widget[] allWidgs = mg.getWidgets();
    int numRemoved = 0;
    for (int i = 0; i < allWidgs.length; i++)
    {
      if (!reach.contains(allWidgs[i]))
      {
        if (Sage.DBG) System.out.println("Removing unreachable Widget: " + allWidgs[i]);
        mg.removeWidget(allWidgs[i]);
        numRemoved++;
      }
    }
    if (Sage.DBG) System.out.println("Done removing all unreachable Widgets! count=" + numRemoved);
  }

  private UIManager uiMgr;
  private Wizard wiz;
  private javax.swing.JFrame myFrame;
  private javax.swing.JFrame diffFrame;
  private javax.swing.JFrame breakpointsFrame;
  private javax.swing.JFrame tracerFrame;
  private javax.swing.JFrame uiCompsFrame;
  private javax.swing.JFrame conflictsFrame;
  private OracleTreeModel model;
  private OracleTree tree;
  private javax.swing.JMenuBar menuBar;
  private javax.swing.JFileChooser stvfc;
  private javax.swing.JFileChooser xmlfc;

  private javax.swing.JLabel conl;

  private javax.swing.JMenuItem newFileMenuItem;
  private javax.swing.JMenuItem loadFileMenuItem;
  private javax.swing.JMenuItem saveFileMenuItem;
  private javax.swing.JMenuItem saveAsMenuItem;
  private javax.swing.JMenuItem saveACopyAsMenuItem;
  private javax.swing.JMenuItem importFileMenuItem;
  private javax.swing.JMenuItem exportMenuItem;
  private javax.swing.JMenu recentFilesMenu;
  private javax.swing.JMenuItem closeMenuItem;

  private javax.swing.JMenuItem undoMenuItem;
  private javax.swing.JMenuItem cutMenuItem;
  private javax.swing.JMenuItem copyMenuItem;
  private javax.swing.JMenuItem pasteMenuItem;
  private javax.swing.JMenuItem pasteReferenceMenuItem;
  private javax.swing.JMenuItem deleteMenuItem;
  private javax.swing.JMenuItem selectAllMenuItem;
  private javax.swing.JMenuItem findAllMenuItem;

  private javax.swing.JMenuItem diffMenuItem;
  private javax.swing.JMenuItem diffUIDMenuItem;
  private javax.swing.JList diffList;
  private javax.swing.JButton genSTVIB;
  private javax.swing.JMenuItem exprEvalItem;
  private javax.swing.JMenuItem genTransItem;
  private javax.swing.JMenuItem consolidateMenusItem;
  private javax.swing.JCheckBoxMenuItem notifyOnErrorsItem;
  private javax.swing.JMenuItem launchAnotherFrameMenusItem;
  private javax.swing.JMenuItem refreshMenuItem;
  private javax.swing.JMenuItem uiPrefixMenuItem;
  private javax.swing.JCheckBoxMenuItem displaySymsItem;
  private javax.swing.JCheckBoxMenuItem displayAttValuesItem;
  private javax.swing.JCheckBoxMenuItem dynBoolPropItem;
  private javax.swing.JCheckBoxMenuItem syntaxAlertBoolPropItem;
  private javax.swing.JMenuItem optimizeItem;
  private javax.swing.JMenu versionControlMenu;
  private javax.swing.JMenuItem vcDiffWorkingMenuItem;
  private javax.swing.JMenuItem vcDiffCurrentMenuItem;
  private javax.swing.JMenuItem vcCheckinMenuItem;
  private javax.swing.JMenuItem vcUpdateTestMenuItem;
  private javax.swing.JMenuItem vcUpdateMenuItem;
  private javax.swing.JMenuItem vcRefreshMenuItem;

  private javax.swing.JCheckBoxMenuItem traceExecPreItem;
  private javax.swing.JCheckBoxMenuItem traceExecPostItem;
  private javax.swing.JCheckBoxMenuItem traceUICreateItem;
  private javax.swing.JCheckBoxMenuItem traceUILayoutItem;
  private javax.swing.JCheckBoxMenuItem traceUIRenderItem;
  private javax.swing.JCheckBoxMenuItem traceUIPreDataItem;
  private javax.swing.JCheckBoxMenuItem traceUIPostDataItem;
  private javax.swing.JCheckBoxMenuItem traceUIPreCompItem;
  private javax.swing.JCheckBoxMenuItem traceUIPostCompItem;
  private javax.swing.JCheckBoxMenuItem traceUIPreCondItem;
  private javax.swing.JCheckBoxMenuItem traceUIPostCondItem;
  private javax.swing.JCheckBoxMenuItem traceMenuItem;
  private javax.swing.JCheckBoxMenuItem traceOptionsMenuItem;
  private javax.swing.JCheckBoxMenuItem traceHookItem;
  private javax.swing.JCheckBoxMenuItem traceListenerItem;
  private javax.swing.JCheckBoxMenuItem traceEventItem;
  private javax.swing.JCheckBoxMenuItem traceScrollItem;
  private javax.swing.JMenuItem pauseItem;
  private javax.swing.JMenuItem resumeItem;
  private javax.swing.JMenuItem stepItem;
  private javax.swing.JMenuItem breakpointsItem;
  private javax.swing.JTable breaksTable;
  private BreaksTableModel breaksTableModel;
  private javax.swing.JMenuItem tracerItem;
  private javax.swing.JTable tracerTable;
  private TracerTableModel tracerTableModel;
  private TracerOp[] traceData;
  private int numTraceData;
  private int traceDataOffset;
  private javax.swing.JMenuItem uiCompsItem;
  private DebugUITree uiCompsTree;
  private DebugUITreeModel uiCompsTreeModel;

  private javax.swing.JTable conflictTable;
  private WidgetConflictTableModel conflictTableModel;
  private java.util.ArrayList widgetConflictDiffs;
  private javax.swing.JButton finalizeConflictsB;
  private sage.version.VersionControlState premergeVCState;
  private WidgetOp[] workingDiffs;

  private java.util.Stack widgetOperations;

  private java.util.Vector breakpoints;

  private String lastExpr;

  private AutoSaver autoSaver;

  private sage.version.VersionControl repoMaster;
  private String lastCheckinMessage;
  private boolean doingRepoCheck;

  // This is used for evaluating expressions while paused in the current context
  private java.util.Stack suspendedContexts = new java.util.Stack();

  private boolean suspendExecution;
  private Thread suspendThread;
  private boolean stepExecution;
  private boolean awtSuspended;
  private Object suspendLock = new Object();
  private boolean inSuspension;

  private boolean traceHookEnabled = false;
  private boolean traceListenerEnabled = false;
  private boolean traceEventEnabled = false;
  private boolean traceMenuEnabled = false;
  private boolean traceOptionsMenuEnabled = false;
  private int traceEvaluateMask = 0;
  private int traceUIMask = 0;

  private StudioFrame parentStudio;
  private StudioFrame childStudio;

  private boolean doingUIDDiff;
  private boolean threeWayUIDiff;
  private java.util.Vector diffRes;
  private static final int CONTAIN_OP = 1;
  private static final int UNCONTAIN_OP = 2;
  private static final int PROPERTY_OP = 3;
  private static final int CREATE_OP = 4;
  private static final int DESTROY_OP = 5;
  private static final int MOVE_OP = 6;
  private static final int RENAME_OP = 7;
  public static class WidgetOp
  {
    public WidgetOp(Widget inw1, Widget inw2)
    {
      opType = CONTAIN_OP;
      w1 = inw1;
      w2 = inw2;
    }
    public WidgetOp(Widget inw1, Widget inw2, int oldIndex)
    {
      opType = UNCONTAIN_OP;
      w1 = inw1;
      w2 = inw2;
      idx = oldIndex;
    }
    // This one is special and is used by the conflict resolver since we need a way to specify a Contain operation
    // without the target index being defined in the Widget model (the normal diff operation specified how to undo, not create).
    // For this, the 3rd Widget is the Widget to insert after, null if it should be first
    public WidgetOp(Widget inw1, Widget inw2, Widget afterWidg)
    {
      opType = CONTAIN_OP;
      w1 = inw1;
      w2 = inw2;
      w3 = afterWidg;
      relativeContain = true;
    }
    public WidgetOp(Widget inw1, byte inpn, String inpv)
    {
      opType = PROPERTY_OP;
      w1 = inw1;
      pn = inpn;
      pv = inpv;
    }
    public WidgetOp(Widget inw1, String inname)
    {
      opType = RENAME_OP;
      w1 = inw1;
      pv = inname;
    }
    public WidgetOp(boolean add, Widget inw1)
    {
      opType = add ? CREATE_OP : DESTROY_OP;
      w1 = inw1;
    }
    public String toString()
    {
      String prefix1 = "";
      String prefix2 = "";
      if (checkGroup != null && checkGroup.symbolMap.get(w1.symbol()) == null)
        prefix1 = "** ";
      if (checkGroup != null && w2 != null && checkGroup.symbolMap.get(w2.symbol()) == null)
        prefix2 = "%% ";
      if (opType == CREATE_OP)
      {
        return "Created " + Widget.TYPES[w1.type()] + " \"" + (w1.getUntranslatedName().length() == 0 ? "Untitled" : w1.getUntranslatedName())  + "\"";
      }
      else if (opType == DESTROY_OP)
      {
        return prefix1 + "Destroyed " + Widget.TYPES[w1.type()] + " \"" + (w1.getUntranslatedName().length() == 0 ? "Untitled" : w1.getUntranslatedName())  + "\"";
      }
      else if (opType == PROPERTY_OP)
      {
        return prefix1 + "Changed Property " + Widget.PROPS[pn] + " from \"" + pv + "\" to \"" + w1.getProperty(pn) + "\" for " +
            Widget.TYPES[w1.type()] + " \"" + (w1.getUntranslatedName().length() == 0 ? "Untitled" : w1.getUntranslatedName())  + "\"";
      }
      else if (opType == RENAME_OP)
      {
        return prefix1 + "Renamed \"" + pv + "\" to \"" + w1.getUntranslatedName() + "\" for " +
            Widget.TYPES[w1.type()] + " \"" + (w1.getUntranslatedName().length() == 0 ? "Untitled" : w1.getUntranslatedName())  + "\"";
      }
      else if (opType == CONTAIN_OP)
      {
        return prefix1 + prefix2 + "Added " + Widget.TYPES[w2.type()] + " \"" + (w2.getUntranslatedName().length() == 0 ? "Untitled" : w2.getUntranslatedName())  + "\" to " +
            Widget.TYPES[w1.type()] + " \"" + (w1.getUntranslatedName().length() == 0 ? "Untitled" : w1.getUntranslatedName())  + "\"";
      }
      else if (opType == UNCONTAIN_OP)
      {
        return prefix1 + prefix2 + "Removed " + Widget.TYPES[w2.type()] + " \"" + (w2.getUntranslatedName().length() == 0 ? "Untitled" : w2.getUntranslatedName())  + "\" from " +
            Widget.TYPES[w1.type()] + " \"" + (w1.getUntranslatedName().length() == 0 ? "Untitled" : w1.getUntranslatedName())  + "\"";
      }
      else if (opType == MOVE_OP)
      {
        return prefix1 + prefix2 + "Moved " + Widget.TYPES[w2.type()] + " \"" + (w2.getUntranslatedName().length() == 0 ? "Untitled" : w2.getUntranslatedName())  + "\". Was child #" +
            (idx + 1);
      }
      return "WidgetOp[type=" + opType + " w1=" + w1 + " w2= " + w2 + " pn=" + pn + " pv=" + pv + " idx=" + idx + ']';
    }
    public boolean applyOpToModuleGroup(tv.sage.ModuleGroup mg)
    {
      Widget x1 = (Widget) mg.symbolMap.get(w1.symbol());
      Widget x2 = null;
      if (w2 != null)
        x2 = (Widget) mg.symbolMap.get(w2.symbol());
      if (x1 == null && opType != CREATE_OP)
        return false;
      if (x2 == null && (opType == CONTAIN_OP || opType == UNCONTAIN_OP || opType == MOVE_OP))
        return false;
      int kidId;
      switch (opType)
      {
        case MOVE_OP:
          kidId = w1.getChildIndex(w2);
          if (kidId < 0)
          {
            if (Sage.DBG) System.out.println("ERROR-1 with widget patching...did not find child " + w2 + " under parent " + w1);
            WidgetFidget.contain(x1, x2);
          }
          else
            WidgetFidget.contain(x1, x2, kidId);
          break;
        case CONTAIN_OP:
          if (relativeContain)
          {
            // If they're related already, break the relation so we get the right relative index below for insertion
            if (x1.contains(x2))
              WidgetFidget.discontent(x1, x2);
            kidId = (w3 == null) ? -1 : w1.getChildIndex(w3);
            if (kidId < 0 && w3 != null)
            {
              if (Sage.DBG) System.out.println("ERROR-2 with widget patching...did not find child " + w3 + " under parent " + w1);
              WidgetFidget.contain(x1, x2);
            }
            else
            {
              WidgetFidget.contain(x1, x2, kidId + 1);
            }
          }
          else
            WidgetFidget.contain(x1, x2);
          break;
        case UNCONTAIN_OP:
          WidgetFidget.discontent(x1, x2);
          break;
        case PROPERTY_OP:
          WidgetFidget.setProperty(x1, pn, w1.getProperty(pn));
          break;
        case RENAME_OP:
          WidgetFidget.setName(x1, w1.getUntranslatedName());
          break;
        case CREATE_OP:
          if (!mg.symbolMap.containsKey(w1.symbol()))
          {
            Widget noob = mg.addWidget(w1.type(), w1.symbol());
            WidgetFidget.setName(noob, w1.getUntranslatedName());
            for (byte p = 0; p <= Widget.MAX_PROP_NUM; p++)
            {
              if (w1.hasProperty(p))
              {
                WidgetFidget.setProperty(noob, p, w1.getProperty(p));
              }
            }
          }
          break;
        case DESTROY_OP:
          mg.removeWidget(x1);
          break;
      }
      return true;
    }
    int opType;
    Widget w1; // parent widget, or the widget created/destroyed
    Widget w2; // child widget
    Widget w3; // insert after this widget
    byte pn; // property name
    String pv; // old property value
    int idx; // prior index of containment
    tv.sage.ModuleGroup checkGroup;
    boolean relativeContain; // true if w3 is the Widget we should insert after (null means first)
  }

  public static class TracerOp
  {
    public TracerOp(Widget w, String expr, int traceOp, Object rezVal)
    {
      this.expr = expr;
      this.w = w;
      this.op = traceOp;
      this.rezVal = rezVal;
    }
    public static boolean hasRez(int op)
    {
      return (op & TRACE_RESULT_MASK) != 0;
    }
    public static String getTraceOpName(int op)
    {
      switch (op)
      {
        case Tracer.CREATE_UI:
          return "CreateUI";
        case Tracer.EVENT_TRACE:
          return "Event";
        case Tracer.HOOK_TRACE:
          return "Hook";
        case Tracer.LAYOUT_UI:
          return "LayoutUI";
        case Tracer.LISTENER_TRACE:
          return "Listener";
        case Tracer.MENU_TRACE:
          return "Menu";
        case Tracer.OPTIONSMENU_TRACE:
          return "OptionsMenu";
        case Tracer.POST_CONDITIONAL_UI:
          return "PostConditionalUI";
        case Tracer.PRE_CONDITIONAL_UI:
          return "PreConditionalUI";
        case Tracer.POST_EVALUATE_COMPONENT_UI:
          return "PostComponentUI";
        case Tracer.PRE_EVALUATE_COMPONENT_UI:
          return "PreComponentUI";
        case Tracer.POST_EVALUATE_DATA_UI:
          return "PostDataUI";
        case Tracer.PRE_EVALUATE_DATA_UI:
          return "PreDataUI";
        case Tracer.POST_EVALUATION:
          return "PostEval";
        case Tracer.PRE_EVALUATION:
          return "PreEval";
        case Tracer.RENDER_UI:
          return "RenderUI";
        default:
          return "???";
      }
    }
    public String toString()
    {
      StringBuffer sb = new StringBuffer(/*"0x"*/);
      //			sb.append(Integer.toString(op, 16));
      //			sb.append(' ');
      sb.append(getTraceOpName(op));
      sb.append(' ');
      if (w != null)
        sb.append(w.getName());
      if (expr != null && (w == null || !expr.equals(w.getName())))
      {
        sb.append(' ');
        sb.append(expr);
      }
      if (hasRez(op))
      {
        sb.append("=>");
        if (rezVal == null)
          sb.append("null");
        else
          sb.append(rezVal.toString());
      }
      return sb.toString();
    }
    public Widget w;
    public int op;
    public Object rezVal;
    public String expr;
  }

  private static String[] TRACER_COL_NAMES = { "Op", "Type", "Name", "Expr", "Result" };
  public class TracerTableModel extends javax.swing.table.AbstractTableModel
  {
    public void fireAdded()
    {
      if (numTraceData == traceData.length)
      {
        fireTableRowsDeleted(0, 0);
      }
      fireTableRowsInserted(numTraceData - 1, numTraceData - 1);
    }
    public int getRowCount()
    {
      return numTraceData;
    }
    public int getColumnCount()
    {
      return 5;
    }
    public String getColumnName(int column)
    {
      return TRACER_COL_NAMES[column];
    }
    public void clear()
    {
      traceDataOffset = 0;
      int oldSize = numTraceData;
      numTraceData = 0;
      java.util.Arrays.fill(traceData, null);
      fireTableRowsDeleted(0, oldSize);
    }

    public Object getValueAt(int rowIndex, int columnIndex)
    {
      TracerOp top = getRowValue(rowIndex);
      if (top == null) return null;
      switch (columnIndex)
      {
        case 0:
          return TracerOp.getTraceOpName(top.op);
        case 1:
          return (top.w == null ? null : uiMgr.getIcon(Widget.TYPES[top.w.type()]));
        case 3:
          return top.expr;
        case 4:
          return top.rezVal;
        case 2:
        default:
          return (top.w == null ? null : top.w.getName());
      }
    }

    public Class getColumnClass(int columnIndex)
    {
      if (columnIndex == 1)
      {
        return javax.swing.ImageIcon.class;
      }
      else
        return String.class;
    }

    public TracerOp getRowValue(int rowIndex)
    {
      if (rowIndex < 0 || rowIndex >= numTraceData) return null;
      return traceData[(traceDataOffset + rowIndex) % traceData.length];
    }
  }

  private static String[] BREAKS_COL_NAMES = { "Enabled", "Type", "Name" };
  public class BreaksTableModel extends javax.swing.table.AbstractTableModel
  {
    public int getRowCount()
    {
      return breakpoints.size();
    }
    public int getColumnCount()
    {
      return 3;
    }
    public String getColumnName(int column)
    {
      return BREAKS_COL_NAMES[column];
    }

    public Object getValueAt(int rowIndex, int columnIndex)
    {
      Breakpoint bp = getRowValue(rowIndex);
      if (bp == null) return null;
      switch (columnIndex)
      {
        case 0:
          return Boolean.valueOf(bp.isEnabled());
        case 1:
          return uiMgr.getIcon(Widget.TYPES[bp.getWidget().type()]);
        case 2:
        default:
          return bp.getWidget().getName();
      }
    }

    public Class getColumnClass(int columnIndex)
    {
      if (columnIndex == 1)
        return javax.swing.ImageIcon.class;
      else if (columnIndex == 0)
        return Boolean.class;
      else
        return String.class;
    }

    public Breakpoint getRowValue(int rowIndex)
    {
      if (rowIndex < 0 || rowIndex >= breakpoints.size()) return null;
      return (Breakpoint) breakpoints.get(rowIndex);
    }

    public boolean isCellEditable(int rowIndex, int columnIndex)
    {
      // Only Enabled/disabled is editable
      return (columnIndex == 0);
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex)
    {
      if (columnIndex == 0 && aValue instanceof Boolean)
      {
        Breakpoint bp = getRowValue(rowIndex);
        if (bp != null)
          bp.setEnabled(((Boolean) aValue).booleanValue());
        tree.repaint();
      }
    }
  }

  private static String[] CONFLICT_COL_NAMES = { "Resolved", "Severity", "Type", "Widget", "Type2", "Widget2", "Repository Diff", "Working Diff" };
  public class WidgetConflictTableModel extends javax.swing.table.AbstractTableModel
  {
    public int getRowCount()
    {
      return widgetConflictDiffs.size();
    }
    public int getColumnCount()
    {
      return 8;
    }
    public String getColumnName(int column)
    {
      return CONFLICT_COL_NAMES[column];
    }

    public Object getValueAt(int rowIndex, int columnIndex)
    {
      WidgetConflict wc = getRowValue(rowIndex);
      if (wc == null) return null;
      switch (columnIndex)
      {
        case 0:
          return Boolean.valueOf(wc.resolved);
        case 1:
          return wc.hard ? "Major" : "Minor";
        case 2:
          return uiMgr.getIcon(Widget.TYPES[wc.repoOp.w1.type()]);
        case 3:
          return "[" + wc.repoOp.w1.symbol() + "] " + wc.repoOp.w1.getName();
        case 4:
          if (wc.repoOp.w2 == null)
            return null;
          else
            return uiMgr.getIcon(Widget.TYPES[wc.repoOp.w2.type()]);
        case 5:
          if (wc.repoOp.w2 == null)
            return "";
          else
            return "[" + wc.repoOp.w2.symbol() + "] " + wc.repoOp.w2.getName();
        case 6:
          return wc.repoOp.toString();
        case 7:
          return wc.workingOp.toString();
        default:
          return null;
      }
    }

    public Class getColumnClass(int columnIndex)
    {
      if (columnIndex == 2 || columnIndex == 4)
        return javax.swing.ImageIcon.class;
      if (columnIndex == 0)
        return Boolean.class;
      else
        return String.class;
    }

    public WidgetConflict getRowValue(int rowIndex)
    {
      if (rowIndex < 0 || rowIndex >= widgetConflictDiffs.size()) return null;
      return (WidgetConflict) widgetConflictDiffs.get(rowIndex);
    }

    public boolean isCellEditable(int rowIndex, int columnIndex)
    {
      // Only resolved state is editable
      if (columnIndex == 0)
      {
        WidgetConflict wc = getRowValue(rowIndex);
        return (wc != null && (!wc.hard || wc.resolutionResult != null));
      }
      else
        return false;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex)
    {
      if (columnIndex == 0 && aValue instanceof Boolean)
      {
        WidgetConflict wc = getRowValue(rowIndex);
        if (wc != null)
          wc.resolved = ((Boolean) aValue).booleanValue();
        if (finalizeConflictsB.isVisible())
        {
          // Check if all are resolved now
          for (int i = 0; i < widgetConflictDiffs.size(); i++)
          {
            if (!((WidgetConflict) widgetConflictDiffs.get(i)).resolved)
            {
              finalizeConflictsB.setEnabled(false);
              return;
            }
          }
          finalizeConflictsB.setEnabled(true);
        }
      }
    }
  }

  private class AutoSaver implements Runnable
  {
    public void run()
    {
      lastAutosaveCheckTime = Sage.time();
      long autosaveInterval = Sage.getLong("studio/autosave_interval", 180000);
      if (autosaveInterval <= 0)
        return;
      autosaveInterval = Math.max(autosaveInterval, 5000);
      while (alive)
      {
        long waitTime = (lastAutosaveCheckTime + autosaveInterval) - Sage.time();
        if (waitTime > 100)
        {
          try
          {
            synchronized (this)
            {
              wait(waitTime);
            }
          }catch (InterruptedException e){}
          continue;
        }
        lastAutosaveCheckTime = Sage.time();
        myMod = uiMgr.getModuleGroup().defaultModule;
        String fname = myMod.description();
        java.io.File f = new java.io.File(fname);
        if (autosaveFile == null || (autosavefname != fname && (autosavefname == null || !autosavefname.equals(fname))))
        {
          autosavefname = fname;
          autosaveFile = new java.io.File(autosavefname + ".bak");
        }
        if (uiMgr.getModuleGroup().lastModified() > f.lastModified())
        {
          // The data has been modified since it was last saved
          if (!autosaveFile.exists() || uiMgr.getModuleGroup().lastModified() > autosaveFile.lastModified())
          {
            // The autosave file does not exist, or the data has been modified since the last time we auto-saved
            backupNow();
          }
        }
      }
    }
    private void backupNow()
    {
      if (uiMgr.getModuleGroup().defaultModule != myMod)
        return;
      try
      {
        myMod.saveXML(autosaveFile, null);
      }
      catch (tv.sage.SageException e)
      {
        System.out.println("There was an error auto-saving the file: " + e);
      }
    }
    public void kill()
    {
      alive = false;
      synchronized (this)
      {
        notifyAll();
      }
    }
    private boolean alive = true;
    private String autosavefname;
    private java.io.File autosaveFile;
    private long lastAutosaveCheckTime;
    private tv.sage.mod.Module myMod;
  }

  private class WidgetConflict
  {
    public WidgetConflict(WidgetOp repoOp, WidgetOp workingOp, boolean inHard)
    {
      this.repoOp = repoOp;
      this.workingOp = workingOp;
      hard = inHard;
    }

    public boolean resolved;
    public WidgetOp repoOp;
    public WidgetOp workingOp;
    public boolean hard;
    // This is what is displayed in the popup for options to pick from; it's keyed on the String
    // to use in the UI and the value is an array of WidgetOp[] which indicate the ops to use for this choice
    public java.util.Map resolutionOptions;
    // The key in the resolutionOptions map that was chosen as the result
    public String resolutionResult;
  }
}
