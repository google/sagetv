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
package sage.vfs;

/**
 *
 * @author Narflex
 */
public interface DataObjectFilter
{
  // version used for programatic calling
  public String getTechnique();
  // Pretty version which is presented to the user
  public String getName();
  public boolean isMatchingFilter();
  // passesFilter should take into account the matching setting
  public boolean passesFilter(String dataType, Object data);
  // We want to override these so that we can avoid refreshing when there's redundant filter changes
  public boolean equals(Object o);
  public int hashCode();
}
