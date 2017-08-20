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
/*
 * Usage of version updated for SageTV integration in the Studio:
 *
 * First get an instance of this class using:
 * Weather = tv_sage_weather_WeatherDotCom_getInstance()
 *
 * Then you can set the units/locationID using:
 * tv_sage_weather_WeatherDotCom_setUnits(Weather, "s")
 * tv_sage_weather_WeatherDotCom_setLocationID(Weather, "USCA0924")
 *
 * You can search for location IDs this way, its returns a java.util.Map of Names to LocationIDs:
 * tv_sage_weather_WeatherDotCom_searchLocations(Weather, "Chicago")
 *
 * To cause an update to occur (it respects caching, returns a boolean):
 * tv_sage_weather_WeatherDotCom_updateNow()
 *
 * To Get more info:
 * tv_sage_weather_WeatherDotCom_getLocationID(Weather)
 * tv_sage_weather_WeatherDotCom_getUnits(Weather)
 * tv_sage_weather_WeatherDotCom_getLastError(Weather)
 * tv_sage_weather_WeatherDotCom_getLocationProperties(Weather) returns a Map
 * tv_sage_weather_WeatherDotCom_getCurrentConditionProperties(Weather) returns a Map
 * tv_sage_weather_WeatherDotCom_getForecastProperties(Weather) returns a Map
 * tv_sage_weather_WeatherDotCom_getSponsorProperties(Weather) returns a Map
 * tv_sage_weather_WeatherDotCom_getLocationInfo(Weather, String property) returns a String
 * tv_sage_weather_WeatherDotCom_getCurrentCondition(Weather, String property) returns a String
 * tv_sage_weather_WeatherDotCom_getForecastCondition(Weather, String property) returns a String
 * tv_sage_weather_WeatherDotCom_getSponsorLinks(Weather, String property) returns a String
 * tv_sage_weather_WeatherDotCom_getLastUpdateTime(Weather)
 * tv_sage_weather_WeatherDotCom_isCurrentlyUpdating(Weather)
 */
package tv.sage.weather;

import java.net.*;
import java.io.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Date;

public class WeatherDotCom
{
  // variables for replacing weatherroom with weather.com if specified
  public static String weathercom_url = "http://xoap.weather.com/weather/local/";

  // arguments for get (excluding units and days)
  public static String partner_id="1010850608";
  public static String licence_key="c52f908e1cce80a5";

  // cache duration: 60 minutes
  public static long cache_duration=60*60*1000; //milliseconds

  private static WeatherDotCom myInstance;
  private static final Object chosenOneLock = new Object();

  public static WeatherDotCom getInstance() {
    if (myInstance == null) {
      synchronized (chosenOneLock) {
        if (myInstance == null) {
          myInstance = new WeatherDotCom();
        }
      }
    }
    return myInstance;
  }
  protected WeatherDotCom()
  {
    loadWeatherDataFromCache();
  }
  /*XXX
	public static String getElementText(Element elem)
	{
	    NodeList children = elem.getChildNodes();
	    for (int child = 0; child < children.getLength(); child++)
		{
			if (children.item(child).getNodeType() == Node.TEXT_NODE)
			{
//			    System.out.println("value" +children.item(child).getNodeValue());
			    return children.item(child).getNodeValue();
		    }
	    }
		return "";
	}
   */
  public java.util.Map searchLocations(String query)
  {
    try
    {
      String searchURLString = "http://xoap.weather.com/search/search?where=" +
          java.net.URLEncoder.encode(query, "UTF-8");
      SearchHandler searchy = new SearchHandler();
      parseXmlFile(searchURLString, false, searchy);
      if (searchy.getSearchResults() == null)
        System.out.println(lastError = "Weather.com returned empty doc");
      return searchy.getSearchResults();
      /*XXX
			Element searchRes = (Element)searchDoc.getElementsByTagName("search").item(0);

			// check for error result
			if (searchRes == null)
			{
				// probably an error doc
				if (searchDoc.getElementsByTagName("error").getLength() >= 1)
				{
//					output_error(output_filename,
//						"Failed to read from weather source",
//						"weather.com returned error status: " + getXML(doc.getDocumentElement(), "err", "error"));
					System.out.println(lastError = ("Weather.com returned error status " + getXML(searchDoc.getDocumentElement(), "err", "error")));
				}
				else
				{
//					output_error(output_filename,
//						"Failed to read from weather source",
//						"weather.com returned empty doc");
					System.out.println(lastError = "Weather.com returned empty doc");
				}
				return null;
			}
       */
      //			java.util.Map rv = new java.util.HashMap();
      /*XXX		    NodeList results = searchRes.getElementsByTagName("loc");
		    for (int i = 0; i < results.getLength(); i++)
		    {
				Element result = (Element)results.item(i);
				String locID = result.getAttribute("id");
				String locName = getElementText(result);
				rv.put(locID, locName);
		    }*/
      //			return rv;
    }
    catch (Exception e)
    {
      System.out.println(lastError = ("Error with weather parsing:" + e));
      return null;
    }
  }
  public void setUnits(String units)
  {
    if (units != null && !units.equals(myUnits))
    {
      // Blow away the cache
      lastLocUpdateTime = lastCCUpdateTime = lastFCUpdateTime = lastSponsorUpdateTime = 0;
    }

    myUnits = units;
  }
  public String getUnits()
  {
    return myUnits;
  }
  public String getLocationID()
  {
    return myLocId;
  }
  public void setLocationID(String locID)
  {
    if (locID != null && !locID.equals(myLocId))
    {
      // Blow away the cache
      lastLocUpdateTime = lastCCUpdateTime = lastFCUpdateTime = lastSponsorUpdateTime = 0;
    }

    myLocId = locID;
  }
  public String getLastError()
  {
    return lastError;
  }

  private String getWindInfo(String windSpeed, String speedUnits)
  {
    try
    {
      Integer.parseInt(windSpeed);
      return windSpeed + speedUnits;
    }
    catch (NumberFormatException e)
    {
      return windSpeed;
    }
  }

  // Returns true if an update occurred successfully; false otherwise
  public boolean updateNow()
  {
    if (updating) return true;
    if (myLocId == null || myLocId.length() == 0) return false;
    try
    {
      updating = true;
      lastError = "";
      boolean updateCC = (lastCCUpdateTime == 0) || (System.currentTimeMillis() - lastCCUpdateTime > 30*60*1000); // 30 minutes
      boolean updateLinks = (lastSponsorUpdateTime == 0) || (System.currentTimeMillis() - lastSponsorUpdateTime > 12*60*60*1000); // 12 hours
      boolean updateFC = (lastFCUpdateTime == 0) || (System.currentTimeMillis() - lastFCUpdateTime > 2*60*60*1000); // 2 hours
      if (!updateLinks && !updateCC && !updateFC)
      {
        return true; // nothing to update right now, use the cache
      }
      updateLinks = true; // TWC always requires this now for some reason
      String urlString = "http://xoap.weather.com/weather/local/" + myLocId +
          "?cc=*&prod=xoap&par=" + partner_id + "&key=" + licence_key + "&unit=" + myUnits;
      if (updateFC)
        urlString += "&dayf=10";
      if (updateLinks)
        urlString += "&link=xoap";
      parseXmlFile(urlString, false, new UpdateHandler());

      saveWeatherDataToCache();
    }
    catch (Exception e)
    {
      System.out.println(lastError = ("Error with weather parsing:" + e));
      e.printStackTrace();
      return false;
    }
    finally
    {
      updating = false;
    }
    return true;
  }
  private java.util.Map getPropertiesWithPrefix(String prefix)
  {
    java.util.Enumeration walker = props.propertyNames();
    java.util.Map rv = new java.util.HashMap();
    while (walker.hasMoreElements())
    {
      Object elem = walker.nextElement();
      if (elem != null && elem.toString().startsWith(prefix))
      {
        rv.put(elem.toString().substring(prefix.length()), props.getProperty(elem.toString()));
      }
    }
    return rv;
  }
  public java.util.Map getLocationProperties()
  {
    return getPropertiesWithPrefix("loc/");
  }
  public java.util.Map getCurrentConditionProperties()
  {
    return getPropertiesWithPrefix("cc/");
  }
  public java.util.Map getForecastProperties()
  {
    return getPropertiesWithPrefix("forecast/");
  }
  public java.util.Map getSponsorProperties()
  {
    return getPropertiesWithPrefix("sponsor/");
  }
  public String getLocationInfo(String propName)
  {
    return props.getProperty("loc/" + propName);
    //Object rv = locMap.get(propName);
    //return (rv == null) ? "" : rv.toString();
  }
  public String getCurrentCondition(String propName)
  {
    return props.getProperty("cc/" + propName);
    //Object rv = ccMap.get(propName);
    //return (rv == null) ? "" : rv.toString();
  }
  public String getForecastCondition(String propName)
  {
    return props.getProperty("forecast/" + propName);
    //Object rv = fcMap.get(propName);
    //return (rv == null) ? "" : rv.toString();
  }
  public String getSponsorLinks(String propName)
  {
    return props.getProperty("sponsor/" + propName);
    //Object rv = sponsorMap.get(propName);
    //return (rv == null) ? "" : rv.toString();
  }
  public long getLastUpdateTime()
  {
    return Math.max(lastLocUpdateTime, Math.max(lastCCUpdateTime, Math.max(lastFCUpdateTime, lastSponsorUpdateTime)));
  }
  public boolean isCurrentlyUpdating()
  {
    return updating;
  }

  private void saveWeatherDataToCache()
  {
    props.put("lastLocUpdateTime", Long.toString(lastLocUpdateTime));
    props.put("lastCCUpdateTime", Long.toString(lastCCUpdateTime));
    props.put("lastFCUpdateTime", Long.toString(lastFCUpdateTime));
    props.put("lastSponsorUpdateTime", Long.toString(lastSponsorUpdateTime));
    if (myLocId != null)
    {
      props.put("locID", myLocId);
    }
    if (myUnits != null)
    {
      props.put("units", myUnits);
    }
    java.io.File cacheFile = new java.io.File(("") + "weather_cache.properties");
    java.io.OutputStream out = null;
    try
    {
      out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(cacheFile));
      props.store(out, "SageTV Weather Data");
    }
    catch (Exception e)
    {
      System.out.println("Error caching weather data of:" + e);
    }
    finally
    {
      if (out != null)
      {
        try{out.close();}catch(Exception e){}
        out = null;
      }
    }
  }

  private void loadWeatherDataFromCache()
  {
    props = new java.util.Properties();
    java.io.File cacheFile = new java.io.File(("") + "weather_cache.properties");
    java.io.InputStream in = null;
    try
    {
      in = new java.io.BufferedInputStream(new java.io.FileInputStream(cacheFile));
      props.load(in);
    }
    catch (Exception e)
    {
      System.out.println("Error reading cached weather data of:" + e);
    }
    finally
    {
      if (in != null)
      {
        try{in.close();}catch(Exception e){}
        in = null;
      }
    }
    try
    {
      lastLocUpdateTime = Long.parseLong(props.getProperty("lastLocUpdateTime", "0"));
      lastFCUpdateTime = Long.parseLong(props.getProperty("lastFCUpdateTime", "0"));
      lastCCUpdateTime = Long.parseLong(props.getProperty("lastCCUpdateTime", "0"));
      lastSponsorUpdateTime = Long.parseLong(props.getProperty("lastSponsorUpdateTime", "0"));
    }catch (NumberFormatException e){}
    myLocId = props.getProperty("locID");
    myUnits = props.getProperty("units", "s");
  }

  private String myLocId;
  private String myUnits = "s";
  private java.util.Properties props = new java.util.Properties();

  private long lastLocUpdateTime;
  private long lastCCUpdateTime;
  private long lastFCUpdateTime;
  private long lastSponsorUpdateTime;
  private boolean updating;
  private String lastError;

  //This method gets the xml out of the document based on the tag and the parent
  //Caveat, it goes off the first match -- limit the matches using element selection
  /*XXX
    public static String getXML(Element doc, String tag, String parent) {
	String tmpstr = null;
	try {
	    NodeList tmpNodes = doc.getElementsByTagName(tag);

	    //get the first match
	    for (int i = 0; i < tmpNodes.getLength() && tmpstr==null; i++)
	    {
//		System.out.println(i);
		if ((((Element)(tmpNodes.item(i).getParentNode())).getTagName()).equals(parent))
		{
		    // check children for value
//		    System.out.println("got an  xml node  for "+parent+"/"+tag);
//		    System.out.println("Node: "+((Element)tmpNodes.item(i)).getTagName());
//		    System.out.println("Node text: "+tmpNodes.item(i).toString());

		    NodeList children=tmpNodes.item(i).getChildNodes();
		    for ( int child=0; child < children.getLength(); child++) {
			if ( children.item(child).getNodeType()==Node.TEXT_NODE ) {
//			    System.out.println("value" +children.item(child).getNodeValue());
			    tmpstr=children.item(child).getNodeValue();
			    break;
			}
		    }
		}
	    }

	}
	catch (Exception e) {
	    System.out.println("Failed to get xml value for "+parent+"/"+tag);
	    e.printStackTrace();
	}

	if (tmpstr ==null)
	    // found nothing, return blank
	    return "";

	// clean up string

	tmpstr.trim();

	//gets rid of the weird (A) character
	if (tmpstr.indexOf((char)194) != -1)
	{
	    tmpstr = tmpstr.substring(0, tmpstr.indexOf((char)194)) + tmpstr.substring(tmpstr.indexOf((char)194) + 1);
	}

	//trim didn't do its job
	if ((int)tmpstr.charAt(0) == 32)
	    tmpstr = tmpstr.substring(1);

	if ((int)tmpstr.charAt(tmpstr.length() -1) == 32)
	    tmpstr = tmpstr.substring(0, tmpstr.length() - 1);

	return tmpstr;
    }
   */

  // Source for sample: http://javaalmanac.com/egs/javax.xml.transform/WriteDom.html
  // Parses an XML file and returns a DOM document.
  // If validating is true, the contents is validated against the DTD
  // specified in the file.
  public void parseXmlFile(String xml_url, boolean validating, DefaultHandler hnd) throws Exception
  {
    System.out.println("downloading from:" + xml_url);
    java.io.InputStream in = null;
    try
    {
      java.net.URL u = new java.net.URL(xml_url);
      java.net.URLConnection con = u.openConnection();
      in = u.openStream();

      factory.setValidating(validating);
      // Create the builder and parse the file
      factory.newSAXParser().parse(in, hnd);
    }
    finally
    {
      if (in != null)
      {
        try
        {
          in.close();
        }
        catch (Exception e)
        {}
      }
    }
  }

  private SAXParserFactory factory = SAXParserFactory.newInstance();

  private class SearchHandler extends DefaultHandler
  {
    private String current_tag;
    private StringBuffer buff = new StringBuffer();
    private String currID;
    private java.util.Map rv;
    public java.util.Map getSearchResults()
    {
      return rv;
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
    {
      if ("search".equalsIgnoreCase(qName))
      {
        rv = new java.util.HashMap();
      }
      else if (rv != null && "loc".equalsIgnoreCase(qName) && attributes != null)
      {
        currID = attributes.getValue("id");
      }
      current_tag = qName;
    }
    public void characters(char[] ch, int start, int length)
    {
      String data = new String(ch,start,length);

      //Jump blank chunk
      if (data.trim().length() == 0)
        return;
      buff.append(data);
    }
    public void endElement(String uri, String localName, String qName)
    {
      String data = buff.toString().trim();

      if (qName.equals(current_tag))
        buff = new StringBuffer();
      if ("err".equalsIgnoreCase(qName))
      {
        System.out.println(lastError = ("Weather.com returned error status " + data));
      }
      else if ("loc".equalsIgnoreCase(qName) && rv != null && currID != null)
      {
        rv.put(currID, data);
      }
    }
  }

  private class UpdateHandler extends DefaultHandler
  {
    private String current_tag;
    private StringBuffer buff = new StringBuffer();
    private boolean inHead = false;
    private boolean inLoc = false;
    private boolean inCC = false;
    private boolean inBar = false;
    private boolean inWind = false;
    private boolean inUV = false;
    private boolean inMoon = false;
    private boolean inLinks = false;
    private boolean inDayFC = false;
    private boolean foundWeather;
    private String unit_temp = "";
    private String unit_dist = "";
    private String unit_speed = "";
    private String unit_press = "";
    private String wind_t = null;
    private String wind_s = null;
    private String bar_r = null;
    private String bar_d = null;
    private int numLinks = 0;
    private String link_l = null;
    private String link_t = null;
    private String currDateNum = null;
    private String currPart = null;

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
    {
      if ("weather".equals(qName))
        foundWeather = true;
      else if ("head".equals(qName))
        inHead = true;
      else if ("loc".equals(qName))
        inLoc = true;
      else if ("cc".equals(qName))
        inCC = true;
      else if (inCC && "bar".equals(qName))
        inBar = true;
      else if (inCC && "uv".equals(qName))
        inUV = true;
      else if (inCC && "wind".equals(qName))
        inWind = true;
      else if (inCC && "moon".equals(qName))
        inMoon = true;
      else if ("lnks".equals(qName))
        inLinks = true;
      else if ("dayf".equals(qName))
        inDayFC = true;
      else if (inDayFC && "day".equals(qName))
      {
        currDateNum = attributes.getValue("d");
        props.put("forecast/date" + currDateNum, attributes.getValue("t") + " " + attributes.getValue("dt"));
      }
      else if (inDayFC && currDateNum != null && "part".equals(qName))
      {
        currPart = attributes.getValue("p");
      }
      else if (inDayFC && currDateNum != null && currPart != null && "wind".equals(qName))
        inWind = true;
      current_tag = qName;
    }
    public void characters(char[] ch, int start, int length)
    {
      String data = new String(ch,start,length);

      //Jump blank chunk
      if (data.trim().length() == 0)
        return;
      buff.append(data);
    }
    public void endElement(String uri, String localName, String qName)
    {
      String data = buff.toString().trim();

      if (qName.equals(current_tag))
        buff = new StringBuffer();
      if ("err".equals(qName))
        System.out.println(lastError = ("Weather.com returned error status " + data));
      else if ("head".equals(qName))
        inHead = false;
      else if (inHead)
      {
        if ("ut".equals(qName))
          unit_temp = "\u00b0" + data;
        else if ("ud".equals(qName))
          unit_dist = data;
        else if ("us".equals(qName))
          unit_speed = data;
        else if ("up".equals(qName))
          unit_press = data;
      }
      else if ("loc".equals(qName))
        inLoc = false;
      else if (inLoc)
      {
        if ("dnam".equals(qName))
          props.put("loc/curr_location", data);
        else if ("sunr".equals(qName))
          props.put("loc/curr_sunrise", data);
        else if ("suns".equals(qName))
          props.put("loc/curr_sunset", data);
      }
      else if ("cc".equals(qName))
      {
        inCC = false;
        lastCCUpdateTime = System.currentTimeMillis();
      }
      else if (inCC)
      {
        if ("wind".equals(qName))
          inWind = false;
        else if ("uv".equals(qName))
          inUV = false;
        else if ("bar".equals(qName))
          inBar = false;
        else if ("moon".equals(qName))
          inMoon = false;
        else if (inMoon)
        {

        }
        else if (inWind)
        {
          if ("t".equals(qName))
            wind_t = data;
          else if ("s".equals(qName))
            wind_s = getWindInfo(data, unit_speed);
          if (wind_t != null && wind_s != null)
          {
            props.put("cc/curr_wind", wind_t + " " + wind_s);
            wind_t = wind_s = null;
          }
        }
        else if (inUV)
        {
          if ("i".equals(qName))
            props.put("cc/curr_uv_index", data);
          else if ("t".equals(qName))
            props.put("cc/curr_uv_warning", data);
        }
        else if (inBar)
        {
          if ("r".equals(qName))
            bar_r = data + unit_press;
          else if ("d".equals(qName))
            bar_d = data;
          if (bar_r != null && bar_d != null)
            props.put("cc/curr_pressure", bar_r + " " + bar_d);
        }
        else if ("obst".equals(qName))
          props.put("cc/curr_recorded_at", data);
        else if ("lsup".equals(qName))
          props.put("cc/curr_updated", data);
        else if ("t".equals(qName))
          props.put("cc/curr_conditions", data);
        else if ("vis".equals(qName))
          props.put("cc/curr_visibility", data + unit_dist);
        else if ("tmp".equals(qName))
          props.put("cc/curr_temp", data + unit_temp);
        else if ("hmid".equals(qName))
          props.put("cc/curr_humidity", data + "%");
        else if ("dewp".equals(qName))
          props.put("cc/curr_dewpoint", data + unit_temp);
        else if ("flik".equals(qName))
        {
          props.put("cc/curr_heatindex", data + unit_temp);
          props.put("cc/curr_windchill", data + unit_temp);
        }
        else if ("icon".equals(qName))
          props.put("cc/curr_icon", data);
      }
      else if ("lnks".equals(qName))
      {
        inLinks = false;
        props.put("sponsor/num_links", Integer.toString(numLinks));
        lastSponsorUpdateTime = System.currentTimeMillis();
      }
      else if (inLinks)
      {
        if ("link".equals(qName))
        {
          if (link_l != null && link_t != null)
          {
            props.put("sponsor/linkurl" + numLinks, link_l + "&par=" + partner_id);
            props.put("sponsor/linktitle" + numLinks, link_t);
            numLinks++;
          }
          link_l = null;
          link_t = null;
        }
        else if ("l".equals(qName))
          link_l = data;
        else if ("t".equals(qName))
          link_t = data;
      }
      else if ("dayf".equals(qName))
      {
        inDayFC = false;
        lastFCUpdateTime = System.currentTimeMillis();
      }
      else if (inDayFC)
      {
        if ("day".equals(qName))
          currDateNum = null;
        else if (currDateNum != null)
        {
          if ("part".equals(qName))
            currPart = null;
          else if (currPart != null)
          {
            if ("wind".equals(qName))
              inWind = false;
            else if (inWind)
            {
              if ("t".equals(qName))
                wind_t = data;
              else if ("s".equals(qName))
                wind_s = getWindInfo(data, unit_speed);
              if (wind_t != null && wind_s != null)
              {
                props.put("forecast/wind" + currPart + currDateNum, wind_t + " " + wind_s);
                wind_t = wind_s = null;
              }
            }
            else if ("t".equals(qName))
              props.put("forecast/conditions" + currPart + currDateNum, data);
            else if ("ppcp".equals(qName))
              props.put("forecast/precip" + currPart + currDateNum, data + "%");
            else if ("hmid".equals(qName))
              props.put("forecast/humid" + currPart + currDateNum, data + "%");
            else if ("icon".equals(qName))
              props.put("forecast/icon" + currPart + currDateNum,data);
          }
          else if ("hi".equals(qName))
            props.put("forecast/hi" + currDateNum, data + unit_temp);
          else if ("low".equals(qName))
            props.put("forecast/low" + currDateNum, data + unit_temp);
          else if ("sunr".equals(qName))
            props.put("forecast/sunrise" + currDateNum, data);
          else if ("suns".equals(qName))
            props.put("forecast/sunset" + currDateNum, data);
        }
      }
    }

    public void endDocument() throws SAXException
    {
      if (!foundWeather)
        System.out.println(lastError = ("Weather.com returned empty doc"));
    }
  }

}
