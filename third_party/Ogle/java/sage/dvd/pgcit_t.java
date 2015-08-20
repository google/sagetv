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

public class pgcit_t extends Struct
{
  public Unsigned16 nr_of_pgci_srp = new Unsigned16();
  public Unsigned16 zero_1 = new Unsigned16();
  public Unsigned32 last_byte = new Unsigned32();
  public pgci_srp_t[] pgci_srp;

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("nr_of_pgci_srp: ");
    sb.append(nr_of_pgci_srp.get());
    sb.append('\n');
    if(pgci_srp!=null)
    {
      for(int i=0;i<nr_of_pgci_srp.get();i++)
      {
        sb.append("pgci_srp ");
        sb.append(i);
        sb.append(":\n");
        sb.append(pgci_srp[i]);
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  public boolean create()
  {
    if(nr_of_pgci_srp.get()!=0)
    {
      pgci_srp = new pgci_srp_t[nr_of_pgci_srp.get()];
      for(int i=0;i<nr_of_pgci_srp.get();i++)
      {
        pgci_srp[i] = new pgci_srp_t();
        pgci_srp[i].setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+8+i*8);

        pgci_srp[i].pgc = new pgc_t();
        pgci_srp[i].pgc.setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+
            (int)pgci_srp[i].pgc_start_byte.get());
        pgci_srp[i].pgc.create();
      }
    }
    return true;
  }
}
//#define PGCIT_SIZE 8
