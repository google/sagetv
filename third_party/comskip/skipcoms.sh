#!/bin/bash
# Script to run comskip against hdpvr recordings. Requires comskip.exe and wine.

# location where recordings are stored
recordings_location=/recordings/
# recordings not to be comskipped
recordings_to_ignore="thomas|dinosaur|mcstuffin|mickey|curious|superwhy|sesame"

# This lock file ensure there is only one instance of comskip running. It is cleaned up daily.
find /var/tmp/ -maxdepth 1 -name .comskip.lock -mtime +1 -exec rm -f {} \;
if [ -e /var/tmp/.comskip.lock ] ; then
	exit 0
fi
touch /var/tmp/.comskip.lock

export PATH=/usr/bin:$PATH
mkdir -p /var/log/comskip
echo "`date '+%H:%M'` $0 starting ..." >> /var/log/comskip/comskip_`date '+%Y%m%d'`.log 2>&1

# this loop processes every recording in the recordings dir (files with a .ts extension from hdpvr)
for file in `find $recordings_location -mmin +2 -name "*.ts" -o -name "*.mpg" | egrep -iv "$recordings_to_ignore" `; do

	echo "`date '+%H:%M'` checking $file..." >> /var/log/comskip/comskip_`date '+%Y%m%d'`.log 2>&1
	processed=`echo $file | sed 's/ts$/edl/g'`
	
	# if an .edl file is missing
	if [ ! -e "$processed" ]; then
		echo "`date '+%H:%M'` $file has not yet been processed..." >> /var/log/comskip/comskip_`date '+%Y%m%d'`.log 2>&1
		size1=`stat -c %s $file`
		sleep 5
		size2=`stat -c %s $file`
		
		# the recording is finished (file size isnt changing)
		if [ "$size1" == "$size2" ]; then
			echo "`date '+%H:%M'` processing $file..." >> /var/log/comskip/comskip_`date '+%Y%m%d'`.log 2>&1
			echo "`date '+%H:%M'` RUNNING WINE" >> /var/log/comskip/comskip_`date '+%Y%m%d'`.log 2>&1
			/usr/bin/nice -n 19 /usr/bin/wine /usr/local/bin/comskip.exe -q -v 0 -t $file >> /var/log/comskip/comskip_`date '+%Y%m%d'`.log 2>&1
			touch $processed
			rm -f $recordings_location/*txt
			rm -f $recordings_location/*log
			echo "`date '+%H:%M'` done processing $file" >> /var/log/comskip/comskip_`date '+%Y%m%d'`.log 2>&1
		else
			echo "`date '+%H:%M'` $file is still recording" >> /var/log/comskip/comskip_`date '+%Y%m%d'`.log 2>&1
			
		fi
	else
		echo "`date '+%H:%M'` $file has already been processed" >> /var/log/comskip/comskip_`date '+%Y%m%d'`.log 2>&1
	fi
done

find /var/log/comskip/ -type f -name "comskip_*" -mtime +14 -exec rm -f {} \;
echo "`date '+%H:%M'` $0 finished" >> /var/log/comskip/comskip_`date '+%Y%m%d'`.log 2>&1

rm -f /var/tmp/.comskip.lock
