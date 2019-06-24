
LIBSRCS += hdhomerun_channels.c
LIBSRCS += hdhomerun_channelscan.c
LIBSRCS += hdhomerun_control.c
LIBSRCS += hdhomerun_debug.c
LIBSRCS += hdhomerun_device.c
LIBSRCS += hdhomerun_device_selector.c
LIBSRCS += hdhomerun_discover.c
LIBSRCS += hdhomerun_os_posix.c
LIBSRCS += hdhomerun_pkt.c
LIBSRCS += hdhomerun_sock_posix.c
LIBSRCS += hdhomerun_video.c

CC    := $(CROSS_COMPILE)gcc
STRIP := $(CROSS_COMPILE)strip

CFLAGS += -O2 -Wall -Wextra -Wmissing-declarations -Wmissing-prototypes -Wstrict-prototypes -Wpointer-arith -Wno-unused-parameter
LDFLAGS += -lpthread
SHARED = -shared -Wl,-soname,libhdhomerun$(LIBEXT)

ifeq ($(OS),Windows_NT)
  BINEXT := .exe
  LIBEXT := .dll
  LDFLAGS += -liphlpapi
else
  OS := $(shell uname -s)
  LIBEXT := .so
  ifeq ($(OS),Linux)
    LDFLAGS += -lrt
  endif
  ifeq ($(OS),SunOS)
    LDFLAGS += -lsocket
  endif
  ifeq ($(OS),Darwin)
    CFLAGS += -arch i386 -arch x86_64
    LIBEXT := .dylib
    SHARED := -dynamiclib -install_name libhdhomerun$(LIBEXT)
  endif
endif

all : hdhomerun_config$(BINEXT) libhdhomerun$(LIBEXT)

hdhomerun_config$(BINEXT) : hdhomerun_config.c $(LIBSRCS)
	$(CC) $(CFLAGS) $+ $(LDFLAGS) -o $@
	$(STRIP) $@

libhdhomerun$(LIBEXT) : $(LIBSRCS)
	$(CC) $(CFLAGS) -fPIC -DDLL_EXPORT $(SHARED) $+ $(LDFLAGS) -o $@

clean :
	-rm -f hdhomerun_config$(BINEXT)
	-rm -f libhdhomerun$(LIBEXT)

distclean : clean

%:
	@echo "(ignoring request to make $@)"

.PHONY: all list clean distclean
