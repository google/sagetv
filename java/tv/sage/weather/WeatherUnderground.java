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
import sage.Sage;

/**
 *
 * @author jkardatzke
 */
public class WeatherUnderground
{
  // base URL for data retrieval (includes Google license key)
  public static String weather_url_prekey = "http://api.wunderground.com/api/";
  public static String weather_url_postkey = "/conditions/forecast7day/q/";
  private static final java.util.Map iconReplacementMap = new java.util.HashMap();

  private static WeatherUnderground myInstance;
  private static final Object chosenOneLock = new Object();

  public static WeatherUnderground getInstance()
  {
    if (myInstance == null) {
      synchronized (chosenOneLock) {
        if (myInstance == null) {
          myInstance = new WeatherUnderground();
        }
      }
    }
    return myInstance;
  }
  protected WeatherUnderground()
  {
    iconReplacementMap.put("chanceflurries", "flurries");
    iconReplacementMap.put("nt_chanceflurries", "flurries");
    iconReplacementMap.put("chancerain", "rain");
    iconReplacementMap.put("nt_chancerain", "rain");
    iconReplacementMap.put("chancesleet", "sleet");
    iconReplacementMap.put("nt_chancesleet", "sleet");
    iconReplacementMap.put("chancesnow", "snow");
    iconReplacementMap.put("nt_chancesnow", "snow");
    iconReplacementMap.put("chancetstorms", "tstorms");
    iconReplacementMap.put("nt_chancetstorms", "tstorms");
    iconReplacementMap.put("clear", "sunny");
    iconReplacementMap.put("nt_cloudy", "cloudy");
    iconReplacementMap.put("nt_flurries", "flurries");
    iconReplacementMap.put("nt_fog", "fog");
    iconReplacementMap.put("hazy", "fog");
    iconReplacementMap.put("nt_hazy", "fog");
    iconReplacementMap.put("mostlycloudy", "cloudy");
    iconReplacementMap.put("nt_mostlycloudy", "cloudy");
    iconReplacementMap.put("mostlysunny", "sunny");
    iconReplacementMap.put("nt_mostlysunny", "nt_clear");
    iconReplacementMap.put("partlycloudy", "partlysunny");
    iconReplacementMap.put("nt_partlysunny", "nt_partlycloudy");
    iconReplacementMap.put("nt_sleet", "sleet");
    iconReplacementMap.put("nt_rain", "rain");
    iconReplacementMap.put("nt_snow", "snow");
    iconReplacementMap.put("nt_sunny", "nt_clear");
    iconReplacementMap.put("nt_tstorms", "tstorms");
    iconReplacementMap.put("unknown", "tstorms");
    loadWeatherDataFromCache();
  }

  private String getIconString(String baseIcon)
  {
    String replaced = (String) iconReplacementMap.get(baseIcon);
    if (replaced != null)
      return replaced;
    else
      return baseIcon;
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

  public boolean updateNow()
  {
    if (updating) return true;
    try
    {
      updating = true;
      lastError = "";
      String loc = Sage.get("epg/zip_code", null);
      if (loc == null)
        loc = "94043";
      boolean updateCC = System.currentTimeMillis() - lastCCUpdateTime > 30*60*1000; // 30 minutes
      if (!updateCC && loc.equals(cachedLoc))
      {
        return true; // nothing to update right now, use the cache
      }
      String weatherKey = Sage.get("weather/wunderground_api_key", "");
      if (weatherKey.length() == 0)
        throw new Exception("Weather Underground API key not provided in property: weather/wunderground_api_key");
      String urlString = weather_url_prekey + weatherKey + weather_url_postkey + loc + ".xml";
      parseXmlFile(urlString, false, new UpdateHandler());

      saveWeatherDataToCache();
      Sage.put("weather/loc/zip", cachedLoc = loc);
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
    String[] keys = Sage.keys(prefix);
    java.util.Map rv = new java.util.HashMap();
    for (int i = 0; i < keys.length; i++)
      rv.put(keys[i], Sage.get(prefix + keys[i], null));
    return rv;
  }
  public java.util.Map getLocationProperties()
  {
    return getPropertiesWithPrefix("weather/loc/");
  }
  public java.util.Map getCurrentConditionProperties()
  {
    return getPropertiesWithPrefix("weather/cc/");
  }
  public java.util.Map getForecastProperties()
  {
    return getPropertiesWithPrefix("weather/forecast/");
  }
  public String getLocationInfo(String propName)
  {
    return Sage.get("weather/loc/" + propName, null);
    //Object rv = locMap.get(propName);
    //return (rv == null) ? "" : rv.toString();
  }
  public String getCurrentCondition(String propName)
  {
    return Sage.get("weather/cc/" + propName, null);
    //Object rv = ccMap.get(propName);
    //return (rv == null) ? "" : rv.toString();
  }
  public String getForecastCondition(String propName)
  {
    return Sage.get("weather/forecast/" + propName, null);
    //Object rv = fcMap.get(propName);
    //return (rv == null) ? "" : rv.toString();
  }
  public long getLastUpdateTime()
  {
    return lastCCUpdateTime;
  }
  public boolean isCurrentlyUpdating()
  {
    return updating;
  }

  private void saveWeatherDataToCache()
  {
    // Everything else will already have been put into the Sage.properties file
    Sage.put("weather/lastCCUpdateTime", Long.toString(lastCCUpdateTime));
  }

  private void loadWeatherDataFromCache()
  {
    try
    {
      lastCCUpdateTime = Sage.getLong("weather/lastCCUpdateTime", 0);
    }catch (NumberFormatException e){}
    cachedLoc = Sage.get("weather/loc/zip", null);
  }


  private long lastCCUpdateTime;
  private boolean updating;
  private String lastError;
  private String cachedLoc;

  //This method gets the xml out of the document based on the tag and the parent
  //Caveat, it goes off the first match -- limit the matches using element selection
  /*XXX
    public static String getXML(Element doc, String tag, String parent) {
	String tmpstr = null;
	try {
	    NodeList tmpNodes = doc.getElementsByTagName(tag);

	    //get the first match
	    for (int i = 0; i < tmpNodes.getLength() && tmpstr==null; i++)
	    {Untitled
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

	return tmpstr;Untitled
    }
   */

  // Source for sample: http://javaalmanac.com/egs/javax.xml.transform/WriteDom.html
  // Parses an XML file and returns a DOM document.
  // If validating is true, the contents is validated against the DTD
  // specified in the file.
  public void parseXmlFile(String xml_url, boolean validating, DefaultHandler hnd) throws Exception
  {
    if (Sage.DBG) System.out.println("downloading from:" + xml_url);
    java.io.InputStream in = null;
    try
    {
      java.net.URL u = new java.net.URL(xml_url);
      java.net.URLConnection con = u.openConnection();
      con.setConnectTimeout(30000);
      con.setReadTimeout(30000);
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

  private class UpdateHandler extends DefaultHandler
  {
    private String current_tag;
    private StringBuffer buff = new StringBuffer();
    private boolean inCC = false;
    private boolean inForecast = false;
    private boolean inDayFC = false;
    private boolean inLow = false;
    private boolean inHigh = false;
    private boolean inDate = false;
    private boolean foundWeather;
    private String unit_temp = "\u00b0";//\u00b0F";  // degrees F
    private String unit_dist = "mi";
    private String unit_speed = "mph";
    private String unit_press = "in";
    private String wind_t = null;
    private String wind_s = null;
    private String bar_r = null;
    private String bar_d = null;
    private int numLinks = 0;
    private String link_l = null;
    private String link_t = null;
    private String currDateNum = null;
    private String currDayName = null;

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
    {
      if ("response".equals(qName))
        foundWeather = true;
      else if ("current_observation".equals(qName))
        inCC = true;
      else if ("simpleforecast".equals(qName))
      {
        inForecast = true;
      }
      else if (inForecast && "forecastday".equals(qName))
        inDayFC = true;
      else if (inDayFC && "high".equals(qName))
      {
        inHigh = true;
      }
      else if (inDayFC && "low".equals(qName))
      {
        inLow = true;
      }
      else if (inDayFC && "date".equals(qName))
      {
        inDate = true;
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
      if ("err".equals(qName))
        System.out.println(lastError = ("Weather Underground returned error status " + data));
      else if ("current_observation".equals(qName))
      {
        inCC = false;
        lastCCUpdateTime = System.currentTimeMillis();
      }
      else if (inCC)
      {
        if ("temp_f".equals(qName))
          Sage.put("weather/cc/curr_temp", ((int)Math.round(Float.parseFloat(data))) + unit_temp);
        else if ("icon_url".equals(qName)) // parse the URL so we get the day/night differential in the icons
          Sage.put("weather/cc/curr_icon", getIconString(data.substring(data.lastIndexOf('/') + 1, data.lastIndexOf('.'))));
      }
      else if ("simpleforecast".equals(qName))
      {
        inForecast = false;
      }
      else if ("forecastday".equals(qName))
      {
        inDayFC = false;
        currDateNum = null;
        currDayName = null;
      }
      else if ("high".equals(qName))
      {
        inHigh = false;
      }
      else if ("low".equals(qName))
      {
        inLow = false;
      }
      else if ("date".equals(qName))
      {
        inDate = false;
      }
      else if (inDayFC)
      {
        if (inDate && "weekday".equals(qName))
        {
          currDayName = data;
          if (currDateNum != null)
            Sage.put("weather/forecast/day" + currDateNum, currDayName);
        }
        else if ("period".equals(qName))
        {
          currDateNum = data;
          if (currDayName != null)
            Sage.put("weather/forecast/day" + currDateNum, currDayName);
        }
        else if (currDateNum != null)
        {
          if ("fahrenheit".equals(qName))
          {
            if (data.length() > 0)
            {
              if (inLow)
                Sage.put("weather/forecast/low" + currDateNum, data + unit_temp);
              else if (inHigh)
                Sage.put("weather/forecast/high" + currDateNum, data + unit_temp);
            }
          }
          else if ("conditions".equals(qName))
            Sage.put("weather/forecast/conditions" + currDateNum, data);
          else if ("pop".equals(qName))
            Sage.put("weather/forecast/precip" + currDateNum, data + "%");
          else if ("icon_url".equals(qName))
            Sage.put("weather/forecast/icon" + currDateNum, getIconString(data.substring(data.lastIndexOf('/') + 1, data.lastIndexOf('.'))));
        }
      }
    }

    public void endDocument() throws SAXException
    {
      if (!foundWeather)
        System.out.println(lastError = ("Weather Underground returned empty doc"));
    }
  }

}
