package sage;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MiniClientCapabilityProtocolTest
{
  @Test
  public void playbackRateRequiresAnExplicitNonEmptyCapability()
  {
    assertFalse(MiniClientSageRenderer.isVideoPlaybackRateSupported(null));
    assertFalse(MiniClientSageRenderer.isVideoPlaybackRateSupported(""));
    assertFalse(MiniClientSageRenderer.isVideoPlaybackRateSupported("  "));
    assertTrue(MiniClientSageRenderer.isVideoPlaybackRateSupported(
        "NATIVE_FORWARD_0.5_TO_2;SEEK_SCAN_4_TO_256"));
  }

  @Test
  public void unknownDiscPolicyFailsSafelyToAuto()
  {
    assertEquals(MiniClientSageRenderer.normalizeDvdDiscPolicy("native"), "native");
    assertEquals(MiniClientSageRenderer.normalizeDvdDiscPolicy("HYBRID"), "hybrid");
    assertEquals(MiniClientSageRenderer.normalizeDvdDiscPolicy("mim_main_feature"), "mim_main_feature");
    assertEquals(MiniClientSageRenderer.normalizeDvdDiscPolicy("future-mode"), "auto");
    assertEquals(MiniClientSageRenderer.normalizeDvdDiscPolicy(null), "auto");
  }

  @Test
  public void mediaStateFragmentIsVersionedAndEncoded()
  {
    assertEquals(MiniPlayer.appendMediaStateUrl("stv://host//tv/a.ts", (byte) 1, (byte) 2,
        "MPEG2-TS;video=1", "5.1", true, 4096),
        "stv://host//tv/a.ts#sagetv-media-v1;active=1;buffer=4096;major=1;minor=2;channel=5.1;encoding=MPEG2-TS%3Bvideo%3D1");
  }

}
