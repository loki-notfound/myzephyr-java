#!/usr/bin/env bash
#
# Packages a zephyr-java release into dist/<version>/ from natives that are already built and
# stripped. bin/build_release.sh calls it after its native phases (through
# bin/package_release_container.sh, in the pinned debian:12 image at /work), and the CI package
# job calls it in the same image at /work after downloading the natives jobs' artifacts, so a
# local release and the CI release run the same packaging steps in the same environment, and
# their files can be compared byte for byte.
#
# Inputs (per platform, from bin/build_linux.sh or bin/build_linux_container.sh,
# bin/build_android.sh and bin/strip_natives.sh):
#   build/natives/linux-<arch>/libzephyr-java.so    and build-env (for the manifest)
#   build/android/<abi>/libzephyr-java.so
#   build/debug/<platform>/libzephyr-java.so.debug
# Steps:
#  1. bin/check_versions.sh          the pom version, VERSION_NAME and RELEASE_TAG (when set)
#                                    agree, and <version> is that version
#  2. mvn package                    the JAR (with the Linux natives), sources and Javadoc jars;
#                                    the pure tests run on the depends-built x86_64 library
#  3. android/ ./gradlew             the AAR (with the Android natives), its sources jar and POM,
#                                    artifactId zephyr-java-android (ARTIFACT_ID in
#                                    android/gradle.properties; the JAR is zephyr-java)
#  4. dist/<version>/                the artifacts under release names, the debug symbols as a
#                                    reproducible tar.gz
#  5. bin/make_maven_repo.sh         dist/<version>/maven/, the static Maven repository
#  6. bin/make_site.sh               dist/<version>/site/, the GitHub Pages content, with the
#                                    repository as site/maven/ (the only copy: the top-level
#                                    maven/ is removed afterwards)
#  7. site-<version>.tar.gz          the site as one reproducible archive, a release asset
#                                    (reproducible: sorted names, mtime SOURCE_DATE_EPOCH,
#                                    numeric owner 0, no atime/ctime pax records, gzip -n
#  8. bin/make_manifest.sh           manifest.json in dist/<version>/
#  9. bin/make_manifest.sh --sums-only
#                                    SHA256SUMS over the top-level files of dist/<version>/ (the
#                                    release assets), checked
# dist/<version>/site/ stays for the Pages publication; its files are covered by the archive's
# hash, not listed in SHA256SUMS, so sha256sum -c SHA256SUMS works on the downloaded assets.
#
# Reproducibility: SOURCE_DATE_EPOCH is the commit time of this checkout unless set; it is
# exported to every step, and Maven gets it as project.build.outputTimestamp (ISO 8601).
# File modes: the jar plugins store each entry's mode from the file system (with umask 022 applied)
# and tar records modes, so the script runs with umask 022, gives the natives it copies into
# target/natives/ mode 0755, every other file it copies and the debug files it packs 0644, and
# writes both tars with
# --mode=u+rwX,go+rX,go-w: the entries read -rw-r--r--, -rwxr-xr-x and drwxr-xr-x whatever the
# caller's umask or the modes of the downloaded natives. The archivers take the
# modes of the checkout's own files straight from the file system, so the tracked inputs the jars
# package (pom.xml as META-INF/maven/.../pom.xml, src/main/java and src/main/resources) get
# u+rwX,go+rX,go-w first: the umask the checkout was made with must not reach the jars.
#
# Environment:
#   JAVA_HOME           a JDK (the image's OpenJDK 17 for releases)
#   ANDROID_HOME        the Android SDK (platform 36, build-tools 36.0.0), when there are ABIs
#   BUILD_LINUX_ARCHES  Linux architectures to package (default "x86_64 arm64"; empty: none)
#   BUILD_ANDROID_ABIS  Android ABIs to package (default "arm64-v8a armeabi-v7a x86_64"; empty:
#                       none, and then no AAR)
#   SOURCE_DATE_EPOCH   optional, see above
# Every platform named must have its stripped library and its debug file; a missing one stops
# the script.
#
# Usage: bin/package_release.sh <version>

set -euo pipefail
umask 022

[ $# -eq 1 ] || { echo "usage: $0 <version>" >&2; exit 2; }

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

: "${JAVA_HOME:?set JAVA_HOME to a JDK}"

phase() {
  echo "=== $(date -u +%FT%TZ) $*"
}

# 1: the versions
bin/check_versions.sh
VERSION="$(python3 -I - "$ROOT/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text.strip())
PY
)"
[ "$1" = "$VERSION" ] || { echo "version $1 is not the version of pom.xml, $VERSION" >&2; exit 1; }

if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
  SOURCE_DATE_EPOCH="$(git log -1 --format=%ct HEAD)"
fi
export SOURCE_DATE_EPOCH
OUTPUT_TIMESTAMP="$(date -u -d "@$SOURCE_DATE_EPOCH" +%Y-%m-%dT%H:%M:%SZ)"
echo "package $VERSION, SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH ($OUTPUT_TIMESTAMP)"

# empty but set means: package none of them
LINUX_ARCHES="${BUILD_LINUX_ARCHES-x86_64 arm64}"
ANDROID_ABIS="${BUILD_ANDROID_ABIS-arm64-v8a armeabi-v7a x86_64}"

# the inputs: every named platform has its stripped library and its debug file
PLATFORMS=()
MISSING=0
for ARCH in $LINUX_ARCHES; do
  PLATFORMS+=("linux-$ARCH")
  for F in "build/natives/linux-$ARCH/libzephyr-java.so" "build/debug/linux-$ARCH/libzephyr-java.so.debug"; do
    [ -f "$F" ] || { echo "missing $F" >&2; MISSING=1; }
  done
done
for ABI in $ANDROID_ABIS; do
  PLATFORMS+=("android-$ABI")
  for F in "build/android/$ABI/libzephyr-java.so" "build/debug/android-$ABI/libzephyr-java.so.debug"; do
    [ -f "$F" ] || { echo "missing $F" >&2; MISSING=1; }
  done
done
[ "$MISSING" = 0 ] || exit 1

# 2: the JAR, sources and Javadoc jars; the tests on the depends-built x86_64 library
phase "mvn package"
# the checkout's modes reach the jars (see "File modes" above); LICENCE.txt and README.md are in
# no jar
chmod -R u+rwX,go+rX,go-w pom.xml src/main
rm -rf target
for ARCH in $LINUX_ARCHES; do
  mkdir -p "target/natives/linux-$ARCH"
  cp "build/natives/linux-$ARCH/libzephyr-java.so" "target/natives/linux-$ARCH/libzephyr-java.so"
  chmod 0755 "target/natives/linux-$ARCH/libzephyr-java.so"
done
MVN_TESTS=()
case " $LINUX_ARCHES " in
  *" x86_64 "*) MVN_TESTS=("-Dnative.library.path=$ROOT/build/natives/linux-x86_64") ;;
  *) echo "no linux-x86_64 library in this build: the tests are skipped"; MVN_TESTS=(-DskipTests) ;;
esac
mvn -q "-Dproject.build.outputTimestamp=$OUTPUT_TIMESTAMP" "${MVN_TESTS[@]}" package
# never an empty Javadoc jar (under a POSIX locale JDK 17's javadoc once failed and the jar held
# only its manifest)
# (the listing first: grep -q in a pipe would end unzip with SIGPIPE, a failure under pipefail)
JAVADOC_LIST="$(unzip -l "target/zephyr-java-$VERSION-javadoc.jar")"
if ! grep -q ' index\.html$' <<< "$JAVADOC_LIST"; then
  echo "target/zephyr-java-$VERSION-javadoc.jar has no index.html: the Javadoc was not generated" >&2
  exit 1
fi

# 3: the AAR, its sources jar and POM (a fresh local repository, so each file exists once)
if [ -n "$ANDROID_ABIS" ]; then
  : "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
  phase "gradle assembleRelease publishReleasePublicationToLocalRepository"
  (cd android && rm -rf build .gradle && ./gradlew --no-daemon -q assembleRelease publishReleasePublicationToLocalRepository)
fi

# 4: dist/<version>/
phase "dist/$VERSION"
DIST="dist/$VERSION"
rm -rf "$DIST" && mkdir -p "$DIST"
cp "target/zephyr-java-$VERSION.jar" "$DIST/zephyr-java-$VERSION.jar"
cp "target/zephyr-java-$VERSION-sources.jar" "$DIST/zephyr-java-$VERSION-sources.jar"
cp "target/zephyr-java-$VERSION-javadoc.jar" "$DIST/zephyr-java-$VERSION-javadoc.jar"
if [ -n "$ANDROID_ABIS" ]; then
  # a SNAPSHOT version is published with a timestamped file name; the repository is fresh
  AAR_ID="$(sed -n 's/^ARTIFACT_ID=//p' android/gradle.properties)"
  REPO="android/build/repo/io/github/loki-notfound/$AAR_ID"
  one() {
    local F
    F="$(find "$REPO" -type f -name "$1" | sort)"
    [ "$(printf '%s\n' "$F" | grep -c .)" = 1 ] || { echo "expected one $1 in $REPO, found: $F" >&2; exit 1; }
    printf '%s\n' "$F"
  }
  cp "$(one '*.aar')" "$DIST/$AAR_ID-$VERSION.aar"
  cp "$(one '*-sources.jar')" "$DIST/$AAR_ID-$VERSION-sources.jar"
  cp "$(one '*.pom')" "$DIST/$AAR_ID-$VERSION.pom"
fi
chmod 0644 "$DIST"/*
# the debug files of the packaged platforms, with their platform directories, reproducible:
# sorted, fixed times, owners and modes, gzip without name and time; the Android build-env files
# next to them are manifest inputs, not debug symbols; the debug files keep the exec bit of the
# library objcopy wrote them from, which a CI artifact download drops: 0644 on both sides
for P in "${PLATFORMS[@]}"; do
  chmod 0644 "build/debug/$P/libzephyr-java.so.debug"
done
tar --sort=name --mtime="@$SOURCE_DATE_EPOCH" --owner=0 --group=0 --numeric-owner --format=gnu \
    --mode=u+rwX,go+rX,go-w --exclude=build-env \
    -C build/debug -cf - "${PLATFORMS[@]}" | gzip -9n > "$DIST/debug-symbols-$VERSION.tar.gz"

# 5, 6: the Maven repository and the Pages site; the repository lives in site/maven/ only
phase "make_maven_repo.sh"
bin/make_maven_repo.sh "$DIST"
phase "make_site.sh"
bin/make_site.sh "$DIST"
rm -rf "$DIST/maven"

# 7: the site as one reproducible archive
phase "site-$VERSION.tar.gz"
tar --sort=name --mtime="@$SOURCE_DATE_EPOCH" --owner=0 --group=0 --numeric-owner \
    --mode=u+rwX,go+rX,go-w \
    --pax-option=exthdr.name=%d/PaxHeaders/%f,delete=atime,delete=ctime \
    -C "$DIST" -cf - site | gzip -n -9 > "$DIST/site-$VERSION.tar.gz"
echo "site-$VERSION.tar.gz: $(tar -tzf "$DIST/site-$VERSION.tar.gz" | wc -l) entries"

# 8: manifest.json (of the release files above, the archive included)
phase "make_manifest.sh"
BUILD_LINUX_ARCHES="$LINUX_ARCHES" BUILD_ANDROID_ABIS="$ANDROID_ABIS" bin/make_manifest.sh "$DIST"

# 9: SHA256SUMS over the release assets (the top-level files)
phase "make_manifest.sh --sums-only"
bin/make_manifest.sh --sums-only "$DIST"
(cd "$DIST" && sha256sum -c --quiet SHA256SUMS)

phase "packaged $DIST"
(cd "$DIST" && ls -l && cat SHA256SUMS && echo "SHA256SUMS: $(wc -l < SHA256SUMS) files")
