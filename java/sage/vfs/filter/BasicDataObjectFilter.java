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
public abstract class BasicDataObjectFilter implements DataObjectFilter
{
  public BasicDataObjectFilter(boolean matchPasses)
  {
    this.matchPasses = matchPasses;
  }
  public boolean equals(Object o)
  {
    return (o.getClass() == getClass()) && (((BasicDataObjectFilter) o).matchPasses == matchPasses);
  }
  public int hashCode()
  {
    return getTechnique().hashCode() + (matchPasses ? 1 : 0);
  }

  public boolean isMatchingFilter()
  {
    return matchPasses;
  }

  protected boolean matchPasses;
}
