#Qian Zhang

NATIVE_CORE_SRC = ../NativeCore
NATIVE_CORE_LIB = ../../../lib/NativeCore

DEBUG_OPTION = -g -O0

CC=gcc
CFLAGS =-Wall $(DEBUG_OPTION) -fPIC -D_FILE_OFFSET_BITS=64 -DLinux -I$(NATIVE_CORE_SRC)
BINDIR=/usr/local/bin

all:dep_make PushTest

OBJFILES=PushTest.o

PushTest: PushTest.c
	$(CC) PushTest.c $(CFLAGS) -Wall -o PushTest libNativeCore.so  

		
dep_make: 
	$(MAKE) -C $(NATIVE_CORE_SRC)
	cp $(NATIVE_CORE_LIB)/libNativeCore.so libNativeCore.so


clean:
	rm -f *.o libNatievCore.so  *.c~ *.h~ *.map PushTest



