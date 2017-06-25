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
package tv.sage.mod;

/**
 * @author 601
 */
public abstract class Translator
{
  static volatile boolean reset = true;

  private static Object lock = new Object();


  /**
   * Reset translation, for a new language.
   */
  public static void reset()
  {
    synchronized (lock)
    {
      reset = true;
    }
  }

  // Only translate the specific Widgets that need it. Otherwise we could end up translating
  // code which is highly dangerous of course.

  static java.util.Map sourceTranslationMap;
  static java.util.Map dynSourceTranslationMap;
  static java.util.ResourceBundle localText;

  static String translateText(String s, boolean dText)
  {
    if (sage.Sage.getRawProperties() == null) return s;
    synchronized (lock)
    {
      if (reset)
      {
        reset = false;

        sourceTranslationMap = new java.util.HashMap();
        dynSourceTranslationMap = new java.util.HashMap();

        // Get the language information and derive the map from that. Look in the STV folder for it;
        // the new naming convention is the STV filename with _i18n attached to it.
        String widgFilename = sage.Wizard.getInstance().getWidgetDBFile().toString();
        int dotIdx = widgFilename.lastIndexOf('.');
        if (dotIdx != -1)
          widgFilename = widgFilename.substring(0, dotIdx);
        // Be sure to use canonical paths due to Win 8.3
        String userDirPath = System.getProperty("user.dir");
        try
        {
          widgFilename = new java.io.File(widgFilename).getCanonicalPath();
          userDirPath = new java.io.File(userDirPath).getCanonicalPath();
        }catch (Exception ef)
        {
          System.out.println("ERROR processing paths of:" + ef);
        }
        if (widgFilename.length() > userDirPath.length() + 1 &&
            widgFilename.toLowerCase().startsWith(userDirPath.toLowerCase()))
          widgFilename = widgFilename.substring(userDirPath.length() + 1);
        //widgFilename = widgFilename.replaceAll("\\\\", "/");
        try
        {
          java.util.ResourceBundle genericText = java.util.ResourceBundle.getBundle(widgFilename + "_i18n",
              new java.util.Locale(""));
          java.util.Enumeration orgKeys = genericText.getKeys();
          while (orgKeys.hasMoreElements())
          {
            String currKey = orgKeys.nextElement().toString();
            if (currKey.toLowerCase().startsWith("d_"))
              dynSourceTranslationMap.put(genericText.getString(currKey), currKey);
            else
              sourceTranslationMap.put(genericText.getString(currKey), currKey);
          }

          // Use the default locale
          localText = java.util.ResourceBundle.getBundle(widgFilename + "_i18n", sage.Sage.userLocale);
          // NOTE FOR DEBUG ONLY
          System.out.println("locale = " + localText.getLocale());
        }
        catch (java.util.MissingResourceException e)
        {
          System.out.println("ERROR Cannot find translation file for STV:" + e);
          sourceTranslationMap.clear();
          dynSourceTranslationMap.clear();
        }
      }

      String transKey = (String) (dText ? dynSourceTranslationMap.get(s) : sourceTranslationMap.get(s));
      if (transKey != null)
      {
        return localText.getString(transKey);
      }
      else
        return s;
    }
  }
}
