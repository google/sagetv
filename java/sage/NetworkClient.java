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

import java.util.HashMap;
import java.util.Map;

/*
 * 10/20/05 - We're changing the network system to only use 2 channels for communication instead of 4. We'll
 * just keep the serverNotifier and clientListener. The serverNotifier will become a message based protocol which
 * handles most client/server communcation. The clientListener will be used to receive larger data updates such as
 * DB, properties and profiler information.
 */
public class NetworkClient
{
  public static final int CS_STATE_UNAUTHORIZED_CLIENT = -2;
  public static final int CS_STATE_VERSION_MISMATCH = -1;
  public static final int CS_STATE_NOT_INITIALIZED = 0;
  public static final int CS_STATE_SERVER_NOT_FOUND = 1;
  public static final int CS_STATE_SERVER_LOADING = 2;
  public static final int CS_STATE_CONNECTION_ESTABLISHING = 3;
  public static final int CS_STATE_FULLY_CONNECTED = 4;

  private NetworkClient()
  {
    createTime = Sage.time();
  }

  public static NetworkClient connectToServer(String serverName, boolean popupError, boolean returnAlways)
  {
    NetworkClient neddy = new NetworkClient();
    if (neddy.connect(serverName, popupError))
      return neddy;
    else
    {
      if (!returnAlways)
      {
        neddy.fullCleanup();
        return null;
      }
      else
        return neddy;
    }
  }
  public boolean isClientConnected()
  {
    return clientListener != null && serverNotifier != null;
  }
  private boolean connect(String serverName, boolean popupError)
  {
    java.net.Socket sake = null;
    try
    {
      if ("unknown".equals(serverName))
        throw new java.net.UnknownHostException("Local server not known yet...don't try to connect");
      sake = new java.net.Socket();
      sake.connect(new java.net.InetSocketAddress(serverName, Sage.getInt(SageTV.SAGETV_PORT, SageTV.DEFAULT_SAGETV_PORT)));
      clientListener = new SageTVConnection(sake);
      // If we got here then we are OK to connect to this server, set our connection state
      connectionState = CS_STATE_CONNECTION_ESTABLISHING;
      if (Sage.DBG) System.out.println("Setting c/s connection state to: CONNECTION_ESTABLISHING");
      SageTV.writeOutStateInfo(SageTV.SAGETV_CLIENT_CONNECTING, serverName, null);
      clientListener.takeFormOfListener();
      sake = new java.net.Socket();
      sake.connect(new java.net.InetSocketAddress(serverName, Sage.getInt(SageTV.SAGETV_PORT, SageTV.DEFAULT_SAGETV_PORT)));
      serverNotifier = new SageTVConnection(sake);
      serverNotifier.takeFormOfNotifier(clientListener);

      clientName = clientListener.getClientName();

      sake = null;
    }
    catch (java.net.UnknownHostException e)
    {
      cleanupFailedClientConnection();
      if (popupError)
        MySwingUtils.showWrappedMessageDialog(Sage.rez("Network_Cannot_Connect_Error") + " (0)",
            Sage.rez("Network_Error"), javax.swing.JOptionPane.ERROR_MESSAGE);
      System.out.println("Error establishing server connection to " + serverName + " of:" + e);
      e.printStackTrace();
      return false;
    }
    catch (java.net.ConnectException e)
    {
      cleanupFailedClientConnection();
      if (popupError)
        MySwingUtils.showWrappedMessageDialog(Sage.rez("Network_Cannot_Connect_Error") + " (1)",
            Sage.rez("Network_Error"), javax.swing.JOptionPane.ERROR_MESSAGE);
      System.out.println("Error establishing server connection to " + serverName + " of:" + e);
      e.printStackTrace();
      return false;
    }
    catch (java.io.IOException e)
    {
      cleanupFailedClientConnection();
      if (e.getMessage() != null && e.getMessage().startsWith("BAD_LICENSE"))
      {
        if (popupError)
        {
          MySwingUtils.showWrappedMessageDialog(Sage.rez("Network_License_Error"),
              Sage.rez("Network_Error"), javax.swing.JOptionPane.ERROR_MESSAGE);
        }
        connectionState = CS_STATE_UNAUTHORIZED_CLIENT;
        SageTV.writeOutStateInfo(SageTV.SAGETV_CLIENT_UNAUTHORIZED, serverName, null);
      }
      else if (e.getMessage() != null && e.getMessage().startsWith("VERSION_ERR"))
      {
        if (popupError)
        {
          MySwingUtils.showWrappedMessageDialog(Sage.rez("Network_Version_Error"),
              Sage.rez("Network_Error"), javax.swing.JOptionPane.ERROR_MESSAGE);
        }
        connectionState = CS_STATE_VERSION_MISMATCH;
        SageTV.writeOutStateInfo(SageTV.SAGETV_CLIENT_VERSION_MISMATCH, serverName, null);
      }
      else
      {
        if (popupError)
        {
          MySwingUtils.showWrappedMessageDialog(Sage.rez("Network_Cannot_Connect_Error") + " (2)",
              Sage.rez("Network_Error"), javax.swing.JOptionPane.ERROR_MESSAGE);
        }
      }
      System.out.println("Error establishing server connection to " + serverName + " of:" + e);
      e.printStackTrace();
      return false;
    }
    catch (Throwable e)
    {
      cleanupFailedClientConnection();
      if (popupError)
        MySwingUtils.showWrappedMessageDialog(Sage.rez("Network_Cannot_Connect_Error") + " (2)",
            Sage.rez("Network_Error"), javax.swing.JOptionPane.ERROR_MESSAGE);
      System.out.println("Error establishing server connection to " + serverName + " of:" + e);
      e.printStackTrace();
      return false;
    }
    finally
    {
      if (sake != null)
      {
        try
        {
          sake.close();
        }
        catch (Exception e){}
        sake = null;
      }
    }
    return true;
  }

  public static String getClientCapability(String client, String capability) {
    NetworkClient neddy = (NetworkClient) clientMap.get(client);
    if(neddy == null) return null;
    return neddy.capabilities.get(capability);
  }

  public static void setClientCapability(String client, String capability, String value) {
    NetworkClient neddy = (NetworkClient) clientMap.get(client);
    if(neddy == null) return;
    if(value == null || value.length() == 0)
      neddy.capabilities.remove(capability);
    else
      neddy.capabilities.put(capability, value);
  }

  public static void clearClientCapabilities(String client) {
    NetworkClient neddy = (NetworkClient) clientMap.get(client);
    if(neddy == null) return;
    neddy.capabilities.clear();
  }

  public static void acceptFromClient(java.net.Socket s)
  {
    SageTVConnection stvconn = null;
    try
    {
      stvconn = new SageTVConnection(s);
      String clientName = stvconn.getClientName();
      if (stvconn.getLinkType() == SageTVConnection.CLIENT_LISTENER)
      {
        if (clientMap.containsKey(clientName))
        {
          // Huh? Why would this occur?
          NetworkClient oldConn = (NetworkClient) clientMap.get(clientName);
          oldConn.fullCleanup();
          clientMap.remove(clientName);
        }

        // Before we do this; we need to check to see if another client has already connected and only done the first
        // part of its initialization. If so; we don't want to block it on completion of the second part.

        // Narflex - 5/14/2012 - I added a synchronized (clientMap) around this block of code because if you don't then if a property
        // change on the server occurs during the initializeListenerData process after that process has done the property sync,
        // but before the new NetworkClient gets put into the clientMap; then the properties on that client could end up being
        // out of sync w/ the server. That would explain the one time I saw this after enabling channels in the server but them
        // not appearing in the client...which I think was done as part of restarting both at once, so those ops could have
        // occurred at times as outlined here.
        // Narflex - 5/30/2012 - I had to remove the sync block because it could lockup other threads (like the Seeker) that were making calls
        // into here that required usage of the clientMap var.  Now I track the property changes that we need to correct things and push them out
        // after we are done here.
        pendingPropXfers = new java.util.Vector();
        stvconn.initializeListenerData();
        NetworkClient neddy = new NetworkClient();
        neddy.clientListener = stvconn;
        neddy.clientName = stvconn.getClientName();
        clientMap.put(clientName, neddy);
        java.util.Vector myPropXfers = pendingPropXfers;
        pendingPropXfers = null;
        if (!myPropXfers.isEmpty())
        {
          if (Sage.DBG) System.out.println("Sending property syncs that occurred during connection initiation...");
          stvconn.updateProperties((String[]) myPropXfers.toArray(Pooler.EMPTY_STRING_ARRAY));
        }

        // In case there's network encoders on the new client that just came up also
        SchedulerSelector.getInstance().kick(true);
      }
      else
      {
        NetworkClient currConn = (NetworkClient) clientMap.get(clientName);
        if (currConn == null)
        {
          // Try to just match it to any other connection that hasn't been completed yet
          if (!clientMap.isEmpty())
          {
            if (Sage.DBG) System.out.println("Couldn't match up client connection on specified ip:port; search manually...");
            java.util.Iterator walker = clientMap.values().iterator();
            while (walker.hasNext())
            {
              currConn = (NetworkClient) walker.next();
              if (currConn != null && currConn.serverNotifier == null)
              {
                currConn.clientListener.setClientName(clientName);
                walker.remove();
                clientMap.put(clientName, currConn);
                currConn.clientName = clientName;
                break;
              }
              currConn = null;
            }
          }
          if (currConn == null)
          {
            if (Sage.DBG) System.out.println("Cleaning up new connection because we can't match it up newName=" + clientName +
                " map=" + clientMap);
            stvconn.cleanup();
            return;
          }
        }
        // This is the server notifier, which is the final connection made. Now all of
        // the threads can be started and we can mark us as alive.
        currConn.serverNotifier = stvconn;
        currConn.alive = true;
        currConn.clientListener.setupKeepAlive();
        currConn.serverNotifier.setListenerMsgShare(currConn.clientListener.getListenerMsgShare());
        currConn.serverNotifier.startupMessagingThreads();
        currConn.clientIP = currConn.serverNotifier.getSocket().getInetAddress().getHostAddress();
        sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.CLIENT_CONNECTED,
            new Object[] { sage.plugin.PluginEventManager.VAR_IPADDRESS, currConn.clientIP,
            sage.plugin.PluginEventManager.VAR_MACADDRESS, null});
        if (!Sage.client)
        {
          String myLocalIP = "127.0.0.1";
          try
          {
            myLocalIP = java.net.InetAddress.getLocalHost().getHostAddress();
          }
          catch (Exception e){}
          if ("127.0.0.1".equals(currConn.clientIP) || myLocalIP.equals(currConn.clientIP))
          {
            currConn.localClient = true;
          }
        }
      }
    }
    catch (Throwable e)
    {
      System.out.println("Error w/SageTV client connection:" + e);
      e.printStackTrace(System.out);
      if (stvconn != null)
      {
        NetworkClient oldConn = (NetworkClient) clientMap.get(stvconn.getClientName());
        if (oldConn != null)
          oldConn.fullCleanup();
        else
        {
          // If we aren't registered in the clientMap yet, we still may need to remove ourselves as listeners here.
          Wizard.getInstance().removeXctListener(stvconn);
          Carny.getInstance().removeCarnyListener(stvconn);
        }
        stvconn.cleanup();
      }
    }
    finally
    {
      pendingPropXfers = null;
    }
  }

  public void clientIsActive()
  {
    alive = true;
    if (serverNotifier != null)
      serverNotifier.startupMessagingThreads();
    if (clientListener != null)
    {
      Pooler.execute(clientListener, "ClientListener");
    }
  }

  public void startCleanup()
  {
    alive = false;
    if (clientListener != null)
    {
      synchronized (clientListener)
      {
        clientListener.cleanup();
        clientListener = null;
      }
    }
  }

  public void finishCleanup()
  {
    if (serverNotifier != null)
    {
      synchronized (serverNotifier)
      {
        serverNotifier.cleanup();
        serverNotifier = null;
      }
    }
  }

  // This should be called if there's a failure while we're connecting to the server
  private void cleanupFailedClientConnection()
  {
    if (clientListener != null)
    {
      clientListener.cleanup();
      clientListener = null;
    }
    if (serverNotifier != null)
    {
      serverNotifier.cleanup();
      serverNotifier = null;
    }
  }

  /*
   * NOTE: Don't sync on the cleanup code. We've had deadlocks here and its silly to try
   * to sync lock when things are dying.
   */
  public void fullCleanup()
  {
    if (Sage.DBG) System.out.println("NetworkClient fullCleanup " + clientName);
    if (alive && serverNotifier != null)
    {
      sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.CLIENT_DISCONNECTED,
          new Object[] { sage.plugin.PluginEventManager.VAR_IPADDRESS, clientIP,
          sage.plugin.PluginEventManager.VAR_MACADDRESS, null});
    }
    alive = false;
    if (Sage.client && serverNotifier != null)
    {
      //			synchronized (serverNotifier)
      {
        java.util.Iterator uiWalker = UIManager.getUIIterator();
        while (uiWalker.hasNext())
          SeekerSelector.getInstance().finishWatch((UIClient) uiWalker.next());
      }
    }
    if (clientListener != null)
    {
      //			synchronized (clientListener)
      {
        if (!Sage.client)
        {
          Wizard.getInstance().removeXctListener(clientListener);
          Carny.getInstance().removeCarnyListener(clientListener);
        }
        clientListener.cleanup();
        clientListener = null;
      }
    }
    if (serverNotifier != null)
    {
      //			synchronized (serverNotifier)
      {
        serverNotifier.cleanup();
        serverNotifier = null;
      }
    }
    if (!Sage.client)
    {
      // Ensure we are actually removing our own object from the map in case it somehow got replaced
      // with a new one of the same name (very possible for the clientID map)
      synchronized (clientMap)
      {
        if (clientMap.get(clientName) == this)
          clientMap.remove(clientName);
      }
      // Trigger cleanup of the watch file map
      SeekerSelector.getInstance().kick();
    }
  }

  public static void distributeRecursivePropertyChange(String name)
  {
    if (clientMap.isEmpty() && pendingPropXfers == null)
      return;
    java.util.Properties allProps = Sage.getAllPrefs();
    java.util.ArrayList xferProps = new java.util.ArrayList();
    java.util.Enumeration propWalker = allProps.propertyNames();
    while (propWalker.hasMoreElements())
    {
      String currKey = (String) propWalker.nextElement();
      if (currKey.startsWith(name))
        xferProps.add(currKey);
    }
    distributePropertyChanges((String[]) xferProps.toArray(Pooler.EMPTY_STRING_ARRAY));
  }

  public static void distributePropertyChange(String name)
  {
    if (clientMap.isEmpty() && pendingPropXfers == null)
      return;
    distributePropertyChanges(new String[] { name });
  }
  public static void distributePropertyChanges(String[] names)
  {
    java.util.Vector tempVec = pendingPropXfers;
    if (tempVec != null)
    {
      tempVec.addAll(java.util.Arrays.asList(names));
    }
    if (clientMap.isEmpty())
      return;
    java.util.ArrayList currClients;
    synchronized (clientMap)
    {
      currClients = new java.util.ArrayList(clientMap.values());
    }
    SageTV.incrementQuanta();
    for (int i = 0; i < currClients.size(); i++)
    {
      NetworkClient neddy = (NetworkClient) currClients.get(i);
      SageTVConnection listy = neddy.clientListener;
      if (listy != null)
      {
        // We can't hold this lock because if the call fails we'll have the connection lock during
        // cleanup which can cause a deadlock
        //synchronized (listy)
        {
          if (neddy.alive)
          {
            listy.updateProperties(names);
          }
        }
      }
    }
  }

  public static void distributeInactiveFile(String filename)
  {
    if (clientMap.isEmpty())
      return;
    java.util.ArrayList currClients;
    synchronized (clientMap)
    {
      currClients = new java.util.ArrayList(clientMap.values());
    }
    for (int i = 0; i < currClients.size(); i++)
    {
      NetworkClient neddy = (NetworkClient) currClients.get(i);
      SageTVConnection listy = neddy.serverNotifier;
      if (listy != null)
      {
        // We can't hold this lock because if the call fails we'll have the connection lock during
        // cleanup which can cause a deadlock
        //synchronized (listy)
        {
          if (neddy.alive)
          {
            listy.inactiveFile(filename);
          }
        }
      }
    }
  }

  public static void distributeScheduleChangedAsync()
  {
    if (!clientMap.isEmpty())
    {
      Pooler.execute(new Runnable()
      {
        public void run()
        {
          java.util.ArrayList currClients;
          synchronized (clientMap)
          {
            currClients = new java.util.ArrayList(clientMap.values());
          }
          for (int i = 0; i < currClients.size(); i++)
          {
            NetworkClient neddy = (NetworkClient) currClients.get(i);
            SageTVConnection listy = neddy.serverNotifier;
            if (listy != null)
            {
              // We can't hold this lock because if the call fails we'll have the connection lock during
              // cleanup which can cause a deadlock
              //synchronized (listy)
              {
                if (neddy.alive)
                {
                  listy.scheduleChanged();
                }
              }
            }
          }
        }
      }, "AsyncSchedChangeDistru");
    }
  }

  public static boolean isCSConnectionRestoring() { return Sage.client && SageTV.neddy != null && SageTV.neddy.restoring; }

  public static SageTVConnection getSN() { return SageTV.neddy.serverNotifier; }
  public static SageTVConnection getCL() { return SageTV.neddy.clientListener; }

  // This is what's used when the client has an error in communicating with the server.
  // At this point, we'll just reset the whole thing and start over, but at least
  // the app won't hang (because that's just bad)
  private boolean restoring = false;
  private final Object restoreLock = new Object();
  private void restoreClientServerConnection()
  {
    // If we try to restore this as part of the restart activity it causes lots of issues; so don't do it
    if (!SageTV.isAlive())
      return;
    synchronized (restoreLock)
    {
      if (restoring || !alive) return; // protect against circularities
    }
    if (java.awt.EventQueue.isDispatchThread())
    {
      Pooler.execute(new Runnable(){public void run(){ restoreClientServerConnection(); } });
      return;
    }
    synchronized (restoreLock)
    {
      if (restoring || !alive) return; // protect against circularities
      restoring = true;
    }

    connectionState = CS_STATE_NOT_INITIALIZED;
    if (Sage.DBG) System.out.println("Setting c/s connection state to: NOT_INITIALIZED");

    java.util.Map uiClientMenuMap = new java.util.HashMap();
    java.util.Map uiClientMFMap = new java.util.HashMap();
    java.util.Map uiClientMediaTimeMap = new java.util.HashMap();
    java.util.Map uiClientPlayMap = new java.util.HashMap();
    java.util.Iterator walker = UIManager.getUIIterator();
    while (walker.hasNext())
    {
      UIManager theUI = (UIManager) walker.next();
      uiClientMenuMap.put(theUI, theUI.getCurrUI());

      // Be sure we can reload the state of the media player as well
      MediaFile currMF = theUI.getVideoFrame().getCurrFile();
      if (currMF != null)
      {
        uiClientMFMap.put(theUI, currMF);
        uiClientMediaTimeMap.put(theUI, new Long(theUI.getVideoFrame().getMediaTimeMillis(true)));
        uiClientPlayMap.put(theUI, new Boolean(theUI.getVideoFrame().isPlayin()));
      }
      theUI.advanceUI("Server Connection Lost");
    }

    if (Sage.DBG) System.out.println("Client/Server connection has been lost. Cleaning up and then attempting to restore...");
    startCleanup();

    // For Carny, we just need to redo the sync to put it back where it should be

    // Seeker doesn't do anything on the client, but it does call VF.goodbye which closes the current file
    VideoFrame.closeAndWaitAll();
    EPG.getInstance().goodbye();

    finishCleanup();

    // Now we're totally disconnected from the server and can start the rebuilding...
    int numReconnects = 0;
    long sleepThisTime = 15000;
    while (!connect(Sage.preferredServer, false))
    {
      numReconnects++;
      if (numReconnects > 5)
      {
        numReconnects = 0;
        sleepThisTime = 7500;
      }
      try{Thread.sleep(sleepThisTime);}catch(Exception e){}
      sleepThisTime *= 2;

      if (Sage.autodiscoveredServer)
      {
        // The IP of the server may have changed...so rediscover it
        ServerInfo[] currServers = discoverServers(5000);
        if (currServers.length > 0 && !Sage.preferredServer.equals(currServers[0].address) && currServers[0].ready)
        {
          if (Sage.DBG) System.out.println("Redid discovery of SageTV server oldIP=" + Sage.preferredServer + " newIP=" + currServers[0].address);
          Sage.preferredServer = currServers[0].address;
        }
        if (currServers.length > 0)
        {
          if (currServers[0].ready)
          {
            // We moved this up into connect so it doesn't flip/flop between states if there's a version/license error
          }
          else
          {
            connectionState = CS_STATE_SERVER_LOADING;
            if (Sage.DBG) System.out.println("Setting c/s connection state to: SERVER_LOADING");
            SageTV.writeOutStateInfo(SageTV.SAGETV_CLIENT_SERVER_LOADING, currServers[0].address, null);
          }
        }
        else
        {
          connectionState = CS_STATE_SERVER_NOT_FOUND;
          if (Sage.DBG) System.out.println("Setting c/s connection state to: SERVER_NOT_FOUND");
          SageTV.writeOutStateInfo(SageTV.SAGETV_CLIENT_SERVER_NOT_FOUND, null, null);
        }
      }
    }
    connectionState = CS_STATE_FULLY_CONNECTED;
    if (Sage.DBG) System.out.println("Setting c/s connection state to: FULLY_CONNECTED");
    SageTV.writeOutStateInfo(SageTV.SAGETV_CLIENT_CONNECTED, Sage.preferredServer, null);

    // After rebuilding, we need to call this
    EPG.getInstance().resyncToProperties(true, true);
    CaptureDeviceManager[] cdms = MMC.getInstance().getCaptureDeviceManagers();
    for (int i = 0; i < cdms.length; i++)
      if (cdms[i] instanceof ClientCaptureManager)
        ((ClientCaptureManager) cdms[i]).resyncToProperties(true);

    clientIsActive();

    synchronized (restoreLock)
    {
      restoring = false;
    }
    // Refresh the current UI for everyone that's still connected
    walker = UIManager.getUIIterator();
    while (walker.hasNext())
    {
      UIManager theUI = (UIManager) walker.next();
      PseudoMenu startUI = (PseudoMenu) uiClientMenuMap.get(theUI);
      if (startUI != null)
      {
        // Reload any file that was playing and seek back to the appropriate time and set the playstate too
        // Do this before we go back to the proper UI menu so video doesn't auto-spawn itself or we kick ourselves out of
        // the OSD menu because we detect nothing is playing.
        MediaFile theMF = (MediaFile) uiClientMFMap.get(theUI);
        // We should not re-use this object reference, we have reloaded the DB from the server so get the new proper reference
        if (theMF != null)
          theMF = Wizard.getInstance().getFileForID(theMF.getID());
        if (theMF != null)
        {
          theUI.getVideoFrame().watch(theMF);
          theUI.getVideoFrame().timeJump(((Long) uiClientMediaTimeMap.get(theUI)).longValue());
          if (((Boolean) uiClientPlayMap.get(theUI)).booleanValue())
            theUI.getVideoFrame().play();
          else
            theUI.getVideoFrame().pause();
        }

        // The screensaver resets the UI history so you can't backup into it
        if (theUI.canBackupUI())
          theUI.backupUI();
        else
          theUI.advanceUI(startUI);
        // Make sure we didn't end up on the 'Server Connection Lost' screen again...and if so, just go to the main menu
        PseudoMenu newUI = theUI.getCurrUI();
        if (newUI != null && newUI.getBlueprint().getName().equals("Server Connection Lost"))
        {
          if (Sage.DBG) System.out.println("Recovery landed on Server Connection Lost...force us to the main menu");
          theUI.advanceUI(UIManager.MAIN_MENU);
        }
      }
      else
        theUI.freshStartup();

      /* We're up and running; broadcast our capabilties to the server */
      if (Sage.DBG) System.out.println("Sending " + theUI.getCapabilities().size()
          + " capabilities: " + theUI.getCapabilities());
      getSN().sendCapabilties(theUI, theUI.getCapabilities().entrySet());
    }
  }

  public static SageTVConnection getNetworkHookConnection(String clientName)
  {
    NetworkClient neddy = (NetworkClient) clientMap.get(clientName);
    if (neddy == null)
      return null;
    else
      return neddy.serverNotifier;
  }

  public static void distributeHook(String hookName, Object[] hookVars)
  {
    java.util.ArrayList currClients;
    synchronized (clientMap)
    {
      currClients = new java.util.ArrayList(clientMap.values());
    }
    for (int i = 0; i < currClients.size(); i++)
    {
      NetworkClient neddy = (NetworkClient) currClients.get(i);
      SageTVConnection listy = neddy.serverNotifier;
      if (listy != null)
      {
        // We can't hold this lock because if the call fails we'll have the connection lock during
        // cleanup which can cause a deadlock
        //synchronized (listy)
        {
          if (neddy.alive)
          {
            listy.sendHook(null, hookName, hookVars);
          }
        }
      }
    }
  }

  public static void sendRestartToLocalClients()
  {
    java.util.ArrayList currClients;
    synchronized (clientMap)
    {
      if (clientMap.isEmpty()) return;
      currClients = new java.util.ArrayList(clientMap.values());
    }
    boolean sentRestarts = false;
    for (int i = 0; i < currClients.size(); i++)
    {
      NetworkClient neddy = (NetworkClient) currClients.get(i);
      if (neddy != null && neddy.localClient)
      {
        neddy.serverNotifier.sendRestart();
        sentRestarts = true;
      }
    }
    if (sentRestarts)
    {
      // Wait for a second here to ensure that the messages actually got sent out
      try{Thread.sleep(500);}catch(Exception e){}
    }
  }

  public static boolean areNonLocalClientsConnected()
  {
    java.util.ArrayList currClients;
    synchronized (clientMap)
    {
      if (clientMap.isEmpty()) return false;
      currClients = new java.util.ArrayList(clientMap.values());
    }
    for (int i = 0; i < currClients.size(); i++)
    {
      NetworkClient neddy = (NetworkClient) currClients.get(i);
      if (neddy != null && !neddy.localClient)
        return true;
    }
    return false;
  }

  public static void communicationFailure(SageTVConnection failedConn)
  {
    if (Sage.DBG && failedConn != null) System.out.println("NetworkManager CommunicationFailure : " + failedConn.getClientName() + " type=" + failedConn.getLinkType());
    if (Sage.client)
    {
      if (SageTV.neddy != null && (failedConn == SageTV.neddy.clientListener || failedConn == SageTV.neddy.serverNotifier))
        SageTV.neddy.restoreClientServerConnection();
    }
    else
    {
      NetworkClient neddy = (NetworkClient) clientMap.get(failedConn.getClientName());
      if (neddy != null)
        neddy.fullCleanup();
    }
  }

  public static void killAll()
  {
    java.util.Iterator walker = (new java.util.ArrayList(clientMap.values())).iterator();
    while (walker.hasNext())
    {
      ((NetworkClient) walker.next()).fullCleanup();
    }
    clientMap.clear();
    clientIDMap.clear();
  }

  public static String[] getConnectedClients()
  {
    return (String[]) clientMap.keySet().toArray(Pooler.EMPTY_STRING_ARRAY);
  }
  public static String[] getConnectedClientIDs()
  {
    return (String[]) clientIDMap.keySet().toArray(Pooler.EMPTY_STRING_ARRAY);
  }


  public static boolean isConnectedClientContext(String context)
  {
    return clientMap.containsKey(context);
  }

  public static ServerInfo[] discoverServers(int discoveryTimeout)
  {
    java.util.ArrayList servers = new java.util.ArrayList();
    if (Sage.DBG) System.out.println("Sending out discovery packets to find SageTV Servers...");
    java.net.DatagramSocket sock = null;
    try
    {
      // Try on the encoder discovery port which is less likely to be in use
      try
      {
        sock = new java.net.DatagramSocket(Sage.getInt("encoding_discovery_port", 8271));
      }
      catch (java.net.BindException be2)
      {
        // Just create it wherever
        sock = new java.net.DatagramSocket();
      }
      java.net.DatagramPacket pack = new java.net.DatagramPacket(new byte[512], 512);
      byte[] data = pack.getData();
      data[0] = 'S';
      data[1] = 'T';
      data[2] = 'V';
      data[3] = Version.MAJOR_VERSION;
      data[4] = Version.MINOR_VERSION;
      data[5] = Version.MICRO_VERSION;
      pack.setLength(32);
      sock.setBroadcast(true);
      // Find the broadcast address for this subnet.
      //			String myIP = SageTV.api("GetLocalIPAddress", new Object[0]).toString();
      //			int lastIdx = myIP.lastIndexOf('.');
      //			myIP = myIP.substring(0, lastIdx) + ".255";
      pack.setAddress(java.net.InetAddress.getByName("255.255.255.255"));
      pack.setPort(Sage.getInt("discovery_port", 8270));
      sock.send(pack);
      long startTime = Sage.eventTime();
      do
      {
        int currTimeout = (int)Math.max(1, (startTime + discoveryTimeout) - Sage.eventTime());
        sock.setSoTimeout(currTimeout);
        sock.receive(pack);
        if (pack.getLength() >= 9)
        {
          if (Sage.DBG) System.out.println("Discovery packet received:" + pack + " from " + pack.getSocketAddress());
          ServerInfo si = new ServerInfo();
          if (data[0] == 'S' && data[1] == 'T' && data[2] == 'V')
          {
            // Check version
            si.majorVer = data[3];
            si.minorVer = data[4];
            si.buildVer = data[5];
            if (Sage.DBG) System.out.println("Server info " + si.majorVer + "." + si.minorVer + "." + si.buildVer);
            if (si.majorVer > Sage.CLIENT_COMPATIBLE_MAJOR_VERSION || (si.majorVer == Sage.CLIENT_COMPATIBLE_MAJOR_VERSION &&
                (si.minorVer > Sage.CLIENT_COMPATIBLE_MINOR_VERSION || (si.minorVer == Sage.CLIENT_COMPATIBLE_MINOR_VERSION &&
                si.buildVer >= Sage.CLIENT_COMPATIBLE_MICRO_VERSION))))
            {
              si.port = ((data[6] & 0xFF) << 8) + (data[7] & 0xFF);
              int descLength = (data[8] & 0xFF);
              si.name = new String(data, 9, descLength, Sage.I18N_CHARSET);
              si.address = pack.getAddress().getHostAddress();
              if (Sage.DBG) System.out.println("Added server info:" + si);
              servers.add(si);
            }
          }
        }
      } while (true);//startTime + discoveryTimeout > Sage.eventTime());
    }
    catch (Exception e)
    {
      //			System.out.println("Error discovering servers:" + e);
    }
    finally
    {
      if (sock != null)
      {
        try
        {
          sock.close();
        }catch (Exception e){}
        sock = null;
      }
    }
    return (ServerInfo[]) servers.toArray(new ServerInfo[0]);
  }

  public static Object clientEvaluateAction(String uiContext, String methodName, Object[] methodArgs) throws Exception
  {
    return clientEvaluateAction(uiContext, null, methodName, methodArgs);
  }

  public static String translateClientNameForID(String clientId) {
    return clientId;
  }

  public static Object clientEvaluateAction(String uiContext, String uiController, String methodName, Object[] methodArgs) throws Exception
  {
    NetworkClient neddy = (NetworkClient) clientMap.get(uiContext);
    if (neddy == null)
    {
      return null;
    }
    SageTVConnection listy = neddy.serverNotifier;
    if (listy != null)
    {
      // We can't hold this lock because if the call fails we'll have the connection lock during
      // cleanup which can cause a deadlock
      //synchronized (listy)
      {
        if (neddy.alive)
        {
          return listy.requestAction(methodName, methodArgs);
        }
      }
    }
    return null;
  }

  public int getConnectionState()
  {
    return connectionState;
  }

  private long createTime;
  private SageTVConnection clientListener;
  private SageTVConnection serverNotifier;
  private boolean alive;
  private String clientName;
  private String clientIP;
  private String clientID; // for embedded only
  private boolean localClient;
  private static final java.util.Map clientMap = java.util.Collections.synchronizedMap(new java.util.HashMap());
  private static final java.util.Map clientIDMap = new java.util.HashMap();
  private static java.util.Vector pendingPropXfers = null;
  private int connectionState = CS_STATE_NOT_INITIALIZED;

  /* Track the optional capabilities of the clients to dynamically alter the core */
  private Map<String, String> capabilities = new HashMap<String, String>();

  public static class ServerInfo
  {
    public byte majorVer;
    public byte minorVer;
    public byte buildVer;
    public int port;
    public String address;
    public String name;
    public boolean ready;
    public String toString()
    {
      return name + ",V" + majorVer + "." + minorVer + "." + buildVer + "," + address;
    }
  }
}
