CC=gcc
CFLAGS = -c -fPIC -I/usr/local/j2sdk1.4.2_06/include/ -I/usr/local/j2sdk1.4.2_06/include/linux -D_FILE_OFFSET_BITS=64

all: LIRCTuner.so

LIRCTuner.so: LIRCTuner.o
	$(CC) -o LIRCTuner.so -shared LIRCTuner.o

LIRCTuner.o: LIRCTuner.c
	$(CC) -Wall -O2 -c LIRCTuner.c

clean:
	rm -f *.o LIRCTuner.so *.c~ *.h~
