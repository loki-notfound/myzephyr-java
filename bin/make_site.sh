#!/usr/bin/env bash
#
# Builds dist/<version>/site/, the content of the project's GitHub Pages for one release:
#   index.html   one plain HTML page (no scripts, no external resources): the name, the
#                not-affiliated disclaimer, the Gradle and Maven lines, links to the user guide
#                on GitHub, the Javadoc, the releases, the maintainer key's fingerprint and, at
#                the bottom, the donation address (the same sentence and address as the README's
#                "Donations")
#   javadoc/     the Javadoc of the JAR: target/apidocs, the generation bin/javadoc_sorted.sh made
#                for the Javadoc jar (bin/package_release.sh runs it), so site and jar are the same
#   maven/       a copy of dist/<version>/maven/ (bin/make_maven_repo.sh, which runs first);
#                bin/merge_previous_release.sh then merges the earlier releases into it and adds
#                their section to index.html
#   .nojekyll    GitHub Pages serves the files as they are instead of running Jekyll over them
# The owner publishes the directory with the release (the gh-pages branch or the docs/ folder of
# main); the URLs below assume https://loki-notfound.github.io/myzephyr-java/.
# bin/build_release.sh calls this script after bin/make_maven_repo.sh. The Javadoc pages carry no
# generation date (notimestamp in pom.xml), so the site is reproducible like the jars.
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
AAR_ID="$(sed -n 's/^ARTIFACT_ID=//p' android/gradle.properties)"

DIST="${1:-dist/$VERSION}"
DIST="$(cd "$DIST" && pwd)"
SITE="$DIST/site"
[ -d "$DIST/maven" ] || { echo "no $DIST/maven: run bin/make_maven_repo.sh first" >&2; exit 1; }
rm -rf "$SITE"
mkdir -p "$SITE"

# the Javadoc: the same generation as the Javadoc jar's
[ -f target/apidocs/index.html ] || { echo "no target/apidocs/index.html: run bin/javadoc_sorted.sh (bin/package_release.sh does)" >&2; exit 1; }
cp -R target/apidocs "$SITE/javadoc"

# the Maven repository
cp -R "$DIST/maven" "$SITE/maven"

: > "$SITE/.nojekyll"

cat > "$SITE/index.html" <<EOF
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>zephyr-java</title>
<style>
body { font-family: sans-serif; max-width: 46rem; margin: 2rem auto; padding: 0 1rem; line-height: 1.5; }
pre { background: #f4f4f4; padding: 0.75rem; overflow-x: auto; }
code { overflow-wrap: anywhere; }
</style>
</head>
<body>
<h1>zephyr-java</h1>
<p>Java bindings for the Zephyr Protocol wallet core, for Android (AAR) and for desktop and server
JVMs (JAR), built from the security-hardened forks of zephyr-backports. Version $VERSION.</p>
<p><strong>Not affiliated with ZephyrProtocol.</strong> An independent project. Report Zephyr
vulnerabilities to the ZephyrProtocol team, not here.</p>

<h2>Android (Gradle, Kotlin DSL)</h2>
<pre>repositories {
    maven { url = uri("https://loki-notfound.github.io/myzephyr-java/maven") }
}

android {
    packaging { resources.excludes += "/META-INF/DEPENDENCIES" }
}

dependencies {
    implementation("io.github.loki-notfound:$AAR_ID:$VERSION")
}</pre>

<h2>Desktop and server (Maven)</h2>
<pre>&lt;repositories&gt;
  &lt;repository&gt;
    &lt;id&gt;zephyr-java&lt;/id&gt;
    &lt;url&gt;https://loki-notfound.github.io/myzephyr-java/maven&lt;/url&gt;
  &lt;/repository&gt;
&lt;/repositories&gt;

&lt;dependency&gt;
  &lt;groupId&gt;io.github.loki-notfound&lt;/groupId&gt;
  &lt;artifactId&gt;zephyr-java&lt;/artifactId&gt;
  &lt;version&gt;$VERSION&lt;/version&gt;
&lt;/dependency&gt;</pre>

<h2>Documentation</h2>
<ul>
<li><a href="https://github.com/loki-notfound/myzephyr-java/blob/main/docs/guide/README.md">User guide</a> (on GitHub)</li>
<li><a href="javadoc/index.html">Javadoc</a> of version $VERSION</li>
<li><a href="https://github.com/loki-notfound/myzephyr-java/releases">Releases</a> with the build manifest and the signed SHA256SUMS</li>
<li><a href="https://github.com/loki-notfound/myzephyr-java">Source</a></li>
</ul>

<h2>Verifying</h2>
<p>Release tags and SHA256SUMS are signed with the maintainer key</p>
<pre>31DA 39AB 43E0 CA67 F1F3  A661 BA2A FF22 DF8A 4A13</pre>
<p>whose public key is <a href="https://github.com/loki-notfound/myzephyr-java/blob/main/KEYS.asc">KEYS.asc</a>
in the repository.</p>

<h2>Donations</h2>
<p>If zephyr-java is useful to you, donations are welcome at this ZEPH address:</p>
<p><code>ZEPHs8MmrN556ieVvv9YUZf3J5pW2uN1z5S88mb9DksG9bcEcqVGg9XbuQc6Q9e9Ka6bjMHYgaSidWfL1r1DGLyngj8NHT3Q6gp</code></p>
</body>
</html>
EOF

echo "wrote ${SITE#"$ROOT"/}: $(find "$SITE" -type f | wc -l) files ($(find "$SITE/javadoc" -type f | wc -l) Javadoc, $(find "$SITE/maven" -type f | wc -l) Maven repository)"
