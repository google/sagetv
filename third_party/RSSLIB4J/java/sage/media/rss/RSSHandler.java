package sage.media.rss;
import org.xml.sax.*;
import org.xml.sax.helpers.*;


/**
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Handler for SAX Parser.
 * <p>
 * This elements are <em>not</em> handled yet:<br><br>
 * cloud<br>
 * rating<br>
 * skipHours<br>
 * skipDays<br>
 * category<br>
 * </p>
 *
 * <blockquote>
 * <em>This module, both source code and documentation, is in the
 * Public Domain, and comes with <strong>NO WARRANTY</strong>.</em>
 * </blockquote>
 *
 * @since RSSLIB4J 0.1
 * @author Francesco aka 'StealthP' stealthp[@]stealthp.org
 * @version 0.2
 */


public class RSSHandler extends DefaultHandler{

  private StringBuffer buff;
  private String current_tag;
  private RSSChannel chan;
  private RSSItem itm;
  private RSSMediaGroup mg;
  private RSSImage img;
  private RSSSequence seq;
  private RSSSequenceElement seq_elem;
  private RSSTextInput input;
  private RSSSyndicationModule sy;

  private boolean reading_chan;
  private boolean reading_item;
  private boolean reading_image;
  private boolean reading_seq;
  private boolean reading_input;
  private boolean reading_media_group;
  private boolean have_dc;


  public static final String CHANNEL_TAG     = "channel";
  public static final String TITLE_TAG       = "title";
  public static final String LINK_TAG        = "link";
  public static final String DESCRIPTION_TAG = "description";
  public static final String ITEM_TAG        = "item";
  public static final String IMAGE_TAG       = "image";
  public static final String IMAGE_W_TAG     = "width";
  public static final String IMAGE_H_TAG     = "height";
  public static final String URL_TAG         = "url";
  public static final String SEQ_TAG         = "rdf:seq";
  public static final String SEQ_ELEMENT_TAG = "rdf:li";
  public static final String TEXTINPUT_TAG   = "textinput";
  public static final String NAME_TAG        = "name";
  public static final String LANGUAGE_TAG    = "language";
  public static final String MANAGING_TAG    = "managingEditor";
  public static final String WMASTER_TAG     = "webMaster";
  public static final String COPY_TAG        = "copyright";
  public static final String PUB_DATE_TAG    = "pubDate";
  public static final String LAST_B_DATE_TAG = "lastBuildDate";
  public static final String GENERATOR_TAG   = "generator";
  public static final String DOCS_TAG        = "docs";
  public static final String TTL_TAG         = "ttl";
  public static final String AUTHOR_TAG      = "author";
  public static final String COMMENTS_TAG    = "comments";
  public static final String CLOUD_TAG       = "cloud";     //TODO
  public static final String RATING_TAG      = "rating";    //TODO
  public static final String SKIPH_TAG       = "skipHours"; //TODO
  public static final String SKIPD_TAG       = "skipDays";  //TODO
  public static final String CATEGORY_TAG    = "category";  //TODO

  public static final String ITUNES_AUTHOR   = "itunes:author";
  public static final String ITUNES_SUMMARY  = "itunes:summary";
  public static final String ITUNES_DURATION = "itunes:duration";
  public static final String ITUNES_KEYWORDS = "itunes:keywords";
  public static final String ITUNES_IMAGE    = "itunes:image";

  public static final String DC_TITLE_TAG        = "dc:title";
  public static final String DC_CREATOR_TAG      = "dc:creator";
  public static final String DC_SUBJECT_TAG      = "dc:subject";
  public static final String DC_DESCRIPTION_TAG  = "dc:description";
  public static final String DC_PUBLISHER_TAG    = "dc:publisher";
  public static final String DC_CONTRIBUTOR_TAG  = "dc:contributor";
  public static final String DC_DATE_TAG         = "dc:date";
  public static final String DC_TYPE_TAG         = "dc:type";
  public static final String DC_FORMAT_TAG       = "dc:format";
  public static final String DC_IDENTIFIER_TAG   = "dc:identifier";
  public static final String DC_SOURCE_TAG       = "dc:source";
  public static final String DC_LANGUAGE_TAG     = "dc:language";
  public static final String DC_RELATION_TAG     = "dc:relation";
  public static final String DC_COVERAGE_TAG     = "dc:coverage";
  public static final String DC_RIGHTS_TAG       = "dc:rights";


  public static final String SY_PERIOD_TAG       = "sy:updatePeriod";
  public static final String SY_FREQ_TAG         = "sy:updateFrequency";
  public static final String SY_BASE_TAG         = "sy:updateBase";
  public static final String CONTENT_ENCODED_TAG = "content:encoded";

  public static final String MEDIA_GROUP_TAG     = "media:group";
  public static final String MEDIA_CONTENT_TAG   = "media:content";
  public static final String MEDIA_TITLE_TAG     = "media:title";
  public static final String MEDIA_DESCRIPTION_TAG = "media:description";
  public static final String MEDIA_THUMBNAIL_TAG = "media:thumbnail";
  public static final String MEDIA_PLAYER_TAG    = "media:player";

  public static final String ENCLOSURE_TAG       = "enclosure";


  public RSSHandler(){

    buff          = new StringBuffer();
    current_tag   = null;
    chan          = new RSSChannel();
    reading_chan  = false;
    reading_item  = false;
    reading_image = false;
    reading_seq   = false;
    reading_input = false;
    reading_media_group = false;
    have_dc       = false;


  }

  /**
   * Receive notification of the start of an element.
   * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or if Namespace processing is not being performed.
   * @param localName The local name (without prefix), or the empty string if Namespace processing is not being performed
   * @param qName The qualified name (with prefix), or the empty string if qualified names are not available
   * @param attributes The attributes attached to the element. If there are no attributes, it shall be an empty Attributes object
   */
  public void startElement(String uri,
      String localName,
      String qName,
      Attributes attributes){



    if (tagIsEqual(qName,CHANNEL_TAG)){
      reading_chan = true;
      processChanAboutAttribute(attributes);
    }

    if (tagIsEqual(qName,ITEM_TAG)){
      reading_item = true;
      reading_chan = false;
      itm = new RSSItem();
      processItemAboutAttribute(attributes);
    }

    if (tagIsEqual(qName,IMAGE_TAG)){
      reading_image = true;
      reading_chan  = false;
      img = new RSSImage();
    }

    if (tagIsEqual(qName,SEQ_TAG)){
      reading_seq = true;
      seq = new RSSSequence();
    }

    if (tagIsEqual(qName,TEXTINPUT_TAG)){
      reading_input = true;
      reading_chan  = false;
      input = new RSSTextInput();
    }

    if (tagIsEqual(qName, MEDIA_GROUP_TAG)){
      reading_media_group = true;
      reading_item = false;
      mg = new RSSMediaGroup();
    }

    if (tagIsEqual(qName, ENCLOSURE_TAG))
    {
      processEnclosureAttributes(attributes);
    }

    if (reading_item && !reading_media_group && (tagIsEqual(qName, MEDIA_CONTENT_TAG) ||
        tagIsEqual(qName, MEDIA_THUMBNAIL_TAG) || tagIsEqual(qName, MEDIA_PLAYER_TAG)))
    {
      reading_media_group = true;
      mg = new RSSMediaGroup();
      reading_item = false;
    }

    if (reading_media_group)
    {
      if (tagIsEqual(qName, MEDIA_CONTENT_TAG)){

        processMediaContentAttributes(attributes);
      }
      if (tagIsEqual(qName, MEDIA_THUMBNAIL_TAG) && attributes.getValue("url") != null){
        processMediaThumbnailAttribute(attributes);
      }
      if (tagIsEqual(qName, MEDIA_PLAYER_TAG)){
        processMediaPlayerAttribute(attributes);
      }
    }
    if (tagIsEqual(qName,SEQ_ELEMENT_TAG))
      processSeqElement(attributes);

    if (qName.toUpperCase().startsWith("SY:"))
      sy = new RSSSyndicationModule();

    if (tagIsEqual(qName, ITUNES_IMAGE))
    {
      if (chan.getRSSImage() == null)
      {
        img = new RSSImage();
        img.setUrl(attributes.getValue("href"));
        chan.setRSSImage(img);
      }
    }

    current_tag = qName;

  }

  /**
   * Receive notification of the end of an element
   * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or if Namespace processing is not being performed.
   * @param localName The local name (without prefix), or the empty string if Namespace processing is not being performed
   * @param qName The qualified name (with prefix), or the empty string if qualified names are not available
   */
  public void endElement(String uri,
      String localName,
      String qName){


    String data = buff.toString().trim();

    if (qName.equals(current_tag)){
      //      data = buff.toString().trim();
      buff = new StringBuffer();
    }


    if (reading_chan)
      processChannel(qName,data);

    if (reading_item)
      processItem(qName,data);

    if (reading_image)
      processImage(qName,data);

    if (reading_input)
      processTextInput(qName,data);

    if (reading_media_group)
      processMediaGroup(qName, data);

    if (tagIsEqual(qName,CHANNEL_TAG)){
      reading_chan = false;
      chan.setSyndicationModule(sy);
    }

    if (tagIsEqual(qName,ITEM_TAG)){
      // Any media is done too
      if (reading_media_group)
      {
        reading_media_group = false;
        itm.setMediaGroup(mg);
      }
      reading_item = false;
      chan.addItem(itm);

      // Check to see if there's an image in the description that we want to extract
      if (itm.getMediaGroup() == null || itm.getMediaGroup().getThumbURL() == null)
      {
        String desc = itm.getDescription();
        if (desc != null)
        {
          int idx1 = 0;
          idx1 = desc.indexOf("src=\"", idx1 + 1);
          while (idx1 != -1)
          {
            int idx2 = desc.indexOf("\"", idx1 + 6);
            if (idx2 != -1)
            {
              String imgStr = desc.substring(idx1 + 5, idx2);
              if (imgStr.endsWith(".jpg"))
              {
                if (itm.getMediaGroup() == null)
                  itm.setMediaGroup(new RSSMediaGroup());
                itm.getMediaGroup().setThumbURL(imgStr);
                break;
              }
            }
            idx1 = desc.indexOf("src=\"", idx1 + 1);
          }
        }
      }
    }

    if (tagIsEqual(qName,IMAGE_TAG)){
      reading_image = false;
      chan.setRSSImage(img);
    }

    if (tagIsEqual(qName,MEDIA_GROUP_TAG)){
      reading_media_group = false;
      if (itm != null)
        itm.setMediaGroup(mg);
    }

    if (tagIsEqual(qName,SEQ_TAG)){
      reading_seq = false;
      chan.addRSSSequence(seq);
    }

    if (tagIsEqual(qName,TEXTINPUT_TAG)){
      reading_input = false;
      chan.setRSSTextInput(input);
    }

  }

  /**
   * Receive notification of character data inside an element
   * @param ch The characters.
   * @param start The start position in the character array.
   * @param length The number of characters to use from the character array.
   */
  public void characters(char[] ch,
      int start,
      int length){


    String data = new String(ch,start,length);

    //Jump blank chunk
    if (data.trim().length() == 0)
      return;

    buff.append(data);

  }

  /**
   * Receive notification when parse are scannering an image
   * @param qName The tag name
   * @param data The tag Value
   */
  private void processImage(String qName,String data){
    //System.out.println("RSSHandler:processImage():: TAG: " + qName);
    if (tagIsEqual(qName,TITLE_TAG))
      img.setTitle(data);

    if (tagIsEqual(qName,LINK_TAG))
      img.setLink(data);

    if (tagIsEqual(qName,URL_TAG))
      img.setUrl(data);

    if (tagIsEqual(qName,IMAGE_W_TAG))
      img.setWidth(data);

    if (tagIsEqual(qName,IMAGE_H_TAG))
      img.setHeight(data);

    if (tagIsEqual(qName,DESCRIPTION_TAG))
      img.setDescription(data);

    if (qName.toUpperCase().startsWith("DC:"))
      processDoublinCoreTags(qName,data,img);

  }


  /**
   * Receive notification when parse are scannering a textinput
   * @param qName The tag name
   * @param data The tag Value
   */

  private void processTextInput(String qName,String data){

    if (tagIsEqual(qName,TITLE_TAG))
      input.setTitle(data);

    if (tagIsEqual(qName,LINK_TAG))
      input.setLink(data);

    if (tagIsEqual(qName,NAME_TAG))
      input.setInputName(data);

    if (tagIsEqual(qName,DESCRIPTION_TAG))
      input.setDescription(data);

    if (qName.toUpperCase().startsWith("DC:"))
      processDoublinCoreTags(qName,data,input);

  }

  /**
   * Receive notification when parse are scannering an Item
   * @param qName The tag name
   * @param data The tag Value
   */
  private void processItem(String qName,String data){

    if (tagIsEqual(qName,TITLE_TAG))
      itm.setTitle(data);

    if (tagIsEqual(qName,LINK_TAG))
      itm.setLink(data);

    if (tagIsEqual(qName,DESCRIPTION_TAG) || tagIsEqual(qName,ITUNES_SUMMARY))
      itm.setDescription(data);

    if (tagIsEqual(qName,PUB_DATE_TAG))
      itm.setPubDate(data);

    if (tagIsEqual(qName,PUB_DATE_TAG))
      itm.setPubDate(data);

    if (tagIsEqual(qName,AUTHOR_TAG) || tagIsEqual(qName,ITUNES_AUTHOR))
      itm.setAuthor(data);

    if (tagIsEqual(qName,COMMENTS_TAG))
      itm.setComments(data);

    if (qName.toUpperCase().startsWith("DC:"))
      processDoublinCoreTags(qName,data,itm);

    if(tagIsEqual(qName, CONTENT_ENCODED_TAG))
      itm.setContentEncoded(data);

    if (tagIsEqual(qName, ITUNES_DURATION))
    {
      itm.setDuration(parseDuration(data));
    }
  }

  private long parseDuration(String s)
  {
    long rv = 0;
    int lastIdx = s.lastIndexOf(':');
    try
    {
      rv += 1000*Integer.parseInt(s.substring(lastIdx + 1));
      int nextIdx = s.lastIndexOf(':', lastIdx - 1);
      if (lastIdx != -1)
      {
        rv += 60000*Integer.parseInt(s.substring(nextIdx + 1, lastIdx));
        if (nextIdx != -1)
          rv += 3600000L*Long.parseLong(s.substring(0, nextIdx));
      }
    }
    catch (NumberFormatException e)
    {
      if (s.indexOf("sec") != -1)
      {
        try
        {
          rv += 1000 * Integer.parseInt(s.substring(0, s.indexOf("sec")).trim());
        }
        catch (NumberFormatException e1)
        {
          System.out.println("ERROR2 with duration tag \"" + s + "\" parsing of:" + e1);
        }
      }
      else
        System.out.println("ERROR with duration tag \"" + s + "\" parsing of:" + e );
    }
    return rv;
  }

  /**
   * Receive notification when parse are scannering the Channel
   * @param qName The tag name
   * @param data The tag Value
   */
  private void processChannel(String qName,String data){

    if (tagIsEqual(qName,TITLE_TAG))
      chan.setTitle(data);

    if (tagIsEqual(qName,LINK_TAG))
      chan.setLink(data);

    if (tagIsEqual(qName,DESCRIPTION_TAG))
      chan.setDescription(data);

    if (tagIsEqual(qName,COPY_TAG))
      chan.setCopyright(data);

    if (tagIsEqual(qName,PUB_DATE_TAG))
      chan.setPubDate(data);

    if (tagIsEqual(qName,LAST_B_DATE_TAG))
      chan.setLastBuildDate(data);

    if (tagIsEqual(qName,GENERATOR_TAG))
      chan.setGenerator(data);

    if (tagIsEqual(qName,DOCS_TAG))
      chan.setDocs(data);

    if (tagIsEqual(qName,TTL_TAG))
      chan.setTTL(data);

    if (tagIsEqual(qName,LANGUAGE_TAG))
      chan.setLanguage(data);


    if (qName.toUpperCase().startsWith("DC:"))
      processDoublinCoreTags(qName,data,chan);

    if (qName.toUpperCase().startsWith("SY:"))
      processSyndicationTags(qName,data);

  }

  /**
   * Receive notification when parse are scannering a doublin core element
   * @param qName tag name
   * @param data tag value
   * @param o RSSObject
   */
  private void processDoublinCoreTags(String qName, String data,RSSObject o){
    o.addDoublinCoreElement(qName.toLowerCase(),data);
  }

  private void processSyndicationTags(String qName, String data){


    if (tagIsEqual(qName,SY_BASE_TAG))
      sy.setSyUpdateBase(data);

    if (tagIsEqual(qName,SY_FREQ_TAG))
      sy.setSyUpdateFrequency(data);

    if (tagIsEqual(qName,SY_PERIOD_TAG))
      sy.setSyUpdatePeriod(data);
  }

  /**
   * Receive notification when parse are scannering a Sequence Item
   * @param a The Atrribute of the tag
   */
  private void processSeqElement(Attributes a)
  {
    if (a.getLength() > 0)
    {
      String res = a.getValue(0);
      seq_elem = new RSSSequenceElement();
      seq_elem.setResource(res);
      seq.addElement(seq_elem);
    }
  }


  /**
   * Receive notification when parse are scannering an Item attribute
   * @param a the attribute
   */
  private void processItemAboutAttribute(Attributes a)
  {
    if (a.getLength() > 0)
    {
      String res = a.getValue(0);
      itm.setAboutAttribute(res);
    }

  }

  /**
   * Receive notification when parse are scannering a Chan attribute
   * @param a the attribute
   */
  private void processChanAboutAttribute(Attributes a)
  {
    if (a.getLength() > 0)
    {
      String res = a.getValue(0);
      chan.setAboutAttribute(res);
    }
  }

  /**
   * Receive notification when parse are scannering an Inputtext attribute
   * @param a the attribute
   */
  private void processInputAboutAttribute(Attributes a)
  {
    if (a.getLength() > 0)
    {
      String res = a.getValue(0);
      input.setAboutAttribute(res);
    }
  }

  /**
   * Check against non-casesentive tag name
   * @param a The first tag
   * @param b The tag to check
   * @return True if the tags are the same
   */
  protected static boolean tagIsEqual(String a, String b){

    return a.equalsIgnoreCase(b);

  }

  /**
   * Get the RSSChannel Object back from the parser
   * @return The RSSChannell Object
   */
  public RSSChannel getRSSChannel(){

    return this.chan;

  }

  private void processMediaGroup(String qName, String data)
  {
    if (tagIsEqual(qName,MEDIA_TITLE_TAG))
      mg.setTitle(data);

    if (tagIsEqual(qName,MEDIA_DESCRIPTION_TAG))
      mg.setDescription(data);

    if (qName.toUpperCase().startsWith("DC:"))
      processDoublinCoreTags(qName,data,mg);

    if (tagIsEqual(qName, MEDIA_THUMBNAIL_TAG) && data.length() > 0 && mg.getThumbURL() == null)
      mg.setThumbURL(data);
  }

  private void processMediaContentAttributes(Attributes attributes)
  {
    mg.addContent(new RSSMediaContent(attributes.getValue("url"), attributes.getValue("type"), attributes.getValue("medium"),
        attributes.getValue("expression"), attributes.getValue("duration"), attributes.getValue("width"), attributes.getValue("height")));
  }

  private void processEnclosureAttributes(Attributes attributes)
  {
    itm.setEnclosure(new RSSEnclosure(attributes.getValue("url"), attributes.getValue("type"),
        attributes.getValue("length") == null ? attributes.getValue("duration") : attributes.getValue("length")));
  }

  private void processMediaThumbnailAttribute(Attributes attributes)
  {
    mg.setThumbURL(attributes.getValue("url"));
    mg.setThumbWidth(attributes.getValue("width"));
    mg.setThumbHeight(attributes.getValue("height"));
  }

  private void processMediaPlayerAttribute(Attributes attributes)
  {
    mg.setPlayerURL(attributes.getValue("url"));
  }

}