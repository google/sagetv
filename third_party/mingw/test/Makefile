

CC=i686-mingw32-gcc

all: pthread-test

pthread-test: pthread-test.c
	$(CC) -DPTW32_STATIC_LIB -o pthread-test pthread-test.c -lpthreadGC2 -lws2_32

clean:
	rm -f *.exe
