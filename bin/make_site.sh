#!/usr/bin/env bash
#
# Builds dist/<version>/site/, the content of the project's GitHub Pages for one release:
#   index.html   the landing page: the name, the not-affiliated disclaimer, the Gradle and Maven
#                lines, links to the user guide, the Javadoc, the releases and the source, the
#                maintainer key's fingerprint and, at the bottom, the donation address (the same
#                sentence and address as the README's "Donations")
#   guide/       the user guide, one page per file of docs/guide/
#   assets/      the house stylesheet (unchanged copies from the lateo house style), the site's
#                own additions and the favicons, with the notice for them
#   .nojekyll    GitHub Pages serves the files as they are instead of running Jekyll over them
#                (these four: site/build_site.py, the same pages as bin/build_site.sh; no script,
#                no external resource, relative links only)
#   javadoc/     the Javadoc of the JAR: target/apidocs, the generation bin/javadoc_sorted.sh made
#                for the Javadoc jar (bin/package_release.sh runs it), so site and jar are the same
#   maven/       a copy of dist/<version>/maven/ (bin/make_maven_repo.sh, which runs first);
#                bin/merge_previous_release.sh then merges the earlier releases into it and writes
#                index.html anew with their section
# The result is checked with site/check_site.py (bin/check_site.sh).
# The owner publishes the directory with the release (the gh-pages branch or the docs/ folder of
# main); the URLs in it assume https://loki-notfound.github.io/myzephyr-java/.
# bin/build_release.sh calls this script after bin/make_maven_repo.sh. The Javadoc pages carry no
# generation date (notimestamp in pom.xml) and the guide's pages name the commit and its date, not
# the build time, so the site is reproducible like the jars.
#
# Environment: JAVA_HOME (a JDK), SOURCE_DATE_EPOCH (optional).
#
# Usage: bin/make_site.sh [dist/<version>]   (default: dist/<version of pom.xml>)

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

: "${JAVA_HOME:?set JAVA_HOME to a JDK}"

if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
  SOURCE_DATE_EPOCH="$(git log -1 --format=%ct HEAD)"
fi
export SOURCE_DATE_EPOCH

VERSION="$(python3 -I - "$ROOT/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text.strip())
PY
)"

DIST="${1:-dist/$VERSION}"
DIST="$(cd "$DIST" && pwd)"
SITE="$DIST/site"
[ -d "$DIST/maven" ] || { echo "no $DIST/maven: run bin/make_maven_repo.sh first" >&2; exit 1; }
rm -rf "$SITE"
mkdir -p "$SITE"

# index.html, guide/, assets/ and .nojekyll
python3 -I site/build_site.py --out "$SITE"

# the Javadoc: the same generation as the Javadoc jar's
[ -f target/apidocs/index.html ] || { echo "no target/apidocs/index.html: run bin/javadoc_sorted.sh (bin/package_release.sh does)" >&2; exit 1; }
cp -R target/apidocs "$SITE/javadoc"

# the Maven repository
cp -R "$DIST/maven" "$SITE/maven"

# the checks of bin/check_site.sh
python3 -I site/check_site.py --site "$SITE"

echo "wrote ${SITE#"$ROOT"/}: $(find "$SITE" -type f | wc -l) files ($(find "$SITE/guide" -type f | wc -l) guide pages, $(find "$SITE/javadoc" -type f | wc -l) Javadoc, $(find "$SITE/maven" -type f | wc -l) Maven repository)"
