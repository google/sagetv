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
public class IntelligentSorter extends BasicMediaNodeSorter
{

  /** Creates a new instance of IntelligentSorter */
  public IntelligentSorter(boolean ascending)
  {
    super(ascending);
    mycomp = sage.Seeker.getInstance().getMediaFileComparator(true);
  }

  public int compare(Object o1, Object o2)
  {
    MediaNode m1 = (MediaNode) o1;
    MediaNode m2 = (MediaNode) o2;
    boolean f1 = (m1 == null) ? false : m1.isFolder();
    boolean f2 = (m2 == null) ? false : m2.isFolder();
    if (f1 != f2)
      return f1 ? -1 : 1;
    // Resolve a group into its first child for sorting purposes here
    if (f1 && m1 instanceof GrouperMediaFileNode)
      o1 = ((GrouperMediaFileNode) m1).getFirstDescendant();
    if (f2 && m2 instanceof GrouperMediaFileNode)
      o2 = ((GrouperMediaFileNode) m2).getFirstDescendant();
    o1 = m1.getDataObject();
    o2 = m2.getDataObject();
    if (!(o1 instanceof sage.MediaFile) && !(o1 instanceof sage.Airing))
      o1 = sage.PredefinedJEPFunction.getMediaFileObj(o1);
    if (!(o2 instanceof sage.MediaFile) && !(o2 instanceof sage.Airing))
      o2 = sage.PredefinedJEPFunction.getMediaFileObj(o2);
    return (ascending ? 1 : -1) * mycomp.compare(o1, o2);
  }

  public String getTechnique()
  {
    return MediaNode.SORT_BY_INTELLIGENT;
  }

  public String getName()
  {
    return sage.Sage.rez("Intelligent");
  }

  private java.util.Comparator mycomp;
}
