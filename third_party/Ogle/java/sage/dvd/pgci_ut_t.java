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

public class pgci_ut_t extends Struct
{
  public Unsigned16 nr_of_lus = new Unsigned16();
  public Unsigned16 zero_1 = new Unsigned16();
  public Unsigned32 last_byte = new Unsigned32();
  public pgci_lu_t[] lu;

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("nr_of_lu: ");
    sb.append(nr_of_lus.get());
    sb.append('\n');
    if(lu!=null)
    {
      for(int i=0;i<nr_of_lus.get();i++)
      {
        sb.append("lu ");
        sb.append(i);
        sb.append(" : ");
        sb.append(lu[i]);
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  public boolean create()
  {
    if(nr_of_lus.get()!=0)
    {
      lu = new pgci_lu_t[nr_of_lus.get()];
      for(int i=0;i<nr_of_lus.get();i++)
      {
        lu[i] = new pgci_lu_t();
        lu[i].setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+8+i*lu[i].size());
        lu[i].pgcit = new pgcit_t();
        lu[i].pgcit.setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+(int)lu[i].lang_start_byte.get());
        lu[i].pgcit.create();
      }
    }
    return true;
  }
}
//#define PGCI_UT_SIZE 8
