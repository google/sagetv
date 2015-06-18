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

public class MiniServer
{
  private Thread BootpThread;
  private Thread TftpThread;
  private Thread MVPThread;
  private java.net.InetAddress serverAddress;
  private java.net.InetAddress gatewayAddress;
  private int count;

  private boolean alive;
  private java.net.DatagramSocket bootpSocket;
  private java.net.DatagramSocket tftpSocket;
  private java.net.DatagramSocket mvpSocket;

  private int clientCount;
  private byte [] clientMAC;
  private java.net.InetAddress [] clientIP;
  private int [] clientType; // 0:MVP 1:...

  public static final int ADDITIONAL_CLIENTS = 16; // We support at most this
  //many extra clients

  public byte [] parseMAC(String macstr)
  {
    byte [] mac=new byte[6];

    try
    {
      java.util.StringTokenizer ST = new java.util.StringTokenizer(
          macstr, ":", false);
      for(int i=0;i<6;i++)
      {
        if(ST.hasMoreTokens())
          mac[i]=(byte) Integer.parseInt(ST.nextToken(), 16);
        else
          return null;
      }
    }
    catch(Exception e)
    {
      e.printStackTrace();
      return null;
    }
    return mac;
  }

  public String MAC2STR(byte [] mac, int offset)
  {
    String str = "";
    for(int i=0;i<6;i++)
    {
      int val=mac[offset+i]&0xFF;
      if(val<0x10) str+="0";
      if(val==0)
      {
        str+="0";
      }
      else
      {
        str+=Integer.toHexString(val);
      }
      if(i<5)
        str+=":";
    }
    return str;
  }
  public MiniServer()
  {
    try
    {
      // For now use first lan address ...
      if (Sage.DBG) System.out.println("Trying to find lan network interface");
      String prefServerIP = Sage.get("miniserver/forced_server_ip", "");
      if (prefServerIP != null && prefServerIP.trim().length() == 0)
        prefServerIP = null;
      try
      {
        java.util.Enumeration interfaceEnum = java.net.NetworkInterface.getNetworkInterfaces();

        // First check for the preferred server IP address
        while (interfaceEnum.hasMoreElements() && serverAddress == null && prefServerIP != null)
        {
          java.net.NetworkInterface firstIf = (java.net.NetworkInterface) interfaceEnum.nextElement();
          java.util.Enumeration addressEnum = firstIf.getInetAddresses();
          while (addressEnum.hasMoreElements())
          {
            serverAddress = (java.net.InetAddress) addressEnum.nextElement();
            if (Sage.DBG) System.out.println("addr: "+serverAddress);
            if (prefServerIP.equals(serverAddress.getHostAddress()))
            {
              if (Sage.DBG) System.out.println("Found matching server interface of " + prefServerIP);
              break;
            }
            else
              serverAddress = null;
          }
        }
        if (serverAddress == null)
        {
          interfaceEnum = java.net.NetworkInterface.getNetworkInterfaces();
          while (interfaceEnum.hasMoreElements() && serverAddress == null)
          {
            java.net.NetworkInterface firstIf = (java.net.NetworkInterface) interfaceEnum.nextElement();
            // Ignore virtual network adapters from VMWare
            if (Sage.WINDOWS_OS && firstIf.getDisplayName().toLowerCase().indexOf("vmware") != -1)
              continue;
            java.util.Enumeration addressEnum = firstIf.getInetAddresses();
            while (addressEnum.hasMoreElements())
            {
              serverAddress = (java.net.InetAddress) addressEnum.nextElement();
              if (Sage.DBG) System.out.println("addr: "+serverAddress);
              if( (serverAddress.getAddress()[0]&0xFF)==10 ||
                  ((serverAddress.getAddress()[0]&0xFF)==172 &&
                  (serverAddress.getAddress()[1]&0xFF)>=16 &&
                  (serverAddress.getAddress()[1]&0xFF)<=31) ||
                  ((serverAddress.getAddress()[0]&0xFF)==192 &&
                  (serverAddress.getAddress()[1]&0xFF)==168))
              {
                break;
              }
              else
              {
                serverAddress=null;
              }
            }
          }
        }
        if(serverAddress==null) // Try again using also public ip space
        {
          if (Sage.DBG) System.out.println("Couldn't find private ip, trying public");
          interfaceEnum = java.net.NetworkInterface.getNetworkInterfaces();
          while (interfaceEnum.hasMoreElements() && serverAddress == null)
          {
            java.net.NetworkInterface firstIf = (java.net.NetworkInterface) interfaceEnum.nextElement();
            if (Sage.WINDOWS_OS && firstIf.getDisplayName().toLowerCase().indexOf("vmware") != -1)
              continue;
            java.util.Enumeration addressEnum = firstIf.getInetAddresses();
            while (addressEnum.hasMoreElements())
            {
              serverAddress = (java.net.InetAddress) addressEnum.nextElement();
              if (Sage.DBG) System.out.println("addr: "+serverAddress);
              if( ((serverAddress.getAddress()[0]&0xFF)==127 &&
                  (serverAddress.getAddress()[1]&0xFF)==0 &&
                  (serverAddress.getAddress()[2]&0xFF)==0 &&
                  (serverAddress.getAddress()[3]&0xFF)==1 ))
              {
                serverAddress=null;
              }
              else
              {
                break;
              }
            }
          }

        }
      }
      catch(java.net.SocketException e)
      {
        System.out.println("Couldn't start MiniServer, no valid server address");
        System.out.println(e);
        //e.printStackTrace();
        return;
      }

      if (serverAddress == null)
      {
        System.out.println("Couldn't start MiniServer, no non-loopback IP addresses found");
        return;
      }

      String servername=serverAddress.getHostAddress();
      if (Sage.DBG) System.out.println("Miniserver running on " + servername);

      //serverAddress = java.net.InetAddress.getByName("192.168.0.1");

      // Get in Sage.properties...
      gatewayAddress = java.net.InetAddress.getByName(
          Sage.get("miniserver/gateway", "192.168.0.1"));

      // Get in Sage.properties...
      // MVP/00:0D:FE:00:A0:4E/192.168.0.111
      String StaticClients = Sage.get("miniserver/clients","");

      java.util.StringTokenizer ST = new java.util.StringTokenizer(StaticClients, ";", false);
      clientCount = ST.countTokens()+ADDITIONAL_CLIENTS;

      clientMAC = new byte [6*clientCount];
      clientIP = new java.net.InetAddress [clientCount];
      clientType = new int [clientCount];

      clientCount=0;
      String token,token2;
      while(ST.hasMoreTokens())
      {
        token = ST.nextToken();
        java.util.StringTokenizer ST2 = new java.util.StringTokenizer(token, "/", false);
        String type, mac, ip;
        if(ST2.hasMoreTokens())
          type = ST2.nextToken();
        else
          continue;
        if(ST2.hasMoreTokens())
          mac = ST2.nextToken();
        else
          continue;
        if(ST2.hasMoreTokens())
          ip = ST2.nextToken();
        else
          continue;
        byte macb[] = parseMAC(mac);
        if(macb!=null)
        {
          System.arraycopy(macb, 0, clientMAC, clientCount*6, 6);
          clientIP[clientCount] = java.net.InetAddress.getByName(ip);
          clientType[clientCount] = 0;
          clientCount+=1;
        }
      }
      if (Sage.DBG) System.out.println("Parsed "+clientCount+" clients");
      //clientMAC = new byte[6]
    }
    catch(java.net.UnknownHostException e)
    {
      System.out.println(e);
      //e.printStackTrace();
    }
    //macAddress = new byte[0];
  }

  public byte[] bootpReplyPacket(byte [] request, int len, byte [] reply)
  {
    int opcode = convUByte(request, 0);
    if(opcode!=1) return null; // Not a BOOTREQUEST
    int hwtype = convUByte(request, 1);
    if(hwtype!=1) return null; // Not on ethernet (do we really care?)
    int hwaddrlen = convUByte(request, 2);
    if(hwaddrlen!=6) return null;
    int hopcount = convUByte(request, 3);
    int transactionId = convInt(request, 4);
    int nSeconds = convUShort(request, 8);
    int flags = convUShort(request, 10);
    if (Sage.DBG) System.out.println("Op:"+ opcode + " hwtype:" + hwtype +
        " hwaddrlen:" + hwaddrlen + "hopcount:" + hopcount + "\n" +
        "transaction:"+ transactionId + " nSeconds:" + nSeconds + " flags:" + flags + "\n" +
        "MAC: "+ MAC2STR(request,12+16) );

    // If it doesn't match our MAC/IP db then only reply if it's a likely MVP and already has IP

    int foundClient = -1;
    for(int i=0;i<clientCount;i++)
    {
      if(request[12+16]==clientMAC[i*6+0] &&
          request[12+17]==clientMAC[i*6+1] &&
          request[12+18]==clientMAC[i*6+2] &&
          request[12+19]==clientMAC[i*6+3] &&
          request[12+20]==clientMAC[i*6+4] &&
          request[12+21]==clientMAC[i*6+5])
      {
        if (Sage.DBG) System.out.println("Matched request to client "+i);
        foundClient=i;
      }
    }

    if(foundClient == -1 &&
        (request[12+16]&0xFF)==0x00 &&
        (request[12+17]&0xFF)==0x0D &&
        (request[12+18]&0xFF)==0xFE &&
        clientCount<clientIP.length &&
        Sage.getBoolean("miniserver/automvp",true))
    {
      if(clientCount<clientIP.length)
      {
        // This MAC address is assigned to hauppauge
        System.arraycopy(request, 12+16, clientMAC, clientCount*6, 6);
        clientIP[clientCount] = null;
        clientType[clientCount] = 0;
        foundClient=clientCount;
        clientCount+=1;
        if (Sage.DBG) System.out.println("Added MVP client to our bootp list "+MAC2STR(request,12+16));
      }
      else
      {
        if (Sage.DBG) System.out.println("Out of bootp entries");
      }
    }

    if(foundClient==-1)
    {
      if (Sage.DBG) System.out.println("Couldn't match client " +MAC2STR(request,12+16));
      reply=null;
      return null;
    }

    System.arraycopy(request, 0, reply, 0, 44);

    reply[0]=2; // Reply

    if(clientIP[clientCount] != null)
    {
      reply[16]=clientIP[foundClient].getAddress()[0];
      reply[17]=clientIP[foundClient].getAddress()[1];
      reply[18]=clientIP[foundClient].getAddress()[2];
      reply[19]=clientIP[foundClient].getAddress()[3];
      reply[24]=gatewayAddress.getAddress()[0];
      reply[25]=gatewayAddress.getAddress()[1];
      reply[26]=gatewayAddress.getAddress()[2];
      reply[27]=gatewayAddress.getAddress()[3];
    }
    else
    {
      if(reply[12]==0)
      {
        if (Sage.DBG) System.out.println("Warning, request from "+MAC2STR(request,12+16)+" doesn't have IP.");
        return null;
      }
    }

    reply[20]=serverAddress.getAddress()[0];
    reply[21]=serverAddress.getAddress()[1];
    reply[22]=serverAddress.getAddress()[2];
    reply[23]=serverAddress.getAddress()[3];

    String servername=serverAddress.getHostAddress();
    if (Sage.DBG) System.out.println("servername " + servername);
    byte [] c=null;
    try
    {
      c=servername.getBytes("ISO8859_1");
    }
    catch(Exception e)
    {
      e.printStackTrace();
    }
    System.arraycopy(c, 0, reply, 44, c.length);
    reply[44+c.length]=0;

    String filename="mvp.bin";
    byte [] b=null;
    try
    {
      b=filename.getBytes("ISO8859_1");
    }
    catch(Exception e)
    {
      e.printStackTrace();
    }
    System.arraycopy(b, 0, reply, 108, b.length);
    reply[108+b.length]=0;
    reply[108+128]=0x63;
    reply[108+129]=(byte)0x82;
    reply[108+130]=0x53;
    reply[108+131]=0x63;
    reply[108+132]=0x35;
    reply[108+133]=0x1;
    reply[108+134]=0x2;
    reply[108+135]=(byte)0xFF;

    if(foundClient!=-1)
    {
      return new byte[] { (byte)serverAddress.getAddress()[0],(byte)serverAddress.getAddress()[1],(byte)serverAddress.getAddress()[2],(byte)255};
    }
    return new byte[] { (byte)reply[12],(byte)reply[13],(byte)reply[14],(byte)reply[15] };
  }


  public byte[] mvpReplyPacket(byte [] request, int len, byte [] reply)
  {
    int command = convInt(request, 0);
    if(command!=1) return null; // Not a BOOTREQUEST
    int id1 = convUShort(request, 4);
    int id2 = convUShort(request, 6);

    reply[3]=1;

    reply[4]=request[6];
    reply[5]=request[7];
    reply[6]=request[4];
    reply[7]=request[5];

    // UPDATE Haup's latest firmware requires the MAC address from the server
    byte[] macAddr = IOUtils.getMACAddress();
    if (macAddr != null)
      System.arraycopy(macAddr, 0, reply, 8, macAddr.length);

    reply[16]=request[16];
    reply[17]=request[17];
    reply[18]=request[18];
    reply[19]=request[19];
    reply[20]=8; // port 2048
    reply[21]=0;

    reply[24]=serverAddress.getAddress()[0];
    reply[25]=serverAddress.getAddress()[1];
    reply[26]=serverAddress.getAddress()[2];
    reply[27]=serverAddress.getAddress()[3];
    reply[28]=0x16;
    reply[29]=(byte)0xAE;

    reply[32]=serverAddress.getAddress()[0];
    reply[33]=serverAddress.getAddress()[1];
    reply[34]=serverAddress.getAddress()[2];
    reply[35]=serverAddress.getAddress()[3];
    reply[36]=0x18;
    reply[37]=(byte)0xC1;

    reply[44]=serverAddress.getAddress()[0];
    reply[45]=serverAddress.getAddress()[1];
    reply[46]=serverAddress.getAddress()[2];
    reply[47]=serverAddress.getAddress()[3];
    reply[48]=0x41;
    reply[49]=(byte)0xF6;
    return new byte[] { (byte)reply[16],(byte)reply[17],(byte)reply[18],(byte)reply[19] };
  }


  public class tftpReplyThread extends Thread
  {
    byte [] request;
    int len;
    int port;
    java.net.InetAddress addr;

    public tftpReplyThread(byte [] request, int len, int port, java.net.InetAddress addr)
    {
      this.request=new byte [request.length];
      System.arraycopy(request, 0, this.request, 0, request.length);
      this.len=len;
      this.port=port;
      this.addr=addr;
    }

    public void run()
    {
      java.io.InputStream tftpStream=null;
      try
      {
        byte [] reply = null;
        int replyLength = 0;
        java.net.DatagramSocket tftpSocket=new java.net.DatagramSocket();
        byte[] udpBufferIn = null;
        java.net.DatagramPacket tftpPacketOut = null;
        java.net.DatagramPacket tftpPacketIn=null;
        boolean done = false;
        int error = 0;
        int readingfile = 0;
        int lastblock = 0;
        java.io.File tftpFile=null;
        byte [] databuffer = new byte[512];
        int datalen = 0;
        int blockn = 1;
        int retry = 0;
        String blckSizeUpdate = null;
        while(!done)
        {
          if(request[0]!=0) return;
          //System.out.println("request "+request[1]);
          switch(request[1])
          {
            case 1: // Read request
              int fnamelen=0;
              while(request[2+fnamelen]!=0) fnamelen++;
              String filename=new String(request, 2, fnamelen);
              int modelen=0;
              while(request[2+fnamelen+1+modelen]!=0) modelen++;
              String mode=new String(request, 2+fnamelen+1, modelen);
              if (Sage.DBG) System.out.println("Reading " + filename + " in mode " + mode);
              if (len > 4 + fnamelen + modelen + 9)
              {
                if (Sage.DBG) System.out.println("Extra information is in TFTP packet....get it out...");
                int tftpOptLen = 0;
                while (request[4+fnamelen+modelen+tftpOptLen] != 0) tftpOptLen++;
                String optType = new String(request, 4 + fnamelen + modelen, tftpOptLen);
                if ("blksize".equals(optType))
                {
                  int blkSizeLen = 0;
                  while (request[5+fnamelen+modelen+tftpOptLen+blkSizeLen] != 0) blkSizeLen++;
                  String blkSizeStr = new String(request, 5 + fnamelen + modelen + tftpOptLen, blkSizeLen);
                  int blkSize = Integer.parseInt(blkSizeStr);
                  if (Sage.DBG) System.out.println("TFTP block size specified of: " + blkSize);
                  databuffer = new byte[blkSize];
                  blckSizeUpdate = blkSizeStr;
                }
                else
                {
                  if (Sage.DBG) System.out.println("Unknown TFTP option specified of: " + optType);
                }
              }
              if(filename.compareTo("dongle.bin")==0) filename="mvp.bin";
              if (filename.equals("dongle.bin.ver"))
              {
                // this is the 40 bytes of data at address 0x34
                tftpFile = new java.io.File(Sage.getPath("core","mvp.bin"));
                if (tftpStream != null) tftpStream.close();
                tftpStream = new java.io.FileInputStream(tftpFile);
                tftpStream.skip(0x34);
                byte[] binVerBytes = new byte[40];
                tftpStream.read(binVerBytes);
                tftpStream.close();
                tftpStream = new java.io.ByteArrayInputStream(binVerBytes);
              }
              else if (filename.equals("mvp.bin") || filename.equals("stp300.bin") || filename.equals("stp300.ver") ||
                  filename.equals("stx1000.bin") || filename.equals("stp200.bin"))
              {
                if (Sage.DBG) System.out.println("Opening file");
                tftpFile = new java.io.File(Sage.getPath("core",filename));
                if (tftpStream != null) tftpStream.close();
                tftpStream = new java.io.FileInputStream(tftpFile);
              }
              else
                return;
              readingfile = 1;
              datalen = tftpStream.read(databuffer);
              if(datalen < 0) datalen=0;
              if(datalen < databuffer.length) lastblock = 1;
              retry = 0;
              if (filename.equals("dongle.bin.ver")) retry=999; // don't retry on that file
              blockn = 1; // TFTP starts at 1!
              break;
            case 2: // Write request
              error = 4;
              break;
            case 3: // Data
              error = 4;
              break;
            case 4: // Acknowledgement
              if(((request[2]&0xFF)<<8 | (request[3]&0xFF) ) == blockn)
              {
                //                            System.out.println("Received ack for block "+blockn);
                blockn++;
                retry = 0;
                if(lastblock==0)
                {
                  datalen = tftpStream.read(databuffer);
                  if(datalen < 0) datalen=0;
                  if(datalen < databuffer.length)
                  {
                    if (Sage.DBG) System.out.println("At last block");
                    lastblock = 1;
                  }
                }
                else
                {
                  return;
                }
              }
              break;
            case 5: // Error
              error = 4;
              break;
            default:
              if (Sage.DBG) System.out.println("TFTP unknown opcode " + request[1]);
          }

          if(error!=0)
          {
            if (reply == null || reply.length < 5)
            {
              reply = new byte[5];
              if (tftpPacketOut != null)
                tftpPacketOut.setData(reply);
            }
            replyLength = 5;
            reply[0] = 0;
            reply[1] = 5;
            reply[2] = 0;
            reply[3] = (byte)error;
            reply[4] = 0;
            if (Sage.DBG) System.out.println("TFTP error " + error);
          }
          else if (blckSizeUpdate != null)
          {
            if (reply == null || reply.length < datalen+4)
            {
              reply = new byte[datalen+4];
              if (tftpPacketOut != null)
                tftpPacketOut.setData(reply);
            }
            replyLength = 2 + 8 + blckSizeUpdate.length() + 1;
            reply[0] = 0;
            reply[1] = 6; // OACK
            System.arraycopy("blksize".getBytes(), 0, reply, 2, 7);
            reply[9] = 0;
            System.arraycopy(blckSizeUpdate.getBytes(), 0, reply, 10, blckSizeUpdate.length());
            reply[9 + blckSizeUpdate.length() + 1] = 0;
            blckSizeUpdate = null;
          }
          else if(readingfile!=0)
          {
            if (reply == null || reply.length < datalen+4)
            {
              reply = new byte[datalen+4];
              if (tftpPacketOut != null)
                tftpPacketOut.setData(reply);
            }
            replyLength = datalen+4;
            System.arraycopy(databuffer, 0, reply, 4, datalen);
            reply[0]=0;
            reply[1]=3; // DATA
            reply[2]=(byte) ((blockn>>8)&0xFF);
            reply[3]=(byte) ((blockn)&0xFF);
            //System.out.println("Sending data block " + blockn);
          }

          while(true)
          {
            if (udpBufferIn == null)
              udpBufferIn=new byte[2048]; // Shouldn't exceed single packet size over ethernet...
            retry+=1;
            if (tftpPacketOut == null)
            {
              tftpPacketOut =
                  new java.net.DatagramPacket(reply,
                      replyLength);
            }
            else
              tftpPacketOut.setLength(replyLength);
            tftpPacketOut.setAddress(addr);
            tftpPacketOut.setPort(port);
            //System.out.println("Sending packet");
            //                    printHex(reply, reply.length);

            tftpSocket.send(tftpPacketOut);
            tftpSocket.setSoTimeout(50);
            try
            {
              request=null;
              len=0;
              if (tftpPacketIn == null)
                tftpPacketIn = new java.net.DatagramPacket(udpBufferIn,2048);
              else
                tftpPacketIn.setLength(2048);
              tftpSocket.receive(tftpPacketIn);
              //                        System.out.println("Received tftp packet\n");
              request=tftpPacketIn.getData();
              len=tftpPacketIn.getLength();
              //                        printHex(request, len);
              retry=0;
              break;
            }
            catch(java.net.SocketTimeoutException ste)
            {
              if (Sage.DBG) System.out.println("TFTP timeout\n");
              if(retry>100)
              {
                if (Sage.DBG) System.out.println("Retry count exceeded\n");
                return;
              }
              continue;
            }
          }
        }
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
      finally
      {
        if (tftpStream != null)
        {
          try{tftpStream.close();}catch(Exception e){}
          tftpStream = null;
        }
      }
    }
  }

  public boolean StartServer()
  {
    if (serverAddress == null) return false;
    alive = true;
    BootpThread = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          byte[] udpBufferIn=new byte[2048]; // Shouldn't exceed single packet size over ethernet...

          bootpSocket=new java.net.DatagramSocket(16867/*67*/);
          bootpSocket.setBroadcast(true);
          if (Sage.DBG) System.out.println(bootpSocket.getLocalAddress() + " " + bootpSocket.getLocalPort());
          bootpSocket.setSoTimeout(5000);
          java.net.DatagramPacket bootpPacketIn=new java.net.DatagramPacket(udpBufferIn,2048);
          while (alive)
          {
            try
            {
              bootpPacketIn.setLength(2048);
              bootpSocket.receive(bootpPacketIn);
              if (Sage.DBG) System.out.println("Received bootp request");
              byte [] request=bootpPacketIn.getData();
              int len=bootpPacketIn.getLength();
              //                            printHex(request, len);
              byte [] reply = new byte[len];
              byte [] replyaddr = bootpReplyPacket(request, len, reply);
              if(replyaddr != null)
              {
                java.net.DatagramPacket bootpPacketOut =
                    new java.net.DatagramPacket(reply,
                        reply.length);
                bootpPacketOut.setAddress(java.net.InetAddress.getByAddress(replyaddr));
                bootpPacketOut.setPort(16868 /*68*/);
                if (Sage.DBG) System.out.println("Sending bootp reply");
                bootpSocket.send(bootpPacketOut);
              }

            }
            catch(java.net.SocketTimeoutException ste)
            {
              //if (Sage.DBG) System.out.println(ste);
            }
            catch (Exception e)
            {
              if (Sage.DBG) System.out.println("MiniError-3:" + e);;
            }
          }
        }
        catch(Exception e)
        {
          if (Sage.DBG) System.out.println(e);
          //e.printStackTrace();
        }
        finally
        {
          if (bootpSocket != null)
          {
            try{bootpSocket.close();}catch(Exception e){}
            bootpSocket = null;
          }
        }
      }
    }, "MiniBootp");
    BootpThread.setDaemon(true);
    BootpThread.setPriority(Thread.MAX_PRIORITY - 3);
    BootpThread.start();

    TftpThread = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          byte[] udpBufferIn=new byte[2048]; // Shouldn't exceed single packet size over ethernet...

          tftpSocket=new java.net.DatagramSocket(16869 /*69*/);
          if (Sage.DBG) System.out.println(tftpSocket.getLocalAddress() + " " + tftpSocket.getLocalPort());
          tftpSocket.setSoTimeout(5000);
          java.net.DatagramPacket tftpPacketIn=
              new java.net.DatagramPacket(udpBufferIn,2048);
          while(alive)
          {
            try
            {
              tftpPacketIn.setLength(2048);
              tftpSocket.receive(tftpPacketIn);
              System.out.println("Received tftp packet\n");
              byte [] request=tftpPacketIn.getData();
              int len=tftpPacketIn.getLength();
              //                            printHex(request, len);
              // TODO: start a thread for that if we want to process multiple requests

              new tftpReplyThread(request, len, tftpPacketIn.getPort(),
                  tftpPacketIn.getAddress()).start();

            }
            catch(java.net.SocketTimeoutException ste)
            {

            }
            catch (Exception e)
            {
              if (Sage.DBG) System.out.println("MiniError-2:" + e);;
            }
          }

        }
        catch(Exception e)
        {
          if (Sage.DBG) System.out.println(e);
          //e.printStackTrace();
        }
        finally
        {
          if (tftpSocket != null)
          {
            try{tftpSocket.close();}catch(Exception e){}
            tftpSocket = null;
          }
        }
      }
    }, "MiniTftp");
    TftpThread.setDaemon(true);
    TftpThread.setPriority(Thread.MAX_PRIORITY - 3);
    TftpThread.start();

    MVPThread = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          byte[] udpBufferIn=new byte[2048]; // Shouldn't exceed single packet size over ethernet...

          mvpSocket=new java.net.DatagramSocket(16881 /*69*/);
          if (Sage.DBG) System.out.println(mvpSocket.getLocalAddress() + " " + mvpSocket.getLocalPort());
          mvpSocket.setSoTimeout(5000);
          java.net.DatagramPacket mvpPacketIn=
              new java.net.DatagramPacket(udpBufferIn,2048);
          while(alive)
          {
            try
            {
              mvpPacketIn.setLength(2048);
              mvpSocket.receive(mvpPacketIn);
              System.out.println("Received mvp packet\n");
              byte [] request=mvpPacketIn.getData();
              int len=mvpPacketIn.getLength();
              //printHex(request, len);
              // TODO: start a thread for that if we want to process multiple requests
              byte [] reply = new byte[len];
              byte [] replyaddr = mvpReplyPacket(request, len, reply);
              if(replyaddr != null)
              {
                java.net.DatagramPacket mvpPacketOut =
                    new java.net.DatagramPacket(reply,
                        reply.length);
                mvpPacketOut.setAddress(java.net.InetAddress.getByAddress(replyaddr));
                mvpPacketOut.setPort(16882 /*68*/);
                if (Sage.DBG) System.out.println("Sending mvp reply");
                mvpSocket.send(mvpPacketOut);
              }
            }
            catch(java.net.SocketTimeoutException ste)
            {

            }
            catch (Exception e)
            {
              if (Sage.DBG) System.out.println("MiniError-1:" + e);;
            }
          }

        }
        catch(Exception e)
        {
          if (Sage.DBG) System.out.println(e);
          //e.printStackTrace();
        }
        finally
        {
          if (mvpSocket != null)
          {
            try{mvpSocket.close();}catch(Exception e){}
            mvpSocket = null;
          }
        }
      }
    }, "MiniMVP");
    MVPThread.setDaemon(true);
    MVPThread.setPriority(Thread.MAX_PRIORITY - 3);
    MVPThread.start();
    return true;
  }

  public void killServer()
  {
    alive = false;
    if (tftpSocket != null)
    {
      try{tftpSocket.close();}catch(Exception e){}
    }
    if (bootpSocket != null)
    {
      try{bootpSocket.close();}catch(Exception e){}
    }
    if (mvpSocket != null)
    {
      try{mvpSocket.close();}catch(Exception e){}
    }
  }

  private static void printHex(byte buffer[], int len)
  {
    byte[] tmpbyte=new byte[1];
    for(int i=0;i<(len+0xF)/0x10;i++)
    {
      if(i<0x10) System.out.print("0"); // Add leading 0
      if(i==0) System.out.print("0"); // Add leading 0
      System.out.print(Integer.toHexString(i*0x10));
      System.out.print(" ");

      for(int j=0;j<16;j++)
      {
        if((i*16+j)<len)
        {
          if(buffer[i*16+j]>31&&buffer[i*16+j]<128)
          {
            tmpbyte[0]=buffer[i*16+j];
            System.out.print(new String(tmpbyte));
          }
          else
          {
            System.out.print(".");
          }
        }
        else
        {
          System.out.print(" ");
        }
      }

      for(int j=0;j<16;j++)
      {
        if((i*16+j)<len)
        {
          System.out.print(" ");
          if(buffer[i*16+j]<0x10&&buffer[i*16+j]>=0)
            System.out.print("0"); // Add leading 0
          if(buffer[i*16+j]<0)
          {
            System.out.print(Integer.toHexString(0x100+buffer[i*16+j]));
          }
          else
          {
            System.out.print(Integer.toHexString(buffer[i*16+j]));
          }
        }
      }
      System.out.print("\n");
    }
  }

  private static int convInt(byte b[], int s)
  {
    int val=( ((int)b[s+3]) & 0xFF)
        + ((((int)b[s+2]) & 0xFF)<<8)
        + ((((int)b[s+1]) & 0xFF)<<16)
        + ((((int)b[s]) & 0x7F)<<24);

    if(b[s]<0) // test negative
    {
      return -2147483648+val;
    }
    else
    {
      return val;
    }
  }

  private static int convUShort(byte b[], int s)
  {
    return ( (((int)b[s+1]) & 0xFF)
        + ((((int)b[s]) & 0xFF)<<8));
  }

  private static int convUByte(byte b[], int s)
  {
    return ((int)b[s]) & 0xFF;
  }

  private static void printHexWord(int val)
  {
    if(val<0x1000) System.out.print("0");
    if(val<0x100) System.out.print("0");
    if(val<0x10) System.out.print("0");
    if(val==0)
    {
      System.out.print("0");
    }
    else
    {
      System.out.print(Integer.toHexString(val));
    }
  }

  /*
    public static void main(String[] args)
    {
        try
        {
            MiniServer ms= new MiniServer();
            ms.StartServer();
            while(true)
            {
                Thread.sleep(500);
            }
        }
        catch(Exception e)
        {
            System.out.println(e);
        }
    }
   */
}
