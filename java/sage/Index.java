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
package sage;

public final class Index
{
  Index(java.util.Comparator inComp)
  {
    this((byte)0, inComp);
  }

  Index(byte inIndexCode, java.util.Comparator inComp)
  {
    data = new DBObject[Wizard.INC_SIZE];
    indexCode = inIndexCode;
    comp = inComp;
  }

  void add(DBObject addMe)
  {
    int index = binarySearch(addMe);
    if (index < 0) index = -(index + 1);

    if ((index == table.num) && (data.length > table.num))
    {
      data[index] = addMe;
    }
    else
    {
      DBObject[] newData = data;
      if (data.length == table.num)
      {
        newData = new DBObject[table.num + Wizard.INC_SIZE];
        if (index > 0) System.arraycopy(data, 0, newData, 0, index);
      }
      if (index < table.num) System.arraycopy(data, index, newData, index + 1, table.num - index);
      newData[index] = addMe;
      data = newData;
    }
  }

  boolean remove(DBObject removeMe)
  {
    int index = findExactIndex(removeMe);
    if (index < 0)
    {
      if (Sage.DBG) System.out.println("BAD Remove of non-existent " + removeMe + " idxCode=" + indexCode);
      return false;
    }

    if (index < table.num - 1)
    {
      System.arraycopy(data, index + 1, data, index, table.num - index - 1);
    }
    data[table.num - 1] = null;
    return true;
  }

  boolean update(DBObject updateMe, DBObject newMe)
  {
    // If they're the same by this comparator, there's no need to do anything
    int originalcmp = comp.compare(updateMe, newMe);
    if (originalcmp == 0) return true;

    int oldIndex = findExactIndex(updateMe);
    if (oldIndex < 0)
    {
      if (Sage.DBG) System.out.println("BAD Update of non-existent " + updateMe + " to " + newMe + " idxCode=" + indexCode);
      return false;
    }

    int low, high;
    /*
     * 10-25-02 Previously, high and then low below were oldIndex - 1 and oldIndex + 1. I changed
     * them to be oldIndex because there's no reason the change needs to alter the position in the index
     */
    if (originalcmp > 0)
    {
      low = 0;
      high = oldIndex;
    }
    else
    {
      low = oldIndex;
      high = table.num - 1;
    }
    int mid = (low + high) >> 1;
    while (low <= high)
    {
      mid = (low + high) >> 1;
    DBObject midVal = data[mid];
    int cmp = comp.compare(midVal, newMe);

    if (cmp < 0)
      low = mid + 1;
    else if (cmp > 0)
      high = mid - 1;
    else
      break;
    }
    if (low > high)
      mid = low;  // key not found, this is the insertion point

    // This means we're moving our position to later in the array,
    // so we need to adjust down an index because the original object
    // will effectively be removed as part of this insertion
    if (originalcmp < 0)
      mid--;

    // mid is the new index we want it at
    if (mid < oldIndex)
    {
      // Shift the block to the right and put the updated one at the left edge
      for (int i = oldIndex; i > mid; i--)
        data[i] = data[i - 1];
    }
    else if (mid > oldIndex)
    {
      // Shift the block to the left and put the updated one at the right edge
      for (int i = oldIndex; i < mid; i++)
        data[i] = data[i + 1];
    }
    // else if they're equal, we've got nothing to do!
    data[mid] = updateMe;
    return true;
  }

  DBObject getSingle(DBObject matchMe)
  {
    try {
      table.acquireReadLock();
      int index = binarySearch(matchMe);
      if (index < 0) return null;
      else return data[index];
    } finally {
      table.releaseReadLock();
    }
  }

  // This doesn't function correctly if the index isn't sorted by ID
  DBObject getSingle(int idMatch)
  {
    if (comp != DBObject.ID_COMPARATOR)
      throw new InternalError("CANNOT CALL GET SINGLE FOR ID ON THAT INDEX! tableCode=" + table.tableCode +
          " indexCode=" + indexCode);
    try {
      table.acquireReadLock();
      int index = binarySearch(idMatch);
      if (index < 0) return null;
      else return data[index];
    } finally {
      table.releaseReadLock();
    }
  }

  void clear()
  {
    try {
      table.acquireWriteLock();
      for (int i = 0; i < data.length; i++)
        data[i] = null;
    } finally {
      table.releaseWriteLock();
    }
  }

  void check()
  {
    try {
      table.acquireWriteLock();
      DBObject[] newData = new DBObject[data.length];
      System.arraycopy(data, 0, newData, 0, table.num);
      mergeSort(data, newData, 0, table.num);
      data = newData;
      newData = null;
    } finally {
      table.releaseWriteLock();
    }
  }

  public int binarySearch(Object key)
  {
    int low = 0;
    int high = table.num - 1;

    while (low <= high)
    {
      int mid = (low + high) >> 1;
    Object midVal = data[mid];
    int cmp = comp.compare(midVal, key);

    if (cmp < 0)
      low = mid + 1;
    else if (cmp > 0)
      high = mid - 1;
    else
      return mid; // key found
    }

    return -(low + 1);  // key not found.
  }

  public int binarySearch(int key)
  {
    int low = 0;
    int high = table.num - 1;

    while (low <= high)
    {
      int mid = (low + high) >> 1;
    DBObject midVal = data[mid];
    int cmp = (midVal == null) ? 1 : (midVal.id - key);

    if (cmp < 0)
      low = mid + 1;
    else if (cmp > 0)
      high = mid - 1;
    else
      return mid; // key found
    }

    return -(low + 1);  // key not found.
  }

  private void mergeSort(Object src[], Object dest[], int low, int high)
  {
    int length = high - low;

    // Insertion sort on smallest arrays
    if (length < 7)
    {
      Object z;
      for (int i=low; i<high; i++)
        for (int j=i; j>low && comp.compare(dest[j-1], dest[j])>0; j--)
        {
          z = dest[j];
          dest[j] = dest[j - 1];
          dest[j - 1] = z;
        }
      return;
    }

    // Recursively sort halves of dest into src
    int mid = (low + high) >> 1;
      mergeSort(dest, src, low, mid);
      mergeSort(dest, src, mid, high);

      // If list is already sorted, just copy from src to dest.  This is an
      // optimization that results in faster sorts for nearly ordered lists.
      if (comp.compare(src[mid-1], src[mid]) <= 0)
      {
        System.arraycopy(src, low, dest, low, length);
        return;
      }

      // Merge sorted halves (now in src) into dest
      for(int i = low, p = low, q = mid; i < high; i++)
      {
        if (q>=high || p<mid && comp.compare(src[p], src[q]) <= 0)
          dest[i] = src[p++];
        else
          dest[i] = src[q++];
      }
  }

  private int findExactIndex(DBObject matchMe)
  {
    int index = binarySearch(matchMe);
    if (index < 0) return index;
    if (data[index].id == matchMe.id) return index;

    for (int i = -1; (i + index >= 0) && (comp.compare(matchMe, data[i + index]) == 0); i--)
      if (data[index + i].id == matchMe.id)
        return index + i;
    for (int i = 1; (i + index < table.num) && (comp.compare(matchMe, data[i + index]) == 0); i++)
      if (data[index + i].id == matchMe.id)
        return index + i;
    return index;
  }

  void massRemove(java.util.Set killUs)
  {
    for (int i = 0; i < table.num; i++)
    {
      if (killUs.contains(data[i]))
        data[i] = null;
    }
  }

  Table table;
  byte indexCode;
  java.util.Comparator comp;
  DBObject[] data;
}
