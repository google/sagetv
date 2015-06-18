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
public class StudioGrouperMediaFileNode extends GrouperMediaFileNode
{

  /**
   * Creates a new instance of StudioGrouperMediaFileNode
   */
  public StudioGrouperMediaFileNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, java.util.Vector inDescendants)
  {
    super(inSource, inParent, inGroupLabel, DATATYPE_VIRTUAL, null, inDescendants);
  }

  protected void verifyCache()
  {
    if (cachedKids != null) return;
    // Organize our list of files by studio
    java.util.Map studioMap = new java.util.HashMap();
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
      String currstudio = s.getStudio();
      if (currstudio == null || currstudio.length() == 0)
        currstudio = unknown;
      String lcstudio = currstudio.toLowerCase();
      java.util.Vector studioKids = (java.util.Vector) studioMap.get(lcstudio);
      if (studioKids == null)
      {
        studioMap.put(lcstudio, studioKids = new java.util.Vector());
        studioKids.add(currMF);
        newCache.add(new SimpleGrouperMediaFileNode(mySource, this, currstudio, DATATYPE_STUDIO, currstudio, studioKids));
      }
      else
        studioKids.add(currMF);
    }

    cachedKids = (BasicMediaNode[]) newCache.toArray(new BasicMediaNode[0]);
    mySource.sortData(cachedKids);
  }
}
