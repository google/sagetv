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

import java.util.Comparator;

/**
 * Represent a object that has a scheduled start and end time.
 */
public interface Schedulable {
  public long getSchedulingStart();

  public long getSchedulingDuration();

  public long getSchedulingEnd();

  static final Comparator<Schedulable> TIME_DURATION_COMPARATOR = new java.util.Comparator<Schedulable>() {
    public int compare(Schedulable o1, Schedulable o2) {
      if (o1 == o2)
        return 0;
      else if (o1 == null)
        return 1;
      else if (o2 == null)
        return -1;

      Schedulable a1 = o1;
      Schedulable a2 = o2;
      long timeDiff = a1.getSchedulingStart() - a2.getSchedulingStart();
      if (timeDiff == 0) {
        timeDiff = a1.getSchedulingDuration() - a2.getSchedulingDuration();
        // NOTE: Trying to sort by largest duration first.
        return (timeDiff == 0) ? 0 : (timeDiff < 0) ? 1 : -1;
      } else {
        return (timeDiff < 0) ? -1 : 1;
      }
    }
  };
}
