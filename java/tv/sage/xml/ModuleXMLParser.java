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
 * Parse a ModuleXMLWidget Module from a XML stream.
 * <br>
 * @author 601
 */
public class ModuleXMLParser
{
  static final String nsuri = "urn:tv.sage/stv";

  public int maxDepth = 0;
  public int anonCount = 0;

  final java.io.File xmlFile;

  public String ModuleName;

  public boolean usesPersistentPrimaryRefs;

  // defined tv.sage.mod.RawWidget
  public final java.util.Vector widgetv = new java.util.Vector(10000);

  // defined tv.sage.mod.RawWidget
  public final java.util.Vector symbolv = new java.util.Vector(100);

  // raw-ID-String, tv.sage.mod.RawWidget
  public final java.util.Map idMap = new java.util.HashMap();


  public ModuleXMLParser(java.io.File xmlFile)
  {
    this.xmlFile = xmlFile;
  }

  //    public static void main(String[] args) throws Exception
  //    {
  //        ModuleXMLParser sxr =  new ModuleXMLParser(new java.io.File("xml/out.xml"));
  //
  //        sxr.parse();
  //
  //        System.out.println("sxr.maxDepth = " + sxr.maxDepth);
  //
  //        java.io.File out = new java.io.File("xml/out.raw");
  //
  //        java.io.FileWriter fw = new java.io.FileWriter(out);
  //
  //        java.util.Iterator itr = sxr.widgetv.iterator();
  //        for (int i = 0; i < Integer.MAX_VALUE && itr.hasNext(); i++)
  //        {
  //            //if (i > 100) break;
  //
  //            //System.out.println(itr.next());
  //
  //            tv.sage.mod.RawWidget rw = (tv.sage.mod.RawWidget)itr.next();
  //
  //            StringBuffer sb = new StringBuffer();
  //
  //            sb.append(i);
  //            sb.append('\t').append(sage.Widget.TYPES[rw.type()]);
  //            sb.append('\t').append("\\N");
  //            sb.append('\n');
  //
  //            fw.write(sb.toString().toCharArray());
  //
  //            java.util.Enumeration e = rw.properties().propertyNames();
  //            while (e.hasMoreElements())
  //            {
  //                sb = new StringBuffer();
  //
  //                sb.append(i);
  //                sb.append('\t').append(sage.Widget.TYPES[rw.type()]);
  //                sb.append('\t').append(e.nextElement());
  //                sb.append('\n');
  //
  //                fw.write(sb.toString().toCharArray());
  //            }
  //        }
  //
  //        fw.close();
  //    }

  public void parse() throws javax.xml.parsers.ParserConfigurationException,
  org.xml.sax.SAXException, java.io.IOException
  {
    // 601 Java 1.5 only
    //org.xml.sax.XMLReader xmlReader = org.xml.sax.helpers.XMLReaderFactory.createXMLReader();

    org.xml.sax.XMLReader xmlReader = javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getXMLReader();
    //        org.xml.sax.XMLReader xmlReader = new javolution.xml.sax.SAX2ReaderImpl();

    xmlReader.setFeature("http://xml.org/sax/features/namespaces", true);

    xmlReader.setContentHandler(new Handler());

    java.io.InputStream inputStream = new java.io.FileInputStream(xmlFile);

    try
    {
      org.xml.sax.InputSource inputSource = new org.xml.sax.InputSource(inputStream);

      //            inputSource.setEncoding("UTF-8");

      xmlReader.parse(inputSource);
    }
    finally
    {
      inputStream.close();
    }
    /*
		java.io.FileInputStream fis = new java.io.FileInputStream(xmlFile);
		javolution.xml.stream.XMLStreamReaderImpl xmlReader = new javolution.xml.stream.XMLStreamReaderImpl();

		java.util.Stack stack = new java.util.Stack();

		String lastPropName = null;
		String lastPropValue = null;
		try
		{
			xmlReader.setInput(fis, "UTF-8");
			for (int e=xmlReader.next(); e != javolution.xml.stream.XMLStreamConstants.END_DOCUMENT; e = xmlReader.next())
			{
				switch (e)
				{ // Event.
					case javolution.xml.stream.XMLStreamConstants.START_ELEMENT:
						byte currType = -1;
						if ((currType = widgetType(xmlReader.getLocalName())) != -1 || xmlReader.getLocalName().equals("Proxy"))
						{
							// ref* def ref* re-def | ref* | anon-def

							ModuleXMLWidget mxw = null;

							CharSequence ref = xmlReader.getAttributeValue(null, "Ref");
							CharSequence sym = xmlReader.getAttributeValue(null, "Sym");
							CharSequence name = xmlReader.getAttributeValue(null, "Name");

							if (ref != null) // reference
							{
								mxw = (ModuleXMLWidget)idMap.get(ref);

								if (mxw == null) // first time
								{
									String refString = ref.toString();
									mxw = new ModuleXMLWidget(currType,
										name == null ? null : name.toString(), refString,
										sym == null ? null : sym.toString());

									if (sym != null) symbolv.add(mxw);

									widgetv.add(mxw);

									idMap.put(refString, mxw); // for future reference
								}
							}
							else // definition by ID or anonymous
							{
								CharSequence id = xmlReader.getAttributeValue(null, "ID");
								if (id != null)
								{
									mxw = (ModuleXMLWidget)idMap.get(id);

									if (mxw == null) // first time
									{
										mxw = new ModuleXMLWidget(currType,
											name == null ? null : name.toString(), null, sym == null ? null : sym.toString());

										if (sym != null) symbolv.add(mxw);

										widgetv.add(mxw);

										idMap.put(id.toString(), mxw); // for future reference
									}
									else if (mxw.isRef()) // previously ref'd
									{
										// transition from ref to definition
										mxw.name = name != null ? name.toString() : null;
										mxw.ref = null;

										if (sym != null)
										{
											mxw.sym = sym.toString();

											symbolv.add(mxw);
										}
									}
									else
									{
										throw (new org.xml.sax.SAXException("duplicate definition for " + id));
									}
								}
								else // anonymous
								{
									anonCount++;

									mxw = new ModuleXMLWidget(currType, name == null ? null : name.toString(),
										null, sym == null ? null : sym.toString());

									if (sym != null) symbolv.add(mxw);

									widgetv.add(mxw);
								}
							}

							stack.push(mxw);
						}
						else if (widgetProperty(xmlReader.getLocalName()) != -1)
						{
							lastPropName = xmlReader.getLocalName().toString();
						}
						else if (xmlReader.getLocalName().equals("Module"))
						{
							ModuleName = xmlReader.getAttributeValue(null, "Name").toString();

							stack.push(null);
						}
			//            else if (localName.equalsIgnoreCase("Proxy"))
			//            {
			//                String id = attributes.getValue("", "ID");
			//                String ref = attributes.getValue("", "Ref");
			//                String sym = attributes.getValue("", "Sym");
			//                String name = attributes.getValue("", "Name");
			//
			//                ModuleXMLWidget mxw = new ModuleXMLWidget("Proxy", null, ref, sym);
			//
			//                widgetv.add(mxw);
			//
			//                stack.push(mxw);
			//            }
						else
						{
							// 601 throw (new org.xml.sax.SAXException("unrecognized tag " + localName));

							tv.sage.mod.Log.ger.warning("undefined property / type " + xmlReader.getLocalName());

							stack.push(null);
						}

						maxDepth = Math.max(maxDepth, stack.size());
						break;
					case javolution.xml.stream.XMLStreamConstants.END_ELEMENT:
						if (lastPropName != null)
						{
							ModuleXMLWidget parent = stack.isEmpty() ? null : (ModuleXMLWidget)stack.peek();
							if (parent != null)
								parent.addProperty(lastPropName, lastPropValue);
							lastPropName = lastPropValue = null;
						}
						else
						{
							Object pop = stack.pop();

							ModuleXMLWidget parent = stack.isEmpty() ? null : (ModuleXMLWidget)stack.peek();

							if (pop instanceof ModuleXMLWidget)
							{
								ModuleXMLWidget w = (ModuleXMLWidget)pop;

								if (parent != null)
								{
									parent.containWidget(w);
								}
							}
						}
						break;
					case javolution.xml.stream.XMLStreamConstants.CHARACTERS:
						if (lastPropName != null)
							lastPropValue = xmlReader.getText().toString();
						break;

				}
			}
		}
		catch (javolution.xml.stream.XMLStreamException ex)
		{
			System.out.println("XML Parsing error:" + ex);
			ex.printStackTrace();
		}
		try
		{
			xmlReader.close();
		}
		catch (javolution.xml.stream.XMLStreamException ex)
		{
		}

		fis.close();*/
    // handle orphan refs in idMap
    java.util.Iterator itr = idMap.values().iterator();
    while (itr.hasNext())
    {
      ModuleXMLWidget mxw = (ModuleXMLWidget)itr.next();

      if (mxw.isRef())
      {
        if (sage.Sage.DBG) System.out.println("Modules: undefined " + mxw);
      }
    }
  }

  byte widgetType(CharSequence tag)
  {
    for (byte i = 0; i < sage.Widget.TYPES.length; i++)
    {
      // Case insensitive for performance reasons
      if (tag.equals(sage.Widget.TYPES[i])) return (i);
    }

    return (-1);
  }

  int widgetProperty(CharSequence tag)
  {
    for (int i = 0; i < sage.Widget.PROPS.length; i++)
    {
      // Case insensitive for performance reasons
      if (tag.equals(sage.Widget.PROPS[i])) return (i);
    }

    return (-1);
  }

  class Handler extends org.xml.sax.helpers.DefaultHandler
  {
    final java.util.Stack stack = new java.util.Stack();
    StringBuffer charactersBuffer = null;
    public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes) throws org.xml.sax.SAXException
    {
      if (charactersBuffer != null)
        charactersBuffer.setLength(0);

      byte currType = -1;
      if ((currType = widgetType(localName)) != -1 || localName.equals("Proxy"))
      {
        // ref* def ref* re-def | ref* | anon-def

        ModuleXMLWidget mxw = null;

        String id = attributes.getValue("", "ID");
        String ref = attributes.getValue("", "Ref");
        String sym = attributes.getValue("", "Sym");
        String name = attributes.getValue("", "Name");

        if (ref != null) // reference
        {
          mxw = (ModuleXMLWidget)idMap.get(ref);

          if (mxw == null) // first time
          {
            mxw = new ModuleXMLWidget(currType, name, ref, sym);

            if (sym != null) symbolv.add(mxw);

            widgetv.add(mxw);

            idMap.put(ref, mxw); // for future reference
          }
          stack.push(mxw);
        }
        else // definition by ID or anonymous
        {
          if (id != null)
          {
            mxw = (ModuleXMLWidget)idMap.get(id);

            if (mxw == null) // first time
            {
              mxw = new ModuleXMLWidget(currType, name, null, sym);

              if (sym != null) symbolv.add(mxw);

              widgetv.add(mxw);

              idMap.put(id, mxw); // for future reference
            }
            else if (mxw.isRef()) // previously ref'd
            {
              // transition from ref to definition
              mxw.name = name;
              mxw.ref = null;

              if (sym != null)
              {
                mxw.sym = sym;

                symbolv.add(mxw);
              }
            }
            else
            {
              throw (new org.xml.sax.SAXException("duplicate definition for " + id));
            }
          }
          else // anonymous
          {
            anonCount++;

            mxw = new ModuleXMLWidget(currType, name, null, sym);

            if (sym != null) symbolv.add(mxw);

            widgetv.add(mxw);
          }

          stack.push(new PrimaryWrapper(mxw));
        }
      }
      else if (widgetProperty(localName) != -1)
      {
        stack.push(new ModuleXMLWidget.Property(localName));
      }
      else if (localName.equals("Module"))
      {
        ModuleName = attributes.getValue("", "Name");
        usesPersistentPrimaryRefs = "true".equals(attributes.getValue("", "PersistentPrimaryRefs"));
        stack.push(null);
      }
      //            else if (localName.equalsIgnoreCase("Proxy"))
      //            {
      //                String id = attributes.getValue("", "ID");
      //                String ref = attributes.getValue("", "Ref");
      //                String sym = attributes.getValue("", "Sym");
      //                String name = attributes.getValue("", "Name");
      //
      //                ModuleXMLWidget mxw = new ModuleXMLWidget("Proxy", null, ref, sym);
      //
      //                widgetv.add(mxw);
      //
      //                stack.push(mxw);
      //            }
      else
      {
        // 601 throw (new org.xml.sax.SAXException("unrecognized tag " + localName));

        if (sage.Sage.DBG) System.out.println("Modules: undefined property / type " + localName);

        stack.push(null);
      }

      maxDepth = Math.max(maxDepth, stack.size());
    }

    public void endElement(String uri, String localName, String qName) throws org.xml.sax.SAXException
    {
      Object pop = stack.pop();

      ModuleXMLWidget parent = null;
      if (!stack.isEmpty())
      {
        Object peeker = stack.peek();
        if (peeker instanceof PrimaryWrapper)
          parent = ((PrimaryWrapper) peeker).mxw;
        else
          parent = (ModuleXMLWidget) peeker;
      }

      if (pop instanceof ModuleXMLWidget.Property)
      {
        ModuleXMLWidget.Property wp = (ModuleXMLWidget.Property)pop;

        wp.setValue(charactersBuffer);

        // 601 handle null parent
        parent.addProperty(wp);
      }
      else if (pop instanceof ModuleXMLWidget)
      {
        ModuleXMLWidget w = (ModuleXMLWidget)pop;

        if (parent != null)
        {
          parent.containWidget(w);
        }
      }
      else if (pop instanceof PrimaryWrapper)
      {
        ModuleXMLWidget w = ((PrimaryWrapper) pop).mxw;
        if (parent != null)
        {
          if (usesPersistentPrimaryRefs)
            parent.containWidgetPrimary(w);
          else
            parent.containWidget(w);
        }
      }
    }

    public void characters(char[] ch, int start, int length)
    {
      if (charactersBuffer == null)
      {
        charactersBuffer = new StringBuffer(length);
      }

      charactersBuffer.append(ch, start, length);
    }

    public void ignorableWhitespace(char[] ch, int start, int length)
    {
      // ignore it
    }
  }

  private static class PrimaryWrapper
  {
    public PrimaryWrapper(ModuleXMLWidget xw)
    {
      mxw = xw;
    }
    public ModuleXMLWidget mxw;
  }
}
