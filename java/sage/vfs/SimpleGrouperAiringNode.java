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
public class SimpleGrouperAiringNode extends GrouperMediaNode
{

  /** Creates a new instance of SimpleGrouperAiringNode */
  public SimpleGrouperAiringNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, String inGroupType, Object inGroupObject, java.util.Vector inDescendants,
      boolean channelPrimary)
  {
    super(inSource, inParent, inGroupLabel, inGroupType, inGroupObject);
    descendants = inDescendants == null ? new java.util.Vector() : inDescendants;
    this.channelPrimary = channelPrimary;
  }

  public synchronized void refresh()
  {
    cachedKids = null;
    super.refresh();
  }

  public Object getThumbnail(Object uiContext)
  {
    ResourceLoadListener loader = (uiContext instanceof ResourceLoadListener) ? ((ResourceLoadListener) uiContext) : null;
    return (loader == null || loader.getUIMgr() == null) ? "folder.png" : loader.getUIMgr().get("ui/default_folder_icon", "folder.png");
  }

  public synchronized MediaNode[] getChildren()
  {
    verifyCache();
    return cachedKids;
  }

  public boolean isHierarchyRealized()
  {
    return true;
  }

  public java.util.Collection getFinalDescendants()
  {
    return (descendants != null) ? (java.util.Collection) descendants.clone() : null;
  }

  public Object getFirstDescendant()
  {
    return (descendants != null && descendants.size() > 0) ? descendants.firstElement() : null;
  }

  protected void verifyCache()
  {
    if (cachedKids != null) return;
    java.util.Vector newCache = new java.util.Vector();
    for (int i = 0; i < descendants.size(); i++)
    {
      Airing a = (Airing) descendants.get(i);
      if (!mySource.passesFilter(DATATYPE_AIRING, a))
        continue;
      newCache.add(new AiringNode(mySource, this, a, channelPrimary));
    }

    cachedKids = (AiringNode[]) newCache.toArray(new AiringNode[0]);
    mySource.sortData(cachedKids);
  }

  protected BasicMediaNode[] cachedKids;
  protected java.util.Vector descendants;
  protected boolean channelPrimary;
}
