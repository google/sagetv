# Ubuntu 26 development build

Docker is the only host prerequisite. The default is `ubuntu:26.04` with Ubuntu's OpenJDK 11.

Run `./sagetv-dev.sh all` for a clean image build, compilation, tests, smoke tests, package, and report. Other commands are `image`, `build`, `test`, `native-test`, `image-test`, `shell`, `clean`, `rebuild`, `package`, and `report`. The named container `opensagetv-ubuntu26-dev` bind-mounts the checkout at `/work/sagetv`, so source edits do not rebuild the image.

Artifacts are collected in `output/server`, `output/native`, `output/packages`, `output/logs`, and `output/test-results`. Run the server interactively from `output/server` with the same Java command used by `tests/linux-modern/smoke-test.sh`.

The native build uses Ubuntu libpng16, giflib, libjpeg-turbo, libtiff, FreeType, FireWire, X11, and audio libraries. SageTV's legacy minimal FFmpeg remains private because `libMpeg2Transcoder.so` uses its historical API; assembler is disabled because its pre-AVX inline assembly is rejected by current binutils.
