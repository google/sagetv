CC=gcc
FREETYPE2_DIR ?= /usr/include/freetype2
CFLAGS = -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -D_FILE_OFFSET_BITS=64 -I$(FREETYPE2_DIR)
BINDIR=/usr/local/bin

OBJFILES=sage_FreetypeFont.o

libFreetypeFontJNI.so: $(OBJFILES)
	$(CC) -shared -o libFreetypeFontJNI.so $(OBJFILES) -lfreetype
#	$(CC) -shared -W1 -o FreetypeFontJNI.dll $(OBJFILES) -lfreetype -lz

clean:
	rm -f *.o FreetypeFontJNI.dll libFreetypeFontJNI.so *.c~ *.h~
