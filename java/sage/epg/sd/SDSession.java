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

import sage.Sage;
import sage.Version;
import sage.epg.sd.gson.Gson;
import sage.epg.sd.gson.JsonArray;
import sage.epg.sd.gson.JsonElement;
import sage.epg.sd.gson.JsonObject;
import sage.epg.sd.gson.JsonSyntaxException;
import sage.epg.sd.gson.stream.JsonWriter;
import sage.epg.sd.json.headend.SDHeadend;
import sage.epg.sd.json.headend.SDHeadendLineup;
import sage.epg.sd.json.images.SDImage;
import sage.epg.sd.json.images.SDProgramImages;
import sage.epg.sd.json.lineup.SDAccountLineup;
import sage.epg.sd.json.lineup.SDAccountLineups;
import sage.epg.sd.json.locale.SDLanguage;
import sage.epg.sd.json.locale.SDRegion;
import sage.epg.sd.json.map.SDLineupMap;
import sage.epg.sd.json.programs.SDInProgressSport;
import sage.epg.sd.json.programs.SDPerson;
import sage.epg.sd.json.programs.SDProgram;
import sage.epg.sd.json.programs.SDSeriesDesc;
import sage.epg.sd.json.programs.SDSeriesDescArray;
import sage.epg.sd.json.schedules.SDScheduleMd5;
import sage.epg.sd.json.schedules.SDScheduleMd5Array;
import sage.epg.sd.json.schedules.SDStationSchedule;
import sage.epg.sd.json.service.SDAvailableService;
import sage.epg.sd.json.status.SDStatus;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.Collection;

public abstract class SDSession
{
  private static final Object debugLock = new Object();
  private static PrintWriter debugWriter = null;
  private static int debugBytes = 0;
  protected static final Gson gson = SDUtils.GSON;

  // Response timeout used for GET and POST HTTP communications.
  public static final int TIMEOUT = 120000;
  public static final String USER_AGENT = "Open Source SageTV " + Version.VERSION;
  private static final String URL_BASE = "https://json.schedulesdirect.org";
  public static final String URL_VERSIONED = URL_BASE + "/20141201";
  // Get images for celebrities. Cast/Crew must have a nameId to use this.
  private static final String GET_CELEBRITY_IMAGES = URL_VERSIONED + "/metadata/celebrity/";
  // Get supported sports that are in progress.
  private static final String GET_IN_PROGRESS_SPORT = URL_VERSIONED + "/metadata/stillRunning/";
  // Delete a lineup that is no longer being updated.
  private static final String DELETE_ACCOUNT_LINEUP = URL_VERSIONED + "/lineups/";
  // Add a lineup by appended ID.
  private static final String ADD_ACCOUNT_LINEUP = DELETE_ACCOUNT_LINEUP;

  // The character set to be used for outgoing communications.
  protected static final Charset OUT_CHARSET = StandardCharsets.UTF_8;
  // The expected character set to be used for incoming communications.
  protected static final Charset IN_CHARSET = StandardCharsets.ISO_8859_1;

  // These are set in the static constructor because they can throw format exceptions.
  // Returns a token if the credentials are valid.
  protected static final URL GET_TOKEN;
  // Get the current account status/saved lineups.
  private static final URL GET_STATUS;
  // Get a list of available services.
  private static final URL GET_AVAILABLE;
  // Get the lineups associated with the authenticated account.
  private static final URL GET_LINEUPS;
  // Get requested guide data for specific programs.
  private static final URL GET_PROGRAMS;
  // Get program descriptions. This only works for episodes (EP).
  private static final URL GET_SERIES_DESC;
  // Get images for programs. hasImageArtwork must be true for this to return anything.
  private static final URL GET_PROGRAMS_IMAGES;
  // Get the schedules for for stations IDs
  private static final URL GET_SCHEDULES;
  // Get the md5 of the schedules for for stations IDs
  private static final URL GET_SCHEDULES_MD5;

  static
  {
    // Work around so that the URL's are constants.
    URL newGetToken;
    URL newGetStatus;
    URL newGetAvailable;
    URL newGetLineups;
    URL newGetPrograms;
    URL newGetSeriesDesc;
    URL newGetProgramsImages;
    URL newGetSchedules;
    URL newGetSchedulesMd5;

    try
    {
      newGetToken = new URL(URL_VERSIONED + "/token");
      newGetStatus = new URL(URL_VERSIONED + "/status");
      newGetAvailable = new URL(URL_VERSIONED + "/available");
      newGetLineups = new URL(URL_VERSIONED + "/lineups");
      newGetPrograms = new URL(URL_VERSIONED + "/programs");
      newGetSeriesDesc = new URL(URL_VERSIONED + "/metadata/description");
      newGetProgramsImages = new URL(URL_VERSIONED + "/metadata/programs");
      newGetSchedules = new URL(URL_VERSIONED + "/schedules");
      newGetSchedulesMd5 = new URL(URL_VERSIONED + "/schedules/md5");
    }
    catch (Exception e)
    {
      // If this ever happens, we did something wrong on the code side of things.
      System.out.println("Unable to create the URL's needed for Schedules Direct.");
      e.printStackTrace(System.out);

      newGetToken = null;
      newGetStatus = null;
      newGetAvailable = null;
      newGetLineups = null;
      newGetPrograms = null;
      newGetSeriesDesc = null;
      newGetProgramsImages = null;
      newGetSchedules = null;
      newGetSchedulesMd5 = null;
    }

    GET_TOKEN = newGetToken;
    GET_STATUS = newGetStatus;
    GET_AVAILABLE = newGetAvailable;
    GET_LINEUPS = newGetLineups;
    GET_PROGRAMS = newGetPrograms;
    GET_SERIES_DESC = newGetSeriesDesc;
    GET_PROGRAMS_IMAGES = newGetProgramsImages;
    GET_SCHEDULES = newGetSchedules;
    GET_SCHEDULES_MD5 = newGetSchedulesMd5;
  }

  // All communications that require authentication will require this token.
  protected String token;
  // This value is set to the time in milliseconds that we will get a new token.
  protected long tokenExpiration;
  // The username to be used for authentication.
  protected final String username;
  // The password must be a SHA-1 hash in lowercase hex.
  protected final String passHash;
  // This is populated when a service is requested to ensure the service actually exists. This is
  // only used while setting up a new lineup, and is a very small array, so this will be sufficient.
  SDAvailableService services[];
  // This is used to send POST and PUT.
  ByteArrayOutputStream outputStream;
  // This is used to create JSON to send POST and PUT.
  JsonWriter jsonWriter;

  /**
   * Create a new session with Schedules Direct.
   *
   * @param username The username to be used to connect.
   * @param passHash The password in a SHA-1 hash.
   * @throws SDSession If the username or password is <code>null</code>.
   */
  public SDSession(String username, String passHash) throws SDException
  {
    this.username = username;
    this.passHash = passHash;

    // This should not be happening since the username and password hash should be getting checked
    // for being null before even trying to create a new session, but in case somehow they get
    // passed that, this will throw an appropriate exception that will give the user the chance to
    // change their username and password, though the error message might be a little misleading.
    if (username == null || passHash == null)
    {
      throw new SDException(SDErrors.INVALID_USER);
    }
  }

  /**
   * Returns the provided username.
   *
   * @return The current username.
   */
  public String getUsername()
  {
    return username;
  }

  /**
   * Enable debug logging for all JSON in and out.
   */
  public static void enableDebug()
  {
    try
    {
      synchronized (debugLock)
      {
        File debugFile = new File("sd_epg.log");
        long fileSize = debugFile.exists() ? debugFile.length() : 0;
        if (fileSize > 67108864)
        {
          File oldFile = new File("sd_epg.log.old");
          if (oldFile.exists()) oldFile.delete();
          debugFile.renameTo(new File("sd_epg.log.old"));
          debugWriter = new PrintWriter(new BufferedWriter(new FileWriter("sd_epg.log")));
        }
        else
        {
          debugWriter = new PrintWriter(new BufferedWriter(new FileWriter("sd_epg.log", true)));
        }

        debugBytes = (int) fileSize;
      }
    }
    catch (IOException e)
    {
      System.out.println("Unable to open sd_epg.log");
      e.printStackTrace(System.out);
    }
  }

  public static boolean debugEnabled()
  {
    return debugWriter != null;
  }

  public static void writeDebugException(Throwable line)
  {
    if (debugWriter == null)
      return;

    try
    {
      synchronized (debugLock)
      {
        String message = line.getMessage();
        if (message == null) message = "null";
        debugWriter.write("#### Exception Start ####");
        debugWriter.write(message);
        debugWriter.write(System.lineSeparator());
        line.printStackTrace(debugWriter);
        debugWriter.write(System.lineSeparator());
        debugWriter.write("#### Exception End ####");
        debugWriter.write(System.lineSeparator());
        debugWriter.flush();
        // Close enough; we really don't need precise math here.
        debugBytes += 512;
        debugRollover();
      }
    }
    catch (Exception e)
    {
      System.out.println("Unable to write to sd_epg.log");
      e.printStackTrace(System.out);
    }
  }

  public static void writeDebugLine(String line)
  {
    if (debugWriter == null)
      return;

    try
    {
      synchronized (debugLock)
      {
        if (line == null) line = "null";
        debugWriter.write(line);
        debugWriter.write(System.lineSeparator());
        debugWriter.flush();
        debugBytes += line.length() + 2;
        debugRollover();
      }
    }
    catch (Exception e)
    {
      System.out.println("Unable to write to sd_epg.log");
      e.printStackTrace(System.out);
    }
  }

  public static void writeDebug(char[] line, int offset, int length)
  {
    if (debugWriter == null)
      return;

    try
    {
      synchronized (debugLock)
      {
        debugWriter.write(line, offset, length);
        debugBytes += length;
        // Don't perform a rollover in this method because we could cut a String of JSON in half.
      }
    }
    catch (Exception e)
    {
      System.out.println("Unable to write to sd_epg.log");
      e.printStackTrace(System.out);
    }
  }

  // The log file size can get out of hand surprisingly fast, so this keeps the file within a size
  // that most text readers can still digest.
  private static void debugRollover()
  {
    if (debugWriter != null && debugBytes > 67108864)
    {
      try
      {
        debugWriter.close();
      } catch (Exception e) {}
      enableDebug();
    }
  }

  /**
   * Connect to Schedules Direct and get a token if there isn't a token or 12 hours has passed since
   * the last token was acquired.
   *
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public synchronized void authenticate() throws IOException, SDException
  {
    // The token is still valid.
    if (System.currentTimeMillis() < tokenExpiration && token != null)
    {
      return;
    }

    // Set the token to null so if we are getting a new token, it doesn't send the old token along
    // for the authentication request.
    token = null;

    JsonObject authRequest = new JsonObject();
    authRequest.addProperty("username", username);
    authRequest.addProperty("password", passHash);

    InputStreamReader reader = post(GET_TOKEN, authRequest);
    JsonObject response = gson.fromJson(reader, JsonObject.class);

    try
    {
      reader.close();
    } catch (Exception e) {}

    JsonElement codeElement = response.get("code");

    if (codeElement == null)
    {
      System.out.println("Received unexpected response from SD:");
      System.out.println(response.toString());
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);
    }

    int code = codeElement.getAsInt();
    JsonElement tokenElement = response.get("token");

    if (code != 0)
    {
      SDErrors.throwErrorForCode(code);
    }
    else if (tokenElement == null)
    {
      // I'm not really sure this is something that happens sometimes, but it would prevent
      // authenticated communications from continuing. The prepending is in case the returned
      // message is ambiguous or implies success when it was clearly not a complete success.
      throw new SDException(SDErrors.SAGETV_TOKEN_RETURN_MISSING);
    }

    token = tokenElement.getAsString();
    // The token is good for 24 hours, but I don't trust that we won't introduce a race condition by
    // relying on that down to the millisecond, so we renew at least every 12 hours.
    tokenExpiration = System.currentTimeMillis() + Sage.MILLIS_PER_DAY / 2;
  }

  /**
   * Clears the last token so the next call to <code>authenticate()</code> will grab a new token.
   * <p/>
   * The communications are stateless, so there's nothing to hang up on.
   */
  public synchronized void endSession()
  {
    token = null;
  }

  public abstract InputStreamReader put(URL url, byte sendBytes[], int off, int len) throws IOException, SDException;

  public synchronized InputStreamReader putAuth(URL url, byte sendBytes[], int off, int len) throws IOException, SDException
  {
    authenticate();
    return put(url, sendBytes, off, len);
  }

  public synchronized InputStreamReader putAuth(URL url, JsonElement jsonElement) throws IOException, SDException
  {
    authenticate();
    return put(url, jsonElement);
  }

  public synchronized InputStreamReader put(URL url, JsonElement jsonElement) throws IOException, SDException
  {
    if (jsonWriter == null)
    {
      outputStream = new ByteArrayOutputStream(32768);
      jsonWriter = gson.newJsonWriter(new OutputStreamWriter(outputStream, OUT_CHARSET));
    }
    else
    {
      outputStream.reset();
    }

    gson.toJson(jsonElement, jsonWriter);
    jsonWriter.flush();

    // This creates a new array on every POST, but it's better than GSON returning a string and then
    // we have to convert the string into a new byte array. If this really ends up being a
    // performance killer, we can create an Appendable implementation.
    byte sendBytes [] = outputStream.toByteArray();
    return put(url, sendBytes, 0, sendBytes.length);
  }

  public abstract InputStreamReader post(URL url, byte sendBytes[], int off, int len) throws IOException, SDException;

  public synchronized InputStreamReader postAuth(URL url, byte sendBytes[], int off, int len) throws IOException, SDException
  {
    authenticate();
    return post(url, sendBytes, off, len);
  }

  public synchronized InputStreamReader postAuth(URL url, JsonElement jsonElement) throws IOException, SDException
  {
    authenticate();
    return post(url, jsonElement);
  }

  public synchronized InputStreamReader post(URL url, JsonElement jsonElement) throws IOException, SDException
  {
    if (jsonWriter == null)
    {
      outputStream = new ByteArrayOutputStream(32768);
      jsonWriter = gson.newJsonWriter(new OutputStreamWriter(outputStream, OUT_CHARSET));
    }
    else
    {
      outputStream.reset();
    }

    gson.toJson(jsonElement, jsonWriter);
    jsonWriter.flush();

    // This creates a new array on every POST, but it's better than GSON returning a string and then
    // we have to convert the string into a new byte array. If this really ends up being a
    // performance killer, we can create an Appendable implementation.
    byte sendBytes [] = outputStream.toByteArray();
    return post(url, sendBytes, 0, sendBytes.length);
  }

  public abstract InputStreamReader get(URL url) throws IOException, SDException;

  public synchronized InputStreamReader getAuth(URL url) throws IOException, SDException
  {
    authenticate();
    return get(url);
  }

  public abstract InputStreamReader delete(URL url) throws IOException, SDException;

  public synchronized InputStreamReader deleteAuth(URL url) throws IOException, SDException
  {
    authenticate();
    return delete(url);
  }

  /**
   * Returns the current status of the account associated with this connection.
   * <p/>
   * This also contains the lineups associated with the account.
   *
   * @return The current status of the account associated with this connection. If the value would
   *         have been <code>null</code>, an exception will be thrown with a UI message.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDStatus getStatus() throws IOException, SDException
  {
    return getAuthJson(GET_STATUS, SDStatus.class);
  }

  /**
   * Get services that are currently available via Schedules Direct.
   *
   * @return The currently available services.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDAvailableService[] getAvailableServices() throws IOException, SDException
  {
    return getJson(GET_AVAILABLE, SDAvailableService[].class);
  }

  /**
   * Get all available regions and their associated countries.
   * <p/>
   * This also returns an example and regular expression for the postal code for each country.
   *
   * @return All available regions.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDRegion[] getRegions() throws IOException, SDException
  {
    return getService("COUNTRIES", SDRegion[].class);
  }

  /**
   * Get all available languages.
   *
   * @return All available languages.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDLanguage[] getLanguages() throws IOException, SDException
  {
    return getService("LANGUAGES", SDLanguage[].class);
  }

  private synchronized <T> T getService(String serviceName, Class<T> returnClass) throws IOException, SDException
  {
    if (services == null)
      services = getAvailableServices();

    // This will work for all services, but DVB-T since the URI uses {ISO 3166-1 alpha-3}.
    URL serviceUrl = null;
    for (SDAvailableService service : services)
    {
      if (service.getType().equals(serviceName))
      {
        serviceUrl = new URL(URL_BASE + service.getUri());
        break;
      }
    }

    // The service is not available.
    if (serviceUrl == null)
    {
      throw new SDException(SDErrors.SAGETV_SERVICE_MISSING);
    }

    return getJson(serviceUrl, returnClass);
  }

  private <T> T putJson(URL url, Class<T> returnClass, JsonElement postElement) throws IOException, SDException
  {
    InputStreamReader reader;

    if (postElement != null)
    {
      reader = put(url, postElement);
    }
    else
    {
      reader = put(url, new byte[0], 0, 0);
    }

    T returnValue = gson.fromJson(reader, returnClass);
    try
    {
      reader.close();
    } catch (Exception e) {}

    if (returnValue == null)
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);

    return returnValue;
  }

  private <T> T putAuthJson(URL url, Class<T> returnClass, JsonElement postElement) throws IOException, SDException
  {
    InputStreamReader reader;

    if (postElement != null)
    {
      reader = putAuth(url, postElement);
    }
    else
    {
      reader = putAuth(url, new byte[0], 0, 0);
    }

    T returnValue = gson.fromJson(reader, returnClass);
    try
    {
      reader.close();
    } catch (Exception e) {}

    if (returnValue == null)
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);

    return returnValue;
  }

  private <T> T postJson(URL url, Class<T> returnClass, JsonElement postElement) throws IOException, SDException
  {
    InputStreamReader reader;

    if (postElement != null)
    {
      reader = post(url, postElement);
    }
    else
    {
      reader = post(url, new byte[0], 0, 0);
    }

    T returnValue = gson.fromJson(reader, returnClass);
    try
    {
      reader.close();
    } catch (Exception e) {}

    if (returnValue == null)
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);

    return returnValue;
  }

  private <T> T postAuthJson(URL url, Class<T> returnClass, JsonElement postElement) throws IOException, SDException
  {
    InputStreamReader reader;

    if (postElement != null)
    {
      reader = postAuth(url, postElement);
    }
    else
    {
      reader = postAuth(url, new byte[0], 0, 0);
    }

    T returnValue = gson.fromJson(reader, returnClass);
    try
    {
      reader.close();
    } catch (Exception e) {}

    if (returnValue == null)
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);

    return returnValue;
  }

  private <T> T getJson(URL url, Class<T> returnClass) throws IOException, SDException
  {
    InputStreamReader reader = get(url);

    T returnValue = gson.fromJson(reader, returnClass);
    try
    {
      reader.close();
    } catch (Exception e) {}

    if (returnValue == null)
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);

    return returnValue;
  }

  private <T> T getAuthJson(URL url, Class<T> returnClass) throws IOException, SDException
  {
    InputStreamReader reader = getAuth(url);

    T returnValue = gson.fromJson(reader, returnClass);
    try
    {
      reader.close();
    } catch (Exception e) {}

    if (returnValue == null)
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);

    return returnValue;
  }

  private <T> T deleteJson(URL url, Class<T> returnClass) throws IOException, SDException
  {
    InputStreamReader reader = delete(url);

    T returnValue = gson.fromJson(reader, returnClass);
    try
    {
      reader.close();
    } catch (Exception e) {}

    if (returnValue == null)
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);

    return returnValue;
  }

  private <T> T deleteAuthJson(URL url, Class<T> returnClass) throws IOException, SDException
  {
    InputStreamReader reader = deleteAuth(url);

    T returnValue = gson.fromJson(reader, returnClass);
    try
    {
      reader.close();
    } catch (Exception e) {}

    if (returnValue == null)
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);

    return returnValue;
  }

  /**
   * Get the available headends for a country and postal code.
   *
   * @param shortCountryName The 3 letter country abbreviation.
   * @param postalCode The postal code to look up.
   * @return The available headends for the provided country and postal code.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDHeadend[] getHeadends(String shortCountryName, String postalCode) throws IOException, SDException
  {
    URL url = new URL(URL_VERSIONED + "/headends?country=" + shortCountryName + "&postalcode=" + postalCode);
    return getAuthJson(url, SDHeadend[].class);
  }

  /**
   * Add a new lineup to account.
   *
   * @param uri The URI provided by a lineup from <code>getHeadends(String, String)</code>.
   * @return The number of account changes remaining.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public int addLineup(String uri) throws IOException, SDException
  {
    URL url = new URL(URL_BASE + uri);
    JsonObject reply = putAuthJson(url, JsonObject.class, null);

    JsonElement codeElement = reply.get("code");
    if (codeElement == null)
      throw new SDException(SDErrors.SAGETV_UNKNOWN);

    int code = codeElement.getAsInt();
    if (code != 0)
    {
      SDErrors.throwErrorForCode(code);
    }

    return reply.get("changesRemaining").getAsInt();
  }

  /**
   * Add a new lineup to account.
   *
   * @param id The ID provided by a lineup from {@link SDHeadendLineup#getLineup()}.
   * @return The number of account changes remaining.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public int addLineupByID(String id) throws IOException, SDException
  {
    URL url = new URL(ADD_ACCOUNT_LINEUP + id);
    JsonObject reply = putAuthJson(url, JsonObject.class, null);

    JsonElement codeElement = reply.get("code");
    if (codeElement == null)
      throw new SDException(SDErrors.SAGETV_UNKNOWN);

    int code = codeElement.getAsInt();
    if (code != 0)
    {
      SDErrors.throwErrorForCode(code);
    }

    return reply.get("changesRemaining").getAsInt();
  }

  /**
   * Get the lineups associated with this account.
   *
   * @return The lineups associated with this account.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDAccountLineups getAccountLineups() throws IOException, SDException
  {
    SDAccountLineups lineups = getAuthJson(GET_LINEUPS, SDAccountLineups.class);

    if (lineups.getCode() != 0)
    {
      SDErrors.throwErrorForCode(lineups.getCode());
    }

    return lineups;
  }

  /**
   * Delete a lineup from account.
   *
   * @param lineup A lineup provided by {@link #getAccountLineups()}.
   * @return The number of account changes remaining.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public int deleteLineup(SDAccountLineup lineup) throws IOException, SDException
  {
    URL url;
    if (lineup.getUri() != null)
      url = new URL(URL_BASE + lineup.getUri());
    else
      url = new URL(DELETE_ACCOUNT_LINEUP + lineup.getLineup());

    JsonObject reply = deleteAuthJson(url, JsonObject.class);

    JsonElement codeElement = reply.get("code");
    if (codeElement == null)
      throw new SDException(SDErrors.SAGETV_UNKNOWN);

    int code = codeElement.getAsInt();
    if (code != 0)
    {
      SDErrors.throwErrorForCode(code);
    }

    return reply.get("changesRemaining").getAsInt();
  }

  /**
   * Get a lineup map for a lineup that is already added to the account.
   * <p/>
   * The returned channel map could be any channel map that implements <code>SDChannelMap</code>.
   *
   * @param uri The URI provided by a lineup from <code>getLineups()</code>.
   * @return The number of account changes remaining.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDLineupMap getLineup(String uri) throws IOException, SDException
  {
    URL url = new URL(URL_BASE + uri);
    SDLineupMap lineupMap = getAuthJson(url, SDLineupMap.class);
    if (lineupMap.getCode() != 0)
      SDErrors.throwErrorForCode(lineupMap.getCode());
    return lineupMap;
  }

  /**
   * Get the details for an array of programs. (limit 5000)
   * <p/>
   * This method will retry all programs that return a soft retry error code (6001) once before
   * returning. If a returned program has the code 6001, it should be queued to be retried later. If
   * error code 6000 is returned, the program no longer exists and should never be retried again.
   *
   * @param programs The array of programs.
   * @return The details as provided by Schedules Direct.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDProgram[] getPrograms(Collection<String> programs) throws IOException, SDException
  {
    if (programs.size() > 5000)
      throw new InvalidParameterException("You cannot get more than 5000 programs in one query.");

    JsonArray submit = new JsonArray();
    for (String program : programs)
    {
      submit.add(SDUtils.fromSageTVtoProgram(program));
    }

    SDProgram[] returnValues = postAuthJson(GET_PROGRAMS, SDProgram[].class, submit);
    submit = null;

    // Retry any soft failures once before returning the results.
    for (SDProgram returnValue : returnValues)
    {
      if (returnValue.getCode() == 6001)
      {
        if (submit == null)
          submit = new JsonArray();
        // The program ID is returned with the error code 6001.
        submit.add(returnValue.getProgramID());
      }
    }

    if (submit != null)
    {
      SDProgram[] softRetry = postAuthJson(GET_PROGRAMS, SDProgram[].class, submit);

      // This should never happen because it would mean we got more programs back than we
      // actually asked for, but we will check for it anyway just in case so that we don't end
      // up with an out of bounds exception when the data coming in would otherwise be valid.
      if (softRetry.length > submit.size())
      {
        System.out.println("Warning: Schedules Direct soft retry expected " + submit.size() +  " programs, got " + softRetry.length + " programs");

        // Resize the array to make up the difference. The only data that will be used from the
        // returned data will be the the programs we actually expected.
        SDProgram[] newArray = new SDProgram[returnValues.length + (submit.size() - softRetry.length)];
        System.arraycopy(returnValues, 0, newArray, 0, returnValues.length);
        returnValues = newArray;
      }

      int j = 0;
      for (int i = 0; i < returnValues.length; i++)
      {
        if (returnValues[i] == null || returnValues[i].getCode() == 6001)
        {
          returnValues[i] = softRetry[j++];
        }
      }
    }

    return returnValues;
  }

  /**
   * Get the show description for the provided programs. (limit 500, episodes (EP) only)
   * <p/>
   * This method will retry all programs that return a soft retry error code (6001) once before
   * returning. If a returned program has the code 6001, it should be queued to be retried later. If
   * error code 6000 is returned, the program no longer exists and should never be retried again.
   *
   * @param programs The array of programs.
   * @return The show descriptions as provided by Schedules Direct.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDSeriesDesc[] getSeriesDesc(String[] programs) throws IOException, SDException
  {
    if (programs.length > 500)
      throw new InvalidParameterException("You cannot get more than 500 show descriptions in one query.");

    JsonArray submit = new JsonArray();
    for (String program : programs)
    {
      submit.add(program);
    }

    SDSeriesDesc[] returnValues = postAuthJson(GET_SERIES_DESC, SDSeriesDescArray.class, submit).getSeriesDescs();

    submit = null;

    // Retry any soft failures once before returning the results.
    for (int i = 0; i < returnValues.length; i++)
    {
      SDSeriesDesc returnValue = returnValues[i];
      if (returnValue.getCode() == 6001)
      {
        if (submit == null)
          submit = new JsonArray();
        // The program ID is returned with the error code 6001. The order of the returned
        // descriptions should not be changing, but the returned series ID isn't going to work here
        // for a retry, so we have to retry using the original episode program ID.
        submit.add(programs[i]);
      }
    }

    if (submit != null)
    {
      SDSeriesDesc[] softRetry = postAuthJson(GET_SERIES_DESC, SDSeriesDescArray.class, submit).getSeriesDescs();

      // This should never happen because it would mean we got more programs back than we
      // actually asked for, but we will check for it anyway just in case so that we don't end
      // up with an out of bounds exception when the data coming in would otherwise be valid.
      if (softRetry.length > submit.size())
      {
        System.out.println("Warning: Schedules Direct soft retry expected " + submit.size() +  " programs, got " + softRetry.length + " programs");

        // Resize the array to make up the difference.
        SDSeriesDesc[] newArray = new SDSeriesDesc[returnValues.length + (submit.size() - softRetry.length)];
        System.arraycopy(returnValues, 0, newArray, 0, returnValues.length);
        returnValues = newArray;
      }

      int j = 0;
      for (int i = 0; i < returnValues.length; i++)
      {
        if (returnValues[i] == null || returnValues[i].getCode() == 6001)
        {
          returnValues[i] = softRetry[j++];
        }
      }
    }

    return returnValues;
  }

  /**
   * Get the available images for the provided programs. (limit 500)
   * <p/>
   * This method will retry all programs that return a soft retry error code (6001) once before
   * returning. If a returned program has the code 6001, it should be queued to be retried later. If
   * error code 6000 is returned, the program no longer exists and should never be retried again.
   *
   * @param programs The array of programs. The left-most 10 characters will only be used. If a
   *                 provided program ID is less than 10 characters, an index out of bounds
   *                 exception will be thrown.
   * @return The available images as provided by Schedules Direct.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDProgramImages[] getProgramImages(String[] programs) throws IOException, SDException
  {
    if (programs.length > 500)
      throw new InvalidParameterException("You cannot get more than 500 images in one query.");

    JsonArray submit = new JsonArray();
    for (String program : programs)
    {
      if (program.length() != 10)
        submit.add(program.substring(0, 10));
      else
        submit.add(program);
    }

    SDProgramImages[] returnValues = postJson(GET_PROGRAMS_IMAGES, SDProgramImages[].class, submit);
    return returnValues;
  }

  /**
   * Get celebrity images for a specific celebrity.
   *
   * @param personId The person ID as provided by {@link SDPerson#getNameId()}. If this is null,
   *                 blank or 0, an empty array will be returned.
   * @return The available images of the provided celebrity as provided by Schedules Direct.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDImage[] getCelebrityImages(String personId) throws IOException, SDException
  {
    if (personId == null || personId.length() == 0 || personId.equals("0"))
      return SDProgramImages.EMPTY_IMAGES;

    try
    {
      // A token is not required to perform this lookup.
      return getJson(new URL(GET_CELEBRITY_IMAGES + personId), SDImage[].class);
    }
    catch (JsonSyntaxException e)
    {
      // These lookups can return undocumented JSON that can throw exceptions when images do not
      // exist. If this happens, we assume there aren't any images.
      return SDProgramImages.EMPTY_IMAGES;
    }
    catch (SDException e)
    {
      // This error is expected from time to time.
      if (e.ERROR == SDErrors.HCF)
        return SDProgramImages.EMPTY_IMAGES;
      // Anything else, throw the exception.
      throw e;
    }
  }

  /**
   * Get the schedules for the provided station IDs (limit 5000)
   * <p/>
   * The same provided dates will be used for all provided station IDs.
   * <p/>
   * Check the returned schedules for code 7100 to see if that station should be queued up for later
   * processing. Check for code 2201 to see if that station no longer exists. Check for code 7020 to
   * see if the request was out of range (always check getSchedulesMd5() for what is valid before
   * making the request to avoid this).
   *
   * @param stationIDs The stations to get the schedules for.
   * @param dates The dates to get schedules for the provided stations IDs.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDStationSchedule[] getSchedules(Collection<Integer> stationIDs, String dates[]) throws IOException, SDException
  {
    if (stationIDs.size() > 5000)
      throw new InvalidParameterException("You cannot get more than 5000 station schedules in one query.");

    JsonArray submit = new JsonArray();
    for (int stationID : stationIDs)
    {
      JsonObject object = new JsonObject();
      object.addProperty("stationID", SDUtils.fromSageTVtoStationID(stationID));
      JsonArray jsonDates = new JsonArray();
      for (String date: dates)
      {
        jsonDates.add(date);
      }
      object.add("date", jsonDates);
      submit.add(object);
    }

    SDStationSchedule returnValues[] = postAuthJson(GET_SCHEDULES, SDStationSchedule[].class, submit);
    return returnValues;
  }

  /**
   * Get the md5 hashes for schedules for the provided station IDs. (limit 5000)
   * <p/>
   * The same provided dates will be used for all provided station IDs.
   * <p/>
   * Check for code 2201 to see if that station no longer exists.
   *
   * @param stationIDs The stations to get the schedules for.
   * @param dates The dates to get schedules for the provided stations IDs. If this is
   *              <code>null</code> or an empty array, today through the furthest available date
   *              will be returned.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDScheduleMd5[] getSchedulesMd5(Collection<Integer> stationIDs, String dates[]) throws IOException, SDException
  {
    if (stationIDs.size() > 5000)
      throw new InvalidParameterException("You cannot get more than 5000 station schedule md5s in one query.");

    JsonArray submit = new JsonArray();
    for (int stationID : stationIDs)
    {
      JsonObject object = new JsonObject();
      object.addProperty("stationID", SDUtils.fromSageTVtoStationID(stationID));

      if (dates != null && dates.length != 0)
      {
        JsonArray jsonDates = new JsonArray();
        for (String date : dates)
        {
          jsonDates.add(date);
        }
        object.add("date", jsonDates);
      }

      submit.add(object);
    }

    if (submit.size() == 0)
      return new SDScheduleMd5[0];

    SDScheduleMd5Array returnValues = postAuthJson(GET_SCHEDULES_MD5, SDScheduleMd5Array.class, submit);
    return returnValues.getMd5s();
  }

  /**
   * Get the status of an in progress sport.
   *
   * @param programId The program ID of the sport
   * @return <code>null</code> if the program ID is invalid or an SDInProgressSport object.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem working with Schedules Direct.
   */
  public SDInProgressSport getInProgressSport(String programId) throws IOException, SDException
  {
    if (programId == null || programId.length() == 0)
      return null;

    if (programId.length() == 12)
      programId = SDUtils.fromSageTVtoProgram(programId);

    // A token is not required to perform this lookup.
    return getAuthJson(new URL(GET_IN_PROGRESS_SPORT + programId), SDInProgressSport.class);
  }
}