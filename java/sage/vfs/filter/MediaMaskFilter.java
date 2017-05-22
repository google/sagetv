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
package sage.vfs.filter;
import sage.vfs.*;

/**
 *
 * @author Narflex
 */
public class MediaMaskFilter extends BasicDataObjectFilter
{

  /**
   * Creates a new instance of MediaMaskFilter
   */
  public MediaMaskFilter(int mediaMask, String technique, String name, boolean matchPasses)
  {
    super(matchPasses);
    this.mediaMask = mediaMask;
    this.technique = technique;
    this.name = name;
  }

  // version used for programatic calling
  public String getTechnique()
  {
    return technique;
  }

  // Pretty version which is presented to the user
  public String getName()
  {
    return name;
  }

  // passesFilter should take into account the matching setting
  public boolean passesFilter(String dataType, Object data)
  {
    if (MediaNode.DATATYPE_FILE.equals(dataType))
    {
      java.io.File f = (java.io.File) data;
      return f.isDirectory() || (((sage.SeekerSelector.getInstance().guessImportedMediaMaskFast(f.getAbsolutePath()) & mediaMask) == 0) == !matchPasses);
    }
    else if (MediaNode.DATATYPE_MEDIAFILE.equals(dataType))
    {
      sage.MediaFile mf = (sage.MediaFile) data;
      return matchPasses == mf.hasMediaMaskAny(mediaMask);
    }
    return false;
  }

  private int mediaMask;
  private String technique;
  private String name;
}
