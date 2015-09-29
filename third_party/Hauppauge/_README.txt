
------------
Note: you'll need to have the tool .exe's in your PATH.
------------

Easiest way to ensure the above is to launch the SDK's Command Prompt:

For SDK6.1:
It can be found from the Start menu:
   Start -> All Programs -> Microsoft Windows SDK v6.1 -> CMD Shell

First, set the build environment:
  Type "setenv /Release /x86 /xp"


Locate file HCWIRBlaster.dll, then
  type: "cd <path to HCWIRBlaster.dll>"

------------

To build HCWIRBlaster.lib:

Run:
dumpbin /exports HCWIRBlaster.dll > HCWIRBlaster.def

Open HCWIRBlaster.def in some text editor and edit it to contain only the names of exported functions in form of:

	EXPORTS
	function_1_name
	function_2_name
	function_3_name
	...

Then run:
lib /def:HCWIRBlaster.def /out:hcwIRBlast.lib /machine:x86

This creates library hcwIRBlast.lib and object hcwIRBlast.exp,
which should be placed in the directory with HCWIRBlaster.dll.


-----
todo:
   ? Should we try to integrate this into the build system ?


