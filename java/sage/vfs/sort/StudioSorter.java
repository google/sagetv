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
public class StudioSorter extends BasicMediaNodeSorter
{
  /** Creates a new instance of StudioSorter */
  public StudioSorter(boolean ascending, boolean ignoreThe)
  {
    super(ascending);
    this.ignoreThe = ignoreThe;
  }

  public int compare(Object o1, Object o2)
  {
    MediaNode m1 = (MediaNode) o1;
    MediaNode m2 = (MediaNode) o2;
    boolean f1 = (m1 == null) ? false : m1.isFolder();
    boolean f2 = (m2 == null) ? false : m2.isFolder();
    if (f1 != f2)
      return f1 ? -1 : 1;
    sage.Show a1 = sage.PredefinedJEPFunction.getShowObj(m1.getDataObject());
    sage.Show a2 = sage.PredefinedJEPFunction.getShowObj(m2.getDataObject());
    String s1 = (a1 == null) ? (m1.getDataType().equals(MediaNode.DATATYPE_STUDIO) ? m1.getPrimaryLabel() : "") : a1.getStudio();
    if (ignoreThe && s1.regionMatches(true, 0, "the ", 0, 4))
      s1 = s1.substring(4);
    String s2 = (a2 == null) ? (m2.getDataType().equals(MediaNode.DATATYPE_STUDIO) ? m2.getPrimaryLabel() : "") : a2.getStudio();
    if (ignoreThe && s2.regionMatches(true, 0, "the ", 0, 4))
      s2 = s2.substring(4);
    return (ascending ? 1 : -1) * s1.compareToIgnoreCase(s2);
  }

  public String getTechnique()
  {
    return ignoreThe ? MediaNode.SORT_BY_STUDIO_IGNORE_THE : MediaNode.SORT_BY_STUDIO;
  }

  public String getName()
  {
    return sage.Sage.rez("Studio");
  }

  private boolean ignoreThe;
}
