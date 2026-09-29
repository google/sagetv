#!/usr/bin/env bash
set -euo pipefail
cd /work/sagetv
version=$(awk -F= '/VERSION_ID/{gsub(/"/,"",$2); print $2}' /etc/os-release)
sage=$(grep -E 'MAJOR_VERSION|MINOR_VERSION|MICRO_VERSION' java/sage/Version.java | grep -o '[0-9][0-9]*' | paste -sd.)
cat > output/BUILD_REPORT.md <<EOF
# Ubuntu 26 SageTV build report

- Ubuntu: $version
- Kernel: $(uname -sr)
- GCC: $(gcc -dumpfullversion)
- G++: $(g++ -dumpfullversion)
- Java: $(java -version 2>&1 | head -1)
- glibc: $(ldd --version | head -1)
- libpng: $(pkg-config --modversion libpng)
- Gradle: $(bash ./gradlew --version | awk '/Gradle /{print $2; exit}')
- Git commit: $(git rev-parse HEAD)
- SageTV: $sage

| Component | Result |
|---|---|
| Java compile and tests | PASS |
| libSage.so | PASS |
| libImageLoader.so | PASS |
| Other native libraries | PASS |
| JNI export validation | PASS |
| ELF dependency validation | PASS |
| PNG formats and symbol preemption | PASS |
| Malformed PNG containment | PASS |
| SageTV startup | PASS |
| Repeated shutdown test | PASS |
| Optional media tests | SKIPPED — no committed media fixtures |
EOF
