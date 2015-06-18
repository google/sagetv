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
public class StandardFilesystemView extends BasicMediaSource
{

  /** Creates a new instance of StandardFilesystemView */
  public StandardFilesystemView()
  {
    root = null;
    sorter = VFSFactory.getInstance().getDefaultSorter();
  }

  // When we create a new VFS; we first create this class; and that will be the wrapper for the current
  // VFS we're building underneath it. All the MediaNodes inside of it will have an outer class
  // reference back here.
  public MediaNode createVFS()
  {
    if (root == null)
    {
      if (Sage.WINDOWS_OS)
        return new FilesystemMediaNode(this, null, null);
      else
        return new FilesystemMediaNode(this, new java.io.File("/"), null);
    }
    else
      return new FilesystemMediaNode(this, root, null);
  }

  public MediaNode createRelativeVFS(Object relativeRoot)
  {
    if (relativeRoot instanceof java.io.File || relativeRoot == null)
      root = (java.io.File)relativeRoot;
    else if (relativeRoot instanceof FilesystemMediaNode)
      root = (java.io.File) ((FilesystemMediaNode)relativeRoot).getDataObject();
    else
      root = new java.io.File(relativeRoot.toString());
    return createVFS();
  }

  private java.io.File root;
}
