# Change Log

## Version 9.1.2 (??)
* Fix: Changed awk parsing to use sed to clean up the Java version check.
* Fix: API methods GetFavoriteAirings() and GetPotentialFavoriteAirings() were returning all airings for keyword favorites.
* New: Increased possible range for scheduling lookahead to 21 days. The default is still 14 days.

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
    * AddGlobalContext( "STVversionText", "October 13, 2015" )
        * This should match the date of the commit.
    * AddGlobalContext( "ThisSTVSetVersionNum", "2015101301" )
        * This should match the date of the commit and if there was more than one commit the same day, the last two digits should be incremented.
        * The format is YYYYMMDDVV.
        * YYYY is the year.
        * MM if the month number.
        * DD is the day of the month.
        * VV is the commit version for this date. This resets to 01 if the date changes.
    * STVVersion [="9.0.1.1"]
        * This should start with MAJOR_VERSION.MINOR_VERSION.MICRO_VERSION in sage/Version.java
        * The last number should only be incremented in tandem with the last two digits of ThisSTVSetVersionNum.