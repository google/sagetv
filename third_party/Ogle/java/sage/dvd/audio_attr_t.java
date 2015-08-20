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

public class audio_attr_t extends Struct
{
  public Unsigned8 audio_format = new Unsigned8(3);
  public Unsigned8 multichannel_extension = new Unsigned8(1);
  public Unsigned8 lang_type = new Unsigned8(2);
  public Unsigned8 application_mode = new Unsigned8(2);

  public Unsigned8 quantization = new Unsigned8(2);
  public Unsigned8 sample_frequency = new Unsigned8(2);
  public Unsigned8 unknown1 = new Unsigned8(1);
  public Unsigned8 channels = new Unsigned8(3);

  public Unsigned16 lang_code = new Unsigned16();
  public Unsigned8 lang_extension = new Unsigned8();
  public Unsigned8 code_extension = new Unsigned8();
  public Unsigned8 unknown3 = new Unsigned8();

  // TODO: add karaoke case
  public Unsigned8 unknown5 = new Unsigned8(4);
  public Unsigned8 dolby_encoded = new Unsigned8(1); /* suitable for surround decoding */
  public Unsigned8 unknown6 = new Unsigned8(3);
}
