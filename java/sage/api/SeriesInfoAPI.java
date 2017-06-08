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
 *
 * @author Narflex
 */
public class SeriesInfoAPI
{
  private SeriesInfoAPI() {}

  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetAllSeriesInfo")
    {
      /**
       * Returns a list of all of the SeriesInfo which is information about television series
       * @return a list of all of the SeriesInfo
       * @since 5.1
       *
       * @declaration public SeriesInfo[] GetAllSeriesInfo();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getAllSeriesInfo();
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesTitle", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the title for the specified SeriesInfo
       * @param SeriesInfo the SeriesInfo object
       * @return the title for the specified SeriesInfo
       * @since 5.1
       *
       * @declaration public String GetSeriesTitle(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getTitle();
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesDescription", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the description for the specified SeriesInfo
       * @param SeriesInfo the SeriesInfo object
       * @return the description for the specified SeriesInfo
       * @since 5.1
       *
       * @declaration public String GetSeriesDescription(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getDescription();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetSeriesCategory", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the category for the specified SeriesInfo
       * @param SeriesInfo the SeriesInfo object
       * @return the category for the SeriesInfo
       * @since V7.0
       *
       * @declaration public String GetSeriesCategory(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getCategory();
      }});
    rft.put(new PredefinedJEPFunction("Show", "GetSeriesSubCategory", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the subcategory for the specified SeriesInfo
       * @param SeriesInfo the SeriesInfo object
       * @return the subcategory for the SeriesInfo
       * @since V7.0
       *
       * @declaration public String GetSeriesSubCategory(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getSubCategory();
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesHistory", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the history description for the specified SeriesInfo
       * @param SeriesInfo the SeriesInfo object
       * @return the history description for the specified SeriesInfo
       * @since 5.1
       *
       * @declaration public String GetSeriesHistory(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getHistory();
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesPremiereDate", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns a String describing the premiere date for the specified SeriesInfo
       * @param SeriesInfo the SeriesInfo object
       * @return a String describing the premiere date for the specified SeriesInfo
       * @since 5.1
       *
       * @declaration public String GetSeriesPremiereDate(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getPremiereDate();
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesFinaleDate", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns a String describing the finale date for the specified SeriesInfo
       * @param SeriesInfo the SeriesInfo object
       * @return a String describing the finale date for the specified SeriesInfo
       * @since 5.1
       *
       * @declaration public String GetSeriesFinaleDate(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getFinaleDate();
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesNetwork", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the name of the network the specified SeriesInfo airs on
       * @param SeriesInfo the SeriesInfo object
       * @return the name of the network the specified SeriesInfo airs on
       * @since 5.1
       *
       * @declaration public String GetSeriesNetwork(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getNetwork();
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesDayOfWeek", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the name of the day of the week the specified SeriesInfo airs on
       * @param SeriesInfo the SeriesInfo object
       * @return the name of the day of the week the specified SeriesInfo airs on
       * @since 5.1
       *
       * @declaration public String GetSeriesDayOfWeek(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getAirDow();
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesHourAndMinuteTimeslot", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the hour/minute timeslot that the specified SeriesInfo airs at
       * @param SeriesInfo the SeriesInfo object
       * @return the hour/minute timeslot that the specified SeriesInfo airs at
       * @since 5.1
       *
       * @declaration public String GetSeriesHourAndMinuteTimeslot(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getAirHrMin();
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "HasSeriesImage", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns true if the specified SeriesInfo has a corresponding image for it
       * @param SeriesInfo the SeriesInfo object
       * @return true if the specified SeriesInfo has a corresponding image for it
       * @since 5.1
       *
       * @declaration public boolean HasSeriesImage(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        if (si == null) return Boolean.FALSE;
        return si.hasImage() ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesImage", -1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the image that corresponds to this SeriesInfo if there is one
       * @param SeriesInfo the SeriesInfo object
       * @return the image that corresponds to this SeriesInfo if there is one
       * @since 5.1
       *
       * @declaration public MetaImage GetSeriesImage(SeriesInfo SeriesInfo);
       */

      /**
       * Returns the image that corresponds to this SeriesInfo if there is one
       * @param SeriesInfo the SeriesInfo object
       * @param Thumb true if a thumbnail is preferred, false if a full size image is
       * @return the image that corresponds to this SeriesInfo if there is one
       * @since 8.0
       *
       * @declaration public MetaImage GetSeriesImage(SeriesInfo SeriesInfo, boolean Thumb);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = false;
        if (curNumberOfParameters == 2)
        {
          thumb = evalBool(stack.pop());
        }
        SeriesInfo si = getSeriesInfo(stack);
        if (si == null) return null;
        String imageURL = si.getImageURL(thumb);
        if (imageURL == null || imageURL.length() == 0)
          return null;
        else
          return MetaImage.getMetaImage(imageURL, stack.getUIComponent());
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesImageURL", -1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the URL of the image that corresponds to this SeriesInfo if there is one
       * @param SeriesInfo the SeriesInfo object
       * @return the URL of the image that corresponds to this SeriesInfo if there is one
       * @since 8.0
       *
       * @declaration public String GetSeriesImageURL(SeriesInfo SeriesInfo);
       */

      /**
       * Returns the image URL that corresponds to this SeriesInfo if there is one
       * @param SeriesInfo the SeriesInfo object
       * @param Thumb true if a thumbnail is preferred, false if a full size image is
       * @return the image URL that corresponds to this SeriesInfo if there is one
       * @since 8.0
       *
       * @declaration public String GetSeriesImageURL(SeriesInfo SeriesInfo, boolean Thumb);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = false;
        if (curNumberOfParameters == 2)
        {
          thumb = evalBool(stack.pop());
        }
        SeriesInfo si = getSeriesInfo(stack);
        if (si == null) return null;
        String imageURL = si.getImageURL(thumb);
        if (imageURL == null || imageURL.length() == 0)
          return null;
        else
          return imageURL;
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesImageCount", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the number of images available that correspond to this SeriesInfo
       * @param SeriesInfo the SeriesInfo object
       * @return the number of images available that correspond to this SeriesInfo
       * @since 8.0
       *
       * @declaration public int GetSeriesImageCount(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        if (si == null) return new Integer(0);
        return new Integer(si.getImageCount());
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesImageAtIndex", new String[] { "SeriesInfo", "Index", "Thumb" })
    {
      /**
       * Returns the image that corresponds to this SeriesInfo at the specified index (when there are multiple images)
       * @param SeriesInfo the SeriesInfo object
       * @param Index the 0-based index of which image to return
       * @param Thumb true if a thumbnail is preferred, false if a full size image is
       * @return the image that corresponds to this SeriesInfo at the specified index
       * @since 8.0
       *
       * @declaration public MetaImage GetSeriesImageAtIndex(SeriesInfo SeriesInfo, int Index, boolean Thumb);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = evalBool(stack.pop());
        int idx = getInt(stack);
        SeriesInfo si = getSeriesInfo(stack);
        if (si == null) return null;
        String imageURL = si.getImageURL(idx, thumb);
        if (imageURL == null || imageURL.length() == 0)
          return null;
        else
          return MetaImage.getMetaImage(imageURL, stack.getUIComponent());
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesImageURLAtIndex", new String[] { "SeriesInfo", "Index", "Thumb" })
    {
      /**
       * Returns the image URL that corresponds to this SeriesInfo at the specified index (when there are multiple images)
       * @param SeriesInfo the SeriesInfo object
       * @param Index the 0-based index of which image to return
       * @param Thumb true if a thumbnail is preferred, false if a full size image is
       * @return the image URL that corresponds to this SeriesInfo at the specified index
       * @since 8.0
       *
       * @declaration public String GetSeriesImageURLAtIndex(SeriesInfo SeriesInfo, int Index, boolean Thumb);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = evalBool(stack.pop());
        int idx = getInt(stack);
        SeriesInfo si = getSeriesInfo(stack);
        if (si == null) return null;
        String imageURL = si.getImageURL(idx, thumb);
        if (imageURL == null || imageURL.length() == 0)
          return null;
        else
          return imageURL;
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "HasSeriesActorImage", new String[] { "SeriesInfo", "Person" })
    {
      /**
       * Returns true if the specified SeriesInfo has a corresponding image for it for the specified Person in it
       * @param SeriesInfo the SeriesInfo object
       * @param Person the Person to check for an image
       * @return true if the specified SeriesInfo has a corresponding image for it for the specified Person in it
       * @since 8.0
       *
       * @declaration public boolean HasSeriesActorImage(SeriesInfo SeriesInfo, Person Person);
       */

      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Person p = getPerson(stack);
        SeriesInfo si = getSeriesInfo(stack);
        if (si == null || p == null) return Boolean.FALSE;
        return si.hasActorInCharacterImage(p) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesActorImage", new String[] { "SeriesInfo", "Person", "Thumb" })
    {
      /**
       * Returns an image of the specified Person in their role in the specified Series
       * @param SeriesInfo the SeriesInfo object
       * @param Person the Person to check for an image
       * @param Thumb true if a thumbnail is preferred, false if a full size image is
       * @return an image of the specified Person in their role in the specified Series
       * @since 8.0
       *
       * @declaration public MetaImage GetSeriesActorImage(SeriesInfo SeriesInfo, Person Person, boolean Thumb);
       */

      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = evalBool(stack.pop());
        Person p = getPerson(stack);
        SeriesInfo si = getSeriesInfo(stack);
        if (si == null || p == null) return null;
        String imageURL = si.getActorInCharacterImageURL(p, thumb);
        if (imageURL == null || imageURL.length() == 0)
          return null;
        else
          return MetaImage.getMetaImage(imageURL, stack.getUIComponent());

      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesActorImageURL", new String[] { "SeriesInfo", "Person", "Thumb" })
    {
      /**
       * Returns an image URL of the specified Person in their role in the specified Series
       * @param SeriesInfo the SeriesInfo object
       * @param Person the Person to check for an image
       * @param Thumb true if a thumbnail is preferred, false if a full size image is
       * @return an image URL of the specified Person in their role in the specified Series
       * @since 8.0
       *
       * @declaration public String GetSeriesActorImageURL(SeriesInfo SeriesInfo, Person Person, boolean Thumb);
       */

      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = evalBool(stack.pop());
        Person p = getPerson(stack);
        SeriesInfo si = getSeriesInfo(stack);
        if (si == null || p == null) return null;
        String imageURL = si.getActorInCharacterImageURL(p, thumb);
        if (imageURL == null || imageURL.length() == 0)
          return null;
        else
          return imageURL;

      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetNumberOfCharactersInSeries", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the number of characters that we have information on for the specified series
       * @param SeriesInfo the SeriesInfo object
       * @return the number of characters that we have information on for the specified series
       * @since 5.1
       *
       * @declaration public int GetNumberOfCharactersInSeries(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(getSeriesInfo(stack).getNumberOfCharacters());
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesActor", 2, new String[] { "SeriesInfo", "Index" })
    {
      /**
       * Returns the name of the actor/actress for the specfied index in the specified SeriesInfo. The range
       * for the index is from 0 to one less than the value of {@link #GetNumberOfCharactersInSeries GetNumberOfCharactersInSeries()}
       * @param SeriesInfo the SeriesInfo object
       * @param Index the 0-based index of the actor to retrieve
       * @return the Person object of the actor/actress for the specfied index in the specified SeriesInfo
       * @since 5.1
       *
       * @declaration public Person GetSeriesActor(SeriesInfo SeriesInfo, int Index);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int idx = getInt(stack);
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getPersonObj(idx);
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesActorList", new String[] { "SeriesInfo" })
    {
      /**
       * Returns a list of the names of the actors/actresses in the specified SeriesInfo.
       * @param SeriesInfo the SeriesInfo object
       * @return a list of the Persons of the actors/actresses in the specified SeriesInfo
       * @since 5.1
       *
       * @declaration public Person[] GetSeriesActorList(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? null : si.getPersonObjList();
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesCharacter", 2, new String[] { "SeriesInfo", "Index" })
    {
      /**
       * Returns the name of the character for the specfied index in the specified SeriesInfo. The range
       * for the index is from 0 to one less than the value of {@link #GetNumberOfCharactersInSeries GetNumberOfCharactersInSeries()}
       * @param SeriesInfo the SeriesInfo object
       * @param Index the 0-based index of the actor to retrieve
       * @return the name of the character for the specfied index in the specified SeriesInfo
       * @since 5.1
       *
       * @declaration public String GetSeriesCharacter(SeriesInfo SeriesInfo, int Index);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int idx = getInt(stack);
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? "" : si.getCharacter(idx);
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesCharacterList", new String[] { "SeriesInfo" })
    {
      /**
       * Returns a list of the names of the characters in the specified SeriesInfo.
       * @param SeriesInfo the SeriesInfo object
       * @return a list of the names of the characters in the specified SeriesInfo
       * @since 5.1
       *
       * @declaration public String[] GetSeriesCharacterList(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        return si == null ? null : si.getCharacterList();
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesCharacterForActor", new String[] { "SeriesInfo", "Actor" })
    {
      /**
       * Returns the name of the character that the corresponding actor plays in this series
       * @param SeriesInfo the SeriesInfo object
       * @param Actor the actor
       * @return the name of the character that the corresponding actor plays in this series, the empty string if there's no correlation
       * @since 7.0
       *
       * @declaration public String GetSeriesCharacterForActor(SeriesInfo SeriesInfo, String Actor);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object obj = stack.pop();
        SeriesInfo si = getSeriesInfo(stack);
        if (si == null || obj == null) return "";
        if (obj instanceof Person)
          return si.getCharacterForActor((Person) obj);
        else
          return si.guessCharacterForActor(obj.toString());
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesID", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns the Series ID of the specified SeriesInfo
       * NOTE: V8.0 IDs are not backwards compatible with prior versions
       * @param SeriesInfo the SeriesInfo object
       * @return the Series ID of the specified SeriesInfo object (currently an integer, represented as a String for future expansion)
       * @since 7.0
       *
       * @declaration public String GetSeriesID(SeriesInfo SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        SeriesInfo si = getSeriesInfo(stack);
        if (si == null)
          return "";
        int id = si.getShowcardID();
        if (id == 0)
          id = si.getSeriesID();
        return Integer.toString(id);
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesInfoForID", 1, new String[] { "SeriesID" })
    {
      /**
       * Returns the SeriesInfo object for the specified Series ID
       * NOTE: V8.0 IDs are not backwards compatible with prior versions
       * @param SeriesID the ID of the desired SeriesInfo object
       * @return the SeriesInfo object with the specified ID, or null if it does not exist
       * @since 7.0
       *
       * @declaration public SeriesInfo GetSeriesInfoForID(String SeriesID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        try
        {
          return Wizard.getInstance().getSeriesInfoForShowcardID(Integer.parseInt(s));
        }
        catch (NumberFormatException nfe)
        {
          return null;
        }
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "AddSeriesInfo", new String[] { "SeriesID", "Title", "Network", "Description", "History", "PremiereDate", "FinaleDate",
        "AirDOW", "AirHrMin", "ImageURL", "People", "Characters" }, true)
    {
      /**
       * Call this to add a SeriesInfo object to the database. If a SeriesInfo with this seriesID is already present, it will be updated
       * to this information. You can use null or String[0] for any fields you don't want to specify.
       * @param SeriesID the ID of the series, this should match the prefix of corresponding ShowIDs w/out the last 4 digits for proper linkage (i.e. the SeriesID for EP1234567890 would be 123456)
       * @param Title the title of the series
       * @param Network the network that airs the series
       * @param Description a description of this series
       * @param History a historical description of the series
       * @param PremiereDate a String representation of the date the series premiered
       * @param FinaleDate a String representation of the date the series ended
       * @param AirDOW a String representation of the day of the week the series airs
       * @param AirHrMin a String representation of the time the series airs
       * @param ImageURL a URL that links to an image for this series
       * @param People names of people/actors in this show
       * @param Characters must be same length as people array, should give the character names the corresponding people have in the series
       * @return the newly added SeriesInfo object, or the updated object if another SeriesInfo object already existed with the same SeriesID
       * @since 7.0
       *
       * @declaration public SeriesInfo AddSeriesInfo(int SeriesID, String Title, String Network, String Description, String History, String PremiereDate, String FinaleDate, String AirDOW, String AirHrMin, String ImageURL, String[] People, String[] Characters);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] chars = getStringList(stack);
        String[] peeps = getStringList(stack);
        String url = getString(stack);
        String airHrMin = getString(stack);
        String airDow = getString(stack);
        String finaleDate = getString(stack);
        String premiereDate = getString(stack);
        String history = getString(stack);
        String desc = getString(stack);
        String net = getString(stack);
        String title = getString(stack);
        int serId = getInt(stack);
        return Wizard.getInstance().addSeriesInfo(serId, title, net, desc, history, premiereDate, finaleDate, airDow, airHrMin, url, peeps, chars);
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "GetSeriesInfoProperty", 2, new String[] { "SeriesInfo", "PropertyName" })
    {
      /**
       * Returns a property value for a specified SeriesInfo object. This must have been set using SetSeriesInfoProperty.
       * Returns the empty string when the property is undefined.
       * @param SeriesInfo the SeriesInfo object
       * @param PropertyName the name of the property
       * @return the property value for the specified SeriesInfo, or the empty string if it is not defined
       * @since 7.0
       *
       * @declaration public String GetSeriesInfoProperty(SeriesInfo SeriesInfo, String PropertyName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String prop = getString(stack);
        SeriesInfo si = getSeriesInfo(stack);
        return si.getProperty(prop);
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "SetSeriesInfoProperty", 3, new String[] { "SeriesInfo", "PropertyName", "PropertyValue" }, true)
    {
      /**
       * Sets a property for this SeriesInfo object. This can be any name/value combination (but the name cannot be null). If the value is null;
       * then the specified property will be removed from this SeriesInfo object. This only impacts the return values from GetSeriesInfoProperty and has no other side effects.
       * @param SeriesInfo the SeriesInfo object
       * @param PropertyName the name of the property
       * @param PropertyValue the value of the property
       * @since 7.0
       *
       * @declaration public void SetSeriesInfoProperty(SeriesInfo SeriesInfo, String PropertyName, String PropertyValue);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String propV = getString(stack);
        String propN = getString(stack);
        SeriesInfo si = getSeriesInfo(stack);
        si.setProperty(propN, propV);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("SeriesInfo", "IsSeriesInfoObject", 1, new String[] { "SeriesInfo" })
    {
      /**
       * Returns true if the argument is a SeriesInfo object. Automatic type conversion is NOT done in this call.
       * @param SeriesInfo the object to test
       * @return true if the argument is an SeriesInfo object
       * @since 7.1
       *
       * @declaration public boolean IsSeriesInfoObject(Object SeriesInfo);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof sage.vfs.MediaNode)
          o = ((sage.vfs.MediaNode) o).getDataObject();
        return Boolean.valueOf(o instanceof SeriesInfo);
      }});
    /*
		rft.put(new PredefinedJEPFunction("SeriesInfo", "", 1, new String[] { "SeriesInfo" })
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 return getSeriesInfo(stack).;
			}});
     */
  }
}
