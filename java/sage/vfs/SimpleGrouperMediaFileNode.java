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
public class SimpleGrouperMediaFileNode extends GrouperMediaFileNode
{

  /** Creates a new instance of SimpleGrouperMediaFileNode */
  public SimpleGrouperMediaFileNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, String inGroupType, Object inGroupObject, java.util.Vector inDescendants)
  {
    super(inSource, inParent, inGroupLabel, inGroupType, inGroupObject, inDescendants);
  }

  public Object getThumbnail(Object uiContext)
  {
    if (groupObject instanceof SeriesInfo)
    {
      SeriesInfo si = (SeriesInfo) groupObject;
      String imageURL = si.getImageURL(false);
      if (imageURL != null && imageURL.length() > 0)
      {
        ResourceLoadListener loader = (uiContext instanceof ResourceLoadListener) ? ((ResourceLoadListener) uiContext) : null;
        return MetaImage.getMetaImage(imageURL, loader);
      }
    }
    return super.getThumbnail(uiContext);
  }

  protected void verifyCache()
  {
    if (cachedKids != null) return;
    java.util.Vector newCache = new java.util.Vector();
    for (int i = 0; i < descendants.size(); i++)
    {
      MediaFile mf = (MediaFile) descendants.get(i);
      if (!mySource.passesFilter(DATATYPE_MEDIAFILE, mf))
        continue;
      newCache.add(new BrowserMediaFileNode(mySource, this, mf));
    }

    cachedKids = (BrowserMediaFileNode[]) newCache.toArray(new BrowserMediaFileNode[0]);
    mySource.sortData(cachedKids);
  }

  public String getProperty(String name)
  {
    if (groupObject instanceof SeriesInfo)
    {
      if ("EpisodeNumber".equalsIgnoreCase(name))
        return Integer.toString(getNumChildren());
      if ("Date".equalsIgnoreCase(name))
        return ((SeriesInfo) groupObject).getPremiereDate();
    }
    return super.getProperty(name);
  }

}
