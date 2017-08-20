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

import sage.Show;

public class SDPerson
{
  private String personId;
  private String nameId;
  private String name;
  private String role;
  private String characterName;
  private String billingOrder;

  // Used for deserialization
  public SDPerson()
  {

  }

  // Convert team to person.
  protected SDPerson(String name, String role, int billingOrder)
  {
    this.name = name;
    this.role = role;
    // Mandatory field
    this.billingOrder = Integer.toString(billingOrder);
  }

  /**
   * string for this person. Used to retrieve images. Optional.
   */
  public String getPersonId()
  {
    if (personId == null)
      return "";

    return personId;
  }

  public int getPersonIdAsInt()
  {
    if (personId == null || personId.length() == 0)
      return 0;
    try
    {
      return Integer.parseInt(personId);
    }
    catch (NumberFormatException e)
    {
      return 0;
    }
  }

  /**
   * string for this person. Used to differentiate people that have various names, such as due to
   * marriage, divorce, etc. Optional. Actors in adult movies will typically not have a personID or
   * nameID.
   */
  public String getNameId()
  {
    if (nameId == null)
      return "";

    return nameId;
  }

  public int getNameIdAsInt()
  {
    if (nameId == null || nameId.length() == 0)
      return 0;
    try
    {
      return Integer.parseInt(nameId);
    }
    catch (NumberFormatException e)
    {
      return 0;
    }
  }

  public boolean isAlias()
  {
    return personId != null && personId.length() > 0 &&
      nameId != null && nameId.length() > 0 && !personId.equals(nameId);
  }

  /**
   * string indicating the person's name. Mandatory.
   */
  public String getName()
  {
    return name;
  }

  /**
   * string indicating what role this person had. Mandatory.
   */
  public String getRole()
  {
    return role;
  }

  /**
   * role string converted to SageTV byte.
   */
  public byte getRoleID()
  {
    if (role == null)
      return 0;

    switch (role)
    {
      case "Actor":
        return Show.ACTOR_ROLE;
      case "Actress":
        return Show.ACTRESS_ROLE;
      case "Choreographer":
        return Show.CHOREOGRAPHER_ROLE;
      case "Coach":
        return Show.COACH_ROLE;
      case "Director":
        return Show.DIRECTOR_ROLE;
      case "Executive Producer":
        return Show.EXECUTIVE_PRODUCER_ROLE;
      case "Guest":
        return Show.GUEST_ROLE;
      case "Guest Star":
        return Show.GUEST_STAR_ROLE;
      case "Host":
        return Show.HOST_ROLE;
      case "Judge":
        return Show.JUDGE_ROLE;
      case "Lead Actor":
        return Show.LEAD_ACTOR_ROLE;
      case "Lead Actress":
        return Show.LEAD_ACTRESS_ROLE;
      case "Narrator":
        return Show.NARRATOR_ROLE;
      case "Producer":
        return Show.PRODUCER_ROLE;
      case "Sports Figure":
        return Show.SPORTS_FIGURE_ROLE;
      case "Supporting Actor":
        return Show.SUPPORTING_ACTOR_ROLE;
      case "Supporting Actress":
        return Show.SUPPORTING_ACTRESS_ROLE;
      case "Writer":
      case "Screenwriter":
      case "Screen Story Writer":
      case "Screen Story":
        return Show.WRITER_ROLE;
      case "Contestant":
        return Show.CONTESTANT_ROLE;
      case "Correspondent":
        return Show.CORRESPONDENT_ROLE;
      case "Team":
        return Show.TEAM_ROLE;
      case "Guest Voice":
        return Show.GUEST_VOICE_ROLE;
      case "Anchor":
        return Show.ANCHOR_ROLE;
      case "Voice":
        return Show.VOICE_ROLE;
      case "Musical Guest":
        return Show.MUSICAL_GUEST_ROLE;
      case "Composer":
        return Show.COMPOSER_ROLE;
      case "Film Editor":
        return Show.FILM_EDITOR_ROLE;
      case "Music Theme":
      case "Music Score:":
      case "Non-Original Music":
      case "Original Music":
      case "Music":
        return Show.MUSIC_ROLE;
      case "Casting":
        return Show.CASTING_ROLE;
      case "Cinematographer":
        return Show.CINEMATOGRAPHER_ROLE;
      case "Costume Designer":
        return Show.COSTUME_DESIGNER_ROLE;
      case "Production Design":
      case "Production Designer":
      case "Art Director":
      case "Art Direction":
      case "Set Decoration":
        return Show.PRODUCTION_DESIGN_ROLE;
      case "Creator":
        return Show.CREATOR_ROLE;
      case "Co-Producer":
        return Show.CO_PRODUCER_ROLE;
      case "Associate Producer":
        return Show.ASSOCIATE_PRODUCER_ROLE;
      case "First Assistant Director":
        return Show.FIRST_ASSISTANT_DIRECTOR_ROLE;
      case "Supervising Art Direction":
        return Show.SUPERVISING_ART_DIRECTION_ROLE;
      case "Co-Executive Producer":
        return Show.CO_EXECUTIVE_PRODUCER_ROLE;
      case "Director of Photography":
        return Show.DIRECTOR_OF_PHOTOGRAPHY_ROLE;
      case "Unit Production Manager":
        return Show.UNIT_PRODUCTION_MANAGER_ROLE;
      case "Key Makeup Artist":
      case "Makeup Artist":
        return Show.MAKEUP_ARTIST_ROLE;
      case "Assistant Director":
        return Show.ASSISTANT_DIRECTOR_ROLE;
      case "Music Supervisor":
        return Show.MUSIC_SUPERVISOR_ROLE;
    }

    if (role.contains("Choreographer"))
      return Show.CHOREOGRAPHER_ROLE;
    else if (role.contains("Coach"))
      return Show.COACH_ROLE;
    else if (role.startsWith("Executive Producer, ")) // Various language executive producers
      return Show.EXECUTIVE_PRODUCER_ROLE;
    else if (role.startsWith("Producer, ")) // Various language producers
      return Show.PRODUCER_ROLE;
    else if (role.startsWith("Director, ")) // Various language directors
      return Show.PRODUCER_ROLE;
    else if (role.startsWith("Writer (") || role.startsWith("Screenwriter"))
      return Show.WRITER_ROLE;

    return 0;
  }

  /**
   * string indicating the name of the character this person played. Optional.
   * <p/>
   * If a character name does not exist, their role is returned instead.
   */
  public String getCharacterName()
  {
    if (characterName == null || characterName.length() == 0)
    {
      if (role != null)
      {
        return role;
      }
      return "";
    }

    return characterName;
  }

  /**
   * string indicating billing order. Mandatory.
   */
  public String getBillingOrder()
  {
    if (billingOrder == null)
      return "";

    return billingOrder;
  }

  @Override
  public String toString()
  {
    return "SDPerson{" +
      "personId='" + personId + '\'' +
      ", nameId='" + nameId + '\'' +
      ", name='" + name + '\'' +
      ", role='" + role + '\'' +
      ", characterName='" + characterName + '\'' +
      ", billingOrder='" + billingOrder + '\'' +
      '}';
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SDPerson person = (SDPerson) o;

    return personId != null ? personId.equals(person.personId) : person.personId == null;
  }

  @Override
  public int hashCode()
  {
    return personId != null ? personId.hashCode() : 0;
  }
}
