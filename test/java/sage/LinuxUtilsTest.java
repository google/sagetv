package sage;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Created by seans on 13/11/16.
 */
public class LinuxUtilsTest
{
  @Test
  public void testGetIPAddressFromInetInfo() throws Exception
  {
    String inetinfo1 = "eno1      Link encap:Ethernet  HWaddr 10:c3:7b:91:3b:4d  \n" +
      "          inet addr:192.168.1.192  Bcast:192.168.1.255  Mask:255.255.255.0\n" +
      "          inet6 addr: fe80::7474:bd53:85aa:8f37/64 Scope:Link\n" +
      "          UP BROADCAST RUNNING MULTICAST  MTU:1500  Metric:1\n" +
      "          RX packets:56401450 errors:0 dropped:0 overruns:0 frame:0\n" +
      "          TX packets:40678669 errors:0 dropped:0 overruns:0 carrier:0\n" +
      "          collisions:0 txqueuelen:1000 \n" +
      "          RX bytes:33436572650 (33.4 GB)  TX bytes:11772048166 (11.7 GB)\n" +
      "          Interrupt:20 Memory:ef300000-ef320000";
    assertEquals("192.168.1.192", LinuxUtils.getIPAddressFromInetInfo(inetinfo1));

    String inetinfo2 = "br0       Link encap:Ethernet  HWaddr 94:de:80:01:64:bb  \n" +
      "          inet addr:192.168.1.10  Bcast:0.0.0.0  Mask:255.255.255.0\n" +
      "          UP BROADCAST RUNNING MULTICAST  MTU:1500  Metric:1\n" +
      "          RX packets:10641514 errors:0 dropped:64937 overruns:0 frame:0\n" +
      "          TX packets:17523316 errors:0 dropped:0 overruns:0 carrier:0\n" +
      "          collisions:0 txqueuelen:1000 \n" +
      "          RX bytes:3721981747 (3.7 GB)  TX bytes:23565332718 (23.5 GB)\n";
    assertEquals("192.168.1.10", LinuxUtils.getIPAddressFromInetInfo(inetinfo2));

    // no IP on this one, but it is UP, but should return null
    String inetinfo3 = "eth0      Link encap:Ethernet  HWaddr 94:de:80:01:64:bb  \n" +
      "          UP BROADCAST RUNNING MULTICAST  MTU:1500  Metric:1\n" +
      "          RX packets:11966319 errors:0 dropped:0 overruns:0 frame:0\n" +
      "          TX packets:17523316 errors:0 dropped:0 overruns:0 carrier:0\n" +
      "          collisions:0 txqueuelen:1000 \n" +
      "          RX bytes:3960802559 (3.9 GB)  TX bytes:23567094632 (23.5 GB)\n";
    assertNull(LinuxUtils.getIPAddressFromInetInfo(inetinfo3));

    // null should not fail with NPE but return null
    assertNull(LinuxUtils.getIPAddressFromInetInfo(null));
  }
}
