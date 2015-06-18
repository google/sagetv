JDK_HOME ?= /usr/local/j2sdk
#JAVA_ARCH ?= i386
JAVA_ARCH ?= amd64


CC=gcc
#CC=mipsel-unknown-linux-gnu-gcc
CFLAGS = -c -fPIC -I$(JDK_HOME)/include -I$(JDK_HOME)/include/linux -D_FILE_OFFSET_BITS=64
BINDIR=/usr/local/bin

OBJFILES=sage_Sage.o sage_MMC.o sage_VideoFrame.o sage_BasicVideoFrame.o

all: libSageX11.so libSage.so
#all: libSage.so

libSageX11.so: $(OBJFILES) sage_UIManagerX11.o
	$(CC) -shared -o libSageX11.so  $(OBJFILES) sage_UIManagerX11.o -L$(JDK_HOME)/jre/lib/$(JAVA_ARCH) -ldl -lX11 -ljawt 

libSage.so: $(OBJFILES) sage_UIManagerNOX11.o
	$(CC) -shared -o libSage.so  $(OBJFILES) sage_UIManagerNOX11.o -ldl

sage_UIManagerX11.o: sage_UIManager.c
	$(CC) $(CFLAGS) -c -o sage_UIManagerX11.o -DWITH_X11 sage_UIManager.c

sage_UIManagerNOX11.o: sage_UIManager.c
	$(CC) $(CFLAGS) -c -o sage_UIManagerNOX11.o sage_UIManager.c

clean:
	rm -f *.o libSage.so libSageX11.so
