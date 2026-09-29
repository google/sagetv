#!/usr/bin/env bash
set -euo pipefail
cd /work/sagetv
mkdir -p output/logs output/native output/server output/packages output/test-results
bash ./gradlew --no-daemon sageJar 2>&1 | tee output/logs/java-build.log
(cd build && bash buildso.sh) 2>&1 | tee output/logs/native-build.log
mkdir -p build/elf
cp "$(command -v ffmpeg)" build/elf/ffmpeg
cp "$(command -v jpegtran)" build/elf/jpegtran
(cd build && bash copyserverfiles.sh) 2>&1 | tee output/logs/server-assembly.log
test -f build/serverrelease/Sage.jar -a -x build/serverrelease/ffmpeg -a -x build/serverrelease/jpegtran
cp -a build/so/. output/native/
cp -a build/serverrelease/. output/server/
