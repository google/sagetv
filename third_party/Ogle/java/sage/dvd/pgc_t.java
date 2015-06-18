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

public class pgc_t extends Struct
{
  public Unsigned16 zero_1 = new Unsigned16();
  public Unsigned8  nr_of_programs = new Unsigned8();
  public Unsigned8  nr_of_cells = new Unsigned8();
  public dvd_time_t playback_time = (dvd_time_t) inner(new dvd_time_t());
  public user_ops_t prohibited_ops = (user_ops_t) inner(new user_ops_t());
  public Unsigned16[] audio_control = (Unsigned16[]) array(new Unsigned16[8]);
  public Unsigned32[] subp_control = (Unsigned32[]) array(new Unsigned32[32]);
  public Unsigned16 next_pgc_nr = new Unsigned16();
  public Unsigned16 prev_pgc_nr = new Unsigned16();
  public Unsigned16 goup_pgc_nr = new Unsigned16();
  public Unsigned8  still_time = new Unsigned8();
  public Unsigned8  pg_playback_mode = new Unsigned8();
  public Unsigned32[] palette = (Unsigned32[]) array(new Unsigned32[16]);
  public Unsigned16 command_tbl_offset = new Unsigned16();
  public Unsigned16 program_map_offset = new Unsigned16();
  public Unsigned16 cell_playback_offset = new Unsigned16();
  public Unsigned16 cell_position_offset = new Unsigned16();
  public pgc_command_tbl_t command_tbl;
  public pgc_program_map_t program_map[];
  public cell_playback_t cell_playback[];
  public cell_position_t cell_position[];

  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("pgc_t:\n");
    sb.append(super.toString());
    sb.append('\n');
    sb.append("nr_of_programs: ");
    sb.append(nr_of_programs.get());
    sb.append('\n');
    sb.append("nr_of_cells: ");
    sb.append(nr_of_cells.get());
    sb.append('\n');
    sb.append("command table: (offset ");
    sb.append(command_tbl_offset.get());
    sb.append(")\n");
    if(command_tbl!=null)
    {
      sb.append(command_tbl);
      sb.append('\n');
    }
    sb.append("program_map: (offset ");
    sb.append(program_map_offset.get());
    sb.append(")\n");
    if(program_map!=null)
    {
      for(int i=0;i<nr_of_programs.get();i++)
      {
        sb.append("program ");
        sb.append(i);
        sb.append(" entry cell: ");
        sb.append(program_map[i].ele.get());
        sb.append('\n');
      }
    }
    sb.append("cell_playback: (offset ");
    sb.append(cell_playback_offset.get());
    sb.append(")\n");
    if(cell_playback!=null)
    {
      for(int i=0;i<nr_of_cells.get();i++)
      {
        sb.append("cell ");
        sb.append(i);
        sb.append(" playback : ");
        sb.append(cell_playback[i]);
        sb.append('\n');
      }
    }
    sb.append("cell_position: (offset ");
    sb.append(cell_position_offset.get());
    sb.append(")\n");
    if(cell_position!=null)
    {
      for(int i=0;i<nr_of_cells.get();i++)
      {
        sb.append("cell ");
        sb.append(i);
        sb.append(" position : ");
        sb.append(cell_position[i]);
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  public boolean create()
  {
    if(command_tbl_offset.get()!=0)
    {
      command_tbl = new pgc_command_tbl_t();
      command_tbl.setByteBuffer(getByteBuffer(),
          getByteBufferPosition()+command_tbl_offset.get());
      command_tbl.create();
    }

    if(program_map_offset.get()!=0)
    {
      program_map = new pgc_program_map_t[nr_of_programs.get()];
      for(int i=0;i<nr_of_programs.get();i++)
      {
        program_map[i] = new pgc_program_map_t();
        program_map[i].setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+program_map_offset.get()+i*program_map[i].size());
      }
    }
    if(cell_playback_offset.get()!=0)
    {
      cell_playback = new cell_playback_t[nr_of_cells.get()];
      for(int i=0;i<nr_of_cells.get();i++)
      {
        cell_playback[i] = new cell_playback_t();
        cell_playback[i].setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+cell_playback_offset.get()+i*cell_playback[i].size());
      }
    }

    if(cell_position_offset.get()!=0)
    {
      cell_position = new cell_position_t[nr_of_cells.get()];
      for(int i=0;i<nr_of_cells.get();i++)
      {
        cell_position[i] = new cell_position_t();
        cell_position[i].setByteBuffer(getByteBuffer(),
            getByteBufferPosition()+cell_position_offset.get()+i*cell_position[i].size());
      }
    }
    return true;
  }
}
//#define PGC_SIZE 236
