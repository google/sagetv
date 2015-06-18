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

public class subp_attr_t extends Struct
{
  public Unsigned8 code_mode = new Unsigned8(3);
  public Unsigned8 zero1 = new Unsigned8(3);
  public Unsigned8 type = new Unsigned8(2);
  public Unsigned8 zero2 = new Unsigned8();
  public Unsigned16 lang_code = new Unsigned16();
  public Unsigned8 lang_extension = new Unsigned8();
  public Unsigned8 code_extension = new Unsigned8();
}
