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

# Builds the following components:
# libfaac
# libfaad2
# xvidcore
# libx264

# then builds FFmpeg and MPlayer

# everything is built in "build" and installed to "stage" so it doesn't clutter up your system

# necessary environment variables
export ROOT_DIR=${ROOT_DIR:-`cd ..;pwd`}
export CODEC_DIR=${CODEC_DIR:-${ROOT_DIR}/codecs}
export FAAC_DIR=${FAAC_DIR:-${CODEC_DIR}/faac}
export FAAD2_DIR=${FAAD2_DIR:-${CODEC_DIR}/faad2}
export XVID_DIR=${XVID_DIR:-${CODEC_DIR}/xvidcore}
export X264_DIR=${X264_DIR:-${CODEC_DIR}/x264}
export FFMPEG_DIR=${FFMPEG_DIR:-${ROOT_DIR}/mplayerhq/ffmpeg}
export MPLAYER_DIR=${MPLAYER_DIR:-${ROOT_DIR}/mplayerhq/mplayer}

# place to store incoming tarballs
export TARBALL_DIR=${TARBALL_DIR:-`pwd`/packages}

# where we'll stage everything
export STAGE_DIR=${STAGE:-`pwd`/stage}
export STAGE_BIN=${STAGE_BIN:-${STAGE_DIR}/bin}
export STAGE_INC=${STAGE_INC:-${STAGE_DIR}/include}
export STAGE_LIB=${STAGE_LIB:-${STAGE_DIR}/lib}


# temporary build path
export BUILD_DIR=${BUILD_DIR:-`pwd`/build}


# toolchain path
export BIN_DIR=${BIN_DIR:-/opt/mingw/bin}


# native tools for host
export CC_FOR_BUILD=gcc
export NATIVE_HOST=${NATIVE_HOST:-`../third_party/GNU/config.guess`}

require_dir () {
	if test ! -d "$1"; then
		echo "Directory missing: \"$1\""
		exit 0
	fi
}

# test source paths to make sure everything exists
require_dir "${ROOT_DIR}"
require_dir "${CODEC_DIR}"
require_dir "${FAAC_DIR}"
require_dir "${FAAD2_DIR}"
require_dir "${XVID_DIR}"
require_dir "${X264_DIR}"
require_dir "${FFMPEG_DIR}"
require_dir "${MPLAYER_DIR}"

# cross-compile toolchain commands
TARGET=i686-pc-mingw32
CC=${TARGET}-gcc
CXX=${TARGET}-g++
LD=${TARGET}-ld
NM=${TARGET}-nm
AS=${TARGET}-as
RANLIB=${TARGET}-ranlib
AR=${TARGET}-ar
DLLTOOL=${TARGET}-dlltool
STRIP=${TARGET}-strip
WINDRES=${TARGET}-windres


test -d "${STAGE_DIR}" || mkdir "${STAGE_DIR}"
test -d "${BUILD_DIR}" || mkdir "${BUILD_DIR}"
test -d "${TARBALL_DIR}" || mkdir "${TARBALL_DIR}"

# make sure our toolchain is in the PATH
echo $PATH | grep -q "${BIN_DIR}" || export PATH=$PATH:"${BIN_DIR}"

# libfaac
if test ! -f "${FAAC_DIR}/configure" ; then
	pushd "${FAAC_DIR}"
	sh bootstrap
	popd
fi
mkdir -p "${BUILD_DIR}/faac"
pushd "${BUILD_DIR}/faac"
	test -f Makefile || \
		"${FAAC_DIR}"/configure --prefix="${STAGE_DIR}" \
			CC=${CC} CXX=${CXX} CFLAGS="-fno-common" \
			--enable-static --disable-shared --without-mp4v2 \
			--build=${NATIVE_HOST} --host=${TARGET} \
		|| exit $?
	test -f libfaac/.libs/libfaac.a || \
		make ${MAKE_JOBS} -C libfaac || exit $?
	test -f "${STAGE_INC}"/faac.h || \
		make -C include install || exit $?
	test libfaac/.libs/libfaac.a -nt "${STAGE_LIB}"/libfaac.a && \
		make -C libfaac install
popd

# libfaad
if test ! -f "${FAAD2_DIR}/configure" ; then
	pushd "${FAAD2_DIR}"
	sh bootstrap
	popd
fi
mkdir -p "${BUILD_DIR}/faad2"
pushd "${BUILD_DIR}/faad2"
	test -f Makefile || \
		"${FAAD2_DIR}"/configure --prefix="${STAGE_DIR}" \
			CC=${CC} CXX=${CXX} CFLAGS="-fno-common" \
			--without-xmms --without-mpeg4ip --without-drm \
			--build=${NATIVE_HOST} --host=${TARGET} \
		|| exit $?
	make ${MAKE_JOBS} -C libfaad
	test libfaad/.libs/libfaad.a -nt "${STAGE_LIB}"/libfaad.a && \
		make -C libfaad install
popd

# xvidcore
mkdir -p "${BUILD_DIR}/xvidcore"
pushd "${BUILD_DIR}/xvidcore"
if test ! -f configure ; then
	rsync -av --cvs-exclude "${XVID_DIR}"/build/generic/* . || exit $?
	cp sources.inc{,.bak}
	CLEAN_DIR=`echo ${XVID_DIR} | sed -e 's/ /\\\ /g'`
	cat sources.inc.bak | sed -e "s|SRC_DIR =.*|SRC_DIR = ${CLEAN_DIR}/src|g" > sources.inc
	sh bootstrap.sh || exit $?
	# libtoolize deletes config.{guess,sub}
	automake -c --add-missing >/dev/null 2>&1
	# now fix the pthread detection so it actually uses pthreads
#	cp configure{,.bak}
#	cat configure.bak | sed -e "s/-lpthread/-lpthreadGC2 -lws2_32/g" > configure
fi
if test ! -f platform.inc ; then
	./configure --prefix="${STAGE_DIR}" \
		CFLAGS="-fno-common" \
		--build=${NATIVE_HOST} --host=${TARGET} \
	|| exit $?
fi
test -f \=build/xvidcore.a || \
	make ${MAKE_JOBS}
if test \=build/xvidcore.a -nt "${STAGE_LIB}"/libxvidcore.a; then
	make install || exit $?
	rm -f "${STAGE_LIB}"/xvidcore.dll
	test -f "${STAGE_LIB}"/xvidcore.a && mv "${STAGE_LIB}"/{,lib}xvidcore.a
fi
popd

# libx264
mkdir -p "${BUILD_DIR}"/x264
pushd "${BUILD_DIR}"/x264
if test ! -f configure ; then
	rsync -av --cvs-exclude "${X264_DIR}"/* .
	cp configure{,.bak}
	cat configure.bak | sed -e 's|DEVNULL="NUL"|DEVNULL="/dev/null"|g'  > configure
fi
test -f config.mak || \
	CC=${CC} AR=${AR} RANLIB=${RANLIB} STRIP=${STRIP} ./configure --prefix="${STAGE_DIR}" \
		--extra-cflags="-fasm -fno-common -D_FILE_OFFSET_BITS=64" \
		--disable-avis-input --disable-mp4-output --enable-pthread \
		--host=${TARGET} \
	|| exit $?
test -f libx264.a || \
	make ${MAKE_JOBS} libx264.a || exit $?
# install libx64.a and x264.h manually
test libx264.a -nt "${STAGE_LIB}"/libx264.a && install libx264.a "${STAGE_LIB}"
test x264.h -nt "${STAGE_LIB}"/x264.h && install x264.h "${STAGE_INC}"
popd


# ffmpeg
mkdir -p "${BUILD_DIR}"/ffmpeg
pushd "${BUILD_DIR}"/ffmpeg
test -f config.mak || \
	${FFMPEG_DIR}/configure --target-os=mingw32 \
			--prefix="${STAGE_DIR}" \
			--disable-ffplay --disable-ffserver \
			--enable-gpl --enable-nonfree \
			--disable-encoder=aac \
			--disable-demuxer=ea --disable-devices \
			--enable-libxvid --enable-libx264 \
			--enable-libfaac --enable-libfaad \
			--enable-static --disable-shared \
			--enable-pthreads \
			--disable-debug \
			--enable-memalign-hack --arch=x86 \
			--extra-cflags="-fno-common -march=i686 -mtune=i686 -DWIN32 -I${STAGE_INC}" \
			--extra-ldflags="-L${STAGE_LIB}" \
			--enable-cross-compile --cross-prefix=${TARGET}- \
	|| exit $?
make ${MAKE_JOBS} || exit $?
 # installing here ensures the libavXXX.a libraries are available for building MPlayer too
make install
popd


# mplayer
mkdir -p "${BUILD_DIR}"/mplayer
pushd "${BUILD_DIR}"/mplayer
# ALWAYS RSYNC! This allows us to modify sources in the mplayer dir and have them rebuild
# the other projects have build systems that just work
rsync --cvs-exclude -av "${MPLAYER_DIR}"/* .
if test ! -f config.mak ; then
# MINGW_MEMALIGN activates our HACK memalign/mingw_memalign_free calls in mingwex
	EXTRA_CFLAGS="-fno-common -DMINGW_MEMALIGN=1" \
	./configure \
		--prefix="${STAGE_DIR}" \
		--target=i686-mingw32 \
		--enable-cross-compile \
		--host-cc=gcc \
		--cc=${CC} --as=${AS} --ar=${AR} --ranlib=${RANLIB} --windres=${WINDRES} \
		--enable-runtime-cpudetection \
		--disable-mencoder \
		--disable-gl --disable-direct3d --enable-directx \
		--enable-largefiles \
		--disable-vidix \
		--disable-langinfo --disable-tv \
		--disable-dvdread --disable-dvdread-internal \
		--disable-unrarexec --disable-menu \
		--disable-libdvdcss-internal --enable-pthreads \
		--disable-debug \
		--disable-liba52 \
		--disable-freetype --disable-fontconfig \
		--enable-stv --enable-stream-sagetv \
		--with-extraincdir="${STAGE_INC}":/opt/mingw/include/directx \
		--with-extralibdir="${STAGE_LIB}" \
		--extra-libs="-lmingwex -lpthreadGC2" \
	|| exit $?
fi
make ${MAKE_JOBS} || exit $?
install -m 755 mplayer.exe "${STAGE_BIN}"
popd

#		--extra-cflags='-I"${STAGE_INC}" -I/opt/mingw/include/directx' \
#		--extra-ldflags='-L"${STAGE_LIB}"' \


exit 0
# ---------------------------------------------------------------------
# ---------------------------------------------------------------------
#                          END OF SCRIPT
#	NOTHING PAST THIS POINT IS RUN, TYPE ANYTHING YOU WANT!
# ---------------------------------------------------------------------
# ---------------------------------------------------------------------





		--disable-mmx --disable-sse \
		--enable-runtime-cpudetection \

		--with-freetype-config="${STAGE_BIN}"/freetype-config --enable-fontconfig \		

		
# ---------------------------------------------------------------------
#      FreeType, libiconv, expat, fontconfig for CC rendering
# ---------------------------------------------------------------------

	# package versions we'll be using
export FT_VERS=2.3.9
export ICONV_VERS=1.12
export EXPAT_VERS=2.0.1
export FONTCONFIG_VERS=2.6.0

	# User agent from firefox, so we can download (otherwise it'll 406 us)
export CURL_AGENT='Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.9.0.7) Gecko/2009021910 Firefox/3.0.7'

		
# freetype
if ! test -f "${TARBALL_DIR}"/freetype-${FT_VERS}.tar.bz2; then
	pushd "${TARBALL_DIR}"
	curl -A "${CURL_AGENT}" -O http://savannah.inetbridge.net/freetype/freetype-${FT_VERS}.tar.bz2 || exit $?
	popd
fi
if test ! -d "${BUILD_DIR}"/freetype-${FT_VERS}; then
	pushd "${BUILD_DIR}"
	tar jxvf "${TARBALL_DIR}"/freetype-${FT_VERS}.tar.bz2 || exit $?
	popd
fi
pushd "${BUILD_DIR}"/freetype-${FT_VERS}
	test -f config.mk || \
		./configure --prefix="${STAGE_DIR}" \
			CC=$CC CPP=$CPP CFLAGS="-fno-common" \
			--build=${NATIVE_HOST} --host=${TARGET} \
		|| exit $?
	make ${MAKE_JOBS} || exit $?
	test objs/.libs/libfreetype.a -nt "${STAGE_DIR}"/lib/libfreetype.a && make install
popd

# libiconv
if ! test -f "${TARBALL_DIR}"/libiconv-${ICONV_VERS}.tar.gz; then
	pushd "${TARBALL_DIR}"
	curl -O http://ftp.gnu.org/gnu/libiconv/libiconv-${ICONV_VERS}.tar.gz || exit $?
	popd
fi
if ! test -d "${BUILD_DIR}"/libiconv-${ICONV_VERS}; then
	pushd "${BUILD_DIR}"
	tar zxvf "${TARBALL_DIR}"/libiconv-${ICONV_VERS}.tar.gz || exit $?
	popd
fi
pushd "${BUILD_DIR}"/libiconv-${ICONV_VERS}
	test -f Makefile || \
		./configure --prefix="${STAGE_DIR}" \
			CC=$CC CPP=$CPP CXX=$CXX \
			--enable-static --disable-shared \
			--build=${NATIVE_HOST} --host=${TARGET} \
		|| exit $?
	make ${MAKE_JOBS} || exit $?
	test lib/.libs/libiconv.a -nt "${STAGE_LIB}"/libiconv.a && make install
popd

# expat (needed for fontconfig)
if test ! -f "${PACKAGE_DIR}"/expat-${EXPAT_VERS}.tar.gz; then
	pushd "${PACKAGE_DIR}"
	curl -O http://superb-west.dl.sourceforge.net/expat/expat-${EXPAT_VERS}.tar.gz || exit $?
	popd
fi
if test ! -d "${BUILD_DIR}"/expat-${EXPAT_VERS}; then
	pushd "${BUILD_DIR}"
	tar zxvf "${PACKAGE_DIR}"/expat-${EXPAT_VERS}.tar.gz || exit $?
	popd
fi
pushd "${BUILD_DIR}"/expat-${EXPAT_VERS}
	test -f Makefile || \
		./configure --prefix="${STAGE_DIR}" \
			--enable-static --disable-shared \
			--build=${NATIVE_HOST} --host=${TARGET}
		|| exit $?
	make ${MAKE_JOBS} || exit $?
	test .libs/libexpat.a -nt "${STAGE_LIB}"/libexpat.a && make install
popd

# fontconfig
if test ! -f "${PACKAGE_DIR}"/fontconfig-${FONTCONFIG_VERS}.tar.gz; then
	pushd "${PACKAGE_DIR}"
	curl -O http://fontconfig.org/release/fontconfig-${FONTCONFIG_VERS}.tar.gz || exit $?
	popd
fi
if test ! -d "${BUILD_DIR}"/fontconfig-${FONTCONFIG_VERS}; then
	pushd "${BUILD_DIR}"
	tar zxvf "${PACKAGE_DIR}"/fontconfig-${FONTCONFIG_VERS}.tar.gz || exit $?
	popd
fi
pushd "${BUILD_DIR}"/fontconfig-${FONTCONFIG_VERS}
	if ! test -f Makefile; then
		./configure --prefix="${STAGE_DIR}" \
			--enable-static --disable-shared \
			--with-freetype-config="${STAGE_BIN}"/freetype-config \
			--with-arch=i386 \
			--with-default-fonts="C:/WINDOWS/Fonts" \
			--with-expat="${STAGE_DIR}" \
			CFLAGS="-I${STAGE_INC} -fno-common" LDFLAGS="-L${STAGE_LIB}" \
			--build=${NATIVE_HOST} --host=${TARGET} \
		|| exit $?
		cp src/Makefile src/Makefile.bak
		# for some reason it tries to create libfontconfig.dll.a even though it doesn't build a dll...
		# this disables that behavior
		cat src/Makefile.bak | sed -e 's/install-data-local:.*$/install-data-local:/g' > src/Makefile
	fi
	make ${MAKE_JOBS} || exit $?
	test src/.libs/libfontconfig.a -nt "${STAGE_LIB}"/libfontconfig.a && make install
popd

