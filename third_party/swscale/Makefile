CC=gcc
CFLAGS = -D_FILE_OFFSET_BITS=64 -O2 -c -fpic
BINDIR=/usr/local/bin
LIBRARY=-lm
OBJFILES=swscale.o

libswscale.so: $(OBJFILES)
	$(CC) -shared -o libswscale.so $(OBJFILES) $(LIBRARY)
	strip --strip-unneeded libswscale.so

swscale.o: swscale.c swscale_template.c

clean:
	rm -f *.o libswscale.so *.c~ *.h~
