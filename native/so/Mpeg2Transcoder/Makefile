JDK_HOME ?= /usr/local/j2sdk
CC=gcc
FFMPEG_DIR = ../../../third_party/ffmpeg
CFLAGS = -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -I$(FFMPEG_DIR)
BINDIR=/usr/local/bin

OBJFILES=Mpeg2Transcoder.o

libMpeg2Transcoder.so: $(OBJFILES)
	$(CC) -shared -o libMpeg2Transcoder.so $(OBJFILES) -L$(FFMPEG_DIR)/libavutil -L$(FFMPEG_DIR)/libavformat -L$(FFMPEG_DIR)/libavcodec -lavformat-minimal -lavcodec-minimal -lavutil-minimal -lm -lz

clean:
	rm -f *.o libMpeg2Transcoder.so *.c~ *.h~
