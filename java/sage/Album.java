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

public class Album
{
  public Album(Stringer inTitle, Person inArtist, Stringer inGenre, Stringer inYear)
  {
    title = inTitle;
    artist = inArtist;
    genre = inGenre;
    year = inYear;
  }

  public String getTitle() { return (title == null) ? "" : title.name; }
  public String getArtist() { return (artist == null) ? "" : artist.name; }
  public String getGenre() { return (genre == null) ? "" : genre.name; }
  public String getYear() { return (year == null) ? "" : year.name; }
  Stringer getTitleStringer() { return title; }
  Person getArtistObj() { return artist; }
  Stringer getGenreStringer() { return genre; }
  Stringer getYearStringer() { return year; }
  void setArtistObj(Person x) { artist = x; }
  private long airCacheTime;
  private Airing[] airCache;
  private java.util.ArrayList thumbLoadNotifiers;
  private Object thumbLock = new Object();
  public Airing[] getAirings()
  {
    if (airCache == null || (Wizard.getInstance().getLastModified(DBObject.MEDIA_MASK_MUSIC) > airCacheTime))
    {
      airCacheTime = Sage.time();
      airCache = Wizard.getInstance().searchByExactAlbum(this);
      java.util.Arrays.sort(airCache, trackCompare);
    }
    return airCache;
  }

  private void addThumbTimer()
  {
    Sage.addTimerTask(new java.util.TimerTask()
    {
      public void run()
      {
        java.util.ArrayList needToBeNotified = null;
        synchronized (thumbLock)
        {
          if (thumbLoadNotifiers != null)
          {
            needToBeNotified = thumbLoadNotifiers;
            thumbLoadNotifiers = null;
          }
        }
        if (needToBeNotified != null)
        {
          java.util.Set alreadyNotified = new java.util.HashSet();
          for (int i = 0; i < needToBeNotified.size(); i++)
          {
            ResourceLoadListener loadNotifier = (ResourceLoadListener) needToBeNotified.get(i);
            // Avoid notifying the same person more than once
            if (alreadyNotified.add(needToBeNotified.get(i)))
            {
              // Check to see if it still even needs it
              if (loadNotifier.loadStillNeeded(Album.this))
              {
                // Check if these will even allow rendering if we do the refresh before we waste that call
                boolean refreshNow = false;
                MetaImage myMeta = MetaImage.getMetaImageNoLoad(Album.this);
                long timeDiff = Sage.eventTime() - loadNotifier.getUIMgr().getRouter().getLastEventTime();
                if (myMeta != null && myMeta.mightLoadFast(loadNotifier.getUIMgr()))
                  refreshNow = true;
                else if (timeDiff > loadNotifier.getUIMgr().getInt("ui/inactivity_timeout_for_full_thumb_load", 1500))
                  refreshNow = true;
                if (refreshNow)
                  loadNotifier.loadFinished(Album.this, true);
                else
                {
                  synchronized (thumbLock)
                  {
                    if (thumbLoadNotifiers == null)
                    {
                      thumbLoadNotifiers = new java.util.ArrayList();
                      thumbLoadNotifiers.add(loadNotifier);
                      addThumbTimer();
                    }
                    else
                      thumbLoadNotifiers.add(loadNotifier);
                  }
                }
              }
            }
          }
        }
      }
    }, Sage.getInt("ui/inactivity_timeout_for_full_thumb_load", 1500), 0);
  }
  public MetaImage getThumbnail(ResourceLoadListener loadNotifier)
  {
    if (loadNotifier != null && loadNotifier.getUIMgr() != null)
      return loadNotifier.getUIMgr().getBGLoader().getMetaImageFast(this, loadNotifier, getGenericAlbumImage(loadNotifier));
    // This will return a MetaImage that will load quickly, and if it can't get one then it'll
    // return one with a Wait wrapper on it so the loadNotifier will be refreshed when the load completes
    if (loadNotifier == null || !loadNotifier.needsLoadCallback(this))
    {
      return MetaImage.getMetaImage(this);
    }
    MetaImage myMeta = MetaImage.getMetaImageNoLoad(this);
    synchronized (thumbLock)
    {
      if (myMeta != null && myMeta.mightLoadFast(loadNotifier.getUIMgr()))
      {
        return myMeta;
      }
      else if (Sage.eventTime() - loadNotifier.getUIMgr().getRouter().getLastEventTime() > loadNotifier.getUIMgr().getInt("ui/inactivity_timeout_for_full_thumb_load", 1500))
      {
        return MetaImage.getMetaImage(this);
      }
      if (thumbLoadNotifiers == null)
      {
        thumbLoadNotifiers = new java.util.ArrayList();
        thumbLoadNotifiers.add(loadNotifier);
        addThumbTimer();
      }
      else if (!thumbLoadNotifiers.contains(loadNotifier))
        thumbLoadNotifiers.add(loadNotifier);
      //System.out.println("Returning the WAITER meta Image and starting the timer for the callback " + this);
      return new MetaImage.Waiter(getGenericAlbumImage(loadNotifier), this);
    }
  }

  public MetaImage getGenericAlbumImage(ResourceLoadListener loadNotifier)
  {
    if (loadNotifier != null && loadNotifier.getUIMgr() != null)
      return MetaImage.getMetaImage(loadNotifier.getUIMgr().get("ui/default_music_icon", "MusicArt.png"), loadNotifier);
    else
      return MetaImage.getMetaImage((String) null);
  }

  public boolean hasThumbnail()
  {
    // First check to see if it's already loaded, then check to see if it exists if it's not loaded
    MetaImage t = MetaImage.getMetaImageNoLoad(this);
    if (t != null)
      return !t.isNullOrFailed();
    Airing[] myAirs = getAirings();
    for (int i = 0; i < myAirs.length; i++)
    {
      MediaFile mf = Wizard.getInstance().getFileForAiring(myAirs[i]);
      if (mf != null)
      {
        if (mf.hasThumbnail())
        {
          return true;
        }
      }
    }
    return false;
  }

  public String toString()
  {
    return Sage.rez("Song_By_Artist", new Object[] { (title == null ? "" : title.name),
        (artist == null ? "" : artist.name) });
  }

  /*
   * NOTE: Narflex: We're no longer creating Album objects on the fly, we're only getting them
   * out of the Wizard so we can use true object equality. Otherwise we'd screw up the hashcodes when
   * we changes the contents of the album (which can happen for the artist if we detect the various artists case)
   * The exception to this rule is when a Playlist needs to return an Album object but that Album doesn't currently
   * exist. We don't want to remove that playlist item because it would destroy what the user created (since the file loss may be temporary)
   */
  /*	public int hashCode()
	{
		return ((title!=null)?title.hashCode():0) + ((artist!=null)?artist.hashCode():0) +
			((genre!=null)?genre.hashCode():0) + ((year!=null)?year.hashCode():0);
	}
	public boolean equals(Object o)
	{
		if (o instanceof Album)
		{
			Album a = (Album) o;
			return a.title == title && a.artist == artist && a.genre == genre && a.year == year;
		}
		return false;
	}*/

  private Stringer title;
  private Person artist;
  private Stringer genre;
  private Stringer year;

  private static final java.util.Comparator trackCompare = new java.util.Comparator()
  {
    public int compare(Object o1, Object o2)
    {
      return ((Airing) o1).partsB - ((Airing) o2).partsB;
    }
  };
}
