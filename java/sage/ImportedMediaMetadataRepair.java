/*
 * Copyright 2026 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 */
package sage;

import sage.media.format.ContainerFormat;

import java.io.File;

/**
 * Repairs an imported video's database timing when its original format probe
 * failed. A one-millisecond imported video is SageTV's historical sentinel for
 * unknown duration; presenting it to an STV makes the timeline appear complete
 * and clamps every UI seek to the beginning.
 */
final class ImportedMediaMetadataRepair
{
  private static final long MINIMUM_REPAIR_FILE_SIZE = 64 * 1024;

  private ImportedMediaMetadataRepair()
  {
  }

  static boolean repairBeforePlayback(MediaFile mediaFile)
  {
    // Valid imported files must remain untouched. Keep this database-repair
    // escape hatch opt-in so ordinary playback never mutates metadata merely
    // because a newer Core is present.
    if (!Sage.getBoolean("videoframe/repair_invalid_imported_metadata_on_playback", false) || mediaFile == null)
      return false;

    File mediaPath = mediaFile.getNumSegments() == 1 ? mediaFile.getFile(0) : null;
    ContainerFormat storedFormat = mediaFile.fileFormat;
    if (!shouldRepair(mediaFile.isImported(), mediaFile.isLocalFile(), mediaFile.isRecording(),
        mediaFile.isAnyLiveStream(), mediaFile.isDVD() || mediaFile.isBluRay(),
        mediaFile.isPicture(), mediaFile.isMusic(), mediaFile.getNumSegments(),
        mediaPath != null && mediaPath.isFile(), mediaPath == null ? 0 : mediaPath.length(),
        mediaFile.getNumSegments() == 1 ? mediaFile.getDuration(0) : 0,
        storedFormat == null ? 0 : storedFormat.getDuration(),
        storedFormat == null ? 0 : storedFormat.getNumberOfStreams()))
      return false;

    String storedName = mediaFile.getName();
    String fileName = mediaPath.getName();
    String namePrefix = storedName != null && storedName.endsWith(fileName) ?
        storedName.substring(0, storedName.length() - fileName.length()) : "";
    System.out.println("Repairing invalid imported media metadata before playback: file=" + mediaPath +
        " storedDuration=" + mediaFile.getDuration(0) + " format=" + storedFormat);
    boolean reinitialized = mediaFile.reinitializeMetadata(false, true, namePrefix);
    if (reinitialized)
      System.out.println("Imported media metadata repair complete: file=" + mediaPath +
          " duration=" + mediaFile.getDuration(0) + " format=" + mediaFile.fileFormat);
    return reinitialized;
  }

  static boolean shouldRepair(boolean imported, boolean local, boolean recording,
      boolean liveStream, boolean disc, boolean picture, boolean music,
      int segmentCount, boolean regularFile, long fileLength, long storedDuration,
      long formatDuration, int streamCount)
  {
    if (!imported || !local || recording || liveStream || disc || picture || music)
      return false;
    if (segmentCount != 1 || !regularFile || fileLength < MINIMUM_REPAIR_FILE_SIZE)
      return false;

    return storedDuration <= 1 && (formatDuration <= 1 || streamCount == 0);
  }
}
