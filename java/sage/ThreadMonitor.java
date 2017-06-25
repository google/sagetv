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

import java.io.File;
import java.lang.management.ThreadMXBean;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.TreeSet;

/**
 * ThreadMonitor class is a simple way of measuring the % cpu time used by each thread over the
 * lifetime of the application. Uses MXBeans
 */
public final class ThreadMonitor extends Thread
{
  /**
   * we keep a map of threads -> cpu info for calculating %cpu but some threads will die, so we
   * should clean them up
   */
  private static final int LOCAL_THREAD_LIST_CLEANUP_INTERVAL = 10;

  /**
   * Jiffies per milli is hardcoded in linux kernel.
   */
  private static final long JIFFIES_PER_MILLI = 10;


  private final long recalcInterval;
  private final ThreadMXBean threadBean;

  /**
   * Construct and start with the specified reporting interval<br>
   */
  public ThreadMonitor(long interval)
  {
    super("ThreadMonitor");
    if (interval < 1000)
    {
      throw new IllegalArgumentException("Interval must be > 1000");
    }
    this.recalcInterval = interval;
    this.threadBean = java.lang.management.ManagementFactory.getThreadMXBean();
    setDaemon(true);
    start();
  }

  static private class ThreadCpuInfo
  {
    long threadId;
    long cpuMillis;
    long cpuPercent;

    /**
     * reverse cpu time comparator
     */
    static final Comparator<ThreadCpuInfo> threadCpuTimeComparator =
        new Comparator<ThreadMonitor.ThreadCpuInfo>()
        {
      public int compare(ThreadCpuInfo o1, ThreadCpuInfo o2)
      {
        return (o2.cpuMillis < o1.cpuMillis ? -1 : (o2.cpuMillis == o1.cpuMillis ? 0 : 1));
      }
        };
        /**
         * reverse cpu % comparator
         */
        static final Comparator<ThreadCpuInfo> threadCpuPercentComparator =
            new Comparator<ThreadMonitor.ThreadCpuInfo>()
            {
          public int compare(ThreadCpuInfo o1, ThreadCpuInfo o2)
          {
            return (o2.cpuPercent < o1.cpuPercent ? -1 : (o2.cpuPercent == o1.cpuPercent ? 0 : 1));
          }
            };
  }

  @Override
  public void run()
  {
    System.out.println("Thread CPU monitoring started interval=" + recalcInterval);

    long lastTimeMillis;
    HashMap<Long, Long> cpuTimeByThreadId = new HashMap<Long, Long>();
    int iterations = 0;

    while (true)
    {
      lastTimeMillis = Sage.time();
      try
      {
        sleep(recalcInterval);
      } catch (InterruptedException e)
      {/* ignore */
      }
      try
      {
        long[] threadIds = threadBean.getAllThreadIds();

        TreeSet<ThreadCpuInfo> cpuInfosCpuTime =
            new TreeSet<ThreadCpuInfo>(ThreadCpuInfo.threadCpuTimeComparator);
        TreeSet<ThreadCpuInfo> cpuInfosPercentCpu =
            new TreeSet<ThreadCpuInfo>(ThreadCpuInfo.threadCpuPercentComparator);
        long intervalTime = Sage.time() - lastTimeMillis;

        for (long threadId : threadIds)
        {
          if (threadId > 0)
          {
            ThreadCpuInfo cpuInfo = new ThreadCpuInfo();
            cpuInfo.threadId = threadId;
            // non-embedded systems can use threadBean to get cpuTime
            cpuInfo.cpuMillis = threadBean.getThreadCpuTime(threadId) / 1000000;
            Long lastCpuTimeL = cpuTimeByThreadId.get(threadId);
            long lastCpuTime = lastCpuTimeL == null ? 0 : lastCpuTimeL.longValue();
            cpuInfo.cpuPercent = (cpuInfo.cpuMillis - lastCpuTime) * 100 / intervalTime;
            if (cpuInfo.cpuMillis > 0)
            {
              cpuTimeByThreadId.put(threadId, cpuInfo.cpuMillis);
              cpuInfosCpuTime.add(cpuInfo);
            }
            // only add percent cpu if there is some...
            if (cpuInfo.cpuPercent > 0)
            {
              cpuInfosPercentCpu.add(cpuInfo);
            }
          }
        }

        int num = 0;
        StringBuffer outLine = new StringBuffer();
        outLine.append("Top 10 Cumulative CPU(sec)");
        for (ThreadCpuInfo threadCpuInfo : cpuInfosCpuTime)
        {
          if (threadCpuInfo.cpuMillis > 999L)
          {
            outLine.append(" ");
            outLine.append(
                threadBean.getThreadInfo(threadCpuInfo.threadId).getThreadName().replace(' ', '_'));
            outLine.append(":");
            outLine.append(threadCpuInfo.cpuMillis / 1000);
            num++;
          }
          if (num >= 10)
          {
            break;
          }
        }
        System.out.println(outLine.toString());

        // sort and print % CPU time
        outLine.setLength(0);
        outLine.append("Top 10 %CPU (last ");
        outLine.append(recalcInterval / 1000);
        outLine.append("s)");
        num = 0;
        for (ThreadCpuInfo threadCpuInfo : cpuInfosPercentCpu)
        {
          if (threadCpuInfo.cpuPercent > 0 && num < 10)
          {
            outLine.append(" ");
            outLine.append(
                threadBean.getThreadInfo(threadCpuInfo.threadId).getThreadName().replace(' ', '_'));
            outLine.append(":");
            outLine.append(threadCpuInfo.cpuPercent);
            outLine.append("%");
            num++;
          }
          if (num >= 10)
          {
            break;
          }
        }
        System.out.println(outLine.toString());


        // Every 10 iterations, clean out any old threads from the cpu time map
        iterations++;
        if (iterations % LOCAL_THREAD_LIST_CLEANUP_INTERVAL == 0)
        {
          // this is a slow On^2 op, which is why we don't do it on every iteration
          HashSet<Long> obsoleteThreadIds = new HashSet<Long>(cpuTimeByThreadId.keySet());
          for (long threadId : threadIds)
          {
            obsoleteThreadIds.remove(threadId);
          }
          cpuTimeByThreadId.keySet().removeAll(obsoleteThreadIds);
        }
      } catch (Throwable e)
      {
        e.printStackTrace(System.out);
      }
    }
  }


  private static final int PROC_CPU_USER_JIFFIES_ELEMENT = 14;

  /**
   * Use /proc/self/task/threadID/stat to get CPUtime for specified thread...
   * <p>
   * see man proc...
   * <p>
   * cpuUserTime is element 14<br>
   * cpuUserSysTime is element 15
   */
  private long getCpuTimeMs(long threadId)
  {
    File procFile = new File("/proc/self/task/" + threadId + "/stat");
    if (!procFile.canRead())
    {
      return -1;
    }
    String procStats = IOUtils.getFileAsString(procFile);
    StringTokenizer tok = new StringTokenizer(procStats, " ");
    // skip 14 tokens
    for (int i = 0; tok.hasMoreTokens() && i < PROC_CPU_USER_JIFFIES_ELEMENT - 1; i++)
    {
      tok.nextToken();
    }
    String cpuTimeUserStr = tok.nextToken();
    String cpuTimeSystemStr = tok.nextToken();
    try
    {
      long cputime =
          (Long.parseLong(cpuTimeUserStr) + Long.parseLong(cpuTimeSystemStr))
          * JIFFIES_PER_MILLI;
      return cputime;
    } catch (NumberFormatException e)
    {
      e.printStackTrace();
    }
    return -1;

  }
}
