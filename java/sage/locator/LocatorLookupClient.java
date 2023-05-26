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
package sage.locator;

public class LocatorLookupClient implements LocatorConstants
{
  public static void main(String[] args) throws Exception
  {
    System.out.println("IP=" + lookupIPForGuid(args[0]));
  }
  private static java.net.Socket connectToServer() throws java.io.IOException
  {
    String theAddress = sage.Sage.get(LOCATOR_SERVER_PROP, LOCATOR_SERVER);
    try
    {
      java.net.Socket sock = new java.net.Socket();
      sock.connect(new java.net.InetSocketAddress(theAddress, LOCATOR_PORT), 5000);
      return sock;
    }
    catch (java.net.SocketException e1)
    {
    }
    catch (java.net.UnknownHostException e2)
    {
    }
    theAddress = sage.Sage.get(BACKUP_LOCATOR_SERVER_PROP, BACKUP_LOCATOR_SERVER);
    java.net.Socket sock = new java.net.Socket();
    sock.connect(new java.net.InetSocketAddress(theAddress, LOCATOR_PORT), 5000);
    return sock;
  }

  public static String lookupIPForGuid(String guid) throws java.io.IOException
  {
    // Break the GUID string apart into 4 x 16 bit chunks
    int guid1, guid2, guid3, guid4;
    java.util.StringTokenizer toker = new java.util.StringTokenizer(guid, "-");
    if (toker.countTokens() != 4)
      throw new java.io.IOException("GUID is an invalid format of \"" + guid + "\" and it should be xxxx-xxxx-xxxx-xxxx");
    try
    {
      guid1 = Integer.parseInt(toker.nextToken(), 16);
      guid2 = Integer.parseInt(toker.nextToken(), 16);
      guid3 = Integer.parseInt(toker.nextToken(), 16);
      guid4 = Integer.parseInt(toker.nextToken(), 16);
    }
    catch (NumberFormatException e)
    {
      throw new java.io.IOException("GUID contains non-hex digits \"" + guid + "\"");
    }

    java.net.Socket sake = null;
    java.io.OutputStream os = null;
    java.io.InputStream is = null;
    try
    {
      sake = connectToServer();
      sake.setSoTimeout(10000);
      os = sake.getOutputStream();
      is = sake.getInputStream();
      // We always do 512 byte requests
      byte[] reqData = new byte[512];
      reqData[0] = (byte)'S';
      reqData[1] = (byte)'T';
      reqData[2] = (byte)'V';
      reqData[3] = 1;
      reqData[8] = 8; // opCode for lookup
      reqData[9] = 0;
      reqData[10] = 8; // 64 bits for the GUID
      reqData[11] = (byte)((guid1 >> 8) & 0xFF);
      reqData[12] = (byte)(guid1 & 0xFF);
      reqData[13] = (byte)((guid2 >> 8) & 0xFF);
      reqData[14] = (byte)(guid2 & 0xFF);
      reqData[15] = (byte)((guid3 >> 8) & 0xFF);
      reqData[16] = (byte)(guid3 & 0xFF);
      reqData[17] = (byte)((guid4 >> 8) & 0xFF);
      reqData[18] = (byte)(guid4 & 0xFF);
      os.write(reqData);
      os.flush();
      // Read back the repsonse from the server
      int currOffset = 0;
      while (currOffset < reqData.length)
      {
        currOffset += is.read(reqData, currOffset, reqData.length - currOffset);
      }
      // Check the header bytes
      if (reqData[0] != 'S' || reqData[1] != 'T' || reqData[2] != 'V')
        throw new java.io.IOException("Invalid header format, missing 'STV'");
      // Check the version
      if (reqData[3] != 1)
        throw new java.io.IOException("Invalid version number:" + reqData[3]);
      // 4 byte pad and then the opcode
      byte opCode = reqData[8];
      // 2 byte length of valid data after the opcode
      int currLength = ((reqData[9] & 0xFF) << 8) | (reqData[10] & 0xFF);
      if (currLength > 500)
        throw new java.io.IOException("Invalid length in requeset of:" + currLength);
      if (opCode == 0)
      {
        // Parse the IP address string and return it
        return new String(reqData, 11, currLength, "ISO8859_1");
      }
      else
        return null; // indicates we connected to the server but the lookup failed
    }
    finally
    {
      try
      {
        sake.close();
      }catch (Exception e){}
      sake = null;
      try
      {
        os.close();
      }
      catch (Exception e){}
      os = null;
      try
      {
        is.close();
      }
      catch (Exception e){}
      is = null;
    }
  }

  public static int haveLocatorPingID(String guid) throws java.io.IOException
  {
    // Break the GUID string apart into 4 x 16 bit chunks
    int guid1, guid2, guid3, guid4;
    java.util.StringTokenizer toker = new java.util.StringTokenizer(guid, "-");
    if (toker.countTokens() != 4)
      throw new java.io.IOException("GUID is an invalid format of \"" + guid + "\" and it should be xxxx-xxxx-xxxx-xxxx");
    try
    {
      guid1 = Integer.parseInt(toker.nextToken(), 16);
      guid2 = Integer.parseInt(toker.nextToken(), 16);
      guid3 = Integer.parseInt(toker.nextToken(), 16);
      guid4 = Integer.parseInt(toker.nextToken(), 16);
    }
    catch (NumberFormatException e)
    {
      throw new java.io.IOException("GUID contains non-hex digits \"" + guid + "\"");
    }

    java.net.Socket sake = null;
    java.io.OutputStream os = null;
    java.io.InputStream is = null;
    try
    {
      sake = connectToServer();
      sake.setSoTimeout(10000);
      os = sake.getOutputStream();
      is = sake.getInputStream();
      // We always do 512 byte requests
      byte[] reqData = new byte[512];
      reqData[0] = (byte)'S';
      reqData[1] = (byte)'T';
      reqData[2] = (byte)'V';
      reqData[3] = 1;
      reqData[8] = 16; // opCode for ping
      reqData[9] = 0;
      reqData[10] = 8; // 64 bits for the GUID
      reqData[11] = (byte)((guid1 >> 8) & 0xFF);
      reqData[12] = (byte)(guid1 & 0xFF);
      reqData[13] = (byte)((guid2 >> 8) & 0xFF);
      reqData[14] = (byte)(guid2 & 0xFF);
      reqData[15] = (byte)((guid3 >> 8) & 0xFF);
      reqData[16] = (byte)(guid3 & 0xFF);
      reqData[17] = (byte)((guid4 >> 8) & 0xFF);
      reqData[18] = (byte)(guid4 & 0xFF);
      os.write(reqData);
      os.flush();
      // Read back the repsonse from the server
      int currOffset = 0;
      while (currOffset < reqData.length)
      {
        currOffset += is.read(reqData, currOffset, reqData.length - currOffset);
      }
      // Check the header bytes
      if (reqData[0] != 'S' || reqData[1] != 'T' || reqData[2] != 'V')
        throw new java.io.IOException("Invalid header format, missing 'STV'");
      // Check the version
      if (reqData[3] != 1)
        throw new java.io.IOException("Invalid version number:" + reqData[3]);
      // 4 byte pad and then the opcode
      byte opCode = reqData[8];
      // 2 byte length of valid data after the opcode
      int currLength = ((reqData[9] & 0xFF) << 8) | (reqData[10] & 0xFF);
      if (currLength > 500)
        throw new java.io.IOException("Invalid length in requeset of:" + currLength);
      return reqData[11];
    }
    finally
    {
      try
      {
        sake.close();
      }catch (Exception e){}
      sake = null;
      try
      {
        os.close();
      }
      catch (Exception e){}
      os = null;
      try
      {
        is.close();
      }
      catch (Exception e){}
      is = null;
    }
  }
}
