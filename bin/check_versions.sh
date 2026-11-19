#!/usr/bin/env bash
#
# Checks that the version of the release is the same everywhere it is written:
#   pom.xml                      project/version (the JAR, sources and Javadoc jars)
#   android/gradle.properties    VERSION_NAME (the AAR and its POM)
#   RELEASE_TAG (optional)       the tag being released must be v<version>
# that the groupId is the same in both builds (pom.xml project/groupId, GROUP of
# android/gradle.properties),
# that the Javadoc bottom text is the same in bin/javadoc-bottom.txt (bin/javadoc_sorted.sh,
# the Javadoc jar and the site) and in every <bottom> of pom.xml (mvn javadoc:javadoc), and that
# the shipped root certificates are the pinned ones:
#   src/main/resources-raw/zephyr/common/cacert.pem   sha256 CA_BUNDLE_SHA256 of bin/ca-bundle.env
#   src/main/resources/zephyr-java.properties         ca.bundle.date = CA_BUNDLE_DATE
# and that the release-bound strings of README.md and the guide (docs/guide/*.md) name the
# version and the groupId of pom.xml:
#   <groupId>:zephyr-java(-android):<version>               every coordinate with a version
#   <groupId>:zephyr-java(-android)                         every coordinate: the groupId (a name
#                                                           with a dot before the artifactId)
#   <version> after <artifactId>zephyr-java(-android)</artifactId>    the Maven snippet
#   <groupId> before <artifactId>zephyr-java(-android)</artifactId>   the Maven snippet
#   zephyrJava = "<version>"                                the version catalog entry
#   git verify-tag v<version>                               the command that checks a release tag
#   This guide describes version `<version>`.              the guide index, exactly once
# and that neither names the Maven repository the project's GitHub Pages served up to
# 2.3.0-sec.2-java.4 (.../myzephyr-java/maven): releases come from Maven Central.
# A coordinate or tag with a placeholder (zephyr-java:<version>) is not checked for its version.
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
POM_GROUP="$(python3 -I - pom.xml <<'PY'
import sys
import xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:groupId", ns).text.strip())
PY
)"
GRADLE_GROUP="$(sed -n 's/^GROUP=//p' android/gradle.properties)"

STATUS=0
if [ "$POM_VERSION" != "$GRADLE_VERSION" ]; then
  echo "version mismatch: pom.xml $POM_VERSION, android/gradle.properties VERSION_NAME '$GRADLE_VERSION'" >&2
  STATUS=1
fi
if [ "$POM_GROUP" != "$GRADLE_GROUP" ]; then
  echo "groupId mismatch: pom.xml $POM_GROUP, android/gradle.properties GROUP '$GRADLE_GROUP'" >&2
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
if ! python3 -I - "$POM_VERSION" "$POM_GROUP" README.md docs/guide/*.md <<'PY'
import re
import sys

version, group, files = sys.argv[1], sys.argv[2], sys.argv[3:]
coordinate = re.compile(r"zephyr-java(?:-android)?:([0-9][^\"'\s)`<]*)")
coordinate_group = re.compile(r"([A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)+):zephyr-java(?:-android)?\b")
artifact = re.compile(r"<artifactId>zephyr-java(?:-android)?</artifactId>")
maven = re.compile(r"<version>([^<]*)</version>")
maven_group = re.compile(r"<groupId>([^<]*)</groupId>")
pages_repository = re.compile(r"github\.io/myzephyr-java/maven")
catalog = re.compile(r"^\s*zephyrJava\s*=\s*\"([^\"]*)\"")
tag = re.compile(r"verify-tag v([0-9]\S*)")
index = re.compile(r"^This guide describes version `([^`]*)`\.$")
bad = 0
index_lines = 0
for path in files:
    after_artifact = False
    last_group = None
    with open(path, encoding="utf-8") as f:
        for number, line in enumerate(f, 1):
            found = [(what, m.group(1)) for what, rx in (("coordinate", coordinate), ("verify-tag", tag))
                     for m in rx.finditer(line)]
            groups = [("coordinate's groupId", m.group(1)) for m in coordinate_group.finditer(line)]
            m = maven_group.search(line)
            if m:
                last_group = m.group(1)
            if artifact.search(line):
                groups.append(("Maven <groupId>", last_group))
            if "</dependency>" in line or line.startswith("```"):
                last_group = None
            for what, value in groups:
                if value != group:
                    print(f"release-bound string: {path}:{number} {what} '{value}', pom.xml {group}", file=sys.stderr)
                    bad += 1
            if pages_repository.search(line):
                print(f"release-bound string: {path}:{number} the former Maven repository on Pages", file=sys.stderr)
                bad += 1
            m = catalog.search(line)
            if m:
                found.append(("version catalog", m.group(1)))
            m = maven.search(line)
            if m and after_artifact:
                found.append(("Maven <version>", m.group(1)))
            if artifact.search(line):
                after_artifact = True
            elif "</dependency>" in line or line.startswith("```"):
                after_artifact = False
            if path == "docs/guide/README.md":
                m = index.match(line.rstrip("\n"))
                if m:
                    index_lines += 1
                    found.append(("guide index", m.group(1)))
            for what, value in found:
                if value != version:
                    print(f"release-bound string: {path}:{number} {what} '{value}', pom.xml {version}", file=sys.stderr)
                    bad += 1
if index_lines != 1:
    print(f"release-bound string: docs/guide/README.md has {index_lines} version lines, expected 1", file=sys.stderr)
    bad += 1
sys.exit(1 if bad else 0)
PY
then
  STATUS=1
fi
[ "$STATUS" = 0 ] && echo "version $POM_VERSION${RELEASE_TAG:+ (tag $RELEASE_TAG)}"
exit "$STATUS"
