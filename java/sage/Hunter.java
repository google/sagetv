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

import java.io.File;
import java.util.Comparator;
import java.util.Set;

/**
 * Hunter is an intermediary interface to switch between Seeker and Seeker2 + Library.
 */
public interface Hunter extends Runnable
{
  // *****SEEKER*****
  public void kick();

  public MediaFile getCurrRecordMediaFile(CaptureDevice capDev);

  public MediaFile getCurrRecordFileForClient(UIClient uiClient);

  public MediaFile getCurrRecordFileForClient(UIClient uiClient, boolean syncLock);

  public CaptureDevice getCaptureDeviceControlledByClient(UIClient uiClient);

  public MediaFile[] getCurrRecordFiles();

  public boolean isAClientControllingCaptureDevice(CaptureDevice capDev);

  public void checkForEncoderHalts();

  public boolean isMediaFileBeingViewed(MediaFile mf);

  public boolean isPrepped();

  public void run();

  public void releaseAllEncoding();

  public void goodbye();

  public void finishWatch(UIClient uiClient);

  public int forceChannelTune(String mmcInputName, String chanString, UIClient uiClient);

  public MediaFile requestWatch(MediaFile watchFile, int[] errorReturn, UIClient uiClient);

  public MediaFile requestWatch(Airing watchAir, int[] errorReturn, UIClient uiClient);

  public int record(final Airing watchAir, UIClient uiClient);

  public void cancelRecord(final Airing watchAir, UIClient uiClient);

  public int modifyRecord(long startTimeModify, long endTimeModify, ManualRecord orgMR, UIClient uiClient);

  public int timedRecord(long startTime, long stopTime, int stationID, int recurrence, Airing recAir, UIClient uiClient);

  public Airing[] getIRScheduledAirings();

  public Object[] getScheduledAirings();

  public Airing[] getInterleavedScheduledAirings();

  public Airing[] getScheduledAiringsForSource(String sourceName);

  public Airing[] getInterleavedScheduledAirings(long startTime, long stopTime);

  public Airing[] getScheduledAiringsForSource(String sourceName, long startTime, long stopTime);

  public Airing[] getRecentWatches(long time);

  public String getDefaultQuality();

  public void setDefaultQuality(String newQuality);

  public boolean getDisableProfilerRecording();

  public void setDisableProfilerRecording(boolean x);

  public boolean getUseDTVMajorMinorChans();

  /**
   * This value is set, but never used for anything. We should get rid of it and remove the API
   * function since it serves no purpose.
   */
  public void setUseDTVMajorMinorChans(boolean x);

  public CaptureDeviceInput getInputForCurrRecordingFile(MediaFile mf);

  public void requestDeviceReset(CaptureDevice capDev);

  public boolean requiresPower();

  // *****LIBRARY*****
  public boolean hasImportableFileExtension(String s);

  public int guessImportedMediaMaskFast(String s);

  public int guessImportedMediaMask(String s);

  public boolean isAutoImportEnabled();

  public boolean isDoingImportScan();

  public void scanLibrary(boolean waitTillComplete);

  public boolean destroyFile(MediaFile mf, boolean considerInProfile, String reason);

  public boolean destroyFile(MediaFile mf, boolean considerInProfile, String reason, String uiContext);

  public File[] getArchiveDirectories(int importMask);

  public void addArchiveDirectory(String s, int importMask);

  public void removeArchiveDirectory(File f, int importMask);

  public File[] getSmbMountedFolders();

  public boolean isSmbMountedFolder(File f);

  /**
   * [0] - Used, [1] - Total
   */
  public long[] getUsedAndTotalVideoDiskspace();

  public long getAvailVideoDiskspace();

  public long getUsedVideoDiskspace();

  public long getUsedImportedLibraryDiskspace();

  public long getTotalImportedLibraryDuration();

  public void processFileExport(File[] files, byte acquisitionTech);

  public boolean requestFileStorage(File theFile, long theSize);

  public void clearFileStorageRequest(File theFile);

  public File requestDirectoryStorage(String dirName, long theSize);

  public void clearDirectoryStorageRequest(File theFile);

  public File[] getVideoStoreDirectories();

  public String getRuleForDirectory(File testDir);

  public long getRuleSizeForDirectory(File testDir);

  public void removeVideoDirectory(File testDir);

  public void changeVideoDirectory(File oldDir, File testDir, String rule, long size);

  public void addVideoDirectory(String testDir, String rule, long size);

  public boolean isPathInManagedStorage(File f);

  public Set<String> getFailedNetworkMounts();

  public Comparator<DBObject> getMediaFileComparator(boolean fast);

  public void disableLibraryScanning();

  public void addIgnoreFile(File ignoreMe);

  public void removeIgnoreFile(File ignoreMe);

  public long getTotalVideoDuration();
}
