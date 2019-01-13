#!/bin/sh

mkdir -p Win32
mkdir -p x64

JOBS="-j32"

# build the image library dependencies
cd ../third_party/codecs/giflib
./autogen.sh --with-pic || { echo "Build failed, exiting."; exit 1; }
make clean
make $JOBS || { echo "Build failed, exiting."; exit 1; }

cd ../jpeg-6b
./configure CFLAGS=-fPIC || { echo "Build failed, exiting."; exit 1; }
make clean
make $JOBS || { echo "Build failed, exiting."; exit 1; }

cd ../libpng
./configure --with-pic || { echo "Build failed, exiting."; exit 1; }
make clean
make $JOBS || { echo "Build failed, exiting."; exit 1; }

cd ../tiff
./configure --with-pic || { echo "Build failed, exiting."; exit 1; }
make clean
make $JOBS || { echo "Build failed, exiting."; exit 1; }


# build the PushReader dependencies
# Build the external codecs
cd ../faac
./bootstrap || { echo "Build failed, exiting."; exit 1; }
./configure CFLAGS=-fno-common --enable-static --disable-shared --without-mp4v2 || { echo "Build faac failed, exiting."; exit 1; }
make clean
make $JOBS || { echo "Build faac failed, exiting."; exit 1; }

cd ../faad2
./bootstrap || { echo "Build failed, exiting."; exit 1; }
./configure CFLAGS=-fno-common --without-xmms --without-mpeg4ip --without-drm || { echo "Build faad2 failed, exiting."; exit 1; }
make clean
make $JOBS # || { echo "Build faad2 failed, exiting."; exit 1; }

cd ../x264
# GCC 4.6 does not have -fno-aggressive-loop-optimizations.
# GCC 4.8 built libx264 crashes without it.
OPT_LIBX264_CFLAG=
GCC_VERSION=`(echo 4.6; ${CC:-gcc} -dumpversion) | sort -V | tail -n 1`
if [ "$GCC_VERSION" != "4.6" ]; then
  OPT_LIBX264_CFLAG="-fno-aggressive-loop-optimizations"
fi
./configure "--extra-cflags=-fno-common -D_FILE_OFFSET_BITS=64 $OPT_LIBX264_CFLAG" || { echo "Build x264 failed, exiting."; exit 1; }
make clean
make $JOBS || { echo "Build x264 failed, exiting."; exit 1; }

cd ../xvidcore/build/generic
./bootstrap.sh || { echo "Build failed, exiting."; exit 1; }
./configure CFLAGS=-fno-common || { echo "Build xvidcore failed, exiting."; exit 1; }
make clean
make $JOBS || { echo "Build xvidcore failed, exiting."; exit 1; }

cd ../../../../ffmpeg
./configure --enable-memalign-hack --disable-ffserver --disable-ffplay --enable-gpl \
  --enable-pthreads --enable-nonfree --enable-libfaac --enable-libx264 --enable-libxvid --enable-static --disable-shared --disable-devices --disable-bzlib \
  --disable-demuxer=msnwc_tcp --enable-libfaad "--extra-cflags=-I. -I`readlink -f ../codecs/faac/include` \
  -I`readlink -f ../codecs/faad2/include` -I`readlink -f ../codecs/x264` -I`readlink -f ../codecs/xvidcore/src`" \
  "--extra-ldflags=-L`readlink -f ../codecs/faac/libfaac/.libs` -L`readlink -f ../codecs/faad2/libfaad/.libs` \
  -L`readlink -f ../codecs/x264` -L`readlink -f ../codecs/xvidcore/build/generic/=build`" || { echo "Build ffmpeg libs failed, exiting."; exit 1; }
make clean
make $JOBS || { echo "Build failed, exiting."; exit 1; }

case `uname` in
  MINGW32*) cd ../../buildwin/Win32 ;;
  MINGW64*) cd ../../buildwin/x64   ;;
esac

rm *.dll

make -C ../../third_party/SageTV-LGPL/Pushreader clean
make -C ../../third_party/SageTV-LGPL/Pushreader || { echo "Build Pushreader failed, exiting."; exit 1; }
cp ../../third_party/SageTV-LGPL/Pushreader/*.dll .


# Build the ffmpeg config for the Mpeg2Transcoder dependencies
cd ../../third_party/ffmpeg
./configure --enable-memalign-hack --build-suffix=-minimal --enable-static --enable-shared \
  --disable-decoders --disable-encoders --disable-parsers --disable-filters \
  --disable-protocols --disable-muxers --disable-demuxers --disable-bsfs --disable-hwaccels \
  --enable-encoder=mpeg1video --enable-encoder=mpeg2video \
  --enable-decoder=mp3 --enable-decoder=mp2 --enable-encoder=mp2 \
  --enable-muxer=mpeg1system --enable-muxer=mpeg1vcd --enable-muxer=mpeg1video \
  --enable-muxer=mpeg2dvd --enable-muxer=mpeg2svcd --enable-muxer=mpeg2video --enable-muxer=mpeg2vob \
  --enable-muxer=yuv4mpegpipe --enable-demuxer=yuv4mpegpipe \
  --enable-demuxer=mp3 --enable-demuxer=mpegps --enable-demuxer=mpegvideo \
  --enable-parser=mpegaudio \
  --enable-protocol=pipe --enable-protocol=http --enable-protocol=file --enable-protocol=stv \
  --enable-pthreads --enable-memalign-hack \
  --disable-ffmpeg --disable-ffserver --disable-ffplay \
  --disable-demuxer=ea || { echo "Build failed, exiting."; exit 1; }
make clean
make $JOBS || { echo "Build failed, exiting."; exit 1; }

case `uname` in
  MINGW32*) cd ../../buildwin/Win32 ;;
  MINGW64*) cd ../../buildwin/x64 ;;
esac

make -C ../../third_party/SageTV-LGPL/imageload -f Makefile.win clean
make -C ../../third_party/SageTV-LGPL/imageload -f Makefile.win || { echo "Build imageloader failed, exiting."; exit 1; }
cp ../../third_party/SageTV-LGPL/imageload/*.dll .

make -C ../../native/so/Mpeg2Transcoder -f Makefile.win clean
make -C ../../native/so/Mpeg2Transcoder -f Makefile.win || { echo "Build failed, exiting."; exit 1; }
cp ../../native/so/Mpeg2Transcoder/*.dll .

make -C ../../native/crosslibs/Freetype -f Makefile.win clean
make -C ../../native/crosslibs/Freetype -f Makefile.win || { echo "Build failed, exiting."; exit 1; }
cp ../../native/crosslibs/Freetype/*.dll .
