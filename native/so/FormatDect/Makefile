
#Original Qian Zhang, 11/7/06
#makefile for linux

CC=gcc
BINDIR=/usr/local/bin

#JSDK=-I/usr/local/j2sdk/include/ -I/usr/local/j2sdk/include/linux 
JSDK=-I/opt/jdk1.5.0_02/include -I/opt/jdk1.5.0_02/include/linux
CFLAG = -D_FILE_OFFSET_BITS=64 -Wall -Wno-missing-braces

TSNATIVE_INC = -I../../ax/TSnative
TSNATIVE_LIB=../../lib/TSnative/TSnative.a
TSNATIVE_SRC = ../../ax/TSnative

all:dep_make formatect 

formatect: FormatDect.c
	$(CC)  -o formatect FormatDect.c $(TSNATIVE_LIB) $(TSNATIVE_INC) $(CFLAG)

	
dep_make: 
	$(MAKE) -C $(TSNATIVE_SRC)
	
clean:
	rm -f *.o *.c~ *.h~ formatdect
 
