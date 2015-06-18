This document describes how to configure MPlayer & FFMPEG to be built with SageTV:

1/19/06 - We've decided to include all the codecs in the build and we'll figure out what royalties are owed for that on the business side

1. Full build for Linux

./configure --disable-mencoder --enable-eavios --enable-stream-sagetv --disable-gif --disable-lirc --disable-lircc

Build without too much dependencies
./configure --disable-mencoder --enable-stream-sagetv --disable-iconv --disable-langinfo --disable-lirc --disable-lircc --disable-joystick --disable-vm --disable-xf86keysym --disable-tv --disable-tv-v4l2 --disable-tv-bsdbt848 --disable-live --disable-dvdread --disable-cdparanoia --disable-freetype --disable-fontconfig --disable-unrarlib --disable-fribidi --disable-enca --disable-gif --disable-png --disable-png --disable-libcdio --disable-liblzo --disable-qtx --disable-xanim --disable-real --disable-xvid --disable-x264 --disable-speex --disable-theora --disable-ladspa --disable-libdv --disable-mad --disable-libmpeg2 --disable-musepack --disable-gl --disable-dga --disable-vesa --disable-svga --disable-sdl --disable-aa --disable-caca --disable-ggi --disable-ggiwmh --disable-directx --disable-dxr2 --disable-dxr3 --disable-dvb --disable-dvbhead --disable-mga --disable-xmga --disable-xinerama --disable-fbdev --disable-mlib --disable-3dfx --disable-tdfxfb --disable-directfb --disable-zr --disable-bl --disable-tdfxvid --disable-tga --disable-pnm --disable-md5sum --disable-ossaudio --disable-arts --disable-esd --disable-jack --disable-openal --disable-amr_nb --disable-amr_nb-fixed --disable-amr_wb --disable-jpeg --enable-stv --enable-largefiles --enable-runtime-cpudetection --disable-vidix-internal --disable-libdvdcss-internal --disable-win32dll --disable-ivtv

2. Full build for Windows

OLD - ./configure --disable-gl --enable-runtime-cpudetection --with-codecsdir=codecs --enable-static --with-livelibdir=/home/Narflex/live --enable-mingw-64bit-file-offset --disable-mencoder --enable-stream-sagetv
./configure --disable-vidix-internal --disable-vidix-external --disable-mencoder --disable-gl --enable-runtime-cpudetection --enable-static --enable-mingw-64bit-file-offset --enable-stream-sagetv --enable-stv --enable-largefiles --disable-langinfo --disable-tv --disable-dvdread --disable-dvdread-internal --disable-freetype --disable-fontconfig --disable-unrarlib --disable-menu --disable-libdvdcss-internal

3. FFMPEG build for Linux

../ffmpeg/configure --enable-liba52 --enable-libfaad --enable-gpl --enable-libmp3lame --enable-amr_wb --enable-amr_nb --enable-libfaac --enable-x264 --enable-xvid --disable-vhook

4. FFMPEG shared library build for Windows

./configure --disable-debug --disable-static --enable-shared --enable-memalign-hack --extra-libs=-lwsock32 --disable-demuxer=GXF --disable-muxer=GXF

5. FFMPEG transcoder executable build for Windows

w/out AMR - ./configure --extra-libs=-lwsock32 --enable-faad --enable-x264 --disable-debug --enable-memalign-hack --enable-a52 --enable-gpl --enable-xvid --enable-faac --enable-mp3lame --disable-demuxer=ea
./configure --extra-libs=-lwsock32 --enable-faad --enable-x264 --enable-memalign-hack --enable-liba52 --enable-gpl --enable-xvid --enable-faac --enable-mp3lame --disable-debug --enable-amr_wb --enable-amr_nb --disable-demuxer=ea

NOTE: We disable the 'ea' demuxer on Windows because it'll just inifnitely parse some '.asf' files from EA game assets
