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
package sage.epg.sd;

import sage.DBObject;
import sage.Person;
import sage.Pooler;
import sage.Sage;
import sage.Wizard;
import sage.epg.sd.gson.Gson;
import sage.epg.sd.gson.GsonBuilder;
import sage.epg.sd.gson.JsonElement;
import sage.epg.sd.gson.JsonObject;
import sage.epg.sd.gson.JsonParseException;
import sage.epg.sd.json.SDError;
import sage.epg.sd.json.images.SDProgramImages;
import sage.epg.sd.json.images.SDProgramImagesDeserializer;
import sage.epg.sd.json.locale.SDLanguage;
import sage.epg.sd.json.locale.SDLanguageDeserializer;
import sage.epg.sd.json.locale.SDRegion;
import sage.epg.sd.json.locale.SDRegionDeserializer;
import sage.epg.sd.json.map.SDLineupMap;
import sage.epg.sd.json.map.SDLineupMapDeserializer;
import sage.epg.sd.json.programs.SDPerson;
import sage.epg.sd.json.programs.SDSeriesDescArrayDeserializer;
import sage.epg.sd.json.programs.SDProgramMetadata;
import sage.epg.sd.json.programs.SDMetadataDeserializer;
import sage.epg.sd.json.programs.SDSeriesDescArray;
import sage.epg.sd.json.schedules.SDProgramSchedule;
import sage.epg.sd.json.schedules.SDProgramScheduleDeserializer;
import sage.epg.sd.json.schedules.SDScheduleMd5Array;
import sage.epg.sd.json.schedules.SDScheduleMd5ArrayDeserializer;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import static sage.epg.sd.SDSession.TIMEOUT;
import static sage.epg.sd.SDSession.USER_AGENT;

public class SDUtils
{
  // This can be a time consuming object to create, so we create it once here and use this instance
  // for everything.
  public static final Gson GSON;

  static
  {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setLenient();
    gsonBuilder.registerTypeAdapter(SDRegion[].class, new SDRegionDeserializer());
    gsonBuilder.registerTypeAdapter(SDLanguage[].class, new SDLanguageDeserializer());
    gsonBuilder.registerTypeAdapter(SDLineupMap.class, new SDLineupMapDeserializer());
    gsonBuilder.registerTypeAdapter(SDProgramMetadata.class, new SDMetadataDeserializer());
    gsonBuilder.registerTypeAdapter(SDSeriesDescArray.class, new SDSeriesDescArrayDeserializer());
    gsonBuilder.registerTypeAdapter(SDScheduleMd5Array.class, new SDScheduleMd5ArrayDeserializer());
    gsonBuilder.registerTypeAdapter(SDProgramImages[].class, new SDProgramImagesDeserializer());
    gsonBuilder.registerTypeAdapter(SDProgramSchedule.class, new SDProgramScheduleDeserializer());
    GSON = gsonBuilder.create();
  }
  
  //use the non token required "ip_isblocked" end point to determine if the user is blocked
  //if an error is returned by the end point then the user is BLOCKED
  public static boolean isSDBlocked() throws IOException, SDException
  {
    URL url = SDSession.GET_IS_BLOCKED;
    HttpsURLConnection connection = (HttpsURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    connection.setConnectTimeout(TIMEOUT);
    connection.setReadTimeout(TIMEOUT);
    connection.setRequestProperty("User-Agent", USER_AGENT);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Accept-Encoding", "deflate,gzip");
    connection.setRequestProperty("Accept-Charset", "ISO-8859-1");
    //secret SD debug mode that will send requests to their debug server. Only enable when working with SD Support
    if(Sage.getBoolean("debug_sd_support", false)) {
      connection.setRequestProperty("RouteTo", "debug");
      if (Sage.DBG) System.out.println("****debug_sd_support**** property set. Sending 'get' with url '" + url);
    }
    if (connection.getResponseCode() == 403){
      if (Sage.DBG) System.out.println("SDUtils:isSDBlocked - 403 response received - account is BLOCKED");
        return true;
    }else{
        //if (Sage.DBG) System.out.println("SDUtils:isSDBlocked - connection response:" + connection.getResponseCode());
        return false;
    }
  }
  public static int handleSDJsonErrorFromHttpResponse(HttpURLConnection httpConn) throws IOException
  {
    InputStream inputStream = getDecodedInputStream(httpConn.getContentEncoding(), httpConn.getInputStream());
    InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.ISO_8859_1);
    JsonElement errorElement = GSON.fromJson(reader, JsonElement.class);
    if (errorElement instanceof JsonObject)
    {
        JsonElement codeElement = ((JsonObject) errorElement).get("code");
        int code = codeElement != null ? codeElement.getAsInt() : -1;
        if (Sage.DBG) System.out.println("SDUtils.handleSDJsonErrorFromHttpResponse: Error:" + code + " : " + SDErrors.getErrorForCode(code));
        return code;
    }else{
        if (Sage.DBG) System.out.println("SDUtils.handleSDJsonErrorFromHttpResponse: Unknown Error");
        return -9999;
    }
  }
  
  public static ByteArrayInputStream createBlankImageInputStream(int width, int height, String format) {
    // Create a blank BufferedImage (white background)
    BufferedImage blankImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    // Fill the image with white color
    blankImage.createGraphics().fillRect(0, 0, width, height);

    // Convert BufferedImage to byte array using ImageIO
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
        ImageIO.write(blankImage, format, baos);
    } catch (IOException e) {
        throw new RuntimeException("Failed to write blank image to stream", e);
    }

    // Convert byte array to ByteArrayInputStream
    return new ByteArrayInputStream(baos.toByteArray());
  }  

  /**
   * Determine what kind of stream is returned and wrap it with an appropriate processing layer.
   *
   * @param connection A <code>HttpsURLConnection</code> ready to receive.
   * @return An <code>InputStreamReader</code> based on the <code>InputStream</code> obtained from
   *         the provided <code>HttpsURLConnection</code>.
   * @throws IOException
   */
  public static InputStreamReader getStream(HttpsURLConnection connection) throws IOException, SDException
  {
    // Determine how we should get the stream and if we should assume there's an error.
    boolean errorPresent = connection.getResponseCode() == 400;
    boolean errorPresent403 = connection.getResponseCode() == 403;
    InputStream inputStream;

    // Schedules Direct returns code 400 for bad JSON and incorrect credentials, but Java will throw
    // an exception so we need to treat this error code like it's not an error.
    if (errorPresent)
    {
      if (SDSession.debugEnabled())
      {
        SDSession.writeDebugLine("HTTP 400 returned");
      }
      inputStream = connection.getErrorStream();
    }
    //process 403 error which indicates debug_sd_support is enabled but SD is not accepting that on their side
    else if (errorPresent403){
      if (SDSession.debugEnabled())
      {
        SDSession.writeDebugLine("HTTP 403 received. Processing");
      }
      inputStream = connection.getErrorStream();
    }
    else
    {
      inputStream = connection.getInputStream();
    }

    inputStream = getDecodedInputStream(connection.getContentEncoding(), inputStream);

    InputStreamReader reader;
    reader = new InputStreamReader(inputStream, SDSession.IN_CHARSET);

    // Process 403 errors based on the returned JSON code.
    if (errorPresent403)
    {
      if (SDSession.debugEnabled()){SDSession.writeDebugLine("HTTP 403 processing");}

      JsonElement errorElement = GSON.fromJson(reader, JsonElement.class);

      if (SDSession.debugEnabled())
      {
        SDSession.writeDebugLine(errorElement.toString());
      }

      if (errorElement instanceof JsonObject)
      {
        JsonElement codeElement = ((JsonObject) errorElement).get("code");
        int code = codeElement != null ? codeElement.getAsInt() : -1;

        // 2055 means RouteTo:debug is set but not enabled server-side.
        if (code == 2055)
        {
          if (SDSession.debugEnabled())
          {
            SDSession.writeDebugLine("HTTP 403 received with ERROR 2055. Disabling debug_sd_support.");
          }
          if (Sage.getBoolean("debug_sd_support", false))
          {
            Sage.putBoolean("debug_sd_support", false);
          }
        }

        SDErrors.throwErrorForCode(code);
      }

      throw new SDException(SDErrors.SAGETV_UNKNOWN);
    }

    //process 400 error
    if (errorPresent)
    {
      if (SDSession.debugEnabled()){SDSession.writeDebugLine("HTTP 400 processing");}

      JsonElement errorElement = GSON.fromJson(reader, JsonElement.class);

      if (SDSession.debugEnabled())
      {
        SDSession.writeDebugLine(errorElement.toString());
      }

      if (errorElement instanceof JsonObject)
      {
        JsonElement codeElement = ((JsonObject) errorElement).get("code");
        int code = codeElement != null ? codeElement.getAsInt() : -1;
        
        if (code==2055){
          if (SDSession.debugEnabled())
          {
              SDSession.writeDebugLine("HTTP 400 received with ERROR 2055. Disabling debug_sd_support.  Process will restart");
          }
          if(Sage.getBoolean("debug_sd_support", false)){
              Sage.putBoolean("debug_sd_support", false);
          }
        }else if (code==4009){
          if (SDSession.debugEnabled())
          {
              SDSession.writeDebugLine("HTTP 400 received with ERROR 4009. Process will restart");
          }
        }        

        SDErrors.throwErrorForCode(code);
      }

      throw new SDException(SDErrors.SAGETV_UNKNOWN);
    }
    
    // Check for error codes in HTTP 200 responses. During service outages, SD may return
    // HTTP 200 with error codes in the JSON body (e.g., "code": 3000 for SERVICE_OFFLINE).
    // This must be checked before treating the response as successful.
    if (connection.getResponseCode() >= 200 && connection.getResponseCode() < 300)
    {
      // Peek at the JSON to check for error codes
      JsonElement element = GSON.fromJson(reader, JsonElement.class);
      
      if (element instanceof JsonObject)
      {
        JsonElement codeElement = ((JsonObject) element).get("code");
        int code = codeElement != null ? codeElement.getAsInt() : 0;
        
        // If there's a non-zero error code in a 2xx response, treat it as an error
        if (code > 0 && code != 1)  // code 0 or 1 may indicate success
        {
          if (SDSession.debugEnabled())
          {
            SDSession.writeDebugLine("HTTP " + connection.getResponseCode() + " received with error code " + code + ": " + SDErrors.getErrorForCode(code));
          }
          SDErrors.throwErrorForCode(code);
        }
      }
      
      // Convert the parsed element back to a reader for normal processing
      String jsonString = element.toString();
      reader = new InputStreamReader(new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)), SDSession.IN_CHARSET);
    }
    
    if (SDSession.debugEnabled())
    {
      try
      {
        SDSession.writeDebugLine(connection.getURL() + " (received): ");
      }
      catch (Exception e)
      {
        SDSession.writeDebugLine("(received): ");
      }

      char[] transferBuffer = new char[32768];
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      OutputStreamWriter writer = new OutputStreamWriter(outputStream);
      int bytesRead;

      while (true)
      {
        bytesRead = reader.read(transferBuffer, 0, transferBuffer.length);

        if (bytesRead == -1)
          break;

        SDSession.writeDebug(transferBuffer, 0, bytesRead);
        writer.write(transferBuffer, 0, bytesRead);
      }

      SDSession.writeDebugLine("");
      writer.flush();
      reader = new InputStreamReader(new ByteArrayInputStream(outputStream.toByteArray()), SDSession.IN_CHARSET);
    }

    return reader;
  }

  static InputStream getDecodedInputStream(String contentEncoding, InputStream inputStream) throws IOException
  {
    if (inputStream == null)
      return null;

    BufferedInputStream bufferedInputStream = inputStream instanceof BufferedInputStream ?
      (BufferedInputStream)inputStream : new BufferedInputStream(inputStream);

    if (contentEncoding != null)
    {
      String normalizedEncoding = contentEncoding.toLowerCase();
      if (normalizedEncoding.contains("gzip"))
        return new GZIPInputStream(bufferedInputStream);
      if (normalizedEncoding.contains("deflate"))
        return new InflaterInputStream(bufferedInputStream);
    }

    // Some responses do not include Content-Encoding. Keep gzip auto-detection as a fallback.
    bufferedInputStream.mark(3);
    int firstByte = bufferedInputStream.read() & 0xff;
    int secondByte = bufferedInputStream.read() & 0xff;
    int thirdByte = bufferedInputStream.read() & 0xff;
    bufferedInputStream.reset();

    boolean gzipPresent = (((secondByte << 8) | firstByte) == GZIPInputStream.GZIP_MAGIC && thirdByte == 8);
    if (gzipPresent)
      return new GZIPInputStream(bufferedInputStream);

    return bufferedInputStream;
  }

  /**
   * Returns the error message and code if an error is present.
   *
   * @param object The Json object to check. A Json object is the only element that can contain an
   *               error message.
   * @return An error if one is present or <code>null</code> if no error is present.
   * @throws JsonParseException This exception is raised if there is a serious issue that occurs
   *                            during parsing of a Json string.
   */
  public static SDError getError(JsonObject object) throws JsonParseException
  {
    JsonElement jsonCode = object.get("code");

    if (jsonCode != null)
    {
      if (!jsonCode.isJsonPrimitive())
      {
        throw new JsonParseException("Unexpected format. " + jsonCode.toString());
      }
      final int code = jsonCode.getAsInt();

      if (code == 0)
        return null;

      final JsonElement message = object.get("message");

      if (message != null && message.isJsonPrimitive())
      {
        return new SDError()
        {
          @Override
          public int getCode()
          {
            return code;
          }

          @Override
          public String getMessage()
          {
            return message.getAsString();
          }
        };
      }
      else
      {
        return new SDError()
        {
          @Override
          public int getCode()
          {
            return code;
          }

          @Override
          public String getMessage()
          {
            return "Error code " + code + ".";
          }
        };
      }
    }

    return null;
  }

  public static int AU_FLAG = 0x40000000;
  public static int NZ_FLAG = 0x20000000;
  public static int AUNZ_FLAG = AU_FLAG | NZ_FLAG;

  public static int fromStationIDtoSageTV(String stationID) throws NumberFormatException
  {
    // This should not be happening since this is mandatory in all JSON that returns a stationID.
    if (stationID == null)
      return 0;

    if (stationID.length() > 12)
      throw new NumberFormatException("Expected <= 12 characters, got " + stationID.length());

    // This will usually be the case.
    if (stationID.length() > 2)
    {
      char char0 = stationID.charAt(0);
      char char1 = stationID.charAt(1);

      if (char0 == 'A' && char1 == 'U')
        return Integer.parseInt(stationID.substring(2)) | AU_FLAG;

      if (char0 == 'N' && char1 == 'Z')
        return Integer.parseInt(stationID.substring(2)) | NZ_FLAG;
    }

    int returnValue = Integer.parseInt(stationID);

    // If we ever see anything that ends up setting a prefix flags, we should log it because that
    // station is going to likely have problems.
    if ((returnValue & AUNZ_FLAG) != 0)
      System.out.println("Warning: StationID " + returnValue + " has set AUNZ_FLAG");

    return Integer.parseInt(stationID);
  }

  public static String fromSageTVtoStationID(int stationID)
  {
    // Australia and New Zealand are the only locations that might need this flag, so this is one
    // less check if you're not in one of those locations.
    if ((stationID & AUNZ_FLAG) != 0)
    {
      if ((stationID & AU_FLAG) != 0)
        return "AU" + Integer.toString(stationID & ~AU_FLAG);

      if ((stationID & NZ_FLAG) != 0)
        return "NZ" + Integer.toString(stationID & ~NZ_FLAG);
    }

    return Integer.toString(stationID);
  }

  private final static Object sdfFullLock = new Object();
  private static SimpleDateFormat sdfFull;
  public static long SDFullUTCToMillis(String utcTime)
  {
    synchronized (sdfFullLock)
    {
      if (utcTime == null)
        return 0;

      if (sdfFull == null)
      {
        sdfFull = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdfFull.setTimeZone(TimeZone.getTimeZone("GMT"));
      }

      // 2014-06-28T05:16:29Z
      try
      {
        return sdfFull.parse(utcTime.replace('T', ' ').substring(0, utcTime.length() - 1)).getTime();
      }
      catch (ParseException e)
      {
        System.out.println("Unable to parse full date " + utcTime);
        return 0;
      }
    }
  }

  private final static Object sdfDateLock = new Object();
  private static SimpleDateFormat sdfDate;
  private static int timeZoneOffset;
  public static void resetTimeZoneOffset()
  {
    synchronized (sdfDateLock)
    {
      // We are not accounting for DST.
      timeZoneOffset = TimeZone.getDefault().getRawOffset();
    }
  }

  public static long SDDateUTCToMillis(String utcDate)
  {
    synchronized (sdfDateLock)
    {
      if (utcDate == null)
        return 0;

      if (sdfDate == null)
      {
        sdfDate = new SimpleDateFormat("yyyy-MM-dd");
        sdfDate.setTimeZone(TimeZone.getTimeZone("GMT"));
        resetTimeZoneOffset();
      }

      // 2014-06-28
      try
      {
        // Add 12 hours and subtract the current timezone offset so that the time is always noon in
        // the current time zone (outside of DST).
        return sdfDate.parse(utcDate).getTime() + Sage.MILLIS_PER_HR * 12 - timeZoneOffset;
      }
      catch (ParseException e)
      {
        System.out.println("Unable to parse date " + utcDate);
        return 0;
      }
    }
  }

  public static String removeLeadingZeros(String channelNumber)
  {
    // Radio stations have 4 zeros total and without these, we can't distinguish between them and
    // normal channels, so we always leave those alone.
    if (channelNumber.length() == 4 && channelNumber.startsWith("0"))
      return channelNumber;

    char channel[] = channelNumber.toCharArray();
    int writeFrom = -1;

    for (int i = 0; i < channel.length; i++)
    {
      if (channel[i] == '0')
        continue;
      writeFrom = i;
      break;
    }

    if (writeFrom == -1)
      return "0";
    if (writeFrom == 0)
      return channelNumber;

    return new String(channel, writeFrom, channel.length - writeFrom);
  }

  //enfore sending 14 character programids
  public static String fromSageTVtoProgram(String program)
  {
    if (program.length() == 14) return program;

    if (program.startsWith("EP") && program.length()==12) //add zeros after EP
    {
        String programNumber = program.substring(2);
        program = "EP00" + programNumber;
        if (Sage.DBG) System.out.println("SDUtils.fromSageTVtoProgram: program = '" + program + "'");
        return program;
    }
    else if (program.startsWith("EP")) //add zeros to end
    {
        program = String.format("%-14s", program).replace(' ', '0');   
        program.replace("EP", "SH");
        if (Sage.DBG) System.out.println("SDUtils.fromSageTVtoProgram: EP program converted to = '" + program + "'");
        return program;
    }else{  //add zerors to the start AFTER the 2 char type
        String programType = program.substring(0, 2);
        String programNumber = program.substring(2);
        program = programType + String.format("%-12s", programNumber).replace(' ', '0');
        if (Sage.DBG) System.out.println("SDUtils.fromSageTVtoProgram: program = '" + program + "'");
        return program;
    }
  }

  public static String fromProgramToSageTV(String program)
  {
    if (program.length() == 14 && program.startsWith("00", 2))
    {
      char programChar[] = program.toCharArray();
      System.arraycopy(programChar, 4, programChar, 2, 10);
      return new String(programChar, 0, 12);
    }

    return program;
  }

  /**
   * Check if a given program ID is valid to send to SD.
   *
   * @param programId The program ID to check.
   * @return <code>true</code> if a given program ID is valid to send to SD.
   */
  public static boolean isValidProgramID(String programId)
  {
      if(programId == null || programId.length() == 0) return false;
      if (programId.startsWith("EP") || programId.startsWith("SH") || programId.startsWith("MV") || programId.startsWith("SP") || programId.startsWith("EV")) return true;
      return false;
  }

  /**
   * Check if a given external ID will likely have an associated series.
   *
   * @param extID The external ID to check.
   * @return <code>true</code> if a given external ID will likely have an associated series.
   */
  public static boolean canGetSeries(String extID)
  {
    char three;
    return extID != null && extID.startsWith("EP") && extID.length() == 14 &&
      (three = extID.charAt(2)) >= '0' && three <= '9';
  }

  /**
   * Converts an episode to a series external ID if it can also be turned into a
   * <code>SeriesInfo</code> object.
   *
   * @param extID The episode to convert.
   * @return <code>null</code> if the episode does not meet the criteria to be converted. Otherwise
   *         a series external ID will be returned.
   */
  public static String getSeriesForEpisode(String extID)
  {
    if (extID == null || extID.startsWith("SH"))
      return extID;

    if (canGetSeries(extID))
    {
      char[] newShow = extID.toCharArray();
      newShow[0] = 'S';
      newShow[1] = 'H';
      newShow[10] = '0';
      newShow[11] = '0';
      newShow[12] = '0';
      newShow[13] = '0';
      return new String(newShow);
    }

    return null;
  }

  /**
   * Removes people that do not have an associated role or character.
   *
   * @param people An array of people to check.
   * @return An array less people without any associated role or character.
   */
  public static SDPerson[] removeNoCharacterPeople(SDPerson people[])
  {
    List<SDPerson> returnList = new ArrayList<>();

    for (SDPerson person : people) {
      if (person.getCharacterName() == null || person.getCharacterName().trim().length() == 0)
      {
        continue;
      }
      returnList.add(person);
    }

    return returnList.size() == people.length ? people : returnList.toArray(new SDPerson[returnList.size()]);
  }

  /**
   * Gets a person from the Wizard.
   * <p/>
   * If a person does not already exist in the Wizard, that person will be added. The purpose of
   * this function is to ensure that we do not clobber additional person data with a new person that
   * only has a name and person ID.
   *
   * @param person The Schedules Direct person to get from the Wizard.
   * @param wiz The Wizard instance to be used to check for and add new people.
   * @return <code>null</code> if the person could not be added or a person from the Wizard.
   */
  public static Person getPerson(SDPerson person, Wizard wiz)
  {
    String personName = person.getName();
    // This should never happen because the name is mandatory.
    if (personName == null || personName.length() == 0)
      return null;

    // We are using the person ID as our ext ID.
    int personId = person.getPersonIdAsInt();

    // Sports teams will not have any kind of person ID, so all we can do is just add their name.
    if (personId == 0){
      //JUSJOKEN 2025-02-12 - NO CHANGE but may need to adjust this as these 0 ids are always being fetched
      return wiz.getPersonForName(personName);
    }

    if (person.isAlias())
      personId *= -1;

    Person newPersion = wiz.getPersonForExtID(personId);
    if (newPersion == null)
    {
      newPersion = wiz.addPerson(personName, personId, 0, 0, "", Pooler.EMPTY_SHORT_ARRAY,
        Pooler.EMPTY_STRING_ARRAY, Pooler.EMPTY_2D_BYTE_ARRAY, DBObject.MEDIA_MASK_TV);
    }
    //JUSJOKEN 2025-02-12 - may need to make a change here to NOT return the person if it was already in the DB
    return newPersion;
  }

  public static byte[] byteCollectionToByteArrayPrimitive(Collection<Byte> collection)
  {
    byte returnValue[] = new byte[collection.size()];
    int i = 0;
    for (Byte byteObject : collection)
    {
      returnValue[i++] = byteObject;
    }
    return returnValue;
  }
}
