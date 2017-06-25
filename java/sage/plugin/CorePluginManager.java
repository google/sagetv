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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import sage.EPG;
import sage.FileDownloader;
import sage.IOUtils;
import sage.MediaFile;
import sage.MetaImage;
import sage.Pooler;
import sage.Sage;
import sage.SageProperties;
import sage.SageTV;
import sage.SageTVPlugin;
import sage.SageTVPluginRegistry;
import sage.UIClient;
import sage.UIManager;
import sage.Version;

import javax.xml.parsers.SAXParserFactory;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * This class is responsible for managing the currently active plugins in the system
 * @author Narflex
 */
public class CorePluginManager implements Runnable
{
  private static final String REPOS_PROP_KEY = "sagetv_repos";
  private static final String REPO_URL_PROP_KEY = "url";
  private static final String REPO_MD5_PROP_KEY = "md5";
  private static final String REPO_LOCALFILE_PROP_KEY = "local";
  // The Repo Properties contains a set of Plugin Repositories that SageTV will periodically check for new
  // and updated SageTV Plugins.
  // The main key is sagetv_repos
  // Each Repo has a unique id, and contains the URL for the REPO, md5, and the local filename
  // sagetv_repos/xxxx/url=Plugin Repo URL
  // sagetv_repos/xxxx/md5=Plugin Repo MD5
  // sagetv_repos/xxxx/local=The Local Filename to use for this repo
  //
  // There are reserved REPO IDs, v7, and v9, with future reserved ids of v10, v11, etc.
  // sagetv_repos/v7/url=Plugin Repo URL
  //
  // When SageTV loads the REPO plugins, it will read v7, then v9, and then any alternate repos
  // listed in the sagetv_additional_repo_list, which is a comma separated list of alternate repos and
  // the order in which to read them.
  // Finally, SageTVPluginsDev.xml is read last, as means to allow devs to forcefully overrade the plugins
  // listed in the plugin system.

  // Comma separated list of alternate repos.  Should not contain, v7, v9, etc, but can contain additional
  // configured repos.  Configured repos would be configured under the sagetv_repos parent key.
  private static final String ADDITIONAL_REPOS_PROP_KEY = "sagetv_additional_repo_list";

  private static final String PLUGIN_PROP_KEY = "sagetv_core_plugins";
  public static final String STANDARD_TYPE_PLUGIN = "Standard";
  public static final String LIBRARY_TYPE_PLUGIN = "Library";
  public static final String STV_TYPE_PLUGIN = "STV";
  public static final String STVI_TYPE_PLUGIN = "STVI";
  public static final String THEME_TYPE_PLUGIN = "Theme";
  public static final String IMAGES_TYPE_PLUGIN = "Images";
  // The properties file contains all the information about what plugins are loaded and how we handle them.
  // Some information is not stored, such as the links to screenshots, videos and website. But those can be correlated against
  // what's in the repository XML file. If an installed plugin is no longer in the repository then that information would be lost.
  // We'll also keep local versions of the plugin install packages at SageTV/PluginInstallers/PluginName/PluginVersion/
  // for plugins that can be enabled/disabled. For other plugin types; we'll download to the same location....but then delete the files after
  // installation.
  // The main key for this is:
  // sagetv_core_plugins
  // Underneath there; each key corresponds to the Identifier for the corresponding plugin.
  // We then have additional keys underneath that for plugin-specific details:
  // sagetv_core_plugins/xxxx/name=Pretty Name for the Plugin
  // sagetv_core_plugins/xxxx/version=Plugin Version
  // sagetv_core_plugins/xxxx/date=Plugin Modification Date
  // sagetv_core_plugins/xxxx/installdate=Date the Plugin was Installed
  // sagetv_core_plugins/xxxx/desc=Description for this plugin
  // sagetv_core_plugins/xxxx/author=Author of the plugin
  // sagetv_core_plugins/xxxx/respath=Resource Path for Plugin (may not be defined)
  // sagetv_core_plugins/xxxx/type=Type of Plugin (only some Types can be disabled)
  // sagetv_core_plugins/xxxx/impl=Fully qualified class name of the class that implements SageTVPlugin (may not be defined)
  // sagetv_core_plugins/xxxx/desktop=Whether or not this is a desktop-only plugin
  // sagetv_core_plugins/xxxx/dependency/yyyy/minversion=Multiple items like this can exist; each one refers to a specific plugin dependency and its version requirements
  // sagetv_core_plugins/xxxx/dependency/yyyy/maxversion=Multiple items like this can exist; each one refers to a specific plugin dependency and its version requirements
  // sagetv_core_plugins/xxxx/dependency/yyyy/type=Multiple items like this can exist; each one refers to a specific plugin dependency and what type it is (STV or Plugin)
  // sagetv_core_plugins/xxxx/enabled=T/F indicating if this plugin is enabled or not
  // sagetv_core_plugins/xxxx/installindex=Integer UID value corresponding to this plugins install order relative to all other plugins
  // sagetv_core_plugins/xxxx/stvimports=semicolon delimited list of all the STVIs which go along with this plugin; these are absolute paths
  // sagetv_core_plugins/xxxx/beta=T/F indicating if its a beta version of a plugin
  // sagetv_core_plugins/xxxx/relnotes=release notes for this plugin
  // sagetv_core_plugins/xxxx/webpages=pipe delimited list of websites for the plugin
  // sagetv_core_plugins/xxxx/demovideos=pipe delimited list of URLs for videos
  // sagetv_core_plugins/xxxx/screenshots=pipe delimited list of URLs for screenshots
  // sagetv_core_plugins/xxxx/reset=boolean which indicates if this plugin configuration needs to be reset, happens when it is reset while being offline
  // sagetv_core_plugins/xxxx/last_upgrade_msg=version number of the last time we notified them an upgrade is available for this plugin
  // sagetv_core_plugins/xxxx/server=Whether or not this is a server-only plugin

  private static CorePluginManager chosenOne;
  private static Object singletonLock = new Object();
  public static CorePluginManager getInstance()
  {
    if (chosenOne == null)
    {
      synchronized (singletonLock)
      {
        if (chosenOne == null)
          chosenOne = new CorePluginManager();
      }
    }
    return chosenOne;
  }

  /** return the repo's value for key **/
  static String repoValue(String id, String key, String defValue)
  {
    return getServerProperty(REPOS_PROP_KEY + "/" + id + "/" + key, defValue);
  }

  /** Creates a new instance of CorePluginManager */
  private CorePluginManager()
  {
    myPlugins = new java.util.HashMap();
    if (!Sage.client || Sage.isNonLocalClient())
      globalInstallCount = Sage.getInt("plugin_install_counter", 0);

    pseudoClient = Sage.client && !Sage.isNonLocalClient();

    loadMyPlugins();
    if (Sage.DBG) System.out.println("LOADED PLUGINS=" + myPlugins.values());

    // Only SageTV servers host plugin repository information...but nonlocal clients
    // also need to check for plugin updates and post a system message about it.
    if (!Sage.client || Sage.isNonLocalClient())
    {
      Thread t = new Thread(this, "PluginRepoUpdater");
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
    }

    // Servers and true clients do their own file tracking, the pseudoclient does not
    if (!Sage.client || Sage.isNonLocalClient())
    {
      fileTracker = new SageProperties(false);
      fileTracker.setupPrefs("filetracker.properties", null);
      fileTrackerFilekeys = new java.util.HashMap();
      String[] filekeys = fileTracker.keys("filekeys");
      for (int i = 0; i < filekeys.length; i++)
      {
        // This sets up a reverse map so we can go to filename->filekey
        fileTrackerFilekeys.put(fileTracker.get("filekeys/" + filekeys[i], ""), filekeys[i]);
        try
        {
          fileKeyCounter = Math.max(fileKeyCounter, Integer.parseInt(filekeys[i]));
        }
        catch (NumberFormatException nfe)
        {
          if (Sage.DBG) System.out.println("ERROR invalid numeric constant in filetracker.properties for filekey of: " + filekeys[i]);
        }
      }

      java.util.Iterator walker = myPlugins.values().iterator();
      while (walker.hasNext())
      {
        verifyPluginInstall((PluginWrapper) walker.next());
      }
    }
  }

  public void refreshLoadedPlugins()
  {
    // This is ONLY for pseudo-client plugin refreshing!
    if (!Sage.client || Sage.isNonLocalClient())
      return;
    loadMyPlugins();
  }

  // This loads all the plugin objects from the .properties file that have already been configured/installed
  private void loadMyPlugins()
  {
    String[] pluginKeys;
    if (pseudoClient)
    {
      // The plugin installation information is stored in the local server; the only thing we know is which ones are enabled/disabled...but due
      // to synchronization issues we must first check the server for the list of plugins and then see if we have any of them locally enabled as well
      pluginKeys = getServerPropertyChildrenNames(PLUGIN_PROP_KEY);
    }
    else
    {
      pluginKeys = Sage.childrenNames(PLUGIN_PROP_KEY);
    }
    for (int i = 0; i < pluginKeys.length; i++)
    {
      loadPlugin(pluginKeys[i]);
    }
  }
  private void loadPlugin(String pluginKey)
  {
    if (myPlugins.containsKey(pluginKey))
      return;
    SageTVPlugin pluggy = null;
    String propBase = PLUGIN_PROP_KEY + "/" + pluginKey + "/";
    String plugType = pseudoClient ? getServerProperty(propBase + "type", "") : Sage.get(propBase + "type", "");
    String version = pseudoClient ? getServerProperty(propBase + "version", "") : Sage.get(propBase + "version", "");
    String author = pseudoClient ? getServerProperty(propBase + "author", "") : Sage.get(propBase + "author", "");
    String pluginClassName = pseudoClient ? getServerProperty(propBase + "impl", "") : Sage.get(propBase + "impl", "");
    boolean desktopOnly = pseudoClient ? getServerBoolProperty(propBase + "desktop", false) : Sage.getBoolean(propBase + "desktop", false);
    boolean serverOnly = pseudoClient ? getServerBoolProperty(propBase + "server", false) : Sage.getBoolean(propBase + "server", false);
    // The pseudo-client only handles standard type plugins that are desktop only; but we also track STVI plugins as well
    // so we also put those in our plugin map too
    if (pseudoClient && !STVI_TYPE_PLUGIN.equals(plugType) && (!desktopOnly || !STANDARD_TYPE_PLUGIN.equals(plugType) || pluginClassName.length() == 0))
      return;
    if (Sage.client && serverOnly)
      return;
    PluginWrapper wrap = new PluginWrapper();
    if (Sage.DBG) System.out.println("CorePluginManager is checking plugin: " + pluginKey + " type: " + plugType +
        " version: " + version + " by: " + author);
    wrap.setId(pluginKey);
    wrap.setVersion(version);
    wrap.setInstallDate(pseudoClient ? getServerLongProperty(propBase + "installdate", 0) : Sage.getLong(propBase + "installdate", 0));
    wrap.setModDate(pseudoClient ? getServerLongProperty(propBase + "date", 0) : Sage.getLong(propBase + "date", 0));
    wrap.setName(pseudoClient ? getServerProperty(propBase + "name", "") : Sage.get(propBase + "name", ""));
    if (!pseudoClient && (wrap.getName() == null || wrap.getName().length() == 0))
    {
      if (Sage.DBG) System.out.println("Detected invalid plugin information for pluginID " + pluginKey + " deleting its information from the properties file.");
      Sage.removeNode(propBase);
      return;
    }
    wrap.setDesc(pseudoClient ? getServerProperty(propBase + "desc", "") : Sage.get(propBase + "desc", ""));
    wrap.setAuthor(author);
    wrap.setReleaseNotes(pseudoClient ? getServerProperty(propBase + "relnotes", "") : Sage.get(propBase + "relnotes", ""));
    wrap.setBeta(pseudoClient ? getServerBoolProperty(propBase + "beta", false) : Sage.getBoolean(propBase + "beta", false));
    wrap.setResPath(pseudoClient ? getServerProperty(propBase + "respath", "") : Sage.get(propBase + "respath", ""));
    wrap.setType(plugType);
    wrap.setImplClass(pseudoClient ? getServerProperty(propBase + "impl", "") : Sage.get(propBase + "impl", ""));
    wrap.setDesktopOnly(desktopOnly);
    wrap.setServerOnly(serverOnly);
    wrap.setInstallIndex(pseudoClient ? getServerIntProperty(propBase + "installindex", 0) : Sage.getInt(propBase + "installindex", 0));
    if (!pseudoClient)
      globalInstallCount = Math.max(globalInstallCount, wrap.getInstallIndex());
    String[] depends = pseudoClient ? getServerPropertyChildrenNames(propBase + "dependency") : Sage.childrenNames(propBase + "dependency");
    for (int j = 0; j < depends.length; j++)
    {
      String dependName = depends[j];
      String depPropBase = propBase + "dependency/" + dependName + "/";
      String minVer = pseudoClient ? getServerProperty(depPropBase + "minversion", "") : Sage.get(depPropBase + "minversion", "");
      String maxVer = pseudoClient ? getServerProperty(depPropBase + "maxversion", "") : Sage.get(depPropBase + "maxversion", "");
      String type = pseudoClient ? getServerProperty(depPropBase + "type", "") : Sage.get(depPropBase + "type", "");
      wrap.addDependency(new PluginWrapper.Dependency(dependName, minVer, maxVer, type));
    }
    String imports = pseudoClient ? getServerProperty(propBase + "stvimports", "") : Sage.get(propBase + "stvimports", "");
    if (imports.length() > 0)
    {
      java.util.StringTokenizer toker = new java.util.StringTokenizer(imports, ";");
      while (toker.hasMoreTokens())
        wrap.addSTVImport(toker.nextToken());
    }
    String demovideos = pseudoClient ? getServerProperty(propBase + "demovideos", "") : Sage.get(propBase + "demovideos", "");
    if (demovideos.length() > 0)
    {
      java.util.StringTokenizer toker = new java.util.StringTokenizer(demovideos, "|");
      while (toker.hasMoreTokens())
        wrap.addDemoVideoURL(toker.nextToken());
    }
    String screenshots = pseudoClient ? getServerProperty(propBase + "screenshots", "") : Sage.get(propBase + "screenshots", "");
    if (screenshots.length() > 0)
    {
      java.util.StringTokenizer toker = new java.util.StringTokenizer(screenshots, "|");
      while (toker.hasMoreTokens())
        wrap.addScreenshotURL(toker.nextToken());
    }
    String webpages = pseudoClient ? getServerProperty(propBase + "webpages", "") : Sage.get(propBase + "webpages", "");
    if (webpages.length() > 0)
    {
      java.util.StringTokenizer toker = new java.util.StringTokenizer(webpages, "|");
      while (toker.hasMoreTokens())
        wrap.addWebpage(toker.nextToken());
    }
    myPlugins.put(pluginKey, wrap);
    if (STANDARD_TYPE_PLUGIN.equalsIgnoreCase(plugType) && pluginClassName.length() > 0)
    {
      // Even with the pseudo-client, enabled status is checked locally in the properties file
      boolean plugEnabled = Sage.getBoolean(propBase + "enabled", pseudoClient ? getServerBoolProperty(propBase + "enabled", false) : false);
      if (plugEnabled)
      {
        // Verify the Core & JVM dependencies for this plugin
        PluginWrapper.Dependency[] deps = wrap.getDependencies();
        boolean badDeps = false;
        for (int i = 0; i < deps.length; i++)
        {
          if ("Core".equals(deps[i].type))
          {
            if (!isVersionCompatible(Version.VERSION, deps[i].minVersion, deps[i].maxVersion))
            {
              if (Sage.DBG) System.out.println("Cannot load plugin " + wrap.getName() + " because it's not compatible with this version of the Core!");
              badDeps = true;
              break;
            }
          }
          else if ("JVM".equals(deps[i].type))
          {
            if (!isVersionCompatible(System.getProperty("java.specification.version"), deps[i].minVersion, deps[i].maxVersion))
            {
              if (Sage.DBG) System.out.println("Cannot enable plugin " + wrap.getName() + " because it's not compatible with this version of Java!");
              badDeps = true;
              break;
            }
          }
        }
        if (!badDeps)
        {
          if (Sage.DBG) System.out.println("Plugin " + pluginKey + " is enabled, try to load it from class: " + pluginClassName);
          pluggy = dynamicLoadStandardPlugin(pluginClassName, desktopOnly, Sage.getBoolean(propBase + "reset", false));
        }
        if (pluggy != null)
        {
          wrap.setRealPlugin(pluggy);
          wrap.setState(PluginWrapper.PLUGIN_STATE_ENABLED);
        }
        else
        {
          if (Sage.DBG) System.out.println("Loading of plugin " + pluginKey + " failed!");
          if (Sage.isHeadless() && desktopOnly)
          {
            if (Sage.DBG) System.out.println("Mark plugin as disabled since it only failed since its a desktop only plugin and this is a non-desktop environment");
            wrap.setState(PluginWrapper.PLUGIN_STATE_DISABLED);
          }
          else
            wrap.setState(PluginWrapper.PLUGIN_STATE_FAILED);
        }
      }
      else
      {
        if (Sage.DBG) System.out.println("Plugin " + pluginKey + " is disabled; skipping it");
        wrap.setState(PluginWrapper.PLUGIN_STATE_DISABLED);
      }
    }
    else
    {
      wrap.setState(PluginWrapper.PLUGIN_STATE_PASSIVE);
    }
  }

  private SageTVPlugin dynamicLoadStandardPlugin(String pluginClassName, boolean desktopOnly, boolean reset)
  {
    if (needRestart) return null;
    SageTVPlugin pluggy = null;
    Class pluginClass = null;
    // First load the class object
    try
    {
      pluginClass = Class.forName(pluginClassName, true, Sage.extClassLoader);
    }
    catch (Throwable t)
    {
      if (Sage.DBG) System.out.println("ERROR loading plugin class " + pluginClassName + " of:" + t);
      if (Sage.DBG) t.printStackTrace();
    }
    // If we need to restart then don't load the plugin for real; or if it disobeys the desktop requirement
    if (pluginClass != null && (!Sage.isHeadless() || !desktopOnly))
    {
      // Check for implementation of the sage.SageTVPlugin interface
      if (!SageTVPlugin.class.isAssignableFrom(pluginClass))
      {
        if (Sage.DBG) System.out.println("ERROR plugin class does not implement the sage.SageTVPlugin interface so it will not be loaded");
      }
      else
      {
        // Now resolve the constructor, try the two arg first, then the one arg
        java.lang.reflect.Constructor pluginConst = null;
        boolean twoArg = true;
        try
        {
          pluginConst = pluginClass.getDeclaredConstructor(new Class[] { SageTVPluginRegistry.class, Boolean.TYPE });
        }
        catch (NoSuchMethodException nsme)
        {
          twoArg = false;
          try
          {
            pluginConst = pluginClass.getDeclaredConstructor(new Class[] { SageTVPluginRegistry.class });
          }
          catch (NoSuchMethodException nsme2)
          {
            if (Sage.DBG) System.out.println("ERROR cannot load plugin because it does not have a single argument constructor that takes a sage.SageTVPluginRegistry object, or a 2 arg constructor that takes (sage.SageTVPluginRegistry, boolean) args!!");
          }
        }
        if (pluginConst != null)
        {
          try
          {
            if (twoArg)
            {
              if (reset && Sage.DBG)
                System.out.println("Invoking plugin constructor with reset enabled");
              pluggy = (SageTVPlugin) pluginConst.newInstance(new Object[] { PluginEventManager.getInstance(), reset ? Boolean.TRUE : Boolean.FALSE });
              reset = false;
            }
            else
            {
              pluggy = (SageTVPlugin) pluginConst.newInstance(new Object[] { PluginEventManager.getInstance() });
              if (reset)
              {
                if (Sage.DBG) System.out.println("Resetting plugin configuration after instantation");
                pluggy.resetConfig();
              }
            }
          }
          catch (Throwable t2)
          {
            if (Sage.DBG)
            {
              System.out.println("ERROR cannot instantiate plugin object due to: " + t2);
              Sage.printStackTrace(t2);
              if (t2 instanceof java.lang.reflect.InvocationTargetException)
              {
                Throwable nested = ((java.lang.reflect.InvocationTargetException) t2).getTargetException();
                System.out.println("Nested exception: " + nested);
                Sage.printStackTrace(nested);
              }
            }
          }
        }
      }
    }
    return pluggy;
  }

  // This loads all of the plugin definitions from a repository XML file
  private synchronized void loadRepoPlugins(List<File> repoXmlFiles)
  {
    // Use a SAX parser for this so we can reuse this code on the embedded systems
    if (repoHandler == null)
      repoHandler = new RepoSAXHandler();
    if (devRepoHandler == null && isDevMode())
      devRepoHandler = new DevRepoSAXHandler();
    if (allRepoPluginsTemp == null)
      allRepoPluginsTemp = new java.util.HashMap();
    else
      allRepoPluginsTemp.clear();
    if (latestRepoPluginsTemp == null)
      latestRepoPluginsTemp = new java.util.HashMap();
    else
      latestRepoPluginsTemp.clear();

    for (File repoXmlFile: repoXmlFiles) {
      if (repoXmlFile.exists()) {
        processRepoXmlFile(repoXmlFile);
      }
    }

    allRepoPlugins = allRepoPluginsTemp;
    allRepoPluginsTemp = null;
    latestRepoPlugins = latestRepoPluginsTemp;
    latestRepoPluginsTemp = null;
  }

  private void processRepoXmlFile(java.io.File repoXmlFile)
  {
    java.io.InputStream inStream = null;
    try
    {
      if (Sage.DBG) System.out.println("Analyzing plugin repository XML file: " + repoXmlFile);
      inStream = new java.io.BufferedInputStream(new java.io.FileInputStream(repoXmlFile));
      factory.setValidating(false);
      if (devRepoHandler!=null && devRepoHandler.canHandleFile(repoXmlFile))
        factory.newSAXParser().parse(inStream, devRepoHandler);
      else
        factory.newSAXParser().parse(inStream, repoHandler);
      if (Sage.DBG) System.out.println("Done processing plugin repository XML file["+repoXmlFile+"] " + " repositoryVersion=" + repoVer);
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR parsing SageTVPlugin Repository XML file["+repoXmlFile+"] of: " + e);
      if (Sage.DBG) e.printStackTrace();
    }
    if (inStream != null)
    {
      try
      {
        inStream.close();
      }
      catch (Exception e){}
    }
  }

  // These can be enabled, disabled, failed or passive...it's all the plugins we've installed
  public PluginWrapper[] getMyPlugins()
  {
    return (PluginWrapper[]) myPlugins.values().toArray(new PluginWrapper[0]);
  }

  public PluginWrapper[] getLatestRepoPlugins()
  {
    return (PluginWrapper[]) latestRepoPlugins.values().toArray(new PluginWrapper[0]);
  }

  public PluginWrapper[] getAllRepoVersions(String pluginID)
  {
    java.util.ArrayList verList = (java.util.ArrayList) allRepoPlugins.get(pluginID);
    if (verList == null)
      return new PluginWrapper[0];
    else
      return (PluginWrapper[]) verList.toArray(new PluginWrapper[0]);
  }

  private PluginWrapper getLatestRepoPluginInVersionRange(String pluginID, String minVer, String maxVer)
  {
    java.util.ArrayList verList;
    if (Sage.client)
    {
      try
      {
        Object[] plugs = (Object[]) SageTV.api("GetAllPluginVersions", new Object[] { pluginID });
        verList = new java.util.ArrayList(java.util.Arrays.asList(plugs));
      }
      catch (Exception e)
      {
        if (Sage.DBG) System.out.println("ERROR retreiving plugin repository information from local server of: " + e);
        return null;
      }
    }
    else
      verList = (java.util.ArrayList) allRepoPlugins.get(pluginID);
    if (verList == null)
      return null;
    else
    {
      PluginWrapper rv = null;
      for (int i = 0; i < verList.size(); i++)
      {
        PluginWrapper testMe = (PluginWrapper) verList.get(i);
        if (isVersionCompatible(testMe.getVersion(), minVer, maxVer))
        {
          if (rv == null || compareVersions(testMe.getVersion(), rv.getVersion()) > 0)
            rv = testMe;
        }
      }
      return rv;
    }
  }

  public String[] getEnabledSTVImports(UIManager uiMgr)
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    java.util.Vector pluginsToCheck = new java.util.Vector(java.util.Arrays.asList(getMyPlugins()));
    java.util.Set failedPluginIDs = new java.util.HashSet();
    // We need to go through ALL of the plugins here
    while (!pluginsToCheck.isEmpty())
    {
      PluginWrapper wrap = (PluginWrapper) pluginsToCheck.remove(0);
      // Check if it has any dependencies; if so, then make sure they have their STVI imported first. If any of them failed to start; then
      // this plugin should be marked as failed as well.
      PluginWrapper.Dependency[] deps = wrap.getDependencies();
      boolean depsDone = true;
      boolean depsFailed = false;
      for (int i = 0; i < deps.length; i++)
      {
        if ("Plugin".equals(deps[i].type))
        {
          PluginWrapper depWrap = (PluginWrapper) myPlugins.get(deps[i].id);
          if (depWrap == null)
          {
            if (pseudoClient) // Can't check these properly
              continue;
            if (Sage.DBG) System.out.println("Failed check of plugin " + wrap + " because its dependency of " + deps[i].id + " does not exist!");
            depsFailed = true;
            break;
          }
          if (failedPluginIDs.contains(deps[i].id) || depWrap.getState() == PluginWrapper.PLUGIN_STATE_FAILED || depWrap.getState() == PluginWrapper.PLUGIN_STATE_DISABLED)
          {
            depsFailed = true;
            if (Sage.DBG) System.out.println("Failed check of plugin " + wrap + " because its dependency of " + deps[i].id + " failed to start/load!");
            break;
          }
          if (STVI_TYPE_PLUGIN.equals(depWrap.getType()) && !isPluginEnabled(depWrap, uiMgr))
          {
            depsFailed = true;
            if (Sage.DBG) System.out.println("Failed check of plugin " + wrap + " because its dependency of " + deps[i].id + " is disabled!");
            break;
          }
          int depIdx = pluginsToCheck.indexOf(depWrap);
          if (depIdx >= 0)
          {
            // Dependency is not imported yet; move us after it in the list
            depsDone = false;
            pluginsToCheck.insertElementAt(wrap, depIdx + 1);
            if (Sage.DBG) System.out.println("Moving loading of plugin STVI " + wrap.getId() + " to after " + depWrap.getId() + " because of its dependency on it.");
            break;
          }
        }
      }
      if (depsFailed)
      {
        failedPluginIDs.add(wrap.getId());
        if (STVI_TYPE_PLUGIN.equals(wrap.getType()))
        {
          if (Sage.DBG) System.out.println("Disabling STVI plugin " + wrap.getName() + " because a dependency is not satisfied!");
          disablePlugin(wrap.getId(), uiMgr);
        }
      }
      if (depsFailed || !depsDone || !STVI_TYPE_PLUGIN.equals(wrap.getType()))
        continue;
      if (isPluginEnabled(wrap, uiMgr))
      {
        String[] imports = wrap.getSTVImports();
        for (int j = 0; j < imports.length; j++)
          rv.add(imports[j]);
      }
    }

    return (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
  }

  public boolean isPluginInstalled(String pluginID)
  {
    return myPlugins.containsKey(pluginID);
  }

  public boolean isPluginInstalledSameVersion(PluginWrapper pluggy)
  {
    PluginWrapper currPlug = (PluginWrapper) myPlugins.get(pluggy.getId());
    if (currPlug == null)
      return false;
    return currPlug.getVersion().equals(pluggy.getVersion());
  }

  // This will resolve the value as UI-specific if it is an STVI
  public boolean isPluginEnabled(String pluginID, UIManager uiMgr)
  {
    return isPluginEnabled((PluginWrapper) myPlugins.get(pluginID), uiMgr);
  }
  private boolean isPluginEnabled(PluginWrapper wrap, UIManager uiMgr)
  {
    if (wrap != null)
    {
      if (wrap.getState() == PluginWrapper.PLUGIN_STATE_PASSIVE)
      {
        if (uiMgr != null && STVI_TYPE_PLUGIN.equals(wrap.getType()))
        {
          return uiMgr.getBoolean(PLUGIN_PROP_KEY + "/" + wrap.getId() + "/enabled",
              pseudoClient ? getServerBoolProperty(PLUGIN_PROP_KEY + "/" + wrap.getId() + "/enabled", false) : Sage.getBoolean(PLUGIN_PROP_KEY + "/" + wrap.getId() + "/enabled", false)) &&
              wrap.isSTVCompatible(uiMgr.getSTVName(), uiMgr.getSTVVersion());
        }
        else
          return false;
      }
      else if (wrap.getState() == PluginWrapper.PLUGIN_STATE_FAILED || wrap.getState() == PluginWrapper.PLUGIN_STATE_ENABLED)
        return true;
    }
    return false;
  }

  public boolean isPluginFailed(String pluginID)
  {
    PluginWrapper wrap = (PluginWrapper) myPlugins.get(pluginID);
    return wrap != null && wrap.getState() == PluginWrapper.PLUGIN_STATE_FAILED;
  }

  // This is used for doing regular update checks to see if we need a new repository file
  public void run()
  {
    refreshAvailablePlugins();
    //if (Sage.DBG) System.out.println("REPOSITORY PLUGINS=" + allRepoPlugins.values());
    while (true)
    {
      // Wait four hours
      try{Thread.sleep(4*60*60*1000);}catch(Exception e){}
      if (Sage.DBG) System.out.println("Doing routine update check for a new SageTVPlugins.xml file...");
      refreshAvailablePlugins();
    }
  }

  /**
   * For the given Plugin URL, verify if the local Plugin File needs updating based on doing a MD5 check
   * against the md5 URL.  If the plugin repo needs updating, then a new copy is downloaded and saved to
   * the localFileName.
   * @param repoUrl
   * @param md5Url
   * @param localFileName
   * @return
     */
  boolean updateLocalPluginRepoFile(String repoUrl, String md5Url, String localFileName)
  {
    java.io.File localPlugRepo = getLocalPluginFile(localFileName);
    if (!Sage.getBoolean("disable_plugin_repository_updates", false) || !localPlugRepo.isFile())
    {
      boolean localOK = false;
      if (localPlugRepo.isFile()) {
        if (md5Url != null) {
          String localMD5 = IOUtils.calcMD5(localPlugRepo);
          if (Sage.DBG) System.out.println("Checking to see if the plugin repository file " + localPlugRepo + " needs to be updated using MD5 URL: " + md5Url);
          java.io.InputStream urlStream = null;
          try {
            java.net.URL verUrl = new java.net.URL(md5Url);
            urlStream = verUrl.openStream();
            StringBuffer sb = new StringBuffer();
            byte[] buf = new byte[128];
            int numRead = urlStream.read(buf);
            while (numRead != -1) {
              sb.append(new String(buf, 0, numRead, Sage.BYTE_CHARSET));
              numRead = urlStream.read(buf);
            }
            String remoteMD5 = sb.toString().trim();
            if (remoteMD5.equalsIgnoreCase(localMD5)) {
              localOK = true;
              if (Sage.DBG)
                System.out.println("Local plugin repository file's checksum matches server's, no need to download a new one for " + localPlugRepo);
            }
          } catch (Exception e) {
            if (Sage.DBG) System.out.println("ERROR trying to retrieve the "+ localMD5 +" file of:" + e);
          } finally {
            if (urlStream != null) {
              try {
                urlStream.close();
              } catch (Exception e) {
              }
              urlStream = null;
            }
          }
        }
      }
      if (!localOK)
      {
        if (Sage.DBG) System.out.println("Downloading new " + localPlugRepo + " file from " + repoUrl);
        FileDownloader downer = new FileDownloader(null);
        Object downRV = downer.downloadFile(repoUrl, null, localPlugRepo);
        if (!(downRV instanceof Boolean) || !((Boolean)downRV).booleanValue())
        {
          if (Sage.DBG) System.out.println("ERROR failed downloading " + repoUrl + " file.");
          // Just re-use what we have if its there
          if (!localPlugRepo.isFile())
            return false;
        }
        // Wait for the download to finish....
        while (!downer.isComplete())
        {
          try { Thread.sleep(250); } catch(Exception e){}
        }
        if (!downer.wasSuccessful())
        {
          if (Sage.DBG) System.out.println("ERROR failed downloading " + repoUrl + " file.");
          // Just re-use what we have if its there
          if (!localPlugRepo.isFile())
            return false;
        }
      }
    }

    // updated
    return true;
  }

  /**
   * Given the localFilename return the java File object, accounting for if the we are running
   * as EMBEDDED or not.
   *
   * @param localFileName local filename without paths (paths will be stripped)
   * @return
     */
  File getLocalPluginFile(String localFileName)
  {
    String name = new File(localFileName).getName(); // just ensures that name doesn't include a path.
    return new java.io.File(name);
  }

  // Returns true if the refresh succeeded
  public synchronized boolean refreshAvailablePlugins()
  {
    // Clients only do the update check, they don't get the repo information themselves
    if (!Sage.client)
    {
      List<File> localFiles = new ArrayList<File>();

      // SageTV 7 Plugins
      String sageTV7URL = repoValue("v7", REPO_URL_PROP_KEY, "http://download.sagetv.com/SageTVPlugins.xml");
      String sageTV7MD5 = repoValue("v7", REPO_MD5_PROP_KEY, "http://download.sagetv.com/SageTVPlugins.md5.txt");
      String sageTV7Local = repoValue("v7", REPO_LOCALFILE_PROP_KEY, "SageTVPlugins.xml");
      localFiles.add(getLocalPluginFile(sageTV7Local));
      updateLocalPluginRepoFile(sageTV7URL, sageTV7MD5, sageTV7Local);

      // SageTV 9 Plugins
      String sageTV9URL = repoValue("v9", REPO_URL_PROP_KEY, "https://raw.githubusercontent.com/OpenSageTV/sagetv-plugin-repo/master/SageTVPluginsV9.xml");
      String sageTV9MD5 = repoValue("v9", REPO_MD5_PROP_KEY, "https://raw.githubusercontent.com/OpenSageTV/sagetv-plugin-repo/master/SageTVPluginsV9.md5");
      String sageTV9Local = repoValue("v9", REPO_LOCALFILE_PROP_KEY, "SageTVPluginsV9.xml");
      localFiles.add(getLocalPluginFile(sageTV9Local));
      updateLocalPluginRepoFile(sageTV9URL, sageTV9MD5, sageTV9Local);

      // process any alternate REPOS
      String alternateRepos[] = getServerProperty(ADDITIONAL_REPOS_PROP_KEY, "").split("\\s,\\s");
      if (alternateRepos!=null)
      {
        String sageTVURL;
        String sageTVMD5;
        String sageTVLocal;
        for (String repoid: alternateRepos)
        {
          if ("v7".equals(repoid)) continue;
          if ("v9".equals(repoid)) continue;
          if (repoid.trim().length()==0) continue;

          sageTVURL = repoValue(repoid, REPO_URL_PROP_KEY, null);
          sageTVMD5 = repoValue(repoid, REPO_MD5_PROP_KEY, null);
          sageTVLocal = repoValue(repoid, REPO_LOCALFILE_PROP_KEY, "SageTVPlugins_"+repoid+".xml");
          localFiles.add(getLocalPluginFile(sageTVLocal));
          updateLocalPluginRepoFile(sageTVURL, sageTVMD5, sageTVLocal);
        }
      }

      // lastly, add in the "SageTVPluginsDev.xml"
      localFiles.add(getLocalPluginFile("SageTVPluginsDev.xml"));

      if (isDevMode()) {
        // SageTV developer mode, find all .xml files in the SageTVPluginsDev.d folder and process them
        // SageTVPluginsDev.d is a cleaner way to dynamically add new plugins to sagetv for development testing
        // Useful especially when you are working several different plugins and you are trying to coordinate
        // the dependencies between each, for test installing, etc.
        File devPluginsDir = getDevPluginsDir();
        if (devPluginsDir.exists() && devPluginsDir.isDirectory()) {
          File plugins[] = devPluginsDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
              return s.toLowerCase().endsWith(".xml");
            }
          });

          if (plugins != null) {
            localFiles.addAll(Arrays.asList(plugins));
          }
        }
      }

      loadRepoPlugins(localFiles);
    }

    // Check if there's any new updates available and post system messages about them.
    postMessagesForAvailableUpdates();
    return true;
  }

  /**
   * Returns the "devmode" Server property.  devmode is a special mode for running SageTV Server.  If Dev Mode is true
   * then a few things might happen.
   * 1. SageTV Plugins will look in the SageTVPluginsDev.d/ folder for plugin xmls and zip files
   * 2. When installing a plugin, if the plugin filename is in the SageTVPluginsDev.d/ folder then it will be "downloaded" from that location.
   * 3. MD5 will be ignored when installing from the SageTVPluginsDev.d/ folder
   * @return
     */
  public static boolean isDevMode() {
    return getServerBoolProperty("devmode", false);
  }

  private void postMessagesForAvailableUpdates()
  {
    String[] myPluginIDs = (String[]) myPlugins.keySet().toArray(Pooler.EMPTY_STRING_ARRAY);
    for (int i = 0; i < myPluginIDs.length; i++)
    {
      PluginWrapper myWrap = (PluginWrapper) myPlugins.get(myPluginIDs[i]);
      PluginWrapper repoWrap;
      if (Sage.client)
        repoWrap = getServerPluginForID(myPluginIDs[i]);
      else
        repoWrap = (PluginWrapper) latestRepoPlugins.get(myPluginIDs[i]);
      // Don't post system messages about library plugin updates
      if (myWrap != null && repoWrap != null && !LIBRARY_TYPE_PLUGIN.equals(myWrap.getType()))
      {
        if (compareVersions(myWrap.getVersion(), repoWrap.getVersion()) < 0)
        {
          String lastNotifyVer = Sage.client ? getServerProperty(PLUGIN_PROP_KEY + "/" + myPluginIDs[i] + "/last_upgrade_msg", "") :
            Sage.get(PLUGIN_PROP_KEY + "/" + myPluginIDs[i] + "/last_upgrade_msg", "");
          if (lastNotifyVer.length() == 0 || compareVersions(lastNotifyVer, repoWrap.getVersion()) < 0)
          {
            if (Sage.client)
            {
              try
              {
                java.util.Properties props = new java.util.Properties();
                props.setProperty("PluginID", myPluginIDs[i]);
                props.setProperty("PluginName", repoWrap.getName());
                props.setProperty("Version", repoWrap.getVersion());
                props.setProperty("IsPluginUpdate", "true");
                SageTV.api("PostSystemMessage", new Object[] {new Integer(sage.msg.SystemMessage.SOFTWARE_UPDATE_AVAILABLE_MSG),
                    new Integer(sage.msg.SystemMessage.INFO_PRIORITY), sage.Sage.rez("PLUGIN_UPDATE_AVAILABLE_MSG",
                        new Object[] { repoWrap.getName(), repoWrap.getVersion() }), props});
                setServerProperty(PLUGIN_PROP_KEY + "/" + myPluginIDs[i] + "/last_upgrade_msg", repoWrap.getVersion());
              }
              catch (Exception e)
              {
                if (Sage.DBG) System.out.println("ERROR posting system message to server for plugin update of:" + e);
              }
            }
            else
            {
              sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createPluginUpdateMsg(myPluginIDs[i], repoWrap.getName(), repoWrap.getVersion()));
              Sage.put(PLUGIN_PROP_KEY + "/" + myPluginIDs[i] + "/last_upgrade_msg", repoWrap.getVersion());
            }
          }
        }
      }
    }
  }

  public synchronized boolean disablePlugin(String pluginID, UIManager uiMgr)
  {
    PluginWrapper wrap = (PluginWrapper) myPlugins.get(pluginID);
    if (wrap != null)
    {
      int state = wrap.getState();
      if (STVI_TYPE_PLUGIN.equals(wrap.getType()) || ((STANDARD_TYPE_PLUGIN.equals(wrap.getType()) && wrap.getImplClass().length() > 0) &&
          (state == PluginWrapper.PLUGIN_STATE_FAILED || state == PluginWrapper.PLUGIN_STATE_ENABLED)))
      {
        java.util.HashSet depCheckAncestors = new java.util.HashSet();
        depCheckAncestors.add(pluginID);
        // Check to make sure there's no other dependencies on us before we disable ourself
        PluginWrapper[] allPlugs = getMyPlugins();
        for (int i = 0; i < allPlugs.length; i++)
        {
          boolean currIsEnabled = allPlugs[i].getState() == PluginWrapper.PLUGIN_STATE_ENABLED ||
              (STVI_TYPE_PLUGIN.equals(allPlugs[i].getType()) && isPluginEnabled(allPlugs[i], uiMgr));
          PluginWrapper.Dependency[] deps = allPlugs[i].getDependencies();
          for (int j = 0; j < deps.length; j++)
          {
            if ("Plugin".equals(deps[j].type))
            {
              if (depCheckAncestors.contains(deps[j].id))
              {
                if (currIsEnabled)
                {
                  if (Sage.DBG) System.out.println("ERROR Cannot disable plugin " + pluginID + " because " + allPlugs[i].getName() + " is dependent on it!");
                  return false;
                }
                else if (depCheckAncestors.add(allPlugs[i].getId()))
                {
                  // If we add a new dependency to the list; then we need to start over and check them all again
                  i = -1;
                  break;
                }
              }
            }
          }
        }
      }
      if (STVI_TYPE_PLUGIN.equals(wrap.getType()))
      {
        if (uiMgr != null && uiMgr.getBoolean(PLUGIN_PROP_KEY + "/" + wrap.getId() + "/enabled", false))
        {
          if (Sage.DBG) System.out.println("Disabling STVI " + pluginID + ", STV will need to be rebuilt...");
          uiMgr.putBoolean(PLUGIN_PROP_KEY + "/" + wrap.getId() + "/enabled", false);
          // NOTE: Are we sure we want to reload the UI at this point? We're not doing that as part of an install and leaving it up to
          // the UI to actually do the reload
          //uiMgr.freshStartup(new java.io.File(uiMgr.getModuleGroup().defaultModule.description()));
        }
      }
      else if (STANDARD_TYPE_PLUGIN.equals(wrap.getType()) && wrap.getImplClass().length() > 0)
      {
        if (state == PluginWrapper.PLUGIN_STATE_FAILED || state == PluginWrapper.PLUGIN_STATE_ENABLED)
        {
          if (Sage.DBG) System.out.println("Disabling plugin: " + wrap.getId());
          Sage.putBoolean(PLUGIN_PROP_KEY + "/" + wrap.getId() + "/enabled", false);
          if (state == PluginWrapper.PLUGIN_STATE_ENABLED)
          {
            startedPlugins.remove(wrap);
            SageTVPlugin realPlugin  = wrap.getRealPlugin();
            try
            {
              if (Sage.DBG) System.out.println("Calling stop on plugin: " + wrap.getName());
              realPlugin.stop();
              PluginEventManager.postEvent(PluginEventManager.PLUGIN_STOPPED, new Object[] { PluginEventManager.VAR_PLUGIN, wrap });
            }
            catch (Throwable t)
            {
              if (Sage.DBG) System.out.println("ERROR stopping plugin " + wrap.getName() + " of:" + t);
              if (Sage.DBG) t.printStackTrace();
            }
            try
            {
              if (Sage.DBG) System.out.println("Calling destroy on plugin: " + wrap.getName());
              realPlugin.destroy();
            }
            catch (Throwable t)
            {
              if (Sage.DBG) System.out.println("ERROR destroying plugin " + wrap.getName() + " of:" + t);
              if (Sage.DBG) t.printStackTrace();
            }
            sage.plugin.PluginEventManager.getInstance().fullUnsubscribe(realPlugin);
            wrap.clearRealPlugin();
          }
          wrap.setState(PluginWrapper.PLUGIN_STATE_DISABLED);
          if (Sage.DBG) System.out.println("DONE Disabling plugin: " + wrap.getId());
        }
      }
    }
    return true;
  }

  // This will enable the specified plugin; we need the UI context in case its an STVI.
  public synchronized boolean enablePlugin(String pluginID, UIManager uiMgr)
  {
    PluginWrapper wrap = (PluginWrapper) myPlugins.get(pluginID);
    if (wrap != null)
    {
      int state = wrap.getState();
      // We also need to enable any dependencies for this plugin regardless of the plugin type
      PluginWrapper.Dependency[] deps = wrap.getDependencies();
      for (int i = 0; i < deps.length; i++)
      {
        if ("Plugin".equals(deps[i].type))
        {
          PluginWrapper depPlug = (PluginWrapper) myPlugins.get(deps[i].id);
          if (depPlug == null)
          {
            if (pseudoClient)
            {
              // We need to skip this dependency check because we won't necessarily know about it in pseudo-client mode
              continue;
            }
            if (Sage.DBG) System.out.println("Cannot enable plugin " + pluginID + " because a dependency is not satisfied by:" + deps[i].id);
            return false;
          }
          if (Sage.DBG) System.out.println("Enabling dependent plugin " + depPlug.getName());
          if (!enablePlugin(deps[i].id, uiMgr))
          {
            if (Sage.DBG) System.out.println("Enabling failed because dependent plugin enabling failed!");
            return false;
          }
        }
        else if ("Core".equals(deps[i].type))
        {
          if (!isVersionCompatible(Version.VERSION, deps[i].minVersion, deps[i].maxVersion))
          {
            if (Sage.DBG) System.out.println("Cannot enable pluing " + pluginID + " because it's not compatible with this version of the Core!");
            return false;
          }
        }
        else if ("JVM".equals(deps[i].type))
        {
          if (!isVersionCompatible(System.getProperty("java.specification.version"), deps[i].minVersion, deps[i].maxVersion))
          {
            if (Sage.DBG) System.out.println("Cannot enable pluing " + pluginID + " because it's not compatible with this version of Java!");
            return false;
          }
        }
      }
      if (STVI_TYPE_PLUGIN.equals(wrap.getType()))
      {
        if (uiMgr != null && !uiMgr.getBoolean(PLUGIN_PROP_KEY + "/" + wrap.getId() + "/enabled", false))
        {
          if (Sage.DBG) System.out.println("Enabling STVI " + wrap.getName() + " and rebuilding the current STV");
          uiMgr.putBoolean(PLUGIN_PROP_KEY + "/" + wrap.getId() + "/enabled", true);
          // We need to prompt the user about this that the STV needs to be reloaded now
          //					uiMgr.freshStartup(new java.io.File(uiMgr.getModuleGroup().defaultModule.description()));
          return true;
        }
      }
      else if (STANDARD_TYPE_PLUGIN.equals(wrap.getType()) && wrap.getImplClass().length() > 0)
      {
        if (state == PluginWrapper.PLUGIN_STATE_DISABLED)
        {
          if (Sage.DBG) System.out.println("Enabling plugin: " + wrap.getName());
          Sage.putBoolean(PLUGIN_PROP_KEY + "/" + wrap.getId() + "/enabled", true);
          SageTVPlugin pluggy = dynamicLoadStandardPlugin(wrap.getImplClass(), wrap.isDesktopOnly(),
              Sage.getBoolean(PLUGIN_PROP_KEY + "/" + wrap.getId() + "/reset", false));
          if (pluggy != null)
          {
            try
            {
              if (Sage.DBG) System.out.println("Starting up standard plugin: " + wrap.getName());
              Thread.currentThread().setContextClassLoader(Sage.extClassLoader);
              pluggy.start();
              wrap.setRealPlugin(pluggy);
              wrap.setState(PluginWrapper.PLUGIN_STATE_ENABLED);
              startedPlugins.add(wrap);
              PluginEventManager.postEvent(PluginEventManager.PLUGIN_STARTED, new Object[] { PluginEventManager.VAR_PLUGIN, wrap });
            }
            catch (Throwable t)
            {
              if (Sage.DBG) System.out.println("ERROR starting up plugin " + wrap.getName() + " of:" + t);
              if (Sage.DBG) t.printStackTrace();
              wrap.setState(PluginWrapper.PLUGIN_STATE_FAILED);
            }
          }
          else
          {
            if (Sage.isHeadless() && wrap.isDesktopOnly())
            {
              if (Sage.DBG) System.out.println("Mark plugin as disabled since it only failed since its a desktop only plugin and this is a non-desktop environment");
              wrap.setState(PluginWrapper.PLUGIN_STATE_DISABLED);
            }
            else
              wrap.setState(PluginWrapper.PLUGIN_STATE_FAILED);
          }
          if (Sage.DBG) System.out.println("DONE Enabling plugin: " + wrap.getId());
          return wrap.getState() == PluginWrapper.PLUGIN_STATE_ENABLED;
        }
        else if (state == PluginWrapper.PLUGIN_STATE_FAILED)
        {
          if (Sage.DBG) System.out.println("Cannot enable plugin " + wrap.getId() + " because it is in the FAILED state!");
          return false;
        }
      }
      // We want to report true even if we couldn't enable it so that we can properly chain the enabling of dependent plugins
      return true;
    }
    return false;
  }

  // This can be UI-specific, so we need a UIManager reference
  public boolean isPluginCompatible(PluginWrapper wrap, UIManager uiMgr)
  {
    return ((Boolean) checkPluginCompatability(wrap, uiMgr, false, false)).booleanValue();
  }
  public boolean isPluginCompatible(PluginWrapper wrap, UIManager uiMgr, boolean skipSTVCheck)
  {
    return ((Boolean) checkPluginCompatability(wrap, uiMgr, false, skipSTVCheck)).booleanValue();
  }
  public String getPluginIncompatibleReason(PluginWrapper wrap, UIManager uiMgr, boolean skipSTVCheck)
  {
    return checkPluginCompatability(wrap, uiMgr, true, skipSTVCheck).toString();
  }
  public String getPluginIncompatibleReason(PluginWrapper wrap, UIManager uiMgr)
  {
    return checkPluginCompatability(wrap, uiMgr, true, false).toString();
  }
  private Object checkPluginCompatability(PluginWrapper wrap, UIManager uiMgr, boolean giveReasons, boolean skipSTVCheck)
  {
    if (wrap == null) return giveReasons ? (Object)"" : (Object)Boolean.FALSE;
    java.util.ArrayList rv = null;
    if (giveReasons)
      rv = new java.util.ArrayList();

    // Check all of the dependencies and make sure they are OK
    // Operating System
    if (!wrap.isLinuxOK() && Sage.LINUX_OS)
    {
      if (giveReasons)
      {
        if (wrap.isWinOK() && wrap.isMacOK()) rv.add("Windows, Macintosh");
        else if (wrap.isWinOK()) rv.add("Windows");
        else if (wrap.isMacOK()) rv.add("Macintosh");
      }
      else
        return Boolean.FALSE;
    }
    if (!wrap.isWinOK() && Sage.WINDOWS_OS)
    {
      if (giveReasons)
      {
        if (wrap.isLinuxOK() && wrap.isMacOK()) rv.add("Linux, Macintosh");
        else if (wrap.isLinuxOK()) rv.add("Linux");
        else if (wrap.isMacOK()) rv.add("Macintosh");
      }
      else
        return Boolean.FALSE;
    }
    if (!wrap.isMacOK() && Sage.MAC_OS_X)
    {
      if (giveReasons)
      {
        if (wrap.isWinOK() && wrap.isLinuxOK()) rv.add("Windows, Linux");
        else if (wrap.isWinOK()) rv.add("Windows");
        else if (wrap.isLinuxOK()) rv.add("Linux");
      }
      else
        return Boolean.FALSE;
    }
    // Desktop
    // We do allow installation of these if there is no UIManager reference since that's what happens
    // when the pseudo-client requests installation...we won't enable it anyways if it requires the desktop
    // and installing is harmless
    if (wrap.isDesktopOnly() && uiMgr != null && uiMgr.getUIClientType() == UIClient.REMOTE_UI)
    {
      if (giveReasons)
        rv.add(Sage.rez("DesktopOnly"));
      else
        return Boolean.FALSE;
    }
    if (wrap.isServerOnly() && Sage.client)
    {
      if (giveReasons)
        rv.add(Sage.rez("ServerOnly"));
      else
        return Boolean.FALSE;
    }
    // Dependencies
    PluginWrapper.Dependency[] deps = wrap.getDependencies();
    Boolean stvDepOK = null;
    java.util.ArrayList stvDepReasons = null;
    for (int i = 0; i < deps.length; i++)
    {
      // We skip STV dependencies if we have no UI we're dealing with.
      if ("STV".equals(deps[i].type) && uiMgr != null && !skipSTVCheck)
      {
        if (!deps[i].id.equals(uiMgr.getSTVName()))
        {
          if (giveReasons)
          {
            if (stvDepReasons == null)
              stvDepReasons = new java.util.ArrayList();
            stvDepReasons.add("STV: " + deps[i].id);
          }
          if (stvDepOK == null)
            stvDepOK = Boolean.FALSE;
        }
        else if (!isVersionCompatible(uiMgr.getSTVVersion(), deps[i].minVersion, deps[i].maxVersion))
        {
          if (giveReasons)
          {
            if (stvDepReasons == null)
              stvDepReasons = new java.util.ArrayList();
            stvDepReasons.add("STV Version: " + deps[i].minVersion + ((deps[i].maxVersion == null || deps[i].maxVersion.length() == 0) ? "+" : ("-" + deps[i].maxVersion)));
          }
          if (stvDepOK == null)
            stvDepOK = Boolean.FALSE;
        }
        else
          stvDepOK = Boolean.TRUE;
      }
      else if ("Plugin".equals(deps[i].type))
      {
        PluginWrapper myWrap = (PluginWrapper) myPlugins.get(deps[i].id);
        /*				if (myWrap != null && !isVersionCompatible(myWrap.getVersion(), deps[i].minVersion, deps[i].maxVersion))
				{
					if (giveReasons)
						rv.add(deps[i].id + " Version: " + deps[i].minVersion + ((deps[i].maxVersion == null || deps[i].maxVersion.length() == 0) ? "+" : ("-" + deps[i].maxVersion)));
					else
						return Boolean.FALSE;
				}*/
        // NOTE: If we do NOT have the required dependency installed, that is OK. But if we have a conflicting version
        // installed....then that's when we have a problem. But we may be able to install an upgraded version of that dependency so long
        // as that doesn't create other conflicts.
        // We do also need to check if the dependency has dependencies which may conflict
        if (myWrap == null || !isVersionCompatible(myWrap.getVersion(), deps[i].minVersion, deps[i].maxVersion))
        {
          myWrap = getLatestRepoPluginInVersionRange(deps[i].id, deps[i].minVersion, deps[i].maxVersion);
          // A dependency cannot be satisfied if its not in the repository
          if (myWrap == null)
          {
            if (giveReasons)
              rv.add(deps[i].id + " Version: " + deps[i].minVersion + ((deps[i].maxVersion == null || deps[i].maxVersion.length() == 0) ? "+" : ("-" + deps[i].maxVersion)));
            else
              return Boolean.FALSE;
          }
          else if (!isPluginCompatible(myWrap, uiMgr))
          {
            if (giveReasons)
              rv.add(getPluginIncompatibleReason(myWrap, uiMgr));
            else
              return Boolean.FALSE;
          }
        }
      }
      else if ("Core".equals(deps[i].type))
      {
        if (!isVersionCompatible(Version.VERSION, deps[i].minVersion, deps[i].maxVersion))
        {
          if (giveReasons)
            rv.add("SageTV Core Version: " + deps[i].minVersion + ((deps[i].maxVersion == null || deps[i].maxVersion.length() == 0) ? "+" : ("-" + deps[i].maxVersion)));
          else
            return Boolean.FALSE;
        }
      }
      else if ("JVM".equals(deps[i].type))
      {
        if (!isVersionCompatible(System.getProperty("java.specification.version"), deps[i].minVersion, deps[i].maxVersion))
        {
          if (giveReasons)
            rv.add("Java Version: " + deps[i].minVersion + ((deps[i].maxVersion == null || deps[i].maxVersion.length() == 0) ? "+" : ("-" + deps[i].maxVersion)));
          else
            return Boolean.FALSE;
        }
      }
    }
    if (stvDepOK != null && stvDepOK.equals(Boolean.FALSE))
    {
      if (giveReasons)
        rv.addAll(stvDepReasons);
      else
        return Boolean.FALSE;
    }

    if (STANDARD_TYPE_PLUGIN.equals(wrap.getType()) || STV_TYPE_PLUGIN.equals(wrap.getType()) || LIBRARY_TYPE_PLUGIN.equals(wrap.getType()))
    {
      // Also check to see if a different version of this plugin is currently installed, and if so
      // ensure that any other plugins we have that are dependent on that plugin will not have conflicts here
      PluginWrapper existingPlugin = (PluginWrapper) myPlugins.get(wrap.getId());
      if (existingPlugin != null && !existingPlugin.getVersion().equals(wrap.getVersion()))
      {
        java.util.Iterator walker = myPlugins.values().iterator();
        while (walker.hasNext())
        {
          PluginWrapper myWrap = (PluginWrapper) walker.next();
          deps = myWrap.getDependencies();
          for (int i = 0; i < deps.length; i++)
          {
            if (deps[i].id.equals(wrap.getId()))
            {
              if ("Plugin".equals(deps[i].type) && (STANDARD_TYPE_PLUGIN.equals(wrap.getType()) || LIBRARY_TYPE_PLUGIN.equals(wrap.getType())))
              {
                if (!isVersionCompatible(wrap.getVersion(), deps[i].minVersion, deps[i].maxVersion))
                {
                  if (giveReasons)
                    rv.add(myWrap.getName() + " Version: " + myWrap.getVersion());
                  else
                    return Boolean.FALSE;
                }
              }
            }
          }
        }
      }
    }
    if (giveReasons)
    {
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < rv.size(); i++)
      {
        if (i != 0)
          sb.append(", ");
        sb.append(rv.get(i).toString());
      }
      return sb.toString();
    }
    else
      return Boolean.TRUE;
  }

  public SageTVPlugin getRealPlugin(String pluginID)
  {
    PluginWrapper wrap = (PluginWrapper) myPlugins.get(pluginID);
    if (wrap != null)
      return wrap.getRealPlugin();
    else
      return null;
  }

  // This will log the version information for the specified file with the file tracker
  private void trackFile(java.io.File targetFilePath, int installIndex, String md5, boolean firstStage) throws java.io.IOException
  {
    // First check to see if the key is already defined
    String absPath = targetFilePath.getAbsolutePath();
    String existingKey = (String) fileTrackerFilekeys.get(absPath);
    if (existingKey == null)
    {
      fileKeyCounter++;
      existingKey = Integer.toString(fileKeyCounter);
      fileTrackerFilekeys.put(absPath, existingKey);
      fileTracker.put("filekeys/" + existingKey, absPath);
    }
    if (firstStage)
    {
      // Check to see if the MD5 is different than the latest one in the filetracker; if its unchanged
      // then we don't put this update in the filetracker since it would imply the file changed outside of the install processes when it actually hasn't
      String[] allVers = fileTracker.keys("vermd5s/" + existingKey);
      java.util.Arrays.sort(allVers, intStringSorter);
      String maxVer = (allVers.length > 0) ? allVers[allVers.length - 1] : "";
      String backupVer = null;
      if (allVers.length == 0 || !md5.equals(fileTracker.get("vermd5s/" + existingKey + "/" + maxVer, "")))
      {
        fileTracker.put("vermd5s/" + existingKey + "/" + installIndex, md5);
        backupVer = Integer.toString(installIndex);
      }
      else
        backupVer = maxVer;
      // Check to see if we already have a backup for this version; if so, then we don't need to do it again.
      // Also verify that the backup actually exists
      String backupFileStr = fileTracker.get("backups/" + existingKey + "/" + backupVer, null);
      if (backupFileStr == null || !(new java.io.File(backupFileStr).exists()))
      {
        // Now create the backup filepath
        java.io.File backupFile = new java.io.File(targetFilePath.getAbsolutePath() + "." + backupVer);
        int x = 0;
        while (backupFile.isFile())
        {
          backupFile = new java.io.File(targetFilePath.getAbsolutePath() + "." + backupVer + "." + x);
          x++;
        }
        IOUtils.copyFile(targetFilePath, backupFile);
        fileTracker.put("backups/" + existingKey + "/" + backupVer, backupFile.getAbsolutePath());
      }
    }
    else
    {
      // We just log the MD5 here so we can compare it later if needed
      fileTracker.put("vermd5s/" + existingKey + "/" + installIndex, md5);
    }
  }

  // upgrade is true if the uninstall is part of an upgrade. The difference is that
  // when we do an upgrade, we allow removal of plugins that other plugins are dependent upon; otherwise we don't allow removal.
  // This can return "OK" if it went fine, "RESTART" if the app needs to be restarted for the uninstall to be completed or
  // "FAILED - XXX" with XXX being the reason for the failure.
  public synchronized String uninstallPlugin(String pluginID, boolean upgrade)
  {
    PluginWrapper myPlug = (PluginWrapper) myPlugins.get(pluginID);
    if (myPlug == null)
      return "FAILED - NULL";
    if (Sage.DBG) System.out.println("Uninstalling plugin " + pluginID + " ver=" + myPlug.getVersion() + " upgrade=" + upgrade);
    if (!upgrade)
    {
      // Check to ensure no other plugins are dependent upon us which a removal could then break
      if (Sage.DBG) System.out.println("Checking other plugins to ensure none are dependent on the plugin being removed...");
      PluginWrapper[] allPlugs = getMyPlugins();
      for (int i = 0; i < allPlugs.length; i++)
      {
        PluginWrapper.Dependency[] deps = allPlugs[i].getDependencies();
        for (int j = 0; j < deps.length; j++)
        {
          if ("Plugin".equals(deps[j].type) && pluginID.equals(deps[j].id))
          {
            if (Sage.DBG) System.out.println("ERROR Cannot remove plugin " + pluginID + " because " + deps[j].id + " is dependent on it!");
            return "FAILED - " + allPlugs[i].getName() + " is dependent on " + myPlug.getName();
          }
        }
      }
    }

    // Now we can proceed with the removal of the files (any failures will be staged), any missing files skipped,
    // and any modified files skipped as well. This process cannot fail unless we are unable to write to the stageddeletes.txt or stagedrenames.txt files, but that
    // shouldn't occur...
    java.io.PrintWriter stagedDeleteWriter = null;
    java.io.File stagedDeleteFile = new java.io.File(System.getProperty("user.dir"), "stageddeletes.txt");
    try
    {
      stagedDeleteWriter = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(stagedDeleteFile, true)));
    }
    catch (java.io.IOException ioe)
    {
      if (Sage.DBG) System.out.println("ERROR cannot open stageddeletes.txt file for writing staging information: " + ioe);
      return "FAILED - Cannot Open " + stagedDeleteFile;
    }
    java.io.PrintWriter stagedRenameWriter = null;
    java.io.File stagedRenameFile = new java.io.File(System.getProperty("user.dir"), "stagedrenames.txt");
    try
    {
      stagedRenameWriter = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(stagedRenameFile, true)));
    }
    catch (java.io.IOException ioe)
    {
      if (Sage.DBG) System.out.println("ERROR cannot open stagedrenames.txt file for writing staging information: " + ioe);
      return "FAILED - Cannot Open " + stagedRenameFile;
    }

    // First thing we do is disable this plugin
    if (Sage.DBG) System.out.println("Disabling plugin " + pluginID + " as part of the uninstall process...");
    disablePlugin(pluginID, null);

    // Track all the directories that are listed in the filetracker for this install, this is how we can decide if any directories need
    // to be deleted as well (we only delete empty ones)
    java.util.HashSet checkDirs = new java.util.HashSet();

    // Now we go through the list of relevant files in the file tracker.
    // We can find the ones related to our install because they have a vermd5s entry that ends in our version number
    // Get the list of all the filekeys first
    try
    {
      String[] allFileKeys = fileTracker.keys("filekeys");
      for (int i = 0; i < allFileKeys.length; i++)
      {
        // Now check if there's a vermd5 for this file key for our install index, if so, then that means that file is part of our install
        String testMD5 = fileTracker.get("vermd5s/" + allFileKeys[i] + "/" + myPlug.getInstallIndex(), null);
        if (testMD5 != null)
        {
          String filepath = fileTracker.get("filekeys/" + allFileKeys[i], "");
          if (Sage.DBG) System.out.println("Uninstall found one of our files in the file tracker path=" + filepath);
          java.io.File realFile = new java.io.File(filepath);
          if (realFile.isFile() || (pendingFilesystem.containsKey(realFile) && !"0".equals(pendingFilesystem.get(realFile))))
          {
            // Now we need to determine if there's another version of the file we want to revert to; or if we should just delete the file, or
            // if we should just leave it alone
            String currMD5 = pendingFilesystem.containsKey(realFile) ? ((String) pendingFilesystem.get(realFile)) : IOUtils.calcMD5(realFile);
            if (currMD5.equals(testMD5))
            {
              if (Sage.DBG) System.out.println("File on disk matches the version we installed, determine what to do with it");
              String[] allVers = fileTracker.keys("vermd5s/" + allFileKeys[i]);
              if (allVers.length == 1)
              {
                if (Sage.DBG) System.out.println("File tracker indicates this version of the file is the only one that ever existed, delete the file");
                if (canModifyPath(realFile) && realFile.delete())
                {
                  if (Sage.DBG) System.out.println("Successfully deleted file as part of uninstall:" + realFile);
                }
                else
                {
                  if (Sage.DBG) System.out.println("Unable to delete file path safely; will do so as part of the next restart: " + realFile);
                  stagedDeleteWriter.println(realFile.getAbsolutePath());
                  pendingFilesystem.put(realFile, "0");
                  needRestart = true;
                }
              }
              else
              {
                java.util.Arrays.sort(allVers, intStringSorter);
                // Now find the maximum version of the file that should exist on disk now and replace it with that version
                String maxVer = allVers[allVers.length - 2];
                String oldPath = fileTracker.get("backups/" + allFileKeys[i] + "/" + maxVer, null);
                java.io.File backupFile = (oldPath == null) ? null : new java.io.File(oldPath);
                if (backupFile != null && backupFile.isFile())
                {
                  if (Sage.DBG) System.out.println("Prior version of the file is specified in file tracker and exists; revert to the file: " + backupFile);
                  // Remove the backup tracker entry since we'll be restoring the file and destorying the backup
                  fileTracker.remove("backups/" + allFileKeys[i] + "/" + maxVer);
                }
                else
                {
                  if (Sage.DBG) System.out.println("ERROR Backup file is missing from:" + backupFile + " search the remaining versions to try to find another one to use instead");
                  // Remove the invalid backup key
                  fileTracker.remove("backups/" + allFileKeys[i] + "/" + maxVer);
                  backupFile = null;
                  for (int j = allVers.length - 3; j >= 0; j--)
                  {
                    oldPath = fileTracker.get("backups/" + allFileKeys[i] + "/" + allVers[j], null);
                    // Remove the backup tracker entry since we'll be restoring the file and destorying the backup
                    fileTracker.remove("backups/" + allFileKeys[i] + "/" + allVers[j]);
                    backupFile = (oldPath == null) ? null : new java.io.File(oldPath);
                    if (backupFile != null && backupFile.isFile())
                    {
                      if (Sage.DBG) System.out.println("Found an alternate version to use for restoring the file from: " + backupFile);
                      break;
                    }
                    backupFile = null;
                  }
                }
                if (backupFile == null)
                {
                  // We do this because the filetracker may have information from some prior version; but then the user deleted the file. Then
                  // when this plugin was installed; no valid backup information was created. So in that case, the proper action would be to destroy the file
                  if (Sage.DBG) System.out.println("ERROR Unable to find a suitable version to restore the backup to; just delete the file: " + realFile);
                  if (canModifyPath(realFile) && realFile.delete())
                  {
                    if (Sage.DBG) System.out.println("Successfully deleted file:" + realFile);
                  }
                  else
                  {
                    if (Sage.DBG) System.out.println("Unable to delete file, staging its deletion for the next restart: " + realFile);
                    needRestart = true;
                    stagedDeleteWriter.println(realFile.getAbsolutePath());
                    pendingFilesystem.put(realFile, "0");
                  }
                }
                else
                {
                  // Check if the MD5 of the backupfile is the same as the one we want to delete; in that case we don't need to worry about replacing it
                  String backupMD5 = IOUtils.calcMD5(backupFile);
                  if (backupMD5.equals(testMD5))
                  {
                    if (Sage.DBG) System.out.println("Backup restoration not needed since MD5s match backupFile=" + backupFile + " realFile=" + realFile);
                    backupFile.delete();
                  }
                  else if (canModifyPath(realFile))
                  {
                    if (realFile.delete())
                    {
                      if (backupFile.renameTo(realFile))
                      {
                        if (Sage.DBG) System.out.println("Successfully restored backup version of the file from: " + backupFile + " to: " + realFile);
                        // Remove this from the MetaImage cache in case it was an actual image file we had loaded (sizing information may have changed)
                        MetaImage.clearFromCache(realFile);
                      }
                      else
                      {
                        if (Sage.DBG) System.out.println("Renaming of the backupfile to the real file failed; stage this operation for after a restart src=" + backupFile + " target:" + realFile);
                        needRestart = true;
                        stagedRenameWriter.println(backupFile.getAbsolutePath());
                        stagedRenameWriter.println(realFile.getAbsolutePath());
                        pendingFilesystem.put(realFile, backupMD5);
                      }
                    }
                    else
                    {
                      if (Sage.DBG) System.out.println("Deleting of the real file failed; stage this operation for after a restart src=" + backupFile + " target:" + realFile);
                      needRestart = true;
                      stagedRenameWriter.println(backupFile.getAbsolutePath());
                      stagedRenameWriter.println(realFile.getAbsolutePath());
                      pendingFilesystem.put(realFile, backupMD5);
                    }
                  }
                  else
                  {
                    if (Sage.DBG) System.out.println("Cannot safely restore file while running; stage this operation for after a restart src=" + backupFile + " target:" + realFile);
                    needRestart = true;
                    stagedRenameWriter.println(backupFile.getAbsolutePath());
                    stagedRenameWriter.println(realFile.getAbsolutePath());
                    pendingFilesystem.put(realFile, backupMD5);
                  }
                }
              }
            }
            else
            {
              if (Sage.DBG) System.out.println("File on disk does not match the version we installed, leave it alone");
            }
          }
          else if (realFile.isDirectory() && "0".equals(testMD5))
          {
            String[] allVers = fileTracker.keys("vermd5s/" + allFileKeys[i]);
            if (allVers.length == 1)
            {
              if (Sage.DBG) System.out.println("Found directory created by our install; we are the last one controlling it so check it for deletion:" + realFile);
              checkDirs.add(realFile);
            }
          }
          else
          {
            if (Sage.DBG) System.out.println("File no longer exists");
          }
          fileTracker.remove("vermd5s/" + allFileKeys[i] + "/" + myPlug.getInstallIndex());
          String backupFilePath = fileTracker.get("backups/" + allFileKeys[i] + "/" + myPlug.getInstallIndex(), null);
          if (backupFilePath != null)
          {
            java.io.File backupFile = new java.io.File(backupFilePath);
            if (backupFile.isFile())
            {
              if (Sage.DBG) System.out.println("File has a backup version related to our installation; removing that as well from: " + backupFile);
              backupFile.delete();
            }
            fileTracker.remove("backups/" + allFileKeys[i] + "/" + myPlug.getInstallIndex());
          }
        }
      }
    }
    finally
    {
      fileTracker.savePrefs();
    }

    // We cycle through this repeatedly so long as its shrinking. The reason is due to parent directories that may need to have subdirectories deleted before them.
    int checkSize = checkDirs.size();
    while (checkSize > 0)
    {
      java.util.Iterator walker = checkDirs.iterator();
      while (walker.hasNext())
      {
        java.io.File testDir = (java.io.File)walker.next();
        if (Sage.DBG) System.out.println("Checking directory to see if its empty so we can delete it: " + testDir);
        String[] fileList = testDir.list();
        if (fileList == null || fileList.length == 0)
        {
          if (Sage.DBG) System.out.println("Removing directory as part of the uninstall process: " + testDir);
          testDir.delete();
          walker.remove();
        }
      }
      if (checkSize == checkDirs.size())
        break;
      else
        checkSize = checkDirs.size();
    }

    // Finally we remove it from our map and clean it from the properties file
    if (Sage.DBG) System.out.println("Clearing property entries to finalize plugin uninstall of " + pluginID);
    myPlugins.remove(pluginID);
    Sage.removeNode(PLUGIN_PROP_KEY + "/" + pluginID);
    Sage.savePrefs();
    if (Sage.DBG) System.out.println("Uninstallation of plugin " + pluginID + " is completed!");
    if (Sage.DBG && !pendingFilesystem.isEmpty()) System.out.println("Pending filesystem MD5 state: " + pendingFilesystem);

    if (stagedDeleteWriter != null)
    {
      try
      {
        stagedDeleteWriter.close();
      }
      catch (Exception e){}
    }
    if (stagedRenameWriter != null)
    {
      try
      {
        stagedRenameWriter.close();
      }
      catch (Exception e){}
    }

    // Delete the staging txt files if we didn't actually write anything to them
    if (stagedDeleteFile.length() == 0)
      stagedDeleteFile.delete();
    if (stagedRenameFile.length() == 0)
      stagedRenameFile.delete();

    return needRestart ? "RESTART" : "OK";
  }

  // This checks to make sure all the files that were supposed to be installed for the plugin still exist
  public void verifyPluginInstall(PluginWrapper myPlug)
  {
    // Get the list of all the filekeys first
    String[] allFileKeys = fileTracker.keys("filekeys");
    for (int i = 0; i < allFileKeys.length; i++)
    {
      // Now check if there's a vermd5 for this file key for our install index, if so, then that means that file is part of our install
      String testMD5 = fileTracker.get("vermd5s/" + allFileKeys[i] + "/" + myPlug.getInstallIndex(), null);
      if (testMD5 != null)
      {
        String filepath = fileTracker.get("filekeys/" + allFileKeys[i], "");
        java.io.File realFile = new java.io.File(filepath);
        if (!realFile.exists())
        {
          if (!Sage.getBoolean(PLUGIN_PROP_KEY + "/" + myPlug.getId() + "/filewarning/" + realFile.getAbsolutePath(), false))
          {
            if (Sage.DBG) System.out.println("Plugin verification found that plugin " + myPlug.getName() + " is missing the file " + realFile.getAbsolutePath());
            sage.msg.MsgManager.postMessage(sage.msg.SystemMessage.createPluginMissingFileMsg(myPlug.getName(), realFile.getAbsolutePath()));
            Sage.putBoolean(PLUGIN_PROP_KEY + "/" + myPlug.getId() + "/filewarning/" + realFile.getAbsolutePath(), true);
          }
          else
          {
            if (Sage.DBG) System.out.println("Plugin verification found that plugin " + myPlug.getName() + " is missing the file " + realFile.getAbsolutePath() + " The user was already warned, so don't repost the message about this.");
          }
        }
      }
    }
  }

  // Installs, re-installs or upgrades the specified plugin. Returns "OK" on success, "RESTART" if a restart is required at which time the install will complete,
  // or "FAILED - XXX" with XXX being the reason for the installation failure.
  public synchronized String installPlugin(PluginWrapper plug, UIManager uiMgr, java.io.File stvRoot)
  {
    // First thing we do is check to make sure this plugin is compatible
    if (Sage.DBG) System.out.println("Plugin installation is starting for " + plug + " ver=" + plug.getVersion());
    if (plug == null)
      return "FAILED - NULL";
    if (Sage.DBG) System.out.println("Checking for compatability of the new plugin...");
    if (!isPluginCompatible(plug, uiMgr, true))
    {
      String reason = getPluginIncompatibleReason(plug, uiMgr, true);
      if (Sage.DBG) System.out.println("Plugin cannot be installed due to compatability reason of: " + reason);
      return "FAILED - Dependency: " + reason;
    }
    if (Sage.DBG) System.out.println("Plugin is compatible!");

    if (stvRoot == null && uiMgr != null)
    {
      stvRoot = new java.io.File(uiMgr.getModuleGroup().defaultModule.description()).getParentFile();
    }

    if (stvRoot == null && plug.getSTVImports().length > 0)
      return "FAIL - STV relative items need to be installed, but no STV context exists. Try installing this plugin from a local UI, extender or placeshifter instead.";

    // Before we do the uninstall; we should download and verify all the packages to ensure we won't fail for that reason and leave them with no plugin installed.
    // Now we get all of the resources for the plugin and download and store them in our local cache if they don't already exist
    java.io.File cacheDir = new java.io.File("PluginInstallers" + java.io.File.separator + plug.getId() + java.io.File.separator + plug.getVersion());
    if (!cacheDir.isDirectory())
    {
      if (!cacheDir.mkdirs())
      {
        if (Sage.DBG) System.out.println("ERROR Cannot create local cache directory for plugin downloads of " + cacheDir.getAbsolutePath());
        return "FAILED - Filesystem Permissions for " + cacheDir;
      }
    }

    // Download all of the packages first; and when that's done extract them
    // Keyed on Package, values are the local File
    java.util.Map packageMap = new java.util.HashMap();
    PluginWrapper.Package[] packages = plug.getPackages();
    for (int i = 0; i < packages.length; i++)
    {
      if (Sage.DBG) System.out.println("Plugin package " + packages[i]);
      java.net.URL packURL = null;
      try
      {
        packURL = new java.net.URL(packages[i].url);
      }
      catch (java.net.MalformedURLException mue)
      {
        if (Sage.DBG) System.out.println("Plugin installation cannot proceed because of an invalid URL in the package. URL=" + packages[i].url + " error=" + mue);
        progressMsg = "";
        return "FAILED - Invalid URL " + packages[i].url;
      }
      // Now check to see if its in our local cache or not
      String assetName = MediaFile.createValidFilename(packURL.toString());
      if (Sage.DBG) System.out.println("Plugin asset name is " + assetName);
      java.io.File assetFile = new java.io.File(cacheDir, assetName);
      boolean downloadNeeded = true;
      if (assetFile.isFile())
      {
        if (Sage.DBG) System.out.println("Asset file exists in local cache....check the MD5 sum...");
        String localMD5 = IOUtils.calcMD5(assetFile);
        progressMsg = Sage.rez("Verifying install packages") + ": " + plug.getName() + " " + (i + 1) + "/" + packages.length;
        if (localMD5 != null && localMD5.equalsIgnoreCase(packages[i].getMD5()))
        {
          if (Sage.DBG) System.out.println("Local cache file is good...the MD5 sums match!");
          downloadNeeded = false;
        }
        else
        {
          if (Sage.DBG) System.out.println("Local cache file is bad...MD5 sums do not match, delete the local file");
          if (!assetFile.delete())
          {
            if (Sage.DBG) System.out.println("ERROR Cannot delete local file from cache: " + assetFile + " using an alternate filename since this would be silly to fail on");
            int x = 0;
            while (assetFile.isFile())
            {
              assetFile = new java.io.File(cacheDir, assetName + x++);
            }
          }
        }
      }
      if (downloadNeeded)
      {
        progressMsg = Sage.rez("Downloading install packages") + ": " + plug.getName() + " " + (i + 1) + "/" + packages.length + " - 0%";
        if (Sage.DBG) System.out.println("Now downloading the plugin package from " + packages[i].url + " to local cache file " + assetFile);
        FileDownloader downer = new FileDownloader(null);
        Object downRV = downer.downloadFile(packages[i].url, null, assetFile);
        if (!(downRV instanceof Boolean) || !((Boolean)downRV).booleanValue())
        {
          if (Sage.DBG) System.out.println("Resource download failed from " + packages[i].url);
          progressMsg = "";
          return "FAILED - Resource Download " + packages[i].url;
        }
        // Wait for the download to finish....this could be awhile of course
        while (!downer.isComplete())
        {
          try { Thread.sleep(250); } catch(Exception e){}
          progressMsg = Sage.rez("Downloading install packages") + ": " + plug.getName() + " " + (i + 1) + "/" + packages.length + " - " + downer.getStatusMessage();
        }
        progressMsg = Sage.rez("Downloading install packages") + ": " + plug.getName() + " " + (i + 1) + "/" + packages.length + " - 100%";
        if (downer.wasSuccessful())
        {
          progressMsg = Sage.rez("Verifying install packages") + ": " + plug.getName() + " " + (i + 1) + " /" + packages.length;
          if (Sage.DBG) System.out.println("Download of package from " + packages[i].url + " is complete");
          // Now we check the MD5
          String localMd5 = IOUtils.calcMD5(assetFile);
          if (!packages[i].getMD5().equalsIgnoreCase(localMd5))
          {
            if (Sage.DBG) System.out.println("ERROR Bad download from " + packages[i].url + " the MD5 sums do not match!!!");
            progressMsg = "";
            return "FAILED - Resource Download " + packages[i].url + " - Invalid MD5 Sum";
          }
          else if (Sage.DBG)
            System.out.println("MD5 sums verified for download from " + packages[i].url);
        }
        else
        {
          if (Sage.DBG) System.out.println("There was an error downloading the package from " + packages[i].url + " to " + assetFile + " of " + downer.getStatusMessage());
          progressMsg = "";
          return "FAILED - Resource Download " + packages[i].url + " - " + downer.getStatusMessage();
        }
      }
      packageMap.put(packages[i], assetFile);
    }

    if (Sage.DBG) System.out.println("All packages are now downloaded for the plugin " + plug.getId() + " beginning uninstall and dependency verification...");

    // See if this plugin is already installed; if so then uninstall the currently installed one...BUT allow it even if it would break dependencies so long
    // as the new version would not have that problem.
    PluginWrapper existingPlugin = (PluginWrapper) myPlugins.get(plug.getId());
    if (existingPlugin != null)
    {
      if (Sage.DBG) System.out.println("Existing version of plugin " + plug.getId() + " found of " + existingPlugin.getVersion() + ", removing that plugin first");
      String uninstRes = uninstallPlugin(plug.getId(), true);
      if (!"OK".equals(uninstRes) && !"RESTART".equals(uninstRes))
        return "FAILED - Uninstall of current version " + uninstRes;
    }
    // All the dependencies should be OK; now we need to ensure all of them are installed first before we proceed
    PluginWrapper.Dependency[] deps = plug.getDependencies();
    for (int i = 0; i < deps.length; i++)
    {
      if ("Plugin".equals(deps[i].type))
      {
        PluginWrapper instDep = (PluginWrapper) myPlugins.get(deps[i].id);
        if (instDep == null || !isVersionCompatible(instDep.getVersion(), deps[i].minVersion, deps[i].maxVersion))
        {
          if (Sage.DBG) System.out.println("Plugin installation has a missing/incompatible dependency of: " + deps[i] + " installing that first...");
          String subRes = installPlugin((PluginWrapper) getLatestRepoPluginInVersionRange(deps[i].id, deps[i].minVersion, deps[i].maxVersion), uiMgr, stvRoot);
          if ("RESTART".equals(subRes))
            needRestart = true;
          else if (!"OK".equals(subRes))
            return "FAILED - Dependency Install " + deps[i].id + "-" + subRes;
        }
      }
    }

    if (Sage.DBG) System.out.println("Plugin dependencies have been verified....proceeding with extraction and installation: " + plug.getId());

    globalInstallCount++;
    int priorStateIndex = globalInstallCount;
    globalInstallCount++;
    int myInstallIndex = globalInstallCount;
    Sage.putInt("plugin_install_counter", globalInstallCount);
    if (Sage.DBG) System.out.println("Plugin apriori state#=" + priorStateIndex + " postInstall state#=" + myInstallIndex);

    java.util.Map nameToFileMap = new java.util.HashMap();
    boolean updateJARs = false;
    boolean resetChanLogos = false;
    try
    {
      for (int i = 0; i < packages.length; i++)
      {
        progressMsg = Sage.rez("Extracting install packages") + ": " + plug.getName() + " " + (i + 1) + "/" + packages.length;
        java.io.File destDir;
        if ("JAR".equals(packages[i].type))
        {
          destDir = new java.io.File(System.getProperty("user.dir"), "JARs").getAbsoluteFile();
          updateJARs = true;
        }
        else if ("System".equals(packages[i].type))
        {
          destDir = new java.io.File(System.getProperty("user.dir")).getAbsoluteFile();
          updateJARs = true;
        }
        else if ("Theme".equals(packages[i].type))
        {
          if (stvRoot == null)
            return "FAIL - STV relative items need to be installed, but no STV context exists. Try installing this plugin from a local UI, extender or placeshifter instead.";
          destDir = new java.io.File(stvRoot.getAbsoluteFile(), "Themes");
        }
        else if ("STV".equals(packages[i].type))
          destDir = new java.io.File(System.getProperty("user.dir"), "STVs").getAbsoluteFile();
        else if ("STVI".equals(packages[i].type))
        {
          if (stvRoot == null)
            return "FAIL - STV relative items need to be installed, but no STV context exists. Try installing this plugin from a local UI, extender or placeshifter instead.";
          destDir = stvRoot.getAbsoluteFile();
        }
        else if ("ChannelLogo".equals(packages[i].type))
        {
          destDir = EPG.getInstance().getLogoDir();
          resetChanLogos = true;
        }
        else if ("FanArt".equals(packages[i].type))
        {
          String fanArtPath = Sage.get("fan_art_dir", null);
          if (fanArtPath == null)
          {
            fanArtPath = Sage.getPath("data", "FanArt");
            Sage.put("fan_art_dir", fanArtPath);
          }
          destDir = new java.io.File(fanArtPath);
        }
        else
        {
          if (Sage.DBG) System.out.println("ERROR Invalid package destination for " + packages[i]);
          progressMsg = "";
          return "FAILED - Invalid Package Type " + packages[i].type;
        }

        byte[] buf = new byte[65536];

        java.io.File currFile = (java.io.File) packageMap.get(packages[i]);
        if ("JAR".equals(packages[i].type) && currFile.getName().endsWith("jar"))
        {
          // user has provided a direct jar file so we don't need to extract it, we just add it directly
          // and track it.
          if (Sage.DBG) System.out.println("Beginning copying jar from cache file " + currFile + " to root dest dir of: " + destDir);

          String result = null;
          try
          {
            // NOTE: Destination JAR file will have the same name as the JAR file as defined in the jar URL
            java.io.File destFile = new java.io.File(destDir, new File(packages[i].url).getName());
            result = installFile(packages[i], destFile, destDir, new FileInstallableItem(currFile, destFile.getName()), priorStateIndex, myInstallIndex, StagedRenameWriter.get(), buf);
            if (result!=null) return result;
          } catch (IOException e)
          {
            if (Sage.DBG || Sage.TESTING)
            {
              System.out.println("ERROR installing jar file " + currFile);
              e.printStackTrace();
            }
            progressMsg = "";
            return "FAILED - Jar Installation of " + packages[i].url;
          }
        }
        else
        {
          // We need to check for overwrite at each file if its OK or not; we also need to stage any failed extractions
          // zip file source, extract and preserve directory structure, permissions and such
          if (Sage.DBG)
            System.out.println("Beginning zip file extraction from cache file " + currFile + " to root dest dir of: " + destDir);
          java.util.zip.ZipFile zippy = null;
          try
          {
            zippy = new java.util.zip.ZipFile(currFile);
            ZipEntryInstallableItem zipEntryFacade = new ZipEntryInstallableItem(zippy);

            int numFiles = zippy.size();
            if (Sage.DBG) System.out.println("Zip archive contains " + numFiles + " entries");
            java.util.Enumeration zipList = zippy.entries();
            while (zipList.hasMoreElements())
            {
              java.util.zip.ZipEntry entry = (java.util.zip.ZipEntry) zipList.nextElement();
              if (Sage.DBG) System.out.println("Processing zip entry: " + entry.getName());
              java.io.File localExtract = new java.io.File(destDir, entry.getName());
              nameToFileMap.put(entry.getName(), localExtract);
              if (entry.isDirectory())
              {
                if (!localExtract.isDirectory() && !localExtract.mkdirs())
                {
                  if (Sage.DBG) System.out.println("ERROR Cannot create directory from zip archive: " + localExtract);
                  progressMsg = "";
                  return "FAILED - Cannot Create " + localExtract;
                } else
                {
                  if (Sage.DBG) System.out.println("Created local directory: " + localExtract);
                  if (entry.getTime() > 0)
                    localExtract.setLastModified(entry.getTime());
                  trackFile(localExtract, myInstallIndex, "0", false);
                  // Also track any parent directories that were automatically created
                  String dirName = entry.getName();
                  int dirIdx = dirName.lastIndexOf(java.io.File.separatorChar);
                  while (dirIdx != -1)
                  {
                    dirName = dirName.substring(0, dirIdx);
                    trackFile(new java.io.File(destDir, dirName), myInstallIndex, "0", false);
                    dirIdx = dirName.lastIndexOf(java.io.File.separatorChar);
                  }
                }
              } else
              {
                zipEntryFacade.setEntry(entry);
                String result = installFile(packages[i], localExtract, destDir, zipEntryFacade, priorStateIndex, myInstallIndex, StagedRenameWriter.get(), buf);
                if (result!=null) return result;
              }
            }
          } catch (java.io.IOException ze)
          {
            if (Sage.DBG) System.out.println("ERROR unzipping cache file " + currFile + " of " + ze);
            progressMsg = "";
            return "FAILED - Zip Extraction of " + packages[i].url;
          } finally
          {
            IOUtils.closeQuietly(zippy);
          }
        }

        // Remove the package files after extraction for all installs unless we have a property set to retain them
        if (Sage.getBoolean("remove_downloaded_plugin_installers", true))
        {
          if (Sage.DBG) System.out.println("Removing downloaded archive file: " + currFile);
          currFile.delete();
        }
      }
    }
    finally
    {
      IOUtils.closeQuietly(StagedRenameWriter.get());
      fileTracker.savePrefs();
    }

    progressMsg = Sage.rez("Finalizing install") + ": " + plug.getName();

    if (!needRestart && updateJARs)
    {
      Sage.updateJARLoader();
    }
    if (resetChanLogos)
    {
      EPG.getInstance().resetLogoMap();
    }
    if (Sage.DBG) System.out.println("Finished extraction of all the contents from the zip archive...writing plugin information to the properties for plugin: " + plug.getId());
    String propBase = PLUGIN_PROP_KEY + '/' + plug.getId() + '/';
    Sage.put(propBase + "name", plug.getName());
    Sage.put(propBase + "version", plug.getVersion());
    Sage.putLong(propBase + "date", plug.getModDate());
    Sage.putLong(propBase + "installdate", Sage.time());
    Sage.put(propBase + "desc", plug.getDesc());
    Sage.put(propBase + "author", plug.getAuthor());
    Sage.put(propBase + "relnotes", plug.getReleaseNotes());
    Sage.putBoolean(propBase + "beta", plug.isBeta());
    Sage.put(propBase + "respath", plug.getResPath());
    Sage.put(propBase + "type", plug.getType());
    Sage.put(propBase + "impl", plug.getImplClass());
    Sage.putBoolean(propBase + "desktop", plug.isDesktopOnly());
    Sage.putBoolean(propBase + "server", plug.isServerOnly());
    Sage.putBoolean(propBase + "enabled", true);
    Sage.putInt(propBase + "installindex", myInstallIndex);
    PluginWrapper.Dependency[] newDeps = plug.getDependencies();
    for (int i = 0; i < newDeps.length; i++)
    {
      String depPropBase = propBase + "dependency/" + newDeps[i].id + "/";
      Sage.put(depPropBase + "minversion", newDeps[i].minVersion);
      Sage.put(depPropBase + "maxversion", newDeps[i].maxVersion);
      Sage.put(depPropBase + "type", newDeps[i].type);
    }
    String[] imports = plug.getSTVImports();
    StringBuffer sb = new StringBuffer();
    if (imports.length > 0)
    {
      for (int i = 0; i < imports.length; i++)
      {
        if (i != 0)
          sb.append(';');
        if (nameToFileMap.containsKey(imports[i]))
          sb.append(((java.io.File) nameToFileMap.get(imports[i])).getAbsolutePath());
        else
          sb.append(new java.io.File(stvRoot, imports[i]).getAbsolutePath());
      }
    }
    Sage.put(propBase + "stvimports", sb.toString());
    String[] demovideos = plug.getDemoVideoURLs();
    sb.setLength(0);
    for (int i = 0; i < demovideos.length; i++)
    {
      if (i != 0)
        sb.append('|');
      sb.append(demovideos[i]);
    }
    Sage.put(propBase + "demovideos", sb.toString());
    String[] screenshots = plug.getScreenshotURLs();
    sb.setLength(0);
    for (int i = 0; i < screenshots.length; i++)
    {
      if (i != 0)
        sb.append('|');
      sb.append(screenshots[i]);
    }
    Sage.put(propBase + "screenshots", sb.toString());
    String[] webpages = plug.getWebpages();
    sb.setLength(0);
    for (int i = 0; i < webpages.length; i++)
    {
      if (i != 0)
        sb.append('|');
      sb.append(webpages[i]);
    }
    Sage.put(propBase + "webpages", sb.toString());
    Sage.savePrefs();

    if (Sage.DBG) System.out.println("Wrote new plugin's information to property file; loading the actual plugin object into our set.");
    // This will actually build the new PluginWrapper object and instantiate the plugin if it's a Standard type
    loadPlugin(plug.getId());

    if (!needRestart)
    {
      PluginWrapper newWrap = (PluginWrapper) myPlugins.get(plug.getId());
      SageTVPlugin realPlug = (newWrap!=null) ? newWrap.getRealPlugin() : null;
      if (realPlug != null)
      {
        try
        {
          Thread.currentThread().setContextClassLoader(Sage.extClassLoader);
          if (Sage.DBG) System.out.println("Plugin object was loaded, now starting it...");
          realPlug.start();
          startedPlugins.add(newWrap);
          PluginEventManager.postEvent(PluginEventManager.PLUGIN_STARTED, new Object[] { PluginEventManager.VAR_PLUGIN, newWrap });
        }
        catch (Throwable t)
        {
          if (Sage.DBG) System.out.println("ERROR starting plugin of:" + t);
          if (Sage.DBG) t.printStackTrace();
          newWrap.setState(PluginWrapper.PLUGIN_STATE_FAILED);
          newWrap.clearRealPlugin();
        }
      }
    }

    // If this is an STVI; we should not rebuild the STV automatically now. That should be something we prompt for in the UI to ask them about
    // after an STVI is installed since we can know the type of plugin installed. Rebuilding would also cause a menu transition which may be
    // unexpected by ther user as well.

    if (Sage.DBG) System.out.println("DONE with plugin installation for " + plug.getId() + " " + plug.getVersion());
    if (Sage.DBG && !pendingFilesystem.isEmpty()) System.out.println("Pending filesystem MD5 state: " + pendingFilesystem);
    progressMsg = "";
    return needRestart ? "RESTART" : "OK";
  }

  public static File getDevPluginsDir() {
    // sagetv appears to assume we'll always be run from the SageTV home directory
    return new File("SageTVPluginsDev.d");
  }

  /**
   * Encapsulates an installable item that might come various sources, such as a ZipFile or regular File
   */
  interface InstallableItem
  {
    String getName();
    InputStream getInputStream() throws IOException;
    long getTime();
  }

  /**
   * Encapsulates java File as an installable item
   */
  private static class FileInstallableItem implements InstallableItem
  {
    private final File file;
    private final String name;

    FileInstallableItem(File file, String name)
    {
      this.file=file;
      this.name=name;
    }
    public String getName()
    {
      return name;
    }

    public InputStream getInputStream() throws FileNotFoundException
    {
      return new FileInputStream(file);
    }

    public long getTime()
    {
      return file.lastModified();
    }
  }

  /**
   * Encapsulates a ZipEntry as an installable item
   */
  private static class ZipEntryInstallableItem implements InstallableItem
  {
    private final ZipFile file;
    private ZipEntry entry;

    ZipEntryInstallableItem(ZipFile file)
    {
      this.file=file;
    }

    public void setEntry(ZipEntry entry) {
      this.entry=entry;
    }

    public String getName()
    {
      return entry.getName();
    }

    public InputStream getInputStream() throws IOException
    {
      return file.getInputStream(entry);
    }

    public long getTime()
    {
      return entry.getTime();
    }
  }

  /**
   * Provides a singleton access to the Staged Renamer writer
   */
  static class StagedRenameWriter implements Closeable
  {
    static StagedRenameWriter instance = new StagedRenameWriter();

    public static final StagedRenameWriter get()
    {
      return instance;
    }

    java.io.PrintWriter stagedRenameWriter = null;

    public void open() throws IOException
    {
      if (stagedRenameWriter==null)
      {
        stagedRenameWriter = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(new java.io.File(System.getProperty("user.dir"),
          "stagedrenames.txt"), true)));
      }
    }

    public void close()
    {
      IOUtils.closeQuietly(stagedRenameWriter);
      stagedRenameWriter=null;
    }

    public void println(String msg) throws IOException
    {
      open();
      stagedRenameWriter.println(msg);
    }
  }

  /**
   * Installs an Installable Item to the localExtract file.
   *
   * @param pkg Plugin Package
   * @param localExtract Local File
   * @param destDir Destination Directory
   * @param entry Installable Item form which the get the Name and Input Stream
   * @param priorStateIndex
   * @param myInstallIndex
   * @param stagedRenameWriter writer for writing staged renames
   * @param buf copy data buffer
   * @return "OK" on success, or and error message if it failed
   * @throws IOException for any IO related errors
   */
  String installFile(PluginWrapper.Package pkg, File localExtract, File destDir, InstallableItem entry, final int priorStateIndex, final int myInstallIndex, StagedRenameWriter stagedRenameWriter, final byte buf[]) throws IOException
  {
    if (pkg.overwrite || !localExtract.isFile())
    {
      boolean openFailed = false;
      java.io.File orgLocalExtract = localExtract;
      // Check to see if the local file is one that we know we shouldn't overwrite while we're running
      if (localExtract.isFile() && !canModifyPath(localExtract))
      {
        openFailed = true;
      }
      String orgMD5 = null;
      if (Sage.DBG) System.out.println("Current extraction local target=" + localExtract);
      if (localExtract.isFile())
      {
        if (pendingFilesystem.containsKey(localExtract))
        {
          orgMD5 = (String) pendingFilesystem.get(localExtract);
          if ("0".equals(orgMD5))
          {
            orgMD5 = null;
          } else
          {
            trackFile(localExtract, priorStateIndex, orgMD5, true);
          }
        } else
        {
          orgMD5 = IOUtils.calcMD5(localExtract);
          // Update the filetracker with the apriori state of this file
          // This will also store a backup of the file if needed
          trackFile(localExtract, priorStateIndex, orgMD5, true);
        }
      } else
      {
        java.io.File dirParent = localExtract.getParentFile();
        if (!dirParent.isDirectory() && !dirParent.mkdirs())
        {
          if (Sage.DBG) System.out.println("ERROR Cannot create directory from zip archive: " + dirParent);
          progressMsg = "";
          return "FAILED - Cannot Create " + dirParent;
        }
        if (Sage.DBG)
          System.out.println("Ensured existence of parent directory " + dirParent + " isDir=" + dirParent.isDirectory());
        // Also track any parent directories that were automatically created
        String dirName = entry.getName();
        int dirIdx = dirName.lastIndexOf(java.io.File.separatorChar);
        while (dirIdx != -1)
        {
          dirName = dirName.substring(0, dirIdx);
          trackFile(new java.io.File(destDir, dirName), myInstallIndex, "0", false);
          dirIdx = dirName.lastIndexOf(java.io.File.separatorChar);
        }
      }

      FileOutputStream outStream=null;
      try
      {
        if (!openFailed)
        {
          try
          {
            outStream = new java.io.FileOutputStream(localExtract);
          } catch (java.io.IOException ioe)
          {
            if (localExtract.isFile())
            {
              if (Sage.DBG) System.out.println("ERROR opening local file to write to: " + localExtract);
              // Extract the file to a temporary location and check the MD5Sum of it to see if we even need to install it
              openFailed = true;
            } else
              throw ioe;
          }
        }
        if (openFailed)
        {
          localExtract = new java.io.File(localExtract.getAbsolutePath() + "." + myInstallIndex);
          int x = 0;
          while (localExtract.isFile())
          {
            localExtract = new java.io.File(localExtract.getAbsolutePath() + "." + myInstallIndex + "." + x++);
          }
          outStream = new java.io.FileOutputStream(localExtract);
        }
        InputStream inStream = entry.getInputStream();
        try
        {
          int numRead = inStream.read(buf);
          while (numRead != -1)
          {
            outStream.write(buf, 0, numRead);
            numRead = inStream.read(buf);
          }
        } finally
        {
          IOUtils.closeQuietly(inStream);
        }
      } finally
      {
        IOUtils.closeQuietly(outStream);
      }
      if (entry.getTime() > 0)
        localExtract.setLastModified(entry.getTime());
      String finalMD5 = IOUtils.calcMD5(localExtract);
      if (openFailed)
      {
        if (Sage.DBG)
          System.out.println("Extracted file to temporary location " + localExtract + " to check MD5 to see if we even need to install it since we can't write to the current file: " + orgLocalExtract);
        if (orgMD5 != null && finalMD5.equals(orgMD5))
        {
          if (Sage.DBG)
            System.out.println("MD5 sums matched...we can safely skip this file as part of the install process: " + orgLocalExtract);
          // We may be skipping the file install; but we still need to log it with the tracker!
          localExtract.delete(); // delete the temp file
        } else
        {
          if (Sage.DBG)
            System.out.println("MD5 sums didn't match; the file will have to be installed after a SageTV restart...update the stagedrename.txt file!");
          needRestart = true;
          if (Sage.DBG)
            System.out.println("Staging file due to restart requirement: " + localExtract + " target=" + orgLocalExtract);
            try
            {
              // Write the information for staging to the stagedrenames.txt file...we already have the staging file resource created
              stagedRenameWriter.println(localExtract.getAbsolutePath());
              stagedRenameWriter.println(orgLocalExtract.getAbsolutePath());
            } catch (java.io.IOException ioe)
            {
              if (Sage.DBG)
                System.out.println("ERROR cannot open stagedrenames.txt file for writing staging information: " + ioe);
              progressMsg = "";
              return "FAILED - Cannot Open " + new java.io.File(System.getProperty("user.dir"), "stagedrenames.txt");
            }

          pendingFilesystem.put(orgLocalExtract, finalMD5);
        }
      } else
      {
        if (Sage.DBG) System.out.println("Extracted file from archive to: " + localExtract);
        // Remove this from the MetaImage cache in case it was an actual image file we had loaded (sizing information may have changed)
        MetaImage.clearFromCache(localExtract);
      }
      trackFile(orgLocalExtract, myInstallIndex, finalMD5, false);
    } else
    {
      if (Sage.DBG)
        System.out.println("Skipping zip entry because overwrite is disabled and the file exists: " + localExtract);
    }
    return null;
  }

  private boolean canModifyPath(java.io.File f)
  {
    if (pendingFilesystem.containsKey(f))
      return false;
    String filepath = f.getName().toLowerCase();
    return !filepath.endsWith(".jar") && !filepath.endsWith(".dll") && !filepath.endsWith(".so") && !filepath.endsWith(".jnilib") &&
        !filepath.endsWith(".class") && !filepath.endsWith(".ax");
  }

  public void startAllPlugins()
  {
    Thread.currentThread().setContextClassLoader(Sage.extClassLoader);
    java.util.Vector pluginsToStart = new java.util.Vector(java.util.Arrays.asList(getMyPlugins()));
    java.util.Set failedPluginStartIDs = new java.util.HashSet();
    // We need to go through ALL of the plugins here in case there's a plugin in between a dependency and its grandparent that
    // doesn't have an actual implementation.
    while (!pluginsToStart.isEmpty())
    {
      PluginWrapper wrap = (PluginWrapper) pluginsToStart.remove(0);
      // Check if it has any dependencies; if so, then make sure they are started first. If any of them failed to start; then
      // this plugin should be marked as failed as well.
      PluginWrapper.Dependency[] deps = wrap.getDependencies();
      boolean depsDone = true;
      boolean depsFailed = false;
      for (int i = 0; i < deps.length; i++)
      {
        if ("Plugin".equals(deps[i].type))
        {
          if (failedPluginStartIDs.contains(deps[i].id))
          {
            depsFailed = true;
            failedPluginStartIDs.add(wrap.getId());
            if (Sage.DBG) System.out.println("Failed starting plugin " + wrap + " because its dependency of " + deps[i].id + " failed to start!");
            if (wrap.getRealPlugin() != null)
            {
              wrap.clearRealPlugin();
              wrap.setState(PluginWrapper.PLUGIN_STATE_FAILED);
            }
            break;
          }
          PluginWrapper depWrap = (PluginWrapper) myPlugins.get(deps[i].id);
          if (depWrap == null)
          {
            if (pseudoClient) // Can't check these properly
              continue;
            if (Sage.DBG) System.out.println("Failed starting plugin " + wrap + " because its dependency of " + deps[i].id + " does not exist!");
            failedPluginStartIDs.add(wrap.getId());
            if (wrap.getRealPlugin() != null)
            {
              wrap.clearRealPlugin();
              wrap.setState(PluginWrapper.PLUGIN_STATE_FAILED);
            }
            depsFailed = true;
            break;
          }
          int depIdx = pluginsToStart.indexOf(depWrap);
          if (depIdx >= 0)
          {
            // Dependency is not started yet; move us after it in the list
            depsDone = false;
            pluginsToStart.insertElementAt(wrap, depIdx + 1);
            if (Sage.DBG) System.out.println("Moving starting of plugin " + wrap.getName() + " to after " + depWrap.getName() + " because of its dependency on it.");
            break;
          }
        }
      }
      if (depsFailed || !depsDone || wrap.getRealPlugin() == null)
        continue;
      try
      {
        if (Sage.DBG) System.out.println("Calling start on plugin: " + wrap.getName());
        wrap.getRealPlugin().start();
        startedPlugins.add(wrap);
      }
      catch (Throwable t)
      {
        if (Sage.DBG) System.out.println("ERROR starting plugin " + wrap.getName() + " of:" + t);
        if (Sage.DBG) t.printStackTrace();
        wrap.clearRealPlugin();
        wrap.setState(PluginWrapper.PLUGIN_STATE_FAILED);
        failedPluginStartIDs.add(wrap.getId());
      }
    }
  }

  public void stopAllPlugins()
  {
    for (int i = startedPlugins.size() - 1; i >= 0; i--)
    {
      PluginWrapper wrap = (PluginWrapper) startedPlugins.get(i);
      SageTVPlugin plug = wrap.getRealPlugin();
      if (plug != null)
      {
        try
        {
          if (Sage.DBG) System.out.println("Calling stop on plugin: " + wrap.getName());
          plug.stop();
        }
        catch (Throwable t)
        {
          if (Sage.DBG) System.out.println("ERROR stopping plugin " + wrap.getName() + " of:" + t);
          if (Sage.DBG) t.printStackTrace();
        }
      }
    }
  }

  public void destroyAllPlugins()
  {
    for (int i = startedPlugins.size() - 1; i >= 0; i--)
    {
      PluginWrapper wrap = (PluginWrapper) startedPlugins.get(i);
      SageTVPlugin plug = wrap.getRealPlugin();
      if (plug != null)
      {
        try
        {
          if (Sage.DBG) System.out.println("Calling destroy on plugin: " + wrap.getName());
          plug.destroy();
        }
        catch (Throwable t)
        {
          if (Sage.DBG) System.out.println("ERROR destroying plugin " + wrap.getName() + " of:" + t);
          if (Sage.DBG) t.printStackTrace();
        }
      }
    }
    myPlugins.clear();
    startedPlugins.clear();
  }

  public PluginWrapper getLatestRepoPlugin(String id)
  {
    if (id != null)
      id = id.toLowerCase();
    return (PluginWrapper) latestRepoPlugins.get(id);
  }

  public PluginWrapper getMyPlugin(String id)
  {
    if (id != null)
      id = id.toLowerCase();
    return (PluginWrapper) myPlugins.get(id);
  }

  public static boolean isVersionCompatible(String target, String minVer, String maxVer)
  {
    try
    {
      if (minVer != null && minVer.length() > 0)
      {
        // Make sure the target is greater than the minimum
        int idx1a = 0;
        int idx1b = target.indexOf('.');
        int idx2a = 0;
        int idx2b = minVer.indexOf('.');
        do
        {
          int t = Integer.parseInt((idx1b == -1) ? target.substring(idx1a) : target.substring(idx1a, idx1b));
          int m = Integer.parseInt((idx2b == -1) ? minVer.substring(idx2a) : minVer.substring(idx2a, idx2b));
          if (t < m)
            return false;
          if (t > m)
            break;
          if (idx2b == -1)
            break; // matching versions, or target version is more detailed than minimum version, so this is OK
          if (idx1b == -1)
            return false; // min ver is more detailed than target ver, so its not sufficient
          idx1a = idx1b + 1;
          idx2a = idx2b + 1;
          idx1b = target.indexOf('.', idx1a);
          idx2b = minVer.indexOf('.', idx2a);
        } while (true);
      }
      if (maxVer != null && maxVer.length() > 0)
      {
        // Make sure the target is less than the minimum
        int idx1a = 0;
        int idx1b = target.indexOf('.');
        int idx2a = 0;
        int idx2b = maxVer.indexOf('.');
        do
        {
          int t = Integer.parseInt((idx1b == -1) ? target.substring(idx1a) : target.substring(idx1a, idx1b));
          int m = Integer.parseInt((idx2b == -1) ? maxVer.substring(idx2a) : maxVer.substring(idx2a, idx2b));
          if (t > m)
            return false;
          if (t < m)
            break;
          if (idx1b == -1)
            break; // matching versions, or max version is more detailed than target version, so this is OK
          if (idx2b == -1)
            return false; // target ver is more detailed than max ver, so its not sufficient
          idx1a = idx1b + 1;
          idx2a = idx2b + 1;
          idx1b = target.indexOf('.', idx1a);
          idx2b = maxVer.indexOf('.', idx2a);
        } while (true);
      }
    }
    catch (NumberFormatException nfe)
    {
      if (Sage.DBG) System.out.println("ERROR doing version comparison, non-numeric version info: target=" + target + " min=" + minVer + " max=" + maxVer);
      return false;
    }
    return true;
  }

  // returns -1 if v1 < v2, 0 if equal, 1 if v1 > v2
  public static int compareVersions(String v1, String v2)
  {
    if (v1 == null && v2 == null) return 0;
    else if (v1 == null) return 1;
    else if (v2 == null) return -1;
    if (v1.equals(v2)) return 0;
    if (v1.length() == 0 && v2.length() == 0) return 0;
    else if (v1.length() == 0) return 1;
    else if (v2.length() == 0) return -1;
    // Make sure the target is less than the minimum
    int idx1a = 0;
    int idx1b = v1.indexOf('.');
    int idx2a = 0;
    int idx2b = v2.indexOf('.');
    do
    {
      int t = Integer.parseInt((idx1b == -1) ? v1.substring(idx1a) : v1.substring(idx1a, idx1b));
      int m = Integer.parseInt((idx2b == -1) ? v2.substring(idx2a) : v2.substring(idx2a, idx2b));
      if (t > m)
        return 1;
      if (t < m)
        return -1;
      if (idx1b == -1)
        return -1;
      if (idx2b == -1)
        return 1;
      idx1a = idx1b + 1;
      idx2a = idx2b + 1;
      idx1b = v1.indexOf('.', idx1a);
      idx2b = v2.indexOf('.', idx2a);
    } while (true);
  }

  public static String[] getServerPropertyChildrenNames(String key)
  {
    try
    {
      Object[] rv = (Object[]) SageTV.api("GetServerSubpropertiesThatAreBranches", new Object[] { key });
      // since networked API calls don't have the array type set correctly when the length is zero
      if (rv.length == 0)
        return Pooler.EMPTY_STRING_ARRAY;
      else
        return (String[]) rv;
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR Cannot retrieve server property information!!! " + e);
      return Pooler.EMPTY_STRING_ARRAY;
    }
  }

  public static String getServerProperty(String name, String defaultValue)
  {
    try
    {
      return (String) SageTV.api("GetServerProperty", new Object[] { name, defaultValue });
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR Cannot retrieve server property information!!! " + e);
      return "";
    }
  }

  public static PluginWrapper getServerPluginForID(String id)
  {
    try
    {
      return (PluginWrapper) SageTV.api("GetAvailablePluginForID", new Object[] { id });
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR Cannot retrieve server plugin information!!! " + e);
      return null;
    }
  }

  public static void setServerProperty(String name, String value)
  {
    try
    {
      SageTV.api("SetServerProperty", new Object[] { name, value });
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR Cannot set server property information!!! " + e);
    }
  }

  public static boolean getServerBoolProperty(String name, boolean defaultValue)
  {
    try
    {
      return Boolean.valueOf((String)SageTV.api("GetServerProperty", new Object[] { name, Boolean.toString(defaultValue) })).booleanValue();
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR Cannot retrieve server property information!!! " + e);
      return defaultValue;
    }
  }

  public static int getServerIntProperty(String name, int defaultValue)
  {
    try
    {
      return Integer.parseInt((String) SageTV.api("GetServerProperty", new Object[] { name, Integer.toString(defaultValue) }));
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR Cannot retrieve server property information!!! " + e);
      return defaultValue;
    }
  }

  public static long getServerLongProperty(String name, long defaultValue)
  {
    try
    {
      return Long.parseLong((String) SageTV.api("GetServerProperty", new Object[] { name, Long.toString(defaultValue) }));
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ERROR Cannot retrieve server property information!!! " + e);
      return defaultValue;
    }
  }

  public boolean isRestartNeeded()
  {
    return needRestart;
  }

  public String getPluginProgress()
  {
    return progressMsg;
  }

  // myPlugins are keyed by the pluginID and resolve to a PluginWrapper
  private java.util.Map myPlugins;
  // allRepoPlugins are keyed by the pluginID and resolve to an ArrayList with potentially multiple versions of the plugin
  // latestRepoPlugins are keyed by the pluginID and resolve to a PluginWrapper
  private java.util.Map allRepoPlugins;
  private java.util.Map latestRepoPlugins;
  // The temp ones are used when building the plugin data from the XML file
  private java.util.Map allRepoPluginsTemp;
  private java.util.Map latestRepoPluginsTemp;
  private SAXParserFactory factory = SAXParserFactory.newInstance();
  private DefaultHandler repoHandler;
  private DevRepoSAXHandler devRepoHandler;
  private String repoVer = "";
  private boolean needRestart;
  private int globalInstallCount;
  // We use a SageProperties object for this so that it keeps the names in a sorted order in the file it creates
  private SageProperties fileTracker;
  private java.util.Map fileTrackerFilekeys;
  private int fileKeyCounter;
  private String progressMsg = "";
  // This is for storing the MD5 of what the filesystem is going to look like after we restart and process the staging files.
  private java.util.Map pendingFilesystem = new java.util.HashMap();
  private boolean pseudoClient;

  // When we stop the plugins; we process this in reverse order
  private java.util.Vector startedPlugins = new java.util.Vector();

  private class RepoSAXHandler extends DefaultHandler
  {
    String current_tag;
    StringBuffer buff = new StringBuffer();
    PluginWrapper currPlugin;
    PluginWrapper.Dependency currDependency;
    PluginWrapper.Package currPackage;
    boolean osRestrictions;
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
    {
      if ("PluginRepository".equalsIgnoreCase(qName))
      {
        if (attributes != null)
        {
          repoVer = attributes.getValue("version");
        }
      }
      else if ("SageTVPlugin".equalsIgnoreCase(qName))
      {
        currPlugin = new PluginWrapper();
        currPlugin.setState(PluginWrapper.PLUGIN_STATE_REPO);
        osRestrictions = false;
      }
      else if ("Version".equalsIgnoreCase(qName))
      {
        if (attributes != null && currPlugin != null)
        {
          currPlugin.setBeta(Boolean.valueOf(attributes.getValue("beta")).booleanValue());
        }
      }
      else if ("Dependency".equalsIgnoreCase(qName))
      {
        currDependency = new PluginWrapper.Dependency();
      }
      else if ("Package".equalsIgnoreCase(qName))
      {
        currPackage = new PluginWrapper.Package();
      }
      else if ("OS".equalsIgnoreCase(qName))
      {
        if (!osRestrictions && currPlugin != null)
        {
          // If there are any OS tags; then set all OS enabling to false
          osRestrictions = true;
          currPlugin.setWinOK(false);
          currPlugin.setMacOK(false);
          currPlugin.setLinuxOK(false);
        }
      }
      current_tag = qName;
    }
    public void characters(char[] ch, int start, int length)
    {
      String data = new String(ch,start,length);

      //Jump blank chunk
      if (data.trim().length() == 0)
        return;
      buff.append(data);
    }
    public void endElement(String uri, String localName, String qName)
    {
      String data = buff.toString().trim();

      if (qName.equals(current_tag))
        buff = new StringBuffer();
      if (currDependency != null)
      {
        if ("Dependency".equalsIgnoreCase(qName))
        {
          currPlugin.addDependency(currDependency);
          currDependency = null;
        }
        else if ("Plugin".equalsIgnoreCase(qName))
        {
          currDependency.type = "Plugin";
          currDependency.id = data.toLowerCase();
        }
        else if ("STV".equalsIgnoreCase(qName))
        {
          currDependency.type = "STV";
          currDependency.id = data;
        }
        else if ("Core".equalsIgnoreCase(qName))
        {
          currDependency.type = "Core";
          currDependency.id = "SageTV";
        }
        else if ("JVM".equalsIgnoreCase(qName))
        {
          currDependency.type = "JVM";
          currDependency.id = "Java";
        }
        else if ("MinVersion".equalsIgnoreCase(qName))
        {
          currDependency.minVersion = data;
        }
        else if ("MaxVersion".equalsIgnoreCase(qName))
        {
          currDependency.maxVersion = data;
        }
      }
      else if (currPackage != null)
      {
        if ("Package".equalsIgnoreCase(qName))
        {
          currPlugin.addPackage(currPackage);
          currPackage = null;
        }
        else if ("PackageType".equalsIgnoreCase(qName))
        {
          currPackage.type = data;
        }
        else if ("Location".equalsIgnoreCase(qName))
        {
          currPackage.url = data;
        }
        else if ("MD5".equalsIgnoreCase(qName))
        {
          currPackage.setRawMD5(data);
        }
        else if ("Overwrite".equalsIgnoreCase(qName))
        {
          currPackage.overwrite = Boolean.valueOf(data).booleanValue();
        }
      }
      else if (currPlugin != null)
      {
        if ("SageTVPlugin".equalsIgnoreCase(qName))
        {
          java.util.ArrayList list = (java.util.ArrayList) allRepoPluginsTemp.get(currPlugin.getId());
          if (list == null)
            allRepoPluginsTemp.put(currPlugin.getId(), list = new java.util.ArrayList());
          list.add(currPlugin);
          PluginWrapper verTest = (PluginWrapper) latestRepoPluginsTemp.get(currPlugin.getId());
          if (verTest == null || compareVersions(verTest.getVersion(), currPlugin.getVersion()) < 0)
            latestRepoPluginsTemp.put(currPlugin.getId(), currPlugin);
          currPlugin = null;
        }
        else if ("Name".equalsIgnoreCase(qName))
        {
          currPlugin.setName(data);
        }
        else if ("Identifier".equalsIgnoreCase(qName))
        {
          currPlugin.setId(data.toLowerCase());
        }
        else if ("Description".equalsIgnoreCase(qName))
        {
          currPlugin.setDesc(data);
        }
        else if ("Screenshot".equalsIgnoreCase(qName))
        {
          if (data.length() > 0)
            currPlugin.addScreenshotURL(data);
        }
        else if ("DemoVideo".equalsIgnoreCase(qName))
        {
          if (data.length() > 0)
            currPlugin.addDemoVideoURL(data);
        }
        else if ("STVImport".equalsIgnoreCase(qName))
        {
          if (data.length() > 0)
            currPlugin.addSTVImport(data);
        }
        else if ("Author".equalsIgnoreCase(qName))
        {
          currPlugin.setAuthor(data);
        }
        else if ("ReleaseNotes".equalsIgnoreCase(qName))
        {
          currPlugin.setReleaseNotes(data);
        }
        else if ("CreationDate".equalsIgnoreCase(qName))
        {
          currPlugin.setCreateDate(parseMetaDate(data));
        }
        else if ("ModificationDate".equalsIgnoreCase(qName))
        {
          currPlugin.setModDate(parseMetaDate(data));
        }
        else if ("Version".equalsIgnoreCase(qName))
        {
          currPlugin.setVersion(data);
        }
        else if ("Webpage".equalsIgnoreCase(qName))
        {
          if (data.length() > 0)
            currPlugin.addWebpage(data);
        }
        else if ("OS".equalsIgnoreCase(qName))
        {
          if (data.equalsIgnoreCase("Linux"))
            currPlugin.setLinuxOK(true);
          else if (data.toLowerCase().startsWith("win"))
            currPlugin.setWinOK(true);
          else if (data.toLowerCase().startsWith("mac"))
            currPlugin.setMacOK(true);
        }
        else if ("ResourcePath".equalsIgnoreCase(qName))
        {
          currPlugin.setResPath(data);
        }
        else if ("ImplementationClass".equalsIgnoreCase(qName))
        {
          currPlugin.setImplClass(data);
        }
        else if ("Desktop".equalsIgnoreCase(qName))
        {
          currPlugin.setDesktopOnly(Boolean.valueOf(data).booleanValue());
        }
        else if ("ServerOnly".equalsIgnoreCase(qName))
        {
          currPlugin.setServerOnly(Boolean.valueOf(data).booleanValue());
        }
        else if ("PluginType".equalsIgnoreCase(qName))
        {
          currPlugin.setType(data);
        }
      }
    }
    private long parseMetaDate(String s)
    {
      // Check for YYYY-MM-DD format
      int idx1 = s.indexOf('.');
      if (idx1 == -1)
        idx1 = s.indexOf('-');
      if (idx1 != -1)
      {
        int idx2 = s.indexOf('.', idx1 + 1);
        if (idx2 == -1)
          idx2 = s.indexOf('-', idx1 + 1);
        if (idx2 != -1)
        {
          try
          {
            return new java.util.GregorianCalendar(Integer.parseInt(s.substring(0, idx1)),
                Integer.parseInt(s.substring(idx1 + 1, idx2)) - 1, Integer.parseInt(s.substring(idx2 + 1))).getTimeInMillis();
          }
          catch (Exception e)
          {
          }
        }
      }
      return 0;
    }
  }

  /**
   * DevRepoSAXHandler upgrades the Location and Version tags for dev plugins in the SageTVPluginsDev.d directory
   * ensuring that if there is a local package install, then the URL is set to a File url for it, and that the
   * version of the plugin is updated so that sagetv will think it needs to install it every time.  This allows
   * for continuous pushing of packages to the SageTVPluginDev.d directory without having to manually update the
   * version field to get sagetv to install it.
   */
  public class DevRepoSAXHandler extends RepoSAXHandler
  {
    private String getFileName(String file)
    {
      URI uri = URI.create(file);
      return new File(uri.getPath()).getName();
    }

    public void endElement(String uri, String localName, String qName)
    {
      boolean handled=false;

      if (currPackage != null)
      {
        if ("Location".equalsIgnoreCase(qName))
        {
          String data = buff.toString().trim();
          if (data.length()>0)
          {
            File localFile = new File(getDevPluginsDir(), getFileName(data));
            if (localFile.exists())
            {
              try
              {
                // set the download url to be the local file
                currPackage.url = localFile.toURI().toURL().toString();
                // set the md5 automatically from the local file
                currPackage.setRawMD5(IOUtils.calcMD5(localFile));
                // super will already populate the version, but in dev mode, we force an increment on the version
                // so that sagetv will try to install it every time
                // LIMITATION: can only update once a minute :)
                SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmm");
                currPlugin.setVersion(currPlugin.getVersion()+"."+sdf.format(new Date()));
                if (Sage.DBG) System.out.println("Changed Package Url, MD5, and Version to " + currPackage.url + ", "+currPackage.getRawMD5()+", " + currPlugin.getVersion() + " for " + currPlugin.getName() + "("+currPlugin.getId()+")");
                handled=true;
              }
              catch (MalformedURLException e)
              {
                System.out.println("Failed to override package url with local url for " + localFile);
              }
            }
          }
        }
        else if ("MD5".equalsIgnoreCase(qName))
        {
          // only set the MD5 from the xml if we haven't already calculated it
          if (currPackage.getRawMD5()!=null)
            handled=true;
        }
      }

      if (handled)
      {
        if (qName.equals(current_tag))
          buff = new StringBuffer();
      }
      else
      {
        super.endElement(uri, localName, qName);
      }
    }

    public boolean canHandleFile(File repoXmlFile)
    {
      return repoXmlFile!=null && repoXmlFile.getParentFile()!=null && repoXmlFile.getParentFile().getName().equals("SageTVPluginsDev.d");
    }
  }

  public static final java.util.Comparator intStringSorter = new java.util.Comparator()
  {
    public int compare(Object o1, Object o2)
    {
      int rv;
      if (o1 == o2) rv = 0;
      else if (o1 == null) rv = (1);
      else if (o2 == null) rv = (-1);
      else
      {
        int i1 = 0;
        int i2 = 0;
        try
        {
          i1 = Integer.parseInt(o1.toString());
        }
        catch (NumberFormatException nfe){}
        try
        {
          i2 = Integer.parseInt(o2.toString());
        }
        catch (NumberFormatException nfe){}

        rv = i1 - i2;
      }
      return rv;
    }
  };
}
