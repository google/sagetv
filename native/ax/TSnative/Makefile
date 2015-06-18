##Original Qian Zhang, 11/7/06.
##Modified 23/02/07 by Jean-Francois Thibert
#makefile for linux

#Select debug you want
DEBUG=-DDEBUG_LINUX
#DEBUG=-DDEBUG_LINUX -O0

#CC=gcc
#CC=mipsel-unknown-linux-gnu-gcc
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

# allow the jdk to reside elsewhere, just export JAVA_HOME
JAVA_HOME ?= /opt/jdk1.5.0_02

#JSDK=-I/usr/local/j2sdk/include/ -I/usr/local/j2sdk/include/linux 
JSDK=-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux 
CFLAGS=-fPIC -D_FILE_OFFSET_BITS=64 -Wall -Wno-missing-braces $(JSDK) $(DEBUG)

TARGETDIR=../../lib/TSnative

SRCS=TSSplitter.c TSBuilder.c TSParser.c PSBuilder.c PSParser.c AVInfRead.c AVUtility.c SIParser.c ConstData.c
OBJS= $(addsuffix .o, $(basename $(SRCS)))

all:  $(TARGETDIR)/libTSnative.so
debug: $(TARGETDIR)/libTSnatived.so

foo:
	@echo TARGET=$(TARGET)
	@echo CROSS_PREFIX=$(CROSS_PREFIX)
	@echo CC=$(CC)

$(TARGETDIR):
	mkdir -p $(TARGETDIR)
	touch $(TARGETDIR)

$(TARGETDIR)/libTSnative.so: $(TARGETDIR) $(OBJS)
	$(CC) -shared -Wl,-Map=libTSnative.map -o $(TARGETDIR)/libTSnative.so $(OBJS)

$(TARGETDIR)/libTSnatived.so: $(TARGETDIR) $(OBJS)
	$(CC) -g -O0 -shared -o $(TARGETDIR)/libTSnative.so $(OBJS)
	
clean:
	rm -f *.o *.c~ *.h~ $(TARGETDIR)/libTSnative.so

depend:
	makedepend -Y -- $(CFLAGS) -- $(SRCS)


# DO NOT DELETE THIS LINE -- make depend depends on it.

TSSplitter.o: TSSplitter.h TSnative.h TSParser.h SIParser.h TSBuilder.h
TSSplitter.o: PSParser.h AVUtility.h PSBuilder.h
TSBuilder.o: TSBuilder.h TSnative.h
TSParser.o: TSParser.h TSnative.h AVUtility.h SIParser.h
PSBuilder.o: TSnative.h PSBuilder.h AVUtility.h
PSParser.o: PSParser.h TSnative.h AVUtility.h
AVInfRead.o: AVUtility.h TSnative.h TSParser.h PSParser.h AVInfRead.h
AVUtility.o: TSnative.h AVUtility.h
SIParser.o: AVUtility.h TSnative.h TSParser.h SIParser.h ConstData.h
ConstData.o: ConstData.h
