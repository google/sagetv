#!/usr/bin/env bash
set -euo pipefail
cd /work/sagetv
mkdir -p output/test-results/elf
find build/so -type f -name '*.so*' | sort | while read -r so; do
  file "$so" | tee -a output/test-results/elf/file.txt
  file "$so" | grep -q 'ELF 64-bit LSB shared object'
  readelf -h "$so" > "output/test-results/elf/$(basename "$so").header"
  readelf -d "$so" > "output/test-results/elf/$(basename "$so").dynamic"
  nm -D "$so" > "output/test-results/elf/$(basename "$so").symbols"
  if LD_LIBRARY_PATH=build/so ldd "$so" | tee "output/test-results/elf/$(basename "$so").ldd" | grep -q 'not found'; then exit 1; fi
done
bash tests/linux-modern/validate-jni-exports.sh
bash tests/linux-modern/test-image-loader.sh
