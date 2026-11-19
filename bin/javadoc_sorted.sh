#!/usr/bin/env bash
#
# Generates the Javadoc of the JAR into target/apidocs from a sorted list of the source files:
# the Javadoc jar (maven-jar-plugin's javadoc-jar execution in pom.xml packages target/apidocs)
# and the site's javadoc/ (bin/make_site.sh copies it) are this one generation.
#
# Why a sorted list: JDK 17's javadoc lists a member that two classes inherit from a package-private
# base under the first class it meets in index-all.html and member-search-index.js (up to java.4:
# close(), createAccount(), ... of ZephyrWalletDefault, inherited by ZephyrWalletFull and the wallet
# RPC client that java.5 removed; the sorted order stays, so that such a case cannot make the jar
# depend on the directory order again).
# maven-javadoc-plugin passes package names, and javadoc then meets the files of a package in the
# file system's directory order, which differs between machines (ext4 seeds its directory hash per
# file system), so a CI build and a local build of the same commit gave different Javadoc jars.
# Files given in LC_ALL=C order are met in that order on every machine. (JDK 21's javadoc does not
# depend on the order; the release image has JDK 17.)
#
# The options are those maven-javadoc-plugin 3.6.3 passed with this pom (its debug options file):
# -protected -author -version -use, UTF-8 for input and output, -source 8, -notimestamp, the
# window and document title "<project name> <version> API", the bottom text of
# bin/javadoc-bottom.txt (pom.xml carries the same text for mvn javadoc:javadoc, and
# bin/check_versions.sh checks that the two agree), and links to the Java 8 API documentation
# through -linkoffline with bin/javadoc-java8/package-list (the plugin's bundled
# java-api-package-list-1.8, line endings LF). Warnings are allowed; an error stops the script.
# The classpath is the compile classpath that Maven resolves (maven-dependency-plugin, pinned).
#
# Missing comments: the generation runs doclint with every group except "missing"
# (-Xdoclint:all,-missing; javadoc's default is all), so the files inherited unchanged from
# monero-java report no "no comment" warnings. A second javadoc run with -Xdoclint:missing over the
# files of bin/javadoc-complete.txt alone (the other sources on the source path) reports a missing
# comment in those files and fails the script when there is one, so a public member added to them
# without a comment stops the release build and the CI job; it writes to a scratch directory that is
# deleted, so target/apidocs is the generation's alone.
#
# Environment: JAVA_HOME (optional; else javadoc from PATH)
# Usage: bin/javadoc_sorted.sh

set -euo pipefail

cd "$(dirname "$0")/.."

JAVADOC="${JAVA_HOME:+$JAVA_HOME/bin/}javadoc"

# "<project name> <version> API", as maven-javadoc-plugin titles the pages
TITLE="$(python3 -I - pom.xml <<'PY'
import sys
import xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(sys.argv[1]).getroot()
print(f'{root.find("m:name", ns).text.strip()} {root.find("m:version", ns).text.strip()} API')
PY
)"

mkdir -p target
mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath \
  -DincludeScope=compile -Dmdep.outputFile=target/javadoc-classpath.txt
find src/main/java -name '*.java' | LC_ALL=C sort > target/javadoc-sources.txt

rm -rf target/apidocs
"$JAVADOC" -quiet -protected -author -version -use \
  -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 -source 8 -notimestamp \
  -windowtitle "$TITLE" -doctitle "$TITLE" -bottom "$(cat bin/javadoc-bottom.txt)" \
  -linkoffline https://docs.oracle.com/javase/8/docs/api bin/javadoc-java8 \
  -Xdoclint:all,-missing \
  -classpath "$(cat target/javadoc-classpath.txt)" \
  -d target/apidocs @target/javadoc-sources.txt
[ -f target/apidocs/index.html ] || { echo "javadoc wrote no target/apidocs/index.html" >&2; exit 1; }
echo "target/apidocs: $(find target/apidocs -type f | wc -l) files from $(wc -l < target/javadoc-sources.txt) sources in sorted order"

# missing comments in the files kept complete (bin/javadoc-complete.txt)
grep -v '^#' bin/javadoc-complete.txt | grep . | sed 's#^#src/main/java/#' > target/javadoc-complete-sources.txt
rm -rf target/javadoc-complete-check
"$JAVADOC" -quiet -protected -encoding UTF-8 -source 8 -Xdoclint:missing -Xmaxwarns 100000 \
  -sourcepath src/main/java -classpath "$(cat target/javadoc-classpath.txt)" \
  -d target/javadoc-complete-check @target/javadoc-complete-sources.txt > target/javadoc-complete.log 2>&1 \
  || { cat target/javadoc-complete.log >&2; exit 1; }
rm -rf target/javadoc-complete-check
cat target/javadoc-complete.log >&2
missing=$(grep -c ': warning:' target/javadoc-complete.log || true)
echo "missing Javadoc in the $(wc -l < target/javadoc-complete-sources.txt) files of bin/javadoc-complete.txt: $missing warnings"
[ "$missing" = 0 ] || { echo "document every public member of the files in bin/javadoc-complete.txt" >&2; exit 1; }
