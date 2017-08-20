# Copyright 2015 The SageTV Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# native/so/HDHomeRun2.0 Makefile - builds a shared library - interface of libhdhomerun to Java part of SageTV

# set system name (will be Linux or Darwin)
UNAME_S := $(shell uname -s)

NATIVECORE_LIB=../../lib/NativeCore/libNativeCore.so
NATIVECORED_LIB=../../lib/NativeCore/libNativeCored.so
CHANNEL_LIB=../../lib/NativeCore/channel.a
CHANNELD_LIB=../../lib/NativeCore/channeld.a

NATIVECORE_SRC = ../../ax/Native2.0/NativeCore
NATIVECORE_INC = -I../../ax/Native2.0/NativeCore
CHANNEL_SRC = ../../ax/Channel-2
CHANNEL_INC = -I../../ax/Channel-2


HDHR_DIR = ../../../third_party/SiliconDust/libhdhomerun

HDHR_SRCS := $(strip $(shell grep -E '^LIBSRCS' $(HDHR_DIR)/Makefile))
HDHR_SRCS := $(filter-out LIBSRCS +=, $(HDHR_SRCS))
HDHR_OBJS := $(HDHR_SRCS:.c=.o)

HDHR_SRCS := $(patsubst %,$(HDHR_DIR)/%,$(HDHR_SRCS))
HDHR_OBJS := $(patsubst %,build/libhdhomerun/%,$(HDHR_OBJS))

HDHR_CFLAGS := -D_GNU_SOURCE -fPIC -Wall -O2 -Wmissing-declarations -Wmissing-prototypes -Wstrict-prototypes -Wpointer-arith
HDHR_CPPFLAGS :=
HDHR_LIBS := -lpthread

# this allows for alternate jdk locations
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

# -Wno-write-strings shouldn't be necessary, but gcc 4.2 is being a bitch
# about passing a const string to a function taking a non-const argument
# we really need to convert string args to const and re-enable this warning
CFLAGS = -fPIC -Wall -Wno-unknown-pragmas -Wno-write-strings $(NATIVECORE_INC) $(CHANNEL_INC) $(DEBUG_OPTION) -I$(HDHR_DIR) -I../../include -Istage/include -Istage/include/hdhomerun -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/linux -DSAGE_DTV_ONLY=1 -D_FILE_OFFSET_BITS=64 -DLinux
LDFLAGS =-Lstage/lib -L.
# Apple OS/X overwrite CFLAGS and LDFLAGS which are only working on Linux
ifeq ($(UNAME_S),Darwin)
CFLAGS = -fPIC -Wall -Wno-unknown-pragmas -Wno-write-strings $(NATIVECORE_INC) $(CHANNEL_INC) $(DEBUG_OPTION) -I$(HDHR_DIR) -I../../include -Istage/include -Istage/include/hdhomerun -c -fPIC -I$(JDK_HOME)/include/ -I$(JDK_HOME)/include/darwin -DSAGE_DTV_ONLY=1 -D_FILE_OFFSET_BITS=64 -DDarwin
LDFLAGS =-Lstage/lib -L. -L$(JDK_HOME)/lib -framework JavaVM
endif

STAGE_DIR=$(abspath ./stage)

LIBS = -lhdhomerun $(HDHR_LIBS) -lstdc++ -lNativeCore -lchannel

#TODO verfiy this setting of optimzed versus debug
#NOTE: not optiomized until stable
#OPT_FLAGS = -O3
OPT_FLAGS = -g -O0
CONFIG_OPTS = --prefix=\"$(STAGE_DIR)\"

# run "make DEBUG=1" to build for debugging
ifdef DEBUG
	LIBS += -ldmalloc
	OPT_FLAGS = -O0 -g
	CONFIG_OPTS += --enable-debug
endif

ifdef TARGET
	CONFIG_OPTS += --host=$(TARGET)
endif

all: dep_make libHDHomeRunCapture.so

foo:
	@echo TARGET=$(TARGET)
	@echo CROSS_PREFIX=$(CROSS_PREFIX)
	@echo CC=$(CC)
	$(MAKE) -e -C $(NATIVECORE_SRC) foo
	$(MAKE) -e -C $(CHANNEL_SRC) foo

OBJFILES=sage_HDHomeRun.o HDHRDevice.o DTVChannel.o SageTuner.o

DTVChannel.o: DTVChannel.cpp
	$(CC) $(CFLAGS) $(OPT_FLAGS) -o $@ $^

SageTuner.o: SageTuner.cpp
	$(CC) $(CFLAGS) $(OPT_FLAGS) -o $@ $^

%.o: %.cpp
	$(CC) $(CFLAGS) $(OPT_FLAGS) -o $@ $^

libHDHomeRunCapture.so: libNativeCore.so libchannel.a stage/lib/libhdhomerun.a $(OBJFILES)
	$(CC) -shared -Wall $(LDFLAGS) -o libHDHomeRunCapture.so $(OBJFILES) $(LIBS)

build:
	@mkdir build
	@touch build

$(HDHR_DIR)/configure:
	@sh -c "cd $(HDHR_DIR); ./bootstrap.sh"
	@touch $(HDHR_DIR)/configure

build/Makefile: build $(HDHR_DIR)/configure
	@sh -c "cd build; ../$(HDHR_DIR)/configure $(CONFIG_OPTS)"
	@touch build/Makefile

stage:
	@mkdir stage
	@touch stage

build/libhdhomerun:
	@mkdir -p build/libhdhomerun
	@touch build/libhdhomerun

build/libhdhomerun/%.o: $(HDHR_DIR)/%.c 
	$(CC) $(HDHR_CFLAGS) $(HDHR_CPPFLAGS) -o $@ -c $<

stage/lib:
	@mkdir -p stage/lib
	@touch stage/lib

stage/lib/libhdhomerun.a: $(HDHR_OBJS)
	ar cru $@ $(HDHR_OBJS)

dep_make: 
	$(MAKE) -C $(NATIVECORE_SRC)
	cp $(NATIVECORE_LIB) .
	$(MAKE) -C $(CHANNEL_SRC)
	cp $(CHANNEL_LIB) libchannel.a
	$(RANLIB) libchannel.a
	@mkdir -p build/libhdhomerun
	@mkdir -p stage/lib


clean:
	rm -f *.o *.so *.a *.c~ *.h~ *.map
	rm -rf build stage

install:
	cp libHDHomeRunCapture.so /opt/sagetv/server
	cp libNativeCore.so /opt/sagetv/server


