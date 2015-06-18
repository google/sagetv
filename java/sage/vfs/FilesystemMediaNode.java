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
public class FilesystemMediaNode extends BasicMediaNode
{
  public FilesystemMediaNode(BasicMediaSource inSource, java.io.File realFile, BasicMediaNode inParent)
  {
    super(inSource, inParent);
    myFile = realFile;
    if (myFile != null)
    {
      isDirCached = !myFile.isFile(); // don't use isDirectory() because offline root drives return false for that
      cachedModTime = myFile.lastModified();
      cachedSize = myFile.length();
    }
  }
  public boolean isFolder()
  {
    return myFile == null || isDirCached;
  }

  public synchronized MediaNode[] getChildren()
  {
    verifyCache();
    return cachedKids;
  }

  private synchronized void verifyCache()
  {
    if (cachedKids != null)
      return;
    java.io.File[] realKids = myFile == null ? java.io.File.listRoots() : myFile.listFiles();
    if (realKids == null)
      return;
    cachedKids = new FilesystemMediaNode[realKids.length];
    int validKids = 0;
    for (int i = 0; i < cachedKids.length; i++)
    {
      if (mySource.passesFilter(DATATYPE_FILE, realKids[i]))
        cachedKids[validKids++] = new FilesystemMediaNode(mySource, realKids[i], this);
    }
    if (validKids < cachedKids.length)
    {
      FilesystemMediaNode[] shorter = new FilesystemMediaNode[validKids];
      System.arraycopy(cachedKids, 0, shorter, 0, validKids);
      cachedKids = shorter;
    }
    mySource.sortData(cachedKids);
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
    return getThumbnail(uiContext);
  }

  public Object getThumbnail(Object uiContext)
  {
    ResourceLoadListener loader = (uiContext instanceof ResourceLoadListener) ? ((ResourceLoadListener) uiContext) : null;
    if (myFile == null || myFile.getParentFile() == null) // root drives
      return (loader == null || loader.getUIMgr() == null) ? "hdd-link.png" : loader.getUIMgr().get("ui/default_hdd_icon", "hdd-link.png");
    else if (isFolder())
      return (loader == null || loader.getUIMgr() == null) ? "folder.png" : loader.getUIMgr().get("ui/default_folder_icon", "folder.png");
    else
    {
      MediaFile mf = Wizard.getInstance().getFileForFilePath(myFile);
      if (mf != null)
      {
        if (mf.isMusic())
        {
          Album al = Wizard.getInstance().getCachedAlbumForMediaFile(mf);
          if (al != null)
            return al.getThumbnail(loader);
        }
        return mf.getThumbnail(loader);
      }
      int mediaMask = Seeker.getInstance().guessImportedMediaMaskFast(myFile.getAbsolutePath());
      return MediaFile.getGenericImage(mediaMask, loader);
    }
  }

  public String getPrimaryLabel()
  {
    if (myFile == null)
      return Sage.rez("Root Filesystem");
    else if (myFile.getParentFile() == null)
      return myFile.getAbsolutePath(); // root drives
    else
      return myFile.getName();
  }

  public String getSecondaryLabel()
  {
    if (myFile == null) return "";
    if (mySource.getSorter() != null && mySource.getSorter().getTechnique().equals(SORT_BY_DATE))
      return Sage.dfClean(myFile.lastModified());
    else // use file size otherwise
    {
      if (isFolder())
        return "";
      long size = cachedSize;//myFile.length();
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
  }

  public boolean isPlayable()
  {
    return myFile != null && myFile.isFile();
  }

  public boolean isVirtual()
  {
    return false;
  }

  public Object getDataObject()
  {
    return myFile;
  }

  public String getDataType()
  {
    return DATATYPE_FILE;
  }

  public synchronized void refresh()
  {
    cachedKids = null;
    super.refresh();
  }

  public long getDate()
  {
    return (myFile == null) ? Sage.time() : cachedModTime;
  }

  public long getSize()
  {
    return isFolder() ? 0 : myFile.length();
  }

  private java.io.File myFile;
  private FilesystemMediaNode[] cachedKids;
  private boolean isDirCached;
  private long cachedModTime;
  private long cachedSize;
}
