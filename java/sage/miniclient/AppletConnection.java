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
package sage.miniclient;

public class AppletConnection
{
  public AppletConnection(AppletGFXCMD theGfx, String serverName, String myID)
  {
    myGfx = theGfx;
    myGfx.setConn(this);
    if (serverName.indexOf(":") == -1)
    {
      this.serverName = serverName;
      this.port = 31099;
    }
    else
    {
      this.serverName = serverName.substring(0, serverName.indexOf(":"));
      this.port = 31099;
      try
      {
        this.port = Integer.parseInt(serverName.substring(serverName.indexOf(":") + 1));
      }
      catch (NumberFormatException e){}
    }
    if (myID == null)
      myID = "00:00:11:11:22:22";
    this.myID = myID;
  }

  private java.net.Socket EstablishServerConnection(int connType) throws java.io.IOException
  {
    int flag=1;
    int blockingmode;
    java.net.Socket sake = null;
    java.io.InputStream inStream = null;
    java.io.OutputStream outStream = null;
    try
    {
      sake = new java.net.Socket(serverName, port);
      sake.setTcpNoDelay(true);
      sake.setKeepAlive(true);
      outStream = sake.getOutputStream();
      inStream = sake.getInputStream();
      byte[] msg = new byte[7];
      msg[0] = (byte)1;
      final String[] macBuf = new String[1];
      macBuf[0] = myID;

      if (macBuf[0] != null)
      {
        for (int i = 0; i < macBuf[0].length(); i+=3)
        {
          msg[1 + i/3] = (byte)(Integer.parseInt(macBuf[0].substring(i, i+2), 16) & 0xFF);
        }
      }
      outStream.write(msg);
      outStream.write(connType);
      int rez = inStream.read();
      if(rez != 2)
      {
        System.out.println("Error with reply from server:" + rez);
        inStream.close();
        outStream.close();
        sake.close();
        return null;
      }
      System.out.println("Connection accepted by server");
      return sake;
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR with socket connection: " + e);
      try
      {
        sake.close();
      }
      catch (Exception e1)
      {}
      try
      {
        inStream.close();
      }
      catch (Exception e1)
      {}
      try
      {
        outStream.close();
      }
      catch (Exception e1)
      {}
      throw e;
    }
  }

  public void connect() throws java.io.IOException
  {
    System.out.println("Attempting to connect to server at " + serverName + ":" + port);
    while (gfxSocket == null)
    {
      gfxSocket = EstablishServerConnection(0);
      if (gfxSocket == null)
      {
        //System.out.println("couldn't connect to gfx server, retrying in 5 secs.");
        //try { Thread.sleep(5000);} catch (InterruptedException e){}
        throw new java.net.ConnectException();
      }
    }
    System.out.println("Connected to gfx server");

    alive = true;
    Thread t = new Thread("GFX-" + serverName)
    {
      public void run()
      {
        GFXThread();
      }
    };
    t.start();
  }

  public boolean isConnected()
  {
    return alive;
  }

  public void close()
  {
    alive = false;
    try
    {
      gfxSocket.close();
    }
    catch (Exception e)
    {}
    AppletGFXCMD oldGfx = myGfx;
    myGfx = null;
    oldGfx.close();
  }

  private void GFXThread()
  {
    byte[] cmd = new byte[4];
    int command,len;
    int[] hasret = new int[1];
    int retval;
    byte[] retbuf = new byte[4];
    java.io.DataInputStream is = null;
    final java.util.Vector gfxSyncVector = new java.util.Vector();

    try
    {
      eventChannel = new java.io.DataOutputStream(new java.io.BufferedOutputStream(gfxSocket.getOutputStream()));
      is = new java.io.DataInputStream(gfxSocket.getInputStream());
      final java.io.DataInputStream gfxIs = is;
      // Create the parallel threads so we can sync video and UI rendering appropriately
      Thread gfxReadThread = new Thread("GFXRead")
      {
        public void run()
        {
          byte[] gfxCmds = new byte[4];
          byte[] cmdbuffer = new byte[4096];
          int len;
          while (alive)
          {
            synchronized (gfxSyncVector)
            {
              if (gfxSyncVector.contains(gfxCmds))
              {
                try
                {
                  gfxSyncVector.wait(5000);
                }catch (InterruptedException e)
                {}
                continue;
              }
            }
            try
            {
              //System.out.println("before gfxread readfully");
              gfxIs.readFully(gfxCmds);
              len = ((gfxCmds[1] & 0xFF) << 16) | ((gfxCmds[2] & 0xFF)<<8) | (gfxCmds[3] & 0xFF);
              if (cmdbuffer.length < len)
              {
                cmdbuffer = new byte[len];
              }
              // Read from the tcp socket
              gfxIs.readFully(cmdbuffer, 0, len);
            }
            catch (Exception e)
            {
              synchronized (gfxSyncVector)
              {
                gfxSyncVector.add(e);
                return;
              }
            }
            synchronized (gfxSyncVector)
            {
              gfxSyncVector.add(gfxCmds);
              gfxSyncVector.add(cmdbuffer);
              gfxSyncVector.notifyAll();
            }
          }
        }
      };
      gfxReadThread.setDaemon(true);
      gfxReadThread.start();
      while (alive)
      {
        byte []cmdbuffer;
        synchronized (gfxSyncVector)
        {
          if (!gfxSyncVector.isEmpty())
          {
            Object newData = gfxSyncVector.get(0);
            if (newData instanceof Throwable)
              throw (Throwable) newData;
            else
            {
              cmd = (byte[]) newData;
              cmdbuffer = (byte[]) gfxSyncVector.get(1);
            }
          }
          else
          {
            try
            {
              gfxSyncVector.wait(5000);
            }
            catch (InterruptedException e){}
            continue;
          }
        }
        //is.readFully(cmd);

        command = (cmd[0] & 0xFF);
        len = ((cmd[1] & 0xFF) << 16) | ((cmd[2] & 0xFF)<<8) | (cmd[3] & 0xFF);
        //System.out.println("inside loop command "+command + " len "+len);
        if (command == 16) // GFX cmd
        {
          // We need to let the opengl rendering thread do that...
          command = (cmdbuffer[0] & 0xFF);
          retval = myGfx.ExecuteGFXCommand(command, len, cmdbuffer, hasret);

          if(hasret[0] != 0)
          {
            retbuf[0] = (byte)((retval>>24) & 0xFF);
            retbuf[1] = (byte) ((retval>>16) & 0xFF);
            retbuf[2] = (byte) ((retval>>8) & 0xFF);
            retbuf[3] = (byte) ((retval>>0) & 0xFF);
            synchronized (eventChannel)
            {
              eventChannel.write(16); // GFX reply
              eventChannel.writeShort(0);eventChannel.write(4);// 3 byte length of 4
              eventChannel.writeInt(0); // timestamp
              eventChannel.writeInt(replyCount++);
              eventChannel.writeInt(0); // pad
              if (encryptEvents && evtEncryptCipher != null)
                eventChannel.write(evtEncryptCipher.doFinal(retbuf, 0, 4));
              else
                eventChannel.write(retbuf, 0, 4);
              eventChannel.flush();
            }
          }
        }
        else if (command == 0) // get property
        {
          String propName = new String(cmdbuffer, 0, len);
          System.out.println("GetProperty: " + propName);
          String propVal = "";
          byte[] propValBytes = null;
          if ("GFX_TEXTMODE".equals(propName))
          {
            propVal = "REMOTEFONTS";
          }
          else if ("GFX_BLENDMODE".equals(propName))
          {
            propVal = "POSTMULTIPLY";
          }
          else if ("GFX_SCALING".equals(propName))
          {
            propVal = "SOFTWARE";
          }
          else if ("GFX_OFFLINE_IMAGE_CACHE".equals(propName))
          {
            propVal = "FALSE";
          }
          else if ("GFX_BITMAP_FORMAT".equals(propName))
          {
            propVal = "PNG,JPG,GIF,BMP";
          }
          else if ("INPUT_DEVICES".equals(propName))
          {
            propVal = "KEYBOARD,MOUSE";
          }
          else if ("DISPLAY_OVERSCAN".equals(propName))
          {
            propVal = "0;0;1.0;1.0";
          }
          else if ("CRYPTO_ALGORITHMS".equals(propName))
            propVal = "";//MiniClient.cryptoFormats;
          else if ("CRYPTO_SYMMETRIC_KEY".equals(propName))
          {
            if (serverPublicKey != null && encryptedSecretKeyBytes == null)
            {
              if (currentCrypto.indexOf("RSA") != -1)
              {
                // We have to generate our secret key and then encrypt it with the server's public key
                javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance("Blowfish");
                javax.crypto.SecretKey myKey = keyGen.generateKey();
                evtEncryptCipher = javax.crypto.Cipher.getInstance("Blowfish");
                evtEncryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, myKey);

                byte[] rawSecretBytes = myKey.getEncoded();
                try
                {
                  javax.crypto.Cipher encryptCipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
                  encryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, serverPublicKey);
                  encryptedSecretKeyBytes = encryptCipher.doFinal(rawSecretBytes);
                }
                catch (Exception e)
                {
                  System.out.println("Error encrypting data to submit to server: " + e);
                  e.printStackTrace();
                }
              }
              else
              {
                // We need to finish the DH key agreement and generate the shared secret key
                /*
                 * Bob gets the DH parameters associated with Alice's public key.
                 * He must use the same parameters when he generates his own key
                 * pair.
                 */
                javax.crypto.spec.DHParameterSpec dhParamSpec = ((javax.crypto.interfaces.DHPublicKey)serverPublicKey).getParams();

                // Bob creates his own DH key pair
                System.out.println("Generate DH keypair ...");
                java.security.KeyPairGenerator bobKpairGen = java.security.KeyPairGenerator.getInstance("DH");
                bobKpairGen.initialize(dhParamSpec);
                java.security.KeyPair bobKpair = bobKpairGen.generateKeyPair();

                // Bob creates and initializes his DH KeyAgreement object
                javax.crypto.KeyAgreement bobKeyAgree = javax.crypto.KeyAgreement.getInstance("DH");
                bobKeyAgree.init(bobKpair.getPrivate());

                // Bob encodes his public key, and sends it over to Alice.
                encryptedSecretKeyBytes = bobKpair.getPublic().getEncoded();

                // We also have to generate the shared secret now
                bobKeyAgree.doPhase(serverPublicKey, true);
                javax.crypto.SecretKey myKey = bobKeyAgree.generateSecret("DES");
                evtEncryptCipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding");
                evtEncryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, myKey);
              }
            }
            propValBytes = encryptedSecretKeyBytes;
          }
          /*					else if ("GFX_SUPPORTED_RESOLUTIONS".equals(propName))
					{
						java.awt.Dimension scrSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
						propVal = Integer.toString(scrSize.width) + "x" + Integer.toString(scrSize.height) + ";windowed";
					}
					else if ("GFX_RESOLUTION".equals(propName))
					{
						sage.SageTVWindow winny = myGfx.getWindow();
						if (winny != null)
							propVal = Integer.toString(winny.getWidth()) + "x" + Integer.toString(winny.getHeight());
					}*/
          synchronized (eventChannel)
          {
            if (propValBytes == null)
              propValBytes = propVal.getBytes("ISO8859_1");
            eventChannel.write(0); // get property reply
            eventChannel.write(0);eventChannel.writeShort(propValBytes.length);// 3 byte length of 0
            eventChannel.writeInt(0); // timestamp
            eventChannel.writeInt(replyCount++);
            eventChannel.writeInt(0); // pad
            if (propValBytes.length > 0)
            {
              if (encryptEvents && evtEncryptCipher != null)
                eventChannel.write(evtEncryptCipher.doFinal(propValBytes));
              else
                eventChannel.write(propValBytes);
            }
            eventChannel.flush();
          }
        }
        else if (command == 1) // set property
        {
          short nameLen = (short) (((cmdbuffer[0] & 0xFF) << 8) | (cmdbuffer[1] & 0xFF));
          short valLen = (short) (((cmdbuffer[2] & 0xFF) << 8) | (cmdbuffer[3] & 0xFF));
          String propName = new String(cmdbuffer, 4, nameLen);
          //String propVal = new String(cmdbuffer, 4 + nameLen, valLen);
          System.out.println("SetProperty " + propName);
          synchronized (eventChannel)
          {
            boolean encryptThisReply = encryptEvents;
            if ("CRYPTO_PUBLIC_KEY".equals(propName))
            {
              byte[] keyBytes = new byte[valLen];
              System.arraycopy(cmdbuffer, 4 + nameLen, keyBytes, 0, valLen);
              java.security.spec.X509EncodedKeySpec pubKeySpec = new java.security.spec.X509EncodedKeySpec(keyBytes);
              java.security.KeyFactory keyFactory;
              if (currentCrypto.indexOf("RSA") != -1)
                keyFactory = java.security.KeyFactory.getInstance("RSA");
              else
                keyFactory = java.security.KeyFactory.getInstance("DH");
              serverPublicKey = keyFactory.generatePublic(pubKeySpec);
              retval = 0;
            }
            else if ("CRYPTO_ALGORITHMS".equals(propName))
            {
              currentCrypto = new String(cmdbuffer, 4 + nameLen, valLen);
              retval = 0;
            }
            else if ("CRYPTO_EVENTS_ENABLE".equals(propName))
            {
              if ("TRUE".equalsIgnoreCase(new String(cmdbuffer, 4 + nameLen, valLen)))
              {
                if (evtEncryptCipher != null)
                {
                  encryptEvents = true;
                  retval = 0;
                }
                else
                {
                  encryptEvents = false;
                  retval = 1;
                }
              }
              else
              {
                encryptEvents = false;
                retval = 0;
              }
              System.out.println("SageTVPlaceshifter event encryption is now=" + encryptEvents);
            }
            else
              retval = 1; // or the error code if it failed the set
            retbuf[0] = (byte)((retval>>24) & 0xFF);
            retbuf[1] = (byte) ((retval>>16) & 0xFF);
            retbuf[2] = (byte) ((retval>>8) & 0xFF);
            retbuf[3] = (byte) ((retval>>0) & 0xFF);
            eventChannel.write(0); // set property reply
            eventChannel.write(0);eventChannel.writeShort(4);// 3 byte length of 4
            eventChannel.writeInt(0); // timestamp
            eventChannel.writeInt(replyCount++);
            eventChannel.writeInt(0); // pad
            if (encryptThisReply)
              eventChannel.write(evtEncryptCipher.doFinal(retbuf, 0, 4));
            else
              eventChannel.write(retbuf, 0, 4);
            eventChannel.flush();
          }
        }

        // Remove whatever we just processed
        synchronized (gfxSyncVector)
        {
          gfxSyncVector.remove(0);
          gfxSyncVector.remove(0);
          gfxSyncVector.notifyAll();
        }
      }
    }
    catch (Throwable e)
    {
      System.out.println("Error w/ GFX Thread: " + e);
      e.printStackTrace();
    }
    finally
    {
      try
      {
        is.close();
      }
      catch (Exception e){}
      try
      {
        eventChannel.close();
      }
      catch (Exception e){}
      try
      {
        gfxSocket.close();
      }
      catch (Exception e){}

      if (alive)
        connectionError();
    }
  }

  public void postKeyEvent(int keyCode, int keyModifiers, char keyChar)
  {
    synchronized (eventChannel)
    {
      try
      {
        eventChannel.write(129); // kb event code
        eventChannel.write(0);eventChannel.writeShort(10);// 3 byte length of 10
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        if (encryptEvents && evtEncryptCipher != null)
        {
          byte[] data = new byte[10];
          data[0] = (byte) ((keyCode >> 24) & 0xFF);
          data[1] = (byte) ((keyCode >> 16) & 0xFF);
          data[2] = (byte) ((keyCode >> 8) & 0xFF);
          data[3] = (byte) (keyCode & 0xFF);
          data[4] = (byte) ((keyChar >> 8 ) & 0xFF);
          data[5] = (byte) (keyChar & 0xFF);
          data[6] = (byte) ((keyModifiers >> 24) & 0xFF);
          data[7] = (byte) ((keyModifiers >> 16) & 0xFF);
          data[8] = (byte) ((keyModifiers >> 8) & 0xFF);
          data[9] = (byte) (keyModifiers & 0xFF);
          eventChannel.write(evtEncryptCipher.doFinal(data));
        }
        else
        {
          eventChannel.writeInt(keyCode);
          eventChannel.writeChar(keyChar);
          eventChannel.writeInt(keyModifiers);
        }
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        connectionError();
      }
    }
  }

  public void postResizeEvent(java.awt.Dimension size)
  {
    synchronized (eventChannel)
    {
      try
      {
        eventChannel.write(192); // resize event code
        eventChannel.write(0);eventChannel.writeShort(8);// 3 byte length of 8
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        if (encryptEvents && evtEncryptCipher != null)
        {
          byte[] data = new byte[8];
          data[0] = (byte) ((size.width >> 24) & 0xFF);
          data[1] = (byte) ((size.width >> 16) & 0xFF);
          data[2] = (byte) ((size.width >> 8) & 0xFF);
          data[3] = (byte) (size.width & 0xFF);
          data[4] = (byte) ((size.height >> 24) & 0xFF);
          data[5] = (byte) ((size.height >> 16) & 0xFF);
          data[6] = (byte) ((size.height >> 8) & 0xFF);
          data[7] = (byte) (size.height & 0xFF);
          eventChannel.write(evtEncryptCipher.doFinal(data));
        }
        else
        {
          eventChannel.writeInt(size.width);
          eventChannel.writeInt(size.height);
        }
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        connectionError();
      }
    }
  }

  public void postMouseEvent(java.awt.event.MouseEvent evt)
  {
    synchronized (eventChannel)
    {
      try
      {
        if (evt.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED)
          eventChannel.write(132); // mouse click event code
        else if (evt.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED)
          eventChannel.write(130); // mouse press event code
        else if (evt.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED)
          eventChannel.write(131); // mouse release event code
        else if (evt.getID() == java.awt.event.MouseEvent.MOUSE_DRAGGED)
          eventChannel.write(134); // mouse drag event code
        else if (evt.getID() == java.awt.event.MouseEvent.MOUSE_MOVED)
          eventChannel.write(133); // mouse move event code
        else if (evt.getID() == java.awt.event.MouseEvent.MOUSE_WHEEL)
          eventChannel.write(135); // mouse wheel event code
        else
          return;
        eventChannel.write(0);eventChannel.writeShort(14);// 3 byte length of 14
        eventChannel.writeInt(0); // timestamp
        eventChannel.writeInt(replyCount++);
        eventChannel.writeInt(0); // pad
        if (encryptEvents && evtEncryptCipher != null)
        {
          byte[] data = new byte[14];
          data[0] = (byte) ((evt.getX() >> 24) & 0xFF);
          data[1] = (byte) ((evt.getX() >> 16) & 0xFF);
          data[2] = (byte) ((evt.getX() >> 8) & 0xFF);
          data[3] = (byte) (evt.getX() & 0xFF);
          data[4] = (byte) ((evt.getY() >> 24) & 0xFF);
          data[5] = (byte) ((evt.getY() >> 16) & 0xFF);
          data[6] = (byte) ((evt.getY() >> 8) & 0xFF);
          data[7] = (byte) (evt.getY() & 0xFF);
          data[8] = (byte) ((evt.getModifiers() >> 24) & 0xFF);
          data[9] = (byte) ((evt.getModifiers() >> 16) & 0xFF);
          data[10] = (byte) ((evt.getModifiers() >> 8) & 0xFF);
          data[11] = (byte) (evt.getModifiers() & 0xFF);
          if (evt.getID() == java.awt.event.MouseEvent.MOUSE_WHEEL)
            data[12] = (byte) ((java.awt.event.MouseWheelEvent)evt).getWheelRotation();
          else
            data[12] = (byte) evt.getClickCount();
          data[13] = (byte) evt.getButton();
          eventChannel.write(evtEncryptCipher.doFinal(data));
        }
        else
        {
          eventChannel.writeInt(evt.getX());
          eventChannel.writeInt(evt.getY());
          eventChannel.writeInt(evt.getModifiers());
          if (evt.getID() == java.awt.event.MouseEvent.MOUSE_WHEEL)
            eventChannel.write(((java.awt.event.MouseWheelEvent)evt).getWheelRotation());
          else
            eventChannel.write(evt.getClickCount());
          eventChannel.write(evt.getButton());
        }
        eventChannel.flush();
      }
      catch (Exception e)
      {
        System.out.println("Error w/ event thread: " + e);
        connectionError();
      }
    }
  }

  private void connectionError()
  {
    close();
  }

  public String getServerName()
  {
    return serverName;
  }

  public AppletGFXCMD getGfxCmd() { return myGfx; }

  private java.net.Socket gfxSocket;
  private String serverName;
  private int port;
  private String myID;

  // This is the secret symmetric key encrypted with the public key
  private byte[] encryptedSecretKeyBytes;
  private java.security.PublicKey serverPublicKey;
  private javax.crypto.Cipher evtEncryptCipher;

  private boolean encryptEvents;

  private java.io.DataOutputStream eventChannel;
  private int replyCount;
  private AppletGFXCMD myGfx;

  private boolean alive;

  private String currentCrypto = null;//MiniClient.cryptoFormats;
}
