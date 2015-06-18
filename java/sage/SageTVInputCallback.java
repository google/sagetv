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

public interface SageTVInputCallback
{
  /*
   * SageTV Command IDs
		LEFT = 2;
		RIGHT = 3;
		UP = 4;
		DOWN = 5;

		PAUSE = 6;
		PLAY = 7;
		FF = 8;
		REW = 9;
		TIME_SCROLL = 10;
		CHANNEL_UP = 11;
		CHANNEL_DOWN = 12;
		VOLUME_UP = 13;
		VOLUME_DOWN = 14;
		TV = 15;
		FASTER = 16;
		SLOWER = 17;
		GUIDE = 18;
		POWER = 19;
		SELECT = 20;
		WATCHED = 21;
		RATE_UP = 22;
		RATE_DOWN = 23;
		INFO = 24;
		RECORD = 25;
		MUTE = 26;
		FULL_SCREEN = 27;
		HOME = 28;
		OPTIONS = 29;

		NUM0 = 30;
		NUM1 = 31;
		NUM2 = 32;
		NUM3 = 33;
		NUM4 = 34;
		NUM5 = 35;
		NUM6 = 36;
		NUM7 = 37;
		NUM8 = 38;
		NUM9 = 39;

		SEARCH = 40;
		SETUP = 41;
		LIBRARY = 42;

		POWER_ON = 43;
		POWER_OFF = 44;
		MUTE_ON = 45;
		MUTE_OFF = 46;

		AR_FILL = 47;
		AR_4X3 = 48;
		AR_16X9 = 49;
		AR_SOURCE = 50;

		VOLUME_UP2 = 51;
		VOLUME_DOWN2 = 52;
		CHANNEL_UP2 = 53;
		CHANNEL_DOWN2 = 54;
		PAGE_UP = 55;
		PAGE_DOWN = 56;
		PAGE_RIGHT = 57;
		PAGE_LEFT = 58;
		PLAY_PAUSE = 59;
		PREV_CHANNEL = 60;

		FF_2 = 61;
		REW_2 = 62;

		LIVE_TV = 63;

		DVD_REVERSE_PLAY = 64;
		DVD_CHAPTER_NEXT = 65;
		DVD_CHAPTER_PREV = 66;
		DVD_MENU = 67;
		DVD_TITLE_MENU = 68;
		DVD_RETURN = 69;
		DVD_SUBTITLE_CHANGE = 70;
		DVD_SUBTITLE_TOGGLE = 71;
		DVD_AUDIO_CHANGE = 72;
		DVD_ANGLE_CHANGE = 73;

		DVD = 74;

		BACK = 75;
		FORWARD = 76;

		CUSTOMIZE = 77;

		CUSTOM1 = 78;
		CUSTOM2 = 79;
		CUSTOM3 = 80;
		CUSTOM4 = 81;
		CUSTOM5 = 82;

		DELETE = 83;
		MUSIC = 84;
		SCHEDULE = 85;
		RECORDINGS = 86;
		PICTURE_LIBRARY = 87;
		VIDEO_LIBRARY = 88;

		STOP = 89;
   */

  /*
   * Call this method to send a SageTV Command to SageTV.
   * sageCommandID - ID code for the SageTV Command listed above.
   */
  public void recvCommand(int sageCommandID);

  /*
   * Call this method to send a SageTV Command to SageTV with a payload. The payload will be
   * accessible through the context of any Listener Widget that gets fired as result of this
   * through the variable named "EventPaylaods".
   *
   * sageCommandID - ID code for the SageTV Command listed above.
   * payload - the payload to pass to SageTV
   */
  public void recvCommand(int sageCommandID, String payload);

  /*
   * Call this method to send a SageTV Command to SageTV with a payload. The payloads will be
   * accessible through the context of any Listener Widget that gets fired as result of this
   * through the variable named "EventPaylaods".
   *
   * sageCommandID - ID code for the SageTV Command listed above.
   * payloads - the payload(s) to pass to SageTV
   */
  public void recvCommand(int sageCommandID, String[] payloads);

  /*
   * Call this method to send SageTV an infrared signal. Used for direct IR control of SageTV.
   *
   * infraredCode - the raw data for the infrared signal that uniquely identifies it
   */
  public void recvInfrared(byte[] infraredCode);

  /*
   * Call this method to send SageTV a keystroke. Used for sending keystrokes directly to SageTV regardless
   * of system focus.
   *
   * keyChar - the character for the representative keystroke, use zero if there's no corresponding character
   * keyCode - the keycode for the keystroke as defined in java.awt.event.KeyEvent.
   * keyModifiers - modifiers keys used such as Ctrl, Alt & Shift as defined in java.awt.event.InputEvent
   */
  public void recvKeystroke(char keyChar, int keyCode, int keyModifiers);
}
