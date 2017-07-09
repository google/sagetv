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

CFLAGS = -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -D_FILE_OFFSET_BITS=64

all: PVR150Tuner.so

PVR150Tuner.so: PVR150Tuner.o
	$(CC) -o PVR150Tuner.so -shared PVR150Tuner.o

PVR150Tuner.o: PVR150Tuner.c
	$(CC) $(CFLAGS) -Wall -O2 -c PVR150Tuner.c

clean:
	rm -f *.o PVR150Tuner.so *.c~ *.h~
