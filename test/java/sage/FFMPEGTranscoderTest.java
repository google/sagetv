package sage;

import org.testng.annotations.Test;
import sage.media.format.BitstreamFormat;
import sage.media.format.ContainerFormat;
import sage.media.format.VideoFormat;

import java.io.File;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.*;

public class FFMPEGTranscoderTest
{

  @Test
  public void testParseFrameSize() throws Throwable
  {
    TestUtils.initializeSageTVForTesting();
    FFMPEGTranscoder transcoder = new FFMPEGTranscoder();

    // should parse and return values
    expectSize(transcoder.parseFrameSize("640x480", 1280, 720), 640, 480);
    expectSize(transcoder.parseFrameSize("640X480", 1280, 720), 640, 480);
    expectSize(transcoder.parseFrameSize("640 x 480", 1280, 720), 640, 480);

    // should fail and use defaults
    expectSize(transcoder.parseFrameSize("", 1280, 720), 1280, 720);
    expectSize(transcoder.parseFrameSize(null, 1280, 720), 1280, 720);
    expectSize(transcoder.parseFrameSize("640w x 480h", 1280, 720), 1280, 720);

    // setup to use original format
    transcoder.sourceFormat = new ContainerFormat();
    transcoder.sourceFormat.setStreamFormats(new BitstreamFormat[] {new VideoFormat()});
    transcoder.sourceFormat.getVideoFormat().setWidth(640);
    transcoder.sourceFormat.getVideoFormat().setHeight(480);

    expectSize(transcoder.parseFrameSize("original", 1280, 720), 640, 480);
  }

  @Test
  public void testTranscoderUsesOptionsFromProperties() throws Throwable
  {
    FFMPEGTranscoder transcoder = spy(new FFMPEGTranscoder());
    transcoder.httplsMode=true;
    transcoder.segmentDur = 10;
    transcoder.currFile = new File("dummyfile.ts");


    int bw = 34;
    transcoder.estimatedBandwidth = 34*1000;

    // ensure that this is not set in the Sage.properties for testing
    // so that we can ensure we get the defaults
    Sage.remove(String.format(FFMPEGTranscoder.BITRATE_OPTIONS_SIZE_KEY, bw));
    try
    {
      try
      {
        transcoder.startTranscode();
        transcoder.stopTranscode();
      } catch (Throwable t)
      {
        // this will fail, but, we just want to make sure our method was called.
      }

      // this is the default when NO properties are set.
      verify(transcoder).parseFrameSize(eq("480x272"), anyInt(), anyInt());


      // ensure that we set this in the Sage.properties, so that we can verify that actually use the Sage.properties
      Sage.put(String.format(FFMPEGTranscoder.BITRATE_OPTIONS_SIZE_KEY, bw), "1280x720");
      try
      {
        transcoder.startTranscode();
        transcoder.stopTranscode();
      } catch (Throwable t)
      {
        // this will fail, but, we just want to make sure our method was called.
      }

      // this should have come from xcode bitrate specific options
      verify(transcoder).parseFrameSize(eq("1280x720"), anyInt(), anyInt());

      // ensure that this is not set in the Sage.properties for testing
      // so that we can ensure we get the defaults
      Sage.remove(String.format(FFMPEGTranscoder.BITRATE_OPTIONS_SIZE_KEY, bw));


      // test if default will be used
      // ensure that we set this in the Sage.properties, so that we can verify that actually use the Sage.properties
      Sage.put(String.format(FFMPEGTranscoder.BITRATE_OPTIONS_SIZE_KEY, "default"), "200x100");
      try
      {
        transcoder.startTranscode();
        transcoder.stopTranscode();
      } catch (Throwable t)
      {
        // this will fail, but, we just want to make sure our method was called.
      }

      // this should have come from xcode bitrate specific options
      verify(transcoder).parseFrameSize(eq("200x100"), anyInt(), anyInt());

      // ensure that this is not set in the Sage.properties for testing
      // so that we can ensure we get the defaults
      Sage.remove(String.format(FFMPEGTranscoder.BITRATE_OPTIONS_SIZE_KEY, "default"));
    }
    finally
    {
      // ensure that this is not set in the Sage.properties for testing
      // so that we can ensure we get the defaults
      Sage.remove(String.format(FFMPEGTranscoder.BITRATE_OPTIONS_SIZE_KEY, bw));
    }
  }

    private void expectSize(int[] sizes, int w, int h)
  {
    assertEquals(sizes[0], w);
    assertEquals(sizes[1], h);
  }
}