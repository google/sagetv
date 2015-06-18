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

public class DVDFileReader implements DVDReader
{
  String dvdroot;
  java.io.File dvddir;
  String hostname;
  String separator = System.getProperty("file.separator");

  // Return the right filename from the dvd ignoring cases...
  private String FindDVDFile(String filename)
  {
    String [] list = (hostname != null) ? sage.IOUtils.listServerFiles(dvddir) : dvddir.list();
    if(list==null)
    {
      if (sage.Sage.DBG) System.out.println("Unable to list files on dvd");
      return null;
    }
    for(int i=0; i<list.length; i++)
    {
      if(filename.compareToIgnoreCase(list[i])==0) return list[i];
    }
    return null;
  }

  public boolean open(String path, String hostname)
  {
    dvdroot=path;
    this.hostname = hostname;
    dvddir = new java.io.File(path); //+separator+"VIDEO_TS"
    String [] list = (hostname != null) ? sage.IOUtils.listServerFiles(dvddir) : dvddir.list();
    if(list==null)
    {
      if (sage.Sage.DBG) System.out.println("Unable to list files on dvd "+path); //+separator+"VIDEO_TS"
      return false;
    }
    if (sage.MiniDVDPlayer.DEBUG_MINIDVD)
    {
      for(int i=0; i<list.length; i++)
      {
        System.out.println(list[i]);
      }
    }
    return true;
  }

  public void close()
  {
    // TODO: verify if we need to do anything for those files...
    //if(dvddir!=null) dvddir.close();
  }

  public DVDSource openFile(int title, int type)
  {
    String filename = "";
    switch(type)
    {
      case DVD_TYPE_IFO:
        if(title==0)
        {
          filename = "VIDEO_TS.IFO";
        }
        else
        {
          filename = "VTS_"+ ((title<10) ? "0" : "") +""+ title + "_0.IFO";
        }
        break;
      case DVD_TYPE_BUP:
        if(title==0)
        {
          filename = "VIDEO_TS.BUP";
        }
        else
        {
          filename = "VTS_"+ ((title<10) ? "0" : "") +""+ title + "_0.BUP";
        }
        break;
      case DVD_TYPE_MENU:
        if(title==0)
        {
          filename = "VIDEO_TS.VOB";
        }
        else
        {
          filename = "VTS_"+ ((title<10) ? "0" : "") +""+ title + "_0.VOB";
        }
        break;
      case DVD_TYPE_TITLE:
        // Special case one file object for multiple segments...
        filename = "VTS_"+ ((title<10) ? "0" : "") +""+ title + "_";
        int titleNum = 1;
        String titleFilename;
        java.util.ArrayList titleNames = new java.util.ArrayList();
        while (true)
        {
          if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("Trying to open "+filename + titleNum + ".VOB");
          if ((titleFilename = FindDVDFile(filename + titleNum + ".VOB"))!=null)
          {
            titleNames.add(dvdroot + separator + titleFilename); //separator + "VIDEO_TS" +
          }
          else
            break;
          titleNum++;
        }
        if (titleNames.size() > 0)
          return new DVDMultiFile((String[]) titleNames.toArray(sage.Pooler.EMPTY_STRING_ARRAY), hostname);
        else
          return null;
    }
    /* FilenameFilter filter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return !name.startsWith(".");
        }
        };
        children = dir.list(filter); */

    if (sage.MiniDVDPlayer.DEBUG_MINIDVD) System.out.println("Trying to open "+filename);
    if((filename = FindDVDFile(filename))!=null)
    {
      return new DVDFile(dvdroot+separator+filename, hostname); //separator+"VIDEO_TS"+
    }
    return null;
  }
}
