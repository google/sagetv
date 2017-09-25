#!/bin/sh

cd ../buildwin
mkdir dll
cd dll

# build the image library dependencies
cd ../../third_party/codecs/giflib
./configure --with-pic || { echo "Build failed, exiting."; exit 1; }
make  # Building utils has an issue.  Skip error check.  || { echo "Build failed, exiting."; exit 1; }

cd ../jpeg-6b
./configure CFLAGS=-fPIC || { echo "Build failed, exiting."; exit 1; }
make

cd ../libpng
./configure --with-pic || { echo "Build failed, exiting."; exit 1; }
make

cd ../tiff
./configure --with-pic || { echo "Build failed, exiting."; exit 1; }
make

cd ../../../buildwin/dll

make -C ../../third_party/SageTV-LGPL/imageload -f Makefile.win || { echo "Build failed, exiting."; exit 1; }
cp ../../third_party/SageTV-LGPL/imageload/*.dll .