
#Original Qian Zhang, 11/7/06
#makefile for linux

CC=gcc
BINDIR=/usr/local/bin
CFLAG = -fPIC -D_FILE_OFFSET_BITS=64 -Wall -Wno-missing-braces

TSNATIVE_INC = -I../../ax/TSnative
TSNATIVE_LIB=../../lib/TSnative/TSnative.a
TSNATIVED_LIB=../../lib/TSnative/TSnatived.a
TSNATIVE_SRC = ../../ax/TSnative

DEBUG_OPTION = -g -O0 -DDEBUGDVB


all:dep_make tsplt
tool:tsplt
debug:tspltd
	
tsplt:splt.c $(SRC)
	$(CC)  -o tsplt splt.c $(SRC) $(TSNATIVE_LIB) $(TSNATIVE_INC) $(CFLAG)

tspltd:splt.c $(SRC)
	$(CC)  -g -O0  -o tspltd splt.c $(SRC) $(TSNATIVED_LIB) $(TSNATIVE_INC) $(CFLAG)
		
dep_make: 
	$(MAKE) -C $(TSNATIVE_SRC)
	
clean:
	rm -f *.o *.c~ *.h~ tsplt
 

