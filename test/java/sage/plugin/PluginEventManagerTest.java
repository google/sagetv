package sage.plugin;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import sage.SageTVEventListener;
import sage.TestUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.*;

/**
 * Created by seans on 17/11/16.
 */
public class PluginEventManagerTest
{
  @BeforeClass
  public void setup() throws Throwable
  {
    TestUtils.initializeSageTVForTesting();
  }

  @Test
  public void testEventPost() throws InterruptedException
  {
    final AtomicBoolean eventHandled = new AtomicBoolean();
    SageTVEventListener listener = new SageTVEventListener()
    {
      @Override
      public void sageEvent(String eventName, Map eventVars)
      {
        System.out.println("Event was fired: " + eventName);
        eventHandled.set(true);
      }
    };

    PluginEventManager.getInstance().reset();
    PluginEventManager.getInstance().startup();
    PluginEventManager.getInstance().eventSubscribe(listener,"test_event");

    // previously passing NULL map would cause error, and the event queue would be deadlocked
    // this should now pass be handled correctly
    PluginEventManager.getInstance().postEvent("test_event", null, false);

    Thread.sleep(500);

    assertTrue(eventHandled.get(), "Event was not handled");
  }
}