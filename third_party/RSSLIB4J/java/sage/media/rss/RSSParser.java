package sage.media.rss;

import org.xml.sax.*;
import javax.xml.parsers.*;
import org.xml.sax.helpers.*;
import java.io.*;
import java.net.*;

/**
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 * 
 * RSS Parser.
 *
 * <blockquote>
 * <em>This module, both source code and documentation, is in the
 * Public Domain, and comes with <strong>NO WARRANTY</strong>.</em>
 * </blockquote>
 *
 * @since RSSLIB4J 0.1
 * @author Francesco aka 'Stealthp' stealthp[@]stealthp.org
 * @version 0.2
 */

public class RSSParser {

  private  SAXParserFactory factory = RSSFactory.getInstance();
  private DefaultHandler hnd;
  private File f;
  private URL u;
  private InputStream in;
  private boolean validate;
  private java.io.File tempFile;
  private boolean retrying;
  public RSSParser(){
    validate = false;
  }

  /**
   * Set the event handler
   * @param h the DefaultHandler
   *
   */
  public void setHandler(DefaultHandler h){
    hnd = h;
  }

  /**
   * Set rss resource by local file name
   * @param file_name loca file name
   * @throws RSSException
   */
  public void setXmlResource(String file_name) throws RSSException{
    f = new File(file_name);
    try{
      in = new FileInputStream(f);
    }catch(Exception e){
      throw new RSSException("RSSParser::setXmlResource(file) fails: "+e);
    }

  }

  /**
   * Set rss resource by URL
   * @param ur the remote url
   * @throws RSSException
   */
  public void setXmlResource(URL ur) throws RSSException{
    u = ur;
    File ft = null;
    try{
      URLConnection con = u.openConnection();
      try
      {
        con.setConnectTimeout(30000);
        con.setReadTimeout(30000);
      }catch (Throwable tr){} // in case its a bad JRE version w/ out these calls
      in = u.openStream();
      // always fix the potential Unicode issues (and at the same time we fix the potential zero length issue)
      //      if (con.getContentLength() == -1){
      //      this.fixZeroLength();
      //   }
      // else
      this.fixUnicodeErrors();

    }catch(IOException e){
      throw new RSSException("RSSParser::setXmlResource(url) fails: "+e);
    }
  }

  /**
   * set true if parse have to validate the document
   * defoult is false
   * @param b true or false
   */
  public void setValidate(boolean b){
    validate = b;
  }

  /**
   * Parse rss file
   * @param filename local file name
   * @param handler the handler
   * @param validating validate document??
   * @throws RSSException
   */
  public static void parseXmlFile (String filename, DefaultHandler handler, boolean validating) throws RSSException{
    RSSParser p = new RSSParser();
    p.setXmlResource(filename);
    p.setHandler(handler);
    p.setValidate(validating);
    p.parse();
  }

  /**
   * Parse rss file from a url
   * @param remote_url remote rss file
   * @param handler the handler
   * @param validating validate document??
   * @throws RSSException
   */
  public static void parseXmlFile(URL remote_url, DefaultHandler handler, boolean validating) throws RSSException{
    RSSParser p = new RSSParser();
    p.setXmlResource(remote_url);
    p.setHandler(handler);
    p.setValidate(validating);
    p.parse();
  }

  /**
   * Try to fix null length bug
   * @throws IOException
   * @throws RSSException
   */
  private void fixZeroLength() throws IOException, RSSException {

    tempFile = File.createTempFile(".rsslib4jbugfix", ".tmp");
    tempFile.deleteOnExit();
    FileWriter fw = new FileWriter(tempFile);
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
    BufferedWriter out = new BufferedWriter(fw);
    String line = "";
    while ( (line = reader.readLine()) != null) {
      out.write(line + "\n");
    }
    out.flush();
    out.close();
    reader.close();
    fw.close();
    setXmlResource(tempFile.getAbsolutePath());

  }

  /**
   * Fix invalid unicode characters in the XML stream (Bad Google!!)
   * @throws IOException
   * @throws RSSException
   */
  private void fixUnicodeErrors() throws IOException, RSSException {

    tempFile = File.createTempFile(".rsslib4jbug2fix", ".tmp");
    tempFile.deleteOnExit();
    FileOutputStream fw = new FileOutputStream(tempFile);
    BufferedInputStream bufIn = new BufferedInputStream(in);

    Reader reader = new InputStreamReader(bufIn, "UTF-8");
    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(fw, "UTF-8"));
    int c = reader.read();
    while (c != -1)
    {
      if ((c >= 0x0020 && c <= 0xD7FF)
          || c == 0x000A || c == 0x0009
          || c == 0x000D
          || (c >= 0xE000 && c <= 0xFFFD)
          || (c >= 0x10000 && c <= 0x10ffff))
      {
        out.write(c);
      }
      //		try
      {
        c = reader.read();
      }
      /*		catch (sun.io.MalformedInputException e2)
    {
      System.out.println("Caught MIE, continuing...");
      //reader.close();
      reader = new InputStreamReader(bufIn, "UTF-8");
      c = 0;
    }*/
    }
    out.flush();
    out.close();
    reader.close();

    fw.close();
    setXmlResource(tempFile.getAbsolutePath());

  }

  /**
   * Call it at the end of the work to preserve memory
   */
  public  void free(){
    this.factory = null;
    this.f       = null;
    this.in      = null;
    this.hnd     = null;
    //    System.gc();
  }

  /**
   * Parse the documen
   * @throws RSSException
   */
  public  void parse() throws RSSException{
    try {
      factory.setValidating(validate);
      // Create the builder and parse the file
      factory.newSAXParser().parse(in,hnd);
    }
    catch (java.io.UnsupportedEncodingException use)
    {
      if (!retrying)
      {
        retrying = true;
        try
        {
          if (u != null)
          {
            setXmlResource(u);
            fixUnicodeErrors();
            parse();
            return;
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
          throw new RSSException("RSSParser::fix fails: "+e.getMessage());
        }
      }
      use.printStackTrace();
      throw new RSSException("RSSParser::fix fails: "+use.getMessage());
    }
    catch (SAXException e) {
      e.printStackTrace();
      throw new RSSException("RSSParser::parse fails: "+e.getMessage());
    }
    catch (ParserConfigurationException e) {
      e.printStackTrace();
      throw new RSSException("RSSParser::parse fails: "+e.getMessage());
    }
    catch (IOException e) {
      e.printStackTrace();
      throw new RSSException("RSSParser::parse fails: "+e.getMessage());
    }
    finally
    {
      if (in != null)
      {
        try
        {
          in.close();
        }
        catch (Exception e){}
      }
      if (tempFile != null)
        tempFile.delete();
      tempFile = null;
    }
  }
}