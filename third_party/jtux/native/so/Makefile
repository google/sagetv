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

CFLAGS = -c -DLINUX -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -Wimplicit -Wstrict-prototypes -Wall -Wno-unknown-pragmas -Iinclude

OBJFILES=jtux_clock.o jtux_file.o jtux_sysvipc.o jtux_dir.o jtux_network.o jtux_process.o jtux_util.o

libjtux.so: $(OBJFILES)
	$(CC) -shared -lnsl -o libjtux.so $(OBJFILES)

clean:
	rm -f *.o libjtux.so *.c~ *.h~ *.class
