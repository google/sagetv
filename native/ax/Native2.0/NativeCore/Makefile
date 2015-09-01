# Copyright 2015 The SageTV Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# native/ax/Native2.0/NativeCore Makefile - builds a shared library
# Original Qian Zhang, 6/1/09. makefile for linux

# set system name (will be Linux or Darwin)
UNAME_S := $(shell uname -s)

#Select debug you want
DEBUG=-DDEBUG_LINUX
CPU_TUNE=
OS=-DLinux
ifeq ($(UNAME_S),Darwin)
OS=-DDarwin
endif

#CC=gcc
#CC=mipsel-unknown-linux-gnu-gcc
BINDIR=/usr/local/bin

ifdef TARGET
	CROSS_PREFIX:=$(TARGET)-
	CPU_TUNE:=-DMINI_PVR
	#CPU_TUNE:=-mno-long-calls -mgpopt -ffast-math -funswitch-loops -mno-check-zero-division -DMINI_PVR
else
	CROSS_PREFIX:=
endif
CC:=$(CROSS_PREFIX)gcc
LD:=$(CROSS_PREFIX)ld
AR:=$(CROSS_PREFIX)ar
RANLIB:=$(CROSS_PREFIX)ranlib
STRIP:=$(CROSS_PREFIX)strip

#TODO why do we need here JAVA_HOME? Don't see usage and if it should be JDK_HOME
# allow the jdk to reside elsewhere, just export JAVA_HOME
JAVA_HOME ?= /opt/jdk1.5.0_02
#JSDK=-I/usr/local/j2sdk/include/ -I/usr/local/j2sdk/include/linux
JSDK=-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux

#CFLAGS=-fPIC -D_FILE_OFFSET_BITS=64 -Wall -Wno-missing-braces $(DEBUG) $(OS)
CFLAGS= -O3 -fPIC -D_FILE_OFFSET_BITS=64 -finline-functions -Wall -Wno-missing-braces -DLinux $(DEBUG) $(OS) $(CPU_TUNE)

SRCS=ATSCPSIParser.c AVAnalyzer.c AVTrack.c Bits.c BlockBuffer.c ChannelScan.c Demuxer.c DVBPSIParser.c ESAnalyzer.c GetAVInf.c NativeCore.c \
     NativeMemory.c PSBuilder.c PSIParser.c PSIParserConstData.c PSParser.c Remuxer.c SectionData.c TSBuilder.c TSCRC32.c TSFilter.c TSParser.c \
	 ScanFilter.c TSInfoParser.c TSChannelParser.c TSEPGParser.c\
     AVFormat/AACFormat.c AVFormat/AC3Format.c AVFormat/DTSFormat.c AVFormat/H264Format.c AVFormat/LPCMFormat.c AVFormat/MpegAudioFormat.c \
     AVFormat/MpegVideoFormat.c AVFormat/VC1Format.c AVFormat/EAC3Format.c AVFormat/MpegVideoFrame.c AVFormat/Subtitle.c

TARGETDIR=../../../lib/NativeCore
ifdef DEV_TARGET_LIB
TARGETDIR:=$(DEV_TARGET_LIB)
endif

OBJS= $(addsuffix .o, $(basename $(SRCS)))
INCS= $(addsuffix .h, $(basename $(SRCS)))

all:  $(TARGETDIR)/libNativeCore.so
debug: $(TARGETDIR)/libNativeCored.so

foo:
	@echo TARGET=$(TARGET)
	@echo CROSS_PREFIX=$(CROSS_PREFIX)
	@echo CC=$(CC)

$(TARGETDIR):
	mkdir -p $(TARGETDIR)
	touch $(TARGETDIR)

$(TARGETDIR)/libNativeCore.so: $(TARGETDIR) $(OBJS)
ifeq ($(UNAME_S),Darwin)
	$(CC) -shared -o $(TARGETDIR)/libNativeCore.so $(OBJS) $(CPU_TUNE)
else
	$(CC) -shared -Wl,-Map=libNativeCore.map -o $(TARGETDIR)/libNativeCore.so $(OBJS) $(CPU_TUNE)
endif

$(TARGETDIR)/libNativeCored.so: $(TARGETDIR) $(OBJS)
	$(CC) -shared -o $(TARGETDIR)/libNativeCored.so $(OBJS) $(CPU_TUNE)

clean:
	rm -f *.o *.map *.c~ *.h~ $(TARGETDIR)/libNativeCore.so $(TARGETDIR)/libNativeCored.so AVFormat/*.o AVFormat/*.c~ AVFormat/*.h~
	rmdir $(TARGETDIR) || true # only remove if empty, don't fail if non-empty
	rmdir ../../../lib || true # only remove if empty, don't fail if non-empty

depend:
	makedepend -Y -- $(CFLAGS) -- $(SRCS)

# DO NOT DELETE THIS LINE -- make depend depends on it.

#$(OBJS): $(SRCS) $(INCS) NativeCore.h TSParser.h Demuxer.h Remuxer.h
NativeCore.o: NativeCore.h
TSFilter.o: TSFilter.h NativeCore.h
TSParser.o: TSParser.h NativeCore.h ESAnalyzer.h
PSParser.o: PSParser.h NativeCore.h ESAnalyzer.h
TSInfoParser.o: TSInfoParser.h TSFilter.h
TSChannelParser.o: TSChannelParser.h TSFilter.h
TSEPGParser.o: TSEPGParser.h TSFilter.h
ATSCPSIParser.o: ATSCPSIParser.h NativeCore.h TSParser.h  TSFilter.h
DVBPSIParser.o: DVBPSIParser.h NativeCore.h TSParser.h  TSFilter.h
PSIParser.o: PSIParser.h NativeCore.h  TSParser.h  TSFilter.h
PSIParserConstData.o: PSIParserConstData.h NativeCore.h
AVAnalyzer.o: AVAnalyzer.h NativeCore.h TSParser.h
AVTrack.o: AVTrack.h NativeCore.h TSParser.h ESAnalyzer.h
ESAnalyzer.o:  ESAnalyzer.h NativeCore.h  TSFilter.h
BlockBuffer.o: BlockBuffer.h NativeCore.h TSParser.h
PSBuilder.o: PSBuilder.h NativeCore.h ESAnalyzer.h
TSBuilder.o: TSBuilder.h NativeCore.h ESAnalyzer.h
Demuxer.o: Demuxer.h NativeCore.h TSParser.h  TSFilter.h ESAnalyzer.h AVTrack.h
Remuxer.o: Remuxer.h NativeCore.h Demuxer.h  TSParser.h  TSFilter.h ESAnalyzer.h AVTrack.h
ChannelScan.o: ChannelScan.h NativeCore.h TSParser.h
GetAVInf.o: GetAVInf.h NativeCore.h   TSParser.h  TSFilter.h PSBuilder.h
SectionData.o: SectionData.h NativeCore.h
TSCRC32.o:  TSCRC32.h NativeCore.h
Bits.o: Bits.h NativeCore.h
ScanFilter.o: ScanFilter.h NativeCore.h ChannelScan.h
NativeMemory.o: NativeMemory.h NativeCore.h
AVFormat/AACFormat.o:  AVFormat/AACFormat.h NativeCore.h
AVFormat/AC3Format.o:  AVFormat/AC3Format.h NativeCore.h
AVFormat/EAC3Format.o:  AVFormat/EAC3Format.h NativeCore.h
AVFormat/DTSFormat.o:  AVFormat/DTSFormat.h NativeCore.h
AVFormat/H264Format.o: AVFormat/H264Format.h NativeCore.h
AVFormat/LPCMFormat.o: AVFormat/LPCMFormat.h NativeCore.h
AVFormat/MpegAudioFormat.o: AVFormat/MpegAudioFormat.h NativeCore.h
AVFormat/MpegVideoFormat.o: AVFormat/MpegVideoFormat.h NativeCore.h
AVFormat/VC1Format.o: AVFormat/VC1Format.h NativeCore.h
AVFormat/Subtitle.o: AVFormat/Subtitle.h NativeCore.h
AVFormat/MpegVideoFrame.o: AVFormat/MpegVideoFrame.h NativeCore.h
