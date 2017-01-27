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

public class SDRecommendation
{
  private String programID;
  private String title120;

  /**
   * programID of the recommendation. Mandatory.
   */
  public String getProgramId()
  {
    return programID;
  }

  /**
   * string indicating the name of the similar program. Mandatory.
   */
  public String getTitle120()
  {
    return title120;
  }

  @Override
  public String toString()
  {
    return "SDRecommendations{" +
      "programID='" + programID + '\'' +
      ", title120='" + title120 + '\'' +
      '}';
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SDRecommendation that = (SDRecommendation) o;

    return programID != null ? programID.equals(that.programID) : that.programID == null;
  }

  @Override
  public int hashCode()
  {
    return programID != null ? programID.hashCode() : 0;
  }
}
