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

// This interface is used to wrap the FFMPEG transcoder (or any other arbitrary transcoding engine)
public interface TranscodeEngine
{
  public void setSourceFile(String server, java.io.File theFile);
  public void startTranscode() throws java.io.IOException;
  public void stopTranscode();
  public void pauseTranscode();
  // Setting the output file to null means its in streaming mode
  public void setOutputFile(java.io.File theFile);
  public void setEnableOutputBuffering(boolean x);
  public void seekToTime(long milliSeekTime) throws java.io.IOException;
  public void seekToPosition(long bytePos) throws java.io.IOException;
  public void setTranscodeFormat(String s, sage.media.format.ContainerFormat sourceFormat);
  public void setTranscodeFormat(sage.media.format.ContainerFormat sourceFormat, sage.media.format.ContainerFormat destFormat);
  public boolean isTranscodeDone();
  public boolean isTranscoding();
  public long getVirtualTranscodeSize(); // the length of the transcoded source in bytes
  public long getVirtualReadPosition();
  public long getAvailableTranscodeBytes();
  public void sendTranscodeOutputToChannel(long offset, long length, java.nio.channels.WritableByteChannel chan) throws java.io.IOException;
  public void readFullyTranscodedData(byte[] buf, int inOffset, int inLength) throws java.io.IOException;
  public void readFullyTranscodedData(java.nio.ByteBuffer buf) throws java.io.IOException;
  public void setActiveFile(boolean x);
}
