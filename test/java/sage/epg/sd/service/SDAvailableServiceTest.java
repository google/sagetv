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
package sage.epg.sd.service;

import org.testng.annotations.Test;
import sage.epg.sd.DeserializeTest;
import sage.epg.sd.json.service.SDAvailableService;

public class SDAvailableServiceTest extends DeserializeTest
{
  @Test(groups = {"gson", "schedulesDirect", "availableService" })
  public void deserialize()
  {
    // Source: https://json.schedulesdirect.org/20141201/available
    String languageJson = "[{\"type\":\"COUNTRIES\",\"description\":\"List of countries which are available.\",\"uri\":\"\\/20141201\\/available\\/countries\"},{\"type\":\"LANGUAGES\",\"description\":\"List of language digraphs and their language names.\",\"uri\":\"\\/20141201\\/available\\/languages\"},{\"type\":\"DVB-S\",\"description\":\"List of satellites which are available.\",\"uri\":\"\\/20141201\\/available\\/dvb-s\"},{\"type\":\"DVB-T\",\"description\":\"List of Freeview transmitters in a country. Country options: AUS, GBR, NZL\",\"uri\":\"\\/20141201\\/transmitters\\/{ISO 3166-1 alpha-3}\"}]";
    SDAvailableService availableServices[] = deserialize(languageJson, SDAvailableService[].class);
  }
}
