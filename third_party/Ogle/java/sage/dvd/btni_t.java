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

public class btni_t extends Struct
{
  public Unsigned16 btn_coln = new Unsigned16(2);
  public Unsigned16 x_start = new Unsigned16(10);
  public Unsigned16 zero1 = new Unsigned16(2);
  public Unsigned16 x_end = new Unsigned16(10);

  public Unsigned16 auto_action_mode = new Unsigned16(2);
  public Unsigned16 y_start = new Unsigned16(10);
  public Unsigned16 zero2 = new Unsigned16(2);
  public Unsigned16 y_end = new Unsigned16(10);

  public Unsigned16 zero3 = new Unsigned16(2);
  public Unsigned16 up = new Unsigned16(6);
  public Unsigned16 zero4 = new Unsigned16(2);
  public Unsigned16 down = new Unsigned16(6);
  public Unsigned16 zero5 = new Unsigned16(2);
  public Unsigned16 left = new Unsigned16(6);
  public Unsigned16 zero6 = new Unsigned16(2);
  public Unsigned16 right = new Unsigned16(6);
  public vm_cmd_t cmd = (vm_cmd_t) inner(new vm_cmd_t());
}
