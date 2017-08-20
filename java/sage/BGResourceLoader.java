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
 * The purpose of this class is to handle asynchronous background loading of image resources. Each SageRenderer will create
 * one of these when it is instantiated. Images can then be registered to be loaded asynchronously be either their corresponding
 * src object which would correspond to a MetaImage; or by a MetaImage directly. They can then optionally have a ResourceLoadListener
 * tied to that. If an RLL is specified; then it will be validated against to make sure the load is still needed and will also be notified
 * when the full loading of the image has completed into the renderer. This class also will hand out MetaImage.Waiter objects that can
 * be used temporarily as an image resource to feed an Image Widget.
 *
 * @author Narflex
 */
public class BGResourceLoader implements Runnable
{
  public static final long IMAGE_LOAD_FAIL_RETRY_TIME = 120000; // 2 minutes
  private static final boolean DEBUG_LOADER = false;
  /** Creates a new instance of BGResourceLoader */
  public BGResourceLoader(UIManager inUIMgr, SageRenderer renderEngine)
  {
    uiMgr = inUIMgr;
    if (renderEngine instanceof NativeImageAllocator)
      nia = (NativeImageAllocator) renderEngine;
    queue = new java.util.ArrayList();
  }

  public void start()
  {
    alive = true;
    Thread t = new Thread(this, "BGLoader2-" + uiMgr.getLocalUIClientName());
    t.setDaemon(true);
    t.setPriority(Thread.MIN_PRIORITY + 1);
    t.start();
    for (int i = 0; i < 3; i++)
    {
      t = new Thread(new Stage1Processor(i != 0), "BGLoader1-" + uiMgr.getLocalUIClientName());
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
    }
  }

  private class Stage1Processor implements Runnable
  {
    public Stage1Processor(boolean acceptMFs)
    {
      // We make sure one of them doesn't take MediaFiles so they all don't get clogged waiting on thumbnail generation
      this.acceptMFs = acceptMFs;
    }
    private boolean acceptMFs;
    public void run()
    {
      while (alive)
      {
        BGQueueItem processMe = null;
        synchronized (queue)
        {
          if (!alive)
            break;
          for (int i = 0; i < queue.size(); i++)
          {
            processMe = (BGQueueItem)queue.get(i);
            if (processMe.myMeta != null || processMe.stage1Active || (processMe.thumbSrc != null && !acceptMFs))
              processMe = null;
            else
            {
              processMe.stage1Active = true;
              break;
            }
          }
          if (processMe == null)
          {
            try
            {
              queue.wait();
            }
            catch (InterruptedException e){}
            continue;
          }
        }

        if (DEBUG_LOADER && Sage.DBG) System.out.println("BGLoader1 is processing the image resource for src=" + (processMe.metaSrc == null ? processMe.thumbSrc : processMe.metaSrc));
        if (!verifyLoadNeeded(processMe))
        {
          if (DEBUG_LOADER && Sage.DBG) System.out.println("BGLoader is dropping resource since its load is no longer needed for src=" + (processMe.metaSrc == null ? processMe.thumbSrc : processMe.metaSrc));
          continue;
        }
        if (DEBUG_LOADER && Sage.DBG) System.out.println("BGLoader is getting the intial MetaImage for src=" + (processMe.metaSrc == null ? processMe.thumbSrc : processMe.metaSrc));
        if (processMe.thumbSrc != null)
          processMe.myMeta = processMe.thumbSrc.getThumbnail(null, true);
        else if (processMe.metaSrc instanceof Album)
          processMe.myMeta = MetaImage.getMetaImage((Album) processMe.metaSrc);
        else if (processMe.metaSrc instanceof java.io.File)
          processMe.myMeta = MetaImage.getMetaImage((java.io.File) processMe.metaSrc);
        else if (processMe.metaSrc instanceof java.awt.Image)
          processMe.myMeta = MetaImage.getMetaImage((java.awt.Image) processMe.metaSrc);
        else if (processMe.metaSrc instanceof java.net.URL)
          processMe.myMeta = MetaImage.getMetaImage((java.net.URL) processMe.metaSrc);
        else if (processMe.metaSrc instanceof MediaFile)
          processMe.myMeta = MetaImage.getMetaImage((MediaFile) processMe.metaSrc);
        else if (processMe.metaSrc instanceof MetaImage.MediaFileThumbnail)
          processMe.myMeta = MetaImage.getMetaImage((MetaImage.MediaFileThumbnail) processMe.metaSrc);
        else if (processMe.metaSrc instanceof MetaFont)
          processMe.myMeta = MetaImage.getMetaImage((MetaFont) processMe.metaSrc);
        else if (processMe.metaSrc instanceof java.util.Vector)
          processMe.myMeta = MetaImage.getMetaImage((java.util.Vector) processMe.metaSrc);
        else
          processMe.myMeta = MetaImage.getMetaImage(processMe.metaSrc.toString(), uiMgr, null);
        processMe.stage1Active = false;
        synchronized (queue)
        {
          // Kick for stage2
          queue.notifyAll();
        }
        if (DEBUG_LOADER && Sage.DBG) System.out.println("BGLoader has the intial MetaImage for src=" + (processMe.metaSrc == null ? processMe.thumbSrc : processMe.metaSrc));
      }
    }
  }

  public void run()
  {
    while (alive)
    {
      BGQueueItem processMe = null;
      synchronized (queue)
      {
        if (!alive)
          break;
        for (int i = 0; i < queue.size(); i++)
        {
          processMe = (BGQueueItem)queue.get(i);
          if (processMe.myMeta == null)
            processMe = null;
          else
            break;
        }
        if (processMe == null)
        {
          try
          {
            queue.wait();
          }
          catch (InterruptedException e){}
          continue;
        }
      }

      if (DEBUG_LOADER && Sage.DBG) System.out.println("BGLoader2 is processing the image resource for src=" + (processMe.metaSrc == null ? processMe.thumbSrc : processMe.metaSrc));
      // Now we do the actual image decode/preload into memory; checking if its needed first of course
      if (!verifyLoadNeeded(processMe))
      {
        if (DEBUG_LOADER && Sage.DBG) System.out.println("BGLoader is dropping resource at step 2 since its load is no longer needed for src=" + (processMe.metaSrc == null ? processMe.thumbSrc : processMe.metaSrc));
        continue;
      }

      if (DEBUG_LOADER && Sage.DBG) System.out.println("BGLoader is preloading the image into the cache now for meta=" + processMe.myMeta);
      if (nia != null)
        nia.preloadImage(processMe.myMeta);
      processMe.myMeta.getJavaImage(0);
      processMe.myMeta.removeJavaRef(0);
      if (processMe.myMeta.isNullOrFailed())
        failedImageTimes.put(getFastKey(processMe.metaSrc == null ? processMe.thumbSrc : processMe.metaSrc), new Long(Sage.eventTime()));
      if (DEBUG_LOADER && Sage.DBG) System.out.println("BGLoader is DONE preloading the image into the cache now; notify the listeners for meta=" + processMe.myMeta);
      java.util.ArrayList notifyMe = null;
      synchronized (queue)
      {
        notifyMe = processMe.listeners;
        queue.remove(processMe);
      }
      // A new request won't come in at this point because the fast loading image would be returned by our main methods
      if (notifyMe != null)
      {
        java.util.HashSet alreadyNotified = Pooler.getPooledHashSet();
        for (int i = 0; i < notifyMe.size(); i++)
        {
          // Avoid notifying the same person more than once
          ResourceLoadListener listy = (ResourceLoadListener) notifyMe.get(i);
          if (listy != null && alreadyNotified.add(listy))
            listy.loadFinished((processMe.metaSrc == null ? processMe.thumbSrc : processMe.metaSrc), processMe.myMeta.isNullOrFailed());
        }
        Pooler.returnPooledHashSet(alreadyNotified);
      }
    }
  }

  public void kill()
  {
    alive = false;
    synchronized (queue)
    {
      queue.clear();
      queue.notifyAll();
    }
  }

  private boolean verifyLoadNeeded(BGQueueItem checkMe)
  {
    // NOTE: We cannot hold the queue Lock and then call loadStillNeeded because it can deadlock
    // against calls coming into MediaFile.getThumbnail. If the listener list changes while we're checking it w/out
    // the lock then do this over again.
    java.util.ArrayList orgListeners;
    boolean needLoad = false;
    synchronized (queue)
    {
      if (checkMe.listeners == null || checkMe.listeners.contains(null))
        return true;
      orgListeners = Pooler.getPooledArrayList();
    }
    while (true)
    {
      synchronized (queue)
      {
        orgListeners.addAll(checkMe.listeners);
      }
      for (int i = 0; i < orgListeners.size(); i++)
      {
        ResourceLoadListener rll = (ResourceLoadListener)orgListeners.get(i);
        if (rll == null || rll.loadStillNeeded(checkMe.thumbSrc != null ? checkMe.thumbSrc : checkMe.metaSrc))
        {
          needLoad = true;
          break;
        }
      }
      synchronized (queue)
      {
        if (needLoad || orgListeners.size() == checkMe.listeners.size())
        {
          if (!needLoad)
            queue.remove(checkMe);
          break;
        }
      }
      orgListeners.clear();
    }
    Pooler.returnPooledArrayList(orgListeners);
    return needLoad;
  }

  // This method will return very quickly and the object returned will already be loaded and ready to render. In the case
  // that this is not possible; a Waiter MetaImage object will be returned using the fallbackImage to display temporarily until
  // the full load of the image is complete. When the load is complete the RLL will be notified of it.
  public MetaImage getMetaImageFast(Object src, ResourceLoadListener inRll, MetaImage fallbackImage)
  {
    if (src == null) return MetaImage.getMetaImage((String) src);
    // First try to see if a MetaImage already exists linked to this
    MetaImage fastMeta = MetaImage.getMetaImageNoLoad(src, inRll);
    if (fastMeta != null && fastMeta.mightLoadFast(uiMgr))
      return fastMeta;

    enqueueItem(fastMeta, src, null, inRll);
    return new MetaImage.Waiter(fallbackImage != null ? fallbackImage : MetaImage.getMetaImage((String) null), src);
  }

  public MetaImage getMetaImageFast(MetaImage inMeta, ResourceLoadListener inRll, MetaImage fallbackImage)
  {
    if (inMeta == null) return MetaImage.getMetaImage((String) null);
    // First try to see if a MetaImage already exists linked to this
    if (inMeta.mightLoadFast(uiMgr))
      return inMeta;

    enqueueItem(inMeta, inMeta.getSource(), null, inRll);
    return new MetaImage.Waiter(fallbackImage != null ? fallbackImage : MetaImage.getMetaImage((String) null), inMeta.getSource());
  }

  // For generated thumbnails
  public MetaImage getMetaThumbFast(MediaFile mf, ResourceLoadListener inRll, MetaImage fallbackImage)
  {
    if (mf == null) return MetaImage.getMetaImage((String) null);
    // First try to see if a MetaImage already exists linked to this
    if (mf.isThumbnailLoaded(uiMgr))
      return mf.getThumbnail(null);

    enqueueItem(null, null, mf, inRll);
    return new MetaImage.Waiter(fallbackImage != null ? fallbackImage : MetaImage.getMetaImage((String) null), mf);
  }

  private void enqueueItem(MetaImage inMeta, Object inSrc, MediaFile inThumb, ResourceLoadListener rll)
  {
    Long lastFailTime = (Long) failedImageTimes.get(getFastKey(inThumb == null ? inSrc : inThumb));
    if (lastFailTime != null && (Sage.eventTime() - lastFailTime.longValue()) < IMAGE_LOAD_FAIL_RETRY_TIME)
      return;
    if (rll != null && !rll.needsLoadCallback(inSrc))
      rll = null;
    // First check to see if this is already in the queue; and if it is then add us as a new listener for it
    synchronized (queue)
    {
      for (int i = 0; i < queue.size(); i++)
      {
        BGQueueItem item = (BGQueueItem) queue.get(i);
        if ((inSrc != null && getFastKey(inSrc).equals(getFastKey(item.metaSrc))) ||
            (inThumb != null && inThumb.equals(item.thumbSrc)))
        {
          if (rll != null)
          {
            if (item.listeners == null)
            {
              item.listeners = new java.util.ArrayList();
              // to indicate this should be loaded even if the listeners are all no longer active
              // since it was also requested to load directly
              item.listeners.add(null);
            }
            item.listeners.add(rll);
          }
          // Item is already enqueued and if we have a listener its now setup so we can just exit
          return;
        }
      }
      // Item is not in the queue yet so we add it
      BGQueueItem newItem = new BGQueueItem();
      newItem.myMeta = inMeta;
      newItem.metaSrc = inSrc;
      newItem.thumbSrc = inThumb;
      if (rll != null)
      {
        newItem.listeners = new java.util.ArrayList();
        newItem.listeners.add(rll);
      }
      queue.add(newItem);
      queue.notifyAll();
    }
  }

  public boolean isAlive()
  {
    return alive;
  }

  // Gives a String for java.net.URL objects to avoid their DNS lookup associated with getting their hashCode
  private Object getFastKey(Object o)
  {
    if (o instanceof java.net.URL)
      return o.toString();
    else
      return o;
  }

  private class BGQueueItem
  {
    public MetaImage myMeta;
    public Object metaSrc;
    public MediaFile thumbSrc; // generated thumbnail files are special
    public java.util.ArrayList listeners;
    public boolean stage1Active;
  }

  private UIManager uiMgr;
  private NativeImageAllocator nia;
  private boolean alive;
  private java.util.ArrayList queue;
  // This map tracks the last time we attempted to load something that failed which then may not end up in the cache.
  private java.util.Map failedImageTimes = new java.util.HashMap();
}
