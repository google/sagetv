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

public class vts_ptt_srpt_t extends Struct{
  public Unsigned16 nr_of_srpts = new Unsigned16();
  public Unsigned16 zero_1 = new Unsigned16();
  public Unsigned32 last_byte = new Unsigned32();
  public Unsigned32[] ttu_offset;
  public ttu_t[]  title;

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("nr_of_srpts: ");
    sb.append(nr_of_srpts.get());
    sb.append('\n');
    if(title!=null)
    {
      for(int i=0;i<nr_of_srpts.get();i++)
      {
        sb.append("title ");
        sb.append(i);
        sb.append(" : ");
        sb.append(title[i]);
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  public boolean create()
  {
    if(nr_of_srpts.get()!=0)
    {
      ttu_offset = (Unsigned32[]) array(new Unsigned32[nr_of_srpts.get()]);
      title = new ttu_t[nr_of_srpts.get()];
      for(int i=0;i<nr_of_srpts.get();i++)
      {
        title[i] = new ttu_t();
        title[i].setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+(int)ttu_offset[i].get());
        title[i].ptt = (ptt_info_t[]) title[i].array(new ptt_info_t[(
            (i == (nr_of_srpts.get()-1)) ? (int)(last_byte.get()+1-ttu_offset[i].get()) :
              (int)(ttu_offset[i+1].get()-ttu_offset[i].get()) )/4]);
      }
    }
    return true;
  }

}
//#define VTS_PTT_SRPT_SIZE 8
