package sage.media.rss;

import java.util.*;

/**
 * Handler for doublin core information.
 *
 * <blockquote>
 * <em>This module, both source code and documentation, is in the
 * Public Domain, and comes with <strong>NO WARRANTY</strong>.</em>
 * </blockquote>
 *
 * <b>Namespace Declarations</b><br>
 * <ul>
 *  <li><b>xmlns:dc="http://purl.org/dc/elements/1.1/"</b></li>
 * </ul>
 * <h2><a name="model">Model</a></h2>
 *  <p>
 * <em>&lt;channel&gt;, &lt;item&gt;, &lt;image&gt;, and &lt;textinput&gt; Elements:</em> </p>
 * <ul>
 * <li><b>&lt;dc:title&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:creator&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:subject&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:description&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:publisher&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:contributor&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:date&gt;</b> ( #PCDATA ) [<a href="http://www.w3.org/TR/NOTE-datetime">W3CDTF</a>]</li>
 * <li><b>&lt;dc:type&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:format&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:identifier&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:source&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:language&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:relation&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:coverage&gt;</b> ( #PCDATA )</li>
 * <li><b>&lt;dc:rights&gt;</b> ( #PCDATA )</li>
 * </ul>
 *
 *
 * @since RSSLIB4J 0.1
 * @author Francesco aka 'Stealthp' stealthp[@]stealthp.org
 * @version 0.2
 */

public class RSSDoublinCoreModule  implements java.io.Serializable{
  private String title;
  private String creator;
  private String subject;
  private String description;
  private String publisher;
  private String contributor;
  private String date;
  private String type;
  private String format;
  private String identifier;
  private String source;
  private String language;
  private String relation;
  private String coverage;
  private String rights;

  /**
   * Set the title
   * @param t title
   */
  public void setDcTitle(String t) {
    title = t;
  }

  /**
   * Set the creator
   * @param t creator
   */
  public void setDcCreator(String t) {
    creator = t;
  }

  /**
   * Set subject
   * @param t subject
   */
  public void setDcSubject(String t) {
    subject = t;
  }

  /**
   * Set the description
   * @param t description
   */
  public void setDcDescription(String t) {
    description = t;
  }

  /**
   * Set the publiscer
   * @param t publisher
   */
  public void setDcPublisher(String t) {
    publisher = t;
  }

  /**
   * Set the Contributor
   * @param t contributor
   */
  public void setDcContributor(String t) {
    contributor = t;
  }

  /**
   * Set the date
   * @param t date
   */
  public void setDcDate(String t) {
    date = t;
  }

  /**
   * Set the type
   * @param t typr
   */
  public void setDcType(String t) {
    type = t;
  }

  /**
   * Set the format
   * @param t format
   */
  public void setDcFormat(String t) {
    format = t;
  }

  /**
   * Set the identifier
   * @param t identifier
   */
  public void setDcIdentifier(String t) {
    identifier = t;
  }

  /**
   * Set the source
   * @param t source
   */
  public void setDcSource(String t) {
    source = t;
  }

  /**
   * Set the language
   * @param t language
   */
  public void setDcLanguage(String t) {
    language = t;
  }

  /**
   * Set declaration
   * @param t declaration
   */
  public void setDcRelation(String t) {
    relation = t;
  }

  /**
   * Set coverage
   * @param t coverage
   */
  public void setDcCoverage(String t) {
    coverage = t;
  }

  /**
   * Set copy rights
   * @param t rights
   */
  public void setDcRights(String t) {
    rights = t;
  }

  /**
   * Get the title
   * @return title
   */
  public String getDcTitle() {
    return title;
  }

  /**
   * Get the creator
   * @return creator
   */
  public String getDcCreator() {
    return creator;
  }

  /**
   * Get the subject
   * @return subject
   */
  public String getDcSubject() {
    return subject;
  }

  /**
   * Get description
   * @return description
   */
  public String getDcDescription() {
    return description;
  }

  /**
   * Get the publischer
   * @return the publischer
   */
  public String getDcPublisher() {
    return publisher;
  }

  /**
   * Get the contributor
   * @return contributor
   */
  public String getDcContributor() {
    return contributor;
  }

  /**
   * Get the date
   * @return date
   */
  public String getDcDate() {
    return date;
  }

  /**
   * Get the type
   * @return the type
   */
  public String getDcType() {
    return type;
  }

  /**
   * The format
   * @return format
   */
  public String getDcFormat() {
    return format;
  }

  /**
   * Get the identifier
   * @return the identifier
   */
  public String getDcIdentifier() {
    return identifier;
  }

  /**
   * Get the source
   * @return the source
   */
  public String getDcSource() {
    return source;
  }

  /**
   * Get the language
   * @return language
   */
  public String getDcLanguage() {
    return language;
  }

  /**
   * Get the declaration
   * @return declaration
   */
  public String getDcRelation() {
    return relation;
  }

  /**
   * Get the coverage
   * @return coverage
   */
  public String getDcCoverage() {
    return coverage;
  }

  /**
   * The Right
   * @return right
   */
  public String getDcRight() {
    return title;
  }

  /**
   * A poor informational string
   * @return info
   */
  public String toDebugString() {
    String info = "DC SUBJECT: " + subject + "\nDC CREATOR: " + creator;
    return info;
  }

  /**
   * Build a RSSDoublinCoreModule object from an Hashtable
   * @param t The Hashtable with key as dc tag and value as tag's value
   * @return the RSSDoublinCoreModule object
   */
  protected static RSSDoublinCoreModule buildDcModule(Hashtable t) {

    if (t == null || t.size() == 0)
      return null;

    RSSDoublinCoreModule dc = new RSSDoublinCoreModule();

    Hashtable tbl = t;

    Enumeration en = t.keys();

    while (en.hasMoreElements()) {

      String qName = (String) en.nextElement();
      String data = (String) tbl.get(qName);

      if (data == null)
        continue;

      if (RSSHandler.tagIsEqual(RSSHandler.DC_TITLE_TAG, qName))
        dc.setDcTitle(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_CREATOR_TAG, qName))
        dc.setDcCreator(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_SUBJECT_TAG, qName))
        dc.setDcSubject(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_DESCRIPTION_TAG, qName))
        dc.setDcDescription(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_PUBLISHER_TAG, qName))
        dc.setDcPublisher(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_CONTRIBUTOR_TAG, qName))
        dc.setDcContributor(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_DATE_TAG, qName))
        dc.setDcDate(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_TYPE_TAG, qName))
        dc.setDcType(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_FORMAT_TAG, qName))
        dc.setDcFormat(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_IDENTIFIER_TAG, qName))
        dc.setDcIdentifier(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_SOURCE_TAG, qName))
        dc.setDcSource(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_LANGUAGE_TAG, qName))
        dc.setDcLanguage(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_RELATION_TAG, qName))
        dc.setDcRelation(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_COVERAGE_TAG, qName))
        dc.setDcCoverage(data);

      if (RSSHandler.tagIsEqual(RSSHandler.DC_RIGHTS_TAG, qName))
        dc.setDcRights(data);
    }

    return dc;
  }

}