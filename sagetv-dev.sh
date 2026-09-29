#!/usr/bin/env bash
set -euo pipefail

IMAGE="${SAGETV_DEV_IMAGE:-opensagetv-ubuntu26-dev}"
CONTAINER="${SAGETV_DEV_CONTAINER:-opensagetv-ubuntu26-dev}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

image_build() {
  docker build --build-arg UBUNTU_VERSION="${UBUNTU_VERSION:-26.04}" \
    --build-arg JAVA_VERSION="${JAVA_VERSION:-11}" -t "$IMAGE" \
    "$ROOT/docker/ubuntu26-dev"
}

container_start() {
  if ! docker container inspect "$CONTAINER" >/dev/null 2>&1; then
    docker create --name "$CONTAINER" --init \
      --mount "type=bind,source=$ROOT,target=/work/sagetv" "$IMAGE" >/dev/null
  fi
  if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER")" != true ]; then
    docker start "$CONTAINER" >/dev/null
  fi
}

run() {
  container_start
  docker exec -e JDK_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
    -e JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 "$CONTAINER" "$@"
}

case "${1:-help}" in
  image) image_build ;;
  build) run bash tests/linux-modern/build.sh ;;
  test) run bash tests/linux-modern/test-all.sh ;;
  native-test) run bash tests/linux-modern/test-native.sh ;;
  image-test) run bash tests/linux-modern/test-image-loader.sh ;;
  package) run bash tests/linux-modern/package.sh ;;
  report) run bash tests/linux-modern/build-report.sh ;;
  shell) container_start; docker exec -it "$CONTAINER" bash ;;
  clean) run bash tests/linux-modern/clean.sh ;;
  rebuild) run bash tests/linux-modern/clean.sh; run bash tests/linux-modern/build.sh ;;
  all)
    image_build
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
    container_start
    if run bash tests/linux-modern/all.sh; then
      echo "BUILD PASSED"
    else
      status=$?
      echo "BUILD FAILED" >&2
      exit "$status"
    fi
    ;;
  *)
    echo "Usage: $0 {image|build|test|native-test|image-test|package|report|shell|clean|rebuild|all}" >&2
    exit 2
    ;;
esac
