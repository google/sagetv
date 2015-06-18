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
public class SimpleGrouperMediaNode extends GrouperMediaNode
{

  /** Creates a new instance of SimpleGrouperMediaFileNode */
  public SimpleGrouperMediaNode(BasicMediaSource inSource, BasicMediaNode inParent, String inGroupLabel, String inGroupType, Object inGroupObject, MediaNode[] inChildren)
  {
    super(inSource, inParent, inGroupLabel, inGroupType, inGroupObject);
    children = inChildren;
    for (int i = 0; i < children.length; i++)
    {
      if (children[i] instanceof BasicMediaNode)
      {
        ((BasicMediaNode) children[i]).parent = this;
      }
    }
  }

  public MediaNode[] getChildren()
  {
    return children;
  }

  // Since we don't really know what the children are doing
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
