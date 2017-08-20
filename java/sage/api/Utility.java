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
package sage.api;

import sage.AWTThreadWatcher;
import sage.Catbert;
import sage.DBObject;
import sage.EPG;
import sage.IOUtils;
import sage.ImageUtils;
import sage.LinuxUtils;
import sage.MediaFile;
import sage.MetaImage;
import sage.MiniClientSageRenderer;
import sage.NativeImageAllocator;
import sage.Pooler;
import sage.PredefinedJEPFunction;
import sage.PseudoMenu;
import sage.Sage;
import sage.SageConstants;
import sage.SageRenderer;
import sage.SageTV;
import sage.SeekerSelector;
import sage.StringMatchUtils;
import sage.UIClient;
import sage.UIManager;
import sage.UserEvent;
import sage.ZPseudoComp;

/**
 * Contains miscellaneous methods useful for a variety of purposes
 */
public class Utility {
  private Utility() {}
  private static java.awt.Robot myRobot;
  private static java.awt.Robot getRobot()
  {
    if (myRobot == null)
    {
      try
      {
        myRobot = new java.awt.Robot();
      }
      catch (Exception e)
      {
        return null;
      }
      myRobot.setAutoWaitForIdle(true);
    }
    return myRobot;
  }

  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Utility", "GetSubgroup", 2, new String[]{"Grouping", "Key"})
    {
      /**
       * Gets the value for the specified key out of a map. Useful for analyzing data from a {@link Database#GroupByMethod GroupByMethod ()} call.
       * @param Grouping the map to get the value from
       * @param Key the key to use for retrieving the value
       * @return the value for the specified key in the specified map
       *
       * @declaration public Object GetSubgroup(java.util.Map Grouping, Object Key);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object theKey = stack.pop();
        java.util.Map theMap = (java.util.Map) stack.pop();
        return (theMap == null) ? null : theMap.get(theKey);
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "Keystroke", -1, new String[]{"Character", "System"})
    {
      /**
       * Executes the specified keystroke in either the SageTV event system or by emulation in the operating system
       * @param Character the keystroke to perform, can contain Ctrl, Shift, Alt and combinations thereof with the specified key name
       * @param System if true then an operating system keystroke should be emulated, if false then keep the keystroke within SageTV
       *
       * @declaration public void Keystroke(String Character, boolean System);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        PseudoMenu currUI = stack.getUIMgrSafe().getCurrUI();
        ZPseudoComp topParent = (currUI != null) ? currUI.getUI() : null;
        boolean system = false;
        if (curNumberOfParameters == 2)
          system = evalBool(stack.pop());
        final String keyStr = getString(stack);
        if (topParent != null)
        {
          if (system)
          {
            if (java.awt.EventQueue.isDispatchThread())
            {
              Pooler.execute(new Runnable()
              {
                public void run()
                {
                  java.awt.Robot robbie = getRobot();
                  if (robbie != null)
                  {
                    int[] kc = Catbert.getKeystrokeFromString(keyStr);
                    if ((kc[1] & java.awt.event.KeyEvent.SHIFT_MASK) == java.awt.event.KeyEvent.SHIFT_MASK)
                      robbie.keyPress(java.awt.event.KeyEvent.VK_SHIFT);
                    if ((kc[1] & java.awt.event.KeyEvent.CTRL_MASK) == java.awt.event.KeyEvent.CTRL_MASK)
                      robbie.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    if ((kc[1] & java.awt.event.KeyEvent.ALT_MASK) == java.awt.event.KeyEvent.ALT_MASK)
                      robbie.keyPress(java.awt.event.KeyEvent.VK_ALT);
                    if ((kc[1] & java.awt.event.KeyEvent.META_MASK) == java.awt.event.KeyEvent.META_MASK)
                      robbie.keyPress(java.awt.event.KeyEvent.VK_META);
                    robbie.keyPress(kc[0]);
                    robbie.keyRelease(kc[0]);
                    if ((kc[1] & java.awt.event.KeyEvent.META_MASK) == java.awt.event.KeyEvent.META_MASK)
                      robbie.keyRelease(java.awt.event.KeyEvent.VK_META);
                    if ((kc[1] & java.awt.event.KeyEvent.ALT_MASK) == java.awt.event.KeyEvent.ALT_MASK)
                      robbie.keyRelease(java.awt.event.KeyEvent.VK_ALT);
                    if ((kc[1] & java.awt.event.KeyEvent.CTRL_MASK) == java.awt.event.KeyEvent.CTRL_MASK)
                      robbie.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    if ((kc[1] & java.awt.event.KeyEvent.SHIFT_MASK) == java.awt.event.KeyEvent.SHIFT_MASK)
                      robbie.keyRelease(java.awt.event.KeyEvent.VK_SHIFT);
                  }
                }
              });
            }
            else
            {
              java.awt.Robot robbie = getRobot();
              if (robbie != null)
              {
                int[] kc = Catbert.getKeystrokeFromString(keyStr);
                if ((kc[1] & java.awt.event.KeyEvent.SHIFT_MASK) == java.awt.event.KeyEvent.SHIFT_MASK)
                  robbie.keyPress(java.awt.event.KeyEvent.VK_SHIFT);
                if ((kc[1] & java.awt.event.KeyEvent.CTRL_MASK) == java.awt.event.KeyEvent.CTRL_MASK)
                  robbie.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                if ((kc[1] & java.awt.event.KeyEvent.ALT_MASK) == java.awt.event.KeyEvent.ALT_MASK)
                  robbie.keyPress(java.awt.event.KeyEvent.VK_ALT);
                if ((kc[1] & java.awt.event.KeyEvent.META_MASK) == java.awt.event.KeyEvent.META_MASK)
                  robbie.keyPress(java.awt.event.KeyEvent.VK_META);
                robbie.keyPress(kc[0]);
                robbie.keyRelease(kc[0]);
                if ((kc[1] & java.awt.event.KeyEvent.META_MASK) == java.awt.event.KeyEvent.META_MASK)
                  robbie.keyRelease(java.awt.event.KeyEvent.VK_META);
                if ((kc[1] & java.awt.event.KeyEvent.ALT_MASK) == java.awt.event.KeyEvent.ALT_MASK)
                  robbie.keyRelease(java.awt.event.KeyEvent.VK_ALT);
                if ((kc[1] & java.awt.event.KeyEvent.CTRL_MASK) == java.awt.event.KeyEvent.CTRL_MASK)
                  robbie.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                if ((kc[1] & java.awt.event.KeyEvent.SHIFT_MASK) == java.awt.event.KeyEvent.SHIFT_MASK)
                  robbie.keyRelease(java.awt.event.KeyEvent.VK_SHIFT);
              }
            }
          }
          else
          {
            int[] kc = Catbert.getKeystrokeFromString(keyStr);
            stack.getUIMgrSafe().getRouter().submitUserEvent(new UserEvent(
                stack.getUIMgrSafe().getRouter().getEventCreationTime(),
                UserEvent.ANYTHING, -1, kc[0], kc[1], (keyStr.equals("Backspace") ? '\b' : (keyStr.length() > 1 ? (char)0 : keyStr.charAt(0)))));
          }
        }
        return (null);
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "Size", 1, new String[]{"Data"})
    {
      /**
       * Returns the size of the specified data.
       * @param Data the object to get the data size of
       * @return for a Collection or Map, the size of it; for an array, the length; for a string, the length, otherwise 0 is returned
       *
       * @declaration public int Size(Object Data);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object obj = stack.pop();
        int size = 0;
        if (obj == null)
          size = 0;
        else if (obj instanceof java.util.Collection)
          size = ((java.util.Collection) obj).size();
        else if (obj instanceof java.util.Map)
          size = ((java.util.Map) obj).size();
        else if (obj.getClass().isArray())
          size = java.lang.reflect.Array.getLength(obj);
        else if (obj instanceof String || obj instanceof sage.Person)
          size = obj.toString().length();
        return new Integer(size);
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "IsEmpty", 1, new String[]{"Data"})
    {
      /**
       * Returns true if the argument is null, zero, an empty string or a failed image load
       * @param Data the object to test
       * @return true if the argument is null, zero, an empty string or a failed image load
       * @since 7.0
       *
       * @declaration public boolean IsEmpty(Object Data);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object obj = stack.pop();
        if (obj == null)
          return Boolean.TRUE;
        else if (obj instanceof MetaImage)
          return Boolean.valueOf(((MetaImage) obj).isNullOrFailed());
        else if (obj.toString().equals("0") || obj.toString().equals("0.0"))
          return Boolean.TRUE;
        else if (obj instanceof java.util.Collection)
          return Boolean.valueOf(((java.util.Collection) obj).isEmpty());
        else if (obj instanceof java.util.Map)
          return Boolean.valueOf(((java.util.Map) obj).isEmpty());
        else if (obj.getClass().isArray())
          return Boolean.valueOf(java.lang.reflect.Array.getLength(obj) == 0);
        else
          return Boolean.valueOf(obj.toString().length() == 0);
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "DateFormat", 2, new String[]{"Format", "Date"})
    {
      private java.util.regex.Pattern mslashdPat;
      private String localmdPat;
      private java.util.Locale tempLocale;
      private java.util.Map cachedMap;
      private void createLocalPat()
      {
        if (tempLocale == Sage.userLocale) return;
        cachedMap = new java.util.HashMap();
        try
        {
          tempLocale = Sage.userLocale;
          java.text.DateFormat shortFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT,
              Sage.userLocale);
          localmdPat = java.util.regex.Pattern.compile("\\W?y+\\W?").
              matcher(((java.text.SimpleDateFormat)shortFormat).toPattern()).
              replaceAll("");
          mslashdPat = java.util.regex.Pattern.compile("M+/d+");
        }
        catch (Exception e)
        {
          System.out.println("DateFormat init error:" + e);
          e.printStackTrace();
        }
      }
      /**
       * Returns a formatted date string for the specified Date.
       * @param Format null if SageTV's default date format should be used, otherwise use a formatting string as specified in java.text.SimpleDateFormat
       * @param Date either a java.util.Date object or a long which corresponds to the date
       * @return the date formatted string
       *
       * @declaration public String DateFormat(String Format, Object Date);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        createLocalPat();
        java.util.Date formatMe = null;
        String formString;
        Object secondArg = null;
        Object firstArg = null;
        secondArg = stack.pop();
        firstArg = stack.pop();
        if (secondArg instanceof java.util.Date)
          formatMe = (java.util.Date) secondArg;
        else if (secondArg instanceof Number)
          formatMe = new java.util.Date(((Number) secondArg).longValue());
        else
          throw new sage.jep.ParseException("DateFormat didn't have a Date or Number for its time argument");
        if (firstArg == null)
          return (Sage.dfClean(formatMe.getTime()));
        else
        {
          String orgFormString = firstArg.toString();
          java.text.DateFormat formatter = (java.text.DateFormat) cachedMap.get(orgFormString);
          if (formatter != null)
          {
            synchronized (formatter)
            {
              return formatter.format(formatMe);
            }
          }
          // Replace M/d with the appropriate representation for this Locale
          formString = orgFormString;
          if (mslashdPat != null)
            formString = mslashdPat.matcher(formString).replaceAll(localmdPat);
          formatter = new java.text.SimpleDateFormat(formString, Sage.userLocale);
          Object rv = formatter.format(formatMe);
          cachedMap.put(orgFormString, formatter);
          return (rv);
        }
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "NumberFormat", 2, new String[]{"Format", "Number"})
    {
      /**
       * Returns a formatted numeric string for the specified number.
       * @param Format a formatting string as specified in java.text.DecimalFormat
       * @param Number the floating point number to format
       * @return the formatted numeric string
       *
       * @declaration public String NumberFormat(String Format, float Number);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        float num = getFloat(stack);
        java.text.DecimalFormat formatter = new java.text.DecimalFormat(getString(stack));
        return formatter.format(num);
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "DurFormat", 2, new String[]{"Format", "Duration"})
    {
      /**
       * Returns a formatted duration String for a period of time in milliseconds. The formatting string
       * uses the % character for escapement (%% is not supported, you cannot display the % symbol in a duration string).
       * The 'd', 'h', 'm' and 's' characters can be used to indicate days, hours, minutes and seconds respectively.
       * Any format character may be prefixed by the 'r' character to indicate it is a required field. <p>
       * For example, the format string %rh:%m for 20 minutes would return 0:20 and for the string $h:%m it would return 20
       * If there's characters before a field value then that value will be zero padded, i.e. 65 minutes for %h:%m would be 1:05
       * @param Format the duration format string, or null to use SageTV's default duration formatting
       * @param Duration the duration to print out in milliseconds
       * @return the formatted duration string
       *
       * @declaration public String DurFormat(String Format, long Duration);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        long formatMe = getLong(stack);
        Object firstArg = stack.pop();
        if (firstArg == null)
          return (Sage.durFormatPretty(formatMe));
        else
        {
          boolean hasDays=false,hasHours=false,hasMins=false,hasSecs=false;
          String formStr = firstArg.toString();
          for (int i = 0; i < formStr.length(); i++)
          {
            char c = formStr.charAt(i);
            if (c == '%')
            {
              i++;
              boolean req = false;
              if (formStr.charAt(i) == 'r')
              {
                req = true;
                i++;
              }
              c = formStr.charAt(i);
              int x = 0;
              if (c == 'd')
                hasDays = true;
              else if (c == 'h')
                hasHours = true;
              else if (c == 'm')
                hasMins = true;
              else if (c == 's')
                hasSecs = true;
            }
          }
          StringBuffer sb = new StringBuffer();
          boolean skipCurrentField = false;
          long formVal = Math.abs(formatMe);
          if (formatMe < 0)
            sb.append("-");
          for (int i = 0; i < formStr.length(); i++)
          {
            char c = formStr.charAt(i);
            if (c == '%')
            {
              i++;
              boolean req = false;
              if (formStr.charAt(i) == 'r')
              {
                req = true;
                i++;
              }
              c = formStr.charAt(i);
              long x = 0;
              if (c == 'd')
              {
                x = formVal / Sage.MILLIS_PER_DAY;
              }
              else if (c == 'h')
              {
                x = formVal / Sage.MILLIS_PER_HR;
                if (hasDays)
                  x %= 24;
              }
              else if (c == 'm')
              {
                x = formVal / 60000;
                if (hasHours)
                  x %= 60;
              }
              else if (c == 's')
              {
                x = formVal / 1000;
                if (hasMins)
                  x %= 60;
              }
              if (req || x != 0)
              {
                skipCurrentField = false;
                if (sb.length() > 0 && x < 10 && c != 'd')
                {
                  // Zero pad
                  sb.append("0");
                }
                sb.append(x);
              }
              else if (!req)
                skipCurrentField = true;
            }
            else if (!skipCurrentField)
              sb.append(c);
          }
          Object rv = sb.toString();
          return (rv);
        }
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "CreateTimeSpan", 2, new String[]{"StartTime", "EndTime"})
    {
      /**
       * Returns a length 2 Long object array which can be used for specifying a time span in a table. The first element
       * will be the StartTime and the second will be the EndTime
       * @param StartTime the long value which specifies the start value of the time span
       * @param EndTime the long value which specifies the end value of the time span
       * @return an array which represents this time span
       *
       * @declaration public Long[] CreateTimeSpan(long StartTime, long EndTime);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Long[] rv = new Long[2];
        rv[1] = new Long(getLong(stack));
        rv[0] = new Long(getLong(stack));
        return (rv);
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "GetElement", 2, new String[]{"Data", "Index"})
    {
      /**
       * Returns the element at the specified index in this data; works for arrays and java.util.List implementations (i.e. Vector, etc.)
       * @param Data the java.util.List or array object to get the element from
       * @param Index the 0-based index of the element to retrieve
       * @return the element at the specified index or null if there is no such element
       *
       * @declaration public Object GetElement(Object Data, int Index);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        int elemNum = getInt(stack);
        Object obj = stack.pop();
        Object rv = null;
        try
        {
          if (obj == null)
            rv = null;
          else if (obj instanceof java.util.List)
            rv = ((java.util.List) obj).get(elemNum);
          else if (obj.getClass().isArray())
            rv = java.lang.reflect.Array.get(obj, elemNum);
        }
        catch (Exception e){} // out of bounds is fine, just return null
        return (rv);
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "SetElement", 3, new String[]{"Data", "Index", "Value"})
    {
      /**
       * Sets the element at the specified index in this data; works for arrays and java.util.List implementations (i.e. Vector, etc.)
       * @param Data the java.util.List or array object to set the element for
       * @param Index the 0-based index of the element to set
       * @param Value the value to set
       * @return the Value parameters is returned
       *
       * @declaration public Object SetElement(Object Data, int Index, Object Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object val = stack.pop();
        int elemNum = getInt(stack);
        Object obj = stack.pop();
        if (obj instanceof java.util.List)
          ((java.util.List) obj).set(elemNum, val);
        else if (obj.getClass().isArray())
          java.lang.reflect.Array.set(obj, elemNum, val);
        return obj;
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "RemoveElementAtIndex", 2, new String[]{"Data", "Index"})
    {
      /**
       * Removes the element at the specified index in this data; works java.util.List implementations (i.e. Vector, etc.)
       * @param Data the java.util.List object to remove the element from
       * @param Index the 0-based index of the element to remove
       * @return the element at the specified index or null if there is no such element
       *
       * @declaration public Object RemoveElementAtIndex(java.util.List Data, int Index);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        int elemNum = getInt(stack);
        Object obj = stack.pop();
        Object rv = null;
        try
        {
          if (obj instanceof java.util.List)
            rv = ((java.util.List) obj).remove(elemNum);
        }
        catch (Exception e){} // out of bounds is fine, just return null
        return rv;
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "RemoveElement", 2, new String[]{"Data", "Value"})
    {
      /**
       * Removes the element at with the specified value from this data. Works for java.util.Collection or java.util.Map implementations.
       * If the value appears multiple times in the data (for Collections) only the first occurrence is removed.
       * @param Data the java.util.Collection or java.util.Map object to remove the element from; for maps it removes based on key
       * @param Value the value to remove from the data
       * @return for java.util.Collections true if the element exists and was removed, false otherwise; for java.util.Maps it returns the value that the specified key corresponded to
       *
       * @declaration public Object RemoveElement(Object Data, Object Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object elem = stack.pop();
        Object obj = stack.pop();
        Object rv = null;
        try
        {
          if (obj instanceof java.util.Collection)
            rv = ((java.util.Collection) obj).remove(elem) ? Boolean.TRUE : Boolean.FALSE;
          else if (obj instanceof java.util.Map)
            rv = ((java.util.Map) obj).remove(elem);
        }
        catch (Exception e){} // out of bounds is fine, just return null
        return (rv);
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "AddElement", 2, new String[]{"Data", "Value"})
    {
      /**
       * Add the element with the specified value to this data. Works for java.util.Collection implementations.
       * @param Data the java.util.Collection object to add the element to
       * @param Value the value to add to the data
       * @return for java.util.Collections true if the data changed as a result of the call (i.e. the add succeded and was not redundant), false otherwise
       * @since 7.0
       *
       * @declaration public boolean AddElement(java.util.Collection Data, Object Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object elem = stack.pop();
        Object obj = stack.pop();
        Object rv = null;
        try
        {
          if (obj instanceof java.util.Collection)
            rv = ((java.util.Collection) obj).add(elem) ? Boolean.TRUE : Boolean.FALSE;
        }
        catch (Exception e){} // out of bounds is fine, just return null
        return (rv);
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "FindElementIndex", 2, new String[]{"Data", "Element"})
    {
      /**
       * Returns the index in the data that the specified element is found at. If there are multiple occurrences of this element
       * only the first index is returned. This works for arrays and java.util.List implementations.
       * @param Data the java.util.List or array to look in
       * @param Element the value to search the data for
       * @return the 0-based index of the specified element in the data, or -1 if it does not exist
       *
       * @declaration public int FindElementIndex(Object Data, Object Element);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object elem = stack.pop();
        Object obj = stack.pop();
        int rv = -1;
        if (obj == null)
          rv = -1;
        else if (obj instanceof java.util.List)
          rv = ((java.util.List) obj).indexOf(elem);
        else if (obj instanceof Object[])
          rv = java.util.Arrays.asList((Object[]) obj).indexOf(elem);
        //else if (obj.getClass().isArray())
        //	rv = java.lang.reflect.Array.get(obj, elemNum);
        return new Integer(rv);
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "FindComparativeElement", 3, new String[]{"Data", "Criteria", "Method"})
    {
      /**
       * Searches a sorted list of data to find the index that the specified criteria exists at; or if it doesn't exist
       * in the data it will use the index that would be the appropriate insertion point for the criteria in the data
       * in order to maintain sort order. The element at that index is what is returned
       * @param Data the data to sort, this must be a java.util.Collection, a java.util.Map, or an array
       * @param Criteria the object to compare the elements to; this must implement java.lang.Comparable
       * @param Method the method name to execute on each element to get the value to compare; use null to compare the elements themselves
       * @return the element at the comparative insertion point
       *
       * @declaration public Object FindComparativeElement(Object Data, Comparable Criteria, String Method);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        String methName = getString(stack);
        sage.jep.function.PostfixMathCommandI compMeth = null;
        if (methName != null)
          compMeth = (sage.jep.function.PostfixMathCommandI) Catbert.getAPI().get(methName);
        Comparable criteria = (Comparable)stack.pop();
        Object dataObj = stack.pop();
        if (dataObj == null) return null;
        Object[] currData;
        if (dataObj instanceof java.util.Collection)
          currData = ((java.util.Collection) dataObj).toArray();
        else if (dataObj instanceof java.util.Map)
          currData = ((java.util.Map) dataObj).keySet().toArray();
        else
          currData = (Object[]) dataObj;
        java.text.Collator collie = java.text.Collator.getInstance(Sage.userLocale);
        collie.setStrength(java.text.Collator.PRIMARY);
        boolean specialChannelCompare = (methName != null) && methName.indexOf("ChannelNumber") != -1;
        java.util.Comparator chanCompare = null;
        if (specialChannelCompare)
        {
          chanCompare = (stack.getUIMgrSafe() == null) ? EPG.channelNumSorter : stack.getUIMgrSafe().channelNumSorter;
        }
        for (int i = 0; i < currData.length; i++)
        {
          Object testRes;
          if (compMeth != null)
          {
            stack.push(currData[i]);
            compMeth.setCurNumberOfParameters(1);
            compMeth.run(stack);
            testRes = stack.pop();
          }
          else
            testRes = currData[i];
          // There's a special case with channel numbers we want to account for
          if (specialChannelCompare)
          {
            try
            {
              if (chanCompare.compare(criteria, testRes) <= 0)
                return currData[i];
            }
            catch (Exception e)
            {
              if (criteria instanceof String && testRes instanceof String)
              {
                if (collie.compare(criteria, testRes) <= 0)
                  return currData[i];
              }
              else if (criteria.compareTo(testRes) <= 0)
                return currData[i];
            }
          }
          else
          {
            if (criteria instanceof String && testRes instanceof String)
            {
              if (collie.compare(criteria, testRes) <= 0)
                return currData[i];
            }
            else if (criteria.compareTo(testRes) <= 0)
              return currData[i];
          }
        }
        if (currData.length > 0)
          return currData[currData.length - 1];
        else
          return null;
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "Substring", 3, new String[]{"String", "StartIndex", "EndIndex"})
    {
      /**
       * Returns the substring from a specified string. Same as java.lang.String.substring(int startIndex, int endIndex)
       * @param String the string to get the substring of
       * @param StartIndex the 0-based index that the substring starts at
       * @param EndIndex the 0-based index that the substring ends at or -1 if the substring goes to the end of the string
       * @return the substring from the specified string
       *
       * @declaration public String Substring(String String, int StartIndex, int EndIndex);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        int idx1 = -1, idx2=-1;
        idx2 = getInt(stack);
        idx1 = getInt(stack);
        Object obj = stack.pop();
        String s = (obj == null) ? "" : obj.toString();
        if (idx2 == -1)
          return (s.substring(idx1));
        else
          return (s.substring(idx1, idx2));
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "SubstringBegin", new String[]{"String", "EndOffset"})
    {
      /**
       * Returns the substring from a specified string. The substring will start at the beginning of the string and end
       * EndIndex characters before the end of the string. Same as Substring(String, 0, Size(String) - EndOffset).
       * @param String the string to get the substring of
       * @param EndOffset the number of characters from the end of the string to terminate the substring (0 implies return the entire string)
       * @return the substring from the specified string
       * @since 7.0
       *
       * @declaration public String SubstringBegin(String String, int EndOffset);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        int idx1 = -1;
        idx1 = getInt(stack);
        Object obj = stack.pop();
        String s = (obj == null) ? "" : obj.toString();
        return s.substring(0, s.length() - idx1);
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "Round", new String[]{"Number"})
    {
      /**
       * Rounds a floating point number to an integral value. For Doubles a Long is returned, for Floats an Integer is returned
       * @param Number the number to round
       * @return the rounded value
       *
       * @declaration public Object Round(Object Number);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object o = stack.pop();
        if (o instanceof Double)
          return new Long(Math.round(((Double) o).doubleValue()));
        else
          return new Integer(Math.round(((Number) o).floatValue()));
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "Time")
    {
      /**
       * Returns the current time; see java.lang.System.currentTimeMillis() for the explanation of the units.
       * @return the current time
       *
       * @declaration public long Time();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(Sage.time());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintCurrentTime")
    {
      /**
       * Returns a string that represents the current time.
       * @return a string that represents the current time.
       *
       * @declaration public String PrintCurrentTime();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.df();
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintDate", 1, new String[]{"Date"})
    {
      /**
       * Returns a formatted date string using the java.text.DateFormat.MEDIUM formatting technique
       * @param Date the date value to format
       * @return a formatted date string
       *
       * @declaration public String PrintDate(long Date);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long x = getLong(stack);
        return (x == 0) ? "" : Sage.dfjMed(x);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintDateLong", 1, new String[]{"Date"})
    {
      private java.text.SimpleDateFormat longdf;
      private java.util.Locale tempLocale;
      private void createLocalPat()
      {
        if (Sage.userLocale == tempLocale) return;
        tempLocale = Sage.userLocale;
        try
        {
          java.text.DateFormat longFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL,
              Sage.userLocale);
          // To get it into the format we want we need to convert the weekday & months to
          // using their short strings. AND we need to remove the year and any conjoining punctuation for it.
          String pat = ((java.text.SimpleDateFormat)longFormat).toPattern();

          // Convert weekday string
          pat = java.util.regex.Pattern.compile("E+").matcher(pat).replaceAll("EEE");

          // Convert month string
          pat = java.util.regex.Pattern.compile("M+").matcher(pat).replaceAll("MMM");

          // Remove the year
          pat = java.util.regex.Pattern.compile("\\W*y+\\W*").matcher(pat).replaceAll("");

          longdf = new java.text.SimpleDateFormat(pat, Sage.userLocale);
        }
        catch (Exception e)
        {
          System.out.println("DateFormat init error:" + e);
          e.printStackTrace();
        }
      }
      /**
       * Returns a formatted date string using SageTV's default detailed date formatting
       * @param Date the date value to format
       * @return a formatted date string
       *
       * @declaration public String PrintDateLong(long Date);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long x = getLong(stack);
        createLocalPat();
        return (x == 0) ? "" : longdf.format(new java.util.Date(x));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintDateShort", 1, new String[]{"Date"})
    {
      /**
       * Returns a formatted date string using the java.text.DateFormat.SHORT formatting technique
       * @param Date the date value to format
       * @return a formatted date string
       *
       * @declaration public String PrintDateShort(long Date);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long x = getLong(stack);
        return (x == 0) ? "" : Sage.dfjShort(x);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintDateFull", 1, new String[]{"Date"})
    {
      /**
       * Returns a formatted date string using the java.text.DateFormat.FULL formatting technique
       * @param Date the date value to format
       * @return a formatted date string
       *
       * @declaration public String PrintDateFull(long Date);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long x = getLong(stack);
        return (x == 0) ? "" : Sage.dfjFull(x);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintTime", 1, new String[]{"Time"})
    {
      /**
       * Returns a formatted time string using the java.text.DateFormat.MEDIUM formatting technique
       * @param Time the time value to format
       * @return a formatted time string
       *
       * @declaration public String PrintTime(long Time);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long x = getLong(stack);
        return (x == 0) ? "" : Sage.tfjMed(x);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintTimeLong", 1, new String[]{"Time"})
    {
      /**
       * Returns a formatted time string using the java.text.DateFormat.LONG formatting technique
       * @param Time the time value to format
       * @return a formatted time string
       *
       * @declaration public String PrintTimeLong(long Time);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long x = getLong(stack);
        return (x == 0) ? "" : Sage.tfjLong(x);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintTimeShort", 1, new String[]{"Time"})
    {
      /**
       * Returns a formatted time string using the java.text.DateFormat.SHORT formatting technique
       * @param Time the time value to format
       * @return a formatted time string
       *
       * @declaration public String PrintTimeShort(long Time);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long x = getLong(stack);
        return (x == 0) ? "" : Sage.tfjShort(x);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintTimeFull", 1, new String[]{"Time"})
    {
      /**
       * Returns a formatted time string using the java.text.DateFormat.FULL formatting technique
       * @param Time the time value to format
       * @return a formatted time string
       *
       * @declaration public String PrintTimeFull(long Time);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long x = getLong(stack);
        return (x == 0) ? "" : Sage.tfjFull(x);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintDuration", 1, new String[]{"Duration"})
    {
      /**
       * Returns a formatted duration string according to SageTV's verbose duration formating, minutes is the most detailed resolution of this format
       * @param Duration the duration in milliseconds to print
       * @return the formatted duration string
       *
       * @declaration public String PrintDuration(long Duration);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.durFormatPretty(getLong(stack));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintDurationWithSeconds", 1, new String[]{"Duration"})
    {
      /**
       * Returns a formatted duration string according to SageTV's default duration formating, seconds is the most detailed resolution of this format
       * @param Duration the duration in milliseconds to print
       * @return the formatted duration string
       *
       * @declaration public String PrintDurationWithSeconds(long Duration);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.durFormatPrettyWithSeconds(getLong(stack));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PrintDurationShort", 1, new String[]{"Duration"})
    {
      /**
       * Returns a formatted duration string according to SageTV's concise duration formating, minutes is the most detailed resolution of this format
       * @param Duration the duration in milliseconds to print
       * @return the formatted duration string
       *
       * @declaration public String PrintDurationShort(long Duration);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.durFormat(getLong(stack));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetDiskFreeSpace", 1, new String[]{"DrivePath"})
    {
      /**
       * Returns the amount of disk free space in bytes at the specified path
       * @param DrivePath the path string of a disk to get the free space of
       * @return the free space on the specified disk in bytes
       *
       * @declaration public long GetDiskFreeSpace(String DrivePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(Sage.getDiskFreeSpace(getString(stack)));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetDiskTotalSpace", 1, new String[]{"DrivePath"})
    {
      /**
       * Returns the amount of total disk space in bytes at the specified path
       * @param DrivePath the path string of a disk to get the total space of
       * @return the total space on the specified disk in bytes
       *
       * @declaration public long GetDiskTotalSpace(String DrivePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(Sage.getDiskTotalSpace(getString(stack)));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetFileSystemType", 1, new String[]{"DrivePath"}, true)
    {
      /**
       * Gets the name of the filesystem type at the specified path
       * @param DrivePath the path string of a disk to get the filesystem type for
       * @return the name of the filesystem type at the specified path
       *
       * @declaration public String GetFileSystemType(String DrivePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Sage.LINUX_OS)
          return Sage.getFileSystemTypeX(getString(stack));
        java.io.File rootDir = IOUtils.getRootDirectory(getFile(stack));
        return rootDir != null ? Sage.getFileSystemTypeX(rootDir.toString()) : "";
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetWindowsRegistryNames", 2, new String[]{"Root", "Key"})
    {
      /**
       * Returns a list of the Windows registry names which exist under the specified root &amp; key (Windows only)
       * Acceptable values for the Root are: "HKCR", "HKEY_CLASSES_ROOT", "HKCC", "HKEY_CURRENT_CONFIG", "HKCU",
       * "HKEY_CURRENT_USER", "HKU", "HKEY_USERS", "HKLM" or "HKEY_LOCAL_MACHINE" (HKLM is the default if nothing matches)
       * @param Root the registry hive to look in
       * @param Key the key path in the registry hive
       * @return the names stored in the registry under the specified key
       *
       * @declaration public String[] GetWindowsRegistryNames(String Root, String Key);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String k = getString(stack);
        String r = getString(stack);
        if (!Sage.WINDOWS_OS)
          return Pooler.EMPTY_STRING_ARRAY;
        return Sage.getRegistryNames(Sage.getHKEYForName(r), k);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetWindowsRegistryDWORDValue", 3, new String[]{"Root", "Key", "Name"})
    {
      /**
       * Returns a DWORD value from the Windows registry for the specified root, key and name(Windows only)
       * Acceptable values for the Root are: "HKCR", "HKEY_CLASSES_ROOT", "HKCC", "HKEY_CURRENT_CONFIG", "HKCU",
       * "HKEY_CURRENT_USER", "HKU", "HKEY_USERS", "HKLM" or "HKEY_LOCAL_MACHINE" (HKLM is the default if nothing matches)
       * @param Root the registry hive to look in
       * @param Key the key path in the registry hive
       * @param Name the name of the registry value to retrieve
       * @return the value of the specified registry setting as a DWORD
       *
       * @declaration public int GetWindowsRegistryDWORDValue(String Root, String Key, String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String n = getString(stack);
        String k = getString(stack);
        String r = getString(stack);
        if (!Sage.WINDOWS_OS)
          return new Integer(0);
        return new Integer(Sage.readDwordValue(Sage.getHKEYForName(r), k, n));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetWindowsRegistryStringValue", 3, new String[]{"Root", "Key", "Name"})
    {
      /**
       * Returns a string value from the Windows registry for the specified root, key and name(Windows only)
       * Acceptable values for the Root are: "HKCR", "HKEY_CLASSES_ROOT", "HKCC", "HKEY_CURRENT_CONFIG", "HKCU",
       * "HKEY_CURRENT_USER", "HKU", "HKEY_USERS", "HKLM" or "HKEY_LOCAL_MACHINE" (HKLM is the default if nothing matches)
       * @param Root the registry hive to look in
       * @param Key the key path in the registry hive
       * @param Name the name of the registry value to retrieve
       * @return the value of the specified registry setting as a string
       *
       * @declaration public int GetWindowsRegistryStringValue(String Root, String Key, String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String n = getString(stack);
        String k = getString(stack);
        String r = getString(stack);
        if (!Sage.WINDOWS_OS)
          return "";
        return Sage.readStringValue(Sage.getHKEYForName(r), k, n);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "RemoveWindowsRegistryValue", 3, new String[]{"Root", "Key", "Name"})
    {
      /**
       * Removes a value from the Windows registry for the specified root, key and name(Windows only)
       * Acceptable values for the Root are: "HKCR", "HKEY_CLASSES_ROOT", "HKCC", "HKEY_CURRENT_CONFIG", "HKCU",
       * "HKEY_CURRENT_USER", "HKU", "HKEY_USERS", "HKLM" or "HKEY_LOCAL_MACHINE" (HKLM is the default if nothing matches)
       * @param Root the registry hive to look in
       * @param Key the key path in the registry hive
       * @param Name the name of the registry value to remove
       * @return true if the specified setting was removed, false otherwise
       *
       * @declaration public boolean RemoveWindowsRegistryValue(String Root, String Key, String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String n = getString(stack);
        String k = getString(stack);
        String r = getString(stack);
        if (!Sage.WINDOWS_OS)
          return Boolean.FALSE;
        return Boolean.valueOf(Sage.removeRegistryValue(Sage.getHKEYForName(r), k, n));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "SetWindowsRegistryDWORDValue", 4, new String[]{"Root", "Key", "Name", "Value"})
    {
      /**
       * Sets a DWORD value in the Windows registry for the specified root, key and name(Windows only)
       * The name will be created if it doesn't already exist.
       * Acceptable values for the Root are: "HKCR", "HKEY_CLASSES_ROOT", "HKCC", "HKEY_CURRENT_CONFIG", "HKCU",
       * "HKEY_CURRENT_USER", "HKU", "HKEY_USERS", "HKLM" or "HKEY_LOCAL_MACHINE" (HKLM is the default if nothing matches)
       * @param Root the registry hive to use
       * @param Key the key path in the registry hive
       * @param Name the name of the registry value to set
       * @param Value the value of the specified registry setting as a DWORD
       * @return true if the operation was successful, false otherwise
       *
       * @declaration public boolean SetWindowsRegistryDWORDValue(String Root, String Key, String Name, int Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int v = getInt(stack);
        String n = getString(stack);
        String k = getString(stack);
        String r = getString(stack);
        if (!Sage.WINDOWS_OS)
          return Boolean.FALSE;
        return Boolean.valueOf(Sage.writeDwordValue(Sage.getHKEYForName(r), k, n, v));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "SetWindowsRegistryStringValue", 4, new String[]{"Root", "Key", "Name", "Value"})
    {
      /**
       * Sets a string value in the Windows registry for the specified root, key and name(Windows only)
       * The name will be created if it doesn't already exist.
       * Acceptable values for the Root are: "HKCR", "HKEY_CLASSES_ROOT", "HKCC", "HKEY_CURRENT_CONFIG", "HKCU",
       * "HKEY_CURRENT_USER", "HKU", "HKEY_USERS", "HKLM" or "HKEY_LOCAL_MACHINE" (HKLM is the default if nothing matches)
       * @param Root the registry hive to use
       * @param Key the key path in the registry hive
       * @param Name the name of the registry value to set
       * @param Value the value of the specified registry setting as a string
       * @return true if the operation was successful, false otherwise
       *
       * @declaration public boolean SetWindowsRegistryStringValue(String Root, String Key, String Name, String Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String v = getString(stack);
        String n = getString(stack);
        String k = getString(stack);
        String r = getString(stack);
        if (!Sage.WINDOWS_OS)
          return Boolean.FALSE;
        return Boolean.valueOf(Sage.writeStringValue(Sage.getHKEYForName(r), k, n, v));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "PlaySound", 1, new String[]{"SoundFile"})
    {
      /**
       * Plays the specified sound file (used for sound effects, don't use for music playback)
       * @param SoundFile the path of the sound resource to play back
       *
       * @declaration public void PlaySound(String SoundFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        stack.getUIMgrSafe().playSound(getString(stack)); return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "If", new String[] { "Condition", "True", "False" })
    {
      /**
       * Returns the second argument if the first argument is true, otherwise the third argument is returned. All 3 arguments
       * will be evaluated in all cases. This does NOT have a short-circuit which prevents evaluation of the third argument if the first is true.
       * @param Condition the value to test to see if it is true
       * @param True the value to return if the Condition is true
       * @param False the value to return if the Condition is not true
       * @return the appropriate value based on the condition
       *
       * @declaration public Object If(boolean Condition, Object True, Object False);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object falseRes = stack.pop();
        Object trueRes = stack.pop();
        if (evalBool(stack.pop()))
          return trueRes;
        else
          return falseRes;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetFileNameFromPath", new String[] { "FilePath" })
    {
      /**
       * Returns the file name from the specified file path; this just returns the filename without any path information.
       * @param FilePath the filepath to get the filename for
       * @return the filename from the specified file path
       *
       * @declaration public String GetFileNameFromPath(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        return (f == null) ? "" : f.getName();
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetAbsoluteFilePath", new String[] { "FilePath" })
    {
      /**
       * Returns the full path name from the specified file path..
       * @param FilePath the filepath to get the full path from
       * @return the full path from the specified file path
       * @since 7.0
       *
       * @declaration public String GetAbsoluteFilePath(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        return (f == null) ? "" : f.getAbsolutePath();
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetFileExtensionFromPath", new String[] { "FilePath" })
    {
      /**
       * Returns the file name extension from the specified file path (not including the '.')
       * @param FilePath the file path to get the extension of
       * @return the extension from the specified file path
       * @since 6.4
       *
       * @declaration public String GetFileExtensionFromPath(String FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        if (s == null)
          return null;
        int idx = s.lastIndexOf('.');
        if (idx == -1)
          return "";
        else
          return s.substring(idx + 1);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "Wait", new String[] { "Time" })
    {
      /**
       * Causes the currently executing thread to sleep for the specified amount of time in milliseconds.
       * @param Time the amount of time to sleep this thread for in milliseconds
       *
       * @declaration public void Wait(long Time);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long l = getLong(stack);
        if (l > 0)
        {
          try{Thread.sleep(l);}catch(Exception e){}
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "Max", new String[] { "Value1", "Value2" })
    {
      /**
       * Returns the maximum of the two arguments; the type of the returned argument will be the same as the highest precision argument
       * @param Value1 one of the values
       * @param Value2 the other value
       * @return the maximum of the passed in values
       *
       * @declaration public Number Max(Number Value1, Number Value2);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object v2 = stack.pop();
        Object v1 = stack.pop();
        if (v2 == null) return v1;
        if (v1 == null) return v2;
        if (v1 instanceof Number && v2 instanceof Number)
        {
          if (v1 instanceof Double || v2 instanceof Double)
            return new Double(Math.max(((Number) v1).doubleValue(), ((Number) v2).doubleValue()));
          else if (v1 instanceof Float || v2 instanceof Float)
            return new Float(Math.max(((Number) v1).floatValue(), ((Number) v2).floatValue()));
          else if (v1 instanceof Long || v2 instanceof Long)
            return new Long(Math.max(((Number) v1).longValue(), ((Number) v2).longValue()));
          else
            return new Integer(Math.max(((Number) v1).intValue(), ((Number) v2).intValue()));
        }
        else
        {
          return new Double(Math.max(Double.parseDouble(v1.toString()), Double.parseDouble(v2.toString())));
        }
      }});
    rft.put(new PredefinedJEPFunction("Utility", "Min", new String[] { "Value1", "Value2" })
    {
      /**
       * Returns the minimum of the two arguments; the type of the returned argument will be the same as the highest precision argument
       * @param Value1 one of the values
       * @param Value2 the other value
       * @return the minimum of the passed in values
       *
       * @declaration public Number Min(Number Value1, Number Value2);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object v2 = stack.pop();
        Object v1 = stack.pop();
        if (v2 == null) return v1;
        if (v1 == null) return v2;
        if (v1 instanceof Number && v2 instanceof Number)
        {
          if (v1 instanceof Double || v2 instanceof Double)
            return new Double(Math.min(((Number) v1).doubleValue(), ((Number) v2).doubleValue()));
          else if (v1 instanceof Float || v2 instanceof Float)
            return new Float(Math.min(((Number) v1).floatValue(), ((Number) v2).floatValue()));
          else if (v1 instanceof Long || v2 instanceof Long)
            return new Long(Math.min(((Number) v1).longValue(), ((Number) v2).longValue()));
          else
            return new Integer(Math.min(((Number) v1).intValue(), ((Number) v2).intValue()));
        }
        else
        {
          return new Double(Math.min(Double.parseDouble(v1.toString()), Double.parseDouble(v2.toString())));
        }
      }});
    rft.put(new PredefinedJEPFunction("Utility", "ExecuteProcess", new String[] { "CommandString", "Arguments", "WorkingDirectory", "ConsoleApp" })
    {
      /**
       * Executes a new process on the system
       * @param CommandString the command to execute (i.e. C:\windows\notepad.exe or ifconfig)
       * @param Arguments the arguments to pass to the command that is executed, if it's a java.util.Collection or array then each element is an argument, otherwise it is considered a single argument; use null for no arguments
       * @param WorkingDirectory the directory to execute the process from or null to execute it from the current working directory
       * @param ConsoleApp if true then SageTV will consume the stdout and stderr output from the process that is launched; if false it will not
       * @return the java.lang.Process object that represents the launched process
       *
       * @declaration public Process ExecuteProcess(String CommandString, Object Arguments, java.io.File WorkingDirectory, boolean ConsoleApp);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean consumeIO = evalBool(stack.pop());
        java.io.File wd = getFile(stack);
        Object args = stack.pop();
        String cmdString = getString(stack);
        String[] cmdArray = null;
        if (args == null)
        {
          cmdArray = new String[] { cmdString };
        }
        else if (!(args instanceof java.util.Collection) && !(args instanceof Object[]))
        {
          cmdArray = new String[] { cmdString, args.toString() };
        }
        else
        {
          java.util.Collection collie;
          if (args instanceof Object[])
          {
            collie = java.util.Arrays.asList((Object[]) args);
          }
          else
          {
            collie = (java.util.Collection) args;
          }
          cmdArray = new String[1 + collie.size()];
          cmdArray[0] = cmdString;
          java.util.Iterator walker = collie.iterator();
          int idx = 1;
          while (walker.hasNext())
          {
            Object nextie = walker.next();
            if (nextie == null)
              cmdArray[idx++] = "";
            else
              cmdArray[idx++] = nextie.toString();
          }
        }
        final Process procy = Runtime.getRuntime().exec(cmdArray, null, wd);
        if (consumeIO)
        {
          Pooler.execute(new Runnable()
          {
            public void run()
            {
              try
              {
                java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(
                    procy.getInputStream()));
                while (buf.readLine() != null);
                buf.close();
              }
              catch (Exception e){}
            }
          }, "InputStreamConsumer");
          Pooler.execute(new Runnable()
          {
            public void run()
            {
              try
              {
                java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(
                    procy.getErrorStream()));
                while (buf.readLine() != null);
                buf.close();
              }
              catch (Exception e){}
            }
          }, "ErrorStreamConsumer");
        }
        return procy;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "ExecuteProcessReturnOutput", new String[] { "CommandString", "Arguments", "WorkingDirectory", "ReturnStdout", "ReturnStderr" })
    {
      /**
       * Executes a new process on the system and returns as a String the output of the process
       * @param CommandString the command to execute (i.e. C:\windows\notepad.exe or ifconfig)
       * @param Arguments the arguments to pass to the command that is executed, if it's a java.util.Collection or array then each element is an argument, otherwise it is considered a single argument; use null for no arguments
       * @param WorkingDirectory the directory to execute the process from or null to execute it from the current working directory
       * @param ReturnStdout if true then SageTV will return the data from stdout as part of the return value
       * @param ReturnStderr if true then SageTV will return the data from stderr as part of the return value
       * @return a String which contains the data from stdout/stderr (depending upon the arguments), null if there was a failure
       *
       * @since 6.0
       *
       * @declaration public String ExecuteProcessReturnOutput(String CommandString, Object Arguments, java.io.File WorkingDirectory, boolean ReturnStdout, boolean ReturnStderr);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        final boolean retStderr = evalBool(stack.pop());
        final boolean retStdout = evalBool(stack.pop());
        java.io.File wd = getFile(stack);
        Object args = stack.pop();
        String cmdString = getString(stack);
        String[] cmdArray = null;
        final StringBuffer rv = new StringBuffer();
        if (args == null)
        {
          cmdArray = new String[] { cmdString };
        }
        else if (!(args instanceof java.util.Collection) && !(args instanceof Object[]))
        {
          cmdArray = new String[] { cmdString, args.toString() };
        }
        else
        {
          java.util.Collection collie;
          if (args instanceof Object[])
          {
            collie = java.util.Arrays.asList((Object[]) args);
          }
          else
          {
            collie = (java.util.Collection) args;
          }
          cmdArray = new String[1 + collie.size()];
          cmdArray[0] = cmdString;
          java.util.Iterator walker = collie.iterator();
          int idx = 1;
          while (walker.hasNext())
          {
            Object nextie = walker.next();
            if (nextie == null)
              cmdArray[idx++] = "";
            else
              cmdArray[idx++] = nextie.toString();
          }
        }
        final Process procy = Runtime.getRuntime().exec(cmdArray, null, wd);
        Thread the = new Thread("InputStreamConsumer")
        {
          public void run()
          {
            try
            {
              java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(
                  procy.getInputStream()));
              String s;
              do
              {
                int c = buf.read();
                if (c == -1)
                  break;
                else if (retStdout)
                  rv.append((char) c);
              }while (true);
              buf.close();
            }
            catch (Exception e){}
          }
        };
        the.setDaemon(true);
        the.start();
        Thread the2 = new Thread("ErrorStreamConsumer")
        {
          public void run()
          {
            try
            {
              java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(
                  procy.getErrorStream()));
              String s;
              do
              {
                int c = buf.read();
                if (c == -1)
                  break;
                else if (retStderr)
                  rv.append((char) c);
              }while (true);
              buf.close();
            }
            catch (Exception e){}
          }
        };
        the2.setDaemon(true);
        the2.start();
        procy.waitFor();
        the.join(1000);
        the2.join(1000);
        return rv.toString();
      }});
    rft.put(new PredefinedJEPFunction("Utility", "LoadImageFile", new String[] { "FilePath" })
    {
      /**
       * Returns a MetaImage object that refers to the specified image file. Used for passing images into Widgets.
       * @param FilePath the file path of the image to load
       * @return the loaded image object
       *
       * @declaration public MetaImage LoadImageFile(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return MetaImage.getMetaImage(getFile(stack));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "LoadImage", new String[] { "Resource" })
    {
      /**
       * Returns a MetaImage object that refers to a specified image resource. This can be used to load images from URLs, JAR resources or the file system.<p>
       * It also has a secondary purpose where you can pass it a MetaImage and then it will load that image into
       * the current image cache so it will render as fast as possible in the next drawing cycle. Good for preloading
       * the next image in a slideshow. If a MetaImage is passed in; this call will not return until that image is loaded into the cache.
       * @param Resource if this is a MetaImage then the image is loaded into the cache, otherwise its converted to a string and then a MetaImage is returned for that resource
       * @return the MetaImage that refers to the passed specified resource, if a MetaImage was passed in then the same object is returned
       *
       * @declaration public MetaImage LoadImage(Object Resource);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof MetaImage)
        {
          // NOTE: We can't preload the native image for non 3-D renderers this way because
          // they don't have accelerated scaling so we don't know what image size to load for it
          SageRenderer renderEngine = stack.getUIMgrSafe().getRootPanel().getRenderEngine();
          if (stack.getUIMgrSafe().getRootPanel().isAcceleratedDrawing() ||
              ((renderEngine instanceof MiniClientSageRenderer) &&
                  (((MiniClientSageRenderer)renderEngine).getGfxScalingCaps() & MiniClientSageRenderer.GFX_SCALING_HW) != 0))
          {
            MetaImage mi = (MetaImage) o;
            if (renderEngine instanceof NativeImageAllocator)
            {
              if (mi instanceof MetaImage.Waiter)
              {
                // Check if this is for a thumbnail that's currently being generated. In that case we need to wait on
                // the thumbnail gen and then load the image after that's done.
                Object waitObj = ((MetaImage.Waiter) mi).getWaitObj();
                if (waitObj instanceof MediaFile)
                {
                  mi = ((MediaFile) waitObj).getThumbnail(stack.getUIComponent(), true);
                  o = mi;
                }
              }
              ((NativeImageAllocator) renderEngine).preloadImage(mi);
            }
          }
          else if ((Sage.getBoolean("ui/disable_native_image_loader", false) ||
              (stack.getUIMgr() != null && stack.getUIMgr().getUIClientType() == UIClient.LOCAL)))
          {
            // Only do this for Java2D rendering locally. Do NOT do it for extenders or placeshifters
            // since we load & scale those on the fly so this would just be wasteful to do
            ((MetaImage) o).getJavaImage(0);
            ((MetaImage) o).removeJavaRef(0);
          }
          else if ((renderEngine instanceof MiniClientSageRenderer) &&
              ((MiniClientSageRenderer)renderEngine).getGfxScalingCaps() == 0)

          {
            // at least load the raw image version, this is for things like the MVP....but don't load it if it's too big.
            MetaImage mi = (MetaImage) o;
            if (mi.getWidth() * mi.getHeight() < 5000000)
            {
              mi.getRawImage(0);
              mi.removeRawRef(0);
            }
          }
          return o;
        }
        return MetaImage.getMetaImage(o == null ? null : o.toString(), stack.getUIComponent());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "SaveImageToFile", new String[] { "MetaImage", "File", "Width", "Height" })
    {
      /**
       * Saves a MetaImage object to a file using the specified image size. The supported formats are JPG and PNG. The format is determined by the file extension, which must be either .jpg or .png.
       * Use zero for the width and height to save it at the original image size.
       * NOTE: This call is a NOOP on embedded platforms
       * @param MetaImage the MetaImage object that should be saved to the specified file
       * @param File the file to save the image to
       * @param Width the width to use in the saved image file
       * @param Height the height to use in the saved image file
       * @return returns true on success or false on failure
       * @since 7.1
       *
       * @declaration public boolean SaveImageToFile(MetaImage MetaImage, java.io.File File, int Width, int Height);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int height = getInt(stack);
        int width = getInt(stack);
        java.io.File file = getFile(stack);
        Object o = stack.pop();
        if (o instanceof MetaImage)
        {
          MetaImage mi = (MetaImage) o;
          try
          {
            sage.media.image.RawImage raw = mi.getRawImage(mi.getImageIndex(width, height));
            if (raw != null)
            {
              sage.media.image.ImageLoader.compressImageToFilePath(raw, file.getAbsolutePath(), file.toString().toLowerCase().endsWith("png") ? "png" : "jpg");
              if (file.isFile() && file.length() > 0)
                return Boolean.TRUE;
            }
          }
          finally
          {
            mi.removeRawRef(mi.getImageIndex(width, height));
          }
          return Boolean.FALSE;
        }
        else
          return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetMetaImageSourceFile", new String[] { "MetaImage" })
    {
      /**
       * Returns the file path that a MetaImage was loaded from. Since not all MetaImage objects come from file paths, this will return null for any non-file based images.
       * @param MetaImage the MetaImage to get the file path for
       * @return the file path for the specified MetaImage, or null if it wasn't loaded from a file path
       * @since 7.1
       *
       * @declaration public java.io.File GetMetaImageSourceFile(MetaImage MetaImage);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof MetaImage)
        {
          Object src = ((MetaImage) o).getSource();
          if (src instanceof java.io.File)
            return src;
          else
            return null;
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetMetaImageAspectRatio", new String[] { "MetaImage" })
    {
      /**
       * Returns the aspect ratio of an image as a floating point number of width/height, zero if the image was a failed load or has not been loaded yet
       * @param MetaImage the MetaImage to get the aspec for
       * @return the aspect ratio of the image as a floating point number of width/height, zero if the image was a failed load or has not been loaded yet
       * @since 8.0
       *
       * @declaration public float GetMetaImageAspectRatio(MetaImage MetaImage);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof MetaImage)
        {
          MetaImage mi = (MetaImage) o;
          if (mi instanceof MetaImage.Waiter || mi.isNullOrFailed())
            return zeroFloat;
          else
            return new Float(((float)mi.getWidth())/mi.getHeight());
        }
        return zeroFloat;
      }
      private Float zeroFloat = new Float(0);
    });
    rft.put(new PredefinedJEPFunction("Utility", "IsMetaImage", new String[] { "MetaImage" })
    {
      /**
       * Returns true if the argument is a MetaImage object.
       * @param MetaImage the Object to test
       * @return true if the argument is a MetaImage object, false otherwise
       * @since 7.1
       *
       * @declaration public boolean IsMetaImage(Object MetaImage);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return (stack.pop() instanceof MetaImage) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetMetaImageBytes", new String[] { "MetaImage" })
    {
      /**
       * Returns a byte array which is the contents of the MetaImage source's data (i.e. compressed image data)
       * @param MetaImage the MetaImage to get the compressed byte data for
       * @return a byte array which is the contents of the MetaImage source's data (i.e. compressed image data), null if it cannot load the data or the argument is not a MetaImage
       * @since 8.1
       *
       * @declaration public byte[] GetMetaImageBytes(MetaImage MetaImage);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof MetaImage)
        {
          return ((MetaImage) o).getSourceAsBytes();
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetImageAsBufferedImage", new String[] { "Resource" })
    {
      /**
       * Returns a java.awt.image.BufferedImage object. This can be used to load images from URLs, JAR resources or the file system.<p>
       * @param Resource if this is a MetaImage then the buffered image is taken from that, otherwise its converted to a string and then the image is loaded from that path
       * @return a newly allocated java.awt.image.BufferedImage corresponding to the specified resource
       * @since 4.1
       *
       * @declaration public java.awt.image.BufferedImage GetImageAsBufferedImage(Object Resource);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (!(o instanceof MetaImage))
        {
          o = MetaImage.getMetaImage(o == null ? null : o.toString(), stack.getUIComponent());
        }
        if (o instanceof MetaImage)
        {
          java.awt.image.BufferedImage rv = ImageUtils.cloneImage(((MetaImage) o).getJavaImage(0));
          ((MetaImage) o).removeJavaRef(0);
          return rv;
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetScaledImageAsBufferedImage", new String[] { "Resource", "Width", "Height" })
    {
      /**
       * Returns a java.awt.image.BufferedImage object. This can be used to load images from URLs, JAR resources or the file system.
       * The size of the returned image will match the passed in arguments.
       * @param Resource if this is a MetaImage then the buffered image is taken from that, otherwise its converted to a string and then the image is loaded from that path
       * @param Width the desired width of the returned image
       * @param Height the desired height of the returned image
       * @return a newly allocated java.awt.image.BufferedImage corresponding to the specified resource
       * @since 4.1
       *
       * @declaration public java.awt.image.BufferedImage GetScaledImageAsBufferedImage(Object Resource, int Width, int Height);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int h = getInt(stack);
        int w = getInt(stack);
        Object o = stack.pop();
        if (!(o instanceof MetaImage))
        {
          o = MetaImage.getMetaImage(o == null ? null : o.toString(), stack.getUIComponent());
        }
        if (o instanceof MetaImage)
        {
          java.awt.image.BufferedImage rv = ImageUtils.createBestScaledImage(((MetaImage) o).getJavaImage(0), w, h);
          ((MetaImage) o).removeJavaRef(0);
          return rv;
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "UnloadImage", new String[] { "ResPath" })
    {
      /**
       * Unloads the specified image resource from memory. NOTE: This does not care about the internal reference
       * count in SageTV for this image which could mean bad things will happen if you use this on images other than ones
       * that you are explicitly managing.
       * @param ResPath the path to the image resource, can be a url, JAR resource path or a file path
       *
       * @declaration public void UnloadImage(String ResPath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MetaImage mi = MetaImage.getMetaImage(getString(stack), stack.getUIComponent());
        if (!mi.isNull(mi))
        {
          MetaImage.clearFromCache(mi.getSource());
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "IsImageLoaded", new String[] { "Image" })
    {
      /**
       * Checks whether the passed in MetaImage (from an API call that returns MetaImage), MediaFile, File, URL or Album is loaded
       * into system memory or into the VRAM cache of the corresponding UI making the call.
       * @param Image the MetaImage to check, or a MediaFile or an Album or a java.io.File or a java.net.URL
       * @return true if the MetaImage (or the MetaImage that would correspond to the passed in resource) is loaded into system memory or the calling UI's VRAM, false otherwise
       * @since 6.1
       *
       * @declaration public boolean IsImageLoaded(Object Image);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o != null)
        {
          if (o instanceof MetaImage)
            return ((MetaImage) o).mightLoadFast(stack.getUIMgr()) ? Boolean.TRUE : Boolean.FALSE;
          else
          {
            MetaImage mi = MetaImage.getMetaImageNoLoad(o);
            return (mi != null && mi.mightLoadFast(stack.getUIMgr())) ? Boolean.TRUE : Boolean.FALSE;
          }
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "DidImageLoadFail", new String[] { "Image" })
    {
      /**
       * Checks whether the passed in MetaImage (from an API call that returns MetaImage), MediaFile, File, URL or Album failed
       * to load successfully. This will return false if the image load has not been attempted yet.
       * @param Image the MetaImage to check, or a MediaFile or an Album or a java.io.File or a java.net.URL
       * @return true if the MetaImage (or the MetaImage that would correspond to the passed in resource) has already tried to load; and the load failed
       * @since 7.0
       *
       * @declaration public boolean DidImageLoadFail(Object Image);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o != null)
        {
          if (o instanceof MetaImage)
            return ((MetaImage) o).isNullOrFailed() ? Boolean.TRUE : Boolean.FALSE;
          else
          {
            MetaImage mi = MetaImage.getMetaImageNoLoad(o);
            return (mi != null && mi.isNullOrFailed()) ? Boolean.TRUE : Boolean.FALSE;
          }
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "DirectoryListing", -1, new String[] { "DirectoryPath" , "MediaMask" }, true)
    {
      /**
       * Returns a list of the files in the specified directory
       * @param DirectoryPath the directory to list the files in
       * @return a list of files in the specified directory
       *
       * @declaration public java.io.File[] DirectoryListing(java.io.File DirectoryPath);
       */

      /**
       * Returns a list of the files in the specified directory. Only directories and file matching the media mask will be returned.
       * @param DirectoryPath the directory to list the files in
       * @param MediaMask the types of content allowed, any combination of 'M'=Music, 'P'=Pictures or 'V'=Videos
       * @return a list of folders and matching files in the specified directory
       * @since 7.0
       *
       * @declaration public java.io.File[] DirectoryListing(java.io.File DirectoryPath, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 2)
        {
          final int mediaMask = getMediaMask(stack);
          java.io.File f = getFile(stack);
          //if (windowsDriveMappings != null && windowsDriveMappings.containsKey(f))
          // return windowsDriveMappings.get(f);
          return f == null ? null : f.listFiles(new java.io.FileFilter()
          {
            public boolean accept(java.io.File path)
            {
              return path.isDirectory() || ((mediaMask & SeekerSelector.getInstance().guessImportedMediaMaskFast(path.getAbsolutePath())) != 0);
            }
          });
        }
        else
        {
          java.io.File f = getFile(stack);
          return f == null ? null : f.listFiles();
        }
      }});
    rft.put(new PredefinedJEPFunction("Utility", "LocalDirectoryListing", new String[] { "DirectoryPath" })
    {
      /**
       * Returns a list of the files in the specified directory on the local filesystem
       * @param DirectoryPath the directory to list the files in
       * @return a list of files in the specified directory
       * @since 6.4
       *
       * @declaration public java.io.File[] LocalDirectoryListing(java.io.File DirectoryPath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        if (stack.getUIMgr() != null && stack.getUIMgr().hasRemoteFSSupport())
        {
          String[] srv = ((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).fsDirListing(f.toString());
          java.io.File[] rv = new java.io.File[(srv == null) ? 0 : srv.length];
          for (int i = 0; i < rv.length; i++)
            rv[i] = new java.io.File(srv[i]);
          return rv;
        }
        else
          return f == null ? null : f.listFiles();
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetFileSystemRoots", true)
    {
      /**
       * Returns the root directories of the file systems (on Linux this'll just be / and on Windows it'll be the drive letters)
       * @return the root directories of the file systems
       *
       * @declaration public java.io.File[] GetFileSystemRoots();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return java.io.File.listRoots();
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetLocalFileSystemRoots")
    {
      /**
       * Returns the root directories of the local file systems  (on Linux this'll just be / and on Windows it'll be the drive letters)
       * @return the root directories of the local file systems
       * @since 6.4
       *
       * @declaration public java.io.File[] GetLocalFileSystemRoots();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().hasRemoteFSSupport())
        {
          String[] srv = ((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).fsGetRoots();
          java.io.File[] rv = new java.io.File[(srv == null) ? 0 : srv.length];
          for (int i = 0; i < rv.length; i++)
            rv[i] = new java.io.File(srv[i]);
          return rv;
        }
        else
          return java.io.File.listRoots();
      }});
    rft.put(new PredefinedJEPFunction("Utility", "StringEndsWith", new String[] { "FullString", "MatchString" })
    {
      /**
       * Returns true if the first string ends with the second, uses java.lang.String.endsWith
       * @param FullString the string to search in
       * @param MatchString the string to search for
       * @return true if FullString ends with MatchString, false otherwise
       *
       * @declaration public boolean StringEndsWith(String FullString, String MatchString);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s2 = getString(stack);
        String s1 = getString(stack);
        return (s1 == null || s2 == null) ? Boolean.FALSE : Boolean.valueOf(s1.endsWith(s2));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "StringStartsWith", new String[] { "FullString", "MatchString" })
    {
      /**
       * Returns true if the first string starts with the second, uses java.lang.String.startsWith
       * @param FullString the string to search in
       * @param MatchString the string to search for
       * @return true if FullString starts with MatchString, false otherwise
       *
       * @declaration public boolean StringStartsWith(String FullString, String MatchString);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s2 = getString(stack);
        String s1 = getString(stack);
        return (s1 == null || s2 == null) ? Boolean.FALSE : Boolean.valueOf(s1.startsWith(s2));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "StringIndexOf", new String[] { "FullString", "MatchString" })
    {
      /**
       * Returns the index of the second string within the first string, -1 if it is not found. Uses java.lang.String.indexOf
       * @param FullString the string to search in
       * @param MatchString the string to search for
       * @return the first 0-based index in FullString that MatchString occurs at or -1 if it is not found
       *
       * @declaration public int StringIndexOf(String FullString, String MatchString);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s2 = getString(stack);
        String s1 = getString(stack);
        return (s1 == null || s2 == null) ? new Integer(-1) : new Integer(s1.indexOf(s2));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "StringLastIndexOf", new String[] { "FullString", "MatchString" })
    {
      /**
       * Returns the last index of the second string within the first string, -1 if it is not found. Uses java.lang.String.lastIndexOf
       * @param FullString the string to search in
       * @param MatchString the string to search for
       * @return the last 0-based index in FullString that MatchString occurs at or -1 if it is not found
       *
       * @declaration public int StringLastIndexOf(String FullString, String MatchString);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s2 = getString(stack);
        String s1 = getString(stack);
        return (s1 == null || s2 == null) ? new Integer(-1) : new Integer(s1.lastIndexOf(s2));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetWorkingDirectory", true)
    {
      /**
       * Returns the current working directory for the application (if this is a client; it'll be the working directory of the server)
       * @return the current working directory for the application
       *
       * @declaration public String GetWorkingDirectory();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return System.getProperty("user.dir");
      }});
    rft.put(new PredefinedJEPFunction("Utility", "HasLocalFilesystem")
    {
      /**
       * Returns true if this client has a local file system that can be accessed.
       * @return true if this client has a local file system that can be accessed
       *
       * @since 6.4
       *
       * @declaration public boolean HasLocalFilesystem();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr != null)
        {
          return (uiMgr.hasRemoteFSSupport() || uiMgr.getUIClientType() == UIClient.LOCAL) ? Boolean.TRUE : Boolean.FALSE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "CreateFilePath", new String[] { "Directory", "File"})
    {
      /**
       * Creates a new file object for the specified directory and file name or relative path
       * @param Directory the directory name
       * @param File the file within the directory or relative file path
       * @return a new file object for the specified directory and file name or relative path
       *
       * @declaration public java.io.File CreateFilePath(String Directory, String File);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s2 = getString(stack);
        String s1 = getString(stack);
        return new java.io.File(s1, s2);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "IsFilePathHidden", new String[] { "FilePath" }, true)
    {
      /**
       * Returns true if the specified file path is marked as a hidden file
       * @param FilePath the file path to test
       * @return true if the specified file path is marked as a hidden file
       *
       * @declaration public boolean IsFilePathHidden(String FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(getFile(stack).isHidden());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "IsLocalFilePathHidden", new String[] { "FilePath" })
    {
      /**
       * Returns true if the specified local file path is marked as a hidden file
       * @param FilePath the file path to test
       * @return true if the specified local file path is marked as a hidden file
       * @since 6.4
       *
       * @declaration public boolean IsLocalFilePathHidden(String FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().hasRemoteFSSupport())
        {
          return Boolean.valueOf((((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).
              fsGetPathAttributes(getString(stack)) & MiniClientSageRenderer.FS_PATH_HIDDEN) != 0);
        }
        else
          return Boolean.valueOf(getFile(stack).isHidden());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "IsFilePath", new String[] { "FilePath" }, true)
    {
      /**
       * Returns true if the specified file path denotes a file that exists and is not a directory
       * @param FilePath the file path to test
       * @return true if the specified file path denotes a file that exists and is not a directory
       *
       * @declaration public boolean IsFilePath(String FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        return Boolean.valueOf(f != null && f.isFile());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "IsLocalFilePath", new String[] { "FilePath" })
    {
      /**
       * Returns true if the specified local file path denotes a file that exists and is not a directory
       * @param FilePath the file path to test
       * @return true if the specified local file path denotes a file that exists and is not a directory
       * @since 6.4
       *
       * @declaration public boolean IsLocalFilePath(String FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().hasRemoteFSSupport())
        {
          return Boolean.valueOf((((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).
              fsGetPathAttributes(getString(stack)) & MiniClientSageRenderer.FS_PATH_FILE) != 0);
        }
        else
          return Boolean.valueOf(getFile(stack).isFile());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "IsDirectoryPath", new String[] { "DirectoryPath" }, true)
    {
      /**
       * Returns true if the specified path denotes a directory that exists
       * @param FilePath the file path to test
       * @return true if the specified path denotes a directory that exists
       *
       * @declaration public boolean IsDirectoryPath(String FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        return Boolean.valueOf(f != null && f.isDirectory());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "IsLocalDirectoryPath", new String[] { "DirectoryPath" })
    {
      /**
       * Returns true if the specified local path denotes a directory that exists
       * @param FilePath the file path to test
       * @return true if the specified local path denotes a directory that exists
       * @since 6.4
       *
       * @declaration public boolean IsLocalDirectoryPath(String FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().hasRemoteFSSupport())
        {
          return Boolean.valueOf((((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).
              fsGetPathAttributes(getString(stack)) & MiniClientSageRenderer.FS_PATH_DIRECTORY) != 0);
        }
        else
          return Boolean.valueOf(getFile(stack).isDirectory());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "CreateNewDirectory", new String[] { "DirectoryPath" }, true)
    {
      /**
       * Creates a new directory and any parent directories for the specified directory path.
       * @param DirectoryPath the directory to create
       * @return true if successful, false otherwise
       *
       * @declaration public boolean CreateNewDirectory(java.io.File DirectoryPath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(getFile(stack).mkdirs());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "CreateNewLocalDirectory", new String[] { "DirectoryPath" })
    {
      /**
       * Creates a new local directory and any parent directories for the specified directory path.
       * @param DirectoryPath the directory to create
       * @return true if successful, false otherwise
       * @since 6.4
       *
       * @declaration public boolean CreateNewLocalDirectory(java.io.File DirectoryPath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().hasRemoteFSSupport())
        {
          return Boolean.valueOf(((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).
              fsCreateDirectory(getString(stack)) == MiniClientSageRenderer.FS_RV_SUCCESS);
        }
        else
          return Boolean.valueOf(getFile(stack).mkdirs());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetPathParentDirectory", new String[] { "FilePath" })
    {
      /**
       * Returns the parent directory for the specified file path
       * @param FilePath the file path to get the parent directory for
       * @return the parent directory for the specified file path
       *
       * @declaration public java.io.File GetPathParentDirectory(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return getFile(stack).getParentFile();
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetPathLastModifiedTime", new String[] { "FilePath" }, true)
    {
      /**
       * Returns the last modified time of the specified file path
       * @param FilePath the file path
       * @return the last modified time of the specified file path
       *
       * @declaration public long GetPathLastModifiedTime(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(getFile(stack).lastModified());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetLocalPathLastModifiedTime", new String[] { "FilePath" })
    {
      /**
       * Returns the last modified time of the specified local file path
       * @param FilePath the file path
       * @return the last modified time of the specified local file path
       * @since 6.4
       *
       * @declaration public long GetLocalPathLastModifiedTime(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().hasRemoteFSSupport())
        {
          return new Long(((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).
              fsGetPathModified(getString(stack)));
        }
        else
          return new Long(getFile(stack).lastModified());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetFilePathSize", new String[] { "FilePath" }, true)
    {
      /**
       * Returns the size in bytes of the specified file path
       * @param FilePath the file path
       * @return the size in bytes of the specified file path
       * @since 6.4
       *
       * @declaration public long GetFilePathSize(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Long(getFile(stack).length());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetLocalFilePathSize", new String[] { "FilePath" })
    {
      /**
       * Returns the size in bytes of the specified local file path
       * @param FilePath the file path
       * @return the size in bytes of the specified local file path
       * @since 6.4
       *
       * @declaration public long GetLocalFilePathSize(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().hasRemoteFSSupport())
        {
          return new Long(((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).
              fsGetFileSize(getString(stack)));
        }
        else
          return new Long(getFile(stack).length());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "DeleteFilePath", new String[] { "FilePath" }, true)
    {
      /**
       * Deletes the file/directory at the corresponding file path (directories must be empty first)
       * @param FilePath the file path
       * @return true if successful, false otherwise
       * @since 6.3.9
       *
       * @declaration public boolean DeleteFilePath(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(getFile(stack).delete());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "DeleteLocalFilePath", new String[] { "FilePath" })
    {
      /**
       * Deletes the file/directory at the corresponding local file path (directories must be empty first)
       * @param FilePath the file path
       * @return true if successful, false otherwise
       * @since 6.4
       *
       * @declaration public boolean DeleteLocalFilePath(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (stack.getUIMgr() != null && stack.getUIMgr().hasRemoteFSSupport())
        {
          return new Boolean(((MiniClientSageRenderer) stack.getUIMgr().getRootPanel().getRenderEngine()).
              fsDeletePath(getString(stack)));
        }
        else
          return Boolean.valueOf(getFile(stack).delete());
      }});
    rft.put(new PredefinedJEPFunction("Utility", "RenameFilePath", new String[] { "OriginalFilePath", "NewFilePath" }, true)
    {
      /**
       * Renames a file/directory
       * @param OriginalFilePath the file path to rename
       * @param NewFilePath the new name for the file path
       * @return true if successful, false otherwise
       * @since 6.3.9
       *
       * @declaration public boolean RenameFilePath(java.io.File OriginalFilePath, java.io.File NewFilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File newPath = getFile(stack);
        java.io.File orgPath = getFile(stack);
        return Boolean.valueOf(orgPath.renameTo(newPath));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "AddToGrouping", new String[] { "Grouping", "Key", "Value" })
    {
      /**
       * Adds the specified value into the grouping using the specified key. Useful on results from {@link Database#GroupByMethod GroupByMethod()}
       * This works using a Map implementation that has Collections as the values and objects as the keys. So if two objects have the same key
       * they will both still exist in the map by being in the Collection that corresponds to their key.
       * @param Grouping the grouping (Map) to add the new key/value pair to
       * @param Key the key to use to store the value in the map
       * @param Value the value to store
       * @return true is always returned
       *
       * @declaration public boolean AddToGrouping(java.util.Map Grouping, Object Key, Object Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object v = stack.pop();
        Object k = stack.pop();
        java.util.Map group = (java.util.Map) stack.pop();
        java.util.Collection c = (java.util.Collection) group.get(k);
        if (c != null)
          c.add(v);
        else
        {
          c = new java.util.Vector();
          c.add(v);
          group.put(k, c);
        }
        return Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "SendNetworkCommand", new String[] { "Hostname", "Port", "Command" })
    {
      /**
       * Opens a TCP/IP socket connection to the specified hostname on the specified port and then sends the specified command. After that
       * the socket is closed.
       * @param Hostname the hostname to connect to
       * @param Port the port to connect on
       * @param Command either a byte[] or a String to send across the socket
       * @return true if successful, false otherwise
       *
       * @declaration public boolean SendNetworkCommand(String Hostname, int Port, Object Command);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object cmd = stack.pop();
        int port = getInt(stack);
        String host = getString(stack);
        java.net.Socket sock = null;
        java.io.OutputStream sockOut = null;
        java.io.PrintWriter pw = null;
        try
        {
          sock = new java.net.Socket(host, port);
          if (cmd != null)
          {
            sockOut = sock.getOutputStream();
            if (cmd instanceof byte[])
            {
              sockOut.write((byte[])cmd);
            }
            else
            {
              pw = new java.io.PrintWriter(sockOut);
              pw.print(cmd.toString());
            }
            sockOut.flush();
          }
        }
        catch (Exception e)
        {
          return Boolean.FALSE;
        }
        finally
        {
          try
          {
            if (pw != null)
              pw.close();
            if (sockOut != null)
              sockOut.close();
            if (sock != null)
              sock.close();
          }
          catch (Exception e1){}
        }
        return Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "ScaleBufferedImage", new String[] { "JavaBufferedImage", "Width", "Height", "Alpha" })
    {
      /**
       * Scales a java.awt.image.BufferedImage object using optimized techniques
       * @param JavaBufferedImage the BufferedImage object that is the source for the scaling
       * @param Width the width of the target image
       * @param Height the height of the target image
       * @param Alpha true if the scaling should be done in ARGB, false if it should be done in RGB
       * @return a new BufferedImage that is a scaled version of the specified image
       *
       * @declaration public java.awt.image.BufferedImage ScaleBufferedImage(java.awt.image.BufferedImage JavaBufferedImage, int Width, int Height, boolean Alpha);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean alpha = evalBool(stack.pop());
        int h = getInt(stack);
        int w = getInt(stack);
        java.awt.image.BufferedImage bi = (java.awt.image.BufferedImage) stack.pop();
        return alpha ? ImageUtils.createBestScaledImage(bi, w, h) : ImageUtils.createBestOpaqueScaledImage(bi, w, h);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "LocalizeString", new String[] { "EnglishText" })
    {
      /**
       * Returns a localized version of the specified string. Uses SageTV's core translation properties to do this.
       * @param EnglishText the English string to translate from
       * @return the translated version of the specified string in the currently configured language
       *
       * @declaration public String LocalizeString(String EnglishText);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Sage.rez(getString(stack));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetLocalIPAddress")
    {
      /**
       * Returns the IP address of the machine
       * @return the IP address of the machine
       *
       * @declaration public String GetLocalIPAddress();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        try
        {
          if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
            return java.net.InetAddress.getLocalHost().getHostAddress();
          else
            return LinuxUtils.getIPAddress();
        }catch(Throwable e)
        {
          System.out.println("ERROR:" + e);
        }
        return "0.0.0.0";
      }});
    rft.put(new PredefinedJEPFunction("Utility", "IsImportableFileType", new String[] { "Filename" })
    {
      /**
       * Returns true if the specified file path has a file extension which would be imported by SageTV into its library.
       * @param Filename the file path to test
       * @return true if the specified file path has a file extension which would be imported by SageTV into its library, false otherwise
       *
       * @declaration public boolean IsImportableFileType(String Filename);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(SeekerSelector.getInstance().hasImportableFileExtension(getString(stack)));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetSubnetMask")
    {
      /**
       * Returns the subnet mask for the currently configured network adapter.
       * NOTE: This is only valid on embedded platforms.
       * @return the subnet mask for the currently configured network adapter
       *
       * @declaration public String GetSubnetMask();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return "255.255.255.0";
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetGatewayAddress")
    {
      /**
       * Returns the gateway address for the currently configured network adapter.
       * NOTE: This is only valid on embedded platforms.
       * @return the gateway address for the currently configured network adapter
       *
       * @declaration public String GetGatewayAddress();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return "0.0.0.0";
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetDNSAddress")
    {
      /**
       * Returns the DNS address for the currently configured network adapter.
       * NOTE: This is only valid on embedded platforms.
       * @return the DNS address for the currently configured network adapter
       *
       * @declaration public String GetDNSAddress();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return "0.0.0.0";
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GuessMajorFileType", new String[] { "Filename" }, true)
    {
      /**
       * Guesses what media type the specified filename corresponds to. It does this based on the configuration
       * for the import library file types.
       * @param Filename the file path to test
       * @return "M", "V", "P", "B" or "D" for a music, video, picture, BluRay or DVD file respectively; if it can't tell it returns "V"
       *
       * @since 6.4
       *
       * @declaration public String GuessMajorFileType(String Filename);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        switch (SeekerSelector.getInstance().guessImportedMediaMaskFast(getString(stack)))
        {
          case DBObject.MEDIA_MASK_DVD:
            return "D";
          case DBObject.MEDIA_MASK_PICTURE:
            return "P";
          case DBObject.MEDIA_MASK_MUSIC:
            return "M";
          case DBObject.MEDIA_MASK_BLURAY:
            return "B";
          default:
            return "V";
        }
      }});
    rft.put(new PredefinedJEPFunction("Utility", "TestPlaceshifterConnectivity", new String[] { "LocatorID" })
    {
      /**
       * Connects to the SageTV Locator server and submits the specified Locator ID for a 'ping'. The Locator server will
       * then attempt to connect to the IP for that GUID and report back the status.  The return code is an integer as follows:
       * -1 - Unable to connect to the locator server (internet connection is down or locator server is down)
       * 0 - The locator server did not have an IP address registered for this GUID
       * 1 - The locator server could not connect to the IP address registered for the GUID
       * 2 - The locator server can connect to the IP address registered for the GUID, but not to the Placeshifter port
       * 3 - The locator server can connect to the IP address/port for the GUID, but the server that is there is not the Placeshifter
       * 10 - The ping was successful. External connections to the Placeshifter should work correctly.
       * @param LocatorID the GUID that should be used for the 'ping'
       * @return an integer status code as described above.
       *
       * @declaration public int TestPlaceshifterConnectivity(String LocatorID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        try
        {
          return new Integer(sage.locator.LocatorLookupClient.haveLocatorPingID(getString(stack)));
        }
        catch (Exception e)
        {
          return new Integer(-1);
        }
      }});
    rft.put(new PredefinedJEPFunction("Utility", "LookupIPForLocatorID", new String[] { "LocatorID" })
    {
      /**
       * Connects to the SageTV Locator server and submits the specified Locator ID for a IP lookup. The Locator server will
       * then lookup the IP for that GUID and report it back.
       * @param LocatorID the GUID that should be used for the lookup
       * @return an String of IP address:port or null if the lookup failed
       *
       * @declaration public String LookupIPForLocatorID(String LocatorID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        try
        {
          return sage.locator.LocatorLookupClient.lookupIPForGuid(getString(stack));
        }
        catch (Exception e)
        {
          return null;
        }
      }});
    rft.put(new PredefinedJEPFunction("Utility", "CreateArray", -1, new String[] { "Value" })
    {
      /**
       * Creates a java.lang.Object array and initializes each element to the passed in argument.
       * NOTE: This method takes a variable number of arguments, and the length of the returned array will be
       * equal to the number of arguments. i.e. calling CreateArray(1, 2) returns an Object array with elements 1 and 2 in it
       * @param Value a value for an element of the array (multiple arguments allowed)
       * @return the newly allocated Object array with its elements set to the arguments
       *
       * @since 6.0
       *
       * @declaration public Object[] CreateArray(Object Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int length = curNumberOfParameters;
        Object[] rv = new Object[length];
        for (int i = length - 1; i >= 0; i--)
          rv[i] = stack.pop();
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "SetScrollPosition", new String[] { "RelativeX", "RelativeY"})
    {
      /**
       * Scrolls the closest pageable UI parent component (or sibling of a parent) to the specified position.
       * @param RelativeX the X position to scroll to between 0.0 and 1.0 (use a negative number to not change the X position)
       * @param RelativeY the Y position to scroll to between 0.0 and 1.0 (use a negative number to not change the Y position)
       *
       * @since 6.2
       *
       * @declaration public void SetScrollPosition(float RelativeX, float RelativeY);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        float y = getFloat(stack);
        float x = getFloat(stack);
        if (stack.getUIComponent() != null && (x >= 0 || y >= 0)) // if they're both negative that's a noop
        {
          stack.getUIMgr().getRouter().updateLastEventTime();
          stack.getUIComponent().setOverallScrollLocation(x, y, true);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "ClearMenuCache")
    {
      /**
       * Clears the cache that links Widgets to the in memory-menu representations for this UI. This also clears the back/forward history
       * to remove any references contained in there as well.
       *
       * @since 6.2
       *
       * @declaration public void ClearMenuCache();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr != null)
          uiMgr.clearMenuCache();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "Animate", new String[] { "WidgetName", "LayerName", "AnimationName", "Duration"})
    {
      /**
       * Starts an animation for the specified Widget in the specified Layer. If the Widget name ends with a '*' then all Widgets
       * that match will be animated; otherwise only the first visible Widget matching the name will be animated. The Widget must
       * also have the specified Layer as it's animation layer (i.e. if the Layer is Foreground, then the corresponding Widget
       * should have an Animation property of LayerForeground). The type of animation is controlled by AnimtionName. There's
       * also suffixes that can be appened to the AnimationName that effect how the timescale for the animation progresses.
       * There's also other suffixes that can be used to specify other options for the animations.
       * <p>
       * Valid strings for the AnimationName are:<br>
       * <ul><li>
       * FullSlideDownOut - slides down off the bottom of the screen</li><li>
       * FullSlideDownIn - slides down in from the top of the screen</li><li>
       * FullSlideUpOut - slides up off the top of the screen</li><li>
       * FullSlideUpIn - slides up in from the bottom of the screen</li><li>
       * FullSlideLeftOut - slides off to the left of the screen</li><li>
       * FullSlideLeftIn - slides in from the left of the screen</li><li>
       * FullSlideRightOut - slides off the right of the screen</li><li>
       * FullSlideRightIn - slides in from the right of the screen</li><li>
       * SlideDownOut - slides down off the bottom of its parent component</li><li>
       * SlideDownIn - slides down in from the top of its parent component</li><li>
       * SlideUpOut - slides up off the top of its parent component</li><li>
       * SlideUpIn - slides up in from the bottom of its parent component</li><li>
       * SlideLeftOut - slides off to the left of its parent component</li><li>
       * SlideLeftIn - slides in from the left of its parent component</li><li>
       * SlideRightOut - slides off the right of its parent component</li><li>
       * SlideRightIn - slides in from the right of its parent component</li><li>
       * FadeOut - fades out</li><li>
       * FadeIn - fades in</li><li>
       * Smooth - smoothly transitions from one position &amp; size to another; the destination image is used for the animation</li><li>
       * Morph - smoothly transitions from one position &amp; size to another; the image fades between the source and the destination</li><li>
       * ZoomOut - shrinks the size down to nothing from its source size</li><li>
       * ZoomIn - grows the size from nothing to its destination size</li><li>
       * HZoomOut - shrinks the size down to nothing horitonzatlly from its source size</li><li>
       * HZoomIn - grows the size from nothing horitonzatlly to its destination size</li><li>
       * VZoomOut - shrinks the size down to nothing vertically from its source size</li><li>
       * VZoomIn - grows the size from nothing vertically to its destination size</li>
       * </ul><p>
       * Timeline modifications for animations affect how the timescale progresses. For out animations, they are eased out if non-linear.
       * For in animations, they are eased in if non-linear. For animations that are neither; the timescale modification occurs at both ends.
       * Bounce only works properly for 'in' animations.
       * <p>
       * Valid suffixes for any of the animations are (default is Quadratic):<br><ul><li>
       * Linear - animation follows a smooth timeline (first order)</li><li>
       * Quadratic - animation follows a quadratic timeline (second order)</li><li>
       * Cubic - animation follows a cubic timeline (third order)</li><li>
       * Bounce - animation follows a timeline that looks like it 'bounces' in</li></ul>
       *<p>
       * Additional options for the animation may also be specified by combining additional suffixes to the
       * AnimationName. The following is a list of valid option suffixes. <br><ul><li>
       * Fade - applies an additional fade in/out effect to the animation (i.e. ZoomOutFade) </li><li>
       * North - for Zoom animations will center the zoom around the top of the component (i.e. ZoomInNorth) </li><li>
       * West - for Zoom animations will center the zoom around the left of the component </li><li>
       * South - for Zoom animations will center the zoom around the bottom of the component </li><li>
       * East - for Zoom animations will center the zoom around the right of the component </li><li>
       * Behind - for Out animations will cause it to be rendered behind the other layers instead of on top as Out animations usually are </li><li>
       * Unclipped - for Slide animations will cause the same motion to occur but without clipping the area when drawn </li><li>
       * Unease - for In or Out animations it will reverse the 'easing' direction so you can slide in &amp; out the same panel w/ out overlap </li></ul>
       *<p>
       * You may combine the directional suffixes to get an additional four directions (i.e. ZoomOutNorthEast). And this
       * can also be combined with the timeline suffixes as well, or even Fade (i.e. ZoomInQuadraticSouthWestFade)
       * <br>
       * For delaying the start of an animation; see here {@link #AnimateDelayed AnimateDelayed()}
       *
       * @param WidgetName the name of the Widget that should be animated
       * @param LayerName the name of the Layer the animated Widget must be in
       * @param AnimationName the name of the animation to perform
       * @param Duration the time in milliseconds that it should take for the animation to complete
       * @return returns true if a matching Widget was found to perform the animation on; false otherwise
       *
       * @since 6.2
       *
       * @declaration public boolean Animate(String WidgetName, String LayerName, String AnimationName, long Duration);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long dur = getLong(stack);
        String animName = getString(stack);
        String surfName = getString(stack);
        String widgName = getString(stack);
        UIManager uiMgr = stack.getUIMgrSafe();
        if (uiMgr == null || !uiMgr.areLayersEnabled()) return Boolean.FALSE;
        PseudoMenu currUI = uiMgr.getCurrUI();
        if (currUI != null)
          return currUI.setupAnimation(widgName, surfName, animName, dur, 0, false) ? Boolean.TRUE : Boolean.FALSE;
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "AnimateVariable", -1, new String[] { "WidgetName", "LayerName", "VarName", "VarValue", "AnimationName", "Duration", "StartDelay", "Interruptable"})
    {
      /**
       * For more details on Animations see here: {@link #Animate Animate()}
       *
       * In addition to what's specified in the Animate API call; this also offers restricting of an
       * Animation by a variable name and value. Usage of the '*' suffix on the WidgetName is allowed.
       *
       * @param WidgetName the name of the Widget that should be animated
       * @param LayerName the name of the Layer the animated Widget must be in
       * @param VarName the name of the variable that must match for the Widget to be animated
       * @param VarValue the value of the variable to match
       * @param AnimationName the name of the animation to perform
       * @param Duration the time in milliseconds that it should take for the animation to complete
       * @param StartDelay the delay in milliseconds before this animation should start
       * @param Interruptable true if the animation can be interrupted to render the next UI update; false if it must complete (this parameter is optional and defaults to false)
       * @return returns true if a matching Widget was found to perform the animation on; false otherwise
       * @since 6.3
       *
       * @declaration public boolean AnimateVariable(String WidgetName, String LayerName, String VarName, Object VarValue, String AnimationName, long Duration, long StartDelay, boolean Interruptable);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean interruptable = false;
        if (curNumberOfParameters == 8)
          interruptable = getBool(stack);
        long delay = getLong(stack);
        long dur = getLong(stack);
        String animName = getString(stack);
        Object value = stack.pop();
        String varName = getString(stack);
        String layer = getString(stack);
        String widgName = getString(stack);
        UIManager uiMgr = stack.getUIMgrSafe();
        if (uiMgr == null || !uiMgr.areLayersEnabled()) return Boolean.FALSE;
        PseudoMenu currUI = uiMgr.getCurrUI();
        if (currUI != null)
          return currUI.setupAnimationVar(widgName, layer, varName, value, animName, dur, delay, interruptable) ? Boolean.TRUE : Boolean.FALSE;

        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "AnimateTransition", -1, new String[] { "SourceWidgetName", "TargetWidgetName", "LayerName", "AnimationName", "Duration", "StartDelay", "Interruptable"})
    {
      /**
       * Performs an Animation between two different Widgets. Normally animations are performed between two different states for a single Widget.
       * This API call allows an animation to occur between two different Widgets and will usually be used with a 'Morph' AnimationName. This
       * may only target a single Widget; so the '*' suffix is not used on the WidgetNames in this call.
       *<br>
       * For more details on Animations see here: {@link #Animate Animate()}
       *<br>
       * @param SourceWidgetName the name of the Widget to use as the source for this animation
       * @param TargetWidgetName the name of the Widget to use as the target (destination) for this animation
       * @param LayerName the name of the Layer the animated Widget must be in
       * @param AnimationName the name of the animation to perform
       * @param Duration the time in milliseconds that it should take for the animation to complete
       * @param StartDelay the delay in milliseconds before this animation should start
       * @param Interruptable true if the animation can be interrupted to render the next UI update; false if it must complete (this parameter is optional and defaults to false)
       * @return returns true if a matching Widget was found to perform the animation on; false otherwise
       *
       * @since 6.3
       *
       * @declaration public boolean AnimateTransition(String SourceWidgetName, String TargetWidgetName, String LayerName, String AnimationName, long Duration, long StartDelay, boolean Interruptable);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean interruptable = false;
        if (curNumberOfParameters == 7)
          interruptable = getBool(stack);
        long delay = getLong(stack);
        long dur = getLong(stack);
        String animName = getString(stack);
        String surfName = getString(stack);
        String dstwidgName = getString(stack);
        String srcwidgName = getString(stack);
        UIManager uiMgr = stack.getUIMgrSafe();
        if (uiMgr == null || !uiMgr.areLayersEnabled()) return Boolean.FALSE;
        PseudoMenu currUI = uiMgr.getCurrUI();
        if (currUI != null)
          return currUI.setupTransitionAnimation(srcwidgName, dstwidgName, surfName, animName, dur, delay, interruptable) ? Boolean.TRUE : Boolean.FALSE;

        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "AnimateDelayed", -1, new String[] { "WidgetName", "LayerName", "AnimationName", "Duration", "StartDelay", "Interruptable"})
    {
      /**
       * This is the same as the Animate API call; but it allows specifiying a delay that should occur before the animation actually starts.
       * Useful for creating sequences of animation effects.
       *
       * For more details see here: {@link #Animate Animate()}
       *
       * @param WidgetName the name of the Widget that should be animated
       * @param LayerName the name of the Layer the animated Widget must be in
       * @param AnimationName the name of the animation to perform
       * @param Duration the time in milliseconds that it should take for the animation to complete
       * @param StartDelay the delay in milliseconds before this animation should start
       * @param Interruptable true if the animation can be interrupted to render the next UI update; false if it must complete (this parameter is optional and defaults to false)
       * @return returns true if a matching Widget was found to perform the animation on; false otherwise
       *
       * @since 6.2
       *
       * @declaration public boolean AnimateDelayed(String WidgetName, String LayerName, String AnimationName, long Duration, long StartDelay, boolean Interruptable);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean interruptable = false;
        if (curNumberOfParameters == 6)
          interruptable = getBool(stack);
        long delay = getLong(stack);
        long dur = getLong(stack);
        String animName = getString(stack);
        String surfName = getString(stack);
        String widgName = getString(stack);
        UIManager uiMgr = stack.getUIMgrSafe();
        if (uiMgr == null || !uiMgr.areLayersEnabled()) return Boolean.FALSE;
        PseudoMenu currUI = uiMgr.getCurrUI();
        if (currUI != null)
          return currUI.setupAnimation(widgName, surfName, animName, dur, delay, interruptable) ? Boolean.TRUE : Boolean.FALSE;

        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "SetCoreAnimationsEnabled", new String[] { "Enabled" })
    {
      /**
       * Sets whether or not animation support is enabled (either layered or Effect based animations; depending upon the STV configuration)
       * @param Enabled true to enable core animations; false otherwise
       *
       * @since 6.2
       *
       * @declaration public void SetCoreAnimationsEnabled(boolean Enabled);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        boolean newVal = evalBool(stack.pop());
        if (uiMgr != null)
          uiMgr.setCoreAnimationsEnabled(newVal);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "AreCoreAnimationsEnabled")
    {
      /**
       * Returns whether or not animation support is enabled (either layered or Effect based animations; depending upon the STV configuration)
       * @return true if core animations are enabled; false otherwise
       *
       * @since 6.2
       *
       * @declaration public boolean AreCoreAnimationsEnabled();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        return (uiMgr == null || !uiMgr.areCoreAnimationsEnabled()) ? Boolean.FALSE : Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "AreCoreAnimationsSupported")
    {
      /**
       * Returns whether or not animation support is possible in the current UI environment. Certain clients (like the MVP) do not support animations;
       * and animations over remote connections are also disabled due to performance reasons.
       * @return true if core animations are supported; false otherwise
       *
       * @since 7.0
       *
       * @declaration public boolean AreCoreAnimationsSupported();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        return (uiMgr == null || uiMgr.getRootPanel() == null || uiMgr.getRootPanel().getRenderEngine() == null ||
            !uiMgr.getRootPanel().getRenderEngine().canSupportAnimations()) ? Boolean.FALSE : Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetUIRefreshLock")
    {
      /**
       * Acquires the lock for this user interface system to prevent other updates from occuring. This can be used
       * at the start of an animation sequence before the refresh call is made to ensure that the animations will
       * all occur on the same refresh cycle. The return value indicates if the lock was acquired. Do NOT release the lock
       * unless you acquired the lock. This lock is re-entrant and is thread-based. You must release it from the same
       * thread that acquired the lock. If this method return false, then you already have the lock.
       * IMPORTANT: It is of CRITICAL IMPORTANCE that ReleaseUIRefreshLock() is called after GetUIRefreshLock() if
       * this method returns true or the user interface system will become completely locked up for this client. It's also
       * important to not release the lock unless you acquired it.
       * @return true if the lock was acquired (which means it MUST be released), false if it was not
       *
       * @since 6.4
       *
       * @declaration public boolean GetUIRefreshLock();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        return uiMgr.getLock(true, null) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "ReleaseUIRefreshLock")
    {
      /**
       * Releases the lock for this user interface system to allow other updates to occur. This must ONLY be used
       * after GetUIRefreshLock() was called and ONLY if GetUIRefreshLock() actually returned true. This must also be called
       * from the same thread that called GetUIRefreshLock()
       *
       * @since 6.4
       *
       * @declaration public void ReleaseUIRefreshLock();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        uiMgr.clearLock(true, false);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "CalculateMD5Sum", new String[] { "FilePath"})
    {
      /**
       * Calculates the MD5 Sum of a given file
       * @param FilePath the path to the file who's MD sum should be calculated
       * @return the MD5 sum of the specified file as a String, null if the file doesn't exist or there's an error reading it
       *
       * @since 6.3
       *
       * @declaration public String CalculateMD5Sum(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        return IOUtils.calcMD5(f);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "CalculateSHA1Hash", new String[] { "EncodeString"})
    {
      /**
       * Calculates the SHA1 hash of a String
       * @param EncodeString the String to be converted into a SHA1 hash
       * @return the SHA1 sum of the provided String or null if the string was null
       * @since 9.0
       *
       * @declaration public String CalculateSHA1Hash(String EncodeString);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String encodeString = getString(stack);
        return IOUtils.calcSHA1(encodeString);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "ReloadNameserverCache")
    {
      /**
       * Reloads the name server cache. Should be used after reconfiguring the network adapter.
       * NOTE: This is only valid on embedded platforms.
       *
       * @declaration public void ReloadNameserverCache();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        //				CVMUtils.reloadNameserverCache();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetTimeSinceLastInput")
    {
      /**
       * Returns the amount of time in milliseconds since the last user input occurred for this UI (used for doing things while the user is idle)
       * @return the amount of time in milliseconds since the last user input occurred
       *
       * @since 6.6
       *
       * @declaration public long GetTimeSinceLastInput();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null)
          return new Long(0);
        else
          return new Long(Math.max(0, Sage.eventTime() - uiMgr.getRouter().getLastEventTime()));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetFileAsString", new String[] { "FilePath" }, true)
    {
      /**
       * Opens the file at the specified path and reads the entire contents of it and returns it as a String.
       * This will use the server's filesystem if executed on SageTVClient.
       * @param FilePath the file path
       * @return a String which represents the contents of the file; the emptry string if there was a failure
       * @since 6.6
       *
       * @declaration public String GetFileAsString(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        // NOTE: There's only one file that's currently allowed to be read using this API call in a secure environment.
        // If we need more later, then they should be added here.
        return IOUtils.getFileAsString(f);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "GetLocalFileAsString", new String[] { "FilePath" })
    {
      /**
       * Opens the file at the specified path and reads the entire contents of it and returns it as a String.
       * @param FilePath the file path
       * @return a String which represents the contents of the file; the emptry string if there was a failure
       * @since 8.0
       *
       * @declaration public String GetLocalFileAsString(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return IOUtils.getFileAsString(getFile(stack));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "WriteStringToFile", new String[] { "FilePath", "Data" }, true)
    {
      /**
       * Opens the file at the specified path and writes out the specified String as its contents.
       * This will use the server's filesystem if executed on SageTVClient.
       * @param FilePath the file path
       * @param Data the contents to write to the file
       * @return true if successful, false if there was an error writing to the file
       * @since 9.0
       *
       * @declaration public boolean WriteStringToFile(java.io.File FilePath, String Data);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        java.io.File f = getFile(stack);
        return IOUtils.writeStringToFile(f, s);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "WriteStringToLocalFile", new String[] { "FilePath", "Data" })
    {
      /**
       * Opens the file at the specified path and writes out the specified String as its contents.
       * @param FilePath the file path
       * @param Data the contents to write to the file
       * @return true if successful, false if there was an error writing to the file
       * @since 9.0
       *
       * @declaration public boolean WriteStringToLocalFile(java.io.File FilePath, String Data);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        java.io.File f = getFile(stack);
        return IOUtils.writeStringToFile(f, s);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "IsLocalRestartNeeded")
    {
      /**
       * Returns true if the local instance of SageTV needs to be restarted due to a plugin install/uninstall
       * @return true if the local instance of SageTV needs to be restarted due to a plugin install/uninstall, false otherwise
       * @since 7.0
       *
       * @declaration public boolean IsLocalRestartNeeded();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return sage.plugin.CorePluginManager.getInstance().isRestartNeeded() ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "IsServerRestartNeeded", true)
    {
      /**
       * Returns true if the server instance of SageTV needs to be restarted due to a plugin install/uninstall
       * @return true if the server instance of SageTV needs to be restarted due to a plugin install/uninstall, false otherwise
       * @since 7.0
       *
       * @declaration public boolean IsServerRestartNeeded();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return sage.plugin.CorePluginManager.getInstance().isRestartNeeded() ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "Restart")
    {
      /**
       * Restarts the local instance of SageTV. Sometimes needed after a plugin install/uninstall. If you want to restart
       * the local and server instance; then perform the restart on the server first. This is only supported on
       * Windows and Linux currently. If this is called from a SageTVClient running on the same machine as the server, this will invoke
       * a restart of the locally running server as well in order to ensure proper file upgrade synchronization.
       * @return true if restarting is supported (Although the restart will likely complete and the method will never return), false otherwise (Mac OS X does not have restart support)
       * @since 7.0
       *
       * @declaration public boolean Restart();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        try
        {
          // Since we may get exceptions in this API call returning properly from forcing a server restart as well
          if (Sage.client && !Sage.isNonLocalClient())
          {
            // When we call into the server for this; it'll invoke a callback into us that notifies us to restart as well
            makeNetworkedCall(stack);
          }
        }
        catch (Throwable t){}
        SageTV.restart();
        return Sage.MAC_OS_X ? Boolean.FALSE : Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "ServerRestart", true)
    {
      /**
       * Restarts the server instance of SageTV. Sometimes needed after a plugin install/uninstall. If you want to restart
       * the local and server instance; then perform the restart on the server first. This is only supported on
       * Windows servers and Linux servers currently.
       * @return true if restarting is supported (Although the restart will likely complete and the method will never return), false otherwise (Mac OS X server does not have restart support)
       * @since 7.0
       *
       * @declaration public boolean ServerRestart();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SageTV.restart();
        return Sage.MAC_OS_X ? Boolean.FALSE : Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("Utility", "QueryServerMacAddress", new String[] { "Hostname" })
    {
      /**
       * Gets the MAC address of the SageTV server at the specified hostname. This will only work if SageTV is running on that host.
       * This call uses a 3 second timeout internally.
       * @param Hostname the hostname/IP of the SageTV server
       * @return a String in the format 00:xx:xx:xx:xx:xx that represents the MAC of the server, or null if it fails
       * @since 7.0
       *
       * @declaration public String QueryServerMacAddress(String Hostname);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return IOUtils.getServerMacAddress(getString(stack));
      }});
    rft.put(new PredefinedJEPFunction("Utility", "ScanWirelessAPs", true)
    {
      /**
       * Scans for wireless access points and returns the results as a map. The keys are the SSID names and the values are Security;Strength where
       * Security will be WEP/WPA/None and strength will be an integer between 0 and 100
       * NOTE: This is only valid on embedded platforms.
       * @return a Map describing the results of the access point scan
       * @since 6.6
       *
       * @declaration public java.util.Map ScanWirelessAPs();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new java.util.HashMap();
      }});
    rft.put(new PredefinedJEPFunction("Utility", "ReformatDeviceAtPathAsEXT3", 1, new String[]{"DrivePath"}, true)
    {
      /**
       * Determines the device that is mounted at the specified path, and then repartitions it to have a single EXT3 partition and then
       * formats that partition. WARNING: THIS WILL DESTROY ALL INFORMATION ON THE TARGET DEVICE AND REFORMAT IT
       * NOTE: This is only valid on embedded platforms.
       * @param DrivePath the path string of a disk to reformat
       * @return zero upon success, -1 if it is unable to find a device that corresponds to the requested path, -2 if it is unable to unmount that path, -3 if there was a problem re-partitioning or reformatting the drive, and -4 if there was a failure remounting the newly formatted drive
       * @since 7.1
       *
       * @declaration public int ReformatDeviceAtPathAsEXT3(String DrivePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(-1);
      }});
    rft.put(new PredefinedJEPFunction("Utility", "ConvertNteChars", 1, new String[]{"NteString"})
    {
      /**
       * converts a string of NTE key characters (and normal characters) into their
       * default character representation - given by the first character in the
       * NTE chatacter list<br>
       * The NTE key characters are the Unicode characters u2460-u2468 and u24EA  (Unicode Circled Digits),
       * representing the numeric Text Keys 1-9 and 0.<br>
       * The characters represented by the keys are defined by the client properties
       * <tt>"ui/numeric_text_input_&lt;ui/translation_language_code&gt;_&lt;key&gt;_lower</tt>.
       *
       * @param NteString the string to convert
       * @return the converted string
       * @since 8.0
       *
       * @declaration public String ConvertNteChars(String NteString);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        Object obj = stack.pop();
        if ( obj==null)
          return null;
        String s = obj.toString();
        return (StringMatchUtils.convertFromNte(s));
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "StringIndexOfNTE", 2, new String[]{"FullString","MatchStringNTE"})
    {
      /**
       * Returns the index of MatchStringNTE string within FullString, -1 if it is not found.<br>
       * Search is case-insentive<br>
       * The MatchStringNTE may contain the Unicode characters u2460-u2468 and u24EA  (Unicode Circled Digits) representing
       * numeric Text Keys 1-9 and 0. The characters represented by the keys are defined by the client properties
       * <tt>"ui/numeric_text_input_&lt;ui/translation_language_code&gt;_&lt;key&gt;_lower</tt>.
       * @param FullString the string to search in
       * @param MatchStringNTE the string to search for
       * @return the first 0-based index in FullString that MatchStringNTE occurs at or -1 if it is not found
       * @since 8.0
       *
       * @declaration public String StringIndexOfNTE(String FullString, String MatchStringNTE);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        String matchNTE = getString(stack);
        String source = getString(stack);
        return (source == null || matchNTE == null) ? new Integer(-1) : new Integer(StringMatchUtils.findMatchingWordIndexNTE(source, matchNTE.toLowerCase()));
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "StringStartsWithNTE", 2, new String[]{"FullString","MatchStringNTE"})
    {
      /**
       * Returns true if the Full String starts with characters matching MatchStringNTE<br>
       * Search is case-insentive<br>
       * The MatchStringNTE may contain the Unicode characters u2460-u2468 and u24EA  (Unicode Circled Digits) representing
       * numeric Text Keys 1-9 and 0. The characters represented by the keys are defined by the client properties
       * <tt>"ui/numeric_text_input_&lt;ui/translation_language_code&gt;_&lt;key&gt;_lower</tt>.
       * @param FullString the string to search in
       * @param MatchStringNTE the string to search for
       * @return true if FullString starts with characters matching MatchStringNTE
       * @since 8.0
       *
       * @declaration public Boolean StringStartsWithNTE(String FullString, String MatchStringNTE);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        String matchNTE = getString(stack);
        String source = getString(stack);
        return (source == null || matchNTE == null) ? Boolean.FALSE : new Boolean(StringMatchUtils.substringMatchesNte(source, 0, matchNTE.toLowerCase()));
      }
    });
    rft.put(new PredefinedJEPFunction("Utility", "DumpServerThreadStates", true)
    {
      /**
       * Dumps all the java stack information on the SageTV server process to the server's debug output stream
       * @since 8.0
       *
       * @declaration public void DumpServerThreadStates();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Sage.DBG) System.out.println("Received API call to dump thread states...do it now!");
        AWTThreadWatcher.dumpThreadStates();
        return null;
      }});
    /*
        rft.put(new PredefinedJEPFunction("Utility", "", -1) {
        public Object runSafely(Catbert.FastStack stack) throws Exception{

            }});
     */

  }
}
