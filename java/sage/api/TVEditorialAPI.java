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
public class TVEditorialAPI
{
  private TVEditorialAPI(){}

  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("TVEditorial", "GetAllTVEditorials")
    {
      /**
       * Returns a list of all of the 'TV Editorials' which are stories about TV shows
       * @return a list of all of the 'TV Editorials'
       * @since 5.1
       *
       * @declaration public TVEditorial[] GetAllTVEditorials();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getEditorials();
      }});
    rft.put(new PredefinedJEPFunction("TVEditorial", "GetEditorialTitle", 1, new String[] { "TVEditorial" })
    {
      /**
       * Returns the title for the specified TVEditorial
       * @param TVEditorial the TVEditorial object
       * @return the title for the specified TVEditorial
       * @since 5.1
       *
       * @declaration public String GetEditorialTitle(TVEditorial TVEditorial);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return getEditorial(stack).getTitle();
      }});
    rft.put(new PredefinedJEPFunction("TVEditorial", "GetEditorialShow", 1, new String[] { "TVEditorial" })
    {
      /**
       * Returns the Show for the specified TVEditorial
       * @param TVEditorial the TVEditorial object
       * @return the Show for the specified TVEditorial
       * @since 5.1
       *
       * @declaration public String GetEditorialShow(TVEditorial TVEditorial);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return getEditorial(stack).getShow();
      }});
    rft.put(new PredefinedJEPFunction("TVEditorial", "GetEditorialText", 1, new String[] { "TVEditorial" })
    {
      /**
       * Returns the text for the specified TVEditorial
       * @param TVEditorial the TVEditorial object
       * @return the text for the specified TVEditorial
       * @since 5.1
       *
       * @declaration public String GetEditorialText(TVEditorial TVEditorial);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return getEditorial(stack).getDescription();
      }});
    rft.put(new PredefinedJEPFunction("TVEditorial", "GetEditorialAirDate", 1, new String[] { "TVEditorial" })
    {
      /**
       * Returns a String representing the airing date for the content the editorial is about
       * @param TVEditorial the TVEditorial object
       * @return a String representing the airing date for the content the editorial is about
       * @since 5.1
       *
       * @declaration public String GetEditorialAirDate(TVEditorial TVEditorial);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return getEditorial(stack).getAirdate();
      }});
    rft.put(new PredefinedJEPFunction("TVEditorial", "GetEditorialNetwork", 1, new String[] { "TVEditorial" })
    {
      /**
       * Returns the network that the Show for this editorial is broadcast on
       * @param TVEditorial the TVEditorial object
       * @return the network that the Show for this editorial is broadcast on
       * @since 5.1
       *
       * @declaration public String GetEditorialNetwork(TVEditorial TVEditorial);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return getEditorial(stack).getNetwork();
      }});
    rft.put(new PredefinedJEPFunction("TVEditorial", "HasEditorialImage", 1, new String[] { "TVEditorial" })
    {
      /**
       * Returns true if the specified editorial has an image that corresponds to it
       * @param TVEditorial the TVEditorial object
       * @return true if the specified editorial has an image that corresponds to it, false otherwise
       * @since 5.1
       *
       * @declaration public boolean HasEditorialImage(TVEditorial TVEditorial);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String imageURL = getEditorial(stack).getImageURL();
        return (imageURL == null || imageURL.length() == 0) ? Boolean.FALSE : Boolean.TRUE;
      }});
    rft.put(new PredefinedJEPFunction("TVEditorial", "GetEditorialImage", 1, new String[] { "TVEditorial" })
    {
      /**
       * Returns the image that corresponds to this editorial if there is one
       * @param TVEditorial the TVEditorial object
       * @return the image that corresponds to this editorial if there is one
       * @since 5.1
       *
       * @declaration public MetaImage GetEditorialImage(TVEditorial TVEditorial);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String imageURL = getEditorial(stack).getImageURL();
        if (imageURL == null || imageURL.length() == 0)
          return MetaImage.getMetaImage((String)null);
        else
          return MetaImage.getMetaImage(imageURL, stack.getUIComponent());
      }});
    /*
		rft.put(new PredefinedJEPFunction("TVEditorial", "", 1, new String[] { "TVEditorial" })
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 return getEditorial(stack).;
			}});
     */
  }
}
