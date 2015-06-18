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
public class VideoNavigatorView extends BasicMediaSource
{

  /** Creates a new instance of TVNavigatorView */
  public VideoNavigatorView()
  {
  }

  // When we create a new VFS; we first create this class; and that will be the wrapper for the current
  // VFS we're building underneath it. All the MediaNodes inside of it will have an outer class
  // reference back here.
  public MediaNode createVFS()
  {
    return new VideoNavigatorTopNode(this);
  }

  public MediaNode createRelativeVFS(Object relativeRoot)
  {
    throw new UnsupportedOperationException("createRelativeVFS not supported!!!");
  }

  public MediaNode createCollectionVFS(java.util.Collection dataObjects)
  {
    throw new UnsupportedOperationException("createCollectionVFS not supported!!!");
  }

  public static class VideoNavigatorTopNode extends GrouperMediaNode
  {
    public VideoNavigatorTopNode(BasicMediaSource inSource)
    {
      super(inSource, null, Sage.rez("Video"), DATATYPE_VIRTUAL, null);

      children = new MediaNode[] {
          new MoviesNavigatorView.MoviesNavigatorTopNode(inSource, this),
          new TVNavigatorView.TVNavigatorTopNode(inSource, this),
          new MusicVideosNavigatorView.MusicVideosNavigatorTopNode(inSource, this),
      };
    }

    public MediaNode[] getChildren()
    {
      return children;
    }

    public boolean isHierarchyRealized()
    {
      return false;
    }

    public java.util.Collection getFinalDescendants()
    {
      return null;
    }

    private MediaNode[] children;
  }
}
