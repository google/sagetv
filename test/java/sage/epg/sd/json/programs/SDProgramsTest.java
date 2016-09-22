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
  public void deserializeRetry()
  {
    String retry = "{\n" +
        "    \"response\": \"PROGRAMID_QUEUED\",\n" +
        "    \"code\": 6001,\n" +
        "    \"serverID\": \"20141201.1\",\n" +
        "    \"message\": \"Fetching programID:SH012423280000  Retry.\",\n" +
        "    \"datetime\": \"2014-11-14T19:15:54Z\"\n" +
        "}";

    SDProgram program = deserialize(retry, SDProgram.class);
    assert program.getCode() == 6001;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "error" })
  public void deserializeError()
  {
    String error = "{\n" +
        "    \"response\": \"INVALID_PROGRAMID\",\n" +
        "    \"code\": 6000,\n" +
        "    \"serverID\": \"20140530.1\",\n" +
        "    \"message\": \"Invalid programID:ZZ01234567891234\",\n" +
        "    \"datetime\": \"2014-11-14T19:17:54Z\"\n" +
        "}";

    SDProgram program = deserialize(error, SDProgram.class);
    assert program.getCode() == 6000;
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "episode" })
  public void deserializeEpisode()
  {
    String episode = "{\n" +
        "    \"programID\": \"EP012599310061\",\n" +
        "    \"resourceID\": \"8100033\",\n" +
        "    \"titles\": [\n" +
        "      {\n" +
        "        \"title120\": \"Hot in Cleveland\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"descriptions\": {\n" +
        "      \"description100\": [\n" +
        "        {\n" +
        "          \"descriptionLanguage\": \"en\",\n" +
        "          \"description\": \"Melanie's hair blow out lasts unusually long; tensions rise among the ladies.\"\n" +
        "        }\n" +
        "      ],\n" +
        "      \"description1000\": [\n" +
        "        {\n" +
        "          \"descriptionLanguage\": \"en\",\n" +
        "          \"description\": \"Melanie's hair blow out lasts unusually long; tensions rise among the ladies and the two rival hairdressers at Elka's favorite salon.\"\n" +
        "        }\n" +
        "      ]\n" +
        "    },\n" +
        "    \"originalAirDate\": \"2012-06-06\",\n" +
        "    \"genres\": [\n" +
        "      \"Sitcom\"\n" +
        "    ],\n" +
        "    \"episodeTitle150\": \"Blow Outs\",\n" +
        "    \"metadata\": [\n" +
        "      {\n" +
        "        \"Gracenote\": {\n" +
        "          \"season\": 3,\n" +
        "          \"episode\": 24,\n" +
        "          \"totalEpisodes\": 24\n" +
        "        }\n" +
        "      }\n" +
        "    ],\n" +
        "    \"contentRating\": [\n" +
        "      {\n" +
        "        \"body\": \"Australian Classification Board\",\n" +
        "        \"code\": \"PG\",\n" +
        "        \"country\": \"AUS\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Canadian Parental Rating\",\n" +
        "        \"code\": \"PG\",\n" +
        "        \"country\": \"CAN\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"USA Parental Rating\",\n" +
        "        \"code\": \"TVPG\",\n" +
        "        \"country\": \"USA\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"duration\": 1320,\n" +
        "    \"cast\": [\n" +
        "      {\n" +
        "        \"billingOrder\": \"01\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"154\",\n" +
        "        \"personId\": \"154\",\n" +
        "        \"name\": \"Valerie Bertinelli\",\n" +
        "        \"characterName\": \"Melanie Moretti\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"02\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"1918\",\n" +
        "        \"personId\": \"1918\",\n" +
        "        \"name\": \"Betty White\",\n" +
        "        \"characterName\": \"Elka Ostrovsky\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"03\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"65675\",\n" +
        "        \"personId\": \"65675\",\n" +
        "        \"name\": \"Wendie Malick\",\n" +
        "        \"characterName\": \"Victoria Chase\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"04\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"71516\",\n" +
        "        \"personId\": \"71516\",\n" +
        "        \"name\": \"Jane Leeves\",\n" +
        "        \"characterName\": \"Joy Scroggs\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"05\",\n" +
        "        \"role\": \"Guest Star\",\n" +
        "        \"nameId\": \"639710\",\n" +
        "        \"personId\": \"618197\",\n" +
        "        \"name\": \"Elizabeth J. Carlisle\",\n" +
        "        \"characterName\": \"Shamed Woman\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"06\",\n" +
        "        \"role\": \"Guest Star\",\n" +
        "        \"nameId\": \"691245\",\n" +
        "        \"personId\": \"664581\",\n" +
        "        \"name\": \"Carol Herman\",\n" +
        "        \"characterName\": \"Mrs. Magee\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"07\",\n" +
        "        \"role\": \"Guest Star\",\n" +
        "        \"nameId\": \"1345\",\n" +
        "        \"personId\": \"1345\",\n" +
        "        \"name\": \"Regis Philbin\",\n" +
        "        \"characterName\": \"Pierre\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"08\",\n" +
        "        \"role\": \"Guest Star\",\n" +
        "        \"nameId\": \"15748\",\n" +
        "        \"personId\": \"15748\",\n" +
        "        \"name\": \"David Spade\",\n" +
        "        \"characterName\": \"Christopher\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"entityType\": \"Episode\",\n" +
        "    \"showType\": \"Series\",\n" +
        "    \"hasImageArtwork\": true,\n" +
        "    \"episodeImage\": {\n" +
        "      \"width\": \"240\",\n" +
        "      \"height\": \"360\",\n" +
        "      \"uri\": \"assets/p9094920_e_v5_ab.jpg\",\n" +
        "      \"category\": \"Iconic\",\n" +
        "      \"primary\": \"true\",\n" +
        "      \"tier\": \"Episode\"\n" +
        "    },\n" +
        "    \"md5\": \"+I8UiR6+vP7kO3qRmQZ60w\"\n" +
        "  }";

    SDProgram program = deserialize(episode, SDProgram.class);
    basicValidation(program);
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "series" })
  public void deserializeSeries()
  {
    String series = "{\n" +
        "    \"programID\": \"SH012599310000\",\n" +
        "    \"resourceID\": \"8100033\",\n" +
        "    \"titles\": [\n" +
        "      {\n" +
        "        \"title120\": \"Hot in Cleveland\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"descriptions\": {\n" +
        "      \"description100\": [\n" +
        "        {\n" +
        "          \"descriptionLanguage\": \"en\",\n" +
        "          \"description\": \"Best friends rediscover themselves in Ohio.\"\n" +
        "        }\n" +
        "      ],\n" +
        "      \"description1000\": [\n" +
        "        {\n" +
        "          \"descriptionLanguage\": \"en\",\n" +
        "          \"description\": \"When their plane has trouble while on a flight to Paris, three glamorous LA women find themselves in a completely new and unexpected place. Feeling the need for a girls-only, once-in-a-lifetime trip, Melanie cashes in her airline miles to finance a trip to the city of light for herself and her two best friends, Joy and Victoria, all \\\"of a certain age\\\" and feeling less than desirable. They never make it to Paris, though -- when the stricken plane lands in Cleveland, the women suddenly find they're popular with the men there. Realizing that while they may be lukewarm in LA, they're hot in Cleveland, the women decide to relocate. They immediately fall in love with their rental house. It takes a little longer, however, for them to warm up to the house's longtime caretaker (Betty White).\"\n" +
        "        }\n" +
        "      ]\n" +
        "    },\n" +
        "    \"originalAirDate\": \"2010-06-16\",\n" +
        "    \"genres\": [\n" +
        "      \"Sitcom\"\n" +
        "    ],\n" +
        "    \"metadata\": [\n" +
        "      {\n" +
        "        \"Gracenote\": {\n" +
        "          \"totalEpisodes\": 153,\n" +
        "          \"totalSeasons\": 6,\n" +
        "          \"season\": 0,\n" +
        "          \"episode\": 0\n" +
        "        }\n" +
        "      }\n" +
        "    ],\n" +
        "    \"keyWords\": {\n" +
        "      \"Mood\": [\n" +
        "        \"Hilarious\",\n" +
        "        \"Charming\",\n" +
        "        \"Spirited\"\n" +
        "      ],\n" +
        "      \"Time Period\": [\n" +
        "        \"2010s\"\n" +
        "      ],\n" +
        "      \"Character\": [\n" +
        "        \"Middle-aged woman\",\n" +
        "        \"Friend\",\n" +
        "        \"Caretaker\",\n" +
        "        \"Actress\",\n" +
        "        \"Beautician\",\n" +
        "        \"Neighbor\"\n" +
        "      ],\n" +
        "      \"Setting\": [\n" +
        "        \"Cleveland\",\n" +
        "        \"House\"\n" +
        "      ],\n" +
        "      \"Subject\": [\n" +
        "        \"Midlife crisis\",\n" +
        "        \"Relocation\",\n" +
        "        \"Female bonding\",\n" +
        "        \"Dating\"\n" +
        "      ]\n" +
        "    },\n" +
        "    \"contentRating\": [\n" +
        "      {\n" +
        "        \"body\": \"Australian Classification Board\",\n" +
        "        \"code\": \"PG\",\n" +
        "        \"country\": \"AUS\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Canadian Parental Rating\",\n" +
        "        \"code\": \"PG\",\n" +
        "        \"country\": \"CAN\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"USA Parental Rating\",\n" +
        "        \"code\": \"TVPG\",\n" +
        "        \"country\": \"USA\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"cast\": [\n" +
        "      {\n" +
        "        \"billingOrder\": \"01\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"154\",\n" +
        "        \"personId\": \"154\",\n" +
        "        \"name\": \"Valerie Bertinelli\",\n" +
        "        \"characterName\": \"Melanie Moretti\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"02\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"1918\",\n" +
        "        \"personId\": \"1918\",\n" +
        "        \"name\": \"Betty White\",\n" +
        "        \"characterName\": \"Elka Ostrovsky\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"03\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"65675\",\n" +
        "        \"personId\": \"65675\",\n" +
        "        \"name\": \"Wendie Malick\",\n" +
        "        \"characterName\": \"Victoria Chase\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"04\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"71516\",\n" +
        "        \"personId\": \"71516\",\n" +
        "        \"name\": \"Jane Leeves\",\n" +
        "        \"characterName\": \"Joy Scroggs\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"crew\": [\n" +
        "      {\n" +
        "        \"billingOrder\": \"01\",\n" +
        "        \"role\": \"Executive Producer\",\n" +
        "        \"nameId\": \"153066\",\n" +
        "        \"personId\": \"86955\",\n" +
        "        \"name\": \"Sean Hayes\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"02\",\n" +
        "        \"role\": \"Executive Producer\",\n" +
        "        \"nameId\": \"646590\",\n" +
        "        \"personId\": \"624973\",\n" +
        "        \"name\": \"Todd Milliner\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"entityType\": \"Show\",\n" +
        "    \"showType\": \"Series\",\n" +
        "    \"recommendations\": [\n" +
        "      {\n" +
        "        \"programID\": \"SH007633980000\",\n" +
        "        \"title120\": \"The New Adventures of Old Christine\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH011580820000\",\n" +
        "        \"title120\": \"Cougar Town\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"SH014076210000\",\n" +
        "        \"title120\": \"Happily Divorced\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"awards\": [\n" +
        "      {\n" +
        "        \"recipient\": \"Betty White\",\n" +
        "        \"name\": \"Screen Actors Guild Awards\",\n" +
        "        \"awardName\": \"Screen Actors Guild Awards\",\n" +
        "        \"personId\": \"1918\",\n" +
        "        \"year\": \"2011\",\n" +
        "        \"category\": \"Outstanding Performance by an Ensemble in a Comedy Series\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"recipient\": \"Betty White\",\n" +
        "        \"name\": \"Screen Actors Guild Awards\",\n" +
        "        \"awardName\": \"Screen Actors Guild Awards\",\n" +
        "        \"personId\": \"1918\",\n" +
        "        \"won\": true,\n" +
        "        \"year\": \"2011\",\n" +
        "        \"category\": \"Outstanding Performance by a Female Actor in a Comedy Series\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"recipient\": \"Wendie Malick\",\n" +
        "        \"name\": \"Screen Actors Guild Awards\",\n" +
        "        \"awardName\": \"Screen Actors Guild Awards\",\n" +
        "        \"personId\": \"65675\",\n" +
        "        \"year\": \"2011\",\n" +
        "        \"category\": \"Outstanding Performance by an Ensemble in a Comedy Series\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"recipient\": \"Valerie Bertinelli\",\n" +
        "        \"name\": \"Screen Actors Guild Awards\",\n" +
        "        \"awardName\": \"Screen Actors Guild Awards\",\n" +
        "        \"personId\": \"154\",\n" +
        "        \"year\": \"2011\",\n" +
        "        \"category\": \"Outstanding Performance by an Ensemble in a Comedy Series\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"recipient\": \"Jane Leeves\",\n" +
        "        \"name\": \"Screen Actors Guild Awards\",\n" +
        "        \"awardName\": \"Screen Actors Guild Awards\",\n" +
        "        \"personId\": \"71516\",\n" +
        "        \"year\": \"2011\",\n" +
        "        \"category\": \"Outstanding Performance by an Ensemble in a Comedy Series\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"recipient\": \"Betty White\",\n" +
        "        \"name\": \"Emmy (Primetime)\",\n" +
        "        \"awardName\": \"Emmy (Primetime)\",\n" +
        "        \"personId\": \"1918\",\n" +
        "        \"year\": \"2011\",\n" +
        "        \"category\": \"Outstanding Supporting Actress in a Comedy Series\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"recipient\": \"Betty White\",\n" +
        "        \"name\": \"Screen Actors Guild Awards\",\n" +
        "        \"awardName\": \"Screen Actors Guild Awards\",\n" +
        "        \"personId\": \"1918\",\n" +
        "        \"won\": true,\n" +
        "        \"year\": \"2012\",\n" +
        "        \"category\": \"Outstanding Performance by a Female Actor in a Comedy Series\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"recipient\": \"Betty White\",\n" +
        "        \"name\": \"Screen Actors Guild Awards\",\n" +
        "        \"awardName\": \"Screen Actors Guild Awards\",\n" +
        "        \"personId\": \"1918\",\n" +
        "        \"year\": \"2013\",\n" +
        "        \"category\": \"Outstanding Performance by a Female Actor in a Comedy Series\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"hasImageArtwork\": true,\n" +
        "    \"md5\": \"PnWUFzR6ALnKk0y9jkTlIw\"\n" +
        "  }";

    SDProgram program = deserialize(series, SDProgram.class);
    basicValidation(program);
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "movie" })
  public void deserializeMovie()
  {
    String movie = "{\n" +
        "    \"programID\": \"MV000158920000\",\n" +
        "    \"titles\": [\n" +
        "      {\n" +
        "        \"title120\": \"Raiders of the Lost Ark\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"descriptions\": {\n" +
        "      \"description100\": [\n" +
        "        {\n" +
        "          \"descriptionLanguage\": \"en\",\n" +
        "          \"description\": \"Indiana Jones (Harrison Ford) braves snakes and Nazis to find the biblical ark of the covenant.\"\n" +
        "        }\n" +
        "      ],\n" +
        "      \"description1000\": [\n" +
        "        {\n" +
        "          \"descriptionLanguage\": \"en\",\n" +
        "          \"description\": \"Renowned archeologist and expert in the occult, Dr. Indiana Jones, is hired by the U.S. Government to find the Ark of the Covenant, which is believed to still hold the ten commandments. Unfortunately, agents of Hitler are also after the Ark. Indy, and his ex-flame Marion, escape from various close scrapes in a quest that takes them from Nepal to Cairo.\"\n" +
        "        }\n" +
        "      ]\n" +
        "    },\n" +
        "    \"genres\": [\n" +
        "      \"Adventure\",\n" +
        "      \"Action\"\n" +
        "    ],\n" +
        "    \"officialURL\": \"http://www.indianajones.com/\",\n" +
        "    \"keyWords\": {\n" +
        "      \"Mood\": [\n" +
        "        \"Thrilling\",\n" +
        "        \"Engaging\",\n" +
        "        \"Charming\"\n" +
        "      ],\n" +
        "      \"Time Period\": [\n" +
        "        \"1930s\"\n" +
        "      ],\n" +
        "      \"Theme\": [\n" +
        "        \"Escape\",\n" +
        "        \"Adventure\",\n" +
        "        \"Pursuit\",\n" +
        "        \"Quest\"\n" +
        "      ],\n" +
        "      \"Character\": [\n" +
        "        \"Archaeologist\",\n" +
        "        \"Villain\",\n" +
        "        \"Explorer\",\n" +
        "        \"Love interest\"\n" +
        "      ],\n" +
        "      \"Setting\": [\n" +
        "        \"Desert\",\n" +
        "        \"Airplane\",\n" +
        "        \"Germany\",\n" +
        "        \"Egypt\",\n" +
        "        \"Jungle\",\n" +
        "        \"Washington, D.C.\"\n" +
        "      ],\n" +
        "      \"Subject\": [\n" +
        "        \"Exploration\",\n" +
        "        \"Expedition\",\n" +
        "        \"Nazism\",\n" +
        "        \"Showdown\"\n" +
        "      ],\n" +
        "      \"General\": [\n" +
        "        \"Ark of the Covenant\",\n" +
        "        \"Snakes\"\n" +
        "      ]\n" +
        "    },\n" +
        "    \"contentRating\": [\n" +
        "      {\n" +
        "        \"body\": \"British Board of Film Classification\",\n" +
        "        \"code\": \"PG\",\n" +
        "        \"country\": \"GBR\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Alberta's Film Classification Board\",\n" +
        "        \"code\": \"14A\",\n" +
        "        \"country\": \"CAN\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"B.C. Film Classification Office\",\n" +
        "        \"code\": \"PG\",\n" +
        "        \"country\": \"CAN\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Manitoba Film Classification Board\",\n" +
        "        \"code\": \"14A\",\n" +
        "        \"country\": \"CAN\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Maritime Film Classification Board\",\n" +
        "        \"code\": \"14\",\n" +
        "        \"country\": \"CAN\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Ontario Film Review Board\",\n" +
        "        \"code\": \"PG\",\n" +
        "        \"country\": \"CAN\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Saskatchewan Film and Video Classification Board\",\n" +
        "        \"code\": \"PG\",\n" +
        "        \"country\": \"CAN\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Departamento de Justiça, Classificação, Títulos e Qualificação\",\n" +
        "        \"code\": \"L\",\n" +
        "        \"country\": \"BRA\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Freiwillige Selbstkontrolle der Filmwirtschaft\",\n" +
        "        \"code\": \"12\",\n" +
        "        \"country\": \"DEU\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Mediakasvatus- ja kuvaohjelmayksikkö\",\n" +
        "        \"code\": \"K12\",\n" +
        "        \"country\": \"FIN\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Motion Picture Association of America\",\n" +
        "        \"code\": \"PG\",\n" +
        "        \"country\": \"USA\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"body\": \"Film & Publication Board\",\n" +
        "        \"code\": \"10\",\n" +
        "        \"country\": \"ZAF\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"contentAdvisory\": [\n" +
        "      \"Adult Language\",\n" +
        "      \"Adult Situations\",\n" +
        "      \"Violence\"\n" +
        "    ],\n" +
        "    \"movie\": {\n" +
        "      \"year\": \"1981\",\n" +
        "      \"duration\": 6900,\n" +
        "      \"qualityRating\": [\n" +
        "        {\n" +
        "          \"ratingsBody\": \"Gracenote\",\n" +
        "          \"rating\": \"4\",\n" +
        "          \"minRating\": \"1\",\n" +
        "          \"maxRating\": \"4\",\n" +
        "          \"increment\": \".5\"\n" +
        "        }\n" +
        "      ]\n" +
        "    },\n" +
        "    \"cast\": [\n" +
        "      {\n" +
        "        \"billingOrder\": \"01\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"25704\",\n" +
        "        \"personId\": \"25704\",\n" +
        "        \"name\": \"Harrison Ford\",\n" +
        "        \"characterName\": \"Dr. Henry 'Indiana' Jones, Jr.\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"02\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"35610\",\n" +
        "        \"personId\": \"35610\",\n" +
        "        \"name\": \"Karen Allen\",\n" +
        "        \"characterName\": \"Marion Ravenwood\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"03\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"74672\",\n" +
        "        \"personId\": \"74672\",\n" +
        "        \"name\": \"Paul Freeman\",\n" +
        "        \"characterName\": \"Rene Belloq\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"04\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"170192\",\n" +
        "        \"personId\": \"169111\",\n" +
        "        \"name\": \"Wolf Kahler\",\n" +
        "        \"characterName\": \"Dietrich\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"05\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"85233\",\n" +
        "        \"personId\": \"85233\",\n" +
        "        \"name\": \"Ronald Lacey\",\n" +
        "        \"characterName\": \"Toht\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"06\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"67941\",\n" +
        "        \"personId\": \"67941\",\n" +
        "        \"name\": \"John Rhys-Davies\",\n" +
        "        \"characterName\": \"Sallah\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"07\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"83286\",\n" +
        "        \"personId\": \"83286\",\n" +
        "        \"name\": \"Denholm Elliott\",\n" +
        "        \"characterName\": \"Marcus Brody\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"08\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"151447\",\n" +
        "        \"personId\": \"151352\",\n" +
        "        \"name\": \"Anthony Higgins\",\n" +
        "        \"characterName\": \"Gobler\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"09\",\n" +
        "        \"role\": \"Actor\",\n" +
        "        \"nameId\": \"3584\",\n" +
        "        \"personId\": \"3584\",\n" +
        "        \"name\": \"Alfred Molina\",\n" +
        "        \"characterName\": \"Sapito\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"crew\": [\n" +
        "      {\n" +
        "        \"billingOrder\": \"01\",\n" +
        "        \"role\": \"Director\",\n" +
        "        \"nameId\": \"1672\",\n" +
        "        \"personId\": \"1672\",\n" +
        "        \"name\": \"Steven Spielberg\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"02\",\n" +
        "        \"role\": \"Writer (Story)\",\n" +
        "        \"nameId\": \"23344\",\n" +
        "        \"personId\": \"23344\",\n" +
        "        \"name\": \"George Lucas\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"03\",\n" +
        "        \"role\": \"Writer (Story)\",\n" +
        "        \"nameId\": \"162726\",\n" +
        "        \"personId\": \"162064\",\n" +
        "        \"name\": \"Philip Kaufman\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"04\",\n" +
        "        \"role\": \"Writer\",\n" +
        "        \"nameId\": \"884\",\n" +
        "        \"personId\": \"884\",\n" +
        "        \"name\": \"Lawrence Kasdan\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"05\",\n" +
        "        \"role\": \"Executive Producer\",\n" +
        "        \"nameId\": \"473903\",\n" +
        "        \"personId\": \"465028\",\n" +
        "        \"name\": \"Howard G. Kazanjian\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"06\",\n" +
        "        \"role\": \"Executive Producer\",\n" +
        "        \"nameId\": \"23344\",\n" +
        "        \"personId\": \"23344\",\n" +
        "        \"name\": \"George Lucas\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"07\",\n" +
        "        \"role\": \"Producer\",\n" +
        "        \"nameId\": \"140145\",\n" +
        "        \"personId\": \"140145\",\n" +
        "        \"name\": \"Frank Marshall\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"08\",\n" +
        "        \"role\": \"Associate Producer\",\n" +
        "        \"nameId\": \"446163\",\n" +
        "        \"personId\": \"437288\",\n" +
        "        \"name\": \"Robert Watts\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"09\",\n" +
        "        \"role\": \"Original Music\",\n" +
        "        \"nameId\": \"533648\",\n" +
        "        \"personId\": \"516972\",\n" +
        "        \"name\": \"John Williams\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"10\",\n" +
        "        \"role\": \"Cinematographer\",\n" +
        "        \"nameId\": \"473904\",\n" +
        "        \"personId\": \"465029\",\n" +
        "        \"name\": \"Douglas Slocombe\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"11\",\n" +
        "        \"role\": \"Film Editor\",\n" +
        "        \"nameId\": \"726543\",\n" +
        "        \"personId\": \"696907\",\n" +
        "        \"name\": \"Michael Kahn\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"12\",\n" +
        "        \"role\": \"Film Editor\",\n" +
        "        \"nameId\": \"23344\",\n" +
        "        \"personId\": \"23344\",\n" +
        "        \"name\": \"George Lucas\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"13\",\n" +
        "        \"role\": \"Film Editor\",\n" +
        "        \"nameId\": \"1672\",\n" +
        "        \"personId\": \"1672\",\n" +
        "        \"name\": \"Steven Spielberg\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"14\",\n" +
        "        \"role\": \"Casting\",\n" +
        "        \"nameId\": \"473905\",\n" +
        "        \"personId\": \"465030\",\n" +
        "        \"name\": \"Jane Feinberg\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"15\",\n" +
        "        \"role\": \"Casting\",\n" +
        "        \"nameId\": \"473906\",\n" +
        "        \"personId\": \"465031\",\n" +
        "        \"name\": \"Mike Fenton\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"16\",\n" +
        "        \"role\": \"Casting\",\n" +
        "        \"nameId\": \"431308\",\n" +
        "        \"personId\": \"422433\",\n" +
        "        \"name\": \"Mary Selway\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"17\",\n" +
        "        \"role\": \"Production Designer\",\n" +
        "        \"nameId\": \"424533\",\n" +
        "        \"personId\": \"415658\",\n" +
        "        \"name\": \"Norman Reynolds\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"18\",\n" +
        "        \"role\": \"Art Direction\",\n" +
        "        \"nameId\": \"454627\",\n" +
        "        \"personId\": \"445752\",\n" +
        "        \"name\": \"Leslie Dilley\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"19\",\n" +
        "        \"role\": \"Set Decoration\",\n" +
        "        \"nameId\": \"262828\",\n" +
        "        \"personId\": \"259262\",\n" +
        "        \"name\": \"Michael Ford\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"20\",\n" +
        "        \"role\": \"Costume Designer\",\n" +
        "        \"nameId\": \"473907\",\n" +
        "        \"personId\": \"465032\",\n" +
        "        \"name\": \"Deborah Nadoolman\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"21\",\n" +
        "        \"role\": \"Hair Stylist\",\n" +
        "        \"nameId\": \"473908\",\n" +
        "        \"personId\": \"465033\",\n" +
        "        \"name\": \"Mike Lockey\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"billingOrder\": \"22\",\n" +
        "        \"role\": \"Makeup Artist\",\n" +
        "        \"nameId\": \"473909\",\n" +
        "        \"personId\": \"465034\",\n" +
        "        \"name\": \"Dickie Mills\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"entityType\": \"Movie\",\n" +
        "    \"showType\": \"Feature Film\",\n" +
        "    \"recommendations\": [\n" +
        "      {\n" +
        "        \"programID\": \"MV000747180000\",\n" +
        "        \"title120\": \"The Mummy\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"MV001078290000\",\n" +
        "        \"title120\": \"Lara Croft: Tomb Raider\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"programID\": \"MV001522590000\",\n" +
        "        \"title120\": \"National Treasure\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"awards\": [\n" +
        "      {\n" +
        "        \"name\": \"Academy Award\",\n" +
        "        \"awardName\": \"Academy Award\",\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Best Music (Score)\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"Academy Award\",\n" +
        "        \"awardName\": \"Academy Award\",\n" +
        "        \"won\": true,\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Best Film Editing\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"Academy Award\",\n" +
        "        \"awardName\": \"Academy Award\",\n" +
        "        \"won\": true,\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Best Art Direction\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"Academy Award\",\n" +
        "        \"awardName\": \"Academy Award\",\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Best Picture\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"Academy Award\",\n" +
        "        \"awardName\": \"Academy Award\",\n" +
        "        \"won\": true,\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Best Visual Effects\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"Academy Award\",\n" +
        "        \"awardName\": \"Academy Award\",\n" +
        "        \"won\": true,\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Best Sound\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"Academy Award\",\n" +
        "        \"awardName\": \"Academy Award\",\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Best Cinematography\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"Academy Award\",\n" +
        "        \"awardName\": \"Academy Award\",\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Best Directing\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"Golden Globe\",\n" +
        "        \"awardName\": \"Golden Globe\",\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Best Director - Motion Picture\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"recipient\": \"Denholm Elliott\",\n" +
        "        \"name\": \"British Academy of Film & Television Arts\",\n" +
        "        \"awardName\": \"British Academy of Film & Television Arts\",\n" +
        "        \"personId\": \"83286\",\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Supporting Artist\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"British Academy of Film & Television Arts\",\n" +
        "        \"awardName\": \"British Academy of Film & Television Arts\",\n" +
        "        \"won\": true,\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Production Design\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"British Academy of Film & Television Arts\",\n" +
        "        \"awardName\": \"British Academy of Film & Television Arts\",\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Editing\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"British Academy of Film & Television Arts\",\n" +
        "        \"awardName\": \"British Academy of Film & Television Arts\",\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Film\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"British Academy of Film & Television Arts\",\n" +
        "        \"awardName\": \"British Academy of Film & Television Arts\",\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Sound\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"British Academy of Film & Television Arts\",\n" +
        "        \"awardName\": \"British Academy of Film & Television Arts\",\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Original Film Music\"\n" +
        "      },\n" +
        "      {\n" +
        "        \"name\": \"British Academy of Film & Television Arts\",\n" +
        "        \"awardName\": \"British Academy of Film & Television Arts\",\n" +
        "        \"year\": \"1981\",\n" +
        "        \"category\": \"Cinematography\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"hasImageArtwork\": true,\n" +
        "    \"md5\": \"6GMLjpf+yvG0mF7AbFydsQ\"\n" +
        "  }";

    SDProgram program = deserialize(movie, SDProgram.class);
    basicValidation(program);
  }

  @Test(groups = {"gson", "schedulesDirect", "program", "sports" })
  public void deserializeSports()
  {
    String sports = "{\n" +
        "    \"programID\": \"EP005544683862\",\n" +
        "    \"resourceID\": \"191273\",\n" +
        "    \"titles\": [\n" +
        "      {\n" +
        "        \"title120\": \"MLB Baseball\"\n" +
        "      }\n" +
        "    ],\n" +
        "    \"eventDetails\": {\n" +
        "      \"venue100\": \"Busch Stadium\",\n" +
        "      \"teams\": [\n" +
        "        {\n" +
        "          \"name\": \"St. Louis Cardinals\",\n" +
        "          \"isHome\": true\n" +
        "        },\n" +
        "        {\n" +
        "          \"name\": \"Chicago Cubs\"\n" +
        "        }\n" +
        "      ],\n" +
        "      \"gameDate\": \"2015-10-10\"\n" +
        "    },\n" +
        "    \"descriptions\": {\n" +
        "      \"description1000\": [\n" +
        "        {\n" +
        "          \"descriptionLanguage\": \"en\",\n" +
        "          \"description\": \"The Cubs, led by young sluggers Anthony Rizzo and Kris Bryant, look to bounce back from a shutout loss when they visit Matt Carpenter and the Cardinals in Game 2 of the National League Division Series.\"\n" +
        "        }\n" +
        "      ],\n" +
        "      \"description100\": [\n" +
        "        {\n" +
        "          \"descriptionLanguage\": \"en\",\n" +
        "          \"description\": \"From Busch Stadium.\"\n" +
        "        }\n" +
        "      ]\n" +
        "    },\n" +
        "    \"genres\": [\n" +
        "      \"Baseball\",\n" +
        "      \"Playoff sports\"\n" +
        "    ],\n" +
        "    \"episodeTitle150\": \"Chicago Cubs at St. Louis Cardinals\",\n" +
        "    \"entityType\": \"Sports\",\n" +
        "    \"showType\": \"Sports event\",\n" +
        "    \"hasImageArtwork\": true,\n" +
        "    \"md5\": \"TU8iBnHEgUh5RozFIWvHrw\"\n" +
        "  }";

    SDProgram program = deserialize(sports, SDProgram.class);
    basicValidation(program);
  }
}
