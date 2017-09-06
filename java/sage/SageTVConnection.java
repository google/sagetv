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
package sage;

import java.util.Map.Entry;
import java.util.Set;

public class SageTVConnection implements Runnable, Wizard.XctSyncClient, Carny.ProfilingListener
{
  private static final int DOS_BYTE_LIMIT = 10000000;
  public static final int STVCS_TIMEOUT = 300000;
  private static final boolean TRANSLATE_DB_IDS = false;
  private static final byte[] OK_BYTES = "OK\r\n".getBytes();
  public SageTVConnection(java.net.Socket s) throws Throwable
  {
    alive = true;
    mySock = s;
    this.buf = new byte[4096];

    if (Sage.DBG) System.out.println("SageTV received connection from:" + mySock.toString());
    try
    {
      // Setup the IO part of the connection.
      setup();

      // Check the password for login of the pitcher.
      checkLogin();

      clientName = "/" + mySock.getInetAddress().getHostAddress() + ":" + mySock.getPort();

      // The first request is identification of client connection as one we're listening
      // on or notifying on
      if (!Sage.client)
        parseRequest();
    }
    catch (java.io.IOException e)
    {
      if (Sage.DBG) System.out.println("Connection lost from: " + mySock.toString() + " by:" + e);
      cleanup();
      throw e;
    }
    catch (Throwable t)
    {
      if (Sage.DBG) System.out.println("Individual server thread (not the server app) " +
          "terminated with uncaught exception:" + t);
      Sage.printStackTrace(t);
      cleanup();
      throw t;
    }
  }

  public static final int SERVER_NOTIFIER = 1; // Used for messaging
  public static final int CLIENT_LISTENER = 2; // Used for server sending data streams to client
  public int getLinkType() { return linkType; }

  // The NOTIFIER is the messaging channel between the client & server
  public void takeFormOfNotifier(SageTVConnection theListener) throws java.io.IOException
  {
    if (!Sage.client)
      throw new IllegalStateException("This is a client mode call.");
    outStream.write(("NOTIFIER /" + theListener.mySock.getLocalAddress().getHostAddress() + ":" + theListener.mySock.getLocalPort() + "\r\n").getBytes(Sage.BYTE_CHARSET));
    outStream.flush();
    linkType = SERVER_NOTIFIER;
    String tempString = readLineBytes(inStream);
    if (tempString == null || !"OK".equals(tempString))
    {
      throw new java.io.IOException("Bad response received from:" + mySock.toString() + " of " + tempString);
    }
  }

  // The LISTENER is the channel used by the server to send db/property/carny updates to the client
  public void takeFormOfListener() throws java.io.IOException
  {
    if (!Sage.client)
      throw new IllegalStateException("This is a client mode call.");
    outStream.write("LISTENER\r\n".getBytes(Sage.BYTE_CHARSET));
    outStream.flush();
    linkType = CLIENT_LISTENER;
    String tempString = readLineBytes(inStream);
    if (tempString == null || !"OK".equals(tempString))
    {
      throw new java.io.IOException("Bad response received from:" + mySock.toString() + " of " + tempString);
    }
    int numStartupSyncs = 6;
    // 4 for Carny, property & wiz

    SageTV.writeOutStateInfo(SageTV.SAGETV_CLIENT_CONNECTING, Sage.preferredServer, SageTV.SYNCING_PROPERTIES);
    // Take properties first, then prime the DB
    parseRequest();
    numStartupSyncs--;

    //		Sage.setSplashText("Priming Object DB...");
    //		Wizard.prime();

    SageTV.writeOutStateInfo(SageTV.SAGETV_CLIENT_CONNECTING, Sage.preferredServer, SageTV.DOWNLOADING_DB);
    try {
      Wizard.getInstance().setDBLoadState(true);
      long startDBTime = Sage.eventTime();
      for (int i = 0; i < numStartupSyncs; i++)
      {
        if (i == 1)
        {
          Wizard.getInstance().setDBLoadState(false);
          if (Sage.DBG) System.out.println("DB loadTime=" + ((Sage.eventTime() - startDBTime)/1000.0) + " sec");
          SageTV.writeOutStateInfo(SageTV.SAGETV_CLIENT_CONNECTING, Sage.preferredServer, SageTV.SYNCING_EPG_PROFILE);
        }
        parseRequest();
      }
    } finally {
      Wizard.getInstance().setDBLoadState(false);
    }
    SageTV.writeOutStateInfo(SageTV.SAGETV_CLIENT_CONNECTING, Sage.preferredServer, SageTV.FINALIZING_CONNECTION);

    // Ensure we have our DB objects synced up on NoShow
    Wizard.getInstance().refreshNoShow();

    // ensure any client created objects don't overlap with server object IDs
    //Wizard.getInstance().notifyOfID(Wizard.getInstance().getCurrWizID() + 1000000);
  }

  public void run()
  {
    try
    {
      boolean moreRequests = true;
      // Allow one socket timeout to occur.
      boolean lastTimedOut = false;
      while (moreRequests && alive)
      {
        try
        {
          moreRequests = parseRequest();
          lastTimedOut = false;
        }
        catch (java.net.SocketTimeoutException ste)
        {
          if (lastTimedOut)
            throw ste;
          lastTimedOut = true;
        }
      }
    }
    catch (java.io.IOException e)
    {
      System.out.println("Connection lost from: " + mySock + " by:" + e);
    }
    catch (Throwable t)
    {
      System.out.println("Individual server thread (not the server app) " +
          "terminated with uncaught exception:" + t);
      Sage.printStackTrace(t);
    }
    finally
    {
      cleanup();
      NetworkClient.communicationFailure(this);
    }
  }
  private static final String CIPHER_TYPE = "Blowfish"; // "Blowfish";
  private static final int CIPHER_KEY_SIZE = 128; // 128;
  // We're stopping this paranoia. We don't need to encrypt this to prevent reverse-engineering. Our protocol is too detailed to be of
  // use to others. And we're going to do an MD5SUM of the license key we send here as well so that's not reversible. It's true that someone
  // now could create a proxy that modifies that information and we may be creating other holes; but we definitely cannot encrypt the communication
  // between PC clients and embedded systems; so that would be open no matter what. BUT we do need to encrypt because of the database. If we don't then
  // it would be very easy to reverse our master key which would be a big problem. Since embedded does not encrypt it's DB this works out fine.
  public synchronized void setup() throws java.io.IOException
  {
    // The catcher sticks with the standard timeout, because it's receiving
    // the large chunks of data, so it should never see a delay.
    mySock.setSoTimeout(STVCS_TIMEOUT);
    mySock.setTcpNoDelay(true);
    underlyingOutStream = new java.io.BufferedOutputStream(mySock.getOutputStream());
    underlyingInStream = new java.io.BufferedInputStream(mySock.getInputStream());
    outStream = new MyDataOutput(underlyingOutStream);
    inStream = new MyDataInput(underlyingInStream);
  }

  // Throws an exception for invalid login.
  public synchronized void checkLogin() throws java.io.IOException
  {
    if (Sage.client)
    {
      // First determine CRYPTO state
      outStream.write("MODE\r\n".getBytes()); // 1 is crypto, 0 is not
      outStream.flush();
      String tempString = readLineBytes(inStream);
      if ("1".equals(tempString))
      {
        byte[] allcryptbits = (byte[])(Sage.q);
        byte[] bcryptbits = new byte[CIPHER_KEY_SIZE/8];
        System.arraycopy(allcryptbits, 0, bcryptbits, 0, CIPHER_KEY_SIZE/8);
        byte[] ivbits = new byte[8];
        System.arraycopy(allcryptbits, CIPHER_KEY_SIZE/8, ivbits, 0, 8);
        javax.crypto.Cipher encryptCipher, decryptCipher;
        try
        {
          javax.crypto.spec.SecretKeySpec skeySpec = new javax.crypto.spec.SecretKeySpec(bcryptbits, CIPHER_TYPE);
          encryptCipher = javax.crypto.Cipher.getInstance(CIPHER_TYPE + "/CFB8/NoPadding");
          encryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, skeySpec,
              new javax.crypto.spec.IvParameterSpec(ivbits));
          decryptCipher = javax.crypto.Cipher.getInstance(CIPHER_TYPE + "/CFB8/NoPadding");
          decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, skeySpec,
              new javax.crypto.spec.IvParameterSpec(ivbits));
        }
        catch (Exception e)
        {
          throw new java.io.IOException("ERROR NETWORKING SYSTEM:" + e);
        }
        underlyingOutStream = new javax.crypto.CipherOutputStream(underlyingOutStream, encryptCipher);
        underlyingInStream = new javax.crypto.CipherInputStream(underlyingInStream, decryptCipher);
      }
      else if (!"0".equals(tempString))
      {
        if (Sage.DBG) System.out.println("Invalid response to MODE setup request of: " + tempString);
        throw new java.io.IOException("VERSION_ERR"); // bad version match is why this would happen
      }
      String key;
      key = WarlockRipper.getEPGLicenseKey();
      if (key == null || key.length() == 0)
        key = "NOKEY";
      // Since we no longer encrypt the connection we should obfuscate the key w/ an MD5SUM
      key = SageTV.oneWayEncrypt(key);
      outStream.write((key + ' ' + Sage.getFileSystemIdentifier(Sage.WINDOWS_OS ? "C:\\" : "/") + ' ' + Version.VERSION + "\r\n").
          getBytes(Sage.BYTE_CHARSET));
      outStream.flush();
      tempString = readLineBytes(inStream);
      if (tempString == null)
      {
        throw new java.io.IOException("Connection terminated from:" + mySock.toString());
      }
      if ("BAD_LICENSE".equals(tempString))
      {
        throw new java.io.IOException(tempString);
      }
      if (tempString.startsWith("VERSION_ERR"))
      {
        throw new java.io.IOException(tempString);
      }
      if (!"OK".equals(tempString))
      {
        throw new java.io.IOException("Incorrect password received from:" + mySock.toString());
      }
    }
    else
    {
      String tempString = readLineBytes(inStream);
      if (tempString == null)
      {
        throw new java.io.IOException("Connection terminated from:" + mySock.toString());
      }
      if (!"MODE".equals(tempString))
      {
        outStream.write(("VERSION_ERR " + Version.VERSION + "\r\n").getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        throw new java.io.IOException("Client has the wrong version:" + mySock.toString());
      }
      // Don't use encrypted C/S connections at all anymore (they apparently got broke somewhere anyhow)
      outStream.write("0\r\n".getBytes(Sage.BYTE_CHARSET));
      outStream.flush();

      tempString = readLineBytes(inStream);
      if (tempString == null)
      {
        throw new java.io.IOException("Connection terminated from:" + mySock.toString());
      }
      int idx = tempString.indexOf(' ');
      if (idx == -1 || idx == 0 || idx == tempString.length() - 1)
      {
        throw new java.io.IOException("Incorrect password received from:" + mySock.toString());
      }
      int veridx = tempString.indexOf(' ', idx + 1);
      int clientMajVer=0, clientMinVer=0, clientMicVer=0;
      if (veridx != -1)
      {
        try
        {
          int dotIdx = tempString.indexOf('.', veridx + 1);
          clientMajVer = Integer.parseInt(tempString.substring(veridx + 1, dotIdx));
          int dotIdx2 = tempString.indexOf('.', dotIdx + 1);
          clientMinVer = Integer.parseInt(tempString.substring(dotIdx + 1, dotIdx2));
          int dotIdx3 = tempString.indexOf('.', dotIdx2 + 1);
          clientMicVer = Integer.parseInt(tempString.substring(dotIdx2 + 1, dotIdx3));
        }
        catch (Exception e)
        {
          if (Sage.DBG) System.out.println("Error parsing version string:" + e);
          outStream.write(("VERSION_ERR " + Version.VERSION + "\r\n").getBytes(Sage.BYTE_CHARSET));
          outStream.flush();
          throw new java.io.IOException("Client has the wrong version:" + mySock.toString());
        }
      }
      if (clientMajVer > Sage.CLIENT_COMPATIBLE_MAJOR_VERSION ||
          (clientMajVer == Sage.CLIENT_COMPATIBLE_MAJOR_VERSION &&
          (clientMinVer > Sage.CLIENT_COMPATIBLE_MINOR_VERSION ||
              (clientMinVer == Sage.CLIENT_COMPATIBLE_MINOR_VERSION &&
              clientMicVer >= Sage.CLIENT_COMPATIBLE_MICRO_VERSION))))
      {
        // Version check passed
      }
      else
      {
        outStream.write(("VERSION_ERR " + Version.VERSION + "\r\n").getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        throw new java.io.IOException("Client has the wrong version:" + mySock.toString());
      }
      // We used to verify that the client license key is valid here..but we don't need that anymore.
      clientKey = tempString.substring(0, idx);
      String clientHw = tempString.substring(idx + 1, veridx);
      outStream.write(OK_BYTES);
      outStream.flush();
    }
  }

  private boolean parseRequest() throws java.io.IOException
  {
    // Clean out any pending responses.
    outStream.flush();

    String tempString = readLineBytes(inStream);
    //		if (Sage.DBG) System.out.println("Received command of:" + tempString + " from " +
    //			mySock.toString());
    if (tempString == null)
    {
      throw new java.io.IOException("Connection terminated from:" + mySock.toString());
    }
    return parseRequest(tempString);
  }

  private boolean parseRequest(String requestString) throws java.io.IOException
  {
    synchronized (this)
    {
      // Tokenize out the call.
      String[] myTokes = tokenizeCall(requestString);
      if (myTokes == null || myTokes.length == 0)
      {
        outStream.write(("ERROR unbalanced quotes in call:" + requestString + "\r\n").getBytes(Sage.BYTE_CHARSET));
        System.out.println("ERROR: Unbalanced quotes in command:" + requestString);
      }
      else if (myTokes[0].equals("NOOP"))
      {
        // needed for keep alive so timeouts don't kill us
        outStream.write(OK_BYTES);
      }
      else if (myTokes[0].equals("NOTIFIER"))
      {
        linkType = SERVER_NOTIFIER;
        // match up listener & notifier to a single client, they're on different ports
        // so we MUST do this
        clientName = myTokes[1];
        outStream.write(OK_BYTES);
      }
      else if (myTokes[0].equals("LISTENER"))
      {
        linkType = CLIENT_LISTENER;
        outStream.write(OK_BYTES);
      }
      else if (myTokes[0].equals("WIZARD_SYNC"))
      {
        // Client updating the server for a DB transaction...
        recvWizardSync(myTokes);
      }
      else if (myTokes[0].equals("WIZARD_SYNC2"))
      {
        // Client updating the server for a DB transaction...
        recvWizardSync2(myTokes);
      }
      else if (myTokes[0].equals("PROPERTY_SYNC"))
      {
        // property information update
        recvPropertySync(myTokes);
      }
      else if (myTokes[0].equals("CARNY_SYNC_LOVE"))
      {
        recvCarnySyncLove(myTokes);
      }
      else if (myTokes[0].equals("CARNY_SYNC_MUSTSEE"))
      {
        recvCarnySyncMustSees(myTokes);
      }
      else if (myTokes[0].equals("CARNY_SYNC_CAUSEMAP"))
      {
        recvCarnySyncCauseMap(myTokes);
      }
      else if (myTokes[0].equals("CARNY_SYNC_WPMAP"))
      {
        recvCarnySyncWPMap(myTokes);
      }
      // All of the below requests have been moved into the messaging system
      /*			else if (myTokes[0].equals("WATCH_LIVE"))
			{
				// takes an airing id
				recvWatchLive(myTokes);
			}
			else if (myTokes[0].equals("WATCH_FILE"))
			{
				// takes an airing id
				recvWatchFile(myTokes);
			}
			else if (myTokes[0].equals("WATCH_FINISH"))
			{
				recvWatchFinish(myTokes);
			}
			else if (myTokes[0].equals("CHANNEL_TUNE"))
			{
				recvForceChannelTune(myTokes);
			}
			else if (myTokes[0].equals("INACTIVE_FILE"))
			{
				recvInactiveFile(myTokes);
			}
			else if (myTokes[0].equals("VERIFY_FILE"))
			{
				recvVerifyFile(myTokes);
			}
			else if (myTokes[0].equals("REQUEST_UPLOAD_SPACE"))
			{
				recvRequestUploadSpace(myTokes);
			}
			else if (myTokes[0].equals("GET_PRETTYSCHEDULE"))
			{
				recvGetPrettySchedule(myTokes);
			}
			else if (myTokes[0].equals("SCHEDULE_CHANGED"))
			{
				recvScheduleChanged(myTokes);
			}
			else if (myTokes[0].equals("HOOK"))
			{
				recvHook(myTokes);
			}
			else if (myTokes[0].equals("ACTION"))
			{
				recvAction(myTokes);
			}
			else if (myTokes[0].equals("RECORD"))
			{
				recvRecord(myTokes);
			}
			else if (myTokes[0].equals("CANCEL_RECORD"))
			{
				recvCancelRecord(myTokes);
			}
			else if (myTokes[0].equals("TIMED_RECORD"))
			{
				recvTimedRecord(myTokes);
			}
			else if (myTokes[0].equals("SET_WATCH"))
			{
				recvSetWatch(myTokes);
			}
			else if (myTokes[0].equals("GET_CURRRECORDFILES"))
			{
				recvGetCurrRecordFiles(myTokes);
			}
			else if (myTokes[0].equals("GET_CURRRECORDFILEFORCLIENT"))
			{
				recvGetCurrRecordFileForClient(myTokes);
			}
       */			else
       {
         outStream.write(("ERROR unrecognized command:" + requestString + "\r\n").getBytes(Sage.BYTE_CHARSET));
         System.out.println("Invalid command received:" + requestString);
       }
      outStream.flush();
      return true;
    }
  }

  /*
   * Additional Client Requests to handle
   * clearWatch
   * setWatch
   * AddLove
   * RemoveLove
   * AddLovePrime
   * RemoveLovePrime
   * AddWasted
   * RemoveWasted
   * DeleteFile
   * MoveFileToLibrary
   * RemoveFavPriority
   * SetFavQuality
   * Record Add/Remove
   * TimedRecordAdd/Modify/Remove
   */

  synchronized void initializeListenerData() throws java.io.IOException
  {
    // This is what we do after we send the OK for LISTENER mode to the client
    java.util.Properties allProps = Sage.getAllPrefs();
    java.util.Properties xferProps = new java.util.Properties();
    java.util.Enumeration propWalker = allProps.propertyNames();
    String[] encRoots = MMC.getInstance().getEncoderPropertyRoots();
    while (propWalker.hasMoreElements())
    {
      String currKey = (String) propWalker.nextElement();
      for (int i = 0; i < SageProperties.CLIENT_XFER_PROPERTY_PREFIXES.length; i++)
      {
        if (currKey.startsWith(SageProperties.CLIENT_XFER_PROPERTY_PREFIXES[i]))
        {
          xferProps.put(currKey, allProps.getProperty(currKey));
          break;
        }
      }
      for (int i = 0; i < encRoots.length; i++)
      {
        if (currKey.startsWith(encRoots[i]))
        {
          xferProps.put(currKey, allProps.getProperty(currKey));
          break;
        }
      }
    }
    int numProps = xferProps.size();
    TimeoutHandler.registerTimeout(30000, mySock);
    outStream.write(("PROPERTY_SYNC " + numProps + "\r\n").getBytes(Sage.BYTE_CHARSET));
    propWalker = xferProps.propertyNames();
    while (propWalker.hasMoreElements())
    {
      String currKey = (String) propWalker.nextElement();
      try
      {
        outStream.writeUTF(currKey);
      }
      catch (java.io.UTFDataFormatException utf)
      {
        // Bad properties should not prevent us from maintaining a network connection
        outStream.writeUTF("noop");
      }
      try
      {
        outStream.writeUTF(xferProps.getProperty(currKey));
      }
      catch (java.io.UTFDataFormatException utf)
      {
        // Bad properties should not prevent us from maintaining a network connection
        outStream.writeUTF("noop");
      }
      //outStream.write(currKey.getBytes(Sage.CHARSET));
      //outStream.write("=".getBytes(Sage.CHARSET));
      //outStream.write(xferProps.getProperty(currKey).getBytes(Sage.CHARSET));
      //outStream.write("\r\n".getBytes(Sage.CHARSET));
    }
    outStream.flush();
    TimeoutHandler.clearTimeout(mySock);
    String tempString = readLineBytes(inStream);
    if (!"OK".equals(tempString))
    {
      throw new java.io.IOException("OK response not received, got:" + tempString);
    }

    outStream.write(("WIZARD_SYNC2 " + Integer.toString(Wizard.VERSION & 0xFF) + "\r\n").getBytes(Sage.BYTE_CHARSET));
    Wizard.getInstance().sendDBThroughStream(mySock, outStream, this);
    outStream.flush();
    tempString = readLineBytes(inStream);
    if (!"OK".equals(tempString))
    {
      throw new java.io.IOException("OK response not received, got:" + tempString);
    }

    Carny.getInstance().addCarnyListener(this);
    TimeoutHandler.registerTimeout(30000, mySock);
    if (!Carny.getInstance().fullClientUpdate(this))
      throw new java.io.IOException("ERROR occurred during sending Carny update to client, abort!");
    TimeoutHandler.clearTimeout(mySock);

    //scheduleChanged();
    spawnListenerQueueThread();
  }

  private void recvWizardSync(String[] myTokes) throws java.io.IOException
  {
    if (myTokes.length != 1)
    {
      outStream.write("ERROR need 1 tokens for WIZARD_SYNC command\r\n".getBytes(Sage.BYTE_CHARSET));
      System.out.println("ERROR need 1 tokens for WIZARD_SYNC command." +
          java.util.Arrays.asList(myTokes));
      return;
    }

    Wizard.getInstance().xctIn(inStream, Wizard.VERSION, TRANSLATE_DB_IDS ? dbIDMap : null);
    outStream.write(OK_BYTES);
  }

  private void recvWizardSync2(String[] myTokes) throws java.io.IOException
  {
    if (myTokes.length != 2)
    {
      outStream.write("ERROR need 2 tokens for WIZARD_SYNC2 command\r\n".getBytes(Sage.BYTE_CHARSET));
      System.out.println("ERROR need 2 tokens for WIZARD_SYNC2 command." +
          java.util.Arrays.asList(myTokes));
      return;
    }

    Wizard.VERSION = (byte)(Integer.parseInt(myTokes[1]) & 0xFF);
    Wizard.getInstance().xctIn(inStream, Wizard.VERSION, TRANSLATE_DB_IDS ? dbIDMap : null);
    outStream.write(OK_BYTES);
  }

  private void recvCarnySyncLove(String[] myTokes) throws java.io.IOException
  {
    if (myTokes.length != 2)
    {
      outStream.write("ERROR need 2 tokens for CARNY_SYNC_LOVE command\r\n".getBytes(Sage.BYTE_CHARSET));
      System.out.println("ERROR need 2 tokens for CARNY_SYNC_LOVE command." +
          java.util.Arrays.asList(myTokes));
      return;
    }
    Wizard wiz = Wizard.getInstance();
    int numLoves = Integer.parseInt(myTokes[1]);
    java.util.Set loveSet = new java.util.HashSet();
    for (int i = 0; i < numLoves; i++)
    {
      Airing theAir = wiz.getAiringForID(readDBID());
      if (theAir != null)
        loveSet.add(theAir);
    }
    Carny.getInstance().updateLoves(loveSet);
    outStream.write(OK_BYTES);
  }

  private void recvCarnySyncMustSees(String[] myTokes) throws java.io.IOException
  {
    if (myTokes.length != 2)
    {
      outStream.write("ERROR need 2 tokens for CARNY_SYNC_MUSTSEE command\r\n".getBytes(Sage.BYTE_CHARSET));
      System.out.println("ERROR need 2 tokens for CARNY_SYNC_MUSTSEE command." +
          java.util.Arrays.asList(myTokes));
      return;
    }
    Wizard wiz = Wizard.getInstance();
    int numLoves = Integer.parseInt(myTokes[1]);
    java.util.Set loveSet = new java.util.HashSet();
    for (int i = 0; i < numLoves; i++)
    {
      Airing theAir = wiz.getAiringForID(readDBID());
      if (theAir != null)
        loveSet.add(theAir);
    }

    Carny.getInstance().updateMustSees(loveSet);
    outStream.write(OK_BYTES);
  }

  private void recvCarnySyncCauseMap(String[] myTokes) throws java.io.IOException
  {
    if (myTokes.length != 2)
    {
      outStream.write("ERROR need 2 tokens for CARNY_SYNC_CAUSEMAP command\r\n".getBytes(Sage.BYTE_CHARSET));
      System.out.println("ERROR need 2 tokens for CARNY_SYNC_CAUSEMAP command." +
          java.util.Arrays.asList(myTokes));
      return;
    }
    Wizard wiz = Wizard.getInstance();
    int numLoves = Integer.parseInt(myTokes[1]);
    java.util.Map causeMap = new java.util.HashMap();
    for (int i = 0; i < numLoves; i++)
    {
      Airing theAir = wiz.getAiringForID(readDBID());
      Agent theAgent = wiz.getAgentForID(readDBID());
      if (theAir != null && theAgent != null)
        causeMap.put(theAir, theAgent);
    }

    Carny.getInstance().updateCauseMap(causeMap);
    outStream.write(OK_BYTES);
  }

  private void recvCarnySyncWPMap(String[] myTokes) throws java.io.IOException
  {
    if (myTokes.length != 2)
    {
      outStream.write("ERROR need 2 tokens for CARNY_SYNC_WPMAP command\r\n".getBytes(Sage.BYTE_CHARSET));
      System.out.println("ERROR need 2 tokens for CARNY_SYNC_WPMAP command." +
          java.util.Arrays.asList(myTokes));
      return;
    }
    Wizard wiz = Wizard.getInstance();
    int numLoves = Integer.parseInt(myTokes[1]);
    java.util.Map wpMap = new java.util.HashMap();
    for (int i = 0; i < numLoves; i++)
    {
      Airing theAir = wiz.getAiringForID(readDBID());
      Float wp = new Float(inStream.readFloat());
      if (theAir != null)
        wpMap.put(theAir, wp);
    }

    Carny.getInstance().updateWPMap(wpMap);
    outStream.write(OK_BYTES);
  }

  private void recvPropertySync(String[] myTokes) throws java.io.IOException
  {
    if (myTokes.length != 2)
    {
      outStream.write("ERROR need 2 tokens for PROPERTY_SYNC command\r\n".getBytes(Sage.BYTE_CHARSET));
      System.out.println("ERROR need 2 tokens for PROPERTY_SYNC command." +
          java.util.Arrays.asList(myTokes));
      return;
    }

    int numProps = Integer.parseInt(myTokes[1]);
    boolean rebuildEPGDS = false;
    boolean rebuildEPGLineups = false;
    boolean rebuildMMC = false;
    while (numProps > 0)
    {
      String name = inStream.readUTF();
      String val = inStream.readUTF();
			Sage.put(name, val);
      if (name.startsWith("epg/channel_lineups") ||
          name.startsWith("epg/service_levels") ||
          name.startsWith("epg/lineup_overrides") ||
          name.startsWith("epg/physical_channel_lineups") ||
          name.startsWith("epg/lineup_physical_overrides"))
      {
        rebuildEPGLineups = true;
      }
      else if (name.startsWith("epg_data_sources"))
        rebuildEPGDS = true;
      else if (name.startsWith("mmc/encoders"))
        rebuildMMC = true;
      numProps--;
    }
    if (rebuildMMC)
    {
      CaptureDeviceManager[] cdms = MMC.getInstance().getCaptureDeviceManagers();
      for (int i = 0; i < cdms.length; i++)
        if (cdms[i] instanceof ClientCaptureManager)
          ((ClientCaptureManager) cdms[i]).resyncToProperties(true);
    }
    if (rebuildEPGDS || rebuildEPGLineups)
      EPG.getInstance().resyncToProperties(rebuildEPGDS, rebuildEPGLineups);
    outStream.write(OK_BYTES);
  }

  private Msg recvWatchLive(Msg myMsg) throws java.io.IOException
  {
    int rv;
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    UIClient requestor = new RemoteUI(dis.readUTF());
    int airID = convertToLocalDBID(dis.readInt());
    Airing theAir = Wizard.getInstance().getAiringForID(airID);
    MediaFile watchFile = null;
    if (theAir != null)
    {
      int[] watchError = new int[1];
      watchFile = SeekerSelector.getInstance().requestWatch(theAir, watchError, requestor);
      if (watchFile != null)
        rv = 0;
      else
        rv = watchError[0];
    }
    else
      rv = VideoFrame.WATCH_FAILED_NULL_AIRING;
    byte[] data = new byte[8];
    writeIntBytes(rv, data, 0);
    writeIntBytes(watchFile == null ? 0 : convertToRemoteDBID(watchFile.getID()), data, 4);
    return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
  }

  private Msg recvWatchFile(Msg myMsg) throws java.io.IOException
  {
    int rv;
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    UIClient requestor = new RemoteUI(dis.readUTF());
    int fileID = convertToLocalDBID(dis.readInt());
    MediaFile mf = Wizard.getInstance().getFileForID(fileID);
    MediaFile watchFile = null;
    if (mf != null)
    {
      int[] watchError = new int[1];
      watchFile = SeekerSelector.getInstance().requestWatch(mf, watchError, requestor);
      if (watchFile != null)
        rv = 0;
      else
        rv = watchError[0];
    }
    else
      rv = VideoFrame.WATCH_FAILED_NULL_AIRING;
    byte[] data = new byte[8];
    writeIntBytes(rv, data, 0);
    writeIntBytes(watchFile == null ? 0 : convertToRemoteDBID(watchFile.getID()), data, 4);
    return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
  }

  private Msg recvWatchFinish(Msg myMsg) throws java.io.IOException
  {
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    UIClient requestor = new RemoteUI(dis.readUTF());
    SeekerSelector.getInstance().finishWatch(requestor);
    return new Msg(RESPONSE_MSG, myMsg.type, null, myMsg.id);
  }

  private Msg recvForceChannelTune(Msg myMsg) throws java.io.IOException
  {
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    UIClient requestor = new RemoteUI(dis.readUTF());
    String chanString = dis.readUTF();
    String mmcInputName = dis.readUTF();
    int rv = SeekerSelector.getInstance().forceChannelTune(mmcInputName, chanString, requestor);
    byte[] data = new byte[4];
    writeIntBytes(rv, data, 0);
    return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
  }

  private Msg recvVerifyFile(Msg myMsg) throws java.io.IOException
  {
    int fileID = convertToLocalDBID(getIntFromBytes(myMsg.data, 0));
    boolean strict = (myMsg.data[4] != 0);
    boolean fixDurs = (myMsg.data[5] != 0);
    MediaFile mf = Wizard.getInstance().getFileForID(fileID);
    boolean rv;
    if (mf != null)
    {
      if (mf.verifyFiles(strict, fixDurs))
        rv = true;
      else
        rv = false;
    }
    else
      rv = false;
    byte[] data = new byte[1];
    data[0] = (byte) (rv ? 1 : 0);
    return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
  }

  private Msg recvRequestUploadSpace(Msg myMsg) throws java.io.IOException
  {
    byte[] data = new byte[4];
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    long totalFileSize = dis.readLong();
    String filename = dis.readUTF();
    java.io.File destFile = new java.io.File(filename);

    if (totalFileSize == 0)
    {
      // This means to clear a request from before
      SageTV.getMediaServer().removeFromUploadList(destFile);
      return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
    }

    if (!destFile.isDirectory() && destFile.exists())
    {
      System.out.println("ERROR Target file already exists");
      return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
    }
    if (!destFile.exists())
    {
      if (destFile.getParentFile() != null)
        IOUtils.safemkdirs(destFile.getParentFile());
    }

    // Be sure we've got enough space for our new files
    long freeSpace = Sage.getDiskFreeSpace(destFile.getAbsolutePath());

    if (totalFileSize > freeSpace)
    {
      if (!Sage.WINDOWS_OS || SeekerSelector.getInstance().isPathInManagedStorage(destFile))
      {
        // Make a storage request with Seeker to get the space we need, and then see if we've got the space
        // now and then cancel the request.
        if (Sage.DBG) System.out.println("Requesting Seeker to clear up " + ((totalFileSize - freeSpace)/1000000) + "MB worth of space");
        java.io.File tempFile = SeekerSelector.getInstance().requestDirectoryStorage("scratch", totalFileSize - freeSpace);
        synchronized (SeekerSelector.getInstance())
        {
          SeekerSelector.getInstance().kick();
          try
          {
            SeekerSelector.getInstance().wait(5000);
          }catch (InterruptedException e){}
        }
        freeSpace = Sage.getDiskFreeSpace(destFile.getAbsolutePath());
        SeekerSelector.getInstance().clearDirectoryStorageRequest(tempFile);
        if (totalFileSize > freeSpace)
        {
          System.out.println("ERROR Unable to clear up enough free space for library import");
          return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
        }
      }
      else
      {
        System.out.println("ERROR Request to clear free space in unmanaged disk area");
        return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
      }
    }

    // Notify the Media Server that it should accept an upload to the specified file path
    int randy = (int)Math.round(java.lang.Math.random() * Integer.MAX_VALUE);
    MediaServer ms = SageTV.getMediaServer();
    if (ms != null)
      ms.addToUploadList(destFile, randy);

    writeIntBytes(randy, data, 0);
    return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
  }

  private Msg recvRequestMediaServerAccess(Msg myMsg) throws java.io.IOException
  {
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    boolean grant = dis.readBoolean();
    String fname = dis.readUTF();
    java.io.File theFile = new java.io.File(fname);
    boolean rv = true;
    if (grant)
      rv = SageTV.getMediaServer().addAuthorizedFile(theFile);
    else
      SageTV.getMediaServer().removeAuthorizedFile(theFile);
    byte[] data = new byte[1];
    data[0] = (byte) (rv ? 1 : 0);
    return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
  }

  private void recvInactiveFile(Msg myMsg) throws java.io.IOException
  {
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    String inactiveFilename = dis.readUTF();
    VideoFrame.inactiveFileAll(inactiveFilename);
  }

  private Msg recvGetPrettySchedule(Msg myMsg) throws java.io.IOException
  {
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    MyDataOutput dos = new MyDataOutput(baos);
    Object[] schedAirs = SeekerSelector.getInstance().getScheduledAirings();
    dos.writeInt(schedAirs.length);
    for (int i = 0; i < schedAirs.length; i++)
    {
      Object[] currSched = (Object[]) schedAirs[i];
      dos.writeUTF(currSched[0].toString());
      Airing[] theAirs = (Airing[]) currSched[1];
      dos.writeInt(theAirs.length);
      for (int j = 0; j < theAirs.length; j++)
        dos.writeInt(convertToRemoteDBID(theAirs[j].id));
    }
    return new Msg(RESPONSE_MSG, myMsg.type, baos.toByteArray(), myMsg.id);
  }

  private Msg recvGetCurrRecordFiles(Msg myMsg) throws java.io.IOException
  {
    MediaFile[] mfs = SeekerSelector.getInstance().getCurrRecordFiles();
    byte[] data = new byte[4*(mfs.length + 1)];
    int offset = 0;
    writeIntBytes(mfs.length, data, 0);
    offset += 4;
    for (int i = 0; i < mfs.length; i++)
    {
      writeIntBytes(convertToRemoteDBID(mfs[i].id), data, offset);
      offset += 4;
    }
    return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
  }

  private Msg recvGetCurrRecordFileForClient(Msg myMsg) throws java.io.IOException
  {
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    UIClient requestor = new RemoteUI(dis.readUTF());
    MediaFile mf = SeekerSelector.getInstance().getCurrRecordFileForClient(requestor);
    byte[] data = new byte[4];
    writeIntBytes((mf == null) ? 0 : mf.id, data, 0);
    return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
  }

  // This is a broadcast message, no response is needed
  private void recvScheduleChanged(Msg myMsg) throws java.io.IOException
  {
    SchedulerSelector.getInstance().setClientDontKnowFlag(myMsg.data[0] != 0);
    Catbert.distributeHookToLocalUIs("RecordingScheduleChanged", null);

    // Not the most efficient way in the world, but much easier than the other options
    java.awt.EventQueue.invokeLater(new Runnable()
      {
        public void run()
        {
          VideoFrame.kickAll();
        }
      });
  }

  // This is a broadcast message, no response is needed
  private void recvRestart(Msg myMsg) throws java.io.IOException
  {
    SageTV.restart();
  }

  private void recvEvent(Msg myMsg) throws java.io.IOException
  {
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    String eventName = dis.readUTF();
    int numArgs = dis.readInt();
    java.util.Map evtArgs = null;
    if (numArgs > 0)
    {
      evtArgs = new java.util.HashMap();
      for (int i = 0; i < numArgs; i++)
      {
        String argKey = dis.readUTF();
        Object argData = readObjectFromStream(dis);
        evtArgs.put(argKey, argData);
      }
    }
    sage.plugin.PluginEventManager.getInstance().postEvent(eventName, evtArgs);
  }

  private Msg recvRecord(Msg myMsg) throws java.io.IOException
  {
    int rv;
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    UIClient requestor = new RemoteUI(dis.readUTF());
    int airID = convertToLocalDBID(dis.readInt());
    Airing theAir = Wizard.getInstance().getAiringForID(airID);
    if (theAir != null)
      rv = SeekerSelector.getInstance().record(theAir, requestor);
    else
      rv = VideoFrame.WATCH_FAILED_NULL_AIRING;
    byte[] data = new byte[4];
    writeIntBytes(rv, data, 0);
    return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
  }

  private Msg recvCancelRecord(Msg myMsg) throws java.io.IOException
  {
    boolean rv;
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    UIClient requestor = new RemoteUI(dis.readUTF());
    int airID = convertToLocalDBID(dis.readInt());
    Airing theAir = Wizard.getInstance().getAiringForID(airID);
    if (theAir != null)
    {
      SeekerSelector.getInstance().cancelRecord(theAir, requestor);
      rv = true;
    }
    else
      rv = false;
    byte[] data = new byte[1];
    data[0] = (byte)(rv ? 1 : 0);
    return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
  }

  private Msg recvTimedRecord(Msg myMsg) throws java.io.IOException
  {
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    UIClient requestor = new RemoteUI(dis.readUTF());
    int airID = convertToLocalDBID(dis.readInt());
    int recurCode = dis.readInt();
    int stationID = dis.readInt();
    long startTime = dis.readLong();
    long endTime = dis.readLong();
    Airing air = Wizard.getInstance().getAiringForID(airID);
    ManualRecord mr = (air == null) ? null : Wizard.getInstance().getManualRecord(air);
    int rv;
    if (mr != null)
    {
      rv = SeekerSelector.getInstance().modifyRecord(startTime-mr.startTime, endTime-mr.getEndTime(), mr, requestor);
    }
    else
    {
      rv = SeekerSelector.getInstance().timedRecord(startTime, endTime, stationID, recurCode, air, requestor);
    }
    byte[] data = new byte[4];
    writeIntBytes(rv, data, 0);
    return new Msg(RESPONSE_MSG, myMsg.type, data, myMsg.id);
  }

  // This stays in for V2 because its called internally also
  private Msg recvSetWatch(Msg myMsg) throws java.io.IOException
  {
    int offset = 0;
    boolean isOn = myMsg.data[0] != 0;
    offset++;
    int airID = convertToLocalDBID(getIntFromBytes(myMsg.data, offset));
    offset += 4;
    long airStart = getLongFromBytes(myMsg.data, offset);
    offset += 8;
    long airEnd = getLongFromBytes(myMsg.data, offset);
    offset += 8;
    long realStart = getLongFromBytes(myMsg.data, offset);
    offset += 8;
    long realEnd = getLongFromBytes(myMsg.data, offset);
    offset += 8;
    int titleNum = getIntFromBytes(myMsg.data, offset);
    Airing theAir = Wizard.getInstance().getAiringForID(airID);
    if (theAir != null)
    {
      if (isOn)
        BigBrother.setWatched(theAir, airStart, airEnd, realStart, realEnd, titleNum, false);
      else
      {
        BigBrother.clearWatched(theAir);
      }
    }
    return new Msg(RESPONSE_MSG, myMsg.type, null, myMsg.id);
  }

  private Msg recvAction(Msg myMsg) throws java.io.IOException
  {
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    String methodName = dis.readUTF();
    int numArgs = dis.readInt();
    Object[] args = new Object[numArgs];
    for (int i = 0; i < numArgs; i++)
    {
      args[i] = readObjectFromStream(dis);
      //if (Sage.DBG) System.out.println("ReadObjectFromStream=" + args[i]);
    }
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    MyDataOutput dos = new MyDataOutput(baos);
    try
    {
      String uiContext = null;
      if (Sage.client)
      {
        uiContext = Seeker.LOCAL_PROCESS_CLIENT;
      }
      // If this is being executed on the client by a server; then we can use our local UI context since that's the only valid one on a client
      Object rv = Catbert.evaluateAction(uiContext, methodName, args);
      dos.writeBoolean(false);
      writeObjectToStream(rv, dos);
    }
    catch (sage.jep.ParseException e)
    {
      System.out.println("Error processing networked method "+methodName+" with args "+ java.util.Arrays.asList(args)+" - "+e);
      e.printStackTrace(System.out);
      dos.writeBoolean(true);
      dos.writeUTF(e.toString());
    }
    return new Msg(RESPONSE_MSG, myMsg.type, baos.toByteArray(), myMsg.id);
  }


  private Msg recvHook(Msg myMsg) throws java.io.IOException
  {
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    String hookClientName = dis.readUTF();
    String methodName = dis.readUTF();
    int numArgs = dis.readInt();
    Object[] args = new Object[numArgs];
    for (int i = 0; i < numArgs; i++)
    {
      args[i] = readObjectFromStream(dis);
      //if (Sage.DBG) System.out.println("ReadObjectFromStream=" + args[i]);
    }
    Object rv = null;
    if (hookClientName.length() == 0)
      Catbert.distributeHookToLocalUIs(methodName, args);
    else
    {
      UIManager targetUI = UIManager.getLocalUIByName(hookClientName);
      if (targetUI != null)
      {
        rv = Catbert.processUISpecificHook(methodName, args, targetUI, false);
      }
    }
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    MyDataOutput dos = new MyDataOutput(baos);
    writeObjectToStream(rv, dos);
    return new Msg(RESPONSE_MSG, myMsg.type, baos.toByteArray(), myMsg.id);
  }

  private void recvClientCapabilities(Msg myMsg) throws java.io.IOException {
    MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(myMsg.data));
    int numCapabilities = dis.readInt();
    if (Sage.DBG) System.out.println("Recieving " + numCapabilities + " capabilties for client " + clientName);
    NetworkClient.clearClientCapabilities(clientName);
    while(numCapabilities-- > 0) {
      String key = dis.readUTF();
      String value = dis.readUTF();
      if (Sage.DBG) System.out.println("  " + key + " = " + value);
      NetworkClient.setClientCapability(clientName, key, value);
    }
  }

  /*
   * OBJECT SERIALIZATION/DESERIALIZATION
   */
  private static final int MAJOR_NULL = 0;
  private static final int MAJOR_PRIMITIVE = 1;
  private static final int MAJOR_JAVAOBJECT = 2;
  private static final int MAJOR_DBOBJECT = 3;
  private static final int MAJOR_PRIMITIVE_ARRAY = 101;
  private static final int MAJOR_JAVAOBJECT_ARRAY = 102;
  private static final int MAJOR_DBOBJECT_ARRAY = 103;
  private static final int MINOR_BOOL = 4;
  private static final int MINOR_CHAR = 5;
  private static final int MINOR_BYTE = 6;
  private static final int MINOR_SHORT = 7;
  private static final int MINOR_INT = 8;
  private static final int MINOR_LONG = 9;
  private static final int MINOR_FLOAT = 10;
  private static final int MINOR_DOUBLE = 11;
  private static final int MINOR_STRING = 12;
  private static final int MINOR_FILE = 13;
  private static final int MINOR_ALBUM = 14;
  private static final int MINOR_SYSMSG = 15;
  private static final int MINOR_PLUGINWRAPPER = 16;
  private Object readObjectFromStream(java.io.DataInput dataIn) throws java.io.IOException
  {
    byte majorType = dataIn.readByte();
    byte minorType = dataIn.readByte();
    if (majorType == MAJOR_NULL)
      return null;
    else if (majorType == MAJOR_JAVAOBJECT)
    {
      if (minorType == MINOR_ALBUM)
      {
        return Wizard.getInstance().getCachedAlbum(Wizard.getInstance().getTitleForID(dataIn.readInt()),
            Wizard.getInstance().getPersonForID(dataIn.readInt()));
      }
      else if (minorType == MINOR_PLUGINWRAPPER)
      {
        return sage.plugin.PluginWrapper.buildFromObjectStream(dataIn);
      }
      else
      {
        String s = dataIn.readUTF();
        Object rv = s;
        if (minorType == MINOR_FILE)
          rv = new java.io.File(IOUtils.convertPlatformPathChars(s));
        else if (minorType == MINOR_SYSMSG)
          rv = sage.msg.SystemMessage.buildMsgFromString(s);
        return rv;
      }
    }
    else if (majorType == MAJOR_JAVAOBJECT_ARRAY)
    {
      Object[] arr;
      int arrSize = dataIn.readInt();
      switch (minorType)
      {
        case MINOR_BOOL:
          Boolean[] ba = new Boolean[arrSize];
          for (int i = 0; i < arrSize; i++)
            ba[i] = new Boolean(dataIn.readBoolean());
          return ba;
        case MINOR_CHAR:
          Character[] a = new Character[arrSize];
          for (int i = 0; i < arrSize; i++)
            a[i] = new Character(dataIn.readChar());
          return a;
        case MINOR_BYTE:
          Byte[] b = new Byte[arrSize];
          for (int i = 0; i < arrSize; i++)
            b[i] = new Byte(dataIn.readByte());
          return b;
        case MINOR_SHORT:
          Short[] s = new Short[arrSize];
          for (int i = 0; i < arrSize; i++)
            s[i] = new Short(dataIn.readShort());
          return s;
        case MINOR_LONG:
          Long[] l = new Long[arrSize];
          for (int i = 0; i < arrSize; i++)
            l[i] = new Long(dataIn.readLong());
          return l;
        case MINOR_FLOAT:
          Float[] f = new Float[arrSize];
          for (int i = 0; i < arrSize; i++)
            f[i] = new Float(dataIn.readFloat());
          return f;
        case MINOR_DOUBLE:
          Double[] d = new Double[arrSize];
          for (int i = 0; i < arrSize; i++)
            d[i] = new Double(dataIn.readDouble());
          return d;
        case MINOR_INT:
          Integer[] x = new Integer[arrSize];
          for (int i = 0; i < arrSize; i++)
            x[i] = new Integer(dataIn.readInt());
          return x;
        case MINOR_FILE:
          arr = new java.io.File[arrSize];
          break;
        case MINOR_SYSMSG:
          arr = new sage.msg.SystemMessage[arrSize];
          break;
        case MINOR_STRING:
          arr = new String[arrSize];
          break;
        case MINOR_ALBUM:
          arr = new Album[arrSize];
          break;
        default:
          arr = new Object[arrSize];
          break;
      }
      for (int i = 0; i < arr.length; i++)
      {
        if (minorType == MINOR_ALBUM)
        {
          arr[i] = Wizard.getInstance().getCachedAlbum(Wizard.getInstance().getTitleForID(dataIn.readInt()),
              Wizard.getInstance().getPersonForID(dataIn.readInt()));
        }
        else if (minorType == MINOR_PLUGINWRAPPER)
        {
          arr[i] = sage.plugin.PluginWrapper.buildFromObjectStream(dataIn);
        }
        else
        {
          String s = dataIn.readUTF();
          Object rv = s;
          if (minorType == MINOR_FILE)
            rv = new java.io.File(IOUtils.convertPlatformPathChars(s));
          else if (minorType == MINOR_SYSMSG)
            rv = sage.msg.SystemMessage.buildMsgFromString(s);
          arr[i] = rv;
        }
      }
      return arr;
    }
    else if (majorType == MAJOR_PRIMITIVE)
    {
      switch (minorType)
      {
        case MINOR_BOOL:
          return Boolean.valueOf(dataIn.readBoolean());
        case MINOR_CHAR:
          return new Character(dataIn.readChar());
        case MINOR_BYTE:
          return new Byte(dataIn.readByte());
        case MINOR_SHORT:
          return new Short(dataIn.readByte());
        case MINOR_LONG:
          return new Long(dataIn.readLong());
        case MINOR_FLOAT:
          return new Float(dataIn.readFloat());
        case MINOR_DOUBLE:
          return new Double(dataIn.readDouble());
        default:
          return new Integer(dataIn.readInt());
      }
    }
    else if (majorType == MAJOR_PRIMITIVE_ARRAY)
    {
      int arrSize = dataIn.readInt();
      switch (minorType)
      {
        case MINOR_BOOL:
          boolean[] ba = new boolean[arrSize];
          for (int i = 0; i < arrSize; i++)
            ba[i] = dataIn.readBoolean();
          return ba;
        case MINOR_CHAR:
          char[] a = new char[arrSize];
          for (int i = 0; i < arrSize; i++)
            a[i] = dataIn.readChar();
          return a;
        case MINOR_BYTE:
          byte[] b = new byte[arrSize];
          for (int i = 0; i < arrSize; i++)
            b[i] = dataIn.readByte();
          return b;
        case MINOR_SHORT:
          short[] s = new short[arrSize];
          for (int i = 0; i < arrSize; i++)
            s[i] = dataIn.readShort();
          return s;
        case MINOR_LONG:
          long[] l = new long[arrSize];
          for (int i = 0; i < arrSize; i++)
            l[i] = dataIn.readLong();
          return l;
        case MINOR_FLOAT:
          float[] f = new float[arrSize];
          for (int i = 0; i < arrSize; i++)
            f[i] = dataIn.readFloat();
          return f;
        case MINOR_DOUBLE:
          double[] d = new double[arrSize];
          for (int i = 0; i < arrSize; i++)
            d[i] = dataIn.readDouble();
          return d;
        default:
          int[] x = new int[arrSize];
          for (int i = 0; i < arrSize; i++)
            x[i] = dataIn.readInt();
          return x;
      }
    }
    else if (majorType == MAJOR_DBOBJECT)
    {
      int dbid = readDBID(dataIn);
      switch (minorType)
      {
        case Wizard.AIRING_CODE:
          return Wizard.getInstance().getAiringForID(dbid);
        case Wizard.SHOW_CODE:
          return Wizard.getInstance().getShowForID(dbid);
        case Wizard.MEDIAFILE_CODE:
          return Wizard.getInstance().getFileForID(dbid);
        case Wizard.AGENT_CODE:
          return Wizard.getInstance().getAgentForID(dbid);
        case Wizard.PLAYLIST_CODE:
          return Wizard.getInstance().getPlaylistForID(dbid);
        case Wizard.CHANNEL_CODE:
          return Wizard.getInstance().getChannelForID(dbid);
        case Wizard.SERIESINFO_CODE:
          return Wizard.getInstance().getSeriesInfoForID(dbid);
        case Wizard.USERRECORD_CODE:
          return Wizard.getInstance().getUserRecordForID(dbid);
        case Wizard.PEOPLE_CODE:
          return Wizard.getInstance().getPersonForID(dbid);
        default:
          throw new IllegalArgumentException("INVALID MINOR TYPE IN DBOBJECT: " + minorType);
      }
    }
    else if (majorType == MAJOR_DBOBJECT_ARRAY)
    {
      int as = dataIn.readInt();
      Wizard wiz = Wizard.getInstance();
      DBObject[] rv = null;
      switch (minorType)
      {
        case Wizard.AIRING_CODE:
          rv = new Airing[as];
          for (int i = 0; i < as; i++)
            rv[i] = wiz.getAiringForID(readDBID(dataIn));
          return rv;
        case Wizard.SHOW_CODE:
          rv = new Show[as];
          for (int i = 0; i < as; i++)
            rv[i] = wiz.getShowForID(readDBID(dataIn));
          return rv;
        case Wizard.MEDIAFILE_CODE:
          rv = new MediaFile[as];
          for (int i = 0; i < as; i++) {
            int dbid = readDBID(dataIn);
            rv[i] = wiz.getFileForID(dbid);
          }
          return rv;
        case Wizard.AGENT_CODE:
          rv = new Agent[as];
          for (int i = 0; i < as; i++)
            rv[i] = wiz.getAgentForID(readDBID(dataIn));
          return rv;
        case Wizard.PLAYLIST_CODE:
          rv = new Playlist[as];
          for (int i = 0; i < as; i++)
            rv[i] = wiz.getPlaylistForID(readDBID(dataIn));
          return rv;
        case Wizard.CHANNEL_CODE:
          rv = new Channel[as];
          for (int i = 0; i < as; i++)
            rv[i] = wiz.getChannelForID(readDBID(dataIn));
          return rv;
        case Wizard.SERIESINFO_CODE:
          rv = new SeriesInfo[as];
          for (int i = 0; i < as; i++)
            rv[i] = wiz.getSeriesInfoForID(readDBID(dataIn));
          return rv;
        case Wizard.USERRECORD_CODE:
          rv = new UserRecord[as];
          for (int i = 0; i < as; i++)
            rv[i] = wiz.getUserRecordForID(readDBID(dataIn));
          return rv;
        case Wizard.PEOPLE_CODE:
          rv = new Person[as];
          for (int i = 0; i < as; i++)
            rv[i] = wiz.getPersonForID(readDBID(dataIn));
          return rv;
        default:
          if (as > 0)
            throw new IllegalArgumentException("INVALID MINOR TYPE IN DBOBJECT: " + minorType);
          else
            return new DBObject[0];
      }
    }
    else
      throw new IllegalArgumentException("INVALID MAJOR TYPE IN OBJECT: " + majorType);
  }

    private void writeObjectToStream(Object obj, java.io.DataOutput dataOut) throws java.io.IOException
  {
    if (obj == null)
    {
      dataOut.writeByte(MAJOR_NULL);
      dataOut.writeByte(MAJOR_NULL);
    }
    else if (obj instanceof DBObject)
    {
      dataOut.writeByte(MAJOR_DBOBJECT);
      // This fixes a bug we found where MediaFile objects that don't have valid Airings were being
      // passed to the server via their Airing object which the server could not resolve...if we send the MediaFile
      // object instead; the API will resolve it properly as needed to the right object type and the server will understand
      // the id of the MediaFile object itself.
      if (obj instanceof MediaFile.FakeAiring)
        obj = ((MediaFile.FakeAiring)obj).getMediaFile();
      DBObject dbobj = (DBObject) obj;
      if (dbobj instanceof Airing)
        dataOut.writeByte(Wizard.AIRING_CODE);
      else if (dbobj instanceof Show)
        dataOut.writeByte(Wizard.SHOW_CODE);
			else if (dbobj instanceof MediaFile)
        dataOut.writeByte(Wizard.MEDIAFILE_CODE);
      else if (dbobj instanceof Agent)
        dataOut.writeByte(Wizard.AGENT_CODE);
      else if (dbobj instanceof Playlist)
        dataOut.writeByte(Wizard.PLAYLIST_CODE);
      else if (dbobj instanceof Channel)
        dataOut.writeByte(Wizard.CHANNEL_CODE);
      else if (dbobj instanceof SeriesInfo)
        dataOut.writeByte(Wizard.SERIESINFO_CODE);
      else if (dbobj instanceof UserRecord)
        dataOut.writeByte(Wizard.USERRECORD_CODE);
      else if (dbobj instanceof Person)
        dataOut.writeByte(Wizard.PEOPLE_CODE);
      else
        throw new IllegalArgumentException("UNSUPPORTED OBJECT TYPE FOR REMOTE ACTION CALL: " + obj);
      dataOut.writeInt(convertToRemoteDBID(dbobj.id));
    }
    else if (obj.getClass().isArray() || obj instanceof java.util.Collection)
    {
      if (obj instanceof java.util.Collection)
        obj = ((java.util.Collection) obj).toArray();
      Class arc = obj.getClass().getComponentType();
      int al = java.lang.reflect.Array.getLength(obj);
      if (arc.isPrimitive())
      {
        dataOut.writeByte(MAJOR_PRIMITIVE_ARRAY);
        if (arc == Boolean.TYPE)
        {
          dataOut.writeByte(MINOR_BOOL);
          dataOut.writeInt(al);
          boolean[] ba = (boolean[]) obj;
          for (int i = 0; i < ba.length; i++)
            dataOut.writeBoolean(ba[i]);
        }
        else if (arc == Character.TYPE)
        {
          dataOut.writeByte(MINOR_CHAR);
          dataOut.writeInt(al);
          char[] a = (char[]) obj;
          for (int i = 0; i < a.length; i++)
            dataOut.writeChar(a[i]);
        }
        else if (arc == Byte.TYPE)
        {
          dataOut.writeByte(MINOR_BYTE);
          dataOut.writeInt(al);
          byte[] a = (byte[]) obj;
          for (int i = 0; i < a.length; i++)
            dataOut.writeByte(a[i]);
        }
        else if (arc == Short.TYPE)
        {
          dataOut.writeByte(MINOR_SHORT);
          dataOut.writeInt(al);
          short[] a = (short[]) obj;
          for (int i = 0; i < a.length; i++)
            dataOut.writeShort(a[i]);
        }
        else if (arc == Long.TYPE)
        {
          dataOut.writeByte(MINOR_LONG);
          dataOut.writeInt(al);
          long[] a = (long[]) obj;
          for (int i = 0; i < a.length; i++)
            dataOut.writeLong(a[i]);
        }
        else if (arc == Float.TYPE)
        {
          dataOut.writeByte(MINOR_FLOAT);
          dataOut.writeInt(al);
          float[] a = (float[]) obj;
          for (int i = 0; i < a.length; i++)
            dataOut.writeFloat(a[i]);
        }
        else if (arc == Double.TYPE)
        {
          dataOut.writeByte(MINOR_DOUBLE);
          dataOut.writeInt(al);
          double[] a = (double[]) obj;
          for (int i = 0; i < a.length; i++)
            dataOut.writeDouble(a[i]);
        }
        else //if (arc == Integer.TYPE)
        {
          dataOut.writeByte(MINOR_INT);
          dataOut.writeInt(al);
          int[] a = (int[]) obj;
          for (int i = 0; i < a.length; i++)
            dataOut.writeInt(a[i]);
        }
        return;
      }
      else if (Number.class.isAssignableFrom(arc) || arc == Boolean.class || arc == Character.class)
      {
        dataOut.writeByte(MAJOR_JAVAOBJECT_ARRAY);
        Object[] a = (Object[]) obj;
        if (arc == Boolean.class)
        {
          dataOut.writeByte(MINOR_BOOL);
          dataOut.writeInt(al);
          for (int i = 0; i < a.length; i++)
            dataOut.writeBoolean(((Boolean)a[i]).booleanValue());
        }
        else if (arc == Character.class)
        {
          dataOut.writeByte(MINOR_CHAR);
          dataOut.writeInt(al);
          for (int i = 0; i < a.length; i++)
            dataOut.writeChar(((Character)a[i]).charValue());
        }
        else if (arc == Byte.class)
        {
          dataOut.writeByte(MINOR_BYTE);
          dataOut.writeInt(al);
          for (int i = 0; i < a.length; i++)
            dataOut.writeByte(((Byte)a[i]).byteValue());
        }
        else if (arc == Short.class)
        {
          dataOut.writeByte(MINOR_SHORT);
          dataOut.writeInt(al);
          for (int i = 0; i < a.length; i++)
            dataOut.writeShort(((Short)a[i]).shortValue());
        }
        else if (arc == Long.class)
        {
          dataOut.writeByte(MINOR_LONG);
          dataOut.writeInt(al);
          for (int i = 0; i < a.length; i++)
            dataOut.writeLong(((Long)a[i]).longValue());
        }
        else if (arc == Float.class)
        {
          dataOut.writeByte(MINOR_FLOAT);
          dataOut.writeInt(al);
          for (int i = 0; i < a.length; i++)
            dataOut.writeFloat(((Float)a[i]).floatValue());
        }
        else if (arc == Double.class)
        {
          dataOut.writeByte(MINOR_DOUBLE);
          dataOut.writeInt(al);
          for (int i = 0; i < a.length; i++)
            dataOut.writeDouble(((Double)a[i]).doubleValue());
        }
        else //if (arc == Integer.class)
        {
          dataOut.writeByte(MINOR_INT);
          dataOut.writeInt(al);
          for (int i = 0; i < a.length; i++)
            dataOut.writeInt(((Integer)a[i]).intValue());
        }
        return;
      }

      Object[] oa = (Object[]) obj;
      if (oa.length == 0)
      {
        if (oa instanceof DBObject[])
        {
          dataOut.writeByte(MAJOR_DBOBJECT_ARRAY);
          dataOut.writeByte(0);
          dataOut.writeInt(0);
          return;
        }
        else
        {
          dataOut.writeByte(MAJOR_JAVAOBJECT_ARRAY);
          if (oa instanceof String[])
            dataOut.writeByte(MINOR_STRING);
          else if (oa instanceof java.io.File[])
            dataOut.writeByte(MINOR_FILE);
          else if (oa instanceof Album[])
            dataOut.writeByte(MINOR_ALBUM);
          else if (oa instanceof sage.msg.SystemMessage[])
            dataOut.writeByte(MINOR_SYSMSG);
          else if (oa instanceof sage.plugin.PluginWrapper[])
            dataOut.writeByte(MINOR_PLUGINWRAPPER);
          else
            dataOut.writeByte(0);
          dataOut.writeInt(0);
          return;
        }
      }
			if (oa[0] instanceof DBObject)
      {
        dataOut.writeByte(MAJOR_DBOBJECT_ARRAY);
        DBObject dbobj = (DBObject) oa[0];
        if (dbobj instanceof Airing)
          dataOut.writeByte(Wizard.AIRING_CODE);
        else if (dbobj instanceof Show)
          dataOut.writeByte(Wizard.SHOW_CODE);
				else if (dbobj instanceof MediaFile)
					dataOut.writeByte(Wizard.MEDIAFILE_CODE);
        else if (dbobj instanceof Agent)
          dataOut.writeByte(Wizard.AGENT_CODE);
        else if (dbobj instanceof Playlist)
          dataOut.writeByte(Wizard.PLAYLIST_CODE);
        else if (dbobj instanceof Channel)
          dataOut.writeByte(Wizard.CHANNEL_CODE);
        else if (dbobj instanceof SeriesInfo)
          dataOut.writeByte(Wizard.SERIESINFO_CODE);
        else if (dbobj instanceof UserRecord)
          dataOut.writeByte(Wizard.USERRECORD_CODE);
        else if (dbobj instanceof Person)
          dataOut.writeByte(Wizard.PEOPLE_CODE);
        else
          throw new IllegalArgumentException("UNSUPPORTED OBJECT TYPE FOR REMOTE ACTION CALL: " + dbobj);
        dataOut.writeInt(oa.length);
        for (int i = 0; i < oa.length; i++)
          dataOut.writeInt(convertToRemoteDBID(((DBObject)oa[i]).id));
      }
      else
      {
        dataOut.writeByte(MAJOR_JAVAOBJECT_ARRAY);
        if (oa[0] instanceof java.io.File)
          dataOut.writeByte(MINOR_FILE);
        else if (oa[0] instanceof Album)
          dataOut.writeByte(MINOR_ALBUM);
        else if (oa[0] instanceof sage.msg.SystemMessage)
          dataOut.writeByte(MINOR_SYSMSG);
        else if (oa[0] instanceof sage.plugin.PluginWrapper)
          dataOut.writeByte(MINOR_PLUGINWRAPPER);
        else
          dataOut.writeByte(MINOR_STRING);
        dataOut.writeInt(oa.length);
        for (int i = 0; i < oa.length; i++)
        {
          if (oa[i] instanceof Album)
          {
            Album alb = (Album) oa[i];
            DBObject str = alb.getTitleStringer();
            dataOut.writeInt(str == null ? 0 : convertToRemoteDBID(str.id));
            str = alb.getArtistObj();
            dataOut.writeInt(str == null ? 0 : convertToRemoteDBID(str.id));
            str = alb.getGenreStringer();
            dataOut.writeInt(str == null ? 0 : convertToRemoteDBID(str.id));
            str = alb.getYearStringer();
            dataOut.writeInt(str == null ? 0 : convertToRemoteDBID(str.id));
          }
          else if (oa[i] instanceof sage.plugin.PluginWrapper)
            ((sage.plugin.PluginWrapper) oa[i]).writeToObjectStream(dataOut);
          else if (oa[i] instanceof sage.msg.SystemMessage)
            dataOut.writeUTF(((sage.msg.SystemMessage) oa[i]).getPersistentString());
          else if (oa[i] instanceof Widget)
            dataOut.writeUTF(((Widget) oa[i]).symbol());
          else
            dataOut.writeUTF(oa[i].toString());
        }
      }
    }
    else if (obj instanceof java.io.File)
    {
      dataOut.writeByte(MAJOR_JAVAOBJECT);
      dataOut.writeByte(MINOR_FILE);
      dataOut.writeUTF(obj.toString());
    }
    else if (obj instanceof sage.msg.SystemMessage)
    {
      dataOut.writeByte(MAJOR_JAVAOBJECT);
      dataOut.writeByte(MINOR_SYSMSG);
      dataOut.writeUTF(((sage.msg.SystemMessage) obj).getPersistentString());
    }
    else if (obj instanceof sage.plugin.PluginWrapper)
    {
      dataOut.writeByte(MAJOR_JAVAOBJECT);
      dataOut.writeByte(MINOR_PLUGINWRAPPER);
      ((sage.plugin.PluginWrapper) obj).writeToObjectStream(dataOut);
    }
    else if (obj instanceof Album)
    {
      dataOut.writeByte(MAJOR_JAVAOBJECT);
      dataOut.writeByte(MINOR_ALBUM);
      Album al = (Album) obj;
      DBObject str = al.getTitleStringer();
      dataOut.writeInt(str == null ? 0 : convertToRemoteDBID(str.id));
      str = al.getArtistObj();
      dataOut.writeInt(str == null ? 0 : convertToRemoteDBID(str.id));
      str = al.getGenreStringer();
      dataOut.writeInt(str == null ? 0 : convertToRemoteDBID(str.id));
      str = al.getYearStringer();
      dataOut.writeInt(str == null ? 0 : convertToRemoteDBID(str.id));
    }
    else if (obj instanceof Boolean)
    {
      dataOut.writeByte(MAJOR_PRIMITIVE);
      dataOut.writeByte(MINOR_BOOL);
      dataOut.writeBoolean(((Boolean)obj).booleanValue());
    }
    else if (obj instanceof Character)
    {
      dataOut.writeByte(MAJOR_PRIMITIVE);
      dataOut.writeByte(MINOR_CHAR);
      dataOut.writeChar(((Character)obj).charValue());
    }
    else if (obj instanceof Byte)
    {
      dataOut.writeByte(MAJOR_PRIMITIVE);
      dataOut.writeByte(MINOR_BYTE);
      dataOut.writeByte(((Byte)obj).byteValue());
    }
    else if (obj instanceof Short)
    {
      dataOut.writeByte(MAJOR_PRIMITIVE);
      dataOut.writeByte(MINOR_SHORT);
      dataOut.writeShort(((Short)obj).shortValue());
    }
    else if (obj instanceof Integer)
    {
      dataOut.writeByte(MAJOR_PRIMITIVE);
      dataOut.writeByte(MINOR_INT);
      dataOut.writeInt(((Integer)obj).intValue());
    }
    else if (obj instanceof Long)
    {
      dataOut.writeByte(MAJOR_PRIMITIVE);
      dataOut.writeByte(MINOR_LONG);
      dataOut.writeLong(((Long)obj).longValue());
    }
    else if (obj instanceof Float)
    {
      dataOut.writeByte(MAJOR_PRIMITIVE);
      dataOut.writeByte(MINOR_FLOAT);
      dataOut.writeFloat(((Float)obj).floatValue());
    }
    else if (obj instanceof Double)
    {
      dataOut.writeByte(MAJOR_PRIMITIVE);
      dataOut.writeByte(MINOR_DOUBLE);
      dataOut.writeDouble(((Double)obj).doubleValue());
    }
    else if (obj instanceof Widget)
    {
      dataOut.writeByte(MAJOR_JAVAOBJECT);
      dataOut.writeByte(MINOR_STRING);
      dataOut.writeUTF(((Widget) obj).symbol());
    }
    else
    {
      dataOut.writeByte(MAJOR_JAVAOBJECT);
      dataOut.writeByte(MINOR_STRING);
      dataOut.writeUTF(obj.toString());
    }
  }

  /*
   *
   * Message based calls - can go in either direction (but only one direction is logical sometimes)
   *
   */
  public Object requestAction(String methodName, Object[] args) throws sage.jep.ParseException {
    //if (Sage.DBG) System.out.println("Sending requestAction to server for " + methodName + " args=" + java.util.Arrays.asList(args));
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(methodName);
      dos.writeInt(args != null ? args.length : 0);
      for (int i = 0; (args != null) && i < args.length; i++)
      {
        writeObjectToStream(args[i], dos);
      }
      Msg response = postMessage(new Msg(REQUEST_MSG, ACTION, baos.toByteArray()));
      MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(response.data, 1, response.data.length - 1));
      if (response.data[0] == 0)
        return readObjectFromStream(dis);
      else
      {
        String err = dis.readUTF();
        if (Sage.DBG) System.out.println("Client requestAction failed, exception: " + err);
        throw new sage.jep.ParseException(err);
      }
    }
    catch (sage.jep.ParseException pe)
    {
      // This is OK. It's when there's an error in the evaluator on the server.
      throw pe;
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: requestAction: " + e);
        e.printStackTrace(System.out);
      }
      return null;
    }
  }

  public Object sendHook(UIClient targetUI, String methodName, Object[] args)
  {
    if (Sage.DBG) System.out.println("Sending hook to client for " + methodName + " args=" + (args == null ? null : java.util.Arrays.asList(args)));
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(targetUI != null ? targetUI.getLocalUIClientName() : "");
      dos.writeUTF(methodName);
      dos.writeInt(args != null ? args.length : 0);
      for (int i = 0; (args != null) && i < args.length; i++)
      {
        writeObjectToStream(args[i], dos);
      }
      Msg response = postMessage(new Msg(REQUEST_MSG, HOOK, baos.toByteArray()));
      MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(response.data));
      return readObjectFromStream(dis);
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: sendHook: " + e);
        e.printStackTrace(System.out);
      }
      return null;
    }
  }

  public void sendEvent(String eventName, java.util.Map eventArgs)
  {
    if (Sage.DBG) System.out.println("Sending event to server for " + eventName + " args=" + eventArgs);
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(eventName);
      dos.writeInt(eventArgs != null ? eventArgs.size() : 0);
      if (eventArgs != null)
      {
        java.util.Iterator walker = eventArgs.entrySet().iterator();
        while (walker.hasNext())
        {
          java.util.Map.Entry currEnt = (java.util.Map.Entry) walker.next();
          dos.writeUTF(currEnt.getKey().toString());
          writeObjectToStream(currEnt.getValue(), dos);
        }
      }
      postMessage(new Msg(BROADCAST_MSG, EVENT, baos.toByteArray()));
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: sendEvent: " + e);
        e.printStackTrace(System.out);
      }
    }
  }

  public void sendRestart()
  {
    if (Sage.DBG) System.out.println("Sending restart command to client at: " + clientName);
    try
    {
      postMessage(new Msg(BROADCAST_MSG, RESTART, null));
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: sendRestart: " + e);
        e.printStackTrace(System.out);
      }
    }
  }

  public int sendCapabilties(UIClient client, Set<Entry<String, String>> set) {
    if (Sage.DBG) System.out.println("Sending " + set.size() + " capabilties");
    if(set == null || set.size() == 0) {
      return 0;
    }
    try {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeInt(set.size());
      for(Entry<String, String> entry : set) {
        dos.writeUTF(entry.getKey());
        dos.writeUTF(entry.getValue());
      }
      postMessage(new Msg(BROADCAST_MSG, CLIENT_CAPABILITY, baos.toByteArray()));
    } catch( Exception e ) {
      if (Sage.DBG) {
        System.out.println("Sending capabilties " + set + " failed: " + e);
        e.printStackTrace(System.out);
      }
      return -1;
    }
    return 0;
  }

  public MediaFile requestWatch(UIClient requestor, Airing watchAir, int[] errorReturn)
  {
    if (Sage.DBG) System.out.println("Sending requestWatch to server for " + watchAir);
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(requestor.getLocalUIClientName());
      dos.writeInt(convertToRemoteDBID(watchAir.id));
      Msg replyMsg = postMessage(new Msg(REQUEST_MSG, WATCH_LIVE, baos.toByteArray()));
      errorReturn[0] = getIntFromBytes(replyMsg.data, 0);
      if (errorReturn[0] == 0)
        return Wizard.getInstance().getFileForID(getIntFromBytes(replyMsg.data, 4));
      else
        return null;
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: requestWatch: " + e);
        e.printStackTrace(System.out);
      }
      errorReturn[0] = VideoFrame.WATCH_FAILED_NETWORK_ERROR;
      return null;
    }
  }

  public int requestRecord(UIClient requestor, Airing watchAir)
  {
    if (Sage.DBG) System.out.println("Sending record to server for " + watchAir);
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(requestor.getLocalUIClientName());
      dos.writeInt(convertToRemoteDBID(watchAir.id));
      return getIntFromBytes(postMessage(new Msg(REQUEST_MSG, RECORD, baos.toByteArray())).data, 0);
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: requestRecord: " + e);
        e.printStackTrace(System.out);
      }
      return VideoFrame.WATCH_FAILED_NETWORK_ERROR;
    }
  }

  public void requestCancelRecord(UIClient requestor, Airing watchAir)
  {
    if (Sage.DBG) System.out.println("Sending cancel record to server for " + watchAir);
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(requestor.getLocalUIClientName());
      dos.writeInt(convertToRemoteDBID(watchAir.id));
      if (postMessage(new Msg(REQUEST_MSG, CANCEL_RECORD, baos.toByteArray())).data[0] == 0)
      {
        if (Sage.DBG) System.out.println("Client requestCancelRecord denied: " + watchAir);
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: requestCancelRecord: " + e);
        e.printStackTrace(System.out);
      }
    }
  }

  public MediaFile requestWatch(UIClient requestor, MediaFile watchFile, int[] errorReturn)
  {
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(requestor.getLocalUIClientName());
      dos.writeInt(convertToRemoteDBID(watchFile.id));
      Msg replyMsg = postMessage(new Msg(REQUEST_MSG, WATCH_FILE, baos.toByteArray()));
      errorReturn[0] = getIntFromBytes(replyMsg.data, 0);
      if(errorReturn[0] == VideoFrame.WATCH_OK)
        return Wizard.getInstance().getFileForID(getIntFromBytes(replyMsg.data, 4));
      else
        return null;
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm requestWatch:" + e);
        e.printStackTrace(System.out);
      }
      return null;
    }
  }

  public void finishWatch(UIClient requestor)
  {
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(requestor.getLocalUIClientName());
      postMessage(new Msg(REQUEST_MSG, WATCH_FINISH, baos.toByteArray()));
    }
    catch (Exception e)
    {
      System.out.println("Error with c/s comm: finishWatch: " + e);
      e.printStackTrace(System.out);
    }
  }

  public int requestModifyRecord(UIClient requestor, long startTimeModify, long endTimeModify, ManualRecord orgMR)
  {
    if (Sage.DBG) System.out.println("Sending modify record to server for " + orgMR);
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(requestor.getLocalUIClientName());
      dos.writeInt(convertToRemoteDBID(orgMR.getContentAiring().id));
      dos.writeInt(orgMR.recur);
      dos.writeInt(orgMR.stationID);
      dos.writeLong(startTimeModify+orgMR.getStartTime());
      dos.writeLong(endTimeModify+orgMR.getEndTime());
      return getIntFromBytes(postMessage(new Msg(REQUEST_MSG, TIMED_RECORD, baos.toByteArray())).data, 0);
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: requestModifyRecord: " + e);
        e.printStackTrace(System.out);
      }
      return VideoFrame.WATCH_FAILED_NETWORK_ERROR;
    }
  }

  public int requestTimedRecord(UIClient requestor, long startTime, long endTime, int stationID, int recurCode, Airing baseAir)
  {
    if (Sage.DBG) System.out.println("Sending timed record to server for " + baseAir);
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(requestor.getLocalUIClientName());
      dos.writeInt(baseAir == null ? 0 : convertToRemoteDBID(baseAir.id));
      dos.writeInt(recurCode);
      dos.writeInt(stationID);
      dos.writeLong(startTime);
      dos.writeLong(endTime);
      return getIntFromBytes(postMessage(new Msg(REQUEST_MSG, TIMED_RECORD, baos.toByteArray())).data, 0);
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: requestTimedRecord: " + e);
        e.printStackTrace(System.out);
      }
      return VideoFrame.WATCH_FAILED_NETWORK_ERROR;
    }
  }

  public int forceChannelTune(UIClient requestor, String mmcInputName, String chanString)
  {
    if (Sage.DBG) System.out.println("Sending forceChannelTune to server for " + mmcInputName + " chan=" + chanString);
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(requestor.getLocalUIClientName());
      dos.writeUTF(chanString);
      dos.writeUTF(mmcInputName);
      Msg response = postMessage(new Msg(REQUEST_MSG, CHANNEL_TUNE, baos.toByteArray()));
      return getIntFromBytes(response.data, 0);
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: forceChannelTune: " + e);
        e.printStackTrace(System.out);
      }
      return VideoFrame.WATCH_FAILED_NETWORK_ERROR;
    }
  }

  public boolean requestVerifyFile(MediaFile mf, boolean strict, boolean fixDurs)
  {
    try
    {
      byte[] data = new byte[6];
      writeIntBytes(convertToRemoteDBID(mf.id), data, 0);
      data[4] = (byte)(strict ? 1 : 0);
      data[5] = (byte)(fixDurs ? 1 : 0);
      Msg response = postMessage(new Msg(REQUEST_MSG, VERIFY_FILE, data));
      return response.data[0] != 0;
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: requestVerifyFile: " + e);
        e.printStackTrace(System.out);
      }
      return false;
    }
  }

  public int requestUploadSpace(java.io.File destFile, long diskSpace)
  {
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeLong(diskSpace);
      dos.writeUTF(destFile.toString());
      Msg response = postMessage(new Msg(REQUEST_MSG, REQUEST_UPLOAD, baos.toByteArray()));
      return getIntFromBytes(response.data, 0);
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: requestUploadSpace: " + e);
        e.printStackTrace(System.out);
      }
      return -1;
    }
  }

  public boolean requestMediaServerAccess(java.io.File theFile, boolean grantAccess)
  {
    if (Sage.DBG) System.out.println("Sending request to server for access to file:" + theFile);
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeBoolean(grantAccess);
      dos.writeUTF(theFile.toString());
      Msg response = postMessage(new Msg(REQUEST_MSG, REQUEST_MS_ACCESS, baos.toByteArray()));
      return response.data[0] != 0;
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: requestMediaServerAccess: " + e);
        e.printStackTrace(System.out);
      }
      return false;
    }
  }

  public boolean updateLoves(java.util.Set s)
  {
    try
    {
      if (!Sage.client)
      {
        if (listenerMsgShare != null && listenerMsgThread != Thread.currentThread())
        {
          // Put this on the async queue to be sent out
          addListenerMsg(new ListenerMsg(LOVE_SYNC_MSG, null, s, null, null));
          return true;
        }
      }
      synchronized (this)
      {
        // To protect against a ConcurrentModificationException against Carny.notifyAiringSwap
        synchronized (s)
        {
          outStream.write(("CARNY_SYNC_LOVE " + s.size() + "\r\n").getBytes(Sage.BYTE_CHARSET));
          java.util.Iterator w = s.iterator();
          while (w.hasNext())
          {
            outStream.writeInt(convertToRemoteDBID(((DBObject)w.next()).id));
          }
        }
        outStream.flush();
        String str = readLineBytes(inStream);
        if (!"OK".equals(str) && Sage.DBG)
          System.out.println("Client updateLoves denied:" + str);
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG)
      {
        System.out.println("Error with c/s comm: updateLoves: " + e);
        e.printStackTrace(System.out);
      }
      NetworkClient.communicationFailure(this);
      return false;
    }
    return true;
  }

  public boolean updateMustSees(java.util.Set s)
  {
    try
    {
      if (!Sage.client)
      {
        if (listenerMsgShare != null && listenerMsgThread != Thread.currentThread())
        {
          // Put this on the async queue to be sent out
          addListenerMsg(new ListenerMsg(MUST_SEE_SYNC_MSG, null, s, null, null));
          return true;
        }
      }
      synchronized (this)
      {
        // To protect against a ConcurrentModificationException against Carny.notifyAiringSwap
        synchronized (s)
        {
          outStream.write(("CARNY_SYNC_MUSTSEE " + s.size() + "\r\n").getBytes(Sage.BYTE_CHARSET));
          java.util.Iterator w = s.iterator();
          while (w.hasNext())
          {
            Airing a = (Airing) w.next();
            outStream.writeInt(convertToRemoteDBID(a.id));
          }
        }
        outStream.flush();
        String str = readLineBytes(inStream);
        if (!"OK".equals(str) && Sage.DBG)
          System.out.println("Client updateMustSees denied:" + str);
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG)
      {
        System.out.println("Error with c/s comm: updateMustSees: " + e);
        e.printStackTrace(System.out);
      }
      NetworkClient.communicationFailure(this);
      return false;
    }
    return true;
  }

  public boolean updateCauseMap(java.util.Map m)
  {
    try
    {
      if (!Sage.client)
      {
        if (listenerMsgShare != null && listenerMsgThread != Thread.currentThread())
        {
          // Put this on the async queue to be sent out
          addListenerMsg(new ListenerMsg(CAUSE_MAP_SYNC_MSG, null, null, m, null));
          return true;
        }
      }
      synchronized (this)
      {
        // To protect against a ConcurrentModificationException against Carny.notifyAiringSwap
        synchronized (m)
        {
          outStream.write(("CARNY_SYNC_CAUSEMAP " + m.size() + "\r\n").getBytes(Sage.BYTE_CHARSET));
          java.util.Iterator w = m.entrySet().iterator();
          while (w.hasNext())
          {
            java.util.Map.Entry ent = (java.util.Map.Entry) w.next();
            Airing a = (Airing) ent.getKey();
            Agent g = (Agent) ent.getValue();
            outStream.writeInt(convertToRemoteDBID(a.id));
            outStream.writeInt(convertToRemoteDBID(g.id));
          }
        }
        outStream.flush();
        String str = readLineBytes(inStream);
        if (!"OK".equals(str) && Sage.DBG)
          System.out.println("Client updateCauseMap denied:" + str);
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG)
      {
        System.out.println("Error with c/s comm: updateCauseMap: " + e);
        e.printStackTrace(System.out);
      }
      NetworkClient.communicationFailure(this);
      return false;
    }
    return true;
  }

  public boolean updateWPMap(java.util.Map m)
  {
    try
    {
      if (!Sage.client)
      {
        if (listenerMsgShare != null && listenerMsgThread != Thread.currentThread())
        {
          // Put this on the async queue to be sent out
          addListenerMsg(new ListenerMsg(WP_MAP_SYNC_MSG, null, null, m, null));
          return true;
        }
      }
      synchronized (this)
      {
        // To protect against a ConcurrentModificationException against Carny.notifyAiringSwap
        synchronized (m)
        {
          outStream.write(("CARNY_SYNC_WPMAP " + m.size() + "\r\n").getBytes(Sage.BYTE_CHARSET));
          java.util.Iterator w = m.entrySet().iterator();
          while (w.hasNext())
          {
            java.util.Map.Entry ent = (java.util.Map.Entry) w.next();
            Airing a = (Airing) ent.getKey();
            outStream.writeInt(convertToRemoteDBID(a.id));
            Float f = (Float) ent.getValue();
            outStream.writeFloat(f.floatValue());
          }
        }
        outStream.flush();
        String str = readLineBytes(inStream);
        if (!"OK".equals(str) && Sage.DBG)
          System.out.println("Client updateWPMap denied:" + str);
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG)
      {
        System.out.println("Error with c/s comm: updateWPMap: " + e);
        e.printStackTrace(System.out);
      }
      NetworkClient.communicationFailure(this);
      return false;
    }
    return true;
  }

  public void updateProperties(String[] propNames)
  {
    try
    {
      if (!Sage.client)
      {
        if (listenerMsgShare != null && listenerMsgThread != Thread.currentThread())
        {
          // Put this on the async queue to be sent out
          addListenerMsg(new ListenerMsg(PROPERTY_SYNC_MSG, null, null, null, propNames));
          return;
        }
      }
      synchronized (this)
      {
        outStream.write(("PROPERTY_SYNC " + propNames.length + "\r\n").getBytes(Sage.BYTE_CHARSET));
        for (int i = 0; i < propNames.length; i++)
        {
          try
          {
            outStream.writeUTF(propNames[i]);
          }
          catch (java.io.UTFDataFormatException utf)
          {
            // Bad properties should not prevent us from maintaining a network connection
            outStream.writeUTF("noop");
          }
          try
          {
            outStream.writeUTF(Sage.get(propNames[i], ""));
          }
          catch (java.io.UTFDataFormatException utf)
          {
            // Bad properties should not prevent us from maintaining a network connection
            outStream.writeUTF("noop");
          }
          //outStream.write((propNames[i] + "=" + Sage.get(propNames[i], "") + "\r\n").getBytes(Sage.CHARSET));
        }
        outStream.flush();
        String str = readLineBytes(inStream);
        if (!"OK".equals(str) && Sage.DBG)
          System.out.println("Client property sync denied:" + str);
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG)
      {
        System.out.println("Error with c/s comm: updateProperties: " + e);
        e.printStackTrace(System.out);
      }
      NetworkClient.communicationFailure(this);
    }
  }

  // For display purposes in the EPG
  public Object[] getScheduledAirings()
  {
    try
    {
      Msg response = postMessage(new Msg(REQUEST_MSG, GET_PRETTY_SCHEDULE, null));
      MyDataInput dis = new MyDataInput(new java.io.ByteArrayInputStream(response.data));
      int numScheds = dis.readInt();
      Object[] rv = new Object[numScheds];
      for (int i = 0; i < numScheds; i++)
      {
        String encName = dis.readUTF();
        int numAirs = dis.readInt();
        java.util.ArrayList theAirs = new java.util.ArrayList();
        for (int j = 0; j < numAirs; j++)
        {
          int airID = dis.readInt();
          Airing tempAir = Wizard.getInstance().getAiringForID(airID);
          if (tempAir != null)
            theAirs.add(tempAir);
        }
        rv[i] = new Object[] { encName, theAirs.toArray(Pooler.EMPTY_AIRING_ARRAY) };
      }
      return rv;
    }
    catch (Exception e)
    {
      if (Sage.DBG)
      {
        System.out.println("Error with c/s comm: getScheduledAirings: " + e);
        e.printStackTrace(System.out);
      }
      return Pooler.EMPTY_OBJECT_ARRAY;
    }
  }

  private boolean keepAlive()
  {
    try
    {
      synchronized (this)
      {
        outStream.write("NOOP\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        String str = readLineBytes(inStream); // OK
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG) {
        System.out.println("Error with c/s comm: keepAlive:" + e);
        e.printStackTrace(System.out);
      }
      NetworkClient.communicationFailure(this);
      return false;
    }
    return true;
  }

  public void cleanup()
  {
    if (Sage.DBG) System.out.println("Cleaning up c/s connection");
    try
    {
      if (underlyingOutStream != null)
        underlyingOutStream.close();
    }
    catch (java.io.IOException e)
    {}
    underlyingOutStream = null;
    try
    {
      if (underlyingInStream != null)
        underlyingInStream.close();
    }
    catch (java.io.IOException e)
    {}
    underlyingInStream = null;
    outStream = null;
    inStream = null;
    try
    {
      if (mySock != null)
        mySock.close();
    }
    catch (java.io.IOException e)
    {}
    mySock = null;
    alive = false;
  }

  // Reads a line from a binary stream, terminates on '\r\n' or '\n'.
  // Yeah, it should also terminate on \r itself, but the transfer
  // protocol this is used for requires \r\n termination anyways.
  public static String readLineBytes(java.io.DataInput inStream)
      throws java.io.InterruptedIOException, java.io.IOException {

    synchronized (inStream)
    {
      //byte[] readBuf = new byte[32];
      int numRead = 0;
      byte currByte = inStream.readByte();
      int readTillPanic = DOS_BYTE_LIMIT;
      StringBuffer result = new StringBuffer();
      while (true)
      {
        if (currByte == '\r')
        {
          currByte = inStream.readByte();
          if (currByte == '\n')
          {
            return result.toString();
            //result.append(new String(readBuf, 0, numRead, Sage.BYTE_CHARSET));
            //return result.toString();
          }
          //readBuf[numRead++] = (byte)'\r';
          result.append('\r');
          /*if (numRead == readBuf.length)
					{
						result.append(new String(readBuf, 0, numRead, Sage.BYTE_CHARSET));
						numRead = 0;
					}*/
        }
        else if (currByte == '\n')
        {
          return result.toString();
          //result.append(new String(readBuf, 0, numRead, Sage.BYTE_CHARSET));
          //return result.toString();
        }
        result.append((char)currByte);
        //readBuf[numRead++] = currByte;
        /*if (numRead == readBuf.length)
				{
					result.append(new String(readBuf, 0, numRead, Sage.BYTE_CHARSET));
					numRead = 0;
				}*/
        currByte = inStream.readByte();
        if (readTillPanic-- < 0)
        {
          throw new java.io.IOException("TOO MANY BYTES RECIEVED FOR A LINE, BAILING TO PREVENT DOS ATTACK");
        }
      }
    }
  }

  private int readDBID() throws java.io.IOException
  {
    return convertToLocalDBID(inStream.readInt());
  }
  private int readDBID(java.io.DataInput dataIn) throws java.io.IOException
  {
    return convertToLocalDBID(dataIn.readInt());
  }
  private int convertToLocalDBID(int remoteID)
  {
    if (!Sage.client || !TRANSLATE_DB_IDS) return remoteID;
    Integer id = new Integer(remoteID);
    Integer rv = (Integer) dbIDMap.get(id);
    if (rv == null)
    {
      rv = new Integer(Wizard.getInstance().getNextWizID());
      dbIDMap.put(id, rv);
    }
    return rv.intValue();
  }
  private int convertToRemoteDBID(int localID)
  {
    if (!Sage.client || !TRANSLATE_DB_IDS) return localID;
    boolean isNegative = (localID < 0) ? true : false;
    Integer id = new Integer(isNegative ? -localID : localID);
    Integer rv = (Integer) reversedDBIDMap.get(id);
    if (rv == null)
    {
      return 0;
    }
    return isNegative ? -rv.intValue() : rv.intValue();
  }

  public static String[] tokenizeCall(String str)
  {
    java.util.StringTokenizer toker = new java.util.StringTokenizer(str, " ");
    String[] rv = new String[toker.countTokens()];
    for (int i = 0; i < rv.length; i++)
      rv[i] = toker.nextToken();
    return rv;
  }

  public void xctOut(byte[] xctData) throws java.io.IOException
  {
    String tempString;
    if (!Sage.client)
    {
      if (listenerMsgShare != null && listenerMsgThread != Thread.currentThread())
      {
        // Put this on the async queue to be sent out
        addListenerMsg(new ListenerMsg(WIZARD_SYNC_MSG, xctData, null, null, null));
        return;
      }
    }
    synchronized (this)
    {
      TimeoutHandler.registerTimeout(30000, mySock);
      // We don't need to use SYNC2 here because the compactDB mode and DB version would have already been set in the initial connection
      outStream.write("WIZARD_SYNC\r\n".getBytes(Sage.BYTE_CHARSET));
      outStream.writeInt(xctData.length + 4);
      if (xctData.length > 0)
        outStream.write(xctData);
      outStream.writeInt(5);
      outStream.writeByte(Wizard.XCTS_DONE);
      outStream.flush();
      tempString = readLineBytes(inStream);
      TimeoutHandler.clearTimeout(mySock);
    }
    if (tempString == null || !"OK".equals(tempString))
    {
      NetworkClient.communicationFailure(this);
      throw new java.io.IOException("Bad response received from:" + mySock.toString() + " of " + tempString);
    }
  }

  public void inactiveFile(String inactiveFilename)
  {
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(inactiveFilename);
      // Narflex - 05/29/2012 - I changed this to be a broadcast message instead of a request message. I know there
      // was some reasoning I had as to why all clients should confirm receipt of this message before proceeding...but in the case
      // of a hung client, we don't want the server to hang until the timeout when this occurs.
      postMessage(new Msg(BROADCAST_MSG, INACTIVE_FILE, baos.toByteArray()));
    }
    catch (Exception e)
    {
      System.out.println("Error communicating with client-inactiveFilename:" + e);
      e.printStackTrace(System.out);
    }
  }

  public void scheduleChanged()
  {
    try
    {
      byte[] data = new byte[1];
      data[0] = (byte) (SchedulerSelector.getInstance().areThereDontKnows() ? 1 : 0);
      postMessage(new Msg(BROADCAST_MSG, SCHEDULE_CHANGED, data));
    }
    catch (Exception e)
    {
      System.out.println("Error communicating with client-scheduleChanged:" + e);
      e.printStackTrace(System.out);
    }
  }

  public void setWatch(boolean isOn, Airing air, long airStart, long airEnd, long realStart, long realEnd)
  {
    setWatch(isOn, air, airStart, airEnd, realStart, realEnd, 0);
  }
  public void setWatch(boolean isOn, Airing air, long airStart, long airEnd, long realStart, long realEnd, int titleNum)
  {
    try
    {
      byte[] myData = new byte[41];
      int offset = 0;
      myData[offset++] = isOn ? (byte)1 : (byte)0;
      writeIntBytes(convertToRemoteDBID(air.id), myData, offset);
      offset += 4;
      writeLongBytes(airStart, myData, offset);
      offset += 8;
      writeLongBytes(airEnd, myData, offset);
      offset += 8;
      writeLongBytes(realStart, myData, offset);
      offset += 8;
      writeLongBytes(realEnd, myData, offset);
      offset += 8;
      writeIntBytes(titleNum, myData, offset);
      Msg myMsg = new Msg(REQUEST_MSG, SET_WATCH, myData);
      postMessage(myMsg); // no data in response
    }
    catch (Exception e)
    {
      System.out.println("Error communicating with client-setWatch:" + e);
      e.printStackTrace(System.out);
    }
  }

  public MediaFile[] getCurrRecordFiles()
  {
    try
    {
      Msg myMsg = new Msg(REQUEST_MSG, GET_CURR_RECORD_FILES, null);
      Msg response = postMessage(myMsg);
      int numFiles = getIntFromBytes(response.data, 0);
      MediaFile[] rv = new MediaFile[numFiles];
      for (int i = 0; i < numFiles; i++)
        rv[i] = Wizard.getInstance().getFileForID(convertToLocalDBID(getIntFromBytes(response.data, (i+1)*4)));
      return rv;
    }
    catch (Exception e)
    {
      System.out.println("Error communicating with client-getCurrRecordFiles:" + e);
      e.printStackTrace(System.out);
      return new MediaFile[0];
    }
  }

  public MediaFile getCurrRecordFileForClient(UIClient requestor)
  {
    try
    {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      MyDataOutput dos = new MyDataOutput(baos);
      dos.writeUTF(requestor.getLocalUIClientName());
      Msg myMsg = new Msg(REQUEST_MSG, GET_CURR_RECORD_FILE_FOR_CLIENT, baos.toByteArray());
      Msg response = postMessage(myMsg);
      return Wizard.getInstance().getFileForID(convertToLocalDBID(getIntFromBytes(response.data, 0)));
    }
    catch (Exception e)
    {
      System.out.println("Error communicating with client-getCurrRecordFileForClient:" + e);
      e.printStackTrace(System.out);
      return null;
    }
  }

  public String getClientName() { return clientName; }
  // This is used if there was a problem matching up the names and we needed to reset the name
  public void setClientName(String x)
  {
    clientName = x;
  }

  void startupMessagingThreads()
  {
    spawnSendThread();
    spawnRecvThread();
  }

  protected Msg postMessage(Msg postMe)
  {
    Integer idObj = null;
    if (postMe.category == REQUEST_MSG)
    {
      // Do this before we send the message so the response can't come too soon
      synchronized (threadSuspendMap)
      {
        threadSuspendMap.put(idObj = new Integer(postMe.id), null);
      }
    }
    synchronized (sendQueue)
    {
      sendQueue.add(postMe);
      sendQueue.notifyAll();
    }
    if (postMe.category == REQUEST_MSG)
    {
      synchronized (threadSuspendMap)
      {
        while (outStream != null && threadSuspendMap.containsKey(idObj) && threadSuspendMap.get(idObj) == null && SageTV.isAlive())
        {
          try{threadSuspendMap.wait(1000);}catch(InterruptedException e){}
        }
      }
      return (Msg) threadSuspendMap.remove(idObj);
    }
    return null;
  }

  private boolean shouldRethread(Msg testMe)
  {
    return (testMe.category == REQUEST_MSG && testMe.type != CLOCK_SYNC);
  }

  protected void recvMessage(final Msg recvMe)
  {
    // First see if it's a fast or a slow message so we know if we need to rethread
    if (shouldRethread(recvMe))
    {
      Pooler.execute(new Runnable()
      {
        public void run()
        {
          recvMessageImpl(recvMe);
        }
      });
      return;
    }
    recvMessageImpl(recvMe);
  }

  private void recvMessageImpl(Msg recvMe)
  {
    // If it's a response then hand it off to the requesting thread
    if (recvMe.category == RESPONSE_MSG)
    {
      // Special message type
      if (recvMe.type == CLOCK_SYNC)
      {
        long newTime = getLongFromBytes(recvMe.data, 0);
        // Don't make adjustments of more than an hour
        // 7/12/03 Adjust it no matter what, too many people have clock problems
        //if (Math.abs(Sage.time() - sysTime) < Sage.MILLIS_PER_HR)
        {
          // only make adjustments if the clock's at least 100 msec off
          if (Math.abs(Sage.time() - newTime) >= 100)
          {
            if (Sage.DBG) System.out.println("Setting the system clock to be " + Sage.df(newTime));
            Sage.setSystemTime(newTime);
          }
        }
        return;
      }
      synchronized (threadSuspendMap)
      {
        threadSuspendMap.put(new Integer(recvMe.id), recvMe);
        threadSuspendMap.notifyAll();
      }
      return;
    }
    // do the work here
    Msg response = null;
    try
    {
      if (recvMe.category == REQUEST_MSG)
      {
        if (recvMe.type == CLOCK_SYNC)
        {
          byte[] timeData = new byte[8];
          writeLongBytes(Sage.time(), timeData, 0);
          response = new Msg(RESPONSE_MSG, CLOCK_SYNC, timeData, recvMe.id);
        }
        else
        {
          switch (recvMe.type)
          {
            case WATCH_LIVE:
              response = recvWatchLive(recvMe);
              break;
            case WATCH_FILE:
              response = recvWatchFile(recvMe);
              break;
            case WATCH_FINISH:
              response = recvWatchFinish(recvMe);
              break;
            case CHANNEL_TUNE:
              response = recvForceChannelTune(recvMe);
              break;
            case VERIFY_FILE:
              response = recvVerifyFile(recvMe);
              break;
            case REQUEST_UPLOAD:
              response = recvRequestUploadSpace(recvMe);
              break;
            case GET_PRETTY_SCHEDULE:
              response = recvGetPrettySchedule(recvMe);
              break;
            case GET_CURR_RECORD_FILES:
              response = recvGetCurrRecordFiles(recvMe);
              break;
            case GET_CURR_RECORD_FILE_FOR_CLIENT:
              response = recvGetCurrRecordFileForClient(recvMe);
              break;
            case RECORD:
              response = recvRecord(recvMe);
              break;
            case CANCEL_RECORD:
              response = recvCancelRecord(recvMe);
              break;
            case TIMED_RECORD:
              response = recvTimedRecord(recvMe);
              break;
            case SET_WATCH:
              response = recvSetWatch(recvMe);
              break;
            case ACTION:
              response = recvAction(recvMe);
              break;
            case HOOK:
              response = recvHook(recvMe);
              break;
            case REQUEST_MS_ACCESS:
              response = recvRequestMediaServerAccess(recvMe);
              break;
          }
        }
      }
      else
      {
        if (recvMe.type == SCHEDULE_CHANGED)
        {
          recvScheduleChanged(recvMe);
        }
        else if (recvMe.type == RESTART)
        {
          recvRestart(recvMe);
        }
        else if (recvMe.type == EVENT)
        {
          recvEvent(recvMe);
        }
        else if (recvMe.type == INACTIVE_FILE)
        {
          recvInactiveFile(recvMe);
        }
        else if (recvMe.type == CLIENT_CAPABILITY)
        {
          recvClientCapabilities(recvMe);
        }
      }
    }
    catch (Exception e)
    {
      System.out.println("ERROR in message processing: categ="+recvMe.category+" type="+recvMe.type+" - " + e);
      if (Sage.DBG) e.printStackTrace(System.out);
      // Be sure to unhang the response thread that called this by giving it something back!
      if (recvMe.category == REQUEST_MSG)
        response = new Msg(RESPONSE_MSG, recvMe.type, null, recvMe.id);
    }

    // Send the return message value
    if (recvMe.category == REQUEST_MSG && response != null)
    {
      postMessage(response);
    }
  }

  private static final int MSG_CONN_KEEP_ALIVE_TIME = 15000;

  private long lastClockSyncTime;
  private static final long CLOCK_SYNC_FREQ = 120000;
  void setupKeepAlive()
  {
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        while (true)
        {
          if (!keepAlive())
            break;

          try{Thread.sleep(5000); }catch (Exception e){}
        }
        NetworkClient.communicationFailure(SageTVConnection.this);
      }
    }, "KeepAlive", Thread.MAX_PRIORITY - 1); // don't make it low or we can lose connections on timeouts too easily
  }

  private void writeIntBytes(int value, byte[] data, int offset)
  {
    data[offset] = ((byte)((value >>> 24) & 0xFF));
    data[offset+1] = ((byte)((value >>> 16) & 0xFF));
    data[offset+2] = ((byte)((value >>> 8) & 0xFF));
    data[offset+3] = ((byte)(value & 0xFF));
  }

  private int getIntFromBytes(byte[] data, int offset)
  {
    int ch1 = data[offset]&0xFF;
    int ch2 = data[offset+1]&0xFF;
    int ch3 = data[offset+2]&0xFF;
    int ch4 = data[offset+3]&0xFF;
    return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
  }

  private long getLongFromBytes(byte[] data, int offset)
  {
    return ((long)(getIntFromBytes(data, offset)) << 32) + (getIntFromBytes(data, offset+4) & 0xFFFFFFFFL);
  }

  private void writeLongBytes(long value, byte[] data, int offset)
  {
    data[offset] = ((byte)((value >>> 56) & 0xFF));
    data[offset+1] = ((byte)((value >>> 48) & 0xFF));
    data[offset+2] = ((byte)((value >>> 40) & 0xFF));
    data[offset+3] = ((byte)((value >>> 32) & 0xFF));
    data[offset+4] = ((byte)((value >>> 24) & 0xFF));
    data[offset+5] = ((byte)((value >>> 16) & 0xFF));
    data[offset+6] = ((byte)((value >>> 8) & 0xFF));
    data[offset+7] = ((byte)((value) & 0xFF));
  }

  private static final String[] MSG_TYPE_NAMES =
    { "NOOP", "CLOCK_SYNC", "WATCH_LIVE", "WATCH_FILE", "WATCH_FINISH", "CHANNEL_TUNE", "VERIFY_FILE", "REQUEST_UPLOAD",
    "INACTIVE_FILE", "GET_PRETTY_SCHEDULE", "GET_CURR_RECORD_FILES", "GET_CURR_RECORD_FILE_FOR_CLIENT", "SCHEDULE_CHANGED",
    "RECORD", "CANCEL_RECORD", "TIMED_RECORD", "SET_WATCH", "ACTION", "HOOK", "REQUEST_MS_ACCESS", "RESTART", "EVENT" };
  // Messages
  private static final short NOOP = 0; // bcast: no data
  private static final short CLOCK_SYNC = 1; // req: no data, resp: long data
  private static final short WATCH_LIVE = 2; // req: String (clientUIName), int data, resp: int (status: 0 is OK, otherwise err code)
  private static final short WATCH_FILE = 3; // req: String (clientUIName), int data, resp: int (status: 0 is OK, otherwise err code)
  private static final short WATCH_FINISH = 4; // req: String (targetUI), resp: no data
  private static final short CHANNEL_TUNE = 5; // req: String (targetUI), String data x2 (first is channel, second is input), resp: int (status: 0 is OK, otherwise err code)
  private static final short VERIFY_FILE = 6; // req: int (file ID), bool (strict), bool (fix durs), resp: boolean
  private static final short REQUEST_UPLOAD = 7; // req: long (size), String (filename), resp: long (upload ID, or 0 if it failed)
  private static final short INACTIVE_FILE = 8; // req: String (filename), resp: no data
  // req: no data, resp: int (numtuners), then for each tuner a String for the encoder name and an int for the num airs then the ints for the airs
  private static final short GET_PRETTY_SCHEDULE = 9;
  private static final short GET_CURR_RECORD_FILES = 10; // req: no data, resp: int numFiles, then an int for each of those IDs
  private static final short GET_CURR_RECORD_FILE_FOR_CLIENT = 11; // req: String (targetUI), resp: int (file ID, or 0)
  private static final short SCHEDULE_CHANGED = 12; // req: boolean (dontKnowFlag), resp: no data
  private static final short RECORD = 13; // req: String (targetUI), int (airingID), resp: int (status: 0 is OK, otherwise err code)
  private static final short CANCEL_RECORD = 14; // req: String (targetUI), int (airingID), resp: bool (success/failure)
  private static final short TIMED_RECORD = 15; // req; String (targetUI), int (airingID), int (recurCode), int (stationID), long (start), long (end), resp:  int (status: 0 is OK, otherwise err code)
  private static final short SET_WATCH = 16; // req: bool (on/off), int (airingID), long (airStart), long (airEnd), long (realStart), long (realEnd), resp: no data
  private static final short ACTION = 17; // req: String (methName), int (numArgs), argData (variable), resp: boolean (isException), returnData (variable)
  private static final short HOOK = 18; // req: String (targetUI), String (hookName), int (numArgs), argData (variable), resp: returnData (variable)
  private static final short REQUEST_MS_ACCESS = 19; // req: boolean (grant), String (filename), resp: boolean
  private static final short RESTART = 20; // req: no data, resp: no data
  private static final short EVENT = 21; // req: String(eventName), int (numArgs), argKey (String), argValue (Object), resp: no data
  private static final short CLIENT_CAPABILITY = 24; // bcast: Short(numCapabilties), Pair<Key,Value>[numCapabilties]

  private void spawnSendThread()
  {
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        if (Sage.DBG) System.out.println("MsgSend thread spawned for " + clientName);
        long lastMsgSendTime = 0;
        boolean notifyQueue = false;
        try
        {
          while (alive)
          {
            Msg nextMsg = null;
            synchronized (sendQueue)
            {
              if (notifyQueue)
              {
                sendQueue.notifyAll();
                notifyQueue = false;
              }
              if (sendQueue.isEmpty())
              {
                long currWait = MSG_CONN_KEEP_ALIVE_TIME - (Sage.eventTime() - lastMsgSendTime);
                if (currWait <= 0)
                {
                  // Use time sync for the keep alive if that's the case
                  // NOTE: Don't sync the clock if the server is local!!
                  if (Sage.isTrueClient() && (Sage.eventTime() - lastClockSyncTime > CLOCK_SYNC_FREQ) &&
                      SageTV.getSyncSystemClock())
                  {
                    nextMsg = new Msg(REQUEST_MSG, CLOCK_SYNC, null);
                    lastClockSyncTime = Sage.eventTime();
                  }
                  else
                  {
                    // Create keep alive message
                    nextMsg = new Msg(BROADCAST_MSG, NOOP, null);
                  }
                }
                else
                {
                  try
                  {
                    sendQueue.wait(currWait);
                  }catch (InterruptedException e){}
                  continue;
                }
              }
              else
                nextMsg = (Msg) sendQueue.remove(0);
            }
            //						if (Sage.DBG) System.out.println("Sending:" + nextMsg);
            if (!Sage.client && listenerMsgShare != null)
            {
              // Make sure the client has their listener quanta up to date for us to send this message
              long checkpoint = SageTV.getGlobalQuanta();
              //System.out.println("Testing checkpoint before sending reply clientState=" + listenerMsgShare.quanta + " serverState=" + checkpoint);
              if (listenerMsgShare.quanta < checkpoint)
              {
                synchronized (listenerMsgShare.queue)
                {
                  int msgCount = 0;
                  while (listenerMsgShare.quanta < checkpoint && alive)
                  {
                    msgCount++;
                    if (Sage.DBG && (msgCount % 10) == 0) System.out.println("Waiting to send message reply to client until it reaches quanta: " + checkpoint + " curr=" +
                        listenerMsgShare.quanta + " queueSize=" + listenerMsgShare.queue.size());
                    try
                    {
                      listenerMsgShare.queue.wait(5000);
                    }
                    catch (InterruptedException e){}
                  }
                }
              }
            }
            // We've got the next message we need to send down the socket so do it!
            outStream.writeInt(nextMsg.id);
            outStream.writeShort(nextMsg.category);
            outStream.writeShort(nextMsg.type);
            if (nextMsg.data == null)
            {
              outStream.writeInt(0);
            }
            else
            {
              outStream.writeInt(nextMsg.data.length);
              outStream.write(nextMsg.data);
            }
            outStream.flush();
            lastMsgSendTime = Sage.eventTime();
            notifyQueue = true;
          }
        }
        catch (Exception e)
        {
          System.out.println("Error communicating with server:" + e);
          cleanup();
          NetworkClient.communicationFailure(SageTVConnection.this);
        }
        finally
        {
          if (Sage.DBG) System.out.println("MsgSend thread terminating for " + clientName);
        }
      }
    }, "ConnSendQueue");
  }

  private void spawnRecvThread()
  {
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        if (Sage.DBG) System.out.println("MsgRecv thread has spawned for " + clientName);
        try
        {
          while (alive)
          {
            int nextMsgID;
            try
            {
              nextMsgID = inStream.readInt();
            }
            catch (java.net.SocketTimeoutException ste)
            {
              // Give it one more try
              nextMsgID = inStream.readInt();
            }
            short msgCategory = inStream.readShort();
            short msgType = inStream.readShort();
            int dataLength = inStream.readInt();
            // For messages with a large amount of data the message is just used as a header for the data
            // which'll come down the channel next. We do NOT rethread on these types of messages since
            // the order of their retrieval matters.
            byte[] data = null;
            if (dataLength > 0)
            {
              data = new byte[dataLength];
              inStream.readFully(data);
            }
            Msg newMsg = new Msg(msgCategory, msgType, data, nextMsgID);
            //						if (Sage.DBG) System.out.println("MessageReceived:" + newMsg);
            recvMessage(newMsg);
          }
        }
        catch (Exception e)
        {
          System.out.println("Error communicating with server:" + e);
          cleanup();
          NetworkClient.communicationFailure(SageTVConnection.this);
        }
        finally
        {
          if (Sage.DBG) System.out.println("MsgRecv thread is terminating for " + clientName);
        }
      }
    }, "ConnRecvQueue");
  }

  private void addListenerMsg(ListenerMsg newMsg)
  {
    synchronized (listenerMsgShare.queue)
    {
      //System.out.println("Enqueing listener message of:" + newMsg);
      listenerMsgShare.queue.add(newMsg);
      listenerMsgShare.queue.notifyAll();
    }
  }

  void constructListenerQueue()
  {
    listenerMsgShare = new ParallelListenerSharedData();
    // Once listenerMsgShare is non-null than we'll start getting the listener messages so we
    // should set the quanta state at that time.
    listenerMsgShare.quanta = SageTV.getGlobalQuanta();
  }

  private void spawnListenerQueueThread()
  {
    Pooler.execute(new Runnable()
    {
      public void run()
      {
        if (Sage.DBG) System.out.println("Listener parallelizer thread has spawned for " + clientName);
        listenerMsgThread = Thread.currentThread();
        try
        {
          boolean didUpdate = false;
          while (alive)
          {
            ListenerMsg currMsg = null;
            synchronized (listenerMsgShare.queue)
            {
              if (didUpdate)
                listenerMsgShare.queue.notifyAll();
              if (listenerMsgShare.queue.isEmpty())
              {
                didUpdate = false;
                try
                {
                  listenerMsgShare.queue.wait(30000);
                }
                catch (Exception e){}
                continue;
              }
              currMsg = (ListenerMsg) listenerMsgShare.queue.remove(0);
            }
            didUpdate = true;
            /*						if (Math.random() < 0.2)
						{
							System.out.println("INTRODUCING ARTIFICAL DELAY!!!");
							try{Thread.sleep(5000);}catch (Exception e){}
						}*/
            //System.out.println("Processing listener message of:" + currMsg);
            switch (currMsg.type)
            {
              case WIZARD_SYNC_MSG:
                xctOut(currMsg.data);
                break;
              case PROPERTY_SYNC_MSG:
                updateProperties(currMsg.props);
                break;
              case LOVE_SYNC_MSG:
                updateLoves(currMsg.set);
                break;
              case MUST_SEE_SYNC_MSG:
                updateMustSees(currMsg.set);
                break;
              case CAUSE_MAP_SYNC_MSG:
                updateCauseMap(currMsg.map);
                break;
              case WP_MAP_SYNC_MSG:
                updateWPMap(currMsg.map);
                break;
            }
            listenerMsgShare.quanta = Math.max(listenerMsgShare.quanta, currMsg.safePoint);
            //System.out.println("Done processing listener message of:" + currMsg);
          }
        }
        catch (Exception e)
        {
          System.out.println("Error communicating with client:" + e);
          e.printStackTrace();
          cleanup();
          NetworkClient.communicationFailure(SageTVConnection.this);
        }
        finally
        {
          if (Sage.DBG) System.out.println("Listener parallelizer  thread is terminating for " + clientName);
        }

      }
    }, "ClientListenerParallelizer");
    // We need to wait until this is up...if we don't then we may hang waiting for the client to respond to
    // the property sync request that happens if there are any changes during connection init
    while (listenerMsgThread == null)
    {
      try{Thread.sleep(20);}catch(Exception e){}
    }
  }

  public java.net.Socket getSocket()
  {
    return mySock;
  }

  public void setListenerMsgShare(ParallelListenerSharedData inShare)
  {
    listenerMsgShare = inShare;
  }

  public ParallelListenerSharedData getListenerMsgShare()
  {
    return listenerMsgShare;
  }

  public String getClientID()
  {
    return null;
  }

  public int getPendingXctCount()
  {
    return (listenerMsgShare == null || listenerMsgShare.queue == null) ? 0 : listenerMsgShare.queue.size();
  }

  private final java.util.Vector sendQueue = new java.util.Vector();
  private final java.util.Map threadSuspendMap = new java.util.HashMap();
  private ParallelListenerSharedData listenerMsgShare;
  private Thread listenerMsgThread;

  private java.net.Socket mySock;
  private MyDataOutput outStream;
  private java.io.DataInput inStream;
  private java.io.OutputStream underlyingOutStream;
  private java.io.InputStream underlyingInStream;
  private byte[] buf; // since we synchronize everything it's OK to share this.
  private int linkType;
  private String clientName;
  private String clientKey;
  private boolean alive = false;
  // To enable ID translation between the client & server, create this object
  private static java.util.Map dbIDMap = new java.util.HashMap()
  {
    public Object put(Object key, Object value)
    {
      reversedDBIDMap.put(value, key);
      return super.put(key, value);
    }
  };
  private static java.util.Map reversedDBIDMap = new java.util.HashMap();
  private static java.util.Map licenseKeyHwidMap = java.util.Collections.synchronizedMap(new java.util.HashMap());

  private int getNewMsgID()
  {
    synchronized (msgCounterLock)
    {
      return msgIdCounter++;
    }
  }
  private int msgIdCounter = 1;
  private Object msgCounterLock = new Object();
  private static final short REQUEST_MSG = 1;
  private static final short RESPONSE_MSG = 2;
  private static final short BROADCAST_MSG = 3;
  private class Msg
  {
    public Msg(short inCat, short inType, byte[] inData)
    {
      this(inCat, inType, inData, getNewMsgID());
    }
    public Msg(short inCat, short inType, byte[] inData, int inID)
    {
      category = inCat;
      type = inType;
      data = inData;
      id = inID;
    }
    public int id;
    public byte[] data;
    public short category; // request, response, broadcast
    public short type; // specific command

    public String toString()
    {
      return "Msg[" + (category == REQUEST_MSG ? "Req" : (category == RESPONSE_MSG ? "Resp" : "Bcast")) + " " + MSG_TYPE_NAMES[type] +
          " id=" + id + " datalen=" + (data == null ? 0 : data.length) + "]";
    }
  }

  private static final int WIZARD_SYNC_MSG = 1;
  private static final int PROPERTY_SYNC_MSG = 2;
  private static final int LOVE_SYNC_MSG = 3;
  private static final int MUST_SEE_SYNC_MSG = 4;
  private static final int CAUSE_MAP_SYNC_MSG = 5;
  private static final int WP_MAP_SYNC_MSG = 6;
  private static final String[] LISTENER_MSG_NAMES = new String[] { "", "WIZARD_SYNC", "PROPERTY_SYNC", "LOVE_SYNC",
    "MUST_SEE_SYNC", "CAUSE_MAP_SYNC", "WP_MAP_SYNC"
  };
  private class ListenerMsg
  {
    public ListenerMsg(int inType, byte[] inData, java.util.Set inSet, java.util.Map inMap, String[] inProps)
    {
      type = inType;
      data = inData;
      set = inSet;
      map = inMap;
      props = inProps;
      safePoint = SageTV.getGlobalQuanta();
    }
    public String toString()
    {
      return "ListenerMsg[" + LISTENER_MSG_NAMES[type] + " safePoint=" + safePoint + "]";
    }
    public int type;
    public byte[] data;
    public java.util.Set set;
    public java.util.Map map;
    public String[] props;
    public long safePoint;
  }
  class ParallelListenerSharedData
  {
    public java.util.Vector queue = new java.util.Vector();
    public volatile long quanta;
  }

  public class RemoteUI implements UIClient
  {
    public RemoteUI(String uiName)
    {
      this.uiName = uiName;
    }

    public String getFullUIClientName()
    {
      return SageTVConnection.this.clientName + "@@" + uiName;
    }

    public String getLocalUIClientName()
    {
      return uiName;
    }

    public int getUIClientType()
    {
      return REMOTE_CLIENT;
    }

    public Object processUIClientHook(String hookName, Object[] hookVars)
    {
      return SageTVConnection.this.sendHook(this, hookName, hookVars);
    }

    public String getUIClientHostname()
    {
      return SageTVConnection.this.clientName;
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof RemoteUI)) return false;
      RemoteUI r = (RemoteUI) o;
      return getFullUIClientName().equals(r.getFullUIClientName());
    }

    public int hashCode()
    {
      return getFullUIClientName().hashCode();
    }

    public String toString() { return getFullUIClientName(); }

    private String uiName;

    public String getCapability(String capability) {
      return NetworkClient.getClientCapability(getClientName(), capability);
    }
  }

  //custom OutputStream to allow for greater than 64k writes with writeUTF
  public static class MyDataOutput extends java.io.OutputStream implements java.io.DataOutput
  {
    private java.io.OutputStream innerStream;
    public MyDataOutput(java.io.OutputStream innerStream) {
      this.innerStream = innerStream;
    }
    public void write(int b) throws java.io.IOException {
      innerStream.write(b);
    }
    public void write(byte b[]) throws java.io.IOException {
      write(b, 0, b.length);
    }
    public void write(byte b[], int off, int len) throws java.io.IOException
    {
      innerStream.write(b, off, len);
    }
    public void flush() throws java.io.IOException {
      innerStream.flush();
    }
    public final void writeBoolean(boolean v) throws java.io.IOException {
      innerStream.write(v ? 1 : 0);
    }
    public final void writeByte(int v) throws java.io.IOException {
      innerStream.write(v);
    }
    public final void writeShort(int v) throws java.io.IOException {
      innerStream.write((v >>> 8) & 0xFF);
      innerStream.write((v >>> 0) & 0xFF);
    }
    public final void writeChar(int v) throws java.io.IOException {
      innerStream.write((v >>> 8) & 0xFF);
      innerStream.write((v >>> 0) & 0xFF);
    }
    public final void writeInt(int v) throws java.io.IOException {
      writeBuffer[0] = (byte)(v >>> 24);
      writeBuffer[1] = (byte)(v >>> 16);
      writeBuffer[2] = (byte)(v >>>  8);
      writeBuffer[3] = (byte)(v >>>  0);
      innerStream.write(writeBuffer, 0, 4);
    }
    private byte writeBuffer[] = new byte[8];
    public final void writeLong(long v) throws java.io.IOException {
      writeBuffer[0] = (byte)(v >>> 56);
      writeBuffer[1] = (byte)(v >>> 48);
      writeBuffer[2] = (byte)(v >>> 40);
      writeBuffer[3] = (byte)(v >>> 32);
      writeBuffer[4] = (byte)(v >>> 24);
      writeBuffer[5] = (byte)(v >>> 16);
      writeBuffer[6] = (byte)(v >>>  8);
      writeBuffer[7] = (byte)(v >>>  0);
      innerStream.write(writeBuffer, 0, 8);
    }
    public final void writeFloat(float v) throws java.io.IOException {
      writeInt(Float.floatToIntBits(v));
    }
    public final void writeDouble(double v) throws java.io.IOException {
      writeLong(Double.doubleToLongBits(v));
    }
    public final void writeBytes(String s) throws java.io.IOException {
      int len = s.length();
      for (int i = 0 ; i < len ; i++) {
        innerStream.write((byte)s.charAt(i));
      }
    }
    public final void writeChars(String s) throws java.io.IOException {
      int len = s.length();
      for (int i = 0 ; i < len ; i++) {
        int v = s.charAt(i);
        innerStream.write((v >>> 8) & 0xFF);
        innerStream.write((v >>> 0) & 0xFF);
      }
    }
    public void writeUTF(String s) throws java.io.IOException
    {
      // Just in case a null gets in here; it's much better to write it out as an empty string than to simply crash with an exception
      if (s == null) s = "";
      int strlen = s.length();
      int utflen = 0;
      int c = 0;

      for (int i = 0; i < strlen; i++) {
        c = s.charAt(i);
        if ((c >= 0x0001) && (c <= 0x007F)) {
          utflen++;
        } else if (c > 0x07FF) {
          utflen += 3;
        } else {
          utflen += 2;
        }
      }

      if (utflen >= 0xFFFF)
      {
        innerStream.write((byte)0xFF);
        innerStream.write((byte)0xFF);
        writeInt(utflen);
      }
      else
      {
        innerStream.write((byte) ((utflen >>> 8) & 0xFF));
        innerStream.write((byte) ((utflen >>> 0) & 0xFF));
      }
      for (int i = 0; i < strlen; i++) {
        c = s.charAt(i);//charr[i];
        if ((c >= 0x0001) && (c <= 0x007F)) {
          innerStream.write((byte) c);
        } else if (c > 0x07FF) {
          innerStream.write((byte) (0xE0 | ((c >> 12) & 0x0F)));
          innerStream.write((byte) (0x80 | ((c >>  6) & 0x3F)));
          innerStream.write((byte) (0x80 | ((c >>  0) & 0x3F)));
        } else {
          innerStream.write((byte) (0xC0 | ((c >>  6) & 0x1F)));
          innerStream.write((byte) (0x80 | ((c >>  0) & 0x3F)));
        }
      }
    }
  }
  //custom InputStream to allow for greater than 64k reads with readUTF
  public static class MyDataInput extends java.io.InputStream implements java.io.DataInput
  {
    private java.io.InputStream innerStream;
    public MyDataInput(java.io.InputStream innerStream) {
      this.innerStream = innerStream;
    }
    public final int read() throws java.io.IOException
    { return innerStream.read(); }

    public final int read(byte b[]) throws java.io.IOException
    { return innerStream.read(b, 0, b.length); }

    public final int read(byte b[], int off, int len) throws java.io.IOException
    { return innerStream.read(b, off, len); }

    public final void readFully(byte b[]) throws java.io.IOException
    { readFully(b, 0, b.length); }

    public final void readFully(byte b[], int off, int len) throws java.io.IOException
    {
      if (len < 0)
        throw new IndexOutOfBoundsException();
      int n = 0;
      while (n < len) {
        int count = innerStream.read(b, off + n, len - n);
        if (count < 0)
          throw new java.io.EOFException();
        n += count;
      }
    }

    public final int skipBytes(int n) throws java.io.IOException {
      int total = 0;
      int cur = 0;

      while ((total<n) && ((cur = (int) innerStream.skip(n-total)) > 0)) {
        total += cur;
      }

      return total;
    }

    public final boolean readBoolean() throws java.io.IOException {
      int ch = innerStream.read();
      if (ch < 0)
        throw new java.io.EOFException();
      return (ch != 0);
    }

    public final byte readByte() throws java.io.IOException {
      int ch = innerStream.read();
      if (ch < 0)
        throw new java.io.EOFException();
      return (byte)(ch);
    }

    public final int readUnsignedByte() throws java.io.IOException {
      int ch = innerStream.read();
      if (ch < 0)
        throw new java.io.EOFException();
      return ch;
    }

    public final short readShort() throws java.io.IOException {
      int ch1 = innerStream.read();
      int ch2 = innerStream.read();
      if ((ch1 | ch2) < 0)
        throw new java.io.EOFException();
      return (short)((ch1 << 8) + (ch2 << 0));
    }

    public final int readUnsignedShort() throws java.io.IOException {
      int ch1 = innerStream.read();
      int ch2 = innerStream.read();
      if ((ch1 | ch2) < 0)
        throw new java.io.EOFException();
      return (ch1 << 8) + (ch2 << 0);
    }

    public final char readChar() throws java.io.IOException {
      int ch1 = innerStream.read();
      int ch2 = innerStream.read();
      if ((ch1 | ch2) < 0)
        throw new java.io.EOFException();
      return (char)((ch1 << 8) + (ch2 << 0));
    }

    byte[] myReadBuff = new byte[8];
    public final int readInt() throws java.io.IOException {
      readFully(myReadBuff, 0, 4);
      int ch1 = myReadBuff[0] & 0xFF;
      int ch2 = myReadBuff[1] & 0xFF;
      int ch3 = myReadBuff[2] & 0xFF;
      int ch4 = myReadBuff[3] & 0xFF;
      if ((ch1 | ch2 | ch3 | ch4) < 0)
        throw new java.io.EOFException();
      return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    public final long readLong() throws java.io.IOException {
      readFully(myReadBuff, 0, 8);
      int ch1 = myReadBuff[0] & 0xFF;
      int ch2 = myReadBuff[1] & 0xFF;
      int ch3 = myReadBuff[2] & 0xFF;
      int ch4 = myReadBuff[3] & 0xFF;
      if ((ch1 | ch2 | ch3 | ch4) < 0)
        throw new java.io.EOFException();
      int i0 = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
      ch1 = myReadBuff[4] & 0xFF;
      ch2 = myReadBuff[5] & 0xFF;
      ch3 = myReadBuff[6] & 0xFF;
      ch4 = myReadBuff[7] & 0xFF;
      if ((ch1 | ch2 | ch3 | ch4) < 0)
        throw new java.io.EOFException();
      int i1 = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
      return ((long)i0 << 32) + (i1 & 0xFFFFFFFFL);
    }

    public final float readFloat() throws java.io.IOException {
      return Float.intBitsToFloat(readInt());
    }
    public final double readDouble() throws java.io.IOException {
      return Double.longBitsToDouble(readLong());
    }

    public final String readLine() throws java.io.IOException {
      throw new UnsupportedOperationException();
    }

    public final String readUTF() throws java.io.IOException {
      // Since we're doing writeUTF from DataOutputStream in the writer we have to
      // be compliant on the reader and use the same charset
      //				return java.io.DataInputStream.readUTF(this);
      //				int utflen = readUnsignedShort();
      //				byte bytearr [] = new byte[utflen];
      //				readFully(bytearr, 0, utflen);
      //				return new String(bytearr, Sage.CHARSET);
      // NOTE: We had to update this to use the same code as in FastRandomFile so that we can deal with UTF
      // strings larger than 64k. That only occurs in the Wiz.bin file and updating the code in here as
      // well as FastRandomFile will cover all usages of Wiz.bin data then.
      int utflen = readUnsignedShort();
      if (utflen == 0)
        return "";
      else if (utflen == 0xFFFF)
        utflen = readInt();
      if (bytearr == null || bytearr.length < utflen)
      {
        bytearr = new byte[utflen*2];
        chararr = new char[utflen*2];
      }

      int c, c2, c3;
      int incount = 0;
      int outcount = 0;

      readFully(bytearr, 0, utflen);

      while (incount < utflen) {
        // Fast path for all 7 bit ASCII chars
        c = bytearr[incount] & 0xFF;
        if (c > 127) break;
        incount++;
        chararr[outcount++]=(char)c;
      }

      int x;
      while (incount < utflen) {
        c = bytearr[incount] & 0xFF;
        if (c < 128) {
          incount++;
          chararr[outcount++]=(char)c;
          continue;
        }
        // Look at the top four bits only, since only they can affect this
        x = c >> 4;
        if (x == 12 || x == 13) {
          // 110xxxxx 10xxxxxx - 2 bytes for this char
          incount += 2;
          if (incount > utflen)
            throw new java.io.UTFDataFormatException("bad UTF data: missing second byte of 2 byte char at " + incount);
          c2 = bytearr[incount - 1];
          // Verify next byte starts with 10xxxxxx
          if ((c2 & 0xC0) != 0x80)
            throw new java.io.UTFDataFormatException("bad UTF data: second byte format after 110xxxx is wrong char: 0x" +
              Integer.toString((int)c2, 16) + " count: " + incount);
          chararr[outcount++]=(char)(((c & 0x1F) << 6) | (c2 & 0x3F));
        }
        else if (x == 14)
        {
          // 1110xxxx 10xxxxxx 10xxxx - 3 bytes for this char
          incount += 3;
          if (incount > utflen)
            throw new java.io.UTFDataFormatException("bad UTF data: missing extra bytes of 3 byte char at " + incount);
          c2 = bytearr[incount - 2];
          c3 = bytearr[incount - 1];
          // Verify next bytes start with 10xxxxxx
          if (((c2 & 0xC0) != 0x80) || ((c3 & 0xC0) != 0x80))
            throw new java.io.UTFDataFormatException("bad UTF data: extra byte format after 1110xxx is wrong char2: 0x" +
              Integer.toString((int)c2, 16) + " char3: " + Integer.toString((int)c3, 16) + " count: " + incount);
          chararr[outcount++]=(char)(((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F));
        }
        else
        {
          // No need to support beyond this, as we only have 16 bit chars in Java
          throw new java.io.UTFDataFormatException("bad UTF data: we don't support more than 16 bit chars char: " +
            Integer.toString((int)c, 16) + " count:" + incount);
        }
      }
      return new String(chararr, 0, outcount);
    }
    // For optimizing UTF reads
    private byte[] bytearr = null;
    private char[] chararr = null;

  }

}
