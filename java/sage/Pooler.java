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

import java.util.ArrayList;

/**
 *
 * @author Narflex
 */
public class Pooler
{
  private Pooler()
  {
  }
  public static final String[] EMPTY_STRING_ARRAY = new String[0];
  public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  public static final Stringer[] EMPTY_STRINGER_ARRAY = new Stringer[0];
  public static final Person[] EMPTY_PERSON_ARRAY = new Person[0];
  public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  public static final byte[][] EMPTY_2D_BYTE_ARRAY = new byte[0][0];
  public static final int[] EMPTY_INT_ARRAY = new int[0];
  public static final short[] EMPTY_SHORT_ARRAY = new short[0];
  public static final long[] EMPTY_LONG_ARRAY = new long[0];
  public static final Airing[] EMPTY_AIRING_ARRAY = new Airing[0];
  public static final Show[] EMPTY_SHOW_ARRAY = new Show[0];
  public static final tv.sage.mod.AbstractWidget[] EMPTY_ABSTRACTWIDGET_ARRAY = new tv.sage.mod.AbstractWidget[0];
  public static final Channel[] EMPTY_CHANNEL_ARRAY = new Channel[0];
  public static final MediaFile[] EMPTY_MEDIA_FILE_ARRAY = new MediaFile[0];
  private static java.util.Stack vectorPool = new java.util.Stack();
  private static int numVecsCreated = 0;
  private static java.util.Stack arrayListPool = new java.util.Stack();
  private static int numArrayListCreated = 0;
  private static java.util.Stack hashSetPool = new java.util.Stack();
  private static int numHashSetCreated = 0;
  private static java.util.Stack hashMapPool = new java.util.Stack();
  private static int numHashMapCreated = 0;
  private static java.util.Stack sbPool = new java.util.Stack();
  private static int numSBCreated = 0;
  // NOTE: We can't use a property for this since the Pooler class is referenced from SageProperties and depending upon the VM (and JIT)
  // it may decide to then load the Pooler class before we have the properties data loaded
  private static final boolean DISABLE_POOLER = false;//Sage.getBoolean("disable_pooler", false);
  public static java.util.Vector getPooledVector()
  {
    if (DISABLE_POOLER) return new java.util.Vector();
    synchronized (vectorPool)
    {
      if (vectorPool.isEmpty())
      {
        if (Sage.DBG && (((numVecsCreated++) % 100) == 0)) System.out.println("Increased vector pool to size=" + numVecsCreated);
        return new java.util.Vector();
      }
      else
        return (java.util.Vector) vectorPool.pop();
    }
  }
  public static void returnPooledVector(java.util.Vector v)
  {
    if (DISABLE_POOLER) return;
    v.clear();
    vectorPool.push(v);
  }

  public static java.util.ArrayList getPooledArrayList()
  {
    if (DISABLE_POOLER) return new java.util.ArrayList();
    synchronized (arrayListPool)
    {
      if (arrayListPool.isEmpty())
      {
        if (Sage.DBG && (((numArrayListCreated++) % 100) == 0)) System.out.println("Increased ArrayList pool to size=" + numArrayListCreated);
        return new java.util.ArrayList();
      }
      else
        return (java.util.ArrayList) arrayListPool.pop();
    }
  }

  public static <T> ArrayList<T> getPooledArrayListType()
  {
    if (DISABLE_POOLER) return new ArrayList<T>();
    synchronized (arrayListPool)
    {
      if (arrayListPool.isEmpty())
      {
        if (Sage.DBG && (((numArrayListCreated++) % 100) == 0)) System.out.println("Increased ArrayList pool to size=" + numArrayListCreated);
        return new ArrayList<T>();
      }
      else {
        @SuppressWarnings("unchecked")
        ArrayList<T> rv = (ArrayList<T>) arrayListPool.pop();
        return rv;
      }
    }
  }

  public static void returnPooledArrayList(java.util.ArrayList v)
  {
    if (DISABLE_POOLER) return;
    v.clear();
    arrayListPool.push(v);
  }

  public static java.util.HashSet getPooledHashSet()
  {
    if (DISABLE_POOLER) return new java.util.HashSet();
    synchronized (hashSetPool)
    {
      if (hashSetPool.isEmpty())
      {
        if (Sage.DBG && (((numHashSetCreated++) % 100) == 0)) System.out.println("Increased hash set pool to size=" + numHashSetCreated);
        return new java.util.HashSet();
      }
      else
        return (java.util.HashSet) hashSetPool.pop();
    }
  }
  public static void returnPooledHashSet(java.util.HashSet v)
  {
    if (DISABLE_POOLER) return;
    v.clear();
    hashSetPool.push(v);
  }

  public static java.util.HashMap getPooledHashMap()
  {
    if (DISABLE_POOLER) return new java.util.HashMap();
    synchronized (hashMapPool)
    {
      if (hashMapPool.isEmpty())
      {
        if (Sage.DBG && (((numHashMapCreated++) % 100) == 0)) System.out.println("Increased hash Map pool to size=" + numHashMapCreated);
        return new java.util.HashMap();
      }
      else
        return (java.util.HashMap) hashMapPool.pop();
    }
  }
  public static void returnPooledHashMap(java.util.HashMap v)
  {
    if (DISABLE_POOLER) return;
    v.clear();
    hashMapPool.push(v);
  }

  public static StringBuffer getPooledSB()
  {
    if (DISABLE_POOLER) return new StringBuffer();
    synchronized (sbPool)
    {
      if (sbPool.isEmpty())
      {
        if (Sage.DBG && (((numSBCreated++) % 100) == 0)) System.out.println("Increased StringBuffer pool to size=" + numSBCreated);
        return new StringBuffer();
      }
      else
        return (StringBuffer) sbPool.pop();
    }
  }
  public static void returnPooledSB(StringBuffer v)
  {
    if (DISABLE_POOLER) return;
    v.setLength(0);
    sbPool.push(v);
  }

  // Executes the Runnable argument in another thread immediately and sets the thread at the specified priority,
  // all executions are in daemon threads
  public static void execute(Runnable runny)
  {
    execute(runny, null, Thread.NORM_PRIORITY);
  }
  public static void execute(Runnable runny, String name)
  {
    execute(runny, name, Thread.NORM_PRIORITY);
  }
  public static void execute(Runnable runny, int priority)
  {
    execute(runny, null, priority);
  }
  public static void execute(Runnable runny, String name, int priority)
  {
    if (name == null) name = "PooledThread";
    if (DISABLE_POOLER)
    {
      Thread t = new Thread(runny, name);
      t.setDaemon(true);
      t.setPriority(priority);
      t.start();
      return;
    }
    synchronized (threadPool)
    {
      if (threadPool.isEmpty())
      {
        PooledThread pt = new PooledThread();
        pt.start();
        pt.execute(runny, name, priority);
        if (Sage.DBG && ((numThreadsCreated++ % 5) == 0))
        {
          System.out.println("Increased Thread pool to size=" + numThreadsCreated);
          if (numThreadsCreated > 100)
          {
            // Something is definitely wrong here...dump all the thread stacks so maybe we can see what it is
            System.out.println("Thread pool count has grown excessively large....dumping all thread stacks:");
            AWTThreadWatcher.dumpThreadStates();
          }
        }
      }
      else
      {
        PooledThread pt = threadPool.pop();
        pt.execute(runny, name, priority);
      }
    }
  }
  private static java.util.Stack<PooledThread> threadPool = new java.util.Stack<PooledThread>();
  private static int numThreadsCreated;
  private static class PooledThread extends Thread
  {
    public PooledThread()
    {
      super();
      setDaemon(true);
      alive = true;
    }
    public void run()
    {
      while (alive)
      {
        try
        {
          synchronized (this)
          {
            if (nextTask == null)
            {
              wait();
              continue;
            }
          }
          nextTask.run();
          nextTask = null;
          if (DISABLE_POOLER) return;
          threadPool.push(this);
        }
        catch (Throwable t)
        {
          if (Sage.DBG) System.out.println("PooledThread ended w/ an exception: " + t);
          if (Sage.DBG) t.printStackTrace();
          nextTask = null;
          if (t instanceof OutOfMemoryError)
          {
            // On embedded just terminate the JVM and let it autorestart if we are out of memory
            sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createOOMMsg());
          }
        }
      }
    }
    public synchronized void kill()
    {
      alive = false;
      notify();
    }
    public synchronized void execute(Runnable runny, String name, int newPriority)
    {
      if (name != null)
        super.setName(name);
      if (getPriority() != newPriority)
        setPriority(newPriority);
      nextTask = runny;
      notify();
    }
    private boolean alive;
    private Runnable nextTask;
  }
}
