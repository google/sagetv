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
package sage.plugin;

/**
 * This class is used to finish up any staging processes for plugin installation. It only has 2 jobs to do.
 * 1. Read each line of the stageddeletes.txt file and delete the filename on each line, delete the stageddeletes.txt file when done
 * 2. Read each line of the stagedrenames.txt file and rename the filename on each odd line to be the filename on the subsequent even line, delete the stagedrenames.txt file when done
 * @author Narflex
 */
public class StageCleaner
{
  /** Creates a new instance of StageCleaner */
  private StageCleaner()
  {
  }

  public static void main(String[] args)
  {
    boolean fail = false;
    // Process the deletes first
    java.io.File deleteFile = new java.io.File("stageddeletes.txt");
    if (deleteFile.isFile())
    {
      java.io.BufferedReader reader = null;
      try
      {
        reader = new java.io.BufferedReader(new java.io.FileReader(deleteFile));
        String name = reader.readLine();
        while (name != null && !fail)
        {
          java.io.File delFile = new java.io.File(name);
          if (delFile.isFile() && !delFile.delete())
          {
            fail = true;
          }
          name = reader.readLine();
        }
      }
      catch (Exception e)
      {
        // Just suppress it since we can't log anything from here
      }
      finally
      {
        if (reader != null)
        {
          try{reader.close();}catch(Exception e1){}
        }
      }
      if (!fail)
        deleteFile.delete();
    }

    // Process the renames next
    java.io.File renameFile = new java.io.File("stagedrenames.txt");
    if (!fail && renameFile.isFile())
    {
      java.io.BufferedReader reader = null;
      try
      {
        reader = new java.io.BufferedReader(new java.io.FileReader(renameFile));
        String name1 = reader.readLine();
        if (name1 != null)
        {
          String name2 = reader.readLine();
          while (name1 != null && name2 != null && !fail)
          {
            // This is protection in case we partially processed this before.
            java.io.File srcFile = new java.io.File(name1);
            if (srcFile.isFile())
            {
              java.io.File destFile = new java.io.File(name2);
              if (destFile.isFile() && !destFile.delete())
              {
                fail = true;
              }
              if (!fail)
              {
                if (!srcFile.renameTo(destFile))
                {
                  fail = true;
                }
              }
            }
            name1 = reader.readLine();
            if (name1 != null)
              name2 = reader.readLine();
          }
        }
      }
      catch (Exception e)
      {
        // Just suppress it since we can't log anything from here
      }
      finally
      {
        if (reader != null)
        {
          try{reader.close();}catch(Exception e){}
        }
      }
      if (!fail)
        renameFile.delete();
    }
  }
}
