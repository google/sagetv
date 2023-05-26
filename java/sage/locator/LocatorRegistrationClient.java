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

public class LocatorRegistrationClient implements Runnable, LocatorConstants
{
  private static final String MESSAGES_PROP = "locator/messages";
  public LocatorRegistrationClient() throws Exception // security exceptions
  {
    alive = true;
    if (sage.Catbert.ENABLE_LOCATOR_API)
    {
      // Be sure we have our local secret locator keys created
      // See if the keys already exist
      java.io.File secretKeyFile = new java.io.File("SageTVLocator.secret.key");
      if (!secretKeyFile.isFile())
      {
        // Reset the system GUID if we lose the locator keys. Otherwise we'll never be able to validate the challenge/resposne
        // with the server!

        // NOTE NOTE NOTE We'll definitely want to do something where we have SageTVLocator.username.key so then a user could transfer the security key
        // from an old system to a new system and we could use that to verify the transfer of username is allowed. Otherwise we'll need some
        // kind of password (and that might be the only way; otherwise how do they get the secret key file off the HD200!)
        sage.Sage.putInt("locator/system_id_base", 0);
        if (sage.Sage.DBG) System.out.println("Resetting locator ID since the secret key file is gone!");
      }
      else
      {
        // Load the key info from the file
        if (sage.Sage.DBG) System.out.println("Loading locator secret key from filesystem since they already exist.");
        java.io.FileInputStream fis = new java.io.FileInputStream(secretKeyFile);
        secretKeyBytes = new byte[fis.available()];
        fis.read(secretKeyBytes);
        fis.close();
        javax.crypto.spec.DESKeySpec secKeySpec = new javax.crypto.spec.DESKeySpec(secretKeyBytes);
        javax.crypto.SecretKeyFactory keyFactory = javax.crypto.SecretKeyFactory.getInstance("DES");
        secretKey = keyFactory.generateSecret(secKeySpec);
      }

      String friendsProp = sage.Sage.get("locator/friend_list", "");
      java.util.StringTokenizer toker = new java.util.StringTokenizer(friendsProp, "|");
      friendList = new String[toker.countTokens()];
      for (int i = 0; i < friendList.length; i++)
        friendList[i] = toker.nextToken();

      String[] msgNodes = sage.Sage.childrenNames(MESSAGES_PROP);
      messages = new LocatorMsg[msgNodes.length];
      for (int i = 0; i < msgNodes.length; i++)
      {
        messages[i] = new LocatorMsg();
        messages[i].fromHandle = sage.Sage.get(MESSAGES_PROP + "/" + msgNodes[i] + "/from", "");
        messages[i].toHandle = sage.Sage.get(MESSAGES_PROP + "/" + msgNodes[i] + "/to", "");
        try
        {
          messages[i].msgID = Long.parseLong(msgNodes[i]);
        }
        catch (NumberFormatException nfe)
        {
          if (sage.Sage.DBG) System.out.println("ERROR with number formatting in message ID property of:" + nfe + " prop=" + msgNodes[i]);
        }
        messages[i].timestamp = sage.Sage.getLong(MESSAGES_PROP + "/" + msgNodes[i] + "/time", 0);
        messages[i].text = sage.Sage.get(MESSAGES_PROP + "/" + msgNodes[i] + "/text", "");
        String[] mediaNodes = sage.Sage.childrenNames(MESSAGES_PROP + "/" + msgNodes[i] + "/media");
        messages[i].media = new MediaInfo[mediaNodes.length];
        for (int j = 0; j < mediaNodes.length; j++)
        {
          messages[i].media[j] = new MediaInfo();
          messages[i].media[j].downloadID = sage.Sage.getInt(MESSAGES_PROP + "/" + msgNodes[i] + "/media/" + mediaNodes[j] + "/secure_download_id", 0);
          messages[i].media[j].mediaType = sage.Sage.getInt(MESSAGES_PROP + "/" + msgNodes[i] + "/media/" + mediaNodes[j] + "/mediatype", 0);
          messages[i].media[j].relativePath = sage.Sage.get(MESSAGES_PROP + "/" + msgNodes[i] + "/media/" + mediaNodes[j] + "/relative_path", "");
          String localPathStr = sage.Sage.get(MESSAGES_PROP + "/" + msgNodes[i] + "/media/" + mediaNodes[j] + "/local_path", "");
          if (localPathStr.length() > 0)
            messages[i].media[j].localPath = new java.io.File(localPathStr);
          messages[i].media[j].size = sage.Sage.getLong(MESSAGES_PROP + "/" + msgNodes[i] + "/media/" + mediaNodes[j] + "/size", 0);
        }
        if (messages[i].hasMedia())
        {
          messages[i].downloadCompleted = sage.Sage.getBoolean(MESSAGES_PROP + "/" + msgNodes[i] + "/downloaded", false);
          messages[i].downloadRequested = sage.Sage.getBoolean(MESSAGES_PROP + "/" + msgNodes[i] + "/requested", false);
        }
      }
    }
    else
    {
      // Be sure we have our local public/private locator keys created
      // See if the keys already exist
      java.io.File pubKeyFile = new java.io.File("SageTVLocator.public.key");
      java.io.File privKeyFile = new java.io.File("SageTVLocator.private.key");
      if (!pubKeyFile.isFile() || !privKeyFile.isFile())
      {
        // Reset the system GUID if we lose the locator keys. Otherwise we'll never be able to validate the challenge/resposne
        // with the server!
        sage.Sage.putInt("locator/system_id_base", 0);
        if (sage.Sage.DBG) System.out.println("Public/private locator key files do not exist. Generating RSA public/private keys for SageTV Locator...");
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        java.security.SecureRandom random = java.security.SecureRandom.getInstance("SHA1PRNG");
        keyGen.initialize(1024, random);
        java.security.KeyPair pair = keyGen.generateKeyPair();
        privKey = pair.getPrivate();
        pubKey = pair.getPublic();
        java.io.OutputStream fos = new java.io.FileOutputStream(pubKeyFile);
        pubKeyBytes = pubKey.getEncoded();
        fos.write(pubKeyBytes);
        fos.close();
        if (sage.Sage.DBG) System.out.println("Wrote out private key to file:" + pubKeyFile);
        fos = new java.io.FileOutputStream(privKeyFile);
        fos.write(privKey.getEncoded());
        fos.close();
        if (sage.Sage.DBG) System.out.println("Wrote out public key to file:" + privKeyFile);
      }
      else
      {
        // Load the key info from the files
        if (sage.Sage.DBG) System.out.println("Loading locator keys from filesystem since they already exist.");
        java.io.FileInputStream fis = new java.io.FileInputStream(pubKeyFile);
        pubKeyBytes = new byte[fis.available()];
        fis.read(pubKeyBytes);
        fis.close();
        java.security.spec.X509EncodedKeySpec pubKeySpec = new java.security.spec.X509EncodedKeySpec(pubKeyBytes);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        pubKey = keyFactory.generatePublic(pubKeySpec);
        fis = new java.io.FileInputStream(privKeyFile);
        byte[] myPrivKeyBytes = new byte[fis.available()];
        fis.read(myPrivKeyBytes);
        fis.close();
        java.security.spec.PKCS8EncodedKeySpec privKeySpec = new java.security.spec.PKCS8EncodedKeySpec(myPrivKeyBytes);
        privKey = keyFactory.generatePrivate(privKeySpec);
      }
    }

    Thread t = new Thread(this, "LocatorRegistrationClient");
    t.setDaemon(true);
    t.setPriority(Thread.MIN_PRIORITY);
    t.start();
  }

  // We pass in which messages to save so we don't bother re-writing the ones we already have
  static void saveMessageProperties(LocatorMsg[] saveMsgs)
  {
    for (int i = 0; i < saveMsgs.length; i++)
    {
      sage.Sage.put(MESSAGES_PROP + "/" + saveMsgs[i].msgID + "/from", saveMsgs[i].fromHandle);
      sage.Sage.put(MESSAGES_PROP + "/" + saveMsgs[i].msgID + "/to", saveMsgs[i].toHandle);
      sage.Sage.putLong(MESSAGES_PROP + "/" + saveMsgs[i].msgID + "/time", saveMsgs[i].timestamp);
      sage.Sage.put(MESSAGES_PROP + "/" + saveMsgs[i].msgID + "/text", saveMsgs[i].text);
      for (int j = 0; saveMsgs[i].media != null && j < saveMsgs[i].media.length; j++)
      {
        sage.Sage.putInt(MESSAGES_PROP + "/" + saveMsgs[i].msgID + "/media/" + j + "/secure_download_id", saveMsgs[i].media[j].downloadID);
        sage.Sage.putInt(MESSAGES_PROP + "/" + saveMsgs[i].msgID + "/media/" + j + "/mediatype", saveMsgs[i].media[j].mediaType);
        sage.Sage.put(MESSAGES_PROP + "/" + saveMsgs[i].msgID + "/media/" + j + "/relative_path", saveMsgs[i].media[j].relativePath);
        if (saveMsgs[i].media[j].localPath != null)
          sage.Sage.put(MESSAGES_PROP + "/" + saveMsgs[i].msgID + "/media/" + j + "/local_path", saveMsgs[i].media[j].localPath.toString());
        sage.Sage.putLong(MESSAGES_PROP + "/" + saveMsgs[i].msgID + "/media/" + j + "/size", saveMsgs[i].media[j].size);
      }
      if (saveMsgs[i].hasMedia())
      {
        sage.Sage.putBoolean(MESSAGES_PROP + "/" + saveMsgs[i].msgID + "/downloaded", saveMsgs[i].downloadCompleted);
        sage.Sage.putBoolean(MESSAGES_PROP + "/" + saveMsgs[i].msgID + "/requested", saveMsgs[i].downloadRequested);
      }
    }
  }

  private static java.net.Socket connectToServer() throws java.io.IOException
  {
    String theAddress = sage.Sage.get(LOCATOR_SERVER_PROP, LOCATOR_SERVER);
    try
    {
      java.net.Socket sock = new java.net.Socket(theAddress, LOCATOR_PORT);
      return sock;
    }
    catch (java.net.SocketException e1)
    {
    }
    catch (java.net.UnknownHostException e2)
    {
    }
    theAddress = sage.Sage.get(BACKUP_LOCATOR_SERVER_PROP, BACKUP_LOCATOR_SERVER);
    java.net.Socket sock = new java.net.Socket(theAddress, LOCATOR_PORT);
    return sock;
  }

  public void kickLocator()
  {
    synchronized (lock)
    {
      lock.notifyAll();
    }
  }

  private void fillInGuid(byte[] data, int offset)
  {
    long myGuid = getSystemGuid();
    data[offset] = (byte)((myGuid >> 56) & 0xFF);
    data[offset + 1] = (byte)((myGuid >> 48) & 0xFF);
    data[offset + 2] = (byte)((myGuid >> 40) & 0xFF);
    data[offset + 3] = (byte)((myGuid >> 32) & 0xFF);
    data[offset + 4] = (byte)((myGuid >> 24) & 0xFF);
    data[offset + 5] = (byte)((myGuid >> 16) & 0xFF);
    data[offset + 6] = (byte)((myGuid >> 8) & 0xFF);
    data[offset + 7] = (byte)(myGuid & 0xFF);
  }

  public synchronized void updateLocatorServer() throws Exception
  {
    if (sage.Catbert.ENABLE_LOCATOR_API)
    {
      int localPort = sage.Sage.getInt("placeshifter_port_forward_extern_port",
          sage.Sage.getInt("extender_and_placeshifter_server_port", 31099));
      if (localPort <= 0)
        localPort = sage.Sage.getInt("extender_and_placeshifter_server_port", 31099);
      /* OLD STYLE
			byte[] updateData = new byte[10];
			fillInGuid(updateData, 0);
			updateData[8] = (byte)((localPort >> 8) & 0xFF);
			updateData[9] = (byte)(localPort & 0xFF);
			performLocatorOperation(STANDARD_CLIENT_UPDATE_REQUEST, updateData);
       */
      String ourHandle = sage.Sage.get("locator/handle", "");
      byte[] handleBytes = ourHandle.getBytes("UTF-8");
      byte[] updateData = new byte[2 + handleBytes.length + 8 + 2 + 4 + 8];
      int currOffset = 0;
      writeShort(handleBytes.length, updateData, currOffset);
      currOffset += 2;
      System.arraycopy(handleBytes, 0, updateData, currOffset, handleBytes.length);
      currOffset += handleBytes.length;
      fillInGuid(updateData, currOffset);
      currOffset += 8;
      writeShort(localPort, updateData, currOffset);
      currOffset += 2;
      writeInt(sage.Sage.getInt("locator/friend_update_id", 0), updateData, currOffset);
      currOffset += 4;
      writeLong(sage.Sage.getLong("locator/update_msg_id", 0), updateData, currOffset);
      performLocatorOperation(USER_UPDATE_REQUEST, updateData);
    }
    else // Old V1 style
    {
      java.net.Socket sake = null;
      java.io.OutputStream os = null;
      java.io.InputStream is = null;
      long myGuid = getSystemGuid();
      try
      {
        sake = connectToServer();
        sake.setSoTimeout(10000);
        os = sake.getOutputStream();
        is = sake.getInputStream();
        int localPort = sage.Sage.getInt("placeshifter_port_forward_extern_port",
            sage.Sage.getInt("extender_and_placeshifter_server_port", 31099));
        if (localPort <= 0)
          localPort = sage.Sage.getInt("extender_and_placeshifter_server_port", 31099);
        // We always do 512 byte requests
        byte[] reqData = new byte[512];

        // We send the registration submission first and then see what the server says
        reqData[0] = (byte)'S';
        reqData[1] = (byte)'T';
        reqData[2] = (byte)'V';
        reqData[3] = 1;
        reqData[8] = 0; // opCode for registration
        reqData[9] = 0;
        reqData[10] = 10; // 64 bits for the GUID + 16 bits for the port
        reqData[11] = (byte)((myGuid >> 56) & 0xFF);
        reqData[12] = (byte)((myGuid >> 48) & 0xFF);
        reqData[13] = (byte)((myGuid >> 40) & 0xFF);
        reqData[14] = (byte)((myGuid >> 32) & 0xFF);
        reqData[15] = (byte)((myGuid >> 24) & 0xFF);
        reqData[16] = (byte)((myGuid >> 16) & 0xFF);
        reqData[17] = (byte)((myGuid >> 8) & 0xFF);
        reqData[18] = (byte)(myGuid & 0xFF);
        reqData[19] = (byte)((localPort >> 8) & 0xFF);
        reqData[20] = (byte)(localPort & 0xFF);
        os.write(reqData);
        os.flush();
        byte[] challengeBytes = null;
        boolean keepConn = true;
        while (keepConn)
        {
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
          switch (opCode)
          {
            case 0: // Our update succeeded
              return;
            case 1: // We're getting a challenge from the server. Encrypt it with our private key and send it back.
              if (sage.Sage.DBG) System.out.println("Server challenged location update...sending encrypted challenge response back to server");
              try
              {
                javax.crypto.Cipher encryptCipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
                encryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, privKey);
                byte[] encryptedChallenge = encryptCipher.doFinal(reqData, 11, currLength);
                reqData[9] = (byte)((encryptedChallenge.length >> 8) & 0xFF);
                reqData[10] = (byte) (encryptedChallenge.length & 0xFF);
                System.arraycopy(encryptedChallenge, 0, reqData, 11, encryptedChallenge.length);
              }
              catch (Exception e)
              {
                // If it's Java 1.4 then we don't support RSA and we just XOR the challenge with our public key and send that back
                if (sage.Sage.DBG) System.out.println("RSA support is not there, using alternate lookup crypto submit..." + e);
                byte[] encryptedChallenge = new byte[currLength];
                for (int i = 0; i < encryptedChallenge.length; i++)
                {
                  encryptedChallenge[i] = reqData[11 + i];
                  if (i < pubKeyBytes.length)
                    encryptedChallenge[i] = (byte)(encryptedChallenge[i] ^ pubKeyBytes[i]);
                }
                System.arraycopy(encryptedChallenge, 0, reqData, 11, encryptedChallenge.length);
              }
              os.write(reqData);
              os.flush();
              break;
            case 2: // Server is requesting our public key to initialize us in the locator system
              if (sage.Sage.DBG) System.out.println("Server is requesting init of the ID for this client, send the public locator key");
              reqData[9] = (byte)((pubKeyBytes.length >> 8) & 0xFF);
              reqData[10] = (byte) (pubKeyBytes.length & 0xFF);
              System.arraycopy(pubKeyBytes, 0, reqData, 11, pubKeyBytes.length);
              os.write(reqData);
              os.flush();
              break;
            default:
              throw new java.io.IOException("Invalid opcode of:" + opCode);
          }
        }
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

  // Returns the user handle that is associated with our locator ID now, null if it failed assocation, it'll
  // return a different value than what's passed in if we already have a handle association.
  public synchronized String requestUserHandle(String newHandle) throws Exception
  {
    byte[] textBytes = newHandle.getBytes("UTF-8");
    byte[] updateData = new byte[10 + textBytes.length];
    fillInGuid(updateData, 0);
    writeShort(textBytes.length, updateData, 8);
    System.arraycopy(textBytes, 0, updateData, 10, textBytes.length);
    LocatorResult rez = performLocatorOperation(REQUEST_HANDLE, updateData);
    if (rez == null) return null;
    if (rez.rv == SUCCESS_REPLY)
    {
      if (sage.Sage.DBG) System.out.println("New locator handle has been established of: " + newHandle);
      sage.Sage.put("locator/handle", newHandle);
      return newHandle;
    }
    else if (rez.rv == LOCATOR_ALREADY_ASSOCIATED_WITH_HANDLE)
    {
      if (rez.data != null)
      {
        if (sage.Sage.DBG) System.out.println("Existing locator handle has been established of: " + rez.data);
        sage.Sage.put("locator/handle", rez.data.toString());
      }
      return rez.data != null ? rez.data.toString() : null;
    }
    else //if (rez.rv == HANDLE_ALREADY_USED)
      return null;
  }

  public String[] getFriendList()
  {
    return friendList;
  }

  /* Done automatically with the update system now
	private synchronized String[] updateFriendList() throws Exception
	{
		String ourHandle = sage.Sage.get("locator/handle", "");
		if (ourHandle.length() == 0)
			return sage.Pooler.EMPTY_STRING_ARRAY;
		byte[] textBytes = ourHandle.getBytes("UTF-8");
		byte[] updateData = new byte[2 + textBytes.length];
		writeShort(textBytes.length, updateData, 0);
		System.arraycopy(textBytes, 0, updateData, 2, textBytes.length);
		LocatorResult rv = performLocatorOperation(GET_FRIEND_LIST, updateData);
		if (rv == null || rv.data == null) return null;
		FriendData[] friendData = (FriendData[]) rv.data;
		String[] names = new String[friendData.length];
		for (int i = 0; i < friendData.length; i++)
			names[i] = friendData[i].handle;
		return names;
	}
   */

  /* handled automatically in the update process now
	private synchronized LocatorMsg[] getAllMessages() throws Exception
	{
		String ourHandle = sage.Sage.get("locator/handle", "");
		if (ourHandle.length() == 0)
			return new LocatorMsg[0];
		byte[] handleBytes = ourHandle.getBytes("UTF-8");
		byte[] updateData = new byte[handleBytes.length + 10];
		writeShort(handleBytes.length, updateData, 8);
		System.arraycopy(handleBytes, 0, updateData, 10, handleBytes.length);
		writeLong(sage.Sage.getLong("locator/update_msg_id", 0), updateData, 0);
		LocatorResult rv = performLocatorOperation(GET_NEW_MESSAGES, updateData);
		if (rv == null || rv.rv != SUCCESS_REPLY)
			return null;
		else
			return (LocatorMsg[]) rv.data;
	}
   */
  public LocatorMsg[] getMessages()
  {
    LocatorMsg[] allMsgs = messages;
    java.util.Vector rv = new java.util.Vector();
    for (int i = 0; i < allMsgs.length; i++)
      if (allMsgs[i].msgType != FRIEND_REQUEST_MSG)
        rv.add(allMsgs[i]);
    return (LocatorMsg[]) rv.toArray(new LocatorMsg[0]);
  }

  public LocatorMsg[] getFriendRequests()
  {
    LocatorMsg[] allMsgs = messages;
    java.util.Vector rv = new java.util.Vector();
    for (int i = 0; i < allMsgs.length; i++)
      if (allMsgs[i].msgType == FRIEND_REQUEST_MSG)
        rv.add(allMsgs[i]);
    return (LocatorMsg[]) rv.toArray(new LocatorMsg[0]);
  }

  public synchronized boolean killFriendship(String toHandle) throws Exception
  {
    String ourHandle = sage.Sage.get("locator/handle", "");
    if (ourHandle.length() == 0)
      return false;
    byte[] ourHandleBytes = ourHandle.getBytes("UTF-8");
    byte[] toHandleBytes = toHandle.getBytes("UTF-8");
    byte[] updateData = new byte[4 + ourHandleBytes.length + toHandleBytes.length];
    writeShort(ourHandleBytes.length, updateData, 0);
    System.arraycopy(ourHandleBytes, 0, updateData, 2, ourHandleBytes.length);
    writeShort(toHandleBytes.length, updateData, 2 + ourHandleBytes.length);
    System.arraycopy(toHandleBytes, 0, updateData, 4 + ourHandleBytes.length, toHandleBytes.length);
    LocatorResult rv = performLocatorOperation(KILL_FRIENDSHIP, updateData);
    if (rv == null || rv.rv != SUCCESS_REPLY)
      return false;
    else
    {
      // Now we want to do an update to get the new friends list
      updateLocatorServer();
      return true;
    }
  }

  public synchronized String lookupFriendIP(String toHandle) throws Exception
  {
    String ourHandle = sage.Sage.get("locator/handle", "");
    if (ourHandle.length() == 0)
      return null;
    byte[] ourHandleBytes = ourHandle.getBytes("UTF-8");
    byte[] toHandleBytes = toHandle.getBytes("UTF-8");
    byte[] updateData = new byte[4 + ourHandleBytes.length + toHandleBytes.length];
    writeShort(ourHandleBytes.length, updateData, 0);
    System.arraycopy(ourHandleBytes, 0, updateData, 2, ourHandleBytes.length);
    writeShort(toHandleBytes.length, updateData, 2 + ourHandleBytes.length);
    System.arraycopy(toHandleBytes, 0, updateData, 4 + ourHandleBytes.length, toHandleBytes.length);
    LocatorResult rv = performLocatorOperation(LOOKUP_IP_FOR_FRIEND, updateData);
    if (rv == null || rv.rv != SUCCESS_REPLY || rv.data == null)
      return null;
    else
      return rv.data.toString();
  }

  // This removes it from the local message store
  public synchronized boolean deleteMessage(LocatorMsg deleteMe)
  {
    for (int i = 0; i < messages.length; i++)
    {
      if (messages[i].msgID == deleteMe.msgID)
      {
        // Cleanup any file transfer authorizations we had for this file
        for (int j = 0; messages[i].media != null && j < messages[i].media.length; j++)
          sage.MiniClientSageRenderer.deauthorizeExternalFileTransfer(messages[i].media[j].downloadID);
        LocatorMsg[] newMsgs = new LocatorMsg[messages.length - 1];
        System.arraycopy(messages, 0, newMsgs, 0, i);
        System.arraycopy(messages, i + 1, newMsgs, i, newMsgs.length - i);
        messages = newMsgs;
        removeFromDownloadQueue(deleteMe);
        sage.Sage.removeNode(MESSAGES_PROP + "/" + deleteMe.msgID);
        return true;
      }
    }
    return false;
  }

  // This deletes it from the server
  private synchronized boolean deleteMessageFromServer(LocatorMsg deleteMe) throws Exception
  {
    String ourHandle = sage.Sage.get("locator/handle", "");
    if (ourHandle.length() == 0)
      return false;
    byte[] ourHandleBytes = ourHandle.getBytes("UTF-8");
    byte[] updateData = new byte[10 + ourHandleBytes.length];
    writeShort(ourHandleBytes.length, updateData, 0);
    System.arraycopy(ourHandleBytes, 0, updateData, 2, ourHandleBytes.length);
    writeLong(deleteMe.msgID, updateData, 2 + ourHandleBytes.length);
    LocatorResult rv = performLocatorOperation(DELETE_MESSAGE, updateData);
    if (rv == null || rv.rv != SUCCESS_REPLY)
      return false;
    else
      return true;
  }

  public synchronized boolean sendFriendRequest(String toHandle, String msgText) throws Exception
  {
    return sendMessage(toHandle, msgText, FRIEND_REQUEST_MSG, null);
  }

  public synchronized boolean acceptFriendRequest(String toHandle) throws Exception
  {
    boolean rv = sendMessage(toHandle, null, FRIEND_REPLY_ACCEPT_MSG, null);
    // Now we want to do an update to get the new friends list
    updateLocatorServer();
    return rv;
  }

  public synchronized boolean rejectFriendRequest(String toHandle) throws Exception
  {
    return sendMessage(toHandle, null, FRIEND_REPLY_REJECT_MSG, null);
  }

  public synchronized boolean sendMessage(String toHandle, String msgText, MediaInfo[] msgMedia) throws Exception
  {
    return sendMessage(toHandle, msgText, NORMAL_MSG, msgMedia);
  }
  private synchronized boolean sendMessage(String toHandle, String msgText, int msgType, MediaInfo[] msgMedia) throws Exception
  {
    if ((msgMedia != null && msgMedia.length == 0) && msgType != NORMAL_MSG)
      throw new IllegalArgumentException("Can only specify message media if it's the normal message type!");
    if (msgType != NORMAL_MSG && msgType != FRIEND_REPLY_ACCEPT_MSG && msgType != FRIEND_REPLY_REJECT_MSG && msgType != FRIEND_REQUEST_MSG)
      throw new IllegalArgumentException("Invalid message type passed to send message of " + msgType);
    String ourHandle = sage.Sage.get("locator/handle", "");
    if (ourHandle.length() == 0)
      return false;
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(4096);
    java.io.DataOutputStream daos = new java.io.DataOutputStream(baos);
    byte[] ourHandleBytes = ourHandle.getBytes("UTF-8");
    daos.writeShort(ourHandleBytes.length);
    daos.write(ourHandleBytes);
    daos.writeByte(msgType);
    byte[] toHandleBytes = toHandle.getBytes("UTF-8");
    daos.writeShort(toHandleBytes.length);
    daos.write(toHandleBytes);
    if ((msgText == null || msgText.length() == 0) && msgType == FRIEND_REQUEST_MSG)
    {
      msgText = "This is the default friend request message text.";
    }
    byte[] textBytes = (msgText == null) ? new byte[0] : msgText.getBytes("UTF-8");
    daos.writeShort(textBytes.length);
    daos.write(textBytes);
    if (msgMedia == null)
      daos.writeShort(0);
    else
    {
      daos.writeShort(msgMedia.length);
      for (int i = 0; i < msgMedia.length; i++)
      {
        daos.writeByte(msgMedia[i].mediaType);
        daos.writeLong(msgMedia[i].size);
        daos.writeInt(msgMedia[i].downloadID);
        byte[] pathBytes = msgMedia[i].relativePath.getBytes("UTF-8");
        daos.writeShort(pathBytes.length);
        daos.write(pathBytes);
      }
    }
    LocatorResult rv = performLocatorOperation(SEND_MESSAGE, baos.toByteArray());
    if (rv == null || rv.rv != SUCCESS_REPLY)
      return false;
    else
      return true;
  }

  private synchronized LocatorResult performLocatorOperation(int opcode, byte[] opdata) throws Exception
  {
    java.net.Socket sake = null;
    java.io.OutputStream os = null;
    java.io.InputStream is = null;
    try
    {
      sake = connectToServer();
      System.out.println("Connected to locator server");
      sake.setSoTimeout(10000);
      os = sake.getOutputStream();
      is = sake.getInputStream();
      // We always do at least 512 byte requests
      byte[] reqData = new byte[512];

      // We send the registration submission first and then see what the server says
      reqData[0] = (byte)'S';
      reqData[1] = (byte)'T';
      reqData[2] = (byte)'V';
      reqData[3] = 2;
      reqData[8] = (byte)(opcode & 0xFF); // opCode for registration
      int dataLength = (opdata == null) ? 0 : opdata.length;
      reqData[9] = (byte)((dataLength >>> 16) & 0xFF);
      reqData[10] = (byte)((dataLength >>> 8) & 0xFF);
      reqData[11] = (byte)(dataLength & 0xFF);
      if (opdata != null)
        System.arraycopy(opdata, 0, reqData, DATA_OFFSET, Math.min(reqData.length - DATA_OFFSET, opdata.length));
      os.write(reqData);
      if (opdata != null && opdata.length > reqData.length - DATA_OFFSET)
      {
        int remBytes = opdata.length - (reqData.length - DATA_OFFSET);
        os.write(opdata, opdata.length - remBytes, remBytes);
      }
      os.flush();
      byte[] challengeBytes = null;
      boolean keepConn = true;
      boolean updateFriendsNext = false;
      while (keepConn)
      {
        // Read back the repsonse from the server
        int currOffset = 0;
        while (currOffset < reqData.length)
        {
          int numRead = is.read(reqData, currOffset, reqData.length - currOffset);
          if (numRead < 0)
            throw new java.io.EOFException();
          currOffset += numRead;
        }
        // Check the header bytes
        if (reqData[0] != 'S' || reqData[1] != 'T' || reqData[2] != 'V')
          throw new java.io.IOException("Invalid header format, missing 'STV'");
        // Check the version
        if (reqData[3] != 2)
          throw new java.io.IOException("Invalid version number:" + reqData[3]);
        // 4 byte pad and then the opcode
        byte serverOpCode = reqData[8];
        // 3 byte length of valid data after the opcode
        int currLength = ((reqData[9] & 0xFF) << 16) | ((reqData[10] & 0xFF) << 8) | (reqData[11] & 0xFF);
        System.out.println("Locator Recvd opcode=" + serverOpCode + " len=" + currLength);
        byte[] fullData = reqData;
        // If we go over the standard 512 byte packet
        if (currLength + DATA_OFFSET > reqData.length)
        {
          fullData = new byte[DATA_OFFSET + currLength];
          System.arraycopy(reqData, 0, fullData, 0, reqData.length);
          currOffset = reqData.length;
          while (currOffset < fullData.length)
          {
            int numRead = is.read(fullData, currOffset, fullData.length - currOffset);
            if (numRead < 0)
              throw new java.io.EOFException();
            currOffset += numRead;
          }
        }
        switch (serverOpCode)
        {
          case SUCCESS_REPLY: // Our request succeeded
            if (currLength == 0 || opcode == STANDARD_CLIENT_UPDATE_REQUEST || opcode == REQUEST_HANDLE)
              return new LocatorResult(SUCCESS_REPLY, null);
            // Handle other opcodes here
            if (opcode == GET_FRIEND_LIST)
            {
              if (sage.Sage.DBG) System.out.println("Received friend list reply from server.");
              // This has special data in it we need to extract
              currOffset = DATA_OFFSET;
              int friendUpdateID = readInt(fullData, currOffset);
              currOffset += 4;
              int numFriends = readInt(fullData, currOffset);
              currOffset += 4;
              FriendData[] rv = new FriendData[numFriends];
              String friendsProp = "";
              String[] newFriendList = new String[numFriends];
              for (int i = 0; i < numFriends; i++)
              {
                int strlen = readShort(fullData, currOffset);
                currOffset += 2;
                String handle = new String(fullData, currOffset, strlen, "UTF-8");
                newFriendList[i] = handle;
                friendsProp += handle + "|";
                currOffset += strlen;
                long avatarID = readLong(fullData, currOffset);
                currOffset += 8;
                rv[i] = new FriendData(handle, avatarID);
              }
              friendList = newFriendList;
              sage.Sage.put("locator/friend_list", friendsProp);
              sage.Sage.putInt("locator/friend_update_id", friendUpdateID);
              return new LocatorResult(SUCCESS_REPLY, null);
            }
            else if (opcode == GET_NEW_MESSAGES)
            {
              if (sage.Sage.DBG) System.out.println("Received new messages reply from server.");
              // This has special data in it we need to extract
              currOffset = DATA_OFFSET;
              java.util.Set existingMsgIDs = new java.util.HashSet();
              for (int i = 0; i < messages.length; i++)
                existingMsgIDs.add(new Long(messages[i].msgID));
              int numMsgs = readInt(fullData, currOffset);
              currOffset += 4;
              java.util.Vector newMsgsVec = new java.util.Vector();
              long updateMsgID = sage.Sage.getLong("locator/update_msg_id", 0);
              for (int i = 0; i < numMsgs; i++)
              {
                LocatorMsg newMsg = new LocatorMsg();
                newMsg.msgID = readLong(fullData, currOffset);
                updateMsgID = Math.max(updateMsgID, newMsg.msgID);
                currOffset += 8;
                newMsg.msgType = fullData[currOffset++] & 0xFF;
                int strlen = readShort(fullData, currOffset);
                currOffset += 2;
                newMsg.fromHandle = new String(fullData, currOffset, strlen, "UTF-8");
                currOffset += strlen;
                strlen = readShort(fullData, currOffset);
                currOffset += 2;
                newMsg.toHandle = new String(fullData, currOffset, strlen, "UTF-8");
                currOffset += strlen;
                newMsg.timestamp = readLong(fullData, currOffset);
                currOffset += 8;
                strlen = readShort(fullData, currOffset);
                currOffset += 2;
                newMsg.text = new String(fullData, currOffset, strlen, "UTF-8");
                currOffset += strlen;
                int numMedia = readShort(fullData, currOffset);
                currOffset += 2;
                newMsg.media = new MediaInfo[numMedia];
                for (int j = 0; j < numMedia; j++)
                {
                  newMsg.media[j] = new MediaInfo();
                  newMsg.media[j].mediaType = fullData[currOffset++] & 0xFF;
                  newMsg.media[j].size = readLong(fullData, currOffset);
                  currOffset += 8;
                  newMsg.media[j].downloadID = readInt(fullData, currOffset);
                  currOffset += 4;
                  strlen = readShort(fullData, currOffset);
                  currOffset += 2;
                  newMsg.media[j].relativePath = new String(fullData, currOffset, strlen, "UTF-8");
                  currOffset += strlen;
                }
                // Ensure we don't download duplicates of the same message somehow
                if (existingMsgIDs.add(new Long(newMsg.msgID)))
                  newMsgsVec.add(newMsg);
              }
              LocatorMsg[] newMsgs = (LocatorMsg[]) newMsgsVec.toArray(new LocatorMsg[0]);
              LocatorMsg[] allMsgs = new LocatorMsg[newMsgs.length + messages.length];
              System.arraycopy(messages, 0, allMsgs, 0, messages.length);
              System.arraycopy(newMsgs, 0, allMsgs, messages.length, newMsgs.length);
              messages = allMsgs;
              saveMessageProperties(newMsgs);
              sage.Sage.putLong("locator/update_msg_id", updateMsgID);

              // NOTE: Now would be when we could delete the messages from the server to clean them up!

              // Register anything that's setup for autodownload
              for (int i = 0; i < newMsgs.length; i++)
              {
                if (newMsgs[i].hasMedia() && sage.Sage.getBoolean("locator/friends/" + newMsgs[i].fromHandle + "/autodownload", false))
                {
                  if (sage.Sage.DBG) System.out.println("Automatically setting up message for media download of:" + newMsgs[i]);
                  addToDownloadQueue(newMsgs[i]);
                }
              }

              if (updateFriendsNext)
              {
                updateFriendsNext = false;
                if (sage.Sage.DBG) System.out.println("Continuing update; friends lists needs to downloaded as well...");
                reqData[8] = GET_FRIEND_LIST;
                opcode = GET_FRIEND_LIST;
                String ourHandle = sage.Sage.get("locator/handle", "");
                byte[] textBytes = ourHandle.getBytes("UTF-8");
                writeShort(textBytes.length, reqData, DATA_OFFSET);
                System.arraycopy(textBytes, 0, reqData, DATA_OFFSET + 2, textBytes.length);
                write3ByteInt(textBytes.length + 2, reqData, 9);
                os.write(reqData);
                os.flush();
                continue;
              }

              return new LocatorResult(SUCCESS_REPLY, null);
            }
            else if (opcode == USER_UPDATE_REQUEST)
            {
              // Check the bitmask to see if we need to do more processing
              int updateMask = (currLength == 0) ? 0 : (fullData[DATA_OFFSET] & 0xFF);
              if (updateMask == UPDATED_FRIENDS_MASK)
              {
                if (sage.Sage.DBG) System.out.println("Friends list has changed; update it with the server...");
                reqData[8] = GET_FRIEND_LIST;
                opcode = GET_FRIEND_LIST;
                String ourHandle = sage.Sage.get("locator/handle", "");
                byte[] textBytes = ourHandle.getBytes("UTF-8");
                writeShort(textBytes.length, reqData, DATA_OFFSET);
                System.arraycopy(textBytes, 0, reqData, DATA_OFFSET + 2, textBytes.length);
                write3ByteInt(textBytes.length + 2, reqData, 9);
                os.write(reqData);
                os.flush();
                continue;
              }
              else if ((updateMask & UPDATED_MSGS_MASK) == UPDATED_MSGS_MASK)
              {
                updateFriendsNext = (updateMask & UPDATED_FRIENDS_MASK) == UPDATED_FRIENDS_MASK;
                if (sage.Sage.DBG) System.out.println("New messages on the server; download them locally...");
                reqData[8] = GET_NEW_MESSAGES;
                opcode = GET_NEW_MESSAGES;
                String ourHandle = sage.Sage.get("locator/handle", "");
                byte[] handleBytes = ourHandle.getBytes("UTF-8");
                writeShort(handleBytes.length, reqData, DATA_OFFSET + 8);
                System.arraycopy(handleBytes, 0, reqData, DATA_OFFSET + 10, handleBytes.length);
                writeLong(sage.Sage.getLong("locator/update_msg_id", 0), reqData, DATA_OFFSET);
                write3ByteInt(handleBytes.length + 10, reqData, 9);
                os.write(reqData);
                os.flush();
                continue;
              }
              return new LocatorResult(SUCCESS_REPLY, null);
            }
            else if (opcode == LOOKUP_IP_FOR_FRIEND)
              return new LocatorResult(SUCCESS_REPLY, new String(reqData, DATA_OFFSET, currLength, "UTF-8"));
            return null;
          case CHALLENGE_RESPONSE: // We're getting a challenge from the server. Decrypt the challenge w/ our secret key and send it back
            if (sage.Sage.DBG) System.out.println("Server challenged location update...sending encrypted challenge response back to server");
            if (secretKey == null)
              throw new java.io.IOException("Server requested a challenge, but we don't have a secret key established!");
            javax.crypto.Cipher decryptCipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding");
            decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedChallenge = decryptCipher.doFinal(reqData, DATA_OFFSET, currLength);
            reqData[9] = 0;
            reqData[10] = (byte)((decryptedChallenge.length >> 8) & 0xFF);
            reqData[11] = (byte) (decryptedChallenge.length & 0xFF);
            System.arraycopy(decryptedChallenge, 0, reqData, DATA_OFFSET, decryptedChallenge.length);
            os.write(reqData);
            os.flush();
            break;
          case NEW_CLIENT_AUTH: // Server is requesting our public key to initialize us in the locator system; this is part of the DH key pair generation
            // which is then used to generate the shared secret key we save
            if (sage.Sage.DBG) System.out.println("Server is requesting init of the ID for this client, send the public DH key to finish keygen");
            byte[] serverPubKeyBytes = new byte[currLength];
            System.arraycopy(reqData, DATA_OFFSET, serverPubKeyBytes, 0, currLength);

            java.security.spec.X509EncodedKeySpec pubKeySpec = new java.security.spec.X509EncodedKeySpec(serverPubKeyBytes);
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("DH");
            javax.crypto.interfaces.DHPublicKey serverPublicKey = (javax.crypto.interfaces.DHPublicKey)keyFactory.generatePublic(pubKeySpec);

            /*
             * Bob gets the DH parameters associated with Alice's public key.
             * He must use the same parameters when he generates his own key
             * pair.
             */
            javax.crypto.spec.DHParameterSpec dhParamSpec = serverPublicKey.getParams();

            // Bob creates his own DH key pair
            if (sage.Sage.DBG) System.out.println("Generate DH keypair ...");
            java.security.KeyPairGenerator bobKpairGen = java.security.KeyPairGenerator.getInstance("DH");
            bobKpairGen.initialize(dhParamSpec);
            java.security.KeyPair bobKpair = bobKpairGen.generateKeyPair();

            // Bob creates and initializes his DH KeyAgreement object
            javax.crypto.KeyAgreement bobKeyAgree = javax.crypto.KeyAgreement.getInstance("DH");
            bobKeyAgree.init(bobKpair.getPrivate());

            // Bob encodes his public key, and sends it over to Alice.
            byte[] pubKeyBytes = bobKpair.getPublic().getEncoded();

            // We also have to generate the shared secret now
            bobKeyAgree.doPhase(serverPublicKey, true);
            secretKey = bobKeyAgree.generateSecret("DES");

            java.io.FileOutputStream secretKeyFile = new java.io.FileOutputStream(new java.io.File("SageTVLocator.secret.key"));
            secretKeyFile.write(secretKey.getEncoded());
            secretKeyFile.close();

            reqData[9] = 0;
            reqData[10] = (byte)((pubKeyBytes.length >> 8) & 0xFF);
            reqData[11] = (byte) (pubKeyBytes.length & 0xFF);
            System.arraycopy(pubKeyBytes, 0, reqData, DATA_OFFSET, pubKeyBytes.length);
            os.write(reqData);
            os.flush();
            break;
          case REQUESTOR_HANDLE_NOT_IN_DB:
            // This means are handle is not valid. We need to clear it.
            // Don't wipe the messages though; but do clear the friend list and the update IDs
            // since we're not going to be in sync properly anymore
            friendList = sage.Pooler.EMPTY_STRING_ARRAY;
            sage.Sage.put("locator/friend_list", "");
            sage.Sage.put("locator/handle", "");
            sage.Sage.putInt("locator/friend_update_id", 0);
            sage.Sage.putLong("locator/update_msg_id", 0);
            return new LocatorResult(serverOpCode, null);
          case HANDLE_NAME_NOT_ALLOWED:
          case HANDLE_ALREADY_USED:
          case TARGET_HANDLE_NOT_IN_DB:
          case DUPLICATE_FRIEND_REQUEST:
          case TARGET_IS_NOT_A_FRIEND:
            return new LocatorResult(serverOpCode, null);
          case LOCATOR_ALREADY_ASSOCIATED_WITH_HANDLE:
            return new LocatorResult(LOCATOR_ALREADY_ASSOCIATED_WITH_HANDLE, new String(reqData, DATA_OFFSET, currLength, "UTF-8"));
          default:
            throw new java.io.IOException("Invalid opcode of:" + serverOpCode);
        }
      }
    }
    catch (java.io.EOFException ef)
    {
      // These can be normal
    }
    finally
    {
      System.out.println("Disconnecting from locator server");
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
    return null;
  }

  // NOTE: WE HAVE TO ENSURE THAT THERE ARE ACTUAL FOLDERS SETUP THAT THE MEDIA CAN BE SAVED TO
  // OTHERWISE WE COULD ENDUP FAILING COMPLETELY IN HERE!!!!
  public boolean addToDownloadQueue(LocatorMsg downloadMe)
  {
    if (!downloadMe.downloadRequested && downloadMe.hasMedia())
    {
      downloadMe.downloadRequested = true;
      // Setup the folders to save the message to
      for (int i = 0; i < downloadMe.media.length; i++)
      {
        downloadMe.media[i].localPath = getTargetFolder(downloadMe.media[i], downloadMe.fromHandle);
        if (sage.Sage.DBG) System.out.println("Setup download of media to target path: " + downloadMe.media[i].localPath);
      }
      saveMessageProperties(new LocatorMsg[] { downloadMe });
      transferer.queueMessageTransfer(downloadMe);
      return true;
    }
    else if (downloadMe.hasMedia())
    {
      transferer.queueMessageTransfer(downloadMe);
      return true;
    }
    return false;
  }

  public boolean removeFromDownloadQueue(LocatorMsg removeMe)
  {
    if (removeMe.downloadRequested)
    {
      removeMe.downloadRequested = false;
      saveMessageProperties(new LocatorMsg[] { removeMe });
      transferer.dequeueMessageTransfer(removeMe);
      return true;
    }
    return false;
  }

  public LocatorTransferManager getTransferer()
  {
    return transferer;
  }

  private java.io.File getTargetFolder(MediaInfo theMedia, String fromUser)
  {
    // We need to get the folder based on file type
    java.io.File[] libFolders = null;
    if (theMedia.mediaType == PICTURE_TYPE)
      libFolders = sage.SeekerSelector.getInstance().getArchiveDirectories(sage.Seeker.PICTURE_DIR_MASK);
    else if (theMedia.mediaType == AUDIO_TYPE)
      libFolders = sage.SeekerSelector.getInstance().getArchiveDirectories(sage.Seeker.MUSIC_DIR_MASK);
    else
      libFolders = sage.SeekerSelector.getInstance().getArchiveDirectories(sage.Seeker.VIDEO_DIR_MASK);

    return new java.io.File(libFolders[0], sage.Sage.rez("From_User", new Object[] { fromUser }) + java.io.File.separatorChar + theMedia.relativePath);
  }

  public static long getSystemGuid()
  {
    // This is done by using a random 32 bit Integer (which is retrieved from the properties file) we then
    // shift that up 32 bits and XOR it with the 40 byte MAC address we get from the system
    int randomID = sage.Sage.getInt("locator/system_id_base", 0);
    if (randomID == 0)
    {
      randomID = new java.util.Random().nextInt();
      sage.Sage.putInt("locator/system_id_base", randomID);
      sage.Sage.savePrefs();
    }
    byte[] mac = sage.IOUtils.getMACAddress();
    if (mac == null)
    {
      System.out.println("MAC address was not able to be detected, using zero for the MAC!");
      mac = new byte[6];
    }
    // Now generate the GUID
    long guid = ((long)randomID << 32) ^ (((mac[1] & 0xFF) << 32) | ((mac[2] & 0xFF) << 24) | ((mac[3] & 0xFF) << 16) |
        ((mac[4] & 0xFF) << 8) | (mac[5] & 0xFF));

    // Now write out the pretty version to the properties file
    String prettyGuid = getPrettyGuid(guid);
    if (!sage.Sage.get("locator/id", "").equals(prettyGuid))
    {
      sage.Sage.put("locator/id", prettyGuid);
      sage.Sage.savePrefs();
    }

    return guid;
  }

  public static String getPrettyGuid(long guid)
  {
    String prettyGuid = "";
    for (int i = 0; i < 4; i++)
    {
      String subGuid = Long.toString((guid >> ((3 - i)*16)) & 0xFFFF, 16);
      while (subGuid.length() < 4)
        subGuid = "0" + subGuid;
      if (i != 0)
        prettyGuid += "-";
      prettyGuid += subGuid;
    }
    return prettyGuid.toUpperCase();
  }

  public void kill()
  {
    alive = false;
  }

  public void run()
  {
    try{Thread.sleep(10000);}catch(Exception e){}
    if (sage.Catbert.ENABLE_LOCATOR_API)
    {
      transferer = new LocatorTransferManager();
      Thread t = new Thread(transferer, "LocatorTransferer");
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();

      // See if we have any downloads that are already requested that need to go in the queue
      for (int i = 0; i < messages.length; i++)
      {
        if (messages[i].hasMedia() && !messages[i].isDownloadCompleted() && messages[i].isDownloadRequested())
        {
          addToDownloadQueue(messages[i]);
        }
      }
    }
    while (alive && sage.Sage.getBoolean("locator/enable_registration", true) && sage.Sage.getBoolean("enable_media_extender_server", true))
    {
      try
      {
        updateLocatorServer();
      }
      catch (Exception e)
      {
        if (sage.Sage.DBG) System.out.println("ERROR updating locator system of:" + e);
        e.printStackTrace();
      }
      // 30 minutes between updates default, min of 10 minutes
      synchronized (lock)
      {
        try
        {
          lock.wait(Math.max(10*60000, sage.Sage.getLong("locator/update_interval", 30*60000)));
        }catch(Exception e){}
      }
    }
  }

  private javax.crypto.SecretKey secretKey;
  private byte[] secretKeyBytes;
  private boolean alive;
  private Object lock = new Object();
  private LocatorTransferManager transferer;
  private String[] friendList;
  private LocatorMsg[] messages;

  // Old stuff
  private java.security.PublicKey pubKey;
  private java.security.PrivateKey privKey;
  private byte[] pubKeyBytes;

  private static class LocatorResult
  {
    public LocatorResult(int rv, Object data)
    {
      this.rv = rv;
      this.data = data;
    }
    public int rv;
    public Object data;
  }

  public static class FriendData
  {
    public FriendData(String handle, long avatarID)
    {
      this.handle = handle;
      this.avatarID = avatarID;
    }
    public String handle;
    public long avatarID;
  }
  private static long readLong(byte[] fullData, int currOffset)
  {
    int i1 = ((fullData[currOffset] & 0xFF) << 24) | ((fullData[currOffset + 1] & 0xFF) << 16) |
        ((fullData[currOffset + 2] & 0xFF) << 8) | (fullData[currOffset + 3] & 0xFF);
    int i2 = ((fullData[currOffset + 4] & 0xFF) << 24) | ((fullData[currOffset + 5] & 0xFF) << 16) |
        ((fullData[currOffset + 6] & 0xFF) << 8) | (fullData[currOffset + 7] & 0xFF);
    return ((long)i1 << 32) + (i2 & 0xFFFFFFFFL);
  }

  private static int readInt(byte[] fullData, int currOffset)
  {
    return ((fullData[currOffset] & 0xFF) << 24) | ((fullData[currOffset + 1] & 0xFF) << 16) |
        ((fullData[currOffset + 2] & 0xFF) << 8) | (fullData[currOffset + 3] & 0xFF);
  }

  private static int readShort(byte[] fullData, int currOffset)
  {
    return ((fullData[currOffset] & 0xFF) << 8) | (fullData[currOffset + 1] & 0xFF);
  }

  private static void writeLong(long value, byte[] fullData, int currOffset)
  {
    fullData[currOffset]     = (byte)((value >>> 56) & 0xFF);
    fullData[currOffset + 1] = (byte)((value >>> 48) & 0xFF);
    fullData[currOffset + 2] = (byte)((value >>> 40) & 0xFF);
    fullData[currOffset + 3] = (byte)((value >>> 32) & 0xFF);
    fullData[currOffset + 4] = (byte)((value >>> 24) & 0xFF);
    fullData[currOffset + 5] = (byte)((value >>> 16) & 0xFF);
    fullData[currOffset + 6] = (byte)((value >>> 8) & 0xFF);
    fullData[currOffset + 7] = (byte)(value & 0xFF);
  }

  private static void writeInt(int value, byte[] fullData, int currOffset)
  {
    fullData[currOffset]     = (byte)((value >>> 24) & 0xFF);
    fullData[currOffset + 1] = (byte)((value >>> 16) & 0xFF);
    fullData[currOffset + 2] = (byte)((value >>> 8) & 0xFF);
    fullData[currOffset + 3] = (byte)(value & 0xFF);
  }

  private static void write3ByteInt(int value, byte[] fullData, int currOffset)
  {
    fullData[currOffset]     = (byte)((value >>> 16) & 0xFF);
    fullData[currOffset + 1] = (byte)((value >>> 8) & 0xFF);
    fullData[currOffset + 2] = (byte)(value & 0xFF);
  }

  private static void writeShort(int value, byte[] fullData, int currOffset)
  {
    fullData[currOffset]     = (byte)((value >>> 8) & 0xFF);
    fullData[currOffset + 1] = (byte)(value & 0xFF);
  }

}
