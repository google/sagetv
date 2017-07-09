JDK_HOME ?= /usr/local/j2sdk

ifdef TARGET
        CROSS_PREFIX:=$(TARGET)-
else
        CROSS_PREFIX:=
endif
CC:=$(CROSS_PREFIX)gcc
CXX:=$(CROSS_PREFIX)g++
LD:=$(CROSS_PREFIX)ld
AR:=$(CROSS_PREFIX)ar
RANLIB:=$(CROSS_PREFIX)ranlib
STRIP:=$(CROSS_PREFIX)strip

CXXFLAGS = -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux
BINDIR=/usr/local/bin

OBJFILES=../../dll/DirecTVSerialControl/DirecTVSerialControl.o ../../common/jni-util.o

libDirecTVSerialControl.so: $(OBJFILES)
	$(CXX) -shared -Wall -o libDirecTVSerialControl.so $(OBJFILES)

clean:
	rm -f *.o libDirecTVSerialControl.so *.c~ *.h~ *.class ../../dll/DirecTVSerialControl/DirecTVSerialControl.o ../../common/jni-util.o
