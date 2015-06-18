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

public interface MediaPlayer extends java.awt.Transparency
{
  /**
   * When called the MediaPlayer should load the specified file.
   * @param majorTypeHint Indicates the type of content (major):
   * VIDEO = 1;
   * AUDIO = 2;
   * DVD = 4;
   * @param minorTypeHint Indicates the type of content (minor):
   * MPEG2_PS = 31;
   * MPEG2_TS = 32;
   * MP3 = 33;
   * MPEG1_PS = 36;
   * WAV = 38;
   * AVI = 39;
   * @param encodingHint A descriptive string for the format the content was recorded in, do not rely on this.
   * @param file The file to play
   * @param hostname null for local files, for remote files it's the IP hostname/address that the file is located on.
   * @param timeshifted true if the file is currently being recorded
   * @param bufferSize The size of the circular buffer used to record to the file being played back.
   */
  void load(byte majorTypeHint, byte minorTypeHint, String encodingHint,
      java.io.File file, String hostname, boolean timeshifted, long bufferSize) throws PlaybackException;

  /**
   * When called the MediaPlayer should load the specified file. This will only be called if canFastLoad just returned
   * true for the same parameters. This does not need to be implemented if canFastLoad always returns false.
   * fastLoad will be called without unloading/freeing the file that was previously loaded by the media player
   * @param majorTypeHint Indicates the type of content (major):
   * VIDEO = 1;
   * AUDIO = 2;
   * DVD = 4;
   * @param minorTypeHint Indicates the type of content (minor):
   * MPEG2_PS = 31;
   * MPEG2_TS = 32;
   * MP3 = 33;
   * MPEG1_PS = 36;
   * WAV = 38;
   * AVI = 39;
   * @param encodingHint A descriptive string for the format the content was recorded in, do not rely on this.
   * @param file The file to play
   * @param hostname null for local files, for remote files it's the IP hostname/address that the file is located on.
   * @param timeshifted true if the file is currently being recorded
   * @param bufferSize The size of the circular buffer used to record to the file being played back.
   * @param waitUntilDone if true the player should wait until the current file has finished playing before loading the specified one
   */
  void fastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint,
      java.io.File file, String hostname, boolean timeshifted, long bufferSize, boolean waitUntilDone) throws PlaybackException;
  /**
   * Returns true if the MediaPlayer is capable of performing a 'fastLoad' call with the specified paramters.
   * @param majorTypeHint Indicates the type of content (major):
   * VIDEO = 1;
   * AUDIO = 2;
   * DVD = 4;
   * @param minorTypeHint Indicates the type of content (minor):
   * MPEG2_PS = 31;
   * MPEG2_TS = 32;
   * MP3 = 33;
   * MPEG1_PS = 36;
   * WAV = 38;
   * AVI = 39;
   * @param encodingHint A descriptive string for the format the content was recorded in, do not rely on this.
   * @param file The file to play
   * @return true if the MediaPlayer supports the fastLoad call for the specified parameters
   */
  boolean canFastLoad(byte majorTypeHint, byte minorTypeHint, String encodingHint, java.io.File file);
  /**
   * When called the MediaPlayer should release all resources associated with it.
   * IMPORTANT: The MediaPlayer object may be re-used again with a call to load() after it is freed.
   */
  void free();
  /**
   * Only called when the file loaded was loaded as a timeshifted file. Indicates that recording to the file has stopped, and it will no longer grow in size.
   */
  void inactiveFile();

  /**
   * Called when the MediaPlayer should stop
   */
  void stop();
  /**
   * Returns the current file loaded by the MediaPlayer
   * @return the current file loaded by the MediaPlayer
   */
  java.io.File getFile();

  /**
   * Indicates the MediaPlayer is in an uninitialized state
   */
  int NO_STATE = 0;
  /**
   * Indicates the MediaPlayer has loaded a file and is ready for playback
   */
  int LOADED_STATE = 1;
  /**
   * The MediaPlayer is playing
   */
  int PLAY_STATE = 2;
  /**
   * The MediaPlayer is paused
   */
  int PAUSE_STATE = 3;
  /**
   * The MediaPlayer is stopped
   */
  int STOPPED_STATE = 4;
  /**
   * The MediaPlayer has encountered an end of stream
   */
  int EOS_STATE = 5;

  /**
   * Returns the state of the MediaPlayer
   * @return The state of the MediaPlayer, one of the _STATE constants.
   */
  int getState();
  /**
   * Returns the duration of the currently loaded file in milliseconds
   * @return the duration of the currently loaded file in milliseconds, or 0 if the duration is unknown
   */
  long getDurationMillis();
  /**
   * Returns the current playback position in the file
   * @return the current playback position in the file in milliseconds from the start
   */
  long getMediaTimeMillis();
  /**
   * Returns the current playback rate of the MediaPlayer
   * @return the current playback rate of the MediaPlayer, if paused return the rate if it were playing
   */
  float getPlaybackRate();

  /**
   * When called the MediaPlayer should pause
   * @return true if the MediaPlayer is no paused
   */
  boolean pause();
  /**
   * When called the MediaPlayer should play
   * @return true if the MediaPlayer is now playing
   */
  boolean play();
  /**
   * The MediaPlayer should seek to the specified time in the file.
   * @param seekTimeMillis The time to seek to in milliseconds.
   * @return The time the seek actually seeked to
   */
  long seek(long seekTimeMillis) throws PlaybackException;
  /**
   * When called the MediaPlayer should set the playback rate to the specified value
   * @param newRate The new playback rate
   * @return the playback rate that the MediaPlayer actually changed to
   */
  float setPlaybackRate(float newRate);
  /**
   * Frame stepping
   * @return true if the call succeeded
   * @param amount The amount of frames to step.
   */
  boolean frameStep(int amount);

  /**
   * Returns whether or not the MediaPlayer is muted
   * @return true if the MediaPlayer is muted
   */
  boolean getMute();
  /**
   * Sets the mute state of the MediaPlayer
   * @param x if true, the MediaPlayer should mute itself, if false it should unmute itself
   */
  void setMute(boolean x);

  /**
   * Returns the current playback volume of the MediaPlayer
   * @return the current playback volume of the MediaPlayer, between 0.0 and 1.0 inclusive
   */
  float getVolume();
  /**
   * Called to set the volume of the MediaPlayer
   * @param f the desired playback volume of the MediaPlayer, between 0.0 and 1.0 inclusive
   * @return the actual playback volume of the MediaPlayer, between 0.0 and 1.0 inclusive
   */
  float setVolume(float f);

  /**
   * Unused
   */
  int FRAME_STEP_FORWARD_CAP = 0x1;
  /**
   * Unused
   */
  int FRAME_STEP_BACKWARD_CAP = 0x2;
  /**
   * Unused
   */
  int PAUSE_CAP = 0x4;
  /**
   * Unused
   */
  int PLAYRATE_FAST_CAP = 0x8;
  /**
   * Unused
   */
  int PLAYRATE_SLOW_CAP = 0x10;
  /**
   * Unused
   */
  int PLAY_REV_CAP = 0x20;
  /**
   * Unused
   */
  int PLAYRATE_FAST_REV_CAP = 0x40;
  /**
   * Unused
   */
  int PLAYRATE_SLOW_REV_CAP = 0x80;
  /**
   * Unused
   */
  int SEEK_CAP = 0x100;
  /**
   * Unused
   */
  int TIMESHIFT_CAP = 0x200;
  /**
   * Indicates that a MediaPlayer plays back live streams so the playback time is irrelevnt
   */
  int LIVE_CAP = 0x400;

  /**
   * Not used
   */
  int getPlaybackCaps();

  /**
   * Returns the color key used by the MediaPlayer, or null if not supported
   * @return the color key used by the MediaPlayer, or null if not supported
   */
  java.awt.Color getColorKey();

  /**
   * Sets the positioning for the video playback region
   * @param videoSrcRect The source rectangle relative to the decoded video size in pixels
   * @param videoDestRect The destination rectangle relative to the UI size in pixels
   * @param hideCursor true if the mouse cursor should be displayed, false otherwise
   */
  void setVideoRectangles(java.awt.Rectangle videoSrcRect, java.awt.Rectangle videoDestRect, boolean hideCursor);
  /**
   * Returns the size in pixels of the decoded video data for the current file
   * @return the size in pixels of the decoded video data for the current file
   */
  java.awt.Dimension getVideoDimensions();

  /**
   * Disables Closed Captioning
   */
  int CC_DISABLED = 0;
  /**
   * Enables CC1 (Closed Captioning)
   */
  int CC_ENABLED_CAPTION1 = 1;
  /**
   * Enables CC2 (Closed Captioning)
   */
  int CC_ENABLED_CAPTION2 = 2;
  /**
   * Enables Text1/CC3 (Closed Captioning)
   */
  int CC_ENABLED_TEXT1 = 3;
  /**
   * Enables Text2/CC4 (Closed Captioning)
   */
  int CC_ENABLED_TEXT2 = 4;
  /**
   * Enables DTVCC (Closed Captioning)
   */
  int CC_ENABLED_DTV_BASE = 5;
  /**
   * Number of DTV CC services available. Max DTVCC state is BASE + COUNT
   */
  int CC_DTV_COUNT = 6;
  /**
   * Sets the closed captioning state for the MediaPlayer
   * @param ccState One of the corresponding CC_ENABLED constants
   * @return true if the call succeeded
   */
  boolean setClosedCaptioningState(int ccState);
  /**
   * Gets the current closed captioning state for the media player
   * @return the current closed captioning state for the media player, one of the CC_ENABLED constants
   */
  int getClosedCaptioningState();
}
