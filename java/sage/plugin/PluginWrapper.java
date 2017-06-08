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
package sage.plugin;

import sage.IOUtils;
import sage.Pooler;
import sage.Sage;
import sage.SageTVPlugin;

import java.io.DataInput;
import java.io.DataOutput;

/**
 * This class is used to hold all the additional meta information about a plugin that we may need to know at runtime for various reasons.
 * Its also the class that's used in the Plugin API.
 * @author Narflex
 */
public class PluginWrapper
{
  public static int PLUGIN_STATE_REPO = 0;
  public static int PLUGIN_STATE_ENABLED = 1;
  public static int PLUGIN_STATE_DISABLED = 2;
  public static int PLUGIN_STATE_FAILED = 3;
  // Passive is for plugins that we don't enable/disable but are already downloaded to the system
  public static int PLUGIN_STATE_PASSIVE = 4;
  /** Creates a new instance of PluginWrapper */
  // This constructor is used when it wraps an exiting plugin that's been created
  public PluginWrapper(SageTVPlugin inPluggy)
  {
    pluggy = inPluggy;
  }

  // This constructor is used when building a plugin object from what's in the repo XML file
  public PluginWrapper()
  {

  }

  public SageTVPlugin getRealPlugin() { return pluggy; }

  public String getName()
  {
    return name;
  }

  public void setName(String name)
  {
    this.name = name;
  }

  public String getId()
  {
    return id;
  }

  public void setId(String id)
  {
    this.id = id;
  }

  public String getDesc()
  {
    return desc;
  }

  public void setDesc(String desc)
  {
    this.desc = desc;
  }

  public String[] getScreenshotURLs()
  {
    return (screenshotURLs == null) ? Pooler.EMPTY_STRING_ARRAY : (String[]) screenshotURLs.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public void addScreenshotURL(String screenshotURL)
  {
    if (screenshotURLs == null)
      screenshotURLs = new java.util.ArrayList();
    screenshotURLs.add(screenshotURL);
  }

  public String[] getDemoVideoURLs()
  {
    return (demoVideoURLs == null) ? Pooler.EMPTY_STRING_ARRAY : (String[]) demoVideoURLs.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public void addDemoVideoURL(String demoVideoURL)
  {
    if (demoVideoURLs == null)
      demoVideoURLs = new java.util.ArrayList();
    demoVideoURLs.add(demoVideoURL);
  }

  public String[] getWebpages()
  {
    return (webpages == null) ? Pooler.EMPTY_STRING_ARRAY : (String[]) webpages.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public void addWebpage(String webpage)
  {
    if (webpages == null)
      webpages = new java.util.ArrayList();
    webpages.add(webpage);
  }

  public String[] getSTVImports()
  {
    return (stvImports == null) ? Pooler.EMPTY_STRING_ARRAY : (String[]) stvImports.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public void addSTVImport(String importAbsolutePath)
  {
    if (stvImports == null)
      stvImports = new java.util.ArrayList();
    stvImports.add(importAbsolutePath);
  }

  public String getAuthor()
  {
    return author;
  }

  public void setAuthor(String author)
  {
    this.author = author;
  }

  public long getCreateDate()
  {
    return createDate;
  }

  public void setCreateDate(long createDate)
  {
    this.createDate = createDate;
  }

  public long getModDate()
  {
    return modDate;
  }

  public void setModDate(long modDate)
  {
    this.modDate = modDate;
  }

  public void setInstallDate(long date)
  {
    installDate = date;
  }

  public long getInstallDate() { return installDate; }

  public String getVersion()
  {
    return version;
  }

  public void setVersion(String version)
  {
    this.version = version;
  }

  public String getResPath()
  {
    return resPath;
  }

  public void setResPath(String resPath)
  {
    this.resPath = resPath;
  }

  public String getImplClass()
  {
    return implClass;
  }

  public void setImplClass(String implClass)
  {
    this.implClass = implClass;
  }

  public boolean isDesktopOnly()
  {
    return desktopOnly;
  }

  public void setDesktopOnly(boolean desktopOnly)
  {
    this.desktopOnly = desktopOnly;
  }

  public boolean isServerOnly()
  {
    return serverOnly;
  }

  public void setServerOnly(boolean serverOnly)
  {
    this.serverOnly = serverOnly;
  }

  public boolean isWinOK()
  {
    return winOK;
  }

  public void setWinOK(boolean winOK)
  {
    this.winOK = winOK;
  }

  public boolean isLinuxOK()
  {
    return linuxOK;
  }

  public void setLinuxOK(boolean linuxOK)
  {
    this.linuxOK = linuxOK;
  }

  public boolean isMacOK()
  {
    return macOK;
  }

  public void setMacOK(boolean macOK)
  {
    this.macOK = macOK;
  }

  public Dependency[] getDependencies()
  {
    return (dependencies == null) ? new Dependency[0] : (Dependency[]) dependencies.toArray(new Dependency[0]);
  }

  public void addDependency(Dependency dependency)
  {
    if (dependencies == null)
      dependencies = new java.util.ArrayList();
    dependencies.add(dependency);
  }

  public Package[] getPackages()
  {
    return (packages == null) ? new Package[0] : (Package[]) packages.toArray(new Package[0]);
  }

  public void addPackage(Package newPackage)
  {
    if (packages == null)
      packages = new java.util.ArrayList();
    packages.add(newPackage);
  }

  public String getType() { return type; }

  public void setType(String t)
  {
    type = t;
  }

  public int getState() { return state; }
  public void setState(int x)
  {
    state = x;
  }

  public int getInstallIndex() { return installIndex; }
  public void setInstallIndex(int x)
  {
    installIndex = x;
  }

  public boolean isBeta() { return beta; }
  public void setBeta(boolean x) { beta = x; }
  public String getReleaseNotes() { return releaseNotes; }
  public void setReleaseNotes(String s) { releaseNotes = s; }

  public String toString()
  {
    return "SageTVPlugin[" + id + " state=" + (state == PLUGIN_STATE_REPO ? "Repository" :
      ((state == PLUGIN_STATE_DISABLED) ? "Disabled" : ((state == PLUGIN_STATE_ENABLED) ? "Enabled" : "Failed"))) +
      " name=" + name + " version=" + version + " type=" + type + " desc=" + desc + " author=" + author + " screenshots=" + screenshotURLs +
      " videos=" + demoVideoURLs + (createDate == 0 ? "" : (" created=" + Sage.dfStd(createDate))) +
      (modDate == 0 ? "" : (" modified=" + Sage.dfStd(modDate))) + (installDate == 0 ? "" : (" installed=" + Sage.dfStd(installDate))) +
      " resPath=" + resPath + " webpages=" + webpages + " implementation=" + implClass + " desktopOnly=" + desktopOnly + " serverOnly=" + serverOnly + " OS=" + (winOK ? "Win," : "") +
      (linuxOK ? "Linux," : "") + (macOK ? "Mac" : "") + " dependencies=" + dependencies + " packages=" + packages + "]";
  }

  public void clearRealPlugin()
  {
    pluggy = null;
  }

  // Returns true if this plugin is compatible with the specified STV
  public boolean isSTVCompatible(String stvName, String stvVersion)
  {
    boolean rv = true;
    for (int i = 0; dependencies != null && i < dependencies.size(); i++)
    {
      Dependency dep = (Dependency) dependencies.get(i);
      if ("STV".equals(dep.type))
      {
        if (stvName == null || !stvName.equalsIgnoreCase(dep.id))
          rv = false;
        else if (CorePluginManager.isVersionCompatible(stvVersion, dep.minVersion, dep.maxVersion))
          return true;
      }
    }
    return rv;
  }

  public void setRealPlugin(SageTVPlugin pluggy)
  {
    this.pluggy = pluggy;
  }

  // This is for when plugin information is sent over the network so we know where to execute the corresponding API calls for it
  public boolean isServerPluginInfo() { return serverPlug; }
  public void setServerPluginInfo(boolean x)
  {
    serverPlug = x;
  }

  public boolean isServerConfiguredPlugin()
  {
    return serverPlug && Sage.client && CorePluginManager.STANDARD_TYPE_PLUGIN.equals(type) && implClass.length() > 0 && !desktopOnly;
  }

  // This is for serializing this object to the c/s communication stream
  public void writeToObjectStream(DataOutput dataOut) throws java.io.IOException
  {
    dataOut.writeUTF(name == null ? "" : name);
    dataOut.writeUTF(id == null ? "" : id);
    dataOut.writeUTF(desc == null ? "" : desc);
    dataOut.writeUTF(type == null ? "" : type);
    dataOut.writeInt(screenshotURLs == null ? 0 : screenshotURLs.size());
    for (int i = 0; screenshotURLs != null && i < screenshotURLs.size(); i++)
      dataOut.writeUTF(screenshotURLs.get(i).toString());
    dataOut.writeInt(demoVideoURLs == null ? 0 : demoVideoURLs.size());
    for (int i = 0; demoVideoURLs != null && i < demoVideoURLs.size(); i++)
      dataOut.writeUTF(demoVideoURLs.get(i).toString());
    dataOut.writeUTF(author == null ? "" : author);
    dataOut.writeLong(createDate);
    dataOut.writeLong(modDate);
    dataOut.writeLong(installDate);
    dataOut.writeUTF(version == null ? "" : version);
    dataOut.writeUTF(resPath == null ? "" : resPath);
    dataOut.writeInt(webpages == null ? 0 : webpages.size());
    for (int i = 0; webpages != null && i < webpages.size(); i++)
      dataOut.writeUTF(webpages.get(i).toString());
    dataOut.writeUTF(implClass == null ? "" : implClass);
    dataOut.writeBoolean(desktopOnly);
    dataOut.writeBoolean(serverOnly);
    dataOut.writeBoolean(winOK);
    dataOut.writeBoolean(linuxOK);
    dataOut.writeBoolean(macOK);
    dataOut.writeInt(dependencies == null ? 0 : dependencies.size());
    for (int i = 0; dependencies != null && i < dependencies.size(); i++)
    {
      Dependency dep = (Dependency) dependencies.get(i);
      dataOut.writeUTF(dep.id == null ? "" : dep.id);
      dataOut.writeUTF(dep.minVersion == null ? "" : dep.minVersion);
      dataOut.writeUTF(dep.maxVersion == null ? "" : dep.maxVersion);
      dataOut.writeUTF(dep.type == null ? "" : dep.type);
    }
    dataOut.writeInt(packages == null ? 0 : packages.size());
    for (int i = 0; packages != null && i < packages.size(); i++)
    {
      Package pack = (Package) packages.get(i);
      dataOut.writeUTF(pack.type == null ? "" : pack.type);
      dataOut.writeUTF(pack.url == null ? "" : pack.url);
      dataOut.writeUTF(pack.rawMD5 == null ? "" : pack.rawMD5);
      dataOut.writeBoolean(pack.overwrite);
    }
    dataOut.writeInt(state);
    dataOut.writeInt(installIndex);
    dataOut.writeInt(stvImports == null ? 0 : stvImports.size());
    for (int i = 0; stvImports != null && i < stvImports.size(); i++)
      dataOut.writeUTF(stvImports.get(i).toString());
    dataOut.writeBoolean(beta);
    dataOut.writeUTF(releaseNotes == null ? "" : releaseNotes);
  }

  // This is for de-serializing this object from the c/s communication stream
  public static PluginWrapper buildFromObjectStream(DataInput dataIn) throws java.io.IOException
  {
    PluginWrapper rv = new PluginWrapper();
    rv.name = dataIn.readUTF();
    rv.id = dataIn.readUTF();
    rv.desc = dataIn.readUTF();
    rv.type = dataIn.readUTF();
    int x = dataIn.readInt();
    if (x > 0)
    {
      rv.screenshotURLs = new java.util.ArrayList();
      for (int i = 0; i < x; i++)
        rv.screenshotURLs.add(dataIn.readUTF());
    }
    x = dataIn.readInt();
    if (x > 0)
    {
      rv.demoVideoURLs = new java.util.ArrayList();
      for (int i = 0; i < x; i++)
        rv.demoVideoURLs.add(dataIn.readUTF());
    }
    rv.author = dataIn.readUTF();
    rv.createDate = dataIn.readLong();
    rv.modDate = dataIn.readLong();
    rv.installDate = dataIn.readLong();
    rv.version = dataIn.readUTF();
    rv.resPath = dataIn.readUTF();
    x = dataIn.readInt();
    if (x > 0)
    {
      rv.webpages = new java.util.ArrayList();
      for (int i = 0; i < x; i++)
        rv.webpages.add(dataIn.readUTF());
    }
    rv.implClass = dataIn.readUTF();
    rv.desktopOnly = dataIn.readBoolean();
    rv.serverOnly = dataIn.readBoolean();
    rv.winOK = dataIn.readBoolean();
    rv.linuxOK = dataIn.readBoolean();
    rv.macOK = dataIn.readBoolean();
    x = dataIn.readInt();
    if (x > 0)
    {
      rv.dependencies = new java.util.ArrayList();
      for (int i = 0; i < x; i++)
        rv.dependencies.add(new Dependency(dataIn.readUTF(), dataIn.readUTF(), dataIn.readUTF(), dataIn.readUTF()));
    }
    x = dataIn.readInt();
    if (x > 0)
    {
      rv.packages = new java.util.ArrayList();
      for (int i = 0; i < x; i++)
        rv.packages.add(new Package(dataIn.readUTF(), dataIn.readUTF(), dataIn.readUTF(), dataIn.readBoolean()));
    }
    rv.state = dataIn.readInt();
    rv.installIndex = dataIn.readInt();
    x = dataIn.readInt();
    if (x > 0)
    {
      rv.stvImports = new java.util.ArrayList();
      for (int i = 0; i < x; i++)
        rv.stvImports.add(dataIn.readUTF());
    }
    rv.beta = dataIn.readBoolean();
    rv.releaseNotes = dataIn.readUTF();
    if (Sage.client)
      rv.serverPlug = true;
    return rv;
  }

  private SageTVPlugin pluggy;
  private String name;
  private String id;
  private String desc;
  private String type;
  private java.util.ArrayList screenshotURLs;
  private java.util.ArrayList demoVideoURLs;
  private String author;
  private long createDate;
  private long modDate;
  private long installDate;
  private String version;
  private String resPath;
  private java.util.ArrayList webpages;
  private String implClass = "";
  private boolean desktopOnly;
  private boolean serverOnly;
  private boolean winOK = true;
  private boolean linuxOK = true;
  private boolean macOK = true;
  private java.util.ArrayList dependencies;
  private java.util.ArrayList packages;
  private int state;
  private int installIndex;
  private java.util.ArrayList stvImports;
  private boolean beta;
  private String releaseNotes;
  private boolean serverPlug;

  public static class Dependency
  {
    public Dependency(String id, String minVer, String maxVer, String type)
    {
      this.id = id;
      this.minVersion = minVer;
      this.maxVersion = maxVer;
      this.type = type;
    }
    public Dependency(){}
    public String id;
    public String minVersion;
    public String maxVersion;
    public String type;

    public String toString()
    {
      return "PluginDependency[" + id + ((minVersion != null && minVersion.length() > 0) ? (" minVer=" + minVersion) : "") +
          ((maxVersion != null && maxVersion.length() > 0) ? (" maxVer=" + maxVersion) : "") +
          " type=" + type + "]";
    }
  }

  public static class Package
  {
    public Package(String type, String url, String md5, boolean overwrite)
    {
      this.type = type;
      this.url = url;
      this.rawMD5 = md5;
      this.overwrite = overwrite;
    }
    public Package(){}
    public String type;
    public String url;
    // MD5 is private, and we'll use the getMD5() method that will check if the MD5 is a URL and if so, it will
    // get the MD5 from the URL and store it locally
    private String rawMD5;
    private String computedMD5;
    public boolean overwrite = true;

    /**
     * The rawMD5 field in the package can be empty, or, it can be a URL from which to download the MD5.
     * If the rawMD5 is null, then we use the URL field and we append .md5 which is standard Maven practice.
     *
     * Calling this method will result in getting the associated MD5 for the package, either by using the rawMD5
     * because it's valid, or by dynamically fetching the md5 from a remote URL.
     *
     * @return md5 for the package
     */
    public String getMD5()
    {
      // we have already figured out the MD5
      if (computedMD5 !=null) return computedMD5;

      // if it's a URL then get it
      if (rawMD5!=null && (rawMD5.startsWith("http")||rawMD5.startsWith("file:")))
      {
        computedMD5 = IOUtils.getUrlAsString(rawMD5);
      }
      else if (rawMD5==null || rawMD5.trim().length()==0)
      {
        // md5 is empty, so get it using the base url
        computedMD5 = IOUtils.getUrlAsString(url+".md5");
      }
      else
      {
        // we have a normal md5 string
        computedMD5 = rawMD5;
      }

      if (computedMD5 !=null) computedMD5 = computedMD5.trim();

      if (Sage.DBG && (computedMD5 ==null || computedMD5.length()==0))
      {
        System.out.println("Failed to get MD5 for Package: " + url + "; MD5: " + rawMD5);
      }

      return computedMD5;
    }

    /**
     * Sets the
     * @param md5
     */
    public void setRawMD5(String md5)
    {
      this.rawMD5=md5;
      this.computedMD5=null;
    }

    public String getRawMD5()
    {
      return rawMD5;
    }

    public String toString()
    {
      return "Package[type=" + type + " url=" + url + " md5=" + rawMD5 + " overwrite=" + overwrite + "]";
    }

  }
}
