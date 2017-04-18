package sage;

import java.util.Map;
import java.util.Vector;

public interface SchedulerInterface extends Runnable
{
  public boolean isPrepped();

  public long getNextMustSeeTime();

  public void setClientDontKnowFlag(boolean x);

  public boolean areThereDontKnows();

  public void kick(boolean delay);

  public void prepareForStandby();

  public Map<DBObject, Vector<Airing>> getUnresolvedConflictsMap();

  public Map<DBObject, Vector<Airing>> getConflictsMap();

  public CaptureDevice[] getMyEncoders();

  public String[] getMyEncoderNames();

  public Vector<Airing> getSchedule(CaptureDevice capDev);

  public Vector<Airing> getMustSee(CaptureDevice capDev);

  public void goodbye();
}
