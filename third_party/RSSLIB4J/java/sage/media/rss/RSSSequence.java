package sage.media.rss;
import java.util.LinkedList;

/**
 * RSSSequences's definitions class.
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

public class RSSSequence {

  private LinkedList list;

  public RSSSequence() {
    list = new LinkedList();
  }

  /**
   * Add an element to a sequence
   * @param el the RSSSequenceElement elment
   */
  public void addElement(RSSSequenceElement el){
    list.add(el);
  }

  /**
   * Return the element of a squence into a LinkedList
   * @return The list
   */
  public LinkedList getElementList(){
    return list;
  }

  /**
   * Return the size of a sequence
   * @return the size
   */
  public int getListSize(){
    return list.size();
  }

  /**
   * Useful for debug
   * @return information
   */
  public String toDebugString(){
    String info = "SEQUENCE HAS " + getListSize() + " ELEMENTS.\n";
    for (int i = 0; i < list.size(); i++){
      RSSSequenceElement e = (RSSSequenceElement)list.get(i);
      info += e.toString()+"\n";
    }
    return info;
  }

}