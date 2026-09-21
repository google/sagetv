package sage;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ImportedMediaMetadataRepairTest
{
  @Test
  public void repairsOneMillisecondImportedVideoWithMissingStreams()
  {
    assertTrue(ImportedMediaMetadataRepair.shouldRepair(true, true, false,
        false, false, false, false, 1, true, 2_202_499_419L, 1, 0, 0));
  }

  @Test
  public void repairsOneMillisecondImportedVideoWithZeroFormatDuration()
  {
    assertTrue(ImportedMediaMetadataRepair.shouldRepair(true, true, false,
        false, false, false, false, 1, true, 2_202_499_419L, 1, 0, 4));
  }

  @Test
  public void leavesValidImportedVideoUntouched()
  {
    assertFalse(ImportedMediaMetadataRepair.shouldRepair(true, true, false,
        false, false, false, false, 1, true, 2_202_499_419L, 7_094_588, 7_094_588, 4));
  }

  @Test
  public void excludesRecordingLiveDiscMusicPictureAndRemoteFiles()
  {
    assertFalse(ImportedMediaMetadataRepair.shouldRepair(true, true, true,
        false, false, false, false, 1, true, 1_000_000, 1, 0, 0));
    assertFalse(ImportedMediaMetadataRepair.shouldRepair(true, true, false,
        true, false, false, false, 1, true, 1_000_000, 1, 0, 0));
    assertFalse(ImportedMediaMetadataRepair.shouldRepair(true, true, false,
        false, true, false, false, 1, true, 1_000_000, 1, 0, 0));
    assertFalse(ImportedMediaMetadataRepair.shouldRepair(true, true, false,
        false, false, true, false, 1, true, 1_000_000, 1, 0, 0));
    assertFalse(ImportedMediaMetadataRepair.shouldRepair(true, true, false,
        false, false, false, true, 1, true, 1_000_000, 1, 0, 0));
    assertFalse(ImportedMediaMetadataRepair.shouldRepair(true, false, false,
        false, false, false, false, 1, true, 1_000_000, 1, 0, 0));
  }

  @Test
  public void excludesTinyAndMultiSegmentInputs()
  {
    assertFalse(ImportedMediaMetadataRepair.shouldRepair(false, true, false,
        false, false, false, false, 1, true, 1_000_000, 1, 0, 0));
    assertFalse(ImportedMediaMetadataRepair.shouldRepair(true, true, false,
        false, false, false, false, 1, false, 1_000_000, 1, 0, 0));
    assertFalse(ImportedMediaMetadataRepair.shouldRepair(true, true, false,
        false, false, false, false, 1, true, 1024, 1, 0, 0));
    assertFalse(ImportedMediaMetadataRepair.shouldRepair(true, true, false,
        false, false, false, false, 2, true, 1_000_000, 1, 0, 0));
  }
}
