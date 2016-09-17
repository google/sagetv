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

public class SDLanguage
{
  private String digraph;
  private String name;

  protected SDLanguage(String digraph, String name)
  {
    this.digraph = digraph;
    this.name = name;
  }

  public String getDigraph()
  {
    return digraph;
  }

  public String getName()
  {
    return name;
  }
}
