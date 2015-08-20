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

public class ttu_t extends Struct
{
  public ptt_info_t[] ptt;
  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    if(ptt!=null)
    {
      for(int i=0;i<ptt.length;i++)
      {
        sb.append("pgcn: ");
        sb.append(ptt[i].pgcn.get());
        sb.append(" pgn: ");
        sb.append(ptt[i].pgn.get());
        sb.append('\n');
      }
    }
    return sb.toString();
  }
}
