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
public class AiringNode extends BasicMediaNode
{

  /** Creates a new instance of AiringNode */
  public AiringNode(BasicMediaSource inSource, BasicMediaNode parent, Airing air, boolean channelPrimary)
  {
    super(inSource, parent);
    this.air = air;
    this.channelPrimary = channelPrimary;
  }
  public boolean isFolder()
  {
    return false;
  }

  public synchronized MediaNode[] getChildren()
  {
    return null;
  }

  public boolean isHierarchyRealized()
  {
    return true;
  }

  public java.util.Collection getFinalDescendants()
  {
    return null;
  }

  public Object getIcon(Object uiContext)
  {
    return getThumbnail(uiContext);
  }

  public Object getThumbnail(Object uiContext)
  {
    ResourceLoadListener loader = (uiContext instanceof ResourceLoadListener) ? ((ResourceLoadListener) uiContext) : null;
    MediaFile mf = Wizard.getInstance().getFileForAiring(air);
    if (mf != null)
    {
      if (mf.isMusic())
      {
        Album al = Wizard.getInstance().getCachedAlbumForMediaFile(mf);
        if (al != null)
          return al.hasThumbnail() ? al.getThumbnail(loader) : al.getGenericAlbumImage(loader);
      }
      return mf.getThumbnail(loader);
    }
    // Else we use the series image if there otherwise the channel logo
    Show sh = air.getShow();
    if (sh != null)
    {
      SeriesInfo si = sh.getSeriesInfo();
      if (si != null)
      {
        String url = si.getImageURL(false);
        if (url != null && url.length() > 0)
        {
          if (loader != null && loader.getUIMgr() != null)
            return loader.getUIMgr().getBGLoader().getMetaImageFast(url, loader, null);
          else
            return MetaImage.getMetaImage(url, loader);
        }
      }
    }
    return EPG.getInstance().getLogo(air.getChannel(), loader);
  }

  public String getPrimaryLabel()
  {
    return channelPrimary ? air.getChannelName() : air.getTitle();
  }

  public String getSecondaryLabel()
  {
    if (channelPrimary)
      return air.getTitle();
    else
    {
      // Return the date/time of the airing
      long start = air.getStartTime();
      long end = air.getEndTime();
      java.text.DateFormat timeFormat = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Sage.userLocale);
      return java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, Sage.userLocale).format(new java.util.Date(start)) + " " +
      timeFormat.format(new java.util.Date(start));
    }
  }

  public boolean isPlayable()
  {
    return Wizard.getInstance().getFileForAiring(air) != null || (air.getSchedulingStart() <= Sage.time() && air.getSchedulingEnd() > Sage.time());
  }

  public boolean isVirtual()
  {
    return false;
  }

  public Object getDataObject()
  {
    return air;
  }

  public String getDataType()
  {
    return DATATYPE_AIRING;
  }

  public String getProperty(String name)
  {
    if ("Date".equalsIgnoreCase(name))
    {
      // Return the time span of the airing
      long start = air.getStartTime();
      long end = air.getEndTime();
      java.text.DateFormat timeFormat = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Sage.userLocale);
      return java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, Sage.userLocale).format(new java.util.Date(start)) + " " +
      timeFormat.format(new java.util.Date(start)) + " - " + timeFormat.format(new java.util.Date(end));
    }
    else if ("Comment".equalsIgnoreCase(name))
    {
      if (Wizard.getInstance().getManualRecord(air) != null)
        return Sage.rez("ManualRecord");
      else if (air.isMustSee())
        return Sage.rez("Favorite");
    }
    else if ("Title".equalsIgnoreCase(name))
    {
      return air.getTitle();
    }
    String x = BrowserMediaFileNode.getAiringProperty(air, name);
    if (x != null)
      return x;
    MediaFile mf = Wizard.getInstance().getFileForAiring(air);
    if (mf != null)
    {
      String rv = mf.getMetadataProperty(name);
      return (rv == null) ? "" : rv;
    }
    return super.getProperty(name);
  }

  private Airing air;
  // This is true when the channel is the primary information to be displayed instead of the airing title
  private boolean channelPrimary;
}
