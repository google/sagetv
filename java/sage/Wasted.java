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

public final class Wasted extends DBObject
{

  public Airing getAiring()
  {
    return (myAiring == null) ?
        (myAiring = Wizard.getInstance().getAiringForID(airingID)) : myAiring;
  }

  Wasted(int inID)
  {
    super(inID);
  }

  Wasted(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    airingID = readID(in, idMap);
    if (ver >= 0x1E)
      manual = in.readBoolean();
  }

  boolean validate()
  {
    getAiring();
    if (myAiring == null)
      return false;
    if (Wizard.GENERATE_MEDIA_MASK)
    {
      // Check to make sure it's not for an imported media file that was deleted. This was a bug prior to 5.1.
      // The only way we can know this is because we used one of our custom externalIDs that start with MF or MP3 or WMA
      Show s = myAiring.getShow();
      if (s == null) return false;
      String sid = s.getExternalID();
      if (sid.startsWith("MF") || sid.startsWith("MP3") || sid.startsWith("WMA"))
        return false;

      // Propagate down our TV media mask
      myAiring.addMediaMask(MEDIA_MASK_TV);
      if (!Wizard.getInstance().isNoShow(s))
        s.addMediaMaskRecursive(MEDIA_MASK_TV);
    }
    return true;
  }

  void update(DBObject fromMe)
  {
    Wasted w = (Wasted) fromMe;
    airingID = w.airingID;
    manual = w.manual;
    super.update(fromMe);
  }

  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    out.writeInt(airingID);
    out.writeBoolean(manual);
  }

  public String toString()
  {
    StringBuilder sb = new StringBuilder("Wasted[id=");
    sb.append(id);
    sb.append(" Airing=");
    sb.append(getAiring());
    if (manual)
      sb.append("MANUAL");
    sb.append(']');
    return sb.toString();
  }

  public boolean isManual() { return manual; }

  int airingID;
  boolean manual;
  transient Airing myAiring;

  public static final Comparator<Wasted> AIRING_ID_COMPARATOR =
      new Comparator<Wasted>()
  {
    public int compare(Wasted m1, Wasted m2)
    {
      if (m1 == m2)
        return 0;
      else if (m1 == null)
        return 1;
      else if (m2 == null)
        return -1;

      return m1.airingID - m2.airingID;
    }
  };
}
