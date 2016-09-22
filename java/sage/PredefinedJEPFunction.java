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
package sage;

public abstract class PredefinedJEPFunction extends sage.jep.function.PostfixMathCommand
    implements sage.jep.function.PostfixMathCommandI
{
  public static final java.util.Map categoryDescriptions;
  public static boolean API_PROFILING = false;
  static
  {
    categoryDescriptions = new java.util.HashMap();
    categoryDescriptions.put("Global", "System methods used for dealing with Sage specifics");
    categoryDescriptions.put("Configuration", "Configuration and properties for the whole system");
    categoryDescriptions.put("MediaPlayer", "Methods related to playback of media files");
    categoryDescriptions.put("Database", "Searching, sorting, filtering, grouping and related data methods");

    categoryDescriptions.put("CaptureDevice", "Represents a capture card or network encoder which is used to record TV.");
    categoryDescriptions.put("CaptureDeviceInput", "Represents an specific input on a CaptureDevice such as the TV Tuner, Composite or S-Video inputs");

    categoryDescriptions.put("Favorite", "Favorites are like Season Passes or Wishlists");

    categoryDescriptions.put("Airing",
        "An Airing represents a specific time and Channel that a television Show is broadcast on." +
        "Uniquely identified by its time-Channel overlap because only one thing can be broadcast on a Channel at any given time.");
    categoryDescriptions.put("Channel", "Represents a specific broadcast station such as WNBC, or HBO2");
    categoryDescriptions.put("Show", "A Show represents a unique television Show. " +
        "Uniquely identified by its ExternalID.");
    categoryDescriptions.put("Album", "Represents an Album of music.");

    categoryDescriptions.put("MediaFile",
        "Represents a file or sequence of files (if the recording is in pieces) that correspond to a Recorded Television Airing, Video File, Music Track, DVD Drive, Ripped DVD or Picture.");

    // Meta???? What are we going to do about it???? ManualRecord needs it for now

    categoryDescriptions.put("Playlist", "Represents a list of any kind of MediaFile, Airing, Album or Playlist to playback in sequence.");
    categoryDescriptions.put("Utility", "Utility methods");
    categoryDescriptions.put("Widget", "Widget reflection API");
    categoryDescriptions.put("Transcode", "Offline Transcoder API");
    categoryDescriptions.put("TVEditorial", "Editorial content about TV Shows");
    categoryDescriptions.put("SeriesInfo", "Information about television series");
    categoryDescriptions.put("SystemMessage", "Methods for interacting with the global SystemMessage queue");
    if (Catbert.ENABLE_LOCATOR_API)
      categoryDescriptions.put("Locator", "Methods for interacting with the Locator server for social networking");
    categoryDescriptions.put("MediaNode", "Node-based content abstraction hierarchy (VFS)");
    categoryDescriptions.put("Plugin", "API calls to be used for downloading, enabling and disabling of SageTV Plugins from the central repository. Also contains methods for configuring them and for interacting with the SageTV Plugin Event system.");
    categoryDescriptions.put("UserRecord", "API for custom data storage in the SageTV database. Uses simple String-based keys for records, and then name-value pairs within those records of type String.");
    categoryDescriptions.put("Security", "API for managing security profiles and their associated permissions");
  }
  public PredefinedJEPFunction(String inGroup, String inName)
  {
    this(inGroup, inName, 0, Pooler.EMPTY_STRING_ARRAY, false);
  }
  public PredefinedJEPFunction(String inGroup, String inName, int numArgs, String[] inParamDesc)
  {
    this(inGroup, inName, numArgs, inParamDesc, false);
  }
  public PredefinedJEPFunction(String inGroup, String inName, String[] inParamDesc)
  {
    this(inGroup, inName, (inParamDesc == null) ? 0 : inParamDesc.length, inParamDesc, false);
  }
  public PredefinedJEPFunction(String inGroup, String inName, boolean net)
  {
    this(inGroup, inName, 0, Pooler.EMPTY_STRING_ARRAY, net);
  }
  public PredefinedJEPFunction(String inGroup, String inName, String[] inParamDesc, boolean net)
  {
    this(inGroup, inName, (inParamDesc == null) ? 0 : inParamDesc.length, inParamDesc, net);
  }
  public PredefinedJEPFunction(String inGroup, String inName, int numArgs, String[] inParamDesc, boolean net)
  {
    networked = net;
    group = inGroup;
    methodName = inName;
    numberOfParameters = numArgs;
    paramDesc = inParamDesc;
    if (paramDesc == null)
      paramDesc = Pooler.EMPTY_STRING_ARRAY;
  }
  public final void run(Catbert.FastStack stack) throws sage.jep.ParseException
  {
    if (networked && Sage.client)
    {
      Object[] args = new Object[curNumberOfParameters];
      for (int i = args.length -1; i >= 0 ; i--)
        args[i] = stack.pop();
      SageTVConnection sn = NetworkClient.getSN();
      if (sn != null)
        stack.push(sn.requestAction(methodName, args));
      else
        stack.push(null); // to keep the stack pointer correct
    }
    else
    {
      if (API_PROFILING)
        numCalls++;
      try
      {
        stack.push(runSafely(stack));
      }
      catch (sage.jep.ParseException pe)
      {
        throw pe;
      }
      catch (Exception e)
      {
        e.printStackTrace(System.out);
        throw new sage.jep.ParseException("Error in method reflection of " + methodName + " of " + e, e);
      }
    }
  }
  public abstract Object runSafely(Catbert.FastStack stack) throws Exception;

  protected Airing getAir(Catbert.FastStack stack)
  {
    return getAirObj(stack.pop());
  }
  public static Airing getAirObj(Object o)
  {
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o instanceof MediaFile)
      return ((MediaFile) o).getContentAiring();
    else if (o instanceof Airing)
      return (Airing) o;
    else if (o instanceof java.io.File)
    {
      MediaFile mf = Wizard.getInstance().getFileForFilePath((java.io.File) o);
      return (mf == null) ? null : mf.getContentAiring();
    }
    else
      return null;
  }
  public static Show getShowObj(Object o)
  {
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o instanceof Show)
      return (Show) o;
    else if (o instanceof MediaFile)
      return ((MediaFile) o).getShow();
    else if (o instanceof Airing)
      return ((Airing) o).getShow();
    else if (o instanceof java.io.File)
    {
      MediaFile mf = Wizard.getInstance().getFileForFilePath((java.io.File) o);
      return (mf == null) ? null : mf.getShow();
    }
    else
      return null;
  }
  protected Show getShow(Catbert.FastStack stack)
  {
    return getShowObj(stack.pop());
  }
  public static Album getAlbumObj(Object o)
  {
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o instanceof Album)
      return (Album) o;
    MediaFile mf = getMediaFileObj(o);
    if (mf != null)
      return Wizard.getInstance().getCachedAlbumForMediaFile(mf);
    return null;
  }
  protected Album getAlbum(Catbert.FastStack stack)
  {
    return getAlbumObj(stack.pop());
  }
  protected Channel getChannelObj(Object o)
  {
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o instanceof Channel)
      return (Channel) o;
    else if (o instanceof MediaFile)
      return ((MediaFile) o).getContentAiring().getChannel();
    else if (o instanceof Airing)
      return ((Airing) o).getChannel();
    else if (o instanceof java.io.File)
    {
      MediaFile mf = Wizard.getInstance().getFileForFilePath((java.io.File) o);
      return (mf == null) ? null : mf.getContentAiring().getChannel();
    }
    else
      return null;
  }
  protected Channel getChannel(Catbert.FastStack stack)
  {
    return getChannelObj(stack.pop());
  }
  protected UserRecord getUserRecord(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o instanceof UserRecord)
      return (UserRecord) o;
    else
      return null;
  }
  protected static String getString(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getPrimaryLabel();
    if (o == null)
      return null;
    else
      return o.toString();
  }
  public static Person getPersonObj(Object o) {
    if (o instanceof Person)
      return (Person) o;
    else if (o == null)
      return null;
    else
    {
      String str = o.toString();
      if (str.length() == 0)
        return null;
      return Wizard.getInstance().getPersonForName(o.toString(), 0, false);
    }
  }
  protected static Person getPerson(Catbert.FastStack stack)
  {
    return getPersonObj(stack.pop());
  }
  protected int getMediaMask(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o == null)
      return 0;
    else
    {
      return DBObject.getMediaMaskFromString(o.toString());
    }
  }
  protected sage.vfs.MediaNode getMediaNode(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o instanceof sage.vfs.MediaNode)
      return (sage.vfs.MediaNode) o;
    else
      return null;
  }
  protected Widget getWidget(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o instanceof Widget)
      return (Widget) o;
    else if (o != null && stack.getUIMgr() != null)
    {
      // Try to lookup the widget by symbol
      return (Widget) (stack.getUIMgr().getModuleGroup().symbolMap.get(o.toString()));
    }
    else
      return null;
  }
  protected sage.plugin.PluginWrapper getPlugin(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o instanceof sage.plugin.PluginWrapper)
      return (sage.plugin.PluginWrapper) o;
    else
      return null;
  }
  protected ZPseudoComp getUIComp(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o instanceof ZPseudoComp)
      return (ZPseudoComp) o;
    else
      return null;
  }
  protected Playlist getPlaylist(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o instanceof Playlist)
      return (Playlist) o;
    else
      return null;
  }
  public static MediaFile getMediaFileObj(Object o)
  {
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o instanceof MediaFile)
      return (MediaFile) o;
    else if (o instanceof Airing)
      return Wizard.getInstance().getFileForAiring((Airing) o);
    else if (o instanceof java.io.File)
      return Wizard.getInstance().getFileForFilePath((java.io.File) o);
    else if (o instanceof Album)
    {
      Airing[] airs = ((Album) o).getAirings();
      if (airs != null && airs.length > 0)
        return Wizard.getInstance().getFileForAiring(airs[0]);
      else
        return null;
    }
    else
      return null;
  }
  protected MediaFile getMediaFile(Catbert.FastStack stack)
  {
    return getMediaFileObj(stack.pop());
  }
  public static String[] getStringListObj(Object o)
  {
    if (o == null)
      return Pooler.EMPTY_STRING_ARRAY;
    else if (o instanceof String[])
      return (String[]) o;
    else if (o instanceof Object[])
    {
      Object[] oa = (Object[]) o;
      String[] rv = new String[oa.length];
      for (int i = 0; i < rv.length; i++)
      {
        rv[i] = (oa[i] == null) ? null : oa[i].toString();
      }
      return rv;
    }
    else if (o instanceof java.util.Collection)
    {
      java.util.Collection c = (java.util.Collection) o;
      String[] rv = new String[c.size()];
      java.util.Iterator walker = c.iterator();
      int i = 0;
      while (walker.hasNext())
      {
        Object currVal = walker.next();
        if (currVal != null)
          rv[i++] = currVal.toString();
      }
      return rv;
    }
    else
    {
      java.util.StringTokenizer toker = new java.util.StringTokenizer(o.toString(), ";");
      String[] rv = new String[toker.countTokens()];
      for (int i = 0; i < rv.length; i++)
        rv[i] = toker.nextToken().trim();
      return rv;
    }
  }
  protected String[] getStringList(Catbert.FastStack stack)
  {
    return getStringListObj(stack.pop());
  }
  protected long getLong(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o == null)
      return 0;
    else if (o instanceof Number)
      return ((Number) o).longValue();
    else if (o instanceof java.util.Date)
      return ((java.util.Date) o).getTime();
    else
      return Long.parseLong(o.toString());
  }
  protected int getInt(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o == null)
      return 0;
    else if (o instanceof Number)
      return ((Number) o).intValue();
    else
      return Integer.parseInt(o.toString());
  }
  protected float getFloat(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o == null)
      return 0;
    else if (o instanceof Number)
      return ((Number) o).floatValue();
    else
      return Float.parseFloat(o.toString());
  }
  protected java.io.File getFile(Catbert.FastStack stack)
  {
    return getFileObj(stack.pop());
  }
  protected java.io.File getFileObj(Object o)
  {
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o == null)
      return null;
    else if (o instanceof java.io.File)
      return (java.io.File) o;
    else if (o instanceof MediaFile)
      return ((MediaFile)o).getFile(0);
    else if (o instanceof Airing)
    {
      MediaFile mf = Wizard.getInstance().getFileForAiring((Airing) o);
      if (mf != null)
        return ((MediaFile)o).getFile(0);
      else // this'll be a useless file path; but it'll probably help with debugging better for users than returning null here
        return new java.io.File(o.toString());
    }
    else
      return new java.io.File(o.toString());
  }
  protected java.util.regex.Pattern getRegex(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o == null)
      return null;
    else if (o instanceof java.util.regex.Pattern)
      return (java.util.regex.Pattern) o;
    else
      return java.util.regex.Pattern.compile(o.toString());
  }
  protected java.awt.Color getColor(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o == null)
      return null;
    else if (o instanceof java.awt.Color)
      return (java.awt.Color) o;
    else
      return java.awt.Color.decode(o.toString());
  }
  public static CaptureDevice getCapDevObj(Object o)
  {
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o instanceof CaptureDevice)
      return (CaptureDevice)o;
    else if (o instanceof CaptureDeviceInput)
      return ((CaptureDeviceInput)o).getCaptureDevice();
    else if (o == null)
      return null;
    else
    {
      CaptureDevice capDev = MMC.getInstance().getCaptureDeviceNamed(o.toString());
      if ( capDev != null )
        return capDev;
      else
      {
        CaptureDeviceInput capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(o.toString());
        if ( capDevInput != null )
          return capDevInput.getCaptureDevice();
        else
          return null;
      }
    }
  }
  protected CaptureDevice getCapDev(Catbert.FastStack stack)
  {
    return getCapDevObj(stack.pop());
  }
  public static CaptureDeviceInput getCapDevInputObj(Object o)
  {
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o instanceof CaptureDeviceInput)
      return (CaptureDeviceInput)o;
    else if (o == null)
      return null;
    else
      return MMC.getInstance().getCaptureDeviceInputNamed(o.toString());
  }
  protected CaptureDeviceInput getCapDevInput(Catbert.FastStack stack)
  {
    return getCapDevInputObj(stack.pop());
  }
  protected TVEditorial getEditorial(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o instanceof TVEditorial)
      return (TVEditorial) o;
    else if (o instanceof Show)
      return Wizard.getInstance().getEditorial((Show) o);
    else
      return null;
  }
  protected SeriesInfo getSeriesInfo(Catbert.FastStack stack)
  {
    return getSeriesInfoObj(stack.pop());
  }
  protected SeriesInfo getSeriesInfoObj(Object o)
  {
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o instanceof SeriesInfo)
      return (SeriesInfo) o;
    // See if we can go Show->Series
    Show s = getShowObj(o);
    return (s != null) ? s.getSeriesInfo() : null;
  }
  protected sage.msg.SystemMessage getSysMsg(Catbert.FastStack stack)
  {
    Object o = stack.pop();
    if (o instanceof sage.vfs.MediaNode)
      o = ((sage.vfs.MediaNode)o).getDataObject();
    if (o instanceof sage.msg.SystemMessage)
      return (sage.msg.SystemMessage) o;
    else
      return null;
  }
  protected boolean getBool(Catbert.FastStack stack)
  {
    return Catbert.evalBool(stack.pop());
  }
  protected boolean evalBool(Object o)
  {
    return Catbert.evalBool(o);
  }

  protected Object makeNetworkedCall(Catbert.FastStack stack) throws sage.jep.ParseException
  {
    Object[] args = new Object[curNumberOfParameters];
    for (int i = args.length -1; i >= 0 ; i--)
      args[i] = stack.pop();
    SageTVConnection sn = NetworkClient.getSN();
    if (sn != null)
      return sn.requestAction(methodName, args);
    else
      return null;
  }

  public String getMethodName() { return methodName; }
  public String getGroup() { return group; }
  public String[] getParamDesc() { return paramDesc; }
  private String methodName;
  private String group;
  private String[] paramDesc;
  private boolean networked;
  // For profiling purposes
  public int numCalls;
}
