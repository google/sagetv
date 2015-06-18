#===================================================================
#  AMR WB Floating-point Speech Codec  
#===================================================================
#
#      File             : makefile.gcc
#      Purpose          : gcc makefile for AMR-WB floating point
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
# default mode = NORM ==> no debug
#
MODE=NORM

#
# compiler flags (for normal, DEBUG compilation)
#
CFLAGS_NORM  = -O4 
CFLAGS_DEBUG = -g -DDEBUG

CFLAGS = -Wall  -I. $(CFLAGS_$(MODE))
CFLAGSDEPEND = -MM $(CFLAGS)                    # for make depend


TMP=$(MODE:NORM=)
TMP2=$(TMP:DEBUG=_debug)


#
# source/object files
#
ENCODER_SRCS=enc_acelp.c enc_dtx.c enc_gain.c enc_if.c enc_lpc.c enc_main.c enc_rom.c enc_util.c encoder.c if_rom.c
DECODER_SRCS=dec_acelp.c dec_dtx.c dec_gain.c dec_if.c dec_lpc.c dec_main.c dec_rom.c dec_util.c decoder.c if_rom.c

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
enc_acelp.o: enc_acelp.c typedef.h enc_util.h
enc_dtx.o: enc_dtx.c typedef.h enc_lpc.h enc_util.h
enc_gain.o: enc_gain.c typedef.h enc_util.h
enc_if.o: enc_if.c  enc_if.h if_rom.h enc.h
enc_lpc.o: enc_lpc.c typedef.h enc_util.h
enc_main.o: enc_main.c enc_dtx.h enc_acelp.h enc_lpc.h enc_main.h enc_gain.h enc_util.h
enc_rom.o: enc_rom.c typedef.h
enc_util.o: enc_util.c typedef.h enc_main.h enc_lpc.h
encoder.o: encoder.c typedef.h enc_if.h
if_rom.o: if_rom.c typedef.h
dec_acelp.o: dec_acelp.c typedef.h dec_util.h
dec_dtx.o: dec_dtx.c typedef.h dec_dtx.h dec_lpc.h dec_util.h
dec_gain.o: dec_gain.c typedef.h dec_util.h
dec_if.o: dec_if.c typedef.h dec_if.h if_rom.h dec.h
dec_lpc.o: dec_lpc.c typedef.h dec_util.h
dec_main.o: dec_main.c typedef.h dec_main.h dec_dtx.h dec_acelp.h dec_gain.h dec_lpc.h dec_util.h
dec_rom.o: dec_rom.c typedef.h
dec_util.o: dec_util.c typedef.h dec_main.h dec_lpc.h
decoder.o: decoder.c typedef.h dec_if.h
