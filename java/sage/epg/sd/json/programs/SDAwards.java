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

public class SDAwards
{
  private String name;
  private String awardName;
  private String recipient;
  private String personId;
  private boolean won;
  private String year;
  private String category;

  /**
   * string containing the name of the award. Optional.
   */
  public String getName()
  {
    return name;
  }

  /**
   * string containing the name of the award. Optional.
   */
  public String getAwardName()
  {
    return awardName;
  }

  /**
   * string containing the name of the recipient. Optional.
   */
  public String getRecipient()
  {
    return recipient;
  }

  /**
   * personId of the recipient. Optional.
   */
  public String getPersonId()
  {
    return personId;
  }

  /**
   * boolean. Optional.
   */
  public boolean isWon()
  {
    return won;
  }

  /**
   * string. Year of award. Optional.
   */
  public String getYear()
  {
    return year;
  }

  /**
   * string. Optional.
   */
  public String getCategory()
  {
    return category;
  }
}
