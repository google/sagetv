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

/**
 *
 * @author Narflex
 */
public class StaticMediaNode extends BasicMediaNode
{
  private String primary;
  private String secondary;
  private Object thumb;
  private Object icon;
  private Object data;
  private String dataType;

  /** Creates a new instance of StaticMediaNode */
  public StaticMediaNode(String primary, String secondary, Object thumb, Object icon, Object data)
  {
    this(primary, secondary, thumb, icon, data, DATATYPE_VIRTUAL);
  }
  public StaticMediaNode(String primary, String secondary, Object thumb, Object icon, Object data, String dataType)
  {
    super(null, null);
    this.primary = primary;
    this.secondary = secondary;
    this.thumb = thumb;
    this.icon = icon;
    this.data = data;
    this.dataType = dataType;
  }

  public boolean isFolder()
  {
    return false;
  }

  public MediaNode[] getChildren()
  {
    return null;
  }

  public int getNumChildren()
  {
    return 0;
  }

  public MediaNode getChildAt(int index)
  {
    return null;
  }

  public MediaNode getParent()
  {
    return null;
  }

  public void setSorting(String technique, boolean ascending)
  {
  }

  public void setFiltering(String technique)
  {
  }

  public void appendFiltering(String technique)
  {
  }

  public boolean isHierarchyRealized()
  {
    return false;
  }

  public java.util.Collection getFinalDescendants()
  {
    return null;
  }

  public Object getIcon(Object uiContext)
  {
    if ((uiContext instanceof sage.ResourceLoadListener) && ((sage.ResourceLoadListener) uiContext).getUIMgr() != null &&
        ((sage.ResourceLoadListener) uiContext).getUIMgr().isXBMCCompatible())
    {
      // NOTE: This is from Confluence's MainMenu where it seems like a thumb definition in contentitems will override an icon definition;
      // since otherwise I'm not sure how the custom backgrounds are supposed to work
      if (thumb != null && thumb.toString().length() > 0)
        return thumb;
    }
    return icon;
  }

  public Object getThumbnail(Object uiContext)
  {
    return thumb;
  }

  public String getPrimaryLabel()
  {
    return primary;
  }

  public String getSecondaryLabel()
  {
    return secondary;
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
    return data;
  }

  public String getDataType()
  {
    return dataType;
  }

  public String getProperty(String name)
  {
    if (data instanceof sage.Airing)
    {
      String s = BrowserMediaFileNode.getAiringProperty((sage.Airing) data, name);
      if (s != null)
        return s;
    }
    return "";
  }
}
