# Change Log

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
