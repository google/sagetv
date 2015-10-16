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
rm -rf so
rm -rf release
rm -rf minirelease
rm -rf elf
rm -rf serverrelease
rm -rf ubuntuserver
rm -rf ubuntuclient
rm -rf clientrelease
rm -rf tmp
rm sagetv*.deb
rm sagetv*.gz
rm SageJar-*.zip

make -C ../native/so/SageLinux clean
make -C ../native/so/IVTVCapture clean
make -C ../third_party/jtux/native/so clean
make -C ../native/so/PVR150Input clean
make -C ../native/so/DVBCapture2.0 clean
make -C ../native/so/FirewireCapture clean
make -C ../native/so/MPEGParser2.0 clean
make -C ../native/so/HDHomeRun2.0 clean
make -C ../native/ax/Native2.0/NativeCore clean
make -C ../native/ax/Channel-2 clean
make -C ../native/ax/TSnative clean

make -C ../native/so/PVR150Tuning clean
make -C ../native/so/DirecTVSerialControl clean
make -C ../native/so/FirewireTuning clean

make -C ../third_party/swscale clean

make -C ../third_party/codecs/giflib distclean
make -C ../third_party/codecs/jpeg-6b distclean
make -C ../third_party/codecs/libpng distclean
make -C ../third_party/codecs/tiff distclean

make -C ../third_party/SageTV-LGPL/imageload clean
make -C ../native/crosslibs/Freetype clean

# faac and faad2 don't clean up after themselves very well
make -C ../third_party/codecs/faac maintainer-clean
  find ../third_party/codecs/faac -name "Makefile.in"  -exec rm {} \;
  for i in aclocal.m4 compile config.guess config.h.in config.sub configure depcomp install-sh ltmain.sh missing; do rm ../third_party/codecs/faac/$i; done
make -C ../third_party/codecs/faad2 maintainer-clean
  find ../third_party/codecs/faad2 -name "Makefile.in"  -exec rm {} \;
  for i in aclocal.m4 compile config.guess config.h.in config.sub configure depcomp INSTALL install-sh ltmain.sh missing; do rm ../third_party/codecs/faad2/$i; done
make -C ../third_party/codecs/xvidcore/build/generic mrproper
make -C ../third_party/codecs/x264 distclean

make -C ../third_party/ffmpeg distclean
make -C ../third_party/mplayer distclean

make -C ../native/so/Mpeg2Transcoder clean
