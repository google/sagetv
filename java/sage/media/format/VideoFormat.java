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
public class VideoFormat extends BitstreamFormat
{
  public String toString()
  {
    return "Video[" + formatName + " " + (fps > 0 ? (fps + " fps ") : "") + ((width > 0 && height > 0) ? (width + "x" + height + " ") : "") +
        ((arNum > 0 && arDen > 0) ? (arNum + ":" + arDen + " ") : "") +
        (bitrate > 0 ? (bitrate/1000 + " kbps ") : "") + (interlaced ? "interlaced" : "progressive") +
        (id != null ? (" id=" + id) : "") + (framePack3D != null ? (" 3D-" + framePack3D) : "") + "]";
  }

  public float getFps()
  {
    return fps;
  }

  public int getFpsNum()
  {
    return fpsNum;
  }

  public int getFpsDen()
  {
    return fpsDen;
  }

  public float getAspectRatio()
  {
    return aspectRatio;
  }

  public int getArNum()
  {
    return arNum;
  }

  public int getArDen()
  {
    return arDen;
  }

  public int getWidth()
  {
    return width;
  }

  public int getHeight()
  {
    return height;
  }

  public String getColorspace()
  {
    return colorspace;
  }

  public boolean isInterlaced()
  {
    return interlaced;
  }

  public void setFps(float fps)
  {
    this.fps = fps;
  }

  public void setFpsNum(int fpsNum)
  {
    this.fpsNum = fpsNum;
  }

  public void setFpsDen(int fpsDen)
  {
    this.fpsDen = fpsDen;
  }

  public void setAspectRatio(float aspectRatio)
  {
    this.aspectRatio = aspectRatio;
  }

  public void setArNum(int arNum)
  {
    this.arNum = arNum;
  }

  public void setArDen(int arDen)
  {
    this.arDen = arDen;
  }

  public void setWidth(int width)
  {
    this.width = width;
  }

  public void setHeight(int height)
  {
    this.height = height;
  }

  public void setColorspace(String colorspace)
  {
    this.colorspace = colorspace != null ? colorspace.intern() : colorspace;
  }

  public void setInterlaced(boolean interlaced)
  {
    this.interlaced = interlaced;
  }

  public String getFullPropertyString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("bf=vid;");
    sb.append(super.getFullPropertyString());
    if (fps > 0)
    {
      sb.append("fps=");
      sb.append(fps);
      sb.append(';');
    }
    if (fpsNum > 0)
    {
      sb.append("fpsn=");
      sb.append(fpsNum);
      sb.append(';');
    }
    if (fpsDen > 0)
    {
      sb.append("fpsd=");
      sb.append(fpsDen);
      sb.append(';');
    }
    if (aspectRatio > 0)
    {
      sb.append("ar=");
      sb.append(aspectRatio);
      sb.append(';');
    }
    if (arNum > 0)
    {
      sb.append("arn=");
      sb.append(arNum);
      sb.append(';');
    }
    if (arDen > 0)
    {
      sb.append("ard=");
      sb.append(arDen);
      sb.append(';');
    }
    if (width > 0)
    {
      sb.append("w=");
      sb.append(width);
      sb.append(';');
    }
    if (height > 0)
    {
      sb.append("h=");
      sb.append(height);
      sb.append(';');
    }
    if (interlaced)
      sb.append("lace=1;");
    if (colorspace != null && colorspace.length() > 0)
    {
      sb.append("cs=");
      sb.append(escapeString(colorspace));
      sb.append(';');
    }
    if (framePack3D != null && framePack3D.length() > 0)
    {
      sb.append("fpa=");
      sb.append(escapeString(framePack3D));
      sb.append(';');
    }
    return sb.toString();
  }

  public static VideoFormat buildVideoFormatFromProperty(String str)
  {
    VideoFormat rv = new VideoFormat();
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
            else if ("fps".equals(name))
              rv.fps = Float.parseFloat(value);
            else if ("fpsd".equals(name))
              rv.fpsDen = Integer.parseInt(value);
            else if ("fpsn".equals(name))
              rv.fpsNum = Integer.parseInt(value);
            else if ("ar".equals(name))
              rv.aspectRatio = Float.parseFloat(value);
            else if ("ard".equals(name))
              rv.arDen = Integer.parseInt(value);
            else if ("arn".equals(name))
              rv.arNum = Integer.parseInt(value);
            else if ("w".equals(name))
              rv.width = Integer.parseInt(value);
            else if ("h".equals(name))
              rv.height = Integer.parseInt(value);
            else if ("lace".equals(name) && "1".equals(value))
              rv.interlaced = true;
            else if ("cs".equals(name))
              rv.colorspace = unescapeString(value).intern();
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
            else if ("fpa".equals(name))
              rv.framePack3D = value.intern();
          }
          catch (Exception e)
          {
            System.out.println("ERROR parsing video format info " + str + " of:" + e);
          }
        }
      }
    }
    return rv;
  }

  public static VideoFormat buildVideoFormatForResolution(String s)
  {
    if (s == null || s.length() == 0) return null;
    VideoFormat rv = new VideoFormat();
    rv.setFormatName(s);
    try
    {
      int xidx = s.indexOf('x');
      if (xidx == -1)
        return rv;
      rv.width = Integer.parseInt(s.substring(0, xidx));
      int lastIdx = xidx+1;
      while (Character.isDigit(s.charAt(lastIdx)))
      {
        lastIdx++;
        if (lastIdx >= s.length())
          break;
      }
      rv.height = Integer.parseInt(s.substring(xidx + 1, lastIdx));
      if (lastIdx < s.length() && s.charAt(lastIdx) == 'i')
        rv.interlaced = true;
      int atSym = s.indexOf('@');
      if (atSym != -1)
      {
        lastIdx = atSym+1;
        while (Character.isDigit(s.charAt(lastIdx)) || s.charAt(lastIdx) == '.')
        {
          lastIdx++;
          if (lastIdx >= s.length())
            break;
        }
        rv.fps = Float.parseFloat(s.substring(atSym + 1, lastIdx));
      }
      return rv;
    }
    catch (Exception e)
    {
      System.out.println("Error parsing resolution:" + s + " of:" + e);
      return rv;
    }
  }

  public String getPrettyResolution()
  {
    // Check for a nice pretty format name
    if ((width == 528 || width == 720 || width == 704) && height == 480 && interlaced)
      return "480i";
    else if ((width == 528 || width == 720 || width == 704) && height == 480 && !interlaced)
      return "480p";
    else if ((width == 720 || width == 704) && height == 576 && interlaced)
      return "576i";
    else if ((width == 720 || width == 704) && height == 576 && !interlaced)
      return "576p";
    else if (height == 720 && width == 1280 && !interlaced)
      return "720p";
    else if ((height == 1080 || height == 1088) && width == 1920 && interlaced)
      return "1080i";
    else if ((height == 1080 || height == 1088) && width == 1920 && !interlaced)
      return "1080p";
    else
      return null;
  }

  public String getPrettyDesc()
  {
    StringBuffer sb = new StringBuffer(super.getPrettyDesc());
    if (arNum > 0 && arDen > 0 && arNum < 50 && arDen < 50)
    {
      if (arNum == 4 && arDen == 3)
        sb.append(" 4:3");
      else if (arNum == 16 && arDen == 9)
        sb.append(" 16:9");
      else if (arNum == arDen)
        sb.append(" 1:1");
      else
      {
        sb.append(' ');
        sb.append(arNum);
        sb.append(':');
        sb.append(arDen);
      }
    }
    if (width > 0 && height > 0)
    {
      sb.append(' ');
      String prettyRez = getPrettyResolution();
      if (prettyRez != null)
      {
        sb.append(prettyRez);
      }
      else
      {
        sb.append(width);
        sb.append('x');
        sb.append(height);
        if (interlaced)
          sb.append('i');
      }
      if (fps > 0 && fps > 2)
      {
        sb.append('@');
        if (fpsNum == 30000 && fpsDen == 1001)
          sb.append("29.97fps");
        else
        {
          sb.append(Math.round(fps));
          sb.append("fps");
        }
      }
    }
    if (framePack3D != null && framePack3D.length() > 0)
    {
      sb.append(" 3D");
    }
    return sb.toString();
  }

  protected float fps;
  protected int fpsNum;
  protected int fpsDen;
  protected float aspectRatio;
  protected int arNum;
  protected int arDen;
  protected int width;
  protected int height;
  protected String colorspace;
  protected boolean interlaced;
  protected String framePack3D;
}
