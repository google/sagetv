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
package sage.epg.sd.json.schedules;

import sage.epg.sd.SDUtils;

public class SDScheduleMd5
{
  protected static final Md5[] EMPTY_MD5S = new Md5[0];

  private String stationID;
  private Md5 dates[];

  protected SDScheduleMd5(String stationID, Md5 dates[])
  {
    this.stationID = stationID;
    this.dates = dates != null ? dates : EMPTY_MD5S;
  }

  public int getStationID()
  {
    return SDUtils.fromStationIDtoSageTV(stationID);
  }

  public Md5[] getDates()
  {
    return dates;
  }

  public static class Md5
  {
    private String date;
    private String md5;

    protected Md5(String date, String md5)
    {
      this.date = date;
      this.md5 = md5;
    }

    public String getDate()
    {
      return date;
    }

    public String getMd5()
    {
      return md5;
    }
  }
}
