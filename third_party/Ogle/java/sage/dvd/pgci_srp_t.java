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

public class pgci_srp_t extends Struct
{
  public Unsigned8 entry_id = new Unsigned8();
  public Unsigned8 block_mode = new Unsigned8(2);
  public Unsigned8 block_type = new Unsigned8(2);
  public Unsigned8 unknown1 = new Unsigned8(4);
  public Unsigned16 ptl_id_mask = new Unsigned16();
  public Unsigned32 pgc_start_byte = new Unsigned32();
  public pgc_t pgc;

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("entry id: ");
    sb.append(entry_id.get());
    sb.append('\n');
    sb.append("block mode: ");
    sb.append(block_mode.get());
    sb.append('\n');
    sb.append("block type: ");
    sb.append(block_type.get());
    sb.append('\n');
    sb.append("parental mask: ");
    sb.append(ptl_id_mask.get());
    sb.append('\n');
    if(pgc!=null)
    {
      sb.append("pgc:\n");
      sb.append(pgc);
      sb.append('\n');
    }
    return sb.toString();
  }
}
//#define PGCI_SRP_SIZE 8
