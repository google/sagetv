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
public abstract class GrouperMediaNode extends BasicMediaNode
{

  /** Creates a new instance of GrouperMediaNode */
  public GrouperMediaNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, String inGroupType, Object inGroupObject)
  {
    super(inSource, inParent);
    groupLabel = inGroupLabel;
    groupType = inGroupType;
    groupObject = inGroupObject;
  }

  public boolean isFolder()
  {
    return true;
  }

  public Object getIcon(Object uiContext)
  {
    return getThumbnail(uiContext);
  }

  public Object getThumbnail(Object uiContext)
  {
    ResourceLoadListener loader = (uiContext instanceof ResourceLoadListener) ? ((ResourceLoadListener) uiContext) : null;
    return (loader == null || loader.getUIMgr() == null) ? "folder.png" : loader.getUIMgr().get("ui/default_folder_icon", "folder.png");
  }

  public String getPrimaryLabel()
  {
    return groupLabel;
  }

  public String getSecondaryLabel()
  {
    return "";
  }

  public boolean isPlayable()
  {
    return false;
  }

  public boolean isVirtual()
  {
    return true;
  }

  public Object getDataObject()
  {
    return groupObject;
  }

  public String getDataType()
  {
    return groupType;
  }

  protected String groupLabel;
  protected String groupType;
  protected Object groupObject;
}
