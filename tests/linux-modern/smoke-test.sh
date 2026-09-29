#!/usr/bin/env bash
set -euo pipefail
cd /work/sagetv/output/server
mkdir -p ../logs/smoke-home ../test-results/smoke
start_once() {
  HOME="$PWD/../logs/smoke-home" LD_LIBRARY_PATH="$PWD" \
    java -Djava.awt.headless=true -cp 'Sage.jar:JARs/*' sage.Sage 0 0 x 'sagetv Sage-smoke.properties' \
    > ../logs/sagetv-smoke.log 2>&1 &
  pid=$!
  sleep 12
  kill -0 "$pid"
  kill -TERM "$pid"
  for _ in $(seq 1 10); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  kill -KILL "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
}
start_once
start_once
grep -q 'Main is starting' ../logs/sagetv-smoke.log
