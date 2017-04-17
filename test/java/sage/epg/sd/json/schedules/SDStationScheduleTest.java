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

import org.testng.annotations.Test;
import sage.epg.sd.DeserializeTest;

import java.io.IOException;

public class SDStationScheduleTest extends DeserializeTest
{
  @Test(groups = {"gson", "schedulesDirect", "schedule", "retry" })
  public void deserializeRetry() throws IOException
  {
    String error = "epg/sd/json/errors/7100.json";
    SDStationSchedule schedule = deserialize(error, SDStationSchedule.class);
    assert schedule.getCode() == 7100;
    assert schedule.retryTime() != null;
  }

  @Test(groups = {"gson", "schedulesDirect", "schedule", "error" })
  public void deserializeError() throws IOException
  {
    String error = "epg/sd/json/errors/2201.json";
    SDStationSchedule schedule = deserialize(error, SDStationSchedule.class);
    assert schedule.getCode() == 2201;
  }

  @Test(groups = {"gson", "schedulesDirect", "schedule", "error" })
  public void deserializeRangeError() throws IOException
  {
    String error = "epg/sd/json/errors/7020.json";
    SDStationSchedule schedule = deserialize(error, SDStationSchedule.class);
    assert schedule.getCode() == 7020;
  }

  @Test(groups = {"gson", "schedulesDirect", "schedule" })
  public void deserialize() throws IOException
  {
    String schedule = "epg/sd/json/schedules/scheduleStation.json";
    SDStationSchedule stationSchedule = deserialize(schedule, SDStationSchedule.class);
    assert stationSchedule.getCode() == 0;
    assert stationSchedule.getMetadata().getMd5() != null;
    assert stationSchedule.getMetadata().getModified() != null;
    assert stationSchedule.getMetadata().getStartDate() != null;

    for (SDProgramSchedule program : stationSchedule.getPrograms())
    {
      assert program.getProgramID() != null;
      assert program.getAirDateTime() != null;
      assert program.getMd5() != null;
    }
  }

  @Test(groups = {"gson", "schedulesDirect", "schedule" })
  public void deserializeBadVideoProperties() throws IOException
  {
    String schedule = "epg/sd/json/schedules/scheduleBadVideoProperties.json";
    SDStationSchedule stationSchedule = deserialize(schedule, SDStationSchedule.class);

    assert stationSchedule.getCode() == 0;
    assert stationSchedule.getMetadata().getMd5() != null;
    assert stationSchedule.getMetadata().getModified() != null;
    assert stationSchedule.getMetadata().getStartDate() != null;

    for (SDProgramSchedule program : stationSchedule.getPrograms())
    {
      assert program.getProgramID() != null;
      assert program.getAirDateTime() != null;
      assert program.getMd5() != null;
    }
  }
}
