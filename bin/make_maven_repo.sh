#!/usr/bin/env bash
#
# Builds dist/<version>/maven/, a complete static Maven repository of one release, for GitHub
# Pages (bin/make_site.sh copies it into the site) or any other static file host:
#   io/github/loki-notfound/zephyr-java/           the JAR, its sources and Javadoc jars, the POM
#                                                  (mvn deploy to a file:// repository)
#   io/github/loki-notfound/zephyr-java-android/   the AAR, its sources jar, POM and Gradle module
#                                                  metadata (Gradle's maven-publish, -PrepoDir)
# with maven-metadata.xml for both artifactIds and .md5, .sha1 and .sha256 next to every file
# (Maven writes .md5 and .sha1, Gradle .md5, .sha1, .sha256 and .sha512; the missing .sha256 are
# added here, as plain hex like the others).
#
# Run after bin/build_release.sh has filled dist/<version>/ (it calls this script): mvn deploy
# and Gradle repackage from the same build state (target/natives/, build/android/), and the
# script checks that every published JAR, AAR and POM is byte-identical to its copy in
# dist/<version>/. Without an AAR in dist/<version>/ (a build without Android ABIs) only the
# JAR is published.
#
# Reproducibility: the jars get project.build.outputTimestamp from SOURCE_DATE_EPOCH (the commit
# time of this checkout unless set), and the <lastUpdated> element that Maven and Gradle write
# into every maven-metadata.xml with the current time is set to SOURCE_DATE_EPOCH, its checksum
# files rewritten. A -SNAPSHOT version is published, as Maven and Gradle always do, with a
# timestamped file name (<version without SNAPSHOT>-<yyyyMMdd.HHmmss>-<n>) and a snapshot
# timestamp of the current time; those two runs of the same SNAPSHOT differ in their file names
# and snapshot metadata, a release version does not.
#
# Environment: JAVA_HOME (a JDK), ANDROID_HOME (for the AAR), SOURCE_DATE_EPOCH (optional).
#
# Usage: bin/make_maven_repo.sh [dist/<version>]   (default: dist/<version of pom.xml>)

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

: "${JAVA_HOME:?set JAVA_HOME to a JDK}"

if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
  SOURCE_DATE_EPOCH="$(git log -1 --format=%ct HEAD)"
fi
export SOURCE_DATE_EPOCH
OUTPUT_TIMESTAMP="$(date -u -d "@$SOURCE_DATE_EPOCH" +%Y-%m-%dT%H:%M:%SZ)"
LAST_UPDATED="$(date -u -d "@$SOURCE_DATE_EPOCH" +%Y%m%d%H%M%S)"

VERSION="$(python3 -I - "$ROOT/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text.strip())
PY
)"
GROUP_PATH="io/github/loki-notfound"
AAR_ID="$(sed -n 's/^ARTIFACT_ID=//p' android/gradle.properties)"

DIST="${1:-dist/$VERSION}"
DIST="$(cd "$DIST" && pwd)"
REPO="$DIST/maven"
[ -f "$DIST/zephyr-java-$VERSION.jar" ] || { echo "no $DIST/zephyr-java-$VERSION.jar: run bin/build_release.sh first" >&2; exit 1; }
rm -rf "$REPO"
mkdir -p "$REPO"
echo "maven repository $REPO (version $VERSION, lastUpdated $LAST_UPDATED)"

# the JAR, sources and Javadoc jars and the POM; maven-deploy-plugin 3.x takes id::url; the
# install phase that deploy runs through is skipped (nothing goes into ~/.m2)
mvn -q -DskipTests -Dmaven.install.skip=true "-Dproject.build.outputTimestamp=$OUTPUT_TIMESTAMP" deploy \
  "-DaltDeploymentRepository=dist::file://$REPO"

# the AAR, its sources jar, POM and module metadata
WITH_AAR=0
if [ -f "$DIST/$AAR_ID-$VERSION.aar" ]; then
  : "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
  (cd android && ./gradlew --no-daemon -q publishReleasePublicationToLocalRepository "-PrepoDir=$REPO")
  WITH_AAR=1
else
  echo "no $DIST/$AAR_ID-$VERSION.aar: the AAR is not published"
fi

# <lastUpdated> from SOURCE_DATE_EPOCH, then the checksum files of the changed metadata
find "$REPO" -type f -name maven-metadata.xml | LC_ALL=C sort | while read -r M; do
  sed -i "s|<lastUpdated>[0-9]*</lastUpdated>|<lastUpdated>$LAST_UPDATED</lastUpdated>|" "$M"
  md5sum "$M" | cut -d' ' -f1 > "$M.md5"
  sha1sum "$M" | cut -d' ' -f1 > "$M.sha1"
  sha256sum "$M" | cut -d' ' -f1 > "$M.sha256"
  [ ! -f "$M.sha512" ] || sha512sum "$M" | cut -d' ' -f1 > "$M.sha512"
done

# .sha256 next to every file that lacks one, then every checksum file checked against its file
is_checksum() {
  case "$1" in *.md5|*.sha1|*.sha256|*.sha512) return 0 ;; esac
  return 1
}
FILES=0
while read -r F; do
  is_checksum "$F" && continue
  FILES=$((FILES + 1))
  [ -f "$F.sha256" ] || sha256sum "$F" | cut -d' ' -f1 > "$F.sha256"
  for ALG in md5 sha1 sha256 sha512; do
    [ -f "$F.$ALG" ] || { [ "$ALG" = sha512 ] && continue; echo "missing $F.$ALG" >&2; exit 1; }
    [ "$("${ALG}sum" "$F" | cut -d' ' -f1)" = "$(cat "$F.$ALG")" ] || { echo "wrong $F.$ALG" >&2; exit 1; }
  done
done < <(find "$REPO" -type f | LC_ALL=C sort)

# maven-metadata.xml for each artifactId, and the published files equal to dist/<version>/
same() {
  # same <file in dist> <find name pattern> [more find tests]: exactly one match, byte-identical
  local D="$1" F
  shift
  F="$(find "$REPO/$GROUP_PATH" -type f -name "$@" ! -name '*.md5' ! -name '*.sha*' | LC_ALL=C sort)"
  [ "$(printf '%s\n' "$F" | grep -c .)" = 1 ] || { echo "expected one $1 in $REPO, found: $F" >&2; exit 1; }
  cmp -s "$F" "$D" || { echo "$F differs from $D" >&2; exit 1; }
  echo "  ${F#"$REPO"/} = $(basename "$D")"
}
IDS=(zephyr-java)
[ "$WITH_AAR" = 0 ] || IDS+=("$AAR_ID")
for ID in "${IDS[@]}"; do
  [ -f "$REPO/$GROUP_PATH/$ID/maven-metadata.xml" ] || { echo "no maven-metadata.xml for $ID" >&2; exit 1; }
done
echo "published files equal to ${DIST#"$ROOT"/}/:"
V="${VERSION%-SNAPSHOT}"
same "$DIST/zephyr-java-$VERSION.jar" "zephyr-java-$V*.jar" ! -name '*-sources.jar' ! -name '*-javadoc.jar'
same "$DIST/zephyr-java-$VERSION-sources.jar" "zephyr-java-$V*-sources.jar"
same "$DIST/zephyr-java-$VERSION-javadoc.jar" "zephyr-java-$V*-javadoc.jar"
if [ "$WITH_AAR" = 1 ]; then
  same "$DIST/$AAR_ID-$VERSION.aar" "$AAR_ID-$V*.aar"
  same "$DIST/$AAR_ID-$VERSION-sources.jar" "$AAR_ID-$V*-sources.jar"
  same "$DIST/$AAR_ID-$VERSION.pom" "$AAR_ID-$V*.pom"
fi
echo "wrote ${REPO#"$ROOT"/}: $FILES files with their checksums, $(find "$REPO" -type f | wc -l) files in all"
