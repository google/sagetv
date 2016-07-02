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

public class NetworkEncoderManager implements CaptureDeviceManager
{
  public NetworkEncoderManager()
  {
    capDevs = new CaptureDevice[0];
  }

  public void detectCaptureDevices(CaptureDevice[] alreadyDetectedDevices)
  {
    String[] encoderKeys = Sage.childrenNames(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/');
    java.util.ArrayList vec = new java.util.ArrayList();
    for (int i = 0; i < encoderKeys.length; i++)
    {
      String devClassProp = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
          CaptureDevice.DEVICE_CLASS, "");
      if (devClassProp.length() > 0 && !devClassProp.equals("NetworkEncoder") && !devClassProp.equals("Multicast"))
        continue;
      String hostProp = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
          NetworkCaptureDevice.ENCODING_HOST, "");
      if (hostProp.length() > 0)
      {
        if (CaptureDevice.isDeviceAlreadyUsed(alreadyDetectedDevices,
            Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
                CaptureDevice.VIDEO_CAPTURE_DEVICE_NAME, ""), Sage.getInt(MMC.MMC_KEY + '/' +
                    CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' + CaptureDevice.VIDEO_CAPTURE_DEVICE_NUM, 0)))
          continue;
        String multicast = Sage.get(MMC.MMC_KEY + '/' + CaptureDevice.ENCODERS + '/' + encoderKeys[i] + '/' +
            MulticastCaptureDevice.MULTICAST_HOST, "");
        if (multicast.length() > 0 || devClassProp.equals("Multicast"))
        {
          try
          {
            MulticastCaptureDevice testEncoder = new MulticastCaptureDevice(Integer.parseInt(encoderKeys[i]));
            vec.add(testEncoder);
          }
          catch (Exception e){System.out.println("ERROR creating capture device:" + e);}
        }
        else
        {
          try
          {
            NetworkCaptureDevice testEncoder = new NetworkCaptureDevice(Integer.parseInt(encoderKeys[i]));
            vec.add(testEncoder);
          }
          catch (NumberFormatException e){}
        }
      }
    }

    if (Sage.getBoolean("network_encoder_discovery", true))
    {
      // Now do the broadcast discovery of new encoding servers on the network
      if (Sage.DBG) System.out.println("Doing broadcast discovery of new encoding servers on the network...");
      java.util.ArrayList servers = new java.util.ArrayList();
      java.net.DatagramSocket sock = null;
      try
      {
        try
        {
          sock  = new java.net.DatagramSocket(Sage.getInt("discovery_port", 8270));
        }
        catch (java.net.BindException be1)
        {
          // Try again on the encoder discovery port which is much less likely to be in use
          try
          {
            sock = new java.net.DatagramSocket(Sage.getInt("encoding_discovery_port", 8271));
          }
          catch (java.net.BindException be2)
          {
            // Just create it wherever
            sock = new java.net.DatagramSocket();
          }
        }
        java.net.DatagramPacket pack = new java.net.DatagramPacket(new byte[512], 512);
        byte[] data = pack.getData();
        data[0] = 'S';
        data[1] = 'T';
        data[2] = 'N';
        data[3] = Sage.ENCODER_COMPATIBLE_MAJOR_VERSION;
        data[4] = Sage.ENCODER_COMPATIBLE_MINOR_VERSION;
        data[5] = Sage.ENCODER_COMPATIBLE_MICRO_VERSION;
        pack.setLength(32);
        sock.setBroadcast(true);
        // Find the broadcast address for this subnet.
        //				String myIP = SageTV.api("GetLocalIPAddress", new Object[0]).toString();
        //				int lastIdx = myIP.lastIndexOf('.');
        //				myIP = myIP.substring(0, lastIdx) + ".255";
        pack.setAddress(java.net.InetAddress.getByName("255.255.255.255"));
        pack.setPort(Sage.getInt("encoding_discovery_port", 8271));
        sock.send(pack);
        long startTime = Sage.eventTime();
        long discoveryTimeout = Sage.getInt("encoding_discovery_timeout", 3000);
        do
        {
          int currTimeout = (int)Math.max(1, (startTime + discoveryTimeout) - Sage.eventTime());
          sock.setSoTimeout(currTimeout);
          sock.receive(pack);
          if (pack.getLength() >= 9)
          {
            if (Sage.DBG) System.out.println("Discovery packet received:" + pack);
            if (data[0] == 'S' && data[1] == 'T' && data[2] == 'N')
            {
              // Check version
              NetworkClient.ServerInfo si = new NetworkClient.ServerInfo();
              si.majorVer = data[3];
              si.minorVer = data[4];
              si.buildVer = data[5];
              if (si.majorVer > Sage.ENCODER_COMPATIBLE_MAJOR_VERSION || (si.majorVer == Sage.ENCODER_COMPATIBLE_MAJOR_VERSION &&
                  (si.minorVer < Sage.ENCODER_COMPATIBLE_MINOR_VERSION || (si.minorVer == Sage.ENCODER_COMPATIBLE_MINOR_VERSION &&
                  si.buildVer == Sage.ENCODER_COMPATIBLE_MICRO_VERSION))))
              {
                si.port = ((data[6] & 0xFF) << 8) + (data[7] & 0xFF);
                int descLength = (data[8] & 0xFF);
                si.name = new String(data, 9, descLength, Sage.I18N_CHARSET);
                si.address = pack.getAddress().getHostName();
                if (Sage.DBG) System.out.println("Added server info:" + si);
                servers.add(si);
              }
            }
          }
        } while (startTime + discoveryTimeout > Sage.eventTime());
      }
      catch (Exception e)
      {
        System.out.println("Error discovering servers:" + e);
      }
      finally
      {
        if (sock != null)
        {
          try
          {
            sock.close();
          }catch (Exception e){}
          sock = null;
        }
      }

      // Now we know where the encoding servers are at!!!!!!!!!
      // Get their properties and see if we don't know about them and then if we don't add them to the list
      // to complete the discovery!
      for (int i = 0; i < servers.size(); i++)
      {
        NetworkClient.ServerInfo si = (NetworkClient.ServerInfo) servers.get(i);
        // Get the properties for this network encoder, this is a map of maps since we might
        // need to change the encoder ID that is used on the remote machine
        java.util.Map encoderProps = new java.util.HashMap();
        java.net.Socket s = null;
        java.io.DataOutputStream os = null;
        java.io.DataInputStream is = null;
        try
        {
          s = new java.net.Socket(si.address, si.port);
          if (Sage.DBG) System.out.println("Connected to encoding server at " + si.address);
          s.setSoTimeout(Sage.getInt("mmc/net_encoding_timeout", 15000));
          //s.setTcpNoDelay(true);
          os = new java.io.DataOutputStream(new java.io.BufferedOutputStream(s.getOutputStream()));
          is = new java.io.DataInputStream(new java.io.BufferedInputStream(s.getInputStream()));
          os.write("PROPERTIES\r\n".getBytes(Sage.BYTE_CHARSET));
          os.flush();
          String propCount = Sage.readLineBytes(is);
          if (propCount != null)
          {
            int numProps = Integer.parseInt(propCount);
            String currProp = null;
            for (int j = 0; j < numProps; j++)
            {
              currProp = Sage.readLineBytes(is);
              int eqIdx = currProp.indexOf('=');
              if (eqIdx != -1)
              {
                int propSlashIdx = currProp.indexOf('/', "mmc/encoders/".length());
                String currEncoderID = currProp.substring("mmc/encoders/".length(), propSlashIdx);
                java.util.Map subMap = (java.util.Map) encoderProps.get(currEncoderID);
                if (subMap == null)
                  encoderProps.put(currEncoderID, subMap = new java.util.HashMap());
                subMap.put(currProp.substring(propSlashIdx + 1, eqIdx), currProp.substring(eqIdx + 1));
              }
            }
          }
          os.write("QUIT\r\n".getBytes(Sage.BYTE_CHARSET));
          os.flush();
        }
        catch (Exception e)
        {
          System.out.println("ERROR in network encoder server discovery of: " + e);
          e.printStackTrace();
        }
        finally
        {
          if (os != null)
          {
            try{os.close();}catch(Exception e){}
            os = null;
          }
          if (is != null)
          {
            try{is.close();}catch(Exception e){}
            is = null;
          }
          if (s != null)
          {
            try{s.close();}catch(Exception e){}
            s = null;
          }
        }

        java.util.Iterator walker = encoderProps.entrySet().iterator();
        while (walker.hasNext())
        {
          java.util.Map.Entry currEnt = (java.util.Map.Entry) walker.next();
          String currEncoderID = currEnt.getKey().toString();
          java.util.Map currEncoderProps = (java.util.Map) currEnt.getValue();
          // Check to make sure we haven't loaded this device already
          String encoderName = currEncoderProps.get(CaptureDevice.VIDEO_CAPTURE_DEVICE_NAME).toString();
          int encoderNum;
          try
          {
            encoderNum = Integer.parseInt(currEncoderProps.get(CaptureDevice.VIDEO_CAPTURE_DEVICE_NUM).toString());
          }catch (NumberFormatException e)
          {
            System.out.println("ERROR in encoder props:" + e);
            continue;
          }
          String encoderHost = (currEncoderProps.containsKey(NetworkCaptureDevice.ENCODING_HOST) && Sage.WINDOWS_OS) ?
              currEncoderProps.get(NetworkCaptureDevice.ENCODING_HOST).toString() :
                ((Sage.WINDOWS_OS ? si.name : si.address) + ":" + si.port);
              boolean deviceMatched = false;
              for (int j = 0; j < vec.size(); j++)
              {
                if ((vec.get(j) instanceof NetworkCaptureDevice) && ((NetworkCaptureDevice) vec.get(j)).isSameCaptureDevice(encoderName, encoderNum, encoderHost))
                {
                  deviceMatched = true;
                  break;
                }
              }
              if (deviceMatched)
              {
                if (Sage.DBG) System.out.println("Ignoring network encoder because it's already in our config: " + currEncoderID);
                continue;
              }
              if (Sage.DBG) System.out.println("NetworkEncoder discovered and is being added to our config: " + encoderName + " " + encoderNum);
              int newEncoderID = NetworkCaptureDevice.createNetworkEncoderID(encoderName, encoderNum, encoderHost);
              // Put all of the encoder properties into our property map
              java.util.Iterator encoderWalker = currEncoderProps.entrySet().iterator();
              while (encoderWalker.hasNext())
              {
                java.util.Map.Entry currEncEnt = (java.util.Map.Entry) encoderWalker.next();
                // Ignore properties for tuning plugins since they won't apply here
                if (currEncEnt.getKey().toString().indexOf(CaptureDeviceInput.TUNING_PLUGIN) == -1)
                  Sage.put("mmc/encoders/" + newEncoderID + "/" + currEncEnt.getKey(), currEncEnt.getValue().toString());
              }
              Sage.put("mmc/encoders/" + newEncoderID + "/" + NetworkCaptureDevice.ENCODING_HOST, encoderHost);
              NetworkCaptureDevice testEncoder = new NetworkCaptureDevice(newEncoderID);
              vec.add(testEncoder);
        }
      }
    }

    capDevs = (CaptureDevice[]) vec.toArray(new CaptureDevice[0]);
  }

  public void freeResources()
  {
    // Stop all of the network encoders if any are running
    for (int i = 0; i < capDevs.length; i++)
      capDevs[i].stopEncoding();
  }

  public CaptureDevice[] getCaptureDevices()
  {
    return capDevs;
  }

  private CaptureDevice[] capDevs;
}
