#!/bin/bash

test -d packages || mkdir packages
test -d build || mkdir build


# pull in yasm sources from SVN
test -d packages/yasm || \
	svn co http://www.tortall.net/svn/yasm/trunk/yasm packages/yasm || exit $?


# prepare yasm for configuring
if ! test -f packages/yasm/configure; then
	pushd packages/yasm
	echo Preparing yasm for building...
	aclocal -I m4
	autoheader
	automake --copy -a
	autoconf
	test -f Makefile && make distclean
	popd
fi


# configure and build
test -d build/yasm || \
	mkdir build/yasm
pushd build/yasm

test -f Makefile ||
	../../packages/yasm/configure || exit $?

make && make install
popd
