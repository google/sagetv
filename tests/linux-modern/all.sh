#!/usr/bin/env bash
set -euo pipefail
cd /work/sagetv
bash tests/linux-modern/clean.sh
bash tests/linux-modern/build.sh
bash tests/linux-modern/test-all.sh
bash tests/linux-modern/package.sh
bash tests/linux-modern/build-report.sh
