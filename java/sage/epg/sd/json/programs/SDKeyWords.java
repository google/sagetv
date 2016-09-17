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

import sage.Pooler;
import sage.epg.sd.gson.annotations.SerializedName;

public class SDKeyWords
{
  private String Mood[];
  @SerializedName("Time Period")
  private String TimePeriod[];
  private String Theme[];
  private String Character[];
  private String Setting[];
  private String Subject[];
  private String General[];

  public String[] getMood()
  {
    if (Mood == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return Mood;
  }

  public String[] getTimePeriod()
  {
    if (TimePeriod == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return TimePeriod;
  }

  public String[] getTheme()
  {
    if (Theme == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return Theme;
  }

  public String[] getCharacter()
  {
    if (Character == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return Character;
  }

  public String[] getSetting()
  {
    if (Setting == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return Setting;
  }

  public String[] getSubject()
  {
    if (Subject == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return Subject;
  }

  public String[] getGeneral()
  {
    if (General == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return General;
  }

  public String[] getAllKeywords()
  {
    String mood[] = getMood();
    String timePeriod[] = getTimePeriod();
    String theme[] = getTheme();
    String character[] = getCharacter();
    String setting[] = getSetting();
    String subject[] = getSubject();
    String general[] = getGeneral();

    int totalWords = mood.length + timePeriod.length + theme.length + character.length + setting.length + subject.length + general.length;
    String returnValue[] = new String[totalWords];

    System.arraycopy(mood, 0, returnValue, returnValue.length - totalWords, mood.length);
    totalWords -= mood.length;
    System.arraycopy(timePeriod, 0, returnValue, returnValue.length - totalWords, timePeriod.length);
    totalWords -= timePeriod.length;
    System.arraycopy(theme, 0, returnValue, returnValue.length - totalWords, theme.length);
    totalWords -= theme.length;
    System.arraycopy(character, 0, returnValue, returnValue.length - totalWords, character.length);
    totalWords -= character.length;
    System.arraycopy(setting, 0, returnValue, returnValue.length - totalWords, setting.length);
    totalWords -= setting.length;
    System.arraycopy(subject, 0, returnValue, returnValue.length - totalWords, subject.length);
    totalWords -= subject.length;
    System.arraycopy(general, 0, returnValue, returnValue.length - totalWords, general.length);
    //totalWords -= general.length;

    return returnValue;
  }
}
