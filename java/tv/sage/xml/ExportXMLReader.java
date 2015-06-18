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

/**
 * @author 601
 */
public class ExportXMLReader extends AbstractXMLReader
{
  // of Widget
  protected final java.util.Collection exports;

  protected final String idPrefix;

  // of Widget, definition generated
  protected final java.util.Set defined = new java.util.HashSet();

  //    // of Widget, undefined forward references
  //    protected final java.util.Set forwards = new java.util.HashSet();


  public ExportXMLReader(java.util.Collection exports, String idPrefix)
  {
    this.exports = exports;

    this.idPrefix = (idPrefix == null ? "" : idPrefix);

    this.persistentPriRefs = false;
  }

  /**
   * @return true to generate reference, define later at root level
   */
  boolean forward(sage.Widget w)
  {
    return (w.isType(sage.Widget.MENU) || w.isType(sage.Widget.THEME));
  }

  /**
   * Generate SAX events for a {@link tv.sage.Widget}.
   */
  public void generate(sage.Widget w, boolean root) throws org.xml.sax.SAXException
  {
    String name = w.getUntranslatedName();
    String tag = sage.Widget.TYPES[w.type()];
    String id = idPrefix.concat(":").concat(Integer.toString(w.id()));

    boolean define = false; // generate definition (true) or reference (false)

    if (forward(w) && !root)
    {
      // 601 by luck, explicit passes at root for MENU and THEME will get them defined.

      //            if (!defined.contains(w))
      //            {
      //                forwards.add(w);
      //            }
    }
    else
    {
      define = defined.add(w);
    }

    if (define)
    {
      sage.Widget[] canz = w.containers();
      boolean anonymous = (canz.length == 0);
      if (!anonymous && canz.length == 1 && !forward(w))
      {
        anonymous = (defined.contains(canz[0]));
      }

      definition(tag, anonymous ? null : id, name, w.symbol(), false);

      String[] pvz = ((tv.sage.mod.AbstractWidget)w).getPropertyValues();
      if (pvz != null)
      {
        for (int j = 0; j < sage.Widget.PROPS.length; j++)
        {
          String value = pvz[j];
          if (value != null)
          {
            property(sage.Widget.PROPS[j], value);
          }
        }
      }

      // recurse contents
      sage.Widget[] conz = w.contents();

      for (int k = 0; k < conz.length; k++)
      {
        if (exports.contains(conz[k]) || defined.contains(conz[k]))
        {
          generate(conz[k], false);
        }
      }

      end(true);
    }
    else if (!root) // reference
    {
      // trim name for references
      if (name != null && name.length() > 24) name = name.substring(0, 22) + "...";

      reference(tag, id, name);
    }
  }

  public void generate() throws org.xml.sax.SAXException
  {
    handler.startDocument();

    definition("Module", null, idPrefix, null, false);

    // MENU first
    java.util.Iterator itr = exports.iterator();
    while (itr.hasNext())
    {
      sage.Widget w = (sage.Widget)itr.next();

      if (w.isType(sage.Widget.MENU))
      {
        generate(w, true);
      }
    }

    exports.removeAll(defined);

    // THEME next
    itr = exports.iterator();
    while (itr.hasNext())
    {
      sage.Widget w = (sage.Widget)itr.next();

      if (w.isType(sage.Widget.THEME))
      {
        generate(w, true);
      }
    }

    exports.removeAll(defined);

    //        // HOOK next
    //        itr = exports.iterator();
    //        while (itr.hasNext())
    //        {
    //            sage.Widget w = (sage.Widget)itr.next();
    //
    //            if (w.isType(sage.Widget.HOOK))
    //            {
    //                generate(w, true);
    //            }
    //        }
    //
    //        exports.removeAll(defined);

    // the rest, if any
    // 601 debug
    if (sage.Sage.DBG) System.out.println("Modules: exports remaining = " + exports.size());

    itr = exports.iterator();
    while (itr.hasNext())
    {
      sage.Widget w = (sage.Widget)itr.next();

      generate(w, true);
    }

    exports.removeAll(defined);

    // 601 debug
    if (sage.Sage.DBG) System.out.println("Modules: exports remaining = " + exports.size());

    end(true);

    handler.endDocument();
  }
}
