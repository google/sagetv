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
public abstract class BasicMediaNode implements MediaNode
{
  protected static java.text.DecimalFormat floatFormat = new java.text.DecimalFormat("0.#");

  /** Creates a new instance of BasicMediaNode */
  public BasicMediaNode(BasicMediaSource inSource, BasicMediaNode inParent)
  {
    mySource = inSource;
    parent = inParent;
  }

  protected BasicMediaNode parent;
  protected BasicMediaSource mySource;
  protected boolean checked;

  public boolean isChecked() { return checked; }
  public void setChecked(boolean x) { checked = x; }

  public int hashCode()
  {
    return (getDataObject() != null) ? getDataObject().hashCode() : super.hashCode();
  }

  public boolean equals(Object o)
  {
    return (o instanceof MediaNode) && (((MediaNode)o).getDataObject() != null) && ((MediaNode)o).getDataObject().equals(getDataObject());
  }

  public int getNumChildren()
  {
    MediaNode[] kids = getChildren();
    return (kids == null) ? 0 : kids.length;
  }

  public MediaNode getChildAt(int x)
  {
    MediaNode[] kids = getChildren();
    return (kids != null && x >= 0 && x < kids.length) ? kids[x] : null;
  }

  public void refresh()
  {
    if (parent != null)
      parent.refresh();
  }

  public MediaNode getParent()
  {
    return parent;
  }

  public MediaSource getMediaSource()
  {
    return mySource;
  }

  public void setSorter(MediaNodeSorter sorter)
  {
    if (mySource.setSorting(sorter))
      refresh();
  }

  public MediaNodeSorter getSorter()
  {
    return mySource.getSorter();
  }

  public DataObjectFilter[] getFilters()
  {
    return mySource.getFilters();
  }

  public void setFiltering(DataObjectFilter filter)
  {
    if (mySource.setFiltering(filter))
      refresh();
  }

  public void appendFiltering(DataObjectFilter filter)
  {
    if (mySource.appendFiltering(filter))
      refresh();
  }

  public String getProperty(String name)
  {
    return "";
  }
}
