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
package sage.vfs.sort;
import sage.vfs.*;

/**
 *
 * @author Narflex
 */
public class FilenameSorter extends BasicMediaNodeSorter
{

  /** Creates a new instance of FilenameSorter */
  public FilenameSorter(boolean ascending)
  {
    super(ascending);
  }

  public int compare(Object o1, Object o2)
  {
    MediaNode m1 = (MediaNode) o1;
    MediaNode m2 = (MediaNode) o2;
    boolean f1 = (m1 == null) ? false : m1.isFolder();
    boolean f2 = (m2 == null) ? false : m2.isFolder();
    if (f1 != f2)
      return f1 ? -1 : 1;
    String s1 = "";
    String s2 = "";
    if (MediaNode.DATATYPE_FILE.equals(m1.getDataType()))
      s1 = ((java.io.File) m1.getDataObject()).getName();
    else if (MediaNode.DATATYPE_MEDIAFILE.equals(m1.getDataType()))
      s1 = ((sage.MediaFile) m1.getDataObject()).getFile(0).getName();
    else
      s1 = m1.getPrimaryLabel();
    if (MediaNode.DATATYPE_FILE.equals(m2.getDataType()))
      s2 = ((java.io.File) m2.getDataObject()).getName();
    else if (MediaNode.DATATYPE_MEDIAFILE.equals(m2.getDataType()))
      s2 = ((sage.MediaFile) m2.getDataObject()).getFile(0).getName();
    else
      s2 = m2.getPrimaryLabel();
    return (ascending ? 1 : -1) * s1.compareToIgnoreCase(s2);
  }

  public String getTechnique()
  {
    return MediaNode.SORT_BY_FILENAME;
  }

  public String getName()
  {
    return sage.Sage.rez("Filename");
  }
}
