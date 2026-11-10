#!/usr/bin/env bash
#
# Checks that the version of the release is the same everywhere it is written:
#   pom.xml                      project/version (the JAR, sources and Javadoc jars)
#   android/gradle.properties    VERSION_NAME (the AAR and its POM)
#   RELEASE_TAG (optional)       the tag being released must be v<version>
# that the Javadoc bottom text is the same in bin/javadoc-bottom.txt (bin/javadoc_sorted.sh,
# the Javadoc jar and the site) and in every <bottom> of pom.xml (mvn javadoc:javadoc), and that
# the shipped root certificates are the pinned ones:
#   src/main/resources-raw/zephyr/common/cacert.pem   sha256 CA_BUNDLE_SHA256 of bin/ca-bundle.env
#   src/main/resources/zephyr-java.properties         ca.bundle.date = CA_BUNDLE_DATE
# Prints the version and exits 0 when they agree; prints the differing values and exits 1
# otherwise. bin/build_release.sh calls it first.
#
# Usage: bin/check_versions.sh            (or RELEASE_TAG=v2.3.0-sec.2-java.1 bin/check_versions.sh)

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
BOTTOM="$(cat bin/javadoc-bottom.txt)"
while IFS= read -r POM_BOTTOM; do
  if [ "$POM_BOTTOM" != "$BOTTOM" ]; then
    echo "Javadoc bottom text: pom.xml '$POM_BOTTOM', bin/javadoc-bottom.txt '$BOTTOM'" >&2
    STATUS=1
  fi
done < <(sed -n 's|.*<bottom>\(.*\)</bottom>.*|\1|p' pom.xml)
# shellcheck source=ca-bundle.env
. bin/ca-bundle.env
CA_FILE=src/main/resources-raw/zephyr/common/cacert.pem
CA_SHA256="$(sha256sum "$CA_FILE" 2>/dev/null | cut -d' ' -f1 || true)"
if [ "$CA_SHA256" != "$CA_BUNDLE_SHA256" ]; then
  echo "root certificates: $CA_FILE sha256 '$CA_SHA256', bin/ca-bundle.env CA_BUNDLE_SHA256 $CA_BUNDLE_SHA256" >&2
  STATUS=1
fi
PROPS_DATE="$(sed -n 's/^ca\.bundle\.date=//p' src/main/resources/zephyr-java.properties)"
if [ "$PROPS_DATE" != "$CA_BUNDLE_DATE" ]; then
  echo "root certificates: zephyr-java.properties ca.bundle.date '$PROPS_DATE', bin/ca-bundle.env CA_BUNDLE_DATE $CA_BUNDLE_DATE" >&2
  STATUS=1
fi
[ "$STATUS" = 0 ] && echo "version $POM_VERSION${RELEASE_TAG:+ (tag $RELEASE_TAG)}"
exit "$STATUS"
