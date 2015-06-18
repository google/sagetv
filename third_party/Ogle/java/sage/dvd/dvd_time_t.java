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

public class dvd_time_t extends Struct
{
  public Unsigned8 hour = new Unsigned8();
  public Unsigned8 minute = new Unsigned8();
  public Unsigned8 second = new Unsigned8();
  public Unsigned8 frame_u = new Unsigned8();

  public long toPTS()
  {
    long time;

    time =  (((long)hour.get()>>4) * 10 + ((long)hour.get()&0x0f)) *60; // time in minutes
    time = (time + (((long)minute.get()>>4) * 10 + (minute.get()  & 0x0f))) * 60; // time in seconds
    time = (time + ((long)(second.get()>>4)*10 + (second.get()& 0x0f))); // time in seconds
    // Modified NTSC to be 3003 instead of 3000, it seems to be right from the PTS in the streams.
    if((frame_u.get() & 0x80)!=0)
    {
      time = (time*30 + (((long)(frame_u.get() & 0x30)>>4) * 10 + ((long)(frame_u.get() & 0x0F)))) * 3003;
    }
    else
    {
      time = (time*25 + (((long)(frame_u.get() & 0x30)>>4) * 10 + ((long)(frame_u.get() & 0x0F)))) * 3600;
    }
    return time;
  }

}
