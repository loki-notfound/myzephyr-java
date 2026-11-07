#!/usr/bin/env bash
#
# Builds a complete zephyr-java release from pinned sources into dist/<version>/.
# Every step is a plain shell command (F-Droid runs this script; CI runs the same scripts).
#
#  1. bin/build_linux_container.sh --all
#                                    Linux natives through depends (x86_64, arm64) in the pinned
#                                    debian:12 image (RELEASES §4: the release natives come from
#                                    that image, the same as CI's, glibc floor 2.36);
#                                    LINUX_BUILD=host builds them on the host with
#                                    bin/build_linux.sh instead, for development only
#  2. bin/build_android_container.sh --all
#                                    Android natives through depends (arm64-v8a, armeabi-v7a,
#                                    x86_64) in the same image with the checkout at /work, the
#                                    path CI builds at too (NDK r18b keeps __FILE__ paths, so the
#                                    Android libraries are reproducible at the same path only);
#                                    ANDROID_BUILD=host builds them on the host with
#                                    bin/build_android.sh instead, for development only
#  3. bin/strip_natives.sh           stripped natives in place, debug files in build/debug/
#  4. bin/package_release_container.sh
#                                    bin/package_release.sh in the same image at /work (JDK, Maven
#                                    from the Debian snapshot, umask 022; QUESTIONS R13b-4), the
#                                    packaging step the CI package job runs in that image too;
#                                    PACKAGE_BUILD=host runs bin/package_release.sh on the host
#                                    instead, for development only.
#                                    bin/package_release.sh is everything after the natives:
#                                    the JAR (with the Linux natives, the pure tests on the
#                                    depends-built x86_64 library), sources and Javadoc jars; the
#                                    AAR (artifactId zephyr-java-android) with its sources jar and
#                                    POM; dist/<version>/ with the debug symbols as a reproducible
#                                    tar.gz; the static Maven repository (bin/make_maven_repo.sh),
#                                    the Pages site (bin/make_site.sh) and its archive
#                                    site-<version>.tar.gz, manifest.json and SHA256SUMS over the
#                                    release assets (bin/make_manifest.sh)
# bin/check_versions.sh runs first: the pom version, VERSION_NAME and RELEASE_TAG (when set)
# must agree.
#
# Reproducibility: SOURCE_DATE_EPOCH is the commit time of this checkout unless set; it is
# exported to every step, and Maven gets it as project.build.outputTimestamp (ISO 8601).
#
# Environment:
#   JAVA_HOME           a JDK, for PACKAGE_BUILD=host only (the container has its own)
#   ANDROID_HOME        the Android SDK (cmdline-tools 22.0, platforms;android-36, build-tools
#                       36.0.0), mounted read-only into the packaging container
#   BUILD_LINUX_ARCHES  Linux architectures to build (default "x86_64 arm64"; empty: none)
#   BUILD_ANDROID_ABIS  Android ABIs to build (default "arm64-v8a armeabi-v7a x86_64"; empty: none,
#                       and then no AAR)
#   SOURCE_DATE_EPOCH   optional, see above
#   LINUX_BUILD         container (default; podman or docker) or host
#   ANDROID_BUILD       container (default; podman or docker) or host
#   PACKAGE_BUILD       container (default; podman or docker) or host
#   SKIP_NATIVES        1: phases 1 to 3 are skipped and the natives already in build/ are packaged
#                       (a repeat of the packaging, such as the release rehearsal); every named
#                       platform's library and debug file must exist and none may contain /home/
#
# Usage: bin/build_release.sh

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

case "${PACKAGE_BUILD:=container}" in
  container) ;;
  host) : "${JAVA_HOME:?set JAVA_HOME to a JDK}" ;;
  *) echo "PACKAGE_BUILD must be container or host, not $PACKAGE_BUILD" >&2; exit 2 ;;
esac
case "${SKIP_NATIVES:=0}" in
  0|1) ;;
  *) echo "SKIP_NATIVES must be 0 or 1, not $SKIP_NATIVES" >&2; exit 2 ;;
esac

bin/check_versions.sh

phase() {
  echo "=== $(date -u +%FT%TZ) $*"
}

if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
  SOURCE_DATE_EPOCH="$(git log -1 --format=%ct HEAD)"
fi
export SOURCE_DATE_EPOCH
OUTPUT_TIMESTAMP="$(date -u -d "@$SOURCE_DATE_EPOCH" +%Y-%m-%dT%H:%M:%SZ)"
echo "SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH ($OUTPUT_TIMESTAMP)"

# the version of the Maven build (pom.xml, project/version)
VERSION="$(python3 -I - "$ROOT/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text.strip())
PY
)"
echo "version $VERSION"

# empty but set means: build none of them
LINUX_ARCHES="${BUILD_LINUX_ARCHES-x86_64 arm64}"
ANDROID_ABIS="${BUILD_ANDROID_ABIS-arm64-v8a armeabi-v7a x86_64}"

# 1, 2: natives
case "${LINUX_BUILD:=container}" in
  container|host) ;;
  *) echo "LINUX_BUILD must be container or host, not $LINUX_BUILD" >&2; exit 2 ;;
esac
case "${ANDROID_BUILD:=container}" in
  container|host) ;;
  *) echo "ANDROID_BUILD must be container or host, not $ANDROID_BUILD" >&2; exit 2 ;;
esac
if [ "$SKIP_NATIVES" = 1 ]; then
  # 1 to 3 skipped: the natives of an earlier build, each with its debug file and no host path
  phase "natives kept (SKIP_NATIVES=1)"
  KEPT=()
  for A in $LINUX_ARCHES; do
    KEPT+=("build/natives/linux-$A/libzephyr-java.so" "build/debug/linux-$A/libzephyr-java.so.debug")
  done
  for A in $ANDROID_ABIS; do
    KEPT+=("build/android/$A/libzephyr-java.so" "build/debug/android-$A/libzephyr-java.so.debug")
  done
  BAD=0
  for F in "${KEPT[@]}"; do
    if [ ! -f "$F" ]; then
      echo "missing $F" >&2
      BAD=1
    elif grep -qa /home/ "$F"; then
      echo "$F contains /home/: not a container build" >&2
      BAD=1
    else
      echo "  $(sha256sum "$F")"
    fi
  done
  [ "$BAD" = 0 ] || exit 1
else
  if [ -n "$LINUX_ARCHES" ]; then
    if [ "$LINUX_BUILD" = container ]; then
      phase "build_linux_container.sh ($LINUX_ARCHES)"
      BUILD_LINUX_ARCHES="$LINUX_ARCHES" bin/build_linux_container.sh --all
    else
      phase "build_linux.sh ($LINUX_ARCHES, on the host: not a release build)"
      BUILD_LINUX_ARCHES="$LINUX_ARCHES" bin/build_linux.sh --all
    fi
  fi
  if [ -n "$ANDROID_ABIS" ]; then
    : "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
    if [ "$ANDROID_BUILD" = container ]; then
      phase "build_android_container.sh ($ANDROID_ABIS)"
      BUILD_ANDROID_ABIS="$ANDROID_ABIS" bin/build_android_container.sh --all
    else
      phase "build_android.sh ($ANDROID_ABIS, on the host: not a release build)"
      BUILD_ANDROID_ABIS="$ANDROID_ABIS" bin/build_android.sh --all
    fi
  fi

  # 3: strip, debug symbols
  phase "strip_natives.sh"
  bin/strip_natives.sh
fi

# 4: the packaging, the same script as the CI package job, in the same image
if [ "$PACKAGE_BUILD" = container ]; then
  phase "package_release_container.sh $VERSION"
  BUILD_LINUX_ARCHES="$LINUX_ARCHES" BUILD_ANDROID_ABIS="$ANDROID_ABIS" bin/package_release_container.sh "$VERSION"
else
  phase "package_release.sh $VERSION (on the host: not a release build)"
  BUILD_LINUX_ARCHES="$LINUX_ARCHES" BUILD_ANDROID_ABIS="$ANDROID_ABIS" bin/package_release.sh "$VERSION"
fi

phase "done"
