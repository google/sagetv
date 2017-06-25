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
package sage.api;

import sage.*;

/**
 * A MediaFile represents a physical file or a group of files that represent the same content. Every MediaFile has an Airing associated with it
 * that describes the metadata information for the content through the use of a Show object. There are also special MediaFiles that represent
 * streams from capture devices directly or other playback hardware such as CD/DVD drives.
 * <p>
 * SageTV will automatically convert the following types to MediaFile if used for a parameter that requires the MediaFile type:<p>
 * Airing - if an Airing has an associated MediaFile then it will be used, otherwise the conversion results in null
 * java.io.File - if the specified physical file has an assoicated MediaFile, then it will be used
 */
public class MediaFileAPI {
  private MediaFileAPI() {}
  private static boolean isNetworkedMediaFileCall(Catbert.FastStack stack, int depth)
  {
    MediaFile mf = PredefinedJEPFunction.getMediaFileObj(stack.peek(depth));
    if (Sage.client && mf != null)
    {
      return mf.getID() > 0 && mf.getGeneralType() != MediaFile.MEDIAFILE_LOCAL_PLAYBACK;
    }
    return false;
  }

  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("MediaFile", "GetMediaFiles", -1, new String[] {"MediaMask"})
    {
      /**
       * Returns all of the MediaFile objects in the database.
       * @return a list of all of the MediaFile objects in the database
       *
       * @declaration public MediaFile[] GetMediaFiles();
       */

      /**
       * Returns all of the MediaFile objects in the database
       * The content it references must also match one of the media types specified in the MediaMask.
       * There's also an additional supported type of 'L' which indicates files that pass IsLibraryFile()
       * @param MediaMask string specifying what content types to search (i.e. "TM" for TV &amp; Music, 'T'=TV, 'M'=Music, 'V'=Video, 'D'=DVD, 'P'=Pictures, 'B'=BluRay)
       * @return a list of all of the MediaFile objects in the database that match the mask
       * @since 6.4
       *
       * @declaration public MediaFile[] GetMediaFiles(String MediaMask);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (curNumberOfParameters == 1)
        {
          String maskString = stack.peek().toString();
          int mediaMask = getMediaMask(stack);
          return Wizard.getInstance().getFiles(mediaMask, maskString.indexOf("L") != -1);
        }
        else
          return Wizard.getInstance().getFiles();
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "AddMediaFile", 2, new String[] {"File", "NamePrefix"}, true)
    {
      /**
       * Adds a new MediaFile to the database. This file will remain in the database until it is manually removed by the
       * user or when the file no longer exists.
       * @param File the file path for the new MediaFile
       * @param NamePrefix the 'prefix' to prepend to the name of this media file for hierarchical purposes (i.e. the subdirectory that the file is in relative to the import root)
       * @return the newly added MediaFile object
       *
       * @declaration public MediaFile AddMediaFile(java.io.File File, String NamePrefix);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String name = getString(stack);
        java.io.File f = getFile(stack);
        MediaFile mf = Wizard.getInstance().addMediaFile(f, name, MediaFile.ACQUISITION_MANUAL);
        return mf;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "CreateTempMediaFile", 1, new String[] {"File"})
    {
      /**
       * Creates a temporary MediaFile object which can be used for playback later. This will not be added into the database;
       * but any metadata that is attached to this MediaFile object will be put in the database until the next cleanup process occurs.
       * @param FilePath the file path for the temporary MediaFile (can also be an smb:// URL)
       * @return the newly created temporary MediaFile object or null if it can't properly resolve the path to a file
       * @since 6.6
       *
       * @declaration public MediaFile CreateTempMediaFile(String FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String s = getString(stack);
        MediaFile mf = Wizard.getInstance().getPlayableMediaFile(s);
        return mf;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "SetMediaFileAiring", new String[] {"MediaFile", "Airing"})
    {
      /**
       * Sets a link between a MediaFile object which represents a file(s) on disk and an Airing object which
       * represents metadata about the content. This is a way to link content information with media.
       * @param MediaFile the MediaFile object to set the content information for
       * @param Airing the Airing object that should be the content metadata pointer for this MediaFile
       * @return true if the operation succeeded, false otherwise; this operation will fail if the Airing is already linked to another MediaFile
       *
       * @declaration public boolean SetMediaFileAiring(MediaFile MediaFile, Airing Airing);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (isNetworkedMediaFileCall(stack, 1))
        {
          return makeNetworkedCall(stack);
        }
        Airing a = getAir(stack);
        MediaFile mf = getMediaFile(stack);
        boolean rv = false;
        if (a != null && mf != null)
        {
          rv = mf.setInfoAiring(a);
        }
        return rv?Boolean.TRUE:Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "SetMediaFileShow", new String[] {"MediaFile", "Show"})
    {
      /**
       * Sets a link between a MediaFile object which represents a file(s) on disk and a Show object which
       * represents metadata about the content. This is a way to link content information with media.
       * This will create a new Airing representing this Show and add it to the database. Then that new Airing is
       * linked with this MediaFile (just like it is in {@link #SetMediaFileAiring SetMediaFileAiring()}
       * @param MediaFile the MediaFile object to set the content information for
       * @param Show the Show object that should be the content information for this MediaFile
       * @return true if the operation succeeded, false otherwise; this operation will fail only if one of the arguments is null
       *
       * @declaration public boolean SetMediaFileShow(MediaFile MediaFile, Show Show);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (isNetworkedMediaFileCall(stack, 1))
        {
          return makeNetworkedCall(stack);
        }
        Show s = getShow(stack);
        MediaFile mf = getMediaFile(stack);
        boolean rv = false;
        if (s != null && mf != null)
        {
          rv = mf.setShow(s);
        }
        return rv?Boolean.TRUE:Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetMediaFileForFilePath", 1, new String[] {"FilePath"})
    {
      /**
       * Returns the MediaFile from the database that corresponds to a specified file on disk
       * @param FilePath the file path to find the corresponding MediaFile for
       * @return the MediaFile for the corresponding file path, or null if there is no corresponding MediaFile
       *
       * @declaration public MediaFile GetMediaFileForFilePath(java.io.File FilePath);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof MediaFile)
          return o;
        else
          return Wizard.getInstance().getFileForFilePath(getFileObj(o));
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsLocalFile", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if the specified MediaFile is local to this system (i.e. doesn't need to be streamed from a server)
       * @param MediaFile the MediaFile object
       * @return true if the specified MediaFile is local to this system (i.e. doesn't need to be streamed from a server), false otherwise
       *
       * @declaration public boolean IsLocalFile(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.isLocalFile());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsLibraryFile", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if the specified MediaFile has been either imported using a library path or if this is a television
       * recording that has had the 'Move to Library' operation performed on it.
       * @param MediaFile the MediaFile object
       * @return true if the specified MediaFile has been either imported using a library path or if this is a television recording that has had the 'Move to Library' operation performed on it; false otherwise
       *
       * @declaration public boolean IsLibraryFile(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.isArchiveFile());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsCompleteRecording", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if SageTV considers this MediaFile a 'complete' recording. The rules behind this are somewhat complex,
       * but the intended purpose is that a 'complete' recording is one that should be presented in the list of recordings to a user.
       * @param MediaFile the MediaFile object
       * @return true if SageTV considers this MediaFile a 'complete' recording
       *
       * @declaration public boolean IsCompleteRecording(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.isCompleteRecording());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsDVD", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if this MediaFile represents DVD content. This can be either a DVD drive or a ripped DVD.
       * @param MediaFile the MediaFile object
       * @return true if this MediaFile represents DVD content, false otherwise
       *
       * @declaration public boolean IsDVD(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && (mf.isDVD() || (mf.isBluRay() && MediaFile.INCLUDE_BLURAYS_AS_DVDS)));
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsBluRay", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if this MediaFile represents BluRay content.
       * @param MediaFile the MediaFile object
       * @return true if this MediaFile represents BluRay content, false otherwise
       * @since 6.6
       *
       * @declaration public boolean IsBluRay(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.isBluRay());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsDVDDrive", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if this MediaFile represents the physical DVD drive in the system. Use this MediaFile to playback DVDs from an optical drive.
       * @param MediaFile the MediaFile object
       * @return true if this MediaFile represents the physical DVD drive in the system, false otherwise
       *
       * @declaration public boolean IsDVDDrive(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.getGeneralType() == MediaFile.MEDIAFILE_DEFAULT_DVD_DRIVE);
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsMusicFile", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if this MediaFile's content is audio only.
       * @param MediaFile the MediaFile object
       * @return true if this MediaFile's content is audio only, false otherwise
       *
       * @declaration public boolean IsMusicFile(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.isMusic());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsVideoFile", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if this MediaFile's content is an audio/video or video file (this will be false for DVD/BluRay content)
       * @param MediaFile the MediaFile object
       * @return true if this MediaFile's content is an audio/video or video file (this will be false for DVD/BluRay content), false otherwise
       *
       * @declaration public boolean IsVideoFile(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.isVideo());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsPictureFile", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if this MediaFile's content represents a picture file
       * @param MediaFile the MediaFile object
       * @return true if this MediaFile's content represents a picture file, false otherwise
       *
       * @declaration public boolean IsPictureFile(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.isPicture());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsTVFile", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if this MediaFile represents recorded television content
       * @param MediaFile the MediaFile object
       * @return true if this MediaFile represents recorded television content, false otherwise
       *
       * @declaration public boolean IsTVFile(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.isTV());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetSegmentFiles", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns the list of files that make up the specified MediaFile object. A MediaFile object can represent more than
       * one physical file on disk. This occurs when a recording of a television show is not contiguous; this can happen for various
       * reasons including the user changing the channel or restarting the system.
       * @param MediaFile the MediaFile object
       * @return the list of files that make up this MediaFile object
       *
       * @declaration public java.io.File[] GetSegmentFiles(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return (mf == null) ? null : mf.getFiles();
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetMediaTitle", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns the title for the specified MediaFile object
       * @param MediaFile the MediaFile object
       * @return the title for the specified MediaFile object
       *
       * @declaration public String GetMediaTitle(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return mf == null ? "" : mf.getMediaTitle();
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetMediaFileRelativePath", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns the path of this MediaFile object relative to the root of the import directory it is in.
       * @param MediaFile the MediaFile object
       * @return the path of this MediaFile object relative to the root of the import directory it is in
       * @since 7.0
       *
       * @declaration public String GetMediaFileRelativePath(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return mf == null ? "" : mf.getName();
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetParentDirectory", 1, new String[] { "MediaFile" })
    {
      /**
       * Gets the directory that the files for this MediaFile are in.
       * @param MediaFile the MediaFile object
       * @return the directory that the files for this MediaFile are in
       *
       * @declaration public java.io.File GetParentDirectory(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return mf == null ? null : mf.getParentFile();
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetSize", 1, new String[] { "MediaFile" })
    {
      /**
       * Gets the total size in bytes of the files on disk that represent this MediaFile
       * @param MediaFile the MediaFile object
       * @return the total size in bytes of the files on disk that represent this MediaFile
       *
       * @declaration public long GetSize(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (isNetworkedMediaFileCall(stack, 0))
        {
          return makeNetworkedCall(stack);
        }
        MediaFile mf = getMediaFile(stack);
        return new Long(mf == null ? 0 : mf.getSize());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetFullImage", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns the MetaImage object which represents the picture file for this MediaFile. If the specified MediaFile
       * is not a picture file, then null is returned
       * @param MediaFile the MediaFile object
       * @return the MetaImage object which represents the picture file for this MediaFile. If the specified MediaFile
       *           is not a picture file, then null is returned
       *
       * @declaration public MetaImage GetFullImage(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return mf == null ? null : mf.getFullImage();
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GenerateThumbnail", new String[] { "MediaFile", "Time", "Width", "Height", "File" })
    {
      /**
       * Generates a thumbnail for the specified MediaFile at the requested offset time in the file using the desired width &amp; height.
       * The resulting thumbnail will be saved to the specified file. This call DOES NOT need to be used for GetThumbnail to work properly; this
       * API call is intended as an extra for developers who want additional thumbnails beyond the one that is normally auto-generated for MediaFiles.
       * This API call will not return until the generation of the thumbnail is complete. If both width &amp; height are zero, then the size will be determined
       * automatically to match the aspect ratio of the video (the largest dimension will match what SageTV uses internally for thumbnail sizes). If only one
       * of width or height is zero, then the other dimension will be determined automatically to match the aspect ratio of the video.
       * @param MediaFile the MediaFile object, must be a Video file (no BluRays or DVDs)
       * @param Time the offset time in seconds at which the thumbnail should be generated (relative to the start of the file), while fractional seconds are supported, accuracy cannot be guaranteed
       * @param Width the width in pixels of the desired thumbnail
       * @param Height the height in pixels of the desired thumbnail
       * @param File the file path to save the thumbnail to
       * @return true if the generation succeeded, false if it failed
       * @since 7.1
       *
       * @declaration public boolean GenerateThumbnail(MediaFile MediaFile, float Time, int Width, int Height, java.io.File File);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        int height = getInt(stack);
        int width = getInt(stack);
        float time = getFloat(stack);
        float orgtime = time;
        MediaFile mf = getMediaFile(stack);
        if (mf == null || !mf.isVideo() || mf.isDVD() || mf.isBluRay())
          return Boolean.FALSE;
        // Find the right segment
        int segment = mf.segmentLocation(Math.round(time * 1000) + mf.getRecordTime(), true);
        String pathString = mf.getFile(segment).toString();
        time -= (mf.getStart(segment) - mf.getRecordTime())/1000.f;
        if (time < 0 || time*1000 > mf.getDuration(segment))
        {
          if (Sage.DBG) System.out.println("GenerateThumbnail returning false because the target time is outside of a segment location time=" + orgtime + " " + mf);
          return Boolean.FALSE;
        }
        if (pathString != null && Sage.client && mf.getGeneralType() != MediaFile.MEDIAFILE_LOCAL_PLAYBACK)
        {
          pathString = "stv://" + Sage.preferredServer + "/" + pathString;
        }
        else if (mf.isRecording())
        {
          // This works around activeFile issues with FFMPEG because the MediaServer always knows that state
          pathString = "stv://localhost/" + pathString;
        }
        pathString = pathString == null ? null : IOUtils.getLibAVFilenameString(pathString);
        boolean deinterlaceGen = false;
        sage.media.format.ContainerFormat cf = mf.getFileFormat();
        if (cf != null)
        {
          sage.media.format.VideoFormat vidForm = cf.getVideoFormat();
          if (vidForm != null && vidForm.isInterlaced())
            deinterlaceGen = true;
        }
        if (width == 0 || height == 0)
        {
          float aspectRatio = mf.getPrimaryVideoAspectRatio(false);
          if (aspectRatio <= 0)
            aspectRatio = 1;
          if (width == 0 && height == 0)
          {
            width = Sage.getInt("ui/thumbnail_width_new", 512);
            height = Sage.getInt("ui/thumbnail_height_new", 512);
            // Maintain aspect ratio
            if (aspectRatio > 1.0f)
              height = Math.round(width / aspectRatio);
            else
              width = Math.round(aspectRatio * height);
          }
          else if (width == 0)
            width = Math.round(aspectRatio * height);
          else
            height = Math.round(width / aspectRatio);
        }
        height -= height % 2;
        width -= width % 2;
        String res;
        try
        {
          res = mf.execVideoThumbGen(pathString, deinterlaceGen, time, width, height, true, f);
        }
        catch (Exception e)
        {
          return Boolean.FALSE;
        }
        if (f.isFile() && f.length() > 0)
          return Boolean.TRUE;
        if (Sage.DBG) System.out.println("Error with GenerateThumbnail for " + mf + " Output of generator: " + res);
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetThumbnail", 1, new String[] { "MediaFile" })
    {
      /**
       * Gets the representative thumbnail image which should be used for iconic display of this MediaFile. For picture files,
       * this will be a thumbnail image. For music files it will be the album art. For any other files it'll be the thumbnail for
       * the file if one exists, otherwise it'll be the channel logo for the file. If none of those exist then null is returned.
       * @param MediaFile the MediaFile object
       * @return the representative thumbnail image which should be used for iconic display of this MediaFile
       *
       * @declaration public MetaImage GetThumbnail(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return mf == null ? null : mf.getThumbnail(stack.getUIComponent());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsThumbnailLoaded", new String[] { "MediaFile" })
    {
      /**
       * Checks whether the passed thumbnail for the specified MediaFile is loaded
       * into system memory or into the VRAM cache of the corresponding UI making the call.
       * @param MediaFile the MediaFile object
       * @return true if the thumbnail image for the specified MediaFile is loaded into system memory or the calling UI's VRAM, false otherwise
       * @since 6.1
       *
       * @declaration public boolean IsThumbnailLoaded(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return (mf != null && mf.isThumbnailLoaded(stack.getUIMgr())) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "HasSpecificThumbnail", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if this MediaFile object has a thumbnail for it that is unique to the content itself. This is true
       * for any music file with album art or any other MediaFile that has another file on disk which contains the representative thumbnail.
       * @param MediaFile the MediaFile object
       * @return true if this MediaFile object has a thumbnail for it that is unique to the content itself, false otherwise
       *
       * @declaration public boolean HasSpecificThumbnail(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.hasSpecificThumbnail());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "HasAnyThumbnail", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if this MediaFile object has a thumbnail representation of it. If this is true, then {@link #GetThumbnail GetThumbnail()}
       * will not return null.
       * @param MediaFile the MediaFile object
       * @return true if this MediaFile object has a thumbnail representation of it, false otherwise
       *
       * @declaration public boolean HasAnyThumbnail(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.hasThumbnail());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsFileCurrentlyRecording", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns true if this MediaFile is currently in the process of recording.
       * @param MediaFile the MediaFile object
       * @return true if this MediaFile is currently in the process of recording, false otherwise
       *
       * @declaration public boolean IsFileCurrentlyRecording(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return Boolean.valueOf(mf != null && mf.isRecording());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "DeleteFile", 1, new String[] { "MediaFile" }, true)
    {
      /**
       * Deletes the files that correspond to this MediaFile from disk and also deletes the MediaFile object from the database.
       * NOTE: This actually delete the files from the disk.
       * This has a slightly different effect on Intelligent Recording versus the {@link #DeleteFileWithoutPrejudice DeleteFileWithoutPrejudice()}
       * @param MediaFile the MediaFile object to delete
       * @return true if the deletion succeeded, false otherwise. A deletion can fail because the file is currently being recorded or watched or because the native filesystem is unable to delete the file.
       *
       * @declaration public boolean DeleteFile(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack); if (mf == null || !Permissions.hasPermission(Permissions.PERMISSION_DELETE, stack.getUIMgr())) return Boolean.FALSE;
        return Boolean.valueOf(SeekerSelector.getInstance().destroyFile(mf, true, "User", stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null));
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "DeleteFileWithoutPrejudice", 1, new String[] { "MediaFile" }, true)
    {
      /**
       * Deletes the files that correspond to this MediaFile from disk and also deletes the MediaFile object from the database.
       * NOTE: This actually delete the files from the disk.
       * This has a slightly different effect on Intelligent Recording versus {@link #DeleteFile DeleteFile()}. DeleteFileWithoutPrejudice should
       * be used when the file was incorrectly recorded or in other cases where this deletion decision should have no effect on intelligent recording.
       * @param MediaFile the MediaFile object to delete
       * @return true if the deletion succeeded, false otherwise. A deletion can fail because the file is currently being recorded or watched or because the native filesystem is unable to delete the file.
       *
       * @declaration public boolean DeleteFileWithoutPrejudice(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack); if (mf == null || !Permissions.hasPermission(Permissions.PERMISSION_DELETE, stack.getUIMgr())) return Boolean.FALSE;
        if (Permissions.hasPermission(Permissions.PERMISSION_RECORDINGSCHEDULE, stack.getUIMgr()))
          BigBrother.clearWatched(mf.getContentAiring());
        return Boolean.valueOf(SeekerSelector.getInstance().destroyFile(mf, false, "User", stack.getUIMgr() != null ? stack.getUIMgr().getLocalUIClientName() : null));
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetFileDuration", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns the total duration of the content in this MediaFile
       * @param MediaFile the MediaFile object
       * @return the total duration in milliseconds of the content in the specified MediaFile
       *
       * @declaration public long GetFileDuration(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return new Long(mf == null ? 0 : mf.getRecordDuration());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetFileStartTime", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns the starting time for the content in ths specified MediaFile. This corresponds to when the file's recording started or the
       * timestamp on the file itself. See java.lang.System.currentTimeMillis() for information on the time units.
       * @param MediaFile the MediaFile object
       * @return the starting time for the content in the specified MediaFile
       *
       * @declaration public long GetFileStartTime(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return new Long(mf == null ? 0 : mf.getRecordTime());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetFileEndTime", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns the ending time for the content in ths specified MediaFile. This corresponds to when the file's recording ended or the
       * timestamp on the file itself plus the file's duration. See java.lang.System.currentTimeMillis() for information on the time units.
       * @param MediaFile the MediaFile object
       * @return the ending time for the content in the specified MediaFile
       *
       * @declaration public long GetFileEndTime(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        if (mf != null && FileDownloader.isDownloading(mf.getFile(0)))
        {
          FileDownloader fd = FileDownloader.getFileDownloader(mf.getFile(0));
          long dlTime = fd.getLastDownloadTimestamp();
          if (dlTime > 0)
            return new Long(mf.getRecordTime() + dlTime);
        }
        return new Long(mf == null ? 0 : mf.getRecordEnd());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "CopyToLocalFile", 2, new String[] { "MediaFile", "LocalFile" })
    {
      /**
       * Downloads the specified MediaFile from the SageTV server and saves it as the specified LocalFile. This call should
       * only be made by SageTV Client.
       * @param MediaFile the MediaFile object to download a copy of
       * @param LocalFile the destination file to store the MediaFile as on the local filesystem
       *
       * @declaration public void CopyToLocalFile(MediaFile MediaFile, java.io.File LocalFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        java.io.File f = getFile(stack);
        MediaFile mf = getMediaFile(stack);
        if (mf != null)
          mf.copyToLocalStorage(f);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetDurationForSegment", 2, new String[] { "MediaFile","SegmentNumber" })
    {
      /**
       * Returns the duration in milliseconds for the specified segment number in this MediaFile.
       * @param MediaFile the MediaFile object
       * @param SegmentNumber the 0-based segment number to get the duration of
       * @return the duration in milliseconds for the specified segment number in this MediaFile
       *
       * @declaration public long GetDurationForSegment(MediaFile MediaFile, int SegmentNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int x = getInt(stack);
        MediaFile mf = getMediaFile(stack);
        if (mf != null && FileDownloader.isDownloading(mf.getFile(0)))
        {
          FileDownloader fd = FileDownloader.getFileDownloader(mf.getFile(0));
          long dlTime = fd.getLastDownloadTimestamp();
          if (dlTime > 0)
            return new Long(dlTime);
        }
        return new Long(mf == null ? 0 : mf.getDuration(x));
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetEndForSegment", 2, new String[] { "MediaFile","SegmentNumber" })
    {
      /**
       * Gets the ending time for a specified segment number in this MediaFile.
       * @param MediaFile the MediaFile object
       * @param SegmentNumber the 0-based segment number to get the end time of
       * @return the ending time for a specified segment number in this MediaFile
       *
       * @declaration public long GetEndForSegment(MediaFile MediaFile, int SegmentNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int x = getInt(stack);
        MediaFile mf = getMediaFile(stack);
        if (mf != null && FileDownloader.isDownloading(mf.getFile(0)))
        {
          FileDownloader fd = FileDownloader.getFileDownloader(mf.getFile(0));
          long dlTime = fd.getLastDownloadTimestamp();
          if (dlTime > 0)
            return new Long(mf.getStart(x) + dlTime);
        }
        return new Long(mf == null ? 0 : mf.getEnd(x));
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetStartForSegment", 2, new String[] { "MediaFile","SegmentNumber" })
    {
      /**
       * Gets the starting time for a specified segment number in this MediaFile.
       * @param MediaFile the MediaFile object
       * @param SegmentNumber the 0-based segment number to get the start time of
       * @return the starting time for a specified segment number in this MediaFile
       *
       * @declaration public long GetStartForSegment(MediaFile MediaFile, int SegmentNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int x = getInt(stack);
        MediaFile mf = getMediaFile(stack);
        return new Long(mf == null ? 0 : mf.getStart(x));
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetFileForSegment", 2, new String[] { "MediaFile","SegmentNumber" })
    {
      /**
       * Gets the file that represents the specified segment number in this MediaFile
       * @param MediaFile the MediaFile object
       * @param SegmentNumber the 0-based segment number to get the file for
       * @return the file that represents the specified segment number in this MediaFile
       *
       * @declaration public java.io.File GetFileForSegment(MediaFile MediaFile, int SegmentNumber);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int x = getInt(stack);
        MediaFile mf = getMediaFile(stack);
        return (mf == null) ? null : mf.getFile(x);
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetNumberOfSegments", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns the number of segments in ths specified MediaFile. Each segment corresponds to a different physical file on disk.
       * @param MediaFile the MediaFile object
       * @return the number of segments in ths specified MediaFile
       *
       * @declaration public int GetNumberOfSegments(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return new Integer(mf == null ? 0 : mf.getNumSegments());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetStartTimesForSegments", 1, new String[] { "MediaFile" })
    {
      /**
       * Returns a list of all of the start times of the segments in the specified MediaFile
       * @param MediaFile the MediaFile object
       * @return a list of all of the start times of the segments in the specified MediaFile
       *
       * @declaration public long[][] GetStartTimesForSegments(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return mf == null ? null : mf.getRecordTimes();
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "MoveFileToLibrary", 1, new String[] { "MediaFile" }, true)
    {
      /**
       * Marks a MediaFile object as being 'Moved to Library' which means the {@link #IsLibraryFile IsLibraryFile()} call will
       * now return true. This can be used to help organize the recorded television files.
       * @param MediaFile the MediaFile ojbect
       *
       * @declaration public void MoveFileToLibrary(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        if (mf != null && Permissions.hasPermission(Permissions.PERMISSION_ARCHIVE, stack.getUIMgr()))
          mf.simpleArchive();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "MoveTVFileOutOfLibrary", 1, new String[] { "MediaFile" }, true)
    {
      /**
       * Un-marks a MediaFile object as being 'Moved to Library' which means the {@link #IsLibraryFile IsLibraryFile()} call will
       * no longer return true. This can only be used on recorded television files and has the opposite effect of
       * {@link #MoveFileToLibrary MoveFileToLibrary()}
       * @param MediaFile the MediaFile ojbect
       *
       * @declaration public void MoveTVFileOutOfLibrary(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        if (mf != null && mf.isTV() && Permissions.hasPermission(Permissions.PERMISSION_ARCHIVE, stack.getUIMgr()))
          mf.simpleUnarchive();
        return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "IsMediaFileObject", 1, new String[] { "Object" })
    {
      /**
       * Returns true if the specified object is a MediaFile object. No automatic type conversion will be performed on the argument.
       * This will return false if the argument is a MediaFile object, BUT that object no longer exists in the SageTV database.
       * @param Object the object to test to see if it is a MediaFile object
       * @return true if the argument is a MediaFile object, false otherwise
       *
       * @declaration public boolean IsMediaFileObject(Object Object);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof sage.vfs.MediaNode)
          o = ((sage.vfs.MediaNode) o).getDataObject();
        return Boolean.valueOf(o instanceof MediaFile && (Wizard.getInstance().isMediaFileOK((MediaFile) o) ||
            ((MediaFile) o).getGeneralType() == MediaFile.MEDIAFILE_LOCAL_PLAYBACK));
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetAlbumForFile", 1, new String[] { "MediaFile" })
    {
      /**
       * Gets the Album object that corresponds to this MediaFile. This only returns a useful object if the argument is a music file.
       * @param MediaFile the MediaFile object
       * @return the Album object that corresponds to this MediaFile
       *
       * @declaration public Album GetAlbumForFile(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        if (mf == null || !mf.isMusic()) return null;
        Wizard wiz = Wizard.getInstance();
        return wiz.getCachedAlbumForMediaFile(mf);
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetMediaFileEncoding", 1, new String[] { "MediaFile" })
    {
      /**
       * Gets the encoding that was used to record this file. This will only return something useful for recorded television files.
       * @param MediaFile the MediaFile object
       * @return the encoding that was used to record this file
       *
       * @declaration public String GetMediaFileEncoding(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return (mf != null) ? mf.getEncodedBy() : "";
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetMediaFileAiring", new String[] { "MediaFile" })
    {
      /**
       * Gets the Airing object that represents the content metadata for this MediaFile
       * @param MediaFile the MediaFile object
       * @return the Airing object that represents the content metadata for this MediaFile
       *
       * @declaration public Airing GetMediaFileAiring(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return getAir(stack);
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetMediaFileID", new String[] { "MediaFile" })
    {
      /**
       * Returns the unique ID used to identify this MediaFile. Can get used later on a call to {@link #GetMediaFileForID GetMediaFileForID()}
       * @param MediaFile the MediaFileobject
       * @return the unique ID used to identify this MediaFile
       *
       * @declaration public int GetMediaFileID(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        return (mf == null) ? null : new Integer(mf.getID());
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetMediaFileForID", new String[] { "MediaFileID" })
    {
      /**
       * Returns the MediaFile object that corresponds to the passed in ID. The ID should have been obtained from a call to {@link #GetMediaFileID GetMediaFileID()}
       * @param id the id of the MediaFile object to get
       * @return the MediaFile object that corresponds to the passed in ID
       *
       * @declaration public MediaFile GetMediaFileForID(int id);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int i = getInt(stack);
        return Wizard.getInstance().getFileForID(i);
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetMediaFileFormatDescription", -1, new String[] { "MediaFile" }, true)
    {
      /**
       * Returns a string that provides a description of this MediaFile's format, i.e. MPEG2-PS[MPEG2-Video/2.0Mbps 4:3 480i@30fps, MP2/192kbps@48kHz]
       * @param MediaFile the MediaFile object
       * @return a string that provides a description of this MediaFile's format
       * @since 5.1
       *
       * @declaration public String GetMediaFileFormatDescription(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        // NOTE: We have a secondary internal use here where we use this to refresh the subtitles that are available before we
        // watch something with SageTVClient
        boolean refreshSubs = false;
        if (curNumberOfParameters == 2)
          refreshSubs = evalBool(stack.pop());
        MediaFile mf = getMediaFile(stack);
        if (mf != null && mf.getFileFormat() != null)
        {
          if (refreshSubs)
            mf.checkForSubtitles();
          return mf.getFileFormat().getPrettyDesc();
        }
        return "";
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetMediaFileMetadata", new String[] { "MediaFile", "Name" })
    {
      /**
       * Returns a string for the corresponding metadata property in the MediaFile's format. These are set during format detection/import.
       * Names set in the property "custom_metadata_properties" (which is a semicolon/comma delimited list) will be available; as well
       * as all standard SageTV metadata fields and details on format information.
       * These include Title, Description, EpisodeName, Track, Duration, Genre, Language, RunningTime,
       * Rated, ParentalRating, PartNumber, TotalParts, HDTV, CC, Stereo, SAP, Subtitled, 3D, DD5.1, Dolby, Letterbox, Live, New, Widescreen, Surround,
       * Dubbed, Taped, Premiere, SeasonPremiere, SeriesPremiere, ChannelPremiere, SeasonFinale, SeriesFinale,  SeasonNumber, EpisodeNumber,
       * ExternalID, Album, Year, OriginalAirDate, ExtendedRatings, Misc, All "Role" Names, Format.Video.Codec,
       * Format.Video.Resolution, Format.Video.Aspect, Format.Video.Bitrate, Format.Video.Width, Format.Video.Height, Format.Video.FPS,
       * Format.Video.Interlaced, Format.Video.Progressive, Format.Video.Index, Format.Video.ID, Format.Audio.NumStreams, Format.Audio[.#].Codec, Format.Audio[.#].Channels,
       * Format.Audio[.#].Language, Format.Audio[.#].SampleRate, Format.Audio[.#].BitsPerSample, Format.Audio[.#].Index, Format.Audio[.#].ID, Format.Subtitle.NumStreams,
       * Format.Subtitle[.#].Codec, Format.Subtitle[.#].Language, Format.Subtitle[.#].Index, Format.Subtitle[.#].ID, Format.Container and Picture.Resolution
       * @param MediaFile the MediaFile object
       * @param Name the name of the property to get
       * @return a string corresponding to the metadata property value, or the emptry string if it is undefined
       * @since 6.6
       *
       * @declaration public String GetMediaFileMetadata(MediaFile MediaFile, String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String name = getString(stack);
        MediaFile mf = getMediaFile(stack);
        if (mf != null)
        {
          String rv = mf.getMetadataProperty(name);
          return (rv == null) ? "" : rv;
        }
        return "";
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "GetMediaFileMetadataProperties", new String[] { "MediaFile" })
    {
      /**
       * Returns a java.util.Properties object that contains all of the metadata properties for a MediaFile object.
       * This will only include properties that can be modified (i.e. no format information is included). These properties will include all
       * the standard database fields, as well as any custom metadata properties that were set for this MediaFile object.
       * See {@link #SetMediaFileMetadata SetMediaFileMetadata()} and {@link #GetMediaFileMetadata GetMediaFileMetadata()} for more details on those properties.
       * @param MediaFile the MediaFile object
       * @return a java.util.Properties object with all the metadata properties for this MediaFile, this is a copy and is safe to modify
       * @since 7.1
       *
       * @declaration public java.util.Properties GetMediaFileMetadataProperties(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        if (mf != null)
        {
          return mf.getMetadataProperties();
        }
        return new java.util.Properties();
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "SetMediaFileMetadata", new String[] { "MediaFile", "Name" , "Value"}, true)
    {
      /**
       * Sets the corresponding metadata property in the MediaFile's format. These are set in the database and are also exported
       * to the corresponding .properties file for that MediaFile. When it exports it will append these updates to the .properties file.
       * It will also update the property "custom_metadata_properties" (which is a semicolon/comma delimited list) which tracks the extra
       * metadata properties that should be retained. Usage of any of the following names will update the corresponding Airing/Show object
       * for the MediaFile as well: Title, Description, EpisodeName, Track, Duration, Genre, Language, RunningTime,
       * Rated, ParentalRating, PartNumber, TotalParts, HDTV, CC, Stereo, SAP, Subtitled, 3D, DD5.1, Dolby, Letterbox, Live, New,
       * Widescreen, Surround, Dubbed, Taped, SeasonNumber, EpisodeNumber Premiere, SeasonPremiere, SeriesPremiere, ChannelPremiere,
       * SeasonFinale, SeriesFinale, ExternalID, Album, Year, OriginalAirDate, ExtendedRatings, Misc and All "Role" Names
       * @param MediaFile the MediaFile object
       * @param Name the name of the property to set
       * @param Value the value of the property to set
       * @since 6.6
       *
       * @declaration public void SetMediaFileMetadata(MediaFile MediaFile, String Name, String Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String value = getString(stack);
        String name = getString(stack);
        MediaFile mf = getMediaFile(stack);
        if (mf != null && mf.getFileFormat() != null && Permissions.hasPermission(Permissions.PERMISSION_EDITMETADATA, stack.getUIMgr()))
        {
          mf.addMetadata(name, value);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "RotatePictureFile", new String[] { "MediaFile", "Degrees" }, true)
    {
      /**
       * Performs a lossless rotation of the specified JPEG picture file (90, 180 or 270 degrees). This will modify the file that is
       * stored on disk.
       * @param MediaFile the MediaFile object
       * @param Degrees the number of degress to rotate the picture in the clockwise direction, can be a positive or negative value and must be a multiple of 90
       * @return true if the rotation was successful, false otherwise
       * @since 5.1
       *
       * @declaration public boolean RotatePictureFile(MediaFile MediaFile, int Degrees);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int degrees = getInt(stack);
        MediaFile mf = getMediaFile(stack);
        if (mf != null && mf.isPicture() && sage.media.format.MediaFormat.JPEG.equals(mf.getContainerFormat()) &&
            Permissions.hasPermission(Permissions.PERMISSION_PICTUREROTATION, stack.getUIMgr()))
        {
          /*
           * To rotate a picture file, we go through these steps:
           * 1. Use jpegtran to generate the rotated image, save it as a temp file in the same location as the original with a TEMP filename prefix
           * 2. Delete the original picture file and rename the tmp file to be that picture filename
           * 3. Set the modification time of the modified file to be 1 + the timestamp of the original file
           * 4. Call reinitializeMetadata on the MediaFile so it recognizes the new timestamp and pushes out the change to the clients
           * 5. Clear the MediaFile from the MetaImage cache so it gets re-generated properly.
           */
          degrees = degrees % 360;
          if (degrees < 0)
            degrees += 360;
          if (degrees == 0)
            return Boolean.TRUE;
          return (performPictureOp(mf, "-rotate", Integer.toString(degrees)) ? Boolean.TRUE : Boolean.FALSE);
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "FlipPictureFile", new String[] { "MediaFile", "Horizontal" }, true)
    {
      /**
       * Performs a lossless flip of the specified JPEG picture file. This will modify the file that is
       * stored on disk.
       * @param MediaFile the MediaFile object
       * @param Horizontal true if it should be flipped horizontally (i.e. around a vertical axis), false if it should be flipped vertically (i.e. around a horizontal axis)
       * @return true if the flip was successful, false otherwise
       * @since 5.1
       *
       * @declaration public boolean FlipPictureFile(MediaFile MediaFile, boolean Horizontal);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean hflip = evalBool(stack.pop());
        MediaFile mf = getMediaFile(stack);
        if (mf != null && mf.isPicture() && sage.media.format.MediaFormat.JPEG.equals(mf.getContainerFormat()) &&
            Permissions.hasPermission(Permissions.PERMISSION_PICTUREROTATION, stack.getUIMgr()))
        {
          /*
           * To rotate a picture file, we go through these steps:
           * 1. Use jpegtran to generate the rotated image, save it as a temp file in the same location as the original with a TEMP filename prefix
           * 2. Delete the original picture file and rename the tmp file to be that picture filename
           * 3. Set the modification time of the modified file to be 1 + the timestamp of the original file
           * 4. Call reinitializeMetadata on the MediaFile so it recognizes the new timestamp and pushes out the change to the clients
           * 5. Clear the MediaFile from the MetaImage cache so it gets re-generated properly.
           */
          return (performPictureOp(mf, "-flip", hflip ? "horizontal" : "vertical") ? Boolean.TRUE : Boolean.FALSE);
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "CanAutorotatePictureFile", new String[] { "MediaFile" })
    {
      /**
       * Returns true if the specified picture file can be autorotated and is currently not in that autorotated position
       * @param MediaFile the MediaFile object
       * @return true if the specified picture file can be autorotated and is currently not in that autorotated position
       * @since 6.4
       *
       * @declaration public boolean CanAutorotatePictureFile(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        if (mf != null && mf.isPicture() && sage.media.format.MediaFormat.JPEG.equals(mf.getContainerFormat()))
        {
          return mf.getContentAiring().getOrientation() > 1 ? Boolean.TRUE : Boolean.FALSE;
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "AutorotatePictureFile", new String[] { "MediaFile" })
    {
      /**
       * Automatically rotates the specified picture file according to the orientation set in the EXIF data.
       * @param MediaFile the MediaFile object that represents the picture
       * @return true if the automatic rotation succeeded, false otherwise
       * @since 6.4
       *
       * @declaration public boolean AutorotatePictureFile(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        if (mf != null && mf.isPicture() && sage.media.format.MediaFormat.JPEG.equals(mf.getContainerFormat()) &&
            Permissions.hasPermission(Permissions.PERMISSION_PICTUREROTATION, stack.getUIMgr()))
        {
          if (mf.getContentAiring().getOrientation() > 1)
          {
            java.io.File srcFile = mf.getFile(0);
            // Backup the original file first if we haven't already
            if (Sage.getBoolean("temp_backup_picture_file_before_rotateflip", true) &&
                !new java.io.File(srcFile.toString() + ".original").isFile())
            {
              IOUtils.copyFile(srcFile, new java.io.File(srcFile.toString() + ".original"));
            }
            String jheadToolPath = sage.Sage.getToolPath("jhead");
            String[] jheadArgs = new String[] { jheadToolPath, "-autorot", srcFile.toString() };
            if (IOUtils.exec2(jheadArgs) == 0)
            {
              if (Sage.DBG) System.out.println("Successfully auto-rotated jpeg image");
              MetaImage.clearFromCache(new MetaImage.MediaFileThumbnail(mf));
              MetaImage.clearFromCache(mf);
              // In case the offset/size changed for some unknown reason, this is very low overhead to reparse the JPEG file
              mf.reinitializeMetadata(true, true, mf.getName().substring(0, mf.getName().length() - srcFile.getName().length()));
              return Boolean.TRUE;
            }
            else
            {
              if (Sage.DBG) System.out.println("ERROR Autorotation of JPEG file failed");
            }
          }
        }
        return Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("MediaFile", "RegeneratePictureThumbnail", new String[] { "MediaFile" })
    {
      /**
       * Regenerates the thumbnail associated with the specified picture file. Sometimes the rotation may be mis-aligned from
       * the thumbnail and this allows a way to repair that.
       * @param MediaFile the MediaFile object
       * @since 6.4
       *
       * @declaration public void RegeneratePictureThumbnail(MediaFile MediaFile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        MediaFile mf = getMediaFile(stack);
        if (mf != null && mf.isPicture())
        {
          // If it has an embedded thumbnail; then we will regenerate the thumbnail and put it back inside
          // the EXIF header. Otherwise we just regenerate the thumbnail as normal by simply deleting the old
          // generated thumbnail.
          if (!mf.isThumbnailEmbedded())
          {
            java.io.File thumbPath = mf.getGeneratedThumbnailFileLocation();
            thumbPath.delete();
            MetaImage.clearFromCache(thumbPath);
          }
          else if (Sage.client)
          {
            // the embedded thumbnails need to be processed on the server
            stack.push(mf);
            return makeNetworkedCall(stack);
          }
          else if (sage.media.format.MediaFormat.JPEG.equals(mf.getContainerFormat()))
          {
            // Figure out the size of the thumbnail (it should match the current dimensions; but may need to be reoriented)
            MetaImage fullImage = MetaImage.getMetaImage(mf);
            int fullWidth = fullImage.getWidth();
            int fullHeight = fullImage.getHeight();
            MetaImage thumbImage = MetaImage.getMetaImage(new MetaImage.MediaFileThumbnail(mf));
            int finalThumbWidth = thumbImage.getWidth();
            int finalThumbHeight = thumbImage.getHeight();
            if ((fullWidth > fullHeight && finalThumbWidth < finalThumbHeight) ||
                (fullWidth < fullHeight && finalThumbWidth > finalThumbHeight))
            {
              int swap = finalThumbWidth;
              finalThumbWidth = finalThumbHeight;
              finalThumbHeight = swap;
            }
            java.io.File srcFile = mf.getFile(0);
            java.io.File tmpFile = new java.io.File(srcFile.getParentFile(), "TEMP" + srcFile.getName());
            int x = 1;
            while (tmpFile.isFile())
              tmpFile = new java.io.File(srcFile.getParentFile(), "TEMP" + (x++) + srcFile.getName());
            String tmpPath = tmpFile.toString();
            if (sage.media.image.ImageLoader.createThumbnail(srcFile.toString(),
                tmpPath, finalThumbWidth, finalThumbHeight, 0))
            {
              // Backup the original file first if we haven't already
              if (Sage.getBoolean("temp_backup_picture_file_before_rotateflip", true) &&
                  !new java.io.File(srcFile.toString() + ".original").isFile())
              {
                IOUtils.copyFile(srcFile, new java.io.File(srcFile.toString() + ".original"));
              }
              // Now re-insert it into the exif data
              String jheadToolPath = sage.Sage.getToolPath("jhead");
              String[] jheadArgs = new String[] { jheadToolPath, "-rt", tmpPath, "-norot", srcFile.toString() };
              if (IOUtils.exec2(jheadArgs) == 0)
              {
                if (Sage.DBG) System.out.println("Successfully regenerated thumbnail and inserted it into exif header!");
                MetaImage.clearFromCache(new MetaImage.MediaFileThumbnail(mf));
                // In case the offset/size changed for some unknown reason, this is very low overhead to reparse the JPEG file
                mf.reinitializeMetadata(true, true, mf.getName().substring(0, mf.getName().length() - (srcFile.getName().length())));
                if (Sage.DBG) System.out.println("MF reinitialized to:" + mf);
              }
              else
              {
                if (Sage.DBG) System.out.println("ERROR Re-insertion of regenerated thumbnail into jpeg file exif header failed");
              }
            }
            else
            {
              if (Sage.DBG) System.out.println("ERROR regeneration of picture file thumbnail failed");
            }
            tmpFile.delete();
          }
        }
        return null;
      }});
    /*
		rft.put(new PredefinedJEPFunction("MediaFile", "", 1, new String[] { "MediaFile" })
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 return getMediaFile(stack);
			}});
     */
  }

  private static boolean performPictureOp(MediaFile mf, String opcmd1, String opcmd2)
  {
    String srcPath = mf.getFile(0).toString();
    java.io.File srcFile = new java.io.File(srcPath);
    java.io.File tmpFile = new java.io.File(srcFile.getParentFile(), "TEMP" + srcFile.getName());
    int x = 1;
    while (tmpFile.isFile())
      tmpFile = new java.io.File(srcFile.getParentFile(), "TEMP" + (x++) + srcFile.getName());
    String tmpPath = tmpFile.toString();

    String toolPath = sage.Sage.getToolPath("jpegtran");
    if (Sage.DBG) System.out.println("jpegtran bin at " + toolPath);

    String[] jpegtranArgs = new String[] { toolPath,
        "-trim", "-copy", "all", opcmd1, opcmd2, "-outfile", tmpPath,
        srcPath };
    if (IOUtils.exec2(jpegtranArgs) != 0)
      return false;
    if (tmpFile.length() < 0.90*srcFile.length())
    {
      System.out.println("ERROR in jpeg rotation, bailing to prevent loss!!!! " + srcFile);
      return false;
    }
    long srcTimestamp = srcFile.lastModified();
    if (Sage.getBoolean("temp_backup_picture_file_before_rotateflip", true) &&
        !new java.io.File(srcFile.toString() + ".original").isFile())
    {
      srcFile.renameTo(new java.io.File(srcFile.toString() + ".original"));
    }
    else if (!srcFile.delete())
    {
      System.out.println("ERROR in jpeg rotation, couldn't delete source file!" + srcFile);
      return false;
    }
    if (!tmpFile.renameTo(srcFile))
    {
      System.out.println("SERIOUS ERROR in jpeg rotation, couldn't rename temp file!!" + tmpFile);
      return false;
    }
    // adding only 1 would be nice, but I've got reservations about timing accuracy on some filesystems
    // and it can't just be 2000 because we test against that in MediaFile due to accuracy issues
    if (!srcFile.setLastModified(srcTimestamp + 4500))
    {
      if (Sage.DBG) System.out.println("Failed chaging rotated picture file's timestamp:" + srcFile);
    }

    // Now extract the thumbnail from the EXIF tag if there and then rotate the thumbnail as well and save it back into the image
    String jheadToolPath = sage.Sage.getToolPath("jhead");
    String[] jheadArgs = new String[] { jheadToolPath, "-st", tmpPath, srcPath };

    // Extract the thumbnail
    if (mf.isThumbnailEmbedded())
    {
      if (IOUtils.exec2(jheadArgs) != 0 || !tmpFile.isFile())
      {
        // This is OK because our own thumb gen system will take over and fix the problem
        if (Sage.DBG) System.out.println("WARNING: extraction of thumbnail failed so we can't autorotate the internal thumbnail");
        tmpFile.delete();
      }
      else
      {
        java.io.File tmpFile2 = new java.io.File(srcFile.getParentFile(), "TEMP" + srcFile.getName());
        x = 2;
        while (tmpFile2.isFile())
          tmpFile2 = new java.io.File(srcFile.getParentFile(), "TEMP" + (x++) + srcFile.getName());
        jpegtranArgs[7] = tmpFile2.toString();
        jpegtranArgs[8] = tmpFile.toString();
        // rotate the thumbnail
        if (IOUtils.exec2(jpegtranArgs) != 0)
        {
          if (Sage.DBG) System.out.println("WARNING: rotation of extracted exif thumbnail failed");
        }
        else
        {
          // reinsert the thumbnail
          jheadArgs = new String[] { jheadToolPath, "-rt", tmpFile2.toString(), "-norot", srcPath };
          if (IOUtils.exec2(jheadArgs) == 0)
          {
            if (Sage.DBG) System.out.println("Successfully rotated internal exif thumbnail for jpeg file");
            MetaImage.clearFromCache(new MetaImage.MediaFileThumbnail(mf));
          }
          else
          {
            if (Sage.DBG) System.out.println("Re-insertion of rotated thumbnail into jpeg file exif header failed");
          }
        }
        tmpFile.delete();
        tmpFile2.delete();
      }
    }

    mf.reinitializeMetadata(true, false, mf.getName().substring(0, mf.getName().length() - (srcFile.getName().length())));
    if (Sage.DBG) System.out.println("MF reinitialized to:" + mf);
    MetaImage.clearFromCache(mf);
    return true;

  }
}
