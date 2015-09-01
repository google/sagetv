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
# native/so/SageLinux Makefile - builds a shared library

# set system name (will be Linux or Darwin)
UNAME_S := $(shell uname -s)

JDK_HOME ?= /usr/local/j2sdk
#JAVA_ARCH ?= i386
JAVA_ARCH ?= amd64


CC=gcc
#CC=mipsel-unknown-linux-gnu-gcc
CFLAGS = -c -fPIC -I$(JDK_HOME)/include -I$(JDK_HOME)/include/linux -D_FILE_OFFSET_BITS=64
ifeq ($(UNAME_S),Darwin)
CFLAGS = -c -fPIC -I$(JDK_HOME)/include -I$(JDK_HOME)/include/darwin -D_FILE_OFFSET_BITS=64
endif
BINDIR=/usr/local/bin

OBJFILES=sage_Sage.o sage_MMC.o sage_VideoFrame.o sage_BasicVideoFrame.o

# Apple OS/X build only non X11 library for now
ifeq ($(UNAME_S),Darwin)
all: libSage.so
else
all: libSageX11.so libSage.so
endif

libSageX11.so: $(OBJFILES) sage_UIManagerX11.o
	$(CC) -shared -o libSageX11.so  $(OBJFILES) sage_UIManagerX11.o -L$(JDK_HOME)/jre/lib/$(JAVA_ARCH) -ldl -lX11 -ljawt 

libSage.so: $(OBJFILES) sage_UIManagerNOX11.o
	$(CC) -shared -o libSage.so  $(OBJFILES) sage_UIManagerNOX11.o -ldl

sage_UIManagerX11.o: sage_UIManager.c
	$(CC) $(CFLAGS) -c -o sage_UIManagerX11.o -DWITH_X11 sage_UIManager.c

sage_UIManagerNOX11.o: sage_UIManager.c
	$(CC) $(CFLAGS) -c -o sage_UIManagerNOX11.o sage_UIManager.c

clean:
	rm -f *.o libSage.so libSageX11.so
