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

FFMPEG_DIR = ../../../third_party/ffmpeg
CFLAGS = -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -I$(FFMPEG_DIR)
BINDIR=/usr/local/bin

OBJFILES=Mpeg2Transcoder.o

libMpeg2Transcoder.so: $(OBJFILES)
	$(CC) -shared -o libMpeg2Transcoder.so $(OBJFILES) -L$(FFMPEG_DIR)/libavutil -L$(FFMPEG_DIR)/libavformat -L$(FFMPEG_DIR)/libavcodec -lavformat-minimal -lavcodec-minimal -lavutil-minimal -lm -lz

clean:
	rm -f *.o libMpeg2Transcoder.so *.c~ *.h~
