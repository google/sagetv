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
package sage.api;

import sage.*;
import sage.vfs.*;

/**
 * Virtual content directory abstraction.
 */
public class MediaNodeAPI {
  private MediaNodeAPI() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("MediaNode", "GetMediaSource", new String[] { "Name" })
    {
      /**
       * Retrieves a MediaNode which is the root of the specified 'Media Source'. All names are case insensitive.
       * Valid names are:
       * Filesystem - provides a view of the native filesystem
       * VideoNavigator - provides various views of the imported videos
       * MusicNavigator - provides various views of the imported music
       * MusicVideosNavigator - provides various views of the imported videos with the 'music videos' category
       * MoviesNavigator - provides various views of all content with the 'Movie' category or that are DVDs or BluRays
       * TVNavigator - provides various views of recorded TV content
       * <br>
       * You can also use names that are "ContentByGrouping" or just "Content".
       * Valid values for "Content" are:
       * Clips - all video files with a duration under 10 mins (controlled by the property max_duration_to_be_a_clip)
       * Music or ImportedMusic - all imported music files
       * Videos or ImportedVideso - all imported video files
       * Picture or Photos or ImportedPictures or ImportedPhotos - all imported picture files
       * DVDs or ImportedDVDs - all imported DVDs
       * BluRays or ImportedBluRays - all imported BluRays
       * Movies - all videos or TV recordings with the 'Movie' category or that are DVDs or BluRays
       * MusicVideos - all imported videos with the 'Music Video' category
       * TV - all recorded TV content
       * MediaFiles - all files in SageTV
       * Compilations - all music files that are by 'Various Artist'
       * MusicPlaylists - all music playlists
       * VideoPlaylists - all video playlists
       * <br>
       * Valid values for "Grouping" are:
       * Folder - grouped according to their relative import path
       * Category or Genre - grouped by category, for music files they are then subgrouped by Artist and then Album
       * Year - grouped by year, for music files they are subgrouped by Album
       * Director - grouped by Director
       * Actor - grouped by actors/actress
       * Studio - grouped by the studio that produced the content if known
       * Title - grouped by title
       * Series - grouped by television series if known (SeriesInfo object)
       * Album - grouped by album
       * Artist - grouped by artist and then subgrouped by album
       * Channel - grouped by channel
       *
       * @param Name the name of the media source
       * @return a MediaNode which contains the hierarchy for the specified media source
       * @since 7.0
       *
       * @declaration public MediaNode GetMediaSource(String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        return sage.vfs.VFSFactory.getInstance().createMediaSource(getString(stack), null);
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetMediaView", new String[] { "Name", "Data" })
    {
      /**
       * Similar to the GetMediaSource API call; but the second argument allows specifying the actual data set to
       * be used for the view. The name describes the type of content or grouping just like in GetMediaSource. Can be used for
       * presenting a subset of another view or for creating a MediaNode view of a fixed list of data such as a list of MediaFiles or Actors.
       * @param Name the view name to use
       * @param Data the dataset that defines the content in the view, can be a Collection, Object[] or a single object
       * @return a MediaNode that represents the specified Data using the specified view Name
       * @since 7.0
       *
       * @declaration public MediaNode GetMediaView(String Name, Object Data);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object dataObj = stack.pop();
        String view = getString(stack);
        java.util.Collection coll = null;
        if (dataObj instanceof java.util.Collection)
          coll = (java.util.Collection) dataObj;
        else if (dataObj instanceof Object[])
          coll = new java.util.Vector(java.util.Arrays.asList((Object[]) dataObj));
        else if (dataObj != null)
        {
          coll = new java.util.Vector();
          coll.add(dataObj);
        }
        return sage.vfs.VFSFactory.getInstance().createCollectionView(view, coll);
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetRelativeMediaSource", new String[] { "Name", "RelativeRoot" })
    {
      /**
       * Creates a MediaNode view with a relative root for a specified media source. This is currently only useable
       * with the 'Filesystem' media source
       * @param Name should be Filesystem; anything else will behave like the GetMediaSource API call
       * @param RelativeRoot the subdirectory which should be the root of this view
       * @return a MediaNode that represents the relative view of the specified media source
       * @since 7.0
       *
       * @declaration public MediaNode GetRelativeMediaSource(String Name, Object RelativeRoot);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object relativeRoot = stack.pop();
        return sage.vfs.VFSFactory.getInstance().createMediaSource(getString(stack), relativeRoot);
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "IsNodeFolder", new String[] { "MediaNode" })
    {
      /**
       * Returns true if the specified MediaNode has children
       * @param MediaNode the specified MediaNode
       * @return true if the specified MediaNode has children, false otherwise
       * @since 7.0
       *
       * @declaration public boolean IsNodeFolder(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return Boolean.valueOf(node != null && node.isFolder());
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeChildren", new String[] { "MediaNode" })
    {
      /**
       * Returns an array of the children of the specified MediaNode
       * @param MediaNode the specified MediaNode
       * @return an array of the children of the specified MediaNode
       * @since 7.0
       *
       * @declaration public MediaNode[] GetNodeChildren(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        MediaNode[] rv = node != null ? node.getChildren() : null;
        return (rv == null) ? null : rv.clone();
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeNumChildren", new String[] { "MediaNode" })
    {
      /**
       * Returns the number of children of the specified MediaNode
       * @param MediaNode the specified MediaNode
       * @return the number of children of the specified MediaNode
       * @since 7.0
       *
       * @declaration public int GetNodeNumChildren(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return new Integer(node == null ? 0 : node.getNumChildren());
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeChildAt", new String[] { "MediaNode", "Index" })
    {
      /**
       * Returns the child of the specified MediaNode at the given index
       * @param MediaNode the specified MediaNode
       * @param Index the index of the child to return (0-based)
       * @return the child of the specified MediaNode at the given index
       * @since 7.0
       *
       * @declaration public MediaNode GetNodeChildAt(MediaNode MediaNode, int Index);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        int idx = getInt(stack);
        MediaNode node = getMediaNode(stack);
        return node != null ? node.getChildAt(idx) : null;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "SetNodeSort", new String[] { "MediaNode", "Technique", "Ascending" })
    {
      /**
       * Sets the sorting technique used by the specified MediaNode hierarchy. This effects all levels of the hierarchy.
       * MediaNodes that are folders are always listed first when sorting. Some of the sorting techniques
       * can also ignore 'the' as a prefix; this is controlled by the property "ui/ignore_the_when_sorting" which defaults to true.
       * Valid sorting technique names are: Date, Size, Name (can ignore the), Filename, Track, Duration, Title (can ignore the),
       * Artist (can ignore the), Album (can ignore the), Category, Year, Rating, Count, EpisodeName, EpisodeID, Rated, Runtime,
       * Studio (can ignore the), Fullpath, OriginalAirDate, ChannelName, Intelligent
       * @param MediaNode the specified MediaNode
       * @param Technique the name of the sorting technique to use, case insensitive
       * @param Ascending true if the sorting should occur in ascending order, false otherwise
       * @since 7.0
       *
       * @declaration public void SetNodeSort(MediaNode MediaNode, String Technique, boolean Ascending);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        boolean ascending = getBool(stack);
        String technique = getString(stack);
        MediaNode node = getMediaNode(stack);
        if (node != null)
          node.setSorter(VFSFactory.getInstance().getSorter(technique, ascending));
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeSortTechnique", new String[] { "MediaNode" })
    {
      /**
       * Returns the name of the current sorting technique used by the specified MediaNode hierarchy.
       * @param MediaNode the specified MediaNode
       * @return the name of the current sorting technique used by the specified MediaNode hierarchy.
       * @since 7.0
       *
       * @declaration public String GetNodeSortTechnique(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        if (node != null && node.getSorter() != null)
          return node.getSorter().getName();
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "IsNodeSortAscending", new String[] { "MediaNode" })
    {
      /**
       * Returns true if the current sorting technique used by the specified MediaNode hierarchy is in ascending order, false otherwise
       * @param MediaNode the specified MediaNode
       * @return true if the current sorting technique used by the specified MediaNode hierarchy is in ascending order, false otherwise
       * @since 7.0
       *
       * @declaration public boolean IsNodeSortAscending(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        if (node != null && node.getSorter() != null)
          return Boolean.valueOf(node.getSorter().isAscending());
        return Boolean.FALSE;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "SetNodeFilter", new String[] { "MediaNode", "Technique", "MatchPasses" })
    {
      /**
       * Sets the filtering techniques used by the specified MediaNode hierarchy. This effects all levels of the hierarchy.
       * Filters can either be inclusive or exclusive. This method will clear all other filters and set this as the only filter.
       * Valid filtering technique names are: Directories, Pictures, Videos, Music, DVD, BluRay, TV, Watched, Archived, DontLike, Favorite,
       * HDTV, ManualRecord, FirstRun, CompleteRecording
       * @param MediaNode the specified MediaNode
       * @param Technique the name of the filtering technique to use, case insensitive
       * @param MatchPasses true if the items matching the filter should be retained, false if matching items should be removed
       * @since 7.0
       *
       * @declaration public void SetNodeFilter(MediaNode MediaNode, String Technique, boolean MatchPasses);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        boolean matchPasses = getBool(stack);
        String technique = getString(stack);
        MediaNode node = getMediaNode(stack);
        node.setFiltering(VFSFactory.getInstance().getFilter(technique, matchPasses));
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeNumFilters", new String[] { "MediaNode" })
    {
      /**
       * Gets the number of filters that are currently set for the specified MediaNode.
       * @param MediaNode the specified MediaNode
       * @return the number of filters that are currently set for the specified MediaNode.
       * @since 7.0
       *
       * @declaration int GetNodeNumFilters(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        if (node != null)
        {
          DataObjectFilter[] filts = node.getFilters();
          if (filts != null)
            return new Integer(filts.length);
        }
        return new Integer(0);
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeFilterTechnique", new String[] { "MediaNode", "FilterIndex" })
    {
      /**
       * Returns the name of the current filtering technique used by the specified MediaNode hierarchy. Since
       * multiple filters can be set; an index must be specified to determine which one to get the technique of
       * @param MediaNode the specified MediaNode
       * @param FilterIndex the 0-based index of the filtering technique to retrieve
       * @return the name of the current filtering technique used by the specified MediaNode hierarchy at the specified index, null if the index is out of bounds
       * @since 7.0
       *
       * @declaration public String GetNodeFilterTechnique(MediaNode MediaNode, int FilterIndex);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        int idx = getInt(stack);
        MediaNode node = getMediaNode(stack);
        if (node != null)
        {
          DataObjectFilter[] filts = node.getFilters();
          if (filts != null && idx >= 0 && idx < filts.length)
            return filts[idx].getTechnique();
        }
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "IsNodeFilterMatching", new String[] { "MediaNode", "FilterIndex" })
    {
      /**
       * Returns the match state of the current filtering technique used by the specified MediaNode hierarchy. Since
       * multiple filters can be set; an index must be specified to determine which one to get the matching state of
       * @param MediaNode the specified MediaNode
       * @param FilterIndex the 0-based index of the filtering match state to retrieve
       * @return true if the current filtering technique used by the specified MediaNode hierarchy at the specified index is MatchPasses, false otherwise or if the index is out of bounds
       * @since 7.0
       *
       * @declaration public boolean IsNodeFilterMatching(MediaNode MediaNode, int FilterIndex);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        int idx = getInt(stack);
        MediaNode node = getMediaNode(stack);
        if (node != null)
        {
          DataObjectFilter[] filts = node.getFilters();
          if (filts != null && idx >= 0 && idx < filts.length)
            return Boolean.valueOf(filts[idx].isMatchingFilter());
        }
        return Boolean.FALSE;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "AppendNodeFilter", new String[] { "MediaNode", "Technique", "MatchPasses" })
    {
      /**
       * Adds a filtering technique to used by the specified MediaNode hierarchy. This effects all levels of the hierarchy.
       * Filters can either be inclusive or exclusive. This method will not clear other filters that have been set.
       * Valid filtering technique names are: Directories, Pictures, Videos, Music, DVD, BluRay, TV, Watched, Archived, DontLike, Favorite,
       * HDTV, ManualRecord, FirstRun, CompleteRecording
       * @param MediaNode the specified MediaNode
       * @param Technique the name of the filtering technique to use, case insensitive
       * @param MatchPasses true if the items matching the filter should be retained, false if matching items should be removed
       * @since 7.0
       *
       * @declaration public void AppendNodeFilter(MediaNode MediaNode, String Technique, boolean MatchPasses);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        boolean matchPasses = getBool(stack);
        String technique = getString(stack);
        MediaNode node = getMediaNode(stack);
        node.appendFiltering(VFSFactory.getInstance().getFilter(technique, matchPasses));
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "IsNodeHierarchyRealized", new String[] { "MediaNode" })
    {
      /**
       * Returns true if the entire set of data objects that back this MediaNode hierarchy has already been realized.
       * This will be true for fixed sets of data; but false for abstractions like the filesystem. When this is true the
       * API call GetAllNodeDescendants will return a valid result.
       * @param MediaNode the specified MediaNode
       * @return true if the entire set of data objects that back this MediaNode hierarchy has already been realized, false otherwise
       * @since 7.0
       *
       * @declaration public boolean IsNodeHierarchyRealized(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return Boolean.valueOf(node != null && node.isHierarchyRealized());
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetAllNodeDescendants", new String[] { "MediaNode" })
    {
      /**
       * Returns the data set that represents all the children under the specified MediaNode if that
       * data set has already been realized.
       * @param MediaNode the specified MediaNode
       * @return a Collection which holds all of the resulting descendants of the specified Media Node; this Collection should NOT be modified
       * @since 7.0
       *
       * @declaration public java.util.Collection GetAllNodeDescendants(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return node == null ? null : node.getFinalDescendants();
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeIcon", new String[] { "MediaNode" })
    {
      /**
       * Returns the icon image associated with the specified MediaNode. This is currently the same as GetNodeThumbnail.
       * @param MediaNode the specified MediaNode
       * @return an Object which represents the icon for this MediaNode; this may be a MetaImage or a resource path that can be used to load an image
       * @since 7.0
       *
       * @declaration public Object GetNodeIcon(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return node == null ? null : node.getIcon(stack.getUIComponent());
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeThumbnail", new String[] { "MediaNode" })
    {
      /**
       * Returns the thumbnail image associated with the specified MediaNode.
       * @param MediaNode the specified MediaNode
       * @return an Object which represents the thumbnail for this MediaNode; this may be a MetaImage or a resource path that can be used to load an image
       * @since 7.0
       *
       * @declaration public Object GetNodeThumbnail(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return node == null ? null : node.getThumbnail(stack.getUIComponent());
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodePrimaryLabel", new String[] { "MediaNode" })
    {
      /**
       * Returns a string representation of the primary data associated with the specified MediaNode suitable for display in the UI.
       * @param MediaNode the specified MediaNode
       * @return a string representation of the primary data associated with the specified MediaNode suitable for display in the UI
       * @since 7.0
       *
       * @declaration public String GetNodePrimaryLabel(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object o = stack.pop();
        if (o instanceof MediaNode)
          return ((MediaNode) o).getPrimaryLabel();
        else if (o != null)
          return o.toString();
        else
          return "";
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeSecondaryLabel", new String[] { "MediaNode" })
    {
      /**
       * Returns a string representation of the secondary data associated with the specified MediaNode suitable for display in the UI.
       * This will usually relate to whatever the current sorting technique is.
       * @param MediaNode the specified MediaNode
       * @return a string representation of the secondary data associated with the specified MediaNode suitable for display in the UI
       * @since 7.0
       *
       * @declaration public String GetNodeSecondaryLabel(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return node == null ? "" : node.getSecondaryLabel();
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "IsNodePlayable", new String[] { "MediaNode" })
    {
      /**
       * Returns true if the Object that this MediaNode wraps is suitable for passing to the Watch API call.
       * @param MediaNode the specified MediaNode
       * @return true if the Object that this MediaNode wraps is suitable for passing to the Watch API call
       * @since 7.0
       *
       * @declaration public boolean IsNodePlayable(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return Boolean.valueOf(node != null && node.isPlayable());
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "IsNodeVirtual", new String[] { "MediaNode" })
    {
      /**
       * Returns true if the specified MediaNode doesn't wrap an actual data object; but just an abstraction of a hierarchy
       * @param MediaNode the specified MediaNode
       * @return true if the specified MediaNode doesn't wrap an actual data object; but just an abstraction of a hierarchy
       * @since 7.0
       *
       * @declaration public boolean IsNodeVirtual(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return Boolean.valueOf(node != null && node.isVirtual());
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeDataObject", new String[] { "MediaNode" })
    {
      /**
       * Returns the Object that is wrapped by the specified MediaNode
       * @param MediaNode the specified MediaNode
       * @return the Object that is wrapped by the specified MediaNode
       * @since 7.0
       *
       * @declaration public Object GetNodeDataObject(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return node == null ? null : node.getDataObject();
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeDataType", new String[] { "MediaNode" })
    {
      /**
       * Returns the type of the Object that is wrapped by the specified MediaNode
       * @param MediaNode the specified MediaNode
       * @return the type of the Object that is wrapped by the specified MediaNode
       * @since 7.0
       *
       * @declaration public String GetNodeDataType(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return node == null ? "" : node.getDataType();
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeProperty", new String[] { "MediaNode", "PropertyName" })
    {
      /**
       * Returns a specific property associated with this MediaNode. This varies depending upon the data type of the
       * MediaNode. For MediaFile based nodes this will end up calling GetMediaFileMetadata.
       * @param MediaNode the specified MediaNode
       * @param PropertyName the name of the property
       * @return the value of the specified property for the specified MediaNode
       * @since 7.0
       *
       * @declaration public String GetNodeProperty(MediaNode MediaNode, String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        String name = getString(stack);
        MediaNode node = getMediaNode(stack);
        return node == null ? "" : node.getProperty(name);
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeParent", new String[] { "MediaNode" })
    {
      /**
       * Returns the MediaNode parent of the specified MediaNode
       * @param MediaNode the specified MediaNode
       * @return the MediaNode parent of the specified MediaNode or null if it doesn't have a parent
       * @since 7.0
       *
       * @declaration public MediaNode GetNodeParent(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return node == null ? null : node.getParent();
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "RefreshNode", new String[] { "MediaNode" })
    {
      /**
       * Refreshes the hierarchy associated with the specified MediaNode.
       * @param MediaNode the specified MediaNode
       * @since 7.0
       *
       * @declaration public void RefreshNode(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        if (node != null)
          node.refresh();
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "SetNodeChecked", new String[] { "MediaNode", "State" })
    {
      /**
       * Sets a flag on this MediaNode to indicate it is in the checked state. Useful for tracking multi-selection of
       * child MediaNodes.
       * @param MediaNode the specified MediaNode
       * @param State true if the MediaNode should be marked as being in the checked state, false otherwise
       * @since 7.0
       *
       * @declaration public void SetNodeChecked(MediaNode MediaNode, boolean State);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        boolean x = getBool(stack);
        MediaNode node = getMediaNode(stack);
        if (node != null)
          node.setChecked(x);
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "IsNodeChecked", new String[] { "MediaNode" })
    {
      /**
       * Returns true if the specified MediaNode had its state set to checked by the SetNodeChecked or SetAllChildrenChecked API calls.
       * @param MediaNode the specified MediaNode
       * @return true if the specified MediaNode had its state set to checked by the SetNodeChecked or SetAllChildrenChecked API calls, false otherwise
       * @since 7.0
       *
       * @declaration public boolean IsNodeChecked(MediaNode MediaNode);
       *
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        return (node == null) ? Boolean.FALSE : Boolean.valueOf(node.isChecked());
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "SetAllChildrenChecked", new String[] { "MediaNode", "State" })
    {
      /**
       * Sets a flag on all the children of this MediaNode to indicate they are in the checked state. Useful for tracking multi-selection of
       * child MediaNodes.
       * @param MediaNode the specified MediaNode
       * @param State true if all the children of the MediaNode should be marked as being in the checked state, false if they should be marked as unchecked
       * @since 7.0
       *
       * @declaration public void SetAllChildrenChecked(MediaNode MediaNode, boolean State);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        boolean x = getBool(stack);
        MediaNode node = getMediaNode(stack);
        if (node != null)
        {
          MediaNode[] kids = node.getChildren();
          for (int i = 0; kids != null && i < kids.length; i++)
            kids[i].setChecked(x);
        }
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetChildrenCheckedCount", new String[] { "MediaNode", "State" })
    {
      /**
       * Returns the number of children of the specified MediaNode that are in the specified checked state.
       * @param MediaNode the specified MediaNode
       * @param State true if the returned count should be for checked children, false if it should be for unchecked children
       * @return the number of children of the specified MediaNode that are in the specified checked state
       * @since 7.0
       *
       * @declaration public int GetChildrenCheckedCount(MediaNode MediaNode, boolean State);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        boolean x = getBool(stack);
        MediaNode node = getMediaNode(stack);
        int count = 0;
        if (node != null)
        {
          MediaNode[] kids = node.getChildren();
          for (int i = 0; kids != null && i < kids.length; i++)
            if (kids[i].isChecked() == x)
              count++;
        }
        return new Integer(count);
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetChildrenCheckedNodes", new String[] { "MediaNode", "State" })
    {
      /**
       * Returns the children of the specified MediaNode that are in the specified checked state.
       * @param MediaNode the specified MediaNode
       * @param State true if the returned list should be for checked children, false if it should be for unchecked children
       * @return an array of children of the specified MediaNode that are in the specified checked state
       * @since 7.0
       *
       * @declaration public java.util.Vector GetChildrenCheckedNodes(MediaNode MediaNode, boolean State);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        boolean x = getBool(stack);
        MediaNode node = getMediaNode(stack);
        if (node != null)
        {
          MediaNode[] kids = node.getChildren();
          java.util.Vector rv = new java.util.Vector();
          for (int i = 0; kids != null && i < kids.length; i++)
            if (kids[i].isChecked() == x)
              rv.add(kids[i]);
          return rv;
        }
        return null;
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "CreateMediaNode", new String[] { "PrimaryLabel", "SecondaryLabel", "Thumbnail", "Icon", "DataObject" })
    {
      /**
       * Creates a static MediaNode that has no parents and no children. Can be used to add arbitrary items to MediaNode lists for display in
       * the UI. The data type for the node will be Virtual.
       * @param PrimaryLabel the value to set as the primary label for the MediaNode
       * @param SecondaryLabel the value to set as the secondary label for the MediaNode
       * @param Thumbnail the Object to use as the thumbnail for the MediaNode
       * @param Icon the Object to use as the icon for the MediaNode
       * @param DataObject the Object that should be the data object for the MediaNode
       * @return a new MediaNode object that has the specified attributes
       * @since 7.0
       *
       * @declaration public MediaNode CreateMediaNode(String PrimaryLabel, String SecondaryLabel, Object Thumbnail, Object Icon, Object DataObject);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object data = stack.pop();
        Object icon = stack.pop();
        Object thumb = stack.pop();
        String secondary = getString(stack);
        String primary = getString(stack);
        return new StaticMediaNode(primary, secondary, thumb, icon, data);
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeFullPath", new String[] { "MediaNode" })
    {
      /**
       * Returns a string which represents the hierarchical path to this MediaNode. This is created by appending the primary labels
       * of all the parents up to the root of the hierarchy. The forward slash is used as a separator.
       * @param MediaNode the specified MediaNode
       * @return a string which represents the hierarchical path to this MediaNode
       * @since 7.0
       *
       * @declaration String GetNodeFullPath(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        if (node != null)
        {
          String rv = "";
          node = node.getParent();
          while (node != null)
          {
            rv = node.getPrimaryLabel() + (rv.length() == 0 ? "" : "/") + rv;
            node = node.getParent();
          }
          return rv;
        }
        return "";
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "GetNodeTypePath", new String[] { "MediaNode" })
    {
      /**
       * Returns a string which represents the hierarchical path to this MediaNode with type information only. This is created by appending the data types
       * of all the parents up to the root of the hierarchy. The forward slash is used as a separator. For Virtual nodes, it will use F if it represents
       * a folder in the import hierarchy; otherwise it'll use the primary label unless that is null, in which case it'll use V.
       * @param MediaNode the specified MediaNode
       * @return a string which represents the hierarchical type path to this MediaNode
       * @since 7.0
       *
       * @declaration String GetNodeTypePath(MediaNode MediaNode);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        MediaNode node = getMediaNode(stack);
        if (node != null)
        {
          String rv = "";
          while (node != null)
          {
            String currType;
            if (MediaNode.DATATYPE_VIRTUAL.equals(node.getDataType()))
            {
              if (node instanceof BrowserMediaFileNode)
                currType = "F";
              else
              {
                currType = node.getPrimaryLabel();
                if (currType == null)
                  currType = "V";
              }
            }
            else
              currType = node.getDataType();
            rv = currType + (rv.length() == 0 ? "" : "/") + rv;
            node = node.getParent();
          }
          return rv;
        }
        return "";
      }
    });
    rft.put(new PredefinedJEPFunction("MediaNode", "IsMediaNodeObject", 1, new String[] { "Object" })
    {
      /**
       * Returns true if the specified object is a MediaNode object. No automatic type conversion will be performed on the argument.
       * @param Object the object to test to see if it is a MediaNode object
       * @return true if the argument is a MediaNode object, false otherwise
       * @since 7.1
       *
       * @declaration public boolean IsMediaNodeObject(Object Object);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        return Boolean.valueOf(o instanceof sage.vfs.MediaNode);
      }});
  }
}
