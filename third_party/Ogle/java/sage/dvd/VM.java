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

public class VM
{
  // Names of some of the system registers
  public static final int ASTN = 1;
  public static final int SPSTN = 2;
  public static final int AGLN = 3;
  public static final int TTN = 4;
  public static final int VTS_TTN = 5;
  public static final int TT_PGCN = 6;
  public static final int PTTN = 7;
  public static final int HL_BTNN = 8;
  public static final int NVTMR = 9;
  public static final int NV_PGCN = 10;
  public static final int AMXMD = 11;
  public static final int CC_PLT = 12;
  public static final int PLT = 13;


  // Don't modify those since they map to the opcodes directly
  public static final int LINK_NONE = 0;
  public static final int LINK_TOPCELL = 1;
  public static final int LINK_NEXTCELL = 2;
  public static final int LINK_PREVCELL = 3;
  public static final int LINK_TOPPG = 5;
  public static final int LINK_NEXTPG = 6;
  public static final int LINK_PREVPG  = 7;
  public static final int LINK_TOPPGC = 9;
  public static final int LINK_NEXTPGC = 10;
  public static final int LINK_PREVPGC = 11;
  public static final int LINK_GOUPPGC = 12;
  public static final int LINK_TAILPGC = 13;
  public static final int LINK_RSM = 16;
  // Added types for indirectly specified links/calls with params comments
  public static final int LINK_PGCN = 32; // pgcn
  public static final int LINK_PTTN = 33; // hl_bn, pttn
  public static final int LINK_PGN = 34; // hl_bn, pgn
  public static final int LINK_CN = 35; // hl_bn, cn
  public static final int LINK_EXIT = 36;
  public static final int LINK_JUMPTT = 37; // ttn
  public static final int LINK_JUMPVTS_TT = 38; // ttn
  public static final int LINK_JUMPVTS_PTT = 39; // ttn pttn
  public static final int LINK_JUMPSS_FP = 40;
  public static final int LINK_JUMPSS_VMGM_MENU = 41; // menu
  public static final int LINK_JUMPSS_VTSM = 42; // vts, ttn, menu
  public static final int LINK_JUMPSS_VMGM_PGCN = 43; // pgcn
  public static final int LINK_CALLSS_FP = 44;
  public static final int LINK_CALLSS_VMGM_MENU = 45; // menu
  public static final int LINK_CALLSS_VTSM = 46; // menu
  public static final int LINK_CALLSS_VMGM_PGCN = 47; // pgcn
  // Used to know when there is nothing to change
  public static final int LINK_CONTINUE = 63;


  // Current DVD domain
  public static final int FP_DOMAIN   = 1;
  public static final int VMGM_DOMAIN = 2;
  public static final int VTSM_DOMAIN = 3;
  public static final int VTS_DOMAIN  = 4;

  public static final int DVD_MENU_Escape = 0;
  public static final int DVD_MENU_Title = 2;
  public static final int DVD_MENU_Root = 3;
  public static final int DVD_MENU_Subpicture = 4;
  public static final int DVD_MENU_Audio = 5;
  public static final int DVD_MENU_Angle = 6;
  public static final int DVD_MENU_Part = 7;

  // unsigned 16 bits general purpose registers
  public int[] GPRM = new int[16];
  public int GPRMtimer = 0; // bit for each register to specify in timer mode
  public long[] GPRM_inittime = new long[16]; // initial time for each register when in timer mode
  // TODO: might need to add some more for handling time mode

  // 0,12,14,15,16,17,18,19,20 are read-only
  public int SPRM[] = new int[24];


  public int domain = 0; // 0 is not set yet
  public int vts = -1;
  public int program = 0;
  public int cell = 0;
  public boolean cellangle = false;
  public int cell_restart = 0;
  public int block = 0;
  public pgc_t pgc;

  //TODO: add resume information...
  public int resume_SPRM[] = new int[24];   // note: only 4,5,6,7,8 are restored
  public int resume_vts = -1;
  public int resume_program = 0;
  public int resume_cell = 0;
  public int resume_block = 0;
  public int resume_pgc;

  // Variables used to communicate changes for the player
  public int player_button = 0;
  public int player_vts = -1;
  public int player_domain = -1;
  public int player_cell = -1;
  public int player_cell_sector = -1;
  public int player_cell_restart = 0;
  public int player_block = 0;
  public int player_spu = -1;
  public int player_audio = -1;
  // base sector offset of current vobu from the cell
  public int player_vobu_sector = 0;
  // additional offset of sector sent to the player
  public int player_vobu_offset = 0;
  // length of the current vobu in sectors
  public int player_vobu_length_sector = 0;
  // sector offset that should be used once the current vobu is done to follow angles
  public int player_next_vobu_sector = 0;
  public pci_t player_pci = null;
  public dsi_t player_dsi = null;
  public float player_rate = 1.0f;
  public int player_vobu_skip = 0;

  public int in_still = 0; // contains number of seconds or 0 if not
  public boolean done_still = false;
  public boolean need_empty = false;
  public boolean done_empty = false;
  public boolean new_palette = false;
  public int running = 0; // stopped=-1 uninit=0 init=1
  public boolean need_flush = false;

  public long forced_start = -1;
  public boolean monitor16_9 = false;

  public DVDSource vmgfile;
  public IFO vmgifo;
  public DVDSource vtsfile;
  public IFO vtsifo;
  public DVDReader reader;

  public DVDSource titlefile;
  public String dvdsource;

  public boolean debugVM=sage.MiniDVDPlayer.DEBUG_MINIDVD && sage.Sage.getBoolean("debug_dvd_vm", false);
  public boolean debugMPEG=false;
  public boolean debugSTATE=sage.MiniDVDPlayer.DEBUG_MINIDVD && sage.Sage.getBoolean("debug_dvd_state", false);

  public boolean reset(String dvdsource, String hostname)
  {
    for(int i=0;i<16;i++)
    {
      GPRM[i]=0;
      GPRM_inittime[i] = 0;
    }
    for(int i=0;i<24;i++)
    {
      SPRM[i]=0;
    }

    SPRM[0] = 0x656E; // en for English
    SPRM[ASTN] = 15;
    SPRM[SPSTN] = 62;
    SPRM[AGLN] = 1;
    SPRM[TTN] = 1;
    SPRM[VTS_TTN] = 1;
    SPRM[TT_PGCN] = 1;
    SPRM[PTTN] = 1;
    SPRM[HL_BTNN] = 1<<10;
    SPRM[NVTMR] = 0;
    SPRM[NV_PGCN] = 1;
    SPRM[AMXMD] = 0;
    SPRM[CC_PLT] = 0x5553; // US for United States
    SPRM[PLT] = 15;
    SPRM[14] = 0x200; // Letterbox?
    SPRM[15] = 0x5000; // audio caps
    SPRM[16] = 0x656E; // en for English
    SPRM[18] = 0x656E; // en for English
    SPRM[20] = 1; // Region 1 ?

    domain = FP_DOMAIN;
    vts = -1;
    program = 0;
    cell = 0;
    block = 0;
    pgc = null;
    running = 0;

    resume_SPRM = new int[24];   // note: only 4,5,6,7,8 are restored
    resume_vts = 0;
    resume_program = 0;
    resume_cell = 0;
    resume_block = 0;
    resume_pgc = 0;

    player_button = 0;
    player_vts = -1;
    player_domain = -1;
    player_cell = -1;
    player_cell_sector = -1;
    player_cell_restart = 0;
    player_block = 0;
    player_spu = -1;
    player_audio = -1;

    player_rate = 1.0f;
    player_vobu_skip = 0;
    player_vobu_sector = 0;
    player_vobu_offset = 0;
    player_vobu_length_sector = 0;
    player_next_vobu_sector = 0;
    forced_start = -1;

    player_pci = new pci_t();
    player_dsi = new dsi_t();

    in_still = 0;
    done_still = false;
    need_empty = false;
    done_empty = false;
    new_palette = false;
    running = 0;
    need_flush = false;

    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("VM reset with dvd source: "+dvdsource);
    this.dvdsource = dvdsource;
    reader = new DVDFileReader();
    reader.open(dvdsource, hostname);
    if(vmgfile!=null)
    {
      vmgfile.close();
      vmgfile=null;
    }
    vmgfile = reader.openFile(0, 0);
    if(vmgfile!=null)
    {
      if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("Trying to parse vmg ifo\n");
      vmgifo = new IFO(vmgfile);
      pgc = vmgifo.first_play_pgc;
      return true;
    }
    return false;
  }

  public void close()
  {
    // TODO: figure out if there is anything we need to close by hand
    if(vtsfile!=null)
    {
      vtsfile.close();
      vtsfile=null;
    }
    if(vmgfile!=null)
    {
      vmgfile.close();
      vmgfile=null;
    }
    if(titlefile!=null)
    {
      titlefile.close();
      titlefile=null;
    }
    if(reader!=null)
    {
      reader.close();
      reader=null;
    }
  }

  private int getGPRM(int reg)
  {
    reg&=0xF; // 16 registers...
    if((GPRMtimer&(1<<reg))!=0)
    {
      int v=(int)((sage.Sage.eventTime()-GPRM_inittime[reg])/1000)&0xFFFF;
      if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("Reading time reg: "+v);
      GPRM[reg]=v;
      return v;
    }
    else
    {
      return GPRM[reg]&0xFFFF;
    }
  }

  private void setGPRM(int reg, int val)
  {
    reg&=0xF; // 16 registers...
    val&=0xFFFF;
    if((GPRMtimer&(1<<reg))!=0)
    {
      GPRM_inittime[reg] = sage.Sage.eventTime()-(val*1000);

    }
    GPRM[reg]=val;
  }

  private void setSPRM(int reg, int val)
  {
    reg&=0x1F; // 16 registers...
    val&=0xFFFF;
    switch(reg)
    {
      // Those are read only registers
      case 0:
      case 12:
      case 14:
      case 15:
      case 16:
      case 17:
      case 18:
      case 19:
      case 20:
        return;
      default:
        SPRM[reg]=val;
    }
  }

  private int getReg(int reg)
  {
    if((reg&0x80)!=0) // TODO: Is there a more elegant way to do that in java?
    {
      if((reg&0x7F)<24)
      {
        return SPRM[reg & 0x1f];
      }
      else
      {
        return 0; // We could add a warning...
      }
    }
    else
    {
      if((reg&0x7F)<16)
      {
        return getGPRM(reg);
      }
      else
      {
        return 0; // We could add a warning...
      }
    }
  }

  private void setReg(int reg, int value)
  {
    value&=0xFFFF;
    if((reg&0x80)!=0) // TODO: Is there a more elegant way to do that in java?
    {
      if((reg&0x7F)<24)
      {
        // TODO: We will want to add callback stuff here probably...
        SPRM[reg & 0x1f]=value;
      }
      else
      {
        return; // We could add a warning...
      }
    }
    else
    {
      if((reg&0x7F)<16)
      {
        setGPRM(reg, value);
      }
      else
      {
        return; // We could add a warning...
      }
    }
  }

  private void setOpcode(int set, int reg1, int reg2, boolean immediate)
  {
    int operand2;
    int tmp;
    if(immediate)
    {
      operand2=reg2;
    }
    else
    {
      operand2=getReg(reg2);
    }

    switch(set)
    {
      case 0:
        return;
      case 1: // mov
        setGPRM(reg1,operand2);
        return;
      case 2: // swp
        tmp=getGPRM(reg1);
        setGPRM(reg1,operand2);
        if(!immediate)
        {
          setGPRM(reg2,tmp);
        }
        return;
      case 3: // add
        tmp=getGPRM(reg1);
        tmp+=operand2;
        if(tmp>65535)tmp=65535;
        setGPRM(reg1,tmp);
        return;
      case 4: // sub
        tmp=getGPRM(reg1);
        tmp-=operand2;
        if(tmp<0)tmp=0;
        setGPRM(reg1,tmp);
        return;
      case 5: // mul
        tmp=getGPRM(reg1);
        tmp*=operand2;
        if(tmp>65535)tmp=65535;
        setGPRM(reg1,(int)tmp);
        return;
      case 6: // div
        tmp=getGPRM(reg1);
        if(operand2!=0)
        {
          tmp/=operand2;
        }
        else
        {
          tmp=65535;
        }
        if(tmp>65535)tmp=65535;
        setGPRM(reg1,tmp);
        return;
      case 7: // mod
        tmp=getGPRM(reg1);
        if(operand2!=0)
        {
          tmp%=operand2;
        }
        else
        {
          tmp=65535;
        }
        if(tmp>65535)tmp=65535;
        setGPRM(reg1,tmp);
        return;
      case 8: // random
        tmp=(int) (java.lang.Math.random()*(double)(operand2+1));
        if(tmp<0)tmp=0;
        if(tmp>65535)tmp=65535;
        setGPRM(reg1,tmp);
        return;
      case 9: // and
        tmp=getGPRM(reg1);
        tmp&=operand2;
        if(tmp>65535)tmp=65535;
        setGPRM(reg1,tmp);
        return;
      case 10: // or
        tmp=getGPRM(reg1);
        tmp|=operand2;
        if(tmp>65535)tmp=65535;
        setGPRM(reg1,tmp);
        return;
      case 11: // and
        tmp=getGPRM(reg1);
        tmp^=operand2;
        if(tmp>65535)tmp=65535;
        setGPRM(reg1,tmp);
        return;
    }
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("Invalid setOpcode "+set);
  }

  // see http://dvd.sourceforge.net/dvdinfo/vmi.html
  private boolean compareOpcode(int cmp, int operand1, int operand2)
  {
    switch(cmp)
    {
      case 0:
        return true;
      case 1:
        return ((operand1 & operand2)!=0);
      case 2:
        return (operand1 == operand2);
      case 3:
        return (operand1 != operand2);
      case 4:
        return (operand1 >= operand2);
      case 5:
        return (operand1 >  operand2);
      case 6:
        return (operand1 <= operand2);
      case 7:
        return (operand1 <  operand2);
    }
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("Invalid compareOpcode "+cmp);
    return false;
  }


  // Note that start is in +1 style of dvd...
  private long executeCommand(vm_cmd_t cmds[], int start, int count)
  {
    int i = 0;
    int total = 0;
    long link=0;

    i = 0;
    while(i < count && total < 65536)
    {
      int line;
      if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("Executing cmd : "+cmds[start-1+i]);
      link = executeCommand(cmds[start-1+i]);

      line =(int) (link&0xFF);
      if((link&0xFF00)!=0)
      {
        return link;
      }

      if(line > 0)
      {
        i = line - 1;
      }
      else
      {
        i++;
      }
      total++;
    }

    return 0;
  }


  // return value encoded:
  // goto address (if any) bits 0-7 // 0 is no special case, 0xFF is break
  // link (if any) bits 8-15 // 0 is no link
  // operand (if any) bits 16-63 // 3 params of 16 bits each

  private long executeCommand(vm_cmd_t vmcmd)
  {
    int type=(vmcmd.bytes[0].get()>>5)&0x7;
    int direct=(vmcmd.bytes[0].get()>>4)&0x1;
    int set=(vmcmd.bytes[0].get()>>0)&0xF;
    int dircmp=(vmcmd.bytes[1].get()>>7)&0x1;
    int cmp=(vmcmd.bytes[1].get()>>4)&0x7;
    int cmd=(vmcmd.bytes[1].get()>>0)&0xF;
    int operand1=(vmcmd.bytes[2].get()<<8)|vmcmd.bytes[3].get();
    int operand2=(vmcmd.bytes[4].get()<<8)|vmcmd.bytes[5].get();
    int operand3=(vmcmd.bytes[6].get()<<8)|vmcmd.bytes[7].get();

    switch(type)
    {
      case 0: // Special instructions
        if(compareOpcode(cmp, getReg(operand1&0xFF),
            dircmp==1 ? operand2 : getReg(operand2&0xFF)))
        {
          int line;
          int level;
          switch(cmd)
          {
            case 0: // NOP
              break;
            case 1: // Goto
              line = vmcmd.bytes[7].get();
              return line;
            case 2: // Break
              return 0xFF;
            case 3: // SetTmpPML
              level = vmcmd.bytes[6].get()&0xF;
              line = vmcmd.bytes[7].get();
              SPRM[13] = level;
              return line;
          }
        }
        break;
      case 1: // Link instructions then direct jump and call instructions
        if(direct==0)
        { // Link
          if(compareOpcode(cmp, getReg(operand1&0xFF),
              dircmp==1 ? operand2 : getReg(operand2&0xFF)))
          {
            switch(cmd)
            {
              case 0: // NOP
                break;
              case 1: // Link
                return ((operand3&0x1F)<<8)|(operand3<<16); // Those maps to the value directly...
              case 4: // LinkPGCN
                return (LINK_PGCN<<8)|(operand3<<16);
              case 5: // LinkPTTN
                return (LINK_PTTN<<8)|(operand3<<16);
              case 6: // LinkPGN
                return (LINK_PGN<<8)|(operand3<<16);
              case 7: // LinkCN
                return (LINK_CN<<8)|(operand3<<16);
            }
          }
        }
        else
        { // Jump/call
          if(compareOpcode(cmp, getReg((operand3>>8)&0xFF),
              getReg(operand3&0xFF)))
          {
            switch(cmd)
            {
              case 0: // NOP
                break;
              case 1: // Exit
                return (LINK_EXIT<<8);
              case 2: // JumpTT
                return (LINK_JUMPTT<<8)|((operand2&0x7F)<<16);
              case 3: // JumpVTS_TT
                return (LINK_JUMPVTS_TT<<8)|((operand2&0x7F)<<16);
              case 5: // JumpVTS_PTT
                return ((long)LINK_JUMPVTS_PTT<<8)|((long)(operand2&0x7F)<<16)|((long)(operand1&0x3FF)<<32);
              case 6: // JumpSS
                switch((operand2>>6)&3)
                {
                  case 0: // FP
                    return (LINK_JUMPSS_FP<<8);
                  case 1: // VMGM menu
                    return (LINK_JUMPSS_VMGM_MENU<<8)|((operand2&0xF)<<16);
                  case 2: // VTSM
                    return ((long)LINK_JUMPSS_VTSM<<8)|
                        (((long)(operand2>>8)&0xFF)<<16)|
                        ((long)(operand1&0xFF)<<32)|
                        ((long)(operand2&0xF)<<48);
                  case 3: // VMGM pgcn
                    return (LINK_JUMPSS_VMGM_PGCN<<8)|((operand1&0x7FFF)<<16); // TODO: how big is operand1?
                }
              case 8: // CallSS
                switch((operand2>>6)&3)
                {
                  case 0: // FP
                    return (LINK_CALLSS_FP<<8)|(((operand2>>8)&0xFF)<<16);
                  case 1: // VMGM menu
                    return ((long)LINK_CALLSS_VMGM_MENU<<8)|
                        ((long)(operand2&0xF)<<16)|(((long)(operand2>>8)&0xFF)<<32);
                  case 2: // VTSM
                    return ((long)LINK_CALLSS_VTSM<<8)|((long)(operand2&0xF)<<16)|
                        ((long)((operand2>>8)&0xFFL)<<32);
                  case 3: // VMGM pgcn
                    return ((long)LINK_CALLSS_VMGM_PGCN<<8)|((long)(operand1&0x7FFF)<<16)|
                        ((long)((operand2>>8)&0xFFL)<<32); // TODO: how big is operand1?
                }
            }
          }
        }
        break;
      case 2: // SPRM Modifiers
        if(compareOpcode(cmp, getReg((operand3>>8)&0xFF),
            getReg(operand3&0xFF)))
        {
          switch(set)
          {
            case 1: // SetSTN
              if((operand1&0x80)!=0)
              {
                SPRM[ASTN] = (direct==1) ? operand1&0x7F : GPRM[operand1&0xF];
              }
              if((operand2&0x8000)!=0)
              {
                SPRM[SPSTN] = (direct==1) ? (operand2>>8)&0x7F : GPRM[(operand2>>8)&0xF];
              }
              if((operand2&0x80)!=0)
              {
                SPRM[AGLN] = (direct==1) ? operand2&0x7F : GPRM[operand2&0xF];
              }
              break;
            case 2: // SetNVTMR
              SPRM[NVTMR] = (direct==1) ? operand1 : GPRM[operand1&0xF];
              SPRM[NV_PGCN] = operand2;
              break;
            case 3: // SetGPRMMD
              if((operand2&0x80)!=0) // Timer mode
              {
                GPRMtimer|=(1<<(operand2&0xF));
              }
              else
              {
                GPRMtimer&=~(1<<(operand2&0xF));
              }
              setGPRM((operand2&0xF), (direct==1) ? operand1 : getReg(operand1&0xFF));
              break;
            case 4: // SetAMXMD
              // Ignore Karaoke for now...
              break;
            case 6: // SetHL_BTNN
              SPRM[HL_BTNN] = (direct==1) ? operand2 : GPRM[operand2&0xF];
              break;
          }
          // Link should not be possible if cmp!=0
          if(cmp==0)
          {
            switch(cmd)
            {
              case 0: // NOP
                break;
              case 1: // Link
                return ((operand3&0x1F)<<8)|(operand3<<16); // Those maps to the value directly...
              case 4: // LinkPGCN
                return (LINK_PGCN<<8)|(operand3<<16);
              case 5: // LinkPTTN
                return (LINK_PTTN<<8)|(operand3<<16);
              case 6: // LinkPGN
                return (LINK_PGN<<8)|(operand3<<16);
              case 7: // LinkCN
                return (LINK_CN<<8)|(operand3<<16);
            }
          }
        }
        break;
      case 3: // GPRM Modifiers
        if(compareOpcode(cmp, getReg((operand1>>8)&0xFF),
            dircmp==1 ? operand3 : getReg(operand3&0xFF)))
        {
          setOpcode(set, operand1&0xF, operand2, direct==1);
          if(cmp==0) // Can link if no compare
          {
            switch(cmd)
            {
              case 0: // NOP
                break;
              case 1: // Link
                return ((operand3&0x1F)<<8)|(operand3<<16); // Those maps to the value directly...
              case 4: // LinkPGCN
                return (LINK_PGCN<<8)|(operand3<<16);
              case 5: // LinkPTTN
                return (LINK_PTTN<<8)|(operand3<<16);
              case 6: // LinkPGN
                return (LINK_PGN<<8)|(operand3<<16);
              case 7: // LinkCN
                return (LINK_CN<<8)|(operand3<<16);
            }
          }
        }
        break;
      case 4: // Set then cond link
        setOpcode(set, cmd, operand1, direct==1);
        if(compareOpcode(cmp, getReg(cmd),
            dircmp==1 ? operand2 : getReg(operand2&0xFF)))
        {
          return ((operand3&0x1F)<<8)|(operand3<<16); // Those maps to the value directly...
        }
        break;
      case 5: // Cond set then cond link
        if(direct==0)
        {
          if(compareOpcode(cmp, getReg(operand1&0xFF),
              dircmp==1 ? operand2 : getReg(operand2&0xFF)))
          {
            setOpcode(set, cmd, (operand1>>8)&0xFF, direct==1);
            return ((operand3&0x1F)<<8)|(operand3<<16); // Those maps to the value directly...
          }
        }
        else
        {
          if(compareOpcode(cmp, getReg((operand2>>8)&0xFF),
              getReg(operand2&0xFF)))
          {
            setOpcode(set, cmd, operand1, direct==1);
            return ((operand3&0x1F)<<8)|(operand3<<16); // Those maps to the value directly...
          }
        }
        break;
      case 6: // Cond set then link
        if(direct==0)
        {
          if(compareOpcode(cmp, getReg(operand1&0xFF),
              dircmp==1 ? operand2 : getReg(operand2&0xFF)))
          {
            setOpcode(set, cmd, (operand1>>8)&0xFF, direct==1);
          }
          return ((operand3&0x1F)<<8)|(operand3<<16); // Those maps to the value directly...
        }
        else
        {
          if(compareOpcode(cmp, getReg((operand2>>8)&0xFF),
              getReg(operand2&0xFF)))
          {
            setOpcode(set, cmd, operand1, direct==1);
          }
          return ((operand3&0x1F)<<8)|(operand3<<16); // Those maps to the value directly...
        }
        // break;
      case 7: // Invalid
        break;
    }
    return 0;
  }

  void next_cell()
  {
    if(debugSTATE) System.out.println("going to next cell\n");
    process_command(play_Cell_post());
  }


  boolean jump_title(int title)
  {
    set_TT(title);
    process_command(play_PGC());
    return true;
  }

  boolean jump_pg(int pg)
  {
    program = pg;
    process_command(play_PG());
    return true;
  }

  boolean jump_cell_block(int cell, int block)
  {
    this.cell = cell;
    process_command(play_Cell());
    if(this.cell == cell)
      this.block = block;
    return true;
  }

  boolean jump_title_part(int title, int part)
  {
    long link;

    if(!set_PTT(title, part))
      return false;
    process_command(play_PGC_PG(program));
    return true;
  }

  boolean jump_top_pg()
  {
    process_command(play_PG());
    return true;
  }

  boolean jump_next_pg()
  {
    if(program >= pgc.nr_of_programs.get())
    {
      process_command(play_PGC_post());
      return true;
    }
    else
    {
      return jump_pg(program+1);
    }
  }

  boolean jump_prev_pg()
  {
    if(program <= 1)
    {
      if(pgc.prev_pgc_nr.get()!=0 && set_PGCN(pgc.prev_pgc_nr.get()))
      {
        process_command(play_PGC());
        return jump_pg(pgc.nr_of_programs.get());
      }
      return false;
    }
    else
    {
      return jump_pg(program - 1);
    }
  }

  boolean jump_up()
  {
    if(pgc.goup_pgc_nr.get()!=0 && set_PGCN(pgc.goup_pgc_nr.get()))
    {
      process_command(play_PGC());
      return true;
    }
    return false;
  }

  boolean jump_menu(int menuid)
  {
    int old_domain = domain;

    switch (domain)
    {
      case VTS_DOMAIN:
        set_RSMinfo(0, block);
      case VTSM_DOMAIN:
      case VMGM_DOMAIN:
        switch(menuid)
        {
          case DVD_MENU_Title:
          case DVD_MENU_Escape:
            domain = VMGM_DOMAIN;
            break;
          case DVD_MENU_Root:
          case DVD_MENU_Subpicture:
          case DVD_MENU_Audio:
          case DVD_MENU_Angle:
          case DVD_MENU_Part:
            domain = VTSM_DOMAIN;
            break;
        }
        if(get_PGCIT()!=null && set_MENU(menuid))
        {
          process_command(play_PGC());
          return true;
        }
        else
        {
          domain = old_domain;
        }
        break;
      case FP_DOMAIN:
        break;
    }

    return false;
  }

  boolean jump_resume()
  {
    long link = (LINK_RSM<<8);

    if(resume_vts<1)
      return false;
    if(!process_command(link))
      return false;
    return true;
  }

  boolean exec_cmd(vm_cmd_t[] cmds)
  {
    long link;
    // TODO: finish that
    link = executeCommand(cmds, 1, 1);
    if((link&0xFF00)!=0)
    {
      return process_command(link);
    }
    else
    {
      return false;
    }
  }


  /* getting information */

  int get_current_menu()
  {
    pgcit_t pgcit;
    int pgcn;
    pgcn = SPRM[TTN];
    pgcit = get_PGCIT();
    return pgcit.pgci_srp[pgcn - 1].entry_id.get() & 0xf ;
  }

  int get_current_title_part()
  {
    vts_ptt_srpt_t vts_ptt_srpt;
    int title, part = 0, vts_ttn;
    boolean found;
    int pgcN, pgN;
    int title_result, part_result;

    vts_ptt_srpt = vtsifo.vts_ptt_srpt;
    pgcN = get_PGCN();
    pgN = program;

    found = false;
    for(vts_ttn = 0; (vts_ttn < vts_ptt_srpt.nr_of_srpts.get()) && !found; vts_ttn++)
    {
      for(part = 0; (part < vts_ptt_srpt.title[vts_ttn].ptt.length) && !found; part++)
      {
        if(vts_ptt_srpt.title[vts_ttn].ptt[part].pgcn.get() == pgcN)
        {
          if(vts_ptt_srpt.title[vts_ttn].ptt[part].pgn.get()  == pgN)
          {
            found = true;
            break;
          }
          if(part > 0 && vts_ptt_srpt.title[vts_ttn].ptt[part].pgn.get() > pgN &&
              vts_ptt_srpt.title[vts_ttn].ptt[part - 1].pgn.get() < pgN)
          {
            part--;
            found = true;
            break;
          }
        }
      }
      if(found)
        break;
    }
    vts_ttn++;
    part++;

    if(!found)
    {
      if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("Could not find chapter");
      return 0;
    }

    title = get_TT(vts, vts_ttn);

    title_result = title;
    part_result = part;
    return (title_result<<16)|part_result;
  }

  int get_audio_stream(int audioN)
  {
    int streamN = -1;

    if(domain != VTS_DOMAIN)
      audioN = 0;

    if(audioN < 8)
    {
      if((pgc.audio_control[audioN].get() & (1<<15))!=0)
      {
        streamN = (pgc.audio_control[audioN].get() >> 8) & 0x07;
      }
    }

    if(domain != VTS_DOMAIN && streamN == -1)
      streamN = 0;

    return streamN;
  }

  int get_subp_stream(int subpN, int mode)
  {
    int streamN = -1;
    int source_aspect = get_video_aspect();

    if(domain != VTS_DOMAIN)
      subpN = 0;

    if(subpN < 32)
    {
      if((pgc.subp_control[subpN].get() & (1<<31))!=0)
      {
        if(source_aspect == 0)
          streamN = ((int)pgc.subp_control[subpN].get() >> 24) & 0x1f;
        if(source_aspect == 3)
        {
          switch (mode)
          {
            case 0:
              streamN = ((int)pgc.subp_control[subpN].get() >> 16) & 0x1f;
              break;
            case 1:
              streamN = ((int)pgc.subp_control[subpN].get() >> 8) & 0x1f;
              break;
            case 2:
              streamN = (int)pgc.subp_control[subpN].get() & 0x1f;
          }
        }
      }
    }

    if(domain != VTS_DOMAIN && streamN == -1)
      streamN = 0;

    return streamN;
  }

  int get_audio_active_stream()
  {
    int audioN;
    int streamN;
    audioN = SPRM[ASTN];
    streamN = get_audio_stream(audioN);

    if(streamN == -1)
    {
      for(audioN = 0; audioN < 8; audioN++)
      {
        if((pgc.audio_control[audioN].get() & (1<<15))!=0)
        {
          if((streamN = get_audio_stream(audioN)) >= 0)
          {
            SPRM[ASTN]=audioN;
            break;
          }
        }
      }
      if(streamN==-1)
      {
        if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("couldn't find stream for audio, sending back 0");
        streamN=0;
      }
    }

    return streamN;
  }

  int get_subp_active_stream(int mode)
  {
    int subpN;
    int streamN;
    subpN = SPRM[SPSTN] & ~0x40;

    if(subpN==62) subpN=0;

    streamN = get_subp_stream(subpN, mode);

    if(streamN == -1)
    {
      for(subpN = 0; subpN < 32; subpN++)
      {
        if((pgc.subp_control[subpN].get() & (1<<31))!=0)
        {
          if((streamN = get_subp_stream(subpN, mode)) >= 0)
            break;
        }
      }
      if(streamN==-1)
      {
        if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("couldn't find stream for subpicture, sending back 3E");
        streamN=0x3E;
      }
    }

    if(domain == VTS_DOMAIN && ((SPRM[SPSTN] & 0x40)==0))
      return (streamN | 0x80);
    else
      return streamN;
  }

  int get_angle_info()
  {
    int num_avail = 1;
    int current = 1;

    if(domain == VTS_DOMAIN)
    {
      title_info_t title;

      if(SPRM[TTN] > vmgifo.tt_srpt.nr_of_srpts.get())
        return (num_avail<<16)|current;
      title = vmgifo.tt_srpt.title[SPRM[TTN] - 1];
      if(title.title_set_nr.get() != vts ||
          title.vts_ttn.get() != SPRM[VTS_TTN])
        return (num_avail<<16)|current;
      num_avail = title.nr_of_angles.get();
      current = SPRM[AGLN];
    }
    return (num_avail<<16)|current;
  }

  int get_video_aspect()
  {
    int aspect = get_video_attr().display_aspect_ratio.get();

    SPRM[14] &= ~(0x3 << 10);
    SPRM[14] |= aspect << 10;

    return aspect;
  }

  public int get_video_scale_permission()
  {
    return get_video_attr().permitted_df.get();
  }

  video_attr_t get_video_attr()
  {
    switch (domain)
    {
      case VTS_DOMAIN:
        return vtsifo.vtsi_mat.vts_video_attr;
      case VTSM_DOMAIN:
        return vtsifo.vtsi_mat.vtsm_video_attr;
      case VMGM_DOMAIN:
      case FP_DOMAIN:
        return vmgifo.vmgi_mat.vmgm_video_attr;
      default:
        return null;
    }
  }

  audio_attr_t get_audio_attr(int streamN)
  {
    switch (domain)
    {
      case VTS_DOMAIN:
        return vtsifo.vtsi_mat.vts_audio_attr[streamN];
      case VTSM_DOMAIN:
        return vtsifo.vtsi_mat.vtsm_audio_attr;
      case VMGM_DOMAIN:
      case FP_DOMAIN:
        return vmgifo.vmgi_mat.vmgm_audio_attr;
      default:
        return null;
    }
  }

  subp_attr_t get_subp_attr(int streamN)
  {
    switch(domain)
    {
      case VTS_DOMAIN:
        return vtsifo.vtsi_mat.vts_subp_attr[streamN];
      case VTSM_DOMAIN:
        return vtsifo.vtsi_mat.vtsm_subp_attr;
      case VMGM_DOMAIN:
      case FP_DOMAIN:
        return vmgifo.vmgi_mat.vmgm_subp_attr;
      default:
        return null;
    }
  }

  private long play_PGC()
  {
    long link;
    program = 1;
    cell = 0;
    block = 0;

    if(debugVM) System.out.println("DVDVM: Entering PGC");
    if(pgc.command_tbl!=null && pgc.command_tbl.nr_of_pre.get()!=0)
    {
      link = executeCommand(pgc.command_tbl.pre_cmd, 1,
          pgc.command_tbl.nr_of_pre.get());

      if((link&0xFF00)!=0)
      {
        return link;
      }
      else
      {
        // Warning?
      }
    }
    return play_PG();
  }

  private long play_PGC_PG(int pgN)
  {
    long link;

    program = pgN;
    cell = 0;
    block = 0;

    if(debugVM) System.out.println("DVDVM: Entering PGC program "+pgN);
    if(pgc.command_tbl!=null && pgc.command_tbl.nr_of_pre.get()!=0)
    {
      link = executeCommand(pgc.command_tbl.pre_cmd, 1,
          pgc.command_tbl.nr_of_pre.get());

      if((link&0xFF00)!=0)
      {
        return link;
      }
      else
      {
        // Warning?
      }
    }
    return play_PG();
  }

  private long play_PGC_post()
  {
    long link;

    if(debugVM) System.out.println("DVDVM: Doing PGC post commands");
    if(pgc.command_tbl!=null && pgc.command_tbl.nr_of_post.get()!=0)
    {
      link = executeCommand(pgc.command_tbl.post_cmd, 1,
          pgc.command_tbl.nr_of_post.get());

      if((link&0xFF00)!=0)
      {
        return link;
      }
      else
      {
        // Warning?
      }
    }
    if(debugVM) System.out.println("DVDVM: no link at PGC post, going to next PGC");

    if(!set_PGCN(pgc.next_pgc_nr.get()))
    {
      // Special fix for the dvd Coursier, exit wasn't much more useful anyway
      link=
          ((long)LINK_JUMPSS_VTSM<<8)|
          (long)(1<<16)|
          (long)(1<<32)|
          (long)(1<<48);
      //link = LINK_EXIT<<8;
      return link;
    }
    return play_PGC();
  }

  private long play_PG()
  {
    if(debugVM) System.out.println("DVDVM: Entering program "+program);
    if(program > pgc.nr_of_programs.get())
    {
      if(debugVM) System.out.println("DVDVM: Reached end of programs (+"+program+"/"+pgc.nr_of_programs.get()+")");
      return play_PGC_post();
    }

    cell = pgc.program_map[program - 1].ele.get();
    return play_Cell();
  }

  private long play_Cell()
  {
    long link = LINK_CONTINUE<<8;
    if(debugVM) System.out.println("DVDVM: Entering cell"+cell);

    if(cell > pgc.nr_of_cells.get())
    {
      if(debugVM) System.out.println("DVDVM: Reached end of number of cells ("+pgc.nr_of_cells.get()+")");
      return play_PGC_post();
    }

    if(debugVM) System.out.println("DVDVM: cell mode "+
        pgc.cell_playback[cell - 1].block_mode.get() +
        " cell type"+
        pgc.cell_playback[cell - 1].block_type.get());
    switch(pgc.cell_playback[cell - 1].block_mode.get())
    {
      case 0:
        break;
      case 1:
        switch(pgc.cell_playback[cell - 1].block_type.get())
        {
          case 0:
          case 2:
          case 3:
            System.out.println("DVDVM: invalid type for cell in mode 1");
            break;
          case 1:
            cell += SPRM[AGLN] - 1;
            cellangle=true;
            break;
        }
        break;
      case 2:
      case 3:
      default:
        if(debugVM) System.out.println("DVDVM: Entering at a non starting cell? " + cell);
        cellangle=true;
    }

    if(!set_PGN())
    {
      if(debugVM) System.out.println("DVDVM: could not set program in cell enter?");
      return play_PGC_post();
    }
    cell_restart++;
    block = 0;
    return link;
  }

  private long play_Cell_post()
  {
    cell_playback_t pbcell;
    if(debugVM) System.out.println("DVDVM: Doing cell "+cell+" post");

    pbcell = pgc.cell_playback[cell - 1];

    if(pbcell.cell_cmd_nr.get() != 0)
    {
      long link;
      if (pgc.command_tbl != null &&
          pgc.command_tbl.nr_of_cell.get() >= pbcell.cell_cmd_nr.get())
      {
        if(debugVM) System.out.println("DVDVM: executing cell post command");
        link = executeCommand(pgc.command_tbl.cell_cmd, pbcell.cell_cmd_nr.get(), 1);
        if((link&0xFF00)!=0)
        {
          return link;
        }
        else
        {
          if(debugVM) System.out.println("DVDVM: no link at cell post, going to next cell");
        }
      }
    }

    switch(pgc.cell_playback[cell - 1].block_mode.get())
    {
      case 0:
        cell++;
        break;
      case 1: /*  The first cell in the block */
      case 2: /*  A cell in the block */
      case 3: /*  The last cell in the block */
      default:
        switch(pgc.cell_playback[cell - 1].block_type.get())
        {
          case 0:
            if(debugVM) System.out.println("DVDVM: invalid type of cell playback");
            break;
          case 1:
            cell++;
            while(cell <= pgc.nr_of_cells.get() &&
                pgc.cell_playback[cell - 1].block_mode.get() >= 2)
            {
              cell++;
            }
            break;
          case 2:
          case 3:
          default:
            if(debugVM) System.out.println("DVDVM: invalid type of cell playback");
        }
        break;
    }

    if(!set_PGN())
    {
      if(debugVM) System.out.println("DVDVM: reached last cell of pgc");
      return play_PGC_post();
    }
    return play_Cell();
  }

  /* Process link - returns 1 if a flush will be needed */
  private boolean process_command(long link)
  {
    int operand1,operand2,operand3;
    operand1=0;

    while(((link>>8)&0xFF)!=LINK_CONTINUE)
    {
      operand1=((int)(link>>16L)&0xFFFF);
      operand2=((int)(link>>32L)&0xFFFF);
      operand3=((int)(link>>48L)&0xFFFF);
      System.out.println("Using link ("+link+") type "+ ((link>>8)&0xFF) +" with "+operand1+" "+operand2+" "+operand3);
      switch((int)((link>>8)&0xFF))
      {
        case LINK_NONE:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          return false;
          //break;
        case LINK_TOPCELL:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          link = play_Cell();
          break;
        case LINK_NEXTCELL:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          cell+=1;
          link = play_Cell();
          break;
        case LINK_PREVCELL:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          cell-=1;
          link = play_Cell();
          break;
        case LINK_TOPPG:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          link = play_PG();
          break;
        case LINK_NEXTPG:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          program+=1;
          link = play_PG();
          break;
        case LINK_PREVPG:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          program-=1;
          link = play_PG();
          break;
        case LINK_TOPPGC:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          link = play_PGC();
          break;
        case LINK_NEXTPGC:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          set_PGCN(pgc.next_pgc_nr.get());
          link = play_PGC();
          break;
        case LINK_PREVPGC:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          set_PGCN(pgc.prev_pgc_nr.get());
          link = play_PGC();
          break;
        case LINK_GOUPPGC:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          set_PGCN(pgc.goup_pgc_nr.get());
          link = play_PGC();
          break;
        case LINK_TAILPGC:
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN]=operand1&0xFC00;
          }
          link = play_PGC_post();
          break;
        case LINK_RSM:
          if(resume_vts == -1)
          {
            link=LINK_EXIT<<8;
            break;
          }

          domain = VTS_DOMAIN;
          vts=resume_vts;

          if(vtsfile!=null)
          {
            vtsfile.close();
            vtsfile=null;
          }

          vtsfile = reader.openFile(vts, 0);
          if(vtsfile!=null)
          {
            System.out.println("Trying to parse vts ifo\n");
            vtsifo = new IFO(vtsfile);
          }

          set_PGCN(resume_pgc);

          SPRM[4] = resume_SPRM[4];
          SPRM[5] = resume_SPRM[5];
          SPRM[6] = resume_SPRM[6];
          SPRM[7] = resume_SPRM[7];
          SPRM[8] = resume_SPRM[8];

          if((operand1&0xFC00)!=0)
            SPRM[HL_BTNN]=operand1&0xFC00;

          cell = resume_cell;

          link = (LINK_CONTINUE<<8) | (resume_block<<16);
          set_PGN();
          break;
          // Added types for indirectly specified links/calls with params comments
        case LINK_PGCN: // pgcn
          set_PGCN(operand1);
          link = play_PGC();
          break;
        case LINK_PTTN: // hl_bn, pttn
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN] = operand1&0xFC00;
          }
          set_VTS_PTT(vts, SPRM[VTS_TTN], operand1&0x3FF);
          link = play_PG();
          break;
        case LINK_PGN: // hl_bn, pgn
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN] = operand1&0xFC00;
          }
          program = operand1&0x7F;
          link = play_PG();
          break;
        case LINK_CN: // hl_bn, cn
          if((operand1>>10)!=0) // Need to update highlighted button
          {
            SPRM[HL_BTNN] = operand1&0xFC00;
          }
          cell = operand1&0xFF;
          link = play_Cell();
          break;
        case LINK_EXIT:
          running=-1;
          return false;
        case LINK_JUMPTT: // ttn
          set_TT(operand1);
          link = play_PGC();
          break;
        case LINK_JUMPVTS_TT: // ttn
          set_VTS_TT(vts, operand1);
          link = play_PGC();
          break;
        case LINK_JUMPVTS_PTT: // ttn pttn
          set_VTS_PTT(vts, operand1, operand2);
          link = play_PGC_PG(program);
          break;
        case LINK_JUMPSS_FP:
          set_FP_PGC();
          link = play_PGC();
          break;
        case LINK_JUMPSS_VMGM_MENU: // menu
          domain = VMGM_DOMAIN;
          set_MENU(operand1);
          link = play_PGC();
          break;
        case LINK_JUMPSS_VTSM: // vts, ttn, menu
          // If operand1 is 0, keep current vts
          if(operand1!=0 && operand1!=vts) // Need to load new vts
          {
            //TODO: verify that
            vts=operand1;
            if(vtsfile!=null)
            {
              vtsfile.close();
              vtsfile=null;
            }
            vtsfile = reader.openFile(vts, 0);
            if(vtsfile!=null)
            {
              System.out.println("Trying to parse vts ifo\n");
              vtsifo = new IFO(vtsfile);
            }
          }
          domain = VTSM_DOMAIN;
          SPRM[VTS_TTN] = operand2;
          SPRM[TTN] = get_TT(vts, SPRM[VTS_TTN]);
          set_MENU(operand3);
          link = play_PGC();
          break;
        case LINK_JUMPSS_VMGM_PGCN: // pgcn
          domain = VMGM_DOMAIN;
          set_PGCN(operand1);
          link = play_PGC();
          break;
        case LINK_CALLSS_FP:
          set_RSMinfo(operand1, 0);
          set_FP_PGC();
          link = play_PGC();
          break;
        case LINK_CALLSS_VMGM_MENU: // menu
          set_RSMinfo(operand2, 0);
          domain = VMGM_DOMAIN;
          set_MENU(operand1);
          link = play_PGC();
          break;
        case LINK_CALLSS_VTSM: // menu
          set_RSMinfo(operand2, 0);
          domain = VTSM_DOMAIN;
          set_MENU(operand1);
          link = play_PGC();
          break;
        case LINK_CALLSS_VMGM_PGCN: // pgcn
          set_RSMinfo(operand2, 0);
          domain = VMGM_DOMAIN;
          set_PGCN(operand1);
          link = play_PGC();
          break;
        default:
          System.out.println("Unknown link type: "+link);
          break;
      }
    }
    operand1=((int)(link>>16)&0xFFFF);
    block = operand1;
    System.out.println("Block after link is "+block);
    return true;
  }

  /* Set */
  private boolean set_TT(int tt)
  {
    set_PTT(tt, 1);
    return true;
  }

  private boolean set_PTT(int tt, int ptt)
  {
    return set_VTS_PTT(vmgifo.tt_srpt.title[tt - 1].title_set_nr.get(),
        vmgifo.tt_srpt.title[tt - 1].vts_ttn.get(), ptt);
  }

  private boolean set_VTS_TT(int vts, int vts_ttn)
  {
    return set_VTS_PTT(vts, vts_ttn, 1);
  }

  private boolean set_VTS_PTT(int vts, int vts_ttn, int part)
  {
    domain = VTS_DOMAIN;
    if(this.vts != vts)
    {
      //TODO: verify that
      this.vts=vts;
      if(vtsfile!=null)
      {
        vtsfile.close();
        vtsfile=null;
      }
      vtsfile = reader.openFile(vts, 0);
      if(vtsfile!=null)
      {
        System.out.println("Trying to parse vts ifo\n");
        vtsifo = new IFO(vtsfile);
      }
    }
    int tmpprogram = vtsifo.vts_ptt_srpt.title[vts_ttn - 1].ptt[part - 1].pgn.get();

    SPRM[TT_PGCN] = vtsifo.vts_ptt_srpt.title[vts_ttn - 1].ptt[part - 1].pgcn.get();
    SPRM[PTTN] = part;
    SPRM[TTN] = get_TT(vts, vts_ttn);
    SPRM[VTS_TTN] = vts_ttn;
    set_PGCN(SPRM[TT_PGCN]);
    program = tmpprogram;
    return true;
  }

  private boolean set_FP_PGC()
  {
    domain = FP_DOMAIN;
    pgc = vmgifo.first_play_pgc;
    return true;
  }

  private boolean set_MENU(int menu)
  {
    return set_PGCN(get_ID(menu));
  }

  private boolean set_PGCN(int pgcN)
  {
    pgcit_t pgcit;

    if(debugVM) System.out.println("set PGCN "+pgcN);
    if(pgcN<1) return false;
    pgcit = get_PGCIT();
    pgc = pgcit.pgci_srp[pgcN - 1].pgc;
    program = 1;

    if(domain == VTS_DOMAIN)
      SPRM[TT_PGCN] = pgcN;

    return true;
  }

  private boolean set_PGN()
  {
    int new_pgN = 0;

    while(new_pgN < pgc.nr_of_programs.get()
        && cell >= pgc.program_map[new_pgN].ele.get())
      new_pgN++;

    if(new_pgN == pgc.nr_of_programs.get()) /* We are at the last program */
      if(cell > pgc.nr_of_cells.get())
        return false; /* We are past the last cell */

    program = new_pgN;

    if(domain == VTS_DOMAIN)
    {
      playback_type_t pb_ty;
      if(SPRM[TTN] > vmgifo.tt_srpt.nr_of_srpts.get())
        return false; /* ?? */
      pb_ty = vmgifo.tt_srpt.title[SPRM[TTN] - 1].pb_ty;
      if(pb_ty.multi_or_random_pgc_title.get() == /* One_Sequential_PGC_Title */ 0)
      {
        int part = get_current_title_part()&0xFFFF;
        SPRM[PTTN] = part;
      }
      else
      {
        // System.out.println("Unsupported playback_type random mode");
        program = (int) (java.lang.Math.random()*(double)(pgc.nr_of_programs.get()))+1;
        System.out.println("playback_type random mode to "+program);
        int part = get_current_title_part()&0xFFFF;
        SPRM[PTTN] = part;
      }
    }
    return true;
  }

  private void set_RSMinfo(int cell, int block)
  {
    if(cell!=0)
    {
      resume_cell = cell;
      resume_block = block;
    }
    else
    {
      resume_cell = this.cell;
      resume_block = block;
    }
    resume_vts = vts;
    resume_pgc = get_PGCN();

    for(int i=0;i<24;i++)
    {
      resume_SPRM[i]=SPRM[i];
    }
    return;
  }

  /* Get */
  private int get_TT(int vts, int vts_ttn)
  {
    for(int i = 1; i <= vmgifo.tt_srpt.nr_of_srpts.get(); i++)
    {
      if( vmgifo.tt_srpt.title[i - 1].title_set_nr.get() == vts &&
          vmgifo.tt_srpt.title[i - 1].vts_ttn.get() == vts_ttn)
      {
        return i;
      }
    }
    return 0;
  }

  private int get_ID(int id)
  {
    int pgcN;
    pgcit_t pgcit;

    pgcit = get_PGCIT();
    id |=0x80;
    System.out.println("trying to find id "+id);

    for(int i=0;i<pgcit.nr_of_pgci_srp.get();i++)
    {
      System.out.println(pgcit.pgci_srp[i].entry_id.get());
      if((pgcit.pgci_srp[i].entry_id.get()) == id)
      {
        pgcN = i + 1;
        return pgcN;
      }
    }
    return 1;
  }

  private int get_PGCN()
  {
    pgcit_t pgcit;
    int pgcN = 1;

    pgcit = get_PGCIT();

    if(pgcit!=null)
    {
      while(pgcN <= pgcit.nr_of_pgci_srp.get())
      {
        if(pgcit.pgci_srp[pgcN - 1].pgc == pgc)
        {
          return pgcN;
        }
        pgcN++;
      }
    }
    System.out.println("Unable to find pgc number");
    return 0;
  }

  private pgcit_t get_MENU_PGCIT(IFO h, int lang)
  {
    int i;

    if(h == null || h.pgci_ut == null)
    {
      return null;
    }

    i = 0;
    while(i < h.pgci_ut.nr_of_lus.get()
        && h.pgci_ut.lu[i].lang_code.get() != lang)
      i++;

    if(i == h.pgci_ut.nr_of_lus.get())
    {
      // Not found give first one...
      i = 0;
    }

    return h.pgci_ut.lu[i].pgcit;
  }

  private pgcit_t get_PGCIT()
  {
    pgcit_t pgcit;

    switch(domain)
    {
      case VTS_DOMAIN:
        pgcit = vtsifo.vts_pgcit;
        break;
      case VTSM_DOMAIN:
        pgcit = get_MENU_PGCIT(vtsifo, SPRM[0]);
        break;
      case VMGM_DOMAIN:
      case FP_DOMAIN:
        pgcit = get_MENU_PGCIT(vmgifo, SPRM[0]);
        break;
      default:
        return null;
    }
    return pgcit;
  }


  public boolean parseNav(byte buf[], int offset, pci_t pci, dsi_t dsi)
  {
    boolean foundpci=false;
    boolean founddsi=false;
    int pcioffset=0;
    int dsioffset=0;
    int b;
    int usedpos=0;
    int pos=0;
    int cur=0xFFFFFFFF;
    while(pos<2048)
    {
      b=buf[offset+pos]&0xFF;
      pos+=1;
      cur<<=8;
      cur|=b;
      if((cur&0xFFFFFF00)==0x00000100)
      {
        if(debugMPEG) System.out.println("block of type "+b);
        /* video or audio */
        if((b==0xE0)||(b==0xC0)||(b==0xBD))
        {
          /* verify we have complete packet */
          if(((pos+2)<=2048)&&
              (pos+((buf[offset+pos]&0xFF)<<8)+((buf[offset+pos+1]&0xFF)+2)<=2048))
          {
            usedpos=pos+((buf[offset+pos]&0xFF)<<8)+(buf[offset+pos+1]&0xFF)+2;
            pos+=((buf[offset+pos]&0xFF)<<8)+(buf[offset+pos+1]&0xFF)+2;
          }
          else
          {
            break;
          }
        }
        else if((b==0xBA))
        {
          /* verify we have complete packet */
          if((pos+10)<=2048)
          {
            usedpos=pos+10;
            pos+=10;
          }
          else
          {
            break;
          }
        }
        else if((b==0xBB)||(b==0xBC)|(b==0xBE)|(b==0xBF))
        {
          /* verify we have complete packet */
          if(((pos+2)<=2048) &&
              ((pos+((buf[offset+pos]&0xFF)<<8)+(buf[offset+pos+1]&0xFF)+2)<=2048))
          {
            if(b==0xBF && buf[offset+pos+2]==0x00) // PCI
            {
              foundpci=true;
              pcioffset=pos+3;
            }

            if(b==0xBF && buf[offset+pos+2]==0x01) // DSI
            {
              founddsi=true;
              dsioffset=pos+3;
            }
            usedpos=pos+((buf[offset+pos]&0xFF)<<8)+(buf[offset+pos+1]&0xFF)+2;
            pos+=((buf[offset+pos]&0xFF)<<8)+(buf[offset+pos+1]&0xFF)+2;
          }
          else
          {
            break;
          }
        }

      }
    }

    if(foundpci && founddsi)
    {

      byte[] navbuffer= new byte[2048];
      System.arraycopy(buf, offset, navbuffer, 0, 2048);

      // We should probably do a copy instead??
      ByteBuffer navBuffer = ByteBuffer.wrap(navbuffer);
      if(debugMPEG) System.out.println("Found pci and found dsi in packet");
      pci.setByteBuffer(navBuffer, pcioffset);
      dsi.setByteBuffer(navBuffer, dsioffset);
      return true;
    }
    return false;
  }


  private void putInt(byte buf[], int offset, int value)
  {
    buf[offset+0]=(byte) ((value>>24)&0xFF);
    buf[offset+1]=(byte) ((value>>16)&0xFF);
    buf[offset+2]=(byte) ((value>>8)&0xFF);
    buf[offset+3]=(byte) ((value>>0)&0xFF);
  }

  private void putLong(byte buf[], int offset, long value)
  {
    buf[offset+0]=(byte) ((value>>56)&0xFF);
    buf[offset+1]=(byte) ((value>>48)&0xFF);
    buf[offset+2]=(byte) ((value>>40)&0xFF);
    buf[offset+3]=(byte) ((value>>32)&0xFF);
    buf[offset+4]=(byte) ((value>>24)&0xFF);
    buf[offset+5]=(byte) ((value>>16)&0xFF);
    buf[offset+6]=(byte) ((value>>8)&0xFF);
    buf[offset+7]=(byte) ((value>>0)&0xFF);
  }

  public synchronized int process(byte buf[], int offset)
  {
    int event=-1;

    if(running==0) // Not started yet
    {
      set_FP_PGC();
      process_command(play_PGC());
      running=1;
    }
    else if(running==-1) // Stopped
    {
      return event;
    }

    if(cell == pgc.nr_of_cells.get()) // Reached last cell of pgc
      in_still += pgc.still_time.get();

    if(need_flush)
    {
      need_flush=false;
      player_vobu_sector = player_cell_sector + block;
      player_vobu_offset = 0;
      player_vobu_skip = 0;
      player_vobu_length_sector = 0;
      player_next_vobu_sector = 0;
      // TODO: handle angles...
      return (sage.DVDReader.DVD_PROCESS_FLUSH<<16);
    }

    if(player_button != (SPRM[HL_BTNN]>>10))
    {
      player_button = SPRM[HL_BTNN]>>10;
    System.out.println("Sending highlight for button "+player_button);
    if(player_button>0 && player_button<=player_pci.hli.hl_gi.btn_ns.get() &&
        player_pci.hli.btnit[player_button-1].auto_action_mode.get()!=0)
    {
      System.out.println("Auto activate detected on button "+player_button);
      activate_button(player_pci);
      player_button = SPRM[HL_BTNN]>>10;
    System.out.println("Sending highlight for button "+player_button);
    }
    return (sage.DVDReader.DVD_PROCESS_HIGHLIGHT<<16)|player_button;
    }

    if(need_empty)
    {
      if(done_empty)
      {
        need_empty=false;
      }
      else
      {
        return (sage.DVDReader.DVD_PROCESS_EMPTY<<16);
      }
    }

    if(player_vts!=vts ||
        player_domain!=domain)
    {
      switch(domain)
      {
        case FP_DOMAIN:
        case VMGM_DOMAIN:
          if(titlefile!=null)
          {
            titlefile.close();
            titlefile=null;
          }
          titlefile = reader.openFile(0, DVDReader.DVD_TYPE_MENU);
          break;
        case VTSM_DOMAIN:
          if(titlefile!=null)
          {
            titlefile.close();
            titlefile=null;
          }
          titlefile = reader.openFile(vts, DVDReader.DVD_TYPE_MENU);
          break;
        case VTS_DOMAIN:
          if(titlefile!=null)
          {
            titlefile.close();
            titlefile=null;
          }
          titlefile = reader.openFile(vts, DVDReader.DVD_TYPE_TITLE);
          break;
      }
      player_vts=vts;
      player_domain=domain;

      new_palette = true;
      player_cell = -1;
      player_spu = -1;
      player_audio = -1;
      cellangle = false;
      return (sage.DVDReader.DVD_PROCESS_VTS<<16)|(vts&0xFFFF);
    }

    if(player_cell != cell ||
        player_cell_sector != pgc.cell_playback[cell - 1].first_sector.get() ||
        player_cell_restart != cell_restart)
    {
      int prevcell = player_cell;
      player_cell = cell;
      player_cell_restart = cell_restart;
      player_cell_sector = (int) pgc.cell_playback[cell - 1].first_sector.get();
      player_block = block;

      System.out.println("New cell starting at player_cell_sector: "+player_cell_sector);
      System.out.println("starting from block "+block);

      if(player_rate<0.0f)
      {
        player_vobu_sector = (int) pgc.cell_playback[cell - 1].last_vobu_start_sector.get();
        System.out.println("Modifying player_vobu_sector for reverse playback to "+
            player_vobu_sector);
      }
      else
      {
        player_vobu_sector = player_cell_sector + block;
      }
      player_vobu_offset = 0;
      player_vobu_skip = 0;
      player_vobu_length_sector = 0;
      player_next_vobu_sector = 0;
      forced_start = -1;

      int first_cell, last_cell;
      long program_length=0;
      long pgc_length=0;
      long cell_start=0;
      long program_start=0;

      first_cell = pgc.program_map[program-1].ele.get();

      if(program < pgc.nr_of_programs.get())
        last_cell = pgc.program_map[program].ele.get() - 1;
      else
        last_cell = pgc.nr_of_cells.get();

      for(int i = first_cell; i <= last_cell; i++)
      {
        if((pgc.cell_playback[i - 1].block_mode.get()&3)==0 ||
            (pgc.cell_playback[i - 1].block_mode.get()&3)==3)
        {
          program_length += pgc.cell_playback[i - 1].playback_time.toPTS();
        }
      }
      pgc_length = pgc.playback_time.toPTS();

      for(int i = 1; i < cell; i++)
      {
        if((pgc.cell_playback[i - 1].block_mode.get()&3)==0 ||
            (pgc.cell_playback[i - 1].block_mode.get()&3)==3)
        {
          cell_start += pgc.cell_playback[i - 1].playback_time.toPTS();
        }
      }
      for(int i = 1; i < pgc.program_map[program-1].ele.get(); i++)
      {
        if((pgc.cell_playback[i - 1].block_mode.get()&3)==0 ||
            (pgc.cell_playback[i - 1].block_mode.get()&3)==3)
        {
          program_start += pgc.cell_playback[i - 1].playback_time.toPTS();
        }
      }
      if(debugSTATE) System.out.println("Cell time info: "+
          " program length " + program_length/90000.0 +
          " pgc length "+ pgc_length/90000.0 +
          " cell start time "+ cell_start/90000.0 +
          " program start time "+ program_start/90000.0);

      putInt(buf, offset+0, player_cell);
      putInt(buf, offset+4, program);
      putLong(buf, offset+8, pgc.cell_playback[player_cell - 1].playback_time.toPTS());
      putLong(buf, offset+16, program_length);
      putLong(buf, offset+24, pgc_length);
      putLong(buf, offset+32, cell_start);
      putLong(buf, offset+40, program_start);
      //            int discontcell=pgc.cell_playback[player_cell - 1].stc_discontinuity.get() +
      //                ((prevcell != player_cell-1) ? 1 : 0) + pgc.cell_playback[player_cell - 1].interleaved.get() ;
      // TODO: in some cases could this be true if we changed pgc but cell number is same?
      int discontcell=( (cellangle==false && (prevcell != player_cell-1)) ? 1 : 0);
      if(pgc.cell_playback[player_cell - 1].block_mode.get()!=0)
      {
        cellangle=true;
      }
      else
      {
        cellangle=false;
      }
      putInt(buf, offset+48,  discontcell);
      new_palette = true;
      player_spu = -1;
      player_audio = -1;
      if(discontcell!=0)
      {
        need_empty=true;
        done_empty=false;
      }
      in_still = 0;

      return (sage.DVDReader.DVD_PROCESS_CELL<<16)|player_cell;
    }

    if(new_palette)
    {
      // copy from pgc.palette
      new_palette=false;
      for(int i=0;i<16;i++)
      {
        buf[offset+i*4+0]=(byte) ((pgc.palette[i].get()>>24)&0xFF);
        buf[offset+i*4+1]=(byte) ((pgc.palette[i].get()>>16)&0xFF);
        buf[offset+i*4+2]=(byte) ((pgc.palette[i].get()>>8)&0xFF);
        buf[offset+i*4+3]=(byte) ((pgc.palette[i].get()>>0)&0xFF);
      }
      return (sage.DVDReader.DVD_PROCESS_CLUT<<16)|16*4;
    }

    if(player_spu!=SPRM[SPSTN])
    {
      player_spu=SPRM[SPSTN];
      System.out.println("Sending stream for SPSTN " + SPRM[SPSTN]);
      return (sage.DVDReader.DVD_PROCESS_PICCHANNEL<<16)|
          get_subp_active_stream(monitor16_9 ? 0 :
            ((get_video_scale_permission()&1)==0 ? 1 :2 ))|(SPRM[SPSTN]&0x40);
    }

    if(player_audio!=SPRM[ASTN])
    {
      int audiostream;
      audio_attr_t audatr;
      audiostream=get_audio_active_stream();
      audatr = get_audio_attr((SPRM[ASTN]<8) ? SPRM[ASTN] : 0);
      int codingMode = (int) audatr.audio_format.get();
      if(codingMode==0) // AC3
      {
        audiostream|=0xBD80;
      }
      else if(codingMode==2) // MPEG
      {
        audiostream=0xC000|(audiostream<<8);
      }
      else if(codingMode==4) // LPCM
      {
        audiostream|=0xBDA0;
      }
      else if(codingMode==6) // DTS
      {
        audiostream|=0xBD88;
      }
      System.out.println("Sending stream for ASTN " + SPRM[ASTN] + " audiostream "+ audiostream);
      player_audio=SPRM[ASTN];
      return (sage.DVDReader.DVD_PROCESS_AUDIOCHANNEL<<16)|audiostream;
    }

    if(in_still!=0)
    {
      return (sage.DVDReader.DVD_PROCESS_PAUSE<<16)|in_still;
    }

    if(player_vobu_offset >= player_vobu_length_sector)
    {
      if(player_rate > 0.0f && (player_next_vobu_sector== 0x3FFFFFFF || player_vobu_skip == 0x3FFFFFFF) ) // end of cell
      {
        if(debugSTATE) System.out.println("End of cell detected in domain "+player_domain + "done empty is "+done_empty);
        in_still = pgc.cell_playback[cell - 1].still_time.get();
        if((in_still!=0 || (player_pci.hli.hl_gi.hli_ss.get()!=0 &&
            (player_domain==VMGM_DOMAIN || player_domain==VTSM_DOMAIN))) && !done_empty)
        {
          if(debugSTATE) System.out.println("need empty");
          need_empty=true;
        }
        else if(in_still == 0 || done_still)
        {
          next_cell();
          in_still=0;
          done_still=false;
          need_empty=false;
          done_empty=false;
        }
        return 0;
      }

      if(player_vobu_skip == -0x3FFFFFFF)
      {
        int goodcell=0;
        player_vobu_skip = 0;
        for(int i = 1; i<= pgc.cell_playback.length; i++)
        {
          if(i>=cell)
            break;
          if((pgc.cell_playback[i-1].block_mode.get()&2)==0)
          {
            if((pgc.cell_playback[i-1].block_mode.get()&1)==1)
            { // Process angles
              // Only update if this gets us to a previous cell than current
              if((i + SPRM[AGLN] - 1)<cell)
              {
                goodcell = i + SPRM[AGLN] - 1;
              }
            }
            else
            {
              goodcell = i;
            }
          }
        }
        if(goodcell==0)
        {
          // we're at start of first cell, exit rewind mode;
          player_rate=1.0f;
          goodcell=1;
          return sage.DVDReader.DVD_PROCESS_RATE<<16;
        }
        // go to end of goodcell
        cell=goodcell;
        return 0;
      }

      if(player_next_vobu_sector==0)
      {
        if(debugSTATE) System.out.println("next 0, trying to read nav block at "+ (player_vobu_sector + player_vobu_offset + player_vobu_skip));
        titlefile.readBlocks(buf, offset,  player_vobu_sector +
            player_vobu_offset + player_vobu_skip, 1);
      }
      else
      {
        if(debugSTATE) System.out.println("Using next vobu, trying to read nav block at "+ player_vobu_sector + player_next_vobu_sector);
        titlefile.readBlocks(buf, offset,  player_vobu_sector + player_next_vobu_sector, 1);
      }
      if(!parseNav(buf, offset, player_pci, player_dsi))
      {
        // player_next_vobu_sector must not be used once we enter this "bad state"
        // usually we should not see those unless we had a seek or a bad dvd
        if(player_next_vobu_sector==0)
        {
          System.out.println("Warning, was expecting nav packet at "+
              (player_vobu_sector + player_vobu_offset + player_vobu_skip) );
        }
        else
        {
          System.out.println("Warning, was expecting nav packet at "+
              (player_vobu_sector + player_next_vobu_sector) );
        }
        player_vobu_sector = player_cell_sector + block;
        //player_vobu_offset = 0;
        player_vobu_length_sector = 0; // Try to find nav every packet...
        player_next_vobu_sector = 0;
        block++; // increase offset of where we search...
        if (player_vobu_sector >= pgc.cell_playback[cell - 1].last_sector.get())
        {
          next_cell();
        }
        return 0;
      }
      else
      {
        // We're entering a new vobu with good nav packets
        // we must update the playback variables
        // next_vobu must be used when there are angles to follow them
        player_vobu_sector = (int) player_dsi.dsi_gi.nv_pck_lbn.get();
        player_vobu_length_sector = (int) player_dsi.dsi_gi.vobu_ea.get()+1;
        player_next_vobu_sector = (int) (player_dsi.vobu_sri.next_vobu.get() & 0x3fffffffL);
        // if there is no angle next_vobu_sector should match length
        block = player_vobu_sector - player_cell_sector;

        if(debugSTATE) System.out.println("vobu sector " + player_vobu_sector + " length " +
            player_vobu_length_sector + " next at delta " + player_next_vobu_sector + " ilvu flag "+
            (int)player_dsi.sml_pbi.category.get());

        if( player_pci.hli.hl_gi.btn_ns.get()!=0 &&
            (SPRM[HL_BTNN]>>10)>player_pci.hli.hl_gi.btn_ns.get())
        {
          if(debugSTATE) System.out.println("fixing button number outside range");
          player_button=0;
          // TODO: confirm the right choice is max button...
          SPRM[HL_BTNN]=player_pci.hli.hl_gi.btn_ns.get()<<10;
        }
        if(player_pci.hli.hl_gi.fosl_btnn.get()!=0 && forced_start!=player_pci.hli.hl_gi.hli_s_ptm.get())
        {
          if(debugSTATE) System.out.println("forced button number"
              + player_pci.hli.hl_gi.fosl_btnn.get());
          forced_start=player_pci.hli.hl_gi.hli_s_ptm.get();
          player_button=0;
          SPRM[HL_BTNN]=player_pci.hli.hl_gi.fosl_btnn.get()<<10;
        }
        player_vobu_offset = 0;
        if(player_rate>1.0f)
        {
          // Find next vobu closest to the speed we are aiming
          // We assume the player will do around 6fps (according to Jeff tests)
          // We also assume blocks are .5 seconds even if they could be a bit larger
          int vobucount = (int) (player_rate/3.0f);
          player_vobu_skip=0;

          if(vobucount<1) vobucount = 1;
          if(vobucount>14) vobucount = 14; // for now don't support extended speeds
          if(vobucount<15) player_vobu_skip =
              (int) (player_dsi.vobu_sri.fwda[19-vobucount].get() & 0x3fffffffL);
          if(player_vobu_skip != 0x3fffffff && player_vobu_skip != 0)
            player_vobu_skip-=player_vobu_length_sector;

          System.out.println("vobu skip is "+player_vobu_skip);
          // we must not use player vobu skip if it is 0 to follow angles correctly
          if(player_vobu_skip!=0)
          {
            player_next_vobu_sector=0; // Force second mode of finding nav packets...
          }
        }
        else if(player_rate<0.0f)
        {
          int vobucount = (int) (-player_rate/3.0f);
          player_vobu_skip=0;

          if(vobucount<1) vobucount = 1;
          if(vobucount>14) vobucount = 14; // for now don't support extended speeds
          if(vobucount<15) player_vobu_skip =
              - (int)(player_dsi.vobu_sri.bwda[vobucount].get() & 0x3fffffffL);
          if(player_vobu_skip != -0x3fffffff)
          {
            player_vobu_skip-=player_vobu_length_sector;
          }
          System.out.println("vobu skip is "+player_vobu_skip);
          // we must not use player vobu skip if it is 0 to follow angles correctly
          if(player_vobu_skip!=0)
          {
            player_next_vobu_sector=0; // Force second mode of finding nav packets...
          }
        }
        else
        {
          player_vobu_skip=0;
        }
        return (sage.DVDReader.DVD_PROCESS_NAV<<16);
      }
    }

    // If we get the done empty was processed
    if(done_empty) done_empty=false;

    if(debugMPEG) System.out.println("Reading block at "+ (player_vobu_sector + player_vobu_offset));
    titlefile.readBlocks(buf, offset, player_vobu_sector + player_vobu_offset, 1);
    player_vobu_offset+=1;
    return (sage.DVDReader.DVD_PROCESS_DATA<<16)|2048;
  }

  public synchronized boolean playControlEx(int playCode, long param1, long param2, pci_t pci)
  {
    if(pci==null) pci=player_pci;
    switch(playCode)
    {
      case sage.DVDReader.DVD_CONTROL_MENU:
        switch((int)param1)
        {
          case 1: // title
            need_flush = jump_menu(DVD_MENU_Title);
            return need_flush;
          case 2: // root
            need_flush = jump_menu(DVD_MENU_Root);
            return need_flush;
        }
        break;
      case sage.DVDReader.DVD_CONTROL_TITLE_SET:
        need_flush = jump_title((int) param1);
        return need_flush;
      case sage.DVDReader.DVD_CONTROL_CHAPTER_SET:
        need_flush = jump_pg((int)param1);
        return need_flush;
      case sage.DVDReader.DVD_CONTROL_CHAPTER_NEXT:
        need_flush = jump_next_pg();
        return need_flush;
      case sage.DVDReader.DVD_CONTROL_CHAPTER_PREV:
        need_flush = jump_prev_pg();
        return need_flush;
      case sage.DVDReader.DVD_CONTROL_ACTIVATE_CURRENT:
        activate_button(pci);
        return true;
      case sage.DVDReader.DVD_CONTROL_RETURN:
        if(domain==VTSM_DOMAIN || domain==VMGM_DOMAIN)
        {
          need_flush = jump_resume();
          return need_flush;
        }
        break;
      case sage.DVDReader.DVD_CONTROL_BUTTON_NAV:
        switch((int)param1)
        {
          case 0:
            select_button((int)param2, pci);
            break;
          case 1:
            select_button(pci.hli.btnit[(SPRM[HL_BTNN]>>10)-1].up.get(), pci);
            break;
          case 2:
            select_button(pci.hli.btnit[(SPRM[HL_BTNN]>>10)-1].right.get(), pci);
            break;
          case 3:
            select_button(pci.hli.btnit[(SPRM[HL_BTNN]>>10)-1].down.get(), pci);
            break;
          case 4:
            select_button(pci.hli.btnit[(SPRM[HL_BTNN]>>10)-1].left.get(), pci);
            break;
        }
        break;
      case sage.DVDReader.DVD_CONTROL_MOUSE_HOVER:
        break;
      case sage.DVDReader.DVD_CONTROL_MOUSE_CLICK:
        break;
      case sage.DVDReader.DVD_CONTROL_ANGLE_CHANGE:
        break;
      case sage.DVDReader.DVD_CONTROL_SUBTITLE_CHANGE: // -1 means next
        if(param1==-1)
        {
          String[] subps = getDVDAvailableSubpictures();
          if(subps==null || subps.length==0)
          {
            param1=0;
            break;
          }
          param1=SPRM[SPSTN]&0x1F;
          param1+=1;
          while(param1>=subps.length) param1-=subps.length;
          SPRM[SPSTN]|=0x40; // We assume they want subs?
        }
        SPRM[SPSTN]=(SPRM[SPSTN]&0x40)|(int)param1|0x40; // make sure its on
        break;
      case sage.DVDReader.DVD_CONTROL_SUBTITLE_TOGGLE:
        SPRM[SPSTN]^=0x40;
        // Ensure a subtitle is actually selected.
        if((SPRM[SPSTN]&0x3F)>=0x20 && (SPRM[SPSTN]&0x40)==0x40)
          SPRM[SPSTN]=(SPRM[SPSTN]&0x40);
        break;
      case sage.DVDReader.DVD_CONTROL_AUDIO_CHANGE: // -1 means next
        if(param1==-1)
        {
          String[] auds = getDVDAvailableLanguages();
          if(auds==null || auds.length==0)
          {
            param1=0;
            break;
          }
          param1=SPRM[ASTN];
          param1+=1;
          while(param1>=auds.length) param1-=auds.length;
        }
        SPRM[ASTN]=(int)param1;
        break;
      case sage.DVDReader.DVD_CONTROL_UNPAUSE:
        in_still=0;
        done_still=true;
        return true;
      case sage.DVDReader.DVD_CONTROL_UNEMPTY:
        done_empty=true;
        return true;
      case sage.DVDReader.DVD_CONTROL_SEEKTO:
        if (player_dsi != null && pgc != null && pgc.cell_playback != null)
        {
          long targetPTS = param1 * 90;
          long cellStart = 0;
          long cellEnd = 0;
          int targetCell = 0;
          long cellPosEnd=0,cellPosStart=0;
          for(int i = 1; i<= pgc.cell_playback.length; i++)
          {
            if((pgc.cell_playback[i-1].block_mode.get()&2)==0)
            {
              cellStart = cellEnd;
              cellEnd += pgc.cell_playback[i-1].playback_time.toPTS();
              cellPosStart = pgc.cell_playback[i-1].first_sector.get();
              cellPosEnd = pgc.cell_playback[i-1].last_sector.get();
              if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("DVD seek test cell[" + i + "] cellEnd=" + cellEnd +
                  " posStart=" + cellPosStart + " posEnd=" + cellPosEnd);
              if (cellEnd > targetPTS)
              {
                targetCell = i;
                break;
              }
            }
          }
          if (targetCell == 0)
            break;
          int targetSector;
          int sectorOffset = (player_vobu_offset + player_vobu_sector) - player_cell_sector;
          long currVobuPTS = player_dsi.dsi_gi.c_eltm.toPTS() + cellStart;
          if (targetCell == cell)
          {
            if (targetPTS >= currVobuPTS)
            {
              targetSector = (int)Math.round(((targetPTS - currVobuPTS)*1.0/
                  (cellEnd - currVobuPTS)) * (cellPosEnd - (cellPosStart + sectorOffset))) + sectorOffset;
            }
            else
            {
              targetSector = (int)Math.round(((targetPTS - cellStart)*1.0/(currVobuPTS - cellStart)) *
                  sectorOffset);
            }
          }
          else
            targetSector = (int)Math.round(((targetPTS - cellStart)*1.0/(cellEnd - cellStart)) * (cellPosEnd - cellPosStart));

          if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("DVD seek currCell=" + cell + " newCell=" + targetCell +
              " targetSector=" + targetSector + " targetPTS=" + targetPTS + " sectorOffset=" + sectorOffset + " currPTS=" +
              currVobuPTS + " oldTarget=" + ((int)Math.round(((targetPTS - cellStart)*1.0/(cellEnd - cellStart)) * (cellPosEnd - cellPosStart))));
          // targetSector could be in the wrong angle...
          jump_cell_block(targetCell, targetSector);
          // by flushing we should be fine for following angles but the starting point can still be wrong
          need_flush = true;
        }
        break;
    }
    return false;
  }

  public synchronized void set_rate(float rate)
  {
    player_rate=rate;
  }

  void select_button(int button, pci_t pci)
  {
    if(button<1 || button>pci.hli.hl_gi.btn_ns.get())
    {
      return;
    }
    SPRM[HL_BTNN] = (button << 10);
    player_button = 0;
  }

  void activate_button(pci_t pci)
  {
    long link;
    vm_cmd_t cmds[] = new vm_cmd_t[1];
    int button=(SPRM[HL_BTNN]>>10);
    if(button<1 || button>pci.hli.hl_gi.btn_ns.get())
    {
      return;
    }
    cmds[0]=pci.hli.btnit[button-1].cmd;
    // TODO: finish that
    link = executeCommand(cmds, 1, 1);
    if((link&0xFF00)!=0)
    {
      need_flush = process_command(link);
    }
    in_still=0;
    player_button=0;
  }

  public synchronized void unpause()
  {
    playControlEx(sage.DVDReader.DVD_CONTROL_UNPAUSE,0,0, null);
  }

  public synchronized void unempty()
  {
    playControlEx(sage.DVDReader.DVD_CONTROL_UNEMPTY,0,0, null);
  }

  public synchronized int getDVDTitle()
  {
    return SPRM[TTN];
  }

  public synchronized int getDVDTotalTitles()
  {
    return vmgifo.tt_srpt.nr_of_srpts.get();
  }

  public synchronized int getDVDChapter()
  {
    return program;
  }

  public synchronized int getDVDTotalChapters()
  {
    return pgc.nr_of_programs.get();
  }

  public synchronized int getDVDDomain()
  {
    return domain;
  }

  public synchronized boolean areDVDButtonsVisible(pci_t pci)
  {
    if(pci==null) pci=player_pci;
    return pci.hli.hl_gi.btn_ns.get() > 0;
  }

  public synchronized int getDVDAngle()
  {
    return get_angle_info()&0xFFFF;
  }

  public synchronized int getDVDTotalAngles()
  {
    return get_angle_info()>>16;
  }

  private String getAudioLanguage(int stream)
  {
    audio_attr_t audatr;
    audatr=get_audio_attr(stream);
    if(audatr.lang_type.get()!=1)
    {
      return (stream + 1) + " Unspecified";
    }
    byte b[]=new byte[2];
    b[0]=(byte) ((audatr.lang_code.get()>>8)&0xFF);
    b[1]=(byte) (audatr.lang_code.get()&0xFF);
    String rv = (stream + 1) + " " + convertLangCode(new String(b));
    short codingMode = audatr.audio_format.get();
    if (codingMode == 0)
      rv += " AC3";
    else if (codingMode == 6)
      rv += " DTS";
    short channels = audatr.channels.get();
    if (channels == 5)
      rv += " 5.1";
    short codeExt = audatr.code_extension.get();
    switch (codeExt)
    {
      case 1:
        rv += " Normal";
        break;
      case 2:
        rv += " for Visually Impaired";
        break;
      case 3:
        rv += " w/ Director's Comments";
        break;
      case 4:
        rv += " w/ Alternate Director's Comments";
        break;
    }
    return rv;
  }

  public synchronized String getDVDLanguage()
  {
    if(SPRM[ASTN]<8 | domain!=VTS_DOMAIN)
    {
      return getAudioLanguage(SPRM[ASTN]);
    }
    else
    {
      return null;
    }
  }

  public synchronized String[] getDVDAvailableLanguages()
  {
    String []tmp = null;
    switch(domain)
    {
      case VTS_DOMAIN:
        tmp=new String[vtsifo.vtsi_mat.nr_of_vts_audio_streams.get()];
        for(int i=0;i<vtsifo.vtsi_mat.nr_of_vts_audio_streams.get();i++)
        {
          tmp[i]=getAudioLanguage(i);
        }
        break;
      case VTSM_DOMAIN:
        tmp=new String[vtsifo.vtsi_mat.nr_of_vtsm_audio_streams.get()];
        for(int i=0;i<vtsifo.vtsi_mat.nr_of_vtsm_audio_streams.get();i++)
        {
          tmp[i]=getAudioLanguage(i);
        }
        break;
      case VMGM_DOMAIN:
      case FP_DOMAIN:
        tmp=new String[vmgifo.vmgi_mat.nr_of_vmgm_audio_streams.get()];
        for(int i=0;i<vmgifo.vmgi_mat.nr_of_vmgm_audio_streams.get();i++)
        {
          tmp[i]=getAudioLanguage(i);
        }
        break;
      default:
        return null;
    }
    return tmp;
  }

  private String getSubpLanguage(int stream)
  {
    subp_attr_t subatr;
    subatr=get_subp_attr(stream);
    if(subatr.type.get()!=1)
    {
      return (stream + 1) + " Unspecified";
    }
    byte b[]=new byte[2];
    b[0]=(byte) ((subatr.lang_code.get()>>8)&0xFF);
    b[1]=(byte) (subatr.lang_code.get()&0xFF);
    String rv = (stream + 1) + " " + convertLangCode(new String(b));
    short subTypeCode = subatr.code_extension.get();
    switch (subTypeCode)
    {
      case 1:
        rv += " Normal";
        break;
      case 2:
        rv += " Large";
        break;
      case 3:
        rv += " Children";
        break;
      case 5:
        rv += " Normal CC";
        break;
      case 6:
        rv += " Large CC";
        break;
      case 7:
        rv += " Children's CC";
        break;
      case 9:
        rv += " Forced";
        break;
      case 13:
        rv += " Director Comments";
        break;
      case 14:
        rv += " Large Director Comments";
        break;
      case 15:
        rv += " Director Comments for Children";
        break;
    }
    return rv;
  }

  public synchronized String getDVDSubpicture()
  {
    if((SPRM[SPSTN]&0x3F)<0x20 && (SPRM[SPSTN]&0x40)==0x40)
    {
      return getSubpLanguage(SPRM[SPSTN] & 0x3F);
    }
    else
    {
      return null;
    }
  }

  public synchronized String[] getDVDAvailableSubpictures()
  {
    String []tmp = null;
    switch(domain)
    {
      case VTS_DOMAIN:
        tmp=new String[vtsifo.vtsi_mat.nr_of_vts_subp_streams.get()];
        for(int i=0;i<vtsifo.vtsi_mat.nr_of_vts_subp_streams.get();i++)
        {
          tmp[i]=getSubpLanguage(i);
        }
        break;
      case VTSM_DOMAIN:
        tmp=new String[vtsifo.vtsi_mat.nr_of_vtsm_subp_streams.get()];
        for(int i=0;i<vtsifo.vtsi_mat.nr_of_vtsm_subp_streams.get();i++)
        {
          tmp[i]=getSubpLanguage(i);
        }
        break;
      case VMGM_DOMAIN:
      case FP_DOMAIN:
        tmp=new String[vmgifo.vmgi_mat.nr_of_vmgm_subp_streams.get()];
        for(int i=0;i<vmgifo.vmgi_mat.nr_of_vmgm_subp_streams.get();i++)
        {
          tmp[i]=getSubpLanguage(i);
        }
        break;
      default:
        return null;
    }
    return tmp;
  }

  public void setMonitor16_9(boolean is16_9)
  {
    monitor16_9=is16_9;
  }

  public static String convertLangCode(String s)
  {
    if (s == null) return s;
    if (s.length() >= 2)
    {
      return new java.util.Locale(s.substring(0, 2)).getDisplayLanguage() + s.substring(2);
    }
    else
      return s;
  }

  public long seek(long mediaTimeMillis) throws sage.PlaybackException
  {
    playControlEx(sage.DVDReader.DVD_CONTROL_SEEKTO,mediaTimeMillis,0, null);
    return mediaTimeMillis;
  }
}
