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
package sage;

public interface EPGImportPlugin
{
  /*
   * Returns a String[][2] for a given zip code. Each element pair represents a provider GUID & a provider name.
   * These names will be displayed in the Setup Wizard for SageTV. The provider GUIDs must be valid arguments
   * for Long.parseLong(providerID), represent positive numbers, and be consistent and unique for given provider.
   * An example of the return value is:
   * { { "1", "Test Lineup1"} {"2", "Test Lineup2"} }
   */
  public String[][] getProviders(String zipCode);
  /*
   * Returns a String[][2]. Each element pair represents a provider ID & a provider name.
   * These names will be displayed in the Setup Wizard for SageTV if Local Markets are selected
   * for the provider. The provider GUIDs must be valid arguments
   * for Long.parseLong(providerID), represent positive numbers, and be consistent and unique for given provider.
   * An example of the return value is:
   * { { "3", "Test Local Lineup1"} {"4", "Test Local Lineup2"} }
   */
  public String[][] getLocalMarkets();
  /*
   * This is called daily (or more often if other events occur) to update the EPG database.
   * It should return true if successful, and false if not. The providerID argument
   * references the provider selected from the functions above. The EPGDBPublic object
   * is what is used to make calls to the database to provide it with the new EPG information.
   * See the documentation on EPGDBPublic for more information
   */
  public boolean updateGuide(String providerID, EPGDBPublic dbInterface);
}
