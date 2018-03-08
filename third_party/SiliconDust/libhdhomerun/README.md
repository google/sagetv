Copyright © 2005-2017 Silicondust USA Inc. <www.silicondust.com>.

This library implements the libhdhomerun protocol for use with Silicondust HDHomeRun TV tuners.

To compile simply "make" - this will compile both the library and the hdhomerun_config command line
utility suitable for sending commands or scripting control of a HDHomeRun.

The top level API is hdhomerun_device - see hdhomerun_device.h for documentation.

Additional libraries required:
- pthread (osx, linux, bsd)
- iphlpapi (windows)
