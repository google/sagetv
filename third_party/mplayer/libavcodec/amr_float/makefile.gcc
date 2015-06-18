#
#
#
#      File             : makefile.lnx
#      Purpose          : gcc makefile for AMR floating point
#                       ; standalone encoder/decoder program
#
#                             make [MODE=DEBUG] [VAD=VAD#] [target [target...]]
#
#                         Important targets are:
#                             default           (same as not specifying a
#                                                target at all)
#                                               remove all objects and libs;
#                                               build libraries; then build
#                                               encoder & decoder programs
#                             depend            make new dependency list
#                             clean             Remove all object/executable/
#                                               verification output files
#                             clean_depend      Clean dependency list
#                             clean_all         clean & clean_depend & rm *.a
#
#
#                         Specifying MODE=DEBUG compiles in debug mode
#                         (libaries compiled in DEBUG mode will be linked)
#
#                         Specifying VAD=VAD1 compiles VAD option 1
#                         Specifying VAD=VAD2 compiles VAD option 2
#
#                         The makefile uses the GNU C compiler (gcc); change
#                         the line CC=gcc below if another compiler is desired
#                         (CFLAGSxxx probably must be changed then as well)
#                         
#
# $Id $
#
#****************************************************************

CC = gcc
MAKEFILENAME = makefile

# Use MODE=DEBUG for debuggable library (default target builds both)
#
# default mode = NORM ==> no debug, no wmops
#
MODE=NORM

# Use VAD=VAD1 for VAD option 1, or VAD=VAD2 for VAD option 2
#
# default mode = VAD1
#
VAD=VAD1

#
# compiler flags (for normal, DEBUG compilation)
#
CFLAGS_NORM  = -O4 -DETSI
CFLAGS_DEBUG = -g -DDEBUG -DETSI

CFLAGS = -Wall  -I. $(CFLAGS_$(MODE)) -D$(VAD)
CFLAGSDEPEND = -MM $(CFLAGS)                    # for make depend


TMP=$(MODE:NORM=)
TMP2=$(TMP:DEBUG=_debug)


#
# source/object files
#
ENCODER_SRCS=encoder.c sp_enc.c interf_enc.c
DECODER_SRCS=decoder.c sp_dec.c interf_dec.c

ENCODER_OBJS=$(ENCODER_SRCS:.c=.o) 
DECODER_OBJS=$(DECODER_SRCS:.c=.o)

ALL_SRCS=$(ENCODER_SRCS) $(DECODER_SRCS)

#
# default target: build standalone speech encoder and decoder
#
default: clean_all encoder decoder


encoder: $(ENCODER_OBJS)
	$(CC) -o encoder $(CFLAGS) $(ENCODER_OBJS) $(LDFLAGS) -lm

decoder: $(DECODER_OBJS)
	$(CC) -o decoder $(CFLAGS) $(DECODER_OBJS) $(LDFLAGS)

#
# how to compile a .c file into a .o
#
.SUFFIXES: .c .h .o
.c.o:
	$(CC) -c $(CFLAGS) $<

#
# make / clean dependency list
#
depend:
	$(MAKE) -f $(MAKEFILENAME) $(MFLAGS) $(MAKEDEFS) clean_depend
	$(CC) $(CFLAGSDEPEND) $(ALL_SRCS) >> $(MAKEFILENAME)

clean_depend:
	chmod u+w $(MAKEFILENAME)
	(awk 'BEGIN{f=1}{if (f) print $0}/^\# DO NOT DELETE THIS LINE -- make depend depends on it./{f=0}'\
	    < $(MAKEFILENAME) > .depend && \
	mv .depend $(MAKEFILENAME)) || exit 1;

#
# remove object/executable files
#
clean:
	rm -f *.o core

clean_all: clean
	rm -f encoder decoder

# DO NOT DELETE THIS LINE -- make depend depends on it.
encoder.o: encoder.c typedef.h interf_enc.h sp_enc.h
sp_enc.o: sp_enc.c sp_enc.h typedef.h rom_enc.h
interf_enc.o: interf_enc.c sp_enc.h typedef.h interf_rom.h rom_dec.h
decoder.o: decoder.c interf_dec.h typedef.h
sp_dec.o: sp_dec.c sp_dec.h rom_dec.h typedef.h
interf_dec.o: interf_dec.c typedef.h sp_dec.h interf_rom.h rom_dec.h
