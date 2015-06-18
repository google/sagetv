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

public class video_attr_t extends Struct
{
  public Unsigned8 mpeg_version = new Unsigned8(2);
  public Unsigned8 video_format = new Unsigned8(2);
  public Unsigned8 display_aspect_ratio = new Unsigned8(2);
  public Unsigned8 permitted_df = new Unsigned8(2);

  public Unsigned8 line21_cc_1 = new Unsigned8(1);
  public Unsigned8 line21_cc_2 = new Unsigned8(1);
  public Unsigned8 bit_rate = new Unsigned8(1);

  public Unsigned8 picture_size = new Unsigned8(2);
  public Unsigned8 letterboxed = new Unsigned8(1);
  public Unsigned8 unknown1 = new Unsigned8(1);
  public Unsigned8 film_mode = new Unsigned8(1);
}
