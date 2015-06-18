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

public class DVDTest
{
  public static void main(String[] args)
  {
    System.out.println("DVDTest");
    if(args.length<1)
    {
      System.out.println("Usage: DVDTest dvdpath");
      return;
    }

    VM testvm = new VM();
    testvm.reset(args[0], null);
    dsi_t testdsi = new dsi_t();
    pci_t testpci = new pci_t();
    byte [] buffer = new byte[2048];
    int retcode=0;

    int bufferSize = 32768;
    java.io.OutputStream pushDumpStream = null;
    try
    {
      pushDumpStream = new java.io.BufferedOutputStream(
          new java.io.FileOutputStream(
              "dvddump.mpg"));
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR creating push dump debug file:" +e );
    }

    while(retcode>=0)
    {
      retcode = testvm.process(buffer, 0);
      switch(retcode>>16)
      {
        case sage.DVDReader.DVD_PROCESS_DATA:
          //System.out.println("Got DATA");
          if (pushDumpStream != null)
          {
            try
            {
              pushDumpStream.write(buffer, 0, retcode&0xFFFF);
            }
            catch (Exception e)
            {
              System.out.println("ERROR writing push buffer dump stream: " + e);
            }
          }

          break;
        case sage.DVDReader.DVD_PROCESS_PAUSE:
          System.out.println("Need to pause for "+ (retcode&0xFF) + " seconds");
          try { testvm.playControlEx(sage.DVDReader.DVD_CONTROL_UNPAUSE, 0, 0, null); }
          catch (Exception e)
          {
            System.out.println("ERROR with unpause: " + e);
          }
          continue;
        case sage.DVDReader.DVD_PROCESS_EMPTY:
          System.out.println("Need to empty buffer");
          try { testvm.playControlEx(sage.DVDReader.DVD_CONTROL_UNEMPTY, 0, 0, null); }
          catch (Exception e)
          {
            System.out.println("ERROR with unempty: " + e);
          }
          continue;
        case sage.DVDReader.DVD_PROCESS_FLUSH:
          System.out.println("Need to flush buffer");
          continue;
        case sage.DVDReader.DVD_PROCESS_CELL:
          System.out.println("Entered cell "+(retcode&0xFFFF));
          continue;
        case sage.DVDReader.DVD_PROCESS_AUDIOCHANNEL:
          System.out.println("New audio channel");
          continue;
        case sage.DVDReader.DVD_PROCESS_PICCHANNEL:
          System.out.println("New picture channel");
          continue;
        case sage.DVDReader.DVD_PROCESS_NAV:
          System.out.println("New navigation data");
          if(testvm.parseNav(buffer, 0, testpci, testdsi))
          {
            if(testpci.hli.hl_gi.btn_ns.get() > 0)
            {
              int button=0;
              System.out.println("On menu with "+testpci.hli.hl_gi.btn_ns.get()+" buttons");
              try
              {
                while(button < 1 || button > testpci.hli.hl_gi.btn_ns.get())
                {
                  int ch;
                  button=0;
                  while((ch = System.in.read ()) != '\n')
                  {
                    if (ch >= '0' && ch <= '9')
                    {
                      button *= 10;
                      button += ch - '0';
                    }
                  }
                }
              }
              catch (Exception e)
              {
                System.out.println("ERROR reading button: " + e);
              }
              System.out.println("selecting "+button);
              testvm.select_button(button, null);
              try { testvm.playControlEx(sage.DVDReader.DVD_CONTROL_ACTIVATE_CURRENT, 0, 0, null); }
              catch (Exception e)
              {
                System.out.println("ERROR with unpause: " + e);
              }
            }
          }
          continue;
        case sage.DVDReader.DVD_PROCESS_CLUT:
          System.out.println("New palette");
          continue;
        case sage.DVDReader.DVD_PROCESS_HIGHLIGHT:
          System.out.println("New button "+(retcode&0xFFFF));
          continue;
        case sage.DVDReader.DVD_PROCESS_VTS:
          System.out.println("Entered VTS "+(retcode&0xFFFF));
          continue;
        default:
          System.out.println("process code "+(retcode>>16));
      }
    }
  }
}
