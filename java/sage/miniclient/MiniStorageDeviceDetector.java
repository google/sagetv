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

/**
 *
 * @author Narflex
 */
public class MiniStorageDeviceDetector implements Runnable
{

  /** Creates a new instance of MiniStorageDeviceDetector */
  public MiniStorageDeviceDetector()
  {
  }

  public void run()
  {
    System.out.println("StorageDeviceDetector started...");
    if (MiniClient.WINDOWS_OS || MiniClient.MAC_OS_X)
    {
      java.io.File volDir = new java.io.File("/Volumes");
      java.util.Set existingDrives = new java.util.HashSet(java.util.Arrays.asList(
          MiniClient.WINDOWS_OS ? java.io.File.listRoots() : volDir.listFiles()));
      while (true)
      {
        java.util.Set currDrives = new java.util.HashSet(java.util.Arrays.asList(
            MiniClient.WINDOWS_OS ? java.io.File.listRoots() : volDir.listFiles()));
        java.util.Set currDrivesClone = new java.util.HashSet(currDrives);
        currDrives.removeAll(existingDrives);

        java.util.Iterator walker = currDrives.iterator();
        while (walker.hasNext())
        {
          java.io.File newDrive = (java.io.File) walker.next();
          System.out.println("Detected new drive: " + newDrive);
          MiniClientConnection foo = MiniClientConnection.currConnection;
          if (foo != null)
            foo.postHotplugEvent(true, newDrive.toString(), "");
        }
        existingDrives.removeAll(currDrivesClone);
        if (!existingDrives.isEmpty())
        {
          System.out.println("Drives have been removed...");
          walker = existingDrives.iterator();
          while (walker.hasNext())
          {
            java.io.File oldDrive = (java.io.File) walker.next();
            System.out.println("Lost old drive: " + oldDrive);
            MiniClientConnection foo = MiniClientConnection.currConnection;
            if (foo != null)
              foo.postHotplugEvent(false, oldDrive.toString(), "");
          }
        }

        existingDrives = currDrivesClone;
        try
        {
          Thread.sleep(10000);
        }
        catch (Exception e)
        {}
      }
    }
    else if (MiniClient.LINUX_OS)
    {
    }
  }
}
