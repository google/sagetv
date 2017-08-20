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

public class SDInProgressSport implements SDError
{
  private int code;
  private String message;

  private String programID;
  private String response;
  private boolean isComplete;
  private String serverID;
  private String datetime;
  private Result result;

  /**
   * Create an empty in progress sport so that we can return an accurate error code.
   *
   * @param code The error code to set for this object
   */
  public SDInProgressSport(int code)
  {
    this.code = code;
  }

  /**
   * If any code other than OK is returned, {@link #isComplete()} should not be used because it
   * will be incorrect.
   * <p/>
   * 0 = OK
   * <br/>
   * 6000 = Invalid program or a sport Schedules Direct doesn't track.
   * <br/>
   * 6001 = Try again in 30 seconds.
   * <br/>
   * 6002 = Valid program, but it is in the future.
   */
  @Override
  public int getCode()
  {
    return code;
  }

  @Override
  public String getMessage()
  {
    return message;
  }

  public String getProgramID()
  {
    return programID;
  }

  public String getResponse()
  {
    return response;
  }

  /**
   * Is the in progress sport done.
   */
  public boolean isComplete()
  {
    return isComplete;
  }

  public String getServerID()
  {
    return serverID;
  }

  public String getDatetime()
  {
    return datetime;
  }

  public TeamResult getHomeTeam()
  {
    return result != null ? result.homeTeam : null;
  }

  public TeamResult getAwayTeam()
  {
    return result != null ? result.awayTeam : null;
  }

  @Override
  public String toString()
  {
    return "SDInProgressSport{" +
      "code=" + code +
      ", message='" + message + '\'' +
      ", programID='" + programID + '\'' +
      ", response='" + response + '\'' +
      ", isComplete=" + isComplete +
      ", serverID='" + serverID + '\'' +
      ", datetime='" + datetime + '\'' +
      ", result=" + result +
      '}';
  }

  // This is here more for convenience so we don't need to write a custom deserializer. Because of
  // this, the class should remain private.
  private class Result
  {
    TeamResult homeTeam;
    TeamResult awayTeam;

    @Override
    public String toString()
    {
      return "Result{" +
        "homeTeam=" + homeTeam +
        ", awayTeam=" + awayTeam +
        '}';
    }
  }

  public class TeamResult
  {
    String name;
    String score;

    public String getName()
    {
      return name;
    }

    public String getScore()
    {
      return score;
    }

    @Override
    public String toString()
    {
      return "TeamResult{" +
        "name='" + name + '\'' +
        ", score='" + score + '\'' +
        '}';
    }
  }
}
