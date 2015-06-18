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

public class ptl_mait_t extends Struct
{
  public Unsigned16 nr_of_countries = new Unsigned16();
  public Unsigned16 nr_of_vtss = new Unsigned16();
  public Unsigned32 last_byte = new Unsigned32();
  public ptl_mait_country_t[] countries;

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("nr_of_countries: ");
    sb.append(nr_of_countries.get());
    sb.append('\n');
    sb.append("nr_of_vtss: ");
    sb.append(nr_of_vtss.get());
    sb.append('\n');
    if(countries!=null)
    {
      for(int i=0;i<nr_of_countries.get();i++)
      {
        sb.append("country ");
        sb.append(i);
        sb.append(" : ");
        sb.append(countries[i]);
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  public boolean create()
  {
    if(nr_of_countries.get()!=0)
    {
      countries = new ptl_mait_country_t[nr_of_countries.get()];
      for(int i=0;i<nr_of_countries.get();i++)
      {
        countries[i] = new ptl_mait_country_t();
        countries[i].setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+8+i*countries[i].size());

        countries[i].pf_ptl_mai = new pf_level_t[nr_of_vtss.get()+1];
        for(int j=0;j<nr_of_vtss.get()+1;j++)
        {
          countries[i].pf_ptl_mai[j] = new pf_level_t();
          countries[i].pf_ptl_mai[j].setByteBuffer(getByteBuffer(),
              getByteBufferPosition()+countries[i].pf_ptl_mai_start_byte.get()+
              j*countries[i].pf_ptl_mai[j].size());
        }
      }
    }
    return true;
  }

}
//#define PTL_MAIT_SIZE 8
