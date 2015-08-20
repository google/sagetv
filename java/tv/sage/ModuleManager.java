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
package tv.sage;

import java.text.ParseException;

/**
 * @author 601
 */
public abstract class ModuleManager
{
  //    public static final boolean isModular = true;

  protected static final String DEFAULT_PROPERTIES_FILENAME = "xml/module.properties";

  // 601 remove later (at least check refs)
  // NARFLEX - I'm disabling usage of this now because it leaves around refs to the last UI
  public static tv.sage.ModuleGroup defaultModuleGroup = null;

  public static ModuleGroup loadModuleGroup(java.util.Properties moduleProperties) throws tv.sage.SageException
  {
    //        tv.sage.mod.Log.ger.info("newModuleGroup:\r\n" + moduleProperties);

    ModuleGroup mg = new ModuleGroup();

    mg.load(moduleProperties);

    // 601 temp hack
    //        if (defaultModuleGroup == null)
    {
      //            defaultModuleGroup = mg;
    }

    tv.sage.Modular[] modz = mg.getModules();
    //       for (int i = 0; i < modz.length; i++)
    {
      //         tv.sage.mod.Log.ger.info("Loaded " + modz[i]);
    }

    if (moduleProperties.getProperty("save") != null) // save XML copies
    {
      for (int i = 0; i < modz.length; i++)
      {
        // 601 cast away
        ((tv.sage.mod.Module)modz[i]).saveXML(new java.io.File("xml/" + modz[i].name() + ".save.xml"), null);
      }
    }

    if (moduleProperties.getProperty("exit") != null)
    {
      throw (new tv.sage.SageException("Quick Exit", tv.sage.SageExceptable.UNKNOWN));
    }

    if (sage.Sage.getBoolean("preload_expression_cache", false))
    {
      if (sage.Sage.DBG) System.out.println("Preloading all Widget data into expression cache....");
      sage.Widget[] widgz = mg.defaultModule.getWidgetz(true);
      for (int i = 0; i < widgz.length; i++){
        try {
          sage.Catbert.precompileWidget(widgz[i]);
        } catch (ParseException e) {
          System.out.println("...When parsing widget with ID "+widgz[i].symbol());
        }
      }
      if (sage.Sage.DBG) System.out.println("DONE preloading all Widget data into expression cache.");
    }

    return (mg);
  }

  public static ModuleGroup newModuleGroup() throws tv.sage.SageException
  {
    ModuleGroup mg = new ModuleGroup();

    mg.load(null);

    // 601 temp hack
    //        if (defaultModuleGroup == null)
    {
      //          defaultModuleGroup = mg;
    }

    return mg;
  }

  /**
   * Get the default Module properties from the default place.
   */
  public static java.util.Properties defaultModuleProperties() throws tv.sage.SageException
  {
    java.util.Properties moduleProperties = new java.util.Properties();

    try
    {
      java.io.InputStream is = new java.io.FileInputStream(DEFAULT_PROPERTIES_FILENAME);

      try
      {
        moduleProperties.load(is);
      }
      finally
      {
        is.close();
      }
    }
    catch (java.io.IOException iox)
    {
      //throw (new tv.sage.SageException(iox, tv.sage.SageExceptable.INTEGRITY));
      iox.printStackTrace();
    }

    return (moduleProperties);
  }
}
