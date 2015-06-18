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

public class ptl_mait_country_t extends Struct
{
  public Unsigned16 country_code = new Unsigned16();
  public Unsigned16 zero_1 = new Unsigned16();
  public Unsigned16 pf_ptl_mai_start_byte = new Unsigned16();
  public Unsigned16 zero_2 = new Unsigned16();
  public pf_level_t[] pf_ptl_mai; // Don't forget to add 1 for main

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("country_code: ");
    sb.append(country_code.get());
    sb.append('\n');
    if(pf_ptl_mai!=null)
    {
      for(int i=0;i<pf_ptl_mai.length;i++)
      {
        sb.append("vts ");
        sb.append(i);
        sb.append(" : ");
        sb.append(pf_ptl_mai[i]);
        sb.append('\n');
      }
    }
    return sb.toString();
  }
}
//#define PTL_MAIT_COUNTRY_SIZE 8
