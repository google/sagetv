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

/*
	Build the plist info here since it's a hell of a lot easier to access the information directly

	Sample plist data:

<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>category</key>
	<string>"comedy"</string>
	<key>comment</key>
	<string>"[Show CMT=[some comment]]"</string>
	<key>description</key>
	<string>"This is some short recording I made to test with"</string>
	<key>filePath</key>
	<string>/Users/Shared/SageTV/TV/10760_2_0628_1429-0.mp4</string>
	<key>name</key>
	<string>"Manual Recording"</string>
	<key>show</key>
	<string>"Montel"</string>
	<key>video kind</key>
	<string>TV show</string>
	<key>year</key>
	<string>"2007"</string>
</dict>
</plist>
 */

/*
	plist header:
	"<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\"><plist version=\"1.0\"><dict>"

	plist entries:
	"<key>" + key string + "</key><string>" + value string + "</string>"

	plist footer:
	"</dict></plist>"
 */
public class iTunesFileExport implements FileExportPlugin, Runnable
{
  public iTunesFileExport()
  {
    currFiles = new java.util.Vector();
  }

  public void run()
  {
    synchronized (syncLock)
    {
      syncLock.notifyAll();
    }
    while (true)
    {
      String[] filenames = null;
      synchronized (syncLock)
      {
        if (!currFiles.isEmpty())
        {
          filenames = (String[]) currFiles.toArray(new String[0]);
          currFiles.clear();
          syncLock.notifyAll();
        }
        else
        {
          try{syncLock.wait();}catch(Exception e){}
        }
      }
      if (filenames != null)
      {
        int ii;
        /*				if (!closePluginNow)
				{
					synchronized (syncLock)
					{
						try
						{
							// Do this so we're not right on top of the video finishing up
							syncLock.wait(10000);
						}
						catch(InterruptedException e)
						{
							continue;
						}
					}
				}
         */
        for(ii=0; ii < filenames.length; ii++) {
          String plistValue = null;
          String extID = null;

          MediaFile theMedia = sage.Wizard.getInstance().getFileForFilePath(new java.io.File(filenames[ii]));
          if(theMedia == null) continue;

          Show theShow = theMedia.getContentAiring().getShow();
          if(theShow == null) continue;

          if(Sage.DBG) System.out.println("Exporting " + filenames[ii] + " to iTunes");

          // TODO: make this generic so it can be used on other platforms
          String plistString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\"><plist version=\"1.0\"><dict>";
          plistString += "<key>filePath</key><string>"+filenames[ii]+"</string>";

          // mark as a movie if appropriate, otherwise it's a TV show
          extID = theShow.getExternalID();
          if((extID != null) ? extID.startsWith("MV") : false)
            plistString += "<key>video kind</key><string>Movie</string>"; // no quotes
          else
            plistString += "<key>video kind</key><string>TV show</string>"; // no quotes

          // add properties here
          // omit name (iTunes will use the filename) if no episode name
          if(theShow.hasEpisodeName()) plistString += "<key>name</key><string>\""+theShow.getEpisodeName()+"\"</string>";

          // values for track properties that are strings must be quoted!
          //					plistValue = ;
          //					if((plistValue != null) ? (plistValue.length() != 0) : 0)
          //						plistString += "<key></key><string>\""+plistValue+"\"</string>";

          plistValue = theShow.getTitle();
          if((plistValue != null) ? (plistValue.length() != 0) : false)
            plistString += "<key>show</key><string>\""+plistValue+"\"</string>";

          if((extID != null) ? (extID.length() != 0) : false)
            plistString += "<key>episode ID</key><string>\""+extID+"\"</string>";

          plistValue = theShow.getDesc();
          if((plistValue != null) ? (plistValue.length() != 0) : false)
            plistString += "<key>description</key><string>\""+plistValue+"\"</string>";

          plistValue = theShow.getCategory();
          if((plistValue != null) ? (plistValue.length() != 0) : false)
            plistString += "<key>category</key><string>\""+plistValue+"\"</string>";

          plistValue = theShow.toString();
          if((plistValue != null) ? (plistValue.length() != 0) : false)
            plistString += "<key>comment</key><string>\""+plistValue+"\"</string>";

          plistValue = theShow.getYear(); // no quotes
          if((plistValue != null) ? (plistValue.length() != 0) : false)
            plistString += "<key>year</key><string>"+plistValue+"</string>";

          if(!theShow.isWatched()) plistString += "<key>unplayed</key><string>true</string>"; // no quotes

          plistString += "</dict></plist>";

          addFilesToiTunes0(plistString);
        }
        //continue;
      }
    }
  }

  public boolean openPlugin()
  {
    if(Sage.DBG) System.out.println("iTunes File Exporter loading...");
    Pooler.execute(this, "iTunesFileExport", Thread.MIN_PRIORITY);
    return true;
  }

  public void closePlugin()
  {
  }

  /*
	To get metadata for the recording:
	sage.Wizard.getInstance().getFileForFilePath(java.io.File)  (just use the first element of the array passed to the plugin)
	That'll return a MediaFile object, and you can call
	MediaFile.getContentAiring().getShow() to get the Show object. The Show object has pretty much all the metadata in it.

	Mapping metadata from Show to iTunes:
		iTunes track field - Show method/field
		name - hasEpisodeName() ? getEpisodeName() : filename
		show (text, show name) - getTitle()
		episode ID (text) - getExternalID
		description (text) - getDesc()
		category (text) - getCategory()
		comment (text) - toString() (full description as shown in SageTV)
		video kind (none/movie/music video/TV show) - none if manual recording, TV show otherwise (maybe movie if recorded movie)
		year (int) - getYear()
		unplayed (boolean) - lastWatched == 0

		getPersonInRole strings:
			artist - ARTIST_ROLE
			album artist - ALBUM_ARTIST_ROLE
			composer - COMPOSER_ROLE


		TBD:
		episode number (int)
		genre (text)
		long description (text)
		rating (int, user rating!)
		season number (int)

	track elements (need to figure out how to build):
		artworks:
			data (picture) - image data
			kind (int) - purpose of this artwork (need value for thumbnail)
   */

  public void filesDoneRecording(java.io.File[] f, byte acquisitionType)
  {
    System.out.println("iTunesFileExport: filesDoneRecording "+acquisitionType+"("+f+") ");

    if (acquisitionType == ACQUISITION_FAVORITE || acquisitionType == ACQUISITION_MANUAL)
    {
      String[] strs = new String[f.length];
      for (int i = 0; i < strs.length; i++)
        strs[i] = f[i].getAbsolutePath();
      synchronized (syncLock)
      {
        currFiles.addAll(java.util.Arrays.asList(strs));
        syncLock.notifyAll();
      }
    }
  }
  private native void addFilesToiTunes0(String plistString);

  private Object syncLock = new Object();
  private java.util.Vector currFiles;
}
