/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Adapted from Ogle - A video player
 * Copyright (C) 2000, 2001 Håkan Hjort
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111, USA.
 */
package sage.dvd;

public class pgc_command_tbl_t extends Struct
{
  public Unsigned16 nr_of_pre = new Unsigned16();
  public Unsigned16 nr_of_post = new Unsigned16();
  public Unsigned16 nr_of_cell = new Unsigned16();
  public Unsigned16 zero_1 = new Unsigned16();

  public vm_cmd_t[] pre_cmd;
  public vm_cmd_t[] post_cmd;
  public vm_cmd_t[] cell_cmd;

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("nr_of_pre: ");
    sb.append(nr_of_pre.get());
    sb.append('\n');
    if(pre_cmd!=null)
    {
      for(int i=0;i<nr_of_pre.get();i++)
      {
        sb.append("pre  ");
        sb.append(i);
        sb.append(" : ");
        sb.append(pre_cmd[i]);
        sb.append('\n');
      }
    }

    sb.append("nr_of_post: ");
    sb.append(nr_of_post.get());
    sb.append('\n');
    if(post_cmd!=null)
    {
      for(int i=0;i<nr_of_post.get();i++)
      {
        sb.append("post ");
        sb.append(i);
        sb.append(" : ");
        sb.append(post_cmd[i]);
        sb.append('\n');
      }
    }

    sb.append("nr_of_cell: ");
    sb.append(nr_of_cell.get());
    sb.append('\n');
    if(cell_cmd!=null)
    {
      for(int i=0;i<nr_of_cell.get();i++)
      {
        sb.append("cell ");
        sb.append(i);
        sb.append(" : ");
        sb.append(cell_cmd[i]);
        sb.append('\n');
      }
    }

    return sb.toString();
  }

  public boolean create()
  {
    if(nr_of_pre.get()!=0)
    {
      pre_cmd = new vm_cmd_t[nr_of_pre.get()];
      for(int i=0;i<nr_of_pre.get();i++)
      {
        pre_cmd[i] = new vm_cmd_t();
        pre_cmd[i].setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+8+i*8);
      }
    }
    if(nr_of_post.get()!=0)
    {
      post_cmd = new vm_cmd_t[nr_of_post.get()];
      for(int i=0;i<nr_of_post.get();i++)
      {
        post_cmd[i] = new vm_cmd_t();
        post_cmd[i].setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+8+i*8+nr_of_pre.get()*8);
      }
    }
    if(nr_of_cell.get()!=0)
    {
      cell_cmd = new vm_cmd_t[nr_of_cell.get()];
      for(int i=0;i<nr_of_cell.get();i++)
      {
        cell_cmd[i] = new vm_cmd_t();
        cell_cmd[i].setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+8+i*8+nr_of_pre.get()*8+nr_of_post.get()*8);
      }
    }
    return true;
  }
}
