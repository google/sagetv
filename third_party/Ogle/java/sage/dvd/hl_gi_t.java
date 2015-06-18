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

public class hl_gi_t extends Struct
{
  public Unsigned16 hli_ss = new Unsigned16();
  public Unsigned32 hli_s_ptm = new Unsigned32();
  public Unsigned32 hli_e_ptm = new Unsigned32();
  public Unsigned32 btn_se_e_ptm = new Unsigned32();
  public Unsigned8 zero1 = new Unsigned8(2);
  public Unsigned8 btngr_ns = new Unsigned8(2);
  public Unsigned8 zero2 = new Unsigned8(1);
  public Unsigned8 btngr1_dsp_ty = new Unsigned8(3);
  public Unsigned8 zero3 = new Unsigned8(1);
  public Unsigned8 btngr2_dsp_ty = new Unsigned8(3);
  public Unsigned8 zero4 = new Unsigned8(1);
  public Unsigned8 btngr3_dsp_ty = new Unsigned8(3);
  public Unsigned8 btn_ofn = new Unsigned8();
  public Unsigned8 btn_ns = new Unsigned8();
  public Unsigned8 nsl_btn_ns = new Unsigned8();
  public Unsigned8 zero5 = new Unsigned8();
  public Unsigned8 fosl_btnn = new Unsigned8();
  public Unsigned8 foac_btnn = new Unsigned8();
}
