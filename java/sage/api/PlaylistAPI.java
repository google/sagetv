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

import sage.*;

/**
 * Calls for creating, editing, removing and querying playlists in the system
 */
public class PlaylistAPI {
  private PlaylistAPI() {}

  private static boolean isNetworkedPlaylistCall(Catbert.FastStack stack, int depth)
  {
    Object plist = stack.peek(depth);
    if (Sage.client && plist instanceof Playlist)
    {
      Playlist p = (Playlist) plist;
      return p.getID() > 0;
    }
    return false;
  }

  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Playlist", "AddToPlaylist", 2, new String[] { "Playlist", "NewItem" })
    {
      /**
       * Adds the specified item to this Playlist. The item may be either an Airing, Album, MediaFile or another Playlist.
       * @param Playlist the Playlist object to add the new item to
       * @param NewItem the new item to add to the Playlist; must be an Airing, Album, MediaFile or Playlist
       *
       * @declaration public void AddToPlaylist(Playlist Playlist, Object NewItem);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (isNetworkedPlaylistCall(stack, 1))
        {
          return makeNetworkedCall(stack);
        }
        Object o = stack.pop();
        Playlist p = getPlaylist(stack);
        // Even w/out playlist permission; users are still allowed to modify the now playing list in their UI since that's a harmless change
        if (!Permissions.hasPermission(Permissions.PERMISSION_PLAYLIST, stack.getUIMgr()) && p != stack.getUIMgrSafe().getVideoFrame().getNowPlayingList())
          return null;
        if (o instanceof Playlist)
          p.addToPlaylist((Playlist)o);
        else if (o instanceof Album)
          p.addToPlaylist((Album)o);
        else if (o instanceof MediaFile)
        {
          if (p.getID() == 0 && ((MediaFile) o).getGeneralType() == MediaFile.MEDIAFILE_LOCAL_PLAYBACK)
            p.addToPlaylist((MediaFile)o);
          else
            p.addToPlaylist(((MediaFile)o).getContentAiring());
        }
        else if (o instanceof Airing)
          p.addToPlaylist((Airing) o);
        // If we're at the end of the now playing list and we've added stuff, then kick the VF to notice it
        if (stack.getUIMgrSafe() != null && p == stack.getUIMgrSafe().getVideoFrame().getNowPlayingList())
          stack.getUIMgrSafe().getVideoFrame().kick();
        sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYLIST_MODIFIED,
            new Object[] { sage.plugin.PluginEventManager.VAR_PLAYLIST, p,
            sage.plugin.PluginEventManager.VAR_UICONTEXT, (stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null) });
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "GetName", 1, new String[] { "Playlist" })
    {
      /**
       * Gets the name of the specified Playlist
       * @param Playlist the Playlist object
       * @return the name of the specified Playlist
       *
       * @declaration public String GetName(Playlist Playlist);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Playlist p = getPlaylist(stack);
        return p == null ? "" : p.getName();
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "GetNumberOfPlaylistItems", 1, new String[] { "Playlist" })
    {
      /**
       * Gets the number of items in the specified Playlist
       * @param Playlist the Playlist object
       * @return the number of items in the specified Playlist
       *
       * @declaration public int GetNumberOfPlaylistItems(Playlist Playlist);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Playlist p = getPlaylist(stack);
        return new Integer(p == null ? 0 : p.getNumSegments());
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "GetPlaylistItemAt", 2, new String[] { "Playlist","Index" })
    {
      /**
       * Gets the item in this Playlist at the specified index
       * @param Playlist the Playlist object
       * @param Index the 0-based index into the playlist to get the item from
       * @return the item at the specified index in the Playlist; this will be an Airing, Album, Playlist or null
       *
       * @declaration public Object GetPlaylistItemAt(Playlist Playlist, int Index);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int x = getInt(stack);
        Playlist p = getPlaylist(stack);
        return (p == null) ? null : p.getSegment(x);
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "GetPlaylistItemTypeAt", 2, new String[] { "Playlist","Index" })
    {
      /**
       * Gets the type of item in the Playlist at the specified index
       * @param Playlist the Playlist object
       * @param Index the 0-based index into the Playlist to get the item type for
       * @return the type of item at the specified index in the Playlist; one of "Airing", "Album", "Playlist" or "" ("Airing" is used for MediaFile items), "MediaFile" will be returned for temporary MediaFile objects that are not in the database
       *
       * @declaration public String GetPlaylistItemTypeAt(Playlist Playlist, int Index);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int x = getInt(stack);
        Playlist p = getPlaylist(stack);
        if (p == null) return "";
        x = p.getSegmentType(x);
        if (x == Playlist.AIRING_SEGMENT)
          return "Airing";
        else if (x == Playlist.ALBUM_SEGMENT)
          return "Album";
        else if (x == Playlist.PLAYLIST_SEGMENT)
          return "Playlist";
        else if (x == Playlist.TEMPMEDIAFILE_SEGMENT)
          return "MediaFile";
        else
          return "";
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "GetPlaylistItems", 1, new String[] { "Playlist" })
    {
      /**
       * Gets the list of items in the specified Playlist
       * @param Playlist the Playlist object
       * @return a list of the items in the specified Playlist
       *
       * @declaration public Object[] GetPlaylistItems(Playlist Playlist);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Playlist p = getPlaylist(stack);
        return p == null ? null : p.getSegments();
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "InsertIntoPlaylist", 3, new String[] { "Playlist","InsertIndex","NewItem" })
    {
      /**
       * Inserts a new item into the specified Playlist at the specified position.
       * @param Playlist the Playlist object to add the new item to
       * @param InsertIndex the 0-based index that the new item should be inserted at
       * @param NewItem the new item to insert into the Playlist; must be an Airing, Album, MediaFile or Playlist
       *
       * @declaration public void InsertIntoPlaylist(Playlist Playlist, int InsertIndex, Object NewItem);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (isNetworkedPlaylistCall(stack, 2))
        {
          return makeNetworkedCall(stack);
        }
        Object o = stack.pop();
        int x = getInt(stack);
        Playlist p = getPlaylist(stack);
        if (p == null) return null;
        // Even w/out playlist permission; users are still allowed to modify the now playing list in their UI since that's a harmless change
        if (!Permissions.hasPermission(Permissions.PERMISSION_PLAYLIST, stack.getUIMgr()) && p != stack.getUIMgrSafe().getVideoFrame().getNowPlayingList())
          return null;
        if (o instanceof Playlist)
          p.insertIntoPlaylist((Playlist)o, x);
        else if (o instanceof Album)
          p.insertIntoPlaylist((Album)o, x);
        else if (o instanceof MediaFile)
        {
          if (p.getID() == 0 && ((MediaFile) o).getGeneralType() == MediaFile.MEDIAFILE_LOCAL_PLAYBACK)
            p.insertIntoPlaylist((MediaFile)o, x);
          else
            p.insertIntoPlaylist(((MediaFile)o).getContentAiring(), x);
        }
        else if (o instanceof Airing)
          p.insertIntoPlaylist((Airing) o, x);
        // If we're at the end of the now playing list and we've added stuff, then kick the VF to notice it
        if (stack.getUIMgrSafe() != null && p == stack.getUIMgrSafe().getVideoFrame().getNowPlayingList())
          stack.getUIMgrSafe().getVideoFrame().kick();
        sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYLIST_MODIFIED,
            new Object[] { sage.plugin.PluginEventManager.VAR_PLAYLIST, p,
            sage.plugin.PluginEventManager.VAR_UICONTEXT, (stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null) });
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "MovePlaylistItemUp", 2, new String[] { "Playlist","Index" })
    {
      /**
       * Swaps the position of the item at the specified index in the Playlist with the item at the position (Index - 1)
       * @param Playlist the Playlist object
       * @param Index the position of the item to move up one in the playlist
       *
       * @declaration public void MovePlaylistItemUp(Playlist Playlist, int Index);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (isNetworkedPlaylistCall(stack, 1))
        {
          return makeNetworkedCall(stack);
        }
        int x = getInt(stack);
        Playlist p = getPlaylist(stack);
        if (p == null) return null;
        // Even w/out playlist permission; users are still allowed to modify the now playing list in their UI since that's a harmless change
        if (!Permissions.hasPermission(Permissions.PERMISSION_PLAYLIST, stack.getUIMgr()) && p != stack.getUIMgrSafe().getVideoFrame().getNowPlayingList())
          return null;
        p.movePlaylistSegment(x, true);
        sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYLIST_MODIFIED,
            new Object[] { sage.plugin.PluginEventManager.VAR_PLAYLIST, p,
            sage.plugin.PluginEventManager.VAR_UICONTEXT, (stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null) });
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "MovePlaylistItemDown", 2, new String[] { "Playlist","Index" })
    {
      /**
       * Swaps the position of the item at the specified index in the Playlist with the item at the position (Index + 1)
       * @param Playlist the Playlist object
       * @param Index the position of the item to move down one in the playlist
       *
       * @declaration public void MovePlaylistItemDown(Playlist Playlist, int Index);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (isNetworkedPlaylistCall(stack, 1))
        {
          return makeNetworkedCall(stack);
        }
        int x = getInt(stack);
        Playlist p = getPlaylist(stack);
        if (p == null) return null;
        // Even w/out playlist permission; users are still allowed to modify the now playing list in their UI since that's a harmless change
        if (!Permissions.hasPermission(Permissions.PERMISSION_PLAYLIST, stack.getUIMgr()) && p != stack.getUIMgrSafe().getVideoFrame().getNowPlayingList())
          return null;
        p.movePlaylistSegment(x, false);
        sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYLIST_MODIFIED,
            new Object[] { sage.plugin.PluginEventManager.VAR_PLAYLIST, p,
            sage.plugin.PluginEventManager.VAR_UICONTEXT, (stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null) });
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "RemovePlaylistItem", 2, new String[] { "Playlist","Item" })
    {
      /**
       * Removes the specified item from the Playlist. If this item appears in the Playlist more than once, only the first occurrence will be removed.
       * @param Playlist the Playlist object
       * @param Item the item to remove from the Playlist, must be an Airing, MediaFile, Album or Playlist
       *
       * @declaration public void RemovePlaylistItem(Playlist Playlist, Object Item);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (isNetworkedPlaylistCall(stack, 1))
        {
          return makeNetworkedCall(stack);
        }
        Object x = stack.pop();
        Playlist p = getPlaylist(stack);
        if (x instanceof MediaFile)
        {
          if (p.getID() != 0 || ((MediaFile) x).getGeneralType() != MediaFile.MEDIAFILE_LOCAL_PLAYBACK)
            x = ((MediaFile) x).getContentAiring();
        }
        if (p == null) return null;
        // Even w/out playlist permission; users are still allowed to modify the now playing list in their UI since that's a harmless change
        if (!Permissions.hasPermission(Permissions.PERMISSION_PLAYLIST, stack.getUIMgr()) && p != stack.getUIMgrSafe().getVideoFrame().getNowPlayingList())
          return null;
        p.removeFromPlaylist(x);
        sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYLIST_MODIFIED,
            new Object[] { sage.plugin.PluginEventManager.VAR_PLAYLIST, p,
            sage.plugin.PluginEventManager.VAR_UICONTEXT, (stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null) });
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "RemovePlaylistItemAt", 2, new String[] { "Playlist","ItemIndex" })
    {
      /**
       * Removes the specified item at the specified index from the Playlist.
       * @param Playlist the Playlist object
       * @param ItemIndex the index of the item to remove from the Playlist
       *
       * @declaration public void RemovePlaylistItemAt(Playlist Playlist, int ItemIndex);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (isNetworkedPlaylistCall(stack, 1))
        {
          return makeNetworkedCall(stack);
        }
        int idx = getInt(stack);
        Playlist p = getPlaylist(stack);
        if (p == null) return null;
        // Even w/out playlist permission; users are still allowed to modify the now playing list in their UI since that's a harmless change
        if (!Permissions.hasPermission(Permissions.PERMISSION_PLAYLIST, stack.getUIMgr()) && p != stack.getUIMgrSafe().getVideoFrame().getNowPlayingList())
          return null;
        p.removeFromPlaylist(idx);
        sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYLIST_MODIFIED,
            new Object[] { sage.plugin.PluginEventManager.VAR_PLAYLIST, p,
            sage.plugin.PluginEventManager.VAR_UICONTEXT, (stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null) });
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "SetName", 2, new String[] { "Playlist","Name" })
    {
      /**
       * Sets the name for this Playlist
       * @param Playlist the Playlist objecxt
       * @param Name the name to set for this Plyalist
       *
       * @declaration public void SetName(Playlist Playlist, String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (isNetworkedPlaylistCall(stack, 1))
        {
          return makeNetworkedCall(stack);
        }
        String s = getString(stack);
        Playlist p = getPlaylist(stack);
        if (p == null) return null;
        // Even w/out playlist permission; users are still allowed to modify the now playing list in their UI since that's a harmless change
        if (!Permissions.hasPermission(Permissions.PERMISSION_PLAYLIST, stack.getUIMgr()) && p != stack.getUIMgrSafe().getVideoFrame().getNowPlayingList())
          return null;
        p.setName(s);
        sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYLIST_MODIFIED,
            new Object[] { sage.plugin.PluginEventManager.VAR_PLAYLIST, p,
            sage.plugin.PluginEventManager.VAR_UICONTEXT, (stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null) });
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "IsPlaylistObject", 1, new String[] { "Playlist" })
    {
      /**
       * Returns true if the passed in argument is a Playlist object
       * @param Playlist the object to test to see if it is a Playlist object
       * @return true if the passed in argument is a Playlist object, false otherwise
       *
       * @declaration public boolean IsPlaylistObject(Object Playlist);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object p = stack.pop();
        if (p instanceof sage.vfs.MediaNode)
          p = ((sage.vfs.MediaNode) p).getDataObject();
        return Boolean.valueOf(p instanceof Playlist && (Wizard.getInstance().getPlaylistForID(((Playlist)p).getID()) != null ||
            (stack.getUIMgr() != null && stack.getUIMgr().getVideoFrame().getNowPlayingList() == p)));
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "GetPlaylists")
    {
      /**
       * Gets a list of all of the Playlists in the database
       * @return a list of all of the Playlists in the database
       *
       * @declaration public Playlist[] GetPlaylists();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getPlaylists();
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "RemovePlaylist", 1, new String[] {"Playlist"})
    {
      /**
       * Removes a specified Playlist from the databse completely. The files in the Playlist will NOT be removed.
       * @param Playlist the Playlist object to remove
       *
       * @declaration public void RemovePlaylist(Playlist Playlist);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (isNetworkedPlaylistCall(stack, 0))
        {
          return makeNetworkedCall(stack);
        }
        Playlist p = getPlaylist(stack);
        if (p != null && p.getID() == 0)
        {
          // Even w/out playlist permission; users are still allowed to modify the now playing list in their UI since that's a harmless change
          p.clear();
        }
        else if (Permissions.hasPermission(Permissions.PERMISSION_PLAYLIST, stack.getUIMgr()))
        {
          Wizard.getInstance().removePlaylist(p);
          sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYLIST_REMOVED,
              new Object[] { sage.plugin.PluginEventManager.VAR_PLAYLIST, p,
              sage.plugin.PluginEventManager.VAR_UICONTEXT, (stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null) });
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "DoesPlaylistHaveVideo", 1, new String[] {"Playlist"})
    {
      /**
       * Returns true if the specified Playlist contains any video files, false otherwise
       * @param Playlist the Playlist object
       * @return true if the specified Playlist contains any video files, false otherwise
       * @since 5.1
       *
       * @declaration public boolean DoesPlaylistHaveVideo(Playlist Playlist);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Playlist p = getPlaylist(stack);
        if (p != null && !p.isMusicPlaylist())
          return Boolean.TRUE;
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "AddPlaylist", 1, new String[] {"Name"}, true)
    {
      /**
       * Creates a new Playlist object
       * @param Name the name for the new Playlist
       * @return the newly created Playlist
       *
       * @declaration public Playlist AddPlaylist(String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String name = getString(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_PLAYLIST, stack.getUIMgr()))
        {
          Playlist rv = Wizard.getInstance().addPlaylist(name);
          sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYLIST_ADDED,
              new Object[] { sage.plugin.PluginEventManager.VAR_PLAYLIST, rv,
              sage.plugin.PluginEventManager.VAR_UICONTEXT, (stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null) });
          return rv;
        }
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "GetNowPlayingList")
    {
      /**
       * Returns the 'Now Playing' playlist. This can be used as a local, client-specific playlist which
       * songs can be added to and then played as a temporary set of songs. i.e. usually playlists are shared between
       * all of the clients that are connected to a SageTV system, but this one is NOT shared
       * @return the Playlist object to use as the 'Now Playing' list
       *
       * @since 5.1
       *
       * @declaration public Playlist GetNowPlayingList();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr != null)
          return uiMgr.getVideoFrame().getNowPlayingList();
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "GetPlaylistProperty", 2, new String[] { "Playlist", "PropertyName" })
    {
      /**
       * Returns a property value for a specified Playlist. This must have been set using SetPlaylistProperty.
       * Returns the empty string when the property is undefined.
       * @param Playlist the Playlist object
       * @param PropertyName the name of the property
       * @return the property value for the specified Playlist, or the empty string if it is not defined
       * @since 7.0
       *
       * @declaration public String GetPlaylistProperty(Playlist Playlist, String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String prop = getString(stack);
        Playlist p = getPlaylist(stack);
        return (p == null) ? "" : p.getProperty(prop);
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "SetPlaylistProperty", 3, new String[] { "Playlist", "PropertyName", "PropertyValue" }, true)
    {
      /**
       * Sets a property for this Playlist. This can be any name/value combination (but the name cannot be null). If the value is null;
       * then the specified property will be removed from this Playlist. This only impacts the return values from GetPlaylistProperty and has no other side effects.
       * @param Playlist the Playlist object
       * @param PropertyName the name of the property
       * @param PropertyValue the value of the property
       * @since 7.0
       *
       * @declaration public void SetPlaylistProperty(Playlist Playlist, String PropertyName, String PropertyValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String propV = getString(stack);
        String propN = getString(stack);
        Playlist p = getPlaylist(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_PLAYLIST, stack.getUIMgr()))
        {
          p.setProperty(propN, propV);
          sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.PLAYLIST_MODIFIED,
              new Object[] { sage.plugin.PluginEventManager.VAR_PLAYLIST, p,
              sage.plugin.PluginEventManager.VAR_UICONTEXT, (stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null) });
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Playlist", "GetPlaylistProperties", 1, new String[] { "Playlist" })
    {
      /**
       * Returns a java.util.Properties object that has all of the user-set properties for this Playlist in it.
       * @param Playlist the Playlist object
       * @return a java.util.Properties object that has all of the user-set properties for this Playlist in it; this is a copy of the original one so it is safe to modify it
       * @since 7.1
       *
       * @declaration public java.util.Properties GetPlaylistProperties(Playlist Playlist);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Playlist p = getPlaylist(stack);
        return (p == null) ? new java.util.Properties() : p.getProperties();
      }});
  }
}
