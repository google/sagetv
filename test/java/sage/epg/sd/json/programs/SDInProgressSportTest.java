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

import org.testng.annotations.Test;
import sage.epg.sd.DeserializeTest;

import java.io.IOException;

public class SDInProgressSportTest extends DeserializeTest
{
  private static void verifyTeam(SDInProgressSport.TeamResult team)
  {
    assert team != null;
    assert team.getName() != null;
    assert team.getName().length() > 0;
    assert team.getScore() != null;
    assert team.getScore().length() > 0;
  }

  @Test(groups = {"gson", "schedulesDirect", "inProgressSport", "complete" })
  public void deserializeComplete() throws IOException
  {
    String future = "epg/sd/json/programs/inProgressSportComplete.json";
    SDInProgressSport program = deserialize(future, SDInProgressSport.class);
    assert program.getCode() == 0;
    verifyTeam(program.getAwayTeam());
    verifyTeam(program.getHomeTeam());
    assert program.isComplete();
    assert program.getProgramID() != null;
    assert program.getProgramID().length() > 0;
  }

  @Test(groups = {"gson", "schedulesDirect", "inProgressSport", "incomplete" })
  public void deserializeIncomplete() throws IOException
  {
    String future = "epg/sd/json/programs/inProgressSportIncomplete.json";
    SDInProgressSport program = deserialize(future, SDInProgressSport.class);
    assert program.getCode() == 0;
    verifyTeam(program.getAwayTeam());
    verifyTeam(program.getHomeTeam());
    assert !program.isComplete();
    assert program.getProgramID() != null;
    assert program.getProgramID().length() > 0;
  }

  @Test(groups = {"gson", "schedulesDirect", "inProgressSport", "future" })
  public void deserializeFuture() throws IOException
  {
    String future = "epg/sd/json/errors/6002.json";
    SDInProgressSport program = deserialize(future, SDInProgressSport.class);
    assert program.getCode() == 6002;
  }

  @Test(groups = {"gson", "schedulesDirect", "inProgressSport", "retry" })
  public void deserializeRetry() throws IOException
  {
    String retry = "epg/sd/json/errors/6001.json";
    SDInProgressSport program = deserialize(retry, SDInProgressSport.class);
    assert program.getCode() == 6001;
  }

  @Test(groups = {"gson", "schedulesDirect", "inProgressSport", "error" })
  public void deserializeError() throws IOException
  {
    String error = "epg/sd/json/errors/6000.json";
    SDInProgressSport program = deserialize(error, SDInProgressSport.class);
    assert program.getCode() == 6000;
  }
}
