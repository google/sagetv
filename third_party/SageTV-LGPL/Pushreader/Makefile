# It's a test pushread build for Windows ZQ with MinGW


#for test  ZQ.
WORK=/c/Sage/native/ax/StrmDeMux

STAGE=/c/sage/buildwin/stage
STAGE_LIB=$(STAGE)/lib
STAGE_INC=$(STAGE)/include
FFMPEG_DIR=/c/sage/ffmpeg

#for test ZQ.
#FFMPEG_DIR=/C/zq/Projects/ffmpeg

CC=gcc
BINDIR=/usr/local/bin
CFLAGS =-D__MINGW32__ -g -c -D_FILE_OFFSET_BITS=64 -I$(FFMPEG_DIR)/libavformat -I$(FFMPEG_DIR)/libavutil -I$(FFMPEG_DIR)/libavcodec -I$(FFMPEG_DIR) -I$(STAGE_INC) -I$(STAGE_INC)
DLL_CFLAGS = -shared -D_FILE_OFFSET_BITS=64 -I$(FFMPEG_DIR)/libavformat -I$(FFMPEG_DIR)/libavutil -I$(FFMPEG_DIR)/libavcodec -I$(FFMPEG_DIR) -I$(STAGE_INC) -I$(STAGE_INC)
#CODECS_LIB = $(FFMPEG_DIR)/libavformat/libavformat.a $(FFMPEG_DIR)/libavcodec/libavcodec.a $(FFMPEG_DIR)/libavutil/libavutil.a -L${STAGE_LIB} -lfaac -lfaad -lxvidcore -lx264
CODECS_LIB = $(STAGE_LIB)/libavformat.a $(STAGE_LIB)/libavcodec.a $(STAGE_LIB)/libavutil.a -L${STAGE_LIB} -lfaac -lfaad -lxvidcore -lx264 
COMM_LIB = -lm -lws2_32 -lpthread

all: Pushreader.dll
test: prtest.exe


OBJFILES=pushreader.o flashmpeg4.o pushreadertest.o feeder.o
SRC_FILES=pushreader.c flashmpeg4.c

prtest.exe: $(OBJFILES)
	$(CC) -g -W1 -o prtest.exe $(OBJFILES) -L${STAGE_LIB} $(CODECS_LIB) -lm -lfaac -lfaad -lxvidcore -lx264 -lws2_32 -lpthread

Pushreader.dll: $(OBJFILES)
	$(CC) $(DLL_CFLAGS) -o Pushreader.dll $(OBJFILES) $(FF_MPEG_LIB) $(CODECS_LIB) $(COMM_LIB) -Wl,--output-def,Pushreader.def,--out-implib,libPushreader.a
	lib /machine:x86 /def:Pushreader.def
	install -v pushreader.h   $(STAGE)/include
	install -v pushreader.dll $(STAGE)
	install -v pushreader.lib $(STAGE)/lib
	install -v pushreader.lib $(WORK)/lib
	install -v pushreader.h   $(WORK)/include
	install -v pushreader.dll $(WORK)
	install -v pushreader.dll $(WORK)/StreamDeMux
	install -v pushreader.dll $(WORK)/StreamDeMux
	install -v pushreader.dll "$(SAGEDEVREL)"
	install -v pushreader.dll /c/Sage/buildwin/prebuilt/release
	install -v pushreader.lib /C/Sage/native/lib/tsnative
	install -v pushreader.h   /c/Sage/native/include
#	install -v pushreader.dll "$(SAGEDEVDEBUG)"

install:
	install -v pushreader.h   $(STAGE)/include
	install -v pushreader.dll $(STAGE)
	install -v pushreader.lib $(STAGE)/lib
	install -v pushreader.lib $(WORK)/lib
	install -v pushreader.h   $(WORK)/include
	install -v pushreader.dll $(WORK)
	install -v pushreader.dll $(WORK)/StreamDeMux
	install -v pushreader.dll /c/Sage/buildwin/prebuilt/release
	install -v pushreader.lib /C/Sage/native/lib/tsnative
	install -v pushreader.h   /c/Sage/native/include
	install -v pushreader.dll "$(SAGEDEVREL)"
	
libPushreader.so: $(OBJFILES)
	$(CC) -g -W1 -shared -o libPushreader.so $(OBJFILES) -L${STAGE_LIB} $(CODECS_LIB) -lm -lfaac -lfaad -lxvidcore -lx264 -lws2_32 -lpthread
	
#	strip --strip-unneeded libPushreader.so 

clean:
	rm -f *.o libPushreader.so prtest.exe *.c~ *.h~    Pushreader.dll 
	
#
#Note: referenc http://www.mingw.org/wiki/MSVC_and_MinGW_DLLs
#      we have to put vcvars.bat into msys.bat to setup path so that lib.exe can be found on the path, which is 
#      in "C:\Program Files\Microsoft Visual Studio 8\VC\bin\lib.exe" 
#	

