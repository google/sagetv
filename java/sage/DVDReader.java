/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sage;

public class DVDReader
{
  public  static final int DVD_CONTROL_MENU = 201; // 1 for title, 2 for root
  public  static final int DVD_CONTROL_TITLE_SET = 202;
  public  static final int DVD_CONTROL_CHAPTER_SET = 205;
  public  static final int DVD_CONTROL_CHAPTER_NEXT = 206;
  public  static final int DVD_CONTROL_CHAPTER_PREV = 207;
  public  static final int DVD_CONTROL_ACTIVATE_CURRENT = 208;
  public  static final int DVD_CONTROL_RETURN = 209;
  public  static final int DVD_CONTROL_BUTTON_NAV = 210; // 1up,2right,3down,4left
  public  static final int DVD_CONTROL_MOUSE_HOVER = 211;
  public  static final int DVD_CONTROL_MOUSE_CLICK = 212;
  public  static final int DVD_CONTROL_ANGLE_CHANGE = 213;
  public  static final int DVD_CONTROL_SUBTITLE_CHANGE = 214;
  public  static final int DVD_CONTROL_SUBTITLE_TOGGLE = 215;
  public  static final int DVD_CONTROL_AUDIO_CHANGE = 216;
  public  static final int DVD_CONTROL_UNPAUSE = 300;
  public  static final int DVD_CONTROL_UNEMPTY = 301;
  public  static final int DVD_CONTROL_SEEKTO = 302;

  // Higher 16 bits of the process return value contain the command
  public static final int DVD_PROCESS_DATA = 0; // Data for N blocks un work buffer
  public static final int DVD_PROCESS_PAUSE = 1; // Pause for X seconds 255=forever
  public static final int DVD_PROCESS_EMPTY = 2; // Player needss to wait until empty
  public static final int DVD_PROCESS_FLUSH = 3; // Nedd flushing
  public static final int DVD_PROCESS_CLUT = 4; // Update player clut
  public static final int DVD_PROCESS_HIGHLIGHT = 5; // Update player highlights
  public static final int DVD_PROCESS_AUDIOCHANNEL = 6; // Update player audio channel
  public static final int DVD_PROCESS_PICCHANNEL = 7; // Update player picture channel
  public static final int DVD_PROCESS_CELL = 8; // Entered new cell
  public static final int DVD_PROCESS_VTS = 9; // Entered new vts
  public static final int DVD_PROCESS_NAV = 10; // Navigation information
  public static final int DVD_PROCESS_RATE = 11; // New rate forced to 1


  private int dvdhandle=0;

  public DVDReader()
  {
    System.loadLibrary("DVDReader");
  }

  public synchronized boolean open(String path)
  {
    if(dvdhandle!=0) DVDclose(dvdhandle);
    System.out.println("Opening dvd "+path);
    dvdhandle = DVDopen(path);
    return dvdhandle!=0;
  }

  public synchronized void close()
  {
    if(dvdhandle!=0) DVDclose(dvdhandle);
    dvdhandle = 0;
  }

  public synchronized int process(byte buf[], int offset)
  {
    return DVDprocess(buf, offset);
  }

  public synchronized int read(byte buf[], int offset, int count)
  {
    int retcode;
    if(count<2048) return 0;
    switch((retcode = process(buf, offset))>>16)
    {
      case DVD_PROCESS_DATA:
        return (retcode&0xFFFF);
      case DVD_PROCESS_PAUSE:
      case DVD_PROCESS_EMPTY:
      case DVD_PROCESS_FLUSH:
      case DVD_PROCESS_CLUT:
      case DVD_PROCESS_HIGHLIGHT:
      case DVD_PROCESS_AUDIOCHANNEL:
      case DVD_PROCESS_PICCHANNEL:
      case DVD_PROCESS_CELL:
      case DVD_PROCESS_VTS:
      case DVD_PROCESS_NAV:
      default:
        System.out.println("process code "+(retcode>>16));
        return 0;
    }
  }

  public synchronized void unpause()
  {
    try {
      playControlEx(DVD_CONTROL_UNPAUSE,0,0);
    } catch(Exception e){}
  }

  public synchronized void unempty()
  {
    try {
      playControlEx(DVD_CONTROL_UNEMPTY,0,0);
    } catch(Exception e){}
  }

  native synchronized int DVDopen(String path);
  native synchronized void DVDclose(int dvdhandle);
  native synchronized int DVDprocess(byte buf[], int offset);
  native synchronized boolean playControlEx(int playCode, long param1, long param2) throws PlaybackException;
  native synchronized int getDVDTitle();
  native synchronized int getDVDTotalTitles();
  native synchronized int getDVDChapter();
  native synchronized int getDVDTotalChapters();
  native synchronized int getDVDDomain();
  native synchronized boolean areDVDButtonsVisible();
  native synchronized int getDVDAngle();
  native synchronized int getDVDTotalAngles();
  native synchronized String getDVDLanguage();
  native synchronized String[] getDVDAvailableLanguages();
  native synchronized String getDVDSubpicture();
  native synchronized String[] getDVDAvailableSubpictures();
}
