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
public class AlbumGrouperMediaFileNode extends GrouperMediaFileNode
{

  /** Creates a new instance of AlbumGrouperMediaFileNode */
  public AlbumGrouperMediaFileNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, String inGroupType, Object inGroupObject, java.util.Vector inFiles)
  {
    super(inSource, inParent, inGroupLabel, inGroupType, inGroupObject, inFiles);
  }

  protected void verifyCache()
  {
    if (cachedKids != null) return;
    // Organize our list of files by album
    java.util.Map albumMap = new java.util.HashMap();
    java.util.Vector newCache = new java.util.Vector();
    for (int i = 0; i < descendants.size(); i++)
    {
      MediaFile currMF = (MediaFile) descendants.get(i);
      if (!mySource.passesFilter(DATATYPE_MEDIAFILE, currMF))
        continue;
      Album currAlbum = Wizard.getInstance().getCachedAlbumForMediaFile(currMF);
      if (currAlbum == null)
        continue;
      java.util.Vector albumKids = (java.util.Vector) albumMap.get(currAlbum);
      if (albumKids == null)
      {
        albumMap.put(currAlbum, albumKids = new java.util.Vector());
        albumKids.add(currMF);
        newCache.add(new AlbumMediaFileNode(mySource, this, currAlbum, albumKids));
      }
      else
        albumKids.add(currMF);
    }

    cachedKids = (AlbumMediaFileNode[]) newCache.toArray(new AlbumMediaFileNode[0]);
    mySource.sortData(cachedKids);
  }
}
