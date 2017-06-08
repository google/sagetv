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

public class SDContentRating
{
  private String body;
  private String code;
  private String country;

  /**
   * name of the rating body. Mandatory.
   */
  public String getBody()
  {
    return body;
  }

  /**
   * The rating assigned. Dependent on the rating body. Mandatory.
   */
  public String getCode()
  {
    return code;
  }

  public String getCountry()
  {
    return country;
  }

  @Override
  public String toString()
  {
    return "SDContentRating{" +
      "body='" + body + '\'' +
      ", code='" + code + '\'' +
      ", country='" + country + '\'' +
      '}';
  }
}
