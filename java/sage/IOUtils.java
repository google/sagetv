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

import sage.io.RemoteSageFile;
import sage.io.SageInputStream;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

public class IOUtils
{
  private IOUtils()
  {
  }

  /**
   * Used to read a URL contents into String.  This operation blocks and the content is a string, so it should
   * not be used on large files, and should not be used on binary files.
   *
   * @param urlStr URL to download
   * @return String contents of the given URL
     */
  public static String getUrlAsString(String urlStr)
  {
    StringBuilder sb = new StringBuilder();
    try
    {
      URL url = new URL(urlStr);
      return getInputStreamAsString(url.openStream());
    }
    catch (Exception e)
    {
      if (Sage.DBG)
      {
        System.out.println("Failed to read URL to string for " + urlStr);
        e.printStackTrace();
      }
    }
    return sb.toString();
  }

  public static void closeQuietly(Closeable closeable)
  {
    if (closeable!=null)
    {
      try
      {
        closeable.close();
      } catch (IOException e)
      {
      }
    }
  }

  public static String getFileExtension(java.io.File f)
  {
    String s = f == null ? "" : f.toString();
    int idx = s.lastIndexOf('.');
    if (idx < 0)
      return "";
    else
      return s.substring(idx + 1);
  }

  // Returns the size of all files contained within the directory & recursively beneath
  public static long getDirectorySize(java.io.File f)
  {
    return getDirectorySize(f, new java.util.HashSet());
  }
  private static long getDirectorySize(java.io.File f, java.util.HashSet done)
  {
    // protect against infinite recursion due to symbolic links on Linux
    java.io.File realF = f;
    try
    {
      // On Linux we need to resolve symbolic links or we could recurse forever
      realF = f.getCanonicalFile();
    }
    catch (java.io.IOException e){}
    if (!done.add(realF)) return 0;
    long rv = 0;
    java.io.File[] kids = f.listFiles();
    for (int i = 0; kids != null && i < kids.length; i++)
    {
      if (kids[i].isFile())
        rv += kids[i].length();
      else if (kids[i].isDirectory())
        rv += getDirectorySize(kids[i]);
    }
    return rv;
  }

  // Returns the all files contained within the directory & recursively beneath. Does NOT return directories
  public static java.io.File[] listFilesRecursive(java.io.File f)
  {
    return listFilesRecursive(f, true);
  }
  public static java.io.File[] listFilesRecursive(java.io.File f, boolean includeAllFileTypes)
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    listFilesRecursive(f, rv, new java.util.HashSet(), includeAllFileTypes);
    return (java.io.File[]) rv.toArray(new java.io.File[0]);
  }
  private static void listFilesRecursive(java.io.File f, java.util.ArrayList rv, java.util.HashSet done, boolean includeAllFileTypes)
  {
    // protect against infinite recursion due to symbolic links on Linux
    java.io.File realF = f;
    try
    {
      // On Linux we need to resolve symbolic links or we could recurse forever
      realF = f.getCanonicalFile();
    }
    catch (java.io.IOException e){}
    if (!done.add(realF)) return;
    java.io.File[] kids = f.listFiles();
    for (int i = 0; kids != null && i < kids.length; i++)
    {
      if (kids[i].isFile())
      {
        if (includeAllFileTypes || SeekerSelector.getInstance().hasImportableFileExtension(kids[i].getName()))
          rv.add(kids[i]);
      }
      else if (kids[i].isDirectory())
        listFilesRecursive(kids[i], rv, done, includeAllFileTypes);
    }
  }

  // Returns the all files contained within the directory & recursively beneath. Does NOT return directories
  public static java.io.File[] listServerFilesRecursive(java.io.File f)
  {
    return listServerFilesRecursive(f, true);
  }
  public static java.io.File[] listServerFilesRecursive(java.io.File f, boolean includeAllFileTypes)
  {
    if (!Sage.client) return new java.io.File[0];
    java.net.Socket sock = null;
    java.io.DataOutputStream outStream = null;
    java.io.DataInputStream inStream = null;
    try
    {
      sock = new java.net.Socket();
      sock.connect(new java.net.InetSocketAddress(Sage.preferredServer, 7818), 5000);
      sock.setSoTimeout(30000);
      //sock.setTcpNoDelay(true);
      outStream = new java.io.DataOutputStream(new java.io.BufferedOutputStream(sock.getOutputStream()));
      inStream = new java.io.DataInputStream(new java.io.BufferedInputStream(sock.getInputStream()));
      // First request access to it since it's probably not in the media filesystem
      boolean gotMSAccess = false;
      if (NetworkClient.getSN().requestMediaServerAccess(f, true))
      {
        gotMSAccess = true;
      }
      outStream.write((includeAllFileTypes ? "LISTRECURSIVEALLW " : "LISTRECURSIVEW").getBytes(sage.Sage.BYTE_CHARSET));
      outStream.write(f.toString().getBytes("UTF-16BE"));
      outStream.write("\r\n".getBytes(sage.Sage.BYTE_CHARSET));
      outStream.flush();
      String str = sage.Sage.readLineBytes(inStream);
      if (!"OK".equals(str))
        throw new java.io.IOException("Error doing remote recursive dir listing of:" + str);
      // get the size
      str = sage.Sage.readLineBytes(inStream);
      int numFiles = Integer.parseInt(str);
      java.io.File[] rv = new java.io.File[numFiles];
      for (int i = 0; i < numFiles; i++)
      {
        rv[i] = new java.io.File(sage.MediaServer.convertToUnicode(sage.Sage.readLineBytes(inStream)));
      }
      if (gotMSAccess)
      {
        NetworkClient.getSN().requestMediaServerAccess(f, false);
      }
      return rv;
    }
    catch (java.io.IOException e)
    {
      if (Sage.DBG) System.out.println("ERROR downloading recursive directory listing from server of :" + e + " for dir: " + f);
      return new java.io.File[0];
    }
    finally
    {
      try{
        if (sock != null)
          sock.close();
      }catch (Exception e1){}
      try{
        if (outStream != null)
          outStream.close();
      }catch (Exception e2){}
      try{
        if (inStream != null)
          inStream.close();
      }catch (Exception e3){}
    }
  }

  // Returns the list of all files contained within the specified directory
  public static String[] listServerFiles(java.io.File f)
  {
    if (!Sage.client) return Pooler.EMPTY_STRING_ARRAY;
    java.net.Socket sock = null;
    java.io.DataOutputStream outStream = null;
    java.io.DataInputStream inStream = null;
    try
    {
      sock = new java.net.Socket();
      sock.connect(new java.net.InetSocketAddress(Sage.preferredServer, 7818), 5000);
      sock.setSoTimeout(30000);
      //sock.setTcpNoDelay(true);
      outStream = new java.io.DataOutputStream(new java.io.BufferedOutputStream(sock.getOutputStream()));
      inStream = new java.io.DataInputStream(new java.io.BufferedInputStream(sock.getInputStream()));
      // First request access to it since it's probably not in the media filesystem
      boolean gotMSAccess = false;
      if (NetworkClient.getSN().requestMediaServerAccess(f, true))
      {
        gotMSAccess = true;
      }
      outStream.write("LISTW ".getBytes(sage.Sage.BYTE_CHARSET));
      outStream.write(f.toString().getBytes("UTF-16BE"));
      outStream.write("\r\n".getBytes(sage.Sage.BYTE_CHARSET));
      outStream.flush();
      String str = sage.Sage.readLineBytes(inStream);
      if (!"OK".equals(str))
        throw new java.io.IOException("Error doing remote recursive dir listing of:" + str);
      // get the size
      str = sage.Sage.readLineBytes(inStream);
      int numFiles = Integer.parseInt(str);
      String[] rv = new String[numFiles];
      for (int i = 0; i < numFiles; i++)
      {
        rv[i] = sage.MediaServer.convertToUnicode(sage.Sage.readLineBytes(inStream));
      }
      if (gotMSAccess)
      {
        NetworkClient.getSN().requestMediaServerAccess(f, false);
      }
      return rv;
    }
    catch (java.io.IOException e)
    {
      if (Sage.DBG) System.out.println("ERROR downloading recursive directory listing from server of :" + e + " for dir: " + f);
      return Pooler.EMPTY_STRING_ARRAY;
    }
    finally
    {
      try{
        if (sock != null)
          sock.close();
      }catch (Exception e1){}
      try{
        if (outStream != null)
          outStream.close();
      }catch (Exception e2){}
      try{
        if (inStream != null)
          inStream.close();
      }catch (Exception e3){}
    }
  }

  public static java.io.File getRootDirectory(java.io.File f)
  {
    if (f == null) return null;
    int numParents = 0;
    java.io.File currParent = f;
    while (currParent.getParentFile() != null)
    {
      currParent = currParent.getParentFile();
      numParents++;
    }

    if (currParent.toString().equals("\\\\") || (Sage.MAC_OS_X && f.toString().startsWith("/Volumes")))
    {
      // UNC Pathname, add the computer name and share name to get the actual root folder
      // or on Mac we need to protect the /Volumes directory
      numParents -= 2;
      while (numParents-- > 0)
        f = f.getParentFile();
      return f;
    }
    else
      return currParent;
  }
  public static void copyFile(java.io.File srcFile, java.io.File destFile) throws java.io.IOException
  {
    java.io.FileOutputStream fos = null;
    java.io.OutputStream outStream = null;
    java.io.InputStream inStream = null;
    try
    {
      outStream = new java.io.BufferedOutputStream(fos = new
          java.io.FileOutputStream(destFile));
      inStream = new java.io.BufferedInputStream(new
          java.io.FileInputStream(srcFile));
      byte[] buf = new byte[65536];
      int numRead = inStream.read(buf);
      while (numRead != -1)
      {
        outStream.write(buf, 0, numRead);
        numRead = inStream.read(buf);
      }
    }
    finally
    {
      try
      {
        if (inStream != null)
        {
          inStream.close();
          inStream = null;
        }
      }
      catch (java.io.IOException e) {}
      try
      {
        if (outStream != null)
        {
          outStream.flush();
          fos.getFD().sync();
          outStream.close();
          outStream = null;
        }
      }
      catch (java.io.IOException e) {}
    }
    // Preserve the file timestamp on the copy as well
    destFile.setLastModified(srcFile.lastModified());
  }

  public static String exec(String[] cmdArray)
  {
    return exec(cmdArray, true, true);
  }
  public static String exec(String[] cmdArray, final boolean getStderr, final boolean getStdout)
  {
    return exec(cmdArray, getStderr, getStdout, false);
  }
  public static String exec(String[] cmdArray, final boolean getStderr, final boolean getStdout, final boolean controlRunaways)
  {
    return exec(cmdArray, getStderr, getStdout, controlRunaways, null);
  }
  public static String exec(String[] cmdArray, final boolean getStderr, final boolean getStdout, final boolean controlRunaways,
      java.io.File workingDir)
  {
    return (String) exec(cmdArray, getStderr, getStdout, controlRunaways, workingDir, false);
  }
  public static java.io.ByteArrayOutputStream execByteOutput(String[] cmdArray, final boolean getStderr, final boolean getStdout, final boolean controlRunaways,
      java.io.File workingDir)
  {
    return (java.io.ByteArrayOutputStream) exec(cmdArray, getStderr, getStdout, controlRunaways, workingDir, true);
  }
  private static Object exec(String[] cmdArray, final boolean getStderr, final boolean getStdout, final boolean controlRunaways,
      java.io.File workingDir, boolean byteOutput)
  {
    final long timeLimit = controlRunaways ? Sage.getLong("control_runaway_exec_time_limit", 60000) : 0;
    final long sizeLimit = controlRunaways ? Sage.getLong("control_runaway_exec_size_limit", 1024*1024) : 0;
    try
    {
      final Process procy = Runtime.getRuntime().exec(cmdArray, null, workingDir);
      final java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(1024);
      final long startTime = Sage.time();
      Thread the = new Thread("InputStreamConsumer")
      {
        public void run()
        {
          try
          {
            java.io.InputStream buf = procy.getInputStream();
            String s;
            do
            {
              int c = buf.read();
              if (c == -1)
                break;
              else if (getStdout)
                baos.write(c);
              if (sizeLimit > 0 && baos.size() > sizeLimit)
              {
                if (Sage.DBG) System.out.println("NOTICE: Forcibly terminating spawned process due to runaway memory detection!");
                procy.destroy();
                break;
              }
            }while (true);
            buf.close();
          }
          catch (Exception e){}
        }
      };
      the.setDaemon(true);
      the.start();
      Thread the2 = new Thread("ErrorStreamConsumer")
      {
        public void run()
        {
          try
          {
            java.io.InputStream buf = procy.getErrorStream();
            String s;
            do
            {
              int c = buf.read();
              if (c == -1)
                break;
              else if (getStderr)
                baos.write(c);
              if (sizeLimit > 0 && baos.size() > sizeLimit)
              {
                if (Sage.DBG) System.out.println("NOTICE: Forcibly terminating spawned process due to runaway memory detection!");
                procy.destroy();
                break;
              }
            }while (true);
            buf.close();
          }
          catch (Exception e){}
        }
      };
      the2.setDaemon(true);
      the2.start();
      if (controlRunaways)
      {
        final boolean[] doneWaitin = new boolean[1];
        doneWaitin[0] = false;
        Pooler.execute(new Runnable()
        {
          public void run()
          {
            try
            {
              procy.waitFor();
            }
            catch (Exception e)
            {
            }
            finally
            {
              synchronized (doneWaitin)
              {
                doneWaitin[0] = true;
                doneWaitin.notifyAll();
              }
            }
          }
        });
        synchronized (doneWaitin)
        {
          while (!doneWaitin[0] && Sage.time() - startTime < timeLimit)
          {
            doneWaitin.wait(Math.max(1, timeLimit - (Sage.time() - startTime)));
          }
          if (!doneWaitin[0])
          {
            if (Sage.DBG) System.out.println("NOTICE: Forcibly terminating spawned process due to runaway execution detection!");
            procy.destroy();
          }
        }
      }
      else
        procy.waitFor();
      the.join(1000);
      the2.join(1000);
      if (byteOutput)
      {
        return baos;
      }
      try
      {
        return baos.toString(Sage.I18N_CHARSET);
      }
      catch (java.io.UnsupportedEncodingException uee)
      {
        // just use the default encoding
        return baos.toString();
      }
    }
    catch (Exception e)
    {
      if (byteOutput)
        return null;
      return e.toString();
    }
  }

  public static int exec2(String[] cmdArray)
  {
    return exec3(cmdArray, true);
  }
  public static int exec2(String[] cmdArray, boolean waitForExit)
  {
    return exec3(cmdArray, waitForExit);
  }
  public static int exec2(String cmd)
  {
    return exec3(cmd, true);
  }
  public static int exec2(String cmd, boolean waitForExit)
  {
    return exec3(cmd, waitForExit);
  }
  private static int exec3(Object obj, boolean waitForExit)
  {
    if (Sage.DBG) System.out.println("Executing process: " +
        ((obj instanceof String[]) ? java.util.Arrays.asList((String[])obj) : obj));
    try
    {
      final Process procy = obj instanceof String[] ?
          Runtime.getRuntime().exec((String[])obj) : Runtime.getRuntime().exec((String) obj);
          if (Sage.DBG) System.out.println("Started process object: " + procy);
          Thread the = new Thread("InputStreamConsumer")
          {
            public void run()
            {
              try
              {
                java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(procy.getInputStream()));
                String s;
                do
                {
                  s = buf.readLine();
                  if (s == null)
                    break;
                  else
                  {
                    if (Sage.DBG) System.out.println("STDOUT:" + procy + ": " + s);
                  }
                }while (true);
                buf.close();
              }
              catch (Exception e){}
            }
          };
          the.setDaemon(true);
          the.start();
          Thread the2 = new Thread("ErrorStreamConsumer")
          {
            public void run()
            {
              try
              {
                java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(procy.getErrorStream()));
                String s;
                do
                {
                  s = buf.readLine();
                  if (s == null)
                    break;
                  else
                  {
                    if (Sage.DBG) System.out.println("STDERR:" + procy + ": " + s);
                  }
                }while (true);
                buf.close();
              }
              catch (Exception e){}
            }
          };
          the2.setDaemon(true);
          the2.start();
          if (waitForExit)
          {
            procy.waitFor();
            the.join(1000);
            the2.join(1000);
            return procy.exitValue();
          }
          else
            return 0;
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("Error executing process " +
          ((obj instanceof String[]) ? (((String[])obj)[0]) : obj) + " : " + e);
      return -1;
    }
  }

  public static boolean deleteDirectory(java.io.File dir)
  {
    if (dir == null) return false;
    java.io.File[] kids = dir.listFiles();
    boolean rv = true;
    for (int i = 0; i < kids.length; i++)
    {
      if (kids[i].isDirectory())
      {
        rv &= deleteDirectory(kids[i]);
      }
      else if (!kids[i].delete())
        rv = false;
    }
    return dir.delete() && rv;
  }

  /**
   * Reads an InputStream as a String and then closes the stream and returns the contents as a String
   *
   * @param is
   * @return
   * @throws IOException
   */
  public static String getInputStreamAsString(java.io.InputStream is) throws IOException
  {
    java.io.BufferedReader buffRead = null;
    StringBuffer sb = new StringBuffer();
    try
    {
      try
      {
        buffRead = new java.io.BufferedReader(new InputStreamReader(is, "UTF-8"));
      } catch (UnsupportedEncodingException uee) {
        buffRead = new java.io.BufferedReader(new InputStreamReader(is));
      }
      char[] cbuf = new char[8192];
      int numRead = buffRead.read(cbuf);
      while (numRead != -1)
      {
        sb.append(cbuf, 0, numRead);
        numRead = buffRead.read(cbuf);
      }
    }
    finally
    {
      if (buffRead != null)
      {
        try{buffRead.close();}catch(Exception e){}
        buffRead = null;
      }
    }
    return sb.toString();
  }

  public static String getFileAsString(java.io.File file)
  {
    try
    {
      return getInputStreamAsString(new FileInputStream(file));
    }
    catch (java.io.IOException e)
    {
      System.out.println("Error reading file " + file + " of: " + e);
    }
    return null;
  }

  public static boolean writeStringToFile(java.io.File file, String s) {
    java.io.PrintWriter pw = null;
    try {
      pw = new java.io.PrintWriter(new java.io.FileWriter(file));
      pw.print(s);
    } catch (java.io.IOException e) {
      System.out.println("Error writing file " + file + " of: " + e);
      return false;
    }
    finally {
      if (pw != null) {
        try {
          pw.close();
        } catch (Exception e){}
      pw = null;
      }
    }
    return true;
  }

  public static String convertPlatformPathChars(String str)
  {
    StringBuffer sb = null;
    int strlen = str.length();
    char replaceChar = java.io.File.separatorChar;
    char testChar = (replaceChar == '/') ? '\\' : '/';
    for (int i = 0; i < strlen; i++)
    {
      char c = str.charAt(i);
      if (c == testChar)
      {
        if (sb == null)
        {
          sb = new StringBuffer(str.length());
          sb.append(str.substring(0, i));
        }
        sb.append(replaceChar);
      }
      else if (sb != null)
        sb.append(c);
    }
    if (sb == null)
      return str;
    else
      return sb.toString();
  }

  public static java.net.InetAddress getSubnetMask()
  {
    return getSubnetMask(null);
  }
  public static java.net.InetAddress getSubnetMask(java.net.InetAddress adapterAddr)
  {
    String ipAddrInfo = exec(new String[] { Sage.WINDOWS_OS ? "ipconfig" : "ifconfig"});
    java.util.regex.Pattern patty = java.util.regex.Pattern.compile("255\\.255\\.[0-9]+\\.[0-9]+");
    java.util.regex.Matcher matchy = patty.matcher(ipAddrInfo);
    try
    {
      int adapterIndex = (adapterAddr == null) ? -1 : ipAddrInfo.indexOf(adapterAddr.getHostAddress());
      while (matchy.find())
      {
        String currMatch = matchy.group();
        if ("255.255.255.255".equals(currMatch))
          continue; // ignore the subnet masks that restrict all since they're not what we want
        // Make sure we're on the network adapter of interest
        if (matchy.start() > adapterIndex)
          return java.net.InetAddress.getByName(currMatch);
      }
      return java.net.InetAddress.getByName("255.255.255.0");
    }
    catch (java.net.UnknownHostException e)
    {
      throw new RuntimeException(e);
    }
  }

  // This returns the 40-byte MAC address
  public static byte[] getMACAddress()
  {
    final String[] macBuf = new String[1];

    try {
      macBuf[0] = sage.Sage.getMACAddress0();
      if(macBuf[0] != null) {
        byte[] rv = new byte[6];
        // The first digit is NOT always zero, don't skip it!
        for (int i = 0; i < macBuf[0].length(); i+=3)
        {
          rv[(i/3)] = (byte)(Integer.parseInt(macBuf[0].substring(i, i+2), 16) & 0xFF);
        }
        return rv;
      }
    } catch(Throwable t) {}

    try
    {
      String forcedMAC = Sage.get("forced_mac_address", null);
      if (forcedMAC != null && forcedMAC.length() > 0)
      {
        if (Sage.DBG) System.out.println("Using forced MAC address of: " + forcedMAC);
        macBuf[0] = forcedMAC;
      }
      else
      {
        String prefix;
        final Process procy = Runtime.getRuntime().exec(Sage.WINDOWS_OS ? "ipconfig /all" : "ifconfig", null, null);
        final java.util.regex.Pattern pat;// = java.util.regex.Pattern.compile(MiniClient.WINDOWS_OS ?
        //"Physical Address(\\. )*\\: (\\p{XDigit}\\p{XDigit}\\-\\p{XDigit}\\p{XDigit}\\-\\p{XDigit}\\p{XDigit}\\-\\p{XDigit}\\p{XDigit}\\-\\p{XDigit}\\p{XDigit}\\-\\p{XDigit}\\p{XDigit})" :
        //"(\\p{XDigit}\\p{XDigit}\\:\\p{XDigit}\\p{XDigit}\\:\\p{XDigit}\\p{XDigit}\\:\\p{XDigit}\\p{XDigit}\\:\\p{XDigit}\\p{XDigit}\\:\\p{XDigit}\\p{XDigit})");
        if (Sage.WINDOWS_OS)
          prefix = "";
        else if (Sage.MAC_OS_X)
          prefix = "ether";
        else
          prefix = ""; // no prefix for linux since language changes the label
        pat = java.util.regex.Pattern.compile(prefix + " ((\\p{XDigit}{2}[:-]){5}\\p{XDigit}{2})");
        Thread the = new Thread("InputStreamConsumer")
        {
          public void run()
          {
            try
            {
              java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(
                  procy.getInputStream()));
              String s;
              macBuf[0] = null;
              while ((s = buf.readLine()) != null)
              {
                java.util.regex.Matcher m = pat.matcher(s);
                // in case there's multiple adapters we only want the first one
                if (macBuf[0] == null && m.find())
                {
                  macBuf[0] = m.group(1);
                }
              }
              buf.close();
            }
            catch (Exception e){}
          }
        };
        the.setDaemon(true);
        the.start();
        Thread the2 = new Thread("ErrorStreamConsumer")
        {
          public void run()
          {
            try
            {
              java.io.BufferedReader buf = new java.io.BufferedReader(new java.io.InputStreamReader(
                  procy.getErrorStream()));
              while (buf.readLine() != null);
              buf.close();
            }
            catch (Exception e){}
          }
        };
        the2.setDaemon(true);
        the2.start();
        the.join();
        the2.join();
        procy.waitFor();
      }
      if (macBuf[0] != null)
      {
        byte[] rv = new byte[6];
        // The first digit is NOT always zero, so don't skip it
        for (int i = 0; i < macBuf[0].length(); i+=3)
        {
          rv[(i/3)] = (byte)(Integer.parseInt(macBuf[0].substring(i, i+2), 16) & 0xFF);
        }
        return rv;
      }
      else
        return null;
    }
    catch (Exception e)
    {
      System.out.println("Error getting MAC address of:" + e);
      return null;
    }
  }

  // Returns true if this socket connection came from the localhost
  public static boolean isLocalhostSocket(java.net.Socket sake)
  {
    byte[] localIP = sake.getLocalAddress().getAddress();
    byte[] remoteIP = sake.getInetAddress().getAddress();
    return ((remoteIP[0] == 127 && remoteIP[1] == 0 && remoteIP[2] == 0 && remoteIP[3] == 1) ||
        (remoteIP[0] == localIP[0] && remoteIP[1] == localIP[1] && remoteIP[2] == localIP[2] && remoteIP[3] == localIP[3]));
  }

  public static boolean safeEquals(Object o1, Object o2)
  {
    return (o1 == o2) || (o1 != null && o2 != null && o1.equals(o2));
  }

  // This returns a UTF-8 string on Windows, otherwise it just returns the string.
  // This is used for passing filenames to non-Unicode apps like FFMPEG
  public static String getLibAVFilenameString(String s)
  {
    if (Sage.WINDOWS_OS)
    {
      // If any non-ASCII characters are in this string; then create a temp file and put it in there instead.
      // This works around an issue where the Windows OS will do a codepage conversion on the UTF-8 values we're passing
      // on the command line. And also the Java bug where it doesn't send Unicode parameters to other processes.
      // We still write the bytes in the temp file as UTF-8 format though.
      boolean hasUni = false;
      for (int i = 0; i < s.length(); i++)
      {
        int c = s.charAt(i) & 0xFFFF;
        if (c > 127)
        {
          hasUni = true;
          break;
        }
      }
      if (hasUni)
      {
        try
        {
          java.io.File tmpFile = java.io.File.createTempFile("stvfm", ".txt");
          tmpFile.deleteOnExit();
          byte[] strBytes = s.getBytes("UTF-8");
          java.io.OutputStream os = new java.io.FileOutputStream(tmpFile);
          os.write(strBytes);
          os.close();
          return tmpFile.getAbsolutePath();
        } catch (java.io.IOException ex)
        {
          if (Sage.DBG) System.out.println("Error creating temp file for UTF-8 parameter passing:" + ex);
          return s;
        }
      }
      else
        return s;
    }
    else
      return s;
  }

  public static boolean safemkdirs(java.io.File f)
  {
    if (Sage.MAC_OS_X)
    {
      String fstr = f.toString();
      if (!fstr.startsWith("/Volumes/"))
        return f.mkdirs();
      // We can create all the dirs up until the one below Volumes
      java.util.Stack dirStack = new java.util.Stack();
      java.io.File currParent = null;
      while (true)
      {
        currParent = f.getParentFile();
        if (currParent == null)
          return false;
        if (currParent.getName().equals("Volumes"))
        {
          // We found the volumes root parent so we break out and create all the dirs on the stack
          break;
        }
        dirStack.push(f);
        f = currParent;
      }
      while (!dirStack.isEmpty())
      {
        if (!((java.io.File) dirStack.pop()).mkdir())
          return false;
      }
      return true;
    }
    else
      return f.mkdirs();
  }

  // Requires positive x
  static int stringSize(long x)
  {
    long p = 10;
    for (int i=1; i<19; i++)
    {
      if (x < p)
        return i;
      p = 10*p;
    }
    return 19;
  }

  final static byte[] digits = {
    '0' , '1' , '2' , '3' , '4' , '5' ,
    '6' , '7' , '8' , '9' , 'a' , 'b' ,
    'c' , 'd' , 'e' , 'f' , 'g' , 'h' ,
    'i' , 'j' , 'k' , 'l' , 'm' , 'n' ,
    'o' , 'p' , 'q' , 'r' , 's' , 't' ,
    'u' , 'v' , 'w' , 'x' , 'y' , 'z'
  };
  final static byte [] DigitTens = {
    '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
    '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
    '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
    '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
    '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
    '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
    '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
    '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
    '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
    '9', '9', '9', '9', '9', '9', '9', '9', '9', '9',
  } ;

  final static byte [] DigitOnes = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
  } ;

  private static final byte[] LONG_MIN_VALUE_STRING_BYTES = "-9223372036854775808".getBytes();

  // Returns the number of bytes written into the array
  public static int printLongInByteArray(long i, byte[] dest, int offset)
  {
    if (i == Long.MIN_VALUE)
    {
      System.arraycopy(LONG_MIN_VALUE_STRING_BYTES, 0, dest, offset, LONG_MIN_VALUE_STRING_BYTES.length);
      return LONG_MIN_VALUE_STRING_BYTES.length;
    }
    int size = (i < 0) ? stringSize(-i) + 1 : stringSize(i);
    long q;
    int r;
    int charPos = size + offset;
    char sign = 0;

    if (i < 0) {
      sign = '-';
      i = -i;
    }

    // Get 2 digits/iteration using longs until quotient fits into an int
    while (i > Integer.MAX_VALUE) {
      q = i / 100;
      // really: r = i - (q * 100);
      r = (int)(i - ((q << 6) + (q << 5) + (q << 2)));
      i = q;
      dest[--charPos] = DigitOnes[r];
      dest[--charPos] = DigitTens[r];
    }

    // Get 2 digits/iteration using ints
    int q2;
    int i2 = (int)i;
    while (i2 >= 65536) {
      q2 = i2 / 100;
      // really: r = i2 - (q * 100);
      r = i2 - ((q2 << 6) + (q2 << 5) + (q2 << 2));
      i2 = q2;
      dest[--charPos] = DigitOnes[r];
      dest[--charPos] = DigitTens[r];
    }

    // Fall thru to fast mode for smaller numbers
    // assert(i2 <= 65536, i2);
    for (;;) {
      q2 = (i2 * 52429) >>> (16+3);
      r = i2 - ((q2 << 3) + (q2 << 1));  // r = i2-(q2*10) ...
      dest[--charPos] = digits[r];
      i2 = q2;
      if (i2 == 0) break;
    }
    if (sign != 0) {
      dest[--charPos] = (byte)sign;
    }
    return size;
  }

  public static boolean isLocalhostAddress(java.net.InetAddress inetAddress)
  {
    byte[] remoteIP = inetAddress.getAddress();
    if (remoteIP[0] == 127 && remoteIP[1] == 0 && remoteIP[2] == 0 && remoteIP[3] == 1)
      return true;
    try
    {
      byte[] localIP = java.net.InetAddress.getLocalHost().getAddress();
      return (remoteIP[0] == localIP[0] && remoteIP[1] == localIP[1] && remoteIP[2] == localIP[2] && remoteIP[3] == localIP[3]);
    }
    catch (Exception e)
    {
      System.out.println("ERROR getting localhost address of:" + e);
    }
    return false;
  }

  static int _IOC_NRBITS = 8;
  static int _IOC_TYPEBITS = 8;
  static int _IOC_SIZEBITS = 14;
  static int _IOC_DIRBITS = 2;

  static int _IOC_NRMASK = ((1 << _IOC_NRBITS)-1);
  static int _IOC_TYPEMASK = ((1 << _IOC_TYPEBITS)-1);
  static int _IOC_SIZEMASK = ((1 << _IOC_SIZEBITS)-1);
  static int _IOC_DIRMASK = ((1 << _IOC_DIRBITS)-1);

  static int _IOC_NRSHIFT = 0;
  static int _IOC_TYPESHIFT = (_IOC_NRSHIFT+_IOC_NRBITS);
  static int _IOC_SIZESHIFT = (_IOC_TYPESHIFT+_IOC_TYPEBITS);
  static int _IOC_DIRSHIFT = (_IOC_SIZESHIFT+_IOC_SIZEBITS);

  static int _IOC_NONE = 0;
  static int _IOC_WRITE = 1;
  static int _IOC_READ = 2;

  static int _IOW(int type, int nr, int size)
  {
    int dir=_IOC_WRITE;
    return (((dir)  << _IOC_DIRSHIFT) |
        ((type) << _IOC_TYPESHIFT) |
        ((nr)   << _IOC_NRSHIFT) |
        ((size) << _IOC_SIZESHIFT));
  }

  static int IOCTL_USB_CONNECTINFO = _IOW('U', 17, 8 /* struct usb_connectinfo */);

  static long getUSBHWID()
  {
    // First get what is supposed to be the serial number; and then verify it exists
    String targetSerial = exec(new String[] { "tbutil", "getserial" });

    // Now convert this to the 64-bit long for the targetSerialID
    long targetSerialID = 0;
    int targetBitOffset = 0;
    try
    {
      int idx = 0;
      long currRead = targetSerial.charAt(idx++);
      while (true)
      {
        currRead = Integer.parseInt(((char) currRead) + "", 16);
        targetSerialID = targetSerialID ^ ((currRead & 0xF) << targetBitOffset);
        if (idx >= targetSerial.length())
          break;
        currRead = targetSerial.charAt(idx++);
        targetBitOffset += 4;
        targetBitOffset = targetBitOffset % 64;
      }
    }
    catch (Exception nfe)
    {
    }


    // Check in /sys/bus/usb/devices to find anything in there that has a 'serial' field; but only check the ones
    // that start with a number.
    String[] usbdevids = new java.io.File("/sys/bus/usb/devices").list();
    for (int i = 0; usbdevids != null && i < usbdevids.length; i++)
    {
      //System.out.println("Checking USB Dev ID=" + usbdevids[i]);
      if (Character.isDigit(usbdevids[i].charAt(0)))
      {
        // Check for the 'serial' field
        java.io.File serialFile = new java.io.File("/sys/bus/usb/devices/" + usbdevids[i] + "/serial");
        if (serialFile.isFile())
        {
          //System.out.println("Serial exists for this device...");
          int usbMajor = Integer.parseInt(usbdevids[i].substring(0, 1));
          java.io.Reader inReader = null;
          int usbMinor = -1;
          try
          {
            inReader = new java.io.FileReader("/sys/bus/usb/devices/" + usbdevids[i] + "/devnum");
            usbMinor = Integer.parseInt(((char)inReader.read()) + "");
            inReader.close();
            inReader = null;
          }
          catch (Exception e)
          {
            continue;
          }
          java.text.DecimalFormat leadZeros = new java.text.DecimalFormat("000");
          //System.out.println("USB dev num " + usbMajor + "-" + usbMinor);
          String verificationDevice = "/dev/bus/usb/" + leadZeros.format(usbMajor) + "/" + leadZeros.format(usbMinor);
          //System.out.println("Verifying w/ device:" + verificationDevice);
          int usb_fd = -1;
          byte[] buf1 = new byte[8];
          byte[] desc = new byte[18];
          try
          {
            usb_fd = jtux.UFile.open(verificationDevice, jtux.UConstant.O_RDWR);
            //System.out.println("ioctl "+IOCTL_USB_CONNECTINFO);
            int retval = jtux.UFile.ioctl(usb_fd, IOCTL_USB_CONNECTINFO, buf1);
            if(retval<0)
            {
              //System.out.println("Error "+retval);
              continue;
            }
            else
            {
              //these bufs create the 'devnum', but you'll need to check endian in java.nio.Buffer
              int checkedDevNum = 0;
              if(java.nio.ByteOrder.nativeOrder() != java.nio.ByteOrder.BIG_ENDIAN)
                checkedDevNum = ((buf1[3] & 0xFF) << 24) | ((buf1[2] & 0xFF) << 16) | ((buf1[1] & 0xFF) << 8) | (buf1[0] & 0xFF);
              else
                checkedDevNum = ((buf1[0] & 0xFF) << 24) | ((buf1[1] & 0xFF) << 16) | ((buf1[2] & 0xFF) << 8) | (buf1[3] & 0xFF);
              //System.out.println("checked dev num=" + checkedDevNum);
              if (checkedDevNum != usbMinor)
                continue;
            }
            jtux.UFile.read(usb_fd, desc, 18);
            // also make sure the serial index is non-zero
            //System.out.println("Manuf index "+ (desc[14]&0xFF) +
            //	" Product index "+ (desc[15]&0xFF) +
            //	" Serial index "+ (desc[16]&0xFF));
            if ((desc[16] & 0xFF) == 0)
              continue;
          }
          catch (jtux.UErrorException e)
          {
            //System.out.println(e);
            continue;
          }
          finally
          {
            try
            {
              jtux.UFile.close(usb_fd);
            }
            catch (jtux.UErrorException e1)
            {
            }
            usb_fd=-1;
          }

          // Now read the serial and convert it to a 64-bit integer to return
          long rv = 0;
          int rvBitOffset = 0;
          try
          {
            inReader = new java.io.FileReader("/sys/bus/usb/devices/" + usbdevids[i] + "/serial");
            long currRead = inReader.read();
            while (currRead != -1)
            {
              currRead = Integer.parseInt(((char) currRead) + "", 16);
              rv = rv ^ ((currRead & 0xF) << rvBitOffset);
              //System.out.println("Updating HWID rv=" + rv + " currRead=" + currRead + " rvBitOffset=" + rvBitOffset);
              currRead = inReader.read();
              rvBitOffset += 4;
              rvBitOffset = rvBitOffset % 64;
            }
            inReader.close();
            inReader = null;
          }
          catch (NumberFormatException nfe)
          {
            // This can happen reading the line terminator
            if (inReader != null)
            {
              try
              {
                inReader.close();
                inReader = null;
              }
              catch (Exception e2){}
            }
          }
          catch (Exception e)
          {
            continue;
          }
          if (targetSerialID != 0 && Math.abs(targetSerialID) != Math.abs(rv))
            continue;
          return Math.abs(rv);
        }
      }
    }
    return 0;
  }

  public static final int SMB_MOUNT_EXISTS = 0;
  public static final int SMB_MOUNT_SUCCEEDED = 1;
  public static final int SMB_MOUNT_FAILED = -1;
  public static Boolean has_smbmount;
  public static int doSMBMount(String smbPath, String localPath)
  {
    if (smbPath.startsWith("smb://"))
      smbPath = smbPath.substring(4);
    // Check if the mount is already done
    String grepStr;
    grepStr = "mount -t smbfs | grep -i \"" + localPath  + "\"";
    if (IOUtils.exec2(new String[] { "sh", "-c", grepStr }) == 0)
    {
      //if (Sage.DBG) System.out.println("SMB Mount already exists");
      return SMB_MOUNT_EXISTS;
    }
    else
    {
      if (Sage.DBG) System.out.println("SMB Mount Path: " + smbPath + " " + localPath);
      new java.io.File(localPath).mkdirs();
      // Extract any authentication information
      String smbUser = null;
      String smbPass = null;
      int authIdx = smbPath.indexOf('@');
      if (authIdx != -1)
      {
        int colonIdx = smbPath.indexOf(':');
        if (colonIdx != -1)
        {
          smbUser = smbPath.substring(2, colonIdx);
          smbPass = smbPath.substring(colonIdx + 1, authIdx);
          smbPath = "//" + smbPath.substring(authIdx + 1);
        }
      }

      String smbOptions;
      if (Sage.LINUX_OS)
      {
        if (smbUser != null)
          smbOptions = "username=" + smbUser + ",password=" + smbPass + ",iocharset=utf8";
        else
          smbOptions = "guest,iocharset=utf8";
      }
      else
      {
        if (smbUser != null)
          smbOptions = smbUser + ":" + smbPass;
        else
          smbOptions = "guest:";
      }
      // check to see if property exists if it doesn't check for smbmount with "which" command
      if (IOUtils.has_smbmount == null)
      {
          String result = IOUtils.exec(new String[] {"which", "smbmount"});
          // if nothing returned from "which" command then smbmount is not present so set property false
          if (result == null || result.length() == 0)
          {
              IOUtils.has_smbmount = Boolean.FALSE;
          } else {
              IOUtils.has_smbmount = Boolean.TRUE;
          }
      }
      // set execution variable based on static Boolean value
      String execSMBMount = IOUtils.has_smbmount ? "smbmount" : "mount.cifs";
      if (IOUtils.exec2(Sage.LINUX_OS ? new String[] { execSMBMount, smbPath, localPath , "-o", smbOptions } :
        new String[] { "mount_smbfs", "-N", "//" + smbOptions + "@" + smbPath.substring(2), localPath}) == 0)
      {
        if (Sage.DBG) System.out.println("SMB Mount Succeeded");
        return SMB_MOUNT_SUCCEEDED;
      }
      else
      {
        if (Sage.DBG) System.out.println("SMB Mount Failed");
        return SMB_MOUNT_FAILED;
      }
    }
  }

  public static final int NFS_MOUNT_EXISTS = 0;
  public static final int NFS_MOUNT_SUCCEEDED = 1;
  public static final int NFS_MOUNT_FAILED = -1;
  public static int doNFSMount(String nfsPath, String localPath)
  {
    if (!Sage.LINUX_OS) return NFS_MOUNT_FAILED;
    if (nfsPath.startsWith("nfs://"))
      nfsPath = nfsPath.substring(6);
    // Check if the mount is already done
    if (IOUtils.exec2(new String[] { "sh", "-c", "mount -t nfs | grep -i \"" + localPath  + "\"" }) == 0)
    {
      //if (Sage.DBG) System.out.println("NFS Mount already exists");
      return NFS_MOUNT_EXISTS;
    }
    else
    {
      if (Sage.DBG) System.out.println("NFS Mount Path: " + nfsPath + " " + localPath);
      new java.io.File(localPath).mkdirs();
      String nfsOptions = "nolock,tcp,rsize=32768,wsize=32768,noatime";
      int nfsRes = IOUtils.exec2(new String[] {"mount", "-t", "nfs", nfsPath, localPath, "-o", nfsOptions});
      if (nfsRes == 0)
      {
        if (Sage.DBG) System.out.println("NFS Mount Succeeded");
        return NFS_MOUNT_SUCCEEDED;
      }
      else
      {
        if (Sage.DBG) System.out.println("NFS Mount Failed res=" + nfsRes);
        return NFS_MOUNT_FAILED;
      }
    }
  }

  public static boolean undoMount(String currPath)
  {
    return IOUtils.exec2(new String[] { "umount", currPath}) == 0;
  }

  public static String convertSMBURLToUNCPath(String smbPath)
  {
    StringBuffer sb = new StringBuffer("\\\\" + smbPath.substring("smb://".length()));
    for (int i = 0; i < sb.length(); i++)
      if (sb.charAt(i) == '/')
        sb.setCharAt(i, '\\');
    return sb.toString();
  }

  /**
   * Creates a java.io.BufferedReader for the specified file and checks the first two bytes for the
   * Unicode BOM and sets the charset accordingly; otherwise if there's no BOM it'll use the passed
   * in defaultCharset
   *
   * @param filePath The file to be opened.
   * @param defaultCharset The charset to be used if the BOM is missing.
   * @return A BufferedReader.
   * @throws java.io.IOException If an I/O error occurs.
   */
  public static java.io.BufferedReader openReaderDetectCharset(String filePath, String defaultCharset) throws java.io.IOException
  {
    return openReaderDetectCharset(new java.io.File(filePath), defaultCharset, true);
  }

  /**
   * Creates a java.io.BufferedReader for the specified file and checks the first two bytes for the
   * Unicode BOM and sets the charset accordingly; otherwise if there's no BOM it'll use the passed
   * in defaultCharset
   *
   * @param filePath The file to be opened.
   * @param defaultCharset The charset to be used if the BOM is missing.
   * @return A <code>BufferedReader</code>.
   * @throws java.io.IOException If an I/O error occurs.
   */
  public static java.io.BufferedReader openReaderDetectCharset(java.io.File filePath, String defaultCharset) throws java.io.IOException
  {
    return openReaderDetectCharset(filePath, defaultCharset, true);
  }

  /**
   * Creates a java.io.BufferedReader for the specified file and checks the first two bytes for the
   * Unicode BOM and sets the charset accordingly; otherwise if there's no BOM it'll use the passed
   * in defaultCharset
   *
   * @param filePath The file to be opened.
   * @param defaultCharset The charset to be used if the BOM is missing.
   * @param local Pass true if the file is locally accessible. If this value is false, the
   *              BufferedReader will be backed by a MediaServerInputStream.
   * @return A <code>BufferedReader</code>.
   * @throws java.io.IOException If an I/O error occurs.
   */
  public static java.io.BufferedReader openReaderDetectCharset(java.io.File filePath, String defaultCharset, boolean local) throws java.io.IOException
  {
    java.io.InputStream fis = null;
    try
    {
      if (local)
        fis = new java.io.FileInputStream(filePath);
      else
      {
        // This is crucial. If we don't do this step the file will almost certainly be inaccessible.
        sage.NetworkClient.getSN().requestMediaServerAccess(filePath, true);
        fis = new SageInputStream(new RemoteSageFile(Sage.preferredServer, filePath, true));
      }

      if (fis.markSupported()) fis.mark(32768);

      int b1 = fis.read();
      int b2 = fis.read();
      // Check for big/little endian unicode marker; otherwise use the default charset to open
      String targetCharset = defaultCharset;
      if (b1 == 0xFF && b2 == 0xFE)
        targetCharset = "UTF-16LE";
      else if (b1 == 0xFE && b2 == 0xFF)
        targetCharset = "UTF-16BE";
      else if (Sage.I18N_CHARSET.equals(defaultCharset))
      {
        // Read 16k of data to verify that we have the proper charset if we think it's UTF8
        byte[] extraData = new byte[16384];
        extraData[0] = (byte)(b1 & 0xFF);
        extraData[1] = (byte)(b2 & 0xFF);
        int dataLen = 2 + fis.read(extraData, 2, extraData.length - 2);
        boolean utf8valid = true;
        for (int i = 0; i < dataLen && utf8valid; i++)
        {
          int c = extraData[i] & 0xFF;
          if (c <= 127)
            continue;
          if (i + 1 >= dataLen)
          {
            break;
          }
          switch (c >> 4)
          {
            case 12: case 13:
              /* 110x xxxx   10xx xxxx*/
              i++;
              c = extraData[i] & 0xFF;
              if ((c & 0xC0) != 0x80)
                utf8valid = false;
              break;
            case 14:
              /* 1110 xxxx  10xx xxxx  10xx xxxx */
              i++;
              c = extraData[i] & 0xFF;
              if ((c & 0xC0) != 0x80 || i + 1 >= dataLen)
                utf8valid = false;
              else
              {
                i++;
                c = extraData[i] & 0xFF;
                if ((c & 0xC0) != 0x80)
                  utf8valid = false;
              }
              break;
            default:
              /* 10xx xxxx,  1111 xxxx */
              utf8valid = false;
              break;
          }
        }
        if (!utf8valid)
        {
          if (Sage.DBG) System.out.println("Charset autodetection found invalid UTF8 data in the file; switching to default charset instead");
          // Cp1252 is a superset of ISO-8859-1; so it's preferable to use it since it'll decode more characters...BUT we really should
          // just use the platform default instead; that's generally what people will be using from a text editor.
          // And embedded doesn't support Cp1252, so we need to remove that from there and just use ISO-8859-1
          targetCharset = null;
        }
      }


      if (fis.markSupported())
      {
        fis.reset();
      }
      else
      {
        fis.close();

        if (local)
          fis = new java.io.FileInputStream(filePath);
        else
          fis = new SageInputStream(new RemoteSageFile(Sage.preferredServer, filePath, true));
      }

      if (targetCharset == null)
        return new java.io.BufferedReader(new java.io.InputStreamReader(fis));
      else
        return new java.io.BufferedReader(new java.io.InputStreamReader(fis, targetCharset));
    }
    catch (java.io.IOException e)
    {
      if (fis != null)
        fis.close();
      throw e;
    }
    finally
    {
      // If the file isn't accessed in some way before the media server timeout, this will prevent
      // the file from continuing to be accessed. Due to the nature of how we use the objects
      // returned from this method, that scenario is extremely unlikely.
      if (!local)
        sage.NetworkClient.getSN().requestMediaServerAccess(filePath, false);
    }
  }

  public static byte[] getCryptoKeys()
  {
    return (byte[]) (Sage.q);
  }

  public static String calcMD5(java.io.File f)
  {
    if (f != null && f.isFile())
    {
      java.io.FileInputStream fis = null;
      try
      {
        fis = new java.io.FileInputStream(f);
        java.security.MessageDigest algorithm = null;
        algorithm = java.security.MessageDigest.getInstance("MD5");
        algorithm.reset();
        byte[] buf = new byte[32768];
        int numRead = fis.read(buf);
        while (numRead > 0)
        {
          algorithm.update(buf, 0, numRead);
          numRead = fis.read(buf);
        }
        byte[] digest = algorithm.digest();
        StringBuilder finalSum = new StringBuilder(32); // The hash will always be 32 characters.
        for (int i = 0; i < digest.length; i++)
        {
          if (((int) (digest[i] & 0xFF)) <= 0x0F)
          {
            finalSum.append('0');
          }
          finalSum.append(Integer.toHexString((int)(digest[i] & 0xFF)));
        }
        return finalSum.toString().toUpperCase();
      }
      catch (Exception e)
      {
        /*if (Sage.DBG)*/ System.out.println("ERROR calculating MD5Sum of:" + e);
        return null;
      }
      finally
      {
        if (fis != null)
        {
          try
          {
            fis.close();
          }
          catch (Exception e)
          {}
        }
      }
    }
    else
      return null;

  }

  /**
   * Encode a <code>String</code> into a SHA-1 hash and return it represented as a
   * <code>String</code> formatted as hexadecimal.
   *
   * @param encodeValue The <code>String</code> to be encoded. The charset must be UTF-8.
   * @return The hexadecimal representation of the calculated hash or <code>null</code> if a
   *         <code>null</code> <code>String</code> is provided or the SHA-1 message digest doesn't
   *         exist on this platform.
   */
  public static String calcSHA1(String encodeValue)
  {
    if (encodeValue == null)
      return null;

    java.security.MessageDigest messageDigest;
    try
    {
      messageDigest = java.security.MessageDigest.getInstance("SHA");
    }
    catch (NoSuchAlgorithmException e)
    {
      System.out.println("Unable to get SHA-1 message digest algorithm!");
      return null;
    }

    messageDigest.reset();
    messageDigest.update(encodeValue.getBytes(StandardCharsets.UTF_8));
    byte shaBytes[] = messageDigest.digest();
    StringBuilder returnValue = new StringBuilder(40); // The hash will always be 40 characters.

    for (int i = 0; i < shaBytes.length; i++)
    {
      if ((shaBytes[i] & 0xFF) <= 0x0F)
      {
        returnValue.append('0');
      }
      returnValue.append(Integer.toHexString((shaBytes[i] & 0xFF)));
    }

    return returnValue.toString();
  }

  public static final String[] VFAT_MOUNTABLE_PARTITION_TYPES = { "6", "b", "c", "e", "f" };
  public static final String[] NTFS_MOUNTABLE_PARTITION_TYPES = { "7" };
  public static boolean isExternalDriveMounted(String devPath, String mountPath)
  {
    return (IOUtils.exec2(new String[] { "sh", "-c", "mount  | grep -i \"" + mountPath  + " type\"" }) == 0);
  }
  public static boolean mountExternalDrive(String devPath, String mountPath)
  {
    return mountExternalDrive(devPath, mountPath, false);
  }
  public static boolean mountExternalDrive(String devPath, String mountPath, boolean optimizePerformance)
  {
    if (!Sage.LINUX_OS) return false;
    // Check to see if it's already mounted
    if (isExternalDriveMounted(devPath, mountPath))
    {
      if (Sage.DBG) System.out.println("Ignoring mount for " + devPath + " because it's already mounted at: " + mountPath);
      return true;
    }
    // First use 'sfdisk' to determine the partition type so we know if we should
    // mount it w/ the '-o utf8' option or not
    String partNum = devPath.substring(devPath.length() - 1);
    String sfRes = IOUtils.exec(new String[] { "sfdisk", "-c", "/dev/" + devPath.substring(0, devPath.length() - 1), partNum});
    sfRes = sfRes.trim();
    if ("83".equals(sfRes) && optimizePerformance)
    {
      // Special options for mounting ext4 drives (this should fail if it's not ext4 since the 83 flag just means ext2/3/4)
      if (IOUtils.exec2(new String[] {"mount", "-t", "ext4", "/dev/" + devPath, mountPath, "-o", "noatime,barrier=0,data=writeback"}) == 0)
        return true;
    }
    // If we have a failure; then keep going and try all the possible ways to mount the drive.
    for (int i = 0; i < VFAT_MOUNTABLE_PARTITION_TYPES.length; i++)
    {
      if (sfRes.equals(VFAT_MOUNTABLE_PARTITION_TYPES[i]))
      {
        if (IOUtils.exec2(new String[] {"mount", "-t", "vfat",  "/dev/" + devPath, mountPath, "-o", "utf8,noatime"}) == 0)
          return true;
      }
    }
    for (int i = 0; i < NTFS_MOUNTABLE_PARTITION_TYPES.length; i++)
    {
      if (sfRes.equals(NTFS_MOUNTABLE_PARTITION_TYPES[i]))
      {
        if (IOUtils.exec2(new String[] {"mount", "/dev/" + devPath, mountPath, "-o", "nls=utf8,noatime"}) == 0)
          return true;
      }
    }
    if (devPath.length() == 3)
    {
      // This is for NTFS mounting since we mount the disk and not the partitions
      if (IOUtils.exec2(new String[] {"mount", "/dev/" + devPath, mountPath, "-o", "nls=utf8,noatime"}) == 0)
        return true;
    }
    if (IOUtils.exec2(new String[] {"mount", "/dev/" + devPath, mountPath, "-o", "noatime"}) == 0)
      return true;
    return (IOUtils.exec2(new String[] {"mount", "/dev/" + devPath, mountPath}) == 0);
  }

  // Reads a newline (\r\n or \n) terminated string (w/out returning the newline). rv can be used as a temp buffer, buf is the buffer used for reading the data and may already contain
  // extra data in it before calling this method, the SocketChannel passed in MUST be a blocking socket. Extra data may be in the buf after returning from this method call.
  public static String readLineBytes(java.nio.channels.SocketChannel s, java.nio.ByteBuffer buf, long timeout, StringBuffer rv) throws java.io.InterruptedIOException, java.io.IOException
  {
    if (rv == null)
      rv = new StringBuffer();
    else
      rv.setLength(0);
    boolean needsFlip = true;
    if (buf.hasRemaining() && buf.position() > 0)
      needsFlip = false;
    else
      buf.clear();
    int readRes = 0;
    TimeoutHandler.registerTimeout(timeout, s);
    try
    {
      if ((!buf.hasRemaining() || buf.position() == 0) && (readRes = s.read(buf)) <= 0)
      {
        throw new java.io.EOFException();
      }
    }
    finally
    {
      TimeoutHandler.clearTimeout(s);
    }
    if (needsFlip)
      buf.flip();
    int currByte = (buf.get() & 0xFF);
    while (true)
    {
      if (currByte == '\r')
      {
        if (!buf.hasRemaining())
        {
          buf.clear();
          TimeoutHandler.registerTimeout(timeout, s);
          try
          {
            if ((readRes = s.read(buf)) <= 0)
            {
              throw new java.io.EOFException();
            }
          }
          finally
          {
            TimeoutHandler.clearTimeout(s);
          }
          buf.flip();
        }
        currByte = (buf.get() & 0xFF);
        if (currByte == '\n')
        {
          return rv.toString();
        }
        rv.append('\r');
      }
      else if (currByte == '\n')
      {
        return rv.toString();
      }
      rv.append((char)currByte);
      if (!buf.hasRemaining())
      {
        buf.clear();
        TimeoutHandler.registerTimeout(timeout, s);
        try
        {
          if ((readRes = s.read(buf)) <= 0)
          {
            throw new java.io.EOFException();
          }
        }
        finally
        {
          TimeoutHandler.clearTimeout(s);
        }
        buf.flip();
      }
      currByte = (buf.get() & 0xFF);
    }
  }

  // Downloads all of the specified files from a SageTV server to the target destination files. Only allowed with
  // SageTVClient mode
  public static void downloadFilesFromSageTVServer(java.io.File[] srcFiles, java.io.File[] dstFiles) throws java.io.IOException
  {
    if (!Sage.client) throw new java.io.IOException("Cannot download files from SageTV server since we are not in client mode!");
    java.net.Socket sock = null;;
    java.io.DataOutputStream outStream = null;
    java.io.DataInputStream inStream = null;
    java.io.OutputStream fileOut = null;
    try
    {
      sock = new java.net.Socket();
      sock.connect(new java.net.InetSocketAddress(Sage.preferredServer, 7818), 5000);
      sock.setSoTimeout(30000);
      //sock.setTcpNoDelay(true);
      outStream = new java.io.DataOutputStream(new java.io.BufferedOutputStream(sock.getOutputStream()));
      inStream = new java.io.DataInputStream(new java.io.BufferedInputStream(sock.getInputStream()));
      byte[] xferBuf = new byte[32768];
      for (int i = 0; i < srcFiles.length; i++)
      {
        // Always request file access since this is generally not used for MediaFile objects
        NetworkClient.getSN().requestMediaServerAccess(srcFiles[i], true);
        outStream.write("OPENW ".getBytes(Sage.BYTE_CHARSET));
        outStream.write(srcFiles[i].toString().getBytes("UTF-16BE"));
        outStream.write("\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        String str = Sage.readLineBytes(inStream);
        if (!"OK".equals(str))
          throw new java.io.IOException("Error opening remote file of:" + str);
        // get the size
        outStream.write("SIZE\r\n".getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        str = Sage.readLineBytes(inStream);
        long remoteSize = Long.parseLong(str.substring(0, str.indexOf(' ')));
        fileOut = new java.io.FileOutputStream(dstFiles[i]);
        outStream.write(("READ 0 " + remoteSize + "\r\n").getBytes(Sage.BYTE_CHARSET));
        outStream.flush();
        while (remoteSize > 0)
        {
          int currRead = (int)Math.min(xferBuf.length, remoteSize);
          inStream.readFully(xferBuf, 0, currRead);
          fileOut.write(xferBuf, 0, currRead);
          remoteSize -= currRead;
        }
        fileOut.close();
        fileOut = null;
        // CLOSE happens automatically when you open a new file
        //outStream.write("CLOSE\r\n".getBytes(Sage.BYTE_CHARSET));
        //outStream.flush();
        NetworkClient.getSN().requestMediaServerAccess(srcFiles[i], false);
      }
    }
    finally
    {
      try{
        if (sock != null)
          sock.close();
      }catch (Exception e1){}
      try{
        if (outStream != null)
          outStream.close();
      }catch (Exception e2){}
      try{
        if (inStream != null)
          inStream.close();
      }catch (Exception e3){}
      try{
        if (fileOut != null)
          fileOut.close();
      }catch (Exception e4){}
    }
  }

  // This uses the old MVP server which as part of the protocol will return the MAC address of the other system
  public static String getServerMacAddress(String hostname)
  {
    // Strip off any port that may be there
    if (hostname.indexOf(":") != -1)
      hostname = hostname.substring(0, hostname.indexOf(":"));
    if (Sage.DBG) System.out.println("Requested to find MAC address of server at: " + hostname);
    java.net.DatagramSocket sock = null;
    try
    {
      sock = new java.net.DatagramSocket(16882); // they always come back on this port
      java.net.DatagramPacket pack = new java.net.DatagramPacket(new byte[50], 50);
      byte[] data = pack.getData();
      data[3] = 1;
      String localIP;
      if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
        localIP = java.net.InetAddress.getLocalHost().getHostAddress();
      else
        localIP = LinuxUtils.getIPAddress();
      if (Sage.DBG) System.out.println("Local IP is " + localIP);
      int idx = localIP.indexOf('.');
      data[16] = (byte)(Integer.parseInt(localIP.substring(0, idx)) & 0xFF);
      localIP = localIP.substring(idx + 1);
      idx = localIP.indexOf('.');
      data[17] = (byte)(Integer.parseInt(localIP.substring(0, idx)) & 0xFF);
      localIP = localIP.substring(idx + 1);
      idx = localIP.indexOf('.');
      data[18] = (byte)(Integer.parseInt(localIP.substring(0, idx)) & 0xFF);
      localIP = localIP.substring(idx + 1);
      data[19] = (byte)(Integer.parseInt(localIP) & 0xFF);
      pack.setLength(50);
      // Find the broadcast address for this subnet.
      pack.setAddress(java.net.InetAddress.getByName(hostname));
      pack.setPort(16881);
      sock.send(pack);
      sock.setSoTimeout(3000);
      sock.receive(pack);
      if (pack.getLength() >= 20)
      {
        if (Sage.DBG) System.out.println("MAC discovery packet received:" + pack);
        String mac = (((data[8] & 0xFF) < 16) ? "0" : "") + Integer.toString(data[8] & 0xFF, 16) + ":" +
            (((data[9] & 0xFF) < 16) ? "0" : "") + Integer.toString(data[9] & 0xFF, 16) + ":" +
            (((data[10] & 0xFF) < 16) ? "0" : "") + Integer.toString(data[10] & 0xFF, 16) + ":" +
            (((data[11] & 0xFF) < 16) ? "0" : "") + Integer.toString(data[11] & 0xFF, 16) + ":" +
            (((data[12] & 0xFF) < 16) ? "0" : "") + Integer.toString(data[12] & 0xFF, 16) + ":" +
            (((data[13] & 0xFF) < 16) ? "0" : "") + Integer.toString(data[13] & 0xFF, 16);
        if (Sage.DBG) System.out.println("Resulting MAC=" + mac);
        return mac;
      }
    }
    catch (Exception e)
    {
      //System.out.println("Error discovering servers:" + e);
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
    return null;

  }

  public static void sendWOLPacket(String macAddress)
  {
    // Strip off any port that may be there
    if (Sage.DBG) System.out.println("Sending out WOL packet to MAC address: " + macAddress);
    java.net.DatagramSocket sock = null;
    try
    {
      sock = new java.net.DatagramSocket(9); // WOL is on port 9
      java.net.DatagramPacket pack = new java.net.DatagramPacket(new byte[102], 102);
      byte[] data = pack.getData();
      data[0] = (byte)0xFF;
      data[1] = (byte)0xFF;
      data[2] = (byte)0xFF;
      data[3] = (byte)0xFF;
      data[4] = (byte)0xFF;
      data[5] = (byte)0xFF;
      byte[] macBytes = new byte[6];
      java.util.StringTokenizer macToker = new java.util.StringTokenizer(macAddress, ":-");
      for (int i = 0; i < macBytes.length; i++)
      {
        macBytes[i] = (byte)(Integer.parseInt(macToker.nextToken(), 16) & 0xFF);
      }
      for (int i = 0; i < 16; i++)
      {
        System.arraycopy(macBytes, 0, data, 6*(i + 1), 6);
      }
      String localIP;
      if (Sage.WINDOWS_OS || Sage.MAC_OS_X)
        localIP = java.net.InetAddress.getLocalHost().getHostAddress();
      else
        localIP = LinuxUtils.getIPAddress();
      if (Sage.DBG) System.out.println("Local IP is " + localIP);
      int lastDot = localIP.lastIndexOf('.');
      String broadcastIP = localIP.substring(0, lastDot) + ".255";
      if (Sage.DBG) System.out.println("Broadcast IP is " + broadcastIP);
      pack.setLength(102);
      pack.setAddress(java.net.InetAddress.getByName(broadcastIP));
      pack.setPort(9);
      sock.send(pack);
    }
    catch (Exception e)
    {
      //System.out.println("Error discovering servers:" + e);
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
  }

  public static byte[] getFileAsBytes(java.io.File f)
  {
    try
    {
      java.io.InputStream is = new java.io.FileInputStream(f);
      byte[] rv = new byte[(int)f.length()];
      int numRead = is.read(rv);
      while (numRead < rv.length)
      {
        int currRead = is.read(rv, numRead, rv.length - numRead);
        if (currRead < 0)
          break;
        numRead += currRead;
      }
      is.close();
      return rv;
    }
    catch (java.io.IOException e)
    {
      if (Sage.DBG) System.out.println("ERROR reading file " + f + " of:" + e);
      return null;
    }

  }
}
