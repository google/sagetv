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

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.Random;

public class SDSageSession extends SDSession
{
  static
  {
    if (Sage.DBG && Sage.getBoolean("debug_sd", false)) enableDebug();
  }

  /**
   * Create a session.
   * <p/>
   * The username and password will be saved if they can be used to authenticate successfully.
   * Otherwise, an <code>SDException</code> will be thrown with an appropriate UI message.
   *
   * @param username The username to use for authentication.
   * @param password The password to use for authentication.
   * @throws IOException If there is an I/O related error.
   * @throws SDException If there is a problem authenticating with the Schedules Direct server.
   */
  public SDSageSession(String username, String password) throws IOException, SDException
  {
    super(username, password);
    authenticate();
  }

  @Override
  public InputStreamReader put(URL url, byte[] sendBytes, int off, int len) throws IOException, SDException
  {
    return put(url, sendBytes, off, len, true);
  }

  public InputStreamReader put(URL url, byte[] sendBytes, int off, int len, boolean retry) throws IOException, SDException
  {
    if (SDSession.debugEnabled())
    {
      SDSession.writeDebugLine("PUT " + url.toString() + " (send): " + System.lineSeparator() + new String(sendBytes, off, len));
    }

    // Verified javax.net.ssl.HttpsURLConnection is present in OpenJDK 7+.
    HttpsURLConnection connection = (HttpsURLConnection)url.openConnection();
    connection.setRequestMethod("PUT");
    connection.setDoOutput(true);
    connection.setConnectTimeout(TIMEOUT);
    connection.setReadTimeout(TIMEOUT);
    connection.setRequestProperty("User-Agent", USER_AGENT);
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Accept-Encoding", "deflate,gzip");
    connection.setRequestProperty("Accept-Charset", "ISO-8859-1");
    // PUT must have a length or we will not get a reply.
    connection.setRequestProperty("Content-Length", Integer.toString(len));
    //secret SD debug mode that will send requests to their debug server. Only enable when working with SD Support
    if(Sage.getBoolean("debug_sd_support", false)) {
      connection.setRequestProperty("RouteTo", "debug");
      if (Sage.DBG) System.out.println("****debug_sd_support**** property set. This should only be true when you are working directly with SD Support staff");
    }

    if (token != null)
      connection.setRequestProperty("token", token);
    try
    {
      // We can timeout just getting the OutputStream.
      OutputStream outStream = connection.getOutputStream();
      outStream.write(sendBytes, off, len);
      outStream.close();

      // Schedules Direct will return an http error 403 if the token has expired. The token can expire
      // because another program is using the same account, so we try once to get the token back.
      if (retry && connection.getResponseCode() == 403)
      {
        token = null;
        authenticate();
        return put(url, sendBytes, off, len, false);
      }

      // We can timeout just getting the InputStream.
      return SDUtils.getStream(connection);
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);
    }
  }

  @Override
  public synchronized InputStreamReader post(URL url, byte sendBytes[], int off, int len) throws IOException, SDException
  {
    return post(url, sendBytes, off, len, true);
  }

  private InputStreamReader post(URL url, byte sendBytes[], int off, int len, boolean retry) throws IOException, SDException
  {
    // Log everything but the authentication POST since that has a reusable password hash in it. The
    // reply is a token that is only valid for at most 24 hours, so there's no reason to not log it.
    if (SDSession.debugEnabled() && !SDSession.GET_TOKEN.equals(url))
    {
      SDSession.writeDebugLine("POST " + url.toString() + " (send): " + System.lineSeparator() + new String(sendBytes, off, len));
    }

    // Verified javax.net.ssl.HttpsURLConnection is present in OpenJDK 7+.
    HttpsURLConnection connection = (HttpsURLConnection)url.openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setConnectTimeout(TIMEOUT);
    connection.setReadTimeout(TIMEOUT);
    connection.setRequestProperty("User-Agent", USER_AGENT);
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Accept-Encoding", "deflate,gzip");
    connection.setRequestProperty("Accept-Charset", "ISO-8859-1");
    // POST must have a length or we will not get a reply.
    connection.setRequestProperty("Content-Length", Integer.toString(len));
    //secret SD debug mode that will send requests to their debug server. Only enable when working with SD Support
    if(Sage.getBoolean("debug_sd_support", false)) {
      connection.setRequestProperty("RouteTo", "debug");
      if (Sage.DBG) System.out.println("****debug_sd_support**** property set. This should only be true when you are working directly with SD Support staff");
    }
    if (token != null)
        if (SDSession.debugEnabled())
        {
            SDSession.writeDebugLine("POST Adding token '" + token + "' to post");
        }
        
      connection.setRequestProperty("token", token);
    try
    {
      // We can timeout just getting the OutputStream.
      OutputStream outStream = connection.getOutputStream();
      outStream.write(sendBytes, off, len);
      outStream.close();

      // Schedules Direct will return an http error 403 if the token has expired. The token can expire
      // because another program is using the same account, so we try once to get the token back.
      if (retry && connection.getResponseCode() == 403)
      {
        token = null;
      if (SDSession.debugEnabled())
        {
            SDSession.writeDebugLine("POST response 403 received. setting token to null");
        }

        try
        {
          // Wait a random interval between 1 and 30000 milliseconds in case more than one SageTV
          // server is updating at the same time. This should effectively get them out of sync each
          // time they overlap and potentially give the other server a chance to complete it's most
          // recent communication before we get a new token.
          Thread.sleep((new Random()).nextInt(30000) + 1);
        } catch (InterruptedException e) {}

        authenticate();
        if (SDSession.debugEnabled())
          {
              SDSession.writeDebugLine("POST retry after authenticate call. token = '" + token + "'");
          }
        return post(url, sendBytes, off, len, false);
      }

      // We can timeout just getting the InputStream.
      return SDUtils.getStream(connection);
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);
    }
  }

  @Override
  public InputStreamReader get(URL url) throws IOException, SDException
  {
    return get(url, true);
  }

  private InputStreamReader get(URL url, boolean retry) throws IOException, SDException
  {
    if (SDSession.debugEnabled())
    {
      SDSession.writeDebugLine("GET " + url.toString());
    }

    // Verified javax.net.ssl.HttpsURLConnection is present in OpenJDK 7+.
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
      if (Sage.DBG) System.out.println("****debug_sd_support**** property set. This should only be true when you are working directly with SD Support staff");
    }
    if (token != null)
        if (SDSession.debugEnabled())
        {
            SDSession.writeDebugLine("GET Adding token '" + token + "' to get");
        }

        connection.setRequestProperty("token", token);

    // Schedules Direct will return an http error 403 if the token has expired. The token can expire
    // because another program is using the same account, so we try once to get the token back.
    if (retry && connection.getResponseCode() == 403)
    {
      token = null;
      if (SDSession.debugEnabled())
        {
            SDSession.writeDebugLine("GET response 403 received. setting token to null");
        }

      try
      {
        // Wait a random interval between 1 and 30000 milliseconds in case more than one SageTV
        // server is updating at the same time. This should effectively get them out of sync each
        // time they overlap and potentially give the other server a chance to complete it's most
        // recent communication before we get a new token.
        Thread.sleep((new Random()).nextInt(30000) + 1);
      } catch (InterruptedException e) {}

      authenticate();
      if (SDSession.debugEnabled())
        {
            SDSession.writeDebugLine("GET retry after authenticate call. token = '" + token + "'");
        }
      return get(url, false);
    }

    try
    {
      // We can timeout just getting the InputStream.
      return SDUtils.getStream(connection);
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);
    }
  }

  @Override
  public InputStreamReader delete(URL url) throws IOException, SDException
  {
    return delete(url, true);
  }

  private InputStreamReader delete(URL url, boolean retry) throws IOException, SDException
  {
    if (SDSession.debugEnabled())
    {
      SDSession.writeDebugLine("DELETE " + url.toString());
    }

    // Verified javax.net.ssl.HttpsURLConnection is present in OpenJDK 7+.
    HttpsURLConnection connection = (HttpsURLConnection)url.openConnection();
    connection.setRequestMethod("DELETE");
    connection.setConnectTimeout(TIMEOUT);
    connection.setReadTimeout(TIMEOUT);
    connection.setRequestProperty("User-Agent", USER_AGENT);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Accept-Encoding", "deflate,gzip");
    connection.setRequestProperty("Accept-Charset", "ISO-8859-1");
    //secret SD debug mode that will send requests to their debug server. Only enable when working with SD Support
    if(Sage.getBoolean("debug_sd_support", false)) {
      connection.setRequestProperty("RouteTo", "debug");
      if (Sage.DBG) System.out.println("****debug_sd_support**** property set. This should only be true when you are working directly with SD Support staff");
    }
    if (token != null)
      connection.setRequestProperty("token", token);

    // Schedules Direct will return an http error 403 if the token has expired. The token can expire
    // because another program is using the same account, so we try once to get the token back.
    if (retry && connection.getResponseCode() == 403)
    {
      token = null;
      authenticate();
      return delete(url, false);
    }

    try
    {
      // We can timeout just getting the InputStream.
      return SDUtils.getStream(connection);
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new SDException(SDErrors.SAGETV_COMMUNICATION_ERROR);
    }
  }
}
