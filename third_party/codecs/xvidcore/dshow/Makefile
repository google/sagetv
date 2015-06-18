##############################################################################
#
# Makefile for XviD DirectShow driver
#
# Adapted from XviD VFW driver makefile.
# Modified by : Peter Ross <pross@xvid.org>
#
# Requires GNU Make because of shell expansion performed at a bad time with
# other make programs (even using := variable assignments)
#
# $Id: Makefile,v 1.7 2008-11-27 11:57:51 Isibaar Exp $
##############################################################################

include sources.inc

##############################################################################
# DXTREE must point to the directx sdk root directory.
#
# if a release prior to "directx v9.0 sdk update (summer 2003)" is installed,
# uncomment the DXBASECLASSES=$(DXTREE)/Samples/MultiMedia/DirectShow/BaseClasses
##############################################################################
DXTREE=/c/DX90SDK
# DXTREE=/c/DXVCSDK
DXBASECLASSES=$(DXTREE)/Samples/C++/DirectShow/BaseClasses
# DXBASECLASSES=$(DXTREE)/Samples/MultiMedia/DirectShow/BaseClasses

MAKEFILE_PWD:=$(shell pwd)
LOCAL_XVID_SRCTREE:=$(MAKEFILE_PWD)/../src
LOCAL_XVID_BUILDTREE:=$(MAKEFILE_PWD)/../build/generic/=build

RM = rm -rf
WINDRES=windres

# Constants which should not be modified
# The `mingw-runtime` package is required when building with -mno-cygwin
CFLAGS += -mthreads
CFLAGS += -I$(SRC_DIR)/w32api -I$(LOCAL_XVID_SRCTREE)
CFLAGS += -mno-cygwin

CXXFLAGS +=-mthreads
CXXFLAGS += -DRELEASE \
        -I$(LOCAL_XVID_SRCTREE) \
        -I$(DXTREE)/Include \
        -I$(DXBASECLASSES) \
        -include $(DXTREE)/mingw_dshow_port.h
CXXFLAGS += -mno-cygwin

##############################################################################
# Optional Compiler options
##############################################################################

CFLAGS += -Wall
CFLAGS += -O2
CFLAGS += -fstrength-reduce
CFLAGS += -finline-functions
CFLAGS += -fgcse
CFLAGS += -ffast-math

CXXFLAGS += -O2

##############################################################################
# Compiler flags for linking stage
##############################################################################

#LDFLAGS +=

##############################################################################
# Rules
##############################################################################

OBJECTS = $(SRC_C:.c=.obj)
OBJECTS+= $(SRC_CPP:.cpp=.obj)
OBJECTS+= $(SRC_RES:.rc=.obj)

.SUFFIXES: .obj .rc .c

BUILD_DIR = =build
VPATH     = $(SRC_DIR):$(BUILD_DIR)

all: $(LIBSO)

$(BUILD_DIR):
	@echo "  D: $(BUILD_DIR)"
	@mkdir -p $(BUILD_DIR)

.rc.obj:
	@echo "  W: $(@D)/$(<F)"
	@mkdir -p $(BUILD_DIR)/$(@D)
	@$(WINDRES) \
		--include-dir=$(SRC_DIR) \
		--input-format=rc \
		--output-format=coff \
		-o $(BUILD_DIR)/$@ $<

.c.obj:
	@echo "  C: $(@D)/$(<F)"
	@mkdir -p $(BUILD_DIR)/$(@D)
	@$(CC) $(CFLAGS) -c -o $(BUILD_DIR)/$@ $<

.cpp.obj:
	@echo "  C: $(@D)/$(<F)"
	@mkdir -p $(BUILD_DIR)/$(@D)
	@$(CXX) $(CXXFLAGS) -c -o $(BUILD_DIR)/$@ $<

$(LIBSO): $(BUILD_DIR) $(OBJECTS)
	@echo "  L: $(@F)"
	@cp $(SRC_DIR)/xvid.ax.def $(BUILD_DIR)/xvid.ax.def
	@cd $(BUILD_DIR) && \
		$(CC) $(LDFLAGS) \
			-mno-cygwin -shared -Wl,-dll,--out-implib,$@.a,--enable-stdcall-fixup \
			-o $@ \
			$(OBJECTS) xvid.ax.def \
			-L$(DXTREE)/Lib -lstrmiids \
			$(DXBASECLASSES)/strmbase.lib \
			-luuid -lwinmm -lole32 -loleaut32 -lcomctl32 -lstdc++

clean:
	@echo "  Cl: Object files and target lib"
	@$(RM) $(BUILD_DIR)

install:

uninstall:
