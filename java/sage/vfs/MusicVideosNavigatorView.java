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

import java.util.Collection;
import sage.*;

/**
 *
 * @author Narflex
 */
public class MusicVideosNavigatorView extends BasicMediaSource
{

  /** Creates a new instance of MusicVideosNavigatorView */
  public MusicVideosNavigatorView()
  {
  }

  // When we create a new VFS; we first create this class; and that will be the wrapper for the current
  // VFS we're building underneath it. All the MediaNodes inside of it will have an outer class
  // reference back here.
  public MediaNode createVFS()
  {
    return new MusicVideosNavigatorTopNode(this, null);
  }

  public MediaNode createRelativeVFS(Object relativeRoot)
  {
    throw new UnsupportedOperationException("createRelativeVFS not supported!!!");
  }

  public MediaNode createCollectionVFS(java.util.Collection dataObjects)
  {
    throw new UnsupportedOperationException("createCollectionVFS not supported!!!");
  }

  private java.util.Vector filterList;

  public static class MusicVideosNavigatorTopNode extends GrouperMediaNode
  {
    public MusicVideosNavigatorTopNode(BasicMediaSource inSource, BasicMediaNode inParent)
    {
      super(inSource, inParent, Sage.rez("MusicVideos"), DATATYPE_VIRTUAL, null);

      java.util.Vector allMusicVideos = VFSFactory.getInstance().createCollection("musicvideos");
      children = new MediaNode[] {
          new GenreGrouperMediaFileNode(mySource, this, Sage.rez("Genres"), allMusicVideos, false),
          new TitleGrouperMediaFileNode(mySource, this, Sage.rez("Titles"), allMusicVideos),
          new YearGrouperMediaFileNode(mySource, this, Sage.rez("Years"), allMusicVideos, false),
          new RoleGrouperMediaFileNode(mySource, this, Sage.rez("Artists"), MediaNode.DATATYPE_VIRTUAL, null, allMusicVideos, Show.ARTIST_ROLE, false),
          new RoleGrouperMediaFileNode(mySource, this, Sage.rez("Directors"), MediaNode.DATATYPE_VIRTUAL, null, allMusicVideos, Show.DIRECTOR_ROLE, false),
          new StudioGrouperMediaFileNode(mySource, this, Sage.rez("Studios"), allMusicVideos),
      };
    }

    public MediaNode[] getChildren()
    {
      return children;
    }

    public boolean isHierarchyRealized()
    {
      return false;
    }

    public Collection getFinalDescendants()
    {
      return null;
    }

    private MediaNode[] children;
  }
}
