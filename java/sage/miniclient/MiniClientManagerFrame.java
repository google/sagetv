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
package sage.miniclient;

import java.awt.event.ItemEvent;

/**
 *
 * @author  Narflex
 */
public class MiniClientManagerFrame extends javax.swing.JFrame implements java.awt.event.ActionListener, javax.swing.event.ListSelectionListener
{
  /** Creates a new instance of MiniClientManagerFrame */
  public MiniClientManagerFrame()
  {
    super("SageTV Placeshifter Servers Manager");

    currServerList = new java.util.Vector();

    frameIcon = java.awt.Toolkit.getDefaultToolkit().createImage(getClass().getClassLoader().getResource("images/SageIcon32.png"));
    GFXCMD2.ensureImageIsLoaded(frameIcon);
    setIconImage(frameIcon);
    addWindowListener(new java.awt.event.WindowAdapter()
    {
      public void windowClosing(java.awt.event.WindowEvent evt)
      {
        MiniClient.myProperties.setProperty("mgr_window_x", Integer.toString(getX()));
        MiniClient.myProperties.setProperty("mgr_window_y", Integer.toString(getY()));
        MiniClient.safeExit(0);
      }
    });

    java.awt.Container mainPanel = getContentPane();
    java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
    mainPanel.setLayout(new java.awt.GridBagLayout());

    javax.swing.JPanel topPanel = new javax.swing.JPanel();
    topPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));

    java.awt.Image logoImage = java.awt.Toolkit.getDefaultToolkit().createImage(getClass().getClassLoader().getResource("images/SageLogo140.png"));
    GFXCMD2.ensureImageIsLoaded(logoImage);
    javax.swing.JLabel logo = new javax.swing.JLabel(new javax.swing.ImageIcon(logoImage));
    topPanel.add(logo);
    logoImage = java.awt.Toolkit.getDefaultToolkit().createImage(getClass().getClassLoader().getResource("images/Placeshifter16pt.png"));
    GFXCMD2.ensureImageIsLoaded(logoImage);
    logo = new javax.swing.JLabel(new javax.swing.ImageIcon(logoImage));
    topPanel.add(logo);
    gbc.gridx = gbc.gridy = 0;
    gbc.weightx = gbc.weighty = 0;
    gbc.fill = gbc.NONE;
    gbc.gridwidth = 5;
    gbc.gridheight = 1;
    gbc.insets = new java.awt.Insets(5, 5, 5, 5);
    mainPanel.add(topPanel, gbc);

    clientList = new javax.swing.JList();
    clientList.setVisibleRowCount(4);
    clientList.setCellRenderer(new ServerCellRenderer());
    javax.swing.JScrollPane listScroller = new javax.swing.JScrollPane(clientList);
    listScroller.setBorder(javax.swing.BorderFactory.createLoweredBevelBorder());
    gbc.weightx = gbc.weighty = 1.0;
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridheight = 10;
    gbc.gridwidth = 4;
    gbc.insets = new java.awt.Insets(5, 5, 5, 5);
    gbc.fill = gbc.BOTH;
    mainPanel.add(listScroller, gbc);
    clientList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    java.awt.event.MouseListener mouseListener = new java.awt.event.MouseAdapter() {
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (e.getClickCount() == 2) {
          int index = clientList.locationToIndex(e.getPoint());
          MgrServerInfo msi = (MgrServerInfo)currServerList.get(index);
          connectToServer(msi);
        }
      }
    };
    clientList.addMouseListener(mouseListener);
    clientList.addListSelectionListener(this);
    clientList.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
    put(javax.swing.KeyStroke.getKeyStroke(
        java.awt.event.KeyEvent.VK_ENTER, 0), "OK");
    clientList.getActionMap().put("OK",
        new javax.swing.AbstractAction()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        connectClientButton.doClick();
      }
    });

    addClientButton = new javax.swing.JButton("Add");
    addClientButton.addActionListener(this);
    editClientButton = new javax.swing.JButton("Edit");
    editClientButton.addActionListener(this);
    removeClientButton = new javax.swing.JButton("Remove");
    removeClientButton.addActionListener(this);
    connectClientButton = new javax.swing.JButton("Connect");
    connectClientButton.addActionListener(this);
    exitButton = new javax.swing.JButton("Exit");
    exitButton.addActionListener(this);
    settingsButton = new javax.swing.JButton("Settings");
    settingsButton.addActionListener(this);

    gbc.weightx = gbc.weighty = 0;
    gbc.gridx = 4;
    gbc.gridwidth = 1;
    gbc.gridy = 1;
    gbc.gridheight = 1;
    gbc.insets = new java.awt.Insets(5, 5, 5, 5);
    gbc.ipady = 5;
    gbc.fill = gbc.HORIZONTAL;
    mainPanel.add(connectClientButton, gbc);
    gbc.gridy++;
    mainPanel.add(addClientButton, gbc);
    gbc.gridy++;
    mainPanel.add(editClientButton, gbc);
    gbc.gridy++;
    mainPanel.add(removeClientButton, gbc);

    editClientButton.setEnabled(false);
    removeClientButton.setEnabled(false);
    connectClientButton.setEnabled(false);

    gbc.gridx = 0;
    gbc.gridy = 11;
    gbc.fill = gbc.NONE;
    mainPanel.add(settingsButton, gbc);

    gbc.gridx = 4;
    mainPanel.add(exitButton, gbc);

    // Add any additional programs that are configured to be launched in the UI
    java.util.Properties externPgms = new java.util.Properties();
    java.io.File externFile = new java.io.File("Programs.properties");
    boolean addedSeparator = false;
    if (externFile.isFile())
    {
      java.io.InputStream is = null;
      try
      {
        is = new java.io.BufferedInputStream(new java.io.FileInputStream(externFile));
        externPgms.load(is);
      }
      catch (Exception e)
      {
        System.out.println("Error loading Programs.properties of:" + e);
      }
      finally
      {
        if (is != null)
        {
          try
          {
            is.close();
          }
          catch (Exception e2)
          {}
        }
      }
      java.util.Iterator walker = externPgms.entrySet().iterator();
      while (walker.hasNext())
      {
        java.util.Map.Entry currEnt = (java.util.Map.Entry) walker.next();
        String currName = currEnt.getKey().toString();
        if (currName.length() > 0)
        {
          // The value is the button name then a semicolon and then a path to an image file
          String currVal = currEnt.getValue().toString();
          java.util.StringTokenizer toker = new java.util.StringTokenizer(currVal, ";");
          String buttName, imageName, execCmd;
          if (toker.countTokens() == 1)
          {
            // Just the executable command
            execCmd = toker.nextToken();
            buttName = "Launch";
            imageName = null;
          }
          else if (toker.countTokens() == 2)
          {
            // Image name and the executable command
            execCmd = toker.nextToken();
            buttName = "Launch";
            imageName = toker.nextToken();
          }
          else
          {
            // All 3
            execCmd = toker.nextToken();
            imageName = toker.nextToken();
            buttName = toker.nextToken();
          }
          javax.swing.JLabel customLabel = null;
          if (imageName != null && imageName.length() > 0)
          {
            java.awt.Image customImage = null;
            try
            {
              customImage = java.awt.Toolkit.getDefaultToolkit().createImage(imageName);
              GFXCMD2.ensureImageIsLoaded(customImage);
              customLabel = new javax.swing.JLabel(new javax.swing.ImageIcon(customImage));
            }
            catch (Exception e)
            {
              System.out.println("ERROR loading custom image of:" + e);
              customLabel = new javax.swing.JLabel(currName);
            }
          }
          else
            customLabel = new javax.swing.JLabel(currName);
          javax.swing.JButton launchButton = new javax.swing.JButton(buttName);
          final String myExecCmd = execCmd;
          launchButton.addActionListener(new java.awt.event.ActionListener()
          {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
              java.util.StringTokenizer tokie = new java.util.StringTokenizer(myExecCmd, "|");
              // Break it up by '|' into the arguments
              String[] cmdArgs = new String[tokie.countTokens()];
              int i = 0;
              while (tokie.hasMoreTokens()) cmdArgs[i++] = tokie.nextToken();
              try
              {
                Runtime.getRuntime().exec(cmdArgs);
              }
              catch (Exception e)
              {
                System.out.println("ERROR Launching Process:" + e);
              }
            }
          });
          gbc.gridx = 0;
          gbc.gridy++;
          gbc.fill = gbc.HORIZONTAL;
          gbc.weighty = 0;
          gbc.weightx = 0;
          gbc.gridwidth = 5;
          //					if (!addedSeparator)
          {
            addedSeparator = true;
            mainPanel.add(new javax.swing.JSeparator(javax.swing.JSeparator.HORIZONTAL), gbc);
            gbc.gridy++;
          }
          gbc.fill = gbc.BOTH;
          gbc.gridwidth = 4;
          mainPanel.add(customLabel, gbc);
          gbc.gridx = 4;
          gbc.gridwidth = 1;
          mainPanel.add(launchButton, gbc);
        }
      }
    }

    // Load the server info that's in the properties file
    String serverNameList = MiniClient.myProperties.getProperty("server_names", "");
    java.util.StringTokenizer toker = new java.util.StringTokenizer(serverNameList, ";");
    while (toker.hasMoreTokens())
    {
      try
      {
        MgrServerInfo theServer = new MgrServerInfo(toker.nextToken());
        currServerList.add(theServer);
      }
      catch (IllegalArgumentException e){}
    }

    sortServerList();
    clientList.setListData((java.util.Vector)currServerList.clone());
    pack();

    Thread serverListRefresher = new Thread("ServerListRefresher")
    {
      public void run()
      {
        refreshServerList();
        while (true)
        {
          try{Thread.sleep(5000);}catch(Exception e){}
          if (isShowing())
          {
            refreshServerList();
          }
        }
      }
    };
    serverListRefresher.setDaemon(true);
    serverListRefresher.start();

    int frameX = 100;
    int frameY = 100;
    try
    {
      frameX = Integer.parseInt(MiniClient.myProperties.getProperty("mgr_window_x", "100"));
      frameY = Integer.parseInt(MiniClient.myProperties.getProperty("mgr_window_y", "100"));
    }
    catch (NumberFormatException e){}
    java.awt.Point newPos = new java.awt.Point(frameX, frameY);
    boolean foundScreen = sage.UIUtils.isPointOnAScreen(newPos);
    if (!foundScreen)
    {
      newPos.x = 150;
      newPos.y = 150;
    }
    setLocation(newPos);

    // Just disable showing this dialog automatically since it slows down showing the UI and quite often the discovery takes longer than the timeout
    // and people get an unnecessary AddServer dialog popping up.
    /*
		// Sleep for a tad so the server discovery can do some quick finds if possible to avoid popping up the add server dialog
		int triesleft = 6;
		while (currServerList.isEmpty() && triesleft-- > 0)
		{
			try{Thread.sleep(50);}catch(Exception e){}
		}

		if (currServerList.isEmpty())
		{
			java.awt.EventQueue.invokeLater(new Runnable()
			{
				public void run()
				{
					addClientButton.doClick();
				}
			});
		}*/
  }

  public void actionPerformed(java.awt.event.ActionEvent e)
  {
    Object src = e.getSource();
    if (src == exitButton)
      MiniClient.safeExit(0);
    else if (src == connectClientButton)
    {
      MgrServerInfo selObj = (MgrServerInfo) clientList.getSelectedValue();
      if (selObj != null)
        connectToServer(selObj);
    }
    else if (src == removeClientButton)
    {
      MgrServerInfo selObj = (MgrServerInfo) clientList.getSelectedValue();
      if (selObj != null && selObj.serverType != LOCAL_SERVER)
      {
        if (javax.swing.JOptionPane.showConfirmDialog(this, "Are you sure you want to remove the entry for \"" + selObj.serverName + "\"",
            "Confirm Remove", javax.swing.JOptionPane.YES_NO_OPTION) == javax.swing.JOptionPane.YES_OPTION)
        {
          currServerList.remove(selObj);
          clientList.setListData((java.util.Vector)currServerList.clone());
          updateServerListProperties();
          MiniClient.saveConfig();
        }
      }
    }
    else if (src == addClientButton)
    {
      if (myAddDialog == null)
        myAddDialog = new AddServerDialog(this);
      MgrServerInfo newServer = myAddDialog.getNewServerInfo(currServerList);
      if (newServer != null)
      {
        currServerList.add(newServer);
        sortServerList();
        clientList.setListData((java.util.Vector)currServerList.clone());
        updateServerListProperties();
        MiniClient.saveConfig();
      }
    }
    else if (src == editClientButton)
    {
      if (myAddDialog == null)
        myAddDialog = new AddServerDialog(this);
      MgrServerInfo selObj = (MgrServerInfo) clientList.getSelectedValue();
      if (selObj != null && selObj.serverType != LOCAL_SERVER)
      {
        selObj = myAddDialog.editServerInfo(currServerList, selObj);
        if (selObj != null)
        {
          sortServerList();
          clientList.setListData((java.util.Vector)currServerList.clone());
          updateServerListProperties();
          MiniClient.saveConfig();
        }
      }
    }
    else if (src == settingsButton)
      doSetup();
  }

  /**
   * NOTE: Server names MUST be unique and they may NOT contain a semicolon character or a forward slash
   */

  private void updateServerListProperties()
  {
    String serverNameList = "";
    for (int i = 0; i < currServerList.size(); i++)
    {
      MgrServerInfo msi = (MgrServerInfo) currServerList.get(i);
      if (msi.serverType != LOCAL_SERVER)
      {
        serverNameList += msi.serverName + ";";
        MiniClient.myProperties.setProperty("servers/" + msi.serverName + "/type", Integer.toString(msi.serverType));
        if (msi.serverDirectAddress != null && msi.serverDirectAddress.length() > 0)
          MiniClient.myProperties.setProperty("servers/" + msi.serverName + "/address", msi.serverDirectAddress);
        if (msi.serverLookupID != null && msi.serverLookupID.length() > 0)
          MiniClient.myProperties.setProperty("servers/" + msi.serverName + "/locator_id", msi.serverLookupID);
        MiniClient.myProperties.setProperty("servers/" + msi.serverName + "/last_connect_time", Long.toString(msi.lastConnectTime));
      }
    }
    MiniClient.myProperties.setProperty("server_names", serverNameList);
  }

  public boolean isConnected()
  {
    return myConn != null && myConn.isConnected();
  }

  public void killConnections()
  {
    if (myConn != null)
      myConn.close();
  }

  private void connectToServer(MgrServerInfo msi)
  {
    synchronized (this)
    {
      if (connecting || isConnected()) return;
      connecting = true;
    }
    // See if the user is trying to connect to a local server through an external IP or locator ID. If this is the case
    // then use the local server instead. There's been lots of issues with people trying to connect the WAN address from their LAN
    // and this optimizes it to use the LAN address instead.
    if (msi.serverType != LOCAL_SERVER && msi.serverLookupID != null && msi.serverLookupID.length() > 0)
    {
      for (int i = 0; i < currServerList.size(); i++)
      {
        MgrServerInfo currMsi = (MgrServerInfo) currServerList.get(i);
        if (currMsi.serverType == LOCAL_SERVER && msi.serverLookupID.equalsIgnoreCase(currMsi.serverLookupID))
        {
          msi = currMsi;
          System.out.println("Swapping out WAN server for LAN server to optimize connection");
          break;
        }
      }
    }
    try
    {
      String addr;
      if (msi.serverType == LOCAL_SERVER)
      {
        addr = msi.serverName;
        if (msi.port != 0)
          addr = addr + ":" + msi.port;
      }
      else if (msi.serverType == LOCATABLE_SERVER)
      {
        try
        {
          msi.lookupIP = sage.locator.LocatorLookupClient.lookupIPForGuid(msi.serverLookupID);
        }
        catch (java.net.NoRouteToHostException e3)
        {
          sage.MySwingUtils.showWrappedMessageDialog(MiniClientConnection.CONNECT_FAILURE_GENERAL_INTERNET,
              "Connection Error", javax.swing.JOptionPane.ERROR_MESSAGE);
          return;
        }
        catch (java.net.UnknownHostException e2)
        {
          sage.MySwingUtils.showWrappedMessageDialog(MiniClientConnection.CONNECT_FAILURE_GENERAL_INTERNET,
              "Connection Error", javax.swing.JOptionPane.ERROR_MESSAGE);
          return;
        }
        catch (Exception e1)
        {
          sage.MySwingUtils.showWrappedMessageDialog(MiniClientConnection.CONNECT_FAILURE_LOCATOR_SERVER,
              "Connection Error", javax.swing.JOptionPane.ERROR_MESSAGE);
          return;
        }
        if (msi.lookupIP == null)
        {
          sage.MySwingUtils.showWrappedMessageDialog(MiniClientConnection.CONNECT_FAILURE_LOCATOR_REGISTRATION,
              "Lookup Error", javax.swing.JOptionPane.ERROR_MESSAGE);
          return;
        }
        addr = msi.lookupIP;
      }
      else
      {
        addr = msi.serverDirectAddress;
      }
      myConn = new MiniClientConnection(addr, MiniClient.forcedMAC, msi.serverType == LOCAL_SERVER, msi);
      try
      {
        myConn.connect();
        setVisible(false);
        msi.lastConnectTime = System.currentTimeMillis();
        MiniClient.myProperties.setProperty("servers/" + (msi.serverType == LOCAL_SERVER ? "local/" : "") +  msi.serverName + "/last_connect_time",
            Long.toString(System.currentTimeMillis()));
      }
      catch (java.net.NoRouteToHostException e3)
      {
        sage.MySwingUtils.showWrappedMessageDialog(MiniClientConnection.CONNECT_FAILURE_GENERAL_INTERNET,
            "Connection Error", javax.swing.JOptionPane.ERROR_MESSAGE);
        return;
      }
      catch (java.net.UnknownHostException e2)
      {
        sage.MySwingUtils.showWrappedMessageDialog(MiniClientConnection.CONNECT_FAILURE_GENERAL_INTERNET,
            "Connection Error", javax.swing.JOptionPane.ERROR_MESSAGE);
        return;
      }
      catch (java.io.IOException e)
      {
        // Now if we're using the Locator ID we try to do the ping with the server to get more information
        if (msi.serverType == LOCATABLE_SERVER)
        {
          try
          {
            int pingRez = sage.locator.LocatorLookupClient.haveLocatorPingID(msi.serverLookupID);
            if (pingRez == sage.locator.LocatorLookupClient.PING_SUCCEED)
            {
              sage.MySwingUtils.showWrappedMessageDialog(MiniClientConnection.CONNECT_FAILURE_CLIENT_SIDE,
                  "Connection Error", javax.swing.JOptionPane.ERROR_MESSAGE);
              return;
            }
          }
          catch (Exception e4)
          {
            sage.MySwingUtils.showWrappedMessageDialog(MiniClientConnection.CONNECT_FAILURE_SEVER_SIDE,
                "Connection Error", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
          }
        }
        sage.MySwingUtils.showWrappedMessageDialog(MiniClientConnection.CONNECT_FAILURE_SEVER_SIDE,
            "Connection Error", javax.swing.JOptionPane.ERROR_MESSAGE);
      }
    }
    finally
    {
      connecting = false;
    }
  }

  public void refreshServerList()
  {
    // Also remove any local servers we did not discover this time
    final java.util.HashSet lastLocalServers = new java.util.HashSet();
    for (int j = 0; j < currServerList.size(); j ++)
    {
      MgrServerInfo msi = (MgrServerInfo) currServerList.get(j);
      if (msi.serverType == LOCAL_SERVER)
        lastLocalServers.add(msi);
    }
    MiniClient.ServerInfo[] sis = MiniClient.discoverServers(5000,
        new MiniClient.ServerDiscoverCallback()
    {
      public void serverDiscovered(MiniClient.ServerInfo si)
      {
        // Check to see if we have this server's info already
        boolean serverAlreadyFound = false;
        for (int j = 0; j < currServerList.size(); j ++)
        {
          MgrServerInfo msi = (MgrServerInfo) currServerList.get(j);
          if (msi.serverType == LOCAL_SERVER)
          {
            if (msi.serverName.equalsIgnoreCase(si.name))
            {
              lastLocalServers.remove(msi);
              serverAlreadyFound = true;
              break;
            }
          }
        }
        if (!serverAlreadyFound)
        {
          currServerList.add(new MgrServerInfo(si));
          sortServerList();
          final java.util.Vector newServerList = (java.util.Vector)currServerList.clone();
          java.awt.EventQueue.invokeLater(new Runnable()
          {
            public void run()
            {
              clientList.setListData(newServerList);
            }
          });
        }
      }
    });
    if (!lastLocalServers.isEmpty())
    {
      // Remove these guys from the list
      currServerList.removeAll(lastLocalServers);
      sortServerList();
      final java.util.Vector newServerList = (java.util.Vector)currServerList.clone();
      java.awt.EventQueue.invokeLater(new Runnable()
      {
        public void run()
        {
          clientList.setListData(newServerList);
        }
      });
    }
  }

  private void sortServerList()
  {
    java.util.Collections.sort(currServerList, new java.util.Comparator()
    {
      public int compare(Object o1, Object o2)
      {
        return ((MgrServerInfo) o1).serverName.compareToIgnoreCase(((MgrServerInfo) o2).serverName);
      }
    });
  }

  public void valueChanged(javax.swing.event.ListSelectionEvent e)
  {
    MgrServerInfo selObj = (MgrServerInfo) clientList.getSelectedValue();
    if (selObj == null)
    {
      editClientButton.setEnabled(false);
      connectClientButton.setEnabled(false);
      removeClientButton.setEnabled(false);
    }
    else
    {
      if (selObj.serverType == LOCAL_SERVER)
      {
        editClientButton.setEnabled(false);
        connectClientButton.setEnabled(true);
        removeClientButton.setEnabled(false);
      }
      else
      {
        editClientButton.setEnabled(true);
        connectClientButton.setEnabled(true);
        removeClientButton.setEnabled(true);
      }
    }
    validate();
  }

  private void doSetup()
  {
    if (setupD == null)
    {
      setupD = new javax.swing.JDialog(this, "Settings", true);
      java.awt.Container setupPane = setupD.getContentPane();
      javax.swing.JButton okB = new javax.swing.JButton("OK");
      javax.swing.Action cancelA = new javax.swing.AbstractAction("Cancel")
      {
        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
          setupD.setVisible(false);
        }
      };
      javax.swing.JButton cancelB = new javax.swing.JButton(cancelA);
      javax.swing.JButton applyB = new javax.swing.JButton("Apply");
      javax.swing.JTabbedPane tabby = new javax.swing.JTabbedPane();

      cancelB.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(
          javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "Cancel");
      cancelB.getActionMap().put("Cancel", cancelA);
      applyB.addActionListener(new java.awt.event.ActionListener()
      {
        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
          if (checkSetupValues())
            applySetupChanges();
        }
      });
      okB.addActionListener(new java.awt.event.ActionListener()
      {
        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
          if (checkSetupValues())
          {
            applySetupChanges();
            setupD.setVisible(false);
          }
        }
      });

      setupPane.setLayout(new java.awt.BorderLayout());
      setupPane.add(tabby, "Center");
      javax.swing.JPanel setupBPanel = new javax.swing.JPanel();
      setupBPanel.setLayout(new java.awt.FlowLayout());
      setupBPanel.add(okB);
      setupBPanel.add(cancelB);
      setupBPanel.add(applyB);
      setupPane.add(setupBPanel, "South");

      javax.swing.JPanel generalP = new javax.swing.JPanel();
      javax.swing.JPanel streamingP = new javax.swing.JPanel();
      javax.swing.JPanel aboutP = new javax.swing.JPanel();

      tabby.addTab("General", generalP);
      tabby.addTab("Streaming", streamingP);
      tabby.addTab("Version", aboutP);

      generalP.setLayout(new java.awt.GridBagLayout());
      streamingP.setLayout(new java.awt.GridBagLayout());

      exitAppWhenConnClosedC = new javax.swing.JCheckBox("Exit application after closing server connection");
      enableVideoPPC = new javax.swing.JCheckBox("<html>Enable Video Post Processing.<br>This will improve video quality but will use more system resources.<br>Disable this if you have performance problems due to high CPU usage.</html>");
      enable3DC = new javax.swing.JCheckBox("<html>Enable 3D Acceleration<br>This is needed for video rendering on platforms without Overlay (such as NVidia GPUs on Vista)</html>");
      enableCustomTitleBarC = new javax.swing.JCheckBox("Enable custom SageTV title bar");
      showStatSummC = new javax.swing.JCheckBox("Show Streaming Statistics in Title Bar");
      java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
      gbc.gridx = gbc.gridy = 0;
      gbc.gridwidth = gbc.gridheight = 1;
      gbc.weightx = gbc.weighty = 1;
      gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
      gbc.anchor = java.awt.GridBagConstraints.WEST;
      generalP.add(exitAppWhenConnClosedC, gbc);
      gbc.gridy++;
      generalP.add(enableCustomTitleBarC, gbc);
      gbc.insets = new java.awt.Insets(0, 10, 0, 0);
      gbc.gridy++;
      gbc.weighty = 0;
      generalP.add(showStatSummC, gbc);
      if (!MiniClient.MAC_OS_X)
      {
        gbc.gridy++;
        gbc.weighty = 0;
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        generalP.add(enable3DC, gbc);
      }
      gbc.gridy++;
      gbc.weighty = 1;
      gbc.insets = new java.awt.Insets(0, 0, 0, 0);
      generalP.add(enableVideoPPC, gbc);
      enableCustomTitleBarC.addItemListener(new java.awt.event.ItemListener()
      {
        public void itemStateChanged(java.awt.event.ItemEvent e)
        {
          showStatSummC.setEnabled(enableCustomTitleBarC.isSelected());
        }
      });

      if (MiniClient.WINDOWS_OS)
      {
        irPanel = new javax.swing.JPanel();
        irPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        irPanel.add(new javax.swing.JLabel("IRMan/USB-UIRT Receive Port:"));
        irC = new javax.swing.JComboBox(new String[] { "", "USB", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9" });
        irPanel.add(irC);
        gbc.gridy++;
        gbc.weightx = 1.0;
        generalP.add(irPanel, gbc);
      }

      localFSPanel = new javax.swing.JPanel();
      localFSPanel.setLayout(new java.awt.GridLayout(3, 1));
      localFSPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Local Filesystem Security"));
      javax.swing.ButtonGroup secureGrp = new javax.swing.ButtonGroup();
      highSecurityR = new javax.swing.JRadioButton("Disable server's access to local filesystem");
      medSecurityR = new javax.swing.JRadioButton("Allow server to browse local filesystem; but prompt for all uploads, downloads and deletions");
      lowSecurityR = new javax.swing.JRadioButton("Allow server full access to local fileystem");
      secureGrp.add(highSecurityR);
      secureGrp.add(medSecurityR);
      secureGrp.add(lowSecurityR);
      localFSPanel.add(highSecurityR);
      localFSPanel.add(medSecurityR);
      localFSPanel.add(lowSecurityR);
      gbc.gridy++;
      gbc.weightx = 1.0;
      generalP.add(localFSPanel, gbc);

      java.awt.event.ItemListener streamModeListy = new java.awt.event.ItemListener()
      {
        public void itemStateChanged(java.awt.event.ItemEvent evt)
        {
          if (dynamicModeR.isSelected() || pullModeR.isSelected())
          {
            videoBitrateS.setEnabled(false);
            videoResC.setEnabled(false);
            videoFpsC.setEnabled(false);
            keyFrameIntervalC.setEnabled(false);
            useBFramesC.setEnabled(false);
            audioBitrateC.setEnabled(false);
          }
          else
          {
            videoBitrateS.setEnabled(true);
            videoResC.setEnabled(true);
            videoFpsC.setEnabled(true);
            keyFrameIntervalC.setEnabled(true);
            useBFramesC.setEnabled(true);
            audioBitrateC.setEnabled(true);
          }
        }
      };
      javax.swing.ButtonGroup streamGrp = new javax.swing.ButtonGroup();
      dynamicModeR = new javax.swing.JRadioButton("<html>Use SageTV's Dynamic Streaming System to auto-select the right format and bitrate.<br>This will also dynamically adjust the bitrate while streaming.</html>");
      dynamicModeR.addItemListener(streamModeListy);
      pullModeR = new javax.swing.JRadioButton("Playback media without transcoding (only recommended for LAN usage)");
      pullModeR.addItemListener(streamModeListy);
      fixedModeR = new javax.swing.JRadioButton("Manually specify the encoding parameters for streaming:");
      fixedModeR.addItemListener(streamModeListy);
      streamGrp.add(dynamicModeR);
      streamGrp.add(pullModeR);
      streamGrp.add(fixedModeR);
      encodingP = new javax.swing.JPanel();
      encodingP.setBorder(javax.swing.BorderFactory.createTitledBorder("Encoding Parameters"));
      gbc.gridy = 0;
      streamingP.add(dynamicModeR, gbc);
      gbc.gridy++;
      streamingP.add(pullModeR, gbc);
      gbc.gridy++;
      streamingP.add(fixedModeR, gbc);
      gbc.gridy++;
      streamingP.add(encodingP, gbc);

      encodingP.setLayout(new java.awt.GridBagLayout());
      javax.swing.SpinnerNumberModel vbrM = new javax.swing.SpinnerNumberModel(300, 50, 2000, 10);
      videoBitrateS = new javax.swing.JSpinner(vbrM);
      videoResC = new javax.swing.JComboBox(new String[] { "D1 (704x480/576)", "CIF (352x240/288)" });
      videoFpsC = new javax.swing.JComboBox(new String[] { "5", "10", "15", "20", "25", "30" });
      javax.swing.SpinnerNumberModel keyFrameM = new javax.swing.SpinnerNumberModel(10, 1, 30, 1);
      keyFrameIntervalC = new javax.swing.JSpinner(keyFrameM);
      useBFramesC = new javax.swing.JCheckBox();
      audioBitrateC = new javax.swing.JComboBox(new String[] { "32", "48", "56", "64", "80", "96", "112", "128", "160", "192" });
      gbc.gridx = gbc.gridy = 0;
      gbc.weightx = 0.8;
      gbc.weighty = 0;
      gbc.anchor = gbc.WEST;
      gbc.gridwidth = gbc.gridheight = 1;
      gbc.insets = new java.awt.Insets(3, 2, 3, 2);
      encodingP.add(new javax.swing.JLabel("Video Bitrate (kbps):"), gbc);
      gbc.gridy++;
      encodingP.add(new javax.swing.JLabel("Video Resolution:"), gbc);
      gbc.gridy++;
      encodingP.add(new javax.swing.JLabel("Video Frames per Second:"), gbc);
      gbc.gridy++;
      encodingP.add(new javax.swing.JLabel("Key Frame Interval (seconds):"), gbc);
      gbc.gridy++;
      encodingP.add(new javax.swing.JLabel("Use B-Frames in video encoding:"), gbc);
      gbc.gridy++;
      encodingP.add(new javax.swing.JLabel("Audio Bitrate (kbps):"), gbc);
      gbc.gridy = 0;
      gbc.gridx++;
      gbc.weightx = 0.4;
      gbc.fill = gbc.NONE;
      encodingP.add(videoBitrateS, gbc);
      gbc.gridy++;
      encodingP.add(videoResC, gbc);
      gbc.gridy++;
      encodingP.add(videoFpsC, gbc);
      gbc.gridy++;
      encodingP.add(keyFrameIntervalC, gbc);
      gbc.gridy++;
      encodingP.add(useBFramesC, gbc);
      gbc.gridy++;
      encodingP.add(audioBitrateC, gbc);

      aboutP.add(new javax.swing.JLabel("SageTV Placeshifter V" + sage.Version.VERSION));

      setupD.setLocation(getX() + 25, getY() + 25);
      setupD.pack();
      sage.MySwingUtils.safePositionDialog(setupD);
    }

    // Set the current parameters
    exitAppWhenConnClosedC.setSelected(MiniClient.myProperties.getProperty("exit_app_on_server_close", "false").equalsIgnoreCase("true"));
    enableVideoPPC.setSelected(MiniClient.myProperties.getProperty("enable_video_postprocessing", "true").equalsIgnoreCase("true"));
    enable3DC.setSelected(MiniClient.myProperties.getProperty("opengl", "true").equalsIgnoreCase("true"));
    enableCustomTitleBarC.setSelected(MiniClient.myProperties.getProperty("enable_custom_title_bar", MiniClient.MAC_OS_X ? "false" : "true").
        equalsIgnoreCase("true"));
    showStatSummC.setSelected(MiniClient.myProperties.getProperty("show_stat_summary", MiniClient.MAC_OS_X ? "false" : "true").equalsIgnoreCase("true"));
    showStatSummC.setEnabled(enableCustomTitleBarC.isSelected());
    if (irC != null)
      irC.setSelectedItem(MiniClient.myProperties.getProperty("irman_rcv_port", ""));
    String fsSecurity = MiniClient.myProperties.getProperty("local_fs_security", "high");
    if ("low".equals(fsSecurity))
      lowSecurityR.setSelected(true);
    else if ("med".equals(fsSecurity))
      medSecurityR.setSelected(true);
    else
      highSecurityR.setSelected(true);
    String streamMode = MiniClient.myProperties.getProperty("streaming_mode", "dynamic");
    if ("pull".equalsIgnoreCase(streamMode))
      pullModeR.setSelected(true);
    else if ("fixed".equalsIgnoreCase(streamMode))
      fixedModeR.setSelected(true);
    else
      dynamicModeR.setSelected(true);

    try
    {
      videoBitrateS.setValue(new Integer(MiniClient.myProperties.getProperty("fixed_encoding/video_bitrate_kbps", "300")));
      keyFrameIntervalC.setValue(new Integer(MiniClient.myProperties.getProperty("fixed_encoding/key_frame_interval", "10")));
    }
    catch (NumberFormatException e){}
    videoResC.setSelectedItem("D1".equals(MiniClient.myProperties.getProperty("fixed_encoding/video_resolution", "CIF")) ?
        "D1 (704x480/576)" : "CIF (352x240/288)");
    videoFpsC.setSelectedItem(MiniClient.myProperties.getProperty("fixed_encoding/fps", "30"));
    useBFramesC.setSelected("true".equalsIgnoreCase(MiniClient.myProperties.getProperty("fixed_encoding/use_b_frames", "true")));
    audioBitrateC.setSelectedItem(MiniClient.myProperties.getProperty("fixed_encoding/audio_bitrate_kbps", "64"));

    setupD.setVisible(true);
  }

  private void applySetupChanges()
  {
    MiniClient.myProperties.setProperty("exit_app_on_server_close", Boolean.toString(exitAppWhenConnClosedC.isSelected()));
    MiniClient.myProperties.setProperty("enable_custom_title_bar", Boolean.toString(enableCustomTitleBarC.isSelected()));
    MiniClient.myProperties.setProperty("show_stat_summary", Boolean.toString(showStatSummC.isSelected()));
    MiniClient.myProperties.setProperty("enable_video_postprocessing", Boolean.toString(enableVideoPPC.isSelected()));
    if (!MiniClient.MAC_OS_X)
      MiniClient.myProperties.setProperty("opengl", Boolean.toString(enable3DC.isSelected()));
    if (irC != null)
    {
      Object obj = irC.getSelectedItem();
      if (obj != null)
      {
        // Plugin can't be loaded while config UI is up so no reason to update it on the fly
        MiniClient.myProperties.setProperty("irman_rcv_port", obj.toString());
      }
    }
    if (lowSecurityR.isSelected())
      MiniClient.myProperties.setProperty("local_fs_security", "low");
    else if (medSecurityR.isSelected())
      MiniClient.myProperties.setProperty("local_fs_security", "med");
    else
      MiniClient.myProperties.setProperty("local_fs_security", "high");
    if (pullModeR.isSelected())
      MiniClient.myProperties.setProperty("streaming_mode", "pull");
    else if (fixedModeR.isSelected())
      MiniClient.myProperties.setProperty("streaming_mode", "fixed");
    else
      MiniClient.myProperties.setProperty("streaming_mode", "dynamic");
    MiniClient.myProperties.setProperty("fixed_encoding/video_bitrate_kbps", videoBitrateS.getValue() + "");
    MiniClient.myProperties.setProperty("fixed_encoding/key_frame_interval", keyFrameIntervalC.getValue() + "");
    MiniClient.myProperties.setProperty("fixed_encoding/video_resolution", videoResC.getSelectedIndex() == 0 ? "D1" : "CIF");
    MiniClient.myProperties.setProperty("fixed_encoding/fps", videoFpsC.getSelectedItem() + "");
    MiniClient.myProperties.setProperty("fixed_encoding/use_b_frames", useBFramesC.isSelected() + "");
    MiniClient.myProperties.setProperty("fixed_encoding/audio_bitrate_kbps", audioBitrateC.getSelectedItem() + "");
  }

  private boolean checkSetupValues()
  {
    return true;
  }

  public static final int LOCAL_SERVER = 1;
  public static final int DIRECT_CONNECT_SERVER = 2;
  public static final int LOCATABLE_SERVER = 3;
  public static class MgrServerInfo
  {
    public MgrServerInfo()
    {
    }
    public MgrServerInfo(String serverName) throws IllegalArgumentException // thrown if the properties values are bad
    {
      this.serverName = serverName;
      try
      {
        serverType = Integer.parseInt(MiniClient.myProperties.getProperty("servers/" + serverName + "/type", ""));
        serverDirectAddress = MiniClient.myProperties.getProperty("servers/" + serverName + "/address", "");
        serverLookupID = MiniClient.myProperties.getProperty("servers/" + serverName + "/locator_id", "");
        lastConnectTime = Long.parseLong(MiniClient.myProperties.getProperty("servers/" + serverName + "/last_connect_time", "0"));
        authBlock = MiniClient.myProperties.getProperty("servers/" + serverName + "/auth_block", "");
      }
      catch (Exception e)
      {
        throw new IllegalArgumentException("Bad server info properties");
      }
      doIPLookupNow();
    }
    public MgrServerInfo(MiniClient.ServerInfo sis)
    {
      serverDirectAddress = serverName = sis.name;
      serverType = LOCAL_SERVER;
      serverLookupID = sis.locatorID;
      port = sis.port;
      try
      {
        lastConnectTime = Long.parseLong(MiniClient.myProperties.getProperty("servers/local/" + serverName + "/last_connect_time",
            Long.toString(System.currentTimeMillis())));
      }catch (NumberFormatException e){}
    }
    public String doIPLookupNow()
    {
      if (serverType == LOCATABLE_SERVER)
      {
        // Attempt a lookup right now
        try
        {
          lookupIP = sage.locator.LocatorLookupClient.lookupIPForGuid(serverLookupID);
        }
        catch (Exception e1)
        {
        }
      }
      return lookupIP;
    }
    public void setAuthBlock(String newAuth)
    {
      authBlock = newAuth;
      MiniClient.myProperties.setProperty("servers/" + serverName + "/auth_block", newAuth);
      MiniClient.saveConfig();
    }
    public String serverName = "";
    public String serverDirectAddress = "";
    public String serverLookupID = "";
    public String lookupIP;
    public int port;
    public long lastConnectTime;
    public int serverType;
    public String authBlock = "";

    public String toString()
    {
      return serverName;
    }
  }

  protected class ServerCellRenderer extends javax.swing.JPanel implements javax.swing.ListCellRenderer
  {
    //java.awt.Color highlightColor = new java.awt.Color(0, 0, 128);

    ServerCellRenderer()
    {
      if (noFocusBorder == null) {
        noFocusBorder = javax.swing.BorderFactory.createLineBorder(java.awt.Color.gray, 1);
      }
      setOpaque(true);
      // This uses four labels.
      logoLabel = new javax.swing.JLabel(new javax.swing.ImageIcon(frameIcon));
      logoLabel.setOpaque(false);
      nameLabel = new javax.swing.JLabel("");
      nameLabel.setOpaque(false);
      addrLabel = new javax.swing.JLabel("");
      addrLabel.setOpaque(false);
      lastConnectionLabel = new javax.swing.JLabel("");
      lastConnectionLabel.setOpaque(false);
      setLayout(new java.awt.GridBagLayout());
      java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
      gbc.gridx = gbc.gridy = 0;
      gbc.gridheight = 3;
      gbc.gridwidth = 1;
      gbc.weightx = 0;
      gbc.weighty = 0;
      gbc.fill = gbc.BOTH;
      gbc.insets = new java.awt.Insets(3, 3, 3, 3);
      gbc.ipadx = 3;
      gbc.ipady = 0;
      add(logoLabel, gbc);

      gbc.gridx = 1;
      gbc.gridheight = 1;
      gbc.weightx = 1.0;
      gbc.fill = gbc.HORIZONTAL;
      add(nameLabel, gbc);
      gbc.gridy++;
      add(addrLabel, gbc);
      gbc.gridy++;
      add(lastConnectionLabel, gbc);

      setBorder(noFocusBorder);
    }

    public java.awt.Component getListCellRendererComponent(javax.swing.JList list, Object value, int index,
        boolean isSelected, boolean cellHasFocus)
    {
      MgrServerInfo msi = (MgrServerInfo)value;
      if (isSelected)
      {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
      }
      else
      {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
      }
      nameLabel.setText(msi.serverName);
      if (msi.lastConnectTime > 0)
        lastConnectionLabel.setText("Last Connected: " + new java.util.Date(msi.lastConnectTime).toString());
      else
        lastConnectionLabel.setText("Last Connected: Never");
      if (msi.serverType == LOCAL_SERVER)
      {
        addrLabel.setText("Local Network");
      }
      else if (msi.serverType == LOCATABLE_SERVER)
      {
        if (msi.lookupIP != null && msi.lookupIP.length() > 0)
          addrLabel.setText("Locator Found Server");
        else
          addrLabel.setText("Locator Failed to Find Server");
      }
      else
        addrLabel.setText(msi.serverDirectAddress);
      setBorder((cellHasFocus) ? javax.swing.UIManager.getBorder("List.focusCellHighlightBorder") : noFocusBorder);
      return this;
    }

    private javax.swing.border.Border noFocusBorder;
    private javax.swing.JLabel logoLabel;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JLabel addrLabel;
    private javax.swing.JLabel lastConnectionLabel;
  }

  private javax.swing.JList clientList;

  private javax.swing.JButton addClientButton;
  private javax.swing.JButton editClientButton;
  private javax.swing.JButton connectClientButton;
  private javax.swing.JButton removeClientButton;
  private javax.swing.JButton exitButton;
  private javax.swing.JButton settingsButton;

  private MiniClientConnection myConn;

  private java.util.Vector currServerList;

  private AddServerDialog myAddDialog;
  private java.awt.Image frameIcon;

  private javax.swing.JDialog setupD;
  private javax.swing.JCheckBox enableVideoPPC;
  private javax.swing.JCheckBox exitAppWhenConnClosedC;
  private javax.swing.JCheckBox showStatSummC;
  private javax.swing.JCheckBox enableCustomTitleBarC;
  private javax.swing.JCheckBox enable3DC;
  private javax.swing.JPanel irPanel;
  private javax.swing.JPanel localFSPanel;
  private javax.swing.JRadioButton highSecurityR;
  private javax.swing.JRadioButton medSecurityR;
  private javax.swing.JRadioButton lowSecurityR;
  private javax.swing.JComboBox irC;
  private javax.swing.JPanel encodingP;
  private javax.swing.JRadioButton dynamicModeR;
  private javax.swing.JRadioButton pullModeR;
  private javax.swing.JRadioButton fixedModeR;

  private javax.swing.JSpinner videoBitrateS;
  private javax.swing.JComboBox videoResC;
  private javax.swing.JComboBox videoFpsC;
  private javax.swing.JSpinner keyFrameIntervalC;
  private javax.swing.JCheckBox useBFramesC;
  private javax.swing.JComboBox audioBitrateC;

  private boolean connecting;
}
