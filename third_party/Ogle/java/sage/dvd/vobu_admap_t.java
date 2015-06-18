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

public class vobu_admap_t extends Struct
{
  public Unsigned32 last_byte = new Unsigned32();
  public Unsigned32[] vobu_start_sectors;

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("last_byte: ");
    sb.append(last_byte.get());
    sb.append('\n');
    /*if(vobu_start_sectors!=null)
        {
            for(int i=0;i<((int)last_byte.get()+1-4)/4;i++)
            {
                sb.append("vobu ");
                sb.append(i);
                sb.append(":");
                sb.append(vobu_start_sectors[i].get());
                sb.append(" ");
            }
        }*/
    return sb.toString();
  }

  public boolean create()
  {
    if(last_byte.get()!=0)
    {
      vobu_start_sectors = (Unsigned32[]) array(new Unsigned32[((int)last_byte.get()+1-4)/4]);
    }
    return true;
  }
}
//#define VOBU_ADMAP_SIZE 4
