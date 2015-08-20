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
 * Represents an Album of music.
 */
public class AlbumAPI{
  private AlbumAPI() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Album", "GetAllMusicArtists")
    {
      /**
       * Returns all of the artists for the music files in the library
       * @return an array of the artists for the music files in the library
       *
       * @declaration public String[] GetAllMusicArtists();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getAllArtists();
      }});
    rft.put(new PredefinedJEPFunction("Album", "GetAllMusicGenres")
    {
      /**
       * Returns all of the genres for the music files in the library
       * @return an array of the genres for the music files in the library
       *
       * @declaration public String[] GetAllMusicGenres();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getAllGenres();
      }});
    rft.put(new PredefinedJEPFunction("Album", "GetAlbums")
    {
      /**
       * Returns all of the Album objects in the library. This list is derived from the music files in the library.
       * @return an array of all of the Album objects in the library
       *
       * @declaration public Album[] GetAlbums();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getAlbums();
      }});
    rft.put(new PredefinedJEPFunction("Album", "GetAllMusicForArtist", 1, new String[] {"Artist"})
    {
      /**
       * Gets all of the Airings (a 'meta' object referring to the music file in this case)
       * that have an artist that matches the passed in artist
       * @param Artist the name of the artist
       * @return an array of the Airings that correspond to music files by the specified artist
       *
       * @declaration public Airing[] GetAllMusicForArtist(String Artist);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().searchByExactArtist(getPerson(stack));
      }});
    rft.put(new PredefinedJEPFunction("Album", "GetAllMusicForGenre", 1, new String[] {"Genre"})
    {
      /**
       * Gets all of the Airings (a 'meta' object referring to the music file in this case)
       * that have a genre that matches the passed in genre
       * @param Genre the name of the genre
       * @return an array of the Airings that correspond to music files by the specified genre
       *
       * @declaration public Airing[] GetAllMusicForGenre(String Genre);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().searchByExactGenre(getString(stack));
      }});
    rft.put(new PredefinedJEPFunction("Album", "GetAlbumTracks", 1, new String[] { "Album" })
    {
      /**
       * Gets all of the Airings (a 'meta' object referring to the music file in this case)
       * that are on this Album in the library. The returned list is sorted by the track number of each song.
       * @param Album the Album object to get the tracks for
       * @return an array of Airings which are the tracks on this Album
       *
       * @declaration public Airing[] GetAlbumTracks(Album Album);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Album al = getAlbum(stack);
        return (al == null) ? Pooler.EMPTY_AIRING_ARRAY : al.getAirings();
      }});
    rft.put(new PredefinedJEPFunction("Album", "GetNumberOfTracks", 1, new String[] { "Album" })
    {
      /**
       * Returns the number of tracks that are on this Album
       * @param Album the Album object
       * @return the number of tracks that are on the specified Album
       *
       * @declaration public int GetNumberOfTracks(Album Album);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Album al = getAlbum(stack);
        return (al == null) ? new Integer(0) : new Integer(al.getAirings().length);
      }});
    rft.put(new PredefinedJEPFunction("Album", "GetAlbumArtist", 1, new String[] { "Album" })
    {
      /**
       * Returns the artist for this Album. If there's more than one artist it will return the
       * localized string for the resource "Various_Artists". This defaults to "Various".
       * @param Album the Album object
       * @return the artist for the specified Album
       *
       * @declaration public String GetAlbumArtist(Album Album);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Album al = getAlbum(stack);
        return (al == null) ? "" : al.getArtist();
      }});
    rft.put(new PredefinedJEPFunction("Album", "GetAlbumArt", 1, new String[] { "Album" })
    {
      /**
       * Returns the album art for this Album.
       * @param Album the Album object
       * @return the album art for the specified Album, this can be fed into an Image Widget to display it
       *
       * @declaration public MetaImage GetAlbumArt(Album Album);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Album al = getAlbum(stack);
        return (al == null) ? null : al.getThumbnail(stack.getUIComponent());
      }});
    rft.put(new PredefinedJEPFunction("Album", "GetAlbumName", 1, new String[] { "Album" })
    {
      /**
       * Returns the name/title for this Album
       * @param Album the Album object
       * @return the name/title of the specified Album
       *
       * @declaration public String GetAlbumName(Album Album);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Album al = getAlbum(stack);
        return (al == null) ? "" : al.getTitle();
      }});
    rft.put(new PredefinedJEPFunction("Album", "HasAlbumArt", 1, new String[] { "Album" })
    {
      /**
       * Returns true if there is album art for this Album
       * @param Album the Album object
       * @return true if there is album art for this Album, false otherwise
       *
       * @declaration public boolean HasAlbumArt(Album Album);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Album al = getAlbum(stack);
        return Boolean.valueOf(al != null && al.hasThumbnail());
      }});
    rft.put(new PredefinedJEPFunction("Album", "IsAlbumObject", 1, new String[] { "Album" })
    {
      /**
       * Returns true if the argument is an Album object
       * @param Album the Object to test
       * @return true if the passed in object is an Album object, false otherwise
       *
       * @declaration public boolean IsAlbumObject(Object Album);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof sage.vfs.MediaNode)
          o = ((sage.vfs.MediaNode) o).getDataObject();
        return Boolean.valueOf(o instanceof Album);
      }});
    rft.put(new PredefinedJEPFunction("Album", "GetAlbumGenre", 1, new String[] { "Album" })
    {
      /**
       * Returns the genre for this Album
       * @param Album the Album object
       * @return the genre for the specified Album
       *
       * @declaration public String GetAlbumGenre(Album Album);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Album al = getAlbum(stack);
        return (al == null) ? "" : al.getGenre();
      }});
    rft.put(new PredefinedJEPFunction("Album", "GetAlbumYear", 1, new String[] { "Album" })
    {
      /**
       * Returns the year this Album was recorded in
       * @param Album the Album object
       * @return the year the specified album was recorded in
       *
       * @declaration public String GetAlbumYear(Album Album);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Album al = getAlbum(stack);
        return (al == null) ? "" : al.getYear();
      }});
    /*
		rft.put(new PredefinedJEPFunction("Album", "", 1, new String[] { "Album" })
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 return ((Album) stack.pop());
			}});
     */
  }
}
