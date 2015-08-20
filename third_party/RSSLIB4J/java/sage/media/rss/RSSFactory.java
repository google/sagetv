package sage.media.rss;

import javax.xml.parsers.*;

/**
 * RSS Factory.
 *
 * <blockquote>
 * <em>This module, both source code and documentation, is in the
 * Public Domain, and comes with <strong>NO WARRANTY</strong>.</em>
 * </blockquote>
 *
 * @since RSSLIB4J 0.2
 * @author Francesco aka 'Stealthp' stealthp[@]stealthp.org
 * @version 0.2
 */


public class RSSFactory {

  private static SAXParserFactory factory;

  protected static  SAXParserFactory getInstance(){
    if (factory == null)
      factory = SAXParserFactory.newInstance();
    return factory;
  }
}