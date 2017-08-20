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

import sage.media.format.ContainerFormat;
import sage.media.format.MediaFormat;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/*
 * To deal with time-based recording, we use FakeAiring again here. After a ManualRecord
 * is done that's time-based, it then changes its airingID to be the fileID of the
 * file it created. This flows through into the Wizard when it maps airingIDs to fileIDs
 * automagically in getAiringForID. FakeAiring is working out pretty nice so far.
 */
public final class ManualRecord extends DBObject
{
  public static final int FILENAME = 0;
  public static final int ENCODING_PROFILE = 1;

  public static final int RECUR_NONE = 0;
  public static final int RECUR_HOURLY = 1;
  public static final int RECUR_DAILY = 2;
  public static final int RECUR_WEEKLY = 3;
  public static final int RECUR_CONTINUOUS = 0x200;
  public static final int RECUR_SUN_MASK = 0x04;
  public static final int RECUR_MON_MASK = 0x08;
  public static final int RECUR_TUE_MASK = 0x10;
  public static final int RECUR_WED_MASK = 0x20;
  public static final int RECUR_THU_MASK = 0x40;
  public static final int RECUR_FRI_MASK = 0x80;
  public static final int RECUR_SAT_MASK = 0x100;

  ManualRecord(int inID)
  {
    super(inID);
    extraInfo = Pooler.EMPTY_STRING_ARRAY;
    weakAirings = new int[0];
    setMediaMask(MEDIA_MASK_TV);
  }

  ManualRecord(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    setMediaMask(MEDIA_MASK_TV);
    int oldAiringID = 0;
    if (ver < 0x23)
      oldAiringID = readID(in, idMap); // airingID
    startTime = in.readLong();
    duration = in.readLong();
    providerID = in.readLong();
    stationID = in.readInt();
    extraInfo = new String[in.readInt()];
    for (int i = 0; i < extraInfo.length; i++)
      extraInfo[i] = in.readUTF();
    if (extraInfo.length > 1 && extraInfo[1].length() > 0)
      extraInfo[1] = MMC.cleanQualityName(extraInfo[1]);

    recur = in.readInt();
    weakAirings = new int[in.readInt()];
    for (int i = 0; i < weakAirings.length; i++)
      weakAirings[i] = readID(in, idMap);
    infoAiringID = readID(in, idMap);
    if (infoAiringID == 0)
      infoAiringID = oldAiringID;
    if (ver > 0x44)
    {
      buildMRProps(in.readUTF());
    }
  }

  private void buildMRProps(String str)
  {
    if (str != null && str.length() > 0)
    {
      if (mrProps == null)
        mrProps = new Properties();
      else
        mrProps.clear();
      int currNameStart = 0;
      int currValueStart = -1;
      for (int i = 0; i < str.length(); i++)
      {
        char c = str.charAt(i);
        if (c == '\\')
        {
          // Escaped character, so skip the next one
          i++;
          continue;
        }
        else if (c == '=')
        {
          // We found the name=value delimeter, set the value start position
          currValueStart = i + 1;
        }
        else if (c == ';' && currValueStart != -1)
        {
          // We're at the end of the name value pair, get their values!
          String name = ContainerFormat.unescapeString(str.substring(currNameStart, currValueStart - 1));
          String value = ContainerFormat.unescapeString(str.substring(currValueStart, i));
          currNameStart = i + 1;
          currValueStart = -1;
          mrProps.setProperty(name, value);
        }
      }
    }
    else if (mrProps != null)
      mrProps.clear();
  }


  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    out.writeLong(startTime);
    out.writeLong(duration);
    out.writeLong(providerID);
    out.writeInt(stationID);
    out.writeInt(extraInfo.length);
    for (int i = 0; i < extraInfo.length; i++)
      out.writeUTF(extraInfo[i] == null ? "" : extraInfo[i]);
    out.writeInt(recur);
    out.writeInt(weakAirings.length);
    for (int i = 0; i < weakAirings.length; i++)
      out.writeInt(weakAirings[i]);
    out.writeInt(infoAiringID);
    if (mrProps == null)
      out.writeUTF("");
    else
    {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<Object, Object> ent : mrProps.entrySet())
      {
        sb.append(MediaFormat.escapeString(ent.getKey().toString()));
        sb.append('=');
        sb.append(MediaFormat.escapeString(ent.getValue().toString()));
        sb.append(';');
      }
      out.writeUTF(sb.toString());
    }
  }

  public int getStationID()
  {
    return stationID;
  }

  public Airing getSchedulingAiring()
  {
    if (myAiring == null)
    {
      myAiring = new FakeAiring(id);
    }
    return myAiring;
  }

  public Airing getContentAiring()
  {
    if (infoAiringID != 0)
    {
      Airing infAir = Wizard.getInstance().getAiringForID(infoAiringID, false, false);
      if (infAir != null)
        return infAir;
    }
    return getSchedulingAiring();
  }

  boolean validate()
  {
    // The Scheduler will remove any expired manual records itself
    return true;//((getEndTime() >= Sage.time()) ||
    //Wizard.getInstance().getFileForAiring(getContentAiring()) != null);
  }

  void update(DBObject fromMe)
  {
    ManualRecord mr = (ManualRecord) fromMe;
    startTime = mr.startTime;
    duration = mr.duration;
    providerID = mr.providerID;
    stationID = mr.stationID;
    extraInfo = mr.extraInfo.clone();
    recur = mr.recur;
    weakAirings = mr.weakAirings.clone();
    infoAiringID = mr.infoAiringID;
    if (myAiring != null)
      getSchedulingAiring();
    if (mr.mrProps != null)
      mrProps = (Properties) mr.mrProps.clone();
    else
      mrProps = null;
    super.update(fromMe);
  }

  public String getRecordingQuality()
  {
    if (extraInfo.length > ENCODING_PROFILE)
      return extraInfo[ENCODING_PROFILE];
    else
      return null;
  }

  public void setRecordingQuality(String quality)
  {
    Wizard wiz = Wizard.getInstance();
    try {
      wiz.acquireWriteLock(Wizard.MANUAL_CODE);
      if (extraInfo.length > ENCODING_PROFILE)
        extraInfo[ENCODING_PROFILE] = quality;
      else
      {
        String[] newExtraInfo = new String[ENCODING_PROFILE + 1];
        System.arraycopy(extraInfo, 0, newExtraInfo, 0, extraInfo.length);
        extraInfo = newExtraInfo;
        extraInfo[ENCODING_PROFILE] = quality;
      }
      wiz.logUpdate(this, Wizard.MANUAL_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.MANUAL_CODE);
    }
  }

  public String getName()
  {
    if (extraInfo.length > FILENAME && extraInfo[FILENAME] != null && extraInfo[FILENAME].length() > 0)
      return extraInfo[FILENAME];
    else
      return "";
  }

  public void setName(String s)
  {
    Wizard wiz = Wizard.getInstance();
    try {
      wiz.acquireWriteLock(Wizard.MANUAL_CODE);
      if (extraInfo.length > FILENAME)
        extraInfo[FILENAME] = s;
      else
      {
        String[] newExtraInfo = new String[FILENAME + 1];
        System.arraycopy(extraInfo, 0, newExtraInfo, 0, extraInfo.length);
        extraInfo = newExtraInfo;
        extraInfo[FILENAME] = s;
      }
      wiz.logUpdate(this, Wizard.MANUAL_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.MANUAL_CODE);
    }
  }

  public synchronized void bully(Airing weaker)
  {
    if (Sage.DBG) System.out.println(this + " bullied " + weaker);
    for (int i = 0; i < weakAirings.length; i++)
      if (weakAirings[i] == weaker.id)
        return;
    int[] newWeaks = new int[weakAirings.length + 1];
    System.arraycopy(weakAirings, 0, newWeaks, 0, weakAirings.length);
    newWeaks[weakAirings.length] = weaker.id;
    Wizard wiz = Wizard.getInstance();
    try {
      wiz.acquireWriteLock(Wizard.MANUAL_CODE);
      weakAirings = newWeaks;
      wiz.logUpdate(this, Wizard.MANUAL_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.MANUAL_CODE);
    }
  }

  synchronized boolean isStronger(Airing testMe)
  {
    //if (Sage.DBG) System.out.println(this + " isStronger(" + testMe + ")");
    // timed record overlaps with the base airing are stronger also,
    // because its a user requested adjustment on something they already specified
    if (testMe.id == infoAiringID)
      return true;
    for (int i = 0; i < weakAirings.length; i++)
      if (weakAirings[i] == testMe.id)
        return true;
    //if (Sage.DBG) System.out.println("stronger returned false");
    return false;
  }

  public long getStartTime() { return startTime; }
  public long getEndTime() { return startTime + duration; }
  public long getDuration() { return duration; }

  public String toString()
  {
    return "ManualRecord[" + getContentAiring() + " time=" + Sage.df(startTime) + " dur=" + Sage.durFormat(duration) + "]";
  }

  public boolean doesOverlap(long testStartTime, long testEndTime)
  {
    return (testEndTime > startTime) && (testStartTime < getEndTime());
  }

  private static boolean doesMaskHaveDay(int mask, int day)
  {
    if (day == Calendar.SUNDAY)
      return (mask & RECUR_SUN_MASK) != 0;
    else if (day == Calendar.MONDAY)
      return (mask & RECUR_MON_MASK) != 0;
    else if (day == Calendar.TUESDAY)
      return (mask & RECUR_TUE_MASK) != 0;
    else if (day == Calendar.WEDNESDAY)
      return (mask & RECUR_WED_MASK) != 0;
    else if (day == Calendar.THURSDAY)
      return (mask & RECUR_THU_MASK) != 0;
    else if (day == Calendar.FRIDAY)
      return (mask & RECUR_FRI_MASK) != 0;
    else if (day == Calendar.SATURDAY)
      return (mask & RECUR_SAT_MASK) != 0;
    else return false;
  }

  public boolean doRecurrencesOverlap(long testStartTime, long testDuration, int testRecur)
  {
    if (testRecur == 0 && recur == 0)
    {
      if (startTime <= testStartTime)
        return startTime + duration > testStartTime;
        else
          return testStartTime + testDuration > startTime;
    }
    else
    {
      long myStartTime = startTime;
      if (testRecur == RECUR_CONTINUOUS || recur == RECUR_CONTINUOUS)
        return true;
      else if (testRecur == RECUR_HOURLY || recur == RECUR_HOURLY)
      {
        // Modulus on an hour
        myStartTime %= Sage.MILLIS_PER_HR;
        testStartTime %= Sage.MILLIS_PER_HR;
        if (myStartTime <= testStartTime)
          return (myStartTime + duration > testStartTime) ||
              (testStartTime + testDuration > myStartTime + Sage.MILLIS_PER_HR);
        else
          return (testStartTime + testDuration > myStartTime) ||
              (myStartTime + duration > testStartTime + Sage.MILLIS_PER_HR);
      }
      else if (testRecur == RECUR_DAILY || recur == RECUR_DAILY)
      {
        // Modulus on a day
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(myStartTime);
        myStartTime = cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (myStartTime % Sage.MILLIS_PER_HR);
        cal.setTimeInMillis(testStartTime);
        testStartTime = cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (testStartTime % Sage.MILLIS_PER_HR);
        if (myStartTime <= testStartTime)
          return (myStartTime + duration > testStartTime) ||
              (testStartTime + testDuration > myStartTime + Sage.MILLIS_PER_DAY);
        else
          return (testStartTime + testDuration > myStartTime) ||
              (myStartTime + duration > testStartTime + Sage.MILLIS_PER_DAY);
      }
      else //if (testRecur == RECUR_WEEKLY || recur == RECUR_WEEKLY || testRecur == RECUR_CUSTOM_WEEKLY ||
        //recur == RECUR_CUSTOM_WEEKLY)
      {
        // Modulus on a week for each days mask
        if (testRecur == RECUR_WEEKLY && recur == RECUR_WEEKLY)
        {
          Calendar cal = new GregorianCalendar();
          cal.setTimeInMillis(myStartTime);
          myStartTime = cal.get(Calendar.DAY_OF_WEEK) * Sage.MILLIS_PER_DAY + cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (myStartTime % Sage.MILLIS_PER_HR);
          cal.setTimeInMillis(testStartTime);
          testStartTime = cal.get(Calendar.DAY_OF_WEEK) * Sage.MILLIS_PER_DAY + cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (testStartTime % Sage.MILLIS_PER_HR);
          if (myStartTime <= testStartTime)
            return (myStartTime + duration > testStartTime) ||
                (testStartTime + testDuration > myStartTime + Sage.MILLIS_PER_WEEK);
          else
            return (testStartTime + testDuration > myStartTime) ||
                (myStartTime + duration > testStartTime + Sage.MILLIS_PER_WEEK);
        }

        if (testRecur == RECUR_WEEKLY)
        {
          Calendar cal = new GregorianCalendar();
          cal.setTimeInMillis(testStartTime);
          int recurDay = cal.get(Calendar.DAY_OF_WEEK);
          if (!doesMaskHaveDay(recur, recurDay))
            return false;
        }
        else if (recur == RECUR_WEEKLY)
        {
          Calendar cal = new GregorianCalendar();
          cal.setTimeInMillis(myStartTime);
          int recurDay = cal.get(Calendar.DAY_OF_WEEK);
          if (!doesMaskHaveDay(testRecur, recurDay))
            return false;
        }
        else
        {
          boolean checkIt = false;
          for (int i = Calendar.SUNDAY; i <= Calendar.SATURDAY; i++)
          {
            if (doesMaskHaveDay(testRecur, i) && doesMaskHaveDay(recur, i))
            {
              checkIt = true;
              break;
            }
          }
          if (!checkIt)
            return false;
        }

        // It comes down to modulus on a day if they repeat on the
        // same day of the week
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(myStartTime);
        myStartTime = cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (myStartTime % Sage.MILLIS_PER_HR);
        cal.setTimeInMillis(testStartTime);
        testStartTime = cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (testStartTime % Sage.MILLIS_PER_HR);
        if (myStartTime <= testStartTime)
          return (myStartTime + duration > testStartTime) ||
              (testStartTime + testDuration > myStartTime + Sage.MILLIS_PER_DAY);
        else
          return (testStartTime + testDuration > myStartTime) ||
              (myStartTime + duration > testStartTime + Sage.MILLIS_PER_DAY);
      }
    }
  }

  public boolean isSameRecurrence(long testStartTime)
  {
    if (recur == 0)
    {
      return (startTime == testStartTime);
    }
    else
    {
      long myStartTime = startTime;
      if (recur == RECUR_CONTINUOUS)
      {
        long x = Math.abs(testStartTime - startTime) / duration;
        return (duration * x) == (Math.abs(testStartTime - startTime));
      }
      else if (recur == RECUR_HOURLY)
      {
        // Modulus on an hour
        myStartTime %= Sage.MILLIS_PER_HR;
        testStartTime %= Sage.MILLIS_PER_HR;
        return (myStartTime == testStartTime);
      }
      else if (recur == RECUR_DAILY)
      {
        // Modulus on a day
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(myStartTime);
        myStartTime = cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (myStartTime % Sage.MILLIS_PER_HR);
        cal.setTimeInMillis(testStartTime);
        testStartTime = cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (testStartTime % Sage.MILLIS_PER_HR);
        return (myStartTime == testStartTime);
      }
      else
      {
        // Modulus on a week for each days mask
        if (recur == RECUR_WEEKLY)
        {
          Calendar cal = new GregorianCalendar();
          cal.setTimeInMillis(myStartTime);
          myStartTime = cal.get(Calendar.DAY_OF_WEEK) * Sage.MILLIS_PER_DAY + cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (myStartTime % Sage.MILLIS_PER_HR);
          cal.setTimeInMillis(testStartTime);
          testStartTime = cal.get(Calendar.DAY_OF_WEEK) * Sage.MILLIS_PER_DAY + cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (testStartTime % Sage.MILLIS_PER_HR);
          return (myStartTime == testStartTime);
        }

        boolean checkIt = false;
        for (int i = Calendar.SUNDAY; i <= Calendar.SATURDAY; i++)
        {
          if (doesMaskHaveDay(recur, i))
          {
            checkIt = true;
            break;
          }
        }
        if (!checkIt)
          return false;

        // It comes down to modulus on a day if they repeat on the
        // same day of the week
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(myStartTime);
        myStartTime = cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (myStartTime % Sage.MILLIS_PER_HR);
        cal.setTimeInMillis(testStartTime);
        testStartTime = cal.get(Calendar.HOUR_OF_DAY) * Sage.MILLIS_PER_HR + (testStartTime % Sage.MILLIS_PER_HR);
        return (myStartTime == testStartTime);
      }
    }
  }
  // This is used purely for display purposes
  public static String getRecurrenceName(int recurCode)
  {
    if (recurCode == RECUR_NONE)
      return Sage.rez("Once");
    if (recurCode == RECUR_DAILY)
      return Sage.rez("Daily");
    if (recurCode == RECUR_WEEKLY)
      return Sage.rez("Weekly");
    if (recurCode == RECUR_CONTINUOUS)
      return Sage.rez("Continuous");
    String[] shortDows = new DateFormatSymbols(Sage.userLocale).getShortWeekdays();
    StringBuilder sb = new StringBuilder();
    if ((recurCode & RECUR_SUN_MASK) != 0) sb.append(shortDows[Calendar.SUNDAY]);
    if ((recurCode & RECUR_MON_MASK) != 0) sb.append(shortDows[Calendar.MONDAY]);
    if ((recurCode & RECUR_TUE_MASK) != 0) sb.append(shortDows[Calendar.TUESDAY]);
    if ((recurCode & RECUR_WED_MASK) != 0) sb.append(shortDows[Calendar.WEDNESDAY]);
    if ((recurCode & RECUR_THU_MASK) != 0) sb.append(shortDows[Calendar.THURSDAY]);
    if ((recurCode & RECUR_FRI_MASK) != 0) sb.append(shortDows[Calendar.FRIDAY]);
    if ((recurCode & RECUR_SAT_MASK) != 0) sb.append(shortDows[Calendar.SATURDAY]);
    return sb.toString();
  }
  public static int getRecurrenceCodeForName(String recurName)
  {
    if ("Once".equals(recurName) || Sage.rez("Once").equals(recurName)) return RECUR_NONE;
    if ("Daily".equals(recurName) || Sage.rez("Daily").equals(recurName)) return RECUR_DAILY;
    if ("Weekly".equals(recurName) || Sage.rez("Weekly").equals(recurName)) return RECUR_WEEKLY;
    if ("Continuous".equals(recurName) || Sage.rez("Continuous").equals(recurName)) return RECUR_CONTINUOUS;
    int mask = 0;
    if (recurName.indexOf("Su") != -1) mask += RECUR_SUN_MASK;
    if (recurName.indexOf("Mo") != -1) mask += RECUR_MON_MASK;
    if (recurName.indexOf("Tu") != -1) mask += RECUR_TUE_MASK;
    if (recurName.indexOf("We") != -1) mask += RECUR_WED_MASK;
    if (recurName.indexOf("Th") != -1) mask += RECUR_THU_MASK;
    if (recurName.indexOf("Fr") != -1) mask += RECUR_FRI_MASK;
    if (recurName.indexOf("Sa") != -1) mask += RECUR_SAT_MASK;
    return mask;
  }

  public static long[] getRecurringStartTimes(long recurStartTime, long recurDuration, int recurCode, long overallDuration)
  {
    if (recurCode == 0)
      return new long[] { recurStartTime };
    Calendar cal = new GregorianCalendar();
    cal.setTimeInMillis(recurStartTime);
    if (recurCode == RECUR_CONTINUOUS)
    {
      while (recurStartTime < Sage.time())
        recurStartTime += recurDuration;
      while (recurStartTime > Sage.time())
        recurStartTime -= recurDuration;
      recurStartTime += recurDuration;
      long[] rv = new long[((int)(overallDuration/recurDuration)) + 1];
      for (int i = 0; i < rv.length; i++)
      {
        rv[i] = recurStartTime + i*recurDuration;
      }
      return rv;
    }
    if (recurCode == RECUR_DAILY)
    {
      while (cal.getTimeInMillis() < Sage.time())
        cal.add(Calendar.DAY_OF_MONTH, 1);//recurStartTime += Sage.MILLIS_PER_DAY;
      while (cal.getTimeInMillis() > Sage.time())
        cal.add(Calendar.DAY_OF_MONTH, -1);//recurStartTime -= Sage.MILLIS_PER_DAY;
      cal.add(Calendar.DAY_OF_MONTH, 1);//recurStartTime += Sage.MILLIS_PER_DAY;
      long[] rv = new long[((int)(overallDuration/Sage.MILLIS_PER_DAY)) + 1];
      for (int i = 0; i < rv.length; i++)
      {
        rv[i] = cal.getTimeInMillis();//recurStartTime + i*Sage.MILLIS_PER_DAY;
        cal.add(Calendar.DAY_OF_MONTH, 1);
      }
      return rv;
    }
    if (recurCode == RECUR_WEEKLY)
    {
      while (cal.getTimeInMillis() < Sage.time())
        cal.add(Calendar.DAY_OF_MONTH, 7);//recurStartTime += Sage.MILLIS_PER_WEEK;
      while (cal.getTimeInMillis() > Sage.time())
        cal.add(Calendar.DAY_OF_MONTH, -7);//recurStartTime -= Sage.MILLIS_PER_WEEK;
      cal.add(Calendar.DAY_OF_MONTH, 7);//recurStartTime += Sage.MILLIS_PER_WEEK;
      long[] rv = new long[((int)(overallDuration/Sage.MILLIS_PER_WEEK)) + 1];
      for (int i = 0; i < rv.length; i++)
      {
        rv[i] = cal.getTimeInMillis();//recurStartTime + i*Sage.MILLIS_PER_WEEK;
        cal.add(Calendar.DAY_OF_MONTH, 7);
      }
      return rv;
    }
    while (cal.getTimeInMillis() < Sage.time())
      cal.add(Calendar.DAY_OF_MONTH, 1);//recurStartTime += Sage.MILLIS_PER_DAY;
    while (cal.getTimeInMillis() > Sage.time())
      cal.add(Calendar.DAY_OF_MONTH, -1);//recurStartTime -= Sage.MILLIS_PER_DAY;
    cal.add(Calendar.DAY_OF_MONTH, 1);//recurStartTime += Sage.MILLIS_PER_DAY;
    List<Long> rv = new ArrayList<Long>();
    while (overallDuration >= 0)
    {
      overallDuration -= Sage.MILLIS_PER_DAY;
      if (doesMaskHaveDay(recurCode, cal.get(Calendar.DAY_OF_WEEK)))
        rv.add(cal.getTimeInMillis());
      cal.add(Calendar.DAY_OF_MONTH, 1);
    }
    long[] rvl = new long[rv.size()];
    for (int i = 0; i < rvl.length; i++)
      rvl[i] = rv.get(i);
    return rvl;
  }

  void clearRecurrence()
  {
    if (recur != 0)
    {
      recur = 0;
      Wizard.getInstance().logUpdate(this, Wizard.MANUAL_CODE);
    }
  }

  public int getRecurrence()
  {
    return recur;
  }

  public String getProperty(String name)
  {
    if (mrProps == null)
      return "";
    String rv = mrProps.getProperty(name);
    return (rv == null) ? "" : rv;
  }

  public void setProperty(String name, String value)
  {
    if (value == null && (mrProps == null || !mrProps.containsKey(name)))
      return;
    if (value != null && mrProps != null && value.equals(mrProps.getProperty(name)))
      return;
    Wizard wiz = Wizard.getInstance();
    try {
      wiz.acquireWriteLock(Wizard.MANUAL_CODE);
      if (value == null)
      {
        mrProps.remove(name);
      }
      else
      {
        if (mrProps == null)
          mrProps = new Properties();
        mrProps.setProperty(name, value);
      }
      wiz.logUpdate(this, Wizard.MANUAL_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.MANUAL_CODE);
    }
  }

  // This is for setting them all at once when cloning an MR for various reasons
  public void setProperties(Properties props)
  {
    if (props == null || props.isEmpty())
      return;
    Wizard wiz = Wizard.getInstance();
    try {
      wiz.acquireWriteLock(Wizard.MANUAL_CODE);
      if (mrProps == null)
        mrProps = new Properties();
      mrProps.putAll(props);
      wiz.logUpdate(this, Wizard.MANUAL_CODE);
    } finally {
      wiz.releaseWriteLock(Wizard.MANUAL_CODE);
    }
  }

  private Airing myAiring;
  long startTime;
  long duration;
  long providerID;
  int stationID;
  String[] extraInfo;
  int recur;
  int[] weakAirings;
  int infoAiringID;
  Properties mrProps;

  public static final DateFormat fileTimeFormat = new SimpleDateFormat("MMdd_HHmm");

  // The channel is secondary so that in a multi-tuner environment we
  // have a unique sorting order.
  public static final Comparator<ManualRecord> TIME_CHANNEL_COMPARATOR =
      new Comparator<ManualRecord>()
  {
    public int compare(ManualRecord a1, ManualRecord a2)
    {
      if (a1 == a2)
        return 0;
      else if (a1 == null)
        return 1;
      else if (a2 == null)
        return -1;

      return (a1.startTime == a2.startTime)
          ? (a1.providerID == a2.providerID)
              ? a1.stationID - a2.stationID
              : Long.signum(a1.providerID - a2.providerID)
          : Long.signum(a1.startTime - a2.startTime);
    }
  };

  public class FakeAiring extends Airing
  {
    public FakeAiring(int fakeID)
    {
      super(fakeID);
      setMediaMask(MEDIA_MASK_TV);
      duration = ManualRecord.this.duration;
      time = ManualRecord.this.startTime;
      providerID = ManualRecord.this.providerID;
      stationID = ManualRecord.this.stationID;
      if (ManualRecord.this.infoAiringID != 0)
      {
        Airing testAir = Wizard.getInstance().getAiringForID(ManualRecord.this.infoAiringID, false, false);
        if (testAir != null)
        {
          showID = testAir.showID;
          // Narflex - 9/15/10 - We changed this so that GetAiringStartTime and related functions would return the proper value for the FakeAiring associated
          // with an MR. This shouldn't break anything, because we should still be using the scheduling times anywhere that's relevant, or we would have found issues
          // with padded favorites and recording them.
          duration = testAir.duration;
          time = testAir.time;
        }
      }
      if (showID == 0)
        showID = Wizard.getInstance().getNoShow().id;
    }

    private String getDisplayName()
    {
      String theName = getName();
      if (theName == null || theName.trim().length() == 0)
      {
        return Sage.rez("Timed_Record") + (recur == 0 ? "" : (" " + Sage.rez("Recurs") + " " + getRecurrenceName(recur)));
      }
      else
        return theName;
    }
    public String getFullString()
    {
      StringBuilder sb;
      Airing infoAir = Wizard.getInstance().getAiringForID(infoAiringID);
      if (infoAir != null && !(infoAir instanceof FakeAiring)) // avoid infinite recursion
      {
        sb = new StringBuilder(infoAir.getFullString());
      }
      else
        sb = new StringBuilder(getDisplayName());
      sb.append('\n');
      sb.append(Sage.durFormatHrMinPretty(ManualRecord.this.duration));
      //sb.append("Record Duration: ");
      return sb.toString();
    }

    public String getPartialString()
    {
      StringBuilder sb = new StringBuilder();
      Airing infoAir = Wizard.getInstance().getAiringForID(infoAiringID);
      if (infoAir != null && infoAir.getShow() != null)
      {
        Show myShow = infoAir.getShow();
        sb.append(myShow.getTitle());
        if (myShow.getEpisodeName().length() > 0)
        {
          sb.append("-");
          sb.append(myShow.getEpisodeName());
        }
        else if (myShow.getDesc().length() > 0 &&
            myShow.getDesc().length() < 32)
        {
          sb.append("-");
          sb.append(myShow.getDesc());
        }
      }
      else
      {
        sb.append(getDisplayName());
      }
      sb.append('\n');
      Object[] formArgs = { Sage.durFormatHrMinPretty(ManualRecord.this.duration),
          (ZClock.getSpecialTimeString(new Date(ManualRecord.this.startTime)) + " - " +
              ZClock.getSpecialTimeString(new Date(ManualRecord.this.startTime + ManualRecord.this.duration))),
              (getChannel() != null ? (getChannelNum(0) + ' ' + getChannel().getName()) : getChannelNum(0)),

      };
      sb.append(Sage.rez("Airing_Title_Time_Channel", formArgs));
      return sb.toString();
    }

    public String getTitle()
    {
      if (infoAiringID != 0)
      {
        Airing infAir = Wizard.getInstance().getAiringForID(infoAiringID);
        if (infAir != null && !(infAir instanceof FakeAiring))
          return infAir.getTitle();
      }
      return getDisplayName();
    }

    public String toString()
    {
      Show myShow = Wizard.getInstance().getShowForID(showID);
      return "FA[stationID=" + stationID + (myShow == null ? (" showID=" + showID) : (" " + myShow.getTitle())) +
          " time=" + Sage.df(ManualRecord.this.startTime) + " dur=" + Sage.durFormat(ManualRecord.this.duration) + ']';
    }

    //public boolean isMustSee() { return true; }

    public ManualRecord getManualRecord() { return ManualRecord.this; }
    public long getSchedulingStart() { return ManualRecord.this.startTime; }
    public long getSchedulingDuration() { return ManualRecord.this.duration; }
    public long getSchedulingEnd() { return ManualRecord.this.startTime + ManualRecord.this.duration; }
    void setPersist(byte how){}
  }
}
