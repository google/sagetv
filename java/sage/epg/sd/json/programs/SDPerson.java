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

import sage.EPGDBPublic;
import sage.EPGDBPublic2;

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
        return EPGDBPublic.ACTOR_ROLE;
      case "Actress":
        return EPGDBPublic.ACTRESS_ROLE;
      case "Choreographer":
        return EPGDBPublic.CHOREOGRAPHER_ROLE;
      case "Coach":
        return EPGDBPublic.COACH_ROLE;
      case "Director":
        return EPGDBPublic.DIRECTOR_ROLE;
      case "Executive Producer":
        return EPGDBPublic.EXECUTIVE_PRODUCER_ROLE;
      case "Guest":
        return EPGDBPublic.GUEST_ROLE;
      case "Guest Star":
        return EPGDBPublic.GUEST_STAR_ROLE;
      case "Host":
        return EPGDBPublic.HOST_ROLE;
      case "Judge":
        return EPGDBPublic.JUDGE_ROLE;
      case "Lead Actor":
        return EPGDBPublic.LEAD_ACTOR_ROLE;
      case "Lead Actress":
        return EPGDBPublic.LEAD_ACTRESS_ROLE;
      case "Narrator":
        return EPGDBPublic.NARRATOR_ROLE;
      case "Producer":
        return EPGDBPublic.PRODUCER_ROLE;
      case "Sports Figure":
        return EPGDBPublic.SPORTS_FIGURE_ROLE;
      case "Supporting Actor":
        return EPGDBPublic.SUPPORTING_ACTOR_ROLE;
      case "Supporting Actress":
        return EPGDBPublic.SUPPORTING_ACTRESS_ROLE;
      case "Writer":
        return EPGDBPublic.WRITER_ROLE;
      case "Contestant":
        return EPGDBPublic2.CONTESTANT_ROLE;
      case "Correspondent":
        return EPGDBPublic2.CORRESPONDENT_ROLE;
      case "Team":
        return EPGDBPublic2.TEAM_ROLE;
      case "Guest Voice":
        return EPGDBPublic2.GUEST_VOICE_ROLE;
      case "Anchor":
        return EPGDBPublic2.ANCHOR_ROLE;
      case "Voice":
        return EPGDBPublic2.VOICE_ROLE;
      case "Musical Guest":
        return EPGDBPublic2.MUSICAL_GUEST_ROLE;
    }

    return 0;
  }

  /**
   * string indicating the name of the character this person played. Optional.
   */
  public String getCharacterName()
  {
    if (characterName == null)
      return "";

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
}
