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
package sage.epg.sd;

import sage.Channel;
import sage.Pooler;
import sage.Show;
import sage.epg.sd.json.images.SDImage;

import java.nio.charset.StandardCharsets;

public class SDImages
{
  // Logos
  public static final String LOGO_S3_AWS_STRING =
      "https://s3.amazonaws.com/schedulesdirect/assets/stationLogos/"; // *.png
  public static final short LOGO_S3_AWS = 0x80;
  public static final short LOGO_HAS_ID = 0x40;

  // Shows, Movies and Series
  public static final String SOURCE_S3_AWS_STRING =
      "https://s3.amazonaws.com/schedulesdirect/assets/"; // *.jpg
  public static final short SOURCE_S3_AWS = 0x80;
  public static final String SOURCE_REDIRECT_STRING =
      "https://json.schedulesdirect.org/20141201/image/assets/"; // *.jpg
  public static final short SOURCE_REDIRECT = 0x40;
  public static final short HAS_SHOWCARD_FLAG = 0x20;
  public static final short IS_THUMB_FLAG = 0x10;
  public static final short IS_TALL_FLAG = 0x08;
  public static final short IMAGE_TYPE_MASK = 0x07;
  public static final short IMAGE_TYPE_PHOTO_FLAG = 0x07;
  public static final short IMAGE_TYPE_POSTER_FLAG = 0x06;
  public static final short IMAGE_TYPE_BOXART_FLAG = 0x05;

  public static final byte SERIES_THUMB_INDEX = 0;
  public static final byte SERIES_FULL_INDEX = 1;

  public static final byte SHOW_PHOTO_TALL_INDEX = 1;
  public static final byte SHOW_PHOTO_WIDE_INDEX = 3;
  public static final byte SHOW_POSTER_TALL_INDEX = 5;
  public static final byte SHOW_POSTER_WIDE_INDEX = 7;
  public static final byte SHOW_BOXART_TALL_INDEX = 9;

  public static final byte ENCODE_ALL = 0;
  public static final byte ENCODE_SERIES_ONLY = 1;
  public static final byte ENCODE_NON_SERIES_ONLY = 2;

  // Mode: 0 = all usable images, 1 = series only, 2 = non-series only
  public static byte[][] encodeImages(SDImage images[], int showcardID[], int mode)
  {
    // The 2D byte array is formatted as follows:
    // [0][0-*] The first index of each image type.
    // [1-*][0] The attributes of the image URL.
    // [1-*][1-*] The image URLs encoded in UTF-8.

    if (images == null || images.length == 0 || showcardID == null || showcardID.length == 0)
      return Pooler.EMPTY_2D_BYTE_ARRAY;

    // The URL will potentially be changed a lot as patterns are discovered. This keeps us from
    // allocating many Strings as we manipulate it.
    StringBuilder urlBuilder = new StringBuilder(128);
    // To keep the indexing from getting out of hand if there is a particularly large amount of
    // images, we stop at 100 images (10 per type). This is also a convenient number because the
    // byte value will be less than 127, so we won't need to make it unsigned it when indexing.
    int maxImages = 100;
    int addedImagesIndex = 0;
    byte[] counts = new byte[mode == ENCODE_SERIES_ONLY ? 2 : 10];
    int maxPerImageType = maxImages / counts.length;
    byte[][] addedImages = new byte[maxPerImageType * counts.length][];
    String showcardString = showcardID[0] > 0 ? Integer.toString(showcardID[0]) : "";

    for (SDImage image : images)
    {
      // Try to get a showcard ID from the URL in case none of the images are usable. This is not
      // that efficient if the showcard ID is never discovered and usable, but this will save time
      // on series info updates.
      if (showcardID[0] == 0)
      {
        urlBuilder.setLength(0);
        image.getUri(urlBuilder);

        int startingChar = -1;
        if (urlBuilder.indexOf(SOURCE_REDIRECT_STRING) == 0)
        {
          startingChar = SOURCE_REDIRECT_STRING.length();
        }
        else if (urlBuilder.indexOf(SOURCE_S3_AWS_STRING) == 0)
        {
          startingChar = SOURCE_S3_AWS_STRING.length();
        }

        // We will not encode anything starting with a p0 because the 0 gets lost when the String is
        // converted to a number and then we would need to add logic to the decoding methods to check
        // for more possibilities due to the additional scenario. Also, I have not seen anything come
        // in with a 0, but it could happen, so we need to account for it.
        if (startingChar != -1 && urlBuilder.charAt(startingChar) == 'p' && urlBuilder.charAt(startingChar + 1) != '0')
        {
          int underscore = urlBuilder.indexOf("_", startingChar);
          if (underscore != -1)
          {
            try
            {
              showcardString = urlBuilder.substring(startingChar + 1, underscore);
              showcardID[0] = Integer.parseInt(showcardString);
              showcardString = 'p' + showcardString + '_';
            }
            catch (NumberFormatException e)
            {
              showcardString = "";
            }
          }
        }
      }

      // Stop trying to add new images.
      if (addedImagesIndex > maxImages)
        break;

      byte size = image.getSizeByte();
      // We are using the medium "size" for thumbnails and the large "size" for regular images. If
      // we grab a range of sizes, we can end up with a lot of the exact same image just a different
      // size.
      if (size != SDImage.SIZE_MD && size != SDImage.SIZE_LG)
        continue;

      urlBuilder.setLength(0);
      image.getUri(urlBuilder);
      byte attributes = 0;
      byte tier = image.getTierByte();
      if (tier == SDImage.TIER_SERIES && mode == ENCODE_NON_SERIES_ONLY)
        continue;
      if (tier != SDImage.TIER_SERIES && mode == ENCODE_SERIES_ONLY)
        continue;
      byte category = image.getCategoryByte();
      boolean tall = image.getHeight() > image.getWidth();
      boolean thumbnail = size == SDImage.SIZE_MD; // ~360x270
      int imageType;

      switch (category)
      {
        case SDImage.CAT_BANNER_L1:
          if (tall || mode != ENCODE_SERIES_ONLY)
            continue;
          attributes |= IMAGE_TYPE_POSTER_FLAG;
          imageType = thumbnail ? Show.IMAGE_POSTER_THUMB_WIDE : Show.IMAGE_POSTER_WIDE;
          break;
        case SDImage.CAT_POSTER_ART:
          if (mode == ENCODE_SERIES_ONLY)
            continue;
          attributes |= IMAGE_TYPE_POSTER_FLAG;
          imageType = thumbnail ?
              (tall ? Show.IMAGE_POSTER_THUMB_TALL : Show.IMAGE_POSTER_THUMB_WIDE) :
              (tall ? Show.IMAGE_POSTER_TALL : Show.IMAGE_POSTER_WIDE);
          break;
        case SDImage.CAT_BOX_ART:
          if (!tall || mode == ENCODE_SERIES_ONLY)
            continue;
          attributes |= IMAGE_TYPE_BOXART_FLAG;
          imageType = thumbnail ? Show.IMAGE_BOXART_THUMB_TALL : Show.IMAGE_BOXART_TALL;
          break;
        case SDImage.CAT_PHOTO:
          if (mode == ENCODE_SERIES_ONLY)
            continue;
          attributes |= IMAGE_TYPE_PHOTO_FLAG;
          imageType = thumbnail ?
              (tall ? Show.IMAGE_PHOTO_THUMB_TALL : Show.IMAGE_PHOTO_THUMB_WIDE) :
              (tall ? Show.IMAGE_PHOTO_TALL : Show.IMAGE_PHOTO_WIDE);
          break;
        default:
          continue;
      }

      int imageTypeIndex;
      if (mode == ENCODE_SERIES_ONLY)
        imageTypeIndex = (getSDIndexForShowType(imageType) % 2) == 0 ? SERIES_THUMB_INDEX : SERIES_FULL_INDEX;
      else
        imageTypeIndex = getSDIndexForShowType(imageType);

      if (counts[imageTypeIndex] > maxPerImageType)
        continue;

      if (tall) attributes |= IS_TALL_FLAG;
      if (thumbnail) attributes |= IS_THUMB_FLAG;

      // Remove known URL prefixes.
      if (urlBuilder.indexOf(SOURCE_REDIRECT_STRING) == 0)
      {
        urlBuilder.delete(0, SOURCE_REDIRECT_STRING.length());
        attributes |= SOURCE_REDIRECT;
      }
      else if (urlBuilder.indexOf(SOURCE_S3_AWS_STRING) == 0)
      {
        urlBuilder.delete(0, SOURCE_S3_AWS_STRING.length());
        attributes |= SOURCE_S3_AWS;
      }
      else
      {
        urlBuilder.delete(0, 8); // Remove https://
      }

      // Remove .jpg file extension.
      urlBuilder.delete(urlBuilder.length() - 4, urlBuilder.length());

      if (showcardID[0] > 0 && urlBuilder.indexOf(showcardString) == 0)
      {
        // Remove p + showcardID + _
        urlBuilder.delete(0, showcardString.length());
        attributes |= HAS_SHOWCARD_FLAG;
      }

      int realIndex = imageTypeIndex * maxPerImageType + counts[imageTypeIndex]++;
      byte[] newBytes = urlBuilder.toString().getBytes(StandardCharsets.UTF_8);
      addedImages[realIndex] = new byte[newBytes.length + 1];
      addedImages[realIndex][0] = attributes;
      System.arraycopy(newBytes , 0, addedImages[realIndex], 1, newBytes.length);

      // Keep track of the total images added so we stop trying to add images once all of the slots
      // are filled and don't need to add up all of the counts later to create the final 2D array.
      addedImagesIndex++;
    }

    if (addedImagesIndex == 0)
      return Pooler.EMPTY_2D_BYTE_ARRAY;

    byte[][] returnValue = new byte[addedImagesIndex + 1][];
    // indexes used to find the first index of each image type
    byte indexes[] = new byte[counts.length];
    returnValue[0] = indexes;

    int lastIndex = 0;
    if (counts[0] > 0)
    {
      indexes[0] = 1;
      System.arraycopy(addedImages, 0, returnValue, 1, counts[0]);
    }

    for (int i = 1; i < counts.length; i++)
    {
      if (counts[i] == 0)
        continue;
      indexes[i] = (byte)(indexes[lastIndex] + counts[lastIndex]);
      if (indexes[i] == 0)
        indexes[i] = 1;

      System.arraycopy(addedImages, i * maxPerImageType, returnValue, indexes[i], counts[i]);
      lastIndex = i;
    }

    return returnValue;
  }

  public static byte[][] encodeEpisodeImage(SDImage image, int showcardID[])
  {
    if (showcardID == null || showcardID.length == 0 || image == null) return Pooler.EMPTY_2D_BYTE_ARRAY;
    byte[] counts = new byte[10];
    byte[][] encodedImage = new byte[2][];
    encodedImage[0] = counts;
    byte attributes = IMAGE_TYPE_PHOTO_FLAG | IS_THUMB_FLAG | IS_TALL_FLAG;
    String showcardString = "";
    StringBuilder urlBuilder = new StringBuilder(128);
    image.getUri(urlBuilder);

    int startingChar = -1;
    if (urlBuilder.indexOf(SOURCE_REDIRECT_STRING) == 0)
    {
      startingChar = SOURCE_REDIRECT_STRING.length();
    }
    else if (urlBuilder.indexOf(SOURCE_S3_AWS_STRING) == 0)
    {
      startingChar = SOURCE_S3_AWS_STRING.length();
    }

    if (startingChar != -1 && urlBuilder.charAt(startingChar) == 'p' && urlBuilder.charAt(startingChar + 1) != '0')
    {
      int underscore = urlBuilder.indexOf("_", startingChar);
      if (underscore != -1)
      {
        try
        {
          showcardString = urlBuilder.substring(startingChar + 1, underscore);
          showcardID[0] = Integer.parseInt(showcardString);
          showcardString = 'p' + showcardString + '_';
        }
        catch (NumberFormatException e)
        {
          showcardString = "";
        }
      }
    }

    // Remove known URL prefixes.
    if (urlBuilder.indexOf(SOURCE_REDIRECT_STRING) == 0)
    {
      urlBuilder.delete(0, SOURCE_REDIRECT_STRING.length());
      attributes |= SOURCE_REDIRECT;
    }
    else if (urlBuilder.indexOf(SOURCE_S3_AWS_STRING) == 0)
    {
      urlBuilder.delete(0, SOURCE_S3_AWS_STRING.length());
      attributes |= SOURCE_S3_AWS;
    }
    else
    {
      urlBuilder.delete(0, 8); // Remove https://
    }

    // Remove .jpg file extension.
    urlBuilder.delete(urlBuilder.length() - 4, urlBuilder.length());

    if (showcardID[0] > 0 && urlBuilder.indexOf(showcardString) == 0)
    {
      // Remove p + showcardID + _
      urlBuilder.delete(0, showcardString.length());
      attributes |= HAS_SHOWCARD_FLAG;
    }

    int imageTypeIndex = getSDIndexForShowType(Show.IMAGE_PHOTO_THUMB_TALL);
    counts[imageTypeIndex]++; // We only have one index, so this is ok.

    byte[] newBytes = urlBuilder.toString().getBytes(StandardCharsets.UTF_8);
    encodedImage[1] = new byte[newBytes.length + 1];
    encodedImage[1][0] = attributes;
    System.arraycopy(newBytes , 0, encodedImage[1], 1, newBytes.length);

    return encodedImage;
  }

  public static byte[] encodeLogoURL(String url, int logoID[])
  {
    if (url == null || url.length() == 0 || logoID == null || logoID.length == 0)
      return Pooler.EMPTY_BYTE_ARRAY;
    logoID[0] = 0;

    byte attributes = 0;
    if (url.startsWith(LOGO_S3_AWS_STRING))
    {
      attributes |= LOGO_S3_AWS;
      int urlLength = LOGO_S3_AWS_STRING.length();
      if (url.startsWith("s", urlLength) && url.charAt(urlLength + 1) != '0')
      {
        int tempLogoID = 0;
        int underscore = url.indexOf('_', urlLength);
        if (underscore != -1)
        {
          try
          {
            tempLogoID = Integer.parseInt(url.substring(urlLength + 1, underscore));
            // Verify that the mask Channel uses will not break the URL.
            if ((tempLogoID & 0xFFFFFF) == tempLogoID)
            {
              attributes |= LOGO_HAS_ID;
              logoID[0] = tempLogoID;
              url = url.substring(underscore + 1, url.length() - 4); // Remove S3, s, ID, _ and .png
            }
            else
            {
              tempLogoID = 0;
            }
          }
          catch (NumberFormatException e)
          {
            tempLogoID = 0;
          }
        }

        if (tempLogoID == 0)
        {
          url = url.substring(LOGO_S3_AWS_STRING.length(), url.length() - 4); // Remove S3 and .png
        }
      }
      else
      {
        url = url.substring(LOGO_S3_AWS_STRING.length(), url.length() - 4); // Remove S3 and .png
      }
    }
    else
    {
      url = url.substring(8, url.length() - 4); // Remove https:// and .png
    }

    byte[] newBytes = url.getBytes(StandardCharsets.UTF_8);
    byte[] returnValue = new byte[newBytes.length + 1];
    returnValue[0] = attributes;
    System.arraycopy(newBytes , 0, returnValue, 1, newBytes.length);
    return returnValue;
  }

  public static String decodeLogoURL(byte[] url, int logoID)
  {
    if (url.length == 0)
      return null;

    if ((url[0] & LOGO_S3_AWS) != 0)
    {
      if ((url[0] & LOGO_HAS_ID) != 0)
      {
        return LOGO_S3_AWS_STRING + 's' + logoID + '_' + new String(url, 1, url.length - 1, StandardCharsets.UTF_8) + ".png";
      }

      return LOGO_S3_AWS_STRING + new String(url, 1, url.length - 1, StandardCharsets.UTF_8) + ".png";
    }

    return "https://" + new String(url, 1, url.length - 1, StandardCharsets.UTF_8) + ".png";
  }

  private static int getSDIndexForSeriesType(int imageType)
  {
    switch (imageType)
    {
      case SERIES_FULL_INDEX:
        return SERIES_FULL_INDEX;
      case SERIES_THUMB_INDEX:
        return SERIES_THUMB_INDEX;
      default:
        return -1;
    }
  }

  protected static int getSDIndexForShowType(int imageType)
  {
    switch (imageType)
    {
      case Show.IMAGE_PHOTO_TALL:
        return SHOW_PHOTO_TALL_INDEX;
      case Show.IMAGE_PHOTO_THUMB_TALL:
        return SHOW_PHOTO_TALL_INDEX - 1;
      case Show.IMAGE_PHOTO_WIDE:
        return SHOW_PHOTO_WIDE_INDEX;
      case Show.IMAGE_PHOTO_THUMB_WIDE:
        return SHOW_PHOTO_WIDE_INDEX - 1;
      case Show.IMAGE_POSTER_TALL:
        return SHOW_POSTER_TALL_INDEX;
      case Show.IMAGE_POSTER_THUMB_TALL:
        return SHOW_POSTER_TALL_INDEX - 1;
      case Show.IMAGE_POSTER_WIDE:
        return SHOW_POSTER_WIDE_INDEX;
      case Show.IMAGE_POSTER_THUMB_WIDE:
        return SHOW_POSTER_WIDE_INDEX - 1;
      case Show.IMAGE_BOXART_TALL:
        return SHOW_BOXART_TALL_INDEX;
      case Show.IMAGE_BOXART_THUMB_TALL:
        return SHOW_BOXART_TALL_INDEX - 1;
      default:
        return -1;
    }
  }

  public static int getShowImageCount(int imageType, byte[][] imageURLs)
  {
    // If we only have the indexes, or an empty array, we don't have anything to return.
    if (imageURLs.length < 2)
      return 0;

    int imageTypeIndex = getSDIndexForShowType(imageType);
    return getImageCount(imageTypeIndex, imageURLs);
  }

  public static int getImageCount(int imageTypeIndex, byte[][] imageURLs)
  {
    byte[] indexes = imageURLs[0];
    if (imageTypeIndex < 0 || indexes.length <= 1 || indexes.length < imageTypeIndex || indexes[imageTypeIndex] == 0)
      return 0;

    if (imageTypeIndex == indexes.length - 1)
    {
      return imageURLs.length - indexes[imageTypeIndex];
    }

    // The next index doesn't point to the index after this one, so we need to find it. We are
    // checking at most 10 indexes, so this should not be a problem.
    if (indexes[imageTypeIndex + 1] == 0)
    {
      int nextIndex = 0;
      for (int i = imageTypeIndex + 1; i < indexes.length; i++)
      {
        if (indexes[i] == 0)
          continue;

        nextIndex = indexes[i];
        break;
      }

      if (nextIndex == 0)
        return imageURLs.length - indexes[imageTypeIndex];
      else
        return nextIndex - indexes[imageTypeIndex];
    }

    return indexes[imageTypeIndex + 1] - indexes[imageTypeIndex];
  }

  protected static String getImageUrl(int showcardID, byte[][] imageURLs, int index, int imageTypeIndex)
  {
    if (getImageCount(imageTypeIndex, imageURLs) <= index)
      return null;

    byte[] indexes = imageURLs[0];
    return decodeImageUrl(showcardID, imageURLs[indexes[imageTypeIndex] + index]);
  }

  public static String getSeriesImageUrl(int showcardID, byte[][] imageURLs, int index, boolean thumb)
  {
    // If all we have is the indexes, there's nothing else to return.
    if (imageURLs.length <= 1 || index < 0)
      return null;

    return getImageUrl(showcardID, imageURLs, index, thumb ? SERIES_THUMB_INDEX : SERIES_FULL_INDEX);
  }

  public static String getShowImageUrl(int showcardID, byte[][] imageURLs, int index, int imageType)
  {
    // If all we have is the indexes, there's nothing else to return.
    if (imageURLs.length < 2 || index < 0)
      return null;

    int imageTypeIndex = getSDIndexForShowType(imageType);
    return getImageUrl(showcardID, imageURLs, index, imageTypeIndex);
  }

  public static String decodeImageUrl(int showcardID, byte[] imageURL)
  {
    // This is the minimum required to return a String,
    // but at this length it's probably not a valid URL.
    if (imageURL.length < 2)
      return null;

    // The StringBuilder will only allocate 16 characters by default and we know the result will be
    // much larger, so to keep it from re-allocating a char array many times, we make a good guess.
    StringBuilder urlBuilder = new StringBuilder(imageURL.length + 64);
    byte attributes = imageURL[0];
    //byte imageType = imageURL[1];

    if ((attributes & SOURCE_S3_AWS) != 0)
      urlBuilder.append(SOURCE_S3_AWS_STRING);
    else if ((attributes & SOURCE_REDIRECT) != 0)
      urlBuilder.append(SOURCE_REDIRECT_STRING);
    else
      urlBuilder.append("https://");

    if (showcardID > 0 && (attributes & HAS_SHOWCARD_FLAG) != 0)
      urlBuilder.append('p').append(showcardID).append('_');

    urlBuilder.append(new String(imageURL, 1, imageURL.length - 1, StandardCharsets.UTF_8)).append(".jpg");

    return urlBuilder.toString();
  }
}
