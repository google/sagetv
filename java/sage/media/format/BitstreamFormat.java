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
package sage.media.format;

/**
 *
 * @author Narflex
 */
public abstract class BitstreamFormat extends MediaFormat
{
  public int getBitrate()
  {
    return bitrate;
  }

  public void setBitrate(int bitrate)
  {
    this.bitrate = bitrate;
  }

  public boolean isVbr()
  {
    return vbr;
  }

  public void setVbr(boolean vbr)
  {
    this.vbr = vbr;
  }

  // Extending classes should also override this and call this super method!!!
  public String getFullPropertyString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("f=");
    sb.append(escapeString(getFormatName()));
    sb.append(';');
    if (bitrate > 0)
    {
      sb.append("br=");
      sb.append(bitrate);
      sb.append(';');
    }
    if (vbr)
    {
      sb.append("vbr=1;");
    }
    if (orderIndex >= 0)
    {
      sb.append("index=");
      sb.append(orderIndex);
      sb.append(';');
    }
    if (primary)
      sb.append("main=yes;");
    if (id != null && id.length() > 0)
    {
      sb.append("tag=");
      sb.append(id);
      sb.append(';');
    }
    return sb.toString();
  }

  // This has to know about it's subclasses!!
  public static BitstreamFormat buildFormatFromProperty(String s)
  {
    // Figure out what kind of stream it is and then pass it onto the subclass to build it.
    int semiIdx = s.indexOf(';');
    int eqIdx = s.indexOf('=');
    if (semiIdx == -1 || eqIdx == -1 || semiIdx < eqIdx)
      return null;
    String streamType = s.substring(eqIdx + 1, semiIdx);
    if ("aud".equals(streamType))
      return AudioFormat.buildAudioFormatFromProperty(s.substring(semiIdx + 1));
    else if ("sub".equals(streamType))
      return SubpictureFormat.buildSubFormatFromProperty(s.substring(semiIdx + 1));
    else if ("vid".equals(streamType))
      return VideoFormat.buildVideoFormatFromProperty(s.substring(semiIdx + 1));
    else
      return null;
  }

  public String getPrettyDesc()
  {
    String formName = getFormatName();
    if (AC3.equals(formName))
      formName = "Dolby Digital";
    else if (EAC3.equals(formName))
      formName = "Dolby Digital Plus";
    StringBuffer sb = new StringBuffer(formName);
    if (bitrate > 1000)
    {
      if (bitrate > 2000000)
      {
        sb.append('/');
        sb.append(bitrate/1000000);
        sb.append('.');
        sb.append((bitrate/100000) % 10);
        sb.append(sage.Sage.rez("Mbps"));
      }
      else
      {
        sb.append('/');
        sb.append(bitrate/1000);
        sb.append(sage.Sage.rez("Kbps"));
      }
    }
    return sb.toString();
  }

  public boolean isPrimary()
  {
    return primary;
  }

  public void setPrimary(boolean primary)
  {
    this.primary = primary;
  }

  public int getOrderIndex()
  {
    return orderIndex;
  }

  public void setOrderIndex(int orderIndex)
  {
    this.orderIndex = orderIndex;
  }

  public String getId()
  {
    return id;
  }

  public void setId(String id)
  {
    this.id = id != null ? id.intern() : id;
  }

  protected int bitrate;
  protected boolean vbr;
  protected boolean primary;
  protected int orderIndex = -1;
  protected String id;
}
