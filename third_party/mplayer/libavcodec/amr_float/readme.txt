
===================================================================
  TS 26.104 
  R99	V3.4.0 2002-02
  REL-4 V4.4.0 2003-03
  REL-5 V5.1.0 2003-03
  3GPP AMR Floating-point Speech Codec  
===================================================================


This readme.txt shortly explains the compilation and use of the AMR floating 
point C-code. The package contains C-source files for the AMR floating-point 
speech encoder and optimized fixed-point speech decoder. The optimized 
fixed-point speech decoder is bit-exact with 3GPP TS 26.073 fixed-point 
speech decoder version 4.1.0.

COMPILING THE SOFTWARE
======================

By default the encoder output and decoder input are formatted according to the
MIME file storage format used e.g. by the MMS service. The specification of
this format can be found in RFC 3267 "Real-Time Transport Protocol(RTP) Payload
Format and File Storage Format for the Adaptive Multi-Rate (AMR) and Adaptive
Multi-Rate Wideband (AMR-WB) Audio Codecs", sections 5.1 and 5.3.

If you want to compile a package with an output compatible with the existing 
3GPP AMR fixed-point C-code and its file format, define "ETSI" 
during compiling (in the compiler's command line). Hence the output 
of the encoder and the input of the decoder will use the ETSI "word"-
format (one bit per word) used by the official 3GPP AMR fixed-point codec.

If you want to compile a package with an output compatible with the AMR IF2
format of the 3GPP specification TS 26.101 "Mandatory speech processing
functions; AMR speech codec frame structure", defining "IF2" during compiling.

NOTE: When using the ETSI stream format the user must take care that the mode index
and the frame type are valid, else the decoder will crash. There is no error 
protection using this format.

For the VAD Option 1 define VAD=VAD1 and for the VAD Option 2 use
VAD=VAD2. The default is VAD1.

Makefiles for gcc and Microsoft C++ version 6.0 are included in this package.
Using MS VC++ makefile, command line is: 

nmake /f makefile.win32 CFG=ETSI VAD=VAD1

When compiling the encoder, you have to compile the files:

encoder.c
interf_enc.c
sp_enc.c

interf_enc.h
interf_rom.h
rom_enc.h
sp_enc.h
typedef.h

When compiling the decoder, you have to compile files:

sp_dec.c
decoder.c
interf_dec.c

interf_dec.h
interf_rom.h
rom_dec.h
sp_dec.h
typedef.h

RUNNING THE SOFTWARE
====================

Usage of the "encoder" program is as follows:

encoder [-dtx] mode speech_file bitstream_file
or
encoder [-dtx] -modefile=mode_file speech_file bitstream_file

<mode> = MR475, MR515, MR59, MR67, MR74, MR795, MR102 or MR122

[mode_file] is optional and the format is the same as in the mode file 
of the corresponding 3GPP TS 26.073 fixed-point C-code. The file is 
an ascii-file containing one mode per line.

Usage of the "decoder" program is as follows: 

decoder speech_file synthesis_file

HISTORY
=======

v. 3.0.0	24.8.00
v. 3.1.0	19.12.00
v. 4.0.0	19.12.00
R99   V. 3.2.0  13.06.01
REL-4 V. 4.1.0  13.06.01
R99   V. 3.3.0  01.09.01
REL-4 V. 4.2.0  01.09.01
R99   V. 3.4.0  08.02.02
REL-4 V. 4.3.0  08.02.02