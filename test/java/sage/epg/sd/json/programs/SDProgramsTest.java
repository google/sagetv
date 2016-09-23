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

public class SDProgramsTest extends DeserializeTest
{
  // Ensure all mandatory fields are present.
  private void basicValidation(SDProgram program)
  {
    assert program.getCode() == 0;
    assert program.getTitles() != null;
    assert program.getEntityType() != null;
    assert program.getMd5() != null;
    assert program.getProgramID() != null;
    assert program.getProgramID().length() == 14;

    if (program.getCast() != null)
    {
      for (SDPerson person : program.getCast())
      {
        assert person.getBillingOrder() != null;
        assert person.getName() != null;
        assert person.getRole() != null;
      }
    }

    if (program.getCrew() != null)
    {
      for (SDPerson person : program.getCrew())
      {
        assert person.getBillingOrder() != null;
        assert person.getName() != null;
        assert person.getRole() != null;
      }
    }

    if (program.getContentRating() != null)
    {
      for (SDContentRating rating : program.getContentRating())
      {
        assert rating.getBody() != null;
        assert rating.getCode() != null;
      }
    }

    if (program.getDescriptions()!= null)
    {
      if (program.getDescriptions().getDescription100() != null)
      {
        for (SDDescriptions.Description description : program.getDescriptions().getDescription100())
        {
          assert description.getDescription() != null;
          assert description.getDescriptionLanguage() != null;
        }
      }

      if (program.getDescriptions().getDescription1000() != null)
      {
        for (SDDescriptions.Description description : program.getDescriptions().getDescription1000())
        {
          assert description.getDescription() != null;
          assert description.getDescriptionLanguage() != null;
        }
      }
    }

    if (program.getEpisodeImage() != null)
    {
      assert program.getEpisodeImage().getHeight() != 0;
      assert program.getEpisodeImage().getWidth() != 0;
      assert program.getEpisodeImage().getUri() != null;
    }

    if (program.getEventDetails() != null)
    {
      assert program.getEventDetails().getVenue100() != null;
      if (program.getEventDetails().getTeams() != null)
      {
        for (SDEventDetails.Teams team : program.getEventDetails().getTeams())
        {
          assert team.getName() != null;
        }
      }
    }

    if (program.getMetadata() != null)
    {
      for (SDProgramMetadata metadata : program.getMetadata())
      {
        if (metadata != null && program.getEntityType().equals("Episode"))
        {
          assert metadata.getSeason() != 0;
          assert metadata.getEpisode() != 0;
        }
      }
    }

    if (program.getRecommendations() != null)
    {
      for (SDRecommendations recommendations : program.getRecommendations())
      {
        assert recommendations.getProgramId() != null;
        assert recommendations.getTitle120() != null;
      }
    }

    for (Title title : program.getTitles())
    {
      assert title.getTitle120() != null;
    }

    assert program.getTitle() != null;
    assert program.getTitle().length() > 0;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "retry" })
  public void deserializeRetry() throws IOException
  {
    String retry = "epg/sd/json/errors/6001.json";
    SDProgram program = deserialize(retry, SDProgram.class);
    assert program.getCode() == 6001;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "error" })
  public void deserializeError() throws IOException
  {
    String error = "epg/sd/json/errors/6000.json";
    SDProgram program = deserialize(error, SDProgram.class);
    assert program.getCode() == 6000;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "episode" })
  public void deserializeEpisode() throws IOException
  {
    String episode = "epg/sd/json/programs/programEpisode.json";
    SDProgram program = deserialize(episode, SDProgram.class);
    basicValidation(program);
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "series" })
  public void deserializeSeries() throws IOException
  {
    String series = "epg/sd/json/programs/programShow.json";
    SDProgram program = deserialize(series, SDProgram.class);
    basicValidation(program);
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "movie" })
  public void deserializeMovie() throws IOException
  {
    String movie = "epg/sd/json/programs/programMovie.json";
    SDProgram program = deserialize(movie, SDProgram.class);
    basicValidation(program);
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "sports" })
  public void deserializeSports() throws IOException
  {
    String sports = "epg/sd/json/programs/programSports.json";
    SDProgram program = deserialize(sports, SDProgram.class);
    basicValidation(program);
  }
}
