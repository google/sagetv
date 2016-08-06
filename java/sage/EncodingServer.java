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

public class EncodingServer implements Runnable
{
  public static final String ENCODING_SERVER_PASSWORD_MD5 = "encoding_server_password_md5";
  private static final boolean NETWORK_ENCODER_DEBUG = Sage.getBoolean("network_encoder_debug", false);
  public EncodingServer(int inPortNum)
  {
    portNum = inPortNum;
    alive = true;
    if (portNum == 0)
      portNum = 6969;
    uploadEncodedFile = Sage.getBoolean("encoding_server_uploading", true);
    /*Thread t = new Thread(this, "EncodingServer");
		t.setDaemon(true);
		t.start();*/
  }

  public void kill()
  {
    alive = false;
    if (capDev != null)
      capDev.freeDevice();
    java.util.Iterator walker = captureMap.keySet().iterator();
    while (walker.hasNext())
    {
      CaptureDeviceInput cdi = (CaptureDeviceInput) walker.next();
      cdi.getCaptureDevice().freeDevice();
      walker.remove();
    }
    if (ss != null)
    {
      try
      {
        ss.close();
      }
      catch (Exception e)
      {}
      ss = null;
    }
    if (discoverySocket != null)
    {
      try
      {
        discoverySocket.close();
      }catch(Exception e){}
      discoverySocket = null;
    }
  }

  public void run()
  {
    try
    {
      ss = new java.net.ServerSocket(portNum);
    }
    catch (Exception e)
    {
      System.out.println("Error creating EncodingServer socket:" + e);
      return;
    }
    if (Sage.DBG) System.out.println("EncodingServer launched on port " + portNum);
    Thread t = new Thread("SageTVEncodingDiscoveryServer")
    {
      public void run()
      {
        discoverySocket = null;
        try
        {
          discoverySocket = new java.net.DatagramSocket(Sage.getInt("encoding_discovery_port", 8271));
        }
        catch (java.io.IOException e)
        {
          System.out.println("Error creating discovery socket:" + e);
          return;
        }
        if (Sage.DBG) System.out.println("SageTVEncodingDiscoveryServer was instantiated.");
        while (alive && discoverySocket != null)
        {
          try
          {
            java.net.DatagramPacket packet = new java.net.DatagramPacket(new byte[4096], 4096);
            discoverySocket.receive(packet);
            if (Sage.DBG) System.out.println("Server got broadcast packet: " + packet);
            if (alive)
            {
              // The first 3 bytes should be STN, and then the next 3 bytes are the version info.
              // It sends 32 bytes so we don't have issues with not having enough data for it to flush or whatever
              // NOTE: We can't send back our description in the UDP packet because the amount of data is too large,
              // so we have to do another TCP connection to the encoding server to find that out
              if (packet.getLength() >= 6)
              {
                byte[] data = packet.getData();
                if (data[0] == 'S' && data[1] == 'T' && data[2] == 'N')
                {
                  byte majVer = data[3];
                  byte minVer = data[4];
                  byte buildVer = data[5];
                  if (majVer > Sage.ENCODER_COMPATIBLE_MAJOR_VERSION ||
                      (majVer == Sage.ENCODER_COMPATIBLE_MAJOR_VERSION &&
                      (minVer > Sage.ENCODER_COMPATIBLE_MINOR_VERSION ||
                          (minVer == Sage.ENCODER_COMPATIBLE_MINOR_VERSION &&
                          buildVer >= Sage.ENCODER_COMPATIBLE_MICRO_VERSION))))
                  {
                    // Compatible version, send back the response with our version info in there
                    data[3] = Sage.ENCODER_COMPATIBLE_MAJOR_VERSION;
                    data[4] = Sage.ENCODER_COMPATIBLE_MINOR_VERSION;
                    data[5] = Sage.ENCODER_COMPATIBLE_MICRO_VERSION;
                    // 2 bytes for the port
                    data[6] = (byte)((portNum >> 8) & 0xFF);
                    data[7] = (byte)(portNum & 0xFF);
                    String desc = SageTV.hostname;
                    byte[] descBytes = desc.getBytes(Sage.I18N_CHARSET);
                    data[8] = (byte)descBytes.length;
                    System.arraycopy(descBytes, 0, data, 9, descBytes.length);
                    packet.setLength(9 + descBytes.length);
                    if (Sage.DBG) System.out.println("Server sent back discovery data:" + packet);
                    discoverySocket.send(packet);
                  }
                }
              }
            }
          }
          catch (java.io.IOException e)
          {
            if (alive)
              System.out.println("Error w/SageTV client connection:" + e);
            try{Thread.sleep(100);}catch(Exception e1){} // if its closing, let it close
          }
        }
        try
        {
          discoverySocket.close();
        }catch (Exception e){}
      }
    };
    t.setDaemon(true);
    t.start();
    while (alive)
    {
      java.net.Socket mySock = null;
      try
      {
        mySock = ss.accept();
        if (Sage.DBG) System.out.println("EncodingServer received connection:" + mySock);
        new EncodingServerConnection(mySock);
      }
      catch (Exception e)
      {
        System.out.println("Error with EncodingServer socket:" + e);
        e.printStackTrace();
        try{Thread.sleep(100);}catch(Exception e1){} // if its closing, let it close
      }
    }
  }

  private boolean ensureDeviceLoad(CaptureDevice cd)
  {
    if (!cd.isLoaded())
    {
      try
      {
        cd.loadDevice();
        return true;
      }
      catch (EncodingException e)
      {
        Catbert.distributeHookToAll("MediaPlayerError", new Object[] { Sage.rez("Capture"), e.getMessage() });
        return false;
      }
    }
    return true;
  }

  private int portNum;
  private boolean alive;
  private java.net.ServerSocket ss;
  private java.net.DatagramSocket discoverySocket;
  // For V3 network encoders
  // This map stores CaptureDeviceInputs as the keys and Strings (filenames) as the values which correspond
  // to what we're currently encoding
  private java.util.Map captureMap = new java.util.HashMap();
  private java.util.Map uploadIDMap = new java.util.HashMap();

  // For V2 network encoders
  private CaptureDevice capDev;
  private CaptureDeviceInput capDevInput;
  private String currRecordFile;
  private Object serverLock = new Object();
  private boolean uploadEncodedFile;

  private class EncodingServerConnection implements Runnable
  {
    private java.net.Socket mySock;
    public EncodingServerConnection(java.net.Socket mySock)
    {
      this.mySock = mySock;
      if (Sage.getBoolean("multithread_encoding_server", true))
      {
        Pooler.execute(this, "EncodingServerConn");
      }
      else
        run();
    }
    public void run()
    {
      java.io.DataOutputStream outStream = null;
      java.io.DataInputStream inStream = null;
      try
      {
        mySock.setSoTimeout(15000);
        // We need to be sure the data gets sent before we close so linger on close
        //mySock.setSoLinger(true, 4);
        //mySock.setTcpNoDelay(true);
        outStream = new java.io.DataOutputStream(new java.io.BufferedOutputStream(mySock.getOutputStream()));
        inStream = new java.io.DataInputStream(new java.io.BufferedInputStream(mySock.getInputStream()));
        /*				String pass = Sage.readLineBytes(inStream);
				if (!pass.equalsIgnoreCase(Sage.get(ENCODING_SERVER_PASSWORD_MD5, "")))
				{
					outStream.write("INVALID_PASSWORD\r\n".getBytes(Sage.CHARSET));
					outStream.flush();
				}
				else // valid password, accept the encoding command
         */
        //					outStream.write("OK\r\n".getBytes(Sage.CHARSET));
        //					outStream.flush();
        while (true)
        {
          String str = Sage.readLineBytes(inStream);
          if (Sage.DBG && NETWORK_ENCODER_DEBUG) System.out.println("EncodingServer Recvd:" + str);
          synchronized (serverLock)
          {
            if (str.equals("VERSION"))
            {
              outStream.write("3.0\r\n".getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.startsWith("STOP"))
            {
              if (str.indexOf(' ') != -1)
              {
                // V3 encoder
                CaptureDeviceInput cdi = MMC.getInstance().getCaptureDeviceInputNamed(str.substring(str.indexOf(' ') + 1));
                if (cdi != null)
                {
                  cdi.getCaptureDevice().stopEncoding();
                  uploadIDMap.remove(captureMap.get(cdi));
                  captureMap.put(cdi, null);
                }
              }
              else
              {
                if (capDev != null)
                  capDev.stopEncoding();
                uploadIDMap.remove(currRecordFile);
                currRecordFile = null;
              }
              //java.awt.EventQueue.invokeAndWait(new Runnable() { public void run() { master.doStop(); } });
              outStream.write("OK\r\n".getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.startsWith("START "))
            {
              currRecordFile = null;
              // Same for V3/V2 encoders because the input name is specified
              java.util.StringTokenizer toker = new java.util.StringTokenizer(str.substring(6), "|");
              int uploadID = 0;
              if (toker.countTokens() == 6)
              {
                // V3 has upload file ID
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
                uploadID = Integer.parseInt(toker.nextToken());
              }
              else
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
              String channel = toker.nextToken();
              long bufferSize = Long.parseLong(toker.nextToken());
              String filename = toker.nextToken();
              String encoding = toker.nextToken();
              if (capDevInput != null)
              {
                capDev = capDevInput.getCaptureDevice();
                if (ensureDeviceLoad(capDev))
                {
                  capDev.setEncodingQuality(encoding);
                  capDev.setRecordBufferSize(0);
                  if (uploadEncodedFile && uploadID != 0)
                  {
                    String remoteFilename = "stv://" + uploadID + "@" + mySock.getInetAddress().getHostAddress() + "/" + filename;
                    if (Sage.DBG) System.out.println("Uploading network encode to remote file: " + remoteFilename);
                    capDev.startEncoding(capDevInput, remoteFilename, channel);
                  }
                  else
                    capDev.startEncoding(capDevInput, filename, channel);
                  currRecordFile = filename;
                  captureMap.put(capDevInput, currRecordFile);
                  if (uploadID != 0)
                    uploadIDMap.put(currRecordFile, new Integer(uploadID));
                  outStream.write("OK\r\n".getBytes(Sage.BYTE_CHARSET));
                }
                else
                {
                  outStream.write("ERROR Device Load Failed\r\n".getBytes(Sage.BYTE_CHARSET));
                }
              }
              else
              {
                outStream.write("ERROR Invalid Input\r\n".getBytes(Sage.BYTE_CHARSET));
              }
            }
            else if (str.startsWith("BUFFER "))
            {
              currRecordFile = null;
              // Same for V3/V2 encoders because the input name is specified
              java.util.StringTokenizer toker = new java.util.StringTokenizer(str.substring(7), "|");
              int uploadID = 0;
              if (toker.countTokens() == 6)
              {
                // V3 has upload file ID
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
                uploadID = Integer.parseInt(toker.nextToken());
              }
              else
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
              String channel = toker.nextToken();
              long bufferSize = Long.parseLong(toker.nextToken());
              String filename = toker.nextToken();
              String encoding = toker.nextToken();
              if (capDevInput != null)
              {
                capDev = capDevInput.getCaptureDevice();
                if (ensureDeviceLoad(capDev))
                {
                  capDev.setEncodingQuality(encoding);
                  capDev.setRecordBufferSize(bufferSize);
                  if (uploadEncodedFile && uploadID != 0)
                  {
                    String remoteFilename = "stv://" + uploadID + "@" + mySock.getInetAddress().getHostAddress() + "/" + filename;
                    if (Sage.DBG) System.out.println("Uploading network encode to remote file: " + remoteFilename);
                    capDev.startEncoding(capDevInput, remoteFilename, channel);
                  }
                  else
                    capDev.startEncoding(capDevInput, filename, channel);
                  currRecordFile = filename;
                  captureMap.put(capDevInput, currRecordFile);
                  if (uploadID != 0)
                    uploadIDMap.put(currRecordFile, new Integer(uploadID));
                  outStream.write("OK\r\n".getBytes(Sage.BYTE_CHARSET));
                }
                else
                {
                  outStream.write("ERROR Device Load Failed\r\n".getBytes(Sage.BYTE_CHARSET));
                }
              }
              else
              {
                outStream.write("ERROR Invalid Input\r\n".getBytes(Sage.BYTE_CHARSET));
              }
            }
            else if (str.startsWith("BUFFER_SWITCH "))
            {
              currRecordFile = null;
              java.util.StringTokenizer toker = new java.util.StringTokenizer(str.substring(14), "|");
              int uploadID = 0;
              if (toker.countTokens() == 5)
              {
                // V3 encoder
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
                capDev = capDevInput.getCaptureDevice();
                uploadID = Integer.parseInt(toker.nextToken());
              }
              else if (toker.countTokens() == 4)
              {
                // V3 encoder
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
                capDev = capDevInput.getCaptureDevice();
              }
              String channel = toker.nextToken();
              long bufferSize = Long.parseLong(toker.nextToken());
              String filename = toker.nextToken();
              if (uploadEncodedFile && uploadID != 0)
              {
                String remoteFilename = "stv://" + uploadID + "@" + mySock.getInetAddress().getHostAddress() + "/" + filename;
                if (Sage.DBG) System.out.println("Uploading network encode to remote file: " + remoteFilename);
                if (capDev != null)
                  capDev.switchEncoding(remoteFilename, channel);
              }
              else if (capDev != null)
                capDev.switchEncoding(filename, channel);
              currRecordFile = filename;
              if (uploadID != 0)
                uploadIDMap.put(currRecordFile, new Integer(uploadID));
              captureMap.put(capDevInput, currRecordFile);
              outStream.write("OK\r\n".getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.startsWith("SWITCH "))
            {
              currRecordFile = null;
              java.util.StringTokenizer toker = new java.util.StringTokenizer(str.substring(7), "|");
              int uploadID = 0;
              if (toker.countTokens() == 4)
              {
                // V3 encoder
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
                capDev = capDevInput.getCaptureDevice();
                uploadID = Integer.parseInt(toker.nextToken());
              }
              else if (toker.countTokens() == 3)
              {
                // V3 encoder
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
                capDev = capDevInput.getCaptureDevice();
              }
              String channel = toker.nextToken();
              String filename = toker.nextToken();
              if (uploadEncodedFile && uploadID != 0)
              {
                String remoteFilename = "stv://" + uploadID + "@" + mySock.getInetAddress().getHostAddress() + "/" + filename;
                if (Sage.DBG) System.out.println("Uploading network encode to remote file: " + remoteFilename);
                if (capDev != null)
                  capDev.switchEncoding(remoteFilename, channel);
              }
              else if (capDev != null)
                capDev.switchEncoding(filename, channel);
              //java.awt.EventQueue.invokeAndWait(new Runnable() { public void run() {
              //	master.switchRecord(fileObj, channel); } });
              currRecordFile = filename;
              if (uploadID != 0)
                uploadIDMap.put(currRecordFile, new Integer(uploadID));
              captureMap.put(capDevInput, currRecordFile);
              outStream.write("OK\r\n".getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.startsWith("GET_START"))
            {
              if (str.indexOf(' ') != -1)
              {
                // V3 encoder
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(str.substring(str.indexOf(' ') + 1));
                capDev = capDevInput.getCaptureDevice();
              }
              outStream.write((((capDev != null) ? capDev.getRecordStart() : 0) + "\r\n").getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.startsWith("GET_SIZE"))
            {
              if (str.indexOf(' ') != -1)
              {
                // V3 encoder
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(str.substring(str.indexOf(' ') + 1));
                capDev = capDevInput.getCaptureDevice();
              }
              outStream.write((((capDev != null) ? capDev.getRecordedBytes() : 0) + "\r\n").getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.startsWith("GET_FILE_SIZE "))
            {
              String filename = str.substring("GET_FILE_SIZE ".length());
              // Find the device performing this capture
              java.util.Iterator walker = captureMap.entrySet().iterator();
              while (walker.hasNext())
              {
                java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
                if (ent.getValue() != null && ent.getValue().equals(filename))
                {
                  capDevInput = (CaptureDeviceInput) ent.getKey();
                  capDev = capDevInput.getCaptureDevice();
                  currRecordFile = ent.getValue().toString();
                  break;
                }
              }
              if (filename.equals(currRecordFile))
                outStream.write((((capDev != null) ? capDev.getRecordedBytes() : 0) + "\r\n").getBytes(Sage.BYTE_CHARSET));
              else
                outStream.write((new java.io.File(filename).length() + "\r\n").getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.equals("NOOP"))
            {
              outStream.write("OK\r\n".getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.startsWith("TUNE "))
            {
              java.util.StringTokenizer toker = new java.util.StringTokenizer(str.substring(5), "|");
              if (toker.countTokens() == 2)
              {
                // V3 encoder
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
                capDev = capDevInput.getCaptureDevice();
              }
              String chanString = toker.nextToken();
              if (capDevInput != null)
                capDevInput.tuneToChannel(chanString);
              outStream.write("OK\r\n".getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.startsWith("AUTOTUNE "))
            {
              java.util.StringTokenizer toker = new java.util.StringTokenizer(str.substring(9), "|");
              if (toker.countTokens() == 2)
              {
                // V3 encoder
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
                capDev = capDevInput.getCaptureDevice();
              }
              String chanString = toker.nextToken();
              boolean rv = false;
              if (capDevInput != null)
                rv = capDevInput.autoTuneChannel(chanString);
              outStream.write((rv ? "OK\r\n" : "NO_SIGNAL\r\n").getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.startsWith("AUTOSCAN "))
            {
              java.util.StringTokenizer toker = new java.util.StringTokenizer(str.substring(9), "|");
              if (toker.countTokens() == 2)
              {
                // V3 encoder
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
                capDev = capDevInput.getCaptureDevice();
              }
              String chanString = toker.nextToken();
              boolean rv = false;
              if (capDevInput != null)
                rv = capDevInput.autoScanChannel(chanString);
              outStream.write((rv ? "OK\r\n" : "NO_SIGNAL\r\n").getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.startsWith("AUTOINFOSCAN "))
            {
              java.util.StringTokenizer toker = new java.util.StringTokenizer(str.substring("AUTOINFOSCAN ".length()), "|");
              if (toker.countTokens() == 2)
              {
                // V3 encoder
                capDevInput = MMC.getInstance().getCaptureDeviceInputNamed(toker.nextToken());
                capDev = capDevInput.getCaptureDevice();
              }
              String chanString = toker.nextToken();
              String rv = "ERROR";
              if (capDevInput != null)
                rv = capDevInput.ScanChannelInfo(chanString);
              outStream.write((rv + "\r\n").getBytes(Sage.BYTE_CHARSET));
            }
            else if (str.equals("PROPERTIES"))
            {
              // We then send the following data structure:
              // 2 bytes - number of properties that follow
              // then for each property
              // 2 bytes for the number of characters
              // the number of characters specified in the prior 2 bytes
              // NOTE: We skip the following properties:
              // provider_id
              // We also modify the following properties:
              // device_class (change to "NetworkEncoder"), encoding_host (add our host:port string)
              java.util.Properties allPrefs = Sage.getAllPrefs();
              String[] encRoots = MMC.getInstance().getEncoderPropertyRoots();
              java.util.Enumeration keyNames = allPrefs.propertyNames();
              java.util.ArrayList propVec = new java.util.ArrayList();
              while (keyNames.hasMoreElements())
              {
                String currKey = (String) keyNames.nextElement();
                for (int i = 0; i < encRoots.length; i++)
                {
                  if (currKey.startsWith(encRoots[i]))
                  {
                    if (currKey.indexOf("provider_id") != -1 ||
                        currKey.indexOf("device_class") != -1 ||
                        currKey.indexOf("encoding_host") != -1)
                      continue;
                    propVec.add(currKey + "=" + allPrefs.get(currKey));
                    break;
                  }
                }
              }
              outStream.write((propVec.size() + "\r\n").getBytes(Sage.BYTE_CHARSET));
              for (int i = 0; i < propVec.size(); i++)
              {
                outStream.write((propVec.get(i) + "\r\n").getBytes(Sage.BYTE_CHARSET));
              }
            }
            else
            {
              outStream.write(("ERROR Unrecognized command of:" + str + "\r\n").getBytes(Sage.BYTE_CHARSET));
            }
          }
          outStream.flush();
          if (Sage.DBG && NETWORK_ENCODER_DEBUG) System.out.println("Encoding server sent response to " + mySock);
        }
      }
      catch (Exception e)
      {
        System.out.println("Error with EncodingServer socket:" + e);
        e.printStackTrace();
        if (outStream != null)
        {
          try
          {
            outStream.write(("ERROR " + e + "\r\n").getBytes(Sage.BYTE_CHARSET));
            outStream.flush();
          }
          catch (Exception e1)
          {}
        }
      }
      finally
      {
        try
        {
          if (inStream != null)
            inStream.close();
        }catch (Exception e){System.out.println("Error1 closing:" + e);}
        inStream = null;
        try
        {
          if (outStream != null)
            outStream.close();
        }catch (Exception e){System.out.println("Error2 closing:" + e);}
        outStream = null;
        try
        {
          if (mySock != null)
            mySock.close();
        }catch (Exception e){System.out.println("Error3 closing:" + e);}
        mySock = null;
      }
    }
  }
}
