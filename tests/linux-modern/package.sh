#!/usr/bin/env bash
set -euo pipefail
cd /work/sagetv
mkdir -p output/packages
tar -C output/server -czf "output/packages/sagetv-server-$(uname -m).tar.gz" .
