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

public class vts_attributes_t extends Struct
{
  public Unsigned32 last_byte = new Unsigned32();
  public Unsigned32 vts_cat = new Unsigned32();

  public Struct vtsm_vobs_attr = inner(new video_attr_t());
  public Unsigned8 zero_1 = new Unsigned8();
  public Unsigned8 nr_of_vtsm_audio_streams = new Unsigned8();
  public Struct vtsm_audio_attr = inner(new audio_attr_t());
  public Struct[] zero_2 = array((Struct[])new audio_attr_t[7]);
  public Unsigned8[] zero_3 = (Unsigned8[]) array(new Unsigned8[16]);
  public Unsigned8 zero_4 = new Unsigned8();
  public Unsigned8 nr_of_vtsm_subp_streams = new Unsigned8();
  public Struct vtsm_subp_attr = inner(new subp_attr_t());
  public Struct[] zero_5 = array((Struct[])new subp_attr_t[27]);

  public Unsigned8[] zero_6 = (Unsigned8[]) array(new Unsigned8[2]);

  public Struct vtstt_vobs_video_attr = inner(new video_attr_t());
  public Unsigned8 zero_7 = new Unsigned8();
  public Unsigned8 nr_of_vtstt_audio_streams = new Unsigned8();
  public Struct[] vtstt_audio_attr = array((Struct[])new audio_attr_t[8]);
  public Unsigned8[] zero_8 = (Unsigned8[]) array(new Unsigned8[16]);
  public Unsigned8 zero_9 = new Unsigned8();
  public Unsigned8 nr_of_vtstt_subp_streams = new Unsigned8();
  public Struct[] vtstt_subp_attr = array((Struct[])new subp_attr_t[32]);
}
//#define VTS_ATTRIBUTES_SIZE 542
//#define VTS_ATTRIBUTES_MIN_SIZE 356
