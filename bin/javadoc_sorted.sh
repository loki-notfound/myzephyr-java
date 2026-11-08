#!/usr/bin/env bash
#
# Generates the Javadoc of the JAR into target/apidocs from a sorted list of the source files:
# the Javadoc jar (maven-jar-plugin's javadoc-jar execution in pom.xml packages target/apidocs)
# and the site's javadoc/ (bin/make_site.sh copies it) are this one generation.
#
# Why a sorted list: JDK 17's javadoc lists a member that two classes inherit from a common base
# (close(), createAccount(), ... of ZephyrWalletDefault, inherited by ZephyrWalletFull and
# ZephyrWalletRpc) under the first class it meets in index-all.html and member-search-index.js.
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
  -classpath "$(cat target/javadoc-classpath.txt)" \
  -d target/apidocs @target/javadoc-sources.txt
[ -f target/apidocs/index.html ] || { echo "javadoc wrote no target/apidocs/index.html" >&2; exit 1; }
echo "target/apidocs: $(find target/apidocs -type f | wc -l) files from $(wc -l < target/javadoc-sources.txt) sources in sorted order"
