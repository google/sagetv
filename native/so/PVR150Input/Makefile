CC=gcc
CFLAGS = -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux
BINDIR=/usr/local/bin

OBJFILES=sage_PVR150Input.o

libPVR150Input.so: $(OBJFILES)
	$(CC) -shared -o libPVR150Input.so $(OBJFILES)

clean:
	rm -f *.o libPVR150Input.so *.c~ *.h~ *.class
