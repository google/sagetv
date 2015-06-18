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

/**
 * This is used with NIO sockets to do timeouts. The only problem is that when the timeout
 * occurs, it'll close the socket instead of causing a SocketTimeoutException (because we have no way to do that)
 * @author Narflex
 */
public class TimeoutHandler implements Runnable
{
  public static final long WAIT_DURATION = 10000;
  /** Creates a new instance of TimeoutHandler */
  private TimeoutHandler()
  {
    Thread t = new Thread(this, "TimeoutHandler");
    t.setDaemon(true);
    t.start();
  }

  // If we're smart about this we can avoid waking up this thread every time a new timeout is registered; as long as our sleep time
  // is shorter than the timeouts; we'll never have to be async notified and this thread will fire quite rarely then (relatively speaking)

  public void run()
  {
    while (true)
    {
      long waitTime = WAIT_DURATION;
      if (!mappy.isEmpty())
      {
        long currTime = Sage.eventTime();
        synchronized (mappy)
        {
          java.util.Iterator walker = mappy.entrySet().iterator();
          while (walker.hasNext())
          {
            java.util.Map.Entry currEnt = (java.util.Map.Entry) walker.next();
            long currTimeout = ((Long) currEnt.getValue()).longValue() - currTime;
            if (currTimeout <= 0)
            {
              // Timeout has expired!
              if (Sage.DBG) System.out.println("TIMEOUT occurred - close the socket asynchronously for " + currEnt.getKey());
              Object currKey = currEnt.getKey();
              try
              {
                if (currKey instanceof java.net.Socket)
                  ((java.net.Socket) currKey).close();
                else if (currKey instanceof java.nio.channels.SocketChannel)
                  ((java.nio.channels.SocketChannel) currKey).close();
              }
              catch(Exception e){}
              walker.remove();
            }
            else
            {
              waitTime = Math.min(waitTime, currTimeout);
            }
          }
        }
      }
      synchronized (this)
      {
        try
        {
          wait(waitTime);
        }
        catch (Exception e){}
      }
    }
  }

  private void addTimeout(long timeout, Object sake)
  {
    if (timeout > 0)
    {
      mappy.put(sake, new Long(Sage.eventTime() + timeout));
      if (timeout < WAIT_DURATION)
      {
        if (Sage.DBG) System.out.println("WARNING using a timeout so small that it disables optimization of the TimeoutHandler!");
        synchronized (this)
        {
          notify();
        }
      }
    }
  }

  private void removeTimeout(Object sake)
  {
    mappy.remove(sake);
  }

  public static void registerTimeout(long timeout, java.net.Socket sake)
  {
    registerTimeoutImpl(timeout, sake);
  }
  public static void registerTimeout(long timeout, java.nio.channels.SocketChannel sake)
  {
    registerTimeoutImpl(timeout, sake);
  }
  private static void registerTimeoutImpl(long timeout, Object sake)
  {
    if (chosenOne == null)
    {
      synchronized (createLock)
      {
        if (chosenOne == null)
          chosenOne = new TimeoutHandler();
      }
    }
    chosenOne.addTimeout(timeout, sake);
  }

  public static void clearTimeout(Object sake)
  {
    if (chosenOne != null)
      chosenOne.removeTimeout(sake);
  }

  public static boolean isTimeoutRegistered(Object sake) {
    return chosenOne != null && chosenOne.mappy.containsKey(sake);
  }

  private static Object createLock = new Object();
  private static TimeoutHandler chosenOne;
  private java.util.Map mappy = java.util.Collections.synchronizedMap(new java.util.HashMap());
}
