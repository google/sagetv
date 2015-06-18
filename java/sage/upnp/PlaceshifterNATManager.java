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
package sage.upnp;

import sage.*;

public class PlaceshifterNATManager implements Runnable, net.sbbi.upnp.DiscoveryEventHandler, AbstractedService
{
  /** Creates a new instance of PlaceshifterNATManager */
  public PlaceshifterNATManager()
  {
  }

  public void start()
  {
    alive = true;
    myThread = new Thread(this, "PSNATMGR");
    myThread.setDaemon(true);
    myThread.setPriority(Thread.MIN_PRIORITY);
    myThread.start();
  }

  public void kill()
  {
    alive = false;
    synchronized (waitLock)
    {
      waitLock.notifyAll();
    }
    try
    {
      myThread.join(2000);
    }
    catch (Exception e){}
    if (myRouter != null)
      removeMappings();
    if (setupDiscoveryListener)
    {
      net.sbbi.upnp.DiscoveryAdvertisement instance = net.sbbi.upnp.DiscoveryAdvertisement.getInstance();
      instance.unRegisterEvent(net.sbbi.upnp.DiscoveryAdvertisement.EVENT_SSDP_ALIVE, "upnp:rootdevice", this);
      setupDiscoveryListener = false;
    }
  }

  private boolean usesUPnP()
  {
    return "xUPnP".equals(Sage.get("placeshifter_port_forward_method", null));
  }

  private void setupDiscovery()
  {
    // NOTE: Disable this by default because it wastes lots of memory when it runs because it's
    // very inefficient in how it uses Java/XML/HTTP
    if (Sage.getBoolean("enable_upnp_event_listener", false))
    {
      // Set us up to listen to discovery broadcast of new devices on the network
      try
      {
        net.sbbi.upnp.DiscoveryAdvertisement instance = net.sbbi.upnp.DiscoveryAdvertisement.getInstance();
        instance.setDaemon(true);
        instance.registerEvent(net.sbbi.upnp.DiscoveryAdvertisement.EVENT_SSDP_ALIVE, "upnp:rootdevice", this);
      }
      catch (java.io.IOException e)
      {
        // If this fails I still want to indicate it's setup so we don't keep retrying if it's not going to work
        System.out.println("ERROR setting up UPnP discovery of:" + e);
      }
      setupDiscoveryListener = true;
    }
  }

  public void run()
  {
    if (Sage.DBG) System.out.println("Starting UPnP NAT Manager...");
    // Establish the initial UPnP setup
    if (usesUPnP())
    {
      findMyRouter();
      synchronizeMappings();
      setupDiscovery();
    }

    // Any changes that occur to the UPnP configuration while we are running do NOT need to be handled by us
    // because they are dealt with in the UI itself. We only need to address inconsistencies between the configuration
    // and the state we are seeing. But we do not make any changes while the sync lock is active.
    while (alive)
    {
      if (Sage.getBoolean("placeshifter_port_forward_upnp_active", false) || !usesUPnP())
      {
        synchronized (waitLock)
        {
          try
          {
            waitLock.wait(15*60000);
          }
          catch (InterruptedException e){}
        }
        continue;
      }

      findMyRouter();
      synchronizeMappings();
      if (!setupDiscoveryListener)
        setupDiscovery();
      synchronized (waitLock)
      {
        try
        {
          waitLock.wait(15*60000);
        }
        catch (InterruptedException e){}
      }
    }
  }

  // This finds all UPnP routers that are on our network and sets the allRouters variable
  private void discoverRouters()
  {
    try
    {
      allRouters = net.sbbi.upnp.impls.InternetGatewayDevice.getDevices(5000);
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("Error with UPnP discovery of: " + e);
      allRouters = null;
    }
  }

  // This finds the router that we're setup to configure if it's available on the network. If we can't find a router
  // then we set myRouter to null.
  private void findMyRouter()
  {
    if (myRouter != null)
    {
      String oldExternalIP = myExternalIP;
      try
      {
        myExternalIP = myRouter.getExternalIPAddress();
      }
      catch (Exception e){}
      if (oldExternalIP != myExternalIP && (oldExternalIP == null || !oldExternalIP.equals(myExternalIP)))
      {
        SageTV.forceLocatorUpdate();
      }
      return;
    }
    if (allRouters == null)
      discoverRouters();
    if (allRouters == null || allRouters.length == 0)
      return;

    // See if we can find a router that matches our UDN. If so, then we use it. If not, then we take the first one and
    // use that one anyways. We also update our properties to reflect this change.
    for (int i = 0; i < allRouters.length; i++)
    {
      String currUDN = allRouters[i].getIGDRootDevice().getUDN();
      if (currUDN != null && currUDN.equals(Sage.get("placeshifter_port_forward_upnp_udn", null)))
      {
        myRouter = allRouters[i];
        break;
      }
    }
    if (myRouter == null)
    {
      // Check if the UI is going to override us
      if (Sage.getBoolean("placeshifter_port_forward_upnp_active", false)) return;

      try
      {
        String externalIP = allRouters[0].getExternalIPAddress();
        if (externalIP != null && !externalIP.equals("0.0.0.0"))
        {
          System.out.println("Previuosly configured UPnP router not found on network. It's UDN was:" + Sage.get("placeshifter_port_forward_upnp_udn", null) +
              " SageTV will automatically use the first UPnP router it finds now instead.");
          myRouter = allRouters[0];
          Sage.put("placeshifter_port_forward_upnp_udn", myRouter.getIGDRootDevice().getUDN());
          SageTV.forceLocatorUpdate();
        }
        else
        {
          System.out.println("Cannot find a valid UPnP router on the network anymore!!!");
        }
      }
      catch (Exception e)
      {
        System.out.println("ERROR getting router's external IP of:" + e);
      }
    }
    if (myRouter != null)
    {
      try
      {
        myExternalIP = myRouter.getExternalIPAddress();
      }
      catch (Exception e){}
    }
    else
      allRouters = null; // if we didn't find one then we definitely need to rediscover them all again next time
  }

  // Sets up the port mapping on the router to match what we have in our configuration.
  private void synchronizeMappings()
  {
    if (myRouter == null) return;
    String desiredLocalIP = null;
    try
    {
      if (sage.Sage.WINDOWS_OS || sage.Sage.MAC_OS_X)
        desiredLocalIP = java.net.InetAddress.getLocalHost().getHostAddress();
      else
        desiredLocalIP = LinuxUtils.getIPAddress();
    }
    catch (java.io.IOException e)
    {
      if (Sage.DBG) System.out.println("Unable to setup UPnP mappings because we can't get a local IP address!");
      return;
    }
    int desiredLocalPort = Sage.getInt("extender_and_placeshifter_server_port", 31099);
    int desiredExternalPort = Sage.getInt("placeshifter_port_forward_extern_port", desiredLocalPort);
    if (desiredExternalPort > 0)
      setupMapping(desiredLocalIP, desiredLocalPort, desiredExternalPort, "SageTV", "TCP");

    // Now go through any other custom UPnP port mappings they want us to establish
    // The format is upnp_port_forward_additional_mappings/svcName/portType/externalPort=internalPort
    // for example: upnp_port_forward_additional_mappings/WebServer/TCP/8080=8080
    String[] customServices = Sage.childrenNames("upnp_port_forward_additional_mappings");
    for (int i = 0; i < customServices.length; i++)
    {
      String[] customTypes = Sage.childrenNames("upnp_port_forward_additional_mappings/" + customServices[i]);
      for (int j = 0; j < customTypes.length; j++)
      {
        String currType = customTypes[j];
        if (!currType.equals("TCP") && !currType.equals("UDP"))
        {
          System.out.println("Bad port type in properties configuration of:" + currType);
          continue;
        }
        String[] customExternalPorts = Sage.keys("upnp_port_forward_additional_mappings/" + customServices[i] + "/" + currType);
        for (int k = 0; k < customExternalPorts.length; k++)
        {
          int currExtPort;
          try
          {
            currExtPort = Integer.parseInt(customExternalPorts[k]);
          }
          catch (NumberFormatException e)
          {
            System.out.println("Skipping custom port mapping for " + customServices[i] + " of " + customExternalPorts[k] + " due to bad formatting.");
            continue;
          }
          int currIntPort = Sage.getInt("upnp_port_forward_additional_mappings/" + customServices[i] + "/" + currType + "/" +
              currExtPort, 0);
          if (currIntPort > 0 && currExtPort > 0)
          {
            setupMapping(desiredLocalIP, currIntPort, currExtPort, customServices[i], currType);
          }
        }
      }
    }
  }

  private boolean setupMapping(String desiredLocalIP, int desiredLocalPort, int desiredExternalPort, String svcName, String portType)
  {
    if (myRouter == null) return false;
    // Now get the mapping and see if it's correct
    net.sbbi.upnp.messages.ActionResponse resp = null;
    try
    {
      resp = myRouter.getSpecificPortMappingEntry(null, desiredExternalPort, "TCP");
    }
    catch (Exception e)
    {
      System.out.println("UPnP ERROR:" + e);
      e.printStackTrace();
      // NOTE: Narflex has seen this happen on his router during normal operation
    }
    try
    {
      if (resp != null)
      {
        String currPort = resp.getOutActionArgumentValue("NewInternalPort");
        String currIP = resp.getOutActionArgumentValue("NewInternalClient");
        if (currPort != null && currPort.equals(Integer.toString(desiredLocalPort)) && currIP != null &&
            currIP.equals(desiredLocalIP))
        {
          // Our UPnP port mappings are already setup correctly!
          //if (Sage.DBG) System.out.println("UPnP port mappings were verified on router to be correct!");
          return true;
        }
        // Check if the UI is going to override us
        if (Sage.getBoolean("placeshifter_port_forward_upnp_active", false)) return true;

        // Double-check to make sure the port is really not setup. Sometime they don't appear in getSpecificPortMappingEntry because
        // they're on a predefined port. But they'll show up in the generic list hopefully in that case (at least they do for my router).
        int portIdx = 0;
        while (true)
        {
          try
          {
            net.sbbi.upnp.messages.ActionResponse genResp = myRouter.getGenericPortMappingEntry(portIdx);
            if (genResp == null) break;
            currPort = genResp.getOutActionArgumentValue("NewInternalPort");
            currIP = genResp.getOutActionArgumentValue("NewInternalClient");
            if (currPort != null && currPort.equals(Integer.toString(desiredLocalPort)) && currIP != null &&
                currIP.equals(desiredLocalIP))
            {
              // Our UPnP port mappings are already setup correctly!
              //if (Sage.DBG) System.out.println("UPnP port mappings were verified on router to be correct!");
              return true;
            }
          }
          catch (Exception e)
          {
            // beyond the end of the list most likely
            break;
          }
          portIdx++;
        }

        // There's a port mappings already, but it's not correct for our purpose so remove it and register ours.
        if (Sage.DBG) System.out.println("Removing port mapping for:" + resp);
        myRouter.deletePortMapping(null, desiredExternalPort, portType);
      }
    }
    catch (Exception e)
    {
      System.out.println("UPnP ERROR:" + e);
      e.printStackTrace();
      // NOTE: We should still go on after a failure here to try to establish the mapping; otherwise
      // it may not be setup
    }

    try
    {
      // Check if the UI is going to override us
      if (Sage.getBoolean("placeshifter_port_forward_upnp_active", false)) return true;

      // Now establish our port mapping on the router
      if (myRouter.addPortMapping(svcName, null, desiredLocalPort, desiredExternalPort, desiredLocalIP, 0, portType))
      {
        if (Sage.DBG) System.out.println("Successfully setup UPnP port mapping!");
        return true;
      }
      else
      {
        if (Sage.DBG) System.out.println("UPnP port mapping setup has failed!");
        return false;
      }
    }
    catch (Exception e)
    {
      System.out.println("UPnP ERROR:" + e);
      e.printStackTrace();
      myRouter = null;
      allRouters = null; // Clear the global cache too since that'll be wrong now
      return false;
    }
  }

  private void removeMappings()
  {
    if (myRouter == null) return;
    int desiredExternalPort = Sage.getInt("placeshifter_port_forward_extern_port",
        Sage.getInt("extender_and_placeshifter_server_port", 31099));
    if (desiredExternalPort > 0)
      removeMapping(desiredExternalPort, "TCP");

    // Now go through any other custom UPnP port mappings they want us to establish
    // The format is upnp_port_forward_additional_mappings/svcName/portType/externalPort=internalPort
    // for example: upnp_port_forward_additional_mappings/WebServer/TCP/8080=8080
    String[] customServices = Sage.childrenNames("upnp_port_forward_additional_mappings");
    for (int i = 0; i < customServices.length; i++)
    {
      String[] customTypes = Sage.childrenNames("upnp_port_forward_additional_mappings/" + customServices[i]);
      for (int j = 0; j < customTypes.length; j++)
      {
        String currType = customTypes[j];
        if (!currType.equals("TCP") && !currType.equals("UDP"))
        {
          System.out.println("Bad port type in properties configuration of:" + currType);
          continue;
        }
        String[] customExternalPorts = Sage.keys("upnp_port_forward_additional_mappings/" + customServices[i] + "/" + currType);
        for (int k = 0; k < customExternalPorts.length; k++)
        {
          int currExtPort;
          try
          {
            currExtPort = Integer.parseInt(customExternalPorts[k]);
          }
          catch (NumberFormatException e)
          {
            System.out.println("Skipping custom port mapping for " + customServices[i] + " of " + customExternalPorts[k] + " due to bad formatting.");
            continue;
          }
          if (currExtPort > 0)
          {
            removeMapping(currExtPort, currType);
          }
        }
      }
    }
  }

  private void removeMapping(int desiredExternalPort, String portType)
  {
    try
    {
      myRouter.deletePortMapping(null, desiredExternalPort, portType);
    }
    catch (Exception e)
    {
      System.out.println("UPnP ERROR:" + e);
      e.printStackTrace();
    }
  }

  public void eventSSDPAlive(String usn, String udn, String nt, String maxAge, java.net.URL location)
  {
    synchronized (waitLock)
    {
      waitLock.notifyAll();
    }
  }

  public void eventSSDPByeBye(String usn, String udn, String nt)
  {
  }

  private Thread myThread;
  private boolean alive;

  private net.sbbi.upnp.impls.InternetGatewayDevice[] allRouters;
  private net.sbbi.upnp.impls.InternetGatewayDevice myRouter;
  private Object waitLock = new Object();

  private boolean setupDiscoveryListener;

  private String myExternalIP;
}
