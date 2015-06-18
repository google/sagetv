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
public abstract class BasicMediaSource implements MediaSource, MediaView
{
  public MediaNode createVFS()
  {
    // MediaView subclasses don't want to have to override this
    throw new UnsupportedOperationException();
  }
  public MediaNode createRelativeVFS(Object relativeRoot)
  {
    // MediaView subclasses don't want to have to override this
    throw new UnsupportedOperationException();
  }

  public MediaNode createCollectionVFS(java.util.Vector dataObjects)
  {
    // MediaSource subclasses don't want to have to override this
    throw new UnsupportedOperationException();
  }

  public MediaNodeSorter getSorter()
  {
    return sorter;
  }

  public boolean passesFilter(String dataType, Object obj)
  {
    for (int i = 0; i < filterList.size(); i++)
      if (!((DataObjectFilter) filterList.get(i)).passesFilter(dataType, obj))
        return false;
    return true;
  }

  public boolean appendFiltering(DataObjectFilter filter)
  {
    if (filter != null && !filterList.contains(filter))
    {
      filterList.add(filter);
      return true;
    }
    else
      return false;
  }

  public boolean setFiltering(DataObjectFilter filter)
  {
    if (filterList.isEmpty() && filter == null) return false;
    if (filterList.size() == 1 && filterList.contains(filter)) return false;
    filterList.clear();
    if (filter != null)
      filterList.add(filter);
    return true;
  }

  public void sortData(MediaNode[] data)
  {
    if (data == null || sorter == null) return;
    java.util.Arrays.sort(data, sorter);
  }

  public boolean setSorting(MediaNodeSorter newSorter)
  {
    if (newSorter == sorter || (newSorter != null && newSorter.equals(sorter)))
      return false; // redundant
    sorter = newSorter;
    return true;
  }

  public DataObjectFilter[] getFilters()
  {
    return (DataObjectFilter[]) filterList.toArray(new DataObjectFilter[0]);
  }

  protected MediaNodeSorter sorter;
  protected java.util.Vector filterList = new java.util.Vector();
}
