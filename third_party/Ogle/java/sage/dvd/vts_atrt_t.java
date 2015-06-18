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

public class vts_atrt_t extends Struct
{
  public Unsigned16 nr_of_vtss = new Unsigned16();
  public Unsigned16 zero_1 = new Unsigned16();
  public Unsigned32 last_byte = new Unsigned32();
  public Unsigned32[] vts_atrt_offsets;
  public vts_attributes_t[] vts;

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("vts attributes copy");
    sb.append("nr_of_vtss: ");
    sb.append(nr_of_vtss.get());
    sb.append('\n');
    if(vts!=null)
    {
      for(int i=0;i<nr_of_vtss.get();i++)
      {
        sb.append("vts ");
        sb.append(i);
        sb.append(" : ");
        sb.append(vts[i]);
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  public boolean create()
  {
    if(nr_of_vtss.get()!=0)
    {
      vts_atrt_offsets = (Unsigned32[]) array(new Unsigned32[nr_of_vtss.get()]);
      vts = new vts_attributes_t[nr_of_vtss.get()];

      for(int i=0;i<nr_of_vtss.get();i++)
      {
        vts[i] = new vts_attributes_t();
        vts[i].setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+(int)vts_atrt_offsets[i].get());
      }
    }
    return true;
  }
}
//#define VTS_ATRT_SIZE 8
