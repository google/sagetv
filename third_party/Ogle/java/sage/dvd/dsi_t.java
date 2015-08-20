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

public class dsi_t extends Struct
{
  public dsi_gi_t dsi_gi = (dsi_gi_t) inner(new dsi_gi_t());
  public sml_pbi_t  sml_pbi = (sml_pbi_t) inner(new sml_pbi_t ());
  public sml_agli_t sml_agli = (sml_agli_t) inner(new sml_agli_t());
  public vobu_sri_t vobu_sri = (vobu_sri_t) inner(new vobu_sri_t());
  public synci_t synci = (synci_t) inner(new synci_t());
  public Unsigned8[] zero1 = (Unsigned8[]) array(new Unsigned8[471]);
}