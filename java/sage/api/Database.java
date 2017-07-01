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

import sage.*;

/**
 * Contains methods for manipulating database objects in SageTV as well as doing general database queries
 *
 * NOTE: All of the 'Search' methods will be limited to 1000 results.
 */
public class Database {
  private Database() {}
  private static final boolean USE_COLLATOR_SORTING = Sage.getBoolean("use_collator_sorting", true);
  private static boolean TRIM_PRONOUNS_IN_SORTING = sage.Sage.getBoolean("ui/ignore_the_when_sorting", true);
  private static String[] pronounsToTrim = new String[] { "the ", "a ", "an " };
  private static void updateTrimPronouns()
  {
    TRIM_PRONOUNS_IN_SORTING = sage.Sage.getBoolean("ui/ignore_the_when_sorting", true);
    String pronounSet = sage.Sage.get("ui/prefixes_to_ignore_on_sort", "the,a,an");
    java.util.StringTokenizer toker = new java.util.StringTokenizer(pronounSet, ",;");
    String[] newSet = new String[toker.countTokens()];
    int i = 0;
    while (toker.hasMoreTokens())
      newSet[i++] = toker.nextToken() + " ";
    pronounsToTrim = newSet;
  }
  private static String trimPronouns(String s)
  {
    if (TRIM_PRONOUNS_IN_SORTING)
    {
      for (int i = 0; i < pronounsToTrim.length; i++)
      {
        if (s.regionMatches(true, 0, pronounsToTrim[i], 0, pronounsToTrim[i].length()))
          return s.substring(pronounsToTrim[i].length());
      }
      return s;
    }
    return s;
  }
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Database", "FilterByBoolMethod", -1, new String[]{"Data", "Method", "MatchValue"})
    {
      /**
       * Filters data by a boolean method. Each element in the 'Data' has the 'Method' executed on it.
       * If the result is the same as the 'MatchValue' parameter then that element will be in the
       * returned data. For Maps &amp; Collections this is done in place. For Arrays a new Array is created.
       * NOTE: If you pass more than 3 arguments to this function then the extra arguments will
       * be passed along to the Method that should be executed.
       * @param Data the data that is to be filtered; this can be a java.util.Collection, java.util.Map or an Array. For Maps &amp; Collections the filtering is done IN-PLACE.
       * @param Method This is what is evaluated with an element as the only argument. This can be a list of methods to test against separated by the '|' character.
       * @param MatchValue the Method must return this value to be in the returned data
       * @return The elements that passed the filter. The type is the same type as the passed in Data.
       *
       * @declaration Object FilterByBoolMethod(Object Data, String Method, boolean MatchValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        // args: filterData, filterMethod
        int extraFilterArgs = curNumberOfParameters - 3;
        final java.util.ArrayList extraArgs = new java.util.ArrayList();
        while (extraFilterArgs-- > 0)
          extraArgs.add(stack.pop());
        boolean invertRes = false;
        invertRes = !evalBool(stack.pop());
        String filterMethName = getString(stack);
        Object dataObj = stack.pop();
        if (dataObj == null) return null;
        java.util.ArrayList filtMeths = new java.util.ArrayList();
        java.util.StringTokenizer toker = new java.util.StringTokenizer(filterMethName, " |");
        while (toker.hasMoreTokens())
        {
          filtMeths.add(Catbert.getAPI().get(toker.nextToken()));
        }
        if (dataObj instanceof java.util.Collection || dataObj instanceof java.util.Map)
        {
          java.util.Collection currData;
          if (dataObj instanceof java.util.Collection)
            currData = (java.util.Collection) dataObj;
          else
            currData = ((java.util.Map) dataObj).keySet();
          java.util.Iterator walker = currData.iterator();
          if ("HasMediaMask".equals(filterMethName) && extraArgs.size() == 1)
          {
            int mediaMask = DBObject.getMediaMaskFromString(extraArgs.get(0).toString());
            while (walker.hasNext())
            {
              if (invertRes == filterTestHasMediaMask(walker.next(), mediaMask))
                walker.remove();
            }
          }
          else if ("IsCompleteRecording|IsManualRecord".equals(filterMethName))
          {
            while (walker.hasNext())
            {
              if (invertRes == filterTestCompleteOrMR(walker.next()))
                walker.remove();
            }
          }
          else if ("IsChannelViewable".equals(filterMethName))
          {
            while (walker.hasNext())
            {
              if (invertRes == filterTestChannelViewable(walker.next()))
                walker.remove();
            }
          }
          else if ("IsMovie".equals(filterMethName))
          {
            while (walker.hasNext())
            {
              if (invertRes == filterTestIsMovie(walker.next()))
                walker.remove();
            }
          }
          else if ("HasSeriesImage".equals(filterMethName))
          {
            while (walker.hasNext())
            {
              if (invertRes == filterTestHasSeriesImage(walker.next()))
                walker.remove();
            }
          }
          else if ("IsFavorite".equals(filterMethName))
          {
            while (walker.hasNext())
            {
              if (invertRes == filterTestIsFavorite(walker.next()))
                walker.remove();
            }
          }
          else if ("IsMediaFileObject".equals(filterMethName))
          {
            while (walker.hasNext())
            {
              if (invertRes == filterTestIsMediaFileObject(walker.next()))
                walker.remove();
            }
          }
          else if ("IsWatched".equals(filterMethName))
          {
            while (walker.hasNext())
            {
              if (invertRes == filterTestIsWatched(walker.next()))
                walker.remove();
            }
          }
          else
          {
            while (walker.hasNext())
            {
              Object currObj = walker.next();
              boolean testResult = false;
              for (int j = 0; j < filtMeths.size(); j++)
              {
                sage.jep.function.PostfixMathCommandI filtMeth = (sage.jep.function.PostfixMathCommandI) filtMeths.get(j);
                stack.push(currObj);
                for (int i = extraArgs.size() - 1; i >= 0; i--)
                  stack.push(extraArgs.get(i));
                filtMeth.setCurNumberOfParameters(1 + extraArgs.size());
                filtMeth.run(stack);
                if (((Boolean) stack.pop()).booleanValue())
                {
                  testResult = true;
                  break;
                }
              }
              if (invertRes == testResult)
                walker.remove();
            }
          }
          return dataObj;
        }
        else
        {
          Object[] currData = (Object[]) dataObj;
          Class filterClass = currData.getClass().getComponentType();
          java.util.ArrayList passedData = new java.util.ArrayList();
          if ("HasMediaMask".equals(filterMethName) && extraArgs.size() == 1)
          {
            int mediaMask = DBObject.getMediaMaskFromString(extraArgs.get(0).toString());
            for (int j = 0; j < currData.length; j++)
            {
              if (invertRes != filterTestHasMediaMask(currData[j], mediaMask))
                passedData.add(currData[j]);
            }
          }
          else if ("AreAiringsSameShow".equals(filterMethName) && extraArgs.size() == 1)
          {
            Airing match = getAirObj(extraArgs.get(0));
            for (int j = 0; j < currData.length; j++)
            {
              if (invertRes != BigBrother.areSameShow(getAirObj(currData[j]), match, false))
                passedData.add(currData[j]);
            }
          }
          else if ("IsCompleteRecording|IsManualRecord".equals(filterMethName))
          {
            for (int j = 0; j < currData.length; j++)
            {
              if (invertRes != filterTestCompleteOrMR(currData[j]))
                passedData.add(currData[j]);
            }
          }
          else if ("IsChannelViewable".equals(filterMethName))
          {
            for (int j = 0; j < currData.length; j++)
            {
              if (invertRes != filterTestChannelViewable(currData[j]))
                passedData.add(currData[j]);
            }
          }
          else if ("IsMovie".equals(filterMethName))
          {
            for (int j = 0; j < currData.length; j++)
            {
              if (invertRes != filterTestIsMovie(currData[j]))
                passedData.add(currData[j]);
            }
          }
          else if ("HasSeriesImage".equals(filterMethName))
          {
            for (int j = 0; j < currData.length; j++)
            {
              if (invertRes != filterTestHasSeriesImage(currData[j]))
                passedData.add(currData[j]);
            }
          }
          else if ("IsFavorite".equals(filterMethName))
          {
            for (int j = 0; j < currData.length; j++)
            {
              if (invertRes != filterTestIsFavorite(currData[j]))
                passedData.add(currData[j]);
            }
          }
          else if ("IsMediaFileObject".equals(filterMethName))
          {
            for (int j = 0; j < currData.length; j++)
            {
              if (invertRes != filterTestIsMediaFileObject(currData[j]))
                passedData.add(currData[j]);
            }
          }
          else if ("IsWatched".equals(filterMethName))
          {
            for (int j = 0; j < currData.length; j++)
            {
              if (invertRes != filterTestIsWatched(currData[j]))
                passedData.add(currData[j]);
            }
          }
          else
          {
            for (int i = 0; i < currData.length; i++)
            {
              boolean testResult = false;
              for (int j = 0; j < filtMeths.size(); j++)
              {
                sage.jep.function.PostfixMathCommandI filtMeth = (sage.jep.function.PostfixMathCommandI) filtMeths.get(j);
                stack.push(currData[i]);
                for (int k = extraArgs.size() - 1; k >= 0; k--)
                  stack.push(extraArgs.get(k));
                filtMeth.setCurNumberOfParameters(1 + extraArgs.size());
                filtMeth.run(stack);
                if (((Boolean) stack.pop()).booleanValue())
                {
                  testResult = true;
                  break;
                }
              }
              if (invertRes != testResult)
                passedData.add(currData[i]);
            }
          }
          return passedData.toArray((Object[])java.lang.reflect.Array.newInstance(filterClass,
              passedData.size()));
        }
      }
      private boolean filterTestHasMediaMask(Object o, int mediaMask)
      {
        if (o instanceof DBObject)
          return ((DBObject) o).hasMediaMaskAny(mediaMask);
        return false;
      }
      private boolean filterTestCompleteOrMR(Object o)
      {
        MediaFile mf = getMediaFileObj(o);
        if (mf != null && mf.isCompleteRecording())
          return true;
        Airing a = getAirObj(o);
        if (a != null && Wizard.getInstance().getManualRecord(a) != null)
          return true;
        return false;
      }
      private boolean filterTestChannelViewable(Object o)
      {
        Channel c = getChannelObj(o);
        return (c != null && c.isViewable());
      }
      private boolean filterTestIsMovie(Object o)
      {
        Show s = getShowObj(o);
        return s != null && s.isMovie();
      }
      private boolean filterTestHasSeriesImage(Object o)
      {
        SeriesInfo si = getSeriesInfoObj(o);
        return si != null && si.hasImage();
      }
      private boolean filterTestIsFavorite(Object o)
      {
        Airing a = getAirObj(o);
        return a != null && Carny.getInstance().isLoveAir(a);
      }
      private boolean filterTestIsWatched(Object o)
      {
        Airing a = getAirObj(o);
        return a != null && a.isWatched();
      }
      private boolean filterTestIsMediaFileObject(Object o)
      {
        return o instanceof MediaFile && (Wizard.getInstance().isMediaFileOK((MediaFile) o) ||
            ((MediaFile) o).getGeneralType() == MediaFile.MEDIAFILE_LOCAL_PLAYBACK);
      }
    });
    rft.put(new PredefinedJEPFunction("Database", "FilterByMethod", -1, new String[]{"Data", "Method", "MatchValue", "MatchedPasses"})
    {

      /**
       * Filters data by a method. Each element in the 'Data' has the 'Method' executed on it.
       * If the result is the same as the 'MatchValue' parameter then that element will be in the
       * returned data if MatchedPasses is true. If MatchedPasses is false then non-matching elements will be in the returned data.
       * For Maps &amp; Collections this is done in place. For Arrays a new Array is created.
       * NOTE: If you pass more than 4 arguments to this function then the extra arguments will
       * be passed along to the Method that should be executed.
       * @param Data the data that is to be filtered; this can be a java.util.Collection, java.util.Map or an Array. For Maps &amp; Collections the filtering is done IN-PLACE. For Maps the keys are used for the filtering.
       * @param Method This is what is evaluated with an element as the only argument. This can be a list of methods to test against separated by the '|' character. There is also a special 'UserCategories' option which will check the ManualRecord, Favorite and MediaFile "UserCategory" property as well as the Show Category &amp; SubCategory for any matches against a comma-delimited list in the MatchValue parameter.
       * @param MatchValue the value to test the return value of Method against
       * @param MatchedPasses if true then matches are included in the return data, if false then everything that doesn't match is returned
       * @return The elements that passed the filter. The type is the same type as the passed in Data.
       *
       * @declaration Object FilterByMethod(Object Data, String Method, Object MatchValue, boolean MatchedPasses);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        // args: filterData, filterMethod
        int extraFilterArgs = curNumberOfParameters - 4;
        final java.util.ArrayList extraArgs = new java.util.ArrayList();
        while (extraFilterArgs-- > 0)
          extraArgs.add(stack.pop());
        boolean invertRes = false;
        invertRes = !evalBool(stack.pop());
        Object matchValue = stack.pop();
        String filterMethName = getString(stack);
        Object dataObj = stack.pop();
        if (dataObj == null) return null;
        if ("UserCategories".equals(filterMethName))
        {
          if (matchValue == null)
          {
            if (!invertRes)
              return null;
            else
              return dataObj;
          }
          java.util.StringTokenizer toker = new java.util.StringTokenizer(matchValue.toString(), ",");
          String[] catMatches = new String[toker.countTokens()];
          for (int i = 0; i < catMatches.length; i++)
            catMatches[i] = toker.nextToken().trim();
          Agent[] cachedFavs = Wizard.getInstance().getFavorites();
          if (dataObj instanceof java.util.Collection || dataObj instanceof java.util.Map)
          {
            java.util.Collection currData;
            if (dataObj instanceof java.util.Collection)
              currData = (java.util.Collection) dataObj;
            else
              currData = ((java.util.Map) dataObj).keySet();
            java.util.Iterator walker = currData.iterator();
            while (walker.hasNext())
            {
              Object currObj = walker.next();
              boolean testResult = categoryTest(currObj, catMatches, cachedFavs);
              if (invertRes == testResult)
                walker.remove();
            }
            return dataObj;
          }
          else
          {
            Object[] currData = (Object[]) dataObj;
            Class filterClass = currData.getClass().getComponentType();
            java.util.ArrayList passedData = new java.util.ArrayList();
            for (int i = 0; i < currData.length; i++)
            {
              boolean testResult = categoryTest(currData[i], catMatches, cachedFavs);
              if (invertRes != testResult)
                passedData.add(currData[i]);
            }
            return passedData.toArray((Object[])java.lang.reflect.Array.newInstance(filterClass,
                passedData.size()));
          }
        }
        java.util.ArrayList filtMeths = new java.util.ArrayList();
        java.util.StringTokenizer toker = new java.util.StringTokenizer(filterMethName, " |");
        while (toker.hasMoreTokens())
        {
          filtMeths.add(Catbert.getAPI().get(toker.nextToken()));
        }
        if (dataObj instanceof java.util.Collection || dataObj instanceof java.util.Map)
        {
          java.util.Collection currData;
          if (dataObj instanceof java.util.Collection)
            currData = (java.util.Collection) dataObj;
          else
            currData = ((java.util.Map) dataObj).keySet();
          java.util.Iterator walker = currData.iterator();
          if ("GetAiringTitle".equals(filterMethName) || "GetShowTitle".equals(filterMethName))
          {
            while (walker.hasNext())
            {
              Object currObj = walker.next();
              if (filterTestAiringTitle(currObj, matchValue) == invertRes)
                walker.remove();
            }
          }
          else if ("GetMovieImageCount".equals(filterMethName))
          {
            int matchInt = 0;
            try
            {
              matchInt = Integer.parseInt(matchValue.toString());
            }catch(NumberFormatException nfe)
            {
              if (Sage.DBG) System.out.println("ERROR in int parameter for filtering on movie image count of:" + nfe);
            }
            while (walker.hasNext())
            {
              Object currObj = walker.next();
              if (filterTestMovieImageCount(currObj, matchInt) == invertRes)
                walker.remove();
            }
          }
          else if ("GetOriginalAiringDate".equals(filterMethName))
          {
            long matchLong = 0;
            try
            {
              matchLong = Long.parseLong(matchValue.toString());
            }catch(NumberFormatException nfe)
            {
              if (Sage.DBG) System.out.println("ERROR in int parameter for filtering on org air date of:" + nfe);
            }
            while (walker.hasNext())
            {
              Object currObj = walker.next();
              if (filterTestOriginalAirDate(currObj, matchLong) == invertRes)
                walker.remove();
            }
          }
          else if ("GetShowSeasonNumber".equals(filterMethName))
          {
            int matchInt = 0;
            try
            {
              matchInt = Integer.parseInt(matchValue.toString());
            }catch(NumberFormatException nfe)
            {
              if (Sage.DBG) System.out.println("ERROR in int parameter for filtering of:" + nfe);
            }
            while (walker.hasNext())
            {
              Object currObj = walker.next();
              if (filterTestSeasonNumber(currObj, matchInt) == invertRes)
                walker.remove();
            }
          }
          else if ("GetShowEpisodeNumber".equals(filterMethName))
          {
            int matchInt = 0;
            try
            {
              matchInt = Integer.parseInt(matchValue.toString());
            }catch(NumberFormatException nfe)
            {
              if (Sage.DBG) System.out.println("ERROR in int parameter for filtering of:" + nfe);
            }
            while (walker.hasNext())
            {
              Object currObj = walker.next();
              if (filterTestEpisodeNumber(currObj, matchInt) == invertRes)
                walker.remove();
            }
          }
          else
          {
            while (walker.hasNext())
            {
              Object currObj = walker.next();
              boolean testResult = false;
              for (int j = 0; j < filtMeths.size(); j++)
              {
                sage.jep.function.PostfixMathCommandI filtMeth = (sage.jep.function.PostfixMathCommandI) filtMeths.get(j);
                stack.push(currObj);
                for (int i = extraArgs.size() - 1; i >= 0; i--)
                  stack.push(extraArgs.get(i));
                filtMeth.setCurNumberOfParameters(1 + extraArgs.size());
                filtMeth.run(stack);
                Object testRes = stack.pop();
                boolean didPass = (testRes == matchValue) || (testRes != null &&
                    (testRes.equals(matchValue) || (matchValue != null &&
                    testRes.toString().equals(matchValue.toString()))));
                if (didPass)
                {
                  testResult = true;
                  break;
                }
              }
              if (invertRes == testResult)
                walker.remove();
            }
          }
          return dataObj;
        }
        else
        {
          Object[] currData = (Object[]) dataObj;
          Class filterClass = currData.getClass().getComponentType();
          java.util.ArrayList passedData = new java.util.ArrayList();
          if ("GetAiringTitle".equals(filterMethName) || "GetShowTitle".equals(filterMethName))
          {
            for (int i = 0; i < currData.length; i++)
            {
              if (filterTestAiringTitle(currData[i], matchValue) != invertRes)
                passedData.add(currData[i]);
            }
          }
          else if ("GetMovieImageCount".equals(filterMethName))
          {
            int matchInt = 0;
            try
            {
              matchInt = Integer.parseInt(matchValue.toString());
            }catch(NumberFormatException nfe)
            {
              if (Sage.DBG) System.out.println("ERROR in int parameter for filtering on movie image count of:" + nfe);
            }
            for (int i = 0; i < currData.length; i++)
            {
              if (filterTestMovieImageCount(currData[i], matchInt) != invertRes)
                passedData.add(currData[i]);
            }
          }
          else if ("GetOriginalAiringDate".equals(filterMethName))
          {
            long matchLong = 0;
            try
            {
              matchLong = Long.parseLong(matchValue.toString());
            }catch(NumberFormatException nfe)
            {
              if (Sage.DBG) System.out.println("ERROR in int parameter for filtering on org air date of:" + nfe);
            }
            for (int i = 0; i < currData.length; i++)
            {
              if (filterTestOriginalAirDate(currData[i], matchLong) != invertRes)
                passedData.add(currData[i]);
            }
          }
          else if ("GetShowSeasonNumber".equals(filterMethName))
          {
            int matchInt = 0;
            try
            {
              matchInt = Integer.parseInt(matchValue.toString());
            }catch(NumberFormatException nfe)
            {
              if (Sage.DBG) System.out.println("ERROR in int parameter for filtering of:" + nfe);
            }
            for (int i = 0; i < currData.length; i++)
            {
              if (filterTestSeasonNumber(currData[i], matchInt) != invertRes)
                passedData.add(currData[i]);
            }
          }
          else if ("GetShowEpisodeNumber".equals(filterMethName))
          {
            int matchInt = 0;
            try
            {
              matchInt = Integer.parseInt(matchValue.toString());
            }catch(NumberFormatException nfe)
            {
              if (Sage.DBG) System.out.println("ERROR in int parameter for filtering of:" + nfe);
            }
            for (int i = 0; i < currData.length; i++)
            {
              if (filterTestEpisodeNumber(currData[i], matchInt) != invertRes)
                passedData.add(currData[i]);
            }
          }
          else
          {
            for (int i = 0; i < currData.length; i++)
            {
              boolean testResult = false;
              for (int j = 0; j < filtMeths.size(); j++)
              {
                sage.jep.function.PostfixMathCommandI filtMeth = (sage.jep.function.PostfixMathCommandI) filtMeths.get(j);
                stack.push(currData[i]);
                for (int k = extraArgs.size() - 1; k >= 0; k--)
                  stack.push(extraArgs.get(k));
                filtMeth.setCurNumberOfParameters(1 + extraArgs.size());
                filtMeth.run(stack);
                Object testRes = stack.pop();
                boolean didPass = (testRes == matchValue) || (testRes != null &&
                    (testRes.equals(matchValue) || (matchValue != null &&
                    testRes.toString().equals(matchValue.toString()))));
                if (didPass)
                {
                  testResult = true;
                  break;
                }
              }
              if (invertRes != testResult)
                passedData.add(currData[i]);
            }
          }
          return passedData.toArray((Object[])java.lang.reflect.Array.newInstance(filterClass,
              passedData.size()));
        }
      }
      private boolean categoryTest(Object obj, String[] cats, Agent[] cachedFavs)
      {
        // Root this in the Airing object since everything else will link to that quickly
        Airing air = getAirObj(obj);
        if (air == null)
          return false;
        // Check the Show first
        Show s = air.getShow();
        if (s != null)
        {
          if (catListTest(s.getCategory(), cats))
            return true;
          if (catListTest(s.getSubCategory(), cats))
            return true;
          if (s.getNumCategories() > 2)
          {
            String[] showCats = s.getCategories();
            for (int i = 2; i < showCats.length; i++)
              if (catListTest(showCats[i], cats))
                return true;
          }
        }

        // Now check the MR properties
        ManualRecord mr = wiz.getManualRecord(air);
        if (mr != null)
        {
          if (catListTest(mr.getProperty("UserCategory"), cats))
            return true;
        }

        // Now check MF property
        MediaFile mf = wiz.getFileForAiring(air);
        if (mf != null)
        {
          if (catListTest(mf.getMetadataProperty("UserCategory"), cats))
            return true;
        }

        // Now check Fav property
        Agent bond = Carny.getInstance().getCauseAgent(air);
        if (bond == null || !bond.isFavorite())
        {
          bond = null;
          // Search through all of the Favorite objects to find the correct one
          StringBuilder sbCache = new StringBuilder();
          for (int i = 0; i < cachedFavs.length; i++)
          {
            if (cachedFavs[i].followsTrend(air, false, sbCache))
            {
              bond = cachedFavs[i];
              break;
            }
          }
        }
        if (bond != null)
        {
          if (catListTest(bond.getProperty("UserCategory"), cats))
            return true;
        }

        return false;
      }
      private boolean catListTest(String catProp, String[] cats)
      {
        if (catProp != null && catProp.length() > 0)
        {
          for (int i = 0; i < cats.length; i++)
          {
            int idx = catProp.indexOf(cats[i]);
            if (idx != -1)
            {
              if (idx == 0 && (cats[i].length() == catProp.length() || catProp.charAt(cats[i].length()) == ','))
                return true;
              else if (idx >= 2 && (catProp.charAt(idx - 2) == ',' || catProp.charAt(idx - 1) == ',') &&
                  ((idx + cats[i].length() == catProp.length() || (idx + cats[i].length() < catProp.length() && catProp.charAt(idx + cats[i].length()) == ','))))
                return true;
            }
          }
        }
        return false;
      }
      private boolean filterTestAiringTitle(Object o, Object matcher)
      {
        Show a = getShowObj(o);
        return a != null && matcher != null && matcher.toString().equals(a.getTitle());
      }
      private boolean filterTestMovieImageCount(Object o, int matcher)
      {
        Show s = getShowObj(o);
        return s != null && s.getImageCount() == matcher;
      }
      private boolean filterTestOriginalAirDate(Object o, long matcher)
      {
        Show s = getShowObj(o);
        return s != null && s.getOriginalAirDate() == matcher;
      }
      private boolean filterTestSeasonNumber(Object o, int matcher)
      {
        Show s = getShowObj(o);
        return s != null && s.getSeasonNumber() == matcher;
      }
      private boolean filterTestEpisodeNumber(Object o, int matcher)
      {
        Show s = getShowObj(o);
        return s != null && s.getEpisodeNumber() == matcher;
      }
      private Wizard wiz = Wizard.getInstance();
    });
    rft.put(new PredefinedJEPFunction("Database", "FilterByMethodRegex", -1, new String[]{"Data", "Method", "RegexPattern", "MatchedPasses", "CompleteMatch"})
    {

      /**
       * Filters data by a method. Each element in the 'Data' has the 'Method' executed on it.
       * The result is then converted to a String and RegexPattern is applied to it.
       * If the regular expression matches the String value and MatchedPasses is true, then the element
       * will be in the returned data. If MatchedPasses is false then non-matching elements will be in the returned data.
       * For Maps &amp; Collections this is done in place. For Arrays a new Array is created.
       * NOTE: If you pass more than 5 arguments to this function then the extra arguments will
       * be passed along to the Method that should be executed.
       * @param Data the data that is to be filtered; this can be a java.util.Collection, java.util.Map or an Array. For Maps &amp; Collections the filtering is done IN-PLACE.
       * @param Method This is what is evaluated with an element as the only argument (and additional arguments if passed in).
       * @param RegexPattern The compiled regular expression used for matching (if it's not compiled, then it will be converted to a compiled regular expression)
       * @param MatchedPasses if true then matches are included in the return data, if false then everything that doesn't match is returned
       * @param CompleteMatch if true then the entire string must match the regular expression, if false then the regular expression only needs to match a substring of it
       * @return The elements that passed the filter. The type is the same type as the passed in Data.
       * @since 5.1
       *
       * @declaration Object FilterByMethodRegex(Object Data, String Method, java.util.regex.Pattern RegexPattern, boolean MatchedPasses, boolean CompleteMatch);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        // args: filterData, filterMethod
        int extraFilterArgs = curNumberOfParameters - 5;
        final java.util.ArrayList extraArgs = new java.util.ArrayList();
        while (extraFilterArgs-- > 0)
          extraArgs.add(stack.pop());
        boolean invertRes = false;
        boolean completeMatch = evalBool(stack.pop());
        invertRes = !evalBool(stack.pop());
        java.util.regex.Pattern regpat = getRegex(stack);
        String filterMethName = getString(stack);
        Object dataObj = stack.pop();
        if (dataObj == null) return null;
        java.util.ArrayList filtMeths = new java.util.ArrayList();
        java.util.StringTokenizer toker = new java.util.StringTokenizer(filterMethName, " |");
        while (toker.hasMoreTokens())
        {
          filtMeths.add(Catbert.getAPI().get(toker.nextToken()));
        }
        if (dataObj instanceof java.util.Collection || dataObj instanceof java.util.Map)
        {
          java.util.Collection currData;
          if (dataObj instanceof java.util.Collection)
            currData = (java.util.Collection) dataObj;
          else
            currData = ((java.util.Map) dataObj).keySet();
          java.util.Iterator walker = currData.iterator();
          while (walker.hasNext())
          {
            Object currObj = walker.next();
            boolean dontRemove = false;
            for (int j = 0; j < filtMeths.size(); j++)
            {
              sage.jep.function.PostfixMathCommandI filtMeth = (sage.jep.function.PostfixMathCommandI) filtMeths.get(j);
              stack.push(currObj);
              for (int i = extraArgs.size() - 1; i >= 0; i--)
                stack.push(extraArgs.get(i));
              filtMeth.setCurNumberOfParameters(1 + extraArgs.size());
              filtMeth.run(stack);
              Object testRes = stack.pop();
              boolean didPass = testRes != null && ((completeMatch && regpat.matcher(testRes.toString()).matches()) ||
                  (!completeMatch && regpat.matcher(testRes.toString()).find()));
              if (didPass != invertRes)
              {
                dontRemove = true;
                break;
              }
            }
            if (!dontRemove)
              walker.remove();
          }
          return dataObj;
        }
        else
        {
          Object[] currData = (Object[]) dataObj;
          Class filterClass = currData.getClass().getComponentType();
          java.util.ArrayList passedData = new java.util.ArrayList();
          for (int i = 0; i < currData.length; i++)
          {
            for (int j = 0; j < filtMeths.size(); j++)
            {
              sage.jep.function.PostfixMathCommandI filtMeth = (sage.jep.function.PostfixMathCommandI) filtMeths.get(j);
              stack.push(currData[i]);
              for (int k = extraArgs.size() - 1; k >= 0; k--)
                stack.push(extraArgs.get(k));
              filtMeth.setCurNumberOfParameters(1 + extraArgs.size());
              filtMeth.run(stack);
              Object testRes = stack.pop();
              boolean didPass = testRes != null && ((completeMatch && regpat.matcher(testRes.toString()).matches()) ||
                  (!completeMatch && regpat.matcher(testRes.toString()).find()));
              if (didPass != invertRes)
              {
                passedData.add(currData[i]);
                break;
              }
            }
          }
          return passedData.toArray((Object[])java.lang.reflect.Array.newInstance(filterClass,
              passedData.size()));
        }
      }
    });
    rft.put(new PredefinedJEPFunction("Database", "FilterByRange", 5, new String[]{"Data", "Method", "LowerBoundInclusive", "UpperBoundExclusive", "KeepWithinBounds"})
    {

      /**
       * Filters data by a comparable range. Each element in the 'Data' has the 'Method' executed on it.
       * If KeepWithinBounds is true, then results that are within the specified range are included in the returned data; otherwise
       * if KeepWithinBounds is false then results that are outside of the specified range are included in the returned data.
       * For Maps &amp; Collections this is done in place. For Arrays a new Array is created.
       * @param Data the data that is to be filtered; this can be a java.util.Collection, java.util.Map or an Array. For Maps &amp; Collections the filtering is done IN-PLACE.
       * @param Method This is what is evaluated with an element as the only argument
       * @param LowerBoundInclusive a java.lang.Comparable which specifies the INCLUSIVE lower bound for the range
       * @param UpperBoundExclusive a java.lang.Comparable which specified the EXCLUSIVE upper bound for the range
       * @param KeepWithinBounds if true then values within the range are returned, if false then values outside the range are returned
       * @return The elements that passed the filter. The type is the same type as the passed in Data.
       *
       * @declaration public Object FilterByRange(Object Data, String Method, java.lang.Comparable LowerBoundInclusive, java.lang.Comparable UpperBoundExclusive, boolean KeepWithinBounds);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        // args: filterData, filterMethod, filterValue, invertResults
        boolean invertRes = false;
        invertRes = !evalBool(stack.pop());
        Object upperBound = stack.pop();
        Object lowerBound = stack.pop();
        String filterMethName = getString(stack);
        Object dataObj = stack.pop();
        if (dataObj == null) return null;
        Object[] currData;
        if (dataObj instanceof java.util.Collection)
          currData = ((java.util.Collection) dataObj).toArray();
        else if (dataObj instanceof java.util.Map)
          currData = ((java.util.Map) dataObj).keySet().toArray();
        else
          currData = (Object[]) dataObj;
        Class filterClass = currData.getClass().getComponentType();
        java.util.ArrayList passedData = new java.util.ArrayList();
        if ("GetAiringEndTime".equals(filterMethName) || "GetAiringStartTime".equals(filterMethName) ||
            "GetScheduleStartTime".equals(filterMethName) || "GetScheduleEndTime".equals(filterMethName))
        {
          boolean isStart = filterMethName.indexOf("Start") != -1;
          boolean isSched = filterMethName.indexOf("Schedule") != -1;
          long minValue = !(lowerBound instanceof Number) ? Long.MIN_VALUE : ((Number)lowerBound).longValue();
          long maxValue = !(upperBound instanceof Number) ? Long.MAX_VALUE : ((Number)upperBound).longValue();
          for (int i = 0; i < currData.length; i++)
          {
            Airing a = getAirObj(currData[i]);
            boolean didPass = true;
            if (a != null)
            {
              long endTime = isStart ? (isSched ? a.getSchedulingStart() : a.getStartTime()) :
                (isSched ? a.getSchedulingEnd() : a.getEndTime());
              if (invertRes)
                didPass = (endTime < minValue) || (endTime >= maxValue);
              else
                didPass = (endTime >= minValue) && (endTime < maxValue);
            }
            else
              didPass = false;
            if (didPass)
              passedData.add(currData[i]);
          }
        }
        else
        {
          sage.jep.function.PostfixMathCommandI filtMeth =
              (sage.jep.function.PostfixMathCommandI) Catbert.getAPI().get(filterMethName);
          for (int i = 0; i < currData.length; i++)
          {
            stack.push(currData[i]);
            filtMeth.setCurNumberOfParameters(1);
            filtMeth.run(stack);
            Object testRes = stack.pop();
            boolean didPass = true;
            if (lowerBound != null)
            {
              if (((Comparable) lowerBound).compareTo(testRes) > 0)
              {
                if (!invertRes)
                  didPass = false;
              }
              else if (invertRes)
                didPass = false;
            }
            if (upperBound != null)
            {
              if (((Comparable) upperBound).compareTo(testRes) <= 0)
              {
                if (!invertRes)
                  didPass = false;
              }
              else if (invertRes)
                didPass = false;
            }
            if (didPass)
              passedData.add(currData[i]);
          }
        }
        return passedData.toArray((Object[])java.lang.reflect.Array.newInstance(filterClass,
            passedData.size()));
      }
    });
    rft.put(new PredefinedJEPFunction("Database", "GroupByMethod", -1, new String[]{"Data", "Method"})
    {

      /**
       * Grouping method for data lists/maps. This will return a Map that uses a key-&gt;value mapping to group the data.
       * The order of the grouping is stable, which means the order of the elements within a subgroup will be the same
       * order as in the pased in data. Use the GetSubgroup method to get the corresponding value for a key.
       * The key for each data element is determined by calling the specified 'Method' with that data element as the sole parameter.
       * Each value in the map will be a java.util.Vector that contains the elements in the group.
       * There is a special Method called "Categories" which will allow items to fall into possibly more than one group. This will
       * group by Category and also by SubCategory all at the same level (if SubCategory is not defined, then it will not be used for an alternate grouping).
       * "Categories" grouping will also break up any category names that have comma or semicolon delimited lists and put the item into each of those.
       * NOTE: If you pass more than 2 arguments to this function then the extra arguments will
       * be passed along to the Method that should be executed.
       * @param Data the data to perform the grouping on, must be a java.util.Collection, java.util.Map or an Array
       * @param Method the name of the Method to execute on each element to retrieve the key used for grouping, see the note above regarding "Categories" as a special option
       * @return a java.util.Map keyed with the values obtained from executing Method on the Data and with values that are Vectors of elements who's keys match
       *
       * @declaration public java.util.Map GroupByMethod(Object Data, String Method);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        /*
         * GREAT IDEA
         * WE WANT TO SUPPORT MAPS. THEY'LL BE USED LIKE ARRAYS ARE, BUT THEY'LL HANDLE
         * THE VARIABLE-DEPTH SCENARIOS (values will be sorted maps or vectors).
         * THERE'S PROBABLY SOME NICE WAY TO LINK THE DATA MAPS
         * WITH THE UI DATATABLES SO THEY CHAIN IN PARALLEL.
         * Or at least something like this will be relevant....they should be sorted too.
         */
        // The first argument is the array data we're going to group
        // The other argument is a quoted string that returns an Object
        // and we use that Object as a key for grouping
        int extraGroupArgs = curNumberOfParameters - 2;
        final java.util.ArrayList extraArgs = new java.util.ArrayList();
        while (extraGroupArgs-- > 0)
          extraArgs.add(stack.pop());
        String groupMethName = getString(stack);
        Object dataObj = stack.pop();
        Object[] linearData;
        if (dataObj instanceof java.util.Collection)
          linearData = ((java.util.Collection) dataObj).toArray();
        else if (dataObj instanceof java.util.Map)
          linearData = ((java.util.Map) dataObj).keySet().toArray();
        else
          linearData = (Object[]) dataObj;
        java.util.Map groupedMap = new java.util.LinkedHashMap();
        if (linearData != null && linearData.length > 0)
        {
          if ("GetShowYear".equals(groupMethName))
          {
            for (int i = 0; i < linearData.length; i++)
            {
              Show s = getShowObj(linearData[i]);
              Object currKey = (s == null) ? null : s.getYear();
              java.util.Vector currVec = (java.util.Vector) groupedMap.get(currKey);
              if (currVec == null)
                groupedMap.put(currKey, currVec = new java.util.Vector());
              currVec.add(linearData[i]);
            }
          }
          else if ("GetShowTitle".equals(groupMethName) || "GetAiringTitle".equals(groupMethName))
          {
            for (int i = 0; i < linearData.length; i++)
            {
              Show s = getShowObj(linearData[i]);
              Object currKey = (s == null) ? null : s.getTitle();
              java.util.Vector currVec = (java.util.Vector) groupedMap.get(currKey);
              if (currVec == null)
                groupedMap.put(currKey, currVec = new java.util.Vector());
              currVec.add(linearData[i]);
            }
          }
          else if ("GetShow".equals(groupMethName))
          {
            for (int i = 0; i < linearData.length; i++)
            {
              Show s = getShowObj(linearData[i]);
              java.util.Vector currVec = (java.util.Vector) groupedMap.get(s);
              if (currVec == null)
                groupedMap.put(s, currVec = new java.util.Vector());
              currVec.add(linearData[i]);
            }
          }
          else if ("GetShowCategory".equals(groupMethName))
          {
            for (int i = 0; i < linearData.length; i++)
            {
              Show s = getShowObj(linearData[i]);
              Object currKey = (s == null) ? null : s.getCategory();
              java.util.Vector currVec = (java.util.Vector) groupedMap.get(currKey);
              if (currVec == null)
                groupedMap.put(currKey, currVec = new java.util.Vector());
              currVec.add(linearData[i]);
            }
          }
          else if ("Categories".equals(groupMethName))
          {
            // This maps a category name to an array of actual category items it should correspond to
            java.util.Map breakdownMap = new java.util.HashMap();
            String[] noBreaks = new String[] { "" };
            for (int i = 0; i < linearData.length; i++)
            {
              Show s = getShowObj(linearData[i]);
              String currKey = (s == null) ? null : s.getCategory();
              String[] currBreaks;
              if (currKey == null || currKey.length() == 0)
                currBreaks = noBreaks;
              else
              {
                currBreaks = (String[]) breakdownMap.get(currKey);
                if (currBreaks == null)
                {
                  java.util.StringTokenizer toker = new java.util.StringTokenizer(currKey, ";,");
                  currBreaks = new String[toker.countTokens()];
                  int x = 0;
                  while (toker.hasMoreTokens())
                    currBreaks[x++] = toker.nextToken().trim();
                  breakdownMap.put(currKey, currBreaks);
                }
              }
              java.util.Vector currVec;
              for (int j = 0; j < currBreaks.length; j++)
              {
                currVec = (java.util.Vector) groupedMap.get(currBreaks[j]);
                if (currVec == null)
                  groupedMap.put(currBreaks[j], currVec = new java.util.Vector());
                currVec.add(linearData[i]);
              }
              if (s != null)
              {
                String newKey = s.getSubCategory();
                if (newKey != null && newKey.length() > 0 && !newKey.equals(currKey))
                {
                  currBreaks = (String[]) breakdownMap.get(newKey);
                  if (currBreaks == null)
                  {
                    java.util.StringTokenizer toker = new java.util.StringTokenizer(newKey, ";,");
                    currBreaks = new String[toker.countTokens()];
                    int x = 0;
                    while (toker.hasMoreTokens())
                      currBreaks[x++] = toker.nextToken().trim();
                    breakdownMap.put(newKey, currBreaks);
                  }
                  for (int j = 0; j < currBreaks.length; j++)
                  {
                    currVec = (java.util.Vector) groupedMap.get(currBreaks[j]);
                    if (currVec == null)
                      groupedMap.put(currBreaks[j], currVec = new java.util.Vector());
                    currVec.add(linearData[i]);
                  }
                }
                if (s.getNumCategories() > 2)
                {
                  java.util.HashSet usedCats = Pooler.getPooledHashSet();
                  usedCats.add(currKey);
                  usedCats.add(newKey);
                  String[] altTestCats = s.getCategories();
                  for (int k = 2; k < altTestCats.length; k++)
                  {
                    if (usedCats.add(altTestCats[k]))
                    {
                      currBreaks = (String[]) breakdownMap.get(altTestCats[k]);
                      if (currBreaks == null)
                      {
                        java.util.StringTokenizer toker = new java.util.StringTokenizer(altTestCats[k], ";,");
                        currBreaks = new String[toker.countTokens()];
                        int x = 0;
                        while (toker.hasMoreTokens())
                          currBreaks[x++] = toker.nextToken().trim();
                        breakdownMap.put(altTestCats[k], currBreaks);
                      }
                      for (int j = 0; j < currBreaks.length; j++)
                      {
                        currVec = (java.util.Vector) groupedMap.get(currBreaks[j]);
                        if (currVec == null)
                          groupedMap.put(currBreaks[j], currVec = new java.util.Vector());
                        currVec.add(linearData[i]);
                      }
                    }
                  }
                }
              }
            }
          }
          else if ("GetAlbumForFile".equals(groupMethName))
          {
            Wizard wiz = Wizard.getInstance();
            for (int i = 0; i < linearData.length; i++)
            {
              MediaFile mf = getMediaFileObj(linearData[i]);
              Object currKey = (mf == null || !mf.isMusic()) ? null : wiz.getCachedAlbumForMediaFile(mf);
              java.util.Vector currVec = (java.util.Vector) groupedMap.get(currKey);
              if (currVec == null)
                groupedMap.put(currKey, currVec = new java.util.Vector());
              currVec.add(linearData[i]);
            }

          }
          else
          {
            sage.jep.function.PostfixMathCommandI groupMeth =
                (sage.jep.function.PostfixMathCommandI) Catbert.getAPI().get(groupMethName);
            for (int i = 0; i < linearData.length; i++)
            {
              stack.push(linearData[i]);
              for (int k = extraArgs.size() - 1; k >= 0; k--)
                stack.push(extraArgs.get(k));
              groupMeth.setCurNumberOfParameters(1 + extraArgs.size());
              groupMeth.run(stack);
              Object currKey = stack.pop();
              java.util.Vector currVec = (java.util.Vector) groupedMap.get(currKey);
              if (currVec == null)
                groupedMap.put(currKey, currVec = new java.util.Vector());
              currVec.add(linearData[i]);
            }
          }
        }
        //if (Sage.DBG) System.out.println("GroupByMethod res=" + groupedMap);
        return (groupedMap);
      }
    });
    rft.put(new PredefinedJEPFunction("Database", "GroupByArrayMethod", -1, new String[]{"Data", "Method"})
    {

      /**
       * Grouping method for data lists/maps. This will return a Map that uses a key-&gt;value mapping to group the data.
       * The order of the grouping is stable, which means the order of the elements within a subgroup will be the same
       * order as in the pased in data. Use the GetSubgroup method to get the corresponding value for a key.
       * The keys for each data element is determined by calling the specified 'Method' with that data element as the sole parameter.
       * The Method should return an array or list, each element of which will be a key that the data element will be grouped by.
       * Each value in the map will be a java.util.Vector that contains the elements in the group.
       * NOTE: If you pass more than 2 arguments to this function then the extra arguments will
       * be passed along to the Method that should be executed.
       * @param Data the data to perform the grouping on, must be a java.util.Collection, java.util.Map or an Array
       * @param Method the name of the Method to execute on each element to retrieve the keys used for grouping
       * @return a java.util.Map keyed with the values obtained from executing Method on the Data and with values that are Vectors of elements who's keys match
       * @since 5.1
       *
       * @declaration public java.util.Map GroupByArrayMethod(Object Data, String Method);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        // The first argument is the array data we're going to group
        // The other argument is a quoted string that returns an Object
        // and we use that Object as a key for grouping
        int extraGroupArgs = curNumberOfParameters - 2;
        final java.util.ArrayList extraArgs = new java.util.ArrayList();
        while (extraGroupArgs-- > 0)
          extraArgs.add(stack.pop());
        String groupMethName = getString(stack);
        Object dataObj = stack.pop();
        Object[] linearData;
        if (dataObj instanceof java.util.Collection)
          linearData = ((java.util.Collection) dataObj).toArray();
        else if (dataObj instanceof java.util.Map)
          linearData = ((java.util.Map) dataObj).keySet().toArray();
        else
          linearData = (Object[]) dataObj;
        java.util.Map groupedMap = new java.util.LinkedHashMap();
        if (linearData != null && linearData.length > 0)
        {
          if ("GetPeopleListInShowInRoles".equals(groupMethName))
          {
            // Determine the roles they're looking for
            String[] roleStrs = getStringListObj(extraArgs.get(0));
            int[] roles = new int[roleStrs.length];
            for (int i = 0; i < roles.length; i++)
              roles[i] = Show.getRoleForString(roleStrs[i]);
            for (int i = 0; i < linearData.length; i++)
            {
              Show s = getShowObj(linearData[i]);
              boolean addedAny = false;
              if (s != null)
              {
                byte[] currRoles = s.getRoles();
                for (int j = 0; j < currRoles.length; j++)
                {
                  for (int k = 0; k < roles.length; k++)
                  {
                    if (roles[k] == currRoles[j])
                    {
                      String peep = s.getPerson(j);
                      if (peep != null && peep.length() > 0)
                      {
                        addedAny = true;
                        java.util.Vector currVec = (java.util.Vector) groupedMap.get(peep);
                        if (currVec == null)
                        {
                          groupedMap.put(peep, currVec = new java.util.Vector());
                          currVec.add(linearData[i]);
                        }
                        else
                        {
                          // NOTE: if there's multiple identical values in this array we don't want to put multiple
                          // references to the object in the mapping value vector
                          if (!currVec.contains(linearData[i]))
                            currVec.add(linearData[i]);
                        }
                      }
                    }
                  }
                }
              }
              if (!addedAny)
              {
                // Put one in the null slot at least
                java.util.Vector currVec = (java.util.Vector) groupedMap.get(null);
                if (currVec == null)
                {
                  groupedMap.put(null, currVec = new java.util.Vector());
                  currVec.add(linearData[i]);
                }
                else
                {
                  currVec.add(linearData[i]);
                }
              }
            }
          }
          else if ("GetShowCategoriesList".equals(groupMethName))
          {
            sage.jep.function.PostfixMathCommandI groupMeth =
                (sage.jep.function.PostfixMathCommandI) Catbert.getAPI().get(groupMethName);
            for (int i = 0; i < linearData.length; i++)
            {
              Show currShow = getShowObj(linearData[i]);
              int numCats = (currShow == null) ? 0 : currShow.getNumCategories();
              for (int m = 0; m < numCats; m++)
              {
                String currCat = currShow.getCategory(m);
                // We'd end up with a huge amount of content in the Movie category in this case which is not
                // that useful at all...they'll already get grouped on the type of movie they are
                if ("Movie".equals(currCat))
                  continue;
                java.util.Vector currVec = (java.util.Vector) groupedMap.get(currCat);
                if (currVec == null)
                {
                  groupedMap.put(currCat, currVec = new java.util.Vector());
                  currVec.add(linearData[i]);
                }
                else
                {
                  // NOTE: if there's multiple identical values in this array we don't want to put multiple
                  // references to the object in the mapping value vector
                  // NOTE: But we can disable this horribly slow search technique for show categories since
                  // those should never actually have duplicates
                  //if (!currVec.contains(linearData[i]))
                  currVec.add(linearData[i]);
                }
              }
              if (numCats == 0 && currShow != null)
              {
                // Put one in the null slot at least
                java.util.Vector currVec = (java.util.Vector) groupedMap.get(null);
                if (currVec == null)
                {
                  groupedMap.put(null, currVec = new java.util.Vector());
                  currVec.add(linearData[i]);
                }
                else
                {
                  if (!currVec.contains(linearData[i]))
                    currVec.add(linearData[i]);
                }
              }
            }
          }
          else
          {
            sage.jep.function.PostfixMathCommandI groupMeth =
                (sage.jep.function.PostfixMathCommandI) Catbert.getAPI().get(groupMethName);
            for (int i = 0; i < linearData.length; i++)
            {
              stack.push(linearData[i]);
              for (int k = extraArgs.size() - 1; k >= 0; k--)
                stack.push(extraArgs.get(k));
              groupMeth.setCurNumberOfParameters(1 + extraArgs.size());
              groupMeth.run(stack);
              Object currKey = stack.pop();
              Object[] keyData;
              if (currKey instanceof Object[])
                keyData = (Object[]) currKey;
              else if (currKey instanceof java.util.Collection)
                keyData = ((java.util.Collection) currKey).toArray();
              else if (currKey == null)
                keyData = Pooler.EMPTY_OBJECT_ARRAY;
              else
                keyData = new Object[] { currKey };
              for (int m = 0; m < keyData.length; m++)
              {
                java.util.Vector currVec = (java.util.Vector) groupedMap.get(keyData[m]);
                if (currVec == null)
                {
                  groupedMap.put(keyData[m], currVec = new java.util.Vector());
                  currVec.add(linearData[i]);
                }
                else
                {
                  // NOTE: if there's multiple identical values in this array we don't want to put multiple
                  // references to the object in the mapping value vector
                  if (!currVec.contains(linearData[i]))
                    currVec.add(linearData[i]);
                }
              }
              if (keyData.length == 0)
              {
                // Put one in the null slot at least
                java.util.Vector currVec = (java.util.Vector) groupedMap.get(null);
                if (currVec == null)
                {
                  groupedMap.put(null, currVec = new java.util.Vector());
                  currVec.add(linearData[i]);
                }
                else
                {
                  if (!currVec.contains(linearData[i]))
                    currVec.add(linearData[i]);
                }
              }
            }
          }
        }
        //if (Sage.DBG) System.out.println("GroupByMethod res=" + groupedMap);
        return (groupedMap);
      }
    });
    rft.put(new PredefinedJEPFunction("Database", "Sort", -1, new String[]{"Data", "Descending", "SortTechnique"})
    {

      /**
       * Sorts a list of data according to the specified sorting technique. The order of the sort can be reversed using
       * the Descending parameter. If you use a method name for the SortTechnique, then the data will be sorted by the natural
       * ordering of the values returned from that mehod call. If that data type does not implement java.lang.Comparable then they
       * will be converted to Strings and those Strings will be compared. NOTE: If you pass more than 3 arguments to this function then the extra arguments will
       * be passed along to the SortTechnique if it refers to a Method that should be executed.
       * @param Data the data to sort, this must be a java.util.Collection, a java.util.Map, or an array; for Collections all the elements must be the same Class
       * @param Descending if true then the data will be sorted in descending order, if false then the order will be reversed
       * @param SortTechnique the technique to sort the data by; this can be a java.util.Comparator
       *         which then explicitly controls the sort, or it can be one of the named sorting techniques of:
       *         Intelligent, ChannelNumber, CaseInsensitive, FavoritePriority, CaptureDevicePriority, Natural or a method name. If null is passed then the elements "natural" sorting is used.
       * @return the sorted data, for passed in Maps this'll be a sorted Map; for Collections or arrays this will be an Object[] array
       *
       * @declaration public Object Sort(Object Data, boolean Descending, Object SortTechnique);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        java.util.Comparator sortie = null;
        boolean invertOrder;
        Object[] currData;
        int extraSortArgs = curNumberOfParameters - 3;
        final java.util.ArrayList extraArgs = new java.util.ArrayList();
        while (extraSortArgs-- > 0)
          extraArgs.add(stack.pop());
        Object sortTech = stack.pop();
        invertOrder = evalBool(stack.pop());
        Object fooData = stack.pop();
        if (fooData instanceof java.util.Collection)
        {
          java.util.Collection collect = (java.util.Collection) fooData;
          if (collect.isEmpty())
            return Pooler.EMPTY_OBJECT_ARRAY;
          // We can't be sure all the elements are the same class as the first one, so just use Object
          //java.util.Iterator walker = collect.iterator();
          currData = collect.toArray();//(Object[])java.lang.reflect.Array.newInstance(walker.next().getClass(), collect.size()));
        }
        else if (fooData instanceof java.util.Map)
        {
          currData = (Object[]) ((java.util.Map) fooData).keySet().toArray();
        }
        else
          currData = (Object[]) fooData;
        boolean alreadySorted = false;
        if (sortTech == null || "Natural".equalsIgnoreCase(sortTech.toString()))
          sortie = null;
        else if (sortTech instanceof java.util.Comparator)
          sortie = (java.util.Comparator) sortTech;
        else
        {
          String filterMethName = sortTech.toString();
          String lcMethName = filterMethName.toLowerCase();
          // Intelligent is the only option so far
          if (optSortMap.containsKey(lcMethName))
            sortie = optSortMap.get(lcMethName);
          else if (filterMethName.equalsIgnoreCase("ChannelNumber") && extraArgs.size() == 0)
            sortie = (stack.getUIMgrSafe() == null) ? EPG.channelNumSorter : stack.getUIMgrSafe().channelNumSorter;
          else if (filterMethName.equalsIgnoreCase("FavoritePriority"))
          {
            alreadySorted = true;
            currData = Carny.getInstance().sortAgentsByPriority(currData);
          }
          else if (filterMethName.equalsIgnoreCase("CaptureDevicePriority"))
          {
            sortie = CaptureDevice.captureDeviceSorter;
          }
          else
          {
            final boolean specialChannelCompare = (filterMethName != null) && filterMethName.indexOf("ChannelNumber") != -1;
            if (filterMethName.equalsIgnoreCase("ChannelNumber"))
              filterMethName = extraArgs.remove(extraArgs.size() - 1).toString();
            final sage.jep.function.PostfixMathCommandI sortMeth =
                (sage.jep.function.PostfixMathCommandI) Catbert.getAPI().get(filterMethName);
            sortie = new java.util.Comparator()
            {
              public int compare(Object o1, Object o2)
              {
                try
                {
                  Catbert.FastStack s = new Catbert.FastStack();
                  s.push(o1);
                  for (int i = extraArgs.size() - 1; i >= 0; i--)
                    s.push(extraArgs.get(i));
                  sortMeth.setCurNumberOfParameters(1 + extraArgs.size());
                  sortMeth.run(s);
                  Object c1 = s.pop();
                  s.push(o2);
                  for (int i = extraArgs.size() - 1; i >= 0; i--)
                    s.push(extraArgs.get(i));
                  sortMeth.setCurNumberOfParameters(1 + extraArgs.size());
                  sortMeth.run(s);
                  Object c2 = s.pop();
                  if (specialChannelCompare)
                  {
                    try
                    {
                      int i1 = Integer.parseInt(c1.toString());
                      int i2 = Integer.parseInt(c2.toString());
                      return i1 - i2;
                    }
                    catch (Exception e)
                    {
                      // 601
                      String s1 = UIManager.leadZero(c1.toString());
                      String s2 = UIManager.leadZero(c2.toString());

                      return (s1.compareTo(s2));

                    }
                  }
                  else if (c1 instanceof Comparable)
                  {
                    try
                    {
                      return ((Comparable)c1).compareTo(c2);
                    }
                    catch (Exception e)
                    {
                      return c1.toString().compareTo(c2.toString());
                    }
                  }
                  else
                    return c1.toString().compareTo(c2.toString());
                }
                catch (Exception e)
                {
                  // We're more or less supressing this now because Boolean's aren't comparable in 1.4 and we'd rather have
                  // sorts work more often if possible
                  e.printStackTrace();
                  throw new IllegalArgumentException("Return type for Sort method is not a Comparable: " + e);
                }
              }
            };
          }
        }
        // args[1] can either be a name of a sorting technique, or it can resolve to a
        // java.util.Comparator object which we use to sort on
        if (currData != null && !alreadySorted)
        {
          if (sortie == null && currData.length > 0 && !(currData[0] instanceof Comparable))
          {
            sortie = new java.util.Comparator()
            {
              public int compare(Object o1, Object o2)
              {
                String s1 = (o1 == null) ? "" : o1.toString();
                String s2 = (o2 == null) ? "" : o2.toString();
                return USE_COLLATOR_SORTING ? collie.compare(s1, s2) : s1.compareToIgnoreCase(s2);
              }
              private java.text.Collator collie;
              {
                if (USE_COLLATOR_SORTING)
                {
                  collie = java.text.Collator.getInstance(Sage.userLocale);
                  collie.setStrength(java.text.Collator.SECONDARY);
                }
              }
            };
          }
          if (invertOrder)
          {
            if (sortie != null)
            {
              final java.util.Comparator innerSort = sortie;
              sortie = new java.util.Comparator()
              {
                public int compare(Object o1, Object o2)
                {
                  return -1 * innerSort.compare(o1, o2);
                }
              };
            }
            else
            {
              sortie = new java.util.Comparator()
              {
                public int compare(Object o1, Object o2)
                {
                  return -1 * ((Comparable) o1).compareTo((Comparable) o2);
                }
              };
            }
          }
          if (sortie != null)
            java.util.Arrays.sort(currData, sortie);
          else
            java.util.Arrays.sort(currData);
          //if (Sage.DBG) System.out.println("Sort res=" + java.util.Arrays.asList(currData));
        }
        if (alreadySorted && invertOrder && currData != null)
        {
          // Reverse the order of the list
          for (int i = 0; i < currData.length/2; i++)
          {
            Object tmp = currData[currData.length - 1 - i];
            currData[currData.length - 1 - i] = currData[i];
            currData[i] = tmp;
          }
        }
        if (fooData instanceof java.util.Map)
        {
          // We need to build a new map with the sorted order
          java.util.Map rv = new java.util.LinkedHashMap();
          java.util.Map oldMap = (java.util.Map) fooData;
          for (int i = 0; i < currData.length; i++)
          {
            rv.put(currData[i], oldMap.get(currData[i]));
          }
          return rv;
        }
        return currData;
      }
      private java.util.Map<String, java.util.Comparator> optSortMap = new java.util.HashMap<String, java.util.Comparator>();

      {
        optSortMap.put("getoriginalairingdate", new java.util.Comparator()
        {
          public int compare(Object o1, Object o2)
          {
            Show s1 = getShowObj(o1);
            Show s2 = getShowObj(o2);
            long l1 = (s1 == null) ? 0 : s1.getOriginalAirDate();
            long l2 = (s2 == null) ? 0 : s2.getOriginalAirDate();
            if (l1 < l2)
              return -1;
            else if (l1 > l2)
              return 1;
            if (s1 != null && s2 != null)
            {
              int n1 = s1.getSeasonNumber();
              int n2 = s2.getSeasonNumber();
              int x = n1 - n2;
              if (x != 0 && n1 != 0 && n2 != 0)
                return x;
              n1 = s1.getEpisodeNumber();
              n2 = s2.getEpisodeNumber();
              x = n1 - n2;
              if (x != 0 && n1 != 0 && n2 != 0)
                return x;
            }
            Airing a1 = getAirObj(o1);
            Airing a2 = getAirObj(o2);
            l1 = (a1 == null) ? 0 : a1.getStartTime();
            l2 = (a2 == null) ? 0 : a2.getStartTime();
            return (l1 < l2) ? -1 : ((l1 > l2) ? 1 : 0);
          }
        });
        optSortMap.put("intelligent", SeekerSelector.getInstance().getMediaFileComparator(true));
        optSortMap.put("caseinsensitive", new java.util.Comparator()
        {
          public int compare(Object o1, Object o2)
          {
            String s1 = (o1 == null) ? "" : o1.toString();
            String s2 = (o2 == null) ? "" : o2.toString();
            return USE_COLLATOR_SORTING ? collie.compare(s1, s2) : s1.compareToIgnoreCase(s2);
          }
          private java.text.Collator collie;
          {
            if (USE_COLLATOR_SORTING)
            {
              collie = java.text.Collator.getInstance(Sage.userLocale);
              collie.setStrength(java.text.Collator.PRIMARY);
            }
          }
        });
        optSortMap.put("getairingstarttime", new java.util.Comparator()
        {
          public int compare(Object o1, Object o2)
          {
            Airing a1 = getAirObj(o1);
            Airing a2 = getAirObj(o2);
            long l1 = (a1 == null) ? 0 : a1.getStartTime();
            long l2 = (a2 == null) ? 0 : a2.getStartTime();
            return (l1 < l2) ? -1 : ((l1 > l2) ? 1 : 0);
          }
        });
        optSortMap.put("getchannelnumber", new java.util.Comparator()
        {
          public int compare(Object o1, Object o2)
          {
            Channel c1 = getChannelObj(o1);
            Channel c2 = getChannelObj(o2);
            String s1 = (c1 == null) ? "" : c1.getNumber();
            String s2 = (c2 == null) ? "" : c2.getNumber();
            try
            {
              int i1 = Integer.parseInt(s1.toString());
              int i2 = Integer.parseInt(s2.toString());
              return i1 - i2;
            }
            catch (Exception e)
            {
              s1 = UIManager.leadZero(s1.toString());
              s2 = UIManager.leadZero(s2.toString());

              return (s1.compareTo(s2));
            }
          }
        });
        optSortMap.put("getschedulestarttime", new java.util.Comparator()
        {
          public int compare(Object o1, Object o2)
          {
            Airing a1 = getAirObj(o1);
            Airing a2 = getAirObj(o2);
            long l1 = (a1 == null) ? 0 : a1.getSchedulingStart();
            long l2 = (a2 == null) ? 0 : a2.getSchedulingStart();
            return (l1 < l2) ? -1 : ((l1 > l2) ? 1 : 0);
          }
        });
        optSortMap.put("getshowepisode", new java.util.Comparator()
        {
          public int compare(Object o1, Object o2)
          {
            Show s1 = getShowObj(o1);
            Show s2 = getShowObj(o2);
            String x1 = (s1 == null) ? "" : s1.getEpisodeName();
            String x2 = (s2 == null) ? "" : s2.getEpisodeName();
            return x1.compareTo(x2);
          }
        });
        optSortMap.put("getshowtitle", new java.util.Comparator()
        {
          public int compare(Object o1, Object o2)
          {
            Show s1 = getShowObj(o1);
            Show s2 = getShowObj(o2);
            String x1 = (s1 == null) ? "" : s1.getTitle();
            String x2 = (s2 == null) ? "" : s2.getTitle();
            return x1.compareTo(x2);
          }
        });
        optSortMap.put("gettracknumber", new java.util.Comparator()
        {
          public int compare(Object o1, Object o2)
          {
            Airing a1 = getAirObj(o1);
            Airing a2 = getAirObj(o2);
            int l1 = (a1 == null) ? 0 : a1.getTrack();
            int l2 = (a2 == null) ? 0 : a2.getTrack();
            return l1 - l2;
          }
        });
        optSortMap.put("iswatched", new java.util.Comparator()
        {
          public int compare(Object o1, Object o2)
          {
            Airing a1 = getAirObj(o1);
            Airing a2 = getAirObj(o2);
            boolean b1 = (a1 != null && a1.isWatched());
            boolean b2 = (a2 != null && a2.isWatched());
            return (b1 == b2) ? 0 : (b1 ? 1 : -1);
          }
        });
      }
    });
    rft.put(new PredefinedJEPFunction("Database", "SortLexical", -1, new String[]{"Data", "Descending", "SortByMethod"})
    {

      /**
       * Sorts a list of data based on the result of calling the "SortByMethod" on each item and using toString on the return value of that method.
       * The order of the sort can be reversed using the Descending parameter. There are many cases where this will return the same thing as a call to {@link #Sort Sort()}
       * SortLexical should be used when sorting text if possible, as it uses more advanced language-specific sorting techniques to determine a proper order.
       * This sort is performed case-insensitive.
       * NOTE: If you pass more than 3 arguments to this function then the extra arguments will be passed along to the SortByMethod.
       * @param Data the data to sort, this must be a java.util.Collection, a java.util.Map, or an array; for Collections all the elements must be the same Class
       * @param Descending if true then the data will be sorted in descending order, if false then the order will be reversed
       * @param SortByMethod the method to call on each data item to get the value it should be sorted by, if this is null then the data elements are converted to Strings directly and then compared
       * @return the sorted data, for passed in Maps this'll be a sorted Map; for Collections or arrays this will be an Object[] array
       * @since 5.1
       *
       * @declaration public Object SortLexical(Object Data, boolean Descending, String SortByMethod);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception
      {
        java.util.Comparator sortie = null;
        Object[] currData;
        int extraSortArgs = curNumberOfParameters - 3;
        final java.util.ArrayList extraArgs = new java.util.ArrayList();
        while (extraSortArgs-- > 0)
          extraArgs.add(stack.pop());
        String filterMethName = getString(stack);
        final boolean invertOrder = evalBool(stack.pop());
        Object fooData = stack.pop();
        if (fooData instanceof java.util.Collection)
        {
          java.util.Collection collect = (java.util.Collection) fooData;
          if (collect.isEmpty())
            return Pooler.EMPTY_OBJECT_ARRAY;
          //java.util.Iterator walker = collect.iterator();
          // We can't be sure all the elements are the same class as the first one, so just use Object
          currData = collect.toArray();//(Object[])java.lang.reflect.Array.newInstance(walker.next().getClass(), collect.size()));
        }
        else if (fooData instanceof java.util.Map)
        {
          currData = (Object[]) ((java.util.Map) fooData).keySet().toArray();
        }
        else
          currData = (Object[]) fooData;
        updateTrimPronouns();
        // Make some of these explicit to speed them up
        if ("GetMediaTitle".equals(filterMethName))
        {
          sortie = new java.util.Comparator()
          {
            public int compare(Object o1, Object o2)
            {
              MediaFile c1 = getMediaFileObj(o1);
              MediaFile c2 = getMediaFileObj(o2);
              if (c1 == c2)
                return 0;
              if (c1 == null)
                return invertOrder ? -1 : 1;
              if (c2 == null)
                return invertOrder ? 1 : -1;
              return (invertOrder ? -1 : 1) *
                  (USE_COLLATOR_SORTING ? collie.compare(trimPronouns(c1.getMediaTitle()), trimPronouns(c2.getMediaTitle())) :
                    trimPronouns(c1.getMediaTitle()).compareToIgnoreCase(trimPronouns(c2.getMediaTitle())));
            }
            private java.text.Collator collie;
            {
              if (USE_COLLATOR_SORTING)
              {
                collie = java.text.Collator.getInstance(Sage.userLocale);
                collie.setStrength(java.text.Collator.SECONDARY);
              }
            }
          };
        }
        else if ("GetShowTitle".equals(filterMethName) || "GetAiringTitle".equals(filterMethName))
        {
          sortie = new java.util.Comparator()
          {
            public int compare(Object o1, Object o2)
            {
              Show c1 = getShowObj(o1);
              Show c2 = getShowObj(o2);
              if (c1 == c2)
                return 0;
              if (c1 == null)
                return invertOrder ? -1 : 1;
              if (c2 == null)
                return invertOrder ? 1 : -1;
              return (invertOrder ? -1 : 1) *
                  (USE_COLLATOR_SORTING ? collie.compare(trimPronouns(c1.getTitle()), trimPronouns(c2.getTitle())) :
                    trimPronouns(c1.getTitle()).compareToIgnoreCase(trimPronouns(c2.getTitle())));
            }
            private java.text.Collator collie;
            {
              if (USE_COLLATOR_SORTING)
              {
                collie = java.text.Collator.getInstance(Sage.userLocale);
                collie.setStrength(java.text.Collator.SECONDARY);
              }
            }
          };
        }
        else if ("GetAlbumArtist".equals(filterMethName))
        {
          sortie = new java.util.Comparator()
          {
            public int compare(Object o1, Object o2)
            {
              Album c1 = getAlbumObj(o1);
              Album c2 = getAlbumObj(o2);
              if (c1 == c2)
                return 0;
              if (c1 == null)
                return invertOrder ? -1 : 1;
              if (c2 == null)
                return invertOrder ? 1 : -1;
              return (invertOrder ? -1 : 1) *
                  (USE_COLLATOR_SORTING ? collie.compare(trimPronouns(c1.getArtist()), trimPronouns(c2.getArtist())) :
                    trimPronouns(c1.getArtist()).compareToIgnoreCase(trimPronouns(c2.getArtist())));
            }
            private java.text.Collator collie;
            {
              if (USE_COLLATOR_SORTING)
              {
                collie = java.text.Collator.getInstance(Sage.userLocale);
                collie.setStrength(java.text.Collator.SECONDARY);
              }
            }
          };
        }
        else if ("GetAlbumName".equals(filterMethName))
        {
          sortie = new java.util.Comparator()
          {
            public int compare(Object o1, Object o2)
            {
              Album c1 = getAlbumObj(o1);
              Album c2 = getAlbumObj(o2);
              if (c1 == c2)
                return 0;
              if (c1 == null)
                return invertOrder ? -1 : 1;
              if (c2 == null)
                return invertOrder ? 1 : -1;
              return (invertOrder ? -1 : 1) *
                  (USE_COLLATOR_SORTING ? collie.compare(trimPronouns(c1.getTitle()), trimPronouns(c2.getTitle())) :
                    trimPronouns(c1.getTitle()).compareToIgnoreCase(trimPronouns(c2.getTitle())));
            }
            private java.text.Collator collie;
            {
              if (USE_COLLATOR_SORTING)
              {
                collie = java.text.Collator.getInstance(Sage.userLocale);
                collie.setStrength(java.text.Collator.SECONDARY);
              }
            }
          };
        }
        else if ("GetChannelName".equals(filterMethName))
        {
          sortie = new java.util.Comparator()
          {
            public int compare(Object o1, Object o2)
            {
              Channel c1 = getChannelObj(o1);
              Channel c2 = getChannelObj(o2);
              if (c1 == c2)
                return 0;
              if (c1 == null)
                return invertOrder ? -1 : 1;
              if (c2 == null)
                return invertOrder ? 1 : -1;
              return (invertOrder ? -1 : 1) *
                  (USE_COLLATOR_SORTING ? collie.compare(trimPronouns(c1.getName()), trimPronouns(c2.getName())) :
                    trimPronouns(c1.getName()).compareToIgnoreCase(trimPronouns(c2.getName())));
            }
            private java.text.Collator collie;
            {
              if (USE_COLLATOR_SORTING)
              {
                collie = java.text.Collator.getInstance(Sage.userLocale);
                collie.setStrength(java.text.Collator.SECONDARY);
              }
            }
          };
        }
        else if ("GetShowEpisode".equals(filterMethName))
        {
          sortie = new java.util.Comparator()
          {
            public int compare(Object o1, Object o2)
            {
              Show c1 = getShowObj(o1);
              Show c2 = getShowObj(o2);
              if (c1 == c2)
                return 0;
              if (c1 == null)
                return invertOrder ? -1 : 1;
              if (c2 == null)
                return invertOrder ? 1 : -1;
              return (invertOrder ? -1 : 1) *
                  (USE_COLLATOR_SORTING ? collie.compare(trimPronouns(c1.getEpisodeName()), trimPronouns(c2.getEpisodeName())) :
                    trimPronouns(c1.getEpisodeName()).compareToIgnoreCase(trimPronouns(c2.getEpisodeName())));
            }
            private java.text.Collator collie;
            {
              if (USE_COLLATOR_SORTING)
              {
                collie = java.text.Collator.getInstance(Sage.userLocale);
                collie.setStrength(java.text.Collator.SECONDARY);
              }
            }
          };
        }
        else
        {
          final sage.jep.function.PostfixMathCommandI sortMeth = (filterMethName == null) ? null :
            (sage.jep.function.PostfixMathCommandI) Catbert.getAPI().get(filterMethName);
          sortie = new java.util.Comparator()
          {
            public int compare(Object o1, Object o2)
            {
              Object c1=null, c2=null;
              if (sortMeth != null)
              {
                Catbert.FastStack s = new Catbert.FastStack();
                s.push(o1);
                for (int i = extraArgs.size() - 1; i >= 0; i--)
                  s.push(extraArgs.get(i));
                sortMeth.setCurNumberOfParameters(1 + extraArgs.size());
                try
                {
                  sortMeth.run(s);
                }
                catch (Exception e)
                {
                  System.out.println("ERROR executing method in LexicalSort:" + e);
                  e.printStackTrace();
                }
                c1 = s.pop();
                s.push(o2);
                for (int i = extraArgs.size() - 1; i >= 0; i--)
                  s.push(extraArgs.get(i));
                sortMeth.setCurNumberOfParameters(1 + extraArgs.size());
                try
                {
                  sortMeth.run(s);
                }
                catch (Exception e)
                {
                  System.out.println("ERROR executing method in LexicalSort:" + e);
                  e.printStackTrace();
                }
                c2 = s.pop();
              }
              else
              {
                c1 = o1;
                c2 = o2;
              }
              if (c1 == c2)
                return 0;
              if (c1 == null)
                return invertOrder ? -1 : 1;
              if (c2 == null)
                return invertOrder ? 1 : -1;
              return (invertOrder ? -1 : 1) *
                  (USE_COLLATOR_SORTING ? collie.compare(trimPronouns(c1.toString()), trimPronouns(c2.toString())) :
                    trimPronouns(c1.toString()).compareToIgnoreCase(trimPronouns(c2.toString())));
            }
            private java.text.Collator collie;
            {
              if (USE_COLLATOR_SORTING)
              {
                collie = java.text.Collator.getInstance(Sage.userLocale);
                collie.setStrength(java.text.Collator.SECONDARY);
              }
            }
          };
        }
        if (currData != null)
        {
          java.util.Arrays.sort(currData, sortie);
          //if (Sage.DBG) System.out.println("Sort res=" + java.util.Arrays.asList(currData));
        }
        if (fooData instanceof java.util.Map)
        {
          // We need to build a new map with the sorted order
          java.util.Map rv = new java.util.LinkedHashMap();
          java.util.Map oldMap = (java.util.Map) fooData;
          for (int i = 0; i < currData.length; i++)
          {
            rv.put(currData[i], oldMap.get(currData[i]));
          }
          return rv;
        }
        return currData;
      }
    });
    rft.put(new PredefinedJEPFunction("Database", "GetAiringsOnChannelAtTime", 4, new String[] {"Channel", "StartTime",
        "EndTime","MustStartDuringTime"})
    {
      /**
       * Returns all of the Airing objects in the database that are on the specified channel during the specified time span.
       * @param Channel the Channel that the Airings need to be one
       * @param StartTime the start of the time window to search for Airings in
       * @param EndTime the end of the time window to search for Airings in
       * @param MustStartDuringTime if true, then only Airings that start during the time window will be returned, if false
       *         then any Airing that overlaps with the time window will be returned
       * @return the Airings on the specified channel within the specified time window
       *
       * @declaration public Airing[] GetAiringsOnChannelAtTime(Channel Channel, long StartTime, long EndTime, boolean MustStartDuringTime);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean must = evalBool(stack.pop());
        long end = getLong(stack);
        long start = getLong(stack);
        Channel c = getChannel(stack);
        return (c == null) ? null : Wizard.getInstance().getAirings(c.getStationID(), start, end, must);
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetAiringsOnViewableChannelsAtTime", 3, new String[] {"StartTime",
        "EndTime","MustStartDuringTime"})
    {
      /**
       * Returns all of the Airing objects in the database on all of the channels that are viewable during the specified time span.
       * @param StartTime the start of the time window to search for Airings in
       * @param EndTime the end of the time window to search for Airings in
       * @param MustStartDuringTime if true, then only Airings that start during the time window will be returned, if false
       *         then any Airing that overlaps with the time window will be returned
       * @return the Airings on all the viewable channels within the specified time window
       *
       * @declaration public Airing[] GetAiringsOnViewableChannelsAtTime(long StartTime, long EndTime, boolean MustStartDuringTime);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean must = evalBool(stack.pop());
        long end = getLong(stack);
        long start = getLong(stack);
        java.util.ArrayList rv = new java.util.ArrayList(500);
        Channel[] currChans = Wizard.getInstance().getChannels();
        for (int j = 0; j < currChans.length; j++)
          if (currChans[j].isViewable())
            rv.addAll(java.util.Arrays.asList(Wizard.getInstance().getAirings(currChans[j].getStationID(), start, end, must)));
        return rv.toArray(Pooler.EMPTY_AIRING_ARRAY);
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetAllNonMusicWithPerson", 1, new String[] {"Person"})
    {
      /**
       * Returns all Airings in the database that refer to content that is NOT a music file and includes the specified person
       * in the list of people involved (i.e. actors, directors, producers, etc.)
       * @param Person the name of the person to search for matching content on
       * @return an array of Airing objects that reference content that includes the specified person, music is not returned
       * @deprecated
       *
       * @declaration public Airing[] GetAllNonMusicWithPerson(String Person);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().searchByExactPerson(getPerson(stack));
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetAllNonMusicWithTitle", 1, new String[] {"Title"})
    {
      /**
       * Returns all Airings in the database that refer to content that is NOT a music file and has the specified title.
       * @param Title the title of the content must match this exactly
       * @return an array of Airing objects that reference content with the specified title, music is not returned
       * @deprecated
       *
       * @declaration public Airing[] GetAllNonMusicWithTitle(String Title);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().searchByExactTitle(getString(stack), ((DBObject.MEDIA_MASK_TV | DBObject.MEDIA_MASK_VIDEO
            | DBObject.MEDIA_MASK_PICTURE | DBObject.MEDIA_MASK_DVD | DBObject.MEDIA_MASK_BLURAY) & DBObject.MEDIA_MASK_ALL));
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchByPerson", -1, new String[] {"SearchString", "MediaMask"})
    {
      /**
       * Returns all Airings in the database that refer to content that is NOT a music file and includes the specified person
       * in the list of people involved (i.e. actors, directors, producers, etc.)
       * This is the same as {@link #GetAllNonMusicWithPerson GetAllNonMusicWithPerson()}
       * @param SearchString the name of the person to search for matching content on
       * @return an array of Airing objects that reference content that includes the specified person, music is not returned
       *
       * @declaration public Airing[] SearchByPerson(String SearchString);
       */

      /**
       * Returns all Airings in the database that refer to content that includes the specified person
       * in the list of people involved (i.e. actors, directors, producers, etc.). The content must also match
       * one of the media types specified in the MediaMask
       * @param SearchString the name of the person to search for matching content on
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Airing objects that reference content that includes the specified person and matches the media mask
       * @since 5.1
       *
       * @declaration public Airing[] SearchByPerson(String SearchString, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 2)
        {
          int masky = getMediaMask(stack);
          return Wizard.getInstance().searchByExactPerson(getPerson(stack), masky);
        }
        else
          return Wizard.getInstance().searchByExactPerson(getPerson(stack));
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchByText", -1, new String[] {"SearchString", "MediaMask"}, true)
    {
      /**
       * Searches the descriptions and episode names of all of the content in the database for the
       * specified search string. This search is case insensitive.
       * @param SearchString the string to search for
       * @return an array of Airings who's content has the specified search string in its description or episode name
       *
       * @declaration public Airing[] SearchByText(String SearchString);
       */

      /**
       * Searches the descriptions and episode names of all of the content in the database for the
       * specified search string. This search is case insensitive. The content must also match
       * one of the media types specified in the MediaMask
       * @param SearchString the string to search for
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Airings who's content has the specified search string in its description or episode name and matches the media mask
       * @since 5.1
       *
       * @declaration public Airing[] SearchByText(String SearchString, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 2)
        {
          int masky = getMediaMask(stack);
          return Wizard.getInstance().searchByText(getString(stack), masky);
        }
        else
          return Wizard.getInstance().searchByText(getString(stack), DBObject.MEDIA_MASK_ALL);
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchByTitle", -1, new String[] {"SearchString", "MediaMask"})
    {
      /**
       * Returns all Airings in the database that refer to content that is NOT a music file and has the specified title.
       * This is the same as {@link #GetAllNonMusicWithTitle GetAllNonMusicWithTitle()}
       * @param SearchString the title of the content must match this exactly
       * @return an array of Airing objects that reference content with the specified title, music is not returned
       *
       * @declaration public Airing[] SearchByTitle(String SearchString);
       */

      /**
       * Returns all Airings in the database that refer to content that has the specified title. The content must also match
       * one of the media types specified in the MediaMask
       * @param SearchString the title of the content must match this exactly
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Airing objects that reference content with the specified title and matches the media mask
       * @since 5.1
       *
       * @declaration public Airing[] SearchByTitle(String SearchString, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 2)
        {
          int masky = getMediaMask(stack);
          return Wizard.getInstance().searchByExactTitle(getString(stack), masky);
        }
        else
          return Wizard.getInstance().searchByExactTitle(getString(stack), DBObject.MEDIA_MASK_ALL);
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchForPeople", -1, new String[] {"SearchString", "MediaMask"}, true)
    {
      /**
       * Returns a list of all of the people in the database that include the search string in their name.
       * This search is case insensitive.
       * @param SearchString the string to search on
       * @return an array of Persons which represent all of the people in the database that matched the search
       *
       * @declaration public Person[] SearchForPeople(String SearchString);
       */

      /**
       * Returns a list of all of the people in the database that include the search string in their name.
       * This search is case insensitive. The content it references must also match one of the media types specified in the MediaMask.
       * @param SearchString the string to search on
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Persons which represent all of the people in the database that matched the search that also have content that matches the MediaMask
       * @since 5.1
       *
       * @declaration public Person[] SearchForPeople(String SearchString, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 2)
        {
          int masky = getMediaMask(stack);
          return Wizard.getInstance().searchForPeople(getString(stack), masky);
        }
        else
          return Wizard.getInstance().searchForPeople(getString(stack), DBObject.MEDIA_MASK_ALL);
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchForTitles", -1, new String[] {"SearchString", "MediaMask"}, true)
    {
      /**
       * Returns a list of all of the titles in the database that include the search string in them.
       * This search is case insensitive.
       * @param SearchString the string to search on
       * @return an array of Strings which represent all of the titles in the database that matched the search
       *
       * @declaration public String[] SearchForTitles(String SearchString);
       */

      /**
       * Returns a list of all of the titles in the database that include the search string in them.
       * This search is case insensitive. The content it references must also match one of the media types specified in the MediaMask.
       * @param SearchString the string to search on
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Strings which represent all of the titles in the database that matched the search that also have content that matches the MediaMask
       * @since 5.1
       *
       * @declaration public String[] SearchForTitles(String SearchString, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 2)
        {
          int masky = getMediaMask(stack);
          return Wizard.getInstance().searchForTitles(getString(stack), masky);
        }
        else
          return Wizard.getInstance().searchForTitles(getString(stack), DBObject.MEDIA_MASK_ALL);
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchForPeopleRegex", -1, new String[] {"RegexPattern", "MediaMask"})
    {
      /**
       * Returns a list of all of the people in the database that match the passed in regular expression.
       * @param RegexPattern The compiled regular expression used for matching (if it's not compiled, then it will be converted to a compiled regular expression)
       * @return an array of Persons which represent all of the people in the database that matched the search
       * @since 5.1
       *
       * @declaration public Person[] SearchForPeopleRegex(java.util.regex.Pattern RegexPattern);
       */

      /**
       * Returns a list of all of the people in the database that match the passed in regular expression.
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param RegexPattern The compiled regular expression used for matching (if it's not compiled, then it will be converted to a compiled regular expression)
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Persons which represent all of the people in the database that matched the search that also have content that matches the MediaMask
       * @since 5.1
       *
       * @declaration public Person[] SearchForPeopleRegex(java.util.regex.Pattern RegexPattern, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 2)
        {
          int masky = getMediaMask(stack);
          return Wizard.getInstance().searchForPeople(getRegex(stack), masky);
        }
        else
          return Wizard.getInstance().searchForPeople(getRegex(stack), DBObject.MEDIA_MASK_ALL);
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchForPeopleNTE", -1, new String[] {"NTEString", "MediaMask"}, true)
    {
      /**
       * Returns a list of all of the people in the database that match the passed in text, where
       * the Unicode characters u2460-u2468 and u24EA  (Unicode Circled Digits) in the text represent the
       * numeric Text Keys 1-9 and 0. This is similar to the predictive text entry input of mobile phones.<br>
       * The characters represented by the keys are defined by the client properties
       * <tt>"ui/numeric_text_input_&lt;ui/translation_language_code&gt;_&lt;key&gt;_lower</tt>
       *
       * @param NTEString A string containing a mix of normal and NumericTextKey characters (u2460-2468, u24ae)
       * @return an array of Persons which represent all of the people in the database that matched the search
       * @since 8.0
       *
       * @declaration public Person[] SearchForPeopleNTE(String NTEString);
       */

      /**
       * Returns a list of all of the people in the database that match the passed in text, where
       * the Unicode characters u2460-u2468 and u24EA  (Unicode Circled Digits) in the text represent the
       * numeric Text Keys 1-9 and 0. This is similar to the predictive text entry input of mobile phones.<br>
       * The characters represented by the keys are defined by the client properties
       * <tt>"ui/numeric_text_input_&lt;ui/translation_language_code&gt;_&lt;key&gt;_lower</tt>
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param NTEString A string containing a mix of normal and NumericTextKey characters (u2460-2468, u24ae)
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Persons which represent all of the people in the database that matched the search that also have content that matches the MediaMask
       * @since 8.0
       *
       * @declaration public Person[] SearchForPeopleNTE(String NTEString, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 2)
        {
          int masky = getMediaMask(stack);
          return Wizard.getInstance().searchForPeopleNTE(getString(stack), masky);
        }
        else
          return Wizard.getInstance().searchForPeopleNTE(getString(stack), DBObject.MEDIA_MASK_ALL);
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchForTitlesRegex", -1, new String[] {"RegexPattern", "MediaMask"})
    {
      /**
       * Returns a list of all of the titles in the database that match the passed in regular expression.
       * @param RegexPattern The compiled regular expression used for matching (if it's not compiled, then it will be converted to a compiled regular expression)
       * @return an array of Strings which represent all of the titles in the database that matched the search
       * @since 5.1
       *
       * @declaration public String[] SearchForTitlesRegex(java.util.regex.Pattern RegexPattern);
       */

      /**
       * Returns a list of all of the titles in the database that match the passed in regular expression.
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param RegexPattern The compiled regular expression used for matching (if it's not compiled, then it will be converted to a compiled regular expression)
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Strings which represent all of the titles in the database that matched the search that also have content that matches the MediaMask
       * @since 5.1
       *
       * @declaration public String[] SearchForTitlesRegex(java.util.regex.Pattern RegexPattern, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 2)
        {
          int masky = getMediaMask(stack);
          return Wizard.getInstance().searchForTitles(getRegex(stack), masky);
        }
        else
          return Wizard.getInstance().searchForTitles(getRegex(stack), DBObject.MEDIA_MASK_ALL);
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchForTitlesNTE", -1, new String[] {"NTEString", "MediaMask"})
    {
      /**
       * Returns a list of all of the titles in the database that match the passed in text, where
       * the Unicode characters u2460-u2468 and u24EA  (Unicode Circled Digits) in the text represent the
       * numeric Text Keys 1-9 and 0. This is similar to the predictive text entry input of mobile phones.<br>
       * The characters represented by the keys are defined by the client properties
       * <tt>"ui/numeric_text_input_&lt;ui/translation_language_code&gt;_&lt;key&gt;_lower</tt>
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param NTEString A string containing a mix of normal and NumericTextKey characters (u2460-2468, u24ae)
       * @return an array of Strings which represent all of the titles in the database that matched the search
       * @since 8.0
       *
       * @declaration public String[] SearchForTitlesNTE(String NTEString);
       */

      /**
       * Returns a list of all of the titles in the database that match the passed in text, where
       * the Unicode characters u2460-u2468 and u24EA  (Unicode Circled Digits) in the text represent the
       * numeric Text Keys 1-9 and 0. This is similar to the predictive text entry input of mobile phones.<br>
       * The characters represented by the keys are defined by the client properties
       * <tt>"ui/numeric_text_input_&lt;ui/translation_language_code&gt;_&lt;key&gt;_lower</tt>
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param NTEString A string containing a mix of normal and NumericTextKey characters (u2460-2468, u24ae)
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Strings which represent all of the titles in the database that matched the search that also have content that matches the MediaMask
       * @since 8.0
       *
       * @declaration public String[] SearchForTitlesNTE(String NTEString, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 2)
        {
          int masky = getMediaMask(stack);
          return Wizard.getInstance().searchForTitlesNTE(getString(stack), masky);
        }
        else
          return Wizard.getInstance().searchForTitlesNTE(getString(stack), DBObject.MEDIA_MASK_ALL);
      }});
    rft.put(new PredefinedJEPFunction("Database", "DataUnion", -1, new String[] { "DataSet1", "DataSet2" })
    {
      /**
       * Creates a Union of one or more sets of data. This method can have zero or more arguments.
       * The ordering of the elements is stable. Any element that is in any data set that is passed in will
       * be included in the returned set. Any duplicate items will be removed.
       * If the arguments are a java.util.Collection, java.util.Map or an array then each element in them will
       * be processed in the Union. If the argument is any other type then the argument itself will be processed in the Union.
       * @param DataSet1 one of the data sets to include in the union
       * @param DataSet2 another one of the data sets to include in the union
       * @return a java.util.Vector which is a union of all of the elements in the passed in arguments
       *
       * @declaration public java.util.Vector DataUnion(Object DataSet1, Object DataSet2);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.util.Vector rv = null;
        java.util.Map rm = null;
        java.util.Set alreadyAdded = new java.util.HashSet();
        java.util.Vector inputArgs = new java.util.Vector();
        for (int i = 0; i < curNumberOfParameters; i++)
          inputArgs.add(stack.pop());

        for (int i = inputArgs.size() - 1; i >= 0; i--)
        {
          Object o2 = inputArgs.get(i);
          if (o2 instanceof java.util.Collection)
          {
            java.util.Iterator walker = ((java.util.Collection) o2).iterator();
            while (walker.hasNext())
            {
              Object o = walker.next();
              if (alreadyAdded.add(o))
              {
                if (rv == null)
                  rv = new java.util.Vector();
                rv.add(o);
              }
            }
          }
          else if (o2 instanceof Object[])
          {
            Object[] oa = (Object[]) o2;
            for (int j = 0; j < oa.length; j++)
            {
              if (alreadyAdded.add(oa[j]))
              {
                if (rv == null)
                  rv = new java.util.Vector();
                rv.add(oa[j]);
              }
            }
          }
          else if (o2 instanceof java.util.Map)
          {
            if (rm == null)
              rm = new java.util.HashMap();
            rm.putAll((java.util.Map) o2);
          }
          else if (o2 != null && alreadyAdded.add(o2))
          {
            if (rv == null)
              rv = new java.util.Vector();
            rv.add(o2);
          }
        }
        if (rv == null)
        {
          if (rm == null)
            return new java.util.Vector();
          else
            return rm;
        }
        else
          return rv;
      }});
    rft.put(new PredefinedJEPFunction("Database", "DataIntersection", 2, new String[] { "DataSet1", "DataSet2" })
    {
      /**
       * Creates an intersection of two sets of data.
       * The ordering of the elements is stable. Any element that is in both of the data sets that are passed in will
       * be included in the returned set.
       * If the arguments are a java.util.Collection or an array then each element in them will
       * be processed in the intersection. If the argument is any other type then the argument itself will be processed in the intersection.
       * @param DataSet1 one of the data sets to include in the intersection
       * @param DataSet2 the other data set to include in the intersection
       * @return a java.util.Vector which is an intersection of all of the elements in the passed in arguments
       *
       * @declaration public java.util.Vector DataIntersection(Object DataSet1, Object DataSet2);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.util.Set okset = new java.util.HashSet();
        Object[] data1;
        Object[] data2;
        Object o = stack.pop();
        if (o instanceof java.util.Collection)
          okset.addAll((java.util.Collection) o);
        else if (o instanceof Object[])
          okset.addAll(java.util.Arrays.asList((Object[]) o));
        else if (o != null)
          okset.add(o);
        o = stack.pop();
        java.util.Vector rv = new java.util.Vector();
        if (o instanceof java.util.Collection)
        {
          java.util.Iterator walker = ((java.util.Collection) o).iterator();
          while (walker.hasNext())
          {
            Object foo = walker.next();
            if (okset.contains(foo))
              rv.add(foo);
          }
        }
        else if (o instanceof Object[])
        {
          Object[] oa = (Object[]) o;
          for (int i = 0; i < oa.length; i++)
            if (okset.contains(oa[i]))
              rv.add(oa[i]);
        }
        else if (okset.contains(o))
        {
          rv.add(o);
        }
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchSelectedFields", -1, new String[] {"SearchString", "CaseSensitive",
        "Titles", "Episode", "Description", "People", "Category", "Rated",
        "ExtendedRatings", "Year", "Misc", "MediaMask"}, true)
    {
      /**
       * Searches the specified fields of all the Airings in the database for the specified search string.
       * @param SearchString the string to search with
       * @param CaseSensitive if true then the search is case senstive, if false then it's case insensitive
       * @param Titles if true then the title fields will be searched, if false then they will not be
       * @param Episode if true then the episode fields will be searched, if false then they will be not be
       * @param Description if true then the description fields will be searched, if false then they will not be
       * @param People if true then the people fields will be searched, if false then they will not be
       * @param Category if true then the category fields will be searched, if false then they will not be
       * @param Rated if true then the rated fields will be searched, if false then they will not be
       * @param ExtendedRatings if true then the extended ratings fields will be searched, if false then they will not be
       * @param Year if true then the year fields will be searched, if false then they will not be
       * @param Misc if true then the miscellaneous fields will be searched, if false then they will not be
       * @return an array of Airings which matches the search criteria
       *
       * @declaration public java.util.Vector SearchSelectedFields(String SearchString, boolean CaseSensitive, boolean Titles, boolean Episode, boolean Description, boolean People, boolean Category, boolean Rated, boolean ExtendedRatings, boolean Year, boolean Misc);
       */

      /**
       * Searches the specified fields of all the Airings in the database for the specified search string.
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param SearchString the string to search with
       * @param CaseSensitive if true then the search is case senstive, if false then it's case insensitive
       * @param Titles if true then the title fields will be searched, if false then they will not be
       * @param Episode if true then the episode fields will be searched, if false then they will be not be
       * @param Description if true then the description fields will be searched, if false then they will not be
       * @param People if true then the people fields will be searched, if false then they will not be
       * @param Category if true then the category fields will be searched, if false then they will not be
       * @param Rated if true then the rated fields will be searched, if false then they will not be
       * @param ExtendedRatings if true then the extended ratings fields will be searched, if false then they will not be
       * @param Year if true then the year fields will be searched, if false then they will not be
       * @param Misc if true then the miscellaneous fields will be searched, if false then they will not be
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Airings which matches the search criteria
       * @since 5.1
       *
       * @declaration public java.util.Vector SearchSelectedFields(String SearchString, boolean CaseSensitive, boolean Titles, boolean Episode, boolean Description, boolean People, boolean Category, boolean Rated, boolean ExtendedRatings, boolean Year, boolean Misc, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int mediaMask = DBObject.MEDIA_MASK_ALL;
        if (curNumberOfParameters == 12)
        {
          mediaMask = getMediaMask(stack);
        }
        boolean miscb = evalBool(stack.pop());
        boolean yearb = evalBool(stack.pop());
        boolean erb = evalBool(stack.pop());
        boolean ratedb = evalBool(stack.pop());
        boolean catb = evalBool(stack.pop());
        boolean peopleb = evalBool(stack.pop());
        boolean descb = evalBool(stack.pop());
        boolean epsb = evalBool(stack.pop());
        boolean titb = evalBool(stack.pop());
        boolean caseb = evalBool(stack.pop());
        String str = getString(stack);
        if(Sage.DBG) System.out.println("CALLING SearchSelectedFields: " + str);
        return Wizard.getInstance().searchWithinFields(str, caseb, titb, epsb, descb, peopleb, catb,
            ratedb, erb, yearb, miscb, mediaMask, false);
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchSelectedExactFields", -1, new String[] {"SearchString", "CaseSensitive",
        "Titles", "Episode", "Description", "People", "Category", "Rated",
        "ExtendedRatings", "Year", "Misc", "MediaMask"})
    {
      /**
       * Searches the specified fields of all the Airings in the database for the specified search string. This requires
       * that the SearchString matches the specified field's value exactly. Unlike {@link #SearchSelectedFields SearchSelectedFields}
       * which only requires that the SearchString exist within the field's value somewhere (i.e. a substring)
       * @param SearchString the string to search with
       * @param CaseSensitive if true then the search is case senstive, if false then it's case insensitive
       * @param Titles if true then the title fields will be searched, if false then they will not be
       * @param Episode if true then the episode fields will be searched, if false then they will be not be
       * @param Description if true then the description fields will be searched, if false then they will not be
       * @param People if true then the people fields will be searched, if false then they will not be
       * @param Category if true then the category fields will be searched, if false then they will not be
       * @param Rated if true then the rated fields will be searched, if false then they will not be
       * @param ExtendedRatings if true then the extended ratings fields will be searched, if false then they will not be
       * @param Year if true then the year fields will be searched, if false then they will not be
       * @param Misc if true then the miscellaneous fields will be searched, if false then they will not be
       * @return an array of Airings which matches the search criteria
       * @since 4.1
       *
       * @declaration public java.util.Vector SearchSelectedExactFields(String SearchString, boolean CaseSensitive, boolean Titles, boolean Episode, boolean Description, boolean People, boolean Category, boolean Rated, boolean ExtendedRatings, boolean Year, boolean Misc);
       */

      /**
       * Searches the specified fields of all the Airings in the database for the specified search string. This requires
       * that the SearchString matches the specified field's value exactly. Unlike {@link #SearchSelectedFields SearchSelectedFields}
       * which only requires that the SearchString exist within the field's value somewhere (i.e. a substring)
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param SearchString the string to search with
       * @param CaseSensitive if true then the search is case senstive, if false then it's case insensitive
       * @param Titles if true then the title fields will be searched, if false then they will not be
       * @param Episode if true then the episode fields will be searched, if false then they will be not be
       * @param Description if true then the description fields will be searched, if false then they will not be
       * @param People if true then the people fields will be searched, if false then they will not be
       * @param Category if true then the category fields will be searched, if false then they will not be
       * @param Rated if true then the rated fields will be searched, if false then they will not be
       * @param ExtendedRatings if true then the extended ratings fields will be searched, if false then they will not be
       * @param Year if true then the year fields will be searched, if false then they will not be
       * @param Misc if true then the miscellaneous fields will be searched, if false then they will not be
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Airings which matches the search criteria
       * @since 5.1
       *
       * @declaration public java.util.Vector SearchSelectedExactFields(String SearchString, boolean CaseSensitive, boolean Titles, boolean Episode, boolean Description, boolean People, boolean Category, boolean Rated, boolean ExtendedRatings, boolean Year, boolean Misc, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int mediaMask = DBObject.MEDIA_MASK_ALL;
        if (curNumberOfParameters == 12)
        {
          mediaMask = getMediaMask(stack);
        }
        boolean miscb = evalBool(stack.pop());
        boolean yearb = evalBool(stack.pop());
        boolean erb = evalBool(stack.pop());
        boolean ratedb = evalBool(stack.pop());
        boolean catb = evalBool(stack.pop());
        boolean peopleb = evalBool(stack.pop());
        boolean descb = evalBool(stack.pop());
        boolean epsb = evalBool(stack.pop());
        boolean titb = evalBool(stack.pop());
        boolean caseb = evalBool(stack.pop());
        String str = getString(stack);
        return Wizard.getInstance().searchForMatchingFields(str, caseb, titb, epsb, descb, peopleb, catb,
            ratedb, erb, yearb, miscb, mediaMask);
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchSelectedFieldsRegex", -1, new String[] {"RegexPattern",
        "Titles", "Episode", "Description", "People", "Category", "Rated",
        "ExtendedRatings", "Year", "Misc", "MediaMask"})
    {
      /**
       * Searches the specified fields of all the Airings in the database and tries to match them against
       * the passed in regular expression.
       * @param RegexPattern The compiled regular expression used for matching (if it's not compiled, then it will be converted to a compiled regular expression)
       * @param Titles if true then the title fields will be searched, if false then they will not be
       * @param Episode if true then the episode fields will be searched, if false then they will be not be
       * @param Description if true then the description fields will be searched, if false then they will not be
       * @param People if true then the people fields will be searched, if false then they will not be
       * @param Category if true then the category fields will be searched, if false then they will not be
       * @param Rated if true then the rated fields will be searched, if false then they will not be
       * @param ExtendedRatings if true then the extended ratings fields will be searched, if false then they will not be
       * @param Year if true then the year fields will be searched, if false then they will not be
       * @param Misc if true then the miscellaneous fields will be searched, if false then they will not be
       * @return an array of Airings which matches the search criteria
       * @since 5.1
       *
       * @declaration public java.util.Vector SearchSelectedFieldsRegex(java.util.regex.Pattern RegexPattern, boolean Titles, boolean Episode, boolean Description, boolean People, boolean Category, boolean Rated, boolean ExtendedRatings, boolean Year, boolean Misc);
       */

      /**
       * Searches the specified fields of all the Airings in the database and tries to match them against
       * the passed in regular expression.
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param RegexPattern The compiled regular expression used for matching (if it's not compiled, then it will be converted to a compiled regular expression)
       * @param Titles if true then the title fields will be searched, if false then they will not be
       * @param Episode if true then the episode fields will be searched, if false then they will be not be
       * @param Description if true then the description fields will be searched, if false then they will not be
       * @param People if true then the people fields will be searched, if false then they will not be
       * @param Category if true then the category fields will be searched, if false then they will not be
       * @param Rated if true then the rated fields will be searched, if false then they will not be
       * @param ExtendedRatings if true then the extended ratings fields will be searched, if false then they will not be
       * @param Year if true then the year fields will be searched, if false then they will not be
       * @param Misc if true then the miscellaneous fields will be searched, if false then they will not be
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Airings which matches the search criteria
       * @since 5.1
       *
       * @declaration public java.util.Vector SearchSelectedFieldsRegex(java.util.regex.Pattern RegexPattern, boolean Titles, boolean Episode, boolean Description, boolean People, boolean Category, boolean Rated, boolean ExtendedRatings, boolean Year, boolean Misc, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int mediaMask = DBObject.MEDIA_MASK_ALL;
        if (curNumberOfParameters == 11)
        {
          mediaMask = getMediaMask(stack);
        }
        boolean miscb = evalBool(stack.pop());
        boolean yearb = evalBool(stack.pop());
        boolean erb = evalBool(stack.pop());
        boolean ratedb = evalBool(stack.pop());
        boolean catb = evalBool(stack.pop());
        boolean peopleb = evalBool(stack.pop());
        boolean descb = evalBool(stack.pop());
        boolean epsb = evalBool(stack.pop());
        boolean titb = evalBool(stack.pop());
        return Wizard.getInstance().searchFields(getRegex(stack), titb, epsb, descb, peopleb, catb,
            ratedb, erb, yearb, miscb, mediaMask);
      }});
    rft.put(new PredefinedJEPFunction("Database", "SearchSelectedFieldsNTE", -1, new String[] {"NTEString",
        "Titles", "Episode", "Description", "People", "Category", "Rated",
        "ExtendedRatings", "Year", "Misc", "MediaMask"}, true)
    {
      /**
       * Searches the specified fields of all the Airings in the database and tries to match them against
       * the passed in text, where the Unicode characters u2460-u2468 and u24EA  (Unicode Circled Digits) in the text represent the
       * numeric Text Keys 1-9 and 0. This is similar to the predictive text entry input of mobile phones.<br>
       * The characters represented by the keys are defined by the client properties
       * <tt>"ui/numeric_text_input_&lt;ui/translation_language_code&gt;_&lt;key&gt;_lower</tt>.
       * @param NTEString A string containing a mix of normal and NumericTextKey characters (u2460-2468, u24ae)
       * @param Titles if true then the title fields will be searched, if false then they will not be
       * @param Episode if true then the episode fields will be searched, if false then they will be not be
       * @param Description if true then the description fields will be searched, if false then they will not be
       * @param People if true then the people fields will be searched, if false then they will not be
       * @param Category if true then the category fields will be searched, if false then they will not be
       * @param Rated if true then the rated fields will be searched, if false then they will not be
       * @param ExtendedRatings if true then the extended ratings fields will be searched, if false then they will not be
       * @param Year if true then the year fields will be searched, if false then they will not be
       * @param Misc if true then the miscellaneous fields will be searched, if false then they will not be
       * @return an array of Airings which matches the search criteria
       * @since 8.0
       *
       * @declaration public java.util.Vector SearchSelectedFieldsNTE(String NTEString, boolean Titles, boolean Episode, boolean Description, boolean People, boolean Category, boolean Rated, boolean ExtendedRatings, boolean Year, boolean Misc);
       */

      /**
       * Searches the specified fields of all the Airings in the database and tries to match them against
       * the passed in text where the Unicode characters u2460-u2468 and u24EA  (Unicode Circled Digits) in the text represent the
       * numeric Text Keys 1-9 and 0. This is similar to the predictive text entry input of mobile phones.<br>
       * The characters represented by the keys are defined by the client properties
       * <tt>"ui/numeric_text_input_&lt;ui/translation_language_code&gt;_&lt;key&gt;_lower</tt>.
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param NTEString A string containing a mix of normal and NumericTextKey characters (u2460-2468, u24ae)
       * @param Titles if true then the title fields will be searched, if false then they will not be
       * @param Episode if true then the episode fields will be searched, if false then they will be not be
       * @param Description if true then the description fields will be searched, if false then they will not be
       * @param People if true then the people fields will be searched, if false then they will not be
       * @param Category if true then the category fields will be searched, if false then they will not be
       * @param Rated if true then the rated fields will be searched, if false then they will not be
       * @param ExtendedRatings if true then the extended ratings fields will be searched, if false then they will not be
       * @param Year if true then the year fields will be searched, if false then they will not be
       * @param Misc if true then the miscellaneous fields will be searched, if false then they will not be
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return an array of Airings which matches the search criteria
       * @since 8.0
       *
       * @declaration public java.util.Vector SearchSelectedFieldsNTE(String NTEString, boolean Titles, boolean Episode, boolean Description, boolean People, boolean Category, boolean Rated, boolean ExtendedRatings, boolean Year, boolean Misc, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int mediaMask = DBObject.MEDIA_MASK_ALL;
        if (curNumberOfParameters == 11)
        {
          mediaMask = getMediaMask(stack);
        }
        boolean miscb = evalBool(stack.pop());
        boolean yearb = evalBool(stack.pop());
        boolean erb = evalBool(stack.pop());
        boolean ratedb = evalBool(stack.pop());
        boolean catb = evalBool(stack.pop());
        boolean peopleb = evalBool(stack.pop());
        boolean descb = evalBool(stack.pop());
        boolean epsb = evalBool(stack.pop());
        boolean titb = evalBool(stack.pop());
        String str = getString(stack);
        if(Sage.DBG) System.out.println("CALLING SearchSelectedFieldsNTE: " + str);
        return Wizard.getInstance().searchFieldsNTE(str, titb, epsb, descb, peopleb, catb,
            ratedb, erb, yearb, miscb, mediaMask, false);
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetChannelsOnLineup", 1, new String[] {"Lineup"})
    {
      /**
       * Returns all of the Channel objects in the database that are on the specified Lineup.
       * @param Lineup the name of the EPG lineup to get the channels for
       * @return an array of Channel objects that are on the specified Lineup
       *
       * @declaration public Channel[] GetChannelsOnLineup(String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int[] statIDs = EPG.getInstance().getAllStations(
            EPG.getInstance().getProviderIDForEPGDSName(getString(stack)));
        Channel[] rv = new Channel[statIDs.length];
        Wizard wiz = Wizard.getInstance();
        for (int i = 0; i < rv.length; i++)
          rv[i] = wiz.getChannelForStationID(statIDs[i]);
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetAllTitles", -1, new String[] {"MediaMask"})
    {
      /**
       * Gets all of the titles that are in the database.
       * @return a list of all of the titles that are in the database
       *
       * @declaration public String[] GetAllTitles();
       */

      /**
       * Gets all of the titles that are in the database.
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return a list of all of the titles that are in the database that also have content that matches the MediaMask
       * @since 5.1
       *
       * @declaration public String[] GetAllTitles(String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int mediaMask = DBObject.MEDIA_MASK_ALL;
        if (curNumberOfParameters == 1)
        {
          mediaMask = getMediaMask(stack);
        }
        return Wizard.getInstance().getAllTitles(mediaMask);
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetAllPeople", -1, new String[] {"MediaMask"})
    {
      /**
       * Gets all of the people that are in the database.
       * @return a list of all of the names of people in the database
       *
       * @declaration public String[] GetAllPeople();
       */

      /**
       * Gets all of the people that are in the database.
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return a list of all of the names of people in the database that also have content that matches the MediaMask
       * @since 5.1
       *
       * @declaration public String[] GetAllPeople(String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int mediaMask = DBObject.MEDIA_MASK_ALL;
        if (curNumberOfParameters == 1)
        {
          mediaMask = getMediaMask(stack);
        }
        return Wizard.getInstance().getAllPeople(mediaMask);
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetAllCategories", -1, new String[] {"MediaMask"})
    {
      /**
       * Gets all of the categories that are in the database.
       * @return all of the names of categories that are in the database
       *
       * @declaration public String[] GetAllCategories();
       */

      /**
       * Gets all of the categories that are in the database.
       * The content it references must also match one of the media types specified in the MediaMask.
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return all of the names of categories that are in the database that also have content that matches the MediaMask
       * @since 5.1
       *
       * @declaration public String[] GetAllCategories(String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int mediaMask = DBObject.MEDIA_MASK_ALL;
        if (curNumberOfParameters == 1)
        {
          mediaMask = getMediaMask(stack);
        }
        return Wizard.getInstance().getAllCategories(mediaMask);
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetAllGroupingCategories", -1, new String[] {"MediaMask"})
    {
      /**
       * Gets all of the categories that are in the database. This is different than GetAllCategories because this one will break apart
       * any comma or semicolon delimited category lists into multiple different categories. i.e. if you have "Comedy; Horror" as a category
       * this API call will break it up into Comedy and Horror as two separate categories. This call will also coalesce any case-sensitive differences in category names.
       * @return all of the names of categories that are in the database with multi-categories broken apart
       * @since 7.0
       * @declaration public String[] GetAllGroupingCategories();
       */

      /**
       * Gets all of the categories that are in the database.
       * The content it references must also match one of the media types specified in the MediaMask.
       * This is different than GetAllCategories because this one will break apart
       * any comma or semicolon delimited category lists into multiple different categories. i.e. if you have "Comedy; Horror" as a category
       * this API call will break it up into Comedy and Horror as two separate categories. This call will also coalesce any case-sensitive differences in category names.
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return all of the names of categories that are in the database that also have content that matches the MediaMask with multi-categories broken apart
       * @since 7.0
       *
       * @declaration public String[] GetAllGroupingCategories(String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int mediaMask = DBObject.MEDIA_MASK_ALL;
        if (curNumberOfParameters == 1)
        {
          mediaMask = getMediaMask(stack);
        }
        String[] allCats = Wizard.getInstance().getAllCategories(mediaMask);
        java.util.Map catMap = new java.util.HashMap();
        for (int i = 0; i < allCats.length; i++)
        {
          java.util.StringTokenizer toker = new java.util.StringTokenizer(allCats[i], ";,");
          while (toker.hasMoreTokens())
          {
            String toke = toker.nextToken().trim();
            String tokeUp = toke.toUpperCase();
            if (!catMap.containsKey(tokeUp))
              catMap.put(tokeUp, toke);
          }
        }
        String[] rv = (String[]) catMap.values().toArray(Pooler.EMPTY_STRING_ARRAY);
        java.util.Arrays.sort(rv);
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetDatabaseLastModifiedTime", new String[] {"MediaMask"})
    {
      /**
       * Returns the last modification time for objects that match anything in the specified MediaMask. This is useful
       * for knowing when to clear caches that are used to optimize UI rendering.
       * @param MediaMask string specifying what content types  (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return the last modification time of anything in the DB that matches anything in the specified MediaMask
       * @since 5.1
       *
       * @declaration public long GetDatabaseLastModifiedTime(String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int mediaMask = getMediaMask(stack);
        return new Long(Wizard.getInstance().getLastModified(mediaMask));
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetFilesWithImportPrefix", new String[] {"MediaData", "ImportPrefix", "IncludeFiles", "IncludeFolders", "GroupFolders"})
    {
      /**
       * Returns a list of java.io.File objects w/ the specified MediaMask whos import prefix matches that of the argument
       * @param MediaData can either by a MediaMask string specifying what content types  ('M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay) or it can be an array/collection of the specific MediaFile objects to analyze
       * @param ImportPrefix a string specifying a subpath that must match the start of the import files path relative to its import root
       * @param IncludeFiles if true, then MediaFile objects with a complete prefix match will be returned
       * @param IncludeFolders if true, then MediaFile objects with a partial prefix match will be returned
       * @param GroupFolders if true, then MediaFile objects with partial prefixes that match will be grouped by their next path section; extract the 'null' keyed value to get the list of the files
       * @return a Vector or Map of java.io.File objects w/ the specified MediaMask whos import prefix matches that of the argument; a Map will be returned if GroupFolders is set to true
       * @since 6.4
       *
       * @declaration public Object GetFilesWithImportPrefix(Object MediaData, String ImportPrefix, boolean IncludeFiles, boolean IncludeFolders, boolean GroupFolders);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean groupFolders = evalBool(stack.pop());
        boolean includeFolders = evalBool(stack.pop());
        boolean includeFiles = evalBool(stack.pop());
        Object[] rawFiles = null;
        String prefix = getString(stack);
        Object mData = stack.pop();
        int mediaMask = 0;
        boolean validMask = false;
        if (mData instanceof Object[])
          rawFiles = (Object[]) mData;
        else if (mData instanceof java.util.Collection)
          rawFiles = ((java.util.Collection) mData).toArray();
        else if (mData != null)
          mediaMask = DBObject.getMediaMaskFromString(mData.toString());
        else
        {
          // null return
          return groupFolders ? (Object)new java.util.LinkedHashMap() : (Object)new java.util.Vector();
        }
        java.util.Vector rv = new java.util.Vector(500);
        java.util.Map groupy = null;
        if (!includeFiles && !includeFolders)
          return rv;
        if (groupFolders)
        {
          groupy = new java.util.LinkedHashMap();
          groupy.put(null, rv);
        }
        // Use direct access to the table to speed this up significantly
        if (rawFiles == null)
          rawFiles = Wizard.getInstance().getRawAccess(Wizard.MEDIAFILE_CODE, (byte) 0);
        int prefixLength = prefix.length();
        for (int i = 0; i < rawFiles.length; i++)
        {
          MediaFile mf = (MediaFile) rawFiles[i];
          if (mf == null || mf.isTV() || !mf.isArchiveFile() || (mediaMask != 0 && !mf.hasMediaMaskAny(mediaMask)))
            continue;
          String name = mf.getName();
          if (name.startsWith(prefix))
          {
            if (!groupFolders && includeFiles && includeFolders)
              rv.add(mf.getFile(0));
            else
            {
              // Find out if it's a folder or a file
              boolean isFolder = false;
              int nextIdx = name.indexOf('/', prefixLength);
              isFolder = (nextIdx != -1) && nextIdx < name.length() - 1;
              if (isFolder && mf.isDVD())
              {
                // Check if it's a VIDEO_TS folder
                if (name.regionMatches(true, nextIdx + 1, Seeker.DVD_VOLUME_SECRET, 0, Seeker.DVD_VOLUME_SECRET.length()))
                  isFolder = false;
              }
              else if (isFolder && mf.isBluRay())
              {
                // Check if it's a BDMV folder
                if (name.regionMatches(true, nextIdx + 1, Seeker.BLURAY_VOLUME_SECRET, 0, Seeker.BLURAY_VOLUME_SECRET.length()))
                  isFolder = false;
              }
              if ((includeFolders && isFolder) || (includeFiles && !isFolder))
              {
                if (groupFolders)
                {
                  if (isFolder)
                  {
                    String subPath = name.substring(prefixLength, nextIdx);
                    java.util.Vector currList = (java.util.Vector) groupy.get(subPath);
                    if (currList == null)
                      groupy.put(subPath, currList = new java.util.Vector());
                    currList.add(mf.getFile(0));
                  }
                  else
                    rv.add(mf.getFile(0));
                }
                else
                  rv.add(mf.getFile(0));
              }
            }
          }
        }
        return groupFolders ? (Object)groupy : (Object)rv;
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetMediaFilesWithImportPrefix", new String[] {"MediaData", "ImportPrefix", "IncludeFiles", "IncludeFolders", "GroupFolders"})
    {
      /**
       * Returns a list or map of MediaFile objects w/ the specified MediaMask whos import prefix matches that of the argument
       * @param MediaData can either by a MediaMask string specifying what content types  ('M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay) or it can be an array/collection of the specific MediaFile objects to analyze
       * @param ImportPrefix a string specifying a subpath that must match the start of the import files path relative to its import root
       * @param IncludeFiles if true, then MediaFile objects with a complete prefix match will be returned
       * @param IncludeFolders if true, then MediaFile objects with a partial prefix match will be returned
       * @param GroupFolders if true, then MediaFile objects with partial prefixes that match will be grouped by their next path section; extract the 'null' keyed value to get the list of the files
       * @return a Vector or Map of MediaFile objects w/ the specified MediaMask whos import prefix matches that of the argument; a Map will be returned if GroupFolders is set to true
       * @since 6.4
       *
       * @declaration public Object GetMediaFilesWithImportPrefix(Object MediaData, String ImportPrefix, boolean IncludeFiles, boolean IncludeFolders, boolean GroupFolders);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean groupFolders = evalBool(stack.pop());
        boolean includeFolders = evalBool(stack.pop());
        boolean includeFiles = evalBool(stack.pop());
        String prefix = getString(stack);
        Object[] rawFiles = null;
        Object mData = stack.pop();
        int mediaMask = 0;
        boolean validMask = false;
        if (mData instanceof Object[])
          rawFiles = (Object[]) mData;
        else if (mData instanceof java.util.Collection)
          rawFiles = ((java.util.Collection) mData).toArray();
        else if (mData != null)
          mediaMask = DBObject.getMediaMaskFromString(mData.toString());
        else
        {
          // null return
          return groupFolders ? (Object)new java.util.LinkedHashMap() : (Object)new java.util.Vector();
        }
        java.util.Vector rv = new java.util.Vector(500);
        java.util.Map groupy = null;
        if (!includeFiles && !includeFolders)
          return rv;
        if (groupFolders)
        {
          groupy = new java.util.LinkedHashMap();
          groupy.put(null, rv);
        }
        // Use direct access to the table to speed this up significantly
        if (rawFiles == null)
          rawFiles = Wizard.getInstance().getRawAccess(Wizard.MEDIAFILE_CODE, (byte) 0);
        int prefixLength = prefix.length();
        for (int i = 0; i < rawFiles.length; i++)
        {
          MediaFile mf = (MediaFile) rawFiles[i];
          if (mf == null || mf.isTV() || !mf.isArchiveFile() || (mediaMask != 0 && !mf.hasMediaMaskAny(mediaMask)))
            continue;
          String name = mf.getName();
          if (name.startsWith(prefix))
          {
            if (!groupFolders && includeFiles && includeFolders)
              rv.add(mf);
            else
            {
              // Find out if it's a folder or a file
              boolean isFolder = false;
              int nextIdx = name.indexOf('/', prefixLength);
              isFolder = (nextIdx != -1) && nextIdx < name.length() - 1;
              if (isFolder && mf.isDVD())
              {
                // Check if it's a VIDEO_TS folder
                if (name.regionMatches(true, nextIdx + 1, Seeker.DVD_VOLUME_SECRET, 0, Seeker.DVD_VOLUME_SECRET.length()))
                  isFolder = false;
              }
              else if (isFolder && mf.isBluRay())
              {
                // Check if it's a BDMV folder
                if (name.regionMatches(true, nextIdx + 1, Seeker.BLURAY_VOLUME_SECRET, 0, Seeker.BLURAY_VOLUME_SECRET.length()))
                  isFolder = false;
              }
              if ((includeFolders && isFolder) || (includeFiles && !isFolder))
              {
                if (groupFolders)
                {
                  if (isFolder)
                  {
                    String subPath = name.substring(prefixLength, nextIdx);
                    java.util.Vector currList = (java.util.Vector) groupy.get(subPath);
                    if (currList == null)
                      groupy.put(subPath, currList = new java.util.Vector());
                    currList.add(mf);
                  }
                  else
                    rv.add(mf);
                }
                else
                  rv.add(mf);
              }
            }
          }
        }
        return groupFolders ? (Object)groupy : (Object)rv;
      }});
    rft.put(new PredefinedJEPFunction("Database", "IsDatabaseMemoryMaxed")
    {
      /**
       * Returns true if the database has maxed out its memory usage and cannot add more content
       * @return true if the database has maxed out its memory usage and cannot add more content
       *
       * @since 6.5
       *
       * @declaration public boolean IsDatabaseMemoryMaxed();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Database", "StripLeadingArticles", new String[] { "Text" })
    {
      /**
       * Strips any leading 'a, an or the' prefixes from the passed in string and returns the resulting string.
       * If the property "ui/ignore_the_when_sorting" is set to false, this method will do nothing. The articles stripped
       * by this method can be defined with the property "ui/prefixes_to_ignore_on_sort".
       * @param Text the string to strip the leading articles from
       * @return the String after the leading articles have been stripped from the past in string
       *
       * @since 7.0
       *
       * @declaration public String StripLeadingArticles(String Text);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        return (s == null) ? null : trimPronouns(s);
      }});
    rft.put(new PredefinedJEPFunction("Database", "GetMediaMask", new String[] { "DBObject" })
    {
      /**
       * Returns a string which represents the different media categories the specified DBObject belongs to.
       * This may contain any of the following characters respectively: T = TV, D = DVD, V = Video, M = Music,
       * P = Photos, B = BluRay, O = VOD, N = Netflix, U = VUDU.
       * @param DBObject the database object to get the media mask of; should be an Airing, Show or MediaFile
       * @return the media mask string for the passed in DBObject
       *
       * @since 8.0
       *
       * @declaration public String GetMediaMask(DBObject DBObject);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof DBObject)
          return ((DBObject) o).getMediaMaskString();
        Airing a = getAirObj(o);
        if (a != null)
          return a.getMediaMaskString();
        return "";
      }});
    rft.put(new PredefinedJEPFunction("Database", "HasMediaMask", new String[] { "DBObject", "MediaMask" })
    {
      /**
       * Tests whether the passed in DBObject matches any of the categories specified in the passed in MediaMask.
       * This may contain any of the following characters respectively: T = TV, D = DVD, V = Video, M = Music,
       * P = Photos, B = BluRay, O = VOD, N = Netflix, U = VUDU.
       * @param DBObject the database object to get the media mask of; should be an Airing, Show or MediaFile
       * @param MediaMask this may contain any of the following characters respectively: T = TV, D = DVD, V = Video, M = Music, P = Photos, B = BluRay, O = VOD, N = Netflix, U = VUDU
       * @return true if the passed in object has any of the media masks from the passed in mask string, false otherwise
       *
       * @since 8.0
       *
       * @declaration public boolean HasMediaMask(DBObject DBObject, String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int mediaMask = getMediaMask(stack);
        Object o = stack.pop();
        if (o instanceof DBObject)
          return ((DBObject) o).hasMediaMaskAny(mediaMask) ? Boolean.TRUE : Boolean.FALSE;
        Airing a = getAirObj(o);
        if (a != null)
          return a.hasMediaMaskAny(mediaMask) ? Boolean.TRUE : Boolean.FALSE;
        return Boolean.FALSE;
      }});

    rft.put(new PredefinedJEPFunction("Database", "SearchForChannel", -1, new String[] {"SearchString", "IncludeNonViewable"})
    {
      /**
       * Returns all Channels in the database (disabled or not) that match the
       * SearchString in their channel number, name, callsign or network. Results
       * will be returned in StationID order
       *
       * @param SearchString the text to search for
       * @return an array of Channel objects that includes the SearchString
       * @since 8.1
       *
       * @declaration public Channel[] SearchForChannel(String SearchString);
       */

      /**
       * Returns all Channels in the database that match the SearchString in their
       * channel number, name, callsign or network. Results will be returned in
       * StationID order
       *
       * @param SearchString the text to search for
       * @param IncludeNonViewable whether to include Non-Viewable (disabled) channels in the results
       * @return an array of Channel objects that includes the SearchString
       * @since 8.1
       *
       * @declaration public Channel[] SearchForChannel(String SearchString, Boolean IncludeNonViewable);
       */

      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean includeDisabled = true;
        if (curNumberOfParameters == 2)
        {
          includeDisabled=evalBool(stack.pop());
        }
        return Wizard.getInstance().searchForChannel(getString(stack), includeDisabled);
      }});
    /*
    rft.put(new PredefinedJEPFunction("Database", "", 1, new String[] {""})
    {public Object runSafely(Catbert.FastStack stack) throws Exception{
       return Wizard.getInstance().;
      }});
     */
  }
}
