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

public class vtsi_mat_t extends Struct
{
  public Unsigned8[] vts_identifier = (Unsigned8[]) array(new Unsigned8[12]);
  public Unsigned32 vts_last_sector = new Unsigned32();
  public Unsigned8[] zero_1 = (Unsigned8[]) array(new Unsigned8[12]);
  public Unsigned32 vtsi_last_sector = new Unsigned32();
  public Unsigned8 zero_2 = new Unsigned8();
  public Unsigned8 specification_version = new Unsigned8();
  public Unsigned32 vts_category = new Unsigned32();
  public Unsigned16 zero_3 = new Unsigned16();
  public Unsigned16 zero_4 = new Unsigned16();
  public Unsigned8 zero_5 = new Unsigned8();
  public Unsigned8[]  zero_6 = (Unsigned8[]) array(new Unsigned8[19]);
  public Unsigned16 zero_7 = new Unsigned16();
  public Unsigned8[] zero_8 = (Unsigned8[]) array(new Unsigned8[32]);
  public Signed64 zero_9 = new Signed64(); // no unsigned?
  public Unsigned8[] zero_10 = (Unsigned8[]) array(new Unsigned8[24]);
  public Unsigned32 vtsi_last_byte = new Unsigned32();
  public Unsigned32 zero_11 = new Unsigned32();
  public Unsigned8[] zero_12 = (Unsigned8[]) array(new Unsigned8[56]);
  public Unsigned32 vtsm_vobs = new Unsigned32();
  public Unsigned32 vtstt_vobs = new Unsigned32();
  public Unsigned32 vts_ptt_srpt = new Unsigned32();
  public Unsigned32 vts_pgcit = new Unsigned32();
  public Unsigned32 vtsm_pgci_ut = new Unsigned32();
  public Unsigned32 vts_tmapt = new Unsigned32();
  public Unsigned32 vtsm_c_adt = new Unsigned32();
  public Unsigned32 vtsm_vobu_admap = new Unsigned32();
  public Unsigned32 vts_c_adt = new Unsigned32();
  public Unsigned32 vts_vobu_admap = new Unsigned32();
  public Unsigned8[] zero_13 = (Unsigned8[]) array(new Unsigned8[24]);

  public video_attr_t vtsm_video_attr = (video_attr_t) inner(new video_attr_t());
  public Unsigned8 zero_14 = new Unsigned8();
  public Unsigned8 nr_of_vtsm_audio_streams = new Unsigned8();
  public audio_attr_t vtsm_audio_attr = (audio_attr_t) inner(new audio_attr_t());
  public audio_attr_t[] zero_15 = (audio_attr_t[]) array((Struct[])new audio_attr_t[7]);
  public Unsigned8[] zero_16 = (Unsigned8[]) array(new Unsigned8[17]);
  public Unsigned8 nr_of_vtsm_subp_streams = new Unsigned8();
  public subp_attr_t vtsm_subp_attr = (subp_attr_t) inner(new subp_attr_t());
  public subp_attr_t[] zero_17 = (subp_attr_t[]) array((Struct[])new subp_attr_t[27]);
  public Unsigned8[] zero_18 = (Unsigned8[]) array(new Unsigned8[2]);

  public video_attr_t vts_video_attr = (video_attr_t) inner(new video_attr_t());
  public Unsigned8 zero_19 = new Unsigned8();
  public Unsigned8 nr_of_vts_audio_streams = new Unsigned8();
  public audio_attr_t[] vts_audio_attr = (audio_attr_t[]) array((Struct[])new audio_attr_t[8]);
  public Unsigned8[] zero_20 = (Unsigned8[]) array(new Unsigned8[17]);
  public Unsigned8 nr_of_vts_subp_streams = new Unsigned8();
  public subp_attr_t[] vts_subp_attr = (subp_attr_t[]) array((Struct[])new subp_attr_t[32]);
  public Unsigned16 zero_21 = new Unsigned16();
  public multichannel_ext_t[] vts_mu_audio_attr = (multichannel_ext_t[]) array((Struct[])new multichannel_ext_t[8]);
}
