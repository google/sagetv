
JDK_HOME ?= /usr/local/j2sdk

CC=g++
CXXFLAGS = -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux
BINDIR=/usr/local/bin

OBJFILES=../../dll/DirecTVSerialControl/DirecTVSerialControl.o ../../common/jni-util.o

libDirecTVSerialControl.so: $(OBJFILES)
	$(CC) -shared -Wall -o libDirecTVSerialControl.so $(OBJFILES)

clean:
	rm -f *.o libDirecTVSerialControl.so *.c~ *.h~ *.class ../../dll/DirecTVSerialControl/DirecTVSerialControl.o
