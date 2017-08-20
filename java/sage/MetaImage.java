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

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;
import java.util.WeakHashMap;

/*
 * NOTE: DON'T DO THUMBNAIL GENERATION IN HERE. WE WANT TO STORE THEM IN SEPARATE FILES
 * SO WE DON'T HAVE TO RESCALE ALL OF THEM EACH TIME THE PICTURE LIBRARY LOADS.
 */
public class MetaImage
{
  private static final Map<Object, MetaImage> globalImageCache = Collections.synchronizedMap(new HashMap<Object, MetaImage>());
  private static final Map<Image, MetaImage> globalWeakImageCache = Collections.synchronizedMap(new WeakHashMap<Image, MetaImage>());
  private static long javaImageCacheSize;
  private static long rawImageCacheSize;
  private static final Map<NativeImageAllocator, Long> nativeImageCacheSizeMap = Collections.synchronizedMap(new WeakHashMap<NativeImageAllocator, Long>());
  // The CacheLocks are for when you're requesting new space in a cache and also for during the load operation
  // that'll end up writing to the cache. We have to do it around both because the loading is abstracted out into the
  // native image allocator in some cases. The CacheLock ensures that only one thread will be adjusting the cache size
  // at any given time.
  // SYNCHRONIZATION RULES:
  // NEVER sync on a MetaImage and then sync on a cache lock; but the reverse is safe
  // ALWAYS sync on a MetaImage if modifying the array fields or the ref counts or image refs
  // NEVER get the RawCacheLock if you already have the JavaCacheLock; and never get a NativeCacheLock if you have a Java or Raw cache lock
  // UPDATE - 12/11/09 - The locking system is too restrictive and needs to be changed to allow loading of multiple images
  // simultaneously into the same cache. We will keep using the CacheLocks for synchronizing changes to the size of an image cache. We're adding
  // LoadingLocks as well; for the Java/Raw cache they share one of these per-MetaImage and its the loadLock field. For NIA loads the NativeImageData
  // object for that NIA will be used as the sync lock there.
  // NEW RULES:
  // NEVER sync on a MetaImage and then sync on a cache lock; but the reverse is safe
  // ALWAYS sync on a MetaImage if modifying the array fields or the ref counts or image refs
  // NEVER get the RawCacheLock if you already have the JavaCacheLock; and never get a NativeCacheLock if you have a Java or Raw cache lock
  // ALWAYS sync on a cache lock if checking the free space or adjusting the free space in a cache
  private static final Object globalJavaCacheLock = new Object();
  private static final Object globalRawCacheLock = new Object();
  // Use weak keys for these maps so we don't hold onto the NIA references
  private static final Map<NativeImageAllocator, Object> niaCacheLocks = Collections.synchronizedMap(new WeakHashMap<NativeImageAllocator, Object>());
  private static final boolean DEBUG_MI = false;
  private static final boolean ASYNC_LOAD_URL_IMAGES = Sage.getBoolean("async_load_url_images", true);
  private static Map<UIManager, AsyncLoader> uimgrToAsyncLoaderMap = new WeakHashMap<UIManager, AsyncLoader>();
  private static Set<Thread> nclPendingThreads = Collections.synchronizedSet(new HashSet<Thread>());
  private static LocalCacheFileTracker localCacheFileTracker;
  private static final int NUM_ASYNC_LOADER_THREADS = Sage.getInt("async_loader_num_threads", 3);
  private static final int URL_IMAGE_LOAD_DELAY = 150;//Sage.getInt("url_image_load_delay", 750);
  private static final boolean ENABLE_JPEG_AUTOROTATE = Sage.getBoolean("autorotate_jpeg_images", true);
  private static final int MAX_CACHED_URL_IMAGES = Sage.getInt("num_cached_url_images", 1000);
  private static final boolean ENABLE_OFFLINE_URL_CACHE = Sage.get("ui/thumbnail_folder", "GeneratedThumbnails").length() > 0;
  private static final boolean DELETE_TRACKED_LOCAL_CACHE_FILE_ON_IMAGE_LOAD = false;
  private static final long URL_RETRY_TIMEOUT_MILLIS =
      Sage.getLong("images_retry_timeout_mins", 5) * Sage.MILLIS_PER_MIN;
  private static final String TEMP_CACHE_IMAGE_SUFFIX = ".img";
  private static final String TEMP_CACHE_IMAGE_PREFIX = "stv";
  static
  {
    MetaImage mi = new MetaImage(null);
    mi.initDataStructures(1);
    mi.permanent = true;
    mi.sourceHasAlpha = true;
    mi.setJavaImage(ImageUtils.getNullImage(), 0, 0);
    mi.setRawImage(sage.media.image.ImageLoader.getNullImage(), 0, 0);
    globalImageCache.put(null, mi);
    cleanupURLImageCache();
  }

  /**
   * Class implementing a limited size cache tracker for local images
   *
   * Local images can be added, and removed by MetaImages
   * when the number of cached images becomes larger than the specified
   * max size, the oldest untouched cache item will be deleted by calling
   *   mi.removeLocalCachedFile().
   * <p>
   * MetaImage is responsible for creating/removing entries in this tracker.
   * This tracker is responsible for creating/deleting files.
   * <P>
   *
   */
  private static class LocalCacheFileTracker {
    private static final boolean DEBUG_MI_CACHE=false;
    private static final int MIN_NUM_FILES = 25;
    private final int maxNumItems;
    private final long maxTotalFileSize;
    private long totalFileSize;

    private static class CachedFileEntry{
      File file;
      long fileSize;
    }
    /**
     * cache is implemented by the wonderful LinkedHashMap class which
     * has age-ordering built-in.
     */
    private final Map<MetaImage, CachedFileEntry> miToCachedFile;

    /**
     * constructor starts cache cleaner thread
     * @param maxNumItems - the max number of cached items to allow
     */
    public LocalCacheFileTracker(int maxNumItems, long maxTotalFileSize) {
      this.maxNumItems=maxNumItems;
      this.maxTotalFileSize=maxTotalFileSize;

      miToCachedFile = Collections.synchronizedMap(new LinkedHashMap<MetaImage, CachedFileEntry>(
          (int) (maxNumItems / 0.75f + 1), 0.75F, true));
    }

    /**
     * get a local File to track, and start tracking it
     * <p>
     * if MetaImage already has a locally tracked file, return it.
     *
     * @param mi MetaImage requiring this locally cached image
     * @return a File to hold a locally cached image
     * @throws IOException when tmp file creation fails
     */
    public File getFile(MetaImage mi) throws IOException{
      CachedFileEntry entry;
      synchronized(this) {
        entry=miToCachedFile.get(mi);
      }
      if ( entry == null ) {
        File file = File.createTempFile(TEMP_CACHE_IMAGE_PREFIX, TEMP_CACHE_IMAGE_SUFFIX);
        entry=new CachedFileEntry();
        entry.file=file;
        entry.fileSize=0;

        // check for cache size overflow, but do not hold the lock
        // while removing cached files.
        while ( miToCachedFile.size() >= maxNumItems ) {
          MetaImage miToRemove=null;
          synchronized (this) {
            // recheck size in the lock
            // -- during load tests, without this lock, NoSuchElementException was thrown!
            if ( miToCachedFile.size() >= maxNumItems ){
              miToRemove=miToCachedFile.keySet().iterator().next();
            }
          }
          if ( miToRemove != null ){
            // tell MetaImage to remove the first (oldest) item, this will remove
            // it from the cache after MI has first done its cleanup
            miToRemove.deleteLocalCacheFile();
          }
        }

        synchronized (this) {
          miToCachedFile.put(mi, entry);
        }
        if (DEBUG_MI_CACHE) System.out.println("added tracked local cache file for MI "+mi+": "+entry.file+", image cache numItems="+miToCachedFile.size() + " total file size="+totalFileSize);
      } else {
        // file already exists, touch it to recheck the file size
        touch(mi);
      }
      return entry.file;
    }

    /**
     * Remove this MetaImage's cached file from the cache tracker and remove from disk
     *
     * @param mi MetaImage using tracked cache file
     * @return true if this MI had a file and it was successfully deleted from disk
     */
    public boolean removeFile(MetaImage mi){
      boolean rv = false;
      synchronized (this) {
        CachedFileEntry entry=miToCachedFile.get(mi);
        if ( entry != null ){
          miToCachedFile.remove(mi);
          totalFileSize-=entry.fileSize;
          rv=entry.file.delete();
          if (DEBUG_MI_CACHE) System.out.println("deleting tracked local cache file for MI "+mi+": "+entry.file+", image cache numItems="+miToCachedFile.size() + " total file size="+totalFileSize);
        }
        return rv;
      }
    }

    /*
     * Update the file size for this MI's cached file
     * <p>
     * Checks total file size of cache, and deletes oldest items if total file size
     * is greater than the maximum.
     */
    public void checkFileSize(MetaImage mi){
      LinkedList<MetaImage> cachedMisToRemove=null;
      synchronized (this) {
        CachedFileEntry entry=miToCachedFile.get(mi);
        if ( entry != null ){
          // check file exists
          if ( entry.file.exists()) {
            // check file length
            if ( entry.file.length() != entry.fileSize) {
              totalFileSize-=entry.fileSize;
              entry.fileSize=entry.file.length();
              totalFileSize+=entry.fileSize;
            }
            if ( totalFileSize > maxTotalFileSize ){
              // cache filesize overflow - add items to remove to reduce cache size
              // but only remove them outside the lock.
              cachedMisToRemove=new LinkedList<MetaImage>();
              Iterator<Entry<MetaImage, CachedFileEntry>> it = miToCachedFile.entrySet().iterator();
              long newTotalFileSize = totalFileSize;
              int newNumFiles=miToCachedFile.size();

              while ( newTotalFileSize > maxTotalFileSize
                  && newNumFiles > MIN_NUM_FILES
                  && it.hasNext() ){
                Entry<MetaImage, CachedFileEntry> entryToRemove = it.next();
                cachedMisToRemove.add(entryToRemove.getKey());
                newTotalFileSize-=entryToRemove.getValue().fileSize;
                newNumFiles--;
              }
              if (DEBUG_MI_CACHE) System.out.println("tracked local cache needs cleanup - deleting "+cachedMisToRemove.size());
            }
          } else {
            // file does not exist
            if ( entry.fileSize > 0 ){
              cachedMisToRemove=new LinkedList<MetaImage>();
              // file used to exist - trigger MI to remove it
              cachedMisToRemove.add(mi);
            }
          }
        }
      }
      // outside the lock - do we need to remove anything?
      if (cachedMisToRemove!= null ) {
        for (MetaImage miToRemove : cachedMisToRemove) {
          // tell MetaImage to remove the cached file, this will remove
          // it from the cache after MI cleanup
          miToRemove.deleteLocalCacheFile();
        }
      }
    }

    /**
     * Tell the cache that this MI has recently been accessed
     * @param mi
     */
    public void touch(MetaImage mi) {
      synchronized (this) {
        miToCachedFile.get(mi);
      }
    }
  }


  public static Object getNiaCacheLock(NativeImageAllocator nia)
  {
    synchronized (niaCacheLocks)
    {
      Object rv = niaCacheLocks.get(nia);
      if (rv != null)
        return rv;
      rv = new Object();
      niaCacheLocks.put(nia, rv);
      return rv;
    }
  }
  public static boolean isThreadLockedOnNCL(Thread testMe)
  {
    return nclPendingThreads.contains(testMe);
  }

  public static void clearHiResNativeCache(MiniClientSageRenderer mcsr)
  {
    synchronized (getNiaCacheLock(mcsr))
    {
      mcsr.releaseHiResNativeImages();
    }
  }

  private static void cleanupURLImageCache()
  {
    if (ENABLE_OFFLINE_URL_CACHE)
    {
      // Get the list of all cached URL images we have and make sure it's not more than our limit, and if it is
      // then we delete them starting from oldest first
      File[] allThumbs = MediaFile.THUMB_FOLDER.listFiles();
      int numURLImages = 0;
      for (int i = 0; allThumbs != null && i < allThumbs.length; i++)
        if (allThumbs[i].getName().startsWith("url-"))
          numURLImages++;
      if (numURLImages > MAX_CACHED_URL_IMAGES)
      {
        Arrays.sort(allThumbs, new Comparator<File>()
            {
          public int compare(File f1, File f2) {
            long l1 = f1.lastModified();
            long l2 = f2.lastModified();
            if (l1 < l2)
              return -1;
            else if (l1 > l2)
              return 1;
            else
              return 0;
          }
            });
        // Remove 25% of the cache to make room for new ones so we don't have to redo this every time we startup once
        // we've maxed out the cache
        for (int i = 0; i < allThumbs.length && numURLImages > MAX_CACHED_URL_IMAGES*3/4; i++)
        {
          if (allThumbs[i].getName().startsWith("url-"))
          {
            numURLImages--;
            allThumbs[i].delete();
          }
        }
      }
    }
    // Also cleanup all the imagery we put in the tmp filesystem last time we ran
    // (can happen on embedded or on PC)
    File[] tempFiles = new File(System.getProperty("java.io.tmpdir")).listFiles();
    for (File f : tempFiles)
    {
      if (f.getName().startsWith(TEMP_CACHE_IMAGE_PREFIX) && f.getName().endsWith(TEMP_CACHE_IMAGE_SUFFIX))
      {
        f.delete();
      }
    }
  }

  private static AsyncLoader getAsyncLoader(UIManager uiMgr) {
    AsyncLoader asload = uimgrToAsyncLoaderMap.get(uiMgr);
    if (asload == null)
    {
      synchronized (uimgrToAsyncLoaderMap)
      {
        asload = uimgrToAsyncLoaderMap.get(uiMgr);
        if (asload == null)
        {
          asload = new AsyncLoader();
          uimgrToAsyncLoaderMap.put(uiMgr, asload);
        }
      }
    }
    return asload;
  }

  public static long getJavaImageCacheSize() { return javaImageCacheSize; }
  public static long getNativeImageCacheSize(NativeImageAllocator nia)
  {
    Long nativeSize = nativeImageCacheSizeMap.get(nia);
    return nativeSize == null ? 0 : nativeSize.longValue();
  }
  static void clearNativeCache(NativeImageAllocator nia)
  {
    if (Sage.DBG) System.out.println("MetaImage clearNativeCache nativeImageCacheSize=" + getNativeImageCacheSize(nia));
    // go through and release all of the native image pointers
    while (true)
    {
      try
      {
        Iterator<MetaImage> walker = globalImageCache.values().iterator();
        while (walker.hasNext())
        {
          MetaImage mi = walker.next();
          mi.releaseNativeImages(nia);
        }
        walker = globalWeakImageCache.values().iterator();
        while (walker.hasNext())
        {
          MetaImage mi = walker.next();
          mi.releaseNativeImages(nia);
        }
        break;
      }
      catch (ConcurrentModificationException cme)
      {
        // This can happen because we're not synchronized on the global maps above. But we don't
        // want to sync on them because of deadlock potential. This is the safest thing to do where
        // we just start over again in the case where something else modified the maps while
        // we were trying to free our images.
      }
    }
    nativeImageCacheSizeMap.remove(nia);
    Sage.gc(true);
  }
  public static MetaImage getMetaImage(MetaFont src)
  {
    // These are only ever accessed right before rendering, so we should load them right now as well. But
    // they can still be cleared from the cache later.
    if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
    if (DEBUG_MI) System.out.println("first getMetaImage src=" + src);
    MetaImage rv = new MetaImage(src);
    rv.initFontImage();
    rv.sourceHasAlpha = true;
    synchronized (globalImageCache)
    {
      if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
      globalImageCache.put(src, rv);
    }
    if (DEBUG_MI) System.out.println("DONE first getMetaImage src=" + src);
    return rv;
  }
  // This is absolute file paths only
  public static MetaImage getMetaImage(File src)
  {
    if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
    if (src != null && !src.isFile())
      return globalImageCache.get(null);
    // Check for a MediaFile...disable this because otherwise we can end up using the high-res display
    // surface for showing thumbnails if they happen to be external files and are also imported as media files.
    /*MediaFile mf = Wizard.getInstance().getFileForFilePath(src);
		if (mf != null)
			return getMetaImage(mf);*/
    if (DEBUG_MI) System.out.println("first getMetaImage src=" + src);
    MetaImage rv = new MetaImage(src);
    rv.initDataStructures(1);
    // In this case we want to get the image attributes from the file without actually loading
    // the image yet.
    try
    {
      sage.media.image.RawImage ri = sage.media.image.ImageLoader.loadImageDimensionsFromFile(src.getAbsolutePath());
      if (ri != null)
      {
        rv.width[0] = ri.getWidth();
        rv.height[0] = ri.getHeight();
        rv.sourceHasAlpha = ri.hasAlpha();
      }
      else
        throw new IOException("Failed loading image dimensions from:" + src);
    }
    catch (IOException e)
    {
      if (Sage.DBG) System.out.println("Error accessing image file " + src + " of " + e);
      return globalImageCache.get(null);
    }

    synchronized (globalImageCache)
    {
      if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
      globalImageCache.put(src, rv);
    }
    if (DEBUG_MI) System.out.println("DONE first getMetaImage src=" + src);
    return rv;
  }
  public static MetaImage getMetaImage(MediaFileThumbnail src)
  {
    if (src != null && !src.mf.hasSpecificThumbnail())
      src = null;
    if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
    if (DEBUG_MI) System.out.println("first getMetaImage src=" + src);
    MetaImage rv = new MetaImage(src);
    rv.initDataStructures(1);
    // In this case we want to get the image attributes from the file without actually loading
    // the image yet.
    if ((src.mf.isLocalFile() || !Sage.client) && !src.mf.isThumbnailEmbedded())
    {
      try
      {
        sage.media.image.RawImage ri = sage.media.image.ImageLoader.loadImageDimensionsFromFile(src.mf.getSpecificThumbnailFile().getAbsolutePath());
        if (ri != null)
        {
          rv.width[0] = ri.getWidth();
          rv.height[0] = ri.getHeight();
          rv.sourceHasAlpha = ri.hasAlpha();
        }
        else
          throw new IOException("Failed loading image dimensions from:" + src);
      }
      catch (IOException e)
      {
        if (Sage.DBG) System.out.println("Error accessing image file " + src + " of " + e);
        return globalImageCache.get(null);
      }
    }
    else
    {
      try
      {
        sage.media.image.RawImage ri = sage.media.image.ImageLoader.loadImageDimensionsFromMemory(src.mf.loadEmbeddedThumbnailData());
        if (ri != null)
        {
          rv.width[0] = ri.getWidth();
          rv.height[0] = ri.getHeight();
          rv.sourceHasAlpha = ri.hasAlpha();
        }
        else
          throw new IOException("Failed loading image dimensions from:" + src);
      }
      catch (IOException e)
      {
        if (Sage.DBG) System.out.println("Error accessing image file " + src + " of " + e);
        return globalImageCache.get(null);
      }
    }

    // See if it needs to be autorotated
    Airing air = src.mf.getContentAiring();
    if (air != null && air.getOrientation() > 1 && ENABLE_JPEG_AUTOROTATE)
    {
      if (air.getOrientation() == 6)
        rv.autoRotated = 90;
      else if (air.getOrientation() == 8)
        rv.autoRotated = -90;
      if (rv.autoRotated != 0)
      {
        int tmp = rv.width[0];
        rv.width[0] = rv.height[0];
        rv.height[0] = tmp;
      }
    }

    synchronized (globalImageCache)
    {
      if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
      globalImageCache.put(src, rv);
    }
    if (DEBUG_MI) System.out.println("DONE first getMetaImage src=" + src);
    return rv;
  }
  public static MetaImage getMetaImage(MediaFile src)
  {
    if (src != null && !src.isPicture())
      src = null;
    if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
    if (DEBUG_MI) System.out.println("first getMetaImage src=" + src);
    MetaImage rv = new MetaImage(src);
    rv.initDataStructures(1);
    // In this case we want to get the image attributes from the file without actually loading
    // the image yet.
    File localSrcFile = src.getFile(0);
    if (src.isLocalFile() || !Sage.client/*isTrueClient()*/) // optimize for pseudo-clients
    {
      try
      {
        sage.media.image.RawImage ri = sage.media.image.ImageLoader.loadImageDimensionsFromFile(localSrcFile.getAbsolutePath());
        if (ri != null)
        {
          rv.width[0] = ri.getWidth();
          rv.height[0] = ri.getHeight();
          rv.sourceHasAlpha = ri.hasAlpha();
        }
        else
          throw new IOException("Failed loading image dimensions from:" + src);
      }
      catch (IOException e)
      {
        if (Sage.DBG) System.out.println("Error accessing image file " + src + " of " + e);
        return globalImageCache.get(null);
      }
    }
    else
    {
      try
      {
        sage.media.image.RawImage ri = sage.media.image.ImageLoader.loadImageDimensionsFromMemory(src.copyToLocalMemory(localSrcFile, 0, 0, null));
        if (ri != null)
        {
          rv.width[0] = ri.getWidth();
          rv.height[0] = ri.getHeight();
          rv.sourceHasAlpha = ri.hasAlpha();
        }
        else
          throw new IOException("Failed loading image dimensions from:" + src);
      }
      catch (IOException e)
      {
        if (Sage.DBG) System.out.println("Error accessing image file " + src + " of " + e);
        return globalImageCache.get(null);
      }
    }

    // See if it needs to be autorotated
    Airing air = src.getContentAiring();
    if (air != null && air.getOrientation() > 1 && ENABLE_JPEG_AUTOROTATE)
    {
      if (air.getOrientation() == 6)
        rv.autoRotated = 90;
      else if (air.getOrientation() == 8)
        rv.autoRotated = -90;
      if (rv.autoRotated != 0)
      {
        int tmp = rv.width[0];
        rv.width[0] = rv.height[0];
        rv.height[0] = tmp;
      }
    }

    synchronized (globalImageCache)
    {
      if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
      globalImageCache.put(src, rv);
    }
    if (DEBUG_MI) System.out.println("DONE first getMetaImage src=" + src);
    return rv;
  }
  public static MetaImage getMetaImage(String src, File logoCheckDir)
  {
    if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
    File logoFile = new File(logoCheckDir, src + ".gif");
    if (!logoFile.isFile())
    {
      if (!(logoFile = new File(logoCheckDir, src + ".jpg")).isFile())
      {
        if (!(logoFile = new File(logoCheckDir, src + ".png")).isFile())
        {
          String srclc = src.toLowerCase();
          if (Sage.WINDOWS_OS || !(logoFile = new File(logoCheckDir, srclc + ".gif")).isFile())
          {
            if (Sage.WINDOWS_OS || !(logoFile = new File(logoCheckDir, srclc + ".jpg")).isFile())
            {
              if (Sage.WINDOWS_OS || !(logoFile = new File(logoCheckDir, srclc + ".png")).isFile())
              {
                // Try the cleaned file name
                String cleanName = MediaFile.createValidFilename(src);
                if (!cleanName.equals(src))
                {
                  MetaImage rv = getMetaImage(cleanName, logoCheckDir);
                  synchronized (globalImageCache)
                  {
                    if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
                    globalImageCache.put(src, rv);
                  }
                  return rv;
                }
                globalImageCache.put(src, globalImageCache.get(null));
                return globalImageCache.get(null);
              }
            }
          }
        }
      }
    }
    MetaImage fileLogoImage = getMetaImage(logoFile);
    synchronized (globalImageCache)
    {
      if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
      globalImageCache.put(src, fileLogoImage);
    }
    return fileLogoImage;
  }
  public static MetaImage getMetaImage(String src)
  {
    return getMetaImage(src, null, null);
  }
  public static MetaImage getMetaImage(String src, ResourceLoadListener loadNotifier)
  {
    return getMetaImage(src, (loadNotifier == null) ? (UIManager)null : loadNotifier.getUIMgr(), loadNotifier);
  }
  public static MetaImage getMetaImage(String src, UIManager uiMgr, ResourceLoadListener loadNotifier)
  {
    MetaImage rv = null;
    if (src != null && src.length() == 0)
      src = null;
    String orgSrc = src;
    if (globalImageCache.containsKey(src))
    {
      rv = globalImageCache.get(src);
      if (rv.destroyed)
        globalImageCache.remove(src);
      else
      {
        if (isURLString(src))
        {
          // Check to make sure we've got this locally cached already; otherwise
          // return a waiter while we download the image
          if (src == null || loadNotifier == null || !loadNotifier.needsLoadCallback(src) || !ASYNC_LOAD_URL_IMAGES || src.startsWith("smb:") ||
              (rv.localCacheFile != null && rv.localCacheFile.isFile()) || rv.loadFailed ||
              rv.mightLoadFast(loadNotifier.getUIMgr()))
            // found a valid cached MI
            if (!rv.loadFailed || rv.lastUrlLoadTime + URL_RETRY_TIMEOUT_MILLIS > Sage.eventTime()) {
              // Either image succeeded, or image failed last time, and we are not yet ready to
              // retry, so return existing MI
              if ( localCacheFileTracker!=null && rv.localCacheFile != null)
                // touch local cache file exists.
                localCacheFileTracker.touch(rv);
              return rv;
            } else {
              // image failed loading last time, but retry timeout has expired
              // retry image loading - clear this cached MI and create a new one below
              clearFromCache(src);
            }
        }
        else
          return rv;
      }
    }
    if (uiMgr != null)
    {
      rv = uiMgr.getUICachedMetaImage(src);
      if (rv != null && !rv.destroyed) return rv;
    }

    // URL handling
    if (isURLString(src))
    {
      if (loadNotifier != null && loadNotifier.needsLoadCallback(src) && ASYNC_LOAD_URL_IMAGES && !src.startsWith("smb:"))
      {
        getAsyncLoader(uiMgr).loadImage(src, loadNotifier);
        return new Waiter(getMetaImage((String)null), src);
      }

      if (DEBUG_MI) {
        System.out.println("Sync-loading in getMetaImage src=" + src + " because loadNotifyer="
            + loadNotifier + (loadNotifier == null ? ""
                : " needsLoadCallback=" + Boolean.valueOf(
                    loadNotifier.needsLoadCallback(src)) + (loadNotifier instanceof ZPseudoComp ?
                        " Widget=" + ((ZPseudoComp) loadNotifier).widg.symbol() + "="
                        + ((ZPseudoComp) loadNotifier).widg.toString()
                        : "")));
      }

      rv = new MetaImage(getURLForString(src));
      rv.initDataStructures(1);
      // We need to load this image so we can get its sizing information
      //		rv.setJavaImage(ImageUtils.fullyLoadImage(src), 0);
      //		rv.getRawImage(0);
      //		rv.removeRawRef(0);
      if (!rv.loadCacheFile())
      {
        rv.setRawImage(sage.media.image.ImageLoader.getNullImage(), 0, 0);
        rv.sourceHasAlpha = true;
      }
      else
      {
        try
        {
          sage.media.image.RawImage ri = sage.media.image.ImageLoader.loadImageDimensionsFromFile(rv.localCacheFile.getAbsolutePath());
          if (ri != null)
          {
            rv.width[0] = ri.getWidth();
            rv.height[0] = ri.getHeight();
            rv.sourceHasAlpha = ri.hasAlpha();
          }
          else
            throw new IOException("Failed loading image dimensions from:" + src);
        }
        catch (IOException e)
        {
          if (Sage.DBG) System.out.println("Error accessing image file " + src + " of " + e);
          return globalImageCache.get(null);
        }
      }
    }
    else
    {
      // This allows for file paths from either OS to work right on our current OS
      src = IOUtils.convertPlatformPathChars(src);
      // check for the full path, or relative to the working path
      File widgFile = null;
      if (uiMgr != null && uiMgr.getModuleGroup() != null &&
          uiMgr.getModuleGroup().defaultModule != null)
        widgFile = new File(uiMgr.getModuleGroup().defaultModule.description());
      if (widgFile != null)
        widgFile = widgFile.getParentFile();
      File[] searchRoots = null;
      if (uiMgr != null)
        searchRoots = uiMgr.getImageSearchRoots();
      MetaImage strMetaImage = null;
      File testFile = null;
      boolean checkMore = true;
      if ((testFile = new File(src)).isFile())
      {
        strMetaImage = getMetaImage(testFile);
        checkMore = false;
      }
      // check for a path relative to the location of the STV file or any STVIs
      else if (searchRoots != null)
      {
        for (int i = 0; i < searchRoots.length; i++)
        {
          if ((testFile = new File(searchRoots[i], src)).isFile())
          {
            strMetaImage = getMetaImage(testFile);
            checkMore = false;
            break;
          }
        }
      }
      if (checkMore)
      {
        // check for an image with the same name as the folder where the STV file is
        if (widgFile != null && (testFile = new File(widgFile, new File(src).getName())).isFile())
          strMetaImage = getMetaImage(testFile);
        // check the working directory for a file with the same name as this image
        else if ((testFile = new File(new File(src).getName())).isFile())
          strMetaImage = getMetaImage(testFile);
        // check in the images subfolder of the STV for a file with the same name
        else if (widgFile != null && (testFile = new File(new File(widgFile, "images"),
            new File(src).getName())).isFile())
          strMetaImage = getMetaImage(testFile);
        // Check in the SageTV7 STV folder as well
        else if ((testFile = new File("STVs" + File.separator + "SageTV7", src)).isFile())
          strMetaImage = getMetaImage(testFile);
      }

      if (strMetaImage != null && !isNull(strMetaImage))
      {
        // Also store the string version for fast access later
        // NOTE: We can't do this because with relative paths and multiple STVs this could be ambiguous now
        if (widgFile == null || !testFile.getAbsolutePath().startsWith(widgFile.toString()))
          globalImageCache.put(orgSrc, strMetaImage);
        else if (uiMgr != null)
          uiMgr.saveUICachedMetaImage(orgSrc, strMetaImage);
      }
      if (strMetaImage != null)
        return strMetaImage;

      if (DEBUG_MI) System.out.println("first getMetaImage src=" + orgSrc);
      rv = new MetaImage(orgSrc);
      rv.initDataStructures(1);
      // If its not a file, then its a resource and we need to fully load it to get the information on it
      //		BufferedImage newBI = ImageUtils.fullyLoadImage(orgSrc);
      // If it doesn't resolve, don't cache it because it may resolve with a different STV path later
      //		if (newBI == ImageUtils.getNullImage())
      //			return (MetaImage) globalImageCache.get(null);
      //		rv.setJavaImage(newBI, 0);

      if (!rv.loadCacheFile())
      {
        rv.setRawImage(sage.media.image.ImageLoader.getNullImage(), 0, 0);
        rv.sourceHasAlpha = true;
      }
      else
      {
        try
        {
          sage.media.image.RawImage ri = sage.media.image.ImageLoader.loadImageDimensionsFromFile(rv.localCacheFile.getAbsolutePath());
          if (ri != null)
          {
            rv.width[0] = ri.getWidth();
            rv.height[0] = ri.getHeight();
            rv.sourceHasAlpha = ri.hasAlpha();
          }
          else
            throw new IOException("Failed loading image dimensions from:" + src);
        }
        catch (IOException e)
        {
          if (Sage.DBG) System.out.println("Error accessing image file " + src + " of " + e);
          return globalImageCache.get(null);
        }
      }
    }
    synchronized (globalImageCache)
    {
      if (globalImageCache.containsKey(orgSrc)) return globalImageCache.get(orgSrc);
      globalImageCache.put(orgSrc, rv);
    }
    if (DEBUG_MI) System.out.println("DONE first getMetaImage src=" + src);
    return rv;
  }

  private static boolean isURLString(String s)
  {
    return s != null && (s.startsWith("http:") || s.startsWith("https:") || s.startsWith("ftp:") || s.startsWith("smb:"));
  }

  private static URL getURLForString(String s)
  {
    if (s == null) return null;
    URLStreamHandler handler = null;
    if (s.startsWith("smb://"))
    {
      try
      {
        handler = (URLStreamHandler)Class.forName("jcifs.smb.Handler").newInstance();
      }
      catch (Exception e)
      {
        System.out.println("Error loading jcifs SMB URL handler:" + e);
      }
    }
    try
    {
      return new URL(null, s, handler);
    }
    catch (MalformedURLException e)
    {
      System.out.println("Error in formatting of URL String " + s + " of " + e);
    }
    return null;
  }

  // NOTE: We can't use URL in the Maps because it's hashcode is inconsistent.
  // This is because Java will resolve the hostname to an address which may vary if the host is using virtual hosting
  public static MetaImage getMetaImage(URL src)
  {
    return getMetaImage(src, null);
  }
  public static MetaImage getMetaImage(URL src, ResourceLoadListener loadNotifier)
  {
    String srcStr = src == null ? ((String)null) : src.toString();
    if (globalImageCache.containsKey(srcStr))
    {
      MetaImage rv = globalImageCache.get(srcStr);
      // Check to make sure we've got this locally cached already; otherwise
      // return a waiter while we download the image
      if (src == null || loadNotifier == null || !loadNotifier.needsLoadCallback(src) || !ASYNC_LOAD_URL_IMAGES || (!"http".equals(src.getProtocol()) && !"https".equals(src.getProtocol()) && !"ftp".equals(src.getProtocol())) ||
          (rv.localCacheFile != null && rv.localCacheFile.isFile()) || rv.loadFailed ||
          rv.mightLoadFast(loadNotifier.getUIMgr()))
        // found a valid cached MI
        if (!rv.loadFailed || rv.lastUrlLoadTime + URL_RETRY_TIMEOUT_MILLIS > Sage.eventTime()) {
          // Either image succeeded, or image failed last time, and we are not yet ready to
          // retry, so return existing MI
          if ( localCacheFileTracker!=null && rv.localCacheFile != null)
            localCacheFileTracker.touch(rv);
          return rv;
        } else {
          // image failed loading last time, but retry timeout has expired
          // retry image loading - clear this cached MI and create a new one below
          clearFromCache(src);
        }
    }

    if (loadNotifier != null && loadNotifier.needsLoadCallback(src) && ASYNC_LOAD_URL_IMAGES && ("http".equals(src.getProtocol()) || "https".equals(src.getProtocol()) || "ftp".equals(src.getProtocol())))
    {
      getAsyncLoader(loadNotifier.getUIMgr()).loadImage(srcStr, loadNotifier);
      return new Waiter(getMetaImage((String)null), src);
    }

    if (DEBUG_MI) System.out.println("first getMetaImage src=" + src);
    MetaImage rv = new MetaImage(src);
    rv.initDataStructures(1);
    // We need to load this image so we can get its sizing information
    //		rv.setJavaImage(ImageUtils.fullyLoadImage(src), 0);
    //		rv.getRawImage(0);
    //		rv.removeRawRef(0);
    if (!rv.loadCacheFile())
    {
      rv.setRawImage(sage.media.image.ImageLoader.getNullImage(), 0, 0);
      rv.sourceHasAlpha = true;
    }
    else
    {
      try
      {
        sage.media.image.RawImage ri = sage.media.image.ImageLoader.loadImageDimensionsFromFile(rv.localCacheFile.getAbsolutePath());
        if (ri != null)
        {
          rv.width[0] = ri.getWidth();
          rv.height[0] = ri.getHeight();
          rv.sourceHasAlpha = ri.hasAlpha();
        }
        else
          throw new IOException("Failed loading image dimensions from:" + src);
      }
      catch (IOException e)
      {
        if (Sage.DBG) System.out.println("Error accessing image file " + src + " of " + e);
        return globalImageCache.get(null);
      }
    }
    synchronized (globalImageCache)
    {
      if (globalImageCache.containsKey(srcStr)) return globalImageCache.get(srcStr);
      globalImageCache.put(srcStr, rv);
    }
    if (DEBUG_MI) System.out.println("DONE first getMetaImage src=" + src);
    return rv;
  }
  public static MetaImage getMetaImage(Album src)
  {
    if (globalImageCache.containsKey(src)) return globalImageCache.get(src);

    if (DEBUG_MI) System.out.println("first getMetaImage src=" + src);
    MetaImage rv = new MetaImage(src);
    if (!rv.initAlbumImage())
      return globalImageCache.get(null);
    synchronized (globalImageCache)
    {
      if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
      globalImageCache.put(src, rv);
    }
    if (DEBUG_MI) System.out.println("DONE first getMetaImage src=" + src);
    return rv;
  }
  public static MetaImage getMetaImageNoLoad(Object src)
  {
    return getMetaImageNoLoad(src, null);
  }
  public static MetaImage getMetaImageNoLoad(Object src, ResourceLoadListener loadListener)
  {
    if (src instanceof URL)
      src = src.toString();
    if (src instanceof String && ((String)src).length() == 0)
      src = null;
    MetaImage rv = globalImageCache.get(src);
    // Since we disabled the File->MF conversion above; we should also do so here
    /*		if (rv == null && src instanceof File)
		{
			MediaFile mf = Wizard.getInstance().getFileForFilePath((File)src);
			if (mf != null)
			{
				rv = (MetaImage) globalImageCache.get(mf);
			}
		}*/
    if (rv != null || loadListener == null)
      return rv;
    UIManager uiMgr = loadListener.getUIMgr();
    if (uiMgr != null && src instanceof String)
      return uiMgr.getUICachedMetaImage((String) src);
    else
      return null;
  }
  // We use weak references to the source object on Java images because we don't want to keep
  // those around unless necessary
  public static MetaImage getMetaImage(Image src)
  {
    if (src == null) return globalImageCache.get(null);
    if (globalWeakImageCache.containsKey(src)) return globalWeakImageCache.get(src);

    if (DEBUG_MI) System.out.println("first getMetaImage src=" + src);
    Object srcObj = new WeakReference<Object>(src);
    MetaImage rv = new MetaImage(srcObj);
    rv.initDataStructures(1);
    // We need to load this image so we can get its sizing information
    // The src object will maintain the reference to the source of truth
    rv.setJavaImage(src, 0, 0);
    synchronized (globalWeakImageCache)
    {
      if (globalWeakImageCache.containsKey(src)) return globalWeakImageCache.get(src);
      globalWeakImageCache.put(src, rv);
    }
    if (DEBUG_MI) System.out.println("DONE first getMetaImage src=" + src);
    return rv;
  }

  public static MetaImage getMetaImage(MetaImage srcImage, MetaImage diffuseImage, Rectangle fullSrcRect, boolean flipX, boolean flipY, int diffuseColor)
  {
    if (srcImage == null) return globalImageCache.get(null);
    if (diffuseImage == null && !flipX && !flipY && diffuseColor == 0xFFFFFF) return srcImage;
    @SuppressWarnings("unchecked")
    Vector<Object> src = Pooler.getPooledVector();
    src.add(srcImage);
    src.add(diffuseImage);
    src.add(fullSrcRect);
    src.add(flipX ? Boolean.TRUE : Boolean.FALSE);
    src.add(flipY ? Boolean.TRUE : Boolean.FALSE);
    src.add(new Integer(diffuseColor));
    MetaImage rv = getMetaImage(src);
    Pooler.returnPooledVector(src);
    return rv;
  }
  public static MetaImage getMetaImage(Vector<Object> src)
  {
    if (src == null) return globalImageCache.get(null);
    if (globalImageCache.containsKey(src))
    {
      MetaImage rv = globalImageCache.get(src);
      return rv;
    }
    {
      // use tmpSrc to avoid needing warning suppression of
      // unchecked conversion at function scope
      @SuppressWarnings("unchecked")
      Vector<Object>  tmpSrc = (Vector<Object>) src.clone();
      src = tmpSrc;
    }
    if (DEBUG_MI) System.out.println("first getMetaImage src=" + src);
    MetaImage rv = new MetaImage(src);
    MetaImage srcImage = (MetaImage) src.get(0);
    rv.initDataStructures(1);
    rv.width[0] = srcImage.getWidth(0);
    rv.height[0] = srcImage.getHeight(0);
    synchronized (globalImageCache)
    {
      if (globalImageCache.containsKey(src)) return globalImageCache.get(src);
      globalImageCache.put(src, rv);
    }
    if (DEBUG_MI) System.out.println("DONE first getMetaImage src=" + src);
    return rv;
  }

  public static boolean isNull(MetaImage img)
  {
    return img == globalImageCache.get(null);
  }

  public static boolean clearFromCache(Object removedFile)
  {
    if (removedFile instanceof URL)
      removedFile = removedFile.toString();
    MetaImage cachedImage = globalImageCache.get(removedFile);
    if (cachedImage != null && !cachedImage.permanent)
    {
      if (cachedImage.localCacheFile != null)
        cachedImage.deleteLocalCacheFile();
      // Narflex: We can't synchronize here because then we're going MetaImage->CacheLock which is against the sync rules!
      //synchronized (cachedImage)
      {
        cachedImage.finalize();
      }
      globalImageCache.remove(removedFile);
      return true;
    }
    else return false;
  }

  protected MetaImage(Object inSrc)
  {
    src = inSrc;
  }
  private void initDataStructures(int num)
  {
    width = new int[num];
    height = new int[num];
    imageOption = new Object[num];
    javaImage = new Image[num];
    lastUsedJava = new long[num];
    javaMemSize = new int[num];
    javaRefCount = new int[num];
    rawImage = new sage.media.image.RawImage[num];
    lastUsedRaw = new long[num];
    rawMemSize = new int[num];
    rawRefCount = new int[num];
    if (nativeAllocData != null)
    {
      for (int i = 0; i < nativeAllocData.length; i++)
      {
        if (nativeAllocData[i] != null && nativeAllocData[i].nia.get() != null)
          nativeAllocData[i].initDataStructures(num);
        else
          nativeAllocData[i] = null;
      }
    }
    numImages = num;
  }
  private void incrementDataStructures(int amount)
  {
    int newNumImages = numImages + amount;
    int[] newwidth = new int[newNumImages];
    System.arraycopy(width, 0, newwidth, 0, width.length);
    width = newwidth;
    int[] newheight = new int[newNumImages];
    System.arraycopy(height, 0, newheight, 0, height.length);
    height = newheight;
    Object[] newimageOption = new Object[newNumImages];
    System.arraycopy(imageOption, 0, newimageOption, 0, imageOption.length);
    imageOption = newimageOption;
    Image[] newjavaImage = new Image[newNumImages];
    System.arraycopy(javaImage, 0, newjavaImage, 0, javaImage.length);
    javaImage = newjavaImage;
    long[] newlastUsedJava = new long[newNumImages];
    System.arraycopy(lastUsedJava, 0, newlastUsedJava, 0, lastUsedJava.length);
    lastUsedJava = newlastUsedJava;
    int[] newjavaMemSize = new int[newNumImages];
    System.arraycopy(javaMemSize, 0, newjavaMemSize, 0, javaMemSize.length);
    javaMemSize = newjavaMemSize;
    int[] newjavaRefCount = new int[newNumImages];
    System.arraycopy(javaRefCount, 0, newjavaRefCount, 0, javaRefCount.length);
    javaRefCount = newjavaRefCount;
    sage.media.image.RawImage[] newrawImage = new sage.media.image.RawImage[newNumImages];
    System.arraycopy(rawImage, 0, newrawImage, 0, rawImage.length);
    rawImage = newrawImage;
    long[] newlastUsedRaw = new long[newNumImages];
    System.arraycopy(lastUsedRaw, 0, newlastUsedRaw, 0, lastUsedRaw.length);
    lastUsedRaw = newlastUsedRaw;
    int[] newrawMemSize = new int[newNumImages];
    System.arraycopy(rawMemSize, 0, newrawMemSize, 0, rawMemSize.length);
    rawMemSize = newrawMemSize;
    int[] newrawRefCount = new int[newNumImages];
    System.arraycopy(rawRefCount, 0, newrawRefCount, 0, rawRefCount.length);
    rawRefCount = newrawRefCount;
    if (nativeAllocData != null)
    {
      for (int i = 0; i < nativeAllocData.length; i++)
      {
        if (nativeAllocData[i] != null && nativeAllocData[i].nia.get() != null)
          nativeAllocData[i].incrementDataStructures(amount);
        else
          nativeAllocData[i] = null;
      }
    }
    numImages += amount;
  }
  private synchronized NativeImageData getNativeImageData(NativeImageAllocator nia)
  {
    int nullIdx = -1;
    if (nativeAllocData != null)
    {
      for (int i = 0; i < nativeAllocData.length; i++)
      {
        if (nativeAllocData[i] != null)
        {
          if (nativeAllocData[i].nia.get() == nia)
            return nativeAllocData[i];
          else if (nativeAllocData[i].nia.get() == null)
          {
            nativeAllocData[i] = null;
            nullIdx = i;
          }
        }
        else
        {
          nullIdx = i;
        }
      }
    }
    if (nullIdx == -1)
    {
      if (nativeAllocData == null)
      {
        nativeAllocData = new NativeImageData[1];
        nullIdx = 0;
      }
      else
      {
        NativeImageData[] tempData = new NativeImageData[nativeAllocData.length + 1];
        System.arraycopy(nativeAllocData, 0, tempData, 0, nativeAllocData.length);
        nativeAllocData = tempData;
        nullIdx = nativeAllocData.length - 1;
      }
    }
    return nativeAllocData[nullIdx] = new NativeImageData(nia);
  }
  private void ensureCacheCanHoldNewImage(int idx)
  {
    if (width[idx] != 0 && height[idx] != 0)
      maintainJavaCacheSize(width[idx]*height[idx]*4, this, idx);
  }
  private void ensureCacheCanHoldNewRawImage(int idx)
  {
    if (width[idx] != 0 && height[idx] != 0)
      maintainRawCacheSize(width[idx]*height[idx]*4, this, idx);
  }

  private void setJavaImage(Image img, int idx, long reservedMemSize)
  {
    if (img == null)
      img = ImageUtils.getNullImage();
    if (width[idx] != 0 && height[idx] != 0 && (img.getWidth(null) != width[idx] || img.getHeight(null) != height[idx]) ||
        imageOption[idx] != null)
    {
      // We need to scale the image
      img = ImageUtils.createBestScaledImage(img, width[idx], height[idx], imageOption[idx]);
    }
    synchronized (globalJavaCacheLock)
    {
      if (DEBUG_MI) System.out.println("MetaImage setJavaImage javaImageCacheSize=" + javaImageCacheSize + " img=" + img + " idx=" + idx + " " + this);
      if (width[idx] == 0 || height[idx] == 0)
      {
        maintainJavaCacheSize(img.getWidth(null)*img.getHeight(null)*4 - reservedMemSize, this, idx);
      }
      synchronized (this)
      {
        javaImage[idx] = img;
        width[idx] = img.getWidth(null);
        height[idx] = img.getHeight(null);
        lastUsedJava[idx] = Sage.eventTime();
      }
      //		synchronized (globalJavaCacheLock)
      {
        javaImageCacheSize += width[idx]*height[idx]*4 - javaMemSize[idx] - reservedMemSize;
        javaMemSize[idx] = width[idx]*height[idx]*4; // assume 32-bit pixels
      }
      if (img == ImageUtils.getNullImage())
      {
        loadFailed = true;
        //			synchronized (globalJavaCacheLock)
        {
          javaImageCacheSize -= javaMemSize[idx];
          javaMemSize[idx] = 0;
        }
      }
      else
      {
        loadFailed = false;
      }
      maintainJavaCacheSize(0, this, idx);
      globalJavaCacheLock.notifyAll();
    }
    Sage.gc();
    if (DEBUG_MI) System.out.println("MetaImage setJavaImage returning javaImageCacheSize=" + javaImageCacheSize + " img=" + img + " idx=" + idx + " " + this);
  }
  public void releaseJavaImage(int idx)
  {
    synchronized (globalJavaCacheLock)
    {
      synchronized (this)
      {
        if (DEBUG_MI) System.out.println("MetaImage releaseJavaImage javaImageCacheSize=" + javaImageCacheSize + " " + this);
        if (javaRefCount[idx] > 0 || (permanent && idx == 0))
        {
          if (DEBUG_MI) System.out.println("CANNOT release meta image, it still has refs");
          return;
        }
        if (javaImage[idx] != null)
        {
          if (!(src instanceof WeakReference) && javaImage[idx] != ImageUtils.getNullImage())
          {
            javaImage[idx].flush();
            Sage.gc();
          }
        }
        javaImage[idx] = null;
        javaImageCacheSize -= javaMemSize[idx];
        javaMemSize[idx] = 0;
      }
    }
    if (DEBUG_MI) System.out.println("MetaImage releaseJavaImage returning javaImageCacheSize=" + javaImageCacheSize + " " + this);
  }
  public void releaseJavaImages()
  {
    synchronized (globalJavaCacheLock)
    {
      synchronized (this)
      {
        if (DEBUG_MI) System.out.println("MetaImage releaseJavaImages javaImageCacheSize=" + javaImageCacheSize + " " + this);
        for (int i = 0; i < javaImage.length; i++)
        {
          if (javaRefCount[i] > 0 || (permanent && i == 0))
          {
            if (DEBUG_MI) System.out.println("ERROR: CANNOT release meta image, it still has refs");
            continue;
          }
          if (javaImage[i] != null && !(src instanceof WeakReference) &&
              javaImage[i] != ImageUtils.getNullImage())
          {
            javaImage[i].flush();
            Sage.gc();
          }
          javaImage[i] = null;
          javaImageCacheSize -= javaMemSize[i];
          javaMemSize[i] = 0;
        }
      }

    }
    if (DEBUG_MI) System.out.println("MetaImage releaseJavaImages returning javaImageCacheSize=" + javaImageCacheSize + " " + this);
  }
  private void initFontImage()
  {
    SageRenderer.CachedFontGlyphs glyphCache = SageRenderer.getAcceleratedFont((MetaFont)src);
    if (glyphCache.glyphCounts.length > numImages)
    {
      if (numImages == 0)
      {
        initDataStructures(glyphCache.glyphCounts.length);
      }
      else
        incrementDataStructures(glyphCache.glyphCounts.length - numImages);
      Arrays.fill(width, SageRenderer.FONT_TEXTURE_MAP_SIZE);
      Arrays.fill(height, SageRenderer.FONT_TEXTURE_MAP_SIZE);
    }
    //		for (int i = 0; i < numImages; i++)
    //			setJavaImage((Image) glyphCache.images.get(i), i);
    //		glyphCache.images = null;
    //		Sage.gc();
  }
  private boolean initAlbumImage()
  {
    MediaFile mf = findMediaFileForAlbum();
    if (mf != null)
    {
      if (numImages == 0)
      {
        initDataStructures(1);
        try
        {
          byte[] data = mf.loadEmbeddedThumbnailData();
          if (data == null)
            return false;
          sage.media.image.RawImage ri = sage.media.image.ImageLoader.loadImageDimensionsFromMemory(data);
          if (ri == null)
            return false;
          width[0] = ri.getWidth();
          height[0] = ri.getHeight();
          sourceHasAlpha = ri.hasAlpha();
        }
        catch (IOException e)
        {
          if (Sage.DBG) System.out.println("Error loading album image:" + this + " of " + e);
          return false;
        }
      }
      return true;
    }
    return false;
  }
  private MediaFile findMediaFileForAlbum()
  {
    Airing[] myAirs = ((Album)src).getAirings();
    for (int i = 0; i < myAirs.length; i++)
    {
      MediaFile mf = Wizard.getInstance().getFileForAiring(myAirs[i]);
      if (mf != null)
      {
        if (mf.hasThumbnail())
        {
          return mf;
        }
      }
    }
    return null;
  }
  public Image getJavaImage() { return getJavaImage(0); }
  public Image getJavaImage(int scaledWidth, int scaledHeight)
  {
    if (scaledWidth == 0 && scaledHeight == 0) return getJavaImage(0);
    int targetIndex = -1;
    synchronized (this)
    {
      for (int i = width.length - 1; i >= 0; i--)
      {
        if (width[i] == scaledWidth && height[i] == scaledHeight)
        {
          targetIndex = i;
          break;
        }
      }
      if (targetIndex == -1)
        targetIndex = addScaledImage(scaledWidth, scaledHeight, null);
    }
    return getJavaImage(targetIndex);
  }
  public Image getJavaImage(int imageIndex)
  {
    synchronized (this)
    {
      lastUsedJava[imageIndex] = Sage.eventTime();
      if (javaImage[imageIndex] != null && !loadFailed)
      {
        addJavaRef(imageIndex);
        return javaImage[imageIndex];
      }
    }
    long javaCacheReserve = 0;
    synchronized (globalJavaCacheLock)
    {
      // Check if there's already Java references held to this image; if there are it means another thread is in the process of loading it so
      // we should just wait on their completion and then use the result they get.
      while (javaRefCount[imageIndex] > 0 && javaImage[imageIndex] == null)
      {
        if (DEBUG_MI) System.out.println("Waiting on load of Java image from other thread to complete mi=" + this);
        try
        {
          globalJavaCacheLock.wait(5000);
        }
        catch (InterruptedException ie){}
      }
      synchronized (this)
      {
        // Add the ref now so it's definitely there after the image object is set
        addJavaRef(imageIndex);
        // Double check on whether or not it's loaded now that we have the global cache lock
        if (javaImage[imageIndex] != null && !loadFailed)
          return javaImage[imageIndex];
      }
      ensureCacheCanHoldNewImage(imageIndex);
      javaCacheReserve = reserveJavaCache(imageIndex);
    }
    try
    {
      if (src instanceof MetaFont)
      {
        //initFontImage();
        MetaFont fonty = (MetaFont) src;
        setJavaImage(fonty.loadJavaFontImage(SageRenderer.getAcceleratedFont(fonty), imageIndex), imageIndex, javaCacheReserve);
        javaCacheReserve = 0;
      }
      else if (src instanceof String)
      {
        if (imageIndex == 0)
          setJavaImage(ImageUtils.fullyLoadImage((String) src), imageIndex, javaCacheReserve);
        else
        {
          setJavaImage(getJavaImage(0), imageIndex, javaCacheReserve);
          removeJavaRef(0);
        }
        javaCacheReserve = 0;
      }
      else if (src instanceof URL)
      {
        if (imageIndex == 0)
        {
          if (localCacheFile != null && localCacheFile.isFile() && localCacheFile.length() > 0)
          {
            setJavaImage(ImageUtils.fullyLoadImage(localCacheFile), imageIndex, javaCacheReserve);
            if (localCacheFile.length() > 500000) {
              if (DEBUG_MI) System.out.println("GetJavaImage(): Deleting large local image cache file: " + localCacheFile);
              deleteLocalCacheFile();
            }
          } else {
            if ( DEBUG_MI ) System.out.println("Sync-loading in getJavaImage() from URL "+src+" because local cache file does not exist");
            setJavaImage(ImageUtils.fullyLoadImage((URL) src), imageIndex, javaCacheReserve);
          }
        }
        else
        {
          setJavaImage(getJavaImage(0), imageIndex, javaCacheReserve);
          removeJavaRef(0);
        }
        javaCacheReserve = 0;
      }
      else if (src instanceof File)
      {
        if (imageIndex == 0)
          setJavaImage(ImageUtils.fullyLoadImage((File) src), imageIndex, javaCacheReserve);
        else
        {
          setJavaImage(getJavaImage(0), imageIndex, javaCacheReserve);
          removeJavaRef(0);
        }
        javaCacheReserve = 0;
      }
      else if (src instanceof MediaFileThumbnail)
      {
        if (imageIndex == 0)
        {
          MediaFileThumbnail mft = (MediaFileThumbnail) src;
          File localSrcFile = mft.mf.getSpecificThumbnailFile();
          if ((mft.mf.isLocalFile() || !Sage.client) && !mft.mf.isThumbnailEmbedded())
            setJavaImage(ImageUtils.rotateImage(ImageUtils.fullyLoadImage(localSrcFile), autoRotated), imageIndex, javaCacheReserve);
          else
          {
            setJavaImage(ImageUtils.rotateImage(mft.mf.loadEmbeddedThumbnail(), autoRotated), imageIndex, javaCacheReserve);
          }
          if (javaMemSize[imageIndex] > 500000)
            Sage.gcPause();
        }
        else
        {
          setJavaImage(getJavaImage(0), imageIndex, javaCacheReserve);
          removeJavaRef(0);
        }
        javaCacheReserve = 0;
      }
      else if (src instanceof MediaFile)
      {
        if (imageIndex == 0)
        {
          MediaFile mf = (MediaFile) src;
          File localSrcFile = mf.getFile(0);
          if (mf.isLocalFile() || !Sage.client/*isTrueClient()*/) // optimize for pseudo-clients
            setJavaImage(ImageUtils.rotateImage(ImageUtils.fullyLoadImage(localSrcFile), autoRotated), imageIndex, javaCacheReserve);
          else
          {
            byte[] imageBytes = mf.copyToLocalMemory(localSrcFile, 0, 0, null);
            setJavaImage(ImageUtils.rotateImage(ImageUtils.fullyLoadImage(imageBytes, 0, imageBytes.length), autoRotated), imageIndex, javaCacheReserve);
          }
          if (javaMemSize[imageIndex] > 500000)
            Sage.gcPause();
        }
        else
        {
          setJavaImage(getJavaImage(0), imageIndex, javaCacheReserve);
          removeJavaRef(0);
        }
        javaCacheReserve = 0;
      }
      else if (src instanceof Album)
      {
        if (imageIndex == 0)
        {
          MediaFile mf = findMediaFileForAlbum();
          if (mf != null)
          {
            setJavaImage(mf.loadEmbeddedThumbnail(), imageIndex, javaCacheReserve);
          }
        }
        else
        {
          setJavaImage(getJavaImage(0), imageIndex, javaCacheReserve);
          removeJavaRef(0);
        }
        javaCacheReserve = 0;
      }
      else if (src instanceof WeakReference)
      {
        @SuppressWarnings("unchecked")
        Image jImage = (Image) ((WeakReference<Object>) src).get();
        if (jImage != null)
        {
          setJavaImage(jImage, imageIndex, javaCacheReserve);
          javaCacheReserve = 0;
        }
      }
      else if (src instanceof Vector)
      {
        @SuppressWarnings("unchecked")
        Vector<Object> srcVec = (Vector<Object>) src;
        MetaImage srcImage = (MetaImage) srcVec.get(0);
        MetaImage diffuseImage = (MetaImage) srcVec.get(1);
        if (imageIndex == 0)
        {
          Image srcJava = srcImage.getJavaImage(0);
          Image diffuseJava = diffuseImage == null ? null : diffuseImage.getJavaImage(0);
          setJavaImage(ImageUtils.createDiffusedImage(srcJava, diffuseJava,
              (Rectangle) srcVec.get(2), ((Boolean) srcVec.get(3)).booleanValue(), ((Boolean) srcVec.get(4)).booleanValue(),
              ((Integer) srcVec.get(5)).intValue()), 0, javaCacheReserve);
          srcImage.removeJavaRef(0);
          if (diffuseJava != null)
            diffuseImage.removeJavaRef(0);
        }
        else
        {
          setJavaImage(getJavaImage(0), imageIndex, javaCacheReserve);
          removeJavaRef(0);
        }
        javaCacheReserve = 0;
      }
    }
    catch (Exception e)
    {
      loadFailed = true;
      setJavaImage(ImageUtils.getNullImage(), imageIndex, javaCacheReserve);
      javaCacheReserve = 0;
    }
    if (javaImage[imageIndex] == null)
    {
      setJavaImage(getJavaImage(0), imageIndex, javaCacheReserve); // this'll do a resize cache if needed
      removeJavaRef(0);
      javaCacheReserve = 0;
    }
    if (javaCacheReserve > 0)
      returnJavaCacheReserve(javaCacheReserve);
    return javaImage[imageIndex];
  }
  public synchronized int getImageIndex(Shape desiredShape)
  {
    if (desiredShape == null) return 0;
    for (int i = imageOption.length - 1; i >= 0; i--)
    {
      if (imageAreaTest(desiredShape, imageOption[i]))
        return i;
    }
    incrementDataStructures(1);
    width[numImages - 1] = width[0];
    height[numImages - 1] = height[0];
    imageOption[numImages - 1] = desiredShape;
    return (numImages - 1);
  }
  public synchronized int getImageIndex(int desiredWidth, int desiredHeight)
  {
    if (desiredWidth == 0 && desiredHeight == 0) return 0;
    for (int i = width.length - 1; i >= 0; i--)
    {
      if (width[i] == desiredWidth && height[i] == desiredHeight)
        return i;
    }
    // Scaled image isn't there, so create it
    addScaledImage(desiredWidth, desiredHeight, null);
    return (numImages - 1);
  }
  public synchronized int getImageIndex(int desiredWidth, int desiredHeight, Shape desiredShape)
  {
    if (desiredWidth == 0 && desiredHeight == 0) return 0;
    for (int i = width.length - 1; i >= 0; i--)
    {
      if (width[i] == desiredWidth && height[i] == desiredHeight && imageAreaTest(desiredShape, imageOption[i]))
        return i;
    }
    // Scaled image isn't there, so create it
    addScaledImage(desiredWidth, desiredHeight, desiredShape);
    return (numImages - 1);
  }
  public synchronized int getImageIndex(int desiredWidth, int desiredHeight, Insets[] scalingInsets)
  {
    if (desiredWidth == 0 && desiredHeight == 0) return 0;
    for (int i = width.length - 1; i >= 0; i--)
    {
      if (width[i] == desiredWidth && height[i] == desiredHeight && insetsTest(scalingInsets, imageOption[i]))
        return i;
    }
    // Scaled image isn't there, so create it
    addScaledImage(desiredWidth, desiredHeight, scalingInsets);
    return (numImages - 1);
  }
  private static boolean imageAreaTest(Shape s1, Object option)
  {
    if (s1 == option) return true;
    if (!(option instanceof Shape)) return false;
    Shape s2 = (Shape) option;
    if (s1 == null || s2 == null) return false;
    if (s1.getClass() != s2.getClass()) return false;
    if (s1 instanceof RoundRectangle2D)
    {
      return s1.getBounds2D().equals(s2.getBounds2D()) &&
          (((RoundRectangle2D) s1).getArcWidth() ==
          ((RoundRectangle2D) s2).getArcWidth()) &&
          (((RoundRectangle2D) s1).getArcHeight() ==
          ((RoundRectangle2D) s2).getArcHeight());
    }
    return s1.equals(s2);
  }
  private static boolean insetsTest(Insets[] ins1, Object obj)
  {
    if (ins1 == obj) return true;
    if (!(obj instanceof Insets[]))
      return false;
    Insets[] ins2 = (Insets[]) obj;
    if (ins1 == null || ins2 == null) return false;
    if (ins1.length != 2 || ins2.length != 2 || ins1[0] == null || ins1[1] == null || ins2[0] == null || ins2[1] == null ||
        !ins1[0].equals(ins2[0]) || !ins1[1].equals(ins2[1]))
      return false;
    return true;
  }
  // NOTE: This is NOT synchronized because that can deadlock when you release another image when allocating a new one; but
  // this is for the native cache and that's only really done from a single thread...so while this may not be 100%
  // safe; if we DO synchronize it then we definitely have a deadlock case which is much worse.
  public void clearNativePointer(NativeImageAllocator nia, int imageIndex)
  {
    Object ncl;
    synchronized (ncl = getNiaCacheLock(nia))
    {
      NativeImageData nid = null;
      while (true)
      {
        synchronized (this)
        {
          if (nid == null)
            nid = getNativeImageData(nia);
          if (nid.nativeRefCount[imageIndex] == 0)
          {
            Long nativeImageCacheSizeObj = nativeImageCacheSizeMap.get(nia);
            long nativeImageCacheSize = (nativeImageCacheSizeObj == null) ? 0 : nativeImageCacheSizeObj.longValue();
            if (nid.nativeImage[imageIndex] != 0)
              nia.releaseNativeImage(nid.nativeImage[imageIndex]);
            nid.nativeImage[imageIndex] = 0;
            nativeImageCacheSize -= nid.nativeMemSize[imageIndex];
            nid.nativeMemSize[imageIndex] = 0;
            nativeImageCacheSizeMap.put(nia, new Long(nativeImageCacheSize));
            return;
          }
        }
        // If there are active references to this image then we must wait for them to be released before we deallocate the image
        while (nid.nativeRefCount[imageIndex] > 0)
        {
          if (DEBUG_MI) System.out.println("Waiting on release of native refs before we release a native image mi=" + this);
          try
          {
            ncl.wait(15);
          }
          catch (InterruptedException ie){}
        }
      }
    }
  }
  public void setNativePointer(NativeImageAllocator nia, int imageIndex, long ptr, int memSize)
  {
    if (ptr == 0)
    {
      clearNativePointer(nia, imageIndex);
      return;
    }
    long memChange = 0;
    Object ncl;
    synchronized (ncl = getNiaCacheLock(nia))
    {
      synchronized (this)
      {
        NativeImageData nid = getNativeImageData(nia);
        nid.nativeImage[imageIndex] = ptr;
        memChange = memSize - nid.nativeMemSize[imageIndex];
        nid.nativeMemSize[imageIndex] = memSize;
        nid.lastUsedNative[imageIndex] = Sage.eventTime();
      }
      Long nativeImageCacheSizeObj = nativeImageCacheSizeMap.get(nia);
      long nativeImageCacheSize = (nativeImageCacheSizeObj == null) ? 0 : nativeImageCacheSizeObj.longValue();
      nativeImageCacheSize += memChange;
      nativeImageCacheSizeMap.put(nia, new Long(nativeImageCacheSize));
      ncl.notifyAll();
    }
  }
  public long getNativeImage(NativeImageAllocator nia) { return getNativeImage(nia, 0); }
  public long getNativeImage(NativeImageAllocator nia, int imageIndex)
  {
    NativeImageData nid = null;
    synchronized (this)
    {
      nid = getNativeImageData(nia);
      nid.lastUsedNative[imageIndex] = Sage.eventTime();
      if (nid.nativeImage[imageIndex] != 0)
      {
        addNativeRef(nid, imageIndex);
        return nid.nativeImage[imageIndex];
      }
    }
    Object ncl;
    synchronized (ncl = getNiaCacheLock(nia))
    {
      // Check if there's already native references held to this image; if there are it means another thread is in the process of loading it so
      // we should just wait on their completion and then use the result they get.
      boolean addedToSet = false;
      try
      {
        while (nid.nativeRefCount[imageIndex] > 0 && nid.nativeImage[imageIndex] == 0)
        {
          if (DEBUG_MI) System.out.println("Waiting on load of native image from other thread to complete mi=" + this);
          if (!addedToSet)
          {
            addedToSet = true;
            nclPendingThreads.add(Thread.currentThread());
          }
          try
          {
            ncl.wait(30);
          }
          catch (InterruptedException ie){}
        }
      }
      finally
      {
        if (addedToSet)
          nclPendingThreads.remove(Thread.currentThread());
      }
      synchronized (this)
      {
        // Set the ref now so it's there after the load
        addNativeRef(nid, imageIndex);
        // Double check
        if (nid.nativeImage[imageIndex] != 0)
          return nid.nativeImage[imageIndex];
      }
    }
    try
    {
      nia.createNativeImage(this, imageIndex);
    }
    catch (Throwable t)
    {
      if (Sage.DBG) System.out.println("ERROR loading native image of:" + t);
      if (Sage.DBG) Sage.printStackTrace(t);
      // NOTE: We should probably set the null image in the native reference but that'll be tricky to do...exceptions
      // shouldn't occur here aside from other errors in our code anyways....but at least this will maintain the proper reference count then
      // since the caller won't receive an unexpected exception
    }
    if (!permanent && (nia instanceof DirectX9SageRenderer) && !(src instanceof WeakReference))
    {
      if (javaRefCount[imageIndex] == 0)
      {
        // Its a big waste to hold onto our system memory copy, especially if DX9 is
        // holding its own copy in there.
        releaseJavaImage(imageIndex);
      }
    }
    return nid.nativeImage[imageIndex];
  }
  private synchronized int addScaledImage(int scaledWidth, int scaledHeight, Object option)
  {
    incrementDataStructures(1);
    width[numImages - 1] = scaledWidth;
    height[numImages - 1] = scaledHeight;
    imageOption[numImages - 1] = option;
    return numImages - 1;
    //		ensureCacheCanHoldNewImage(numImages - 1);
    // NOTE: 3/31/06 - I removed this because they'll get loaded on demand later and for remote image cache management
    // we never want to force a Java image load if its not necessary
    //		setJavaImage(getJavaImage(0), numImages - 1);
    //		removeJavaRef(0);
    //		Sage.gc();
  }

  private int releaseNativeImages(NativeImageAllocator nia)
  {
    NativeImageData nid = getNativeImageData(nia);
    int numReleased = 0;
    long freedAmount = 0;
    synchronized (getNiaCacheLock(nia))
    {
      synchronized (this)
      {
        for (int i = 0; i < numImages; i++)
        {
          if (nid.nativeImage[i] != 0)
          {
            if (nid.nativeRefCount[i] == 0)
            {
              nia.releaseNativeImage(nid.nativeImage[i]);
              numReleased++;
              nid.nativeImage[i] = 0;
              nid.lastUsedNative[i] = 0;
              freedAmount += nid.nativeMemSize[i];
              nid.nativeMemSize[i] = 0;
            }
            else
            {
              System.out.println("ERROR: Native image references still exist and we're releasing!!! src=" + src);
            }
          }
        }
      }
      Long nativeImageCacheSizeObj = nativeImageCacheSizeMap.get(nia);
      long nativeImageCacheSize = (nativeImageCacheSizeObj == null) ? 0 : nativeImageCacheSizeObj.longValue();
      nativeImageCacheSize -= freedAmount;
      nativeImageCacheSizeMap.put(nia, new Long(nativeImageCacheSize));
    }
    return numReleased;
  }

  public static Object[] getLeastRecentlyUsedImage(NativeImageAllocator nia, MetaImage dontKillMe, int saveMeIndex)
  {
    return getLeastRecentlyUsedImage(nia, dontKillMe, saveMeIndex, false);
  }

  @SuppressWarnings("unused")
  private static Object[] getLeastRecentlyUsedImage(NativeImageAllocator nia, MetaImage dontKillMe, int saveMeIndex, boolean isRaw)
  {
    long oldest = Long.MAX_VALUE;
    MetaImage oldestImage = null;
    int oldestIndex = 0;
    int outstandingRefs = 0;
    for (int z = 0; z < 2; z++)
    {
      try
      {
        Iterator<MetaImage> walker = (z == 0) ? globalImageCache.values().iterator() :
          globalWeakImageCache.values().iterator();
        while (walker.hasNext())
        {
          MetaImage mi = walker.next();
          if (mi.permanent) continue;
          synchronized (mi) // this'll deadlock because it can be called from another sync block
          {
            NativeImageData nid = null;
            for (int i = 0; i < mi.numImages; i++)
            {
              if (mi == dontKillMe && i == saveMeIndex) continue;
              if (nia == null)
              {
                if (isRaw)
                {
                  if (mi.rawRefCount[i] <= 0 && mi.rawImage[i] != null && mi.lastUsedRaw[i] < oldest &&
                      mi.rawMemSize[i] != 0)
                  {
                    oldest = mi.lastUsedRaw[i];
                    oldestImage = mi;
                    oldestIndex = i;
                  }
                  if (DEBUG_MI && mi.rawRefCount[i] > 0) outstandingRefs++;
                }
                else
                {
                  if (mi.javaRefCount[i] <= 0 && mi.javaImage[i] != null && mi.lastUsedJava[i] < oldest &&
                      mi.javaMemSize[i] != 0)
                  {
                    oldest = mi.lastUsedJava[i];
                    oldestImage = mi;
                    oldestIndex = i;
                  }
                  if (DEBUG_MI && mi.javaRefCount[i] > 0) outstandingRefs++;
                }
              }
              else
              {
                if (nid == null)
                  nid = mi.getNativeImageData(nia);
                if (nid.nativeRefCount[i] <= 0 && nid.nativeImage[i] != 0 && nid.lastUsedNative[i] < oldest)
                {
                  oldest = nid.lastUsedNative[i];
                  oldestImage = mi;
                  oldestIndex = i;
                }
                if (DEBUG_MI && nid.nativeRefCount[i] > 0) outstandingRefs++;
                //if (nid.nativeRefCount[i] > 0)
                //	System.out.println("Native Ref Still Exists For:" + mi.src);
              }
            }
          }
        }
      }
      catch (ConcurrentModificationException cme)
      {
        // If someone else modifies the cache stats while we're analyzing them, then redo the analysis we were just doing
        z--;
        continue;
      }
    }
    if (oldestImage == null)
    {
      if (DEBUG_MI) System.out.println("No free images found to release. Outstanding refCount=" + outstandingRefs);
      return null;
    }
    if (DEBUG_MI) System.out.println("Oldest image sysmem=" + nia + " mi=" + oldestImage + " time=" +
        Sage.df(oldest));
    return new Object[] { oldestImage, new Integer(oldestIndex) };
  }

  // This is used to correlate a native image handle with the corresponding MetaImage/index for it
  public static Object[] getImageDataForNativeHandle(NativeImageAllocator nia, int handle)
  {
    for (int z = 0; z < 2; z++)
    {
      try
      {
        Iterator<MetaImage> walker = (z == 0) ? globalImageCache.values().iterator() :
          globalWeakImageCache.values().iterator();
        while (walker.hasNext())
        {
          MetaImage mi = walker.next();
          synchronized (mi) // this'll deadlock because it can be called from another sync block
          {
            NativeImageData nid = mi.getNativeImageData(nia);
            for (int i = 0; i < mi.numImages; i++)
            {
              if (nid.nativeImage[i] == handle)
                return new Object[] { mi, new Integer(i) };
            }
          }
        }
      }
      catch (ConcurrentModificationException cme)
      {
        // If someone else modifies the cache stats while we're analyzing them, then redo the analysis we were just doing
        z--;
        continue;
      }
    }
    return null;
  }

  private static int getNumCachedJavaImages()
  {
    int numjimages = 0;
    for (int z = 0; z < 2; z++)
    {
      try
      {
        Iterator<MetaImage> walker = (z == 0) ? globalImageCache.values().iterator() :
          globalWeakImageCache.values().iterator();
        while (walker.hasNext())
        {
          MetaImage mi = walker.next();
          if (mi.permanent) continue;
          for (int i = 0; i < mi.numImages; i++)
          {
            if (mi.javaImage[i] != null)
              numjimages++;
          }
        }
      }
      catch (ConcurrentModificationException cme)
      {
        // If someone else modifies the cache stats while we're analyzing them, then restart the counting
        z = -1;
        numjimages = 0;
        continue;
      }
    }
    return numjimages;
  }

  private static int getNumCachedRawImages()
  {
    int numrimages = 0;
    for (int z = 0; z < 2; z++)
    {
      try
      {
        Iterator<MetaImage> walker = (z == 0) ? globalImageCache.values().iterator() :
          globalWeakImageCache.values().iterator();
        while (walker.hasNext())
        {
          MetaImage mi = walker.next();
          if (mi.permanent) continue;
          for (int i = 0; i < mi.numImages; i++)
          {
            if (mi.rawImage[i] != null)
              numrimages++;
          }
        }
      }
      catch (ConcurrentModificationException cme)
      {
        // If someone else modifies the cache stats while we're analyzing them, then restart the counting
        z = -1;
        numrimages = 0;
        continue;
      }
    }
    return numrimages;
  }

  private long reserveJavaCache(int imageIndex)
  {
    long reservedAmount = width[imageIndex] * height[imageIndex] * 4;
    if (reservedAmount > 0)
    {
      synchronized (globalJavaCacheLock)
      {
        javaImageCacheSize += reservedAmount;
      }
    }
    return reservedAmount;
  }

  private static void returnJavaCacheReserve(long reservedAmount)
  {
    synchronized (globalJavaCacheLock)
    {
      javaImageCacheSize -= reservedAmount;
    }
  }

  private long reserveRawCache(int imageIndex)
  {
    long reservedAmount = width[imageIndex] * height[imageIndex] * 4;
    if (reservedAmount > 0)
    {
      synchronized (globalRawCacheLock)
      {
        rawImageCacheSize += reservedAmount;
      }
    }
    return reservedAmount;
  }

  private static void returnRawCacheReserve(long reservedAmount)
  {
    synchronized (globalRawCacheLock)
    {
      rawImageCacheSize -= reservedAmount;
    }
  }

  public static void reserveNativeCache(NativeImageAllocator nia, long reservedAmount)
  {
    if (reservedAmount > 0)
    {
      Object ncl = getNiaCacheLock(nia);
      synchronized (ncl)
      {
        Long nativeImageCacheSizeObj = nativeImageCacheSizeMap.get(nia);
        long nativeImageCacheSize = (nativeImageCacheSizeObj == null) ? 0 : nativeImageCacheSizeObj.longValue();
        nativeImageCacheSizeMap.put(nia, new Long(nativeImageCacheSize + reservedAmount));
      }
    }
  }

  public static void returnNativeCacheReserve(NativeImageAllocator nia, long reservedAmount)
  {
    if (reservedAmount > 0)
    {
      Object ncl = getNiaCacheLock(nia);
      synchronized (ncl)
      {
        Long nativeImageCacheSizeObj = nativeImageCacheSizeMap.get(nia);
        long nativeImageCacheSize = (nativeImageCacheSizeObj == null) ? 0 : nativeImageCacheSizeObj.longValue();
        nativeImageCacheSizeMap.put(nia, new Long(nativeImageCacheSize - reservedAmount));
      }
    }
  }

  // NOTE: We cannot have the lock on a MetaImage when we call this function or it can deadlock from another
  // thread that's doing the inverse of what we're doing.
  private static void maintainJavaCacheSize(long reservedAmount, MetaImage saveMe, int saveMeIndex)
  {
    if (reservedAmount <= 0) return;
    // Since we can't easily determine the size of an image before we load it, we manage
    // the Java cache retroactively to handle this.
    int cacheScale = Sage.getInt("ui/system_memory_2dimage_cache_scale", 2);
    long cacheSize = Sage.getLong("ui/system_memory_2dimage_cache_size", 16000000)*cacheScale - reservedAmount;
    long cacheLimit = Sage.getLong("ui/system_memory_2dimage_cache_limit", 20000000)*cacheScale - reservedAmount;
    // NOTE: Always allow at least a certain number of images to be cached. This way if they
    // loading a huge picture library the memory usage may have a spike, but at least we won't
    // be thrashing around memory.
    int minJavaImageAllowance = Sage.getInt("ui/image_cache_min_num_allowance2", 2);
    boolean check1;
    //synchronized (globalCacheLock)
    {
      check1 = javaImageCacheSize > cacheLimit;
    }
    if (check1 && getNumCachedJavaImages() > minJavaImageAllowance)
    {
      while (true)
      {
        Object[] metaIndex;
        synchronized (globalJavaCacheLock)
        {
          if (javaImageCacheSize <= cacheSize || getNumCachedJavaImages() <= minJavaImageAllowance)
            return;
        }
        // Find the least recently used image and release that one
        metaIndex = getLeastRecentlyUsedImage(null, saveMe, saveMeIndex);
        if (metaIndex == null)
        {
          System.out.println("COULD NOT MAINTAIN JAVA IMAGE CACHE SIZE, SIZE="+javaImageCacheSize + " limit=" +
              cacheLimit);
          return;
        }
        if (DEBUG_MI) System.out.println("Releasing Java image to maintain cache size=" + javaImageCacheSize +
            " " + metaIndex[0]);
        ((MetaImage) metaIndex[0]).releaseJavaImage(((Integer) metaIndex[1]).intValue());
      }
    }
  }

  // NOTE: We cannot have the lock on a MetaImage when we call this function or it can deadlock from another
  // thread that's doing the inverse of what we're doing.
  private static void maintainRawCacheSize(long reservedAmount, MetaImage saveMe, int saveMeIndex)
  {
    // Since we can't easily determine the size of an image before we load it, we manage
    // the raw cache retroactively to handle this.
    int cacheScale = Sage.getInt("ui/system_memory_2dimage_cache_scale", 2);
    long cacheSize = Sage.getLong("ui/system_memory_2dimage_cache_size", 16000000)*cacheScale - reservedAmount;
    long cacheLimit = Sage.getLong("ui/system_memory_2dimage_cache_limit", 20000000)*cacheScale - reservedAmount;
    // NOTE: Always allow at least a certain number of images to be cached. This way if they
    // loading a huge picture library the memory usage may have a spike, but at least we won't
    // be thrashing around memory.
    int minRawImageAllowance = Sage.getInt("ui/image_cache_min_num_allowance2", 2);
    boolean check1;
    //		synchronized (globalCacheLock)
    {
      check1 = rawImageCacheSize > cacheLimit;
    }
    if (check1 && getNumCachedRawImages() > minRawImageAllowance)
    {
      while (true)
      {
        Object[] metaIndex;
        synchronized (globalRawCacheLock)
        {
          if (rawImageCacheSize <= cacheSize || getNumCachedRawImages() <= minRawImageAllowance)
            return;
        }
        // Find the least recently used image and release that one
        metaIndex = getLeastRecentlyUsedImage(null, saveMe, saveMeIndex, true);
        if (metaIndex == null)
        {
          System.out.println("COULD NOT MAINTAIN RAW IMAGE CACHE SIZE, SIZE="+rawImageCacheSize + " limit=" +
              cacheLimit);
          return;
        }
        if (DEBUG_MI) System.out.println("Releasing raw image to maintain cache size=" + rawImageCacheSize +
            " " + metaIndex[0]);
        ((MetaImage) metaIndex[0]).releaseRawImage(((Integer) metaIndex[1]).intValue());
      }
    }
  }

  public Dimension getImageSize(int imageIndex)
  {
    if (imageIndex < 0 || imageIndex >= width.length)
      return null;
    else
      return new Dimension(width[imageIndex], height[imageIndex]);
  }

  @Override
  protected void finalize()
  {
    if (DEBUG_MI) System.out.println("Running finalize for " + this + " id=0x" + Integer.toString(System.identityHashCode(this), 16) + " class=" + getClass());
    int numReleased = 0;
    deleteLocalCacheFile();
    if (nativeAllocData != null)
    {
      for (int i = 0; i < nativeAllocData.length; i++)
      {
        if (nativeAllocData[i] != null && nativeAllocData[i].nia.get() != null)
          numReleased += releaseNativeImages(nativeAllocData[i].nia.get());
      }
    }
    releaseJavaImages();
    releaseRawImages();
    destroyed = true;
  }

  public int getWidth() { return width[0]; }
  public int getWidth(int idx) { return width[idx]; }
  public int getHeight() { return height[0]; }
  public int getHeight(int idx) { return height[idx]; }
  public Object getSource() { return src; }
  public int getNumImages() { return numImages; }

  public String getLcSourcePathname()
  {
    if (src == null)
      return "null";
    if (src instanceof File || src instanceof String || src instanceof URL)
      return src.toString().toLowerCase();
    if (src instanceof MediaFileThumbnail)
    {
      File localSrcFile = ((MediaFileThumbnail) src).mf.getSpecificThumbnailFile();
      return localSrcFile.toString().toLowerCase();
    }
    if (src instanceof MediaFile)
    {
      File localSrcFile = ((MediaFile) src).getFile(0);
      return localSrcFile.toString().toLowerCase();
    }
    if (src instanceof Album)
    {
      MediaFile mf = findMediaFileForAlbum();
      if (mf != null && mf.thumbnailFile != null)
        return mf.thumbnailFile.toString().toLowerCase();
    }
    return src.toString().toLowerCase();
  }

  public synchronized void addJavaRef(int imageIndex)
  {
    if (DEBUG_MI) System.out.println("Adding java ref for: " + this);
    javaRefCount[imageIndex]++;
  }
  public void removeJavaRef(int imageIndex)
  {
    synchronized (this)
    {
      if (DEBUG_MI) System.out.println("Removing java ref for: " + this);
      javaRefCount[imageIndex] = Math.max(0, javaRefCount[imageIndex] - 1);
    }
  }
  public synchronized void addRawRef(int imageIndex)
  {
    if (DEBUG_MI) System.out.println("Adding raw ref for: " + this);
    rawRefCount[imageIndex]++;
  }
  public void removeRawRef(int imageIndex)
  {
    synchronized (this)
    {
      if (DEBUG_MI) System.out.println("Removing raw ref for: " + this);
      rawRefCount[imageIndex] = Math.max(0, rawRefCount[imageIndex] - 1);
    }
  }
  public synchronized void addNativeRef(NativeImageAllocator nia, int imageIndex)
  {
    NativeImageData nid = getNativeImageData(nia);
    nid.nativeRefCount[imageIndex]++;
  }
  private synchronized void addNativeRef(NativeImageData nid, int imageIndex)
  {
    nid.nativeRefCount[imageIndex]++;
  }
  public void removeNativeRef(NativeImageAllocator nia, int imageIndex)
  {
    NativeImageData nid;
    synchronized (this)
    {
      nid = getNativeImageData(nia);
      nid.nativeRefCount[imageIndex] = Math.max(0, nid.nativeRefCount[imageIndex] - 1);
    }
  }
  @SuppressWarnings("unused")
  private synchronized void removeNativeRef(NativeImageData nid, int imageIndex)
  {
    nid.nativeRefCount[imageIndex] = Math.max(0, nid.nativeRefCount[imageIndex] - 1);
  }
  public int getNativeMemUse(NativeImageAllocator nia, int imageIndex)
  {
    NativeImageData nid = getNativeImageData(nia);
    return nid.nativeMemSize[imageIndex];
  }

  @Override
  public String toString()
  {
    StringBuffer sb = new StringBuffer("MetaImage[");
    sb.append(src);
    for (int i = 0; i < numImages; i++)
    {
      sb.append("#" + i + " ");
      sb.append(width[i] + "x" + height[i]);
      if (imageOption[i] != null)
        sb.append(" Option=" + imageOption[i]);
      sb.append(" javaImage=" + (javaImage[i] != null));
      sb.append(" javaMem=" + javaMemSize[i]);
      sb.append(" jref=" + javaRefCount[i]);
      //sb.append(" nativeImage=" + (nativeImage[i] != 0));
      //sb.append(" nativeMem=" + nativeMemSize[i]);
      //sb.append(" nref=" + nativeRefCount[i] + " ");
    }
    sb.append("]");
    return sb.toString();
  }

  public boolean isNullOrFailed()
  {
    return (globalImageCache.get(null) == this) || loadFailed || src == null;
  }

  public byte[] getSourceAsBytes()
  {
    if (src instanceof MetaFont)
      return null;
    else if (localCacheFile != null && localCacheFile.isFile() && localCacheFile.length() > 0)
    {
      if ( localCacheFileTracker!=null )
        localCacheFileTracker.touch(this);
      return IOUtils.getFileAsBytes(localCacheFile);
    }
    else if (src instanceof String)
    {
      try
      {
        InputStream is = getClass().getClassLoader().getResourceAsStream(src.toString());
        if (is == null)
          return null;
        ByteArrayOutputStream rv = new ByteArrayOutputStream(2048);
        byte[] buf = new byte[1024];
        int numRead = is.read(buf);
        while (numRead > 0)
        {
          rv.write(buf, 0, numRead);
          numRead = is.read(buf);
        }
        is.close();
        return rv.toByteArray();
      }
      catch (Exception e)
      {
        if (Sage.DBG) System.out.println("ERROR reading resource " + src + " as stream of:" + e);
        return null;
      }
    }
    else if (src instanceof URL)
    {
      try
      {
        InputStream is = ((URL) src).openStream();
        ByteArrayOutputStream rv = new ByteArrayOutputStream(2048);
        byte[] buf = new byte[1024];
        int numRead = is.read(buf);
        while (numRead > 0)
        {
          rv.write(buf, 0, numRead);
          numRead = is.read(buf);
        }
        is.close();
        return rv.toByteArray();
      }
      catch (IOException e)
      {
        if (Sage.DBG) System.out.println("ERROR reading url " + src + " of:" + e);
        return null;
      }
    }
    else if (src instanceof File)
    {
      return IOUtils.getFileAsBytes((File) src);
    }
    else if (src instanceof MediaFile)
    {
      try
      {
        MediaFile mf = (MediaFile) src;
        File localSrcFile = mf.getFile(0);
        if (mf.isLocalFile() || !Sage.client/*isTrueClient()*/) // optimize for pseudo-clients
        {
          return IOUtils.getFileAsBytes(localSrcFile);
        }
        else
        {
          return mf.copyToLocalMemory(localSrcFile, 0, 0, null);
        }
      }
      catch (IOException e)
      {
        if (Sage.DBG) System.out.println("ERROR reading file " + src + " of:" + e);
        return null;
      }
    }
    else if (src instanceof MediaFileThumbnail)
    {
      MediaFileThumbnail mft = (MediaFileThumbnail) src;
      File localSrcFile = mft.mf.getSpecificThumbnailFile();
      if ((mft.mf.isLocalFile() || !Sage.client) && !mft.mf.isThumbnailEmbedded())
      {
        return IOUtils.getFileAsBytes(localSrcFile);
      }
      else
      {
        return mft.mf.loadEmbeddedThumbnailData();
      }
    }
    else if (src instanceof Album)
    {
      MediaFile mf = findMediaFileForAlbum();
      if (mf != null)
      {
        return mf.loadEmbeddedThumbnailData();
      }
    }
    return null;
  }

  public static String convertToAsciiName(String s)
  {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (Character.isLetterOrDigit(c))
        sb.append(c);
      else
        sb.append(Integer.toString(c, 16));
    }
    return sb.toString();
  }

  // Returns a string that will attempt to uniquely identify this image. Can be used for remote
  // caching purposes. There's a timestamp for the image embedded in it in addition to path/name information.
  public String getUniqueResourceID(int imageIndex)
  {
    if (imageOption[imageIndex] != null) return "";
    if (src instanceof MetaFont)
    {
      MetaFont mf = (MetaFont) src;
      return "glyphmap-" + convertToAsciiName(mf.getName()) + "-" + mf.getSize() + "-" + mf.getStyle() + "-" + imageIndex + "-" + SageTV.hostname + "-" + Version.VERSION +
          "-" + getWidth(imageIndex) + "-" + getHeight(imageIndex);
    }
    else if (src instanceof String)
    {
      return "resource-" + convertToAsciiName(src.toString()) + "-" + Version.VERSION +
          "-" + getWidth(imageIndex) + "-" + getHeight(imageIndex);
    }
    else if (src instanceof URL)
    {
      return "url-" + convertToAsciiName(src.toString()) +
          "-" + getWidth(imageIndex) + "-" + getHeight(imageIndex);
    }
    else if (src instanceof File)
    {
      File f = (File) src;
      long t = f.lastModified();
      return "file-" + convertToAsciiName(src.toString()) + "-" + (t - (t%1000)) +
          "-" + getWidth(imageIndex) + "-" + getHeight(imageIndex);
    }
    else if (src instanceof MediaFile)
    {
      MediaFile mf = (MediaFile) src;
      File localSrcFile = mf.getFile(0);
      if (mf.isLocalFile())
      {
        long t = localSrcFile.lastModified();
        return "file-" + WidgetMeta.convertToCleanPropertyName(localSrcFile.toString()) + "-" + (t - (t%1000)) +
            "-" + getWidth(imageIndex) + "-" + getHeight(imageIndex);
      }
      else
      {
        return ""; // we can't cache this accurately
      }
    }
    else if (src instanceof MediaFileThumbnail)
    {
      MediaFileThumbnail mft = (MediaFileThumbnail) src;
      File localSrcFile = mft.mf.getSpecificThumbnailFile();
      if (mft.mf.isLocalFile())
      {
        long t = localSrcFile.lastModified();
        return "thumbfile-" + WidgetMeta.convertToCleanPropertyName(localSrcFile.toString()) + "-" + (t - (t%1000)) +
            "-" + getWidth(imageIndex) + "-" + getHeight(imageIndex);
      }
      else
      {
        return ""; // we can't cache this accurately
      }
    }
    else if (src instanceof Album)
    {
      MediaFile mf = findMediaFileForAlbum();
      if (mf != null)
      {
        File localSrcFile = mf.getFile(0);
        long t = localSrcFile.lastModified();
        return "embedded-" + WidgetMeta.convertToCleanPropertyName(localSrcFile.toString()) + "-" + (t - (t%1000)) +
            "-" + getWidth(imageIndex) + "-" + getHeight(imageIndex);
      }
    }
    return "";
  }

  public Object getImageOption(int imageIndex)
  {
    return imageOption[imageIndex];
  }

  public boolean hasAlpha()
  {
    return sourceHasAlpha;
  }

  // Returns true if there's any Java cached images for this MetaImage, or if the corresponding UIMgr
  // has it in its native cache
  public boolean mightLoadFast(UIManager uiMgr)
  {
    if ( localCacheFileTracker != null && localCacheFile != null ) {
      localCacheFileTracker.touch(this);
    }

    if (uiMgr != null && uiMgr.getRootPanel() != null && uiMgr.getRootPanel().getRenderEngine() instanceof NativeImageAllocator)
    {
      if (nativeAllocData != null)
      {
        NativeImageAllocator niaTest = (NativeImageAllocator) uiMgr.getRootPanel().getRenderEngine();
        for (int i = 0; i < nativeAllocData.length; i++)
        {
          if (nativeAllocData[i] != null && nativeAllocData[i].nia != null && nativeAllocData[i].nia.get() == niaTest)
          {
            for (int j = 0; j < nativeAllocData[i].nativeMemSize.length; j++)
            {
              if (nativeAllocData[i].nativeMemSize[j] > 0)
                return true;
            }
          }
        }
      }
      // If we've got hardware scaling then the image can be fully loaded into the VRAM cache
      if (uiMgr.getRootPanel().getRenderEngine() instanceof DirectX9SageRenderer ||
          (uiMgr.getRootPanel().getRenderEngine() instanceof MiniClientSageRenderer &&
              ((MiniClientSageRenderer) uiMgr.getRootPanel().getRenderEngine()).getGfxScalingCaps() == MiniClientSageRenderer.GFX_SCALING_HW))
        return false;
    }
    for (int i = 0; javaImage != null && i < javaImage.length; i++)
    {
      if (javaImage[i] != null)
        return true;
    }
    for (int i = 0; rawImage != null && i < rawImage.length; i++)
    {
      if (rawImage[i] != null)
        return true;
    }
    return false;
  }

  private void setRawImage(sage.media.image.RawImage img, int idx, long reservedAmount)
  {
    if (img == null)
      img = sage.media.image.ImageLoader.getNullImage();
    if (width[idx] != 0 && height[idx] != 0 && (img.getWidth() != width[idx] || img.getHeight() != height[idx]))
    {
      // We need to scale the image
      if (img == sage.media.image.ImageLoader.getNullImage())
      {
        img = sage.media.image.ImageLoader.createNullImage(width[idx], height[idx]);
      }
      else
      {
        if (Sage.DBG) System.out.println("ERROR: RawImages don't autoscale when set!");
        img = sage.media.image.ImageLoader.createNullImage(width[idx], height[idx]);
      }
    }
    synchronized (globalRawCacheLock)
    {
      if (DEBUG_MI) System.out.println("MetaImage setRawImage rawImageCacheSize=" + rawImageCacheSize + " img=" + img + " idx=" + idx + " " + this);
      if (width[idx] == 0 || height[idx] == 0)
      {
        maintainRawCacheSize(img.getWidth()*img.getHeight()*4 - reservedAmount, this, idx);
      }
      synchronized (this)
      {
        rawImage[idx] = img;
        width[idx] = img.getWidth();
        height[idx] = img.getHeight();
      }
      //synchronized (globalCacheLock)
      {
        rawImageCacheSize += width[idx]*height[idx]*4 - rawMemSize[idx] - reservedAmount;
        rawMemSize[idx] = width[idx]*height[idx]*4; // assume 32-bit pixels
      }
      lastUsedRaw[idx] = Sage.eventTime();
      if (img == sage.media.image.ImageLoader.getNullImage())
      {
        loadFailed = true;
        //synchronized (globalCacheLock)
        {
          rawImageCacheSize -= rawMemSize[idx];
          rawMemSize[idx] = 0;
        }
      }
      else
      {
        loadFailed = false;
      }
      maintainRawCacheSize(0, this, idx);
      globalRawCacheLock.notifyAll();
    }
    if (DEBUG_MI) System.out.println("MetaImage setRawImage returning rawImageCacheSize=" + rawImageCacheSize + " img=" + img + " idx=" + idx + " " + this);
  }

  public boolean hasScaledInsets(int imageIndex)
  {
    return imageIndex > 0 && imageIndex < numImages && imageOption[imageIndex] instanceof Insets[];
  }

  private int[] getInsetsArray(int imageIndex)
  {
    if (!hasScaledInsets(imageIndex)) return null;
    Insets[] inny = (Insets[]) imageOption[imageIndex];
    if (inny.length != 2 || inny[0] == null || inny[1] == null) return null;
    return new int[] { inny[0].top, inny[0].right, inny[0].bottom, inny[0].left,
        inny[1].top, inny[1].right, inny[1].bottom, inny[1].left };
  }

  public sage.media.image.RawImage getRawImage(int imageIndex)
  {
    synchronized (this)
    {
      lastUsedRaw[imageIndex] = Sage.eventTime();
      if (rawImage[imageIndex] != null)
      {
        addRawRef(imageIndex);
        return rawImage[imageIndex];
      }
    }
    long rawCacheReserve = 0;
    synchronized (globalRawCacheLock)
    {
      // Check if there's already raw references held to this image; if there are it means another thread is in the process of loading it so
      // we should just wait on their completion and then use the result they get.
      while (rawRefCount[imageIndex] > 0 && rawImage[imageIndex] == null)
      {
        if (DEBUG_MI) System.out.println("Waiting on load of Raw image from other thread to complete mi=" + this);
        try
        {
          globalRawCacheLock.wait(30);
        }
        catch (InterruptedException ie){}
      }
      synchronized (this)
      {
        // Add the ref now so it's there after the image is loaded
        addRawRef(imageIndex);
        // Double check on if it's already loaded
        if (rawImage[imageIndex] != null)
          return rawImage[imageIndex];
      }
      ensureCacheCanHoldNewRawImage(imageIndex);
      rawCacheReserve = reserveRawCache(imageIndex);
    }
    try
    {
      if (src instanceof MetaFont)
      {
        MetaFont fonty = (MetaFont) src;
        if (javaImage[imageIndex] instanceof BufferedImage && !loadFailed)
        {
          if (Sage.DBG) System.out.println("Creating RawImage for Font from cached Java image - " + src);
          // If we've got a Java copy just create the raw copy from that since it's faster
          setRawImage(new sage.media.image.RawImage((BufferedImage)getJavaImage(imageIndex)), imageIndex, rawCacheReserve);
          removeJavaRef(imageIndex);
        }
        else
          setRawImage(fonty.loadRawFontImage(SageRenderer.getAcceleratedFont(fonty), imageIndex), imageIndex, rawCacheReserve);
        rawCacheReserve = 0;
      }
      else if (hasScaledInsets(imageIndex))
      {
        sage.media.image.RawImage rawy = getRawImage(0);
        if (Sage.DBG) System.out.println("Creating RawImage with scaled insets from raw copy for " + src);
        setRawImage(sage.media.image.ImageLoader.scaleRawImageWithInsets(rawy, getWidth(imageIndex),
            getHeight(imageIndex), getInsetsArray(imageIndex)), imageIndex, rawCacheReserve);
        removeRawRef(0);
        rawCacheReserve = 0;
      }
      else
      {
        // See if we've got a raw copy we can just scale; if not then check if we've got a Java copy
        // we can copy over; and lastly check for the original Java copy and then scale of that; if none
        // of that is there then we have to fully load it again
        if (rawImage[0] != null && !loadFailed)
        {
          if (Sage.DBG) System.out.println("Creating scaled copy of RawImage of size " + getWidth(imageIndex) + "x" +
              getHeight(imageIndex) + " for " + src);
          sage.media.image.RawImage rawy = getRawImage(0);
          setRawImage(sage.media.image.ImageLoader.scaleRawImage(rawy, getWidth(imageIndex), getHeight(imageIndex)), imageIndex, rawCacheReserve);
          removeRawRef(0);
          rawCacheReserve = 0;
        }
        else if (javaImage[imageIndex] instanceof BufferedImage && !loadFailed &&
            sage.media.image.RawImage.canCreateRawFromJava((BufferedImage) javaImage[imageIndex]))
        {
          if (Sage.DBG) System.out.println("Creating copy of Java image to RawImage of size " + getWidth(imageIndex) + "x" +
              getHeight(imageIndex) + " for " + src);
          setRawImage(new sage.media.image.RawImage((BufferedImage) getJavaImage(imageIndex)), imageIndex, rawCacheReserve);
          removeJavaRef(imageIndex);
          rawCacheReserve = 0;
        }
        else if (javaImage[0] instanceof BufferedImage && !loadFailed &&
            sage.media.image.RawImage.canCreateRawFromJava((BufferedImage) javaImage[0]))
        {
          BufferedImage buffy = (BufferedImage) getJavaImage(0);
          sage.media.image.RawImage initialRaw = new sage.media.image.RawImage(buffy);
          if (Sage.DBG) System.out.println("Creating scaled copy of RawImage from Java image of size " + getWidth(imageIndex) + "x" +
              getHeight(imageIndex) + " for " + src);
          setRawImage(sage.media.image.ImageLoader.scaleRawImage(initialRaw, getWidth(imageIndex), getHeight(imageIndex)), imageIndex, rawCacheReserve);
          sage.media.image.ImageLoader.freeImage(initialRaw);
          removeJavaRef(0);
          rawCacheReserve = 0;
        }
        else if (src instanceof WeakReference)
        {
          Image img = (Image) ((WeakReference<?>)src).get();
          BufferedImage buffImg;
          boolean freeBuffy = false;
          if (!(img instanceof BufferedImage) || !sage.media.image.RawImage.canCreateRawFromJava((BufferedImage)img))
          {
            freeBuffy = true;
            buffImg = ImageUtils.createBestImage(img);
          }
          else
          {
            buffImg = (BufferedImage) img;
          }
          if (buffImg.getWidth() == getWidth(imageIndex) && buffImg.getHeight() == getHeight(imageIndex))
          {
            if (Sage.DBG) System.out.println("Creating copy of src Java image to RawImage of size " + getWidth(imageIndex) + "x" +
                getHeight(imageIndex) + " for " + src);
            setRawImage(new sage.media.image.RawImage(buffImg), imageIndex, rawCacheReserve);
          }
          else
          {
            sage.media.image.RawImage initialRaw = new sage.media.image.RawImage(buffImg);
            if (Sage.DBG) System.out.println("Creating scaled copy of RawImage from src Java image of size " + getWidth(imageIndex) + "x" +
                getHeight(imageIndex) + " for " + src);
            setRawImage(sage.media.image.ImageLoader.scaleRawImage(initialRaw, getWidth(imageIndex), getHeight(imageIndex)), imageIndex, rawCacheReserve);
            sage.media.image.ImageLoader.freeImage(initialRaw);
          }
          rawCacheReserve = 0;
          if (freeBuffy)
            buffImg.flush();
        }
        else if (src instanceof File)
        {
          File f = (File) src;
          if (Sage.DBG) System.out.println("Loading RawImage of size " + getWidth(imageIndex) + "x" +
              getHeight(imageIndex) + " for " + src + " fileSize=" + f.length());
          setRawImage(loadScaledImageFromFileSafely(f.toString(),
              imageIndex == 0 ? 0 : getWidth(imageIndex), imageIndex == 0 ? 0 : getHeight(imageIndex)), imageIndex, rawCacheReserve);
          rawCacheReserve = 0;
        }
        else if (src instanceof MediaFile)
        {
          if (Sage.DBG) System.out.println("Loading RawImage of size " + getWidth(imageIndex) + "x" +
              getHeight(imageIndex) + " for " + src);
          MediaFile mf = (MediaFile) src;
          File localSrcFile = mf.getFile(0);
          if (mf.isLocalFile() || !Sage.client/*isTrueClient()*/) // optimize for pseudo-clients
          {
            setRawImage(sage.media.image.ImageLoader.loadResizedRotatedImageFromFile(localSrcFile.toString(),
                imageIndex == 0 ? 0 : getWidth(imageIndex), imageIndex == 0 ? 0 : getHeight(imageIndex),
                    32, autoRotated),
                    imageIndex, rawCacheReserve);
          }
          else
          {
            byte[] imageBytes = mf.copyToLocalMemory(localSrcFile, 0, 0, null);
            setRawImage(sage.media.image.ImageLoader.loadScaledImageFromMemory(imageBytes,
                imageIndex == 0 ? 0 : getWidth(imageIndex), imageIndex == 0 ? 0 : getHeight(imageIndex),
                    32, autoRotated),
                    imageIndex, rawCacheReserve);
          }
          rawCacheReserve = 0;
        }
        else if (src instanceof MediaFileThumbnail)
        {
          if (Sage.DBG) System.out.println("Loading RawImage of size " + getWidth(imageIndex) + "x" +
              getHeight(imageIndex) + " for " + src);
          MediaFileThumbnail mft = (MediaFileThumbnail) src;
          File localSrcFile = mft.mf.getSpecificThumbnailFile();
          if ((mft.mf.isLocalFile() || !Sage.client) && !mft.mf.isThumbnailEmbedded())
          {
            setRawImage(sage.media.image.ImageLoader.loadResizedRotatedImageFromFile(localSrcFile.toString(),
                imageIndex == 0 ? 0 : getWidth(imageIndex), imageIndex == 0 ? 0 : getHeight(imageIndex),
                    32, autoRotated),
                    imageIndex, rawCacheReserve);
          }
          else
          {
            byte[] imageBytes = mft.mf.loadEmbeddedThumbnailData();
            setRawImage(sage.media.image.ImageLoader.loadScaledImageFromMemory(imageBytes,
                imageIndex == 0 ? 0 : getWidth(imageIndex), imageIndex == 0 ? 0 : getHeight(imageIndex),
                    32, autoRotated),
                    imageIndex, rawCacheReserve);
          }
          rawCacheReserve = 0;
        }
        else if (src instanceof String || src instanceof URL)
        {
          if (Sage.DBG) System.out.println("Loading RawImage of size " + getWidth(imageIndex) + "x" +
              getHeight(imageIndex) + " for " + src + (localCacheFile != null ? (" fileSize=" + localCacheFile.length()) : ""));
          if (localCacheFile == null || !localCacheFile.isFile() || localCacheFile.length() == 0)
          {
            if (DEBUG_MI) System.out.println("Sync-loading in getRawImage as Local cache file - file "+localCacheFile+" is not found");
            if (!loadCacheFile())
              throw new RuntimeException("ERROR loading resource file from:" + src);
          }

          if ( localCacheFileTracker!=null && localCacheFile != null)
            localCacheFileTracker.touch(this);

          setRawImage(loadScaledImageFromFileSafely(localCacheFile.toString(),
              imageIndex == 0 ? 0 : getWidth(imageIndex), imageIndex == 0 ? 0 : getHeight(imageIndex)), imageIndex, rawCacheReserve);
          rawCacheReserve = 0;
          // Image loaded, do we need to delete the on-disk tmp file.
          // nielm(19/12/2011) I know this can be collapsed into a single
          // IF statement, but this is a lot clearer...
          //
          // DO NOT delete the smb:// files because they are the REAL files; not a copy
          if (!src.toString().startsWith("smb://")) {
            if  ( localCacheFileTracker != null ) {
              // tracked files
              if ( DELETE_TRACKED_LOCAL_CACHE_FILE_ON_IMAGE_LOAD ){
                if (DEBUG_MI) System.out.println("GetRawImage(): Deleting local image cache file: " + localCacheFile);
                deleteLocalCacheFile();
              }
            } else {
              // untracked files...
              // delete if offline cache is not available, or if a large file
              if (!ENABLE_OFFLINE_URL_CACHE || localCacheFile.length() > 500000) {
                if (DEBUG_MI) System.out.println("GetRawImage(): Deleting local image cache file: " + localCacheFile);
                deleteLocalCacheFile();
              }
            }
          }
        }
        else if (src instanceof Album)
        {
          if (Sage.DBG) System.out.println("Loading RawImage of size " + getWidth(imageIndex) + "x" +
              getHeight(imageIndex) + " for " + src);
          MediaFile mf = findMediaFileForAlbum();
          if (mf != null)
          {
            byte[] imData = mf.loadEmbeddedThumbnailData();
            if (imData != null)
            {
              setRawImage(sage.media.image.ImageLoader.loadScaledImageFromMemory(imData,
                  imageIndex == 0 ? 0 : getWidth(imageIndex), imageIndex == 0 ? 0 : getHeight(imageIndex)),
                  imageIndex, rawCacheReserve);
              rawCacheReserve = 0;
            }
          }
        }
        else if (src instanceof Vector)
        {
          @SuppressWarnings("unchecked")
          Vector<Object> srcVec = (Vector<Object>) src;
          MetaImage srcImage = (MetaImage) srcVec.get(0);
          MetaImage diffuseImage = (MetaImage) srcVec.get(1);
          int diffuseColor = ((Integer) srcVec.get(5)).intValue();
          boolean hasDiffuseColor = diffuseColor != 0xFFFFFF;
          int diffuseR = (diffuseColor >>> 16) & 0xFF;
          int diffuseG = (diffuseColor >>> 8) & 0xFF;
          int diffuseB = diffuseColor & 0xFF;
          if (diffuseImage != null)
          {
            sage.media.image.RawImage srcRaw = srcImage.getRawImage(0);
            Rectangle diffuseRect = (Rectangle) srcVec.get(2);
            int targetWidth = (diffuseRect == null) ? srcRaw.getWidth() : diffuseRect.width;
            int targetHeight = (diffuseRect == null) ? srcRaw.getHeight() : diffuseRect.height;
            int offx = (diffuseRect == null) ? 0 : diffuseRect.x;
            int offy = (diffuseRect == null) ? 0 : diffuseRect.y;
            boolean flipX = ((Boolean) srcVec.get(3)).booleanValue();
            boolean flipY = ((Boolean) srcVec.get(4)).booleanValue();
            int diffuseIdx = diffuseImage.getImageIndex(targetWidth, targetHeight);
            sage.media.image.RawImage diffuseRaw = diffuseImage.getRawImage(diffuseIdx);

            ByteBuffer targetBuff = ByteBuffer.allocateDirect(targetWidth * targetHeight * 4);
            ByteBuffer srcBuff = srcRaw.getROData();
            ByteBuffer diffuseBuff = diffuseRaw.getROData();
            targetBuff.clear();
            int srcWidth4 = 4*srcRaw.getWidth();
            for (int y = offy; y < offy + targetHeight; y++)
            {
              int srcoff = y*srcWidth4;
              int dstoff = y*4*targetWidth;
              int targetPos = dstoff;
              if (flipY)
                targetBuff.position(targetPos = ((targetHeight - (y - offy) - 1)*4*targetWidth));
              for (int x = offx; x < offx + targetWidth; x++)
              {
                int srcoff2 = srcoff + 4*x;
                int dstoff2 = dstoff + 4*(x - offx);
                if (flipX)
                  targetBuff.position(targetPos + (targetWidth - (x - offx) - 1) * 4);
                targetBuff.put((byte)((((srcBuff.get(srcoff2) & 0xFF) * (diffuseBuff.get(dstoff2) & 0xFF)) / 255) & 0xFF));
                if (hasDiffuseColor)
                {
                  targetBuff.put((byte)((((srcBuff.get(srcoff2 + 1) & 0xFF) * (diffuseBuff.get(dstoff2 + 1) & 0xFF) * diffuseR) / 65025) & 0xFF));
                  targetBuff.put((byte)((((srcBuff.get(srcoff2 + 2) & 0xFF) * (diffuseBuff.get(dstoff2 + 2) & 0xFF) * diffuseG) / 65025) & 0xFF));
                  targetBuff.put((byte)((((srcBuff.get(srcoff2 + 3) & 0xFF) * (diffuseBuff.get(dstoff2 + 3) & 0xFF) * diffuseB) / 65025) & 0xFF));
                }
                else
                {
                  targetBuff.put((byte)((((srcBuff.get(srcoff2 + 1) & 0xFF) * (diffuseBuff.get(dstoff2 + 1) & 0xFF)) / 255) & 0xFF));
                  targetBuff.put((byte)((((srcBuff.get(srcoff2 + 2) & 0xFF) * (diffuseBuff.get(dstoff2 + 2) & 0xFF)) / 255) & 0xFF));
                  targetBuff.put((byte)((((srcBuff.get(srcoff2 + 3) & 0xFF) * (diffuseBuff.get(dstoff2 + 3) & 0xFF)) / 255) & 0xFF));
                }
              }
            }
            srcImage.removeRawRef(0);
            diffuseImage.removeRawRef(diffuseIdx);
            sage.media.image.RawImage img0 = new sage.media.image.RawImage(targetWidth, targetHeight, targetBuff, true,
                4 * targetWidth, true);
            setRawImage(img0, 0, rawCacheReserve);
            if (imageIndex != 0)
            {
              // Now that we have the 0-index image cached; we can just use our standard raw image scaler to get the other one
              setRawImage(sage.media.image.ImageLoader.scaleRawImage(img0, getWidth(imageIndex), getHeight(imageIndex)), imageIndex, rawCacheReserve);
            }
            rawCacheReserve = 0;

          }
          else
          {
            // Flipping only
            int srcImgIdx = imageIndex == 0 ? 0 : srcImage.getImageIndex(getWidth(imageIndex), getHeight(imageIndex));
            sage.media.image.RawImage srcRaw = srcImage.getRawImage(srcImgIdx);
            int targetWidth = srcRaw.getWidth();
            int targetHeight = srcRaw.getHeight();
            boolean flipX = ((Boolean) srcVec.get(3)).booleanValue();
            boolean flipY = ((Boolean) srcVec.get(4)).booleanValue();
            ByteBuffer targetBuff = ByteBuffer.allocateDirect(targetWidth * targetHeight * 4);
            ByteBuffer srcBuff = srcRaw.getROData();
            targetBuff.clear();
            for (int y = 0; y < targetHeight; y++)
            {
              int dstoff = y*4*targetWidth;
              int targetPos = dstoff;
              if (flipY)
                targetBuff.position(targetPos = ((targetHeight - y - 1)*4*targetWidth));
              for (int x = 0; x < targetWidth; x++)
              {
                int dstoff2 = dstoff + 4*x;
                if (flipX)
                  targetBuff.position(targetPos + (targetWidth - x - 1) * 4);
                targetBuff.put(srcBuff.get(dstoff2));
                if (hasDiffuseColor)
                {
                  targetBuff.put((byte)(((srcBuff.get(dstoff2 + 1) & 0xFF) * diffuseR / 255) & 0xFF));
                  targetBuff.put((byte)(((srcBuff.get(dstoff2 + 2) & 0xFF) * diffuseR / 255) & 0xFF));
                  targetBuff.put((byte)(((srcBuff.get(dstoff2 + 3) & 0xFF) * diffuseR / 255) & 0xFF));
                }
                else
                {
                  targetBuff.put(srcBuff.get(dstoff2 + 1));
                  targetBuff.put(srcBuff.get(dstoff2 + 2));
                  targetBuff.put(srcBuff.get(dstoff2 + 3));
                }
              }
            }
            srcImage.removeRawRef(srcImgIdx);
            setRawImage(new sage.media.image.RawImage(targetWidth, targetHeight, targetBuff, true,
                4 * targetWidth, true), imageIndex, rawCacheReserve);
            rawCacheReserve = 0;
          }
        }
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("ImageLoad failed for:" + src + " with:" + e);
      e.printStackTrace();
      loadFailed = true;
      setRawImage(sage.media.image.ImageLoader.getNullImage(), imageIndex, rawCacheReserve);
      rawCacheReserve = 0;
    }
    if (rawImage[imageIndex] == null)
    {
      if (Sage.DBG) System.out.println("ImageLoad failed for:" + src);
      loadFailed = true;
      setRawImage(sage.media.image.ImageLoader.getNullImage(), imageIndex, rawCacheReserve);
      rawCacheReserve = 0;
    }
    if (rawCacheReserve > 0)
      returnRawCacheReserve(rawCacheReserve);
    return rawImage[imageIndex];
  }

  public void releaseRawImage(int idx)
  {
    synchronized (globalRawCacheLock)
    {
      synchronized (this)
      {
        if (DEBUG_MI) System.out.println("MetaImage releaseRawImage rawImageCacheSize=" + rawImageCacheSize + " " + this);
        if (rawRefCount[idx] > 0 || (permanent && idx == 0))
        {
          if (DEBUG_MI) System.out.println("CANNOT release meta image, it still has refs");
          return;
        }
        if (rawImage[idx] != null && rawImage[idx] != sage.media.image.ImageLoader.getNullImage())
        {
          sage.media.image.ImageLoader.freeImage(rawImage[idx]);
        }
        rawImage[idx] = null;
        rawImageCacheSize -= rawMemSize[idx];
        rawMemSize[idx] = 0;
      }
    }
    if (DEBUG_MI) System.out.println("MetaImage releaseRawImage returning rawImageCacheSize=" + rawImageCacheSize + " " + this);
  }
  public void releaseRawImages()
  {
    synchronized (globalRawCacheLock)
    {
      synchronized (this)
      {
        if (DEBUG_MI) System.out.println("MetaImage releaseRawImages rawImageCacheSize=" + rawImageCacheSize + " " + this);
        for (int i = 0; i < rawImage.length; i++)
        {
          if (rawRefCount[i] > 0 || (permanent && i == 0))
          {
            if (DEBUG_MI) System.out.println("CANNOT release meta image, it still has refs");
            continue;
          }
          if (rawImage[i] != null && rawImage[i] != sage.media.image.ImageLoader.getNullImage())
          {
            sage.media.image.ImageLoader.freeImage(rawImage[i]);
          }
          rawImage[i] = null;
          rawImageCacheSize -= rawMemSize[i];
          rawMemSize[i] = 0;
        }
      }
    }
    if (DEBUG_MI) System.out.println("MetaImage releaseRawImages returning rawImageCacheSize=" + rawImageCacheSize + " " + this);
  }

  public static void printCacheContents()
  {
    //		synchronized (globalCacheLock)
    {
      System.out.println("DUMPING META IMAGE CACHE---->");
      Iterator<MetaImage> walker = globalImageCache.values().iterator();
      int x = 0;
      while (walker.hasNext())
      {
        MetaImage mi = walker.next();
        System.out.println("#" + x + " src=" + mi.src);
        for (int i = 0; i < mi.javaImage.length; i++)
        {
          if (mi.javaImage[i] != null)
          {
            System.out.println("Java " + mi.width[i] + "x" + mi.height[i] +
                " size=" + mi.javaMemSize[i]/1024 + "KB age=" + (Sage.eventTime() - mi.lastUsedJava[i])/1000.0 + " sec");
          }
          if (mi.rawImage[i] != null)
          {
            System.out.println("Raw " + mi.width[i] + "x" + mi.height[i] +
                " size=" + mi.rawMemSize[i]/1024 + "KB age=" + (Sage.eventTime() - mi.lastUsedRaw[i])/1000.0 + " sec");
          }
          for (int j = 0; mi.nativeAllocData != null && j < mi.nativeAllocData.length; j++)
          {
            if (mi.nativeAllocData[j].nativeImage[i] != 0)
            {
              System.out.println("Native-" + mi.nativeAllocData[j].nia.get() + " " + mi.width[i] + "x" + mi.height[i] +
                  " size=" + mi.nativeAllocData[j].nativeMemSize[i]/1024 + "KB age=" + (Sage.eventTime() - mi.nativeAllocData[j].lastUsedNative[i])/1000.0 + " sec");
            }
          }
        }
        x++;
      }
      System.out.println("<---- DONE DUMPING META IMAGE CACHE");
    }
  }

  // NOTE: Narflex - When I did the full merge of the two code branches, this function was not synchronized
  // on embedded, but was synchronized on desktop. I'm leaving it as sync'd since desktop is generally the more reliable code.
  // The problem with synchronizing here is that then we lock this MetaImage object while it's doing the URL download which
  // may have network issues...and when we walk through the map to release an image, this can block that...which is very bad.
  private boolean loadCacheFile()
  {
    File myCacheFile = null;

    try
    {
      boolean usePermCache = ENABLE_OFFLINE_URL_CACHE && src instanceof URL;
      if (usePermCache)
      {
        myCacheFile = new File(MediaFile.THUMB_FOLDER, "url-" + convertToAsciiName(src.toString()));
        if (myCacheFile.isFile() && myCacheFile.length() > 0)
        {
          myCacheFile.setLastModified(Sage.time()); // so we know which are the oldest
          localCacheFile = myCacheFile;
          return true; // already cached on disk
        }
      }
      InputStream is = null;
      if (src instanceof String)
        is = getClass().getClassLoader().getResourceAsStream((String) src);
      else
      {
        HttpURLConnection.setFollowRedirects(true);
        URL myURL = (URL) src;
        URLConnection myURLConn;
        try
        {
          while (true)
          {
            lastUrlLoadTime = Sage.eventTime();
            myURLConn = myURL.openConnection();
            myURLConn.setConnectTimeout(30000);
            myURLConn.setReadTimeout(30000);
            is = myURLConn.getInputStream();
            if (myURLConn instanceof HttpURLConnection)
            {
              HttpURLConnection httpConn = (HttpURLConnection) myURLConn;
              if (httpConn.getResponseCode() / 100 == 3)
              {
                if (Sage.DBG) System.out.println("Internally processing HTTP redirect...");
                is.close();
                myURL = new URL(httpConn.getHeaderField("Location:"));
                continue;
              }
            }
            break;
          }
        }
        catch (Exception e)
        {
          if (Sage.DBG) System.out.println("ERROR with URL \"" + src + "\" download of:" + e);
          try{
            if (is != null)
              is.close();
          }catch (Exception e3){}
          is = null;
        }
      }
      if (is != null)
      {
        File tmpCacheFile = null;
        try
        {
          if (!usePermCache)
            myCacheFile = getNewLocalCacheFile();
          else
            tmpCacheFile = File.createTempFile(TEMP_CACHE_IMAGE_PREFIX, TEMP_CACHE_IMAGE_SUFFIX, MediaFile.THUMB_FOLDER);
          FileOutputStream fos = new FileOutputStream(tmpCacheFile != null ? tmpCacheFile : myCacheFile);
          byte[] buf = new byte[16384];
          if (Sage.DBG) System.out.println("Downloading-2 URL " + src + " to local cache file: " + myCacheFile + " id=" + System.identityHashCode(this));
          int numRead = is.read(buf);
          while (numRead != -1)
          {
            fos.write(buf, 0, numRead);
            numRead = is.read(buf);
          }
          fos.close();
          // touch tracker to check the file size
          if ( localCacheFileTracker!=null )
            localCacheFileTracker.touch(this);
        }
        finally
        {
          if (usePermCache && tmpCacheFile != null)
          {
            if (!tmpCacheFile.renameTo(myCacheFile))
              tmpCacheFile.delete(); // this should only fail if something else is in the process of doing this from another thread
          }
          localCacheFile = myCacheFile;
          is.close();

          if (localCacheFileTracker != null) {
            // tell local cache tracker to update the file size
            localCacheFileTracker.checkFileSize(this);
          }
        }
      }
    }
    catch (Exception e)
    {
      if (Sage.DBG) System.out.println("Error creating cached image data from URL \"" + src + "\" of:" + e);
      deleteLocalCacheFile();
      return false;
    }

    return localCacheFile != null && localCacheFile.isFile();
  }

  private File getNewLocalCacheFile() throws IOException
  {
    if (localCacheFileTracker == null) {
      return File.createTempFile(TEMP_CACHE_IMAGE_PREFIX, TEMP_CACHE_IMAGE_SUFFIX);
    } else {
      return localCacheFileTracker.getFile(this);
    }
  }

  /**
   * delete locally cached files from disk, and from the
   * local cache file tracker if it is in use.
   *
   * @return true on success
   */
  private boolean deleteLocalCacheFile() {
    // DO NOT delete the smb:// files because they are the REAL files; not a copy
    if (localCacheFile != null && !src.toString().startsWith("smb://")) {
      if (localCacheFileTracker != null) {
        // using tracked cache - remove it from the cache.
        localCacheFileTracker.removeFile(this);
      }
      if (localCacheFile.exists()) {
        localCacheFile.delete();
      }
      localCacheFile=null;
      return true;
    }
    return false;
  }

  // Not strictly required because GC should clean up the WeakReferences, but it will make things
  // a little faster on GC by clearing this one ourselves
  public static void notifyOfDeadUIManager(UIManager uiMgr)
  {
    uimgrToAsyncLoaderMap.remove(uiMgr);
  }

  // Returns true if the source for this image is from a JPEG file. Since we only use the
  // native image loader we can tell based on the alpha. The only image format we support
  // that does NOT have alpha is JPEG. :)
  public boolean isJPEG()
  {
    return !sourceHasAlpha;
  }

  // This returns the amount in degrees that loaded images have been rotated from the original version.
  public int getRotation()
  {
    return autoRotated;
  }

  public boolean isTIFF()
  {
    // Do this based off the filename or the MediaFile object type, this really only matters for scaling
    // so while this might fail for embedded TIFF thumbnails; that should never have any real negative effect
    if (src instanceof MediaFile)
      return sage.media.format.MediaFormat.TIFF.equals(((MediaFile) src).getContainerFormat());
    if (src == null) return false;
    String str = src.toString().toLowerCase();
    return str.endsWith(".tif") || str.endsWith(".tiff");
  }

  public static sage.media.image.RawImage loadScaledImageFromFileSafely(String imgFile, int width, int height) throws IOException
  {
    return loadScaledImageFromFileSafely(imgFile, width, height, 0);
  }
  public static sage.media.image.RawImage loadScaledImageFromFileSafely(String imgFile, int width, int height, int rotation) throws IOException
  {
    // First try to load it with the native loader; if that fails, then use the Java loader and create a raw image from that
    try
    {
      return sage.media.image.ImageLoader.loadResizedRotatedImageFromFile(imgFile, width, height, 32, rotation);
    }
    catch (IOException ioe)
    {
      if (sage.Sage.DBG) System.out.println("ERROR LOADING NATIVE IMAGE, REVERTING TO JAVA LOAD for: " + imgFile);
      BufferedImage javaImage = ImageUtils.rotateImage(ImageUtils.fullyLoadImage(new File(imgFile)), rotation);
      if (javaImage != null)
      {
        if (width != 0 && height != 0 && (javaImage.getWidth() != width || javaImage.getHeight() != height))
          javaImage = sage.ImageUtils.createBestScaledImage(javaImage, width, height);
        return new sage.media.image.RawImage(javaImage);
      }
      else
        throw ioe;
    }
  }

  // Used to wrap around a MediaFile's thumbnail (MediaFile objects themselves indicate a picture file)
  public static class MediaFileThumbnail
  {
    public MediaFileThumbnail(MediaFile f)
    {
      mf = f;
    }
    @Override
    public int hashCode()
    {
      return mf.hashCode();
    }
    @Override
    public boolean equals(Object o)
    {
      return (o instanceof MediaFileThumbnail) && ((MediaFileThumbnail) o).mf == mf;
    }
    @Override
    public String toString()
    {
      return "MediaFileThumbnail[" + mf + "]";
    }
    public MediaFile mf;
  }

  public static class Waiter extends MetaImage
  {
    public Waiter(MetaImage base, Object waitObj)
    {
      super(null);
      this.base = base;
      this.waitObj = waitObj;
    }
    @Override
    public void releaseJavaImage(int idx)
    {
      base.releaseJavaImage(idx);
    }
    @Override
    public void releaseJavaImages()
    {
      base.releaseJavaImages();
    }
    @Override
    public void releaseRawImage(int idx)
    {
      base.releaseRawImage(idx);
    }
    @Override
    public void releaseRawImages()
    {
      base.releaseRawImages();
    }
    @Override
    public Image getJavaImage() { return base.getJavaImage(); }
    @Override
    public Image getJavaImage(int scaledWidth, int scaledHeight)
    {
      return base.getJavaImage(scaledWidth, scaledHeight);
    }
    @Override
    public Image getJavaImage(int imageIndex)
    {
      return base.getJavaImage(imageIndex);
    }
    @Override
    public int getImageIndex(Shape desiredShape)
    {
      return base.getImageIndex(desiredShape);
    }
    @Override
    public int getImageIndex(int desiredWidth, int desiredHeight)
    {
      return base.getImageIndex(desiredWidth, desiredHeight);
    }
    @Override
    public int getImageIndex(int desiredWidth, int desiredHeight, Shape desiredShape)
    {
      return base.getImageIndex(desiredWidth, desiredHeight, desiredShape);
    }
    @Override
    public void setNativePointer(NativeImageAllocator nia, int imageIndex, long ptr, int memSize)
    {
      base.setNativePointer(nia, imageIndex, ptr, memSize);
    }
    @Override
    public long getNativeImage(NativeImageAllocator nia) { return base.getNativeImage(nia); }
    @Override
    public long getNativeImage(NativeImageAllocator nia, int imageIndex)
    {
      return base.getNativeImage(nia, imageIndex);
    }
    @Override
    public sage.media.image.RawImage getRawImage(int imageIndex)
    {
      return base.getRawImage(imageIndex);
    }
    @Override
    public Dimension getImageSize(int imageIndex)
    {
      return base.getImageSize(imageIndex);
    }
    @Override
    public int getWidth() { return base.getWidth(); }
    @Override
    public int getWidth(int idx) { return base.getWidth(idx); }
    @Override
    public int getHeight() { return base.getHeight(); }
    @Override
    public int getHeight(int idx) { return base.getHeight(idx); }
    @Override
    public Object getSource() { return base.getSource(); }
    @Override
    public int getNumImages() { return base.getNumImages(); }
    @Override
    public String getLcSourcePathname()
    {
      return base.getLcSourcePathname();
    }
    @Override
    public void addJavaRef(int imageIndex)
    {
      base.addJavaRef(imageIndex);
    }
    @Override
    public void removeJavaRef(int imageIndex)
    {
      base.removeJavaRef(imageIndex);
    }
    @Override
    public void addRawRef(int imageIndex)
    {
      base.addRawRef(imageIndex);
    }
    @Override
    public void removeRawRef(int imageIndex)
    {
      base.removeRawRef(imageIndex);
    }
    @Override
    public void addNativeRef(NativeImageAllocator nia, int imageIndex)
    {
      base.addNativeRef(nia, imageIndex);
    }
    @Override
    public void removeNativeRef(NativeImageAllocator nia, int imageIndex)
    {
      base.removeNativeRef(nia, imageIndex);
    }
    @Override
    public String toString()
    {
      // hack so null images are equal in the Studio
      return base.toString();
    }
    @Override
    public boolean isNullOrFailed()
    {
      return base.isNullOrFailed();
    }
    @Override
    public byte[] getSourceAsBytes()
    {
      return base.getSourceAsBytes();
    }
    @Override
    public String getUniqueResourceID(int imageIndex)
    {
      return base.getUniqueResourceID(imageIndex);
    }
    @Override
    public Object getImageOption(int imageIndex)
    {
      return base.getImageOption(imageIndex);
    }
    @Override
    public boolean hasAlpha()
    {
      return base.hasAlpha();
    }

    public Object getWaitObj()
    {
      return waitObj;
    }
    @Override
    public void finalize()
    {
      // We don't want to clean anything up for waiter objects
    }
    public MetaImage getBase()
    {
      return base;
    }
    private MetaImage base;
    private Object waitObj;
  }

  private static class AsyncLoader implements Runnable
  {
    public AsyncLoader()
    {
    }
    public void loadImage(String srcURL, ResourceLoadListener loadNotifier)
    {
        synchronized (jobList)
        {
        if (taskNotifiers == null)
          taskNotifiers = new HashMap<String, ArrayList<ResourceLoadListener>>();
          if (numThreads == 0 || (jobList.size() > 0 && numThreads < NUM_ASYNC_LOADER_THREADS))
          {
          Thread t = new Thread(this, "AsyncURLImageLoader-" + numThreads);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
            numThreads++;
          }
          Object[] newJob = new Object[3];
          newJob[0] = srcURL;
          newJob[1] = loadNotifier;
        newJob[2] = new Long(Sage.eventTime());
          jobList.add(newJob);
          jobList.notifyAll();
        }
    }
    public void run()
    {
      try
      {
        while (uimgrToAsyncLoaderMap.containsValue(this))
        {
          Object[] currJob = null;
          synchronized (jobList)
          {
            long maxWait = 30000;
            for (int i = 0; i < jobList.size(); i++)
            {
              Object[] testJob =jobList.get(i);
              if (Sage.eventTime() - ((Long)testJob[2]).longValue() > URL_IMAGE_LOAD_DELAY)
              {
                currJob = testJob;
                jobList.remove(i);
                break;
              }
              else
              {
                maxWait = Math.min(maxWait, URL_IMAGE_LOAD_DELAY - (Sage.eventTime() - ((Long)testJob[2]).longValue()));
              }
            }
            if (currJob == null)
            {
              if (maxWait > 0)
              {
                try
                {
                  jobList.wait(maxWait);
                }catch (InterruptedException e){}
              }
              continue;
            }
          }
          String urlStr = (String) currJob[0];
          ResourceLoadListener loadNotifier = (ResourceLoadListener) currJob[1];
          if (loadNotifier.loadStillNeeded(urlStr))
          {
            // Check if we're in the process of loading this one still
            synchronized (taskNotifiers)
            {
              ArrayList<ResourceLoadListener> notifyList = taskNotifiers.get(urlStr);
              if (notifyList != null)
              {
                notifyList.add(loadNotifier);
                continue;
              }
              {
                // use tmpList to avoid needing warning suppression of
                // unchecked conversion at function scope
                @SuppressWarnings("unchecked")
                ArrayList<ResourceLoadListener> tmpList = Pooler.getPooledArrayList();
                notifyList = tmpList;
              }
              notifyList.add(loadNotifier);
              taskNotifiers.put(urlStr, notifyList);
            }
            // Check if we've already got it loaded from another async op that occurred before us
            boolean goodLoad;
            if (globalImageCache.containsKey(urlStr))
            {
              MetaImage mi = globalImageCache.get(urlStr);
              // Don't redownload it if we already have. It's possible for another thread
              // to get to this point after a previous one has already downloaded the image.
              if (mi.localCacheFile == null || !mi.localCacheFile.isFile())
                mi.loadCacheFile();
              goodLoad = !globalImageCache.get(urlStr).isNullOrFailed();
            }
            else
            {
              URL currURL = getURLForString(urlStr);
              MetaImage rv = new MetaImage(currURL);
              rv.initDataStructures(1);
              // We need to load this image so we can get its sizing information
              if (!rv.loadCacheFile())
              {
                rv.setRawImage(sage.media.image.ImageLoader.getNullImage(), 0, 0);
                rv.setJavaImage(ImageUtils.getNullImage(), 0, 0);
              }
              else
              {
                try
                {
                  sage.media.image.RawImage ri = sage.media.image.ImageLoader.loadImageDimensionsFromFile(rv.localCacheFile.getAbsolutePath());
                  if (ri != null)
                  {
                    rv.width[0] = ri.getWidth();
                    rv.height[0] = ri.getHeight();
                  }
                  else
                    throw new IOException("Failed loading image dimensions from:" + currURL);
                }
                catch (IOException e)
                {
                  if (Sage.DBG) System.out.println("Error accessing image file " + currURL + " of " + e);
                }
              }
              synchronized (globalImageCache)
              {
                if (globalImageCache.containsKey(urlStr))
                  rv = globalImageCache.get(urlStr);
                else
                  globalImageCache.put(urlStr, rv);
              }
              goodLoad = !rv.isNullOrFailed();
            }
            synchronized (taskNotifiers)
            {
              ArrayList<?> notifyList = taskNotifiers.get(urlStr);
              for (int i = 0; i < notifyList.size(); i++)
              {
                ((ResourceLoadListener) notifyList.get(i)).loadFinished(urlStr, goodLoad);
              }
              Pooler.returnPooledArrayList(notifyList);
              taskNotifiers.remove(urlStr);
            }
          }
        }
      }
      finally
      {
        synchronized (jobList)
        {
          numThreads--;
        }
      }
    }
    private final Vector<Object[]> jobList = new Vector<Object[]>();
    private int numThreads = 0;
    private Map<String, ArrayList<ResourceLoadListener>> taskNotifiers;
  }

  private Object src;
  private int numImages;
  private int[] width;
  private int[] height;
  private Object[] imageOption; // null if its the full rect, or a Shape or a Insets[]
  private Image[] javaImage;
  private long[] lastUsedJava;
  private int[] javaMemSize;
  private int[] javaRefCount;
  private sage.media.image.RawImage[] rawImage;
  private long[] lastUsedRaw;
  private int[] rawMemSize;
  private int[] rawRefCount;
  private NativeImageData[] nativeAllocData;
  private boolean loadFailed;
  private long lastUrlLoadTime;
  private boolean permanent;
  private boolean sourceHasAlpha = true;
  private File localCacheFile; // if we've downloaded the data
  private boolean destroyed;
  private int autoRotated; // degrees the image has been rotated from its original version

  private class NativeImageData
  {
    NativeImageData(NativeImageAllocator inNA)
    {
      nia = new WeakReference<NativeImageAllocator>(inNA);
      initDataStructures(MetaImage.this.numImages);
    }
    void initDataStructures(int num)
    {
      nativeImage = new long[num];
      lastUsedNative = new long[num];
      nativeMemSize = new int[num];
      nativeRefCount = new int[num];
    }
    void incrementDataStructures(int amount)
    {
      long[] newnativeImage = new long[numImages + amount];
      System.arraycopy(nativeImage, 0, newnativeImage, 0, nativeImage.length);
      nativeImage = newnativeImage;
      long[] newlastUsedNative = new long[numImages + amount];
      System.arraycopy(lastUsedNative, 0, newlastUsedNative, 0, lastUsedNative.length);
      lastUsedNative = newlastUsedNative;
      int[] newnativeMemSize = new int[numImages + amount];
      System.arraycopy(nativeMemSize, 0, newnativeMemSize, 0, nativeMemSize.length);
      nativeMemSize = newnativeMemSize;
      int[] newnativeRefCount = new int[numImages + amount];
      System.arraycopy(nativeRefCount, 0, newnativeRefCount, 0, nativeRefCount.length);
      nativeRefCount = newnativeRefCount;
    }
    long[] nativeImage;
    long[] lastUsedNative;
    int[] nativeMemSize;
    int[] nativeRefCount;
    WeakReference<NativeImageAllocator> nia;
  }
}
