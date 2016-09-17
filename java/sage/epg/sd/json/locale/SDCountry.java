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
package sage.epg.sd.json.locale;

public class SDCountry
{
  private String fullName;
  private String shortName;
  private String postalCodeExample;
  private String postalCode; // Regex

  public String getFullName()
  {
    return fullName;
  }

  public String getShortName()
  {
    return shortName;
  }

  public String getPostalCodeExample()
  {
    return postalCodeExample;
  }

  public String getPostalCode()
  {
    return postalCode;
  }
}
