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

import java.nio.*;

public class IFO
{
  DVDSource file;

  ByteBuffer IFOBuffer;

  /* VMGI */
  vmgi_mat_t     vmgi_mat;
  tt_srpt_t      tt_srpt;
  pgc_t          first_play_pgc;
  ptl_mait_t     ptl_mait;
  vts_atrt_t     vts_atrt;
  txtdt_mgi_t    txtdt_mgi;

  /* Common */
  pgci_ut_t      pgci_ut;
  c_adt_t        menu_c_adt;
  vobu_admap_t   menu_vobu_admap;

  /* VTSI */
  vtsi_mat_t     vtsi_mat;
  vts_ptt_srpt_t vts_ptt_srpt;
  pgcit_t        vts_pgcit;
  vts_tmapt_t    vts_tmapt;
  c_adt_t        vts_c_adt;
  vobu_admap_t   vts_vobu_admap;


  private boolean loadFP_PGC()
  {
    // Don't fail on no first play pgc?
    if(vmgi_mat.first_play_pgc.get()==0) return true;
    first_play_pgc = new pgc_t();
    first_play_pgc.setByteBuffer(IFOBuffer, (int)vmgi_mat.first_play_pgc.get());
    if(first_play_pgc.create()!=false)
    {
      if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(first_play_pgc);
      return true;
    }
    return false;
  }

  private boolean loadTT_SRPT()
  {
    if(vmgi_mat.tt_srpt.get()==0) return false;
    tt_srpt = new tt_srpt_t();
    tt_srpt.setByteBuffer(IFOBuffer, (int)vmgi_mat.tt_srpt.get()*2048);
    tt_srpt.title = (title_info_t[])(tt_srpt.array(new title_info_t[tt_srpt.nr_of_srpts.get()]));
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(tt_srpt);
    return true;
  }

  private boolean loadPCGI_UT()
  {
    if(vmgi_mat!=null)
    {
      if(vmgi_mat.vmgm_pgci_ut.get()==0) return true;
      pgci_ut = new pgci_ut_t();
      pgci_ut.setByteBuffer(IFOBuffer, (int)vmgi_mat.vmgm_pgci_ut.get()*2048);
    }
    else if(vtsi_mat!=null)
    {
      if(vtsi_mat.vtsm_pgci_ut.get()==0) return true;
      pgci_ut = new pgci_ut_t();
      pgci_ut.setByteBuffer(IFOBuffer, (int)vtsi_mat.vtsm_pgci_ut.get()*2048);
    }
    else
    {
      return false;
    }
    pgci_ut.create();
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(pgci_ut);
    return true;
  }

  private boolean loadPTL_MAIT()
  {
    if(vmgi_mat.ptl_mait.get()==0) return false;
    ptl_mait = new ptl_mait_t();
    ptl_mait.setByteBuffer(IFOBuffer, (int)vmgi_mat.ptl_mait.get()*2048);
    ptl_mait.create();
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(ptl_mait);
    return true;
  }

  private boolean loadVTS_ATRT()
  {
    if(vmgi_mat.vts_atrt.get()==0) return false;
    vts_atrt = new vts_atrt_t();
    vts_atrt.setByteBuffer(IFOBuffer, (int)vmgi_mat.vts_atrt.get()*2048);
    vts_atrt.create();
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(vts_atrt);
    return true;
  }

  private boolean loadMENU_C_ADT()
  {
    if(vmgi_mat!=null)
    {
      if(vmgi_mat.vmgm_c_adt.get()==0) return true;
      menu_c_adt = new c_adt_t();
      menu_c_adt.setByteBuffer(IFOBuffer, (int)vmgi_mat.vmgm_c_adt.get()*2048);
    }
    else if(vtsi_mat!=null)
    {
      if(vtsi_mat.vtsm_c_adt.get()==0) return true;
      menu_c_adt = new c_adt_t();
      menu_c_adt.setByteBuffer(IFOBuffer, (int)vtsi_mat.vtsm_c_adt.get()*2048);
    }
    else
    {
      return false;
    }
    menu_c_adt.create();
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(menu_c_adt);
    return true;
  }

  private boolean loadMENU_VOBU_ADMAP()
  {
    if(vmgi_mat!=null)
    {
      if(vmgi_mat.vmgm_vobu_admap.get()==0) return true;
      menu_vobu_admap = new vobu_admap_t();
      menu_vobu_admap.setByteBuffer(IFOBuffer, (int)vmgi_mat.vmgm_vobu_admap.get()*2048);
    }
    else if(vtsi_mat!=null)
    {
      if(vtsi_mat.vtsm_vobu_admap.get()==0) return true;
      menu_vobu_admap = new vobu_admap_t();
      menu_vobu_admap.setByteBuffer(IFOBuffer, (int)vtsi_mat.vtsm_vobu_admap.get()*2048);
    }
    else
    {
      return false;
    }
    menu_vobu_admap.create();
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(menu_vobu_admap);
    return true;
  }

  private boolean loadVTS_TT_SPRT()
  {
    if(vtsi_mat.vts_ptt_srpt.get()==0) return false;
    vts_ptt_srpt = new vts_ptt_srpt_t();
    vts_ptt_srpt.setByteBuffer(IFOBuffer, (int)vtsi_mat.vts_ptt_srpt.get()*2048);
    vts_ptt_srpt.create();
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(vts_ptt_srpt);
    return true;
  }

  private boolean loadVTS_PGCIT()
  {
    if(vtsi_mat.vts_pgcit.get()==0) return false;
    vts_pgcit = new pgcit_t();
    vts_pgcit.setByteBuffer(IFOBuffer, (int)vtsi_mat.vts_pgcit.get()*2048);
    vts_pgcit.create();
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(vts_pgcit);
    return true;
  }

  private boolean loadVTS_TMAPT()
  {
    if(vtsi_mat.vts_tmapt.get()==0) return false;
    vts_tmapt = new vts_tmapt_t();
    vts_tmapt.setByteBuffer(IFOBuffer, (int)vtsi_mat.vts_tmapt.get()*2048);
    vts_tmapt.create();
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(vts_tmapt);
    return true;
  }

  private boolean loadVTS_C_ADT()
  {
    if(vtsi_mat.vts_c_adt.get()==0) return false;
    vts_c_adt = new c_adt_t();
    vts_c_adt.setByteBuffer(IFOBuffer, (int)vtsi_mat.vts_c_adt.get()*2048);
    vts_c_adt.create();
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(vts_c_adt);
    return true;
  }

  private boolean loadVTS_VOBU_ADMAP()
  {
    if(vtsi_mat.vts_vobu_admap.get()==0) return true;
    vts_vobu_admap = new vobu_admap_t();
    vts_vobu_admap.setByteBuffer(IFOBuffer, (int)vtsi_mat.vts_vobu_admap.get()*2048);
    vts_vobu_admap.create();
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println(vts_vobu_admap);
    return true;
  }


  private boolean loadVMG()
  {
    vmgi_mat = new vmgi_mat_t();
    file.seek(0);
    ByteBuffer vmgibuffer = ByteBuffer.wrap(new byte[vmgi_mat.size()]);
    vmgibuffer.order(ByteOrder.BIG_ENDIAN);
    file.readBytes(vmgibuffer.array(), vmgi_mat.size());
    vmgi_mat.setByteBuffer(vmgibuffer, 0);
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("VMGI: "+vmgi_mat);
    byte identifier[] = new byte[12];
    for(int i=0;i<12;i++)
    {
      identifier[i]=(byte)((Struct.Unsigned8)vmgi_mat.vmg_identifier[i]).get();
    }
    if((new String(identifier)).compareTo("DVDVIDEO-VMG")!=0)
    {
      return false;
    }
    // New version tries loading complete ifo instead since it will save us lots of readBytes,
    // creating byte buffers and ...
    int ifosize = ((int)vmgi_mat.vmgi_last_sector.get()+1)*2048;
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("size of ifo: "+ifosize);
    IFOBuffer = ByteBuffer.allocate(ifosize);
    IFOBuffer.order(ByteOrder.BIG_ENDIAN);
    file.seek(0);
    file.readBytes(IFOBuffer.array(), IFOBuffer.capacity());
    vmgi_mat.setByteBuffer(IFOBuffer, 0);
    loadFP_PGC();
    loadTT_SRPT();
    loadPCGI_UT();
    loadPTL_MAIT();
    loadVTS_ATRT();
    loadMENU_C_ADT();
    loadMENU_VOBU_ADMAP();
    return true;
  }

  private boolean loadVTS()
  {
    vmgi_mat = null;
    vtsi_mat = new vtsi_mat_t();
    file.seek(0);
    ByteBuffer vtsibuffer = ByteBuffer.wrap(new byte[vtsi_mat.size()]);
    vtsibuffer.order(ByteOrder.BIG_ENDIAN);
    file.readBytes(vtsibuffer.array(), vtsi_mat.size());
    vtsi_mat.setByteBuffer(vtsibuffer, 0);
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("VTSI: "+vtsi_mat);
    byte identifier[] = new byte[12];
    for(int i=0;i<12;i++)
    {
      identifier[i]=(byte)((Struct.Unsigned8)vtsi_mat.vts_identifier[i]).get();
    }
    if((new String(identifier)).compareTo("DVDVIDEO-VTS")!=0)
    {
      return false;
    }
    // New version tries loading complete ifo instead since it will save us lots of readBytes,
    // creating byte buffers and ...
    int ifosize = ((int)vtsi_mat.vtsi_last_sector.get()+1)*2048;
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("size of ifo: "+ifosize);
    IFOBuffer = ByteBuffer.allocate(ifosize);
    IFOBuffer.order(ByteOrder.BIG_ENDIAN);
    file.seek(0);
    file.readBytes(IFOBuffer.array(), IFOBuffer.capacity());
    vtsi_mat.setByteBuffer(IFOBuffer, 0);
    loadVTS_TT_SPRT();
    loadVTS_PGCIT();
    loadPCGI_UT();
    loadVTS_TMAPT();
    loadMENU_C_ADT();
    loadMENU_VOBU_ADMAP();
    loadVTS_C_ADT();
    loadVTS_VOBU_ADMAP();
    return true;
  }

  public IFO(DVDSource file)
  {
    this.file=file;
    if(loadVMG())
    {
      if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("Loaded VMG");
    }
    else if(loadVTS())
    {
      if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("Loaded VTS");
    }
    else
    {
      if (sage.Sage.DBG) System.out.println("Invalid IFO file!\n");
      // Throw exception here...
    }
  }
}
