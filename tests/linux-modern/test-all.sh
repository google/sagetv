#!/usr/bin/env bash
set -euo pipefail
cd /work/sagetv
bash ./gradlew --no-daemon test
bash tests/linux-modern/test-native.sh
bash tests/linux-modern/smoke-test.sh
