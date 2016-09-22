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
package sage.epg.sd.json.status;

import sage.Pooler;
import sage.epg.sd.json.headend.SDHeadendLineup;

public class SDStatus
{
  private static final SDHeadendLineup[] EMPTY_HEADEND_LINEUP = new SDHeadendLineup[0];
  private static final SDSystemStatus[] EMPTY_SYSTEM_STATUS = new SDSystemStatus[0];

  private SDAccount account;
  private SDHeadendLineup lineups[];
  private String lastDataUpdate;
  private String notifications[];
  private SDSystemStatus systemStatus[];
  private String serverID;
  private String datetime;
  private int code;

  public SDAccount getAccount()
  {
    return account;
  }

  public SDHeadendLineup[] getLineups()
  {
    if (lineups == null)
      return EMPTY_HEADEND_LINEUP;

    return lineups;
  }

  public String getLastDataUpdate()
  {
    return lastDataUpdate;
  }

  public String[] getNotifications()
  {
    if (notifications == null)
      return Pooler.EMPTY_STRING_ARRAY;

    return notifications;
  }

  public SDSystemStatus[] getSystemStatus()
  {
    if (systemStatus == null)
      return EMPTY_SYSTEM_STATUS;

    return systemStatus;
  }

  public String getServerID()
  {
    return serverID;
  }

  public String getDatetime()
  {
    return datetime;
  }

  public int getCode()
  {
    return code;
  }
}
