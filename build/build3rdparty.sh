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
# Build the external codecs
cd ../third_party/codecs/faac
./bootstrap || { echo "Build failed, exiting."; exit 1; }
./configure CFLAGS=-fno-common --enable-static --disable-shared --without-mp4v2 || { echo "Build failed, exiting."; exit 1; }
make -j32 || { echo "Build failed, exiting."; exit 1; }

cd ../faad2
./bootstrap || { echo "Build failed, exiting."; exit 1; }
./configure CFLAGS=-fno-common --without-xmms --without-mpeg4ip --without-drm || { echo "Build failed, exiting."; exit 1; }
make -j32 # || { echo "Build failed, exiting."; exit 1; }

cd ../x264
./configure "--extra-cflags=-fasm -fno-common -D_FILE_OFFSET_BITS=64" --disable-avis-input --disable-mp4-output --enable-pthread || { echo "Build failed, exiting."; exit 1; }
make -j32 || { echo "Build failed, exiting."; exit 1; }

cd ../xvidcore/build/generic
./bootstrap.sh || { echo "Build failed, exiting."; exit 1; }
./configure CFLAGS=-fno-common || { echo "Build failed, exiting."; exit 1; }
make -j32 || { echo "Build failed, exiting."; exit 1; }

# Build FFMPEG
cd ../../../../ffmpeg
make clean
./configure --disable-ffserver --disable-ffplay --enable-gpl --enable-pthreads   --enable-nonfree --enable-libfaac --enable-libx264 --enable-libxvid --disable-devices   --disable-demuxer=msnwc_tcp --enable-libfaad "--extra-cflags=-I. -I`readlink -f ../codecs/faad2/include` -I`readlink -f ../codecs/faac/include` -I`readlink -f ../codecs/x264` -I`readlink -f ../codecs/xvidcore/src`" "--extra-ldflags=-L`readlink -f ../codecs/faac/libfaac/.libs` -L`readlink -f ../codecs/faad2/libfaad/.libs` -L`readlink -f ../codecs/x264` -L`readlink -f ../codecs/xvidcore/build/generic/=build`" || { echo "Build failed, exiting."; exit 1; }
make -j32 || { echo "Build failed, exiting."; exit 1; }

cd ../../build

# Build mplayer (if MPLAYER_NEW=1 is set, then the newer mplayer will be build)
./buildmplayer.sh

# Copy the files to the release folder
mkdir elf
cd elf
cp ../../third_party/ffmpeg/ffmpeg .
cp ../../third_party/codecs/jpeg-6b/jpegtran .
cd ..
