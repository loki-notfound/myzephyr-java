#!/usr/bin/env bash
#
# Checks that the version of the release is the same everywhere it is written:
#   pom.xml                      project/version (the JAR, sources and Javadoc jars)
#   android/gradle.properties    VERSION_NAME (the AAR and its POM)
#   RELEASE_TAG (optional)       the tag being released must be v<version>
# Prints the version and exits 0 when they agree; prints the differing values and exits 1
# otherwise. bin/build_release.sh calls it first.
#
# Usage: bin/check_versions.sh            (or RELEASE_TAG=v2.3.0-sec.1-java.1 bin/check_versions.sh)

set -euo pipefail

cd "$(dirname "$0")/.."

POM_VERSION="$(python3 -I - pom.xml <<'PY'
import sys
import xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text.strip())
PY
)"
GRADLE_VERSION="$(sed -n 's/^VERSION_NAME=//p' android/gradle.properties)"

STATUS=0
if [ "$POM_VERSION" != "$GRADLE_VERSION" ]; then
  echo "version mismatch: pom.xml $POM_VERSION, android/gradle.properties VERSION_NAME '$GRADLE_VERSION'" >&2
  STATUS=1
fi
if [ -n "${RELEASE_TAG:-}" ] && [ "$RELEASE_TAG" != "v$POM_VERSION" ]; then
  echo "version mismatch: RELEASE_TAG $RELEASE_TAG, expected v$POM_VERSION" >&2
  STATUS=1
fi
[ "$STATUS" = 0 ] && echo "version $POM_VERSION${RELEASE_TAG:+ (tag $RELEASE_TAG)}"
exit "$STATUS"
