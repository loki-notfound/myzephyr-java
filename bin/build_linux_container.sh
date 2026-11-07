#!/usr/bin/env bash
#
# Builds the Linux release natives in the pinned debian:12 image, the same image the CI natives
# job uses, so a local build can be compared with the CI build (glibc floor 2.36: Debian 12,
# Ubuntu 23.04 and later, RHEL 10).
#
# Runs, inside the image with this checkout mounted at /work:
#   bin/container-build-deps.sh                  the Debian 12 build packages from the pinned
#                                                Debian snapshot (as root)
#   bin/build_linux.sh <x86_64|arm64|--all>      depends, zephyr, the JNI library
#   bin/strip_natives.sh                         stripped natives and debug files, as in CI
# with JAVA_HOME at the image's OpenJDK 17 (jni.h only), SOURCE_DATE_EPOCH passed through (the
# commit time of this checkout unless set), BUILD_LINUX_ARCHES passed through, and
# ZEPHYR_JAVA_CONTAINER set to the image, which bin/build_linux.sh records in
# build/natives/linux-<arch>/build-env for the manifest.
# The depends prefixes, the build directories and the outputs stay under the checkout, as for a
# host build, so they persist between container runs. The packages built in the image have their
# own depends build ids (the compiler versions are part of them), but depends keeps one build per
# package and host, so a switch between host and container rebuilds the depends packages, and
# bin/build_linux.sh wipes build directories configured for the other path. A host build and a
# container build write the same build/natives/linux-<arch>/, so the last one wins; releases build
# Linux in the container only. bin/build_android_container.sh is the same for the Android natives.
# podman (rootless: files written in the container belong to the calling user) or, when podman
# is absent, docker.
#
# Usage: bin/build_linux_container.sh <x86_64|arm64|--all>

set -euo pipefail

IMAGE=docker.io/library/debian:12@sha256:366a6da5efcceffbaa19c560a101b97b87a1a9664eeeac8bc3d24395ddbf14f3

[ $# -eq 1 ] || { echo "usage: $0 <x86_64|arm64|--all>" >&2; exit 2; }
case "$1" in
  x86_64|arm64|--all) ;;
  *) echo "usage: $0 <x86_64|arm64|--all>" >&2; exit 2 ;;
esac

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

if command -v podman >/dev/null; then
  ENGINE=podman
elif command -v docker >/dev/null; then
  ENGINE=docker
else
  echo "neither podman nor docker is installed" >&2
  exit 1
fi

if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
  SOURCE_DATE_EPOCH="$(git log -1 --format=%ct HEAD)"
fi
echo "$ENGINE $IMAGE, SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH"

# TAR_OPTIONS: rootless podman maps root in the container to the calling user with 65,536
# subordinate ids, and GNU tar running as root restores the archives' owners, some of which lie
# outside that range ("Cannot change ownership ... Invalid argument" in depends' extraction)
ENV_ARGS=(-e "SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH" -e "ZEPHYR_JAVA_CONTAINER=$IMAGE" -e TAR_OPTIONS=--no-same-owner)
[ -n "${BUILD_LINUX_ARCHES+x}" ] && ENV_ARGS+=(-e "BUILD_LINUX_ARCHES=$BUILD_LINUX_ARCHES")

"$ENGINE" run --rm -v "$ROOT":/work -w /work "${ENV_ARGS[@]}" "$IMAGE" bash -c '
  set -euo pipefail
  bin/container-build-deps.sh
  # the mounted checkout belongs to another uid when docker runs as root
  git config --global --add safe.directory "*"
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture)"
  echo "in $(. /etc/os-release; echo "$PRETTY_NAME"), $(ldd --version | head -1), $(gcc --version | head -1), JAVA_HOME=$JAVA_HOME"
  bin/build_linux.sh "$1"
  bin/strip_natives.sh
' build_linux_container "$1"
