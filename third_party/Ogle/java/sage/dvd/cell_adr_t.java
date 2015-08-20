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

public class cell_adr_t extends Struct
{
  public Unsigned16 vob_id = new Unsigned16();
  public Unsigned8  cell_id = new Unsigned8();
  public Unsigned8  zero_1 = new Unsigned8();
  public Unsigned32 start_sector = new Unsigned32();
  public Unsigned32 last_sector = new Unsigned32();

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("vob_id: ");
    sb.append(vob_id.get());
    sb.append(" cell_id: ");
    sb.append(cell_id.get());
    sb.append(" start sector: ");
    sb.append(start_sector.get());
    sb.append(" last sector: ");
    sb.append(last_sector.get());
    return sb.toString();
  }
}
