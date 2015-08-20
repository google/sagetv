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

public final class Watched extends DBObject
{

  public Show getShow()
  {
    return (getAiring() == null) ? null : getAiring().getShow();
  }

  public Airing getAiring()
  {
    return (myAiring == null) ?
        (myAiring = Wizard.getInstance().getAiringForID(airingID)) : myAiring;
  }

  public long getWatchStart() { return watchStart; }
  public long getWatchEnd() { return watchEnd; }
  public long getRealWatchStart() { return realStart; }
  public long getRealWatchEnd() { return realEnd; }
  public long getRealDuration() { return realEnd - realStart; }
  public long getWatchDuration() { return watchEnd - watchStart; }
  public int getTitleNum() { return titleNum; }

  Watched(int inID)
  {
    super(inID);
  }

  Watched(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    airingID = readID(in, idMap);
    watchStart = in.readLong();
    watchEnd = in.readLong();
    realStart = in.readLong();
    realEnd = in.readLong();
    showID = readID(in, idMap);
    time = in.readLong();
    if (ver >= 0x40)
      titleNum = in.readInt();
  }

  boolean validate()
  {
    if (getAiring() != null)
    {
      if (Wizard.GENERATE_MEDIA_MASK)
      {
        // We only track watched for TV before 5.1
        setMediaMask(MEDIA_MASK_TV);
        // Propagate down our TV media mask
        myAiring.addMediaMask(MEDIA_MASK_TV);
        Show s = myAiring.getShow();
        if (s != null && !Wizard.getInstance().isNoShow(s))
          s.addMediaMaskRecursive(MEDIA_MASK_TV);
      }
      return true;
    }
    return false;
  }

  void update(DBObject fromMe)
  {
    Watched w = (Watched) fromMe;
    airingID = w.airingID;
    watchStart = w.watchStart;
    watchEnd = w.watchEnd;
    realStart = w.realStart;
    realEnd = w.realEnd;
    showID = w.showID;
    time = w.time;
    titleNum = w.titleNum;
    super.update(fromMe);
  }

  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    out.writeInt(airingID);
    out.writeLong(watchStart);
    out.writeLong(watchEnd);
    out.writeLong(realStart);
    out.writeLong(realEnd);
    out.writeInt(showID);
    out.writeLong(time);
    out.writeInt(titleNum);
  }

  public String toString()
  {
    StringBuilder sb = new StringBuilder("Watched[id=");
    sb.append(id);
    sb.append(" Airing=");
    sb.append(getAiring() != null ? getAiring().toString() : Integer.toString(airingID));
    sb.append(", WatchStart=");
    sb.append(Sage.df(watchStart));
    sb.append(", WatchEnd=");
    sb.append(Sage.df(watchEnd));
    sb.append(", RealStart=");
    sb.append(Sage.df(realStart));
    sb.append(", RealEnd=");
    sb.append(Sage.df(realEnd));
    if (titleNum > 0)
      sb.append(", Title=" + titleNum);
    sb.append(']');
    return sb.toString();
  }

  int airingID;
  long watchStart;
  long watchEnd;
  long realStart;
  long realEnd;
  // These two fields are stored here so there's not a dependence on the Airing object for sorting
  int showID;
  long time;
  int titleNum;
  transient Airing myAiring;

  public static final Comparator<Watched> SHOW_ID_COMPARATOR =
      new Comparator<Watched>()
  {
    public int compare(Watched w1, Watched w2)
    {
      if (w1 == w2) return 0;
      else if (w1 == null) return 1;
      else if (w2 == null) return -1;

      return (w1.showID == w2.showID)
          ? (w1.time == w2.time)
              ? w1.id - w2.id
              : Long.signum(w1.time - w2.time)
          : w1.showID - w2.showID;
    }
  };

  public static final Comparator<Watched> TIME_COMPARATOR =
      new Comparator<Watched>()
  {
    public int compare(Watched w1, Watched w2)
    {
      if (w1 == w2)
        return 0;
      else if (w1 == null)
        return 1;
      else if (w2 == null)
        return -1;

      return Long.signum((w1.realEnd == 0 ? w1.realStart : w1.realEnd)
          - (w2.realEnd == 0 ? w2.realStart : w2.realEnd));
    }
  };
}
