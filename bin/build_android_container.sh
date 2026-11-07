#!/usr/bin/env bash
#
# Builds the Android release natives in the pinned debian:12 image, the same image and the same
# path (/work) as the CI natives job and bin/build_linux_container.sh, so a local build can be
# compared with the CI build (QUESTIONS R13-6): NDK r18b's clang 7 cannot rewrite __FILE__, so
# the Android libraries keep the absolute source paths of the build and are reproducible at the
# same build path only; with the checkout at /work on both sides those paths read /work/...
#
# Runs, inside the image with this checkout mounted at /work:
#   bin/container-build-deps.sh                          the Debian 12 build packages from the
#                                                        pinned Debian snapshot (as root)
#   bin/build_android.sh <arm64-v8a|armeabi-v7a|x86_64|--all>
#                                                        depends (NDK r18b), zephyr, the JNI
#                                                        library
#   bin/strip_natives.sh                                 stripped natives and debug files, as in CI
# with JAVA_HOME at the image's OpenJDK 17 (jni.h only), SOURCE_DATE_EPOCH passed through (the
# commit time of this checkout unless set), BUILD_ANDROID_ABIS passed through, and
# ZEPHYR_JAVA_CONTAINER set to the image, which bin/android_build_env.sh records in
# build/debug/android-<abi>/build-env for the manifest.
# The depends prefixes, the build directories and the outputs stay under the checkout and are
# shared with a host build of bin/build_android.sh: depends' package build ids contain the prefix
# path and the compilers, so the first container run after a host build rebuilds the Android
# depends packages, and bin/build_android.sh wipes build directories configured for the other
# path. Releases build Android in the container only.
# podman (rootless: files written in the container belong to the calling user) or, when podman
# is absent, docker.
#
# Usage: bin/build_android_container.sh <arm64-v8a|armeabi-v7a|x86_64|--all>

set -euo pipefail

IMAGE=docker.io/library/debian:12@sha256:366a6da5efcceffbaa19c560a101b97b87a1a9664eeeac8bc3d24395ddbf14f3

usage() {
  echo "usage: $0 <arm64-v8a|armeabi-v7a|x86_64|--all>" >&2
  exit 2
}
[ $# -eq 1 ] || usage
case "$1" in
  arm64-v8a|armeabi-v7a|x86_64|--all) ;;
  *) usage ;;
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

# TAR_OPTIONS: as in bin/build_linux_container.sh (rootless podman maps root to the calling user
# with 65,536 subordinate ids; GNU tar as root would restore archive owners outside that range)
ENV_ARGS=(-e "SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH" -e "ZEPHYR_JAVA_CONTAINER=$IMAGE" -e TAR_OPTIONS=--no-same-owner)
[ -n "${BUILD_ANDROID_ABIS+x}" ] && ENV_ARGS+=(-e "BUILD_ANDROID_ABIS=$BUILD_ANDROID_ABIS")

"$ENGINE" run --rm -v "$ROOT":/work -w /work "${ENV_ARGS[@]}" "$IMAGE" bash -c '
  set -euo pipefail
  bin/container-build-deps.sh
  # the mounted checkout belongs to another uid when docker runs as root
  git config --global --add safe.directory "*"
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture)"
  echo "in $(. /etc/os-release; echo "$PRETTY_NAME"), $(ldd --version | head -1), $(gcc --version | head -1), JAVA_HOME=$JAVA_HOME"
  bin/build_android.sh "$1"
  bin/strip_natives.sh
' build_android_container "$1"
