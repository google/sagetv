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

import sage.epg.sd.json.SDError;

public class SDSeriesDescArray
{
  protected static final SDSeriesDesc[] EMPTY_SERIES_DESC = new SDSeriesDesc[0];

  SDSeriesDesc seriesDescs[];

  protected SDSeriesDescArray(SDSeriesDesc seriesDesc[])
  {
    this.seriesDescs = seriesDesc != null ? seriesDesc : EMPTY_SERIES_DESC;
  }

  public SDSeriesDesc[] getSeriesDescs()
  {
    return seriesDescs;
  }
}
