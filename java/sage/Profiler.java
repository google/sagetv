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

public class Profiler
{
  static final long MILLIS_PER_HOUR = 60*60000L;

  public static boolean isMustSee(Airing air)
  {
    Show s = air.getShow();
    if ((s == null) || BigBrother.isWatched(air, true))
      return false;

    if (!air.isViewable())
      return false;

    return Carny.getInstance().isMustSee(air);
  }

  // returns an int[2] for start, and end slot inclusively. This may wrap
  // around NUM_SLOTS
  static int[] determineSlots(Airing a)
  {
    java.util.Calendar cal = new java.util.GregorianCalendar();
    cal.setTimeInMillis(a.time);
    int startIdx = 24*(cal.get(java.util.Calendar.DAY_OF_WEEK) -
        java.util.Calendar.SUNDAY);
    startIdx += cal.get(java.util.Calendar.HOUR_OF_DAY);
    long useThisDur = Math.min(a.getDuration(), Sage.MILLIS_PER_WEEK) - 1;
    int endIdx = startIdx + (int)(useThisDur / (60*60*1000L));
    return new int[] { startIdx, endIdx };
  }
}
