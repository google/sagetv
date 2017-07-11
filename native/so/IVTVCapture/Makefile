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

CFLAGS = -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -I../../../third_party/V4L -D_FILE_OFFSET_BITS=64
BINDIR=/usr/local/bin

OBJFILES=sage_IVTVCaptureDevice.o sage_SFIRTuner.o misc.o thread_util.o circbuffer.o

libIVTVCapture.so: $(OBJFILES)
	$(CC) -shared -Wall -lpthread -o libIVTVCapture.so $(OBJFILES)

clean:
	rm -f *.o libIVTVCapture.so *.c~ *.h~
