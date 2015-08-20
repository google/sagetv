package sage.media.rss;

/**
 * RSSTextInput's definitions class.
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

public class RSSTextInput
extends RSSObject {

  private String name;

  /**
   * Set the input type name
   * @param n the input type name
   */
  public void setInputName(String n) {
    name = n;
  }

  /**
   * Get the form input name
   * @return the name
   */
  public String getInputName() {
    return name;
  }

  /**
   * Get the form action
   * @return the action
   */
  public String getFormAction() {
    return super.getLink();
  }

  /**
   * Info
   * @return info string
   */
  public String toDebugString() {
    String info = "FORM ACTION: " + getFormAction() + "\n" + "INPUT NAME: " +
        getInputName() + "\n" + "DESCRIPTION: " + super.getDescription();
    return info;
  }

  /**
   * A basic rendering in html
   * @return the html form
   */
  public String toHTML() {
    String html = "<form method\"GET\" action=\"" + getFormAction() + "\">\n" +
        super.getDescription() + "<br>\n" + "<input type=\"text\" name=\"" +
        getInputName() + "\">\n</form>";
    return html;
  }

}
