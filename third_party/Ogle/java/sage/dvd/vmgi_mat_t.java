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

public class vmgi_mat_t extends Struct
{
  public Unsigned8[] vmg_identifier = (Unsigned8[]) array(new Unsigned8[12]);
  public Unsigned32 vmg_last_sector = new Unsigned32();
  public Unsigned8[] zero_1 = (Unsigned8[]) array(new Unsigned8[12]);
  public Unsigned32 vmgi_last_sector = new Unsigned32();
  public Unsigned8 zero_2 = new Unsigned8();
  public Unsigned8 specification_version = new Unsigned8();
  public Unsigned32 vmg_category = new Unsigned32();
  public Unsigned16 vmg_nr_of_volumes = new Unsigned16();
  public Unsigned16 vmg_this_volume_nr = new Unsigned16();
  public Unsigned8 disc_side = new Unsigned8();
  public Unsigned8[] zero_3 = (Unsigned8[]) array(new Unsigned8[19]);
  public Unsigned16 vmg_nr_of_title_sets = new Unsigned16();
  public Unsigned8[] provider_identifier = (Unsigned8[]) array(new Unsigned8[32]);
  public Signed64 vmg_pos_code = new Signed64(); // We can't do unsigned?
  public Unsigned8[] zero_4 = (Unsigned8[]) array(new Unsigned8[24]);
  public Unsigned32 vmgi_last_byte = new Unsigned32();
  public Unsigned32 first_play_pgc = new Unsigned32();
  public Unsigned8[] zero_5 = (Unsigned8[]) array(new Unsigned8[56]);
  public Unsigned32 vmgm_vobs = new Unsigned32();
  public Unsigned32 tt_srpt = new Unsigned32();
  public Unsigned32 vmgm_pgci_ut = new Unsigned32();
  public Unsigned32 ptl_mait = new Unsigned32();
  public Unsigned32 vts_atrt = new Unsigned32();
  public Unsigned32 txtdt_mgi = new Unsigned32();
  public Unsigned32 vmgm_c_adt = new Unsigned32();
  public Unsigned32 vmgm_vobu_admap = new Unsigned32();
  public Unsigned8[] zero_6 = (Unsigned8[]) array(new Unsigned8[32]);

  public video_attr_t vmgm_video_attr = (video_attr_t) inner(new video_attr_t());
  public Unsigned8 zero_7 = new Unsigned8();
  public Unsigned8 nr_of_vmgm_audio_streams = new Unsigned8();
  public audio_attr_t vmgm_audio_attr = (audio_attr_t) inner(new audio_attr_t());
  public audio_attr_t[] zero_8 = (audio_attr_t[]) array((Struct [])new audio_attr_t[7]); // TODO: verify this
  public Unsigned8[] zero_9 = (Unsigned8[]) array(new Unsigned8[17]);
  public Unsigned8 nr_of_vmgm_subp_streams = new Unsigned8();
  public subp_attr_t vmgm_subp_attr = (subp_attr_t) inner(new subp_attr_t());
  public subp_attr_t[] zero_10 = (subp_attr_t[]) array((Struct [])new subp_attr_t[27]);
}
