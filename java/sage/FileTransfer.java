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

public class FileTransfer extends SystemTask
{
  private static java.util.Map uiXferMap = new java.util.WeakHashMap();
  public static FileTransfer getFileTransfer(UIManager uiMgr)
  {
    if (uiXferMap.containsKey(uiMgr))
      return (FileTransfer) uiXferMap.get(uiMgr);
    FileTransfer rv = new FileTransfer(uiMgr);
    uiXferMap.put(uiMgr, rv);
    return rv;
  }
  public FileTransfer(UIManager inUIMgr)
  {
    uiMgrWeak = new java.lang.ref.WeakReference(inUIMgr);
  }

  private boolean shouldIncludeAllFileTypes()
  {
    UIManager uiMgr = (UIManager) uiMgrWeak.get();

    if (uiMgr.isServerUI())
    {
      return uiMgr.getBoolean("file_browser/include_all_files", false);
    }
    else
    {
      try
      {
        Object serverProp = SageTV.api("GetServerProperty", new Object[] { "file_browser/clients_can_include_all_files", "false" });
        if (serverProp != null && "true".equalsIgnoreCase(serverProp.toString()))
        {
          return uiMgr.getBoolean("file_browser/include_all_files", false);
        }
      }
      catch (Throwable t)
      {
        if (Sage.DBG) System.out.println("ERROR executing server API call of:" + t);
        t.printStackTrace();
      }
      return false;
    }
  }

  // srcFile is either a file or a directory, it's copied to destFile which must be a directory
  public synchronized Object transferFile(String srcFileStr, java.io.File destFile)
  {
    statusMessage = "";
    orgDestFile = destFile;
    if (Sage.DBG) System.out.println("Transfer requested for files src=" + srcFileStr + " dest=" + destFile);
    UIManager uiMgr = (UIManager) uiMgrWeak.get();
    remoteUIXfer = uiMgr.getUIClientType() == UIClient.REMOTE_UI && uiMgr.hasRemoteFSSupport();
    smbMountHolder = null;
    if (srcFileStr.startsWith("smb://"))
    {
      remoteUIXfer = false;
      smbMountHolder = srcFileStr;
      srcFileStr = FSManager.getInstance().requestLocalSMBAccess(srcFileStr);
      if (srcFileStr == null)
        return Boolean.FALSE;
    }
    java.io.File srcFile = new java.io.File(srcFileStr);
    if (remoteUIXfer)
    {
      MiniClientSageRenderer mcsr = (MiniClientSageRenderer)uiMgr.getRootPanel().getRenderEngine();
      int fileStats = mcsr.fsGetPathAttributes(srcFile.toString());
      if ((fileStats & MiniClientSageRenderer.FS_PATH_FILE) != 0)
      {
        srcFiles = new java.io.File[] { srcFile };
        destFiles = new java.io.File[] { new java.io.File(destFile, srcFile.getName()) };
      }
      else if ((fileStats & MiniClientSageRenderer.FS_PATH_DIRECTORY) != 0)
      {
        String[] srcFileStrs = listRemoteFilesRecursive(srcFile, shouldIncludeAllFileTypes());
        srcFiles = new java.io.File[srcFileStrs.length];
        for (int i = 0; i < srcFileStrs.length; i++)
          srcFiles[i] = new java.io.File(srcFileStrs[i]);
        destFiles = new java.io.File[srcFiles.length];
        if (Sage.DBG) System.out.println("Dir copy from " + srcFile + " to " + destFile + " is " + srcFiles.length + " files");
        int srcFilePrefix = (srcFile.getParentFile() != null) ? srcFile.getParentFile().getAbsolutePath().length() :
          srcFile.getAbsolutePath().length();
        for (int i = 0; i < srcFiles.length; i++)
        {
          destFiles[i] = new java.io.File(destFile + srcFiles[i].getAbsolutePath().substring(srcFilePrefix));
        }
      }
      else
      {
        if (Sage.DBG) System.out.println("Using local instead of remote xfer since file does not exist in remote filesystem");
        remoteUIXfer = false;
      }
    }
    if (!remoteUIXfer)
    {
      if (srcFile.isFile())
      {
        srcFiles = new java.io.File[] { srcFile };
        destFiles = new java.io.File[] { new java.io.File(destFile, srcFile.getName()) };
      }
      else if (srcFile.isDirectory())
      {
        srcFiles = IOUtils.listFilesRecursive(srcFile, shouldIncludeAllFileTypes());
        destFiles = new java.io.File[srcFiles.length];
        if (Sage.DBG) System.out.println("Dir copy from " + srcFile + " to " + destFile + " is " + srcFiles.length + " files");
        int srcFilePrefix = (srcFile.getParentFile() != null) ? srcFile.getParentFile().getAbsolutePath().length() :
          srcFile.getAbsolutePath().length();
        String destFileStr = destFile.getAbsolutePath();
        if (!destFileStr.endsWith(java.io.File.separator))
          destFileStr += java.io.File.separator;
        for (int i = 0; i < srcFiles.length; i++)
        {
          destFiles[i] = new java.io.File(destFileStr + srcFiles[i].getAbsolutePath().substring(srcFilePrefix));
        }
      }
      else
      {
        cleanup();
        return Boolean.FALSE;
      }
    }
    // Find the total size of the files
    totalFileSize = 0;
    if (remoteUIXfer)
    {
      MiniClientSageRenderer mcsr = (MiniClientSageRenderer)uiMgr.getRootPanel().getRenderEngine();
      for (int i = 0; i < srcFiles.length; i++)
        totalFileSize += mcsr.fsGetFileSize(srcFiles[i].toString());
    }
    else
    {
      for (int i = 0; i < srcFiles.length; i++)
        totalFileSize += srcFiles[i].length();
    }

    if (Sage.DBG) System.out.println("Total size of original source files for xfer: " + totalFileSize);

    // Perform the storage request so we know we've got enough diskspace for the upload
    if (Sage.client)
    {
      uploadKey = NetworkClient.getSN().requestUploadSpace(destFile, totalFileSize);
      if (uploadKey < 0)
      {
        if (Sage.DBG) System.out.println("Server was unable to clear up enough free space for library import");
        statusMessage = Sage.rez("NO_SPACE_FOR_COPY");
        cleanup();
        return Boolean.FALSE;
      }
    }
    else
    {
      if (!destFile.isDirectory() && destFile.exists())
      {
        cleanup();
        return Boolean.FALSE;
      }
      if (!destFile.exists())
      {
        if (destFile.getParentFile() != null)
          IOUtils.safemkdirs(destFile.getParentFile());
      }

      // Be sure we've got enough space for our new files
      long freeSpace = Sage.getDiskFreeSpace(destFile.getAbsolutePath());

      if (totalFileSize > freeSpace)
      {
        if (!Sage.WINDOWS_OS || SeekerSelector.getInstance().isPathInManagedStorage(destFile))
        {
          // Make a storage request with Seeker to get the space we need, and then see if we've got the space
          // now and then cancel the request.
          if (Sage.DBG) System.out.println("Requesting Seeker to clear up " + ((totalFileSize - freeSpace)/1000000) + "MB worth of space");
          java.io.File tempFile = SeekerSelector.getInstance().requestDirectoryStorage("scratch", totalFileSize - freeSpace);
          synchronized (SeekerSelector.getInstance())
          {
            SeekerSelector.getInstance().kick();
            try
            {
              SeekerSelector.getInstance().wait(5000);
            }catch (InterruptedException e){}
          }
          freeSpace = Sage.getDiskFreeSpace(destFile.getAbsolutePath());
          SeekerSelector.getInstance().clearDirectoryStorageRequest(tempFile);
          if (totalFileSize > freeSpace)
          {
            if (Sage.DBG) System.out.println("Unable to clear up enough free space for library import");
            statusMessage = Sage.rez("NO_SPACE_FOR_COPY");
            cleanup();
            return Boolean.FALSE;
          }
        }
        else
        {
          statusMessage = Sage.rez("NO_SPACE_FOR_COPY");
          cleanup();
          return Boolean.FALSE;
        }
      }
    }

    startTaskThread("FileTransfer");

    return Boolean.TRUE;
  }

  public void taskRun()
  {
    long totalCopied = 0;
    byte[] buf = new byte[Sage.getInt("file_transfer_buffer_size", 65536)];
    java.text.NumberFormat prctForm = java.text.NumberFormat.getPercentInstance();
    UIManager uiMgr = (UIManager) uiMgrWeak.get();
    try
    {
      for (int i = 0; i < srcFiles.length; i++)
      {
        if (abort) return;
        if (Sage.DBG) System.out.println("Copying " + srcFiles[i] + " to " + destFiles[i]);
        java.io.InputStream is = null;
        if (Sage.client/*isTrueClient()*/)
        {
          java.net.Socket sock = null;
          java.io.DataOutputStream outStream = null;
          java.io.DataInputStream inStream = null;
          java.io.BufferedOutputStream fileOut = null;
          try
          {
            sock = new java.net.Socket(/*host*/Sage.preferredServer, 7818);
            sock.setSoTimeout(30000);
            //sock.setTcpNoDelay(true);
            outStream = new java.io.DataOutputStream(new java.io.BufferedOutputStream(sock.getOutputStream()));
            inStream = new java.io.DataInputStream(new java.io.BufferedInputStream(sock.getInputStream()));
            outStream.write(("WRITEOPEN " + destFiles[i] + " " + Integer.toString(uploadKey) + "\r\n").getBytes(Sage.BYTE_CHARSET));
            outStream.flush();
            String str = Sage.readLineBytes(inStream);
            if (!"OK".equals(str))
              throw new java.io.IOException("Error opening remote file of:" + str);

            long fileSize = srcFiles[i].length();
            outStream.write(("WRITE 0 " + fileSize + "\r\n").getBytes(Sage.BYTE_CHARSET));
            outStream.flush();

            is = new java.io.FileInputStream(srcFiles[i]);
            int numRead = is.read(buf);
            while (numRead > 0 && fileSize > 0)
            {
              if (abort) return;
              totalCopied += numRead;
              fileSize -= numRead;
              statusMessage = prctForm.format(((double)totalCopied)/totalFileSize);
              outStream.write(buf, 0, numRead);
              numRead = is.read(buf);
            }
            outStream.write("QUIT\r\n".getBytes(Sage.BYTE_CHARSET));
            outStream.flush();
          }
          catch (java.io.IOException e)
          {
            statusMessage = "Error:" + e.toString();
            return;
          }
          finally
          {
            try
            {
              if (sock != null)
                sock.close();
            }
            catch (Exception e){}
            try
            {
              if (outStream != null)
                outStream.close();
            }
            catch (Exception e){}
            try
            {
              if (inStream != null)
                inStream.close();
            }
            catch (Exception e){}
            try
            {
              if (fileOut != null)
                fileOut.close();
            }
            catch (Exception e){}
          }
        }
        else if (remoteUIXfer)
        {
          MiniClientSageRenderer mcsr = (MiniClientSageRenderer)uiMgr.getRootPanel().getRenderEngine();
          IOUtils.safemkdirs(destFiles[i].getParentFile());
          MiniClientSageRenderer.RemoteFSXfer xferOp = mcsr.fsUploadFile(destFiles[i], srcFiles[i].toString());
          long startingTotal = totalCopied;
          while (!xferOp.isDone())
          {
            try{Thread.sleep(250);}catch (Exception e){}
            totalCopied = startingTotal + xferOp.getBytesXferd();
            statusMessage = prctForm.format(((double)totalCopied)/totalFileSize);
            if (abort)
            {
              xferOp.abortNow();
              break;
            }
          }
          if (xferOp.error != 0)
          {
            statusMessage = "Error: " + xferOp.error;
          }
        }
        else
        {
          IOUtils.safemkdirs(destFiles[i].getParentFile());
          java.io.OutputStream os = null;
          try
          {
            // Don't overwrite the file with itself
            if (srcFiles[i].getCanonicalFile().equals(destFiles[i].getCanonicalFile()))
              continue;

            is = new java.io.FileInputStream(srcFiles[i]);
            os = new java.io.FileOutputStream(destFiles[i]);
            int numRead = is.read(buf);
            while (numRead > 0)
            {
              if (abort) return;
              totalCopied += numRead;
              statusMessage = prctForm.format(((double)totalCopied)/totalFileSize);
              os.write(buf, 0, numRead);
              numRead = is.read(buf);
            }
          }
          catch (java.io.IOException e)
          {
            statusMessage = "Error:" + e.toString();
            return;
          }
          finally
          {
            try
            {
              if (is != null) is.close();
            }catch (Exception e){}
            try
            {
              if (os != null) os.close();
            }catch (Exception e){}
            is = null;
            os = null;
          }
        }
      }
    }
    finally
    {
      if (Sage.client)
        NetworkClient.getSN().requestUploadSpace(orgDestFile, 0);
      cleanup();
    }

    succeeded();
  }

  protected String[] listRemoteFilesRecursive(java.io.File f, boolean allFileTypes)
  {
    java.util.ArrayList rv = new java.util.ArrayList();
    // I'd rather not use canonical paths over the remote FS protocol; so for this special case
    // just limit it to going no more than 32 directories deep, which should cover anything realistic
    listRemoteFilesRecursive(f.toString(), rv, 32, allFileTypes);
    return (String[]) rv.toArray(Pooler.EMPTY_STRING_ARRAY);
  }
  private void listRemoteFilesRecursive(String f, java.util.ArrayList rv, int depth, boolean allFileTypes)
  {
    // protect against infinite recursion due to symbolic links on Linux
    if (depth == 0) return;
    UIManager uiMgr = (UIManager) uiMgrWeak.get();
    if (!f.endsWith("\\") && !f.endsWith("/"))
      f = f + java.io.File.separator;
    MiniClientSageRenderer mcsr = (MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine();
    String[] kids = mcsr.fsDirListing(f);
    for (int i = 0; kids != null && i < kids.length; i++)
    {
      String currPath = kids[i];
      int fileStats = mcsr.fsGetPathAttributes(currPath);
      if ((fileStats & MiniClientSageRenderer.FS_PATH_FILE) != 0)
      {
        if (allFileTypes || SeekerSelector.getInstance().hasImportableFileExtension(currPath))
          rv.add(currPath);
      }
      else if ((fileStats & MiniClientSageRenderer.FS_PATH_DIRECTORY) != 0)
        listRemoteFilesRecursive(currPath, rv, depth - 1, allFileTypes);
    }
  }

  private void cleanup()
  {
    if (smbMountHolder != null)
    {
      FSManager.getInstance().releaseLocalSMBAccess(smbMountHolder);
      smbMountHolder = null;
    }
  }

  protected long totalFileSize;
  protected java.io.File[] srcFiles;
  protected java.io.File[] destFiles;
  protected java.io.File orgDestFile;
  protected int uploadKey;
  protected java.lang.ref.WeakReference uiMgrWeak;
  protected boolean remoteUIXfer;
  protected String smbMountHolder; // must release when done
}
