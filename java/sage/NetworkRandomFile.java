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

/*
 * We use the read caching from the FasterRandomFile class to optimize our network access.
 * We don't support any writing via this class of course
 */
public class NetworkRandomFile extends FasterRandomFile
{
  public NetworkRandomFile(String inHostname, String name, String mode, String inCharset, String inTranscodeMode) throws java.io.IOException
  {
    super(inCharset);
    if (mode.indexOf("c") != -1 || mode.indexOf("w") != -1)
      throw new java.io.IOException("Unsupported mode for remote random file:" + mode);
    remoteFilename = name;
    hostname = inHostname;
    transcodeMode = inTranscodeMode;
    setBufferSize(32768);
    openConnection();
  }

  public NetworkRandomFile(String inHostname, java.io.File file, String mode, String inCharset, String inTranscodeMode) throws java.io.IOException
  {
    this(inHostname, file.toString(), mode, inCharset, inTranscodeMode);
  }

  public NetworkRandomFile(String inHostname, String name, String mode, String inCharset) throws java.io.IOException
  {
    this(inHostname, name, mode, inCharset, null);
  }

  public NetworkRandomFile(String inHostname, java.io.File file, String mode, String inCharset) throws java.io.IOException
  {
    this(inHostname, file.toString(), mode, inCharset);
  }

  protected void reopenConnection() throws java.io.IOException
  {
    closeConnection();
    openConnection();
  }

  protected synchronized void openConnection() throws java.io.IOException
  {
    sock = new java.net.Socket();
    sock.connect(new java.net.InetSocketAddress(hostname, 7818));
    sock.setSoTimeout(30000);
    outStream = new java.io.DataOutputStream(new java.io.BufferedOutputStream(sock.getOutputStream()));
    inStream = new java.io.DataInputStream(new java.io.BufferedInputStream(sock.getInputStream()));
    if (transcodeMode != null && transcodeMode.length() > 0)
    {
      outStream.write(("XCODE_SETUP " + transcodeMode + "\r\n").getBytes(Sage.BYTE_CHARSET));
      outStream.flush();
      String str = Sage.readLineBytes(inStream);
      if (!"OK".equals(str))
        throw new java.io.IOException("Error with remote transcode setup for " + transcodeMode + " of: " + str);
    }
    outStream.write("OPENW ".getBytes(Sage.BYTE_CHARSET));
    outStream.write(remoteFilename.getBytes("UTF-16BE"));
    outStream.write("\r\n".getBytes(Sage.BYTE_CHARSET));
    outStream.flush();
    String str = Sage.readLineBytes(inStream);
    if (!"OK".equals(str))
      throw new java.io.IOException("Error opening remote file of:" + str);
  }

  public boolean isTranscoding()
  {
    return transcodeMode != null && transcodeMode.length() > 0;
  }

  protected void closeConnection()
  {
    if (outStream != null)
    {
      synchronized (this)
      {
        try
        {
          outStream.write("QUIT\r\n".getBytes(Sage.BYTE_CHARSET));
          outStream.flush();
        }
        catch (Exception e){}
      }
    }
    if (inStream != null)
    {
      try{inStream.close();}catch(Exception e){}
      inStream = null;
    }
    if (outStream != null)
    {
      try{outStream.close();}catch(Exception e){}
      outStream = null;
    }
    if (sock != null)
    {
      try{sock.close();}catch(Exception e){}
      sock = null;
    }
  }

  public void flush() throws java.io.IOException
  {
    // Flushes the read buffer only in this case
    rbuffptr = 0;
    rbufflen = 0;
  }

  public long length() throws java.io.IOException
  {
    // If we're in the middle of a transcode seek then we don't know what the real size is
    if (dontWaitOnNextRead)
      return currTotalSize;
    try
    {
      return getRemoteSize();
    }
    catch (java.io.IOException e)
    {
      // Retry the connection once
      reopenConnection();
      return getRemoteSize();
    }
  }

  protected synchronized long getRemoteSize() throws java.io.IOException
  {
    outStream.write("SIZE\r\n".getBytes(Sage.BYTE_CHARSET));
    outStream.flush();
    String str = Sage.readLineBytes(inStream);
    long currAvailSize = Long.parseLong(str.substring(0, str.indexOf(' ')));
    currTotalSize = Long.parseLong(str.substring(str.indexOf(' ') + 1));
    maxRemoteSize = Math.max(maxRemoteSize, currAvailSize);
    if (currAvailSize != currTotalSize)
    {
      activeFile = true;
    }
    return maxRemoteSize;
  }

  public void seek(long newfp) throws java.io.IOException
  {
    // See if we can do this within the buffer we have
    if ((newfp > fp - rbuffptr) && newfp < (fp + rbufflen - rbuffptr))
    {
      rbuffptr += (int) (newfp - fp);
      fp = newfp;
    }
    else
    {
      flush();
      fp = newfp;
      dontWaitOnNextRead = isTranscoding();
    }
  }

  protected int readFromServer(int length) throws java.io.IOException
  {
    if (length == 0) return 0;
    if (length == -1)
    {
      if (maxRemoteSize - fp > rbuff.length || dontWaitOnNextRead)
      {
        length = rbuff.length;
      }
      else
      {
        getRemoteSize();
        length = (int)Math.min(rbuff.length, maxRemoteSize - fp);
        int numTries = 200;
        while (activeFile && length < rbuff.length && numTries-- > 0)
        {
          try{Thread.sleep(50);}catch(Exception e){}
          getRemoteSize();
          length = (int)Math.min(rbuff.length, maxRemoteSize - fp);
        }
      }
    }
    if (length <= 0)
      return -1; // end of file
    readFromServer(rbuff, 0, length);
    dontWaitOnNextRead = false;
    return length;
  }

  protected synchronized void readFromServer(byte[] theBuff, int theOff, int theLength) throws java.io.IOException
  {
    try
    {
      outStream.write(("READ " + fp + " " + theLength + "\r\n").getBytes(Sage.BYTE_CHARSET));
      outStream.flush();
      inStream.readFully(theBuff, theOff, theLength);
    }catch (java.io.IOException e)
    {
      // One more try
      reopenConnection();
      outStream.write(("READ " + fp + " " + theLength + "\r\n").getBytes(Sage.BYTE_CHARSET));
      outStream.flush();
      inStream.readFully(theBuff, theOff, theLength);
    }
  }

  protected void ensureBuffer() throws java.io.IOException
  {
    if (rbufflen <= rbuffptr)
    {
      rbufflen = readFromServer(-1);
      rbuffptr = 0;
      if (rbufflen < 0)
        throw new java.io.EOFException();
    }
  }

  public void readFully(byte b[], int off, int len) throws java.io.IOException
  {
    int leftToRead = len;
    if (optimizeReadFully)
    {
      int leftInBuffer = rbufflen - rbuffptr;
      if (leftInBuffer > 0)
      {
        int currRead = Math.min(leftToRead, rbufflen - rbuffptr);
        System.arraycopy(rbuff, rbuffptr, b, off, currRead);
        leftToRead -= currRead;
        rbuffptr += currRead;
        fp += currRead;
      }
      if (leftToRead > 0)
      {
        readFromServer(b, len - leftToRead + off, leftToRead);
        fp += leftToRead;
      }
    }
    else
    {
      do
      {
        ensureBuffer();
        int currRead = Math.min(leftToRead, rbufflen - rbuffptr);
        System.arraycopy(rbuff, rbuffptr, b, off, currRead);
        leftToRead -= currRead;
        rbuffptr += currRead;
        off += currRead;
        fp += currRead;
      }while (leftToRead > 0);
    }
    if (crypto)
    {
      for (int i = off; i < off + len; i++)
      {
        int x = b[i] ^ (cryptoKeys[((int)(fp++)) % 128]);
        b[i] = (byte)(((x & 0x0F) << 4) | ((x & 0xF0) >> 4));
      }
    }
  }

  public int skipBytes(int n) throws java.io.IOException
  {
    rbuffptr = 0;
    rbufflen = 0;
    fp += n;
    return n;
  }

  public void close() throws java.io.IOException
  {
    activeFile = false;
    closeConnection();
    buff = null;
  }

  public void fullFlush() throws java.io.IOException
  {
    flush();
  }

  public void write(int b) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void write(byte b) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void writeUnencryptedByte(byte b) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void write(byte b[], int off, int len) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void write(byte b[])	throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void writeUTF(String s) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void writeBoolean(boolean b)	throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public void writeByte(int b) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  public final void setLength(long len) throws java.io.IOException
  {
    throw new java.io.IOException("Unsupported operation");
  }

  protected String remoteFilename;
  protected java.net.Socket sock = null;
  protected java.io.DataOutputStream outStream = null;
  protected java.io.DataInputStream inStream = null;
  protected String hostname;

  protected long maxRemoteSize;
  protected boolean activeFile;
  protected String transcodeMode;

  // This is for when we seek in transcoding mode; we shouldn't wait for the file size to be correct
  // since it won't be until we inform the server that we're trying to read from a different position in the file.
  protected boolean dontWaitOnNextRead;

  protected long currTotalSize;
}
