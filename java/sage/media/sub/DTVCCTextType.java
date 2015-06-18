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
package sage.media.sub;

public enum DTVCCTextType {
  DIALOG,
  SPEAKER,
  ROBOT,
  LANGUAGE,
  VOICEOVER,
  AUDIO_TRANSLATION,
  SUBTITLE_TRANSLATION,
  VOICE_QUALITY,
  LYRICS,
  EFFECT,
  MUSICAL_SCORE,
  OATH,
  UNDEFINED0,
  UNDEFINED1,
  UNDEFINED2,
  INVISIBLE;

  public static DTVCCTextType from(int data) {
    switch (data) {
      case 0: return DIALOG;
      case 1: return SPEAKER;
      case 2: return ROBOT;
      case 3: return LANGUAGE;
      case 4: return VOICEOVER;
      case 5: return AUDIO_TRANSLATION;
      case 6: return SUBTITLE_TRANSLATION;
      case 7: return VOICE_QUALITY;
      case 8: return LYRICS;
      case 9: return EFFECT;
      case 10: return MUSICAL_SCORE;
      case 11: return OATH;
      case 12: return UNDEFINED0;
      case 13: return UNDEFINED1;
      case 14: return UNDEFINED2;
      case 15: return INVISIBLE;
    }
    return UNDEFINED0;
  }
}