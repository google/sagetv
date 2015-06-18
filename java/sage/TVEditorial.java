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

/**
 *
 * @author Narflex
 */
public class TVEditorial extends DBObject
{
  TVEditorial(int inID)
  {
    super(inID);
    setMediaMask(MEDIA_MASK_TV);
  }
  TVEditorial(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    showID = readID(in, idMap);
    title = Wizard.getInstance().getTitleForID(readID(in, idMap));
    network = Wizard.getInstance().getNetworkForID(readID(in, idMap));
    airdate = in.readUTF();
    description = in.readUTF();
    imageURL = in.readUTF();
    setMediaMask(MEDIA_MASK_TV);
  }

  boolean validate()
  {
    getShow();
    if (myShow == null)
      return false;
    return true;
  }

  public Show getShow()
  {
    return (myShow != null) ? myShow : (myShow = Wizard.getInstance().getShowForID(showID));
  }

  void update(DBObject fromMe)
  {
    TVEditorial a = (TVEditorial) fromMe;
    if (showID != a.showID)
      myShow = null;
    showID = a.showID;
    title = a.title;
    network = a.network;
    airdate = a.airdate;
    description = a.description;
    imageURL = a.imageURL;
    super.update(fromMe);
  }

  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    boolean useLookupIdx = (flags & Wizard.WRITE_OPT_USE_ARRAY_INDICES) != 0;
    out.writeInt(showID);
    out.writeInt((title == null) ? 0 : (useLookupIdx ? title.lookupIdx : title.id));
    out.writeInt((network == null) ? 0 : (useLookupIdx ? network.lookupIdx : network.id));
    out.writeUTF(airdate);
    out.writeUTF(description);
    out.writeUTF(imageURL);
  }

  public String getTitle()
  {
    return (title == null) ? "" : title.name;
  }

  public String toString()
  {
    if (getShow() == null) return "BAD EDITORIAL";
    StringBuilder sb = new StringBuilder("TVEditorial[");
    sb.append(id);
    sb.append(',');
    sb.append(showID);
    sb.append(",\"");
    sb.append(getTitle());
    sb.append("\",");
    sb.append(getAirdate());
    sb.append(']');
    return sb.toString();
  }

  public String getAirdate()
  {
    return airdate;
  }

  public String getDescription()
  {
    return description;
  }

  public String getImageURL()
  {
    return imageURL;
  }

  public String getNetwork()
  {
    return (network == null) ? "" : network.name;
  }

  int showID;
  Stringer title;
  Stringer network;
  String airdate = "";
  String description = "";
  String imageURL = "";

  private transient Show myShow;

  public static final Comparator<TVEditorial> SHOW_ID_COMPARATOR =
      new Comparator<TVEditorial>()
  {
    public int compare(TVEditorial a1, TVEditorial a2)
    {
      if (a1 == a2)
        return 0;
      else if (a1 == null)
        return 1;
      else if (a2 == null)
        return -1;

      return a1.showID - a2.showID;
    }
  };
}
