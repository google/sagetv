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
import sage.epg.sd.SDErrors;
import sage.epg.sd.SDRipper;
import sage.epg.sd.json.programs.SDInProgressSport;

/**
 * Show represents detailed information about content. This is where the actual metadata information is stored.
 * Show is separated from Airing because there can be multiple Airings of the same Show.
 * <p>
 * SageTV will automatically convert the following types to Show if used for a parameter that requires the Show type:<p>
 * Airing - every Airing corresponds to a single Show which describes the Airing's content in more detail, so the Airing's Show is used<p>
 * MediaFile - this is resolved to an Airing by the 1:1 relationship between MediaFiles and Airings, and then the Airing is resolved to a Show
 */
public class ShowAPI {
  private ShowAPI() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Show", "IsShowEPGDataUnique", 1, new String[] { "Show" })
    {
      /**
       * If this is true, then two Airings that both represent this Show will contain the same content.
       * If this is false then it means the EPG metadata for the content is 'generic' two different Airings
       * each with this Show for its metadata may actually represent different content
       * @param Show the Show object
       * @return true if all Airings of this Show represent the same content, false otherwise
       *
       * @declaration public boolean IsShowEPGDataUnique(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Boolean.valueOf(BigBrother.isUnique(getShow(stack)));
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowMisc", 1, new String[] { "Show" })
    {
      /**
       * Returns the miscellaneous metadata for this Show. This includes things such as
       * the star rating for a movie, the studio a movie was produced at, etc.
       * @param Show the Show object
       * @return the miscellaneous metadata for this Show
       *
       * @declaration public String GetShowMisc(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? "" : s.getBonusesString();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowCategory", 1, new String[] { "Show" })
    {
      /**
       * Returns the category for the specified Show. For music files, this will be the genre.
       * @param Show the Show object
       * @return the category for the Show
       *
       * @declaration public String GetShowCategory(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object obj = stack.pop();
        Show s = getShowObj(obj);
        if (s != null)
          return s.getCategory();
        // Check for an Album instead since it can also have a year
        Album al = getAlbumObj(obj);
        if (al != null)
          return al.getGenre();
        // Check for a SeriesInfo category
        SeriesInfo si = getSeriesInfoObj(obj);
        return (si == null) ? "" : si.getCategory();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowSubCategory", 1, new String[] { "Show" })
    {
      /**
       * Returns the subcategory for the specified Show
       * @param Show the Show object
       * @return the subcategory for the Show
       *
       * @declaration public String GetShowSubCategory(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object obj = stack.pop();
        Show s = getShowObj(obj);
        if (s != null)
          return s.getSubCategory();
        // Check for a SeriesInfo category
        SeriesInfo si = getSeriesInfoObj(obj);
        return (si == null) ? "" : si.getSubCategory();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowCategoriesString", -1, new String[] { "Show", "Delimiter" })
    {
      /**
       * Returns a String of categories for the Show, separated by '/' if there are multiple levels of categories. For music files, this will be the genre.
       * @param Show the Show object
       * @return the categories for the Show
       * @since 7.1
       *
       * @declaration public String GetShowCategoriesString(Show Show);
       */

      /**
       * Returns a String of categories for the Show, separated by the specified delimiter if there are multiple levels of categories. For music files, this will be the genre.
       * @param Show the Show object
       * @param Delimiter the string to use to separate multiple categories
       * @return the categories for the Show
       * @since 8.0
       *
       * @declaration public String GetShowCategoriesString(Show Show, String Delimiter);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String delim = " / ";
        if (curNumberOfParameters == 2)
          delim = getString(stack);
        Object obj = stack.pop();
        Show s = getShowObj(obj);
        if (s != null)
          return s.getCategoriesString(delim);
        // Check for an Album instead since it can also have a year
        Album al = getAlbumObj(obj);
        if (al != null)
          return al.getGenre();
        // Check for a SeriesInfo category
        SeriesInfo si = getSeriesInfoObj(obj);
        return (si == null) ? "" : si.getCategory();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowCategoriesList", 1, new String[] { "Show" })
    {
      /**
       * Returns a String array of categories for the Show. For music files, this will be the genre.
       * @param Show the Show object
       * @return the categories for the Show
       * @since 7.1
       *
       * @declaration public String[] GetShowCategoriesList(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object obj = stack.pop();
        Show s = getShowObj(obj);
        if (s != null)
          return s.getCategories();
        // Check for an Album instead since it can also have a year
        Album al = getAlbumObj(obj);
        if (al != null)
          return new String[] { al.getGenre() };
        // Check for a SeriesInfo category
        SeriesInfo si = getSeriesInfoObj(obj);
        return (si == null) ? Pooler.EMPTY_STRING_ARRAY : new String[] { si.getCategory() };
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowDescription", 1, new String[] { "Show" })
    {
      // For caching the last retrieved description which allows faster access when re-using the description
      // in a demand-loaded desc environment
      private Show lastShowUsed;
      private String lastDesc;
      /**
       * Returns the description for the specified Show
       * @param Show the Show object
       * @return the desccription for the Show
       *
       * @declaration public String GetShowDescription(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object obj = stack.pop();
        Show s = getShowObj(obj);
        if (s != null)
          return s.getDesc();
        // Also check for a SeriesInfo
        SeriesInfo si = getSeriesInfoObj(obj);
        return (si == null) ? "" : si.getDescription();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowEpisode", 1, new String[] { "Show" })
    {
      /**
       * Returns the episode name for the specified Show. For music files, this will be the name of the song.
       * @param Show the Show object
       * @return the episode name for the specified Show. For music files, this will be the name of the song. For imported videos, this will be the title of the file
       *
       * @declaration public String GetShowEpisode(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? "" : s.getEpisodeName();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowExpandedRatings", 1, new String[] { "Show" })
    {
      /**
       * Returns the epxanded ratings information for the specified Show. This includes thigs like
       * Violence, Nudity, Adult Language, etc.
       * @param Show the Show object
       * @return the expanded ratings for the Show
       *
       * @declaration public String GetShowExpandedRatings(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? "" : Catbert.getPrettyStringList(s.getExpandedRatings());
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowParentalRating", 1, new String[] { "Show" })
    {
      /**
       * Returns the parental rating for this show. The parental rating field in Airing is used instead of this in the standard implementation.
       * @deprecated
       * @param Show the Show object
       * @return the parental rating info for this show
       *
       * @declaration public String GetShowParentalRating(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? "" : s.getParentalRating();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowRated", 1, new String[] { "Show" })
    {
      /**
       * Returns the MPAA rating for the specified Show (only used for movies).
       * @param Show the Show object
       * @return the MPAA rating for this Show, will be one of: G, PG, R, PG-13, etc.
       *
       * @declaration public String GetShowRated(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? "" : s.getRated();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowDuration", 1, new String[] { "Show" })
    {
      /**
       * Returns the duration of the specified Show. Most Shows do not contain duration information, with the exception
       * of movies whose show duration indicates the runing time of the movie.
       * @param Show the Show object
       * @return the duration in milliseconds of the specified Show, 0 if it is not set
       *
       * @declaration public long GetShowDuration(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? new Long(0) : new Long(s.getDuration());
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowTitle", 1, new String[] { "Show" })
    {
      /**
       * Returns the title of the specified Show. For music this will correspond to the Album name. For imported videos, For imported videos, this will be the title of the file with the relative import path as it's prefix.
       * @param Show the Show object
       * @return the title of the specified Show
       *
       * @declaration public String GetShowTitle(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object obj = stack.pop();
        Show s = getShowObj(obj);
        if (s != null)
          return s.getTitle();
        // Check for an Album instead since it can also have a title
        Album al = getAlbumObj(obj);
        if (al != null)
          return al.getTitle();
        // Also check for a SeriesInfo
        SeriesInfo si = getSeriesInfoObj(obj);
        return (si == null) ? "" : si.getTitle();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowYear", 1, new String[] { "Show" })
    {
      /**
       * Gets the year of the specified Show. This is usually only valid for movies.
       * @param Show the Show object
       * @return the year the specified Show was produced in
       *
       * @declaration public String GetShowYear(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object obj = stack.pop();
        Show s = getShowObj(obj);
        if (s != null)
          return s.getYear();
        // Check for an Album instead since it can also have a year
        Album al = getAlbumObj(obj);
        return (al==null) ? "" : al.getYear();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowExternalID", 1, new String[] { "Show" })
    {
      /**
       * Gets the global unique ID which is used to identify Shows. This ID is common among all SageTV users.
       * @param Show the Show object
       * @return the global unique ID which represents this Show
       *
       * @declaration public String GetShowExternalID(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? "" : s.getExternalID();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetOriginalAiringDate", new String[] { "Show" })
    {
      /**
       * Gets the date that this Show was originally aired at.
       * @param Show the Show object
       * @return the date that this Show was originally aired at, same units as java.lang.System.currentTimeMillis()
       *
       * @declaration public long GetOriginalAiringDate(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? new Long(0) : new Long(s.getOriginalAirDate());
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetRoleTypes")
    {
      /**
       * Gets a list of all of the valid roles that people can have in a Show
       * @return a list of all of the valid roles that people can have in a Show
       *
       * @declaration public String[] GetRoleTypes();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Show.ROLE_NAMES.clone();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPeopleInShow", 1, new String[] { "Show" })
    {
      /**
       * Gets a list of all of the people involved in this Show. The order of the returned list will
       * correlate with the values returned from {@link #GetRolesInShow GetRolesInShow}.
       * @param Show the Show object
       * @return a list of all of the people involved in this Show as a comma separated list
       *
       * @declaration public String GetPeopleInShow(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? "" : s.getPeopleString(Show.ALL_ROLES);
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPersonListInShow", 1, new String[] { "Show" })
    {
      /**
       * Gets a list of all of the people involved in this Show. The order of the returned list will
       * correlate with the values returned from {@link #GetRolesInShow GetRolesInShow}.
       * @param Show the Show object
       * @return a list of all of the people involved in this Show as a Person array
       * @since 9.0.3
       *
       * @declaration public Person[] GetPersonListInShow(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? Pooler.EMPTY_PERSON_ARRAY : s.getPeopleObjList(Show.ALL_ROLES);
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPeopleListInShow", 1, new String[] { "Show" })
    {
      /**
       * Gets a list of all of the people involved in this Show. The order of the returned list will
       * correlate with the values returned from {@link #GetRolesInShow GetRolesInShow}.
       * @param Show the Show object
       * @return a list of all of the people involved in this Show as a String array
       * @since 5.1
       *
       * @declaration public String[] GetPeopleListInShow(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? Pooler.EMPTY_STRING_ARRAY : s.getPeopleList(Show.ALL_ROLES);
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPeopleAndCharacterListInShow", 1, new String[] { "Show" })
    {
      /**
       * Gets a list of all of the people involved in this Show and the character each of them plays if known. The order of the returned list will
       * correlate with the values returned from {@link #GetRolesInShow GetRolesInShow}.
       * @param Show the Show object
       * @return a list of all of the people involved in this Show with the characters they play as a String array
       * @since 7.0
       *
       * @declaration public String[] GetPeopleAndCharacterListInShow(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? Pooler.EMPTY_STRING_ARRAY : s.getPeopleCharacterList(Show.ALL_ROLES);
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetRolesInShow", 1, new String[] { "Show" })
    {
      /**
       * Gets a list of the roles for each of the people in the specified Show. The order of the returned list will
       * correlate with the values returned from {@link #GetPeopleInShow GetPeopleInShow}
       * @param Show the Show object
       * @return a list of the roles for each of the people in the specified Show
       *
       * @declaration public String[] GetRolesInShow(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        if (s==null) return "";
        byte[] roles = s.getRoles();
        if (roles == null) return "";
        String[] rv = new String[roles.length];
        for (int i = 0; i < roles.length; i++)
          rv[i] = Show.ROLE_NAMES[roles[i]];
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPeopleInShowInRole", 2, new String[] { "Show", "Role" })
    {
      /**
       * Gets the people in the specified Show in the specified Role. Returned as a comma separated list.
       * @param Show the Show object
       * @param Role the role to get the people for
       * @return the people in the specified Show in the specified Role
       *
       * @declaration public String GetPeopleInShowInRole(Show Show, String Role);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String role = getString(stack);
        Object obj = stack.pop();
        Show s = getShowObj(obj);
        if (s != null)
          return s.getPeopleString(Show.getRoleForString(role));
        // Check for album artist query
        if (Sage.rez("Artist").equals(role) || Sage.rez("Album_Artist").equals(role))
        {
          Album al = getAlbumObj(obj);
          if (al != null)
            return al.getArtist();
        }
        return "";
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPeopleInShowInRoles", 2, new String[] { "Show", "RoleList" })
    {
      /**
       * Gets the people in the specified Show in the specified Roles. Returned as a comma separated list.
       * @param Show the Show object
       * @param RoleList the roles to get the people for
       * @return the people in the specified Show in the specified Roles
       *
       * @declaration public String GetPeopleInShowInRoles(Show Show, String[] RoleList);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] roles = getStringList(stack);
        Show s = getShow(stack);
        if (s == null) return "";
        String rv = "";
        for (int i = 0; i < roles.length; i++)
        {
          String str = s.getPeopleString(Show.getRoleForString(roles[i]));
          if (str.length() > 0)
          {
            if (rv.length() > 0)
              rv += ", ";
            rv += str;
          }
        }
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPeopleAndCharacterInShowInRole", 2, new String[] { "Show", "Role" })
    {
      /**
       * Gets the people in the specified Show in the specified Role. Returned as a comma separated list.
       * Each name will also append the character they play if known; using the localized format "Actor as Character".
       * @param Show the Show object
       * @param Role the role to get the people for
       * @return the people in the specified Show in the specified Role
       * @since 7.0
       *
       * @declaration public String GetPeopleAndCharacterInShowInRole(Show Show, String Role);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String role = getString(stack);
        Object obj = stack.pop();
        Show s = getShowObj(obj);
        if (s != null)
          return s.getPeopleCharacterString(Show.getRoleForString(role));
        // Check for album artist query
        if (Sage.rez("Artist").equals(role) || Sage.rez("Album_Artist").equals(role))
        {
          Album al = getAlbumObj(obj);
          if (al != null)
            return al.getArtist();
        }
        return "";
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPeopleAndCharacterInShowInRoles", 2, new String[] { "Show", "RoleList" })
    {
      /**
       * Gets the people in the specified Show in the specified Roles. Returned as a comma separated list.
       * Each name will also append the character they play if known; using the localized format "Actor as Character".
       * @param Show the Show object
       * @param RoleList the roles to get the people for
       * @return the people in the specified Show in the specified Roles
       * @since 7.0
       *
       * @declaration public String GetPeopleAndCharacterInShowInRoles(Show Show, String[] RoleList);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] roles = getStringList(stack);
        Show s = getShow(stack);
        if (s == null) return "";
        String rv = "";
        for (int i = 0; i < roles.length; i++)
        {
          String str = s.getPeopleCharacterString(Show.getRoleForString(roles[i]));
          if (str.length() > 0)
          {
            if (rv.length() > 0)
              rv += ", ";
            rv += str;
          }
        }
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPeopleListInShowInRole", 2, new String[] { "Show", "Role" })
    {
      /**
       * Gets the people in the specified Show in the specified Role. Returned as a String array.
       * @param Show the Show object
       * @param Role the role to get the people for
       * @return the people in the specified Show in the specified Role as a String array
       * @since 5.1
       *
       * @declaration public String[] GetPeopleListInShowInRole(Show Show, String Role);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String role = getString(stack);
        Show s = getShow(stack);
        return (s==null) ? Pooler.EMPTY_STRING_ARRAY : s.getPeopleList(Show.getRoleForString(role));
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPersonListInShowInRole", 2, new String[] { "Show", "Role" })
    {
      /**
       * Gets the people in the specified Show in the specified Role. Returned as a Person array.
       * @param Show the Show object
       * @param Role the role to get the people for
       * @return the people in the specified Show in the specified Role as a Person array
       * @since 9.0.3
       *
       * @declaration public Person[] GetPersonListInShowInRole(Show Show, String Role);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String role = getString(stack);
        Show s = getShow(stack);
        return (s==null) ? Pooler.EMPTY_PERSON_ARRAY : s.getPeopleObjList(Show.getRoleForString(role));
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPeopleListInShowInRoles", 2, new String[] { "Show", "RoleList" })
    {
      /**
       * Gets the people in the specified Show in the specified Roles. Returned as a String array.
       * @param Show the Show object
       * @param RoleList the roles to get the people for
       * @return the people in the specified Show in the specified Roles as a String array
       * @since 5.1
       *
       * @declaration public String[] GetPeopleListInShowInRoles(Show Show, String[] RoleList);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] roles = getStringList(stack);
        Show s = getShow(stack);
        if (s == null) return "";
        java.util.ArrayList rv = new java.util.ArrayList();
        for (int i = 0; i < roles.length; i++)
        {
          String[] str = s.getPeopleList(Show.getRoleForString(roles[i]));
          for (int j = 0; (str != null) && j < str.length; j++)
            rv.add(str[j]);
        }
        return (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
      }});
     rft.put(new PredefinedJEPFunction("Show", "GetPersonListInShowInRoles", 2, new String[] { "Show", "RoleList" })
    {
      /**
       * Gets the people in the specified Show in the specified Roles. Returned as a Person array.
       * @param Show the Show object
       * @param RoleList the roles to get the people for
       * @return the people in the specified Show in the specified Roles as a Person array
       * @since 9.0.3
       *
       * @declaration public Person[] GetPersonListInShowInRoles(Show Show, String[] RoleList);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] roles = getStringList(stack);
        Show s = getShow(stack);
        if (s == null) return "";
        java.util.ArrayList rv = new java.util.ArrayList();
        for (int i = 0; i < roles.length; i++)
        {
          Person[] str = s.getPeopleObjList(Show.getRoleForString(roles[i]));
          for (int j = 0; (str != null) && j < str.length; j++)
            rv.add(str[j]);
        }
        return (Person[]) rv.toArray(Pooler.EMPTY_PERSON_ARRAY);
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPeopleAndCharacterListInShowInRole", 2, new String[] { "Show", "Role" })
    {
      /**
       * Gets the people in the specified Show in the specified Role. Returned as a String array.
       * Each string will also indicate the character they play if known; using the localized format "Actor as Character".
       * @param Show the Show object
       * @param Role the role to get the people for
       * @return the people in the specified Show in the specified Role as a String array
       * @since 7.0
       *
       * @declaration public String[] GetPeopleAndCharacterListInShowInRole(Show Show, String Role);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String role = getString(stack);
        Show s = getShow(stack);
        return (s==null) ? Pooler.EMPTY_STRING_ARRAY : s.getPeopleCharacterList(Show.getRoleForString(role));
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetPeopleAndCharacterListInShowInRoles", 2, new String[] { "Show", "RoleList" })
    {
      /**
       * Gets the people in the specified Show in the specified Roles. Returned as a String array.
       * Each string will also indicate the character they play if known; using the localized format "Actor as Character".
       * @param Show the Show object
       * @param RoleList the roles to get the people for
       * @return the people in the specified Show in the specified Roles as a String array
       * @since 7.0
       *
       * @declaration public String[] GetPeopleAndCharacterListInShowInRoles(Show Show, String[] RoleList);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] roles = getStringList(stack);
        Show s = getShow(stack);
        if (s == null) return "";
        java.util.ArrayList rv = new java.util.ArrayList();
        for (int i = 0; i < roles.length; i++)
        {
          String[] str = s.getPeopleCharacterList(Show.getRoleForString(roles[i]));
          for (int j = 0; (str != null) && j < str.length; j++)
            if (str[j].length() > 0)
              rv.add(str[j]);
        }
        return (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
      }});
    rft.put(new PredefinedJEPFunction("Show", "IsShowObject", 1, new String[] { "Show" })
    {
      /**
       * Returns true if the passed in argument is a Show object. No automatic type conversion
       * will be done on the argument.
       * @param Show the object to test to see if its a Show
       * @return true if the passed in argument is a Show object, false otherwise
       *
       * @declaration public boolean IsShowObject(Object Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof sage.vfs.MediaNode)
          o = ((sage.vfs.MediaNode) o).getDataObject();
        return Boolean.valueOf(o instanceof Show);
      }});
    rft.put(new PredefinedJEPFunction("Show", "IsShowFirstRun", 1, new String[] { "Airing" })
    {
      /**
       * Returns true if the specified Airing represents the first run of the Show content.
       * @param Airing the Airing object
       * @return true if the specified Airing represents the first run of its Show content, false otherwise
       *
       * @declaration public boolean IsShowFirstRun(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing s = getAir(stack);
        return Boolean.valueOf(s != null && s.isFirstRun());
      }});
    rft.put(new PredefinedJEPFunction("Show", "IsShowReRun", 1, new String[] { "Airing" })
    {
      /**
       * Returns true if the specified Airing represents a rerun of the Show content.
       * @param Airing the Airing object
       * @return true if the specified Airing represents a rerun of its Show content, false otherwise
       *
       * @declaration public boolean IsShowReRun(Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Airing s = getAir(stack);
        return Boolean.valueOf(s != null && !(s.isFirstRun()));
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowLanguage", 1, new String[] { "Show" })
    {
      /**
       * Returns the language that the specified Show is in.
       * @param Show the Show object
       * @return the language that the specified Show is in
       *
       * @declaration public String GetShowLanguage(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? "" : s.getLanguage();
      }});
    rft.put(new PredefinedJEPFunction("Show", "AddShow", -1, new String[] {
        "Title", "IsFirstRun", "Episode", "Description", "Duration", "Category", "SubCategory",
        "PeopleList", "RolesListForPeopleList", "Rated", "ExpandedRatingsList", "Year",
        "ParentalRatings", "MiscList", "ExternalID", "Language", "OriginalAirDate", "SeasonNumber", "EpisodeNumber"}, true)
    {
      /**
       * Adds a new Show to the database. Null or the empty string ("") can be passed in for any unneeded fields.
       * @param Title the title of the Show (for music this should be album name)
       * @param IsFirstRun true if this Show is a first run, false otherwise (this parameter has no effect anymore since Airings determine first/rerun status)
       * @param Episode the episode name for this Show (for music this should be the song title)
       * @param Description the description of the Show
       * @param Duration the duration of the Show, not necessary and can be zero; this is only used for indicating differences between Airing duration and the actual content duration
       * @param Category the category of the Show (should be genre for music)
       * @param SubCategory the subcategory of the Show
       * @param PeopleList a list of all of the people in the Show, the roles of the people should correspond to the RolesListForPeopleList argument
       * @param RolesListForPeopleList a list of the roles for the people in the Show, this should correspond to the PeopleList argument
       * @param Rated the rating for the Show see {@link #GetShowRated GetShowRated()}
       * @param ExpandedRatingsList the expanded ratings list for the show, see {@link #GetShowExpandedRatings GetShowExpandedRatings()}
       * @param Year the year of the Show
       * @param ParentalRating the parental rating for the Show (this is no longer used since Airing contains the parental rating)
       * @param MiscList miscellaneous metadata for the Show
       * @param ExternalID the global ID which should be used to uniquely identify this Show
       * @param Language the language for the Show
       * @param OriginalAirDate the original airing date of the Show
       * @return the newly created Show object
       *
       * @declaration public Show AddShow(String Title, boolean IsFirstRun, String Episode, String Description, long Duration, String Category, String SubCategory, String[] PeopleList, String[] RolesListForPeopleList, String Rated, String[] ExpandedRatingsList, String Year, String ParentalRating, String[] MiscList, String ExternalID, String Language, long OriginalAirDate);
       */

      /**
       * Adds a new Show to the database. Null or the empty string ("") can be passed in for any unneeded fields.
       * @param Title the title of the Show (for music this should be album name)
       * @param IsFirstRun true if this Show is a first run, false otherwise (this parameter has no effect anymore since Airings determine first/rerun status)
       * @param Episode the episode name for this Show (for music this should be the song title)
       * @param Description the description of the Show
       * @param Duration the duration of the Show, not necessary and can be zero; this is only used for indicating differences between Airing duration and the actual content duration
       * @param Categories an array of the categories of the Show (should be genre for music)
       * @param PeopleList a list of all of the people in the Show, the roles of the people should correspond to the RolesListForPeopleList argument
       * @param RolesListForPeopleList a list of the roles for the people in the Show, this should correspond to the PeopleList argument
       * @param Rated the rating for the Show see {@link #GetShowRated GetShowRated()}
       * @param ExpandedRatingsList the expanded ratings list for the show, see {@link #GetShowExpandedRatings GetShowExpandedRatings()}
       * @param Year the year of the Show
       * @param ParentalRating the parental rating for the Show (this is no longer used since Airing contains the parental rating)
       * @param MiscList miscellaneous metadata for the Show
       * @param ExternalID the global ID which should be used to uniquely identify this Show
       * @param Language the language for the Show
       * @param OriginalAirDate the original airing date of the Show
       * @param SeasonNumber the season number of the Show
       * @param EpisodeNumber the episode number for the specific season for the Show
       * @return the newly created Show object
       * @since 7.1
       *
       * @declaration public Show AddShow(String Title, boolean IsFirstRun, String Episode, String Description, long Duration, String[] Categories, String[] PeopleList, String[] RolesListForPeopleList, String Rated, String[] ExpandedRatingsList, String Year, String ParentalRating, String[] MiscList, String ExternalID, String Language, long OriginalAirDate, int SeasonNumber, int EpisodeNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        short season = 0;
        short episode = 0;
        if (curNumberOfParameters == 18)
        {
          episode = (short) getInt(stack);
          season = (short) getInt(stack);
        }
        long oad = getLong(stack);
        String lang = getString(stack);
        String extid = getString(stack);
        String[] bonus = getStringList(stack);
        String pr = getString(stack);
        String year = getString(stack);
        String[] ers = getStringList(stack);
        String rated = getString(stack);
        String[] roles = getStringList(stack);
        byte[] broles = new byte[roles.length];
        for (int i = 0; i < broles.length; i++)
          broles[i] = (byte)Show.getRoleForString(roles[i]);
        String[] peeps = getStringList(stack);
        String[] cats;
        if (curNumberOfParameters == 18)
        {
          cats = getStringList(stack);
        }
        else
        {
          cats = new String[2];
          cats[1] = getString(stack);
          cats[0] = getString(stack);
        }
        long dur = getLong(stack);
        String desc = getString(stack);
        String eps = getString(stack);
        boolean first = evalBool(stack.pop());
        String tit = getString(stack);
        return Wizard.getInstance().addShow(tit, tit, eps, desc, dur, cats, peeps,
            broles, rated, ers, year, pr, bonus, extid, lang, oad, 0, season, episode, false, (byte)0,
            (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0);
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetAiringsForShow", 2, new String[] {"Show", "StartingAfterTime"})
    {
      /**
       * Returns a list of all of the Airings for the specified Show starting after the specified time.
       * @param Show the Show object
       * @param StartingAfterTime the time that all returned Airings should start after
       * @return a list of all of the Airings for the specified Show starting after the specified time
       *
       * @declaration public Airing[] GetAiringsForShow(Show Show, long StartingAfterTime);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        long start = getLong(stack);
        return Wizard.getInstance().getAirings((Show) stack.pop(), start);
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowForExternalID", 1, new String[] {"ExternalID"})
    {
      /**
       * Gets a Show based on the global unique ID which is used to identify Shows. This ID is common among all SageTV users.
       * This value can be obtained from {@link #GetShowExternalID GetShowExternalID()}
       *
       * @param ExternalID the external ID to find the corresponding Show for
       * @return the Show which corresponds to the specified externalID, or null if it isn't found in the database
       *
       * @declaration public Show GetShowForExternalID(String ExternalID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getShowForExternalID(getString(stack));
      }});
    rft.put(new PredefinedJEPFunction("Show", "IsMovie", 1, new String[] {"Show"})
    {
      /**
       * Returns true if the specified Show object is a Movie. This is true if the ExternalID starts with 'MV' or if the primary
       * category for the content is "Movie"
       *
       * @param Show the Show to test if its a Movie or not
       * @return true if the specified Show is a Movie
       * @since 8.0
       *
       * @declaration public boolean IsMovie(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s != null && s.isMovie()) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowSeriesInfo", 1, new String[] {"Show"})
    {
      /**
       * Gets the SeriesInfo object for a specified Show if that Show is for a television series and there
       * is information on that series.
       *
       * @param Show the Show object
       * @return the SeriesInfo for the specified Show, or null if the Show has no SeriesInfo
       * @since 5.1
       *
       * @declaration public SeriesInfo GetShowSeriesInfo(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s == null) ? null : s.getSeriesInfo();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowSeasonNumber", 1, new String[] { "Show" })
    {
      /**
       * Returns the season number of the specified Show. For episodic content; sometimes a numeric value is given to the
       * season. If that information exists, this will return it.
       * @param Show the Show object
       * @return the season number of the specified Show, 0 if it is not set
       * @since 7.1
       *
       * @declaration public int GetShowSeasonNumber(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? new Integer(0) : new Integer(s.getSeasonNumber());
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowEpisodeNumber", 1, new String[] { "Show" })
    {
      /**
       * Returns the episode number of the specified Show. For episodic content; sometimes a numeric value is given to the
       * episode in a season. If that information exists, this will return it.
       * @param Show the Show object
       * @return the episode number of the specified Show, 0 if it is not set
       * @since 7.1
       *
       * @declaration public int GetShowEpisodeNumber(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s==null) ? new Integer(0) : new Integer(s.getEpisodeNumber());
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetShowImage", 4, new String[] { "Show", "Type", "Index", "Fallback" })
    {
      int[][] IMAGE_PREF_ORDERS = new int[][] {
        {},
        {Show.IMAGE_PHOTO_TALL, Show.IMAGE_PHOTO_WIDE, Show.IMAGE_POSTER_TALL},
        {Show.IMAGE_PHOTO_WIDE, Show.IMAGE_PHOTO_TALL, Show.IMAGE_POSTER_WIDE},
        {Show.IMAGE_PHOTO_THUMB_TALL, Show.IMAGE_PHOTO_THUMB_WIDE, Show.IMAGE_POSTER_THUMB_TALL},
        {Show.IMAGE_PHOTO_THUMB_WIDE, Show.IMAGE_PHOTO_THUMB_TALL, Show.IMAGE_POSTER_THUMB_WIDE},
        {Show.IMAGE_POSTER_TALL, Show.IMAGE_POSTER_WIDE, Show.IMAGE_PHOTO_TALL},
        {Show.IMAGE_POSTER_WIDE, Show.IMAGE_POSTER_TALL, Show.IMAGE_PHOTO_WIDE},
        {Show.IMAGE_POSTER_THUMB_TALL, Show.IMAGE_POSTER_THUMB_WIDE, Show.IMAGE_PHOTO_THUMB_TALL},
        {Show.IMAGE_POSTER_THUMB_WIDE, Show.IMAGE_POSTER_THUMB_TALL, Show.IMAGE_PHOTO_THUMB_WIDE},
      };
      /**
       * Returns an image specific to this Show. For the standard implementation, this will only return values for Movies (and not all movies have images).
       * Use {@link #GetShowImageCount GetShowImageCount} to determine what the valid values are for the Index parameter.
       * In the future this will be expanded to support plugin image providers to extend what is returned.
       * @param Show the Show object
       * @param Type the type of image, can be one of "PhotoTall", "PhotoWide", "PhotoThumbTall", "PhotoThumbWide", "PosterTall", "PosterWide", "PosterThumbTall" or "PosterThumbWide". In the future, there will be support to expand these types using image plugin providers.
       * @param Index the 0-based index of the image to retrieve when multiple images exist for a given Type
       * @param Fallback should be 3 if the returned image must match the requested parameters, 2 if a substitute image may be used that requires a similar type, 1 if a substitute image may be used that requires the same size, or 0 if any image type may be substituted (size is preferred over type)
       * @return a MetaImage corresponding to the requested image, or null if no image matching the requested parameters is found or an invalid Type is specified
       * @since 7.1
       *
       * @declaration public MetaImage GetShowImage(Show Show, String Type, int Index, int Fallback);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int fallback = getInt(stack);
        int index = Math.max(0, getInt(stack));
        String imageType = getString(stack);
        Show s = getShow(stack);
        if (s == null)
          return null;
        int imageNumType = 0;
        if ("PhotoTall".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_PHOTO_TALL;
        else if ("PhotoWide".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_PHOTO_WIDE;
        else if ("PosterWide".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_POSTER_WIDE;
        else if ("PosterTall".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_POSTER_TALL;
        else if ("PhotoThumbTall".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_PHOTO_THUMB_TALL;
        else if ("PhotoThumbWide".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_PHOTO_THUMB_WIDE;
        else if ("PosterThumbWide".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_POSTER_THUMB_WIDE;
        else if ("PosterThumbTall".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_POSTER_THUMB_TALL;
        if (imageNumType == 0) return null;

        int count = s.getImageCount(IMAGE_PREF_ORDERS[imageNumType][0]);
        if (count > index)
          return MetaImage.getMetaImage(s.getImageUrl(index, IMAGE_PREF_ORDERS[imageNumType][0]), stack.getUIComponent());
        if (fallback >= 3) return null;
        if (count > 0)
          return MetaImage.getMetaImage(s.getImageUrl(count - 1, IMAGE_PREF_ORDERS[imageNumType][0]), stack.getUIComponent());
        if (fallback == 1 || fallback == 0)
        {
          count = s.getImageCount(IMAGE_PREF_ORDERS[imageNumType][2]);
          if (count > index)
            return MetaImage.getMetaImage(s.getImageUrl(index, IMAGE_PREF_ORDERS[imageNumType][2]), stack.getUIComponent());
          if (count > 0)
            return MetaImage.getMetaImage(s.getImageUrl(count - 1, Show.IMAGE_POSTER_TALL), stack.getUIComponent());
          if (fallback == 1)
            return null;
        }
        if (fallback == 2 || fallback == 0)
        {
          count = s.getImageCount(IMAGE_PREF_ORDERS[imageNumType][1]);
          if (count > index)
            return MetaImage.getMetaImage(s.getImageUrl(index, IMAGE_PREF_ORDERS[imageNumType][1]), stack.getUIComponent());
          if (count > 0)
            return MetaImage.getMetaImage(s.getImageUrl(count - 1, IMAGE_PREF_ORDERS[imageNumType][1]), stack.getUIComponent());
          if (fallback == 2)
            return null;
        }
        String imgUrl = s.getAnyImageUrl(index);
        return (imgUrl == null) ? null : MetaImage.getMetaImage(imgUrl, stack.getUIComponent());
      }});

    rft.put(new PredefinedJEPFunction("Show", "GetShowImageCount", 2, new String[] { "Show", "Type" })
    {
      /**
       * Returns a count of images specific to this Show. For the standard implementation, this will only return non-zero values for Movies (and not all movies have images).
       * In the future this will be expanded to support plugin image providers to extend what is returned.
       * @param Show the Show object
       * @param Type the type of image, can be one of "PhotoTall", "PhotoWide", "PhotoThumbTall", "PhotoThumbWide", "PosterTall", "PosterWide", "PosterThumbTall" or "PosterThumbWide". If this is null or the empty string, then it will return 1 if any images exist and zero if none exist. In the future, there will be support to expand these types using image plugin providers.
       * @return the number of images that match the requested type for the specified Show
       * @since 7.1
       *
       * @declaration public int GetShowImageCount(Show Show, String Type);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String imageType = getString(stack);
        Show s = getShow(stack);
        if (s == null)
          return new Integer(0);
        if (imageType == null || imageType.length() == 0) return s.hasAnyImages() ? new Integer(1) : new Integer(0);
        int imageNumType = 0;
        if ("PhotoTall".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_PHOTO_TALL;
        else if ("PhotoWide".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_PHOTO_WIDE;
        else if ("PosterWide".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_POSTER_WIDE;
        else if ("PosterTall".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_POSTER_TALL;
        else if ("PhotoThumbTall".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_PHOTO_THUMB_TALL;
        else if ("PhotoThumbWide".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_PHOTO_THUMB_WIDE;
        else if ("PosterThumbWide".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_POSTER_THUMB_WIDE;
        else if ("PosterThumbTall".equalsIgnoreCase(imageType))
          imageNumType = Show.IMAGE_POSTER_THUMB_TALL;
        if (imageNumType == 0) return new Integer(0);
        return new Integer(s.getImageCount(imageNumType));
      }});

    rft.put(new PredefinedJEPFunction("Show", "HasMovieImage", new String[] { "Show" })
    {
      /**
       * Returns true if the passed in Show that represents a Movie has any imagery associated with it
       * @param Show the Show object
       * @return true if the passed in Show that represents a Movie has any imagery associated with it, false otherwise
       * @since 8.0
       *
       * @declaration public boolean HasMovieImage(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s != null && s.hasAnyImages()) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetMovieImage", new String[] { "Show", "Thumb" })
    {
      /**
       * Returns a MetaImage for an image that's representative of this Movie
       * @param Show the Show object
       * @param Thumb true if it should return a thumbnail image
       * @return a MetaImage for an image that's representative of this Movie, null if there is no such image
       * @since 8.0
       *
       * @declaration public MetaImage GetMovieImage(Show Show, boolean Thumb);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = evalBool(stack.pop());
        Show s = getShow(stack);
        if (s == null) return null;
        String imgUrl = s.getAnyImageUrl(0, thumb);
        return (imgUrl == null) ? null : MetaImage.getMetaImage(imgUrl, stack.getUIComponent());
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetMovieImageURL", new String[] { "Show", "Thumb" })
    {
      /**
       * Returns an image URL that's representative of this Movie
       * @param Show the Show object
       * @param Thumb true if it should return a thumbnail image
       * @return a URL for an image that's representative of this Movie, null if there is no such image
       * @since 8.0
       *
       * @declaration public String GetMovieImageURL(Show Show, boolean Thumb);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = evalBool(stack.pop());
        Show s = getShow(stack);
        if (s == null) return null;
        String imgUrl = s.getAnyImageUrl(0, thumb);
        return (imgUrl == null) ? null : imgUrl;
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetMovieImageCount", new String[] { "Show" })
    {
      /**
       * Returns the number of images available for a Show that represents a Movie
       * @param Show the Show object
       * @return the number of images available for a Show that represents a Movie
       * @since 8.0
       *
       * @declaration public int GetMovieImageCount(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        return (s != null) ? new Integer(s.getImageCount()) : new Integer(0);
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetMovieImageAtIndex", new String[] { "Show", "Index", "Thumb" })
    {
      /**
       * Returns the image at the specified index for a Show that represents a Movie
       * @param Show the Show object
       * @param Index the 0-based index number of the image to retrieve
       * @param Thumb true if it should return a thumbnail image
       * @return a MetaImage that corresponds to the requested image, or null if it doesn't exist
       * @since 8.0
       *
       * @declaration public MetaImage GetMovieImageAtIndex(Show Show, int Index, boolean Thumb);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = evalBool(stack.pop());
        int idx = getInt(stack);
        Show s = getShow(stack);
        if (s == null) return null;
        String imgUrl = s.getImageUrlForIndex(idx, thumb);
        return (imgUrl == null) ? null : MetaImage.getMetaImage(imgUrl, stack.getUIComponent());
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetMovieImageURLAtIndex", new String[] { "Show", "Index", "Thumb" })
    {
      /**
       * Returns the image URL at the specified index for a Show that represents a Movie
       * @param Show the Show object
       * @param Index the 0-based index number of the image to retrieve
       * @param Thumb true if it should return a thumbnail image
       * @return a URL that corresponds to the requested image, or null if it doesn't exist
       * @since 8.0
       *
       * @declaration public String GetMovieImageURLAtIndex(Show Show, int Index, boolean Thumb);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = evalBool(stack.pop());
        int idx = getInt(stack);
        Show s = getShow(stack);
        if (s == null) return null;
        String imgUrl = s.getImageUrlForIndex(idx, thumb);
        return (imgUrl == null) ? null : imgUrl;
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetMovieStarRating", 1, new String[] { "Show" })
    {
      /**
       * Returns the star rating for a Movie as a floating point number.
       * @param Show the movie to get the star rating for
       * @return the star rating as a floating point number, zero if there is no star rating for this Show
       * @since 8.1
       *
       * @declaration public float GetMovieStarRating(Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Show s = getShow(stack);
        if (s == null)
          return new Float(0);
        String rating = s.getRating();
        if (rating == null)
          return new Float(0);
        float f = 0;
        for (int i = 0; i < rating.length(); i++) {
          if (rating.charAt(i) == '*')
            f = f + 1.0f;
          else if (rating.charAt(i) == '+')
            f = f + 0.5f;
        }
        return new Float(f);
      }});
    rft.put(new PredefinedJEPFunction("Show", "IsSDEPGInProgressSport", 1, new String[] { "ExternalIDs" }, true)
    {
      /**
       * Returns if the provided external ID's can be tracked when in progress through Schedules Direct.
       * Note that if the Schedules Direct service is not available, this will always return false
       * for all requested ID's.
       * @param ExternalIDs Array of external ID's to look up
       * @return true for the corresponding index of each external ID that can be tracked, otherwise false on the same index
       * @since 9.0
       *
       * @declaration public boolean[] IsSDEPGInProgressSport(String[] ExternalIDs);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String externalIDs[] = getStringList(stack);
        SDInProgressSport sports[] = SDRipper.getInProgressSport(externalIDs);
        boolean returnValues[] = new boolean[sports.length];
        for (int i = 0; i < sports.length; i++)
        {
          if (sports[i] == null)
            continue;

          int code = sports[i].getCode();
          // All 3 of these codes indicate that the program can be tracked when it is in progress.
          returnValues[i] = code == SDErrors.OK.CODE ||
            code == SDErrors.FUTURE_PROGRAM.CODE ||
            code == SDErrors.PROGRAMID_QUEUED.CODE;
        }

        return returnValues;
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetSDEPGInProgressSportStatus", 1, new String[] { "ExternalIDs" }, true)
    {
      /**
       * Returns the current Schedules Direct provided in progress status for each of the provided external ID's.
       * The status will be one of the following:
       * 0 = Complete
       * 1 = In progress
       * 2 = Status is not available at the moment (try again in 30 seconds)
       * 3 = Program is in the future and will be able to be tracked
       * 4 = Program is not trackable
       * 5 = Schedules Direct is offline/not available right now (try again in an hour)
       * 6 = Schedules Direct authentication failure
       * 7 = General failure
       * @param ExternalIDs Array of external ID's to look up
       * @return int for each corresponding index representing the current status of the requested external ID's
       * @since 9.0
       *
       * @declaration public int[] GetSDEPGInProgressSportStatus(String[] ExternalIDs);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String externalIDs[] = getStringList(stack);
        SDInProgressSport sports[] = SDRipper.getInProgressSport(externalIDs);
        int returnValues[] = new int[sports.length];
        for (int i = 0; i < sports.length; i++)
        {
          SDInProgressSport sport = sports[i];
          if (sport == null)
          {
            returnValues[i] = 7;
            continue;
          }

          int code = sport.getCode();
          if (code == SDErrors.OK.CODE)
            returnValues[i] = sport.isComplete() ? 0 : 1;
          else if (code == SDErrors.PROGRAMID_QUEUED.CODE)
            returnValues[i] = 2;
          else if (code == SDErrors.FUTURE_PROGRAM.CODE)
            returnValues[i] = 3;
          else if (code == SDErrors.INVALID_PROGRAMID.CODE)
            returnValues[i] = 4;
          else if (code == SDErrors.SERVICE_OFFLINE.CODE)
            returnValues[i] = 5;
          else if (code == SDErrors.SAGETV_NO_PASSWORD.CODE)
            returnValues[i] = 6;
          else
            returnValues[i] = 7;
        }
        return returnValues;
      }});
    /*
		rft.put(new PredefinedJEPFunction("Show", "", 1, new String[] { "Show" })
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 return getShow(stack).;
			}});
     */
  }
}
