
JAVA_HOME ?= /usr/local/j2sdk

CC=gcc
CFLAGS = -c -fPIC -I$(JAVA_HOME)/include/ -I$(JAVA_HOME)/include/linux -D_FILE_OFFSET_BITS=64

all: PVR150Tuner.so

PVR150Tuner.so: PVR150Tuner.o
	$(CC) -o PVR150Tuner.so -shared PVR150Tuner.o

PVR150Tuner.o: PVR150Tuner.c
	$(CC) $(CFLAGS) -Wall -O2 -c PVR150Tuner.c

clean:
	rm -f *.o PVR150Tuner.so *.c~ *.h~
