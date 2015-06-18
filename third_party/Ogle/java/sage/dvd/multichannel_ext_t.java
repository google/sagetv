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

public class multichannel_ext_t extends Struct
{
  public Unsigned8 zero1 = new Unsigned8(7);
  public Unsigned8 ach0_gme = new Unsigned8(1);

  public Unsigned8 zero2 = new Unsigned8(7);
  public Unsigned8 ach1_gme = new Unsigned8(1);

  public Unsigned8 zero3 = new Unsigned8(4);
  public Unsigned8 ach2_gv1e = new Unsigned8(1);
  public Unsigned8 ach2_gv2e = new Unsigned8(1);
  public Unsigned8 ach2_gm1e = new Unsigned8(1);
  public Unsigned8 ach2_gm2e = new Unsigned8(1);

  public Unsigned8 zero4 = new Unsigned8(4);
  public Unsigned8 ach3_gv1e = new Unsigned8(1);
  public Unsigned8 ach3_gv2e = new Unsigned8(1);
  public Unsigned8 ach3_gmAe = new Unsigned8(1);
  public Unsigned8 ach3_se2e = new Unsigned8(1);

  public Unsigned8 zero5 = new Unsigned8(4);
  public Unsigned8 ach4_gv1e = new Unsigned8(1);
  public Unsigned8 ach4_gv2e = new Unsigned8(1);
  public Unsigned8 ach4_gmBe = new Unsigned8(1);
  public Unsigned8 ach4_seBe = new Unsigned8(1);
  public Unsigned8[] zero6 = (Unsigned8[]) array(new Unsigned8[19]);
}
