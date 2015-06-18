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

public class user_ops_t extends Struct
{
  public Unsigned8 zero = new Unsigned8(7);
  public Unsigned8 video_pres_mode_change = new Unsigned8(1);

  public Unsigned8 karaoke_audio_pres_mode_change = new Unsigned8(1);
  public Unsigned8 angle_change = new Unsigned8(1);
  public Unsigned8 subpic_stream_change = new Unsigned8(1);
  public Unsigned8 audio_stream_change = new Unsigned8(1);
  public Unsigned8 pause_on = new Unsigned8(1);
  public Unsigned8 still_off = new Unsigned8(1);
  public Unsigned8 button_select_or_activate = new Unsigned8(1);
  public Unsigned8 resume = new Unsigned8(1);

  public Unsigned8 chapter_menu_call = new Unsigned8(1);
  public Unsigned8 angle_menu_call = new Unsigned8(1);
  public Unsigned8 audio_menu_call = new Unsigned8(1);
  public Unsigned8 subpic_menu_call = new Unsigned8(1);
  public Unsigned8 root_menu_call = new Unsigned8(1);
  public Unsigned8 title_menu_call = new Unsigned8(1);
  public Unsigned8 backward_scan = new Unsigned8(1);
  public Unsigned8 forward_scan = new Unsigned8(1);

  public Unsigned8 next_pg_search = new Unsigned8(1);
  public Unsigned8 prev_or_top_pg_search = new Unsigned8(1);
  public Unsigned8 time_or_chapter_search = new Unsigned8(1);
  public Unsigned8 go_up = new Unsigned8(1);
  public Unsigned8 stop = new Unsigned8(1);
  public Unsigned8 title_play = new Unsigned8(1);
  public Unsigned8 chapter_search_or_play = new Unsigned8(1);
  public Unsigned8 title_or_time_play = new Unsigned8(1);
}
