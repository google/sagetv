#Qian Zhang

NATIVE_CORE_SRC = ../NativeCore
NATIVE_CORE_LIB = ../../../lib/NativeCore

#DEBUG_OPTION =-g 
CPU_TUNE = 

ifdef TARGET
        CROSS_PREFIX:=$(TARGET)-
		CPU_TUNE:=
		#CPU_TUNE:= -pg -ffast-math -funswitch-loops -mno-check-zero-division
		NATIVE_CORE_LIB = /sage/lib
else
        CROSS_PREFIX:=
endif

ifdef TARGET
	NATIVECORED_LIB=/sage/lib/libNativeCored.so
	CHANNEL_LIB=/sage/lib/channel.a
endif

CC:=$(CROSS_PREFIX)gcc
LD:=$(CROSS_PREFIX)ld
AR:=$(CROSS_PREFIX)ar
RANLIB:=$(CROSS_PREFIX)ranlib
STRIP:=$(CROSS_PREFIX)strip

#########
SRCS0=ATSCPSIParser.c AVAnalyzer.c AVTrack.c Bits.c BlockBuffer.c ChannelScan.c Demuxer.c DVBPSIParser.c ESAnalyzer.c GetAVInf.c NativeCore.c \
     NativeMemory.c PSBuilder.c PSIParser.c PSIParserConstData.c PSParser.c Remuxer.c SectionData.c TSBuilder.c TSCRC32.c TSFilter.c TSParser.c \
	 ScanFilter.c TSInfoParser.c	\
     AVFormat/AACFormat.c AVFormat/AC3Format.c AVFormat/DTSFormat.c AVFormat/H264Format.c AVFormat/LPCMFormat.c AVFormat/MpegAudioFormat.c \
     AVFormat/MpegVideoFormat.c AVFormat/VC1Format.c AVFormat/EAC3Format.c AVFormat/MpegVideoFrame.c AVFormat/Subtitle.c 

SRCS=$(addprefix ../NativeCore/, $(basename $(SRCS0)))
OBJS= $(addsuffix .o, $(basename $(SRCS)))
#########



CFLAGS = -O3 -Wall $(NATIVECORE_INC) $(CHANNEL_INC) $(DEBUG_OPTION) -fPIC -I$(JAVA_HOME)/include/ -I$(NATIVE_CORE_SRC) -I$(JAVA_HOME)/include/linux -D_FILE_OFFSET_BITS=64 -DLinux $(CPU_TUNE)
BINDIR=/usr/local/bin

#CFLAGS =-Wall $(DEBUG_OPTION) -fPIC -D_FILE_OFFSET_BITS=64 -DLinux -I$(NATIVE_CORE_SRC)


all:pvrpts

OBJFILES=PVRPTS.o VRTSParser.o

pvrpts: PVRPTS.c PVRTSParser.c PVRTSParser.h
	$(CC) PVRPTS.c PVRTSParser.c $(CFLAGS) -Wall -o pvrpts $(OBJS)

		
install:
ifdef TARGET
	cp pvrpts /sage/playground
else
	cp pvrpts /usr/bin
endif 
	

clean:
	rm -f *.o libNatievCore.so  *.c~ *.h~ *.map pvrpts
