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
public class SeriesGrouperMediaFileNode extends GrouperMediaFileNode
{

  /**
   * Creates a new instance of SeriesGrouperMediaFileNode
   */
  public SeriesGrouperMediaFileNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, java.util.Vector inDescendants)
  {
    super(inSource, inParent, inGroupLabel, DATATYPE_VIRTUAL, null, inDescendants);
  }

  protected void verifyCache()
  {
    if (cachedKids != null) return;
    // Organize our list of files by series
    java.util.Map seriesMap = new java.util.HashMap();
    java.util.Vector newCache = new java.util.Vector();
    for (int i = 0; i < descendants.size(); i++)
    {
      MediaFile currMF = (MediaFile) descendants.get(i);
      if (!mySource.passesFilter(DATATYPE_MEDIAFILE, currMF))
        continue;
      SeriesInfo currSeries = currMF.getShow().getSeriesInfo();
      if (currSeries != null)
      {
        java.util.Vector seriesKids = (java.util.Vector) seriesMap.get(currSeries);
        if (seriesKids == null)
        {
          seriesMap.put(currSeries, seriesKids = new java.util.Vector());
          newCache.add(new SimpleGrouperMediaFileNode(mySource, this, currSeries.getTitle(), DATATYPE_SERIESINFO, currSeries, seriesKids));
          seriesKids.add(currMF);
        }
        else
          seriesKids.add(currMF);
      }
      else
        newCache.add(new BrowserMediaFileNode(mySource, this, currMF));
    }

    cachedKids = (BasicMediaNode[]) newCache.toArray(new BasicMediaNode[0]);
    mySource.sortData(cachedKids);
  }
}
