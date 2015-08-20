

===================================================================
  3GPP AMR Wideband Floating-point Speech Codec
===================================================================

This readme.txt shortly explains the compilation and use of the AMR-WB floating 
point C-code. The package contains C-source files for the AMR floating-point 
speech encoder and fast fixed-point speech decoder. The fast fixed-point 
speech decoder is bit-exact with 3GPP TS 26.173 fixed-point speech decoder 
version 5.4.0.

COMPILING THE SOFTWARE
======================

By default the encoder output and decoder input are formatted according to the
MIME file storage format used e.g. by the MMS service. The specification of
this format can be found in RFC 3267 "Real-Time Transport Protocol(RTP) Payload
Format and File Storage Format for the Adaptive Multi-Rate (AMR) and Adaptive
Multi-Rate Wideband (AMR-WB) Audio Codecs", sections 5.1 and 5.3.

You can compile a package with an output compatible with the AMR-WB IF2 format
specified in the 3GPP specification TS 26.201 "Speech Codec speech processing
functions; AMR Wideband Speech Codec; Frame Structure" by defining "IF2" during
compiling.

File typedef.h has definitions of numeric datatypes. You must change 
these according your platform. The default datatypes are defined for IA-32 
architecture.

When compiling the encoder, you have to compile the files:

enc_acelp.c
enc_dtx.c
enc_gain.c
enc_if.c
enc_lpc.c
enc_main.c
enc_rom.c
enc_util.c
encoder.c
if_rom.c

enc.h
enc_acelp.h
enc_dtx.h
enc_gain.h
enc_if.h
enc_lpc.h
enc_main.h
enc_util.h
if_rom.c

When compiling the decoder, you have to compile files:

dec_acelp.c
dec_dtx.c
dec_gain.c
dec_if.c
dec_lpc.c
dec_main.c
dec_rom.c
dec_util.c
decoder.c
if_rom.c

dec.h
dec_acelp.h
dec_dtx.h
dec_gain.h
dec_if.h
dec_lpc.h
dec_main.h
dec_util.h
if_rom.h

RUNNING THE SOFTWARE
====================

Usage of the "encoder" program is as follows:

encoder [-dtx] mode speech_file bitstream_file
or
encoder [-dtx] -modefile=mode_file speech_file bitstream_file

<mode> :  (0)  (1)   (2)   (3)   (4)   (5)   (6)   (7)   (8)
bitrate: 6.60 8.85 12.65 14.25 15.85 18.25 19.85 23.05 23.85 kbit/s

[mode_file] is optional and the format is the same as in the mode file 
of the corresponding 3GPP TS 26.173 fixed-point C-code v.5.4.0. 
The file is an ascii-file containing one mode per line.

Usage of the "decoder" program is as follows: 

decoder speech_file synthesis_file


HISTORY
=======

v. 5.0.0	05.03.02
v. 5.1.0	18.02.03



