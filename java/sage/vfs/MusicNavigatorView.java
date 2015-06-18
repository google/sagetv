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
public class MusicNavigatorView extends BasicMediaSource
{

  /** Creates a new instance of MusicNavigatorView */
  public MusicNavigatorView()
  {
  }

  // When we create a new VFS; we first create this class; and that will be the wrapper for the current
  // VFS we're building underneath it. All the MediaNodes inside of it will have an outer class
  // reference back here.
  public MediaNode createVFS()
  {
    return new MusicNavigatorTopNode(this);
  }

  public MediaNode createRelativeVFS(Object relativeRoot)
  {
    throw new UnsupportedOperationException("createRelativeVFS not supported!!!");
  }

  public MediaNode createCollectionVFS(java.util.Collection dataObjects)
  {
    throw new UnsupportedOperationException("createCollectionVFS not supported!!!");
  }

  public static java.util.Vector getCompilations()
  {
    Wizard wiz = Wizard.getInstance();
    Airing[] mairs = wiz.searchByExactArtist(PredefinedJEPFunction.getPersonObj(Sage.rez("Various_Artists")));
    java.util.Vector mfs = new java.util.Vector();
    for (int i = 0; i < mairs.length; i++)
    {
      MediaFile mf = wiz.getFileForAiring(mairs[i]);
      if (mf != null)
        mfs.add(mf);
    }
    return mfs;
  }

  private java.util.Vector filterList;

  public static class MusicNavigatorTopNode extends GrouperMediaNode
  {
    public MusicNavigatorTopNode(BasicMediaSource inSource)
    {
      super(inSource, null, Sage.rez("Music"), DATATYPE_VIRTUAL, null);

      java.util.Vector allMusic = new java.util.Vector(java.util.Arrays.asList(Wizard.getInstance().getFiles(DBObject.MEDIA_MASK_MUSIC, true)));
      children = new MediaNode[] {
          new GenreGrouperMediaFileNode(mySource, this, Sage.rez("Genres"), allMusic, true),
          new RoleGrouperMediaFileNode(mySource, this, Sage.rez("Artists"), DATATYPE_VIRTUAL, null, allMusic, Show.ARTIST_ROLE, true),
          new AlbumGrouperMediaFileNode(mySource, this, Sage.rez("Albums"), DATATYPE_VIRTUAL, null, allMusic),
          new SimpleGrouperMediaFileNode(mySource, this, Sage.rez("Songs"), DATATYPE_VIRTUAL, null, allMusic),
          new YearGrouperMediaFileNode(mySource, this, Sage.rez("Years"), allMusic, true),
          new AlbumGrouperMediaFileNode(mySource, this, Sage.rez("Compilations"), DATATYPE_VIRTUAL, null, getCompilations()),
          new SimpleGrouperMediaNode(mySource, this, Sage.rez("Playlists"), DATATYPE_VIRTUAL, null, PlaylistGrouperMediaNode.buildNodes(mySource, null, Wizard.getInstance().getMusicPlaylists()))
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
