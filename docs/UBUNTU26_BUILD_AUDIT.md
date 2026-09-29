# Ubuntu 26 Linux native build audit

Audited on Ubuntu 26.04 amd64 with GCC 15 and OpenJDK 11. Commands, ELF headers, dynamic dependencies, and symbols are retained under `output/test-results/elf`.

| Library | Source/build | Embedded third party | Dynamic dependencies | JNI/status/tests/changes |
|---|---|---|---|---|
| libSage.so | `native/so/SageLinux` | none | libc, libdl | JNI; PASS; fixed filesystem format width |
| libIVTVCapture.so | `native/so/IVTVCapture` | none | libc, pthread | JNI; PASS; typed callbacks and portable recursive mutex |
| libjtux.so | `third_party/jtux/native/so` | jtux source | libc, libnsl | JNI; PASS; fixed IPC/iovec 64-bit conversions |
| libPVR150Input.so | `native/so/PVR150Input` | none | libc | JNI; PASS |
| libDVBCapture.so | `native/so/DVBCapture2.0` | NativeCore/channel static archive | libc, libNativeCore | JNI; PASS; C23 prototypes and pthread entry ABI |
| libFirewireCapture.so | `native/so/FirewireCapture` | none | libraw1394, libavc1394, libiec61883 | JNI; PASS |
| libMPEGParser.so | `native/so/MPEGParser2.0` | TSnative/NativeCore | libc, libNativeCore, libTSnative | JNI; PASS; C23 prototypes |
| libHDHomeRunCapture.so | `native/so/HDHomeRun2.0` | bundled HDHR client/channel archive | libstdc++, libc, libNativeCore | JNI; PASS |
| libJavaRemuxer2.so | `native/dll/JavaRemuxer2` | NativeCore | libc, libNativeCore | JNI; PASS |
| tuner plugins | `native/so/*Tuning` | none | libc/FireWire as applicable | plugin ABI; PASS |
| libswscale.so | `third_party/swscale` | historical Sage scaler | libc | non-JNI; PASS; exported scaler API remains a collision risk limited to private name |
| libImageLoader.so | `third_party/SageTV-LGPL/imageload` | none | system libpng16, gif, jpeg, tiff; private libswscale | JNI; PASS; modern transforms, explicit ARGB conversion, setjmp containment, giflib 5 API |
| libFreetypeFontJNI.so | `native/crosslibs/Freetype` | none | system FreeType | JNI; PASS; explicit modern module headers |
| libav*-minimal.so | `third_party/ffmpeg` | old private FFmpeg | libc | non-JNI; PASS; disabled obsolete asm, fixed callbacks; retained for transcoder ABI |
| libMpeg2Transcoder.so | `native/so/Mpeg2Transcoder` | none | private libav*-minimal | JNI; PASS; fixed 64-bit handles and callback constness |

`libImageLoader.so` has a `NEEDED` entry for `libpng16.so.16`; `nm -D --defined-only` exposes no `png_*` implementation. PNG tests cover RGB, RGBA, palette, grayscale, grayscale-alpha, tRNS, 16-bit, a palette channel-logo analogue, deliberate prior global libpng loading, and truncation. Malformed input returns failure without terminating the process.

Warnings remain categorized in `output/logs/native-build.log`: mostly unused legacy variables, misleading indentation, deprecated private FFmpeg APIs, and bounded-string diagnostics. Compile-blocking ABI/type issues were fixed; warnings are not globally promoted to errors.
