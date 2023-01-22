#!/bin/sh
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

# Setting MPLAYER_NEW=1 when calling this script 
# will cause the NEWER mplayer to be built
# By default the older player is built
MPLAYER_NEW="${MPLAYER_NEW:=0}"

# ensure the elf directory is created so that we can copy mplayer
mkdir elf

# checkout the latest fork of the mplayer
if [ "$MPLAYER_NEW" = "1" ] ; then
	mkdir tmp/
	cd tmp/
	rm -rf mplayer
	git clone -b sagetv https://github.com/OpenSageTV/mplayer.git
	cd mplayer
	echo | ./configure-sagetv
	make -j32 || { echo "Build failed, exiting."; exit 1; }
	echo "Built NEW mplayer"
	cp -v mplayer ../../elf/mplayer
	cd ../../
else
	# use legacy mplayer build
	cd ../third_party/mplayer/
	LDFLAGS="-no-pie" ./configure --host-cc=gcc --disable-gcc-check --enable-runtime-cpudetection --disable-mencoder --disable-gl --enable-directx --enable-largefiles --disable-langinfo --disable-tv --disable-dvdread --disable-dvdread-internal --disable-menu --disable-libdvdcss-internal --enable-pthreads --disable-debug --disable-freetype --disable-fontconfig --enable-stv --enable-stream-sagetv --disable-ivtv --disable-x264 --extra-libs=-lpthread --disable-png || { echo "Build failed, exiting."; exit 1; }
	make -j32 || { echo "Build failed, exiting."; exit 1; }
  echo "Built OLD mplayer"
	cp -v mplayer ../../build/elf
	cd ../../build
fi
