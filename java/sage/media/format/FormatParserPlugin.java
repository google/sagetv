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
package sage.media.format;

/**
 * This class provides an interface for the creation of a plug to override
 * the built in file format detection.  This will allow the community to create
 * plugins that will allow SageTV to be able to recognize newer media formates
 * that the core is not currently able to handle. 
 * 
 * @author jvl711
 */
public interface FormatParserPlugin 
{

  /**
   * SageTV will pass a file to this method when it needs to know its format.  This 
   * could because it is the first time it saw the file, or because SageTV needed to
   * rediscover the file format because of some other trigger. The implementor should 
   * construct a ContainerFormat and provide information about the media file.
   * 
   * <pre>
   * 
   * For most media files you will need to do the following
   * 
   * Set the container format of the file with this method.  For example "MKV", "AVI"
   * <code>
   * ContainerFormat.setFormatName(formatName);
   * </code>
   * 
   * Set the duration of the file in milliseconds
   * <code>
   * ContainerFormat.setDuration(duration);
   * </code>
   * 
   * Set the total bitrate of the file
   * <code>
   * ContainerFormat.setBitrate((int)bitrate);
   * </code>
   * 
   * Create the associated stream object for each stream in the file
   * For video streams use VideoFormat
   * For audio streams use AudioFormat
   * For subtitles use SubpictureFormat
   * It is important that for each stream you set the setOrderIndex.  This should be 
   * the index that the stream exists in the file.  So normally the video stream would be
   * index 0, and your first audio stream would be index 1.  Your second audio stream would be
   * index 2 and so on.
   * <code>
   * ContainerFormat.setStreamFormats(streams);
   * </code>
   * 
   * </Pre>
   * If you return a container format with no streams, SageTV will discard your ContainerFormat and use it's own format
   * detector to attempt to determine what the files format is.
   * 
   * @param file Media file to determine the media format for
   * @return ContainerFormat populated with the format of the given file
   */
  public ContainerFormat parseFormat(java.io.File file);
    
}
