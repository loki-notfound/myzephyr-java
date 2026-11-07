#!/usr/bin/env bash
#
# Builds a complete zephyr-java release from pinned sources into dist/<version>/.
# Every step is a plain shell command (F-Droid runs this script; CI runs the same scripts).
#
#  1. bin/build_linux.sh --all       Linux natives through depends (x86_64, arm64)
#  2. bin/build_android.sh --all     Android natives through depends (arm64-v8a, armeabi-v7a, x86_64)
#  3. bin/strip_natives.sh           stripped natives in place, debug files in build/debug/
#  4. mvn package                    the JAR (with the Linux natives), sources and Javadoc jars;
#                                    the pure tests run on the depends-built x86_64 library
#  5. android/ ./gradlew             the AAR (with the Android natives), its sources jar and POM
#  6. dist/<version>/                the artifacts under release names, the debug symbols as a
#                                    reproducible tar.gz
#  7. bin/make_manifest.sh           manifest.json and SHA256SUMS in dist/<version>/
# bin/check_versions.sh runs first: the pom version, VERSION_NAME and RELEASE_TAG (when set)
# must agree.
#
# Reproducibility: SOURCE_DATE_EPOCH is the commit time of this checkout unless set; it is
# exported to every step, and Maven gets it as project.build.outputTimestamp (ISO 8601).
#
# Environment:
#   JAVA_HOME           a JDK (21)
#   ANDROID_HOME        the Android SDK (platform 36, build-tools 36.0.0)
#   BUILD_LINUX_ARCHES  Linux architectures to build (default "x86_64 arm64"; empty: none)
#   BUILD_ANDROID_ABIS  Android ABIs to build (default "arm64-v8a armeabi-v7a x86_64"; empty: none,
#                       and then no AAR)
#   SOURCE_DATE_EPOCH   optional, see above
#
# Usage: bin/build_release.sh

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

: "${JAVA_HOME:?set JAVA_HOME to a JDK}"

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
if [ -n "$LINUX_ARCHES" ]; then
  phase "build_linux.sh ($LINUX_ARCHES)"
  BUILD_LINUX_ARCHES="$LINUX_ARCHES" bin/build_linux.sh --all
fi
if [ -n "$ANDROID_ABIS" ]; then
  : "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
  phase "build_android.sh ($ANDROID_ABIS)"
  BUILD_ANDROID_ABIS="$ANDROID_ABIS" bin/build_android.sh --all
fi

# 3: strip, debug symbols
phase "strip_natives.sh"
bin/strip_natives.sh

# 4: the JAR, sources and Javadoc jars; the tests on the depends-built x86_64 library
phase "mvn package"
rm -rf target
for ARCH in $LINUX_ARCHES; do
  mkdir -p "target/natives/linux-$ARCH"
  cp "build/natives/linux-$ARCH/libzephyr-java.so" "target/natives/linux-$ARCH/libzephyr-java.so"
done
MVN_TESTS=()
case " $LINUX_ARCHES " in
  *" x86_64 "*) MVN_TESTS=("-Dnative.library.path=$ROOT/build/natives/linux-x86_64") ;;
  *) echo "no linux-x86_64 library in this build: the tests are skipped"; MVN_TESTS=(-DskipTests) ;;
esac
mvn -q "-Dproject.build.outputTimestamp=$OUTPUT_TIMESTAMP" "${MVN_TESTS[@]}" package

# 5: the AAR, its sources jar and POM (a fresh local repository, so each file exists once)
if [ -n "$ANDROID_ABIS" ]; then
  phase "gradle assembleRelease publishReleasePublicationToLocalRepository"
  (cd android && rm -rf build .gradle && ./gradlew --no-daemon -q assembleRelease publishReleasePublicationToLocalRepository)
fi

# 6: dist/<version>/
phase "dist/$VERSION"
DIST="dist/$VERSION"
rm -rf "$DIST" && mkdir -p "$DIST"
cp "target/zephyr-java-$VERSION.jar" "$DIST/zephyr-java-$VERSION.jar"
cp "target/zephyr-java-$VERSION-sources.jar" "$DIST/zephyr-java-$VERSION-sources.jar"
cp "target/zephyr-java-$VERSION-javadoc.jar" "$DIST/zephyr-java-$VERSION-javadoc.jar"
if [ -n "$ANDROID_ABIS" ]; then
  # a SNAPSHOT version is published with a timestamped file name; the repository is fresh
  REPO="android/build/repo/io/github/loki-notfound/zephyr-java"
  one() {
    local F
    F="$(find "$REPO" -type f -name "$1" | sort)"
    [ "$(printf '%s\n' "$F" | grep -c .)" = 1 ] || { echo "expected one $1 in $REPO, found: $F" >&2; exit 1; }
    printf '%s\n' "$F"
  }
  cp "$(one '*.aar')" "$DIST/zephyr-java-$VERSION.aar"
  cp "$(one '*-sources.jar')" "$DIST/zephyr-java-$VERSION-android-sources.jar"
  cp "$(one '*.pom')" "$DIST/zephyr-java-$VERSION-android.pom"
fi
# the debug files of the platforms built in this run, with their platform directories,
# reproducible: sorted, fixed times and owners, gzip without name and time
PLATFORMS=()
for ARCH in $LINUX_ARCHES; do PLATFORMS+=("linux-$ARCH"); done
for ABI in $ANDROID_ABIS; do PLATFORMS+=("android-$ABI"); done
tar --sort=name --mtime="@$SOURCE_DATE_EPOCH" --owner=0 --group=0 --numeric-owner --format=gnu \
    -C build/debug -cf - "${PLATFORMS[@]}" | gzip -9n > "$DIST/debug-symbols-$VERSION.tar.gz"

# 7: manifest.json and SHA256SUMS
phase "make_manifest.sh"
bin/make_manifest.sh "$DIST"
(cd "$DIST" && sha256sum -c --quiet SHA256SUMS)

phase "done"
(cd "$DIST" && ls -l && cat SHA256SUMS)
