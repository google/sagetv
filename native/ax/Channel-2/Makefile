##Created by Qian Zhang, 09/27/09.
##
#makefile for linux

#Select debug you want
DEBUG=-DDEBUG_LINUX
#DEBUG=-DDEBUG_LINUX -O0

JAVA_HOME ?= /opt/jdk1.5.0_02

#CC=gcc
BINDIR=/usr/local/bin

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

#JSDK=-I/usr/local/j2sdk/include/ -I/usr/local/j2sdk/include/linux 
JSDK=-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux 
INCLUDE= -I../Native2.0/NativeCore 

CFLAGS=-fPIC -D_FILE_OFFSET_BITS=64 -Wall -Wno-missing-braces -DLinux $(INCLUDE) $(JSDK) $(DEBUG)

TARGETDIR=../../lib/NativeCore
ifdef TARGET
	TARGETDIR=/sage/lib
endif

SRCS=Channel.c ChannelDataMng.c
OBJS= $(addsuffix .o, $(basename $(SRCS)))

all:  $(TARGETDIR)/channel.a

foo:
	@echo TARGET=$(TARGET)
	@echo CROSS_PREFIX=$(CROSS_PREFIX)
	@echo CC=$(CC)

$(TARGETDIR):
	mkdir -p $(TARGETDIR)
	touch $(TARGETDIR)

$(TARGETDIR)/channel.a: $(TARGETDIR) $(OBJS)
	$(AR) rvu $@ $(OBJS)
	$(RANLIB) $@

clean:
	rm -f *.o *.c~ *.h~ $(TARGETDIR)/channel.a

depend:
	makedepend -Y -- $(CFLAGS) -- $(SRCS)


# DO NOT DELETE THIS LINE -- make depend depends on it.

Channel.o:../Native2.0/NativeCore/ATSCPSIParser.h  ../Native2.0/NativeCore/AVAnalyzer.h 
Channel.o:../Native2.0/NativeCore/AVTrack.h        ../Native2.0/NativeCore/BlockBuffer.h 
Channel.o:../Native2.0/NativeCore/ChannelScan.h    ../Native2.0/NativeCore/ChannelScanInc.h 
Channel.o:../Native2.0/NativeCore/Demuxer.h        ../Native2.0/NativeCore/DVBPSIParser.h 
Channel.o:../Native2.0/NativeCore/ESAnalyzer.h     ../Native2.0/NativeCore/GetAVInf.h         
Channel.o:../Native2.0/NativeCore/NativeCore.h     ../Native2.0/NativeCore/NativeMemory.h 
Channel.o:../Native2.0/NativeCore/PSBuilder.h      ../Native2.0/NativeCore/PSIParserConstData.h 
Channel.o:../Native2.0/NativeCore/PSIParser.h      ../Native2.0/NativeCore/PSParser.h 
Channel.o:../Native2.0/NativeCore/Remuxer.h        ../Native2.0/NativeCore/SectionData.h 
Channel.o:../Native2.0/NativeCore/TSBuilder.h      ../Native2.0/NativeCore/TSCRC32.h 
Channel.o:../Native2.0/NativeCore/TSFilter.h       ../Native2.0/NativeCore/TSParser.h
Channel.o:../Native2.0/NativeCore/Bits.h 
Channel.o:../Native2.0/NativeCore/AVFormat/AACFormat.h        ../Native2.0/NativeCore/AVFormat/AC3Format.h 
Channel.o:../Native2.0/NativeCore/AVFormat/DTSFormat.h        ../Native2.0/NativeCore/AVFormat/EAC3Format.h 
Channel.o:../Native2.0/NativeCore/AVFormat/H264Format.h 	    ../Native2.0/NativeCore/AVFormat/LPCMFormat.h
Channel.o:../Native2.0/NativeCore/AVFormat/MediaVideoFormat.h ../Native2.0/NativeCore/AVFormat/MpegAudioFormat.h
Channel.o:../Native2.0/NativeCore/AVFormat/MpegVideoFormat.h  ../Native2.0/NativeCore/AVFormat/VC1Format.h
ChannelDataMng.o:../Native2.0/NativeCore/ATSCPSIParser.h  ../Native2.0/NativeCore/AVAnalyzer.h 
ChannelDataMng.o:../Native2.0/NativeCore/AVTrack.h 		  ../Native2.0/NativeCore/BlockBuffer.h 
ChannelDataMng.o:../Native2.0/NativeCore/ChannelScan.h    ../Native2.0/NativeCore/ChannelScanInc.h 
ChannelDataMng.o:../Native2.0/NativeCore/Demuxer.h        ../Native2.0/NativeCore/DVBPSIParser.h 
ChannelDataMng.o:../Native2.0/NativeCore/ESAnalyzer.h     ../Native2.0/NativeCore/GetAVInf.h         
ChannelDataMng.o:../Native2.0/NativeCore/NativeCore.h     ../Native2.0/NativeCore/NativeMemory.h 
ChannelDataMng.o:../Native2.0/NativeCore/PSBuilder.h      ../Native2.0/NativeCore/PSIParserConstData.h 
ChannelDataMng.o:../Native2.0/NativeCore/PSIParser.h      ../Native2.0/NativeCore/PSParser.h 
ChannelDataMng.o:../Native2.0/NativeCore/Remuxer.h        ../Native2.0/NativeCore/SectionData.h 
ChannelDataMng.o:../Native2.0/NativeCore/TSBuilder.h      ../Native2.0/NativeCore/TSCRC32.h 
ChannelDataMng.o:../Native2.0/NativeCore/TSFilter.h       ../Native2.0/NativeCore/TSParser.h
ChannelDataMng.o:../Native2.0/NativeCore/Bits.h 
ChannelDataMng.o:../Native2.0/NativeCore/AVFormat/AACFormat.h        ../Native2.0/NativeCore/AVFormat/AC3Format.h 
ChannelDataMng.o:../Native2.0/NativeCore/AVFormat/DTSFormat.h        ../Native2.0/NativeCore/AVFormat/EAC3Format.h 
ChannelDataMng.o:../Native2.0/NativeCore/AVFormat/H264Format.h 	    ../Native2.0/NativeCore/AVFormat/LPCMFormat.h
ChannelDataMng.o:../Native2.0/NativeCore/AVFormat/MediaVideoFormat.h ../Native2.0/NativeCore/AVFormat/MpegAudioFormat.h
ChannelDataMng.o:../Native2.0/NativeCore/AVFormat/MpegVideoFormat.h  ../Native2.0/NativeCore/AVFormat/VC1Format.h







