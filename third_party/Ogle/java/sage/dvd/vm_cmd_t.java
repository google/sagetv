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

public class vm_cmd_t extends Struct
{
  public Unsigned8[] bytes = (Unsigned8[]) array(new Unsigned8[8]);

  public String compareOpcode(int cmp)
  {
    switch(cmp)
    {
      case 0:
        return "";
      case 1:
        return "&";
      case 2:
        return "==";
      case 3:
        return "!=";
      case 4:
        return ">=";
      case 5:
        return ">";
      case 6:
        return "<=";
      case 7:
        return "<";
    }
    return "invalid";
  }

  private String setOpcode(int set)
  {
    switch(set)
    {
      case 1: // mov
        return "=";
      case 2: // swp
        return "swap";
      case 3: // add
        return "+=";
      case 4: // sub
        return "-=";
      case 5: // mul
        return "*=";
      case 6: // div
        return "/=";
      case 7: // mod
        return "%=";
      case 8: // random
        return "random";
      case 9: // and
        return "&=";
      case 10: // or
        return "|=";
      case 11: // xor
        return "^=";
    }
    return "invalid";
  }

  private String getReg(int reg)
  {
    if((reg&0x80)!=0)
    {
      if((reg&0x7F)<24)
      {
        return "SPRM["+(reg & 0x1f)+"]";
      }
      else
      {
        return "Invalid SPRM";
      }
    }
    else
    {
      if((reg&0x7F)<16)
      {
        return "GPRM["+(reg)+"]";
      }
      else
      {
        return "Invalid GPRM";
      }
    }
  }

  public String toString()
  {
    StringBuffer sb = new StringBuffer();

    sb.append(super.toString());
    sb.append(" : ");

    int type=(bytes[0].get()>>5)&0x7;
    int direct=(bytes[0].get()>>4)&0x1;
    int set=(bytes[0].get()>>0)&0xF;
    int dircmp=(bytes[1].get()>>7)&0x1;
    int cmp=(bytes[1].get()>>4)&0x7;
    int cmd=(bytes[1].get()>>0)&0xF;
    int operand1=(bytes[2].get()<<8)|bytes[3].get();
    int operand2=(bytes[4].get()<<8)|bytes[5].get();
    int operand3=(bytes[6].get()<<8)|bytes[7].get();

    switch(type)
    {
      case 0: // Special instructions
        if(cmp!=0)
        {
          sb.append("if ");
          sb.append(getReg(operand1&0xFF));
          sb.append(" ");
          sb.append(compareOpcode(cmp));
          sb.append(" ");
          if(dircmp==1)
            sb.append(operand2);
          else
            sb.append(getReg(operand2&0xFF));

          sb.append(" : ");
        }
        {
          int line;
          int level;
          switch(cmd)
          {
            case 0: // NOP
              sb.append("NOP");
              break;
            case 1: // Goto
              sb.append("Goto ");
              line = bytes[7].get();
              sb.append(line);
              break;
            case 2: // Break
              sb.append("Break ");
              break;
            case 3: // SetTmpPML
              level = bytes[6].get()&0xF;
              line = bytes[7].get();
              sb.append("SetTmpPML "+level+" "+" Goto "+line);
              break;
          }
        }
        break;
      case 1: // Link instructions then direct jump and call instructions
        if(direct==0)
        { // Link
          if(cmp!=0)
          {
            sb.append("if ");
            sb.append(getReg(operand1&0xFF));
            sb.append(" ");
            sb.append(compareOpcode(cmp));
            sb.append(" ");
            if(dircmp==1)
              sb.append(operand2);
            else
              sb.append(getReg(operand2&0xFF));
            sb.append(" : ");
          }
          {
            switch(cmd)
            {
              case 0: // NOP
                sb.append("NOP");
                break;
              case 1: // Link
                sb.append("Link "+(operand3&0x1F)+" "+(operand3));
                break;
              case 4: // LinkPGCN
                sb.append("LinkPGCN "+(operand3));
                break;
              case 5: // LinkPTTN
                sb.append("LinkPTTN "+(operand3));
                break;
              case 6: // LinkPGN
                sb.append("LinkPGN "+(operand3));
                break;
              case 7: // LinkCN
                sb.append("LinkCN "+(operand3));
                break;
            }
          }
        }
        else
        { // Jump/call
          if(cmp!=0)
          {
            sb.append("if ");
            sb.append(getReg((operand3>>8)&0xFF));
            sb.append(" ");
            sb.append(compareOpcode(cmp));
            sb.append(" ");
            sb.append(getReg(operand3&0xFF));
            sb.append(" : ");
          }
          {
            switch(cmd)
            {
              case 0: // NOP
                sb.append("NOP");
                break;
              case 1: // Exit
                sb.append("Exit");
                break;
              case 2: // JumpTT
                sb.append("JumpTT "+(operand2&0x7F));
                break;
              case 3: // JumpVTS_TT
                sb.append("JumpVTS_TT "+(operand2&0x7F));
                break;
              case 5: // JumpVTS_PTT
                sb.append("JumpVTS_PTT "+(operand2&0x7F)+","+(operand1&0x3FF));
                break;
              case 6: // JumpSS
                switch((operand2>>6)&3)
                {
                  case 0: // FP
                    sb.append("JumpSS FP ");
                    break;
                  case 1: // VMGM menu
                    sb.append("JumpSS VMGM menu "+(operand2&0xF));
                    break;
                  case 2: // VTSM
                    sb.append("JumpSS VTSM "+((operand2>>8)&0xFF)+","+(operand1&0xFF)+","+(operand2&0xF));
                    break;
                  case 3: // VMGM pgcn
                    sb.append("JumpSS VMGM pgc "+(operand1&0x7FFF));
                    break;
                }
                break;
              case 8: // CallSS
                switch((operand2>>6)&3)
                {
                  case 0: // FP
                    sb.append("CallSS FP "+((operand2>>8)&0xFF));
                    break;
                  case 1: // VMGM menu
                    sb.append("CallSS VMGM menu "+(operand2&0xF)+","+((operand2>>8)&0xFF));
                    break;
                  case 2: // VTSM
                    sb.append("CallSS VTSM "+(operand2&0xF)+","+((operand2>>8)&0xFF));
                    break;
                  case 3: // VMGM pgcn
                    sb.append("CallSS VMGM pgc "+(operand1&0x7FFF)+","+((operand2>>8)&0xFF));
                    break;
                }
                break;
            }
          }
        }
        break;
      case 2: // SPRM Modifiers
        if(cmp!=0)
        {
          sb.append("if ");
          sb.append(getReg((operand3>>8)&0xFF));
          sb.append(" ");
          sb.append(compareOpcode(cmp));
          sb.append(" ");
          sb.append(getReg(operand3&0xFF));
          sb.append(" : ");
        }
        {
          switch(set)
          {
            case 1: // SetSTN
              sb.append("SetSTN ");
              if((operand1&0x80)!=0)
              {
                sb.append("ASTN = ");
                if(direct==1)
                  sb.append(operand1&0x7F);
                else
                  sb.append(getReg(operand1&0xF));
              }
              if((operand2&0x8000)!=0)
              {
                sb.append("SPSTN = ");
                if(direct==1)
                  sb.append((operand2>>8)&0x7F);
                else
                  sb.append(getReg((operand2>>8)&0xF));
              }
              if((operand2&0x80)!=0)
              {
                sb.append("AGLN = ");
                if(direct==1)
                  sb.append(operand2&0x7F);
                else
                  sb.append(getReg((operand2)&0xF));
              }
              break;
            case 2: // SetNVTMR
              sb.append("SetNVTMR ");
              if(direct==1)
                sb.append(operand1);
              else
                sb.append(getReg((operand1)&0xF));

              sb.append(" pgcn= ");
              sb.append(operand2);
              break;
            case 3: // SetGPRMMD
              sb.append("SetGPRMMD ");

              if((operand2&0x80)!=0) // Timer mode
              {
                sb.append("timer ");
              }
              else
              {
              }
              sb.append(getReg(operand2&0xF));
              sb.append("=");
              if(direct==1)
                sb.append(operand1);
              else
                sb.append(getReg(operand1&0xFF));
              break;
            case 4: // SetAMXMD
              // Ignore Karaoke for now...
              sb.append("SetAMXMD");
              break;
            case 6: // SetHL_BTNN
              sb.append("SetHL_BTTN ");
              if(direct==1)
                sb.append(operand2);
              else
                sb.append(getReg(operand2&0xF));
              break;
          }
          // Link should not be possible if cmp!=0
          if(cmp==0)
          {
            switch(cmd)
            {
              case 1: // Link
                sb.append(" Link "+(operand3&0x1F)+" "+(operand3));
                break;
              case 4: // LinkPGCN
                sb.append(" LinkPGCN "+(operand3));
                break;
              case 5: // LinkPTTN
                sb.append(" LinkPTTN "+(operand3));
                break;
              case 6: // LinkPGN
                sb.append(" LinkPGN "+(operand3));
                break;
              case 7: // LinkCN
                sb.append(" LinkCN "+(operand3));
                break;
            }
          }
        }
        break;
      case 3: // GPRM Modifiers
        if(cmp!=0)
        {
          sb.append("if ");
          sb.append(getReg((operand1>>8)&0xFF));
          sb.append(" ");
          sb.append(compareOpcode(cmp));
          sb.append(" ");
          if(dircmp==1)
            sb.append(operand3);
          else
            sb.append(getReg(operand3&0xFF));
          sb.append(" : ");
        }
        {
          sb.append(getReg(operand1&0xF));
          sb.append(" ");
          sb.append(setOpcode(set));
          sb.append(" ");
          if(direct==1)
            sb.append(operand2);
          else
            sb.append(getReg(operand2&0xFF));

          if(cmp==0) // Can link if no compare
          {
            switch(cmd)
            {
              case 1: // Link
                sb.append(" Link "+(operand3&0x1F)+" "+(operand3));
                break;
              case 4: // LinkPGCN
                sb.append(" LinkPGCN "+(operand3));
                break;
              case 5: // LinkPTTN
                sb.append(" LinkPTTN "+(operand3));
                break;
              case 6: // LinkPGN
                sb.append(" LinkPGN "+(operand3));
                break;
              case 7: // LinkCN
                sb.append(" LinkCN "+(operand3));
                break;
            }
          }
        }
        break;
      case 4: // Set then cond link
        sb.append(getReg(cmd));
        sb.append(" ");
        sb.append(setOpcode(set));
        sb.append(" ");
        if(direct==1)
          sb.append(operand1);
        else
          sb.append(getReg(operand1&0xFF));

        sb.append(" ");

        if(cmp!=0)
        {
          sb.append("if ");
          sb.append(getReg(cmd));
          sb.append(" ");
          sb.append(compareOpcode(cmp));
          sb.append(" ");
          if(dircmp==1)
            sb.append(operand2);
          else
            sb.append(getReg(operand2&0xFF));
          sb.append(" : ");
        }

        sb.append(" Link "+(operand3&0x1F)+" "+(operand3));
        break;
      case 5: // Cond set then cond link
        if(direct==0)
        {
          if(cmp!=0)
          {
            sb.append("if ");
            sb.append(getReg(operand1&0xFF));
            sb.append(" ");
            sb.append(compareOpcode(cmp));
            sb.append(" ");
            if(dircmp==1)
              sb.append(operand2);
            else
              sb.append(getReg(operand2&0xFF));

            sb.append(" : ");
          }

          sb.append(getReg(cmd));
          sb.append(" ");
          sb.append(setOpcode(set));
          sb.append(" ");
          sb.append(getReg((operand1>>8)&0xFF));
          sb.append(" ");

          sb.append(" Link "+(operand3&0x1F)+" "+(operand3));
        }
        else
        {
          if(cmp!=0)
          {
            sb.append("if ");
            sb.append(getReg((operand2>>8)&0xFF));
            sb.append(" ");
            sb.append(compareOpcode(cmp));
            sb.append(" ");
            sb.append(getReg(operand2&0xFF));
            sb.append(" : ");
          }

          sb.append(getReg(cmd));
          sb.append(" ");
          sb.append(setOpcode(set));
          sb.append(" ");
          sb.append(operand1);
          sb.append(" ");

          sb.append(" Link "+(operand3&0x1F)+" "+(operand3));
        }
        break;
      case 6: // Cond set then link
        if(direct==0)
        {
          if(cmp!=0)
          {
            sb.append("if ");
            sb.append(getReg(operand1&0xFF));
            sb.append(" ");
            sb.append(compareOpcode(cmp));
            sb.append(" ");
            if(dircmp==1)
              sb.append(operand2);
            else
              sb.append(getReg(operand2&0xFF));

            sb.append(" : ");
          }

          sb.append(getReg(cmd));
          sb.append(" ");
          sb.append(setOpcode(set));
          sb.append(" ");
          sb.append(getReg((operand1>>8)&0xFF));
          sb.append(" ");

          sb.append(" always Link "+(operand3&0x1F)+" "+(operand3));
        }
        else
        {
          if(cmp!=0)
          {
            sb.append("if ");
            sb.append(getReg((operand2>>8)&0xFF));
            sb.append(" ");
            sb.append(compareOpcode(cmp));
            sb.append(" ");
            sb.append(getReg(operand2&0xFF));
            sb.append(" : ");
          }

          sb.append(getReg(cmd));
          sb.append(" ");
          sb.append(setOpcode(set));
          sb.append(" ");
          sb.append(operand1);
          sb.append(" ");

          sb.append(" always Link "+(operand3&0x1F)+" "+(operand3));
        }
        break;
        // break;
      case 7: // Invalid
        break;
    }

    return sb.toString();

  }

}
