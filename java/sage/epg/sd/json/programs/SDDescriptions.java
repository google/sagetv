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
package sage.epg.sd.json.programs;

public class SDDescriptions
{
  private static final Description[] EMPTY_DESCRIPTIONS = new Description[0];
  private static final Description BLANK_DESCRIPTION = new Description("");

  private Description description100[];
  private Description description1000[];

  public Description[] getDescription100()
  {
    if (description100 == null)
      return EMPTY_DESCRIPTIONS;

    return description100;
  }

  public Description[] getDescription1000()
  {
    if (description1000 == null)
      return EMPTY_DESCRIPTIONS;

    return description1000;
  }

  /**
   * Get a description in a specific language if available.
   * <p/>
   * If there aren't any descriptions, an English (en) description with an empty string will be
   * returned. If the requested language does not exist, the first language available will be
   * returned instead.
   * <p/>
   * The long description if available will always be preferred over the short description.
   *
   * @param language The desired language abbreviation. (Ex. en for English)
   * @return An available description or <code>null</code> if no descriptions exist.
   */
  public Description getDescription(String language)
  {
    if (description1000 == null || description1000.length == 0)
    {
      if (description100 == null || description100.length == 0)
        return BLANK_DESCRIPTION; // No description.

      for (Description desc : description100)
      {
        if (language.equals(desc.descriptionLanguage))
          return desc;
      }

      return description100[0];
    }

    for (Description desc : description1000)
    {
      if (language.equals(desc.descriptionLanguage))
        return desc;
    }

    if (description100 != null && description100.length > 0)
    {
      for (Description desc : description100)
      {
        if (language.equals(desc.descriptionLanguage))
          return desc;
      }
    }

    return description1000[0];
  }

  public Description getFirstDescription()
  {
    if (description1000 != null && description1000.length > 0)
      return description1000[0];
    if (description100 != null && description100.length > 0)
      return description100[0];
    return BLANK_DESCRIPTION;
  }

  public static class Description
  {
    private String descriptionLanguage;
    private String description;

    public Description()
    {
      // Used by GSON.
    }

    private Description(String description)
    {
      descriptionLanguage = "en";
      this.description = description;
    }

    public String getDescriptionLanguage()
    {
      if (descriptionLanguage == null)
        return "";

      return descriptionLanguage;
    }

    public String getDescription()
    {
      if (description == null)
        return "";

      return description;
    }
  }
}
