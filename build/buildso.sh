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
mkdir so
cd so
make -C ../../native/so/SageLinux || { echo "Build failed, exiting."; exit 1; }
cp ../../native/so/SageLinux/*.so .
make -C ../../native/so/IVTVCapture || { echo "Build failed, exiting."; exit 1; }
cp ../../native/so/IVTVCapture/*.so .
make -C ../../third_party/jtux/native/so || { echo "Build failed, exiting."; exit 1; }
cp ../../third_party/jtux/native/so/*.so .
make -C ../../native/so/PVR150Input || { echo "Build failed, exiting."; exit 1; }
cp ../../native/so/PVR150Input/*.so .
make -C ../../native/so/DVBCapture2.0 || { echo "Build failed, exiting."; exit 1; }
cp ../../native/so/DVBCapture2.0/*.so .
make -C ../../native/so/FirewireCapture || { echo "Build failed, exiting."; exit 1; }
cp ../../native/so/FirewireCapture/*.so .
make -C ../../native/so/MPEGParser2.0 || { echo "Build failed, exiting."; exit 1; }
cp ../../native/so/MPEGParser2.0/*.so .
make -C ../../native/so/HDHomeRun2.0 || { echo "Build failed, exiting."; exit 1; }
cp ../../native/so/HDHomeRun2.0/*.so .
make -C ../../native/dll/JavaRemuxer2 || { echo "Build failed, exiting."; exit 1; }
cp ../../native/dll/JavaRemuxer2/*.so .

mkdir irtunerplugins
cd irtunerplugins
make -C ../../../native/so/PVR150Tuning || { echo "Build failed, exiting."; exit 1; }
cp ../../../native/so/PVR150Tuning/*.so .
make -C ../../../native/so/DirecTVSerialControl || { echo "Build failed, exiting."; exit 1; }
cp ../../../native/so/DirecTVSerialControl/*.so .
make -C ../../../native/so/FirewireTuning || { echo "Build failed, exiting."; exit 1; }
cp ../../../native/so/FirewireTuning/*.so .
cd ..

# build the image library dependencies
make -C ../../third_party/swscale || { echo "Build failed, exiting."; exit 1; }
cp ../../third_party/swscale/*.so .

cd ../../third_party/codecs/giflib
./configure --with-pic || { echo "Build failed, exiting."; exit 1; }
make || { echo "Build failed, exiting."; exit 1; }

cd ../jpeg-6b
./configure CFLAGS=-fPIC || { echo "Build failed, exiting."; exit 1; }
make

cd ../libpng
./configure --with-pic || { echo "Build failed, exiting."; exit 1; }
make

cd ../tiff
./configure --with-pic || { echo "Build failed, exiting."; exit 1; }
make

cd ../../../build/so

make -C ../../third_party/SageTV-LGPL/imageload || { echo "Build failed, exiting."; exit 1; }
cp ../../third_party/SageTV-LGPL/imageload/*.so .

make -C ../../native/crosslibs/Freetype || { echo "Build failed, exiting."; exit 1; }
cp ../../native/crosslibs/Freetype/*.so .

# Build the ffmpeg config for the libMpeg2Transcoder dependency
cd ../../third_party/ffmpeg
# We need to clean it first because it may have been built/compiled
# in a different configuration previously
make clean
./configure --build-suffix=-minimal --disable-static --enable-shared \
  --disable-decoders --disable-encoders --disable-parsers --disable-filters \
  --disable-protocols --disable-muxers --disable-demuxers --disable-bsfs \
  --enable-encoder=mpeg1video --enable-encoder=mpeg2video \
  --enable-decoder=mp3 --enable-decoder=mp2 --enable-encoder=mp2 \
  --enable-muxer=mpeg1system --enable-muxer=mpeg1vcd --enable-muxer=mpeg1video \
  --enable-muxer=mpeg2dvd --enable-muxer=mpeg2svcd --enable-muxer=mpeg2video --enable-muxer=mpeg2vob \
  --enable-muxer=yuv4mpegpipe --enable-demuxer=yuv4mpegpipe \
  --enable-demuxer=mp3 --enable-demuxer=mpegps --enable-demuxer=mpegvideo \
  --enable-parser=mpegaudio \
  --enable-protocol=pipe --enable-protocol=http --enable-protocol=file --enable-protocol=stv \
  --enable-pthreads \
  --disable-ffmpeg --disable-ffserver --disable-ffplay \
  --disable-demuxer=ea || { echo "Build failed, exiting."; exit 1; }
make -j32 || { echo "Build failed, exiting."; exit 1; }
cp libavutil/libavutil-minimal.so.* ../../build/so
cp libavcodec/libavcodec-minimal.so.* ../../build/so
cp libavformat/libavformat-minimal.so.* ../../build/so
cd ../../build/so

make -C ../../native/so/Mpeg2Transcoder || { echo "Build failed, exiting."; exit 1; }
cp ../../native/so/Mpeg2Transcoder/*.so .

