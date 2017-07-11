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
CFLAGS = -D_FILE_OFFSET_BITS=64 -O2 -c -fpic
BINDIR=/usr/local/bin
LIBRARY=-lm
OBJFILES=swscale.o

libswscale.so: $(OBJFILES)
	$(CC) -shared -o libswscale.so $(OBJFILES) $(LIBRARY)
	$(STRIP) --strip-unneeded libswscale.so

swscale.o: swscale.c swscale_template.c

clean:
	rm -f *.o libswscale.so *.c~ *.h~
