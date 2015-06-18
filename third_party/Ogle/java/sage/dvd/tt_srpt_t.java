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

public class tt_srpt_t extends Struct
{
  public Unsigned16 nr_of_srpts = new Unsigned16();
  public Unsigned16 zero_1 = new Unsigned16();
  public Unsigned32 last_byte = new Unsigned32();
  public title_info_t[] title;

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("tt_srpt_t:\n");
    sb.append(super.toString());
    sb.append('\n');
    sb.append("number of titles: ");
    sb.append(nr_of_srpts.get());
    sb.append('\n');
    sb.append("last byte: ");
    sb.append(last_byte.get());
    sb.append('\n');


    for(int i=0;i<nr_of_srpts.get();i++)
    {
      sb.append("Title ");
      sb.append(i);
      sb.append('\n');
      sb.append("nr_of_angles: ");
      sb.append(title[i].nr_of_angles.get());
      sb.append('\n');
      sb.append("nr_of_ptts: ");
      sb.append(title[i].nr_of_ptts.get());
      sb.append('\n');
      sb.append("parental_id: ");
      sb.append(title[i].parental_id.get());
      sb.append('\n');
      sb.append("title_set_nr: ");
      sb.append(title[i].title_set_nr.get());
      sb.append('\n');
      sb.append("vts_ttn: ");
      sb.append(title[i].vts_ttn.get());
      sb.append('\n');
      sb.append("title_set_sector: ");
      sb.append(title[i].title_set_sector.get());
      sb.append('\n');
      sb.append('\n');
    }
    return sb.toString();
  }
}
//#define TT_SRPT_SIZE 8
