#!/bin/sh
# x86-mingw32-build.sh -- vim: filetype=sh
# $Id: x86-mingw32-build.sh,v 1.1 2009-02-11 00:08:33 dave Exp $
#
# Script to guide the user through the build of a GNU/Linux hosted
# MinGW cross-compiler for Win32.
#
# Copyright (C) 2006, MinGW Project
# Written by Keith Marshall <keithmarshall@users.sourceforge.net>
# 
# This is the primary script for the x86-mingw32-build package.
#
# x86-mingw32-build is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by the
# Free Software Foundation; either version 2, or (at your option) any later
# version.
# 
# x86-mingw32-build is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
# or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# for further details.
# 
# You should have received a copy of the GNU General Public License along
# with x86-mingw32-build; see the file COPYING.  If not, write to the Free
# Software Foundation, 51 Franklin St - Fifth Floor, Boston, MA 02110-1301,
# USA.

CURDIR=`pwd`
test -r $0.sh.conf && script=$0.sh || script=$0
. $script.functions
. $script.conf
. $script.getopts

TARGET=${1-${TARGET-"${TARGET_CPU-i386}-mingw32"}}

assume BUILD_METHOD interactive
test "$BUILD_METHOD" = interactive && BUILD_METHOD=dialogue || BUILD_METHOD=batch
test -r $script.$BUILD_METHOD && . $script.$BUILD_METHOD

echo "
$script: checking package availability ..."
setbuilddir $PACKAGE_DIR .
for FILE in $DOWNLOAD
do
  prompt " $FILE ... "
  if test -f $FILE
  then
    echo ok
  elif isyes $ALLOW_DOWNLOADS
  then
    echo downloading ...
    $RUN wget $DOWNLOAD_HOST/$FILE || die $? "$script: download failed"
  else
    die 2 "missing ...
$script: unable to continue"
  fi
done

prompt "
$script: preparing the build tree... "
eval $RUN $CLEAN_SLATE_AT_START
setbuilddir "$WORKING_DIR" .
echo "done."

MAKE=${MAKE-"make"}
PATH=$INSTALL_DIR/bin:$PATH
unrecoverable="$script: unrecoverable error"

for STAGE in 1 2
do for COMPONENT in $BUILD_COMPONENTS
  do echo "
$script: stage $STAGE: build $COMPONENT ..."
  case $COMPONENT in

    binutils)
      if test -r binutils*/build/Makefile
      then
        cd binutils*/build
      else
	$RUN prepare binutils-$BINUTILS_VERSION
	$RUN setbuilddir binutils*
	$RUN ../configure --prefix="$INSTALL_DIR" --target="$TARGET" \
	  --with-sysroot \
	  $GLOBAL_BASE_OPTIONS $BINUTILS_BASE_OPTIONS || die $? \
          "$unrecoverable configuring binutils"
      fi
      $RUN $MAKE CFLAGS="`echo $CFLAGS_FOR_BINUTILS`" \
        LDFLAGS="`echo $LDFLAGS_FOR_BINUTILS`" || die $? \
	"$unrecoverable building binutils"
      $RUN $MAKE install || die $? \
	"$unrecoverable installing binutils"
      cd "$WORKING_DIR"; test $LEAN_BUILD && $RUN rm -rf binutils*
      ;;

    gcc)
      test -e "$INSTALL_DIR/mingw" || ln -s "$INSTALL_DIR" "$INSTALL_DIR/mingw"
      test -r gcc-*/configure || $RUN prepare gcc-core-$GCC_VERSION ""
      if test $STAGE -eq 2
      then
        for FILE in $GCC_LANGUAGE_OPTIONS
	do
	  case $GCC_LANGUAGE_SET in *$FILE*) ;; *) FILE=no ;; esac
	  case $FILE in 'c++') FILE='g++' ;; f77) FILE=g77 ;; esac
	  test $FILE = no || $RUN prepare gcc-$FILE-$GCC_VERSION ""
	done
      fi
      setbuilddir build-gcc .
      $RUN ../gcc-*/configure --prefix="$INSTALL_DIR" --target="$TARGET" \
	$GLOBAL_BASE_OPTIONS $GCC_BASE_OPTIONS --enable-languages=`
          case $STAGE in 1) echo c ;; 2) echo $GCC_LANGUAGE_SET ;; esac` \
        --with-headers="$INSTALL_DIR/include" \
        --with-sysroot="$INSTALL_DIR" || die $? \
        "$unrecoverable configuring gcc"
      $RUN $MAKE CFLAGS="$CFLAGS_FOR_GCC" LDFLAGS="$LDFLAGS_FOR_GCC" || die $? \
        "$unrecoverable building gcc"
      $RUN $MAKE install || die $? \
        "$unrecoverable installing gcc"
      cd "$WORKING_DIR"; test $LEAN_BUILD && rm -rf build-gcc
      ;;

    headers | mingwrt | w32api)
      test -r mingwrt-*/configure || $RUN prepare mingwrt-$RUNTIME_VERSION-mingw32
      test -r w32api-*/configure || $RUN prepare w32api-$W32API_VERSION
      case $COMPONENT in
	headers)
	  $RUN mkdir -p "$INSTALL_DIR/include"
          test -e "$INSTALL_DIR/usr" || (
	    $RUN cd "$INSTALL_DIR" && $RUN ln -s . usr )
          test -e "$INSTALL_DIR/usr/local" || (
	    $RUN cd "$INSTALL_DIR/usr" && $RUN ln -s . local )
	  $RUN cp -r mingwrt-*/include "$INSTALL_DIR" || die $? \
            "$unrecoverable installing mingwrt headers"
	  $RUN cp -r w32api-*/include "$INSTALL_DIR" || die $? \
            "$unrecoverable installing w32api headers"
	  ;;
	mingwrt)
          test -e w32api || $RUN ln -s w32api-* w32api
	  ;;
      esac
      case $COMPONENT in mingwrt | w32api)
	setbuilddir ${COMPONENT}-*
	$RUN ../configure --prefix="$INSTALL_DIR" --host="$TARGET" \
          --build=${BUILD_PLATFORM=`../config.guess`} || die $? \
          "$unrecoverable configuring $COMPONENT"
	$RUN $MAKE CFLAGS="$CFLAGS_FOR_RUNTIME" \
          LDFLAGS="$LDFLAGS_FOR_RUNTIME" || die $? \
          "$unrecoverable building $COMPONENT"
        $RUN $MAKE install || die $? \
          "$unrecoverable installing $COMPONENT"
        ;;
      esac
      ;;

  esac; done
  cd "$WORKING_DIR"; test $LEAN_BUILD && $RUN rm -rf mingwrt-* w32api-*
  BUILD_COMPONENTS=`case $BUILD_COMPONENTS in *gcc*) echo gcc ;; esac`
done

prompt "
$script: cleaning up... "
cd "$WORKING_DIR/.."; eval $RUN $CLEAN_SLATE_ON_EXIT
echo "done."
exit 0

# $RCSfile: x86-mingw32-build.sh,v $Revision: 1.1 $: end of file
