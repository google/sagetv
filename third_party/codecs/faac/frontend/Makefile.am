bin_PROGRAMS = faac

faac_SOURCES = main.c input.c

if WITH_MP4V2
INCLUDES = -I$(top_srcdir)/include -I$(top_srcdir)/common/mp4v2
LDADD = $(top_builddir)/libfaac/libfaac.la $(top_srcdir)/common/mp4v2/libmp4v2.la -lm -lstdc++
else
INCLUDES = -I$(top_srcdir)/include 
LDADD = $(top_builddir)/libfaac/libfaac.la -lm
endif
