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
public abstract class GrouperMediaFileNode extends GrouperMediaNode
{
  /**
   * Creates a new instance of GenreMediaFileNode
   */
  public GrouperMediaFileNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, String inGroupType, Object inGroupObject, java.util.Vector inDescendants)
  {
    super(inSource, inParent, inGroupLabel, inGroupType, inGroupObject);
    descendants = inDescendants == null ? new java.util.Vector() : inDescendants;
  }

  public synchronized void refresh()
  {
    cachedKids = null;
    super.refresh();
  }

  public Object getThumbnail(Object uiContext)
  {
    ResourceLoadListener loader = (uiContext instanceof ResourceLoadListener) ? ((ResourceLoadListener) uiContext) : null;
    // Use the thumbnail from our first descendant instead if any have one
    if (randomThumbIndex < 0 || randomThumbIndex >= descendants.size())
      randomThumbIndex = (int)Math.floor(Math.random() * descendants.size());
    for (int i = 0; i < descendants.size(); i++)
    {
      MediaFile mf = (MediaFile) descendants.get((i + randomThumbIndex) % descendants.size());
      if (mf.hasThumbnail())
      {
        if (mf.isMusic())
        {
          Album al = Wizard.getInstance().getCachedAlbumForMediaFile(mf);
          if (al != null)
            return al.getThumbnail(loader);
        }
        return mf.getThumbnail(loader);
      }
    }
    return (loader == null || loader.getUIMgr() == null) ? "folder.png" : loader.getUIMgr().get("ui/default_folder_icon", "folder.png");
  }

  protected abstract void verifyCache();

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

  protected BasicMediaNode[] cachedKids;
  protected java.util.Vector descendants;
  protected int randomThumbIndex = -1;
}
