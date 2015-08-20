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

import sage.Sage;

/**
 *
 * @author Narflex
 */
public class AudioFormat extends BitstreamFormat
{
  public String toString()
  {
    return "Audio[" + formatName + " " + (samplingRate > 0 ? (samplingRate + " Hz ") : "") + (channels > 0 ? (channels + " channels ") : "") +
        (bitrate > 0 ? (bitrate/1000 + " kbps") : "") +
        (primary ? " MAIN" : "") +
        (orderIndex >= 0 ? (" idx=" + orderIndex) : "") +
        (id != null ? (" id=" + id) : "") +
        (audioTransport != null ? (" at=" + audioTransport) : "") +
        (language == null ? "]" : (" " + language + "]"));
  }
  public int getSamplingRate()
  {
    return samplingRate;
  }

  public int getBitsPerSample()
  {
    return bitsPerSample;
  }

  public int getChannels()
  {
    return channels;
  }

  public String getAudioTransport()
  {
    return audioTransport;
  }

  public void setSamplingRate(int samplingRate)
  {
    this.samplingRate = samplingRate;
  }

  public void setBitsPerSample(int bitsPerSample)
  {
    this.bitsPerSample = bitsPerSample;
  }

  public void setChannels(int channels)
  {
    this.channels = channels;
  }

  public void setAudioTransport(String at)
  {
    audioTransport = (at != null) ? at.intern() : at;
  }

  public String getLanguage()
  {
    return language;
  }

  public void setLanguage(String s)
  {
    language = s != null ? s.intern() : s;
  }

  public String getFullPropertyString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("bf=aud;");
    sb.append(super.getFullPropertyString());
    if (samplingRate > 0)
    {
      sb.append("sr=");
      sb.append(samplingRate);
      sb.append(';');
    }
    if (bitsPerSample > 0)
    {
      sb.append("bsmp=");
      sb.append(bitsPerSample);
      sb.append(';');
    }
    if (channels > 0)
    {
      sb.append("ch=");
      sb.append(channels);
      sb.append(';');
    }
    if (language != null && language.length() > 0)
    {
      sb.append("lang=");
      sb.append(escapeString(language));
      sb.append(';');
    }
    if (audioTransport != null && audioTransport.length() > 0)
    {
      sb.append("at=");
      sb.append(audioTransport);
      sb.append(';');
    }
    return sb.toString();
  }

  public static AudioFormat buildAudioFormatFromProperty(String str)
  {
    AudioFormat rv = new AudioFormat();
    int currNameStart = 0;
    int currValueStart = -1;
    for (int i = 0; i < str.length(); i++)
    {
      char c = str.charAt(i);
      if (c == '\\')
      {
        // Escaped character, so skip the next one
        i++;
        continue;
      }
      else if (c == '=')
      {
        // We found the name=value delimeter, set the value start position
        currValueStart = i + 1;
      }
      else if ((c == ';' || i == str.length() - 1) && currValueStart != -1)
      {
        if (c != ';' && i == str.length() - 1)
          i++;
        // We're at the end of the name value pair, get their values!
        String name = str.substring(currNameStart, currValueStart - 1);
        String value = str.substring(currValueStart, i);
        currNameStart = i + 1;
        currValueStart = -1;
        if (value.length() > 0)
        {
          try
          {
            if ("f".equals(name))
              rv.setFormatName(unescapeString(value));
            else if ("sr".equals(name))
              rv.samplingRate = Integer.parseInt(value);
            else if ("bsmp".equals(name))
              rv.bitsPerSample = Integer.parseInt(value);
            else if ("ch".equals(name))
            {
              rv.channels = Integer.parseInt(value);
              if (rv.channels == 5)
                rv.channels = 6; // legacy bug where we detected 5.1 as 5 channels
            }
            else if ("lang".equals(name))
              rv.language = unescapeString(value).intern();
            else if ("br".equals(name))
              rv.bitrate = Integer.parseInt(value);
            else if ("vbr".equals(name))
              rv.vbr = "1".equals(value);
            else if ("main".equals(name))
              rv.primary = "yes".equalsIgnoreCase(value);
            else if ("index".equals(name))
              rv.orderIndex = Integer.parseInt(value);
            else if ("tag".equals(name))
              rv.id = value.intern();
            else if ("at".equals(name))
              rv.audioTransport = value.intern();
          }
          catch (Exception e)
          {
            System.out.println("ERROR parsing audio format info " + str + " of:" + e);
          }
        }
      }
    }
    return rv;
  }

  public String getPrettyDesc()
  {
    StringBuffer sb = new StringBuffer(super.getPrettyDesc());
    if (samplingRate > 1000)
    {
      sb.append('@');
      sb.append(samplingRate/1000);
      if (((samplingRate/100) % 10) > 0)
      {
        sb.append('.');
        sb.append((samplingRate/100) % 10);
      }
      sb.append(sage.Sage.rez("kHz"));
    }
    if (channels == 2)
      sb.append(" " + sage.Sage.rez("Stereo"));
    else if (channels == 6)
      sb.append(" 5.1");
    else if (channels == 7)
      sb.append(" 6.1");
    else if (channels == 8)
      sb.append(" 7.1");
    if (language != null && language.length() > 0)
    {
      sb.append(' ');
      sb.append(language);
    }
    return sb.toString();
  }

  protected int samplingRate;
  protected int bitsPerSample;
  protected int channels;
  protected String language;
  protected String audioTransport;

  public static final java.util.Comparator<AudioFormat> AUDIO_FORMAT_SORTER = new java.util.Comparator<AudioFormat>()
  {
    public int compare(AudioFormat a1, AudioFormat a2)
    {
      // Don't put the primary first; just sort by the index # since that's how they're presented in the demux
      // Except on Linux....where we don't care about the DirectShow Demux at all
      if (Sage.LINUX_OS && (a1.isPrimary() != a2.isPrimary()))
        return a1.isPrimary() ? -1 : 1;
      else
        return a1.getOrderIndex() - a2.getOrderIndex();
    }
  };
}
