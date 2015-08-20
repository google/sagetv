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

public class ZClock extends ZLabel
{
  private static final long MILLIS_PER_DAY = 1000L*60*60*24;
  private static java.text.DateFormat TIME_ONLY_FORMAT;
  private static java.text.DateFormat DATE_ONLY_FORMAT;
  private static java.text.DateFormat CONCISE_TIME_DATE_FORMAT;
  private static java.util.Locale cacheLocale;
  public static final int TIME_ONLY = 1;
  public static final int DATE_ONLY = 2;
  public static final int DATE_TIME = 3;
  public static final int TIME_DATE = 4;
  public static final int SPECIAL_TIME_ONLY = 5;
  public static String getSpecialTimeString(java.util.Date d)
  {
    checkLocaleCache();
    java.util.Calendar cal = new java.util.GregorianCalendar();
    int currDay = cal.get(java.util.Calendar.DAY_OF_YEAR);
    cal.setTime(d);
    int myDay = cal.get(java.util.Calendar.DAY_OF_YEAR);
    if (currDay == myDay) return TIME_ONLY_FORMAT.format(d);
    else return CONCISE_TIME_DATE_FORMAT.format(d);
  }
  static
  {
    checkLocaleCache();
  }
  private static void checkLocaleCache()
  {
    if (cacheLocale == Sage.userLocale) return;
    TIME_ONLY_FORMAT = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Sage.userLocale);
    if (TIME_ONLY_FORMAT instanceof java.text.SimpleDateFormat)
    {
      String pat = ((java.text.SimpleDateFormat) TIME_ONLY_FORMAT).toPattern();
      if (pat.endsWith(" a"))
      {
        TIME_ONLY_FORMAT = new java.text.SimpleDateFormat(pat.substring(0, pat.length() - 2) + "a");
      }
    }
    DATE_ONLY_FORMAT = new java.text.SimpleDateFormat("EEE M/d", Sage.userLocale);
    CONCISE_TIME_DATE_FORMAT = new java.text.SimpleDateFormat("EEE M/d h:mma", Sage.userLocale);
    try
    {
      java.text.DateFormat longFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL,
          Sage.userLocale);
      // To get it into the format we want we need to convert the weekday & months to
      // using their short strings. AND we need to remove the year and any conjoining punctuation for it.
      // NOTE: Localized Patterns don't use the same characters for year, date, etc. as we do in the US
      String pat = ((java.text.SimpleDateFormat)longFormat).toPattern();
      String orgPat = pat;
      // Convert weekday string
      pat = java.util.regex.Pattern.compile("E+").matcher(pat).replaceAll("EEE");

      // Convert month string
      pat = java.util.regex.Pattern.compile("M+").matcher(pat).replaceAll("MMM");

      // Remove the year
      pat = java.util.regex.Pattern.compile("[\\W&&[^\\']]*y+\\W*").matcher(pat).replaceAll("");

      // If there's quoted text at the end of the string remove it.
      if (pat.charAt(pat.length() - 1) == '\'')
      {
        int nextQuote = pat.lastIndexOf('\'', pat.length() - 2);
        if (nextQuote != -1)
        {
          pat = pat.substring(0, nextQuote).trim();
        }
      }
      if (Sage.DBG) System.out.println("DatePat=" + pat + " orgPat=" + orgPat);
      DATE_ONLY_FORMAT = new java.text.SimpleDateFormat(pat, Sage.userLocale);

      CONCISE_TIME_DATE_FORMAT = new java.text.SimpleDateFormat(pat + " " +
          ((java.text.SimpleDateFormat)TIME_ONLY_FORMAT).toPattern(), Sage.userLocale);
    }
    catch (Exception e)
    {
      System.out.println("DateFormat init error:" + e);
      e.printStackTrace();
    }
    cacheLocale = Sage.userLocale;
  }

  public ZClock(ZRoot inReality, int inFormat)
  {
    this(inReality, inFormat, null, null);
  }
  public ZClock(ZRoot inReality, int inFormat,
      java.util.Date inTime)
  {
    this(inReality, inFormat, inTime, null);
  }
  public ZClock(ZRoot inReality, int inFormat, MetaFont clockFont)
  {
    this(inReality, inFormat, null, clockFont);
  }
  public ZClock(ZRoot inReality, int inFormat,
      java.util.Date inTime, MetaFont clockFont)
  {
    super(inReality, null, clockFont);
    time = inTime;
    format = inFormat;
    refreshClock();
  }
  public ZClock(ZRoot inReality, java.text.DateFormat inCustomFormat,
      java.util.Date inTime, MetaFont clockFont)
  {
    super(inReality, null, clockFont);
    time = inTime;
    customFormat = inCustomFormat;
    refreshClock();
  }

  public void setTime(java.util.Date inTime)
  {
    time = inTime;
    refreshClock();
  }

  public long getTimeInMillis()
  {
    return (time == null) ? Sage.time() : time.getTime();
  }

  protected void doLayoutNow()
  {
    if (!registeredAnimation)
    {
      registeredAnimation = true;
      reality.registerAnimation(this, Sage.eventTime() + 60000 - (Sage.time() % 60000));
    }
    refreshClock();
    super.doLayoutNow();
  }

  public void animationCallback(long animationTime)
  {
    if (!(parent instanceof ZPseudoComp) || ((ZPseudoComp)parent).passesUpwardConditional())
    {
      if (textOverride && parent instanceof ZPseudoComp)
      {
        ((ZPseudoComp)parent).appendToDirty(true);
        ((ZPseudoComp)parent).evaluateTree(false, true);
      }
      else
        refreshClock();
    }
    reality.registerAnimation(this, Sage.eventTime() + 60000 - (Sage.time() % 60000));
  }

  public boolean setText(String s)
  {
    textOverride = true;
    return super.setText(s);
  }

  private void refreshClock()
  {
    if (textOverride) return;
    checkLocaleCache();
    java.util.Date myTime = (time == null) ? new java.util.Date(Sage.time()) : time;
    if (customFormat != null)
      super.setText(customFormat.format(myTime));
    else if (format == TIME_ONLY)
      super.setText(TIME_ONLY_FORMAT.format(myTime));
    else if (format == SPECIAL_TIME_ONLY)
      super.setText(getSpecialTimeString(myTime));
    else if (format == DATE_ONLY)
      super.setText(DATE_ONLY_FORMAT.format(myTime));
    else if (format == DATE_TIME)
      super.setText(DATE_ONLY_FORMAT.format(myTime) + " " + TIME_ONLY_FORMAT.format(myTime));
    else if (format == TIME_DATE)
      super.setText(TIME_ONLY_FORMAT.format(myTime) + " " + DATE_ONLY_FORMAT.format(myTime));
    else
      super.setText(CONCISE_TIME_DATE_FORMAT.format(myTime));
  }

  private int format;
  protected java.util.Date time;
  private java.text.DateFormat customFormat;
  private boolean textOverride;
}
