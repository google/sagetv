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

public class c_adt_t extends Struct
{
  public Unsigned16 nr_of_vobs = new Unsigned16();
  public Unsigned16 zero_1 = new Unsigned16();
  public Unsigned32 last_byte = new Unsigned32();
  public cell_adr_t[] cell_adr_table;

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("nr_of_vobs: ");
    sb.append(nr_of_vobs.get());
    sb.append('\n');
    if(cell_adr_table!=null)
    {
      for(int i=0;i<nr_of_vobs.get();i++)
      {
        sb.append(cell_adr_table[i]);
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  public boolean create()
  {
    if(nr_of_vobs.get()!=0)
    {
      cell_adr_table = (cell_adr_t[]) array((Struct[])new cell_adr_t[nr_of_vobs.get()]);
    }
    return true;
  }
}
//#define C_ADT_SIZE 8
