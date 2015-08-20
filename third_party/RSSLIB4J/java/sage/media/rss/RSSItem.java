package sage.media.rss;

/**
 * RSSItems's definitions class.
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


public class RSSItem extends RSSObject{

  private String date;
  private String auth;
  private String comm;
  protected String contentEncoded;
  private RSSMediaGroup mg;
  private RSSEnclosure encl;
  private long duration;

  /**
   * Get the date
   * @return the date as string
   */
  public String getDate(){
    if (super.getDoublinCoreElements() == null){
      if (super.getPubDate() == null){
        date = null;
        return null;
      }else{
        date = super.getPubDate();
        return date;
      }
    }else{
      date = (String)super.getDoublinCoreElements().get(RSSHandler.DC_DATE_TAG);
      return date;
    }
  }

  /**
   * Set the date of the item
   * @param d the date
   */
  public void setDate(String d){
    date = d;
    if (super.getDoublinCoreElements() != null){
      if (super.getDoublinCoreElements().containsKey(RSSHandler.DC_DATE_TAG)){
        super.addDoublinCoreElement(RSSHandler.DC_DATE_TAG,d);
      }else{
        if (super.getPubDate() != null)
          super.setPubDate(d);
        date = d;
      }
    }
    date = d;
  }

  /**
   * Set the item's author
   * @param author Email address of the author of the item.
   */
  public void setAuthor(String author){
    auth = author;
  }

  /**
   * Set the item's comment
   * @param comment URL of a page for comments relating to the item
   */
  public void setComments(String comment){
    comm = comment;
  }

  /**
   * Get the comments url
   * @return comments url (optional)
   */
  public String getComments(){
    return comm;
  }

  /**
   * Get the item's author
   * @return author (optional)
   */
  public String getAuthor(){
    return auth;
  }

  /**
   * Useful for debug
   * @return the info string
   */
  public String toDebugString() {
    String info = "ABOUT ATTRIBUTE: " + about + "\n" + "TITLE: " + title +
        "\n" + "LINK: " + link + "\n" +
        "DESCRIPTION: " + description + "\n" + "DATE: " + getDate();
    return info;
  }

  /**
   * @return Returns the contentEncoded.
   */
  public String getContentEncoded() {
    return contentEncoded;
  }

  private String cleanDesc;
  public String getCleanDescription()
  {
    if (cleanDesc != null)
      return cleanDesc;
    String currCleanDesc = super.getDescription();
    if (currCleanDesc == null) return "";
    StringBuffer sb = new StringBuffer(currCleanDesc.length());
    int lastTagStart = -1;
    boolean justWroteWS = false;
    boolean justWroteNL = false;
    CharacterReference reffy = new CharacterReference("", 0);
    int lastEntityRefStart = -1;
    for (int i = 0; i < currCleanDesc.length(); i++)
    {
      char c = currCleanDesc.charAt(i);
      if (lastTagStart != -1)
      {
        // We're currently inside a tag; skip all until we close the tag
        if (c == '>')
        {
          String tag = currCleanDesc.substring(lastTagStart + 1,
              (currCleanDesc.charAt(i - 1) == '/') ? (i - 1) : i);
          // "br" and "p" tags are special; we put a newline in for them
          if ("br".equals(tag) || "p".equals(tag))
          {
            if (!justWroteNL)
            {
              sb.append("\r\n");
              justWroteNL = true;
              justWroteWS = false;
            }
          }
          lastTagStart = -1;
        }
      }
      else if (c == '<')
        lastTagStart = i;
      else
      {
        // We're outside of a tag. Just consolidate whitespace.
        if (c == '\t' || c == ' ')
        {
          if (!justWroteNL && !justWroteWS)
          {
            justWroteWS = true;
            sb.append(' ');
          }
        }
        else if (c == '\n')
        {
          // Apparently we should ignore these from some feedback from Fujitsu; and on comparison w/ an IE render it
          // does the same thing
          if (!justWroteNL && false)
          {
            sb.append('\n');
            justWroteNL = true;
          }
        }
        else if (c == '\r')
        {
          if (!justWroteNL)
          {
            // See if the next is '\n'
            if (i < currCleanDesc.length() - 1)
            {
              if (currCleanDesc.charAt(i + 1) == '\n')
              {
                justWroteNL = true;
                sb.append("\r\n");
              }
            }
          }
        }
        else
        {
          sb.append(c);
          justWroteNL = justWroteWS = false;
        }
      }
    }
    cleanDesc = Translate.decode(sb.toString()).trim(); // convert all of the entity refs
    return cleanDesc;
  }

  /**
   * @param contentEncoded The contentEncoded to set.
   */
  public void setContentEncoded(String contentEncoded) {
    this.contentEncoded = contentEncoded;
  }

  public void setMediaGroup(RSSMediaGroup mg)
  {
    this.mg = mg;
  }
  public RSSMediaGroup getMediaGroup()
  {
    return mg;
  }
  public void setEnclosure(RSSEnclosure enc)
  {
    this.encl = enc;
  }
  public RSSEnclosure getEnclosure()
  {
    return encl;
  }

  public void setDuration(long x)
  {
    duration = x;
  }
  public long getDuration()
  {
    return duration;
  }
}
