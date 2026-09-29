#!/usr/bin/env bash
set -euo pipefail
cd /work/sagetv
bash ./gradlew --no-daemon clean
if [ -d build ]; then (cd build && bash clean.sh) || true; fi
rm -rf output
find native third_party -type f \( -name '*.o' -o -name '*.so' -o -name '*.so.*' \) -delete
