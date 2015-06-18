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
package sage.version;

/**
 *
 * @author jkardatzke
 */
public interface VersionControl
{
  // Returns an object that describes the various state values for a file
  public VersionControlState getState(java.io.File f);

  // Returns a string that indicates the version of the working file in our filesystem
  public String getWorkingVersion(java.io.File f);

  // Returns a string that indicates the current version in the repository of the specified local file path
  public String getRepositoryVersion(java.io.File f);

  // Returns true if the specified file path differs from it's corresponding version in the repository
  public boolean isFileModified(java.io.File f);

  // Returns true if the specified file path's base version matches the latest version in the repository (it may still be modified though)
  public boolean isFileCurrent(java.io.File f);

  // Gets the specified version of a file from the repository and puts it into another local file path (for diff/merge/revert)
  // Returns null on success, error message on failure
  public String getFileVersionContents(java.io.File workingFile, String version, java.io.File repoFile) throws java.io.IOException;

  // Checks in the specified file using comments, returns the output from the repository for the operation
  public String checkinFile(java.io.File f, String comments);

  // Updates the local version of the file to the latest version in the repository (if it is unchanged), returns the output from the repository for the operation
  public String updateFile(java.io.File f);

  // Updates the local version of the file to the specified version in the repository (if it is unchanged), returns the output from the repository for the operation
  public String updateFile(java.io.File f, String version);
}
