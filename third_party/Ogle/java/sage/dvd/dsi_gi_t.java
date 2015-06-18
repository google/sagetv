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

public class dsi_gi_t extends Struct
{
  public Unsigned32 nv_pck_scr = new Unsigned32();
  public Unsigned32 nv_pck_lbn = new Unsigned32();
  public Unsigned32 vobu_ea = new Unsigned32();
  public Unsigned32 vobu_1stref_ea = new Unsigned32();
  public Unsigned32 vobu_2ndref_ea = new Unsigned32();
  public Unsigned32 vobu_3rdref_ea = new Unsigned32();
  public Unsigned16 vobu_vob_idn = new Unsigned16();
  public Unsigned8 zero1 = new Unsigned8();
  public Unsigned8 vobu_c_idn = new Unsigned8();
  public dvd_time_t c_eltm = (dvd_time_t) inner(new dvd_time_t());
}
