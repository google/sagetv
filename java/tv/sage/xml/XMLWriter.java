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
 * @author 601
 */
public class XMLWriter
{
  // instance

  final java.io.File xmlFile;

  public XMLWriter(java.io.File xmlFile)
  {
    this.xmlFile = xmlFile;
  }

  /**
   * write all Widgets
   */
  public void write() throws java.io.IOException
  {
    // 601 sage.Widget[] widgetz = sage.Wizard.getInstance().getWidgets();
    // 601 sage.Widget[] widgetz = sage.Wizard.getInstance().getWidgets601();
    sage.Widget[] widgetz = sage.WidgetMeta.getWidgets();

    org.xml.sax.XMLReader stvReader = new STVReader(widgetz);

    try
    {
      write(stvReader);
    }
    catch (Exception x)
    {
      // 601 debug
      x.printStackTrace();

      java.io.IOException iox = new java.io.IOException("write XML");

      iox.initCause(x);

      throw (iox);
    }
  }

  protected void write(org.xml.sax.XMLReader xmlReader) throws tv.sage.SageException
  {
    try
    {
      // where the xml goes
      javax.xml.transform.stream.StreamResult streamResult =
          new javax.xml.transform.stream.StreamResult(xmlFile);

      xmlReader.setFeature("http://xml.org/sax/features/namespaces", true);
      xmlReader.setFeature("http://xml.org/sax/features/namespace-prefixes", false);

      // 601 Java 1.4.2 requires non null InputSource
      org.xml.sax.InputSource inputSource = new org.xml.sax.InputSource();

      javax.xml.transform.sax.SAXSource saxSource =
          new javax.xml.transform.sax.SAXSource(xmlReader, inputSource);

      // send it
      javax.xml.transform.TransformerFactory transformerFactory =
          javax.xml.transform.TransformerFactory.newInstance();

      javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();

      transformer.transform(saxSource, streamResult);
    }
    catch (org.xml.sax.SAXException sx)
    {
      throw (new tv.sage.SageException(sx, tv.sage.SageExceptable.LOCAL));
    }
    catch (javax.xml.transform.TransformerException tx)
    {
      throw (new tv.sage.SageException(tx, tv.sage.SageExceptable.LOCAL));
    }
  }


  static class STVReader extends AbstractXMLReader
  {
    // undefined forward references
    protected final java.util.Set forwards = new java.util.HashSet();

    final sage.Widget[] widgetz;

    STVReader(sage.Widget[] widgetz)
    {
      this.widgetz = widgetz;
    }

    // defined Widget
    public final java.util.Set widgetSet = new java.util.HashSet(); // Widget


    /**
     * @return true to generate reference, define later
     */
    boolean forward(sage.Widget w)
    {
      return (w.isType(sage.Widget.MENU) || w.isType(sage.Widget.THEME));
    }

    /**
     * Generate SAX events for a {@link tv.sage.Wiget}.
     */
    public void generate(sage.Widget w, boolean root) throws org.xml.sax.SAXException
    {
      // 601 String symbol = w.symbol();
      String symbol = null;

      if (forward(w)) // make up a symbol
      {
        String name = w.getUntranslatedName().trim();

        if (name.length() > 0)
        {
          StringBuffer sb = new StringBuffer().append(w.TYPES[w.type()]).append(':');

          for (int i = 0; i < name.length(); i++)
          {
            char ch = name.charAt(i);

            if (Character.isJavaIdentifierPart(ch))
            {
              sb.append(ch);
            }
          }

          symbol = sb.toString();
        }
      }

      String tag = sage.Widget.TYPES[w.type()];
      String id = String.valueOf(w.id());

      boolean define = false;

      if (forward(w) && !root) // delay define until we're at root level
      {
        if (!widgetSet.contains(w))
        {
          forwards.add(w);
        }
      }
      else
      {
        define = widgetSet.add(w);
      }

      if (define) // full definition
      {
        boolean removed = forwards.remove(w); // 601 just in case

        // 601 if (removed) tv.sage.mod.Log.ger.warning("forward.removed " + w);
        //if (removed) System.out.println("forward.removed " + w);

        sage.Widget[] conz = w.containers();

        // 601 improve (handle proxy)
        boolean anonymous = (conz.length == 0);
        if (!anonymous && conz.length == 1 && !forward(w))
        {
          anonymous = (widgetSet.contains(conz[0]));
        }

        // 601 really get untranslated name for ITEM/TEXT/ACTION

        definition(tag, anonymous ? null : id, w.getUntranslatedName(), symbol, false);

        // 601 mid-way
        //                //boolean proped = false;
        //                for (int j = 0; j < sage.Widget.PROPS.length; j++)
        //                {
        //                    String name = sage.Widget.PROPS[j];
        //
        //                    // 601 really get untranslated ATTRIBUTE.VALUE
        //
        //                    // 601 caution, needs interface access
        //
        //                    // 601 String value = ((tv.sage.mod.AbstractWidget)w).properties().getProperty(name);
        //                    String value = ((tv.sage.mod.AbstractWidget)w).properties().getProperty(name);
        //
        //                    if (value != null)
        //                    {
        //                        property(name, value);
        //
        //                        //proped = true;
        //                    }
        //                }

        //                if (tv.sage.ModuleManager.isModular)
        //                {

        String [] pvz = ((AbstractWidget)w).getPropertyValues();
        if (pvz != null)
        {
          for (int i = 0; i < sage.Widget.PROPS.length; i++)
          {
            String value = pvz[i];

            if (value != null) property(sage.Widget.PROPS[i], value);
          }
        }

        //                }
        //                else
        //                {
        //                    // of String[] { prop-name, untrans-prop-value }
        //                    java.util.Vector propv = ((sage.WidgetImp)w).getPropv();
        //                    // 601 java.util.Vector propv = w.getPropv();
        //                    if (propv != null && propv.size() > 0)
        //                    {
        //                        java.util.Iterator itr = propv.iterator();
        //                        while (itr.hasNext())
        //                        {
        //                            String[] nvz = (String[])itr.next();
        //
        //                            property(nvz[0], nvz[1]);
        //                        }
        //                    }
        //                }

        // recurse contents
        conz = w.contents();

        for (int k = 0; k < conz.length; k++)
        {
          //                    if (conz[k].getModule() == module) // recurse
          {
            generate(conz[k], false);
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

      definition("Module", null, "default", null, false);

      //            tv.sage.mod.Module.Iterator itr = module.iterator();
      //            while (itr.hasNext())
      for (int i = 0; i < widgetz.length; i++)
      {
        //                sage.Widget w = itr.nextWidget();
        sage.Widget w = widgetz[i];

        if (!widgetSet.contains(w)) // not defined by earlier recursion
        {
          generate(w, true);
        }
        else if (forwards.contains(w))
        {
          forwards.remove(w);

          generate(w, true);
        }
      }

      if (forwards.size() > 0) // outstanding forward references
      {
        // 601 tv.sage.mod.Log.ger.warning("outstanding forwards " + forwards.size());
        System.out.println("outstanding forwards " + forwards.size());

        sage.Widget[] forwardz = (sage.Widget[])forwards.toArray(new sage.Widget[forwards.size()]);

        forwards.clear();

        for (int i = 0; i < forwardz.length; i++)
        {
          // 601 tv.sage.mod.Log.ger.warning("forward " + forwardz[i]);
          System.out.println("forward " + forwardz[i]);

          generate(forwardz[i], true);
        }
      }

      end(true);

      handler.endDocument();
    }
  }


  protected static final char[] iwsCharz = new char[100];
  static {
    iwsCharz[0] = '\n'; for (int i = 1; i < iwsCharz.length; i++) iwsCharz[i] = ' '; }

  /**
   * A handy abstraction of {@link org.xml.sax.XMLReader}.
   * Used to generate SAX events representing a collection of Widget.
   * @author 601
   */
  static abstract class AbstractXMLReader implements org.xml.sax.XMLReader
  {
    protected org.xml.sax.ContentHandler handler;
    protected org.xml.sax.DTDHandler dTDHandler;

    /**
     * these are important:
     * "http://xml.org/sax/features/namespaces", true
     * "http://xml.org/sax/features/namespace-prefixes", false
     */
    protected java.util.Properties featureProperties = new java.util.Properties();

    protected final java.util.Stack stack = new java.util.Stack();

    protected final String nsuri = "urn:tv.sage/stv";

    protected volatile int lastPush = -1;

    void newLine(int indent) throws org.xml.sax.SAXException
    {
      if (indent + 1 > iwsCharz.length) indent = 0;

      handler.ignorableWhitespace(iwsCharz, 0, indent + 1);
    }

    protected void definition(String tag, String id, String name, String sym, boolean undefined) throws org.xml.sax.SAXException
    {
      //System.out.println("definition " + tag + id + name + sym);

      org.xml.sax.helpers.AttributesImpl attr = new org.xml.sax.helpers.AttributesImpl();

      if (id == null && name == null && sym == null) throw (new org.xml.sax.SAXException("definition:  all null"));

      if (id != null) attr.addAttribute(nsuri, "ID", "ID", "", id);
      if (name != null) attr.addAttribute(nsuri, "Name", "Name", "", name);
      if (sym != null) attr.addAttribute(nsuri, "Sym", "Sym", "", sym);
      //if (undefined) attr.addAttribute(nsuri, "Proxy", "Proxy", "", name);

      newLine(stack.size());
      handler.startElement(nsuri, tag, tag, attr);

      stack.push(tag);

      lastPush = stack.size();
    }

    //    protected void anonymous(String tag, String name, String sym, boolean undefined) throws org.xml.sax.SAXException
    //    {
    //        org.xml.sax.helpers.AttributesImpl attr = new org.xml.sax.helpers.AttributesImpl();
    //
    //        if (name != null) attr.addAttribute(nsuri, "Name", "Name", "", name);
    //        if (sym != null) attr.addAttribute(nsuri, "Sym", "Sym", "", sym);
    //
    //        newLine(stack.size());
    //        handler.startElement(nsuri, tag, tag, attr);
    //
    //        stack.push(tag);
    //
    //        lastPush = stack.size();
    //    }

    /**
     * generate a reference only
     */
    protected void reference(String tag, String id, String name) throws org.xml.sax.SAXException
    {
      org.xml.sax.helpers.AttributesImpl attr = new org.xml.sax.helpers.AttributesImpl();

      // 601 integrity?
      if (id == null && name == null) throw (new org.xml.sax.SAXException("reference:  all null"));

      if (id != null) attr.addAttribute(nsuri, "Ref", "Ref", "", id);
      if (name != null) attr.addAttribute(nsuri, "Name", "Name", "", name);

      int indent = stack.size();

      newLine(indent);
      handler.startElement(nsuri, tag, tag, attr);
      handler.endElement(nsuri, tag, tag);

      lastPush = -1;
    }

    /**
     * generate a protected
     */
    protected void property(String name, String value) throws org.xml.sax.SAXException
    {
      org.xml.sax.helpers.AttributesImpl attr = new org.xml.sax.helpers.AttributesImpl();

      int indent = stack.size();

      newLine(indent);
      handler.startElement(nsuri, name, name, attr);

      char[] chz = value.toCharArray();
      handler.characters(chz, 0, chz.length);

      handler.endElement(nsuri, name, name);

      lastPush = -1;
    }

    protected void end(boolean dent) throws org.xml.sax.SAXException
    {
      int level = stack.size();

      String tag = (String)stack.pop();

      if (dent && level != lastPush)
      {
        newLine(stack.size());
      }

      handler.endElement(nsuri, tag, tag);

      lastPush = -1;
    }

    protected void comment(String comment, boolean dent) throws org.xml.sax.SAXException
    {
      if (handler instanceof org.xml.sax.ext.LexicalHandler)
      {
        if (dent)
        {
          int indent = stack.size();

          newLine(indent);
        }

        char[] chz = comment.toCharArray();

        ((org.xml.sax.ext.LexicalHandler)handler).comment(chz, 0, chz.length);

        lastPush = -1;
      }
    }

    /**
     * generate SAX events via handler or helper methods
     */
    public abstract void generate() throws org.xml.sax.SAXException;


    public void parse(String str) throws java.io.IOException, org.xml.sax.SAXException
    {
      if (handler != null)
      {
        generate();
      }
      else
      {
        throw (new org.xml.sax.SAXException("No content handler"));
      }
    }

    public void parse(org.xml.sax.InputSource inputSource) throws java.io.IOException, org.xml.sax.SAXException
    {
      if (handler != null)
      {
        generate();
      }
      else
      {
        throw (new org.xml.sax.SAXException("No content handler"));
      }
    }


    // get

    public org.xml.sax.ContentHandler getContentHandler()
    {
      return (handler);
    }

    public org.xml.sax.DTDHandler getDTDHandler()
    {
      return (dTDHandler);
    }

    public org.xml.sax.EntityResolver getEntityResolver()
    {
      throw new UnsupportedOperationException();
    }

    public org.xml.sax.ErrorHandler getErrorHandler()
    {
      throw new UnsupportedOperationException();
    }

    public boolean getFeature(String str) throws org.xml.sax.SAXNotRecognizedException, org.xml.sax.SAXNotSupportedException
    {
      String value = featureProperties.getProperty(str);

      if (value != null)
      {
        return (value.equalsIgnoreCase("true"));
      }
      else
      {
        throw (new org.xml.sax.SAXNotRecognizedException(str));
      }

      //throw (new org.xml.sax.SAXNotSupportedException("getFeature"));
    }

    public Object getProperty(String str) throws org.xml.sax.SAXNotRecognizedException, org.xml.sax.SAXNotSupportedException
    {
      throw (new org.xml.sax.SAXNotSupportedException("getProperty"));
    }


    // set

    public void setContentHandler(org.xml.sax.ContentHandler contentHandler)
    {
      handler = contentHandler;
    }

    public void setDTDHandler(org.xml.sax.DTDHandler dTDHandler)
    {
      this.dTDHandler = dTDHandler;
    }

    public void setEntityResolver(org.xml.sax.EntityResolver entityResolver)
    {
      throw new UnsupportedOperationException();
    }

    public void setErrorHandler(org.xml.sax.ErrorHandler errorHandler)
    {
      throw new UnsupportedOperationException();
    }

    public void setFeature(String str, boolean param) throws org.xml.sax.SAXNotRecognizedException, org.xml.sax.SAXNotSupportedException
    {
      featureProperties.setProperty(str, param ? "true" : "false");

      // throw (new org.xml.sax.SAXNotSupportedException("setFeature"));
    }

    public void setProperty(String str, Object obj) throws org.xml.sax.SAXNotRecognizedException, org.xml.sax.SAXNotSupportedException
    {
      // 601 Java 1.4.2 can't handle org.xml.sax.SAXNotSupportedException
      // throw (new org.xml.sax.SAXNotSupportedException("setProperty"));

      // System.out.println("AbstractXMLReader.setProperty(" + str + ", " + obj + ")");
    }
  }
}
