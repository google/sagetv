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
public class VFSFactory
{
  private static VFSFactory chosenOne;
  private static final Object chosenOneLock = new Object();

  public static VFSFactory getInstance() {
    if (chosenOne == null) {
      synchronized (chosenOneLock) {
        if (chosenOne == null) {
          chosenOne = new VFSFactory();
        }
      }
    }
    return chosenOne;
  }
  /** Creates a new instance of VFSFactory */
  private VFSFactory()
  {
    // Establish our registry of sorters
    addToSortRegistry(new sage.vfs.sort.NameSorter(true, false));
    addToSortRegistry(new sage.vfs.sort.NameSorter(false, false));
    addToSortRegistry(new sage.vfs.sort.NameSorter(true, true));
    addToSortRegistry(new sage.vfs.sort.NameSorter(false, true));
    addToSortRegistry(new sage.vfs.sort.DateSorter(true));
    addToSortRegistry(new sage.vfs.sort.DateSorter(false));
    addToSortRegistry(new sage.vfs.sort.SizeSorter(true));
    addToSortRegistry(new sage.vfs.sort.SizeSorter(false));
    addToSortRegistry(new sage.vfs.sort.TrackSorter(true));
    addToSortRegistry(new sage.vfs.sort.TrackSorter(false));
    addToSortRegistry(new sage.vfs.sort.FilenameSorter(true));
    addToSortRegistry(new sage.vfs.sort.FilenameSorter(false));
    addToSortRegistry(new sage.vfs.sort.DurationSorter(true));
    addToSortRegistry(new sage.vfs.sort.DurationSorter(false));
    addToSortRegistry(new sage.vfs.sort.TitleSorter(true, false));
    addToSortRegistry(new sage.vfs.sort.TitleSorter(false, false));
    addToSortRegistry(new sage.vfs.sort.TitleSorter(true, true));
    addToSortRegistry(new sage.vfs.sort.TitleSorter(false, true));
    addToSortRegistry(new sage.vfs.sort.ArtistSorter(true, false));
    addToSortRegistry(new sage.vfs.sort.ArtistSorter(false, false));
    addToSortRegistry(new sage.vfs.sort.ArtistSorter(true, true));
    addToSortRegistry(new sage.vfs.sort.ArtistSorter(false, true));
    addToSortRegistry(new sage.vfs.sort.CategorySorter(true));
    addToSortRegistry(new sage.vfs.sort.CategorySorter(false));
    addToSortRegistry(new sage.vfs.sort.YearSorter(true));
    addToSortRegistry(new sage.vfs.sort.YearSorter(false));
    addToSortRegistry(new sage.vfs.sort.RatingSorter(true));
    addToSortRegistry(new sage.vfs.sort.RatingSorter(false));
    addToSortRegistry(new sage.vfs.sort.RatedSorter(true));
    addToSortRegistry(new sage.vfs.sort.RatedSorter(false));
    addToSortRegistry(new sage.vfs.sort.CountSorter(true));
    addToSortRegistry(new sage.vfs.sort.CountSorter(false));
    addToSortRegistry(new sage.vfs.sort.EpisodeNameSorter(true));
    addToSortRegistry(new sage.vfs.sort.EpisodeNameSorter(false));
    addToSortRegistry(new sage.vfs.sort.EpisodeIDSorter(true));
    addToSortRegistry(new sage.vfs.sort.EpisodeIDSorter(false));
    addToSortRegistry(new sage.vfs.sort.RuntimeSorter(true));
    addToSortRegistry(new sage.vfs.sort.RuntimeSorter(false));
    addToSortRegistry(new sage.vfs.sort.StudioSorter(true, false));
    addToSortRegistry(new sage.vfs.sort.StudioSorter(false, false));
    addToSortRegistry(new sage.vfs.sort.StudioSorter(true, true));
    addToSortRegistry(new sage.vfs.sort.StudioSorter(false, true));
    addToSortRegistry(new sage.vfs.sort.AlbumSorter(true, false));
    addToSortRegistry(new sage.vfs.sort.AlbumSorter(false, false));
    addToSortRegistry(new sage.vfs.sort.AlbumSorter(true, true));
    addToSortRegistry(new sage.vfs.sort.AlbumSorter(false, true));
    addToSortRegistry(new sage.vfs.sort.OriginalAirDateSorter(true));
    addToSortRegistry(new sage.vfs.sort.OriginalAirDateSorter(false));
    addToSortRegistry(new sage.vfs.sort.ChannelNameSorter(true));
    addToSortRegistry(new sage.vfs.sort.ChannelNameSorter(false));
    addToSortRegistry(new sage.vfs.sort.IntelligentSorter(true));
    addToSortRegistry(new sage.vfs.sort.IntelligentSorter(false));

    // Establish our registry of filterers
    addToFilterRegistry(new sage.vfs.filter.DirectoryFilter(true));
    addToFilterRegistry(new sage.vfs.filter.DirectoryFilter(false));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_BLURAY, MediaNode.FILTER_BLURAY, sage.Sage.rez("BluRay"), true));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_BLURAY, MediaNode.FILTER_BLURAY, sage.Sage.rez("BluRay"), false));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_DVD, MediaNode.FILTER_DVD, sage.Sage.rez("DVD"), true));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_DVD, MediaNode.FILTER_DVD, sage.Sage.rez("DVD"), false));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_MUSIC, MediaNode.FILTER_MUSIC, sage.Sage.rez("Music"), true));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_MUSIC, MediaNode.FILTER_MUSIC, sage.Sage.rez("Music"), false));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_PICTURE, MediaNode.FILTER_PICTURES, sage.Sage.rez("Pictures"), true));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_PICTURE, MediaNode.FILTER_PICTURES, sage.Sage.rez("Pictures"), false));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_VIDEO, MediaNode.FILTER_VIDEOS, sage.Sage.rez("Videos"), true));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_VIDEO, MediaNode.FILTER_VIDEOS, sage.Sage.rez("Videos"), false));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_TV, MediaNode.FILTER_TV, sage.Sage.rez("TV"), true));
    addToFilterRegistry(new sage.vfs.filter.MediaMaskFilter(sage.DBObject.MEDIA_MASK_TV, MediaNode.FILTER_TV, sage.Sage.rez("TV"), false));
    addToFilterRegistry(new sage.vfs.filter.WatchedFilter(true));
    addToFilterRegistry(new sage.vfs.filter.WatchedFilter(false));
    addToFilterRegistry(new sage.vfs.filter.ArchivedFilter(true));
    addToFilterRegistry(new sage.vfs.filter.ArchivedFilter(false));
    addToFilterRegistry(new sage.vfs.filter.DontLikeFilter(true));
    addToFilterRegistry(new sage.vfs.filter.DontLikeFilter(false));
    addToFilterRegistry(new sage.vfs.filter.FavoriteFilter(true));
    addToFilterRegistry(new sage.vfs.filter.FavoriteFilter(false));
    addToFilterRegistry(new sage.vfs.filter.FirstRunFilter(true));
    addToFilterRegistry(new sage.vfs.filter.FirstRunFilter(false));
    addToFilterRegistry(new sage.vfs.filter.HDTVFilter(true));
    addToFilterRegistry(new sage.vfs.filter.HDTVFilter(false));
    addToFilterRegistry(new sage.vfs.filter.ManualRecordFilter(true));
    addToFilterRegistry(new sage.vfs.filter.ManualRecordFilter(false));
    addToFilterRegistry(new sage.vfs.filter.CompleteRecordingFilter(true));
    addToFilterRegistry(new sage.vfs.filter.CompleteRecordingFilter(false));
  }

  private void addToSortRegistry(MediaNodeSorter addMe)
  {
    sortRegistry.put(addMe.getTechnique().toLowerCase() + (addMe.isAscending() ? "A": "D"), addMe);
  }

  private void addToFilterRegistry(DataObjectFilter addMe)
  {
    filterRegistry.put(addMe.getTechnique().toLowerCase() + (addMe.isMatchingFilter() ? "Y": "N"), addMe);
  }

  public MediaNode createMediaSource(String name, Object relativeRoot)
  {
    name = name.toLowerCase();
    if ("filesystem".equals(name))
    {
      if (relativeRoot == null)
        return new StandardFilesystemView().createVFS();
      else
        return new StandardFilesystemView().createRelativeVFS(relativeRoot);
    }
    else if ("videonavigator".equals(name))
    {
      return new VideoNavigatorView().createVFS();
    }
    else if ("musicnavigator".equals(name))
    {
      return new MusicNavigatorView().createVFS();
    }
    else if ("musicvideosnavigator".equals(name))
    {
      return new MusicVideosNavigatorView().createVFS();
    }
    else if ("moviesnavigator".equals(name))
    {
      return new MoviesNavigatorView().createVFS();
    }
    else if ("tvnavigator".equals(name))
    {
      return new TVNavigatorView().createVFS();
    }
    else
      return createCollectionView(name);
  }

  public MediaNode createCollectionView(String name)
  {
    String lcname = name.toLowerCase();
    int byIdx = lcname.indexOf("by");
    if (byIdx == -1)
    {
      // Straight view of a collection
      return createCollectionView2(name, "default", createCollection(lcname));
    }
    String collectionName = lcname.substring(0, byIdx);
    String viewName = lcname.substring(byIdx + 2);
    return createCollectionView2(collectionName, viewName, createCollection(collectionName));
  }

  public java.util.Vector createCollection(String name)
  {
    name = name.toLowerCase();
    if ("clips".equals(name))
    {
      java.util.Vector mfSubset = new java.util.Vector();
      MediaFile[] mfs = Wizard.getInstance().getFiles(DBObject.MEDIA_MASK_VIDEO, true);
      long clipBound = Sage.getLong("max_duration_to_be_a_clip", 600000);
      for (int i = 0; i < mfs.length; i++)
        if (mfs[i].getRecordDuration() <= clipBound)
          mfSubset.add(mfs[i]);
      return mfSubset;
    }
    else if ("music".equals(name) || "importedmusic".equals(name))
    {
      return new java.util.Vector(java.util.Arrays.asList(Wizard.getInstance().getFiles(DBObject.MEDIA_MASK_MUSIC, true)));
    }
    else if ("pictures".equals(name) || "importedpictures".equals(name) || "photos".equals(name) || "importedphotos".equals(name))
    {
      return new java.util.Vector(java.util.Arrays.asList(Wizard.getInstance().getFiles(DBObject.MEDIA_MASK_PICTURE, true)));
    }
    else if ("videos".equals(name) || "importedvideos".equals(name))
    {
      return new java.util.Vector(java.util.Arrays.asList(Wizard.getInstance().getFiles((byte)(DBObject.MEDIA_MASK_VIDEO | DBObject.MEDIA_MASK_DVD | DBObject.MEDIA_MASK_BLURAY), true)));
    }
    else if ("dvds".equals(name) || "importeddvds".equals(name))
    {
      return new java.util.Vector(java.util.Arrays.asList(Wizard.getInstance().getFiles(DBObject.MEDIA_MASK_DVD, true)));
    }
    else if ("blurays".equals(name) || "importedblurays".equals(name))
    {
      return new java.util.Vector(java.util.Arrays.asList(Wizard.getInstance().getFiles(DBObject.MEDIA_MASK_BLURAY, true)));
    }
    else if ("movies".equals(name))
    {
      MediaFile[] vidFiles = Wizard.getInstance().getFiles((byte)(DBObject.MEDIA_MASK_DVD | DBObject.MEDIA_MASK_BLURAY | DBObject.MEDIA_MASK_VIDEO | DBObject.MEDIA_MASK_TV), false);
      java.util.Vector validFiles = new java.util.Vector(vidFiles.length/2);
      String movieMatcher = Sage.rez("Movie").toLowerCase();
      for (int i = 0; i < vidFiles.length; i++)
      {
        if (vidFiles[i].getGeneralType() != MediaFile.MEDIAFILE_DEFAULT_DVD_DRIVE &&
            (vidFiles[i].isDVD() || vidFiles[i].isBluRay() || (vidFiles[i].getShow() != null && vidFiles[i].getShow().getCategory().equalsIgnoreCase(movieMatcher))))
          validFiles.add(vidFiles[i]);
      }
      return validFiles;
    }
    else if ("musicvideos".equals(name))
    {
      MediaFile[] vidFiles = Wizard.getInstance().getFiles(DBObject.MEDIA_MASK_VIDEO, false);
      java.util.Vector validFiles = new java.util.Vector(vidFiles.length/2);
      String mvMatcher = Sage.rez("Music Video").toLowerCase();
      for (int i = 0; i < vidFiles.length; i++)
      {
        if ((vidFiles[i].getShow() != null && vidFiles[i].getShow().getCategory().equalsIgnoreCase(mvMatcher)))
          validFiles.add(vidFiles[i]);
      }
      return validFiles;
    }
    else if ("tv".equals(name))
    {
      return new java.util.Vector(java.util.Arrays.asList(Wizard.getInstance().getFiles(DBObject.MEDIA_MASK_TV, false)));
    }
    else if ("mediafiles".equals(name))
    {
      return new java.util.Vector(java.util.Arrays.asList(Wizard.getInstance().getFiles()));
    }
    else if ("compilations".equals(name))
    {
      Wizard wiz = Wizard.getInstance();
      Airing[] mairs = wiz.searchByExactArtist(PredefinedJEPFunction.getPersonObj(Sage.rez("Various_Artists")));
      java.util.Vector mfs = new java.util.Vector();
      for (int i = 0; i < mairs.length; i++)
      {
        MediaFile mf = wiz.getFileForAiring(mairs[i]);
        if (mf != null)
          mfs.add(mf);
      }
      return mfs;
    }
    else if ("musicplaylists".equals(name))
      return new java.util.Vector(java.util.Arrays.asList(Wizard.getInstance().getMusicPlaylists()));
    else if ("videoplaylists".equals(name))
      return new java.util.Vector(java.util.Arrays.asList(Wizard.getInstance().getVideoPlaylists()));
    else
      throw new UnsupportedOperationException("ERROR Invalid collection name specified for VFS of:" + name);
  }

  public MediaNode createCollectionView(String name, java.util.Collection dataObjects)
  {
    String lcname = name.toLowerCase();
    int byIdx = lcname.indexOf("by");
    if (byIdx == -1)
      return createCollectionView2(name, "default", new java.util.Vector(dataObjects));
    else
      return createCollectionView2(name.substring(0, byIdx), name.substring(byIdx + 2), new java.util.Vector(dataObjects));
  }
  private MediaNode createCollectionView2(String dataType, String viewName, java.util.Vector dataObjects)
  {
    String lcDataType = dataType.toLowerCase();
    String lcView = viewName.toLowerCase();
    if (lcView.equals("default"))
    {
      if (lcDataType.equalsIgnoreCase(MediaNode.DATATYPE_ACTOR))
      {
        MediaNode[] actList = new MediaNode[dataObjects.size()];
        java.util.Iterator walker = dataObjects.iterator();
        int i = 0;
        while (walker.hasNext())
        {
          String obj = walker.next().toString();
          actList[i++] = new StaticMediaNode(obj, "", null, null, obj, MediaNode.DATATYPE_ACTOR);
        }
        SimpleNodeView snv = new SimpleNodeView();
        snv.setRootNode(new SimpleGrouperMediaNode(snv, null, Sage.rez("Actors"), MediaNode.DATATYPE_VIRTUAL, null, actList));
        return snv.createVFS();
      }
      else if (lcDataType.equalsIgnoreCase(MediaNode.DATATYPE_MEDIAFILE) ||
          (!dataObjects.isEmpty() && dataObjects.get(0) instanceof MediaFile))
      {
        for (int i = 0; i < dataObjects.size(); i++)
        {
          // ensure they are all mediafile objects
          Object obj = dataObjects.get(i);
          if (!(obj instanceof MediaFile))
          {
            MediaFile mf = PredefinedJEPFunction.getMediaFileObj(obj);
            if (mf == null)
              dataObjects.remove(i--);
            else
              dataObjects.setElementAt(mf, i);
          }
        }
        SimpleNodeView snv = new SimpleNodeView();
        snv.setRootNode(new SimpleGrouperMediaFileNode(snv, null, dataType, MediaNode.DATATYPE_VIRTUAL, null, dataObjects));
        return snv.createVFS();
      }
      else if (lcDataType.equalsIgnoreCase(MediaNode.DATATYPE_AIRING) ||
          (!dataObjects.isEmpty() && dataObjects.get(0) instanceof Airing))
      {
        for (int i = 0; i < dataObjects.size(); i++)
        {
          // ensure they are all Airing objects
          Object obj = dataObjects.get(i);
          if (!(obj instanceof Airing))
          {
            Airing a = PredefinedJEPFunction.getAirObj(obj);
            if (a == null)
              dataObjects.remove(i--);
            else
              dataObjects.setElementAt(a, i);
          }
        }
        SimpleNodeView snv = new SimpleNodeView();
        snv.setRootNode(new SimpleGrouperAiringNode(snv, null, dataType, MediaNode.DATATYPE_VIRTUAL, null, dataObjects, lcDataType.indexOf("channel") != -1));
        return snv.createVFS();
      }
      else if (lcDataType.equals("musicplaylists") || lcDataType.equals("videoplaylists"))
      {
        SimpleNodeView snv = new SimpleNodeView();
        snv.setRootNode(new SimpleGrouperMediaNode(snv, null, dataType, MediaNode.DATATYPE_VIRTUAL, null,
            PlaylistGrouperMediaNode.buildNodes(snv, null, (Playlist[]) dataObjects.toArray(new Playlist[0]))));
        return snv.createVFS();
      }
      else if (lcDataType.equalsIgnoreCase(MediaNode.DATATYPE_PLAYLIST) ||
          (!dataObjects.isEmpty() && dataObjects.get(0) instanceof Playlist))
      {
        // This is a view of the items in a single playlist
        SimpleNodeView snv = new SimpleNodeView();
        snv.setRootNode(new PlaylistGrouperMediaNode(snv, null, (Playlist) dataObjects.get(0), true));
        return snv.createVFS();
      }
      else
        throw new UnsupportedOperationException("ERROR Requested to create default view of undefined datatype=" + dataType);
    }
    else if (lcView.equals("folder"))
    {
      SimpleNodeView snv = new SimpleNodeView();
      snv.setRootNode(new BrowserMediaFileNode(snv, null, "", dataObjects));
      return snv.createVFS();
    }
    else if (lcView.equals("genre") || lcView.equals("category"))
    {
      SimpleNodeView snv = new SimpleNodeView();
      snv.setRootNode(new GenreGrouperMediaFileNode(snv, null, Sage.rez("Genres"), dataObjects, "music".equals(lcDataType)));
      return snv.createVFS();
    }
    else if (lcView.equals("year"))
    {
      SimpleNodeView snv = new SimpleNodeView();
      snv.setRootNode(new YearGrouperMediaFileNode(snv, null, Sage.rez("Years"), dataObjects, "music".equals(lcDataType)));
      return snv.createVFS();
    }
    else if (lcView.equals("director"))
    {
      SimpleNodeView snv = new SimpleNodeView();
      snv.setRootNode(new RoleGrouperMediaFileNode(snv, null, Sage.rez("Directors"),
          MediaNode.DATATYPE_VIRTUAL, null, dataObjects, new int[] { Show.DIRECTOR_ROLE }, false));
      return snv.createVFS();
    }
    else if (lcView.equals("actor"))
    {
      SimpleNodeView snv = new SimpleNodeView();
      snv.setRootNode(new RoleGrouperMediaFileNode(snv, null, Sage.rez("Actors"), MediaNode.DATATYPE_VIRTUAL, null, dataObjects,
          new int[] { Show.ACTOR_ROLE, Show.ACTRESS_ROLE, Show.LEAD_ACTOR_ROLE, Show.LEAD_ACTRESS_ROLE, Show.GUEST_ROLE, Show.GUEST_STAR_ROLE}, false));
      return snv.createVFS();
    }
    else if (lcView.equals("studio"))
    {
      SimpleNodeView snv = new SimpleNodeView();
      snv.setRootNode(new StudioGrouperMediaFileNode(snv, null, Sage.rez("Studios"), dataObjects));
      return snv.createVFS();
    }
    else if (lcView.equals("title"))
    {
      SimpleNodeView snv = new SimpleNodeView();
      snv.setRootNode(new TitleGrouperMediaFileNode(snv, null, Sage.rez("Titles"), dataObjects));
      return snv.createVFS();
    }
    else if (lcView.equals("series"))
    {
      SimpleNodeView snv = new SimpleNodeView();
      snv.setRootNode(new SeriesGrouperMediaFileNode(snv, null, Sage.rez("Series"), dataObjects));
      return snv.createVFS();
    }
    else if (lcView.equals("album"))
    {
      SimpleNodeView snv = new SimpleNodeView();
      snv.setRootNode(new AlbumGrouperMediaFileNode(snv, null, Sage.rez("Albums"), MediaNode.DATATYPE_VIRTUAL, null, dataObjects));
      return snv.createVFS();
    }
    else if (lcView.equals("artist"))
    {
      SimpleNodeView snv = new SimpleNodeView();
      snv.setRootNode(new RoleGrouperMediaFileNode(snv, null, Sage.rez("Artist"),
          MediaNode.DATATYPE_VIRTUAL, null, dataObjects, new int[] { Show.ARTIST_ROLE }, true));
      return snv.createVFS();
    }
    else if (lcView.equals("channel"))
    {
      SimpleNodeView snv = new SimpleNodeView();
      snv.setRootNode(new ChannelGrouperMediaFileNode(snv, null, Sage.rez("Channels"), dataObjects));
      return snv.createVFS();
    }
    else
      throw new UnsupportedOperationException("ERROR Requested to create undefined view in VFS datatype=" + dataType + " view=" + viewName);
  }

  private MediaNodeSorter defaultSorterA = new sage.vfs.sort.NameSorter(true, false);
  private MediaNodeSorter defaultSorterD = new sage.vfs.sort.NameSorter(false, false);
  public MediaNodeSorter getDefaultSorter()
  {
    return defaultSorterA;
  }
  public MediaNodeSorter getDefaultSorter(boolean ascending)
  {
    return ascending ? defaultSorterA : defaultSorterD;
  }

  public MediaNodeSorter getSorter(String technique, boolean ascending)
  {
    if (technique == null) return null;
    MediaNodeSorter registeredSorter = null;
    if (sage.Sage.getBoolean("ui/ignore_the_when_sorting", true))
    {
      // Check for the 'ignorethe' version of the sorter
      registeredSorter = (MediaNodeSorter) sortRegistry.get(technique.toLowerCase() + (ascending ? "ignoretheA" : "ignoretheD"));
      if (registeredSorter != null)
        return registeredSorter;
    }
    registeredSorter = (MediaNodeSorter) sortRegistry.get(technique.toLowerCase() + (ascending ? "A" : "D"));
    return registeredSorter;
  }

  public DataObjectFilter getFilter(String technique, boolean matchPasses)
  {
    if (technique == null) return null;
    return (DataObjectFilter) filterRegistry.get(technique.toLowerCase() + (matchPasses ? "Y" : "N"));
  }

  private java.util.Map sortRegistry = new java.util.HashMap();
  private java.util.Map filterRegistry = new java.util.HashMap();
}
