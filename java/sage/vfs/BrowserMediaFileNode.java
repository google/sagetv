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
public class BrowserMediaFileNode extends BasicMediaNode
{
  public BrowserMediaFileNode(BasicMediaSource inSource, BasicMediaNode parent, String relativePath, java.util.Vector allChildren)
  {
    super(inSource, parent);
    this.relativePath = relativePath;
    descendants = allChildren;
  }
  public BrowserMediaFileNode(BasicMediaSource inSource, BasicMediaNode parent, MediaFile mf)
  {
    super(inSource, parent);
    this.mf = mf;
  }
  public boolean isFolder()
  {
    return mf == null;
  }

  public synchronized MediaNode[] getChildren()
  {
    verifyCache();
    return cachedKids;
  }

  private synchronized void verifyCache()
  {
    if (cachedKids != null || mf != null)
      return;

    cachedDate = 0;
    int prefixLength = relativePath.length();
    if (prefixLength > 0)
      prefixLength++; // trailing slash
    java.util.Vector newCache = new java.util.Vector();
    java.util.HashMap groupy = new java.util.HashMap();
    for (int i = 0; i < descendants.size(); i++)
    {
      MediaFile currMF = (MediaFile) descendants.get(i);
      if (!mySource.passesFilter(DATATYPE_MEDIAFILE, currMF))
        continue;
      cachedDate = Math.max(cachedDate, currMF.getContentAiring().getStartTime());
      // Find out if it's a folder or a file
      boolean isFolder = false;
      String name = currMF.getName();
      int nextIdx = name.indexOf('/', prefixLength);
      isFolder = (nextIdx != -1) && nextIdx < name.length() - 1;
      if (isFolder && currMF.isDVD())
      {
        // Check if it's a VIDEO_TS folder
        if (name.regionMatches(true, nextIdx + 1, Seeker.DVD_VOLUME_SECRET, 0, Seeker.DVD_VOLUME_SECRET.length()))
          isFolder = false;
      }
      else if (isFolder && currMF.isBluRay())
      {
        // Check if it's a BDMV folder
        if (name.regionMatches(true, nextIdx + 1, Seeker.BLURAY_VOLUME_SECRET, 0, Seeker.BLURAY_VOLUME_SECRET.length()))
          isFolder = false;
      }
      if (isFolder)
      {
        String subPath = name.substring(0, nextIdx);
        java.util.Vector currList = (java.util.Vector) groupy.get(subPath);
        if (currList == null)
        {
          groupy.put(subPath, currList = new java.util.Vector());
          currList.add(currMF);
          newCache.add(new BrowserMediaFileNode(mySource, this, subPath, currList));
        }
        else
          currList.add(currMF);
      }
      else
        newCache.add(new BrowserMediaFileNode(mySource, this, currMF));
    }

    cachedKids = (BrowserMediaFileNode[]) newCache.toArray(new BrowserMediaFileNode[0]);
    mySource.sortData(cachedKids);
  }

  public boolean isHierarchyRealized()
  {
    return true;
  }

  public java.util.Collection getFinalDescendants()
  {
    if (descendants == null)
      return null;
    return (java.util.Collection) descendants.clone();
  }

  public Object getIcon(Object uiContext)
  {
    return getThumbnail(uiContext);
  }

  public Object getThumbnail(Object uiContext)
  {
    ResourceLoadListener loader = (uiContext instanceof ResourceLoadListener) ? ((ResourceLoadListener) uiContext) : null;
    if (mf == null)
      return (loader == null || loader.getUIMgr() == null) ? "folder.png" : loader.getUIMgr().get("ui/default_folder_icon", "folder.png");
    else
    {
      if (mf.isMusic())
      {
        Album al = Wizard.getInstance().getCachedAlbumForMediaFile(mf);
        if (al != null)
          return al.hasThumbnail() ? al.getThumbnail(loader) : al.getGenericAlbumImage(loader);
      }
      return mf.getThumbnail(loader);
    }
  }

  public String getPrimaryLabel()
  {
    if (mf == null)
    {
      int idx = relativePath.lastIndexOf('/');
      return (idx == -1) ? relativePath : relativePath.substring(idx + 1);
    }
    if (mf.isMusic())
      return mf.getMediaTitle() + " - " + mf.getShow().getPeopleString(Show.ARTIST_ROLE);
    // Check for episodic listing
    if (parent != null && DATATYPE_SERIESINFO.equals(parent.getDataType()))
    {
      String epName = mf.getShow().getEpisodeName();
      if (epName.length() > 0)
        return epName;
    }
    return mf.getMediaTitle();
  }

  public String getSecondaryLabel()
  {
    String currSort = mySource.getSorter() == null ? "" : mySource.getSorter().getTechnique();
    if (currSort.equals(SORT_BY_DATE))
      return Sage.dfStd(getDate());
    if (mf == null)
      return "";
    else
    {
      if (currSort.equals(SORT_BY_DATE))
        return Sage.dfStd(mf.getContentAiring().getStartTime());
      else if (mf.isPicture() || currSort.equals(SORT_BY_SIZE))
      {
        long size = getSize();
        if (size == 0)
          return "";
        double dsize = size / 1024.0;
        if (dsize < 1024)
          return floatFormat.format(dsize) + " KB";
        dsize = dsize / 1024;
        if (dsize < 1024)
          return floatFormat.format(dsize) + " MB";
        dsize = dsize / 1024;
        if (dsize < 1024)
          return floatFormat.format(dsize) + " GB";
        dsize = dsize / 1024;
        return floatFormat.format(dsize) + " TB";
      }
      else
      {
        long dur = mf.getRecordDuration();
        return dur < 1000 ? "" : Sage.durFormatSlim(dur);
      }
    }
  }

  public long getDate()
  {
    if (mf != null)
      return mf.getContentAiring().getStartTime();
    else
    {
      verifyCache();
      return cachedDate;
    }
  }

  public long getSize()
  {
    if (mf != null)
      return cachedSize > 0 ? cachedSize : (cachedSize = mf.getSize());
      else
        return 0;
  }

  public boolean isPlayable()
  {
    return mf != null;
  }

  public boolean isVirtual()
  {
    return mf == null;
  }

  public Object getDataObject()
  {
    return mf == null ? (Object)relativePath : (Object)mf;
  }

  public String getDataType()
  {
    return mf == null ? DATATYPE_VIRTUAL : DATATYPE_MEDIAFILE;
  }

  public synchronized void refresh()
  {
    cachedKids = null;
    super.refresh();
  }

  public static String getAiringProperty(Airing a, String name)
  {
    if (a == null) return null;
    Show sh = a.getShow();
    if (sh == null)
      return null;
    if ("Date".equalsIgnoreCase(name))
    {
      // Original Air Date is preferred by XBMC
      if (sh.getOriginalAirDate() != 0)
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, Sage.userLocale).format(new java.util.Date(sh.getOriginalAirDate()));
      long t = a.getStartTime();
      return java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, Sage.userLocale).format(new java.util.Date(t)) + " " +
      java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Sage.userLocale).format(new java.util.Date(t));
    }
    else if ("SeasonNumber".equalsIgnoreCase(name))
    {
      // Approximate this by the number of years since the series started
      SeriesInfo si = sh.getSeriesInfo();
      if (si != null)
      {
        try
        {
          // Extract the year
          int orgYear = Integer.parseInt(si.getPremiereDate().substring(0, 4));
          java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
          cal.setTimeInMillis(sh.getOriginalAirDate());
          int currYear = cal.get(java.util.GregorianCalendar.YEAR);
          return Integer.toString(1 + currYear - orgYear);
        }
        catch (Exception e){}
      }
    }
    else if ("EpisodeNumber".equalsIgnoreCase(name))
    {
      if (sh.getSeriesInfo() != null)
      {
        try
        {
          String extID = sh.getExternalID();
          // Remove leading zeroes
          return Integer.toString(Integer.parseInt(extID.substring(extID.length() - 4)));
        }
        catch (Exception e){}
      }
    }
    else if ("genretype".equalsIgnoreCase(name))
    {
      // Numeric genres used in XBMC
      // 16 - movie/drama
      // 32 - news
      // 48 - show??
      // 64 - sports
      // 80 - children
      // 96 - music
      // 112 - arts
      // 128 - social
      // 144 - science
      // 160 - hobby
      // 176 - special
      // 240 or 0 - unknown
      // NOTE: THIS IS NOT COMPLETE YET!!!!
      String showCat = sh.getCategory().toLowerCase();
      if ("news".equals(showCat) || "newsmagazine".equals(showCat))
        return "32";
      else if ("sports".equals(showCat) || "sports event".equals(showCat) || "sports non-event".equals(showCat) || "sports talk".equals(showCat))
        return "64";
      else if ("children".equals(showCat))
        return "80";
      else if ("movie".equals(showCat) || "drama".equals(showCat))
        return "16";
      else if ("community".equals(showCat) || "talk".equals(showCat) || "bus.".equals(showCat))
        return "128";
      else if ("educational".equals(showCat) || "science".equals(showCat))
        return "144";
      else if ("arts".equals(showCat) || "art".equals(showCat) || "ballet".equals(showCat))
        return "112";
      else if ("music".equals(showCat))
        return "96";
      else if ("special".equals(showCat))
        return "176";
      else if (sh.getExternalID().startsWith("SH"))
        return "48";
      else
        return "0";
    }
    return null;
  }

  public String getProperty(String name)
  {
    if (mf != null)
    {
      String x = getAiringProperty(mf.getContentAiring(), name);
      if (x != null)
        return x;
      if ("Title".equalsIgnoreCase(name))
      {
        // Check for episodic listing
        if (parent != null && DATATYPE_SERIESINFO.equals(parent.getDataType()))
        {
          String epName = mf.getShow().getEpisodeName();
          if (epName.length() > 0)
            return epName;
        }
        else
          return getPrimaryLabel();
      }
      String rv = mf.getMetadataProperty(name);
      return (rv == null) ? "" : rv;
    }
    return super.getProperty(name);
  }

  private MediaFile mf;
  private String relativePath;
  private java.util.Vector descendants;
  private BrowserMediaFileNode[] cachedKids;
  private long cachedDate;
  private long cachedSize;
}
