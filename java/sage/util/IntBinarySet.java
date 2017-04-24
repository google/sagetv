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
package sage.util;

import java.util.Arrays;

/**
 * This is a very basic binary search based class that will only store unique {@link Integer} on a
 * binary sorted array. It's primary purpose is to reduce Integer object creation which will
 * extremely rapidly grow when we are building the maps.
 * <p/>
 * We store Integer objects, but we actually search them using primitives. This works well because
 * we are only unboxing. This was also attempted with a two array approach, but this turns out to
 * be narrowly faster and more memory efficient.
 * <p/>
 * This has very minimal safe-guards, so take care when using this class.
 */
public class IntBinarySet
{
  private static int DEFAULT_SIZE = 10;

  private int size = 0;
  private Integer elementData[];

  /**
   * Create a new empty set with the initial capacity of 10.
   */
  public IntBinarySet()
  {
    this(DEFAULT_SIZE);
  }

  /**
   * Create a new empty set with the specified initial capacity.
   *
   * @param size The initial capacity.
   */
  public IntBinarySet(int size)
  {
    this.elementData = new Integer[size];
  }

  /**
   * Create a new set using the provided array directly.
   * <p/>
   * No checks are performed. The array must already be free of duplicates and sorted correctly. A
   * defensive copy will not be made and after adding any new elements, there is no guarantee that
   * the provided array will be the same array used internally.
   *
   * @param array The array to be used.
   * @param size The size of the array that is used. This can be the total length of the array.
   */
  public IntBinarySet(int array[], int size)
  {
    this.size = size;
    this.elementData = new Integer[size];
    for (int i = 0; i < size; i++)
    {
      this.elementData[i] = array[i];
    }
  }

  private void ensureCapacity(int needCapacity)
  {
    // Checking for overflow.
    if (needCapacity > elementData.length && needCapacity - elementData.length > 0)
    {
      int oldCapacity = elementData.length;
      int newCapacity = (oldCapacity + 1) * 2;
      if (newCapacity <= 0) // overflow
        newCapacity = Integer.MAX_VALUE;
      // If we are already as big as we can get.
      if (newCapacity == oldCapacity)
        throw new OutOfMemoryError();
      elementData = Arrays.copyOf(elementData, newCapacity);
    }
  }

  /**
   * Add a new int to the set.
   *
   * @param add The int to add if it does not already exist.
   * @return <code>true</code> if the int did not already exist and was added.
   */
  public boolean addInt(int add)
  {
    int index = binarySearch(add);
    if (index < 0)
    {
      ensureCapacity(size + 1);

      index = -(index + 1);
      fastAddInt(index, add);
      return true;
    }
    return false;
  }

  // Skips checks for if this is the right place to put this index. Do no use this unless you know
  // exactly where we need to insert the new value.
  private void fastAddInt(int index, int add)
  {
    if (index != size)
    {
      System.arraycopy(elementData, index, elementData, index + 1, size - index);
    }

    elementData[index] = add;
    size++;
  }

  /**
   * Remove an int from the set.
   *
   * @param remove The int to remove if it exists.
   * @return <code>true</code> if the int did exist and was removed.
   */
  public boolean removeInt(int remove)
  {
    int index = binarySearch(remove);
    if (index >= 0)
    {
      int numMoved = size - index - 1;
      if (numMoved > 0)
        System.arraycopy(elementData, index + 1, elementData, index, numMoved);
      size--;
      return true;
    }
    return false;
  }

  /**
   * Does this set contain the provided int.
   *
   * @param lookup The int to search for.
   * @return <code>true</code> if the int exists in this set.
   */
  public boolean containsInt(int lookup)
  {
    return binarySearch(lookup) >= 0;
  }

  /**
   * Get the boxed Integer object for the provided int if it is a part of this set.
   * <p/>
   * This method is intended to greatly reduce object creation when dealing with very large amounts
   * of autoboxing since beyond a certain number range, all boxing results in new object creation
   * even if it is for a number we just boxed a second ago.
   * <p/>
   * This should be used instead of {@link #containsInt(int)} if the resulting actions of true will
   * involve using an Integer.
   *
   * @param lookup The int to look up.
   * @return <code>null</code> if the set does not contain the provided int or an Integer of the
   *         same value if it does.
   */
  public Integer getInteger(int lookup)
  {
    return binarySearchReturn(lookup);
  }

  /**
   * Get a boxed Integer object if it is already a part of this set or insert a new one and return
   * a boxed Integer.
   * <p/>
   * This is intended as a speedup for use of this class for on the spot caching. This is also
   * limited to 100,000 entries before the cache will start just autoboxing without caching. This
   * should only be used as a temporary object for very intense interactions with things like maps
   * or sets when using Integer.
   * <p/>
   * This will also filter out any numbers that the JVM always caches (-128 to 127).
   *
   * @param lookup The int to look up.
   * @return An Integer of the same value if it does.
   */
  public Integer addOrGet(int lookup)
  {
    // If the number is cached anyway, there's no reason to re-cache it.
    if (lookup >= -128 && lookup <= 127)
    {
      return lookup;
    }
    else if (size < 100000) // Sanity limit.
    {
      return binarySearchAddOrGet(lookup);
    }
    else
    {
      Integer returnValue = binarySearchReturn(lookup);
      return returnValue != null ? returnValue : lookup; // autoboxed
    }
  }

  /**
   * Clears the stored values.
   */
  public void clear()
  {
    // Remove objects for GC.
    for (int i = 0; i < size; i++)
      elementData[i] = null;
    size = 0;
  }

  /**
   * Get the current number of int values in the set.
   *
   * @return The current size of this set.
   */
  public int size()
  {
    return size;
  }

  /**
   * Reduce the internal arrays to the smallest size possible.
   */
  public void trim()
  {
    if (size != elementData.length)
    {
      elementData = Arrays.copyOf(elementData, size);
    }
  }

  private Integer binarySearchAddOrGet(int key)
  {
    Integer array[] = elementData;
    int low = 0;
    int high = size - 1;

    Integer returnValue;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      returnValue = array[mid]; // create local reference
      int midVal = returnValue; // unboxing

      if (midVal < key)
        low = mid + 1;
      else if (midVal > key)
        high = mid - 1;
      else
        return returnValue; // return boxed local reference
    }

    fastAddInt(low, key);
    return elementData[low];  // key not found.
  }

  private Integer binarySearchReturn(int key)
  {
    Integer array[] = elementData;
    int low = 0;
    int high = size - 1;

    Integer returnValue;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      returnValue = array[mid]; // create local reference
      int midVal = returnValue; // unboxing

      if (midVal < key)
        low = mid + 1;
      else if (midVal > key)
        high = mid - 1;
      else
        return returnValue; // return boxed local reference
    }
    return null;  // key not found.
  }

  private int binarySearch(int key)
  {
    Integer array[] = elementData;
    int low = 0;
    int high = size - 1;

    while (low <= high) {
      int mid = (low + high) >>> 1;
      int midVal = array[mid];

      if (midVal < key)
        low = mid + 1;
      else if (midVal > key)
        high = mid - 1;
      else
        return mid; // key found
    }
    return -(low + 1);  // key not found.
  }
}
