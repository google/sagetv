# PC
#CC=gcc
#CXX=g++
#CFLAGS = -DLINUX -DFIXEDPOINT -D_FILE_OFFSET_BITS=64 -Os -c -fpic
#CXXFLAGS = -DLINUX -DFIXEDPOINT -D_FILE_OFFSET_BITS=64 -Os -c -fpic
# 8634
CC=mipsel-linux-gcc
CXX=mipsel-linux-g++
CFLAGS = -DLINUX -DFIXEDPOINT -ffast-math -msoft-float -D_FILE_OFFSET_BITS=64 -Os -c -fpic
CXXFLAGS = -DLINUX -DFIXEDPOINT -ffast-math -msoft-float -D_FILE_OFFSET_BITS=64 -Os -c -fpic

BINDIR=/usr/local/bin
LIBRARY=-lm
OBJFILES=BeatDetect.o Expr.o PerFrameEqn.o PresetLoader.o \
BuiltinFuncs.o Func.o PerPixelEqn.o PresetMerge.o \
BuiltinParams.o InitCond.o PerPointEqn.o TimeKeeper.o \
CustomShape.o PCM.o Preset.o VisEngine.o \
CustomWave.o Param.o PresetChooser.o timer.o \
Eval.o Parser.o PresetFrameIO.o wipemalloc.o

libVisEngine.so: $(OBJFILES)
	$(CC) -W1 -shared -o libVisEngine.so $(OBJFILES) $(LIBRARY)
	mipsel-linux-strip --strip-unneeded libVisEngine.so

VisTest: libVisEngine.so VisTest.o
	$(CXX) -W1 -o VisTest -msoft-float -L. -lVisEngine VisTest.o -lm $(LIBRARY)

clean:
	rm -f *.o libVisEngine.so *.c~ *.h~ *.hpp~ *.cpp~
