NATIVECORE_LIB=../../lib/NativeCore/libNativeCore.so
NATIVECORE_SRC=../../ax/Native2.0/NativeCore
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

CFLAGS=-Os -Wall -I$(NATIVECORE_SRC) -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -D_FILE_OFFSET_BITS=64 -DLinux

all: nativeCore libJavaRemuxer2.so

OBJFILES=JavaRemuxer2.o

libJavaRemuxer2.so: $(OBJFILES)
	$(CC) -shared -Wl,-Map=libJavaRemuxer2.map -Wall -o libJavaRemuxer2.so $(OBJFILES) libNativeCore.so

nativeCore:
	$(MAKE) -C $(NATIVECORE_SRC)
	cp $(NATIVECORE_LIB) libNativeCore.so

JavaRemuxer2.o: JavaRemuxer2.cpp
	$(CC) $(CFLAGS) -c -o JavaRemuxer2.o JavaRemuxer2.cpp

clean:
	rm -f *.o libJavaRemuxer2.so libNativeCore.so *.c~ *.h~ *.map

install:
	cp libJavaRemuxer2.so /opt/sagetv/server
	cp libNativeCore.so /opt/sagetv/server
