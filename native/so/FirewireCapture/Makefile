JDK_HOME ?= /usr/local/j2sdk
LIBRAW1394_DIR ?= /usr/local/include/libraw1394
LIBAVC1394_DIR ?= /usr/local/include/libavc1394
LIBIEC61883_DIR ?= /usr/local/include/libiec61883

ifdef TARGET
        CROSS_PREFIX:=$(TARGET)-
else
        CROSS_PREFIX:=
endif
CC:=$(CROSS_PREFIX)gcc
LD:=$(CROSS_PREFIX)ld
AR:=$(CROSS_PREFIX)ar
RANLIB:=$(CROSS_PREFIX)ranlib
STRIP:=$(CROSS_PREFIX)strip

CFLAGS = -c -fPIC -I. -I$(LIBRAW1394_DIR) -I$(LIBAVC1394_DIR) -I$(LIBIEC61883_DIR) -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -D_FILE_OFFSET_BITS=64

OBJFILES=sage_FirewireCaptureDevice.o

libFirewireCapture.so: $(OBJFILES)
	$(CC) -shared -o libFirewireCapture.so $(OBJFILES) -liec61883 -lraw1394 -lavc1394 -lrom1394

clean:
	rm -f *.o libFirewireCapture.so *.c~ *.h~
