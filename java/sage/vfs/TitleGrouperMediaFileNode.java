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
public class TitleGrouperMediaFileNode extends GrouperMediaFileNode
{

  /**
   * Creates a new instance of TitleGrouperMediaFileNode
   */
  public TitleGrouperMediaFileNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, java.util.Vector inDescendants)
  {
    super(inSource, inParent, inGroupLabel, DATATYPE_VIRTUAL, null, inDescendants);
  }

  protected void verifyCache()
  {
    if (cachedKids != null) return;
    // Organize our list of files by title
    java.util.Map titleMap = new java.util.HashMap();
    java.util.Vector newCache = new java.util.Vector();
    for (int i = 0; i < descendants.size(); i++)
    {
      MediaFile currMF = (MediaFile) descendants.get(i);
      if (!mySource.passesFilter(DATATYPE_MEDIAFILE, currMF))
        continue;
      String currTitle = currMF.getMediaTitle();
      String lcTitle = currTitle.toLowerCase();
      java.util.Vector titleKids = (java.util.Vector) titleMap.get(lcTitle);
      if (titleKids == null)
      {
        titleMap.put(lcTitle, titleKids = new java.util.Vector());
        titleKids.add(currMF);
      }
      else
        titleKids.add(currMF);
    }

    java.util.Iterator walker = titleMap.entrySet().iterator();
    while (walker.hasNext())
    {
      java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
      String currTitle = ent.getKey().toString();
      java.util.Vector currKids = (java.util.Vector) ent.getValue();
      if (currKids.size() > 1)
        newCache.add(new SimpleGrouperMediaFileNode(mySource, this, currTitle, DATATYPE_TITLE, currTitle, currKids));
      else
        newCache.add(new BrowserMediaFileNode(mySource, this, (MediaFile)currKids.get(0)));
    }

    cachedKids = (BasicMediaNode[]) newCache.toArray(new BasicMediaNode[0]);
    mySource.sortData(cachedKids);

  }
}
