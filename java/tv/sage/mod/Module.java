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
package tv.sage.mod;

import sage.SageConstants;

/**
 * A collection of Widget.
 * <br>
 * @author 601
 */
public class Module implements tv.sage.Modular
{
  //    // (moduleName-String, Module)
  //    public static java.util.Map moduleMap = new java.util.TreeMap(new java.util.Comparator()
  //    {
  //        public int compare(Object o1, Object o2)
  //        {
  //            int diff = ((String)o1).compareTo(o2);
  //
  //            //System.out.println("compare o1=" + o1 + " o2=" + o2 + " diff=" + diff);
  //
  //            return (diff);
  //        }
  //    });
  //
  //    // (module:symbol-String, Widget)
  //    public static final java.util.Map symbolMap = new java.util.HashMap();


  //    public static Module[] modulez()
  //    {
  //        synchronized (moduleMap)
  //        {
  //            return ((Module[])moduleMap.values().toArray(new Module[moduleMap.size()]));
  //        }
  //    }


  // instance

  protected final String name;
  protected String description;

  protected boolean hot = false;
  protected long lastModified = 2808000000L;

  private AbstractWidget[] wimpz = new AbstractWidget[0];
  private int wimpzLength;
  private static final int WIMPZ_GROWTH = 1000;

  protected tv.sage.ModuleGroup myGroup;
  protected boolean batchLoad;

  public Module(String name)
  {
    this.name = name;

    description = name;
  }

  public void setModuleGroup(tv.sage.ModuleGroup inGroup)
  {
    myGroup = inGroup;
  }

  // This is used for XBMC skin loading to optimize performance and so there's
  // consistency when different users load the exact same XBMC files
  public void setBatchLoad(boolean x)
  {
    if (x)
    {
      backupUIDCount = 0;
    }
    batchLoad = x;
  }

  public String name()
  {
    return (name);
  }

  public String description()
  {
    return (description);
  }

  public synchronized long lastModified()
  {
    return (lastModified);
  }

  public synchronized void setChanged()
  {
    hot = true;

    lastModified = sage.Sage.time();
  }

  // This is for changes that we don't want to act like standard STV modifications; i.e. automatic imports
  public synchronized void forceLastModified(long modTime)
  {
    hot = false;
    lastModified = modTime;
  }

  public String toString()
  {
    return ("Module " + name + " Widgets=" + wimpzLength + " hot=" + hot + " lastModified=" + new java.util.Date(lastModified));
  }

  private boolean isValidSymbol(String s)
  {
    if (s == null || s.indexOf(':') != -1 || s.length() < 6 || (myGroup != null && myGroup.symbolMap.containsKey(s)))
      return false;
    else
      return true;
  }

  private String backupUIPrefix = "XBMC";
  private long backupUIDCount;
  public String getUIPrefix()
  {
    String uiPrefix;
    if (batchLoad || sage.Sage.getRawProperties() == null)
      uiPrefix = backupUIPrefix;
    else
      uiPrefix = sage.Sage.get("studio/custom_ui_prefix", "");
    if (uiPrefix.length() == 0)
    {
      for (int j = 0; j < 5; j++)
      {
        uiPrefix = uiPrefix + (char)(((int)(Math.random() * 26)) + 'A');
      }
      if (sage.Sage.getRawProperties() == null)
        backupUIPrefix = uiPrefix;
      else
        sage.Sage.put("studio/custom_ui_prefix", uiPrefix);
    }
    return uiPrefix;
  }

  public String getUID()
  {
    long uidCount = backupUIDCount;
    if (!batchLoad && sage.Sage.getRawProperties() != null)
      uidCount = sage.Sage.getLong("studio/widget_uid_counter", 1);
    String rv;
    do
    {
      uidCount++;
      rv = getUIPrefix() + "-" + (uidCount - 1);
    } while (myGroup != null && myGroup.symbolMap.containsKey(rv));
    if (!batchLoad && sage.Sage.getRawProperties() != null)
      sage.Sage.putLong("studio/widget_uid_counter", uidCount);
    else
      backupUIDCount = uidCount;
    return rv;
  }

  public sage.Widget getWidgetForId(int id)
  {
    // return null if not found

    if (id >= 0 && id < wimpzLength)
    {
      return (wimpz[id]); // 601 could be null here too
    }

    return (null);
  }

  public sage.Widget addWidget(final byte type, String symbol)
  {
    //        if (sage.Sage.DBG) Log.ger.fine("addWidget " + sage.Widget.TYPES[type]);

    synchronized (this)
    {
      final int index = wimpzLength;

      RawWidget rawWidget = new RawWidget()
      {
        public int index()
        { return (index); }

        public byte type()
        { return (type); }
        public String name()
        { return (""); }
        public java.util.Properties properties()
        { return (new java.util.Properties()); }

        public int[] contentz()
        { return (new int[0]); }
        public int[] containerz()
        { return (new int[0]); }

        public String symbol()
        { return (null); }
      };

      AbstractWidget widget = AbstractWidget.create(rawWidget);
      if (!isValidSymbol(symbol))
      {
        widget.symbol = getUID();
      }
      else
        widget.symbol = symbol;

      widget.setCC(rawWidget, this, wimpz);

      if (wimpzLength == wimpz.length)
      {
        AbstractWidget[] newWimpz = new AbstractWidget[wimpz.length + WIMPZ_GROWTH];

        System.arraycopy(wimpz, 0, newWimpz, 0, wimpzLength);

        newWimpz[wimpzLength++] = widget;

        wimpz = newWimpz;
      }
      else
        wimpz[wimpzLength++] = widget;

      setChanged();

      return (widget);
    }
  }

  public void removeWidget(sage.Widget widget)
  {
    //        if (sage.Sage.DBG) Log.ger.fine("removeWidget " + widget);

    synchronized (this)
    {
      // 601 HANDLE Proxy?!?
      GenericWidget gwidget = (GenericWidget)widget;

      int index = gwidget.index();

      if (wimpz[index] == gwidget)
      {
        wimpz[index] = null;

        AbstractWidget[] contz = gwidget.contentz();
        for (int i = 0; i < contz.length; i++)
        {
          gwidget.discontent(contz[i]);
        }

        AbstractWidget[] conrz = gwidget.containerz();
        for (int i = 0; i < conrz.length; i++)
        {
          ((GenericWidget)conrz[i]).discontent(gwidget);
        }
      }

      setChanged();
    }
  }

  public synchronized sage.Widget kloneWidget(sage.Widget widget)
  {
    AbstractWidget klone = (AbstractWidget)addWidget(widget.type(), null);

    klone.setName(widget.getUntranslatedName());

    String[] valuez = ((AbstractWidget)widget).getPropertyValues();
    if (valuez != null)
    {
      for (byte prop = 0; prop < sage.Widget.PROPS.length; prop++)
      {
        if (valuez[prop] != null)
        {
          klone.setProperty(prop, valuez[prop]);
        }
      }
    }

    // 601 Fix for Andy's Copy Attribute / Expression Error
    if (klone instanceof Attribute)
    {
      klone.retranslate();
    }

    setChanged();

    return (klone);
  }

  public void resurrectWidget(sage.Widget widget)
  {
    if (widget == null) return;

    int index = widget.id();

    synchronized (this)
    {
      if (index >= 0 && index < wimpzLength && wimpz[index] == null)
      {
        wimpz[index] = (AbstractWidget)widget;
      }

      setChanged();
    }
  }

  public void retranslate()
  {
    tv.sage.mod.Translator.reset();

    synchronized (this)
    {
      for (int i = 0; i < wimpzLength; i++)
      {
        if (wimpz[i] != null)
        {
          wimpz[i].retranslate();
        }
      }
    }
  }

  protected void load(RawWidget[] rwz)
  {
    wimpz = new AbstractWidget[rwz.length + WIMPZ_GROWTH];
    wimpzLength = rwz.length;
    for (int i = 0; i < wimpzLength; i++)
    {
      wimpz[i] = AbstractWidget.create(rwz[i]);
      if (!isValidSymbol(wimpz[i].symbol()))
        wimpz[i].symbol = getUID();
    }

    for (int i = 0; i < wimpzLength; i++)
    {
      wimpz[i].setCC(rwz[i], this, wimpz);
    }
  }

  //    public void load(java.util.Vector wimpv)
  //    {
  //        wimpz = new AbstractWidget[wimpv.size()];
  //
  //        for (int i = 0; i < wimpz.length; i++)
  //        {
  //            wimpz[i] = (AbstractWidget)wimpv.get(i);
  //        }
  //    }

  /**
   * Add missing symbol for foreign references
   */
  protected void symbolize()
  {
    if (sage.Sage.DBG) System.out.println("Modules: symbolize " + this);

    sage.Widget[] wz = getWidgetz();

    for (int i = 0; i < wz.length; i++)
    {
      if (wz[i].getModule() != this) // stolen Widget
      {
        //                if (wz[i].symbol() == null)
        //                {
        //                    AbstractWidget aw = (AbstractWidget)wz[i];
        //
        //                    aw.symbol = aw.module.name() + ":" + tv.sage.Widget.TYPES[aw.type()] + "-" + aw.index();
        //                }
      }
      else // check the kids
      {
        //                if (wz[i].isType(tv.sage.Widget.MENU))
        //                {
        //                    Log.ger.fine("symbolize.check " + wz[i]);
        //                }

        sage.Widget[] conz = wz[i].contents();

        for (int j = 0; j < conz.length; j++)
        {
          if (conz[j].getModule() != this && conz[j].symbol() == null) // foreign && umsymboled
          {
            AbstractWidget aw = (AbstractWidget)conz[j];

            aw.symbol = aw.module.name() + ":" + sage.Widget.TYPES[aw.type()] + "-" + aw.index();

            aw.getModule().setChanged();
          }
        }

        //                conz = wz[i].containers();
        //
        //                for (int j = 0; j < conz.length; j++)
        //                {
        //                    if (conz[j].getModule() != mod && conz[j].symbol() == null) // foreign && umsymboled
        //                    {
        //                        AbstractWidget aw = (AbstractWidget)conz[j];
        //
        //                        aw.symbol = aw.module.name() + ":" + tv.sage.Widget.TYPES[aw.type()] + "-" + aw.index();
        //                    }
        //                }
      }
    }
  }

  private long cazshStamp;
  private sage.Widget[] cazsh = null;
  public synchronized sage.Widget[] getWidgetz()
  {
    return getWidgetz(false);
  }
  public synchronized sage.Widget[] getWidgetz(boolean dontCache)
  {
    long lastModTime = lastModified;
    if (cazsh != null && lastModTime <= cazshStamp)
      return cazsh;
    //return (wimpz);

    // 601 make a copy (w/o null)

    java.util.ArrayList awv = new java.util.ArrayList(wimpzLength);

    for (int i = 0; i < wimpzLength; i++)
    {
      if (wimpz[i] != null) awv.add(wimpz[i]);
    }
    if (!dontCache)
    {
      cazshStamp = lastModTime;
      return cazsh = (sage.Widget[])awv.toArray(new AbstractWidget[awv.size()]);
    }
    else
      return (sage.Widget[])awv.toArray(new AbstractWidget[awv.size()]);

    //        sage.Widget[] widgetz = new sage.Widget[wimpz.length];
    //
    //        System.arraycopy(wimpz, 0, widgetz, 0, wimpz.length);
    //
    //        return (widgetz);
  }
  private sage.Widget[][] tcazsh = new sage.Widget[sage.Widget.TYPES.length][];
  private long[] tcazshStamp = new long[sage.Widget.TYPES.length];
  public synchronized sage.Widget[] findWidgetz(byte type)
  {
    long lastModTime = lastModified;
    if (tcazsh[type] != null && lastModTime <= tcazshStamp[type]) return tcazsh[type];
    java.util.ArrayList awv = new java.util.ArrayList();

    for (int i = 0; i < wimpzLength; i++)
    {
      if (wimpz[i] != null && wimpz[i].type() == type) awv.add(wimpz[i]);
    }
    tcazshStamp[type] = lastModTime;
    return tcazsh[type] = ((sage.Widget[])awv.toArray(new AbstractWidget[awv.size()]));
  }


  //    public synchronized sage.Widget[] findOrphanz(byte type)
  //    {
  //        java.util.Vector wv = new java.util.Vector();
  //
  //        for (int i = 0; i < wimpz.length; i++)
  //        {
  //            AbstractWidget wimp = wimpz[i];
  //
  //            if (wimpz[i] != null && wimp.type() == type && wimp.containerz().length == 0) wv.add(wimp);
  //        }
  //
  //        return ((sage.Widget[])wv.toArray(new AbstractWidget[wv.size()]));
  //    }

  public Iterator iterator()
  {
    //return (new Iterator());
    return (new TypeIterator());
  }

  public abstract class Iterator implements java.util.Iterator
  {
    // 601 FIX for null in wimpz

    int index = 0;

    /**
     * @return <tt>true</tt> if the iterator has more elements.
     */
    public boolean hasNext()
    {
      return (index < wimpzLength);
    }

    /**
     * @return the next element in the iteration.
     * @exception NoSuchElementException iteration has no more elements.
     */
    public Object next()
    {
      return (nextWidget());
    }

    public sage.Widget nextWidget()
    {
      if (hasNext())
      {
        return (wimpz[index++]);
      }

      throw (new java.util.NoSuchElementException());
    }

    /**
     * @exception UnsupportedOperationException if the <tt>remove</tt>
     * operation is not supported by this Iterator.
     */
    public void remove()
    {
      throw (new UnsupportedOperationException());
    }
  }

  class TypeIterator extends Iterator
  {
    // 601 FIX order Menu, Theme, Hook, etc.

    int type = 0;

    sage.Widget[][] typedWidgetz = new sage.Widget[sage.Widget.TYPES.length][];

    TypeIterator()
    {
      for (int i = 0; i < typedWidgetz.length; i++)
      {
        //	public static final byte MENU = 0;
        //	public static final byte OPTIONSMENU = 1;
        //	public static final byte PANEL = 2;
        //	public static final byte THEME = 3;
        //	public static final byte ACTION = 4;
        //	public static final byte CONDITIONAL = 5;
        //	public static final byte BRANCH = 6;
        //	public static final byte LISTENER = 7;
        //	public static final byte ITEM = 8;
        //	public static final byte TABLE = 9;
        //	public static final byte TABLECOMPONENT = 10;
        //	public static final byte TEXT = 11;
        //	public static final byte IMAGE = 12;
        //	public static final byte TEXTINPUT = 13;
        //	public static final byte VIDEO = 14;
        //	public static final byte SHAPE = 15;
        //	public static final byte ATTRIBUTE = 16;
        //	public static final byte HOOK = 17;

        int j = i;
        switch (i) // swap to get MENU, THEME, HOOK, ...
        {
          case sage.Widget.OPTIONSMENU:
            j = sage.Widget.THEME; break; // 2nd
          case sage.Widget.PANEL:
            j = sage.Widget.HOOK; break; // 3rd

          case sage.Widget.THEME:
            j = sage.Widget.OPTIONSMENU; break;
          case sage.Widget.HOOK:
            j = sage.Widget.PANEL; break;
        }

        typedWidgetz[i] = findWidgetz((byte)j);
      }

      while (type < typedWidgetz.length && typedWidgetz[type].length == 0) // skip empties
      {
        type++;
      }
    }

    public boolean hasNext()
    {
      return (type < typedWidgetz.length && index < typedWidgetz[type].length);
    }

    public sage.Widget nextWidget()
    {
      if (hasNext())
      {
        try
        {
          return (typedWidgetz[type][index]);
        }
        finally // position to next valid or done
        {
          index++; // step index

          if (index == typedWidgetz[type].length) // step type
          {
            type++;

            while (type < typedWidgetz.length && typedWidgetz[type].length == 0) // skip empties
            {
              type++;
            }

            index = 0;
          }
        }
      }

      throw (new java.util.NoSuchElementException());
    }
  }

  public static Module loadXBMC(java.io.File xbmcSkinFile) throws tv.sage.SageException
  {
    // XBMC Skin loading
    if (sage.Sage.DBG) System.out.println("Modules: loadXBMC from " + xbmcSkinFile);
    try
    {
			sage.xbmc.XBMCSkinParser xbmc = new sage.xbmc.XBMCSkinParser(xbmcSkinFile.getParentFile(), null);
			Module mod = xbmc.getModule();

      mod.description = xbmcSkinFile.toString();
      mod.lastModified = xbmcSkinFile.lastModified();
      // patch index
      return (mod);
    }
    catch (Exception x)
    {
      throw (new tv.sage.SageException("Module.loadXBMC failure", x, tv.sage.SageExceptable.INTEGRITY));
    }
  }

  public static Module loadSTV(java.io.File stvFile) throws tv.sage.SageException
  {
    if (sage.Sage.DBG) System.out.println("Modules: loadSTV from " + stvFile);

    try
    {
      tv.sage.mod.STVReader stvReader = new tv.sage.mod.STVReader(stvFile);

      AbstractWidget[] widgz = stvReader.loadOpt();

      if (sage.Sage.DBG) System.out.println("Modules: loadSTV count = " + widgz.length);

      Module mod = new Module("default");

      mod.description = stvFile.toString();

      mod.wimpz = widgz;
      mod.wimpzLength = widgz.length;



      /*			tv.sage.mod.STVWidget[] swz = stvReader.load();

            // fixup index/contentz/containerz
            tv.sage.mod.STVWidget.fixCC(swz, stvReader.idMap);

            Log.ger.fine("loadSTV count = " + swz.length);

            Module mod = new Module("default");

            mod.description = stvFile.toString();

            mod.load(swz);
       */
      // patch index
      if (true)
      {
        //                AbstractWidget[] wimpz = new AbstractWidget[mod.wimpz.length];

        //              int index = 0;
        /*                TypeIterator ti = mod.new TypeIterator();
                while (ti.hasNext())
                {
                    wimpz[index] = (AbstractWidget)ti.next();
                    wimpz[index].index = index;

                    // Symbolize MENU
//                    if (wimpz[index].isType(sage.Widget.MENU))
//                    {
//                        wimpz[index].symbol = "MENU:" + wimpz[index].name();
//                    }

                    index++;
                }*/

        //            mod.wimpz = wimpz;
        for (int i = 0; i < mod.wimpz.length; i++)
        {
          mod.wimpz[i].index = i;
          mod.wimpz[i].module = mod;
        }
      }

      //            synchronized (moduleMap)
      //            {
      //                moduleMap.put(mod.name, mod);
      //            }

      return (mod);
    }
    catch (Exception x)
    {
      throw (new tv.sage.SageException("Module.loadSTV failure", x, tv.sage.SageExceptable.INTEGRITY));
    }
  }

  public static Module loadXML(tv.sage.ModuleGroup group, java.util.Map symbolMap, java.io.File xmlFile) throws tv.sage.SageException
  {
    if (sage.Sage.DBG) System.out.println("Modules: loadXML from " + xmlFile);

    try
    {
      tv.sage.xml.ModuleXMLParser mxp =  new tv.sage.xml.ModuleXMLParser(xmlFile);

      mxp.parse();

      if (sage.Sage.DBG) {
        System.out.println("Modules: loadXML count = " + mxp.widgetv.size());
        System.out.println("Modules: loadXML depth = " + mxp.maxDepth);
        System.out.println("Modules: loadXML anon  = " + mxp.anonCount);
        System.out.println("Modules: loadXML sym   = " + mxp.symbolv.size());
      }

      tv.sage.xml.ModuleXMLWidget[] mxwz =
          (tv.sage.xml.ModuleXMLWidget[])mxp.widgetv.toArray(new tv.sage.xml.ModuleXMLWidget[mxp.widgetv.size()]);

      tv.sage.xml.ModuleXMLWidget.fixCC(mxwz);

      Module mod = new Module(mxp.ModuleName);
      // We need to set this now so symbol validation works properly
      mod.setModuleGroup(group);

      mod.description = xmlFile.toString();

      mod.load(mxwz);

      // publish symbols
      tv.sage.xml.ModuleXMLWidget[] mxsz =
          (tv.sage.xml.ModuleXMLWidget[])mxp.symbolv.toArray(new tv.sage.xml.ModuleXMLWidget[mxp.symbolv.size()]);

      // Fix UID counting issues by analyzing the symbol map. This is for a mistake Jeff made where we reset the UID counter
      // on Andy & his machine.
      if (sage.Sage.getBoolean("repair_uid_prefix_count", false))
      {
        System.out.println("Analyzing widget symbols to fix uid count...");
        String uidPrefix = mod.getUIPrefix();
        long maxSym = 0;
        for (int i = 0; i < mxsz.length; i++)
        {
          String currSym = mxsz[i].symbol();
          if (currSym.startsWith(uidPrefix))
          {
            int idx = currSym.indexOf("-");
            long currID = Long.parseLong(currSym.substring(idx + 1));
            if (currID > maxSym)
              maxSym = currID;
          }
        }
        long currMax = sage.Sage.getLong("studio/widget_uid_counter", 0);
        if (maxSym > currMax)
        {
          System.out.println("Found max symbol: " + maxSym + " currCount=" + currMax);
          sage.Sage.putLong("studio/widget_uid_counter", maxSym);
        }
      }

      for (int i = 0; i < mxsz.length; i++)
      {
        if (mxsz[i].type() != -1)
        {
          Object previous = symbolMap.put(mxsz[i].symbol(), mod.wimpz[mxsz[i].index()]);

          if (previous != null)
          {
            if (previous == mod.wimpz[mxsz[i].index()])
            {
              if (sage.Sage.DBG) System.out.println("Modules: symbolMap redefinition " + mxsz[i].symbol());
            }
            else
            {
              if (sage.Sage.DBG) System.out.println("Modules: symbolMap duplicate " + mxsz[i].symbol() + " REPAIRING");
              symbolMap.put(mxsz[i].symbol(), previous);
              mod.wimpz[mxsz[i].index()].setSymbol(mod.getUID());
              symbolMap.put(mod.wimpz[mxsz[i].index()].symbol(), mod.wimpz[mxsz[i].index()]);
              if (sage.Sage.DBG) System.out.println("Modules: previous=" + previous + " new=" + mod.wimpz[mxsz[i].index()]);
            }
          }
        }
      }

      //            synchronized (moduleMap)
      //            {
      //                moduleMap.put(mod.name, mod);
      //            }


      return (mod);
    }
    catch (Exception x)
    {
      throw (new tv.sage.SageException("Module.loadXML failure", x, tv.sage.SageExceptable.INTEGRITY));
    }
  }

  /**
   * @return Map of (Widget, Integer newIndex) for breakpoints.
   */
  public java.util.Map saveXML(java.io.File xmlFile, String newDescription) throws tv.sage.SageException
  {
    if (sage.Sage.DBG) System.out.println("Modules: saveXML " + name + " to " + xmlFile);

    if (newDescription != null)
      description = newDescription;

    tv.sage.xml.ModuleXMLReader mxr = new tv.sage.xml.ModuleXMLReader(this);

    tv.sage.xml.AbstractXMLReader.transform(mxr, xmlFile);

    if (sage.Sage.DBG) System.out.println("Modules: pointed count = " + mxr.pointed.size());
    //        int count = 0;
    //        java.util.Iterator itr = mxr.pointed.entrySet().iterator();
    //        while (itr.hasNext() && count++ < 1000)
    //        {
    //            java.util.Map.Entry me = (java.util.Map.Entry)itr.next();
    //
    //            Log.ger.info("pointed " + ((sage.Widget)me.getKey()).id() + " = " + me.getValue());
    //        }
    //        Log.ger.info("pointed.");

    return (mxr.pointed);

    //        try
    //        {
    //            // where the xml goes
    //            javax.xml.transform.stream.StreamResult streamResult =
    //                new javax.xml.transform.stream.StreamResult(xmlFile);
    //
    //            // where it comes from
    //            org.xml.sax.XMLReader xmlReader = getXMLReader();
    //
    //            xmlReader.setFeature("http://xml.org/sax/features/namespaces", true);
    //            xmlReader.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
    //
    //            // 601 Java 1.4.2 requires non null InputSource
    //            org.xml.sax.InputSource inputSource = new org.xml.sax.InputSource();
    //
    //            javax.xml.transform.sax.SAXSource saxSource =
    //                new javax.xml.transform.sax.SAXSource(xmlReader, inputSource);
    //
    //            // send it
    //            javax.xml.transform.TransformerFactory transformerFactory =
    //                javax.xml.transform.TransformerFactory.newInstance();
    //
    //            javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
    //
    //            transformer.transform(saxSource, streamResult);
    //        }
    //        catch (org.xml.sax.SAXException sx)
    //        {
    //            throw (new tv.sage.SageException(sx, tv.sage.SageExceptable.LOCAL));
    //        }
    //        catch (javax.xml.transform.TransformerException tx)
    //        {
    //            throw (new tv.sage.SageException(tx, tv.sage.SageExceptable.LOCAL));
    //        }
  }

  public void importXML(java.util.Map symbolMap, java.io.File file, sage.UIManager uiMan) throws tv.sage.SageException
  {
    if (sage.Sage.DBG) System.out.println("Modules: importXML from " + file);

    RawWidget[] rwz = null;

    // load imports
    boolean isWIZ = tv.sage.ModuleGroup.isWIZFile(file);

    if (isWIZ) // Wizard STV(I)
    {
      try
      {
        tv.sage.mod.STVReader stvReader = new tv.sage.mod.STVReader(file);

        tv.sage.mod.STVWidget[] swz = stvReader.load();

        // fixup index/contentz/containerz
        tv.sage.mod.STVWidget.fixCC(swz, stvReader.idMap);

        rwz = swz;

        if (sage.Sage.DBG) System.out.println("Modules: importSTV count = " + swz.length);
      }
      catch (Exception x)
      {
        // 601 FIX

        x.printStackTrace();

        throw (new tv.sage.SageRuntimeException(x, tv.sage.SageExceptable.UNKNOWN));
      }
    }
    else // XML
    {
      try
      {
        tv.sage.xml.ModuleXMLParser mxp =  new tv.sage.xml.ModuleXMLParser(file);

        mxp.parse();

        tv.sage.xml.ModuleXMLWidget[] mxwz =
            (tv.sage.xml.ModuleXMLWidget[])mxp.widgetv.toArray(new tv.sage.xml.ModuleXMLWidget[mxp.widgetv.size()]);

        tv.sage.xml.ModuleXMLWidget.fixCC(mxwz);

        rwz = mxwz;

        if (sage.Sage.DBG) {
          System.out.println("Modules: importXML count = " + mxp.widgetv.size());
          System.out.println("Modules: importXML depth = " + mxp.maxDepth);
          System.out.println("Modules: importXML anon  = " + mxp.anonCount);
          System.out.println("Modules: importXML sym   = " + mxp.symbolv.size());
        }
      }
      catch (Exception x)
      {
        // 601 FIX

        x.printStackTrace();

        throw (new tv.sage.SageRuntimeException(x, tv.sage.SageExceptable.UNKNOWN));
      }
    }


    AbstractWidget[] importz = new AbstractWidget[rwz.length];

    // create
    for (int i = 0; i < importz.length; i++)
    {
      importz[i] = AbstractWidget.create(rwz[i]);
      if (!isValidSymbol(importz[i].symbol()))
      {
        importz[i].symbol = getUID();
      }
      Object previous = symbolMap.put(importz[i].symbol(), importz[i]);

      if (previous != null)
      {
        if (previous == importz[i])
        {
          if (sage.Sage.DBG) System.out.println("Modules: symbolMap redefinition " + importz[i].symbol());
        }
        else
        {
          if (sage.Sage.DBG) System.out.println("Modules: symbolMap duplicate " + importz[i].symbol());
        }
      }
    }

    // convert c/c indexes to Objects
    for (int i = 0; i < importz.length; i++)
    {
      importz[i].setCC(rwz[i], this, importz);
    }


    // combine
    AbstractWidget[] oldWimpz;

    synchronized (this)
    {
      oldWimpz = wimpz;

      AbstractWidget[] newWimpz = new AbstractWidget[wimpz.length + rwz.length];

      System.arraycopy(wimpz, 0, newWimpz, 0, wimpzLength);
      System.arraycopy(importz, 0, newWimpz, wimpzLength, importz.length);

      // patch indexes (0...n, 0...m) ==> (0...n+m)
      for (int i = wimpzLength; i < wimpzLength + importz.length; i++)
      {
        newWimpz[i].index = i;
      }

      wimpz = newWimpz;
      wimpzLength += importz.length;

      setChanged();
    }

    // process hooks
    // 601
    if (uiMan != null)
    {
      // Look for an STVImported hook in the imported widgets
      java.util.ArrayList stvImportHooks = new java.util.ArrayList(); // of AbstractWidget
      for (int i = 0; i < importz.length; i++)
      {
        if (importz[i] != null && importz[i].isType(sage.Widget.HOOK) && importz[i].name().equals("STVImported"))
        {
          stvImportHooks.add(importz[i]);
        }
      }
      for (int i = 0; i < stvImportHooks.size(); i++)
      {
        if (sage.Sage.DBG) System.out.println("Module processing STVImported Hook");

        Object hookRv = sage.Catbert.processHookDirectly((sage.Widget) stvImportHooks.get(i),
            new Object[] { oldWimpz, importz }, uiMan, null);
        if (sage.Catbert.AUTO_CLEANUP_STV_IMPORTED_HOOK.equals(hookRv))
        {
          if (sage.Sage.DBG) System.out.println("Auto-cleanup of STVImported hook is running...");
          // Find all Widgets that are recursive children of this hook
          java.util.Set killerSet = new java.util.HashSet();
          java.util.ArrayList checkMeStill = new java.util.ArrayList();
          checkMeStill.add(stvImportHooks.get(i));
          killerSet.add(stvImportHooks.get(i));
          while (!checkMeStill.isEmpty())
          {
            sage.Widget currWidg = (sage.Widget) checkMeStill.remove(0);
            sage.Widget[] kids = currWidg.contents();
            for (int j = 0; j < kids.length; j++)
            {
              if (killerSet.add(kids[j]))
                checkMeStill.add(kids[j]);
            }
          }
          if (sage.Sage.DBG) System.out.println("Cleaning up " + killerSet.size() + " widgets from import");
          java.util.Iterator walker = killerSet.iterator();
          while (walker.hasNext())
            removeWidget((sage.Widget) walker.next());
        }
      }
    }
  }

  public void exportXML(java.util.Collection widgets, java.io.File xmlFile) throws tv.sage.SageException
  {
    if (sage.Sage.DBG) System.out.println("Modules: exportXML " + widgets.size() + " widgets of " + name + " to " + xmlFile);

    String prefix = xmlFile.getName();

    // drop .xml
    int indexOf = prefix.toLowerCase().lastIndexOf(".xml");
    if (indexOf > 0) prefix = prefix.substring(0, indexOf);

    tv.sage.xml.AbstractXMLReader.transform(new tv.sage.xml.ExportXMLReader(widgets, prefix), xmlFile);
  }

  public java.util.Set find(String name)
  {
    throw new UnsupportedOperationException();
  }
}
