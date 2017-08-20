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

CFLAGS = -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux
BINDIR=/usr/local/bin

OBJFILES=sage_PVR150Input.o

libPVR150Input.so: $(OBJFILES)
	$(CC) -shared -o libPVR150Input.so $(OBJFILES)

clean:
	rm -f *.o libPVR150Input.so *.c~ *.h~ *.class
