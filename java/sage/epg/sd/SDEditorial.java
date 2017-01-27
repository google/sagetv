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
package sage.epg.sd;

import sage.Airing;
import sage.epg.sd.json.programs.SDProgram;
import sage.epg.sd.json.programs.SDRecommendation;

import java.util.Comparator;

/**
 * This is class is used to organize all of the data collected when generating editorials based on
 * recommendations from Schedules Direct.
 */
public class SDEditorial
{
  private SDRecommendation recommendation;
  private SDProgram program;
  private Airing airing;
  private int weight;

  protected SDEditorial(SDRecommendation recommendation)
  {
    this.recommendation = recommendation;
  }

  public SDRecommendation getRecommendation()
  {
    return recommendation;
  }

  public SDProgram getProgram()
  {
    return program;
  }

  protected void setProgram(SDProgram program)
  {
    this.program = program;
  }

  public Airing getAiring()
  {
    return airing;
  }

  protected void setAiring(Airing airing)
  {
    this.airing = airing;
  }

  public int getWeight()
  {
    return weight;
  }

  protected void incrementWeight()
  {
    this.weight++;
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SDEditorial editorial = (SDEditorial) o;

    return recommendation != null ? recommendation.equals(editorial.recommendation) : editorial.recommendation == null;
  }

  @Override
  public int hashCode()
  {
    return recommendation != null ? recommendation.hashCode() : 0;
  }

  public static final Comparator<SDEditorial> WEIGHT_COMPARATOR = new Comparator<SDEditorial>()
  {
    @Override
    public int compare(SDEditorial o1, SDEditorial o2)
    {
      return Integer.compare(o1.weight, o2.weight);
    }
  };
}
