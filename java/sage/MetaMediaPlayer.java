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

/**
 * This interface is for MediaPlayer implementations which utlize other MediaPlayers underneath them
 * to abstract out playing content that is more than just a simple file or sequence of file segments (since MediaFile already supports that).
 * Example uses are for BluRay ISO/folder playback or for SMIL playback or for SWF implementations w/ media players inside of them.
 * We'll also expand this later to have the ability to overlay graphics on top of the current media (although
 * we may want to just put that in another interface since it really isn't the same as what this one is for)
 *
 * @author Narflex
 */
public interface MetaMediaPlayer extends MediaPlayer
{
  // Not sure yet exactly what we'll need in here; but something for handling the VideoFrame.getMediaPlayerForMediaFile-type calls
  // will be needed
  public MediaPlayer getCurrMediaPlayer();

  // Since we have multiple files we're managing we need to know which one is now inactive
  public void inactiveFile(String inactiveFilename);
}
