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
public interface MediaNode
{
  // Known data types
  public static final String DATATYPE_FILE = "File";
  public static final String DATATYPE_MEDIAFILE = "MediaFile";
  public static final String DATATYPE_VIRTUAL = "Virtual";
  public static final String DATATYPE_CATEGORY = "Category";
  public static final String DATATYPE_YEAR = "Year";
  public static final String DATATYPE_ALBUM = "Album";
  public static final String DATATYPE_ARTIST = "Artist";
  public static final String DATATYPE_SERIESINFO = "SeriesInfo";
  public static final String DATATYPE_DIRECTOR = "Director";
  public static final String DATATYPE_ACTOR = "Actor";
  public static final String DATATYPE_PLAYLIST = "Playlist";
  public static final String DATATYPE_STUDIO = "Studio";
  public static final String DATATYPE_TITLE = "Title";
  public static final String DATATYPE_CHANNEL = "Channel";
  public static final String DATATYPE_AIRING = "Airing";

  // Known sorting techniques
  public static final String SORT_BY_DATE = "date";
  public static final String SORT_BY_SIZE = "size";
  public static final String SORT_BY_NAME = "name";
  public static final String SORT_BY_NAME_IGNORE_THE = "nameignorethe";
  public static final String SORT_BY_FILENAME = "filename";
  public static final String SORT_BY_TRACK = "track";
  public static final String SORT_BY_DURATION = "duration";
  public static final String SORT_BY_TITLE = "title";
  public static final String SORT_BY_TITLE_IGNORE_THE = "titleignorethe";
  public static final String SORT_BY_ARTIST = "artist";
  public static final String SORT_BY_ARTIST_IGNORE_THE = "artistignorethe";
  public static final String SORT_BY_ALBUM = "album";
  public static final String SORT_BY_ALBUM_IGNORE_THE = "albumignorethe";
  public static final String SORT_BY_CATEGORY = "category";
  public static final String SORT_BY_YEAR = "year";
  public static final String SORT_BY_RATING = "rating";
  public static final String SORT_BY_COUNT = "count";
  public static final String SORT_BY_EPISODE_NAME = "episodename";
  public static final String SORT_BY_EPISODE_ID = "episodeid";
  public static final String SORT_BY_RATED = "rated";
  public static final String SORT_BY_RUNTIME = "runtime";
  public static final String SORT_BY_STUDIO = "studio";
  public static final String SORT_BY_STUDIO_IGNORE_THE = "studioignorethe";
  public static final String SORT_BY_FULL_PATH = "fullpath";
  public static final String SORT_BY_ORIGINAL_AIR_DATE = "originalairdate";
  public static final String SORT_BY_CHANNEL_NAME = "channelname";
  public static final String SORT_BY_INTELLIGENT = "intelligent";

  // Known filtering types
  public static final String FILTER_DIRECTORY = "directories";
  public static final String FILTER_PICTURES = "pictures";
  public static final String FILTER_VIDEOS = "videos";
  public static final String FILTER_MUSIC = "music";
  public static final String FILTER_DVD = "dvd";
  public static final String FILTER_BLURAY = "bluray";
  public static final String FILTER_TV = "tv";
  public static final String FILTER_WATCHED = "watched";
  public static final String FILTER_ARCHIVED = "archived";
  public static final String FILTER_DONTLIKE = "dontlike";
  public static final String FILTER_FAVORITE = "favorite";
  public static final String FILTER_HDTV = "hdtv";
  public static final String FILTER_MANUAL_RECORD = "manualrecord";
  public static final String FILTER_FIRSTRUN = "firstrun";
  public static final String FILTER_COMPLETE_RECORDING = "completerecording";

  public boolean isFolder();
  public MediaNode[] getChildren();
  public int getNumChildren();
  public MediaNode getChildAt(int index);
  public MediaNode getParent();

  public void setSorter(MediaNodeSorter sortie);
  public MediaNodeSorter getSorter();
  public DataObjectFilter[] getFilters();
  public void setFiltering(DataObjectFilter technique);
  public void appendFiltering(DataObjectFilter technique);

  public boolean isHierarchyRealized();
  public java.util.Collection getFinalDescendants();

  public Object getIcon(Object uiContext);
  public Object getThumbnail(Object uiContext);
  public String getPrimaryLabel();
  public String getSecondaryLabel();
  public boolean isPlayable();
  public boolean isVirtual();
  public Object getDataObject();
  public String getDataType();
  public void setChecked(boolean x);
  public boolean isChecked();
  public String getProperty(String name);

  public void refresh(); // if its using a cache; this will clear it

  public MediaSource getMediaSource();
}
