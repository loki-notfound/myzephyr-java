#!/usr/bin/env bash
#
# Packages a zephyr-java release (bin/package_release.sh) in the pinned debian:12 image, the same
# image, Debian snapshot and path (/work) as the native builds and the CI package job, so the jars,
# the AAR and the archives of a local build can be compared with CI's byte for byte
# one environment produces every release file.
#
# Runs, inside the image with this checkout mounted at /work:
#   bin/container-build-deps.sh          the Debian 12 packages from the pinned Debian snapshot,
#                                        OpenJDK 17 and Maven included (as root)
#   bin/place_zephyr_tree.sh             the pinned zephyr tree with the build-layer patches
#                                        (already there after the native builds; the manifest
#                                        reads its depends recipes)
#   umask 022
#   bin/package_release.sh <version>     the JAR, the AAR, the Maven repository, the site, the
#                                        archives, manifest.json and SHA256SUMS in dist/<version>/
# with
#   JAVA_HOME           the image's OpenJDK 17 (javac, javadoc, Maven and Gradle)
#   ANDROID_HOME        /opt/android-sdk: the host's Android SDK ($ANDROID_HOME), mounted
#                       read-only; it must hold the pinned packages cmdline-tools 22.0,
#                       platforms;android-36 and build-tools;36.0.0 (checked below, recorded in
#                       the manifest; the CI package job installs the same three)
#   GRADLE_USER_HOME    /work/.gradle-home, and Maven's local repository /work/.m2
#                       (MAVEN_OPTS=-Dmaven.repo.local), both gitignored: the container needs
#                       nothing from the host's home directory, and the downloads (the Gradle
#                       distribution, Maven Central, Google Maven) persist between runs
#   SOURCE_DATE_EPOCH   passed through (the commit time of this checkout unless set)
#   ZEPHYR_JAVA_CONTAINER  the image, which bin/make_manifest.sh records as
#                       toolchain.packaging_container (toolchain.packaging_env = container)
#   TAR_OPTIONS         --no-same-owner, as in bin/build_linux_container.sh
#   LANG                C.UTF-8 (in Debian's libc): the image sets no locale, and JDK 17 then
#                       writes files such as Javadoc's options file in ASCII, and the © of
#                       the Javadoc footer fails
#   BUILD_LINUX_ARCHES, BUILD_ANDROID_ABIS, RELEASE_TAG   passed through when set
#   PREV_SITE_ARCHIVE   when set, a local copy of the previous release's site archive: the file is
#                       mounted read-only at /opt/previous-release/ and the variable points there
#                       (bin/merge_previous_release.sh checks its sha256 like a download's)
# podman (rootless: files written in the container belong to the calling user) or, when podman
# is absent, docker.
#
# Usage: bin/package_release_container.sh <version>

set -euo pipefail

IMAGE=docker.io/library/debian:12@sha256:366a6da5efcceffbaa19c560a101b97b87a1a9664eeeac8bc3d24395ddbf14f3

[ $# -eq 1 ] || { echo "usage: $0 <version>" >&2; exit 2; }

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

ENV_ARGS=(-e "SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH" -e "ZEPHYR_JAVA_CONTAINER=$IMAGE"
          -e TAR_OPTIONS=--no-same-owner -e LANG=C.UTF-8
          -e GRADLE_USER_HOME=/work/.gradle-home -e MAVEN_OPTS=-Dmaven.repo.local=/work/.m2)
MOUNTS=(-v "$ROOT":/work)
[ -n "${BUILD_LINUX_ARCHES+x}" ] && ENV_ARGS+=(-e "BUILD_LINUX_ARCHES=$BUILD_LINUX_ARCHES")
[ -n "${BUILD_ANDROID_ABIS+x}" ] && ENV_ARGS+=(-e "BUILD_ANDROID_ABIS=$BUILD_ANDROID_ABIS")
[ -n "${RELEASE_TAG:-}" ] && ENV_ARGS+=(-e "RELEASE_TAG=$RELEASE_TAG")
if [ -n "${PREV_SITE_ARCHIVE:-}" ]; then
  [ -f "$PREV_SITE_ARCHIVE" ] || { echo "PREV_SITE_ARCHIVE $PREV_SITE_ARCHIVE is not a file" >&2; exit 1; }
  PREV_IN="/opt/previous-release/$(basename "$PREV_SITE_ARCHIVE")"
  MOUNTS+=(-v "$(realpath "$PREV_SITE_ARCHIVE")":"$PREV_IN":ro)
  ENV_ARGS+=(-e "PREV_SITE_ARCHIVE=$PREV_IN")
fi

# the Android SDK, when there are ABIs: the pinned packages, read-only at /opt/android-sdk
if [ -n "${BUILD_ANDROID_ABIS-arm64-v8a armeabi-v7a x86_64}" ]; then
  : "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
  revision() {
    sed -n 's/^Pkg\.Revision=//p' "$ANDROID_HOME/$1/source.properties" 2>/dev/null | head -1
  }
  STATUS=0
  [ "$(revision cmdline-tools/latest)" = 22.0 ] || { echo "$ANDROID_HOME: cmdline-tools/latest is not 22.0" >&2; STATUS=1; }
  [ -n "$(revision platforms/android-36)" ] || { echo "$ANDROID_HOME: no platforms;android-36" >&2; STATUS=1; }
  [ "$(revision build-tools/36.0.0)" = 36.0.0 ] || { echo "$ANDROID_HOME: no build-tools;36.0.0" >&2; STATUS=1; }
  [ "$STATUS" = 0 ] || exit 1
  MOUNTS+=(-v "$ANDROID_HOME":/opt/android-sdk:ro)
  ENV_ARGS+=(-e ANDROID_HOME=/opt/android-sdk)
fi

"$ENGINE" run --rm "${MOUNTS[@]}" -w /work "${ENV_ARGS[@]}" "$IMAGE" bash -c '
  set -euo pipefail
  bin/container-build-deps.sh
  # the mounted checkout belongs to another uid when docker runs as root
  git config --global --add safe.directory "*"
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture)"
  # the pinned zephyr tree with the patches (present after the native builds; the CI package job
  # places it the same way), for bin/make_manifest.sh
  bin/place_zephyr_tree.sh
  umask 022
  echo "in $(. /etc/os-release; echo "$PRETTY_NAME"), $("$JAVA_HOME/bin/javac" -version 2>&1), $(mvn -B --version 2>&1 | sed -n 1p), umask $(umask)"
  bin/package_release.sh "$1"
' package_release_container "$1"
