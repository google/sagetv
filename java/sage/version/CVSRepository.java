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
public class CVSRepository implements VersionControl
{
  public CVSRepository()
  {
    workingVerPat = java.util.regex.Pattern.compile(sage.Sage.get("versioning/cvs/working_rev_pat", "\\s*Working revision\\:\\s*(\\S+)\\s"));
    repoVerPat = java.util.regex.Pattern.compile(sage.Sage.get("versioning/cvs/repository_rev_pat", "\\s*Repository revision\\:\\s*(\\S+)\\s"));
    statusPat = java.util.regex.Pattern.compile(sage.Sage.get("versioning/cvs/status_pat", "\\s*Status\\:\\s*(.*)\\s"));
    relRepoPathPat = java.util.regex.Pattern.compile(sage.Sage.get("versioning/cvs/rel_repo_path_pat",
        "\\s*Repository revision\\:\\s*\\S+\\s*\\/\\S+?\\/(.*)\\/"));
  }

  public VersionControlState getState(java.io.File f)
  {
    System.out.println("CVS: getting status...:");
    String repoStatus = sage.IOUtils.exec(new String[] { "cvs", "-z", "9", "status", f.getName() }, true, true, false, f.getParentFile());
    VersionControlState rv = new VersionControlState();

    try
    {
      java.util.regex.Matcher mat = workingVerPat.matcher(repoStatus);
      if (!mat.find())
        return null;
      rv.workingVersion = mat.group(1).trim();

      mat = repoVerPat.matcher(repoStatus);
      if (!mat.find())
        return null;
      rv.repositoryVersion = mat.group(1).trim();

      mat = statusPat.matcher(repoStatus);
      if (!mat.find())
        return null;
      String str = mat.group(1).trim();
      rv.isModified = sage.Sage.get("versioning/cvs/needs_merge", "Needs Merge").equalsIgnoreCase(str) ||
          sage.Sage.get("versioning/cvs/locally_modified", "Locally Modified").equalsIgnoreCase(str);
      rv.isCurrent = sage.Sage.get("versioning/cvs/up_to_date", "Up-to-date").equalsIgnoreCase(str) ||
          sage.Sage.get("versioning/cvs/locally_modified", "Locally Modified").equalsIgnoreCase(str);
    }
    catch (Exception e)
    {
      // This can happen if the file isn't actually in version control
      System.out.println("ERROR with version control parsing of:" + e);
      e.printStackTrace();
      return null;
    }
    finally {
      System.out.println("CVS: got status.");
    }

    return rv;
  }

  private String getStatusResult(java.io.File f, java.util.regex.Pattern pat, int group)
  {
    System.out.println("CVS: getting status for: "+pat.pattern());
    String repoStatus = sage.IOUtils.exec(new String[] { "cvs", "-z", "9", "status", f.getName() }, true, true, false, f.getParentFile());
    java.util.regex.Matcher mat = pat.matcher(repoStatus);
    mat.find();
    System.out.println("CVS: got status.");
    return mat.group(group).trim();
  }

  public String getWorkingVersion(java.io.File f)
  {
    return getStatusResult(f, workingVerPat, 1);
  }

  public String getRepositoryVersion(java.io.File f)
  {
    return getStatusResult(f, repoVerPat, 1);
  }

  public boolean isFileModified(java.io.File f)
  {
    String currStatus = getStatusResult(f, statusPat, 1);
    return sage.Sage.get("versioning/cvs/needs_merge", "Needs Merge").equalsIgnoreCase(currStatus) ||
        sage.Sage.get("versioning/cvs/locally_modified", "Locally Modified").equalsIgnoreCase(currStatus);
  }

  public boolean isFileCurrent(java.io.File f)
  {
    String currStatus = getStatusResult(f, statusPat, 1);
    return sage.Sage.get("versioning/cvs/up_to_date", "Up-to-date").equalsIgnoreCase(currStatus) ||
        sage.Sage.get("versioning/cvs/locally_modified", "Locally Modified").equalsIgnoreCase(currStatus);
  }

  public String getFileVersionContents(java.io.File workingFile, String version, java.io.File repoFile) throws java.io.IOException
  {
    String modulePath = getStatusResult(workingFile, relRepoPathPat, 1);
    System.out.println("Module Path for " + workingFile + " is \"" + modulePath + "\")");
    System.out.println("CVS: getting version: "+version);
    java.io.ByteArrayOutputStream repoFileData = sage.IOUtils.execByteOutput(new String[] { "cvs", "-z", "9", "-q", "co", "-r" + version, "-p",
        modulePath + "/" + workingFile.getName() }, false, true, false, workingFile.getParentFile());
    java.io.FileOutputStream fw = new java.io.FileOutputStream(repoFile);
    repoFileData.writeTo(fw);
    fw.close();
    System.out.println("CVS: got version: "+version);

    return null;
  }

  public String checkinFile(java.io.File f, String comments)
  {
    // First save out the message text to a file if it was used
    java.io.File msgFile = null;
    if (comments.length() > 0)
    {
      try
      {
        msgFile = java.io.File.createTempFile("stvcmt", ".txt");
        java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(msgFile));
        pw.print(comments);
        pw.close();
      }
      catch (java.io.IOException ioe)
      {
        return "There was an I/O error, the file was not checked in. Error details: " + ioe;
      }
    }
    System.out.println("CVS: committing..:");
    try {
      if (msgFile != null)
        return sage.IOUtils.exec(new String[] { "cvs", "-z", "9", "commit", "-F", msgFile.getAbsolutePath(), f.getName() },
            true, true, false, f.getParentFile());
      else
        return sage.IOUtils.exec(new String[] { "cvs", "-z", "9", "commit", "-m", "No comment", f.getName() },
            true, true, false, f.getParentFile());
    } finally {
      System.out.println("CVS: committed");
    }
  }

  public String updateFile(java.io.File f)
  {
    // Just run the update command and return the output from it
    System.out.println("CVS: updating..:");
    try {
      return sage.IOUtils.exec(new String[] { "cvs", "-z", "9", "update", f.getName() }, true, true, false, f.getParentFile());
    } finally {
      System.out.println("CVS: updated.");
    }
  }

  public String updateFile(java.io.File f, String version)
  {
    // Just run the update command and return the output from it
    try {
      System.out.println("CVS: updating to version :"+version);
      return sage.IOUtils.exec(new String[] { "cvs", "-z", "9", "update", "-r", version, f.getName() }, true, true, false, f.getParentFile());
    } finally {
      System.out.println("CVS: updated.");
    }
  }

  private java.util.regex.Pattern workingVerPat;
  private java.util.regex.Pattern repoVerPat;
  private java.util.regex.Pattern statusPat; // values are Up-to-date, Locally Modified, Needs Merge and Needs Patch
  private java.util.regex.Pattern relRepoPathPat;
}
