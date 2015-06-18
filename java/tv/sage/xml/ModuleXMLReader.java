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
package tv.sage.xml;

import tv.sage.mod.AbstractWidget;

/**
 * Used to generate an XML file.
 * XMLReader is a confusing name. This class Read's a module
 * generating SAX events which can be output to an XML file.
 * <br>
 * @author 601
 */
public class ModuleXMLReader extends AbstractXMLReader
{
  protected final tv.sage.mod.Module module;
  private boolean v6CompatibleSTV = false;

  // of Widget
  public final java.util.Set defined = new java.util.HashSet();

  // of Widget, undefined forward references
  protected final java.util.Set forwards = new java.util.HashSet();

  // of Widget, an XML Element has been generated
  public final java.util.Set tagged = new java.util.HashSet();

  // of (Widget, Integer new index)
  public final java.util.Map pointed = new java.util.HashMap();

  public ModuleXMLReader(tv.sage.mod.Module module)
  {
    this.module = module;
    this.persistentPriRefs = true;
    this.v6CompatibleSTV = sage.Sage.getBoolean("studio/save_v6_compatible_stvs", false);
  }


  /**
   * @return true to generate reference, define later
   */
  boolean forward(sage.Widget w)
  {
    return (w.isType(sage.Widget.MENU)/* || w.isType(sage.Widget.THEME)*/);
    //        return (w.isType(sage.Widget.MENU) || w.isType(sage.Widget.THEME));
  }

  /**
   * Generate SAX events for a {@link tv.sage.Widget}.
   */
  public void generate(sage.Widget parentW, sage.Widget w, boolean root) throws org.xml.sax.SAXException
  {
    boolean foreign = w.getModule() != module;

    boolean proxy = ((AbstractWidget)w).isProxy();

    String symbol = w.symbol();
    String tag = (proxy || foreign ? "Proxy" : sage.Widget.TYPES[w.type()]);
    String id = String.valueOf(w.id());

    if (foreign)
    {
      id = w.getModule().name() + id;

      if (symbol == null)
      {
        //symbol = id;

        throw (new org.xml.sax.SAXException("Widget w/o symbol " + w ));
      }

      //            if (root)
      //            {
      //                throw (new org.xml.sax.SAXException("foreign root! " + w ));
      //            }
    }

    boolean define = false;

    sage.Widget[] conz = w.containers();

    boolean primary = parentW == null;
    if (!primary)
    {
      if (conz.length > 0 && conz[0] == parentW)
        primary = true;
    }

    if ((!primary || forward(w)) && !root) // delay define until we're at root level
      //        if (forward(w) && !root) // delay define until we're at root level
    {
      if (!defined.contains(w))
      {
        forwards.add(w);
      }
    }
    else
    {
      define = defined.add(w);
    }

    // now either def or ref
    if (tagged.add(w)) // first time
    {
      int index = tagged.size() - 1;

      if (w.id() != index)
      {
        pointed.put(w, new Integer(index));
      }
    }

    if (define) // full definition
    {
      boolean removed = forwards.remove(w); // 601 just in case

      //if (removed) tv.sage.mod.Log.ger.warning("forward.removed " + w);

      // 601 improve (handle proxy)
      boolean anonymous = (conz.length == 0);
      if (!anonymous && conz.length == 1 && !forward(w))
      {
        anonymous = (defined.contains(conz[0]));
      }

      // 601 really get untranslated name for ITEM/TEXT/ACTION

      definition(tag, anonymous ? null : id, w.getUntranslatedName(), symbol, false);

      if (!proxy && !foreign)
      {
        String[] pvz = ((tv.sage.mod.AbstractWidget)w).getPropertyValues();
        if (pvz != null)
        {
          // To make the STV V6 compatible; we just need to not write properties greater than index 72
          for (int j = 0; j < (v6CompatibleSTV ? 73 : sage.Widget.PROPS.length); j++)
          {
            String value = pvz[j];

            if (value != null)
            {
              property(sage.Widget.PROPS[j], value);
            }
          }
        }
      }

      if (!proxy && !foreign)
      {
        // recurse contents
        conz = w.contents();

        for (int k = 0; k < conz.length; k++)
        {
          //                    if (conz[k].getModule() == module) // recurse
          {
            generate(w, conz[k], false);
          }
        }
      }

      end(true);
    }
    else // just a reference
    {
      String name = w.getUntranslatedName();

      if (name != null && name.length() > 24) name = name.substring(0, 22) + "...";

      reference(tag, id, name);
    }
  }

  public void generate() throws org.xml.sax.SAXException
  {
    final int limit = Integer.MAX_VALUE;

    // comment(" pre-startDocument ", true);

    handler.startDocument();

    definition("Module", null, module.name(), null, false);

    tv.sage.mod.Module.Iterator itr = module.iterator();
    while (itr.hasNext())
    {
      sage.Widget w = itr.nextWidget();

      // 601
      //            if (w.getModule() == module && !defined.contains(w)) // not defined by earlier recursion
      if (!defined.contains(w)) // not defined by earlier recursion
      {
        generate(null, w, true);
      }
      else if (forwards.contains(w))
      {
        forwards.remove(w);

        generate(null, w, true);
      }
    }

    if (forwards.size() > 0) // outstanding forward references
    {
      if (sage.Sage.DBG) System.out.println("Modules: outstanding forwards " + forwards.size());

      sage.Widget[] forwardz = (sage.Widget[])forwards.toArray(new sage.Widget[forwards.size()]);

      forwards.clear();

      for (int i = 0; i < forwardz.length; i++)
      {
        if (sage.Sage.DBG) System.out.println("Modules: forward " + forwardz[i]);

        generate(null, forwardz[i], true);
      }
    }

    end(true);

    handler.endDocument();
  }
}
