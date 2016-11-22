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
package sage.epg.sd.json.map.channel;

import sage.epg.sd.json.map.SDChannelMap;

public class SDAdvancedChannelMap extends SDChannelMap
{
  // Source: http://forums.schedulesdirect.org/viewtopic.php?f=17&t=2722&p=8917
  private int frequencyHz;
  private String polarization;
  private String deliverySystem; // (DVB-C, DVB-S, DVB-T, ATSC)
  private String modulationSystem; // (QPSK, QAM64, QAM256, 8VSB)
  private int symbolrate;
  private String fec;
  private int vpid;
  private int apid_a;
  private int apid_d;
  private int teletextID;
  private String conditionalAccess;
  private int serviceID;
  private int networkID;
  private int transportID;
  private int radioID;
  private String providerChannel;
  private String providerCallsign;
  private String logicalChannelNumber;
  private int channelMajor; // (typically for ATSC digital channels)
  private int channelMinor;
  private String matchType; // (providerChannel, providerCallsign, logicalChannelNumber)

  public int getFrequencyHz()
  {
    return frequencyHz;
  }

  public String getPolarization()
  {
    return polarization;
  }

  public String getDeliverySystem()
  {
    return deliverySystem;
  }

  public String getModulationSystem()
  {
    return modulationSystem;
  }

  public int getSymbolrate()
  {
    return symbolrate;
  }

  public String getFec()
  {
    return fec;
  }

  public int getVpid()
  {
    return vpid;
  }

  public int getApid_a()
  {
    return apid_a;
  }

  public int getApid_d()
  {
    return apid_d;
  }

  public int getTeletextID()
  {
    return teletextID;
  }

  public String getConditionalAccess()
  {
    return conditionalAccess;
  }

  public int getServiceID()
  {
    return serviceID;
  }

  public int getNetworkID()
  {
    return networkID;
  }

  public int getTransportID()
  {
    return transportID;
  }

  public int getRadioID()
  {
    return radioID;
  }

  public String getProviderChannel()
  {
    return providerChannel;
  }

  public String getProviderCallsign()
  {
    return providerCallsign;
  }

  public String getLogicalChannelNumber()
  {
    return logicalChannelNumber;
  }

  public int getChannelMajor()
  {
    return channelMajor;
  }

  public int getChannelMinor()
  {
    return channelMinor;
  }

  public String getMatchType()
  {
    return matchType;
  }

  public String getMatchTypeValue()
  {
    if (matchType == null)
      return null;

    switch (matchType)
    {
      case "providerChannel":
        return providerChannel;
      case "providerCallsign":
        return providerCallsign;
      case "logicalChannelNumber":
        return logicalChannelNumber;
      default:
        return null;
    }
  }

  @Override
  public String toString()
  {
    return "SDAdvancedChannelMap{" +
        "frequencyHz=" + frequencyHz +
        ", polarization='" + polarization + '\'' +
        ", deliverySystem='" + deliverySystem + '\'' +
        ", modulationSystem='" + modulationSystem + '\'' +
        ", symbolrate=" + symbolrate +
        ", fec='" + fec + '\'' +
        ", vpid=" + vpid +
        ", apid_a=" + apid_a +
        ", apid_d=" + apid_d +
        ", teletextID=" + teletextID +
        ", conditionalAccess='" + conditionalAccess + '\'' +
        ", serviceID=" + serviceID +
        ", networkID=" + networkID +
        ", transportID=" + transportID +
        ", radioID=" + radioID +
        ", providerChannel='" + providerChannel + '\'' +
        ", providerCallsign='" + providerCallsign + '\'' +
        ", logicalChannelNumber='" + logicalChannelNumber + '\'' +
        ", channelMajor=" + channelMajor +
        ", channelMinor=" + channelMinor +
        ", matchType='" + matchType + '\'' +
        "} " + super.toString();
  }
}
