#!/bin/bash

# check if gpg is available
HAVE_GPG=0
test `gpg --version > /dev/null 2>&1; echo $?` -eq 0 && HAVE_GPG=1

if test $HAVE_GPG -eq 1; then
	# import sig key if necessary
	gpg --list-keys | grep -q 1024D/F71EDF1C || gpg --import jsm.gpg
fi

test -d build || mkdir build
test -d packages || mkdir packages

MINGW_TARGET=i686-pc-mingw32
GCC_VER=4.2.4

# First we need to download gcc-core and gcc-g++ from ftp.gnu.org because they are not hosted on SF
pushd packages
if test ! -f gcc-core-$GCC_VER.tar.bz2; then
	curl -O ftp://ftp.gnu.org/gnu/gcc/gcc-$GCC_VER/gcc-core-$GCC_VER.tar.bz2
	if test $HAVE_GPG -eq 1; then
		test -f gcc-core-$GCC_VER.tar.bz2.sig || \
			curl -O ftp://ftp.gnu.org/gnu/gcc/gcc-$GCC_VER/gcc-core-$GCC_VER.tar.bz2.sig
		gpg --verify gcc-core-$GCC_VER.tar.bz2.sig gcc-core-$GCC_VER.tar.bz2 || exit $?
	fi
fi
if test ! -f gcc-g++-$GCC_VER.tar.bz2; then
	curl -O ftp://ftp.gnu.org/gnu/gcc/gcc-$GCC_VER/gcc-g++-$GCC_VER.tar.bz2
	if test $HAVE_GPG -eq 1; then
		test -f gcc-g++-$GCC_VER.tar.bz2.sig || \
			curl -O ftp://ftp.gnu.org/gnu/gcc/gcc-$GCC_VER/gcc-g++-$GCC_VER.tar.bz2.sig
		gpg --verify gcc-g++-$GCC_VER.tar.bz2.sig gcc-g++-$GCC_VER.tar.bz2 || exit $?
	fi
fi
popd

# There might be some concern about using vanilla gcc source,
# but keep in mind most of the changes are for building/running in MSYS, not Cygwin
# Everything else is available via MinGW project downloads on SF


# build our mingw32 cross-compiler toolchain
pushd x86-mingw32-build
# FIXME: Put build settings in this file instead of in x86-mingw32-build.sh.conf
./x86-mingw32-build.sh --batch --no-pre-clean --no-post-clean $MINGW_TARGET || exit $?
popd

# copy directx headers from Allegro <http://alleg.sourceforge.net>
cp dx80/include/*.h /opt/mingw/include/directx/

# make sure our new build tools are in the path
echo $PATH | grep -q /opt/ming/bin || export PATH=$PATH:/opt/mingw/bin

# build pthreads-win32
if ! test -f /opt/mingw/lib/libpthreadGC2.a; then
	pushd pthreads
	rm -f libpthread*.{a,stamp} pthread*.{dll,stamp}
	make CROSS=${MINGW_TARGET}- clean GC
	install -m 644 pthread.h semaphore.h sched.h /opt/mingw/include
	install -m 644 pthread*.dll /opt/mingw/lib
#	install -m 644 libpthread*.a /opt/mingw/lib
	popd
fi

# build zlib
if ! test -f /opt/mingw/lib/libz.a; then
	test -f packages/zlib-1.2.3.tar.gz || \
		curl -o packages/zlib-1.2.3.tar.gz http://www.zlib.net/zlib-1.2.3.tar.gz
	test -d build/zlib-1.2.3 || \
		tar zxvf packages/zlib-1.2.3.tar.gz -C build
	
	pushd build/zlib-1.2.3
	CC=${MINGW_TARGET}-gcc AR="${MINGW_TARGET}-ar -q" RANLIB=${MINGW_TARGET}-ranlib ./configure --prefix=/opt/mingw
	make && make install
	popd
fi
