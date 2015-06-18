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

public class vts_tmap_t extends Struct{
  public Unsigned8 tmu = new Unsigned8();
  public Unsigned8 zero_1 = new Unsigned8();
  public Unsigned16 nr_of_entries = new Unsigned16();
  public Unsigned32[] map_ent;

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("time unit (seconds): ");
    sb.append(tmu.get());
    sb.append('\n');
    sb.append("nr_of_entries: ");
    sb.append(nr_of_entries.get());
    sb.append('\n');
    if(map_ent!=null)
    {
      for(int i=0;i<nr_of_entries.get();i++)
      {
        sb.append("map_ent ");
        sb.append(i);
        sb.append(" : ");
        sb.append(map_ent[i].get());
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  public boolean create()
  {
    if(nr_of_entries.get()!=0)
    {
      map_ent = (Unsigned32[]) array(new Unsigned32[nr_of_entries.get()]);
    }
    return true;
  }
}
//#define VTS_TMAP_SIZE 4
