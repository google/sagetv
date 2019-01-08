# Pushread build for Windows with MinGW

FFMPEG_DIR=../../ffmpeg

CC=gcc
BINDIR=/usr/local/bin
CFLAGS =-D__MINGW32__ -g -c -s -D_FILE_OFFSET_BITS=64 -I$(FFMPEG_DIR)/libavformat -I$(FFMPEG_DIR)/libavutil -I$(FFMPEG_DIR)/libavcodec -I$(FFMPEG_DIR)/libswresample -I$(FFMPEG_DIR)
DLL_CFLAGS = -s -static-libgcc -Wl,--kill-at -shared -D_FILE_OFFSET_BITS=64 -I$(FFMPEG_DIR)/libavformat -I$(FFMPEG_DIR)/libavutil -I$(FFMPEG_DIR)/libavcodec -I$(FFMPEG_DIR)
CODECS_LIB = $(FFMPEG_DIR)/libavformat/libavformat.a $(FFMPEG_DIR)/libavcodec/libavcodec.a $(FFMPEG_DIR)/libavutil/libavutil.a ../../codecs/faac/libfaac/.libs/libfaac.a ../../codecs/faad2/libfaad/.libs/libfaad.a "../../codecs/xvidcore/build/generic/=build/xvidcore.a" ../../codecs/x264/libx264.a -Wl,-Bstatic -lpthread
COMM_LIB = -lm -lws2_32 -lpthread

all: Pushreader.dll
test: prtest.exe


OBJFILES=pushreader.o flashmpeg4.o pushreadertest.o feeder.o
SRC_FILES=pushreader.c flashmpeg4.c

prtest.exe: $(OBJFILES)
	$(CC) -g -W1 -o prtest.exe $(OBJFILES) $(CODECS_LIB) -lm -lfaac -lfaad -lxvidcore -lx264 -lws2_32 -lpthread

Pushreader.dll: $(OBJFILES)
	$(CC) $(DLL_CFLAGS) -o Pushreader.dll $(OBJFILES) $(CODECS_LIB) $(COMM_LIB) -Wl,--output-def,Pushreader.def,--out-implib,libPushreader.a,-Bstatic -lz
	sed -i s/\.skip_prologue/_skip_prologue/ Pushreader.def
	dlltool -d Pushreader.def -D Pushreader.dll -l Pushreader.lib
	case `uname` in \
	  MINGW32*) mkdir -p Win32 ; cp Pushreader.* Win32 ;; \
	  MINGW64*) mkdir -p x64   ; cp Pushreader.* x64   ;; \
	esac

	
clean:
	rm -f *.o libPushreader.so prtest.exe *.c~ *.h~ Pushreader.dll Pushreader.lib
	case `uname` in \
	  MINGW32*) rm -f ./Win32/* ;; \
	  MINGW64*) rm -f ./x64/*   ;; \
	esac
