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

import java.text.ParseException;
import java.util.Calendar;
import java.util.SimpleTimeZone;

/**
 * Parse IEEE 1003.1 POSIX timezone into SimpleTimeZone, so long as it follows these examples:
 *	"EST5EDT,M3.2.0/2,M11.1.0/2",
 *	"CST6CDT,M3.2.0/2:00,M11.1.0/2:00",
 *	"CET-1CEST,M3.5.0/2,M10.5.0/3",
 * Specifically: stdoffsetdst[offset],start[/time],end[/time]
 * See http://www.gnu.org/software/libc/manual/html_node/TZ-Variable.html
 * NOTE(codefu): We're only allowing Mm.n.d formatted start/end dates
 *
 * @author codefu@google.com (John McDole)
 */
public class TimeZoneParse {
  static final int DEFAULT_CHANGEOVER_TIME = 7200000; // Default: 02:00:00
  boolean validParse = false;
  boolean noDST = false;
  String data;
  int currentOffset;
  int maxOffset;

  String stdName;
  int stdOffsetMS;

  String dstName;
  int dstOffsetMS;

  int startDates[] = {1, 1, 0};
  int startTime = DEFAULT_CHANGEOVER_TIME;
  int endDates[] = {1, 1, 0};
  int endTime = DEFAULT_CHANGEOVER_TIME;

  TimeZoneParse(String data) {
    this.data = data;
    maxOffset = data.length();
  }

  TimeZoneParse parse() throws ParseException {
    stdName = parseTimezoneName();
    if (stdName == null)
      throw new java.text.ParseException("invalid standard timezone", currentOffset);

    stdOffsetMS = parseTimezoneOffset(false);

    dstName = parseTimezoneName();
    if (dstName == null) {
      if (currentOffset < maxOffset)
        throw new java.text.ParseException("invalid data encountered", currentOffset);
      noDST = true;
      validParse = true;
      return this;
    }

    int tmpOffset = currentOffset;
    dstOffsetMS = parseTimezoneOffset(true);
    if (tmpOffset == currentOffset) {
      dstOffsetMS = stdOffsetMS - 3600000; // default: 1 hour ahead
    }

    if (currentOffset >= maxOffset || data.charAt(currentOffset++) != ',') {
      throw new java.text.ParseException("not enough information after dst name", currentOffset);
    }

    parseDates(startDates);
    if (currentOffset < maxOffset && data.charAt(currentOffset++) == '/') {
      startTime = parseTime(false);
    }

    if (currentOffset >= maxOffset || data.charAt(currentOffset++) != ',')
      throw new java.text.ParseException("missing end dst boundary date", currentOffset);
    parseDates(endDates);
    if (currentOffset < maxOffset && data.charAt(currentOffset++) == '/') {
      endTime = parseTime(false);
    }
    validParse = true;
    return this;
  }

  /**
   * Parse "Mm.w.d", representing the [d]ay of week (0-6, Sunday-Saturday) on the [w]eek of the
   * month (0-5, 5="last [d]ay of month), for the [m]onth (1-12).
   *
   * @param date array storing [m,w,d]
   * @throws ParseException
   */
  private void parseDates(int[] date) throws ParseException {
    if (currentOffset >= maxOffset || data.charAt(currentOffset++) != 'M')
      throw new java.text.ParseException("invalid dst boundary date", currentOffset);
    int tmpOffset = currentOffset;
    date[0] = parseNumber();
    if (tmpOffset == currentOffset || date[0] < 1 || date[0] > 12)
      throw new java.text.ParseException("invalid dst month", currentOffset);
    if (currentOffset >= maxOffset || data.charAt(currentOffset++) != '.')
      throw new java.text.ParseException("invalid dst boundary char", currentOffset);
    tmpOffset = currentOffset;
    date[1] = parseNumber();
    if (tmpOffset == currentOffset || date[1] < 1 || date[1] > 5)
      throw new java.text.ParseException("invalid dst boundary week", currentOffset);
    if (currentOffset >= maxOffset || data.charAt(currentOffset++) != '.')
      throw new java.text.ParseException("invalid dst boundary char", currentOffset);
    tmpOffset = currentOffset;
    date[2] = parseNumber();
    if (tmpOffset == currentOffset || date[2] < 0 || date[2] > 6)
      throw new java.text.ParseException("invalid dst boundary day", currentOffset);
  }

  /**
   * Parse the name of the timezone; it must be 3 characters long, not start with a :, have embedded
   * digits, commas, or a +/-. There is no space between the name and the offset.
   * @return timezone name
   */
  private String parseTimezoneName() {
    int follow = currentOffset;
    if (follow+3 > maxOffset) return null;
    if (data.charAt(currentOffset) == ':') {
      return null;
    }

    int nameLength = follow+3;
    while (follow < nameLength) {
      char look = data.charAt(follow);
      if (Character.isLetter(look)) {
        if (look == '-' || look == '+' || look == ',') {
          break;
        }
      } else {
        break;
      }
      follow++;
    }
    if (follow != nameLength) return null;
    String ret = data.substring(currentOffset, follow);
    currentOffset = follow;
    return ret;
  }

  /*
   * Simply parse some digits.
   */
  private int parseNumber() {
    int follow = currentOffset;
    int value = 0;
    while (follow < maxOffset) {
      char look = data.charAt(follow);
      if(look >= '0' && look <= '9') {
        value = value * 10 + (look - '0');
      } else {
        break;
      }
      follow++;
    }
    currentOffset = follow;
    return value;
  }

  /**
   * Parse a time offset, which can be in the format of [+|-]hh[:mm[:ss]].  This is the time
   * added to local time to get UTC.
   * @return prased time
   * @throws ParseException
   */
  private int parseTimezoneOffset(boolean isOptional) throws ParseException {
    if (currentOffset >= maxOffset) {
      if(!isOptional)
        throw new java.text.ParseException("Missing non-optional timezone offset", currentOffset);
      return 0;
    }
    char optionalSign = data.charAt(currentOffset);
    boolean negate = false;
    if (optionalSign == '+' || optionalSign == '-') {
      if (optionalSign == '-') negate = true;
      isOptional = false; // If the + is here, we need a number.
      currentOffset++;
    }

    int ret = parseTime(isOptional);
    return (negate == true) ? -ret : ret;
  }

  /**
   * Parse the [hh[:mm[:ss]]]
   * @throws ParseException if time is not optional and missing or if it doesn't match the pattern
   */
  private int parseTime(boolean isOptional) throws ParseException {
    int sec = 0;
    int min = 0;
    int tmpOffset = currentOffset;
    int hour = parseNumber();
    if (tmpOffset == currentOffset) {
      if(!isOptional) {
        throw new java.text.ParseException("Missing non-optional hours", currentOffset);
      }
      return 0;
    }
    if (hour < 0 || hour > 23) throw new java.text.ParseException("Invalid hour", currentOffset);
    if (currentOffset < maxOffset && data.charAt(currentOffset) == ':') {
      tmpOffset = ++currentOffset;
      min = parseNumber();
      if (tmpOffset == currentOffset) {
        throw new java.text.ParseException("Missing non-optional minutes", currentOffset);
      }
      if (min < 0 || min > 59) throw new java.text.ParseException("Invalid minute", currentOffset);
      if (currentOffset < maxOffset && data.charAt(currentOffset) == ':') {
        tmpOffset = ++currentOffset;
        sec = parseNumber();
        if (tmpOffset == currentOffset) {
          throw new java.text.ParseException("Missing non-optional seconds", currentOffset);
        }
        if (sec < 0 || sec > 59)
          throw new java.text.ParseException("Invalid second", currentOffset);
      }
    }
    int ret = ((hour * 3600) + (min * 60) + sec) * 1000;
    return ret;
  }

  /* POSIX day/month mappings to Java Calendar */
  static final int days[] = {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};
  static final int months[] = {-1, Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH,
    Calendar.APRIL, Calendar.MAY, Calendar.JUNE, Calendar.JULY, Calendar.AUGUST,
    Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER, Calendar.DECEMBER};

  /**
   * Convert the parsed POSIX TZ string to SimpleTimeZone
   * @return a converted timezone or null.
   */
  SimpleTimeZone convertToTz() {
    if (validParse) {
      // "Last Day" default; else negate
      int startDayOfWeek = days[startDates[2]];
      int startDay;
      if (startDates[1] != 5) {
        startDay = (startDates[1] - 1) * 7 + startDayOfWeek;
        startDayOfWeek = -startDayOfWeek;
      } else {
        startDay = -1;
      }
      // "Last Day" default; else negate
      int endDayOfWeek = days[endDates[2]];
      int endDay;
      if (endDates[1] != 5) {
        endDay = (endDates[1] - 1) * 7 + endDayOfWeek;
        endDayOfWeek = -endDay;
      } else {
        endDay = -1;
      }
      SimpleTimeZone tz;
      try {
        if(noDST) {
          tz = new SimpleTimeZone(-stdOffsetMS, stdName);
        } else {
          tz = new SimpleTimeZone(-stdOffsetMS, stdName,
              /* start */months[startDates[0]], startDay, startDayOfWeek, startTime,
              /* end */months[endDates[0]], endDay, endDayOfWeek, endTime, (stdOffsetMS - dstOffsetMS));
        }
      } catch (IllegalArgumentException e) {
        return null;
      }
      return tz;
    }
    return null;
  }
}
