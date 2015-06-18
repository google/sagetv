CC=mipsel-unknown-linux-gnu-gcc
CFLAGS = -c -fPIC -I$(JDK_HOME)/include -I$(JDK_HOME)/include/linux
BINDIR=/usr/local/bin

OBJFILES=sage_CVMUtils.o

libCVMUtils.so: $(OBJFILES)
	$(CC) -shared -W1 -o libCVMUtils.so  $(OBJFILES) 

clean:
	rm -f *.o libCVMUtils.so

