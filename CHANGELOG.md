# Change Log

* HD-PVR2 video capture device: add ability to select multiple audio inputs (Windows)
* HD PVR 60 video capture device: new device support (Windows)

## Version 9.1.7 (2017-09-24)
* Fix: add support for 2nd tuner of Hauppauge WinTV-dualHD usb tuner stick (Windows).
* Changes in the STV set 2017081201 for the next SageTV release v9.1.7.0:
    * malore menus: Removed random misc adjectives after show titles; only display misc textafter the title if it is a star rating.
	* Removed Zap2it logo from System Information.
	* EPG Lineup configuration: Changed help text above option buttons, put Schedules Direct option at top of list, old built-in EPG option renamed as plugin option and moved down.
	* Fixed Music by Artist filtering issue resulting in 0 songs per artist after entering 2nd and subsequent chars.
	* Disabled access to YouTube, Google videos, and channels.com.
	* Detailed Setup -> General: reworded the Sync System Clock option.
	* Detailed Setup -> Advanced: removed Debug Logging enable/disable option because it is always enabled now.
	* Configuration Wizard playback testing/configuration menu uses the "Default" decoder settings instead of SageTV MPEG decoders.
	* Detailed Setup -> Customize: renamed extra option to mark channels in guide with non-Zap2it channel IDs to refer to non-Tribune IDs.
	* Changed Zap2it text to Tribune elsewhere in the STV, since the EPG data fo the old built-in and new SD EPG data both ultimately come from Tribune.  
* Fix: resolved 'Grey-scale channel logos are green and half-width' for Windows releases (was fixed for linux in 9.0.8.423 and newer)

## Version 9.1.6 (2017-08-10)
* Fix: Various fixes and cleanup on Linux Firewire and DVB.
* Fix: Added support for all 4 tuners on the Hauppauge WinTV-quadHD tuner in Windows.
* New: Add Schedules Direct lineup by ID.
* Change: Removed ZZZ from Schedules Direct Regions because it doesn't do anything.
* Fix: VOB and MP4 subtitles locking methods were not being called.
* Fix: Fixes to HDHomeRun (and probably others) ATSC Scanning returning blank and garbled channels.
* Fix: Reduced Schedules Direct person image import threads to 4 (including the execution thread) and added logging for when new threads are created for during the process.
* Change: Removed unhelpful alias to original person log entries.
* Fix: Fixed issue with Schedules Direct forcing a full airing re-import on stations that do not have a No Data airing.
* Change: Lowered the priority of the Schedules Direct person image import threads.
* Fix: Removed use of G1GC in Windows due to possible memory leak issues. 

## Version 9.1.5 (2017-06-19)
* Fix: Carny throws a null pointer exception if a show has a null title.

## Version 9.1.4 (2017-06-11)
* Fix: Schedules Direct deleted lineups were not removed from accounts correctly.
* Fix: When checking for existing lineups and a deleted lineup exists, a null pointer exception was thrown.
* Change: The SRT subtitle monitoring thread now uses Pooler.
* Fix: Index out of bounds exception while getting recommendations from Schedules Direct.

## Version 9.1.3 (2017-05-30)
* Fix: A missing space in an if test causes the Linux start script to fail.

## Version 9.1.2 (2017-05-30)
* Fix: Changed awk parsing to use sed to clean up the Java version check.
* Fix: API methods GetFavoriteAirings() and GetPotentialFavoriteAirings() were returning all airings for keyword favorites.
* New: Increased possible range for scheduling lookahead to 21 days. The default is still 14 days.
* Fix: Removed check in Scheduler that was preventing a future airing beyond lookahead from being considered to resolve a conflict.
* Fix: Fixed Carny not being marked prepped on startup when no agents exist.

## Version 9.1.1 (2017-05-22)
* Fix: Fixed a problem with awk parsing in Ubuntu

## Version 9.1.0 (2017-05-22)
* Fix: Transcoder crashing on Linux with signal 11.
* New: Added new API method to get enabled and disabled favorites.
    * public Airing[] GetPotentialFavoriteAirings(Favorite Favorite);
* Fix: Aliases without a non-alias would cause an NPE when searching.
* Fix: Schedules Direct aliasing logic was applied backwards.
* New: Carny is now multi-threaded and highly optimized.
* New: Schedules Direct movie length is now imported.
* New: Schedules Direct alternative channel logos can now be used by changing the property sdepg_core/use_alternate_logos=false to true.
    * This can also be changed in the UI via Setup > Detailed Setup > Customize > Use Alternative Schedules Direct Channel Logos.
* New: Enabled G1GC String deduplication for Java versions 8 and 9.

## Version 9.0.14 (2017-03-18)
* New: Added new API methods for in progress sports tracking using Schedules Direct.
  * public boolean IsSDEPGServiceAvailable();
  * public boolean[] IsSDEPGInProgressSport(String[] ExternalIDs);
  * public int[] GetSDEPGInProgressSportStatus(String[] ExternalIDs);
* New: Added editorials based on recommendations from Schedules Direct.
* Fix: Radio stations in Schedules Direct guide data now retain their prepended zeros in the guide data.
* Fix: Teams from Schedules Direct were being skipped because they do not have a person ID. 

## Version 9.0.13 (2017-01-19)
* Fix: Schedules Direct was unable to distinguish between two lineups with the exact same name.
* Fix: Added handling for an unknown regular expression Schedules Direct was providing for the postal code for a few countries. The code also now skips the check if it does not recognize the regex formatting.
* Fix: Added better handling to Seeker when starting a recording and no directories are selectable for the desired encoder.
* Force debug logging to always be on.
* Fix: Watched calculation for movies with commercials is improved
* Fix: Prevent freezing between programs when playing back on Windows (matches V7 behavior, although not ideal, avoids freezing)
* New: Added more roles for Person objects.
* New: Schedules Direct Person images are now imported.
* New: Schedules Direct movie quality ratings are now a part of the bonus data.
* Fix: Schedules Direct movie images are now prioritized to use box art first.
* Fix: Schedules Direct now updates channels with No Data with previously saved hashes that happen to still be valid.
* Fix: Startup now explicitly adds lucene-core-3.6.0.jar before loading the JARs folder to address a common upgrade issue.

## Version 9.0.12 (2016-12-22)
* New: Schedules Direct now includes teams as people for favorite scheduling.
* New: SageTV server will no longer allow the server to go to sleep until video conversions are complete.
* New: Updated DVB-S & DVB-T frequencies for New Zealand
* New: Add STV support for enabling and disabling favorites
* Fix: Schedules Direct was not returning the saved country in some cases.
* Fix: Removed asterisks from password field when entering the password for Schedules Direct.
* Fix: Fixed so that Ministry will not allow sleep while converting.
* Fix: Allow mounting DVD iso images as non-root
* Linux Placeshifter: Added AC3 support

## Version 9.0.11 (2016-11-20)
* Fix: Enable streams with valid PAT packets and invalid PMT packets to be able to be detected by the built in remuxer.
* Fix: Linux tries a few more adapters when trying to get the primary server IP address.

## Version 9.0.9 (2016-10-10)
* Fix: GetSeriesID wasn't always returning a valid series ID
* New: Added logic to Schedules Direct program categories to ensure Movie is the first category for programs that start with MV
* Fix: Cleaned up the logic for determining when images from Schedules Direct should be in a Show or SeriesInfo object
* Fix: Clarified in logging when we can't process anything currently because Schedules Direct is offline
* Fix: Added random timeout when Schedules Direct token expires before getting a new token in case there are multiple SageTV servers using the same account

## Version 9.0.8.429 (2016-09-27)
* Fix: Fixed plugin bug that caused some upgraded plugins to be in a corrupted state
* Fix: Fixed bug in the EPG license detection logic

## Version 9.0.8 (2016-09-22)
* New: Added Schedules Direct EPG support as a core BETA feature

## Version 9.0.7 (2016-08-10)
* New: Added SageTVPluginsDev.d directory support (See [SageTVPluginsDev README](SageTVPluginsDev.md))
* New: Added direct JAR linking in SageTV Plugin Manifest (ie, no need to repackage library plugins as .zip files)


#### Notes about incrementing versions for developers:

* If you are the first to commit changes after a release, ensure that the following have been incremented beyond the last release:
    * MICRO_VERSION in sage/Version.java
* If you make any changes to stvs/SageTV7/SageTV7.xml, ensure that the following are updated in the STV:
    * AddGlobalContext( "STVversionText", "August 12, 2017" )
        * This should match the date of the commit.
    * AddGlobalContext( "ThisSTVSetVersionNum", "2017081201" )
        * This should match the date of the commit and if there was more than one commit the same day, the last two digits should be incremented.
        * The format is YYYYMMDDVV.
        * YYYY is the year.
        * MM if the month number.
        * DD is the day of the month.
        * VV is the commit version for this date. This resets to 01 if the date changes.
    * STVVersion [="9.1.7.0"]
        * This should start with MAJOR_VERSION.MINOR_VERSION.MICRO_VERSION in sage/Version.java
        * The last number should be incremented for each update of the STV for the MAJOR_VERSION.MINOR_VERSION.MICRO_VERSION SageTV release, starting with 0 for the first STV version of a new release.
