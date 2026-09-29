#!/usr/bin/env bash
set -euo pipefail
cd /work/sagetv
python3 tests/linux-modern/generate_png_fixtures.py
gcc -Wall -Wextra -Ithird_party/SageTV-LGPL/imageload \
  tests/linux-modern/image_loader_test.c -ldl -o output/test-results/image-loader-test
mapfile -t fixtures < <(find output/test-results/png-fixtures -name '*.png' | sort)
LD_LIBRARY_PATH=build/so output/test-results/image-loader-test \
  build/so/libImageLoader.so "${fixtures[@]}"
ldd build/so/libImageLoader.so | grep -E 'libpng16\.so\.16'
if nm -D --defined-only build/so/libImageLoader.so | grep -q ' T png_'; then
  echo "libImageLoader exports bundled libpng symbols" >&2; exit 1
fi
