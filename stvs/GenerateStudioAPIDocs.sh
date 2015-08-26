#!/bin/bash
javac ../java/tv/sage/util/StudioAPIProcessor.java
java -cp ../java/ tv.sage.util.StudioAPIProcessor ../java/sage/api/ ../java/tempdocs/sage/api
rm ../java/tv/sage/util/StudioAPIProcessor*.class
cd ../java/tempdocs
# For some reason the javadocs generated with jdk7/8 from Googles builds
# end up with no formatting at all...but the one from jdk6 works, so force
# that for Narflex since he'll usually be building this. :)
if [ -e /usr/local/buildtools/java/jdk6/bin/javadoc ] ; then
  JAVADOC="/usr/local/buildtools/java/jdk6/bin/javadoc"
else
  JAVADOC="javadoc"
fi
# We are two levels of the base now
$JAVADOC -classpath ../../third_party/Oracle/vecmath.jar:../../third_party/Lucene/lucene-core-3.6.0.jar:../../third_party/JCIFS/jcifs-1.1.6.jar -package -windowtitle "SageTV V9.0 API Specification" -doctitle 'SageTV<sup><font size="-2">TM</font></sup> V9.0 API Specification' -header '<b>SageTV Platform </b><br><font size="-1">V9.0</font>' -bottom '<font size="-1">SageTV is a trademark or registered trademark of Google, Inc. in the US and other countries.<br>Copyright 2000-2015 The SageTV Authors. All Rights Reserved.</font>' -d ../../stvs/doc -sourcepath .:../../java:../../third_party/RSSLIB4J/java:../../third_party/Javolution/java:../../third_party/Ogle/java:../../third_party/jtux/java:../../third_party/MetadataExtractor/java:../../third_party/SingularSys/java:../../third_party/jcraft/java sage.api sage.media.rss
rm -rf ../../java/tempdocs
