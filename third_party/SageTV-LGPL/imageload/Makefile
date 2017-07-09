JDK_HOME ?= /usr/local/j2sdk

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
CFLAGS = -g -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -DO_BINARY=0 -Dstricmp=strcasecmp -I../../../native/include

OBJFILES=sage_media_image_ImageLoader.o imageload.o

libImageLoader.so: $(OBJFILES)
	$(CC) -shared -o libImageLoader.so $(OBJFILES) ../../codecs/giflib/lib/.libs/libgif.a ../../codecs/libpng/.libs/libpng12.a ../../codecs/jpeg-6b/libjpeg.a -lz ../../codecs/tiff/libtiff/.libs/libtiff.a -L../../swscale -lswscale

imagetest: $(OBJFILES)
	$(CC) -DO_BINARY=0 -g -o imagetest test.c $(OBJFILES) ../../codecs/giflib/lib/.libs/libgif.a ../../codecs/libpng/.libs/libpng12.a ../../codecs/jpeg-6b/libjpeg.a -lz ../../codecs/tiff/libtiff/.libs/libtiff.a -lm -L../../swscale -lswscale

clean:
	rm -f *.o libImageLoader.so *.c~ *.h~ *.class
