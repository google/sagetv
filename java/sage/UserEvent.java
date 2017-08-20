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

public class UserEvent
{
  public static final int LEFT = 2;
  public static final int RIGHT = 3;
  public static final int UP = 4;
  public static final int DOWN = 5;

  public static final int PAUSE = 6; // s
  public static final int PLAY = 7; // d
  public static final int FF = 8; // f
  public static final int REW = 9; // a
  public static final int TIME_SCROLL = 10; // g
  public static final int CHANNEL_UP = 11; // PgUp
  public static final int CHANNEL_DOWN = 12; // PgDn
  public static final int VOLUME_UP = 13; // r
  public static final int VOLUME_DOWN = 14; // e
  public static final int TV = 15; // v
  public static final int FASTER = 16; // m
  public static final int SLOWER = 17; // n
  public static final int GUIDE = 18; // x
  public static final int POWER = 19; // z
  public static final int SELECT = 20; // Enter
  public static final int WATCHED = 21; // w
  public static final int RATE_UP = 22; // k
  public static final int RATE_DOWN = 23; // j
  public static final int INFO = 24; // i
  public static final int RECORD = 25; // t
  public static final int MUTE = 26; //
  public static final int FULL_SCREEN = 27; // Ctrl-F
  public static final int HOME = 28; // home
  public static final int OPTIONS = 29; // o

  public static final int NUM0 = 30;
  public static final int NUM1 = 31;
  public static final int NUM2 = 32;
  public static final int NUM3 = 33;
  public static final int NUM4 = 34;
  public static final int NUM5 = 35;
  public static final int NUM6 = 36;
  public static final int NUM7 = 37;
  public static final int NUM8 = 38;
  public static final int NUM9 = 39;

  public static final int SEARCH = 40;
  public static final int SETUP = 41;
  public static final int LIBRARY = 42;

  public static final int POWER_ON = 43;
  public static final int POWER_OFF = 44;
  public static final int MUTE_ON = 45;
  public static final int MUTE_OFF = 46;

  public static final int AR_FILL = 47;
  public static final int AR_4X3 = 48;
  public static final int AR_16X9 = 49;
  public static final int AR_SOURCE = 50;

  public static final int VOLUME_UP2 = 51;
  public static final int VOLUME_DOWN2 = 52;
  public static final int CHANNEL_UP2 = 53;
  public static final int CHANNEL_DOWN2 = 54;
  public static final int PAGE_UP = 55;
  public static final int PAGE_DOWN = 56;
  public static final int PAGE_RIGHT = 57;
  public static final int PAGE_LEFT = 58;
  public static final int PLAY_PAUSE = 59;
  public static final int PREV_CHANNEL = 60;

  public static final int FF_2 = 61;
  public static final int REW_2 = 62;

  public static final int LIVE_TV = 63;

  public static final int DVD_REVERSE_PLAY = 64;
  public static final int DVD_CHAPTER_NEXT = 65;
  public static final int DVD_CHAPTER_PREV = 66;
  public static final int DVD_MENU = 67;
  public static final int DVD_TITLE_MENU = 68;
  public static final int DVD_RETURN = 69;
  public static final int DVD_SUBTITLE_CHANGE = 70;
  public static final int DVD_SUBTITLE_TOGGLE = 71;
  public static final int DVD_AUDIO_CHANGE = 72;
  public static final int DVD_ANGLE_CHANGE = 73;

  public static final int DVD = 74;

  public static final int BACK = 75;
  public static final int FORWARD = 76;

  public static final int CUSTOMIZE = 77;

  public static final int CUSTOM1 = 78;
  public static final int CUSTOM2 = 79;
  public static final int CUSTOM3 = 80;
  public static final int CUSTOM4 = 81;
  public static final int CUSTOM5 = 82;

  public static final int DELETE = 83;
  public static final int MUSIC = 84;
  public static final int SCHEDULE = 85;
  public static final int RECORDINGS = 86;
  public static final int PICTURE_LIBRARY = 87;
  public static final int VIDEO_LIBRARY = 88;

  public static final int STOP = 89;
  public static final int EJECT = 90;
  public static final int STOP_EJECT = 91;
  public static final int INPUT = 92;

  public static final int SMOOTH_FF = 93;
  public static final int SMOOTH_REW = 94;

  public static final int DASH = 95;

  public static final int AR_TOGGLE = 96;

  public static final int FULL_SCREEN_ON = 97;
  public static final int FULL_SCREEN_OFF = 98;

  public static final int RIGHT_FF = 99;
  public static final int LEFT_REW = 100;
  public static final int UP_VOL_UP = 101;
  public static final int DOWN_VOL_DOWN = 102;

  public static final int ONLINE = 103;
  public static final int VIDEO_OUTPUT = 104;

  public static final int SCROLL_LEFT = 105;
  public static final int SCROLL_RIGHT = 106;
  public static final int SCROLL_UP = 107;
  public static final int SCROLL_DOWN = 108;

  public static final int ANYTHING = 109;

  public static final int MIN_EVT_ID = 2;
  public static final int MAX_EVT_ID = 108;

  private static final String[] UENAMES = { "?", "?", "left", "right",
    "up", "down", "pause", "play", "ff", "rew", "time_scroll",
    "ch_up", "ch_down", "vol_up", "vol_down", "tv",
    "faster", "slower", "guide", "power", "select", "watched", "like", "dont_like",
    "info", "record", "mute", "full_screen", "home", "options", "0", "1", "2", "3", "4",
    "5", "6", "7", "8", "9", "search", "setup", "library", "power_on", "power_off",
    "mute_on", "mute_off", "ar_fill", "ar_4x3", "ar_16x9", "ar_source", "vol_up2", "vol_down2",
    "ch_up2", "ch_down2", "page_up", "page_down", "page_right", "page_left", "play_pause",
    "prev_channel", "ff_2", "rew_2", "live_tv", "dvd_reverse", "dvd_chapter_up", "dvd_chapter_down",
    "dvd_menu", "dvd_title_menu", "dvd_return", "dvd_subtitle_change", "dvd_subtitle_toggle",
    "dvd_audio_change", "dvd_angle_change", "dvd", "back", "forward", "customize",
    "custom1", "custom2", "custom3", "custom4", "custom5", "delete", "music", "schedule", "recordings",
    "picture_library", "video_library", "stop", "eject", "stop_eject", "input", "smooth_ff", "smooth_rew", "dash", "ar_toggle",
    "full_screen_on", "full_screen_off", "right_ff", "left_rew", "up_vol_up", "down_vol_down", "online", "video_output",
    "scroll_left", "scroll_right", "scroll_up", "scroll_down", "anything", };

  private static final String[] TRANSLATION_UENAMES = { "?", "?", "Command_Left", "Command_Right",
    "Command_Up", "Command_Down", "Command_Pause", "Command_Play", "Command_Skip_Fwd_Page_Right", "Command_Skip_Bkwd_Page_Left", "Command_Time_Scroll",
    "Command_Channel_Up_Page_Up", "Command_Channel_Down_Page_Down", "Command_Volume_Up", "Command_Volume_Down", "Command_TV",
    "Command_Play_Faster", "Command_Play_Slower", "Command_Guide", "Command_Power", "Command_Select", "Command_Watched", "Command_Favorite", "Command_Dont_Like",
    "Command_Info", "Command_Record", "Command_Mute", "Command_Full_Screen", "Command_Home", "Command_Options", "Command_Num_0", "Command_Num_1",
    "Command_Num_2", "Command_Num_3", "Command_Num_4", "Command_Num_5", "Command_Num_6", "Command_Num_7",
    "Command_Num_8", "Command_Num_9", "Command_Search", "Command_Setup", "Command_Library", "Command_Power_On", "Command_Power_Off",
    "Command_Mute_On", "Command_Mute_Off", "Command_Aspect_Ratio_Fill", "Command_Aspect_Ratio_4x3", "Command_Aspect_Ratio_16x9", "Command_Aspect_Ratio_Source",
    "Command_Right_Volume_Up", "Command_Left_Volume_Down",
    "Command_Up_Channel_Up", "Command_Down_Channel_Down", "Command_Page_Up", "Command_Page_Down", "Command_Page_Right", "Command_Page_Left", "Command_Play_Pause",
    "Command_Previous_Channel", "Command_Skip_Fwd_2", "Command_Skip_Bkwd_2", "Command_Live_TV", "Command_DVD_Reverse_Play", "Command_DVD_Next_Chapter",
    "Command_DVD_Prev_Chapter", "Command_DVD_Menu", "Command_DVD_Title_Menu", "Command_DVD_Return", "Command_DVD_Subtitle_Change", "Command_DVD_Subtitle_Toggle",
    "Command_DVD_Audio_Change", "Command_DVD_Angle_Change", "Command_DVD", "Command_Back", "Command_Forward", "Command_Customize",
    "Command_Custom1", "Command_Custom2", "Command_Custom3", "Command_Custom4", "Command_Custom5", "Command_Delete", "Command_Music_Jukebox",
    "Command_Recording_Schedule", "Command_SageTV_Recording",
    "Command_Picture_Library", "Command_Video_Library", "Command_Stop", "Command_Eject", "Command_Stop_Eject", "Command_Input", "Command_Smooth_FF", "Command_Smooth_Rew",
    "-", "Command_Aspect_Ratio_Toggle", "Command_Full_Screen_On", "Command_Full_Screen_Off",
    "Command_Right_Skip_Fwd", "Command_Left_Skip_Bkwd", "Command_Up_Volume_Up", "Command_Down_Volume_Down", "Command_Online",
    "Command_Video_Output", "Command_Scroll_Left", "Command_Scroll_Right", "Command_Scroll_Up", "Command_Scroll_Down", "Command_Anything", };

  public static String[] PRETTY_UENAMES = { "?", "?", "Left", "Right",
    "Up", "Down", "Pause", "Play", "Skip Fwd/Page Right", "Skip Bkwd/Page Left", "Time Scroll",
    "Channel Up/Page Up", "Channel Down/Page Down", "Volume Up", "Volume Down", "TV",
    "Play Faster", "Play Slower", "Guide", "Power", "Select", "Watched", "Favorite", "Don't Like",
    "Info", "Record", "Mute", "Full Screen", "Home", "Options", "Num 0", "Num 1", "Num 2",
    "Num 3", "Num 4", "Num 5", "Num 6", "Num 7", "Num 8", "Num 9", "Search", "Setup", "Library",
    "Power On", "Power Off", "Mute On", "Mute Off", "Aspect Ratio Fill", "Aspect Ratio 4x3",
    "Aspect Ratio 16x9", "Aspect Ratio Source", "Right/Volume Up", "Left/Volume Down",
    "Up/Channel Up", "Down/Channel Down", "Page Up", "Page Down", "Page Right", "Page Left", "Play/Pause",
    "Previous Channel", "Skip Fwd #2", "Skip Bkwd #2", "Live TV", "DVD Reverse Play", "DVD Next Chapter", "DVD Prev Chapter",
    "DVD Menu", "DVD Title Menu", "DVD Return", "DVD Subtitle Change", "DVD Subtitle Toggle",
    "DVD Audio Change", "DVD Angle Change", "DVD", "Back", "Forward", "Customize",
    "Custom1", "Custom2", "Custom3", "Custom4", "Custom5", "Delete", "Music Jukebox", "Recording Schedule",
    "SageTV Recordings", "Picture Library", "Video Library", "Stop", "Eject", "Stop/Eject", "Input",
    "Smooth Fast Forward", "Smooth Rewind", "-", "Aspect Ratio Toggle", "Full Screen On", "Full Screen Off",
    "Right/Skip Fwd", "Left/Skip Bkwd", "Up/Volume Up", "Down/Volume Down", "Online", "Video Output",
    "Scroll Left", "Scroll Right", "Scroll Up", "Scroll Down", "Anything", };

  private static final java.util.Map nameCodeLookup;
  static
  {
    nameCodeLookup = new java.util.HashMap();
    for (int i = 0; i < UENAMES.length; i++)
      nameCodeLookup.put(UENAMES[i].toLowerCase(), new Integer(i));
    for (int i = 0; i < PRETTY_UENAMES.length; i++)
      nameCodeLookup.put(PRETTY_UENAMES[i].toLowerCase(), new Integer(i));
  }

  public static void updateNameMaps()
  {
    for (int i = 2; i < PRETTY_UENAMES.length; i++)
    {
      PRETTY_UENAMES[i] = Sage.rez(TRANSLATION_UENAMES[i]);
      nameCodeLookup.put(PRETTY_UENAMES[i].toLowerCase(), new Integer(i));
    }
  }

  public static String getEvtName(int id)
  {
    return (id < MIN_EVT_ID) || (id > MAX_EVT_ID) ? "" : UENAMES[id];
  }
  public static int getEvtCodeForName(String s)
  {
    if (s == null) return 0;
    Integer i = (Integer) nameCodeLookup.get(s.toLowerCase());
    if (i != null)
      return i.intValue();
    else
      return 0;
  }

  public static String getPrettyEvtName(int id)
  {
    return (id < MIN_EVT_ID) || (id > MAX_EVT_ID) ? "" : PRETTY_UENAMES[id];
  }

  public static long[] getDefaultIRCodes(int id)
  {
    if (Sage.LINUX_OS)
    {
      // PVR150
      switch (id)
      {
        case NUM0:
          return new long[] { -8704};
        case NUM1:
          return new long[] { -8700};
        case NUM2:
          return new long[] { -8696};
        case NUM3:
          return new long[] { -8692};
        case NUM4:
          return new long[] { -8688};
        case NUM5:
          return new long[] { -8684};
        case NUM6:
          return new long[] { -8680};
        case NUM7:
          return new long[] { -8676};
        case NUM8:
          return new long[] { -8672};
        case NUM9:
          return new long[] { -8668};
        case BACK:
          return new long[] { -8580};
        case CHANNEL_DOWN:
          return new long[] { -8572};
        case CHANNEL_UP:
          return new long[] { -8576};
        case RATE_DOWN:
          return new long[] { -8660};
        case DOWN:
          return new long[] { -8620};
        case FF:
          return new long[] { -8496};
        case FF_2:
          return new long[] { -8584};
        case GUIDE:
          return new long[] { -8596};
        case HOME:
          return new long[] { -8468};
        case INFO:
          return new long[] { -8652};
        case LEFT:
          return new long[] { -8616};
        case RATE_UP:
          return new long[] { -8520};
        case MUSIC:
          return new long[] { -8604};
        case MUTE:
          return new long[] { -8644};
        case OPTIONS:
          return new long[] { -8480};
        case PAUSE:
          return new long[] { -8512};
        case PICTURE_LIBRARY:
          return new long[] { -8600};
        case PLAY:
          return new long[] { -8492};
        case POWER:
          return new long[] { -8460};
        case PREV_CHANNEL:
          return new long[] { -8632};
        case RECORD:
          return new long[] { -8484};
        case RECORDINGS:
          return new long[] { -8608};
        case REW:
          return new long[] { -8504};
        case REW_2:
          return new long[] { -8560};
        case RIGHT:
          return new long[] { -8612};
        case SELECT:
          return new long[] { -8556};
        case STOP:
          return new long[] { -8488};
        case TV:
          return new long[] { -8592};
        case UP:
          return new long[] { -8624};
        case VOLUME_DOWN:
          return new long[] { -8636};
        case VOLUME_UP:
          return new long[] { -8640};
        case WATCHED:
          return new long[] { -8540};
      }
    }
    return new long[0];
  }

  public static int getNumCode(int ueCode)
  {
    if (ueCode < NUM0 || ueCode > NUM9) return -1;
    else return ueCode - NUM0;
  }

  public UserEvent(long inWhen, int inType, long inIR)
  {
    this(inWhen, inType, inIR, -1, -1, (char)0);
  }
  public UserEvent(long inWhen, int inType, long inIR, int inKeyCode, int inKeyMods, char inKeyChar)
  {
    when = inWhen;
    type = inType;
    irCode = inIR;
    keyCode = inKeyCode;
    keyModifiers = inKeyMods;
    keyChar = inKeyChar;
  }

  public long getWhen() { return when; }

  public int getType()
  {
    return type;
  }

  public void setPayloads(Object x) { payloads = x; }
  public Object getPayloads() { return payloads; }

  public long getIRCode() { return irCode; }
  public int getKeyCode() { return keyCode; }
  public int getKeyModifiers() { return keyModifiers; }
  public char getKeyChar() { return keyChar; }

  public String toString()
  {
    String typeString = "?";
    if (type >= 0 && type < UENAMES.length) typeString = UENAMES[type];
    return "UserEvent[" + typeString + ']';
  }

  public boolean isIR() { return irCode != -1; }
  public boolean isKB() { return keyCode > 0 || keyChar > 0; }
  public boolean isDirectionalType()
  {
    return type == LEFT || type == UP || type == DOWN || type == RIGHT || type == VOLUME_UP2 ||
        type == VOLUME_DOWN2 || type == CHANNEL_UP2 || type == CHANNEL_DOWN2 ||
        type == LEFT_REW || type == RIGHT_FF || type == UP_VOL_UP || type == DOWN_VOL_DOWN;
  }
  public static boolean isUpEvent(int type)
  {
    return type == UP || type == CHANNEL_UP2 || type == UP_VOL_UP;
  }
  public static boolean isDownEvent(int type)
  {
    return type == DOWN || type == CHANNEL_DOWN2 || type == DOWN_VOL_DOWN;
  }
  public static boolean isLeftEvent(int type)
  {
    return type == LEFT || type == VOLUME_DOWN2 || type == LEFT_REW;
  }
  public static boolean isRightEvent(int type)
  {
    return type == RIGHT || type == VOLUME_UP2 || type == RIGHT_FF;
  }

  public int getSecondaryType() { return getSecondaryType(type); }
  public static int getSecondaryType(int theType)
  {
    switch (theType)
    {
      case FF:
        return PAGE_RIGHT;
      case REW:
        return PAGE_LEFT;
      case CHANNEL_UP:
        return PAGE_UP;
      case CHANNEL_DOWN:
        return PAGE_DOWN;
      case VOLUME_UP2:
        return RIGHT;
      case VOLUME_DOWN2:
        return LEFT;
      case CHANNEL_UP2:
        return UP;
      case CHANNEL_DOWN2:
        return DOWN;
      case STOP_EJECT:
        return STOP;
      case LEFT_REW:
        return LEFT;
      case RIGHT_FF:
        return RIGHT;
      case UP_VOL_UP:
        return UP;
      case DOWN_VOL_DOWN:
        return DOWN;
      default:
        return 0;
    }
  }
  public int getTernaryType() { return getTernaryType(type); }
  public static int getTernaryType(int theType)
  {
    switch (theType)
    {
      case VOLUME_UP2:
        return VOLUME_UP;
      case VOLUME_DOWN2:
        return VOLUME_DOWN;
      case CHANNEL_UP2:
        return CHANNEL_UP;
      case CHANNEL_DOWN2:
        return CHANNEL_DOWN;
      case STOP_EJECT:
        return EJECT;
      case LEFT_REW:
        return REW;
      case RIGHT_FF:
        return FF;
      case UP_VOL_UP:
        return VOLUME_UP;
      case DOWN_VOL_DOWN:
        return VOLUME_DOWN;
      default:
        return 0;
    }
  }

  public boolean equals(Object o)
  {
    if (o instanceof UserEvent)
    {
      UserEvent ue = (UserEvent) o;
      return (type == ue.type) && (when == ue.when) && (irCode == ue.irCode) && (keyCode == ue.keyCode) &&
          (keyModifiers == ue.keyModifiers) && (keyChar == ue.keyChar);
    }
    return false;
  }

  public boolean isDiscardable()
  {
    return discardable;
  }

  public void setDiscardable(boolean x)
  {
    discardable = x;
  }

  private int type;
  private long when;
  private long irCode;
  private int keyCode;
  private int keyModifiers;
  private char keyChar;
  private Object payloads; // extra data for extensibility purposes
  private boolean discardable = true;
}
