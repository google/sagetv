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
package sage.vfs;

import sage.*;
/**
 *
 * @author Narflex
 */
public class PlaylistGrouperMediaNode extends GrouperMediaNode
{

  /** Creates a new instance of SimpleGrouperMediaFileNode */
  public PlaylistGrouperMediaNode(BasicMediaSource inSource, BasicMediaNode inParent, sage.Playlist inGroupObject, boolean showKids)
  {
    super(inSource, inParent, inGroupObject.getName(), DATATYPE_PLAYLIST, inGroupObject);
    myPlaylist = inGroupObject;
    this.showKids = showKids;
  }

  public static PlaylistGrouperMediaNode[] buildNodes(BasicMediaSource inSource, BasicMediaNode inParent, sage.Playlist[] pls)
  {
    PlaylistGrouperMediaNode[] rv = new PlaylistGrouperMediaNode[pls.length];
    for (int i = 0; i < rv.length; i++)
    {
      rv[i] = new PlaylistGrouperMediaNode(inSource, inParent, pls[i], true);
    }
    return rv;
  }

  public synchronized void refresh()
  {
    cachedKids = null;
    super.refresh();
  }

  public Object getThumbnail(Object uiContext)
  {
    ResourceLoadListener loader = (uiContext instanceof ResourceLoadListener) ? ((ResourceLoadListener) uiContext) : null;
    return (loader == null || loader.getUIMgr() == null) ? "folder.png" : loader.getUIMgr().get("ui/default_folder_icon", "folder.png");
  }

  // No filtering or sorting for playlist displays!!
  protected void verifyCache()
  {
    if (cachedKids != null) return;
    java.util.Vector newCache = new java.util.Vector();
    for (int i = 0; showKids && i < myPlaylist.getNumSegments(); i++)
    {
      int currSegType = myPlaylist.getSegmentType(i);
      if (currSegType == sage.Playlist.AIRING_SEGMENT)
      {
        sage.MediaFile mf = Wizard.getInstance().getFileForAiring((sage.Airing) myPlaylist.getSegment(i));
        if (mf != null)
          newCache.add(new BrowserMediaFileNode(mySource, this, mf));
      }
      else if (currSegType == sage.Playlist.ALBUM_SEGMENT)
        newCache.add(new AlbumMediaFileNode(mySource, this, (sage.Album) myPlaylist.getSegment(i), new java.util.Vector()));
      else if (currSegType == sage.Playlist.PLAYLIST_SEGMENT)
        newCache.add(new PlaylistGrouperMediaNode(mySource, this, (sage.Playlist) myPlaylist.getSegment(i), false));
    }

    cachedKids = (MediaNode[]) newCache.toArray(new MediaNode[0]);
    // We don't do sorting for playlist views since we should always see them in playlist order!
    //mySource.sortData(cachedKids);
  }

  public synchronized MediaNode[] getChildren()
  {
    verifyCache();
    return cachedKids;
  }

  public boolean isHierarchyRealized()
  {
    return true;
  }

  public java.util.Collection getFinalDescendants()
  {
    return showKids ? java.util.Arrays.asList(myPlaylist.getSegments()) : null;
  }

  private sage.Playlist myPlaylist;
  protected MediaNode[] cachedKids;
  private boolean showKids;
}
