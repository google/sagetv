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
CFLAGS = -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -D_FILE_OFFSET_BITS=64

all: FirewireTuner.so

FirewireTuner.so: FirewireTuner.o
	$(CC) -o FirewireTuner.so -shared FirewireTuner.o -liec61883 -lraw1394 -lavc1394 -lrom1394

FirewireTuner.o: FirewireTuner.c
	$(CC) $(CFLAGS) -Wall -O2 -c FirewireTuner.c

clean:
	rm -f *.o FirewireTuner.so *.c~ *.h~
