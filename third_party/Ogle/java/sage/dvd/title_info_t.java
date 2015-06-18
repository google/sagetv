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

public class title_info_t extends Struct
{
  public playback_type_t pb_ty = (playback_type_t) inner(new playback_type_t());
  public Unsigned8 nr_of_angles = new Unsigned8();
  public Unsigned16 nr_of_ptts = new Unsigned16();
  public Unsigned16 parental_id = new Unsigned16();
  public Unsigned8 title_set_nr = new Unsigned8();
  public Unsigned8 vts_ttn = new Unsigned8();
  public Unsigned32 title_set_sector = new Unsigned32();
}
