#
#
#NOTE:  openRemuxer0, closeRemuxer0, pushRemuxData0 are called only by  RemuxTranscodeEngine for a Hauppauge MVP, 
# which use TSNative library,  so we have to save TSNative.  --ZQ
#

TSNATIVE_LIB=../../lib/TSnative/libTSnative.so
TSNATIVED_LIB=../../lib/TSnative/libTSnatived.so
TSNATIVE_SRC =    ../../ax/TSnative
TSNATIVE_INC = -I../../ax/TSnative

NATIVECORE_LIB=../../lib/NativeCore/libNativeCore.so
NATIVECORED_LIB=../../lib/NativeCore/libNativeCored.so
NATIVECORE_SRC = ../../ax/Native2.0/NativeCore
NATIVECORE_INC = -I../../ax/Native2.0/NativeCore

JDK_HOME ?= /usr/local/j2sdk

#DEBUG_OPTION = -g -O0 -DDEBUGDVB
DEBUG_OPTION = -g -O0

ifdef TARGET
    CROSS_PREFIX:=$(TARGET)-
	RMV_FLAG:=-DNO_MEDIA_MVP
    ADD_FLAG:=
else
    CROSS_PREFIX:=
	RMV_FLAG:=
	ADD_FLAG:=libTSnative.so
endif

CC:=$(CROSS_PREFIX)gcc
LD:=$(CROSS_PREFIX)ld
AR:=$(CROSS_PREFIX)ar
RANLIB:=$(CROSS_PREFIX)ranlib
STRIP:=$(CROSS_PREFIX)strip

CFLAGS =-g $(TSNATIVE_INC) $(NATIVECORE_INC) $(DEBUG_OPTION) $(RMV_FLAG) -I../../include -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -D_FILE_OFFSET_BITS=64

all:dep_make libMPEGParser.so
debug:dep_make_debug libMPEGParser.so.debug

OBJFILES=sage_media_format_MPEGParser.o

libMPEGParser.so: $(OBJFILES)
	$(CC)  -shared -o libMPEGParser.so $(OBJFILES) libNativeCore.so $(ADDFLAG)

libMPEGParser.so.debug: $(OBJFILES)
	$(CC)  -shared -g -O0 -Wl,-Map=libMPEGParser.map -W1 -o libMPEGParser.so $(OBJFILES) 

dep_make:
	$(MAKE) -C $(TSNATIVE_SRC)
	cp $(TSNATIVE_LIB) libTSnative.so
	$(MAKE) -C $(NATIVECORE_SRC)
	cp $(NATIVECORE_LIB) libNativeCore.so

dep_make_debug:
	$(MAKE) -C $(TSNATIVE_SRC) debug
	cp $(TSNATIVE_LIB) libTSnatived.so

clean:
	$(MAKE) -C $(NATIVECORE_SRC) clean
	$(MAKE) -C $(TSNATIVE_SRC) clean
	rm -f *.o libMPEGParser.so *.c~ *.h~ *.so

install:
	cp libMPEGParser.so /opt/sagetv/server
	cp libNativeCore.so /opt/sagetv/server
