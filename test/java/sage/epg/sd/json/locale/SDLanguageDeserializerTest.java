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

import org.testng.annotations.Test;
import sage.epg.sd.DeserializeTest;

public class SDLanguageDeserializerTest extends DeserializeTest
{
  @Test(groups = {"gson", "schedulesDirect", "language" })
  public void deserialize()
  {
    // Source: https://json.schedulesdirect.org/20141201/available/languages
    String languageJson = "{\"af\":\"Afrikaans\",\"ar\":\"Arabic\",\"ca\":\"Catalan\",\"cy\":\"Welsh\",\"da\":\"Danish\",\"de\":\"German\",\"el\":\"Greek\",\"en\":\"English\",\"en-GB\":\"English - United Kingdom\",\"es\":\"Spanish\",\"es-ES\":\"Spanish - Spain\",\"et\":\"Estonian\",\"eu\":\"Basque\",\"fa\":\"Persian - Farsi\",\"fi\":\"Finnish\",\"fr\":\"French\",\"fr-CA\":\"French - Canada\",\"gd\":\"Scottish Gaelic\",\"he\":\"Hebrew\",\"hi\":\"Hindi\",\"hr\":\"Croatian\",\"hu\":\"Hungarian\",\"hy\":\"Armenian\",\"it\":\"Italian\",\"iu\":\"Inuktitut\",\"ja\":\"Japanese\",\"ko\":\"Korean\",\"la\":\"Latin\",\"lv\":\"Latvian\",\"ml\":\"Malayalam\",\"nl\":\"Dutch\",\"no\":\"Norwegian\",\"pa\":\"Panjabi\",\"pl\":\"Polish\",\"pt\":\"Portuguese\",\"pt-BR\":\"Portuguese - Brazil\",\"ro\":\"Romanian\",\"ru\":\"Russian\",\"sr\":\"Serbian\",\"sv\":\"Swedish\",\"ta\":\"Tamil\",\"te\":\"Telugu\",\"th\":\"Thai\",\"tl\":\"Tagalog\",\"tr\":\"Turkish\",\"und\":\"English\",\"ur\":\"Urdu\",\"vi\":\"Vietnamese\",\"zh\":\"Chinese\"}";
    SDLanguage languages[] = deserialize(languageJson, SDLanguage[].class);
  }
}
