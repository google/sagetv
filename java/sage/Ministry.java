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

// Transcoding Manager
public class Ministry implements Runnable
{
  private static final String NEXT_JOB_ID = "transcoder/next_job_id";
  protected static final String TRANSCODE_JOB_PROPS = "transcoder/jobs";

  private static final String[] DEAD_FORMAT_NAMES = {
    "Razr Compatible-Fair Quality",
    "Razr Compatible-Good Quality",
    "Razr Compatible-High Quality",
    "MPEG4 HDTV Deinterlaced in AVI-High Quality",
    "MPEG4 HDTV in AVI-High Quality",
    "MPEG4 HDTV Deinterlaced in AVI-Good Quality",
    "MPEG4 HDTV in AVI-Good Quality",
    "MPEG4 Deinterlaced in AVI-High Quality",
    "MPEG4 in AVI-High Quality",
    "MPEG4 Deinterlaced in AVI-Good Quality",
    "MPEG4 in AVI-Good Quality",
    "PSP Compatible-Good Quality",
    "PSP Compatible-High Quality",
    "PSP Compatible Widescreen-Good Quality",
    "PSP Compatible Widescreen-High Quality",
    "iPod Compatible-Fair Quality",
    "iPod Compatible-Good Quality",
    "iPod Compatible-High Quality",
    "DVD Compatible-Standard Play",
    "DVD Compatible-Standard Play with AC3",
    "DVD Compatible-Standard Play w/ AC3",
    "DVD Compatible-Long Play",
    "DVD Compatible-Long Play with AC3",
    "DVD Compatible-Long Play w/ AC3",
    "DVD Compatible-Extra Long Play",
    "DVD Compatible-Extra Long Play with AC3",
    "DVD Compatible-Extra Long Play w/ AC3",
    "Razr-Fair Quality",
    "Razr-Good Quality",
    "Razr-High Quality",
    "MPEG4 HDTV-High Quality Deinterlaced AVI",
    "MPEG4 HDTV-Good Quality Deinterlaced AVI",
    "MPEG4-High Quality Deinterlaced AVI",
    "MPEG4-Good Quality Deinterlaced AVI",
  };

  private static final String[][] SUBSTITUTE_FORMAT_NAMES = {
    { "MPEG4 HDTV-High Quality Deinterlaced AVI", "MPEG4 HDTV-High Quality AVI" },
    { "MPEG4 HDTV-Good Quality Deinterlaced AVI", "MPEG4 HDTV-Good Quality AVI" },
    { "MPEG4-High Quality Deinterlaced AVI", "MPEG4-High Quality AVI" },
    { "MPEG4-Good Quality Deinterlaced AVI", "MPEG4-Good Quality AVI" },
  };

  // We got rid of the deinterlace options here since that's done automatically in FFMPEGTranscoder.java now (it'll add the -deinterlace flag if the input is
  // interlaced and it's not scaling it down vertically by more than half)
  public static final String[][] PREDEFINED_TRANSCODER_FORMATS = {
    /*		{ "Razr-Fair Quality", "f=3gp;[bf=vid;f=mpeg4;br=80000;fps=29.97;w=176;h=144;][bf=aud;f=amr_nb;sr=8000;ch=1;bsmp=8;br=7950;]" },
		{ "Razr-Good Quality", "f=3gp;[bf=vid;f=mpeg4;br=120000;fps=29.97;w=176;h=144;][bf=aud;f=amr_nb;sr=8000;ch=1;bsmp=8;br=7950;]" },
		{ "Razr-High Quality", "f=3gp;[bf=vid;f=mpeg4;br=172000;fps=29.97;w=176;h=144;][bf=aud;f=amr_nb;sr=8000;ch=1;bsmp=8;br=7950;]" },
     *///		{ "MPEG4 HDTV-High Quality Deinterlaced AVI", "f=avi;MCompressionDetails=-vtag xvid -deinterlace;[bf=vid;f=mpeg4;br=8000000;][bf=aud;]" },
    { "MPEG4 HDTV-High Quality AVI", "f=avi;MCompressionDetails=-vtag xvid;[bf=vid;f=mpeg4;br=8000000;][bf=aud;]" },
    //	{ "MPEG4 HDTV-Good Quality Deinterlaced AVI", "f=avi;MCompressionDetails=-vtag xvid -deinterlace;[bf=vid;f=mpeg4;br=6000000;][bf=aud;]" },
    { "MPEG4 HDTV-Good Quality AVI", "f=avi;MCompressionDetails=-vtag xvid;[bf=vid;f=mpeg4;br=6000000;][bf=aud;]" },
    { "MPEG4 HDTV-High Quality H.264 MKV", "f=matroska;MCompressionDetails=-coder 1 -flags +loop -cmp +chroma -partitions +parti8x8+parti4x4+partp8x8+partb8x8 -me_method hex -subq 2 -me_range 16 -g 250 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -b_strategy 1 -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -bf 3 -refs 1 -directpred 1 -trellis 0 -flags2 +bpyramid-mixed_refs+wpred+dct8x8+fastpskip -wpredp 0 -rc_lookahead 10;[bf=vid;f=h264;br=7500000;][bf=aud;]" },
    { "MPEG4 HDTV-Good Quality H.264 MKV", "f=matroska;MCompressionDetails=-coder 1 -flags +loop -cmp +chroma -partitions +parti8x8+parti4x4-partp8x8-partb8x8 -me_method dia -subq 1 -me_range 16 -g 250 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -b_strategy 1 -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -bf 3 -refs 1 -directpred 1 -trellis 0 -flags2 +bpyramid-mixed_refs+wpred+dct8x8+fastpskip-mbtree -wpredp 0 -rc_lookahead 0;[bf=vid;f=h264;br=4500000;][bf=aud;]" },
    { "PSP-Good Quality", "f=psp;MCompressionDetails=-bitexact 1[bf=vid;f=xvid;br=216000;fps=29.97;w=320;h=240;][bf=aud;f=aac;sr=24000;ch=1;br=48000;]" },
    { "PSP-High Quality", "f=psp;MCompressionDetails=-bitexact 1[bf=vid;f=xvid;br=768000;fps=29.97;w=320;h=240;][bf=aud;f=aac;sr=24000;ch=2;br=128000;]" },
    { "PSP-Widescreen Good Quality", "f=psp;MCompressionDetails=-bitexact 1[bf=vid;f=xvid;br=216000;fps=29.97;w=368;h=208;][bf=aud;f=aac;sr=24000;ch=1;br=48000;]" },
    { "PSP-Widescreen High Quality", "f=psp;MCompressionDetails=-bitexact 1[bf=vid;f=xvid;br=768000;fps=29.97;w=368;h=208;][bf=aud;f=aac;sr=24000;ch=2;br=128000;]" },
    /* TODO: ffmpeg now has ipod format handler */
    { "iPod-Fair Quality", "f=mp4;MCompressionDetails=-bufsize 33554432 -g 300;[bf=vid;f=mpeg4;br=500000;fps=29.97;w=512;h=384;arn=4;ard=3;][bf=aud;f=aac;sr=44100;ch=2;br=64000;]" },
    { "iPod-Good Quality", "f=mp4;MCompressionDetails=-maxrate 1250000 -bufsize 33554432 -g 300;[bf=vid;f=mpeg4;br=1000000;fps=29.97;w=512;h=384;arn=4;ard=3;][bf=aud;f=aac;sr=44100;ch=2;br=96000;]" },
    { "iPod-High Quality", "f=mp4;MCompressionDetails=-maxrate 2500000 -qmin 3 -qmax 5 -bufsize 33554432 -g 300;[bf=vid;f=mpeg4;br=1800000;fps=29.97;w=512;h=384;arn=4;ard=3;][bf=aud;f=aac;sr=44100;ch=2;br=128000;]" },
    /* TODO: Someone recommended better settings for iPhone (investigate)
	-r 29.97 -vcodec libx264 -s 480x272 -flags +loop -cmp +chroma -deblockalpha 0 -deblockbeta 0 -crf 24 -bt 256k -refs 1 -coder 0 -me umh -me_range 16 -subq 5 -partitions +parti4x4+parti8x8+partp8x8 -g 250 -keyint_min 25 -level 30 -qmin 10 -qmax 51 -trellis 2 -sc_threshold 40 -i_qfactor 0.71 -acodec libfaac -ab 128k -ar 48000 -ac 2
     */
    { "iPhone-Standard", "f=mp4;MCompressionDetails=-coder 0 -flags +loop -cmp +chroma -partitions +parti8x8+parti4x4+partp8x8+partb8x8 -me_method umh -subq 8 -me_range 16 -g 250 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -b_strategy 2 -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -bf 0 -refs 5 -directpred 3 -trellis 1 -flags2 -wpred-dct8x8 -wpredp 0 -rc_lookahead 50 -level 13 -maxrate 768000 -bufsize 3000000 -async 50;[bf=vid;f=h264;br=640000;fps=29.97;w=480;h=368;arn=4;ard=3;][bf=aud;f=aac;sr=48000;ch=2;br=128000;]" },
    { "iPhone-Widescreen", "f=mp4;MCompressionDetails=-coder 0 -flags +loop -cmp +chroma -partitions +parti8x8+parti4x4+partp8x8+partb8x8 -me_method umh -subq 8 -me_range 16 -g 250 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -b_strategy 2 -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -bf 0 -refs 5 -directpred 3 -trellis 1 -flags2 -wpred-dct8x8 -wpredp 0 -rc_lookahead 50 -level 13 -maxrate 768000 -bufsize 3000000 -async 50;[bf=vid;f=h264;br=640000;fps=29.97;w=480;h=272;arn=16;ard=9;][bf=aud;f=aac;sr=48000;ch=2;br=128000;]" },
    { "AppleTV-High Quality", "f=mp4;MCompressionDetails=-coder 0 -flags +loop -cmp +chroma -partitions +parti8x8+parti4x4+partp8x8+partb8x8 -me_method umh -subq 8 -me_range 16 -g 250 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -b_strategy 2 -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -bf 0 -refs 1 -directpred 3 -trellis 1 -flags2 -wpred-dct8x8 -wpredp 0 -rc_lookahead 50 -level 30 -maxrate 10000000 -bufsize 10000000;[bf=vid;f=h264;br=2500000;fps=29.97;w=720;h=480;arn=4;ard=3;][bf=aud;f=aac;sr=48000;ch=2;br=128000;]" },
    { "AppleTV-High Quality Widescreen", "f=mp4;MCompressionDetails=-coder 0 -flags +loop -cmp +chroma -partitions +parti8x8+parti4x4+partp8x8+partb8x8 -me_method umh -subq 8 -me_range 16 -g 250 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -b_strategy 2 -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -bf 0 -refs 1 -directpred 3 -trellis 1 -flags2 -wpred-dct8x8 -wpredp 0 -rc_lookahead 50 -level 30 -maxrate 10000000 -bufsize 10000000;[bf=vid;f=h264;br=2500000;fps=29.97;w=960;h=540;arn=16;ard=9;][bf=aud;f=aac;sr=48000;ch=2;br=128000;]" },
  };
  public static final String[][] PREDEFINED_TRANSCODER_FORMATS_NTSC = {
    //		{ "MPEG4-High Quality Deinterlaced AVI", "f=avi;MCompressionDetails=-vtag xvid -deinterlace;[bf=vid;f=mpeg4;br=2000000;w=720;h=480;fps=29.97;][bf=aud;]" },
    { "MPEG4-High Quality AVI", "f=avi;MCompressionDetails=-vtag xvid;[bf=vid;f=mpeg4;br=2000000;w=720;h=480;fps=29.97;][bf=aud;]" },
    //		{ "MPEG4-Good Quality Deinterlaced AVI", "f=avi;MCompressionDetails=-vtag xvid -deinterlace;[bf=vid;f=mpeg4;br=1500000;w=720;h=480;fps=29.97;][bf=aud;]" },
    { "MPEG4-Good Quality AVI", "f=avi;MCompressionDetails=-vtag xvid;[bf=vid;f=mpeg4;br=1500000;w=720;h=480;fps=29.97;][bf=aud;]" },
    { "MPEG4-High Quality H.264 MKV", "f=matroska;MCompressionDetails=-coder 1 -flags +loop -cmp +chroma -partitions +parti8x8+parti4x4+partp8x8+partb8x8 -me_method hex -subq 6 -me_range 16 -g 250 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -b_strategy 1 -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -bf 3 -refs 2 -directpred 1 -trellis 1 -flags2 +bpyramid+mixed_refs+wpred+dct8x8+fastpskip -wpredp 2 -rc_lookahead 30;[bf=vid;f=h264;br=2000000;w=720;h=480;fps=29.97;][bf=aud;]" },
    { "MPEG4-Good Quality H.264 MKV", "f=matroska;MCompressionDetails=-coder 1 -flags +loop -cmp +chroma -partitions +parti8x8+parti4x4-partp8x8-partb8x8 -me_method dia -subq 1 -me_range 16 -g 250 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -b_strategy 1 -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -bf 3 -refs 1 -directpred 1 -trellis 0 -flags2 +bpyramid-mixed_refs+wpred+dct8x8+fastpskip-mbtree -wpredp 0 -rc_lookahead 0;[bf=vid;f=h264;br=1500000;w=720;h=480;fps=29.97;][bf=aud;]" },
    { "DVD-Standard Play", "f=dvd;[bf=vid;f=mpeg2video;br=6400000;fps=29.97;w=720;h=480;vbr=1;][bf=aud;f=mp2;sr=48000;ch=2;bsmp=16;br=384000;]" },
    { "DVD-Standard Play with AC3", "f=dvd;[bf=vid;f=mpeg2video;br=6400000;fps=29.97;w=720;h=480;vbr=1;][bf=aud;f=ac3;sr=48000;bsmp=16;br=384000;]" },
    { "DVD-Long Play", "f=dvd;[bf=vid;f=mpeg2video;br=4800000;fps=29.97;w=720;h=480;vbr=1;][bf=aud;f=mp2;sr=48000;ch=2;bsmp=16;br=384000;]" },
    { "DVD-Long Play with AC3", "f=dvd;[bf=vid;f=mpeg2video;br=4800000;fps=29.97;w=720;h=480;vbr=1;][bf=aud;f=ac3;sr=48000;bsmp=16;br=384000;]" },
    { "DVD-Extra Long Play", "f=dvd;[bf=vid;f=mpeg2video;br=3000000;fps=29.97;w=720;h=480;vbr=1;][bf=aud;f=mp2;sr=48000;ch=2;bsmp=16;br=384000;]" },
    { "DVD-Extra Long Play with AC3", "f=dvd;[bf=vid;f=mpeg2video;br=3000000;fps=29.97;w=720;h=480;vbr=1;][bf=aud;f=ac3;sr=48000;bsmp=16;br=384000;]" },
  };
  public static final String[][] PREDEFINED_TRANSCODER_FORMATS_PAL = {
    //		{ "MPEG4-High Quality Deinterlaced AVI", "f=avi;MCompressionDetails=-vtag xvid -deinterlace;[bf=vid;f=mpeg4;br=2000000;w=720;h=576;fps=25;][bf=aud;]" },
    { "MPEG4-High Quality AVI", "f=avi;MCompressionDetails=-vtag xvid;[bf=vid;f=mpeg4;br=2000000;w=720;h=576;fps=25;][bf=aud;]" },
    //		{ "MPEG4-Good Quality Deinterlaced AVI", "f=avi;MCompressionDetails=-vtag xvid -deinterlace;[bf=vid;f=mpeg4;br=1500000;w=720;h=576;fps=25;][bf=aud;]" },
    { "MPEG4-Good Quality AVI", "f=avi;MCompressionDetails=-vtag xvid;[bf=vid;f=mpeg4;br=1500000;w=720;h=576;fps=25;][bf=aud;]" },
    { "MPEG4-High Quality H.264 MKV", "f=matroska;MCompressionDetails=-coder 1 -flags +loop -cmp +chroma -partitions +parti8x8+parti4x4+partp8x8+partb8x8 -me_method hex -subq 6 -me_range 16 -g 250 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -b_strategy 1 -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -bf 3 -refs 2 -directpred 1 -trellis 1 -flags2 +bpyramid+mixed_refs+wpred+dct8x8+fastpskip -wpredp 2 -rc_lookahead 30;[bf=vid;f=h264;br=2000000;w=720;h=576;fps=25;][bf=aud;]" },
    { "MPEG4-Good Quality H.264 MKV", "f=matroska;MCompressionDetails=-coder 1 -flags +loop -cmp +chroma -partitions +parti8x8+parti4x4-partp8x8-partb8x8 -me_method dia -subq 1 -me_range 16 -g 250 -keyint_min 25 -sc_threshold 40 -i_qfactor 0.71 -b_strategy 1 -qcomp 0.6 -qmin 10 -qmax 51 -qdiff 4 -bf 3 -refs 1 -directpred 1 -trellis 0 -flags2 +bpyramid-mixed_refs+wpred+dct8x8+fastpskip-mbtree -wpredp 0 -rc_lookahead 0;[bf=vid;f=h264;br=1500000;w=720;h=576;fps=25;][bf=aud;]" },
    { "DVD-Standard Play", "f=dvd;[bf=vid;f=mpeg2video;br=6400000;fps=25;w=720;h=576;vbr=1;][bf=aud;f=mp2;sr=48000;ch=2;bsmp=16;br=384000;]" },
    { "DVD-Standard Play with AC3", "f=dvd;[bf=vid;f=mpeg2video;br=6400000;fps=25;w=720;h=576;vbr=1;][bf=aud;f=ac3;sr=48000;bsmp=16;br=384000;]" },
    { "DVD-Long Play", "f=dvd;[bf=vid;f=mpeg2video;br=4800000;fps=25;w=720;h=576;vbr=1;][bf=aud;f=mp2;sr=48000;ch=2;bsmp=16;br=384000;]" },
    { "DVD-Long Play with AC3", "f=dvd;[bf=vid;f=mpeg2video;br=4800000;fps=25;w=720;h=576;vbr=1;][bf=aud;f=ac3;sr=48000;bsmp=16;br=384000;]" },
    { "DVD-Extra Long Play", "f=dvd;[bf=vid;f=mpeg2video;br=3000000;fps=25;w=720;h=576;vbr=1;][bf=aud;f=mp2;sr=48000;ch=2;bsmp=16;br=384000;]" },
    { "DVD-Extra Long Play with AC3", "f=dvd;[bf=vid;f=mpeg2video;br=3000000;fps=25;w=720;h=576;vbr=1;][bf=aud;f=ac3;sr=48000;bsmp=16;br=384000;]" },
  };

  private static class MinistryHolder
  {
    public static final Ministry instance = new Ministry();
  }

  public static Ministry getInstance()
  {
    return MinistryHolder.instance;
  }

  /** Creates a new instance of Ministry */
  public Ministry()
  {
    jobCounter = Sage.getInt(NEXT_JOB_ID, 1);

    for (int i = 0; i < DEAD_FORMAT_NAMES.length; i++)
      Sage.remove("transcoder/formats/" + DEAD_FORMAT_NAMES[i]);
    for (int i = 0; i < PREDEFINED_TRANSCODER_FORMATS.length; i++)
      Sage.put("transcoder/formats/" + PREDEFINED_TRANSCODER_FORMATS[i][0], PREDEFINED_TRANSCODER_FORMATS[i][1]);
    if (MMC.getInstance().isNTSCVideoFormat())
    {
      for (int i = 0; i < PREDEFINED_TRANSCODER_FORMATS_NTSC.length; i++)
        Sage.put("transcoder/formats/" + PREDEFINED_TRANSCODER_FORMATS_NTSC[i][0], PREDEFINED_TRANSCODER_FORMATS_NTSC[i][1]);
    }
    else
    {
      for (int i = 0; i < PREDEFINED_TRANSCODER_FORMATS_PAL.length; i++)
        Sage.put("transcoder/formats/" + PREDEFINED_TRANSCODER_FORMATS_PAL[i][0], PREDEFINED_TRANSCODER_FORMATS_PAL[i][1]);
    }
  }

  public void notifyOfID(int x)
  {
    synchronized (idLock)
    {
      if (x >= jobCounter)
      {
        jobCounter = x + 1;
        Sage.putInt(NEXT_JOB_ID, jobCounter);
      }
    }
  }

  public int getNextJobID()
  {
    synchronized (idLock)
    {
      Sage.putInt(NEXT_JOB_ID, jobCounter + 1);
      jobCounter++;
      return jobCounter - 1;
    }
  }

  public void spawn()
  {
    alive = true;
    ministryThread = new Thread(this, "Ministry");
    ministryThread.setPriority(Thread.MIN_PRIORITY);
    ministryThread.setDaemon(true);
    ministryThread.start();
  }

  void goodbye()
  {
    alive = false;
    synchronized (lock)
    {
      lock.notifyAll();
    }
    if (ministryThread != null)
    {
      try
      {
        ministryThread.join(10000);
      }
      catch (InterruptedException e){}
    }
  }

  public void run()
  {
    // Just wait a sec at first so we don't slow down anything initializing since we're lowest priority
    try{Thread.sleep(5000);}catch(Exception e){}

    if (Sage.DBG) System.out.println("Ministry is starting");
    // Look for any MediaFiles which we should mark as transcoded immediately
    MediaFile[] mfs = Wizard.getInstance().getFiles();
    for (int i = 0; i < mfs.length; i++)
    {
      MediaFile mf = mfs[i];
      if (doesFileAlwaysRequireTranscoding(mf))
      {
        if (Sage.DBG) System.out.println("Added for transcoding:" + mf);
        sage.media.format.ContainerFormat cf = new sage.media.format.ContainerFormat();
        cf.setFormatName(sage.media.format.MediaFormat.AVI);
        waitingForConversion.add(new DShowTranscodeJob(mf, "AVI", cf, true, null));
      }
    }

    String[] jobKeys = Sage.childrenNames(TRANSCODE_JOB_PROPS);
    for (int i = 0; i < jobKeys.length; i++)
    {
      int currJobID;
      try
      {
        currJobID = Integer.parseInt(jobKeys[i]);
      }
      catch (NumberFormatException e)
      {
        System.out.println("ERROR in transcode job id format:" + e);
        continue;
      }
      String processor = Sage.get(TRANSCODE_JOB_PROPS + '/' + currJobID + '/' + TranscodeJob.TRANSCODE_PROCESSOR, null);
      TranscodeJob tj;
      try
      {
        if ("sagetv".equals(processor))
        {
          tj = new FFMPEGTranscodeJob(currJobID);
        }
        else
        {
          System.out.println("Unknown Transcode processor:" + processor);
          continue;
        }
        if (tj.getJobState() == TranscodeJob.COMPLETED || tj.getJobState() < 0)
          waitingForAbsolution.add(tj);
        else
          waitingForConversion.add(tj);
      }
      catch (IllegalArgumentException iae)
      {
        System.out.println("BAD transcode job data:" + iae);
        Sage.removeNode(TRANSCODE_JOB_PROPS + '/' + currJobID);
      }
    }

    while (alive)
    {
      long waitTime = Sage.getLong("mstry_engine_update_frequency", 3*Sage.MILLIS_PER_MIN);
      try
      {
        // Check if anything that's waiting to be converted is now ready for the conversion queue
        if (!waitingForConversion.isEmpty())
        {
          for (int i = 0; i < waitingForConversion.size(); i++)
          {
            TranscodeJob tj = (TranscodeJob) waitingForConversion.get(i);
            if (tj.isReadyForConversion())
            {
              waitingForConversion.removeElementAt(i);
              startConversion(tj);
            }
            else if (tj.hasLostHope())
            {
              waitingForConversion.removeElementAt(i);
            }
            else
            {
              waitTime = Math.min(waitTime, tj.getWaitTime());
            }
          }
        }

        synchronized (converting)
        {
          if (converting.size() > 0)
          {
            TranscodeJob mainConvert = (TranscodeJob) converting.get(0);
            switch (mainConvert.getJobState())
            {
              case TranscodeJob.WAITING:
                mainConvert.startTranscode();
                dirty = true;
                break;
              case TranscodeJob.TRANSCODING:
                break;
              case TranscodeJob.TRANSCODE_FAILED:
                converting.remove(0);
                waitingForAbsolution.add(mainConvert);
                mainConvert.cleanupCurrentTranscode();
                mainConvert.abandon();
                dirty = true;
                break;
              case TranscodeJob.DESTROYED:
                converting.remove(0);
                mainConvert.cleanupCurrentTranscode();
                mainConvert.abandon();
                dirty = true;
                break;
              case TranscodeJob.TRANSCODING_SEGMENT_COMPLETE:
                mainConvert.cleanupCurrentTranscode();
                if (mainConvert.getClipDuration() == 0 || mainConvert.transcodeSegment < mainConvert.getEndingSegment())
                  mainConvert.getTempFile(mainConvert.transcodeSegment).setLastModified(mainConvert.getMediaFile().
                      getEnd(mainConvert.transcodeSegment));
                else
                  mainConvert.getTempFile(mainConvert.transcodeSegment).setLastModified(mainConvert.getMediaFile().
                      getRecordTime() + mainConvert.getClipStartTime() + mainConvert.getClipDuration());
                if (mainConvert.transcodeSegment  < mainConvert.getEndingSegment())
                {
                  mainConvert.continueTranscode();
                }
                else
                {
                  mainConvert.setJobState(TranscodeJob.LIMBO);
                  converting.remove(0);
                  waitingForAbsolution.add(mainConvert);
                }
                dirty = true;
                break;
            }
          }
        }

        Hunter seek = SeekerSelector.getInstance();
        for (int i = 0; i < waitingForAbsolution.size(); i++)
        {
          TranscodeJob tj = (TranscodeJob) waitingForAbsolution.get(i);
          if (tj.getJobState() == TranscodeJob.COMPLETED || tj.getJobState() < 0)
            continue;
          if (Sage.DBG) System.out.println("Ministry is absolving " + tj.getMediaFile());
          if (tj.shouldReplaceOriginal() && seek.isMediaFileBeingViewed(tj.getMediaFile()))
          {
            if (Sage.DBG) System.out.println("Waiting to perform transcode DB update until file use has completed.");
            continue;
          }
          java.io.File[] newFiles = tj.getTargetFiles();
          java.io.File[] currFiles = tj.getTempFiles();
          java.util.ArrayList actualFilesVec = new java.util.ArrayList();
          for (int j = tj.getStartingSegment(); j <= tj.getEndingSegment(); j++)
          {
            actualFilesVec.add(newFiles[j]);
            seek.addIgnoreFile(newFiles[j]);
            if (!currFiles[j].equals(newFiles[j]) && (tj.shouldReplaceOriginal() || !newFiles[j].equals(tj.getMediaFile().getFile(j))))
              newFiles[j].delete(); // delete the target file so we can rename appropriately
            if (!currFiles[j].renameTo(newFiles[j]))
            {
              if (Sage.DBG) System.out.println("Renaming of transcoded file " + currFiles[j] + " failed to " + newFiles[j]);
            }
          }
          boolean ok;
          if (tj.shouldReplaceOriginal())
          {
            if (tj.getClipStartTime() != 0 || tj.getClipDuration() != 0)
            {
              long theStart = tj.getClipStartTime() + tj.getMediaFile().getRecordTime();
              long clipDur = tj.getClipDuration();
              long theEnd;
              if (clipDur != 0)
                theEnd = theStart + clipDur;
              else
                theEnd = tj.getMediaFile().getRecordEnd();
              ok = tj.getMediaFile().setFiles((java.io.File[]) actualFilesVec.toArray(new java.io.File[0]), theStart, theEnd);
              tj.getMediaFile().thisIsComplete();
            }
            else
              ok = tj.getMediaFile().setFiles((java.io.File[]) actualFilesVec.toArray(new java.io.File[0]));
            if (!ok)
            {
              for (int j = tj.getStartingSegment(); j <= tj.getEndingSegment(); j++)
              {
                if (!newFiles[j].renameTo(currFiles[j]))
                {
                  if (Sage.DBG) System.out.println("Re-renaming of transcoded file " + newFiles[j] + " failed to " + currFiles[j]);
                }
              }
            }
            else
              SeekerSelector.getInstance().processFileExport(tj.getMediaFile().getFiles(), MediaFile.ACQUISITION_MANUAL);
          }
          else
          {
            if (Sage.getBoolean("transcoder/dont_add_converted_duplicate_files_to_db", false))
            {
              ok = true;
            }
            else
            {
              MediaFile addedFile = Wizard.getInstance().addMediaFile(newFiles[tj.getStartingSegment()], "", MediaFile.ACQUISITION_MANUAL);
              if (addedFile != null)
              {
                if (addedFile.isArchiveFile() != tj.getMediaFile().isArchiveFile())
                {
                  if (tj.getMediaFile().isArchiveFile())
                    addedFile.simpleArchive();
                  else
                    addedFile.simpleUnarchive();
                }
                // Copy any auxillary metadata
                sage.media.format.ContainerFormat cf = tj.getMediaFile().getFileFormat();
                if (cf != null && cf.hasMetadata())
                {
                  // We need to do it one by one so that the external .properties file gets updated (instead of addMetadata that does it all at once)
                  java.util.Properties metaProps = cf.getMetadata();
                  java.util.Iterator walker = metaProps.entrySet().iterator();
                  while (walker.hasNext())
                  {
                    java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
                    if (ent.getKey() != null && ent.getValue() != null)
                      addedFile.addMetadata(ent.getKey().toString(), ent.getValue().toString());
                  }
                }
                if (Sage.DBG) System.out.println("New Library File " + addedFile);
                if (tj.getClipStartTime() == 0 && tj.getClipDuration() == 0)
                {
                  // Converted the whole thing, so use the complete airing.
                  addedFile.setInfoAiring(Wizard.getInstance().addAiring(tj.getMediaFile().getShow(), 0,
                      tj.getMediaFile().getContentAiring().getStartTime(),
                      tj.getMediaFile().getContentAiring().getDuration(),
                      tj.getMediaFile().getContentAiring().partsB, tj.getMediaFile().getContentAiring().miscB,
                      tj.getMediaFile().getContentAiring().prB, tj.getMediaFile().getMediaMask()));
                }
                else
                {
                  long airStart = tj.getMediaFile().getRecordTime() + tj.getClipStartTime();
                  addedFile.setInfoAiring(Wizard.getInstance().addAiring(tj.getMediaFile().getShow(), 0,
                      airStart, (tj.getClipDuration() == 0) ?
                          (tj.getMediaFile().getRecordEnd() - airStart) : tj.getClipDuration(),
                          tj.getMediaFile().getContentAiring().partsB, tj.getMediaFile().getContentAiring().miscB,
                          tj.getMediaFile().getContentAiring().prB, tj.getMediaFile().getMediaMask()));
                }
                for (int x = 1; x < newFiles.length; x++)
                {
                  addedFile.addSegmentFileDirect((java.io.File) actualFilesVec.get(x));
                }
                ok = true;
                SeekerSelector.getInstance().processFileExport(addedFile.getFiles(), MediaFile.ACQUISITION_MANUAL);
              }
              else
                ok = false;
            }
          }
          for (int j = 0; j < newFiles.length; j++)
          {
            seek.removeIgnoreFile(newFiles[j]);
          }
          // It might not be ready yet for some reason if there's a lock on a file
          if (ok)
          {
            // It's all done
            //waitingForAbsolution.remove(i--);
            tj.setJobState(TranscodeJob.COMPLETED);
            if (tj instanceof FFMPEGTranscodeJob)
              tj.saveToProps();
          }
          if (tj.hasLostHope())
            tj.abandon();
        }
      }
      catch (Throwable t)
      {
        System.out.println("ERROR Occured in core of Ministry:" + t);
        t.printStackTrace();
      }
      synchronized (lock)
      {
        if (!dirty)
        {
          try
          {
            if (Sage.DBG) System.out.println("Ministry is waiting for " + waitTime/1000 + " sec");
            lock.wait(waitTime);
          }catch(Exception e){}
        }
      }
      dirty = false;
    }

    // Abandon all current transcode jobs since we're shutting down
    if (Sage.DBG) System.out.println("Ministry is shutting down....destroying the converts in progress");
    while (waitingForAbsolution.size() > 0)
    {
      TranscodeJob tj = (TranscodeJob) waitingForAbsolution.remove(0);
      tj.cleanupCurrentTranscode();
      tj.abandon();
    }
    while (converting.size() > 0)
    {
      TranscodeJob tj = (TranscodeJob) converting.remove(0);
      tj.cleanupCurrentTranscode();
      tj.abandon();
    }
  }

  public static sage.media.format.ContainerFormat getPredefinedTargetFormat(String formatName)
  {
    for (int i = 0; i < SUBSTITUTE_FORMAT_NAMES.length; i++)
    {
      if (SUBSTITUTE_FORMAT_NAMES[i][0].equalsIgnoreCase(formatName))
      {
        formatName = SUBSTITUTE_FORMAT_NAMES[i][1];
        break;
      }
    }
    return sage.media.format.ContainerFormat.buildFormatFromString(Sage.get("transcoder/formats/" + formatName, null));
  }

  public void submitForPotentialTranscoding(MediaFile mf)
  {
    if (doesFileAlwaysRequireTranscoding(mf))
    {
      synchronized (waitingForConversion)
      {
        for (int i = 0; i < waitingForConversion.size(); i++)
        {
          TranscodeJob tj = (TranscodeJob) waitingForConversion.get(i);
          if (tj.getMediaFile() == mf)
            return;
        }
        if (Sage.DBG) System.out.println("Added for transcoding:" + mf);
        sage.media.format.ContainerFormat cf = new sage.media.format.ContainerFormat();
        cf.setFormatName(sage.media.format.MediaFormat.AVI);
        waitingForConversion.add(new DShowTranscodeJob(mf, "AVI", cf, true, null));
      }
      kick();
    }
    else
    {
      // Check for a Favorite auto conversion
      String targetFormat = Carny.getInstance().getAutoConvertFormat(mf);
      if (targetFormat != null && targetFormat.length() > 0)
      {
        if (Sage.DBG) System.out.println("Setting up automatic Favorite conversion to format " + targetFormat + " for " + mf);
        java.io.File destDir = Carny.getInstance().getAutoConvertDest(mf);
        if (destDir != null)
          destDir.mkdirs();
        addTranscodeJob(mf, targetFormat, getPredefinedTargetFormat(targetFormat),
            destDir, Carny.getInstance().isDeleteAfterConversion(mf), 0, 0);
      }
    }
  }

  public int addTranscodeJob(MediaFile srcFile, String formatName, sage.media.format.ContainerFormat theFormat, java.io.File destFile,
      boolean deleteSourceAfter, long clipStartTime, long clipDuration)
  {
    TranscodeJob tj;
    synchronized (waitingForConversion)
    {
      if (Sage.DBG) System.out.println("Added for transcoding:" + srcFile);
      tj = new FFMPEGTranscodeJob(srcFile, formatName, theFormat, deleteSourceAfter, destFile,
          clipStartTime, clipDuration);
      tj.saveToProps();
      waitingForConversion.add(tj);
    }
    kick();
    return tj.getJobID();
  }

  public void clearCompletedTranscodes()
  {
    synchronized (waitingForAbsolution)
    {
      for (int i = 0; i < waitingForAbsolution.size(); i++)
      {
        TranscodeJob tj = (TranscodeJob) waitingForAbsolution.get(i);
        if (tj.getJobState() == TranscodeJob.COMPLETED || tj.getJobState() < 0)
        {
          Sage.removeNode(TRANSCODE_JOB_PROPS + '/' + tj.getJobID());
          waitingForAbsolution.removeElementAt(i--);
        }
      }
    }
  }

  public boolean cancelTranscodeJob(int jobID)
  {
    boolean rv = false;
    synchronized (waitingForConversion)
    {
      for (int i = 0; i < waitingForConversion.size(); i++)
      {
        TranscodeJob tj = (TranscodeJob) waitingForConversion.get(i);
        if (tj.getJobID() == jobID)
        {
          if (Sage.DBG) System.out.println("KillTranscoding for:" + tj.getMediaFile());
          tj.cleanupCurrentTranscode();
          tj.abandon();
          Sage.removeNode(TRANSCODE_JOB_PROPS + '/' + tj.getJobID());
          waitingForConversion.removeElementAt(i);
          rv = true;
          break;
        }
      }
    }
    synchronized (converting)
    {
      for (int i = 0; !rv && i < converting.size(); i++)
      {
        TranscodeJob tj = (TranscodeJob) converting.get(i);
        if (tj.getJobID() == jobID)
        {
          if (Sage.DBG) System.out.println("KillTranscoding for:" + tj.getMediaFile());
          tj.cleanupCurrentTranscode();
          tj.abandon();
          Sage.removeNode(TRANSCODE_JOB_PROPS + '/' + tj.getJobID());
          converting.removeElementAt(i);
          rv = true;
          break;
        }
      }
    }
    synchronized (waitingForAbsolution)
    {
      for (int i = 0; !rv && i < waitingForAbsolution.size(); i++)
      {
        TranscodeJob tj = (TranscodeJob) waitingForAbsolution.get(i);
        if (tj.getJobID() == jobID)
        {
          if (Sage.DBG) System.out.println("KillTranscoding for:" + tj.getMediaFile());
          Sage.removeNode(TRANSCODE_JOB_PROPS + '/' + tj.getJobID());
          waitingForAbsolution.removeElementAt(i);
          rv = true;
          break;
        }
      }
    }
    if (rv)
      kick();
    return rv;
  }

  protected TranscodeJob getJobForID(int jobID)
  {
    synchronized (waitingForConversion)
    {
      for (int i = 0; i < waitingForConversion.size(); i++)
      {
        TranscodeJob tj = (TranscodeJob) waitingForConversion.get(i);
        if (tj.getJobID() == jobID)
          return tj;
      }
    }
    synchronized (converting)
    {
      for (int i = 0; i < converting.size(); i++)
      {
        TranscodeJob tj = (TranscodeJob) converting.get(i);
        if (tj.getJobID() == jobID)
          return tj;
      }
    }
    synchronized (waitingForAbsolution)
    {
      for (int i = 0; i < waitingForAbsolution.size(); i++)
      {
        TranscodeJob tj = (TranscodeJob) waitingForAbsolution.get(i);
        if (tj.getJobID() == jobID)
          return tj;
      }
    }
    return null;
  }

  public int getJobStatusCode(int jobID)
  {
    TranscodeJob tj = getJobForID(jobID);
    return (tj == null) ? -1 : tj.getJobState();
  }

  public MediaFile getJobSourceFile(int jobID)
  {
    TranscodeJob tj = getJobForID(jobID);
    return (tj == null) ? null : tj.getMediaFile();
  }

  public java.io.File getJobDestFile(int jobID)
  {
    TranscodeJob tj = getJobForID(jobID);
    return (tj == null) ? null : tj.getDestFile();
  }

  public String getJobFormat(int jobID)
  {
    TranscodeJob tj = getJobForID(jobID);
    return (tj == null) ? "" : tj.getTargetFormatName();
  }

  public float getJobPercentComplete(int jobID)
  {
    TranscodeJob tj = getJobForID(jobID);
    return (tj == null) ? 0 : tj.getPercentComplete();
  }

  public boolean getJobShouldKeepOriginal(int jobID)
  {
    TranscodeJob tj = getJobForID(jobID);
    return (tj == null) ? false : !tj.shouldReplaceOriginal();
  }

  public long getJobClipStartTime(int jobID)
  {
    TranscodeJob tj = getJobForID(jobID);
    return (tj == null) ? 0 : tj.getClipStartTime();
  }

  public long getJobClipDuration(int jobID)
  {
    TranscodeJob tj = getJobForID(jobID);
    return (tj == null) ? 0 : tj.getClipDuration();
  }

  public int[] getTranscodeJobIDs()
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    synchronized (waitingForConversion)
    {
      for (int i = 0; i < waitingForConversion.size(); i++)
      {
        TranscodeJob tj = (TranscodeJob) waitingForConversion.get(i);
        rv.add(new Integer(tj.getJobID()));
      }
    }
    synchronized (converting)
    {
      for (int i = 0; i < converting.size(); i++)
      {
        TranscodeJob tj = (TranscodeJob) converting.get(i);
        rv.add(new Integer(tj.getJobID()));
      }
    }
    synchronized (waitingForAbsolution)
    {
      for (int i = 0; i < waitingForAbsolution.size(); i++)
      {
        TranscodeJob tj = (TranscodeJob) waitingForAbsolution.get(i);
        rv.add(new Integer(tj.getJobID()));
      }
    }
    int[] irv = new int[rv.size()];
    for (int i = 0; i < rv.size(); i++)
      irv[i] = ((Integer) rv.get(i)).intValue();
    return irv;
  }

  public void killTranscoding(MediaFile mf)
  {
    // IT SHOULD FIND THE MF'S JOB IF IT HAS ONE AND THEN KILL THE FILTER GRAPH, IT MUST
    // WAIT UNTIL ITS KILLED SO ITS SURE IT CAN DELETE THE FILES BECAUSE WE DON'T WANT THE SOURCE FILTER
    // TO BE HOLDING THEM OPEN. THEN IT NEEDS TO REMOVE THEM FROM OUR QUEUES ALSO
    boolean changed = false;
    synchronized (converting)
    {
      for (int i = 0; i < converting.size(); i++)
      {
        TranscodeJob tj = (TranscodeJob) converting.get(i);
        if (tj.getMediaFile() == mf)
        {
          if (Sage.DBG) System.out.println("KillTranscoding for:" + mf);
          tj.cleanupCurrentTranscode();
          tj.abandon();
          tj.setJobState(TranscodeJob.DESTROYED);
          converting.removeElementAt(i);
          Sage.removeNode(TRANSCODE_JOB_PROPS + '/' + tj.getJobID());
          changed = true;
          break;
        }
      }
    }
    if (changed)
      kick();
  }

  public boolean doesFileAlwaysRequireTranscoding(MediaFile mf)
  {
    return sage.media.format.MediaFormat.MPEG2_PS.equals(mf.getContainerFormat()) &&
        sage.media.format.MediaFormat.MPEG4X.equals(mf.getPrimaryVideoFormat()) && !mf.isAnyLiveStream();
  }

  public void kick()
  {
    dirty = true;
    synchronized (lock)
    {
      lock.notifyAll();
    }
  }

  private void startConversion(TranscodeJob tj)
  {
    tj.setJobState(TranscodeJob.WAITING);
    converting.add(tj);
  }

  public boolean isValidSourceFormat(sage.media.format.ContainerFormat cf)
  {
    // We can transcode anything without DRM that's not WMV9 or WMALossless
    if (cf == null || cf.isDRMProtected() ||
        //			sage.media.format.MediaFormat.WMV9.equals(cf.getPrimaryVideoFormat()) ||
        sage.media.format.MediaFormat.WMA9LOSSLESS.equals(cf.getPrimaryAudioFormat()))
      return false;
    else
      return true;
  }

  public boolean requiresPower()
  {
    // This is true if there's any transcode jobs are in the queue
    synchronized (this)
    {
    	return (converting.size() > 0 || waitingForConversion.size() > 0);
    }
  }
  
  private Object lock = new Object();
  private java.util.Vector waitingForConversion = new java.util.Vector();
  private java.util.Vector converting = new java.util.Vector();
  private java.util.Vector waitingForAbsolution = new java.util.Vector();

  private boolean dirty = false;
  private boolean alive;
  private Thread ministryThread;
  private int jobCounter;
  private Object idLock = new Object();
}
