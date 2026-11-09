package sage;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class EPGScheduledUpdateTest
{
  @BeforeClass
  public void setup() throws Throwable
  {
    TestUtils.initializeSageTVForTesting();
  }

  @AfterMethod
  public void cleanup()
  {
    Sage.removeNode(EPG.EPG_DATA_SOURCES_KEY + "/900001");
    Sage.removeNode(EPG.EPG_DATA_SOURCES_KEY + "/900002");
  }

  private TestSource source(int id, long lastRun)
  {
    Sage.putLong(EPG.EPG_DATA_SOURCES_KEY + '/' + id + "/last_run", lastRun);
    TestSource source = new TestSource(id);
    source.setExpandedUntil(Sage.time() + Sage.MILLIS_PER_DAY);
    return source;
  }

  @Test
  public void overdueWindowRunsDespiteFutureExpansion()
  {
    long now = Sage.time();
    long window = now - Sage.MILLIS_PER_HR;
    TestSource source = source(900001, window - 1);
    assertTrue(source.getTimeTillUpdate() > 0);
    assertEquals(EPG.getScheduledUpdateWait(source, window, now), 0L);
    assertTrue(source.expand());
    assertEquals(source.imports, 1);
    assertTrue(source.isChanDownloadComplete());
    long expandedUntil = source.getExpandedUntil();
    assertTrue(expandedUntil > now);
    assertEquals(EPG.getScheduledUpdateWait(source, window, Sage.time()), Long.MAX_VALUE);
    assertEquals(source.getExpandedUntil(), expandedUntil);
    assertEquals(source.imports, 1);
  }

  @Test
  public void futureWindowAndDisabledSourceAreNotForced()
  {
    long now = Sage.time();
    TestSource source = source(900001, 0);
    long expandedUntil = source.getExpandedUntil();
    assertEquals(EPG.getScheduledUpdateWait(source, now + Sage.MILLIS_PER_HR, now), Long.MAX_VALUE);
    assertEquals(EPG.getScheduledUpdateWait(source, now, now), Long.MAX_VALUE);
    assertEquals(source.getExpandedUntil(), expandedUntil);
    source.setEnabled(false);
    assertEquals(EPG.getScheduledUpdateWait(source, now - Sage.MILLIS_PER_HR, now), Long.MAX_VALUE);
    assertEquals(source.getExpandedUntil(), expandedUntil);
  }

  @Test
  public void sourcesAreCheckedIndependently()
  {
    long now = Sage.time();
    long window = now - Sage.MILLIS_PER_HR;
    TestSource completed = source(900001, window);
    TestSource overdue = source(900002, window - 1);
    assertEquals(EPG.getScheduledUpdateWait(completed, window, now), Long.MAX_VALUE);
    assertEquals(EPG.getScheduledUpdateWait(overdue, window, now), 0L);
    assertTrue(completed.getExpandedUntil() > now);
    assertEquals(overdue.getExpandedUntil(), 0L);
  }

  @Test
  public void retryDelayIsRespected()
  {
    long now = Sage.time();
    long window = now - Sage.MILLIS_PER_HR;
    TestSource source = source(900001, window - 1);
    source.retryDelay = Sage.MILLIS_PER_HR;
    assertEquals(EPG.getScheduledUpdateWait(source, window, now), Sage.MILLIS_PER_HR);
    assertTrue(source.expand());
    assertEquals(source.imports, 0);
    assertEquals(EPG.getScheduledUpdateWait(source, window, now), Sage.MILLIS_PER_HR);
    source.retryDelay = 0;
    assertEquals(EPG.getScheduledUpdateWait(source, window, now), 0L);
    assertTrue(source.expand());
    assertEquals(source.imports, 1);
  }

  @Test
  public void failedAttemptDoesNotResetSourceAgainInSameWindow()
  {
    long now = Sage.time();
    long window = now - Sage.MILLIS_PER_HR;
    TestSource source = source(900001, window - 1);
    source.succeed = false;
    assertEquals(EPG.getScheduledUpdateWait(source, window, now), 0L);
    assertFalse(source.expand());
    assertEquals(source.imports, 1);
    long expandedUntil = source.getExpandedUntil();
    assertEquals(EPG.getScheduledUpdateWait(source, window, Sage.time()), Long.MAX_VALUE);
    assertEquals(source.getExpandedUntil(), expandedUntil);
  }

  private static class TestSource extends EPGDataSource
  {
    int imports;
    long retryDelay;
    boolean succeed = true;

    TestSource(int id)
    {
      super(id);
      dataScanAllowed = false;
      chanDownloadComplete = true;
    }

    @Override
    public boolean initDataScanInfo()
    {
      return false;
    }

    @Override
    public long getTimeTillExpand()
    {
      return retryDelay > 0 ? retryDelay : super.getTimeTillExpand();
    }

    @Override
    protected boolean extractGuide(long guideTime)
    {
      imports++;
      return succeed;
    }
  }
}