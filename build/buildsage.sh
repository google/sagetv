#!/bin/bash
#
# Copyright 2015 The SageTV Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
echo "Bulding Sage.jar..."
rm -rf TempClassFiles
mkdir TempClassFiles
rm -rf TempBuildImages
mkdir TempBuildImages
cd TempBuildImages
rm -rf images
mkdir images
cd images
cp ../../../images/SageTV/images/*.gif .
cp ../../../images/SageTV/images/*.png .
mkdir studio
cd studio
cp ../../../../images/SageTV/images/studio/*.gif .
cd ..
cd ..
cd ..
rm -rf release
mkdir release
SAGETV_JARS=../third_party/UPnPLib/sbbi-upnplib-1.0.3.jar:../third_party/JOGL/Linux/jogl.all.jar:../third_party/Oracle/vecmath.jar:../third_party/Lucene/lucene-core-3.6.0.jar:../third_party/JCIFS/jcifs-1.1.6.jar
javac -cp $SAGETV_JARS -source 1.5 -target 1.5 -deprecation -O -sourcepath ../java:../third_party/Javolution/java:../third_party/jcraft/java:../third_party/jtux/java:../third_party/MetadataExtractor/java:../third_party/Ogle/java:../third_party/RSSLIB4J/java:../third_party/SingularSys/java -d TempClassFiles ../java/sage/SageTV.java ../java/sage/Sage.java ../java/sage/PVR350OSDRenderingPlugin.java ../java/sage/MiniPlayer.java ../java/sage/WindowsServiceControl.java ../java/tv/sage/weather/WeatherDotCom.java ../third_party/Ogle/java/sage/dvd/*.java ../third_party/RSSLIB4J/java/sage/media/rss/*.java ../java/sage/StudioFrame.java ../third_party/jcraft/java/com/jcraft/jzlib/*.java ../third_party/jtux/java/jtux/*.java ../java/tv/sage/weather/WeatherUnderground.java ../java/sage/upnp/PlaceshifterNATManager.java
if [[ $? -gt 0 ]]
then
   echo "Error compiling source code"
   exit 1
fi
jar -cf0 release/Sage.jar -C TempClassFiles sage
jar -uf0 release/Sage.jar -C TempClassFiles tv
jar -uf0 release/Sage.jar -C TempClassFiles jtux
jar -uf0 release/Sage.jar -C TempClassFiles com
jar -uf0 release/Sage.jar -C TempBuildImages images
cd ../i18n
jar -uf0 ../build/release/Sage.jar *.properties
cd ../build
rm -rf TempClassFiles
rm -rf TempBuildImages
echo "Done building Sage.jar, file is in release/Sage.jar"
exit 0