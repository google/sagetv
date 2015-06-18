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
public class GenreGrouperMediaFileNode extends GrouperMediaFileNode
{

  /**
   * Creates a new instance of GenreGrouperMediaFileNode
   */
  public GenreGrouperMediaFileNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, java.util.Vector inDescendants, boolean groupKidsByArtist)
  {
    super(inSource, inParent, inGroupLabel, DATATYPE_VIRTUAL, null, inDescendants);
    this.groupKidsByArtist = groupKidsByArtist;
  }

  protected void verifyCache()
  {
    if (cachedKids != null) return;
    // Organize our list of files by genre
    java.util.Map genreMap = new java.util.HashMap();
    java.util.Vector newCache = new java.util.Vector();
    String unknown = Sage.rez("Unknown");
    for (int i = 0; i < descendants.size(); i++)
    {
      MediaFile currMF = (MediaFile) descendants.get(i);
      if (!mySource.passesFilter(DATATYPE_MEDIAFILE, currMF))
        continue;
      Show s = currMF.getShow();
      if (s == null)
        continue;
      String currGenre = s.getCategory();
      if (currGenre == null || currGenre.length() == 0)
        currGenre = unknown;
      String lcGenre = currGenre.toLowerCase();
      java.util.Vector genreKids = (java.util.Vector) genreMap.get(lcGenre);
      if (genreKids == null)
      {
        genreMap.put(lcGenre, genreKids = new java.util.Vector());
        genreKids.add(currMF);
        if (groupKidsByArtist)
          newCache.add(new RoleGrouperMediaFileNode(mySource, this, currGenre, DATATYPE_CATEGORY, currGenre, genreKids, Show.ARTIST_ROLE, true));
        else
          newCache.add(new SimpleGrouperMediaFileNode(mySource, this, currGenre, DATATYPE_CATEGORY, currGenre, genreKids));
      }
      else
        genreKids.add(currMF);
    }

    cachedKids = (BasicMediaNode[]) newCache.toArray(new BasicMediaNode[0]);
    mySource.sortData(cachedKids);

  }

  private boolean groupKidsByArtist;
}
