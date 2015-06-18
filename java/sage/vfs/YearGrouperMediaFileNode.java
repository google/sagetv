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
public class YearGrouperMediaFileNode extends GrouperMediaFileNode
{

  /**
   * Creates a new instance of YearGrouperMediaFileNode
   */
  public YearGrouperMediaFileNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, java.util.Vector inDescendants, boolean groupKidsByAlbum)
  {
    super(inSource, inParent, inGroupLabel, DATATYPE_VIRTUAL, null, inDescendants);
    this.groupKidsByAlbum = groupKidsByAlbum;
  }

  protected void verifyCache()
  {
    if (cachedKids != null) return;
    // Organize our list of files by genre
    java.util.Map yearMap = new java.util.HashMap();
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
      String currYear = s.getYear();
      if (currYear == null || currYear.length() == 0)
        currYear = unknown;
      java.util.Vector yearKids = (java.util.Vector) yearMap.get(currYear);
      if (yearKids == null)
      {
        yearMap.put(currYear, yearKids = new java.util.Vector());
        yearKids.add(currMF);
        if (groupKidsByAlbum)
          newCache.add(new AlbumGrouperMediaFileNode(mySource, this, currYear, DATATYPE_YEAR, currYear, yearKids));
        else
          newCache.add(new SimpleGrouperMediaFileNode(mySource, this, currYear, DATATYPE_YEAR, currYear, yearKids));
      }
      else
        yearKids.add(currMF);
    }

    cachedKids = (BasicMediaNode[]) newCache.toArray(new BasicMediaNode[0]);
    mySource.sortData(cachedKids);

  }

  private boolean groupKidsByAlbum;
}
