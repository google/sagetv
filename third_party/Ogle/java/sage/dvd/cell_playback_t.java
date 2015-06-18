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

public class cell_playback_t extends Struct
{
  final static int BLOCK_TYPE_NONE = 0x0;
  final static int BLOCK_TYPE_ANGLE_BLOCK = 0x1;

  final static int BLOCK_MODE_NOT_IN_BLOCK = 0x0;
  final static int BLOCK_MODE_FIRST_CELL = 0x1;
  final static int BLOCK_MODE_IN_BLOCK = 0x2;
  final static int BLOCK_MODE_LAST_CELL = 0x3;

  public Unsigned8 block_mode = new Unsigned8(2);
  public Unsigned8 block_type = new Unsigned8(2);
  public Unsigned8 seamless_play = new Unsigned8(1);
  public Unsigned8 interleaved = new Unsigned8(1);
  public Unsigned8 stc_discontinuity = new Unsigned8(1);
  public Unsigned8 seamless_angle = new Unsigned8(1);

  public Unsigned8 playback_mode = new Unsigned8(1);
  public Unsigned8 restricted = new Unsigned8(1);
  public Unsigned8 unknown2 = new Unsigned8(6);

  public Unsigned8 still_time = new Unsigned8();
  public Unsigned8 cell_cmd_nr = new Unsigned8();

  public dvd_time_t playback_time = (dvd_time_t) inner(new dvd_time_t()); // Verify if that works

  public Unsigned32 first_sector = new Unsigned32();
  public Unsigned32 first_ilvu_end_sector = new Unsigned32();
  public Unsigned32 last_vobu_start_sector = new Unsigned32();
  public Unsigned32 last_sector = new Unsigned32();
}
