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
    if (Sage.EMBEDDED)
    {
      if (Sage.DBG) System.out.println("Performing network configuration...");
      // First stop any DHCP service that's running
      IOUtils.exec2("/app/sage/scripts/stopdhcp");
      // Stop any WPA supplicant apps that are running
      IOUtils.exec2("/app/sage/scripts/stopwpasup");

      boolean wifi = Sage.getBoolean("UseWireless", false);
      String wifiDev = Sage.get("WirelessInterface", "ra0");
      boolean wifiFailed = false;
      try
      {
        if (wifi)
        {
          // First check to see if we actually have the wireless adapter
          if (new java.io.File("/sys/class/net/" + wifiDev).exists())
          {
            // Wifi adapter is present
            if (Sage.DBG) System.out.println("Wireless adapter " + wifiDev + " was found");
            // Check to see if the wireless adapter has been brought up yet
            if (IOUtils.exec2(new String[] { "sh", "-c", "ifconfig | grep " + wifiDev}) != 0)
            {
              if (Sage.DBG) System.out.println("Wireless adapter has not been loaded yet...loading it up now");
              IOUtils.exec2(new String[] { "ifconfig", wifiDev, "up"});
              if (IOUtils.exec2(new String[] { "sh", "-c", "ifconfig | grep " + wifiDev}) != 0)
              {
                if (Sage.DBG) System.out.println("Wireless adapter failed to load with ifconfig!");
                wifiFailed = true;
              }
              else
              {
                if (Sage.DBG) System.out.println("Successfully loaded wireless adapter " + wifiDev);
              }
            }
            if (!wifiFailed)
            {
              // We loaded the adapter and its in ifconfig now. Next we need to setup the config file for
              // wpa_supplicant. We may already know the security mode if the user had to manually specify
              // the access point; otherwise we'll need to autodetect it.
              String accessPoint = Sage.get("WirelessSSID", "");
              String wifiSecurity = Sage.get("WirelessSecurity", "Auto");
              String lastWifiSecurity = Sage.get("LastWifiSecurity", "WPA");
              String wifiPassword = Sage.get("WifiPassword", "");
              if (accessPoint.length() == 0)
              {
                if (Sage.DBG) System.out.println("No WiFi AP has been selected yet; cannot load wifi");
                wifiFailed = true;
              }
              else
              {
                if ("Auto".equalsIgnoreCase(wifiSecurity))
                {
                  // Now we need to automatically determine the security mode from the iwlist output; if we
                  // can't find it then we just use the last security mode we did
                  java.util.Map scannedAPs = scanForWifiAPs(wifiDev);
                  if (scannedAPs.get(accessPoint) != null)
                  {
                    String securityStrength = scannedAPs.get(accessPoint).toString();
                    int idx = securityStrength.indexOf(';');
                    if (idx != -1)
                    {
                      wifiSecurity = securityStrength.substring(0, idx);
                      Sage.put("LastWifiSecurity", wifiSecurity);
                    }
                    else
                    {
                      if (Sage.DBG) System.out.println("Invalid security;strength info of: " + securityStrength);
                      wifiSecurity = lastWifiSecurity;
                    }
                  }
                  else
                  {
                    if (Sage.DBG) System.out.println("AccessPoint not found in scan; using last security mode");
                    wifiSecurity = lastWifiSecurity;
                  }
                  if (Sage.DBG) System.out.println("Wifi Auto security mode has been set as: " + wifiSecurity);
                }
                new java.io.File("/tmp/wpa_supplicant.conf").delete();
                java.io.PrintWriter confStream = null;
                try
                {
                  boolean hexKey = true;
                  for (int i = 0; i < wifiPassword.length(); i++)
                  {
                    if (Character.digit(wifiPassword.charAt(i), 16) < 0)
                    {
                      hexKey = false;
                      break;
                    }
                  }
                  confStream = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter("/tmp/wpa_supplicant.conf", false)));
                  confStream.println("ap_scan=1");
                  confStream.println("ctrl_interface=/tmp/wpa_supplicant");
                  confStream.println("ctrl_interface_group=0");
                  confStream.println("network={");
                  confStream.println("scan_ssid=1");
                  confStream.println("ssid=\"" + accessPoint + "\"");
                  confStream.println("priority=5");
                  if ("WPA".equals(wifiSecurity))
                  {
                    if(hexKey && wifiPassword.length() == 64)
                    {
                      confStream.println("psk=" + wifiPassword );
                    }
                    else
                    {
                      confStream.println("psk=\"" + wifiPassword + "\"");
                    }
                  }
                  else if ("WEP".equals(wifiSecurity))
                  {
                    confStream.println("key_mgmt=NONE");
                    if(hexKey &&
                        (wifiPassword.length() == 10 ||
                        wifiPassword.length() == 26 ||
                        wifiPassword.length() == 58))
                    {
                      confStream.println("wep_key0=" + wifiPassword);
                    }
                    else
                    {
                      confStream.println("wep_key0=\"" + wifiPassword + "\"");
                    }
                    confStream.println("wep_tx_keyidx=0");
                    confStream.println("auth_alg=SHARED");
                  }
                  else // no security
                  {
                    confStream.println("key_mgmt=NONE");
                  }
                  confStream.println("}");
                }
                catch (java.io.IOException e)
                {
                  if (Sage.DBG) System.out.println("ERROR writing wpa_supplicant.conf file of:" + e);
                  wifiFailed = true;
                }
                finally
                {
                  if (confStream != null)
                  {
                    try{confStream.close();}catch(Exception e){}
                  }
                }
                if (!wifiFailed)
                {
                  if (Sage.DBG) System.out.println("Wifi configuration is complete; attempt to connect to access point now in background");
                  IOUtils.exec2(new String[] { "/app/sage/scripts/startwpasup", wifiDev});
                  // We should wait until the connection is completed or fail
                  // for up to 30 seconds
                  int wtimeout=Sage.getInt("WirelessTimeout", 30);
                  int waittime=0;
                  for(waittime=0;waittime<wtimeout;waittime++)
                  {
                    if(new java.io.File("/tmp/wpaready").exists())
                      break;
                    try{Thread.sleep(1000);} catch (Exception e) {}
                  }
                  if(!(waittime<wtimeout))
                  {
                    return -2; // Timeout waiting for wireless connection
                  }
                }
                else
                {
                  return -1;
                }
              }
            }
          }
          else
          {
            if (Sage.DBG) System.out.println("Disabling use of wireless since adapter " + wifiDev + " is not present");
            wifi = false;
          }
        }
        if (!wifi && new java.io.File("/sys/class/net/" + wifiDev).exists())
        {
          // disable wireless if its there
          IOUtils.exec2(new String[] { "ifconfig", wifiDev, "down" });
        }
        else
        {
          // disable eth0 in case we had conflicting routes
          IOUtils.exec2(new String[] { "ifconfig", Sage.get("NetworkInterface", "eth0"), "down"});
        }
        // Don't bother with settingup the IP if we're using wifi and it's setup failed
        if (!wifi || !wifiFailed)
        {
          String netIface = !wifi ? Sage.get("NetworkInterface", "eth0") : wifiDev;
          if (Sage.getBoolean("UseDHCP", true))
          {
            if (Sage.DBG) System.out.println("Using DHCP");
            IOUtils.exec2(new String[] { "/app/sage/scripts/startdhcp", netIface });
            int dhcptimeout=Sage.getInt("DHCPTimeout", 30);
            int waittime=0;
            for(waittime=0;waittime<dhcptimeout;waittime++)
            {
              if(new java.io.File("/tmp/lan").exists())
                break;
              try{Thread.sleep(1000);} catch (Exception e) {}
            }
            if(!(waittime<dhcptimeout))
            {
              return -3; // Timeout waiting for dhcp
            }
          }
          else
          {
            if (Sage.DBG) System.out.println("Using static IP addressing");
            IOUtils.exec2(new String[] { "/app/sage/scripts/setnet", netIface,
                Sage.get("StaticIP", "0.0.0.0"), Sage.get("StaticSubnet", "255.255.255.0"),
                Sage.get("StaticGateway", "192.168.0.1"), Sage.get("StaticDNS", "192.168.0.1")});
          }
        }
      }
      finally
      {
        // Always do this at the end in case it changed
        CVMUtils.reloadNameserverCache();
        if (!ntpdateThreadLaunched)
        {
          // Try to set the time using an NTP server
          int res = IOUtils.exec2(new String[] { "/usr/local/bin/ntpdate", NTP_SERVERS[0]});
          ntpdateThreadLaunched = true;
          ntpdateWorked = res == 0;
          if (ntpdateWorked && SageTV.startTime == 0)
            SageTV.startTime = Sage.time();

          // It's VERY important that we have the time synced on startup...so try all of the servers before we move on here
          for (int i = 1; i < NTP_SERVERS.length && !ntpdateWorked; i++)
          {
            res = IOUtils.exec2(new String[] { "/usr/local/bin/ntpdate", NTP_SERVERS[i]});
            ntpdateWorked = res == 0;
            if (ntpdateWorked && SageTV.startTime == 0)
              SageTV.startTime = Sage.time();
          }

          // Run this in the background
          Thread t = new Thread("ntpdateUpdater")
          {
            public void run()
            {
              if (Sage.DBG) System.out.println("Started thread for doing ntpdate update...");
              boolean worked = false;
              int ntpIndex = 0;
              while (true)
              {
                int res = IOUtils.exec2(new String[] { "/usr/local/bin/ntpdate", NTP_SERVERS[ntpIndex]});
                ntpIndex++;
                if (res != 0)
                {
                  if (Sage.DBG) System.out.println("ntpdate update failed...wait and then try again...");
                  try{Thread.sleep(3000);}catch(Exception e){}
                  ntpIndex = ntpIndex % NTP_SERVERS.length;
                }
                else
                {
                  if (Sage.DBG) System.out.println("ntpdate updated succeeded!");
                  if (SageTV.startTime == 0)
                    SageTV.startTime = Sage.time();
                  worked = true;
                  if (!ntpdateWorked)
                  {
                    // When we first set the clock we need to kick some parts of the core system if we didn't get it set at the start
                    Carny.getInstance().kickHard();
                    Scheduler.getInstance().kick(false);
                  }
                  ntpdateWorked = true;
                }
                if (worked)
                {
                  // The clock can drift pretty easily on the 8654; so redo this update every half hour
                  try{Thread.sleep(Sage.MILLIS_PER_HR/2);}catch(Exception e){}
                }
              }
            }
          };
          t.setPriority(Thread.MIN_PRIORITY);
          t.start();
        }
      }
      return 0;
    }
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

  // Returns SSIDName->Security;SignalStrength in a Map for scanning the WIFI APs on the specified interface
  public static java.util.Map scanForWifiAPs(String wifiDev)
  {
    if (!Sage.EMBEDDED) throw new UnsupportedOperationException("scanForWifiAPs is not implemented for this platform!");
    if (Sage.DBG) System.out.println("Scanning for wifi access points...");
    // The wireless device must be up in order to do this so make sure that's the case first
    IOUtils.exec(new String[] { "ifconfig", wifiDev, "up" });
    String scanOutput = IOUtils.exec(new String[] { "iwlist", wifiDev, "scanning" }, true, true);
    java.util.StringTokenizer toker = new java.util.StringTokenizer(scanOutput, "\r\n");
    String currSSID = null;
    String currStrength = "0";
    String currSecurity = "WPA";
    java.util.Map rv = new java.util.HashMap();
    while (toker.hasMoreTokens())
    {
      String currLine = toker.nextToken();
      if (currLine.indexOf("ESSID:") != -1)
      {
        if (currSSID != null)
        {
          rv.put(currSSID, currSecurity + ";" + currStrength);
        }
        int quote1 = currLine.indexOf('\"');
        int quote2 = currLine.lastIndexOf('\"');
        if (quote1 != -1 && quote2 != -1)
        {
          currSSID = currLine.substring(quote1 + 1, quote2);
        }
      }
      else if (currLine.indexOf("Quality") != -1 && currLine.indexOf("Signal level") != -1)
      {
        int idx1 = currLine.indexOf("Quality") + "Quality".length() + 1; // skip char after quality which might be = or :
        int idx2 = currLine.indexOf('/');
        if (idx1 != -1 && idx2 != -1)
        {
          currStrength = currLine.substring(idx1, idx2).trim();
          try
          {
            Integer.parseInt(currStrength);
          }
          catch (NumberFormatException nfe)
          {
            if (Sage.DBG) System.out.println("ERROR parsing iwlist signal strength of " + nfe + " on line:" + currLine);
            currStrength = "0";
          }
        }
      }
      else if (currLine.indexOf("Encryption key:off") != -1)
      {
        currSecurity = "None";
      }
      else if (currLine.indexOf("Encryption key:on") != -1)
      {
        currSecurity = "WEP";
      }
      else if (currLine.indexOf("IE: ") != -1 && currLine.indexOf("WPA") != -1 && "WEP".equals(currSecurity))
      {
        currSecurity = "WPA";
      }
    }
    if (currSSID != null)
    {
      rv.put(currSSID, currSecurity + ";" + currStrength);
    }
    if (Sage.DBG) System.out.println("Wifi scan results=" + rv);
    return rv;
  }

  public static String getIPAddress()
  {
    if (Sage.EMBEDDED)
    {
      String br0Info = IOUtils.exec(new String[] { "ifconfig", "br0" });
      //System.out.println("eth0Info=" + eth0Info);
      int idx0 = br0Info.indexOf("inet addr:");
      int idx1 = br0Info.indexOf("Bcast:");
      if (idx0 != -1 && idx1 != -1)
      {
        return br0Info.substring(idx0 + "inet addr:".length(), idx1).trim();
      }
      else
      {
        return "0.0.0.0";
      }
    }
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

  private static int wasSmbEnabled = -1;
  public static void updateSmbConfig(boolean alwaysRestart)
  {
    if (Sage.LINUX_OS && Sage.EMBEDDED && !Sage.client)
    {
      boolean enabled = Sage.getBoolean(SAMBA_CONFIG_ENABLE, false);
      if (!enabled)
      {
        if (wasSmbEnabled != 0)
        {
          if (Sage.DBG) System.out.println("Stopping the smbd/nmbd servers");
          IOUtils.exec2("/app/sage/scripts/stopsmbd");
          wasSmbEnabled = 0;
        }
      }
      else
      {
        if (Sage.DBG) System.out.println("Updating SMB configuration...");
        wasSmbEnabled = 1;
        // First setup the hostname for the system
        String hostname = SageTV.hostname = Sage.get(SAMBA_CONFIG_MACHINE, "HD300");
        if (Sage.DBG) System.out.println("Setting hostname to be: " + hostname);
        IOUtils.exec2(new String[] { "hostname", hostname });
        IOUtils.exec2(new String[] { "sh", "-c", "echo \"127.0.0.1\tlocalhost " + hostname + "\" > /tmp/hosts" });
        String workgroup = Sage.get(SAMBA_CONFIG_WORKGROUP, "WORKGROUP");

        java.io.PrintWriter smbConfStream = null;
        try
        {
          smbConfStream = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter("/tmp/smb.conf.tmp"), 4096));
          // Write the global section first
          smbConfStream.println("[global]\r\n\tguest account = root\r\n\tsecurity = share\r\n\tlocal master = no");
          smbConfStream.println("\tnetbios name = " + hostname);
          smbConfStream.println("\tserver string = " + hostname);
          smbConfStream.println("\tworkgroup = " + workgroup);
          // Do the hard disk if its there
          /*					if (FSManager.getInstance().getLocalHDD() != null)
					{
						smbConfStream.println("[HDD]\r\n\tcomment = Hard Disk");
						smbConfStream.println("\tpath = " + FSManager.getInstance().getLocalHDD().getAbsolutePath());
						smbConfStream.println("\tread only = No\r\n\tforce user = root\r\n\tforce group = root\r\n\tguest ok = yes");
					}*/
          if (Sage.DBG)
          {
            // Expose the /tmp folder for debug log retrieval
            smbConfStream.println("[TempLogs]\r\n\tcomment = Tmp");
            smbConfStream.println("\tpath = /tmp");
            smbConfStream.println("\tread only = No\r\n\tforce user = root\r\n\tforce group = root\r\n\tguest ok = yes");

            // Expose the writable flash partition, we only do this in debug mode for now...
            smbConfStream.println("[InternalFlash]\r\n\tcomment = InternalFlash");
            smbConfStream.println("\tpath = /rw");
            smbConfStream.println("\tread only = No\r\n\tforce user = root\r\n\tforce group = root\r\n\tguest ok = yes");
          }
          if (SageConstants.PVR)
          {
            // Expose the tv, music, videos, pictures shares if we're a PVR and they exist
            if (new java.io.File("/var/media/music").isDirectory())
            {
              smbConfStream.println("[Music]\r\n\tcomment = Music");
              smbConfStream.println("\tpath = /var/media/music");
              smbConfStream.println("\tread only = No\r\n\tforce user = root\r\n\tforce group = root\r\n\tguest ok = yes");
            }
            if (new java.io.File("/var/media/videos").isDirectory())
            {
              smbConfStream.println("[Videos]\r\n\tcomment = Videos");
              smbConfStream.println("\tpath = /var/media/videos");
              smbConfStream.println("\tread only = No\r\n\tforce user = root\r\n\tforce group = root\r\n\tguest ok = yes");
            }
            if (new java.io.File("/var/media/pictures").isDirectory())
            {
              smbConfStream.println("[Photos]\r\n\tcomment = Photos");
              smbConfStream.println("\tpath = /var/media/pictures");
              smbConfStream.println("\tread only = No\r\n\tforce user = root\r\n\tforce group = root\r\n\tguest ok = yes");
            }
            if (new java.io.File("/var/media/tv").isDirectory())
            {
              smbConfStream.println("[TV]\r\n\tcomment = TV");
              smbConfStream.println("\tpath = /var/media/tv");
              smbConfStream.println("\tread only = No\r\n\tforce user = root\r\n\tforce group = root\r\n\tguest ok = yes");
            }
          }

          // Do any external USB drives that are there
          java.util.Map usbDevMap = NewStorageDeviceDetector.getInstance().getDeviceMap();
          if (usbDevMap != null && !usbDevMap.isEmpty())
          {
            java.util.Iterator walker = usbDevMap.entrySet().iterator();
            while (walker.hasNext())
            {
              java.util.Map.Entry currEnt = (java.util.Map.Entry) walker.next();
              String prettyName = (String)currEnt.getKey();
              java.io.File path = (java.io.File) currEnt.getValue();
              smbConfStream.println("[" + path.getName().toUpperCase() + "]");
              smbConfStream.println("\tread only = No\r\n\tforce user = root\r\n\tforce group = root\r\n\tguest ok = yes");
              smbConfStream.println("\tcomment = " + prettyName);
              smbConfStream.println("\tpath = " + path.getAbsolutePath());
            }
          }
          smbConfStream.close();
          java.io.File smbConf = new java.io.File("/tmp/smb.conf");
          smbConf.delete();
          if (!new java.io.File("/tmp/smb.conf.tmp").renameTo(smbConf))
          {
            if (Sage.DBG) System.out.println("ERROR renaming smb.conf.tmp to smb.conf!!!");
          }
          smbConfStream = null;
        }
        catch (java.io.IOException e)
        {
          System.out.println("ERROR updating SMB configuration of:" + e);
          e.printStackTrace();
        }
        finally
        {
          if (smbConfStream != null)
          {
            try{smbConfStream.close();}catch(Exception e2){}
          }
        }
        // Now samba should automatically reload this file w/ in a minute and expose the new updates
        if (alwaysRestart)
        {
          if (Sage.DBG) System.out.println("Stopping the smbd/nmbd servers");
          IOUtils.exec2("/app/sage/scripts/stopsmbd");
          // Wait to ensure that nmbd is killed
          while (IOUtils.exec2(new String[] {"pidof", "nmbd"}) == 0)
          {
            try{Thread.sleep(100);}catch(Exception e){}
          }
        }
        if (Sage.DBG) System.out.println("Starting the smbd/nmbd servers if they're not running...");
        IOUtils.exec2("/app/sage/scripts/startsmbd");
      }
    }
    else
      throw new UnsupportedOperationException("UpdateSmbConfig is not implemented for this platform!");
  }
}
