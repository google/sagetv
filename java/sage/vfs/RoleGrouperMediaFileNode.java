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
public class RoleGrouperMediaFileNode extends GrouperMediaFileNode
{
  /**
   * Creates a new instance of RoleGrouperMediaFileNode
   */
  public RoleGrouperMediaFileNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, String inGroupType, Object inGroupObject, java.util.Vector inFiles, int grouperRole,
      boolean groupKidsByAlbum)
  {
    this(inSource, inParent, inGroupLabel, inGroupType, inGroupObject, inFiles, new int[] { grouperRole }, groupKidsByAlbum);
  }
  public RoleGrouperMediaFileNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, String inGroupType, Object inGroupObject, java.util.Vector inFiles, int[] grouperRoles,
      boolean groupKidsByAlbum)
  {
    super(inSource, inParent, inGroupLabel, inGroupType, inGroupObject, inFiles);
    this.grouperRoles = grouperRoles;
    this.groupKidsByAlbum = groupKidsByAlbum;
  }

  protected void verifyCache()
  {
    if (cachedKids != null) return;
    // Organize our list of files by artist
    java.util.Map artistMap = new java.util.HashMap();
    java.util.Vector newCache = new java.util.Vector();
    String[] unknown = new String[] { Sage.rez("Unknown") };
    String targetDatatype = DATATYPE_ACTOR;
    if (grouperRoles[0] == Show.ARTIST_ROLE)
      targetDatatype = DATATYPE_ARTIST;
    else if (grouperRoles[0] == Show.DIRECTOR_ROLE)
      targetDatatype = DATATYPE_DIRECTOR;
    for (int i = 0; i < descendants.size(); i++)
    {
      MediaFile currMF = (MediaFile) descendants.get(i);
      if (!mySource.passesFilter(DATATYPE_MEDIAFILE, currMF))
        continue;
      Show s = currMF.getShow();
      if (s == null)
        continue;
      String[] currArtists = s.getPeopleList(grouperRoles);
      if (currArtists == null || currArtists.length == 0)
        currArtists = unknown;
      for (int j = 0; j < currArtists.length; j++)
      {
        String lcArtist = currArtists[j].toLowerCase();
        java.util.Vector artistKids = (java.util.Vector) artistMap.get(lcArtist);
        if (artistKids == null)
        {
          artistMap.put(lcArtist, artistKids = new java.util.Vector());
          artistKids.add(currMF);
          if (groupKidsByAlbum)
            newCache.add(new AlbumGrouperMediaFileNode(mySource, this, currArtists[j], targetDatatype, currArtists[j], artistKids));
          else
            newCache.add(new SimpleGrouperMediaFileNode(mySource, this, currArtists[j], targetDatatype, currArtists[j], artistKids));
        }
        else
          artistKids.add(currMF);
      }
    }

    cachedKids = (BasicMediaNode[]) newCache.toArray(new BasicMediaNode[0]);
    mySource.sortData(cachedKids);
  }

  private int[] grouperRoles;
  private boolean groupKidsByAlbum;
}
