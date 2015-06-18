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

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Table
{
  private static final boolean VERIFY_INDICES = false;
  Table(byte inTableCode, Index inPrimary)
  {
    this(inTableCode, inPrimary, new Index[0]);
  }

  Table(byte inTableCode, Index inPrimary, Index[] inOthers)
  {
    num = 0;
    tableCode = inTableCode;
    primary = inPrimary;
    primary.table = this;
    others = inOthers;
    for (int i = 0; i < others.length; i++)
      others[i].table = this;

    // I'm going with a 'fair' lock for now...it'll be closer to current functionality
    rwLock = new ReentrantReadWriteLock(true);
  }

  void clearProfile()
  {
    primary.clearProfile();
  }

  void clear()
  {
    try {
      acquireWriteLock();
      primary.clear();
      for (int i = 0; i < others.length; i++)
        others[i].clear();
      num = 0;
    } finally {
      releaseWriteLock();
    }
  }

  void check()
  {
    primary.check();
    for (int i = 0; i < others.length; i++)
      others[i].check();
  }

  Index getIndex(byte indexCode)
  {
    if (indexCode <= 0) return primary;
    for (int i = 0; i < others.length; i++)
      if (others[i].indexCode == indexCode)
        return others[i];
    throw new ArrayIndexOutOfBoundsException("Index code " + indexCode + " is not found in tables.");
  }

  boolean remove(DBObject removeMe, boolean logTX)
  {
    boolean rv;
    try {
      acquireWriteLock();
      if (rv = primary.remove(removeMe)) {
        for (int i = 0; i < others.length; i++)
          others[i].remove(removeMe);
        num--;
        modCount++;
      }
      if (VERIFY_INDICES) {
        for (int i = 0; i < primary.data.length - 1; i++) {
          if (primary.comp.compare(primary.data[i], primary.data[i + 1]) > 0)
            System.out.println("PRIMARY REMOVE SORT IS SCREWED UP code=" + tableCode);
          for (int j = 0; j < others.length; j++)
            if (others[j].comp.compare(others[j].data[i], others[j].data[i + 1]) > 0)
              System.out.println("OTHER REMOVE SORT IS SCREWED UP code=" + tableCode + " otherIdx=" + j);
        }
      }
    } finally {
      releaseWriteLock();
    }
    if (logTX)
      wiz.logRemove(removeMe, tableCode);
    return rv;
  }

  void add(DBObject addMe, boolean logTX)
  {
    try {
      acquireWriteLock();
      primary.add(addMe);
      for (int i = 0; i < others.length; i++)
        others[i].add(addMe);
      num++;
      modCount++;
      if (VERIFY_INDICES) {
        for (int i = 0; i < primary.data.length - 1; i++) {
          if (primary.comp.compare(primary.data[i], primary.data[i + 1]) > 0)
            System.out.println("PRIMARY ADD SORT IS SCREWED UP code=" + tableCode);
          for (int j = 0; j < others.length; j++)
            if (others[j].comp.compare(others[j].data[i], others[j].data[i + 1]) > 0)
              System.out.println("OTHER ADD SORT IS SCREWED UP code=" + tableCode + " otherIdx=" + j);
        }
      }
    } finally {
      releaseWriteLock();
    }

    if (logTX)
      wiz.logAdd(addMe, tableCode);
  }

  void update(DBObject updateMe, DBObject newMe, boolean logTX)
  {
    try {
      acquireWriteLock();
      primary.update(updateMe, newMe);
      for (int i = 0; i<others.length;i++)
        others[i].update(updateMe, newMe);
      updateMe.update(newMe);
      modCount++;

      if (VERIFY_INDICES) {
        for (int i = 0; i < primary.data.length - 1; i++) {
          if (primary.comp.compare(primary.data[i], primary.data[i + 1]) > 0)
            System.out.println("PRIMARY UPDATE SORT IS SCREWED UP code=" + tableCode);
          for (int j = 0; j < others.length; j++)
            if (others[j].comp.compare(others[j].data[i], others[j].data[i + 1]) > 0)
              System.out.println("OTHER UPDATE SORT IS SCREWED UP code=" + tableCode + " otherIdx=" + j);
        }
      }
    } finally {
      releaseWriteLock();
    }

    if (logTX)
      wiz.logUpdate(updateMe, tableCode);
  }

  // 5/5/09 - Narflex - I had a temporary test case (probably due to disk fragmentation) which caused bad delays in other
  // parts of the app due because we had the lock and this was taking so long to get through a cycle (up to 10 seconds)
  // So I changed the count from 2000 to 100 which should make those delays not cause issues. It'll slowdown mass removes
  // slightly; but it'll be much better for the user experience.
  // 6/22/09 - Narflex - So this was causing new problems because of all the CPU used when check is run and the # of times
  // it would now be run with the reduced count. I also noticed it was doing the log inside the lock; which is not necessary.
  // So now it does the log outside of the lock which means it shouldn't cause UI slowdown now (since the logging was the slowest)
  // part of it all.
  void massRemove(java.util.Set killUs, boolean logXct)
  {
    // 7/11/12 - Narflex - I'm concerned that there's an issue where a client connects during the middle of a mass remove
    // and then we won't propogate all the transactions here correctly....so let's just do it the same way all the time.
    // 7/12/12 BFN(codefu): If this function is ever changed such that remove() is not called on each table item, you will need
    // to fix the subclasses of this that use Luecene (see ShowTable, etc all)
    java.util.Iterator walker = killUs.iterator();
    int i = 0;
    while (walker.hasNext()) {
      remove((DBObject) walker.next(), logXct);
      i++;
      // This was 300...but we changed it to 50 because with the new async transactions the clients may end
      // up getting far behind the server since the server will process this faster..so we need to give them more
      // time to catch up here.
      if (i % 25 == 0 || wiz.getMaxPendingClientXcts() > 8) {
        wiz.mpause();
        wiz.compressDBIfNeeded();
        wiz.waitUntilDBClientSyncsComplete();
      }
    }
  }

  long getModCount() {
    return modCount;
  }

  void incModCount() {
    synchronized (modCountLock) {
      modCount++;
    }
  }

  void acquireReadLock() {
    if (!wiz.isDBLoading())
      rwLock.readLock().lock();
  }

  void releaseReadLock() {
    if (!wiz.isDBLoading())
      rwLock.readLock().unlock();
  }

  void acquireWriteLock() {
    if (!wiz.isDBLoading())
      rwLock.writeLock().lock();
  }

  void releaseWriteLock() {
    if (!wiz.isDBLoading())
      rwLock.writeLock().unlock();
  }

  void setWizard(Wizard inWiz) {
    wiz = inWiz;
  }

  byte tableCode;
  Index primary;
  Index[] others;
  int num;
  private final Object modCountLock = new Object();
  long modCount = 0;
  private ReentrantReadWriteLock rwLock;
  private Wizard wiz;
}
