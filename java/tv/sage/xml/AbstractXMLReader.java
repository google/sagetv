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
 * A handy abstraction of {@link org.xml.sax.XMLReader}.
 * Used to generate SAX events representing a collection of Widget.
 * @author 601
 */
public abstract class AbstractXMLReader implements org.xml.sax.XMLReader
{
  protected org.xml.sax.ContentHandler handler;
  protected org.xml.sax.DTDHandler dTDHandler;

  /**
   * these are important:
   * "http://xml.org/sax/features/namespaces", true
   * "http://xml.org/sax/features/namespace-prefixes", false
   */
  protected java.util.Properties featureProperties = new java.util.Properties();

  // of String, Element name
  protected final java.util.Stack stack = new java.util.Stack();

  protected static final char[] iwsCharz = new char[100];

  static {
    iwsCharz[0] = '\n'; for (int i = 1; i < iwsCharz.length; i++) iwsCharz[i] = ' '; }

  protected final String nsuri = "urn:tv.sage/stv";

  // stack level of open tag, potential special empty <tag/>
  protected volatile int lastPush = -1;

  protected boolean persistentPriRefs = true;

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

    if (tag.equals("Module"))
    {
      // 601 hack test
      //System.out.println("java.version=1.4 is " + System.getProperty("java.version").startsWith("1.4"));
      if (persistentPriRefs)
        attr.addAttribute(nsuri, "PersistentPrimaryRefs", "PersistentPrimaryRefs", "", "true");
      // 601 extra blank line with JVM 1.4 fix (java.version=1.5.0_03 OR java.version=1.4.2_08)
      if (!System.getProperty("java.version").startsWith("1.4"))
      {
        newLine(stack.size());
      }
    }
    else
    {
      newLine(stack.size());
    }

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

    if (dent && level != lastPush) // check for empty (attr only) element, <tag.../>
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


  public static void transform(org.xml.sax.XMLReader xmlReader, java.io.File xmlFile) throws tv.sage.SageException
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
      javax.xml.transform.Transformer transformer =
          javax.xml.transform.TransformerFactory.newInstance().newTransformer();

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
}
