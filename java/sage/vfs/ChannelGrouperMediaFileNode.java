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
public class ChannelGrouperMediaFileNode extends GrouperMediaFileNode
{

  /**
   * Creates a new instance of ChannelGrouperMediaFileNode
   */
  public ChannelGrouperMediaFileNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, java.util.Vector inDescendants)
  {
    super(inSource, inParent, inGroupLabel, DATATYPE_VIRTUAL, null, inDescendants);
  }

  protected void verifyCache()
  {
    if (cachedKids != null) return;
    // Organize our list of files by Channel
    java.util.Map channelMap = new java.util.HashMap();
    java.util.Vector newCache = new java.util.Vector();
    for (int i = 0; i < descendants.size(); i++)
    {
      MediaFile currMF = (MediaFile) descendants.get(i);
      if (!mySource.passesFilter(DATATYPE_MEDIAFILE, currMF))
        continue;
      Channel c = currMF.getContentAiring().getChannel();
      java.util.Vector channelKids = (java.util.Vector) channelMap.get(c);
      if (channelKids == null)
      {
        channelMap.put(c, channelKids = new java.util.Vector());
        channelKids.add(currMF);
        newCache.add(new SimpleGrouperMediaFileNode(mySource, this, c.getName(), DATATYPE_CHANNEL, c, channelKids));
      }
      else
        channelKids.add(currMF);
    }

    cachedKids = (BasicMediaNode[]) newCache.toArray(new BasicMediaNode[0]);
    mySource.sortData(cachedKids);
  }
}
