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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

/*
 * THESE CANNOT HAVE NULL NAMES
 */
public final class Stringer extends DBObject implements Comparable<Stringer>
{

  Stringer(int inID)
  {
    super(inID);
  }

  Stringer(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    name = in.readUTF();
    ignoreCaseHash = name.toLowerCase().hashCode();
  }

  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    out.writeUTF(name);
  }

  public String toString()
  {
    return name;
  }

  void update(DBObject x)
  {
    Stringer fromMe = (Stringer) x;
    name = fromMe.name;
    ignoreCaseHash = name.toLowerCase().hashCode();
    super.update(fromMe);
  }

  public int compareTo(Stringer o)
  {
    return toString().compareTo(o.toString());
  }

  // NOTE: Any string passed into this method must already be all lowercase.
  public boolean equalsIgnoreCase(String compare)
  {
    String thisName = name;
    int thisIgnoreHash = ignoreCaseHash;
    // The hash could be 0, but it's not going to be any more often than any other hash and in case
    // we miss a case where the title is set, this covers that problem.
    if (thisIgnoreHash == 0)
      return thisName.equalsIgnoreCase(compare);
    return compare == thisName ||
      (compare.hashCode() == thisIgnoreHash && compare.length() == thisName.length() &&
        thisName.regionMatches(true, 0, compare, 0, thisName.length()));
  }

  public boolean equalsIgnoreCase(Stringer compare)
  {
    String thisName = name;
    String compareName = compare.name;
    int thisIgnoreHash = ignoreCaseHash;
    // The hash could be 0, but it's not going to be any more often than any other hash and in case
    // we miss a case where the title is set, this covers that problem.
    if (thisIgnoreHash == 0)
      return thisName.equalsIgnoreCase(compareName);
    return compareName == thisName ||
      (compare.ignoreCaseHash == thisIgnoreHash && compareName.length() == thisName.length() &&
        thisName.regionMatches(true, 0, compareName, 0, thisName.length()));
  }

  String name;
  // Used for case insensitive comparisons.
  transient int ignoreCaseHash;

  public static final Comparator<Stringer> NAME_COMPARATOR =
      new Comparator<Stringer>()
  {
    public int compare(Stringer o1, Stringer o2)
    {
      if (o1 == o2)
        return 0;
      else if (o1 == null)
        return 1;
      else if (o2 == null)
        return -1;

      return o1.name.compareTo(o2.name);
    }
  };
}
