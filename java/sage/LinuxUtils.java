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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LinuxUtils
{
  public static final String NET_CONFIG_WIRED = "linux/network/wired";
  public static final String NET_CONFIG_DHCP = "linux/network/dhcp";
  public static final String NET_CONFIG_IP_ADDRESS = "linux/network/ip_address";
  public static final String NET_CONFIG_NETMASK = "linux/network/netmask";
  public static final String NET_CONFIG_GATEWAY = "linux/network/gateway";
  public static final String NET_CONFIG_PRIMARY_DNS = "linux/network/primary_dns";
  public static final String NET_CONFIG_SECONDARY_DNS = "linux/network/secondary_dns";
  public static final String NET_CONFIG_SSID = "linux/network/ssid";
  public static final String NET_CONFIG_ENCRYPTION = "linux/network/encryption";
  public static final String NET_CONFIG_ENCRYPTION_KEY = "linux/network/encryption_key";
  public static final String NET_CONFIG_SKIP_SETUP = "linux/network/skip_setup";

  public static final String SAMBA_CONFIG_ENABLE = "samba/enable_server";
  public static final String SAMBA_CONFIG_MACHINE = "samba/machine_name";
  public static final String SAMBA_CONFIG_WORKGROUP = "samba/workgroup";

  public static final int NO_DISC = 0;
  public static final int BLANK_CD = 1;
  public static final int VALID_CD = 2;
  public static final int BLANK_DVD_MINUS_R = 10;
  public static final int BLANK_DVD_PLUS_R = 11;
  public static final int DVD_MINUS_R = 12;
  public static final int DVD_PLUS_R = 13;

  private static boolean ntpdateThreadLaunched = false;
  private static boolean ntpdateWorked = false;
  private static final String[] NTP_SERVERS = { "time-a.nist.gov", "north-america.pool.ntp.org", "utcnist2.colorado.edu", "nist1-la.WiTime.net" };
  public static int reconfigureNetworking()
  {
    // This uses values from the property settings
    if (Sage.getBoolean(NET_CONFIG_WIRED, true))
    {
      // Bring down the wireless interface if it's there
      bringDownWireless();

      setupNetworking(Sage.get("linux/wired_network_port", "eth0"));
    }
    else
    {
      // Bring down the wired interface if it's there
      if (Sage.getBoolean("linux/disable_wired_when_wireless_is_enabled", false))
        bringDownWired();

      // Be sure the wired interface is loaded (it may need to be before it is configured)
      IOUtils.exec2("ifconfig " + Sage.get("linux/wireless_network_port", "eth1") + " up");

      // Setup the wireless networking properties before we try to connect to the network or it won't work
      IOUtils.exec2("iwconfig " + Sage.get("linux/wireless_network_port", "eth1") + " essid " + Sage.get(NET_CONFIG_SSID, "any"));

      String crypto = Sage.get(NET_CONFIG_ENCRYPTION, "WPA");
      if ("None".equals(crypto))
      {
        IOUtils.exec2("iwconfig " + Sage.get("linux/wireless_network_port", "eth1") + " key off");
      }
      else
      {
        // Check if the key is all hex
        String key = Sage.get(NET_CONFIG_ENCRYPTION_KEY, "");
        boolean hexKey = true;
        for (int i = 0; i < key.length(); i++)
        {
          if (Character.digit(key.charAt(i), 16) < 0)
          {
            hexKey = false;
            break;
          }
        }
        if ("WEP".equals(crypto))
        {
          IOUtils.exec2("iwconfig " + Sage.get("linux/wireless_network_port", "eth1") + " key on");
          if (hexKey)
            IOUtils.exec2("iwconfig " + Sage.get("linux/wireless_network_port", "eth1") + " key " + Sage.get(NET_CONFIG_ENCRYPTION_KEY, ""));
          else
            IOUtils.exec2("iwconfig " + Sage.get("linux/wireless_network_port", "eth1") + " key s:" + Sage.get(NET_CONFIG_ENCRYPTION_KEY, ""));
        }
        else // WPA
        {
          // NOT FINISHED YET, we'll need to setup a configuration file for wpa_supplicant and then run it
        }
      }

      setupNetworking(Sage.get("linux/wireless_network_port", "eth1"));
    }
    return 0;
  }

  public static void bringDownWireless()
  {
    if (Sage.DBG) System.out.println("Bringing down wireless interface");
    IOUtils.exec2("ifconfig " + Sage.get("linux/wireless_network_port", "eth1") + " down");
  }
  public static void bringDownWired()
  {
    if (Sage.DBG) System.out.println("Bringing down wired interface");
    IOUtils.exec2("ifconfig " + Sage.get("linux/wired_network_port", "eth0") + " down");
  }
  public static void setupNetworking(String netInterface)
  {
    if (Sage.DBG) System.out.println("Setting up network interface " + netInterface);
    if (Sage.getBoolean(NET_CONFIG_SKIP_SETUP, true)) return;
    if (Sage.getBoolean(NET_CONFIG_DHCP, true))
    {
      // If we started a dhcp daemon for this connection before, we need to kill it
      IOUtils.exec2("dhcpcd " + netInterface + " -k");
      // Wait for the dhcpcd interface to actually go down
      try{Thread.sleep(2000);} catch (Exception e) {}
      // Wireless DHCP can take a little time to establish
      if (Sage.getBoolean(NET_CONFIG_WIRED, true))
        IOUtils.exec2("dhcpcd " + netInterface + " -t 5");
      else
        IOUtils.exec2("dhcpcd " + netInterface + " -t 30");
    }
    else
    {
      String ip = Sage.get(NET_CONFIG_IP_ADDRESS, "");
      int lastdot = ip.lastIndexOf('.');
      String broadcast = "192.168.0.255";
      if (lastdot != -1)
        broadcast = ip.substring(0, lastdot) + ".255";
      IOUtils.exec2("ifconfig " + netInterface + " " + ip + " broadcast " + broadcast + " netmask " +
          Sage.get(NET_CONFIG_NETMASK, "255.255.255.0") + " up");
      IOUtils.exec2("route add default gw " + Sage.get(NET_CONFIG_GATEWAY, ""));
      // Now write out the resolv.conf file
      java.io.PrintWriter outStream = null;
      try
      {
        outStream = new java.io.PrintWriter(new java.io.FileWriter("/etc/resolv.conf"));
        boolean hasDNS = Sage.get(NET_CONFIG_PRIMARY_DNS, "").length() > 0 ||
            Sage.get(NET_CONFIG_SECONDARY_DNS, "").length() > 0;
            if (hasDNS)
            {
              if (Sage.get(NET_CONFIG_PRIMARY_DNS, "").length() > 0)
                outStream.println("nameserver " + Sage.get(NET_CONFIG_PRIMARY_DNS, ""));
              if (Sage.get(NET_CONFIG_SECONDARY_DNS, "").length() > 0)
                outStream.println("nameserver " + Sage.get(NET_CONFIG_SECONDARY_DNS, ""));
            }
            else if (Sage.get(NET_CONFIG_GATEWAY, "").length() > 0)
              outStream.println("nameserver " + Sage.get(NET_CONFIG_GATEWAY, ""));
            outStream.close();
      }
      catch (java.io.IOException e)
      {
        System.out.println("ERROR writing out resolv.conf file: " + e);
      }
    }
  }

  public static String getIPAddress()
  {
    // We can't enumerate the network interfaces because it also includes interfaces
    // that are down. So this might not be are actual IP. The only way we can do it
    // is using ifconfig or native code. This can't be done correctly in Java.

    // List of "known" linux network addresses
    // adding the "configured" one first, may result is 2 hits on that one if it's wrong, and it's
    // in the extended list
    List<String> devices = new ArrayList<>(Arrays.asList(Sage.get("linux/wired_network_port", "eth0"), "eth0","eth1","eno1","br0","docker0"));
    String ip=null;
    for (String dev: devices)
    {
      ip=getIPAddressFromInetInfo(IOUtils.exec(new String[] { "ifconfig", dev }));
      if (ip!=null) return ip;
    }

    System.out.println("Linux: Unable to get IP ADDRESS from " + devices);
    return "0.0.0.0";
  }

  static java.util.regex.Pattern INETINFO_IP_PATTERN = java.util.regex.Pattern.compile(
    "inet addr\\:(\\p{Digit}\\p{Digit}?\\p{Digit}?\\.\\p{Digit}\\p{Digit}?\\p{Digit}?\\.\\p{Digit}\\p{Digit}?\\p{Digit}?\\.\\p{Digit}\\p{Digit}?\\p{Digit}?) ");

  static String getIPAddressFromInetInfo(String inetInfo) {
    if (inetInfo!=null && inetInfo.contains("UP"))
    {
      java.util.regex.Matcher mat = INETINFO_IP_PATTERN.matcher(inetInfo);
      // Go with eth1
      if (mat.find())
      {
        return mat.group(1);
      }
    }
    return null;
  }

  public static int getOpticalDiscType()
  {
    // For Linux we use cdrecord and parse its output to see what's in the optical drive
    // It returns 0 if there's valid media in the drive (not blank either)
    String burnPath = Sage.get("linux/cd_burn", "cdrecord");
    String devPath = Sage.get("default_burner_device", "/dev/cdrom");
    String rez = IOUtils.exec(new String[] { "sh", "-c", burnPath + " -toc dev=" + devPath });
    if (rez.indexOf("No disk") != -1)
    {
      // This occurs on non-existent media
      if (Sage.DBG) System.out.println("No disc detected in drive");
      return NO_DISC;
    }
    else if (rez.indexOf("Sense flags: Blk 0") != -1)
    {
      // This occurs on blank or non-existent media
      if (rez.indexOf("Found DVD+ media") != -1 || rez.indexOf("DVD+R driver") != -1 ||
          rez.indexOf("DVD+RW driver") != -1)
      {
        if (Sage.DBG) System.out.println("Blank DVD+R disc detected in drive");
        return BLANK_DVD_PLUS_R;
      }
      else if (rez.indexOf("Found DVD media") != -1 || rez.indexOf("DVD-RW driver") != -1)
      {
        if (Sage.DBG) System.out.println("Blank DVD-R disc detected in drive");
        return BLANK_DVD_MINUS_R;
      }
      else
      {
        if (Sage.DBG) System.out.println("Blank CD-R disc detected in drive");
        return BLANK_CD;
      }
    }
    else
    {
      // Media should be valid
      if (rez.indexOf("Found DVD+ media") != -1 || rez.indexOf("DVD+R driver") != -1 ||
          rez.indexOf("DVD+RW driver") != -1)
      {
        if (Sage.DBG) System.out.println("DVD+R detected in drive");
        return DVD_PLUS_R;
      }
      else if (rez.indexOf("Found DVD media") != -1 || rez.indexOf("DVD-RW driver") != -1)
      {
        if (Sage.DBG) System.out.println("DVD detected in drive");
        return DVD_MINUS_R;
      }
      else
      {
        if (Sage.DBG) System.out.println("CD detected in drive");
        return VALID_CD;
      }
    }
  }
}
