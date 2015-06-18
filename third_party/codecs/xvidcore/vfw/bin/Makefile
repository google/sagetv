##############################################################################
#
# Makefile for XviD VFW driver
#
# Author : Milan Cutka <cutka at szm.sk>
# Modified by : Edouard Gomez <ed.gomez at free.fr>
#               Peter Ross <pross@xvid.org>
#
# Requires GNU Make because of shell expansion performed at a bad time with
# other make programs (even using := variable assignments)
#
# $Id: Makefile,v 1.7 2008-12-15 10:22:07 Isibaar Exp $
##############################################################################

include sources.inc

MAKEFILE_PWD:=$(shell pwd)
LOCAL_XVID_SRCTREE:=$(MAKEFILE_PWD)/../../src
LOCAL_XVID_BUILDTREE:=$(MAKEFILE_PWD)/../../build/generic/=build

RM = rm -rf
WINDRES=windres

# Constants which should not be modified
# The `mingw-runtime` package is required when building with -mno-cygwin
CFLAGS += -I$(SRC_DIR)/w32api -I$(LOCAL_XVID_SRCTREE)
CFLAGS += -mno-cygwin
CFLAGS += -D_WIN32_IE=0x0501

##############################################################################
# Optional Compiler options
##############################################################################

CFLAGS += -Wall
CFLAGS += -O2
CFLAGS += -fstrength-reduce
CFLAGS += -finline-functions
CFLAGS += -fgcse
CFLAGS += -ffast-math

##############################################################################
# Compiler flags for linking stage
##############################################################################

# LDFLAGS +=

##############################################################################
# Rules
##############################################################################

OBJECTS = $(SRC_C:.c=.obj)
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

$(LIBSO): $(BUILD_DIR) $(OBJECTS)
	@echo "  L: $(@F)"
	@cp $(SRC_DIR)/driverproc.def $(BUILD_DIR)/driverproc.def
	@cd $(BUILD_DIR) && \
		$(CC) $(LDFLAGS) \
			-mno-cygwin -shared -Wl,-dll,--out-implib,$@.a,--enable-stdcall-fixup \
			-o $@ \
			$(OBJECTS) driverproc.def \
			-lgdi32 -lwinmm -lcomdlg32 -lcomctl32

clean:
	@echo "  Cl: Object files and target lib"
	@$(RM) $(BUILD_DIR)

install:
	@echo "  I: xvid.dll"
	@cp $(BUILD_DIR)/$(LIBSO) $(LIBSO)
	@rundll32.exe setupapi,InstallHinfSection DefaultInstall 132 ./xvid.inf
	@rm $(LIBSO)

uninstall:
	@rundll32.exe setupapi,InstallHinfSection Remove_XviD 132 ./xvid.inf
