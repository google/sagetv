#!/usr/bin/env bash
set -euo pipefail
cd /work/sagetv
actual=output/test-results/actual-jni.txt
find build/so -type f -name '*.so*' -exec nm -D --defined-only {} + | awk '{print $3}' | grep '^Java_' | sort -u > "$actual"
printf '%s\n' \
 Java_sage_Sage_getFileSystemType \
 Java_sage_media_image_ImageLoader_createThumbnail \
 Java_sage_media_image_ImageLoader_loadScaledImageFromFile \
 Java_sage_media_image_ImageLoader_freeImage0 \
 Java_sage_media_image_ImageLoader_compressImageToFile \
 Java_sage_media_image_ImageLoader_loadImageDimensionsFromFile \
 Java_sage_media_image_ImageLoader_scaleRawImage | sort > output/test-results/expected-jni.txt
comm -23 output/test-results/expected-jni.txt "$actual" > output/test-results/missing-jni.txt
test ! -s output/test-results/missing-jni.txt
