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
package sage.plugin;

import java.util.Collections;

/**
 * @author Narflex
 */
public class PluginEventManager implements sage.SageTVPluginRegistry, Runnable
{
  public static final String MEDIA_FILE_IMPORTED = "MediaFileImported";
  public static final String IMPORTING_COMPLETED = "ImportingCompleted";
  public static final String IMPORTING_STARTED = "ImportingStarted";
  public static final String RECORDING_COMPLETED = "RecordingCompleted";
  public static final String RECORDING_STARTED = "RecordingStarted";
  public static final String RECORDING_SEGMENT_ADDED = "RecordingSegmentAdded";
  public static final String RECORDING_STOPPED = "RecordingStopped";
  public static final String ALL_PLUGINS_LOADED = "AllPluginsLoaded";
  public static final String RECORDING_SCHEDULE_CHANGED = "RecordingScheduleChanged";
  public static final String CONFLICT_STATUS_CHANGED = "ConflictStatusChanged";
  public static final String SYSTEM_MESSAGE_POSTED = "SystemMessagePosted";
  public static final String EPG_UPDATE_COMPLETED = "EPGUpdateCompleted";
  public static final String MEDIA_FILE_REMOVED = "MediaFileRemoved";
  public static final String PLAYBACK_STOPPED = "PlaybackStopped";
  public static final String PLAYBACK_FINISHED = "PlaybackFinished";
  public static final String PLAYBACK_STARTED = "PlaybackStarted";
  public static final String FAVORITE_ADDED = "FavoriteAdded";
  public static final String FAVORITE_MODIFIED = "FavoriteModified";
  public static final String FAVORITE_REMOVED = "FavoriteRemoved";
  public static final String PLAYLIST_ADDED = "PlaylistAdded";
  public static final String PLAYLIST_MODIFIED = "PlaylistModified";
  public static final String PLAYLIST_REMOVED = "PlaylistRemoved";
  public static final String CLIENT_CONNECTED = "ClientConnected";
  public static final String CLIENT_DISCONNECTED = "ClientDisconnected";
  public static final String PLUGIN_STARTED = "PluginStarted";
  public static final String PLUGIN_STOPPED = "PluginStopped";
  public static final String PLAYBACK_PAUSED = "PlaybackPaused";
  public static final String PLAYBACK_RESUMED = "PlaybackResumed";
  public static final String SYSTEM_ALERT_LEVEL_RESET = "SystemAlertLevelReset";
  public static final String SYSTEM_MESSAGE_REMOVED = "SystemMessageRemoved";
  public static final String ALL_SYSTEM_MESSAGES_REMOVED = "AllSystemMessagesRemoved";
  public static final String PLAYBACK_RATECHANGE = "PlaybackRateChange";
  public static final String PLAYBACK_SEEK = "PlaybackSeek";
  public static final String PLAYBACK_SEGMENTCHANGE = "PlaybackSegmentChange";
  public static final String WATCHED_STATE_CHANGED = "WatchedStateChanged";
  public static final String MANUAL_RECORD_ADDED = "ManualRecordAdded";
  public static final String MANUAL_RECORD_MODIFIED = "ManualRecordModified";
  public static final String MANUAL_RECORD_REMOVED = "ManualRecordRemoved";

  public static final String VAR_MEDIAFILE = "MediaFile";
  public static final String VAR_SYSTEMMESSAGE = "SystemMessage";
  public static final String VAR_UICONTEXT = "UIContext";
  public static final String VAR_PLAYLIST = "Playlist";
  public static final String VAR_FAVORITE = "Favorite";
  public static final String VAR_AIRING = "Airing";
  public static final String VAR_DURATION = "Duration";
  public static final String VAR_EXPIRATION = "Expiration";
  public static final String VAR_MEDIATIME = "MediaTime";
  public static final String VAR_CHAPTERNUM = "ChapterNum";
  public static final String VAR_TITLENUM = "TitleNum";
  public static final String VAR_IPADDRESS = "IPAddress";
  public static final String VAR_MACADDRESS = "MACAddress";
  public static final String VAR_REASON = "Reason";
  public static final String VAR_PLUGIN = "Plugin";
  public static final String VAR_FULLREINDEX = "FullReindex";
  public static final String VAR_PLAYBACKRATE = "PlaybackRate";
  public static final String VAR_MEDIASEGMENT = "MediaSegment";
  private static PluginEventManager chosenOne;
  private static final Object chosenOneLock = new Object();

  public static PluginEventManager getInstance() {
    if (chosenOne == null) {
      synchronized (chosenOneLock) {
        if (chosenOne == null) {
          chosenOne = new PluginEventManager();
        }
      }
    }
    return chosenOne;
  }

  // Call this method to subscribe to a specific event
  public void eventSubscribe(sage.SageTVEventListener listener, String eventName)
  {
    synchronized (listenerMap)
    {
      java.util.Vector currListeners = (java.util.Vector) listenerMap.get(eventName);
      if (currListeners == null)
        listenerMap.put(eventName, currListeners = new java.util.Vector());
      if (!currListeners.contains(listener))
        currListeners.add(listener);
    }
  }

  // Call this method to unsubscribe from a specific event
  public void eventUnsubscribe(sage.SageTVEventListener listener, String eventName)
  {
    synchronized (listenerMap)
    {
      java.util.Vector currListeners = (java.util.Vector) listenerMap.get(eventName);
      if (currListeners != null)
        currListeners.remove(listener);
    }
  }

  // This is used when a plugin is disabled in case it doesn't cleanup its own subscriptions properly
  public void fullUnsubscribe(sage.SageTVEventListener listener)
  {
    synchronized (listenerMap)
    {
      java.util.Iterator walker = listenerMap.values().iterator();
      while (walker.hasNext())
      {
        java.util.Vector currVec = (java.util.Vector) walker.next();
        if (currVec != null)
          currVec.remove(listener);
      }
    }
  }

  // This is for making it easier for us to write code to post events internally
  public static void postEvent(String eventName, Object[] eventVars)
  {
    if (eventVars != null && (eventVars.length/2)*2 != eventVars.length)
      throw new InternalError("postEvent eventVars must be of length 2 array!!!");
    java.util.HashMap varMap = new java.util.HashMap();
    for (int i = 0; eventVars != null && i < eventVars.length; i++)
    {
      String varName = eventVars[i++].toString();
      varMap.put(varName, eventVars[i]);
    }
    getInstance().postEvent(eventName, varMap);
    if (sage.Sage.client)
    {
      // See if we also want to post this event to the server, if there's a UIContext in the variable map then we do
      try
      {
        if (varMap.get(VAR_UICONTEXT) != null && eventName.startsWith("Playback"))
        {
          // Make sure it's not a local media file event
          sage.MediaFile mf = (sage.MediaFile) varMap.get(VAR_MEDIAFILE);
          if (mf != null && mf.getGeneralType() != sage.MediaFile.MEDIAFILE_LOCAL_PLAYBACK)
          {
            java.util.HashMap newVarMap = new java.util.HashMap();
            newVarMap.putAll(varMap);
            newVarMap.put(VAR_UICONTEXT, "/" + sage.NetworkClient.getCL().getSocket().getLocalAddress().getHostAddress() + ":" +
                sage.NetworkClient.getCL().getSocket().getLocalPort());
            sage.NetworkClient.getSN().sendEvent(eventName, newVarMap);
          }
        }
      }
      catch (Exception e)
      {
        if (sage.Sage.DBG) System.out.println("ERROR sending event to server of:" + e);
        e.printStackTrace();
      }
    }
  }

  // This will post the event asynchronously to SageTV's plugin event queue and return immediately
  public void postEvent(String eventName, java.util.Map eventVars)
  {
    postEvent(eventName, eventVars, false);
  }

  // This will post the event asynchronously and return immediately; unless waitUntilDone is true,
  // and then it will not return until all the subscribed plugins have received the event.
  public void postEvent(String eventName, java.util.Map eventVars, boolean waitUntilDone)
  {
    synchronized (eventQueue)
    {
      EventData evtData = new EventData(eventName, eventVars);
      eventQueue.add(evtData);
      eventQueue.notifyAll();
      while (alive && waitUntilDone && eventQueue.contains(evtData))
      {
        try{eventQueue.wait(5000);}catch(InterruptedException e){}
      }
    }
  }

  public void startup()
  {
    if (alive) return;
    alive = true;
    Thread t = new Thread(this, "PluginEventQueue");
    t.setDaemon(true);
    t.setPriority(Thread.MIN_PRIORITY);
    t.start();
  }

  /** Creates a new instance of PluginEventManager */
  private PluginEventManager()
  {
    listenerMap = new java.util.HashMap();
    eventQueue = new java.util.Vector();
    alive = false;
  }

  public void run()
  {
    while (alive)
    {
      EventData processMe = null;
      synchronized (eventQueue)
      {
        if (eventQueue.isEmpty())
        {
          try { eventQueue.wait(60000); } catch (InterruptedException e){}
          continue;
        }
        processMe = (EventData) eventQueue.firstElement();
      }
      // wrap the plugin processing code in a try/finally to avoid a deadlock
      // In the event that encounter an issue we need to make sure to remove this event from the queue
      // or else if we are set to wait, then we'll never complete
      try
      {
        // set vars to a default empty map if it's not there or else NPE
        // previously, without the above try/finally any event posted without a Map causes the plugin
        // system to deadlock
        if (processMe.vars==null)
          processMe.vars = Collections.emptyMap();
        processMe.vars = java.util.Collections.unmodifiableMap(processMe.vars);
        java.util.Vector listeners = (java.util.Vector) listenerMap.get(processMe.name);
        boolean debugEvents = sage.Sage.getBoolean("debug_core_events", false);
        if (sage.Sage.DBG && debugEvents)
          System.out.println("Core is about to process the event " + processMe.name + " vars=" + processMe.vars);
        if (listeners != null && !listeners.isEmpty())
        {
          // We convert this to an array to avoid issues where the listeners are changed while we're sending out the event
          sage.SageTVEventListener[] listies = (sage.SageTVEventListener[]) listeners.toArray(new sage.SageTVEventListener[0]);
          for (int i = 0; i < listies.length; i++)
          {
            try
            {
              if (debugEvents && sage.Sage.DBG)
                System.out.println("Core sending event " + processMe.name + " to " + listies[i] + " vars=" + processMe.vars);
              // NOTE: This blocks this thread until the plugin event completes.
              // This has a side effect of a Plugin disabling the entire event system, if it misbehaves.
              // We should probably rewrite the Plugin Event handling to use the Java 1.5 Executor Servic
              // so that we can publish events and then get a Future on which we can call
              // .get(timeout) to wait at most for the event to complete.
              listies[i].sageEvent(processMe.name, processMe.vars);
              if (debugEvents && sage.Sage.DBG)
                System.out.println("DONE Core sending event " + processMe.name + " to " + listies[i]);
            } catch (Throwable t)
            {
              if (sage.Sage.DBG) System.out.println("Exception error in plugin event listener of: " + t);
              if (sage.Sage.DBG) t.printStackTrace();
            }
          }
        }
      } finally
      {
        synchronized (eventQueue)
        {
          eventQueue.remove(0);
          eventQueue.notifyAll();
        }
      }
    }
  }

  // used internally for unit testing
  void reset() {
    if (listenerMap!=null) listenerMap.clear();
    if (eventQueue!=null) eventQueue.clear();
  }

  private java.util.Map listenerMap;
  private java.util.Vector eventQueue;
  private boolean alive;

  private static class EventData
  {
    public EventData(String inName, java.util.Map inVars)
    {
      name = inName;
      vars = inVars;
    }
    public String name;
    public java.util.Map vars;
  }
}
