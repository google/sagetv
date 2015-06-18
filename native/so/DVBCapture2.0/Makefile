

NATIVECORE_LIB=../../lib/NativeCore/libNativeCore.so
NATIVECORED_LIB=../../lib/NativeCore/libNativeCored.so
CHANNEL_LIB=../../lib/NativeCore/channel.a
CHANNELD_LIB=../../lib/NativeCore/channeld.a

NATIVECORE_SRC = ../../ax/Native2.0/NativeCore
NATIVECORE_INC = -I../../ax/Native2.0/NativeCore
CHANNEL_SRC = ../../ax/Channel-2
CHANNEL_INC = -I../../ax/Channel-2

#DEBUG_OPTION = -g -O0 -DDEBUGDVB
DEBUG_OPTION = -g -O0

JDK_HOME ?= /usr/local/j2sdk

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

CFLAGS = -Os -Wall $(NATIVECORE_INC) $(CHANNEL_INC) $(DEBUG_OPTION) -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -D_FILE_OFFSET_BITS=64 -DLinux 
BINDIR=/usr/local/bin

all:dep_make libDVBCapture.so
debug:debug_dep_make libDVBCapture.so libDVBCapture.so.debug

OBJFILES=sage_DVBCaptureDevice.o circbuffer.o thread_util.o

libDVBCapture.so: $(OBJFILES) 
	$(CC)  -shared -Wl,-Map=libDVBCapture.map -Wall -o libDVBCapture.so $(OBJFILES) libNativeCore.so  $(CHANNEL_LIB)

libDVBCapture.so.debug: $(OBJFILES) 
	$(CC) -g -O0 -Wl,-Map=libDVBCapture.map -shared -W1 -o libDVBCapture.so libNativeCored.so $(OBJFILES)  $(CHANNELD_LIB)

dvbtest: dep_make libDVBCapture.so dvbtest.o sage_DVBCaptureDevice.c
	$(CC)   -Wall -o dvbtest dvbtest.o libNativeCore.so  $(CHANNEL_LIB)

dvbtest.o: sage_DVBCaptureDevice.c
	$(CC) $(CFLAGS) -c -o dvbtest.o -DSTANDALONE sage_DVBCaptureDevice.c		

dep_make: 
	$(MAKE) -C $(NATIVECORE_SRC)
	$(MAKE) -C $(CHANNEL_SRC)
	cp $(NATIVECORE_LIB) libNativeCore.so

debug_dep_make: 
	$(MAKE) -C $(NATIVECORE_SRC) debug
	$(MAKE) -C $(CHANNEL_SRC)  debug
	cp $(NATIVECORE_LIB) libNativeCored.so

clean:
	rm -f *.o libDVBCapture.so libNativeCore.so libNativeCored.so *.c~ *.h~ *.map

install:
	cp libDVBCapture.so /opt/sagetv/server
	cp libNativeCore.so /opt/sagetv/server


