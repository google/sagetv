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

public class MPEG2EncodingParams
{
  public static final MPEG2EncodingParams DEFAULT_HW_QUALITY;
  public static final MPEG2EncodingParams DEFAULT_SW_QUALITY;
  static
  {
    DEFAULT_HW_QUALITY = new MPEG2EncodingParams();
    DEFAULT_SW_QUALITY = new MPEG2EncodingParams();
    DEFAULT_SW_QUALITY.videobitrate = new Integer(1700000);
    DEFAULT_SW_QUALITY.outputstreamtype = new Integer(10);
    DEFAULT_HW_QUALITY.outputstreamtype = new Integer(10);
  }
  /*
	 People keep asking for this list, so I'll make it convenient for myself:
Quality  VBR AvgVideoRate Resolution AudioBitrate
Best     No  6Mbps        720x480    384kbps
Great    No  3.8Mbps      720x480    384kbps
Good     No  2.8Mbps      480x480    256kbps
Fair     No  1.7Mbps      480x480    256kbps
CVD      Yes 2.5Mbps      352x480    224kbps
SVCD ELP Yes 1.152Mbps    480x480    224kbps
SVCD LP  Yes 1.6Mbps      480x480    224kbps
SVCD SP  Yes 2Mbps        480x480    224kbps
DVD ELP  Yes 3Mbps        720x480    384kbps
DVD LP   Yes 4.8Mbps      720x480    384kbps
DVD SP   Yes 6.4Mbps      720x480    384kbps
MPEG2Max No  12Mbps       720x480    384kbps

NOTE1: For Creative VBDVCR, 640x480 is used instead of 720x480 and 320x480 insted of 352x480
NOTE2: AudioSampling of 48kHz is used for all of these, except CVD & SVCD which are 44.1kHz
   */
  static final String[] DEFAULT_PYTHON_ENCODINGS = {
    "videobitrate=6000000|vbr=0|width=720|height=480|audiobitrate=384|outputstreamtype=10|audiosampling=48000",
    "videobitrate=3800000|vbr=0|width=720|height=480|audiobitrate=384|outputstreamtype=10|audiosampling=48000",
    "videobitrate=2800000|vbr=0|width=480|height=480|audiobitrate=256|outputstreamtype=10|audiosampling=48000",
    "videobitrate=1700000|vbr=0|width=480|height=480|audiobitrate=256|outputstreamtype=10|audiosampling=48000",
    "videobitrate=2500000|peakvideobitrate=3000000|vbr=1|width=352|height=480|audiobitrate=224|outputstreamtype=0|audiosampling=48000",
    "videobitrate=1152000|peakvideobitrate=1440000|vbr=1|width=480|height=480|audiobitrate=224|outputstreamtype=12|audiosampling=44100",
    "videobitrate=1600000|peakvideobitrate=2000000|vbr=1|width=480|height=480|audiobitrate=224|outputstreamtype=12|audiosampling=44100",
    "videobitrate=2000000|peakvideobitrate=2500000|vbr=1|width=480|height=480|audiobitrate=224|outputstreamtype=12|audiosampling=44100",
    "videobitrate=3000000|peakvideobitrate=4400000|vbr=1|width=720|height=480|audiobitrate=384|outputstreamtype=10|audiosampling=48000",
    "videobitrate=4800000|peakvideobitrate=6000000|vbr=1|width=720|height=480|audiobitrate=384|outputstreamtype=10|audiosampling=48000",
    "videobitrate=6400000|peakvideobitrate=8000000|vbr=1|width=720|height=480|audiobitrate=384|outputstreamtype=10|audiosampling=48000",
    "videobitrate=12000000|vbr=0|width=720|height=480|audiobitrate=384|outputstreamtype=0|audiosampling=48000",
    "videobitrate=1150000|vbr=0|width=352|height=240|audiobitrate=224|outputstreamtype=11|audiosampling=44100"
  };
  static final String[] DEFAULT_PYTHON_ENCODING_NAMES = {
    Sage.rez("Best"),
    Sage.rez("Great"),
    Sage.rez("Good"),
    Sage.rez("Fair"),
    "CVD",
    Sage.rez("SVCD_Extra_Long_Play"),
    Sage.rez("SVCD_Long_Play"),
    Sage.rez("SVCD_Standard_Play"),
    Sage.rez("DVD_Extra_Long_Play"),
    Sage.rez("DVD_Long_Play"),
    Sage.rez("DVD_Standard_Play"),
    Sage.rez("MPEG2_Max_Quality"),
    "VCD"
  };

  static final String[] DEFAULT_DIVX_ENCODINGS = {
    "videobitrate=4000000|peakvideobitrate=4000000|vbr=0|width=720|height=480|audiobitrate=224|outputstreamtype=101|audiosampling=48000|ipb=2|aspectratio=0",
    "videobitrate=3000000|peakvideobitrate=3000000|vbr=0|width=720|height=480|audiobitrate=224|outputstreamtype=101|audiosampling=48000|ipb=2|aspectratio=0",
    "videobitrate=2000001|peakvideobitrate=2000001|vbr=0|width=720|height=480|audiobitrate=128|outputstreamtype=101|audiosampling=48000|ipb=2|aspectratio=0",
    "videobitrate=2000000|peakvideobitrate=2000000|vbr=0|width=640|height=480|audiobitrate=128|outputstreamtype=101|audiosampling=48000|ipb=2|deinterlace=1|aspectratio=0",
    "videobitrate=768000|peakvideobitrate=768000|vbr=0|width=352|height=240|audiobitrate=128|outputstreamtype=101|audiosampling=48000|ipb=2|deinterlace=1|aspectratio=0",
    "videobitrate=200000|peakvideobitrate=200000|vbr=0|width=176|height=144|audiobitrate=64|outputstreamtype=101|audiosampling=48000|fps=15|ipb=2|deinterlace=1|aspectratio=0",
    "videobitrate=128000|peakvideobitrate=128000|vbr=0|width=176|height=144|audiobitrate=64|outputstreamtype=101|audiosampling=48000|fps=15|ipb=2|deinterlace=1|aspectratio=0",
  };
  static final String[] DEFAULT_DIVX_ENCODING_NAMES = {
    Sage.rez("DivX_Certified_Home_Theater_Highest_Quality"),
    Sage.rez("DivX_Certified_Home_Theater_High_Quality"),
    Sage.rez("DivX_Certified_Home_Theater_Medium_Quality"),
    Sage.rez("DivX_Certified_Portable_High_Quality"),
    Sage.rez("DivX_Certified_Portable_Medium_Quality"),
    Sage.rez("DivX_Certified_Handheld_High_Quality"),
    Sage.rez("DivX_Certified_Handheld_Medium_Quality"),
  };

  static final String[] DEFAULT_MPEG4_ENCODINGS = {
    "videobitrate=4000000|vbr=0|width=720|height=480|audiobitrate=224|outputstreamtype=102|audiosampling=48000",
    "videobitrate=3000000|vbr=0|width=720|height=480|audiobitrate=224|outputstreamtype=102|audiosampling=48000",
    "videobitrate=2000000|vbr=0|width=480|height=480|audiobitrate=226|outputstreamtype=102|audiosampling=48000",
    "videobitrate=1500000|vbr=0|width=480|height=480|audiobitrate=128|outputstreamtype=102|audiosampling=48000",
    "videobitrate=700000|vbr=0|width=352|height=240|audiobitrate=128|outputstreamtype=102|audiosampling=48000",
  };
  static final String[] DEFAULT_MPEG4_ENCODING_NAMES = {
    Sage.rez("MPEG4_High_Quality"),
    Sage.rez("MPEG4_Good_Quality"),
    Sage.rez("MPEG4_Standard_Play"),
    Sage.rez("MPEG4_Long_Play"),
    Sage.rez("MPEG4_Extended_Play"),
  };
  static final String[] DEFAULT_SW_ENCODINGS = {
    "videobitrate=6000000|vbr=0|width=720|height=480|audiobitrate=384|outputstreamtype=10|audiosampling=48000",
    "videobitrate=3800000|vbr=0|width=720|height=480|audiobitrate=384|outputstreamtype=10|audiosampling=48000",
    "videobitrate=2800000|vbr=0|width=480|height=480|audiobitrate=256|outputstreamtype=10|audiosampling=48000",
    "videobitrate=1700000|vbr=0|width=480|height=480|audiobitrate=256|outputstreamtype=10|audiosampling=48000",
    "videobitrate=1152000|peakvideobitrate=1440000|vbr=1|width=480|height=480|audiobitrate=224|outputstreamtype=12|audiosampling=44100",
    "videobitrate=1600000|peakvideobitrate=2000000|vbr=1|width=480|height=480|audiobitrate=224|outputstreamtype=12|audiosampling=44100",
    "videobitrate=2000000|peakvideobitrate=2500000|vbr=1|width=480|height=480|audiobitrate=224|outputstreamtype=12|audiosampling=44100",
    "videobitrate=3000000|peakvideobitrate=4400000|vbr=1|width=720|height=480|audiobitrate=384|outputstreamtype=10|audiosampling=48000",
    "videobitrate=4800000|peakvideobitrate=6000000|vbr=1|width=720|height=480|audiobitrate=384|outputstreamtype=10|audiosampling=48000",
    "videobitrate=6400000|peakvideobitrate=8000000|vbr=1|width=720|height=480|audiobitrate=384|outputstreamtype=10|audiosampling=48000",
    "videobitrate=1150000|vbr=0|width=352|height=240|audiobitrate=224|outputstreamtype=11|audiosampling=44100",
    //		"videobitrate=1100000|peakvideobitrate=1100000|vbr=1|width=640|height=480|audiobitrate=128|outputstreamtype=101|audiosampling=48000|ipb=2|deinterlace=1|aspectratio=0",
    //		"videobitrate=768000|peakvideobitrate=768000|vbr=1|width=352|height=240|audiobitrate=128|outputstreamtype=101|audiosampling=48000|ipb=2|deinterlace=1|aspectratio=0",
  };
  static final String[] DEFAULT_SW_ENCODING_NAMES = {
    Sage.rez("Best"),
    Sage.rez("Great"),
    Sage.rez("Good"),
    Sage.rez("Fair"),
    Sage.rez("SVCD_Extra_Long_Play"),
    Sage.rez("SVCD_Long_Play"),
    Sage.rez("SVCD_Standard_Play"),
    Sage.rez("DVD_Extra_Long_Play"),
    Sage.rez("DVD_Long_Play"),
    Sage.rez("DVD_Standard_Play"),
    "VCD",
    //		Sage.rez("DivX_Certified_Home_Theater"),
    //		Sage.rez("DivX_Certified_Portable"),
  };
  static final String[] DEFAULT_HDPVR_ENCODINGS = {
    "videobitrate=12000000|outputstreamtype=1",
    "videobitrate=7600000|outputstreamtype=1",
    "videobitrate=5600000|outputstreamtype=1",
    "videobitrate=3400000|outputstreamtype=1",
  };
  static final String[] DEFAULT_HDPVR_ENCODING_NAMES = {
    Sage.rez("Best") + "-H.264",
    Sage.rez("Great") + "-H.264",
    Sage.rez("Good") +  "-H.264",
    Sage.rez("Fair") +  "-H.264",
  };
  /*
		PROP_AUDIOOUTPUTMODE_MONO		= 0x03,
		PROP_AUDIOOUTPUTMODE_STEREO		= 0x00,
		PROP_AUDIOOUTPUTMODE_DUAL		= 0x02,
		PROP_AUDIOOUTPUTMODE_JOINT		= 0x01
   */
  // Anything with a stream type over 99 is going to be AVI for us. 7/30/04
  public static final int STREAMOUTPUT_PROGRAM		= 0;
  public static final int STREAMOUTPUT_TRANSPORT		= 1;
  public static final int STREAMOUTPUT_MPEG1			= 2;
  public static final int STREAMOUTPUT_PES_AV		    = 3;
  public static final int STREAMOUTPUT_PES_Video		= 5;
  public static final int STREAMOUTPUT_PES_Audio		= 7;
  public static final int STREAMOUTPUT_DVD			= 10;
  public static final int STREAMOUTPUT_VCD			= 11;
  public static final int STREAMOUTPUT_SVCD			= 12;
  public static final int STREAMOUTPUT_CUSTOM_DIVX    = 101;
  public static final int STREAMOUTPUT_CUSTOM_MPEG4   = 102;
  /*
		PROP_STREAMOUTPUT_PROGRAM		= 0,
		PROP_STREAMOUTPUT_TRANSPORT		= 1,
		PROP_STREAMOUTPUT_MPEG1			= 2,
		PROP_STREAMOUTPUT_PES_AV		= 3,
		PROP_STREAMOUTPUT_PES_Video		= 5,
		PROP_STREAMOUTPUT_PES_Audio		= 7,
		PROP_STREAMOUTPUT_DVD			= 10,
		PROP_STREAMOUTPUT_VCD			= 11
		PROP_STREAMOUTPUT_SVCD			= 12
		CUSTOM - DIVX                   = 101
		CUSTOM - MPEG4                   = 102
   */
  private static final String PYTHON2_ENCODING = "python2_encoding";
  private static final java.util.Map defaultEncodingMap = new java.util.LinkedHashMap();
  private static String[] mpeg2DefaultQualityNames;
  private static String[] swEncodeQualityNames;
  private static String[] allDefaultQualityNames;
  private static String[] hdpvrEncodeQualityNames;
  static
  {
    // NOTE: THIS SHOULD BE REDONE SO THAT THEY'RE ALL STORED IN THE PROPERTIES FILE AND WE
    // DETECT WHICH ARE MPEG4/DIVX/MPEG2 WHEN WE LOAD THEM UP
    // Create all of the default quality settings
    // NOTE: We can't store them in the properties file because their names are language dependent
    // and we end up with qualities in all different languages if they're switching languages.
    if (!Sage.getBoolean("seeker/no_default_qualities", false))
    {
      for (int i = 0; i < DEFAULT_PYTHON_ENCODINGS.length; i++)
        defaultEncodingMap.put(DEFAULT_PYTHON_ENCODING_NAMES[i],
            new MPEG2EncodingParams(DEFAULT_PYTHON_ENCODINGS[i]));
      //				Sage.put(MMC.MMC_KEY + '/' + PYTHON2_ENCODING + '/' +
      //					DEFAULT_PYTHON_ENCODING_NAMES[i], DEFAULT_PYTHON_ENCODINGS[i]);
      for (int i = 0; i < DEFAULT_DIVX_ENCODINGS.length; i++)
        defaultEncodingMap.put(DEFAULT_DIVX_ENCODING_NAMES[i],
            new MPEG2EncodingParams(DEFAULT_DIVX_ENCODINGS[i]));
      //				Sage.put(MMC.MMC_KEY + '/' + PYTHON2_ENCODING + '/' +
      //					DEFAULT_DIVX_ENCODING_NAMES[i], DEFAULT_DIVX_ENCODINGS[i]);
      for (int i = 0; i < DEFAULT_MPEG4_ENCODINGS.length; i++)
        defaultEncodingMap.put(DEFAULT_MPEG4_ENCODING_NAMES[i],
            new MPEG2EncodingParams(DEFAULT_MPEG4_ENCODINGS[i]));
      //				Sage.put(MMC.MMC_KEY + '/' + PYTHON2_ENCODING + '/' +
      //					DEFAULT_MPEG4_ENCODING_NAMES[i], DEFAULT_MPEG4_ENCODINGS[i]);
      for (int i = 0; i < DEFAULT_SW_ENCODINGS.length; i++)
        defaultEncodingMap.put(DEFAULT_SW_ENCODING_NAMES[i],
            new MPEG2EncodingParams(DEFAULT_SW_ENCODINGS[i]));
      for (int i = 0; i < DEFAULT_HDPVR_ENCODINGS.length; i++)
        defaultEncodingMap.put(DEFAULT_HDPVR_ENCODING_NAMES[i],
            new MPEG2EncodingParams(DEFAULT_HDPVR_ENCODINGS[i]));
    }
    java.util.Set defaultNameSet = new java.util.HashSet(defaultEncodingMap.keySet());
    String[] pythonQuals = Sage.keys(MMC.MMC_KEY + '/' + PYTHON2_ENCODING + '/');

    for (int i = 0; i < pythonQuals.length; i++)
      defaultEncodingMap.put(pythonQuals[i],
          new MPEG2EncodingParams(Sage.get(MMC.MMC_KEY + '/' + PYTHON2_ENCODING + '/' + pythonQuals[i], "")));

    //		for (int i = 0; i < pythonQuals.length; i++) // case they're overwriting a divx or mpeg4
    //			defaultEncodingMap.put(pythonQuals[i],
    //				new MPEG2EncodingParams(Sage.get(MMC.MMC_KEY + '/' + PYTHON2_ENCODING + '/' + pythonQuals[i], "")));

    java.util.Set customQualNames = new java.util.HashSet(defaultEncodingMap.keySet());
    customQualNames.removeAll(defaultNameSet);

    java.util.Iterator walker = customQualNames.iterator();
    java.util.ArrayList customMpeg2Quals = new java.util.ArrayList();
    java.util.ArrayList customMpeg2AndDivxQuals = new java.util.ArrayList();
    java.util.ArrayList customHDPVRQuals = new java.util.ArrayList();
    while (walker.hasNext())
    {
      String qualName = (String) walker.next();
      MPEG2EncodingParams parms = (MPEG2EncodingParams) defaultEncodingMap.get(qualName);
      if (parms != null && parms.outputstreamtype != null)
      {
        if (parms.outputstreamtype.intValue() < 100)
        {
          customMpeg2Quals.add(qualName);
          customMpeg2AndDivxQuals.add(qualName);
          if (parms.outputstreamtype.intValue() == STREAMOUTPUT_TRANSPORT)
            customHDPVRQuals.add(qualName);
        }
        else if (parms.outputstreamtype.intValue() == STREAMOUTPUT_CUSTOM_DIVX)
          customMpeg2AndDivxQuals.add(qualName);
      }
    }
    if (!Sage.getBoolean("seeker/no_default_qualities", false))
      customMpeg2Quals.addAll(java.util.Arrays.asList(DEFAULT_PYTHON_ENCODING_NAMES));
    mpeg2DefaultQualityNames = (String[]) customMpeg2Quals.toArray(Pooler.EMPTY_STRING_ARRAY);
    java.util.Arrays.sort(mpeg2DefaultQualityNames);

    if (!Sage.getBoolean("seeker/no_default_qualities", false))
      customMpeg2AndDivxQuals.addAll(java.util.Arrays.asList(DEFAULT_SW_ENCODING_NAMES));
    swEncodeQualityNames = (String[]) customMpeg2AndDivxQuals.toArray(Pooler.EMPTY_STRING_ARRAY);
    java.util.Arrays.sort(swEncodeQualityNames);

    if (!Sage.getBoolean("seeker/no_default_qualities", false))
      customHDPVRQuals.addAll(java.util.Arrays.asList(DEFAULT_HDPVR_ENCODING_NAMES));
    hdpvrEncodeQualityNames = (String[]) customHDPVRQuals.toArray(Pooler.EMPTY_STRING_ARRAY);
    java.util.Arrays.sort(hdpvrEncodeQualityNames);

    allDefaultQualityNames = (String[]) defaultEncodingMap.keySet().toArray(Pooler.EMPTY_STRING_ARRAY);
    java.util.Arrays.sort(allDefaultQualityNames);
  }

  public static MPEG2EncodingParams getQuality(String name)
  {
    return (MPEG2EncodingParams) defaultEncodingMap.get(name);
  }

  public static java.util.Map getQualityOptions(String name)
  {
    MPEG2EncodingParams q = getQuality(name);
    return (q == null) ? null : q.getOptionsMap();
  }

  public static String[] getQualityNames()
  {
    return mpeg2DefaultQualityNames;
  }

  public static String[] getQualityNames(boolean allQualities)
  {
    return allDefaultQualityNames;
  }

  public static String[] getQualityNames(int streamType)
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    java.util.Iterator walker = defaultEncodingMap.entrySet().iterator();
    while (walker.hasNext())
    {
      java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
      if (((MPEG2EncodingParams) ent.getValue()).outputstreamtype.intValue() == streamType)
      {
        rv.add(ent.getKey());
      }
    }
    return (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public static String[] getSWEncodeQualityNames()
  {
    return swEncodeQualityNames;
  }

  public static String[] getHDPVREncodeQualityNames()
  {
    return hdpvrEncodeQualityNames;
  }

  public MPEG2EncodingParams(String paramString)
  {
    this();
    java.util.StringTokenizer toker = new java.util.StringTokenizer(paramString, "|");
    while (toker.hasMoreTokens())
    {
      String currToke = toker.nextToken();
      int idx = currToke.indexOf('=');
      try
      {
        java.lang.reflect.Field currField = getClass().getDeclaredField(currToke.substring(0, idx).toLowerCase());
        currField.set(this, new Integer(currToke.substring(idx + 1)));
      }
      catch (Throwable e)
      {
        System.err.println("Error in python encoding paramString \"" + paramString + "\" of:" + e);
      }
    }
    if (!MMC.getInstance().isNTSCVideoFormat())
    {
      if (height != null && height.intValue() == 480)
        height = new Integer(576);
      if (height != null && height.intValue() == 240)
        height = new Integer(288);
      if (fps != null && fps.intValue() == 30)
        fps = new Integer(25);
    }
  }
  public MPEG2EncodingParams()
  {
    audiooutputmode = new Integer(0);// = 0;
    audiocrc = new Integer(0);// = 0;
    gopsize = new Integer(15);// = 15;
    videobitrate = new Integer(4000000);// = 4000000;
    peakvideobitrate = null;//new Integer(5000000);
    inversetelecine = new Integer(0);// = 0;
    closedgop = new Integer(0);// = 0;
    vbr = new Integer(0);// = 0;
    outputstreamtype = new Integer(0);// = 0;
    width = new Integer(720);// = 720;
    height = new Integer(MMC.getInstance().isNTSCVideoFormat() ? 480 : 576);// = 480;
    audiobitrate = new Integer(384);// = 384;
    audiosampling = new Integer(48000);// = 48000;
    overallbitrate = new Integer(0);

    disablefilter = new Integer(1);
    medianfilter = new Integer(3);
    mediancoringlumahi = new Integer(0);
    mediancoringlumalo = new Integer(255);
    mediancoringchromahi = new Integer(0); // 0 to 255 (default 0)
    mediancoringchromalo = new Integer(255); // 0 to 255 (default 255)
    lumaspatialflt = new Integer(3);       // enum SPATIAL_FILTER_LUMA
    chromaspatialflt = new Integer(1);     // enum SPATIAL_FILTER_CHROMA
    dnrmode = new Integer(3);              // enum DNR_MODE
    dnrspatialfltlevel = new Integer(0);   // 0 to 15 (default 0) - used in static mode only
    dnrtemporalfltlevel = new Integer(0);  // 0 to 15 (default 0) - used in static mode only
    dnrsmoothfactor = new Integer(200);      // 0 to 255 (default 200)
    dnr_ntlf_max_y = new Integer(15);       // 0 to 15 (default 15) - max NTLF Luma
    dnr_ntlf_max_uv = new Integer(15);      // 0 to 15 (default 15) - max NTLF Chroma
    dnrtemporalmultfactor = new Integer(48);// 0 to 255 (default 48) - temporal filter multplier factor
    dnrtemporaladdfactor = new Integer(4); // 0 to 15 (default 4) - temporal filter add factor
    dnrspatialmultfactor = new Integer(21); // 0 to 255 (default 21) - spatial filter multiplier factor
    dnrspatialsubfactor = new Integer(2);  // 0 to 15 (default 2) - spatial filter sub factor
    lumanltflevel = new Integer(0);        // 0 to 15
    lumanltfcoeffindex = new Integer(0);   // 0 to 63
    lumanltfcoeffvalue = new Integer(0);   // 0 to 255
    vimzoneheight = new Integer(2);        // 0 to 15 (default 2)

    fps = new Integer(30);
    ipb = new Integer(0);
    deinterlace = new Integer(0);
    aspectratio = new Integer(1);
  }

  public java.util.Map getOptionsMap()
  {
    if (optionsMap == null)
    {
      optionsMap = new java.util.LinkedHashMap();
      optionsMap.put("audiooutputmode", audiooutputmode.toString());
      optionsMap.put("audiocrc", audiocrc.toString());
      optionsMap.put("gopsize", gopsize.toString());
      optionsMap.put("videobitrate", videobitrate.toString());
      optionsMap.put("peakvideobitrate", peakvideobitrate==null?null:peakvideobitrate.toString());
      optionsMap.put("inversetelecine", inversetelecine.toString());
      optionsMap.put("closedgop", closedgop.toString());
      optionsMap.put("vbr", vbr.toString());
      optionsMap.put("outputstreamtype", outputstreamtype.toString());
      optionsMap.put("width", width.toString());
      optionsMap.put("height", height.toString());
      optionsMap.put("audiobitrate", audiobitrate.toString());
      optionsMap.put("audiosampling", audiosampling.toString());
      optionsMap.put("disablefilter", disablefilter.toString());
      optionsMap.put("medianfilter", medianfilter.toString());
      optionsMap.put("fps", fps.toString());
      optionsMap.put("ipb", ipb.toString());
      optionsMap.put("deinterlace", deinterlace.toString());
      optionsMap.put("aspectratio", aspectratio.toString());
    }
    return (java.util.Map) optionsMap.clone();
  }

  // NOTE: This may not have the correct container for MPEG4 material since it might be AVI
  public sage.media.format.ContainerFormat getContainerFormatObj()
  {
    // Do NOT re-use these objects since it will mess up metadata setting in their properties maps!
    sage.media.format.ContainerFormat cf = new sage.media.format.ContainerFormat();
    sage.media.format.BitstreamFormat[] streams = new sage.media.format.BitstreamFormat[2]; // audio & video
    switch (outputstreamtype.intValue())
    {
      case STREAMOUTPUT_DVD:
      case STREAMOUTPUT_PROGRAM:
      case STREAMOUTPUT_SVCD:
      case STREAMOUTPUT_CUSTOM_DIVX:
      case STREAMOUTPUT_CUSTOM_MPEG4:
        cf.setFormatName(sage.media.format.MediaFormat.MPEG2_PS);
        break;
      case STREAMOUTPUT_TRANSPORT:
        cf.setFormatName(sage.media.format.MediaFormat.MPEG2_TS);
        break;
      case STREAMOUTPUT_VCD:
      case STREAMOUTPUT_MPEG1:
        cf.setFormatName(sage.media.format.MediaFormat.MPEG1);
        break;
      case STREAMOUTPUT_PES_AV:
      case STREAMOUTPUT_PES_Video:
        cf.setFormatName(sage.media.format.MediaFormat.MPEG2_PES_VIDEO);
        break;
      case STREAMOUTPUT_PES_Audio:
        cf.setFormatName(sage.media.format.MediaFormat.MPEG2_PES_AUDIO);
        break;
    }
    cf.setBitrate(overallbitrate.intValue());
    sage.media.format.VideoFormat vidFormat = new sage.media.format.VideoFormat();
    switch (outputstreamtype.intValue())
    {
      case STREAMOUTPUT_DVD:
      case STREAMOUTPUT_PROGRAM:
      case STREAMOUTPUT_SVCD:
      case STREAMOUTPUT_TRANSPORT:
      case STREAMOUTPUT_PES_AV:
      case STREAMOUTPUT_PES_Video:
      case STREAMOUTPUT_PES_Audio:
        vidFormat.setFormatName(sage.media.format.MediaFormat.MPEG2_VIDEO);
        break;
      case STREAMOUTPUT_CUSTOM_DIVX:
      case STREAMOUTPUT_CUSTOM_MPEG4:
        vidFormat.setFormatName(sage.media.format.MediaFormat.MPEG4X);
        break;
      case STREAMOUTPUT_VCD:
      case STREAMOUTPUT_MPEG1:
        vidFormat.setFormatName(sage.media.format.MediaFormat.MPEG1_VIDEO);
        break;
    }
    vidFormat.setWidth(width.intValue());
    vidFormat.setHeight(height.intValue());
    vidFormat.setBitrate(videobitrate.intValue());
    // NOTE: This may not be true if the capture hardware doesn't do interlaced MPEG2
    vidFormat.setInterlaced(deinterlace.intValue() == 0);
    if (fps.intValue() == 30)
    {
      vidFormat.setFps(29.97f);
      vidFormat.setFpsNum(30000);
      vidFormat.setFpsDen(1001);
    }
    else
    {
      vidFormat.setFps(fps.intValue());
      vidFormat.setFpsNum(fps.intValue());
      vidFormat.setFpsDen(1);
    }
    vidFormat.setAspectRatio(4.0f/3.0f);
    vidFormat.setArNum(4);
    vidFormat.setArDen(3);
    sage.media.format.AudioFormat audFormat = new sage.media.format.AudioFormat();
    audFormat.setFormatName(sage.media.format.MediaFormat.MP2);
    audFormat.setBitrate(audiobitrate.intValue() * 1000);
    audFormat.setSamplingRate(audiosampling.intValue());
    audFormat.setChannels(audiooutputmode.intValue() == 3 ? 1 : 2);
    streams[0] = vidFormat;
    streams[1] = audFormat;
    cf.setStreamFormats(streams);
    return cf;
  }
  private java.util.LinkedHashMap optionsMap;

  Integer audiooutputmode;// = 0;
  Integer audiocrc;// = 0;
  Integer gopsize;// = 15;
  Integer videobitrate;// = 4000000;
  Integer peakvideobitrate;
  Integer inversetelecine;// = 0;
  Integer closedgop;// = 0;
  Integer vbr;// = 0;
  Integer outputstreamtype;// = 0;
  Integer width;// = 720;
  Integer height;// = 480;
  Integer audiobitrate;// = 384;
  Integer audiosampling;// = 48000;

  Integer overallbitrate;

  // Prefiltering settings
  Integer disablefilter;        // if TRUE, filter settings will be disabled.

  Integer medianfilter;         // 0:Disable, 1:Horizontal, 2:Vertical, 3:Horz&Vert(default), 4:Diagonal

  Integer mediancoringlumahi;   // 0 to 255 (default 0)
  Integer mediancoringlumalo;   // 0 to 255 (default 255)
  Integer mediancoringchromahi; // 0 to 255 (default 0)
  Integer mediancoringchromalo; // 0 to 255 (default 255)
  Integer lumaspatialflt;       // 0:Disable, 1:1D Horiz, 2:1D Vert, 3:2D Horz & Vert Seperatable(default), 4:2D Non Seperable
  Integer chromaspatialflt;     // 0:Disable, 1:1D Horz(default)
  Integer dnrmode;              // 0:StaticBoth, 1:StaticTime/DynamicSpace, 2:DynamicTime/StaticSpace, 3:DynamicBoth(default)
  Integer dnrspatialfltlevel;   // 0 to 15 (default 0) - used in static mode only
  Integer dnrtemporalfltlevel;  // 0 to 15 (default 0) - used in static mode only
  Integer dnrsmoothfactor;      // 0 to 255 (default 200)
  Integer dnr_ntlf_max_y;       // 0 to 15 (default 15) - max NTLF Luma
  Integer dnr_ntlf_max_uv;      // 0 to 15 (default 15) - max NTLF Chroma
  Integer dnrtemporalmultfactor;// 0 to 255 (default 48) - temporal filter multplier factor
  Integer dnrtemporaladdfactor; // 0 to 15 (default 4) - temporal filter add factor
  Integer dnrspatialmultfactor; // 0 to 255 (default 21) - spatial filter multiplier factor
  Integer dnrspatialsubfactor;  // 0 to 15 (default 2) - spatial filter sub factor
  Integer lumanltflevel;        // 0 to 15
  Integer lumanltfcoeffindex;   // 0 to 63
  Integer lumanltfcoeffvalue;   // 0 to 255
  Integer vimzoneheight;        // 0 to 15 (default 2)

  Integer fps; // frame rate, 30, 25 or 15 are valid values
  Integer ipb; // 0 or 3 is ipb, 1 is i, 2 is ip
  Integer deinterlace; // 0 is none, 1 is deinterlaced
  Integer aspectratio; // 0 is 1:1, 1 is 4:3 and 2 is 16:9
}
