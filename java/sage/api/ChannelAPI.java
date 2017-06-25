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
package sage.api;

import sage.*;

/**
 * A Channel represents a logical station on a broadcast, cable or satellite lineup.
 * <p>
 * Channel numbers always refer to logical channel numbers EXCEPT when they are explicitly stated to refer
 * to physical channel numbers.
 * <p>
 * SageTV will automatically convert the following types to Channel if used for a parameter that requires the Channel type:<p>
 * Airing - every Airing has an associated Channel, that Channel is used<p>
 * MediaFile - due to the 1:1 mapping between MediaFiles and Airings, the MediaFile is resolved to an Airing and then to a Channel
 */
public class ChannelAPI{
  private ChannelAPI() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Channel", "GetChannelDescription", 1, new String[] { "Channel" })
    {
      /**
       * Gets the descriptive name for this Channel. This is the longer text string.
       * @param Channel the Channel Object
       * @return the longer descriptive name for the specified Channel
       *
       * @declaration public String GetChannelDescription(Channel Channel);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Channel c = getChannel(stack);
        return (c == null) ? "" : c.getLongName();
      }});
    rft.put(new PredefinedJEPFunction("Channel", "GetChannelName", 1, new String[] { "Channel" })
    {
      /**
       * Gets the name for this Channel. This is the Channel's call sign.
       * @param Channel the Channel Object
       * @return the name (call sign) for the specified Channel
       *
       * @declaration public String GetChannelName(Channel Channel);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Channel c = getChannel(stack);
        return (c == null) ? "" : c.getName();
      }});
    rft.put(new PredefinedJEPFunction("Channel", "GetChannelNetwork", 1, new String[] { "Channel" })
    {
      /**
       * Gets the name of the associated network for this Channel. This is a way of grouping kinds of Channels together.
       * @param Channel the Channel Object
       * @return the network name for the specified Channel
       *
       * @declaration public String GetChannelNetwork(Channel Channel);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Channel c = getChannel(stack);
        return (c == null) ? "" : c.getNetwork();
      }});
    rft.put(new PredefinedJEPFunction("Channel", "GetChannelNumber", 1, new String[] { "Channel" })
    {
      /**
       * Gets the channel number to tune to for this Channel. SageTV will return the first channel number it finds for this Channel since there may be multiple ones.
       * @param Channel the Channel Object
       * @return a channel number associated with this Channel
       *
       * @declaration public String GetChannelNumber(Channel Channel);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Channel c = getChannel(stack);
        return (c == null) ? "" : c.getNumber();
      }});
    rft.put(new PredefinedJEPFunction("Channel", "GetChannelNumberForLineup", 2, new String[] { "Channel", "Lineup" })
    {
      /**
       * Gets the channel number to tune to for this Channel on the specified lineup.
       * SageTV will return the first channel number it finds for this Channel on the lineup since there may be multiple ones.
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       * @return the channel number for the specified Channel on the specified Lineup
       *
       * @declaration public String GetChannelNumberForLineup(Channel Channel, String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        Channel c = getChannel(stack);
        return (c == null) ? "" : c.getNumber(EPG.getInstance().getProviderIDForEPGDSName(s));
      }});
    rft.put(new PredefinedJEPFunction("Channel", "GetPhysicalChannelNumberForLineup", 2, new String[] { "Channel", "Lineup" })
    {
      /**
       * Gets the physical channel number to tune to for this Channel on the specified lineup.
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       * @return the physical channel number for the specified Channel on the specified Lineup
       *
       * @since 5.1
       *
       * @declaration public String GetPhysicalChannelNumberForLineup(Channel Channel, String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        Channel c = getChannel(stack);
        return EPG.getInstance().getPhysicalChannel(EPG.getInstance().getProviderIDForEPGDSName(s), (c == null) ? 0 : c.getStationID());
      }});
    rft.put(new PredefinedJEPFunction("Channel", "IsChannelViewable", 1, new String[] { "Channel" })
    {
      /**
       * Returns true if there is a configured lineup for which this channel is viewable.
       * @param Channel the Channel object
       * @return true if there is a configured lineup for which this channel is viewable.
       *
       * @declaration public boolean IsChannelViewable(Channel Channel);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Channel c = getChannel(stack);
        return Boolean.valueOf((c == null) ? true : c.isViewable());
      }});
    rft.put(new PredefinedJEPFunction("Channel", "IsChannelViewableOnLineup", 2, new String[] { "Channel", "Lineup" })
    {
      /**
       * Returns true if this Channel is viewable on the specified Lineup
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       * @return true if this Channel is viewable on the specified Lineup
       *
       * @declaration public boolean IsChannelViewableOnLineup(Channel Channel, String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String lineup = getString(stack);
        Channel c = getChannel(stack);
        return Boolean.valueOf(c != null && c.isViewable(EPG.getInstance().getProviderIDForEPGDSName(lineup)));
      }});
    rft.put(new PredefinedJEPFunction("Channel", "IsChannelViewableOnNumberOnLineup", 3, new String[] { "Channel", "ChannelNumber", "Lineup" })
    {
      /**
       * Returns true if this Channel is viewable on the specified Lineup on the specified channel number
       * @param Channel the Channel object
       * @param ChannelNumber the channel number to check
       * @param Lineup the name of the Lineup
       * @return true if this Channel is viewable on the specified Lineup on the specified channel number
       *
       * @declaration public boolean IsChannelViewableOnNumberOnLineup(Channel Channel, String ChannelNumber, String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String lineup = getString(stack);
        String num = getString(stack);
        Channel c = getChannel(stack);
        return Boolean.valueOf(c != null && EPG.getInstance().getEPGDSForEPGDSName(lineup).canViewStationOnChannel(
            c.getStationID(), num));
      }});
    rft.put(new PredefinedJEPFunction("Channel", "GetChannelNumbersForLineup", 2, new String[] { "Channel", "Lineup" })
    {
      /**
       * Gets the channel numbers which can be used to tune this Channel on the specified lineup.
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       * @return the channel numbers for the specified Channel on the specified Lineup
       *
       * @declaration public String[] GetChannelNumbersForLineup(Channel Channel, String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        Channel c = getChannel(stack);
        return EPG.getInstance().getChannels(EPG.getInstance().getProviderIDForEPGDSName(s), c == null ? 0 : c.getStationID());
      }});
    rft.put(new PredefinedJEPFunction("Channel", "ClearChannelMappingOnLineup", 2, new String[] { "Channel", "Lineup" }, true)
    {
      /**
       * Clears any associated channel mappings that were created for this Channel on the specified Lineup
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       *
       * @declaration public void ClearChannelMappingOnLineup(Channel Channel, String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        EPG.getInstance().clearOverride(EPG.getInstance().getProviderIDForEPGDSName(s), getChannel(stack).getStationID()); return null;
      }});
    rft.put(new PredefinedJEPFunction("Channel", "IsChannelRemappedOnLineup", 2, new String[] { "Channel", "Lineup" })
    {
      /**
       * Returns true if the user has remapped this Channel to a different number than it's default on the specified Lineup
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       * @return true if the user has remapped this Channel to a different number than it's default on the specified Lineup
       *
       * @declaration public boolean IsChannelRemappedOnLineup(Channel Channel, String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        return Boolean.valueOf(EPG.getInstance().isOverriden(EPG.getInstance().getProviderIDForEPGDSName(s), getChannel(stack).getStationID()));
      }});
    rft.put(new PredefinedJEPFunction("Channel", "SetChannelMappingForLineup", 3, new String[] { "Channel", "Lineup", "NewNumber" }, true)
    {
      /**
       * Maps a channel on a lineup to a new channel number.
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       * @param NewNumber the new channel number to use for this Channel
       *
       * @declaration public void SetChannelMappingForLineup(Channel Channel, String Lineup, String NewNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String n = getString(stack);
        String s = getString(stack);
        EPG.getInstance().setOverride(EPG.getInstance().getProviderIDForEPGDSName(s),
            getChannel(stack).getStationID(), n); return null;
      }});
    rft.put(new PredefinedJEPFunction("Channel", "SetChannelMappingsForLineup", 3, new String[] { "Channel", "Lineup", "NewNumbers" }, true)
    {
      /**
       * Maps a channel on a lineup to a new channel number(s).
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       * @param NewNumbers the new channel numbers to use for this Channel
       * @since 6.4.3
       *
       * @declaration public void SetChannelMappingsForLineup(Channel Channel, String Lineup, String[] NewNumbers);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String[] n = getStringList(stack);
        String s = getString(stack);
        EPG.getInstance().setOverride(EPG.getInstance().getProviderIDForEPGDSName(s),
            getChannel(stack).getStationID(), n); return null;
      }});
    rft.put(new PredefinedJEPFunction("Channel", "ClearPhysicalChannelMappingOnLineup", 2, new String[] { "Channel", "Lineup" }, true)
    {
      /**
       * Clears any associated physical channel mappings that were created for this Channel on the specified Lineup
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       *
       * @since 5.1
       *
       * @declaration public void ClearPhysicalChannelMappingsOnLineup(Channel Channel, String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        EPG.getInstance().clearPhysicalOverride(EPG.getInstance().getProviderIDForEPGDSName(s), getChannel(stack).getStationID()); return null;
      }});
    rft.put(new PredefinedJEPFunction("Channel", "IsPhysicalChannelRemappedOnLineup", 2, new String[] { "Channel", "Lineup" })
    {
      /**
       * Returns true if the user has remapped this physical Channel to a different physical number than it's default on the specified Lineup
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       * @return true if the user has remapped this physical Channel to a different number than it's default on the specified Lineup
       *
       * @since 5.1
       *
       * @declaration public boolean IsPhysicalChannelRemappedOnLineup(Channel Channel, String Lineup);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        return Boolean.valueOf(EPG.getInstance().isPhysicalOverriden(EPG.getInstance().getProviderIDForEPGDSName(s), getChannel(stack).getStationID()));
      }});
    rft.put(new PredefinedJEPFunction("Channel", "SetPhysicalChannelMappingForLineup", 3, new String[] { "Channel", "Lineup", "NewNumber" }, true)
    {
      /**
       * Maps a Channel on a lineup to a new physical channel number.
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       * @param NewNumber the new phyical channel number to use for this Channel
       *
       * @since 5.1
       *
       * @declaration public void SetPhysicalChannelMappingForLineup(Channel Channel, String Lineup, String NewNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String n = getString(stack);
        String s = getString(stack);
        EPG.getInstance().setPhysicalOverride(EPG.getInstance().getProviderIDForEPGDSName(s),
            getChannel(stack).getStationID(), n); return null;
      }});
    rft.put(new PredefinedJEPFunction("Channel", "GetStationID", 1, new String[] { "Channel" })
    {
      /**
       * Returns an ID which can be used with {@link #GetChannelForStationID GetChannelForStationID()} for doing keyed lookups of Channel objects
       * @param Channel the Channel object
       * @return the station ID for the specified Channel
       *
       * @declaration public int GetStationID(Channel Channel);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Channel c = getChannel(stack);
        return new Integer((c == null) ? 0 : c.getStationID());
      }});
    rft.put(new PredefinedJEPFunction("Channel", "GetChannelLogo", -1, new String[] { "Channel", "Type", "Index", "Fallback" })
    {
      /**
       * Gets the logo image for the specified Channel if one exists
       * @param Channel the Channel object
       * @return the logo image for the Channel
       *
       * @declaration public MetaImage GetChannelLogo(Channel Channel);
       */

      /**
       * Returns a Channel logo for the requested Channel if one exists. This can provide more detailed requests then the single argument GetChannelLogo call.
       * @param Channel the Channel object
       * @param Type the type of image, can be one of "Small", "Med" or "Large" (all logos have all sizes available, w/ the exception of user-supplied logos)
       * @param Index the 0-based index of the image to retrieve when multiple images exist for a given Type (there are only ever 0, 1 or 2 logos for a channel)
       * @param Fallback should be true if an alternate image is allowed (this enables checking user-supplied logos first, as well as falling back to the primary logo if a secondary one is requested but does not exist)
       * @return a MetaImage corresponding to the requested image, or null if no image matching the requested parameters is found or an invalid Type is specified
       * @since 7.1
       *
       * @declaration public MetaImage GetChannelLogo(Channel Channel, String Type, int Index, boolean Fallback);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 1)
        {
          Channel c = getChannel(stack);
          return (c == null) ? null : EPG.getInstance().getLogo(c, stack.getUIComponent());
        }
        else
        {
          boolean fallback = evalBool(stack.pop());
          int index = getInt(stack);
          String imageType = getString(stack);
          Channel c = getChannel(stack);
          if (c == null)
            return null;
          int imageTypeNum = 0;
          if ("Small".equalsIgnoreCase(imageType))
            imageTypeNum = Channel.LOGO_SMALL;
          else if ("Med".equalsIgnoreCase(imageType) || "Medium".equalsIgnoreCase(imageType))
            imageTypeNum = Channel.LOGO_MED;
          else if ("Large".equalsIgnoreCase(imageType))
            imageTypeNum = Channel.LOGO_LARGE;
          if (imageTypeNum == 0) return null;
          if (!fallback)
          {
            if (c.getLogoCount(imageTypeNum) <= index)
              return null;
            return MetaImage.getMetaImage(c.getLogoUrl(index, imageTypeNum), stack.getUIComponent());
          }

          MetaImage userLogo = EPG.getInstance().getLogo(c.getName(), stack.getUIComponent());
          if (userLogo != null)
            return userLogo;

          if (c.getLogoCount(imageTypeNum) > index)
            return MetaImage.getMetaImage(c.getLogoUrl(index, imageTypeNum), stack.getUIComponent());
          if (c.getLogoCount(imageTypeNum) > 0)
            return MetaImage.getMetaImage(c.getLogoUrl(0, imageTypeNum), stack.getUIComponent());
          return null;
        }
      }});
    rft.put(new PredefinedJEPFunction("Channel", "IsChannelObject", 1, new String[] { "Channel" })
    {
      /**
       * Returns true if the argument is a Channel object. Automatic type conversion is NOT done in this call.
       * @param Channel the object to test
       * @return true if the argument is a Channel object
       *
       * @declaration public boolean IsChannelObject(Channel Channel);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof sage.vfs.MediaNode)
          o = ((sage.vfs.MediaNode) o).getDataObject();
        return Boolean.valueOf(o instanceof Channel);
      }});
    rft.put(new PredefinedJEPFunction("Channel", "SetChannelViewabilityForChannelNumberOnLineup", 4, new String[] { "Channel", "ChannelNumber", "Lineup", "Viewable" }, true)
    {
      /**
       * Sets whether or not the specified Channel is viewable on the specified number on the specified Lineup
       * @param Channel the Channel object
       * @param ChannelNumber the channel number to set the viewability state for
       * @param Lineup the name of the Lineup
       * @param Viewable true if is viewable, false if it is not
       *
       * @declaration public void SetChannelViewabilityForChannelNumberOnLineup(Channel Channel, String ChannelNumber, String Lineup, boolean Viewable);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean good = evalBool(stack.pop());
        String lineup = getString(stack);
        String num = getString(stack);
        EPG.getInstance().getEPGDSForEPGDSName(lineup).setCanViewStationOnChannel(getChannel(stack).getStationID(),
            num, good); return null;
      }});
    rft.put(new PredefinedJEPFunction("Channel", "SetChannelViewabilityForChannelOnLineup", 3, new String[] { "Channel", "Lineup", "Viewable(T/F)" }, true)
    {
      /**
       * Sets whether or not the specified Channel is viewable on the specified Lineup. This affects all channel numbers it appears on.
       * @param Channel the Channel object
       * @param Lineup the name of the Lineup
       * @param Viewable true if is viewable, false if it is not
       *
       * @declaration public void SetChannelViewabilityForChannelOnLineup(Channel Channel, String Lineup, boolean Viewable);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean good = evalBool(stack.pop());
        String lineup = getString(stack);
        EPG.getInstance().getEPGDSForEPGDSName(lineup).setCanViewStation(getChannel(stack).getStationID(), good);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Channel", "GetChannelForStationID", new String[] { "StationID" })
    {
      /**
       * Returns the Channel object that has the corresponding station ID. The station ID is retrieved using {@link #GetStationID GetStationID()}
       * @param StationID the station ID to look up
       * @return the Channel with the specified station ID
       *
       * @declaration public Channel GetChannelForStationID(int StationID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int i = getInt(stack);
        return Wizard.getInstance().getChannelForStationID(i);
      }});
    rft.put(new PredefinedJEPFunction("Channel", "AddChannel", 4, new String[] {"CallSign", "Name", "Network", "StationID"}, true)
    {
      /**
       * Adds a new Channel to the database. The CallSign should not match that of any other channel; but this rule is not enforced.
       * The StationID is what is used as the unique key to identify a station. Be sure that if you're creating new station IDs they do not conflict with existing ones.
       * The safest way to pick a station ID (if you need to at random) is to make it less than 10000 and ensure that no channel already exists with that station ID.
       * @param CallSign the 'Name' to assign to the new Channel
       * @param Name the 'Description' to assign to the new Channel
       * @param Network the 'Network' that the Channel is part of (can be "")
       * @param StationID the unique ID to give to this Channel
       * @return the newly created Channel object, if the station ID is already in use it will return the existing Channel object, but updated with the passed in values
       *
       * @declaration public Channel AddChannel(String CallSign, String Name, String Network, int StationID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int statID = getInt(stack);
        String net = getString(stack);
        String name = getString(stack);
        boolean[] didAdd = new boolean[1];
        didAdd[0] = false;
        Channel rv = Wizard.getInstance().addChannel(getString(stack), name, net, statID, 0, didAdd);
        if (didAdd[0])
          Wizard.getInstance().resetAirings(statID);
        return rv;
      }});
    rft.put(new PredefinedJEPFunction("Channel", "GetAllChannels")
    {
      /**
       * Returns all of the Channels that are defined in the system
       * @return all of the Channel objects that are defined in the system
       *
       * @declaration public Channel[] GetAllChannels();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getChannels();
      }});

    rft.put(new PredefinedJEPFunction("Show", "GetChannelLogoCount", 1, new String[] { "Channel" })
    {
      /**
       * Returns a count of logos for this channel. This will either be 0, 1 or 2. This does NOT include user-supplied channel logos.
       * Since all channel logos have all types, this does not require a type argument.
       * @param Channel the Channel object
       * @return the number of logos for the specified Channel (does NOT include user-supplied logos)
       * @since 7.1
       *
       * @declaration public int GetChannelLogoCount(Channel Channel);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Channel c = getChannel(stack);
        if (c == null)
          return new Integer(0);
        return new Integer(c.getLogoCount(Channel.LOGO_MED));
      }});
    rft.put(new PredefinedJEPFunction("Channel", "GetChannelLogoURL", -1, new String[] { "Channel", "Type", "Index", "Fallback" })
    {
      /**
       * Gets the URL of the logo image for the specified Channel if one exists
       * @param Channel the Channel object
       * @return the URL of the logo image for the Channel
       * @since 8.0
       *
       * @declaration public String GetChannelLogoURL(Channel Channel);
       */

      /**
       * Returns a Channel logo URL for the requested Channel if one exists. This can provide more detailed requests then the single argument GetChannelLogoURL call.
       * @param Channel the Channel object
       * @param Type the type of image, can be one of "Small", "Med" or "Large" (all logos have all sizes available, w/ the exception of user-supplied logos)
       * @param Index the 0-based index of the image to retrieve when multiple images exist for a given Type (there are only ever 0, 1 or 2 logos for a channel)
       * @param Fallback should be true if an alternate image is allowed (this enables checking user-supplied logos first, as well as falling back to the primary logo if a secondary one is requested but does not exist)
       * @return a URL corresponding to the requested image, or null if no image matching the requested parameters is found or an invalid Type is specified
       * @since 8.0
       *
       * @declaration public String GetChannelLogoURL(Channel Channel, String Type, int Index, boolean Fallback);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 1)
        {
          Channel c = getChannel(stack);
          if (c == null) return null;
          Object rv = EPG.getInstance().getLogoPath(c);
          return (rv instanceof String) ? rv : null;
        }
        else
        {
          boolean fallback = evalBool(stack.pop());
          int index = getInt(stack);
          String imageType = getString(stack);
          Channel c = getChannel(stack);
          if (c == null)
            return null;
          int imageTypeNum = 0;
          if ("Small".equalsIgnoreCase(imageType))
            imageTypeNum = Channel.LOGO_SMALL;
          else if ("Med".equalsIgnoreCase(imageType) || "Medium".equalsIgnoreCase(imageType))
            imageTypeNum = Channel.LOGO_MED;
          else if ("Large".equalsIgnoreCase(imageType))
            imageTypeNum = Channel.LOGO_LARGE;
          if (imageTypeNum == 0) return null;
          if (!fallback)
          {
            if (c.getLogoCount(imageTypeNum) <= index)
              return null;
            return c.getLogoUrl(index, imageTypeNum);
          }

          Object rv = EPG.getInstance().getLogoPath(c);
          if (rv instanceof String)
            return rv;

          int logoCount = c.getLogoCount(imageTypeNum);
          if (logoCount > index)
            return c.getLogoUrl(index, imageTypeNum);
          if (logoCount > 0)
            return c.getLogoUrl(0, imageTypeNum);
          return null;
        }
      }});

  }
}
