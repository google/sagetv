dnl Process this file with autoconf to produce a configure script.
AC_INIT(giflib, [4.1.4], [abadger1999@sourceforge.net], giflib)
AC_CONFIG_SRCDIR([lib/dgif_lib.c])
AM_INIT_AUTOMAKE([gnu check-news dist-bzip2 -Wall])
AM_CONFIG_HEADER(config.h)

dnl Checks for programs.
AC_PROG_LIBTOOL
AC_PROG_CC
AC_PROG_CPP
AC_PROG_INSTALL
AC_PROG_LN_S
AC_PROG_MAKE_SET

AC_PATH_XTRA

dnl Checks for libraries.

    AC_CHECK_LIB(m, pow, [MATH_LIB="${MATH_LIB} -lm"
                 COMPILABLE_EXTRAS="${COMPILABLE_EXTRAS} gifclrmp gifrotat"] )

if test ${MATH_LIB}; then
    AC_CHECK_LIB(rle, rle_hdr_init, [RLE_LIB="${RLE_LIBS} -lrle -lm"
                 COMPILABLE_EXTRAS="${COMPILABLE_EXTRAS} gif2rle rle2gif"],
                 ,"-lm" )
fi

dnl I don't have this on my system.  Could someone else configure it and
dnl check that it works?
    AC_CHECK_LIB(gl_s, main, [GL_S_LIB="${GL_S_LIB} -lgl_s $X_LIBS"
                             AC_DEFINE(HAVE_LIBGL_S, [1], [Define if the gl_s library is installed (SGI GL library)])
                 COMPILABLE_EXTRAS="${COMPILABLE_EXTRAS} gif2iris"],, $X_LIBS)

    AC_CHECK_LIB(X11, main, [X11_LIB="${X11_LIB} ${X_PRE_LIBS} $X_LIBS ${X_EXTRA_LIBS} -lX11"
                             AC_DEFINE(HAVE_LIBX11, [1], [Define if the X11 library is installed])
                 COMPILABLE_EXTRAS="${COMPILABLE_EXTRAS} gif2x11"],, $X_LIBS)
    DEVS="${GL_S_LIB} ${X11_LIB}" 
    AC_SUBST(DEVS)
    AC_SUBST(COMPILABLE_EXTRAS)
    AC_SUBST(GL_S_LIB)
    AC_SUBST(X11_LIB)
    AC_SUBST(X_CFLAGS)
    AC_SUBST(MATH_LIB)
    AC_SUBST(RLE_LIB)

dnl Checks for header files.
AC_HEADER_STDC
dnl * HEADER_STDC checks for stdargs.h so this is a bit redundant.
dnl * The code will prefer stdargs.h if available.
dnl   If not available, we will try to fallback to using varargs.h
AC_CHECK_HEADERS([stdarg.h varargs.h], break,
		 AC_MSG_ERROR([giflib 4.x must have stdarg.h or varargs.h]))

AC_CHECK_HEADERS(fcntl.h, ,
		 AC_MSG_ERROR([giflib 4.x must have fcntl.h to compile]))

dnl Can we survive without these?
AC_CHECK_HEADERS(unistd.h)

dnl Find a way to define a 32 bit integer
foundint=no
AC_CHECK_HEADERS([sys/types.h], [
	AC_DEFINE([UINT32], u_int32_t,
		  [Set to an unsigned 32 bit integer available on this system])
	foundint=yes]
)

if test x$foundint != x'yes'; then
AC_CHECK_HEADERS([stdint.h], [
	 AC_DEFINE([UINT32], uint32_t,
		  [Set to an unsigned 32 bit integer available on this system])
	 foundint=yes]
)
fi

if test x$foundint != x'yes'; then
	AC_CHECK_SIZEOF(unsigned int)
	if test $ac_cv_sizeof_unsigned_int -eq 4; then
		AC_DEFINE([UINT32], [unsigned int],
		  [Set to an unsigned 32 bit integer available on this system])
	else
		AC_CHECK_SIZEOF(unsigned long)
		if test $ac_cv_sizeof_unsigned_long -eq 4; then
			AC_DEFINE([UINT32], [unsigned long],
		  [Set to an unsigned 32 bit integer available on this system])
		else
			AC_ERROR("Unable to find a thirty-two bit integer type")
		fi
	fi
fi

dnl Checks for typedefs, structures, and compiler characteristics.
AC_C_CONST

dnl Checks for library functions.
AC_FUNC_MEMCMP
AC_FUNC_SETVBUF_REVERSED
AC_FUNC_VPRINTF
AC_CHECK_FUNCS(strdup)

AC_CONFIG_FILES([
util/Makefile
lib/Makefile
Makefile
doc/Makefile
pic/Makefile
])
AC_OUTPUT
