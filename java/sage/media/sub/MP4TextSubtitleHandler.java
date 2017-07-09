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

/**
 *
 * @author Narflex
 */
public class MP4TextSubtitleHandler extends SubtitleHandler
{

  /** Creates a new instance of MP4TextSubtitleHandler */
  public MP4TextSubtitleHandler(byte[] configInfo, boolean isTX3G)
  {
    super(null);
    this.isTX3G = isTX3G;
    processHeaderInfo(configInfo);
  }

  public void processHeaderInfo(byte[] configInfo)
  {
    // TODO: Get the real format information once we do stylized text
    if (SUB_DEBUG) System.out.println("MP4Text handler got config data");

    if (isTX3G)
    {
      // There's 30 bytes of data we don't care about (style info), and then possibly an atoms which we want to also ignore
      tx3gHeaderSize = 30;
      if (configInfo.length > 34)
      {
        int extraDataSize = ((configInfo[30] & 0xFF) << 24) | ((configInfo[31] & 0xFF) << 16) | ((configInfo[32] & 0xFF) << 8) | (configInfo[33] & 0xFF);
        if (tx3gHeaderSize + extraDataSize <= configInfo.length)
          tx3gHeaderSize += extraDataSize;
      }
    }
  }

  public void loadSubtitlesFromFiles(sage.MediaFile sourceFile)
  {
    throw new UnsupportedOperationException("RawSubtitleHandler cannot load subtitles from external files!");
  }

  protected boolean insertEntryForPostedInfo(long time, long dur, byte[] rawData)
  {
    String rawText;
    int sizeOffset = 0;

    subtitleLock.readLock().lock();

    try
    {
      if (subEntries != null && subEntries.isEmpty())
      {
        // Initialization data is 43 bytes; then 2 bytes for the string length...for regular MP4 text, it's variable for TX3G
        sizeOffset = isTX3G ? tx3gHeaderSize : 43;
      }
    }
    finally
    {
      subtitleLock.readLock().unlock();
    }

    if (rawData == null || rawData.length < sizeOffset + 2)
      return false;
    int textLength = ((rawData[sizeOffset] & 0xFF) << 8) + (rawData[sizeOffset + 1] & 0xFF);
    if (sizeOffset + 2 + textLength > rawData.length)
      return false;
    try
    {
      rawText = new String(rawData, sizeOffset + 2, textLength, sage.Sage.I18N_CHARSET);
    }
    catch (java.io.UnsupportedEncodingException uee)
    {
      rawText = new String(rawData, sizeOffset + 2, textLength);
    }
    return insertSubtitleEntry(new SubtitleEntry(rawText, time, dur));
  }

  private boolean isTX3G;
  private int tx3gHeaderSize;
}
