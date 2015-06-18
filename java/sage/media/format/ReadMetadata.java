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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import sage.media.exif.imaging.ImageMetadataReader;
import sage.media.exif.imaging.ImageProcessingException;
import sage.media.exif.imaging.jpeg.JpegMetadataReader;
import sage.media.exif.imaging.jpeg.JpegSegmentData;
import sage.media.exif.imaging.jpeg.JpegSegmentReader;
import sage.media.exif.metadata.Directory;
import sage.media.exif.metadata.Metadata;
import sage.media.exif.metadata.MetadataException;
import sage.media.exif.metadata.exif.CanonMakernoteDirectory;
import sage.media.exif.metadata.exif.CasioType1MakernoteDirectory;
import sage.media.exif.metadata.exif.CasioType2MakernoteDirectory;
import sage.media.exif.metadata.exif.ExifDescriptor;
import sage.media.exif.metadata.exif.ExifDirectory;
import sage.media.exif.metadata.exif.ExifReader;
import sage.media.exif.metadata.exif.FujifilmMakernoteDirectory;
import sage.media.exif.metadata.exif.GpsDirectory;
import sage.media.exif.metadata.exif.NikonType1MakernoteDirectory;
import sage.media.exif.metadata.exif.NikonType2MakernoteDirectory;
import sage.media.exif.metadata.iptc.IptcDirectory;
import sage.media.exif.metadata.jpeg.JpegCommentDirectory;
import sage.media.exif.metadata.jpeg.JpegDirectory;


/**
 * Class to read EXIF and IPTC metadata from image files, and return a sensible
 * subset of that data to a caller.
 *
 * @author Niel Markwick
 *
 */
public class ReadMetadata {

  static private final boolean DEBUG_OUTPUT = sage.Sage.getBoolean("debug_exif_parser", false);

  private final Metadata metadata;
  private final long thumbnailSize;
  private final long thumbnailFileOffset;
  private final int[] thumbnailDimensions;

  private List metadataList = null;
  private Map metadataMap = null;

  public final static String KEY_CAMERA_NAME = "Camera";
  public final static String KEY_SHUTTER_SPEED = "Shutter Speed";
  public final static String KEY_APERTURE = "Aperture";
  public final static String KEY_FLASH = "Flash";

  /**
   * Combined comments from: EXIF.user_comment, EXIF.win_comment, IPTC.comment, JPEG.comment
   */
  public final static String KEY_COMBINED_COMMENTS = "Comments";

  /**
   * Combined title from EXIF.win_title, IPTC.Headline
   */
  public final static String KEY_COMBINED_TITLE = "Title";
  /**
   * Combined title from EXIF.win_subject, IPTC.Caption
   */
  public final static String KEY_COMBINED_SUBJECT = "Title";

  /**
   * Combined keywords from EXIF.win_keywords, IPTC.keywords
   */
  public final static String KEY_COMBINED_KEYWORDS = "Keywords";

  /**
   * Combined authors from EXIF.artist EXIF.win_author, IPTC.by_line, IPTC.writer
   */
  public final static String KEY_COMBINED_AUTHORS = "Author";


  public final static String KEY_IMAGE_DIMENSIONS = "Dimensions";

  public final static String KEY_GPS_POSITION = "Gps Position";

  /**
   * Read metadata from a given image file.
   * <p>
   * Uses magic numbers to determine file type <br>
   * Only JPG files are supported at present
   * <p>
   * Warnings are written to System.err
   *
   * @param imageFile --
   *            file to parse
   * @throws sage.media.exif.imaging.ImageProcessingException
   *             for any parsing errors
   * @throws java.io.FileNotFoundException
   *             if the file is not found.
   */
  public ReadMetadata(File imageFile) throws ImageProcessingException,
  FileNotFoundException {

    if (!imageFile.canRead()) {
      throw new FileNotFoundException(imageFile.toString());
    }

    if (DEBUG_OUTPUT) {
      System.err.println("Reading " + imageFile.toString());
    }

    // read file manually instead of using ImageMetadataReader so that
    // the file offsets of the EXIF data can be obtained for JPG files
    // so that the thumbnail size/offset can be returned.
    BufferedInputStream inputStream;
    try {
      inputStream = new BufferedInputStream(
          new FileInputStream(imageFile));
    } catch (FileNotFoundException e) {
      throw new ImageProcessingException("File not found: "
          + imageFile.getPath(), e);
    }

    // determine file type
    int magicNumber = ImageMetadataReader.readMagicNumber(inputStream);
    if (magicNumber == ImageMetadataReader.JPEG_FILE_MAGIC_NUMBER) {

      // JPG file: extract metadata (keeping segment info for further use)

      JpegSegmentReader segmentReader = new JpegSegmentReader(inputStream);
      metadata = JpegMetadataReader
          .extractMetadataFromJpegSegmentReader(segmentReader);

      // if we have an EXIF directory, store thumbnail size and offset in
      // file.
      if (metadata.containsDirectory(ExifDirectory.class)) {
        ExifDirectory exif = (ExifDirectory) metadata
            .getDirectory(ExifDirectory.class);
        if (exif.containsTag(ExifDirectory.TAG_THUMBNAIL_LENGTH)
            && exif.containsTag(ExifDirectory.TAG_THUMBNAIL_OFFSET)) {

          // find exif data in JPEG segments
          // handle possibilty of multiple app1 segments
          // APP1 may contain EXIF or XMP metadata
          int numApp1Segments = segmentReader
              .getSegmentCount(JpegSegmentReader.SEGMENT_APP1);
          // iterate backwards so that the first EXIF segment
          // overrides the later
          // ones...
          long exifFileOffset = -1;
          long offset = -1;
          for (int segNum = numApp1Segments - 1; segNum >= 0; segNum--) {
            JpegSegmentData segment = segmentReader
                .getSegmentData();

            byte[] segmentBytes = segment.getSegment(
                JpegSegmentReader.SEGMENT_APP1, segNum);
            if (ExifReader.isExifSegment(segmentBytes)) {
              exifFileOffset = segment.getSegmentOffset(
                  JpegSegmentReader.SEGMENT_APP1, segNum);
              break;
            }
          }
          if (exifFileOffset >= 0) {
            try {
              long thumbOffset = exif
                  .getLong(ExifDirectory.TAG_THUMBNAIL_OFFSET);
              offset = exifFileOffset
                  + ExifReader.TIFF_HEADER_START_OFFSET
                  + thumbOffset;
            } catch (MetadataException e) {
              offset = -1;
            }
          }
          long length = -1;
          try {
            length = exif
                .getLong(ExifDirectory.TAG_THUMBNAIL_LENGTH);

            // no thumbnail -- zero length
            if (length == 0)
              length = -1;
          } catch (MetadataException e) {
            length = -1;
          }
          if (offset > 0 && length > 0) {
            thumbnailFileOffset = offset;
            thumbnailSize = length;
          } else {
            thumbnailFileOffset = -1;
            thumbnailSize = -1;
          }

          if (!hasJpgThumbnail()) {
            thumbnailDimensions = null;
          } else {
            // Ok, so we have a JPEG thumbnail
            // -- read metadata for the thumbnail itself so that we can get the thumbnail dimensions

            Metadata thumbMetadata = JpegMetadataReader
                .extractMetadataFromJpegSegmentReader(new JpegSegmentReader(
                    getThumbnailBytes()));

            // try getting thumbnail dimensions from JPG
            if (thumbMetadata
                .containsDirectory(JpegDirectory.class)) {
              JpegDirectory jpgDir = (JpegDirectory) thumbMetadata
                  .getDirectory(JpegDirectory.class);
              if (jpgDir != null
                  && jpgDir
                  .containsTag(JpegDirectory.TAG_JPEG_IMAGE_WIDTH)
                  && jpgDir
                  .containsTag(JpegDirectory.TAG_JPEG_IMAGE_HEIGHT)) {

                int[] dimensions = null;
                try {
                  int height = jpgDir
                      .getInt(JpegDirectory.TAG_JPEG_IMAGE_HEIGHT);
                  int width = jpgDir
                      .getInt(JpegDirectory.TAG_JPEG_IMAGE_WIDTH);
                  if (width > 0 && height > 0)
                    dimensions = new int[] { width, height };
                } catch (MetadataException e) {
                  System.out
                  .println("Error parsing JPG dimensions metadata "
                      + e.toString());
                }
                thumbnailDimensions = dimensions;
              } else {
                thumbnailDimensions = null;
              }
            } else {
              thumbnailDimensions = null;
            }
          }

        } else {
          /* no thumbnail tags */
          thumbnailFileOffset = -1;
          thumbnailSize = -1;
          thumbnailDimensions = null;
        }
      } else {
        /* no EXIF metadata */
        thumbnailFileOffset = -1;
        thumbnailSize = -1;
        thumbnailDimensions = null;
      }
    } else {
      // TODO: handle TIFF image formats
      throw new ImageProcessingException("File format not supported: "
          + imageFile.getPath());
    }

    if (DEBUG_OUTPUT) {
      // report errors from parsing each directory as warnings to System.err
      Iterator dirIt = metadata.getDirectoryIterator();
      while (dirIt.hasNext()) {
        Directory dir = (Directory) dirIt.next();
        System.err.println("Found dir " + dir.getName());
        if (dir.hasErrors()) {
          for (Iterator errIt = dir.getErrors(); errIt.hasNext();) {
            String error = (String) errIt.next();
            System.err.println("Warning when parsing file "
                + imageFile.toString() + " in metadata directory: "
                + dir.getName() + ": " + error);
          }
        }
      }
    }
  }

  /**
   * Performs checks to determine if the image has a JPG thumbnail in the EXIF
   * data.
   * <p>
   * The EXIF TAG_COMPRESION , and the magic number at the beginning of the
   * thumbnail bytes are used to verify that the thumb is in JPG format
   *
   * @return true if embedded EXIF thumbnail is present and is in JPG format
   */
  public boolean hasJpgThumbnail() {

    if (getThumbnailType() != ExifDirectory.COMPRESSION_JPEG)
      return false;

    // final check -- check magic number at start of data
    byte[] thumbData;
    try {
      ExifDirectory exif = (ExifDirectory) metadata
          .getDirectory(ExifDirectory.class);
      thumbData = exif.getThumbnailData();
    } catch (MetadataException e) {
      return false;
    }

    if (thumbData.length > 2) {
      int magicNumber;
      magicNumber = (thumbData[0] & 0xFF) << 8;
      magicNumber |= (thumbData[1] & 0xFF);
      if (magicNumber == ImageMetadataReader.JPEG_FILE_MAGIC_NUMBER)
        return true;
    }
    return false;
  }

  /**
   * Gets type of embedded EXIF thumbnail.
   *
   * @return value of EXIF Image type (see
   *         {@link ExifDirectory#TAG_COMPRESSION}) or -1 if no thumbnail is
   *         present
   */
  public int getThumbnailType() {

    if (!metadata.containsDirectory(ExifDirectory.class))
      return -1;
    ExifDirectory exif = (ExifDirectory) metadata
        .getDirectory(ExifDirectory.class);
    if (!exif.containsThumbnail())
      return -1;
    if (!exif.containsTag(ExifDirectory.TAG_COMPRESSION))
      return -1;
    try {
      if (exif.getThumbnailData().length == 0)
        return -1;
      return exif.getInt(ExifDirectory.TAG_COMPRESSION);
    } catch (MetadataException e) {
      return -1;
    }
  }

  /**
   * Get the pixel dimensions of the image's JPG thumbnail.
   *
   * This reads the JPG metatdata for the thumbnail image, so it will only
   * work for JPG thumbnails.
   *
   * This function can be used to check that a thumbnail is sufficiently large
   * to be able to be used.
   *
   * @return int[2] containing width and height of the thumbnail image in
   *         pixels, or null if no JPG thumbnail is present
   */
  public int[] getThumbnailDimensions() {
    // return copy of internal array
    if (thumbnailDimensions!=null)
      return (int[])thumbnailDimensions.clone();
    return null;
  }

  /**
   * Get Date/Time image was generated from image metadata.
   *
   * @return Date of image generation, if present.
   * @throws MetadataException
   *             if EXIF date/time not preset, or is in an incorrect format.
   */
  public Date getImageDate() throws MetadataException {
    if (!metadata.containsDirectory(ExifDirectory.class))
      throw new MetadataException("Image does not contain EXIF data");
    ExifDirectory exif = (ExifDirectory) metadata
        .getDirectory(ExifDirectory.class);
    if (exif.containsTag(ExifDirectory.TAG_DATETIME_ORIGINAL))
      return exif.getDate(ExifDirectory.TAG_DATETIME_ORIGINAL);
    else if (exif.containsTag(ExifDirectory.TAG_DATETIME))
      return exif.getDate(ExifDirectory.TAG_DATETIME);
    else if (exif.containsTag(ExifDirectory.TAG_DATETIME_DIGITIZED))
      return exif.getDate(ExifDirectory.TAG_DATETIME_DIGITIZED);
    else
      throw new MetadataException("Image does not contain an EXIF date");
  }

  /**
   * Get file offset of raw thumbnail data
   *
   * @return offset or -1 if thumbnail does not exist
   */
  public long getThumbnailFileOffset() {
    return thumbnailFileOffset;
  }

  /**
   * Get size of raw thumbnail data bytes
   *
   * @return size or -1 if thumbnail does not exist
   */
  public long getThumbnailSize() {
    return thumbnailSize;
  }

  /**
   * Get the byte[] containing the raw thumbnail data
   *
   * @return byte[], or null if no thumbnail is present.
   */
  public byte[] getThumbnailBytes() {
    if (!metadata.containsDirectory(ExifDirectory.class))
      return null;
    ExifDirectory exif = (ExifDirectory) metadata
        .getDirectory(ExifDirectory.class);
    if (!exif.containsThumbnail())
      return null;
    try {
      byte[] bytes = exif.getThumbnailData();
      if (bytes.length > 0)
        return bytes;
      else
        return null;
    } catch (MetadataException e) {
      return null;
    }
  }

  /**
   * Get the pixel dimensions of the image.
   *
   * @return int[2] containing width and height of the image in pixels,
   * or null if the image does not known how big it is
   */
  public int[] getImageDimensions() {
    // try getting dimensions from JPG
    if (metadata.containsDirectory(JpegDirectory.class)) {
      JpegDirectory jpgDir = (JpegDirectory) metadata
          .getDirectory(JpegDirectory.class);
      if (jpgDir != null
          && jpgDir.containsTag(JpegDirectory.TAG_JPEG_IMAGE_WIDTH)
          && jpgDir.containsTag(JpegDirectory.TAG_JPEG_IMAGE_HEIGHT)) {
        try {
          int height = jpgDir
              .getInt(JpegDirectory.TAG_JPEG_IMAGE_HEIGHT);
          int width = jpgDir
              .getInt(JpegDirectory.TAG_JPEG_IMAGE_WIDTH);
          if (width > 0 && height > 0)
            return new int[] { width, height };
        } catch (MetadataException e) {
          System.out.println("Error parsing JPG dimensions metadata "
              + e.toString());
        }
      }
    }

    // failed with JPEG, try getting dimensions from EXIF
    if (metadata.containsDirectory(ExifDirectory.class)) {
      ExifDirectory exifDir = (ExifDirectory) metadata
          .getDirectory(ExifDirectory.class);
      if (exifDir != null
          && exifDir.containsTag(ExifDirectory.TAG_EXIF_IMAGE_WIDTH)
          && exifDir.containsTag(ExifDirectory.TAG_EXIF_IMAGE_HEIGHT)) {
        try {
          int height = exifDir
              .getInt(ExifDirectory.TAG_EXIF_IMAGE_HEIGHT);
          int width = exifDir
              .getInt(ExifDirectory.TAG_EXIF_IMAGE_WIDTH);
          if (width > 0 && height > 0)
            return new int[] { width, height };
        } catch (MetadataException e) {
          System.out
          .println("Error parsing EXIF dimensions metadata "
              + e.toString());
        }
      }
    }
    return null;
  }

  /**
   * Gets the integer representation of the image orientation.
   * <p>
   * If orientation flag is not set, returns 0 (unknown).
   * Other possible values are:
   * <ul>
   * <li> 1: "Top, left side (Horizontal / normal)";
   * <li> 2: "Top, right side (Mirror horizontal)";
   * <li> 3: "Bottom, right side (Rotate 180)";
   * <li> 4: "Bottom, left side (Mirror vertical)";
   * <li> 5: "Left side, top (Mirror horizontal and rotate 270 CW)";
   * <li> 6: "Right side, top (Rotate 90 CW)";
   * <li> 7: "Right side, bottom (Mirror horizontal and rotate 90 CW)";
   * <li> 8: "Left side, bottom (Rotate 270 CW)";
   * </ul>
   */
  public int getImageOrientationAsInt() {
    if ( metadata.containsDirectory(ExifDirectory.class)){
      Directory exifDir=metadata.getDirectory(ExifDirectory.class);

      if ( exifDir.containsTag(ExifDirectory.TAG_ORIENTATION)){
        try {
          return exifDir.getInt(ExifDirectory.TAG_ORIENTATION);
        } catch ( MetadataException e){
          System.err.println("Error getting orientation as int "+e);
        }
      }
    }
    // fallthrough: unknown Orientation
    return 0;
  }

  /**
   * Get string representation of EXIF Orientation flag.
   * <p>
   * @return String representation of EXIF Orientation, or "Unknown" if not set.
   * see {@link #getImageOrientationAsInt()} for possible values
   */
  public String getImageOrientationAsString(){
    if ( metadata.containsDirectory(ExifDirectory.class)){
      Directory exifDir=metadata.getDirectory(ExifDirectory.class);
      try {
        String orientation = exifDir.getDescription(ExifDirectory.TAG_ORIENTATION);
        if ( orientation != null)
          return orientation;
      } catch (MetadataException e){
        System.err.println("Error getting orientation as string "+e);
      }
    }
    return "Unknown";
  }

  /**
   * Get metadata values as a List of String[2]
   * <p>
   * where:
   * <ul>
   * <li> String[0] contains the name of the metadata tag</li>
   * <li> String[1] is the value in a human-readable representation<br/> eg
   * "1/60 sec", "F5.6"</li>
   * </ul>
   *
   * Data is returned in a sensible order: Comments, keywords, EXIF image
   * data, Manufacturer image data.
   *
   * @return List&lt;String[2]&gt;
   */
  public synchronized List getMetadataAsList() {
    if (metadataList == null) {
      metadataList = new LinkedList();

      Directory exifDir = null;
      Directory jpgCommentDir = null;
      Directory iptcDir = null;
      if (metadata.containsDirectory(ExifDirectory.class))
        exifDir = metadata.getDirectory(ExifDirectory.class);
      if (metadata.containsDirectory(IptcDirectory.class))
        iptcDir = metadata.getDirectory(IptcDirectory.class);
      if (metadata.containsDirectory(JpegCommentDirectory.class))
        jpgCommentDir = metadata
        .getDirectory(JpegCommentDirectory.class);


      // Add titles/headlines, but avoid duplicates
      {
        List titleList=new LinkedList();
        addDirTagValueToListIfNotPresent(titleList, iptcDir, IptcDirectory.TAG_HEADLINE);
        addDirTagValueToListIfNotPresent(titleList, exifDir, ExifDirectory.TAG_WIN_TITLE);
        String value=generateValueFromList(titleList,"\n");
        if ( value != null)
          metadataList.add(new String[]{KEY_COMBINED_TITLE,value});
      }

      addTagKeyValuePairToListAsString(metadataList, exifDir,
          ExifDirectory.TAG_WIN_SUBJECT, null);

      // then add comments: jpg, iptc, exif, avoid duplicates and add as \n separated list
      {
        List commentList = new LinkedList();
        addDirTagValueToListIfNotPresent(commentList, jpgCommentDir, JpegCommentDirectory.TAG_JPEG_COMMENT);
        addDirTagValueToListIfNotPresent(commentList, exifDir, ExifDirectory.TAG_USER_COMMENT);
        addDirTagValueToListIfNotPresent(commentList, exifDir, ExifDirectory.TAG_WIN_COMMENT);
        addDirTagValueToListIfNotPresent(commentList, iptcDir, IptcDirectory.TAG_CAPTION);
        String value=generateValueFromList(commentList, "\n");
        if ( value != null)
          metadataList.add(new Object[] { KEY_COMBINED_COMMENTS,
              value});
      }

      // add combined keywords
      {
        List keywordList=getKeywordsList();
        if ( keywordList.size()>0) {
          String value=generateValueFromList(keywordList, "; ");
          if ( value != null)
            metadataList.add(new Object[] { KEY_COMBINED_KEYWORDS,
                value});
        }
      }

      // then add author/artist
      {
        List commentList = new LinkedList();
        addDirTagValueToListIfNotPresent(commentList, exifDir, ExifDirectory.TAG_ARTIST);
        addDirTagValueToListIfNotPresent(commentList, exifDir, ExifDirectory.TAG_WIN_AUTHOR);
        addDirTagValueToListIfNotPresent(commentList, iptcDir, IptcDirectory.TAG_BY_LINE);
        addDirTagValueToListIfNotPresent(commentList, iptcDir, IptcDirectory.TAG_WRITER);
        String value=generateValueFromList(commentList, "; ");
        if ( value != null)
          metadataList.add(new Object[] { KEY_COMBINED_AUTHORS,
              value});
      }


      {
        List copyrightsList=new LinkedList();
        addDirTagValueToListIfNotPresent(copyrightsList, exifDir, ExifDirectory.TAG_COPYRIGHT);
        addDirTagValueToListIfNotPresent(copyrightsList, iptcDir, IptcDirectory.TAG_COPYRIGHT_NOTICE);
        String value=generateValueFromList(copyrightsList, "; ");
        if ( value != null)
          metadataList.add(new Object[] { exifDir.getTagName(ExifDirectory.TAG_COPYRIGHT),
              value});
      }

      if (exifDir != null) {

        // add Exif Date/Time
        // if ( exifDir.containsTag(ExifDirectory.TAG_DATETIME_ORIGINAL)
        // ){
        // addDirTagToListAsString(retval, exifDir,
        // ExifDirectory.TAG_DATETIME_ORIGINAL, null);
        // } else if(exifDir.containsTag(ExifDirectory.TAG_DATETIME) ){
        // addDirTagToListAsString(retval, exifDir,
        // ExifDirectory.TAG_DATETIME,
        // null);
        // } else if (exifDir.containsTag(ExifDirectory.TAG_DATETIME) ){
        // addDirTagToListAsString(retval, exifDir,
        // ExifDirectory.TAG_DATETIME,
        // null);
        // }

        // add image dimensions if known
        int[] dimensions = getImageDimensions();
        if (dimensions != null) {
          metadataList.add(new Object[] { KEY_IMAGE_DIMENSIONS,
              dimensions[0] + "x" + dimensions[1] + " pixels" });
        }

        // then add technical info on camera properties
        String cameraMake = null;
        if (exifDir.containsTag(ExifDirectory.TAG_MAKE)) {
          cameraMake = exifDir.getString(ExifDirectory.TAG_MAKE);
        }
        String cameraModel = null;
        if (exifDir.containsTag(ExifDirectory.TAG_MODEL)) {
          cameraModel = exifDir.getString(ExifDirectory.TAG_MODEL);
        }
        String camera = new String();
        if (cameraMake != null)
          camera=camera+cameraMake.trim();
        if (cameraModel != null) {
          if (cameraMake != null)
            camera=camera+(" - ");
          camera=camera+(cameraModel.trim());
        }
        if (camera.length() > 0)
          metadataList.add(new Object[] { KEY_CAMERA_NAME,
              camera.toString() });

        // Shutter Speed can be in either: Exposure Time or Shutter
        // Speed
        // Value
        if (exifDir.containsTag(ExifDirectory.TAG_EXPOSURE_TIME)) {
          addTagKeyValuePairToListAsString(metadataList, exifDir,
              ExifDirectory.TAG_EXPOSURE_TIME, KEY_SHUTTER_SPEED);
        } else if (exifDir.containsTag(ExifDirectory.TAG_SHUTTER_SPEED)) {
          addTagKeyValuePairToListAsString(metadataList, exifDir,
              ExifDirectory.TAG_SHUTTER_SPEED, KEY_SHUTTER_SPEED);
        }

        // Aperture... can be in F-number, Aperture Value or Max
        // Aperture
        if (exifDir.containsTag(ExifDirectory.TAG_FNUMBER)) {
          addTagKeyValuePairToListAsString(metadataList, exifDir,
              ExifDirectory.TAG_FNUMBER, KEY_APERTURE);
        } else if (exifDir.containsTag(ExifDirectory.TAG_APERTURE)) {
          addTagKeyValuePairToListAsString(metadataList, exifDir,
              ExifDirectory.TAG_APERTURE, KEY_APERTURE);
        }
        if (exifDir.containsTag(ExifDirectory.TAG_EXPOSURE_BIAS)) {
          float bias = 0;
          try {
            bias = exifDir
                .getFloat(ExifDirectory.TAG_EXPOSURE_BIAS);
          } catch (MetadataException e) {
            System.out.println("Problem getting exposure bias "
                + e.toString());
          }
          if (Math.abs(bias) > 0.01)
            addTagKeyValuePairToListAsString(metadataList, exifDir,
                ExifDirectory.TAG_EXPOSURE_BIAS, null);
        }

        // ISO
        addTagKeyValuePairToListAsString(metadataList, exifDir,
            ExifDirectory.TAG_ISO_EQUIVALENT, null);

        // Flash mode

        addTagKeyValuePairToListAsString(metadataList, exifDir,
            ExifDirectory.TAG_FLASH, null);

        // Focal Length
        addTagKeyValuePairToListAsString(metadataList, exifDir,
            ExifDirectory.TAG_FOCAL_LENGTH, null);
        // Lens
        addTagKeyValuePairToListAsString(metadataList, exifDir,
            ExifDirectory.TAG_LENS, null);

        // Digital Zoom
        if (exifDir.containsTag(ExifDirectory.TAG_DIGITAL_ZOOM_RATIO)) {
          float digitalZoom = 0;
          try {
            digitalZoom = exifDir
                .getFloat(ExifDirectory.TAG_DIGITAL_ZOOM_RATIO);
          } catch (MetadataException e) {
            System.err.println("error getting digital zoom from EXIF data "
                + e.toString());
          }
          if (digitalZoom > 1.001)
            addTagKeyValuePairToListAsString(metadataList, exifDir,
                ExifDirectory.TAG_DIGITAL_ZOOM_RATIO, null);
        }

        try {
          if (exifDir.containsTag(ExifDirectory.TAG_EXPOSURE_PROGRAM)
              && exifDir
              .getInt(ExifDirectory.TAG_EXPOSURE_PROGRAM) != 0)
            addTagKeyValuePairToListAsString(metadataList, exifDir,
                ExifDirectory.TAG_EXPOSURE_PROGRAM, null);
        } catch (MetadataException e) {
          System.out.println("Problem getting Exposure Program "
              + e.toString());
        }

        try {
          if (exifDir.containsTag(ExifDirectory.TAG_METERING_MODE)
              && exifDir.getInt(ExifDirectory.TAG_METERING_MODE) != 0
              && exifDir.getInt(ExifDirectory.TAG_METERING_MODE) != 255)
            addTagKeyValuePairToListAsString(metadataList, exifDir,
                ExifDirectory.TAG_METERING_MODE, null);
        } catch (MetadataException e) {
          System.out.println("Problem getting Metering Mode"
              + e.toString());
        }
        try {
          if (exifDir.containsTag(ExifDirectory.TAG_WHITE_BALANCE)
              && exifDir.getInt(ExifDirectory.TAG_WHITE_BALANCE) != 0
              && exifDir.getInt(ExifDirectory.TAG_WHITE_BALANCE) != 255)
            addTagKeyValuePairToListAsString(metadataList, exifDir,
                ExifDirectory.TAG_WHITE_BALANCE, null);
        } catch (MetadataException e) {
          System.out.println("Problem getting White Balance"
              + e.toString());
        }
      }
      // then add GPS information
      if ( metadata.containsDirectory(GpsDirectory.class)){
        Directory gps=metadata.getDirectory(GpsDirectory.class);
        if (gps.containsTag(GpsDirectory.TAG_GPS_LATITUDE)
            && gps.containsTag(GpsDirectory.TAG_GPS_LONGITUDE)
            && gps.containsTag(GpsDirectory.TAG_GPS_LATITUDE_REF)
            && gps.containsTag(GpsDirectory.TAG_GPS_LONGITUDE_REF)) {
          try {
            String latLong=
                gps.getDescription(GpsDirectory.TAG_GPS_LATITUDE_REF)
                +" "
                + gps.getDescription(GpsDirectory.TAG_GPS_LATITUDE)
                + " - "
                + gps.getDescription(GpsDirectory.TAG_GPS_LONGITUDE_REF)
                +" "
                + gps.getDescription(GpsDirectory.TAG_GPS_LONGITUDE);
            metadataList.add(new String[]{KEY_GPS_POSITION,latLong});
          } catch (MetadataException e){
            System.err.println("Warning: failed to get GPS Position -"
                +e.toString());
          }

        }

      }

      // then add maker-specific information

      // Canon:
      // Image Size/Quality
      // Easy Shooting Mode
      // Focus type
      // AF Point Used = Right
      // Flash Bias = 0.0 EV
      // Auto Exposure Bracketing = 0
      // AEB Bracket Value = 0
      if ( metadata.containsDirectory(CanonMakernoteDirectory.class)){
        Directory canonDir=metadata.getDirectory(CanonMakernoteDirectory.class);
        addTagKeyValuePairToListAsString(metadataList, canonDir,
            CanonMakernoteDirectory.TAG_CANON_STATE1_EASY_SHOOTING_MODE, null);
        addTagKeyValuePairToListAsString(metadataList, canonDir,
            CanonMakernoteDirectory.TAG_CANON_STATE1_FOCUS_TYPE, null);
        addTagKeyValuePairToListAsString(metadataList, canonDir,
            CanonMakernoteDirectory.TAG_CANON_STATE1_AF_POINT_SELECTED, null);
        addTagKeyValuePairToListAsString(metadataList, canonDir,
            CanonMakernoteDirectory.TAG_CANON_STATE2_FLASH_BIAS, null);
        addTagKeyValuePairToListAsString(metadataList, canonDir,
            CanonMakernoteDirectory.TAG_CANON_STATE2_WHITE_BALANCE, null);
      }

      // Casio
      addDirectoryValuesToList(CasioType1MakernoteDirectory.class,
          new int[]{
        CasioType1MakernoteDirectory.TAG_CASIO_WHITE_BALANCE,
        CasioType1MakernoteDirectory.TAG_CASIO_OBJECT_DISTANCE});

      addDirectoryValuesToList(CasioType2MakernoteDirectory.class,
          new int[]{
        CasioType2MakernoteDirectory.TAG_CASIO_TYPE2_WHITE_BALANCE_1,
        CasioType2MakernoteDirectory.TAG_CASIO_TYPE2_WHITE_BALANCE_2,
        CasioType2MakernoteDirectory.TAG_CASIO_TYPE2_OBJECT_DISTANCE});

      // Fujifilm
      addDirectoryValuesToList(FujifilmMakernoteDirectory.class,
          new int[]{
        FujifilmMakernoteDirectory.TAG_FUJIFILM_WHITE_BALANCE,
        FujifilmMakernoteDirectory.TAG_FUJIFILM_PICTURE_MODE});

      // Nikon
      addDirectoryValuesToList(NikonType1MakernoteDirectory.class,
          new int[]{
        NikonType1MakernoteDirectory.TAG_NIKON_TYPE1_WHITE_BALANCE});

      addDirectoryValuesToList(NikonType2MakernoteDirectory.class,
          new int[]{
        NikonType2MakernoteDirectory.TAG_NIKON_TYPE2_CAMERA_WHITE_BALANCE,
        NikonType2MakernoteDirectory.TAG_NIKON_TYPE2_LENS,
        NikonType2MakernoteDirectory.TAG_NIKON_TYPE2_LIGHT_SOURCE});
    }
    return metadataList;
  }

  /**
   * Get the keywords from the exif and iptc metadata, returning them as a List
   *
   * @return List&lt;String&gt; of keyword strings, list.size()=0 if none found
   */
  public List getKeywordsList() {
    Directory exifDir = null;
    Directory iptcDir = null;
    if (metadata.containsDirectory(ExifDirectory.class))
      exifDir = metadata.getDirectory(ExifDirectory.class);
    if (metadata.containsDirectory(IptcDirectory.class))
      iptcDir = metadata.getDirectory(IptcDirectory.class);

    // add keywords to a list, avoiding duplicates
    List keywordList=new LinkedList();
    if ( exifDir!=null ){
      if ( exifDir.containsTag(ExifDirectory.TAG_WIN_KEYWORDS)){
        try {
          String keywords=exifDir.getDescription(ExifDirectory.TAG_WIN_KEYWORDS).trim();
          StringTokenizer tok=new StringTokenizer(keywords," ");
          while(tok.hasMoreTokens()) {
            String keyword=tok.nextToken();
            if ( ! keywordList.contains(keyword) && keyword.length()>0)
              keywordList.add(keyword);
          }
        } catch (MetadataException e){
          System.err.println("Warning: failed to get tag value for ExifDirectory.TAG_WIN_KEYWORDS -"
              +e.toString());
        }
      }
    }
    if ( iptcDir!=null && iptcDir.containsTag(IptcDirectory.TAG_KEYWORDS)){
      try {
        Object keywordsObj=iptcDir.getObject(IptcDirectory.TAG_KEYWORDS);
        if ( keywordsObj.getClass().isArray()) {
          String keywords[]=(String[])keywordsObj;
          for (int i = 0; i < keywords.length; i++) {
            if ( keywords[i].length()>0
                && ! keywordList.contains(keywords[i])) {
              keywordList.add(keywords[i]);
            }
          }
        } else {
          String keyword=(String)keywordsObj;
          if ( ! keywordList.contains(keyword) && keyword.length()>0)
            keywordList.add(keyword);
        }
      } catch (ClassCastException e){
        System.err.println("Warning: failed to get tag value for ExifDirectory.TAG_WIN_KEYWORDS -"
            +e.toString());
      }
    }
    return keywordList;
  }

  /**
   * Get metadata as a Map of &lt;String,String&gt;
   * <p>
   * where
   * <ul>
   * <li> key is the name of the metadata tag</li>
   * <li> value is the value in a human-readable representation<br/> eg "1/60
   * sec", "F5.6"</li>
   * </ul>
   *
   * @return Map&lt;String,String&gt;
   */
  public synchronized Map getMetadataAsMap() {
    if (metadataMap == null) {
      metadataMap = new HashMap();
      for (Iterator it = getMetadataAsList().iterator(); it.hasNext();) {
        Object[] item = (Object[]) it.next();
        assert item.length == 2 : "Invalid metadata item";
        metadataMap.put(item[0], item[1]);
      }
    }
    return metadataMap;
  }

  /**
   * Returns metadata from {@link #getMetadataAsList()} as a human readable
   * multiline String
   */
  public String toString() {

    String retval = new String();
    for (Iterator it = getMetadataAsList().iterator(); it.hasNext();) {
      Object[] val = (Object[]) it.next();
      if (retval.length() > 0)
        retval=retval+('\n');
      retval=retval+(val[0]);
      retval=retval+(": ");
      retval=retval+(val[1]);
    }
    return retval.toString();
  }

  /**
   * Provides access to the raw metadata
   *
   * @return the metadata object, or null if no metadata found in image
   */
  public Metadata getMetadata() {
    return metadata;
  }

  /**
   * helper function to build list of Object[2] tag-value pairs
   *
   * @param list
   *            List to add to
   * @param dir
   *            {@link Directory} from which to extract the tag
   * @param tag
   *            tagID to extract
   * @param tagName
   *            (optional) custom tag name, or null to use the tag name
   *            defined by the {@link Directory}
   */
  private static void addTagKeyValuePairToListAsString(List list, Directory dir,
      int tag, String tagName) {
    if (dir != null && dir.containsTag(tag)) {
      try {
        String val = dir.getDescription(tag);
        if (val != null && val.length() > 0) {
          if (tagName == null)
            tagName = dir.getTagName(tag);
          list.add(new Object[] { tagName, val });
        }
      } catch (MetadataException e) {
        System.out.println("Error getting tag value for "
            + dir.getClass().getName() + " tag " + tag + " -- "
            + e.toString());
      }
    }
  }
  /**
   * Helper function to add a tag value to a list if that tag value is not already present
   *
   * @param list
   * @param dir
   * @param tag
   */
  private static void addDirTagValueToListIfNotPresent(List list, Directory dir,
      int tag){
    if ( dir!=null && dir.containsTag(tag)){
      try {
        String value=dir.getDescription(tag).trim();
        if ( !list.contains(value) && value.length()>0)
          list.add(value);
      } catch (MetadataException e){
        System.err.println("Warning: failed to get tag value for"
            + dir.getClass().getName() + " tag " + tag + " -- "
            +e.toString());
      }
    }

  }

  /**
   * take the list and build a string using separator
   *
   * @param list&lt;String&gt;
   * @param Separator
   * @return combined result
   */
  private static String generateValueFromList(List list, String Separator){
    if ( list.size()==1 ) {
      return (String)list.get(0);
    } else if ( list.size()>1 ) {
      StringBuffer sb=new StringBuffer();
      for (Iterator it = list.iterator(); it.hasNext();) {
        if ( sb.length()>0)
          sb.append(Separator);
        sb.append((String) it.next());
      }
      return sb.toString();
    }
    return null;
  }

  /**
   * Add the values represented by tags in the Directory class to the metadataList
   */
  private void addDirectoryValuesToList(Class makerNoteClass, int[] tags) {
    if ( metadata.containsDirectory(makerNoteClass)) {
      Directory dir=metadata.getDirectory(makerNoteClass);
      for (int i = 0; i < tags.length; i++) {
        addTagKeyValuePairToListAsString(metadataList, dir,
            tags[i], null);
      }
    }
  }
}
